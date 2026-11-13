package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
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
 * A train that was sent somewhere and has not arrived says so.  A route waiting on its sensor does not (MT-037).
 *
 *  - It must fire: a train sent onto a path it never completes otherwise waits silently and for ever.
 *  - It must not fire for a route: a route's trigger monitor sits on its sensor for as long as the layout runs, through
 *    the same wait, on a locomotive called "Dummy Loc".
 *
 * So the quota is passed in by the CALLER rather than read inside the wait: "this train was dispatched and is on its
 * way" is a fact the dispatch loop has and the wait does not.  The quota is a second rather than Adam's ten, which only
 * matters for watching a real railway by hand; FEEDBACK_ADVISORY_MS is a public volatile field so a test can shrink
 * it.
 */
public class testStuckTrainAdvisory
{
    private static MarklinControlStation model;

    private static final List<String> logged = new CopyOnWriteArrayList<>();

    private static Handler listener;

    private static long quotaWas;

    /** Long enough to be sure the wait has had its chance, short enough not to pad the battery. */
    private static final long QUOTA_MS = 1000;

    /** The trigger waits' quota: many multiples of it still make a fast test. */
    private static final long TRIGGER_QUOTA_MS = 120;

    /** More than one trigger wait, because the leak this guards against fires once PER ROUTE. */
    private static final int ROUTES = 5;

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
        listener = support.LogTap.messagesInto(logged);

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
     * A sensor that makes and lets go does not restart the clock: the advisory is measured from when the train was
     * SENT.  This watches the number the hook is handed, since the sentence rounds to whole minutes and every number
     * here rounds to zero, against the time it was handed.
     *
     * MUTATION: in `Locomotive.waitForOccupiedFeedback`, move `final long began = System.currentTimeMillis();` into the
     * loop.
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

        assertTrue(watcher.reported >= watcher.firedAt - began - 200, "the advisory reported " + watcher.reported
            + "ms when said " + (watcher.firedAt - began) + "ms after the wait began: the clock restarted at the flicker");

