package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import static org.traincontrol.marklin.MarklinControlStation.init;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests for Layout.bfs, the autonomy path finder.
 *
 * Written to cover the change of its "visited" collection from a LinkedList to a HashSet.  That swap is
 * only safe because Point decides equality by name and derives hashCode from the same field, so
 * membership is unchanged and only the lookup cost differs - but the far more important property to pin
 * is the one that was deliberately NOT changed at the same time.
 *
 * bfs marks a point visited when it is DEQUEUED, not when it is enqueued.  Marking on enqueue is the
 * usual BFS refinement and stops a point being queued more than once, but it would break this method:
 * every caller passes excludePaths, and finding an allowed alternative depends on being able to reach
 * one point by several different routes.  testExcludedPathFallsBackToAnAlternativeViaASharedPoint below
 * fails if anyone makes that change.
 *
 * NOTE FOR ANYONE ADDING TO THIS FILE: bfs is not deterministic.  Layout.getNeighbors ends with
 * Collections.shuffle, so among several equally short routes the one returned varies per call.  An
 * assertion on an exact route is therefore only safe where the shortest route is unique - every such
 * assertion below has been checked against that.  Anything else must assert on length, on whether a
 * route was found at all, or over enough repetitions to cover the possibilities.
 */
public class testLayoutBfs
{
    private static MarklinControlStation model;

    /**
     * Point's constructor rejects a destination that has no s88 feedback, so every destination created
     * here needs one.  bfs never reads the feedback - it only asks isDestination() - so a single shared
     * one serves every destination in this class.
     */
    private static String destinationS88;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();

        MarklinFeedback feedback = model.newFeedback(47000, null);

        // Left clear, matching the convention in testAutonomyPathValidation
        model.setFeedbackState(feedback.getName(), false);

