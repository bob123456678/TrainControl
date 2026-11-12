package ui;

import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.AskingWindows;
import support.Components;
import support.Mouse;
import support.Reflect;
import support.StationDoors;

/**
 * The train window and the menus that lead to it and to Station Settings (FR-111), pressed as the user presses them on
 * the frozen railway.
 *
 * @author Adam
 */
public class testTheTrainWindow
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static org.json.JSONObject asOpened;

    private static final String PAGE = "1 - Main";

    private LayoutEditor editor;

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

        asOpened = Components.onTheEventThread(() -> session.snapshotSetup());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    @BeforeMethod
    public void putTheSetupBack() throws Exception
    {
        support.FrozenEditor.putBack(ui, session, asOpened);
    }

    @AfterMethod(alwaysRun = true)
    public void closeWhatIsLeft() throws Exception
    {
        support.FrozenEditor.dispose(editor);

        editor = null;

        AskingWindows.closeEveryDialog();
    }

    private AutonomyEditorPanel theEditorsPanel() throws Exception
    {
        editor = support.FrozenEditor.editorOn(model, ui, session, PAGE);

        return Components.onTheEventThread(() -> editor.getAutonomyPanel());
    }

    private static TileKey aSquareWithATrain()
    {
        for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
        {
            // The setup's own train, not one only the railway holds there, such as a train put at its home
            if (PAGE.equals(each.getKey().getPage()) && session.getStore().isStation(each.getKey())
                && each.getValue().equals(session.getLocomotiveNameAt(each.getKey()))) return each.getKey();
        }

        throw new AssertionError("precondition: no train stands on a station of " + PAGE);
    }

    private static TileKey anEmptyStation()
    {
        for (TileKey each : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(each.getPage()) && session.getStore().isStation(each) && session.getLocomotiveNameAt(each) == null)
            {
                return each;
            }
        }

        throw new AssertionError("precondition: no empty station on " + PAGE);
    }

    private static TileKey aPlainSensor()
    {
        for (TileKey each : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(each.getPage()) && !session.getStore().isStation(each) && session.getLocomotiveNameAt(each) == null)
            {
                return each;
            }
        }

        throw new AssertionError("precondition: no sensor that is not a station on " + PAGE);
    }

    private static String aTrainNotPlaced()
    {
        for (String name : model.getLocList())
        {
            if (!session.placementsAutonomyWillWrite().containsValue(name)) return name;
        }

        throw new AssertionError("precondition: every locomotive is placed");
    }

    // ---------------------------------------------------------------------------------------------------- the window

    /**
     * Blue is for section headings and the Train tab has none: its row labels (Train, Active, Facing, Arrived from, Tail
     * lies) are black Semibold 13, as the Settings tab's are, and nothing in it is blue.
     *
     * MUTATION: build a row label with `UiStyle.label`, which is blue, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheRowLabelsAreBlack() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        java.awt.Container form = StationDoors.part(window, "train.form", java.awt.Container.class);

        java.util.List<String> blue = new java.util.ArrayList<>();

        for (javax.swing.JLabel each : Components.all(form, javax.swing.JLabel.class, null))
        {
            if (org.traincontrol.gui.widgets.UiStyle.BLUE.equals(each.getForeground())) blue.add(each.getText());
        }

        assertTrue(blue.isEmpty(), "blue in a tab with no section headings: " + blue);

        for (String key : new String[] {"autosetup.ui.trainTrain", "autosetup.ui.trainActive", "autosetup.ui.trainFacing",
            "autosetup.ui.trainArrived", "autosetup.ui.trainTail"})
        {
            java.util.List<javax.swing.JLabel> found = Components.all(form, javax.swing.JLabel.class,
                label -> I18n.t(key).equals(label.getText()));

            assertEquals(found.size(), 1, key + " is not one label of the tab");
            assertEquals(found.get(0).getForeground(), java.awt.Color.BLACK, key + " is not black");
            assertEquals(found.get(0).getFont(), org.traincontrol.gui.widgets.UiStyle.LABEL_FONT, key + " is not Semibold 13");
        }

        StationDoors.close(window);
    }

    /**
     * The train window's Locomotive Settings button wears the application's button font, Segoe UI Bold 12, as every other
     * window's buttons do (the tail's Pick button is held in testTheTailCanBeGivenInTheEditor).
     *
     * MUTATION: give `train.settings` a Plain 13 font again in `TrainTab`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheWindowsButtonsWearTheButtonFont() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        javax.swing.JButton settings = StationDoors.part(window, "train.settings", javax.swing.JButton.class);

        assertEquals(settings.getFont(), org.traincontrol.gui.widgets.UiStyle.BUTTON_FONT, "train.settings is not Bold 12");

        StationDoors.close(window);
    }

    /**
     * The train popup and Station Settings both end with Done Editing, not Close (Adam): what is changed there is saved
     * as it is made, so the button only says the editing is over.
     *
     * MUTATION: give `StationWindowBase.assemble`'s button `autosetup.ui.stationClose` again, and this fails.
     */
    @Test(timeOut = 120000)
    public void testBothWindowsEndWithDoneEditing() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        java.awt.Window train = StationDoors.openTrain(panel, square);

        assertEquals(StationDoors.part(train, "station.close", javax.swing.JButton.class).getText(),
            said("autosetup.ui.stationDone", null), "the train popup does not end with Done Editing");

        StationDoors.close(train);

        java.awt.Window station = StationDoors.open(panel, square);

        assertEquals(StationDoors.part(station, "station.close", javax.swing.JButton.class).getText(),
            said("autosetup.ui.stationDone", null), "Station Settings does not end with Done Editing");

        StationDoors.close(station);
    }

    /**
     * The train is a tab of Station Settings (FR-195; Adam: "change the 'change train at station' to just open the new
     * tab"): Change Train at... on the editor's menu brings forward the Station Settings already open and switches it to
     * its Train tab, Station Settings... switches it back, and Place a Locomotive Here... on an empty station opens the
     * Train tab too.  No second window opens.
     *
     * MUTATION: leave `showTab` out of `AutonomyEditorPanel.openTrainTab`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testChangeTrainOpensStationSettingsOnItsTrainTab() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();
        TileKey empty = anEmptyStation();

        StationSettingsWindow open = StationDoors.open(panel, full);

        assertFalse(StationDoors.showsTheTrainTab(open), "precondition: Station Settings opened on its Train tab");

        fromTheEditorsMenu(panel, full, I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(full)));

        assertSame(StationDoors.showing(StationSettingsWindow.class), open, "Change Train at did not bring forward the"
            + " Station Settings already open");

        assertTrue(StationDoors.showsTheTrainTab(open), "Change Train at did not switch Station Settings to its Train tab");

        assertEquals(stationWindowsShowing(), 1, "Change Train at opened a second window");

        fromTheEditorsMenu(panel, full, I18n.t("autosetup.ui.menuStationSettings"));

        assertFalse(StationDoors.showsTheTrainTab(open), "Station Settings... did not switch back to the Settings tab");

        StationDoors.close(open);

        fromTheEditorsMenu(panel, empty, I18n.t("autosetup.ui.menuPlaceHere"));

        StationSettingsWindow placing = StationDoors.showing(StationSettingsWindow.class);

        assertNotNull(placing, "Place a Locomotive Here did not open Station Settings");
        assertEquals(placing.squareForTest(), empty, "Place a Locomotive Here opened another square");
        assertTrue(StationDoors.showsTheTrainTab(placing), "Place a Locomotive Here did not open the Train tab");

        StationDoors.close(placing);
    }

    /**
     * Station Settings' tabs wear icons as Locomotive Settings' do (Adam: "a station icon and a locomotive icon, similar
     * to the style in the locomotive editor window"): the Settings tab the drawn station, the Train tab the locomotive
     * of Locomotive Settings' first tab, each beside its title.
     *
     * MUTATION: drop the icon from the Train tab's `tabs.addTab` in `StationSettingsWindow`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheTabsWearAStationAndALocomotive() throws Exception
    {
        StationSettingsWindow window = StationDoors.open(theEditorsPanel(), aSquareWithATrain());

        javax.swing.JTabbedPane tabs = StationDoors.part(window, "station.tabs", javax.swing.JTabbedPane.class);

        javax.swing.Icon[] expected = {org.traincontrol.gui.widgets.TabIcons.station(),
            org.traincontrol.gui.widgets.TabIcons.locomotive()};

        for (int i = 0; i < 2; i++)
        {
            final int tab = i;

            javax.swing.Icon icon = Components.onTheEventThread(() -> tabs.getIconAt(tab));

            assertNotNull(icon, "tab " + i + " has no icon");
            assertEquals(painted(icon), painted(expected[i]), "tab " + i + " does not wear its drawn icon");
            assertFalse(Components.onTheEventThread(() -> tabs.getTitleAt(tab)).isEmpty(), "tab " + i + " lost its title");
        }

        StationDoors.close(window);
    }

    /** An icon's pixels, as one string to compare. */
    private static String painted(javax.swing.Icon icon)
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        icon.paintIcon(null, image.getGraphics(), 0, 0);

        return icon.getIconWidth() + "x" + icon.getIconHeight() + java.util.Arrays.toString(
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    /** Clicks an item of the editor's menu on a square, as a right-click and a click do. */
    private static void fromTheEditorsMenu(AutonomyEditorPanel panel, TileKey square, String item) throws Exception
    {
        JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

        AbstractButton chosen = Components.withText(menu, item);

        assertNotNull(chosen, "the editor's menu on " + square + " has no \"" + item + "\"");

        Components.onTheEventThread(() -> { chosen.doClick(); return null; });

        Components.settle();
    }

    /** How many of the autonomy editor's square windows are showing. */
    private static int stationWindowsShowing()
    {
        return Components.onTheEventThread(() ->
        {
            int out = 0;

            for (java.awt.Window each : java.awt.Window.getWindows())
            {
                if (each.isShowing() && each.getClass().getPackage().getName().endsWith(".autonomyeditor")) out++;
            }

            return out;
        });
    }

    /**
     * Every (i) of the train popup explains its row in every state (OB-429): where no train stands, each says what its
     * row is and that no train stands here; beside Tail lies, where the tail is not known, it says what Tail lies means
     * and why it is not known here.
     *
     * MUTATION: give the tail mark no tooltip where `tailFacts` finds no question, as before, and this fails.
     */
    @Test(timeOut = 120000)
    public void testEveryMarkExplainsItsRowWithNoAnswer() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        for (TileKey square : new TileKey[] {anEmptyStation(), aSquareWithATrain()})
        {
            StationSettingsWindow window = StationDoors.openTrain(panel, square);

            try
            {
                java.util.List<String> marks = Components.onTheEventThread(() ->
                {
                    java.util.List<String> out = new java.util.ArrayList<>();

                    java.awt.Container form = Components.named(window, "train.form", java.awt.Container.class);

                    for (javax.swing.JLabel mark : Components.all(form, javax.swing.JLabel.class,
                        l -> l.getIcon() == org.traincontrol.gui.InfoMark.icon()))
                    {
                        out.add(mark.getToolTipText() == null ? "" : mark.getToolTipText().replaceAll("<[^>]*>", " ")
                            .replace("&nbsp;", " ").replaceAll("\\s+", " ").trim());
                    }

                    return out;
                });

                assertEquals(marks.size(), 4, "precondition: the popup has not its four (i) marks: " + marks);

                for (String each : marks)
                {
                    assertFalse(each.isEmpty(), "an (i) of the popup on " + square + " says nothing: " + marks);
                }

                String tail = marks.get(3);

                if (session.getLocomotiveNameAt(square) == null)
                {
                    assertTrue(tail.contains(said("autosetup.ui.flyNoTrain", null)), "the (i) beside Tail lies does not"
                        + " say no train stands here: " + tail);
                }
                else
                {
                    assertNotNull(Components.onTheEventThread(() -> Components.find(Components.named(window,
                        "train.tail", java.awt.Container.class), javax.swing.JLabel.class,
                        l -> I18n.t("autosetup.ui.tailCrossedNotKnown").equals(l.getText()))),
                        "precondition: the tail of the train on " + square + " is known");

                    assertTrue(tail.contains(I18n.t("autosetup.ui.flyTailNow").replaceAll("\\s+", " ")), "the (i)"
                        + " beside an unknown Tail lies does not say what Tail lies means: " + tail);
                }
            }
            finally
            {
                SwingUtilities.invokeAndWait(window::dispose);
            }
        }
    }

    /**
     * Opened and closed, the window changes no setup and dirties nothing.
     *
     * MUTATION: have `fill`'s `train.setSelectedItem` run outside `reading`, so `pressTrain` acts, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLookingChangesNothing() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        String before = session.snapshotSetup().toString();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        StationDoors.close(window);

        Components.settle();

        assertEquals(session.snapshotSetup().toString(), before, "looking at a train changed the setup");

        assertFalse(panel.isDirty(), "looking at a train dirtied the setup");
    }

    /**
     * The list puts a train on the square, and None takes it off.
     *
     * MUTATION: have `pressTrain` skip `removeLocomotive`, and None leaves the train there and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheListPlacesATrainAndNoneTakesItOff() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = anEmptyStation();

        String train = aTrainNotPlaced();

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        assertFalse(StationDoors.live(window, "train.active"), "Active takes clicks with no train to pause");

        Map<TileKey, String> others = session.placementsAutonomyWillWrite();

        StationDoors.pick(window, "train.train", train);

        assertEquals(session.getLocomotiveNameAt(square), train, "the list did not put the train on the square");

        assertTrue(StationDoors.live(window, "train.active"), "Active stays gray with a train here");

        others.put(square, train);

        assertEquals(session.placementsAutonomyWillWrite(), others, "placing moved another train");

        StationDoors.pick(window, "train.train", I18n.t("autosetup.ui.labelNone"));

        assertNull(session.getLocomotiveNameAt(square), "None left the train on the square");
    }

    /**
     * A key step in the Train list only shows a train: Down, or a typed letter, places nothing, Escape puts the list
     * back and leaves the window open, and Enter places the train shown.  A pick from the open list acts as the list
     * closes; a list closed by canceling puts the choice back.
     *
     * MUTATION: have `StationWindowBase.Choice.fireActionEvent` ignore `quiet`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAKeyStepInTheListPlacesNothingUntilEnter() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = anEmptyStation();

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        javax.swing.JComboBox<?> list = StationDoors.part(window, "train.train", javax.swing.JComboBox.class);

        String none = I18n.t("autosetup.ui.labelNone");

        support.Keys.press(list, java.awt.event.KeyEvent.VK_DOWN);

        Object shown = Components.onTheEventThread(list::getSelectedItem);

        assertFalse(none.equals(shown), "precondition: Down did not step the list");
        assertNull(session.getLocomotiveNameAt(square), "Down placed " + shown);

        // A typed letter steps to the next train whose name starts with it
        String last = String.valueOf(Components.onTheEventThread(() -> list.getItemAt(list.getItemCount() - 1)));
        char letter = Character.toUpperCase(last.charAt(0));

        if (letter >= 'A' && letter <= 'Z' || letter >= '0' && letter <= '9')
        {
            support.Keys.press(list, letter);

            assertNull(session.getLocomotiveNameAt(square), "typing " + letter + " placed "
                + Components.onTheEventThread(list::getSelectedItem));
        }

        support.Keys.press(list, java.awt.event.KeyEvent.VK_ESCAPE);

        assertEquals(Components.onTheEventThread(list::getSelectedItem), none, "Escape did not put the list back");
        assertTrue(Components.onTheEventThread(window::isShowing), "Escape on a stepped list closed the window");

        support.Keys.press(list, java.awt.event.KeyEvent.VK_DOWN);
        support.Keys.press(list, java.awt.event.KeyEvent.VK_ENTER);

        String placed = session.getLocomotiveNameAt(square);

        assertNotNull(placed, "Enter placed nothing");
        assertTrue(String.valueOf(shown).startsWith(placed), "Enter placed " + placed + ", not " + shown);

        // As a pick from the open list: chosen while it is open, acted on as it closes
        Components.onTheEventThread(() -> { list.setPopupVisible(true); list.setSelectedItem(none); return null; });

        assertEquals(session.getLocomotiveNameAt(square), placed, "a choice in the open list acted before it closed");

        Components.onTheEventThread(() -> { list.setPopupVisible(false); return null; });
        Components.settle();

        assertNull(session.getLocomotiveNameAt(square), "None picked from the list left the train there");

        Components.onTheEventThread(() ->
        {
            list.setPopupVisible(true);
            list.setSelectedIndex(1);
            list.firePopupMenuCanceled();
            list.setPopupVisible(false);

            return null;
        });
        Components.settle();

        assertNull(session.getLocomotiveNameAt(square), "a canceled list placed a train");
        assertEquals(Components.onTheEventThread(list::getSelectedItem), none, "a canceled list did not put the choice back");

        StationDoors.close(window);
    }

    /**
     * A train placed from the list records the side it came in by, in the setup and on the railway, as every door that
     * puts a train down by hand does (Adam: "the missing arrival side should be set - either from the data, by the
     * user, or randomly"): Tunnel's one way in is from the north, BottomMainB's from the west.  The squares are emptied
     * first, so nothing recorded before can answer.
     *
     * MUTATION: drop the `session.setArrivedFrom` from `AutonomyEditorPanel.placeLocomotive`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testAPlacedTrainRecordsTheSideItCameIn() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            for (String[] each : new String[][] {{"Tunnel", "N"}, {"BottomMainB", "W"}})
            {
                final TileKey square = support.StandingTrain.squareNamed(session, each[0]);

                assertEquals(Components.onTheEventThread(() -> session.unbarredArrivalSides(square)).toString(),
                    "[" + each[1] + "]", "precondition: " + each[0] + "'s ways in have changed");

                Components.onTheEventThread(() -> { session.placeLocomotive(square, null); return null; });

                Components.settle();

                String train = aTrainNotPlaced();

                StationSettingsWindow window = StationDoors.openTrain(panel, square);

                StationDoors.pick(window, "train.train", train);

                assertEquals(session.getLocomotiveNameAt(square), train, "the list did not put the train on " + each[0]);

                assertEquals(session.getArrivedFrom(square), each[1], "the train put on " + each[0] + " recorded no side,"
                    + " or the wrong one, in the setup");

                final Locomotive placed = model.getLocByName(train);

                assertTrue(Components.waitFor(() -> model.getAutoLayout().getLocomotiveLocation(placed) != null, 20000),
                    "the rebuild did not put " + train + " on the railway");

                Components.settle();

                assertEquals(model.getAutoLayout().getLocomotiveLocation(placed).getArrivedFrom(), each[1], "the train put"
                    + " on " + each[0] + " has no side, or the wrong one, on the railway");

                StationDoors.close(window);
            }
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);
        }
    }

    /**
     * Active reads the train, not the setup's list, so a card's pause shows at once (OB-346 part 2), in the window and on the
     * editor's tick; clicking it sets the train going.
     *
     * MUTATION: have `AutonomyEditorPanel.isPaused` read the setup's list, and the tick still says going and this fails.
     */
    @Test(timeOut = 120000)
    public void testActiveReadsTheTrain() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        Locomotive train = model.getLocByName(session.getLocomotiveNameAt(square));

        try
        {
            train.setAutonomyPaused(true);

            assertFalse(session.getPausedLocomotives().contains(train.getName()), "precondition: the setup knows of the pause");

            JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

            AbstractButton tick = Components.withText(menu, I18n.f("autolayout.ui.menuLocomotiveActive", train.getName()));

            assertNotNull(tick, "precondition: the editor's menu has no Is Active");

            assertFalse(tick.isSelected(), "the editor's tick reads going for a train its card paused");

            StationSettingsWindow window = StationDoors.openTrain(panel, square);

            assertFalse(StationDoors.on(window, "train.active"), "the window reads going for a train its card paused");

            StationDoors.press(window, "train.active");

            assertFalse(train.isAutonomyPaused(), "ticking Active did not set the train going");
        }
        finally
        {
            train.setAutonomyPaused(false);
        }
    }

    /**
     * During a run the window is read-only but for Active, which pauses the train alone (FR-059), and the setup stays as it
     * was.
     *
     * MUTATION: have `fill` gray Active with the rest, or `pressActive` go through `act`, which refuses, and this fails.
     */
    @Test(timeOut = 180000)
    public void testDuringARunOnlyActiveIsLive() throws Exception
    {
        Thread driving = null;

        try
        {
            Components.settle();

            Object[] dispatched = regression.testTheImportDoorReadsAnOldFile.dispatchATrain(ui, null);

            driving = (Thread) dispatched[3];

            TileKey square = null;

            for (Map.Entry<TileKey, String> each : session.trainsWhereTheyStand().entrySet())
            {
                if (!each.getValue().equals(dispatched[0]) && !ui.getModel().getAutoLayout().isAlreadyUnderway(
                    model.getLocByName(each.getValue())) && session.getStore().isStation(each.getKey())
                    && each.getValue().equals(session.getLocomotiveNameAt(each.getKey()))) square = each.getKey();
            }

            assertNotNull(square, "precondition: no other train stands on a station during the run");

            final TileKey standing = square;

            Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, ui, standing),
                I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(standing))));

            Components.settle();

            StationSettingsWindow window = StationDoors.showing(StationSettingsWindow.class);

            assertNotNull(window, "the train window did not open during a run");

            assertTrue(window.isReadOnly(), "the train window is not read-only during a run");

            assertFalse(StationDoors.live(window, "train.train"), "the list is live during a run");

            assertTrue(StationDoors.live(window, "train.active"), "Active is gray during a run");

            String before = session.snapshotSetup().toString();

            Locomotive train = model.getLocByName(session.getLocomotiveNameAt(standing));

            StationDoors.press(window, "train.active");

            try
            {
                assertTrue(train.isAutonomyPaused(), "unticking Active during a run did not pause the train");

                assertEquals(session.snapshotSetup().toString(), before, "pausing a train during a run edited the setup");
            }
            finally
            {
                train.setAutonomyPaused(false);
            }

            // And FR-059: the running menu ticks the train it stands on, as its card does
            JPopupMenu menu = AskingWindows.menuOn(ui, ui, standing);

            JMenuItem tick = AskingWindows.itemOf(menu, I18n.f("autolayout.ui.menuLocomotiveActive", train.getName()));

            assertTrue(tick.isSelected(), "the running menu reads paused for a train that is going");

            Mouse.choose(tick);

            Components.settle();

            assertTrue(train.isAutonomyPaused(), "the running menu's tick did not pause the train");

            train.setAutonomyPaused(false);

            assertTrue(AskingWindows.hasItem(AskingWindows.menuOn(ui, ui, standing), I18n.t("autosetup.ui.menuStationSettings")),
                "the running menu has no Station Settings");

            Mouse.closeMenus();
        }
        finally
        {
            if (driving != null) driving.interrupt();
        }
    }

    /** The train window on this square, opened from the track diagram's menu. */
    private static StationSettingsWindow theDiagramsTrainWindow(TileKey square) throws Exception
    {
        Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, ui, square),
            I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(square))));

        Components.settle();

        StationSettingsWindow window = StationDoors.showing(StationSettingsWindow.class);

        assertNotNull(window, "the train window did not open from the track diagram");

        return window;
    }

    /**
     * While a route runs with autonomy idle, the train window's Active pauses the train alone, as the track diagram's
     * Is Active does (FR-187): live, and the setup is as it was.
     *
     * MUTATION: gray Active in `TrainTab.fill` unless autonomy itself runs, and this fails.
     */
    @Test(timeOut = 180000)
    public void testWhileARouteRunsActivePausesTheTrainAlone() throws Exception
    {
        TileKey square = aSquareWithATrain();

        Locomotive train = model.getLocByName(session.getLocomotiveNameAt(square));

        StationSettingsWindow window = theDiagramsTrainWindow(square);

        assertTrue(StationDoors.on(window, "train.active"), "precondition: Active is not ticked");

        regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, true);

        try
        {
            assertTrue(Components.waitFor(() -> Components.onTheEventThread(window::isReadOnly), 5000),
                "the window is not read-only while a route runs");

            assertTrue(StationDoors.live(window, "train.active"), "Active is gray while a route runs");

            String before = Components.onTheEventThread(() -> session.snapshotSetup().toString());

            StationDoors.press(window, "train.active");

            assertTrue(train.isAutonomyPaused(), "unticking Active while a route runs did not pause the train");
            assertEquals(Components.onTheEventThread(() -> session.snapshotSetup().toString()), before,
                "Active while a route runs edited the setup");
        }
        finally
        {
            regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, false);

            train.setAutonomyPaused(false);

            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A train stepped to in the list is placed when the keyboard moves on in the window (Tab, another control clicked),
     * as a typed field applies on leaving, and the choice still shown when the window closes is applied too.
     *
     * MUTATION: drop the focus listener from `StationWindowBase.Choice`, or the lists from `commitFields`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLeavingTheListOrClosingTheWindowAppliesTheTrainShown() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = anEmptyStation();

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        javax.swing.JComboBox<?> list = StationDoors.part(window, "train.train", javax.swing.JComboBox.class);

        support.Keys.press(list, java.awt.event.KeyEvent.VK_DOWN);

        Object shown = Components.onTheEventThread(list::getSelectedItem);

        assertNull(session.getLocomotiveNameAt(square), "precondition: Down placed " + shown);

        // Tab takes the keyboard to the next control in the window
        StationDoors.loseKeyboard(list, false, StationDoors.part(window, "train.active", java.awt.Component.class));

        String placed = session.getLocomotiveNameAt(square);

        assertNotNull(placed, "leaving the list placed nothing");
        assertTrue(String.valueOf(shown).startsWith(placed), "leaving the list placed " + placed + ", not " + shown);

        support.Keys.press(list, java.awt.event.KeyEvent.VK_UP);

        assertEquals(session.getLocomotiveNameAt(square), placed, "precondition: Up took the train off");

        StationDoors.close(window);

        assertNull(session.getLocomotiveNameAt(square), "closing the window dropped None, shown in the list");
    }

    /**
     * A train stepped to in the list is put back, asking nothing, when another window is clicked, so a placement question
     * never swallows that click (the main window's Stop, the editor's Save).  Locomotive Settings... opens the train
     * standing, not the one shown.
     *
     * MUTATION: have the Choice's focus listener settle a loss for now, or the window's settle the lists, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAnotherWindowClickedPutsTheListBackAndAsksNothing() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();
        String standing = session.getLocomotiveNameAt(square);

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        javax.swing.JComboBox<?> list = StationDoors.part(window, "train.train", javax.swing.JComboBox.class);

        Object was = StationDoors.step(list);

        LocomotiveSettingsFrame frame = null;

        try (support.Answering answering = support.Answering.start(message -> null))
        {
            StationDoors.press(window, "train.settings");
            StationDoors.toAnotherWindow(window, list);

            frame = StationDoors.showing(LocomotiveSettingsFrame.class);

            assertEquals(answering.asked(), java.util.Collections.emptyList(), "another window clicked asked");
        }
        finally
        {
            if (frame != null) SwingUtilities.invokeAndWait(frame::dispose);
        }

        assertEquals(session.getLocomotiveNameAt(square), standing, "another window clicked placed the train shown");
        assertEquals(Components.onTheEventThread(list::getSelectedItem), was, "the list still shows a train not placed");
        assertNotNull(frame, "Locomotive Settings did not open");
        assertEquals(frame.getLocomotive(), model.getLocByName(standing), "Locomotive Settings opened on the train shown");

        StationDoors.close(window);
    }

    /**
     * In the autonomy editor's own train window, Active stays a setup edit, gray while a route runs: a pause of the
     * railway's train alone would be undone by the editor's draft (the track diagram's window pauses it).
     *
     * MUTATION: leave `editor.menuOnly` out of `TrainTab.pausesAlone`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testInTheEditorActiveIsGrayWhileARouteRuns() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        assertTrue(StationDoors.live(window, "train.active"), "precondition: Active is gray at rest");

        regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, true);

        try
        {
            assertTrue(Components.waitFor(() -> Components.onTheEventThread(window::isReadOnly), 5000),
                "precondition: the window is not read-only while a route runs");

            assertFalse(StationDoors.live(window, "train.active"), "the editor's Active is live while a route runs");
        }
        finally
        {
            regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, false);

            StationDoors.close(window);
        }
    }

    /**
     * Station Settings on a square with a train, opened from the track diagram's menu, with its watch stopped: a press
     * comes in the second before the watch looks again.
     */
    private static org.traincontrol.gui.autonomyeditor.StationSettingsWindow theDiagramsStationSettingsUnwatched()
        throws Exception
    {
        Mouse.choose(AskingWindows.itemOf(AskingWindows.menuOn(ui, ui, aSquareWithATrain()),
            I18n.t("autosetup.ui.menuStationSettings")));

        Components.settle();

        org.traincontrol.gui.autonomyeditor.StationSettingsWindow window =
            StationDoors.showing(org.traincontrol.gui.autonomyeditor.StationSettingsWindow.class);

        assertNotNull(window, "Station Settings did not open from the track diagram");

        Components.onTheEventThread(() ->
        {
            ((javax.swing.Timer) Reflect.get(window.getClass().getSuperclass(), window, "watch")).stop();

            return null;
        });

        return window;
    }

    /**
     * Station Settings opened from the track diagram goes, writing nothing, when a press comes after a configuration
     * was loaded and before its watch has looked again: the old setup is as it was.
     *
     * MUTATION: drop the `stillAbout` check from `StationWindowBase.refused`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testADiagramWindowWritesNothingOnceAnotherSetupIsLoaded() throws Exception
    {
        org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = theDiagramsStationSettingsUnwatched();

        assertTrue(StationDoors.live(window, "station.fullAutonomy"), "precondition: the box is gray");

        String before = Components.onTheEventThread(() -> session.snapshotSetup().toString());

        try
        {
            // A load replaces the main window's setup
            Components.onTheEventThread(() ->
            {
                Reflect.set(TrainControlUI.class, ui, "autonomySession", null);

                return null;
            });

            assertTrue(Components.onTheEventThread(() -> ui.getAutonomySession() != session), "precondition: no other setup");

            StationDoors.press(window, "station.fullAutonomy", message -> null);

            assertEquals(Components.onTheEventThread(() -> session.snapshotSetup().toString()), before,
                "a press after a load wrote to the setup the window opened on");
            assertFalse(Components.onTheEventThread(window::isShowing), "the window stayed open on a setup no longer loaded");
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                Reflect.set(TrainControlUI.class, ui, "autonomySession", session);
                window.dispose();

                return null;
            });
        }
    }

    /**
     * Station Settings opened from the track diagram refuses a press made once an editor has opened, before its watch
     * has looked again: said over the window, as a run's refusal is, and the setup is as it was.
     *
     * MUTATION: drop the editor's check from `StationWindowBase.refused`, and this fails.
     */
    @Test(timeOut = 180000)
    public void testADiagramWindowRefusesAPressOnceAnEditorOpens() throws Exception
    {
        org.traincontrol.gui.autonomyeditor.StationSettingsWindow window = theDiagramsStationSettingsUnwatched();

        assertFalse(window.isReadOnly(), "precondition: read-only at rest");

        editor = support.FrozenEditor.editorOn(model, ui, session, PAGE);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(ui::isLayoutEditorOpen), 10000),
            "precondition: the editor did not open");
        assertTrue(Components.onTheEventThread(window::isShowing), "precondition: the editor closed the window");
        assertTrue(StationDoors.live(window, "station.fullAutonomy"), "precondition: the box is gray");

        String before = Components.onTheEventThread(() -> session.snapshotSetup().toString());

        java.util.List<String> said = StationDoors.press(window, "station.fullAutonomy", message -> null);

        assertEquals(said, java.util.Collections.singletonList(I18n.t("autosetup.ui.menuEditorOpen")),
            "the press was not refused for the open editor");
        assertEquals(Components.onTheEventThread(() -> session.snapshotSetup().toString()), before,
            "a press with the editor open wrote to the setup");

        SwingUtilities.invokeAndWait(window::dispose);
    }

    /**
     * Hovering an (i) holds every tooltip up long enough to read, and only while the pointer is on it: a window closed
     * under the pointer, or a second arrival with no leaving between, still leaves the dismiss delay as it was.
     *
     * MUTATION: drop the hierarchy listener from `InfoMark.label`, and this fails.
     */
    @Test(timeOut = 60000)
    public void testAnInfoMarkPutsTheTooltipDelayBack() throws Exception
    {
        javax.swing.ToolTipManager tips = javax.swing.ToolTipManager.sharedInstance();

        int was = Components.onTheEventThread(tips::getDismissDelay);

        javax.swing.JLabel[] mark = new javax.swing.JLabel[1];

        javax.swing.JDialog shown = Components.onTheEventThread(() ->
        {
            TrainControlUI.installLookAndFeel();

            javax.swing.JDialog out = new javax.swing.JDialog();

            mark[0] = org.traincontrol.gui.InfoMark.label("a sentence");
            out.add(mark[0]);
            out.pack();
            out.setVisible(true);

            return out;
        });

        try
        {
            Mouse.enter(mark[0]);
            Mouse.enter(mark[0]);

            assertTrue(Components.onTheEventThread(tips::getDismissDelay) > was, "precondition: the (i) did not hold its tooltip up");

            Mouse.exit(mark[0]);

            assertEquals((int) Components.onTheEventThread(tips::getDismissDelay), was, "a second arrival kept the long delay");

            Mouse.enter(mark[0]);

            // Escape closes the window under the pointer: no exit comes
            SwingUtilities.invokeAndWait(shown::dispose);

            assertEquals((int) Components.onTheEventThread(tips::getDismissDelay), was,
                "a window closed under the pointer left every tooltip at the long delay");
        }
        finally
        {
            Components.onTheEventThread(() -> { tips.setDismissDelay(was); shown.dispose(); return null; });
        }
    }

    /**
     * Locomotive Settings opens that train's window on its Autonomy tab (FR-048), over the window that asked.
     *
     * MUTATION: have `pressSettings` open the Locomotive tab, as it did, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLocomotiveSettingsOpensThatTrain() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        StationSettingsWindow window = StationDoors.openTrain(panel, square);

        StationDoors.press(window, "train.settings");

        LocomotiveSettingsFrame frame = StationDoors.showing(LocomotiveSettingsFrame.class);

        assertNotNull(frame, "Locomotive Settings did not open");

        assertEquals(frame.getLocomotive(), model.getLocByName(session.getLocomotiveNameAt(square)),
            "Locomotive Settings opened on another locomotive");
        assertEquals(support.SettingsWindow.tabShowing(), LocomotiveSettingsFrame.Tab.AUTONOMY.ordinal(),
            "Locomotive Settings did not open on the Autonomy tab");

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * Locomotive Settings... sits in the lower left corner, on Done Editing's row at the form's left edge, and never sets
     * the window's width (Adam: "move the <wrench> locomotive settings label somewhere that doesn't widen the whole
     * window"; and to the lower left, on the close button's row).
     *
     * MUTATION: put the button back under the Train list in the form, and this fails.
     */
    @Test(timeOut = 120000)
    public void testLocomotiveSettingsDoesNotWidenTheWindow() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        try
        {
            int[] widths = Components.onTheEventThread(() ->
            {
                java.awt.Container root = window.getContentPane();
                java.awt.Container form = Components.named(window, "train.form", java.awt.Container.class);
                java.awt.Component close = Components.named(window, "station.close", java.awt.Component.class);
                java.awt.Component settings = Components.named(window, "train.settings", java.awt.Component.class);

                java.awt.Rectangle button = SwingUtilities.convertRectangle(settings.getParent(), settings.getBounds(), root);
                java.awt.Rectangle done = SwingUtilities.convertRectangle(close.getParent(), close.getBounds(), root);

                assertEquals(button.x, SwingUtilities.convertPoint(form, form.getInsets().left, 0, root).x,
                    "Locomotive Settings... is not at the form's left edge");

                assertTrue(button.y <= done.getCenterY() && done.getCenterY() <= button.y + button.height,
                    "Locomotive Settings... is not on Done Editing's row: " + button + " and " + done);

                assertTrue(button.x + button.width < done.x, "Locomotive Settings... is not left of Done Editing");

                window.pack();

                int with = window.getWidth();

                java.awt.Container holder = settings.getParent();

                holder.remove(settings);
                holder.revalidate();

                window.pack();

                return new int[] {with, window.getWidth()};
            });

            assertEquals(widths[0], widths[1], "Locomotive Settings... sets the train window's width");
        }
        finally
        {
            // Even after a failure: a window left open takes the class's later right-clicks
            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /**
     * A click on an (i) shows its tooltip at once, without the hover delay (Adam: "click on any i should immediately
     * show the tooltip, so you don't have to wait for the hover timeout"), and leaves every other component's delay as
     * it was.  The hover delay is set to a minute, so a tooltip within the wait came from the click.
     *
     * MUTATION: drop the press listener from `InfoMark.label`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAClickOnAnInfoMarkShowsItsTooltipAtOnce() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        StationSettingsWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        javax.swing.ToolTipManager tips = javax.swing.ToolTipManager.sharedInstance();

        int was = Components.onTheEventThread(tips::getInitialDelay);

        java.awt.Container form = StationDoors.part(window, "train.form", java.awt.Container.class);

        javax.swing.JLabel mark = Components.onTheEventThread(() -> Components.find(form, javax.swing.JLabel.class,
            l -> l.getIcon() == org.traincontrol.gui.InfoMark.icon() && l.getToolTipText() != null));

        try
        {
            Components.onTheEventThread(() -> { tips.setInitialDelay(60000); return null; });

            Mouse.click(mark);

            assertTrue(Components.waitFor(() -> tipShowingFor(mark), 1000), "a click on an (i) showed no tooltip");

            assertEquals((int) Components.onTheEventThread(tips::getInitialDelay), 60000, "the click changed the hover"
                + " delay of every other component");
        }
        finally
        {
            Mouse.exit(mark);

            Components.onTheEventThread(() -> { tips.setInitialDelay(was); return null; });

            SwingUtilities.invokeAndWait(window::dispose);
        }
    }

    /** Whether a tooltip of this component is on screen, in any window. */
    private static boolean tipShowingFor(java.awt.Component owner)
    {
        return Components.onTheEventThread(() ->
        {
            for (java.awt.Window each : java.awt.Window.getWindows())
            {
                if (each.isShowing() && Components.find(each, javax.swing.JToolTip.class,
                    tip -> tip.isShowing() && tip.getComponent() == owner) != null) return true;
            }

            return false;
        });
    }

    // ------------------------------------------------------------------------------------------------------------ menus

    /**
     * The editor's menu on a sensor square leads to the windows, and holds no setting of the square any more.
     *
     * MUTATION: put the maximum length item back on the menu, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsMenuHoldsTheWindowsNotTheSettings() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(square);

        JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

        for (String item : new String[] {I18n.t("autosetup.ui.menuStationSettings"),
            I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(square)),
            I18n.f("layout.ui.menuRemoveLocomotive", train), I18n.f("autolayout.ui.menuLocomotiveActive", train)})
        {
            assertNotNull(Components.withText(menu, item), "the editor's menu has no \"" + item + "\"");
        }

        for (String gone : new String[] {I18n.t("autosetup.ui.menuAutoDestination"), I18n.t("autosetup.ui.stationSectionStop"),
            I18n.t("autosetup.ui.stationSectionThrough")})
        {
            assertNull(Components.withText(menu, gone), "the editor's menu still has \"" + gone + "\"");
        }

        AbstractButton opens = Components.withText(menu, I18n.t("autosetup.ui.menuStationSettings"));

        Components.onTheEventThread(() -> { opens.doClick(); return null; });

        assertEquals(panel.stationSettingsWindow().squareForTest(), square, "the menu opened another square's window");
    }

    /**
     * On an empty station the item says it places a train; the active train can still be placed straight from the diagram's
     * menu, and Remove is there where a train stands (Adam: "it should still allow you to place the train that is active
     * directly").
     *
     * MUTATION: drop the Place item from `LayoutRightclickAutonomyMenu`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuStillPlacesTheActiveTrain() throws Exception
    {
        TileKey full = aSquareWithATrain();
        TileKey empty = anEmptyStation();

        String train = session.getLocomotiveNameAt(full);
        String active = aTrainNotPlaced();

        makeActive(active);

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu onTheTrain = AskingWindows.menuOn(ui, ui, full);

        for (String item : new String[] {I18n.f("layout.ui.menuRemoveLocomotive", train),
            I18n.f("layout.ui.menuPlaceLocomotive", active), I18n.t("autosetup.ui.menuStationSettings"),
            I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(full))})
        {
            assertTrue(AskingWindows.hasItem(onTheTrain, item), "the diagram's menu over a train has no \"" + item + "\"");
        }

        Mouse.closeMenus();

        JPopupMenu onTheEmpty = AskingWindows.menuOn(ui, ui, empty);

        for (String item : new String[] {I18n.f("layout.ui.menuPlaceLocomotive", active), I18n.t("autosetup.ui.menuPlaceHere"),
            I18n.t("autosetup.ui.menuStationSettings")})
        {
            assertTrue(AskingWindows.hasItem(onTheEmpty, item), "the diagram's menu over an empty station has no \"" + item + "\"");
        }

        Mouse.closeMenus();
    }

    /** Whether any item of the menu, its submenus included, is Edit <name>... for any name. */
    private static boolean offersEdit(java.awt.Container menu)
    {
        String[] around = I18n.f("autolayout.ui.menuEditLocomotive", "#TRAIN#").split("#TRAIN#", -1);

        return Components.find(menu, JMenuItem.class, item -> item.getText() != null
            && item.getText().startsWith(around[0]) && item.getText().endsWith(around[1])) != null;
    }

    /**
     * The diagram's menu over a train is grouped as Adam drew it (FR-182) and reordered it: no title row, as the
     * station's name heads the train's own items, Is Active first, then Change Direction and Change Arrived From with
     * Station Settings... right below; behind a divider Change Train at..., Place, Remove and Edit <name>..., and
     * nothing else.  "Edit Locomotive doesn't exist in the same grouping as any of the autonomy options"; no divider
     * twice.
     *
     * MUTATION: put Station Settings back behind the hand doors in `LayoutRightclickAutonomyMenu`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuOverATrainIsGrouped() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);
        String active = aTrainNotPlaced();

        makeActive(active);

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, full);

        java.util.List<java.util.List<String>> groups = groups(menu);

        java.util.List<String> shape = shape(menu);

        Mouse.closeMenus();

        assertEquals(shape.get(0), I18n.t("autolayout.ui.menuStartAutonomy"), "the menu does not start with Start: it"
            + " has a title row again: " + shape);

        String name = groupWith(groups, said("autolayout.ui.menuLocomotiveActive", train)).get(0);

        assertEquals(java.util.Collections.frequency(shape, "(heading) " + name), 1, "the station's name heads the menu"
            + " twice: " + shape);

        java.util.List<String> own = groupWith(groups, said("autolayout.ui.menuLocomotiveActive", train));

        assertEquals(own.indexOf(said("autolayout.ui.menuLocomotiveActive", train)), 1, "Is Active is not right below"
            + " the station's name: " + groups);

        assertTrue(own.get(2).startsWith(said("autosetup.ui.menuChangeDirectionUnknown", null)), "Change Direction does"
            + " not follow Is Active: " + groups);

        int settings = own.indexOf(I18n.t("autosetup.ui.menuStationSettings"));

        assertTrue(settings == own.size() - 1 && own.get(settings - 1).startsWith(said(
            "autosetup.ui.menuChangeArrivedFromUnknown", null)), "Station Settings... is not right below Change Arrived"
            + " From, last of the train's own items: " + groups);

        java.util.List<String> hand = groupWith(groups, said("autolayout.ui.menuEditLocomotive", train));

        assertEquals(hand, java.util.Arrays.asList(said("autosetup.ui.menuChangeTrainAt", session.describeTile(full)),
            said("layout.ui.menuPlaceLocomotive", active), said("layout.ui.menuRemoveLocomotive", train),
            said("autolayout.ui.menuEditLocomotive", train)), "Change Train at, Place, Remove and Edit are not a group"
            + " of their own, in that order: " + groups);

        for (int at = 1; at < shape.size(); at++)
        {
            assertFalse("----".equals(shape.get(at)) && "----".equals(shape.get(at - 1)), "two dividers in a row: "
                + shape);
        }

        assertFalse("----".equals(shape.get(shape.size() - 1)), "the menu ends in a divider: " + shape);
    }

    /**
     * Over an empty station Station Settings... is the station's own item, and the two ways to put a train there are a
     * group of their own behind a divider, on the track diagram and in the autonomy editor (FR-182, as Adam
     * reordered it).
     *
     * MUTATION: put the editor's Station Settings back below Place, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAnEmptyStationsMenusAreGrouped() throws Exception
    {
        TileKey empty = anEmptyStation();

        String active = aTrainNotPlaced();

        makeActive(active);

        AskingWindows.showThePageOf(ui, empty);

        java.util.List<java.util.List<String>> diagram = groups(AskingWindows.menuOn(ui, ui, empty));

        Mouse.closeMenus();

        AutonomyEditorPanel panel = theEditorsPanel();

        java.util.List<java.util.List<String>> inTheEditor = groups(Components.onTheEventThread(()
            -> panel.buildTileMenu(empty, null)));

        for (java.util.List<java.util.List<String>> groups : java.util.Arrays.asList(diagram, inTheEditor))
        {
            java.util.List<String> hand = groupWith(groups, I18n.t("autosetup.ui.menuPlaceHere"));

            int place = hand.indexOf(said("layout.ui.menuPlaceLocomotive", active));

            assertEquals(hand.subList(Math.max(0, place), hand.size()), java.util.Arrays.asList(
                said("layout.ui.menuPlaceLocomotive", active), I18n.t("autosetup.ui.menuPlaceHere")),
                "Place and Place a Locomotive Here... are not together at the end of their group: " + groups);

            java.util.List<String> before = groups.get(groups.indexOf(hand) - 1);

            assertEquals(before.get(before.size() - 1), I18n.t("autosetup.ui.menuStationSettings"),
                "Station Settings does not stand just before the Place items' divider: " + groups);
        }
    }

    /**
     * The autonomy editor's station menu is grouped the same way, with no Edit (FR-182 as Adam reordered it): Station
     * Settings... alone right under the square's name (OB-437), the train's own items, then Change Train at..., Place
     * and Remove.
     *
     * MUTATION: put Change Train at... back with the train's own items in `EditorTileMenus.buildTileMenu`, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsStationMenuIsGrouped() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);
        String active = aTrainNotPlaced();

        makeActive(active);

        java.util.List<java.util.List<String>> groups = groups(Components.onTheEventThread(()
            -> panel.buildTileMenu(full, null)));

        java.util.List<String> own = groupWith(groups, said("autolayout.ui.menuLocomotiveActive", train));

        assertEquals(own.get(0), said("autolayout.ui.menuLocomotiveActive", train), "Is Active is not the first of the"
            + " train's own items: " + groups);

        assertEquals(groups.get(1), java.util.Collections.singletonList(I18n.t("autosetup.ui.menuStationSettings")),
            "Station Settings is not alone in the group right under the square's name: " + groups);

        assertTrue(own.get(own.size() - 1).startsWith(said("autosetup.ui.menuChangeArrivedFromUnknown", null)),
            "Change Arrived From is not the last of the train's own items: " + groups);

        assertEquals(groups.get(groups.indexOf(own) + 1), java.util.Arrays.asList(
            said("autosetup.ui.menuChangeTrainAt", session.describeTile(full)),
            said("layout.ui.menuPlaceLocomotive", active), said("layout.ui.menuRemoveLocomotive", train)),
            "Change Train at, Place and Remove do not follow the train's own items as a group: " + groups);
    }

    /**
     * The editor's menu starts with the square's name and a divider on an empty station, a plain sensor and a station
     * with a train (FR-182), and no divider stands twice in a row or at the end.
     *
     * MUTATION: leave `AutonomyEditorPanel.tidy` off the end of `EditorTileMenus.buildTileMenu`, and the plain sensor's
     * empty hand-door group leaves two dividers in a row and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsMenuStartsWithItsHeading() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        for (final TileKey square : new TileKey[] {anEmptyStation(), aPlainSensor(), aSquareWithATrain()})
        {
            final JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

            java.util.List<String> shape = shape(menu);

            String name = session.getStore().getPointName(square);

            assertTrue(shape.size() > 2 && shape.get(0).startsWith("(heading) ") && "----".equals(shape.get(1)),
                "the editor's menu on " + square + " does not start with its heading and a divider: " + shape);

            if (name != null && !name.trim().isEmpty())
            {
                assertEquals(shape.get(0), "(heading) " + name.trim(), "the editor's menu on " + square + " is not headed"
                    + " with its name: " + shape);
            }

            for (int at = 1; at < shape.size(); at++)
            {
                assertFalse("----".equals(shape.get(at)) && "----".equals(shape.get(at - 1)), "the editor's menu on "
                    + square + " has two dividers in a row: " + shape);
            }

            assertFalse("----".equals(shape.get(shape.size() - 1)), "the editor's menu on " + square + " ends in a"
                + " divider: " + shape);
        }
    }

    /**
     * The train popup's row is "Arrived", not "Arrived from" (FR-182): its choices already say "From the East (right)",
     * so the label must not end in the word they begin with, in any language.
     *
     * MUTATION: put "Arrived from" back, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheArrivedRowDoesNotSayFromTwice() throws Exception
    {
        for (String language : new String[] {"en", "da", "de", "es", "fr", "it", "nl", "pl"})
        {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org.traincontrol.resources.messages",
                new java.util.Locale(language));

            String[] label = bundle.getString("autosetup.ui.trainArrived").trim().split("\\s+");

            for (String side : new String[] {"N", "E", "S", "W"})
            {
                String choice = bundle.getString("autolayout.ui.side" + side).trim().split("\\s+")[0];

                assertFalse(label[label.length - 1].equalsIgnoreCase(choice), language + ": the row says \""
                    + bundle.getString("autosetup.ui.trainArrived") + "\" before a choice that says \""
                    + bundle.getString("autolayout.ui.side" + side) + "\"");
            }
        }
    }

    /** The menu's parts in order: "----" for a divider, "(heading) " before a grayed item's text. */
    private static java.util.List<String> shape(final JPopupMenu menu)
    {
        return Components.onTheEventThread(() ->
        {
            java.util.List<String> out = new java.util.ArrayList<>();

            for (java.awt.Component part : menu.getComponents())
            {
                out.add(part instanceof javax.swing.JSeparator ? "----" : part instanceof JMenuItem
                    ? (part.isEnabled() ? "" : "(heading) ") + ((JMenuItem) part).getText() : "?");
            }

            return out;
        });
    }

    /** The menu's own items, by text, in the groups its dividers make; empty groups left out. */
    private static java.util.List<java.util.List<String>> groups(final JPopupMenu menu)
    {
        return Components.onTheEventThread(() ->
        {
            java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();

            java.util.List<String> group = new java.util.ArrayList<>();

            for (java.awt.Component part : menu.getComponents())
            {
                if (part instanceof javax.swing.JSeparator && !group.isEmpty())
                {
                    out.add(group);

                    group = new java.util.ArrayList<>();
                }
                else if (part instanceof JMenuItem)
                {
                    group.add(((JMenuItem) part).getText());
                }
            }

            if (!group.isEmpty()) out.add(group);

            return out;
        });
    }

    private static java.util.List<String> groupWith(java.util.List<java.util.List<String>> groups, String item)
    {
        for (java.util.List<String> group : groups) if (group.contains(item)) return group;

        throw new AssertionError("the menu has no \"" + item + "\": " + groups);
    }

    /** The message, or a line no menu says where this build's bundle has no such key. */
    private static String said(String key, String name)
    {
        try
        {
            return name == null ? I18n.t(key) : I18n.f(key, name);
        }
        catch (java.util.MissingResourceException none)
        {
            return "(no " + key + ")";
        }
    }

    /** Makes this locomotive the active one, as choosing it on a key does; mapping it again would take it off. */
    private static void makeActive(String name)
    {
        if (ui.getActiveLoc() == null || !name.equals(ui.getActiveLoc().getName())) ui.mapLocToCurrentButton(name);

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() != null && name.equals(ui.getActiveLoc().getName()), 10000),
            "precondition: " + name + " could not be made the active locomotive");
    }

    /**
     * The diagram's menu over a train always offers Edit Locomotive..., which opens that train's Locomotive Settings on
     * the Autonomy tab, beside Place for the selected locomotive and the train window (Adam: "there should always be an
     * edit loc, place current (if selected and different), and place new option").
     *
     * MUTATION: have the item open the Locomotive tab, or open the train window as the old dialog's item did, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuOverATrainOpensItsSettings() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        makeActive(aTrainNotPlaced());

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, full);

        for (String item : new String[] {I18n.f("autolayout.ui.menuEditLocomotive", train),
            I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName()),
            I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(full))})
        {
            assertTrue(AskingWindows.hasItem(menu, item), "the diagram's menu over a train has no \"" + item + "\"");
        }

        Mouse.choose(AskingWindows.itemOf(menu, I18n.f("autolayout.ui.menuEditLocomotive", train)));

        Components.settle();

        LocomotiveSettingsFrame frame = StationDoors.showing(LocomotiveSettingsFrame.class);

        try
        {
            assertNotNull(frame, "Edit Locomotive... opened no Locomotive Settings");
            assertEquals(frame.getLocomotive(), model.getLocByName(train), "Edit Locomotive... opened another locomotive");
            assertEquals(support.SettingsWindow.tabShowing(), LocomotiveSettingsFrame.Tab.AUTONOMY.ordinal(),
                "Edit Locomotive... did not open the Autonomy tab");
        }
        finally
        {
            support.SettingsWindow.closeIfOpen();
        }
    }

    /**
     * During a run the diagram's menu over a train still offers Edit Locomotive... (Adam: "there should always be an edit
     * loc"), on the running menu and on the menu while a run holds the setup, and the window opens locked ("open
     * locked").
     *
     * MUTATION: leave `editLocomotiveItem` off the running menu, and this fails.
     */
    @Test(timeOut = 120000)
    public void testDuringARunTheDiagramsMenuStillEditsTheTrain() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu running = runningMenuOn(full);

        assertTrue(AskingWindows.hasItem(running, I18n.f("autolayout.ui.menuEditLocomotive", train)),
            "the running menu over a train has no Edit Locomotive...");

        Mouse.closeMenus();

        model.getAutoLayout().setStagingInProgress(true);

        try
        {
            assertTrue(ui.aRunHoldsTheSetup(), "precondition: no run holds the setup");

            JPopupMenu held = AskingWindows.menuOn(ui, ui, full);

            Mouse.choose(AskingWindows.itemOf(held, I18n.f("autolayout.ui.menuEditLocomotive", train)));

            Components.settle();

            LocomotiveSettingsFrame frame = StationDoors.showing(LocomotiveSettingsFrame.class);

            assertNotNull(frame, "Edit Locomotive... during a run opened no Locomotive Settings");
            assertEquals(frame.getLocomotive(), model.getLocByName(train), "Edit Locomotive... opened another locomotive");

            support.SettingsWindow.assertLocked("the diagram's Edit Locomotive... during a run",
                I18n.t("loc.ui.settingsAutonomyStrip"));
        }
        finally
        {
            model.getAutoLayout().setStagingInProgress(false);

            support.SettingsWindow.closeIfOpen();
        }
    }

    /** The diagram's menu over the square while autonomy runs, its run flag set and put back around the right-click. */
    private static JPopupMenu runningMenuOn(TileKey square) throws Exception
    {
        final java.lang.reflect.Field staging = Reflect.field(TrainControlUI.class, "stagingFlowActive");

        try
        {
            Components.onTheEventThread(() -> { staging.set(ui, true); return null; });

            JPopupMenu running = AskingWindows.menuOn(ui, ui, square);

            assertTrue(AskingWindows.hasItem(running, I18n.t("autolayout.ui.menuStopAutonomyGracefully")),
                "precondition: this is not the running menu");

            return running;
        }
        finally
        {
            Components.onTheEventThread(() -> { staging.set(ui, false); return null; });
        }
    }

    /**
     * On the menu shown while autonomy runs, Edit <name>... is never in a group with the autonomy items (FR-182): not
     * with Stop or Is Active.  Change Train at... is a hand door beside it, as on the menu at rest.
     *
     * MUTATION: drop the divider before the hand doors in `LayoutRightclickAutonomyMenu`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testDuringARunEditIsApartFromTheAutonomyItems() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        AskingWindows.showThePageOf(ui, full);

        java.util.List<java.util.List<String>> groups = groups(runningMenuOn(full));

        Mouse.closeMenus();

        java.util.List<String> edit = groupWith(groups, said("autolayout.ui.menuEditLocomotive", train));

        for (String autonomy : new String[] {I18n.t("autolayout.ui.menuStopAutonomyGracefully"),
            said("autolayout.ui.menuLocomotiveActive", train)})
        {
            assertFalse(edit.contains(autonomy), "Edit is in a group with \"" + autonomy + "\" on the running menu: "
                + groups);
        }
    }

    /**
     * The menu while autonomy runs is ordered as the menu at rest is: no title row, the
     * station's name heading Is Active and Station Settings..., then Change Train at... and Edit <name>... behind a
     * divider.  The items both menus have stand in the same order and the same groups.
     *
     * MUTATION: put Station Settings... back behind a divider of its own at the end of the running menu, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheRunningMenuIsOrderedAsTheMenuAtRest() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        AskingWindows.showThePageOf(ui, full);

        java.util.List<java.util.List<String>> atRest = groups(AskingWindows.menuOn(ui, ui, full));

        Mouse.closeMenus();

        JPopupMenu runningMenu = runningMenuOn(full);

        java.util.List<String> shape = shape(runningMenu);
        java.util.List<java.util.List<String>> running = groups(runningMenu);

        Mouse.closeMenus();

        assertEquals(shape.get(0), I18n.t("autolayout.ui.menuStopAutonomyGracefully"), "the running menu has a title row: "
            + shape);

        java.util.Set<String> both = new java.util.LinkedHashSet<>();

        for (java.util.List<String> group : atRest) both.addAll(group);

        java.util.Set<String> inRunning = new java.util.HashSet<>();

        for (java.util.List<String> group : running) inRunning.addAll(group);

        both.retainAll(inRunning);

        for (String item : new String[] {said("autolayout.ui.menuLocomotiveActive", train),
            I18n.t("autosetup.ui.menuStationSettings"), said("autosetup.ui.menuChangeTrainAt", session.describeTile(full)),
            said("autolayout.ui.menuEditLocomotive", train)})
        {
            assertTrue(both.contains(item), "precondition: \"" + item + "\" is not on both menus: " + atRest + " and "
                + running);
        }

        assertEquals(only(running, both), only(atRest, both), "the running menu's order or groups differ from the menu"
            + " at rest for the items both have");
    }

    /**
     * While autonomy runs, an empty station's menu offers no way to place a train there: neither Place a Locomotive
     * Here... nor Place <the active locomotive>.
     *
     * MUTATION: drop `busy && locomotive == null` from the train window's line in `LayoutRightclickAutonomyMenu`, and
     * this fails.
     */
    @Test
    public void testTheRunningMenuPlacesNothingOnAnEmptyStation() throws Exception
    {
        TileKey empty = anEmptyStation();

        AskingWindows.showThePageOf(ui, empty);

        JPopupMenu running = runningMenuOn(empty);

        Mouse.closeMenus();

        assertFalse(AskingWindows.hasItem(running, I18n.t("autosetup.ui.menuPlaceHere")),
            "the running menu over an empty station offers Place a Locomotive Here...");

        if (ui.getActiveLoc() != null)
        {
            assertFalse(AskingWindows.hasItem(running, I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName())),
                "the running menu over an empty station offers to place the active locomotive");
        }
    }

    /**
     * The diagram's menu offers Change Direction only over a train on the railway: lifted off, its station's menu has
     * none, though the setup still records it there.
     *
     * MUTATION: drop the `trainOnTheRailway` check from `TrainControlUI.addAutonomyFacingItems`, and this fails.
     */
    @Test
    public void testTheDiagramsMenuTurnsOnlyATrainOnTheRailway() throws Exception
    {
        TileKey full = aSquareWithATrain();
        String train = session.getLocomotiveNameAt(full);
        String turn = I18n.t("autosetup.ui.menuChangeDirectionUnknown");

        AskingWindows.showThePageOf(ui, full);

        assertTrue(offers(AskingWindows.menuOn(ui, ui, full), turn), "precondition: no Change Direction over " + train);

        Mouse.closeMenus();

        java.util.List<org.traincontrol.automation.Point> lifted = new java.util.ArrayList<>();
        Locomotive loc = null;

        try
        {
            for (org.traincontrol.automation.Point p : model.getAutoLayout().getPoints())
            {
                if (p.getCurrentLocomotive() != null && train.equals(p.getCurrentLocomotive().getName())) lifted.add(p);
            }

            loc = lifted.get(0).getCurrentLocomotive();

            for (org.traincontrol.automation.Point p : lifted) p.setLocomotive(null);

            assertEquals(session.getLocomotiveNameAt(full), train, "precondition: the setup no longer records " + train);
            assertFalse(offers(AskingWindows.menuOn(ui, ui, full), turn),
                "Change Direction is offered over a station the railway has no train on");

        }
        finally
        {
            Mouse.closeMenus();

            for (org.traincontrol.automation.Point p : lifted) p.setLocomotive(loc);
        }
    }

    /** Whether any item of the menu, its submenus included, starts with this text. */
    private static boolean offers(JPopupMenu menu, String start)
    {
        for (javax.swing.JMenuItem item : Components.all(menu, javax.swing.JMenuItem.class, null))
        {
            if (item.getText() != null && item.getText().startsWith(start)) return true;
        }

        return false;
    }

    /** The menu's groups with only these items, empty groups left out. */
    private static java.util.List<java.util.List<String>> only(java.util.List<java.util.List<String>> groups,
        java.util.Set<String> items)
    {
        java.util.List<java.util.List<String>> out = new java.util.ArrayList<>();

        for (java.util.List<String> group : groups)
        {
            java.util.List<String> kept = new java.util.ArrayList<>(group);

            kept.retainAll(items);

            if (!kept.isEmpty()) out.add(kept);
        }

        return out;
    }

    /**
     * Place is offered for the selected locomotive only when it is not the train standing there; Edit Locomotive... and
     * the train window stay.
     *
     * MUTATION: drop `!ui.getActiveLoc().equals(locomotive)` from the Place item's test, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuOffersPlaceOnlyForAnotherLocomotive() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        makeActive(train);

        AskingWindows.showThePageOf(ui, full);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, full);

        assertFalse(AskingWindows.hasItem(menu, I18n.f("layout.ui.menuPlaceLocomotive", train)),
            "the diagram's menu offers to place the train already standing there");
        assertTrue(AskingWindows.hasItem(menu, I18n.f("autolayout.ui.menuEditLocomotive", train)),
            "the diagram's menu lost Edit Locomotive... with its own train selected");
        assertTrue(AskingWindows.hasItem(menu, I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(full))),
            "the diagram's menu lost the train window with its own train selected");

        Mouse.closeMenus();
    }

    /**
     * On an empty station there is no train to edit: no Edit Locomotive..., while Place for the selected locomotive and
     * Place a Locomotive Here... stay.
     *
     * MUTATION: offer the item whether or not a train stands there, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheDiagramsMenuOverAnEmptyStationHasNoEditLocomotive() throws Exception
    {
        TileKey empty = anEmptyStation();

        makeActive(aTrainNotPlaced());

        AskingWindows.showThePageOf(ui, empty);

        JPopupMenu menu = AskingWindows.menuOn(ui, ui, empty);

        assertFalse(offersEdit(menu),
            "the diagram's menu offers Edit Locomotive... where no train stands");
        assertTrue(AskingWindows.hasItem(menu, I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName())),
            "the diagram's menu over an empty station cannot place the selected locomotive");
        assertTrue(AskingWindows.hasItem(menu, I18n.t("autosetup.ui.menuPlaceHere")),
            "the diagram's menu over an empty station has no Place a Locomotive Here...");

        Mouse.closeMenus();
    }

    /**
     * The autonomy editor's menu has no Edit Locomotive (Adam: "no edit loc in the autonomy editor"): over a train it
     * offers the train window, and nothing that opens the old station dialog.
     *
     * MUTATION: add the diagram's Edit Locomotive... item to `EditorTileMenus`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsMenuHasNoEditLocomotive() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey square = aSquareWithATrain();

        JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

        assertFalse(offersEdit(menu),
            "the editor's menu offers Edit Locomotive...");
        assertNotNull(Components.withText(menu, I18n.f("autosetup.ui.menuChangeTrainAt", session.describeTile(square))),
            "the editor's menu has no train window");
    }

    // ------------------------------------------------------------------------------------- Place the selected train

    /** Takes the selected locomotive off its key, as choosing it again does. */
    private static void nothingIsSelected()
    {
        if (ui.getActiveLoc() != null) ui.mapLocToCurrentButton(ui.getActiveLoc().getName());

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() == null, 10000),
            "precondition: the selected locomotive could not be taken off its key");
    }

    private static JMenuItem placeItemOn(AutonomyEditorPanel panel, TileKey square, String train) throws Exception
    {
        JPopupMenu menu = Components.onTheEventThread(() -> panel.buildTileMenu(square, null));

        return (JMenuItem) Components.withText(menu, I18n.f("layout.ui.menuPlaceLocomotive", train));
    }

    private static java.util.List<String> roadNames(java.util.List<org.traincontrol.automation.Edge> road)
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        if (road != null) for (org.traincontrol.automation.Edge edge : road) out.add(edge.getEnd().getName());

        return out;
    }

    /**
     * The editor's station menu places the selected locomotive in one click, through the door the train window uses: it
     * records the side it came in by and asks where the tail lies, and the answer is kept (Adam: "there should always be
     * an edit loc, place current (if selected and different), and place new option").  Tunnel, one way in from the north,
     * and a train of 5.
     *
     * MUTATION: have the item call `session.placeLocomotive` instead of `AutonomyEditorPanel.placeLocomotive`, and the
     * side and the tail are not recorded and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheEditorsMenuPlacesTheSelectedTrainWithItsSideAndTail() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        final TileKey tunnel = support.StandingTrain.squareNamed(session, "Tunnel");

        String name = aTrainNotPlaced();

        Locomotive train = model.getLocByName(name);

        Integer lengthWas = train.getTrainLength();

        try
        {
            train.setTrainLength(5);

            Components.onTheEventThread(() -> { session.placeLocomotive(tunnel, null); return null; });

            Components.settle();

            org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPre = tunnelPreIsTheTail(tunnel);

            makeActive(name);

            JMenuItem item = placeItemOn(panel, tunnel, name);

            assertNotNull(item, "the editor's menu on Tunnel has no Place " + name);
            assertTrue(item.isEnabled(), "Place " + name + " is grayed with no route running");

            Components.onTheEventThread(() -> { item.doClick(); return null; });

            assertEquals(session.getLocomotiveNameAt(tunnel), name, "the item did not put the selected train on Tunnel");
            assertEquals(session.getArrivedFrom(tunnel), "N", "the placement recorded no arrival side, or the wrong one");

            assertStandsFromTheNorthWithItsTail(train, tunnelPre);
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            train.setTrainLength(lengthWas);
        }
    }

    /** Answers the tail question with TunnelPre, the tail of a five-unit train come into Tunnel from the north. */
    private static org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPreIsTheTail(TileKey tunnel) throws Exception
    {
        org.traincontrol.automation.Layout railway = model.getAutoLayout();

        org.traincontrol.automation.Point copy = railway.getPoint(session.facingsFor(tunnel).keySet().iterator().next());

        org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPre = org.traincontrol.gui.TailCrossedPrompt
            .choicesFor(railway, copy, "N", 5, null).stream()
            .filter(choice -> choice.getFarthest().getName().startsWith("TunnelPre")).findFirst().orElse(null);

        assertNotNull(tunnelPre, "precondition: a five-unit train at Tunnel from the north is not offered TunnelPre");

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(tunnelPre.getFarthest().getName());

        return tunnelPre;
    }

    /** Waits for the train to stand on the railway come in from the north, its tail along the road chosen. */
    private static void assertStandsFromTheNorthWithItsTail(Locomotive train,
        org.traincontrol.gui.TailCrossedPrompt.Choice tail)
    {
        final java.util.List<String> expected = roadNames(tail.getRoad());

        assertTrue(Components.waitFor(() ->
        {
            org.traincontrol.automation.Point standing = model.getAutoLayout().getLocomotiveLocation(train);

            return standing != null && "N".equals(standing.getArrivedFrom())
                && expected.equals(roadNames(standing.getArrivedAlong()));
        }, 20000), "the tail answer was not kept; expected the road " + expected);
    }

    /**
     * Placed over another train through the train window's list, the new one stands on Tunnel on the railway with its
     * side and tail, as on an empty station (`testTheEditorsMenuPlacesTheSelectedTrainWithItsSideAndTail`), and the
     * train it displaced leaves the setup and the railway, as the diagram's Place does (OB-416).
     *
     * MUTATION: name only `name` in `AutonomyEditorPanel.placeLocomotive`'s `placementChanged`, and the rebuild puts the
     * displaced train back over the new one and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheListOverAnotherTrainTakesItsPlace() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        placeOverTheTrainOnTunnel(theList(panel), false);
    }

    /**
     * After a run the railway can hold X where the setup names Y: placed over them through the list, the new train
     * takes the square and both leave it, on the railway and in the setup (OB-416).
     *
     * MUTATION: leave `tileMenus.trainStanding(tile)` out of the names `AutonomyEditorPanel.placeLocomotive` gives
     * `placementChanged`, and the rebuild puts X back over the new train and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheListOverARailwaysOtherTrainTakesItsPlace() throws Exception
    {
        placeOverTheTrainOnTunnel(theList(theEditorsPanel()), true);
    }

    /** The train window's list on Tunnel. */
    private static PlacingDoor theList(AutonomyEditorPanel panel)
    {
        return name ->
        {
            StationSettingsWindow window = StationDoors.openTrain(panel, support.StandingTrain.squareNamed(session, "Tunnel"));

            StationDoors.pick(window, "train.train", name);

            return window;
        };
    }

    /**
     * The same through the editor's Place <selected> (OB-416).
     *
     * MUTATION: name only `name` in `AutonomyEditorPanel.placeLocomotive`'s `placementChanged`, and the rebuild puts the
     * displaced train back over the new one and this fails.
     */
    @Test(timeOut = 180000)
    public void testTheEditorsPlaceOverAnotherTrainTakesItsPlace() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        placeOverTheTrainOnTunnel(name ->
        {
            makeActive(name);

            JMenuItem item = placeItemOn(panel, support.StandingTrain.squareNamed(session, "Tunnel"), name);

            assertNotNull(item, "the editor's menu on Tunnel has no Place " + name);

            Components.onTheEventThread(() -> { item.doClick(); return null; });

            return null;
        }, false);
    }

    /**
     * Cancel after placing over another train brings the station back as it was, as after a drop onto a train: the
     * displaced train is back in the setup and on the railway, and the placed one is off both (OB-416).
     *
     * MUTATION: drop `putBackOnDiscard.addAll(named)` from `AutonomyEditorPanel.placeLocomotive`, and the displaced train
     * ends nowhere and this fails.
     */
    @Test(timeOut = 180000)
    public void testCancelAfterPlacingOverAnotherTrainPutsTheStationBack() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();

        String was = session.getLocomotiveNameAt(full);
        String name = aTrainOffTheRailway();

        String wasBefore = where(was);
        String nameBefore = where(name);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            makeActive(name);

            JMenuItem item = placeItemOn(panel, full, name);

            assertNotNull(item, "precondition: the editor's menu has no Place " + name + " over " + was);

            Components.onTheEventThread(() -> { item.doClick(); return null; });

            cancelOnceOnTheRailway(name);

            assertEquals(where(was), wasBefore, "Cancel did not put the displaced train back");
            assertEquals(where(name), nameBefore, "Cancel did not take the placed train back off");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);
        }
    }

    /** Where the setup and the railway have this train, with the side it came in by on the railway. */
    private static String where(String name)
    {
        TileKey inSetup = null;

        for (Map.Entry<TileKey, String> each : session.placementsAutonomyWillWrite().entrySet())
        {
            if (name.equals(each.getValue())) inSetup = each.getKey();
        }

        org.traincontrol.automation.Point on = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));

        return "setup " + inSetup + ", railway " + (on == null ? null : on.getName() + " from " + on.getArrivedFrom());
    }

    /** Waits for the placed train to stand on the railway, then Cancels the editor. */
    private void cancelOnceOnTheRailway(String name) throws Exception
    {
        assertTrue(Components.waitFor(() -> model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name)) != null,
            20000), "precondition: " + name + " was not put on the railway");

        Components.settle();

        cancelTheEditor();
    }

    /** Cancel on the editor, answering Yes to throwing the edits away. */
    private void cancelTheEditor() throws Exception
    {
        final LayoutEditor closing = editor;

        final java.lang.reflect.Method exit = Reflect.method(LayoutEditor.class, "confirmExit");

        try (support.Answering answering = support.Answering.start(message -> support.Answering.press(AskingWindows.YES)))
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    exit.invoke(closing);
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(Components.waitFor(() -> !closing.isDisplayable(), 20000), "Cancel did not close the editor");
        }

        Components.settle();

        editor = null;
    }

    /** A door that places the named train on Tunnel; returns the window it left open, or null. */
    private interface PlacingDoor
    {
        StationSettingsWindow place(String name) throws Exception;
    }

    /**
     * Stands a train on the empty Tunnel through the door, then places another over it through the same door;
     * `afterARun` first swaps the railway's train there for one the setup does not place.
     */
    private void placeOverTheTrainOnTunnel(PlacingDoor door, boolean afterARun) throws Exception
    {
        final TileKey tunnel = support.StandingTrain.squareNamed(session, "Tunnel");

        assertNull(session.getLocomotiveNameAt(tunnel), "precondition: Tunnel is not empty in the setup");

        // `putBack` leaves a train an earlier claim placed standing on the railway, so Tunnel is emptied there too
        offTheRailwayAt(tunnel);

        StationSettingsWindow window = null;

        String was = aTrainOffTheRailway();

        final Locomotive displaced = model.getLocByName(was);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        StationSettingsWindow first = door.place(was);

        if (first != null) StationDoors.close(first);

        assertTrue(Components.waitFor(() -> model.getAutoLayout().getLocomotiveLocation(displaced) != null, 20000),
            "precondition: " + was + " was not put on Tunnel on the railway");

        Components.settle();

        final String onTheRailway = afterARun ? aTrainOffTheRailway() : null;

        if (afterARun)
        {
            final String copy = model.getAutoLayout().getLocomotiveLocation(displaced).getName();

            Components.onTheEventThread(() ->
            {
                model.getAutoLayout().moveLocomotive(null, copy, false);

                assertTrue(model.getAutoLayout().moveLocomotive(onTheRailway, copy, false),
                    "precondition: " + onTheRailway + " could not be stood on Tunnel");

                return null;
            });

            Components.settle();

            assertEquals(session.trainOnTheRailway(tunnel, model.getAutoLayout()), onTheRailway,
                "precondition: the railway does not hold " + onTheRailway + " on Tunnel");
            assertEquals(session.getLocomotiveNameAt(tunnel), was, "precondition: the setup does not name " + was);
        }

        String name = aTrainOffTheRailway();

        Locomotive train = model.getLocByName(name);

        Integer lengthWas = train.getTrainLength();

        try
        {
            train.setTrainLength(5);

            org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPre = tunnelPreIsTheTail(tunnel);

            window = door.place(name);

            assertEquals(session.getLocomotiveNameAt(tunnel), name, "the train was not put on Tunnel in the setup");
            assertEquals(session.getArrivedFrom(tunnel), "N", "the placement recorded no arrival side, or the wrong one");

            assertStandsFromTheNorthWithItsTail(train, tunnelPre);

            assertFalse(session.placementsAutonomyWillWrite().containsValue(was), was + " is still in the setup");

            assertNull(model.getAutoLayout().getLocomotiveLocation(displaced), was + " still stands on the railway");

            if (onTheRailway != null)
            {
                assertNull(model.getAutoLayout().getLocomotiveLocation(model.getLocByName(onTheRailway)),
                    onTheRailway + ", which only the railway had on Tunnel, still stands on the railway");
            }
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            train.setTrainLength(lengthWas);

            if (window != null) StationDoors.close(window);

            offTheRailwayAt(tunnel);
        }
    }

    /** A train neither the setup nor the railway has standing anywhere. */
    private static String aTrainOffTheRailway()
    {
        return support.StandingTrain.standingNowhere(model, session);
    }

    /** Lifts whatever stands on this square's copies off the railway alone. */
    private static void offTheRailwayAt(TileKey square)
    {
        Components.onTheEventThread(() ->
        {
            for (String copy : session.facingsFor(square).keySet())
            {
                org.traincontrol.automation.Point point = model.getAutoLayout().getPoint(copy);

                if (point != null && point.getCurrentLocomotive() != null) model.getAutoLayout().moveLocomotive(null, copy, false);
            }

            return null;
        });

        Components.settle();
    }

    /**
     * No Place item for the selected train where nothing is selected, or where the selected train already stands.
     *
     * MUTATION: drop the `equals(standing)` test or the `isStation` test in `EditorTileMenus.placeSelectedItem`, and the
     * matching part of this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsMenuHasNoPlaceForNothingOrForTheTrainAlreadyThere() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();
        TileKey empty = anEmptyStation();

        String standing = session.getLocomotiveNameAt(full);
        String other = aTrainNotPlaced();

        nothingIsSelected();

        assertNull(placeItemOn(panel, empty, other), "Place was offered with nothing selected");
        assertNull(placeItemOn(panel, full, standing), "Place was offered with nothing selected, over a train");

        makeActive(standing);

        assertNull(placeItemOn(panel, full, standing), "Place was offered for the train already standing there");

        makeActive(other);

        assertNotNull(placeItemOn(panel, empty, other), "precondition: no Place for a selected train that stands nowhere");
        assertNotNull(placeItemOn(panel, full, other), "precondition: no Place over another train");

        // Only a station takes a train: a point that is none gets no Place
        TileKey plain = null;

        for (TileKey each : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(each.getPage()) && !session.getStore().isStation(each)) plain = each;
        }

        assertNotNull(plain, "precondition: " + PAGE + " has no point that is not a station");

        assertNull(placeItemOn(panel, plain, other), "Place was offered on a point that is not a station");
    }

    /**
     * Which train "stands there" is read off the railway, as the Train at item and the diagram's menu read it: after a
     * run the railway can hold X where the setup still names Y, and Place X is not offered on X's own square.
     *
     * MUTATION: pass `editor.locomotiveAt(target)` instead of `trainStanding(target)` to `placeSelectedItem`, and this
     * fails.
     */
    @Test(timeOut = 180000)
    public void testAfterARunPlaceReadsTheTrainOnTheRailway() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey full = aSquareWithATrain();

        String inTheSetup = session.getLocomotiveNameAt(full);

        org.traincontrol.automation.Layout railway = model.getAutoLayout();

        org.traincontrol.automation.Point copy = railway.getLocomotiveLocation(model.getLocByName(inTheSetup));

        assertNotNull(copy, "precondition: " + inTheSetup + " is not on the railway");

        String x = aTrainNotPlaced();

        try
        {
            Components.onTheEventThread(() ->
            {
                railway.moveLocomotive(null, copy.getName(), false);

                assertTrue(railway.moveLocomotive(x, copy.getName(), false), "precondition: " + x + " could not be stood there");

                return null;
            });

            Components.settle();

            assertEquals(Components.onTheEventThread(() -> session.trainOnTheRailway(full, model.getAutoLayout())), x,
                "precondition: the railway does not hold " + x + " on the square");

            makeActive(x);

            assertNull(placeItemOn(panel, full, x), "Place " + x + " is offered on the square the railway has it standing on");

            makeActive(inTheSetup);

            assertNotNull(placeItemOn(panel, full, inTheSetup),
                "Place " + inTheSetup + " is not offered where the railway has another train");
        }
        finally
        {
            // The railway as the setup has it again, for the claims after this one
            Components.onTheEventThread(() ->
            {
                railway.moveLocomotive(null, copy.getName(), false);
                railway.moveLocomotive(inTheSetup, copy.getName(), false);

                return null;
            });

            Components.settle();
        }
    }

    /**
     * While a route runs the item is not offered at all (Adam: "hide the Place during a run"), and it is back once the
     * route is over; one offered at rest and pressed during the run places nothing.
     *
     * MUTATION: offer the item while `aRunHoldsTheSetup()`, grayed or not, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheEditorsPlaceFollowsTheRunRule() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey empty = anEmptyStation();

        String name = aTrainNotPlaced();

        makeActive(name);

        JMenuItem atRest = placeItemOn(panel, empty, name);

        assertNotNull(atRest, "precondition: the editor's menu has no Place " + name + " at rest");

        regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, true);

        try
        {
            assertNull(placeItemOn(panel, empty, name), "the editor's menu offers Place " + name + " while a route runs");

            StationDoors.pressThoughGrayed(atRest);

            assertNull(session.getLocomotiveNameAt(empty), "a Place offered at rest put a train down during the run");
        }
        finally
        {
            regression.testNoSetupEditWhileARouteRuns.aRouteRuns(ui, false);
        }

        JMenuItem after = placeItemOn(panel, empty, name);

        assertNotNull(after, "Place is not offered again once the route is over");
        assertTrue(after.isEnabled(), "Place is grayed once the route is over");
    }

    // ------------------------------------------------------------------------------------- Cancel undoes a placement

    /**
     * Cancel undoes a train placed on an empty station from the train window's list, as it undoes a drag (MT-914): the
     * station is as the editor opened it, in the setup and on the railway, and the train, which stood nowhere, stands
     * nowhere again with nothing of the placement left on the railway's copies of the square.
     *
     * MUTATION: put `if (named.size() > 1)` back before `putBackOnDiscard.addAll(named)` in
     * `AutonomyEditorPanel.placeLocomotive`, and the train stays on the station and this fails.
     */
    @Test(timeOut = 180000)
    public void testCancelAfterPlacingOnAnEmptyStationPutsItBack() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey empty = anEmptyStation();

        offTheRailwayAt(empty);

        String name = aTrainOffTheRailway();

        org.json.JSONObject opened = Components.onTheEventThread(() -> session.snapshotSetup());

        String nameBefore = where(name);
        String stationBefore = station(empty);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            StationDoors.placeFromTheList(panel, empty, name);

            cancelOnceOnTheRailway(name);

            assertEquals(station(empty), stationBefore, "Cancel did not bring the station back as the editor opened it");
            assertEquals(where(name), nameBefore, "Cancel did not take the placed train back off");
            // Of this train only: the closing fold writes in what earlier claims left standing on the railway alone
            assertEquals(mentions(Components.onTheEventThread(() -> session.snapshotSetup()), name), mentions(opened, name),
                "Cancel left something of the placement in the setup");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            offTheRailwayAt(empty);
        }
    }

    /**
     * A train that stood on another station, placed on an empty one through the editor's Place <selected>, is back on
     * its own after Cancel, come in by the same side with its tail along the same road, in the setup and on the railway
     * (MT-914): a train of 5 on Tunnel from the north, its tail on TunnelPre, as a first editor left it.
     *
     * MUTATION: put `if (named.size() > 1)` back before `putBackOnDiscard.addAll(named)` in
     * `AutonomyEditorPanel.placeLocomotive`, and the train stays on the empty station and this fails.
     */
    @Test(timeOut = 240000)
    public void testCancelPutsAPlacedTrainBackWithItsSideAndTail() throws Exception
    {
        AutonomyEditorPanel first = theEditorsPanel();

        final TileKey tunnel = support.StandingTrain.squareNamed(session, "Tunnel");

        offTheRailwayAt(tunnel);

        String name = aTrainOffTheRailway();

        Locomotive train = model.getLocByName(name);

        Integer lengthWas = train.getTrainLength();

        TileKey empty = null;

        try
        {
            train.setTrainLength(5);

            org.traincontrol.gui.TailCrossedPrompt.Choice tunnelPre = tunnelPreIsTheTail(tunnel);

            makeActive(name);

            JMenuItem onTunnel = placeItemOn(first, tunnel, name);

            assertNotNull(onTunnel, "precondition: the editor's menu on Tunnel has no Place " + name);

            Components.onTheEventThread(() -> { onTunnel.doClick(); return null; });

            assertStandsFromTheNorthWithItsTail(train, tunnelPre);

            Components.settle();

            assertNotNull(session.getArrivedAlong(tunnel), "precondition: the setup recorded no tail on Tunnel");

            // Saved, as the closing fold writes what the railway holds into the setup; then a second editor opens
            saveTheEditor();

            AutonomyEditorPanel panel = theEditorsPanel();

            empty = anEmptyStation();

            offTheRailwayAt(empty);

            String nameBefore = whereWithItsTail(name);
            String tunnelBefore = station(tunnel);
            String emptyBefore = station(empty);

            org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

            JMenuItem item = placeItemOn(panel, empty, name);

            assertNotNull(item, "precondition: the editor's menu has no Place " + name + " on an empty station");

            Components.onTheEventThread(() -> { item.doClick(); return null; });

            final TileKey target = empty;

            assertTrue(Components.waitFor(() -> target.equals(session.getStationIndex().squareOf(
                model.getAutoLayout().getLocomotiveLocation(train))), 20000), "precondition: " + name
                + " was not moved onto " + target + " on the railway");

            Components.settle();

            cancelTheEditor();

            assertEquals(whereWithItsTail(name), nameBefore, "Cancel did not put the train back with its side and tail");
            assertEquals(station(tunnel), tunnelBefore, "Cancel did not bring Tunnel back as the editor opened it");
            assertEquals(station(empty), emptyBefore, "Cancel did not empty the station again");
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            train.setTrainLength(lengthWas);

            offTheRailwayAt(tunnel);

            if (empty != null) offTheRailwayAt(empty);
        }
    }

    /**
     * Save instead of Cancel keeps a placement on an empty station, in the setup and on the railway.
     *
     * MUTATION: have the Save button's autonomy branch call `discardAutonomyWork()` before it closes, and this fails.
     */
    @Test(timeOut = 180000)
    public void testSaveKeepsAPlacementOnAnEmptyStation() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TileKey empty = anEmptyStation();

        offTheRailwayAt(empty);

        String name = aTrainOffTheRailway();

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        try
        {
            StationDoors.placeFromTheList(panel, empty, name);

            saveTheEditor();

            assertEquals(session.getLocomotiveNameAt(empty), name, "Save did not keep the placement in the setup");

            assertTrue(Components.waitFor(() -> empty.equals(session.getStationIndex().squareOf(
                model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name)))), 20000),
                "Save did not keep " + name + " on " + empty + " on the railway: " + where(name));
        }
        finally
        {
            org.traincontrol.gui.TailCrossedPrompt.answerForTests(null);

            offTheRailwayAt(empty);
        }
    }

    /** How often a setup names this train. */
    private static int mentions(org.json.JSONObject setup, String name)
    {
        return setup.toString().split(java.util.regex.Pattern.quote(org.json.JSONObject.quote(name)), -1).length - 1;
    }

    /** The editor's Save, pressed, which closes it. */
    private void saveTheEditor() throws Exception
    {
        final LayoutEditor closing = editor;

        AbstractButton save = Components.withText(closing, I18n.t("layout.ui.saveChanges"));

        assertNotNull(save, "precondition: the editor has no Save button");

        StationDoors.pressAndAnswer(save, message -> null);

        assertTrue(Components.waitFor(() -> !closing.isDisplayable(), 20000), "precondition: Save did not close the editor");

        Components.settle();

        editor = null;
    }

    /** {@link #where} with the road the train's tail lies along on the railway. */
    private static String whereWithItsTail(String name)
    {
        org.traincontrol.automation.Point on = model.getAutoLayout().getLocomotiveLocation(model.getLocByName(name));

        return where(name) + " along " + (on == null ? null : roadNames(on.getArrivedAlong()));
    }

    /** What the setup and the railway's copies hold on a square: the train, its facing, its side and its tail. */
    private static String station(TileKey square)
    {
        return Components.onTheEventThread(() ->
        {
            StringBuilder out = new StringBuilder("setup " + session.getLocomotiveNameAt(square));

            for (String property : new String[] {"facing", "arrivedFrom", "arrivedAlong"})
            {
                out.append(' ').append(property).append('=').append(session.getPointProperty(square, property));
            }

            for (String copy : new java.util.TreeSet<>(session.facingsFor(square).keySet()))
            {
                org.traincontrol.automation.Point point = model.getAutoLayout().getPoint(copy);

                if (point == null) continue;

                out.append("; ").append(copy).append(' ').append(point.getCurrentLocomotive() == null ? null
                    : point.getCurrentLocomotive().getName()).append(" from ").append(point.getArrivedFrom())
                    .append(" along ").append(roadNames(point.getArrivedAlong()));
            }

            return out.toString();
        });
    }
}
