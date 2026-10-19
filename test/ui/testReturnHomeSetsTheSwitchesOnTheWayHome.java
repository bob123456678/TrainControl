package ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Restart;

/**
 * Return Home, pressed in the window, runs a train home through the turnout, and the turnout is left as the way home
 * set it (list 2's Return Home row: the switches).
 *
 * On the `single-switch` railway, simulated, with its track measured and its stations named on the diagram: a train put on BranchPlatform by hand, which makes
 * that platform its home, and then put on WestEnd.  Its way home is WestEnd, Approach and the turnout's branch.  The turnout is put the other
 * way first, so the run has something to set.
 *
 * MUTATION: in `Layout.loadReturnToHomeTimetable`, take out `staged.add(entry);`, and the train stays where it is and
 * the turnout as it was.
 *
 * @author Adam
 */
public class testReturnHomeSetsTheSwitchesOnTheWayHome
{
    private static final String TRAIN = "RH homeward train";

    private static final String HOME = "BranchPlatform";
    private static final String AWAY = "WestEnd";

    private static final long RUN_MS = 60000;

    /** The squares between the sensors, which carry the scenario's track lengths. */
    private static final int[][] BETWEEN = { {2, 3}, {4, 3}, {6, 3}, {5, 2} };

    /** The four stations' squares: WestEnd, Approach, BranchPlatform, MainPlatform. */
    private static final int[][] STATIONS = { {1, 3}, {3, 3}, {6, 2}, {7, 3} };

    private static Restart restart;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        restart = Restart.open(support.Scenario.folderFor("single-switch"));

        model = restart.getModel();

        final TrainControlUI ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui.getActiveDiagramConfiguration(), "precondition: the scenario's setup did not load");

        // The track between the sensors measured, and each station showing its own name, which the scenario leaves to
        // the test, so the setup can be run
        final String page = model.getLayoutList().get(0);

        for (int[] square : BETWEEN)
        {
            ui.getAutonomySession().setTileLength(new TileKey(page, square[0], square[1]), 1);
        }

        for (int[] square : STATIONS)
        {
            TileKey station = new TileKey(page, square[0], square[1]);

            ui.getAutonomySession().setCaption(station, station);
        }

        final String active = ui.getActiveDiagramConfiguration();

        SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

        Components.settle();

        assertNotNull(model.getAutoLayout(), "precondition: the scenario's setup did not load again");

        if (model.getLocByName(TRAIN) == null) model.newMM2Locomotive(TRAIN, 67);

        model.getLocByName(TRAIN).setPreferredSpeed(30);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null && model.getAutoLayout() != null) model.getAutoLayout().stopLocomotives();
        }
        finally
        {
            if (restart != null) restart.close();
        }
    }

    /**
     * Return Home brings the train to its home platform, and the turnout on its way reads as the way home set it.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testReturnHomeRunsTheTrainHomeThroughTheTurnout() throws Exception
    {
        final Layout railway = model.getAutoLayout();

        railway.setSimulate(true);
        railway.setMinDelay(0);
        railway.setMaxDelay(0);

        final Locomotive train = model.getLocByName(TRAIN);

        // Put on its home platform first, which makes it the train's home, then by hand on the far end
        final Point home = place(railway, HOME);

        assertEquals(railway.getHomeStations().get(train), home, "precondition: putting the train on " + HOME
            + " did not make it the train's home");

        final Point away = place(railway, AWAY);

        final List<Edge> way = railway.bfs(away, home, null);

        assertTrue(way != null && !way.isEmpty(), "precondition: there is no way from " + away.getName() + " home to "
            + home.getName());

        final Map<String, Accessory.accessorySetting> settings = new LinkedHashMap<>();

        for (Edge e : way) settings.putAll(e.getConfigCommands());

        assertTrue(!settings.isEmpty(), "precondition: the way home sets no turnout");

        for (Map.Entry<String, Accessory.accessorySetting> c : settings.entrySet())
        {
            model.getAccessoryByName(c.getKey()).setSwitched(!Accessory.isThrow(c.getValue()));
        }

        final org.traincontrol.automationui.AutonomySession session = ((TrainControlUI) model.getGUI()).getAutonomySession();

        assertTrue(!session.hasErrors(), "precondition: the setup cannot be run: " + session.check());

        if (!model.getPowerState()) model.go();

        assertTrue(model.waitForPowerState(true, 10000), "precondition: the power is not on");

        // The Auto tab's Return Home button, by its field, saying what the operator reads on it
        final java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField("returnHomeButton");

        field.setAccessible(true);

        final AbstractButton returnHome = (AbstractButton) field.get(model.getGUI());

        assertEquals(Components.onTheEventThread(returnHome::getText), I18n.t("ui.main.returnHome"),
            "the window's Return Home does not say so");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.select(returnHome);

            Components.press(returnHome);

            assertTrue(Components.waitFor(() -> home.equals(railway.getLocomotiveLocation(train))
                && railway.getActiveLocomotives().isEmpty(), RUN_MS), "Return Home did not bring the train home to "
                + home.getName() + "; it is at " + railway.getLocomotiveLocation(train) + ", and the window said "
                + answering.asked());
        }

        for (Map.Entry<String, Accessory.accessorySetting> c : settings.entrySet())
        {
            assertEquals(model.getAccessoryByName(c.getKey()).isSwitched(), Accessory.isThrow(c.getValue()), c.getKey()
                + " was not set " + c.getValue() + " by the run home along " + railway.pathToString(way));
        }
    }

    /**
     * Puts the train by hand on the first copy of the named station, by name, that takes it.
     */
    private static Point place(Layout railway, String station)
    {
        List<Point> copies = new ArrayList<>();

        for (Point p : railway.getPoints())
        {
            if (p.getName().startsWith(station) && p.isDestination()) copies.add(p);
        }

        copies.sort(java.util.Comparator.comparing(Point::getName));

        for (Point p : copies)
        {
            if (railway.moveLocomotive(TRAIN, p.getName(), false)) return p;
        }

        throw new AssertionError("precondition: the train could not be put on " + station + "; its copies are " + copies);
    }
}
