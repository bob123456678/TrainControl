package ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
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
 * Atomic Routes cannot be switched off while the railway could release track under a train.
 *
 * Adam, 2026-09-21: *"can we simply refuse it now if there are unmeasured segments in what's walkable
 * via the autonomy editor?  Limit it to logical edges that it prompts for, and on active pages only."*
 * Then, of a setup loaded from a file: *"yes, shut the file door too - just enable the setting and show
 * a warning in the log."*
 *
 * **THE ESCAPE HAS TWO CLAUSES, and the first two versions of this gate only knew one** (VD14-B1).
 * `Layout.tailHasProvablyPassed` hands an edge back when `pathIsUnmeasured` - no measured edge anywhere
 * on the path - OR when `behind >= trainLength`, and a locomotive's length is **0** until somebody sets
 * it, so `0 >= 0` is true the first time every edge is asked about.  An unmeasured TRAIN releases the
 * whole railway under itself however well the track is measured.
 *
 * **AND THE TRACK HALF IS NARROWER THAN "ANY UNMEASURED EDGE"** (VD14-C6).  A path always ends at a
 * destination, so an unmeasured rail with measured track between it and every destination can never be
 * part of an unmeasured path.  `Layout.unmeasuredTrackThatCouldBeReleased` walks back from the active
 * destinations over unmeasured edges and returns only the rails that could really be handed back.
 *
 * **THE CONTROLS ARE THE CLAIMS THAT COULD GO WRONG.**  A gate that fires whatever the railway looks
 * like would pass a test that only measures the refusal, and would take the setting away from an
 * operator who has done the work - the failure Adam's standing rule is about.  So each half is asked
 * with the railway put right first, and the door must be OPEN.
 *
 * MUTATION: make `whyNonAtomicRoutesAreRefused` return a sentence unconditionally and the two controls
 * go red; return null unconditionally and the two refusals do; drop the `trainsWithNoLength` term from
 * the checkbox's question and the train claim goes red, and from `keepAtomicRoutesOnWhileTrackIsUnmeasured`
 * (its `&& trains.isEmpty()`) and the file door's train claim does - which was reached by no claim at
 * all until VD15-T4; count every unmeasured edge rather than the reachable ones and the claims in
 * `core.testAutoLayout.testARailwayCountsItsUnmeasuredDrivableTrack` do.
 *
 * @author Adam
 */
public class testNonAtomicRoutesNeedTheirLengths
{
    private static support.LayoutSandbox sandbox;

    private static MarklinControlStation model;

    private static TrainControlUI ui;

    private static Layout layout;

    /** Every edge this class shortened, and what it held. */
    private static final java.util.Map<Edge, Integer> lengthsWere = new java.util.LinkedHashMap<>();

    /** Every locomotive whose length this class set, and what it held. */
    private static final java.util.Map<Locomotive, Integer> trainsWere = new java.util.LinkedHashMap<>();

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
            // EVERYTHING BACK FIRST, before anything that can throw (VD13-T5).  The train lengths are
            // Adam's own database, so they matter more than the rest of this teardown.
            for (java.util.Map.Entry<Locomotive, Integer> was : trainsWere.entrySet())
            {
                was.getKey().setTrainLength(was.getValue() == null ? 0 : was.getValue());
            }

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
     * Measured everywhere, the door is open; one rail short, it is refused and names the rail.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheTrackHalfRefusesAndNamesTheRail() throws Exception
    {
        putTheRailwayRight();

        // THE CONTROL.  Everything measured, so nothing is in the way of switching atomic routes off.
        assertNull(ui.whyNonAtomicRoutesAreRefused(),
            "every rail has a length and every train has a length, and the door still refuses - which"
            + " takes the setting away from an operator who has done the work: "
            + ui.whyNonAtomicRoutesAreRefused());

        Edge rail = aRailThatCouldBeReleased();

        assertNotNull(rail, "precondition: no rail of this railway can reach a destination at all");

        shorten(rail);

        try
        {
            List<String> named = layout.unmeasuredTrackThatCouldBeReleased();

            assertEquals(named.size(), 1,
                "taking the length off " + rail.getName() + " should leave exactly one rail that could"
                + " be released, and the railway names " + named
                + " - a rail is two Edge objects and this is counted by the pair of places it joins");

            String why = ui.whyNonAtomicRoutesAreRefused();

            assertEquals(why, I18n.f("autolayout.errorNonAtomicNeedsLengths", 1, named.get(0)),
                "the refusal does not name the rail it is about, so the operator is told how much is"
                + " unmeasured and nothing about where: " + why);
        }
        finally
        {
            restore(rail);
        }
    }

