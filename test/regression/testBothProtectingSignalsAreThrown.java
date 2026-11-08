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
import org.traincontrol.marklin.udp.CS2Message;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A platform with two protecting signals throws BOTH of them (MT-023): they are commanded together and show the same
 * aspect, because a platform guarded at one end and open at the other looks protected.  Asserted at the layout via the
 * public `refreshAllProtectingSignals`, so no trains or hardware.
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
        model = support.TestStation.simulatedInDebug();

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

    /** Occupied: both red.  Empty again: both green. */
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
     * Deleting a locomotive takes it off the railway, not just out of the lists: `locDeleted` does not sweep the
     * locomotive STANDING on a Point, whose name `Point.toJSON` would write back out and invalidate the next load.
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
     * Protection re-asserts after something else has moved the signal: `signalAspects` remembers what protection last
     * COMMANDED, not what the signal shows.
     *
     * Not asserted: which aspect "wins" (Adam: "This shouldn't happen. The protecting signal is at the destination, so
     * the destination shouldn't be in the middle."), only that protection is not SILENTLY SKIPPED because of what it
     * remembers.  Two platforms share one signal so both triggers see the square CLAIMED.
     */
    @Test
    public void testProtectionReassertsAfterSomethingElseMovesTheSignal() throws Exception
    {
        clearFeedbacks("47423", "47424", "47425", "47426");

        Layout layout = instantLayout();

        layout.createPoint("MP A", true, "47423");
        layout.createPoint("MP B", true, "47424");
        layout.createPoint("MP P", true, "47425");
        layout.createPoint("MP Q", true, "47426");

        layout.getPoint("MP P").setProtectingSignal(near.getName());
        layout.getPoint("MP Q").setProtectingSignal(near.getName());

        List<Edge> path = createEdgesAlong(layout, "MP A", "MP B");

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
            // Once: a start is announced again at each milestone, and a second run would read the signal back after it
            // had driven it green, which reads like protection having failed.
            if (!Boolean.TRUE.equals(started) || !watched.compareAndSet(false, true)) return null;

            layout.getPoint("MP P").setLocomotive(first);

            protectedAtFirst.set(near.isSwitched());

            // What configuring a path across the signal tile does: the same accessory, the same door,
            // and no word to the protection that is holding it red.
            near.setState(Accessory.accessorySetting.GREEN);

            // Any later occupancy change asks again.  Both platforms are claimed, so the answer has not changed, but a
            // signal showing the wrong aspect must still be commanded.
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
     * A dispatch does not touch a signal it has nothing to do with (OB-166).  Adam: "Signals should only be touched
     * when a route activates."  The platform is EMPTY and the signal RED, as a route leaves it.
     *
     * MUTATION: putting any of the three refreshAllProtectingSignals() calls back fails this.
     */
    @Test
    public void testADispatchLeavesAnUnrelatedSignalAlone() throws Exception
    {
        clearFeedbacks("47451", "47452", "47453");

        Layout layout = instantLayout();

        layout.createPoint("UN A", true, "47451");
        layout.createPoint("UN B", true, "47452");
        layout.createPoint("UN P", true, "47453");

        // The protected platform has nothing to do with the dispatch, and nobody is standing on it.
        layout.getPoint("UN P").setProtectingSignal(far.getName());

        List<Edge> path = createEdgesAlong(layout, "UN A", "UN B");

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
     * A train passing a station leaves that station's guards alone (OB-315): the entry guard is thrown only where a
     * journey ends; asserted here beside the exit guard.
     *
     * MUTATION: let a reservation by a passing train count again, and this fails.
     */
    @Test
    public void testAPassingTrainLeavesAStationsGuardsAlone() throws Exception
    {
        clearFeedbacks("47461", "47462", "47463");

        Layout layout = instantLayout();

        // A, then M, a station the train only passes, then B where it stops
        layout.createPoint("PT A", true, "47461");
        layout.createPoint("PT M", true, "47462");
        layout.createPoint("PT B", true, "47463");

        layout.getPoint("PT M").setProtectingSignal(near.getName());
        layout.getPoint("PT M").setEntrySignals(java.util.Collections.singletonList(far.getName()));

        List<Edge> path = createEdgesAlong(layout, "PT A", "PT M", "PT B");

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
     * A station a train only passes reads as passed while the train hands over from its claim to its journey (OB-315):
     * the guard's rule must read the claims first (`pathHeldBy`); the straddle is forced by a journeys' map whose read
     * performs the hand-over.
     *
     * MUTATION: read the journey first again, and this fails.
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

        final List<Edge> path = createEdgesAlong(layout, "HO A", "HO M", "HO B");

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
     * A hand dispatch does NOT touch the signal of a train standing elsewhere (OB-166): while nothing is running the
     * protection refresh is deliberately silent.
     *
     * MUTATION: putting the `refreshAllProtectingSignals()` call back into `executePath` fails this test.
     */
    @Test
    public void testAHandDispatchLeavesAStandingTrainsSignalAlone() throws Exception
    {
        // Its OWN addresses: a simulated dispatch clears its feedback from a DETACHED thread, so a sibling's addresses
        // (47431-47433) may not be clear yet.
        clearFeedbacks("47441", "47442", "47443");

        Layout layout = instantLayout();

        // The two the dispatch runs between, and a third where somebody is already standing.
        layout.createPoint("HD A", true, "47441");
        layout.createPoint("HD B", true, "47442");
        layout.createPoint("HD P", true, "47443");

        // Only the STANDING train's platform is protected, so nothing about the dispatch itself can
        // account for the signal moving.
        layout.getPoint("HD P").setProtectingSignal(far.getName());

        List<Edge> path = createEdgesAlong(layout, "HD A", "HD B");

        Locomotive driving = model.getLocByName(model.getLocList().get(0));
        Locomotive standing = model.getLocByName(model.getLocList().get(1));

        assertNotEquals(driving.getName(), standing.getName(),
            "this test needs two different locomotives");

        assertTrue(layout.moveLocomotive(driving.getName(), "HD A", false),
            "precondition - the train that will be dispatched must be placed");

        assertTrue(layout.moveLocomotive(standing.getName(), "HD P", false),
            "precondition - somebody must be standing at the protected platform");

        // Placed by hand while idle, so the signal has NOT been commanded: asserting that silence stops this test
        // passing for the wrong reason.
        far.setState(Accessory.accessorySetting.GREEN);

        assertFalse(far.isSwitched(),
            "precondition - placing a train while nothing is running must not command its signal, or "
            + "there would be nothing for the sweep to do");

        assertTrue(layout.executePath(path, driving, 30, null),
            "the hand dispatch did not complete, so nothing below tests anything");

        // NOT PROTECTED: a train placed by hand at a protected platform keeps the aspect its signal showed until a
        // route activates over it (OB-166).
        assertFalse(far.isSwitched(),
            "a dispatch between two other points commanded the signal of a platform it has nothing "
            + "to do with - which is the sweep coming back");

        layout.getPoint("HD A").setLocomotive(null);
        layout.getPoint("HD B").setLocomotive(null);
        layout.getPoint("HD P").setLocomotive(null);
    }

    /**
     * A train dispatched BY HAND protects the platform it is heading for (MT-139): `refreshProtectingSignal` returns
     * unless `isRunning()`, which is false while a hand dispatch locks its path, so the distinction is "is a train
     * under way".  Observed from the start callback, right after the path is locked.
     */
    @Test
    public void testAHandDispatchedTrainProtectsItsDestination() throws Exception
    {
        // Created before they are set: setFeedbackState refuses a feedback the station has never
        // heard of, and 47421/47422 are this class's own - testAutonomySimulationSanity has 47411/2.
        clearFeedbacks("47421", "47422");

        Layout layout = instantLayout();

        layout.createPoint("MD A", true, "47421");
        layout.createPoint("MD B", true, "47422");

        layout.getPoint("MD B").setProtectingSignal(near.getName());

        List<Edge> path = createEdgesAlong(layout, "MD A", "MD B");

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
     * A dispatch that is TURNED AWAY leaves the signals alone: the sweep must not run before the checks that can
     * refuse a dispatch.  Speed zero is the earliest refusal (`errorInvalidSpeed`), needing no fixture.
     *
     * MUTATION: moving `refreshAllProtectingSignals()` back above the checks in `executePath` fails this test.
     */
    @Test
    public void testARefusedDispatchCommandsNothing() throws Exception
    {
        clearFeedbacks("47425", "47426");

        Layout layout = instantLayout();

        layout.createPoint("RD A", true, "47425");
        layout.createPoint("RD B", true, "47426");

        layout.getPoint("RD B").setProtectingSignal(near.getName());

        List<Edge> path = createEdgesAlong(layout, "RD A", "RD B");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "RD A", false),
            "precondition - the locomotive must be placed at the start");

        // A train standing at the protected platform, so the sweep would have something to say: with the platform empty
        // it would command GREEN, already the aspect, and nothing is sent, so the test would pass with the defect
        // present.
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
     * A failure while LOCKING a path releases what it has taken: `executePath`'s catch deliberately does not unlock
     * (right for a failure MID-RUN), but while locking the train has not moved, so every edge taken so far would stay
     * occupied.  The throw is injected through `configureEdge`.
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

        // The second edge fails when the path sets it up, after the first is locked and this one's Point reserved.  Not
        // on the FIRST ask: `isPathClear` previews every edge before anything is locked, leaving nothing to check.
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
     * A layout with one platform guarded at each end.  The s88 numbers and the run-wide delays are here because
     * `fromJSON` invalidates the WHOLE layout over a missing one, which reads like the signals having been dropped.
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
     * A train that arrives at a station as the end of its journey throws the station's entry guard red (FR-096),
     * through a real hand dispatch: the claim is about the call in `executePath` where the arrival is recorded.
     */
    @Test
    public void testAnEntryGuardGoesRedWhenATrainArrivesThere() throws Exception
    {
        Layout layout = entryGuardLine();

        layout.getPoint("EG B").setEntrySignals(Arrays.asList(far.getName()));

        far.setState(Accessory.accessorySetting.GREEN);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertTrue(layout.moveLocomotive(loc.getName(), "EG A", false), "precondition - the train must start at A");

        List<Edge> path = edgesAlong(layout, "EG A", "EG B");

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
     * A train that only PASSES a station leaves its entry guard alone, and throws the one at the station it stops at
     * (FR-096).  The second half is the control: a guard that never threw anything would pass the first.
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

        List<Edge> path = edgesAlong(layout, "EG A", "EG B", "EG C");

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

    /** A line A - B - C, this class's own feedbacks 47461-47463, simulated and with no delays. */
    private Layout entryGuardLine() throws Exception
    {
        clearFeedbacks("47461", "47462", "47463");

        Layout layout = instantLayout();

        layout.createPoint("EG A", true, "47461");
        layout.createPoint("EG B", true, "47462");
        layout.createPoint("EG C", true, "47463");

        layout.createEdge("EG A", "EG B");
        layout.createEdge("EG B", "EG C");

        return layout;
    }

    // ------------------------------------------------------------------------------------ OB-340: leaving a terminus

    /**
     * A train leaving a terminus is let past its exit guard (OB-340).  Adam: *"when departing a terminus, autonomy
     * should make its exit guard green.  otherwise, trains stopped by that guard would remain stuck."*
     *
     * MUTATION: leave the guards out of what the first edge sets, and this fails.
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

        final java.util.function.Consumer<Locomotive> startWas = readAtRouteStart(loc, l ->
        {
            atDeparture[0] = aspectOver(layout, "TX T", loc, near);

            heldAt[0] = layout.getActiveAccSettings().get(near);
        });

        // The first start is the run being registered; the second, the train at TX M
        readAtTheSecondStart(layout, "OB-340 watcher", () ->
        {
            onTheWay[0] = aspectOver(layout, "TX T", loc, near);

            heldAfter[0] = layout.getActiveAccSettings().get(near);
        });

        List<Edge> path = edgesAlong(layout, "TX T", "TX M", "TX B");

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
     * A train standing at a terminus keeps its exit guard red, the control for the claim above (OB-340): only the
     * first edge out sets it green.
     *
     * MUTATION: let the last edge in set the guards of the station it reaches green, and this fails.
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

        List<Edge> path = edgesAlong(layout, "TX B", "TX M", "TX T");

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
     * A train leaving a through station turns its exit guards green too (OB-340): held GREEN with the first edge's
     * accessories, and green when asked again on the way.
     *
     * MUTATION: leave the guards out of what the first edge sets, or let protection turn red a signal a path holds
     * green, and this fails.
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

        final java.util.function.Consumer<Locomotive> startWas = readAtRouteStart(loc, l ->
        {
            atDeparture[0] = aspects();

            farHeld[0] = layout.getActiveAccSettings().get(far);
        });

        readAtTheSecondStart(layout, "OB-340 through watcher", () ->
        {
            onTheWay[0] = aspects();

            // every guard asked again, the journey still holding the platform
            layout.refreshAllProtectingSignals();

            farAskedAgain[0] = far.isSwitched() ? "RED" : "GREEN";
        });

        List<Edge> path = edgesAlong(layout, "TX T", "TX M", "TX B");

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
     */
    @Test
    public void testAMustTurnStationsBarredCopyDepartureTurnsItsExitGuardGreen() throws Exception
    {
        assertEquals(leaveACopy(true), "GREEN, the train holding the platform", "a train leaving a must-turn station from"
            + " its barred side's copy found its exit guard RED (OB-340)");
    }

    /**
     * An exit guard a terminus shares with another platform turns GREEN for the train leaving, though another train
     * stands on that platform, stays green while the first edge out holds it, and is RED once the journey has given
     * its track back (OB-340).
     *
     * MUTATION: leave the guards out of what the first edge sets, and the first claim fails; let protection turn red a
     * signal a path holds green, and the second does.
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
     * The exit signal a train's route out turns green, shared with another platform a train stands on, stays GREEN
     * while the route holds it and is RED for the standing train once the run is over (OB-340).
     *
     * MUTATION: let protection turn red a signal a path holds green, and this fails.
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
     * `routeCommandsIt`: whether the first edge out commands `near` green.  Returns the guard at each step.
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

        final java.util.function.Consumer<Locomotive> startWas = readAtRouteStart(leaving, l ->
            read[0] = aspectOver(layout, "TX T", leaving, near));

        // At TX M, the first edge still held: P's occupancy changes and back, which asks protection again
        readAtTheSecondStart(layout, "OB-340 shared watcher", () ->
        {
            layout.getPoint("TX P").setLocomotive(null);
            layout.getPoint("TX P").setLocomotive(standing);

            read[1] = aspectOver(layout, "TX T", leaving, near);
        });

        List<Edge> path = edgesAlong(layout, "TX T", "TX M", "TX B");

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
     * With Atomic Routes off, a guard shared with a platform another train stands at is GREEN as the train leaves and
     * RED over the other train once the tail has cleared the first edge out (OB-340).  Four unmeasured edges.
     *
     * MUTATION: leave the start unreleased where the tail clears the first edge, so nothing asks its guards then, and
     * this fails.
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
     * With Atomic Routes on, the first edge's hold on a shared guard lapses when the tail has cleared it, but nothing
     * asks the guard then: it stays GREEN until the next change of occupancy there turns it RED (OB-340).
     *
     * MUTATION: keep the first edge's hold once its tail has cleared it, or record the clearing only where non-atomic
     * routes give the edge back, and this fails.
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
     * A train leaves the terminus FE T over four edges while another stands at FE P, both guarded by `near` (OB-340);
     * `atomic`: Atomic Routes.  Returns the guard as the train sets off, at FE M, at FE N, and after the other train
     * has been taken off P and put back.
     */
    private List<String> leaveBesideAnotherTrainOverFourEdges(boolean atomic) throws Exception
    {
        String[] names = {"FE T", "FE M", "FE N", "FE O", "FE B", "FE P"};

        final Layout layout = instantLayout();

        layout.setAtomicRoutes(atomic);

        createPointsOnClearFeedbacks(layout, names, 47481);

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

        final java.util.function.Consumer<Locomotive> startWas = readAtRouteStart(leaving, l ->
            read[0] = aspectOver(layout, "FE T", leaving, near));

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

    /**
     * What every reading of `raceTheRefresh` is: GREEN and held GREEN as the train sets off, GREEN after the refresh, and
     * the departure's GREEN sent only once its hold could be read
     */
    private static final String STAYS_GREEN = "GREEN, the train holding the platform, held GREEN; after the other thread's"
        + " refresh: GREEN, the train holding the platform; the GREEN commanded with its hold readable";

    /** How long any one step of `raceTheRefresh` may wait, so that a wrong answer is a failure and never a hang */
    private static final long RACE_BOUND_MS = 20000;

    /** How long the whole departure of `raceTheRefresh` may take: each step it can wait for, and then some */
    private static final long DEPARTURE_BOUND_MS = 3 * RACE_BOUND_MS;

    /**
     * A protection refresh on another train's thread that straddles a departure's claim does not turn the leaving
     * train's exit guard RED after the departure has set it GREEN, with Atomic Routes off (OB-340): the refresh reads
     * the holds and commands the signal as one step.
     *
     * MUTATION: take the signal's lock out of `Layout.refreshOneSignal`, or out of the departure's GREEN in
     * `configureAndLockPath`, and the forced reading goes RED after the refresh.
     */
    @Test
    public void testARefreshStraddlingADeparturesClaimLeavesItsGuardGreenWithAtomicRoutesOff() throws Exception
    {
        assertEquals(Arrays.asList(raceTheRefresh(true, false, false), raceTheRefresh(false, false, false)), Arrays.asList(
            "forced: " + STAYS_GREEN, "after the claim: " + STAYS_GREEN), "a guard shared with another platform, the"
            + " leaving train's exit guard, as it sets off and once another thread's refresh of it is done - that refresh"
            + " forced to read the holds before the claim and to finish after the departure, and then made after the claim"
            + " (RSA67-C1)");
    }

    /**
     * The same with Atomic Routes on (OB-340).
     *
     * MUTATION: as `testARefreshStraddlingADeparturesClaimLeavesItsGuardGreenWithAtomicRoutesOff`.
     */
    @Test
    public void testARefreshStraddlingADeparturesClaimLeavesItsGuardGreenWithAtomicRoutesOn() throws Exception
    {
        assertEquals(Arrays.asList(raceTheRefresh(true, true, false), raceTheRefresh(false, true, false)), Arrays.asList(
            "forced: " + STAYS_GREEN, "after the claim: " + STAYS_GREEN), "a guard shared with another platform, the"
            + " leaving train's exit guard, as it sets off and once another thread's refresh of it is done - that refresh"
            + " forced to read the holds before the claim and to finish after the departure, and then made after the claim"
            + " (RSA67-C1)");
    }

    /**
     * The same where the first edge out commands the shared guard GREEN itself, with Atomic Routes off (OB-340): the
     * edge's own command is sent under the signal's lock once the edge is taken.
     *
     * MUTATION: take the signal's lock out of `Layout.configureEdge`'s command, and the forced reading goes RED after
     * the refresh.
     */
    @Test
    public void testARefreshStraddlingADeparturesClaimLeavesTheGreenItsFirstEdgeCommandsWithAtomicRoutesOff()
        throws Exception
    {
        assertEquals(Arrays.asList(raceTheRefresh(true, false, true), raceTheRefresh(false, false, true)), Arrays.asList(
            "forced: " + STAYS_GREEN, "after the claim: " + STAYS_GREEN), "a guard shared with another platform that the"
            + " leaving train's first edge commands GREEN, as it sets off and once another thread's refresh of it is done -"
            + " that refresh forced to read the holds before the claim and to finish after the departure, and then made"
            + " after the claim (RSA67-C1)");
    }

    /**
     * The same with Atomic Routes on (OB-340).
     *
     * MUTATION: as `testARefreshStraddlingADeparturesClaimLeavesTheGreenItsFirstEdgeCommandsWithAtomicRoutesOff`.
     */
    @Test
    public void testARefreshStraddlingADeparturesClaimLeavesTheGreenItsFirstEdgeCommandsWithAtomicRoutesOn()
        throws Exception
    {
        assertEquals(Arrays.asList(raceTheRefresh(true, true, true), raceTheRefresh(false, true, true)), Arrays.asList(
            "forced: " + STAYS_GREEN, "after the claim: " + STAYS_GREEN), "a guard shared with another platform that the"
            + " leaving train's first edge commands GREEN, as it sets off and once another thread's refresh of it is done -"
            + " that refresh forced to read the holds before the claim and to finish after the departure, and then made"
            + " after the claim (RSA67-C1)");
    }

    /**
     * A train leaves RR S for RR N by RR M while another thread refreshes `near`, the guard RR S shares with RR P.
     * `forced`: the refresh straddles the claim; `atomic`: Atomic Routes; `edgeCommandsIt`: the first edge out commands
     * `near` GREEN itself.  Another train is under way on one held edge so the refresh walks its commands.  Every GREEN
     * sent to `near` before the hand-over is checked against the holds as sent; the departure runs on its own thread,
     * joined with a bound, so a refresh waiting for the railway's monitor fails rather than hangs.
     *
     * Returns the guard as `aspectOver` reads it as the train sets off; once the refresh is done; and whether the
     * GREEN was sent with its hold readable.
     */
    private String raceTheRefresh(final boolean forced, boolean atomic, boolean edgeCommandsIt) throws Exception
    {
        String[] names = {"RR S", "RR M", "RR N", "RR P", "RR X", "RR Y"};

        final Layout layout = instantLayout();

        layout.setAtomicRoutes(atomic);

        createPointsOnClearFeedbacks(layout, names, 47491);

        layout.getPoint("RR S").setProtectingSignal(near.getName());
        layout.getPoint("RR P").setProtectingSignal(near.getName());

        List<Edge> path = createEdgesAlong(layout, "RR S", "RR M", "RR N");

        if (edgeCommandsIt) path.get(0).addConfigCommand(near.getName(), Accessory.accessorySetting.GREEN);

        final Locomotive leaving = model.getLocByName(model.getLocList().get(0));
        final Locomotive other = model.getLocByName(model.getLocList().get(1));

        assertNotEquals(leaving.getName(), other.getName(), "this test needs two different locomotives");

        layout.getPoint("RR S").setLocomotive(leaving);

        final java.util.concurrent.CountDownLatch refreshIsReading = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch letTheRefreshGo = new java.util.concurrent.CountDownLatch(1);
        final Thread[] refresher = {null};
        final String[] read = {"not read", "not read", "not refreshed"};
        final java.util.concurrent.atomic.AtomicBoolean handedOver = new java.util.concurrent.atomic.AtomicBoolean();
        final List<String> greens = new java.util.concurrent.CopyOnWriteArrayList<>();

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

        final java.util.function.Consumer<Locomotive> startWas = readAtRouteStart(leaving, l ->
        {
            handedOver.set(true);

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
        });

        final Object[] ran = {"not finished"};

        final Thread departing = new Thread(() ->
        {
            try
            {
                ran[0] = layout.executePath(path, leaving, 30, null);
            }
            catch (Throwable e)
            {
                ran[0] = e;
            }
        }, "RSA67-C1 departure");

        departing.setDaemon(true);

        // Each GREEN of the guard the departure sends before its hand-over, and whether the holds already had it then
        model.setSentMessageObserver(m ->
        {
            if (Thread.currentThread() != departing || handedOver.get()) return;

            if (!Integer.valueOf(CS2Message.CMD_ACC_SWITCH).equals(m.getCommand()) || m.extractUID() != near.getUID()
                || m.getData().length < 5 || m.getData()[4] != 1) return;

            greens.add(Boolean.FALSE.equals(layout.getActiveAccSettings().get(near)) ? "held" : "not yet held");
        });

        Thread watcher = null;

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

            departing.start();
            departing.join(DEPARTURE_BOUND_MS);
        }
        finally
        {
            model.setSentMessageObserver(null);

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

            // Not while a departure is stuck: clearing a platform asks the guard, and may wait on what it holds
            if (!departing.isAlive()) for (String name : names) layout.getPoint(name).setLocomotive(null);
        }

        assertFalse(departing.isAlive(), "the departure did not finish within " + DEPARTURE_BOUND_MS + " ms - it is waiting"
            + " for something that waits for it, as a refresh needing the railway's monitor would");

        assertEquals(ran[0], Boolean.TRUE, "the dispatch did not complete, so nothing tests anything");

        assertEquals(read[2], "refreshed", "the other thread's refresh must have run to its end");

        String green = greens.isEmpty() ? "no GREEN commanded" : greens.contains("not yet held")
            ? "a GREEN commanded before its hold was readable " + greens : "the GREEN commanded with its hold readable";

        return (forced ? "forced: " : "after the claim: ") + read[0] + "; after the other thread's refresh: " + read[1]
            + "; " + green;
    }

    /**
     * A protection refresh of one Point, as an occupancy change there makes it; returns "refreshed", or what it threw.
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

    /** The railway's live map of journeys under way, so that a test can put a train among them without running it. */
    @SuppressWarnings("unchecked")
    private static java.util.Map<Locomotive, List<Edge>> journeys(Layout layout) throws Exception
    {
        java.lang.reflect.Field active = Layout.class.getDeclaredField("activeLocomotives");

        active.setAccessible(true);

        return (java.util.Map<Locomotive, List<Edge>>) active.get(layout);
    }

    /**
     * Whether `waiting` is BLOCKED entering a monitor `holding` holds - the departure waiting for the refresh, which is
     * the fix's shape and which the forcing must let through rather than deadlock on.
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
     * Where the first edge out commands its own station's exit guard, the edge's setting stands (OB-340): one
     * commanding it RED leaves it RED, held RED.
     *
     * MUTATION: set every guard of the station green whatever the edge commands, and this fails.
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

        final java.util.function.Consumer<Locomotive> startWas = readAtRouteStart(loc, l ->
        {
            read[0] = near.isSwitched() ? "RED" : "GREEN";
            read[1] = layout.getActiveAccSettings().get(near);
        });

        List<Edge> path = edgesAlong(layout, "TX T", "TX M", "TX B");

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
     * A departure given back unrun leaves the station's exit guard RED over the train still standing there (OB-340):
     * the route is given back and the start reserved again, which asks the guard.
     *
     * MUTATION: give the route back without reserving the start again, and this fails.
     */
    @Test
    public void testADepartureGivenBackForAStopLeavesItsGuardRed() throws Exception
    {
        assertEquals(failADeparture(false), Arrays.asList("GREEN", "RED, the train holding the platform"), "the exit"
            + " guard while the route was being set, and after a stop gave it back (OB-340)");
    }

    /**
     * A departure that fails while its route is being set leaves the station's exit guard RED over the train still
     * standing there (OB-340): what was taken is released and the start reserved again, which asks the guard.
     *
     * MUTATION: release a failed route without reserving the start again, and this fails.
     */
    @Test
    public void testADepartureThatFailsWhileItsRouteIsSetLeavesItsGuardRed() throws Exception
    {
        assertEquals(failADeparture(true), Arrays.asList("GREEN", "RED, the train holding the platform"), "the exit"
            + " guard while the route was being set, and after the route failed (OB-340)");
    }

    /**
     * A departure from the terminus TX T, guarded by `near`, that does not happen: a stop is ordered while its second
     * edge is set up, or (`fails`) the edge cannot be configured (OB-340).  Returns the guard during and after.
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
     * Two copies of one station square, each a way out to CP M and on to CP B, both guarded by `near`; the train leaves
     * from whichever is not a terminus (OB-340).  `mustTurn`: CP 2 is the turning copy of a barred side; else CP 1.
     */
    private String leaveACopy(boolean mustTurn) throws Exception
    {
        clearFeedbacks("47475", "47476", "47477", "47478");

        final Layout layout = instantLayout();

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

        final java.util.function.Consumer<Locomotive> startWas = readAtRouteStart(loc, l ->
            atDeparture[0] = aspectOver(layout, from, loc, near));

        List<Edge> path = edgesAlong(layout, from, "CP M", "CP B");

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

    /** The layout's existing edges from each point to the next, in order. */
    private static List<Edge> edgesAlong(Layout layout, String... points)
    {
        List<Edge> path = new LinkedList<>();

        for (int i = 0; i + 1 < points.length; i++) path.add(layout.getEdge(points[i], points[i + 1]));

        return path;
    }

    /** The edges created from each point to the next, in order. */
    private static List<Edge> createEdgesAlong(Layout layout, String... points) throws Exception
    {
        List<Edge> path = new LinkedList<>();

        for (int i = 0; i + 1 < points.length; i++) path.add(layout.createEdge(points[i], points[i + 1]));

        return path;
    }

    /** Makes each feedback exist, if the station has not heard of it, and clear. */
    private static void clearFeedbacks(String... feedbacks)
    {
        for (String feedback : feedbacks)
        {
            if (!model.isFeedbackSet(feedback)) model.newFeedback(Integer.parseInt(feedback), null);

            model.setFeedbackState(feedback, false);
        }
    }

    /** A new layout that simulates its dispatches and waits no time between steps. */
    private static Layout instantLayout() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setMaxDelay(0);
        layout.setMinDelay(0);
        layout.setSimulate(true);

        return layout;
    }

    /** Creates a point for each name on consecutive feedbacks from `first`, each feedback made clear just before. */
    private static void createPointsOnClearFeedbacks(Layout layout, String[] names, int first) throws Exception
    {
        for (int i = 0; i < names.length; i++)
        {
            String feedback = Integer.toString(first + i);

            clearFeedbacks(feedback);

            layout.createPoint(names[i], true, feedback);
        }
    }

    /**
     * Runs `reading` when the locomotive's route starts, ahead of the start callback it had; returns that callback, to
     * put back.
     */
    private static java.util.function.Consumer<Locomotive> readAtRouteStart(Locomotive loc,
        java.util.function.Consumer<Locomotive> reading)
    {
        final java.util.function.Consumer<Locomotive> startWas = loc.hasCallback(Layout.CB_ROUTE_START)
            ? loc.getCallback(Layout.CB_ROUTE_START) : l -> { };

        loc.setCallback(Layout.CB_ROUTE_START, l ->
        {
            reading.accept(l);

            startWas.accept(l);
        });

        return startWas;
    }

    /** Runs `reading` at the second start the layout announces; the first is the run being registered. */
    private static void readAtTheSecondStart(Layout layout, String name, Runnable reading)
    {
        final java.util.concurrent.atomic.AtomicInteger starts = new java.util.concurrent.atomic.AtomicInteger();

        layout.setCallback(name, (edges, l, started) ->
        {
            if (!Boolean.TRUE.equals(started) || starts.incrementAndGet() != 2) return null;

            reading.run();

            return null;
        });
    }

    /**
     * T, M, B in a line, joined both ways, and P apart: this class's own feedbacks 47471-47474, simulated, no delays
     * (OB-340); `terminus`: whether T is a terminus.
     */
    private Layout terminusLine(boolean terminus) throws Exception
    {
        clearFeedbacks("47471", "47472", "47473", "47474");

        Layout layout = instantLayout();

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
