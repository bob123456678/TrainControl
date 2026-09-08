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

/**
 * When a run ends, whoever draws the railway is told - from every door, not from two of the four.
 *
 * W7-A2, and the sibling `OB-189` missed. A destination reversal is recorded by toggling the
 * locomotive's name into `Layout.reversedOnArrival`, and the window writes it to the setup in
 * `TrainControlUI.reconcileFacingWhenIdle` - which refuses while anything is moving and is reached
 * only from `updateVisiblePoints()`. So a turn a run made is PENDING until a refresh happens with the
 * railway idle.
 *
 * `41913ecd` fixed that for the two hand-driven doors by calling `updateVisiblePoints()` after the
 * journey returns. The third and fourth doors are the timetable button and Return Home - which loads
 * a timetable and runs it through the same shared arrival path, so every terminus arrival toggles
 * `reversedOnArrival` - and neither completion handler refreshed anything. After a Return Home run
 * the diagram went on showing every returned train facing the way it set off, a dispatch made before
 * any unrelated repaint was offered paths for the wrong heading, and the exit capture wrote the
 * un-reconciled facing to disk.
 *
 * **Why the announcements a run already makes were not enough.** `Layout` announces to its registered
 * callbacks at both ends of every path, and `announceRunFinished()` announces again when the last
 * locomotive thread goes. Every one of those, during a timetable, fires while `isRunning()` is still
 * true: the arrival callback fires before `executePath`'s `finally` has decremented the thread count,
 * and the thread count reaching zero between two legs happens while `running` is still set for the
 * length of the run. `reconcileFacingWhenIdle` correctly refuses at all of them, and nothing announced
 * afterwards. So the reversals accumulated with nobody to drain them.
 *
 * **One place rather than a fourth copy.** The remedy is that a timetable announces once the railway
 * has actually gone idle - `executeTimetableInternal` already waits for exactly that before it returns
 * - and that the window's single refresh callback tells the graph. That one pair covers the timetable,
 * Return Home, and both hand-driven doors, whose `announceRunFinished()` already fires with the count
 * at zero and nothing running.
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
        // BEFORE the model (OB-111): `init` reads the machine-global layout preference, and on the
        // operator's machine that names his real railway - which the suite then reads, writes back
        // with different line endings, and can raise a modal dialog over.
        //
        // This class was written on 2026-09-08 without one, which
        // `testSwitchingToACentralStationLayout` reported at once: it pins the classes that still
        // build a model bare, and its own instruction for a NEW one is to give it a sandbox rather
        // than to add it to the list.
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * Not registered in the locomotive database - a timetable entry only holds the reference, and a
     * test that took a real address would collide with the operator's own railway (OB-111).
     */
    private MarklinLocomotive dummyLoc()
    {
        MarklinLocomotive loc = new MarklinLocomotive(model, 1, MarklinLocomotive.decoderType.MM2,
            "RunEnd Loc " + (++locCounter));

        // The dispatch loop skips an entry whose locomotive has no usable speed, so without this the
        // run ends for a reason that has nothing to do with what is being measured.
        loc.setPreferredSpeed(35);

        return loc;
    }

    /**
     * A timetable of one entry that can never execute, over a layout with one edge.
     *
     * The same fixture `testLayoutTimetable.testAPermanentlyUnexecutableEntryEndsTheRun` uses: an
     * invalidated layout is refused by `executePath` on its first line, so the run gives up on its own
     * after `TIMETABLE_STUCK_MS` rather than needing hardware. What is being measured here is what
     * happens when the run ENDS, which is the same moment either way.
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
     * A timetable run - which is what Return Home is - announces once the railway has gone idle.
     *
     * The graph is told about a reversal only by a refresh that happens while nothing is running, and
     * the ONLY thing that tells the window a timetable has anything to say is the callback `Layout`
     * fires. Every announcement a timetable makes today is made while `isRunning()` is still true, so
     * `reconcileFacingWhenIdle` refuses at every one of them and the turns a Return Home run made stay
     * pending until something unrelated repaints.
     *
     * MUTATION this catches: remove `announceRunFinished()` from the end of `executeTimetableInternal`
     * and this fails, reporting that nothing was announced with the railway idle. Moving it above the
     * completion wait fails it too, which is the point - an announcement made while the last train is
     * still arriving is one `reconcileFacingWhenIdle` refuses.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test(timeOut = 120000)
    public void testATimetableRunAnnouncesOnceTheRailwayIsIdle() throws Exception
    {
        Layout layout = stuckTimetable();

        final AtomicInteger announcements = new AtomicInteger();
        final AtomicInteger whileIdle = new AtomicInteger();

        // SAMPLED WHERE THE ANNOUNCEMENT IS MADE, not where it is acted on, and the first version of
        // this test got that wrong and passed.
        //
        // `AutonomyRefreshCallback` posts its runnable to the event thread, so asking `isRunning()`
        // inside it asks whenever the queue gets round to it - which on a fixture that announces
        // hundreds of times in four seconds is often after the run has ended.  The test then reported
        // a property nothing in the program has: the announcements were all made mid-run and merely
        // ARRIVED late.  That is exactly W7-A2's "self-heals on the next unrelated refresh", which is
        // what makes the defect intermittent, and a test that accepts it cannot see the defect.
        //
        // A raw callback under its own name - `Layout` keys them, so this coexists with the window's
        // rather than replacing it - answers the question that decides the outcome: was an
        // announcement ever MADE with the railway idle?
        layout.setCallback("W7A2Probe",
            new Layout.TriFunction<List<Edge>, org.traincontrol.base.Locomotive, Boolean, Void>()
        {
            @Override
            public Void apply(List<Edge> edges, org.traincontrol.base.Locomotive loc, Boolean locked)
            {
                announcements.incrementAndGet();

                // WHAT reconcileFacingWhenIdle ASKS.  It returns immediately while anything is
                // moving - a train between two copies is meant to disagree with the graph - so an
                // announcement made then reaches the window and changes nothing.
                if (!layout.isRunning()) whileIdle.incrementAndGet();

                return null;
            }
        });

        // And the window's own door is attached as well, because that is the one the remedy has to
        // travel through: it is what carries the announcement above to updateVisiblePoints().
        AutonomyRefreshCallback.attach(layout, () -> { });

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

            // The run must actually have STARTED, or the wait below sees a `false` that has nothing to
            // do with the run ending.  executeTimetableInternal sets `running` only after the worker is
            // scheduled - testLayoutTimetable guards its own waits the same way.
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
     * And the window's ONE refresh callback is what tells the graph.
     *
     * The announcement above is useless unless somebody acts on it, and the acting is
     * `updateVisiblePoints()` - the only route to `reconcileFacingWhenIdle`. `attachAutonomyRefresh`
     * is the single registration the window makes on every layout it builds, so putting the call there
     * covers the timetable, Return Home and both hand-driven doors at once, rather than adding a
     * fourth copy of the same line at a fourth caller (W7-C2: the per-caller shape is what left the
     * timetable out).
     *
     * The comments are stripped before the check, because the paragraph explaining why the call is
     * there contains the name of the call - and an assertion its own documentation satisfies is not an
     * assertion. `testTheWindowAttachesItsRefreshCallback` was caught making exactly that mistake.
     *
     * MUTATION this catches: delete `updateVisiblePoints()` from the runnable and this fails.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheWindowsOneRefreshTellsTheGraph() throws Exception
    {
        String ui = codeOnly(new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get("src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8));

        String attach = bodyOf(ui, "void attachAutonomyRefresh(Layout layout)");

        assertNotEquals(attach, "",
            "attachAutonomyRefresh is not declared that way any more, so this checked nothing");

        assertTrue(attach.contains("updateVisiblePoints()"),
            "the window's one refresh callback does not tell the graph.  updateVisiblePoints() is the"
            + " only route to reconcileFacingWhenIdle, which is the only thing that writes a reversal"
            + " a run made into the setup - so a timetable or Return Home run ends with the diagram"
            + " still drawing every turned train the way it set off (W7-A2)");
    }

    /** The declaration's body, brace-matched. */
    private static String bodyOf(String source, String declaration)
    {
        // Mixed line endings in this file are not part of the rule being checked
        source = source.replace("\r\n", "\n");
        declaration = declaration.replace("\r\n", "\n");

        int at = source.indexOf(declaration);

        if (at < 0) return "";

        int open = source.indexOf('{', at + declaration.length());

        if (open < 0) return "";

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(at, i + 1);
        }

        return "";
    }

    /** The source with its line comments removed, so a comment cannot satisfy an assertion. */
    private static String codeOnly(String source)
    {
        StringBuilder out = new StringBuilder();

        for (String line : source.split("\n", -1))
        {
            int slashes = line.indexOf("//");

            out.append(slashes >= 0 ? line.substring(0, slashes) : line).append("\n");
        }

        return out.toString();
    }
}
