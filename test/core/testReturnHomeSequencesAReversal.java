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
 * Return Home reaches a berth that no single path can reach, by stopping on the way (Adam: "yes, return home should
 * learn to sequence a reversal as two paths.").
 *
 * A path sets every switch it needs before the train departs, so one accessory cannot be asked for two settings on one
 * route; a journey that turns round and returns through the same junction asks exactly that.  This is that shape in
 * miniature, with three tests: the single path really is refused, the planner gets there anyway, and it does not invent
 * a stop when the run was clear all along.
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
     * The layout preference, pointed at a throwaway copy before anything is constructed: the window opens whatever the
     * saved preference names.
     */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();

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

    /** The precondition everything else rests on: as ONE path, the journey is refused. */
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

    /** And Return Home gets the train there, by stopping at the reversing point on the way. */
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

            // BOTH MOVES ARE THE SAME TRAIN: a plan of the right length made of two different trains would pass every
            // line above.
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

    /** ...and it does not stop on the way when it does not have to: the extra move is a real cost on a real railway. */
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
     * The stop may be a square autonomy is not allowed to choose, which is what makes this usable on Adam's layout
     * (Adam: "If we made it a 'may reverse' station, unselectable in autonomy, would that allow trains to pass through
     * and stop there to reverse in manual operation").  `isAutoDestination` is about autonomy's own choices, and homing
     * carries out an instruction the operator already gave.
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
     * But a square that is switched OFF is not one: no train stands there and a reversal is a train standing there.
     * Right, but it looks like the arrangement above in the editor, so it is pinned against driving trains onto track
     * taken out of use.
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
     * A train STANDING on a reversing point has already turned, and the reachability proof must agree:
     * `firstClearRoute` sets off with `turned = from.isReversing()` and `connected` used `false`.  `connected` is what
     * `plan()` consults to declare a locomotive UNREACHABLE, so a proof stricter than the executor refuses a journey it
     * would have routed and blames the railway.  The smallest separating shape: the train stands on the reversing point
     * itself.
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
     * Two berths with nothing between them that turns a train, and the planner now takes it (Adam: "Return home is
     * manual, take the rule out and say why.").  The train is driven NOSE FIRST into a berth it cannot reverse out of;
     * it gets home, which is what he asked for, as at `firstClearRoute`'s arrival test.  The `turned` seeding is
     * untouched: at a reversing point the arrival flip leaves the train trailing, at a terminus it is spent, and the
     * flag still decides how a route is described.
     *
     * MUTATION: putting `mustBackIn` back at `firstClearRoute`'s arrival test returns this to `NO_PLAN_FOUND`.
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

            // THE OUTCOME ITSELF, named exactly: "not impossible" admits five of the seven outcomes, and if the home
            // assignment stopped taking, `triage()` would answer NO_HOMES before the code under test ran.
            assertEquals(String.valueOf(plan.getOutcome()), "READY",
                "the planner did not answer READY.  Adam took `mustBackIn` out on 2026-09-04, so a "
                + "train that cannot reverse is now driven nose first into its berth rather than "
                + "refused - IMPOSSIBLE would be a claim about the track, and the track is fine; "
                + "NO_PLAN_FOUND is the answer this gave while the rule was there.  Blocked: "
                + plan.getBlocked() + ", moves: " + plan.getMoves());
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * A journey that has to STOP somewhere on the way is not an impossible journey.  `connected` will not travel
     * THROUGH a terminus, right for a single path, but it is what `plan()` consults to declare a locomotive UNREACHABLE
     * and the plan ends a move at any station.  A proof may be looser than the search, never tighter.
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

        // The only way to the far end is by stopping at a terminus in the middle, which a single path may not drive
        // through.
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
     * Two trains whose only way home is the same single square, one at a time: no other fixture here has a home that
     * takes TWO moves to reach (`ringWith` has every station one hop from every other).  Neither berth can be reached
     * in one path, so each train stops at the ramp first and only one fits on it; the plan is a queue.  `misplaced`
     * gives no credit for getting closer, so a move to the ramp ranks level with shuffling a train somewhere useless.
     *
     * No mutation is named: the search scores `cost + misplaced(next)` and has no separate staging estimate to swap.
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

        // THE SWITCH THAT MAKES IT TWO MOVES: one way in, the other way out, so the ramp has to be a stop.
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

            // AND THE RAMP IS NEVER OCCUPIED TWICE, which makes it a queue rather than two independent journeys.
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
     * A home autonomy will never choose is still a home (Adam: "Can Be Chosen in Full Autonomy - does not apply to
     * returning home, of course.  these should be allowed.").  `HomeStaging` never consulted `autoDestination`, but
     * "already true and untested" is how a rule gets removed by somebody tidying up.  The sibling above pins the flag
     * on a staging stop; this pins it on the destination itself.
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
     * A start, a reversing point, and a berth beyond it, with one switch that has to be thrown differently for each
     * leg.
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