    /**
     * A train with no length is refused too, and named - the second clause of the escape (VD14-B1).
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testTheTrackHalfRefusesAndNamesTheRail")
    public void testATrainWithNoLengthIsRefusedToo() throws Exception
    {
        putTheRailwayRight();

        assertNull(ui.whyNonAtomicRoutesAreRefused(),
            "precondition: the railway is not in a state where the door is open, so nothing below is"
            + " about the train half: " + ui.whyNonAtomicRoutesAreRefused());

        Locomotive train = null;

        for (Locomotive candidate : layout.getLocomotivesToRun())
        {
            if (candidate != null)
            {
                train = candidate;

                break;
            }
        }

        assertNotNull(train, "precondition: this configuration places no locomotive, so there is no"
            + " train to take a length off");

        remember(train);

        try
        {
            train.setTrainLength(0);

            assertEquals(layout.trainsWithNoLength(), java.util.Arrays.asList(train.getName()),
                "the railway does not report " + train.getName() + " as having no train length: "
                + layout.trainsWithNoLength());

            String why = ui.whyNonAtomicRoutesAreRefused();

            assertEquals(why,
                I18n.f("autolayout.errorNonAtomicNeedsTrainLengths", 1, train.getName()),
                "a train with no length releases every edge as its head passes - `behind >= 0` is true"
                + " the first time each one is asked about - and Atomic Routes can still be switched"
                + " off, or the refusal does not name the train: " + why);
        }
        finally
        {
            train.setTrainLength(trainsWere.get(train) == null ? 1 : trainsWere.get(train));
        }
    }

    /**
     * A SETUP THAT LOADS NON-ATOMIC OVER TRACK IT COULD RELEASE COMES UP ATOMIC (Adam, 2026-09-21).
     *
     * *"Yes, shut the file door too - just enable the setting and show a warning in the log."*
     *
     * The checkbox refuses the gesture, because somebody is there to read the refusal and is one gesture
     * from fixing it.  A file has nobody at it, and refusing the load would make a configuration he
     * already has unopenable - so the safe setting is written instead and the log says why.
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testTheTrackHalfRefusesAndNamesTheRail")
    public void testALoadedSetupCannotRunNonAtomicOverTrackItCouldRelease() throws Exception
    {
        putTheRailwayRight();

        Edge rail = aRailThatCouldBeReleased();

        assertNotNull(rail, "precondition: no rail of this railway can reach a destination at all");

        shorten(rail);

        try
        {
            layout.setAtomicRoutes(false);

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTrackIsUnmeasured());

            assertTrue(layout.isAtomicRoutes(),
                "a setup that turns atomic routes off has been loaded with " + rail.getName()
                + " unmeasured, and the railway is running non-atomic: a path over that rail will be"
                + " handed back as the head passes it, and nobody was at the door to be told");

            // THE CONTROL: put right, a deliberate OFF is left alone.
            restore(rail);

            layout.setAtomicRoutes(false);

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTrackIsUnmeasured());

            assertFalse(layout.isAtomicRoutes(),
                "the railway can release nothing under a train and the load turned atomic routes back"
                + " on anyway, which takes the setting away from an operator who has done the work");
        }
        finally
        {
            restore(rail);

            layout.setAtomicRoutes(atomicWas);
        }
    }

    /**
     * AND A SETUP THAT LOADS NON-ATOMIC WITH A TRAIN THAT HAS NO LENGTH COMES UP ATOMIC (VD15-T4).
     *
     * The file door has the same two halves as the checkbox, and only the track one was asked about -
     * so deleting `&& trains.isEmpty()` from `keepAtomicRoutesOnWhileTrackIsUnmeasured` left every
     * claim in this class green, and `autolayout.warnAtomicRoutesKeptOnTrains` was a string no test
     * ever reached.  A train of no length releases the whole railway under itself however well the
     * track is measured, so this is the half that does not depend on the operator's rails at all.
     *
     * The door-open control is in the track claim above, which watches a deliberate OFF survive a
     * load on a railway that is put right; it is not repeated here.
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testTheTrackHalfRefusesAndNamesTheRail")
    public void testALoadedSetupCannotRunNonAtomicWithATrainThatHasNoLength() throws Exception
    {
        putTheRailwayRight();

        Locomotive train = null;

        for (Locomotive candidate : layout.getLocomotivesToRun())
        {
            if (candidate != null)
            {
                train = candidate;

                break;
            }
        }

        assertNotNull(train, "precondition: this configuration places no locomotive, so there is no"
            + " train to take a length off");

        remember(train);

        try
        {
            train.setTrainLength(0);

            layout.setAtomicRoutes(false);

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTrackIsUnmeasured());

            assertTrue(layout.isAtomicRoutes(),
                "a setup that turns atomic routes off has been loaded while " + train.getName()
                + " has no train length, and the railway is running non-atomic: every edge will be"
                + " handed back as that train's head passes it, and nobody was at the door to be told");
        }
        finally
        {
            train.setTrainLength(trainsWere.get(train) == null ? 1 : trainsWere.get(train));

            layout.setAtomicRoutes(atomicWas);
        }
    }

    // ---------------------------------------------------------------- the railway

    /** Gives every rail and every train a length, remembering what they held. */
    private static void putTheRailwayRight()
    {
        for (Edge edge : new ArrayList<>(layout.getEdges()))
        {
            if (edge == null || edge.getLength() > 0) continue;

            if (!lengthsWere.containsKey(edge)) lengthsWere.put(edge, edge.getLength());

            edge.setLength(1);
        }

        for (Locomotive loc : new ArrayList<>(layout.getLocomotivesToRun()))
        {
            if (loc == null) continue;

            if (loc.getTrainLength() != null && loc.getTrainLength() > 0) continue;

            remember(loc);

            loc.setTrainLength(1);
        }
    }

    /**
     * A rail whose far end can reach a destination, so taking its length away is something the gate
     * should notice.
     *
     * @return the edge, or null where this railway has none
     */
    private static Edge aRailThatCouldBeReleased()
    {
        for (Edge edge : new ArrayList<>(layout.getEdges()))
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            if (!edge.getEnd().isDestination() || !edge.getEnd().isActive()) continue;

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

    /** Remembers a locomotive's own train length, once. */
    private static void remember(Locomotive loc)
    {
        if (!trainsWere.containsKey(loc)) trainsWere.put(loc, loc.getTrainLength());
    }

    /** Lets the event thread catch up, twice being what the window needs after init (OB-192). */
    private static void settle() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
