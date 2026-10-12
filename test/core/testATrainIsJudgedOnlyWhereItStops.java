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
 * A train is judged for room where it stops, and not at a square it only passes (Adam, MT-333, 2026-09-14).
 *
 * **THIS CLASS USED TO CLAIM MORE**, as `testATrainMustFitEverySquareOnItsRoute`, and the history is kept
 * below because it is the reason the fixture is shaped as it is.  Adam, 2026-09-14, on 75 407 DB refused from
 * Tunnel to BottomMainA at a square it only runs past: *"this SHOULD be allowed per the standing rule that this
 * switch blocking should only affect berthes."*  The standing rule is his of 2026-09-12 - *"make a rule that
 * parking berths cant block any other edges, but not make that check for active stations"* - and a train passing
 * a square stands across nothing there.  So the ruling below is read as meant - its mechanic is a STANDING train's,
 * which is unchanged and still blocks other roads wherever it is measured - and, Adam confirming it with no separate
 * rule for a running train (*"This is all correct as stated ... No separate rule."*), the same
 * railway now claims the reverse: one measured unit past the switch at a square the train only passes does NOT
 * refuse it.  The destination and a square the train turns at are still judged.
 *
 * `regression.testAPassingTrainMayStandAcrossThePoints` is the same ruling on Adam's own railway.
 *
 * What follows is the class as it was written for the ruling of 2026-09-09.
 *
 * **Adam's ruling of 2026-09-09**, answering the question of what "will the train fit" means, put to
 * him as a choice between the destination only and the whole route:
 *
 * > *"For 1, it's b.  This should only apply if lengths are specified - and edges are already locked as
 * > trains pass through in non-dynamic mode.  So it's really about implementing the same mechanic."*
 *
 * Until then the rule was asked once, of the last edge: `measuredRoomAtTheEndOf` walked back from the
 * berth to the last switch and nothing before that was judged.  So a route could cross a measured
 * stretch far too short for the train and still be offered, which is what he reported on his own
 * railway - *"technically incorrect to say there is a path since we pass the track of length 1 at 22,7
 * to get there"*.
 *
 * **The same mechanic, at every square.**  Nothing new is measured and no new walk was written: the
 * rule is asked of every PREFIX of the route, so the answer at square N is "if the train stood here,
 * would its tail be clear of the switch behind it".  That makes the destination check the last
 * iteration of the loop rather than a separate rule.
 *
 * **With one condition, as it then was (removed 2026-09-14 with the pass-through check itself, and its claim
 * with it - WK7-C3).**  A square the train passes THROUGH was judged only where a switch bounds it.  The walk has a second
 * stopping condition - the start of the path - and at the destination that is harmless, because the
 * train comes to rest there and lies back over the route.  At a pass-through square it measures
 * nothing: a two-edge prefix answers "two edges of room" when the honest answer is that the track
 * behind where the train started has not been looked at and the train is standing on it.  The first
 * cut of this change did not have that condition and the battery came back with a four-unit train
 * refused four units of room.  It lived in `Layout.roomAfterASwitchOnTheWay`, removed with the pass-through
 * check on 2026-09-14.
 *
 * **Only where lengths are specified**, which is the other half of his ruling and was already the
 * doctrine of the walk: unmeasured is unknown rather than zero, and unknown is not a refusal - *"an
 * unmeasured run in: generally, allow it"*.
 *
 * **The route this asserts on runs the OTHER way round the fixture**, from `BranchPlatform` to
 * `WestEnd`, because that is the direction in which the switch comes FIRST: a short stretch just past
 * it, and ten units of berth beyond that.  Going the other way there is no switch behind Approach at
 * all, so nothing bounds it and nothing should be refused - which was a claim in its own right,
 * `testASquareWithNoSwitchBehindItIsNotJudged`, until no passed square was judged at all and it could no longer
 * fail (WK7-C3).
 *
 * `core.testTheLengthGuardsOnTheRealLayout.testRampDownIsOfferedPastTheOneUnitItOnlyPasses` - until 2026-09-14
 * `testWhyRampDownIsRefused` - is the same ruling measured on Adam's own layout, and it is the test this
 * change turned round.
 *
 * @author Adam
 */
public class testATrainIsJudgedOnlyWhereItStops
{
    private static support.Scenario scenario;

    private static MarklinLocomotive train;

    /**
     * What the train is called, and it says what it is for.
     */
    private static final String TRAIN = "route length probe";

    /**
     * Its address, chosen only so that a stray one left behind by a crash is identifiable.
     *
     * Addresses are not unique in this database and several test classes already share one, so this
     * needs to be nobody else's rather than free.
     */
    private static final int ADDRESS = 63;

    /**
     * How long the train is in every test here, so that the numbers below can be read against one.
     */
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

    /**
     * Takes the class's train off the database and puts the layout preference back.
     */
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
     * The fixture really does put one measured unit just past the switch and ten at the far end.
     *
     * Asserted first and on its own, because everything below is about the difference between those
     * two numbers.  If the stretch past the switch ever grew long enough to hold the train, the
     * refusal would stop happening and the reason would not be the guard.
     *
     * @throws Exception on a failure to build
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
     * One measured unit past the switch at a square the train only passes does not refuse it (MT-333).
     *
     * What this claim said until 2026-09-14 was the other way round.  Approach is not where the train stops: it runs
     * through it to WestEnd, which has ten units of room.
     *
     * MUTATION: judge a passed square again - drop the `if (!comesToRest) continue;` in
     * `Layout.whyTooLongForThisRoute` - and WestEnd is refused and this fails.
     *
     * @throws Exception on a failure to build
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
     * A train that fits the tight stretch is still offered the berth beyond it.
     *
     * The other control, and the one that says the refusal is about the train's length rather than
     * about that square being on the route at all.  Same railway, same lengths, a shorter train.
     *
     * @throws Exception on a failure to build
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
     * One unit between the switch and Approach, ten at the WestEnd end.
     *
     * The END of an edge counts towards its length, so a length on a platform is part of the room a
     * train coming to rest there has.
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

    /**
     * Every square back to unmeasured, so no test inherits another's numbers.
     */
    private void clearEveryLength()
    {
        for (TileKey tile : everyTile()) scenario.getSession().setTileLength(tile, 0);
    }

    /**
     * Every square this fixture is made of, collected before anything is written to it.
     *
     * `setTileLength` rebuilds the reducer, so walking its own collections while writing to them
     * re-derives the graph under the iterator.
     *
     * @return the tiles, in the order the reducer holds them
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
     * The route the claims here are about, taken from the railway rather than assumed.
     *
     * From `bfs` rather than from what the railway offers, because what it offers is the thing under
     * test - asking it for the route would make every assertion conditional on the refusal not
     * happening.
     *
     * @return the edges, in order
     * @throws Exception on a failure to build
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
     * Puts the train on a named square and asks the railway where it may go.
     *
     * @param start the square to stand it on
     * @return the names of every destination offered, empty when none is
     * @throws Exception on a failure to build
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

    /**
     * The names of a route's squares, for a failure message.
     *
     * @param route the edges
     * @return the squares it runs through, in order
     */
    private String names(List<Edge> route)
    {
        StringBuilder out = new StringBuilder();

        for (Edge edge : route) out.append(" ").append(edge.getName());

        return out.toString();
    }
}
