import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.base.TileGraph;
import org.traincontrol.base.TileGraph.Direction;
import org.traincontrol.base.TileGraph.Exit;
import org.traincontrol.base.TileGraph.Landing;
import org.traincontrol.base.TileGraph.Problem;
import org.traincontrol.base.TileGraph.RouteId;
import org.traincontrol.base.TileGraph.TileKey;
import org.traincontrol.base.TilePorts.Route;
import org.traincontrol.base.TilePorts.Side;

/**
 * The tile graph: which tiles connect to which, and which way a train may move through them.
 *
 * Built from pages assembled in memory rather than from files, so these tests describe track rather than
 * fixtures - a horizontal run of straights, a switch, a tunnel pair - and none of them need hardware, a
 * Central Station, or the locomotive database.
 *
 * @author Adam
 */
public class testTileGraph
{
    /**
     * A run of straights connects end to end, and stops where the track stops.  Nothing connects to a
     * blank square, which is what keeps a diagram's white space out of the graph.
     */
    @Test
    public void testAStraightRunConnectsAndStopsAtBlankSquares() throws IOException
    {
        LayoutDiagram page = page("main", 5, 3);
        straight(page, 1, 1);
        straight(page, 2, 1);
        straight(page, 3, 1);

        TileGraph graph = graph(page);

        // entering the middle tile from the west, we leave east and land on its neighbour
        List<Exit> exits = graph.exits(key("main", 2, 1), Side.W);
        assertEquals(exits.size(), 1);
        assertEquals(exits.get(0).getSide(), Side.E);

        Landing landing = graph.landing(key("main", 2, 1), Side.E);
        assertNotNull(landing);
        assertEquals(landing.getTile(), key("main", 3, 1));
        assertEquals(landing.getEntrySide(), Side.W);

        // the run ends: east of the last tile is empty
        assertNull(graph.landing(key("main", 3, 1), Side.E));

        // and no straight ever offers a way out to the north
        assertTrue(graph.exits(key("main", 2, 1), Side.N).isEmpty());
    }

    /**
     * Rotation breaks adjacency exactly as the art says.  A vertical straight beside a horizontal one has
     * no port facing it, so the two do not connect however close they are drawn.
     */
    @Test
    public void testRotationBreaksAdjacency() throws IOException
    {
        LayoutDiagram page = page("main", 4, 3);
        straight(page, 1, 1);
        // orientation 1 turns a straight vertical
        add(page, componentType.STRAIGHT, 2, 1, 1);

        TileGraph graph = graph(page);

        // the horizontal tile still wants to leave east, but its neighbour has no west port
        assertNull(graph.landing(key("main", 1, 1), Side.E));
    }

