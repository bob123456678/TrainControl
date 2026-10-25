package core;

import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;

/**
 * A frozen copy of Adam's railway, whose ground truth cannot move.
 *
 * Many classes in this suite open `cs2_sample_layout`, his real, live railway, because complex pathing and
 * random-movement checks need geometry no hand-authored fixture is going to have; it changes as he operates it, so
 * those tests' ground truth moved underneath them.  `test/layouts/live-snapshot` is the same railway taken out of `git
 * show HEAD:` (not the working tree, which carries uncommitted operating changes) and checked in; its README records
 * the commit.  It is the base Adam asked mutations to be built on: *"make sure you still have a copy of the live layout
 * for ones that require complex pathing and random movement checks.  I'd recommend using these complex setups as the
 * base for mutations."*
 *
 * This class asserts that the snapshot is still a railway rather than a skeleton, and that the numbers are exactly what
 * they were when it was frozen: a snapshot that can drift is not a snapshot, and the failure it guards against is the
 * one `LayoutSandbox.wiredPages` documents, a railway of ninety connections arriving as five edges with every test on
 * top of it passing.
 *
 * @author Adam
 */
public class testTheFrozenRailwayIsStillTheRailway
{
    private static support.Scenario scenario;

    /** The pages the snapshot holds, in the order the index names them. */
    private static final String[] PAGES =
    {
        "1 - Main", "2 - Bottom", "3 - Top Parking", "4 - Combined", "5 - Test"
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111); the scenario is a checked-in
        // copy.
        scenario = support.Scenario.open("live-snapshot");

        scenario.getModel().stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    /** Every page the railway is drawn on is there. */
    @Test
    public void testTheSnapshotHoldsTheWholeRailway() throws Exception
    {
        List<String> pages = new ArrayList<>(scenario.getModel().getLayoutList());

        assertEquals(pages, java.util.Arrays.asList(PAGES),
            "the snapshot no longer holds the five pages it was frozen with - it holds " + pages
            + ". A page missing from the folder is a page silently missing from every test that stands"
            + " on this scenario.");
    }

    /**
     * And it reduces to a real graph rather than to a handful of disconnected sensors.
     *
     * The counts are the ones measured when the snapshot was taken, pinned rather than bounded because nothing but an
     * edit can move them: a change here is either somebody editing the fixture or the reduction changing its mind, and
     * both want saying out loud.
     */
    @Test
    public void testTheSnapshotStillReducesToARailway() throws Exception
    {
        Layout built = scenario.build();

        // REFROZEN from Adam's measured layout (`e36df979`): his one-way marks, compulsory turn at BottomMainC and
        // barred arrivals take arrival sides away, and an arrival side that is gone is an edge and a copy that are not
        // built.  The five pages are byte-identical to the first freeze, so this is the setup moving, not the diagram.
        int reduced = scenario.getSession().getReducer().getEdges().size();

        // WHAT A REFRESH OF THE SNAPSHOT CHANGES (FR-136): all three figures, here and in its README.
        String refresh = "  If live-snapshot was refreshed on purpose, set " + reduced + " reduced edges, "
            + built.getPoints().size() + " Points and " + built.getEdges().size() + " edges here and in the README's"
            + " \"What it builds to\"";

        assertEquals(reduced, 122,
            "the frozen railway reduced to " + reduced
            + " edges rather than the 122 it was frozen with. Below about fifty this is the five-edge"
            + " skeleton LayoutSandbox.wiredPages describes - a railway whose switches have no"
            + " accessories, which TileGraph refuses to trace through." + refresh);

        assertEquals(built.getPoints().size(), 87,
            "the frozen railway built to " + built.getPoints().size() + " Points rather than 87." + refresh);

        assertEquals(built.getEdges().size(), 125,
            "the frozen railway built to " + built.getEdges().size() + " edges rather than 125." + refresh);
    }

    /**
     * And a train can be routed across it, which is what the snapshot is kept for.
     *
     * A hand-authored scenario is three or four sensors on one page; this one has ninety-six Points, five pages and
     * portals between them, and is the only fixture in the suite where a path finder has anything to find.
     */
    @Test
    public void testAPathCanBeFoundAcrossTheFrozenRailway() throws Exception
    {
        Layout built = scenario.build();

        int pairsTried = 0;
        int pathsFound = 0;

        List<Point> stations = new ArrayList<>();

        for (Point point : built.getPoints())
        {
            if (point.isDestination()) stations.add(point);
        }

        assertFalse(stations.isEmpty(), "the frozen railway has no stations on it at all");

        // A SAMPLE RATHER THAN THE WHOLE CENSUS: the claim is that this railway is connected enough to route on, not
        // that every pair is reachable (plenty legitimately are not, and the room and terminus rules say so).
        for (int i = 0; i < stations.size() && pairsTried < 40; i += 3)
        {
            for (int j = i + 1; j < stations.size() && pairsTried < 40; j += 7)
            {
                pairsTried++;

                List<Edge> path = built.bfs(stations.get(i), stations.get(j),
                    new ArrayList<List<Edge>>());

                if (path != null && !path.isEmpty()) pathsFound++;
            }
        }

        assertTrue(pairsTried > 10, "only " + pairsTried + " pairs of stations were tried, so this"
            + " test is not asking the question it says it is");

        assertTrue(pathsFound > pairsTried / 2, "only " + pathsFound + " of " + pairsTried
            + " station pairs could be routed between. On a railway this size most pairs are"
            + " reachable, so this is the shape of a graph that has come apart rather than of a"
            + " railway with a few dead arms.");
    }

    /**
     * The scenario is a COPY, and nothing here can reach the original.
     *
     * Asserted rather than assumed because the whole library rests on it, and OB-111 records what it costs when a test
     * reads the operator's own folder (his railway rewritten with different line endings, and once a change of his own
     * masked by the churn).
     */
    @Test
    public void testNothingIsPointedAtTheOperatorsOwnRailway() throws Exception
    {
        java.io.File folder = scenario.getFolder();

        assertNotNull(folder, "the scenario has no folder");

        String live = new java.io.File("cs2_sample_layout").getAbsolutePath();

        assertFalse(folder.getAbsolutePath().startsWith(live),
            "the scenario is pointed at " + folder.getAbsolutePath() + ", which is inside the"
            + " operator's own railway");

        String checkedIn = support.Scenario.folderFor("live-snapshot").getAbsolutePath();

        assertFalse(folder.getAbsolutePath().equals(checkedIn),
            "the scenario is pointed at the CHECKED-IN folder rather than at a copy of it, so the"
            + " window and the store would write to a tracked fixture and the next `git status` would"
            + " show the suite editing the repository");
    }
}
