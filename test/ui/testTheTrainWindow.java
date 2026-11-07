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
 * The train window and the menus that lead to it and to Station Settings (FR-111, FR-059, FR-072, OB-346 part 2), pressed
 * as the user presses them on the frozen railway.
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
            if (PAGE.equals(each.getKey().getPage()) && session.getStore().isStation(each.getKey())) return each.getKey();
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
                    model.getLocByName(each.getValue())) && session.getStore().isStation(each.getKey())) square = each.getKey();
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
     * Locomotive Settings opens that train's window, over the window that asked.
     *
     * MUTATION: have `pressSettings` skip `pressFromWindow`, and it hangs off the main window and this fails.
     */
    @Test(timeOut = 120000)
    public void testLocomotiveSettingsOpensThatTrain() throws Exception
    {
        AutonomyEditorPanel panel = theEditorsPanel();

        TrainWindow window = StationDoors.openTrain(panel, aSquareWithATrain());

        StationDoors.press(window, "train.settings");

        LocomotiveSettingsFrame frame = StationDoors.showing(LocomotiveSettingsFrame.class);

        assertNotNull(frame, "Locomotive Settings did not open");

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

        // Not twice: mapping the active train to its button again takes it off
        if (ui.getActiveLoc() == null || !active.equals(ui.getActiveLoc().getName())) ui.mapLocToCurrentButton(active);

        assertTrue(Components.waitFor(() -> ui.getActiveLoc() != null && active.equals(ui.getActiveLoc().getName()), 10000),
            "precondition: " + active + " could not be made the active locomotive");

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
}
