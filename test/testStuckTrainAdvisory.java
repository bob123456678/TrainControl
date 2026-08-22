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
 * A train that was sent somewhere and has not arrived says so.  A route waiting on its sensor does not.
 *
 * MT-037, written to Adam's specification: shrink the quota, put a train on a station with two or more
 * sensors between it and its target, trigger the first by hand, wait past the quota, and look at the
 * log.  Debug and simulate on for the application; simulation OFF for autonomy, because a simulated run
 * triggers the sensors for you and the train is then never stuck.
 *
 * Both halves matter, and they pull in opposite directions:
 *
 *  - **It must fire.**  A train sent onto a path that it never completes - lifted off, power cut at the
 *    locomotive, a sensor that failed to make - waits for its next sensor silently and for ever.  That
 *    is by design, and the design is right; what was missing was anybody saying so out loud.
 *  - **It must not fire for a route.**  A route's trigger monitor sits on its sensor for as long as the
 *    layout runs, and it does that through the same wait, on a locomotive called "Dummy Loc" that
 *    exists only to borrow these utilities.  When the advisory was first written it went inside the
 *    wait, so every enabled route in the layout announced a phantom stuck train every five minutes.
 *
 * That is why the quota is passed in by the CALLER rather than read inside the wait: "this train was
 * dispatched and is on its way" is a fact the dispatch loop has and the wait does not.  These tests
 * cover both sides of that split, and the second one is the regression test for the leak.
 *
 * The quota is a second rather than Adam's ten.  Nothing about what is asserted depends on the number -
 * it is the same code either way - and FEEDBACK_ADVISORY_MS is a public volatile field precisely so
 * that a test can shrink it.  His ten seconds is the right number for doing this by hand, where the
 * point is to watch a real railway do nothing for a while.
 */
public class testStuckTrainAdvisory
{
    private static MarklinControlStation model;

    private static final List<String> logged = new CopyOnWriteArrayList<>();

    private static Handler listener;

    private static long quotaWas;

    /** Long enough to be sure the wait has had its chance, short enough not to pad the battery */
    private static final long QUOTA_MS = 1000;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Simulate and debug, as Adam's procedure says - this is the application's simulation, which
        // only stops it talking to a Central Station that is not there
        model = init(null, true, false, false, true);

        model.stop();

        quotaWas = Locomotive.FEEDBACK_ADVISORY_MS;

        Locomotive.FEEDBACK_ADVISORY_MS = QUOTA_MS;

        // Everything the model logs, captured where it goes: log(String) writes to this logger, and
        // to the view when there is one.  There is no view here.
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

    @AfterClass
    public static void tearDownClass()
    {
        Locomotive.FEEDBACK_ADVISORY_MS = quotaWas;

        if (listener != null)
        {
            Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(listener);
        }
    }

    /**
     * A train dispatched over two sensors, stopped after the first, is named in the log.
     *
     * The whole path, not the wait on its own: the advisory is asked for by the dispatch loop, and the
     * defect this file exists for was twice about which CALLER asks.  So this drives a real Layout with
     * a real path and stops the train where a real one stops - between sensors, with the first one made
     * and the second one never coming.
     */
    @Test
    public void testATrainThatStopsBetweenSensorsIsNamed() throws Exception
    {
        logged.clear();

        Layout layout = threeStations("MT37");

        // Autonomy simulation OFF, which is the point of the whole procedure: with it on, the sensors
        // are triggered for us a moment after each edge and no train is ever stuck
        layout.setSimulate(false);

        MarklinLocomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.moveLocomotive(loc.getName(), "MT37_A", false);

        List<Edge> path = layout.bfs(layout.getPoint("MT37_A"), layout.getPoint("MT37_C"), null);

        assertNotNull(path, "the fixture has no route from A to C");

        assertEquals(path.size(), 2, "the train must cross TWO sensors, or nothing waits for a second");

        Thread dispatch = new Thread(() -> layout.executePath(path, loc, 30, null));

        dispatch.setDaemon(true);
        dispatch.start();

        // Under way FIRST.  The dispatch checks the whole path is clear before it takes it, so making
        // a sensor before that check refuses the path for being occupied - which is the same railway
        // saying something completely different, and is what this test did on its first run.
        assertTrue(startedMoving(loc), "the train never started: " + logged);

        // The first sensor makes, by hand - the train has reached the middle station
        occupy(layout, "MT37_B");

        // And the second never does.  Past the quota, plus enough for the dispatch loop to have got
        // there and settled.
        Thread.sleep(QUOTA_MS * 4);

        String said = find("has not reached");

        assertNotNull(said, "nothing was said about a train that was dispatched and has stopped.  "
            + "The log held: " + logged);

        assertTrue(said.contains(loc.getName()),
            "the advisory does not name the train, so the operator does not know which one to go and "
            + "look at: " + said);

        dispatch.interrupt();
    }

    /**
     * And a route's trigger monitor, waiting on its sensor for ever, says nothing at all.
     *
     * The regression test.  MarklinRoute watches its trigger sensor through this same wait, on a
     * locomotive it invents called "Dummy Loc" - so an advisory built into the wait rather than asked
     * for by the caller announced a stuck train, once per enabled route, for ever.
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

        assertNull(find("has not reached"),
            "a route waiting on its trigger sensor announced a stuck train.  That is what every "
            + "enabled route on the layout would do, every quota, for ever: " + logged);

        assertNull(find("Dummy Loc"),
            "the locomotive routes borrow to watch their sensors has been mentioned to the user at "
            + "all, which is a name from the inside of the program: " + logged);

        watching.interrupt();
    }

    /**
     * The advisory is said once, not every time round the loop.
     *
     * A message repeated every wake-up is a message nobody reads, and this wait wakes as often as the
     * quota until it has spoken.
     */
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

    // ---------------------------------------------------------------------------------------------

    /**
     * Three stations in a line, so a train sent from one end crosses two sensors to reach the other.
     */
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
     * Waits until the dispatch has actually set the train going.
     *
     * @return whether it did, within a few seconds
     */
    private static boolean startedMoving(MarklinLocomotive loc) throws Exception
    {
        for (int waited = 0; waited < 60; waited++)
        {
            if (loc.getSpeed() > 0) return true;

            Thread.sleep(50);
        }

        return false;
    }

    /**
     * Makes a station's sensor by hand, which is how Adam tests this: clicking the s88 on the track
     * diagram rather than running a train over it.
     */
    private static void occupy(Layout layout, String point)
    {
        String sensor = layout.getPoint(point).getS88();

        assertNotNull(sensor, "no sensor on " + point);

        model.setFeedbackState(sensor, true);
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
