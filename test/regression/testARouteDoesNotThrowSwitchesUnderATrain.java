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
import support.SourceOf;

/**
 * A route will not switch an accessory that autonomy has locked.
 *
 * `configureAndLockPath` reserves every accessory on a path; three doors reach a route (an s88 trigger fired by an
 * AUTONOMY train, the manual routes tab, the diagram's route tile), and none may set one back while a train is under
 * way on it.  Its own class: the test takes the model apart, so it gets its own JVM.
 *
 * @author Adam
 */
public class testARouteDoesNotThrowSwitchesUnderATrain
{
    private static MarklinControlStation model;

    /**
     * How long the power is given to come back on after a `go()`: an acknowledgment rather than a railway event, so
     * bounded (`waitForPowerState` says why), and generous because what this waits out is a busy machine.
     */
    private static final long POWER_PATIENCE_MS = 15000;

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
        model = support.TestStation.simulatedInDebug();

        // So that a command comes back as an echo and the model's own power state follows it: without this `stop()`
        // sends and nothing answers, so `getPowerState()` never changes and the oracle is dead
        // (testAutonomyPathValidation too).
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
     * A route that cuts the power is never asked about, at EITHER door (MT-247): `conflictingAccessoryAndReason`
     * (before the run) and the per-accessory check in `execRoute`'s loop must both excuse a route carrying a stop.
     * Checked as the agreement between the two doors, since a test of either alone passes when they disagree.
     *
     * MUTATION: take `!this.hasEmergencyStop()` out of the midway condition again and this fails.
     */
    @Test
    public void testBothDoorsCarveOutTheEmergencyStop() throws Exception
    {
        String screen = SourceOf.declaration("public String[] conflictingAccessoryAndReason()")
            .replaceAll("\\s+", " ");

        String midway = SourceOf.declaration("private void execRoute(boolean auto, int recursionLimit, boolean overrideConflicts)")
            .replaceAll("\\s+", " ");

        assertTrue(screen.contains("if (this.hasEmergencyStop()) return null;"),
            "the PRE-ROUTE screen no longer excuses a route that cuts the power, so such a route is "
            + "now screened before it starts - which is a change to Adam's 2026-09-01 ruling made "
            + "without saying so");

        assertTrue(midway.contains("final boolean askable = !auto && !this.hasEmergencyStop()"),
            "the MIDWAY question no longer excuses a route that cuts the power.  The pre-route screen "
            + "still does, so such a route is waved past it and asked here instead - and a Cancel "
            + "then discards a power cut the dialog never mentioned (DIR-A3)");

        // AND THE CONTROL: the s88 door is not what changed, and must not become a question.
        assertEquals(MarklinRoute.respondToConflict(false, false),
            MarklinRoute.ConflictResponse.SKIP_THIS_ACCESSORY,
            "the unattended door started cancelling, so an s88 route that cuts the power would stop "
            + "cutting it");
    }
    /**
     * What a route does about a conflicting accessory command depends on who is there (MT-247): declining cancels the
     * route; the s88 door asks nobody, skips the conflicting ironwork and runs the rest; a route carrying a stop is
     * not asked at all.  Run as the rule, since the manual branch needs a `View`; the call site is pinned below.
     */
    @Test
    public void testWhatAConflictDoesDependsOnWhoIsThere()
    {
        assertEquals(MarklinRoute.respondToConflict(true, true),
            MarklinRoute.ConflictResponse.RUN_EVERYTHING,
            "the operator said yes and the route did not run everything.  OK means the ironwork is "
            + "set and so is the rest (MT-247)");

        assertEquals(MarklinRoute.respondToConflict(true, false),
            MarklinRoute.ConflictResponse.CANCEL_ROUTE,
            "the operator said no and the route went on running its speeds, functions and chained "
            + "routes.  Somebody looking at the railway said no to THIS ROUTE; running most of it is "
            + "not what they said (MT-247)");

        // THE AUTO DOOR, unchanged and load-bearing: no dialog, the ironwork left alone, everything
        // else still run - which is how a route that cuts the power still cuts it.
        assertEquals(MarklinRoute.respondToConflict(false, false),
            MarklinRoute.ConflictResponse.SKIP_THIS_ACCESSORY,
            "an s88-fired route cancelled itself over a conflict.  Nobody is there to be asked, and "
            + "the emergency stop it may carry has to run (MT-247)");

        // AND THE ANSWER IS IGNORED WHEN THERE IS NOBODY TO GIVE IT, so a stray true cannot make the
        // unattended door behave like the attended one.
        assertEquals(MarklinRoute.respondToConflict(false, true),
            MarklinRoute.ConflictResponse.SKIP_THIS_ACCESSORY,
            "with nobody to ask, an answer of yes was acted on anyway");
    }

