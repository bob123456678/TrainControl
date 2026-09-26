package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.testng.Assert.assertEquals;
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

        for (int sensor = 194; sensor <= 198; sensor++)
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
     * A duplicate dispatch refused inside the lock leaves the winner's claim alone - on the same route too (AUT2-C1,
     * TDY2-C6).
     *
     * AUT-C1 made the refused dispatch's clean-up remove the claim only when it was "its own": `takingPath.remove(loc,
     * path)`.  But that compares by value, and a list of edges equals any other listing the same edges - so a double
     * dispatch of ONE route, the likeliest duplicate (a double click, or a hand send racing autonomy to the same square),
     * removed the winner's claim while the winner was still locking.  The train was then in neither map: a third dispatch
     * could pass, and the cap undercounted.  Every refusal inside `configureAndLockPath` either took nothing or has already
     * dropped its own claim, so the caller had nothing of its own to remove.
     *
     * Two threads dispatch the same route.  This thread holds the layout's monitor until both are parked on it, and the
     * run list's throughout - so the winner, having claimed and locked, waits before it registers, and the loser is
     * refused inside the lock in exactly that window.  Then the claim is read.  The winner is let go afterwards and its
     * run finished by setting the sensor it is waiting for.
     *
     * MUTATION: put back the caller's `takingPath.remove(loc, path)` and this fails.
     *
     * @throws Exception on a failure to build the fixture or to finish the winner's run
     */
    @Test
    public void testARefusedDuplicateLeavesTheWinnersClaim() throws Exception
    {
        Layout layout = oneEdge();

        Locomotive train = aTrainAtTheStart(layout);

        Object runList = field(layout, "activeLocomotives");

        @SuppressWarnings("unchecked")
        java.util.Map<Locomotive, List<Edge>> claims =
            (java.util.Map<Locomotive, List<Edge>>) field(layout, "takingPath");

        final Boolean[] answered = new Boolean[2];
        final Thread[] dispatch = new Thread[2];

        int winner = -1;

        try
        {
            synchronized (runList)
            {
                synchronized (layout)
                {
                    for (int i = 0; i < 2; i++)
                    {
                        final int which = i;

                        dispatch[i] = new Thread(() ->
                        {
                            try
                            {
                                answered[which] = layout.executePath(new ArrayList<>(theRoute(layout)), train, 30, null);
                            }
                            catch (Exception e)
                            {
                                answered[which] = null;
                            }
                        }, "duplicate-dispatch-" + i);

                        dispatch[i].setDaemon(true);
                        dispatch[i].start();
                    }

                    // BOTH PAST THE OUTER CHECK, parked on the lock's monitor, which this thread holds.
                    waitUntil(() -> dispatch[0].getState() == Thread.State.BLOCKED
                        && dispatch[1].getState() == Thread.State.BLOCKED, "both dispatches to reach the lock");
                }

                // ONE REFUSED INSIDE THE LOCK, the other claimed and waiting on the run list, which this thread holds.
                waitUntil(() -> !dispatch[0].isAlive() || !dispatch[1].isAlive(), "one dispatch to be refused");

                int loser = dispatch[0].isAlive() ? 1 : 0;

                winner = 1 - loser;

                assertEquals(answered[loser], Boolean.FALSE, "precondition: the duplicate was not refused - it answered "
                    + answered[loser]);

                assertTrue(claims.containsKey(train), "a duplicate dispatch of the same route was refused inside the lock,"
                    + " and its clean-up removed the winner's claim while the winner was still locking - the train is"
                    + " in neither map, so a third dispatch could pass and the cap undercounts (AUT2-C1, TDY2-C6)");
            }
        }
        finally
        {
            // THE WINNER'S RUN, finished: it waits for the route's end sensor, which nothing else here sets.
            model.setFeedbackState("195", true);

            if (winner >= 0) dispatch[winner].join(DEADLINE_SECONDS * 1000L);

            model.setFeedbackState("195", false);

            if (layout.isAlreadyUnderway(train)) layout.unlockPath(theRoute(layout), train);
        }
    }

    /**
     * A train under way is last known where it set off, or at the last station on its path whose sensor it has tripped
     * (RLV7-C1) - not at a point with no sensor it has only passed into, nor at a sensor that is no station.
     *
     * A locked path holds every point on it for its train, so a reload confirmed while it is under way has to choose
     * one; `Layout.getLastPointsReached` is that choice, and every carry across a reload reads it.  A point with no
     * sensor becomes a milestone the moment the loop reaches it, ahead of the train; nothing can put a train back on a
     * point that is no station.
     *
     * MUTATION: keep it at its last milestone whatever that is, and this fails.
     *
     * @throws Exception from the dispatch
     */
    @Test
    public void testATrainUnderWayIsWhereItWasLastSeen() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("LS_a", true, "194");
        layout.createPoint("LS_j", false, null);
        layout.createPoint("LS_m", false, "196");
        layout.createPoint("LS_c", true, "197");
        layout.createPoint("LS_b", true, "198");

        layout.createEdge("LS_a", "LS_j");
        layout.createEdge("LS_j", "LS_m");
        layout.createEdge("LS_m", "LS_c");
        layout.createEdge("LS_c", "LS_b");

        final Locomotive train = model.getLocByName(model.getLocList().get(0));

        layout.getPoint("LS_a").setLocomotive(train);

        final List<Edge> path = Arrays.asList(layout.getEdge("LS_a", "LS_j"), layout.getEdge("LS_j", "LS_m"),
            layout.getEdge("LS_m", "LS_c"), layout.getEdge("LS_c", "LS_b"));

        Future<Boolean> run = dispatcher.submit((Callable<Boolean>) () -> layout.executePath(path, train, 30, null));

        try
        {
            // PAST LS_j, which has no sensor, and waiting on LS_m's
            waitUntil(() -> layout.getReachedMilestones(train) != null
                && layout.getReachedMilestones(train).contains(layout.getPoint("LS_j")), "the train to pass LS_j");

            assertEquals(layout.getLastPointsReached().get(train), layout.getPoint("LS_a"), "a train that has only passed"
                + " into LS_j, which has no sensor, is last known there rather than at LS_a, where it set off (RLV7-C1)");

            // AND PAST LS_m, whose sensor it trips but which is no station
            model.setFeedbackState("196", true);

            waitUntil(() -> layout.getReachedMilestones(train).contains(layout.getPoint("LS_m")),
                "the train to trip LS_m's sensor");

            assertEquals(layout.getLastPointsReached().get(train), layout.getPoint("LS_a"), "a train past LS_m, a sensor"
                + " that is no station, is last known there - nothing could put it back on it (RLV7-C1)");

            // AND AT LS_c, a station whose sensor it trips: last known there
            model.setFeedbackState("197", true);

            waitUntil(() -> layout.getReachedMilestones(train).contains(layout.getPoint("LS_c")),
                "the train to trip LS_c's sensor");

            assertEquals(layout.getLastPointsReached().get(train), layout.getPoint("LS_c"), "a train that has tripped the"
                + " sensor of LS_c, a station on its way, is not last known there (RLV7-C1)");
        }
        finally
        {
            // ITS RUN, finished: it waits for LS_b's sensor, which nothing else here sets
            model.setFeedbackState("197", true);
            model.setFeedbackState("198", true);

            try
            {
                run.get(DEADLINE_SECONDS, TimeUnit.SECONDS);
            }
            catch (Exception notFinished)
            {
                run.cancel(true);
            }

            model.setFeedbackState("196", false);
            model.setFeedbackState("197", false);
            model.setFeedbackState("198", false);

            if (layout.isAlreadyUnderway(train)) layout.unlockPath(path, train);
        }
    }

    private static Object field(Object target, String name) throws Exception
    {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);

        f.setAccessible(true);

        return f.get(target);
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, String what) throws Exception
    {
        long giveUp = System.currentTimeMillis() + DEADLINE_SECONDS * 1000L;

        while (!condition.getAsBoolean())
        {
            if (System.currentTimeMillis() > giveUp)
            {
                throw new AssertionError("gave up waiting for " + what + " after " + DEADLINE_SECONDS + " seconds");
            }

            Thread.sleep(10);
        }
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
