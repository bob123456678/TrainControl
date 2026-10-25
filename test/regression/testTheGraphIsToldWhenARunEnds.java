package regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.automationui.AutonomyRefreshCallback;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.SourceOf;

/**
 * When a run ends, whoever draws the railway is told, from every door and not from two of the four (sibling of OB-189).
 *
 * A destination reversal is recorded in `Layout.reversedOnArrival` and written to the setup by
 * `TrainControlUI.reconcileFacingWhenIdle`, which refuses while anything is moving and is reached only from
 * `updateVisiblePoints()`, so a turn a run made is PENDING until a refresh happens with the railway idle.  The
 * timetable button and Return Home had no refresh at completion, and every announcement `Layout` makes during a
 * timetable fires while `isRunning()` is still true.  The remedy is one pair: a timetable announces once the railway
 * has gone idle, and the window's single refresh callback tells the graph.
 *
 * @author Adam
 */
public class testTheGraphIsToldWhenARunEnds
{
    private static MarklinControlStation model;

    private static int locCounter = 0;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model (OB-111): `init` reads the machine-global layout preference, which on the operator's machine
        // names his real railway.
        sandbox = support.LayoutSandbox.open();

        // DEBUG, because `Layout.setSimulate(true)` refuses outside debug mode and a hand dispatch could not reach its
        // destination without it; the flag adds logging and nothing else.
        model = support.TestStation.simulatedInDebug();

        // And the network is explicitly down, which is the other half of that condition.
        model.setNetworkCommState(false);

        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Not registered in the locomotive database: a test that took a real address would collide with the operator's
     * railway (OB-111).
     */
    private MarklinLocomotive dummyLoc()
    {
        MarklinLocomotive loc = new MarklinLocomotive(model, 1, MarklinLocomotive.decoderType.MM2,
            "RunEnd Loc " + (++locCounter));

        // The dispatch loop skips an entry whose locomotive has no usable speed, which would end the run for an
        // unrelated reason.
        loc.setPreferredSpeed(35);

        return loc;
    }

    /**
     * A timetable of one entry that can never execute, over a layout with one edge: the fixture of
     * `testLayoutTimetable.testAPermanentlyUnexecutableEntryEndsTheRun`, so the run gives up on its own after
     * `TIMETABLE_STUCK_MS` without hardware.
     */
    private Layout stuckTimetable() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RE_A", false, null);
        layout.createPoint("RE_B", false, null);
        layout.createEdge("RE_A", "RE_B");

        // Max before min: setMinDelay rejects a value above the current maximum
        layout.setMaxDelay(1);
        layout.setMinDelay(1);

        List<Edge> path = Arrays.asList(layout.getEdge("RE_A", "RE_B"));

        List<TimetablePath> entries = new ArrayList<>();

        entries.add(new TimetablePath(dummyLoc(), path, 0L));

        layout.setTimetable(entries);

        layout.invalidate();

