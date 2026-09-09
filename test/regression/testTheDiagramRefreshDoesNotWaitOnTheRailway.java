package regression;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Accessory;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * OB-192: the window froze the moment autonomy started, and the trains kept running.
 *
 * Adam, 2026-09-09: *"starting autonomous operation from the current track state, via the netbeans
 * compiled jar, makes the UI unresponsive.  Trains still run, but nothing is repainted, and controls
 * are stuck."*  And: *"I am now seeing autonomy freeze on every run, with the UI completely
 * unresponsive... Things still seem to run in the background, just not the UI."*
 *
 * Trains moving with nothing repainting and no control answering is not a crash and not a stalled
 * railway - it is the EVENT THREAD blocked on a lock somebody else is holding.
 *
 * **THE TWO LEGS, WHICH IS WHY THERE ARE TWO TESTS.**  `Layout.java`'s own comment above `getEdges`
 * names this deadlock and forbids exactly one half of it:
 *
 * - **The railway takes the WINDOW's monitor while holding its OWN.**  `configureAndLockPath` is
 *   `synchronized (this)` on the `Layout` and commands an accessory per edge inside that block;
 *   `MarklinAccessory.setSwitched` calls `TrainControlUI.repaintSwitch`, which is `synchronized` on
 *   the window.  `testTheRailwayTakesTheWindowsMonitorWhileHoldingItsOwn` measures that this leg is
 *   real and it is meant to stay - a driving thread has to be able to say a switch has moved.
 *
 * - **So the window must NEVER take the RAILWAY's monitor while holding its own.**
 *   `TrainControlUI.updateVisiblePoints` is `synchronized` on the window and is called on the event
 *   thread by `DiagramMonitorDriver` every tick of a run and by `attachAutonomyRefresh` at both ends
 *   of every path.  It reached `Layout.edgesCoveredByStandingTrains`, which is `synchronized` on the
 *   `Layout`.  AB-BA, and the freeze Adam is describing.
 *   `testTheDiagramRefreshDoesNotWaitOnTheRailway` is the claim that this leg is gone.
 *
 * **BOUNDED, ALWAYS.**  Neither test may leave a thread stuck: the monitor a helper takes is released
 * on a timer whatever happens, and every wait is a `Future.get(timeout)` that fails with a message
 * rather than a hang.  Producing the real deadlock would be a truer reproduction and would leave a
 * wedged event thread behind in a JVM that then never exits, which poisons every class after it.
 *
 * **THE FREE COST IS MEASURED FIRST**, so a red result is attributable.  A refresh that is slow on its
 * own would time out here for a reason that has nothing to do with a monitor, so the same refresh is
 * timed with the railway's monitor free and the assertion is made against that baseline as well as
 * against the wall clock.
 *
 * MUTATION: put the blocking call back - have `refreshCoveredTrack` ask
 * `getAutonomySession().tilesBlockedByStandingTrains(this.model.getAutoLayout())` on the event thread
 * - and `testTheDiagramRefreshDoesNotWaitOnTheRailway` goes red, timing out with the event thread
 * parked on `Layout.edgesCoveredByStandingTrains`.
 *
 * @author Adam
 */
