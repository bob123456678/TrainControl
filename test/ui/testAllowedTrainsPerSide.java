package ui;

import java.util.Collections;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.AutonomySession.WhichTrains;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.autonomyeditor.StationSettingsWindow;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.StationDoors;

/**
 * Allowed trains per arrival side in Station Settings (FR-260), on `single-switch`'s Approach: the one row while the
 * cells agree, the grid under it once they differ, the user's St31Apost rule set from the window and routed as
 * `core.testTrainsBySide` routes it, and cells set back to agree folding the grid and saving as before.
 */
public class testAllowedTrainsPerSide
{
    private static final String PAGE = "1 - Junction";
    private static final String Q = "Grid Q";
    private static final String MX = "Grid MX 26";

    private static support.FrozenEditor frozen;
    private static AutonomySession session;
    private static TileKey approach;

    private LayoutEditor editor;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        frozen = support.FrozenEditor.window("single-switch");
        session = frozen.sessionOrFail("single-switch has no autonomy setup");
        approach = support.AskingWindows.stationNamed(session, "Approach");

        if (frozen.model.getLocByName(Q) == null) frozen.model.newMFXLocomotive(Q, 3131);
        if (frozen.model.getLocByName(MX) == null) frozen.model.newMFXLocomotive(MX, 3132);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (frozen == null) return;

