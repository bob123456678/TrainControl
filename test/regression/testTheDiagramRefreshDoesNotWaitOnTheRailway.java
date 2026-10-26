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
 * OB-192: the window froze while autonomy ran; the trains kept moving but nothing repainted.
 *
 * The event thread was blocked on a lock someone else held.  `Layout.java`'s comment above `getEdges` names the
 * deadlock; one test per half: the railway must not take the window's monitor while holding its own
 * (`testADrivingThreadNeverWaitsForTheWindowsMonitor`), and the window must never take the railway's, nor on the event
 * thread at all (`testTheDiagramRefreshDoesNotWaitOnTheRailway`).  The measured doors are here;
 * `testNothingOnTheEventThreadTakesTheRailwaysMonitor` is the census of the rest.
 *
 * Bounded: a helper's monitor is released on a timer and every wait is a `Future.get(timeout)`, so a failure is a
 * message, not a hang.  The free cost of a refresh is measured first, so a red result is attributable.
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
     * How long a helper holds a monitor: longer than a refresh could finish by luck, short enough for a cheap red run.
     */
    private static final long HOLD_MS = 8000;

    /** How long the event thread gets for one refresh while that monitor is held; far below HOLD_MS. */
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

        // The frozen copy, never the operator's railway; opened before `init`, which reads the layout preference.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so there is no event thread to block");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new AssertionError("no autonomy setup on this railway");

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the snapshot did not build to a layout");

        // Warmed, then measured: the first refresh pays for class loading and would set a baseline nothing matches.
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
     * (`configureAndLockPath` holds it across a `CONFIGURE_SLEEP` per accessory.)
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
     * A driving thread commands an accessory, holding the railway's monitor and the accessory's locks, without waiting
     * for the window's monitor, which another thread holds.
     *
     * MUTATION: make `TrainControlUI.repaintSwitch` `synchronized` again, and this fails.
     */
    @Test
    public void testADrivingThreadNeverWaitsForTheWindowsMonitor() throws Exception
    {
        final Accessory accessory = anAccessoryAPathCommands();

        java.lang.reflect.Method lockOf = Layout.class.getDeclaredMethod("signalLock", Accessory.class);

        lockOf.setAccessible(true);

        final Object signalLock = lockOf.invoke(layout, accessory);

        final CountDownLatch driverIsIn = new CountDownLatch(1);
        final CountDownLatch driverIsOut = new CountDownLatch(1);

        Thread driving = new Thread(() ->
        {
            synchronized (layout)
            {
                synchronized (signalLock)
                {
                    driverIsIn.countDown();

                    // The command a dispatch sends, to the setting the accessory already has
                    accessory.setState(accessory.isSwitched()
                        ? Accessory.accessorySetting.RED : Accessory.accessorySetting.GREEN);

                    driverIsOut.countDown();
                }
            }
        }, "RSA67-C1 a driving thread");

        driving.setDaemon(true);

        try
        {
            // Held for a moment only, and released whatever happens.
            synchronized (ui)
            {
                driving.start();

                assertTrue(driverIsIn.await(30, TimeUnit.SECONDS),
                    "the helper never reached the railway's monitor, so nothing was tested");

                assertTrue(driverIsOut.await(PATIENCE_MS, TimeUnit.MILLISECONDS),
                    "a thread holding the railway's monitor and a signal's lock, commanding " + accessory.getName()
                    + ", waited for the window's monitor, which this test holds - and the window can hold it while it"
                    + " reaches a protection refresh that waits for that lock: a deadlock (RSA67-C1)");
            }
        }
        finally
        {
            driving.join(TimeUnit.SECONDS.toMillis(30));
        }
    }

    /** An accessory a path on the snapshot commands, found without creating one. */
    private static Accessory anAccessoryAPathCommands()
    {
        for (org.traincontrol.automation.Edge edge : layout.getEdges())
        {
            for (String name : edge.getConfigCommands().keySet())
            {
                Accessory found = model.getAccessoryByName(name);

                if (found != null) return found;
            }
        }

        throw new AssertionError("no path on this railway commands an accessory");
    }

    /**
     * The Return Home button is refreshed on the event thread while a driving thread holds the railway's monitor.
     *
     * The second door: `refreshReturnHomeButton` asks `Layout.triageReturnToHome`, which is `synchronized` on the
     * `Layout`.  `isAutonomyBusy` does not cover it: `AutoLocomotiveStatus.findPaths` holds the monitor with nothing
     * running, on the worker this same refresh has just submitted to.
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

        // The answer must also land: "did not block" is true of a method with an empty body.
        java.lang.reflect.Method waiting =
            TrainControlUI.class.getDeclaredMethod("awaitReturnHomeTriage", long.class);

        waiting.setAccessible(true);

        assertTrue((Boolean) waiting.invoke(ui, 30000L),
            "the return-home triage never landed, so the button is never painted from it");

        java.lang.reflect.Field button = TrainControlUI.class.getDeclaredField("returnHomeButton");

        button.setAccessible(true);

        javax.swing.JButton offered = (javax.swing.JButton) button.get(ui);

        // The railway asked directly, from this thread, which may block.
        final boolean somethingToDo = layout.triageReturnToHome() == null;

        pump();

        assertEquals(offered.isEnabled(), somethingToDo,
            "the button says " + (offered.isEnabled() ? "there is" : "there is nothing")
            + " to send home, and the railway says the opposite.  The answer is worked out on a"
            + " worker now, and this is the assertion that it is still the railway's answer");
    }

    /**
     * The third door: the diagram's right-click menu asks the same question while it is being built, on the event
     * thread (`HomeLocomotiveMenu.addReturnHomeItem`).
     */
    @Test
    public void testTheReturnHomeMenuDoesNotWaitOnTheRailway() throws Exception
    {
        whileTheRailwayIsHeld("HomeLocomotiveMenu.addReturnHomeItem", () ->
        {
            try
            {
                Class<?> menu = Class.forName("org.traincontrol.gui.HomeLocomotiveMenu");

                // The sentence as the menu hands it where Start is offered: none.
                java.lang.reflect.Method door = menu.getDeclaredMethod("addReturnHomeItem",
                    javax.swing.JComponent.class, TrainControlUI.class, String.class);

                door.setAccessible(true);

                door.invoke(null, new javax.swing.JPanel(), ui, null);
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
     * `captionIsActive` reached `Layout.isChoosableByAutonomy` once per captioned square, from three event-thread
     * paths.  The answer is now worked out on `CoveredTrackRenderer` and read off a volatile field.
     *
     * MUTATION: put the `for (Point point : ...) if (railway.isChoosableByAutonomy(point))` loop back
     * into `captionIsActive` and this times out with the event thread parked on the railway's monitor.
     */
    @Test
    public void testTheCaptionRuleDoesNotWaitOnTheRailway() throws Exception
    {
        // The setting off, or the rule short-circuits on its first line and nothing is measured.
        final java.util.prefs.Preferences prefs =
            TrainControlUI.getPrefs();

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
                    // Every square: the defect was one monitor acquisition per caption, and one lucky square proves
                    // nothing.
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
     * The constructor used to ask `Layout.getPossiblePaths` (synchronized, a whole-graph search); `showFor` now
     * gathers the answers on a worker and hands them in, so the constructor must build a menu about a square with a
     * train on it without asking the railway anything.  A train is placed first (the path section exists only for
     * a square with a locomotive), and `gatherPathOptions` is called first on this thread, as `showFor`'s worker does,
     * so a search put back inside the constructor would show.
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

        // Off the event thread, as the worker does; the square is resolved first, on the event thread, as `showFor`
        // does.
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
     * `AutonomyEditorPanel.applyWhy` asked `Layout.explainDestinations` (synchronized, a whole-graph walk per
     * candidate) straight from the click.  The editor cannot be open while autonomy runs (OB-047), but
     * `AutoLocomotiveStatus.findPaths` can hold the monitor with nothing running, as this fixture does.  The question
     * now goes to `WhyRenderer` and the answer is painted by an `invokeLater`; the capture stays on the event thread
     * because `layoutSource.get()` and `getStationIndex` may build state.
     *
     * MUTATION: call `composeWhy` directly from `applyWhy` instead of submitting it, and this times
     * out with the event thread parked on `Layout.explainDestinations`.
     */
    @Test
    public void testTheWhyToolDoesNotWaitOnTheRailway() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey occupied = squareWithATrainOnIt();

        final org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel panel = aWhyPanel();

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

        // The answer must also land: "did not block" is true of an `applyWhy` that does nothing (OB-191).
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
     * What the tool says is the railway's own answer, not a shape that happens to be there (OB-191: it painted the
     * paths and said nothing).
     *
     * The railway is the oracle, asked from this thread: a train that cannot start names the reason, and one that
     * can names the stations it may go to or says there are none.  Where the reduction finds a run to an offered
     * station, `traces` must not be empty, which a `paintWhy` that dropped the worker's map would fail.
     */
    @Test
    public void testTheWhyToolSaysWhatTheRailwaySays() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey occupied = squareWithATrainOnIt();

        final org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel panel = aWhyPanel();

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

        // Every point of the square is asked, because which copy holds the locomotive is the session's business.
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

        // The railway's own two answers, asked from this thread, where waiting for its monitor is allowed.
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

        // And the lines, where the reduction can draw one; asked of the reducer directly, since a station the running
        // graph offers need not be reachable across the diagram.
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
            org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.class.getDeclaredField("traces");

        traces.setAccessible(true);

        assertFalse(((java.util.Map<?, ?>) traces.get(panel)).isEmpty(),
            "the reduction can draw a run from " + occupied + " to a station the railway offers, and"
            + " the editor drew nothing.  The traces are built on the worker now and installed by the"
            + " paint, so an answer whose lines are dropped on the way looks exactly like this");
    }

    /**
     * The why answer lists the stations autonomy could choose first and the ones it never will after, each by page and
     * then by name with the train's own page first, and names the page of a station on another one (FR-080; MT-429).
     * The grouping is the railway's (`explainDestinationsGrouped`) and the names are the session's (`describeTile`).
     */
    @Test
    public void testTheWhyAnswerListsStationsThenBerthsWithTheirPages() throws Exception
    {
        final org.traincontrol.automationui.TileGraph.TileKey occupied = squareWithATrainOnIt();

        final org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel panel = aWhyPanel();

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

        org.traincontrol.base.Locomotive train = trainOn(occupied);

        assertNotNull(train, "no train stands on " + occupied + ", so there is no answer to read");

        assertNull(layout.explainCannotStart(train), "precondition: " + train.getName()
            + " cannot start at all, so the answer has no list of stations to put in order: " + said);

        org.traincontrol.automation.Layout.Destinations grouped = layout.explainDestinationsGrouped(train);

        // Each refused station as the answer should show it, and whether it belongs with the choosable ones (any copy).
        java.util.Map<String, Boolean> expected = new java.util.LinkedHashMap<>();

        // Each shown name's place in the order: its page (the train's own first), then the session's name for the
        // square.
        java.util.Map<String, String[]> rank = new java.util.HashMap<>();

        String elsewhere = null;

        for (java.util.Map.Entry<String, String> entry : grouped.getReasons().entrySet())
        {
            if (entry.getValue() == null) continue;

            String shown = shownAs(entry.getKey(), occupied);

            boolean couldChoose = !grouped.getBarred().contains(entry.getKey());

            expected.put(shown, Boolean.TRUE.equals(expected.get(shown)) || couldChoose);

            org.traincontrol.automationui.TileGraph.TileKey square =
                session.getStationIndex().squareOf(entry.getKey());

            rank.put(shown.replace("&", "&amp;").replace("<", "&lt;"), new String[] {
                square == null ? "2" : square.getPage().equals(occupied.getPage()) ? "0" : "1" + square.getPage(),
                square == null ? shown : session.describeTile(square) });

            if (elsewhere == null && square != null && !square.getPage().equals(occupied.getPage()))
            {
                elsewhere = shown;
            }
        }

        assertTrue(expected.containsValue(true) && expected.containsValue(false),
            "precondition: " + train.getName() + " needs refused stations of both kinds for the order to"
            + " mean anything, and has " + expected);

        assertNotNull(elsewhere, "precondition: none of the stations refused to " + train.getName()
            + " is on a page other than " + occupied.getPage() + ", so a page could never be shown");

        java.util.List<String> candidates = section(said, "autolayout.ui.whyHeaderCandidates");
        java.util.List<String> never = section(said, "autolayout.ui.whyHeaderBarred");

        assertFalse(candidates.isEmpty(),
            "the answer has no group of stations autonomy could choose - the stations and the berths are"
            + " mixed in one list. Adam, FR-080: 'show all stations first, then show berths'. It says: " + said);

        assertFalse(never.isEmpty(),
            "the answer has no group of stations autonomy will never choose. It says: " + said);

        assertTrue(said.indexOf(heading("autolayout.ui.whyHeaderCandidates"))
            < said.indexOf(heading("autolayout.ui.whyHeaderBarred")),
            "the berths are listed before the stations autonomy could choose. It says: " + said);

        assertEquals(candidates, inPageOrder(candidates, rank),
            "the stations autonomy could choose are not by page and then by name, with the train's page first."
            + "  Adam, MT-429: 'by page then station name, rather than station name only, with the current page"
            + " being first'");

        assertEquals(never, inPageOrder(never, rank),
            "the stations autonomy will never choose are not by page and then by name, with the train's page first");

        assertTrue(org.traincontrol.util.I18n.t("autosetup.ui.whyStationOnPage").contains("(Page "),
            "the page is not written with a capital P. Adam, MT-429: 'capitalize Page'");

        for (java.util.Map.Entry<String, Boolean> station : expected.entrySet())
        {
            String name = station.getKey().replace("&", "&amp;").replace("<", "&lt;");

            assertTrue((station.getValue() ? candidates : never).contains(name),
                station.getKey() + " should be listed with the stations autonomy "
                + (station.getValue() ? "could choose" : "will never choose") + ", by that name - a station"
                + " on another page carries its page, as " + elsewhere + " should.  Could choose: " + candidates
                + ".  Never: " + never);
        }
    }

    /** The locomotive standing on a square, whichever of its Points holds it. */
    private static org.traincontrol.base.Locomotive trainOn(org.traincontrol.automationui.TileGraph.TileKey square)
    {
        for (String pointName : session.getStationIndex().pointNamesAt(square))
        {
            org.traincontrol.automation.Point at = layout.getPoint(pointName);

            if (at != null && at.getCurrentLocomotive() != null) return at.getCurrentLocomotive();
        }

        return null;
    }

    /**
     * What the why answer should call a Point: the session's name for its square, plus the page when it is not the
     * train's.
     */
    private static String shownAs(String pointName, org.traincontrol.automationui.TileGraph.TileKey from)
    {
        org.traincontrol.automationui.TileGraph.TileKey square = session.getStationIndex().squareOf(pointName);

        if (square == null) return pointName;

        String name = session.describeTile(square);

        return square.getPage() != null && !square.getPage().equals(from.getPage())
            ? org.traincontrol.util.I18n.f("autosetup.ui.whyStationOnPage", name, square.getPage()) : name;
    }

    /** A heading's words up to its count, as the answer writes them, for finding it there. */
    private static String heading(String key)
    {
        String template = org.traincontrol.util.I18n.t(key);

        int count = template.indexOf("{0}");

        return (count < 0 ? template : template.substring(0, count)).replace("&", "&amp;").replace("<", "&lt;");
    }

    /** The station names under one heading of the answer, in the order shown; empty when there is no such heading. */
    private static java.util.List<String> section(String said, String key)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        boolean inside = false;

        for (String line : said.split("<br>"))
        {
            if (line.startsWith("<b>"))
            {
                inside = line.contains(heading(key));
                continue;
            }

            int colon = line.indexOf(": ");

            if (!inside || colon < 0)
            {
                inside = inside && colon >= 0;
                continue;
            }

            out.add(line.substring(0, colon));
        }

        return out;
    }

    /** A copy in the order MT-429 asks for: page (the train's own first, then by name), then station name. */
    private static java.util.List<String> inPageOrder(java.util.List<String> names,
        final java.util.Map<String, String[]> rank)
    {
        java.util.List<String> copy = new java.util.ArrayList<>(names);

        java.util.Collections.sort(copy, (a, b) ->
        {
            String[] x = rank.containsKey(a) ? rank.get(a) : new String[] { "3", a };
            String[] y = rank.containsKey(b) ? rank.get(b) : new String[] { "3", b };

            int byPage = x[0].compareTo(y[0]);

            if (byPage != 0) return byPage;

            int byName = x[1].compareTo(y[1]);

            return byName != 0 ? byName : a.compareTo(b);
        });

        return copy;
    }

    /**
     * An autonomy editor panel wired to this fixture's railway, built on the event thread with a null page (as the tile
     * menus do).
     */
    private static org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel aWhyPanel() throws Exception
    {
        final org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel[] built =
            new org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel(session, null, () -> { });

            built[0].setLayoutSource(() -> layout);
        });

        assertNotNull(built[0], "the editor panel was not built");

        return built[0];
    }

    /** What is drawn on a square, which the tool refuses to answer about unless it is a sensor. */
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

    /** The tool itself, which is private and reached from a mouse listener. */
    private static java.lang.reflect.Method theWhyTool() throws Exception
    {
        java.lang.reflect.Method why = org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.class
            .getDeclaredMethod("applyWhy", org.traincontrol.automationui.TileGraph.TileKey.class,
                org.traincontrol.base.LayoutDiagramComponent.class);

        why.setAccessible(true);

        return why;
    }

    /** Waits for every why ask this test has started to be worked out. */
    private static boolean awaitWhy(org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel panel) throws Exception
    {
        java.lang.reflect.Method waiting =
            org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.class.getDeclaredMethod("awaitWhy", long.class);

        waiting.setAccessible(true);

        return (Boolean) waiting.invoke(panel, 60000L);
    }

    /**
     * What the editor's hint line says, mark-up left in (the panel has no banner, so `say` and `sayRich` write to the
     * hint label).
     */
    private static String whatTheEditorSays(org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel panel)
        throws Exception
    {
        java.lang.reflect.Field label =
            org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.class.getDeclaredField("hint");

        label.setAccessible(true);

        String said = ((javax.swing.JLabel) label.get(panel)).getText();

        return said == null ? "" : said;
    }

    /** A station square with a locomotive standing on it, placing one if the snapshot has none there. */
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

        // None standing, so one is put down: the first destination square, the first locomotive the setup means to run.
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
     * Starts a daemon thread that takes the railway's monitor and keeps it, as a dispatch does
     * (`configureAndLockPath` is inside it while throwing ironwork).  It is released on a timer as well as on the
     * latch, so a failing test cannot leave the railway locked for the rest of the class.
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
     * Holds the railway's monitor and asserts one door of the window still answers on the event thread.
     *
     * One body for every door, because there is one rule; written out per door they would be measured slightly
     * differently.  The free cost is measured for this door first, so a red result is attributable.
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

    /** Runs one diagram refresh on the event thread and says what it cost, waiting as long as it takes. */
    private static long refreshOnTheEventThread() throws Exception
    {
        return onTheEventThread(() -> ui.updateVisiblePoints());
    }

    /** The same, given only so long. */
    private static long refreshOnTheEventThread(long patience) throws Exception
    {
        return onTheEventThread(() -> ui.updateVisiblePoints(), patience);
    }

    /** Runs anything on the event thread and says what it cost, waiting as long as it takes. */
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
     * The same, given only so long.  The wait is made from a third thread, because `invokeAndWait` has no timeout and a
     * wedged event thread would wedge this test too.
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
                // Canceled but never waited for: the event thread may still be stuck in a monitor it cannot be
                // interrupted out of; it unwinds when the helper gives the monitor back.
                refreshed.cancel(true);
            }
        }
        finally
        {
            watchdog.shutdownNow();
        }
    }

    /** Where the event thread is standing, for a failure message somebody can act on. */
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