    /**
     * And `execRoute` actually asks that rule (MT-247).
     *
     * Naming a rule moves the defect to the call site, which is this repository's recurring shape.
     */
    @Test
    public void testTheRouteAsksThatRule() throws Exception
    {
        String source = SourceOf.declaration("private void execRoute(boolean auto, int recursionLimit, boolean overrideConflicts)")
            .replaceAll("\\s+", " ");

        assertTrue(source.contains("final ConflictResponse response = respondToConflict(askable,"),
            "execRoute no longer decides through respondToConflict, so the ruling is tested here and "
            + "something else decides what the railway does");

        assertTrue(source.contains("if (response == ConflictResponse.CANCEL_ROUTE) {")
                || source.contains("else if (response == ConflictResponse.CANCEL_ROUTE) {"),
            "execRoute no longer acts on CANCEL_ROUTE, so declining cancels nothing");
    }
    /**
     * The accessory on a locked path stays where the path put it.  The control at the end matters as much: with
     * autonomy not running, the same route throws the same accessory.
     *
     * MUTATION: removing the `accessoryHeldByAutonomy()` refusal from `MarklinRoute.execRoute` fails the first
     * assertion.
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

        // Fired from INSIDE the dispatch, the only moment the hazard exists: `activeLocomotives` (what isRunning() and
        // getActiveAccs read) is written by executePathInternal AFTER the lock succeeds.  So the route runs from the
        // started callback, where a real s88 trigger route fires.  The verdicts are ACCUMULATED, not overwritten: the
        // callback fires once per leg.
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

        // the control
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
     * A route may set an accessory on a locked path to the setting the path already holds it at (Adam: *"make sure the
     * code allows switching to the correct (same) setting that autonomy is using"*): that moves nothing under the
     * train, so it is neither refused nor asked about.  The control: a route setting the other way, while the same
     * train crosses, still is.
     *
     * MUTATION: refuse every command on a locked accessory again, whatever it sets, and this fails.
     */
    @Test
    public void testARouteMaySetTheSettingThePathAlreadyHolds() throws Exception
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

