package regression;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * MT-334's shape, asked of the grey wash that came back on 2026-09-09.
 *
 * Adam asked for the wash back beside the line and said how: *"That plus the line, **drawn and
 * refreshed carefully**, should do the trick."*  This codebase has already paid for the other kind of
 * refresh - MT-334 was the whole diagram being rebuilt because one pathing arrow changed, 384 tiles
 * out of 384, and what he saw was the page flickering.
 *
 * **Two things are asserted, and they are different claims.**
 *
 * - **No rebuild.**  The tiles of the page are collected as objects before autonomy starts and again
 *   after, and compared by identity.  A rebuild replaces every `LayoutLabel` in the grid; a refresh
 *   leaves them where they are.  That is exactly how `testTheDiagramIsNotRebuiltForAnArrow` measures
 *   the same failure, and it is the measurement that failure has.
 *
 * - **And only what changed is redrawn.**  Identity alone would be satisfied by a refresh that told
 *   every tile on the page to repaint - no flicker from rebuilding, but a full-page repaint on every
 *   train movement, and the refresh runs whenever anything about a train changes.  So a counting
 *   `RepaintManager` is installed for the duration of the gesture and records which components were
 *   made dirty.  Every one of them has to be a tile of a square whose state actually changed.
 *
 * **The changed squares are worked out from the railway, not from the window.**  `blockedSquares()`
 * translates `Layout.edgesCoveredByStandingTrains` through the reducer here, the same way
 * `ui.testBlockedTrackIsGreyWhileAutonomyRuns` does and for the same reason: asking the window under
 * test what the right answer is agrees with it whatever it says.
 *
 * **Both directions of the gesture.**  Starting autonomy puts the wash on and stopping it takes the
 * wash off, and the second is the half a set comparison makes easy to forget - it is the half OB-180
 * was reported about.
 *
 * MUTATION: make `repaintTheWashWhereItChanged` repaint every registered tile and
 * `testStartingAutonomyRedrawsOnlyTheSquaresThatChanged` fails.  Drop the blocked set from the
 * comparison it makes and `testStoppingAutonomyTakesTheWashOffAgain` fails with nothing redrawn.
 *
 * @author Adam
 */
