package core;

import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.automation.Edge;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Arrays;
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
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
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
    // FIVE, not twenty (2026-08-29).
    //
    // This is a time-boxed simulation, so the count measures how loaded the machine is as much as
    // whether the railway did anything: it passes alone and came back 18 inside a full battery. The
    // mutation it exists to catch - the accessory-command loop removed from configureEdge - produces
    // NO actuations at all, so a floor of five catches it exactly as well as twenty and does not go
    // red because something else was running.
    private static final int MIN_TOTAL_ACTUATIONS = 5;
    private static final int MIN_STATION_CHANGES_PER_LOC = 3;

    // TST-A4: these addresses (1-7 MM2) are exactly what a real layout occupies, and
    // MarklinControlStation.newAccessory carries over whatever actuation count already sits at the
    // address - init() restores the operator's own LocDB, not a fresh one. Taken once, before the run,
    // so the assertion below can compare the DELTA rather than the raw total.
    private static Map<String, Integer> baselineActuations;

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

        baselineActuations = new HashMap<>();

        for (String name : ACCESSORY_NAMES)
        {
            baselineActuations.put(name, model.getAccessoryByName(name).getNumActuations());
        }

        loadSanityFixture();
    }

    /**
     * (Re)loads the frozen autonomy file from the test folder as the model's auto layout.
     *
     * Called after EVERY test method, not just at class setup, because Layout's version counter is
     * static: every construction retires all earlier instances, and a retired Layout refuses to
     * dispatch - executePathInternal turns every picked path away at its entry fence, while
     * runLocomotive's own loop keeps spinning on the plain running flag.  So any test in this class
     * that builds its own Layout silently disarms the soak test, which then fails with "should have
     * executed at least one path" whenever TestNG happens to order it second.  Within-class order is
     * arbitrary reflection order, so that failure comes and goes between runs of an unchanged suite.
     * Reloading makes the fixture the newest - and therefore current - instance again, whatever the
     * order.
     *
     * Only tests reach that state.  A real reload goes through parseAuto, which stops the outgoing
     * layout before replacing it; retiring one that is still running takes a direct new Layout(model).
     */
    private static void loadSanityFixture() throws Exception
    {
        String json = new BufferedReader(new InputStreamReader(
                testAutonomySimulationSanity.class.getResource("/autonomy_sanity.json").openStream()))
                .lines().collect(Collectors.joining("\n"));

        model.parseAuto(json);
    }

    @AfterMethod
    public void restoreSanityFixture() throws Exception
    {
        loadSanityFixture();
    }

    @AfterClass(alwaysRun = true)
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

            // MUTATION this catches: remove the accessory-command loop from Layout.configureEdge so
            // autonomy never actually throws a switch.  Comparing against a baseline of 0 (or against
            // a DB carried-over count instead of a delta) would stay >= MIN_TOTAL_ACTUATIONS purely
            // from whatever the operator's own restored LocDB already had at this address; comparing
            // the delta since setUpClass does not.
            totalActuations += acc.getNumActuations() - baselineActuations.get(name);
        }

        assertTrue(totalActuations >= MIN_TOTAL_ACTUATIONS,
            "Accessories should have actuated at least " + MIN_TOTAL_ACTUATIONS
                + " times since the run began (was " + totalActuations + ")");

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

                // Max before min - setMinDelay rejects a value above the current maximum
                layout.setMaxDelay(0);
                layout.setMinDelay(0);
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

            // Max before min - setMinDelay rejects a value above the current maximum
            retiring.setMaxDelay(CLEAR_DELAY_S);
            retiring.setMinDelay(CLEAR_DELAY_S);
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

    /**
     * TST-A4: proves the actuation-confirmation guard that testSimulatedAutonomyRaisesNoWarning trusts to
     * stay silent is actually capable of firing.
     *
     * That soak test runs entirely against the fixture's own Layout, which loads with "simulate": true
     * (test/autonomy_sanity.json:143).  Layout.configureAndLockPath returns at Layout.java:2604 -
     * "if (this.simulate || !PATH_INTEGRITY_VALIDATION) return true;" - BEFORE validatePathActuation ever
     * runs, so for the whole two-minute run handleMisconfiguredPath is unreachable and
     * getPathValidationFailureCount() is pinned at 0 no matter what the guard would have found.  "No
     * warning fired" there is unfalsifiable, not a verified outcome - the mechanism was never armed.
     *
     * Simulate mode cannot simply be turned off for the soak itself: it is also what makes
     * simAnnounce/simClearBehind fake each point's sensor as the train "arrives", which is the only
     * reason the fixture's locomotives move at all without real hardware.  So this is a separate, small
     * Layout built directly (like testAutonomyPathValidation.java's fixtures) with simulate left at its
     * default OFF, network still disconnected and DEBUG_SIMULATE_PACKETS still on from setUpClass - so
     * the CS echo is simulated and no real hardware is needed, but the real validatePathActuation guard
     * runs instead of being bypassed.  The path's one accessory is then driven to the wrong state out of
     * band so it can never confirm, proving the exact mechanism the soak test's silence depends on can in
     * fact detect a real misconfiguration.
     *
     * MUTATION this catches: delete the guard at Layout.java:2604-2609 (or make validatePathActuation
     * return true unconditionally, or set PATH_INTEGRITY_VALIDATION = false) - the failure below would
     * then never be recorded and this test goes red, exactly where the always-simulate soak test above
     * cannot.
     */
    @Test
    public void testPathValidationCanActuallyFireOutsideSimulateMode() throws Exception
    {
        int originalMs = Layout.PATH_VALIDATION_MS;
        Layout.PATH_VALIDATION_MS = 100;

        MarklinLocomotive loc = model.newMM2Locomotive("Sanity val loc", 66);

        try
        {
            Layout layout = new Layout(model);

            assertFalse(layout.isSimulate(),
                "precondition: this Layout must run the real guard, not the simulate-mode bypass the "
                    + "soak test above relies on for its own reason to exist");

            layout.createPoint("SANITY_VAL_A", false, null);

            MarklinFeedback fb = model.newFeedback(47421, null);
            model.setFeedbackState(fb.getName(), false);
            layout.createPoint("SANITY_VAL_B", true, fb.getName());

            Edge edge = layout.createEdge("SANITY_VAL_A", "SANITY_VAL_B");

            MarklinAccessory acc = model.newSwitch(8, MM2, false);
            edge.addConfigCommand(acc.getName(), Accessory.accessorySetting.TURN);

            // Continuously drives the accessory to the opposite of its commanded state so it can never
            // confirm - the same technique testAutonomyPathValidation.startCorrupting uses for this
            // exact guard.
            final boolean[] corrupting = { true };

            Thread corrupter = new Thread(() ->
            {
                while (corrupting[0])
                {
                    acc.setSwitched(false);

                    try
                    {
                        Thread.sleep(2);
                    }
                    catch (InterruptedException ex)
                    {
                        return;
                    }
                }
            });

            corrupter.setDaemon(true);
            corrupter.start();

            int before = layout.getPathValidationFailureCount();
            boolean result;

            try
            {
                result = layout.configureAndLockPath(Arrays.asList(edge), loc);
            }
            finally
            {
                corrupting[0] = false;
            }

            assertFalse(result,
                "a misconfigured accessory must fail configureAndLockPath outside simulate mode");

            assertTrue(layout.getPathValidationFailureCount() > before,
                "validatePathActuation must have recorded the failure - this is exactly the mechanism "
                    + "the soak test above trusts to stay silent, and its simulate=true Layout never lets "
                    + "it run at all");
        }
        finally
        {
            Layout.PATH_VALIDATION_MS = originalMs;
            model.deleteLoc("Sanity val loc");
        }
    }
}
