package ui;

import java.awt.Component;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.LocomotiveSelector;
import org.traincontrol.gui.locomotive.LocomotiveSelectorItem;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Mouse;
import support.SettingsWindow;

/**
 * Multi-Unit from the locomotive database's menu, and the locomotive shortcuts pressed in the database or a page's own
 * window, ask over that window (OB-324); the same doors in the main window ask over the main window.  Real windows, real
 * right clicks and real keys, on the single-switch railway with two locomotives of the class's own.
 */
public class testTheLocomotiveDoorsAskOverTheirWindow
{
    private static final String ONE = "OB-324 one";
    private static final String TWO = "OB-324 two";
    private static final String ROUTE = "OB-324 route";

    private static Locomotive one;
    private static Locomotive two;
    private static LocomotiveSelector database;
    private static LayoutPopupUI page;

    private static final long PATIENCE_MS = 15000;

    private static TrainControlUI ui;
    private static MarklinControlStation model;
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("single-switch", null);

        ui = opened.ui;

        database = ui.getLocSelector();

        model = opened.model;
        sandbox = opened.sandbox;

        // The start-up raise has ended, so a window opened from the main window is compared with where that settles
        Components.waitFor(() -> !ui.isAlwaysOnTop(), PATIENCE_MS);

        Components.settle();

        one = model.newMM2Locomotive(ONE, 71);
        two = model.newMM2Locomotive(TWO, 72);

