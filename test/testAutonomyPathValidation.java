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

    @BeforeClass
    public static void setUpClass() throws Exception
    {
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
    public static void tearDownClass()
    {
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = false;
        Layout.PATH_VALIDATION_MS = 1000;
        Layout.PATH_INTEGRITY_VALIDATION = true;
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
        TestPath tp = new TestPath();

        tp.layout = new Layout(model);
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
        assertTrue(loc.getSpeed() == 0, "The locomotive must be stopped");
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
     */
    @Test
    public void testUiAlertFiresAtMostOncePerLayout() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        int originalThreshold = Layout.PATH_VALIDATION_ALERT_THRESHOLD;
        Layout.PATH_VALIDATION_ALERT_THRESHOLD = 3;

        try
        {
            TestPath tp = buildPath(41, "_thresh");
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
                }

                // The failure that reaches the threshold fires the one-time alert.
                tp.layout.configureAndLockPath(tp.path, dummyLoc());
                assertTrue(tp.layout.hasShownPathValidationAlert(),
                    "Alert must fire once the threshold is reached");

                // Further failures keep accumulating (the count is never reset) but must not re-alert -
                // the latch stays true and no second popup is raised.
                for (int i = 0; i < 3; i++)
                {
                    tp.layout.configureAndLockPath(tp.path, dummyLoc());
                }

                assertTrue(tp.layout.getPathValidationFailureCount() == Layout.PATH_VALIDATION_ALERT_THRESHOLD + 3,
                    "The failure count must keep accumulating past the threshold");
                assertTrue(tp.layout.hasShownPathValidationAlert(),
                    "The alert latch must remain set (no reset, no repeat popups)");
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
}
