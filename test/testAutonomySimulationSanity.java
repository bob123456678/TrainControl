import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.automation.Edge;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.List;
import java.util.LinkedList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Sanity check: run the kind of autonomy file the main UI ships with, in simulate mode, for one minute and
 * confirm the path integrity validation warning never fires - while also verifying the run was real (the
 * accessories actually actuated many times and every locomotive changed stations repeatedly).
 *
 * In simulate mode the guard is bypassed (there is no real actuation to confirm), so a clean run must
 * never record a path validation failure.  PATH_VALIDATION_ALERT_THRESHOLD is raised so the failure
 * counter never resets - any single failure would therefore be caught.  DEBUG_SIMULATE_PACKETS is on so
 * the Central Station echoes are simulated, which is what advances each accessory's actuation count.
 *
 * The frozen layout (test/autonomy_sanity.json) is a larger version of the UI's sample_autonomy.json:
 * three departure stations plus an arrival station, four switches and three signals, three locomotives,
 * and 0-1s action delays so trains cycle quickly.  The switches are commanded to different positions on
 * different routes, so they toggle constantly as the trains move around.
 */
public class testAutonomySimulationSanity
{
    private static MarklinControlStation model;

    private static final Accessory.accessoryDecoderType MM2 = Accessory.accessoryDecoderType.MM2;

    private static final String[] LOCO_NAMES =
    {
        "Auto Test Loc 1", "Auto Test Loc 2", "Auto Test Loc 3"
    };

    private static final String[] ACCESSORY_NAMES =
    {
        "Switch 1", "Switch 2", "Switch 3", "Switch 4", "Signal 5", "Signal 6", "Signal 7"
    };

    // How long to run, and how sensitively to sample locomotive positions.
    private static final long RUN_MS = 120_000;
    private static final long POLL_MS = 500;