        // The path holds it STRAIGHT
        ab.addConfigCommand(turnout.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("RG_A").setLocomotive(loc);

        final boolean[] sawTheLock = {false};
        final String[] sameSetting = {"not asked"};
        final String[] otherSetting = {"not asked"};

        // Asked from inside the dispatch, with the path locked and the train registered - see
        // testAnAccessoryOnALockedPathIsNotSwitchedByARoute
        layout.setCallback("same setting probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started) || sawTheLock[0] || !layout.getActiveAccs().contains(turnout)) return null;

            sawTheLock[0] = true;

            String[] same = switchRoute(84951, false).conflictingAccessoryAndReason();
            String[] other = switchRoute(84952, true).conflictingAccessoryAndReason();

            sameSetting[0] = same == null ? null : Arrays.toString(same);
            otherSetting[0] = other == null ? null : Arrays.toString(other);

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertTrue(sawTheLock[0], "precondition: while the train was under way the path must own this accessory");

            assertNull(sameSetting[0], "a route setting the turnout STRAIGHT - the setting the locked path holds it at,"
                + " which moves nothing - was refused: " + sameSetting[0] + ".  Adam: \"make sure the code allows switching"
                + " to the correct (same) setting that autonomy is using\"");

            assertNotNull(otherSetting[0], "the control: a route setting the turnout TURN, against the path its train is"
                + " crossing, was not refused");
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    /**
     * A route may set a guard a locked path holds green to that green: the exit guard and home signal of the path's
     * platforms are held accessories too, and Adam's rule reaches them (*"make sure the code allows switching to the
     * correct (same) setting that autonomy is using"*).  The control: a guard no path holds is still refused green.
     *
     * MUTATION: ask the protecting-signal rule whatever the path holds, or let a held green through whatever the guard
     * shows, and this fails.
     */
    @Test
    public void testARouteMaySetAGuardTheGreenItsPathHolds() throws Exception
    {
        for (String s : new String[] {S88, S88_MID})
        {
            if (!model.isFeedbackSet(s)) model.newFeedback(Integer.parseInt(s), null);

            model.setFeedbackState(s, false);
        }

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("RG_A", true, S88_MID);
        layout.createPoint("RG_B", true, S88);

        Edge ab = layout.createEdge("RG_A", "RG_B");

        // the guard of the platform the train leaves, which its path holds green
        MarklinAccessory guard = model.newSwitch(SIGNAL_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        // and one no path holds, over the platform it is bound for
        MarklinAccessory other = model.newSwitch(GUARD_UNHELD, Accessory.accessoryDecoderType.MM2, false);

        ab.addConfigCommand(guard.getName(), Accessory.accessorySetting.GREEN);

        layout.getPoint("RG_A").setProtectingSignal(guard.getName());
        layout.getPoint("RG_B").setProtectingSignal(other.getName());

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("RG_A").setLocomotive(loc);

        final boolean[] asked = {false};
        final String[] held = {"not asked"};
        final String[] unheld = {"not asked"};
        final String[] showingRed = {"not asked"};

        layout.setCallback("held guard probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started) || asked[0] || !layout.getActiveAccs().contains(guard)) return null;

            asked[0] = true;

            String[] a = accessoryRoute(84953, SIGNAL_ADDRESS, false).conflictingAccessoryAndReason();
            String[] b = accessoryRoute(84954, GUARD_UNHELD, false).conflictingAccessoryAndReason();

            held[0] = a == null ? null : Arrays.toString(a);
            unheld[0] = b == null ? null : Arrays.toString(b);

            // held green, but showing red
            guard.setSwitched(true);

            String[] c = accessoryRoute(84955, SIGNAL_ADDRESS, false).conflictingAccessoryAndReason();

            showingRed[0] = c == null ? null : Arrays.toString(c);

            guard.setSwitched(false);

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(ab), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertTrue(asked[0], "precondition: while the train was under way the path must hold the guard");

            assertNull(held[0], "a route setting the guard of the platform the train leaves GREEN - the setting its path"
                + " holds it at - was refused: " + held[0] + ".  Adam: \"make sure the code allows switching to the correct"
                + " (same) setting that autonomy is using\"");

            assertNotNull(unheld[0], "the control: a route setting GREEN a guard no path holds, over the platform the"
                + " train is bound for, was not refused");

            assertNotNull(showingRed[0], "a route setting GREEN a guard its path holds green but that shows RED - set so to"
                + " protect a train beside it - was not refused: green is a change there (C1-B)");
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    /**
     * The exit guard of a terminus a train is leaving is held GREEN by its journey's first edge (OB-340): a route may
     * not turn it red in front of the train, and may set the green it shows.
     *
     * MUTATION: leave the guard out of what the first edge holds, and both claims fail.
     */
    @Test
    public void testARouteMayNotTurnRedTheGuardATrainLeavingATerminusHolds() throws Exception
    {
        for (String s : new String[] {S88, S88_MID, S88_MID2})
        {
            if (!model.isFeedbackSet(s)) model.newFeedback(Integer.parseInt(s), null);

            model.setFeedbackState(s, false);
        }

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("RT_T", true, S88_MID);
        layout.createPoint("RT_M", true, S88_MID2);
        layout.createPoint("RT_B", true, S88);

        layout.getPoint("RT_T").setTerminus(true);

        Edge tm = layout.createEdge("RT_T", "RT_M");
        Edge mb = layout.createEdge("RT_M", "RT_B");

        // the terminus's exit guard, which no edge commands
        MarklinAccessory guard = model.newSwitch(SIGNAL_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        layout.getPoint("RT_T").setProtectingSignal(guard.getName());

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("RT_T").setLocomotive(loc);

        // RED, as protection holds it over the train standing there
        guard.setState(Accessory.accessorySetting.RED);

        final int[] starts = {0};
        final String[] showing = {"not asked"};
        final String[] red = {"not asked"};
        final String[] green = {"not asked"};

        layout.setCallback("leaving guard probe", (edges, l, started) ->
        {
            // the second start: the train at RT_M, its journey still holding the terminus
            if (!Boolean.TRUE.equals(started) || ++starts[0] != 2) return null;

            showing[0] = guard.isSwitched() ? "RED" : "GREEN";

            String[] a = accessoryRoute(84971, SIGNAL_ADDRESS, true).conflictingAccessoryAndReason();
            String[] b = accessoryRoute(84972, SIGNAL_ADDRESS, false).conflictingAccessoryAndReason();

            red[0] = a == null ? null : Arrays.toString(a);
            green[0] = b == null ? null : Arrays.toString(b);

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(tm, mb), loc, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertEquals(showing[0], "GREEN", "precondition: the train leaving the terminus must find its exit guard green");

            assertNotNull(red[0], "a route turning RED the exit guard of the terminus a train is leaving was not refused -"
                + " the train is held by it in front of the platform it is leaving (OB-340)");

            assertNull(green[0], "a route setting GREEN the exit guard a train leaving the terminus holds green, and that"
                + " shows green, was refused: " + green[0]);
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    /**
     * A route already under way when a dispatch begins does not set the accessory either (MT-189): the guard is asked
     * again before each accessory command, and a stopped route is the smaller harm than a switch thrown under a train.
     *
     * Runs with no interface (`getGUI()` is null), so nobody is asked and the switch must never move; the yes branch is
     * `testSomebodyCanRunTheRouteAnyway`.  The first command carries a delay long enough for the dispatch to lock the
     * path between the route's two commands.
     *
     * MUTATION: removing the `heldReason(rc)` re-check from the loop fails this test.
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
        // execRoute starts a thread of its own and returns at once, so the path is locked and then held for
        // longer than the route's first delay, from inside the started callback.  The route's second command
        // then lands while the lock is definitely held, whatever a dispatch happens to take.
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
     * A route may set a protecting signal RED over an occupied platform.  It may not set it GREEN.
     *
     * The two halves of the guard are different rules: a turnout on a locked path must not move at all, while a
     * protecting signal is harmed only by a command that turns protection OFF.  The signal here is not on the
     * dispatched path, so this tests the protection half alone.
     *
     * MUTATION: dropping the `!rc.getSetting()` test fails the RED half while GREEN still passes.  Returning the
     * locked-path key from the protecting-signal branch of `heldReason` fails the last assertion (the operator's
     * sentence).
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

            // ONE query, both halves read off it: the railway is moving underneath these calls (the
            // probe runs from a dispatch callback), so two queries need not answer about the same instant.
            String[] why = toGreen.conflictingAccessoryAndReason();

            greenRefusals.add(why == null ? null : why[0]);
            greenReasons.add(why == null ? null : why[1]);

            // And whether the PRECONDITION for the reason assertion held, sampled here rather than assumed down there:
            // the protecting-signal reason is only right while the signal is not among an active path's accessories,
            // and `getActiveAccs` is computed from a map the dispatch is writing to as this runs.
            signalWasActive.add(layout.getActiveAccs().contains(signal));

            // What the two sides of that comparison actually were: `heldReason` and `getActiveAccs` resolve an
            // accessory differently, and this suite runs against the real locomotive database, where address 86 may be
            // spoken for.
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

            // And WHICH refusal it is, which is what the operator gets asked about: with one sentence for both, "which
            // is on track a train is running over right now" would be said about a train standing still on a platform
            // nothing is routed over.  Asserted only while the fixture says what it means to say: if the signal HAS
            // become one of the active path's accessories, the locked-path reason is correct (the probe ran late).
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
     * A platform a locked path has only reserved is refused as reserved, not as having a train standing at it: the
     * path holds its destination from the moment it is locked (`Point.reserve`).  Asked from inside the dispatch.
     *
     * MUTATION: have `heldReason` give the standing key whatever `protectsAStandingTrain` answers, or have
     * `protectsAStandingTrain` stop asking `whereTheTrainIs`, and this fails.
     */
    @Test
    public void testAPlatformOnlyReservedIsNotSaidToHaveATrainStanding() throws Exception
    {
        if (!model.isFeedbackSet(S88_PLATFORM)) model.newFeedback(Integer.parseInt(S88_PLATFORM), null);

        model.setFeedbackState(S88_PLATFORM, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("GR_A", false, null);

        // The platform is the DESTINATION this time, so the path itself reserves it
        layout.createPoint("GR_PLATFORM", true, S88_PLATFORM);

        Edge toPlatform = layout.createEdge("GR_A", "GR_PLATFORM");

        MarklinAccessory signal = model.newSwitch(SIGNAL_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        signal.setState(Accessory.accessorySetting.GREEN);

        layout.getPoint("GR_PLATFORM").setProtectingSignal(signal.getName());

        final Locomotive running = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("GR_A").setLocomotive(running);

        final List<String> reasons = new ArrayList<>();
        final List<Boolean> onlyReserved = new ArrayList<>();
        final List<Boolean> signalWasActive = new ArrayList<>();

        layout.setCallback("reservation probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started) || !reasons.isEmpty()) return null;

            // What the railway says, sampled at the moment of the refusal
            onlyReserved.add(layout.getPoint("GR_PLATFORM").getCurrentLocomotive() == running
                && layout.whereTheTrainIs(running) == layout.getPoint("GR_A"));

            signalWasActive.add(layout.getActiveAccs().contains(signal));

            String[] why = signalRoute(84941, false).conflictingAccessoryAndReason();

            reasons.add(why == null ? null : why[1]);

            return null;
        });

        try
        {
            assertTrue(layout.executePath(Arrays.asList(toPlatform), running, 30, null),
                "the dispatch did not complete, so nothing below tests anything");

            assertFalse(reasons.isEmpty(), "the probe never ran");

            assertTrue(onlyReserved.get(0), "precondition: when the probe ran the platform was not held by the train's"
                + " path alone - the train was already there, or nothing held it - so this would test the wrong case");

            assertFalse(signalWasActive.get(0), "precondition: the signal was one of the path's own accessories, where the"
                + " locked-path reason is the right answer");

            assertEquals(reasons.get(0), "route.refusedSignalProtectingReservedPlatform", "a route was refused over a"
                + " platform the train's path had only reserved, with the sentence for a train standing at it - and nobody"
                + " is standing there yet (GSR-C2)");

            assertNotEquals(org.traincontrol.util.I18n.f(reasons.get(0), "R", "S"),
                org.traincontrol.util.I18n.f("route.refusedSignalProtectingOccupiedPlatform", "R", "S"),
                "the reserved platform's sentence is the standing train's");
        }
        finally
        {
            layout.getPoint("GR_A").setLocomotive(null);
            layout.getPoint("GR_PLATFORM").setLocomotive(null);

            signal.setState(Accessory.accessorySetting.GREEN);

            model.clearAutoLayout();
        }
    }

    /**
     * An accessory the train has already gone past is still settable; one ahead of it is not.
     * Adam: "be careful with auto disallowed routes to avoid regression.
     * once a train passes, signals on the route, but behind the train, should still be allowed to be changed by auto
     * routes, for example."
     *
     * The guard asks whether there is a train on top of the accessory, not whether the edge's lock is held (with
     * `atomicRoutes` the lock holds until the run ends).  The tail decides, not the head: an edge is cleared only once
     * the train's LENGTH has gone past it.  Two accessories are checked at the SAME moment so a uniformly-off guard
     * cannot pass.  Three legs, since an edge is released only once the train reaches the end of the one AFTER it, and
     * the assertions read the FIRST and LAST observation because clearance shows one leg late.
     *
     * MUTATION: making `getActiveAccs` ignore `clearedEdges` fails the behind-the-train half only.
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

        // The setting Adam runs.
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
        // single variable here would let the last leg speak for all of them.
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
     * Somebody who says so can run the route anyway (MT-189): the MODEL half, that saying yes really sets the
     * accessory.  The dialog is `TrainControlUI.askAboutRouteConflict`, checked by hand in MT-189.
     *
     * MUTATION: making `execRouteOverridingConflicts` pass `false` fails this test.
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
     * OK on a route that would switch track under a train fires all of it (MT-508); a route that also carries an
     * emergency stop is not asked about at all.  MT-508's route has switch A under the train, switch B elsewhere and a
     * stop-only Route command (`core.testAStopRouteStandsAlone`).
     *
     * MUTATION: have the override skip the held switch, have a route with a stop ask, or have the guarded run throw the
     * held switch or drop the stop, and this fails.
     */
    @Test
    public void testOKFiresEveryCommandOfTheRoute() throws Exception
    {
        final boolean[] askedWithAStop = {true};
        final boolean[] withAStop = {true, false, true};
        final boolean[] askedWithout = {false};
        final boolean[] withoutAStop = {false, false};

        final int elsewhere = SWITCH_ADDRESS + 9;

        MarklinAccessory present = model.getAccessoryByAddressIfPresent(elsewhere, Accessory.accessoryDecoderType.MM2);

        final MarklinAccessory otherSwitch = present != null ? present
            : model.newSwitch(elsewhere, Accessory.accessoryDecoderType.MM2, false);

        // MT-508'S ROUTE: A, B AND A STOP - no question, fired guarded.
        onARouteOverATrain((route, underTheTrain) ->
        {
            askedWithAStop[0] = route.conflictingAccessoryAndReason() != null;

            route.execRoute(false);

            try
            {
                settle();

                model.waitForPowerState(false, POWER_PATIENCE_MS);
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            withAStop[0] = underTheTrain.isSwitched();
            withAStop[1] = otherSwitch.isSwitched();
            withAStop[2] = model.getPowerState();
        }, elsewhere, otherSwitch, true);

        assertFalse(askedWithAStop[0], "a route carrying an emergency stop put the question up - Adam, 2026-09-01:"
            + " \"Emergency stop should never conflict or prompt.\"");

        assertFalse(withAStop[0], "the route with a stop, fired without a question, threw switch A under the train");
        assertTrue(withAStop[1], "the route with a stop, fired without a question, did not throw switch B (MT-508)");
        assertFalse(withAStop[2], "the route's emergency stop did not run - the power is still on (MT-508)");

        // THE SAME WITHOUT THE STOP: asked, and OK throws both.
        onARouteOverATrain((route, underTheTrain) ->
        {
            askedWithout[0] = route.conflictingAccessoryAndReason() != null;

            // OK.
            route.execRouteOverridingConflicts();

            try
            {
                settle();
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }

            withoutAStop[0] = underTheTrain.isSwitched();
            withoutAStop[1] = otherSwitch.isSwitched();
        }, elsewhere, otherSwitch, false);

        assertTrue(askedWithout[0], "precondition: a route over the switch under the train was not questioned, so there"
            + " is no OK to press");

        assertTrue(withoutAStop[0], "OK did not throw switch A, the one under the train (MT-508)");
        assertTrue(withoutAStop[1], "OK did not throw switch B, elsewhere on the railway (MT-508)");
    }

    /** What is done with the route, from inside the dispatch that holds switch A. */
    private interface WithTheRoute
    {
        void run(MarklinRoute route, MarklinAccessory underTheTrain);
    }

    /**
     * A train dispatched over switch A, and inside that dispatch a route commanding A and B, with an emergency stop
     * where asked, handed to `probe`.  The power is on before, and put back after.  The stop is a Route command firing
     * a stop-only route (Adam: *"if a route has emergency stop, it cannot have any other types of commands"*).
     */
    private void onARouteOverATrain(WithTheRoute probe, int elsewhere, MarklinAccessory otherSwitch, boolean stop)
        throws Exception
    {
        final String stopRoute = "MT-508 route - the stop";

        if (stop && model.getRoute(stopRoute) == null)
        {
            List<RouteCommand> alone = new ArrayList<>();

            alone.add(RouteCommand.RouteCommandStop());

            assertTrue(model.newRoute(new MarklinRoute(model, stopRoute, 84909, alone, 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null)), "precondition: the stop route could not be"
                + " added to the route list");
        }

        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("OK_A", false, null);
        layout.createPoint("OK_B", true, S88);

        Edge ab = layout.createEdge("OK_A", "OK_B");

        final MarklinAccessory underTheTrain =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        underTheTrain.setSwitched(false);

        otherSwitch.setSwitched(false);

        ab.addConfigCommand(underTheTrain.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("OK_A").setLocomotive(loc);

        model.go();

        assertTrue(model.waitForPowerState(true, POWER_PATIENCE_MS), "precondition: the power has to be ON, or the"
            + " route's stop has nothing to turn off");

        layout.setCallback("OK probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            List<RouteCommand> commands = new ArrayList<>();

            commands.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, true));
            commands.add(RouteCommand.RouteCommandAccessory(elsewhere, Accessory.accessoryDecoderType.MM2, true));

            if (stop) commands.add(RouteCommand.RouteCommandRoute(stopRoute));

            probe.run(new MarklinRoute(model, "MT-508 route", 84908, commands, 0,
                MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null), underTheTrain);

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

            model.waitForPowerState(true, POWER_PATIENCE_MS);

            otherSwitch.setSwitched(false);

            if (model.getRoute(stopRoute) != null) model.deleteRoute(stopRoute);

            model.clearAutoLayout();
        }
    }

    /**
     * Deleting a locomotive says how many routes drive it, before it takes their commands away (Adam: "OK - let's add
     * a warning about this.").  Commands only: a route that merely mentions it in a condition must not count.
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
     * And the confirmation dialog actually asks for that count: anchored on the call, since the key existing in the
     * bundles says only that somebody wrote a sentence.
     *
     * MUTATION: count something else than `r.commandsDrive(value)` in `LocomotiveActions.deleteLoc`, and this fails.
     */
    @Test
    public void testTheDeleteDialogNamesTheRoutesThatWillLoseCommands() throws Exception
    {
        String source = SourceOf.declaration("    boolean deleteLoc(String value, Component source)");

        assertTrue(source.contains("r.commandsDrive(value)"),
            "the delete flow no longer counts the routes that drive this locomotive, so the "
            + "confirmation cannot say what deleting it will take away");

        assertTrue(source.contains("ui.confirmDeleteFromDatabaseWithRoutes"),
            "the delete confirmation no longer uses the message that names the route count, so the "
            + "operator is asked the old question and told nothing about their routes");
    }

    /**
     * A route that cuts the power is never held back, and its stop always goes out (MT-247; `behaviour/routes.md`
     * section 7a): being excused the question is worth nothing if the command is then dropped.
     *
     * Two claims and two controls, inside a dispatch with the path locked: the route carrying a stop is not screened at
     * the human door while the SAME route without one is; and the power really goes off while the conflicting
     * accessory is still not thrown.
     *
     * MUTATION: take `if (this.hasEmergencyStop()) return null;` out of
     * `MarklinRoute.conflictingAccessoryAndReason` and the first claim fails.
     */
    @Test
    public void testARouteWithAnEmergencyStopIsNeverHeldBack() throws Exception
    {
        if (!model.isFeedbackSet(S88)) model.newFeedback(Integer.parseInt(S88), null);

        model.setFeedbackState(S88, false);

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);

        layout.createPoint("ES_A", false, null);
        layout.createPoint("ES_B", true, S88);

        Edge ab = layout.createEdge("ES_A", "ES_B");

        MarklinAccessory turnout =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        turnout.setSwitched(false);

        ab.addConfigCommand(turnout.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("ES_A").setLocomotive(loc);

        final String[][] askedAboutTheStop = {null};
        final String[][] askedAboutTheOther = {null};
        final boolean[] sawTheLock = {false};
        final boolean[] powerAfter = {true};
        final boolean[] switchedAfter = {false};

        layout.setCallback("emergency stop probe", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            if (layout.getActiveAccs().contains(turnout)) sawTheLock[0] = true;

            // THE SCREENING QUESTION, asked of both routes at the moment the conflict is real.
            askedAboutTheStop[0] = new String[][] {routeWithAStop(84921).conflictingAccessoryAndReason()}[0];
            askedAboutTheOther[0] = new String[][] {route(84922).conflictingAccessoryAndReason()}[0];

            // AND THEN RUN IT.  The power is put on first so that "the power is off" afterwards can
            // only be this route's doing.
            model.go();

            try
            {
                settle();
            }
            catch (InterruptedException ignored)
            {
            }

            routeWithAStop(84923).execRoute(false);

            try
            {
                settle();
            }
            catch (InterruptedException ignored)
            {
            }

            powerAfter[0] = model.getPowerState();
            switchedAfter[0] = turnout.isSwitched();

            return null;
        });

        try
        {
            layout.executePath(Arrays.asList(ab), loc(loc), 30, null);

            settle();
        }
        finally
        {
            // The callback is not removed: every test in this class begins with `clearAutoLayout()`, which discards the
            // whole Layout and its callbacks, and `setCallback(name, null)` throws (ConcurrentHashMap).
            model.go();
        }

        assertTrue(sawTheLock[0],
            "the probe never saw the turnout reserved, so nothing below was asked in the state this "
            + "test is about - the fixture is wrong, not the railway");

        // THE CONTROL FIRST: the conflict really is there for a route without a stop.
        assertNotNull(askedAboutTheOther[0],
            "the same route WITHOUT an emergency stop was not screened either, so the claim below is "
            + "about a door that screens nothing rather than about the stop");

        assertNull(askedAboutTheStop[0],
            "a route carrying an emergency stop was screened at the human door, so the operator would "
            + "be asked to confirm it - and whichever way they answered, they would not be answering "
            + "about the power. Adam, 2026-09-01: \"emergency stop should never conflict or prompt\" "
            + "(SVN-A4, FX2-2)");

        assertFalse(powerAfter[0],
            "the route ran and the power is still on, so its emergency stop was skipped. That is the "
            + "one refusal that can make things worse (behaviour.md section 7a)");

        assertFalse(switchedAfter[0],
            "the emergency stop was obeyed by running the whole route, including the turnout under a "
            + "moving train. Declining to throw that switch was never the part in dispute - the stop "
            + "is exempt from the QUESTION, not from the per-command check");
    }

    /**
     * The same one-accessory route, with an emergency stop after it (second on purpose, since a stop written first
     * would pass an implementation that only looked at the head).
     *
     * @param id a route id nothing else uses
     * @return the route, not executed
     */
    private static MarklinRoute routeWithAStop(int id)
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(SWITCH_ADDRESS,
            Accessory.accessoryDecoderType.MM2, true));

        commands.add(RouteCommand.RouteCommandStop());

        return new MarklinRoute(model, "ES route " + id, id, commands, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);
    }

    private static MarklinRoute route(int id)
    {
        return oneCommandRoute("RG route ", id, SWITCH_ADDRESS, true);
    }

    /** A route of the name followed by its id, with one command: the accessory at the address, thrown or not. */
    private static MarklinRoute oneCommandRoute(String name, int id, int address, boolean throwIt)
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(address, Accessory.accessoryDecoderType.MM2, throwIt));

        return new MarklinRoute(model, name + id, id, commands, 0,
            MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);
    }

    /**
     * A one-command route that sets the turnout one way.
     *
     * @param id a route id nothing else uses
     * @param turn true for TURN, false for STRAIGHT - the sense RouteCommand and Accessory both use
     * @return the route, not executed
     */
    private static MarklinRoute switchRoute(int id, boolean turn)
    {
        return oneCommandRoute("RG switch ", id, SWITCH_ADDRESS, turn);
    }

    /** A second protecting signal, which no path holds. */
    private static final int GUARD_UNHELD = 87;

    /**
     * A one-command route setting an accessory by address.
     *
     * @param id a route id nothing else uses
     * @param address the accessory's address
     * @param throwIt true for TURN or RED, false for STRAIGHT or GREEN
     * @return the route, not executed
     */
    private static MarklinRoute accessoryRoute(int id, int address, boolean throwIt)
    {
        return oneCommandRoute("RG accessory ", id, address, throwIt);
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
        return oneCommandRoute("RG signal ", id, SIGNAL_ADDRESS, red);
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
     * A turnout under a MEASURED train stays refused, on a path that is only partly measured.  Sibling of
     * `testAnAccessoryBehindTheTrainIsStillSettable`, with a train that has a length.
     *
     * Edges of 100, 100 and 0 with a train of 250: when the head finishes the unmeasured third edge, the first edge
     * has 150 units of train still standing on it, so being reported clear is the ONLY thing that stops `heldReason`
     * refusing a route that would throw that turnout.
     *
     * MUTATION: restoring the escape (release when the last edge traversed had no length) fails the
     * behind-the-train assertion.
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

            // 100 measured units behind, 250 of train.
            // WHAT IT ACTUALLY SAW, so a failure inside a full battery can be told apart from one run alone.
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
