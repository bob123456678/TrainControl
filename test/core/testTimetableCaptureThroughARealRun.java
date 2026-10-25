package core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomyRefreshCallback;
import org.traincontrol.base.Accessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Capture, tested the way the operator meets it: a real configuration, real autonomy, real trains.
 *
 * Adam: "Make sure you validate via REALISTIC tests for the timetable issue that still persists."  The earlier
 * `testTimetableCapture` built its own two-point Layout, set the flag on it, handed `executePath` a path it had
 * assembled, and could only see the model.  The capture worked throughout; what had stopped was the REDRAW, because
 * the registration that repainted the timetable was deleted with the GraphStream window.
 *
 * So this does the whole thing:
 *
 * - a real configuration, through `model.parseAuto`, which is what loading one does
 * - capture switched on where the window switches it, on `model.getAutoLayout()`
 * - `AutonomyRefreshCallback.attach`, which is the same call the window makes
 * - `runLocomotives()`, so autonomy picks its own paths rather than being handed one
 *
 * and then asks the two questions that were separately false: did anything get captured, and was anybody told.  The
 * old test is kept as a faster unit test of the flag.
 */
public class testTimetableCaptureThroughARealRun
{
    private static final Accessory.accessoryDecoderType MM2 = Accessory.accessoryDecoderType.MM2;

    /** The names `autonomy_sanity.json` places; parseAuto places only locomotives that already exist. */
    private static final String[] LOCO_NAMES =
    {
        "Auto Test Loc 1", "Auto Test Loc 2", "Auto Test Loc 3"
    };

    private static final long RUN_MS = 45000;

    /** Where this test puts its train.  A station, so autonomy will dispatch from it. */
    private static final String START = "Station 1";

    /**
     * How long to wait for autonomy to dispatch ANYTHING before calling it a fault (OB-114).
     *
     * Far above what a dispatch takes, and deliberately not derived from RUN_MS, which is how long a run is watched
     * for.  Raised to 480 seconds after it ran out twice in consecutive batteries on a busy machine and passed alone.
     * The wait ends the moment a locomotive moves, so a higher ceiling is only ever spent on a run that is failing
     * anyway; no wall-clock number can tell a slow machine from a railway that will never move.
     */
    private static final long STARTUP_CEILING_MS = 480000;

    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulatedInDebug();

        // Not connected, and simulating packets, so accessory actuations are confirmed and the trains
        // actually move - the same arrangement testAutonomySimulationSanity uses.
        model.setNetworkCommState(false);
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        for (int at = 0; at < LOCO_NAMES.length; at++)
        {
            model.newMM2Locomotive(LOCO_NAMES[at], 61 + at);
        }

        model.newSwitch(1, MM2, false);
        model.newSwitch(2, MM2, false);
        model.newSwitch(3, MM2, false);
        model.newSwitch(4, MM2, false);
        model.newSignal(5, MM2, false);
        model.newSignal(6, MM2, false);
        model.newSignal(7, MM2, false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (model != null && model.hasAutoLayout()) model.getAutoLayout().stopLocomotives();
    }

    /**
     * With capture on, a real autonomy run fills the timetable AND says so.  Two assertions because two things were
     * broken and only one was visible: the capture half works, which tells "the table is empty" apart from no redraw.
     */
    @Test
    public void testARealRunCapturesAndAnnouncesIt() throws Exception
    {
        Layout layout = loadedConfiguration();

        // Set in BOTH directions: parseAuto preserves the flag across a rebuild, so whichever test TestNG runs first
        // decides what the other starts with.
        layout.setTimetableCapture(false);

        assertFalse(layout.isTimetableCapture(), "capture did not switch off");

        // As the window does: on the model's own layout, before starting.
        layout.setTimetableCapture(true);

        // And the notification the window attaches, by the same call.
        final AtomicInteger announcements = new AtomicInteger();

        AutonomyRefreshCallback.attach(layout, () -> announcements.incrementAndGet());

        assertTrue(layout.getTimetable().isEmpty(), "the fixture started with a timetable already");

        model.go();
        layout.runLocomotives();

        boolean moved = false;

        long deadline = System.currentTimeMillis() + RUN_MS;

        while (System.currentTimeMillis() < deadline)
        {
            if (!layout.getActiveLocomotives().isEmpty()) moved = true;

            // Enough captured to be sure it is not a single lucky dispatch.
            if (layout.getTimetable().size() >= 3 && announcements.get() > 0) break;

            Thread.sleep(200);
        }

        layout.stopLocomotives();

        assertTrue(moved,
            "no locomotive ever moved, so this test proved nothing about capture.  The fixture is "
            + "broken rather than the feature - check that the three locomotives exist and that "
            + "parseAuto placed them");

        assertTrue(layout.getTimetable().size() >= 3,
            "a real autonomy run with capture ON put " + layout.getTimetable().size() + " entries in "
            + "the timetable.  This is the half Adam reported twice, and the half that was never "
            + "actually broken - if it fails now, capture itself has gone");

        // The half that WAS broken, and that no model-level test could see.
        assertTrue(announcements.get() > 0,
            "nothing was told that a path had started or finished.  The entries are in the timetable "
            + "and the panel showing them is never repainted, which is exactly what the operator "
            + "reports as \"nothing gets captured\" - the table stays empty on screen while the data "
            + "behind it is perfectly correct.  Deleted with the GraphStream window in d8db4879");
    }

