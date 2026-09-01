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

        for (int i = 0; i < 3; i++)
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
     * ...and so has one standing on a TERMINUS, which is the half that bites on the real railway.
     *
     * FV2-B2.  The runtime turns a train on arrival at a terminus or a reversing point, in one
     * statement - `if (end.isTerminus() || end.isReversing())` in `executePath`.  So "a train already
     * standing on a reversing point sets off turned" is true of a terminus word for word, and the first
     * fix for D24-B1 took only the reversing limb.
     *
     * **This is the reachable one.**  On a diagram-derived graph a reversing Point is not a destination,
     * so `plan()`'s own `!isDestination()` clause catches that locomotive before `connected` is asked at
     * all.  A terminus copy IS emitted as a destination, and Adam's parking berths are termini - so the
     * case that survives to reach this code is a train parked in a berth, homed at another berth, with
     * nothing between them that turns anything.
     *
     * MUTATION: dropping `|| from.isTerminus()` from `connected` fails this and leaves the reversing
     * test above green.
     */
    @Test
    public void testATrainStandingOnATerminusHasAlreadyTurnedToo() throws Exception
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

            assertNotEquals(String.valueOf(plan.getOutcome()), "IMPOSSIBLE",
                "a train parked in a terminus berth, homed at another one, was PROVED unable to get "
                + "there - but it was turned round as it arrived where it stands, exactly as it would "
                + "be on arrival at the berth it is going to.  Blocked: " + plan.getBlocked());
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
