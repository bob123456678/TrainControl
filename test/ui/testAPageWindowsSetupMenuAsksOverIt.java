package ui;

import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
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
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.autonomyeditor.TrainWindow;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Mouse;
import support.StationDoors;

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

    /** Station Settings as this window's own menu opens it, hanging off that window. */
    private static StationSettingsWindow settingsFrom(Window window, TileKey square) throws Exception
    {
        Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, window, square), I18n.t("autosetup.ui.menuStationSettings")));

        Components.settle();

        StationSettingsWindow opened = StationDoors.showing(StationSettingsWindow.class);

        assertNotNull(opened, "Station Settings did not open from the window");

        assertSame(opened.getOwner(), window, "Station Settings hangs off the wrong window");

        return opened;
    }

    /** A control of Station Settings pressed from each window; its one question comes back over that window's settings. */
    private static void asksOverTheSettings(TileKey square, String control, Function<String, Answering.Answer> rule,
        String title, int type, String startsOn, String... buttons) throws Exception
    {
        for (Window window : new Window[] {ui, page})
        {
            StationSettingsWindow settings = settingsFrom(window, square);

            try (Answering answering = Answering.start(rule))
            {
                Components.press(StationDoors.part(settings, control, javax.swing.AbstractButton.class));

                assertTrue(answering.awaitAsked(1, PATIENCE_MS), control + " asked nothing");

                Components.settle();

                AskingWindows.assertAsked(answering.questions().get(0), settings, title, AskingWindows.ANY_MESSAGE, type,
                    startsOn, buttons);
            }

            StationDoors.close(settings);
        }
    }

    /** A number typed into a field of each window's settings, refused beside the field, and nothing written. */
    private static void assertARefusal(String field, String problem, String property, String typed, String said) throws Exception
    {
        Object was = session.getPointProperty(station, property);

        for (Window window : new Window[] {ui, page})
        {
            StationSettingsWindow settings = settingsFrom(window, station);

            StationDoors.typeAndEnter(settings, field, typed);

            assertTrue(!StationDoors.said(settings, problem).isEmpty(), "\"" + typed + "\" was not refused");

            if (said != null) assertEquals(StationDoors.said(settings, problem), said, "the refusal of \"" + typed + "\" changed");

            assertEquals(StationDoors.text(settings, field), typed, "the refusal took back what was typed");

            assertEquals(session.getPointProperty(station, property), was, "a refused value was written");

            // Back, or closing would try it again
            StationDoors.typeAndEnter(settings, field, was == null ? "" : String.valueOf(was));

            StationDoors.close(settings);
        }
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
     * Speed Into Here's refusals are said beside it in the window that hangs off the one asked from, and a number writes it.
     *
     * MUTATION: have `StationWindows.station` hang the window off `dialogParent()`, and it hangs off the main window.
     */
    @Test
    public void testSpeedMultiplierHangsOffTheWindow() throws Exception
    {
        String invalid = I18n.t("autolayout.ui.errorInvalidSpeedMultiplier");

        assertARefusal("station.speed", "station.speedProblem", "speedMultiplier", "999", invalid);
        assertARefusal("station.speed", "station.speedProblem", "speedMultiplier", "many", invalid);

        StationSettingsWindow settings = settingsFrom(page, station);

        StationDoors.typeAndEnter(settings, "station.speed", "150");

        assertEquals(((Number) session.getPointProperty(station, "speedMultiplier")).doubleValue(), 1.5, 0.001,
            "the number in the page's window was not written");

        StationDoors.typeAndEnter(settings, "station.speed", "100");

        StationDoors.close(settings);
    }

    /**
     * Maximum Train Length's refusals are said beside it, in the window that hangs off the one asked from.
     *
     * MUTATION: have `StationWindows.station` hang the window off `dialogParent()`, and it hangs off the main window.
     */
    @Test
    public void testMaxTrainLengthHangsOffTheWindow() throws Exception
    {
        assertARefusal("station.maxLength", "station.maxLengthProblem", "maxTrainLength", "long",
            I18n.f("autosetup.ui.errorNotANumber", "long"));

        assertARefusal("station.maxLength", "station.maxLengthProblem", "maxTrainLength", "-3", null);
    }

    // ---------------------------------------------------------------- the name

    /**
     * Another square's name is refused beside the field; a quote mark is taken out of the name.
     *
     * MUTATION: have `StationWindows.station` hang the window off `dialogParent()`, and it hangs off the main window.
     */
    @Test
    public void testRenameHangsOffTheWindow() throws Exception
    {
        String name = session.getStore().getPointName(station);

        for (Window window : new Window[] {ui, page})
        {
            StationSettingsWindow settings = settingsFrom(window, station);

            StationDoors.typeAndEnter(settings, "station.name", "BottomMainA");

            assertTrue(!StationDoors.said(settings, "station.nameProblem").isEmpty(), "another's name was not refused");

            assertEquals(session.getStore().getPointName(station), name, "another's name was taken");

            // Quotes stripped, it is the name it already has, so nothing changes
            StationDoors.typeAndEnter(settings, "station.name", name + "\"");

            assertEquals(session.getStore().getPointName(station), name, "the stripped name is not the name");

            assertEquals(StationDoors.text(settings, "station.name"), name, "the field still shows the quote");

            StationDoors.close(settings);
        }
    }

    /**
     * A sensor made a station asks its name over Station Settings, and Cancel leaves it as it was.
     *
     * MUTATION: have `pressedFrom` skip setting `askingWindow`, and it hangs off the window behind.
     */
    @Test
    public void testANewStationsNameIsAskedOverTheWindow() throws Exception
    {
        TileKey nameless = aSquare(each -> each.isFeedback()
            && !session.getStore().isStation(new TileKey(station.getPage(), each.getX(), each.getY()))
            && session.getStore().getPointName(new TileKey(station.getPage(), each.getX(), each.getY())) == null);

        asksOverTheSettings(nameless, "station.stops", message -> cancel(), input(), JOptionPane.QUESTION_MESSAGE, null, ok(),
            UIManager.getString("OptionPane.cancelButtonText"));

        assertFalse(session.getStore().isStation(nameless), "Cancel made it a station");
    }

    // ---------------------------------------------------------------- homes and placing

    /**
     * Home Locomotive given a train homed elsewhere asks over Station Settings, and No leaves the homes alone.
     *
     * MUTATION: have `pressedFrom` skip setting `askingWindow`, and it hangs off the window behind.
     */
    @Test(priority = -1)
    public void testHomeAsksOverTheWindow() throws Exception
    {
        String train = null;
        TileKey elsewhere = null;

        for (java.util.Map.Entry<TileKey, String> home : session.homesToPlace().entrySet())
        {
            if (!home.getKey().equals(station) && session.placementsAutonomyWillWrite().containsValue(home.getValue()))
            {
                train = home.getValue();
                elsewhere = home.getKey();
            }
        }

        assertNotNull(train, "precondition: no standing train is homed away from " + station);

        Object was = session.getPointProperty(station, "home");

        String asked = I18n.f("autolayout.ui.confirmHomeIsAlreadySet", train, session.describeTile(elsewhere));

        for (Window window : new Window[] {ui, page})
        {
            StationSettingsWindow settings = settingsFrom(window, station);

            List<Answering.Question> questions = StationDoors.pickAndAnswer(settings, "station.home", train,
                message -> message.equals(asked) ? Answering.press(NO) : Answering.pressAt(0));

            assertFalse(questions.isEmpty(), "a second home asked nothing");

            assertSame(questions.get(questions.size() - 1).owner, settings, "the question hangs off the wrong window");

            StationDoors.close(settings);
        }

        assertEquals(session.getPointProperty(station, "home"), was, "No changed the home here");

        assertEquals(session.getPointProperty(elsewhere, "home"), train, "No changed the home there");
    }

    /**
     * Train at..., from the window's own menu, opens the train window over that window, and looking places nothing.
     *
     * MUTATION: have `StationWindows.train` hang the window off `dialogParent()`, and it hangs off the main window.
     */
    @Test(priority = -1)
    public void testTheTrainWindowHangsOffTheWindow() throws Exception
    {
        String standing = String.valueOf(session.getLocomotiveNameAt(station));

        for (Window window : new Window[] {ui, page})
        {
            JPopupMenu menu = AskingWindows.menuOn(ui, window, station);

            Mouse.choose(AskingWindows.itemOf(menu, session.getLocomotiveNameAt(station) == null
                ? I18n.t("autosetup.ui.menuPlaceHere") : I18n.f("autosetup.ui.menuTrainAt", session.describeTile(station))));

            Components.settle();

            TrainWindow opened = StationDoors.showing(TrainWindow.class);

            assertNotNull(opened, "the train window did not open");

            assertSame(opened.getOwner(), window, "the train window hangs off the wrong window");

            StationDoors.close(opened);
        }

        assertEquals(String.valueOf(session.getLocomotiveNameAt(station)), standing, "looking placed something");
    }

    // ---------------------------------------------------------------- the advanced parameters

    /**
     * Unavailable While asks over Station Settings, and Cancel writes nothing.
     *
     * MUTATION: have `pressedFrom` skip setting `askingWindow`, and it hangs off the window behind.
     */
    @Test
    public void testBlockedByPointsAsksOverTheWindow() throws Exception
    {
        List<TileKey> was = new ArrayList<>(session.getStore().getBlockingPoints(station));

        asksOverTheSettings(station, "station.unavailableChoose", message -> cancel(),
            I18n.t("autosetup.ui.menuBlockedByPointsTitle"), JOptionPane.PLAIN_MESSAGE, ok(), ok(),
            I18n.t("autosetup.ui.optionPickBlockerOnDiagram"), UIManager.getString("OptionPane.cancelButtonText"));

        assertEquals(new ArrayList<>(session.getStore().getBlockingPoints(station)), was, "Cancel changed them");
    }

    /**
     * Blacklist on an empty list asks over Station Settings, and Cancel writes nothing; with no locomotives it says so over
     * it; keeping out the station's home locomotive is warned about over it, and No writes nothing.
     *
     * MUTATION: have `pressedFrom` skip setting `askingWindow`, and they hang off the window behind.
     */
    @Test(priority = -1)
    public void testExcludedLocomotivesAsksOverTheWindow() throws Exception
    {
        Object was = String.valueOf(session.getPointProperty(station, "excludedLocs"));

        asksOverTheSettings(station, "station.whichBlack", message -> cancel(), I18n.t("autosetup.ui.labelLocomotiveExclusions"),
            JOptionPane.PLAIN_MESSAGE, null, ok(), UIManager.getString("OptionPane.cancelButtonText"));

        assertEquals(String.valueOf(session.getPointProperty(station, "excludedLocs")), was, "Cancel changed the list");

        AskingWindows.withOnlyTheseLocomotives(model, Collections.emptyList(), () ->
        {
            StationSettingsWindow settings = settingsFrom(page, station);

            try (Answering answering = Answering.start(message -> null))
            {
                Components.press(StationDoors.part(settings, "station.whichBlack", javax.swing.AbstractButton.class));

                assertTrue(answering.awaitAsked(1, PATIENCE_MS), "no locomotives said nothing");

                AskingWindows.assertSaid(answering.questions().get(0), settings, I18n.t("error.noLocs"));
            }

            StationDoors.close(settings);
        });

        final String home = model.getLocList().get(0);

        session.setHome(station, home);

        try
        {
            String warned = I18n.f("autolayout.ui.confirmExcludingHome", home, session.describeTile(station));

            for (Window window : new Window[] {ui, page})
            {
                StationSettingsWindow settings = settingsFrom(window, station);

                List<String> asked = StationDoors.press(settings, "station.whichBlack", message ->
                    message.equals(warned) ? Answering.press(NO) : StationDoors.choosing(home));

                assertEquals(asked.size(), 2, "keeping out the home locomotive did not warn: " + asked);

                assertEquals(asked.get(1), warned, "the warning changed");

                StationDoors.close(settings);
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
     * Clear Locomotives asks over the window; No lifts nothing and Yes lifts them.  Then, with nothing placed, Home
     * Locomotive offers none but None.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and
     * they hang off the main window.
     */
    @Test(priority = 5)
    public void testClearLocomotivesAsksOverTheWindow() throws Exception
    {
        assertBulk("autolayout.ui.menuClearLocomotives", () -> session.placementsAutonomyWillWrite().size());

        StationSettingsWindow settings = settingsFrom(page, station);

        assertEquals(StationDoors.part(settings, "station.home", javax.swing.JComboBox.class).getItemCount(), 1,
            "Home Locomotive offers a train though none is placed");

        StationDoors.close(settings);
    }

    /**
     * With no train left to offer, none placed elsewhere and none in the database, the train window's list offers only None.
     *
     * MUTATION: `dialogOwner` for `((TrainControlUI) dialogOwner).dialogParent()` in `AutonomyEditorPanel.owner`, and it
     * hangs off the main window.
     */
    @Test(priority = 6)
    public void testTheTrainListOffersNoneWithNoTrain() throws Exception
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
                Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, window, station),
                    I18n.t("autosetup.ui.menuPlaceHere")));

                Components.settle();

                TrainWindow opened = StationDoors.showing(TrainWindow.class);

                assertNotNull(opened, "the train window did not open");

                assertSame(opened.getOwner(), window, "the train window hangs off the wrong window");

                assertEquals(StationDoors.part(opened, "train.train", javax.swing.JComboBox.class).getItemCount(), 1,
                    "the list offers a train where none exists");

                StationDoors.close(opened);
            }
        });
    }
}
