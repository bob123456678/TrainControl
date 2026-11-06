package ui;

import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.GraphLocAssign;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.TailCrossedPrompt;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomy.ArrivalSidePrompt;
import org.traincontrol.gui.autonomy.FacingPrompt;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Mouse;

/**
 * The track diagram's right-click menu and paste key, in a page's own window, ask over that window (OB-324): the run
 * doors (Start, Graceful Stop, Return Home), a hand send and its refusals, and the questions a placement leads to.  Each
 * claim uses the real window and the real menu, and the main window's diagram for the other route, on the frozen copy
 * of Adam's railway in a sandbox.
 */
public class testAPageWindowsMenuAsksOverIt
{
    private static final long PATIENCE_MS = 20000;

    private static final String HIS_TRAIN = "75 407 DB";

    private static final String ROUTE = "OB-324 route";

    private static final String YES = AskingWindows.YES;
    private static final String NO = AskingWindows.NO;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutPopupUI page;
    private static Locomotive train;
    private static boolean echoWas;

    /** The sides a claim let trains in by, to bar again. */
    private static final java.util.Map<TileKey, java.util.Set<Side>> barredWere = new java.util.HashMap<>();

    /** Stations on the page the windows show. */
    private static TileKey mainA;
    private static TileKey mainB;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        Components.settle();

        session = ui.getAutonomySession();

        assertNotNull(session, "no autonomy setup on this railway");

        train = model.getLocByName(HIS_TRAIN);

        assertNotNull(train, "precondition: this data has no " + HIS_TRAIN);

        mainA = named("BottomMainA");
        mainB = named("BottomMainB");

        AskingWindows.showThePageOf(ui, mainB);

        page = AskingWindows.openThePage(ui);

