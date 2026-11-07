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
import org.traincontrol.gui.autonomyeditor.TrainWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.AskingWindows;
import support.Components;
import support.Mouse;
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
     * Blue is for section headings and the train popup has none: its row labels (Train, Active, Facing, Arrived from, Tail
     * lies) are black Semibold 13, as Station Settings' are, and nothing in it is blue.
     *
     * MUTATION: build a row label with `UiStyle.label`, which is blue, and this fails.
     */
    @Test(timeOut = 120000)
    public void testTheRowLabelsAreBlack() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TrainWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        java.util.List<String> blue = new java.util.ArrayList<>();

        for (javax.swing.JLabel each : Components.all(window, javax.swing.JLabel.class, null))
        {
            if (org.traincontrol.gui.widgets.UiStyle.BLUE.equals(each.getForeground())) blue.add(each.getText());
        }

        assertTrue(blue.isEmpty(), "blue in a window with no section headings: " + blue);

        for (String key : new String[] {"autosetup.ui.trainTrain", "autosetup.ui.trainActive", "autosetup.ui.trainFacing",
            "autosetup.ui.trainArrived", "autosetup.ui.trainTail"})
        {
            java.util.List<javax.swing.JLabel> found = Components.all(window, javax.swing.JLabel.class,
                label -> I18n.t(key).equals(label.getText()));

            assertEquals(found.size(), 1, key + " is not one label of the window");
            assertEquals(found.get(0).getForeground(), java.awt.Color.BLACK, key + " is not black");
            assertEquals(found.get(0).getFont(), org.traincontrol.gui.widgets.UiStyle.LABEL_FONT, key + " is not Semibold 13");
        }

        StationDoors.close(window);
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

        TrainWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

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

        TrainWindow window = StationDoors.openTrain(panel, square);

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

                TrainWindow window = StationDoors.openTrain(panel, square);

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

            TrainWindow window = StationDoors.openTrain(panel, square);

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
                I18n.f("autosetup.ui.menuTrainAt", session.describeTile(standing))));

            Components.settle();

            TrainWindow window = StationDoors.showing(TrainWindow.class);

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

        TrainWindow window = StationDoors.openTrain(panel, square);

        StationDoors.press(window, "train.settings");

        LocomotiveSettingsFrame frame = StationDoors.showing(LocomotiveSettingsFrame.class);

        assertNotNull(frame, "Locomotive Settings did not open");

        assertEquals(frame.getLocomotive(), model.getLocByName(session.getLocomotiveNameAt(square)),
            "Locomotive Settings opened on another locomotive");
        assertEquals(support.SettingsWindow.tabShowing(), LocomotiveSettingsFrame.Tab.AUTONOMY.ordinal(),
            "Locomotive Settings did not open on the Autonomy tab");

        SwingUtilities.invokeAndWait(frame::dispose);
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
            I18n.f("autosetup.ui.menuTrainAt", session.describeTile(square)),
            I18n.f("autosetup.ui.menuRemoveLocomotive", train), I18n.f("autolayout.ui.menuLocomotiveActive", train)})
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
            I18n.f("autosetup.ui.menuTrainAt", session.describeTile(full))})
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

        for (String item : new String[] {I18n.t("autolayout.ui.labelEditLocomotiveAt"),
            I18n.f("layout.ui.menuPlaceLocomotive", ui.getActiveLoc().getName()),
            I18n.f("autosetup.ui.menuTrainAt", session.describeTile(full))})
        {
            assertTrue(AskingWindows.hasItem(menu, item), "the diagram's menu over a train has no \"" + item + "\"");
        }

        Mouse.choose(AskingWindows.itemOf(menu, I18n.t("autolayout.ui.labelEditLocomotiveAt")));

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
     * MUTATION: leave `addEditLocomotive` out of `addWhileRunning`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testDuringARunTheDiagramsMenuStillEditsTheTrain() throws Exception
    {
        TileKey full = aSquareWithATrain();

        String train = session.getLocomotiveNameAt(full);

        AskingWindows.showThePageOf(ui, full);

        final java.lang.reflect.Field staging = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        staging.setAccessible(true);

        try
        {
            Components.onTheEventThread(() -> { staging.set(ui, true); return null; });

            JPopupMenu running = AskingWindows.menuOn(ui, ui, full);

            assertTrue(AskingWindows.hasItem(running, I18n.t("autolayout.ui.menuStopAutonomyGracefully")),
                "precondition: this is not the running menu");

            assertTrue(AskingWindows.hasItem(running, I18n.t("autolayout.ui.labelEditLocomotiveAt")),
                "the running menu over a train has no Edit Locomotive...");

            Mouse.closeMenus();
        }
        finally
        {
            Components.onTheEventThread(() -> { staging.set(ui, false); return null; });
        }

        model.getAutoLayout().setStagingInProgress(true);

        try
        {
            assertTrue(ui.aRunHoldsTheSetup(), "precondition: no run holds the setup");

            JPopupMenu held = AskingWindows.menuOn(ui, ui, full);

            Mouse.choose(AskingWindows.itemOf(held, I18n.t("autolayout.ui.labelEditLocomotiveAt")));

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
        assertTrue(AskingWindows.hasItem(menu, I18n.t("autolayout.ui.labelEditLocomotiveAt")),
            "the diagram's menu lost Edit Locomotive... with its own train selected");
        assertTrue(AskingWindows.hasItem(menu, I18n.f("autosetup.ui.menuTrainAt", session.describeTile(full))),
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

        assertFalse(AskingWindows.hasItem(menu, I18n.t("autolayout.ui.labelEditLocomotiveAt")),
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

        assertNull(Components.withText(menu, I18n.t("autolayout.ui.labelEditLocomotiveAt")),
            "the editor's menu offers Edit Locomotive...");
        assertNotNull(Components.withText(menu, I18n.f("autosetup.ui.menuTrainAt", session.describeTile(square))),
            "the editor's menu has no train window");
    }
}
