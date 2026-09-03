package core;

import org.traincontrol.automation.Edge;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Renaming a locomotive must not lose it from the collections that are keyed on the locomotive object.
 *
 * These were written when a Locomotive's hashCode was built from its name, address and decoder type -
 * all three mutable in place - so anything holding the object as a hash key lost track of it the moment
 * one changed: iteration still found it, but contains(), remove() and get() did not.
 *
 * hashCode is now identity-based, which removes the hazard at the root rather than repairing each
 * collection after the fact.  These tests are kept because they assert the *behaviour* - an exclusion
 * still applies after a rename, a deleted locomotive stops being excluded - which must hold however
 * that is achieved.  They are what would prove the remaining repair calls safe to delete, and
 * testHashNeverMovesWhenAnIdentityFieldChanges guards the root fix itself.
 *
 * The autonomy graph holds locomotive references in two such places, both populated from the JSON at
 * load and both probed while routing:
 *
 *  - Point.excludedLocs, checked by isPathClear and pickPath.  A stale entry here was never a mere
 *    bookkeeping problem: the exclusion stopped applying silently, and the locomotive was routed into
 *    a station it had been excluded from.
 *  - Layout.locomotivesToRun.
 *
 * Renaming is refused while the layout isRunning(), which also covers a graceful stop with paths still
 * finishing - so activeLocomotives and locomotiveMilestones cannot be stale, and are not covered here.
 * The gap these tests pin is the idle one: graph loaded, nothing moving, the rename permitted and
 * correct to permit.
 *
 * Three operations change a locomotive's identity and all three are covered below - rename, address
 * change, and deletion, which has to remove the locomotive rather than re-key it.  A fourth,
 * syncWithCS2 adopting an address the Central Station reports, is not: reaching it from a test needs a
 * Central Station to sync against.  It defers while anything is running and otherwise runs the same
 * repair as changeLocAddress, which is covered here.
 */
public class testLayoutRenameKeys
{
    private static MarklinControlStation model;

    private static int feedbackBase = 47300;

