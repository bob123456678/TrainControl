package core;

import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Verifies the configureAndLockPath guard in Layout: autonomy trains must not be released until the
 * Central Station confirms the path's accessories reached their commanded state.  On a mismatch the guard
 * stops the locomotive and releases its locks (returning false so executePath does not run it); power is
 * deliberately left on so other locomotives are unaffected, and autonomy re-attempts the path organically.
 * A UI popup is only raised once failures reach PATH_VALIDATION_ALERT_THRESHOLD, and at most once per
 * Layout instance - further failures keep being logged and counted but do not re-trigger it.
 *
 * The tests run disconnected with DEBUG_SIMULATE_PACKETS = true so exec() echoes each accessory command
 * back (simulating a working CS), letting the accessories confirm.  A fault is forced by asynchronously
 * driving the configured accessories to the wrong state out of band - so validation fails and the guard
 * takes the error path.  The error path is observed via the return value and the released path locks (not
 * a shared field), since multiple locomotives validate concurrently.
 */
public class testAutonomyPathValidation
{
    private static MarklinControlStation model;

    private static final Accessory.accessoryDecoderType MM2 = Accessory.accessoryDecoderType.MM2;

    private static int locCounter = 0;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Before init(), not after: init() reads TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF as soon as
        // showUI constructs the real window, so a sandbox opened afterwards protects nothing.  Without
        // this, showUI = true below opens and can write to Adam's own railway (OB-111), and can also
        // raise the modal "create a track diagram?" prompt that no test here will ever click, stalling
        // the whole battery.
        sandbox = support.LayoutSandbox.open();

        // showUI = true so the failure popup renders and the operator can see the error.
        model = init(null, true, true, false, true);

        // Not connected => exec() takes the simulated-echo branch; debug is on so that branch is active.
        model.setNetworkCommState(false);
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        // Keep the tests fast: the fault case waits out this timeout once.  The production default is much
        // larger to accommodate a slow Central Station.
        Layout.PATH_VALIDATION_MS = 100;

        // Exercise the guard explicitly (this is the production default, but be robust to other tests).
        Layout.PATH_INTEGRITY_VALIDATION = true;
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = false;
        Layout.PATH_VALIDATION_MS = 1000;
        Layout.PATH_INTEGRITY_VALIDATION = true;

