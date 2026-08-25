package regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
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
 * A route will not switch an accessory that autonomy has locked.
 *
 * AU-A2, found by an independent review of the whole application and proved by running it.
 *
 * Route execution and autonomy path locking each worked exactly as designed, and neither consulted the
 * other. `configureAndLockPath` reserves every accessory on a path, commands it and validates it - and
 * a route then set the same accessory back, with no refusal and nothing said. The train is routed off
 * the path that was protecting it.
 *
 * **Three doors reached it, and the automatic one is why this is not merely a nuisance.** An s88
 * trigger route left over from manual operation fires when an AUTONOMY train crosses the trigger
 * sensor - sensors are shared and reused on this railway, which is its own recorded lesson - so no
 * person is involved at any point. The routes tab is manual and silent. And the diagram's route tile
 * looked guarded and was not: that guard asks `activeAccs.contains(c.getAccessory())`, and a route
 * component's accessory is null, so the one door that appeared to check was checking nothing.
 *
 * **Its own class on purpose.** The guard can only see the layout the model holds, so the test has to
 * put its fixture there - and doing that inside a class that shares a model with twenty other tests
 * broke two of them. One JVM per class means this one can take the model apart and put it back without
 * anybody noticing.
 *
 * @author Adam
 */
public class testARouteDoesNotThrowSwitchesUnderATrain
{
    private static MarklinControlStation model;

    /** Nothing else in the suite uses this address, and a route by address must resolve to it. */
    private static final int SWITCH_ADDRESS = 84;

