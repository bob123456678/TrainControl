package core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for the timetable subsystem - capture, the unfinished-entry sentinel, and reset.
 *
 * Two of these pin fixes for defects that were invisible in ordinary use:
 *
 *  - Capture stored the gap between a pair of entries on the EARLIER of them, while the replay loop
 *    and the edit dialog both read that field as the delay BEFORE the entry holding it.  A captured
 *    timetable therefore replayed with every gap shifted one entry back, the first captured gap was
 *    never applied, and the last entry always started immediately.  Only auto-capture used the
 *    opposite convention, which is why a hand-edited timetable always behaved correctly and the
 *    problem went unnoticed.
 *
 *  - getUnfinishedTimetablePathIndex returned 0 both for "entry 0 is unfinished" and for "nothing is
 *    unfinished".  Entries are dispatched on their own threads and can finish out of order, so a
 *    graceful stop can leave [unfinished, finished] - and the caller read that 0 as "nothing
 *    unfinished" and wiped every completion timestamp, including the one that had genuinely finished.
 *
 * executeTimetable itself is not covered: it spawns a thread per entry and drives real path
 * execution, which needs hardware or a far larger harness.  What is covered is the state it reads
 * and writes.
 */
public class testLayoutTimetable
{
    private static MarklinControlStation model;

