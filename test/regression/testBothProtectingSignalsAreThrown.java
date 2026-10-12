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

    @AfterClass(alwaysRun = true)
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
     * A dispatch does not touch a signal it has nothing to do with (OB-166).
     *
     * Adam: "when sending a train from bottominnerotherside to bottominner, signal 64 goes from red to
     * green unnecessarily."  And his ruling on it: "Signals should only be touched when a route
     * activates."
     *
     * Measured on his own layout before this was written. Signal 64 protects TopMainR2's two copies
     * and nothing else; both were empty; and the only route between the two points he named does not
     * command it. What moved it was the sweep - `refreshAllProtectingSignals` re-commanding every
     * protecting signal on the layout from current occupancy, the first time anything is dispatched
     * by hand. An empty platform is commanded GREEN, so a signal he had red went green because a train
     * elsewhere started moving.
     *
     * The platform here is EMPTY and the signal is RED, which is the state a route leaves behind. A
     * dispatch between two other points is not a route activating over this signal, so nothing may
     * command it.
     *
     * MUTATION: putting any of the three refreshAllProtectingSignals() calls back fails this.
     */
    @Test
    public void testADispatchLeavesAnUnrelatedSignalAlone() throws Exception
    {
        for (String feedback : new String[] {"47451", "47452", "47453"})
        {
            if (!model.isFeedbackSet(feedback)) model.newFeedback(Integer.parseInt(feedback), null);

            model.setFeedbackState(feedback, false);
        }

        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        layout.createPoint("UN A", true, "47451");
        layout.createPoint("UN B", true, "47452");
        layout.createPoint("UN P", true, "47453");

        // The protected platform has nothing to do with the dispatch, and nobody is standing on it.
        layout.getPoint("UN P").setProtectingSignal(far.getName());

        List<Edge> path = new LinkedList<>();
        path.add(layout.createEdge("UN A", "UN B"));

        Locomotive driving = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(driving.getName(), "UN A", false),
            "precondition - the train to dispatch must be placed");

        // RED, as a route would have left it.
        far.setState(Accessory.accessorySetting.RED);

        assertTrue(far.isSwitched(), "precondition - the signal must start red");

        assertTrue(layout.executePath(path, driving, 30, null),
            "the hand dispatch did not complete, so nothing below tests anything");

        assertTrue(far.isSwitched(),
            "a dispatch between two other points drove a signal from red to green. It protects an "
            + "empty platform that this route never touches - and a signal is only to be touched when "
            + "a route activates over it, not because some train somewhere started moving");

        layout.getPoint("UN A").setLocomotive(null);
        layout.getPoint("UN B").setLocomotive(null);
    }

    /**
     * A train passing a station leaves that station's guards alone (OB-315, OB-316).
     *
     * Adam, 2026-10-03: *"I think the root cause for the last two is that the exit guard signals are applied for more than
     * just the arrival station."*  They were: an exit guard was red while its platform was claimed, and a locked path
     * claims every station along it - so each station a train only passed turned its exit guard red until the path let
     * it go.  And his question, *"check if the same issue applies to the entry guards, not just exit"*: the entry guard is
     * thrown only where a journey ends, so a passing train leaves it alone - asserted here beside the exit guard.
     *
     * Watched through the whole run, because the red a passing train left lasted only while the path held the station.
     *
     * MUTATION: let a reservation by a passing train count again, and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testAPassingTrainLeavesAStationsGuardsAlone() throws Exception
    {
        for (String feedback : new String[] {"47461", "47462", "47463"})
        {
            if (!model.isFeedbackSet(feedback)) model.newFeedback(Integer.parseInt(feedback), null);

            model.setFeedbackState(feedback, false);
        }

        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        // A, then M, a station the train only passes, then B where it stops
        layout.createPoint("PT A", true, "47461");
        layout.createPoint("PT M", true, "47462");
        layout.createPoint("PT B", true, "47463");

        layout.getPoint("PT M").setProtectingSignal(near.getName());
        layout.getPoint("PT M").setEntrySignals(java.util.Collections.singletonList(far.getName()));

        List<Edge> path = new LinkedList<>();
        path.add(layout.createEdge("PT A", "PT M"));
        path.add(layout.createEdge("PT M", "PT B"));

        Locomotive driving = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(driving.getName(), "PT A", false),
            "precondition - the train to dispatch must be placed");

        near.setState(Accessory.accessorySetting.GREEN);
        far.setState(Accessory.accessorySetting.GREEN);

        // WATCHED THROUGHOUT THE RUN
        final java.util.concurrent.atomic.AtomicBoolean watching = new java.util.concurrent.atomic.AtomicBoolean(true);
        final java.util.concurrent.atomic.AtomicBoolean exitRed = new java.util.concurrent.atomic.AtomicBoolean();
        final java.util.concurrent.atomic.AtomicBoolean entryRed = new java.util.concurrent.atomic.AtomicBoolean();

        Thread watcher = new Thread(() ->
        {
            while (watching.get())
            {
                if (near.isSwitched()) exitRed.set(true);
                if (far.isSwitched()) entryRed.set(true);

                try
                {
                    Thread.sleep(2);
                }
                catch (InterruptedException e)
                {
                    return;
                }
            }
        }, "OB-315 watcher");

        watcher.setDaemon(true);
        watcher.start();

        boolean ran;

        try
        {
            ran = layout.executePath(path, driving, 30, null);
        }
        finally
        {
            watching.set(false);
            watcher.join(1000);
        }

        assertTrue(ran, "the hand dispatch did not complete, so nothing above tests anything");

        assertFalse(exitRed.get(), "a train only passing PT M turned its exit guard red (OB-315, OB-316)");

        assertFalse(entryRed.get(), "a train only passing PT M turned its entry guard red");

        layout.getPoint("PT A").setLocomotive(null);
        layout.getPoint("PT M").setLocomotive(null);
        layout.getPoint("PT B").setLocomotive(null);
    }

    /**
     * A station a train only passes reads as passed while the train hands over from its claim to its journey (RSA30-C1).
     *
     * The hand-over writes the journey and then takes the claim away.  The guard's rule read the journey first and the
     * claim second, so a refresh whose two reads straddled the hand-over found the train in neither, read it as standing
     * at the station it was passing, and turned that station's exit guard red for the whole passage - OB-315's symptom,
     * through a race.  `pathHeldBy` reads the claims first for exactly this (RSA3-C1, RSA4-C3).
     *
     * The straddle made certain: the claim taken, and a journeys' map whose read by this thread performs the hand-over
     * itself, after the value is read.
     *
     * MUTATION: read the journey first again, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    public void testAPassedStationReadsAsPassedAcrossTheHandOver() throws Exception
    {
        for (String feedback : new String[] {"47464", "47465", "47466"})
        {
            if (!model.isFeedbackSet(feedback)) model.newFeedback(Integer.parseInt(feedback), null);
        }

        final Layout layout = new Layout(model);

        layout.createPoint("HO A", true, "47464");
        layout.createPoint("HO M", true, "47465");
        layout.createPoint("HO B", true, "47466");

        final List<Edge> path = new LinkedList<>();
        path.add(layout.createEdge("HO A", "HO M"));
        path.add(layout.createEdge("HO M", "HO B"));

        final Locomotive train = model.getLocByName(model.getLocList().get(0));

        java.lang.reflect.Field takingField = Layout.class.getDeclaredField("takingPath");

        takingField.setAccessible(true);

        @SuppressWarnings("unchecked")
        final java.util.Map<Locomotive, List<Edge>> taking = (java.util.Map<Locomotive, List<Edge>>) takingField.get(layout);

        // THE JOURNEYS, read by this thread: the value read, then the hand-over done - journey written, claim taken away
        final Thread reader = Thread.currentThread();

        final java.util.concurrent.ConcurrentHashMap<Locomotive, List<Edge>> journeys =
            new java.util.concurrent.ConcurrentHashMap<Locomotive, List<Edge>>()
        {
            @Override
            public List<Edge> get(Object key)
            {
                List<Edge> read = super.get(key);

                if (Thread.currentThread() == reader && train.equals(key) && taking.containsKey(train))
                {
                    super.put(train, path);

                    taking.remove(train);
                }

                return read;
            }
        };

        java.lang.reflect.Field journeysField = Layout.class.getDeclaredField("activeLocomotives");

        journeysField.setAccessible(true);

        java.lang.reflect.Field modifiers = java.lang.reflect.Field.class.getDeclaredField("modifiers");

        modifiers.setAccessible(true);
        modifiers.setInt(journeysField, journeysField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

        journeysField.set(layout, journeys);

        // THE CLAIM TAKEN
        taking.put(train, path);

        java.lang.reflect.Method passing = Layout.class.getDeclaredMethod("onlyPassing", Locomotive.class,
            org.traincontrol.automation.Point.class);

        passing.setAccessible(true);

        boolean passed = (boolean) passing.invoke(layout, train, layout.getPoint("HO M"));

        assertTrue(journeys.containsKey(train) || !taking.containsKey(train) || passed, "precondition: nothing happened");

        assertTrue(passed, "a train passing HO M, read while it handed over from its claim to its journey, was read as"
            + " standing there - so HO M's exit guard turns red in front of it (RSA30-C1)");
    }

    /**
     * A hand dispatch does NOT touch the signal of a train standing elsewhere (AU-B7, reversed).
     *
     * The test next door covers the DESTINATION of the dispatched train. This one is about a train
     * nobody dispatched, and it is the case the sweep exists for.
     *
     * While nothing is running the protection refresh is deliberately silent - trains are placed and
     * taken off by hand then, and driving real signals from a setup gesture is what that silence
     * exists to prevent. So a train placed at a protected platform while idle produces NO occupancy
     * change, and nothing will ever command that platform's signal on its own.
     *
     * **THE ASSERTION WAS REVERSED AND THIS PARAGRAPH WAS NOT** (D24-C6, corrected 2026-09-03).
     *
     * What follows described the fault as it stood before `OB-166`: `runLocomotives` and
     * `executeTimetableInternal` swept every protecting signal the moment they set `running`;
     * `executePath` - the diagram's right-click dispatch - became a full run in the MT-139 work and did
     * not inherit the sweep, so starting autonomy threw a platform red and hand-dispatching a different
     * train left it green with a train standing at it.  Adam's rule was quoted for it: "the same thing
     * should happen in manual operation vs auto".
     *
     * `OB-166` settled it the other way - a hand dispatch leaves a standing train's signal alone, which
     * is what this method is named for - and the sweep came off all three doors.  The assertion was
     * changed from `assertTrue` to `assertFalse` and this text was left describing the opposite.
     *
     * MUTATION: putting the `refreshAllProtectingSignals()` call back into `executePath` fails this
     * test, which is the direction that matters now.  (It said "removing", which was backwards:
     * removing it is what makes this pass.)
     */
    @Test
    public void testAHandDispatchLeavesAStandingTrainsSignalAlone() throws Exception
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

        // NOT PROTECTED, as of Adam's ruling of 2026-08-31: "Signals should only be touched when a
        // route activates."
        //
        // This required the opposite, and AU-B7 is the finding it came from. The sweep that made it
        // true also RELEASED - it commanded every empty protected platform green - so a hand dispatch
        // drove signals a route had set, which is OB-166. He ruled the sweep out, and this is what
        // that costs: a train placed by hand at a protected platform keeps whatever aspect its signal
        // already showed until a route activates over it.
        //
        // Kept and inverted rather than deleted, because the cost is the interesting part of the
        // record.
        assertFalse(far.isSwitched(),
            "a dispatch between two other points commanded the signal of a platform it has nothing "
            + "to do with - which is the sweep coming back");

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

    // ------------------------------------------------------------------------------------ FR-096: the entry guard

    /**
     * A train that arrives at a station as the end of its journey throws the station's entry guard red (FR-096).
     *
     * Adam, 2026-09-23: *"a signal that turns red after arrival at the final designation"*, and *"The next route
     * sets it green, so that is out of scope."*
     *
     * Through a real hand dispatch, as `testAHandDispatchedTrainProtectsItsDestination` above is: the claim is about
     * the call in `executePath` where the arrival is recorded, not about the method it calls.
     *
     * @throws Exception from the run
     */
    @Test
    public void testAnEntryGuardGoesRedWhenATrainArrivesThere() throws Exception
    {
        Layout layout = entryGuardLine();

        layout.getPoint("EG B").setEntrySignals(Arrays.asList(far.getName()));

        far.setState(Accessory.accessorySetting.GREEN);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "EG A", false), "precondition - the train must start at A");

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("EG A", "EG B"));

        try
        {
            assertTrue(layout.executePath(path, loc, 30, null), "the dispatch did not complete, so nothing below tests anything");

            assertTrue(far.isSwitched(), "a train arrived at B at the end of its journey and B's entry guard stayed GREEN");
        }
        finally
        {
            layout.getPoint("EG B").setLocomotive(null);
            layout.getPoint("EG A").setLocomotive(null);
        }
    }

    /**
     * A train that only PASSES a station leaves its entry guard alone - and throws the one at the station it stops at.
     *
     * "Final destination" is the end of the journey (FR-096).  The second half is the control: without it a guard that
     * never threw anything at all would pass the first.
     *
     * @throws Exception from the run
     */
    @Test
    public void testATrainPassingThroughLeavesTheEntryGuardAlone() throws Exception
    {
        Layout layout = entryGuardLine();

        layout.getPoint("EG B").setEntrySignals(Arrays.asList(near.getName()));
        layout.getPoint("EG C").setEntrySignals(Arrays.asList(far.getName()));

        near.setState(Accessory.accessorySetting.GREEN);
        far.setState(Accessory.accessorySetting.GREEN);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "EG A", false), "precondition - the train must start at A");

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("EG A", "EG B"));
        path.add(layout.getEdge("EG B", "EG C"));

        try
        {
            assertTrue(layout.executePath(path, loc, 30, null), "the dispatch did not complete, so nothing below tests anything");

            assertFalse(near.isSwitched(), "a train that only passed B threw B's entry guard red");

            assertTrue(far.isSwitched(), "CONTROL: the train stopped at C and C's entry guard stayed GREEN");
        }
        finally
        {
            layout.getPoint("EG C").setLocomotive(null);
            layout.getPoint("EG A").setLocomotive(null);
        }
    }

    /**
     * One entry-guard signal is written as a string, several as an array, and both come back (FR-096) - the shapes
     * the protecting signals use.
     */
    @Test
    public void testAnEntryGuardIsReadAndWrittenInBothShapes() throws Exception
    {
        String json = "{"
            + "\"points\": ["
            + "  {\"name\": \"ONE\", \"station\": true, \"s88\": 108, \"entrySignal\": \"" + near.getName() + "\"},"
            + "  {\"name\": \"TWO\", \"station\": true, \"s88\": 109,"
            + "   \"entrySignal\": [\"" + near.getName() + "\", \"" + far.getName() + "\"]}"
            + "],"
            + "\"edges\": [{\"start\": \"ONE\", \"end\": \"TWO\", \"length\": 1}],"
            + "\"minDelay\": 1, \"maxDelay\": 2, \"defaultLocSpeed\": 35}";

        Layout layout = Layout.fromJSON(json, model);

        assertTrue(layout.isValid(), "the layout is invalid: " + Layout.getLastError());

        assertEquals(layout.getPoint("ONE").getEntrySignals(), Arrays.asList(near.getName()));
        assertEquals(layout.getPoint("TWO").getEntrySignals(), Arrays.asList(near.getName(), far.getName()));

        assertTrue(layout.getPoint("ONE").toJSON().get("entrySignal") instanceof String,
            "one entry-guard signal was not written back as a bare string");
        assertTrue(layout.getPoint("TWO").toJSON().get("entrySignal") instanceof org.json.JSONArray,
            "two entry-guard signals were not written back as a list");

        assertTrue(layout.getPoint("TWO").getProtectingSignals().isEmpty(),
            "the entry guard was read into the protecting signals - two lists that are thrown at different moments");
    }

    /**
     * A line A - B - C, this class's own feedbacks 47461-47463, simulated and with no delays.
     */
    private Layout entryGuardLine() throws Exception
    {
        for (String feedback : new String[] {"47461", "47462", "47463"})
        {
            if (!model.isFeedbackSet(feedback)) model.newFeedback(Integer.parseInt(feedback), null);

            model.setFeedbackState(feedback, false);
        }

        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        layout.createPoint("EG A", true, "47461");
        layout.createPoint("EG B", true, "47462");
        layout.createPoint("EG C", true, "47463");

        layout.createEdge("EG A", "EG B");
        layout.createEdge("EG B", "EG C");

        return layout;
    }

    // ------------------------------------------------------------------------------------ OB-340: leaving a terminus

    /**
     * A train leaving a terminus is let past its exit guard (OB-340).
     *
     * Adam, 2026-10-12: *"when departing a terminus, autonomy should make its exit guard green.  otherwise, trains stopped
     * by that guard would remain stuck."*  No route out commands this guard - it stands on the station's own track - and
     * the journey setting off still claims the platform, so protection held the guard red over the train meant to leave.
     * The first edge out now sets it GREEN with its own accessories, and holds it as it holds them.
     *
     * Read where the train sets off (its departure functions, just before it is given its speed) and again at the first
     * station on the way, the platform still held by the journey and the first edge still held: GREEN, and held GREEN, so
     * a route may not turn it red in front of the train.  After the run the platform is free, and the guard green.
     *
     * MUTATION: leave the guards out of what the first edge sets, and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testATrainLeavingATerminusTurnsItsExitGuardGreen() throws Exception
    {
        final Layout layout = terminusLine(true);

        layout.getPoint("TX T").setProtectingSignal(near.getName());

        final Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "TX T", false), "precondition - the train must stand at the terminus");

        // RED, as protection holds it over the train standing there
        near.setState(Accessory.accessorySetting.RED);

        final String[] atDeparture = {"not read"};
        final String[] onTheWay = {"not read"};
        final Object[] heldAt = {"not read"};
        final Object[] heldAfter = {"not read"};

        final java.util.function.Consumer<Locomotive> startWas = loc.hasCallback(Layout.CB_ROUTE_START)
            ? loc.getCallback(Layout.CB_ROUTE_START) : l -> { };

        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            atDeparture[0] = aspectOver(layout, "TX T", loc, near);

            heldAt[0] = layout.getActiveAccSettings().get(near);

            startWas.accept(l);
        });

        // The first start is the run being registered; the second, the train at TX M
        final java.util.concurrent.atomic.AtomicInteger starts = new java.util.concurrent.atomic.AtomicInteger();

        layout.setCallback("OB-340 watcher", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started) || starts.incrementAndGet() != 2) return null;

            onTheWay[0] = aspectOver(layout, "TX T", loc, near);

            heldAfter[0] = layout.getActiveAccSettings().get(near);

            return null;
        });

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("TX T", "TX M"));
        path.add(layout.getEdge("TX M", "TX B"));

        try
        {
            assertTrue(layout.executePath(path, loc, 30, null), "the dispatch did not complete, so nothing below tests anything");

            assertEquals(atDeparture[0], "GREEN, the train holding the platform",
                "a train setting off from a terminus found its exit guard RED - no route out commands it, and the train's"
                + " own claim on the platform held it red, so a guard that stops trains keeps this one there (OB-340)");

            assertEquals(onTheWay[0], "GREEN, the train holding the platform",
                "the exit guard of the terminus the train is leaving went back to red while its journey still held the"
                + " platform (OB-340)");

            assertEquals(heldAt[0], Boolean.FALSE,
                "the journey leaving the terminus does not hold its exit guard GREEN, so a route may turn it red in front"
                + " of the train (OB-340)");

            assertEquals(heldAfter[0], Boolean.FALSE, "the journey's first edge, still held, does not hold the exit guard"
                + " GREEN with its own accessories (OB-340)");

            assertFalse(near.isSwitched(), "the terminus is free after the run, and its exit guard is not green");
        }
        finally
        {
            loc.setCallback(Layout.CB_ROUTE_START, startWas);

            layout.getPoint("TX B").setLocomotive(null);
            layout.getPoint("TX T").setLocomotive(null);
        }
    }

    /**
     * A train standing at a terminus keeps its exit guard red - the control for the claim above (OB-340).
     *
     * A train run in to stand there turns the guard red as its path claims the platform, and leaves it red once it has
     * arrived: only the first edge out of it sets the guard green.  The guard starts GREEN, as a departure leaves it over
     * the empty platform, so this is also the next train in after one has left.
     *
     * MUTATION: let the last edge in set the guards of the station it reaches green, and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testATrainArrivingAtATerminusTurnsItsExitGuardRed() throws Exception
    {
        final Layout layout = terminusLine(true);

        layout.getPoint("TX T").setProtectingSignal(near.getName());

        final Locomotive loc = model.getLocByName(model.getLocList().get(0));

        final Locomotive.locDirection facing = loc.getDirection();

        assertTrue(layout.moveLocomotive(loc.getName(), "TX B", false), "precondition - the train must start at B");

        // GREEN, as a train that left the terminus left it
        near.setState(Accessory.accessorySetting.GREEN);

        final String[] onTheWayIn = {"not read"};

        layout.setCallback("OB-340 arrival watcher", (edges, l, started) ->
        {
            if (Boolean.TRUE.equals(started) && "not read".equals(onTheWayIn[0]))
            {
                onTheWayIn[0] = near.isSwitched() ? "RED" : "GREEN";
            }

            return null;
        });

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("TX B", "TX M"));
        path.add(layout.getEdge("TX M", "TX T"));

        try
        {
            assertTrue(layout.executePath(path, loc, 30, null), "the dispatch did not complete, so nothing below tests anything");

            assertEquals(onTheWayIn[0], "RED", "a train bound for the terminus left its exit guard GREEN on the way in");

            assertEquals(layout.getPoint("TX T").getCurrentLocomotive(), loc, "precondition - the train must stand there");

            assertTrue(near.isSwitched(), "a train standing at the terminus at the end of its journey left its exit guard"
                + " GREEN - only a train leaving it may (OB-340)");
        }
        finally
        {
            loc.setDirection(facing);

            layout.getPoint("TX T").setLocomotive(null);
            layout.getPoint("TX B").setLocomotive(null);
        }
    }

    /**
     * A train leaving a through station turns its exit guards green too (OB-340; Adam, 2026-10-12: *"extend the rule to
     * MAY TURN stations too.  just make the exit guard green when something departs."*).
     *
     * Its exit signal is its route's to set, green as the route is claimed, as before; a guard no route out commands, which
     * the journey's own claim held red, now turns green with the first edge's accessories, is held GREEN with them, and
     * stays green asked again on the way.
     *
     * MUTATION: leave the guards out of what the first edge sets, or let protection turn red a signal a path holds green,
     * and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testAThroughStationsExitGuardsTurnGreenAsATrainLeaves() throws Exception
    {
        final Layout layout = terminusLine(false);

        // near: the exit signal the route out commands; far: a guard no route out commands
        layout.getPoint("TX T").setProtectingSignals(Arrays.asList(near.getName(), far.getName()));

        layout.getEdge("TX T", "TX M").addConfigCommand(near.getName(), Accessory.accessorySetting.GREEN);

        final Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "TX T", false), "precondition - the train must stand at the station");

        near.setState(Accessory.accessorySetting.RED);
        far.setState(Accessory.accessorySetting.RED);

        final String[] atDeparture = {"not read"};
        final String[] onTheWay = {"not read"};
        final Object[] farHeld = {"not read"};
        final String[] farAskedAgain = {"not read"};

        final java.util.function.Consumer<Locomotive> startWas = loc.hasCallback(Layout.CB_ROUTE_START)
            ? loc.getCallback(Layout.CB_ROUTE_START) : l -> { };

        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            atDeparture[0] = aspects();

            farHeld[0] = layout.getActiveAccSettings().get(far);

            startWas.accept(l);
        });

        final java.util.concurrent.atomic.AtomicInteger starts = new java.util.concurrent.atomic.AtomicInteger();

        layout.setCallback("OB-340 through watcher", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started) || starts.incrementAndGet() != 2) return null;

            onTheWay[0] = aspects();

            // every guard asked again, the journey still holding the platform
            layout.refreshAllProtectingSignals();

            farAskedAgain[0] = far.isSwitched() ? "RED" : "GREEN";

            return null;
        });

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("TX T", "TX M"));
        path.add(layout.getEdge("TX M", "TX B"));

        try
        {
            assertTrue(layout.executePath(path, loc, 30, null), "the dispatch did not complete, so nothing below tests anything");

            assertEquals(atDeparture[0], "near GREEN, far GREEN", "a train setting off from a through station did not"
                + " find both its exit guards GREEN: the exit signal its route commands, and the guard no route commands,"
                + " which the journey's own claim held red (OB-340)");

            assertEquals(onTheWay[0], "near GREEN, far GREEN", "a through station's exit guards went back to red while the"
                + " journey leaving it still held the platform (OB-340)");

            assertEquals(farHeld[0], Boolean.FALSE, "the journey leaving a through station does not hold its exit guard"
                + " GREEN, so a route may turn it red in front of the train (OB-340)");

            assertEquals(farAskedAgain[0], "GREEN", "a through station's guard, asked again while the journey leaving it"
                + " still held the platform, went RED in front of the train (OB-340)");
        }
        finally
        {
            loc.setCallback(Layout.CB_ROUTE_START, startWas);

            layout.getPoint("TX B").setLocomotive(null);
            layout.getPoint("TX T").setLocomotive(null);
        }
    }

    /**
     * A train leaving a station trains MAY turn at, forward from its plain copy, turns the station's exit guard green
     * (OB-340): the rule is the departure's, from any station, whichever copy the train stands on.
     *
     * MUTATION: set only a terminus copy's guards with the first edge, and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testAMayTurnStationsForwardDepartureTurnsItsExitGuardGreen() throws Exception
    {
        assertEquals(leaveACopy(false), "GREEN, the train holding the platform", "a train leaving a may-turn station"
            + " forward found its exit guard RED (OB-340)");
    }

    /**
     * A train leaving a must-turn station from a copy that is not a terminus - the turning copy of an arrival side that is
     * barred, built as a reversing point - turns the station's exit guard green (OB-340).
     *
     * MUTATION: set only a terminus copy's guards with the first edge, and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testAMustTurnStationsBarredCopyDepartureTurnsItsExitGuardGreen() throws Exception
    {
        assertEquals(leaveACopy(true), "GREEN, the train holding the platform", "a train leaving a must-turn station from"
            + " its barred side's copy found its exit guard RED (OB-340)");
    }

    /**
     * An exit guard a terminus shares with another platform turns GREEN for the train leaving the terminus, though another
     * train stands on that platform, and protection leaves it green while the first edge out holds it - asked again by an
     * occupancy change there on the way; once the journey has given its track back it is RED for the train standing
     * there (OB-340; Adam's ruling of 2026-10-12 on a shared guard).
     *
     * MUTATION: leave the guards out of what the first edge sets, and the first claim fails; let protection turn red a
     * signal a path holds green, and the second does.
     *
     * @throws Exception from the run
     */
    @Test
    public void testAnExitGuardSharedWithAnotherTrainsPlatformIsGreenWhileTheTrainLeaves() throws Exception
    {
        assertEquals(leaveBesideAnotherTrain(false), Arrays.asList("GREEN, the train holding the platform",
            "GREEN, the train holding the platform", "RED, the train holding the platform"),
            "a shared exit guard, read as the train leaves the terminus, on its way with the other platform's occupancy"
            + " asked again, and over the other train once the run is over: GREEN, GREEN, then RED (OB-340)");
    }

    /**
     * The exit signal a train's route out turns green, shared with another platform a train stands on, stays GREEN while
     * the route holds it, asked again on the way, and is RED for the standing train once the run is over (OB-340).  Adam's
     * own railway pairs signals 63 and 64 with TopMainR0, R1 and R2, and only the routes out of those platforms set them.
     *
     * MUTATION: let protection turn red a signal a path holds green, and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testAnExitSignalSharedWithAnOccupiedPlatformStaysGreenForTheTrainLeaving() throws Exception
    {
        assertEquals(leaveBesideAnotherTrain(true), Arrays.asList("GREEN, the train holding the platform",
            "GREEN, the train holding the platform", "RED, the train holding the platform"), "the exit signal the route"
            + " out set GREEN, shared with an occupied platform, read as the train sets off, on its way with the other"
            + " platform's occupancy asked again, and over the other train once the run is over (OB-340)");
    }

    /**
     * A train leaves the terminus TX T while another stands at TX P, both guarded by `near` (OB-340).
     *
     * @param routeCommandsIt whether the first edge out commands `near` green, as a route out does its exit signal
     * @return the guard as the train sets off; at TX M, once the other train has been taken off P and put back; and over
     *         the other train once the run is over - each as `aspectOver` reads it
     */
    private List<String> leaveBesideAnotherTrain(boolean routeCommandsIt) throws Exception
    {
        final Layout layout = terminusLine(true);

        layout.getPoint("TX T").setProtectingSignal(near.getName());
        layout.getPoint("TX P").setProtectingSignal(near.getName());

        if (routeCommandsIt) layout.getEdge("TX T", "TX M").addConfigCommand(near.getName(), Accessory.accessorySetting.GREEN);

        final Locomotive leaving = model.getLocByName(model.getLocList().get(0));
        final Locomotive standing = model.getLocByName(model.getLocList().get(1));

        assertNotEquals(leaving.getName(), standing.getName(), "this test needs two different locomotives");

        assertTrue(layout.moveLocomotive(leaving.getName(), "TX T", false), "precondition - a train must stand at the terminus");
        assertTrue(layout.moveLocomotive(standing.getName(), "TX P", false), "precondition - another must stand at P");

        near.setState(Accessory.accessorySetting.RED);

        final String[] read = {"not read", "not read", "not read"};

        final java.util.function.Consumer<Locomotive> startWas = leaving.hasCallback(Layout.CB_ROUTE_START)
            ? leaving.getCallback(Layout.CB_ROUTE_START) : l -> { };

        leaving.setCallback(Layout.CB_ROUTE_START, l ->
        {
            read[0] = aspectOver(layout, "TX T", leaving, near);

            startWas.accept(l);
        });

        final java.util.concurrent.atomic.AtomicInteger starts = new java.util.concurrent.atomic.AtomicInteger();

        // At TX M, the first edge still held: P's occupancy changes and back, which asks protection again
        layout.setCallback("OB-340 shared watcher", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started) || starts.incrementAndGet() != 2) return null;

            layout.getPoint("TX P").setLocomotive(null);
            layout.getPoint("TX P").setLocomotive(standing);

            read[1] = aspectOver(layout, "TX T", leaving, near);

            return null;
        });

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("TX T", "TX M"));
        path.add(layout.getEdge("TX M", "TX B"));

        try
        {
            assertTrue(layout.executePath(path, leaving, 30, null), "the dispatch did not complete, so nothing tests"
                + " anything");

            read[2] = aspectOver(layout, "TX P", standing, near);
        }
        finally
        {
            leaving.setCallback(Layout.CB_ROUTE_START, startWas);

            layout.getPoint("TX B").setLocomotive(null);
            layout.getPoint("TX T").setLocomotive(null);
            layout.getPoint("TX P").setLocomotive(null);
        }

        return Arrays.asList(read);
    }

    /**
     * With Atomic Routes off - Adam's own setting - a guard shared with a platform another train stands at is GREEN as the
     * train leaves and still GREEN while the first edge out holds it, and RED over the other train once the tail has
     * cleared that edge: the hold lapses there, and the start, given back at once, asks it again (OB-340).
     *
     * A journey of four edges, none measured, so the tail clears the first edge when the head finishes the second.
     *
     * MUTATION: leave the start unreleased where the tail clears the first edge, so nothing asks its guards then, and
     * this fails.  (Keeping the first edge's hold past its clearing does not: non-atomic routes give the edge itself back
     * before the start, so nothing holds the guard either way - the claim below, with Atomic Routes on, is that one's.)
     *
     * @throws Exception from the run
     */
    @Test
    public void testASharedGuardGoesBackToRedAsTheTailClearsTheFirstEdgeWithAtomicRoutesOff() throws Exception
    {
        assertEquals(leaveBesideAnotherTrainOverFourEdges(false), Arrays.asList("GREEN, the train holding the platform",
            "GREEN, the train holding the platform", "RED, the train holding the platform",
            "RED, the train holding the platform"), "a shared exit guard with Atomic Routes off: as the train leaves, at"
            + " the end of its first edge, once its tail has cleared that edge, and after an occupancy change at the other"
            + " platform (OB-340)");
    }

    /**
     * With Atomic Routes on, the first edge's hold on a shared guard lapses when the tail has cleared it, but nothing asks
     * the guard then: it stays GREEN over the train at the other platform until the next change of occupancy there turns
     * it RED (OB-340).
     *
     * MUTATION: keep the first edge's hold once its tail has cleared it, or record the clearing only where non-atomic
     * routes give the edge back, and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testASharedGuardIsAskedAgainOnceTheTailHasClearedTheFirstEdgeWithAtomicRoutesOn() throws Exception
    {
        assertEquals(leaveBesideAnotherTrainOverFourEdges(true), Arrays.asList("GREEN, the train holding the platform",
            "GREEN, the train holding the platform", "GREEN, the train holding the platform",
            "RED, the train holding the platform"), "a shared exit guard with Atomic Routes on: as the train leaves, at"
            + " the end of its first edge, once its tail has cleared that edge, and after an occupancy change at the other"
            + " platform (OB-340)");
    }

    /**
     * A train leaves the terminus FE T over four edges - FE M, FE N, FE O, FE B - while another stands at FE P, both
     * guarded by `near`; this class's own feedbacks 47481-47486 (OB-340).
     *
     * @param atomic Atomic Routes
     * @return the guard as the train sets off and at FE M, beside the train leaving; then at FE N, where the tail has
     *         cleared the first edge, and there once the other train has been taken off P and put back, beside that one
     */
    private List<String> leaveBesideAnotherTrainOverFourEdges(boolean atomic) throws Exception
    {
        String[] names = {"FE T", "FE M", "FE N", "FE O", "FE B", "FE P"};

        final Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);
        layout.setAtomicRoutes(atomic);

        for (int i = 0; i < names.length; i++)
        {
            String feedback = Integer.toString(47481 + i);

            if (!model.isFeedbackSet(feedback)) model.newFeedback(47481 + i, null);

            model.setFeedbackState(feedback, false);

            layout.createPoint(names[i], true, feedback);
        }

        layout.getPoint("FE T").setTerminus(true);
        layout.getPoint("FE T").setProtectingSignal(near.getName());
        layout.getPoint("FE P").setProtectingSignal(near.getName());

        List<Edge> path = new LinkedList<>();

        for (int i = 0; i < 4; i++) path.add(layout.createEdge(names[i], names[i + 1]));

        final Locomotive leaving = model.getLocByName(model.getLocList().get(0));
        final Locomotive standing = model.getLocByName(model.getLocList().get(1));

        assertNotEquals(leaving.getName(), standing.getName(), "this test needs two different locomotives");

        assertTrue(layout.moveLocomotive(leaving.getName(), "FE T", false), "precondition - a train must stand at the terminus");
        assertTrue(layout.moveLocomotive(standing.getName(), "FE P", false), "precondition - another must stand at P");

        near.setState(Accessory.accessorySetting.RED);

        final String[] read = {"not read", "not read", "not read", "not read"};

        final java.util.function.Consumer<Locomotive> startWas = leaving.hasCallback(Layout.CB_ROUTE_START)
            ? leaving.getCallback(Layout.CB_ROUTE_START) : l -> { };

        leaving.setCallback(Layout.CB_ROUTE_START, l ->
        {
            read[0] = aspectOver(layout, "FE T", leaving, near);

            startWas.accept(l);
        });

        final java.util.concurrent.atomic.AtomicInteger starts = new java.util.concurrent.atomic.AtomicInteger();

        // The second start is the head at FE M; the third, the head at FE N, the tail past the first edge
        layout.setCallback("OB-340 four-edge watcher", (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started)) return null;

            int start = starts.incrementAndGet();

            if (start == 2) read[1] = aspectOver(layout, "FE T", leaving, near);

            if (start == 3)
            {
                read[2] = aspectOver(layout, "FE P", standing, near);

                layout.getPoint("FE P").setLocomotive(null);
                layout.getPoint("FE P").setLocomotive(standing);

                read[3] = aspectOver(layout, "FE P", standing, near);
            }

            return null;
        });

        try
        {
            assertTrue(layout.executePath(path, leaving, 30, null), "the dispatch did not complete, so nothing tests"
                + " anything");
        }
        finally
        {
            leaving.setCallback(Layout.CB_ROUTE_START, startWas);

            for (String name : names) layout.getPoint(name).setLocomotive(null);
        }

        return Arrays.asList(read);
    }

    /** What every reading of `raceTheRefresh` is: GREEN and held GREEN as the train sets off, and GREEN after the refresh */
    private static final String STAYS_GREEN = "GREEN, the train holding the platform, held GREEN; after the other thread's"
        + " refresh: GREEN, the train holding the platform";

    /** How long any one step of `raceTheRefresh` may wait, so that a wrong answer is a failure and never a hang */
    private static final long RACE_BOUND_MS = 20000;

    /**
     * A protection refresh on another train's thread that has read the holds BEFORE a departure claims its route does not
     * turn the leaving train's exit guard RED after the departure has set it GREEN, with Atomic Routes off - nor does the
     * same refresh made after the claim (RSA67-C1, OB-340).  The refresh reads the holds and commands the signal as one
     * step against the departure's GREEN, so it either commands first and the GREEN lands last, or reads the hold.
     *
     * MUTATION: take the signal's lock out of `Layout.refreshOneSignal`, or out of the departure's GREEN in
     * `configureAndLockPath`, and the forced reading goes RED after the refresh.
     *
     * @throws Exception from the run
     */
    @Test
    public void testARefreshStraddlingADeparturesClaimLeavesItsGuardGreenWithAtomicRoutesOff() throws Exception
    {
        assertEquals(Arrays.asList(raceTheRefresh(true, false), raceTheRefresh(false, false)), Arrays.asList(
            "forced: " + STAYS_GREEN, "after the claim: " + STAYS_GREEN), "a guard shared with another platform, the"
            + " leaving train's exit guard, as it sets off and once another thread's refresh of it is done - that refresh"
            + " forced to read the holds before the claim and to finish after the departure, and then made after the claim"
            + " (RSA67-C1)");
    }

    /**
     * The same with Atomic Routes on (RSA67-C1, OB-340).
     *
     * MUTATION: as `testARefreshStraddlingADeparturesClaimLeavesItsGuardGreenWithAtomicRoutesOff`.
     *
     * @throws Exception from the run
     */
    @Test
    public void testARefreshStraddlingADeparturesClaimLeavesItsGuardGreenWithAtomicRoutesOn() throws Exception
    {
        assertEquals(Arrays.asList(raceTheRefresh(true, true), raceTheRefresh(false, true)), Arrays.asList(
            "forced: " + STAYS_GREEN, "after the claim: " + STAYS_GREEN), "a guard shared with another platform, the"
            + " leaving train's exit guard, as it sets off and once another thread's refresh of it is done - that refresh"
            + " forced to read the holds before the claim and to finish after the departure, and then made after the claim"
            + " (RSA67-C1)");
    }

    /**
     * A train leaves RR S for RR N by RR M while another thread refreshes `near`, the guard RR S shares with RR P; this
     * class's own feedbacks 47491-47496 (RSA67-C1).
     *
     * Another train is under way on one held edge, RR X to RR Y, put among the journeys directly, so that a refresh walks
     * that edge's commands as it reads the holds.  Forced, the refresh runs on a thread of its own before the departure,
     * and that walk waits there, once, until the departure has been handed over - its guard set GREEN, its speed not yet
     * given - or until the departure is waiting for a monitor the refresh holds: so the refresh has read the holds before
     * the claim, and finishes after the GREEN unless the departure waits for it.  Unforced, the same refresh is made on
     * the departing thread at the hand-over, after the claim.
     *
     * @param forced whether the refresh straddles the claim
     * @param atomic Atomic Routes
     * @return the guard as `aspectOver` reads it, with the setting the paths hold it at, as the train sets off; and as
     *         `aspectOver` reads it once the refresh is done
     */
    private String raceTheRefresh(final boolean forced, boolean atomic) throws Exception
    {
        String[] names = {"RR S", "RR M", "RR N", "RR P", "RR X", "RR Y"};

        final Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);
        layout.setAtomicRoutes(atomic);

        for (int i = 0; i < names.length; i++)
        {
            String feedback = Integer.toString(47491 + i);

            if (!model.isFeedbackSet(feedback)) model.newFeedback(47491 + i, null);

            model.setFeedbackState(feedback, false);

            layout.createPoint(names[i], true, feedback);
        }

        layout.getPoint("RR S").setProtectingSignal(near.getName());
        layout.getPoint("RR P").setProtectingSignal(near.getName());

        List<Edge> path = new LinkedList<>();
        path.add(layout.createEdge("RR S", "RR M"));
        path.add(layout.createEdge("RR M", "RR N"));

        final Locomotive leaving = model.getLocByName(model.getLocList().get(0));
        final Locomotive other = model.getLocByName(model.getLocList().get(1));

        assertNotEquals(leaving.getName(), other.getName(), "this test needs two different locomotives");

        layout.getPoint("RR S").setLocomotive(leaving);

        final java.util.concurrent.CountDownLatch refreshIsReading = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch letTheRefreshGo = new java.util.concurrent.CountDownLatch(1);
        final Thread[] refresher = {null};
        final String[] read = {"not read", "not read", "not refreshed"};

        // The other train's one held edge: on the refresh's thread, the first walk of its commands waits
        Edge held = new Edge(layout.getPoint("RR X"), layout.getPoint("RR Y"))
        {
            @Override
            public java.util.Map<String, Accessory.accessorySetting> getConfigCommands()
            {
                if (Thread.currentThread() == refresher[0] && refreshIsReading.getCount() > 0)
                {
                    refreshIsReading.countDown();

                    try
                    {
                        letTheRefreshGo.await(RACE_BOUND_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                }

                return super.getConfigCommands();
            }
        };

        held.setOccupied();

        journeys(layout).put(other, Arrays.asList(held));

        near.setState(Accessory.accessorySetting.RED);

        final java.util.function.Consumer<Locomotive> startWas = leaving.hasCallback(Layout.CB_ROUTE_START)
            ? leaving.getCallback(Layout.CB_ROUTE_START) : l -> { };

        leaving.setCallback(Layout.CB_ROUTE_START, l ->
        {
            java.util.Map<Accessory, Boolean> holds = layout.getActiveAccSettings();

            read[0] = aspectOver(layout, "RR S", leaving, near) + ", " + (!holds.containsKey(near) ? "not held"
                : holds.get(near) == null ? "held both ways" : holds.get(near) ? "held RED" : "held GREEN");

            if (forced)
            {
                letTheRefreshGo.countDown();

                try
                {
                    refresher[0].join(RACE_BOUND_MS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
            else
            {
                read[2] = refreshOn(layout, "RR P");
            }

            read[1] = aspectOver(layout, "RR S", leaving, near);

            startWas.accept(l);
        });

        Thread watcher = null;

        boolean ran;

        try
        {
            if (forced)
            {
                refresher[0] = new Thread(() -> read[2] = refreshOn(layout, "RR P"), "RSA67-C1 refresh");
                refresher[0].setDaemon(true);
                refresher[0].start();

                assertTrue(refreshIsReading.await(RACE_BOUND_MS, java.util.concurrent.TimeUnit.MILLISECONDS),
                    "precondition: the other thread's refresh must be reading the holds before the claim");

                // Lets the refresh go once the departure is waiting for it, where it can never reach its hand-over
                final Thread departing = Thread.currentThread();

                watcher = new Thread(() ->
                {
                    long until = System.currentTimeMillis() + RACE_BOUND_MS;

                    while (letTheRefreshGo.getCount() > 0 && System.currentTimeMillis() < until)
                    {
                        if (waitsForAMonitorHeldBy(departing, refresher[0]))
                        {
                            letTheRefreshGo.countDown();
                        }

                        try
                        {
                            Thread.sleep(1);
                        }
                        catch (InterruptedException e)
                        {
                            return;
                        }
                    }
                }, "RSA67-C1 watcher");

                watcher.setDaemon(true);
                watcher.start();
            }

            ran = layout.executePath(path, leaving, 30, null);
        }
        finally
        {
            letTheRefreshGo.countDown();

            leaving.setCallback(Layout.CB_ROUTE_START, startWas);

            if (watcher != null)
            {
                watcher.interrupt();
                watcher.join(RACE_BOUND_MS);
            }

            if (refresher[0] != null) refresher[0].join(RACE_BOUND_MS);

            journeys(layout).remove(other);

            held.setUnoccupied();

            for (String name : names) layout.getPoint(name).setLocomotive(null);
        }

        assertTrue(ran, "the dispatch did not complete, so nothing tests anything");

        assertEquals(read[2], "refreshed", "the other thread's refresh must have run to its end");

        return (forced ? "forced: " : "after the claim: ") + read[0] + "; after the other thread's refresh: " + read[1];
    }

    /**
     * A protection refresh of one Point, as an occupancy change there makes it (RSA67-C1).
     *
     * @param layout the railway
     * @param point the Point
     * @return "refreshed", or what it threw
     */
    private static String refreshOn(Layout layout, String point)
    {
        try
        {
            java.lang.reflect.Method refresh = Layout.class.getDeclaredMethod("refreshProtectingSignal",
                org.traincontrol.automation.Point.class);

            refresh.setAccessible(true);
            refresh.invoke(layout, layout.getPoint(point));

            return "refreshed";
        }
        catch (Exception e)
        {
            return "the refresh threw " + e;
        }
    }

    /**
     * The railway's journeys under way, so that a test can put a train among them without running it (RSA67-C1).
     *
     * @param layout the railway
     * @return its live map of each train under way to its path
     */
    @SuppressWarnings("unchecked")
    private static java.util.Map<Locomotive, List<Edge>> journeys(Layout layout) throws Exception
    {
        java.lang.reflect.Field active = Layout.class.getDeclaredField("activeLocomotives");

        active.setAccessible(true);

        return (java.util.Map<Locomotive, List<Edge>>) active.get(layout);
    }

    /**
     * Whether one thread is BLOCKED entering a monitor another thread holds - the departure waiting for the refresh, which
     * is the fix's shape and which the forcing must let through rather than deadlock on (RSA67-C1).
     *
     * @param waiting the thread that may be waiting
     * @param holding the thread that may hold what it waits for
     * @return true when it does
     */
    private static boolean waitsForAMonitorHeldBy(Thread waiting, Thread holding)
    {
        java.lang.management.ThreadMXBean threads = java.lang.management.ManagementFactory.getThreadMXBean();

        java.lang.management.ThreadInfo blocked = threads.getThreadInfo(new long[] {waiting.getId()}, false, false)[0];

        if (blocked == null || blocked.getThreadState() != Thread.State.BLOCKED || blocked.getLockInfo() == null) return false;

        java.lang.management.ThreadInfo holder = threads.getThreadInfo(new long[] {holding.getId()}, true, false)[0];

        if (holder == null) return false;

        for (java.lang.management.MonitorInfo monitor : holder.getLockedMonitors())
        {
            if (monitor.getIdentityHashCode() == blocked.getLockInfo().getIdentityHashCode()) return true;
        }

        return false;
    }

    /**
     * Where the first edge out commands its own station's exit guard, the edge's setting stands (OB-340): one commanding it
     * RED - under one spelling of its name where the station uses the other, one accessory - leaves it RED, held RED.
     *
     * MUTATION: set every guard of the station green whatever the edge commands, and this fails.
     *
     * @throws Exception from the run
     */
    @Test
    public void testTheFirstEdgesOwnSettingOfAGuardStands() throws Exception
    {
        final Layout layout = terminusLine(true);

        final String otherSpelling = near.getName().startsWith("Switch ")
            ? "Signal " + near.getName().substring("Switch ".length())
            : "Switch " + near.getName().substring(near.getName().indexOf(' ') + 1);

        assertEquals(model.getAccessoryByName(otherSpelling), near, "precondition: both spellings must name one accessory");

        layout.getPoint("TX T").setProtectingSignal(otherSpelling);

        layout.getEdge("TX T", "TX M").addConfigCommand(near.getName(), Accessory.accessorySetting.RED);

        final Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "TX T", false), "precondition - the train must stand at the terminus");

        // GREEN, so a red at the departure is the edge's doing
        near.setState(Accessory.accessorySetting.GREEN);

        final Object[] read = {"not read", "not read"};

        final java.util.function.Consumer<Locomotive> startWas = loc.hasCallback(Layout.CB_ROUTE_START)
            ? loc.getCallback(Layout.CB_ROUTE_START) : l -> { };

        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            read[0] = near.isSwitched() ? "RED" : "GREEN";
            read[1] = layout.getActiveAccSettings().get(near);

            startWas.accept(l);
        });

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("TX T", "TX M"));
        path.add(layout.getEdge("TX M", "TX B"));

        try
        {
            assertTrue(layout.executePath(path, loc, 30, null), "the dispatch did not complete, so nothing below tests anything");

            assertEquals(Arrays.asList(read), Arrays.asList("RED", Boolean.TRUE), "a first edge commanding its own"
                + " station's exit guard RED, read as the train sets off: the guard and the setting it is held at (OB-340)");
        }
        finally
        {
            loc.setCallback(Layout.CB_ROUTE_START, startWas);

            layout.getPoint("TX B").setLocomotive(null);
            layout.getPoint("TX T").setLocomotive(null);
        }
    }

    /**
     * A departure given back unrun - a stop ordered while its route was being set - leaves the station's exit guard RED
     * over the train still standing there, though the first edge had already set it GREEN (OB-340): the route is given back
     * and the start reserved again, which asks the guard.
     *
     * MUTATION: give the route back without reserving the start again, and this fails.
     *
     * @throws Exception from the dispatch
     */
    @Test
    public void testADepartureGivenBackForAStopLeavesItsGuardRed() throws Exception
    {
        assertEquals(failADeparture(false), Arrays.asList("GREEN", "RED, the train holding the platform"), "the exit"
            + " guard while the route was being set, and after a stop gave it back (OB-340)");
    }

    /**
     * A departure that fails while its route is being set - an accessory that cannot be commanded - leaves the station's
     * exit guard RED over the train still standing there, though the first edge had already set it GREEN (OB-340): what
     * was taken is released and the start reserved again, which asks the guard.
     *
     * MUTATION: release a failed route without reserving the start again, and this fails.
     *
     * @throws Exception from the dispatch
     */
    @Test
    public void testADepartureThatFailsWhileItsRouteIsSetLeavesItsGuardRed() throws Exception
    {
        assertEquals(failADeparture(true), Arrays.asList("GREEN", "RED, the train holding the platform"), "the exit"
            + " guard while the route was being set, and after the route failed (OB-340)");
    }

    /**
     * A departure from the terminus TX T, guarded by `near`, that does not happen: while its second edge is being set up -
     * the first edge's accessories and guards set - a stop is ordered, or the edge cannot be configured (OB-340).
     *
     * @param fails true for an edge that cannot be configured, false for a stop
     * @return the guard as the second edge is set up, and after the departure as `aspectOver` reads it
     */
    private List<String> failADeparture(boolean fails) throws Exception
    {
        final Layout layout = terminusLine(true);

        layout.getPoint("TX T").setProtectingSignal(near.getName());

        final Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "TX T", false), "precondition - the train must stand at the terminus");

        near.setState(Accessory.accessorySetting.RED);

        final String[] read = {"not read", "not read"};

        final java.util.concurrent.atomic.AtomicBoolean asked = new java.util.concurrent.atomic.AtomicBoolean();

        // Asked once its claim is taken - the configuration preview before it is let through
        Edge second = new Edge(layout.getPoint("TX M"), layout.getPoint("TX B"))
        {
            @Override
            public java.util.Map<String, Accessory.accessorySetting> getConfigCommands()
            {
                if (layout.isAlreadyUnderway(loc) && asked.compareAndSet(false, true))
                {
                    read[0] = near.isSwitched() ? "RED" : "GREEN";

                    if (fails) throw new IllegalStateException("the accessory went away mid-lock");

                    layout.stopEveryTrainWhereItIs();
                }

                return super.getConfigCommands();
            }
        };

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge("TX T", "TX M"));
        path.add(second);

        try
        {
            boolean ran;

            try
            {
                ran = layout.executePath(path, loc, 30, null);
            }
            catch (RuntimeException failed)
            {
                ran = false;
            }

            assertFalse(ran, "precondition: the departure must not happen");

            read[1] = aspectOver(layout, "TX T", loc, near);
        }
        finally
        {
            layout.getPoint("TX T").setLocomotive(null);
        }

        return Arrays.asList(read);
    }

    /**
     * Two copies of one station square, CP 1 and CP 2, each a way out to CP M and on to CP B, both guarded by `near`; a
     * train leaves from CP 2 or CP 1 - whichever is not a terminus - and the guard is read where it sets off (OB-340).
     *
     * @param mustTurn true: CP 1 a terminus and CP 2 the turning copy of a barred side, a reversing point and no
     *        destination - the train leaves from CP 2; false: CP 1 a plain copy and CP 2 the turning one - the train leaves
     *        forward from CP 1
     * @return the guard's aspect where the train sets off, as `aspectOver` reads it
     */
    private String leaveACopy(boolean mustTurn) throws Exception
    {
        for (String feedback : new String[] {"47475", "47476", "47477", "47478"})
        {
            if (!model.isFeedbackSet(feedback)) model.newFeedback(Integer.parseInt(feedback), null);

            model.setFeedbackState(feedback, false);
        }

        final Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        layout.createPoint("CP 1", true, "47475");
        layout.createPoint("CP 2", !mustTurn, "47476");
        layout.createPoint("CP M", true, "47477");
        layout.createPoint("CP B", true, "47478");

        if (mustTurn)
        {
            layout.getPoint("CP 1").setTerminus(true);
            layout.getPoint("CP 2").setReversing(true);
        }
        else
        {
            layout.getPoint("CP 2").setTerminus(true);
        }

        final String from = mustTurn ? "CP 2" : "CP 1";

        for (String copy : new String[] {"CP 1", "CP 2"})
        {
            layout.getPoint(copy).setBlock("CP square");
            layout.getPoint(copy).setProtectingSignal(near.getName());

            layout.createEdge(copy, "CP M");
        }

        layout.createEdge("CP M", "CP B");

        final Locomotive loc = model.getLocByName(model.getLocList().get(0));

        // Stood there by hand, as a train on a barred side's copy can only be
        layout.getPoint(from).setLocomotive(loc);

        near.setState(Accessory.accessorySetting.RED);

        final String[] atDeparture = {"not read"};

        final java.util.function.Consumer<Locomotive> startWas = loc.hasCallback(Layout.CB_ROUTE_START)
            ? loc.getCallback(Layout.CB_ROUTE_START) : l -> { };

        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            atDeparture[0] = aspectOver(layout, from, loc, near);

            startWas.accept(l);
        });

        List<Edge> path = new LinkedList<>();
        path.add(layout.getEdge(from, "CP M"));
        path.add(layout.getEdge("CP M", "CP B"));

        try
        {
            assertTrue(layout.executePath(path, loc, 30, null), "the dispatch did not complete, so nothing tests anything");
        }
        finally
        {
            loc.setCallback(Layout.CB_ROUTE_START, startWas);

            layout.getPoint("CP B").setLocomotive(null);
            layout.getPoint(from).setLocomotive(null);
        }

        return atDeparture[0];
    }

    /**
     * A signal's aspect, and whether the train still holds the platform it guards - the claims above read the two at the
     * same instant, since the platform held is what made the guard red.
     */
    private static String aspectOver(Layout layout, String platform, Locomotive train, MarklinAccessory signal)
    {
        return (signal.isSwitched() ? "RED" : "GREEN")
            + (train.equals(layout.getPoint(platform).getCurrentLocomotive()) ? ", the train holding the platform"
                : ", the platform let go");
    }

    /** Both signals' aspects, near first */
    private static String aspects()
    {
        return "near " + (near.isSwitched() ? "RED" : "GREEN") + ", far " + (far.isSwitched() ? "RED" : "GREEN");
    }

    /**
     * T, M, B in a line, joined both ways, and P apart: this class's own feedbacks 47471-47474, simulated and with no
     * delays (OB-340).
     *
     * @param terminus whether T is a terminus
     */
    private Layout terminusLine(boolean terminus) throws Exception
    {
        for (String feedback : new String[] {"47471", "47472", "47473", "47474"})
        {
            if (!model.isFeedbackSet(feedback)) model.newFeedback(Integer.parseInt(feedback), null);

            model.setFeedbackState(feedback, false);
        }

        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        layout.createPoint("TX T", true, "47471");
        layout.createPoint("TX M", true, "47472");
        layout.createPoint("TX B", true, "47473");
        layout.createPoint("TX P", true, "47474");

        if (terminus) layout.getPoint("TX T").setTerminus(true);

        layout.createEdge("TX T", "TX M");
        layout.createEdge("TX M", "TX B");
        layout.createEdge("TX B", "TX M");
        layout.createEdge("TX M", "TX T");

        return layout;
    }
}
