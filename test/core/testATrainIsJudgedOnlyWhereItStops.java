package core;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A train is judged for room where it stops, and not at a square it only passes (Adam, MT-333).
 *
 * Adam, on 75 407 DB refused from Tunnel to BottomMainA at a square it only runs past: "this SHOULD be allowed per the
 * standing rule that this switch blocking should only affect berthes."  The standing rule: "make a rule that parking
 * berths cant block any other edges, but not make that check for active stations."  A train passing a square stands
 * across nothing there, so one measured unit past the switch at a square the train only passes does NOT refuse it; the
 * destination and a square the train turns at are still judged.  A STANDING train's mechanic is unchanged and still
 * blocks other roads wherever it is measured.
 *
 * The room rule is Adam's ruling that "will the train fit" means the whole route: "This should only apply if lengths
 * are specified - and edges are already locked as trains pass through in non-dynamic mode.  So it's really about
 * implementing the same mechanic."  It is asked of every PREFIX of the route, so the answer at square N is "if the
 * train stood here, would its tail be clear of the switch behind it", and the destination check is the last iteration
 * rather than a separate rule.
 *
 * **Only where lengths are specified:** unmeasured is unknown rather than zero, and unknown is not a refusal.
 *
 * **The route runs from `BranchPlatform` to `WestEnd`**, the direction in which the switch comes FIRST: a short stretch
 * just past it, and ten units of berth beyond.  The other way there is no switch behind Approach, so nothing bounds it.
 *
 * `regression.testAPassingTrainMayStandAcrossThePoints` and
 * `core.testTheLengthGuardsOnTheRealLayout.testRampDownIsOfferedPastTheOneUnitItOnlyPasses` are the same ruling on
 * Adam's own railway.
 *
 * @author Adam
 */
public class testATrainIsJudgedOnlyWhereItStops
{
    private static support.Scenario scenario;

    private static MarklinLocomotive train;

    /** The probe train's name. */
    private static final String TRAIN = "route length probe";

    /**
     * Its address, chosen so that a stray one left behind by a crash is identifiable: nobody else's, as addresses are
     * not unique here.
     */
    private static final int ADDRESS = 63;

