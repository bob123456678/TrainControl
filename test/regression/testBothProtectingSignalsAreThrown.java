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
