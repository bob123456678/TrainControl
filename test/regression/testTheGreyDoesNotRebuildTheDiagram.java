package regression;

import java.io.File;
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
 * - **No rebuild.**  The tiles of the page are collected as objects before the gesture and again
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
 * **THE GESTURE IS A TRAIN LENGTH NOW, AND IT USED TO BE STARTING AUTONOMY** (W7B-B1).  The grey was
 * fenced on a running railway, so starting and stopping a run was what put the wash on and took it
 * off.  Adam, 2026-09-09: *"yes, this greyout should appear at idle and be regenerated if a placement
 * or train/track length is changed."*  With the fence gone, starting a run changes not one square -
 * this class would have gone on passing while measuring nothing, which is the failure mode the
 * repository keeps finding.  So it measures one of the three gestures he named instead, and the train
 * length is the one worth taking: it is the only one that needs no rebuild to take effect, which is
 * what leaves the repaint count as the only thing moving.
 *
 * **THROUGH THE DOOR.**  `TrainControlUI.applyTrainLength` is everything `promptTrainLength` does once
 * its dialog has answered; nothing here calls the window's own recompute, because a refresh the test
 * asked for would pass whether or not the door asks for one.
 *
 * **Both directions of the gesture.**  Measuring the train puts the wash on and unmeasuring it takes
 * the wash off, and the second is the half a set comparison makes easy to forget - it is the half
 * OB-180 was reported about.
 *
 * MUTATION: make `repaintTheWashWhereItChanged` repaint every registered tile and
 * `testMeasuringTheTrainRedrawsOnlyTheSquaresThatChanged` fails.  Drop the blocked set from the
 * comparison it makes and `testUnmeasuringTheTrainTakesTheWashOffAgain` fails with nothing redrawn.
 * Take `blockedTrackChanged()` out of `applyTrainLength` and both fail with nothing redrawn at all.
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

    /** What the fixture measures the train at - the "wash on" half of the gesture. */
    private static final int MEASURED = 4;

    /** The tiles of the page, as objects, before the gesture and after each half of it. */
    private static List<LayoutLabel> tilesBefore;
    private static List<LayoutLabel> tilesAfterStart;
    private static List<LayoutLabel> tilesAfterStop;

    /** The squares the railway blocks while the train stands where it does. */
    private static Set<TileKey> blocked;

    /** And the squares it is drawn on, which a length change moves as well. */
    private static Set<TileKey> covered;

    /** Which tiles were made dirty by measuring the train, and by unmeasuring it again. */
    private static Set<JComponent> onStart;
    private static Set<JComponent> onStop;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("building a page of tiles needs a display");
        }

        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // This opened `cs2_sample_layout`, whose placements and lengths move as Adam runs trains on it
        // - so on 2026-09-09 this class reported "no standing train covers any track here" against a
        // working tree where every train had been driven away, and a class that skips everything reads
        // as a green one.  `live-snapshot` is the same railway with the clock stopped at `e6f4649c`,
        // which is the state these assertions were written against.
        //
        // `Scenario.folderFor` rather than a path: it is the only naming of a fixture folder that
        // cannot spell its way out to the operator's own railway, and `LayoutSandbox` still copies
        // what it names before anything reads it.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

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

        assertFalse(layout.isRunning(),
            "the frozen railway came up with autonomy running, and this class is about the gesture"
            + " rather than about a run");

        refreshCoveredTrack();

        blocked = blockedSquares();
        covered = coveredSquares();

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

            // THE TRAIN IS UNMEASURED TO BEGIN WITH, so the gesture below is what puts the wash on.
            //
            // This used to start and stop AUTONOMY, because the grey was fenced on a running railway.
            // That fence is gone (W7B-B1, Adam 2026-09-09: *"yes, this greyout should appear at idle
            // and be regenerated if a placement or train/track length is changed"*), so starting a run
            // no longer changes a single square and the measurement would have been of nothing at all.
            // The gesture that adds and removes the wash at idle is one of the three he named, and
            // this class takes the train length: it is the only one of the three that needs no rebuild
            // to take effect, which is what lets the claim be about repaints alone.
            //
            // A length of zero is how this railway says "not set", and an unmeasured train covers
            // nothing.
            SwingUtilities.invokeAndWait(() -> ui.applyTrainLength(train, 0));

            settleMarks();

            counter.clear();

            // THE GESTURE: the train is measured, and the window recomputes what is covered.  THROUGH
            // THE DOOR, and nothing here asks for a refresh - `applyTrainLength` is what
            // `promptTrainLength` calls once the dialog has answered.
            SwingUtilities.invokeAndWait(() -> ui.applyTrainLength(train, MEASURED));

            settleMarks();

            onStart = counter.seenTiles();

            tilesAfterStart = tilesOf(HOST);

            // AND THE OTHER HALF - the wash has to come off again.
            counter.clear();

            SwingUtilities.invokeAndWait(() -> ui.applyTrainLength(train, 0));

            settleMarks();

            onStop = counter.seenTiles();

            tilesAfterStop = tilesOf(HOST);

            SwingUtilities.invokeAndWait(() -> ui.applyTrainLength(train, MEASURED));

            settleMarks();
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
            if (layout != null) layout.stopLocomotives();

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
     * Measuring the train leaves every tile of the page where it was.
     */
    @Test
    public void testMeasuringTheTrainDoesNotRebuildThePage()
    {
        assertEquals(tilesAfterStart.size(), tilesBefore.size(),
            "the page has a different number of tiles after the train was measured");

        assertEquals(replaced(tilesBefore, tilesAfterStart), 0,
            replaced(tilesBefore, tilesAfterStart) + " of the page's " + tilesBefore.size()
            + " tiles were destroyed and built again when the train was measured. That is the whole"
            + " diagram being redrawn - MT-334 - and greying blocked track changes no tile art at all");
    }

    /**
     * And only the squares whose state changed were told to redraw.
     */
    @Test
    public void testMeasuringTheTrainRedrawsOnlyTheSquaresThatChanged()
    {
        Set<JComponent> allowed = tilesOfTheChangedSquares();

        assertFalse(onStart.isEmpty(),
            "measuring the train redrew nothing at all, so the " + blocked.size() + " squares the"
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
            + " train moves is the flicker MT-334 was about. They are " + nameThem(strays)
            + "; the squares that did change are " + blocked + " blocked and " + covered
            + " covered");
    }

    /**
     * Unmeasuring the train takes the wash off - the same squares, redrawn, and no rebuild.
     */
    @Test
    public void testUnmeasuringTheTrainTakesTheWashOffAgain()
    {
        assertEquals(replaced(tilesBefore, tilesAfterStop), 0,
            "the page was rebuilt when the train was unmeasured");

        assertFalse(onStop.isEmpty(),
            "unmeasuring the train redrew nothing, so every square its tail had greyed stays grey"
            + " after the train that greyed it has no length at all - which is OB-180's half of this:"
            + " taking a mark off is as much work as putting it on");

        Set<JComponent> allowed = tilesOfTheChangedSquares();

        for (JComponent dirty : onStop)
        {
            assertTrue(allowed.contains(dirty),
                "a tile no train covers or blocks was redrawn when the train was unmeasured, out of "
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

        /**
         * The DIAGRAM SQUARES among them, which is what this class is about.
         *
         * The gesture is `applyTrainLength`, and that door does two things: it tells the marks to
         * recompute and it refreshes the autonomy findings, because FR-046 warns about a train with
         * no length and that warning has to go when the length arrives.  The second dirties the strip
         * at the top of the window - five components that belong to no square of the diagram - and
         * counting those as squares redrawn accused the wash of repainting free track.
         *
         * MT-334 is the PAGE flickering, so the population is the page's tiles.  A `LayoutLabel` is
         * one square of the diagram and nothing else is; the caption labels beside a station are
         * `StationCaption`s and the strip is not a tile at all.
         *
         * KNOWN LIMIT, stated rather than left to be found: a full-page repaint asked for by
         * repainting the CONTAINER would dirty the container and not its children, and would be
         * invisible here.  The identity comparison in `testMeasuringTheTrainDoesNotRebuildThePage`
         * is what catches the rebuild; this catches the refresh that touches too many squares.
         */
        synchronized Set<JComponent> seenTiles()
        {
            Set<JComponent> tiles = new LinkedHashSet<>();

            for (JComponent each : dirty)
            {
                if (each instanceof LayoutLabel) tiles.add(each);
            }

            return tiles;
        }
    }

    /**
     * Every on-screen tile of every square whose covered or blocked state the gesture changed.
     *
     * **BOTH MARKS, and it used to be the blocked one alone.**  While the gesture was starting and
     * stopping autonomy that was right: the line was drawn either way, so only the wash moved and the
     * squares that changed were exactly the blocked ones.  A train LENGTH moves both - the line is as
     * long as the train and the wash is the whole of the edges its tail reaches - so a covered square
     * losing its line is a square this gesture legitimately redraws, and counting it as a stray
     * accused the window of repainting free track.
     *
     * A square can be on screen more than once, which is why this asks the registry rather than the
     * grid.
     *
     * @return the tiles that are allowed to have been redrawn
     */
    /**
     * Which SQUARES a set of redrawn tiles belong to, for an assertion message that can be acted on.
     *
     * A `LayoutLabel` does not say which square it is - `setSquare` has no getter - so this asks the
     * registry the other way round, over every square the reduction knows.  A tile that belongs to no
     * such square is reported as unknown, which is itself the answer: it is not a square of the graph.
     *
     * @param tiles the tiles to name
     * @return the squares, as text
     */
    private static String nameThem(java.util.Collection<JComponent> tiles)
    {
        Set<String> named = new LinkedHashSet<>();

        for (JComponent tile : tiles)
        {
            String where = "an unkeyed tile";

            for (TileKey square : everySquare())
            {
                if (ui.getDiagramTileRegistry().labelsFor(square).contains(tile))
                {
                    where = square.toString();

                    break;
                }
            }

            named.add(where);
        }

        return named.toString();
    }

    /** Every square the reduction knows, for `nameThem`. */
    private static Set<TileKey> everySquare()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey tile : session.getReducer().getPoints().keySet()) out.add(tile);

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            out.add(edge.getStart());
            out.add(edge.getEnd());

            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) out.add(step.getTile());
            }
        }

        out.remove(null);

        return out;
    }

    private static Set<JComponent> tilesOfTheChangedSquares()
    {
        Set<TileKey> changed = new LinkedHashSet<>(blocked);

        changed.addAll(covered);

        Set<JComponent> out = new LinkedHashSet<>();

        for (TileKey square : changed)
        {
            out.addAll(ui.getDiagramTileRegistry().labelsFor(square));
        }

        return out;
    }

    /**
     * The squares the train is DRAWN on while it is measured, worked out from the railway.
     *
     * The same source as `blockedSquares` - `Layout.edgesCoveredByStandingTrains` - but the endpoints
     * kept, because a train standing at a sensor is drawn standing there.  The window's own
     * `isTrackCovered` is not asked, for the reason `blockedSquares` gives.
     *
     * @return the covered squares
     */
    private static Set<TileKey> coveredSquares()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (Edge edge : layout.edgesCoveredByStandingTrains().keySet())
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            TileKey from = session.getStationIndex().squareOf(edge.getStart().getName());
            TileKey to = session.getStationIndex().squareOf(edge.getEnd().getName());

            if (from != null) out.add(from);
            if (to != null) out.add(to);

            if (from == null || to == null) continue;

            for (GraphReducer.ReducedEdge reduced : session.getReducer().getEdges())
            {
                boolean sameWay = from.equals(reduced.getStart()) && to.equals(reduced.getEnd());
                boolean otherWay = to.equals(reduced.getStart()) && from.equals(reduced.getEnd());

                if (!sameWay && !otherWay) continue;

                for (GraphReducer.TileStep step : reduced.getPath())
                {
                    if (step.getTile() != null) out.add(step.getTile());
                }

                break;
            }
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

    /**
     * WAITS for the marks a gesture asked for, without asking for them.
     *
     * The difference between this and `refreshCoveredTrack` below is the whole point of the gesture
     * block above: calling the window's own recompute after the door would prove that the recompute
     * works, which is not the claim.
     */
    private static void settleMarks() throws Exception
    {
        support.CoveredMarks.settle(ui);

        pump();
    }

    private static void refreshCoveredTrack() throws Exception
    {
        // AND WAITED FOR (OB-192).  The window works the marks out on a worker now, because asking
        // for them on the event thread meant waiting on the `Layout` monitor a dispatch holds - so
        // asking and reading are two moments, and `support.CoveredMarks` is the one place that knows
        // it.
        support.CoveredMarks.refresh(ui);

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

            candidate.setTrainLength(MEASURED);

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

        train.setTrainLength(MEASURED);
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