        return layout;
    }

    /**
     * A timetable run, which is what Return Home is, announces once the railway has gone idle: every announcement a
     * timetable makes today is made while `isRunning()` is still true, so `reconcileFacingWhenIdle` refuses at each.
     *
     * MUTATION: remove `announceRunFinished()` from the end of `executeTimetableInternal` and this fails, reporting
     * that nothing was announced with the railway idle. Moving it above the completion wait fails it too.
     */
    @Test(timeOut = 120000)
    public void testATimetableRunAnnouncesOnceTheRailwayIsIdle() throws Exception
    {
        Layout layout = stuckTimetable();

        final AtomicInteger announcements = new AtomicInteger();
        final AtomicInteger whileIdle = new AtomicInteger();

        // SAMPLED WHERE THE ANNOUNCEMENT IS MADE, not where it is acted on: `AutonomyRefreshCallback` posts to the
        // event thread, so asking `isRunning()` inside it answers whenever the queue gets round to it, often after the
        // run ended.  A raw callback under its own name (`Layout` keys them) asks whether an announcement was ever MADE
        // with the railway idle.
        layout.setCallback("W7A2Probe",
            new Layout.TriFunction<List<Edge>, org.traincontrol.base.Locomotive, Boolean, Void>()
        {
            @Override
            public Void apply(List<Edge> edges, org.traincontrol.base.Locomotive loc, Boolean locked)
            {
                announcements.incrementAndGet();

                // WHAT reconcileFacingWhenIdle ASKS: it returns immediately while anything is moving, so an
                // announcement then changes nothing.
                if (!layout.isRunning()) whileIdle.incrementAndGet();

                return null;
            }
        });

        // And the window's own door is attached as well: the remedy has to travel through it to updateVisiblePoints().
        AutonomyRefreshCallback.attach(layout, () -> { });

        long was = Layout.TIMETABLE_STUCK_MS;

        // Short enough to finish inside the timeout above; the shipped value is minutes.
        Layout.TIMETABLE_STUCK_MS = 4000;

        try
        {
            Thread runner = new Thread(layout::executeTimetable);

            // Daemon, so that a failure here can never hold the JVM open
            runner.setDaemon(true);
            runner.start();

            // The run must actually have STARTED, or the wait below sees a `false` unrelated to the run ending.
            long startDeadline = System.currentTimeMillis() + 5000;

            while (!layout.isAutoRunning() && System.currentTimeMillis() < startDeadline)
            {
                Thread.sleep(50);
            }

            assertTrue(layout.isAutoRunning(), "precondition: the run should have started by now");

            // And it must have FINISHED before anything is asked about the end of it.
            runner.join(60000);

            assertFalse(runner.isAlive(),
                "the timetable never finished, so this proves nothing about what happens when it does");

            // The window posts its refresh, so give the announcement the same chance the window has.
            Thread.sleep(500);

            assertTrue(whileIdle.get() > 0,
                "the timetable run ended and nothing was told with the railway idle -"
                + " " + announcements.get() + " announcement(s), " + whileIdle.get()
                + " of them while nothing was running.  The graph learns about a reversal only from a"
                + " refresh made when nothing is moving (reconcileFacingWhenIdle refuses otherwise),"
                + " so after a Return Home run - which is a timetable - every train that turned at its"
                + " terminus goes on being drawn facing the way it set off, a dispatch before any"
                + " unrelated repaint is offered paths for the wrong heading, and exiting writes the"
                + " un-reconciled facing to disk.  This is W7-A2: OB-189 was fixed at two of its four"
                + " doors");
        }
        finally
        {
            Layout.TIMETABLE_STUCK_MS = was;
            layout.stopLocomotives();
        }
    }

    /**
     * A HAND DISPATCH announces once its journey is over, with nothing running (OB-189).  `isRunning()` is three things
     * ORed (`running`, a non-empty `activeLocomotives`, a positive thread count) and the announcement has to land after
     * all three have gone; the end-of-path callback fires with the dispatching thread still counted, so the two are
     * counted separately.  A real journey, not a refusal, and autonomy is never started, since `running` is not cleared
     * by a path completing.
     *
     * MUTATION: removing `announceRunFinished()` from `executePath`'s `finally` leaves announcements from the path's
     * own start, milestone and end, all made while the dispatching thread is still counted, and none with the railway
     * idle.
     */
    @Test(timeOut = 120000)
    public void testAHandDispatchTellsTheGraphWhenItsJourneyEnds() throws Exception
    {
        Layout layout = new Layout(model);

        // In the database, because `moveLocomotive` resolves by name against the control station; removed again in the
        // finally.
        MarklinLocomotive loc = model.newMM2Locomotive("W7A2 hand dispatch", 231);

        assertNotNull(loc, "the fixture locomotive could not be created");

        java.util.concurrent.ExecutorService watchdog =
            java.util.concurrent.Executors.newSingleThreadExecutor();

        try
        {
            // The same two sensors `testAutonomySimulationSanity` uses, primed the same way: an unreported sensor does
            // not default to clear, and an occupied destination is refused before the run starts.
            if (!model.isFeedbackSet("47411")) model.newFeedback(47411, null);
            if (!model.isFeedbackSet("47412")) model.newFeedback(47412, null);

            model.setFeedbackState("47411", false);
            model.setFeedbackState("47412", false);

            // AND THE LAYOUT SIMULATES: otherwise nothing sets the destination sensor and the run waits forever.
            layout.setMaxDelay(0);
            layout.setMinDelay(0);
            layout.setSimulate(true);

            layout.createPoint("W7A2 start", true, "47411");
            layout.createPoint("W7A2 end", true, "47412");

            List<Edge> path = Arrays.asList(layout.createEdge("W7A2 start", "W7A2 end"));

            assertTrue(layout.moveLocomotive("W7A2 hand dispatch", "W7A2 start", false),
                "precondition: the locomotive must be standing at the start of the path");

            final AtomicInteger announcements = new AtomicInteger();
            final AtomicInteger whileIdle = new AtomicInteger();

            // SAMPLED WHERE THE ANNOUNCEMENT IS MADE, for the reason the timetable test above gives.
            layout.setCallback("W7A2HandProbe",
                new Layout.TriFunction<List<Edge>, org.traincontrol.base.Locomotive, Boolean, Void>()
            {
                @Override
                public Void apply(List<Edge> edges, org.traincontrol.base.Locomotive who,
                    Boolean locked)
                {
                    announcements.incrementAndGet();

                    if (!layout.isRunning()) whileIdle.incrementAndGet();

                    return null;
                }
            });

            AutonomyRefreshCallback.attach(layout, () -> { });

            // NOT `runLocomotives()`: this is the hand-driven door, and starting autonomy would set `running`, which a
            // finished path does not clear.
            assertFalse(layout.isAutoRunning(),
                "precondition: autonomy must NOT be running, or every announcement below is made"
                + " while isRunning() is true for a reason that has nothing to do with this dispatch");

            java.util.concurrent.Future<Boolean> run =
                watchdog.submit(() -> layout.executePath(path, loc, 30, null));

            try
            {
                assertTrue(run.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    "the hand dispatch reported failure rather than completing, so nothing below is"
                    + " about a journey that ended");
            }
            catch (java.util.concurrent.TimeoutException wedged)
            {
                layout.stopLocomotives();

                fail("the hand dispatch never reached its destination, so this test never exercised"
                    + " the end of a run");
            }

            assertTrue(announcements.get() > 0,
                "the journey ran and announced nothing at all, so the probe is not attached and"
                + " neither assertion here means anything");

            assertTrue(whileIdle.get() > 0,
                "the hand dispatch finished and nothing was told with the railway idle - "
                + announcements.get() + " announcement(s), " + whileIdle.get() + " of them while"
                + " nothing was running.  Every announcement a path makes on its own - the start, each"
                + " milestone, the end - is made while the dispatching thread is still counted, so"
                + " isRunning() is true at all of them and reconcileFacingWhenIdle refuses at all of"
                + " them.  Only announceRunFinished(), from executePath's finally after the count"
                + " reaches zero, lands with the railway idle.  Without it a train that turned at its"
                + " destination goes on being drawn facing the way it set off until something"
                + " unrelated repaints, and exiting writes the un-reconciled facing to disk (OB-189,"
                + " W7-A2)");
        }
        finally
        {
            layout.stopLocomotives();
            watchdog.shutdownNow();
            model.deleteLoc("W7A2 hand dispatch");
        }
    }

    /**
     * And the window's ONE refresh callback is what tells the graph: `attachAutonomyRefresh` is the single registration
     * the window makes on every layout it builds, so the call there covers the timetable, Return Home and both
     * hand-driven doors at once.  The comments are stripped before the check, since an assertion its own documentation
     * satisfies is not one.
     *
     * MUTATION: delete `updateVisiblePoints()` from the runnable and this fails.
     */
    @Test
    public void testTheWindowsOneRefreshTellsTheGraph() throws Exception
    {
        String attach = SourceOf.withoutComments(SourceOf.declaration("void attachAutonomyRefresh(Layout layout)"));

        assertTrue(attach.contains("updateVisiblePoints()"),
            "the window's one refresh callback does not tell the graph.  updateVisiblePoints() is the"
            + " only route to reconcileFacingWhenIdle, which is the only thing that writes a reversal"
            + " a run made into the setup - so a timetable or Return Home run ends with the diagram"
            + " still drawing every turned train the way it set off (W7-A2)");
    }
}
