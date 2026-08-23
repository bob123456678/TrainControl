package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A route sitting on its trigger sensor says nothing about it, however long it sits.
 *
 * MT-037, Adam: "Leave an automatic route enabled and watch the log for ten minutes. It must say
 * NOTHING about its trigger sensor... it does so on a locomotive called 'Dummy Loc' - if that name ever
 * appears in the log, the advisory has leaked out of the dispatch path into the shared wait." And:
 * "Add a test for this. Many simultaneous auto routes, trigger them synthetically, short duration."
 *
 * Ten minutes of watching is not a test, so this does what he asked instead: shrink the advisory quota
 * to a fraction of a second, put several waits on it at once, and let them sit for many times the
 * quota.
 *
 * **The trap is that the wait is SHARED.** Route triggers borrow `Locomotive`'s feedback utilities -
 * `MarklinRoute` literally builds a locomotive called "Dummy Loc" to get at them - so an advisory added
 * for the benefit of a dispatched train fires for every route monitor on the layout as well, once each,
 * for as long as the railway is switched on. The split that prevents it is that the two-argument
 * `waitForOccupiedFeedback` passes no advisory and the three-argument one does, and only the dispatch
 * loop calls the three-argument door.
 *
 * @author Adam
 */
public class testTriggerWaitsSayNothing
{
    private static MarklinControlStation model;
    private static Handler listener;
    private static long quotaWas;

    private static final List<String> logged = new CopyOnWriteArrayList<>();

    /** Short enough that many multiples of it still make a fast test */
    private static final long QUOTA_MS = 120;

    /** More than one, because the leak this guards against fires once PER ROUTE */
    private static final int ROUTES = 5;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        quotaWas = Locomotive.FEEDBACK_ADVISORY_MS;
        Locomotive.FEEDBACK_ADVISORY_MS = QUOTA_MS;

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

        if (model != null) model.stop();
    }

    /**
     * Five waits, all sitting on sensors that never fire, for many times the advisory quota.
     */
    @Test
    public void testWaitingOnATriggerSensorIsSilent() throws Exception
    {
        final String[] sensors = new String[ROUTES];
        final Thread[] monitors = new Thread[ROUTES];

        for (int i = 0; i < ROUTES; i++)
        {
            // Addresses of their own, well clear of anything the sample layout uses
            sensors[i] = Integer.toString(920 + i);

            model.setFeedbackState(sensors[i], false);
        }

        logged.clear();

        for (int i = 0; i < ROUTES; i++)
        {
            final String sensor = sensors[i];

            // The door a route's trigger monitor comes in by: two arguments, no advisory.
            //
            // A REAL locomotive rather than one called "Dummy Loc": the advisory names whichever
            // locomotive is waiting, so looking for the word "dummy" would have looked for the one
            // string this test cannot produce - which is exactly the mistake the first version of it
            // made, and it passed while the shared door was advising. What matters is that this door
            // says nothing about ANY locomotive; a route monitor is simply the case where the
            // locomotive is a fiction.
            final Locomotive dummy = model.getLocByName(model.getLocList().get(0));

            monitors[i] = new Thread(() -> dummy.waitForOccupiedFeedback(sensor, 0));

            monitors[i].setDaemon(true);
            monitors[i].start();
        }

        // Long enough that an advisory quota would have come round several times over
        Thread.sleep(QUOTA_MS * 8);

        StringBuilder said = new StringBuilder();

        // The advisory's own words - "{0} has not reached {1} after {2} minutes" - and the
        // sensors these waits sit on, so a differently worded notice about them is caught too.
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
     * And the advisory door has exactly one caller.
     *
     * The behaviour above can only be kept by keeping the split, and the split is easy to undo by
     * accident: the natural way to "improve" a wait is to add the advisory to the shared one, which is
     * the door every route monitor on the layout comes in by. Only the dispatch loop - which knows a
     * train was actually sent somewhere - may say anything.
     *
     * `Locomotive` itself is not counted: it declares both doors and delegates from one to the other,
     * so its own file is full of calls that are the implementation rather than users of it.
     */
    @Test
    public void testOnlyTheDispatchPathAsksForAnAdvisory() throws Exception
    {
        File src = new File("src");

        if (!src.isDirectory()) return;

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
     * Which files call the advising wait.
     *
     * Reads whole files rather than lines: both of Layout's calls put their arguments on the following
     * line, so a line-by-line scan sees the name and none of the arguments and concludes there are no
     * callers at all - which is a green test for a rule it never checked.
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

            // Locomotive declares both doors and delegates from one to the other, so its own file is
            // full of calls that are the implementation rather than users of it.
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

    /**
     * How many arguments the call whose opening bracket is at {@code open} was given.
     */
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
}
