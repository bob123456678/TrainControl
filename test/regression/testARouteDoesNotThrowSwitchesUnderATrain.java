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
