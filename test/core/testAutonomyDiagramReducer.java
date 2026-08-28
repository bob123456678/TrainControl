package core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.GraphReducer.ReducedPoint;
import org.traincontrol.automationui.GraphReducer.TileStep;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * The contraction from tiles to the autonomy graph: which Points exist, what connects them, and which
 * of those connections cannot run at the same time.
 *
 * Every test builds its track in memory, so what is being asserted is railway - two sensors with track
 * between them, a switch that fans out, a crossing two routes share - rather than fixture plumbing.
 *
 * @author Adam
 */
public class testAutonomyDiagramReducer
{
    /**
     * A red arrow stops a train LANDING, not passing through (corrected 2026-08-28).
     *
     * The case the two tests beside this one could not see, and the one an independent reviewer found.
     * Both of them bar the DESTINATION - the single arrangement where "cannot stop here" and "cannot
     * come this way" give the same answer - so the walks could enforce the wrong one of the two and
     * stay green.
     *
     * What the build actually does is not in doubt: `AutonomyBuilder` emits the barred copy of a split
     * square as a non-station and says so in its own comment - "the copy still exists and still carries
     * traffic; it is simply not somewhere a train can be sent". A javadoc of mine claimed the opposite,
     * and the walks were written to match the javadoc.
     *
     * The consequence was not cosmetic. The path test drew no route for journeys the railway runs, and
     * the findings reported stations as reaching nothing when they reach plenty - warnings Adam acts on
     * by editing a diagram that was never wrong.
     *
     * MUTATION: refusing a barred arrival at every hop - which is what shipped - fails both halves.
     */
    @Test
    public void testABarredArrivalCanStillBePassedThrough() throws IOException
    {
        // A - B - C again, but this time the bar is in the MIDDLE of the journey.
        LayoutDiagram page = page("main", 8, 3);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);
        straight(page, 4, 1);
        feedback(page, 5, 1, 13);

        GraphReducer reducer = reduce(graph(page), null);

        TileKey a = key("main", 1, 1);
        TileKey b = key("main", 3, 1);
        TileKey c = key("main", 5, 1);

        Set<TileKey> none = Collections.emptySet();

        // B refuses trains arriving from A's side.  A train may not STOP there that way; it may still
        // run through on its way to C.
        Map<TileKey, Set<Side>> barred = new java.util.LinkedHashMap<>();

        barred.put(b, java.util.EnumSet.of(arrivalSideAt(reducer, a, b)));

        assertNotNull(reducer.findPath(a, c, none, none, barred),
            "no route from A to C because the square BETWEEN them refuses arrivals from A's side - but "
            + "the railway runs that train: the build emits the barred copy and carries traffic "
            + "through it, and only refuses to send a train there to stop");

        assertTrue(reducer.reachableTiles(a, none, none, barred).contains(c),
            "C is reported unreachable from A because the square between them is barred, so the "
            + "findings would tell Adam a station reaches nothing when it reaches C perfectly well");

