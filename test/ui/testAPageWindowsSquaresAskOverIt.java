package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Mouse;

/**
 * A left click on a turnout or signal asks its questions over the window it was clicked in (OB-324): real clicks on a
 * real page window on the single-switch railway, and on the main window's own diagram for the other route.  With the
 * main window minimized (the original symptom) the question still shows, and a window that has closed or gone to the
 * taskbar gives way to the main window.
 */
public class testAPageWindowsSquaresAskOverIt
{
    private static final long PATIENCE_MS = 15000;

    private static final String PAGE = "1 - Junction";

    private static final TileKey TURNOUT = new TileKey(PAGE, 5, 3);

    private static final int ROUTE_ID = 900;

    private static final TileKey SIGNAL = new TileKey(PAGE, 8, 2);
    private static final TileKey ROUTE_TILE = new TileKey(PAGE, 3, 1);

    private static final String ROUTE = "OB-324 route";

    private static final String PLATFORM = "MainPlatform";

    private static LayoutPopupUI page;
    private static boolean echoWas;
    private static TrainControlUI ui;
    private static MarklinControlStation model;
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // A signal past the branch's buffer stop and a route tile, in the sandbox's copy only: the scenario has neither
        AskingWindows.Railway railway = AskingWindows.openRailway("single-switch", folder ->
        {
            try
            {
                Files.write(new File(folder, "config/gleisbilder/" + PAGE + ".cs2").toPath(),
                    ("element\n .id=0x10208\n .typ=signal\n .artikel=40\n"
                        + "element\n .id=0x10103\n .typ=fahrstrasse\n .artikel=" + ROUTE_ID + "\n")
                        .getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);
            }
            catch (java.io.IOException cannotWrite)
            {
                throw new IllegalStateException(cannotWrite);
            }
        });

        sandbox = railway.sandbox;
        model = railway.model;
        ui = railway.ui;

        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        assertTrue(Components.waitFor(() -> AskingWindows.tileIn(ui, ui, TURNOUT) != null, PATIENCE_MS),
            "the main window's diagram does not show the turnout");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            if (model != null && model.getRoute(ROUTE) != null) model.deleteRoute(ROUTE);

            if (model != null) model.clearAutoLayout();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @BeforeMethod
    public void openThePage() throws Exception
    {
        page = AskingWindows.openThePage(ui);

        AskingWindows.awaitTileIn(ui, page, TURNOUT);
        AskingWindows.awaitTileIn(ui, page, SIGNAL);
    }

    /** Power on, autonomy stopped, no train at the platform, nothing left open, the main window back up. */
    @AfterMethod(alwaysRun = true)
    public void putTheRailwayBack() throws Exception
    {
        AskingWindows.closeEveryDialog();
        AskingWindows.minimize(ui, false);
        AskingWindows.closeThePage(page);

        Layout layout = model.getAutoLayoutIfLoaded();

        if (layout != null)
        {
            layout.stopLocomotives();

            Point platform = layout.getPoint(PLATFORM);

            if (platform != null) platform.setLocomotive(null);
        }

        model.go();

        assertTrue(model.waitForPowerState(true, PATIENCE_MS), "the power did not come back on");
    }

    private static void powerOff() throws Exception
    {
        model.stop();

        assertTrue(model.waitForPowerState(false, PATIENCE_MS), "the power did not go off");
    }

    private static void assertIsThePowerQuestion(Answering.Question question, java.awt.Window over)
    {
        String first = I18n.t("layout.ui.optionTurnPowerOnAndProceed");

        AskingWindows.assertAsked(question, over, I18n.t("layout.ui.dialogPleaseConfirm"),
            I18n.t("layout.ui.confirmAccessorySwitchPowerOff"), JOptionPane.QUESTION_MESSAGE, first, first,
            I18n.t("layout.ui.optionProceed"), I18n.t("ui.cancel"));
    }

    private static Accessory turnoutWithThePowerOff() throws Exception
    {
        powerOff();

        return AskingWindows.tileIn(ui, page, TURNOUT).getComponent().getAccessory();
    }

