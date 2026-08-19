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
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.Exit;
import org.traincontrol.automationui.TileGraph.Landing;
import org.traincontrol.automationui.TileGraph.Problem;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * The tile graph: which tiles connect to which, and which way a train may move through them.
 *
 * Built from pages assembled in memory rather than from files, so these tests describe track rather than
 * fixtures - a horizontal run of straights, a switch, a tunnel pair - and none of them need hardware, a
 * Central Station, or the locomotive database.
 *
 * @author Adam
 */
public class testAutonomyDiagramTiles
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

        // The base-to-forks default must not apply here.  It would say "out of the toe only" while the
        // blades say "into the toe only", and the two together would leave the tile impassable both ways -
        // silently deleting a piece of track that is perfectly usable in the trailing direction.
        for (Map.Entry<RouteId, Route> entry : graph.getRoutes(sw).entrySet())
        {
            assertEquals(graph.defaultDirection(sw, entry.getKey()), Direction.BOTH,
                "a route the hardware already restricts should add no default of its own");
        }

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
     * A walk over the track as drawn cannot change tracks at a crossing.
     *
     * Two independent lines meeting on one square: north-south, and east-west.  Asking for a route from
     * a square on one to a square on the other must answer "no continuous track", because there is
     * none - the two lines cross without joining.
     *
     * The walk used to union the sides of every route on a square, so it arrived along one line and
     * left along the other.  setOneWayRun then reported success and restricted stretches of both, and
     * applyOneWay could not catch it: at the crossing no single route touches both the side it came
     * from and the side it left by, so that square was skipped in silence.
     */
    @Test
    public void testAWalkCannotChangeTracksAtACrossing() throws IOException
    {
        LayoutDiagram page = page("main", 5, 5);

        // the east-west line, straights at orientation 0
        straight(page, 1, 2);
        add(page, componentType.CROSSING, 2, 2, 0);
        straight(page, 3, 2);

        // and the north-south one, across it - a straight is east-west until it is turned
        add(page, componentType.STRAIGHT, 2, 1, 1);
        add(page, componentType.STRAIGHT, 2, 3, 1);

        TileGraph graph = graph(page);

        assertNotNull(graph.findUndirectedPath(key("main", 1, 2), key("main", 3, 2)),
            "the east-west line is continuous and must still be walkable");

        assertNotNull(graph.findUndirectedPath(key("main", 2, 1), key("main", 2, 3)),
            "and so is the north-south one");

        assertNull(graph.findUndirectedPath(key("main", 1, 2), key("main", 2, 1)),
            "the two lines cross without joining, so no track runs from one to the other");

        assertNull(graph.findUndirectedPath(key("main", 2, 3), key("main", 3, 2)),
            "nor the other way round");
    }

    /**
     * A switch is the opposite case, and must keep working: its forks DO meet, at the toe.
     */
    @Test
    public void testAWalkStillTakesEitherForkOfASwitch() throws IOException
    {
        LayoutDiagram page = page("main", 5, 5);

        // a SWITCH_LEFT at orientation 0 has its toe S and its legs N and W
        add(page, componentType.SWITCH_LEFT, 2, 2, 0);
        add(page, componentType.STRAIGHT, 2, 3, 1);
        add(page, componentType.STRAIGHT, 2, 1, 1);
        straight(page, 1, 2);

        TileGraph graph = graph(page);

        assertNotNull(graph.findUndirectedPath(key("main", 2, 3), key("main", 2, 1)),
            "straight through the switch");

        assertNotNull(graph.findUndirectedPath(key("main", 2, 3), key("main", 1, 2)),
            "and out by the diverging fork - both routes touch the toe, so both are continuous track");
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

        // A portal has two ports and only one of them is a side.  S is the visible one, where the tunnel
        // meets ordinary track; the pairing is the other, and it has no direction on the grid, so it is
        // addressed as a null side.  (This test used to ask for the partner at S, which conflated the
        // two - and passed while portals were in fact impassable.)

        // unpaired, the tunnel goes nowhere
        assertTrue(graph.exits(tunnelOne, Side.S).isEmpty(), "an unpaired tunnel offers no way through");

        graph.pairPortals(tunnelOne, tunnelTwo);

        List<Exit> takeIt = graph.exits(tunnelOne, Side.S);
        assertEquals(takeIt.size(), 1, "a paired tunnel should continue");
        assertNull(takeIt.get(0).getSide(), "the jump is not a side");

        Landing landing = graph.landing(tunnelOne, takeIt.get(0).getSide());
        assertNotNull(landing, "a paired tunnel should continue");
        assertEquals(landing.getTile(), tunnelTwo);
        assertNull(landing.getEntrySide(), "arriving through a portal is not arriving at a side");

        // and it is mutual
        List<Exit> backAgain = graph.exits(tunnelTwo, Side.S);
        assertEquals(backAgain.size(), 1);

        Landing back = graph.landing(tunnelTwo, backAgain.get(0).getSide());
        assertNotNull(back);
        assertEquals(back.getTile(), tunnelOne);

        assertTrue(graph.validatePortals().isEmpty(), "a mutual pairing should not be reported");
    }

    /**
     * A train must actually be able to cross a paired portal, in both directions.
     *
     * The test above checks that landing() knows where the partner is, which is not the same thing and
     * passed happily while portals were unusable: exits() skipped stub routes, so a link offered no way
     * out at all and nothing ever asked landing() the question.  Track could reach a portal and stop
     * dead there, and every cross-page route silently vanished.
     *
     * So this walks the whole way through - approach track, portal tile, the jump, the partner, and out
     * onto the track beyond - which is the only version of the question that would have failed.
     */
    @Test
    public void testATrainCanWalkThroughAPairedPortalBothWays() throws IOException
    {
        // one: straight at (1,1), tunnel below it at (1,2) opening south
        LayoutDiagram one = page("one", 4, 4);
        add(one, componentType.STRAIGHT, 1, 1, 1);          // vertical, so it meets the tunnel
        add(one, componentType.TUNNEL, 1, 2, 0);            // opens south

        // two: tunnel at (2,2) opening south, straight below at (2,3)
        LayoutDiagram two = page("two", 4, 4);
        add(two, componentType.TUNNEL, 2, 2, 0);
        add(two, componentType.STRAIGHT, 2, 3, 1);

        TileGraph graph = graph(one, two);

        TileKey tunnelOne = key("one", 1, 2);
        TileKey tunnelTwo = key("two", 2, 2);

        // unpaired, the portal is a dead end: you can arrive and never leave
        assertTrue(graph.exits(tunnelOne, Side.S).isEmpty(),
            "an unpaired portal must offer no way through");

        graph.pairPortals(tunnelOne, tunnelTwo);

        // entering the portal from its track side, the only way on is the pairing
        List<Exit> intoPortal = graph.exits(tunnelOne, Side.S);
        assertEquals(intoPortal.size(), 1, "a paired portal should offer the jump");
        assertNull(intoPortal.get(0).getSide(), "the jump has no side on the grid");

        Landing atPartner = graph.landing(tunnelOne, intoPortal.get(0).getSide());
        assertNotNull(atPartner);
        assertEquals(atPartner.getTile(), tunnelTwo);
        assertNull(atPartner.getEntrySide(), "arriving through a portal is not arriving at a side");

        // and from there back out onto the track beyond
        List<Exit> outOfPartner = graph.exits(tunnelTwo, atPartner.getEntrySide());
        assertEquals(outOfPartner.size(), 1, "the partner should put the train back on the track");
        assertEquals(outOfPartner.get(0).getSide(), Side.S);

        Landing beyond = graph.landing(tunnelTwo, outOfPartner.get(0).getSide());
        assertNotNull(beyond, "the track beyond the partner should be reachable");
        assertEquals(beyond.getTile(), key("two", 2, 3));

        // the same walk must work in the other direction, since a pairing is mutual
        List<Exit> backIn = graph.exits(tunnelTwo, Side.S);
        assertEquals(backIn.size(), 1);
        assertNull(backIn.get(0).getSide());

        Landing back = graph.landing(tunnelTwo, backIn.get(0).getSide());
        assertNotNull(back);
        assertEquals(back.getTile(), tunnelOne);

        List<Exit> outOfOne = graph.exits(tunnelOne, back.getEntrySide());
        assertEquals(outOfOne.size(), 1);
        assertEquals(outOfOne.get(0).getSide(), Side.S);
    }

    /**
     * A route button carries whatever line it is sitting on, and nothing when it sits beside the rails.
     *
     * It is a control someone put on the diagram, not track: its art touches no border and its
     * orientation is the same in horizontal and vertical runs alike.  So what it conducts cannot come
     * from the tile - it comes from the neighbours.
     */
    @Test
    public void testARouteButtonCarriesWhateverLineItSitsOn() throws IOException
    {
        // dropped into a horizontal run
        LayoutDiagram page = page("main", 8, 8);
        straight(page, 1, 1);
        add(page, componentType.ROUTE, 2, 1, 0);
        straight(page, 3, 1);

        TileGraph graph = graph(page);

        List<Exit> through = graph.exits(key("main", 2, 1), Side.W);
        assertEquals(through.size(), 1, "a button in a straight run should carry it");
        assertEquals(through.get(0).getSide(), Side.E);

        // and the run is walkable end to end
        Landing beyond = graph.landing(key("main", 2, 1), Side.E);
        assertNotNull(beyond);
        assertEquals(beyond.getTile(), key("main", 3, 1));
    }

    /**
     * The same button dropped into a vertical run carries that instead - which a fixed north-south or
     * east-west reading could only ever get right half the time, since the orientation does not say.
     */
    @Test
    public void testARouteButtonCarriesAVerticalRunToo() throws IOException
    {
        LayoutDiagram page = page("main", 8, 8);
        add(page, componentType.STRAIGHT, 2, 1, 1);
        add(page, componentType.ROUTE, 2, 2, 0);
        add(page, componentType.STRAIGHT, 2, 3, 1);

        TileGraph graph = graph(page);

        List<Exit> through = graph.exits(key("main", 2, 2), Side.N);
        assertEquals(through.size(), 1);
        assertEquals(through.get(0).getSide(), Side.S);
    }

    /**
     * A button on a curve follows the curve.  This is what the fixed-crossing reading could not do: it
     * would have offered a straight through the corner and broken the curve entirely.
     */
    @Test
    public void testARouteButtonOnACurveFollowsTheCurve() throws IOException
    {
        LayoutDiagram page = page("main", 8, 8);

        // track arriving from the west, and leaving to the south
        straight(page, 1, 2);
        add(page, componentType.ROUTE, 2, 2, 0);
        add(page, componentType.STRAIGHT, 2, 3, 1);

        TileGraph graph = graph(page);

        List<Exit> around = graph.exits(key("main", 2, 2), Side.W);
        assertEquals(around.size(), 1, "a button between a westward and a southward neighbour turns");
        assertEquals(around.get(0).getSide(), Side.S);
    }

    /**
     * A button placed beside the rails carries nothing - which is the honest reading of a control that
     * has no track meaning, and the case a fixed crossing would have invented track for.
     */
    @Test
    public void testARouteButtonBesideTheRailsCarriesNothing() throws IOException
    {
        LayoutDiagram page = page("main", 8, 8);
        straight(page, 1, 1);
        straight(page, 2, 1);

        // sitting below the run, touched by it on one side only
        add(page, componentType.ROUTE, 2, 2, 0);

        TileGraph graph = graph(page);

        for (Side side : Side.values())
        {
            assertTrue(graph.exits(key("main", 2, 2), side).isEmpty(),
                "a button with one neighbour should carry nothing, but it exits " + side);
        }
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

    /**
     * A pairing whose far end is no longer a link is reported.
     *
     * Pairings are stored by coordinate and replayed without asking what is there now, so redrawing the
     * far end as plain track left a pairing that is mutual, whose both ends exist, and that every check
     * was happy with - while the walk jumped into a square with no way out and the cross-page route
     * simply disappeared.  Of every way a link can be misconfigured, this was the only silent one.
     */
    @Test
    public void testAPairingWhoseFarEndIsNoLongerALinkIsReported() throws Exception
    {
        LayoutDiagram page = page("main", 6, 6);

        add(page, componentType.LINK, 1, 1, 0);

        // what used to be the partner, redrawn as ordinary track
        straight(page, 4, 4);

        TileGraph graph = graph(page);

        graph.pairPortals(key("main", 1, 1), key("main", 4, 4));

        List<Problem> found = graph.validatePortals();

        assertFalse(found.isEmpty(),
            "a pairing pointing at something that is not a link has to say so");
    }
}