    /** How long the train is in every test here, so the numbers below can be read against one. */
    private static final int TRAIN_LENGTH = 5;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111).
        scenario = support.Scenario.open("single-switch");

        scenario.getModel().stop();

        train = scenario.getModel().newMM2Locomotive(TRAIN, ADDRESS);

        assertNotNull(train, "the class could not create its own train, so every claim below would be"
            + " about whatever the fixture happened to contain");

        train.setTrainLength(TRAIN_LENGTH);
    }

    /** Takes the class's train off the database and puts the layout preference back. */
    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null)
        {
            try
            {
                scenario.getModel().deleteLoc(TRAIN);
            }
            catch (Exception alreadyGone)
            {
            }

            scenario.close();
        }
    }

    /**
     * The fixture really does put one measured unit just past the switch and ten at the far end.  Asserted first and on
     * its own: if the stretch past the switch ever grew long enough to hold the train, the refusal would stop happening
     * and the reason would not be the guard.
     */
    @Test
    public void testTheFixtureIsTightPastTheSwitchAndRoomyAtTheEnd() throws Exception
    {
        oneUnitPastTheSwitchAndTenAtWestEnd();

        List<Edge> route = theRouteFromBranchPlatformToWestEnd();

        assertEquals(route.size(), 2,
            "the route from BranchPlatform to WestEnd is " + names(route) + " rather than the two"
            + " edges this class was measured on, so the numbers below are read off different track");

        assertTrue(route.get(0).crossesASwitch(),
            "the first edge - BranchPlatform to Approach - does not cross the switch, so nothing"
            + " bounds Approach and the rule is right to say nothing about it. That is a different"
            + " fixture from the one this class asserts on");

        assertEquals(route.get(0).getRoomAtTheEnd(), 1,
            "the stretch between the switch and Approach measures " + route.get(0).getRoomAtTheEnd()
            + " units rather than the one this class sets, so the train is not too long for it and"
            + " the refusal below would be testing nothing");

        Integer atTheBerth = Layout.measuredRoomAtTheEndOf(route, train);

        assertNotNull(atTheBerth, "the berth at WestEnd is not measured at all, so the refusal below"
            + " could be the guard declining to judge rather than the route rule");

        assertTrue(atTheBerth >= TRAIN_LENGTH,
            "WestEnd has " + atTheBerth + " units of room, which does not hold the " + TRAIN_LENGTH
            + "-unit train - so the refusal below could be the OLD rule refusing at the destination,"
            + " and this class would prove nothing about the route");
    }

    /**
     * One measured unit past the switch at a square the train only passes does not refuse it (MT-333): Approach is not
     * where the train stops, and WestEnd has ten units of room.
     *
     * MUTATION: judge a passed square again - drop the `if (!comesToRest) continue;` in `Layout.whyTooLongForThisRoute`
     * - and WestEnd is refused and this fails.
     */
    @Test
    public void testATightStretchTheTrainOnlyPassesDoesNotRefuseIt() throws Exception
    {
        oneUnitPastTheSwitchAndTenAtWestEnd();

        assertTrue(destinationsFrom("BranchPlatform").contains("WestEnd"),
            "WestEnd is refused to a " + TRAIN_LENGTH + "-unit train because its route passes one measured unit"
            + " at Approach, where it does not stop.  Adam, MT-333: 'this switch blocking should only affect"
            + " berthes'");

        assertNull(Layout.whyTooLongForThisRoute(theRouteFromBranchPlatformToWestEnd(), train),
            "the room rule refuses the route to WestEnd: '"
            + Layout.whyTooLongForThisRoute(theRouteFromBranchPlatformToWestEnd(), train) + "'");
    }

    /**
     * A train that fits the tight stretch is still offered the berth beyond it: the control showing the refusal is
     * about the train's length, not about that square being on the route.
     */
    @Test
    public void testAShorterTrainIsStillOfferedTheSameBerth() throws Exception
    {
        oneUnitPastTheSwitchAndTenAtWestEnd();

        train.setTrainLength(1);

        try
        {
            assertTrue(destinationsFrom("BranchPlatform").contains("WestEnd"),
                "a one-unit train is refused a route across one measured unit, so the rule is not"
                + " comparing the train against the room - it is refusing the square");

            assertNull(Layout.whyTooLongForThisRoute(theRouteFromBranchPlatformToWestEnd(), train),
                "the rule refuses a train that exactly fits every stretch on its route, so the"
                + " comparison is >= where Adam's is >");
        }
        finally
        {
            train.setTrainLength(TRAIN_LENGTH);
        }
    }

    /**
     * One unit between the switch and Approach, ten at the WestEnd end; the END of an edge counts towards its length.
     */
    private void oneUnitPastTheSwitchAndTenAtWestEnd()
    {
        clearEveryLength();

        // Just past the switch at (5,3), going west: (4,3) is the stretch, (3,3) is Approach itself.
        scenario.getSession().setTileLength(scenario.tile(4, 3), 1);

        // And a roomy berth beyond it: (2,3) plus the end square (1,3).
        scenario.getSession().setTileLength(scenario.tile(2, 3), 10);
        scenario.getSession().setTileLength(scenario.tile(1, 3), 10);
    }

    /** Every square back to unmeasured, so no test inherits another's numbers. */
    private void clearEveryLength()
    {
        for (TileKey tile : everyTile()) scenario.getSession().setTileLength(tile, 0);
    }

    /**
     * Every square this fixture is made of, collected first: `setTileLength` rebuilds the reducer, which would
     * re-derive the graph under the iterator.
     */
    private Set<TileKey> everyTile()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        out.addAll(scenario.getSession().getReducer().getPoints().keySet());

        for (org.traincontrol.automationui.GraphReducer.ReducedEdge edge
            : scenario.getSession().getReducer().getEdges())
        {
            for (org.traincontrol.automationui.GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) out.add(step.getTile());
            }
        }

        return out;
    }

    /**
     * The route the claims here are about, taken from `bfs` rather than from what the railway offers, which is the
     * thing under test.
     */
    private List<Edge> theRouteFromBranchPlatformToWestEnd() throws Exception
    {
        Layout built = scenario.build();

        Point from = built.getPoint("BranchPlatform");
        Point to = built.getPoint("WestEnd");

        assertNotNull(from, "there is no square called BranchPlatform on this railway");
        assertNotNull(to, "there is no square called WestEnd on this railway");

        List<Edge> route = built.bfs(from, to, new LinkedList<List<Edge>>());

        assertNotNull(route, "there is no route at all from BranchPlatform to WestEnd, so this class"
            + " is asserting about track that does not connect");

        return route;
    }

    /**
     * Puts the train on a named square and asks the railway where it may go: the names of every destination offered.
     */
    private Set<String> destinationsFrom(String start) throws Exception
    {
        Layout built = scenario.build();

        Point from = built.getPoint(start);

        assertNotNull(from, "there is no square called \"" + start + "\" on this railway, so the claim"
            + " that names it is asserting about nothing");

        // OFF WHEREVER IT WAS FIRST: moveLocomotive fills the target without emptying the old square
        // when the two are different Points, and a train recorded twice blocks twice the track.
        for (Point point : built.getPoints())
        {
            if (train.equals(point.getCurrentLocomotive()))
            {
                built.moveLocomotive(null, point.getName(), true);
            }
        }

        built.moveLocomotive(train.getName(), from.getName(), false);

        Set<String> out = new LinkedHashSet<>();

        List<List<Edge>> paths = built.getPossiblePaths(train, true);

        if (paths == null) return out;

        for (List<Edge> path : paths)
        {
            if (!path.isEmpty()) out.add(path.get(path.size() - 1).getEnd().getName());
        }

        return out;
    }

    /** The names of a route's squares, for a failure message. */
    private String names(List<Edge> route)
    {
        StringBuilder out = new StringBuilder();

        for (Edge edge : route) out.append(" ").append(edge.getName());

        return out.toString();
    }
}
