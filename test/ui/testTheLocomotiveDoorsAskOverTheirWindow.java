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
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
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

    /** Presses a shortcut in this window, posted so that a question it asks cannot hold this thread. */
    private static void pressKey(Window window, int key) throws Exception
    {
        AskingWindows.pressKey(whereKeysGo(window), key, CTRL);
    }

    /** Right-clicks a locomotive in the database, or the active one's button in the main window, for its Multi-Unit. */
    private static void chooseMultiUnit(Window window, String name) throws Exception
    {
        JPopupMenu menu = null;

        if (window == database)
        {
            // A window just opened can still be settling into place, which closes a menu as it opens
            for (int tries = 0; menu == null && tries < 3; tries++) menu = Mouse.rightClick(tile(name), PATIENCE_MS / 3);
        }
        else
        {
            Field current = TrainControlUI.class.getDeclaredField("currentButton");

            current.setAccessible(true);

            Components.select((JButton) current.get(ui));

            Components.settle();

            menu = Mouse.rightClick((JButton) current.get(ui), PATIENCE_MS);
        }

        assertNotNull(menu, "no menu opened");

        Locomotive loco = model.getLocByName(name);

        Mouse.choose(AskingWindows.itemOf(menu, I18n.t(loco.hasLinkedLocomotives()
            ? "loc.ui.menuEditMultiUnitLocomotives" : "loc.ui.menuSetAsMultiUnit")));
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

    /** Multi-Unit on the first locomotive says this over the database, and over the main window from there. */
    private static void assertMultiUnitSays(String said)
    {
        assertSaidBy(multiUnit(database, ONE), database, said);
        assertSaidBy(multiUnit(ui, ONE), ui, said);
    }

    private static Answering.Answer cancel()
    {
        return Answering.pressAt(1);
    }

    // ---------------------------------------------------------------- Multi-Unit

    /** The chooser's shape over a window. */
    private static void assertTheChooser(Answering.Question question, Window over, String said)
    {
        AskingWindows.assertAsked(question, over, I18n.f("loc.ui.dialogLinkLocomotivesToMultiUnit", ONE), said,
            JOptionPane.PLAIN_MESSAGE, String.valueOf(TrainControlUI.OK_CANCEL_OPTS[0]),
            String.valueOf(TrainControlUI.OK_CANCEL_OPTS[0]), String.valueOf(TrainControlUI.OK_CANCEL_OPTS[1]));
    }

    /**
     * Multi-Unit from the database's right-click menu asks over the database, the same question as from the main
     * window's own button, which asks over the main window; Cancel links nothing.
     *
     * MUTATION: `this` for `asked` at the chooser in `changeLinkedLocomotives`, and it hangs off the main window.
     */
    @Test
    public void testMultiUnitAsksOverTheWindowItWasChosenIn() throws Exception
    {
        Answering.Question fromTheMain = asked(message -> cancel(), multiUnit(ui, ONE), "the button's menu asked nothing");

        assertTheChooser(fromTheMain, ui, fromTheMain.message);

        Answering.Question fromTheDatabase = asked(message -> cancel(), multiUnit(database, ONE),
            "the database's menu asked nothing");

        assertTheChooser(fromTheDatabase, database, fromTheMain.message);

        assertFalse(one.hasLinkedLocomotives(), "Cancel linked something");
    }

    /**
     * OK over the database, with a locomotive ticked, links it.
     *
     * MUTATION: `this` for `asked` at the chooser in `changeLinkedLocomotives`, and it hangs off the main window.
     */
    @Test
    public void testOKOverTheDatabaseLinksWhatIsTicked() throws Exception
    {
        Answering.Question question = asked(message ->
        {
            AskingWindows.inTheQuestion(JCheckBox.class, each -> TWO.equals(each.getText()), box -> box.doClick(0));

            return Answering.pressAt(0);
        }, multiUnit(database, ONE), "the database's menu asked nothing");

        assertSame(question.owner, database, "the chooser is not the database's");

        assertTrue(Components.waitFor(() -> one.getLinkedLocomotiveNames().containsKey(TWO), PATIENCE_MS),
            "OK did not link the ticked locomotive");
    }

    /**
     * A locomotive already in another's multi-unit is told so over the database, or over the main window from there.
     *
     * MUTATION: `this` for `asked` at the first message in `changeLinkedLocomotives`, and it hangs off the main window.
     */
    @Test
    public void testAlreadyLinkedIsSaidOverTheWindowThatAsked() throws Exception
    {
        Map<String, Double> linked = new HashMap<>();

        linked.put(TWO, 1.0);

        one.setLinkedLocomotives(linked);

        String said = I18n.f("loc.ui.errorLocomotiveAlreadyLinkedCannotBeMultiUnit", ONE);

        assertSaidBy(multiUnit(database, TWO), database, said);

        Components.onTheEventThread(() ->
        {
            ui.mapLocToCurrentButton(TWO);

            return null;
        });

        Components.settle();

        try
        {
            assertSaidBy(key(page, KeyEvent.VK_L), page, said);
            assertSaidBy(multiUnit(ui, TWO), ui, said);
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
     * While autonomy is busy, Multi-Unit says it cannot be done, over the database and over the main window.
     *
     * MUTATION: `this` for `asked` at that message in `changeLinkedLocomotives`, and it hangs off the main window.
     */
    @Test
    public void testNoMultiUnitsWhileRunningIsSaidOverTheWindowThatAsked() throws Exception
    {
        model.getAutoLayout().setStagingInProgress(true);

        String said = I18n.t("autolayout.ui.errorCannotEditMultiUnitsWhileRunning");

        assertMultiUnitSays(said);
        assertSaidBy(key(database, KeyEvent.VK_L), database, said);
        assertSaidBy(key(page, KeyEvent.VK_L), page, said);
        assertSaidBy(key(ui, KeyEvent.VK_L), ui, said);
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
     * While a route drives it, Multi-Unit and Edit say so over the window that asked.
     *
     * MUTATION: `this` for `asked` at `refuseWhileARouteDrivesAny` in `changeLinkedLocomotives`, and it hangs off the
     * main window.
     */
    @Test
    public void testADrivingRouteIsSaidOverTheWindowThatAsked() throws Exception
    {
        aRouteDrivesIt();

        String said = I18n.f("loc.ui.errorLocomotiveDrivenByARunningRoute", ONE, ROUTE);

        assertMultiUnitSays(said);
        assertSaidBy(key(page, KeyEvent.VK_R), page, said);
        assertSaidBy(key(ui, KeyEvent.VK_R), ui, said);
    }

    /**
     * With fewer than two locomotives, Multi-Unit says so over the window that asked.
     *
     * MUTATION: `this` for `asked` at that message in `changeLinkedLocomotives`, and it hangs off the main window.
     */
    @Test
    public void testNeedingTwoIsSaidOverTheWindowThatAsked() throws Exception
    {
        AskingWindows.withOnlyTheseLocomotives(model, Collections.singletonList(ONE), () ->
        {
            String said = I18n.t("loc.ui.errorNeedTwoLocomotivesForMultiUnit");

            assertSaidBy(multiUnit(database, ONE), database, said);
            assertSaidBy(key(ui, KeyEvent.VK_L), ui, said);
        });
    }

    /**
     * With the main window minimized, Multi-Unit from the database still shows, over the database.
     *
     * MUTATION: `this` for `asked` at the chooser in `changeLinkedLocomotives`, and it is hidden with the main window.
     */
    @Test
    public void testWithTheMainWindowMinimizedMultiUnitShowsOverTheDatabase() throws Exception
    {
        AskingWindows.minimize(ui, true);

        assertSame(asked(message -> cancel(), multiUnit(database, ONE), "the database's menu asked nothing").owner, database,
            "the chooser is not the database's");
    }

    /**
     * A database closed while its question is up takes the question with it, and nothing is linked.
     *
     * MUTATION: `this` for `asked` at the chooser in `changeLinkedLocomotives`, and the question outlives the window.
     */
    @Test
    public void testADatabaseThatClosesTakesItsQuestionWithIt() throws Exception
    {
        chooseMultiUnit(database, ONE);

        AskingWindows.assertClosingTakesTheQuestion(database);

        assertFalse(one.hasLinkedLocomotives(), "the closed question linked something");
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

    /**
     * Control+R asks over the window it was pressed in, the same form from each, and Cancel changes nothing.
     *
     * MUTATION: `this.ui` for `this.ui.dialogParent()` at `source` in `changeLocAddress`, and it hangs off the main window.
     */
    @Test
    public void testEditNameAndAddressAsksOverTheWindowItWasPressedIn() throws Exception
    {
        for (Window window : new Window[] {ui, page, database})
        {
            Answering.Question question = asked(message -> cancel(), key(window, KeyEvent.VK_R), "Control+R asked nothing");

            // The form's fields, which it fills as it opens, are not its words
            AskingWindows.assertAsked(question, window, I18n.f("loc.ui.dialogChangeNameOrAddress", ONE),
                AskingWindows.ANY_MESSAGE, JOptionPane.PLAIN_MESSAGE, String.valueOf(TrainControlUI.OK_CANCEL_OPTS[0]),
                String.valueOf(TrainControlUI.OK_CANCEL_OPTS[0]), String.valueOf(TrainControlUI.OK_CANCEL_OPTS[1]));

            assertTrue(question.message.contains(ONE), "the form does not hold the name: " + question.message);
        }

        assertEquals(one.getName(), ONE, "Cancel renamed it");
    }

    /** Types into the form's name field, which holds the name, and presses OK. */
    private static Answering.Answer renamedTo(String name)
    {
        AskingWindows.inTheQuestion(JTextField.class, each -> ONE.equals(each.getText()), field -> field.setText(name));

        return Answering.pressAt(0);
    }

    /** Control+R in this window, renaming to this; the message that follows, over the same window. */
    private static void assertARefusedName(Window window, String name, String said) throws Exception
    {
        try (Answering answering = Answering.start(message -> message.contains(ONE) ? renamedTo(name) : null))
        {
            pressKey(window, KeyEvent.VK_R);

            assertTrue(answering.awaitAsked(2, PATIENCE_MS), "no message followed the name \"" + name + "\": "
                + answering.asked());

            AskingWindows.assertSaid(answering.questions().get(1), window, said);
        }

        Components.settle();
    }

    /**
     * A name refused says why over the window the form was opened in: empty, too long, another's, or with a comma.
     *
     * MUTATION: `this.ui` for `this.ui.dialogParent()` at `source` in `changeLocAddress`, and they hang off the main
     * window.
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
     * MUTATION: `this.ui` for `this.ui.dialogParent()` at `source` in `changeLocAddress` or `deleteLoc`, and they hang
     * off the main window.
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
     * Control+N asks over the window it was pressed in, the same form from each, and Cancel writes nothing.
     *
     * MUTATION: `this.ui` for `this.ui.dialogParent()` at `source` in `changeLocNotes`, and it hangs off the main window.
     */
    @Test
    public void testNotesAskOverTheWindowItWasPressedIn() throws Exception
    {
        Answering.Question fromTheMain = asked(message -> cancel(), key(ui, KeyEvent.VK_N), "Control+N asked nothing");

        String notesWere = String.valueOf(one.getNotes());

        for (Window window : new Window[] {ui, page, database})
        {
            AskingWindows.assertAsked(window == ui ? fromTheMain
                : asked(message -> cancel(), key(window, KeyEvent.VK_N), "Control+N asked nothing"), window,
                I18n.f("loc.ui.dialogEditLocomotiveInfo", ONE), fromTheMain.message, JOptionPane.PLAIN_MESSAGE,
                String.valueOf(TrainControlUI.OK_CANCEL_OPTS[0]), String.valueOf(TrainControlUI.OK_CANCEL_OPTS[0]),
                String.valueOf(TrainControlUI.OK_CANCEL_OPTS[1]));
        }

        assertEquals(String.valueOf(one.getNotes()), notesWere, "Cancel wrote the notes");
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
