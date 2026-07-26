import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.util.I18n;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Differential test: the current Layout.bfs against the implementation it replaced.
 *
 * The change swapped bfs's "visited" collection from a LinkedList probed with contains() to a HashSet.
 * The argument that this is safe is that Point decides equality by name and derives hashCode from the
 * same field, so membership is decided identically and only the lookup cost differs.  That is an
 * argument; this suite is the evidence.
 *
 * WHAT EQUIVALENCE CAN MEAN HERE.  bfs is deliberately nondeterministic - Layout.getNeighbors ends with
 * Collections.shuffle, commented "Randomize order to allow for variation in paths" - so two calls with
 * identical arguments may legitimately return different routes of the same length.  Comparing exact
 * routes would therefore fail even when comparing the current implementation against itself, and the
 * first version of this file did exactly that and reported false divergences.
 *
 * What IS deterministic, and so what gets compared:
 *
 *   - whether a route exists at all: reachability does not depend on the order neighbours are tried
 *   - the LENGTH of the route returned: breadth first exhausts every allowed route of length n before
 *     trying any of length n+1, whatever order it tries them in
 *   - the SET of routes each implementation can produce, sampled over many runs - see
 *     testBothImplementationsExploreTheSameSetOfRoutes
 *
 * Only the choice among equally short routes is random, and that is the one thing this change could not
 * affect.  Note the same caveat applies to any test of bfs: an exact-route assertion is only safe when
 * the shortest route is unique.
 *
 * legacyBfs below is transcribed from the pre-change source (commit 071d424~1) rather than from memory.
 * It is verbatim apart from two unavoidable adjustments: Layout.PointPath is private, so an identical
 * local holder stands in for it, and this.getNeighbors becomes layout.getNeighbors - the same public
 * method the original called on itself.
 *
 * Both implementations run against the SAME Layout instance, so the graphs cannot differ, and bfs does
 * not mutate the layout, so the order they run in does not matter.
 */
public class testLayoutBfsEquivalence
{
    private static MarklinControlStation model;
    private static String destinationS88;

    /** Deliberately different from testLayoutBfs, so the two suites do not explore the same shapes. */
    private static final int SEEDS = 120;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();

        MarklinFeedback feedback = model.newFeedback(47100, null);
        model.setFeedbackState(feedback.getName(), false);

