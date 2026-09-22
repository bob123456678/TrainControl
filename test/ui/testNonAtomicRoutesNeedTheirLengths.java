package ui;

import java.util.ArrayList;
import javax.swing.SwingUtilities;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.util.I18n;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Atomic Routes cannot be switched off while drivable track has no length (Adam, 2026-09-21).
 *
 * Adam, shown that the only thing standing between an unmeasured railway and track handed back under a
 * moving train was a sentence in a tooltip: *"can we simply refuse it now if there are unmeasured
 * segments in what's walkable via the autonomy editor?  Limit it to logical edges that it prompts for,
 * and on active pages only."*  Then, of a setup loaded from a file: *"yes, shut the file door too - just
 * enable the setting and show a warning in the log."*
 *
 * **WHY IT IS UNSAFE, AND EXACTLY WHEN.**  Non-atomic mode releases an edge as soon as
 * `Layout.tailHasProvablyPassed` returns true, and that returns true immediately when the path it is
 * asked about has NO MEASURED EDGE AT ALL.  So the hazard is a rail with no length: a path over it can
 * be released with the train still lying on it.
 *
 * **WHICH IS NOT THE EDITOR'S SQUARE COUNT, and the first version of this gate asked that** (VD13-B2).
 * An edge's length is the sum of its squares, so a railway with one unmeasured switch square still has a
 * length on every edge - the escape provably cannot fire, and the only cost of the missing square is
 * that the tail walk under-counts and edges are held LONGER, which is the safe direction.  Asking the
 * editor therefore refused an operator who had finished measuring; and it answered "nothing unmeasured"
 * for a railway with no diagram at all, which is where the danger actually lives.
 * `Layout.unmeasuredDrivableTrack` is the question in the form the hazard takes, and all three doors -
 * the checkbox and both load paths - now ask it.
 *
 * **THE CONTROL IS THE FIRST CLAIM, and it is the one that could go wrong.**  A refusal that fires
 * whatever the railway looks like would pass a test that only measures the refusal, and would take the
 * setting away from somebody who has done the work - which is the failure Adam's standing rule is about.
 *
 * MUTATION: make `whyNonAtomicRoutesAreRefused` return the message unconditionally and the first claim
 * goes red; make it return null unconditionally and the second does; drop the `unmeasured <= 0` return
 * from `keepAtomicRoutesOnWhileTrackIsUnmeasured` and the control in the second method goes red.
 *
 * @author Adam
 */