        try
        {
            for (String name : new String[] {Q, MX}) if (frozen.model.getLocByName(name) != null) frozen.model.deleteLoc(name);
        }
        finally
        {
            frozen.close();
        }
    }

    @BeforeMethod
    public void openTheEditor() throws Exception
    {
        if (frozen == null) throw new SkipException("the class did not set up");

        frozen.putBack();
        editor = frozen.editorOn(PAGE);
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheEditor() throws Exception
    {
        try
        {
            support.AskingWindows.closeEveryDialog();
        }
        finally
        {
            support.FrozenEditor.dispose(editor);
        }
    }

    /**
     * A square whose cells agree shows the one row with Per side... and no grid, and Per side... shows the grid without
     * writing one, its (i) explaining the grid; a square whose cells differ shows the grid at once, each cell's choice and
     * list as the setup has them.
     *
     * MUTATION: have `showWhich` ignore `hasTrainsBySide`, and the second window shows the row; have the (i) read
     * `grid` out of `fill`, and Per side... leaves it on the one list.
     */
    @Test(timeOut = 120000)
    public void testTheRowWhileTheCellsAgreeAndTheGridOnceTheyDiffer() throws Exception
    {
        on(() -> session.setWhichTrains(approach, WhichTrains.WHITELIST, Collections.singletonList(Q)));

        StationSettingsWindow window = opened();

        assertTrue(showing(window, "station.whichWhite") && StationDoors.on(window, "station.whichWhite"),
            "a square whose cells agree does not show its one list on the row");
        assertTrue(showing(window, "station.whichPerSide"), "Per side... is not offered");
        assertFalse(showing(window, "station.whichGrid"), "a square whose cells agree shows the grid");

        StationDoors.press(window, "station.whichPerSide");

        assertTrue(showing(window, "station.whichGrid"), "Per side... showed no grid");
        StationSettingsWindow agreeing = window;

        assertTrue(Components.onTheEventThread(() -> agreeing.flyoverForTest("which")).contains(I18n.t("autosetup.ui.flyWhichBySide")),
            "the row's (i) does not explain the grid");
        assertFalse(showing(window, "station.whichWhite"), "the row still shows over the grid");
        assertFalse(Components.onTheEventThread(() -> session.hasTrainsBySide(approach)), "Per side... alone wrote a grid");

        StationDoors.close(window);

        on(() -> session.setWhichTrains(approach, Side.E, true, WhichTrains.WHITELIST, Collections.singletonList(MX)));

        window = opened();

        assertTrue(showing(window, "station.whichGrid"), "a square whose cells differ shows no grid");
        assertFalse(showing(window, "station.whichPerSide"), "Per side... shows beside the grid");
        assertEquals(shown(window, "station.which.E.through"), I18n.t("autosetup.ui.stationWhichWhite"),
            "the cell set does not show its choice");
        assertEquals(StationDoors.text(window, "station.which.E.through.names"), MX, "the cell set does not show its list");
        assertEquals(StationDoors.text(window, "station.which.W.stop.names"), Q, "a Stop cell does not show the one list");
        assertEquals(shown(window, "station.which.W.through"), I18n.t("autosetup.ui.stationWhichAll"),
            "a station's Run through cell does not read All");
    }

    /**
     * The user's St31Apost rule set from the window: Changing direction May, Per side..., then from the west a whitelist
     * of nobody to stop, from the east only Q to stop and only MX 26 to run through.  The setup holds the grid, and the
     * railway built from it lets Q run west to east but only MX 26 east to west.  With Q at home there, a Stop cell
     * keeping Q out asks about the home, as the one list does; a Run through cell, whose list or choice keeps Q out, asks
     * nothing.
     *
     * MUTATION: have `pressCell` prompt for the square's one list (a null side), and this fails; drop `!through` from
     * `EditorPrompts.promptTrainList` or from `AutonomyEditorPanel.setWhichTrains`, and Run through asks about the home.
     */
    @Test(timeOut = 180000)
    public void testTheSt31RuleSetFromTheWindowRoutes() throws Exception
    {
        on(() -> session.setPointProperty(approach, "home", Q));

        StationSettingsWindow window = opened();
        String white = I18n.t("autosetup.ui.stationWhichWhite");

        StationDoors.press(window, "station.turnMay");
        StationDoors.press(window, "station.whichPerSide");

        assertEquals(StationDoors.pickAndAnswer(window, "station.which.W.stop", white, message -> StationDoors.choosing()).size(),
            2, "a Stop cell keeping Q out did not ask about its home");
        StationDoors.pickAndAnswer(window, "station.which.E.stop", white, message -> StationDoors.choosing(Q));
        assertEquals(StationDoors.pickAndAnswer(window, "station.which.E.through", white, message -> StationDoors.choosing(MX))
            .size(), 1, "a Run through cell's list asked about the home");
        StationDoors.pick(window, "station.which.E.through", I18n.t("autosetup.ui.stationWhichAll"));
        assertEquals(StationDoors.pickAndAnswer(window, "station.which.E.through", white, message -> Answering.pressAt(0)),
            Collections.emptyList(), "a Run through cell's choice asked about the home");

        on(() ->
        {
            assertEquals(session.getTrainList(approach, Side.W, false), Collections.emptyList(), "from the west somebody stops");
            assertEquals(session.getWhichTrains(approach, Side.W, false), WhichTrains.WHITELIST, "from the west all may stop");
            assertEquals(session.getTrainList(approach, Side.E, false), Collections.singletonList(Q), "from the east, Stop");
            assertEquals(session.getTrainList(approach, Side.E, true), Collections.singletonList(MX), "from the east, Run through");
            assertEquals(session.getWhichTrains(approach, Side.W, true), WhichTrains.ALL, "from the west, Run through");
        });

        assertTrue(showing(window, "station.whichGrid"), "the grid folded while its cells differ");

        Layout railway = Layout.fromJSON(Components.onTheEventThread(session::buildConfiguration), frozen.model);

        assertTrue(railway.isValid(), "the setup does not build: " + Layout.getLastError());
        assertTrue(overApproach(railway, Q, "WestEnd", "MainPlatform"), "Q may not run west to east over Approach");
        assertTrue(overApproach(railway, MX, "MainPlatform", "WestEnd"), "MX 26 may not run east to west over Approach");
        assertFalse(overApproach(railway, Q, "MainPlatform", "WestEnd"),
            "Q runs east to west over Approach, whose Run through from the east names only MX 26");
    }

    /**
     * Cells set back to agree fold the grid into the one row, which reads the one list, and the setup is as it was before
     * the grid, key for key: only the one list's keys are saved.
     *
     * MUTATION: have `AutonomySession.storeGrid` keep the grid when its cells agree, and this fails.
     */
    @Test(timeOut = 120000)
    public void testCellsSetBackToAgreeFoldTheGridAndSaveAsBefore() throws Exception
    {
        on(() -> session.setWhichTrains(approach, WhichTrains.WHITELIST, Collections.singletonList(Q)));

        String before = setup();
        StationSettingsWindow window = opened();

        StationDoors.press(window, "station.whichPerSide");
        StationDoors.pickAndAnswer(window, "station.which.E.through", I18n.t("autosetup.ui.stationWhichWhite"),
            message -> StationDoors.choosing(MX));

        assertTrue(Components.onTheEventThread(() -> session.hasTrainsBySide(approach)), "precondition: no grid was written");

        StationDoors.pick(window, "station.which.E.through", I18n.t("autosetup.ui.stationWhichAll"));

        assertFalse(Components.onTheEventThread(() -> session.hasTrainsBySide(approach)), "cells that agree kept the grid");
        assertFalse(showing(window, "station.whichGrid"), "the grid did not fold once its cells agreed");
        assertTrue(StationDoors.on(window, "station.whichWhite"), "the row does not read the one list");
        assertEquals(setup(), before, "cells set back to agree saved differently");
    }

    /**
     * A square that is not a station heeds only Run through: its Stop cells gray and its (i) says so, while a Run through
     * cell's pencil sets that cell's list.
     *
     * MUTATION: have `whichCell` count every cell as heeded, or drop the track line from `StationFlyovers.whichBySide`,
     * or have the pencil ask for the one list (a null side), and this fails.
     */
    @Test(timeOut = 120000)
    public void testANonStationGraysItsStopCellsAndThePencilSetsItsCell() throws Exception
    {
        on(() ->
        {
            session.setStopsAndRunsThrough(approach, false, true);
            session.setWhichTrains(approach, Side.E, true, WhichTrains.WHITELIST, Collections.singletonList(MX));
        });

        StationSettingsWindow window = opened();

        assertFalse(StationDoors.live(window, "station.which.E.stop"), "a non-station's Stop cell can be set");
        assertTrue(StationDoors.live(window, "station.which.E.through"), "a non-station's Run through cell cannot be set");
        assertTrue(Components.onTheEventThread(() -> window.flyoverForTest("which"))
            .contains(I18n.t("autosetup.ui.flyWhichBySideTrack")), "the (i) does not say only Run through applies");

        StationDoors.press(window, "station.which.E.through.choose", message -> StationDoors.choosing(Q, MX));

        on(() -> assertEquals(new java.util.HashSet<>(session.getTrainList(approach, Side.E, true)),
            new java.util.HashSet<>(java.util.Arrays.asList(Q, MX)), "the pencil did not set the cell's list"));
        on(() -> assertEquals(session.getWhichTrains(approach, Side.W, true), WhichTrains.ALL, "the pencil set another cell"));
    }

    /** Whether this train, standing at `from`, has a trip to `to` over Approach. */
    private static boolean overApproach(Layout railway, String train, String from, String to)
    {
        Locomotive loc = frozen.model.getLocByName(train);
        List<String> copies = session.getStationIndex().pointNamesAt(approach);

        for (Point each : railway.getPoints()) if (each.getCurrentLocomotive() != null) each.setLocomotive(null);

        railway.getPoint(from).setLocomotive(loc);

        for (List<Edge> path : railway.getPossiblePaths(loc, false))
        {
            boolean over = path.stream().anyMatch(e -> copies.contains(e.getEnd().getName()));

            if (over && to.equals(path.get(path.size() - 1).getEnd().getName())) return true;
        }

        return false;
    }

    private StationSettingsWindow opened() throws Exception
    {
        AutonomyEditorPanel panel = Components.onTheEventThread(editor::getAutonomyPanel);
        StationSettingsWindow window = StationDoors.open(panel, approach);

        Components.onTheEventThread(() -> { window.openAdvancedForTest(); return null; });

        StationDoors.current(window);

        return window;
    }

    private static void on(Runnable change)
    {
        Components.onTheEventThread(() -> { change.run(); return null; });
    }

    private static String setup()
    {
        return Components.onTheEventThread(() -> session.snapshotSetup().toString());
    }

    private static Object shown(java.awt.Component window, String name)
    {
        JComboBox<?> list = StationDoors.part(window, name, JComboBox.class);

        return Components.onTheEventThread(list::getSelectedItem);
    }

    private static boolean showing(java.awt.Component window, String name)
    {
        return Components.onTheEventThread(() ->
        {
            JComponent part = Components.named(window, name, JComponent.class);

            return part != null && part.isShowing();
        });
    }
}
