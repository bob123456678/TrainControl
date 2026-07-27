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
 * A Locomotive's hashCode is built from its name, address and decoder type, and all three are mutable
 * in place - rename assigns the name, setAddress assigns the other two.  Anything holding the object as
 * a hash key therefore loses track of it the moment one changes: iteration still finds it, but
 * contains(), remove() and get() do not.
 *
 * The autonomy graph holds locomotive references in two such places, both populated from the JSON at
 * load and both probed while routing:
 *
 *  - Point.excludedLocs, checked by isPathClear and pickPath.  A stale entry here is not a bookkeeping
 *    problem: the exclusion silently stops applying, and the locomotive gets routed into a station it
 *    was excluded from.
 *  - Layout.locomotivesToRun.
 *
 * Renaming is refused while the layout isRunning(), which also covers a graceful stop with paths still
 * finishing - so activeLocomotives and locomotiveMilestones cannot be stale, and are not covered here.
 * The gap these tests pin is the idle one: graph loaded, nothing moving, the rename permitted and
 * correct to permit.
 *
 * Three identity changes reach these collections, and all three are covered below: rename, address
 * change (address and decoder type are hashCode inputs too), and deletion - which has to remove the
 * locomotive rather than re-key it.
 *
 * Not covered: the same drift through syncWithCS2, which updates a locomotive's address when the
 * Central Station reports a different one.  That path now defers while anything is running and
 * otherwise performs the same repair, but reaching it from a test needs a Central Station to sync
 * against.
 */
public class testLayoutRenameKeys
{
    private static MarklinControlStation model;

    private static int feedbackBase = 47300;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);
        model.stop();
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        for (String name : new String[] {
            "RK excluded", "RK excluded 2", "RK export", "RK export 2", "RK torun", "RK torun 2",
            "RK addr", "RK deleted", "RK drifted" })
        {
            model.deleteLoc(name);
        }
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
     * The cleanup has to work even on a locomotive whose hash has already drifted.
     *
     * setAddress is called directly here, bypassing changeLocAddress and its repair, to manufacture
     * exactly the stale state the repairs exist to prevent.  locDeleted must still find it: it scans
     * rather than calling remove(), because a hash lookup is the one thing that cannot find a drifted
     * key.  Without that, the cleanup would work only in the cases that did not need it.
     */
    @Test
    public void testDeletingFindsALocomotiveWhoseHashAlreadyDrifted() throws Exception
    {
        MarklinLocomotive excluded = model.newDCCLocomotive("RK drifted", 96);
        Layout layout = layoutExcluding(excluded);

        Point b = layout.getPoint("RK_B");

        int before = excluded.hashCode();

        // Drift it, without the repair that changeLocAddress would have run
        excluded.setAddress(97, MarklinLocomotive.decoderType.DCC);

        assertNotEquals(excluded.hashCode(), before,
            "precondition: the address change moved the locomotive's hash");

        assertFalse(b.getExcludedLocs().contains(excluded),
            "precondition: and the set can no longer find it by lookup - this is the whole hazard");

        layout.locDeleted(excluded);

        assertTrue(b.getExcludedLocs().isEmpty(),
            "the cleanup must still remove it, which means scanning rather than looking up");
    }
}
