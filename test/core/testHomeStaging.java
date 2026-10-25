package core;

import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Edge;
import org.traincontrol.base.Locomotive;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * Home stations and the "return to home" planner.
 *
 * Two things are under test and they fail differently:
 *
 *  - **Where home comes from.**  A locomotive's home is the station it occupied when it first appeared
 *    on the graph, claimed once and never re-claimed.  The map is injective by construction - a station
 *    can be claimed by only one locomotive - because two locomotives wanting one station would make
 *    returning home unsatisfiable no matter how good the planner is.  A locomotive placed on a station
 *    already claimed gets no home and becomes a free agent.
 *  - **The plan.**  Getting everyone home is a rearrangement problem: a station holds one locomotive,
 *    so a train cannot go home while another sits there.  The planner works on a copy of the occupancy,
 *    never on live state, because "could this move happen after three other moves" has no answer on
 *    live state.
 *
 * Plans are checked by *applying* them - each move is replayed against the model and the target
 * asserted free beforehand - rather than by inspecting the move list.  A plan that reads plausibly and
 * routes a train into an occupied station is exactly the failure worth catching, and only replay
 * catches it.
 */
public class testHomeStaging
{
    private static MarklinControlStation model;

    /** Feedback range picked clear of the other suites (88xx is shared, so these sit at the top). */
    private static final int S88_BASE = 8890;

    private static final String LOC_A = "HS alpha";
    private static final String LOC_B = "HS bravo";
    private static final String LOC_C = "HS charlie";

    private static final String[] TEST_LOCS = { LOC_A, LOC_B, LOC_C };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();
        model.stop();

        for (int i = 0; i < TEST_LOCS.length; i++)
        {
            model.newMM2Locomotive(TEST_LOCS[i], 81 + i);
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        for (String name : TEST_LOCS)
        {
            if (model != null) model.deleteLoc(name);
        }
    }

    // Fixtures

    private static String json(String s)
    {
        return s.replace('\'', '"');
    }

    /**
     * Four stations in a ring, each edge present in both directions, so every station can reach every
     * other and the graph itself never makes a plan impossible.  Which locomotives start where is the
     * only variable.
     */
    private static String ring(String locAtA, String locAtB, String locAtC)
    {
        return json("{'points': ["
            + station("HS A", 0, locAtA) + ","
            + station("HS B", 1, locAtB) + ","
            + station("HS C", 2, locAtC) + ","
            + station("HS D", 3, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("HS C", "HS D") + "," + edge("HS D", "HS C") + ","
            + edge("HS D", "HS A") + "," + edge("HS A", "HS D")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * Loading a configuration leaves alone what its placements do not carry.
     *
     * `captureFromLayout` writes a placement as `{"name": X}` only, so `parseAuto` must treat an absent key as
     * "not stated", not as an instruction to clear train length, reversible and the function slots in the live
     * `Locomotive`.  The fixture is the ordinary one: `station()` emits the name-only placement the editor writes.
     *
     * MUTATION: restoring any of the four `else` clauses in `parseAuto` (`setTrainLength(0)`,
     * `setReversible(false)`, `setDepartureFunc(null)`, `setArrivalFunc(null)`).
     */
    @Test
    public void testLoadingDoesNotClearWhatThePlacementDoesNotCarry()
    {
        MarklinLocomotive standing = loc(LOC_A);

        Integer lengthWas = standing.getTrainLength();
        boolean reversibleWas = standing.isReversible();
        Integer arrivalWas = standing.getArrivalFunc();
        Integer departureWas = standing.getDepartureFunc();

        try
        {
            // What the locomotive window would have set.
            standing.setTrainLength(12);
            standing.setReversible(true);
            standing.setArrivalFunc(3);
            standing.setDepartureFunc(4);

            // And what the diagram writes: a name, and nothing else.
            Layout layout = load(ring(LOC_A, null, null));

            assertNotNull(layout.getLocomotiveLocation(standing),
                "precondition: the fixture must actually place this locomotive, or the load never "
                + "reaches the code under test");

            assertEquals(standing.getTrainLength(), Integer.valueOf(12),
                "loading a configuration cleared the train length.  The placement carries only a name "
                + "- which is what the editor writes - and an absent key is not an instruction to "
                + "forget the number somebody typed.  With it go the tail-clear guard and the "
                + "reverse-over-switch guard, both of which decline to judge a train of no length");

            assertTrue(standing.isReversible(),
                "loading a configuration made a reversible locomotive non-reversible, so mustBackIn "
                + "will refuse it a terminus and pickPath will plan it the long way round");

            assertEquals(standing.getArrivalFunc(), Integer.valueOf(3),
                "loading a configuration cleared the arrival function, so it stops firing and nothing "
                + "says it has gone");

            assertEquals(standing.getDepartureFunc(), Integer.valueOf(4),
                "loading a configuration cleared the departure function");
        }
        finally
        {
            standing.setTrainLength(lengthWas);
            standing.setReversible(reversibleWas);
            standing.setArrivalFunc(arrivalWas);
            standing.setDepartureFunc(departureWas);
        }
    }

    /**
     * The planner asks the same question about covered track that the runtime does (OB-184).
     *
     * Adam: **"The home planner does not consider blocks due to the length of a train, i.e. a train
     * that blocks edges behind it."**
     *
     * A pairing test, not a journey: a tail only extends along a LINEAR stretch, and the real case (a tail across a
     * SWITCH, reached through lock edges) cannot be built from this suite's hand-made fixtures.  So this pins that
     * the planner consults the covered set the way `Layout.isPathClear` does: symmetric lock partners only, because
     * one-directional travel restrictions share that collection and sweeping them refuses clear track.
     *
     * MUTATION: removing either half from `passesTheTailsOfTrainsThatHaveNotMoved`, or dropping its call
     * from the route search, fails this.
     */
    @Test
    public void testThePlannerAsksTheRuntimeQuestionAboutCoveredTrack() throws Exception
    {
        // ONE TRAIN AT A TIME: the single map records one train per place, so the planner reads each train's own
        // record.
        assertTrue(SourceOf.declaration("private HomeStaging(").contains("layout.tailsOfEachStandingTrain()"),
            "the planner no longer asks which track the trains are lying across, so it routes through it"
            + " and the runtime refuses the plan on its first move (OB-184)");

        assertTrue(SourceOf.declaration("private List<Edge> firstClearRoute(")
            .contains("passesTheTailsOfTrainsThatHaveNotMoved(e, loc, state)"),
            "the route search does not consult the covered track, so the rule exists and nothing calls"
            + " it - which is what a half-finished refactor leaves behind");

        String atStart = SourceOf.declaration("private static boolean liesAcrossAtStart(");

        assertTrue(atStart.contains("sharing.getLockEdges().contains(edge)"),
            "the planner sweeps lock edges without checking they are SYMMETRIC. Sharing a tile is"
            + " mutual; the builder's travel restrictions live in the same collection and are"
            + " one-directional, so sweeping both refuses routes over physically clear track and blames"
            + " a train nowhere near it (RGD-B2)");

        // AND THE RUNTIME STILL ASKS IT THE SAME WAY.  The pairing is the point: if `isPathClear` moves
        // to a different rule, this planner silently goes back to disagreeing with it.
        String layout = SourceOf.declaration(
            "private boolean isPathClear(List<Edge> path, Locomotive loc, boolean logFailures, boolean byHand)");

        assertTrue(layout.contains("if (!sharing.getLockEdges().contains(e)) continue;"),
            "the runtime's covered-track rule has changed shape, so the planner above is now mirroring"
            + " a rule that no longer exists - which is how these two came to disagree in the first"
            + " place");

        // AND THE BY-PLACE QUESTION, on both sides: the runtime refuses every copy of a rail a tail lies
        // across, and a planner that asked only the Edge would plan onto the turning copy the runtime then refuses.
        assertTrue(layout.contains("lyingAcross = anotherTailOn(e, loc, coveredPlaces);"),
            "the runtime no longer asks whether another train's tail lies on the metal an edge runs over (AUT-B1)");

        assertTrue(SourceOf.declaration("private boolean passesTheTailsOfTrainsItHasMoved(")
            .contains("if (Layout.anotherTailOn(edge, mover, places) != null) return false;"),
            "the planner's check of the tails its own moves leave does not ask by place, so it plans onto a copy of"
            + " the rail the runtime refuses (AUT-B1)");

        // EVERY TRAIN on those places, not the first, each asked against its own record.
        assertTrue(atStart.contains("return Layout.tailLiesOn(edge, train, places);"),
            "the planner's check of the tails of trains that have not moved does not ask by place, for every train"
            + " lying there (AUT-B1, AUT2-C2)");
    }    /**
     * And a placement that DOES carry a value still applies it: a legacy `autonomy.json` has these keys, and
     * importing one has to keep meaning what it meant.
     */
    @Test
    public void testAPlacementThatCarriesAValueStillSetsIt()
    {
        MarklinLocomotive standing = loc(LOC_A);

        Integer lengthWas = standing.getTrainLength();
        boolean reversibleWas = standing.isReversible();

        try
        {
            standing.setTrainLength(12);
            standing.setReversible(true);

            model.parseAuto(json("{'points': ["
                + "{'name': 'HS A', 'station': true, 's88': " + (S88_BASE + 1)
                + ", 'loc': {'name': '" + LOC_A + "', 'trainLength': 5, 'reversible': false}},"
                + station("HS B", 2, null)
                + "], 'edges': [" + edge("HS A", "HS B") + "," + edge("HS B", "HS A")
                + "], 'minDelay': 0, 'maxDelay': 0, 'defaultLocSpeed': 30}"));

            assertTrue(model.getAutoLayout().isValid(),
                "precondition: the graph must parse - " + Layout.getLastError());

            assertEquals(standing.getTrainLength(), Integer.valueOf(5),
                "a placement that states a train length no longer applies it, so importing a legacy "
                + "autonomy.json has stopped meaning what it meant");

            assertFalse(standing.isReversible(),
                "a placement that states reversibility no longer applies it");
        }
        finally
        {
            standing.setTrainLength(lengthWas);
            standing.setReversible(reversibleWas);
        }
    }

    private static String station(String name, int s88Offset, String loc)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (S88_BASE + s88Offset)
            + (loc == null ? "" : ", 'loc': {'name': '" + loc + "'}") + "}";
    }

    private static String edge(String from, String to)
    {
        return "{'start': '" + from + "', 'end': '" + to + "'}";
    }

    /**
     * The ring with HS D assigned to a named locomotive (which need not exist).  Rewrites station()'s output and
     * asserts the rewrite landed, so a fixture change cannot yield a config with no assignment.
     */
    private static String ringAssigning(String homeAtD)
    {
        String raw = station("HS D", 3, null);
        String plain = json(raw);
        String assigned = json(raw.substring(0, raw.length() - 1) + ", 'home': '" + homeAtD + "'}");

        String config = ring(LOC_A, null, null);

        assertTrue(config.contains(plain), "precondition: the ring fixture must still emit HS D plainly");

        return config.replace(plain, assigned);
    }

    /**
     * The audit speaks when the runtime refuses a berth for want of room and the planner does not.
     *
     * `HomeStaging` carries its own copy of the runtime's rules and never reads `getLength()`.  The assertion is
     * deliberately weak: not that the planner must carry the rule, only that the two must not silently disagree,
     * which `auditAgainstRuntime` notices.  Fixture: a ring whose HS D is a terminus over an edge measured 5, with
     * a train 40 long homed there.
     *
     * MUTATION: taking the length off the edge, or the train length off the locomotive, makes the runtime stop
     * refusing and the audit fall back to zero; both are asserted as preconditions.
     */
    @Test
    public void testThePlannerAndTheRuntimeAgreeAboutRoomToReverse() throws Exception
    {
        Layout layout = load(shortBerth());

        Locomotive tooLong = loc(LOC_A);

        // PUT BACK AFTERWARDS: the other tests share these locomotives, and a hardcoded true would hand them one
        // this test had made reversible.
        Integer lengthWas = tooLong.getTrainLength();
        boolean reversibleWas = tooLong.isReversible();

        try
        {

        tooLong.setTrainLength(40);

        // REVERSIBLE, which makes the fixture discriminate: a non-reversible train must arrive at a terminus already
        // turned (`mustBackIn`) and this ring has no reversing point, so HS D would be refused whatever the length.
        tooLong.setReversible(true);

        // Not a precondition on what the lines above set (asserting a setter's value proves only that it sets):
        // this asserts what the fixture has to be for the claim to mean anything.
        assertTrue(layout.getEdge("HS A", "HS D").getLength() < tooLong.getTrainLength(),
            "precondition: the approach to HS D is not shorter than the train, so nothing refuses it "
            + "and this fixture cannot show anything");

        // No precondition about mustBackIn: it never looks at the layout, so a railway without a reversing point is
        // where it IS the alternative explanation.  What rules that out is the CONTROL below, a train that FITS: with
        // the room rule deleted a 40-unit train is still refused by mustBackIn and the claim stays green.

        assertTrue(layout.getPoint("HS D").isTerminus(),
            "precondition: HS D must be a terminus - the rule only applies where the train reverses");

        assertTrue(layout.getEdge("HS A", "HS D") != null
                && layout.getEdge("HS A", "HS D").getLength() > 0
                && layout.getEdge("HS A", "HS D").getLength() < 40,
            "precondition: the approach to HS D must be MEASURED and shorter than the train, or the "
            + "guard declines to judge it at all.  Got: "
            + (layout.getEdge("HS A", "HS D") == null
                ? "no edge" : layout.getEdge("HS A", "HS D").getLength()));

        // THE RUNTIME REFUSES IT, which is the half that exists.
        assertFalse(layout.isPathClear(layout.bfs(layout.getPoint("HS A"),
            layout.getPoint("HS D"), null), tooLong),
            "control: the runtime does not refuse a train too long for the berth it would reverse "
            + "into, so there is no divergence for the audit to find and this test proves nothing");

        // AND THE PLANNER REFUSES IT TOO, so the audit is silent.
        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "the runtime refuses this berth for want of room to reverse and the staging planner "
            + "still offers it.  The planner re-implements isPathClear's rules by hand, and this is "
            + "the rule it did not get - so Return Home can produce a plan whose first move the "
            + "railway then refuses");

        // And it shows up as the planner having nowhere to send the train, rather than as a plan.
        assertFalse(layout.planReturnToHome().isPossible(),
            "the planner produced a plan to a berth the runtime will refuse on the first move.  "
            + "Outcome: " + layout.planReturnToHome().getOutcome());

        // THE CONTROL: a three-unit train fits the five-unit approach.  Without it the test passes with the room
        // rule deleted altogether, because a 40-unit train is refused anyway.
        tooLong.setTrainLength(3);

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "control: with a train that fits, the planner and the runtime still disagree - so the "
            + "disagreement above is not about room and this test is measuring something else");

        assertTrue(layout.planReturnToHome().isPossible(),
            "control: a three-unit train cannot reach a berth reached over five measured units, so "
            + "something other than room is refusing it and the assertions above prove nothing about "
            + "the room rule.  Outcome: " + layout.planReturnToHome().getOutcome());

        }
        finally
        {
            tooLong.setTrainLength(lengthWas);
            tooLong.setReversible(reversibleWas);
        }
    }

