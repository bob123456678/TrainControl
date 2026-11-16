import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.model.ViewListener;

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
        return buildPath(addressBase, suffix, false);
    }

    /**
     * The same, with the start a station when startIsStation, as a path autonomy picks must start (its sensor
     * is addressBase + 4).
     */
    private TestPath buildPath(int addressBase, String suffix, boolean startIsStation) throws Exception
    {
        TestPath tp = new TestPath();

        tp.layout = new Layout(model);

        if (startIsStation)
        {
            MarklinFeedback start = model.newFeedback(addressBase + 4, null);
            model.setFeedbackState(start.getName(), false);
            tp.layout.createPoint("A" + suffix, true, start.getName());
        }
        else
        {
            tp.layout.createPoint("A" + suffix, false, null);
        }

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
     * The validation period the busy-station claims run with.  Each echo buys two periods, a full second, so a
     * stall of half a second on a loaded machine cannot end a wait that the stand-in station is keeping alive.
     */
    private static final int BUSY_PERIOD = 500;

    /**
     * A full 13-byte accessory echo, as the Central Station sends it.  receiveMessage only routes a message
     * whose response bit (the low bit of the second byte) is set, and it drops a packet identical to the one
     * before it, so callers alternate settings or accessories.
     *
     * @param switched true for turned, false for straight
     */
    private static CS2Message accessoryEcho(MarklinAccessory acc, boolean switched)
    {
        int uid = acc.getUID();
        byte[] raw = new byte[CS2Message.MESSAGE_LENGTH];

        raw[1] = (byte) ((CS2Message.CMD_ACC_SWITCH << 1) | 1);
        raw[2] = 0x47;
        raw[3] = 0x11;
        raw[4] = 6;
        raw[5] = (byte) (uid >> 24);
        raw[6] = (byte) (uid >> 16);
        raw[7] = (byte) (uid >> 8);
        raw[8] = (byte) uid;
        raw[9] = (byte) (switched ? 0 : 1);
        raw[10] = 1;

        return new CS2Message(raw);
    }

    /**
     * One thing the stand-in station does, after a pause.
     */
    private static class Step
    {
        final long pauseMs;
        final Runnable action;

        Step(long pauseMs, Runnable action)
        {
            this.pauseMs = pauseMs;
            this.action = action;
        }
    }

    /**
     * Adds count echoes of acc, alternating turned and straight, one every 50 ms.
     */
    private static void busyWith(List<Step> steps, MarklinAccessory acc, int count)
    {
        for (int i = 0; i < count; i++)
        {
            boolean switched = i % 2 == 0;
            steps.add(new Step(50, () -> model.receiveMessage(accessoryEcho(acc, switched))));
        }
    }

    /**
     * Adds an echo of acc after the given pause.
     */
    private static void answer(List<Step> steps, long pauseMs, MarklinAccessory acc, boolean switched)
    {
        steps.add(new Step(pauseMs, () -> model.receiveMessage(accessoryEcho(acc, switched))));
    }

    /**
     * The track power going off, as the Central Station reports it.
     */
    private static void powerOff(List<Step> steps)
    {
        steps.add(new Step(50, () -> model.receiveMessage(new CS2Message(CS2Message.CMD_SYSTEM,
            new byte[] { 0, 0, 0, 0, (byte) CS2Message.CMD_SYSSUB_STOP }))));
    }

    /**
     * A stand-in station that runs its steps until it runs out or is stopped.
     */
    private static class Station extends Thread
    {
        private final List<Step> steps;
        private volatile boolean stopped = false;

        Station(List<Step> steps)
        {
            this.steps = steps;
            this.setDaemon(true);
        }

        @Override
        public void run()
        {
            try
            {
                for (Step s : steps)
                {
                    Thread.sleep(s.pauseMs);

                    if (stopped) return;

                    s.action.run();
                }
            }
            catch (InterruptedException ex)
            {
                Thread.currentThread().interrupt();
            }
        }

        void finish() throws InterruptedException
        {
            stopped = true;
            this.join();
        }
    }

    /**
     * Configures the path with BUSY_PERIOD and the simulated echo off, so its switches are answered only by the
     * stand-in station.  Returns how long configureAndLockPath took, in ms, and puts its result in result[0].
     */
    private long timePathWithStation(TestPath tp, List<Step> steps, boolean[] result) throws Exception
    {
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = false;
        Layout.PATH_VALIDATION_MS = BUSY_PERIOD;
        Station station = new Station(steps);

        try
        {
            station.start();
            long start = System.currentTimeMillis();
            result[0] = tp.layout.configureAndLockPath(tp.path, dummyLoc());
            return System.currentTimeMillis() - start;
        }
        finally
        {
            station.finish();
            Layout.PATH_VALIDATION_MS = 100;
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;
        }
    }

    /**
     * How soon a power cut or a stop must end the wait, in ms from the start of the path.  The cut lands about
     * 0.55 s in, and the wait looks for it every 250 ms; the station goes on answering until about 3.6 s in.
     */
    private static final long CUT_ENDS_THE_WAIT_MS = 2000;

    /**
     * The longest a busy station is waited for, in ms.
     */
    private static long busyCap()
    {
        return (long) BUSY_PERIOD * Layout.PATH_VALIDATION_BUSY_MAX;
    }

    /**
     * A station still answering other accessories is waited for: a CS3 switches one at a time, about a second
     * each, so a path's switches can be answered long after PATH_VALIDATION_MS * (switches + 1).  Here the
     * path's two switches are answered after 3 s of echoes for another switch, twice the plain deadline.
     */
    @Test(timeOut = 60000)
    public void testABusyStationIsWaitedFor() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(140, "_busy");
        MarklinAccessory other = model.newSwitch(143, MM2, false);

        List<Step> steps = new ArrayList<>();
        busyWith(steps, other, 60);
        answer(steps, 50, tp.acc1, true);
        answer(steps, 50, tp.acc2, true);

        boolean[] result = new boolean[1];
        timePathWithStation(tp, steps, result);

        assertTrue(result[0], "A path whose switches are answered after a busy station's backlog must pass");
    }

    /**
     * After another train's backlog a CS3 answers the path's own switches about a second apart, so each of those
     * answers keeps the wait alive too.  Otherwise the third switch lands after the deadline the backlog left,
     * every time, and the train is stopped with "could not be confirmed".
     */
    @Test(timeOut = 60000)
    public void testThreeSwitchesAnsweredAboutAPeriodApartAfterABacklogPass() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(180, "_three");
        MarklinAccessory acc3 = model.newSwitch(182, MM2, false);
        tp.path.get(0).addConfigCommand(acc3.getName(), accessorySetting.TURN);
        MarklinAccessory other = model.newSwitch(183, MM2, false);

        long apart = BUSY_PERIOD * 9 / 10;

        List<Step> steps = new ArrayList<>();
        busyWith(steps, other, 60);
        answer(steps, apart, tp.acc1, true);
        answer(steps, apart, tp.acc2, true);
        answer(steps, apart, acc3, true);

        boolean[] result = new boolean[1];
        timePathWithStation(tp, steps, result);

        assertTrue(result[0], "Three switches answered about a period apart after a backlog must pass");
        assertEquals(tp.layout.getPathValidationFailureCount(), 0, "Nothing may be counted against the accessories");
    }

    /**
     * A station that keeps answering other accessories but never ours is waited for only up to
     * PATH_VALIDATION_BUSY_MAX periods, so a lost echo cannot hold the train forever.
     */
    @Test(timeOut = 60000)
    public void testABusyStationIsWaitedForOnlyUpToTheCap() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(150, "_cap");
        MarklinAccessory other = model.newSwitch(153, MM2, false);

        // Far longer than the cap: the station is stopped once the path gives up
        List<Step> steps = new ArrayList<>();
        busyWith(steps, other, (int) (busyCap() / 50) * 2);

        boolean[] result = new boolean[1];
        long elapsed = timePathWithStation(tp, steps, result);

        assertFalse(result[0], "A path whose switches are never answered must fail");
        assertTrue(elapsed >= busyCap(), "The busy station must be waited for up to the cap, but the path failed "
            + "after " + elapsed + " ms (cap " + busyCap() + " ms)");
        assertTrue(elapsed < busyCap() + 2500, "The cap must end the wait, but the path took " + elapsed + " ms (cap "
            + busyCap() + " ms)");
    }

    /**
     * A silent station still fails at the plain deadline, PATH_VALIDATION_MS * (switches + 1), well short of the
     * cap.
     */
    @Test(timeOut = 60000)
    public void testASilentStationStillTimesOutOnTime() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(160, "_silent");

        boolean[] result = new boolean[1];
        long elapsed = timePathWithStation(tp, new ArrayList<>(), result);

        assertFalse(result[0], "A path whose switches are never answered must fail");
        assertTrue(elapsed < busyCap() / 2, "A silent station must not be waited for, but the path took " + elapsed
            + " ms");
    }

    /**
     * Echoes of the path's own switches in the wrong position do not count as a busy station: a path that the
     * station keeps reporting switched back still fails at the plain deadline, well short of the cap.
     */
    @Test(timeOut = 60000)
    public void testEchoesOfThePathsOwnSwitchesDoNotExtendTheWait() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(170, "_own");

        // Both switches answered straight, the opposite of the path's TURN, for far longer than the cap
        List<Step> steps = new ArrayList<>();

        for (int i = 0; i < (int) (busyCap() / 50) * 2; i++)
        {
            answer(steps, 50, i % 2 == 0 ? tp.acc1 : tp.acc2, false);
        }

        boolean[] result = new boolean[1];
        long elapsed = timePathWithStation(tp, steps, result);

        assertFalse(result[0], "A path its own switches contradict must fail");
        assertTrue(elapsed < busyCap() / 2, "Echoes of the path's own switches in the wrong position must not "
            + "extend the wait, but the path took " + elapsed + " ms");
    }

    /**
     * The track power going off ends the wait: a station that goes on answering after the cut must not confirm
     * the path, or the train would set off as soon as the power came back.  The train stays where it is, and
     * nothing is counted against the accessories.
     */
    @Test(timeOut = 60000)
    public void testAPowerCutDuringTheWaitKeepsTheTrainBack() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(190, "_power");
        MarklinAccessory other = model.newSwitch(193, MM2, false);

        // The station goes on answering for 3 s after the cut, which would keep the wait alive that long
        List<Step> steps = new ArrayList<>();
        busyWith(steps, other, 10);
        powerOff(steps);
        busyWith(steps, other, 60);
        answer(steps, 50, tp.acc1, true);
        answer(steps, 50, tp.acc2, true);

        boolean[] result = new boolean[1];
        long elapsed;

        try
        {
            elapsed = timePathWithStation(tp, steps, result);
        }
        finally
        {
            model.go();
            waitForPower(true, 1000);
        }

        assertFalse(result[0], "A path whose wait the power cut ended must not be run");
        assertTrue(elapsed < CUT_ENDS_THE_WAIT_MS, "The power cut must end the wait at once, but the path took "
            + elapsed + " ms");
        assertEquals(tp.layout.getPathValidationFailureCount(), 0, "A power cut is not a misconfigured path");
        assertFalse(tp.path.get(0).isOccupied(dummyLoc()), "The path must be released");
    }

    /**
     * Stopping autonomy gracefully ends the wait of a train autonomy sent: it does not depart, and nothing is
     * counted against the accessories.
     */
    @Test(timeOut = 60000)
    public void testStoppingAutonomyDuringTheWaitKeepsTheTrainBack() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(200, "_stop", true);
        MarklinAccessory other = model.newSwitch(203, MM2, false);

        // A run with no locomotives of its own, so only this path is sent
        tp.layout.runLocomotives();
        assertTrue(tp.layout.isPathClear(tp.path, dummyLoc()), "precondition: autonomy may send this path");

        // The station goes on answering for 3 s after the stop, which would keep the wait alive that long
        List<Step> steps = new ArrayList<>();
        busyWith(steps, other, 10);
        steps.add(new Step(50, () -> tp.layout.stopLocomotives()));
        busyWith(steps, other, 60);
        answer(steps, 50, tp.acc1, true);
        answer(steps, 50, tp.acc2, true);

        boolean[] result = new boolean[1];
        long elapsed;

        try
        {
            elapsed = timePathWithStation(tp, steps, result);
        }
        finally
        {
            tp.layout.stopLocomotives();
        }

        assertFalse(result[0], "A train autonomy sent must not depart after autonomy was stopped");
        assertTrue(elapsed < CUT_ENDS_THE_WAIT_MS, "Stopping autonomy must end the wait at once, but the path took "
            + elapsed + " ms");
        assertEquals(tp.layout.getPathValidationFailureCount(), 0, "A stop is not a misconfigured path");
        assertFalse(tp.path.get(0).isOccupied(dummyLoc()), "The path must be released");
    }

    /**
     * No train is given a speed while the power is off, even with Path Integrity Validation turned off: the
     * station would remember the speed and the train would set off when the power came back.
     */
    @Test(timeOut = 60000)
    public void testNoTrainDepartsWhileThePowerIsOff() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        TestPath tp = buildPath(210, "_off");
        MarklinLocomotive loc = dummyLoc();
        tp.layout.getPoint("A_off").setLocomotive(loc);

        boolean[] result = { true };
        Thread send = new Thread(() -> result[0] = tp.layout.executePath(tp.path, loc, 30, null));
        send.setDaemon(true);

        Layout.PATH_INTEGRITY_VALIDATION = false;

        try
        {
            model.stop();
            waitForPower(false, 1000);
            assertFalse(model.getPowerState(), "precondition: the power is off");

            send.start();
            send.join(5000);
        }
        finally
        {
            Layout.PATH_INTEGRITY_VALIDATION = true;
            model.go();
            waitForPower(true, 1000);
        }

        assertFalse(send.isAlive(), "The train must not have been sent on its way");
        assertFalse(result[0], "executePath must refuse while the power is off");
        assertEquals(loc.getSpeed(), 0, "The train must not have been given a speed");
        assertFalse(tp.path.get(0).isOccupied(dummyLoc()), "The path must be released");
    }

    /**
     * A Stop and a Go while the path's switches are still being commanded keep the train back.  Commanding takes
     * about 150 ms a switch, so on a long path a quick Stop and Go fits inside it, and a station that echoes
     * commands while stopped confirms switches that never moved.  The train is released unrun.
     */
    @Test(timeOut = 60000)
    public void testAStopAndGoWhileTheRouteIsSetKeepsTheTrainBack() throws Exception
    {
        stopAndGoWhileTheRouteIsSet(300, "_sg", true);
    }

    /**
     * The same with Path Integrity Validation turned off.
     */
    @Test(timeOut = 60000)
    public void testAStopAndGoWhileTheRouteIsSetKeepsTheTrainBackWithoutValidation() throws Exception
    {
        stopAndGoWhileTheRouteIsSet(320, "_sgo", false);
    }

    private void stopAndGoWhileTheRouteIsSet(int addressBase, String suffix, boolean validate) throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        // Ten switches, so commanding the path takes about 1.6 s
        TestPath tp = buildPath(addressBase, suffix);

        for (int i = 2; i < 10; i++)
        {
            tp.path.get(0).addConfigCommand(model.newSwitch(addressBase + i, MM2, false).getName(),
                accessorySetting.TURN);
        }

        MarklinLocomotive loc = dummyLoc();
        tp.layout.getPoint("A" + suffix).setLocomotive(loc);

        boolean[] result = { true };
        Thread send = new Thread(() -> result[0] = tp.layout.executePath(tp.path, loc, 30, null));
        send.setDaemon(true);

        Layout.PATH_INTEGRITY_VALIDATION = validate;

        try
        {
            send.start();
            Thread.sleep(300);

            model.stop();
            waitForPower(false, 1000);
            assertFalse(model.getPowerState(), "precondition: the power went off");

            model.go();
            waitForPower(true, 1000);
            assertTrue(model.getPowerState(), "precondition: the power came back");
            assertEquals(loc.getSpeed(), 0, "precondition: the Stop and Go must land before the train sets off");

            send.join(5000);
        }
        finally
        {
            Layout.PATH_INTEGRITY_VALIDATION = true;
        }

        assertFalse(send.isAlive(), "The train must not have been sent on its way");
        assertFalse(result[0], "executePath must refuse a path the power was cut during");
        assertEquals(loc.getSpeed(), 0, "The train must not have been given a speed");
        assertFalse(tp.path.get(0).isOccupied(dummyLoc()), "The path must be released");
    }

    /**
     * With the track power off, autonomy waits for it and says so once.  It used to set the path again at every
     * retry, a line for each switch and another that the train was not sent.  Once the power is back the train
     * sets off.
     */
    @Test(timeOut = 60000)
    public void testAutonomyWaitsForThePowerAndSaysSoOnce() throws Exception
    {
        model.go();
        waitForPower(true, 1000);

        final List<String> keys = Collections.synchronizedList(new ArrayList<>());

        // The model, with every message key it is asked to log written down on the way through
        ViewListener recording = (ViewListener) Proxy.newProxyInstance(
            ViewListener.class.getClassLoader(), new Class<?>[]{ ViewListener.class }, (proxy, method, args) ->
            {
                if (method.getName().equals("logf")) keys.add((String) args[0]);

                try
                {
                    return method.invoke(model, args);
                }
                catch (InvocationTargetException e)
                {
                    throw e.getCause();
                }
            });

        Layout layout = new Layout(recording);

        MarklinFeedback atA = model.newFeedback(344, null);
        model.setFeedbackState(atA.getName(), false);
        layout.createPoint("A_wait", true, atA.getName());

        MarklinFeedback atB = model.newFeedback(345, null);
        model.setFeedbackState(atB.getName(), false);
        layout.createPoint("B_wait", true, atB.getName());

        Edge edge = layout.createEdge("A_wait", "B_wait");
        edge.addConfigCommand(model.newSwitch(340, MM2, false).getName(), accessorySetting.TURN);
        edge.addConfigCommand(model.newSwitch(341, MM2, false).getName(), accessorySetting.TURN);

        String name = "PV waiting loc";
        MarklinLocomotive loc = model.newMM2Locomotive(name, 79);

        try
        {
            loc.setPreferredSpeed(30);
            assertTrue(layout.moveLocomotive(name, "A_wait", false), "precondition: the train was placed");

            // A retry every second
            layout.setMaxDelay(1);
            layout.setMinDelay(1);
            layout.setSimulate(true);

            model.stop();
            waitForPower(false, 1000);
            assertFalse(model.getPowerState(), "precondition: the power is off");

            keys.clear();
            layout.runLocomotives();

            // Several retry periods with the power off
            Thread.sleep(3500);

            List<String> duringTheCut = new ArrayList<>(keys);

            model.go();
            waitForPower(true, 1000);

            long deadline = System.currentTimeMillis() + 10000;

            while (!keys.contains("autolayout.infoExecutingPathForLocomotive") && System.currentTimeMillis() < deadline)
            {
                Thread.sleep(50);
            }

            assertEquals(Collections.frequency(duringTheCut, "autolayout.infoConfiguringAccessory"), 0,
                "No switch may be commanded while the power is off: " + duringTheCut);
            assertEquals(Collections.frequency(duringTheCut, "autolayout.infoWaitingForTrackPower"), 1,
                "The wait for the power must be logged once: " + duringTheCut);
            assertTrue(keys.contains("autolayout.infoExecutingPathForLocomotive"),
                "The train must set off once the power is back: " + keys);
        }
        finally
        {
            layout.stopLocomotives();
            model.go();
            waitForPower(true, 1000);

            // The train finishes the path it set off on before it is deleted
            long until = System.currentTimeMillis() + 20000;

            while (layout.isRunning() && System.currentTimeMillis() < until)
            {
                Thread.sleep(50);
            }

            model.deleteLoc(name);
        }
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