        // And the square itself is still not a DESTINATION by that side, which is the half that was
        // right all along.
        assertFalse(reducer.reachableTiles(a, none, none, barred).contains(b),
            "the barred square is offered as somewhere a train can be sent, which is exactly what the "
            + "red arrow refuses");
    }

    /**
     * A barred arrival is not a route, for either walk (OB-120).
     *
     * Adam: "test a path should respect all red arrow restrictions."
     *
     * The red arrows are barred arrivals - the sides a station refuses to let trains STOP at it by.
     * `AutonomyBuilder` emits the barred copy as a non-station that still carries traffic, so a train
     * may run through such a square and simply may not be sent there. The reducer's walks obeyed
     * neither half, so the editor's path test drew routes into stations that refuse them - the one
     * thing a path test must not do, since its whole purpose is to report what a train would find.
     *
     * This paragraph said the opposite for a day, and the code beneath it was written to match. See
     * `testABarredArrivalCanStillBePassedThrough`.
     *
     * **Both walks, and that is the half worth testing.** `applyTest` says in as many words that it
     * shares its turn sets with the reachability check "so the path test and the findings panel cannot
     * disagree about which way a train may go". Teaching findPath about the arrows and not
     * reachableTiles would have broken that quietly: the test would refuse a run the findings panel
     * went on counting as reachable, and the two screens would contradict each other with no error
     * anywhere.
     *
     * The START is exempt, as it is in the build - a train standing on a square did not arrive there by
     * any side, and refusing it would make a restricted station unable to SEND trains rather than
     * unable to receive them. The last case here is that one.
     *
     * MUTATION: dropping the `refusesArrival` test from either walk fails its half of this.
     */
    @Test
    public void testTheWalksObeyTheRedArrows() throws IOException
    {
        // Three sensors in a row, A - B - C, the same shape the connectivity tests use.
        LayoutDiagram page = page("main", 8, 3);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);
        straight(page, 4, 1);
        feedback(page, 5, 1, 13);

        GraphReducer reducer = reduce(graph(page), null);

        TileKey a = key("main", 1, 1);
        TileKey b = key("main", 3, 1);
        TileKey c = key("main", 5, 1);

        Set<TileKey> none = Collections.emptySet();

        // Nothing barred: the run exists, which is the control - without it a fault that broke the
        // walk entirely would read as this test passing.
        assertNotNull(reducer.findPath(a, c, none, none), "the plain run should exist");

        assertTrue(reducer.reachableTiles(a, none, none).contains(c),
            "the plain run should be reachable too");

        // Now C refuses trains arriving by the side this run reaches it from.
        Map<TileKey, Set<Side>> barred = new java.util.LinkedHashMap<>();

        barred.put(c, java.util.EnumSet.of(arrivalSideAt(reducer, b, c)));

        assertNull(reducer.findPath(a, c, none, none, barred),
            "the path test found a run into a station that refuses arrivals from that side - a route "
            + "the railway will never offer, drawn on the diagram as though it would");

        assertFalse(reducer.reachableTiles(a, none, none, barred).contains(c),
            "the reachability walk still counts a barred station as reachable, so the findings panel "
            + "and the path test now disagree about the same run");

        // And the start is exempt: A may still SEND trains even if A refuses arrivals.
        Map<TileKey, Set<Side>> barredStart = new java.util.LinkedHashMap<>();

        barredStart.put(a, java.util.EnumSet.allOf(Side.class));

        assertNotNull(reducer.findPath(a, c, none, none, barredStart),
            "a station that refuses every arrival can no longer send a train anywhere. A train "
            + "standing there did not arrive by any side, and the build exempts the start for exactly "
            + "this reason");
    }

    /**
     * The side a run from one square arrives at the next by.
     *
     * Read off the reducer rather than assumed, so this test does not have to know which way the
     * fixture happens to lay its track out.
     */
    private Side arrivalSideAt(GraphReducer reducer, TileKey from, TileKey to)
    {
        for (ReducedEdge edge : reducer.getEdges())
        {
            if (edge.getStart().equals(from) && edge.getEnd().equals(to)) return edge.getEntrySide();
        }

        throw new IllegalStateException("no edge from " + from + " to " + to + " in the fixture");
    }

    /**
     * Every feedback tile becomes a Point without anyone asking, and a run of plain track between two of
     * them collapses to exactly one edge each way.
     */
    @Test
    public void testARunOfTrackBetweenTwoSensorsBecomesOneEdgeEachWay() throws IOException
    {
        LayoutDiagram page = page("main", 8, 3);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        straight(page, 3, 1);
        straight(page, 4, 1);
        feedback(page, 5, 1, 12);

        GraphReducer reducer = reduce(graph(page), null);

        assertEquals(reducer.getPoints().size(), 2, "both sensors should be Points");

        List<ReducedEdge> between = edgesBetween(reducer, key("main", 1, 1), key("main", 5, 1));
        assertEquals(between.size(), 1, "one edge in each direction, not one per tile");
        assertEquals(between.get(0).getPath().size(), 3, "the three plain tiles collapse into it");

        assertEquals(edgesBetween(reducer, key("main", 5, 1), key("main", 1, 1)).size(), 1);
        assertEquals(reducer.getEdges().size(), 2);
    }

    /**
     * Adjacent sensors still make an edge - one with no track in between.
     */
    @Test
    public void testAdjacentSensorsConnectDirectly() throws IOException
    {
        LayoutDiagram page = page("main", 5, 3);
        feedback(page, 1, 1, 11);
        feedback(page, 2, 1, 12);

        GraphReducer reducer = reduce(graph(page), null);

        List<ReducedEdge> between = edgesBetween(reducer, key("main", 1, 1), key("main", 2, 1));
        assertEquals(between.size(), 1);
        assertTrue(between.get(0).getPath().isEmpty());
        assertEquals(between.get(0).getLength(), 0);
    }

    /**
     * A switch is a branch point, not a node.  Walking through one forks the path, and each fork becomes
     * its own edge carrying the accessory setting that selects it.
     */
    @Test
    public void testASwitchForksIntoOneEdgePerBranchWithItsCommand() throws IOException
    {
        // sensor - switch - two sensors, one straight ahead and one to the west
        // SWITCH_LEFT at orientation 0 has its toe south, straight ahead north, and its branch west
        LayoutDiagram page = page("main", 6, 6);
        feedbackNS(page, 2, 3, 11);
        add(page, componentType.SWITCH_LEFT, 2, 2, 0, 7);
        wire(page, 2, 2, 7, Accessory.accessoryType.SWITCH);
        feedbackNS(page, 2, 1, 12);
        feedback(page, 1, 2, 13);

        GraphReducer reducer = reduce(graph(page), null);

        assertEquals(reducer.getPoints().size(), 3);

        // from the toe both branches are reachable, as separate edges
        List<ReducedEdge> straightOn = edgesBetween(reducer, key("main", 2, 3), key("main", 2, 1));
        List<ReducedEdge> diverging = edgesBetween(reducer, key("main", 2, 3), key("main", 1, 2));

        assertEquals(straightOn.size(), 1, "the straight route should be an edge");
        assertEquals(diverging.size(), 1, "the diverging route should be its own edge");

        // and each carries the position it needs
        assertEquals(straightOn.get(0).getCommands().size(), 1);
        assertEquals(diverging.get(0).getCommands().size(), 1);
        assertNotEquals(
            straightOn.get(0).getCommands().values().iterator().next(),
            diverging.get(0).getCommands().values().iterator().next(),
            "the two branches cannot want the same switch position");

        // the switch itself is not a Point
        assertFalse(reducer.getPoints().containsKey(key("main", 2, 2)));
    }

    /**
     * Crossing a signal requires it green, gathered exactly like a switch position - which is what lets
     * one rule cover both.
     */
    @Test
    public void testCrossingASignalCommandsItGreen() throws IOException
    {
        LayoutDiagram page = page("main", 6, 3);
        feedback(page, 1, 1, 11);
        add(page, componentType.SIGNAL, 2, 1, 0, 21);
        wire(page, 2, 1, 21, Accessory.accessoryType.SIGNAL);
        feedback(page, 3, 1, 12);

        GraphReducer reducer = reduce(graph(page), null);

        ReducedEdge edge = edgesBetween(reducer, key("main", 1, 1), key("main", 3, 1)).get(0);

        assertEquals(edge.getCommands().size(), 1, "the signal on the path should be commanded");
    }

    /**
     * Edge length is the sum of the tiles it covers, endpoints excluded, and zero until the user assigns
     * lengths - so train-length accounting stays inert on an untouched diagram.
     */
    @Test
    public void testLengthIsTheSumOfTheTilesCovered() throws IOException
    {
        LayoutDiagram page = page("main", 8, 3);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        straight(page, 3, 1);
        feedback(page, 4, 1, 12);

        // nothing authored: no lengths anywhere
        assertEquals(edgesBetween(reduce(graph(page), null),
            key("main", 1, 1), key("main", 4, 1)).get(0).getLength(), 0);

        Map<TileKey, Integer> lengths = new HashMap<>();
        lengths.put(key("main", 2, 1), 40);
        lengths.put(key("main", 3, 1), 2);
        // a length on an endpoint must not count - the Point is not track between the two
        lengths.put(key("main", 1, 1), 999);

        GraphReducer reducer = reduce(graph(page), authored(lengths, null, null));

        assertEquals(edgesBetween(reducer, key("main", 1, 1), key("main", 4, 1)).get(0).getLength(), 42);
    }

    /**
     * Two edges that share a tile are the same piece of railway and must not run at once.
     */
    @Test
    public void testEdgesSharingATileAreLockedAgainstEachOther() throws IOException
    {
        // two routes meeting at a crossing: north-south and east-west
        LayoutDiagram page = page("main", 6, 6);
        feedbackNS(page, 2, 1, 11);
        feedbackNS(page, 2, 3, 12);
        add(page, componentType.CROSSING, 2, 2, 0);
        feedback(page, 1, 2, 13);
        feedback(page, 3, 2, 14);

        GraphReducer reducer = reduce(graph(page), null);

        ReducedEdge northSouth = edgesBetween(reducer, key("main", 2, 1), key("main", 2, 3)).get(0);
        ReducedEdge eastWest = edgesBetween(reducer, key("main", 1, 2), key("main", 3, 2)).get(0);

        assertTrue(locked(reducer, northSouth, eastWest),
            "two routes over one crossing must be mutually exclusive");
        assertTrue(locked(reducer, eastWest, northSouth), "and the lock must be mutual");
    }

    /**
     * An overpass is the one shared tile that is not a conflict: the two tracks are at different heights.
     * Crossing it by the same route still is.
     */
    @Test
    public void testAnOverpassDoesNotLockItsTwoLevelsAgainstEachOther() throws IOException
    {
        LayoutDiagram page = page("main", 6, 6);
        feedbackNS(page, 2, 1, 11);
        feedbackNS(page, 2, 3, 12);
        add(page, componentType.OVERPASS, 2, 2, 0);
        feedback(page, 1, 2, 13);
        feedback(page, 3, 2, 14);

        GraphReducer reducer = reduce(graph(page), null);

        ReducedEdge northSouth = edgesBetween(reducer, key("main", 2, 1), key("main", 2, 3)).get(0);
        ReducedEdge eastWest = edgesBetween(reducer, key("main", 1, 2), key("main", 3, 2)).get(0);

        assertFalse(locked(reducer, northSouth, eastWest),
            "an overpass carries one track above the other, so these do not conflict");
    }

    /**
     * The two directions of one run are the same track, not rivals for it.
     */
    @Test
    public void testOppositeDirectionsOfOneRunAreNotLockedAgainstEachOther() throws IOException
    {
        LayoutDiagram page = page("main", 6, 3);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);

        GraphReducer reducer = reduce(graph(page), null);

        ReducedEdge forward = edgesBetween(reducer, key("main", 1, 1), key("main", 3, 1)).get(0);
        ReducedEdge backward = edgesBetween(reducer, key("main", 3, 1), key("main", 1, 1)).get(0);

        assertFalse(locked(reducer, forward, backward),
            "an edge and its reverse are one piece of track, not two claims on it");
    }

    /**
     * A one-way tile removes the edge that would run against it, and only that one.
     */
    @Test
    public void testAOneWayTileSuppressesExactlyTheOpposingEdge() throws IOException
    {
        LayoutDiagram page = page("main", 6, 3);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);

        TileGraph graph = graph(page);
        TileKey middle = key("main", 2, 1);

        // allow travel toward the east only
        RouteId only = graph.getRoutes(middle).keySet().iterator().next();
        Route route = graph.getRoutes(middle).get(only);
        graph.setDirection(middle, only,
            route.getA() == Side.E ? Direction.TOWARD_A : Direction.TOWARD_B);

        GraphReducer reducer = reduce(graph, null);

        assertEquals(edgesBetween(reducer, key("main", 1, 1), key("main", 3, 1)).size(), 1,
            "the permitted direction should survive");
        assertEquals(edgesBetween(reducer, key("main", 3, 1), key("main", 1, 1)).size(), 0,
            "the opposing direction should be gone");
    }

    /**
     * The connectivity test: a shortest run comes back in order, and it respects directions - the same
     * pair of Points can be reachable one way and not the other on the same track.
     */
    @Test
    public void testFindPathFollowsDirectionsAndReturnsAShortestRun() throws IOException
    {
        // three sensors in a row: A - B - C, with the track between B and C one-way toward C
        LayoutDiagram page = page("main", 8, 3);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);
        straight(page, 4, 1);
        feedback(page, 5, 1, 13);

        TileGraph graph = graph(page);
        TileKey oneWay = key("main", 4, 1);

        RouteId only = graph.getRoutes(oneWay).keySet().iterator().next();
        Route route = graph.getRoutes(oneWay).get(only);
        graph.setDirection(oneWay, only,
            route.getA() == Side.E ? Direction.TOWARD_A : Direction.TOWARD_B);

        GraphReducer reducer = reduce(graph, null);

        TileKey a = key("main", 1, 1);
        TileKey b = key("main", 3, 1);
        TileKey c = key("main", 5, 1);

        // A to C crosses both edges, in order
        List<ReducedEdge> run = reducer.findPath(a, c);
        assertNotNull(run, "A should reach C");
        assertEquals(run.size(), 2, "two edges: A-B then B-C");
        assertEquals(run.get(0).getStart(), a);
        assertEquals(run.get(0).getEnd(), b);
        assertEquals(run.get(1).getStart(), b);
        assertEquals(run.get(1).getEnd(), c);

        // C cannot get back past the one-way tile, to B or to A
        assertNull(reducer.findPath(c, a), "the one-way track should block the return");
        assertNull(reducer.findPath(c, b), "even the single hop against it");

        // but B still reaches A, because only the B-C stretch was restricted
        assertNotNull(reducer.findPath(b, a));

        // a Point trivially reaches itself, with an empty run rather than a null one
        assertEquals(reducer.findPath(a, a).size(), 0);

        // and a tile that is not a Point is a null, not an exception
        assertNull(reducer.findPath(a, key("main", 2, 1)));
    }

    /**
     * Two ways round between the same pair of sensors must not become two edges.
     *
     * An edge's identity in the autonomy model is its pair of Point NAMES, so a passing loop used to
     * emit two edges called the same thing; createEdge throws on the second and parseAuto invalidates
     * the WHOLE configuration.  A user with a passing loop could not load autonomy at all, and the
     * message named only JSON - nothing pointed back at the diagram.
     */
    @Test
    public void testTwoRoutesBetweenTheSameSensorsBecomeOneEdge() throws IOException
    {
        // A - switch - {straight on, over the top} - switch - B, i.e. an ordinary passing loop.
        //
        //        (2,0)-(3,0)-(4,0)-(5,0)      the loop, over the top
        //       /                              //  A(0,1)-(1,1)-SW-(3,1)-(4,1)-SW-B(6,1)
        //
        // Every tile is stated by the two sides it has to join.  A CURVE is E-S at orientation 0 and
        // ports rotate by (4 - orientation) quarter turns clockwise, so 1 is N-E, 2 is N-W, 3 is S-W.
        // SWITCH_LEFT is straight N-S with its turn S-W at orientation 0, which makes orientation 3
        // straight E-W with the turn W-N - toe west, so a train from A meets it facing the toe and may
        // diverge.  SWITCH_RIGHT is the mirror, and orientation 1 puts its toe east.
        //
        // This was wrong in three places before, which is what the guard below now catches: the loop
        // joined up nowhere, no edges came out, and the assertions ran zero times for years.
        LayoutDiagram page = page("main", 10, 5);

        feedback(page, 0, 1, 11);
        straight(page, 1, 1);

        add(page, componentType.SWITCH_LEFT, 2, 1, 3, 40);   // E-W, and W-N into the loop
        wire(page, 2, 1, 40, Accessory.accessoryType.SWITCH);

        straight(page, 3, 1);
        straight(page, 4, 1);

        add(page, componentType.SWITCH_RIGHT, 5, 1, 1, 41);  // W-E, and E-N out of the loop
        wire(page, 5, 1, 41, Accessory.accessoryType.SWITCH);

        feedback(page, 6, 1, 12);

        add(page, componentType.CURVE, 2, 0, 0);   // E and S
        straight(page, 3, 0);
        straight(page, 4, 0);
        add(page, componentType.CURVE, 5, 0, 3);   // S and W

        // Both switches open both ways.  They default to base-to-forks - out of the toe only - and the
        // toes here face each other, so left alone the far switch refuses every trailing move and the
        // loop carries nothing.  What is under test is the parallel route, not the default.
        TileGraph graph = graph(page);

        for (TileKey tile : graph.getTiles().keySet())
        {
            for (TileGraph.RouteId routeId : graph.getRoutes(tile).keySet())
            {
                graph.setDirection(tile, routeId, TileGraph.Direction.BOTH);
            }
        }

        GraphReducer reducer = reduce(graph, null);

        // The fixture has to have built the thing this test is about before any of it means anything.
        //
        // "Whatever the geometry turns out to be" is what this used to say, and it was a licence for the
        // geometry to turn out to be nothing at all: two curves were rotated off the track they were
        // meant to join, the passing loop joined up nowhere, no edges were emitted, and the loop below
        // ran zero times.  The test passed for years without once exercising the parallel-route rule it
        // is named after.
        assertFalse(reducer.getEdges().isEmpty(),
            "the passing loop produced no edges at all, so nothing below this line is being tested");

        // whatever the geometry turns out to be, the invariant is the same: at most one edge per
        // ordered pair, because that is all the model can hold
        Set<String> pairs = new HashSet<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            String pair = edge.getStart() + " -> " + edge.getEnd();

            assertTrue(pairs.add(pair), "two edges emitted for " + pair);

            assertNotEquals(edge.getStart(), edge.getEnd(),
                "an edge from a Point to itself is a shape the model has no room for");
        }
    }

    /**
     * A sensor with nothing next to it is counted and left out.  Emitting it would only produce an
     * unreachable node that fails validation later, with nothing to say about why.
     */
    @Test
    public void testIsolatedSensorsAreSkippedAndCounted() throws IOException
    {
        LayoutDiagram page = page("main", 8, 5);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);

        // marooned in a blank area
        feedback(page, 6, 3, 99);

        GraphReducer reducer = reduce(graph(page), null);

        assertEquals(reducer.getPoints().size(), 2);
        assertFalse(reducer.getPoints().containsKey(key("main", 6, 3)));
        assertEquals(reducer.getIsolatedFeedbackTiles(), 1);
    }

    /**
     * Points are named from their coordinate unless the user named them - an s88 address will not do,
     * since a station and its approach guards legitimately share one sensor.
     */
    @Test
    public void testPointsAreNamedAndStationsAreDesignated() throws IOException
    {
        LayoutDiagram page = page("main", 6, 3);
        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 11);

        Map<TileKey, String> names = new HashMap<>();
        names.put(key("main", 1, 1), "Track 14 entrance");

        Set<TileKey> stations = new HashSet<>();
        stations.add(key("main", 1, 1));

        GraphReducer reducer = reduce(graph(page), authored(null, names, stations));

        ReducedPoint named = reducer.getPoints().get(key("main", 1, 1));
        ReducedPoint generated = reducer.getPoints().get(key("main", 3, 1));

        assertEquals(named.getName(), "Track 14 entrance");
        assertTrue(named.isStation());

        assertNotNull(generated.getName());
        assertFalse(generated.isStation(), "a Point is not a station until the user says so");

        // a Point's s88 is the RAW address, the number feedback is registered under - not the halved
        // logical address CS2File derives for accessories
        assertEquals(named.getS88(), 11);
        assertEquals(generated.getS88(), 11);

        // the two share an s88 and must still be distinguishable
        assertNotEquals(named.getName(), generated.getName(),
            "two sensors sharing an address still need distinct names");
    }

    /**
     * A defective turnout may only be trailed through, so the edge it lies on exists in one direction
     * only - and it commands nothing, because there is no address to command.
     */
    @Test
    public void testDefectiveTurnoutsProduceOneDirectionalEdgesWithNoCommands() throws IOException
    {
        // sensor north of the turnout, sensor south of its toe
        LayoutDiagram page = page("main", 6, 6);
        feedbackNS(page, 2, 1, 11);
        add(page, componentType.CUSTOM_PERM_LEFT, 2, 2, 0);
        feedbackNS(page, 2, 3, 12);

        GraphReducer reducer = reduce(graph(page), null);

        assertEquals(edgesBetween(reducer, key("main", 2, 1), key("main", 2, 3)).size(), 1,
            "the trailing direction should exist");
        assertEquals(edgesBetween(reducer, key("main", 2, 3), key("main", 2, 1)).size(), 0,
            "the facing direction should not");

        assertTrue(edgesBetween(reducer, key("main", 2, 1), key("main", 2, 3))
            .get(0).getCommands().isEmpty(), "there is no address to command");
    }

    /**
     * A switch drawn without an address cannot be thrown, so autonomy must not route over it trusting it
     * to already be lying the right way.  It is reported instead - the same danger CUSTOM_PERM_* exists
     * to describe, except here nobody said so.
     */
    @Test
    public void testASwitchWithNoAddressIsRefusedRatherThanAssumed() throws IOException
    {
        LayoutDiagram page = page("main", 6, 6);
        feedbackNS(page, 2, 3, 11);
        // no wire() call: the switch has no accessory behind it
        add(page, componentType.SWITCH_LEFT, 2, 2, 0, 7);
        feedbackNS(page, 2, 1, 12);

        GraphReducer reducer = reduce(graph(page), null);

        assertTrue(reducer.getEdges().isEmpty(),
            "no edge may cross a switch that cannot be commanded");

        // and the diagram is refused outright: an autonomy diagram should not contain unmapped switches
        TileGraph graph = graph(page);

        boolean reported = false;

        for (TileGraph.Problem p : graph.getProblems())
        {
            if (TileGraph.ERROR_NO_ADDRESS.equals(p.getMessageKey()) && p.isBlocking()) reported = true;
        }

        assertTrue(reported, "an unaddressed switch should block the build, not merely be skipped");
        assertTrue(graph.hasBlockingProblems());
    }

    /**
     * The same diagram must reduce to the same graph twice - otherwise two builds cannot be compared,
     * and the ground truth diff against the existing layout is meaningless.
     */
    @Test
    public void testReductionIsDeterministic() throws IOException
    {
        LayoutDiagram page = page("main", 8, 6);
        feedbackNS(page, 2, 3, 11);
        add(page, componentType.SWITCH_LEFT, 2, 2, 0, 7);
        wire(page, 2, 2, 7, Accessory.accessoryType.SWITCH);
        feedbackNS(page, 2, 1, 12);
        feedback(page, 1, 2, 13);

        GraphReducer first = reduce(graph(page), null);
        GraphReducer second = reduce(graph(page), null);

        assertEquals(describe(first), describe(second));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private LayoutDiagram page(String name, int sx, int sy)
    {
        return new LayoutDiagram(name, sx, sy, null, null);
    }

    private void add(LayoutDiagram page, componentType type, int x, int y, int orientation)
        throws IOException
    {
        add(page, type, x, y, orientation, 0);
    }

    private void add(LayoutDiagram page, componentType type, int x, int y, int orientation, int address)
        throws IOException
    {
        page.addComponent(type, x, y, orientation, 0, address, address, accessoryDecoderType.MM2, null);
    }

    private void straight(LayoutDiagram page, int x, int y) throws IOException
    {
        add(page, componentType.STRAIGHT, x, y, 0);
    }

    /**
     * A feedback tile lying east-west, which is how FEEDBACK is drawn at orientation 0.
     */
    /**
     * A feedback tile lying east-west, which is how FEEDBACK is drawn at orientation 0.
     *
     * The address given is the RAW one, as it appears in the CS2 file - which is what a Point's s88
     * means.  The logical address is deliberately different here (CS2File halves the raw value), so a
     * Point built from the wrong one shows up immediately rather than agreeing by coincidence.
     */
    private void feedback(LayoutDiagram page, int x, int y, int rawAddress) throws IOException
    {
        page.addComponent(componentType.FEEDBACK, x, y, 0, 0, rawAddress / 2, rawAddress,
            accessoryDecoderType.MM2, null);
    }

    /**
     * A feedback tile lying north-south.  Orientation matters as much here as it does on the diagram: a
     * horizontal sensor placed above a vertical switch has no port facing it and simply does not connect.
     */
    private void feedbackNS(LayoutDiagram page, int x, int y, int rawAddress) throws IOException
    {
        page.addComponent(componentType.FEEDBACK, x, y, 1, 0, rawAddress / 2, rawAddress,
            accessoryDecoderType.MM2, null);
    }

    /**
     * Wires an accessory onto a tile, as parsing a real layout does.  Without one a switch or signal has
     * no address to command, and autonomy refuses to route over it.
     */
    private void wire(LayoutDiagram page, int x, int y, int address, Accessory.accessoryType type)
    {
        page.getComponent(x, y).setAccessory(
            new MarklinAccessory(null, address, type, accessoryDecoderType.MM2,
                (type == Accessory.accessoryType.SIGNAL ? "Signal " : "Switch ") + address, false, 0));
    }

    private TileGraph graph(LayoutDiagram... pages)
    {
        return new TileGraph(new ArrayList<>(Arrays.asList(pages)), Collections.<String>emptySet());
    }

    private GraphReducer reduce(TileGraph graph, GraphReducer.Authored authored)
    {
        GraphReducer reducer = new GraphReducer(graph, authored);
        reducer.reduce();
        return reducer;
    }

    private GraphReducer.Authored authored(final Map<TileKey, Integer> lengths,
        final Map<TileKey, String> names, final Set<TileKey> stations)
    {
        return new GraphReducer.Authored()
        {
            @Override
            public String getPointName(TileKey tile)
            {
                return names == null ? null : names.get(tile);
            }

            @Override
            public boolean isStation(TileKey tile)
            {
                return stations != null && stations.contains(tile);
            }

            @Override
            public int getTileLength(TileKey tile)
            {
                if (lengths == null) return 0;

                Integer value = lengths.get(tile);

                return value == null ? 0 : value;
            }
        };
    }

    private TileKey key(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }

    private List<ReducedEdge> edgesBetween(GraphReducer reducer, TileKey start, TileKey end)
    {
        List<ReducedEdge> out = new ArrayList<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            if (edge.getStart().equals(start) && edge.getEnd().equals(end)) out.add(edge);
        }

        return out;
    }

    private boolean locked(GraphReducer reducer, ReducedEdge a, ReducedEdge b)
    {
        Set<ReducedEdge> set = reducer.getLocks().get(a);

        return set != null && set.contains(b);
    }

    /**
     * A stable textual form of the whole reduction, for comparing two runs.
     */
    private String describe(GraphReducer reducer)
    {
        StringBuilder out = new StringBuilder();

        for (ReducedPoint p : reducer.getPoints().values())
        {
            out.append("P ").append(p.getName()).append(" s88=").append(p.getS88()).append("\n");
        }

        for (ReducedEdge e : reducer.getEdges())
        {
            out.append("E ").append(e.getStart()).append(" -> ").append(e.getEnd())
               .append(" len=").append(e.getLength())
               .append(" cmd=").append(e.getCommands())
               .append(" path=");

            for (TileStep step : e.getPath())
            {
                out.append(step).append(" ");
            }

            out.append("\n");
        }

        return out.toString();
    }

    /**
     * A run that crosses one square twice, on the square's two separate tracks, is still a run.
     *
     * The walk used to mark a square visited by its coordinates alone, so the second crossing looked
     * exactly like a circle and the walk stopped - the run vanished, and nothing was reported about it,
     * which is the worst of the three things that could have happened.  A crossing carries two routes
     * that never meet; passing over one and coming back through the other is ordinary railway.
     */
    @Test
    public void testARunMayCrossTheSameSquareTwice() throws Exception
    {
        //        F1 -- x -- x
        //                   |
        //   x -- x -- CROSS -- x -- F2         the crossing is entered W-E, then again N-S
        //             |
        //             x
        //
        // Built as a loop of straight track through one CROSSING tile on both of its routes.
        // A CURVE is E-S at orientation 0, and ports rotate by (4 - orientation) quarter turns
        // clockwise - so orientation 1 is N-E, 2 is N-W and 3 is S-W.  Every curve below is stated by
        // the two sides it has to join, with the orientation that gives them; get one wrong and the
        // track simply does not meet, and this test would pass or fail for a reason of its own.
        LayoutDiagram page = page("main", 8, 8);

        feedback(page, 0, 3, 11);
        straight(page, 1, 3);
        straight(page, 2, 3);

        // the crossing: N-S and E-W, two routes with no connection between them
        add(page, componentType.CROSSING, 3, 3, 0);

        straight(page, 4, 3);
        straight(page, 5, 3);

        // up, over and back down into the crossing's OTHER route
        curve(page, 6, 3, 2);      // N and W
        straightNS(page, 6, 2);
        curve(page, 6, 1, 3);      // S and W
        straight(page, 5, 1);
        straight(page, 4, 1);
        curve(page, 3, 1, 0);      // E and S
        straightNS(page, 3, 2);

        // and out of the crossing's south side to the far sensor
        straightNS(page, 3, 4);
        feedbackNS(page, 3, 5, 12);

        // Switches default to base-to-forks, which is an unrelated variable here - so every route is
        // opened both ways before the reduction.
        TileGraph graph = graph(page);

        for (TileKey tile : graph.getTiles().keySet())
        {
            for (RouteId routeId : graph.getRoutes(tile).keySet())
            {
                graph.setDirection(tile, routeId, Direction.BOTH);
            }
        }

        GraphReducer reducer = reduce(graph, null);

        Set<Integer> reached = new LinkedHashSet<>();

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            GraphReducer.ReducedPoint start = reducer.getPoints().get(edge.getStart());
            GraphReducer.ReducedPoint end = reducer.getPoints().get(edge.getEnd());

            if (start != null && end != null && start.getS88() == 11) reached.add(end.getS88());
        }

        assertTrue(reached.contains(12),
            "the run from 11 to 12 uses the crossing twice, which is what a crossing is for - reached "
            + reached);
    }

    /**
     * A curve at the given orientation.  Ports rotate by (4 - orientation) quarter turns clockwise, so
     * these are stated by orientation and checked against the connections the test then asserts.
     */
    private void curve(LayoutDiagram page, int x, int y, int orientation) throws IOException
    {
        add(page, componentType.CURVE, x, y, orientation);
    }

    /**
     * A straight running north to south.  Orientation 0 is east-west, and one rotation turns it.
     */
    private void straightNS(LayoutDiagram page, int x, int y) throws IOException
    {
        add(page, componentType.STRAIGHT, x, y, 1);
    }

    /**
     * Reachability does not jump between the two arms of a double curve.
     *
     * A FEEDBACK_DOUBLE_CURVE at orientation 0 is two independent curves - N-W and E-S - in one square.
     * A sensor on its N side and one on its E side are on DIFFERENT arms, so no train can run from one
     * to the other through the square: arriving from N, the only way on is W.
     *
     * The plain tile adjacency the station checks used to walk would say they connect (the square is
     * one node with edges to both), which is the over-report that let checkStations miss a
     * STATION_UNREACHABLE the runtime would show.  reachableTiles is the split-aware answer Layout.bfs
     * actually has, so it must exclude the far sensor.
     */
    @Test
    public void testReachableTilesDoesNotCrossADoubleCurve() throws IOException
    {
        //        A (2,1), north of the double curve, on its N arm
        //        |
        //   [DBLCURVE (2,2)] -- B (3,2), east of it, on its E arm
        LayoutDiagram page = page("main", 6, 6);

        feedbackNS(page, 2, 1, 11);                                          // A -> the DC's N side
        add(page, componentType.FEEDBACK_DOUBLE_CURVE, 2, 2, 0, 22);          // the through-point
        feedback(page, 3, 2, 12);                                            // B -> the DC's E side

        TileGraph graph = graph(page);

        for (TileKey tile : graph.getTiles().keySet())
        {
            for (RouteId routeId : graph.getRoutes(tile).keySet())
            {
                graph.setDirection(tile, routeId, Direction.BOTH);
            }
        }

        GraphReducer reducer = reduce(graph, null);

        TileKey a  = key("main", 2, 1);
        TileKey dc = key("main", 2, 2);
        TileKey b  = key("main", 3, 2);

        Set<TileKey> none = Collections.emptySet();
        Set<TileKey> from = reducer.reachableTiles(a, none, none);

        assertTrue(from.contains(a), "a Point reaches itself");
        assertTrue(from.contains(dc), "A reaches the double curve on its own arm");
        assertFalse(from.contains(b),
            "A must NOT reach B: that jumps from the N-W arm to the E-S arm in mid-square");

        // and the split-aware answer agrees with findPath, which the editor's path test already uses
        assertNull(reducer.findPath(a, b), "findPath and reachableTiles must agree on the impossible run");
        assertNotNull(reducer.findPath(a, dc), "and on the possible one");
    }

    /**
     * A length set on a station counts toward the edge that arrives there.
     *
     * The sum used to cover only the track strictly BETWEEN two sensors, so a user who put lengths on
     * their platforms - which is where the length of a train matters, and the first place anybody would
     * put them - saw every edge come out as zero.  The tile a train ARRIVES on is part of the journey to
     * it, and counting the end of each edge gives every tile along a route exactly once.
     */
    @Test
    public void testALengthOnAStationCountsTowardTheEdgeIntoIt() throws IOException
    {
        LayoutDiagram page = page("main", 6, 4);

        feedback(page, 1, 1, 11);
        straight(page, 2, 1);
        feedback(page, 3, 1, 12);

        final TileKey middle = key("main", 2, 1);
        final TileKey end = key("main", 3, 1);

        java.util.Map<TileKey, Integer> lengths = new java.util.LinkedHashMap<>();
        lengths.put(middle, 2);
        lengths.put(end, 5);

        GraphReducer reducer = reduce(graph(page), authored(lengths, null, null));

        List<ReducedEdge> run = edgesBetween(reducer, key("main", 1, 1), end);

        assertFalse(run.isEmpty(), "the two sensors should be joined");

        assertEquals(run.get(0).getLength(), 7,
            "2 for the track between them and 5 for the platform arrived at");
    }

}
