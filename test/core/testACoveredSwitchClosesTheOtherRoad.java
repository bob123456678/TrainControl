package core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.traincontrol.base.Locomotive;

/**
 * A turnout and the two routes that share its metal, on `test/layouts/single-switch` (a west end, an approach, and the
 * two platforms the switch divides; the approach splits into two Points): the two routes lock each other, and a tail
 * lying across one road of the switch closes the other road, which is a different Edge.
 *
 * `Layout.isPathClear` refuses a path over an edge a standing train's tail covers, then over any edge that shares
 * metal with a covered one.  Coverage is recorded per edge, so the other pair of a switch's arms is not in the covered
 * set itself.  `testATrainCoversTheTrackBehindIt` exercises only the direct hit.
 *
 * `single-switch` is the only railway with a turnout (`GraphReducer` derives the relation from shared tiles).  The
 * controls are the point, since the railway refuses paths for a dozen reasons: the branch road is not itself
 * covered, the covered edge really is the main road, the two are lock partners both ways, and the identical path is
 * cleared the moment the tail is taken away.
 *
 * MUTATION: in `Layout.isPathClear`, make `if (lyingAcross == null)` above the `for (Edge sharing :
 * e.getLockEdges())` loop `if (lyingAcross == null && Boolean.parseBoolean("false"))`; only
 * `testTheOtherRoadThroughTheSwitchIsRefused` fails.
 *
 * @author Adam
 */
public class testACoveredSwitchClosesTheOtherRoad
{
    private static support.Scenario scenario;

    /** The train that stands at the main platform with its tail over the blades. */
    private static final String STANDING = "SM shared metal standing";

    /** The train that tries to come through the switch onto the branch. */
    private static final String MOVING = "SM shared metal moving";

    private static final int STANDING_ADDRESS = 72;

    private static final int MOVING_ADDRESS = 73;

    /**
     * Long enough to lie across the blades: the run in to the main platform is three units, so nine puts six behind the
     * switch.
     */
    private static final int TAIL = 9;

    /**
     * The squares between the sensors, where a length can live: a sensor's own square is an edge ENDPOINT, never an
     * intermediate step.  The fixture ships with no lengths on purpose (topology in the files, lengths in code).
     */
    private static final int[][] BETWEEN = { {2, 3}, {4, 3}, {6, 3}, {5, 2} };

    /**
     * The main road through the switch, BOTH WAYS ROUND: a tail fouls a rail whichever way traffic runs, and the two
     * copies are two Edges.  Which of them the tail walk names is an ordering detail; that both lock the branch road is
     * asserted below.
     */
    private static final String[] MAIN_ROAD =
    {
        "Approach (eastbound) -> MainPlatform",
        "MainPlatform -> Approach (westbound)"
    };

    /** The OTHER road over the same blades, both ways round. */
    private static final String[] BRANCH_ROAD =
    {
        "Approach (eastbound) -> BranchPlatform",
        "BranchPlatform -> Approach (westbound)"
    };

    /** The one the path takes: onto the branch platform, through the switch. */
    private static final String BRANCH = "Approach (eastbound) -> BranchPlatform";

    /** The lead in, which is on the path and shares metal with nothing. */
    private static final String LEAD_IN = "WestEnd -> Approach (eastbound)";