        Components.onTheEventThread(() ->
        {
            ui.mapLocToCurrentButton(ONE);

            return null;
        });

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == one, PATIENCE_MS), "precondition: " + ONE + " is not active");

        page = AskingWindows.openThePage(ui);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            AskingWindows.closeThePage(page);

            if (model != null) model.clearAutoLayout();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** The database open as the Locomotive menu opens it, listing this class's locomotives. */
    @BeforeMethod
    public void openTheDatabase() throws Exception
    {
        Components.onTheEventThread(() ->
        {
            database.refreshLocSelectorList();

            ui.showLocSelector(false);

            return null;
        });

        assertTrue(Components.waitFor(() -> database.isShowing() && tile(ONE) != null && tile(TWO) != null, PATIENCE_MS),
            "the locomotive database did not open with its tiles");

        Components.settle();
    }

    @AfterMethod(alwaysRun = true)
    public void putThingsBack() throws Exception
    {
        SettingsWindow.closeIfOpen();
        AskingWindows.closeEveryDialog();
        AskingWindows.minimize(ui, false);

        model.getAutoLayout().setStagingInProgress(false);

        one.setLinkedLocomotives(new HashMap<>());

        if (model.getRoute(ROUTE) != null)
        {
            model.deleteRoute(ROUTE);
        }

        Components.settle();
    }

    private static LocomotiveSelectorItem tile(String name)
    {
        return Components.find(database, LocomotiveSelectorItem.class, item -> name.equals(item.getText()));
    }

    /** The main window's keyboard, which hears the shortcuts there. */
    private static Component keyboard() throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField("KeyboardTab");

        field.setAccessible(true);

        return (Component) field.get(ui);
    }

    /** Where a key goes in this window. */
    private static Component whereKeysGo(Window window) throws Exception
    {
        return window == ui ? keyboard() : window;
    }

    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;

    /** The shortcuts that open the Locomotive Settings window: E and R on its first tab, N on Notes, L on Multi-Unit. */
    private static final int[] LOCOMOTIVE_KEYS = {KeyEvent.VK_E, KeyEvent.VK_R, KeyEvent.VK_N, KeyEvent.VK_L};

    private static final int[] LOCOMOTIVE_KEYS_TABS = {0, 0, 2, 3};

    /** Presses a shortcut in this window, posted so that a question it asks cannot hold this thread. */
    private static void pressKey(Window window, int key) throws Exception
    {
        AskingWindows.pressKey(whereKeysGo(window), key, CTRL);
    }

    /**
     * Opens the Locomotive Settings window the way each window's door does: from the database tile's menu, then its
     * Multi-Unit tab; or from the active button's Set as Multi-Unit..., which opens that tab itself.
     */
    private static void chooseMultiUnit(Window window, String name) throws Exception
    {
        JPopupMenu menu = null;

        if (window == database)
        {
            // A window just opened can still be settling into place, which closes a menu as it opens
            for (int tries = 0; menu == null && tries < 3; tries++) menu = Mouse.rightClick(tile(name), PATIENCE_MS / 3);

            assertNotNull(menu, "no menu opened");

            Mouse.choose(AskingWindows.itemOf(menu, I18n.t("loc.ui.menuLocomotiveSettings")));

            Components.waitFor(() -> SettingsWindow.showing() != null, 2000);

            if (SettingsWindow.showing() != null) SettingsWindow.selectTab(3);

            return;
        }

        Mouse.choose(keyMenuItem(name));
    }

    /** The active button's multi-unit entry, with its menu open. */
    private static JMenuItem keyMenuItem(String name) throws Exception
    {
        Field current = TrainControlUI.class.getDeclaredField("currentButton");

        current.setAccessible(true);

        Components.select((JButton) current.get(ui));

        Components.settle();

        JPopupMenu menu = Mouse.rightClick((JButton) current.get(ui), PATIENCE_MS);

        assertNotNull(menu, "no menu opened");

        Locomotive loco = model.getLocByName(name);

        return AskingWindows.itemOf(menu, I18n.t(loco.hasLinkedLocomotives()
            ? "loc.ui.menuEditMultiUnitLocomotives" : "loc.ui.menuSetAsMultiUnit"));
    }

    /** The question a door asked, answered by the rule. */
    private static Answering.Question asked(Function<String, Answering.Answer> rule, Runnable door, String notAsked)
    {
        try (Answering answering = Answering.start(rule))
        {
            door.run();

            Answering.Question question = AskingWindows.onlyQuestion(answering, notAsked);

            AskingWindows.awaitAnswered();

            return question;
        }
    }

    private static Runnable key(Window window, int key)
    {
        return () ->
        {
            try
            {
                pressKey(window, key);
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        };
    }

    private static Runnable multiUnit(Window window, String name)
    {
        return () ->
        {
            try
            {
                chooseMultiUnit(window, name);
            }
            catch (Exception e)
            {
                throw new IllegalStateException(e);
            }
        };
    }

    /** What a door says, in a plain message, over this window. */
    private static void assertSaidBy(Runnable door, Window over, String said)
    {
        AskingWindows.assertSaid(asked(message -> null, door, "nothing was said"), over, said);
    }

    private static Answering.Answer cancel()
    {
        return Answering.pressAt(1);
    }

    // ---------------------------------------------------------------- Multi-Unit

    /**
     * Presses a locomotive shortcut in a window and returns the Locomotive Settings window it opened, on this tab and for
     * this locomotive.
     */
    private static LocomotiveSettingsFrame theKeyOpened(Window window, int key, Locomotive loc, int tab) throws Exception
    {
        pressKey(window, key);

        LocomotiveSettingsFrame opened = SettingsWindow.awaited();

        String name = "Control+" + KeyEvent.getKeyText(key);

        assertNotNull(opened, name + " opened no Locomotive Settings window");
        assertSame(Components.onTheEventThread(opened::getLocomotive), loc, name + " opened the window for another locomotive");
        assertEquals(SettingsWindow.tabShowing(), tab, name + " opened the window on another tab");

        return opened;
    }

    /**
     * Raises a window, does what opens the Locomotive Settings window from it, and says whether the new window stands on
     * top.  Read at once: the harness drops a window that stays on top.
     */
    private static boolean openedWhileRaised(Window raised, Runnable door)
    {
        Components.onTheEventThread(() ->
        {
            raised.setAlwaysOnTop(true);

            return null;
        });

        door.run();

        assertTrue(Components.waitFor(() -> SettingsWindow.showing() != null, 2000), "the window did not open");

        boolean onTop = Components.onTheEventThread(() -> SettingsWindow.showing().isAlwaysOnTop());

        Components.onTheEventThread(() ->
        {
            raised.setAlwaysOnTop(false);

            return null;
        });

        return onTop;
    }

    /** The window the multi-unit door opened, on its Multi-Unit tab and for this locomotive. */
    private static LocomotiveSettingsFrame theWindowFor(Locomotive loc) throws Exception
    {
        LocomotiveSettingsFrame window = SettingsWindow.awaited();

        assertNotNull(window, "the door opened no Locomotive Settings window");
        assertSame(Components.onTheEventThread(window::getLocomotive), loc, "the window is for another locomotive");
        assertEquals(SettingsWindow.tabShowing(), 3, "the window did not open on the Multi-Unit tab");

        return window;
    }

    /**
     * Multi-Unit from the database's menu (its Locomotive Settings..., then the Multi-Unit tab) and from the active
     * button's Set as Multi-Unit... open the one Locomotive Settings window on the Multi-Unit tab, standing as high as the
     * window that asked; Cancel links nothing.
     *
     * MUTATION: open the window on another tab from `LocomotiveMenuItems.multiUnit`, or do not stand it as high as the
     * asking window in `TrainControlUI.openLocomotiveSettings`, and this fails.
     */
    @Test
    public void testMultiUnitAsksOverTheWindowItWasChosenIn() throws Exception
    {
        chooseMultiUnit(ui, ONE);

        LocomotiveSettingsFrame fromTheMain = theWindowFor(one);

        assertEquals((boolean) Components.onTheEventThread(fromTheMain::isAlwaysOnTop), ui.isAlwaysOnTop(), "the window is not as high as the main window");

        SettingsWindow.press(SettingsWindow.cancel());

        // The database stands on top while the main window does not, so the window must follow the database.  The
        // harness drops a window that stays on top for a second and a half, so the database is raised with the menu
        // open and the window is read at once.
        JPopupMenu menu = Mouse.rightClick(tile(ONE), PATIENCE_MS);

        assertNotNull(menu, "no menu opened");

        JMenuItem settings = AskingWindows.itemOf(menu, I18n.t("loc.ui.menuLocomotiveSettings"));

        boolean onTop = openedWhileRaised(database, () -> Mouse.choose(settings));

        SettingsWindow.selectTab(3);

        LocomotiveSettingsFrame fromTheDatabase = theWindowFor(one);

        assertTrue(Components.onTheEventThread(fromTheDatabase::isShowing), "the window is not on screen");
        assertTrue(onTop, "the window stands lower than the database it was asked from: it opens behind it");

        SettingsWindow.press(SettingsWindow.cancel());

        assertFalse(one.hasLinkedLocomotives(), "Cancel linked something");
    }

    /**
     * OK (Save) in the window opened from the database, with a locomotive ticked, links it.
     *
     * MUTATION: leave `applyLinkedLocomotives` out of `LocomotiveSettingsFrame.write`, and this fails.
     */
    @Test
    public void testOKOverTheDatabaseLinksWhatIsTicked() throws Exception
    {
        chooseMultiUnit(database, ONE);

        theWindowFor(one);

        SettingsWindow.tickMember(TWO);
        SettingsWindow.press(SettingsWindow.save());

        assertTrue(Components.waitFor(() -> one.getLinkedLocomotiveNames().containsKey(TWO), PATIENCE_MS),
            "Save did not link the ticked locomotive");
    }

    /**
     * A locomotive already in another's multi-unit has its Multi-Unit tab say so, from the database, the main window
     * and Control+L over a page.
     *
     * MUTATION: show the list instead of the sentence in `LocomotiveSettingsFrame.MultiUnitTab`, or open another tab in
     * the "linked locomotives" row of `TrainControlUI.KeyShortcuts`, and this fails.
     */
    @Test
    public void testAlreadyLinkedIsSaidOverTheWindowThatAsked() throws Exception
    {
        Map<String, Double> linked = new HashMap<>();

        linked.put(TWO, 1.0);

        one.setLinkedLocomotives(linked);

        String sentence = I18n.f("loc.ui.settingsPartOfMultiUnit", TWO, ONE);

        chooseMultiUnit(database, TWO);

        theWindowFor(two);

        assertTheTabSays(SettingsWindow.showing(), sentence, "the tab does not say " + TWO + " belongs to " + ONE);

        SettingsWindow.press(SettingsWindow.cancel());

        Components.onTheEventThread(() ->
        {
            ui.mapLocToCurrentButton(TWO);

            return null;
        });

        Components.settle();

        try
        {
            LocomotiveSettingsFrame fromThePage = theKeyOpened(page, KeyEvent.VK_L, two, 3);

            assertTheTabSays(fromThePage, sentence, "Control+L over a page: the tab does not say " + TWO + " belongs to " + ONE);

            SettingsWindow.press(SettingsWindow.cancel());

            chooseMultiUnit(ui, TWO);

            theWindowFor(two);
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                ui.mapLocToCurrentButton(ONE);

                return null;
            });

            Components.settle();
        }
    }

    /**
     * While autonomy is busy no door opens the window: the database's menu says so over the database, the button's
     * entry is grayed with the reason as its tooltip, and Control+E, R, N and L say it over the window they were pressed
     * in.
     *
     * MUTATION: `this` for the asking window in `TrainControlUI.openLocomotiveSettings`, or leave autonomy out of
     * `LocomotiveSettings.why`, and this fails.
     */
    @Test
    public void testNoMultiUnitsWhileRunningIsSaidOverTheWindowThatAsked() throws Exception
    {
        model.getAutoLayout().setStagingInProgress(true);

        String said = I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning");

        assertSaidBy(multiUnit(database, ONE), database, said);

        JMenuItem item = keyMenuItem(ONE);

        assertFalse(item.isEnabled(), "the button's entry is open while autonomy runs");
        assertEquals(item.getToolTipText(), said, "the button's entry does not say why");

        Mouse.closeMenus();

        assertEveryLocomotiveKeySays(said);

        assertNull(SettingsWindow.showing(), "the window opened while autonomy runs");
    }

    /** Each of the four locomotive shortcuts, in each window, says this over the window it was pressed in. */
    private static void assertEveryLocomotiveKeySays(String said)
    {
        for (Window window : new Window[] {database, page, ui})
        {
            for (int shortcut : LOCOMOTIVE_KEYS) assertSaidBy(key(window, shortcut), window, said);
        }
    }

    /** The window shows this sentence once. */
    private static void assertTheTabSays(LocomotiveSettingsFrame window, String sentence, String message)
    {
        assertEquals(Components.all(window.getContentPane(), javax.swing.JLabel.class,
            label -> sentence.equals(label.getText())).size(), 1, message);
    }

    /** Presses Save and checks that the one thing said is this, over the Locomotive Settings window itself. */
    private static void assertSaveSays(LocomotiveSettingsFrame window, String said)
    {
        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(SettingsWindow.save());

            AskingWindows.assertSaid(AskingWindows.onlyQuestion(answering, "Save said nothing"), window, said);

            AskingWindows.awaitAnswered();
        }
    }

    /** A route that drives the first locomotive, running long enough for every door to be tried. */
    private static void aRouteDrivesIt() throws Exception
    {
        List<RouteCommand> commands = new ArrayList<>();

        RouteCommand fires = RouteCommand.RouteCommandFunction(ONE, 1, true);

        fires.setDelay(20000);

        commands.add(fires);

        model.newRoute(ROUTE, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        new Thread(() -> model.execRoute(ROUTE)).start();

        assertTrue(Components.waitFor(() -> model.getRoute(ROUTE).isExecuting(), PATIENCE_MS),
            "precondition: the route never started");
    }

    /**
     * A route that drives the locomotive does not keep the window: it opens from both doors and from Control+R for the
     * stopped locomotive, and the route's refusal is said at Save over the window itself.
     *
     * MUTATION: put a running route into `LocomotiveSettings.why`, and the window does not open and this fails.
     */
    @Test
    public void testADrivingRouteIsSaidOverTheWindowThatAsked() throws Exception
    {
        aRouteDrivesIt();

        String said = I18n.f("loc.ui.errorLocomotiveDrivenByARunningRoute", ONE, ROUTE);

        chooseMultiUnit(database, ONE);

        LocomotiveSettingsFrame window = theWindowFor(one);

        SettingsWindow.tickMember(TWO);

        assertSaveSays(window, said);

        SettingsWindow.press(SettingsWindow.cancel());

        for (Window pressed : new Window[] {page, ui})
        {
            LocomotiveSettingsFrame fromTheKey = theKeyOpened(pressed, KeyEvent.VK_R, one, 0);

            SettingsWindow.type("settings.name", ONE + " renamed");

            assertSaveSays(fromTheKey, said);

            SettingsWindow.press(SettingsWindow.cancel());
        }

        assertEquals(one.getName(), ONE, "a refused rename renamed it");
    }

    /**
     * With fewer than two locomotives the Multi-Unit tab says so, instead of a list, from the button's menu and from
     * Control+L.
     *
     * MUTATION: show the list whatever the number of locomotives in `LocomotiveSettingsFrame.MultiUnitTab`, and this
     * fails.
     */
    @Test
    public void testNeedingTwoIsSaidOverTheWindowThatAsked() throws Exception
    {
        AskingWindows.withOnlyTheseLocomotives(model, Collections.singletonList(ONE), () ->
        {
            chooseMultiUnit(ui, ONE);

            theWindowFor(one);

            String said = I18n.t("loc.ui.errorNeedTwoLocomotivesForMultiUnit");

            assertTheTabSays(SettingsWindow.showing(), said, "the tab does not say two locomotives are needed");

            SettingsWindow.press(SettingsWindow.cancel());

            LocomotiveSettingsFrame fromTheKey = theKeyOpened(ui, KeyEvent.VK_L, one, 3);

            assertTheTabSays(fromTheKey, said, "Control+L: the tab does not say two are needed");

            SettingsWindow.press(SettingsWindow.cancel());
        });
    }

    /**
     * With the main window minimized, the window opened from the database still shows.
     *
     * MUTATION: show the window only when the main window is not minimized, in `TrainControlUI.openLocomotiveSettings`, and
     * this fails.
     */
    @Test
    public void testWithTheMainWindowMinimizedMultiUnitShowsOverTheDatabase() throws Exception
    {
        AskingWindows.minimize(ui, true);

        chooseMultiUnit(database, ONE);

        assertTrue(Components.onTheEventThread(theWindowFor(one)::isShowing), "the window is not on screen with the main window minimized");
    }

    /**
     * Control+E, R, N and L open the one Locomotive Settings window for the shown locomotive on the right tab, from the main
     * window, a page's window and the database, and from a page they stand as high as the page.
     *
     * MUTATION: `this` for the asking window in `TrainControlUI.openLocomotiveSettings`, or another tab in any of the four
     * rows of `TrainControlUI.KeyShortcuts` (`locomotive settings`, `change the address`, `locomotive notes`, `linked
     * locomotives`), and this fails.
     */
    @Test
    public void testTheLocomotiveKeysOpenTheWindowOverTheWindowTheyWerePressedIn() throws Exception
    {
        for (Window window : new Window[] {ui, page, database})
        {
            for (int at = 0; at < LOCOMOTIVE_KEYS.length; at++)
            {
                theKeyOpened(window, LOCOMOTIVE_KEYS[at], one, LOCOMOTIVE_KEYS_TABS[at]);

                SettingsWindow.press(SettingsWindow.cancel());
            }

            // The database lowers itself when it gains focus, so its height is claimed from its menu above
            if (window != page) continue;

            boolean onTop = openedWhileRaised(window, key(window, KeyEvent.VK_E));

            assertTrue(onTop, "the window stands lower than the page Control+E was pressed in: it opens behind it");

            SettingsWindow.closeIfOpen();
        }
    }

    /**
     * A locomotive that is moving is not edited from the keyboard: each of the four shortcuts says "Stop it" over the
     * window it was pressed in, and opens nothing.
     *
     * MUTATION: leave `isMoving` out of `LocomotiveSettings.why`, or send one of the four rows around
     * `openLocomotiveSettings`, and this fails.
     */
    @Test
    public void testAMovingLocomotiveIsSaidOverTheWindowTheKeyWasPressedIn() throws Exception
    {
        SettingsWindow.power(ui, model, true);
        one.setSpeed(100);

        try
        {
            String said = I18n.f("loc.ui.stopToEdit", ONE);

            assertEveryLocomotiveKeySays(said);

            assertNull(SettingsWindow.showing(), "the window opened for a moving locomotive");
        }
        finally
        {
            one.setSpeed(0);
            SettingsWindow.power(ui, model, false);
        }
    }

    // ---------------------------------------------------------------- the shortcuts

    /**
     * Control+F asks over the window it was pressed in, and an answer jumps to that locomotive.
     *
     * MUTATION: `this.ui` for `this.ui.stillShowing(asked)` in `quickLocSearch`, and it hangs off the main window.
     */
    @Test
    public void testQuickFindAsksOverTheWindowItWasPressedIn() throws Exception
    {
        for (Window window : new Window[] {page, database, ui})
        {
            AskingWindows.assertAsked(asked(message -> cancel(), key(window, KeyEvent.VK_F), "Control+F asked nothing"),
                window, UIManager.getString("OptionPane.inputDialogTitle"), I18n.t("loc.ui.promptEnterLocomotiveNameToJumpTo"),
                JOptionPane.QUESTION_MESSAGE, null, UIManager.getString("OptionPane.okButtonText"),
                UIManager.getString("OptionPane.cancelButtonText"));
        }

        assertSame(ui.getActiveLoc(), one, "Cancel moved the active locomotive");

        // A jump goes to a button holding the locomotive: the other one on another button, and that one showing
        AskingWindows.pressKey(keyboard(), KeyEvent.VK_W, 0);

        Components.onTheEventThread(() ->
        {
            ui.mapLocToCurrentButton(TWO);

            return null;
        });

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == two, PATIENCE_MS), "precondition: " + TWO + " is not showing");

        assertSame(asked(message -> Answering.type(ONE), key(page, KeyEvent.VK_F), "Control+F asked nothing").owner, page,
            "the question is not the page's");

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == one, PATIENCE_MS), "the answer did not jump to " + ONE);
    }

    /** A part of the database's window, which its keys really arrive at. */
    private static Component inTheDatabase(String field) throws Exception
    {
        Field part = LocomotiveSelector.class.getDeclaredField(field);

        part.setAccessible(true);

        return (Component) part.get(database);
    }

    /**
     * Control+F in the database's list and in its filter box asks over the database: a key there comes from a component
     * inside the window, not the window.
     *
     * MUTATION: `this` for `SwingUtilities.getWindowAncestor(on)` in `childWindowKeyEvent`, and it hangs off the main
     * window.
     */
    @Test
    public void testAKeyInsideTheDatabaseAsksOverTheDatabase() throws Exception
    {
        Component list = inTheDatabase("MainLocList");
        Component filter = inTheDatabase("LocFilterBox");

        assertSame(asked(message -> cancel(), () -> AskingWindows.pressKey(list, KeyEvent.VK_F, CTRL),
            "Control+F in the list asked nothing").owner, database, "the list's question is not the database's");

        assertSame(asked(message -> cancel(), () -> AskingWindows.releaseKey(filter, KeyEvent.VK_F, CTRL),
            "Control+F in the filter box asked nothing").owner, database, "the filter box's question is not the database's");
    }

    /**
     * With the main window minimized, Control+F in the page still shows, over the page.
     *
     * MUTATION: `this.ui` for `this.ui.stillShowing(asked)` in `quickLocSearch`, and it is hidden with the main window.
     */
    @Test
    public void testWithTheMainWindowMinimizedQuickFindShowsOverThePage() throws Exception
    {
        AskingWindows.minimize(ui, true);

        assertSame(asked(message -> cancel(), key(page, KeyEvent.VK_F), "Control+F asked nothing").owner, page,
            "the question is not the page's");
    }

    /** Control+R in this window, a name typed in the window that opens and saved; what is said then, over that window. */
    private static void assertARefusedName(Window window, String name, String said) throws Exception
    {
        LocomotiveSettingsFrame opened = theKeyOpened(window, KeyEvent.VK_R, one, 0);

        SettingsWindow.type("settings.name", name);

        assertSaveSays(opened, said);

        SettingsWindow.press(SettingsWindow.cancel());
    }

    /**
     * A name refused says why over the Locomotive Settings window that Control+R opened from the main window, a page or the
     * database: empty, too long, another's, or with a comma.
     *
     * MUTATION: `this.ui` for `source` at the refusals in `LocomotiveActions.writeNameAndAddress`, or `this` for the
     * asking window in `LocomotiveSettingsFrame.write`, and they hang off the main window.
     */
    @Test
    public void testARefusedNameIsSaidOverTheWindowThatAsked() throws Exception
    {
        assertARefusedName(page, " ", I18n.t("loc.ui.errorEmptyLocomotiveName"));

        StringBuilder tooLong = new StringBuilder();

        while (tooLong.length() <= TrainControlUI.MAX_LOC_NAME_DATABASE) tooLong.append('x');

        assertARefusedName(database, tooLong.toString(),
            I18n.f("loc.ui.errorLocomotiveNameTooLong", TrainControlUI.MAX_LOC_NAME_DATABASE));

        assertARefusedName(page, TWO, I18n.f("loc.ui.errorLocomotiveAlreadyExists", TWO));

        assertARefusedName(database, "OB-324, comma", I18n.t("loc.ui.errorLocomotiveNameUnusable"));

        assertEquals(one.getName(), ONE, "a refused name renamed it");

        assertARefusedName(ui, " ", I18n.t("loc.ui.errorEmptyLocomotiveName"));
    }

    /**
     * While autonomy is busy, Control+R and Control+Delete say so over the window they were pressed in.
     *
     * MUTATION: `this` for the asking window in `TrainControlUI.openLocomotiveSettings`, or `this.ui` for
     * `this.ui.dialogParent()` at `source` in `deleteLoc`, and they hang off the main window.
     */
    @Test
    public void testNoEditsWhileRunningAreSaidOverTheWindowThatAsked() throws Exception
    {
        model.getAutoLayout().setStagingInProgress(true);

        String said = I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning");

        for (Window window : new Window[] {page, database, ui})
        {
            assertSaidBy(key(window, KeyEvent.VK_R), window, said);
            assertSaidBy(key(window, KeyEvent.VK_DELETE), window, said);
        }
    }

    /**
     * Control+Delete asks over the window it was pressed in, starting on No, and No keeps the locomotive; Yes deletes it.
     *
     * MUTATION: `this.ui` for `this.ui.dialogParent()` at `source` in `deleteLoc`, and it hangs off the main window.
     */
    @Test
    public void testDeleteAsksOverTheWindowItWasPressedIn() throws Exception
    {
        String no = String.valueOf(TrainControlUI.YES_NO_OPTS[1]);
        String yes = String.valueOf(TrainControlUI.YES_NO_OPTS[0]);

        for (Window window : new Window[] {page, database, ui})
        {
            AskingWindows.assertAsked(asked(message -> Answering.press(no), key(window, KeyEvent.VK_DELETE),
                "Control+Delete asked nothing"), window, I18n.t("layout.ui.dialogPleaseConfirm"),
                I18n.f("ui.confirmDeleteFromDatabase", ONE), JOptionPane.PLAIN_MESSAGE, no, yes, no);
        }

        assertNotNull(model.getLocByName(ONE), "No deleted it");

        final String going = "OB-324 going";

        model.newMM2Locomotive(going, 73);

        Components.onTheEventThread(() ->
        {
            ui.mapLocToCurrentButton(going);

            return null;
        });

        Components.settle();

        try
        {
            assertSame(asked(message -> Answering.press(yes), key(page, KeyEvent.VK_DELETE),
                "Control+Delete asked nothing").owner, page, "the question is not the page's");

            assertTrue(Components.waitFor(() -> model.getLocByName(going) == null, PATIENCE_MS), "Yes did not delete it");
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                ui.mapLocToCurrentButton(ONE);

                return null;
            });

            Components.settle();
        }
    }

    /**
     * Control+A with an empty database says so over the window it was pressed in.
     *
     * MUTATION: `this` for `stillShowing(asked)` in `showLocSelector`, and it hangs off the main window.
     */
    @Test
    public void testAnEmptyDatabaseIsSaidOverTheWindowThatAsked() throws Exception
    {
        AskingWindows.withOnlyTheseLocomotives(model, Collections.emptyList(), () ->
        {
            String said = I18n.t("error.noLocs");

            assertSaidBy(key(page, KeyEvent.VK_A), page, said);
            assertSaidBy(key(ui, KeyEvent.VK_A), ui, said);
        });
    }
}