        waiting.interrupt();
    }

    /**
     * An advisory said is not said again when the sensor flickers after it: the restarted wait is the same journey.
     *
     * MUTATION: in `Locomotive.waitForOccupiedFeedback`, move `boolean advised = adviseAfterMs <= 0;` into the loop.
     */
    @Test
    public void testAFlickerAfterTheAdvisoryDoesNotSayItAgain() throws Exception
    {
        Watcher watcher = new Watcher(model);
        MarklinFeedback sensor = model.newFeedback(80, null);
        String name = sensor.getName();
        model.setFeedbackState(name, false);

        Thread waiting = new Thread(() -> watcher.waitForOccupiedFeedback(name, 300, 200));
        waiting.setDaemon(true);
        waiting.start();

        assertTrue(support.Components.waitFor(() -> watcher.said.get() == 1, 5000), "precondition: never said");
        model.setFeedbackState(name, true);
        assertTrue(support.Components.waitFor(() -> watcher.debounces.get() == 1, 5000),
            "precondition: the wait did not see the sensor made");
        model.setFeedbackState(name, false);

        // Waiting is the point: a restart that forgot the advisory says it again at once, being past the quota
        support.Components.waitFor(() -> watcher.said.get() > 1, 1000);
        model.setFeedbackState(name, true);
        waiting.join(5000);

        assertFalse(waiting.isAlive(), "the wait did not end on the held sensor");
        assertEquals(watcher.debounces.get(), 2, "the flicker did not restart the wait");
        assertEquals(watcher.said.get(), 1, "the advisory was said again after a flicker");
    }

    /** Five waits, all sitting on sensors that never fire, for many times the advisory quota. */
    @Test
    public void testWaitingOnATriggerSensorIsSilent() throws Exception
    {
        // Its own quota, short, so many multiples of it still make a fast test
        Locomotive.FEEDBACK_ADVISORY_MS = TRIGGER_QUOTA_MS;

        try
        {
            waitOnTriggerSensors();
        }
        finally
        {
            Locomotive.FEEDBACK_ADVISORY_MS = QUOTA_MS;
        }
    }

    private static void waitOnTriggerSensors() throws Exception
    {
        final String[] sensors = new String[ROUTES];
        final Thread[] monitors = new Thread[ROUTES];

        for (int i = 0; i < ROUTES; i++)
        {
            // Addresses of their own, well clear of anything the sample layout uses; made, so the release below lands
            sensors[i] = model.newFeedback(920 + i, null).getName();

            model.setFeedbackState(sensors[i], false);
        }

        logged.clear();

        for (int i = 0; i < ROUTES; i++)
        {
            final String sensor = sensors[i];

            // The door a route's trigger monitor comes in by: two arguments, no advisory.  A REAL locomotive rather
            // than one called "Dummy Loc": the advisory names whichever locomotive is waiting, so looking for "dummy"
            // looked for the one string this test cannot produce.  What matters is that this door says nothing about
            // ANY locomotive.
            final Locomotive dummy = model.getLocByName(model.getLocList().get(0));

            monitors[i] = new Thread(() -> dummy.waitForOccupiedFeedback(sensor, 0));

            monitors[i].setDaemon(true);
            monitors[i].start();
        }

        // Long enough that an advisory quota would have come round several times over.
        Thread.sleep(TRIGGER_QUOTA_MS * 8);

        StringBuilder said = new StringBuilder();

        // The advisory's own words and the sensors these waits sit on, so a differently worded notice about them is
        // caught too.
        for (String line : logged)
        {
            boolean aboutTheWait = line.contains("has not reached");

            for (String sensor : sensors)
            {
                if (line.contains(sensor)) aboutTheWait = true;
            }

            if (aboutTheWait) said.append("\n  ").append(line);
        }

        assertEquals(said.toString(), "",
            "a route waiting on its trigger sensor talked about it. A route monitor sits on its sensor "
            + "for as long as the layout is switched on, and it does so through a locomotive called "
            + "\"Dummy Loc\" - so an advisory here is said once per route, for ever, about a train that "
            + "does not exist (MT-037):" + said);

        // Let them go, so nothing is left sitting on a sensor
        for (int i = 0; i < ROUTES; i++)
        {
            model.setFeedbackState(sensors[i], true);
        }

        for (Thread monitor : monitors)
        {
            monitor.join(2000);
        }
    }

    /**
     * And the advisory door has exactly one caller.  The split is easy to undo by accident: the natural way to
     * "improve" a wait is to add the advisory to the shared one, which every route monitor comes in by.  Only the
     * dispatch loop, which knows a train was actually sent somewhere, may say anything.  `Locomotive` itself is not
     * counted: it declares both doors and delegates from one to the other.
     */
    @Test
    public void testOnlyTheDispatchPathAsksForAnAdvisory() throws Exception
    {
        File src = new File("src");

        assertTrue(src.isDirectory(),
            "cannot find " + src.getAbsolutePath() + " - a test that reads the source cannot pass by not finding it. This returned quietly, so renaming or moving that file would have taken this rule with it and said nothing");

        java.util.Set<String> callers = new java.util.TreeSet<>();

        walk(src, callers);

        assertEquals(callers, new java.util.TreeSet<>(java.util.Arrays.asList("Layout.java")),
            "the advising waitForOccupiedFeedback - the one with a third argument - is called from "
            + callers + ". Only the dispatch loop in Layout may ask for it, because that is the only "
            + "caller that knows a train was actually sent somewhere. Every other wait, route triggers "
            + "included, comes in by the silent two-argument door - and that door is shared with every "
            + "route monitor on the layout (MT-037)");
    }

    /**
     * Which files call the advising wait; reads whole files, because both of Layout's calls put their arguments on the
     * following line.
     */
    private void walk(File where, java.util.Set<String> into) throws Exception
    {
        File[] children = where.listFiles();

        if (children == null) return;

        for (File child : children)
        {
            if (child.isDirectory())
            {
                walk(child, into);
                continue;
            }

            if (!child.getName().endsWith(".java")) continue;

            // Locomotive declares both doors and delegates from one to the other, so its calls are the implementation.
            if (child.getName().equals("Locomotive.java")) continue;

            String source = new String(Files.readAllBytes(child.toPath()), StandardCharsets.UTF_8);

            int at = source.indexOf("waitForOccupiedFeedback(");

            while (at >= 0)
            {
                if (arity(source, at + "waitForOccupiedFeedback(".length() - 1) >= 3)
                {
                    into.add(child.getName());
                }

                at = source.indexOf("waitForOccupiedFeedback(", at + 1);
            }
        }
    }

    /** How many arguments the call whose opening bracket is at {@code open} was given. */
    private int arity(String source, int open)
    {
        int depth = 0;
        int commas = 0;
        boolean anything = false;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '(') depth++;
            else if (c == ')')
            {
                depth--;

                if (depth == 0) break;
            }
            else if (c == ',' && depth == 1) commas++;
            else if (!Character.isWhitespace(c) && depth >= 1) anything = true;
        }

        return anything ? commas + 1 : 0;
    }

    /** A locomotive that writes down what the advisory hook was handed (`waitedTooLongFor` is protected, for this). */
    private static final class Watcher extends MarklinLocomotive
    {
        volatile long reported = -1;

        // When the advisory was said, how often, and how many debounces the wait began
        volatile long firedAt = -1;
        final java.util.concurrent.atomic.AtomicInteger said = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger debounces = new java.util.concurrent.atomic.AtomicInteger();

        Watcher(MarklinControlStation model)
        {
            super(model, 2, MarklinLocomotive.decoderType.MM2, "Watcher");
        }

        @Override
        protected void waitedTooLongFor(String feedbackName, long waitedMs)
        {
            reported = waitedMs;
            firedAt = System.currentTimeMillis();
            said.incrementAndGet();
        }

        @Override
        public Locomotive delay(long ms)
        {
            debounces.incrementAndGet();
            return super.delay(ms);
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
