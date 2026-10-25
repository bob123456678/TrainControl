package core;

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
 * The tile graph: which tiles connect to which, and which way a train may move through them. Built from pages assembled
 * in memory, so these tests describe track rather than fixtures and need no hardware, Central Station or locomotive
 * database.
 *
 * @author Adam
 */
public class testAutonomyDiagramTiles
{
    /**
     * A run of straights connects end to end and stops where the track stops; nothing connects to a blank square, which
     * keeps a diagram's white space out of the graph.
     */
    @Test
    public void testAStraightRunConnectsAndStopsAtBlankSquares() throws IOException
    {
        LayoutDiagram page = page("main", 5, 3);
        straight(page, 1, 1);
        straight(page, 2, 1);
        straight(page, 3, 1);

        TileGraph graph = graph(page);

        List<Exit> exits = graph.exits(key("main", 2, 1), Side.W);
        assertEquals(exits.size(), 1);
        assertEquals(exits.get(0).getSide(), Side.E);

        Landing landing = graph.landing(key("main", 2, 1), Side.E);
        assertNotNull(landing);
        assertEquals(landing.getTile(), key("main", 3, 1));
        assertEquals(landing.getEntrySide(), Side.W);

        assertNull(graph.landing(key("main", 3, 1), Side.E));

        assertTrue(graph.exits(key("main", 2, 1), Side.N).isEmpty());
    }

    /**
     * Rotation breaks adjacency as the art says: a vertical straight beside a horizontal one has no port facing it.
     */
    @Test
    public void testRotationBreaksAdjacency() throws IOException
    {
        LayoutDiagram page = page("main", 4, 3);
        straight(page, 1, 1);
        add(page, componentType.STRAIGHT, 2, 1, 1);

        TileGraph graph = graph(page);

        assertNull(graph.landing(key("main", 1, 1), Side.E));
    }