    /**
     * A berth reached two ways: the direct approach (5 from HS A) is too short for the 40-unit train homed at
     * HS D; the way round through HS B and HS C (60) is long enough.  Every edge is measured, since an unmeasured
     * segment makes the rule decline to judge and the planner would route the long way for another reason.
     */
    private static String twoApproaches()
    {
        return json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + station("HS C", 2, null) + ","
            + "{'name': 'HS D', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'terminus': true, 'home': '" + LOC_A + "'}"
            + "],'edges': ["
            + "{'start': 'HS A', 'end': 'HS B', 'length': 60},"
            + "{'start': 'HS B', 'end': 'HS A', 'length': 60},"
            + "{'start': 'HS B', 'end': 'HS C', 'length': 60},"
            + "{'start': 'HS C', 'end': 'HS B', 'length': 60},"
            // THE WAY ROUND, long enough for the train.
            + "{'start': 'HS C', 'end': 'HS D', 'length': 60},"
            + "{'start': 'HS D', 'end': 'HS C', 'length': 60},"
            // AND THE DIRECT ONE, which is not.
            + "{'start': 'HS A', 'end': 'HS D', 'length': 5},"
            + "{'start': 'HS D', 'end': 'HS A', 'length': 5}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * `continue` and `return null` are the whole difference between "this approach is too short, keep looking"
     * and "this berth is refused".  Unlike the room-to-reverse agreement test (every route too short, planner and
     * runtime must not disagree), ONE route is too short here and the planner must find the other.
     *
     * MUTATION: turn the `continue` into `return null` and the plan becomes impossible.
     */
    @Test
    public void testALongerApproachIsMoreRoom() throws Exception
    {
        Layout layout = load(twoApproaches());

        Locomotive train = loc(LOC_A);

        // PUT BACK AFTERWARDS: these locomotives are shared with eighty-odd other tests.
        Integer lengthWas = train.getTrainLength();
        boolean reversibleWas = train.isReversible();

        try
        {
            train.setTrainLength(40);
            train.setReversible(true);

            assertEquals(train.getTrainLength(), Integer.valueOf(40),
                "precondition: the train has to be longer than the direct approach, or nothing here "
                + "is about room at all");

            HomeStaging.Plan plan = layout.planReturnToHome();

            assertTrue(plan.isPossible(),
                "the berth was refused because ONE approach to it is too short.  The rule is a "
                + "`continue` - another route to the same berth may be longer, and a longer approach "
                + "is more room - and a `return null` there gives up on the berth entirely.  Outcome "
                + "was " + plan.getOutcome() + " (TS3-B7)");
        }
        finally
        {
            train.setTrainLength(lengthWas);
            train.setReversible(reversibleWas);
        }
    }
    /**
     * The planner offers a platform whose approach holds the train, as the railway does.
     *
     * Adam: *"it would not fit just past 14,12 ... so we need a clear rule to govern that this is OK, or simply make a
     * rule that parking berths cant block any other edges, but not make that check for active stations."*
     *
     * `Layout.theApproachItselfHoldsIt` is the relaxation and both sides ask it: the runtime in
     * `whyTooLongForThisRoute` (room applies only where the train comes to rest, MT-333) and the planner at its
     * destination test.  A planner stricter than the runtime would leave a train standing with nothing said.
     *
     * Fixture: HS D is a station autonomy may choose, over an edge measured 6 that crosses a switch with 3 beyond
     * it, and the train is 6 long: room after the last switch is 3 and refuses; the approach itself is 6 and allows.
     *
     * MUTATION: dropping `theApproachItselfHoldsIt` from the planner's destination test fails this; dropping it
     * from `whyTooLongForThisRoute` fails the runtime half below.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testThePlannerBuysThePlatformRelaxationToo() throws Exception
    {
        Layout layout = load(platformWithASwitchBehindIt());

        Locomotive train = loc(LOC_A);

        Integer lengthWas = train.getTrainLength();
        boolean reversibleWas = train.isReversible();

        try
        {
            train.setTrainLength(6);
            train.setReversible(true);

            List<Edge> route = new ArrayList<>();

            route.add(layout.getEdge("HS A", "HS D"));

            // THE FIXTURE REALLY IS THE CASE: the room refuses and the approach allows.
            assertEquals(Layout.measuredRoomAtTheEndOf(route, train), Integer.valueOf(3),
                "the room after the last switch is not 3 on this fixture, so nothing refuses this"
                + " arrival and the claim below cannot fail");

            assertTrue(Layout.theApproachItselfHoldsIt(route, train),
                "the approach does not hold the train on this fixture, so there is no relaxation to"
                + " buy and the claim below would pass on a planner that never had one");

            // THE RUNTIME ACCEPTS IT, which is the half Adam ruled on.
            assertNull(Layout.whyTooLongForThisRoute(route, train),
                "the railway refuses a six-unit train at a platform whose approach measures six,"
                + " which is the arrival Adam's relaxation of 2026-09-12 exists to allow: "
                + Layout.whyTooLongForThisRoute(route, train));

            // AND SO DOES THE PLANNER.
            HomeStaging.Plan plan = layout.planReturnToHome();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
                "the planner would not stage a train the railway would have run: " + plan.getOutcome()
                + ". A planner stricter than its own runtime drops the plan silently - the train stays"
                + " where it is and nothing says why - which is what PRW-B2 leg 2 was about.");

            assertFalse(plan.getMoves().isEmpty(),
                "the plan has no moves in it, so \"planned\" says nothing about this arrival");

            applyPlan(layout, plan);
        }
        finally
        {
            train.setTrainLength(lengthWas);
            train.setReversible(reversibleWas);
        }
    }

    /**
     * The planner refuses a home the operator has said is too short for the train.
     *
     * The **Advanced Parameters** maximum train length is a preference, not a measurement, and
     * `HomeStaging.canRest` ends with `at.validateTrainLength(loc)`; no other claim here covers it, since the room
     * and berth rules both measure the track.
     *
     * MUTATION: deleting `at.validateTrainLength(loc)` from `canRest` makes this plan READY, and the runtime then
     * refuses the move it contains.  The control beside it (the limit raised) tells that from an unstageable fixture.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testThePlannerRefusesAHomeTheOperatorSaidIsTooShort() throws Exception
    {
        Layout layout = load(platformWithAStatedLimit());

        Locomotive train = loc(LOC_A);

        Integer lengthWas = train.getTrainLength();
        boolean reversibleWas = train.isReversible();

        try
        {
            train.setTrainLength(6);
            train.setReversible(true);

            List<Edge> route = new ArrayList<>();

            route.add(layout.getEdge("HS A", "HS D"));

            // THE RUNTIME REFUSES IT, and says so in the sentence about the stated length.
            assertNotNull(Layout.whyTooLongForThisRoute(route, train),
                "the railway accepts a six-unit train at a platform the operator limited to four, so"
                + " there is nothing here for the planner to agree with");

            // SO THE PLANNER MUST NOT OFFER IT.
            HomeStaging.Plan plan = layout.planReturnToHome();

            // THE CONTROL, IN THIS TEST: the same railway with the limit raised plans fine, so the refusal is this
            // rule and not an unstageable fixture.
            Layout generous = load(platformWithAStatedLimit()
                .replace("\"maxTrainLength\": 4", "\"maxTrainLength\": 20"));

            assertEquals(generous.planReturnToHome().getOutcome(), HomeStaging.Outcome.READY,
                "the same railway with the operator's limit raised does not plan either, so the"
                + " refusal below says nothing about the limit");

            assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
                "the planner staged a train into a platform the operator said is too short for it."
                + " The railway refuses that move, so the run stops with the fleet half staged and"
                + " nothing to do about it - which is OB-073, from a number he typed rather than from"
                + " a length he measured.");
        }
        finally
        {
            train.setTrainLength(lengthWas);
            train.setReversible(reversibleWas);
        }
    }

    /**
     * A home the operator has limited to four units, reached over track long enough for six: the track says yes
     * and the preference says no.
     *
     * @return the configuration
     */
    private static String platformWithAStatedLimit()
    {
        return json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + "{'name': 'HS D', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'maxTrainLength': 4, 'home': '" + LOC_A + "'}"
            + "],'edges': ["
            + "{'start': 'HS A', 'end': 'HS D', 'length': 20},"
            + "{'start': 'HS D', 'end': 'HS A', 'length': 20},"
            + "{'start': 'HS A', 'end': 'HS B', 'length': 20},"
            + "{'start': 'HS B', 'end': 'HS A', 'length': 20}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A platform reached over a six-unit approach with a switch three units from the end (Adam's figures for the
     * relaxation).
     *
     * @return the configuration
     */
    private static String platformWithASwitchBehindIt()
    {
        return json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + "{'name': 'HS D', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'home': '" + LOC_A + "'}"
            + "],'edges': ["
            // THE ONE THAT MATTERS: measured 6, crossing a switch with 3 beyond it (`roomAtTheEnd` makes
            // `crossesASwitch` true; the room rule and the relaxation read the two numbers).
            + "{'start': 'HS A', 'end': 'HS D', 'length': 6, 'roomAtTheEnd': 3},"
            + "{'start': 'HS D', 'end': 'HS A', 'length': 6, 'roomAtTheEnd': 3},"
            + "{'start': 'HS A', 'end': 'HS B', 'length': 6},"
            + "{'start': 'HS B', 'end': 'HS A', 'length': 6}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * The ring, with HS D a terminus reached over a short measured edge.
     *
     * @return the configuration
     */
    private static String shortBerth()
    {
        return json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + station("HS C", 2, null) + ","
            + "{'name': 'HS D', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'terminus': true, 'home': '" + LOC_A + "'}"
            + "],'edges': ["
            // EVERY EDGE MEASURED, the point of the fixture: with any segment unmeasured the rule declines to judge and
            // the planner routes the long way round, which the runtime accepts for the same reason, so the two agree
            // and the test proves nothing.  Measured throughout, every route to HS D is too short for the train.
            + "{'start': 'HS A', 'end': 'HS B', 'length': 5},"
            + "{'start': 'HS B', 'end': 'HS A', 'length': 5},"
            + "{'start': 'HS B', 'end': 'HS C', 'length': 5},"
            + "{'start': 'HS C', 'end': 'HS B', 'length': 5},"
            + "{'start': 'HS A', 'end': 'HS D', 'length': 5},"
            + "{'start': 'HS D', 'end': 'HS A', 'length': 5}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }


    /** Loads a graph and returns it, asserting it parsed - an invalid graph fails every test below
     *  for reasons that have nothing to do with staging. */
    private static Layout load(String config)
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertTrue(layout.isValid(), "precondition: the test graph must parse - " + Layout.getLastError());

        return layout;
    }

    private static MarklinLocomotive loc(String name)
    {
        return model.getLocByName(name);
    }

    /**
     * Replays a plan against the model, checking the invariant that makes it a plan at all: every move
     * must find its destination free at the moment it runs.
     * "Free" means the station holds nobody else, and no square somebody else is standing on holds it back through
     * the occupancy restriction (FR-001, OB-073): `moveLocomotive` PLACES a locomotive rather than refusing, so
     * without the second check a replay could not notice a move the railway then refuses.
     *
     * The planner applies the restriction (`HomeStaging.firstClearRoute` reads `Point.heldBackBy`) and so does the
     * railway (Adam: *"enforce the occupancy ruling in all modes and then rely on isPathClear"*), so the oracle asks
     * it too, with the LIVE occupancy, which the replay has.
     *
     * MUTATION: dropping the `heldBackBy` line from `HomeStaging.firstClearRoute`'s search fails
     * `testAHomeHeldBackByAnOccupiedPointStillGetsAnExecutablePlan` here.
     */
    private static void applyPlan(Layout layout, HomeStaging.Plan plan)
    {
        for (HomeStaging.Move move : plan.getMoves())
        {
            org.traincontrol.automation.Point end = layout.getPoint(move.getEnd().getName());

            assertNull(end.getCurrentLocomotive(),
                "move \"" + move + "\" sends a locomotive into an occupied station");

            // AND NOT HELD BACK BY A SQUARE SOMEBODY ELSE IS STANDING ON (FR-001, OB-073): the railway refuses that
            // AFTER the planner offered it, which staging exists to avoid.  The locomotive being moved is exempt
            // where it is itself the occupant, which `heldBackBy` applies.
            org.traincontrol.automation.Point heldBy = org.traincontrol.automation.Point.heldBackBy(
                end, move.getLocomotive());

            assertNull(heldBy,
                "move \"" + move + "\" sends a locomotive to a station held back while "
                + (heldBy == null ? "" : heldBy.getName() + " is occupied by "
                    + (heldBy.getCurrentLocomotive() == null
                        ? "somebody" : heldBy.getCurrentLocomotive().getName()))
                + ". The railway refuses that leg, so the plan cannot be carried out and the run"
                + " stops with the fleet half-staged - which is OB-073, and the reason this oracle"
                + " grades plans against the railway rather than against itself");

            assertTrue(
                layout.moveLocomotive(move.getLocomotive().getName(), move.getEnd().getName(), false),
                "move \"" + move + "\" was rejected by the model");
        }
    }

    private static void assertEveryoneHome(Layout layout)
    {
        assertFalse(layout.getHomeStations().isEmpty(),
            "precondition: there must be homes to check, or the loop below runs zero times and "
            + "\"everyone home\" is vacuously true");

        for (java.util.Map.Entry<org.traincontrol.base.Locomotive, org.traincontrol.automation.Point> e
            : layout.getHomeStations().entrySet())
        {
            assertEquals(layout.getPoint(e.getValue().getName()).getCurrentLocomotive(), e.getKey(),
                e.getKey().getName() + " did not end on its home station " + e.getValue().getName());
        }
    }

    // Where home comes from

    /**
     * Loading the graph claims a home for every locomotive it places.
     */
    @Test
    public void testLoadingTheGraphClaimsHomes()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"));
        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS B"));
        assertNull(layout.getHomeStation(loc(LOC_C)), "a locomotive the graph never placed has no home");
    }

    /**
     * A locomotive placed by hand after the load claims where it is put - but only if that station is
     * not already somebody's home.
     */
    @Test
    public void testPlacingByHandClaimsAnUnclaimedStationOnly()
    {
        Layout layout = load(ring(LOC_A, null, null));

        assertTrue(layout.moveLocomotive(LOC_B, "HS C", false));
        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS C"),
            "an unclaimed station is claimed by the first locomotive placed on it");

        // HS A belongs to LOC_A, which has moved away - the claim outlives the occupancy
        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));
        assertTrue(layout.moveLocomotive(LOC_C, "HS A", false));

        assertNull(layout.getHomeStation(loc(LOC_C)),
            "HS A is already claimed, so this locomotive is a free agent rather than a second claimant");
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "and the original claim is untouched");
    }

    /**
     * Moving a locomotive that already has a home does not re-home it - otherwise "return to home"
     * would mean "return to wherever it was last put", which is not a destination anyone chose.
     */
    @Test
    public void testMovingAHomedLocomotiveDoesNotRehomeIt()
    {
        Layout layout = load(ring(LOC_A, null, null));

        assertTrue(layout.moveLocomotive(LOC_A, "HS C", false));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"));
    }

    /**
     * Deleting a locomotive releases its claim, so the station can be claimed again.
     *
     * Without this the station stays spoken for by something that no longer exists, and nothing placed
     * there afterwards could ever have a home - the same leak that outlived deleted locomotives in the
     * exclusion sets until it was found.
     */
    @Test
    public void testDeletingALocomotiveReleasesItsHome() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"));

        MarklinLocomotive deleted = loc(LOC_A);
        layout.locDeleted(deleted);

        assertNull(layout.getHomeStation(deleted));

        assertTrue(layout.moveLocomotive(LOC_B, "HS A", false));
        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS A"),
            "the released station is claimable again");
    }

    // Planning

    /**
     * Three stations in a line, one way only - there is no route from any station back to itself.
     */
    private static String line(String locAtA)
    {
        return json("{'points': ["
            + station("HS A", 0, locAtA) + ","
            + station("HS B", 1, null) + ","
            + station("HS C", 2, null)
            + "],'edges': [" + edge("HS A", "HS B") + "," + edge("HS B", "HS C")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A layout already in order reports so, even when no locomotive could route back to where it
     * stands.
     *
     * The reachability check asks whether each locomotive can get from where it is to its home.  Asked
     * of a locomotive already at home that is a route from a point to itself, which exists only if the
     * track loops - so on a line the check called a perfectly ordered layout impossible.  Everything
     * already being home is now settled before reachability is considered at all.
     */
    @Test
    public void testAlreadyHomeIsReportedOnALayoutWithNoWayBack()
    {
        Layout layout = load(line(LOC_A));

        assertEquals(layout.planReturnToHome().getOutcome(), HomeStaging.Outcome.ALREADY_HOME);
    }

    /**
     * Triage answers the cheap half without planning, and never disagrees with the plan.
     *
     * The UI asks it before confirming that the timetable will be replaced - there is no point putting
     * that question to someone when nothing is going to run.
     */
    @Test
    public void testTriageAgreesWithThePlanAndNeedsNoSearch()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertEquals(layout.triageReturnToHome(), HomeStaging.Outcome.ALREADY_HOME);
        assertEquals(layout.planReturnToHome().getOutcome(), HomeStaging.Outcome.ALREADY_HOME);

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        assertNull(layout.triageReturnToHome(),
            "with work to do, only a plan can say whether it is possible");
        assertTrue(layout.planReturnToHome().isPossible());
    }

    /**
     * Gives the layout a timetable of its own, so there is something to lose.
     */
    private static List<TimetablePath> giveTimetable(Layout layout, String locName)
    {
        List<List<Edge>> paths = layout.getPossiblePaths(loc(locName), true);

        assertFalse(paths.isEmpty(), "precondition: the fixture must offer this locomotive somewhere to go");

        List<TimetablePath> timetable = new ArrayList<>();

        timetable.add(new TimetablePath(loc(locName), paths.get(0), 111L));
        timetable.add(new TimetablePath(loc(locName), paths.get(0), 222L));

        layout.setTimetable(new ArrayList<>(timetable));

        return timetable;
    }

    /**
     * A staging run borrows the timetable and gives back exactly what it took.
     *
     * The operator's timetable is theirs - captured by hand, or built entry by entry - and it is only
     * written to disk when the autonomy file is saved.  Returning home occupies the timetable because
     * that is the machinery that drives trains, so it has to put back what it displaced: the same
     * entries, in the same order, with their execution times intact.  Sequential mode has to go back
     * too, or the restored timetable would run under staging semantics it was never written for.
     */
    @Test
    public void testTheTimetableIsBorrowedAndGivenBackUnchanged()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        List<TimetablePath> original = giveTimetable(layout, LOC_A);

        assertFalse(layout.isTimetableSequential(), "precondition: an ordinary timetable is not sequential");

        // What the UI does before it hands the timetable over - a copy, because getTimetable is live
        List<TimetablePath> borrowed = new ArrayList<>(layout.getTimetable());

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        assertTrue(layout.loadReturnToHomeTimetable().isPossible(),
            "precondition: the fixture must produce a plan, or nothing displaces the timetable and the "
            + "assertions below would be measuring the wrong thing");

        assertNotEquals(layout.getTimetable(), original, "the plan should have displaced the timetable");
        assertTrue(layout.isTimetableSequential(), "a staging plan must run one train at a time");

        // What the UI does in its finally, however the run ended
        layout.setTimetable(borrowed);

        assertEquals(layout.getTimetable().size(), original.size(), "a different number of entries came back");

        for (int i = 0; i < original.size(); i++)
        {
            assertSame(layout.getTimetable().get(i), original.get(i),
                "entry " + i + " is not the one that was taken away");

            assertEquals(layout.getTimetable().get(i).getExecutionTime(), original.get(i).getExecutionTime(),
                "entry " + i + " came back with a different execution time");
        }

        assertFalse(layout.isTimetableSequential(),
            "restoring the timetable must also restore ordinary execution, or the operator's timetable "
            + "would run under staging rules");
    }

    /**
     * A fold while the plan occupies the timetable writes his timetable, not the plan, and no one-at-a-time flag;
     * once the plan has handed it back, a fold writes the timetable as it is then.  Otherwise a timetable changed
     * afterwards would never be saved: the one held when Return Home began would be written every time.
     *
     * MUTATION: fold the timetable the railway holds while the plan has it, or leave the loan standing after the
     * hand-back, and this fails.
     *
     * @throws Exception from the JSON
     */
    @Test
    public void testAFoldWritesHisTimetableWhileThePlanHasItAndTheTimetableOnceItIsBack() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        List<TimetablePath> original = giveTimetable(layout, LOC_A);

        List<TimetablePath> borrowed = new ArrayList<>(layout.getTimetable());

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        assertTrue(layout.loadReturnToHomeTimetable().isPossible(), "precondition: the fixture must produce a plan");

        org.json.JSONObject folded = new org.json.JSONObject(layout.toJSON());

        assertEquals(folded.getJSONArray("timetable").length(), original.size(), "a fold while the plan has the"
            + " timetable wrote the plan (RLV9-B1)");

        for (int i = 0; i < original.size(); i++)
        {
            assertTrue(original.get(i).toJSON().similar(folded.getJSONArray("timetable").getJSONObject(i)), "a fold"
                + " while the plan has the timetable wrote entry " + i + " other than his (RLV9-B1)");
        }

        assertFalse(folded.optBoolean("timetableSequential", false), "a fold while the plan has the timetable marked"
            + " it to run one train at a time (RLV9-B1)");

        // HANDED BACK, as the runner's finally hands it back, and then changed: an entry deleted
        layout.setTimetable(borrowed);

        layout.getTimetable().remove(1);

        folded = new org.json.JSONObject(layout.toJSON());

        assertEquals(folded.getJSONArray("timetable").length(), 1, "a fold after the plan handed the timetable back"
            + " wrote the timetable as it was when Return Home began, not as it is - the loan outlived the hand-back"
            + " (RLV10-C5)");
    }

    /**
     * With capture on, a staging run records nothing.
     *
     * Capture appends every path a locomotive starts to the timetable - and a staging run IS the
     * timetable, walked by a loop that re-reads its own size.  So capture turned the run into a
     * quine: each move appended a copy of itself, the loop walked into the copies, each copy failed
     * validation because the locomotive now stood at its path's end, and a run whose trains had all
     * arrived ended in an emergency stop and a failure dialog.  Excluding the append for the duration
     * is the fix; excluding it only around the load - which is what shipped first - was not.
     */
    @Test
    public void testAStagingRunIsNotCapturedIntoItsOwnTimetable() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        layout.setTimetableCapture(true);
        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        assertTrue(layout.loadReturnToHomeTimetable().isPossible(), "precondition: a plan must load");

        int staged = layout.getTimetable().size();

        assertTrue(staged > 0, "precondition: the plan must have put something in the timetable");
        assertTrue(layout.isTimetableSequential(), "precondition: a staged plan runs sequentially");

        // What executePath does for each move it starts.  Reached by reflection rather than through a
        // hook added to production code: the guard under test lives in addTimetableEntry, and a method
        // that exists only so a test can reach it is the kind of thing this review round removed.
        //
        // The flag is set the same way, because the guard is on the RUN and not on staging: a normal
        // timetable run with capture on appended itself too, and its retries never give up, so it
        // never finished at all.  Excluding only staging fixed one of two identical entrances.
        Method append = Layout.class.getDeclaredMethod(
            "addTimetableEntry", Locomotive.class, List.class, long.class);

        Field executing = Layout.class.getDeclaredField("timetableExecuting");

        append.setAccessible(true);
        executing.setAccessible(true);
        executing.setBoolean(layout, true);

        for (HomeStaging.Move move : layout.planReturnToHome().getMoves())
        {
            append.invoke(layout, move.getLocomotive(), move.getPath(), 0L);
        }

        executing.setBoolean(layout, false);

        assertEquals(layout.getTimetable().size(), staged,
            "a timetable run appended itself to the list it was executing - the dispatch loop would "
            + "then walk into the copies and abandon a run that had actually succeeded");
    }

    /**
     * Loading a plan leaves timetable capture as it found it.
     *
     * Capture has to be off while the plan is written, or the staging moves would be recorded as though
     * the operator had driven them.  Turning it off is not the same as turning it off permanently, and
     * an operator who had capture on has no reason to expect it silently switched off.
     */
    @Test
    public void testTimetableCaptureSurvivesLoadingAPlan()
    {
        for (boolean capturing : new boolean[] { true, false })
        {
            Layout layout = load(ring(LOC_A, LOC_B, null));

            layout.setTimetableCapture(capturing);
            assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

            layout.loadReturnToHomeTimetable();

            assertEquals(layout.isTimetableCapture(), capturing,
                "capture was " + capturing + " before the plan was loaded and must be " + capturing
                + " after it");
        }
    }

    /**
     * getTimetable hands back the live list, which is why the caller has to copy it.
     *
     * Holding the returned list and expecting to restore from it later does not work - loading a plan
     * empties that very list.  This is pinned because the mistake is invisible: the code reads as though
     * it saved the timetable, and the failure only shows up as the operator's timetable quietly gone.
     */
    @Test
    public void testGetTimetableIsLiveSoCallersMustCopyToRestoreIt()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        giveTimetable(layout, LOC_A);

        TimetablePath first = layout.getTimetable().get(0);

        List<TimetablePath> notACopy = layout.getTimetable();
        List<TimetablePath> aCopy = new ArrayList<>(layout.getTimetable());

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));
        assertTrue(layout.loadReturnToHomeTimetable().isPossible(), "precondition: a plan must be loaded");

        // Checked by content rather than by size: a plan that happened to have as many moves as the
        // timetable had entries would make a size comparison pass while proving nothing
        assertFalse(notACopy.contains(first),
            "if the live list still held the original entries this test would be pointless - and the "
            + "copy in the UI could be dropped");

        assertTrue(aCopy.contains(first), "the copy must still hold what was taken away");

        layout.setTimetable(aCopy);

        assertEquals(layout.getTimetable().size(), 2, "only the copy could restore the original");
    }

    /**
     * Nothing placed means nothing to offer, and it must not read as an error.
     */
    @Test
    public void testEmptyGraphReportsNoLocomotives()
    {
        Layout layout = load(ring(null, null, null));

        assertEquals(layout.planReturnToHome().getOutcome(), HomeStaging.Outcome.NO_LOCOMOTIVES);
    }

    /**
     * Everything already where it belongs is its own answer, distinct from "a plan is ready" - there is
     * nothing to run, and offering a run would be misleading.
     */
    @Test
    public void testUntouchedGraphReportsAlreadyHome()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.ALREADY_HOME);
        assertFalse(plan.isPossible(), "there is nothing to execute");
    }

    /**
     * The straightforward case: one locomotive has wandered, its home is free, send it back.
     */
    @Test
    public void testASingleDisplacedLocomotiveIsPlannedHome()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "outcome was " + plan.getOutcome());
        assertEquals(plan.getMoves().size(), 1, "one locomotive is away, so one move: " + plan.getMoves());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);
    }

    /**
     * The case the greedy pass cannot solve on its own: two locomotives on each other's home stations.
     *
     * Neither can go home first - the station it wants is occupied by the one that wants the station it
     * is standing on.  It needs a locomotive moved somewhere it does not belong, which only the search
     * will do, and it is solvable here only because the ring has a spare station.
     */
    @Test
    public void testTwoLocomotivesOnEachOthersHomesAreUnwound()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // Park one out of the way, swap them, then take the parked one back out of the picture
        assertTrue(layout.moveLocomotive(LOC_A, "HS C", false));
        assertTrue(layout.moveLocomotive(LOC_B, "HS A", false));
        assertTrue(layout.moveLocomotive(LOC_A, "HS B", false));

        assertEquals(layout.getPoint("HS A").getCurrentLocomotive(), loc(LOC_B));
        assertEquals(layout.getPoint("HS B").getCurrentLocomotive(), loc(LOC_A));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "outcome was " + plan.getOutcome());
        // Exactly three, not merely at least three.  A swap cannot be done in two, and A* with an
        // admissible heuristic returns the shortest plan - so this is also the regression test for the
        // priority queue: it was ordered on a score map the relaxation rewrote, which changed an
        // enqueued entry's priority in place and let polls return states that were not the cheapest.
        // A search that explores out of order can still reach the goal, but by a longer route.
        assertEquals(plan.getMoves().size(), 3,
            "a swap needs one locomotive moved out of the way and back, and no more: " + plan.getMoves());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);
    }

    /**
     * A corridor with one siding: HOME and FAR are joined only through MID, and MID has a siding.
     */
    private static String corridor(String atHome, String atMid, String atFar)
    {
        return json("{'points': ["
            + station("HS HOME", 4, atHome) + ","
            + station("HS MID", 5, atMid) + ","
            + station("HS FAR", 6, atFar) + ","
            + station("HS SIDING", 7, null)
            + "],'edges': ["
            + edge("HS HOME", "HS MID") + "," + edge("HS MID", "HS HOME") + ","
            + edge("HS MID", "HS FAR") + "," + edge("HS FAR", "HS MID") + ","
            + edge("HS MID", "HS SIDING") + "," + edge("HS SIDING", "HS MID")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A locomotive that is already home may still have to move, so that another can get past it.
     *
     * The only way from FAR to HOME is through MID, and MID's own occupant is sitting exactly where it
     * belongs.  Nothing is wrong with the arrangement except that it is in the way, so the plan has to
     * park that locomotive on the siding, let the other through, and put it back.
     *
     * This is the shape a real layout produced: a locomotive could not reach its home because a
     * correctly-placed train stood on the only corridor to it.  The planner reported that as
     * impossible, because it asked whether any of a handful of routes computed WITHOUT regard to
     * occupancy happened to be clear, rather than whether a clear route existed.  On a layout with
     * loops those routes share the busy stretch, so they were all blocked at once.
     */
    @Test
    public void testALocomotiveAtHomeStepsAsideAndReturns()
    {
        Layout layout = load(corridor(LOC_A, LOC_B, null));

        // LOC_A goes to the far end, so its way back runs through LOC_B's home
        assertTrue(layout.moveLocomotive(LOC_A, "HS FAR", false));

        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS MID"));
        assertEquals(layout.getPoint("HS MID").getCurrentLocomotive(), loc(LOC_B),
            "precondition: the blocker is standing on its own home");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(),
            "a train in the way is not an impossibility - it can be moved.  Outcome was "
            + plan.getOutcome());

        assertTrue(plan.getMoves().size() >= 3,
            "the blocker must step aside and come back: " + plan.getMoves());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);
    }

    /**
     * Deleting a station releases the home claim on it.
     *
     * The claim map is keyed by locomotive and valued by point, and releasing was wired only into the
     * key side: deleting a locomotive frees its claim, deleting its home did not.  A claim that
     * outlives its station is worse than a leak - the locomotive still counts as misplaced, so Return
     * Home stays lit, and pressing it reports that the locomotive cannot reach a station that is no
     * longer on the graph.  Stable, unactionable, and only escapable by re-creating the station or
     * reloading the file.
     */
    @Test
    public void testDeletingAStationReleasesTheHomeClaimOnIt() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "precondition: the locomotive is homed at the station about to be deleted");

        // A station can only be deleted once nothing connects to it, and the ring is bidirectional -
        // all four edges touching HS A have to go first
        assertTrue(layout.moveLocomotive(LOC_A, "HS C", false));

        layout.deleteEdge("HS A", "HS B");
        layout.deleteEdge("HS B", "HS A");
        layout.deleteEdge("HS D", "HS A");
        layout.deleteEdge("HS A", "HS D");
        layout.deletePoint("HS A");

        assertNull(layout.getHomeStation(loc(LOC_A)),
            "the claim outlived the station it was held against");

        // LOC_B is still homed and still at home, so the only homed locomotive is where it belongs.
        // Before the fix this said IMPOSSIBLE, naming a station that is no longer on the graph.
        assertEquals(layout.triageReturnToHome(), HomeStaging.Outcome.ALREADY_HOME,
            "a locomotive whose home was deleted is homeless, not permanently unable to reach home");
    }

    /**
     * A free agent may be moved but never has to land anywhere in particular, so it does not make a
     * plan impossible by sitting on somebody's home.
     */
    @Test
    public void testAFreeAgentIsMovedOutOfTheWay()
    {
        Layout layout = load(ring(LOC_A, null, null));

        // LOC_C has no home, and parks on the station LOC_A wants
        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));
        assertTrue(layout.moveLocomotive(LOC_C, "HS A", false));

        assertNull(layout.getHomeStation(loc(LOC_C)), "precondition: it really is a free agent");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "outcome was " + plan.getOutcome());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);
    }

    // Loading the timetable

    /**
     * The plan is handed to the existing timetable machinery rather than to a second execution engine.
     * Every entry must be unrun and undelayed: executeTimetable resumes at the first entry whose
     * execution time is zero, and dispatches each as soon as the one before it has started.
     */
    @Test
    public void testLoadingTheTimetableProducesRunnableEntries()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

        assertTrue(plan.isPossible());
        assertEquals(layout.getTimetable().size(), plan.getMoves().size(),
            "every move should be loaded as a timetable entry");

        for (int i = 0; i < layout.getTimetable().size(); i++)
        {
            assertEquals(layout.getTimetable().get(i).getLoc(), plan.getMoves().get(i).getLocomotive(),
                "entry " + i + " must keep the plan's order - the order is what makes it safe");
            assertEquals(layout.getTimetable().get(i).getExecutionTime(), 0,
                "a freshly loaded entry has not run");
            assertEquals(layout.getTimetable().get(i).getSecondsToNext(), 0,
                "no artificial delay: the retry loop holds moves back when the path is busy");
        }

        // Nothing is asserted about capture here any more.  Loading used to switch it off, and this
        // line pinned that - but it only ever passed because the default is off, so it would have gone
        // on passing had the behavior broken.  Capture is now left exactly as the operator set it, and
        // the guarantee that a run does not record itself lives where it is actually enforced:
        // testAStagingRunIsNotCapturedIntoItsOwnTimetable.
    }

    /**
     * A staging plan must be flagged to run one train at a time, and any other timetable must not be.
     *
     * This is the fix for a real failure: executeTimetable normally dispatches an entry as soon as the
     * one before it has STARTED, and executePath locks a whole path up front.  Two staging moves
     * overlapping therefore contended for an edge the planner - which models nothing as moving - never
     * considered, and the second retried forever on a fixed path it could not abandon, while a free
     * alternative existed that only live path selection would have found.
     */
    @Test
    public void testStagingPlansAreFlaggedSequentialAndOtherTimetablesAreNot()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertFalse(layout.isTimetableSequential(), "a freshly parsed layout has no staging plan");

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));
        assertTrue(layout.loadReturnToHomeTimetable().isPossible());

        assertTrue(layout.isTimetableSequential(),
            "a staging plan is only valid one train at a time");

        // Any other timetable load must put it back - the flag belongs to the plan, not the layout
        layout.setTimetable(new java.util.LinkedList<>());

        assertFalse(layout.isTimetableSequential(),
            "loading any other timetable restores the normal overlapping behaviour");
    }

    /**
     * Nothing is loaded when there is nothing to do, so a stale plan cannot be left sitting in the
     * timetable waiting to be executed by accident.
     *
     * MUTATION: deleting `if (!plan.isPossible()) return plan;` from `loadReturnToHomeTimetable`.
     *
     * The empty timetable does not catch it: an ALREADY_HOME plan has no moves, so the timetable stays empty either
     * way.  What the guard decides is the SEQUENTIAL flag: past it, a no-trains plan would make every ordinary
     * timetable loaded afterwards run one train at a time.
     */
    @Test
    public void testNothingIsLoadedWhenAlreadyHome()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));
        layout.setTimetable(new java.util.LinkedList<>());

        assertFalse(layout.isTimetableSequential(),
            "precondition: setTimetable is supposed to restore the overlapping behaviour, so the "
            + "assertion below would prove nothing if the flag were already false for another reason");

        HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.ALREADY_HOME);
        assertTrue(layout.getTimetable().isEmpty(), "an untouched layout loads no moves");

        assertFalse(layout.isTimetableSequential(),
            "a layout that was already home came back with the timetable set to run one train at a "
            + "time.  Nothing was staged, so there is nothing to run one at a time - and the flag "
            + "stays on for whatever ordinary timetable is loaded next");
    }

    // Assigning a home, rather than deriving one

    /**
     * An assignment says where a locomotive belongs; standing somewhere only ever said where it was.  Assignments
     * apply before anything is derived, and what they do not mention falls back to the old rule, so a layout that
     * assigns nothing behaves as it always did.
     */
    @Test
    public void testAnAssignmentBeatsWhereTheLocomotiveHappensToStand() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "precondition: with nothing assigned, home is the station the locomotive was placed on");

        layout.setHomeLocomotive("HS D", LOC_A);

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS D"),
            "the assignment decides, not the occupancy");

        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS B"),
            "a locomotive nobody assigned keeps the home it derived");

        // HS A is still occupied by LOC_A, but LOC_A is spoken for, so nothing claims HS A
        assertNull(layout.getHomeStation(loc(LOC_C)),
            "a locomotive that is not on the graph derives nothing");
    }

    /**
     * One station per locomotive: assigning it somewhere new gives up wherever it was.  Two stations waiting for the
     * same train could never both be satisfied, and the planner would report the layout impossible rather than the
     * assignment contradictory.
     */
    @Test
    public void testAssigningALocomotiveSomewhereNewReleasesItsOldStation() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.setHomeLocomotive("HS D", LOC_A);
        layout.setHomeLocomotive("HS C", LOC_A);

        assertNull(layout.getPoint("HS D").getHomeLoc(),
            "the station it was assigned to before has to let go of it");
        assertEquals(layout.getPoint("HS C").getHomeLoc(), loc(LOC_A));
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS C"));
    }

    /**
     * Clearing one station falls back to the rule that was there before it was assigned.
     */
    @Test
    public void testClearingOneStationFallsBackToWhereItsLocomotiveStands() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.setHomeLocomotive("HS D", LOC_A);

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS D"),
            "precondition: the assignment took effect");

        layout.setHomeLocomotive("HS D", null);

        assertNull(layout.getPoint("HS D").getHomeLoc());
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "with the assignment gone, home is once again the station it stands on");
    }

    /**
     * Clearing every assignment restores the derived homes exactly: the promise of the feature.  Compared as a whole
     * map, because a leftover entry is precisely the failure worth catching.
     */
    @Test
    public void testClearingEveryAssignmentRestoresThePositionalHomesExactly() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, LOC_C));

        Map<Locomotive, Point> before = new LinkedHashMap<>(layout.getHomeStations());

        assertFalse(before.isEmpty(), "precondition: the fixture must derive some homes to restore");

        layout.setHomeLocomotive("HS D", LOC_A);
        layout.setHomeLocomotive("HS A", LOC_B);

        assertNotEquals(layout.getHomeStations(), before,
            "precondition: the assignments must have actually changed something");

        layout.clearHomeLocomotives();

        assertEquals(layout.getHomeStations(), before,
            "clearing has to leave exactly the homes a layout without this feature would have had");
    }

    /**
     * Only assignments count as assignments; derived homes are not offered to clear.  The menu item that clears them
     * all is shown on this answer alone.
     */
    @Test
    public void testOnlyAssignmentsCountAsHomeLocomotives() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertFalse(layout.getHomeStations().isEmpty(), "precondition: homes were derived");
        assertFalse(layout.hasHomeLocomotives(), "derived homes are not assignments");

        layout.setHomeLocomotive("HS D", LOC_A);
        assertTrue(layout.hasHomeLocomotives());

        layout.clearHomeLocomotives();
        assertFalse(layout.hasHomeLocomotives());
    }

    /**
     * An assignment naming a locomotive that is not on the graph is kept (it may be placed later) and ignored (an
     * absent locomotive contributes no goal, so it cannot make a fine layout unplannable).
     */
    @Test
    public void testAnAssignmentForALocomotiveNotOnTheGraphIsKeptButIgnored() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // LOC_C is in the database, but the fixture never placed it
        layout.setHomeLocomotive("HS D", LOC_C);

        assertEquals(layout.getHomeStation(loc(LOC_C)), layout.getPoint("HS D"), "the assignment is kept");

        assertTrue(layout.moveLocomotive(LOC_A, "HS C", false));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "a home for an absent locomotive must not stop the locomotives that are here");

        for (HomeStaging.Move move : plan.getMoves())
        {
            assertNotEquals(move.getLocomotive(), loc(LOC_C),
                "nothing can be routed for a locomotive that is not on the graph");
        }

        applyPlan(layout, plan);

        assertEquals(layout.getPoint("HS A").getCurrentLocomotive(), loc(LOC_A),
            "the locomotive that is here still went home");
    }

    /**
     * An assignment naming something that is not a locomotive at all is dropped when the file loads: a name matching
     * nothing cannot resolve later, and keeping it would be written back and reported on every load.  The graph is
     * never invalidated over it.
     */
    @Test
    public void testAnAssignmentNamingAnUnknownLocomotiveIsDroppedOnLoad()
    {
        Layout layout = load(ringAssigning("HS phantom"));

        assertNull(layout.getPoint("HS D").getHomeLoc(),
            "a dangling name is removed rather than carried around");
        assertFalse(layout.hasHomeLocomotives());
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "and everything else still derives its home as usual");
    }

    /**
     * A placement naming a locomotive the database does not have loses the placement, not the layout, as every
     * other dangling name in the loader does.  Adam: **"drop the train and keep the rest."**  The legacy importer
     * already behaves this way
     * (`core.testATrainIsPlacedOnceAndKeepsItsFacing.testAPlacementForAnUnknownLocomotiveIsRefused`).
     *
     * Asserted on everything the file carries around that placement (the other train, the home, the exclusion, the
     * station maximum, the edges): a layout that merely parses is not the claim.
     *
     * MUTATION: put the `invalidate` back and the first assertion fails; every assertion after it is about a railway
     * that does not exist.
     */
    @Test
    public void testAPlacementForALocomotiveNotInTheDatabaseDropsOnlyThePlacement() throws Exception
    {
        // AND THE LINE THE OPERATOR READS (MT-452): the drop is silent unless it says so.  Its sibling
        // `testALockEdgeNamingMissingTrackIsDroppedLoudly` listens the same way.
        final List<String> heard = new java.util.concurrent.CopyOnWriteArrayList<>();

        java.util.logging.Handler ear = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                heard.add(record.getMessage());
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        };

        java.util.logging.Logger modelLog =
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName());

        modelLog.addHandler(ear);

        try
        {
            model.parseAuto(aPlacementForAPhantom());
        }
        finally
        {
            modelLog.removeHandler(ear);
        }

        // The sentence is the bundle's own, with the phantom's name and the square it stood on
        final String expected = org.traincontrol.util.I18n.f("autolayout.warnPlacedLocomotiveNotInDatabase",
            "LD phantom, sold years ago", "LD_X");

        boolean said = false;

        for (String line : heard)
        {
            if (line != null && line.contains(expected)) said = true;
        }

        assertTrue(said,
            "the placement was dropped without a word, so the operator's file quietly stops matching his"
            + " railway and nothing tells him which train went.  Heard: " + heard);

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the loader returned no layout at all for a configuration naming one phantom train");

        assertTrue(layout.isValid(),
            "one placement naming a locomotive that is not in the database took the whole configuration out of"
            + " service - \"" + Layout.getLastError() + "\" - where every other dangling name in this loader is"
            + " dropped with a log line (AMR-C3; Adam: \"drop the train and keep the rest\")");

        assertNull(layout.getPoint("LD_X").getCurrentLocomotive(),
            "the phantom was placed on the railway, so a name matching nothing in the database is being carried"
            + " around as though it were a train");

        // AND THE REST OF THE FILE, which is the half the ruling is actually about.
        assertEquals(layout.getPoint("LD_Z").getCurrentLocomotive(), loc(LOC_A),
            "the train that IS in the database was not placed, so the drop took more than the one placement");

        assertEquals(layout.getPoint("LD_Y").getHomeLoc(), loc(LOC_A), "the home assignment was lost with it");

        assertTrue(layout.getPoint("LD_Y").getExcludedLocs().contains(loc(LOC_B)),
            "the exclusion was lost with it, which is a restriction the operator believes is in force");

        assertEquals(layout.getPoint("LD_Y").getMaxTrainLength().intValue(), 7,
            "the station maximum was lost with it");

        assertNotNull(layout.getEdge("LD_X", "LD_Y"), "the track was lost with it");
    }

    /**
     * Three squares in a line: one holding a phantom, one carrying a home, an exclusion and a maximum, and one
     * holding a locomotive this database really has.
     *
     * @return the configuration
     */
    private static String aPlacementForAPhantom()
    {
        return json("{'points': ["
            + "{'name': 'LD_X', 'station': true, 's88': " + (S88_BASE + 102)
            + ", 'loc': {'name': 'LD phantom, sold years ago'}},"
            + "{'name': 'LD_Y', 'station': true, 's88': " + (S88_BASE + 103)
            + ", 'home': '" + LOC_A + "', 'maxTrainLength': 7, 'excludedLocs': ['" + LOC_B + "']},"
            + "{'name': 'LD_Z', 'station': true, 's88': " + (S88_BASE + 104)
            + ", 'loc': {'name': '" + LOC_A + "'}}"
            + "],'edges': ["
            + edge("LD_X", "LD_Y") + "," + edge("LD_Y", "LD_X") + ","
            + edge("LD_Y", "LD_Z") + "," + edge("LD_Z", "LD_Y")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A lock naming track the file does not contain is dropped LOUDLY, and the locks that do resolve are kept.
     *
     * A lock edge says two roads are one piece of metal: if its name is a misspelling of track that IS there,
     * dropping it lets two trains onto shared metal.  Adam: **"drop the lock edge with a loud log line too."**
     *
     * Pins that the configuration loads, that a lock in the same list that DOES resolve is applied, and that the
     * model's own `java.util.logging` logger carries the dedicated warning naming both the owning track and the name
     * that matched nothing.
     *
     * MUTATION: put the `invalidate` back and the first assertion fails; drop the `logf` and the last one does.
     */
    @Test
    public void testALockEdgeNamingMissingTrackIsDroppedLoudly() throws Exception
    {
        final List<String> heard = new java.util.concurrent.CopyOnWriteArrayList<>();

        java.util.logging.Handler ear = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                heard.add(record.getMessage());
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        };

        java.util.logging.Logger modelLog =
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName());

        modelLog.addHandler(ear);

        try
        {
            model.parseAuto(aLockNamingMissingTrack());

            Layout layout = model.getAutoLayout();

            assertNotNull(layout, "the loader returned no layout at all for a configuration with one dangling lock");

            assertTrue(layout.isValid(),
                "one lock naming track that is not in the file took the whole configuration out of service - \""
                + Layout.getLastError() + "\" (AMR-C3; Adam: \"drop the lock edge with a loud log line too\")");

            Edge owner = layout.getEdge("LK_A", "LK_B");
            Edge real = layout.getEdge("LK_C", "LK_D");

            assertTrue(owner.getLockEdges().contains(real),
                "the lock that DOES resolve was lost with the one that does not, so a misspelt entry cost its"
                + " neighbour a real constraint on shared metal");

            assertEquals(owner.getLockEdges().size(), 1,
                "something other than the one real lock was applied: " + owner.getLockEdges());

            String expected = org.traincontrol.util.I18n.f("autolayout.warnLockEdgeNotInGraph",
                "LK_A -> LK_B", "LK_Q -> LK_R");

            assertTrue(heard.contains(expected),
                "the lock was dropped without the warning, which is the silent loss Adam asked to be told about -"
                + " a misspelt name here lets two trains onto shared metal and nothing says so.  Heard: " + heard);
        }
        finally
        {
            modelLog.removeHandler(ear);
        }
    }

    /**
     * Two roads, A - B and C - D, with A -> B locked against C -> D (real) and against LK_Q -> LK_R, which the file does
     * not contain.
     *
     * @return the configuration
     */
    private static String aLockNamingMissingTrack()
    {
        return json("{'points': ["
            + square("LK_A", 106, null, true, null) + ","
            + square("LK_B", 107, null, true, null) + ","
            + square("LK_C", 108, null, true, null) + ","
            + square("LK_D", 109, null, true, null)
            + "],'edges': ["
            + "{'start': 'LK_A', 'end': 'LK_B', 'lockedges': ["
            + "{'start': 'LK_C', 'end': 'LK_D'}, {'start': 'LK_Q', 'end': 'LK_R'}]},"
            + edge("LK_B", "LK_A") + ","
            + edge("LK_C", "LK_D") + "," + edge("LK_D", "LK_C")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * Assignments survive a save and a reload, which is the only reason to store them on the point.
     */
    @Test
    public void testAssignmentsSurviveBeingSavedAndReloaded() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.setHomeLocomotive("HS D", LOC_A);

        Layout reloaded = load(layout.toJSON());

        assertEquals(reloaded.getPoint("HS D").getHomeLoc(), loc(LOC_A));
        assertEquals(reloaded.getHomeStation(loc(LOC_A)), reloaded.getPoint("HS D"));
        assertTrue(reloaded.hasHomeLocomotives());
    }

    /**
     * The whole point: returning home goes to the assigned station, not the one it was standing on.
     */
    @Test
    public void testReturningHomeGoesToTheAssignedStation() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        layout.setHomeLocomotive("HS D", LOC_A);
        layout.setHomeLocomotive("HS C", LOC_B);

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "outcome was " + plan.getOutcome());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);

        assertEquals(layout.getPoint("HS D").getCurrentLocomotive(), loc(LOC_A));
        assertEquals(layout.getPoint("HS C").getCurrentLocomotive(), loc(LOC_B));
    }

    /**
     * Deleting a locomotive takes the assignment naming it with it.  The assignment is a name, so deleting the object
     * does not touch it; left behind it is written back on every save, reported on every load and offered by the menus.
     */
    @Test
    public void testDeletingALocomotiveClearsTheAssignmentNamingIt() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        layout.setHomeLocomotive("HS D", LOC_C);

        assertEquals(layout.getPoint("HS D").getHomeLoc(), loc(LOC_C), "precondition: the assignment was made");

        layout.locDeleted(loc(LOC_C));

        assertNull(layout.getPoint("HS D").getHomeLoc(),
            "a name nothing can resolve must not be left sitting on the point");
        assertNull(layout.getHomeStation(loc(LOC_C)));
        assertFalse(layout.hasHomeLocomotives(),
            "and nothing is left for the clear-all menu item to offer");
    }

    /**
     * Renaming a locomotive keeps its assignment.  Every other reference the layout holds is an identity-hashed object
     * reference that a rename cannot dislodge, but an assignment is a name and dangles unless repaired.  Driven through
     * renameLoc, because the wiring is the part that would be missing.
     */
    @Test
    public void testRenamingALocomotiveKeepsItsAssignment() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        layout.setHomeLocomotive("HS D", LOC_A);

        String renamed = LOC_A + " renamed";

        assertTrue(model.renameLoc(LOC_A, renamed), "precondition: the rename must succeed");

        try
        {
            assertSame(layout.getPoint("HS D").getHomeLoc(), model.getLocByName(renamed),
                "the assignment is the LOCOMOTIVE, so a rename is nothing it has to be told about - "
                + "the object it points at is the object that was renamed");

            // Only a rebuild proves it: that is what resolves names, and what would have dropped a
            // stale one
            layout.rebuildHomeStations();

            assertSame(layout.getPoint("HS D").getHomeLoc(), model.getLocByName(renamed),
                "and it survives being re-derived rather than being reported missing and dropped");
            assertEquals(layout.getHomeStation(model.getLocByName(renamed)), layout.getPoint("HS D"));
        }
        finally
        {
            // The database is the real one, so the name goes back whatever happened above.  Not asserted: an assertion
            // in a finally replaces the failure it is cleaning up after, and tearDownClass deletes by the original
            // name.
            model.renameLoc(renamed, LOC_A);
        }
    }

    /**
     * Renaming a station keeps its assignment, which holds only because the point object survives.
     */
    @Test
    public void testRenamingAStationKeepsItsAssignment() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.setHomeLocomotive("HS D", LOC_A);
        layout.renamePoint("HS D", "HS Depot");

        assertEquals(layout.getPoint("HS Depot").getHomeLoc(), loc(LOC_A));
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS Depot"));
    }

    /**
     * An assignment is the locomotive itself, so there is no name to get wrong: nothing to trim (locomotive names are
     * never trimmed) and nothing to mismatch.  The object goes in and the same object comes back, whatever it is
     * called.
     */
    @Test
    public void testAnAssignmentIsTheLocomotiveItself()
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        Locomotive alpha = loc(LOC_A);

        d.setHomeLoc(alpha);

        assertSame(d.getHomeLoc(), alpha,
            "the assignment is not the locomotive that was given to it");

        // Renaming is the case this refactor exists for: the object is the same object, so the
        // assignment needs no repair and cannot be left naming something that is gone.
        String was = alpha.getName();

        try
        {
            alpha.rename("HS alpha renamed");

            assertSame(d.getHomeLoc(), alpha,
                "the assignment stopped pointing at its locomotive when the locomotive was renamed. "
                + "That is the whole reason this is an object rather than a name");
        }
        finally
        {
            alpha.rename(was);
        }

        d.setHomeLoc(null);

        assertNull(d.getHomeLoc(), "null is how a station says it has no locomotive of its own");
    }

    /**
     * A station knows which locomotives it could never hold, before the assignment is made.  Each of these makes
     * Return Home report IMPOSSIBLE forever, with advice to check the track that is the wrong remedy.  The chooser
     * warns rather than refuses (the same state is reachable by editing the station later) and asks the planner's own
     * rule, so the answer given at assignment is the one Return Home gives later.
     */
    @Test
    public void testAStationKnowsWhichLocomotivesItCouldNeverHold() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        // Point state dies with the layout, but locomotive state does not (load() re-parses the graph, not the
        // database): restore it even if an assertion fails, or the next test runs against a locomotive left
        // non-reversible.
        final boolean wasReversible = loc(LOC_A).isReversible();
        final Integer wasLength = loc(LOC_A).getTrainLength();

        assertTrue(HomeStaging.canBeHome(loc(LOC_A), d), "precondition: an ordinary station accepts it");

        try
        {
            // Excluded by the station
            d.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));
            assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "a station that excludes it cannot be its home");
            d.setExcludedLocs(new HashSet<Locomotive>());

            // Longer than the station allows
            loc(LOC_A).setTrainLength(10);
            d.setMaxTrainLength(5);
            assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "a train too long to stop there cannot rest there");
            d.setMaxTrainLength(0);
            assertTrue(HomeStaging.canBeHome(loc(LOC_A), d), "and no limit means no objection");

            // A terminus is NOT a reason to refuse a home.  Adam: "trains should be allowed to back into terminuses if
            // they are not reversible (that's why we have the reversing point at feedback 2013)".  That question
            // belongs to the route, where isPathClear sees whether the way there turns the train round.
            loc(LOC_A).setReversible(false);
            d.setTerminus(true);
            assertTrue(HomeStaging.canBeHome(loc(LOC_A), d),
                "a parking berth was refused as a home to a train that cannot reverse");
            loc(LOC_A).setReversible(true);
            assertTrue(HomeStaging.canBeHome(loc(LOC_A), d), "and a reversible one, as always");
            d.setTerminus(false);

            // Switched off
            d.setActive(false);
            assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "an inactive station is not a destination for anything");
            d.setActive(true);
        }
        finally
        {
            loc(LOC_A).setReversible(wasReversible);
            loc(LOC_A).setTrainLength(wasLength);
        }
    }

    /**
     * And the warning agrees with what Return Home would then do: report a permanent IMPOSSIBLE.  The chooser
     * delegates rather than restating the rules, and this fails if the two answers ever differ.
     */
    @Test
    public void testAnImpossibleAssignmentIsExactlyWhatReturnHomeWouldRefuse() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        d.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "precondition: the chooser would warn about this");

        // Made anyway, which the chooser permits and a hand-edited file does not even ask about
        layout.setHomeLocomotive("HS D", LOC_A);

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "what the chooser warns about is exactly what the planner calls impossible");
        assertTrue(plan.getBlocked().contains(loc(LOC_A)), "and it names the locomotive concerned");

        d.setExcludedLocs(new HashSet<Locomotive>());
    }

    /**
     * A train that cannot reverse is homed at a terminus with nothing on the way to turn it round.
     *
     * Adam: **"Return home is manual, take the rule out and say why."**  The rule was `mustBackIn`: a terminus was
     * refused to a non-reversible train unless a reversing point lay on the route, so a real railway answered
     * `NO_PLAN_FOUND` for a train that setting `reversible` true staged at once.
     *
     * The fixture is the plain ring with **no reversing point anywhere**: with one, the old rule would have been
     * satisfied and this would have passed before the change.
     *
     * MUTATION: restoring `mustBackIn` at either of its two routing sites, `firstClearRoute`'s arrival test or the
     * `connected` seed, fails this.
     */
    @Test
    public void testATrainThatCannotReverseIsStillStagedHomeToATerminus() throws Exception
    {
        Layout layout = load(ringAssigning(LOC_A));

        Point d = layout.getPoint("HS D");

        // Locomotive state outlives the layout - load() re-parses the graph, not the database.
        final boolean wasReversible = loc(LOC_A).isReversible();

        try
        {
            loc(LOC_A).setReversible(false);

            d.setTerminus(true);

            // THE FIXTURE HAS TO BE THE HARD CASE, or this passes for the wrong reason.
            for (Point p : layout.getPoints())
            {
                assertFalse(p.isReversing(),
                    "the ring must carry no reversing point - " + p.getName() + " does, so the old "
                    + "rule would have been satisfied and this would prove nothing");
            }

            assertTrue(HomeStaging.canBeHome(loc(LOC_A), d),
                "precondition: the chooser already allows a terminus as a home, so any refusal below "
                + "is the routing rule rather than the home rule");

            HomeStaging.Plan plan = layout.planReturnToHome();

            assertTrue(plan.isPossible(),
                "a train that cannot reverse was refused a plan home to its terminus, and Adam ruled "
                + "on 2026-09-04 that Return Home is manual operation.  Outcome: " + plan.getOutcome());
        }
        finally
        {
            loc(LOC_A).setReversible(wasReversible);
        }
    }
    /**
     * A retired Layout refuses a path outright rather than part-running it.
     *
     * Reloading the autonomy file builds a new Layout, and the version counter is static, so the old
     * one is retired while whatever holds it carries on.  Every speed write was already fenced, so no
     * train moved - but the path was locked and every switch and signal on it commanded first, and the
     * fence-abort then returned before the completion block that removes the locomotive from
     * activeLocomotives.  That strand is what left isRunning() true for the rest of the session.
     */
    @Test
    public void testARetiredLayoutRefusesToRunAPath() throws Exception
    {
        Layout retired = load(ring(LOC_A, LOC_B, null));

        List<List<Edge>> paths = retired.getPossiblePaths(loc(LOC_A), true);

        assertFalse(paths.isEmpty(), "precondition: the fixture must offer this locomotive somewhere to go");

        List<Edge> path = paths.get(0);

        // Parsing again builds a new Layout, which is what a reload does - and the version counter is
        // static, so this retires the one above
        load(ring(LOC_A, LOC_B, null));

        assertFalse(retired.isCurrentLayout(), "precondition: the first layout must now be retired");

        assertFalse(retired.executePath(path, loc(LOC_A), 30, null),
            "a retired layout must refuse the path rather than locking it and aborting part way");

        assertTrue(retired.getActiveLocomotives().isEmpty(),
            "and must strand nothing in activeLocomotives - a strand there is what made isRunning() "
            + "true for the rest of the session");
    }

    /**
     * A retired Layout stops executing its timetable instead of waiting for a train that will never run.
     *
     * The dispatch loop waits for the entry ahead to leave activeLocomotives before starting the next
     * one.  On a retired Layout that entry is the strand above, nothing reachable from the UI can call
     * stopLocomotives() on a graph getAutoLayout() no longer resolves to, and the executor never
     * returned - so the staging worker holding it never reached its finally, and every surface asking
     * isAutonomyBusy answered "trains are moving" until TrainControl was restarted.
     *
     * The timeout is the assertion: without the fence this call does not come back.
     */
    @Test(timeOut = 30000)
    public void testARetiredLayoutStopsExecutingItsTimetable() throws Exception
    {
        Layout retired = load(ring(LOC_A, LOC_B, null));

        // The swap arrangement, because a single move never reaches the wait that spins
        assertTrue(retired.moveLocomotive(LOC_A, "HS C", false));
        assertTrue(retired.moveLocomotive(LOC_B, "HS A", false));
        assertTrue(retired.moveLocomotive(LOC_A, "HS B", false));

        HomeStaging.Plan plan = retired.loadReturnToHomeTimetable();

        assertTrue(plan.isPossible(), "precondition: there must be a plan to execute");
        assertTrue(plan.getMoves().size() > 1, "precondition: one move never reaches the sequential wait");
        assertTrue(retired.isTimetableSequential(), "precondition: a staged plan runs one train at a time");

        load(ring(LOC_A, LOC_B, null));

        assertFalse(retired.isCurrentLayout(), "precondition: the first layout must now be retired");

        retired.executeTimetable();

        assertTrue(retired.getActiveLocomotives().isEmpty(),
            "a retired layout must dispatch nothing at all");
    }


    /**
     * Nothing edits the graph while the railway is using it, deletes included.
     *
     * `renamePoint` refuses while `isRunning() || isStagingInProgress()` (a rename mid-run mutates `Point.hashCode`
     * under live visited sets); `deletePoint` and `deleteEdge` do more damage and had no such guard.  The menu items
     * are grayed when the popup OPENS and the action fires when it is CLICKED, so starting autonomy from another
     * window in between leaves a live item over a running railway (the two-moment gap of OB-057 and OB-090): the
     * guard has to be the one that actually holds.
     *
     * Staging is the busy state because the planner walks these structures with nothing dispatched (the window a bare
     * `isAutoRunning` flag waves through) and it can be entered without moving a train.  `renamePoint` is the
     * CONTROL: if it stops refusing, the two deletes are passing for another reason.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheGraphCannotBeEditedWhileTheRailwayIsUsingIt() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertFalse(layout.isRunning() || layout.isStagingInProgress(),
            "precondition: the layout must start idle, or every refusal below is the wrong refusal");

        List<Edge> edges = new ArrayList<>(layout.getEdges());

        assertFalse(edges.isEmpty(), "precondition: the ring has edges to try to delete");

        Edge victim = edges.get(0);
        String from = victim.getStart().getName();
        String to = victim.getEnd().getName();
        String point = layout.getPoints().iterator().next().getName();

        layout.setStagingInProgress(true);

        try
        {
            // THE CONTROL, and it is the one that already works.
            try
            {
                layout.renamePoint(point, point + " renamed");
                fail("renamePoint let a rename through while staging was in progress. That guard has"
                    + " been there and is what the two below are modelled on, so this failing means"
                    + " the busy state itself is not being seen");
            }
            catch (Exception expected)
            {
            }

            try
            {
                layout.deleteEdge(from, to);
                fail("deleteEdge removed " + from + " -> " + to + " while the staging planner was"
                    + " walking the graph. Paths already handed out hold that edge (C3)");
            }
            catch (Exception expected)
            {
            }

            try
            {
                layout.deletePoint(point);
                fail("deletePoint removed " + point + " while the staging planner was walking the"
                    + " graph. renamePoint refuses this and only mutates the point; this removes it"
                    + " from under live visited sets (C3)");
            }
            catch (Exception expected)
            {
            }
        }
        finally
        {
            layout.setStagingInProgress(false);
        }

        // AND THE WAY PAST IS REAL.  A guard with no way past would be worse than the defect: once
        // the railway is idle the same three edits have to go through.
        assertNotNull(layout.getPoint(point),
            "the point was deleted after all, so the refusals above did not refuse");

        layout.deleteEdge(from, to);

        assertNull(layout.getEdge(from, to),
            "the guard is refusing an idle layout too, which makes the graph uneditable");

        // A point still wired to something refuses on its own account - "delete edges first" - which
        // is a different refusal and would pass this for the wrong reason.  So the wiring goes first.
        for (Edge e : new ArrayList<>(layout.getEdges()))
        {
            if (point.equals(e.getStart().getName()) || point.equals(e.getEnd().getName()))
            {
                layout.deleteEdge(e.getStart().getName(), e.getEnd().getName());
            }
        }

        layout.deletePoint(point);

        assertNull(layout.getPoint(point),
            "an idle layout refused to delete a point with nothing left attached to it");
    }
    /**
     * And while a run is going (OB-264): the same three edits refused with `isRunning()` true and staging off, the
     * other half of the OR the guards are written as.  The running flag is set by reflection as `executeTimetable`
     * sets it: what is under test is the guard, not a dispatch.  Then idle, and the same edits go through.
     *
     * MUTATION: drop `isRunning() ||` from any of the three guards and this fails.
     *
     * @throws Exception on a failure to build
     */
    @Test
    public void testTheGraphCannotBeEditedWhileARunIsGoing() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertFalse(layout.isRunning() || layout.isStagingInProgress(), "precondition: the layout must start idle");

        Edge victim = new ArrayList<>(layout.getEdges()).get(0);
        String from = victim.getStart().getName();
        String to = victim.getEnd().getName();
        String point = layout.getPoints().iterator().next().getName();

        Field running = Layout.class.getDeclaredField("running");

        running.setAccessible(true);

        running.set(layout, true);

        try
        {
            assertTrue(layout.isRunning() && !layout.isStagingInProgress(), "precondition: the run is not seen");

            try
            {
                layout.renamePoint(point, point + " renamed");
                fail("renamePoint let a rename through while a run was going (OB-264)");
            }
            catch (Exception expected)
            {
            }

            try
            {
                layout.deleteEdge(from, to);
                fail("deleteEdge removed " + from + " -> " + to + " while a run was going (OB-264)");
            }
            catch (Exception expected)
            {
            }

            try
            {
                layout.deletePoint(point);
                fail("deletePoint removed " + point + " while a run was going (OB-264)");
            }
            catch (Exception expected)
            {
            }
        }
        finally
        {
            running.set(layout, false);
        }

        assertNotNull(layout.getPoint(point), "the point was deleted after all");

        layout.deleteEdge(from, to);

        assertNull(layout.getEdge(from, to), "the guard refuses an idle layout too");
    }

    /**
     * The model can see a staging flow, not merely a dispatched run.
     *
     * Six guards ask the *model* whether autonomy is busy - locomotive delete, rename, address change,
     * the rename-proposal pass, the diagram save, and the sync's address adoption - and the model
     * answered on isRunning() alone.  Nothing is dispatched while a plan is being derived, so all six
     * read the planning window as idle: a locomotive could be deleted out from under a plan about to
     * drive it, or re-addressed so the plan's commands reach a decoder the train no longer answers.
     *
     * The flag has to live on the Layout rather than the UI, because the sync deferral is model-side
     * and no UI predicate can ever cover it.
     */
    @Test
    public void testTheModelSeesAStagingFlowAsAutonomyRunning() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertFalse(model.isAutonomyRunning(), "precondition: nothing is running or being planned");

        layout.setStagingInProgress(true);

        assertTrue(layout.isStagingInProgress());
        assertTrue(model.isAutonomyRunning(),
            "the model has to see a staging flow, or every model-side guard treats the planning "
            + "window as idle");

        layout.setStagingInProgress(false);

        assertFalse(model.isAutonomyRunning(), "and stop seeing it once the flow is done");
    }

    /**
     * The timetable can be read as a snapshot, while the getter stays live for the callers that edit it.
     *
     * Locomotive threads append to the timetable under the Layout monitor whenever capture is on during
     * a run, and the EDT iterates the same list to repaint - the table, and the legend that marks a
     * locomotive at its timetable start.  Neither reader holds the monitor, and both use iterators.
     *
     * The getter cannot simply return a copy: deleteTimetableEntry removes from the list it hands back,
     * so a snapshot there would make that deletion apply to nothing.  Hence two accessors, and this
     * pins the difference between them.
     */
    @Test
    public void testTheTimetableSnapshotIsACopyWhileTheGetterStaysLive() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        giveTimetable(layout, LOC_A);

        List<TimetablePath> snapshot = layout.getTimetableSnapshot();
        int before = snapshot.size();

        assertTrue(before > 0, "precondition: the fixture must have given us a timetable");

        // What deleteTimetableEntry does - it mutates the list the getter returns
        layout.getTimetable().remove(0);

        assertEquals(snapshot.size(), before,
            "a snapshot handed to a repaint must not change underneath the repaint");
        assertEquals(layout.getTimetable().size(), before - 1,
            "while the getter stays live, which is what the editing surfaces depend on");
    }


    /**
     * A derived home is not an assignment, and the display must not treat them alike.
     *
     * getHomeStation answers with the positional fallback as well as the assignments, which is right
     * for the planner - Return Home moves a locomotive back to where it started whether or not anyone
     * said so.  It is wrong for the locomotive list, which paints "standing at home" teal: on a graph
     * nobody has assigned anything on, every placed locomotive is standing on its derived home, so the
     * whole list went teal while the graph - which reads the assignments - outlined nothing.
     *
     * Assigning one locomotive elsewhere then moves the count for locomotives nobody touched, because
     * an assignment makes rebuildHomeStations refuse the fallback claims that collide with it.  That is
     * the shape of the reported bug, and this pins the difference the two readings produce.
     */
    @Test
    public void testADerivedHomeIsNotAnAssignment() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, LOC_C));

        assertFalse(layout.hasHomeLocomotives(), "precondition: this graph assigns nothing");

        for (Point point : layout.getPoints())
        {
            assertNull(point.getHomeLoc(), point.getName() + " carries no assignment");

            if (point.getCurrentLocomotive() != null)
            {
                assertEquals(layout.getHomeStation(point.getCurrentLocomotive()), point,
                    point.getName() + " is nonetheless its occupant's derived home");
            }
        }

        // One locomotive given a station it is not standing on - HS D is empty, LOC_A is at HS A
        layout.setHomeLocomotive("HS D", LOC_A);

        int atAssignedHome = 0;
        int atDerivedHome = 0;

        for (Point point : layout.getPoints())
        {
            Locomotive here = point.getCurrentLocomotive();

            if (here == null) continue;

            if (here.equals(point.getHomeLoc())) atAssignedHome++;
            if (point.equals(layout.getHomeStation(here))) atDerivedHome++;
        }

        assertEquals(atAssignedHome, 0,
            "nothing is standing on a station that was assigned to it, which is what the graph outlines "
            + "and what the locomotive list must agree with");

        assertEquals(atDerivedHome, 2,
            "while two locomotives stand on homes that were only ever derived - the reading that used "
            + "to paint them teal against a graph showing nothing");
    }

    /**
     * getHomeStations hands back a snapshot, not a window onto the live map.  The planner reads it on a worker thread
     * while placement, deletions and rebuilds write to it elsewhere; as a view that is a
     * ConcurrentModificationException or a plan built from half the homes, and turning it back into a view is a
     * one-word change nothing else notices.
     */
    @Test
    public void testGetHomeStationsIsASnapshotRatherThanALiveView() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        Map<Locomotive, Point> taken = layout.getHomeStations();
        int before = taken.size();

        assertEquals(taken.get(loc(LOC_A)), layout.getPoint("HS A"), "precondition: derived from placement");

        // Two different writers, either of which a view would expose to a reader mid-iteration
        layout.setHomeLocomotive("HS D", LOC_A);                  // rebuild: clear and repopulate
        assertTrue(layout.moveLocomotive(LOC_C, "HS C", false));   // claimHome: a put

        assertEquals(taken.size(), before, "a map handed out earlier must not grow underneath its reader");
        assertEquals(taken.get(loc(LOC_A)), layout.getPoint("HS A"), "nor change what it already said");

        assertEquals(layout.getHomeStations().get(loc(LOC_A)), layout.getPoint("HS D"),
            "while a fresh read does see the change");
        assertEquals(layout.getHomeStations().size(), before + 1);
    }

    // Point types in the path, rather than only at its end
    //
    // The suite pinned canBeHome - the rule for where a locomotive may COME TO REST - and nothing at
    // all about what it may drive through.  These cover the traversal half: terminus, reversing,
    // non-station, exclusions and shared sensors, in the middle of a route rather than at the end.

    /**
     * The four-station ring, with extra JSON spliced onto any of its stations.
     *
     * Named apart from ring(String, String, String) rather than overloading it: three nulls match both
     * signatures and neither is more specific, so ring(null, null, null) stopped compiling.
     */
    private static String ringWith(String[] locs, String[] extras)
    {
        return ringWith(locs, extras, new int[]{0, 1, 2, 3});
    }

    /**
     * The ring with explicit sensor offsets, so two stations can be given one address.
     *
     * A separate argument rather than another entry in extras: station() already emits an s88, and
     * splicing a second one produced a duplicate key and a graph that would not parse at all.
     */
    private static String ringWith(String[] locs, String[] extras, int[] s88Offsets)
    {
        StringBuilder points = new StringBuilder();

        for (int i = 0; i < 4; i++)
        {
            String raw = station("HS " + (char) ('A' + i), s88Offsets[i], locs[i]);

            if (extras[i] != null)
            {
                // Splice before the closing brace, and assert it landed - a fixture that silently
                // dropped the flag would make every test below pass while testing nothing
                assertTrue(raw.endsWith("}"), "station JSON shape changed: " + raw);
                raw = raw.substring(0, raw.length() - 1) + ", " + extras[i] + "}";
            }

            if (i > 0) points.append(",");

            points.append(raw);
        }

        String config = json("{'points': [" + points + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("HS C", "HS D") + "," + edge("HS D", "HS C") + ","
            + edge("HS D", "HS A") + "," + edge("HS A", "HS D")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");

        for (String extra : extras)
        {
            if (extra != null) assertTrue(config.contains(json(extra)), "fixture lost " + extra);
        }

        return config;
    }

    /**
     * Three mutually connected stations, the third a terminus.
     *
     * Used where the terminus has to be the ONLY place a train can step aside to.  Carving that out of
     * the ring with exclusions would have worked today and broken the moment exclusions start applying
     * to stations driven through - this says what it means instead.
     */
    private static String triangleWithTerminus(String locAtA, String locAtB)
    {
        String t = station("HS T", 2, null);

        assertTrue(t.endsWith("}"), "station JSON shape changed: " + t);

        return json("{'points': ["
            + station("HS A", 0, locAtA) + ","
            + station("HS B", 1, locAtB) + ","
            + t.substring(0, t.length() - 1) + ", 'terminus': true}"
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS T") + "," + edge("HS T", "HS B") + ","
            + edge("HS T", "HS A") + "," + edge("HS A", "HS T")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A blocker with a home of its own does not make staging impossible.
     *
     * The impossibility scan proves "no move can ever end there", so its tests are state-INDEPENDENT; `connected` says
     * so: a route blocked merely by another train is not impossible, since moving that train is what the planner is
     * for.  OB-073's state-AWARE rest test read the starting occupancy, so a locomotive standing on a watched square
     * proved the goal unreachable even when the plan's own first move vacates it.
     *
     * The blocker here has a home elsewhere, the difference between "in the way" and "stuck": one with no home
     * genuinely cannot be moved and IMPOSSIBLE is right for it.
     */
    @Test
    public void testABlockerWithAHomeOfItsOwnIsNotAProofOfImpossibility() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // A wants HS B, which is held back while HS D is occupied.
        layout.getPoint("HS B").setBlockedBy(
            java.util.Arrays.asList(layout.getPoint("HS D")));

        // Without this the fixture could stop taking and the test would pass vacuously, on a
        // station nothing is watching.
        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS B this tests nothing at all");

        assign(layout, LOC_A, "HS B");

        // And B has a home of its own, which is NOT the square it is standing on. Staging will move
        // it, and moving it clears the square A is waiting for.
        assign(layout, LOC_B, "HS C");

        assertTrue(layout.moveLocomotive(LOC_A, "HS A", false), "the fixture could not be arranged");
        assertTrue(layout.moveLocomotive(LOC_B, "HS D", false),
            "could not stand the blocker on the watched point");

        HomeStaging.Plan plan = layout.planReturnToHome();

        // THE OUTCOME, not merely "not IMPOSSIBLE": `assertNotEquals(IMPOSSIBLE)` is satisfied by five other outcomes,
        // three of which mean the planner did nothing (NO_LOCOMOTIVES, NO_HOMES, the search limit).  A movable blocker
        // must not stop a plan being FOUND, so the outcome is READY and the proof is the moves.
        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "the planner did not find the two-move answer. B is standing on the square that holds A's"
            + " home back, and B is itself being staged to HS C - so the first move of the plan clears"
            + " it. IMPOSSIBLE is presented to the operator as a proof, with the locomotives named, and"
            + " it skips the search that would have found this (FBR-B1). Got: " + plan.getOutcome());

        // AND THE PLAN ACTUALLY MOVES BOTH, which READY alone does not promise: an empty plan is a
        // plan. Named rather than counted, so a plan that moves one train twice cannot pass.
        java.util.Set<String> moved = new java.util.LinkedHashSet<>();

        for (HomeStaging.Move move : plan.getMoves())
        {
            if (move.getLocomotive() != null) moved.add(move.getLocomotive().getName());
        }

        assertTrue(moved.contains(LOC_B),
            "the plan does not move the blocker, so whatever it found is not the answer this test is"
            + " about: B leaving HS D is what frees A's home. Moved: " + moved);

        assertTrue(moved.contains(LOC_A),
            "the plan frees A's home and then leaves A where it is. Moved: " + moved);

        assertFalse(plan.getBlocked().contains(loc(LOC_A)),
            "and A must not be named as blocked when what is in its way is about to leave");
    }

    /**
     * Two homes that hold each other back are a real deadlock.
     *
     * HS C is held back while HS D is occupied and HS D while HS C is, each the other occupant's home.  The
     * restriction is enforced in every tier (Adam: *"enforce the occupancy ruling in all modes and then rely on
     * isPathClear"*), so the planner cannot stage it and says NO_PLAN_FOUND, not IMPOSSIBLE: IMPOSSIBLE names
     * locomotives and asserts that no arrangement exists, and the scan that produced it (OB-085) was removed for
     * proving false things about working railways.  IMPOSSIBLE appearing here means that scan has come back.
     *
     * The control is the same fixture with one hold removed, so NO_PLAN_FOUND is the mutual hold and not a ring
     * nothing can be staged on.
     */
    @Test
    public void testTwoHomesThatHoldEachOtherBackAreADeadlock() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assign(layout, LOC_A, "HS C");
        assign(layout, LOC_B, "HS D");

        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));
        layout.getPoint("HS D").setBlockedBy(Arrays.asList(layout.getPoint("HS C")));

        assertEquals(layout.getPoint("HS C").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS C this tests nothing at all");
        assertEquals(layout.getPoint("HS D").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS D this tests nothing at all");

        assertNotEquals(layout.getPoint("HS C"), locationOfLoc(layout, LOC_A),
            "precondition: a train already on its home never arrives, and nothing would be checked");
        assertNotEquals(layout.getPoint("HS D"), locationOfLoc(layout, LOC_B),
            "precondition: a train already on its home never arrives, and nothing would be checked");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.NO_PLAN_FOUND,
            "two homes that hold each other back are a deadlock since the restriction became every"
            + " tier's: whichever arrives last finds its station closed by a train already parked."
            + "  READY means the planner has stopped reading FR-001 again; IMPOSSIBLE means the"
            + " OB-085 cycle scan has come back, and it was wrong three times.  Got: "
            + plan.getOutcome());

        // THE CONTROL: one hold instead of two, and the same ring stages perfectly well - so the
        // refusal above is the mutual hold rather than a fixture nothing can be staged on.
        layout.getPoint("HS D").setBlockedBy(new java.util.LinkedList<Point>());

        HomeStaging.Plan freed = HomeStaging.snapshot(layout).plan();

        assertEquals(freed.getOutcome(), HomeStaging.Outcome.READY,
            "with only one of the two holds in place this ring still cannot be staged, so the"
            + " deadlock above says nothing about the pair of restrictions.  Got: " + freed);

        applyPlan(layout, freed);

        assertEveryoneHome(layout);
    }

    /**
     * A sensor shared with an approach guard does not make an ordinary layout impossible (OB-085).
     *
     * The scan asked "would a train standing there close this station" through `sameTrackAs`, which unions block copies
     * AND the points reporting the same feedback address; the sensor half is conservative on purpose (a refused plan,
     * never a wrong movement).  But IMPOSSIBLE is a PROOF, naming locomotives and asserting no arrangement exists, so
     * it may only be built out of the relation the railway enforces, the block.  The fixture builds its restrictions
     * out of a shared sensor, which reaches the widened relation.
     *
     * The scan is removed; this stays as the line it drew: no verdict of IMPOSSIBLE may be built out of the sensor
     * relation.
     */
    @Test
    public void testASharedSensorDoesNotMakeAnOrdinaryLayoutImpossible() throws Exception
    {
        Layout layout = load(twoHomesAndAGuardSharingASensor());

        assign(layout, LOC_A, "HS C");
        assign(layout, LOC_B, "HS D");

        // One way: HS C waits for HS D to be clear.
        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));

        // The other way is about the GUARD, not about HS C.
        layout.getPoint("HS D").setBlockedBy(Arrays.asList(layout.getPoint("HS W2")));

        assertEquals(layout.getPoint("HS W2").getS88(), layout.getPoint("HS C").getS88(),
            "the fixture did not take: the guard has to share HS C's feedback for this to be the "
            + "case that went wrong");

        assertNull(layout.getPoint("HS W2").getBlock(),
            "the fixture did not take: the guard must NOT be a block copy of anything, or this is a "
            + "different case entirely");

        assertNotEquals(layout.getPoint("HS W2"), layout.getPoint("HS C"),
            "the fixture did not take: they have to be different squares");

        // What the railway says, asked in the order the arrangement is built - the same call
        // isPathClear makes.
        assertNull(Point.heldBackBy(layout.getPoint("HS C"), loc(LOC_A)),
            "precondition: with HS D empty the railway lets a train into HS C, so an arrangement "
            + "exists and IMPOSSIBLE would be a false claim");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "an ordinary layout - one one-way hold, and an approach guard sharing a feedback address "
            + "with the other platform - was declared impossible.  The railway stages it: park at "
            + "HS C while HS D is empty, then park at HS D.  Blocked: " + plan.getBlocked());
    }

    /**
     * The blocked list names the locomotive that genuinely cannot get home, and nobody else (OB-085).
     *
     * The cycle argument rests on both trains actually parking; if one can never get home (no route, a home it cannot
     * rest at) the square stays clear and the other is free to arrive.  The plan is impossible either way, but the LIST
     * is what the operator reads, and naming a locomotive that could get home sends them looking for a fault that is
     * not there.  The `unreachable` scan it comes from is untouched.
     */
    @Test
    public void testACycleDoesNotNameATrainThatWasAlreadyStuck() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assign(layout, LOC_A, "HS C");
        assign(layout, LOC_B, "HS D");

        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));
        layout.getPoint("HS D").setBlockedBy(Arrays.asList(layout.getPoint("HS C")));

        // LOC_B cannot rest at HS D whatever anybody else does, so it never parks there - which means
        // HS C is never actually held back.
        layout.getPoint("HS D").setActive(false);

        assertFalse(layout.getPoint("HS D").isActive(),
            "the fixture did not take: HS D has to be somewhere LOC_B cannot come to rest");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "LOC_B has a home it cannot rest at, so this arrangement really is impossible");

        assertTrue(plan.getBlocked().contains(loc(LOC_B)),
            "the locomotive that genuinely cannot get home is not named.  Got: " + plan.getBlocked());

        assertFalse(plan.getBlocked().contains(loc(LOC_A)),
            "a locomotive that could park at HS C perfectly well - because the train supposedly "
            + "holding it back can never arrive - was named as blocked.  That sends the operator "
            + "looking for a fault that is not there.  Got: " + plan.getBlocked());
    }

    /**
     * A hold in ONE direction is an ordering, not an impossibility (OB-085).
     *
     * The control for the pair: with one of the two restrictions removed the fixture is solvable (park at HS C first
     * while HS D is empty, then HS D, which nothing watches), and a scan reporting it impossible would refuse
     * arrangements the railway can make.  The cycle scan is gone; this outlives it as the second of the pair that says
     * an occupancy restriction may not produce IMPOSSIBLE.  The other is
     * testAHomeHeldBackByAnOccupiedPointStillGetsAnExecutablePlan (the plan still executes).
     */
    @Test
    public void testAOneWayHoldIsJustAnOrdering() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assign(layout, LOC_A, "HS C");
        assign(layout, LOC_B, "HS D");

        // One direction only.
        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));

        assertTrue(layout.getPoint("HS D").getBlockedBy().isEmpty(),
            "the fixture did not take: this test is about there being NO cycle");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "a station held back by another station's square is an ordering constraint, not a "
            + "contradiction - park at HS C while HS D is empty, then park at HS D.  Reporting this "
            + "impossible refuses an arrangement the railway can make");
    }

    /**
     * Two trains already standing on their own homes are not in a cycle, whatever watches what: a station's
     * restrictions are read when a train ARRIVES, and parked trains never arrive.
     *
     * A third locomotive is out of place, so the planner has work to do and cannot answer from `triage()` before the
     * scan is reached.
     *
     * MUTATION-CHECKED: deleting the both-already-parked exemption from the scan fails this test.
     */
    @Test
    public void testTwoTrainsAlreadyAtHomeAreNotACycle() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, LOC_C));

        // Each of the two is standing on the home it is given.
        assign(layout, LOC_A, "HS A");
        assign(layout, LOC_B, "HS B");

        // And a third that is not, so there is something for the planner to do.
        assign(layout, LOC_C, "HS D");

        layout.getPoint("HS A").setBlockedBy(Arrays.asList(layout.getPoint("HS B")));
        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS A")));

        assertEquals(layout.getPoint("HS A"), locationOfLoc(layout, LOC_A),
            "the fixture did not take: this test is about trains that are ALREADY home");
        assertEquals(layout.getPoint("HS B"), locationOfLoc(layout, LOC_B),
            "the fixture did not take: this test is about trains that are ALREADY home");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.getBlocked().contains(loc(LOC_A)) || plan.getBlocked().contains(loc(LOC_B)),
            "two trains already on their homes never arrive anywhere, so no restriction is ever "
            + "consulted and neither is unreachable.  Got: " + plan.getBlocked());
    }

    /**
     * Where a locomotive is standing, by name, for the preconditions above.
     *
     * @param layout the layout
     * @param locName the locomotive
     * @return the point it stands on, or null
     */
    private static Point locationOfLoc(Layout layout, String locName)
    {
        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() != null && p.getCurrentLocomotive().equals(loc(locName)))
            {
                return p;
            }
        }

        return null;
    }

    /**
     * Saving a layout that has not changed produces the same file it produced last time.
     *
     * `Point.excludedLocs` is a set of Locomotive objects with identity hashCodes, so iteration order differs on every
     * JVM run; without a stable order, opening and saving a layout rewrote the file with nothing changed (a sync on
     * every launch for a layout kept in OneDrive).
     *
     * Written as "two sets built in opposite orders serialise identically", the property, not "run it twice and hope
     * the hashes differ".  The sets are `LinkedHashSet`s, which keep the fixture's order: a HashSet discards insertion
     * order, so the two strings would be equal whatever `toJSON` did.
     *
     * MUTATION: removing the `Collections.sort(locNames)` from `Point.toJSON` fails this.
     */
    @Test
    public void testAnUnchangedLayoutSerialisesTheSameWayTwice() throws Exception
    {
        Layout one = load(ring(LOC_A, LOC_B, LOC_C));
        Layout two = load(ring(LOC_A, LOC_B, LOC_C));

        java.util.List<String> names = Arrays.asList(LOC_A, LOC_B, LOC_C);

        // The same three locomotives, added in opposite orders.
        // LinkedHashSet, NOT HashSet: see the javadoc.
        java.util.Set<Locomotive> forward = new java.util.LinkedHashSet<>();
        java.util.Set<Locomotive> backward = new java.util.LinkedHashSet<>();

        for (String name : names) forward.add(loc(name));

        for (int at = names.size() - 1; at >= 0; at--) backward.add(loc(names.get(at)));

        one.getPoint("HS D").setExcludedLocs(forward);
        two.getPoint("HS D").setExcludedLocs(backward);

        assertEquals(one.getPoint("HS D").getExcludedLocs().size(), 3,
            "the fixture did not take: with fewer than two excluded locomotives there is no order to "
            + "get wrong");

        assertEquals(one.getPoint("HS D").toJSON().toString(),
            two.getPoint("HS D").toJSON().toString(),
            "the same set of excluded locomotives wrote itself out two different ways, so saving a "
            + "layout nothing has changed produces a different file every time");
    }

    /**
     * The home rule at the door it was made about, and not at the other one (MT-165).
     *
     * `parseAuto` calls `Point.setHomeLoc` directly, so a hand-edited or imported configuration installs a home the
     * menu would refuse; that model door exists because a file cannot be grayed out.  The other door is the POSITIONAL
     * default in `claimHome`, which makes a square the home of whatever stands on it.
     *
     * Adam: "unless there is an explicit home, the home should be where the train started at startup".  An ASSIGNMENT
     * is a person naming a station, with no way to know which copy they meant; a POSITIONAL home is the graph noticing
     * where a train IS, and the copy is the one under the wheels.  Refusing it would leave no train on a main-line
     * platform with a home, and Return Home dark.  The ambiguity moves to the far end (a train returning on the other
     * copy), which the third assertion holds: the copies of a square are one place.
     *
     * MUTATION: putting back `homeAt.setHomeLoc(home)` unconditionally in `parseAuto` fails the first half; putting
     * back the `p.getBlock() != null` return in `claimHome` fails the second; making `atHome` compare Points rather
     * than squares fails the third.
     */
    @Test
    public void testTheHomeRuleReachesTheDoorsHomesActuallyComeFrom() throws Exception
    {
        // The file door: a configuration naming a home on a square that is two graph Points.
        Layout fromFile = load(blockAssigningItsWatchedSquare(LOC_A));

        Point copy = fromFile.getPoint("HS W2");

        assertNotNull(copy.getBlock(),
            "the fixture did not take: this test needs a square emitted as more than one Point");

        // KEPT: the home is the square, so a square drawn as several Points is an ordinary home.
        assertEquals(copy.getHomeLoc(), loc(LOC_A),
            "a home named by a configuration file was dropped as it loaded because its square is "
            + "drawn as more than one graph Point. The home is the SQUARE - a train on any copy of it "
            + "is home - so there is nothing ambiguous to refuse, and silently dropping it is a "
            + "Return Home that quietly does something else");

        // And the control: an ordinary square in the same file keeps the home it was given, so the
        // load is not simply dropping homes.
        Layout ordinary = load(ring(LOC_A, LOC_B, LOC_C));

        Point plain = ordinary.getPoint("HS D");

        assertNull(plain.getBlock(), "the control square must not be a multi-point square");

        ordinary.setHomeLocomotive("HS D", LOC_A);

        assertEquals(plain.getHomeLoc(), loc(LOC_A),
            "an ordinary square stopped accepting a home, so the rule refuses more than it was asked");

        ordinary.setHomeLocomotive("HS D", null);

        // The positional door: a train standing on such a square DOES derive a home from it (Adam: "unless there is an
        // explicit home, the home should be where the train started at startup"); main-line platforms are such squares.
        Layout derived = load(blockOfTwoWatching(null, null));

        Point standing = derived.getPoint("HS W2");

        assertNotNull(standing.getBlock(), "the fixture did not take");

        standing.setLocomotive(loc(LOC_A));

        derived.rebuildHomeStations();

        assertTrue(derived.getHomeStations().containsValue(standing),
            "a train standing on a split square was given no home, so Return Home has nothing to "
            + "offer for it - which on Adam's railway is most of the platforms trains stand on");

        // AND THE HALF THAT MAKES IT SAFE: the copies are one place.  A train coming back on the far copy of its own
        // platform must count as home; judged by Point identity the planner would try to move it to the exact copy,
        // which may be impossible.
        Point otherCopy = derived.getPoint("HS W1");

        assertNotNull(otherCopy.getBlock(), "the fixture did not take: HS W1 must be the other copy");

        assertEquals(otherCopy.getBlock(), standing.getBlock(),
            "the fixture did not take: the two copies must share a block, or this asks nothing");

        standing.setLocomotive(null);
        otherCopy.setLocomotive(loc(LOC_A));

        assertEquals(HomeStaging.snapshot(derived).triage(), HomeStaging.Outcome.ALREADY_HOME,
            "a train standing on the other copy of its own home platform was judged not home.  The "
            + "copies of a square are one piece of track - that is what a block IS - so the planner "
            + "would have tried to move it onto a particular arrival side of the platform it is "
            + "already standing on");

        otherCopy.setLocomotive(null);
    }

    /**
     * A square that is more than one graph Point can be a home.
     *
     * Adam: "the home should just be the logical point, and the direction is wherever the locomotive was facing when
     * it started moving."  One square, one answer, however many arrival sides it is drawn as.
     *
     * `getBlock()` identifies such a square: `AutonomyBuilder` sets it only when a square was emitted as more than one
     * Point.  Accepted at the MODEL door, so a hand-edited file, an imported configuration and the scripting API agree
     * with the menu.
     *
     * MUTATION: putting a `p.getBlock() != null` refusal back into `Layout.setHomeLocomotive` fails this test.
     */
    @Test
    public void testAHomeCanBeASquareThatIsSeveralPoints() throws Exception
    {
        Layout layout = load(blockOfTwoWatching(null, null));

        Point copy = layout.getPoint("HS W2");

        assertNotNull(copy.getBlock(),
            "the fixture did not take: this test needs a square emitted as more than one Point, "
            + "which is exactly what carrying a block means");

        // ACCEPTED: the home is the square, so there is nothing ambiguous about it.
        layout.setHomeLocomotive("HS W2", LOC_A);

        assertEquals(copy.getHomeLoc(), loc(LOC_A),
            "a home on a square drawn as two arrival sides was refused. The home is the square, so "
            + "there is nothing ambiguous about it - and on Adam's own layout this refusal covered "
            + "ten of the thirty-six station squares, which are the main-line platforms");

        layout.setHomeLocomotive("HS W2", null);

        // And the control: an ordinary single-Point square is still assignable, so the rule is not
        // simply refusing homes.
        Point ordinary = layout.getPoint("HS C");

        assertNull(ordinary.getBlock(),
            "the fixture did not take: the control square must NOT be a multi-point square");

        layout.setHomeLocomotive("HS C", LOC_A);

        assertEquals(ordinary.getHomeLoc(), loc(LOC_A),
            "an ordinary square stopped being assignable, so the rule is refusing far more than it "
            + "was asked to");

        layout.setHomeLocomotive("HS C", null);
    }

    /** Assigns homes directly, which is clearer here than rewriting fixture JSON per case. */
    private static void assign(Layout layout, String locName, String stationName) throws Exception
    {
        layout.setHomeLocomotive(stationName, locName);
    }

    /**
     * Sets reversibility on both test locomotives and returns what they were.
     *
     * Locomotive state outlives load(), which re-parses the graph and not the database, so anything
     * changed here has to be put back exactly - restoring a hardcoded "true" would hand every later
     * test a locomotive this one had quietly made reversible.
     */
    private static boolean[] setReversible(boolean state, String... names)
    {
        boolean[] was = new boolean[names.length];

        for (int i = 0; i < names.length; i++)
        {
            was[i] = loc(names[i]).isReversible();
            loc(names[i]).setReversible(state);
        }

        return was;
    }

    private static void restoreReversible(boolean[] was, String... names)
    {
        for (int i = 0; i < names.length; i++) loc(names[i]).setReversible(was[i]);
    }

    // Terminus

    /**
     * A terminus is a legitimate place to put a train mid-plan.
     *
     * Two locomotives standing on each other's homes can only be unwound by parking one somewhere
     * else first.  Here the only free station is a terminus, so the plan exists if and only if a
     * terminus may be an intermediate stop.  A* offers every active destination as a target and gates
     * it through canRest, which permits a terminus to a reversible locomotive - this pins that, and
     * pins that the train can leave again afterwards.
     */
    @Test
    public void testATerminusCanBeTheEndOfAnIntermediateMove() throws Exception
    {
        Layout layout = load(triangleWithTerminus(LOC_A, LOC_B));

        assertTrue(layout.getPoint("HS T").isTerminus(), "precondition: HS T is the terminus");

        assign(layout, LOC_A, "HS B");
        assign(layout, LOC_B, "HS A");

        // Set rather than asserted: a locomotive is not reversible by default
        boolean[] was = setReversible(true, LOC_A, LOC_B);

        try
        {
            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertTrue(plan.isPossible(),
                "a swap needs one train parked elsewhere, and the terminus is the only elsewhere: " + plan);

            applyPlan(layout, plan);

            boolean usedTerminus = false;

            for (HomeStaging.Move m : plan.getMoves())
            {
                if (m.getEnd().getName().equals("HS T")) usedTerminus = true;
            }

            assertTrue(usedTerminus, "the terminus has to be the station used: " + plan.getMoves());
        }
        finally
        {
            restoreReversible(was, LOC_A, LOC_B);
        }
    }

    /**
     * ...but only for a locomotive that can reverse out of it again.
     *
     * The same fixture with a non-reversible locomotive: the terminus is unusable, so there is nowhere
     * to step aside and no plan exists.  isPathClear refuses a terminus to a non-reversible locomotive,
     * so a planner that offered it would be planning a move the runtime then rejects.
     */
    @Test
    public void testANonReversibleLocomotiveMayBeParkedAtATerminus() throws Exception
    {
        Layout layout = load(triangleWithTerminus(LOC_A, LOC_B));

        assign(layout, LOC_A, "HS B");
        assign(layout, LOC_B, "HS A");

        boolean[] was = setReversible(false, LOC_A, LOC_B);

        try
        {
            // SOMETHING HAS TO TURN THEM ROUND: Adam: "non-reversing trains have to back in", so a terminus is
            // reachable for these two only if a reversing point lies on the way; without one the fixture asks a
            // different question.
            layout.getPoint("HS B").setReversing(true);

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            // POSSIBLE (Adam: "trains should be allowed to back into terminuses if they are not reversible (that's why
            // we have the reversing point at feedback 2013)."): a terminus is not a reason a train cannot LIVE
            // somewhere; whether it can get there is a route question, and Layout.isPathClear asks it.
            assertTrue(plan.isPossible(),
                "a terminus was treated as somewhere a non-reversible train can never rest, which is "
                + "the rule Adam reversed: most parking berths are terminuses, and a train backs into "
                + "one past a reversing point. " + plan);
        }
        finally
        {
            restoreReversible(was, LOC_A, LOC_B);
        }
    }

    /**
     * A train that cannot reverse is turned on the way home only to back into its berth.
     *
     * The planner rested any train on any station, and the run turns a train at every terminus and reversing point it
     * arrives at (`ALWAYS_REVERSE`), so a train that cannot reverse could be stepped aside onto a square that turns it
     * and driven on the other way, the tier disagreement the right-click menu refuses since OB-205.  Adam: *"this
     * should only be allowed if the train is going to reverse into its berth on the next turn."*
     *
     * The fixture has one way home and it turns the train twice: P -> T1 -> T2 -> R, one way, T1 and T2 termini.  A
     * route never passes a terminus, so the plan is three moves.  THE CONTROL is the same railway with a train that can
     * reverse, for which that plan is right.  The oracle is the ruling itself, asked of every move, so the assertion
     * holds however the planner finds its answer.
     */
    @Test
    public void testATrainThatCannotReverseIsTurnedOnTheWayOnlyToGoHome() throws Exception
    {
        Layout layout = load(twoTurnsBeforeHome());

        assign(layout, LOC_A, "HS R");

        boolean[] was = setReversible(true, LOC_A);

        try
        {
            HomeStaging.Plan reversible = HomeStaging.snapshot(layout).plan();

            assertEquals(reversible.getOutcome(), HomeStaging.Outcome.READY,
                "control: a train that can reverse has no plan home on a railway with one way there: " + reversible);

            assertNotNull(turnedAndNotSentHome(layout, reversible, true),
                "precondition: the plan home does not rest the train on a square that turns it and then send it"
                + " somewhere other than home, so this fixture cannot show the rule: " + reversible.getMoves());

            loc(LOC_A).setReversible(false);

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            String broken = turnedAndNotSentHome(layout, plan, false);

            assertNull(broken,
                "Return Home rests " + LOC_A + ", which cannot reverse, on a square that turns it and then sends it on"
                + " somewhere other than its home - " + broken + ".  Adam: only to reverse into its berth on the next"
                + " turn (AMH-B1)");
        }
        finally
        {
            restoreReversible(was, LOC_A);
        }
    }

    /**
     * A train that cannot reverse is not turned in the MIDDLE of a move and sent on.
     *
     * A route may turn a train at a reversing point on the way (how a train that cannot reverse backs into a berth,
     * MT-245) and carry on, arriving the wrong way round.  Adam: *"It should be the first option, but the locking
     * mechanism will refuse it.  That's why we started the 2 step process for parking, which is OK in my opinion."*  So
     * a turn on the way is allowed only where the move ends at the train's home, or at a square it comes to rest facing
     * out of (a berth or a terminus).
     *
     * X and H either side of a reversing point R, with a spare Y beyond it that only the train which cannot reverse
     * may use.  The swap needs Y, and the only way to Y turns the train there.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATrainThatCannotReverseIsNotTurnedMidMoveAndSentOn() throws Exception
    {
        Layout layout = load(aReversingPointInTheMiddle());

        Point spare = layout.getPoint("HS Y");

        layout.getPoint("HS R").setReversing(true);

        // SWAPPED, so both trains are away from home and the spare is needed: the fixture stands LOC_A at HS H and
        // LOC_B at HS X, and homing each where it already is answers ALREADY_HOME.
        assign(layout, LOC_A, "HS X");
        assign(layout, LOC_B, "HS H");

        boolean[] was = setReversible(true, LOC_A, LOC_B);

        try
        {
            // Only the train that cannot reverse may use the spare, so the plan has just one shape.
            java.util.Set<org.traincontrol.base.Locomotive> keepOut = new java.util.HashSet<>();
            keepOut.add(loc(LOC_A));
            spare.setExcludedLocs(keepOut);

            HomeStaging.Plan reversible = HomeStaging.snapshot(layout).plan();

            assertEquals(reversible.getOutcome(), HomeStaging.Outcome.READY,
                "control: the swap has no plan even for trains that can reverse: " + reversible);

            // THE FIXTURE'S SHAPE, not a violation judged as if nobody can reverse: the oracle
            // clears a turn whose next move goes home, which this plan's does.  What this claim needs is that
            // the spare really is reached by a route that turns the train.
            HomeStaging.Move ontoTheSpare = null;

            for (HomeStaging.Move move : reversible.getMoves())
            {
                if (move.getEnd().getName().equals("HS Y")) ontoTheSpare = move;
            }

            assertNotNull(ontoTheSpare,
                "precondition: no move of the plan uses the spare HS Y at all, so this fixture cannot show the rule: "
                + reversible.getMoves());

            boolean turnsOnTheWayIn = false;

            for (int e = 0; e + 1 < ontoTheSpare.getPath().size(); e++)
            {
                if (ontoTheSpare.getPath().get(e).getEnd().isReversing()) turnsOnTheWayIn = true;
            }

            assertTrue(turnsOnTheWayIn,
                "precondition: the way onto the spare turns nobody, so there is no mid-move turn here to rule on: "
                + ontoTheSpare);

            loc(LOC_B).setReversible(false);

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertNull(turnedAndNotSentHome(layout, plan, false),
                "Return Home turns " + LOC_B + ", which cannot reverse, at a reversing point in the middle of a move and"
                + " runs it on to a square that is neither its home nor a berth (AMV-B1)");
        }
        finally
        {
            spare.setExcludedLocs(new java.util.HashSet<org.traincontrol.base.Locomotive>());
            layout.getPoint("HS R").setReversing(false);
            restoreReversible(was, LOC_A, LOC_B);
        }
    }

    /**
     * A train the railway already had standing on a terminus may step aside on its way home.
     *
     * `behaviour.md` section 6: a plan may rest a train that cannot reverse on a terminus or reversing point only when
     * its next move takes it home - *"a train the railway already had standing on one moves as before"*.  The test is
     * whether this plan put the train there, not whether it is STANDING on such a square; otherwise, with its home
     * occupied, a train found on a terminus cannot step aside and the answer is NO_PLAN_FOUND.
     *
     * LOC_A stands on the terminus HS A and is homed to HS B; LOC_B stands on HS B and is homed to HS A.  LOC_B is kept
     * off the two spare squares, so the only way round is LOC_A stepping aside, LOC_B going home, and LOC_A following,
     * which the control with a reversible LOC_A asserts before the claim is made.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATrainTheRailwayHadOnATerminusMayStepAsideBeforeGoingHome() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        Point terminus = layout.getPoint("HS A");

        boolean[] was = setReversible(true, LOC_A);

        try
        {
            terminus.setTerminus(true);

            assign(layout, LOC_A, "HS B");
            assign(layout, LOC_B, "HS A");

            // LOC_B CANNOT STEP ASIDE, so the only way round is LOC_A stepping aside first.  Without this the
            // arrangement LOC_B -> HS C, LOC_A -> HS B, LOC_B -> HS A exists and never asks anything of LOC_A.
            java.util.Set<org.traincontrol.base.Locomotive> keepOut = new java.util.HashSet<>();
            keepOut.add(loc(LOC_B));

            layout.getPoint("HS C").setExcludedLocs(keepOut);
            layout.getPoint("HS D").setExcludedLocs(keepOut);

            // THE CONTROL, with a train that may reverse: the arrangement exists and needs the step aside.
            HomeStaging.Plan allowed = HomeStaging.snapshot(layout).plan();

            assertEquals(allowed.getOutcome(), HomeStaging.Outcome.READY,
                "precondition: the fixture has no plan even for a train that can reverse, so it cannot show the rule: "
                + allowed.getMoves());

            boolean stepsAside = false;

            for (HomeStaging.Move move : allowed.getMoves())
            {
                if (move.getLocomotive().getName().equals(LOC_A) && !move.getEnd().getName().equals("HS B"))
                {
                    stepsAside = true;
                }
            }

            assertTrue(stepsAside,
                "precondition: nothing steps aside in this arrangement, so the rule is not exercised: "
                + allowed.getMoves());

            // AND NOW THE TRAIN THE RULE IS ABOUT.  It was not turned by this plan - the railway had it there.
            restoreReversible(was, LOC_A);
            was = setReversible(false, LOC_A);

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
                "Return Home refuses to move a train that cannot reverse off the terminus the railway had it standing"
                + " on, so nothing can come home: " + plan.getMoves());

            assertNull(turnedAndNotSentHome(layout, plan, false),
                "the plan turns a train that cannot reverse and then sends it somewhere other than home, which is the"
                + " rule this must not break to satisfy the claim above");
        }
        finally
        {
            layout.getPoint("HS C").setExcludedLocs(new java.util.HashSet<org.traincontrol.base.Locomotive>());
            layout.getPoint("HS D").setExcludedLocs(new java.util.HashSet<org.traincontrol.base.Locomotive>());

            terminus.setTerminus(false);
            restoreReversible(was, LOC_A);
        }
    }

    /**
     * A train with no home that the railway had standing on a terminus can still be moved off it.
     *
     * The same clause where it strands two trains: a train placed on a square that is already somebody's home keeps no
     * home (`Layout.claimHome` calls it a free agent), so "may move only to its home" refuses it every square, and the
     * train whose berth it stands on cannot come home.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATrainWithNoHomeTheRailwayHadOnATerminusCanBeMovedOffIt() throws Exception
    {
        Layout layout = load(ring(LOC_C, LOC_A, null));

        Point terminus = layout.getPoint("HS A");

        boolean[] was = setReversible(true, LOC_C);

        try
        {
            terminus.setTerminus(true);

            layout.setHomeLocomotive("HS A", null);
            assign(layout, LOC_A, "HS A");

            assertNull(layout.getHomeStations().get(loc(LOC_C)), "precondition: LOC_C still has a home");

            HomeStaging.Plan allowed = HomeStaging.snapshot(layout).plan();

            assertEquals(allowed.getOutcome(), HomeStaging.Outcome.READY,
                "precondition: no plan even for a train that can reverse: " + allowed.getMoves());

            boolean movesOff = false;

            for (HomeStaging.Move move : allowed.getMoves())
            {
                if (move.getLocomotive().getName().equals(LOC_C)) movesOff = true;
            }

            assertTrue(movesOff, "precondition: the free agent never moves in this arrangement: " + allowed.getMoves());

            restoreReversible(was, LOC_C);
            was = setReversible(false, LOC_C);

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
                "a train with no home that cannot reverse is refused every move off the terminus the railway had it"
                + " standing on, so the train whose berth it is on can never come home: " + plan.getMoves());

            assertNull(turnedAndNotSentHome(layout, plan, false), "the plan breaks AMH-B1 to satisfy the claim above");
        }
        finally
        {
            terminus.setTerminus(false);
            restoreReversible(was, LOC_C);
        }
    }

    /**
     * A train that cannot reverse and has no home is never turned at all: the ruling allows a turn only where the
     * train is going to its berth next, which a free agent can never satisfy.  LOC_C stands on LOC_A's home with no
     * home of its own, and the only square it may step aside to is a terminus.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testATrainWithNoHomeThatCannotReverseIsNeverTurnedAside() throws Exception
    {
        Layout layout = load(ring(LOC_C, LOC_A, null));

        Point terminus = layout.getPoint("HS D");
        Point otherSpare = layout.getPoint("HS C");

        boolean[] was = setReversible(false, LOC_C);

        try
        {
            terminus.setTerminus(true);

            // LOC_C keeps no home of its own, and LOC_A's home is the square LOC_C is standing on.
            layout.setHomeLocomotive("HS A", null);
            assign(layout, LOC_A, "HS A");

            assertNull(layout.getHomeStations().get(loc(LOC_C)), "precondition: LOC_C still has a home");

            // ...and the only square it may step aside to is the terminus.
            java.util.Set<org.traincontrol.base.Locomotive> keepOut = new java.util.HashSet<>();
            keepOut.add(loc(LOC_C));
            otherSpare.setExcludedLocs(keepOut);

            // THE FIXTURE HAS TO STEP IT ASIDE AT ALL.  The assertion below is satisfied by an empty plan for
            // any reason, so without this it would pass on a railway where nothing ever uses the terminus.  With a
            // train that CAN reverse, the same arrangement does use it.
            boolean[] allowedToReverse = setReversible(true, LOC_C);

            HomeStaging.Plan allowed;

            try
            {
                allowed = HomeStaging.snapshot(layout).plan();
            }
            finally
            {
                restoreReversible(allowedToReverse, LOC_C);
            }

            boolean stepsAside = false;

            for (HomeStaging.Move move : allowed.getMoves())
            {
                if (move.getEnd().getName().equals("HS D")) stepsAside = true;
            }

            assertTrue(stepsAside,
                "precondition: nothing steps aside onto the terminus even for a train that can reverse, so this fixture"
                + " cannot show the rule: " + allowed.getOutcome() + " " + allowed.getMoves());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertNull(turnedAndNotSentHome(layout, plan, false),
                "Return Home steps " + LOC_C + " aside onto a terminus.  It cannot reverse and has no berth to be going"
                + " to, so nothing can turn it back - Adam's ruling allows the turn only on the way home (AMV-C7)");
        }
        finally
        {
            otherSpare.setExcludedLocs(new java.util.HashSet<org.traincontrol.base.Locomotive>());
            terminus.setTerminus(false);
            restoreReversible(was, LOC_C);
        }
    }

    /**
     * An edge two trains' tails lay across at the start is passed only when BOTH have moved.
     *
     * `passesTheTailsOfTrainsThatHaveNotMoved` must ask about EVERY train whose tail lay across the edge at the start
     * (found by the direct cover, a lock partner, or the places the edge runs over), not only the first one found:
     * the runtime walks the tails as they are now and refuses that leg, and the plan stops half way (OB-073).
     *
     * The throat TT_X -> TT_Y runs over two places, TT:k1 and TT:k2.  Two trains of two units stand at TT_B1 and TT_B2,
     * each come in over one of them, so bravo's tail lies on TT:k1 and charlie's on TT:k2; bravo is the one found first.
     * Called by reflection, as `testARoadThatTurnedIsNotTheSameStateAsOneThatDidNot` calls `tailKey`.
     *
     * MUTATION: settle on the first train found again, and this fails.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testAnEdgeTwoTailsLieAcrossWaitsForBoth() throws Exception
    {
        Layout layout = load(twoTailsOverOneThroat());

        Integer wasB = loc(LOC_B).getTrainLength();
        Integer wasC = loc(LOC_C).getTrainLength();

        try
        {
            loc(LOC_B).setTrainLength(2);
            loc(LOC_C).setTrainLength(2);

            for (String[] berth : new String[][] {{"TT_C1", "TT_B1"}, {"TT_C2", "TT_B2"}})
            {
                Edge approach = layout.getEdge(berth[0], berth[1]);
                Point at = layout.getPoint(berth[1]);

                at.setArrivedFrom(layout.entrySideOf(approach, at));
                at.setArrivedAlong(Arrays.asList(approach));
            }

            Map<String, Locomotive> claimed = layout.placesCoveredByStandingTrains();

            assertEquals(claimed.get("TT:k1"), loc(LOC_B), "precondition: bravo's tail does not lie on TT:k1 - " + claimed);
            assertEquals(claimed.get("TT:k2"), loc(LOC_C), "precondition: charlie's tail does not lie on TT:k2 - " + claimed);

            HomeStaging staging = HomeStaging.snapshot(layout);

            Method passes = HomeStaging.class.getDeclaredMethod("passesTheTailsOfTrainsThatHaveNotMoved", Edge.class,
                Locomotive.class, Map.class);

            passes.setAccessible(true);

            Field startField = HomeStaging.class.getDeclaredField("start");

            startField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<Point, Locomotive> start = new LinkedHashMap<>((Map<Point, Locomotive>) startField.get(staging));

            Edge throat = layout.getEdge("TT_X", "TT_Y");

            assertNotNull(throat, "precondition: the fixture has no throat");

            // THE CONTROLS: nobody has moved, and both have.
            assertFalse((Boolean) passes.invoke(staging, throat, loc(LOC_A), start), "control: with both trains still"
                + " standing, the planner lets alpha over the throat their tails lie across");

            Map<Point, Locomotive> bothGone = new LinkedHashMap<>(start);

            bothGone.remove(layout.getPoint("TT_B1"));
            bothGone.remove(layout.getPoint("TT_B2"));

            assertTrue((Boolean) passes.invoke(staging, throat, loc(LOC_A), bothGone), "control: with both trains gone,"
                + " the planner still refuses the throat their tails no longer lie across");

            // THE CASE: bravo, found first, has gone; charlie is still standing.
            Map<Point, Locomotive> oneGone = new LinkedHashMap<>(start);

            oneGone.remove(layout.getPoint("TT_B1"));

            assertFalse((Boolean) passes.invoke(staging, throat, loc(LOC_A), oneGone), "the planner let alpha over the"
                + " throat because bravo, the first train it found there, had moved - while charlie's tail still lies on"
                + " TT:k2, so the runtime refuses the leg and the plan stops half way (AUT2-C2, OB-073)");
        }
        finally
        {
            for (String berth : new String[] {"TT_B1", "TT_B2"})
            {
                layout.getPoint(berth).setArrivedFrom(null);
                layout.getPoint(berth).setArrivedAlong(null);
            }

            loc(LOC_B).setTrainLength(wasB == null ? 0 : wasB);
            loc(LOC_C).setTrainLength(wasC == null ? 0 : wasC);
        }
    }

    /** A throat two trains' tails lie across, one place each; alpha stands short of it. */
    private static String twoTailsOverOneThroat()
    {
        return json("{'points': ["
            + square("TT_M", 70, null, true, LOC_A) + ","
            + square("TT_X", 71, null, true, null) + ","
            + square("TT_Y", 72, null, true, null) + ","
            + square("TT_C1", 73, null, true, null) + ","
            + square("TT_B1", 74, null, true, LOC_B) + ","
            + square("TT_C2", 75, null, true, null) + ","
            + square("TT_B2", 76, null, true, LOC_C)
            + "],'edges': ["
            + edge("TT_M", "TT_X") + ","
            + "{'start': 'TT_X', 'end': 'TT_Y', 'length': 2,"
            + " 'places': [{'at': 'TT:k1', 'length': 1}, {'at': 'TT:k2', 'length': 1}]},"
            + "{'start': 'TT_C1', 'end': 'TT_B1', 'length': 2,"
            + " 'places': [{'at': 'TT:k1', 'length': 1}, {'at': 'TT:b1', 'length': 1}]},"
            + "{'start': 'TT_C2', 'end': 'TT_B2', 'length': 2,"
            + " 'places': [{'at': 'TT:k2', 'length': 1}, {'at': 'TT:b2', 'length': 1}]}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A sensor a standing train's TAIL holds is not blocked for the whole plan.
     *
     * `blockedSensors` treats an occupied sensor as unexplained unless a train stands on a point that reports it.  A
     * train's tail holds the section behind it too (Adam: **"Yes, tails hold sensors."**), so the sensor behind a
     * standing train is explained, and its point is not closed for the moves made after that train has gone.
     *
     * TB_A holds a three-unit train that came in over a three-unit approach from TB_B, so its tail lies over that
     * approach and TB_B's sensor is set.  The only way home for the other train runs through TB_B.
     *
     * **THE TRAIN WHOSE TAIL IT IS HAS SOMEWHERE TO GO**, the whole case.  While it stands there the railway refuses
     * that section to anybody (`isPathClear` reads the live feedback), so letting a train in would offer what the
     * runtime refuses (OB-073).  But its home is a spur off its own square, so once it has gone the section must not
     * stay shut for the REST of the plan: the other train can come home.
     *
     * TWO CONTROLS: with that sensor clear the plan is found, so the refusal is about the sensor; and a sensor NOTHING
     * accounts for still blocks, which is the rule this narrows rather than removes.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testASensorHeldByAStandingTrainsTailIsNotBlocked() throws Exception
    {
        Layout layout = load(aTailOverTheApproach());

        Point approachFrom = layout.getPoint("TB_B");
        Point berth = layout.getPoint("TB_A");

        Integer wasLength = loc(LOC_B).getTrainLength();

        try
        {
            loc(LOC_B).setTrainLength(3);

            layout.setHomeLocomotive("TB_H", LOC_A);
            layout.setHomeLocomotive("TB_P", LOC_B);

            // The tail: it came in from TB_B, along that approach, which is how a run leaves a train.
            Edge approach = layout.getEdge("TB_B", "TB_A");

            berth.setArrivedFrom(layout.entrySideOf(approach, berth));
            berth.setArrivedAlong(java.util.Arrays.asList(approach));

            assertTrue(layout.edgesCoveredByStandingTrains().containsKey(approach),
                "precondition: the standing train's tail does not cover its approach, so no sensor is held by a tail");

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
                "control: with every sensor clear there is no plan home, so the refusal below would not be the sensor");

            // Now the section under that tail reads occupied, as latching detection does.
            model.setFeedbackState(approachFrom.getS88(), true);

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
                "the sensor behind a standing train reads occupied because that train's tail is lying on it, and Return"
                + " Home treated it as an obstruction nothing can clear - so TB_B was shut for the whole plan and no"
                + " train could pass it (AMH-B2)");

            model.setFeedbackState(approachFrom.getS88(), false);

            // AND THE RULE IT NARROWS still holds: a sensor no train accounts for blocks.
            model.setFeedbackState(layout.getPoint("TB_H").getS88(), true);

            assertNotEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
                "control: a sensor nothing on the graph accounts for no longer blocks, so the fix has swallowed the rule");
        }
        finally
        {
            model.setFeedbackState(approachFrom.getS88(), false);
            model.setFeedbackState(layout.getPoint("TB_H").getS88(), false);
            berth.setArrivedFrom(null);
            berth.setArrivedAlong(null);
            loc(LOC_B).setTrainLength(wasLength == null ? 0 : wasLength);
        }
    }

    /**
     * A train has to fit in the room at a square the route TURNS it at, and is asked nothing at a square it only
     * passes (Adam's ruling on MT-333).
     *
     * `firstClearRoute` asks `measuredRoomAtTheEndOf` at the destination and at a reversing square in the MIDDLE of a
     * route: a train that turns stands still, so the stretch behind that square has to hold it; one that runs through
     * does not stand there and the runtime asks it nothing.
     *
     * Three railways identical but for one thing each: the turn with one unit of room refuses a three-unit train, the
     * same square not reversing admits it (the half Adam ruled on), and the same turn admits a one-unit train (the
     * refusal is the room, not the flag).  The train is reversible, so the berth rules of MT-445 and MT-449, which
     * govern a NON-reversible train, cannot be what refuses.
     *
     * MUTATION: delete the `next.isReversing()` arm and the first assertion fails; widen it to every square and the
     * second does.
     */
    @Test
    public void testATrainMustFitTheRoomWhereARouteTurnsIt() throws Exception
    {
        Integer wasLength = loc(LOC_A).getTrainLength();
        boolean couldReverse = loc(LOC_A).isReversible();

        try
        {
            loc(LOC_A).setReversible(true);
            loc(LOC_A).setTrainLength(3);

            Layout turning = load(aShortStretchBeforeATurn("RN"));
            turning.getPoint("RN_R").setReversing(true);
            assign(turning, LOC_A, "RN_H");

            // IMPOSSIBLE, NAMING IT, since OB-325: RN_H is the only other station, so a train refused the one road there
            // has no move at all, and the plan says so with its name rather than searching and answering "no plan found"
            assertEquals(HomeStaging.snapshot(turning).plan().getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
                "the only road home turns the train at RN_R, where one unit of measured track lies behind it, and a"
                + " three-unit train was sent there anyway - so it stands across the junction and the railway refuses"
                + " the move (AMH-C2)");

            // THE HALF ADAM RULED ON (MT-333): the same square, not a turning one, is not asked about at all.
            Layout passing = load(aShortStretchBeforeATurn("RP"));
            assign(passing, LOC_A, "RP_H");

            assertEquals(HomeStaging.snapshot(passing).plan().getOutcome(), HomeStaging.Outcome.READY,
                "a square the train only runs through was judged on the room behind it, which refuses trains the"
                + " railway admits: it does not stand there (Adam, MT-333, 2026-09-14)");

            // AND IT IS THE ROOM, NOT THE TURN: the same turning square takes a train that fits.
            loc(LOC_A).setTrainLength(1);

            Layout shortTrain = load(aShortStretchBeforeATurn("RQ"));
            shortTrain.getPoint("RQ_R").setReversing(true);
            assign(shortTrain, LOC_A, "RQ_H");

            assertEquals(HomeStaging.snapshot(shortTrain).plan().getOutcome(), HomeStaging.Outcome.READY,
                "control: a one-unit train is refused the same turn, so the refusal above is the reversing flag and"
                + " not the room at all");
        }
        finally
        {
            loc(LOC_A).setTrainLength(wasLength == null ? 0 : wasLength);
            loc(LOC_A).setReversible(couldReverse);
        }
    }

    /**
     * X - R - H in both directions, with one unit of track behind R and five behind H.  LOC_A stands at X; every other
     * locomotive is left off the graph, so the only question is whether the one road home is allowed.
     *
     * @param tag a prefix of its own, so each copy is an independent railway (tests for different scenarios do not
     *        share a track diagram)
     * @return the configuration
     */
    private static String aShortStretchBeforeATurn(String tag)
    {
        int base = "RN".equals(tag) ? 90 : ("RP".equals(tag) ? 94 : 98);

        return json("{'points': ["
            + square(tag + "_X", base, null, true, LOC_A) + ","
            + square(tag + "_R", base + 1, null, false, null) + ","
            + square(tag + "_H", base + 2, null, true, null)
            + "],'edges': ["
            + "{'start': '" + tag + "_X', 'end': '" + tag + "_R', 'length': 1},"
            + "{'start': '" + tag + "_R', 'end': '" + tag + "_X', 'length': 1},"
            + "{'start': '" + tag + "_R', 'end': '" + tag + "_H', 'length': 5},"
            + "{'start': '" + tag + "_H', 'end': '" + tag + "_R', 'length': 5}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    // THE CLAIM WAS REMOVED RATHER THAN LEFT GREEN.
    //
    // Seven fixtures could not produce the case the validation describes: a sensor is "explained" whenever a point
    // reporting it holds a train, so in every shape built here the held section was accounted for by its own occupant
    // and not by a tail.  A test that cannot fail is worse than none.  The FIX is kept: `edgesCoveredByStandingTrains`
    // claims an edge whole, so freeing both of its ends frees a point no tail ever reached; see
    // `HomeStaging.blockedSensors`.

    @Test
    public void testATurnIsNotLaunderedThroughABerth() throws Exception
    {
        Layout layout = load(aBerthBeyondAReversingPoint());

        layout.getPoint("TD_R").setReversing(true);

        // THE LAUNDERING HAS TO BE THE ONLY PLAN, and it takes three trains: LOC_A waits somewhere while another train
        // crosses, and the only free square is the berth TD_P, reached from TD_Y by a route that turns it at TD_R.
        // Then LOC_A must come OUT of the berth, and the way out through TD_W turns nobody and ends at an ordinary
        // station, which is not where a turned train may be sent.  The berth has to be free when LOC_A must wait and
        // wanted only afterwards, so LOC_C is not homed in it and LOC_A is not homed where it stands; the preconditions
        // below pin both.
        assign(layout, LOC_A, "TD_Z");
        assign(layout, LOC_B, "TD_Y");
        assign(layout, LOC_C, "TD_X");

        assertNotSame(layout.getHomeStations().get(loc(LOC_A)), layout.getPoint("TD_Y"),
            "precondition: LOC_A is homed where it already stands, so it never moves and nothing is laundered");
        assertNotSame(layout.getHomeStations().get(loc(LOC_C)), layout.getPoint("TD_P"),
            "precondition: LOC_C is homed in the berth, so it claims it before LOC_A ever needs it - which is what the"
            + " third version of this fixture did");

        boolean[] was = setReversible(false, LOC_A);

        try
        {
            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            // THE PLAN HAS TO PARK IT IN THE BERTH, and this says so by name rather than by counting moves; the
            // precondition carries the whole plan into its message, so a plan that is not the one the claim is about
            // can be seen.
            boolean throughTheBerth = false;

            for (HomeStaging.Move move : plan.getMoves())
            {
                if (move.getLocomotive().equals(loc(LOC_A)) && move.getEnd().getName().equals("TD_P"))
                {
                    throughTheBerth = true;
                }
            }

            assertTrue(throughTheBerth,
                "precondition: the plan never parks " + LOC_A + " in the berth TD_P, so this fixture cannot show the"
                + " laundering.  Outcome " + plan.getOutcome() + ", moves: " + plan.getMoves());

            assertNull(turnedAndNotSentHome(layout, plan, false),
                "Return Home turns " + LOC_A + ", which cannot reverse, on its way into the berth and then takes it out"
                + " of the berth to an ordinary station - so it arrives running the wrong way round, by two moves"
                + " instead of one (AMW-B3): " + plan.getMoves());
        }
        finally
        {
            layout.getPoint("TD_R").setReversing(false);
            restoreReversible(was, LOC_A);
        }
    }

    /**
     * The same railway plans the same way twice.
     *
     * `Layout.getNeighbors` shuffles and `firstClearRoute` returns the first route that shuffle finds, so the answer
     * must not ride on it: a capability claim that answers differently on each press cannot be told from a regression.
     * Asserted as the property rather than an ordering, so it holds however the planner is made deterministic.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testThePlannerAnswersTheSameWayTwice() throws Exception
    {
        Layout layout = load(twoEqualWaysRound());

        layout.setHomeLocomotive("TE_H", LOC_A);

        assertNotNull(layout.getEdge("TE_S", "TE_U"), "precondition: the fixture has only one way round");
        assertNotNull(layout.getEdge("TE_S", "TE_V"), "precondition: the fixture has only one way round");

        String first = null;

        for (int press = 0; press < 12; press++)
        {
            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            StringBuilder said = new StringBuilder(String.valueOf(plan.getOutcome()));

            for (HomeStaging.Move move : plan.getMoves())
            {
                said.append(" | ").append(move.getLocomotive().getName()).append(" -> ");

                for (Edge e : move.getPath()) said.append(e.getEnd().getName()).append(",");
            }

            if (first == null) first = said.toString();

            assertEquals(said.toString(), first,
                "press " + (press + 1) + " of Return Home planned a different way round the same railway, so the answer"
                + " rides on a shuffle rather than on the track (AMH-C1).  A capability claim that answers differently"
                + " on each press cannot tell a regression from chance");
        }
    }

    private static String aBerthBeyondAReversingPoint()
    {
        return json("{'points': ["
            + square("TD_X", 75, null, true, LOC_B) + ","
            + square("TD_R", 76, null, false, null) + ","
            + "{'name': 'TD_P', 'station': true, 's88': " + (S88_BASE + 77) + ", 'autoDestination': false},"
            + square("TD_Y", 78, null, true, LOC_A) + ","
            + square("TD_W", 79, null, false, null) + ","
            + square("TD_Z", 84, null, true, LOC_C)
            + "],'edges': ["
            + edge("TD_X", "TD_R") + "," + edge("TD_R", "TD_X") + ","
            + edge("TD_R", "TD_P") + "," + edge("TD_P", "TD_R") + ","
            + edge("TD_R", "TD_Y") + "," + edge("TD_Y", "TD_R") + ","
            + edge("TD_P", "TD_W") + "," + edge("TD_W", "TD_P") + ","
            + edge("TD_W", "TD_Z") + "," + edge("TD_Z", "TD_W") + ","
            + edge("TD_W", "TD_X") + "," + edge("TD_X", "TD_W")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * Two equal-length ways between the same pair, so a shuffled search has something to choose between: TE_S reaches
     * TE_H by TE_U or by TE_V, both one square long and clear.  A ring proves nothing, since every route there is
     * forced.  The claim asserts the ROUTE, not just the outcome.
     */
    private static String twoEqualWaysRound()
    {
        return json("{'points': ["
            + square("TE_S", 80, null, true, LOC_A) + ","
            + square("TE_U", 81, null, false, null) + ","
            + square("TE_V", 82, null, false, null) + ","
            + square("TE_H", 83, null, true, null)
            + "],'edges': ["
            + edge("TE_S", "TE_U") + "," + edge("TE_U", "TE_S") + ","
            + edge("TE_U", "TE_H") + "," + edge("TE_H", "TE_U") + ","
            + edge("TE_S", "TE_V") + "," + edge("TE_V", "TE_S") + ","
            + edge("TE_V", "TE_H") + "," + edge("TE_H", "TE_V")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A berth whose approach is measured, so a standing train's tail lies over the square behind it.
     *
     * S -> B -> H is the way home for the train at S; B -> A is the berth's approach, three units in one place.  A -> P
     * is the berth train's own way home, a spur off its own square that never touches B, so that train can leave
     * without entering the section its tail holds.
     */
    private static String aTailOverTheApproach()
    {
        return json("{'points': ["
            + square("TB_S", 60, null, true, LOC_A) + ","
            + square("TB_B", 61, null, true, null) + ","
            + square("TB_H", 62, null, true, null) + ","
            + square("TB_A", 63, null, true, LOC_B) + ","
            + square("TB_P", 64, null, true, null)
            + "],'edges': ["
            + edge("TB_S", "TB_B") + "," + edge("TB_B", "TB_S") + ","
            + edge("TB_B", "TB_H") + "," + edge("TB_H", "TB_B") + ","
            + edge("TB_A", "TB_P") + "," + edge("TB_P", "TB_A") + ","
            + "{'start': 'TB_B', 'end': 'TB_A', 'length': 3, 'places': [{'at': 'TB:approach', 'length': 3}]},"
            + "{'start': 'TB_A', 'end': 'TB_B', 'length': 3, 'places': [{'at': 'TB:approach', 'length': 3}]}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /** X and H either side of a square that can be made to reverse trains, with a spare Y beyond it. */
    private static String aReversingPointInTheMiddle()
    {
        return json("{'points': ["
            + square("HS X", 50, null, true, LOC_B) + ","
            + square("HS R", 51, null, false, null) + ","
            + square("HS H", 52, null, true, LOC_A) + ","
            + square("HS Y", 53, null, true, null)
            + "],'edges': ["
            + edge("HS X", "HS R") + "," + edge("HS R", "HS X") + ","
            + edge("HS R", "HS H") + "," + edge("HS H", "HS R") + ","
            + edge("HS R", "HS Y") + "," + edge("HS Y", "HS R")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * The first move of a plan that breaks the turning rulings, or null.
     *
     * A move breaks them when a train that cannot reverse is turned (the move ends at a terminus or reversing point, or
     * its route passes one) and does not end where that turn may leave it: at its home, or at a square it comes to rest
     * facing out of (a berth or terminus it has backed into).  A rest at a turning square that is not its home must
     * also be followed by that train's move home.
     *
     * @param asIfItCannotReverse judge every train as though it could not reverse, for the control
     */
    private static String turnedAndNotSentHome(Layout layout, HomeStaging.Plan plan, boolean asIfItCannotReverse)
    {
        // CARRIED PER TRAIN, because the ruling is about a train and not a move.  An oracle that asks one move at a
        // time cannot see the laundering: move 1 turns the train into a berth, which is permitted, and move 2 leaves
        // the berth over a route that turns nobody and ends nowhere turning, so neither breaks a rule of its own; the
        // PAIR does.  So once the plan has turned a train that cannot reverse, its very next move must end at its home;
        // reaching home clears it and nothing else does.
        java.util.Set<org.traincontrol.base.Locomotive> owed = new java.util.LinkedHashSet<>();

        for (HomeStaging.Move move : plan.getMoves())
        {
            org.traincontrol.base.Locomotive train = move.getLocomotive();

            if (train.isReversible() && !asIfItCannotReverse) continue;

            Point end = move.getEnd();
            Point home = layout.getHomeStations().get(train);

            boolean atItsHome = home != null && home.isSamePlaceAs(end);

            // A move owed to home that goes anywhere else is the violation, whatever it was parked on.
            if (owed.contains(train) && !atItsHome)
            {
                return "turned earlier and then sent to " + end.getName() + " rather than home: " + move;
            }

            if (atItsHome)
            {
                owed.remove(train);

                continue;
            }

            boolean turnedOnTheWay = end.isTerminus() || end.isReversing();

            for (int e = 0; e + 1 < move.getPath().size(); e++)
            {
                if (move.getPath().get(e).getEnd().isReversing()) turnedOnTheWay = true;
            }

            // Turned, and not home: the next move it makes owes itself to its home.  A train with no home can never
            // satisfy that, so turning it at all is the violation.
            if (turnedOnTheWay)
            {
                if (home == null) return "turned with no home to go to: " + move;

                owed.add(train);
            }
        }

        return null;
    }

    /** P -> T1 -> T2 -> R, one way, T1 and T2 termini, a train at P: the only way to R turns it twice. */
    private static String twoTurnsBeforeHome()
    {
        return json("{'points': ["
            + station("HS P", 40, LOC_A) + ","
            + terminus("HS T1", 41) + ","
            + terminus("HS T2", 42) + ","
            + station("HS R", 43, null)
            + "],'edges': ["
            + edge("HS P", "HS T1") + "," + edge("HS T1", "HS T2") + "," + edge("HS T2", "HS R")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A terminus may be arrived at but never driven through - and that is not the same as unreachable.
     *
     * The planner refuses to expand a terminus and the runtime refuses any path whose intermediate point is one, so
     * the two agree about a SINGLE PATH.  But `plan()` ends a move at any station and starts the next from there, so
     * HS A -> HS B -> HS C is two ordinary moves (`testALocomotiveStartingOnATerminusIsPlannedHome` shows a terminus
     * can be set off from again), and IMPOSSIBLE would be false of this track.  NO_PLAN_FOUND says the planner may
     * fail to find the arrangement and nothing more: **"I did not find a way" is allowed to be wrong; "there is no way"
     * is not.**
     */
    @Test
    public void testAHomeReachableOnlyThroughATerminusIsNotProvedImpossible() throws Exception
    {
        // A line, not a ring: HS A - HS B - HS C, with the middle station a terminus
        Layout layout = load(ringWith(new String[]{LOC_A, null, null, null},
                                  new String[]{null, "'terminus': true", null, null}));

        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        // The ring still offers HS A -> HS D -> HS C, so this must SUCCEED - the terminus only removes
        // one of the two ways round.  Asserting the reachable case first keeps the test below honest.
        assertTrue(plan.isPossible(), "the long way round the ring is still open: " + plan);

        // Now close the other way round, leaving the terminus as the only route
        Layout blocked = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null).substring(0, station("HS B", 1, null).length() - 1)
                + ", 'terminus': true}" + ","
            + station("HS C", 2, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertTrue(blocked.getPoint("HS B").isTerminus(), "precondition: the only route is through HS B");

        assign(blocked, LOC_A, "HS C");

        HomeStaging.Plan impossible = HomeStaging.snapshot(blocked).plan();

        assertNotEquals(impossible.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "the planner PROVED HS C unreachable, and it is not: HS B is a station, so stopping there "
            + "and setting off again is two ordinary moves.  IMPOSSIBLE is a claim about the track, and "
            + "it sends the operator to look at track that is fine: " + impossible);

        // AND IT NOW FINDS THE ARRANGEMENT.  The control here used to assert the opposite, and the reason was
        // `mustBackIn` rather than the track.  With that rule gone ("Return home is manual") the planner does what the
        // paragraph above says the track allows: stop at HS B, set off again, reach HS C.
        assertFalse(loc(LOC_A).isReversible(),
            "the fixture rests on this train not being reversible - if it were, the old rule never "
            + "applied here and this assertion would say nothing about the change");

        assertTrue(impossible.isPossible(),
            "HS B is a station, so stopping there and setting off again is two ordinary moves - and "
            + "this is now the only route.  The planner declining it is the refusal Adam overturned: "
            + impossible.getOutcome());

        assertTrue(impossible.getBlocked().isEmpty(),
            "nobody is named as blocked, because nobody has been shown to be: " + impossible);
    }

    /**
     * A locomotive standing on a terminus can still be sent home.
     *
     * The rule is that a terminus may not be an intermediate point; being the point a path STARTS from
     * is expressly allowed, by isPathClear and by the planner seeding its search there.
     */
    @Test
    public void testALocomotiveStartingOnATerminusIsPlannedHome() throws Exception
    {
        Layout layout = load(ringWith(new String[]{null, null, null, LOC_A},
                                  new String[]{null, null, null, "'terminus': true"}));

        assign(layout, LOC_A, "HS B");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertTrue(plan.isPossible(), "a train may leave a terminus: " + plan);

        applyPlan(layout, plan);

        assertEquals(plan.getMoves().size(), 1, "one move, straight home: " + plan.getMoves());
        assertEquals(plan.getMoves().get(0).getEnd().getName(), "HS B");
    }

    // Reversing

    /**
     * Two roads to the same square that differ only in whether they passed a reversing point are two states, not one.
     *
     * **The search keeps one route per key and reads it back to decide what the train may do next.**  `astar` stores
     * the route in `routesOf` against the state key, `turnedByThePlan` reads it, and a train turned on the way is then
     * restricted to squares the turn was for.  `tailKey` identified a state by where the trains stand and what their
     * tails cover, dropping a train whose tail covered nothing, which is exactly where two roads are otherwise
     * indistinguishable; whichever the search reached first decided the restriction for both.
     *
     * Driven at `tailKey` because that is where the fact is dropped, with unmeasured edges so the tails are empty for
     * both roads and the turn is the only difference left.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testARoadThatTurnedIsNotTheSameStateAsOneThatDidNot() throws Exception
    {
        Layout layout = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + "{'name': 'HS R', 'station': true, 's88': " + (S88_BASE + 2) + ", 'reversing': true},"
            + station("HS Z", 3, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS Z") + ","
            + edge("HS A", "HS R") + "," + edge("HS R", "HS Z")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertTrue(layout.getPoint("HS R").isReversing(), "precondition: HS R does not reverse");

        java.lang.reflect.Method tailKey = HomeStaging.class.getDeclaredMethod(
            "tailKey", java.util.Map.class, java.util.Map.class);

        tailKey.setAccessible(true);

        HomeStaging staging = HomeStaging.snapshot(layout);

        MarklinLocomotive train = loc(LOC_A);

        java.util.Map<Point, org.traincontrol.base.Locomotive> state = new java.util.LinkedHashMap<>();

        state.put(layout.getPoint("HS Z"), train);

        String straight = (String) tailKey.invoke(staging, state,
            oneRoute(train, layout.getEdge("HS A", "HS B"), layout.getEdge("HS B", "HS Z")));

        String throughTheTurn = (String) tailKey.invoke(staging, state,
            oneRoute(train, layout.getEdge("HS A", "HS R"), layout.getEdge("HS R", "HS Z")));

        // THE CONTROL: the same road twice is the same state, so the claim below is about the turn
        // and not about the key changing every time it is asked.
        assertEquals(straight, (String) tailKey.invoke(staging, state,
            oneRoute(train, layout.getEdge("HS A", "HS B"), layout.getEdge("HS B", "HS Z"))),
            "the same arrangement reached the same way gave two different keys");

        assertNotEquals(throughTheTurn, straight,
            "a road that passed a reversing point and one that did not are the same state to the"
            + " search (" + straight + ").  It keeps one route per key and reads it back to decide"
            + " whether the train is restricted to where the turn was for, so whichever was found"
            + " first answers for both (RTX-C1)");
    }

    /**
     * One train's route, as `astar` hands it to `tailKey`.
     *
     * @param train the train that moved
     * @param legs the road it took
     * @return the routes map
     */
    private static java.util.Map<org.traincontrol.base.Locomotive, java.util.List<Edge>> oneRoute(
        MarklinLocomotive train, Edge... legs)
    {
        java.util.Map<org.traincontrol.base.Locomotive, java.util.List<Edge>> out =
            new java.util.LinkedHashMap<>();

        for (Edge leg : legs)
        {
            assertTrue(leg != null, "precondition: the fixture is missing one of its edges");
        }

        out.put(train, java.util.Arrays.asList(legs));

        return out;
    }

    /**
     * A reversing station is an ordinary station to the planner - drivable through, restable on.
     *
     * Neither the planner nor isPathClear has any rule about reversing points, so this pins the
     * absence: if one is ever added to the runtime, the planner has to learn it in the same change.
     */
    @Test
    public void testAReversingStationIsAnOrdinaryStationToThePlanner() throws Exception
    {
        Layout layout = load(ringWith(new String[]{LOC_A, null, null, null},
                                  new String[]{null, "'reversing': true", null, null}));

        assertTrue(layout.getPoint("HS B").isReversing(), "precondition: HS B reverses");

        // Home two stations away, so the shortest route runs THROUGH the reversing station
        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan through = HomeStaging.snapshot(layout).plan();

        assertTrue(through.isPossible(), "a reversing station may be driven through: " + through);

        // And it may be a home in its own right
        Layout resting = load(ringWith(new String[]{LOC_A, null, null, null},
                                   new String[]{null, "'reversing': true", null, null}));

        assign(resting, LOC_A, "HS B");

        assertTrue(HomeStaging.canBeHome(loc(LOC_A), resting.getPoint("HS B")),
            "a reversing station can be a home");

        assertTrue(HomeStaging.snapshot(resting).plan().isPossible());
    }

    // Exclusions on the way past - collision prevention, not just parking

    /**
     * A station's exclusion list says where a locomotive may not STOP - not where it may not go.
     *
     * The two lists mean different things on purpose.  On a non-station, exclusion blocks passage, which
     * is the collision constraint.  On a station it blocks parking only, because a station on a through
     * route is exactly where an operator writes "not this one, not here" without wanting to sever the
     * route.
     *
     * Made to block passage as well, briefly, and reverted: on the author's own layout that removed 45%
     * of the reachable station pairs for two locomotives, and Return Home then ran its search out
     * instead of finding the plan that was there before.  A line graph is used here rather than the
     * ring, so passing through HS B is the only way to reach HS C and the test cannot be satisfied by
     * the long way round.
     */
    @Test
    public void testAStationExclusionStopsParkingNotPassing() throws Exception
    {
        Layout line = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + station("HS C", 2, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        Point b = line.getPoint("HS B");

        b.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        assertFalse(HomeStaging.canBeHome(loc(LOC_A), b),
            "the exclusion still forbids parking there");

        assign(line, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(line).plan();

        assertTrue(plan.isPossible(),
            "but HS B may be driven through, and it is the only way to HS C: " + plan);

        applyPlan(line, plan);

        assertEquals(plan.getMoves().get(0).getEnd().getName(), "HS C");
    }

    /**
     * A non-station that excludes the locomotive cannot be passed at all.
     *
     * The other half of the same rule, and the one that carries the collision constraint.  Without it
     * the test above would be satisfied by exclusions meaning nothing anywhere.
     */
    @Test
    public void testANonStationExclusionBlocksPassage() throws Exception
    {
        Layout line = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + "{'name': 'Mid', 'station': false, 's88': " + (S88_BASE + 1) + "}" + ","
            + station("HS C", 2, null)
            + "],'edges': ["
            + edge("HS A", "Mid") + "," + edge("Mid", "HS A") + ","
            + edge("Mid", "HS C") + "," + edge("HS C", "Mid")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        Point mid = line.getPoint("Mid");

        assertFalse(mid.isDestination(), "precondition: Mid is not a station");

        mid.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        assign(line, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(line).plan();

        assertFalse(plan.isPossible(),
            "the only route runs through a non-station this locomotive may not enter: " + plan);
    }


    /**
     * A train parked on a deactivated point still holds its detection section.
     *
     * A section is electrical: taking a siding out of service does not lift the train standing on it
     * off the rails.  The mutual-exclusion rule skipped inactive siblings, so the planner would route
     * a second train into the active twin of that section - and the runtime, reading the real sensor,
     * refuses it partway through the run.
     */
    @Test
    public void testATrainOnAnInactivePointStillClosesItsSection() throws Exception
    {
        // HS A is out of service with LOC_B stored on it, and HS C reports HS A's sensor
        Layout layout = load(ringWith(new String[]{LOC_B, LOC_A, null, null},
                                      new String[]{"'active': false", null, null, null},
                                      new int[]{0, 1, 0, 3}));

        assertFalse(layout.getPoint("HS A").isActive(), "precondition: HS A is out of service");
        assertEquals(layout.getPoint("HS C").getS88(), layout.getPoint("HS A").getS88(),
            "precondition: HS C reports HS A's sensor");

        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.isPossible(),
            "HS C is the far end of a section LOC_B is standing on: " + plan);
    }

    /**
     * A locomotive standing on a deactivated point is not planned home.
     *
     * Every other test here exempts the origin - that is what stops a train's own sensor blocking its
     * own departure - but the runtime does not exempt it from the inactive-point rule, and staging
     * executes with autonomy running.  Planning the move anyway means it is refused at its first edge.
     */
    @Test
    public void testALocomotiveOnAnInactivePointIsNotPlannedHome() throws Exception
    {
        Layout layout = load(ringWith(new String[]{LOC_A, null, null, null},
                                      new String[]{"'active': false", null, null, null}));

        assertFalse(layout.getPoint("HS A").isActive(), "precondition: HS A is out of service");

        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.isPossible(),
            "the runtime would refuse this at the first edge, so it must not be planned: " + plan);

        // IMPOSSIBLE, not NO_PLAN_FOUND.  !isPossible() alone was true before the pre-check learned
        // this rule as well - the search simply found nothing - so it pinned the refusal without
        // pinning the upgrade.  A locomotive that cannot leave where it stands is provably stuck, and
        // saying so costs one flag test instead of the whole budget.
        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "a locomotive that cannot depart is proved stuck, not searched for: " + plan);

        assertTrue(plan.getBlocked().contains(loc(LOC_A)),
            "and it is named, so the operator knows which train to reactivate: " + plan.getBlocked());
    }

    /**
     * A locomotive standing on a point that is not a station is not planned home either.
     *
     * `isPathClear` refuses a path from a non-station "in fully autonomous mode", and staging executes with autonomy
     * running, so the rule is in force for every leg of a Return Home run.  A planner without it starts the run, has
     * the first leg refused and retried every two seconds, and abandons it with "the track it needs never became free",
     * which is not what is wrong.  A train gets there by being placed by hand.
     *
     * The control is the point: the same graph with the same train, differing only in whether that place is a station,
     * must plan perfectly well; without it this passes for a fixture broken some other way.
     *
     * MUTATION: dropping either half, the pre-scan's isDestination test or firstClearRoute's, leaves the first
     * assertion failing, since the search then finds the move.
     */
    @Test
    public void testALocomotiveOnANonStationIsNotPlannedHome() throws Exception
    {
        Layout layout = load(nonStationOrigin(false));

        assertFalse(layout.getPoint("HS P").isDestination(),
            "precondition: HS P is not a station, which is the whole case");

        assertTrue(layout.getPoint("HS P").isActive(),
            "precondition: HS P is in service - the inactive rule is the OTHER half, and if it fired "
            + "here this test would pass without the rule under test existing at all");

        assign(layout, LOC_A, "HS A");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.isPossible(),
            "the runtime refuses a path that starts anywhere but a station, so this would be planned "
            + "and then retried every two seconds until the run was abandoned: " + plan);

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "a locomotive that cannot depart is proved stuck, not searched for: " + plan);

        assertTrue(plan.getBlocked().contains(loc(LOC_A)),
            "and it is named, so the operator knows which train to move by hand: " + plan.getBlocked());

        // The control.  One flag different, and the plan is ordinary.
        Layout station = load(nonStationOrigin(true));

        assertTrue(station.getPoint("HS P").isDestination(),
            "the control did not take: HS P has to be a station in this one");

        assign(station, LOC_A, "HS A");

        HomeStaging.Plan ordinary = HomeStaging.snapshot(station).plan();

        assertTrue(ordinary.isPossible(),
            "the same train, the same square, the same empty home one edge away - and the only "
            + "difference is whether the square is a station.  If this fails the fixture is wrong "
            + "and the assertions above prove nothing: " + ordinary);
    }

    /**
     * The parity audit reports nothing when planner and runtime actually agree.
     *
     * The audit exists to catch the planner drifting from the rules it re-implements, which only works
     * if a clean layout is silent.  Its runtime oracle is getPossiblePaths, which filters destinations
     * on occupancy and station-ness but not on exclusion - pickPath does that separately - so a free
     * station that excludes the locomotive was reported as a disagreement on every run.  That is two
     * runtime methods disagreeing with each other, not a planner defect, and on a layout carrying
     * station exclusions it made the instrument cry wolf exactly where it is meant to be believed.
     */
    @Test
    public void testTheParityAuditIsSilentWhenTheTwoAgree() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        // Free, reachable, a station, and excluded - the combination the oracle used to offer
        layout.getPoint("HS C").setExcludedLocs(
            new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        assign(layout, LOC_A, "HS B");

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "an excluded destination is not a divergence - canRest refuses it and pickPath would too");
    }

    /**
     * The parity audit is silent about a locomotive that cannot depart, too.
     *
     * The companion to the exclusion case: the audit's oracle is asked at rest, where the runtime does
     * not apply its inactive-point rule, so it offers departures from a deactivated point that the
     * planner refuses because staging executes with autonomy running.  That is the third deliberate
     * divergence, and reporting it would make the instrument cry wolf in the configuration the rule
     * was just added for.
     */
    @Test
    public void testTheParityAuditIsSilentAboutALocomotiveThatCannotDepart() throws Exception
    {
        Layout layout = load(ringWith(new String[]{LOC_A, null, null, null},
                                      new String[]{"'active': false", null, null, null}));

        assertFalse(layout.getPoint("HS A").isActive(), "precondition: HS A is out of service");

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "the planner refuses every departure from an inactive point, and is right to - comparing "
            + "that against an at-rest oracle reports the rule as a defect");
    }

    // Shared sensors

    /**
     * Two active points that report one sensor are never both occupied.
     *
     * They are one piece of track as far as detection goes, so a plan that ends with a train on each
     * of them is describing something the layout cannot do - and the runtime would refuse the second
     * arrival, stranding the run halfway through.
     *
     * The rule used to be expressed by blocking the sensor address, and only for a sensor that was
     * READING occupied when the snapshot was taken - so on a layout whose feedback was quiet it did
     * not apply at all, and the planner would put both trains on one section for the runtime to
     * discover halfway through the run.  It is now structural, in canEnter.
     */
    @Test
    public void testTwoActivePointsSharingASensorAreNeverBothOccupied() throws Exception
    {
        // HS C reports HS A's sensor, and each train is assigned to one of the pair
        Layout layout = load(ringWith(new String[]{null, LOC_A, null, LOC_B},
                                  new String[]{null, null, null, null},
                                  new int[]{0, 1, 0, 3}));

        assertEquals(layout.getPoint("HS C").getS88(), layout.getPoint("HS A").getS88(),
            "precondition: HS A and HS C report one sensor");

        assertTrue(layout.getPoint("HS A").isActive() && layout.getPoint("HS C").isActive(),
            "precondition: both are active, which is what makes them mutually exclusive");

        assign(layout, LOC_A, "HS A");
        assign(layout, LOC_B, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.isPossible(),
            "the two homes are one detection section - both trains cannot stand on them: " + plan);

        // IMPOSSIBLE, not NO_PLAN_FOUND.  The distinction is the whole point: NO_PLAN_FOUND means the
        // search ran out of room and says so, and reaching it here costs the entire budget - instant on
        // this four-point fixture, fifteen seconds on a real layout.  Conflicting goals are provable
        // without searching at all.
        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "conflicting homes are proved, not searched for: " + plan);

        assertTrue(plan.getBlocked().contains(loc(LOC_A)) && plan.getBlocked().contains(loc(LOC_B)),
            "and both locomotives are named, since either assignment could be the wrong one: "
            + plan.getBlocked());
    }


    /**
     * Two trains already standing on their own homes are not told the arrangement is impossible.
     *
     * The pairwise goal scan proves two homes on one detection section cannot both be occupied, but about an ARRIVAL,
     * and nothing arrives when the train is already there; its sibling scan carries that exemption ("Both already
     * parked: nothing arrives, so nothing is checked.") and this one lacked it.  IMPOSSIBLE refuses the WHOLE staging
     * run, so a third locomotive that only needed driving to the next platform never moves, and the two names given are
     * the trains already where they belong.  A platform and its terminus stub sharing a feedback is an ordinary shape.
     *
     * MUTATION: removing the exemption restores the fault: IMPOSSIBLE, with the two parked locomotives named.
     */
    @Test
    public void testTwoTrainsAlreadyHomeOnOneSensorAreNotRefused() throws Exception
    {
        // HS A and HS B are one detection section, and each holds the train whose home it is.  HS C
        // holds a third, whose home is the empty HS D - so there IS something for the run to do.
        Layout layout = load(ringWith(new String[]{LOC_A, LOC_B, LOC_C, null},
                                  new String[]{null, null, null, null},
                                  new int[]{0, 0, 2, 3}));

        assertEquals(layout.getPoint("HS A").getS88(), layout.getPoint("HS B").getS88(),
            "precondition: HS A and HS B report one sensor, which is the whole case");

        assertTrue(layout.getPoint("HS A").isActive() && layout.getPoint("HS B").isActive(),
            "precondition: both are active, or the scan under test never looks at them");

        assign(layout, LOC_A, "HS A");
        assign(layout, LOC_B, "HS B");
        assign(layout, LOC_C, "HS D");

        assertEquals(layout.getPoint("HS A").getCurrentLocomotive(), loc(LOC_A),
            "precondition: the first train is standing on its own home");

        assertEquals(layout.getPoint("HS B").getCurrentLocomotive(), loc(LOC_B),
            "precondition: the second train is standing on its own home");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "two trains that are already home were proved unable to get there, which refused the "
            + "whole run - including the third train, which only had to cross to the next platform.  "
            + "Blocked: " + plan.getBlocked());

        assertFalse(plan.getBlocked().contains(loc(LOC_A)) || plan.getBlocked().contains(loc(LOC_B)),
            "a train that is already standing on its home was named as unable to reach it: "
            + plan.getBlocked());

        assertTrue(plan.isPossible(),
            "the third train has an empty home one edge away and nothing in its way: " + plan);
    }

    /**
     * Control: the shared address closes the other point only while something is standing on it.
     *
     * Without this the test above would be satisfied by a planner that refused every layout with a
     * shared address on it, which is most real layouts.
     */
    @Test
    public void testASharedSensorOnlyClosesTheOtherPointWhileItIsHeld() throws Exception
    {
        Layout layout = load(ringWith(new String[]{null, LOC_A, null, null},
                                  new String[]{null, null, null, null},
                                  new int[]{0, 1, 0, 3}));

        assertEquals(layout.getPoint("HS C").getS88(), layout.getPoint("HS A").getS88(),
            "precondition: HS A and HS C report one sensor");

        assign(layout, LOC_A, "HS A");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertTrue(plan.isPossible(),
            "nothing is standing on HS C, so its twin HS A is free to be occupied: " + plan);

        applyPlan(layout, plan);

        assertEquals(plan.getMoves().size(), 1, "one move: " + plan.getMoves());
    }

    /**
     * Excluding a locomotive from the station that is its home is recognized before it is applied.
     *
     * The guard on the other door already existed: assigning a home to a station that excludes the
     * locomotive warns.  This state is identical - every future Return Home reports IMPOSSIBLE and
     * refuses to move anything, including the locomotives that could have gone home - and reaching it
     * was silent, one keystroke over a hovered node.
     *
     * The predicate is what both UI paths ask; the dialog around it is not testable here.
     */
    @Test
    public void testExcludingALocomotiveFromItsOwnHomeIsRecognised() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        Point d = layout.getPoint("HS D");

        layout.setHomeLocomotive("HS D", LOC_A);

        assertEquals(HomeStaging.homeBrokenByExcluding(d, Arrays.asList((Locomotive) loc(LOC_A))),
            LOC_A, "HS D is the home of this locomotive, so excluding it there strands it");

        assertNull(HomeStaging.homeBrokenByExcluding(d, Arrays.asList((Locomotive) loc(LOC_B))),
            "another locomotive being excluded from HS D breaks nothing");

        assertNull(HomeStaging.homeBrokenByExcluding(layout.getPoint("HS C"),
            Arrays.asList((Locomotive) loc(LOC_A))),
            "and neither does excluding it from a station that is not its home");

        // The state the guard exists to prevent, confirmed to be as bad as claimed
        d.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE);
        assertTrue(plan.getMoves().isEmpty(),
            "and no locomotive moves at all, not even LOC_B which could have gone home");

        d.setExcludedLocs(new HashSet<Locomotive>());
    }

    /**
     * A positional home on an exit-only station is a launch pad, and does not block the fleet.
     *
     * Some layouts stage trains on one-way tracks - departure edges only, re-staged by hand; the
     * author's graph carries nineteen.  A locomotive loaded there acquires it as its positional
     * home, autonomy is free to dispatch it, and it can then never return - which, because a plan is
     * all-or-nothing, made Return Home answer IMPOSSIBLE for the whole fleet for the rest of the
     * session (found in the wild: MV 1134 on St99).  The graph states the intent topologically, so
     * the planner now treats such a locomotive as homeless.
     *
     * An ASSIGNED home on the same station keeps the strict contract - the operator chose it, so it
     * still answers IMPOSSIBLE with the locomotive named.
     */
    @Test
    public void testALaunchPadPositionalHomeDoesNotBlockTheFleet() throws Exception
    {
        // Two stations, a siding, and a launch pad whose only edge points INTO the layout.  The
        // siding matters: the dispatched pad locomotive is parked on the homed one's station, so
        // the plan exists only if the free agent has somewhere legitimate to step aside to - the
        // first version of this fixture had no siding, and its READY expectation was impossible
        // for any planner (the launch pad itself can never be re-entered).
        Layout layout = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + station("HS C", 5, null) + ","
            + station("Launch", 4, LOC_B)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("Launch", "HS A")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertTrue(layout.getIncomingEdges(layout.getPoint("Launch")).isEmpty(),
            "precondition: nothing can reach the launch pad");

        // LOC_B was loaded on the pad (positional home Launch), then ran - the wild scenario
        assertTrue(layout.moveLocomotive(LOC_B, "HS B", false),
            "precondition: the dispatch itself must succeed");

        assign(layout, LOC_A, "HS B");

        try
        {
            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
                "the launch-pad locomotive is homeless, not a blocker: " + plan);

            assertTrue(plan.getBlocked().isEmpty(), "nothing is reported stuck: " + plan.getBlocked());

            boolean movesHomedLoc = false;

            for (HomeStaging.Move m : plan.getMoves())
            {
                if (m.getLocomotive().equals(loc(LOC_A))) movesHomedLoc = true;
            }

            assertTrue(movesHomedLoc, "and the locomotive with a real home still gets there: "
                + plan.getMoves());

            // The strict half: ASSIGN the unreachable station and the answer is IMPOSSIBLE again,
            // because the operator explicitly asked for something the track cannot do
            assign(layout, LOC_B, "Launch");

            HomeStaging.Plan strict = HomeStaging.snapshot(layout).plan();

            assertEquals(strict.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
                "an assigned home is still honoured with an error: " + strict);

            assertTrue(strict.getBlocked().contains(loc(LOC_B)),
                "and the locomotive concerned is named");
        }
        finally
        {
            layout.setHomeLocomotive("Launch", null);
        }
    }

    /**
     * A cornered search never buys a plan by moving a locomotive off its launch pad.
     *
     * Free agents exist to break deadlocks, and the A* expansion generates moves for every
     * locomotive in the state - so a locomotive still standing on its pad could be relocated when
     * the search was cornered, reachable whenever the pad shares a sensor with a point on someone
     * else's route.  A pad has no incoming edges, so that move can never be planner-undone: the
     * hand-staging the pad represents would be destroyed permanently as a side effect.  The honest
     * answer is NO_PLAN_FOUND - the only way out required undoing hand-staging, which is the
     * operator's to undo.
     */
    @Test
    public void testTheSearchNeverMovesALocomotiveOffItsLaunchPad() throws Exception
    {
        // X must travel HS A -> HS D -> HS B; the pad shares HS D's sensor, so the locomotive
        // parked on the pad closes HS D (two active points on one section are mutually exclusive).
        // Pre-fix, the search unwedges itself by shunting the pad locomotive to HS E.
        Layout layout = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + station("HS D", 2, null) + ","
            + station("HS E", 3, null) + ","
            + station("Pad", 2, LOC_B)
            + "],'edges': ["
            + edge("HS A", "HS D") + "," + edge("HS D", "HS B") + ","
            + edge("Pad", "HS E")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertEquals(layout.getPoint("Pad").getS88(), layout.getPoint("HS D").getS88(),
            "precondition: the pad and HS D are one detection section");
        assertTrue(layout.getIncomingEdges(layout.getPoint("Pad")).isEmpty(),
            "precondition: the pad has no way back in");

        assign(layout, LOC_A, "HS B");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        for (HomeStaging.Move m : plan.getMoves())
        {
            assertFalse(m.getLocomotive().equals(loc(LOC_B)),
                "the plan bought its way out by destroying hand-staging: " + plan.getMoves());
        }

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.NO_PLAN_FOUND,
            "with the pad exempt, the honest answer is that no plan was found: " + plan);
    }

    /**
     * "Return home" may still stage a locomotive onto a reversing station.
     *
     * This is the half of the rule that gives it its purpose.  Parking tracks are flagged reversing so
     * full autonomy never sends a train to one at random; return home is what fills them deliberately.
     * The exclusion therefore lives in pickPath alone - HomeStaging reaches its destinations through
     * canRest and firstClearRoute, neither of which consults the flag.
     *
     * If the exclusion is ever moved somewhere shared - isPathClear, canRest, bfs - the two behaviors
     * collapse into one and this fails, which is the whole reason it is written down.
     */
    @Test
    public void testReturnHomeMayStillStageOntoAReversingStation() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.getPoint("HS C").setReversing(true);

        assertTrue(layout.getPoint("HS C").isReversing(),
            "precondition: the destination under test must actually be a reversing station");

        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "a reversing station is a legitimate home for staging: " + plan);

        assertTrue(plan.getMoves().stream()
                .anyMatch(m -> m.getLocomotive().equals(loc(LOC_A))
                    && "HS C".equals(m.getEnd().getName())),
            "the plan must actually send the locomotive there: " + plan.getMoves());
    }

    /**
     * A plan whose destination is held back by an occupied point is one the railway can carry out (OB-073).
     *
     * FR-001 holds a station back while another named square is occupied, and `isPathClear` enforces that on every move
     * staging makes.  What matters is that a plan is EXECUTABLE, not that such a station is unreachable (staging can
     * move whatever stands there), so this asserts the property rather than `!= READY`, which could not tell a proof
     * from a refusal: a plan comes back and every move finds its destination free at the moment it runs.  The move
     * count is not asserted.
     */
    @Test
    public void testAHomeHeldBackByAnOccupiedPointStillGetsAnExecutablePlan() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // B is where A wants to go, and it is held back by D - where nobody is yet.
        layout.getPoint("HS B").setBlockedBy(
            java.util.Arrays.asList(layout.getPoint("HS D")));

        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS B this tests nothing at all");

        assign(layout, LOC_A, "HS B");

        assertTrue(layout.moveLocomotive(LOC_A, "HS A", false), "the fixture could not be arranged");

        // Somebody on the watched point. Execution refuses A's arrival at B while this is true, so any
        // plan that sends A straight there is one the railway will not carry out.
        assertTrue(layout.moveLocomotive(LOC_B, "HS D", false),
            "could not stand a second locomotive on the watched point");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "no plan was produced for an arrangement that has one. B can be moved off the watched "
            + "square and A can then take its home - three moves. Reporting anything else here means "
            + "the impossibility scan has started proving things about occupancy again, which it "
            + "cannot: staging moves whatever is standing in the way (FBR-B2).  Got: "
            + plan.getOutcome());

        // The restriction really is live while the plan is made, or this fixture says nothing about
        // the tier it is here to pin.
        assertEquals(Point.heldBackBy(layout.getPoint("HS B"), loc(LOC_A)), layout.getPoint("HS D"),
            "precondition: HS B must be held back at plan time - the whole test is that Return Home "
            + "goes there anyway and the railway lets it");

        applyPlan(layout, plan);

        assertEveryoneHome(layout);
    }

    // FR-001 written once, and the audit that grades it

    /**
     * A square with everything about it optional: the FR-001 tests below need points that are not stations, carry no
     * feedback and share a block, none of which station() can express.
     *
     * @param name the point name
     * @param s88Offset the sensor offset from S88_BASE, or null for a point with no feedback
     * @param block the shared-square identity, or null for a square emitted as a single Point
     * @param station whether a train may be sent there
     * @param loc the locomotive standing on it, or null
     * @return the point's JSON, in single quotes for json()
     */
    private static String square(String name, Integer s88Offset, String block, boolean station,
        String loc)
    {
        return "{'name': '" + name + "', 'station': " + station
            + (s88Offset == null ? "" : ", 's88': " + (S88_BASE + s88Offset))
            + (block == null ? "" : ", 'block': '" + block + "'")
            + (loc == null ? "" : ", 'loc': {'name': '" + loc + "'}") + "}";
    }

    /**
     * Two locomotives cannot be homed on two copies of one square, whichever door they arrive by.
     *
     * `claimHome` refuses a square another home already occupies: two locomotives homed on one platform is a state
     * nothing can satisfy (`sharesSection` sees two active Points on one sensor, and Return Home answers IMPOSSIBLE
     * naming both).  The ASSIGNMENT loop asks about squares too, since `parseAuto` writes a home straight onto the
     * Point, so a file or an older setup would otherwise walk both assignments through intact.
     *
     * The fixture is one square emitted as two copies sharing a block, with a different locomotive named as the home of
     * each.
     *
     * MUTATION: removing the square test from the assignment loop leaves both homes standing and fails the first
     * assertion below.
     */
    @Test
    public void testTwoHomesOnOneSquareDoNotBothSurviveTheLoader() throws Exception
    {
        Layout layout = load(twoHomesOnOneSquare());

        // The fixture really is one square in two copies, or there is nothing to refuse.
        assertTrue(layout.getPoint("HS W1").isSamePlaceAs(layout.getPoint("HS W2")),
            "precondition: the two copies do not share a block, so they are two different places and "
            + "homing a locomotive on each of them is perfectly legal");

        int homedThere = 0;

        for (Locomotive l : new Locomotive[] {loc(LOC_A), loc(LOC_B)})
        {
            Point home = layout.getHomeStation(l);

            if (home != null && home.isSamePlaceAs(layout.getPoint("HS W1"))) homedThere++;
        }

        assertEquals(homedThere, 1,
            "both locomotives came out of the loader homed on the same piece of track, which is a "
            + "state nothing can satisfy: Return Home reports IMPOSSIBLE naming both for the rest of "
            + "the session, and the diagram shows what look like two different stations.  claimHome "
            + "refuses exactly this; the assignment loop did not ask");

        // And the loser really was let go rather than merely hidden from the map.
        int stillNamed = 0;

        for (String name : new String[] {"HS W1", "HS W2"})
        {
            if (layout.getPoint(name).getHomeLoc() != null) stillNamed++;
        }

        assertEquals(stillNamed, 1,
            "the losing assignment is still written on its Point, so it will be saved back out and "
            + "warn again on every load - which is the reason the sibling rule drops rather than "
            + "ignores");
    }

    /**
     * One square emitted as two copies, with a different locomotive homed on each.
     *
     * @return the graph JSON
     */
    private static String twoHomesOnOneSquare()
    {
        return json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, LOC_B) + ","
            // ONE SENSOR AND ONE BLOCK, which is what makes them two copies of one platform rather
            // than two platforms.  Both are stations, because the state being refused is two homes on
            // one place a train may stop.
            + "{'name': 'HS W1', 'station': true, 's88': " + (S88_BASE + 2)
            + ", 'block': 'W', 'home': '" + LOC_A + "'},"
            + "{'name': 'HS W2', 'station': true, 's88': " + (S88_BASE + 2)
            + ", 'block': 'W', 'home': '" + LOC_B + "'}"
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS A", "HS W1") + "," + edge("HS W1", "HS A") + ","
            + edge("HS B", "HS W2") + "," + edge("HS W2", "HS B")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A longer approach really is tried when the short one has no room.
     *
     * The room rule refuses with `continue` on the theory that **"another route to the same berth may be longer, and a
     * longer approach is more room"**, which holds only if the search still has the longer route to try.  The arrival
     * must not be recorded in `seen` before the room test runs: `alreadyReached` is a dominance test, and the short
     * route would dominate the longer one before its room is measured.
     *
     * HS D is a terminus with two ways in (five units from HS A, ten by way of HS B) and the train is eight long: the
     * short way has no room and the long way does.
     *
     * MUTATION: moving the room check back below the `seen` marking fails this, and nothing else.
     */
    @Test
    public void testALongerApproachIsStillTriedWhenTheShortOneHasNoRoom() throws Exception
    {
        Layout layout = load(twoWaysToOneBerth());

        Locomotive loc = loc(LOC_A);

        Integer lengthWas = loc.getTrainLength();
        boolean reversibleWas = loc.isReversible();

        try
        {
            loc.setTrainLength(8);

            // Reversible, so mustBackIn does not refuse the terminus for its own reasons and leave
            // this test passing for something other than room.
            loc.setReversible(true);

            assertTrue(layout.getPoint("HS D").isTerminus(),
                "precondition: HS D must be a terminus, or the room rule never applies");

            // The two approaches, and their measurements - a control for the fixture itself.
            assertEquals(layout.getEdge("HS A", "HS D").getLength(), 5,
                "precondition: the short way in must be too short for an eight-unit train");

            assertEquals(layout.getEdge("HS B", "HS D").getLength(), 5,
                "precondition: the long way's last leg");

            assertEquals(layout.getEdge("HS A", "HS B").getLength(), 5,
                "precondition: the long way's first leg - five and five is room for eight");

            // TWENTY TIMES, though NOT because of the shuffle: the search's queue is FIFO and both ways into HS D leave
            // the origin, so the direct one is recorded first whatever order the shuffle gives and the long way is
            // pruned as dominated every time.  The repetition is kept because a guard that catches a regression only
            // sometimes reads as protection; if the order ever matters, twenty attempts still catch it and one might
            // not.
            int refused = 0;

            HomeStaging.Outcome last = null;

            for (int attempt = 0; attempt < 20; attempt++)
            {
                HomeStaging.Plan plan = layout.planReturnToHome();

                if (!plan.isPossible())
                {
                    refused++;
                    last = plan.getOutcome();
                }
            }

            assertEquals(refused, 0,
                "the planner gave up on a berth it can reach with room to spare, in " + refused
                + " of 20 attempts (last outcome " + last + ").  The short way in is five units and "
                + "the train is eight, so that approach is rightly refused - but the way round "
                + "through HS B is ten, and the rule's own comment says another route may be longer.  "
                + "It never got to try one: the refused arrival was already recorded as seen, and the "
                + "longer route was then pruned as dominated before its room was ever measured");

            // AND THE CONTROL: "a plan was found" is also what happens with the room rule deleted altogether (the short
            // way is then acceptable).  So the pair is the test: a train that fits the LONG approach gets home, and one
            // that fits NEITHER (twenty units) does not.
            loc.setTrainLength(20);

            assertFalse(layout.planReturnToHome().isPossible(),
                "control: a train too long for either way into the berth was still given a plan, so "
                + "the room rule is not being applied at all and the assertion above is satisfied by "
                + "its absence.  Outcome: " + layout.planReturnToHome().getOutcome());
        }
        finally
        {
            loc.setTrainLength(lengthWas);
            loc.setReversible(reversibleWas);
        }
    }

    /**
     * A terminus with two ways in, one too short for the train and one long enough.
     *
     * @return the graph JSON
     */
    private static String twoWaysToOneBerth()
    {
        return json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            // HS B IS NOT A STATION, which is what makes this fixture show anything: with a station there the A* stages
            // the train and reaches HS D in two moves each long enough, and cannot see the pruning.  Here the only
            // choice is between two SINGLE moves: five units direct, or ten by way of a square nobody can stop at.
            + square("HS B", 1, null, false, null) + ","
            + "{'name': 'HS D', 'station': true, 's88': " + (S88_BASE + 3)
            + ", 'terminus': true, 'home': '" + LOC_A + "'}"
            + "],'edges': ["
            + "{'start': 'HS A', 'end': 'HS B', 'length': 5},"
            + "{'start': 'HS B', 'end': 'HS A', 'length': 5},"
            // NO THIRD STATION AT ALL.  With one, the A* stages the train there and the leg back out is long
            // enough on its own, so the choice between the two ways into HS D is never the thing being decided.
            // THE TWO WAYS IN.  Direct from HS A is five; round by HS B is ten.
            + "{'start': 'HS A', 'end': 'HS D', 'length': 5},"
            + "{'start': 'HS D', 'end': 'HS A', 'length': 5},"
            + "{'start': 'HS B', 'end': 'HS D', 'length': 5},"
            + "{'start': 'HS D', 'end': 'HS B', 'length': 5}"
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * HS A - HS B - HS C in a line, plus a watched square emitted as TWO copies in one block, off on a
     * siding of its own so nothing can leave it.
     *
     * HS B is the destination the test holds back; the watched square carries no sensor, which is the
     * configuration in which the planner's sensor stand-in for a block covers nothing at all.
     *
     * @param locOnW2 a locomotive on the second copy of the watched square, or null
     * @param locOnC the same locomotive on HS C instead - the control - or null
     * @return the graph JSON
     */
    /**
     * HS A - HS P - HS B in a line, with a locomotive parked on the middle square.
     *
     * @param pIsAStation whether the middle square is a station, which is the only variable
     * @return the graph JSON
     */
    private static String nonStationOrigin(boolean pIsAStation)
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, null) + ","
            + square("HS P", 1, null, pIsAStation, LOC_A) + ","
            + square("HS B", 2, null, true, null)
            + "],'edges': ["
            + edge("HS A", "HS P") + "," + edge("HS P", "HS A") + ","
            + edge("HS P", "HS B") + "," + edge("HS B", "HS P")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * The block fixture, with a HOME assigned to the second copy of the watched square.  Rewrites what `square`
     * produced and asserts the rewrite landed (the device of `ringAssigning`), so a fixture change cannot yield a
     * config with no assignment.
     *
     * @param homeAtW2 the locomotive named as the home
     * @return the graph JSON
     */
    private static String blockAssigningItsWatchedSquare(String homeAtW2)
    {
        // Through json() on both sides, like ringAssigning: the fixture emits single quotes and
        // json() converts them, so a raw fragment never matches what blockOfTwoWatching returns.
        String raw = square("HS W2", null, "HSW", false, null);
        String plain = json(raw);
        String assigned = json(raw.substring(0, raw.length() - 1)
            + ", 'home': '" + homeAtW2 + "'}");

        String config = blockOfTwoWatching(null, null);

        assertTrue(config.contains(plain),
            "precondition: the block fixture must still emit HS W2 plainly");

        return config.replace(plain, assigned);
    }

    /**
     * Two locomotives cannot both be homed on one platform (MT-165).
     *
     * The MT-165 rule lets a positional home sit on a square drawn as several graph Points, so the injectivity test has
     * to know that too: `claimHome` ends `if (this.homeStations.containsValue(p)) return;`, which compares POINTS, so
     * the far copy of a platform looks like a station nobody has spoken for.
     *
     * The sequence is ordinary: a train takes a platform as its home and is sent away by autonomy; the operator places
     * another train on the other copy and it takes that as ITS home.  **No arrangement satisfies that**: two active
     * Points on one sensor make `sharesSection` true, so Return Home says IMPOSSIBLE naming both for the rest of the
     * session.
     *
     * MUTATION: putting `containsValue(p)` back in place of the block-aware test fails the last assertion.
     */
    @Test
    public void testASecondLocomotiveDoesNotHomeOnTheOtherCopyOfAPlatform() throws Exception
    {
        Layout layout = load(twoStationCopiesAndASiding());

        Point p1 = layout.getPoint("HS P1");
        Point p2 = layout.getPoint("HS P2");

        assertNotNull(p1.getBlock(), "the fixture did not take: HS P1 must carry a block");

        assertEquals(p1.getBlock(), p2.getBlock(),
            "the fixture did not take: the two copies must share a block, or this asks nothing");

        // The MT-165 rule working: a train placed on a split platform takes it as its home.
        assertTrue(layout.moveLocomotive(LOC_A, "HS P1", false),
            "could not place the first locomotive");

        assertEquals(layout.getHomeStations().get(loc(LOC_A)), p1,
            "precondition: the first locomotive took no home from the square it was placed on, so "
            + "this cannot ask anything about the second");

        // It leaves.  By hand here; by autonomy in life.  The platform is empty and still its home.
        assertTrue(layout.moveLocomotive(LOC_A, "HS Q", false),
            "could not move the first locomotive away");

        assertEquals(layout.getHomeStations().get(loc(LOC_A)), p1,
            "precondition: moving a locomotive re-homed it, so the rest of this asks nothing");

        // And another train is put on the OTHER COPY of that platform.
        assertTrue(layout.moveLocomotive(LOC_B, "HS P2", false),
            "could not place the second locomotive");

        assertNull(layout.getHomeStations().get(loc(LOC_B)),
            "a second locomotive was given the far copy of a platform that is already the first "
            + "one's home.  The copies of a square are one piece of track, so no arrangement puts "
            + "both trains at home - and Return Home answers IMPOSSIBLE naming both of them from "
            + "then on, while the diagram shows what look like two different stations");
    }

    /**
     * A home is a SQUARE, and which way the train faces is not part of it.
     *
     * Adam: "so the home should just be the logical point, and the direction is wherever the locomotive was facing when
     * it started moving."  A square on the diagram is one graph Point per arrival side; refusing a home on any such
     * square (at the editor menu, `Layout.setHomeLocomotive` and the loader) cost the main-line platforms.  The answer
     * to "is the train home?" is about the square, as `Point.isSamePlaceAs` has said since MT-165.  And
     * `AutonomyBuilder.homeCopy` chooses which copy carries the home, so a loader that refused every copy would throw
     * that choice away: a home set in the editor would be gone at the next start with only a log line.
     *
     * The last assertion makes "the square" mean something: usability is asked of the square too.  A platform with a
     * turning copy and a plain copy is a good home for a locomotive that cannot reverse, because it can stand on the
     * plain one.
     *
     * MUTATION: putting back any of the three `getBlock() != null` refusals fails one of the first three assertions;
     * asking `canRest` about the Point rather than the square fails the last.
     */
    @Test
    public void testAHomeIsTheSquareAndNotOneOfItsDirections() throws Exception
    {
        Layout layout = load(twoStationCopiesAndASiding());

        Point p1 = layout.getPoint("HS P1");
        Point p2 = layout.getPoint("HS P2");

        assertNotNull(p1.getBlock(), "the fixture did not take: HS P1 must carry a block");
        assertEquals(p1.getBlock(), p2.getBlock(), "the fixture did not take: one square, two copies");

        // 1. The door a person uses.
        assertNull(HomeStaging.whyNotAHome(loc(LOC_A), p2),
            "the editor refuses a home on a platform drawn as two arrival sides, which is ten of "
            + "Adam's thirty-six station squares");

        // 2. The door the model uses.
        layout.setHomeLocomotive("HS P2", LOC_A);

        assertEquals(p2.getHomeLoc(), loc(LOC_A),
            "the model refused to record a home on a split square");

        final boolean wasReversible = loc(LOC_A).isReversible();

        try
        {
            // 3. And usability is asked of the SQUARE.  A station's exclusion list is a property of one graph Point, so
            // one copy can bar a locomotive the other welcomes; the home is the square, so what matters is whether it
            // can stand on any copy.  Exclusions are used rather than a turning berth, since a terminus disqualifies
            // nobody.
            p2.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

            assertTrue(HomeStaging.canBeHome(loc(LOC_A), p2),
                "a home was refused because the copy carrying it bars this locomotive - while the "
                + "other copy of the same platform welcomes it perfectly well");

            // And the control: when EVERY copy bars it, the refusal is real.
            p1.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

            assertFalse(HomeStaging.canBeHome(loc(LOC_A), p2),
                "a platform every copy of which bars this locomotive was offered to it anyway, so "
                + "the square rule has stopped refusing anything");
        }
        finally
        {
            p1.setExcludedLocs(new HashSet<Locomotive>());
            p2.setExcludedLocs(new HashSet<Locomotive>());
            loc(LOC_A).setReversible(wasReversible);
            layout.setHomeLocomotive("HS P2", null);
        }
    }

    /**
     * The planner agrees with the door that accepted the home.
     *
     * The home is the square, and `whyNotAHome` asks the square whether a train can stand there.  The planner's own
     * reachability scan asked `canRest` and `connected` about the one copy that carries the home, so the editor
     * accepted a home that Return Home then called IMPOSSIBLE.  **Both halves have to be asked of the same copy**: a
     * copy you can rest at but not reach, plus another you can reach but not rest at, would pass two separate scans
     * while being no home.
     *
     * The disqualifier is an exclusion list, a property of one graph Point (a terminus disqualifies nobody: Adam,
     * "trains should be allowed to back into terminuses if they are not reversible").
     *
     * MUTATION: asking `canRest(l, home)` about the stored copy fails the first assertion; asking resting and
     * connectedness over copies independently rather than per copy fails the second.
     */
    @Test
    public void testThePlannerAgreesWithTheDoorThatAcceptedTheHome() throws Exception
    {
        Layout layout = load(twoStationCopiesAndASiding());

        Point barred = layout.getPoint("HS P2");
        Point open = layout.getPoint("HS P1");

        assertEquals(barred.getBlock(), open.getBlock(),
            "the fixture did not take: these must be two copies of one platform");

        try
        {
            // The home is on the copy that bars this locomotive; the other copy welcomes it.
            barred.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

            layout.setHomeLocomotive("HS P2", LOC_A);

            assertTrue(layout.moveLocomotive(LOC_A, "HS Q", false),
                "could not place the locomotive away from its home");

            assertNull(HomeStaging.whyNotAHome(loc(LOC_A), barred),
                "precondition: the editor must accept this home, or the planner agreeing with it "
                + "proves nothing");

            // plan(), not triage(): triage is the cheap pre-check - already home, no homes, nothing
            // placed - and the reachability scan that decides IMPOSSIBLE lives in plan().
            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
                "Return Home called a home impossible that the application had just accepted. The "
                + "home is the platform, and the locomotive is welcome on its other copy");

            // AND THE CONTROL: when every copy bars it, it really is impossible.
            open.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(),
                HomeStaging.Outcome.IMPOSSIBLE,
                "a platform every copy of which bars this locomotive was accepted for it, so the "
                + "rule has stopped refusing anything");
        }
        finally
        {
            open.setExcludedLocs(new HashSet<Locomotive>());
            barred.setExcludedLocs(new HashSet<Locomotive>());
            layout.setHomeLocomotive("HS P2", null);
            layout.moveLocomotive(null, "HS Q", false);
        }
    }

    /**
     * One platform holds one home, whichever of its copies is named.
     *
     * `setHomeLocomotive` enforces one station per LOCOMOTIVE and a Point holds one `homeLoc`, so naming a second
     * locomotive at the same Point displaces the first.  Two copies of one platform are two Points with two fields, so
     * assigning one locomotive to each would leave both homed on one piece of track, which Return Home says is
     * IMPOSSIBLE.  Displacing rather than refusing, as naming the same Point twice already does: the operator is saying
     * this train lives here now.
     *
     * MUTATION: sweeping only the exact Point rather than the square leaves both homed.
     */
    @Test
    public void testOnePlatformHoldsOneHomeAcrossItsCopies() throws Exception
    {
        Layout layout = load(twoStationCopiesAndASiding());

        Point p1 = layout.getPoint("HS P1");
        Point p2 = layout.getPoint("HS P2");

        assertEquals(p1.getBlock(), p2.getBlock(), "the fixture did not take: one square, two copies");

        try
        {
            layout.setHomeLocomotive("HS P1", LOC_A);

            assertEquals(p1.getHomeLoc(), loc(LOC_A), "precondition: the first assignment did not take");

            // A second locomotive named at the OTHER copy of the same platform.
            layout.setHomeLocomotive("HS P2", LOC_B);

            assertEquals(p2.getHomeLoc(), loc(LOC_B), "the second assignment did not take");

            assertNull(p1.getHomeLoc(),
                "two locomotives are homed on two copies of one platform - which is one piece of "
                + "track, so no arrangement puts both of them home, and Return Home answers "
                + "IMPOSSIBLE naming both from now on");

            int here = 0;

            for (Point at : layout.getHomeStations().values())
            {
                if (at.isSamePlaceAs(p1)) here++;
            }

            assertEquals(here, 1, "the platform is recorded as the home of more than one locomotive");
        }
        finally
        {
            layout.setHomeLocomotive("HS P1", null);
            layout.setHomeLocomotive("HS P2", null);
        }
    }

    /**
     * A terminus can be a home for a train that cannot reverse.
     *
     * Adam: "trains should be allowed to back into terminuses if they are not reversible (that's why we have the
     * reversing point at feedback 2013)."  Almost every parking berth is a terminus, so refusing one in `canRest` would
     * refuse homes at most of the places a train is parked.
     *
     * Whether it can GET there with a reversal is a route question, and `Layout.isPathClear` asks it (a terminus is
     * allowed when the path passes a reversing point); asking again here, without a route, could only guess.  An
     * over-strict check at a door where nothing can satisfy it is worse than no check.
     *
     * MUTATION: putting the terminus clause back in canRest fails this.
     */
    @Test
    public void testATerminusCanBeAHomeForATrainThatCannotReverse() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        final boolean wasReversible = loc(LOC_A).isReversible();

        try
        {
            loc(LOC_A).setReversible(false);

            d.setTerminus(true);

            assertNull(HomeStaging.whyNotAHome(loc(LOC_A), d),
                "a parking berth was refused as a home to a train that cannot reverse. It backs in "
                + "past a reversing point and leaves forwards - and on Adam's railway that refusal "
                + "covered most of the places a train is actually parked");
        }
        finally
        {
            d.setTerminus(false);
            loc(LOC_A).setReversible(wasReversible);
        }
    }

    /**
     * A train that cannot reverse gets home to its terminus WITH OR WITHOUT a reversing point on the way.
     *
     * `mustBackIn` once refused a terminus unless the route turned the train, so it backed in and could leave forwards.
     * Adam took it out: *"Return home is manual, take the rule out and say why."*  The reasoning is at
     * `firstClearRoute`'s arrival test: pressing Return Home IS the operator asking, which is what makes it manual, and
     * manual operation may back a non-reversible train into a terminus.
     *
     * The two halves are the same railway with one flag moved, which keeps this about the rule rather than the fixture,
     * and both answer the same way.
     *
     * MUTATION: restoring `mustBackIn` at `firstClearRoute`'s arrival test fails the first half and leaves the second
     * passing.
     */
    @Test
    public void testATrainThatCannotReverseHasToBackIntoItsHome() throws Exception
    {
        Layout layout = load(straightToATerminus());

        Point berth = layout.getPoint("HS T");
        Point middle = layout.getPoint("HS M");

        assertTrue(berth.isTerminus(), "the fixture did not take: the home must be a terminus");

        final boolean wasReversible = loc(LOC_A).isReversible();

        try
        {
            loc(LOC_A).setReversible(false);

            layout.setHomeLocomotive("HS T", LOC_A);

            assertTrue(layout.moveLocomotive(LOC_A, "HS S", false),
                "could not place the locomotive away from its home");

            // NOTHING TURNS IT ROUND on the way, so it would have to back out of the berth to leave -
            // which is what "cannot reverse" forbids.
            assertFalse(middle.isReversing(), "the fixture did not take: nothing may reverse it yet");

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(),
                HomeStaging.Outcome.READY,
                "a train that cannot reverse was refused a plan home to its terminus because nothing "
                + "on the way turns it round.  Adam took that rule out on 2026-09-04: Return Home is "
                + "manual operation, and manual operation may back a train into a terminus - his own "
                + "ruling of 2026-09-01, which isPathClear has followed since");

            // The same railway, with a reversing point on the way: it made the difference once and no longer does.
            middle.setReversing(true);

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
                "and with a reversing point on the way, as it always did");
        }
        finally
        {
            middle.setReversing(false);
            loc(LOC_A).setReversible(wasReversible);
            layout.setHomeLocomotive("HS T", null);
            layout.moveLocomotive(null, "HS S", false);
        }
    }

    /**
     * A start, a square in the middle that can be made to reverse trains, and a terminus beyond it.
     */
    private static String straightToATerminus()
    {
        return json("{'points': ["
            + square("HS S", 7, null, true, null) + ","
            + square("HS M", 8, null, true, null) + ","
            + terminus("HS T", 9)
            + "],'edges': ["
            + edge("HS S", "HS M") + "," + edge("HS M", "HS S") + ","
            + edge("HS M", "HS T") + "," + edge("HS T", "HS M")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A station square a train can only leave by reversing.
     */
    private static String terminus(String name, int s88Offset)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (S88_BASE + s88Offset)
            + ", 'terminus': true}";
    }

    /**
     * One platform drawn as two Points, and a siding to send a train to.  Both copies are stations carrying the same
     * sensor, which is what a split platform IS on the derived graph (`Point` refuses a destination with no feedback).
     */
    private static String twoStationCopiesAndASiding()
    {
        return json("{'points': ["
            + square("HS P1", 5, "HSP", true, null) + ","
            + square("HS P2", 5, "HSP", true, null) + ","
            + square("HS Q", 6, null, true, null)
            + "],'edges': ["
            + edge("HS P1", "HS Q") + "," + edge("HS Q", "HS P1") + ","
            + edge("HS P2", "HS Q") + "," + edge("HS Q", "HS P2")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    private static String blockOfTwoWatching(String locOnW2, String locOnC)
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 1, null, true, null) + ","
            + square("HS C", 2, null, true, locOnC) + ","
            + square("HS W1", null, "HSW", false, null) + ","
            + square("HS W2", null, "HSW", false, locOnW2)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("HS W1", "HS W2") + "," + edge("HS W2", "HS W1")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * Two homes, a one-way hold, and an approach guard that shares a feedback with the other home.
     *
     * The counterexample to the OB-085 impossibility proof.  HS C is held back while HS D is occupied; HS D is held
     * back while its approach guard HS W2 is occupied, and HS W2 shares a feedback address with HS C, which
     * AutonomyBuilder says is normal: "a station, its approach guard and a reversing point can be three Points on one
     * feedback - so the sensor cannot say which Points are one square."  There is no cycle, and the railway stages it
     * in two moves.
     *
     * No block anywhere, deliberately: "shares a sensor" versus "is the same block" is the point of the fixture.
     *
     * @return the graph JSON
     */
    private static String twoHomesAndAGuardSharingASensor()
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 1, null, true, LOC_B) + ","
            + square("HS C", 2, null, true, null) + ","
            + square("HS D", 3, null, true, null) + ","
            + square("HS W2", 2, null, false, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("HS C", "HS D") + "," + edge("HS D", "HS C") + ","
            + edge("HS D", "HS A") + "," + edge("HS A", "HS D") + ","
            + edge("HS W2", "HS D") + "," + edge("HS D", "HS W2")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * HS A - HS B, with HS B watched by an EMPTY square and sharing its feedback with an occupied one.
     *
     * The narrowness fixture for the audit's FR-001 exemption: HS B carries a blockedBy list, so an exemption written
     * "skip every destination that has one" would skip it, but nothing stands on the square it names, so the genuine
     * divergence underneath stays visible.
     *
     * @return the graph JSON
     */
    private static String watchedSquareEmptyButTheSensorIsNot()
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 1, null, true, null) + ","
            + square("HS X", 1, null, false, LOC_B) + ","
            + square("HS W", 2, null, false, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS X", "HS W") + "," + edge("HS W", "HS X")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * The audit's FR-001 exemption is NARROW - it does not blind the instrument.
     *
     * An exemption written too wide (skipping every FR-001 destination rather than the currently-held ones) would blind
     * the audit to a real mis-copy of the rule; the sibling tests, which assert the audit is SILENT, cannot tell a
     * narrow hole from a total one.  This is the only test in the class that asserts it still speaks.
     *
     * The vehicle is a real divergence the audit reports today: HS B shares its feedback with HS X, where LOC_B stands,
     * so `canEnter`'s shared-sensor rule refuses the arrival while the at-rest runtime offers it.  HS B also carries a
     * blockedBy list naming a square nobody is on, which makes the two shapes of exemption differ.  If that divergence
     * is ever exempted in its own right, this needs a new vehicle rather than deletion: it pins the FR-001 skip.
     *
     * MUTATION: widening the exemption to `if (!p.getBlockedBy().isEmpty()) continue;` takes the count to 0 and fails
     * this test and no other.
     */
    @Test
    public void testTheParityAuditStillReportsADivergenceOnAWatchedStation() throws Exception
    {
        Layout layout = load(watchedSquareEmptyButTheSensorIsNot());

        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS W")));

        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: without a blockedBy list the two exemption shapes behave "
            + "identically and this test proves nothing");

        assertNull(layout.getPoint("HS W").getCurrentLocomotive(),
            "precondition: the watched square must be EMPTY - a held-back station is exempt, and "
            + "correctly so, which is the case the tests above cover");

        assertEquals(layout.getPoint("HS X").getCurrentLocomotive(), loc(LOC_B),
            "precondition: somebody has to be on the square that shares HS B's feedback, or there is "
            + "no divergence left for the audit to find");

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 1,
            "the audit must still speak: the at-rest runtime offers HS B and the planner refuses it "
            + "over the shared sensor, which is a genuine divergence and not an FR-001 one.  Zero "
            + "here means the FR-001 exemption is skipping every destination that carries a "
            + "blockedBy list, which is the hole that would hide a real mis-copy of the rule");
    }

    /**
     * The parity audit is silent about a station FR-001 is holding back.
     *
     * Both sides apply the rule in every tier, at rest too (Adam: "enforce the occupancy ruling in all modes and then rely
     * on isPathClear."): the runtime through `isPathClear`, the planner through its path search and `canRest`, all by
     * `Point.heldBackBy`.  So neither offers HS C while HS B is occupied, and the audit has nothing to report; a count
     * above zero means one side has stopped applying it.
     *
     * MUTATION: make `Layout.blockingOccupantOf` answer null, or take FR-001 out of both the planner's path search and
     * `canRest`, and this fails.  Taking it out of `canRest` alone does not: the path search already refuses the square.
     */
    @Test
    public void testTheParityAuditIsSilentAboutAStationHeldBackByAnOccupiedSquare() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // HS C is held back while HS B is occupied - and HS B is where LOC_B is standing
        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS B")));

        assertEquals(layout.getPoint("HS C").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS C this tests nothing at all");

        assertEquals(layout.getPoint("HS B").getCurrentLocomotive(), loc(LOC_B),
            "precondition: somebody has to be standing on the watched square");

        // At rest, which is when the audit runs: the runtime applies FR-001 there too.
        assertFalse(layout.isAutoRunning(),
            "precondition: the audit's oracle is at-rest getPossiblePaths, so autonomy must not be running");

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "the audit reports a station held back by an occupied square, so the planner or the runtime "
            + "has stopped applying FR-001 at rest");
    }

    /**
     * A station held back by the square the ARRIVING train is leaving can still be reached.
     *
     * Adam: "The condition should not apply to trains leaving - only departing."  Both production copies exempt the
     * locomotive being routed, or the one movement that clears the condition is the movement it forbids.  The replay
     * oracle (`applyPlan`) calls `Point.heldBackBy`, the same call `isPathClear` makes, so the exemption holds there
     * too; without it a legal plan whose move IS the departing train would be a false red.
     *
     * MUTATION: restoring applyPlan's hand-written loop (assertNull on `watched.getCurrentLocomotive()` for every
     * entry in `end.getBlockedBy()`) fails this test and no other.
     */
    @Test
    public void testAStationHeldBackByTheTrainThatIsLeavingItIsStillReachable() throws Exception
    {
        Layout layout = load(ringWith(new String[]{null, null, null, LOC_A},
                                      new String[]{null, null, null, null}));

        layout.getPoint("HS A").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));

        assertEquals(layout.getPoint("HS A").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS A this tests nothing at all");

        assertEquals(layout.getPoint("HS D").getCurrentLocomotive(), loc(LOC_A),
            "precondition: the arriving train must be the one standing on the watched square - that "
            + "is the whole case");

        assign(layout, LOC_A, "HS A");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "the railway allows this arrival - the train leaving the watched square is exempt - so "
            + "the planner must offer it.  Got: " + plan);

        applyPlan(layout, plan);

        assertEveryoneHome(layout);
    }

    /**
     * Return Home does NOT stage a train into a home FR-001 is holding back.
     *
     * Adam: **"if it's cleaner to go with consistency across the board, then let's enforce the occupancy ruling in all
     * modes and then rely on isPathClear.  Revert the prior lax ruling."**  So the planner reads the restriction, as it
     * must: the runtime refuses this arrival in every tier, and a plan offering a leg the railway refuses is OB-073.
     * `auditAgainstRuntime` keeps the two together.
     *
     * The fixture is the strongest form: nothing can leave block HSW (a siding of its own), so HS B is held back for
     * the whole run and no shunting clears it; the planner will happily move an occupant OFF a watched square and back,
     * so an escapable watcher proves nothing about the rule.  **The control is the same fixture with the watcher
     * cleared**; without it NO_PLAN_FOUND would be satisfied by a layout nothing can be staged on.
     *
     * MUTATION: dropping the FR-001 term from `canRest(loc, at, state)` fails this.
     */
    @Test
    public void testAHomeHeldBackByAnOccupiedSquareIsNotStaged() throws Exception
    {
        Layout layout = load(blockOfTwoWatching(LOC_B, null));

        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS W1")));

        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS B this tests nothing at all");

        assertEquals(layout.getPoint("HS W2").getCurrentLocomotive(), loc(LOC_B),
            "precondition: somebody else must be standing on the watched block, and be stuck there");

        assertEquals(Point.heldBackBy(layout.getPoint("HS B"), loc(LOC_A)), layout.getPoint("HS W1"),
            "precondition: FR-001 really does hold HS B back here - that is the restriction whose "
            + "tier is under test, and without it this fixture says nothing");

        assign(layout, LOC_A, "HS B");

        assertNotEquals(layout.planReturnToHome().getOutcome(), HomeStaging.Outcome.READY,
            "Return Home staged a train into a home held back by a square nothing can leave. Since"
            + " Adam's ruling of 2026-09-10 the runtime refuses that arrival in every tier, so a plan"
            + " containing it is a plan the first move fails on - which is OB-073.  Got: "
            + layout.planReturnToHome());

        // THE CONTROL: the same fixture with the RESTRICTION removed rather than the train, or the refusal above is
        // satisfied by a layout nothing can be staged on; lifting the occupant would take LOC_B off its own home.
        layout.getPoint("HS B").setBlockedBy(new java.util.LinkedList<Point>());

        HomeStaging.Plan freed = layout.planReturnToHome();

        assertEquals(freed.getOutcome(), HomeStaging.Outcome.READY,
            "with the watched block empty the same arrangement is still not staged, so the refusal"
            + " above says nothing about the restriction - it says this fixture cannot be staged."
            + "  Got: " + freed);

        applyPlan(layout, freed);

        assertEveryoneHome(layout);
    }

    /**
     * A home reached only through a square held back by an occupied one is not staged through it (OB-295): the runtime
     * refuses the route, so a plan through it is OB-073's plan that fails on its first move.
     *
     * HS A to HS C runs through HS B, and HS B is held back while HS W1's block is occupied - by LOC_B, on HS W2, with
     * nowhere to go.
     *
     * MUTATION: leave the squares a plan passes out of its FR-001 question, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testAHomeBeyondAHeldBackSquareIsNotStagedThroughIt() throws Exception
    {
        Layout layout = load(blockOfTwoWatching(LOC_B, null));

        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS W1")));

        assign(layout, LOC_A, "HS C");

        List<Edge> through = new java.util.LinkedList<>();

        through.add(layout.getEdge("HS A", "HS B"));
        through.add(layout.getEdge("HS B", "HS C"));

        assertFalse(layout.isPathClear(through, loc(LOC_A), false), "precondition: the railway lets a train through HS"
            + " B while the square holding it back is occupied, so a plan through it is no plan the runtime refuses");

        assertNotEquals(layout.planReturnToHome().getOutcome(), HomeStaging.Outcome.READY, "Return Home staged a train"
            + " through a square held back by an occupied one, which the railway refuses (OB-295): "
            + layout.planReturnToHome());

        // THE CONTROL: the restriction removed, the same arrangement is staged.
        layout.getPoint("HS B").setBlockedBy(new java.util.LinkedList<Point>());

        HomeStaging.Plan freed = layout.planReturnToHome();

        assertEquals(freed.getOutcome(), HomeStaging.Outcome.READY, "with nothing held back the same arrangement is not"
            + " staged either, so the refusal above says nothing about the restriction: " + freed);
    }

    /**
     * Every tier gives the same answer about a held-back arrival.
     *
     * The other half of Adam's ruling: with both fences gone (`isAutoRunning`, then `isFullAutonomyRunning`), the
     * question **"may a train be sent to a station held back by an occupied square"** has one answer wherever it is
     * asked: no.  Asserted over the three flag combinations that used to differ, by reflection as
     * `testAStagingRunIsNotCapturedIntoItsOwnTimetable` reaches them: stopped, running under a timetable, and running
     * as full autonomy.  A fence of either kind coming back moves exactly one of these three.
     */
    @Test
    public void testEveryTierRefusesAHeldBackArrival() throws Exception
    {
        Layout layout = load(blockOfTwoWatching(LOC_B, null));

        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS W1")));

        assertEquals(Point.heldBackBy(layout.getPoint("HS B"), loc(LOC_A)), layout.getPoint("HS W1"),
            "precondition: FR-001 holds HS B back, or there is nothing for the fence to decide");

        assign(layout, LOC_A, "HS B");

        // THE PATH IS TAKEN FROM THE RAILWAY, not from a plan: since the ruling the planner refuses
        // this arrival too, so there is no staged move to read it out of - which is the point.
        List<Edge> path = new java.util.LinkedList<>();

        path.add(layout.getEdge("HS A", "HS B"));

        assertNotNull(path.get(0), "precondition: the fixture has no edge from HS A to HS B");

        // The two flags set the way executeTimetable sets them, by reflection: runLocomotives dispatches trains, and
        // what is under test is the flag rather than the dispatch.
        Field running = Layout.class.getDeclaredField("running");
        Field executing = Layout.class.getDeclaredField("timetableExecuting");

        running.setAccessible(true);
        executing.setAccessible(true);

        try
        {
            // STOPPED, which is what a hand dispatch looks like to this rule.
            assertFalse(layout.isPathClear(path, loc(LOC_A), false),
                "a hand-driven send was allowed into a station held back by an occupied square. Since"
                + " 2026-09-10 there is no fence on this rule at all");

            // RUNNING UNDER A TIMETABLE, which is what a Return Home staging run looks like.
            running.setBoolean(layout, true);
            executing.setBoolean(layout, true);

            assertFalse(layout.isPathClear(path, loc(LOC_A), false),
                "a staging run was allowed into a station held back by an occupied square, so the"
                + " `isFullAutonomyRunning` fence is back - and the planner, which now refuses this"
                + " arrival, would be the stricter half");

            // AND FULL AUTONOMY, which is the tier that always refused it.
            executing.setBoolean(layout, false);

            assertFalse(layout.isPathClear(path, loc(LOC_A), false),
                "full autonomy allowed it, which no version of this rule has ever done");
        }
        finally
        {
            running.setBoolean(layout, false);
            executing.setBoolean(layout, false);
        }

        // AND THE CONTROL: with the watched block empty every tier allows it, so the three refusals
        // above are the restriction rather than a path that does not exist.
        layout.getPoint("HS W2").setLocomotive(null);

        assertTrue(layout.isPathClear(path, loc(LOC_A), false),
            "the path is refused even with nothing standing on the watched block, so the three"
            + " refusals above say nothing about FR-001");
    }

    /**
     * A locomotive held on two points is reported, not planned for.
     *
     * A locked path reserves every point along it for the one locomotive, and a path that failed part-way through
     * unlocking leaves those reservations standing.  Nothing in the model tells a reservation from a train (`reserve()`
     * and `setLocomotive()` write the same field), so the planner can see a locomotive at two places at once.  Counting
     * it properly is the wrong fix: the plan would depart from whichever point the map yields first, driving a real
     * train from a place it is not standing.  NO_PLAN_FOUND claims nothing false; a guessed origin may.
     *
     * The operator is told instead the locomotive and the remedy: place it on the square it is actually on, which
     * sweeps the rest.
     *
     * MUTATION: removing the check returns NO_PLAN_FOUND, which the first assertion rejects; naming nothing fails the
     * second.
     */
    @Test
    public void testALocomotiveHeldOnTwoPointsIsReportedRatherThanGuessedAt() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        assign(layout, LOC_A, "HS C");

        // The control first, so a failure below cannot be the fixture being unable to plan at all.
        assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
            "precondition: with one reservation this is an ordinary two-station move");

        reserveAsAFailedPathWould(layout, "HS B", LOC_A);

        assertEquals(layout.getPoint("HS A").getCurrentLocomotive(), loc(LOC_A),
            "the fixture did not take: the train has to still be held at HS A as well");

        assertEquals(layout.getPoint("HS B").getCurrentLocomotive(), loc(LOC_A),
            "the fixture did not take: HS B has to be reserved for the same locomotive");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.POSITION_AMBIGUOUS,
            "a locomotive standing in two places was planned for anyway, or dismissed as no plan "
            + "found - neither of which tells the operator what is actually wrong: " + plan);

        assertTrue(plan.getBlocked().contains(loc(LOC_A)),
            "and it is named, or the operator has nothing to act on: " + plan.getBlocked());

        assertTrue(plan.getMoves().isEmpty(),
            "nothing may be planned for a train whose position is not known: " + plan.getMoves());
    }

    /**
     * Reserves a point for a locomotive without taking it off anywhere else, as a locked path does.  reserve() is
     * package-private on purpose, so the test reaches it the way the failure does rather than widening the door.
     *
     * @param layout the graph
     * @param pointName the point to reserve
     * @param locName the locomotive to reserve it for
     */
    private static void reserveAsAFailedPathWould(Layout layout, String pointName, String locName)
        throws Exception
    {
        Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod(
            "reserve", org.traincontrol.base.Locomotive.class);

        reserve.setAccessible(true);

        reserve.invoke(layout.getPoint(pointName), loc(locName));
    }

    /**
     * A train may not be planned into the detection section it is standing on.
     *
     * canEnter exempts the MOVER from its own sensor ("there != null && !there.equals(loc)"), but isPathClear reads the
     * live feedback for the end of every edge and refuses the path whoever is standing on it.  The exemption looks
     * right (a train must not be blocked by its own sensor), yet isPathClear never checks the point a path starts from:
     * the train is blocked from a DIFFERENT point that reports the same sensor, which really is set while it stands
     * there.
     *
     * Hardware-conditional: on pulsed feedback the sensor clears behind the train; on latching detection it fires every
     * time.  So the feedback is set here explicitly.
     *
     * MUTATION: putting the exemption back makes the planner ready a move the runtime refuses, which is the first and
     * last assertions together.
     */
    @Test
    public void testATrainIsNotPlannedIntoItsOwnDetectionSection() throws Exception
    {
        Layout layout = load(json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 0, null, true, null) + ","
            + square("HS C", 2, null, true, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertEquals(layout.getPoint("HS B").getS88(), layout.getPoint("HS A").getS88(),
            "precondition: HS A and HS B are one detection section, which is the whole case");

        assign(layout, LOC_A, "HS C");

        String sensor = layout.getPoint("HS A").getS88();

        model.newFeedback(Integer.parseInt(sensor), null);

        assertTrue(model.setFeedbackState(sensor, true),
            "precondition: the feedback has to exist, or nothing below is testing anything");

        try
        {
            assertTrue(layout.isFeedbackOccupied(sensor),
                "precondition: the section the train is standing on reads occupied, which is what "
                + "latching occupancy detection does and pulsed feedback does not");

            // What the RUNTIME says, which is the half that does not move.
            assertTrue(layout.getPossiblePaths(loc(LOC_A), true).isEmpty(),
                "precondition: the runtime offers this train nowhere to go, because the only way out "
                + "of HS A ends on a point reporting the sensor HS A is holding");

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
                "the planner readied a move the runtime has just refused, so the run would start, "
                + "retry every two seconds and be abandoned: " + plan);
        }
        finally
        {
            // The model is shared by every test in this class, and a sensor left set is a fault that
            // would surface somewhere else entirely.
            model.setFeedbackState(sensor, false);
        }
    }


    /**
     * The record of where the railway turned a train follows a rename, and goes when the train does.
     *
     * `Layout.reversedOnArrival` is keyed by NAME, because what it feeds is a facing written into the setup and a file
     * holds a name.  A deleted locomotive's entry must not be inherited by a later one of the same name, and a rename
     * must not lose the entry.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheTurnRecordFollowsARenameAndGoesWithADelete() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        java.lang.reflect.Field record = Layout.class.getDeclaredField("reversedOnArrival");
        record.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Map<String, String> turns = (java.util.Map<String, String>) record.get(layout);

        turns.put(LOC_A, "HS A");

        assertEquals(layout.turnedOnArrivalAt(LOC_A), "HS A", "precondition: the record was not written");

        // THROUGH THE DOOR THE OPERATOR USES: `renameLoc` is what has to remember the record, and pinning
        // `locRenamed` alone left the call site as the only thing that could be wrong.
        assertTrue(model.renameLoc(LOC_A, "HS renamed"), "precondition: the rename was refused");

        // PUT BACK WHATEVER HAPPENS: the locomotive database is shared with every other class in this suite.
        try
        {
            assertNull(layout.turnedOnArrivalAt(LOC_A), "the record still answers under the old name");
            assertEquals(layout.turnedOnArrivalAt("HS renamed"), "HS A",
                "the turn was lost by the rename, so the train keeps the facing it drove in with");
        }
        finally
        {
            assertTrue(model.renameLoc("HS renamed", LOC_A),
                "the name was not put back for the rest of the class");
        }

        layout.locDeleted(loc(LOC_A));

        assertNull(layout.turnedOnArrivalAt(LOC_A),
            "the record survives the locomotive, so a later one given the same name inherits a turn it never made");
    }

    /**
     * A configuration carrying a negative maximum train length does not load at all.
     *
     * `Layout.fromJSON` requires `maxTrainLength >= 0` and invalidates the WHOLE configuration otherwise, which is why
     * the editor door refuses one (pinned in `core.testMassAssignLengths`, which builds no running layout and could not
     * show this half).
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testANegativeMaximumStopsTheConfigurationLoading() throws Exception
    {
        String config = ring(LOC_A, null, null);

        String plain = json(station("HS B", 1, null));
        String limited = json(station("HS B", 1, null).replace("}", ", 'maxTrainLength': -3}"));

        assertTrue(config.contains(plain), "precondition: the ring fixture no longer emits HS B plainly");

        model.parseAuto(config.replace(plain, limited));

        Layout built = model.getAutoLayout();

        // REFUSED, AND REFUSED FOR THE MAXIMUM: `assertFalse(built != null && built.isValid())` passes for ANY
        // invalidation, so the error the loader recorded is what distinguishes "the negative maximum was rejected" from
        // "this fixture stopped loading for another reason".
        assertFalse(built != null && built.isValid(),
            "a configuration with a maximum train length of -3 loaded, so the editor door's refusal is guarding"
            + " nothing - and the reason SET-B1 mattered was that it does not load");

        String why = Layout.getLastError();

        assertTrue(why != null && why.toLowerCase().contains("length"),
            "the configuration was refused, but not for the train length: " + why + ".  A refusal for"
            + " some other reason would hide the maximum loading perfectly well");

        // AND THE CONTROL: the same railway without the negative loads.
        model.parseAuto(config);

        assertTrue(model.getAutoLayout().isValid(),
            "the plain fixture does not load either, so the claim above is about the fixture and not the maximum: "
            + Layout.getLastError());
    }

    /**
     * A train that cannot leave where it stands is named, not searched for (OB-325).
     *
     * Its home is connected by track, so the pre-scan passes it, and the search can find no first move; without the
     * scan Return Home searches the whole fleet for about fifteen seconds and answers "no plan found", naming nobody.
     *
     * Here the train of 4 stands on HS T, whose one way out is HS U (a terminus that takes 2) and its home HS A lies
     * beyond HS U.  The plan is IMPOSSIBLE and names it, with the reason.  The control: a train of 2 fits HS U and goes
     * home in two moves.
     *
     * MUTATION: drop the first-move scan from `plan` and the first assertion fails (NO_PLAN_FOUND); have the scan
     * answer "no move" for everybody and the control does.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATrainThatCannotLeaveWhereItStandsIsNamed() throws Exception
    {
        Layout layout = load(json("{'points': ["
            + "{'name': 'HS A', 'station': true, 's88': " + S88_BASE + ", 'maxTrainLength': 9},"
            + "{'name': 'HS T', 'station': true, 's88': " + (S88_BASE + 1) + ", 'loc': {'name': '" + LOC_A + "'}},"
            + "{'name': 'HS U', 'station': true, 's88': " + (S88_BASE + 2) + ", 'terminus': true, 'maxTrainLength': 2}"
            + "],'edges': [" + edge("HS T", "HS U") + "," + edge("HS U", "HS A")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertTrue(layout.getPoint("HS U").isTerminus(), "precondition: HS U is not a terminus");
        assertEquals((int) layout.getPoint("HS U").getMaxTrainLength(), 2, "precondition: HS U does not take 2");

        assign(layout, LOC_A, "HS A");

        MarklinLocomotive train = loc(LOC_A);

        boolean[] wasReversible = setReversible(true, LOC_A);
        Integer wasLength = train.getTrainLength();

        try
        {
            train.setTrainLength(4);

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE, "a train of 4 on HS T, whose only way out is"
                + " a terminus that takes 2, can never leave - Return Home searched for it and answered that nothing was"
                + " found, naming nobody (OB-325).  Got: " + plan.getOutcome());

            assertEquals(plan.getBlocked(), java.util.Collections.singletonList(train), "the train that cannot leave was"
                + " not the one named");

            List<String> why = plan.getReasons().get(train);

            assertTrue(why != null && why.stream().anyMatch(s -> s.contains("HS T")), "the reason does not say where the"
                + " train is stuck: " + why);

            // THE CONTROL: a train of 2 fits HS U, turns there and goes home
            train.setTrainLength(2);

            HomeStaging.Plan control = HomeStaging.snapshot(layout).plan();

            assertEquals(control.getOutcome(), HomeStaging.Outcome.READY, "a train of 2, which fits HS U, was not planned"
                + " home - the scan refuses a train that can leave.  Got: " + control);
        }
        finally
        {
            train.setTrainLength(wasLength);
            restoreReversible(wasReversible, LOC_A);
        }
    }

    /**
     * A train held in only by another train is planned, not named (OB-325's guard).
     *
     * The first-move scan names a train as one that can never leave, and that is a proof only because it is asked with
     * every other train taken off.  Here HS alpha's one way out runs through HS U, where HS bravo stands, and HS bravo can
     * move on to its own home first.  Asked with HS bravo left where it is, HS alpha would have no move at all, and the
     * plan would name it as stuck where two moves bring both trains home.
     *
     * MUTATION: ask the scan with the whole arrangement instead of the train alone, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATrainHeldInOnlyByAnotherIsPlannedNotNamed() throws Exception
    {
        Layout layout = load(json("{'points': ["
            + station("HS X", 0, LOC_A) + ","
            + station("HS U", 1, LOC_B) + ","
            + station("HS H", 2, null) + ","
            + station("HS Y", 3, null)
            + "],'edges': [" + edge("HS X", "HS U") + "," + edge("HS U", "HS H") + "," + edge("HS U", "HS Y")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assign(layout, LOC_A, "HS H");
        assign(layout, LOC_B, "HS Y");

        assertEquals(locationOfLoc(layout, LOC_B), layout.getPoint("HS U"),
            "precondition: HS bravo is not standing in HS alpha's way");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY, "HS alpha's only way out is through HS bravo, which"
            + " can move on to its own home first - the plan answered " + plan.getOutcome() + ", naming "
            + plan.getBlocked() + ": a train that another only had to move out of the way for was named as stuck");

        applyPlan(layout, plan);

        assertEveryoneHome(layout);
    }

    /**
     * A sensor reading occupied with nothing known on it is not a proof that a train can never leave.
     *
     * A sensor that reads occupied which no train on the graph accounts for blocks every arrangement alike, so where
     * one lay on a train's only way out the first-move scan would name the train with a sentence about its length that
     * is not the cause.  What holds such a contact (an unknown vehicle, a dirty contact, a tail past where the walk
     * stops) goes when somebody clears it: live state, not a fact about the railway.  So the train is left to the
     * search, which answers nothing found.  The control: the sensor clear, the plan is ready.
     *
     * MUTATION: name the train without asking again with no sensor read, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testASensorOnTheOnlyWayOutIsNotAProof() throws Exception
    {
        Layout layout = load(json("{'points': ["
            + "{'name': 'HS T', 'station': true, 's88': " + S88_BASE + ", 'loc': {'name': '" + LOC_A + "'}},"
            + "{'name': 'HS M', 'station': false, 's88': " + (S88_BASE + 1) + "},"
            + "{'name': 'HS H', 'station': true, 's88': " + (S88_BASE + 2) + "}"
            + "],'edges': [" + edge("HS T", "HS M") + "," + edge("HS M", "HS H")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assign(layout, LOC_A, "HS H");

        String sensor = layout.getPoint("HS M").getS88();

        try
        {
            model.setFeedbackState(sensor, true);

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertFalse(plan.getBlocked().contains(loc(LOC_A)), "a train held only by a sensor that reads occupied with"
                + " nothing known on it was named as one that can never get home (" + plan.getOutcome() + ", "
                + plan.getReasons() + ") - the sensor goes when somebody clears it (RSA58-C1)");

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.NO_PLAN_FOUND, "a train held only by an occupied sensor"
                + " is answered as before round 93: nothing found, it may still be possible");

            // THE CONTROL: the sensor clear, the one road home is open
            model.setFeedbackState(sensor, false);

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
                "control: with the sensor clear the train still cannot be planned home, so the answer above is not the"
                + " sensor's");
        }
        finally
        {
            model.setFeedbackState(sensor, false);
        }
    }
}
