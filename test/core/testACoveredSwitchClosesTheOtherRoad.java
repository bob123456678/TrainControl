package core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * A tail lying across one road of a switch closes the other road, which is a different Edge.
 *
 * **THE ANTI-COLLISION RULE AT A SWITCH, AND IT HAD NO TEST THAT WENT RED WITHOUT IT** (AUT9-B2).
 *
 * `Layout.isPathClear` refuses a path over an edge a standing train's tail covers, and then - the part
 * this class is about - over any edge that SHARES METAL with a covered one.  Its own comment records
 * the collision it was written for: Adam, 2026-09-07, EN57-203 *"is allowed to traverse a
 * blocked/shaded switch (60) to get from TunnelLeftPark to BottomMainC, even though it should not be
 * possible."*  Coverage is recorded per EDGE; a train lying across one pair of a switch's arms fouls
 * the other pair, and the other pair is an Edge that was never in the covered set.
 *
 * The autonomy review disabled that branch and ran the two dedicated covered-track classes.  **Both
 * stayed fully green.**  `testATrainCoversTheTrackBehindIt` exercises only the DIRECT hit -
 * `coveredTrack.get(e) != null` - and `testTwoRoutesShareOneSwitch` exercises lock edges through a
 * route HOLD (`Edge.isOccupied`), which is a different mechanism entirely: a route hold is a
 * reservation somebody took out, and a standing train holds nothing.  So the branch that fixed the
 * switch-60 collision was covered by nothing that turns red when it is removed.
 *
 * **WHY IT HAS TO BE THIS FIXTURE.**  The relation is derived by `GraphReducer` from SHARED TILES, so
 * expressing it needs a turnout: two logical routes over one piece of metal.  `single-switch` is the
 * only railway in the library that has one, and it exists for exactly this - `test/README.md` recorded
 * the absence as MON-C17 to C21 before a defect walked through the gap.
 *
 * **THE CONTROLS ARE THE POINT, not politeness.**  A refusal proves nothing on its own: the railway
 * refuses paths for a dozen reasons, and a class that only ever asserts "refused" passes on any of
 * them.  So this asserts, on the same built railway, that the branch road is NOT itself covered (the
 * direct rule cannot be what refuses it), that the covered edge really is the main road, that the two
 * are lock partners in both directions, and that the identical path over the identical railway is
 * CLEARED the moment the tail is taken away.  `assert-the-variable-not-the-control`.
 *
 * MUTATION, and it is the review's own: in `Layout.isPathClear`, change
 * `if (lyingAcross == null)` above the `for (Edge sharing : e.getLockEdges())` loop to
 * `if (lyingAcross == null && Boolean.parseBoolean("false"))`.
 * `testTheOtherRoadThroughTheSwitchIsRefused` then fails and every other test in this class stays
 * green - which is what says the failure is about that branch and not about the fixture.
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
     * Long enough to lie across the blades.
     *
     * The run in from the switch to the main platform is measured at three units below, so nine puts
     * six units of train behind the switch - which is the shape Adam described: *"a train of length 4
     * is standing at bottommainb, which has a length of 1 leading up to its switch"*.
     */
    private static final int TAIL = 9;

    /**
     * The squares between the sensors, which is where a length can live.
     *
     * A sensor's own square is an edge ENDPOINT and never an intermediate step, so measuring the
     * platforms would measure nothing.  Only the track between them counts, and the fixture ships with
     * no lengths at all on purpose - topology in the files, lengths in code.
     */
    private static final int[][] BETWEEN = { {2, 3}, {4, 3}, {6, 3}, {5, 2} };

    /**
     * The main road through the switch, BOTH WAYS ROUND.
     *
     * A tail fouls a rail whichever way traffic runs on it, and the two copies of this road are two
     * Edges.  Which of them the tail walk names is an ordering detail - the approach lies west of the
     * platform, so both candidates offer the same side and the walk takes whichever comes first - and
     * a test that pinned one of them would go red on a change that means nothing.  What is not a
     * detail, and is asserted below, is that both of them lock the branch road.
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
     * THE FIXTURE: the two roads through the switch are lock partners, in both directions.
     *
     * Asserted first because everything below is a statement about that relation, and a fixture where
     * it has quietly gone would let the subject pass or fail for a reason nobody could see.  Symmetry
     * is asserted rather than assumed: the rule under test tells shared metal from an FR-001 occupancy
     * restriction by exactly that, and a one-directional pair would be skipped.
     *
     * @throws Exception on a failure to build
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
     * THE STATE: the tail covers the main road and does NOT cover the branch road.
     *
     * This is the half that makes the refusal below attributable.  If the branch road were itself in
     * the covered set, the direct rule - `coveredTrack.get(e) != null` - would refuse it and the
     * shared-metal branch could be deleted with this class still green, which is exactly the gap
     * AUT9-B2 reports.
     *
     * @throws Exception on a failure to build
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
     * THE SUBJECT: the other road through the switch is refused.
     *
     * Nothing in the path is covered.  `Approach (eastbound) -> BranchPlatform` shares metal with the
     * road the tail is lying across, and running a second train over it would put it through blades a
     * train is already standing on - which is the collision Adam reported at switch 60.
     *
     * @throws Exception on a failure to build
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
     * THE CONTROL: the same path over the same railway is cleared when nothing is lying across it.
     *
     * A refusal is only evidence if the acceptance exists.  Without this, a rule that refused every
     * path on this railway - or a fixture whose branch platform had simply become unreachable - would
     * pass the test above and prove nothing.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheSameRoadIsClearWithNothingLyingAcrossIt() throws Exception
    {
        Layout built = staged();

        // THE TAIL TAKEN AWAY, and nothing else touched: the trains stand where they stood, the
        // lengths are what they were, the switch is the same switch.  The only difference between this
        // and the test above is whether anything protrudes.
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

    // ---------------------------------------------------------------- the staging

    /**
     * Builds the railway with a train standing over the blades and another waiting at the west end.
     *
     * PLACED IN CODE, never read off the fixture.  `single-switch` ships with nothing standing
     * anywhere and no locomotive named, deliberately - so the two trains here are made by this class,
     * put where this class wants them, and the arrival side that decides which way the tail lies is
     * said out loud rather than inferred.
     *
     * @return the built layout
     * @throws Exception on a failure to build
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

        // IT CAME IN FROM THE WEST, which is the only way in: the approach is west of the platform and
        // the buffer stop is east of it.  Recorded rather than guessed, because a tail walk with no
        // arrival side stops at the first hop and blocks nothing.
        platform.setArrivedFrom("W");

        return built;
    }

    /**
     * The path from the west end onto the branch platform, through the switch.
     *
     * @param built the railway
     * @return the two edges, in order
     */
    private static List<Edge> ontoTheBranch(Layout built)
    {
        List<Edge> path = new ArrayList<>();

        path.add(edge(built, LEAD_IN));
        path.add(edge(built, BRANCH));

        return path;
    }

    /**
     * @param built the railway
     * @param name the edge's name
     * @return that edge, with a message naming what is there instead
     */
    private static Edge edge(Layout built, String name)
    {
        for (Edge edge : built.getEdges())
        {
            if (name.equals(edge.getName())) return edge;
        }

        throw new IllegalStateException("the fixture has no edge called \"" + name + "\" - it has "
            + edgeNames(built));
    }

    /**
     * @param built the railway
     * @param name the edge's name
     * @return the names of the edges it holds when it is claimed
     */
    private static Set<String> lockNames(Layout built, String name)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Edge locked : edge(built, name).getLockEdges()) out.add(locked.getName());

        return out;
    }

    /**
     * @param names the candidates
     * @param found what was actually there
     * @return whether any candidate is in it
     */
    private static boolean anyOf(String[] names, Set<String> found)
    {
        for (String name : names)
        {
            if (found.contains(name)) return true;
        }

        return false;
    }

    /**
     * @param covered the covered set
     * @return its edge names
     */
    private static Set<String> coveredNames(Map<Edge, Locomotive> covered)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Edge edge : covered.keySet()) out.add(edge.getName());

        return out;
    }

    /**
     * @param built the railway
     * @return every edge name
     */
    private static Set<String> edgeNames(Layout built)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Edge edge : built.getEdges()) out.add(edge.getName());

        return out;
    }

    /**
     * @param built the railway
     * @return every Point name
     */
    private static Set<String> pointNames(Layout built)
    {
        Set<String> out = new LinkedHashSet<>();

        for (Point point : built.getPoints()) out.add(point.getName());

        return out;
    }
}