public class testNonAtomicRoutesNeedTheirLengths
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static TrainControlUI ui;

    private static Layout layout;

    /** Every edge this class shortened, and what it held, put back in teardown. */
    private static final java.util.Map<Edge, Integer> lengthsWere = new java.util.LinkedHashMap<>();

    private static boolean atomicWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the door is a checkbox on a window, and a window needs a display");
        }

        // THE FROZEN COPY of the operator's railway, not the railway he is operating (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);

        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so the door this is about does not exist");

        settle();
        settle();

        assertTrue(model.hasAutoLayout(), "the sandbox copy holds no autonomy setup");

        layout = model.getAutoLayout();

        assertNotNull(layout, "the setup did not build");

        atomicWas = layout.isAtomicRoutes();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            // EVERY LENGTH BACK FIRST, before anything that can throw (VD13-T5).
            for (java.util.Map.Entry<Edge, Integer> was : lengthsWere.entrySet())
            {
                was.getKey().setLength(was.getValue());
            }

            if (layout != null) layout.setAtomicRoutes(atomicWas);

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Measured everywhere, the door is open; one rail short, it is refused and says how much.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheDoorIsOpenWhenEverythingIsMeasuredAndShutWhenItIsNot() throws Exception
    {
        measureEveryRail();

        assertEquals(layout.unmeasuredDrivableTrack(), 0,
            "precondition: measuring every rail left " + layout.unmeasuredDrivableTrack()
            + " still without a length, so the control below cannot show an open door");

        // THE CONTROL.  Everything measured, so nothing is in the way of switching atomic routes off.
        assertNull(ui.whyNonAtomicRoutesAreRefused(),
            "every rail a train can be driven over has a length and the door still refuses, which takes"
            + " the setting away from an operator who has done the work: "
            + ui.whyNonAtomicRoutesAreRefused());

        Edge rail = aDrivableRail();

        assertNotNull(rail, "precondition: this railway has no rail whose far end is switched on");

        shorten(rail);

        try
        {
            assertEquals(layout.unmeasuredDrivableTrack(), 1,
                "taking the length off " + rail.getName() + " should leave exactly one rail unmeasured,"
                + " and the railway counts " + layout.unmeasuredDrivableTrack()
                + " - a rail is two Edge objects and this is counted by the pair of places it joins");

            String why = ui.whyNonAtomicRoutesAreRefused();

            assertNotNull(why,
                "one rail a train can be driven over has no length and Atomic Routes can still be"
                + " switched off - so a path over it will be handed back as the head passes, with the"
                + " train still standing on it, which is what the tooltip used to warn about and"
                + " nothing enforced");

            // THE WHOLE MESSAGE, not just a digit in it (VD13-T6).  `contains("1")` was satisfied by
            // 13, 21 and 118, so a door that passed the wrong quantity - the whole-railway count, say -
            // would have read as correct.
            assertEquals(why, I18n.f("autolayout.errorNonAtomicNeedsLengths", 1),
                "the refusal does not say how much track is unmeasured, so the operator cannot tell"
                + " whether it is one rail or the whole railway: " + why);
        }
        finally
        {
            restore(rail);
        }
    }

    /**
     * A SETUP THAT LOADS NON-ATOMIC OVER UNMEASURED TRACK COMES UP ATOMIC (Adam, 2026-09-21).
     *
     * *"Yes, shut the file door too - just enable the setting and show a warning in the log."*
     *
     * The checkbox refuses the gesture, because somebody is there to read the refusal and is one gesture
     * from fixing it.  A file has nobody at it, and refusing the load would make a configuration he
     * already has unopenable - so the safe setting is written instead and the log says why.  Turning
     * atomic routes ON can never be the unsafe answer: atomic mode releases nothing until a run ends.
     *
     * **THE CONTROL IS THE SECOND HALF.**  A rule that forced the setting on whatever the railway looked
     * like would take non-atomic mode away from an operator who has measured everything, and a test that
     * only checked the forcing would pass.
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testTheDoorIsOpenWhenEverythingIsMeasuredAndShutWhenItIsNot")
    public void testALoadedSetupCannotRunNonAtomicOverUnmeasuredTrack() throws Exception
    {
        measureEveryRail();

        Edge rail = aDrivableRail();

        assertNotNull(rail, "precondition: this railway has no rail whose far end is switched on");

        shorten(rail);

        try
        {
            layout.setAtomicRoutes(false);

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTrackIsUnmeasured());

            assertTrue(layout.isAtomicRoutes(),
                "a setup that turns atomic routes off has been loaded with " + rail.getName()
                + " unmeasured, and the railway is running non-atomic: a path over that rail will be"
                + " handed back as the head passes it, and nobody was at the door to be told");

            // THE CONTROL: measured everywhere, a deliberate OFF is left alone.
            restore(rail);

            assertEquals(layout.unmeasuredDrivableTrack(), 0,
                "precondition: putting the length back left " + layout.unmeasuredDrivableTrack()
                + " rails still unmeasured, so the control below is not about a measured railway");

            layout.setAtomicRoutes(false);

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTrackIsUnmeasured());

            assertFalse(layout.isAtomicRoutes(),
                "every rail has a length and the load turned atomic routes back on anyway, which takes"
                + " the setting away from an operator who has done the work - the same failure the"
                + " refusal at the checkbox is guarded against");
        }
        finally
        {
            restore(rail);

            layout.setAtomicRoutes(atomicWas);
        }
    }

    /** Gives every edge with no length a length, remembering what it held. */
    private static void measureEveryRail()
    {
        for (Edge edge : new ArrayList<>(layout.getEdges()))
        {
            if (edge == null || edge.getLength() > 0) continue;

            if (!lengthsWere.containsKey(edge)) lengthsWere.put(edge, edge.getLength());

            edge.setLength(1);
        }
    }

    /**
     * A rail a train could be driven over - its far end switched on, so `isPathClear` does not refuse it.
     *
     * @return the edge, or null where this railway has none
     */
    private static Edge aDrivableRail()
    {
        for (Edge edge : new ArrayList<>(layout.getEdges()))
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            if (!edge.getEnd().isActive()) continue;

            return edge;
        }

        return null;
    }

    /** Takes the length off one rail, remembering what it held. */
    private static void shorten(Edge rail)
    {
        if (!lengthsWere.containsKey(rail)) lengthsWere.put(rail, rail.getLength());

        rail.setLength(0);
    }

    /** Puts one rail's length back. */
    private static void restore(Edge rail)
    {
        Integer was = lengthsWere.get(rail);

        rail.setLength(was == null || was <= 0 ? 1 : was);
    }

    /** Lets the event thread catch up, twice being what the window needs after init (OB-192). */
    private static void settle() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