    /**
     * A switch runs base to forks until the user says otherwise: entering at the toe fans out, entering at a fork goes
     * nowhere, so every switch on an untouched diagram is deterministic.
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

        assertTrue(graph.exits(sw, Side.N).isEmpty(), "trailing from the straight leg should be closed");
        assertTrue(graph.exits(sw, Side.W).isEmpty(), "trailing from the branch should be closed");
    }

    /** Opening the trailing direction is per branch, not per switch. */
    @Test
    public void testTrailingIsEnabledPerBranch() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.SWITCH_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);
        TileKey sw = key("main", 1, 1);

        graph.setDirection(sw, new RouteId(0, 0), Direction.BOTH);

        assertEquals(graph.exits(sw, Side.N).size(), 1, "the straight leg should now trail");
        assertTrue(graph.exits(sw, Side.W).isEmpty(), "the diverging leg should still be closed");

        assertEquals(graph.exits(sw, Side.S).size(), 2);
    }

    /**
     * A branch set to none is not traversable either way: how a present but unused route is taken out of the graph.
     */
    @Test
    public void testABranchSetToNoneIsNotTraversable() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.SWITCH_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);
        TileKey sw = key("main", 1, 1);

        graph.setDirection(sw, new RouteId(1, 0), Direction.NONE);

        List<Exit> fromToe = graph.exits(sw, Side.S);
        assertEquals(fromToe.size(), 1, "only the straight route should remain");
        assertEquals(fromToe.get(0).getSide(), Side.N);
    }

    /** Each exit carries the switch position it needs, where the edge's config commands come from. */
    @Test
    public void testExitsCarryTheSwitchPositionTheyRequire() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.SWITCH_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);

        List<Exit> exits = graph.exits(key("main", 1, 1), Side.S);

        // Floor: without this, exits() returning empty would leave the loop below running zero times
        // and this test would pass having checked neither the straight nor the diverging exit.
        assertFalse(exits.isEmpty(), "a switch toe should have exits to check");

        for (Exit exit : exits)
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
     * A defective switch cannot be thrown, so it may only be trailed through, and no user setting re-opens the facing
     * direction (the hardware speaking).
     */
    @Test
    public void testDefectiveSwitchesRefuseFacingMovesEvenIfTheUserAsks() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.CUSTOM_PERM_LEFT, 1, 1, 0);

        TileGraph graph = graph(page);
        TileKey sw = key("main", 1, 1);

        // The base-to-forks default must not apply here: it would say "out of the toe only" while the blades say "into
        // the toe only", silently deleting usable track.
        for (Map.Entry<RouteId, Route> entry : graph.getRoutes(sw).entrySet())
        {
            assertEquals(graph.defaultDirection(sw, entry.getKey()), Direction.BOTH,
                "a route the hardware already restricts should add no default of its own");
        }

        List<Exit> fromStraight = graph.exits(sw, Side.N);
        assertEquals(fromStraight.size(), 1);
        assertEquals(fromStraight.get(0).getSide(), Side.S);

        assertTrue(graph.exits(sw, Side.S).isEmpty());

        for (Map.Entry<RouteId, Route> entry : graph.getRoutes(sw).entrySet())
        {
            graph.setDirection(sw, entry.getKey(), Direction.BOTH);
        }

        assertTrue(graph.exits(sw, Side.S).isEmpty(),
            "a switch that cannot be thrown must not become facing-traversable on request");

        assertTrue(hasProblem(graph, TileGraph.WARN_PERMANENT_TURNOUT, false));
    }

    /** A crossing offers two independent through routes: entering from the north leaves south and nowhere else. */
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
     * A walk over the track as drawn cannot change tracks at a crossing: a route from a square on the north-south line
     * to one on the east-west line must answer "no continuous track". Taking the union of every route's sides on a
     * square would let the walk arrive along one line and leave along the other, and `applyOneWay` would skip that
     * square in silence.
     */
    @Test
    public void testAWalkCannotChangeTracksAtACrossing() throws IOException
    {
        LayoutDiagram page = page("main", 5, 5);

        straight(page, 1, 2);
        add(page, componentType.CROSSING, 2, 2, 0);
        straight(page, 3, 2);

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

    /** A switch is the opposite case: its forks DO meet, at the toe. */
    @Test
    public void testAWalkStillTakesEitherForkOfASwitch() throws IOException
    {
        LayoutDiagram page = page("main", 5, 5);

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

    /** An overpass behaves as a crossing here; what separates them is lock derivation, not connectivity. */
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
     * An unpaired tunnel stops the track; pairing it continues onto the partner tile, which may be on another page, the
     * only way a walk crosses pages.
     */
    @Test
    public void testTunnelsOnlyContinueOncePaired() throws IOException
    {
        LayoutDiagram one = page("one", 4, 4);
        straight(one, 1, 1);
        add(one, componentType.TUNNEL, 1, 2, 0);

        LayoutDiagram two = page("two", 4, 4);
        add(two, componentType.TUNNEL, 2, 2, 0);
        straight(two, 2, 1);

        TileGraph graph = graph(one, two);

        TileKey tunnelOne = key("one", 1, 2);
        TileKey tunnelTwo = key("two", 2, 2);

        // A portal has two ports and only one of them is a side: S is the visible one, where the tunnel meets ordinary
        // track, and the pairing has no direction on the grid, so it is addressed as a null side.

        assertTrue(graph.exits(tunnelOne, Side.S).isEmpty(), "an unpaired tunnel offers no way through");

        graph.pairPortals(tunnelOne, tunnelTwo);

        List<Exit> takeIt = graph.exits(tunnelOne, Side.S);
        assertEquals(takeIt.size(), 1, "a paired tunnel should continue");
        assertNull(takeIt.get(0).getSide(), "the jump is not a side");

        Landing landing = graph.landing(tunnelOne, takeIt.get(0).getSide());
        assertNotNull(landing, "a paired tunnel should continue");
        assertEquals(landing.getTile(), tunnelTwo);
        assertNull(landing.getEntrySide(), "arriving through a portal is not arriving at a side");

        List<Exit> backAgain = graph.exits(tunnelTwo, Side.S);
        assertEquals(backAgain.size(), 1);

        Landing back = graph.landing(tunnelTwo, backAgain.get(0).getSide());
        assertNotNull(back);
        assertEquals(back.getTile(), tunnelOne);

        assertTrue(graph.validatePortals().isEmpty(), "a mutual pairing should not be reported");
    }

    /**
     * A train must actually be able to cross a paired portal, in both directions. The test above only checks that
     * landing() knows the partner, which passed while exits() skipped stub routes and portals were unusable; this walks
     * the whole way through (approach track, portal, jump, partner, out onto the track beyond).
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
     * A route button carries whatever line it is sitting on, and nothing beside the rails: it is a control, not track
     * (no border art, the same orientation in horizontal and vertical runs), so what it conducts comes from the
     * neighbors.
     */
    @Test
    public void testARouteButtonCarriesWhateverLineItSitsOn() throws IOException
    {
        LayoutDiagram page = page("main", 8, 8);
        straight(page, 1, 1);
        add(page, componentType.ROUTE, 2, 1, 0);
        straight(page, 3, 1);

        TileGraph graph = graph(page);

        List<Exit> through = graph.exits(key("main", 2, 1), Side.W);
        assertEquals(through.size(), 1, "a button in a straight run should carry it");
        assertEquals(through.get(0).getSide(), Side.E);

        Landing beyond = graph.landing(key("main", 2, 1), Side.E);
        assertNotNull(beyond);
        assertEquals(beyond.getTile(), key("main", 3, 1));
    }

    /**
     * The same button in a vertical run carries that instead, which a fixed north-south or east-west reading could only
     * get right half the time.
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

    /** A button on a curve follows the curve, which a fixed-crossing reading could not do. */
    @Test
    public void testARouteButtonOnACurveFollowsTheCurve() throws IOException
    {
        LayoutDiagram page = page("main", 8, 8);

        straight(page, 1, 2);
        add(page, componentType.ROUTE, 2, 2, 0);
        add(page, componentType.STRAIGHT, 2, 3, 1);

        TileGraph graph = graph(page);

        List<Exit> around = graph.exits(key("main", 2, 2), Side.W);
        assertEquals(around.size(), 1, "a button between a westward and a southward neighbour turns");
        assertEquals(around.get(0).getSide(), Side.S);
    }

    /**
     * A button beside the rails carries nothing: a control with no track meaning, where a fixed crossing would have
     * invented track.
     */
    @Test
    public void testARouteButtonBesideTheRailsCarriesNothing() throws IOException
    {
        LayoutDiagram page = page("main", 8, 8);
        straight(page, 1, 1);
        straight(page, 2, 1);

        add(page, componentType.ROUTE, 2, 2, 0);

        TileGraph graph = graph(page);

        for (Side side : Side.values())
        {
            assertTrue(graph.exits(key("main", 2, 2), side).isEmpty(),
                "a button with one neighbour should carry nothing, but it exits " + side);
        }
    }

    /**
     * A half pairing is an error rather than a one-way jump: a train that can get in but never out is worse than being
     * told the pairing is wrong.
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
     * Direction is stored per tile, so a one-way run is set by marking its tiles; the opposing direction disappears
     * from the tiles themselves.
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

        assertFalse(graph.exits(middle, Side.W).isEmpty());
        assertFalse(graph.exits(middle, Side.E).isEmpty());

        // a straight at orientation 0 is E-W; allow travel toward W only
        RouteId only = graph.getRoutes(middle).keySet().iterator().next();
        Route route = graph.getRoutes(middle).get(only);
        Direction towardWest = route.getA() == Side.W ? Direction.TOWARD_A : Direction.TOWARD_B;

        graph.setDirection(middle, only, towardWest);

        assertFalse(graph.exits(middle, Side.E).isEmpty(), "entering from the east still leaves west");
        assertTrue(graph.exits(middle, Side.W).isEmpty(), "entering from the west is now closed");

        graph.setDirection(middle, only, Direction.NONE);
        assertTrue(graph.exits(middle, Side.E).isEmpty());
        assertTrue(graph.exits(middle, Side.W).isEmpty());
    }

    /**
     * An excluded page contributes nothing at all, which keeps a page duplicated for display from minting a second
     * Point for every sensor it redraws.
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
     * Scissors are a drawing convention, not a routing element, so a diagram carrying one is refused outright: ignoring
     * the tile would leave a hole walks quietly route around.
     */
    @Test
    public void testScissorsBlockTheBuild() throws IOException
    {
        LayoutDiagram page = page("main", 4, 4);
        add(page, componentType.CUSTOM_SCISSORS, 1, 1, 0);

        TileGraph graph = graph(page);

        assertTrue(hasProblem(graph, TileGraph.ERROR_SCISSORS, true));
        assertTrue(graph.hasBlockingProblems());

        assertTrue(graph.exits(key("main", 1, 1), Side.S).isEmpty());
    }

    /**
     * A turntable is legitimate track that is not routable: autonomy stops there rather than the diagram being refused.
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

    /** Every feedback tile is a Point, and nothing else is. */
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

    /**
     * A link with track running into it, pointing at a page autonomy uses, refuses the diagram (OB-150): trains reach
     * it and have nowhere to go, and the page it was drawn to continue onto is real and never joined up.
     */
    @Test
    public void testAnUnpairedLinkToAnIncludedPageBlocks() throws Exception
    {
        LayoutDiagram main = page("main", 6, 6);
        LayoutDiagram other = page("other", 6, 6);

        // Track either side, so whichever way the stub faces there is something to arrive from.
        straight(main, 0, 1);
        linkTo(main, 1, 1, 1);
        straight(main, 2, 1);

        TileGraph graph = new TileGraph(new ArrayList<>(Arrays.asList(main, other)),
            Collections.<String>emptySet());

        // The unpaired-link sweep lives in validatePortals, not the constructor.
        graph.validatePortals();

        assertTrue(hasProblem(graph, TileGraph.WARN_PORTAL_NEVER_PAIRED, true),
            "a reachable unpaired link to a page inside autonomy has to refuse the diagram");
    }

    /**
     * The same link, once its destination page is left out of autonomy, is a warning (OB-150): an arrow to an excluded
     * page cannot be paired, so an error would name a fault whose only remedy is switching the arrow off. Written
     * beside the test above because the two must disagree; a rule that always answers "outside autonomy" would turn
     * OB-150 off without failing anything.
     */
    @Test
    public void testAnUnpairedLinkToAnExcludedPageDoesNotBlock() throws Exception
    {
        LayoutDiagram main = page("main", 6, 6);
        LayoutDiagram other = page("other", 6, 6);

        straight(main, 0, 1);
        linkTo(main, 1, 1, 1);
        straight(main, 2, 1);

        Set<String> excluded = new java.util.LinkedHashSet<>();
        excluded.add("other");

        TileGraph graph = new TileGraph(new ArrayList<>(Arrays.asList(main, other)), excluded);

        graph.validatePortals();

        assertFalse(hasProblem(graph, TileGraph.WARN_PORTAL_NEVER_PAIRED, true),
            "a link to a page left out of autonomy cannot be paired, so refusing the diagram over it "
            + "asks for something the user cannot do");

        assertTrue(hasProblem(graph, TileGraph.WARN_PORTAL_EXCLUDED_PAGE, false),
            "it is still worth saying - it stopped blocking, it did not stop being reported");

        // Its own message, not a quieter copy: warnLinkNeverPaired says to right-click and pair it, which cannot work
        // when the far page is left out of autonomy.
        assertFalse(hasProblem(graph, TileGraph.WARN_PORTAL_NEVER_PAIRED, false),
            "this case has its own message and should not also be reported as an ordinary "
            + "never-paired link");
    }

    // helpers

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
    /**
     * A link tile pointing at the nth page of the layout. {@link #add} leaves the raw destination address at zero,
     * which is a real page, so a link added by that helper points at the first one whether or not anybody meant it to.
     */
    private void linkTo(LayoutDiagram page, int x, int y, int destination) throws IOException
    {
        page.addComponent(componentType.LINK, x, y, 0, 0, 0, destination,
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
     * A pairing whose far end is no longer a link is reported: pairings are stored by coordinate and replayed without
     * asking what is there now, so redrawing the far end as plain track left a mutual pairing every check was happy
     * with while cross-page routes silently disappeared.
     */
    @Test
    public void testAPairingWhoseFarEndIsNoLongerALinkIsReported() throws Exception
    {
        LayoutDiagram page = page("main", 6, 6);

        add(page, componentType.LINK, 1, 1, 0);

        straight(page, 4, 4);

        TileGraph graph = graph(page);

        graph.pairPortals(key("main", 1, 1), key("main", 4, 4));

        List<Problem> found = graph.validatePortals();

        assertFalse(found.isEmpty(),
            "a pairing pointing at something that is not a link has to say so");
    }
}
