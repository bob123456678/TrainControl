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
 * A route button carries no track of its own: `transparentRoutes` collects the sides where a neighbor presents a real
 * port OR is itself transparent, then joins N to S and E to W where both face, or the only two faces where there are
 * exactly two.  Adam: "make it be an error if two route tiles are next to each other (only if they are connected to
 * something else on the graph)... I am inclined to treat it as a static crossing under the hood."  Three shapes, only
 * two are errors:
 *
 *   two buttons side by side, with track running into the pair - the pair conducts a route across diagram with no rails
 *   three real arms - the through-pair wins and the third is DROPPED, silently
 *   four real arms - both pairs are emitted, a fixed crossing, left alone
 *
 * These are BLOCKING errors, so `testTheOperatorsOwnRailwayIsNotRefused` runs the checks over the frozen snapshot and
 * names every square that trips them: a rule too strict is found here rather than by a railway that will not start.
 */
public class testRouteTilePlacement
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public void connect() throws Exception
    {
        // Before the model: init reads the saved layout path, which on the operator's machine is his own railway
        // (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedInDebug();
    }

    @AfterClass(alwaysRun = true)
    public void disconnect() throws Exception
    {
        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }

    /**
     * The road a route tile carries can be turned back into the sides it joins, which drawing it needs (OB-279): a
     * route tile has no port map of its own, and looking its road up in the port map by index found nothing, so a
     * train's tail across one stopped the orange line.
     *
     * MUTATION: `transparentRouteOf` answering null fails this; and dropping its use from `LayoutLabel.routesOf` fails
     * `core.testTheShadingFollowsTheTrain`, whose train lies across that route tile.
     */
    @Test
    public void testARouteTilesRoadCanBeDrawn() throws Exception
    {
        LayoutDiagram page = page();

        // straight - button - straight, along one row: the button carries east-west
        track(page, 1, 1);
        button(page, 2, 1);
        track(page, 3, 1);

        page.checkBounds();

        TileGraph graph = new TileGraph(new ArrayList<>(Arrays.asList(page)), java.util.Collections.<String>emptySet());

        java.util.Map<TileGraph.RouteId, org.traincontrol.automationui.TilePorts.Route> roads =
            graph.getRoutes(new TileGraph.TileKey(page.getName(), 2, 1));

        assertEquals(roads.size(), 1, "precondition: a route button between two straights carries one road, and it"
            + " carries " + roads.keySet());

        for (java.util.Map.Entry<TileGraph.RouteId, org.traincontrol.automationui.TilePorts.Route> road : roads.entrySet())
        {
            org.traincontrol.automationui.TilePorts.Route drawn = TileGraph.transparentRouteOf(road.getKey());

            assertNotNull(drawn, "the route tile's road " + road.getKey() + " cannot be turned into sides, so nothing"
                + " can draw it - the orange line and the grey band break at every route tile");

            assertEquals(java.util.EnumSet.of(drawn.getA(), drawn.getB()),
                java.util.EnumSet.of(road.getValue().getA(), road.getValue().getB()),
                "the route tile's road " + road.getKey() + " comes back as the wrong sides");
        }

        assertNull(TileGraph.transparentRouteOf(new TileGraph.RouteId(0, 0)),
            "an ordinary tile's first road was read as a route tile's, so a straight would be drawn along the wrong"
            + " axis");
    }

    /** Two buttons side by side, with track into the pair, is refused (Adam's rule). */
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
     * Two buttons side by side with nothing running into them are a control panel, not a fault ("only if they are
     * connected to something else on the graph").
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
     * Buttons reaching track at ONE end splice nothing, and are not a fault (OB-160; Adam: "since the two route icons
     * are next to each other but there is no connect to the link nor the straight track, it should not emit an
     * error.").  The far straight lies NORTH-SOUTH and presents no face; turned a quarter it becomes the refused test
     * above.
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
     * Track into a button from three sides is refused, because one arm is dropped: N and S win as a through-pair and E
     * is discarded.
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
     * Four ways in is a crossing, not a fault (Adam: "I am inclined to treat it as a static crossing under the hood.");
     * this keeps the three-arm rule from growing into "any busy button".
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

    /** A button on a plain through line, the ordinary case, is silent. */
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
     * A button at the end of a line conducts nothing, and that is not a dropped arm (OB-160): with one facing arm there
     * is no through-pair to win and nothing is discarded.
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
     * AND THE OPERATOR'S OWN RAILWAY STILL STARTS: these are blocking errors, so the failure names every square the
     * rule objected to, to decide whether to narrow the rule or redraw the tile.
     */
    @Test
    public void testTheOperatorsOwnRailwayIsNotRefused() throws Exception
    {
        File folder = new File("test/test_layout_snapshot");

        if (!folder.isDirectory())
        {
            throw new AssertionError("no test/test_layout_snapshot to check");
        }

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

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

        // A RATCHET, AND CURRENTLY EMPTY.  Exact equality rather than isEmpty, so it cannot go stale in either
        // direction: a new offending square fails, and so does a square added here without being explained.
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


    /** What is drawn around a square, so a refusal says what to look at. */
    private static String neighbourhood(List<LayoutDiagram> pages, TileGraph.TileKey tile)
    {
        LayoutDiagram page = null;

        for (LayoutDiagram one : pages)
        {
            if (one.getName().equals(tile.getPage())) page = one;
        }

        if (page == null) return "(page not found)";

        // INDEXED THE WAY THE GRAPH INDEXES, by each component's own getX/getY: checkBounds can move the grid's origin.
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

    /** A straight lying east-west, which presents a real port to its east and west neighbors. */
    private static void track(LayoutDiagram page, int x, int y) throws Exception
    {
        track(page, x, y, 0);
    }

    /**
     * A straight at a given orientation (0 for east-west, 1 for north-south).  The orientation is the whole fixture for
     * the three- and four-arm shapes: a straight left at 0 presents nothing to a tile above or below it.
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