    private static int locCounter = 0;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();
    }

    /**
     * Not registered in the locomotive database - a timetable entry only holds the reference.
     * Names are unique because Locomotive equality includes the name.
     */
    private MarklinLocomotive dummyLoc()
    {
        MarklinLocomotive loc = new MarklinLocomotive(model, 1, MarklinLocomotive.decoderType.MM2,
            "TT Loc " + (++locCounter));

        // WITH A SPEED, which was never what these tests are about (SG-A5).
        //
        // A fresh locomotive has none, and the dispatch loop now SKIPS an entry whose locomotive has
        // a speed outside 1 to 100 rather than retrying it - so the two give-up tests below ended
        // instantly, by the new rule, without ever reaching the bounds they were written to check.
        // The reason those entries can never execute is that the layout is invalidated, and that is
        // the mechanism they must keep exercising.
        loc.setPreferredSpeed(35);

        return loc;
    }

    /**
     * A layout with one usable edge.  A timetable entry needs only a non-empty path - the points do
     * not have to be destinations, since nothing here routes anything.
     */
    private Layout layoutWithOnePath() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("TT_A", false, null);
        layout.createPoint("TT_B", false, null);
        layout.createEdge("TT_A", "TT_B");

        return layout;
    }

    private List<Edge> path(Layout layout)
    {
        return Arrays.asList(layout.getEdge("TT_A", "TT_B"));
    }

    /**
     * addTimetableEntry is private - capture is normally driven from executePath.  Reflection rather
     * than widening the production API for a test.
     */
    private static boolean capture(Layout layout, Locomotive loc, List<Edge> path, long timestamp)
        throws Exception
    {
        Method method = Layout.class.getDeclaredMethod(
            "addTimetableEntry", Locomotive.class, List.class, long.class);

        method.setAccessible(true);

        return (boolean) method.invoke(layout, loc, path, timestamp);
    }

    /**
     * Replaces the timetable with entries having the given execution times.  Zero means unfinished.
     */
    private void setEntries(Layout layout, long... executionTimes)
    {
        List<TimetablePath> entries = new ArrayList<>();

        for (long executionTime : executionTimes)
        {
            entries.add(new TimetablePath(dummyLoc(), path(layout), executionTime));
        }

        layout.setTimetable(entries);
    }

    /**
     * Each captured gap belongs to the entry it precedes, which is how replay and the edit dialog
     * both read it.
     */
    @Test
    public void testCaptureStoresEachGapOnTheLaterEntry() throws Exception
    {
        Layout layout = layoutWithOnePath();
        layout.setTimetableCapture(true);

        Locomotive loc = dummyLoc();
        List<Edge> path = path(layout);

        long start = 1000000L;

        assertTrue(capture(layout, loc, path, start));
        assertTrue(capture(layout, loc, path, start + 10000));
        assertTrue(capture(layout, loc, path, start + 45000));
        assertTrue(capture(layout, loc, path, start + 50000));

        List<TimetablePath> timetable = layout.getTimetable();

        assertEquals(timetable.size(), 4);

        // Gaps were 10s, 35s, 5s
        assertEquals(timetable.get(0).getSecondsToNext(), 0L,
            "the first entry has nothing before it, so it carries no gap");
        assertEquals(timetable.get(1).getSecondsToNext(), 10000L, "gap between entry 0 and entry 1");
        assertEquals(timetable.get(2).getSecondsToNext(), 35000L, "gap between entry 1 and entry 2");
        assertEquals(timetable.get(3).getSecondsToNext(), 5000L,
            "the last entry keeps its real gap - storing gaps on the earlier entry left this at zero, "
            + "so the final entry always started immediately");
    }

    /**
     * A single captured entry has no gap to record.
     */
    @Test
    public void testFirstCapturedEntryHasNoGap() throws Exception
    {
        Layout layout = layoutWithOnePath();
        layout.setTimetableCapture(true);

        assertTrue(capture(layout, dummyLoc(), path(layout), 5000L));

        assertEquals(layout.getTimetable().size(), 1);
        assertEquals(layout.getTimetable().get(0).getSecondsToNext(), 0L);
    }

    /**
     * Nothing is recorded unless capture is switched on.
     */
    @Test
    public void testNothingIsCapturedWhenCaptureIsOff() throws Exception
    {
        Layout layout = layoutWithOnePath();

        assertFalse(capture(layout, dummyLoc(), path(layout), 1000L),
            "capture is off by default, so the entry must be refused");

        assertTrue(layout.getTimetable().isEmpty());
    }

    /**
     * An empty path is refused even while capturing.
     */
    @Test
    public void testEmptyPathIsNotCaptured() throws Exception
    {
        Layout layout = layoutWithOnePath();
        layout.setTimetableCapture(true);

        assertFalse(capture(layout, dummyLoc(), new ArrayList<Edge>(), 1000L));

        assertTrue(layout.getTimetable().isEmpty());
    }

    /**
     * -1, not 0, when every entry has finished.
     */
    @Test
    public void testUnfinishedIndexIsMinusOneWhenEverythingHasFinished() throws Exception
    {
        Layout layout = layoutWithOnePath();

        setEntries(layout, 100L, 200L, 300L);

        assertEquals(layout.getUnfinishedTimetablePathIndex(), -1,
            "every entry has an execution time, so there is no unfinished entry to name");
    }

    /**
     * The index names the first entry that has not finished.
     */
    @Test
    public void testUnfinishedIndexNamesTheFirstUnfinishedEntry() throws Exception
    {
        Layout layout = layoutWithOnePath();

        setEntries(layout, 100L, 200L, 0L, 0L);

        assertEquals(layout.getUnfinishedTimetablePathIndex(), 2);
    }

    /**
     * The case the old sentinel could not express.
     *
     * Entries are dispatched on their own threads, so a later one can finish while an earlier one is
     * still retrying; a graceful stop then leaves exactly this state.  0 has to mean "entry 0 is
     * unfinished" here - reading it as "nothing is unfinished" is what wiped the completed entry's
     * timestamp on resume.
     */
    @Test
    public void testUnfinishedIndexIsZeroWhenOnlyTheFirstEntryIsUnfinished() throws Exception
    {
        Layout layout = layoutWithOnePath();

        setEntries(layout, 0L, 200L);

        assertEquals(layout.getUnfinishedTimetablePathIndex(), 0,
            "entry 0 is unfinished, which must not be confused with there being nothing unfinished");

        assertFalse(layout.getTimetable().get(0).isExecuted());
        assertTrue(layout.getTimetable().get(1).isExecuted(),
            "entry 1 finished out of order and must keep saying so");
    }

    /**
     * An empty timetable has nothing unfinished.  The caller clamps this to 0 before using it as a
     * loop start.
     */
    @Test
    public void testEmptyTimetableHasNothingUnfinished() throws Exception
    {
        Layout layout = layoutWithOnePath();

        assertEquals(layout.getUnfinishedTimetablePathIndex(), -1);
        assertEquals(Math.max(0, layout.getUnfinishedTimetablePathIndex()), 0,
            "clamped, this is a safe loop start for an empty timetable");
    }

    /**
     * executePath refuses permanently once the layout is invalidated - its very first check.  This is
     * the premise T3 rests on, and it holds however T3 is eventually resolved.
     */
    @Test
    public void testExecutePathRefusesPermanentlyWhenTheLayoutIsInvalid() throws Exception
    {
        Layout layout = layoutWithOnePath();

        assertTrue(layout.isValid(), "precondition: a fresh layout is valid");

        layout.invalidate();

        assertFalse(layout.isValid());
        assertFalse(layout.executePath(path(layout), dummyLoc(), 50, null),
            "an invalidated layout can never execute a path, no matter how often it is retried");
    }

    /**
     * An entry that can never execute ends the run, rather than being retried for ever.
     *
     * This was a characterisation test pinning the opposite - T3, a known defect - with a note saying
     * it must be inverted when the retry loop learned to give up. It has been.
     *
     * The loop is `while (running && !executePath(...))`, and both the attempt cap and the pause that
     * bounded it were gated on `timetableSequential` - true only for a return-home plan. So a captured
     * timetable, which is the parallel kind, retried a refusal that could never change until a human
     * noticed. Here the refusal is an invalidated layout, which executePath rejects on its first line.
     *
     * The bound is a TIME rather than a count of attempts, because the pause between attempts is the
     * user's own delay setting, anything from a quarter second to tens of them - a count would bound a
     * different amount of standing still on one layout than on another.
     */
    @Test(timeOut = 60000)
    public void testAPermanentlyUnexecutableEntryEndsTheRun() throws Exception
    {
        Layout layout = layoutWithOnePath();

        // Max before min: setMinDelay rejects a value above the current maximum
        layout.setMaxDelay(1);
        layout.setMinDelay(1);

        setEntries(layout, 0L);

        layout.invalidate();

        long was = Layout.TIMETABLE_STUCK_MS;

        // Short enough to finish inside the timeout above.  The shipped value is minutes, because a
        // train in a parallel timetable may legitimately wait a long time for another to clear its way.
        Layout.TIMETABLE_STUCK_MS = 4000;

        try
        {
            Thread runner = new Thread(layout::executeTimetable);

            // Daemon, so that a failure here can never hold the JVM open
            runner.setDaemon(true);
            runner.start();

            // Precondition: the run must actually have STARTED before the wait loop below is asked to
            // see it end.  executeTimetableInternal sets `running = true` only after the worker is
            // scheduled, so the main thread's very first read can otherwise see `false` before the
            // retry loop ever ran - the wait loop below exits immediately, and both closing assertions
            // pass without the give-up bound having been exercised at all.  testAGracefulStopEndsTheRetryLoop
            // guards this the same way.
            long startDeadline = System.currentTimeMillis() + 3000;

            while (!layout.isAutoRunning() && System.currentTimeMillis() < startDeadline)
            {
                Thread.sleep(50);
            }

            assertTrue(layout.isAutoRunning(), "precondition: the run should have started by now");

            long deadline = System.currentTimeMillis() + 40000;

            while (layout.isAutoRunning() && System.currentTimeMillis() < deadline)
            {
                Thread.sleep(100);
            }

            assertFalse(layout.isAutoRunning(),
                "the entry can never execute, so the run must end on its own - nobody asked it to stop");

            assertFalse(layout.getTimetable().get(0).isExecuted(),
                "and it has of course never executed");
        }
        finally
        {
            Layout.TIMETABLE_STUCK_MS = was;
            layout.stopLocomotives();
        }
    }

    /**
     * A graceful stop still ends the retry loop, whatever else it does.
     *
     * Kept as its own test after T3 was fixed. An unstoppable loop would be far worse than an unbounded
     * one, and the fix touches exactly the code that ends it.
     */
    @Test(timeOut = 60000)
    public void testAGracefulStopEndsTheRetryLoop() throws Exception
    {
        Layout layout = layoutWithOnePath();

        layout.setMaxDelay(1);
        layout.setMinDelay(1);

        setEntries(layout, 0L);

        layout.invalidate();

        long was = Layout.TIMETABLE_STUCK_MS;

        // Long enough that the give-up cannot be what ends this run - the stop below must be
        Layout.TIMETABLE_STUCK_MS = 600000;

        try
        {
            Thread runner = new Thread(layout::executeTimetable);
            runner.setDaemon(true);
            runner.start();

            Thread.sleep(3000);

            assertTrue(layout.isAutoRunning(), "precondition: the run should still be going");

            layout.stopLocomotives();

            long deadline = System.currentTimeMillis() + 20000;

            while (layout.isAutoRunning() && System.currentTimeMillis() < deadline)
            {
                Thread.sleep(100);
            }

            assertFalse(layout.isAutoRunning(), "a graceful stop must end the retry loop");
        }
        finally
        {
            Layout.TIMETABLE_STUCK_MS = was;
            layout.stopLocomotives();
        }
    }

    /**
     * Reset marks every entry unfinished again, and the sentinel then points at the first.
     */
    @Test
    public void testResetMarksEveryEntryUnfinished() throws Exception
    {
        Layout layout = layoutWithOnePath();

        setEntries(layout, 100L, 200L, 300L);

        assertEquals(layout.getUnfinishedTimetablePathIndex(), -1);

        layout.resetTimetable();

        for (TimetablePath entry : layout.getTimetable())
        {
            assertEquals(entry.getExecutionTime(), 0L);
            assertFalse(entry.isExecuted());
        }

        assertEquals(layout.getUnfinishedTimetablePathIndex(), 0,
            "after a reset the first entry is the one to run next");
    }

    /**
     * A timetable entry through a point the railway does not have - one on a page left out of autonomy - is kept as it
     * was written, not dropped at the load (Adam, 2026-09-30: "Just keep the entries, and if a path is run that contains
     * a point on a disabled page, reject it with an error").  Dropped, the next fold wrote the shorter timetable over the
     * configuration and the entry was gone for good.
     *
     * MUTATION: drop an entry the railway cannot build at the load, and this fails.
     *
     * @throws Exception from the layout
     */
    @Test
    public void testAnEntryThroughAPointTheRailwayLacksIsKeptAsWritten() throws Exception
    {
        Layout layout = layoutWithOnePath();

        String loc = model.getLocList().get(0);

        org.json.JSONObject written = anEntry(loc, "TT_A", "TT_Z");

        TimetablePath entry = TimetablePath.fromJSON(written.toString(), model, layout);

        assertFalse(entry.isRunnable(), "an entry through a point the railway does not have reads as runnable");

        assertEquals(entry.getStartName(), "TT_A", "the entry does not say where it starts");
        assertEquals(entry.getEndName(), "TT_Z", "the entry does not say where it ends");

        org.json.JSONObject back = entry.toJSON();

        assertEquals(back.getJSONArray("path").toString(), written.getJSONArray("path").toString(), "the entry was not"
            + " written back as it was read");

        assertEquals(back.getString("loc"), loc, "the entry was not written back for its locomotive");
    }

    /**
     * The run stops at an entry the railway cannot run, and says why (Adam, 2026-09-30: "stop the timetable run upon
     * encountering an invalid path, and let the user know").  Refused and passed over, a train's later entries started
     * where it was not, and the run stood for minutes before giving up, saying only that the track never became free
     * (RSA9-B1).  The entry is left unrun, so the window names it, and nothing after it runs.
     *
     * MUTATION: pass over an entry the railway cannot run, and this fails.
     *
     * @throws Exception from the layout
     */
    @Test(timeOut = 60000)
    public void testAnEntryTheRailwayCannotRunStopsTheRunAndSaysWhy() throws Exception
    {
        final Layout layout = layoutWithOnePath();

        // Max before min: setMinDelay rejects a value above the current maximum
        layout.setMaxDelay(1);
        layout.setMinDelay(1);

        String loc = model.getLocList().get(0);

        TimetablePath first = TimetablePath.fromJSON(anEntry(loc, "TT_A", "TT_Z").toString(), model, layout);
        TimetablePath second = TimetablePath.fromJSON(anEntry(loc, "TT_Y", "TT_B").toString(), model, layout);

        layout.setTimetable(new ArrayList<>(Arrays.asList(first, second)));

        final List<String> logged = java.util.Collections.synchronizedList(new ArrayList<String>());

        java.util.logging.Handler tap = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                if (record != null && record.getMessage() != null) logged.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);

        try
        {
            final boolean[] completed = {true};

            Thread runner = new Thread(() -> completed[0] = layout.executeTimetable());

            runner.setDaemon(true);
            runner.start();
            runner.join(30000);

            assertFalse(runner.isAlive(), "a timetable stopped at an entry the railway cannot run did not end");

            assertFalse(layout.isAutoRunning(), "the run still reads as running");

            assertFalse(completed[0], "a run stopped at an entry it cannot run reads as finished");

            assertFalse(second.isExecuted(), "the run went on past an entry it cannot run (RSA9-B1)");

            assertFalse(first.isExecuted(), "the entry the run stopped at reads as run, so the window cannot name it");

            assertEquals(layout.getUnfinishedTimetablePathIndex(), 0, "the run does not say where it stopped");

            assertEquals(layout.whyTheTimetableStopped(), first.whyNotRunnable(), "the run does not say why it stopped");

            String said = org.traincontrol.util.I18n.f("autolayout.errorTimetableEntryNotRun", first.toString(),
                first.whyNotRunnable());

            assertTrue(logged.contains(said), "the entry was not refused with its reason: " + logged);
        }
        finally
        {
            java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);

            layout.stopLocomotives();
        }
    }

    /**
     * A route that ends where it starts - a lap - is refused: kept unrunnable when a timetable is read, so the run stops
     * there and says why, and refused by the run itself whatever door brings it (RSA30-C2).
     *
     * No door of the application offers one; a timetable read from a file can carry one.  Run, it left the train on no
     * Point - the arrival took it off the station it had just reached - with that station's exit guard green under it,
     * and in non-atomic mode freed the station while the train was still on its way there.
     *
     * MUTATION: take either refusal out, and this fails.
     *
     * @throws Exception from the layout
     */
    @Test(timeOut = 60000)
    public void testALapIsRefused() throws Exception
    {
        // TWO STATIONS, so a train can be stood at the start
        for (int number : new int[] {47521, 47522})
        {
            if (!model.isFeedbackSet(String.valueOf(number))) model.newFeedback(number, null);

            model.setFeedbackState(String.valueOf(number), false);
        }

        final Layout layout = new Layout(model);

        layout.createPoint("LAP_A", true, "47521");
        layout.createPoint("LAP_B", true, "47522");
        layout.createEdge("LAP_A", "LAP_B");
        layout.createEdge("LAP_B", "LAP_A");

        String loc = model.getLocList().get(0);

        org.json.JSONObject written = anEntry(loc, "LAP_A", "LAP_B");

        written.getJSONArray("path").put(new org.json.JSONObject().put("start", "LAP_B").put("end", "LAP_A"));

        TimetablePath entry = TimetablePath.fromJSON(written.toString(), model, layout);

        String why = org.traincontrol.util.I18n.f("autolayout.errorPathEndsWhereItStarts", "LAP_A");

        assertFalse(entry.isRunnable(), "a timetable entry that ends where it starts reads as runnable (RSA30-C2)");

        assertEquals(entry.whyNotRunnable(), why, "the lap is refused without saying why");

        // AND THE RUN REFUSES ONE, whatever door brings it
        final List<String> logged = java.util.Collections.synchronizedList(new ArrayList<String>());

        java.util.logging.Handler tap = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                if (record != null && record.getMessage() != null) logged.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);

        final Locomotive train = model.getLocByName(loc);

        try
        {
            assertTrue(layout.moveLocomotive(loc, "LAP_A", false), "precondition: could not stand the train at LAP_A");

            // ON A THREAD OF ITS OWN, with a limit: refused, it returns at once; run, it would wait on a sensor nobody plays
            final boolean[] ran = {true};

            Thread run = new Thread(() -> ran[0] = layout.executePath(Arrays.asList(layout.getEdge("LAP_A", "LAP_B"),
                layout.getEdge("LAP_B", "LAP_A")), train, 30, null));

            run.setDaemon(true);
            run.start();
            run.join(10000);

            boolean refused = !run.isAlive() && !ran[0];

            if (run.isAlive()) layout.stopLocomotives();

            assertTrue(refused, "a lap was run (RSA30-C2)");

            assertTrue(logged.contains(why), "the run refused the lap without saying why: " + logged);
        }
        finally
        {
            java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);

            layout.moveLocomotive(null, "LAP_A", false);
            layout.moveLocomotive(null, "LAP_B", false);
        }
    }

    /**
     * Where a locomotive's timetable starts is the start of its first entry the railway can run.
     *
     * MUTATION: take the first entry whatever it is, and this fails.
     *
     * @throws Exception from the layout
     */
    @Test
    public void testATimetableStartsAtTheFirstEntryTheRailwayCanRun() throws Exception
    {
        Layout layout = layoutWithOnePath();

        String name = model.getLocList().get(0);

        Locomotive loc = model.getLocByName(name);

        TimetablePath unrunnable = TimetablePath.fromJSON(anEntry(name, "TT_Y", "TT_Z").toString(), model, layout);
        TimetablePath runnable = TimetablePath.fromJSON(anEntry(name, "TT_A", "TT_B").toString(), model, layout);

        layout.setTimetable(new ArrayList<>(Arrays.asList(unrunnable, runnable)));

        assertEquals(layout.getTimetableStartingPoint(loc), layout.getPoint("TT_A"), "the timetable's start is read"
            + " from an entry the railway cannot run");
    }

    /** One timetable entry as a configuration stores it: this locomotive, one leg. */
    private static org.json.JSONObject anEntry(String loc, String start, String end)
    {
        return new org.json.JSONObject().put("loc", loc).put("executionTime", 0L).put("secondsToNext", 0L)
            .put("path", new org.json.JSONArray().put(new org.json.JSONObject().put("start", start).put("end", end)));
    }

    /**
     * A row deleted from the timetable is the entry the operator was shown, removed under the railway's lock - or nothing
     * (OB-260; Adam, 2026-10-06: "Fix OB-260").
     *
     * The delete asked, held a confirmation open as long as the operator liked, and then removed the row by number from
     * the plain list capture appends to under this railway's lock.  So it is asked of the entry, and taken under the lock.
     *
     * MUTATION: remove by the row number alone, or without the lock, and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testADeletedRowIsTheOneShownOrNone() throws Exception
    {
        Layout layout = layoutWithOnePath();

        setEntries(layout, 100, 200, 300);

        List<TimetablePath> before = new ArrayList<>(layout.getTimetable());

        Method remove = Layout.class.getDeclaredMethod("removeTimetableEntry", int.class, TimetablePath.class);

        assertTrue(java.lang.reflect.Modifier.isSynchronized(remove.getModifiers()), "the timetable's remove does not take"
            + " the railway's lock, which capture appends under from locomotive threads (OB-260)");

        // ANOTHER ENTRY AT THAT ROW NOW: nothing goes
        assertFalse((Boolean) remove.invoke(layout, 1, before.get(2)), "a row whose entry is no longer the one the"
            + " operator was shown was removed anyway (OB-260)");

        assertEquals(layout.getTimetable(), before, "a refused removal changed the timetable");

        // THE ONE SHOWN: it goes, and only it
        assertTrue((Boolean) remove.invoke(layout, 1, before.get(1)), "the row the operator confirmed was not removed");

        assertEquals(layout.getTimetable(), Arrays.asList(before.get(0), before.get(2)), "the wrong entry was removed");
    }
}