public class testTheDiagramRefreshDoesNotWaitOnTheRailway
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;

    /**
     * How long a helper holds a monitor.  Long enough that a blocked refresh cannot finish inside the
     * window by luck, short enough that a red run costs seconds rather than the class.
     */
    private static final long HOLD_MS = 8000;

    /**
     * How long the event thread is given to complete one refresh while that monitor is held.
     *
     * Generous against the free cost measured in `@BeforeClass` and far below `HOLD_MS`, so the two
     * outcomes are unambiguous: a refresh that does not take the railway's monitor returns in
     * milliseconds, and one that does cannot return until the hold expires.
     */
    private static final long PATIENCE_MS = 3000;

    /** What one refresh costs with nothing held, measured before anything is locked. */
    private static long freeCostMs;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the window and its event thread are the subject here");
        }

        // THE FROZEN COPY, never the operator's own railway.  `Scenario.folderFor` is the only naming
        // of a fixture folder that cannot spell its way out to `cs2_sample_layout`, and the sandbox is
        // opened BEFORE `init`, which reads the layout preference (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so there is no event thread to block");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new SkipException("no autonomy setup on this railway");

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the snapshot did not build to a layout");

        // WARMED, then measured.  The first refresh of a session pays for class loading and for the
        // station captions being written for the first time; timing that would set a baseline nothing
        // else ever matches.
        refreshOnTheEventThread();

        freeCostMs = refreshOnTheEventThread();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (layout != null) layout.stopLocomotives();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    // ---------------------------------------------------------------- the claim

    /**
     * The event thread finishes a diagram refresh while a driving thread holds the railway's monitor.
     *
     * This is OB-192 stated as a property.  Every long-held acquisition of that monitor belongs to a
     * thread that is driving a train - `configureAndLockPath` holds it across a `CONFIGURE_SLEEP` per
     * accessory of the path - so a refresh that waits for it waits for ironwork to move, on the thread
     * that draws the whole application.
     */
    @Test
    public void testTheDiagramRefreshDoesNotWaitOnTheRailway() throws Exception
    {
        assertTrue(freeCostMs < PATIENCE_MS,
            "one refresh costs " + freeCostMs + "ms with nothing held at all, which is already more"
            + " than the " + PATIENCE_MS + "ms this test allows - so a red result below would say"
            + " nothing about monitors.  Raise PATIENCE_MS or find out why the refresh is this slow");

        CountDownLatch taken = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread driving = holdsTheRailway(taken, release);

        try
        {
            assertTrue(taken.await(30, TimeUnit.SECONDS),
                "the helper never got the railway's monitor, so nothing was tested");

            long took;

            try
            {
                took = refreshOnTheEventThread(PATIENCE_MS);
            }
            catch (TimeoutException stuck)
            {
                fail("THE EVENT THREAD IS BLOCKED ON THE RAILWAY'S MONITOR - this is OB-192."
                    + "  One diagram refresh took the free-running cost of " + freeCostMs + "ms and"
                    + " has now been waiting more than " + PATIENCE_MS + "ms, purely because another"
                    + " thread is inside `synchronized (layout)`.  During a run that thread is"
                    + " `configureAndLockPath`, which holds that monitor across a CONFIGURE_SLEEP for"
                    + " every accessory of the path - so the window stops repainting and stops"
                    + " answering while the trains carry on.  Where it is parked:\n"
                    + whereTheEventThreadIs());

                return;
            }

            assertTrue(took < PATIENCE_MS,
                "the refresh took " + took + "ms with the railway's monitor held, against " + freeCostMs
                + "ms with it free.  The event thread is waiting on a lock a driving thread holds");
        }
        finally
        {
            release.countDown();

            driving.join(TimeUnit.SECONDS.toMillis(30));
        }
    }

    /**
     * The other leg, measured rather than assumed: a thread holding the railway's monitor DOES have to
     * wait for the window's.
     *
     * Not a rule being defended - this one is meant to stay, and `Layout.getEdges`'s comment calls it
     * unavoidable: a driving thread commands an accessory from inside `configureAndLockPath`'s
     * `synchronized (this)`, and `MarklinAccessory.setSwitched` tells the window to repaint that
     * switch.  It is here because it is the PREMISE of the test above.  With both legs real the two
     * orders make an AB-BA deadlock, and the only leg that can be removed is the window's.
     *
     * `repaintSwitch` stands for that chain: it is the `synchronized` window method the accessory
     * calls, and it is called here directly so the test does not depend on a real accessory being
     * present on the snapshot.
     */
    @Test
    public void testTheRailwayTakesTheWindowsMonitorWhileHoldingItsOwn() throws Exception
    {
        final CountDownLatch driverIsIn = new CountDownLatch(1);
        final CountDownLatch driverIsOut = new CountDownLatch(1);

        Thread driving = new Thread(() ->
        {
            synchronized (layout)
            {
                driverIsIn.countDown();

                // The call a locomotive thread makes from inside configureAndLockPath.
                ui.repaintSwitch(1, Accessory.accessoryDecoderType.MM2);

                driverIsOut.countDown();
            }
        }, "OB-192 a driving thread");

        driving.setDaemon(true);

        // HELD FOR A MOMENT ONLY, and released in the finally whatever happens - the window's monitor
        // is what the event thread needs to draw anything.
        synchronized (ui)
        {
            driving.start();

            assertTrue(driverIsIn.await(30, TimeUnit.SECONDS),
                "the helper never reached the railway's monitor, so nothing was tested");

            assertFalse(driverIsOut.await(750, TimeUnit.MILLISECONDS),
                "a thread holding the railway's monitor got through `repaintSwitch` while this test"
                + " held the window's, so that call no longer needs the window's monitor.  If that is"
                + " deliberate, the premise of testTheDiagramRefreshDoesNotWaitOnTheRailway has"
                + " changed and this class needs rewriting rather than deleting");
        }

        assertTrue(driverIsOut.await(30, TimeUnit.SECONDS),
            "the driving thread never got the window's monitor even after it was given back");

        driving.join(TimeUnit.SECONDS.toMillis(30));
    }

    /**
     * The Return Home button is refreshed on the event thread while a driving thread holds the
     * railway's monitor.
     *
     * THE SECOND DOOR, and the one the OB-192 fix left standing.  `refreshReturnHomeButton` runs on
     * the event thread - `repaintAutoLocListLite` and `repaintAutoLocListFull` both call it from
     * inside their `invokeLater`, which is what runs on every arrival, every departure and every
     * placement - and it asks `Layout.triageReturnToHome`, which builds a `HomeStaging.snapshot` and
     * so calls `Layout.getHomeStations`, `synchronized` on the `Layout`.  Same monitor, same thread,
     * same freeze as the covered marks.
     *
     * **AND `isAutonomyBusy` DOES NOT COVER IT.**  That guard was believed to: it asks
     * `Layout.isRunning`, which counts `locomotiveThreads` and so is true for a hand dispatch as well
     * as for autonomy.  What it does not cover is every OTHER holder of that monitor with no train
     * moving at all - `AutoLocomotiveStatus.findPaths` calls `getPossiblePaths`, which is
     * `synchronized` and searches the whole graph, once per panel, on the `AutonomyRenderer` worker
     * this same refresh has just submitted to.  So the event thread and that worker race for the
     * railway's monitor on every refresh, with autonomy stopped, which is the state this fixture is
     * in.
     */
    @Test
    public void testTheReturnHomeButtonDoesNotWaitOnTheRailway() throws Exception
    {
        assertFalse(layout.isRunning(),
            "nothing is running on this snapshot, so `refreshReturnHomeButton` reaches the railway"
            + " rather than returning at its guard - if that has changed this test is measuring the"
            + " guard and not the door");

        whileTheRailwayIsHeld("refreshReturnHomeButton", () ->
        {
            try
            {
                java.lang.reflect.Method door =
                    TrainControlUI.class.getDeclaredMethod("refreshReturnHomeButton");

                door.setAccessible(true);

                door.invoke(ui);
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });

        // AND THE ANSWER ACTUALLY LANDS.  Without this the test above is satisfied by a
        // `refreshReturnHomeButton` that does nothing at all - "did not block" is true of a method
        // with an empty body, and the button greying itself correctly is the whole point of it.
        java.lang.reflect.Method waiting =
            TrainControlUI.class.getDeclaredMethod("awaitReturnHomeTriage", long.class);

        waiting.setAccessible(true);

        assertTrue((Boolean) waiting.invoke(ui, 30000L),
            "the return-home triage never landed, so the button is never painted from it");

        java.lang.reflect.Field button = TrainControlUI.class.getDeclaredField("returnHomeButton");

        button.setAccessible(true);

        javax.swing.JButton offered = (javax.swing.JButton) button.get(ui);

        // The railway asked directly, from THIS thread - which may block, and is allowed to, because
        // this is not the event thread.
        final boolean somethingToDo = layout.triageReturnToHome() == null;

        pump();

        assertEquals(offered.isEnabled(), somethingToDo,
            "the button says " + (offered.isEnabled() ? "there is" : "there is nothing")
            + " to send home, and the railway says the opposite.  The answer is worked out on a"
            + " worker now, and this is the assertion that it is still the railway's answer");
    }

    /**
     * The third door: the diagram's right-click menu asks the same question while it is being built.
     *
     * `HomeLocomotiveMenu.addReturnHomeItem` greys the "Return Locomotives Home" item and says why,
     * and it reaches `triageReturnToHome` to find out.  A popup menu is built on the event thread by
     * definition, so this is the same wait with a mouse button behind it rather than an arrival.
     */
    @Test
    public void testTheReturnHomeMenuDoesNotWaitOnTheRailway() throws Exception
    {
        whileTheRailwayIsHeld("HomeLocomotiveMenu.addReturnHomeItem", () ->
        {
            try
            {
                Class<?> menu = Class.forName("org.traincontrol.gui.HomeLocomotiveMenu");

                java.lang.reflect.Method door = menu.getDeclaredMethod("addReturnHomeItem",
                    javax.swing.JComponent.class, TrainControlUI.class);

                door.setAccessible(true);

                door.invoke(null, new javax.swing.JPanel(), ui);
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });
    }

    /**
     * Nothing on the event thread asks the railway whether anything is away from home.
     *
     * **A TRIPWIRE, because the three tests above cannot be the whole guard.**  They measure the three
     * doors that existed when this was written; a fourth surface that wants to grey a control - and
     * this question has grown one roughly every fortnight - would reach `triageReturnToHome` on the
     * event thread and no test above would notice.  That is exactly how this defect arrived: the first
     * OB-192 fix moved `refreshCoveredTrack` off the event thread and left these three, because the
     * sentence saying the event thread must not take the railway's monitor was a comment.
     *
     * So the rule is stated as a property of the source instead.  `Layout.triageReturnToHome` builds a
     * `HomeStaging.snapshot`, which calls `Layout.getHomeStations` - `synchronized` on the `Layout` -
     * and every user interface class runs on the event thread unless it has gone to some trouble not
     * to.  Two methods have gone to that trouble, and they are named below.  Every other surface reads
     * the button the first of them paints, through `isReturnHomeOffered`.
     *
     * **WHAT IT CANNOT SEE, said out loud.**  Source cannot tell which thread a line runs on, so this
     * is a list of methods rather than a proof.  Its value is that a NEW call site cannot be added
     * without somebody reading this and answering the question - which is one more reading than the
     * three call sites this defect was made of ever got.
     *
     * THE WAY PAST IS A LINE, not a rewrite: a surface that must genuinely ask for itself puts its own
     * method here with the thread it is on.  A check with no way past is one people delete.
     *
     * MUTATION: put `layout.triageReturnToHome()` back at the top of `refreshReturnHomeButton` and this
     * fails, quoting the line and naming the method it is in.
     */
    @Test
    public void testNothingOnTheEventThreadAsksWhetherAnythingIsAwayFromHome() throws Exception
    {
        java.io.File gui = new java.io.File("src/org/traincontrol/gui");

        assertTrue(gui.isDirectory(),
            "run this from the project root - " + gui.getAbsolutePath() + " is not there");

        for (java.io.File source : gui.listFiles())
        {
            if (!source.getName().endsWith(".java")) continue;

            String code = withoutComments(read(source));

            if (!code.contains(ASKS)) continue;

            assertEquals(source.getName(), "TrainControlUI.java",
                source.getName() + " asks the railway whether anything is away from home.  Only"
                + " TrainControlUI may, and only from the two methods named in this test - that call"
                + " reaches `Layout.getHomeStations`, which is `synchronized` on the `Layout`, and a"
                + " user interface class is on the event thread unless it has arranged not to be."
                + "  Read `TrainControlUI.isReturnHomeOffered` instead: it is the button the one"
                + " asker paints, so a surface that reads it cannot disagree with the button either");

            // Both spans, and BOTH ARE OFF THE EVENT THREAD - which is the whole content of this list:
            //
            //   `workOutReturnHomeTriage` runs on `ReturnHomeTriageRenderer`, a daemon thread of its
            //   own, and is the ask that paints the button every surface then reads.
            //
            //   `requestReturnToHome` asks once more at the END of a staging run, from inside the
            //   `new Thread` it has already started - the same thread that has been blocking on
            //   `executeTimetable` - to find out whether everybody actually got home.  Its own comment
            //   says why it is asked rather than deduced from the return value.
            java.util.List<int[]> allowed = new java.util.ArrayList<>();

            allowed.add(spanOf(code, "private void workOutReturnHomeTriage()"));
            allowed.add(spanOf(code, "public void requestReturnToHome()"));

            for (int at = code.indexOf(ASKS); at >= 0; at = code.indexOf(ASKS, at + 1))
            {
                boolean inside = false;

                for (int[] span : allowed)
                {
                    if (at >= span[0] && at < span[1]) inside = true;
                }

                assertTrue(inside,
                    "the railway is asked whether anything is away from home outside the two methods"
                    + " that are allowed to ask it, at:\n    " + lineAround(code, at)
                    + "\n\nThat call reaches `Layout.getHomeStations`, which is `synchronized` on the"
                    + " `Layout`, and on the event thread waiting for that monitor is OB-192: a"
                    + " dispatch holds it across a CONFIGURE_SLEEP per accessory of a path, and"
                    + " `AutoLocomotiveStatus.findPaths` holds it for a search of the whole graph with"
                    + " nothing running at all.  Read `isReturnHomeOffered`, or - if this really must"
                    + " ask for itself, from a thread of its own - add the method to the list in this"
                    + " test saying which thread that is");
            }
        }
    }

    /**
     * Where one method begins and the next member's javadoc starts.
     *
     * @param code the source, comments already out
     * @param declaration the method's declaration, exactly as written
     * @return the half-open span
     */
    private static int[] spanOf(String code, String declaration)
    {
        int from = code.indexOf(declaration);

        assertTrue(from >= 0, "`" + declaration + "` is gone, and this test names it as one of the two"
            + " places allowed to ask the railway whether anything is away from home");

        // A comment-stripped file has one blank line where each javadoc was, so the next member is the
        // next declaration at class indent - which is what a line starting with four spaces and a
        // non-space, after the closing brace of this one, is.
        int to = code.indexOf("\n    }\n", from);

        return new int[] { from, to < 0 ? code.length() : to };
    }

    /**
     * The line an offending call is on, for a message somebody can act on.
     *
     * @param code the source
     * @param at where the call is
     * @return that line, trimmed
     */
    private static String lineAround(String code, int at)
    {
        int from = code.lastIndexOf('\n', at) + 1;

        int to = code.indexOf('\n', at);

        return code.substring(from, to < 0 ? code.length() : to).trim();
    }

    /** What asking the railway whether anything is away from home looks like in the source. */
    private static final String ASKS = ".triageReturnToHome(";

    /**
     * A file, as text.
     *
     * @param source the file
     * @return its content
     */
    private static String read(java.io.File source) throws Exception
    {
        byte[] raw = java.nio.file.Files.readAllBytes(source.toPath());

        return new String(raw, "UTF-8");
    }

    /**
     * The same source with its comments taken out.
     *
     * Necessary rather than tidy: this rule is written out at length in the javadoc of every method
     * that used to break it, so counting raw occurrences would count the explanations.
     *
     * @param code the source
     * @return the code alone
     */
    private static String withoutComments(String code)
    {
        StringBuilder out = new StringBuilder(code.length());

        boolean block = false;
        boolean line = false;
        boolean quoted = false;

        for (int at = 0; at < code.length(); at++)
        {
            char here = code.charAt(at);
            char next = at + 1 < code.length() ? code.charAt(at + 1) : '\0';

            if (block)
            {
                if (here == '*' && next == '/') { block = false; at++; }

                continue;
            }

            if (line)
            {
                if (here == '\n') { line = false; out.append(here); }

                continue;
            }

            if (quoted)
            {
                if (here == '\\') { at++; continue; }

                if (here == '"' || here == '\n') quoted = false;

                continue;
            }

            if (here == '/' && next == '*') { block = true; at++; continue; }

            if (here == '/' && next == '/') { line = true; at++; continue; }

            if (here == '"') { quoted = true; continue; }

            out.append(here);
        }

        return out.toString();
    }

    // ---------------------------------------------------------------- the fixture

    /**
     * Starts a daemon thread that takes the railway's monitor and keeps it, exactly as a dispatch does.
     *
     * `synchronized (layout)` is the same monitor every `synchronized` method of `Layout` takes, which
     * is what `configureAndLockPath` is inside while it throws ironwork.
     *
     * RELEASED ON A TIMER as well as on the latch, so a test that fails part way through cannot leave
     * the railway locked for the rest of the class.
     *
     * @param taken counted down once the monitor is held
     * @param release counted down by the caller to let it go
     * @return the running thread
     */
    private static Thread holdsTheRailway(final CountDownLatch taken, final CountDownLatch release)
    {
        Thread driving = new Thread(() ->
        {
            synchronized (layout)
            {
                taken.countDown();

                try
                {
                    release.await(HOLD_MS, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }, "OB-192 a driving thread");

        driving.setDaemon(true);

        driving.start();

        return driving;
    }

    /**
     * Holds the railway's monitor and asserts one door of the window still answers on the event
     * thread.
     *
     * ONE BODY FOR EVERY DOOR, because there is one rule: nothing the event thread does may wait on
     * the `Layout` monitor.  Written out once per door, the fourth door would be measured slightly
     * differently from the first three, and the difference is where a real wait hides.
     *
     * The free cost is measured for THIS door first, so a red result is attributable - a door that is
     * slow on its own would otherwise time out here for a reason that has nothing to do with a
     * monitor.
     *
     * @param door what is being called, for the message
     * @param job the call, made on the event thread
     */
    private static void whileTheRailwayIsHeld(String door, Runnable job) throws Exception
    {
        long free = onTheEventThread(job);

        assertTrue(free < PATIENCE_MS,
            door + " costs " + free + "ms with nothing held at all, which is already more than the "
            + PATIENCE_MS + "ms this test allows - so a red result below would say nothing about"
            + " monitors.  Raise PATIENCE_MS or find out why it is this slow");

        CountDownLatch taken = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread driving = holdsTheRailway(taken, release);

        try
        {
            assertTrue(taken.await(30, TimeUnit.SECONDS),
                "the helper never got the railway's monitor, so nothing was tested");

            long took;

            try
            {
                took = onTheEventThread(job, PATIENCE_MS);
            }
            catch (TimeoutException stuck)
            {
                fail("THE EVENT THREAD IS BLOCKED ON THE RAILWAY'S MONITOR - this is OB-192, at `"
                    + door + "`.  It costs " + free + "ms with that monitor free and has now been"
                    + " waiting more than " + PATIENCE_MS + "ms, purely because another thread is"
                    + " inside `synchronized (layout)`.  That thread is a dispatch inside"
                    + " `configureAndLockPath`, holding it across a CONFIGURE_SLEEP per accessory of"
                    + " the path - or `AutoLocomotiveStatus.findPaths` inside `getPossiblePaths`,"
                    + " searching the whole graph with nothing running at all.  Either way the window"
                    + " stops repainting and stops answering.  Where it is parked:\n"
                    + whereTheEventThreadIs());

                return;
            }

            assertTrue(took < PATIENCE_MS,
                door + " took " + took + "ms with the railway's monitor held, against " + free
                + "ms with it free.  The event thread is waiting on a lock another thread holds");
        }
        finally
        {
            release.countDown();

            driving.join(TimeUnit.SECONDS.toMillis(30));
        }
    }

    /**
     * Runs one diagram refresh on the event thread and says what it cost, waiting as long as it takes.
     *
     * @return the milliseconds it took
     */
    private static long refreshOnTheEventThread() throws Exception
    {
        return onTheEventThread(() -> ui.updateVisiblePoints());
    }

    /**
     * The same, given only so long.
     *
     * @param patience how long to wait
     * @return the milliseconds it took
     * @throws TimeoutException when the event thread did not finish in time
     */
    private static long refreshOnTheEventThread(long patience) throws Exception
    {
        return onTheEventThread(() -> ui.updateVisiblePoints(), patience);
    }

    /**
     * Runs anything on the event thread and says what it cost, waiting as long as it takes.
     *
     * @param job what to run there
     * @return the milliseconds it took
     */
    private static long onTheEventThread(Runnable job) throws Exception
    {
        try
        {
            return onTheEventThread(job, TimeUnit.MINUTES.toMillis(2));
        }
        catch (TimeoutException nothingIsHeld)
        {
            fail("a call with nothing locked took over two minutes: " + whereTheEventThreadIs());

            return -1;
        }
    }

    /**
     * The same, given only so long.
     *
     * The wait is made from a THIRD thread rather than by this one calling `invokeAndWait`, because
     * `invokeAndWait` has no timeout: were the event thread wedged, this test would be too.
     *
     * @param job what to run there
     * @param patience how long to wait
     * @return the milliseconds it took
     * @throws TimeoutException when the event thread did not finish in time
     */
    private static long onTheEventThread(Runnable job, long patience) throws Exception
    {
        ExecutorService watchdog = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread waiting = new Thread(runnable, "OB-192 watchdog");

            waiting.setDaemon(true);

            return waiting;
        });

        try
        {
            Future<Long> refreshed = watchdog.submit(() ->
            {
                long began = System.nanoTime();

                SwingUtilities.invokeAndWait(job);

                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
            });

            try
            {
                return refreshed.get(patience, TimeUnit.MILLISECONDS);
            }
            finally
            {
                // Cancelled but never waited for: the point of the timeout is that the event thread may
                // still be stuck, and the runnable it is stuck in cannot be interrupted out of a
                // monitor.  It unwinds by itself when the helper gives the monitor back.
                refreshed.cancel(true);
            }
        }
        finally
        {
            watchdog.shutdownNow();
        }
    }

    /**
     * Where the event thread is standing, for a failure message somebody can act on.
     *
     * @return its stack, or a note saying it could not be found
     */
    private static String whereTheEventThreadIs()
    {
        for (java.util.Map.Entry<Thread, StackTraceElement[]> running
            : Thread.getAllStackTraces().entrySet())
        {
            if (!running.getKey().getName().startsWith("AWT-EventQueue")) continue;

            StringBuilder out = new StringBuilder("    " + running.getKey().getName()
                + " is " + running.getKey().getState() + "\n");

            int depth = 0;

            for (StackTraceElement frame : running.getValue())
            {
                out.append("        at ").append(frame).append("\n");

                if (++depth >= 25) break;
            }

            return out.toString();
        }

        return "    (no event thread found at all)";
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