    private static final String MAIN = MAIN_ROAD[0];
    private static final String MAIN_BACK = MAIN_ROAD[1];
    private static final String BRANCH_BACK = BRANCH_ROAD[1];

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111).
        scenario = support.Scenario.open("single-switch");

        scenario.getModel().stop();

        for (int[] square : BETWEEN)
        {
            scenario.getSession().setTileLength(scenario.tile(square[0], square[1]), 1);
        }

        scenario.getModel().newMM2Locomotive(STANDING, STANDING_ADDRESS);
        scenario.getModel().newMM2Locomotive(MOVING, MOVING_ADDRESS);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null)
        {
            try { scenario.getModel().deleteLoc(STANDING); } catch (Exception ignored) { }
            try { scenario.getModel().deleteLoc(MOVING); } catch (Exception ignored) { }

            scenario.close();
        }
    }

    /**
     * THE FIXTURE: the two roads through the switch are lock partners, in both directions.  Symmetry is asserted
     * because the rule under test tells shared metal from an FR-001 occupancy restriction by it.
     */
    @Test
    public void testTheTwoRoadsThroughTheSwitchShareMetal() throws Exception
    {
        Layout built = scenario.build();

        for (String main : MAIN_ROAD)
        {
            for (String branch : BRANCH_ROAD)
            {
                assertTrue(lockNames(built, main).contains(branch),
                    "`" + main + "` does not hold `" + branch + "`, so there is no shared metal here"
                    + " and nothing below is about a switch.  It holds " + lockNames(built, main));

                assertTrue(lockNames(built, branch).contains(main),
                    "`" + branch + "` does not hold `" + main + "` BACK.  The rule under test tells"
                    + " shared metal from an FR-001 occupancy restriction by exactly that symmetry -"
                    + " a restriction is one-directional - so a partner that does not list this edge"
                    + " back is skipped and the refusal would never happen.  It holds "
                    + lockNames(built, branch));
            }
        }

        assertFalse(lockNames(built, LEAD_IN).contains(BRANCH),
            "the lead in from the west end holds the branch road too, so it is not the innocent"
            + " neighbour this class uses it as");
    }

    /**
     * THE STATE: the tail covers the main road and does NOT cover the branch road, which makes the refusal below
     * attributable: otherwise the direct rule would refuse it.
     */
    @Test
    public void testTheTailCoversTheMainRoadAndNotTheBranch() throws Exception
    {
        Layout built = staged();

        Map<Edge, Locomotive> covered = built.edgesCoveredByStandingTrains();

        assertFalse(covered.isEmpty(),
            "with a train of " + TAIL + " standing at the main platform over three units of run in,"
            + " nothing is covered at all - so the tail walk stopped early and this class is measuring"
            + " an empty rule");

        assertTrue(anyOf(MAIN_ROAD, coveredNames(covered)),
            "the tail does not lie across the main road through the switch, which is the whole"
            + " premise.  It covers " + coveredNames(covered));

        for (String branch : BRANCH_ROAD)
        {
            assertFalse(coveredNames(covered).contains(branch),
                "`" + branch + "` is itself in the covered set, so a refusal below would come from"
                + " the DIRECT rule - `coveredTrack.get(e) != null` - and would say nothing at all"
                + " about shared metal, which is the gap this class exists to close.  Covered: "
                + coveredNames(covered));
        }
    }

    /**
     * THE SUBJECT: the other road through the switch is refused.  It shares metal with the road the tail lies across,
     * the collision Adam reported at switch 60.
     */
    @Test
    public void testTheOtherRoadThroughTheSwitchIsRefused() throws Exception
    {
        Layout built = staged();

        Locomotive mover = scenario.getModel().getLocByName(MOVING);

        assertNotNull(mover, "the moving train is not on this railway");

        assertFalse(built.isPathClear(ontoTheBranch(built), mover),
            "a path onto the branch platform was cleared while another train's tail lies across the"
            + " main road through the same switch.  Neither edge of that path is covered - the covered"
            + " one is " + coveredNames(built.edgesCoveredByStandingTrains())
            + " - so what should refuse it is the shared metal: the two roads are"
            + " lock partners, and a train lying across one fouls the blades for the other.  This is"
            + " Adam's switch 60 (AUT9-B2), on the fixture that can express it");
    }

    /**
     * THE CONTROL: the same path over the same railway is cleared when nothing is lying across it; a rule that refused
     * every path would otherwise pass the test above.
     */
    @Test
    public void testTheSameRoadIsClearWithNothingLyingAcrossIt() throws Exception
    {
        Layout built = staged();

        // The tail taken away and nothing else touched; the only difference from the test above is whether anything
        // protrudes.
        Locomotive standing = scenario.getModel().getLocByName(STANDING);

        assertNotNull(standing, "the standing train is not on this railway");

        standing.setTrainLength(0);

        assertTrue(built.edgesCoveredByStandingTrains().isEmpty(),
            "the tail was taken away and track is still covered, so this control is not controlling"
            + " for what it says it is");

        Locomotive mover = scenario.getModel().getLocByName(MOVING);

        assertTrue(built.isPathClear(ontoTheBranch(built), mover),
            "the branch platform is unreachable from the west end even with nothing lying across the"
            + " switch, so the refusal in the test above says nothing about tails.  The railway"
            + " refused it for: " + Layout.getLastError());
    }

    // ---------------------------------------------------------------- the two routes through the switch

    /**
     * The railway the fixture says it is: four stations, five Points, six edges, pinned exactly so a drifting fixture
     * or reduction is noticed.
     */
    @Test
    public void testTheJunctionBuildsToTheShapeItIsDrawnAs() throws Exception
    {
        Layout built = scenario.build();

        assertEquals(scenario.getSession().getReducer().getPoints().size(), 4,
            "the fixture is drawn with four feedback sensors and reduced to "
            + scenario.getSession().getReducer().getPoints().size());

        assertEquals(built.getPoints().size(), 5,
            "four sensors, one of which - the approach - is arrived at from both sides and so becomes"
            + " two Points. Built: " + pointNames(built));

        assertEquals(built.getEdges().size(), 6,
            "three pieces of track, each running both ways. Built: " + edgeNames(built));

        // AND THE SPLIT IS A REAL SPLIT: two Points that are one piece of metal.
        Point east = built.getPoint("Approach (eastbound)");
        Point west = built.getPoint("Approach (westbound)");

        assertNotNull(east, "the approach did not split - " + pointNames(built));
        assertNotNull(west, "the approach did not split - " + pointNames(built));

        assertEquals(east.getBlock(), west.getBlock(),
            "the two copies of the approach do not share a block, so the model would let two trains"
            + " stand on one square");

        assertNotNull(east.getBlock(), "the copies share no block at all");
    }

    /**
     * The two routes through the switch lock each other, in both directions of travel: every one of the four edges
     * names the two that use the other blade position.
     */
    @Test
    public void testTheTwoRoutesThroughTheSwitchLockEachOther() throws Exception
    {
        Layout built = scenario.build();

        assertEquals(lockNames(built, MAIN), asSet(BRANCH, BRANCH_BACK),
            "the main route over the switch should hold both directions of the branch route, and holds "
            + lockNames(built, MAIN));

        assertEquals(lockNames(built, BRANCH), asSet(MAIN, MAIN_BACK),
            "the branch route over the switch should hold both directions of the main route, and holds "
            + lockNames(built, BRANCH));

        // AND THE TRAILING DIRECTION TOO: a tail lies across a rail whichever way traffic runs, and the fixture opens
        // the switch's trailing direction since a switch defaults to base-to-forks, which gives a one-way railway.
        assertEquals(lockNames(built, MAIN_BACK), asSet(BRANCH, BRANCH_BACK),
            "the main route read the other way should hold the branch route, and holds "
            + lockNames(built, MAIN_BACK));

        assertEquals(lockNames(built, BRANCH_BACK), asSet(MAIN, MAIN_BACK),
            "the branch route read the other way should hold the main route, and holds "
            + lockNames(built, BRANCH_BACK));
    }

    /**
     * And the lock does something: claiming one arm makes the other read as occupied, which `isPathClear` asks before
     * letting a train onto a switch.
     */
    @Test
    public void testClaimingOneArmHoldsTheOther() throws Exception
    {
        Layout built = scenario.build();

        Edge main = edge(built, MAIN);
        Edge branch = edge(built, BRANCH);

        assertFalse(branch.isOccupied(null),
            "the branch arm reads as occupied before anything has claimed anything");

        main.setOccupied();

        assertTrue(branch.isOccupied(null),
            "a route was granted over the main arm of the switch and the branch arm still reads clear,"
            + " so a second train could be cleared onto the same blades");

        main.setUnoccupied();

        assertFalse(branch.isOccupied(null),
            "the main arm was released and the branch arm is still held, so the switch would stay"
            + " blocked for good");
    }

    /**
     * The switch really is switchable, and its two positions are the two routes: the parser turns a `linksweiche` with
     * no usable address into a CUSTOM_PERM_LEFT, a defective turnout that can only be trailed through, invisibly in the
     * file.
     */
    @Test
    public void testTheSwitchIsCommandedByEachRoute() throws Exception
    {
        Layout built = scenario.build();

        assertFalse(edge(built, MAIN).getConfigCommands().isEmpty(),
            "the main route over the switch commands nothing, so the blades are never thrown for it -"
            + " which is what a defective turnout with no address looks like, not a switch");

        assertFalse(edge(built, BRANCH).getConfigCommands().isEmpty(),
            "the branch route over the switch commands nothing");

        assertFalse(edge(built, MAIN).getConfigCommands().toString()
            .equals(edge(built, BRANCH).getConfigCommands().toString()),
            "the two routes over the switch command the SAME thing, so one of them is not throwing the"
            + " blades and a train sent that way would take whichever road the switch was already"
            + " lying for: " + edge(built, MAIN).getConfigCommands());
    }

    // ---------------------------------------------------------------- the staging

    /**
     * Builds the railway with a train standing over the blades and another waiting at the west end, placed in code:
     * `single-switch` ships with nothing standing.
     */
    private static Layout staged() throws Exception
    {
        Layout built = scenario.build();

        Locomotive standing = scenario.getModel().getLocByName(STANDING);
        Locomotive mover = scenario.getModel().getLocByName(MOVING);

        assertNotNull(standing, "the standing train did not reach the built railway");
        assertNotNull(mover, "the moving train did not reach the built railway");

        standing.setTrainLength(TAIL);

        built.moveLocomotive(STANDING, "MainPlatform", false);
        built.moveLocomotive(MOVING, "WestEnd", false);

        Point platform = built.getPoint("MainPlatform");

        assertNotNull(platform, "the fixture has no MainPlatform - it has " + pointNames(built));

        // It came in from the west, the only way in.  Recorded, because a tail walk with no arrival side stops at the
        // first hop and blocks nothing.
        platform.setArrivedFrom("W");

        return built;
    }

    /** The path from the west end onto the branch platform, through the switch. */
    private static List<Edge> ontoTheBranch(Layout built)
    {
        List<Edge> path = new ArrayList<>();

        path.add(edge(built, LEAD_IN));
        path.add(edge(built, BRANCH));

        return path;
    }

    /** That edge, with a message naming what is there instead. */
    private static Edge edge(Layout built, String name)
    {
        for (Edge edge : built.getEdges())
        {
            if (name.equals(edge.getName())) return edge;
        }

        throw new IllegalStateException("the fixture has no edge called \"" + name + "\" - it has "
            + edgeNames(built));
    }

    /** The names of the edges it holds when it is claimed. */
    private static Set<String> lockNames(Layout built, String name)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Edge locked : edge(built, name).getLockEdges()) out.add(locked.getName());

        return out;
    }

    private static Set<String> asSet(String... names)
    {
        Set<String> out = new LinkedHashSet<>();

        for (String name : names) out.add(name);

        return out;
    }

    /** Whether any candidate is in what was actually there. */
    private static boolean anyOf(String[] names, Set<String> found)
    {
        for (String name : names)
        {
            if (found.contains(name)) return true;
        }

        return false;
    }

    /** The covered set's edge names. */
    private static Set<String> coveredNames(Map<Edge, Locomotive> covered)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Edge edge : covered.keySet()) out.add(edge.getName());

        return out;
    }

    /** Every edge name. */
    private static Set<String> edgeNames(Layout built)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Edge edge : built.getEdges()) out.add(edge.getName());

        return out;
    }

    /** Every Point name. */
    private static Set<String> pointNames(Layout built)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Point point : built.getPoints()) out.add(point.getName());

        return out;
    }
}