    /**
     * With capture off, a run of the same length captures nothing: the mutation check for the test above, which a
     * capture ignoring the flag, or a fixture arriving with entries, would pass.
     */
    @Test
    public void testARealRunCapturesNothingWithCaptureOff() throws Exception
    {
        Layout layout = loadedConfiguration();

        // Set explicitly: parseAuto PRESERVES the flag across a rebuild (an ordinary rebuild must not switch it off),
        // so after the test above this fixture arrives with capture ON.
        layout.setTimetableCapture(false);

        assertFalse(layout.isTimetableCapture(), "capture did not switch off");

        model.go();
        layout.runLocomotives();

        // Waits for a train to MOVE, not for a fixed number of seconds (OB-114): on a loaded machine autonomy does not
        // always dispatch within a fixed window.  The ceiling is far above a dispatch; nothing is skipped, since a
        // minute of nothing moving is a real fault and a skip that fires routinely goes unnoticed.
        boolean moved = false;

        long deadline = System.currentTimeMillis() + STARTUP_CEILING_MS;

        while (!moved && System.currentTimeMillis() < deadline)
        {
            if (!layout.getActiveLocomotives().isEmpty()) moved = true;
            else Thread.sleep(200);
        }

        // And then let it actually run for a while, which is the part capture would have recorded.
        if (moved) Thread.sleep(RUN_MS / 2);

        // ASKED BEFORE THE STOP: `isAutoRunning()` is the field `stopLocomotives` clears, so read afterwards it says
        // "Auto running: false" whatever happened, and several of `explainCannotStart`'s rules are fenced on it too.
        // Only on the failing path.
        String why = moved ? "" : whyNothingMoved(layout, "with autonomy still running");

        layout.stopLocomotives();

        // AND AGAIN WITH IT STOPPED, because the running answer is a SUBSTITUTION (MT-374): `explainDestinations`
        // replaces the real reason with "Blocked by a train or a route in progress" whenever `isAutoRunning()`, since
        // the reason it would quote comes from a static every running thread writes.  Both readings are printed.
        if (!moved) why = why + whyNothingMoved(layout, "after being stopped, so the real refusal is"
            + " no longer substituted - but the rules fenced on isAutoRunning now answer about a"
            + " stopped railway");

        assertTrue(moved, "no locomotive moved in " + (STARTUP_CEILING_MS / 1000) + " seconds, so "
            + "nothing was declined and nothing is proved." + why);

        assertTrue(layout.getTimetable().isEmpty(),
            "trains ran with capture switched OFF and the timetable filled anyway, so the flag is "
            + "not being consulted: " + layout.getTimetable());
    }

    /**
     * What the railway says about each train that did not start.
     *
     * `explainCannotStart` is the answer the application gives an operator who presses Start and sees nothing happen;
     * it is only reached when the assertion is already lost.  Deliberately not asserted ON: a diagnosis, not a
     * specification.
     *
     * @param layout the railway that would not move
     * @param when which reading this is, running or stopped, because the two do not agree (MT-374)
     * @return the reasons, ready to append to a failure
     */
    private String whyNothingMoved(Layout layout, String when)
    {
        StringBuilder why = new StringBuilder("\n  What the railway says about each train, " + when
            + ":");

        try
        {
            for (org.traincontrol.base.Locomotive loc : layout.getLocomotivesToRun())
            {
                String reason = layout.explainCannotStart(loc);

                why.append("\n    ").append(loc.getName()).append(": ")
                    .append(reason == null ? "free to be given a route" : reason);

                // AND WHERE IT COULD ACTUALLY GO: `explainCannotStart` asks four things about the TRAIN, so a railway
                // where every train is blocked by the others reads as every train being free.
                if (reason == null)
                {
                    java.util.Map<String, String> destinations = layout.explainDestinations(loc);

                    int open = 0;

                    for (String each : destinations.keySet())
                    {
                        if (destinations.get(each) == null) open++;
                    }

                    why.append(" - ").append(open).append(" of ").append(destinations.size())
                        .append(" destinations open");

                    if (open == 0) why.append(", so it has nowhere to go: ").append(destinations);
                }
            }

            why.append("\n  Auto running: ").append(layout.isAutoRunning());

            // AND WHETHER THIS IS THE LAYOUT THAT WOULD DISPATCH (MT-374): a retired Layout refuses to dispatch
            // SILENTLY (`loadedConfiguration`), and a test watching one sees a running railway with open destinations
            // that never moves, which nothing printed above can tell from a busy machine.
            why.append("\n  This layout is the one the model holds: ")
                .append(model != null && model.getAutoLayout() == layout);

            why.append("\n  Active on it: ").append(layout.getActiveLocomotives());

            why.append("\n  Trains it would run: ").append(layout.getLocomotivesToRun());
        }
        catch (RuntimeException e)
        {
            // A failure here would replace a real failure with this one, and this is only ever
            // reached when the test has already lost.
            why.append("\n    (could not be asked: ").append(e).append(")");
        }

        return why.toString();
    }

