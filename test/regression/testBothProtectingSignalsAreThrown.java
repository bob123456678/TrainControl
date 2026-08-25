package regression;

import java.util.LinkedList;
import java.util.List;
import org.traincontrol.automation.Edge;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A platform with two protecting signals throws BOTH of them.
 *
 * MT-023, Adam: "Does not work - only first is set to red. Selection process is ok."
 *
 * A station may be reachable from each end, so it may be protected by a signal on each approach. They
 * are commanded together and show the same aspect - they say the same thing about the same platform -
 * and a platform guarded at one end and open at the other is worse than one guarded at neither,
 * because it looks protected.
 *
 * **Why this test is at the layout rather than in the editor.** Reading the chain settled where it is
 * NOT: the store keeps a list, `protectingSignalNames` maps every one of them, the builder writes one
 * as a bare string and several as an array, `fromJSON` reads both shapes back (testAutoLayout covers
 * that), and the aspect is memoised per ACCESSORY rather than per Point - with a comment recording that
 * keying it per Point was itself a bug, because one copy of a square wrote a memo while standing empty
 * and the signal stayed green with a train at the platform.
 *
 * Every link handles several. What no test covered was the end of the chain actually being reached for
 * more than one - which is exactly what "only first is set to red" describes.
 *
 * `refreshAllProtectingSignals` is public and asks each signal directly, without the "only while
 * running" guard that protects the per-occupancy path, so this needs no trains and no hardware.
 *
 * @author Adam
 */
public class testBothProtectingSignalsAreThrown
{
    private static MarklinControlStation model;

    /** The test range testAccessory established; 280-285 are its own, so these are the next free two */
    private static final int NEAR = 286;
    private static final int FAR = 287;

