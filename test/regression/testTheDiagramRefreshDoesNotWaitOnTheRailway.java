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
 * **THE MEASURED DOORS ARE HERE; THE CENSUS IS NEXT DOOR.**  This class holds a monitor and times a
 * call, which is the only thing that can prove a door is shut - but it can only do that for the doors
 * somebody has written a test for, and this defect has arrived through seven of them in two rounds.
 * `testNothingOnTheEventThreadTakesTheRailwaysMonitor` is the other half: it reads the list of
 * `synchronized` methods out of `Layout.java` and requires every call to one of them from a
 * user-interface class to be written down with the thread it is on.  A source guard that used to live
 * in this file pinned a single method name against a rule its own failure message stated generally,
 * and reported clean about four doors; it has been deleted rather than kept beside the general one.
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
     * The fourth door: the caption rule asks the railway whether autonomy can choose each station.
     *
     * D3-B1.  `captionIsActive` reached `Layout.isChoosableByAutonomy` - `synchronized` on the
     * `Layout` - once per captioned square, and it is asked from three places that all run on the
     * event thread: the grid build inside `repaintLayout`'s `invokeLater`, the overlay toggle, and
     * `refreshCaptionVisibility`.  So switching diagram pages during a run froze the window for the
     * remainder of somebody else's departure.  `updateVisiblePoints` was fixed for exactly this and
     * its own comment now says the old reasoning "surveyed one of the two things this method does";
     * the caption path is the thing it did not survey either.
     *
     * The answer is worked out on `CoveredTrackRenderer` now and read off a volatile field, so this
     * costs a set lookup whoever is holding the railway.
     *
     * MUTATION: put the `for (Point point : ...) if (railway.isChoosableByAutonomy(point))` loop back
     * into `captionIsActive` and this times out with the event thread parked on the railway's monitor.
     */
    @Test
    public void testTheCaptionRuleDoesNotWaitOnTheRailway() throws Exception
    {
        // The setting off, or the rule short-circuits on its first line and nothing is measured.
        final java.util.prefs.Preferences prefs =
            java.util.prefs.Preferences.userNodeForPackage(TrainControlUI.class);

        final boolean was = prefs.getBoolean(TrainControlUI.SHOW_INACTIVE_LABELS_PREF,
            TrainControlUI.SHOW_INACTIVE_LABELS_DEFAULT);

        prefs.putBoolean(TrainControlUI.SHOW_INACTIVE_LABELS_PREF, false);

        try
        {
            final java.lang.reflect.Method rule = TrainControlUI.class.getDeclaredMethod(
                "captionIsActive", org.traincontrol.automationui.TileGraph.TileKey.class);

            rule.setAccessible(true);

            final java.util.List<org.traincontrol.automationui.TileGraph.TileKey> squares =
                new java.util.ArrayList<>(session.getStationIndex().squares());

            assertFalse(squares.isEmpty(),
                "the snapshot has no station squares, so the caption rule was never asked and this"
                + " test measured nothing");

            whileTheRailwayIsHeld("TrainControlUI.captionIsActive", () ->
            {
                try
                {
                    // EVERY square, because the defect was one monitor acquisition PER CAPTION - the
                    // three doors all walk the whole diagram, and one lucky square proves nothing.
                    for (org.traincontrol.automationui.TileGraph.TileKey square : squares)
                    {
                        rule.invoke(ui, square);
                    }
                }
                catch (Exception failed)
                {
                    throw new RuntimeException(failed);
                }
            });
        }
        finally
        {
            prefs.putBoolean(TrainControlUI.SHOW_INACTIVE_LABELS_PREF, was);
        }
    }

    /**
     * The fifth door: the diagram's right-click menu is built on the event thread.
     *
     * D3-A1.  The menu's constructor asked `Layout.getPossiblePaths` - `synchronized`, and itself a
     * search of the whole graph - and then `isOfferableToOperator` and `isChoosableByAutonomy` once per
     * candidate path, all inside `showFor`'s `invokeLater`.  Right-clicking an idle train while another
     * one was mid-dispatch parked the event thread on the railway's monitor for the length of that
     * train's switch-throwing, which is the most ordinary gesture there is.
     *
     * `showFor` gathers the answers on a worker now and hands them to the constructor, so what is
     * measured here is the constructor: it must build a menu about a square with a train on it without
     * asking the railway anything.
     *
     * A TRAIN IS PLACED FIRST rather than hoped for.  The path section only exists for a square with a
     * locomotive standing on it, so a fixture that happens to have none would measure the empty case
     * and report clean - which is the shape of a guard that passes because it never ran.
     *
     * AND THE GATHERED ANSWERS ARE REAL ONES, for the same reason.  Handing the constructor a null - the
     * "nothing to offer" case - would skip the whole path section, and a search put back INSIDE it
     * would be invisible here.  So `gatherPathOptions` is called first, on this thread, which is
     * exactly what `showFor`'s worker does; only the drawing is then timed on the event thread.
     *
     * MUTATION: move the `getPossiblePaths` call back out of `gatherPathOptions` into the constructor
     * and this times out.
     */
    @Test
    public void testTheRightClickMenuDoesNotWaitOnTheRailway() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey occupied = squareWithATrainOnIt();

        Class<?> menu = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

        final java.lang.reflect.Constructor<?> build = menu.getDeclaredConstructors()[0];

        assertEquals(build.getParameterCount(), 4,
            "the menu's constructor no longer takes the gathered answers as its fourth argument, so"
            + " either it is asking the railway itself again or this test is building the wrong thing");

        build.setAccessible(true);

        // OFF THE EVENT THREAD, on this one, which is the whole arrangement being tested.
        //
        // The square is resolved first, as `showFor` resolves it - on the event thread, because that
        // lookup goes through the lazy session builder.  What the worker is handed is a Point.
        java.lang.reflect.Method gather = menu.getDeclaredMethod("gatherPathOptions",
            TrainControlUI.class, org.traincontrol.automation.Point.class);

        gather.setAccessible(true);

        final Object answers = gather.invoke(null, ui, ui.getAutonomyPointForTile(occupied));

        assertNotNull(answers,
            "the gather found nothing to offer for a square with a train standing on it, so the path"
            + " section would be absent and this test would measure the empty menu");

        whileTheRailwayIsHeld("LayoutRightclickAutonomyMenu's constructor", () ->
        {
            try
            {
                build.newInstance(ui, occupied, occupied, answers);
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });
    }

    /**
     * The sixth door: the autonomy editor's "why is this train not moving" tool.
     *
     * D3-C1, and the last `ON THE EVENT THREAD` allowance in
     * `testNothingOnTheEventThreadTakesTheRailwaysMonitor`.  `AutonomyEditorPanel.applyWhy` asked
     * `Layout.explainDestinations` - `synchronized`, and a walk of the whole graph once per candidate -
     * straight from the click that started it, and then ran `GraphReducer.findPath` once more for
     * every station the railway offered.
     *
     * **IT WAS NOT A LIVE FREEZE, AND IT IS MOVED ANYWAY.**  The editor cannot be open while autonomy
     * is running (OB-047), so no dispatch can be holding that monitor while this tool is used - but
     * `AutoLocomotiveStatus.findPaths` can, with nothing running at all, which is the state this
     * fixture is in and the state the held monitor below stands for.  Adam, 2026-09-09: *"I would
     * rather take it off EDT.  It's not critical now, but we want to avoid these pitfalls."*  A rule
     * with one standing exception is a rule with a way in.
     *
     * The question goes to `WhyRenderer` now and the answer is painted by an `invokeLater`, which is
     * the shape `refreshCoveredTrack` and `refreshReturnHomeButton` already use.  What stays on the
     * event thread is the capture: `layoutSource.get()` BUILDS a `Layout` when there is none and
     * `getStationIndex` DERIVES the square-to-Point translation when nothing has yet, so neither may
     * be asked for on a worker.
     *
     * MUTATION: call `composeWhy` directly from `applyWhy` instead of submitting it, and this times
     * out with the event thread parked on `Layout.explainDestinations`.
     */
    @Test
    public void testTheWhyToolDoesNotWaitOnTheRailway() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey occupied = squareWithATrainOnIt();

        final org.traincontrol.gui.AutonomyEditorPanel panel = aWhyPanel();

        final org.traincontrol.base.LayoutDiagramComponent drawn = theSquareItself(occupied);

        final java.lang.reflect.Method why = theWhyTool();

        whileTheRailwayIsHeld("AutonomyEditorPanel.applyWhy", () ->
        {
            try
            {
                why.invoke(panel, occupied, drawn);
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });

        // AND THE ANSWER ACTUALLY LANDS.  Without this the test above is satisfied by an `applyWhy`
        // that does nothing at all - "did not block" is true of a method with an empty body, and this
        // tool being silently blank is OB-191, which is the other way it has been broken.
        assertTrue(awaitWhy(panel), "the why answer never landed, so nothing was ever painted from it");

        pump();

        String said = whatTheEditorSays(panel);

        assertFalse(said.trim().isEmpty(),
            "the why tool painted nothing at all - the banner is empty, which is OB-191");

        assertFalse(said.contains(org.traincontrol.util.I18n.t("autolayout.ui.whyWorking")),
            "the banner still says the answer is being worked out, so the worker's answer never"
            + " replaced the message `applyWhy` puts up while it waits.  What it says: " + said);
    }

    /**
     * And what it says is the railway's own answer, not a shape that happens to be there.
     *
     * OB-191 was this tool painting the paths and saying nothing - *"the banner expands, but I see no
     * text"* - so "it answered" has to mean the answer is READ BACK and checked against the railway,
     * which is the only thing that can tell a report from an empty strip.
     *
     * **THE RAILWAY IS THE ORACLE, ASKED FROM THIS THREAD.**  `explainCannotStart` and
     * `explainDestinations` are asked here, off the event thread, where blocking is allowed - and the
     * report must agree with them about the two things it can be: a train that cannot start at all
     * names the reason it cannot, and a train that can names either the stations it may go to or the
     * fact that there are none.  Nothing here re-implements the panel's collapse of Points onto
     * squares; it compares against the answers the panel was composed from.
     *
     * **AND THE LINES ARE THE OTHER HALF.**  A train with somewhere to go draws its routes on the
     * diagram, and those are built on the worker now and installed by the paint - so where the
     * reduction can find a run to a station the railway offers, `traces` must not be empty.  That is
     * the assertion a `paintWhy` that dropped the worker's map would fail.
     */
    @Test
    public void testTheWhyToolSaysWhatTheRailwaySays() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey occupied = squareWithATrainOnIt();

        final org.traincontrol.gui.AutonomyEditorPanel panel = aWhyPanel();

        final org.traincontrol.base.LayoutDiagramComponent drawn = theSquareItself(occupied);

        final java.lang.reflect.Method why = theWhyTool();

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                why.invoke(panel, occupied, drawn);
            }
            catch (Exception failed)
            {
                throw new RuntimeException(failed);
            }
        });

        assertTrue(awaitWhy(panel), "the why answer never landed");

        pump();

        String said = whatTheEditorSays(panel);

        // WHICH TRAIN THE REPORT IS ABOUT.  Every point of the square is asked, because a square is
        // several Points and which copy holds the locomotive is the session's business, not this
        // test's - so the report has to name one of the trains standing there, and the message says
        // which ones those were.
        java.util.List<String> standing = new java.util.ArrayList<>();

        for (String pointName : session.getStationIndex().pointNamesAt(occupied))
        {
            org.traincontrol.automation.Point at = layout.getPoint(pointName);

            if (at != null && at.getCurrentLocomotive() != null)
            {
                standing.add(at.getCurrentLocomotive().getName());
            }
        }

        assertFalse(standing.isEmpty(),
            "no train is standing on " + occupied + " any more, so the report cannot be about one and"
            + " this test is measuring the empty case");

        boolean named = false;

        for (String train : standing)
        {
            if (said.contains(train.replace("&", "&amp;").replace("<", "&lt;"))) named = true;
        }

        assertTrue(named,
            "the report does not name any of the trains standing on " + occupied + " " + standing
            + ", so it is not about the train that was clicked.  What it says: " + said);

        // THE RAILWAY'S OWN TWO ANSWERS, asked from this thread, where waiting for its monitor is
        // allowed.
        org.traincontrol.base.Locomotive train = null;

        for (String pointName : session.getStationIndex().pointNamesAt(occupied))
        {
            org.traincontrol.automation.Point at = layout.getPoint(pointName);

            if (at != null && at.getCurrentLocomotive() != null)
            {
                train = at.getCurrentLocomotive();
                break;
            }
        }

        String cannotStart = layout.explainCannotStart(train);

        if (cannotStart != null)
        {
            assertTrue(said.contains(cannotStart.replace("&", "&amp;").replace("<", "&lt;")),
                "the railway says " + train.getName() + " cannot be sent anywhere because \""
                + cannotStart + "\", and the report does not say so.  What it says: " + said);

            return;
        }

        java.util.Map<String, String> reasons = layout.explainDestinations(train);

        boolean somewhereToGo = reasons.containsValue(null);

        String nowhere = org.traincontrol.util.I18n.t("autosetup.ui.whyNowhere");

        assertEquals(!said.contains(nowhere), somewhereToGo,
            "the railway offers " + (somewhereToGo ? "at least one" : "no") + " destination to "
            + train.getName() + ", and the report says the opposite.  `explainDestinations` came back"
            + " with " + reasons.size() + " station(s), of which " + (somewhereToGo ? "some" : "none")
            + " were available.  What the report says: " + said);

        if (!somewhereToGo || session.getReducer() == null) return;

        // AND THE LINES, where the reduction can draw one.  Asked of the reducer directly rather than
        // inferred from the report: a station the running graph offers need not be reachable across
        // the DIAGRAM, and this test may only demand a line where one can be drawn.
        boolean aRunExists = false;

        for (java.util.Map.Entry<String, String> entry : reasons.entrySet())
        {
            if (entry.getValue() != null) continue;

            org.traincontrol.automationui.TileGraph.TileKey where =
                session.getStationIndex().squareOf(entry.getKey());

            if (where == null) continue;

            if (session.getReducer().findPath(occupied, where, session.mayTurnTiles(),
                session.mandatoryTurnTiles(), session.barredArrivals(), session.shutTiles()) != null)
            {
                aRunExists = true;
                break;
            }
        }

        if (!aRunExists) return;

        java.lang.reflect.Field traces =
            org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("traces");

        traces.setAccessible(true);

        assertFalse(((java.util.Map<?, ?>) traces.get(panel)).isEmpty(),
            "the reduction can draw a run from " + occupied + " to a station the railway offers, and"
            + " the editor drew nothing.  The traces are built on the worker now and installed by the"
            + " paint, so an answer whose lines are dropped on the way looks exactly like this");
    }

    /**
     * An autonomy editor panel wired to this fixture's railway, built on the event thread.
     *
     * A null page, which is how `TrainControlUI` builds this panel for the tile menus: the square is
     * handed straight to the tool, so there is no diagram for it to name.
     *
     * @return the panel
     * @throws Exception when the event thread refuses to build it
     */
    private static org.traincontrol.gui.AutonomyEditorPanel aWhyPanel() throws Exception
    {
        final org.traincontrol.gui.AutonomyEditorPanel[] built =
            new org.traincontrol.gui.AutonomyEditorPanel[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, null, () -> { });

            built[0].setLayoutSource(() -> layout);
        });

        assertNotNull(built[0], "the editor panel was not built");

        return built[0];
    }

    /**
     * What is drawn on a square, which the tool refuses to answer about unless it is a sensor.
     *
     * @param square the station's square
     * @return the tile
     */
    private static org.traincontrol.base.LayoutDiagramComponent theSquareItself(
        org.traincontrol.automationui.TileGraph.TileKey square)
    {
        org.traincontrol.base.LayoutDiagramComponent drawn = session.getGraph().getTiles().get(square);

        assertNotNull(drawn, "the graph has no tile at " + square + ", so the tool would refuse");

        assertTrue(drawn.isFeedback(),
            square + " is not a sensor, so the why tool answers `labelPointNotStation` and never"
            + " reaches the railway at all - this test would measure the refusal");

        return drawn;
    }

    /**
     * The tool itself, which is private and reached from a mouse listener.
     *
     * @return the method
     * @throws Exception when it is no longer there under that name
     */
    private static java.lang.reflect.Method theWhyTool() throws Exception
    {
        java.lang.reflect.Method why = org.traincontrol.gui.AutonomyEditorPanel.class
            .getDeclaredMethod("applyWhy", org.traincontrol.automationui.TileGraph.TileKey.class,
                org.traincontrol.base.LayoutDiagramComponent.class);

        why.setAccessible(true);

        return why;
    }

    /**
     * Waits for every why ask this test has started to be worked out.
     *
     * @param panel the editor
     * @return true when nothing is outstanding
     * @throws Exception when the panel no longer offers the wait
     */
    private static boolean awaitWhy(org.traincontrol.gui.AutonomyEditorPanel panel) throws Exception
    {
        java.lang.reflect.Method waiting =
            org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredMethod("awaitWhy", long.class);

        waiting.setAccessible(true);

        return (Boolean) waiting.invoke(panel, 60000L);
    }

    /**
     * What the editor's hint line says, with its mark-up left in.
     *
     * The panel is built here without a banner, so `say` and `sayRich` both write into the hint label
     * - which is the fallback the panel documents for a panel mounted without one.
     *
     * @param panel the editor
     * @return the text
     * @throws Exception when the label is no longer there
     */
    private static String whatTheEditorSays(org.traincontrol.gui.AutonomyEditorPanel panel)
        throws Exception
    {
        java.lang.reflect.Field label =
            org.traincontrol.gui.AutonomyEditorPanel.class.getDeclaredField("hint");

        label.setAccessible(true);

        String said = ((javax.swing.JLabel) label.get(panel)).getText();

        return said == null ? "" : said;
    }

    /**
     * A station square with a locomotive standing on it, placing one if the snapshot has none there.
     *
     * @return the square
     */
    private static org.traincontrol.automationui.TileGraph.TileKey squareWithATrainOnIt()
    {
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> squares =
            new java.util.ArrayList<>(session.getStationIndex().squares());

        assertFalse(squares.isEmpty(), "the snapshot has no station squares to right-click");

        for (org.traincontrol.automationui.TileGraph.TileKey square : squares)
        {
            org.traincontrol.automation.Point at = ui.getAutonomyPointForTile(square);

            if (at != null && at.getCurrentLocomotive() != null) return square;
        }

        // NONE STANDING, so one is put down.  The first square whose Point is a destination, and the
        // first locomotive the setup means to run.
        for (org.traincontrol.automationui.TileGraph.TileKey square : squares)
        {
            org.traincontrol.automation.Point at = ui.getAutonomyPointForTile(square);

            if (at == null || !at.isDestination()) continue;

            for (org.traincontrol.base.Locomotive loc : layout.getLocomotivesToRun())
            {
                layout.moveLocomotive(loc.getName(), at.getName(), false);

                return square;
            }
        }

        fail("no square on this snapshot can hold a train, so the right-click menu's path section was"
            + " never reachable and this test would have measured the empty case");

        return null;
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
