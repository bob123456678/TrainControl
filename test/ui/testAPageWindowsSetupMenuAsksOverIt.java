package ui;

import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.swing.JDialog;
import javax.swing.JList;
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
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;

/**
 * The autonomy setup on a square's right-click menu asks its questions over the window the square was clicked in
 * (OB-324): the same panel builds them for a page's own window and for the main window's diagram, and each claim
 * presses the item in both and reads the two questions.  On the frozen copy of Adam's railway, in a sandbox.  Every
 * door shares one line, `AutonomyEditorPanel.owner`, so each MUTATION is that line.
 */
public class testAPageWindowsSetupMenuAsksOverIt
{
    private static final long PATIENCE_MS = 20000;

    private static final String YES = AskingWindows.YES;
    private static final String NO = AskingWindows.NO;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutPopupUI page;

    /** A station on the page the windows show. */
    private static TileKey station;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        Components.settle();

        session = ui.getAutonomySession();

        assertNotNull(session, "no autonomy setup on this railway");

        station = AskingWindows.stationNamed(session, "BottomMainB");

        AskingWindows.showThePageOf(ui, station);

        page = AskingWindows.openThePage(ui);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            AskingWindows.closeThePage(page);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** A door can leave the main window on another tab or page. */
    @BeforeMethod
    public void theWindowsOnTheStationsPage() throws Exception
    {
        AskingWindows.showThePageOf(ui, station);
    }

    @AfterMethod(alwaysRun = true)
    public void closeWhatIsLeft() throws Exception
    {
        AskingWindows.closeEveryDialog();
    }

    /** The first square on the windows' page that this says yes to. */
    private static TileKey aSquare(java.util.function.Predicate<LayoutDiagramComponent> wanted)
    {
        for (LayoutDiagramComponent each : model.getLayout(station.getPage()).getAll())
        {
            if (wanted.test(each)) return new TileKey(station.getPage(), each.getX(), each.getY());
        }

        throw new AssertionError("precondition: the page has no such square");
    }

    /** Presses an item, answering by the rule, until this many questions have been asked; what they were. */
    private static List<Answering.Question> press(Window window, TileKey square, Function<JPopupMenu, JMenuItem> item,
        Function<String, Answering.Answer> rule, int questions) throws Exception
    {
        return AskingWindows.choose(item.apply(AskingWindows.menuOn(ui, window, square)), rule, questions);
    }

    private static Function<JPopupMenu, JMenuItem> item(String text)
    {
        return menu -> AskingWindows.itemOf(menu, text);
    }

    /** An item whose text holds a number: `key` with whichever count the menu shows. */
    private static Function<JPopupMenu, JMenuItem> counted(String key)
    {
        return menu ->
        {
            for (int count = 0; count < 2000; count++)
            {
                if (AskingWindows.hasItem(menu, I18n.f(key, count))) return AskingWindows.itemOf(menu, I18n.f(key, count));
            }

            throw new AssertionError("precondition: the menu has no " + key);
        };
    }

    /**
     * The door pressed from the main window and then from the page's: each hangs off its own window, both say the same
     * and start on the same button.  Answered by the rule, which must change nothing.
     */
    private static Answering.Question bothWays(TileKey square, Function<JPopupMenu, JMenuItem> item,
        Function<String, Answering.Answer> rule, String title, int type, String startsOn, String... buttons) throws Exception
    {
        Answering.Question fromTheMain = press(ui, square, item, rule, 1).get(0);

        Answering.Question fromThePage = press(page, square, item, rule, 1).get(0);

        for (Answering.Question each : new Answering.Question[] {fromTheMain, fromThePage})
        {
            AskingWindows.assertAsked(each, each == fromTheMain ? ui : page, title, fromTheMain.message, type, startsOn,
                buttons);
        }

        return fromThePage;
    }

    /** {@link #bothWays} for a list to pick from, whose message holds what its field was last given. */
    private static void pickingBothWays(TileKey square, Function<JPopupMenu, JMenuItem> item,
        Function<String, Answering.Answer> rule, String title, int type, String startsOn, String... buttons) throws Exception
    {
        for (Window window : new Window[] {ui, page})
        {
            AskingWindows.assertAsked(press(window, square, item, rule, 1).get(0), window, title, AskingWindows.ANY_MESSAGE,
                type, startsOn, buttons);
        }
    }

