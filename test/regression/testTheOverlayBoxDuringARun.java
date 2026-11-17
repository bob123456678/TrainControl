package regression;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.AutonomyOverlayToggle;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import support.Components;
import support.Reflect;

/**
 * The track diagram's autonomy box (OB-496, Adam: "the checkbox does not hide the orange lines associated with train
 * locations.  also, make sure it can be safely toggled on and off during a run"), on the frozen railway in simulation,
 * with the production monitor and a tile on every square.
 *
 * @author Adam
 */
public class testTheOverlayBoxDuringARun
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;
    private static ExecutorService watchdog;
    private static JCheckBox box;
    private static Locomotive train;
    private static Integer trainLengthWas;
    private static boolean simulatingWas;

    /** The parent of the tiles this class registers. */
    private static final JPanel OURS = new JPanel();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        simulatingWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        Components.drain(1);
        Components.drain(1);

        session = ui.getAutonomySession();

        assertNotNull(session, "the sandbox copy holds no autonomy setup");

        AutonomyOverlayToggle strip = (AutonomyOverlayToggle) Reflect.get(TrainControlUI.class, ui, "autonomyOverlayToggle");

        assertNotNull(strip, "the track diagram has no autonomy strip");

        box = (JCheckBox) Reflect.get(AutonomyOverlayToggle.class, strip, "show");
        watchdog = Executors.newSingleThreadExecutor();

        // Every square one unit long, so a one-unit train finds room wherever it goes
        SwingUtilities.invokeAndWait(() -> support.TileLengths.measureEveryTileAs(session, 1));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the setup did not build");

        train = support.CoveredMarks.leaveOneTrainThatCovers(layout, 2);

        assertNotNull(train, "no train on this railway covers track");

        trainLengthWas = train.getTrainLength();

        train.setTrainLength(2);

        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        SwingUtilities.invokeAndWait(() ->
        {
            ui.getDiagramMonitorDriver().bind(session);
            ui.getDiagramMonitorDriver().attach();
            ui.getDiagramMonitorDriver().start();
        });

        for (TileKey square : everyTile())
        {
            LayoutDiagramComponent art = session.getGraph().getTiles().get(square);

            if (art == null || art.isText()) continue;

            LayoutLabel label = new LayoutLabel(art, OURS, 30, ui, false);

            label.setSquare(square);

            ui.getDiagramTileRegistry().register(square, label);
        }

        Components.drain(1);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = simulatingWas;

            if (layout != null) layout.stopLocomotives();

            if (train != null) train.setTrainLength(trainLengthWas == null ? 0 : trainLengthWas);

            if (box != null) tick(true);
        }
        finally
        {
            if (watchdog != null) watchdog.shutdownNow();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Unticked, no square draws a train's orange line; ticked again, every square the train lies on draws it.
     *
     * MUTATION: have `LayoutLabel`'s `trainsShown` default to `() -> true` again, and this fails.
     */
    @Test
    public void testUntickingTheBoxHidesTheTrainsLines() throws Exception
    {
        tick(true);
        support.CoveredMarks.refresh(ui);

        List<LayoutLabel> covered = coveredTiles();

        assertFalse(covered.isEmpty(), "precondition: the train lies on no square");

        for (LayoutLabel tile : covered)
        {
            assertTrue(support.Rendered.showsTheTrainMark(tile), "precondition: a square the train lies on draws no line");
        }

        tick(false);

        for (LayoutLabel tile : covered)
        {
            assertFalse(support.Rendered.showsTheTrainMark(tile), "with the box unticked, a square the train lies on still"
                + " draws its orange line");
        }

        tick(true);

        for (LayoutLabel tile : covered)
        {
            assertTrue(support.Rendered.showsTheTrainMark(tile), "with the box ticked again, a square the train lies on"
                + " draws no line");
        }
    }

    /**
     * Toggled again and again while a train runs to another station: every click goes through, the setup stays shut to
     * edits all the while, the train arrives, and once ticked the diagram draws it where it now stands, not where it was.
     *
     * MUTATION: have `AutonomyOverlayToggle.apply` stop the monitor rather than switch it off, and this fails.
     */
    @Test(dependsOnMethods = "testUntickingTheBoxHidesTheTrainsLines", timeOut = 240000)
    public void testTogglingTheBoxWhileATrainRunsCostsTheRunNothing() throws Exception
    {
        tick(true);

        Point from = layout.getLocomotiveLocation(train);
        List<Edge> path = null;

        for (Point candidate : layout.getPoints())
        {
            if (candidate.isSamePlaceAs(from) || !candidate.isDestination() || candidate.isTerminus()
                || candidate.isReversing()) continue;

            List<Edge> out = layout.bfs(from, candidate, null);

            if (out == null || out.size() < 2 || !layout.isPathClear(out, train, false)) continue;

            path = out;

            break;
        }

        assertNotNull(path, "precondition: the train has nowhere two legs away to go");

        Point to = path.get(path.size() - 1).getEnd();
        final List<Edge> leg = path;

        Future<Boolean> run = watchdog.submit(() -> layout.executePath(leg, train, 30, null));

        int clicks = 0;
        long until = System.currentTimeMillis() + 120000;

        while (!run.isDone() && System.currentTimeMillis() < until)
        {
            tick(!box.isSelected());

            clicks++;

            boolean held = ui.aRunHoldsTheSetup();

            if (!run.isDone()) assertTrue(held, "after click " + clicks + " of the box the setup could be edited mid-run");

            try
            {
                run.get(150, TimeUnit.MILLISECONDS);
            }
            catch (java.util.concurrent.TimeoutException stillRunning)
            {
                // the next click
            }
        }

        assertTrue(run.get(1, TimeUnit.SECONDS), "the train did not arrive at " + to.getName() + " with the box toggled");
        assertTrue(clicks >= 4, "precondition: the box was toggled only " + clicks + " times while the train ran");

        assertTrue(layout.getLocomotiveLocation(train).isSamePlaceAs(to), "the train is at "
            + layout.getLocomotiveLocation(train).getName() + ", not " + to.getName());

        tick(true);
        support.CoveredMarks.refresh(ui);
        Components.drain(3);

        assertTrue(ui.getDiagramMonitorDriver().isEnabled(), "the monitor is off with the box ticked");

        TileKey there = session.getStationIndex().squareOf(to.getName());
        TileKey before = session.getStationIndex().squareOf(from.getName());

        for (LayoutLabel tile : ui.getDiagramTileRegistry().labelsFor(there))
        {
            assertTrue(support.Rendered.showsTheTrainMark(tile), "with the box ticked again the train is not drawn where"
                + " it stands, " + to.getName());
        }

        if (!ui.isTrackCovered(before))
        {
            for (LayoutLabel tile : ui.getDiagramTileRegistry().labelsFor(before))
            {
                assertFalse(support.Rendered.showsTheTrainMark(tile), "with the box ticked again the train is still drawn"
                    + " where it started, " + from.getName());
            }
        }

        assertTrue(Components.waitFor(() -> ui.getDiagramTileRegistry().overlayAt(there) != null, 10000), "with the box"
            + " ticked again the monitor draws nothing where the train stands");
    }

    /** Sets the box as a click does, through its own listener. */
    private static void tick(boolean on) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            if (box.isSelected() != on) box.doClick();
        });

        Components.drain(1);
    }

    /** The tiles this class registered on every square the train is drawn across. */
    private static List<LayoutLabel> coveredTiles() throws Exception
    {
        List<LayoutLabel> out = new ArrayList<>();

        for (TileKey square : everyTile())
        {
            if (ui.isTrackCovered(square)) out.addAll(ui.getDiagramTileRegistry().labelsFor(square));
        }

        return out;
    }

    private static Set<TileKey> everyTile()
    {
        Set<TileKey> out = new LinkedHashSet<>(session.getReducer().getPoints().keySet());

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) out.add(step.getTile());
            }
        }

        return out;
    }
}
