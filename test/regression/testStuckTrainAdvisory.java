package regression;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train that was sent somewhere and has not arrived says so.  A route waiting on its sensor does not (MT-037:
 * shrink the quota, put a train on a station with two or more sensors between it and its target, trigger the first by
 * hand, wait past the quota, and look at the log; simulation OFF for autonomy, because a simulated run triggers the
 * sensors for you).
 *
 *  - It must fire: a train sent onto a path it never completes (lifted off, power cut, a sensor that failed to make)
 *    otherwise waits silently and for ever.
 *  - It must not fire for a route: a route's trigger monitor sits on its sensor for as long as the layout runs, through
 *    the same wait, on a locomotive called "Dummy Loc"; an advisory inside the wait announced a phantom stuck train per
 *    enabled route every five minutes.
 *
 * So the quota is passed in by the CALLER rather than read inside the wait: "this train was dispatched and is on its
 * way" is a fact the dispatch loop has and the wait does not.  The quota is a second rather than Adam's ten, which
 * only matters for watching a real railway by hand; FEEDBACK_ADVISORY_MS is a public volatile field so a test can
 * shrink it.
 */
public class testStuckTrainAdvisory
{
    private static MarklinControlStation model;

    private static final List<String> logged = new CopyOnWriteArrayList<>();

    private static Handler listener;

    private static long quotaWas;

    /** Long enough to be sure the wait has had its chance, short enough not to pad the battery. */
    private static final long QUOTA_MS = 1000;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Simulate and debug, as Adam's procedure says; the application's simulation only stops it talking to a Central
        // Station.
        model = support.TestStation.simulatedInDebug();

        model.stop();

        quotaWas = Locomotive.FEEDBACK_ADVISORY_MS;

        Locomotive.FEEDBACK_ADVISORY_MS = QUOTA_MS;

        // Everything the model logs, captured where it goes: log(String) writes to this logger, and to the view when
        // there is one.
        listener = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                if (record != null && record.getMessage() != null) logged.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(listener);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        Locomotive.FEEDBACK_ADVISORY_MS = quotaWas;