    /** A list to pick from, whose Cancel leaves this setting of the station as it was. */
    private static void assertCancelKeeps(String property, Function<JPopupMenu, JMenuItem> door, String title) throws Exception
    {
        Object was = String.valueOf(session.getPointProperty(station, property));

        pickingBothWays(station, door, message -> cancel(), title, JOptionPane.PLAIN_MESSAGE, null, ok(),
            UIManager.getString("OptionPane.cancelButtonText"));

        assertEquals(String.valueOf(session.getPointProperty(station, property)), was, "Cancel changed " + property);
    }

    private static Answering.Answer cancel()
    {
        return Answering.press(UIManager.getString("OptionPane.cancelButtonText"));
    }

    private static String ok()
    {
        return UIManager.getString("OptionPane.okButtonText");
    }

    private static String input()
    {
        return UIManager.getString("OptionPane.inputDialogTitle");
    }

    /** A number typed in answer to `prompt`, then the message that follows, over each window. */
    private static void assertARefusal(TileKey square, Function<JPopupMenu, JMenuItem> item, String prompt, String typed,
        String said) throws Exception
    {
        for (Window window : new Window[] {ui, page})
        {
            List<Answering.Question> asked = press(window, square, item,
                message -> message.equals(prompt) ? Answering.type(typed) : null, 2);

            assertSame(asked.get(1).owner, window, "the refusal of \"" + typed + "\" hangs off the wrong window");

            if (said != null) assertEquals(asked.get(1).message, said, "the refusal of \"" + typed + "\" changed");
        }
    }

    // ---------------------------------------------------------------- the numbers

    /**
     * Speed Multiplier asks over the window, Cancel writes nothing, a number writes it, and both refusals are said over
     * the same window.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and
     * they hang off the main window.
     */
    @Test
    public void testSpeedMultiplierAsksOverTheWindow() throws Exception
    {
        Function<JPopupMenu, JMenuItem> door = counted("autolayout.ui.menuSpeedMultiplier");

        String prompt = I18n.f("autolayout.ui.promptEnterSpeedMultiplier", session.describeTile(station));

        Object was = session.getPointProperty(station, "speedMultiplier");

        AskingWindows.assertAsked(bothWays(station, door, message -> cancel(), input(), JOptionPane.QUESTION_MESSAGE, null,
            ok(), UIManager.getString("OptionPane.cancelButtonText")), page, input(), prompt, JOptionPane.QUESTION_MESSAGE,
            null, ok(), UIManager.getString("OptionPane.cancelButtonText"));

        assertEquals(session.getPointProperty(station, "speedMultiplier"), was, "Cancel wrote the multiplier");

        String invalid = I18n.t("autolayout.ui.errorInvalidSpeedMultiplier");

        assertARefusal(station, door, prompt, "999", invalid);
        assertARefusal(station, door, prompt, "many", invalid);

        assertEquals(session.getPointProperty(station, "speedMultiplier"), was, "a refused number was written");

        press(page, station, door, message -> Answering.type("150"), 1);

        assertEquals(((Number) session.getPointProperty(station, "speedMultiplier")).doubleValue(), 1.5, 0.001,
            "the answer over the page's window was not written");

        press(page, station, door, message -> Answering.type("100"), 1);
    }

    /**
     * Max Train Length asks over the window, Cancel writes nothing, and a number that is none is refused over it.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and
     * they hang off the main window.
     */
    @Test
    public void testMaxTrainLengthAsksOverTheWindow() throws Exception
    {
        Function<JPopupMenu, JMenuItem> door = menu ->
        {
            int maximum = ((Number) (session.getPointProperty(station, "maxTrainLength") == null ? 0
                : session.getPointProperty(station, "maxTrainLength"))).intValue();

            return AskingWindows.itemOf(menu, I18n.f("autolayout.ui.menuMaxTrainLength",
                maximum == 0 ? I18n.t("autolayout.ui.any") : String.valueOf(maximum)));
        };

        String prompt = I18n.f("autolayout.ui.promptEnterMaxTrainLength", session.describeTile(station));

        Object was = session.getPointProperty(station, "maxTrainLength");

        bothWays(station, door, message -> cancel(), input(), JOptionPane.QUESTION_MESSAGE, null, ok(),
            UIManager.getString("OptionPane.cancelButtonText"));

        assertEquals(session.getPointProperty(station, "maxTrainLength"), was, "Cancel wrote the length");

        assertARefusal(station, door, prompt, "long", I18n.f("autosetup.ui.errorNotANumber", "long"));

        assertARefusal(station, door, prompt, "-3", null);

        assertEquals(session.getPointProperty(station, "maxTrainLength"), was, "a refused length was written");
    }

