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

    /** The protecting signal, which nothing else in this class touches. */
    private static final int SIGNAL_ADDRESS = 86;

    /** The protected platform's own sensor - a destination must have one. */
    private static final String S88_PLATFORM = "48404";

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

    @AfterClass(alwaysRun = true)
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
     * A route already under way when a dispatch begins does not set the accessory either.
     *
     * Found by an independent review, which reproduced it with a timestamped log: the route committed
     * at 24.209, autonomy configured and locked the turnout at 24.711, and the route set it against
     * the locked path at 26.764 - with a poller confirming `getActiveAccs()` contained it the whole
     * time. No refusal and no log line.
     *
     * The guard was asked ONCE, before the command loop. And that loop takes seconds: `execRoute`
     * sleeps `SLEEP_INTERVAL` plus each command's own delay between every pair of commands. So a
     * dispatch that locked a path while a route was part way through was invisible to it, and AU-A2 -
     * "the train is routed off the path that was protecting it" - survived in a window seconds wide,
     * through all three doors including the s88 trigger with nobody present. Adam's railway has 39
     * s88-triggered routes, many of them multi-command.
     *
     * **What this costs, said plainly.** The guard is now asked again immediately before each
     * accessory command, so a route can be stopped part way through with some of its ironwork set and
     * the rest not. That is a real cost. It is the smaller one: the alternative is throwing a switch
     * under a train that is crossing it. Once the answer is settled it holds for the rest of the
     * route, so it does not go on flipping between the two states as conditions change under it.
     *
     * **Which branch this covers.** Adam ruled that a conflict appearing mid-route should ASK, at the
     * two doors with a person at them, and set none of its accessories at the s88 trigger door -
     * which is not the same as stopping, and this said stopping (VD9-C18). This test runs
     * with no interface attached - `init` is called with `showUI` false, so `getGUI()` is null - so it
     * exercises the branch with nobody to ask, which is the one that must never move the switch. It is
     * also exactly what the s88 door does.
     *
     * The other branch, where somebody says yes, is not reachable from here: the model's View is final
     * and set at construction, so a stub cannot be injected into the shared fixture. Its model half is
     * already covered by testSomebodyCanRunTheRouteAnyway, which drives the same override this sets;
     * the dialog itself is MT-189.
     *
     * The window is opened deliberately here with a command delay, rather than raced: the first
     * command carries a delay long enough for the dispatch to lock the path while the route sits
     * between its two commands.
     *
     * MUTATION: asking the guard only once - removing the `heldReason(rc)` re-check from the loop -
     * fails this test.
     */
    @Test
    public void testARouteAlreadyRunningDoesNotThrowTheSwitchEither() throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("MF_A", false, null);
        layout.createPoint("MF_B", true, S88);

        Edge ab = layout.createEdge("MF_A", "MF_B");

        MarklinAccessory turnout =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        turnout.setSwitched(false);

        // The path owns it and sets it STRAIGHT when it locks.
        ab.addConfigCommand(turnout.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("MF_A").setLocomotive(loc);

        // Two commands: a harmless one that holds the route open, then the one that would throw the
        // turnout.  The delay on the first is the window.
        List<RouteCommand> commands = new ArrayList<>();

        RouteCommand waits = RouteCommand.RouteCommandAccessory(SWITCH_AHEAD,
            Accessory.accessoryDecoderType.MM2, true);

        waits.setDelay(2500);

        commands.add(waits);
        commands.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS,
            Accessory.accessoryDecoderType.MM2, true));

        MarklinRoute midFlight = new MarklinRoute(model, "RG midflight", 84931, commands, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        // Started BEFORE anything is locked, so the up-front check sees a clear railway and commits -
        // which is the precondition, and the whole point.
        assertNull(midFlight.conflictingAccessory(),
            "precondition: nothing is locked yet, so the route must look safe when it starts. If it "
            + "does not, this test is exercising the up-front check rather than the window after it");

        // The dispatch is held OPEN while the route runs, rather than the two being raced.
        //
        // execRoute starts a thread of its own and returns at once - the first version of this test
        // wrapped it in another thread and joined that, which returned immediately and let every
        // assertion run before the route had reached its second command. It passed with the guard
        // deleted, which is how it was found.
        //
        // So the path is locked and then held for longer than the route's first delay, from inside
        // the started callback. The route's second command therefore lands while the lock is
        // definitely held, with no dependence on how long a dispatch happens to take.
        layout.setCallback("hold the path", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            try
            {
                Thread.sleep(4000);
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            return null;
        });

        midFlight.execRoute(false);

        // Let it issue its first command and enter the delay before the path is taken.
        Thread.sleep(400);

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertFalse(turnout.isSwitched(),
                "a route that was already running when the dispatch began went on to throw a turnout "
                + "the path had just configured and validated. The guard was asked once, before the "
                + "command loop, and that loop takes seconds - so the train is routed off the path "
                + "that was protecting it, which is the defect the up-front check was added to stop");
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    /**
     * A route may set a protecting signal RED over an occupied platform. It may not set it GREEN.
     *
     * The guard has two halves and they are not the same rule. A turnout on a locked path must not
     * move at all - any position but the one the path configured is wrong for the train crossing it.
     * A protecting signal is different: the only harmful command is the one that turns protection OFF.
     *
     * The first version asked neither. It refused any route touching any protecting signal of any
     * platform with a train standing at it, whether or not a path was locked anywhere and whichever
     * aspect the route was asking for - and because accessories are skipped as a GROUP, one such
     * signal took every turnout in the route with it. Found by review, which reproduced it with
     * nothing locked: `getActiveAccs` was empty and `conflictingAccessory` was not null.
     *
     * That is the over-strict guard Adam said he would rather not have at all, and it is broad enough
     * to stop most of a layout's routes working while trains sit at platforms - which is all the time.
     *
     * The signal here is on a Point that is NOT on the dispatched path, so the accessory half of the
     * guard cannot see it and this tests the protection half alone.
     *
     * MUTATION: dropping the `!rc.getSetting()` test - so the aspect stops mattering, as it did -
     * fails this test on the RED half while the GREEN half still passes.  Returning the locked-path
     * key from the protecting-signal branch of `heldReason` fails the last assertion, which is the one
     * that decides what sentence the operator is shown - and it fails it on every run, because that
     * assertion's guard is about whether the signal is on an active path, which under the mutation it
     * still is not.
     */
    @Test
    public void testASignalMayBeSetREDOverAnOccupiedPlatform() throws Exception
    {
        for (String s : new String[] {S88, S88_PLATFORM})
        {
            if (!model.isFeedbackSet(s)) model.newFeedback(Integer.parseInt(s), null);

            model.setFeedbackState(s, false);
        }

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("PS_A", false, null);
        layout.createPoint("PS_B", true, S88);

        // The platform, off to one side: nothing is routed over it, so nothing about it can reach
        // getActiveAccs.  Only the protection half of the guard can see this signal.
        layout.createPoint("PS_PLATFORM", true, S88_PLATFORM);

        Edge ab = layout.createEdge("PS_A", "PS_B");

        MarklinAccessory signal =
            model.newSwitch(SIGNAL_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        signal.setState(Accessory.accessorySetting.GREEN);

        layout.getPoint("PS_PLATFORM").setProtectingSignal(signal.getName());

        Locomotive running = model.getLocByName(model.getLocList().get(0));
        Locomotive parked = model.getLocByName(model.getLocList().get(1));

        layout.getPoint("PS_A").setLocomotive(running);
        layout.getPoint("PS_PLATFORM").setLocomotive(parked);

        final List<String> redRefusals = new ArrayList<>();
        final List<String> greenRefusals = new ArrayList<>();
        final List<String> greenReasons = new ArrayList<>();
        final List<Boolean> signalWasActive = new ArrayList<>();
        final List<String> diagnosis = new ArrayList<>();
        final List<Boolean> redActuallySet = new ArrayList<>();

        layout.setCallback("aspect probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            // true is RED / TURN, false is GREEN / STRAIGHT - see Accessory.
            MarklinRoute toRed = signalRoute(84921, true);
            MarklinRoute toGreen = signalRoute(84922, false);

            redRefusals.add(toRed.conflictingAccessory());

            // ONE query, both halves read off it.
            //
            // This asked toGreen twice - once for the name, once for the reason - and asserted on the
            // second answer, which need not be about the same instant as the first.  The railway is
            // moving underneath these calls; that is the whole point of running the probe from a
            // dispatch callback.
            String[] why = toGreen.conflictingAccessoryAndReason();

            greenRefusals.add(why == null ? null : why[0]);
            greenReasons.add(why == null ? null : why[1]);

            // And whether the PRECONDITION for the reason assertion held, sampled here rather than
            // assumed down there.
            //
            // The protecting-signal reason is only the right answer while the signal is not among the
            // accessories of an active path - and `getActiveAccs` is computed from a map the dispatch
            // is writing to as this runs.  A battery run at 01:46 saw it empty for the RED query and
            // non-empty for the GREEN one, a few microseconds apart, which is the only way that run's
            // two answers can both be true.
            signalWasActive.add(layout.getActiveAccs().contains(signal));

            // What the two sides of that comparison actually were.
            //
            // heldReason does not ask about THIS object: it resolves the route command's address and
            // protocol through getAccessoryByAddressIfPresent, and getActiveAccs fills itself through
            // getAccessoryByName. Three doors to one signal, and this suite runs against the real
            // locomotive database, where address 86 may already be spoken for.
            StringBuilder said = new StringBuilder();

            said.append("active=[");

            for (org.traincontrol.base.Accessory one : layout.getActiveAccs())
            {
                said.append(one.getName()).append("#")
                    .append(Integer.toHexString(System.identityHashCode(one))).append(" ");
            }

            said.append("] signal=").append(signal.getName()).append("#")
                .append(Integer.toHexString(System.identityHashCode(signal)));

            org.traincontrol.marklin.MarklinAccessory byName =
                model.getAccessoryByName(signal.getName());

            said.append(" byName=").append(byName == null ? "null"
                : byName.getName() + "#" + Integer.toHexString(System.identityHashCode(byName)));

            diagnosis.add(said.toString());

            signal.setState(Accessory.accessorySetting.GREEN);

            toRed.execRoute(false);

            try
            {
                settle();
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            redActuallySet.add(signal.isSwitched());

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc(running), 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertFalse(redRefusals.isEmpty(), "the probe never ran");

            assertNull(redRefusals.get(0),
                "a route was refused for setting a protecting signal RED - which is exactly what "
                + "protection itself would command. Nothing is made less safe by it, and refusing it "
                + "silently drops every other accessory in the route as well");

            assertTrue(redActuallySet.get(0),
                "the route was not refused but the signal did not go red either, so the assertion "
                + "above is passing on a route that did nothing");

            // The control, and the half that must not be lost: turning protection OFF is the hazard
            // this guard was added for.
            assertEquals(greenRefusals.get(0), signal.getName(),
                "a route was allowed to turn a platform's protecting signal GREEN with a train "
                + "standing at it. Nothing re-asserts it until the next occupancy change, so that is "
                + "a green aspect inviting a hand-driven train into an occupied platform");

            // And WHICH refusal it is, which is what the operator gets asked about.
            //
            // `heldReason` has always told the two apart and the log has said so since fb9b04b8, but
            // the reason stopped at this class: `conflictingAccessory` returned the name and dropped
            // it, so the confirmation dialog had one sentence for both and used the wrong one here -
            // "which is on track a train is running over right now", about a train standing still on a
            // platform nothing is routed over.
            //
            // An operator asked the wrong question answers the wrong question. Told a train is running
            // over the signal, the careful answer is Cancel and a route that was safe to force is lost;
            // told it often enough about parked trains, the answer becomes a reflex, and one day the
            // train really is moving.
            // Asserted only while the fixture is actually saying what it means to say.
            //
            // If the signal HAS become one of the active path's accessories, the locked-path reason is
            // the correct answer and asserting the other one would be asserting a bug. That is not the
            // case this test was written for and it is not a failure - it is the probe having run a
            // moment later than intended, which cannot be prevented from here.
            if (Boolean.TRUE.equals(signalWasActive.get(0)))
            {
                System.out.println("testASignalMayBeSetREDOverAnOccupiedPlatform: the signal was on "
                    + "an active path when the probe ran, so the reason assertion does not apply to "
                    + "this run. The refusal itself was still checked.");
            }
            else
            {
                assertEquals(greenReasons.get(0), "route.refusedSignalProtectingOccupiedPlatform",
                    "the refusal came back under the LOCKED PATH reason while the signal was NOT on "
                    + "an active path - so there is nothing for that reason to be about, and it is "
                    + "the sentence the dialog shows the operator: \"a train is running over it\", "
                    + "about a train standing still. What the probe saw: " + diagnosis.get(0));
            }
        }
        finally
        {
            layout.getPoint("PS_A").setLocomotive(null);
            layout.getPoint("PS_PLATFORM").setLocomotive(null);

            signal.setState(Accessory.accessorySetting.GREEN);

            model.clearAutoLayout();
        }
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
     * `TrainControlUI.askAboutRouteConflict`, and it is one method for both doors so they cannot
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

            // FIRED AUTOMATICALLY, which is the door this rule is about.
            //
            // `skipAccessories = auto && conflict != null` - so with `auto` false the whole rule is
            // switched off and this assertion ran down a branch where nothing could be discarded. A
            // reviewer checked the whole corpus: every execRoute in the suite passed false, so the
            // safety behaviour being asserted here - a route carrying an emergency stop is not thrown
            // away whole - was never actually exercised. Restoring the original defect at the
            // automatic door left the battery green.
            //
            // This is the door with no human at it: a sensor fires it, and whatever it decides is what
            // the railway does.
            new MarklinRoute(model, "MX emergency", 84903, commands, 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null).execRoute(true);

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
     * A route that cuts the power is never held up by the conflict question.
     *
     * Adam, 2026-09-01: **"emergency stop should never conflict or prompt."**
     *
     * The test above proves the stop survives a refusal at the AUTOMATIC door, where nobody is asked.
     * At the two human doors it did not survive at all: `TrainControlUI` and `LayoutLabel` both ask
     * first and `return` on Cancel, so `execRoute` is never reached and a route that cuts power and
     * sets a trap point does neither.  Which of those two things the operator was declining is not even
     * a question the dialog asks - it names one accessory.
     *
     * So the question is not asked about such a route.  `conflictingAccessoryAndReason` is what every
     * door consults before acting, and it now answers "nothing to confirm" whenever the route carries a
     * stop: no dialog, no wait, no answer that can throw the stop away.  The conflicting accessory is
     * still skipped and still logged, exactly as at the s88 door - declining to throw a switch under a
     * moving train is right, and it was never the part in dispute.
     *
     * The second assertion is the control.  Without the stop the same route on the same conflict must
     * still raise the question, or this would be a rule that switched the guard off for everybody.
     */
    @Test
    public void testARouteThatCutsThePowerIsNeverHeldUpByTheQuestion() throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);
        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("XS_A", false, null);
        layout.createPoint("XS_B", true, S88);

        Edge ab = layout.createEdge("XS_A", "XS_B");

        MarklinAccessory turnout =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        turnout.setSwitched(false);

        ab.addConfigCommand(turnout.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("XS_A").setLocomotive(loc);

        final String[][] withStop = {null};
        final String[][] withoutStop = {null};
        final boolean[] flagged = {false};

        layout.setCallback("stop route probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            List<RouteCommand> both = new ArrayList<>();
            both.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS,
                Accessory.accessoryDecoderType.MM2, true));
            both.add(RouteCommand.RouteCommandStop());

            MarklinRoute carriesStop = new MarklinRoute(model, "XS with stop", 84921, both, 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            flagged[0] = carriesStop.hasEmergencyStop();

            withStop[0] = carriesStop.conflictingAccessoryAndReason();

            List<RouteCommand> accessoryOnly = new ArrayList<>();
            accessoryOnly.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS,
                Accessory.accessoryDecoderType.MM2, true));

            withoutStop[0] = new MarklinRoute(model, "XS without stop", 84922, accessoryOnly, 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null)
                .conflictingAccessoryAndReason();

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertTrue(flagged[0],
                "the route carrying RouteCommandStop is not recognised as carrying an emergency stop, "
                + "so nothing below can be about emergency stops");

            // THE CONTROL FIRST, so a rule that simply switched the guard off cannot pass unnoticed.
            assertNotNull(withoutStop[0],
                "the same accessory on the same locked path raised no question even without a stop in "
                + "the route - the conflict guard is off altogether, which is not what was asked for");

            assertNull(withStop[0],
                "a route that cuts the power still raises the conflict question, so the operator is "
                + "asked before an emergency stop can run - and answering Cancel throws the stop away "
                + "at both human doors, because they return before execRoute");
        }
        finally
        {
            model.go();
            model.clearAutoLayout();
        }
    }

    /**
     * Deleting a locomotive says how many routes drive it, before it takes their commands away.
     *
     * Adam, 2026-09-01, on the behaviour itself: "OK - let\u0027s add a warning about this."
     *
     * The behaviour is deliberate and stays: a command naming a locomotive that is not in the database
     * does nothing when the route fires, and leaving it makes the route look complete while it is not.
     * v2.8.1 kept those commands, so an operator who deletes a locomotive and adds it back under the
     * same name - which is how a decoder type gets changed - used to find their routes intact and now
     * does not.  Nobody was told, and the one log line the deletion writes is about CONDITIONS.
     *
     * **Commands only, and that is the half worth testing.**  A condition naming the locomotive is
     * deliberately left in place, so counting it would warn about something the route keeps.  The
     * second assertion is a route that only mentions the locomotive in a condition: it must not count.
     */
    @Test
    public void testDeletingALocomotiveCountsTheRoutesThatDriveIt() throws Exception
    {
        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        List<RouteCommand> drives = new ArrayList<>();
        drives.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS,
            Accessory.accessoryDecoderType.MM2, true));
        drives.add(RouteCommand.RouteCommandLocomotiveSpeed(loc.getName(), 20));

        MarklinRoute driving = new MarklinRoute(model, "XD drives", 84931, drives, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

        assertTrue(driving.commandsDrive(loc.getName()),
            "a route carrying a speed command for this locomotive does not report that it drives it, "
            + "so the warning would say nothing while the commands are deleted anyway");

        assertFalse(driving.commandsDrive("A locomotive with no route at all"),
            "the count answers yes for a locomotive the route never mentions - it is counting routes "
            + "rather than routes that name this one");

        // A ROUTE THAT ONLY MENTIONS IT IN A CONDITION KEEPS ITS COMMANDS, so it must not be counted.
        List<RouteCommand> accessoryOnly = new ArrayList<>();
        accessoryOnly.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS,
            Accessory.accessoryDecoderType.MM2, true));

        List<RouteCommand> namedInACondition = new ArrayList<>();
        namedInACondition.add(RouteCommand.RouteCommandLocomotiveSpeed(loc.getName(), 0));

        MarklinRoute conditionOnly = new MarklinRoute(model, "XD condition", 84932, accessoryOnly, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false,
            org.traincontrol.base.NodeExpression.fromList(namedInACondition));

        assertFalse(conditionOnly.commandsDrive(loc.getName()),
            "a route that names the locomotive only in a condition was counted, and its commands are "
            + "not the ones being deleted - the warning would overstate what is lost");
    }

    /**
     * And the confirmation dialog actually asks for that count.
     *
     * A count nothing consults is a count nobody reads.  Anchored on the call rather than on the key
     * alone, because the key existing in the bundles says only that somebody wrote a sentence.
     */
    @Test
    public void testTheDeleteDialogNamesTheRoutesThatWillLoseCommands() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(source.contains("r.commandsDrive(value)"),
            "the delete flow no longer counts the routes that drive this locomotive, so the "
            + "confirmation cannot say what deleting it will take away");

        assertTrue(source.contains("ui.confirmDeleteFromDatabaseWithRoutes"),
            "the delete confirmation no longer uses the message that names the route count, so the "
            + "operator is asked the old question and told nothing about their routes");
    }

    /**
     * An s88 route with nothing in its way still runs while autonomy is going (A102).
     *
     * **The automatic door was exercised exactly once in the whole suite, and always with a
     * conflict.** `testTheStopInARefusedRouteStillRuns` above fires `execRoute(true)` for a route
     * whose turnout IS on a locked path, so between them the tests say what that door refuses and
     * nothing says what it does when there is nothing to refuse.
     *
     * The rule is `skipAccessories = auto && conflict != null`. Drop the second half and every
     * s88-triggered route stops setting anything for as long as autonomy is running - which on this
     * railway is most of an evening, since sensors are shared and these routes were written for manual
     * operation. Nothing would fail, because the only test of that door expects its accessory to be
     * skipped.
     *
     * So this is the other side of the same `if`: a route on a DIFFERENT accessory, one no edge
     * configures, fired automatically while a train is running, must set it.
     *
     * MUTATION this catches: `skipAccessories = auto` fails this test and leaves the one above green.
     *
     * @throws Exception
     */
    @Test
    public void testAnAutomaticRouteWithNoConflictStillSetsItsAccessory() throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("MX_A", false, null);
        layout.createPoint("MX_B", true, S88);

        Edge ab = layout.createEdge("MX_A", "MX_B");

        // The one the PATH configures, so there is a locked accessory on the layout at all - without
        // it the guard has nothing to find and this passes for a railway with no autonomy on it.
        MarklinAccessory locked =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        locked.setSwitched(false);

        ab.addConfigCommand(locked.getName(), Accessory.accessorySetting.STRAIGHT);

        // And the one the ROUTE sets, which no edge mentions.
        final int freeAddress = SWITCH_ADDRESS + 7;

        MarklinAccessory free =
            model.newSwitch(freeAddress, Accessory.accessoryDecoderType.MM2, false);

        free.setSwitched(false);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("MX_A").setLocomotive(loc);

        model.go();

        final boolean[] freeAfter = {false};
        final boolean[] lockedAfter = {false};

        layout.setCallback("no conflict probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(freeAddress,
                Accessory.accessoryDecoderType.MM2, true));

            // FIRED AUTOMATICALLY, the door with nobody at it.
            new MarklinRoute(model, "MX free", 84907, commands, 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null).execRoute(true);

            try
            {
                settle();
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            if (free.isSwitched()) freeAfter[0] = true;
            if (locked.isSwitched()) lockedAfter[0] = true;

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertTrue(freeAfter[0],
                "an s88-triggered route whose accessory is on no locked path did not set it while "
                + "autonomy was running.  Every route written for manual operation stops working the "
                + "moment a train is out, and nothing says so");

            assertFalse(lockedAfter[0],
                "the accessory the PATH configured was moved, which means the fixture has no locked "
                + "accessory and the test above passed for a layout with nothing to conflict with");
        }
        finally
        {
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
     * A one-command route that sets the signal to a named aspect.
     *
     * @param id a route id nothing else uses
     * @param red true for RED, false for GREEN - the sense RouteCommand and Accessory both use
     * @return the route, not executed
     */
    private static MarklinRoute signalRoute(int id, boolean red)
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(SIGNAL_ADDRESS,
            Accessory.accessoryDecoderType.MM2, red));

        return new MarklinRoute(model, "RG signal " + id, id, commands, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);
    }

    /** The dispatched locomotive, by the name executePath wants. */
    private static Locomotive loc(Locomotive l)
    {
        return l;
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

    /**
     * A turnout under a MEASURED train stays refused, on a path that is only partly measured (WK-B1).
     *
     * The sibling of `testAnAccessoryBehindTheTrainIsStillSettable`, with the one thing that test does
     * not have: a train with a length. Without one every edge is handed back the moment the head
     * leaves it, which is the right answer on an unmeasured railway and says nothing about a measured
     * one.
     *
     * Edges of 100, 100 and 0 with a train of 250 - the example from the commit that introduced the
     * rule this tests. When the head finishes the unmeasured third edge, the first edge has 100 units
     * of measured distance behind it and 150 units of train still standing on it.
     *
     * A looser rule released it there, on the reasoning that nothing better could be known over
     * unmeasured track. Nothing better can be known, which is exactly why the answer must be that the
     * train is still there: `getActiveAccs` skips cleared edges, and with atomicRoutes on the lock is
     * held for the whole run by design - so being reported clear is the ONLY thing that stops
     * `heldReason` refusing a route that would throw that turnout.
     *
     * MUTATION: restoring the escape - release when the last edge traversed had no length - fails the
     * behind-the-train assertion while every other test in this class still passes.
     */
    @Test
    public void testAMeasuredTrainKeepsItsTurnoutsRefused() throws Exception
    {
        for (String s : new String[] {S88, S88_MID, S88_MID2})
        {
            if (!model.isFeedbackSet(s)) model.newFeedback(Integer.parseInt(s), null);

            model.setFeedbackState(s, false);
        }

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);
        layout.setAtomicRoutes(true);

        layout.createPoint("ML_A", false, null);
        layout.createPoint("ML_B", false, S88_MID);
        layout.createPoint("ML_C", false, S88_MID2);
        layout.createPoint("ML_D", true, S88);

        Edge ab = layout.createEdge("ML_A", "ML_B");
        Edge bc = layout.createEdge("ML_B", "ML_C");
        Edge cd = layout.createEdge("ML_C", "ML_D");

        // Measured, measured, unmeasured - a railway with lengths on some of its track and not the
        // rest, which is the one this is about.
        ab.setLength(100);
        bc.setLength(100);
        cd.setLength(0);

        MarklinAccessory behind =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        MarklinAccessory ahead =
            model.newSwitch(SWITCH_AHEAD, Accessory.accessoryDecoderType.MM2, false);

        behind.setSwitched(false);
        ahead.setSwitched(false);

        ab.addConfigCommand(behind.getName(), Accessory.accessorySetting.STRAIGHT);
        cd.addConfigCommand(ahead.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        Integer wasLength = loc.getTrainLength();

        // Longer than the measured distance the run can accumulate behind the first edge.
        loc.setTrainLength(250);

        layout.getPoint("ML_A").setLocomotive(loc);

        final List<Boolean> behindHeld = new ArrayList<>();
        final List<Boolean> aheadHeld = new ArrayList<>();

        layout.setCallback("measured probe", (edges, l, started) ->
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
                + "behind the train and one ahead.  Got " + behindHeld.size());

            int last = behindHeld.size() - 1;

            // PRECONDITIONS, so this cannot pass for the wrong reason.
            assertTrue(behindHeld.get(0),
                "precondition: when the train sets off the path must own the first turnout");

            assertEquals(loc.getTrainLength(), Integer.valueOf(250),
                "precondition: the train length did not survive to the run, so the rule compared "
                + "against nothing and this test proves nothing");

            // THE FINDING. 100 measured units behind, 250 of train.
            // WHAT IT ACTUALLY SAW, because this failed once inside a full battery and passed alone,
            // and the message could not tell anybody why. A failure nobody can reproduce gets deleted
            // rather than fixed.
            String seen = " [observed: " + behindHeld.size() + " legs, ab=" + ab.getLength()
                + " bc=" + bc.getLength() + " cd=" + cd.getLength()
                + " trainLength=" + loc.getTrainLength() + " held=" + behindHeld + "]";

            assertTrue(ab.getLength() == 100 && bc.getLength() == 100,
                "the measured lengths are not on the edges at run time, so the rule saw an unmeasured "
                + "path and released everything - this test would then be vacuous rather than wrong"
                + seen);

            assertTrue(behindHeld.get(last),
                "the turnout on the first edge stopped being refused while 150 units of a 250 train "
                + "were still standing on it.  The head had run on over unmeasured track, which says "
                + "nothing about where the tail is - and with atomicRoutes on, an edge being reported "
                + "clear is the only thing that stops a route throwing that turnout (WK-B1)" + seen);

            assertTrue(aheadHeld.get(last),
                "the turnout the train had not yet reached stopped being protected");
        }
        finally
        {
            loc.setTrainLength(wasLength);
            layout.setAtomicRoutes(false);
            model.clearAutoLayout();
        }
    }
}