    // Minimum activity a genuine one-minute run must produce.
    private static final int MIN_TOTAL_ACTUATIONS = 20;
    private static final int MIN_STATION_CHANGES_PER_LOC = 3;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);

        // Not connected: the layout may enter simulate mode, and exec() takes the simulated-echo branch so
        // accessory actuations are confirmed (which is what advances getNumActuations()).
        model.setNetworkCommState(false);
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        // Guard enabled; never reset the counter so even a single failure is detectable.
        Layout.PATH_INTEGRITY_VALIDATION = true;
        Layout.PATH_VALIDATION_ALERT_THRESHOLD = Integer.MAX_VALUE;

        // parseAuto only places locomotives that already exist - create the three the file references.
        model.newMM2Locomotive(LOCO_NAMES[0], 61);
        model.newMM2Locomotive(LOCO_NAMES[1], 62);
        model.newMM2Locomotive(LOCO_NAMES[2], 63);

        // The accessories referenced by the edges must exist in the DB - parseAuto does not reliably create
        // them - so add each one the file uses (the number in the name is the address).
        model.newSwitch(1, MM2, false);
        model.newSwitch(2, MM2, false);
        model.newSwitch(3, MM2, false);
        model.newSwitch(4, MM2, false);
        model.newSignal(5, MM2, false);
        model.newSignal(6, MM2, false);
        model.newSignal(7, MM2, false);

        loadSanityFixture();
    }

    /**
     * (Re)loads the frozen autonomy file from the test folder as the model's auto layout.
     *
     * Called after EVERY test method, not just at class setup, because Layout's version counter is
     * static: every construction retires all earlier instances, and a retired Layout refuses to
     * dispatch - runLocomotives spins only while isCurrentLayout().  So any test in this class that
     * builds its own Layout silently disarms the soak test, which then fails with "should have executed
     * at least one path" whenever TestNG happens to order it second.  Within-class order is arbitrary
     * reflection order, so that failure comes and goes between runs of an unchanged suite.  Reloading
     * makes the fixture the newest - and therefore current - instance again, whatever the order.
     */
    private static void loadSanityFixture() throws Exception
    {
        String json = new BufferedReader(new InputStreamReader(
                testAutonomySimulationSanity.class.getResource("autonomy_sanity.json").openStream()))
                .lines().collect(Collectors.joining("\n"));

        model.parseAuto(json);
    }

    @AfterMethod
    public void restoreSanityFixture() throws Exception
    {
        loadSanityFixture();
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (model.hasAutoLayout())
        {
            model.getAutoLayout().stopLocomotives();
        }

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = false;
        Layout.PATH_VALIDATION_ALERT_THRESHOLD = 3;

        for (String name : LOCO_NAMES)
        {
            model.deleteLoc(name);
        }
    }

    @Test
    public void testSimulatedAutonomyRaisesNoWarning() throws Exception
    {
        Layout layout = model.getAutoLayout();

        assertTrue(layout != null && layout.isValid(),
            "The autonomy file must parse into a valid layout");

        // Count each locomotive's completed routes (= station-to-station moves) via the arrival callback.
        // This is reliable, unlike sampling getLocomotiveLocation, which returns an arbitrary one of the
        // several points a locomotive occupies mid-path (and so can appear stuck while the train runs).
        Map<String, AtomicInteger> stationChanges = new HashMap<>();

        for (String name : LOCO_NAMES)
        {
            stationChanges.put(name, new AtomicInteger(0));
            model.getLocByName(name).setCallback(Layout.CB_ROUTE_END,
                (l) -> stationChanges.get(l.getName()).incrementAndGet());
        }

        model.go();
        layout.runLocomotives();

        boolean sawActivity = false;

        // Soak, confirming no warning ever fires.
        long deadline = System.currentTimeMillis() + RUN_MS;

        while (System.currentTimeMillis() < deadline)
        {
            if (!layout.getActiveLocomotives().isEmpty())
            {
                sawActivity = true;
            }

            assertTrue(layout.getPathValidationFailureCount() == 0,
                "No path validation warning must occur during a simulated run (failures="
                    + layout.getPathValidationFailureCount() + ")");

            Thread.sleep(POLL_MS);
        }

        layout.stopLocomotives();

        // Let any in-flight paths finish so the actuation counters settle and the run threads exit before
        // teardown removes the locomotives.
        long windDown = System.currentTimeMillis() + 5000;

        while (!layout.getActiveLocomotives().isEmpty() && System.currentTimeMillis() < windDown)
        {
            Thread.sleep(100);
        }

        // The run must not have been vacuous.
        assertTrue(sawActivity, "The simulated autonomy should have executed at least one path");

        // Accessories must actually have been actuated a meaningful number of times (the switches toggle as
        // trains take the different routes).
        int totalActuations = 0;

        for (String name : ACCESSORY_NAMES)
        {
            Accessory acc = model.getAccessoryByName(name);
            assertTrue(acc != null, "Accessory " + name + " should exist");
            totalActuations += acc.getNumActuations();
        }

        assertTrue(totalActuations >= MIN_TOTAL_ACTUATIONS,
            "Accessories should have actuated at least " + MIN_TOTAL_ACTUATIONS + " times (was " + totalActuations + ")");

        // Every locomotive must have changed stations enough times.
        for (String name : LOCO_NAMES)
        {
            int changes = stationChanges.get(name).get();
            assertTrue(changes >= MIN_STATION_CHANGES_PER_LOC,
                name + " should have changed stations at least " + MIN_STATION_CHANGES_PER_LOC
                    + " times (was " + changes + ")");
        }

        // And of course - no warning across the whole run.
        assertTrue(layout.getPathValidationFailureCount() == 0,
            "No path validation warning must occur during a simulated run");
    }

    /**
     * Two consecutive path points sharing one s88 must not wedge the run.
     *
     * The simulation announces each point by setting its sensor, waits for the occupancy to hold
     * 201ms, then spawns a DETACHED thread to clear it "behind the train" after a random delay.
     * That clear has no relevance check.  When the next point shares the same sensor - routine on
     * the real layout, where BottomMainPost and TunnelLongParkReverse both report 2013 - the stale
     * clear can land after the next point's announcement: either inside the 201ms hold window
     * (the waiter starts over) or between the announcement and the wait (the waiter never sees
     * occupancy).  Both leave the waiter blocked on a sensor no producer will ever set again -
     * observed live at 04:07:37.970, one millisecond after the milestone.
     *
     * Real hardware is immune: a physical sensor spanning both points simply stays held.  Only the
     * per-point pulse model manufactures the false gap.
     *
     * This is a RACE, so the red is probabilistic per iteration; six iterations make a silent
     * pre-fix pass astronomically unlikely, and the first wedge fails fast via the watchdog.  The
     * executor thread is a daemon, so a wedged run cannot hold the JVM open past the class.
     */
    @Test
    public void testSharedSensorPulsesDoNotWedgeThePath() throws Exception
    {
        MarklinLocomotive loc = model.newMM2Locomotive("Sim race loc", 64);

        if (!model.isFeedbackSet("47401")) model.newFeedback(47401, null);
        if (!model.isFeedbackSet("47402")) model.newFeedback(47402, null);
        if (!model.isFeedbackSet("47403")) model.newFeedback(47403, null);

        ExecutorService watchdog = Executors.newSingleThreadExecutor(r ->
        {
            Thread t = new Thread(r, "sim-race-watchdog");
            t.setDaemon(true);
            return t;
        });

        try
        {
            for (int i = 1; i <= 6; i++)
            {
                model.setFeedbackState("47401", false);
                model.setFeedbackState("47402", false);
                model.setFeedbackState("47403", false);

                Layout layout = new Layout(model);

                layout.setMinDelay(0);
                layout.setMaxDelay(0);
                layout.setSimulate(true);

                layout.createPoint("SR A", true, "47401");
                layout.createPoint("SR M1", false, "47402");
                layout.createPoint("SR M2", false, "47402");
                layout.createPoint("SR B", true, "47403");

                List<Edge> path = new LinkedList<>();

                path.add(layout.createEdge("SR A", "SR M1"));
                path.add(layout.createEdge("SR M1", "SR M2"));
                path.add(layout.createEdge("SR M2", "SR B"));

                assertTrue(layout.moveLocomotive("Sim race loc", "SR A", false),
                    "iteration " + i + ": precondition - the locomotive must be placed");

                Future<Boolean> run = watchdog.submit(() -> layout.executePath(path, loc, 30, null));

                try
                {
                    assertTrue(run.get(15, TimeUnit.SECONDS),
                        "iteration " + i + ": the path reported failure rather than completing");
                }
                catch (TimeoutException e)
                {
                    layout.stopLocomotives();

                    fail("iteration " + i + ": WEDGED - the stale clear-behind of SR M1 destroyed "
                        + "the shared sensor 47402 after SR M2 was announced, and the waiter is now "
                        + "blocked on a sensor no producer will ever set again");
                }

                // Let the final detached clear threads settle before the next iteration resets state.
                // Belt and braces since the CP-C1 fence: constructing the next iteration's Layout
                // retires this one, so its stragglers stand down on their own.
                Thread.sleep(300);
            }
        }
        finally
        {
            watchdog.shutdownNow();
            model.deleteLoc("Sim race loc");
        }
    }

    /**
     * CP-C1: a clear-behind that outlives its Layout must not clear a sensor the NEXT run needs.
     *
     * The clear is spawned detached after a delay of up to maxDelay SECONDS, so a run can end - and its
     * Layout be replaced by a reload - with clears still pending.  The epoch map is per instance, so an
     * orphan clear consults a map the new run never bumps: it passes its own stand-down check and
     * clears the sensor anyway.  That is the SF-B1 wedge, one Layout boundary later.
     *
     * Deterministic, unlike its sibling: the delay makes the clear provably still pending when the
     * Layout is retired, and the precondition below asserts exactly that - so if the timing margin were
     * ever lost this test would fail loudly rather than start passing for the wrong reason.
     */
    @Test
    public void testAClearFromARetiredLayoutStandsDown() throws Exception
    {
        final int CLEAR_DELAY_S = 3;

        MarklinLocomotive loc = model.newMM2Locomotive("Sim orphan loc", 65);

        if (!model.isFeedbackSet("47411")) model.newFeedback(47411, null);
        if (!model.isFeedbackSet("47412")) model.newFeedback(47412, null);

        try
        {
            model.setFeedbackState("47411", false);
            model.setFeedbackState("47412", false);

            Layout retiring = new Layout(model);

            retiring.setMinDelay(CLEAR_DELAY_S);
            retiring.setMaxDelay(CLEAR_DELAY_S);
            retiring.setSimulate(true);

            retiring.createPoint("SO A", true, "47411");
            retiring.createPoint("SO B", true, "47412");

            List<Edge> path = new LinkedList<>();

            path.add(retiring.createEdge("SO A", "SO B"));

            assertTrue(retiring.moveLocomotive("Sim orphan loc", "SO A", false),
                "precondition: the locomotive must be placed");

            // Returns once SO B is reached - its clear-behind thread is still sleeping off the delay.
            assertTrue(retiring.executePath(path, loc, 30, null),
                "precondition: the path must complete");

            assertTrue(model.getFeedbackState("47412"),
                "precondition: the destination sensor is still set and its clear-behind still pending");

            // Retire the layout, exactly as loading another autonomy configuration would.
            new Layout(model);

            assertFalse(retiring.isCurrentLayout(),
                "precondition: the first layout must now be retired");

            Thread.sleep((CLEAR_DELAY_S + 3) * 1000L);

            assertTrue(model.getFeedbackState("47412"),
                "a clear-behind belonging to a retired Layout cleared a sensor that belongs to the "
                    + "current one - the SF-B1 wedge, one reload later");
        }
        finally
        {
            model.deleteLoc("Sim orphan loc");
        }
    }
}
