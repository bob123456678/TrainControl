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
            TrainWindow window = StationDoors.openTrain(panel, support.StandingTrain.squareNamed(session, "Tunnel"));

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

        final java.lang.reflect.Method exit = LayoutEditor.class.getDeclaredMethod("confirmExit");

        exit.setAccessible(true);

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
        TrainWindow place(String name) throws Exception;
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

        TrainWindow window = null;

        String was = aTrainOffTheRailway();

        final Locomotive displaced = model.getLocByName(was);

        org.traincontrol.gui.TailCrossedPrompt.answerForTests(org.traincontrol.gui.TailCrossedPrompt.DISMISSED);

        TrainWindow first = door.place(was);

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