        TrainControlUI.setUnattended(false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            AskingWindows.closeThePage(page);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** The power on, the railway at rest, only his train standing, on the first station, and it the active one. */
    @BeforeMethod
    public void theRailwayAtRest() throws Exception
    {
        model.go();

        assertTrue(model.waitForPowerState(true, PATIENCE_MS), "precondition: the power did not come on");

        Components.onTheEventThread(() ->
        {
            for (Point point : railway().getPoints())
            {
                if (point.getCurrentLocomotive() != null) railway().moveLocomotive(null, point.getName(), true);
            }

            return null;
        });

        railway().setSimulate(true);

        Components.onTheEventThread(() ->
        {
            if (ui.getActiveLoc() != train) ui.mapLocToCurrentButton(HIS_TRAIN);

            return null;
        });

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == train, PATIENCE_MS),
            "precondition: " + HIS_TRAIN + " is not active");
    }

    @AfterMethod(alwaysRun = true)
    public void putThingsBack() throws Exception
    {
        AskingWindows.closeEveryDialog();
        AskingWindows.minimize(ui, false);

        takenUp(false);

        Components.onTheEventThread(() ->
        {
            button("startAutonomy").setEnabled(true);

            return null;
        });

        train.setTrainLength(0);
        train.setReversible(false);

        barredWere.forEach(session::setBarredArrivals);

        barredWere.clear();

        if (model.getRoute(ROUTE) != null) model.deleteRoute(ROUTE);

        Components.waitFor(() -> !busy(), PATIENCE_MS);

        Components.settle();
    }

    private static boolean busy()
    {
        return railway().isRunning() || railway().isStagingInProgress();
    }

    /** The railway taken up by a flow of the window's, as Return to Home takes it. */
    private static void takenUp(boolean taken) throws Exception
    {
        Field flow = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        flow.setAccessible(true);

        flow.setBoolean(ui, taken);

        railway().setStagingInProgress(taken);
    }

    /** His train away from its home, so that Return to Home is on offer. */
    private static void offerReturnHome() throws Exception
    {
        stands(mainB);

        Components.onTheEventThread(() ->
        {
            ui.repaintAutoLocList(true);

            return null;
        });

        assertTrue(Components.waitFor(() -> button("returnHomeButton").isEnabled(), PATIENCE_MS),
            "precondition: Return to Home is not on offer: " + button("returnHomeButton").getToolTipText());
    }

    private static Layout railway()
    {
        return model.getAutoLayout();
    }

    private static TileKey named(String name)
    {
        return AskingWindows.stationNamed(session, name);
    }

    private static AbstractButton button(String field)
    {
        try
        {
            Field found = TrainControlUI.class.getDeclaredField(field);

            found.setAccessible(true);

            return (AbstractButton) found.get(ui);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T fieldOfTheWindow(String name) throws Exception
    {
        Field found = TrainControlUI.class.getDeclaredField(name);

        found.setAccessible(true);

        @SuppressWarnings("unchecked")
        T value = (T) found.get(ui);

        return value;
    }

    private static JPopupMenu menuOn(Window window, TileKey square)
    {
        return AskingWindows.menuOn(ui, window, square);
    }

    /** His train standing on this square, on the railway and not in the setup. */
    private static Point stands(TileKey square) throws Exception
    {
        return Components.onTheEventThread(() ->
        {
            Point point = ui.getAutonomyPointForTile(square);

            assertTrue(railway().moveLocomotive(HIS_TRAIN, point.getName(), false, true),
                "precondition: " + HIS_TRAIN + " could not stand on " + square);

            ui.updateVisiblePoints();

            return ui.getAutonomyPointForTile(square);
        });
    }

    /** Chooses the item on a menu already open; the questions it asks. */
    private static List<Answering.Question> choose(JMenuItem item, Function<String, Answering.Answer> rule, int questions)
    {
        return AskingWindows.choose(item, rule, questions);
    }

    /** Start on the menu in this window, answered No; the question. */
    private static Answering.Question startAnsweredNo(Window window)
    {
        return choose(AskingWindows.itemOf(menuOn(window, mainB), I18n.t("autolayout.ui.menuStartAutonomy")),
            message -> Answering.press(NO), 1).get(0);
    }

    private static Window[] bothWindows()
    {
        return new Window[] {ui, page};
    }

    // ---------------------------------------------------------------- the run doors

    /**
     * Start on an open menu, grayed since, says why over the window the menu was in.
     *
     * MUTATION: `ui` for `ui.dialogParent()` at Start's catch in `LayoutRightclickAutonomyMenu`, or leave the menu's
     * items out of `itemsPressFromWindow`, and it hangs off the main window.
     */
    @Test
    public void testStartAfterItGrayedSaysWhyOverTheWindow() throws Exception
    {
        stands(mainA);

        for (Window window : bothWindows())
        {
            JMenuItem start = AskingWindows.itemOf(menuOn(window, mainB), I18n.t("autolayout.ui.menuStartAutonomy"));

            Components.onTheEventThread(() ->
            {
                button("startAutonomy").setEnabled(false);

                return null;
            });

            AskingWindows.assertSaid(choose(start, message -> null, 1).get(0), window, ui.whyAutonomyWillNotStart());

            Components.onTheEventThread(() ->
            {
                button("startAutonomy").setEnabled(true);

                return null;
            });
        }
    }

    /**
     * Graceful Stop once a stop has been asked says so over the window the menu was in.
     *
     * MUTATION: `ui` for `ui.dialogParent()` at Stop's catch in `LayoutRightclickAutonomyMenu`, and it hangs off the main
     * window.
     */
    @Test
    public void testAStopAlreadyAskedForIsSaidOverTheWindow() throws Exception
    {
        takenUp(true);

        Components.onTheEventThread(() ->
        {
            button("gracefulStop").setEnabled(false);

            return null;
        });

        for (Window window : bothWindows())
        {
            AskingWindows.assertSaid(choose(AskingWindows.itemOf(menuOn(window, mainB),
                I18n.t("autolayout.ui.menuStopAutonomyGracefully")), message -> null, 1).get(0), window,
                I18n.t("autolayout.errorStopRequestAlreadyIssuedWaitForTrains"));
        }
    }

    /**
     * Start with the power gone off since the menu opened says so over the window, and starts nothing.
     *
     * MUTATION: `this` for `dialogParent()` at the message in `refusedToSendATrain`, and it hangs off the main window.
     */
    @Test
    public void testStartWithThePowerOffSaysSoOverTheWindow() throws Exception
    {
        stands(mainA);

        for (Window window : bothWindows())
        {
            model.go();

            assertTrue(model.waitForPowerState(true, PATIENCE_MS), "precondition: the power did not come on");

            JMenuItem start = AskingWindows.itemOf(menuOn(window, mainB), I18n.t("autolayout.ui.menuStartAutonomy"));

            model.stop();

            assertTrue(model.waitForPowerState(false, PATIENCE_MS), "precondition: the power did not go off");

            AskingWindows.assertSaid(choose(start, message -> null, 1).get(0), window, I18n.t("autolayout.ui.powerOnToStart"));

            assertFalse(railway().isAutoRunning(), "a refused Start started");
        }
    }

    /**
     * Start in a simulation with Simulate not ticked warns over the window, starting on No; No starts nothing.
     *
     * MUTATION: `this` for `dialogParent()` in `declinedToRunUnsimulated`, and it hangs off the main window.
     */
    @Test
    public void testTheSimulateWarningAsksOverTheWindow() throws Exception
    {
        stands(mainA);

        railway().setSimulate(false);

        for (Window window : bothWindows())
        {
            AskingWindows.assertAsked(startAnsweredNo(window), window, I18n.t("ui.dialogConfirm"),
                I18n.t("autolayout.ui.warnSimulateNotTicked"), JOptionPane.WARNING_MESSAGE, NO, YES, NO);

            assertFalse(railway().isAutoRunning(), "No started the run");
        }
    }

    /**
     * Start with no train to run says so over the window, from the worker it goes on to.
     *
     * MUTATION: `this` for `stillShowing(asked)` at that message in `startAutonomyActionPerformed`, and it hangs off the
     * main window.
     */
    @Test
    public void testStartWithNoTrainSaysSoOverTheWindow() throws Exception
    {
        stands(mainA);

        List<Locomotive> toRun = new ArrayList<>(railway().getLocomotivesToRun());

        try
        {
            railway().getLocomotivesToRun().clear();

            for (Window window : bothWindows())
            {
                AskingWindows.assertSaid(choose(AskingWindows.itemOf(menuOn(window, mainB),
                    I18n.t("autolayout.ui.menuStartAutonomy")), message -> null, 1).get(0), window,
                    I18n.t("autolayout.ui.infoPleaseAddLocomotivesToGraph"));

                assertTrue(Components.waitFor(() -> button("startAutonomy").isEnabled(), PATIENCE_MS),
                    "Start was not offered again");

                assertFalse(railway().isAutoRunning(), "autonomy started with no train to run");
            }
        }
        finally
        {
            railway().getLocomotivesToRun().addAll(toRun);
        }
    }

    /**
     * The window that pressed Start closes before the worker has its say: the message goes over the main window.
     *
     * MUTATION: `asked` for `stillShowing(asked)` at that message in `startAutonomyActionPerformed`, and it hangs off a
     * window that is not there.
     */
    @Test(priority = 5)
    public void testAPageClosedBeforeTheWorkerSpeaksGivesWayToTheMainWindow() throws Exception
    {
        stands(mainA);

        List<Locomotive> toRun = new ArrayList<>(railway().getLocomotivesToRun());

        LayoutPopupUI going = AskingWindows.openThePage(ui);

        try
        {
            railway().getLocomotivesToRun().clear();

            JMenuItem start = AskingWindows.itemOf(menuOn(going, mainB), I18n.t("autolayout.ui.menuStartAutonomy"));

            try (Answering answering = Answering.start(message -> null))
            {
                SwingUtilities.invokeLater(() ->
                {
                    start.doClick(0);

                    going.setVisible(false);
                });

                AskingWindows.assertSaid(AskingWindows.onlyQuestion(answering, "nothing was said"), ui,
                    I18n.t("autolayout.ui.infoPleaseAddLocomotivesToGraph"));
            }
        }
        finally
        {
            railway().getLocomotivesToRun().addAll(toRun);

            AskingWindows.closeThePage(going);
        }
    }

    /**
     * An armed route makes Start ask over the window, from the worker; No starts nothing.
     *
     * MUTATION: `this` for `stillShowing(asked)` in `confirmOnEventThread`, and it hangs off the main window.
     */
    @Test
    public void testAnArmedRouteAsksOverTheWindow() throws Exception
    {
        stands(mainA);

        assertTrue(model.newRoute(ROUTE, new ArrayList<>(), 0,
            org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null), "precondition: no route");

        ((org.traincontrol.marklin.MarklinRoute) model.getRoute(ROUTE)).enable();

        for (Window window : bothWindows())
        {
            AskingWindows.assertAsked(startAnsweredNo(window), window, I18n.t("ui.dialogConfirm"),
                I18n.t("route.ui.confirmConditionalRoutesActiveProceed"), JOptionPane.PLAIN_MESSAGE, YES, YES, NO);

            assertTrue(Components.waitFor(() -> button("startAutonomy").isEnabled(), PATIENCE_MS),
                "Start was not offered again after No");

            assertFalse(railway().isAutoRunning(), "No started the run");
        }

        ((org.traincontrol.marklin.MarklinRoute) model.getRoute(ROUTE)).disable();
    }

    /**
     * Return to Home with autonomy busy since the menu opened says so over the window.
     *
     * MUTATION: `this.ui` for `asked` at that message in `requestReturnToHome`, and it hangs off the main window.
     */
    @Test
    public void testReturnHomeWhileBusySaysSoOverTheWindow() throws Exception
    {
        String said = null;

        for (Window window : bothWindows())
        {
            offerReturnHome();

            JMenuItem home = AskingWindows.itemOf(menuOn(window, mainB), I18n.t("autolayout.ui.menuReturnToHome"));

            takenUp(true);

            Answering.Question question = choose(home, message -> null, 1).get(0);

            takenUp(false);

            said = said == null ? question.message : said;

            AskingWindows.assertSaid(question, window, said);
        }
    }

    /**
     * Return to Home with no plan to offer, the trains gone since the menu opened, says why over the window, from the
     * worker, and moves nothing.
     *
     * MUTATION: `this.ui` for `this.ui.stillShowing(asked)` at that message in `requestReturnToHome`, and it hangs off
     * the main window.
     */
    @Test
    public void testReturnHomeWithNoPlanSaysWhyOverTheWindow() throws Exception
    {
        String said = null;

        for (Window window : bothWindows())
        {
            offerReturnHome();

            JMenuItem home = AskingWindows.itemOf(menuOn(window, mainB), I18n.t("autolayout.ui.menuReturnToHome"));

            Components.onTheEventThread(() ->
            {
                for (Point point : railway().getPoints())
                {
                    if (point.getCurrentLocomotive() != null) railway().moveLocomotive(null, point.getName(), true);
                }

                return null;
            });

            Answering.Question question = choose(home, message -> null, 1).get(0);

            said = said == null ? question.message : said;

            AskingWindows.assertSaid(question, window, said);

            assertTrue(Components.waitFor(() -> !busy() && button("startAutonomy").isEnabled(), PATIENCE_MS),
                "the refused Return to Home did not give the buttons back");
        }
    }

    // ---------------------------------------------------------------- a hand send

    /** The first destination item of his train's menu, standing on this square, and the square it goes to. */
    private static JMenuItem aDestination(JPopupMenu menu, java.util.function.Predicate<Point> wanted)
    {
        for (List<Edge> path : railway().getPossiblePaths(train, true))
        {
            Point end = path.get(path.size() - 1).getEnd();

            String text = "-> " + session.getStationIndex().describe(end);

            if (wanted.test(end) && AskingWindows.hasItem(menu, text)) return AskingWindows.itemOf(menu, text);
        }

        throw new AssertionError("precondition: his train is offered no such destination");
    }

    private static boolean turnsThere(Point end)
    {
        return session.mayTurnTiles().contains(session.getStationIndex().squareOf(end.getName()));
    }

    /**
     * A path the train is too long for, grown since the menu opened, is refused over the window, and nothing is sent.
     *
     * MUTATION: `this` for `asked` at that message in `sendATrainByHand`, and it hangs off the main window.
     */
    @Test
    public void testAPathThatCannotBeTakenIsSaidOverTheWindow() throws Exception
    {
        String said = null;

        for (Window window : bothWindows())
        {
            train.setTrainLength(0);

            stands(mainB);

            JMenuItem destination = aDestination(menuOn(window, mainB), end -> !turnsThere(end));

            train.setTrainLength(9999);

            Answering.Question question = choose(destination, message -> null, 1).get(0);

            said = said == null ? question.message : said;

            AskingWindows.assertSaid(question, window, said);

            assertTrue(railway().getActiveLocomotives().isEmpty(), "the refused train was sent");
        }
    }

    /**
     * A destination taken since the menu opened: the railway refuses at dispatch, said over the window from the send's
     * own thread.
     *
     * MUTATION: `this` for `stillShowing(asked)` at that message in `sendATrainByHand`, and it hangs off the main window.
     */
    @Test
    public void testADispatchRefusedIsSaidOverTheWindow() throws Exception
    {
        for (Window window : bothWindows())
        {
            stands(mainB);

            final Point[] end = new Point[1];

            JMenuItem destination = aDestination(menuOn(window, mainB), each ->
            {
                if (turnsThere(each)) return false;

                end[0] = each;

                return true;
            });

            Locomotive other = model.getLocByName(model.getLocList().get(0).equals(HIS_TRAIN)
                ? model.getLocList().get(1) : model.getLocList().get(0));

            assertTrue(Components.onTheEventThread(() -> railway().moveLocomotive(other.getName(), end[0].getName(), false, true)),
                "precondition: nothing could stand on the destination");

            AskingWindows.assertSaid(choose(destination, message -> null, 1).get(0), window,
                I18n.t("autolayout.ui.autoFailedCheckLog"));

            Components.onTheEventThread(() -> railway().moveLocomotive(null, end[0].getName(), true));
        }
    }

    /** The reversal question's shape over a window. */
    private static void assertTheReversal(Answering.Question question, Window over, String end)
    {
        AskingWindows.assertAsked(question, over, I18n.t("autolayout.ui.confirmManualReversalTitle"),
            I18n.f("autolayout.ui.confirmManualReversal", HIS_TRAIN, end), JOptionPane.QUESTION_MESSAGE, YES, YES, NO);
    }

    /**
     * The question whether to keep the direction asks over the window; and what comes up while it stands, a refresh or
     * an editor on its way or a route begun, refuses the send over the same window.
     *
     * MUTATION: `parent` for the `dialogParent()` line in `ManualReversalPrompt.ask`, or `this` for `over` in
     * `refusedForAnEditorOnItsWay`, and it hangs off the main window.
     */
    @Test
    public void testTheReversalQuestionAndWhatFollowsItAreOverTheWindow() throws Exception
    {
        TileKey post = named("BottomMainPost");

        train.setReversible(true);

        java.util.concurrent.atomic.AtomicInteger refreshes = fieldOfTheWindow("refreshesUnderWay");

        Field onItsWay = TrainControlUI.class.getDeclaredField("editorOnItsWay");

        onItsWay.setAccessible(true);

        for (Window window : bothWindows())
        {
            stands(post);

            final Point[] end = new Point[1];

            JMenuItem destination = aDestination(menuOn(window, post), each ->
            {
                if (!turnsThere(each)) return false;

                end[0] = each;

                return true;
            });

            String refreshing = I18n.t("autosetup.ui.errorDiagramRefreshing");

            List<Answering.Question> asked = choose(destination, message ->
            {
                if (message.equals(refreshing)) return null;

                refreshes.incrementAndGet();

                return Answering.press(YES);
            }, 2);

            refreshes.decrementAndGet();

            assertTheReversal(asked.get(0), window, end[0].getName());

            AskingWindows.assertSaid(asked.get(1), window, refreshing);

            assertTrue(railway().getActiveLocomotives().isEmpty(), "a refused send was sent");

            String editor = I18n.t("autosetup.ui.menuEditorOpen");

            destination = aDestination(menuOn(window, post), TURNS_THERE);

            asked = choose(destination, message ->
            {
                if (message.equals(editor)) return null;

                try
                {
                    onItsWay.setBoolean(ui, true);
                }
                catch (IllegalAccessException e)
                {
                    throw new IllegalStateException(e);
                }

                return Answering.press(YES);
            }, 2);

            onItsWay.setBoolean(ui, false);

            AskingWindows.assertSaid(asked.get(1), window, editor);

            String routeRuns = TrainControlUI.routeRunningRefusal(java.util.Collections.singletonList(ROUTE));

            assertTrue(model.newRoute(ROUTE, new ArrayList<>(), 0,
                org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null), "precondition: no route");

            asked = choose(aDestination(menuOn(window, post), TURNS_THERE), message ->
            {
                if (message.equals(routeRuns)) return null;

                model.getRoute(ROUTE).setExecuting();

                return Answering.press(YES);
            }, 2);

            model.getRoute(ROUTE).stopExecuting();

            model.deleteRoute(ROUTE);

            AskingWindows.assertSaid(asked.get(1), window, routeRuns);
        }
    }

    private static final java.util.function.Predicate<Point> TURNS_THERE =
        testAPageWindowsMenuAsksOverIt::turnsThere;

    /**
     * Yes over the page's window sends the train.
     *
     * MUTATION: `parent` for the `dialogParent()` line in `ManualReversalPrompt.ask`, and it hangs off the main window.
     */
    @Test(priority = 6)
    public void testYesOverThePageWindowSendsTheTrain() throws Exception
    {
        TileKey post = named("BottomMainPost");

        train.setReversible(true);

        stands(post);

        JMenuItem destination = aDestination(menuOn(page, post), TURNS_THERE);

        assertSame(choose(destination, message -> Answering.press(YES), 1).get(0).owner, page,
            "the question is not the page's");

        assertTrue(Components.waitFor(() -> !railway().getActiveLocomotives().isEmpty()
            || railway().getLocomotiveLocation(train) != ui.getAutonomyPointForTile(post), PATIENCE_MS),
            "Yes sent nothing");

        assertTrue(Components.waitFor(() -> railway().getActiveLocomotives().isEmpty(), 120000),
            "precondition: the train sent did not arrive");
    }

    // ---------------------------------------------------------------- what a placement asks

    /** A station that asks which side a train put down there arrived from. */
    private static TileKey aStationThatAsksTheSide()
    {
        for (TileKey square : session.mayTurnTiles())
        {
            Point point = ui.getAutonomyPointForTile(square);

            if (!square.getPage().equals(mainB.getPage()) || point == null || !point.isDestination()) continue;

            barredWere.putIfAbsent(square, new java.util.HashSet<>(session.getBarredArrivals(square)));

            session.setBarredArrivals(square, java.util.Collections.emptySet());

            if (ArrivalSidePrompt.wouldAsk(railway(), point, true, session.unbarredArrivalSides(square))) return square;
        }

        throw new AssertionError("precondition: no station on this page asks which side a train arrived from");
    }

    private static List<String> sidesOffered(TileKey square)
    {
        List<String> labels = new ArrayList<>();

        Point point = ui.getAutonomyPointForTile(square);

        for (String side : ArrivalSidePrompt.choicesFor(railway(), point, session.unbarredArrivalSides(square)))
        {
            labels.add(ArrivalSidePrompt.labelFor(side));
        }

        return labels;
    }

    private static String[] asArray(List<String> list)
    {
        return list.toArray(new String[0]);
    }

    /**
     * Place asks which side the train arrived from over the window it was chosen in; closing the question places nothing.
     *
     * MUTATION: `ui` for `ui.dialogParent()` at `forPlacement` in `placeFacing`, and it hangs off the main window.
     */
    @Test
    public void testPlaceAsksTheSideOverTheWindow() throws Exception
    {
        TileKey square = aStationThatAsksTheSide();

        List<String> sides = sidesOffered(square);

        for (Window window : bothWindows())
        {
            try (Answering answering = Answering.start(message -> Answering.press(sides.get(0))))
            {
                Mouse.choose(AskingWindows.itemOf(menuOn(window, square), I18n.f("layout.ui.menuPlaceLocomotive", HIS_TRAIN)));

                assertTrue(answering.awaitAsked(1, PATIENCE_MS), "Place asked nothing");

                AskingWindows.assertAsked(answering.questions().get(0), window, I18n.t("autolayout.ui.askArrivalSideTitle"),
                    answering.questions().get(0).message, JOptionPane.QUESTION_MESSAGE, sides.get(0), asArray(sides));
            }

            assertTrue(Components.waitFor(() -> railway().getLocomotiveLocation(train) != null, PATIENCE_MS),
                "the answer placed nothing");

            takeHimOff();
        }
    }

    /** Points the pointer at the square in this window, and presses Control+V there. */
    private static void paste(Window window, TileKey square) throws Exception
    {
        Mouse.enter(AskingWindows.awaitTileIn(ui, window, square));

        final java.awt.Component on = window == ui ? fieldOfTheWindow("KeyboardTab") : window;

        AskingWindows.pressKey(on, KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK);
    }

    /**
     * Control+V over a square that is no station says so over the window it was pressed in.
     *
     * MUTATION: `ui` for `ui.dialogParent()` at that message in `locomotiveGestureOnDiagram`, and it hangs off the main
     * window.
     */
    @Test
    public void testPastingOffAStationIsSaidOverTheWindow() throws Exception
    {
        TileKey track = null;

        for (LayoutDiagramComponent each : model.getLayout(mainB.getPage()).getAll())
        {
            TileKey here = new TileKey(mainB.getPage(), each.getX(), each.getY());

            if (each.getType() == LayoutDiagramComponent.componentType.STRAIGHT && !session.getStore().isStation(here)
                && ui.getAutonomyPointForTile(here) != null) track = here;

            if (each.isFeedback() && !session.getStore().isStation(here) && ui.getAutonomyPointForTile(here) != null) track = here;
        }

        assertNotNull(track, "precondition: no square that autonomy knows and is no station");

        for (Window window : bothWindows())
        {
            try (Answering answering = Answering.start(message -> null))
            {
                paste(window, track);

                AskingWindows.assertSaid(AskingWindows.onlyQuestion(answering, "Control+V said nothing"), window,
                    I18n.t("autolayout.ui.errorTrainOnlyAtStation"));
            }

            Components.settle();
        }
    }

    /**
     * Control+V on a station trains may turn at asks the side and then the facing, both over the window it was pressed
     * in; dismissing the facing leaves the train off the square.
     *
     * MUTATION: `ui` for `ui.dialogParent()` at either prompt in `locomotiveGestureOnDiagram`, and it hangs off the main
     * window.
     */
    @Test
    public void testPasteAsksTheSideAndTheFacingOverTheWindow() throws Exception
    {
        TileKey square = aStationThatAsksTheSide();

        List<String> sides = sidesOffered(square);

        List<String> facings = new ArrayList<>();

        for (Side each : session.facingsFor(square).values())
        {
            if (!facings.contains(FacingPrompt.labelFor(each))) facings.add(FacingPrompt.labelFor(each));
        }

        for (Window window : bothWindows())
        {
            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                paste(window, square);

                assertTrue(answering.awaitAsked(2, PATIENCE_MS), "Control+V asked " + answering.asked());

                Answering.Question side = answering.questions().get(0);
                Answering.Question facing = answering.questions().get(1);

                AskingWindows.assertAsked(side, window, I18n.t("autolayout.ui.askArrivalSideTitle"), side.message,
                    JOptionPane.QUESTION_MESSAGE, sides.get(0), asArray(sides));

                assertSame(facing.owner, window, "the facing question hangs off the wrong window");
                assertEquals(facing.title, I18n.t("autolayout.ui.askFacingTitle"), "the facing question's title changed");
                assertEquals(facing.type, JOptionPane.QUESTION_MESSAGE, "the facing question's type changed");
                assertEquals(new java.util.HashSet<>(facing.buttons), new java.util.HashSet<>(facings),
                    "the facing question's buttons changed");
            }

            assertTrue(Components.waitFor(() -> railway().getLocomotiveLocation(train) != null, PATIENCE_MS),
                "the answers placed nothing");

            takeHimOff();

            Components.settle();
        }
    }

    // ---------------------------------------------------------------- the tail question, on the diagram

    /** The small window of a tail question waiting on the diagram. */
    private static JDialog awaitThePick()
    {
        final String title = I18n.t("autolayout.ui.askArrivalSideTitle");

        final JDialog[] found = new JDialog[1];

        assertTrue(Components.waitFor(() ->
        {
            for (Window window : Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing() && !((JDialog) window).isModal()
                    && title.equals(((JDialog) window).getTitle())) found[0] = (JDialog) window;
            }

            return found[0] != null;
        }, PATIENCE_MS), "the tail question did not go on the diagram");

        return found[0];
    }

    /** Places his train, five long, on the square from the south, from this window: a junction lies under its tail. */
    private static JDialog placeOnTunnel(Window window, TileKey tunnel)
    {
        train.setTrainLength(5);

        JMenuItem place = AskingWindows.itemOf(menuOn(window, tunnel), I18n.f("layout.ui.menuPlaceLocomotive", HIS_TRAIN));

        try (Answering answering = Answering.start(message -> Answering.press(ArrivalSidePrompt.labelFor("S"))))
        {
            Mouse.choose(place);

            return awaitThePick();
        }
    }

    /** A claim made on a square, from a page window of its page. */
    private interface OnTunnel
    {
        void run(TileKey tunnel, LayoutPopupUI there) throws Exception;
    }

    /** With a page window of the square's page, which is closed and the page before put back afterward. */
    private static void onTunnelsPage(OnTunnel claim) throws Exception
    {
        TileKey tunnel = named("BottomMainPost");

        AskingWindows.showThePageOf(ui, tunnel);

        LayoutPopupUI there = AskingWindows.openThePage(ui);

        try
        {
            claim.run(tunnel, there);
        }
        finally
        {
            AskingWindows.closeEveryDialog();
            AskingWindows.closeThePage(there);
            AskingWindows.showThePageOf(ui, mainB);
        }
    }

    private static void waitUntilGone(JDialog pick, String why)
    {
        assertTrue(Components.waitFor(() -> !pick.isShowing(), PATIENCE_MS), why);

        Components.settle();
    }

    private static void cancelThe(JDialog pick)
    {
        Components.press(Components.withText(pick, I18n.t("ui.cancel")));

        waitUntilGone(pick, "Cancel did not end the tail question");
    }

    private static void takeHimOff() throws Exception
    {
        Components.onTheEventThread(() ->
        {
            railway().moveLocomotive(null, railway().getLocomotiveLocation(train).getName(), true);

            return null;
        });
    }

    /**
     * The tail question on the diagram hangs off the window of the door, at its top, and Cancel leaves the train placed.
     *
     * MUTATION: `ui` for `over` at the small window in `DiagramPick.arm`, and it hangs off the main window.
     */
    @Test
    public void testTheTailQuestionOnTheDiagramIsOverTheWindow() throws Exception
    {
        onTunnelsPage((tunnel, there) ->
        {
            for (Window window : new Window[] {ui, there})
            {
                JDialog pick = placeOnTunnel(window, tunnel);

                assertSame(pick.getOwner(), window, "the tail question hangs off the wrong window");

                assertEquals(pick.getY(), window.getY() + 60, "the tail question is not at the top of its window");

                cancelThe(pick);

                assertNotNull(railway().getLocomotiveLocation(train), "Cancel took the train off");

                takeHimOff();
            }
        });
    }

    /**
     * With a sensor on another page, the tail question is the list, over the window of the door; Cancel leaves the train
     * placed.
     *
     * MUTATION: `parent` for `dialogParent()` at `over` in `askAfterPlacement`, and it hangs off the main window.
     */
    @Test
    public void testTheTailQuestionAsAListIsOverTheWindow() throws Exception
    {
        onTunnelsPage((tunnel, there) ->
        {
            train.setTrainLength(30);

            for (Window window : new Window[] {ui, there})
            {
                Answering.Question list = choose(AskingWindows.itemOf(menuOn(window, tunnel),
                    I18n.f("layout.ui.menuPlaceLocomotive", HIS_TRAIN)), message -> Answering.pressAt(1), 1).get(0);

                AskingWindows.assertAsked(list, window, I18n.t("autolayout.ui.askArrivalSideTitle"), AskingWindows.ANY_MESSAGE,
                    JOptionPane.QUESTION_MESSAGE, I18n.t("ui.ok"), I18n.t("ui.ok"), I18n.t("ui.cancel"));

                assertNotNull(railway().getLocomotiveLocation(train), "Cancel took the train off");

                takeHimOff();
            }
        });
    }

    private static void assertTheTidyingIsReportedOver(Answering answering, Window over)
    {
        assertTrue(Components.waitFor(() -> answering.questions().stream()
            .anyMatch(each -> I18n.t("autosetup.ui.titleSetupTidied").equals(each.title)), PATIENCE_MS),
            "no report of what the save tidied: " + answering.asked());

        for (Answering.Question each : answering.questions())
        {
            assertSame(each.owner, over, "\"" + each.message + "\" hangs off the wrong window");
        }
    }

    /**
     * What the save before the tail question tidied is reported after it, over the window of the door: the wait in between
     * hands the window back.
     *
     * MUTATION: `askedFrom = null` for `askedFrom = before` in `pressFromWindow`, and it hangs off the main window.
     */
    @Test
    public void testAReportAfterTheTailQuestionIsOverTheWindow() throws Exception
    {
        onTunnelsPage((tunnel, there) ->
        {
            session.setTileLength(new TileKey(tunnel.getPage(), 900, 900), 5);

            JDialog pick = placeOnTunnel(there, tunnel);

            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                cancelThe(pick);

                assertTheTidyingIsReportedOver(answering, there);
            }

            takeHimOff();
        });
    }

    /**
     * A click on a lit sensor in the page's window answers it, and the train has that road.
     *
     * MUTATION: `ui` for `over` at the small window in `DiagramPick.arm`, and it hangs off the main window.
     */
    @Test
    public void testAClickInThePageAnswersTheTailQuestion() throws Exception
    {
        onTunnelsPage((tunnel, there) ->
        {
            JDialog pick = placeOnTunnel(there, tunnel);

            assertSame(pick.getOwner(), there, "the tail question hangs off the wrong window");

            Point at = railway().getLocomotiveLocation(train);

            TailCrossedPrompt.Choice pre = null;

            for (TailCrossedPrompt.Choice each : TailCrossedPrompt.choicesFor(railway(), at, "S", 5, null))
            {
                if (each.isReached()) pre = each;
            }

            assertNotNull(pre, "precondition: no sensor is offered");

            Mouse.click(AskingWindows.awaitTileIn(ui, there, session.getStationIndex().squareOf(pre.getFarthest())));

            waitUntilGone(pick, "the click did not answer the tail question");

            assertEquals(railway().getLocomotiveLocation(train).getArrivedAlong(), pre.getRoad(),
                "the train does not have the road clicked");
        });
    }

    /**
     * With the main window minimized the tail question still shows, over the page.
     *
     * MUTATION: `ui` for `over` at the small window in `DiagramPick.arm`, and it is hidden with the main window.
     */
    @Test
    public void testWithTheMainWindowMinimizedTheTailQuestionShowsOverThePage() throws Exception
    {
        onTunnelsPage((tunnel, there) ->
        {
            AskingWindows.minimize(ui, true);

            JDialog pick = placeOnTunnel(there, tunnel);

            assertSame(pick.getOwner(), there, "the tail question hangs off the wrong window");
            assertTrue(pick.getX() > -10000, "the tail question stands off the screen, by the minimized main window");

            cancelThe(pick);
        });
    }

    /**
     * The window that closes under the question takes it with it, as no answer: the door ends, the train stays placed,
     * and the next placement can ask.
     *
     * MUTATION: drop the `componentHidden` listener in `DiagramPick.arm`, and the door waits for an answer that cannot
     * come.
     */
    @Test
    public void testAPageThatClosesEndsTheTailQuestion() throws Exception
    {
        onTunnelsPage((tunnel, there) ->
        {
            JDialog pick = placeOnTunnel(there, tunnel);

            SwingUtilities.invokeLater(() -> there.setVisible(false));

            waitUntilGone(pick, "the tail question outlived its window");

            assertNotNull(railway().getLocomotiveLocation(train), "the train did not stay placed");

            takeHimOff();

            JDialog next = placeOnTunnel(ui, tunnel);

            assertSame(next.getOwner(), ui, "the next question hangs off the wrong window");

            cancelThe(next);
        });
    }

    /**
     * While the question waits no door is asking: Graceful Stop's item on the main window's menu says its message over
     * the main window, though the door that waits is the page's.
     *
     * MUTATION: `loop.enter()` without `pressFromWindow(null, ...)` in `DiagramPick.ask`, and it hangs off the page.
     */
    @Test
    public void testADoorOfTheMainWindowWhileItWaitsAsksOverTheMainWindow() throws Exception
    {
        onTunnelsPage((tunnel, there) ->
        {
            JDialog pick = placeOnTunnel(there, tunnel);

            takenUp(true);

            Components.onTheEventThread(() ->
            {
                button("gracefulStop").setEnabled(false);

                return null;
            });

            try (Answering answering = Answering.start(message -> null))
            {
                Mouse.choose(AskingWindows.itemOf(menuOn(ui, tunnel), I18n.t("autolayout.ui.menuStopAutonomyGracefully")));

                AskingWindows.assertSaid(AskingWindows.onlyQuestion(answering, "the stop said nothing"), ui,
                    I18n.t("autolayout.errorStopRequestAlreadyIssuedWaitForTrains"));
            }

            takenUp(false);

            cancelThe(pick);
        });
    }

    // ---------------------------------------------------------------- Edit Locomotive

    /** The Edit form's item on a station where his train stands. */
    private static JMenuItem editItem(Window window, TileKey square, Point standing)
    {
        return AskingWindows.itemOf(menuOn(window, square), GraphLocAssign.menuLabelFor(standing));
    }

    /**
     * Edit Locomotive asks over the window; Cancel writes nothing and OK writes the train into the setup.
     *
     * MUTATION: `ui` for `ui.dialogParent()` at the form in `LayoutRightclickAutonomyMenu`, and it hangs off the main
     * window.
     */
    @Test
    public void testEditLocomotiveAsksOverTheWindow() throws Exception
    {
        Point standing = stands(mainB);

        String title = I18n.f("autolayout.ui.dialogEditOrAssignLocomotive", session.getStationIndex().describe(standing));

        String wasInTheSetup = session.getLocomotiveNameAt(mainB);

        String said = null;

        for (Window window : bothWindows())
        {
            Answering.Question question = choose(editItem(window, mainB, standing), message -> Answering.pressAt(1), 1).get(0);

            said = said == null ? question.message : said;

            AskingWindows.assertAsked(question, window, title, said, JOptionPane.PLAIN_MESSAGE,
                String.valueOf(TrainControlUI.OK_CANCEL_OPTS[0]), String.valueOf(TrainControlUI.OK_CANCEL_OPTS[0]),
                String.valueOf(TrainControlUI.OK_CANCEL_OPTS[1]));

            assertEquals(session.getLocomotiveNameAt(mainB), wasInTheSetup, "Cancel wrote the setup");
        }

        choose(editItem(page, mainB, standing), message -> Answering.pressAt(0), 1);

        assertTrue(Components.waitFor(() -> HIS_TRAIN.equals(session.getLocomotiveNameAt(mainB)), PATIENCE_MS),
            "OK over the page's window did not write the setup");
    }

    /**
     * A run begun since the menu opened refuses the form over the window.
     *
     * MUTATION: in `RunGates.over`, leave out `source == ui`, and it hangs off the main window.
     */
    @Test
    public void testARunBegunSinceTheMenuOpenedIsSaidOverTheWindow() throws Exception
    {
        Point standing = stands(mainB);

        for (Window window : bothWindows())
        {
            JMenuItem edit = editItem(window, mainB, standing);

            railway().setStagingInProgress(true);

            AskingWindows.assertSaid(choose(edit, message -> null, 1).get(0), window,
                I18n.t("ui.main.errorNoEditsWhileRunning"));

            railway().setStagingInProgress(false);
        }
    }

    /**
     * With the main window minimized, the form still shows, over the page.
     *
     * MUTATION: `ui` for `ui.dialogParent()` at the form in `LayoutRightclickAutonomyMenu`, and it is hidden with the
     * main window.
     */
    @Test
    public void testWithTheMainWindowMinimizedTheFormShowsOverThePage() throws Exception
    {
        Point standing = stands(mainB);

        JMenuItem edit = editItem(page, mainB, standing);

        AskingWindows.minimize(ui, true);

        assertSame(choose(edit, message -> Answering.pressAt(1), 1).get(0).owner, page, "the form is not the page's");
    }

    // ---------------------------------------------------------------- the setup report

    /**
     * A placement's save that tidies a setting on a square that has gone reports it over the window of the door.
     *
     * MUTATION: `ui` for `ui.dialogParent()` at `AutonomyReport.show` in `placeFacing`, and it hangs off the main window.
     */
    @Test
    public void testTheSetupReportIsOverTheWindow() throws Exception
    {
        for (Window window : bothWindows())
        {
            session.setTileLength(new TileKey(mainB.getPage(), 900, 900), 5);

            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                Mouse.choose(AskingWindows.itemOf(menuOn(window, mainA), I18n.f("layout.ui.menuPlaceLocomotive", HIS_TRAIN)));

                assertTheTidyingIsReportedOver(answering, window);
            }

            Components.settle();

            takeHimOff();
        }
    }
}
