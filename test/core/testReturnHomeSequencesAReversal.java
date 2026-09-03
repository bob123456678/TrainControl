package core;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.base.Accessory;

/**
 * Return Home reaches a berth that no single path can reach, by stopping on the way.
 *
 * Adam, on the limitation: "yes, return home should learn to sequence a reversal as two paths."  And
 * on this file: "We need test cases for that return home paradigm."
 *
 * WHAT MAKES IT TWO PATHS.  A path sets every switch it needs before the train departs, so one
 * accessory cannot be asked for two settings on one route.  A journey that turns round and comes back
 * through the junction it went out over asks exactly that.  Measured on his own layout: 2-8-4 3505 SP
 * from TopMainR2 to TopMainR0Park is refused with "Has conflicting commands ([Switch 75 STRAIGHT])",
 * and the same journey split at the reversing point is two clear paths.
 *
 * This is that shape in miniature - one switch, wanted one way to reach the reversing point and the
 * other way to leave it - and the three tests are the three things worth knowing about it: that the
 * single path really is refused (or the rest proves nothing), that the planner gets there anyway, and
 * that it does not invent a stop when the run was clear all along.
 */
public class testReturnHomeSequencesAReversal
{
    private static MarklinControlStation model;

    /** The sensors the three points stand on. */
    private static final int FEEDBACK_BASE = 2600;

    /** The one switch this is all about. */
    private static final int SWITCH_ADDRESS = 250;

    private static final Accessory.accessoryDecoderType MM2 = Accessory.accessoryDecoderType.MM2;

    private static String swtch;