        if (listener != null)
        {
            Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(listener);
        }
    }

    /**
     * A train dispatched over two sensors, stopped after the first, is named in the log.  The whole path, not the wait
     * on its own: the advisory is asked for by the dispatch loop, and the defect was twice about which CALLER asks.
     */
    @Test
    public void testATrainThatStopsBetweenSensorsIsNamed() throws Exception
    {
        logged.clear();

        Layout layout = threeStations("MT37");

        // Autonomy simulation OFF: with it on, the sensors are triggered for us and no train is ever stuck.
        layout.setSimulate(false);

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "MT37_A", false);

        List<Edge> path = layout.bfs(layout.getPoint("MT37_A"), layout.getPoint("MT37_C"), null);

        assertNotNull(path, "the fixture has no route from A to C");

        assertEquals(path.size(), 2, "the train must cross TWO sensors, or nothing waits for a second");

        Thread dispatch = new Thread(() -> layout.executePath(path, loc, 30, null));

        dispatch.setDaemon(true);
        dispatch.start();

        // Under way FIRST: the dispatch checks the whole path is clear before it takes it, so a sensor made before that
        // refuses the path for being occupied.
        assertTrue(startedMoving(loc), "the train never started: " + logged);

        // The first sensor makes, by hand - the train has reached the middle station
        occupy(layout, "MT37_B");

        // And the second never does: past the quota, plus enough for the dispatch loop to have settled.
        Thread.sleep(QUOTA_MS * 4);

        String said = find("has not reached");

        assertNotNull(said, "nothing was said about a train that was dispatched and has stopped.  "
            + "The log held: " + logged);

        assertTrue(said.contains(loc.getName()),
            "the advisory does not name the train, so the operator does not know which one to go and "
            + "look at: " + said);

        assertTrue(said.contains(waitingFor(layout, "MT37_C")),
            "the advisory does not name the SENSOR it is waiting for, which is the other half of "
            + "where to look: " + said);

        dispatch.interrupt();
    }

    /**
     * And a route's trigger monitor, waiting on its sensor for ever, says nothing at all: `MarklinRoute` waits on its
     * trigger through this same wait, on "Dummy Loc".
     */
    @Test
    public void testARouteWaitingOnItsSensorSaysNothing() throws Exception
    {
        logged.clear();

        MarklinLocomotive dummy = new MarklinLocomotive(model, 1,
            MarklinLocomotive.decoderType.MM2, "Dummy Loc");

        MarklinFeedback sensor = model.newFeedback(77, null);

        model.setFeedbackState(sensor.getName(), false);

        // The two-argument wait, which is the door everything except the dispatch loop comes in by
        Thread watching = new Thread(() ->
            dummy.waitForOccupiedFeedback(sensor.getName(), 0));

        watching.setDaemon(true);
        watching.start();

        Thread.sleep(QUOTA_MS * 4);

        // The precondition that makes the two assertNull below mean anything: a bare Thread swallows a throw on its
        // first line, and a regression test that cannot fail is worse than none.
        assertTrue(watching.isAlive(),
            "the watching thread is not running, so nothing waited and the assertions below prove "
            + "nothing");

        assertNull(find("has not reached"),
            "a route waiting on its trigger sensor announced a stuck train.  That is what every "
            + "enabled route on the layout would do, every quota, for ever: " + logged);

        assertNull(find("Dummy Loc"),
            "the locomotive routes borrow to watch their sensors has been mentioned to the user at "
            + "all, which is a name from the inside of the program: " + logged);

        watching.interrupt();
    }

    /** The advisory is said once, not every time round the loop. */
    @Test
    public void testItIsSaidOnce() throws Exception
    {
        logged.clear();

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        MarklinFeedback sensor = model.newFeedback(78, null);

        model.setFeedbackState(sensor.getName(), false);

        Thread waiting = new Thread(() ->
            loc.waitForOccupiedFeedback(sensor.getName(), 0, QUOTA_MS));

        waiting.setDaemon(true);
        waiting.start();

        Thread.sleep(QUOTA_MS * 5);

        int said = 0;

        for (String line : logged)
        {
            if (line.contains("has not reached")) said++;
        }

        assertEquals(said, 1, "the advisory was said " + said + " times over five quotas");

        waiting.interrupt();
    }

    /** Three stations in a line, so a train sent from one end crosses two sensors to reach the other. */
    private static Layout threeStations(String prefix) throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback a = model.newFeedback(71, null);
        MarklinFeedback b = model.newFeedback(72, null);
        MarklinFeedback c = model.newFeedback(73, null);

        model.setFeedbackState(a.getName(), true);
        model.setFeedbackState(b.getName(), false);
        model.setFeedbackState(c.getName(), false);

        layout.createPoint(prefix + "_A", true, a.getName());
        layout.createPoint(prefix + "_B", true, b.getName());
        layout.createPoint(prefix + "_C", true, c.getName());

        layout.createEdge(prefix + "_A", prefix + "_B");
        layout.createEdge(prefix + "_B", prefix + "_C");

        return layout;
    }

    /**
     * A sensor that makes and lets go does not restart the clock.  The wait starts again on a flicker, and the elapsed
     * time in the advisory must still be measured from when the train was SENT; the restart carried a REMAINDER of the
     * quota instead of the origin, so a train missing for five and a half minutes was announced after "0 minutes".
     *
     * This watches the number the hook is handed rather than the sentence, because the sentence rounds to whole
     * minutes and every number in a test this short rounds to zero.
     */
    @Test
    public void testAFlickerDoesNotRestartTheClock() throws Exception
    {
        Watcher watcher = new Watcher(model);

        MarklinFeedback sensor = model.newFeedback(79, null);

        model.setFeedbackState(sensor.getName(), false);

        final long quota = QUOTA_MS * 2;

        long began = System.currentTimeMillis();

        // minDuration means the sensor must STAY made, so a blip sends it round again
        Thread waiting = new Thread(() ->
            watcher.waitForOccupiedFeedback(sensor.getName(), 500, quota));

        waiting.setDaemon(true);
        waiting.start();

        // Well before the quota: make it, and let go before the duration is up
        Thread.sleep(300);

        model.setFeedbackState(sensor.getName(), true);

        Thread.sleep(100);

        model.setFeedbackState(sensor.getName(), false);

        // Now past the quota, measured from the START rather than from the flicker
        Thread.sleep(quota + 800);

        assertTrue(watcher.reported > 0,
            "the advisory never fired at all, so this test proves nothing about when it fires");

        long sinceTheStart = System.currentTimeMillis() - began;

        assertTrue(watcher.reported >= quota - 200,
            "the advisory reported " + watcher.reported + "ms, but the train has been waiting for "
            + sinceTheStart + "ms and the quota is " + quota + "ms.  The clock restarted at the "
            + "flicker, so a train missing for minutes is described as freshly sent");

        waiting.interrupt();
    }

    /** A locomotive that writes down what the advisory hook was handed (`waitedTooLongFor` is protected, for this). */
    private static final class Watcher extends MarklinLocomotive
    {
        volatile long reported = -1;

        Watcher(MarklinControlStation model)
        {
            super(model, 2, MarklinLocomotive.decoderType.MM2, "Watcher");
        }

        @Override
        protected void waitedTooLongFor(String feedbackName, long waitedMs)
        {
            reported = waitedMs;
        }
    }

    /** Waits until the dispatch has actually set the train going, within a few seconds. */
    private static boolean startedMoving(MarklinLocomotive loc) throws Exception
    {
        for (int waited = 0; waited < 60; waited++)
        {
            if (loc.getSpeed() > 0) return true;

            Thread.sleep(50);
        }

        return false;
    }

    /** Makes a station's sensor by hand, as Adam tests this: clicking the s88 on the track diagram. */
    private static void occupy(Layout layout, String point)
    {
        String sensor = layout.getPoint(point).getS88();

        assertNotNull(sensor, "no sensor on " + point);

        model.setFeedbackState(sensor, true);
    }

    /** The sensor a station's Point is watching, as the advisory names it. */
    private static String waitingFor(Layout layout, String point)
    {
        return layout.getPoint(point).getS88();
    }

    private static String find(String fragment)
    {
        for (String line : logged)
        {
            if (line != null && line.contains(fragment)) return line;
        }

        return null;
    }
}
