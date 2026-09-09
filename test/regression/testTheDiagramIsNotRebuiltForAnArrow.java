package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * MT-334 - changing one pathing arrow repaints the arrows, not every tile on the page.
 *
 * Adam, 2026-09-08, on the fix that was meant to settle this: *"it still flickers, but less"*, and he
 * confirmed that the whole diagram redraws when a pathing arrow is changed.
 *
 * **Less, because only one of five doors had been moved.** OB-185 gave the panel a light redraw door -
 * `annotationsChanged`, which re-sets the annotations on the labels that are already there - and wired
 * exactly one caller to it: the **One-Way Run** tool. The four doors that change a direction the
 * ordinary way still ended at `setupChanged`, which runs `LayoutEditor.refreshGrid` and builds every
 * label on the page again:
 *
 * - `cycle` - LEFT-CLICKING A PIECE OF TRACK, which is the gesture Adam's report names;
 * - `applyArmMask` - clicking a switch, a crossing or a double curve, and the Open/Shut arm items;
 * - `setAllBranches` - "All branches: both ways / closed";
 * - `directionItem` - the per-route radio on the right-click menu.
 *
 * Each carries a comment saying why it announces - "directions are edges in the running graph" - and
 * that half is right and is kept: `annotationsChanged` rebuilds the running layout exactly as
 * `setupChanged` does. What it does not do is throw the tiles away and make them again.
 *
 * **Asserted by identity, which is what "the whole diagram redraws" means.** A rebuild replaces every
 * `LayoutLabel` in the grid with a new object; an annotation refresh leaves them where they are and
 * repaints only the ones whose annotation actually differs. So the tiles are collected before the
 * click and after it, and compared as objects.
 *
 * MUTATION: point any of the four doors back at `setupChanged()` and
 * `testClickingATrackTileKeepsTheTilesItDidNotChange` fails with every label replaced. Make
 * `annotationsChanged` do nothing at all and `testTheClickStillChangesTheArrow` fails.
 *
 * @author Adam
 */
public class testTheDiagramIsNotRebuiltForAnArrow
{
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static support.LayoutSandbox sandbox;
    private static AutonomySession session;
    private static LayoutEditor editor;

    private static final String PAGE = "1 - Main";

    /** A piece of plain track with exactly one route through it - what `cycle` acts on. */
    private static TileKey track;

    private static RouteId route;

    /** The direction that square's run had before the click, and after it. */
    private static Direction before;
    private static Direction after;