public class testTheGreyDoesNotRebuildTheDiagram
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;

    private static LayoutDiagram page;
    private static LayoutGrid grid;
    private static final JPanel HOST = new JPanel();

    private static final int TILE = 30;

    private static Locomotive train;
    private static Integer trainLengthWas;

    /** The tiles of the page, as objects, before the gesture and after each half of it. */
    private static List<LayoutLabel> tilesBefore;
    private static List<LayoutLabel> tilesAfterStart;
    private static List<LayoutLabel> tilesAfterStop;

    /** The squares the railway blocks while the train stands where it does. */
    private static Set<TileKey> blocked;

    /** Which tiles were made dirty by starting autonomy, and by stopping it again. */
    private static Set<JComponent> onStart;
    private static Set<JComponent> onStop;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("building a page of tiles needs a display");
        }

        sandbox = support.LayoutSandbox.open(new File("cs2_sample_layout"));

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new SkipException("no autonomy setup on this railway");

        SwingUtilities.invokeAndWait(() -> measureEveryTileAs(1));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        oneTrainThatCoversSomething();

        if (train == null) throw new SkipException("no standing train covers any track here");

        setRunning(false);

        refreshCoveredTrack();

        blocked = blockedSquares();

        if (blocked.isEmpty())
        {
            throw new SkipException("the standing train blocks no square, so there is no wash to add");
        }

        page = model.getLayout(blocked.iterator().next().getPage());

        if (page == null) throw new SkipException("the blocked squares are on no page this model has");

        SwingUtilities.invokeAndWait(() ->
        {
            // popup = true lays the grid out to its own natural size; master = null keeps it out of
            // editor mode, which is where the marks are drawn.  The same construction support.Rendered
            // uses.
            grid = new LayoutGrid(page, TILE, HOST, null, true, ui);
        });

        settle();

        tilesBefore = tilesOf(HOST);

        if (tilesBefore.size() < 10)
        {
            throw new SkipException("the page drew " + tilesBefore.size() + " tiles - too few to say"
                + " anything about a full-page redraw");
        }

        Counting counter = new Counting();

        RepaintManager was = RepaintManager.currentManager(HOST);

        try
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(counter));

            // THE GESTURE: autonomy starts, and the window recomputes what is covered.  Nothing else
            // is touched, so every repaint counted below is one this refresh asked for.
            counter.clear();

            setRunning(true);

            refreshCoveredTrack();

            onStart = counter.seen();

            tilesAfterStart = tilesOf(HOST);

            // AND THE OTHER HALF - the wash has to come off again.
            counter.clear();

            setRunning(false);

            refreshCoveredTrack();

            onStop = counter.seen();

            tilesAfterStop = tilesOf(HOST);
        }
        finally
        {
            final RepaintManager restore = was;

            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(restore));
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (layout != null)
            {
                setRunning(false);

                layout.stopLocomotives();
            }

            // PUT BACK: init() opens Adam's own locomotive database.
            if (train != null) train.setTrainLength(trainLengthWas == null ? 0 : trainLengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    // ---------------------------------------------------------------- the claims

    /**
     * Starting autonomy leaves every tile of the page where it was.
     */
    @Test
    public void testStartingAutonomyDoesNotRebuildThePage()
    {
        assertEquals(tilesAfterStart.size(), tilesBefore.size(),
            "the page has a different number of tiles after autonomy started");

        assertEquals(replaced(tilesBefore, tilesAfterStart), 0,
            replaced(tilesBefore, tilesAfterStart) + " of the page's " + tilesBefore.size()
            + " tiles were destroyed and built again when autonomy started. That is the whole diagram"
            + " being redrawn - MT-334 - and greying blocked track changes no tile art at all");
    }

    /**
     * And only the squares whose state changed were told to redraw.
     */
    @Test
    public void testStartingAutonomyRedrawsOnlyTheSquaresThatChanged()
    {
        Set<JComponent> allowed = tilesOfTheChangedSquares();

        assertFalse(onStart.isEmpty(),
            "starting autonomy redrew nothing at all, so the " + blocked.size() + " squares the"
            + " railway now refuses stay drawn as free track until something unrelated repaints them");

        List<JComponent> strays = new ArrayList<>();

        for (JComponent dirty : onStart)
        {
            if (!allowed.contains(dirty)) strays.add(dirty);
        }

        assertEquals(strays.size(), 0,
            strays.size() + " tiles were redrawn that no train covers or blocks, out of "
            + onStart.size() + " redrawn and " + tilesBefore.size() + " on the page. The refresh is"
            + " repainting more than it changed, which on a railway where this runs every time a"
            + " train moves is the flicker MT-334 was about");
    }

    /**
     * Stopping autonomy takes the wash off - the same squares, redrawn, and no rebuild.
     */
    @Test
    public void testStoppingAutonomyTakesTheWashOffAgain()
    {
        assertEquals(replaced(tilesBefore, tilesAfterStop), 0,
            "the page was rebuilt when autonomy stopped");

        assertFalse(onStop.isEmpty(),
            "stopping autonomy redrew nothing, so every square greyed while it ran stays grey on a"
            + " railway where nothing is routing - which is OB-180's half of this: taking a mark off"
            + " is as much work as putting it on");

        Set<JComponent> allowed = tilesOfTheChangedSquares();

        for (JComponent dirty : onStop)
        {
            assertTrue(allowed.contains(dirty),
                "a tile no train covers or blocks was redrawn when autonomy stopped, out of "
                + onStop.size() + " redrawn and " + tilesBefore.size() + " on the page");
        }
    }

    // ---------------------------------------------------------------- counting the repaints

    /**
     * A RepaintManager that writes down which components were made dirty.
     *
     * Recorded BEFORE `super`, so what is counted is the `repaint()` calls production made rather than
     * the subset Swing decided to act on - a component that is not on screen is exactly the case where
     * the two differ, and every component here is offscreen.
     */
    private static final class Counting extends RepaintManager
    {
        private final Set<JComponent> dirty = new LinkedHashSet<>();

        @Override
        public synchronized void addDirtyRegion(JComponent c, int x, int y, int w, int h)
        {
            if (c != null) dirty.add(c);

            super.addDirtyRegion(c, x, y, w, h);
        }

        synchronized void clear()
        {
            dirty.clear();
        }

        synchronized Set<JComponent> seen()
        {
            return new LinkedHashSet<>(dirty);
        }
    }

    /**
     * Every on-screen tile of every square whose covered or blocked state the gesture changed.
     *
     * The wash appears on every blocked square and the line is drawn on a subset of them, so the
     * squares that change when autonomy starts or stops are exactly the blocked ones.  A square can be
     * on screen more than once, which is why this asks the registry rather than the grid.
     *
     * @return the tiles that are allowed to have been redrawn
     */
    private static Set<JComponent> tilesOfTheChangedSquares()
    {
        Set<JComponent> out = new LinkedHashSet<>();

        for (TileKey square : blocked)
        {
            out.addAll(ui.getDiagramTileRegistry().labelsFor(square));
        }

        return out;
    }

    private static int replaced(List<LayoutLabel> before, List<LayoutLabel> after)
    {
        int found = 0;

        for (int at = 0; at < Math.min(before.size(), after.size()); at++)
        {
            if (before.get(at) != after.get(at)) found++;
        }

        return found;
    }

    private static List<LayoutLabel> tilesOf(java.awt.Container container)
    {
        List<LayoutLabel> out = new ArrayList<>();

        collect(container, out);

        return out;
    }

    private static void collect(java.awt.Container container, List<LayoutLabel> into)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof LayoutLabel) into.add((LayoutLabel) child);

            if (child instanceof java.awt.Container) collect((java.awt.Container) child, into);
        }
    }

    // ---------------------------------------------------------------- the railway's own answer

    /**
     * The squares the railway holds blocked, worked out from the covered EDGES - the same translation
     * `ui.testBlockedTrackIsGreyWhileAutonomyRuns` makes, and stated there.
     *
     * @return the blocked squares
     */
    private static Set<TileKey> blockedSquares()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (Edge edge : layout.edgesCoveredByStandingTrains().keySet())
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            TileKey from = session.getStationIndex().squareOf(edge.getStart().getName());
            TileKey to = session.getStationIndex().squareOf(edge.getEnd().getName());

            if (from == null || to == null) continue;

            for (GraphReducer.ReducedEdge reduced : session.getReducer().getEdges())
            {
                boolean sameWay = from.equals(reduced.getStart()) && to.equals(reduced.getEnd());
                boolean otherWay = to.equals(reduced.getStart()) && from.equals(reduced.getEnd());

                if (!sameWay && !otherWay) continue;

                for (GraphReducer.TileStep step : reduced.getPath())
                {
                    if (step.getTile() == null) continue;

                    if (step.getTile().equals(from) || step.getTile().equals(to)) continue;

                    out.add(step.getTile());
                }

                break;
            }
        }

        return out;
    }

    // ---------------------------------------------------------------- the fixture

    private static void setRunning(boolean value) throws Exception
    {
        Field running = Layout.class.getDeclaredField("running");

        running.setAccessible(true);
        running.setBoolean(layout, value);
    }

    private static void refreshCoveredTrack() throws Exception
    {
        java.lang.reflect.Method refresh =
            TrainControlUI.class.getDeclaredMethod("refreshCoveredTrack");

        refresh.setAccessible(true);
        refresh.invoke(ui);

        pump();
    }

    private static void settle() throws Exception
    {
        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(60, java.util.concurrent.TimeUnit.SECONDS);

        pump();
    }

    /**
     * Leaves one train standing, and only one that actually covers track.
     */
    private static void oneTrainThatCoversSomething()
    {
        Point keep = null;

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() == null || point.getArrivedFrom() == null) continue;

            Locomotive candidate = point.getCurrentLocomotive();

            Integer was = candidate.getTrainLength();

            candidate.setTrainLength(4);

            boolean covers = layout.edgesCoveredByStandingTrains().containsValue(candidate);

            candidate.setTrainLength(was);

            if (covers)
            {
                keep = point;

                break;
            }
        }

        if (keep == null) return;

        for (Point point : layout.getPoints())
        {
            if (point == keep || point.getCurrentLocomotive() == null) continue;

            layout.moveLocomotive(null, point.getName(), true);
        }

        train = keep.getCurrentLocomotive();

        trainLengthWas = train.getTrainLength();

        train.setTrainLength(4);
    }

    private static void measureEveryTileAs(int units)
    {
        Set<TileKey> everyTile = new LinkedHashSet<>();

        for (TileKey tile : session.getReducer().getPoints().keySet()) everyTile.add(tile);

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) everyTile.add(step.getTile());
            }
        }

        for (TileKey tile : everyTile) session.setTileLength(tile, units);
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }
}