    /** Clicks the square, answers with this button, and reads the question against the power question's shape. */
    private static void assertThePowerQuestion(LayoutLabel square, java.awt.Window over, String answer) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(answer)))
        {
            Mouse.click(square);

            assertIsThePowerQuestion(AskingWindows.onlyQuestion(answering, "a click with the power off asked nothing"), over);
        }

        Components.settle();
    }

    /**
     * The power question hangs off the page's window, says what it always has, and Cancel throws nothing.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at the power question in `LayoutLabel`, and it hangs off the main
     * window.
     */
    @Test
    public void testThePowerQuestionHangsOffThePageWindow() throws Exception
    {
        boolean was = turnoutWithThePowerOff().isSwitched();

        assertThePowerQuestion(AskingWindows.tileIn(ui, page, TURNOUT), page, I18n.t("ui.cancel"));

        Components.settle();

        assertEquals(turnoutWithThePowerOff().isSwitched(), was, "Cancel threw the turnout");
        assertFalse(model.getPowerState(), "Cancel turned the power on");
    }

    /**
     * Proceed, answered over the page's window, throws the turnout and leaves the power off.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at the power question in `LayoutLabel`, and it hangs off the main
     * window.
     */
    @Test
    public void testProceedOverThePageWindowThrowsTheTurnout() throws Exception
    {
        Accessory turnout = turnoutWithThePowerOff();

        boolean was = turnout.isSwitched();

        assertThePowerQuestion(AskingWindows.tileIn(ui, page, TURNOUT), page, I18n.t("layout.ui.optionProceed"));

        assertTrue(Components.waitFor(() -> turnout.isSwitched() != was, PATIENCE_MS), "Proceed did not throw it");

        assertFalse(model.getPowerState(), "Proceed turned the power on");
    }

    /**
     * The same click on the main window's own diagram still hangs off the main window, whichever window is open.
     *
     * MUTATION: in `LayoutLabel`, the page window whatever window the tile is in (`ui`'s popups' first), and the main
     * window's question hangs off the page.
     */
    @Test
    public void testThePowerQuestionFromTheMainWindowHangsOffTheMainWindow() throws Exception
    {
        powerOff();

        assertThePowerQuestion(AskingWindows.awaitTileIn(ui, ui, TURNOUT), ui, I18n.t("ui.cancel"));
    }

    /**
     * The original symptom: with the main window minimized the question still shows, over the page's window.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at the power question in `LayoutLabel`, and it is hidden with the
     * main window.
     */
    @Test
    public void testWithTheMainWindowMinimizedTheQuestionShowsOverThePage() throws Exception
    {
        powerOff();

        AskingWindows.minimize(ui, true);

        assertThePowerQuestion(AskingWindows.tileIn(ui, page, TURNOUT), page, I18n.t("ui.cancel"));
    }

    /**
     * Page windows follow the main window's always-on-top, and so does the question over one.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at the power question in `LayoutLabel`, and it hangs off the main
     * window.
     */
    @Test
    public void testAQuestionOverAnAlwaysOnTopPageStaysOnTop() throws Exception
    {
        powerOff();

        Components.onTheEventThread(() ->
        {
            ui.setAlwaysOnTop(true);

            return null;
        });

        try
        {
            assertTrue(Components.waitFor(page::isAlwaysOnTop, PATIENCE_MS), "the page did not follow the main window");

            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
            {
                Mouse.click(AskingWindows.tileIn(ui, page, TURNOUT));

                Answering.Question question = AskingWindows.onlyQuestion(answering, "nothing was asked");

                assertSame(question.owner, page, "the question is not the page's");
                assertTrue(question.alwaysOnTop, "the question is under an always-on-top page");
            }
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                ui.setAlwaysOnTop(false);

                return null;
            });
        }
    }

    /**
     * A page window that closes under its open question takes it away: nothing is stranded, nothing thrown, and it
     * counts as Cancel.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at the power question in `LayoutLabel`, and the question outlives
     * the window, over the main window.
     */
    @Test
    public void testAPageThatClosesTakesItsQuestionWithIt() throws Exception
    {
        powerOff();

        Accessory turnout = AskingWindows.tileIn(ui, page, TURNOUT).getComponent().getAccessory();

        boolean was = turnout.isSwitched();

        final Thread.UncaughtExceptionHandler handlerWas = Thread.getDefaultUncaughtExceptionHandler();

        final java.util.List<Throwable> thrown = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> thrown.add(failure));

        try
        {
            Mouse.click(AskingWindows.tileIn(ui, page, TURNOUT));

            AskingWindows.assertClosingTakesTheQuestion(page);

            Components.settle();
        }
        finally
        {
            Thread.setDefaultUncaughtExceptionHandler(handlerWas);
        }

        assertEquals(thrown, java.util.Collections.emptyList(), "closing the window threw");
        assertEquals(turnout.isSwitched(), was, "the closed question threw the turnout");
    }

    /**
     * A page that has closed by the time the question is put gives way to the main window, never a hidden owner.
     *
     * MUTATION: `asked` for `tcUI.stillShowing(asked)` at the power question in `LayoutLabel`, and the question hangs
     * off a window that is not there.
     */
    @Test
    public void testAPageGoneBeforeTheQuestionGivesWayToTheMainWindow() throws Exception
    {
        powerOff();

        final LayoutLabel square = AskingWindows.tileIn(ui, page, TURNOUT);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            // The click posts its question; the window goes in the same breath, before it is put
            SwingUtilities.invokeLater(() ->
            {
                java.awt.Point screen = new java.awt.Point(square.getWidth() / 2, square.getHeight() / 2);

                square.dispatchEvent(new java.awt.event.MouseEvent(square, java.awt.event.MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0, screen.x, screen.y, 1, false, java.awt.event.MouseEvent.BUTTON1));

                page.setVisible(false);
            });

            assertIsThePowerQuestion(AskingWindows.onlyQuestion(answering, "nothing was asked"), ui);
        }
    }

    /**
     * Nor a page in the taskbar, which would hide what it owns.
     *
     * MUTATION: drop the `ICONIFIED` half of `TrainControlUI.stillShowing`, and the question hangs off the minimized page.
     */
    @Test
    public void testAMinimizedPageGivesWayToTheMainWindow() throws Exception
    {
        powerOff();

        final LayoutLabel square = AskingWindows.tileIn(ui, page, TURNOUT);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            clickThen(square, () -> page.setExtendedState(page.getExtendedState() | java.awt.Frame.ICONIFIED));

            Answering.Question question = AskingWindows.onlyQuestion(answering, "nothing was asked");

            assertSame(question.owner, ui, "the question hangs off a minimized page, which hides it");
        }
    }

    /** A signal protecting a platform a train stands at, with autonomy running and nobody to run. */
    private static Accessory protectingTheOccupiedPlatform() throws Exception
    {
        Accessory signal = AskingWindows.tileIn(ui, page, SIGNAL).getComponent().getAccessory();

        assertNotNull(signal, "the added signal has no accessory");

        Layout layout = model.getAutoLayout();

        Point platform = layout.getPoint(PLATFORM);

        assertNotNull(platform, "there is no " + PLATFORM + " in the loaded configuration");

        platform.setProtectingSignal(signal.getName());

        platform.setLocomotive(model.getLocByName(model.getLocList().get(0)));

        signal.setSwitched(true);

        layout.runLocomotives();

        assertTrue(model.isAutonomyRunning(), "precondition: autonomy is running");

        return signal;
    }

    /** Clicks the signal in this window and reads the protection question; answers with this button. */
    private static void assertTheProtectionQuestion(java.awt.Window over, String answer) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(answer)))
        {
            Mouse.click(AskingWindows.tileIn(ui, over, SIGNAL));

            AskingWindows.assertAsked(AskingWindows.onlyQuestion(answering, "nothing was asked before clearing it"), over,
                I18n.t("layout.ui.dialogPleaseConfirm"), I18n.t("layout.ui.confirmAccessoryProtecting"),
                JOptionPane.QUESTION_MESSAGE, I18n.t("ui.ok"), I18n.t("ui.ok"), I18n.t("ui.cancel"));
        }

        Components.settle();
    }

    /**
     * The question about a protecting signal hangs off the page's window, and Cancel leaves it red.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at the protection question in `LayoutLabel`, and it hangs off the
     * main window.
     */
    @Test
    public void testTheProtectionQuestionHangsOffThePageWindow() throws Exception
    {
        Accessory signal = protectingTheOccupiedPlatform();

        assertTheProtectionQuestion(page, I18n.t("ui.cancel"));

        Components.settle();

        assertTrue(signal.isSwitched(), "Cancel set the protecting signal green");
    }

    /**
     * And OK over it sets the signal green.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at the protection question in `LayoutLabel`, and it hangs off the
     * main window.
     */
    @Test
    public void testOKOverThePageWindowSetsTheSignalGreen() throws Exception
    {
        Accessory signal = protectingTheOccupiedPlatform();

        assertTheProtectionQuestion(page, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> !signal.isSwitched(), PATIENCE_MS), "OK did not set the signal green");
    }

    /**
     * The same click on the main window's diagram hangs off the main window.
     *
     * MUTATION: in `LayoutLabel`, the page window whatever window the tile is in, and it hangs off the page.
     */
    @Test
    public void testTheProtectionQuestionFromTheMainWindowHangsOffTheMainWindow() throws Exception
    {
        protectingTheOccupiedPlatform();

        assertTheProtectionQuestion(ui, I18n.t("ui.cancel"));
    }

    /** A route tile whose route would set the protecting signal green; returns the signal. */
    private static Accessory aRouteOverTheProtection() throws Exception
    {
        Accessory signal = protectingTheOccupiedPlatform();

        theRouteDoes(commandFor(signal));

        return signal;
    }

    private static RouteCommand commandFor(Accessory accessory)
    {
        return RouteCommand.RouteCommandAccessory(((MarklinAccessory) accessory).getAddress() + 1,
            accessory.getDecoderType(), false);
    }

    /** The tile's route, made anew with these commands. */
    private static void theRouteDoes(RouteCommand... commands)
    {
        if (model.getRoute(ROUTE) != null) model.deleteRoute(ROUTE);

        assertTrue(model.newRoute(ROUTE, ROUTE_ID, new java.util.ArrayList<>(java.util.Arrays.asList(commands)), 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null), "precondition: the route was not made");
    }

    /**
     * A route clicked in this window, which meets the protection only on its way: nothing stands at the platform until it
     * has started, and it sets the turnout and waits before it comes to the signal.  Answers with this button; the question
     * it parks on.
     */
    private static Accessory aRouteMeetsTheProtectionOnItsWay(java.awt.Window over, String answer) throws Exception
    {
        Accessory signal = AskingWindows.tileIn(ui, page, SIGNAL).getComponent().getAccessory();

        Layout layout = model.getAutoLayout();

        Point platform = layout.getPoint(PLATFORM);

        platform.setProtectingSignal(signal.getName());

        signal.setSwitched(true);

        layout.runLocomotives();

        assertTrue(model.isAutonomyRunning(), "precondition: autonomy is running");

        RouteCommand first = commandFor(AskingWindows.tileIn(ui, page, TURNOUT).getComponent().getAccessory());

        first.setDelay(3000);

        theRouteDoes(first, commandFor(signal));

        try (Answering answering = Answering.start(message -> Answering.press(answer)))
        {
            Mouse.click(AskingWindows.awaitTileIn(ui, over, ROUTE_TILE));

            assertTrue(Components.waitFor(() -> model.getRoute(ROUTE).isExecuting(), PATIENCE_MS), "the route did not start");

            platform.setLocomotive(model.getLocByName(model.getLocList().get(0)));

            AskingWindows.assertAsked(AskingWindows.onlyQuestion(answering, "nothing was asked on the route's way"), over,
                I18n.t("layout.ui.dialogPleaseConfirm"),
                I18n.f("layout.ui.confirmRouteProtectingSignal", ROUTE, signal.getName()),
                JOptionPane.QUESTION_MESSAGE, I18n.t("ui.cancel"), I18n.t("ui.ok"), I18n.t("ui.cancel"));
        }

        return signal;
    }

    /**
     * The question a route started from the page parks on, when the railway changes under it, hangs off the page, and Cancel
     * leaves the signal as it was.
     *
     * MUTATION: `this` for `stillShowing(routeStartedFrom.get())` in `confirmRouteConflictMidway`, and it hangs off the
     * main window.
     */
    @Test
    public void testAQuestionOnARoutesWayHangsOffThePageWindow() throws Exception
    {
        Accessory signal = aRouteMeetsTheProtectionOnItsWay(page, I18n.t("ui.cancel"));

        assertTrue(Components.waitFor(() -> !model.getRoute(ROUTE).isExecuting(), PATIENCE_MS), "Cancel did not end the route");

        assertTrue(signal.isSwitched(), "Cancel set the signal green");
    }

    /**
     * And OK over it goes on, and sets the signal green.
     *
     * MUTATION: `this` for `stillShowing(routeStartedFrom.get())` in `confirmRouteConflictMidway`, and it hangs off the
     * main window.
     */
    @Test
    public void testOKOnARoutesWayGoesOn() throws Exception
    {
        Accessory signal = aRouteMeetsTheProtectionOnItsWay(page, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> !signal.isSwitched(), PATIENCE_MS), "OK did not set the signal green");
    }

    /**
     * With the main window minimized the route's question still shows, over the page, while the route waits.
     *
     * MUTATION: `this` for `stillShowing(routeStartedFrom.get())` in `confirmRouteConflictMidway`, and it is hidden with the
     * main window.
     */
    @Test
    public void testWithTheMainWindowMinimizedARoutesQuestionShowsOverThePage() throws Exception
    {
        AskingWindows.minimize(ui, true);

        aRouteMeetsTheProtectionOnItsWay(page, I18n.t("ui.cancel"));
    }

    /**
     * Started from the main window's own diagram, it hangs off the main window.
     *
     * MUTATION: `routeStartedFrom.get()` is the open page whichever door started the route, and it hangs off the page.
     */
    @Test
    public void testAQuestionOnARoutesWayFromTheMainWindowHangsOffTheMainWindow() throws Exception
    {
        aRouteMeetsTheProtectionOnItsWay(ui, I18n.t("ui.cancel"));
    }

    /**
     * A page that closes between the question being asked for and its being put gives way to the main window: the event
     * thread is held until the asking thread waits on it, and the window goes in between.
     *
     * MUTATION: `over == null ? this : over` for the window in `askAboutRouteConflict`, and it hangs off a window that is
     * not there.
     */
    @Test
    public void testAPageGoneAsTheConflictIsPutGivesWayToTheMainWindow() throws Exception
    {
        aRouteOverTheProtection();

        final LayoutLabel tile = AskingWindows.awaitTileIn(ui, page, ROUTE_TILE);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            clickThen(tile, () -> SwingUtilities.invokeLater(() ->
            {
                long until = System.currentTimeMillis() + PATIENCE_MS;

                while (!aThreadIsAsking() && System.currentTimeMillis() < until) Thread.yield();

                page.setVisible(false);
            }));

            assertSame(AskingWindows.onlyQuestion(answering, "nothing was asked").owner, ui,
                "the question hangs off a window that has gone");
        }
    }

    /** Clicks the square, and does this on the event thread in the same breath: nothing posted since comes between. */
    private static void clickThen(LayoutLabel square, Runnable then)
    {
        SwingUtilities.invokeLater(() ->
        {
            square.dispatchEvent(new java.awt.event.MouseEvent(square, java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(), 0, 1, 1, 1, false, java.awt.event.MouseEvent.BUTTON1));

            then.run();
        });
    }

    private static boolean aThreadIsAsking()
    {
        for (StackTraceElement[] frames : Thread.getAllStackTraces().values())
        {
            for (StackTraceElement frame : frames)
            {
                if (frame.getMethodName().equals("askAboutRouteConflict")) return true;
            }
        }

        return false;
    }

    /** Clicks the route's tile in this window and reads the conflict question; answers with this button. */
    private static void assertTheRouteQuestion(java.awt.Window over, Accessory signal, String answer) throws Exception
    {
        try (Answering answering = Answering.start(message -> Answering.press(answer)))
        {
            Mouse.click(AskingWindows.awaitTileIn(ui, over, ROUTE_TILE));

            AskingWindows.assertAsked(AskingWindows.onlyQuestion(answering, "nothing was asked before running it"), over,
                I18n.t("layout.ui.dialogPleaseConfirm"),
                I18n.f("layout.ui.confirmRouteProtectingSignal", ROUTE, signal.getName()),
                JOptionPane.QUESTION_MESSAGE, I18n.t("ui.cancel"), I18n.t("ui.ok"), I18n.t("ui.cancel"));
        }

        Components.settle();
    }

    /**
     * A route tile's conflict question hangs off the page's window, and Cancel runs nothing.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at `askAboutRouteConflict` in `LayoutLabel`, and it hangs off the
     * main window.
     */
    @Test
    public void testTheRouteConflictHangsOffThePageWindow() throws Exception
    {
        Accessory signal = aRouteOverTheProtection();

        assertTheRouteQuestion(page, signal, I18n.t("ui.cancel"));

        Components.settle();

        assertTrue(signal.isSwitched(), "Cancel ran the route");
    }

    /**
     * And OK over it runs the route over the conflict.
     *
     * MUTATION: `tcUI` for `tcUI.stillShowing(asked)` at `askAboutRouteConflict` in `LayoutLabel`, and it hangs off the
     * main window.
     */
    @Test
    public void testOKOverThePageWindowRunsTheRoute() throws Exception
    {
        Accessory signal = aRouteOverTheProtection();

        assertTheRouteQuestion(page, signal, I18n.t("ui.ok"));

        assertTrue(Components.waitFor(() -> !signal.isSwitched(), PATIENCE_MS), "OK did not run the route");
    }

    /**
     * The same click on the main window's diagram hangs off the main window.
     *
     * MUTATION: in `LayoutLabel`, the page window whatever window the tile is in, and it hangs off the page.
     */
    @Test
    public void testTheRouteConflictFromTheMainWindowHangsOffTheMainWindow() throws Exception
    {
        Accessory signal = aRouteOverTheProtection();

        assertTheRouteQuestion(ui, signal, I18n.t("ui.cancel"));
    }
}
