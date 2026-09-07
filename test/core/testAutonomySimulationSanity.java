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
     * Two turns in one session cancel; they do not add up to one (VAL9-A1).
     *
     * The record of a turn at the destination is a **net flip owed to the graph**, not a log of events,
     * and getting that wrong made the fix worse than the defect it replaced.
     *
     * The arrival block is shared: it records autonomy's reversals as well as the manual ones the
     * ruling was about, because `shouldReverseAt` answers `isReversing()` whenever there is no prompt
     * policy. And an autonomy session is `isRunning()` from end to end, while the drain only happens
     * once the railway is idle - so a whole session's reversals reach the drain together.
     *
     * A plain set collapsed them to one name. **A shuttle that turned at both ends came back facing the
     * way it started and had its facing flipped once** - which is worse than the stale graph this was
     * fixing, because a stale graph was at least right about a train that had turned an even number of
     * times.
     *
     * So membership toggles. This runs the shuttle out and back and asserts there is nothing owed.
     *
     * MUTATION: `reversedOnArrival.add(name)` in place of the toggle fails this.
     *
     * @throws Exception on a failure to run
     */
    @Test
    public void testTwoTurnsInOneSessionCancel() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinLocomotive loc = model.newMM2Locomotive("B4 shuttle", 232);

        ExecutorService watchdog = Executors.newSingleThreadExecutor();

        try
        {
            if (!model.isFeedbackSet("47421")) model.newFeedback(47421, null);
            if (!model.isFeedbackSet("47422")) model.newFeedback(47422, null);

            model.setFeedbackState("47421", false);
            model.setFeedbackState("47422", false);

            layout.setMaxDelay(0);
            layout.setMinDelay(0);
            layout.setSimulate(true);

            layout.createPoint("B4 west", true, "47421");
            layout.createPoint("B4 east", true, "47422");

            // BOTH ENDS TURN, which is what a shuttle is.
            layout.getPoint("B4 west").setReversing(true);
            layout.getPoint("B4 east").setReversing(true);

            Edge out = layout.createEdge("B4 west", "B4 east");
            Edge back = layout.createEdge("B4 east", "B4 west");

            out.setLength(50);
            back.setLength(50);

            loc.setTrainLength(1);

            assertTrue(layout.moveLocomotive("B4 shuttle", "B4 west", false),
                "precondition: the locomotive must be placed");

            assertTrue(layout.takeReversalsOnArrival().isEmpty(),
                "precondition: nothing is owed before the shuttle runs");

            layout.runLocomotives();

            run(watchdog, layout, java.util.Arrays.asList(out), loc, "out");

            // ONE LEG, ONE FLIP OWED.  Read WITHOUT draining, or the second leg starts from a clean
            // slate and the cancellation below is never exercised - the test would pass because
            // nothing accumulated rather than because two turns cancelled.
            assertTrue(owed(layout).contains(loc.getName()),
                "after one turn the graph is owed a flip, and it is not. The rest of this test cannot"
                + " tell cancellation from nothing ever being recorded");

            run(watchdog, layout, java.util.Arrays.asList(back), loc, "back");

            assertFalse(layout.takeReversalsOnArrival().contains(loc.getName()),
                "the shuttle turned at both ends and came back facing the way it started, and the"
                + " graph is still owed a flip. A record of EVENTS collapses to one name and flips the"
                + " facing once - which is worse than the stale graph this was fixing, because a stale"
                + " graph was right about a train that turned an even number of times (VAL9-A1)");
        }
        finally
        {
            layout.stopLocomotives();
            watchdog.shutdownNow();
            model.deleteLoc("B4 shuttle");
        }
    }

    /**
     * What the graph is owed, without draining it.
     *
     * By reflection because the only public reader is the drain, and draining here would destroy the
     * state the next leg has to cancel.
     *
     * @param layout the layout
     * @return the names owed a flip
     * @throws Exception on a reflection failure
     */
    @SuppressWarnings("unchecked")
    private static java.util.Set<String> owed(Layout layout) throws Exception
    {
        java.lang.reflect.Field f = Layout.class.getDeclaredField("reversedOnArrival");

        f.setAccessible(true);

        return new java.util.HashSet<>((java.util.Set<String>) f.get(layout));
    }

    /**
     * Runs one leg and fails with a legible message rather than a timeout.
     *
     * @param watchdog the executor
     * @param layout the layout
     * @param path the leg
     * @param loc the locomotive
     * @param which which leg, for the message
     * @throws Exception on a failure to run
     */
    private static void run(ExecutorService watchdog, Layout layout, List<Edge> path,
        MarklinLocomotive loc, String which) throws Exception
    {
        assertTrue(layout.isPathClear(path, loc, true),
            "the " + which + " leg is refused before it starts");

        Future<Boolean> leg = watchdog.submit(() -> layout.executePath(path, loc, 30, null));

        try
        {
            assertTrue(leg.get(20, TimeUnit.SECONDS),
                "the " + which + " leg reported failure rather than completing");
        }
        catch (TimeoutException wedged)
        {
            layout.stopLocomotives();

            fail("the " + which + " leg never arrived, so its reversal never happened");
        }
    }

    /**
     * A turn the railway makes at the destination is recorded there (IND9-B4).
     *
     * Adam, 2026-09-07: **"it should be recorded at the destination.  Otherwise, it’s just the same as
     * always."**
     *
     * A reversal at the arrival is the one direction change nothing was following. Its echo lands
     * inside the run’s own thousand-millisecond pause, while `isRunning()` is still true, so the window
     * takes the baseline-only branch; and when the run ends the idle reconcile LEVELS the baseline
     * rather than following it. Both are right for a command somebody else sent mid-run - that is what
     * they were written for - and both were wrong for this one, because this one is not news arriving
     * late. It is something this code did on purpose and knew about as it did it.
     *
     * Before the fix: the physical train reversed, the graph and the setup went on saying it had not,
     * and the next dispatch offered paths for the wrong heading.
     *
     * **In this class rather than beside the reversal rules**, because it needs a run that actually
     * ARRIVES. Every `executePath` test elsewhere throws from a callback before the arrival, so none of
     * them reaches the block under test; this class runs with `DEBUG_SIMULATE_PACKETS` and a watchdog,
     * which is what lets a path complete. A test that could not reach the arrival would be asserting
     * the absence of something it never triggered.
     *
     * The drain is asserted too: a record read without clearing is re-applied on every refresh, which
     * turns one reversal into a metronome.
     *
     * MUTATION: removing the `reversedOnArrival.add` at the arrival fails the first assertion; making
     * `takeReversalsOnArrival` a plain getter fails the last.
     *
     * @throws Exception on a failure to run
     */
    @Test
    public void testATurnAtTheDestinationIsRecordedThere() throws Exception
    {
        Layout layout = new Layout(model);

        MarklinLocomotive loc = model.newMM2Locomotive("B4 turner", 231);

        ExecutorService watchdog = Executors.newSingleThreadExecutor();

        try
        {
            if (!model.isFeedbackSet("47411")) model.newFeedback(47411, null);
            if (!model.isFeedbackSet("47412")) model.newFeedback(47412, null);

            // CLEARED FIRST.  isPathClear refuses any path whose destination sensor reads occupied,
            // and a sensor nobody has reported does not default to clear - the sibling test in this
            // class primes its three the same way.
            model.setFeedbackState("47411", false);
            model.setFeedbackState("47412", false);

            // AND THE LAYOUT SIMULATES.  Without this nothing ever sets the destination sensor and the
            // run waits on it forever - a railway event wait is deliberately unbounded.
            layout.setMaxDelay(0);
            layout.setMinDelay(0);
            layout.setSimulate(true);

            layout.createPoint("B4 start", true, "47411");
            layout.createPoint("B4 end", true, "47412");

            // A REVERSING point rather than a terminus.  Both turn an arriving train without asking -
            // `shouldReverseAt` answers `current.isReversing()` when there is no policy, and a terminus
            // is a reversing point that is also a destination - but a terminus brings the track-room
            // rule with it, and an unmeasured two-point fixture is refused by it before the run starts.
            // The block under test is the same one either way.
            layout.getPoint("B4 end").setReversing(true);

            List<Edge> path = new LinkedList<>();

            Edge only = layout.createEdge("B4 start", "B4 end");

            path.add(only);

            // MEASURED, and generously.  A reversing destination brings the track-room rule with it,
            // and an unmeasured fixture is refused by it before the run starts - which would report
            // "the arrival never happened" for a reason that has nothing to do with what is on test.
            only.setLength(50);

            loc.setTrainLength(1);

            assertTrue(layout.moveLocomotive("B4 turner", "B4 start", false),
                "precondition: the locomotive must be placed");

            assertTrue(layout.takeReversalsOnArrival().isEmpty(),
                "precondition: nothing has been reversed yet, or the assertion below cannot tell this"
                + " run's reversal from an older one");

            // Separated from the run below so a refusal is not reported as a failed journey - two very
            // different faults with one symptom.
            assertTrue(layout.isPathClear(path, loc, true),
                "the path is refused before it starts, so the run below could never arrive");

            layout.runLocomotives();

            Future<Boolean> run = watchdog.submit(() -> layout.executePath(path, loc, 30, null));

            try
            {
                assertTrue(run.get(20, TimeUnit.SECONDS),
                    "the path reported failure rather than completing, so the arrival never happened");
            }
            catch (TimeoutException wedged)
            {
                layout.stopLocomotives();

                fail("the run never reached its destination, so this test never exercised the arrival");
            }

            java.util.Set<String> turned = layout.takeReversalsOnArrival();

            assertTrue(turned.contains(loc.getName()),
                "the railway turned the train round at its destination and recorded nothing. Neither"
                + " of the two paths that follow a direction change can see this one - the echo"
                + " arrives while the run is still going, and the idle reconcile then levels the"
                + " baseline - so the graph never learns it (IND9-B4)");

            assertTrue(layout.takeReversalsOnArrival().isEmpty(),
                "the record was not drained, so the same reversal is written to the graph again on"
                + " every refresh, flipping the facing back and forth instead of recording it once");
        }
        finally
        {
            layout.stopLocomotives();
            watchdog.shutdownNow();
            model.deleteLoc("B4 turner");
        }
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
            if (model != null) model.getAutoLayout().stopLocomotives();
        }

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = false;
        Layout.PATH_VALIDATION_ALERT_THRESHOLD = 3;

        for (String name : LOCO_NAMES)
        {
            if (model != null) model.deleteLoc(name);
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

            // WHAT THIS CAN AND CANNOT CATCH (TCX-B7).
            //
            // It reads as a check on the path-integrity guard and is not one.  That guard compares
            // each accessory against the state it was commanded to, and in simulate mode no accessory
            // is actuated at all - so `configureAndLockPath` returns before it
            // (`Layout.java:3043-3046`) and no regression in it can ever move this counter.
            //
            // **It cannot be made to, either, and that is the finding's real answer.** Validating
            // actuation means reading back real hardware; a simulated railway has none. The guard is
            // reachable only with a Central Station attached, so no automated test can reach it -
            // stated here rather than left looking covered.
            //
            // What the counter DOES see here is `handleMisconfiguredPath` reached the other two ways
            // - a configure that failed, or a lock that threw - and those happen in simulate.  That
            // is worth asserting and is what this now says.
            assertTrue(layout.getPathValidationFailureCount() == 0,
                "a path was abandoned as misconfigured during the run - a configure that failed or a "
                    + "lock that threw, since the actuation guard itself is skipped in simulate "
                    + "(failures="
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

        // And no path abandoned as misconfigured across the whole run.  See the same assertion
        // earlier in this class for what that does and does not cover (TCX-B7).
        assertTrue(layout.getPathValidationFailureCount() == 0,
            "a path was abandoned as misconfigured during the run - a configure that failed or a lock "
            + "that threw; the actuation guard itself is skipped in simulate and cannot be covered here");
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