    /** What model.autoLayout held before this class started installing hand-built ones over it. */
    private static Layout hadAutoLayout;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);
        model.stop();

        hadAutoLayout = readAutoLayout();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        for (String name : new String[] {
            "RK excluded", "RK excluded 2", "RK export", "RK export 2", "RK torun", "RK torun 2",
            "RK addr", "RK deleted", "RK drifted", "RK drifted renamed" })
        {
            model.deleteLoc(name);
        }

        // TST-B20: attach() installs a hand-built Layout into the shared model by reflection, and
        // nothing put back what was there before - so whatever this class ran last stayed installed as
        // model.getAutoLayout() for every class that shares this JVM after it.
        attach(hadAutoLayout);
    }

    private static Layout readAutoLayout() throws Exception
    {
        Field field = MarklinControlStation.class.getDeclaredField("autoLayout");

        field.setAccessible(true);

        return (Layout) field.get(model);
    }

    /**
     * renameLoc only re-keys the graph when the model has one, and the only public routes to that are
     * parseAuto and the lazy getAutoLayout - neither of which can install a hand-built Layout.
     * Reflection instead, matching testAccessory and testNetworkProxy.
     */
    private static void attach(Layout layout) throws Exception
    {
        Field field = MarklinControlStation.class.getDeclaredField("autoLayout");

        field.setAccessible(true);
        field.set(model, layout);
    }

    /**
     * Two stations, the second excluding the given locomotive.  Each call takes a fresh pair of
     * feedback modules, because feedback state lives on the control station and outlives the Layout.
     */
    private Layout layoutExcluding(Locomotive excluded) throws Exception
    {
        Layout layout = new Layout(model);

        String s88A = model.newFeedback(feedbackBase++, null).getName();
        String s88B = model.newFeedback(feedbackBase++, null).getName();

        model.setFeedbackState(s88A, false);
        model.setFeedbackState(s88B, false);

        layout.createPoint("RK_A", true, s88A);
        layout.createPoint("RK_B", true, s88B);
        layout.createEdge("RK_A", "RK_B");

        Set<Locomotive> excludedLocs = new HashSet<>();
        excludedLocs.add(excluded);

        layout.getPoint("RK_B").setExcludedLocs(excludedLocs);

        attach(layout);

        return layout;
    }

    /**
     * The defect: rename an excluded locomotive while the graph is idle, and the exclusion silently
     * stops applying.
     */
    @Test
    public void testExclusionSurvivesARename() throws Exception
    {
        MarklinLocomotive excluded = model.newDCCLocomotive("RK excluded", 90);
        Layout layout = layoutExcluding(excluded);

        Point b = layout.getPoint("RK_B");

        assertTrue(b.getExcludedLocs().contains(excluded), "precondition: the exclusion applies");

        assertTrue(model.renameLoc("RK excluded", "RK excluded 2"));

        assertTrue(b.getExcludedLocs().contains(excluded),
            "the exclusion must still apply after a rename - it is looked up by object, and a stale "
            + "hash means the locomotive is routed into a station it was excluded from");

        assertEquals(b.getExcludedLocs().size(), 1, "and it must not have been duplicated or dropped");
    }

    /**
     * Export was never affected by the drift, because toJSON iterates and reads the live name - but it
     * must still be right after the re-key, which rebuilds the set.
     */
    @Test
    public void testExportUsesTheNewNameAfterARename() throws Exception
    {
        MarklinLocomotive excluded = model.newDCCLocomotive("RK export", 91);
        Layout layout = layoutExcluding(excluded);

        assertTrue(model.renameLoc("RK export", "RK export 2"));

        String json = layout.toJSON();

        assertTrue(json.contains("RK export 2"), "export must carry the current name");
        assertFalse(json.contains("\"RK export\""), "and must not carry the old one");
    }

    /**
     * locomotivesToRun is the other object-keyed collection loaded from the JSON.
     */
    @Test
    public void testLocomotivesToRunSurvivesARename() throws Exception
    {
        MarklinLocomotive running = model.newDCCLocomotive("RK torun", 92);
        Layout layout = layoutExcluding(running);

        layout.setLocomotivesToRun(Arrays.asList((Locomotive) running));

        assertTrue(layout.getLocomotivesToRun().contains(running), "precondition");

        assertTrue(model.renameLoc("RK torun", "RK torun 2"));

        assertTrue(layout.getLocomotivesToRun().contains(running),
            "a renamed locomotive must still be recognised as one of the ones to run");

        assertEquals(layout.getLocomotivesToRun().size(), 1);
    }

    /**
     * An address change drifts the hash exactly as a rename does - address and decoder type are both
     * hashCode inputs - so changeLocAddress needs the same repair.
     *
     * This was missed when the rename case was fixed: the repair was wired into renameLoc only, and
     * changing a locomotive's address went on silently voiding its exclusions.
     */
    @Test
    public void testExclusionSurvivesAnAddressChange() throws Exception
    {
        MarklinLocomotive excluded = model.newDCCLocomotive("RK addr", 93);
        Layout layout = layoutExcluding(excluded);

        Point b = layout.getPoint("RK_B");

        assertTrue(b.getExcludedLocs().contains(excluded), "precondition: the exclusion applies");

        model.changeLocAddress("RK addr", 94, MarklinLocomotive.decoderType.DCC);

        assertTrue(b.getExcludedLocs().contains(excluded),
            "the exclusion must survive an address change for the same reason it survives a rename");

        assertEquals(b.getExcludedLocs().size(), 1);
    }

    /**
     * Deleting a locomotive must take it out of the exclusion sets as well.
     *
     * locDeleted cleared locomotivesToRun, activeLocomotives and locomotiveMilestones but not the
     * points' own sets, so a deleted locomotive stayed excluded for the life of the graph and its name
     * kept being exported as an exclusion for a locomotive that no longer exists.
     */
    @Test
    public void testDeletingALocomotiveClearsItsExclusion() throws Exception
    {
        MarklinLocomotive excluded = model.newDCCLocomotive("RK deleted", 95);
        Layout layout = layoutExcluding(excluded);

        Point b = layout.getPoint("RK_B");

        assertTrue(b.getExcludedLocs().contains(excluded), "precondition: the exclusion applies");

        layout.locDeleted(excluded);

        assertTrue(b.getExcludedLocs().isEmpty(),
            "a deleted locomotive must not stay in the exclusion set");

        assertFalse(layout.toJSON().contains("RK deleted"),
            "and must not still be exported as an exclusion");
    }

    /**
     * The invariant that made all of the re-keying repairs unnecessary: a locomotive's hash never
     * moves, whatever happens to its fields.
     *
     * This test used to assert the opposite.  It manufactured a drifted hash by calling setAddress
     * directly - bypassing the repair - to prove the delete cleanup could still find such a
     * locomotive.  Since equals and hashCode became identity-based, drift cannot be manufactured at
     * all, and the assertion that used to set the scene fails instead.
     *
     * Inverted rather than deleted, because that failure is the point: this is now the regression
     * guard for the root fix, and it fails the moment anyone reimplements hashCode in terms of the
     * locomotive's fields - which is the obvious thing for a future author to do, and which would
     * silently reopen six defects at once.
     *
     * The raw mutators are called deliberately, rather than renameLoc and changeLocAddress: the claim
     * is about the object itself, so going through the methods that repair the collections would test
     * the repairs instead of the invariant.
     */
    @Test
    public void testHashNeverMovesWhenAnIdentityFieldChanges() throws Exception
    {
        MarklinLocomotive loc = model.newDCCLocomotive("RK drifted", 96);
        Layout layout = layoutExcluding(loc);

        Point b = layout.getPoint("RK_B");

        int before = loc.hashCode();

        loc.setAddress(97, MarklinLocomotive.decoderType.DCC);

        assertEquals(loc.hashCode(), before, "an address change must not move the locomotive's hash");
        assertTrue(b.getExcludedLocs().contains(loc),
            "so the exclusion is still found by lookup, with no repair having run");

        loc.rename("RK drifted renamed");

        assertEquals(loc.hashCode(), before, "and neither must a rename");
        assertTrue(b.getExcludedLocs().contains(loc), "and it is still found");

        layout.locDeleted(loc);

        assertTrue(b.getExcludedLocs().isEmpty(), "and deletion still removes it");
    }

    /**
     * UC-C5: renaming a point onto an existing name must be refused by the model.
     *
     * renamePoint checks only that the OLD name exists.  points.put(newName, p) overwrites the
     * existing point, and the edge-key rebuild below it drops colliding edges - graph corruption.
     * Today the sole caller checks uniqueness in the dialog; the model must not depend on one
     * dialog to protect its data (editRoute's own comment states the standard).
     */
    @Test
    public void testRenamingOntoAnExistingPointIsRefused() throws Exception
    {
        Layout layout = new Layout(model);

        String s88A = model.newFeedback(feedbackBase++, null).getName();
        String s88B = model.newFeedback(feedbackBase++, null).getName();

        layout.createPoint("UCR_A", true, s88A);
        layout.createPoint("UCR_B", true, s88B);

        Edge e = layout.createEdge("UCR_A", "UCR_B");

        try
        {
            layout.renamePoint("UCR_A", "UCR_B");
            fail("renaming UCR_A onto the existing UCR_B must throw, not overwrite it");
        }
        catch (Exception expected)
        {
            // refused
        }

        assertNotNull(layout.getPoint("UCR_A"), "the source point is untouched after the refusal");
        assertNotNull(layout.getPoint("UCR_B"), "the target point still exists");
        assertTrue(layout.getEdges().contains(e), "and the edge between them survived");
    }

    /**
     * UC-C18: a rename is refused while the staging planner is at work, not only while trains run.
     *
     * The first guard tested the bare running flag, which the staging planning window passes - yet
     * the planner runs bfs over exactly the structures a rename mutates, with nothing dispatched at
     * all.  isRunning() also counts in-flight locomotives, so the guard now holds through the
     * graceful-stop wind-down too.
     */
    @Test
    public void testRenamingIsRefusedWhileStagingIsPlanning() throws Exception
    {
        Layout layout = new Layout(model);

        String s88 = model.newFeedback(feedbackBase++, null).getName();

        layout.createPoint("UCR_S", true, s88);

        layout.setStagingInProgress(true);

        try
        {
            layout.renamePoint("UCR_S", "UCR_S2");
            fail("the staging planner walks these structures - a rename under it must be refused");
        }
        catch (Exception expected)
        {
            // refused
        }
        finally
        {
            layout.setStagingInProgress(false);
        }

        assertNotNull(layout.getPoint("UCR_S"), "the point is untouched after the refusal");
        assertNull(layout.getPoint("UCR_S2"), "and nothing was created under the new name");
    }
}
