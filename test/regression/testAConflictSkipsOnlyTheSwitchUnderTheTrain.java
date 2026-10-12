package regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinRoute;

/**
 * An auto-triggered route skips the switch under the train and runs the rest of itself.
 *
 * Adam, 2026-09-06 (MT-247): **"1. cancel should cancel everything.  OK should fire everything.  2.
 * if the route is auto triggered: popup, just a notification in the log.  don't run the conflicting
 * switch commands, but do run the power off and others."**  Asked what "conflicting" means, he was
 * clear: an accessory command whose switch sits on track a train currently occupies or has reserved.
 * Everything else in the route still runs.
 *
 * **What was wrong.**  `execRoute` read the ruling as being about the WHOLE accessory group.  It
 * asked `accessoryHeldByAutonomy` once before the loop - which returns the FIRST conflicting command
 * in the route and nothing about the others - and set `skipAccessories` for the entire run:
 *
 *     boolean skipAccessories = auto && conflict != null;
 *
 * So one turnout under a train dropped every other turnout in the route with it, and the ones it
 * dropped were on track nothing was standing on.  A four-turnout route fired by an s88 set none of
 * them because of one.  The per-command check that already existed - `heldReason(rc)`, asked
 * immediately before each command, which is the right grain - could never run at that door, because
 * the group skip `continue`d past it.
 *
 * The cost is not hypothetical: `heldReason`'s own comment records the same shape being found from
 * the other side, where an over-strict signal rule fired "and because accessories are skipped as a
 * group, it took the whole route's turnouts with it".
 *
 * **The rule Adam is protecting cuts the other way.**  "Refused whole" was argued for accessories on
 * the ground that setting three switches of five leaves the layout in a state nobody chose.  That is
 * an argument about a route being interrupted PART WAY, which still happens and still cannot be
 * helped.  It is not an argument for refusing a switch on clear track because a different switch is
 * busy.
 *
 * MUTATION: restoring `boolean skipAccessories = auto && conflict != null;` and the
 * `if (skipAccessories) continue;` at the top of the accessory branch fails
 * `testTheClearSwitchIsStillThrown` in both orders.
 *
 * @author Adam
 */
public class testAConflictSkipsOnlyTheSwitchUnderTheTrain
{
    private static MarklinControlStation model;

    /**
     * How long the power is given to come back on after a `go()`.
     *
     * An acknowledgement rather than a railway event, so it is bounded - `waitForPowerState` says why
     * at length.  Generous, because what this waits out is a busy machine.
     */
    private static final long POWER_PATIENCE_MS = 15000;

    /**
     * A THROWAWAY COPY OF THE LAYOUT, opened before the model is built (OB-111).
     *
     * `init` reads the layout preference and loads whatever it names, which on Adam's machine is his
     * real railway.  This class builds its own two-point layout and has no business reading that one.
     */
    private static support.LayoutSandbox sandbox;

    /** The turnout the PATH configures, so autonomy holds it while the train is out. */
    private static final int SWITCH_HELD = 94;

    /** The turnout no edge mentions, which nothing is standing on. */
    private static final int SWITCH_CLEAR = 95;