        if (sandbox != null) sandbox.close();
    }

    /**
     * A freshly-built single-edge path (A -> B) whose edge commands two switches to TURN.
     */
    private static class TestPath
    {
        Layout layout;
        List<Edge> path;
        MarklinAccessory acc1;
        MarklinAccessory acc2;
    }

    /**
     * Builds an isolated layout.  Distinct accessory addresses per test prevent the fault test's
     * corrupted accessories from leaking into the clean tests (the accessory DB is shared).
     */
    private TestPath buildPath(int addressBase, String suffix) throws Exception
    {
        return buildPath(addressBase, suffix, model);
    }

    /**
     * Same fixture, against a caller-supplied ViewListener rather than the model directly - for
     * testUiAlertFiresAtMostOncePerLayout, which needs to count calls to showAutonomyAlert rather than
     * just read the latch afterwards.
     */
    private TestPath buildPath(int addressBase, String suffix, org.traincontrol.model.ViewListener control)
        throws Exception
    {
        TestPath tp = new TestPath();

        tp.layout = new Layout(control);
        tp.layout.createPoint("A" + suffix, false, null);

        // Destination points require an s88 feedback; register one (named Integer.toString(id)) and keep
        // it clear so the path reads as available in isPathClear.
        MarklinFeedback fb = model.newFeedback(addressBase, null);
        model.setFeedbackState(fb.getName(), false);
        tp.layout.createPoint("B" + suffix, true, fb.getName());

        Edge edge = tp.layout.createEdge("A" + suffix, "B" + suffix);

        tp.acc1 = model.newSwitch(addressBase, MM2, false);
        tp.acc2 = model.newSwitch(addressBase + 1, MM2, false);

        edge.addConfigCommand(tp.acc1.getName(), accessorySetting.TURN);
        edge.addConfigCommand(tp.acc2.getName(), accessorySetting.TURN);

        tp.path = Arrays.asList(edge);

        return tp;
    }

    /**
     * A three-point path A -> B -> C, so that reserving it holds more than one point at once.  The
     * single-edge fixture above cannot show the reservation being torn down, because there is only
     * ever one point to hold.
     */
    private TestPath buildThreePointPath(int addressBase, String suffix) throws Exception
    {
        TestPath tp = new TestPath();

        tp.layout = new Layout(model);
        tp.layout.createPoint("A" + suffix, false, null);

        MarklinFeedback fbB = model.newFeedback(addressBase, null);
        model.setFeedbackState(fbB.getName(), false);
        tp.layout.createPoint("B" + suffix, true, fbB.getName());

        MarklinFeedback fbC = model.newFeedback(addressBase + 1, null);
        model.setFeedbackState(fbC.getName(), false);
        tp.layout.createPoint("C" + suffix, true, fbC.getName());

        Edge ab = tp.layout.createEdge("A" + suffix, "B" + suffix);
        Edge bc = tp.layout.createEdge("B" + suffix, "C" + suffix);

        tp.acc1 = model.newSwitch(addressBase + 2, MM2, false);
        tp.acc2 = model.newSwitch(addressBase + 3, MM2, false);

        ab.addConfigCommand(tp.acc1.getName(), accessorySetting.TURN);
        bc.addConfigCommand(tp.acc2.getName(), accessorySetting.TURN);

        tp.path = Arrays.asList(ab, bc);

        return tp;
    }

    /**
     * With atomic routes off, the track a train has passed is given back - lock edges and all.
     *
     * Adam, on MT-087: "WORKS FINE in atomic mode.  In non atomic mode, locks aren't getting released.
     * Example: EN57-203 is started from BottomSecondary to TopMainR2Inter.  After it passes Tunnel,
     * EN57-947 should be able to go from TopMainR2 to BottomSecondary, but no movement is allowed at
     * all."
     *
     * **Non-atomic mode exists to give track back as the train clears it.** executePath does give the
     * EDGE back the moment `tailHasProvablyPassed` says the train is clear of it - and it deliberately
     * kept every LOCK EDGE that edge had taken, until the whole path finished. So the edges came free
     * and the shared throats did not, and on a railway where routes cross, a throat held is a route
     * refused. Every square the train had been through went on blocking everything that crossed it for
     * the rest of the run, which is "no movement is allowed at all".
     *
     * The proof is the same one the edge itself is released on. If the train is clear of the edge, it
     * is clear of the throat that edge needed; there is nothing left for the lock to protect.
     *
     * Written as a run rather than as a sequence of calls: the early release happens inside
     * executePath's progress loop and there is no other door to it.
     *
     * MUTATION this catches: putting `setLockedEdgeUnoccupied()` back at the early release leaves the
     * crossing held while the train is still going, and the assertion fails.
     */
    @Test
    public void testNonAtomicGivesBackTheLocksOfTrackThePassedTrainHasCleared() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        Layout layout = new Layout(model);

        layout.setAtomicRoutes(false);
        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        MarklinFeedback first = model.newFeedback(91, null);
        MarklinFeedback second = model.newFeedback(92, null);
        MarklinFeedback third = model.newFeedback(94, null);
        MarklinFeedback fourth = model.newFeedback(95, null);
        MarklinFeedback beyond = model.newFeedback(93, null);

        for (MarklinFeedback fb : new MarklinFeedback[]{first, second, third, fourth, beyond})
        {
            model.setFeedbackState(fb.getName(), false);
        }

        // A destination needs a sensor; the ORIGIN is where the train starts and needs none,
        // so it is the one point here that is not a station.
        //
        // FOUR edges, not two.  A two-edge run in simulation is over in a second, and unlockPath
        // releases everything at the end - so the first version of this test watched a finished run
        // and could not tell "given back early" from "given back at the end", which is the whole
        // question.
        layout.createPoint("NA_A", false, null);
        layout.createPoint("NA_B", true, first.getName());
        layout.createPoint("NA_C", true, second.getName());
        layout.createPoint("NA_D", true, third.getName());
        layout.createPoint("NA_E", true, fourth.getName());

        // The crossing this run's FIRST edge needs, and nothing else about the run touches.
        layout.createPoint("NA_X", false, null);
        layout.createPoint("NA_Y", true, beyond.getName());

        Edge crossing = layout.createEdge("NA_X", "NA_Y");

        Edge ab = layout.createEdge("NA_A", "NA_B");
        Edge bc = layout.createEdge("NA_B", "NA_C");
        Edge cd = layout.createEdge("NA_C", "NA_D");
        Edge de = layout.createEdge("NA_D", "NA_E");

        ab.addLockEdge(crossing);

        MarklinLocomotive loc = dummyLoc();

        loc.setPreferredSpeed(35);
        loc.setTrainLength(0);

        layout.getPoint("NA_A").setLocomotive(loc);

        List<Edge> path = Arrays.asList(ab, bc, cd, de);

        assertFalse(crossing.isLockHeld(null), "precondition: the crossing is free before the run");

        final Layout running = layout;
        final MarklinLocomotive driver = loc;

        final Thread run = new Thread(() -> running.executePath(path, driver, 35, null));

        run.setDaemon(true);
        run.start();

        try
        {
            // The crossing has to be TAKEN first, or "it came free" says nothing.
            assertTrue(waitFor(() -> crossing.isLockHeld(null), 10000),
                "precondition: the run never locked the crossing, so there is nothing to release");

            // ...and given back once the train is past the edge that needed it, WHILE THE RUN IS
            // STILL GOING.  That is the whole of non-atomic mode, and the only thing that separates
            // it from atomic: everything comes free at the end either way.
            final boolean[] stillRunning = {false};

            assertTrue(waitFor(() ->
            {
                if (crossing.isLockHeld(null)) return false;

                stillRunning[0] = run.isAlive();

                return true;
            }, 30000),
                "the crossing was never released at all");

            assertTrue(stillRunning[0],
                "the crossing this train had already passed came free only when the whole run "
                + "finished.  Non-atomic mode gives the edge back the moment the tail is clear of it "
                + "and used to keep its lock edges to the end, so every throat the train had been "
                + "through blocked every route that crossed it for the rest of the run - which is "
                + "Adam's \"no movement is allowed at all\"");
        }
        finally
        {
            running.stopLocomotives();

            run.join(10000);
        }
    }

    /**
     * Polls a condition until it holds or the deadline passes.
     *
     * @param what the question
     * @param timeoutMs how long to give it
     * @return whether it ever held
     */
    private static boolean waitFor(java.util.concurrent.Callable<Boolean> what, long timeoutMs)
        throws Exception
    {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline)
        {
            if (Boolean.TRUE.equals(what.call())) return true;

            Thread.sleep(25);
        }

        return false;
    }

    private MarklinLocomotive dummyLoc()
    {
        return new MarklinLocomotive(model, 1, MarklinLocomotive.decoderType.MM2, "PV Loc " + (++locCounter));
    }

    /**
     * Polls the power state until it matches expected or the timeout elapses (power is set via async echoes).
     */
    private void waitForPower(boolean expected, long timeoutMs) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (model.getPowerState() != expected && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(10);
        }
    }

    /**
     * Continuously drives both accessories to STRAIGHT (the opposite of the commanded TURN) so the path
     * never validates, no matter how many times it is re-configured.  The CONFIGURE_SLEEP gap between the
     * two config commands guarantees the pair is never both-confirmed at once.  Returns the stop flag.
     */
    private boolean[] startCorrupting(TestPath tp)
    {
        final boolean[] corrupting = { true };

        Thread corrupter = new Thread(() ->
        {
            while (corrupting[0])
            {
                tp.acc1.setSwitched(false);
                tp.acc2.setSwitched(false);

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

        return corrupting;
    }

    /**
     * A clean configuration validates successfully and does not cut power.
     */
    @Test
    public void testCleanConfigurationPasses() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(11, "_clean");

        boolean result = tp.layout.configureAndLockPath(tp.path, dummyLoc());

        assertTrue(result, "configureAndLockPath should return true");
        assertTrue(model.getPowerState(), "A clean configuration must not cut power");
    }

    /**
     * Repeated clean configurations never spuriously trip the guard.
     */
    @Test
    public void testManyCleanConfigurationsPass() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        for (int i = 0; i < 15; i++)
        {
            TestPath tp = buildPath(100 + i * 2, "_many" + i);

            boolean result = tp.layout.configureAndLockPath(tp.path, dummyLoc());

            assertTrue(result, "Run " + i + ": configureAndLockPath should return true");
            assertTrue(model.getPowerState(), "Run " + i + ": a clean configuration must not cut power");
        }
    }

    /**
     * A misconfigured accessory takes the error path: the guard stops the locomotive and releases its
     * locks (returning false) after a single validation attempt, leaving power on for everyone else.
     */
    @Test
    public void testMisconfiguredAccessoryTriggersErrorPath() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(21, "_fault");

        boolean[] corrupting = startCorrupting(tp);

        MarklinLocomotive loc = dummyLoc();

        // TST-C13: a freshly-built dummyLoc() already sits at speed 0, so asserting getSpeed() == 0
        // afterwards proved nothing about handleMisconfiguredPath's loc.setSpeed(0) (Layout.java:2772) -
        // deleting that production line would leave this test green.  Giving the locomotive a nonzero
        // speed here means the closing assertion below can only pass if the guard actually stopped it.
        loc.setSpeed(30);
        assertTrue(loc.getSpeed() == 30, "precondition: the locomotive must be moving before the fault");

        boolean result;

        try
        {
            result = tp.layout.configureAndLockPath(tp.path, loc);
        }
        finally
        {
            corrupting[0] = false;
        }

        // Validation failed, so the path is not executed
        assertFalse(result, "configureAndLockPath must return false when the path cannot be confirmed");

        // Power stays on - the guard stops the loco and releases its locks instead of cutting power, so
        // other locomotives are unaffected
        assertTrue(model.getPowerState(), "Power must stay on after a misconfigured path");

        // The locomotive is stopped and the failed path's locks are released
        assertTrue(loc.getSpeed() == 0,
            "The locomotive must be stopped - it was moving at 30 before the fault, so this can only "
                + "pass if handleMisconfiguredPath actually stopped it");
        assertFalse(tp.path.get(0).isOccupied(dummyLoc()), "The failed path's edge must be released");
    }

    /**
     * In pure simulation mode there is no real actuation to confirm, so the guard is bypassed entirely -
     * even a deliberately misconfigured accessory must not cut power.
     */
    @Test
    public void testSimulateModeBypassesValidation() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(31, "_sim");
        tp.layout.setSimulate(true);

        boolean[] corrupting = startCorrupting(tp);

        boolean result;

        try
        {
            result = tp.layout.configureAndLockPath(tp.path, dummyLoc());
        }
        finally
        {
            corrupting[0] = false;
        }

        assertTrue(result);
        assertTrue(model.getPowerState(), "Simulation mode must bypass the guard (power stays on)");
    }

    /**
     * The UI popup is suppressed until the failure count reaches PATH_VALIDATION_ALERT_THRESHOLD, and then
     * fires at most ONCE per Layout instance: further failures past the threshold keep being logged and
     * counted (the count never resets), but must not re-trigger the popup - the console log is considered
     * sufficient after the first alert.
     *
     * hasShownPathValidationAlert() is a latch that is set once and never cleared, so asserting it is
     * still true after further failures cannot fail no matter how many extra popups those failures
     * raised - a mutation that moved control.showAutonomyAlert(...) OUT of the "not shown yet" guard
     * (Layout.java:2807-2812) would leave every one of those assertions green while the operator got a
     * modal dialog on every failure for the rest of the session.  So this counts the actual calls to
     * showAutonomyAlert through a proxy ViewListener, rather than only reading the latch.
     */
    @Test
    public void testUiAlertFiresAtMostOncePerLayout() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        int originalThreshold = Layout.PATH_VALIDATION_ALERT_THRESHOLD;
        Layout.PATH_VALIDATION_ALERT_THRESHOLD = 3;

        final int[] alertCalls = { 0 };

        org.traincontrol.model.ViewListener counting =
            (org.traincontrol.model.ViewListener) java.lang.reflect.Proxy.newProxyInstance(
                org.traincontrol.model.ViewListener.class.getClassLoader(),
                new Class<?>[] { org.traincontrol.model.ViewListener.class },
                (proxy, method, args) ->
                {
                    if (method.getName().equals("showAutonomyAlert")) alertCalls[0]++;
                    return method.invoke(model, args);
                });

        try
        {
            TestPath tp = buildPath(41, "_thresh", counting);
            boolean[] corrupting = startCorrupting(tp);

            try
            {
                // The path is released after each failure, so it can be re-attempted on the same layout.
                // Below the threshold the count climbs but the alert must not have fired yet.
                for (int i = 1; i < Layout.PATH_VALIDATION_ALERT_THRESHOLD; i++)
                {
                    tp.layout.configureAndLockPath(tp.path, dummyLoc());
                    assertTrue(tp.layout.getPathValidationFailureCount() == i,
                        "Failure " + i + " should accumulate without alerting");
                    assertFalse(tp.layout.hasShownPathValidationAlert(),
                        "Alert must not fire before the threshold is reached (failure " + i + ")");
                    assertEquals(alertCalls[0], 0,
                        "showAutonomyAlert must not be called before the threshold is reached");
                }

                // The failure that reaches the threshold fires the one-time alert.
                tp.layout.configureAndLockPath(tp.path, dummyLoc());
                assertTrue(tp.layout.hasShownPathValidationAlert(),
                    "Alert must fire once the threshold is reached");
                assertEquals(alertCalls[0], 1,
                    "showAutonomyAlert must be called exactly once when the threshold is first reached");

                // Further failures keep accumulating (the count is never reset) but must not re-alert -
                // the latch stays true, no second popup is raised, and - unlike the latch - this can
                // actually tell the difference: it counts every call, not just whether one ever happened.
                for (int i = 0; i < 3; i++)
                {
                    tp.layout.configureAndLockPath(tp.path, dummyLoc());
                }

                assertTrue(tp.layout.getPathValidationFailureCount() == Layout.PATH_VALIDATION_ALERT_THRESHOLD + 3,
                    "The failure count must keep accumulating past the threshold");
                assertTrue(tp.layout.hasShownPathValidationAlert(),
                    "The alert latch must remain set (no reset, no repeat popups)");
                assertEquals(alertCalls[0], 1,
                    "showAutonomyAlert must still have been called only once - three more failures must "
                        + "not have raised three more popups");
            }
            finally
            {
                corrupting[0] = false;
            }

            // Keep the UI up briefly so the operator can actually read the single popup before the suite
            // tears it down (the alert is shown asynchronously on the EDT).
            Thread.sleep(5000);
        }
        finally
        {
            Layout.PATH_VALIDATION_ALERT_THRESHOLD = originalThreshold;
        }
    }

    /**
     * An edge whose configuration names an accessory that is not in the database cannot be set up, so
     * the path must be refused outright rather than locked and handed to a locomotive.  Otherwise the
     * switch is never commanded and the train runs over it in whatever position it was left in.
     *
     * This is deliberately independent of Path Integrity Validation and of simulation mode: that guard
     * asks whether the Central Station confirmed an actuation, whereas here no command is sent at all.
     */
    @Test
    public void testMissingAccessoryPreventsPathFromBeingUsed() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(51, "_missing");

        // No accessory can be called this - the maximum logical address is 320 (MM2) and 2048 (DCC)
        String missing = "Switch 99999";
        assertNull(model.getAccessoryByName(missing), "precondition: the accessory must not exist");

        tp.path.get(0).addConfigCommand(missing, accessorySetting.TURN);

        MarklinLocomotive loc = dummyLoc();

        assertFalse(tp.layout.isPathClear(tp.path, loc),
            "a path naming an accessory we do not have must not be reported as clear");

        assertFalse(tp.layout.configureAndLockPath(tp.path, loc),
            "the path must not be locked, nor reported as ready to run");

        assertFalse(tp.path.get(0).isOccupied(dummyLoc()),
            "the rejected path must not be left locked");

        assertTrue(model.getPowerState(),
            "power must stay on so the other locomotives are unaffected");

        // The rest of the layout is still usable - only this path is refused.  Rejecting it during the
        // preview means inspecting a path (pickPath, getPossiblePaths, debugPath) no longer has the
        // side effect of invalidating the whole configuration.
        assertTrue(tp.layout.isValid(),
            "one unusable path must not invalidate the entire layout");
    }

    /**
     * The same case end to end: executePath must refuse to release the locomotive at all.
     */
    @Test
    public void testMissingAccessoryPreventsLocomotiveFromDeparting() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(61, "_missingrun");

        String missing = "Switch 99999";
        assertNull(model.getAccessoryByName(missing), "precondition: the accessory must not exist");

        tp.path.get(0).addConfigCommand(missing, accessorySetting.TURN);

        MarklinLocomotive loc = dummyLoc();
        tp.layout.getPoint("A_missingrun").setLocomotive(loc);

        assertFalse(tp.layout.executePath(tp.path, loc, 30, null),
            "the locomotive must not be released onto a path that could not be set up");

        assertTrue(loc.getSpeed() == 0, "the locomotive must never have been started");

        assertFalse(tp.path.get(0).isOccupied(dummyLoc()),
            "the rejected path must not be left locked");
    }

    /**
     * Two paths holding one crossing, and the first to finish does not free it (RC-A9).
     *
     * `Edge.occupied` was a boolean, so it could hold one claim however many were made.  Two edges
     * naming a third as a lock edge is what a crossing looks like when the editor writes it, and with
     * a boolean the second lock wrote the same true the first had while the first release wrote false
     * for both - so the crossing went free with a train still on it.
     *
     * Nothing about atomicRoutes here: this is the flag itself, and it is shared by both modes.
     */
    @Test
    public void testTwoPathsHoldingOneCrossingBothHaveToLetGo() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        Layout layout = new Layout(model);

        layout.createPoint("CT_A", false, null);
        layout.createPoint("CT_B", false, null);
        layout.createPoint("CT_C", false, null);
        layout.createPoint("CT_D", false, null);
        layout.createPoint("CT_X", false, null);
        layout.createPoint("CT_Y", false, null);

        // The crossing, which is on neither path and protected by both
        Edge crossing = layout.createEdge("CT_X", "CT_Y");

        Edge first = layout.createEdge("CT_A", "CT_B");
        Edge second = layout.createEdge("CT_C", "CT_D");

        first.addLockEdge(crossing);
        second.addLockEdge(crossing);

        MarklinLocomotive asking = dummyLoc();

        first.setOccupied();
        second.setOccupied();

        assertTrue(crossing.isLockHeld(asking), "precondition: two locks make a crossing held");

        first.setUnoccupied();

        assertTrue(crossing.isLockHeld(asking),
            "one of the two paths across this crossing finished and the crossing went free, with the "
            + "other train still holding it.  A boolean cannot count claims: the second lock wrote the "
            + "same true as the first, and the first release wrote false for both (RC-A9)");

        second.setUnoccupied();

        assertFalse(crossing.isLockHeld(asking),
            "both paths let go and the crossing is still held, so a count is being raised more often "
            + "than it is lowered and this track is blocked for the rest of the session");
    }

    /**
     * A path does not give up an edge twice, because the second time it may not be its own (RC-A9).
     *
     * With atomicRoutes off, executePath releases each edge the moment the train's tail has provably
     * passed it - deliberately keeping that edge's own lock edges held, since a crossing behind the
     * train may still be in use - and unlockPath releases everything again at the end.  Two releases
     * per edge, which a boolean could not tell from one.
     *
     * The sequence, in the order the railway performs it:
     *
     *   A locks an edge with no lock edges of its own.
     *   A's tail passes it, so it is released early.
     *   B locks a DIFFERENT edge that names A's edge as a lock edge, so it is protected again.
     *   A reaches its destination and unlockPath walks A's path.
     *
     * The last step must not touch it.  It is not A's any more.
     *
     * The asymmetry this needs - B's edge names A's, A's names nothing - is what GraphEdgeEdit writes,
     * and 104 of the 118 relations in the shipped sample layout are asymmetric.
     */
    @Test
    public void testAPathDoesNotReleaseAnEdgeItHasAlreadyReleased() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        Layout layout = new Layout(model);
        layout.setAtomicRoutes(false);

        layout.createPoint("DR_A", false, null);

        MarklinFeedback fb = model.newFeedback(83, null);
        model.setFeedbackState(fb.getName(), false);
        layout.createPoint("DR_B", true, fb.getName());

        layout.createPoint("DR_M1", false, null);
        layout.createPoint("DR_M2", false, null);

        // A's path.  It names nothing, which is what lets a second train reach it at all.
        Edge shared = layout.createEdge("DR_A", "DR_B");

        // B's path, which protects A's edge while B is crossing
        Edge protector = layout.createEdge("DR_M1", "DR_M2");
        protector.addLockEdge(shared);

        MarklinLocomotive first = dummyLoc();
        MarklinLocomotive second = dummyLoc();
        MarklinLocomotive third = dummyLoc();

        layout.getPoint("DR_A").setLocomotive(first);

        List<Edge> path = Arrays.asList(shared);

        assertTrue(layout.configureAndLockPath(path, first), "precondition: the path locks cleanly");

        // What executePath does once the tail has provably passed: release the edge, and record that
        // it has been released.  Both halves, because unlockPath reads the record.
        java.lang.reflect.Field clearedField = Layout.class.getDeclaredField("clearedEdges");
        clearedField.setAccessible(true);

        java.util.Map<org.traincontrol.base.Locomotive, java.util.Set<Edge>> cleared =
            (java.util.Map<org.traincontrol.base.Locomotive, java.util.Set<Edge>>)
                clearedField.get(layout);

        cleared.put(first, java.util.concurrent.ConcurrentHashMap.<Edge>newKeySet());
        cleared.get(first).add(shared);

        shared.setLockedEdgeUnoccupied();

        assertFalse(shared.isLockHeld(third),
            "precondition: the early release lets the edge go");

        // And by now a second train has locked something that protects it
        protector.setOccupied();

        assertTrue(shared.isLockHeld(third),
            "precondition: the second train's lock protects the edge the first has left");

        layout.unlockPath(path, first);

        assertTrue(shared.isLockHeld(third),
            "the first train's path released this edge a SECOND time when it finished, and by then the "
            + "claim standing on it belonged to the second train - so the track is now free while a "
            + "train is crossing it, and the next path over it will be allowed out (RC-A9)");

        // And the second train letting go really does free it, so this is not simply stuck
        protector.setUnoccupied();

        assertFalse(shared.isLockHeld(third),
            "the second train let go and the edge is still held, so something is raising the count "
            + "more often than it lowers it and this track is blocked for the rest of the session");
    }

    /**
     * With atomic routes disabled, unlockPath must release a skipped edge's LOCK edges.
     *
     * executePath's early unlock frees each edge as the train clears it, using
     * setLockedEdgeUnoccupied - which deliberately leaves the edge's lock edges held until the path
     * completes, since a crossing may still be in use.  unlockPath is what finally releases them.  But
     * if another locomotive has claimed the edge's end point in the meantime, unlockPath skips the edge
     * to avoid clearing that locomotive's lock - and used to skip the lock edges along with it, leaving
     * the crossing marked occupied for the rest of the session and blocking every path through it.
     */
    @Test
    public void testUnlockPathReleasesLockEdgesOfASkippedEdge() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        Layout layout = new Layout(model);
        layout.setAtomicRoutes(false);

        layout.createPoint("LE_A", false, null);
        layout.createPoint("LE_B", false, null);

        MarklinFeedback fb = model.newFeedback(81, null);
        model.setFeedbackState(fb.getName(), false);
        layout.createPoint("LE_C", true, fb.getName());

        // A crossing that is not on the path, but is locked whenever the first edge is
        layout.createPoint("LE_X", false, null);
        layout.createPoint("LE_Y", false, null);
        Edge crossing = layout.createEdge("LE_X", "LE_Y");

        Edge ab = layout.createEdge("LE_A", "LE_B");
        Edge bc = layout.createEdge("LE_B", "LE_C");
        ab.addLockEdge(crossing);

        MarklinLocomotive loc = dummyLoc();
        MarklinLocomotive intruder = dummyLoc();

        layout.getPoint("LE_A").setLocomotive(loc);

        List<Edge> path = Arrays.asList(ab, bc);

        assertTrue(layout.configureAndLockPath(path, loc), "precondition: the path locks cleanly");
        assertTrue(crossing.isOccupied(dummyLoc()), "precondition: the crossing is locked with the path");

        // What executePath's early unlock does once the train has cleared the first edge: release the
        // edge but deliberately not its lock edges
        ab.setLockedEdgeUnoccupied();

        // ...and by then another locomotive has taken the point the train has just left
        layout.getPoint("LE_B").setLocomotive(intruder);

        layout.unlockPath(path, loc);

        assertFalse(crossing.isOccupied(dummyLoc()),
            "the crossing must be released even though its edge was skipped");
    }

    /**
     * Two different locomotives cannot end up on one square, however they are put there.
     *
     * The sweep in setLocomotive enforces "one locomotive, one place" - a train taken off every square
     * but the one claiming it.  This is the other invariant, "one square, one locomotive", and hand
     * placement is the only thing that can still break it: autonomy is refused by the block occupancy
     * check long before it gets here.
     *
     * Placing by hand displaces, deliberately - it is a person saying where a train actually is, and
     * whoever was there is by definition not.  But displacing only cleared the target Point, and a
     * square is several Points now, so putting a second train on the OTHER copy of an occupied platform
     * left both standing on one piece of track.  Which is a collision on the layout, and on the diagram
     * a caption naming two trains where the menu below it can only answer for one.
     */
    @Test
    public void testHandPlacingOnAnOccupiedSquareDisplacesWhoeverIsThere() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        Layout layout = new Layout(model);

        MarklinFeedback fb = model.newFeedback(85, null);
        model.setFeedbackState(fb.getName(), false);

        // One platform, emitted as two copies - which is what an arrival-side split produces
        layout.createPoint("BLK_east", true, fb.getName());
        layout.createPoint("BLK_west", true, fb.getName());

        layout.getPoint("BLK_east").setBlock("main:4,4");
        layout.getPoint("BLK_west").setBlock("main:4,4");

        // Through moveLocomotive, because that is the hand-placement door and the rule lives there.
        // Real locomotives, since it resolves them by name against the control station.
        String first = model.getLocList().get(0);
        String second = model.getLocList().get(1);

        assertTrue(layout.moveLocomotive(first, "BLK_east", false),
            "precondition: the first train can be placed");

        assertEquals(layout.getPoint("BLK_east").getCurrentLocomotive(),
            model.getLocByName(first), "precondition: it is standing on one copy");

        // and now somebody puts another train on the other copy of the same platform
        assertTrue(layout.moveLocomotive(second, "BLK_west", false),
            "the placement itself must still take effect");

        assertEquals(layout.getPoint("BLK_west").getCurrentLocomotive(),
            model.getLocByName(second));

        assertNull(layout.getPoint("BLK_east").getCurrentLocomotive(),
            "two trains were left standing on one square");
    }

    /**
     * A lock edge refuses a route when another route HOLDS it, and not merely because a train is parked
     * at the point it leads to.
     *
     * The two halves are one test on purpose.  Dropping the parked-train refusal is only safe because
     * the held-lock refusal exists, so an assertion that the first is gone means nothing without an
     * assertion that the second still bites - and someone reinstating the first would want to see, in
     * one place, what it was traded against.
     *
     * The parked train cannot be on the track the lock protects: reduction cuts an edge at every
     * sensor, so a Point's tile is an endpoint of the edges meeting there and part of the path of none
     * of them.  Counted as an obstruction anyway, a train standing next to a junction was a permanent
     * roadblock for every route across it, and two such trains deadlocked with no way out for either.
     */
    @Test
    public void testALockEdgeRefusesAHeldRouteButNotAParkedTrain() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        Layout layout = new Layout(model);
        layout.setAtomicRoutes(false);

        layout.createPoint("LK_A", false, null);
        layout.createPoint("LK_B", false, null);

        MarklinFeedback fb = model.newFeedback(83, null);
        model.setFeedbackState(fb.getName(), false);
        layout.createPoint("LK_C", true, fb.getName());

        // A crossing off the path, locked whenever the first edge is, with somewhere for a train to
        // stand at the end of it
        MarklinFeedback beyond = model.newFeedback(84, null);
        model.setFeedbackState(beyond.getName(), false);

        layout.createPoint("LK_X", false, null);
        layout.createPoint("LK_Y", true, beyond.getName());
        Edge crossing = layout.createEdge("LK_X", "LK_Y");

        Edge ab = layout.createEdge("LK_A", "LK_B");
        Edge bc = layout.createEdge("LK_B", "LK_C");
        ab.addLockEdge(crossing);

        MarklinLocomotive loc = dummyLoc();
        MarklinLocomotive parked = dummyLoc();

        layout.getPoint("LK_A").setLocomotive(loc);

        List<Edge> path = Arrays.asList(ab, bc);

        assertTrue(layout.isPathClear(path, loc), "precondition: the path is clear with nothing about");

        // A train standing at the far end of the crossing.  It is not on the crossing.
        layout.getPoint("LK_Y").setLocomotive(parked);

        assertTrue(layout.isPathClear(path, loc),
            "a train parked at the point a lock edge leads to is not on the track the lock protects");

        // ...but a route that has actually taken that track is.
        crossing.setOccupied();

        assertFalse(layout.isPathClear(path, loc),
            "a lock edge held by another route must refuse this one");

        crossing.setUnoccupied();

        assertTrue(layout.isPathClear(path, loc), "and is offered again once that route releases it");
    }

    /**
     * Control: the identical path runs normally once the accessory exists, confirming the tests above
     * fail for the missing accessory and not for anything else in the setup.
     */
    @Test
    public void testPathRunsOnceTheAccessoryExists() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(71, "_present");

        MarklinAccessory extra = model.newSwitch(75, MM2, false);
        tp.path.get(0).addConfigCommand(extra.getName(), accessorySetting.TURN);

        MarklinLocomotive loc = dummyLoc();

        assertTrue(tp.layout.isPathClear(tp.path, loc),
            "control: with every accessory present the path is clear");

        assertTrue(tp.layout.configureAndLockPath(tp.path, loc),
            "control: with every accessory present the path locks and is ready");
    }
    /**
     * A path that fails to configure leaves the train at its start, not on no point at all.
     *
     * handleMisconfiguredPath releases the locks and, by its own promise, leaves the train "at its
     * start point (it never departed)".  The sweep broke that promise: the start had already been swept
     * off during locking, so releasing the path's end points left the train nowhere - and a train on no
     * point is invisible to pickPath and drops out of autonomy until a reload.
     */
    @Test
    public void testAFailedConfigurationLeavesTheTrainAtItsStart() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildThreePointPath(60, "_strand");

        MarklinLocomotive loc = dummyLoc();

        tp.layout.getPoint("A_strand").setLocomotive(loc);

        boolean[] corrupting = startCorrupting(tp);

        try
        {
            boolean result = tp.layout.configureAndLockPath(tp.path, loc);

            assertFalse(result, "a path whose accessories never confirm must not lock");
        }
        finally
        {
            corrupting[0] = false;
        }

        assertEquals(tp.layout.getPoint("A_strand").getCurrentLocomotive(), loc,
            "the train was stranded on no point and has dropped out of autonomy");

        assertNull(tp.layout.getPoint("B_strand").getCurrentLocomotive(),
            "a released path must not leave the train reserving track it never reached");

        assertNull(tp.layout.getPoint("C_strand").getCurrentLocomotive());
    }

}
