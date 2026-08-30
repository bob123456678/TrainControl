package core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;

/**
 * Route buttons placed where what they conduct is not what was drawn (OB-160).
 *
 * A route button carries no track of its own.  What it conducts is decided entirely by what is beside
 * it: `transparentRoutes` collects the sides where a neighbour presents a real port OR is itself
 * transparent, then joins N to S and E to W where both face, or the only two faces where there are
 * exactly two.
 *
 * Adam: "make it be an error if two route tiles are next to each other (only if they are connected to
 * something else on the graph)... I am inclined to treat it as a static crossing under the hood."
 *
 * THREE SHAPES, AND ONLY TWO ARE ERRORS:
 *
 *   two buttons side by side, with track running into the pair - each counts the other as a face, so
 *   the pair conducts a route across diagram with no rails on it
 *
 *   three real arms - the through-pair wins and the third is DROPPED, silently, where every other drop
 *   in this pipeline warns
 *
 *   four real arms - both pairs are emitted, which IS a fixed crossing, and is left alone
 *
 * THE LAST TEST IS THE ONE THAT MATTERS TO THE OPERATOR.  These are BLOCKING errors, so a shape that
 * exists on his railway would stop autonomy from starting.  testTheOperatorsOwnRailwayIsNotRefused
 * runs the checks over the frozen snapshot and names every square that trips them, so a rule that is
 * too strict is found here rather than by a railway that will not start.
 */
