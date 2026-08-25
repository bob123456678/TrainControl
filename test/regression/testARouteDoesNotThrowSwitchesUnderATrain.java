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

    /** The middle sensors of the three-leg path, so the train has somewhere to be part way along. */
    private static final String S88_MID = "48402";

    private static final String S88_MID2 = "48403";

    /** A second accessory, so "behind" and "ahead" can be told apart at the same instant. */
    private static final int SWITCH_AHEAD = 85;

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
     * An accessory the train has already gone past is still settable; one ahead of it is not.
     *
     * Adam, 2026-08-25, on the guard the rest of this class is about: "be careful with auto disallowed
     * routes to avoid regression.  once a train passes, signals on the route, but behind the train,
     * should still be allowed to be changed by auto routes, for example."
     *
     * He was right, and the first version was over-strict for exactly the reason he suspected.  The
     * guard asked whether the edge's LOCK was still held - and with `atomicRoutes` on, which is what
     * his own configuration uses, the lock is held for the whole path until the run ends, by design.
     * So every accessory on a long run was refused for the whole of it, including the ones the train
     * cleared in the first thirty seconds.  On a railway with 39 s88-triggered routes that is a lot of
     * silent refusals.
     *
     * Locking and clearance are different questions.  The lock asks "may another train be routed
     * here", and atomic means no for the whole run.  This asks "is there a train on top of this", and
     * the railway already computed the answer - it is what decides when an edge may be released in
     * non-atomic mode.  It simply was not computed when nothing was going to be released.  Now it is,
     * in both modes, by the same code, so the two cannot drift apart.
     *
     * **Position is not the test; the tail is.**  An edge is only cleared once the train's LENGTH has
     * gone past it, so a turnout under the middle of a train stays refused.
     *
     * Two accessories, checked at the SAME moment, so this cannot pass by the guard being uniformly
     * off: at the instant the train has crossed the first leg, that leg's turnout must be free and the
     * last leg's must not.
     *
     * **Three legs, not two, and that is a fact about the railway rather than about this test.** An
     * edge is only released once the train has reached the end of the edge AFTER it - "unlock 1 edge
     * prior to the current one", as the dispatch loop puts it, so that clearance always trails the
     * train by a whole edge. Clearance is now computed by that same code, so it inherits the same
     * conservatism: on a two-edge path the first edge is never cleared mid-run, because there is no
     * third leg to reach. That is the existing trade for unlocking and it stays exactly as it was.
     *
     * For the same reason the assertions read the FIRST and LAST observation rather than a numbered
     * leg: the dispatch loop fires its progress callback before it does the release, so the clearance
     * becomes visible one leg after the train crossed the edge. Pinning this test to leg two would
     * make it a test of that ordering rather than of the guard.
     *
     * MUTATION: making `getActiveAccs` ignore `clearedEdges` again - the state this test was written
     * for - fails it on the behind-the-train half while the ahead-of-the-train half still passes.
     */
    @Test
    public void testAnAccessoryBehindTheTrainIsStillSettable() throws Exception
    {
        for (String s : new String[] {S88, S88_MID, S88_MID2})
        {
            if (!model.isFeedbackSet(s)) model.newFeedback(Integer.parseInt(s), null);

            model.setFeedbackState(s, false);
        }

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        // The setting Adam runs, and the one the old guard could not see past.
        layout.setAtomicRoutes(true);

        layout.createPoint("BH_A", false, null);
        layout.createPoint("BH_B", false, S88_MID);
        layout.createPoint("BH_C", false, S88_MID2);
        layout.createPoint("BH_D", true, S88);

        Edge ab = layout.createEdge("BH_A", "BH_B");
        Edge bc = layout.createEdge("BH_B", "BH_C");
        Edge cd = layout.createEdge("BH_C", "BH_D");

        MarklinAccessory behind =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        MarklinAccessory ahead =
            model.newSwitch(SWITCH_AHEAD, Accessory.accessoryDecoderType.MM2, false);

        behind.setSwitched(false);
        ahead.setSwitched(false);

        ab.addConfigCommand(behind.getName(), Accessory.accessorySetting.STRAIGHT);
        cd.addConfigCommand(ahead.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("BH_A").setLocomotive(loc);

        // Accumulated per leg, never overwritten - the started callback fires once per leg, and a
        // single variable here would let the last leg speak for all of them.  That mistake is recorded
        // at length on the first test in this class.
        final List<Boolean> behindHeld = new ArrayList<>();
        final List<Boolean> aheadHeld = new ArrayList<>();

        layout.setCallback("behind probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            behindHeld.add(layout.getActiveAccs().contains(behind));
            aheadHeld.add(layout.getActiveAccs().contains(ahead));

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab, bc, cd), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertTrue(behindHeld.size() >= 3,
                "the run reported fewer than three legs, so there was never a moment with one leg "
                + "behind the train and one ahead - which is the only moment this test is about.  "
                + "Got " + behindHeld.size());

            int last = behindHeld.size() - 1;

            // The train has only just left: everything on the path is still in front of it.
            assertTrue(behindHeld.get(0),
                "precondition: when the train sets off the path must own the first turnout, or the "
                + "guard has nothing to find and this test would pass for the wrong reason");

            assertTrue(aheadHeld.get(0),
                "precondition: when the train sets off the last turnout must be held too");

            // The train has finished with the first leg, and has still to cross the last.  BOTH of
            // these are read from the same observation, so neither a guard that is uniformly on nor
            // one that is uniformly off can satisfy them together.
            assertFalse(behindHeld.get(last),
                "a turnout the train had already gone past was still refused.  With atomicRoutes on "
                + "the lock is held for the whole run by design, so a guard that only reads the lock "
                + "refuses every accessory on the path for the whole run - which is the regression "
                + "Adam predicted");

            assertTrue(aheadHeld.get(last),
                "the turnout the train had NOT yet reached stopped being protected.  That is the "
                + "original bug back again, and it is the half that matters: this must not be a "
                + "guard that simply lets everything through once a run is under way");
        }
        finally
        {
            layout.setAtomicRoutes(false);
            model.clearAutoLayout();
        }
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
