package core;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * One locomotive is dispatched onto one path at a time.
 *
 * REG9-A2 and REG9V-B2: the guard that enforces this had no assertion of any kind. It was removed and
 * seven dispatch classes - 179 tests - stayed green; its sibling nine lines away was removed and 180
 * stayed green. Both are now one predicate, `Layout.isAlreadyUnderway`, and this is its test.
 *
 * **The two states are not the same question.** A locomotive joins `activeLocomotives` only after
 * `configureAndLockPath` RETURNS, and that call is seconds long - it throws every turnout and signal on
 * the path with a wait between each. For the whole of that window "is it running" answers no. So:
 *
 * - **claiming** - the route is locked, the accessories are being thrown, nothing is registered yet.
 *   Reached by a race, and the case the guard was written for.
 * - **running** - registered and moving. Reachable with no race at all: the diagram's right-click
 *   items do not re-check when they are clicked, so a menu opened while a train was idle and clicked
 *   after it set off is one gesture, not two threads.
 *
 * Both put two driving threads on one physical train, each one's completion unlocking points the other
 * is still relying on - the invariant `Point.reserve` describes, broken from above.
 *
 * **Why the refusal is timed rather than simply asserted false.** With the guard gone, `executePath`
 * does not return true - it goes on to dispatch, and a dispatch waits on sensors that nothing in this
 * fixture will ever set. A plain `assertFalse` would hang the battery for ever instead of failing
 * (`testLocomotive` did exactly that on 2026-09-09). So the call is made on a thread with a deadline,
 * and "did not refuse promptly" is the failure - which is what a missing guard actually looks like.
 *
 * **What the control is, and is not.** There is no "and a clean locomotive dispatches fine" claim here,
 * for the same reason: proving that would mean running a train the fixture cannot finish. The control
 * is the predicate's own two-sidedness - it answers false before the claim, true during it, and false
 * again once the path is released - so a predicate stuck at true, which would refuse every dispatch on
 * the railway, fails these tests too.
 *
 * MUTATION: make `isAlreadyUnderway` return false and both tests fail on the deadline; make it return
 * true and both fail on their release claims.
 *
 * @author Adam
 */