        destinationS88 = feedback.getName();
    }

    /**
     * Builds a layout from edge specifications written as "Start>End".  Points are created implicitly,
     * and any point listed in destinations is marked as one - bfs refuses an end point that is not.
     */
    private Layout graph(List<String> destinations, String... edgeSpecs) throws Exception
    {
        Layout layout = new Layout(model);

        // LinkedHashSet so points are created in a stable, readable order
        Set<String> points = new LinkedHashSet<>();

        for (String spec : edgeSpecs)
        {
            points.addAll(Arrays.asList(spec.split(">")));
        }

        for (String name : points)
        {
            boolean destination = destinations.contains(name);

            layout.createPoint(name, destination, destination ? destinationS88 : null);
        }

        for (String spec : edgeSpecs)
        {
            String[] ends = spec.split(">");
            layout.createEdge(ends[0], ends[1]);
        }

        return layout;
    }

    /**
     * Renders a path as "Start>Next>...>End" so failures name the route rather than an object list.
     */
    private static String render(List<Edge> path)
    {
        if (path == null)
        {
            return null;
        }

        StringBuilder out = new StringBuilder(path.get(0).getStart().getName());

        for (Edge e : path)
        {
            out.append(">").append(e.getEnd().getName());
        }

        return out.toString();
    }

    /**
     * A returned path must actually be walkable: each edge starting where the previous one ended, the
     * first starting at start and the last ending at end.
     */
    private static void assertWalkable(List<Edge> path, String start, String end)
    {
        assertNotNull(path, "expected a path from " + start + " to " + end);
        assertFalse(path.isEmpty(), "a path must contain at least one edge");

        assertEquals(path.get(0).getStart().getName(), start, "path must begin at " + start);
        assertEquals(path.get(path.size() - 1).getEnd().getName(), end, "path must finish at " + end);

        for (int i = 1; i < path.size(); i++)
        {
            assertEquals(path.get(i).getStart().getName(), path.get(i - 1).getEnd().getName(),
                "edge " + i + " must start where edge " + (i - 1) + " ended, in " + render(path));
        }
    }

    @Test
    public void testDirectEdge() throws Exception
    {
        Layout l = graph(Arrays.asList("B"), "A>B");

        List<Edge> path = l.bfs(l.getPoint("A"), l.getPoint("B"), null);

        assertWalkable(path, "A", "B");
        assertEquals(render(path), "A>B");
    }

    /**
     * Breadth first means the one-edge route wins over the three-edge one, whichever order the edges
     * were declared in.
     */
    @Test
    public void testReturnsTheShortestPath() throws Exception
    {
        Layout l = graph(Arrays.asList("T"), "S>A", "A>B", "B>T", "S>T");

        List<Edge> path = l.bfs(l.getPoint("S"), l.getPoint("T"), null);

        assertWalkable(path, "S", "T");
        assertEquals(render(path), "S>T", "the single-edge route is shorter");
    }

    /**
     * The property that mark-on-dequeue exists for.
     *
     * Two routes converge on a shared midpoint M before continuing to T.  Excluding the first route
     * found must yield the other one.  With visited marked on ENQUEUE, M would be claimed by whichever
     * route reached it first and the second would never be queued, so this returns null instead - which
     * is why that optimisation must not be applied here.
     *
     * Repeated deliberately.  getNeighbors shuffles, so a single attempt would catch mark-on-enqueue
     * only about half the time - measured at 247 of 500 runs - because the shuffle sometimes hands the
     * search the alternative route first, before it can be pruned.  Mark-on-dequeue succeeds on every
     * one of 500 runs, so requiring success twenty times in a row costs nothing here and turns a coin
     * toss into a near-certain catch.
     */
    @Test(timeOut = 30000)
    public void testExcludedPathFallsBackToAnAlternativeViaASharedPoint() throws Exception
    {
        Layout l = graph(Arrays.asList("T"), "S>A", "S>B", "A>M", "B>M", "M>T");

        Point s = l.getPoint("S");
        Point t = l.getPoint("T");

        for (int attempt = 0; attempt < 20; attempt++)
        {
            List<Edge> first = l.bfs(s, t, null);

            assertWalkable(first, "S", "T");
            assertEquals(first.size(), 3);

            List<List<Edge>> exclude = new ArrayList<>();
            exclude.add(first);

            List<Edge> second = l.bfs(s, t, exclude);

            assertNotNull(second,
                "attempt " + attempt + ": an alternative route through the shared midpoint exists and "
                + "must be found - returning null here means visited is being marked on enqueue rather "
                + "than on dequeue");

            assertWalkable(second, "S", "T");
            assertEquals(second.size(), 3);
            assertNotEquals(render(second), render(first),
                "attempt " + attempt + ": the excluded route must not be returned again");
        }
    }

    /**
     * Once every route has been excluded there is nothing left to return.
     */
    @Test
    public void testAllRoutesExcludedReturnsNull() throws Exception
    {
        Layout l = graph(Arrays.asList("T"), "S>A", "S>B", "A>M", "B>M", "M>T");

        Point s = l.getPoint("S");
        Point t = l.getPoint("T");

        List<List<Edge>> exclude = new ArrayList<>();

        List<Edge> first = l.bfs(s, t, null);
        exclude.add(first);

        List<Edge> second = l.bfs(s, t, exclude);
        exclude.add(second);

        assertNull(l.bfs(s, t, exclude), "both routes are excluded, so there is nothing to return");
    }

    /**
     * Disconnected components yield no path rather than an exception.
     */
    @Test
    public void testUnreachableDestinationReturnsNull() throws Exception
    {
        Layout l = graph(Arrays.asList("B", "D"), "A>B", "C>D");

        assertNull(l.bfs(l.getPoint("A"), l.getPoint("D"), null), "A and D are in separate components");
    }

    /**
     * A locomotive can only be sent to a destination, so bfs refuses any other end point.
     */
    @Test
    public void testEndPointThatIsNotADestinationReturnsNull() throws Exception
    {
        Layout l = graph(Arrays.<String>asList(), "A>B");

        assertNull(l.bfs(l.getPoint("A"), l.getPoint("B"), null), "B is not marked as a destination");
    }

    /**
     * A cycle must not trap the search.  The timeout turns a hang into a failure rather than a build
     * that never finishes.
     */
    @Test(timeOut = 10000)
    public void testCycleTerminates() throws Exception
    {
        Layout l = graph(Arrays.asList("T"), "S>A", "A>S", "A>B", "B>A", "A>T");

        List<Edge> path = l.bfs(l.getPoint("S"), l.getPoint("T"), null);

        assertWalkable(path, "S", "T");
        assertEquals(render(path), "S>A>T");
    }

    /**
     * A cycle on the way to the destination must not stop the destination being reached, and must not
     * requeue endlessly now that visited is a set.
     */
    @Test(timeOut = 10000)
    public void testCycleBeforeTheDestinationStillResolves() throws Exception
    {
        Layout l = graph(Arrays.asList("T"), "S>A", "A>B", "B>C", "C>A", "C>T");

        List<Edge> path = l.bfs(l.getPoint("S"), l.getPoint("T"), null);

        assertWalkable(path, "S", "T");
        assertEquals(render(path), "S>A>B>C>T");
    }

    /**
     * A point that belongs to a different layout is not in this one, and bfs rejects it rather than
     * silently searching from nothing.
     */
    @Test
    public void testPointFromAnotherLayoutIsRejected() throws Exception
    {
        Layout l = graph(Arrays.asList("B"), "A>B");
        Layout other = graph(Arrays.asList("Z"), "Y>Z");

        try
        {
            l.bfs(other.getPoint("Y"), l.getPoint("B"), null);

            fail("a point that is not part of this layout must be rejected");
        }
        catch (Exception e)
        {
            assertNotNull(e.getMessage(), "the rejection must carry a message");
        }
    }

    /**
     * Start and end being the same destination: there is no zero-length path, so this resolves only if
     * a loop back to it exists.
     */
    @Test(timeOut = 10000)
    public void testStartEqualsEndNeedsALoop() throws Exception
    {
        Layout noLoop = graph(Arrays.asList("A"), "A>B");

        assertNull(noLoop.bfs(noLoop.getPoint("A"), noLoop.getPoint("A"), null),
            "there is no way back to A");

        Layout loop = graph(Arrays.asList("A"), "A>B", "B>A");

        List<Edge> path = loop.bfs(loop.getPoint("A"), loop.getPoint("A"), null);

        assertWalkable(path, "A", "A");
        assertEquals(render(path), "A>B>A");
    }

    // ------------------------------------------------------------------------------------------
    // Property-based tests over randomly generated graphs.
    //
    // The hand-written cases above are only as good as the topologies I thought to write, and I chose
    // those knowing what I expected the answer to be.  These generate graphs from a seed instead and
    // check invariants that must hold for any of them, against a shortest-path implementation written
    // independently of Layout.bfs.
    //
    // Seeds are a fixed sequence rather than a fresh Random, so a failure is reproducible and names the
    // seed that produced it.
    // ------------------------------------------------------------------------------------------

    private static final int RANDOM_SEEDS = 150;

    /**
     * A generated layout, together with the plain adjacency map of the edges that were actually
     * created.  Mirroring only successful createEdge calls means the reference below can never
     * disagree with the layout about which edges exist.
     */
    private static final class RandomGraph
    {
        Layout layout;
        List<String> names = new ArrayList<>();
        Set<String> destinations = new HashSet<>();
        Map<String, List<String>> adjacency = new HashMap<>();
    }

    private RandomGraph randomGraph(long seed) throws Exception
    {
        Random random = new Random(seed);
        RandomGraph g = new RandomGraph();

        g.layout = new Layout(model);

        int pointCount = 2 + random.nextInt(8);

        for (int i = 0; i < pointCount; i++)
        {
            String name = "P" + i;
            boolean destination = random.nextInt(3) == 0;

            g.layout.createPoint(name, destination, destination ? destinationS88 : null);
            g.names.add(name);
            g.adjacency.put(name, new ArrayList<>());

            if (destination)
            {
                g.destinations.add(name);
            }
        }

        // Density varies by seed so both sparse and dense shapes get covered.  Self-loops are offered
        // rather than filtered out - if the layout rejects one it simply does not reach the reference.
        double density = 0.1 + (random.nextInt(30) / 100.0);

        for (String from : g.names)
        {
            for (String to : g.names)
            {
                if (random.nextDouble() >= density)
                {
                    continue;
                }

                try
                {
                    g.layout.createEdge(from, to);
                    g.adjacency.get(from).add(to);
                }
                catch (Exception rejected)
                {
                    // The layout would not take this edge, so the reference must not have it either
                }
            }
        }

        return g;
    }

    /**
     * Fewest edges from start to end, using at least one edge, or -1 if there is no such route.
     * Written plainly and independently of Layout.bfs - that is the whole point of it.
     */
    private static int referenceDistance(Map<String, List<String>> adjacency, String start, String end)
    {
        Map<String, Integer> distance = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

        distance.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty())
        {
            String from = queue.remove();

            for (String to : adjacency.getOrDefault(from, new ArrayList<>()))
            {
                // Checked before the visited test, so a route back to the start is found when
                // start and end are the same point
                if (to.equals(end))
                {
                    return distance.get(from) + 1;
                }

                if (!distance.containsKey(to))
                {
                    distance.put(to, distance.get(from) + 1);
                    queue.add(to);
                }
            }
        }

        return -1;
    }

    /**
     * No point may appear twice, except that the last may be the start again - bfs returns as soon as
     * it finds an edge into the end point, so the end is the one point that can close a loop.
     */
    private static void assertSimple(List<Edge> path, String context)
    {
        Set<String> seen = new HashSet<>();

        seen.add(path.get(0).getStart().getName());

        for (int i = 0; i < path.size(); i++)
        {
            String point = path.get(i).getEnd().getName();
            boolean closingLoop = (i == path.size() - 1) && point.equals(path.get(0).getStart().getName());

            if (!closingLoop)
            {
                assertTrue(seen.add(point),
                    context + ": " + point + " appears twice in " + render(path));
            }
        }
    }

    /**
     * For any generated graph, bfs must find a route exactly when one exists, and it must be a
     * shortest one.
     */
    @Test(timeOut = 180000)
    public void testRandomGraphsMatchAnIndependentShortestPath() throws Exception
    {
        int reachable = 0;

        for (long seed = 0; seed < RANDOM_SEEDS; seed++)
        {
            RandomGraph g = randomGraph(seed);

            for (String startName : g.names)
            {
                for (String endName : g.names)
                {
                    String context = "seed " + seed + " " + startName + "->" + endName;

                    Point start = g.layout.getPoint(startName);
                    Point end = g.layout.getPoint(endName);

                    List<Edge> path = g.layout.bfs(start, end, null);

                    if (!g.destinations.contains(endName))
                    {
                        assertNull(path, context + ": end is not a destination");
                        continue;
                    }

                    int expected = referenceDistance(g.adjacency, startName, endName);

                    if (expected < 0)
                    {
                        assertNull(path, context + ": unreachable, so nothing may be returned");
                    }
                    else
                    {
                        assertNotNull(path, context + ": a route of " + expected + " edges exists");
                        assertWalkable(path, startName, endName);
                        assertSimple(path, context);
                        assertEquals(path.size(), expected, context + ": must be a shortest route");
                        reachable++;
                    }
                }
            }
        }

        // Guards against the generator degenerating into graphs with no routes at all, which would
        // make every assertion above vacuous
        assertTrue(reachable > 300, "expected plenty of reachable pairs, got " + reachable);
    }

    /**
     * Repeatedly excluding whatever was returned must terminate, must never hand back a route that was
     * already excluded, and must keep returning walkable routes until it runs out.
     */
    @Test(timeOut = 180000)
    public void testRandomGraphsExclusionTerminatesWithoutRepeating() throws Exception
    {
        int exhausted = 0;
        int alternativesFound = 0;

        for (long seed = 0; seed < RANDOM_SEEDS; seed++)
        {
            RandomGraph g = randomGraph(seed);

            for (String endName : g.destinations)
            {
                String startName = g.names.get((int) (seed % g.names.size()));
                String context = "seed " + seed + " " + startName + "->" + endName;

                Point start = g.layout.getPoint(startName);
                Point end = g.layout.getPoint(endName);

                List<List<Edge>> exclude = new ArrayList<>();
                Set<String> routesSeen = new HashSet<>();

                boolean ranOut = false;

                for (int attempt = 0; attempt < 40; attempt++)
                {
                    List<Edge> path = g.layout.bfs(start, end, exclude);

                    if (path == null)
                    {
                        ranOut = true;
                        break;
                    }

                    assertWalkable(path, startName, endName);
                    assertSimple(path, context);

                    assertTrue(routesSeen.add(render(path)),
                        context + ": returned " + render(path) + " a second time despite it being excluded");

                    exclude.add(path);

                    if (routesSeen.size() > 1)
                    {
                        alternativesFound++;
                    }
                }

                assertTrue(ranOut,
                    context + ": still returning new routes after 40 exclusions - " + routesSeen);

                exhausted++;
            }
        }

        assertTrue(exhausted > 50, "expected many start/destination pairs, got " + exhausted);

        // If this were zero, the exclusion path would never have been meaningfully exercised - which is
        // exactly the case that depends on visited being marked on dequeue
        assertTrue(alternativesFound > 0,
            "no graph ever yielded a second distinct route, so exclusion was never really tested");
    }
}