    /** This class's own sensor. */
    private static final String S88 = "48501";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, true);

        // So that a command comes back as an echo and the model's own power state follows it.
        //
        // Without this, `stop()` sends and nothing answers, so `getPowerState()` never changes and the
        // stop assertion below is dead - it would report the power still on however well the route
        // worked.  `testARouteDoesNotThrowSwitchesUnderATrain` sets up the same way and says why.
        model.setNetworkCommState(false);

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null)
        {
            model.clearAutoLayout();
            model.stop();
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * The defect: a switch on clear track was dropped because another switch was under a train.
     *
     * **Both orders, in one test.**  The group skip is decided before the loop starts, so it drops
     * the clear switch whether that command comes first or second - and a per-command rule has to get
     * both right for the same reason.  Asserting only one order would leave a fix that merely moved
     * the boundary looking green.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheClearSwitchIsStillThrown() throws Exception
    {
        Outcome heldFirst = fire(84921, true);

        assertTrue(heldFirst.clearWasSwitched,
            "a switch on track nothing is standing on was not thrown, because a DIFFERENT switch in"
            + " the same route is under a train. Adam: \"don't run the conflicting switch commands,"
            + " but do run the power off and others\" - this one is not a conflicting switch command");

        Outcome clearFirst = fire(84922, false);

        assertTrue(clearFirst.clearWasSwitched,
            "the same switch, commanded before the conflicting one rather than after it, was still"
            + " dropped - so the refusal is being decided for the whole route rather than per"
            + " command");
    }

    /**
     * The control: the switch that IS under the train is still refused.
     *
     * Without this the test above passes on a route that sets everything, which is the original
     * AU-A2 defect back again and the one thing that must not happen.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheSwitchUnderTheTrainIsStillRefused() throws Exception
    {
        Outcome heldFirst = fire(84923, true);

        assertFalse(heldFirst.heldWasSwitched,
            "the turnout the path had configured and locked was thrown by an s88-triggered route"
            + " while the train was crossing it. That is AU-A2 itself, and it is the reason this"
            + " guard exists at all");

        Outcome clearFirst = fire(84924, false);

        assertFalse(clearFirst.heldWasSwitched,
            "the same turnout was thrown when the route commanded it second, so whatever refuses it"
            + " depends on the order the commands happen to be in");
    }

    /**
     * And the rest of the route runs, which is the half Adam names explicitly.
     *
     * **"do run the power off and others."**  The emergency stop is the command whose loss costs
     * most, and the existing suite already pins that it survives a refusal - this asserts it in the
     * same route as a refused switch AND a clear one, which is the shape a real safety route has.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testThePowerStillGoesOff() throws Exception
    {
        Outcome outcome = fire(84925, true);

        assertFalse(outcome.powerStillOn,
            "a route carrying an emergency stop did not cut the power because one of its switches was"
            + " under a train. The switch is worth refusing; the stop is not");
    }

    // ---------------------------------------------------------------- the fixture

    /** What one firing of the route did. */
    private static final class Outcome
    {
        private boolean heldWasSwitched;
        private boolean clearWasSwitched;
        private boolean powerStillOn = true;
    }

    /**
     * Locks a path, fires a three-command route at the automatic door, and reports what happened.
     *
     * The route is [held switch, clear switch, stop] or [clear switch, held switch, stop], which is
     * the shape of a real safety route: some ironwork and a power cut.
     *
     * @param routeId a route id nothing else uses
     * @param heldFirst whether the conflicting command comes before the clear one
     * @return what the railway looked like once the route had run
     * @throws Exception on a failure to build the fixture
     */
    private Outcome fire(int routeId, boolean heldFirst) throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("CS_A", false, null);
        layout.createPoint("CS_B", true, S88);

        Edge ab = layout.createEdge("CS_A", "CS_B");

        MarklinAccessory held =
            model.newSwitch(SWITCH_HELD, Accessory.accessoryDecoderType.MM2, false);

        MarklinAccessory clear =
            model.newSwitch(SWITCH_CLEAR, Accessory.accessoryDecoderType.MM2, false);

        held.setSwitched(false);
        clear.setSwitched(false);

        // Only the first is on the path, so only the first is one autonomy holds. The second is the
        // control built into the fixture: if the guard refused it too, it is not asking about trains.
        ab.addConfigCommand(held.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("CS_A").setLocomotive(loc);

        model.go();

        // WAITED FOR, NOT READ AT ONCE (2026-09-09).
        //
        // `go()` SENDS a command and returns; the power flag is written by the ECHO, which
        // `receiveMessage` hands to `locMessageProcessor` - so it is set on another thread, some time
        // after the call, and a read taken here has no happens-before with that write at all.  On an
        // idle machine the echo always landed first and this line was a formality; in a battery it did
        // not, and the class was green run alone and red in the suite.
        //
        // WHAT TURNS THE POWER OFF IS THIS CLASS, in the method before: a route carrying an emergency
        // stop, restored by a `model.go()` in a `finally` which is asynchronous in exactly the same
        // way.  So the state this precondition is about is established by this class and read before
        // it has arrived - not left behind by anything else.  Every test class runs in its own JVM,
        // and `init(simulate)` turns the power on, so no earlier class can be reaching this one.
        //
        // `waitForPowerState` is the railway's own wait for precisely this: it waits on the monitor
        // `setPowerState` notifies, inside it, and it is BOUNDED because an acknowledgement of a
        // command TrainControl itself just sent either comes back in milliseconds or is not coming.
        assertTrue(model.waitForPowerState(true, POWER_PATIENCE_MS),
            "precondition: the power has to be ON, or the route's stop has nothing to turn off");

        final Outcome outcome = new Outcome();

        layout.setCallback("conflict grain probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            List<RouteCommand> commands = new ArrayList<>();

            RouteCommand heldCommand = RouteCommand.RouteCommandAccessory(SWITCH_HELD,
                Accessory.accessoryDecoderType.MM2, true);

            RouteCommand clearCommand = RouteCommand.RouteCommandAccessory(SWITCH_CLEAR,
                Accessory.accessoryDecoderType.MM2, true);

            commands.add(heldFirst ? heldCommand : clearCommand);
            commands.add(heldFirst ? clearCommand : heldCommand);
            commands.add(RouteCommand.RouteCommandStop());

            // FIRED AUTOMATICALLY, which is the door this rule is about: a sensor fires it and
            // whatever it decides is what the railway does. Passing false here would switch the whole
            // rule off and the assertions would run down a branch that cannot refuse anything.
            new MarklinRoute(model, "CS grain " + routeId, routeId, commands, 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null).execRoute(true);

            try
            {
                settle();
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            outcome.heldWasSwitched = held.isSwitched();
            outcome.clearWasSwitched = clear.isSwitched();

            // THE SAME WAIT, THE OTHER WAY ROUND.  The stop's echo is delivered on the same worker
            // as the go's, so `settle`'s fixed 600ms is a guess about how long that thread takes to
            // get to it - and a sample taken too early reports a route that DID cut the power as one
            // that did not.  That is this class's own precondition flake, mirrored: bounded, so a
            // route that really never cut the power still fails, only more slowly.
            try
            {
                model.waitForPowerState(false, POWER_PATIENCE_MS);
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            outcome.powerStillOn = model.getPowerState();

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");
        }
        finally
        {
            model.go();
            model.clearAutoLayout();
        }

        return outcome;
    }

    /**
     * Waits out the thread execRoute starts and does not join.
     *
     * @throws InterruptedException if the wait is interrupted
     */
    private static void settle() throws InterruptedException
    {
        Thread.sleep(600);
    }
}
