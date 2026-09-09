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
     * Runs one diagram refresh on the event thread and says what it cost, waiting as long as it takes.
     *
     * @return the milliseconds it took
     */
    private static long refreshOnTheEventThread() throws Exception
    {
        try
        {
            return refreshOnTheEventThread(TimeUnit.MINUTES.toMillis(2));
        }
        catch (TimeoutException nothingIsHeld)
        {
            fail("a refresh with nothing locked took over two minutes: " + whereTheEventThreadIs());

            return -1;
        }
    }

    /**
     * The same, given only so long.
     *
     * The wait is made from a THIRD thread rather than by this one calling `invokeAndWait`, because
     * `invokeAndWait` has no timeout: were the event thread wedged, this test would be too.
     *
     * @param patience how long to wait
     * @return the milliseconds it took
     * @throws TimeoutException when the event thread did not finish in time
     */
    private static long refreshOnTheEventThread(long patience) throws Exception
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

                SwingUtilities.invokeAndWait(() -> ui.updateVisiblePoints());

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
