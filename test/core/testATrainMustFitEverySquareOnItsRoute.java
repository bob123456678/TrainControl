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
 * A train must fit at every square its route runs through, not only at the one it stops at.
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
 * **With one condition, and it is the whole of the difference between a rule and an artefact.**  A
 * square the train passes THROUGH is judged only where a switch bounds it.  The walk has a second
 * stopping condition - the start of the path - and at the destination that is harmless, because the
 * train comes to rest there and lies back over the route.  At a pass-through square it measures
 * nothing: a two-edge prefix answers "two edges of room" when the honest answer is that the track
 * behind where the train started has not been looked at and the train is standing on it.  The first
 * cut of this change did not have that condition and the battery came back with a four-unit train
 * refused four units of room.  `Layout.roomAfterASwitchOnTheWay` is where it lives.
 *
 * **Only where lengths are specified**, which is the other half of his ruling and was already the
 * doctrine of the walk: unmeasured is unknown rather than zero, and unknown is not a refusal - *"an
 * unmeasured run in: generally, allow it"*.
 *
 * **The route this asserts on runs the OTHER way round the fixture**, from `BranchPlatform` to
 * `WestEnd`, because that is the direction in which the switch comes FIRST: a short stretch just past
 * it, and ten units of berth beyond that.  Going the other way there is no switch behind Approach at
 * all, so nothing bounds it and nothing should be refused - which is a claim in its own right, and
 * `testASquareWithNoSwitchBehindItIsNotJudged` is it.
 *
 * `core.testTheLengthGuardsOnTheRealLayout.testWhyRampDownIsRefused` is the same ruling measured on
 * Adam's own layout, and it is the test this change turned round.
 *
 * @author Adam
 */
public class testATrainMustFitEverySquareOnItsRoute
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
     * The refusal itself: room enough at the berth, not enough past the switch, so the route is out.
     *
     * This is Adam's ruling in one assertion.  Before it the destination was the only square asked
     * about, and this route was offered.
     *
     * MUTATION: asking `measuredRoomAtTheEndOf` of the whole path instead of each prefix - which is
     * what the rule did until 2026-09-09 - offers WestEnd again and fails this.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testARoomyBerthDoesNotExcuseATightStretchPastTheSwitch() throws Exception
    {
        oneUnitPastTheSwitchAndTenAtWestEnd();

        assertFalse(destinationsFrom("BranchPlatform").contains("WestEnd"),
            "WestEnd is still offered to a " + TRAIN_LENGTH + "-unit train whose route crosses one"
            + " measured unit at Approach. The berth has room and the way to it does not, which is the"
            + " case Adam's ruling of 2026-09-09 is about");
    }

    /**
     * And the sentence says which square, not just that something was too long.
     *
     * A refusal naming the destination when the destination is not the problem is worse than no
     * refusal: it sends the operator to measure the one stretch that was already long enough.  Adam,
     * on the berth rule: *"there is no notice that can help state/debug this."*
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheRefusalNamesTheSquareThatIsTooShort() throws Exception
    {
        oneUnitPastTheSwitchAndTenAtWestEnd();

        List<Edge> route = theRouteFromBranchPlatformToWestEnd();

        String why = Layout.whyTooLongForThisRoute(route, train);

        assertNotNull(why, "the route is refused when the railway is asked for destinations and"
            + " accepted when the rule is asked directly, so the two doors disagree");

        assertTrue(why.contains("Approach"),
            "the refusal is \"" + why + "\", which does not name Approach - the square the train does"
            + " not fit at. WestEnd has room and measuring it again will not help anybody");

        assertTrue(why.contains(String.valueOf(TRAIN_LENGTH)),
            "the refusal is \"" + why + "\" and does not carry the length of the train, so there is"
            + " nothing in it to measure against");
    }

    /**
     * A square with no switch behind it is not judged at all, however short the route so far.
     *
     * The condition that separates the rule from the artefact.  Run the same railway the other way -
     * WestEnd to MainPlatform - and Approach has no switch between it and where the train started, so
     * the two measured units before it bound nothing: the track behind the train is track the train is
     * standing on.
     *
     * Without this the first cut of the ruling refused a four-unit train four units of room, and the
     * staging planner gave up on berths it could reach.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testASquareWithNoSwitchBehindItIsNotJudged() throws Exception
    {
        clearEveryLength();

        // Two units before Approach, ten past the switch - the mirror of the measurement above.
        scenario.getSession().setTileLength(scenario.tile(2, 3), 1);
        scenario.getSession().setTileLength(scenario.tile(3, 3), 1);
        scenario.getSession().setTileLength(scenario.tile(6, 3), 10);
        scenario.getSession().setTileLength(scenario.tile(7, 3), 10);

        Layout built = scenario.build();

        Point from = built.getPoint("WestEnd");
        Point to = built.getPoint("Approach (eastbound)");

        assertNotNull(from, "there is no square called WestEnd on this railway");
        assertNotNull(to, "there is no square called \"Approach (eastbound)\" on this railway");

        List<Edge> route = built.bfs(from, to, new LinkedList<List<Edge>>());

        assertNotNull(route, "there is no route from WestEnd to Approach, so this claim is about track"
            + " that does not connect");

        assertFalse(route.get(0).crossesASwitch(),
            "the run from WestEnd to Approach crosses a switch now, so something DOES bound Approach"
            + " and this claim is about a different railway");

        assertNull(Layout.roomAfterASwitchOnTheWay(route, train),
            "the rule measured " + Layout.roomAfterASwitchOnTheWay(route, train) + " units of room at"
            + " a square with no switch between it and where the train started. Nothing bounds it -"
            + " the track behind the train is track the train is standing on - so the only honest"
            + " answer is that the question cannot be asked here");

        assertTrue(destinationsFrom("WestEnd").contains("MainPlatform"),
            "MainPlatform is refused to a " + TRAIN_LENGTH + "-unit train with ten units of room past"
            + " the switch, because two measured units before an unbounded square were counted as its"
            + " room. That is the artefact this condition exists to remove");
    }

    /**
     * Unmeasured is unknown, not zero: clear the stretch past the switch and the train is welcome.
     *
     * Adam's other half - *"this should only apply if lengths are specified"* - and the strongest form
     * of the control: one railway, one train, one number changed, opposite answers.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testAnUnmeasuredStretchStillAdmitsTheTrain() throws Exception
    {
        oneUnitPastTheSwitchAndTenAtWestEnd();

        Set<String> whenTight = destinationsFrom("BranchPlatform");

        // The one number: the square past the switch goes back to unmeasured.
        scenario.getSession().setTileLength(scenario.tile(4, 3), 0);

        Set<String> whenUnmeasured = destinationsFrom("BranchPlatform");

        assertFalse(whenTight.contains("WestEnd"),
            "the one-unit stretch did not refuse WestEnd, so the comparison below is between two"
            + " railways that both admit it and says nothing");

        assertTrue(whenUnmeasured.contains("WestEnd"),
            "WestEnd is refused on a railway where the stretch past the switch is not measured at all,"
            + " so the rule is refusing on the ABSENCE of a measurement rather than on one - which"
            + " makes every unmeasured layout unusable and is the failure the walk's own comment says"
            + " was removed once already");
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