    /** The tiles of the grid before the click and after it, as objects. */
    private static List<LayoutLabel> tilesBefore;
    private static List<LayoutLabel> tilesAfter;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor's grid needs a display");
        }

        sandbox = // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // The SHAPE is what this class is about, and the shape is the same in both.  Where his trains
        // are standing, how long they are and which side they came in by are not, and reading those
        // off the live folder is how `testTheLengthGuardsOnTheRealLayout` came to assert that the
        // 2-8-4 stood at BottomMainB - it is at BottomMainA now, and that class was red for a reason
        // that had nothing to do with any guard.  A fixture that moves while nobody is looking makes
        // every class over it say something different every week.
        support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there are no arrows to change");
        }

        final LayoutDiagram page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);

        track = plainTrackOn(page);

        if (track == null) throw new SkipException("no single-route track square on " + PAGE);

        route = session.getRoutes(track).keySet().iterator().next();

        final LayoutEditor[] built = new LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        editor = built[0];

        settle();

        tilesBefore = tilesOf(editor);

        before = session.getGraph().getDirection(track, route);

        // THE PRODUCTION DOOR: this is what LayoutEditor.receiveClickEvent calls when a square is
        // left-clicked in autonomy mode, with no tool selected - which is the gesture Adam's report is
        // about.
        javax.swing.SwingUtilities.invokeAndWait(() ->
            editor.getAutonomyPanel().tileClicked(track, page.getComponent(track.getX(),
                track.getY()), false));

        settle();

        after = session.getGraph().getDirection(track, route);

        tilesAfter = tilesOf(editor);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (editor != null)
        {
            final LayoutEditor closing = editor;

            javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

        if (ui != null)
        {
            final TrainControlUI closing = ui;

            javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }

    /**
     * The defect: the click left every tile on the page where it was.
     */
    @Test
    public void testClickingATrackTileKeepsTheTilesItDidNotChange()
    {
        assertFalse(tilesBefore.isEmpty(), "the editor drew no tiles at all");

        assertEquals(tilesAfter.size(), tilesBefore.size(),
            "the page has a different number of tiles after one arrow was changed");

        List<Integer> replaced = new ArrayList<>();

        for (int at = 0; at < tilesBefore.size(); at++)
        {
            if (tilesBefore.get(at) != tilesAfter.get(at)) replaced.add(at);
        }

        assertEquals(replaced.size(), 0,
            replaced.size() + " of the page's " + tilesBefore.size() + " tiles were destroyed and"
            + " built again by changing one arrow at " + track + ". That is the whole diagram being"
            + " redrawn, which is what Adam sees as the flicker - a direction restriction changes no"
            + " tile art at all");
    }

    /**
     * CONTROL - and the click did change the arrow, so the test above is not passing because nothing
     * happened.
     */
    @Test
    public void testTheClickStillChangesTheArrow()
    {
        assertNotEquals(after, before,
            "the direction of the run through " + track + " is still " + before + " after the click,"
            + " so nothing was asked of the redraw and the assertion about it says nothing");
    }

    /**
     * CONTROL - a change that really does alter the tile art still rebuilds the grid.
     *
     * The wrong fix is "never rebuild", which would leave a caption drawn on a square it had been
     * moved off. A caption is part of the tile art, so its door is the heavy one and has to stay
     * heavy.
     */
    @Test(dependsOnMethods = {"testClickingATrackTileKeepsTheTilesItDidNotChange", "testTheClickStillChangesTheArrow"})
    public void testMovingACaptionStillRebuildsTheGrid() throws Exception
    {
        final LayoutDiagram page = model.getLayout(PAGE);

        TileKey from = null;
        TileKey to = null;

        for (java.util.Map.Entry<TileKey, TileKey> caption : session.getCaptions().entrySet())
        {
            if (!PAGE.equals(caption.getKey().getPage())) continue;

            TileKey free = freeNeighbour(page, caption.getKey());

            if (free == null) continue;

            from = caption.getKey();
            to = free;

            break;
        }

        if (from == null) throw new SkipException("no caption on " + PAGE + " with room beside it");

        List<LayoutLabel> was = tilesOf(editor);

        final TileKey moveFrom = from;
        final TileKey moveTo = to;

        final boolean[] moved = new boolean[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            moved[0] = editor.getAutonomyPanel().moveCaption(moveFrom, moveTo,
                page.getComponent(moveTo.getX(), moveTo.getY())));

        assertTrue(moved[0], "the caption would not move, so this control tested nothing");

        settle();

        List<LayoutLabel> now = tilesOf(editor);

        int kept = 0;

        for (LayoutLabel label : now)
        {
            if (was.contains(label)) kept++;
        }

        assertEquals(kept, 0,
            kept + " of the page's tiles survived a caption being moved. A caption is part of the tile"
            + " art, so its door has to stay the heavy one - otherwise the name is left drawn on the"
            + " square it was moved off");
    }

    // ---------------------------------------------------------------- the fixture

    /**
     * A square carrying one run of track and nothing autonomy ignores - what a left-click cycles.
     *
     * @param page the diagram
     * @return the square, or null when the page has none
     */
    private static TileKey plainTrackOn(LayoutDiagram page)
    {
        if (session.getGraph() == null) return null;

        for (TileKey tile : session.getGraph().getTiles().keySet())
        {
            if (!PAGE.equals(tile.getPage())) continue;

            org.traincontrol.base.LayoutDiagramComponent here =
                page.getComponent(tile.getX(), tile.getY());

            if (here == null || here.isSwitch() || here.isSignal() || here.isFeedback()) continue;

            if (org.traincontrol.automationui.TilePorts.hasPortal(here.getType())) continue;

            if (session.getRoutes(tile).size() != 1) continue;

            return tile;
        }

        return null;
    }

    /**
     * A square beside this one that is inside the page, empty, and carries no caption.
     */
    private static TileKey freeNeighbour(LayoutDiagram page, TileKey tile)
    {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] step : around)
        {
            int x = tile.getX() + step[0];
            int y = tile.getY() + step[1];

            if (x < page.getMinx() || x > page.getMaxx()) continue;
            if (y < page.getMiny() || y > page.getMaxy()) continue;

            if (page.getComponent(x, y) != null) continue;

            TileKey at = new TileKey(tile.getPage(), x, y);

            if (session.getCaptionTarget(at) != null) continue;

            return at;
        }

        return null;
    }

    /**
     * Every tile the editor is drawing, in the order the container holds them.
     *
     * The spacers are left out: LayoutGrid puts an empty label along its last row and column, they are
     * on no square, and a rebuild replaces them like everything else - so they would answer the
     * question without being part of it.
     *
     * @param window the editor
     * @return the labels
     * @throws Exception on an event-thread failure
     */
    private static List<LayoutLabel> tilesOf(final LayoutEditor window) throws Exception
    {
        final List<LayoutLabel> out = new ArrayList<>();

        javax.swing.SwingUtilities.invokeAndWait(() -> collect(window.getContentPane(), out));

        return out;
    }

    private static void collect(java.awt.Container container, List<LayoutLabel> into)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (child instanceof LayoutLabel && !((LayoutLabel) child).isSpacer())
            {
                into.add((LayoutLabel) child);
            }

            if (child instanceof java.awt.Container) collect((java.awt.Container) child, into);
        }
    }

    private static void settle() throws Exception
    {
        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

        // Twice: the light door posts its running-layout rebuild one event later, and the heavy one
        // posts the annotation refresh behind the grid it has just built.
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
        javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }
}
