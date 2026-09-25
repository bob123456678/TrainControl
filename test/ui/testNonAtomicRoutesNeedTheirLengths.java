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
 * the checkbox's question and the train claim goes red, and from `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack`
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
     * AND A SETUP THAT LOADS NON-ATOMIC WITH A TRAIN THAT HAS NO LENGTH COMES UP ATOMIC (VD15-T4).
     *
     * The file door has the same two halves as the checkbox, and only the track one was asked about -
     * so deleting `&& trains.isEmpty()` from `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` left every
     * claim in this class green, and `autolayout.warnAtomicRoutesKeptOnTrains` was a string no test
     * ever reached.  A train of no length releases the whole railway under itself however well the
     * track is measured, so this is the half that does not depend on the operator's rails at all.
     *
     * The door-open control is the one in
     * `testALoadedSetupCannotRunNonAtomicOverTrackItCouldRelease`, which watches a deliberate OFF
     * survive a load on a railway that is put right - the same door, asked when nothing is wrong.
     * This depends on that method so that the control is known to have run (VD16-T6); it is not
     * repeated here.
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testALoadedSetupCannotRunNonAtomicOverTrackItCouldRelease")
    public void testALoadedSetupCannotRunNonAtomicWithATrainThatHasNoLength() throws Exception
    {
        putTheRailwayRight();

        // WHICH HALF THIS IS ABOUT IS HELD BY `testTheFileDoorLogsTheHalfItFound`, NOT HERE (VD17-T3).
        //
        // There was a precondition on this line asserting that the track half was quiet.  It could not
        // fail: `putTheRailwayRight()` above gives every zero-length edge a length by walking the same
        // collection the counter reads, so "no track could be released" is true by construction two
        // lines later, and no change to the production code could redden it.  The door forces the
        // setting back on for either half and says which in the log, so the log is where the halves
        // are told apart - and that rule is a claim of its own in this class.

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
     * THE FILE DOOR LOGS THE HALF IT FOUND, and each half has its own sentence (VD16-T7).
     *
     * Swapping `autolayout.warnAtomicRoutesKeptOn` and `autolayout.warnAtomicRoutesKeptOnTrains` in
     * `keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack` leaves every behavioural claim in this
     * class green - the setting comes back on either way - and the operator is told to measure track
     * when what is wrong is a train's length, which is a remedy for a fault he does not have.
     *
     * **A TEXT CLAIM, because the log has no reader.**  `MarklinControlStation.log` hands the line to
     * the window, which inserts it into a private `JTextArea` with no accessor; reaching it would mean
     * reflection on a field the GUI builder owns, or a getter added to production for this test alone.
     * What is pinned instead is the pairing: the track branch names the track key and the train branch
     * names the train key, in a method short enough to read whole.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheFileDoorLogsTheHalfItFound() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/TrainControlUI.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        int at = source.indexOf("public void keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()");

        assertTrue(at > 0, "the file door has been renamed or moved, so this rule is about nothing");

        String body = source.substring(at, source.indexOf("loadAutoLayoutSettings();", at));

        // THE CLOSING QUOTE IS PART OF THE NEEDLE, so the track key is not found inside the train
        // one - built rather than written, because an escaped quote in a patch is one more thing
        // to get wrong.
        String quote = String.valueOf((char) 34);

        String trackKey = "autolayout.warnAtomicRoutesKeptOn" + quote;
        String trainKey = "autolayout.warnAtomicRoutesKeptOnTrains" + quote;

        // THE BRANCHES, NOT THE GAPS (VD17-T1).
        //
        // The first version of this measured how far each key sat from the nearest matching `.size()`
        // and called that the pairing.  It is not: swap the two `if` CONDITIONS and leave the two
        // `logf` lines exactly where they are, and every distance is byte for byte what it was - while
        // an operator with one unmeasured rail is told "0 locomotives have no train length" over an
        // empty list, which is the fault this rule exists to prevent.  What says which half a sentence
        // is about is the branch it sits in, so that is what is read.
        int trackBranch = body.indexOf("if (!track.isEmpty())");
        int trainBranch = body.indexOf("if (!trains.isEmpty())");

        assertTrue(trackBranch > 0 && trainBranch > trackBranch,
            "the file door's two branches are not where this rule can find them - the track branch is"
            + " at " + trackBranch + " and the train branch at " + trainBranch + ".  If they have been"
            + " reordered or rewritten, this rule has to be rewritten with them, because what it holds"
            + " is that each sentence is logged from the branch that found the fault it describes");

        String inTrack = body.substring(trackBranch, trainBranch);
        String inTrains = body.substring(trainBranch);

                // ONCE EACH, IN THE WHOLE METHOD (VD18-T4).  Reading only the two spans left a sentence logged
        // ABOVE the first branch invisible to every assertion here - an unconditional line telling an
        // operator with one unmeasured rail that 0 locomotives have no train length, which is the
        // fault this rule exists to prevent, sitting in neither span and so in neither check.
        assertEquals(occurrences(body, trackKey), 1,
            "the track sentence is logged " + occurrences(body, trackKey) + " times in this method."
            + "  Once, from the branch that found unmeasured track - a second one is either a"
            + " duplicate or a line outside both branches, which reports a fault the door has not"
            + " found");

        assertEquals(occurrences(body, trainKey), 1,
            "the train sentence is logged " + occurrences(body, trainKey) + " times in this method,"
            + " and it belongs only to the branch that found a train with no length");

        // AND EACH SENTENCE IS GIVEN ITS OWN HALF TO NAME, count AND list.  Swapping just the lists -
        // someOf(trains) in the track branch - leaves every span check above green while each sentence
        // names the other half's remedy.
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
     * AND THE START BUTTON ASKS THE SAME QUESTION, WHICH IS THE DOOR THAT MATTERS (VD16-B2).
     *
     * The other three doors cannot cover it.  An edge length is only ever written by `parseAuto`, and
     * both file doors re-ask afterwards - but a TRAIN length is written on the live layout by
     * `applyTrainLength` (whose zero means "not set") and by `GraphLocAssign.commitChanges`, neither
     * of which rebuilds anything.  So the checkbox can be unticked honestly over a measured railway
     * and a length cleared a minute later puts `behind >= trainLength` back to `0 >= 0`.
     *
     * **A TEXT CLAIM, because pressing Start on this fixture would dispatch Adam's trains.**  What it
     * pins is that the handler asks before it hands anything to `runLocomotives` - the behaviour the
     * call produces is the file door's, and the claims above are what hold that.
     *
     * MUTATION: delete the call from `startAutonomyActionPerformed` and this goes red; move it below
     * the `runLocomotives` call and it goes red too, which is the half a `contains` would miss.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testStartAsksBeforeItDispatchesAnything() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/TrainControlUI.java").toPath()),
            java.nio.charset.StandardCharsets.UTF_8);

        int at = source.indexOf("private void startAutonomyActionPerformed(");

        assertTrue(at > 0, "the Start handler has been renamed, so this rule is about nothing");

        int dispatch = source.indexOf("runLocomotives();", at);

        assertTrue(dispatch > at,
            "the Start handler no longer calls runLocomotives, so what this rule is about has moved"
            + " and the question has to move with it");

        int asks = source.indexOf("keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()", at);

        assertTrue(asks > at && asks < dispatch,
            "Start does not ask whether the railway could release track under a train before it"
            + " dispatches.  A train length can be cleared on the live layout long after the checkbox"
            + " was unticked honestly - applyTrainLength takes 0, and GraphLocAssign defaults to it -"
            + " and then every edge is handed back as that train's head passes it");
    }

    /**
     * EVERY DOOR THAT DISPATCHES A TRAIN ASKS THE GATE, AND THIS IS THE LIST (GS-B1).
     *
     * The claim above it pinned Start, on the reasoning that Start was the choke point because no
     * edge is released until autonomy runs.  That was wrong about WHERE the release lives: it is in
     * `Layout.executePathInternal`, and Execute Timetable, Return Home and the two hand dispatches
     * all reach it without passing Start.  Return Home is the worst of them, because several trains
     * move at once and the one with no train length hands back the track under itself while the
     * others are being routed around it.
     *
     * **A LIST IS A WEAK GUARD and it is written as one deliberately.**  A source-shape rule can only
     * know the doors it names, so the failure message says to add the new door here as well.  What it
     * buys is that the next door cannot be added in silence, which is how this one came to exist.
     *
     * MUTATION: delete the call from any one of the five and this names that one (VD17-T7: it said
     * four, over a five-entry list).
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testEveryDispatchDoorAsksTheGate() throws Exception
    {
        String gate = "keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()";

        String[][] doors =
        {
            {"src/org/traincontrol/gui/TrainControlUI.java",
                "private void startAutonomyActionPerformed(", "runLocomotives();"},
            {"src/org/traincontrol/gui/TrainControlUI.java",
                "private void executeTimetableActionPerformed(", "executeTimetable();"},
            {"src/org/traincontrol/gui/TrainControlUI.java",
                "public void requestReturnToHome()", "executeTimetable();"},
            {"src/org/traincontrol/gui/AutoLocomotiveStatus.java",
                "ManualReversalPrompt.forJourney(", "executePath("},
            {"src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java",
                "ManualReversalPrompt.forJourney(", "executePath("},
        };

        for (String[] door : doors)
        {
            String source = new String(java.nio.file.Files.readAllBytes(
                new java.io.File(door[0]).toPath()), java.nio.charset.StandardCharsets.UTF_8);

            int from = source.indexOf(door[1]);

            assertTrue(from > 0, door[0] + " no longer has " + door[1]
                + ", so this rule is about nothing.  Point it at wherever that door went");

            int dispatch = source.indexOf(door[2], from);

            assertTrue(dispatch > from, door[0] + " reaches " + door[1]
                + " and no longer dispatches with " + door[2]
                + " - if the dispatch moved, this rule has to move with it");

            int asks = source.indexOf(gate, from);

            assertTrue(asks > from && asks < dispatch,
                door[0] + ": the door at " + door[1] + " dispatches a train without asking whether"
                + " the railway could release track under one.  A train length can be cleared on the"
                + " live layout long after Atomic Routes was unticked honestly, and then every edge"
                + " is handed back as that train's head passes it.  Add the call - and if you are"
                + " adding a new door, add it to the list in this method too");
        }
    }

    /**
     * Execute Timetable asks the gate only once its own refusals have passed (GUI-A1).
     *
     * The gate writes the setting.  Asked first, a press refused as "wait for active locomotives to stop" switched the
     * running railway to atomic on its way to being refused - and a refused press should change nothing.
     *
     * MUTATION: move the call back above the refusals and this fails.
     *
     * @throws Exception from reading the source
     */
    @Test
    public void testExecuteTimetableAsksTheGateAfterItsRefusals() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), java.nio.charset.StandardCharsets.UTF_8);

        int door = source.indexOf("private void executeTimetableActionPerformed(");
        int end = source.indexOf("GEN-LAST:event_executeTimetableActionPerformed", door);

        assertTrue(door > 0 && end > door, "precondition: Execute Timetable's handler is not where this looks for it");

        String handler = source.substring(door, end);

        int asks = handler.indexOf("keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()");
        int busy = handler.indexOf("autolayout.ui.errorWaitForActiveLocomotivesToStop");
        int empty = handler.indexOf("timetable.ui.errorNoEntriesCaptureCommandsFirst");

        assertTrue(busy > 0 && empty > 0, "precondition: the handler no longer refuses a busy railway or an empty"
            + " timetable, so there is nothing for the gate to come after");

        assertTrue(asks > busy && asks > empty, "Execute Timetable asks the Atomic Routes gate before it refuses - so a"
            + " refused press still switches the running railway's setting (GUI-A1)");

        // AND AFTER THE TWO REFUSALS BELOW THOSE (TDU2-C2): the conditional-route warning, declined, and a train not at
        // its entry's start square.
        int warning = handler.indexOf("route.ui.confirmConditionalRoutesActiveProceed");
        int notAtStart = handler.indexOf("timetable.ui.infoLocomotiveMustBeMovedToStart");

        assertTrue(warning > 0 && notAtStart > 0, "precondition: the handler no longer warns about conditional routes or"
            + " refuses a train away from its start");

        assertTrue(asks > warning && asks > notAtStart, "Execute Timetable asks the Atomic Routes gate before its"
            + " conditional-route warning or its start-square check - a press declined there still switches the setting"
            + " (TDU2-C2)");
    }

    /**
     * Start and Return Home ask the gate only once their own refusals have passed, as Execute Timetable does (TDU-C8).
     *
     * GUI-A1 moved Execute Timetable's call below its refusals - *"a refused press should change nothing"* - and the two
     * sibling doors kept the old order: Return Home refused as "locomotives running", or Start refused for the power,
     * switched the railway to atomic on the way to being refused.
     *
     * MUTATION: move either call back above its refusals and this fails.
     *
     * @throws Exception from reading the source
     */
    @Test
    public void testStartAndReturnHomeAskTheGateAfterTheirRefusals() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(
            new java.io.File("src/org/traincontrol/gui/TrainControlUI.java").toPath()), java.nio.charset.StandardCharsets.UTF_8);

        String gate = "keepAtomicRoutesOnWhileTheRailwayCouldReleaseTrack()";

        int home = source.indexOf("public void requestReturnToHome()");
        int homeEnd = source.indexOf("\n    }", home);

        assertTrue(home > 0 && homeEnd > home, "precondition: Return Home's door is not where this looks for it");

        String returnHome = source.substring(home, homeEnd);

        int homeBusy = returnHome.indexOf("HomeStaging.Outcome.LOCOMOTIVES_RUNNING");
        int homePower = returnHome.indexOf("autolayout.ui.powerOnToStart");
        int homeGate = returnHome.indexOf(gate);

        assertTrue(homeBusy > 0 && homePower > 0 && homeGate > 0, "precondition: Return Home no longer refuses a busy"
            + " railway and one with the power off, or no longer asks the gate");

        assertTrue(homeGate > homeBusy && homeGate > homePower, "Return Home asks the Atomic Routes gate before it"
            + " refuses - so a refused press still switches the running railway's setting (TDU-C8, GUI-A1)");

        // AND AFTER ITS PLAN IS KNOWN TO BE POSSIBLE (TDU2-C2): no plan, or an impossible one, is a refusal too.
        int possible = returnHome.indexOf("plan.isPossible()");

        assertTrue(possible > 0, "precondition: Return Home no longer refuses an impossible plan");

        assertTrue(homeGate > possible, "Return Home asks the Atomic Routes gate before it knows its plan is possible - a"
            + " press refused for having no plan still switches the setting (TDU2-C2)");

        int start = source.indexOf("private void startAutonomyActionPerformed(");
        int end = source.indexOf("GEN-LAST:event_startAutonomyActionPerformed", start);

        assertTrue(start > 0 && end > start, "precondition: Start's handler is not where this looks for it");

        String startDoor = source.substring(start, end);

        int power = startDoor.indexOf("autolayout.ui.powerOnToStart");
        int none = startDoor.indexOf("autolayout.ui.infoPleaseAddLocomotivesToGraph");
        int asks = startDoor.indexOf(gate);

        assertTrue(power > 0 && none > 0 && asks > 0, "precondition: Start no longer refuses with the power off or with"
            + " no trains, or no longer asks the gate");

        assertTrue(asks > power && asks > none, "Start asks the Atomic Routes gate before it refuses - so a refused press"
            + " still switches the running railway's setting (TDU-C8, GUI-A1)");

        // AND A GATE THAT FAILS STOPS START (TDU2-C2): the gate is what keeps a run atomic over unmeasured track, and a
        // Start that carried on past a failure of it would run non-atomic over it.
        int started = startDoor.indexOf("started.set(true)", asks);

        assertTrue(started > asks && startDoor.substring(asks, started).contains("return;"), "Start carries on when its"
            + " Atomic Routes gate fails - the run starts without the question being answered (TDU2-C2)");
    }

    /**
     * How many times one string occurs in another.
     *
     * @param text the whole
     * @param needle what to count
     * @return the count
     */
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