        destinationS88 = feedback.getName();
    }

    /**
     * Stand-in for Layout's private PointPath, with the same two fields.
     */
    private static final class PointPath
    {
        public Point start;
        public List<Edge> path;

        public PointPath(Point start, List<Edge> path)
        {
            this.start = start;
            this.path = path;
        }
    }

    /**
     * Layout.bfs as it was before the visited collection was changed.  Do not tidy this up - its value
     * is in being a faithful copy of what shipped previously.
     */
    private static List<Edge> legacyBfs(Layout layout, Point start, Point end, List<List<Edge>> excludePaths)
        throws Exception
    {
        start = layout.getPoint(start.getName());
        end = layout.getPoint(end.getName());

        if (start == null || end == null)
        {
            throw new Exception(
                I18n.f("autolayout.errorInvalidPointsSpecified")
            );
        }

        if (!end.isDestination())
        {
            return null;
        }

        List<Point> visited = new LinkedList<>();
        Queue<PointPath> queue = new LinkedList<>();

        queue.add(new PointPath(start, new LinkedList<>()));

        while (!queue.isEmpty())
        {
            PointPath current = queue.remove();
            Point point = current.start;
            List<Edge> path = current.path;

            visited.add(point);

            for (Edge next : layout.getNeighbors(point))
            {
                if (next.getEnd().equals(end))
                {
                    path.add(next);

                    // Path is not within the list of disallowed paths - return it
                    if (excludePaths == null || !excludePaths.contains(path))
                    {
                        return path;
                    }
                    // Path is disallowed - continue and get another one
                    else
                    {
                        path.remove(path.size() - 1);
                    }
                }
                else if (!visited.contains(next.getEnd()))
                {
                    List<Edge> newPath = new LinkedList<>(path);
                    newPath.add(next);

                    queue.add(new PointPath(next.getEnd(), newPath));
                }
            }
        }

        return null;
    }

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
     * The comparison the shuffle permits: both must agree on whether a route exists, and on how long it
     * is.  Which of several equally short routes came back is random by design, so it is not compared.
     */
    private static void assertSameOutcome(List<Edge> current, List<Edge> legacy, String context)
    {
        if (current == null || legacy == null)
        {
            assertEquals(render(current), render(legacy),
                context + ": one implementation found a route and the other did not");
            return;
        }

        assertEquals(current.size(), legacy.size(),
            context + ": route lengths differ - current " + render(current)
            + ", previous " + render(legacy));
    }

    private static final class RandomGraph
    {
        Layout layout;
        List<String> names = new ArrayList<>();
        Set<String> destinations = new HashSet<>();
    }

    /**
     * Wider and denser than the generator in testLayoutBfs, so the two suites cover different ground.
     */
    private RandomGraph randomGraph(long seed) throws Exception
    {
        Random random = new Random(seed);
        RandomGraph g = new RandomGraph();

        g.layout = new Layout(model);

        int pointCount = 2 + random.nextInt(11);

        for (int i = 0; i < pointCount; i++)
        {
            String name = "Q" + i;
            boolean destination = random.nextInt(3) == 0;

            g.layout.createPoint(name, destination, destination ? destinationS88 : null);
            g.names.add(name);

            if (destination)
            {
                g.destinations.add(name);
            }
        }

        double density = 0.08 + (random.nextInt(38) / 100.0);

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
                }
                catch (Exception rejected)
                {
                    // Whatever the layout refuses simply is not part of the graph
                }
            }
        }

        return g;
    }

    /**
     * Every start/end pair of every generated graph: both implementations must agree on whether a route
     * exists and on how long it is.
     */
    @Test(timeOut = 300000)
    public void testBothImplementationsAgreeOnRandomGraphs() throws Exception
    {
        int compared = 0;
        int routesFound = 0;

        for (long seed = 0; seed < SEEDS; seed++)
        {
            RandomGraph g = randomGraph(seed);

            for (String startName : g.names)
            {
                for (String endName : g.names)
                {
                    Point start = g.layout.getPoint(startName);
                    Point end = g.layout.getPoint(endName);

                    List<Edge> current = g.layout.bfs(start, end, null);
                    List<Edge> legacy = legacyBfs(g.layout, start, end, null);

                    assertSameOutcome(current, legacy, "seed " + seed + " " + startName + "->" + endName);

                    compared++;

                    if (current != null)
                    {
                        routesFound++;
                    }
                }
            }
        }

        // Two implementations that both always returned null would agree perfectly and prove nothing
        assertTrue(routesFound > 300,
            "only " + routesFound + " of " + compared + " comparisons found a route, so the agreement "
            + "above is close to vacuous");
    }

    // The differential deliberately does NOT compare repeated exclusion over random graphs.  Measured
    // first: comparing the current implementation against ITSELF that way produces just as many
    // disagreements (62 in a 120-graph run) as comparing it against the previous one (57).  With
    // mark-on-dequeue, which points get enqueued at each depth depends on the order neighbours are
    // tried, so the set of routes the search can enumerate varies per call - and once some are
    // excluded, whether an allowed one remains findable varies too.  A test built on that compares
    // luck, not implementations.  What it would have measured is covered instead by the fixed
    // topologies below and by the set-of-routes test, both verified stable over repeated runs.

    /**
     * The shapes testLayoutBfs pins by hand, checked for agreement too.
     */
    @Test(timeOut = 60000)
    public void testBothImplementationsAgreeOnTheHandWrittenTopologies() throws Exception
    {
        for (String[] edges : topologies())
        {
            Layout layout = build(edges);

            for (String startName : pointsOf(edges))
            {
                for (String endName : pointsOf(edges))
                {
                    Point start = layout.getPoint(startName);
                    Point end = layout.getPoint(endName);

                    String context = Arrays.toString(edges) + " " + startName + "->" + endName;

                    List<Edge> current = layout.bfs(start, end, null);

                    assertSameOutcome(current, legacyBfs(layout, start, end, null), context);

                    if (current != null)
                    {
                        List<List<Edge>> exclude = new ArrayList<>();
                        exclude.add(current);

                        assertSameOutcome(layout.bfs(start, end, exclude),
                            legacyBfs(layout, start, end, exclude),
                            context + " with one route excluded");
                    }
                }
            }
        }
    }

    /**
     * Stronger than comparing single calls: across many runs each implementation should reach exactly
     * the same set of routes, since the only thing separating two runs is the shuffle.
     *
     * This also pins the nondeterminism itself.  The shared-midpoint graph has two distinct three-edge
     * routes, and requiring both to be observed proves getNeighbors really does vary the order - if that
     * shuffle were ever removed, this fails and explains why.  A hundred runs makes missing one of two
     * routes vanishingly unlikely.
     */
    @Test(timeOut = 60000)
    public void testBothImplementationsExploreTheSameSetOfRoutes() throws Exception
    {
        Layout layout = build(new String[] {"S>A", "S>B", "A>M", "B>M", "M>T"});

        Point start = layout.getPoint("S");
        Point end = layout.getPoint("T");

        Set<String> currentRoutes = new TreeSet<>();
        Set<String> legacyRoutes = new TreeSet<>();

        for (int i = 0; i < 100; i++)
        {
            currentRoutes.add(render(layout.bfs(start, end, null)));
            legacyRoutes.add(render(legacyBfs(layout, start, end, null)));
        }

        assertEquals(currentRoutes, legacyRoutes,
            "the two implementations reach different sets of routes");

        assertEquals(currentRoutes, new TreeSet<>(Arrays.asList("S>A>M>T", "S>B>M>T")),
            "both three-edge routes should turn up across 100 runs - if only one does, getNeighbors has "
            + "stopped shuffling and the tie-break is no longer random");
    }

    /**
     * Both must reject a point that is not part of the layout, rather than one silently searching from
     * nothing while the other throws.
     */
    @Test
    public void testBothImplementationsRejectAForeignPoint() throws Exception
    {
        RandomGraph g = randomGraph(1);

        Layout other = new Layout(model);
        other.createPoint("NotInG", true, destinationS88);

        Point foreign = other.getPoint("NotInG");
        Point end = g.layout.getPoint(g.names.get(0));

        boolean currentThrew = false;
        boolean legacyThrew = false;

        try
        {
            g.layout.bfs(foreign, end, null);
        }
        catch (Exception e)
        {
            currentThrew = true;
        }

        try
        {
            legacyBfs(g.layout, foreign, end, null);
        }
        catch (Exception e)
        {
            legacyThrew = true;
        }

        assertTrue(currentThrew, "a point from another layout must be rejected");
        assertTrue(legacyThrew, "the previous implementation rejected it too");
    }

    private static String[][] topologies()
    {
        return new String[][] {
            {"A>B"},
            {"S>A", "A>B", "B>T", "S>T"},
            {"S>A", "S>B", "A>M", "B>M", "M>T"},
            {"A>B", "C>D"},
            {"S>A", "A>S", "A>B", "B>A", "A>T"},
            {"S>A", "A>B", "B>C", "C>A", "C>T"},
            {"A>B", "B>A"}
        };
    }

    private static Set<String> pointsOf(String[] edges)
    {
        Set<String> names = new LinkedHashSet<>();

        for (String spec : edges)
        {
            names.addAll(Arrays.asList(spec.split(">")));
        }

        return names;
    }

    /** Every point is made a destination, so no pair is skipped for lacking one. */
    private Layout build(String[] edges) throws Exception
    {
        Layout layout = new Layout(model);

        for (String name : pointsOf(edges))
        {
            layout.createPoint(name, true, destinationS88);
        }

        for (String spec : edges)
        {
            String[] pair = spec.split(">");
            layout.createEdge(pair[0], pair[1]);
        }

        return layout;
    }
}
