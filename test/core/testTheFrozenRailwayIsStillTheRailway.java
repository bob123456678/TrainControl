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
 * **Why it exists.**  Eighteen classes in this suite open `cs2_sample_layout` - his real, live railway
 * - because complex pathing and random-movement checks need geometry no hand-authored fixture is going
 * to have.  That folder changes as he operates it, so those tests' ground truth moves underneath them:
 * on 2026-09-09 three of them broke that way in one morning, one having picked its subject out of the
 * live railway by a property he had since changed.
 *
 * `test/layouts/live-snapshot` is the same railway taken out of `git show HEAD:` - not the working tree,
 * which carries uncommitted operating changes - and checked in.  Its README records the commit.  It is
 * the base Adam asked mutations to be built on: *"make sure you still have a copy of the live layout for
 * ones that require complex pathing and random movement checks.  I'd recommend using these complex
 * setups as the base for mutations."*
 *
 * **What this class asserts** is that the snapshot is still a railway rather than a skeleton, and that
 * the numbers are exactly what they were when it was frozen.  Exact, not "more than a few": a snapshot
 * that can drift is not a snapshot, and the failure mode this is guarding against is the one
 * `LayoutSandbox.wiredPages` documents - a railway of ninety connections arriving as five edges, with
 * every test on top of it passing because an assertion about a square that has no edges is usually an
 * assertion about null.
 *
 * @author Adam
 */
public class testTheFrozenRailwayIsStillTheRailway
{
    private static support.Scenario scenario;

    /**
     * The pages the snapshot holds, in the order the index names them.
     */
    private static final String[] PAGES =
    {
        "1 - Main", "2 - Bottom", "3 - Top Parking", "4 - Combined", "5 - Test"
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111). Nothing here
        // can reach cs2_sample_layout: the scenario is a checked-in copy and the copy is sandboxed.
        scenario = support.Scenario.open("live-snapshot");

        scenario.getModel().stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    /**
     * Every page the railway is drawn on is there.
     */
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
     * The counts are the ones measured when the snapshot was taken.  They are pinned rather than
     * bounded because the folder is checked in and nothing but an edit can move them: a change here is
     * either somebody editing the fixture or the reduction changing its mind, and both want saying out
     * loud.
     */
    @Test
    public void testTheSnapshotStillReducesToARailway() throws Exception
    {
        Layout built = scenario.build();

        assertEquals(scenario.getSession().getReducer().getEdges().size(), 128,
            "the frozen railway reduced to " + scenario.getSession().getReducer().getEdges().size()
            + " edges rather than the 128 it was frozen with. Below about fifty this is the five-edge"
            + " skeleton LayoutSandbox.wiredPages describes - a railway whose switches have no"
            + " accessories, which TileGraph refuses to trace through.");

        assertEquals(built.getPoints().size(), 96,
            "the frozen railway built to " + built.getPoints().size() + " Points rather than 96");

        assertEquals(built.getEdges().size(), 149,
            "the frozen railway built to " + built.getEdges().size() + " edges rather than 149");
    }

    /**
     * And a train can be routed across it, which is what the snapshot is kept for.
     *
     * A hand-authored scenario is three or four sensors on one page; this one has ninety-six Points,
     * five pages and portals between them, and it is the only fixture in the suite where a path finder
     * has anything to find.
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

        // A SAMPLE RATHER THAN THE WHOLE CENSUS: ninety-six Points is nine thousand pairs, and the
        // claim being made is that this railway is connected enough to route on, not that every pair
        // is reachable - plenty of them legitimately are not, and the room and terminus rules say so.
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
     * Asserted rather than assumed because it is the rule the whole library rests on, and OB-111 is the
     * record of what it costs when a test reads the operator's own folder: his railway is rewritten with
     * different line endings, and on one occasion a change he had made himself was masked by the churn.
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