    /**
     * The layout preference, pointed at a throwaway copy before anything is constructed.
     *
     * Not optional, and not a formality: the window opens whatever the saved preference names, which on
     * Adam's machine is his real railway.  The first version of this class went without it and
     * testSwitchingToACentralStationLayout caught it - "there are now 57 test classes that build a model
     * without pointing the layout preference at a sandbox first, up from 56".
     */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);

        for (int i = 0; i < 5; i++)
        {
            model.newFeedback(FEEDBACK_BASE + i, null);
        }

        MarklinAccessory acc = model.newSwitch(SWITCH_ADDRESS, MM2, false);

        swtch = acc.getName();
    }

    @org.testng.annotations.AfterClass
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The precondition everything else rests on: as ONE path, the journey is refused.
     *
     * Without this the two tests below could pass on a layout where the direct route worked, and would
     * be saying nothing about reversals at all.
     */
    @Test
    public void testTheJourneyIsRefusedAsASinglePath() throws Exception
    {
        Layout layout = reversalLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            List<Edge> whole = new LinkedList<>();

            whole.add(layout.getEdge("RH start", "RH mid"));
            whole.add(layout.getEdge("RH mid", "RH home"));

            assertFalse(layout.isPathClear(whole, loc, false),
                "the two legs together were accepted as one path, so this fixture does not reproduce "
                + "the thing it is about - one switch asked for two settings on one route");

            assertTrue(String.valueOf(Layout.getLastError()).toLowerCase().contains("conflicting"),
                "the single path was refused, but for some other reason than the switch conflict - so "
                + "the fixture is what is wrong here, not the planner: " + Layout.getLastError());

            // And each leg on its own is fine, which is what makes two moves possible at all.
            assertTrue(layout.isPathClear(
                Arrays.asList(layout.getEdge("RH start", "RH mid")), loc, false),
                "the first leg is not clear on its own: " + Layout.getLastError());

            assertTrue(layout.isPathClear(
                Arrays.asList(layout.getEdge("RH mid", "RH home")), loc, false),
                "the second leg is not clear on its own: " + Layout.getLastError());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * And Return Home gets the train there, by stopping at the reversing point on the way.
     */
    @Test
    public void testReturnHomeGoesViaTheReversingPoint() throws Exception
    {
        Layout layout = reversalLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            assertTrue(layout.moveLocomotive(loc.getName(), "RH start", false),
                "could not place the locomotive");

            layout.setHomeLocomotive("RH home", loc.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertTrue(plan.isPossible(),
                "Return Home gave up on a berth the train can reach in two moves - out to the "
                + "reversing point, then in.  Outcome " + plan.getOutcome()
                + ", blocked " + plan.getBlocked());

            assertEquals(plan.getMoves().size(), 2,
                "the plan does not take two moves, and one move is the path the runtime refuses - it "
                + "has to stop at the reversing point and set the switch again: " + plan.getMoves());

            assertEquals(plan.getMoves().get(0).getEnd().getName(), "RH mid",
                "the first move does not stop at the reversing point, so whatever this plan is doing "
                + "it is not turning the train round: " + plan.getMoves());

            assertEquals(plan.getMoves().get(1).getEnd().getName(), "RH home",
                "the plan does not finish at the home: " + plan.getMoves());

            // BOTH MOVES ARE THE SAME TRAIN.  A plan of the right length made of two different
            // trains moves would pass every line above.
            assertEquals(plan.getMoves().get(0).getLocomotive().getName(), loc.getName(),
                "the first move belongs to another locomotive: " + plan.getMoves());

            assertEquals(plan.getMoves().get(1).getLocomotive().getName(), loc.getName(),
                "the second move belongs to another locomotive: " + plan.getMoves());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * ...and it does not stop on the way when it does not have to.
     *
     * A planner that always went by way of the reversing point would satisfy the test above perfectly
     * and be wrong: the extra move is a real cost on a real railway, and a train whose home is
     * straight ahead of it should simply go.
     */
    @Test
    public void testAnOrdinaryJourneyIsStillOneMove() throws Exception
    {
        Layout layout = reversalLayout();

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            // The conflict removed, and nothing else changed: now the whole run is one clear path.
            layout.getEdge("RH mid", "RH home").clearAllConfigCommands();

            assertTrue(layout.moveLocomotive(loc.getName(), "RH start", false),
                "could not place the locomotive");

            layout.setHomeLocomotive("RH home", loc.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertTrue(plan.isPossible(), "the plain journey was refused: " + plan.getOutcome());

            assertEquals(plan.getMoves().size(), 1,
                "a train whose home is reachable in one move was sent round by way of the reversing "
                + "point, which is a stop nobody needs: " + plan.getMoves());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * The stop may be a square autonomy is not allowed to choose - which is the arrangement Adam asked
     * about, and it is what makes this usable on his layout.
     *
     * "If we made it a 'may reverse' station, unselectable in autonomy, would that allow trains to pass
     * through and stop there to reverse in manual operation, then allowing the selection of the parking
     * tracks?"
     *
     * Yes, and for homing too.  The two questions are separate ones: `isAutoDestination` says whether
     * autonomy may send a train there as a destination of its own, and homing is not autonomy choosing
     * - it is carrying out an instruction the operator has already given.  A reversing square marked
     * "never pick this" is exactly the right shape for a place that exists only to turn trains round,
     * and it would have been useless for that if this said no.
     */
    @Test
    public void testTheStopMayBeASquareAutonomyWillNotChoose() throws Exception
    {
        Layout layout = reversalLayout();

        // The workaround, set: a place trains turn round at, that autonomy never sends anyone to.
        layout.getPoint("RH mid").setAutoDestination(false);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            assertTrue(layout.moveLocomotive(loc.getName(), "RH start", false),
                "could not place the locomotive");

            layout.setHomeLocomotive("RH home", loc.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertTrue(plan.isPossible(),
                "a reversing square that autonomy may not choose was refused as a place to turn round "
                + "on the way home, which is the one job that kind of square exists to do.  Outcome "
                + plan.getOutcome() + ", blocked " + plan.getBlocked());

            assertEquals(plan.getMoves().size(), 2,
                "the plan is not the two-move one: " + plan.getMoves());

            assertEquals(plan.getMoves().get(0).getEnd().getName(), "RH mid",
                "the train did not stop at the reversing square: " + plan.getMoves());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * But a square that is switched OFF is not one, and the difference is worth stating.
     *
     * Out of service means no train stands there, and a reversal is a train standing there - so the
     * route through it goes with it.  This is the right answer and not an oversight, but it is one
     * click away from the arrangement above and the two look alike in the editor, so it is pinned:
     * a later change that quietly let homing use switched-off squares would be driving trains onto
     * track the operator has taken out of use.
     */
    @Test
    public void testASwitchedOffSquareIsNotAPlaceToTurnRound() throws Exception
    {
        Layout layout = reversalLayout();

        layout.getPoint("RH mid").setActive(false);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            assertTrue(layout.moveLocomotive(loc.getName(), "RH start", false),
                "could not place the locomotive");

            layout.setHomeLocomotive("RH home", loc.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertFalse(plan.isPossible(),
                "a train was routed home by way of a square the operator has taken out of service: "
                + plan.getMoves());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * A train STANDING on a reversing point has already turned, and the reachability proof must agree.
     *
     * D24-B1.  Two searches in this class ask whether a route turns the train round, and they disagreed
     * about where the train starts.  `firstClearRoute` sets off with `turned = from.isReversing()` -
     * "a train already standing on a reversing point sets off turned" - and `connected` set off with
     * `false`, always.
     *
     * That difference would be harmless in a heuristic.  It is not harmless here, because `connected`
     * is what `plan()` consults to declare a locomotive UNREACHABLE, and unreachable is the one thing
     * this class claims to have PROVED: the outcome is IMPOSSIBLE, no moves are offered, and the
     * operator is told to go and look at the track.  A proof may only use rules the runtime actually
     * enforces.  Being stricter than the executor does not make it safe, it makes it wrong - it refuses
     * a journey `firstClearRoute` would have routed and blames the railway.
     *
     * The shape is the smallest one that separates them: the train is standing on the reversing point
     * itself, so the turn is behind it before it moves, and nothing further along the way turns
     * anything.  Start it anywhere else and both searches agree.
     *
     * MUTATION: putting `turned.add(false)` back in `connected` fails this - the outcome goes to
     * IMPOSSIBLE with the locomotive named as blocked.
     */
    @Test
    public void testATrainStandingOnAReversingPointHasAlreadyTurned() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RH pad", true, Integer.toString(FEEDBACK_BASE));
        layout.createPoint("RH berth", true, Integer.toString(FEEDBACK_BASE + 1));

        // Where it stands turns trains; where it is going demands one, and there is nothing in between.
        layout.getPoint("RH pad").setReversing(true);
        layout.getPoint("RH berth").setTerminus(true);

        layout.createEdge("RH pad", "RH berth");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            // It cannot turn itself, so it has to arrive already turned - which it is.
            loc.setReversible(false);

            assertTrue(layout.moveLocomotive(loc.getName(), "RH pad", false),
                "could not place the locomotive");

            layout.setHomeLocomotive("RH berth", loc.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertNotEquals(String.valueOf(plan.getOutcome()), "IMPOSSIBLE",
                "the planner PROVED a journey impossible that its own route search would have taken - "
                + "the train is standing on the reversing point, so it is already turned.  Blocked: "
                + plan.getBlocked());

            assertTrue(plan.isPossible(),
                "and it should have a plan: one move, off the pad and into the berth.  Outcome "
                + plan.getOutcome());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * A train leaving a TERMINUS leaves forwards, so the planner may not claim it can back in.
     *
     * The other half of D24-B1 was that `executePath` flips direction on arrival at a terminus and at a
     * reversing point alike, in one statement.  True - and it does not mean the two seed this search
     * the same way, which is what the first fix assumed and what SV2-A1 caught before it shipped.
     *
     * `turned` is asked at arrival by `mustBackIn`, and it means "this train will BACK INTO the berth
     * it is ending at".  At a reversing point the arrival flip leaves the train trailing, so it goes on
     * backing.  At a terminus that flip is the one that turns a backed-in train round to face OUT
     * again; it is spent.  A train leaving a berth leaves forwards, and would arrive nose first at the
     * next one - stuck in a berth it cannot reverse out of, which is the whole of what Adam asked for
     * when he said non-reversing trains must back in.
     *
     * **So the right answer here is that no plan is found, and the point of the test is WHICH refusal.**
     * `NO_PLAN_FOUND` says "I did not find a way" and is allowed to be wrong.  `IMPOSSIBLE` says "there
     * is no way", and this class only ever offers that as a proof - the operator is told to go and look
     * at the track.  `connected` is what makes that claim, and it seeds from a terminus as well as a
     * reversing point precisely so it cannot over-claim: a proof may be looser than the search it
     * guards, never tighter.
     *
     * MUTATION: dropping `|| from.isTerminus()` from `connected` turns the outcome into IMPOSSIBLE and
     * fails the first assertion.  Restoring it in `firstClearRoute` as well fails the second, because
     * the planner then offers a plan that drives a train nose first into a berth it cannot leave.
     */
    @Test
    public void testALeavingTerminusDoesNotCountAsHavingBackedIn() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RH berth A", true, Integer.toString(FEEDBACK_BASE));
        layout.createPoint("RH berth B", true, Integer.toString(FEEDBACK_BASE + 1));

        // Two berths, and nothing on the way between them that turns a train.
        layout.getPoint("RH berth A").setTerminus(true);
        layout.getPoint("RH berth B").setTerminus(true);

        layout.createEdge("RH berth A", "RH berth B");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(false);

            assertTrue(layout.moveLocomotive(loc.getName(), "RH berth A", false),
                "could not place the locomotive");

            layout.setHomeLocomotive("RH berth B", loc.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            // THE OUTCOME ITSELF, which is what this test is about (TV2-C5).
            //
            // Two assertions stood here - not IMPOSSIBLE, and not possible - and between them they
            // admitted five of the seven outcomes.  The javadoc says the point of the test is WHICH
            // refusal, and that was the one thing neither of them checked: if the home assignment ever
            // stopped taking, `triage()` would answer NO_HOMES before a line of the code under test
            // ran, and this would stay green while exercising nothing.
            //
            // One assertion implies both: NO_PLAN_FOUND is neither IMPOSSIBLE (a claim about the
            // track, which is fine) nor READY (a plan that drives the train in nose first).  Both
            // mutations still fail it - IMPOSSIBLE from the `connected` one, READY from the
            // `firstClearRoute` one.
            assertEquals(String.valueOf(plan.getOutcome()), "NO_PLAN_FOUND",
                "the planner did not answer NO_PLAN_FOUND.  IMPOSSIBLE would be a claim about the "
                + "track, and the track is fine; READY would be a plan that drives a non-reversible "
                + "train nose first into a berth it cannot reverse out of; anything else means this "
                + "test never reached the code it is about.  Blocked: " + plan.getBlocked()
                + ", moves: " + plan.getMoves());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * A journey that has to STOP somewhere on the way is not an impossible journey.
     *
     * Found on Adam's own railway, 2026-09-01, after he made the ramp a place trains may stop so that
     * they could get into the parking berths: the planner's answer went from "no arrangement found" to
     * **IMPOSSIBLE**, naming a locomotive, for a train whose two-move route he had just built.
     *
     * `connected` will not travel THROUGH a terminus - `if (!next.isTerminus())` - which is right for a
     * single path, because a train arrives at one and cannot drive on past it.  But `connected` is not
     * asked about a single path.  It is what `plan()` consults to declare a locomotive UNREACHABLE, and
     * `plan()`'s search ends a move at any station and starts the next one from there.  Stopping at a
     * terminus and setting off again is an ordinary two-move plan.
     *
     * So the proof was stricter than the planner it guards, which is the same defect as `D24-B1` in a
     * different limb, and the invariant `SV2` wrote down: **a proof may be looser than the search, never
     * tighter.**  IMPOSSIBLE sends the operator to look at track that is fine.
     *
     * MUTATION: restoring `if (!next.isTerminus())` in `connected` makes the outcome IMPOSSIBLE again
     * and fails the first assertion.
     */
    @Test
    public void testAJourneyThatMustStopOnTheWayIsNotImpossible() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RH from", true, Integer.toString(FEEDBACK_BASE));
        layout.createPoint("RH stop", true, Integer.toString(FEEDBACK_BASE + 1));
        layout.createPoint("RH end", true, Integer.toString(FEEDBACK_BASE + 2));

        // The only way to the far end is by stopping at a terminus in the middle - which a single path
        // may not drive through, and a plan of two moves may.
        layout.getPoint("RH stop").setTerminus(true);

        layout.createEdge("RH from", "RH stop");
        layout.createEdge("RH stop", "RH end");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            assertTrue(layout.moveLocomotive(loc.getName(), "RH from", false),
                "could not place the locomotive");

            layout.setHomeLocomotive("RH end", loc.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertNotEquals(String.valueOf(plan.getOutcome()), "IMPOSSIBLE",
                "the planner PROVED this journey impossible because the only way there is to stop at a "
                + "terminus on the way - which is a two-move plan, not an impossibility, and the search "
                + "it is guarding can make exactly that plan.  Blocked: " + plan.getBlocked());

            assertTrue(plan.isPossible(),
                "and there is a plan: out to the stop, then on to the end.  Outcome "
                + plan.getOutcome());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * Two trains whose only way home is the same single square, one at a time.
     *
     * Adam's railway in miniature, and the shape no fixture in this suite had.  Every staging test is
     * built on `ringWith`, a four-point ring where every station is one hop from every other - so no
     * test has ever had a home that takes TWO moves to reach, and the multi-move plans they do assert
     * are "step aside so another can pass", driven by occupancy rather than by distance.  On a ring the
     * planner's heuristic cannot be wrong about staging, because there is no staging.
     *
     * Here there is.  Neither berth can be reached in one path - the switch would have to be thrown two
     * ways on one route - so each train must stop at the ramp first, and only one train fits on it.  The
     * plan is a queue: in to the ramp, out to a berth, and again for the second train.
     *
     * **What this catches.** `misplaced` counts trains that are not home and gives no credit for
     * getting closer, so a move to the ramp costs one and buys nothing the search can see; it ranks
     * exactly level with shuffling a train somewhere useless.  With two trains and a shared bottleneck
     * that is enough to lose the plan among the permutations.
     *
     * MUTATION: scoring on `misplaced(next)` instead of the staging estimate fails this - the outcome
     * is NO_PLAN_FOUND.
     */
    @Test
    public void testTwoTrainsQueueThroughOneStagingSquare() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("Q ramp", true, Integer.toString(FEEDBACK_BASE));
        layout.createPoint("Q berth 1", true, Integer.toString(FEEDBACK_BASE + 1));
        layout.createPoint("Q berth 2", true, Integer.toString(FEEDBACK_BASE + 2));
        layout.createPoint("Q yard 1", true, Integer.toString(FEEDBACK_BASE + 3));
        layout.createPoint("Q yard 2", true, Integer.toString(FEEDBACK_BASE + 4));

        layout.getPoint("Q berth 1").setTerminus(true);
        layout.getPoint("Q berth 2").setTerminus(true);
        layout.getPoint("Q ramp").setReversing(true);

        // Both trains start out in the yard and must come in through the ramp.
        Edge in1 = layout.createEdge("Q yard 1", "Q ramp");
        Edge in2 = layout.createEdge("Q yard 2", "Q ramp");

        Edge out1 = layout.createEdge("Q ramp", "Q berth 1");
        Edge out2 = layout.createEdge("Q ramp", "Q berth 2");

        // THE SWITCH THAT MAKES IT TWO MOVES: one way in, the other way out, so no single path can do
        // both and the ramp has to be a stop rather than somewhere to pass through.
        in1.addConfigCommand(swtch, accessorySetting.STRAIGHT);
        in2.addConfigCommand(swtch, accessorySetting.STRAIGHT);
        out1.addConfigCommand(swtch, accessorySetting.TURN);
        out2.addConfigCommand(swtch, accessorySetting.TURN);

        Locomotive one = model.getLocByName(model.getLocList().get(0));
        Locomotive two = model.getLocByName(model.getLocList().get(1));

        boolean wasOne = one.isReversible();
        boolean wasTwo = two.isReversible();

        try
        {
            one.setReversible(true);
            two.setReversible(true);

            assertTrue(layout.moveLocomotive(one.getName(), "Q yard 1", false), "could not place one");
            assertTrue(layout.moveLocomotive(two.getName(), "Q yard 2", false), "could not place two");

            layout.setHomeLocomotive("Q berth 1", one.getName());
            layout.setHomeLocomotive("Q berth 2", two.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertTrue(plan.isPossible(),
                "neither train can reach its berth in one path, so each has to stop at the ramp on the "
                + "way - and only one of them fits on it at a time.  That is a queue, not an "
                + "impossibility.  Outcome " + plan.getOutcome() + ", blocked " + plan.getBlocked());

            // Four moves: in to the ramp and out to a berth, twice over.
            assertEquals(plan.getMoves().size(), 4,
                "the plan is not the queue: each train goes to the ramp and then to its berth, which "
                + "is two moves each: " + plan.getMoves());

            // AND THE RAMP IS NEVER OCCUPIED TWICE, which is what makes it a queue rather than two
            // independent journeys that happen to be listed one after the other.
            String rampHolder = null;

            for (HomeStaging.Move move : plan.getMoves())
            {
                if ("Q ramp".equals(move.getEnd().getName()))
                {
                    assertNull(rampHolder,
                        "a second train was sent to the ramp while " + rampHolder + " was still on it: "
                        + plan.getMoves());

                    rampHolder = move.getLocomotive().getName();
                }
                else if (move.getLocomotive().getName().equals(rampHolder))
                {
                    rampHolder = null;
                }
            }
        }
        finally
        {
            one.setReversible(wasOne);
            two.setReversible(wasTwo);
        }
    }

    /**
     * A home autonomy will never choose is still a home.
     *
     * Adam, 2026-09-01: **"Can Be Chosen in Full Autonomy - does not apply to returning home, of
     * course.  these should be allowed."**
     *
     * It is already true and nothing consulted it - `HomeStaging` does not mention `autoDestination`
     * anywhere - but "already true and untested" is how a rule gets removed by somebody tidying up.
     * The whole point of that flag is the parking berth: a square the operator sends trains to and
     * autonomy leaves alone.  A berth autonomy may not choose but a train may not return to would be
     * useless, and it is where most of his trains live.
     *
     * The sibling above pins the same flag on a square used as a STAGING stop.  This pins it on the
     * destination itself, which is the case he was talking about.
     *
     * MUTATION: adding `&& at.isAutoDestination()` to `canRest` fails this.
     */
    @Test
    public void testAHomeAutonomyWillNeverChooseIsStillAHome() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RH away", true, Integer.toString(FEEDBACK_BASE));
        layout.createPoint("RH berth", true, Integer.toString(FEEDBACK_BASE + 1));

        layout.createEdge("RH away", "RH berth");

        // The berth: somewhere the operator parks trains, and autonomy never picks.
        layout.getPoint("RH berth").setAutoDestination(false);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            assertFalse(layout.getPoint("RH berth").isAutoDestination(),
                "the fixture did not take: this berth has to be one autonomy will not choose, or the "
                + "test is about nothing");

            assertTrue(layout.moveLocomotive(loc.getName(), "RH away", false),
                "could not place the locomotive");

            layout.setHomeLocomotive("RH berth", loc.getName());

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertTrue(plan.isPossible(),
                "a locomotive was refused its own home because autonomy is not allowed to choose that "
                + "square - which is what a parking berth IS, and where most of his trains live.  "
                + "Outcome " + plan.getOutcome() + ", blocked " + plan.getBlocked());

            assertEquals(plan.getMoves().size(), 1, "one move, straight there: " + plan.getMoves());

            assertEquals(plan.getMoves().get(0).getEnd().getName(), "RH berth",
                "the plan does not end at the berth: " + plan.getMoves());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * A start, a reversing point, and a berth beyond it - with one switch that has to be thrown
     * differently for each leg.
     */
    private static Layout reversalLayout() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RH start", true, Integer.toString(FEEDBACK_BASE));
        layout.createPoint("RH mid", true, Integer.toString(FEEDBACK_BASE + 1));
        layout.createPoint("RH home", true, Integer.toString(FEEDBACK_BASE + 2));

        // Where the train turns round, and where it is going.
        layout.getPoint("RH mid").setReversing(true);
        layout.getPoint("RH home").setTerminus(true);

        Edge out = layout.createEdge("RH start", "RH mid");
        Edge in = layout.createEdge("RH mid", "RH home");

        // THE WHOLE POINT: one switch, wanted both ways.
        out.addConfigCommand(swtch, accessorySetting.STRAIGHT);
        in.addConfigCommand(swtch, accessorySetting.TURN);

        return layout;
    }
}