public class testATrainIsDispatchedOnce
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static ExecutorService dispatcher;

    /** Long enough that a slow machine is not the reason, short enough not to hold the battery. */
    private static final int DEADLINE_SECONDS = 20;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // THE SANDBOX FIRST, BEFORE THE MODEL, because init reads the layout preference - which on
        // Adam's machine names his real railway.  The rule and the check are in
        // regression.testSwitchingToACentralStationLayout.
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, false);

        for (int sensor = 194; sensor <= 195; sensor++)
        {
            model.newFeedback(sensor, null);
        }

        dispatcher = Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "dispatch-under-test");

            // DAEMON, so that a dispatch this test could not stop cannot keep the JVM alive after the
            // battery has moved on.
            thread.setDaemon(true);

            return thread;
        });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (dispatcher != null) dispatcher.shutdownNow();

        if (sandbox != null) sandbox.close();
    }

    /**
     * A locomotive that has locked its route is not dispatched again while it is locking.
     */
    @Test
    public void testALocomotiveStillClaimingItsRouteIsNotSentOutAgain() throws Exception
    {
        Layout layout = oneEdge();

        Locomotive train = aTrainAtTheStart(layout);

        assertFalse(layout.isAlreadyUnderway(train),
            "the locomotive counted as under way before anything dispatched it, so a refusal below "
            + "would prove nothing");

        assertTrue(layout.configureAndLockPath(theRoute(layout), train),
            "the route would not lock, so the state this test is about was never reached");

        try
        {
            assertTrue(layout.isAlreadyUnderway(train),
                "a locomotive that has locked a route and not yet set off is not counted as under "
                + "way. It joins activeLocomotives only after configureAndLockPath returns, and that "
                + "is seconds of throwing accessories - the whole window a second dispatch fits in");

            assertRefusedPromptly(layout, train,
                "a locomotive already claiming a route was dispatched onto it a second time. Two "
                + "threads then drive one physical train, and each one's completion unlocks points "
                + "the other is still relying on (REG9-A2)");
        }
        finally
        {
            layout.unlockPath(theRoute(layout), train);
        }

        assertFalse(layout.isAlreadyUnderway(train),
            "the claim outlived the path it was for. A predicate stuck at true refuses every dispatch "
            + "the railway asks for afterwards, which is worse than the defect it guards");
    }

    /**
     * The lock itself refuses a locomotive already claiming a route - where a race has let a second dispatch past the
     * check outside it (AUT-C1).
     *
     * `executePath` asks `isAlreadyUnderway` before it reaches `configureAndLockPath`, and the claim that answer reads is
     * taken inside the lock's monitor - seconds later when another train is throwing its accessories.  Two dispatches of
     * one train in that window both pass the outer check and queue on the monitor.  Where the two routes share no track -
     * a square nothing arrives at is one Point that may leave by any side - both locked, and two threads drove one train.
     * So the lock asks again, in the monitor where the claim is taken.
     *
     * MUTATION: take the check out of `configureAndLockPath` and this fails.
     *
     * @throws Exception on a failure to build the fixture
     */
    @Test
    public void testTheLockRefusesALocomotiveAlreadyClaimingARoute() throws Exception
    {
        Layout layout = oneEdge();

        // A SECOND WAY OUT OF THE SAME SQUARE, sharing no track with the first.
        layout.createPoint("DSP_c", true, model.newFeedback(196, null).getName());
        layout.createEdge("DSP_a", "DSP_c");

        Locomotive train = aTrainAtTheStart(layout);

        List<Edge> other = Arrays.asList(layout.getEdge("DSP_a", "DSP_c"));

        assertTrue(layout.configureAndLockPath(theRoute(layout), train),
            "the first route would not lock, so the state this test is about was never reached");

        try
        {
            // THE SECOND DISPATCH, arrived at the lock as a race delivers it: past the outer check.
            boolean second = layout.configureAndLockPath(other, train);

            if (second) layout.unlockPath(other, train);

            assertFalse(second, "a locomotive already claiming one route locked a second one, sharing no track with the"
                + " first - two threads would drive one train.  The outer check had been passed in the window the claim"
                + " is taken in, and the lock did not ask again (AUT-C1)");
        }
        finally
        {
            layout.unlockPath(theRoute(layout), train);
        }
    }

    /**
     * And one that is registered as running is not dispatched again either.
     *
     * The sequential case, which needs no race: `activeLocomotives` is what "running" means, and the
     * menus that dispatch do not re-ask when they are clicked.
     */
    @Test
    public void testARunningLocomotiveIsNotSentOutAgain() throws Exception
    {
        Layout layout = oneEdge();

        Locomotive train = aTrainAtTheStart(layout);

        List<Edge> route = theRoute(layout);

        // Registered directly, which is what a running train looks like from here: `executePath` puts
        // the locomotive in this map itself once its path is locked and its accessories confirmed, and
        // reaching that state for real means running a train this fixture cannot finish.
        layout.getActiveLocomotives().put(train, route);

        try
        {
            assertTrue(layout.isAlreadyUnderway(train),
                "a registered, running locomotive is not counted as under way");

            assertRefusedPromptly(layout, train,
                "a locomotive already running was dispatched onto a second path. The diagram's "
                + "right-click items do not re-check when they are clicked, so a menu opened while "
                + "the train was idle and clicked after it set off reaches this with one gesture "
                + "(REG9V-B2)");
        }
        finally
        {
            layout.getActiveLocomotives().remove(train);
        }

        assertFalse(layout.isAlreadyUnderway(train),
            "the locomotive still counts as under way after it stopped running");
    }

    /**
     * Asserts that a dispatch is refused, and refused quickly.
     *
     * A missing guard does not make this return true - it makes it go on and dispatch, and a dispatch
     * waits on sensors nothing here will set. So the deadline IS the assertion for that case.
     *
     * @param layout the railway
     * @param train the locomotive that is already under way
     * @param why what a failure means
     */
    private static void assertRefusedPromptly(Layout layout, Locomotive train, String why)
        throws Exception
    {
        Future<Boolean> answer = dispatcher.submit((Callable<Boolean>) () ->
            layout.executePath(theRoute(layout), train, 30, null));

        try
        {
            assertFalse(answer.get(DEADLINE_SECONDS, TimeUnit.SECONDS), why);
        }
        catch (TimeoutException neverAnswered)
        {
            answer.cancel(true);

            throw new AssertionError(why + " - and it did not refuse at all: the call was still "
                + "running after " + DEADLINE_SECONDS + " seconds, which is what a dispatch that got "
                + "past the guard looks like, waiting on a sensor this fixture never sets");
        }
    }

    /**
     * Two points and the edge between them.
     */
    private static Layout oneEdge() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("DSP_a", true, "194");
        layout.createPoint("DSP_b", true, "195");

        layout.createEdge("DSP_a", "DSP_b");

        return layout;
    }

    /**
     * A locomotive standing on the start of the route, which `executePath` requires before it reaches
     * anything this test is about.
     */
    private static Locomotive aTrainAtTheStart(Layout layout) throws Exception
    {
        Locomotive train = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("DSP_a").setLocomotive(train);

        return train;
    }

    private static List<Edge> theRoute(Layout layout)
    {
        return Arrays.asList(layout.getEdge("DSP_a", "DSP_b"));
    }
}