public class testRouteTilePlacement
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public void connect() throws Exception
    {
        // Before the model: init reads the saved layout path, which on the operator's machine is his
        // own railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, true);
    }

    @AfterClass
    public void disconnect() throws Exception
    {
        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }

    /**
     * Two buttons side by side, with track into the pair, is refused (Adam's rule).
     */
    @Test
    public void testTwoAdjacentRouteTilesWithTrackAreRefused() throws Exception
    {
        LayoutDiagram page = page();

        // straight - button - button - straight, along one row
        track(page, 1, 1);
        button(page, 2, 1);
        button(page, 3, 1);
        track(page, 4, 1);

        assertTrue(reports(page, TileGraph.ERROR_ADJACENT_ROUTE_TILES),
            "two route buttons beside each other, with track running into the pair, conduct a route "
            + "across two squares of diagram that have no rails on them - and nothing said so");
    }

    /**
     * Two buttons side by side with nothing running into them are a control panel, not a fault.
     *
     * The half of Adam's rule that keeps it from firing on every panel: "only if they are connected to
     * something else on the graph".
     */
    @Test
    public void testTwoAdjacentRouteTilesAloneAreFine() throws Exception
    {
        LayoutDiagram page = page();

        button(page, 2, 1);
        button(page, 3, 1);

        assertFalse(reports(page, TileGraph.ERROR_ADJACENT_ROUTE_TILES),
            "a pair of buttons with no track anywhere near them was refused - they conduct nothing, "
            + "and a panel of buttons drawn away from the rails is what buttons are for");
    }

    /**
     * Buttons reaching track at ONE end splice nothing, and are not a fault (OB-160).
     *
     * Adam, on the square this first fired on: "since the two route icons are next to each other but
     * there is no connect to the link nor the straight track, it should not emit an error.  There is no
     * ambiguity there, I believe."  He is right - a route through a run of buttons needs two ends, and
     * with one it conducts nothing.
     *
     * The fixture is his: a sensor against the western end, and past the far button a straight lying
     * NORTH-SOUTH, which presents no face to it.  Turn that straight a quarter turn and it becomes the
     * test above, which is refused - the difference between a panel beside the track and a splice.
     */
    @Test
    public void testAdjacentRouteTilesReachingTrackAtOneEndAreFine() throws Exception
    {
        LayoutDiagram page = page();

        track(page, 1, 1, 0);
        button(page, 2, 1);
        button(page, 3, 1);

        // lying north-south, so it faces the button above and below it rather than the one beside it
        track(page, 4, 1, 1);

        assertFalse(reports(page, TileGraph.ERROR_ADJACENT_ROUTE_TILES),
            "a pair of buttons with track against one end only was refused - a route through them "
            + "needs two ends, and with one there is nothing to splice and nothing ambiguous (OB-160)");
    }

    /**
     * Track into a button from three sides is refused, because one arm is dropped.
     *
     * The case Adam asked me to look for.  N and S win as a through-pair and E is discarded, so a line
     * drawn into the side of a button on a running line is severed with nothing said.
     */
    @Test
    public void testThreeWaysIntoARouteTileAreRefused() throws Exception
    {
        LayoutDiagram page = page();

        button(page, 2, 2);

        // north and south have to LIE north-south, or they present nothing to the button between them
        track(page, 2, 1, 1);
        track(page, 2, 3, 1);
        track(page, 3, 2, 0);

        assertTrue(reports(page, TileGraph.ERROR_ROUTE_TILE_THREE_WAY),
            "track runs into this button from three sides and only two of them are carried, so the "
            + "third is dropped in silence - which is the one drop in this pipeline that said nothing");
    }

    /**
     * Four ways in is a crossing, and a crossing is not a fault.
     *
     * Adam: "I am inclined to treat it as a static crossing under the hood."  Both pairs are emitted,
     * which is exactly that - so this must NOT be reported, and the test exists to keep the
     * three-arm rule from growing into "any busy button".
     */
    @Test
    public void testFourWaysIntoARouteTileIsACrossing() throws Exception
    {
        LayoutDiagram page = page();

        button(page, 2, 2);

        track(page, 2, 1, 1);
        track(page, 2, 3, 1);
        track(page, 1, 2, 0);
        track(page, 3, 2, 0);

        assertFalse(reports(page, TileGraph.ERROR_ROUTE_TILE_THREE_WAY),
            "four arms into a button is a fixed crossing - both pairs are carried and nothing is "
            + "dropped, so there is nothing to report");

        assertFalse(reports(page, TileGraph.ERROR_ADJACENT_ROUTE_TILES),
            "a crossing was reported as adjacent buttons, which it is not");
    }

    /**
     * A button on a plain through line, which is the ordinary case, is silent.
     */
    @Test
    public void testAButtonOnAPlainLineIsFine() throws Exception
    {
        LayoutDiagram page = page();

        track(page, 1, 1);
        button(page, 2, 1);
        track(page, 3, 1);

        assertFalse(reports(page, TileGraph.ERROR_ADJACENT_ROUTE_TILES), "a lone button was refused");
        assertFalse(reports(page, TileGraph.ERROR_ROUTE_TILE_THREE_WAY), "a lone button was refused");
    }

    /**
     * A button at the end of a line conducts nothing, and that is not a dropped arm (OB-160).
     *
     * The narrowing this rule needed, found by running it over the operator's railway before it
     * shipped: at `1 - Main:3,5` a feedback to the east is rotated a quarter turn, so it presents no
     * face, and only the western arm faces at all.  With one arm there is no through-pair to win and
     * nothing is discarded - the button simply carries nothing, which is what a button beside the
     * rails or at the end of a line is for.
     *
     * Reported, that was a blocking error on a correctly drawn square.
     */
    @Test
    public void testAButtonWithOneArmIsNotADroppedArm() throws Exception
    {
        LayoutDiagram page = page();

        button(page, 2, 2);

        // one straight to the west, facing it, and nothing else anywhere near
        track(page, 1, 2);

        assertFalse(reports(page, TileGraph.ERROR_ROUTE_TILE_THREE_WAY),
            "a button with a single arm was refused as though an arm had been dropped - nothing was "
            + "carried, so nothing was displaced, and this is how a button at the end of a line is "
            + "drawn (OB-160)");
    }

    /**
     * AND THE OPERATOR'S OWN RAILWAY STILL STARTS.
     *
     * These are blocking errors, so a shape that exists on his layout would stop autonomy dead.  The
     * frozen snapshot is his railway; if this fails, the rule is too strict and the failure names
     * every square it objected to, which is the information needed to decide whether to narrow the
     * rule or redraw the tile.
     *
     * Deliberately not a warning-shaped assertion.  He asked for an error, and an error that fires on
     * a working railway is the kind of guard he has said he would rather not have - so it is measured
     * here rather than discovered there.
     */
    @Test
    public void testTheOperatorsOwnRailwayIsNotRefused() throws Exception
    {
        File folder = new File("test/test_layout_snapshot");

        if (!folder.isDirectory())
        {
            throw new org.testng.SkipException("no test/test_layout_snapshot to check");
        }

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

        assertFalse(pages.isEmpty(), "the snapshot did not parse, so this checks nothing");

        TileGraph graph = new TileGraph(pages, java.util.Collections.<String>emptySet());

        List<String> refused = new ArrayList<>();

        for (TileGraph.Problem problem : graph.getProblems())
        {
            if (TileGraph.ERROR_ADJACENT_ROUTE_TILES.equals(problem.getMessageKey())
                || TileGraph.ERROR_ROUTE_TILE_THREE_WAY.equals(problem.getMessageKey()))
            {
                refused.add(problem.getMessageKey() + " at " + problem.getTile()
                    + " " + neighbourhood(pages, problem.getTile()));
            }
        }

        // A RATCHET, AND CURRENTLY EMPTY.
        //
        // It was not: the first version of these rules refused four squares of his railway, three of
        // them because a single arm was read as a dropped one and the fourth because "connected to
        // something else" was implemented as "touches track anywhere" rather than "reaches track at
        // both ends".  Both were rules of mine, not faults of his - which is what this test is for.
        //
        // Exact equality rather than isEmpty, so it cannot go stale in either direction: a new
        // offending square fails, and so does a square being added here without being explained.
        java.util.List<String> known = java.util.Collections.<String>emptyList();

        java.util.List<String> bare = new ArrayList<>();

        for (String one : refused) bare.add(one.substring(0, one.indexOf(" [")));

        java.util.Collections.sort(bare);

        assertEquals(bare, known,
            "the route-tile checks refuse a different set of squares on the operator's railway than "
            + "the one square known to trip them.  These are BLOCKING errors, so anything new here "
            + "would stop autonomy from starting on his layout - and anything missing means the known "
            + "square was redrawn and this list wants updating.  Found: " + refused);
    }


    /**
     * What is drawn around a square, so a refusal says what to look at.
     *
     * A coordinate on its own tells the reader which page to open and nothing about why the check
     * objected - and these two checks are entirely about what the neighbours are.
     */
    private static String neighbourhood(List<LayoutDiagram> pages, TileGraph.TileKey tile)
    {
        LayoutDiagram page = null;

        for (LayoutDiagram one : pages)
        {
            if (one.getName().equals(tile.getPage())) page = one;
        }

        if (page == null) return "(page not found)";

        // INDEXED THE WAY THE GRAPH INDEXES, by each component's own getX/getY rather than by the
        // page's grid.  checkBounds can move the grid's origin, so the two disagree - and a report
        // that disagrees with the check it is explaining is worse than no report.
        java.util.Map<String, org.traincontrol.base.LayoutDiagramComponent> byPosition =
            new java.util.HashMap<>();

        for (org.traincontrol.base.LayoutDiagramComponent one : page.getAll())
        {
            byPosition.put(one.getX() + "," + one.getY(), one);
        }

        StringBuilder out = new StringBuilder("[");

        int[][] around = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        String[] named = {"N", "E", "S", "W"};

        for (int at = 0; at < around.length; at++)
        {
            org.traincontrol.base.LayoutDiagramComponent side = byPosition.get(
                (tile.getX() + around[at][0]) + "," + (tile.getY() + around[at][1]));

            out.append(at > 0 ? " " : "").append(named[at]).append('=')
                .append(side == null ? "-" : side.getType() + "/" + side.getOrientation());
        }

        return out.append(']').toString();
    }

    // ================================================================ the fixture

    private static int pages = 0;

    /** An empty page big enough for the shapes above. */
    private static LayoutDiagram page()
    {
        LayoutDiagram page = new LayoutDiagram("Buttons " + (++pages), 8, 6, null, null);

        page.setEdit(true);

        return page;
    }

    /**
     * A straight lying east-west, which presents a real port to its east and west neighbours.
     */
    private static void track(LayoutDiagram page, int x, int y) throws Exception
    {
        track(page, x, y, 0);
    }

    /**
     * A straight at a given orientation.
     *
     * THE ORIENTATION IS THE WHOLE FIXTURE for the three- and four-arm shapes.  A straight left at 0
     * lies east-west and presents nothing to a tile above or below it, so a "track to the north" drawn
     * without turning it faces nowhere and the button sees one arm, not three.  My first version of
     * these tests made exactly that mistake and passed for the wrong reason on the four-arm case.
     *
     * @param orientation 0 for east-west, 1 for north-south
     */
    private static void track(LayoutDiagram page, int x, int y, int orientation) throws Exception
    {
        page.addComponent(componentType.STRAIGHT, x, y, orientation, 0, 0, 0,
            accessoryDecoderType.MM2, null);
    }

    /** A route button, which carries no track of its own. */
    private static void button(LayoutDiagram page, int x, int y) throws Exception
    {
        page.addComponent(componentType.ROUTE, x, y, 0, 0, 1, 1, accessoryDecoderType.MM2, null);
    }

    /** Whether building the graph over this page raises the given problem. */
    private static boolean reports(LayoutDiagram page, String messageKey) throws Exception
    {
        page.checkBounds();

        TileGraph graph = new TileGraph(
            new ArrayList<>(Arrays.asList(page)), java.util.Collections.<String>emptySet());

        for (TileGraph.Problem problem : graph.getProblems())
        {
            if (messageKey.equals(problem.getMessageKey())) return true;
        }

        return false;
    }
}