    private static MarklinAccessory near;
    private static MarklinAccessory far;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, true);

        near = model.getAccessoryByAddress(NEAR, Accessory.accessoryDecoderType.MM2);
        far = model.getAccessoryByAddress(FAR, Accessory.accessoryDecoderType.MM2);

        assertNotNull(near, "could not get an accessory at " + NEAR);
        assertNotNull(far, "could not get an accessory at " + FAR);
    }

    @AfterClass
    public static void tearDownClass()
    {
        // Left as found.  These are rows in the operator's own accessory database, and a test that
        // leaves two signals thrown has changed what his railway believes about itself.
        if (near != null) near.setState(Accessory.accessorySetting.GREEN);
        if (far != null) far.setState(Accessory.accessorySetting.GREEN);

        if (model != null) model.stop();
    }

    /**
     * Occupied: both red. Empty again: both green.
     */
    @Test
    public void testAPlatformGuardedAtBothEndsThrowsBothSignals() throws Exception
    {
        Layout layout = Layout.fromJSON(twoSignalLayout(), model);

        assertNotNull(layout, "the layout did not parse");
        assertTrue(layout.isValid(), "the layout is invalid: " + Layout.getLastError());

        assertEquals(layout.getPoint("PLATFORM").getProtectingSignals(),
            Arrays.asList(near.getName(), far.getName()),
            "the platform did not come back protected by both signals, so anything below this would "
            + "be testing the wrong thing");

        near.setState(Accessory.accessorySetting.GREEN);
        far.setState(Accessory.accessorySetting.GREEN);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertNotNull(loc, "no locomotive to stand on the platform");

        layout.getPoint("PLATFORM").setLocomotive(loc);

        layout.refreshAllProtectingSignals();

        assertTrue(near.isSwitched(),
            "the first signal protecting an occupied platform was not thrown");

        assertTrue(far.isSwitched(),
            "only the FIRST signal was thrown. The platform is guarded at one end and open at the "
            + "other, which is worse than being guarded at neither - it looks protected (MT-023)");

        // And back, because a rule that only closes is half a rule
        layout.getPoint("PLATFORM").setLocomotive(null);

        layout.refreshAllProtectingSignals();

        assertFalse(near.isSwitched(), "the first signal stayed red over an empty platform");

        assertFalse(far.isSwitched(),
            "the second signal stayed red over an empty platform - which holds every train that "
            + "approaches from that end, for good");
    }

    /**
     * Deleting a locomotive takes it off the railway, not just out of the lists.
     *
     * UR-3, from the uninformed review. `locDeleted` sweeps six things - the run list, the active
     * locomotives, the milestones, each Point's exclusions, each Point's home, and the home claims -
     * and does not clear the locomotive STANDING on a Point.
     *
     * Two consequences, both bad. The square stays occupied by a train that no longer exists, so
     * nothing can ever be routed through it again; and `Point.toJSON` writes the name back out, so the
     * next load reports a locomotive that is not in the database and **invalidates the whole
     * configuration** - which answers null for every point in it, so the railway simply stops working.
     *
     * The same shape as the exclusions and the home two lines above it, both of which had to be added
     * later for exactly this reason. Their own comments say so.
     */
    @Test
    public void testDeletingALocomotiveTakesItOffThePointItStandsOn() throws Exception
    {
        Layout layout = Layout.fromJSON(twoSignalLayout(), model);

        assertTrue(layout.isValid(), "the layout is invalid: " + Layout.getLastError());

        Locomotive standing = model.getLocByName(model.getLocList().get(0));

        assertNotNull(standing, "no locomotive to stand on the platform");

        layout.getPoint("PLATFORM").setLocomotive(standing);

        assertEquals(layout.getPoint("PLATFORM").getCurrentLocomotive(), standing,
            "the locomotive was not placed, so nothing below tests anything");

        layout.locDeleted(standing);

        assertNull(layout.getPoint("PLATFORM").getCurrentLocomotive(),
            "a deleted locomotive is still standing on the platform. The square is occupied by a train "
            + "that does not exist, so nothing can be routed through it again (UR-3)");

        assertFalse(layout.getPoint("PLATFORM").toJSON().toString().contains(standing.getName()),
            "the deleted locomotive's name is still written into the configuration. On the next load "
            + "that is a locomotive not in the database, which invalidates the WHOLE configuration - "
            + "every point in it then answers null and the railway stops working");
    }

    /**
     * Protection re-asserts after something else has moved the signal.
     *
     * UR-4, from the uninformed review. `TilePorts` gives a SIGNAL tile a GREEN configuration command,
     * so a path configured across one commands it green through `getConfigCommands` - and
     * `configureAndLockPath` does that AFTER reserving the point, in the same loop: `e.getEnd().reserve`
     * then `configureEdge(e)`. The same `Accessory`, driven behind protection's back.
     *
     * `signalAspects` remembers what protection last COMMANDED rather than what the signal is showing,
     * so from then on it agrees with itself and sends nothing. The signal stays green over an occupied
     * platform until the train leaves, and no later occupancy change corrects it.
     *
     * **What this test does NOT assert.** Adam's ruling, 2026-08-23: "This shouldn't happen. The
     * protecting signal is at the destination, so the destination shouldn't be in the middle. If a
     * train just passes through (legally, i.e. there is no lock edge preventing it), then you would set
     * the signal to green and set the destination's to red (if any)." A signal a path crosses and a
     * signal protecting the destination are two different signals, so which aspect "wins" is not a
     * question the railway is supposed to be asked. This asserts only the part that holds either way:
     * protection may not be SILENTLY SKIPPED because of something it remembers. Whatever moved the
     * signal, the next occupancy change is decided by looking.
     *
     * The memo is still wanted. It is what keeps `configureAndLockPath` from putting a burst of
     * accessory traffic under the layout monitor, one command per point it reserves, on the thread the
     * interface also needs. It just may not be the only thing asked - and the accessory's own state is
     * set optimistically by every caller, protection and path configuration alike, so it is the one
     * record that cannot go stale behind somebody's back.
     *
     * Two platforms share one signal so that both triggers see the square CLAIMED. With one platform
     * the clearing transition would write the memo false in between and the second command would be
     * sent for the ordinary reason, testing nothing.
     */
    @Test
    public void testProtectionReassertsAfterSomethingElseMovesTheSignal() throws Exception
    {
        for (int address : new int[]{47423, 47424, 47425, 47426})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);

            model.setFeedbackState(Integer.toString(address), false);
        }

        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        layout.createPoint("MP A", true, "47423");
        layout.createPoint("MP B", true, "47424");
        layout.createPoint("MP P", true, "47425");
        layout.createPoint("MP Q", true, "47426");

        layout.getPoint("MP P").setProtectingSignal(near.getName());
        layout.getPoint("MP Q").setProtectingSignal(near.getName());

        List<Edge> path = new LinkedList<>();
        path.add(layout.createEdge("MP A", "MP B"));

        Locomotive driving = model.getLocByName(model.getLocList().get(0));
        Locomotive first = model.getLocByName(model.getLocList().get(1));
        Locomotive second = model.getLocByName(model.getLocList().get(2));

        assertNotNull(first, "the database does not hold enough locomotives for this test");
        assertNotNull(second, "the database does not hold enough locomotives for this test");

        assertTrue(layout.moveLocomotive(driving.getName(), "MP A", false),
            "precondition - the driven locomotive must be placed");

        near.setState(Accessory.accessorySetting.GREEN);

        final java.util.concurrent.atomic.AtomicBoolean watched =
            new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean protectedAtFirst =
            new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean protectedAfter =
            new java.util.concurrent.atomic.AtomicBoolean();

        // Inside the start callback because that is where a train is under way, which is the only
        // condition under which protection speaks at all - see the hand-dispatch test below.
        layout.setCallback("memo watcher", (edges, l, started) ->
        {
            // Once. A start is announced again as the train passes each milestone, and a second run
            // of this block read the signal back AFTER it had already driven it green - which reads
            // exactly like protection having failed at the first step.
            if (!Boolean.TRUE.equals(started) || !watched.compareAndSet(false, true)) return null;

            layout.getPoint("MP P").setLocomotive(first);

            protectedAtFirst.set(near.isSwitched());

            // What configuring a path across the signal tile does: the same accessory, the same door,
            // and no word to the protection that is holding it red.
            near.setState(Accessory.accessorySetting.GREEN);

            // Any later occupancy change asks again. Both platforms behind this signal are claimed,
            // so the answer has not changed - and a signal showing the wrong aspect must still be
            // commanded.
            layout.getPoint("MP Q").setLocomotive(second);

            protectedAfter.set(near.isSwitched());

            return null;
        });

        assertTrue(layout.executePath(path, driving, 30, null),
            "the dispatch did not complete, so nothing below tests anything");

        assertTrue(protectedAtFirst.get(),
            "the platform was not protected in the first place, so nothing below tests anything");

        assertTrue(protectedAfter.get(),
            "the signal is GREEN over an occupied platform. Something else drove it green, and "
            + "protection agreed with its own memo instead of looking, so it sent nothing - and every "
            + "refresh after this one agrees too. The platform stays open until the train leaves (UR-4)");

        layout.getPoint("MP P").setLocomotive(null);
        layout.getPoint("MP Q").setLocomotive(null);
        layout.getPoint("MP A").setLocomotive(null);
        layout.getPoint("MP B").setLocomotive(null);
    }

    /**
     * A hand dispatch protects the platform of a train that is just standing there (AU-B7).
     *
     * The test next door covers the DESTINATION of the dispatched train. This one is about a train
     * nobody dispatched, and it is the case the sweep exists for.
     *
     * While nothing is running the protection refresh is deliberately silent - trains are placed and
     * taken off by hand then, and driving real signals from a setup gesture is what that silence
     * exists to prevent. So a train placed at a protected platform while idle produces NO occupancy
     * change, and nothing will ever command that platform's signal on its own.
     *
     * `runLocomotives` and `executeTimetableInternal` both sweep every protecting signal the moment
     * they set `running`, for exactly that reason. `executePath` - the diagram's right-click dispatch -
     * became a full run in the MT-139 work, counting its thread and engaging every guard, and did not
     * inherit the sweep. So starting autonomy threw that platform red and hand-dispatching a different
     * train left it green with a train standing at it.
     *
     * Adam's rule, quoted inside `executePath` itself: "The same thing should happen in manual
     * operation vs auto - the same switches and signals set, and guards applied." Two of the three
     * doors did.
     *
     * Found by a review pass, which built the probe this is written from.
     *
     * MUTATION: removing the `refreshAllProtectingSignals()` call from `executePath` fails this test.
     */
    @Test
    public void testAHandDispatchProtectsAPlatformSomebodyIsAlreadyStandingIn() throws Exception
    {
        // Its OWN addresses (validation pass, C-3).
        //
        // This took 47431-47433, which testAThrowWhileLockingReleasesTheTrack builds LK A/B/C on -
        // and a simulated dispatch clears its feedback from a DETACHED thread, so on a loaded machine
        // the clear had not landed when the sibling ran and its path read as occupied. Two failures in
        // eighteen runs, with the reason in the log: "Expects feedback 47432 to be clear".
        //
        // The test next door already avoids this by using a range of its own and says so. This one
        // took the busy addresses instead.
        for (String feedback : new String[] {"47441", "47442", "47443"})
        {
            if (!model.isFeedbackSet(feedback)) model.newFeedback(Integer.parseInt(feedback), null);

            model.setFeedbackState(feedback, false);
        }

        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        // The two the dispatch runs between, and a third where somebody is already standing.
        layout.createPoint("HD A", true, "47441");
        layout.createPoint("HD B", true, "47442");
        layout.createPoint("HD P", true, "47443");

        // Only the STANDING train's platform is protected, so nothing about the dispatch itself can
        // account for the signal moving.
        layout.getPoint("HD P").setProtectingSignal(far.getName());

        List<Edge> path = new LinkedList<>();
        path.add(layout.createEdge("HD A", "HD B"));

        Locomotive driving = model.getLocByName(model.getLocList().get(0));
        Locomotive standing = model.getLocByName(model.getLocList().get(1));

        assertNotEquals(driving.getName(), standing.getName(),
            "this test needs two different locomotives");

        assertTrue(layout.moveLocomotive(driving.getName(), "HD A", false),
            "precondition - the train that will be dispatched must be placed");

        assertTrue(layout.moveLocomotive(standing.getName(), "HD P", false),
            "precondition - somebody must be standing at the protected platform");

        // Placed by hand while idle, so the signal has NOT been commanded - that silence is the
        // behaviour the sweep exists to compensate for, and asserting it here is what stops this test
        // passing for the wrong reason.
        far.setState(Accessory.accessorySetting.GREEN);

        assertFalse(far.isSwitched(),
            "precondition - placing a train while nothing is running must not command its signal, or "
            + "there would be nothing for the sweep to do");

        assertTrue(layout.executePath(path, driving, 30, null),
            "the hand dispatch did not complete, so nothing below tests anything");

        assertTrue(far.isSwitched(),
            "a train was standing at a protected platform, somebody hand-dispatched a DIFFERENT train, "
            + "and the platform stayed GREEN for the whole dispatch.  Starting autonomy or executing a "
            + "timetable sweeps the signals at that moment for exactly this reason; the right-click "
            + "dispatch became a run and did not inherit the sweep");

        layout.getPoint("HD A").setLocomotive(null);
        layout.getPoint("HD B").setLocomotive(null);
        layout.getPoint("HD P").setLocomotive(null);
    }

    /**
     * A train dispatched BY HAND protects the platform it is heading for.
     *
     * UR-2, from the uninformed review, and Adam's ruling on it: "The same thing should happen in
     * manual operation vs auto - the same switches and signals set, and guards applied."
     *
     * Today it does not. `refreshProtectingSignal` returns unless `isRunning()`, and a hand dispatch
     * from the diagram's right-click menu is a bare `new Thread` straight into `executePath` - it never
     * touches `locomotiveThreads`, and `activeLocomotives` is written AFTER `configureAndLockPath` has
     * already reserved every point on the route. So during the one phase that matters, none of
     * isRunning()'s three terms is true and the destination's signal is never commanded.
     *
     * The window is the approach: the platform is reserved for this train, and the signal guarding it
     * still shows GREEN to the next one. It closes only when the train itself claims the destination,
     * by which time it is standing there.
     *
     * **Why the guard is not simply wrong.** Its comment records the defect it exists for - "cutting a
     * locomotive off a platform with Control+X drove its protecting signals on the spot, which is
     * hardware moving in response to a setup gesture". That is still true and must stay fixed. The
     * distinction it draws is just the wrong one: not "is autonomy running" but "is a train under way",
     * and a hand dispatch is a train under way.
     *
     * **Observed from the start callback**, which fires immediately after the path is locked, rather
     * than by polling a running train. By then `activeLocomotives` holds the locomotive, so isRunning()
     * has become true - but nothing asks the destination again, so what the callback sees is the aspect
     * chosen back when the path was locked. Deterministic, and it is the exact moment the approach
     * begins.
     */
    @Test
    public void testAHandDispatchedTrainProtectsItsDestination() throws Exception
    {
        // Created before they are set: setFeedbackState refuses a feedback the station has never
        // heard of, and 47421/47422 are this class's own - testAutonomySimulationSanity has 47411/2.
        if (!model.isFeedbackSet("47421")) model.newFeedback(47421, null);
        if (!model.isFeedbackSet("47422")) model.newFeedback(47422, null);

        model.setFeedbackState("47421", false);
        model.setFeedbackState("47422", false);

        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        layout.createPoint("MD A", true, "47421");
        layout.createPoint("MD B", true, "47422");

        layout.getPoint("MD B").setProtectingSignal(near.getName());

        List<Edge> path = new LinkedList<>();
        path.add(layout.createEdge("MD A", "MD B"));

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "MD A", false),
            "precondition - the locomotive must be placed at the start");

        near.setState(Accessory.accessorySetting.GREEN);

        final java.util.concurrent.atomic.AtomicBoolean redAtDeparture =
            new java.util.concurrent.atomic.AtomicBoolean();

        layout.setCallback("protection watcher", (edges, l, started) ->
        {
            if (Boolean.TRUE.equals(started)) redAtDeparture.set(near.isSwitched());
            return null;
        });

        // No runLocomotives() and no autonomy: this is the right-click menu's dispatch, which is a
        // bare thread into executePath.
        assertTrue(layout.executePath(path, loc, 30, null),
            "the hand dispatch did not complete, so nothing below tests anything");

        assertTrue(redAtDeparture.get(),
            "a train was dispatched by hand toward a protected platform and the platform's signal was "
            + "left GREEN for the whole approach. The route was locked before anything registered the "
            + "locomotive as running, so protection looked, saw nothing running, and said nothing - "
            + "the same dispatch under autonomy throws the signal (UR-2)");

        layout.getPoint("MD A").setLocomotive(null);
        layout.getPoint("MD B").setLocomotive(null);
    }

    /**
     * A dispatch that is TURNED AWAY leaves the signals alone.
     *
     * Found by review, which reproduced it: the sweep the test above is about ran at the very top of
     * `executePath`, before all eight of the checks that can refuse a dispatch. So asking for a bad one
     * - a speed out of range, an empty path, a train that is not at the start, one that is already
     * running - commanded every protecting signal on the layout on the way to being told no.
     *
     * And they stayed. The thread count falls straight back to zero, `isRunning()` goes false, and the
     * per-occupancy refresh is deliberately silent while nothing runs - so nothing would correct them
     * until something else started a run.
     *
     * The rule being broken is one this class's own subject states for itself: the railway is not
     * commanded while the operator is still deciding what it should look like. A refusal is that
     * moment.
     *
     * Speed zero is the refusal used here because it is the earliest one that needs no fixture of its
     * own - `errorInvalidSpeed`, before anything is locked or reserved.
     *
     * MUTATION: moving `refreshAllProtectingSignals()` back above the checks in `executePath` - which
     * is where it was - fails this test.
     */
    @Test
    public void testARefusedDispatchCommandsNothing() throws Exception
    {
        if (!model.isFeedbackSet("47425")) model.newFeedback(47425, null);
        if (!model.isFeedbackSet("47426")) model.newFeedback(47426, null);

        model.setFeedbackState("47425", false);
        model.setFeedbackState("47426", false);

        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        layout.createPoint("RD A", true, "47425");
        layout.createPoint("RD B", true, "47426");

        layout.getPoint("RD B").setProtectingSignal(near.getName());

        List<Edge> path = new LinkedList<>();
        path.add(layout.createEdge("RD A", "RD B"));

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "RD A", false),
            "precondition - the locomotive must be placed at the start");

        // A train standing at the protected platform, so the sweep would have something to say: with
        // the platform empty it would command GREEN, which is already the aspect, and refreshOneSignal
        // sends nothing when the aspect does not change.  This test would then pass with the defect
        // present, which is the shape of vacuous test this suite keeps finding.
        layout.getPoint("RD B").setLocomotive(loc);

        near.setState(Accessory.accessorySetting.GREEN);

        assertFalse(near.isSwitched(), "the fixture did not take: the signal must start green");

        try
        {
            // Speed zero: refused by errorInvalidSpeed, before anything is locked.
            assertFalse(layout.executePath(path, loc, 0, null),
                "precondition: this dispatch must be REFUSED, or the test is about an accepted one");

            assertFalse(near.isSwitched(),
                "a dispatch that was turned away commanded a protecting signal anyway, and nothing "
                + "will move it back: the thread count is zero again, so the per-occupancy refresh is "
                + "silent. The railway was commanded while the operator was still deciding what it "
                + "should look like");
        }
        finally
        {
            layout.getPoint("RD A").setLocomotive(null);
            layout.getPoint("RD B").setLocomotive(null);

            near.setState(Accessory.accessorySetting.GREEN);
        }
    }

    /**
     * A failure while LOCKING a path releases what it has taken.
     *
     * UR-11, from the uninformed review. The two returned-false failures of `configureAndLockPath` are
     * handled - `handleMisconfiguredPath` plus `takingPath.remove` - and a THROWN one was not. It
     * escaped the lock loop into `executePath`'s catch, whose comment says why it deliberately does not
     * unlock:
     *
     *   "The locomotive may be physically standing on those edges, and releasing them would let another
     *   train be routed into occupied track."
     *
     * That is true of a failure MID-RUN and false of a failure while locking: `loc.setSpeed(speed)` is
     * not issued until after configureAndLockPath has returned, so the train has not moved. The rule was
     * lifted from the case whose precondition made it safe.
     *
     * Two things were left behind, and neither ever clears. Every edge taken so far stays occupied for
     * the rest of the session, along with its lock edges - so that track is refused to every train.
     * And `reserve` has already put the locomotive on those Points and deliberately does not sweep, so
     * it is recorded at several places at once; `pickPath` then takes the first Point in iteration order
     * where the locomotive matches, which can be a mid-path Point it is not standing on, and configures
     * a whole route - real ironwork - for a departure from a station the train is not at.
     *
     * The throw is injected rather than provoked. `configureEdge` reaches `setSwitched`, which calls
     * into Swing on the driving thread and then onto the network, and neither can be made to fail on
     * demand from a test. What the fix is about is not which call throws but what is left behind when
     * one does.
     */
    @Test
    public void testAThrowWhileLockingReleasesTheTrack() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);

        for (int address : new int[]{47431, 47432, 47433})
        {
            if (!model.isFeedbackSet(Integer.toString(address))) model.newFeedback(address, null);
        }

        layout.createPoint("LK A", true, "47431");
        layout.createPoint("LK B", false, "47432");
        layout.createPoint("LK C", true, "47433");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        Edge first = layout.createEdge("LK A", "LK B");

        // The second edge fails the moment the path tries to set it up - after the first has been
        // locked and this one's Point reserved.
        // Not on the FIRST ask.  isPathClear previews the configuration of every edge before anything is
        // locked - its own comment says so - so a subclass that throws unconditionally throws there, with
        // nothing taken yet and nothing to leave behind.  That version of this test passed against the
        // unfixed code, which is the trap worth recording: the injected failure has to land where the
        // real one would.
        final java.util.concurrent.atomic.AtomicInteger asked =
            new java.util.concurrent.atomic.AtomicInteger();

        Edge second = new Edge(layout.getPoint("LK B"), layout.getPoint("LK C"))
        {
            @Override
            public java.util.Map<String, org.traincontrol.base.Accessory.accessorySetting> getConfigCommands()
            {
                if (asked.incrementAndGet() > 1)
                {
                    throw new IllegalStateException("the accessory went away mid-lock");
                }

                return super.getConfigCommands();
            }
        };

        layout.getPoint("LK A").setLocomotive(loc);

        List<Edge> path = new LinkedList<>();
        path.add(first);
        path.add(second);

        try
        {
            layout.configureAndLockPath(path, loc);

            fail("the injected failure did not reach the lock loop, so nothing below tests anything");
        }
        catch (RuntimeException expected)
        {
            // what executePath's handler sees
        }

        assertFalse(first.isOccupied(loc),
            "the edges locked before the failure are still occupied. Nothing clears them, so that "
            + "track - and every lock edge behind it - is refused to every train for the rest of the "
            + "session (UR-11)");

        assertNull(layout.getPoint("LK B").getCurrentLocomotive(),
            "the locomotive is still reserved on a Point in the middle of the path it never took. "
            + "reserve does not sweep, so it is now recorded in two places at once - and pickPath takes "
            + "the first one it finds, which can be a station the train is not standing at (UR-11)");

        assertEquals(layout.getPoint("LK A").getCurrentLocomotive(), loc,
            "the locomotive was swept off the point it is actually standing on");
    }

    /**
     * A layout with one platform guarded at each end.
     *
     * The s88 numbers and the run-wide delays are here because `fromJSON` invalidates the WHOLE layout
     * over a missing one - and an invalidated layout answers null for every point in it, which reads
     * exactly like the signals having been dropped.
     */
    private String twoSignalLayout()
    {
        return "{"
            + "\"points\": ["
            + "  {\"name\": \"PLATFORM\", \"station\": true, \"s88\": 106,"
            + "   \"protectingSignal\": [\"" + near.getName() + "\", \"" + far.getName() + "\"]},"
            + "  {\"name\": \"APPROACH\", \"station\": true, \"s88\": 107}"
            + "],"
            + "\"edges\": [{\"start\": \"APPROACH\", \"end\": \"PLATFORM\", \"length\": 1}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";
    }
}