    // ---------------------------------------------------------------- the name

    /**
     * Rename asks over the window; a quote mark is warned about over it, another square's name refused over it.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and
     * they hang off the main window.
     */
    @Test
    public void testRenameAsksOverTheWindow() throws Exception
    {
        Function<JPopupMenu, JMenuItem> door = item(I18n.t("autosetup.ui.menuRename"));

        String prompt = I18n.t("autosetup.ui.promptPointName");

        String name = session.getStore().getPointName(station);

        bothWays(station, door, message -> cancel(), input(), JOptionPane.QUESTION_MESSAGE, null, ok(),
            UIManager.getString("OptionPane.cancelButtonText"));

        assertEquals(session.getStore().getPointName(station), name, "Cancel renamed it");

        assertARefusal(station, door, prompt, "BottomMainA", null);

        assertEquals(session.getStore().getPointName(station), name, "another's name was taken");

        // Quotes stripped, it is the name it already has, so nothing changes
        for (Window window : new Window[] {ui, page})
        {
            List<Answering.Question> asked = press(window, station, door,
                message -> message.equals(prompt) ? Answering.type(name + "\"") : null, 2);

            assertSame(asked.get(1).owner, window, "the warning about quotes hangs off the wrong window");

            assertEquals(asked.get(1).message, I18n.t("autosetup.ui.warnQuotesStrippedFromName"));
        }

        assertEquals(session.getStore().getPointName(station), name, "the stripped name is not the name");
    }

    /**
     * A sensor made a station asks its name over the window, and Cancel leaves it as it was.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test
    public void testANewStationsNameIsAskedOverTheWindow() throws Exception
    {
        TileKey nameless = aSquare(each -> each.isFeedback()
            && !session.getStore().isStation(new TileKey(station.getPage(), each.getX(), each.getY()))
            && session.getStore().getPointName(new TileKey(station.getPage(), each.getX(), each.getY())) == null);

        bothWays(nameless, item(I18n.t("autosetup.ui.menuCanStop")), message -> cancel(), input(),
            JOptionPane.QUESTION_MESSAGE, null, ok(), UIManager.getString("OptionPane.cancelButtonText"));

        assertFalse(session.getStore().isStation(nameless), "Cancel made it a station");
    }

    // ---------------------------------------------------------------- homes and placing

    /**
     * Home asks over the window, starting with what it offers, and Cancel leaves the home alone.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test(priority = -1)
    public void testHomeAsksOverTheWindow() throws Exception
    {
        Function<JPopupMenu, JMenuItem> door = menu -> AskingWindows.hasItem(menu, I18n.t("autosetup.ui.menuHomeNone"))
            ? AskingWindows.itemOf(menu, I18n.t("autosetup.ui.menuHomeNone"))
            : AskingWindows.itemOf(menu, I18n.f("autosetup.ui.menuHomeFor", session.getPointProperty(station, "home")));

        assertCancelKeeps("home", door, I18n.t("autosetup.ui.menuHomeNone"));
    }

    /**
     * Add to Autonomy asks over the window, Cancel placing nothing; with every locomotive already in autonomy it says so
     * over the window.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and
     * they hang off the main window.
     */
    @Test(priority = -1)
    public void testAddToAutonomyAsksOverTheWindow() throws Exception
    {
        Function<JPopupMenu, JMenuItem> door = item(I18n.t("autosetup.ui.menuAddToAutonomy"));

        String standing = String.valueOf(session.getLocomotiveNameAt(station));

        pickingBothWays(station, door, message -> cancel(), I18n.t("autosetup.ui.menuAddToAutonomy"), JOptionPane.PLAIN_MESSAGE, null,
            ok(), UIManager.getString("OptionPane.cancelButtonText"));

        assertEquals(String.valueOf(session.getLocomotiveNameAt(station)), standing, "Cancel placed something");
    }

    // ---------------------------------------------------------------- the advanced parameters