    /**
     * A switch runs base to forks until the user says otherwise: entering at the toe fans out, entering
     * at a fork does not go anywhere.  This is the default that makes every switch on an untouched
     * diagram deterministic without anybody clicking.
     */
    @Test
    public void testSwitchesDefaultToBaseToForks() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.SWITCH_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);
        TileKey sw = key("main", 1, 1);

        // the toe of a SWITCH_LEFT at orientation 0 is S; from there both positions are available
        List<Exit> fromToe = graph.exits(sw, Side.S);
        assertEquals(fromToe.size(), 2, "both branches should be reachable from the toe");
        assertEquals(exitSides(fromToe), new HashSet<>(Arrays.asList(Side.N, Side.W)));

        // trailing moves are closed until enabled
        assertTrue(graph.exits(sw, Side.N).isEmpty(), "trailing from the straight leg should be closed");
        assertTrue(graph.exits(sw, Side.W).isEmpty(), "trailing from the branch should be closed");
    }

    /**
     * Opening the trailing direction is per branch, not per switch.  Enabling one leg must not quietly
     * open the other.
     */
    @Test
    public void testTrailingIsEnabledPerBranch() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.SWITCH_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);
        TileKey sw = key("main", 1, 1);

        // find the straight route (state 0) and open it both ways
        graph.setDirection(sw, new RouteId(0, 0), Direction.BOTH);

        assertEquals(graph.exits(sw, Side.N).size(), 1, "the straight leg should now trail");
        assertTrue(graph.exits(sw, Side.W).isEmpty(), "the diverging leg should still be closed");

        // the toe still fans out both ways
        assertEquals(graph.exits(sw, Side.S).size(), 2);
    }

    /**
     * A branch set to none is not traversable either way - this is how a physically present but unused
     * route is taken out of the graph.
     */
    @Test
    public void testABranchSetToNoneIsNotTraversable() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.SWITCH_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);
        TileKey sw = key("main", 1, 1);

        // close the diverging route (state 1)
        graph.setDirection(sw, new RouteId(1, 0), Direction.NONE);

        List<Exit> fromToe = graph.exits(sw, Side.S);
        assertEquals(fromToe.size(), 1, "only the straight route should remain");
        assertEquals(fromToe.get(0).getSide(), Side.N);
    }

    /**
     * Each exit carries the switch position it needs, which is where the edge's config commands come from.
     */
    @Test
    public void testExitsCarryTheSwitchPositionTheyRequire() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.SWITCH_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);

        for (Exit exit : graph.exits(key("main", 1, 1), Side.S))
        {
            if (exit.getSide() == Side.N)
            {
                assertEquals(exit.getState(), 0, "the straight route is the unswitched position");
            }
            else
            {
                assertEquals(exit.getState(), 1, "the diverging route is the switched position");
            }
        }
    }

    /**
     * A defective switch cannot be thrown, so it may only be trailed through - and no user setting can
     * re-open the facing direction, because that is the hardware talking, not a preference.
     */
    @Test
    public void testDefectiveSwitchesRefuseFacingMovesEvenIfTheUserAsks() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.CUSTOM_PERM_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);
        TileKey sw = key("main", 1, 1);

        // trailing works: entering at a fork leaves at the toe
        List<Exit> fromStraight = graph.exits(sw, Side.N);
        assertEquals(fromStraight.size(), 1);
        assertEquals(fromStraight.get(0).getSide(), Side.S);

        // facing is refused
        assertTrue(graph.exits(sw, Side.S).isEmpty());

        // and asking for it changes nothing
        for (Map.Entry<RouteId, Route> entry : graph.getRoutes(sw).entrySet())
        {
            graph.setDirection(sw, entry.getKey(), Direction.BOTH);
        }

        assertTrue(graph.exits(sw, Side.S).isEmpty(),
            "a switch that cannot be thrown must not become facing-traversable on request");

        // it is reported, so the user knows autonomy is treating it as broken
        assertTrue(hasProblem(graph, TileGraph.WARN_PERMANENT_TURNOUT, false));
    }

    /**
     * A crossing offers two independent through routes.  Entering from the north leaves south and nowhere
     * else - the two routes share the tile without meeting.
     */
    @Test
    public void testCrossingRoutesDoNotMeet() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.CROSSING, 1, 1, 0);

        TileGraph graph = graph(page);

        List<Exit> fromNorth = graph.exits(key("main", 1, 1), Side.N);
        assertEquals(fromNorth.size(), 1);
        assertEquals(fromNorth.get(0).getSide(), Side.S);

        List<Exit> fromEast = graph.exits(key("main", 1, 1), Side.E);
        assertEquals(fromEast.size(), 1);
        assertEquals(fromEast.get(0).getSide(), Side.W);
    }

    /**
     * An overpass behaves identically here - what separates it from a crossing is lock derivation, not
     * connectivity.
     */
    @Test
    public void testOverpassRoutesDoNotMeetEither() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.OVERPASS, 1, 1, 0);

        TileGraph graph = graph(page);

        assertEquals(graph.exits(key("main", 1, 1), Side.N).get(0).getSide(), Side.S);
        assertEquals(graph.exits(key("main", 1, 1), Side.E).get(0).getSide(), Side.W);
    }

    /**
     * An unpaired tunnel stops the track.  Pairing it continues onto the partner tile, which may be on
     * another page - that is the only way a walk crosses pages.
     */
    @Test
    public void testTunnelsOnlyContinueOncePaired() throws IOException
    {
        LayoutDiagram one = page("one", 4, 4);
        straight(one, 1, 1);
        // a tunnel at orientation 0 opens to the south
        add(one, componentType.TUNNEL, 1, 2, 0);

        LayoutDiagram two = page("two", 4, 4);
        add(two, componentType.TUNNEL, 2, 2, 0);
        straight(two, 2, 1);

        TileGraph graph = graph(one, two);

        TileKey tunnelOne = key("one", 1, 2);
        TileKey tunnelTwo = key("two", 2, 2);

        // unpaired, the tunnel goes nowhere
        assertNull(graph.landing(tunnelOne, Side.S));

        graph.pairPortals(tunnelOne, tunnelTwo);

        Landing landing = graph.landing(tunnelOne, Side.S);
        assertNotNull(landing, "a paired tunnel should continue");
        assertEquals(landing.getTile(), tunnelTwo);
        assertEquals(landing.getEntrySide(), Side.S);

        // and it is mutual
        Landing back = graph.landing(tunnelTwo, Side.S);
        assertNotNull(back);
        assertEquals(back.getTile(), tunnelOne);

        assertTrue(graph.validatePortals().isEmpty(), "a mutual pairing should not be reported");
    }

    /**
     * A half pairing is an error rather than a one-way jump: a train that can get in but never out is a
     * worse outcome than being told the pairing is wrong.
     */
    @Test
    public void testHalfPairedPortalsAreReported() throws IOException
    {
        LayoutDiagram one = page("one", 4, 4);
        add(one, componentType.TUNNEL, 1, 2, 0);

        LayoutDiagram two = page("two", 4, 4);
        add(two, componentType.TUNNEL, 2, 2, 0);
        add(two, componentType.TUNNEL, 3, 2, 0);

        TileGraph graph = graph(one, two);

        // one names two, but two names a third tile
        graph.pairPortals(key("one", 1, 2), key("two", 2, 2));
        graph.pairPortals(key("two", 2, 2), key("two", 3, 2));

        assertFalse(graph.validatePortals().isEmpty(), "a broken pairing should be reported");
    }

    /**
     * Direction is stored per tile, so a one-way run is set by marking its tiles, and the opposing
     * direction disappears from the tiles themselves rather than from some connection between them.
     */
    @Test
    public void testPerTileDirectionClosesOneWay() throws IOException
    {
        LayoutDiagram page = page("main", 5, 3);
        straight(page, 1, 1);
        straight(page, 2, 1);
        straight(page, 3, 1);

        TileGraph graph = graph(page);
        TileKey middle = key("main", 2, 1);

        // by default the middle tile is traversable both ways
        assertFalse(graph.exits(middle, Side.W).isEmpty());
        assertFalse(graph.exits(middle, Side.E).isEmpty());

        // a straight at orientation 0 is E-W; allow travel toward W only
        RouteId only = graph.getRoutes(middle).keySet().iterator().next();
        Route route = graph.getRoutes(middle).get(only);
        Direction towardWest = route.getA() == Side.W ? Direction.TOWARD_A : Direction.TOWARD_B;

        graph.setDirection(middle, only, towardWest);

        assertFalse(graph.exits(middle, Side.E).isEmpty(), "entering from the east still leaves west");
        assertTrue(graph.exits(middle, Side.W).isEmpty(), "entering from the west is now closed");

        // and closing it entirely stops the run at that tile
        graph.setDirection(middle, only, Direction.NONE);
        assertTrue(graph.exits(middle, Side.E).isEmpty());
        assertTrue(graph.exits(middle, Side.W).isEmpty());
    }

    /**
     * An excluded page contributes nothing at all - no tiles, no feedback, no problems.  This is what
     * keeps a page duplicated for display from minting a second Point for every sensor it redraws.
     */
    @Test
    public void testExcludedPagesContributeNothing() throws IOException
    {
        LayoutDiagram main = page("main", 4, 4);
        feedback(main, 1, 1, 5);

        LayoutDiagram combined = page("combined", 4, 4);
        feedback(combined, 1, 1, 5);
        add(combined, componentType.TURNTABLE, 2, 2, 0);

        TileGraph graph = new TileGraph(Arrays.asList(main, combined),
            new HashSet<>(Collections.singletonList("combined")));

        assertEquals(graph.getFeedbackTiles().size(), 1, "the duplicated sensor should not appear twice");
        assertEquals(graph.getPages(), new HashSet<>(Collections.singletonList("main")));

        // not even its turntable is worth warning about, since autonomy never looks at that page
        assertFalse(hasProblem(graph, TileGraph.WARN_TURNTABLE, false));
    }

    /**
     * Scissors are a drawing convention rather than a routing element, so a diagram carrying one is
     * refused outright.  Ignoring the tile would leave a hole that walks quietly route around.
     */
    @Test
    public void testScissorsBlockTheBuild() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.CUSTOM_SCISSORS, 1, 1, 0);

        TileGraph graph = graph(page);

        assertTrue(hasProblem(graph, TileGraph.ERROR_SCISSORS, true));
        assertTrue(graph.hasBlockingProblems());

        // and it carries no track, so nothing routes through it either
        assertTrue(graph.exits(key("main", 1, 1), Side.S).isEmpty());
    }

    /**
     * A turntable is legitimate track that simply is not routable: autonomy stops there rather than the
     * diagram being refused.
     */
    @Test
    public void testTurntablesStopWithoutBlocking() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.TURNTABLE, 1, 1, 0);

        TileGraph graph = graph(page);

        assertTrue(hasProblem(graph, TileGraph.WARN_TURNTABLE, false));
        assertFalse(graph.hasBlockingProblems(), "a turntable must not refuse the diagram");
        assertTrue(graph.exits(key("main", 1, 1), Side.N).isEmpty());
    }

    /**
     * Every feedback tile is a Point, and nothing else is.  Plain track carries no sensor and so cannot
     * be a Point however it is drawn.
     */
    @Test
    public void testEveryFeedbackTileIsFoundAndNothingElseIs() throws IOException
    {
        LayoutDiagram page = page("main", 6, 4);
        feedback(page, 1, 1, 11);
        feedback(page, 2, 1, 12);
        straight(page, 3, 1);
        add(page, componentType.SWITCH_LEFT, 4, 1, 0);

        TileGraph graph = graph(page);

        assertEquals(graph.getFeedbackTiles().size(), 2);
        assertTrue(graph.getFeedbackTiles().contains(key("main", 1, 1)));
        assertTrue(graph.getFeedbackTiles().contains(key("main", 2, 1)));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private LayoutDiagram page(String name, int sx, int sy)
    {
        return new LayoutDiagram(name, sx, sy, null, null);
    }

    private void add(LayoutDiagram page, componentType type, int x, int y, int orientation)
        throws IOException
    {
        page.addComponent(type, x, y, orientation, 0, 0, 0, accessoryDecoderType.MM2, null);
    }

    private void straight(LayoutDiagram page, int x, int y) throws IOException
    {
        add(page, componentType.STRAIGHT, x, y, 0);
    }

    private void feedback(LayoutDiagram page, int x, int y, int address) throws IOException
    {
        page.addComponent(componentType.FEEDBACK, x, y, 0, 0, address, address,
            accessoryDecoderType.MM2, null);
    }

    private TileGraph graph(LayoutDiagram... pages)
    {
        return new TileGraph(new ArrayList<>(Arrays.asList(pages)), Collections.<String>emptySet());
    }

    private TileKey key(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }

    private Set<Side> exitSides(List<Exit> exits)
    {
        Set<Side> out = new HashSet<>();

        for (Exit e : exits)
        {
            out.add(e.getSide());
        }

        return out;
    }

    private boolean hasProblem(TileGraph graph, String messageKey, boolean blocking)
    {
        for (Problem p : graph.getProblems())
        {
            if (messageKey.equals(p.getMessageKey()) && p.isBlocking() == blocking) return true;
        }

        return false;
    }
}
