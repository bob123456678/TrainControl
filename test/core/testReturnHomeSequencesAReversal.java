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

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);

        for (int i = 0; i < 3; i++)
        {
            model.newFeedback(FEEDBACK_BASE + i, null);
        }

        MarklinAccessory acc = model.newSwitch(SWITCH_ADDRESS, MM2, false);

        swtch = acc.getName();
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