    /**
     * Blocked by Points asks over the window, and Cancel writes nothing.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test
    public void testBlockedByPointsAsksOverTheWindow() throws Exception
    {
        List<TileKey> was = new ArrayList<>(session.getStore().getBlockingPoints(station));

        bothWays(station, counted("autolayout.ui.menuBlockedByPoints"), message -> cancel(),
            I18n.t("autosetup.ui.menuBlockedByPointsTitle"), JOptionPane.PLAIN_MESSAGE, ok(), ok(),
            I18n.t("autosetup.ui.optionPickBlockerOnDiagram"), UIManager.getString("OptionPane.cancelButtonText"));

        assertEquals(new ArrayList<>(session.getStore().getBlockingPoints(station)), was, "Cancel changed them");
    }

    /**
     * Excluded Locomotives asks over the window, and Cancel writes nothing; with no locomotives it says so over the
     * window; excluding the station's home locomotive is warned about over the window, and No writes nothing.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and
     * they hang off the main window.
     */
    @Test(priority = -1)
    public void testExcludedLocomotivesAsksOverTheWindow() throws Exception
    {
        Function<JPopupMenu, JMenuItem> door = counted("autolayout.ui.menuExcludedLocomotives");

        Object was = String.valueOf(session.getPointProperty(station, "excludedLocs"));

        assertCancelKeeps("excludedLocs", door, I18n.t("autosetup.ui.labelLocomotiveExclusions"));

        AskingWindows.withOnlyTheseLocomotives(model, Collections.emptyList(), () ->
        {
            AskingWindows.assertSaid(press(page, station, door, message -> null, 1).get(0), page, I18n.t("error.noLocs"));
        });

        final String home = model.getLocList().get(0);

        session.setHome(station, home);

        try
        {
            String warned = I18n.f("autolayout.ui.confirmExcludingHome", home, session.describeTile(station));

            for (Window window : new Window[] {ui, page})
            {
                List<Answering.Question> asked = press(window, station, door, message ->
                {
                    if (message.equals(warned)) return Answering.press(NO);

                    Components.onTheEventThread(() ->
                    {
                        for (Window each : Window.getWindows())
                        {
                            if (!(each instanceof JDialog) || !each.isShowing()) continue;

                            @SuppressWarnings("unchecked")
                            JList<String> list = Components.find(each, JList.class, any -> true);

                            if (list != null) list.setSelectedValue(home, true);
                        }

                        return null;
                    });

                    return Answering.pressAt(0);
                }, 2);

                AskingWindows.assertAsked(asked.get(1), window, I18n.t("autosetup.ui.labelExcludedLocs"), warned,
                    JOptionPane.PLAIN_MESSAGE, NO, YES, NO);
            }

            assertEquals(String.valueOf(session.getPointProperty(station, "excludedLocs")), was, "No wrote the list");
        }
        finally
        {
            session.setHome(station, null);
        }
    }

    /**
     * Set Length asks over the window, and Cancel writes nothing.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test
    public void testSetLengthAsksOverTheWindow() throws Exception
    {
        TileKey track = aSquare(each -> each.getType() == LayoutDiagramComponent.componentType.STRAIGHT
            && !session.getStore().isStation(new TileKey(station.getPage(), each.getX(), each.getY())));

        int was = session.getStore().getTileLength(track);

        for (Window window : new Window[] {ui, page})
        {
            AskingWindows.assertAsked(press(window, track, item(I18n.t("autosetup.ui.menuSetLength")),
                message -> Answering.pressAt(2), 1).get(0), window, I18n.t("autosetup.ui.menuSetLength"),
                AskingWindows.ANY_MESSAGE, JOptionPane.PLAIN_MESSAGE, ok(), ok(), I18n.t("autosetup.ui.optionClearLength"),
                UIManager.getString("OptionPane.cancelButtonText"));
        }

        assertEquals(session.getStore().getTileLength(track), was, "Cancel wrote the length");
    }

    // ---------------------------------------------------------------- Bulk Tools, last: Yes changes the setup

    /** A Bulk Tools door whose title is its item's words, answered No from both windows. */
    private static void assertNoFromBothWays(String menuKey, int count) throws Exception
    {
        bothWays(station, counted(menuKey), message -> Answering.press(NO), I18n.f(menuKey, count),
            JOptionPane.WARNING_MESSAGE, NO, YES, NO);
    }

    /** A Bulk Tools door about this many things, whose No leaves what `state` reads as it was. */
    private static void assertNoKeeps(String menuKey, int count, java.util.function.Supplier<Object> state) throws Exception
    {
        assertTrue(count > 0, "precondition: nothing for " + menuKey + " to change");

        Object was = state.get();

        assertNoFromBothWays(menuKey, count);

        assertEquals(state.get(), was, "No changed something");
    }