    /**
     * How long to wait for the railway to go quiet before replacing it (OB-199); only waited out when something is
     * wrong, so a hung run says so rather than hanging.
     */
    private static final long QUIET_MS = 60_000;

    /**
     * Waits for the layout in force to stop driving trains, before a new one replaces it (OB-199).
     *
     * `stopLocomotives` stops trains gracefully, at their next station, so it returns long before the railway is quiet.
     * The locomotives belong to the MODEL, so a second configuration parsed over a still-running one asks a fleet
     * somebody else is driving: nothing can be dispatched and the test waits until its ceiling.
     */
    private static void quiesce() throws Exception
    {
        Layout previous = model.getAutoLayout();

        if (previous == null) return;

        previous.stopLocomotives();

        long deadline = System.currentTimeMillis() + QUIET_MS;

        while (System.currentTimeMillis() < deadline)
        {
            if (!previous.isRunning() && previous.getActiveLocomotives().isEmpty()) return;

            Thread.sleep(200);
        }

        fail("the railway was still running " + (QUIET_MS / 1000) + " seconds after being asked to"
            + " stop - " + previous.getActiveLocomotives().size() + " locomotive(s) still active."
            + "  A new configuration parsed on top of that one would be asking for trains this one is"
            + " still driving, which is OB-199");
    }

    private static Layout loadedConfiguration() throws Exception
    {
        // THE RAILWAY IN FORCE IS STOPPED FIRST, and waited for (OB-199).  See `quiesce`.
        quiesce();

        String json = new BufferedReader(new InputStreamReader(
            testTimetableCaptureThroughARealRun.class
                .getResource("/autonomy_sanity.json").openStream()))
            .lines().collect(Collectors.joining("\n"));

        model.parseAuto(json);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the fixture did not parse: " + Layout.getLastError());
        assertTrue(layout.isValid(), "the fixture is invalid: " + Layout.getLastError());

        emptyTheRailway(layout);

        clearTheSensors(layout);

        putOneTrainOn(layout);

        return layout;
    }

    /**
     * Puts every sensor back to clear, the other half of starting from a known railway (MT-374).
     *
     * `emptyTheRailway` puts the trains back but nothing put the SENSORS back, so a feedback left occupied by the
     * previous run stayed occupied and a destination whose approach expects it clear was shut for ever ("Station 2 =
     * Expects feedback 5 to be clear").  It is load-dependent because a busy machine cuts the first run off mid-leg.
     * Only sensors the RAILWAY knows about, since clearing others would be the same fault the other way.
     *
     * @param layout the freshly parsed railway
     */
    private static void clearTheSensors(Layout layout)
    {
        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.getS88() == null) continue;

            model.setFeedbackState(point.getS88(), false);
        }
    }

    /**
     * Takes every train off, so this test starts from a railway nobody else has arranged.
     *
     * Adam: "let's get it to start with an empty layout onto which you place locomotives, with other locomotives
     * removed."  `autonomy_sanity.json` places three trains on a railway with four destinations and one single-track
     * corridor, so it ran at capacity minus one with one train able to move at a time: fine for autonomy's
     * contention handling, the arrangement least likely to produce a moving train on a loaded machine.  That made this
     * class fail batteries with "no locomotive moved in 480 seconds", and `explainCannotStart` could not say why.
     *
     * Through `moveLocomotive(null, ..., true)`, the door the diagram's Cut uses, so the state is one the application
     * can also be in.
     *
     * @param layout the freshly parsed railway
     */
    private static void emptyTheRailway(Layout layout) throws Exception
    {
        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null)
            {
                layout.moveLocomotive(null, point.getName(), true);
            }
        }

        assertTrue(layout.getLocomotivesToRun().isEmpty(),
            "the railway still has " + layout.getLocomotivesToRun().size() + " train(s) on it after "
            + "being emptied, so this test is not starting from the state it says it is: "
            + layout.getLocomotivesToRun());
    }

    /**
     * And puts back exactly the train this test drives.  One, deliberately: a second adds nothing to what is asserted
     * and halves the free platforms on a single-track corridor.
     *
     * @param layout the emptied railway
     */
    private static void putOneTrainOn(Layout layout) throws Exception
    {
        assertTrue(layout.moveLocomotive(LOCO_NAMES[0], START, false),
            "the railway would not accept " + LOCO_NAMES[0] + " at " + START + ", so there is nothing "
            + "to run and every assertion below would be about an empty railway");

        assertEquals(layout.getLocomotivesToRun().size(), 1,
            "this test drives one train and the railway reports " + layout.getLocomotivesToRun().size()
            + ": " + layout.getLocomotivesToRun());

        assertNotNull(layout.getPoint(START).getCurrentLocomotive(),
            "the train is not standing where it was put");
    }
}