    private static final String S88 = "48401";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);

        // So that a command comes back as an echo and the model's own power state follows it.
        //
        // Without this, `stop()` sends and nothing answers, so `getPowerState()` never changes and the
        // test's oracle is dead - it would report the power still on however well the route worked.
        // testAutonomyPathValidation sets up the same way and says why.
        model.setNetworkCommState(false);

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (model != null)
        {
            model.clearAutoLayout();
            model.stop();
        }
    }

    /**
     * The accessory on a locked path stays where the path put it.
     *
     * The control at the end matters as much as the assertion: with autonomy not running, the same
     * route throws the same accessory. Nothing about ordinary route use changes, and a guard that
     * refused routes generally would satisfy the first assertion just as well.
     *
     * MUTATION: removing the `accessoryHeldByAutonomy()` refusal from `MarklinRoute.execRoute` fails
     * this test on its first assertion.
     */
    @Test
    public void testAnAccessoryOnALockedPathIsNotSwitchedByARoute() throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("RG_A", false, null);
        layout.createPoint("RG_B", true, S88);

        Edge ab = layout.createEdge("RG_A", "RG_B");

        MarklinAccessory turnout =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        turnout.setSwitched(false);

        // The path owns it, and sets it STRAIGHT when it locks.
        ab.addConfigCommand(turnout.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("RG_A").setLocomotive(loc);

        // Fired from INSIDE the dispatch, which is the only moment the hazard exists.
        //
        // Locking a path is not enough on its own: `activeLocomotives` - which is what makes
        // isRunning() true and what getActiveAccs reads - is written by executePathInternal AFTER the
        // lock succeeds. That gap is itself a recorded finding (UR-2). So the route is run from the
        // started callback, which is exactly where a real s88 trigger route would fire: with the path
        // locked, the train registered, and the accessories reserved.
        // ACCUMULATED, not overwritten.
        //
        // The started callback fires once per leg, and the first version of this recorded its verdict
        // into a variable the second call then overwrote - by which time the accessory had already been
        // thrown, so "unchanged since I last looked" was true and the test passed with the guard
        // deleted. Found by running the mutation the javadoc claims, which is the only reason it was
        // found at all.
        final boolean[] sawTheLock = {false};
        final boolean[] everSwitched = {false};

        layout.setCallback("route probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            if (layout.getActiveAccs().contains(turnout)) sawTheLock[0] = true;

            boolean before = turnout.isSwitched();

            route(84901).execRoute(false);

            try
            {
                settle();
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            if (turnout.isSwitched() != before) everSwitched[0] = true;

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertTrue(sawTheLock[0],
                "precondition: while the train was under way the path must actually own this "
                + "accessory, or the guard has nothing to find and this test would pass for the "
                + "wrong reason");

            assertFalse(everSwitched[0],
                "a route switched an accessory on a path autonomy had locked, validated and was "
                + "running a train over.  Nothing refused it and nothing was said");
        }
        finally
        {
            model.clearAutoLayout();
        }

        // --- the control ---------------------------------------------------------------------------
        assertFalse(model.isAutonomyRunning(),
            "the control needs autonomy stopped, or it is not a control");

        turnout.setSwitched(false);

        route(84902).execRoute(false);

        settle();

        assertTrue(turnout.isSwitched(),
            "with autonomy not running the route did not switch the accessory either, so the guard is "
            + "refusing routes generally rather than refusing this one case");
    }

    /**
     * Somebody who says so can run the route anyway.
     *
     * Adam, 2026-08-25: "conflicting routes should still be executable in case of a transient
     * accessory failure.  Add a confirmation dialog to the UI similar to how individual clicks
     * currently work when an accessory has an active route."
     *
     * The case he is protecting is the reason a refusal on its own was wrong, and it is worth stating
     * because it is not obvious: **a turnout that did not take the command is exactly when somebody
     * needs to set it, and exactly when it will be on a locked path** - because the path is what
     * commanded it. A guard with no way past it takes the recovery away at the moment it is wanted.
     *
     * So the shape is: the s88 trigger door refuses, because nobody is there to ask; the two doors
     * with a person at them ask, the same way clicking an accessory on an active route has always
     * asked, and call this when the answer is yes.
     *
     * This tests the MODEL half - that saying yes really does set the accessory. The dialog itself is
     * `TrainControlUI.confirmRouteOverActivePath`, and it is one method for both doors so they cannot
     * drift; MT-189 checks it by hand.
     *
     * MUTATION: making `execRouteOverridingConflicts` pass `false` - so the override does nothing -
     * fails this test.
     */
    @Test
    public void testSomebodyCanRunTheRouteAnyway() throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("OV_A", false, null);
        layout.createPoint("OV_B", true, S88);

        Edge ab = layout.createEdge("OV_A", "OV_B");

        MarklinAccessory turnout =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        turnout.setSwitched(false);

        ab.addConfigCommand(turnout.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("OV_A").setLocomotive(loc);

        final boolean[] sawConflict = {false};
        final boolean[] switchedAfter = {false};

        layout.setCallback("override probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            MarklinRoute conflicting = route(84904);

            // The question the two UI doors ask before they show the dialog.
            if (conflicting.conflictingAccessory() != null) sawConflict[0] = true;

            conflicting.execRouteOverridingConflicts();

            try
            {
                settle();
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            if (turnout.isSwitched()) switchedAfter[0] = true;

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertTrue(sawConflict[0],
                "precondition: the route has to REPORT the conflict, or the UI would never put the "
                + "question up and there would be nothing to override");

            assertTrue(switchedAfter[0],
                "somebody said to run the route anyway and it still did not set the accessory.  That "
                + "takes away the recovery from a turnout that did not take its command - which is "
                + "the case the override exists for");
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    /**
     * A refused route still cuts the power, if that is what it also says.
     *
     * **The first version of the guard did not, and it is the worst thing this round produced.** The
     * refusal returned before the command loop, so every command in the route was discarded - and a
     * route that cuts the power AND sets a trap point, which is the shape a safety route on an s88
     * trigger naturally has, was refused entirely because of the turnout. The emergency stop did not
     * run, with nobody present, on the door that fires by itself.
     *
     * "Refused whole" is a good argument about accessories: setting three switches of five leaves the
     * layout in a state nobody chose. It is not an argument for suppressing a stop, which is safe to
     * obey whatever else is true. So the accessories go as a group and everything else runs.
     *
     * Found by the pass that validated the guard, which measured `getPowerState()` afterwards rather
     * than reasoning about it.
     *
     * MUTATION: making the refusal `return` instead of setting `skipAccessories` fails this test - the
     * power stays on.
     */
    @Test
    public void testTheStopInARefusedRouteStillRuns() throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("MX_A", false, null);
        layout.createPoint("MX_B", true, S88);

        Edge ab = layout.createEdge("MX_A", "MX_B");

        MarklinAccessory turnout =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        turnout.setSwitched(false);

        ab.addConfigCommand(turnout.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("MX_A").setLocomotive(loc);

        model.go();

        assertTrue(model.getPowerState(),
            "precondition: the power has to be ON, or the route's stop has nothing to turn off and "
            + "this test passes without exercising anything");

        final boolean[] powerAfter = {true};
        final boolean[] switchedAfter = {false};

        layout.setCallback("mixed route probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            // The route the whole finding is about: an emergency stop AND a locked accessory.
            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS,
                Accessory.accessoryDecoderType.MM2, true));
            commands.add(RouteCommand.RouteCommandStop());

            new MarklinRoute(model, "MX emergency", 84903, commands, 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null).execRoute(false);

            try
            {
                settle();
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            if (!model.getPowerState()) powerAfter[0] = false;

            if (turnout.isSwitched()) switchedAfter[0] = true;

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertFalse(powerAfter[0],
                "a route carrying an emergency stop was discarded whole because one of its switches "
                + "was on a locked path, so the power stayed on.  The switch is worth refusing; the "
                + "stop is not");

            assertFalse(switchedAfter[0],
                "and the accessory half must still be refused - otherwise this test would pass by the "
                + "guard doing nothing at all");
        }
        finally
        {
            model.go();
            model.clearAutoLayout();
        }
    }

    /**
     * A one-command route that sets the test's accessory.
     *
     * @param id a route id nothing else uses
     * @return the route, not executed
     */
    private static MarklinRoute route(int id)
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS,
            Accessory.accessoryDecoderType.MM2, true));

        return new MarklinRoute(model, "RG route " + id, id, commands, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);
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