    /** A Bulk Tools door, No from both windows and then Yes from the page's; the count of what it changes goes. */
    private static void assertBulk(String menuKey, java.util.function.IntSupplier changed) throws Exception
    {
        int before = changed.getAsInt();

        assertTrue(before > 0, "precondition: nothing for " + menuKey + " to change");

        assertNoFromBothWays(menuKey, before);

        assertEquals(changed.getAsInt(), before, "No changed something");

        assertSame(press(page, station, counted(menuKey), message -> Answering.press(YES), 1).get(0).owner, page,
            "the question is not the page's");

        assertTrue(Components.waitFor(() -> changed.getAsInt() != before, PATIENCE_MS), "Yes changed nothing");
    }

    /**
     * Allow Every Path, Clear All Track Lengths and Clear All Max Train Lengths ask over the window; No changes nothing and
     * Yes does what each says.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and they
     * hang off the main window.
     */
    @Test(priority = 1)
    public void testTheWholeLayoutToolsAskOverTheWindow() throws Exception
    {
        assertBulk("autolayout.ui.menuAllowEveryPath", () -> session.routesNotOpenBothWays().size());

        assertBulk("autolayout.ui.menuClearAllTrackLengths", () -> session.tilesWithALength().size());

        session.setPointProperty(station, "maxTrainLength", 3);

        assertBulk("autolayout.ui.menuClearAllMaxTrainLengths", () -> session.tilesWithAMaxTrainLength().size());
    }

    /**
     * Place All at Their Homes asks over the window; No moves nothing.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test(priority = 2)
    public void testPlaceAllAtTheirHomesAsksOverTheWindow() throws Exception
    {
        assertNoKeeps("autolayout.ui.menuPlaceEveryTrainAtHome", session.homesToPlace().size(),
            () -> new ArrayList<>(session.placementsAutonomyWillWrite().entrySet()));
    }

    /**
     * Home Every Train Where It Stands asks over the window; No sets nothing.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test(priority = 3)
    public void testHomeEveryTrainAsksOverTheWindow() throws Exception
    {
        assertNoKeeps("autolayout.ui.menuHomeEveryTrainWhereItStands", session.trainsWhereTheyStand().size(),
            () -> new ArrayList<>(session.homesToPlace().entrySet()));
    }

    /**
     * Clear All Home Locomotives asks over the window; No clears nothing and Yes clears them.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test(priority = 4)
    public void testClearAllHomesAsksOverTheWindow() throws Exception
    {
        assertBulk("autolayout.ui.menuClearAllHomeLocomotives", () -> session.tilesWithAHome().size());
    }

    /**
     * Clear Locomotives asks over the window; No lifts nothing and Yes lifts them.  Then, with nothing placed, Home says
     * there is no locomotive to choose, over the window.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and
     * they hang off the main window.
     */
    @Test(priority = 5)
    public void testClearLocomotivesAsksOverTheWindow() throws Exception
    {
        assertBulk("autolayout.ui.menuClearLocomotives", () -> session.placementsAutonomyWillWrite().size());

        Function<JPopupMenu, JMenuItem> home = item(I18n.t("autosetup.ui.menuHomeNone"));

        AskingWindows.assertSaid(press(page, station, home, message -> null, 1).get(0), page, I18n.t("error.noLocs"));
        AskingWindows.assertSaid(press(ui, station, home, message -> null, 1).get(0), ui, I18n.t("error.noLocs"));
    }

    /**
     * With no train left to offer, none placed elsewhere and none in the database, Add to Autonomy says so over the window.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test(priority = 6)
    public void testAddToAutonomyWithNoTrainSaysSoOverTheWindow() throws Exception
    {
        if (session.placementsAutonomyWillWrite().size() > 0)
        {
            press(ui, station, counted("autolayout.ui.menuClearLocomotives"), message -> Answering.press(YES), 1);
        }

        assertTrue(Components.waitFor(() -> session.placementsAutonomyWillWrite().isEmpty(), PATIENCE_MS),
            "precondition: trains are still placed");

        AskingWindows.withOnlyTheseLocomotives(model, Collections.emptyList(), () ->
        {
            for (Window window : new Window[] {ui, page})
            {
                AskingWindows.assertSaid(press(window, station, item(I18n.t("autosetup.ui.menuAddToAutonomy")),
                    message -> null, 1).get(0), window, I18n.t("autosetup.ui.infoAllLocomotivesInAutonomy"));
            }
        });
    }
}
