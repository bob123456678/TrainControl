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
import support.SourceOf;

/**
 * Atomic Routes cannot be switched off while the railway could release track under a train (Adam: "can we simply refuse
 * it now if there are unmeasured segments in what's walkable via the autonomy editor?"; of a setup loaded from a file:
 * "yes, shut the file door too - just enable the setting and show a warning in the log.").
 *
 * The escape has two clauses: `Layout.tailHasProvablyPassed` hands an edge back when `pathIsUnmeasured` OR when `behind
 * >= trainLength`, and a locomotive's length is 0 until set, so an unmeasured TRAIN releases the whole railway.  The
 * track half is narrower than "any unmeasured edge": `Layout.unmeasuredTrackThatCouldBeReleased` walks back from the
 * active destinations over unmeasured edges.  The controls are the claims that could go wrong: each half is asked with
 * the railway put right first, and the door must be OPEN.
 *
 * MUTATION: make `whyNonAtomicRoutesAreRefused` return a sentence unconditionally and the two controls go red; return
 * null unconditionally and the two refusals do; drop the `trainsWithNoLength` term from the checkbox's question and the
 * train claim goes red, and from `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` (its `&& trains.isEmpty()`) and
 * the file door's train claim does; count every unmeasured edge rather than the reachable ones and the claims in
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

        model = support.TestStation.simulatedWithWindowInDebug();

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
            // EVERYTHING BACK FIRST, before anything that can throw: the train lengths are Adam's own database.
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

    /** Measured everywhere, the door is open; one rail short, it is refused and names the rail. */
    @Test
    public void testTheTrackHalfRefusesAndNamesTheRail() throws Exception
    {
        putTheRailwayRight();

        // THE CONTROL: everything measured, so nothing is in the way of switching atomic routes off.
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

    /** A train with no length is refused too, and named: the second clause of the escape. */
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
     * A setup that loads non-atomic over track it could release comes up atomic (Adam: "Yes, shut the file door too -
     * just enable the setting and show a warning in the log.").  A file has nobody at it, and refusing the load would
     * make a configuration he already has unopenable.
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

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack());

            assertTrue(layout.isAtomicRoutes(),
                "a setup that turns atomic routes off has been loaded with " + rail.getName()
                + " unmeasured, and the railway is running non-atomic: a path over that rail will be"
                + " handed back as the head passes it, and nobody was at the door to be told");

            // THE CONTROL: put right, a deliberate OFF is left alone.
            restore(rail);

            layout.setAtomicRoutes(false);

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack());

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
     * A setup that loads non-atomic with a train that has no length comes up atomic.  The file door has the same two
     * halves as the checkbox; deleting `&& trains.isEmpty()` from `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack`
     * once left every claim green.  The door-open control is in
     * `testALoadedSetupCannotRunNonAtomicOverTrackItCouldRelease`, which this depends on so the control is known to
     * have run.
     */
    @Test(dependsOnMethods = "testALoadedSetupCannotRunNonAtomicOverTrackItCouldRelease")
    public void testALoadedSetupCannotRunNonAtomicWithATrainThatHasNoLength() throws Exception
    {
        putTheRailwayRight();

        // WHICH HALF THIS IS ABOUT IS HELD BY `testTheFileDoorLogsTheHalfItFound`, NOT HERE: a precondition that the
        // track half was quiet could not fail, since `putTheRailwayRight()` makes it true by construction.

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

            SwingUtilities.invokeAndWait(() -> ui.keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack());

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

    /**
     * THE FILE DOOR LOGS THE HALF IT FOUND, and each half has its own sentence: swapping
     * `autolayout.warnAtomicRoutesKeptOn` and `autolayout.warnAtomicRoutesKeptOnTrains` leaves every behavioral claim
     * green while the operator is told to measure track when a train's length is wrong.  A text claim because the log
     * has no reader (a private `JTextArea`): the track branch names the track key and the train branch the train key.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheFileDoorLogsTheHalfItFound() throws Exception
    {
        String whole = SourceOf.declaration("public void keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()");

        int cut = whole.indexOf("loadAutoLayoutSettings();");

        assertTrue(cut > 0, "the file door no longer ends in loadAutoLayoutSettings(), so this rule is about nothing");

        String body = whole.substring(0, cut);

        // THE CLOSING QUOTE IS PART OF THE NEEDLE, so the track key is not found inside the train one; built, not
        // written, to avoid an escaped quote.
        String quote = String.valueOf((char) 34);

        String trackKey = "autolayout.warnAtomicRoutesKeptOn" + quote;
        String trainKey = "autolayout.warnAtomicRoutesKeptOnTrains" + quote;

        // THE BRANCHES, NOT THE GAPS: swapping the two `if` CONDITIONS and leaving the `logf` lines where they are
        // leaves every distance unchanged, while an operator with one unmeasured rail is told "0 locomotives have no
        // train length".
        int trackBranch = body.indexOf("if (!track.isEmpty())");
        int trainBranch = body.indexOf("if (!trains.isEmpty())");

        assertTrue(trackBranch > 0 && trainBranch > trackBranch,
            "the file door's two branches are not where this rule can find them - the track branch is"
            + " at " + trackBranch + " and the train branch at " + trainBranch + ".  If they have been"
            + " reordered or rewritten, this rule has to be rewritten with them, because what it holds"
            + " is that each sentence is logged from the branch that found the fault it describes");

        String inTrack = body.substring(trackBranch, trainBranch);
        String inTrains = body.substring(trainBranch);

        // ONCE EACH, IN THE WHOLE METHOD: a sentence logged above the first branch sits in neither span and so in
        // neither check.
        assertEquals(occurrences(body, trackKey), 1,
            "the track sentence is logged " + occurrences(body, trackKey) + " times in this method."
            + "  Once, from the branch that found unmeasured track - a second one is either a"
            + " duplicate or a line outside both branches, which reports a fault the door has not"
            + " found");

        assertEquals(occurrences(body, trainKey), 1,
            "the train sentence is logged " + occurrences(body, trainKey) + " times in this method,"
            + " and it belongs only to the branch that found a train with no length");

        // AND EACH SENTENCE IS GIVEN ITS OWN HALF TO NAME, count AND list: swapping just the lists leaves every span
        // check green.
        assertTrue(inTrack.contains(trackKey) && inTrack.contains("track.size()")
            && inTrack.contains("someOf(track)"),
            "the branch that found unmeasured track does not log the track sentence with the track to"
            + " name, so the operator is told about the wrong half of the gate: " + inTrack);

        assertFalse(inTrack.contains(trainKey) || inTrack.contains("someOf(trains)"),
            "the branch that found unmeasured track names the TRAINS: an operator with an unmeasured"
            + " rail is told to set a train length, over a list of locomotives that are all measured");

        assertTrue(inTrains.contains(trainKey) && inTrains.contains("trains.size()")
            && inTrains.contains("someOf(trains)"),
            "the branch that found a train with no length does not log the train sentence with the"
            + " trains to name: " + inTrains);

        assertFalse(inTrains.contains(trackKey) || inTrains.contains("someOf(track)"),
            "the branch that found a train with no length names the TRACK, so an operator is told to"
            + " measure track he has already measured");
    }

    /**
     * AND THE START BUTTON ASKS THE SAME QUESTION, kept for the reason only it gives: a TRAIN length is written on the
     * live layout by `applyTrainLength` and `GraphLocAssign.commitChanges`, neither of which rebuilds anything, so a
     * length cleared after the checkbox was unticked puts `behind >= trainLength` back to `0 >= 0`.  A text claim,
     * because pressing Start here would dispatch Adam's trains: the handler asks before it hands anything to
     * `runLocomotives`.
     *
     * MUTATION: delete the call from `startAutonomyActionPerformed` and this goes red; move it below
     * the `runLocomotives` call and it goes red too, which is the half a `contains` would miss.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testStartAsksBeforeItDispatchesAnything() throws Exception
    {
        String source = SourceOf.declaration("private void startAutonomyActionPerformed(");

        int dispatch = source.indexOf("runLocomotives();");

        assertTrue(dispatch > 0,
            "the Start handler no longer calls runLocomotives, so what this rule is about has moved"
            + " and the question has to move with it");

        int asks = source.indexOf("keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()");

        assertTrue(asks > 0 && asks < dispatch,
            "Start does not ask whether the railway could release track under a train before it"
            + " dispatches.  A train length can be cleared on the live layout long after the checkbox"
            + " was unticked honestly - applyTrainLength takes 0, and GraphLocAssign defaults to it -"
            + " and then every edge is handed back as that train's head passes it");
    }

    /**
     * EVERY DOOR THAT DISPATCHES A TRAIN ASKS THE GATE, AND THIS IS THE LIST: the release lives in
     * `Layout.executePathInternal`, which Execute Timetable, Return Home and the hand dispatch reach without passing
     * Start.  A source-shape rule can only know the doors it names, so the failure message says to add a new door here
     * as well.
     *
     * MUTATION: delete the call from any one of the four and this names that one.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testEveryDispatchDoorAsksTheGate() throws Exception
    {
        String gate = "keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()";

        // Each door's own text, found by its declaration, so a dispatch elsewhere cannot stand in for it.
        String[][] doors =
        {
            {"TrainControlUI", "private void startAutonomyActionPerformed(", "runLocomotives();"},
            {"TrainControlUI", "private void executeTimetableActionPerformed(", "executeTimetable();"},
            {"ReturnHomeController", "    void requestReturnToHome()", "executeTimetable("},
            // THE TWO HAND DOORS ARE ONE: both send through `sendATrainByHand`, which dispatches through
            // `executePathByHand`
            {"TrainControlUI", "void sendATrainByHand(", "executePathByHand("},
        };

        for (String[] door : doors)
        {
            String source = SourceOf.declaration(door[1]);

            int dispatch = source.indexOf(door[2]);

            assertTrue(dispatch > 0, door[0] + " reaches " + door[1]
                + " and no longer dispatches with " + door[2]
                + " - if the dispatch moved, this rule has to move with it");

            int asks = source.indexOf(gate);

            assertTrue(asks > 0 && asks < dispatch,
                door[0] + ": the door at " + door[1] + " dispatches a train without asking whether"
                + " the railway could release track under one.  A train length can be cleared on the"
                + " live layout long after Atomic Routes was unticked honestly, and then every edge"
                + " is handed back as that train's head passes it.  Add the call - and if you are"
                + " adding a new door, add it to the list in this method too");
        }
    }

    /**
     * Execute Timetable asks the gate only once its own refusals have passed: the gate writes the setting, so a refused
     * press must not have switched the running railway to atomic.
     *
     * MUTATION: move the call back above the refusals and this fails.
     *
     * @throws Exception from reading the source
     */
    @Test
    public void testExecuteTimetableAsksTheGateAfterItsRefusals() throws Exception
    {
        String handler = SourceOf.declaration("private void executeTimetableActionPerformed(");

        int asks = handler.indexOf("keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()");
        int busy = handler.indexOf("RunGates.WAIT_FOR_THE_TRAINS");
        int empty = handler.indexOf("timetable.ui.errorNoEntriesCaptureCommandsFirst");

        assertTrue(busy > 0 && empty > 0, "precondition: the handler no longer refuses a busy railway or an empty"
            + " timetable, so there is nothing for the gate to come after");

        assertTrue(asks > busy && asks > empty, "Execute Timetable asks the Atomic Routes gate before it refuses - so a"
            + " refused press still switches the running railway's setting (GUI-A1)");

        // AND AFTER THE TWO REFUSALS BELOW THOSE: the conditional-route warning, declined, and a train not at its
        // entry's start square.
        int warning = handler.indexOf("route.ui.confirmConditionalRoutesActiveProceed");
        int notAtStart = handler.indexOf("timetable.ui.infoLocomotiveMustBeMovedToStart");

        assertTrue(warning > 0 && notAtStart > 0, "precondition: the handler no longer warns about conditional routes or"
            + " refuses a train away from its start");

        assertTrue(asks > warning && asks > notAtStart, "Execute Timetable asks the Atomic Routes gate before its"
            + " conditional-route warning or its start-square check - a press declined there still switches the setting"
            + " (TDU2-C2)");
    }

    /**
     * Start and Return Home ask the gate only once their own refusals have passed, as Execute Timetable does: "a
     * refused press should change nothing".
     *
     * MUTATION: move either call back above its refusals and this fails.
     *
     * @throws Exception from reading the source
     */
    @Test
    public void testStartAndReturnHomeAskTheGateAfterTheirRefusals() throws Exception
    {
        String gate = "keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()";

        String returnHome = SourceOf.declaration("    void requestReturnToHome()");

        int homeBusy = returnHome.indexOf("HomeStaging.Outcome.LOCOMOTIVES_RUNNING");

        // THE POWER IS THE ONE GATE'S: `refusedToSendATrain` asks it for every door that sends a train
        int homePower = returnHome.indexOf("refusedToSendATrain(");
        int homeGate = returnHome.indexOf(gate);

        assertTrue(homeBusy > 0 && homePower > 0 && homeGate > 0, "precondition: Return Home no longer refuses a busy"
            + " railway and one with the power off, or no longer asks the gate");

        assertTrue(homeGate > homeBusy && homeGate > homePower, "Return Home asks the Atomic Routes gate before it"
            + " refuses - so a refused press still switches the running railway's setting (TDU-C8, GUI-A1)");

        // AND AFTER ITS PLAN IS KNOWN TO BE POSSIBLE: no plan, or an impossible one, is a refusal too.
        int possible = returnHome.indexOf("plan.isPossible()");

        assertTrue(possible > 0, "precondition: Return Home no longer refuses an impossible plan");

        assertTrue(homeGate > possible, "Return Home asks the Atomic Routes gate before it knows its plan is possible - a"
            + " press refused for having no plan still switches the setting (TDU2-C2)");

        String startDoor = SourceOf.declaration("private void startAutonomyActionPerformed(");

        int power = startDoor.indexOf("refusedToSendATrain(");
        int none = startDoor.indexOf("autolayout.ui.infoPleaseAddLocomotivesToGraph");
        int asks = startDoor.indexOf(gate);

        assertTrue(power > 0 && none > 0 && asks > 0, "precondition: Start no longer refuses with the power off or with"
            + " no trains, or no longer asks the gate");

        assertTrue(asks > power && asks > none, "Start asks the Atomic Routes gate before it refuses - so a refused press"
            + " still switches the running railway's setting (TDU-C8, GUI-A1)");
    }

    /**
     * A gate that fails stops every run door, and gives its button back: a door that carried on past a failure would
     * run non-atomic over unmeasured track.  One door at a time, each named when it fails.
     *
     * MUTATION: take the `return` out of any door's catch, or Execute Timetable's `setEnabled(true)`, and this fails
     * naming the door.
     *
     * @throws Exception reading the source
     */
    @Test
    public void testAGateThatFailsStopsEveryRunDoor() throws Exception
    {
        String gate = "keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()";

        String[][] doors = {
            {"Start", "private void startAutonomyActionPerformed(", null},
            {"Return Home", "    void requestReturnToHome()", null},
            {"Execute Timetable", "private void executeTimetableActionPerformed(", "this.executeTimetable.setEnabled(true);"}};

        for (String[] door : doors)
        {
            String region = SourceOf.declaration(door[1]);

            int asks = region.indexOf(gate);

            assertTrue(asks > 0, "precondition: " + door[0] + " no longer asks the Atomic Routes gate");

            int caught = region.indexOf("catch (", asks);

            assertTrue(caught > asks && caught - asks < 400, door[0] + " does not catch a failure of its Atomic Routes gate"
                + " (TDU2-C2, TDU3-C4)");

            int body = region.indexOf("{", caught);
            int closes = region.indexOf("}", body);

            String handler = region.substring(body, closes);

            assertTrue(handler.contains("return;"), door[0] + " carries on when its Atomic Routes gate fails - the run goes"
                + " on without the question answered (TDU2-C2)");

            if (door[2] != null)
            {
                assertTrue(handler.contains(door[2]), door[0] + " keeps its button greyed when its Atomic Routes gate fails"
                    + " (TDU3-C4)");
            }
        }
    }

    /** How many times one string occurs in another. */
    private static int occurrences(String text, String needle)
    {
        int count = 0;

        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) count++;

        return count;
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

    /** A rail whose far end can reach a destination, so taking its length away is something the gate should notice. */
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
