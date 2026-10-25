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
 * MT-334's shape, asked of the gray wash that came back.
 *
 * Adam asked for the wash back beside the line: "That plus the line, **drawn and refreshed carefully**, should do the
 * trick."  MT-334 was the whole diagram being rebuilt because one pathing arrow changed, and he saw the page flicker.
 *
 * Two different claims are asserted:
 *
 * - **No rebuild.**  The page's tiles are collected as objects before and after the gesture and compared by identity
 *   (as `testTheDiagramIsNotRebuiltForAnArrow` does): a rebuild replaces every `LayoutLabel`, a refresh does not.
 *
 * - **Only what changed is redrawn.**  Identity alone would pass a refresh that repaints every tile on every train
 *   movement, so a counting `RepaintManager` records which components were made dirty, and each must be a tile of a
 *   square whose state actually changed.
 *
 * The changed squares are worked out from the railway, not from the window: `blockedSquares()` translates
 * `Layout.edgesCoveredByStandingTrains` through the reducer, as `ui.testBlockedTrackIsGreyWhileAutonomyRuns` does,
 * because asking the window what the right answer is agrees with it whatever it says.
 *
 * The gesture is a TRAIN LENGTH.  The gray was once fenced on a running railway, so starting autonomy put the wash on;
 * Adam: "yes, this greyout should appear at idle and be regenerated if a placement or train/track length is changed."
 * With the fence gone, starting a run changes no square and this class would have measured nothing.  The train length
 * is the only one of his three gestures that needs no rebuild, which leaves the repaint count as the only thing moving.
 *
 * THROUGH THE DOOR: `TrainControlUI.applyTrainLength` is everything `promptTrainLength` does once its dialog has
 * answered; nothing here calls the window's own recompute.  BOTH directions: unmeasuring the train takes the wash off
 * (OB-180).
 *
 * MUTATION: make `repaintTheWashWhereItChanged` repaint every registered tile and
 * `testMeasuringTheTrainRedrawsOnlyTheSquaresThatChanged` fails.  Drop the blocked set from the comparison it makes and
 * `testUnmeasuringTheTrainTakesTheWashOffAgain` fails with nothing redrawn.  Take `blockedTrackChanged()` out of
 * `applyTrainLength` and both fail with nothing redrawn at all.
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

    /** Why the set-up found no fixture, or null; each claim asks it, so it fails rather than skips. */
    private static String unbuilt;

    /** Fails the claim when the set-up found no fixture: a skip or an assert in `setUpClass` reads as skipped tests. */
    private static void assertBuilt()
    {
        if (unbuilt != null) throw new AssertionError(unbuilt);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("building a page of tiles needs a display");
        }

        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, `test/layouts/live-snapshot`):
        // `cs2_sample_layout`'s placements and lengths move as Adam runs trains on it, and a class that skips
        // everything reads as green.  `Scenario.folderFor` is the only naming of a fixture folder that cannot spell its
        // way out to his own railway.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null)
        {
            unbuilt = "no autonomy setup on this railway";
            return;
        }

        SwingUtilities.invokeAndWait(() -> measureEveryTileAs(1));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        oneTrainThatCoversSomething();

        if (train == null)
        {
            unbuilt = "no standing train covers any track here";
            return;
        }

        assertFalse(layout.isRunning(),
            "the frozen railway came up with autonomy running, and this class is about the gesture"
            + " rather than about a run");

        refreshCoveredTrack();

        blocked = blockedSquares();
        covered = coveredSquares();

        if (blocked.isEmpty())
        {
            unbuilt = "the standing train blocks no square, so there is no wash to add";
            return;
        }

        page = model.getLayout(blocked.iterator().next().getPage());

        if (page == null)
        {
            unbuilt = "the blocked squares are on no page this model has";
            return;
        }

        SwingUtilities.invokeAndWait(() ->
        {
            // popup = true lays the grid out to its natural size; master = null keeps it out of editor mode, where the
            // marks are drawn (as support.Rendered does).
            grid = new LayoutGrid(page, TILE, HOST, null, true, ui);
        });

        settle();

        tilesBefore = tilesOf(HOST);

        if (tilesBefore.size() < 10)
        {
            unbuilt = "the page drew " + tilesBefore.size() + " tiles - too few to say"
                + " anything about a full-page redraw";
            return;
        }

        Counting counter = new Counting();

        RepaintManager was = RepaintManager.currentManager(HOST);

        try
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(counter));

            // THE TRAIN IS UNMEASURED TO BEGIN WITH, so the gesture below is what puts the wash on (zero is how this
            // railway says "not set", and an unmeasured train covers nothing).  Starting autonomy would change no
            // square now that the gray is not fenced on a running railway.
            SwingUtilities.invokeAndWait(() -> ui.applyTrainLength(train, 0));

            settleMarks();

            counter.clear();

            // THE GESTURE, THROUGH THE DOOR: nothing here asks for a refresh.
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
        assertBuilt();

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
        assertBuilt();

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
        assertBuilt();

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
     * A tile whose picture changes - a switch thrown, a sensor occupied - redraws its own neighborhood, not the whole
     * window it sits in.
     *
     * `LayoutLabel.setImageOnEDT` also repainted the tile's `parent` (the whole tab on the main window), so every
     * switch, signal and sensor event during a run redrew everything.  The tile's own repaint already goes through the
     * diagram's panel, which draws overlapping children together (OB-172); the squares around it are redrawn as a
     * train's icon is (MT-642).
     *
     * MUTATION: repaint the parent again, and this fails.
     */
    @Test
    public void testATileThatChangesRedrawsItsNeighbourhoodOnly() throws Exception
    {
        assertBuilt();

        // A PAGE UNDER A TAB, as the main window builds one: the grid's master is what a tile calls its parent
        final JPanel tab = new JPanel();

        final JPanel host = new JPanel();

        final LayoutGrid[] built = new LayoutGrid[1];

        SwingUtilities.invokeAndWait(() -> built[0] = new LayoutGrid(page, TILE, host, tab, true, ui));

        settle();

        List<LayoutLabel> tiles = tilesOf(host);

        LayoutLabel tile = null;

        java.lang.reflect.Field what = LayoutLabel.class.getDeclaredField("component");

        what.setAccessible(true);

        // A TILE WITH A PICTURE - track, a switch, a sensor - in the diagram's own panel
        for (LayoutLabel t : tiles)
        {
            Object c = what.get(t);

            if (!t.isSpacer() && c != null && !((org.traincontrol.base.LayoutDiagramComponent) c).isText()
                && t.getParent() != null && t.getParent() != host) tile = t;
        }

        assertNotNull(tile, "precondition: no tile of the page with a picture sits in a panel of its own");

        java.lang.reflect.Field parentField = LayoutLabel.class.getDeclaredField("parent");

        parentField.setAccessible(true);

        assertSame(parentField.get(tile), tab, "precondition: the tile does not call the tab its parent, so this asks"
            + " nothing");

        final LayoutLabel changing = tile;

        final java.lang.reflect.Method setImage = LayoutLabel.class.getDeclaredMethod("setImageOnEDT", boolean.class);

        setImage.setAccessible(true);

        Counting counter = new Counting();

        RepaintManager was = RepaintManager.currentManager(HOST);

        try
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(counter));

            counter.clear();

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    setImage.invoke(changing, true);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            // POSTED, so the work it posts is waited for
            settle();

            Set<JComponent> dirty = counter.seen();

            assertFalse(dirty.isEmpty(), "precondition: the tile's update redrew nothing at all");

            assertFalse(dirty.contains(tab), "a tile whose picture changed redrew the whole panel the diagram sits in -"
                + " the main window's tab, or the editor's window - on every switch, signal and sensor event: " + dirty);
        }
        finally
        {
            final RepaintManager restore = was;

            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(restore));

            SwingUtilities.invokeAndWait(() -> built[0].discard());
        }
    }

    /**
     * A refresh of the station captions that changes nothing lays nothing out again.  `updateStationLabels` revalidated
     * and repainted each caption's PARENT, the whole page, on every publish of the running diagram (up to five times a
     * second); a caption that changes revalidates and repaints itself.
     *
     * MUTATION: revalidate the captions' parent again, and this fails.
     */
    @Test
    public void testACaptionRefreshThatChangesNothingLaysNothingOut() throws Exception
    {
        assertBuilt();

        Counting counter = new Counting();

        RepaintManager was = RepaintManager.currentManager(HOST);

        try
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(counter));

            // ONCE, so the captions say what they say now; then again, with nothing changed
            ui.updateVisiblePoints();

            settle();

            counter.clear();

            ui.updateVisiblePoints();

            settle();

            Set<JComponent> captions = new LinkedHashSet<>();

            for (JComponent c : counter.seen())
            {
                if (c instanceof org.traincontrol.gui.widgets.StationCaption) captions.add(c);
            }

            if (captions.isEmpty()) throw new AssertionError("no station caption on the window's page was refreshed");

            Set<java.awt.Container> pages = new LinkedHashSet<>();

            for (JComponent c : captions) if (c.getParent() != null) pages.add(c.getParent());

            for (java.awt.Container laidOut : counter.invalidated())
            {
                assertFalse(pages.contains(laidOut), "a refresh of the station captions that changed nothing laid the"
                    + " whole page out again - on every publish of the running diagram");
            }

            for (JComponent c : counter.seen())
            {
                assertFalse(pages.contains(c), "a refresh of the station captions that changed nothing redrew the whole"
                    + " page - on every publish of the running diagram");
            }
        }
        finally
        {
            final RepaintManager restore = was;

            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(restore));
        }
    }

    /**
     * Refreshing the static autonomy layer with nothing changed redraws no square: `showStaticAutonomyLayer` cleared
     * every mark and set them all again, so the unchanged-mark check never saw one.  It now clears only the squares
     * that dropped out.
     *
     * LAST IN THE CLASS: it shows the main window's static layer, and the tests about the gray count every square redrawn
     * - with the layer showing, the main window's own squares are among them.
     *
     * MUTATION: clear every mark first again, and this fails.
     */
    @Test(priority = 1)
    public void testAStaticLayerThatChangesNothingRedrawsNothing() throws Exception
    {
        assertBuilt();

        Counting counter = new Counting();

        RepaintManager was = RepaintManager.currentManager(HOST);

        try
        {
            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(counter));

            SwingUtilities.invokeAndWait(() -> ui.showStaticAutonomyLayer(true));

            settle();

            // PRECONDITION: squares of the window's page wear a mark, so a clear would redraw them
            java.lang.reflect.Field tilesField = org.traincontrol.gui.autonomy.DiagramTileRegistry.class.getDeclaredField("tiles");

            tilesField.setAccessible(true);

            java.lang.reflect.Field markField = LayoutLabel.class.getDeclaredField("autonomyAnnotation");

            markField.setAccessible(true);

            int marked = 0;

            for (Object labels : ((java.util.Map<?, ?>) tilesField.get(ui.getDiagramTileRegistry())).values())
            {
                for (Object label : (java.util.Collection<?>) labels) if (markField.get(label) != null) marked++;
            }

            if (marked == 0) throw new AssertionError("no square of the window's page wears a mark of the static layer");

            counter.clear();

            SwingUtilities.invokeAndWait(() -> ui.showStaticAutonomyLayer(true));

            settle();

            List<JComponent> redrawn = new ArrayList<>();

            for (JComponent c : counter.seen()) if (c instanceof LayoutLabel) redrawn.add(c);

            assertTrue(redrawn.isEmpty(), "the static layer, refreshed with nothing changed, redrew " + redrawn.size()
                + " of the " + marked + " marked squares - each cleared and set again");
        }
        finally
        {
            final RepaintManager restore = was;

            SwingUtilities.invokeAndWait(() -> RepaintManager.setCurrentManager(restore));
        }
    }

    /** A RepaintManager that records the `repaint()` calls production made, BEFORE Swing filters them (offscreen). */
    private static final class Counting extends RepaintManager
    {
        private final Set<JComponent> dirty = new LinkedHashSet<>();

        /** The components asked to lay out again - for the captions' claim */
        private final Set<java.awt.Container> invalid = new LinkedHashSet<>();

        @Override
        public synchronized void addInvalidComponent(JComponent c)
        {
            if (c != null) invalid.add(c);

            super.addInvalidComponent(c);
        }

        synchronized Set<java.awt.Container> invalidated()
        {
            return new LinkedHashSet<>(invalid);
        }

        @Override
        public synchronized void addDirtyRegion(JComponent c, int x, int y, int w, int h)
        {
            if (c != null) dirty.add(c);

            super.addDirtyRegion(c, x, y, w, h);
        }

        synchronized void clear()
        {
            dirty.clear();
            invalid.clear();
        }

        synchronized Set<JComponent> seen()
        {
            return new LinkedHashSet<>(dirty);
        }

        /**
         * The DIAGRAM SQUARES among them, which is what this class is about.
         *
         * `applyTrainLength` also refreshes the autonomy findings (FR-046 warns about a train with no length), which
         * dirties the strip at the top of the window: five components that belong to no square, whose counting accused
         * the wash of repainting free track.  A `LayoutLabel` is one square and nothing else is.
         *
         * KNOWN LIMIT: a full-page repaint asked for by repainting the CONTAINER dirties the container, not its
         * children, and is invisible here; the identity comparison in `testMeasuringTheTrainDoesNotRebuildThePage`
         * catches the rebuild, and this catches the refresh that touches too many squares.
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
     * Which SQUARES a set of redrawn tiles belong to, for an assertion message that can be acted on.  A `LayoutLabel`
     * has no square getter, so this asks the registry the other way round; a tile that belongs to no square of the
     * graph is reported as unknown.
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

    /**
     * Every on-screen tile of every square whose covered or blocked state the gesture changed.  BOTH marks: a train
     * LENGTH moves the line as well as the wash, so a covered square losing its line is legitimately redrawn.  Asks the
     * registry rather than the grid, since a square can be on screen more than once.
     */
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
     * The squares the train is DRAWN on while it is measured, worked out from the railway: the same source as
     * `blockedSquares`, endpoints kept because a train standing at a sensor is drawn there.
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
     * The squares the railway holds blocked, worked out from the covered EDGES as in
     * `ui.testBlockedTrackIsGreyWhileAutonomyRuns`.
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
     * WAITS for the marks a gesture asked for, without asking for them: calling the window's own recompute after the
     * door would prove that the recompute works, which is not the claim.
     */
    private static void settleMarks() throws Exception
    {
        support.CoveredMarks.settle(ui);

        pump();
    }

    private static void refreshCoveredTrack() throws Exception
    {
        // AND WAITED FOR (OB-192): the window works the marks out on a worker (the event thread would wait on the
        // `Layout` monitor a dispatch holds), so asking and reading are two moments, known to `support.CoveredMarks`.
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
