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

    /**
     * A railway loaded into an empty model is the current one, even when something asked the model for its railway while
     * the load was being parsed (RLV8-C3).
     *
     * Asking an empty model builds a railway, and every railway built is newer than those before it.  The parse built
     * the loaded one first, so one built during the parse was newer, and the one loaded was retired from the start:
     * valid, named as running, and no train it sent would get under way.  The seam runs where the lookup would land.
     *
     * MUTATION: assign the parsed railway without making it the current one, and this fails.
     *
     * @throws Exception from the parse
     */
    @Test
    public void testARailwayLoadedIsCurrentThoughOneWasBuiltDuringItsParse() throws Exception
    {
        Layout built = oneEdge();

        built.setDefaultLocSpeed(30);

        final String json = built.toJSON();

        java.lang.reflect.Field seam = MarklinControlStation.class.getField("whileParsingForTest");

        model.clearAutoLayout();

        seam.set(null, (Runnable) () -> model.getAutoLayout());

        try
        {
            model.parseAuto(json);
        }
        finally
        {
            seam.set(null, null);
        }

        Layout loaded = model.getAutoLayout();

        assertEquals(loaded.getPoints().size(), 2, "precondition: the model holds something other than the railway"
            + " loaded");

        assertTrue(loaded.isCurrentLayout(), "a railway loaded while a lookup built one during its parse is retired from"
            + " the start, so no train it sends gets under way (RLV8-C3)");

        model.clearAutoLayout();
    }

    /**
     * A train under way whose last station a release has cleared, atomic routes off, is read there - and written there
     * by a fold - though another train's path now runs through it (RLV8-C4).
     *
     * A second train locking a path through that point is recorded on it; the carry skipped the first train wherever the
     * point had any occupant, so it was modelled back where it set off, and the fold wrote the second train there as if
     * it stood.  A train only passing through stands elsewhere.
     *
     * Built by hand, as a run would leave it: the maps a dispatch writes, set directly.
     *
     * And the side RC_x records - its occupant's, the passing train's - is not written as the kept train's (RLV9-C7).
     *
     * MUTATION: skip wherever the point has an occupant, write each point's occupant, or write a point's side for
     * whichever train the fold writes there, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testATrainIsReadWhereItWasLastSeenThoughAnotherPassesThere() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RC_a", true, "194");
        layout.createPoint("RC_x", true, "195");
        layout.createPoint("RC_b", true, "196");
        layout.createPoint("RC_c", true, "197");
        layout.createPoint("RC_d", true, "198");

        layout.createEdge("RC_a", "RC_x");
        layout.createEdge("RC_x", "RC_b");
        layout.createEdge("RC_c", "RC_x");
        layout.createEdge("RC_x", "RC_d");

        layout.setDefaultLocSpeed(30);

        Locomotive first = model.getLocByName(model.getLocList().get(0));
        Locomotive second = model.getLocByName(model.getLocList().get(1));

        // RESERVED, as locking a path records a train on every point of it - `setLocomotive` would take it off the others
        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        // FIRST: set off from RC_a, tripped RC_x, and the release behind it has cleared RC_a and RC_x; it holds RC_b
        reserve.invoke(layout.getPoint("RC_b"), first);

        // SECOND: standing at RC_c, its path locked through RC_x to RC_d
        reserve.invoke(layout.getPoint("RC_c"), second);
        reserve.invoke(layout.getPoint("RC_x"), second);
        reserve.invoke(layout.getPoint("RC_d"), second);

        assertEquals(layout.getPoint("RC_x").getCurrentLocomotive(), second, "precondition: RC_x does not record the"
            + " second train, whose path runs through it");

        // A SIDE RECORDED FOR ITS OCCUPANT (RLV9-C7)
        layout.getPoint("RC_x").setArrivedFrom("N");

        java.util.Map<Locomotive, List<Edge>> active =
            (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
        java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
            (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

        active.put(first, Arrays.asList(layout.getEdge("RC_a", "RC_x"), layout.getEdge("RC_x", "RC_b")));
        milestones.put(first, new java.util.concurrent.CopyOnWriteArrayList<>(
            Arrays.asList(layout.getPoint("RC_a"), layout.getPoint("RC_x"))));

        active.put(second, Arrays.asList(layout.getEdge("RC_c", "RC_x"), layout.getEdge("RC_x", "RC_d")));
        milestones.put(second, new java.util.concurrent.CopyOnWriteArrayList<>(
            Arrays.asList(layout.getPoint("RC_c"))));

        try
        {
            assertEquals(layout.getLastPointsReached().get(first), layout.getPoint("RC_x"), "precondition: the first"
                + " train is not last known at RC_x, the station whose sensor it tripped");

            String[] read = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(layout).get(first.getName());

            assertEquals(read == null ? null : read[0], "RC_x", "the first train, last seen at RC_x, is not read there"
                + " because the second train's path runs through it - the carry leaves it where it set off (RLV8-C4)");

            java.lang.reflect.Method kept = Layout.class.getMethod("toJSON", java.util.Map.class);

            org.json.JSONObject written = new org.json.JSONObject((String) kept.invoke(layout,
                layout.getLastPointsReached()));

            java.util.Map<String, String> standing = new java.util.HashMap<>();

            for (Object point : written.getJSONArray("points"))
            {
                org.json.JSONObject p = (org.json.JSONObject) point;

                if (p.has("loc")) standing.put(p.getString("name"), p.getJSONObject("loc").getString("name"));
            }

            assertEquals(standing.get("RC_x"), first.getName(), "a fold writes the train passing through RC_x there, not"
                + " the one last seen there (RLV8-C4): " + standing);

            for (Object point : written.getJSONArray("points"))
            {
                org.json.JSONObject p = (org.json.JSONObject) point;

                if ("RC_x".equals(p.getString("name")))
                {
                    assertFalse(p.has("arrivedFrom"), "a fold writes the side RC_x records for the train passing through"
                        + " it as the side of the train kept there (RLV9-C7): " + p);
                }
            }

            assertEquals(standing.get("RC_c"), second.getName(), "a fold does not write the second train where it set"
                + " off: " + standing);

            assertEquals(java.util.Collections.frequency(standing.values(), first.getName()), 1, "a fold writes the"
                + " first train on other than one point: " + standing);

            assertEquals(java.util.Collections.frequency(standing.values(), second.getName()), 1, "a fold writes the"
                + " second train on other than one point: " + standing);
        }
        finally
        {
            active.clear();
            milestones.clear();
        }
    }

    /**
     * A train under way whose last station another train has since stopped on, atomic routes off, is kept on the first
     * station ahead on its path that it still holds (RLV9-C4) - read there by the carry and written there by a fold.  It
     * was kept on no point: the one standing there wins it, and a fold then erased the train under way from the
     * configuration, so no later load brought it back.
     *
     * And a fold writes no train the railway no longer shows anywhere, however it is kept (RLV9-C7).
     *
     * Built by hand, as a run would leave it: the maps a dispatch writes, set directly.
     *
     * MUTATION: keep it on the point another stands on, or write a kept train the railway does not show, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testATrainWhoseLastStationAnotherHasStoppedOnIsKeptOnTheStationAheadItHolds() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("RK_a", true, "194");
        layout.createPoint("RK_x", true, "195");
        layout.createPoint("RK_b", true, "196");
        layout.createPoint("RK_e", true, "197");

        layout.createEdge("RK_a", "RK_x");
        layout.createEdge("RK_x", "RK_b");

        layout.setDefaultLocSpeed(30);

        Locomotive first = model.getLocByName(model.getLocList().get(0));
        Locomotive second = model.getLocByName(model.getLocList().get(1));
        Locomotive third = model.getLocByName(model.getLocList().get(2));

        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        // FIRST: set off from RK_a, no sensor tripped yet; the release behind it has cleared RK_a, and it holds RK_x and
        // RK_b ahead
        reserve.invoke(layout.getPoint("RK_x"), first);
        reserve.invoke(layout.getPoint("RK_b"), first);

        // SECOND: has since stopped on RK_a, as an arrival stands it there
        layout.getPoint("RK_a").setLocomotive(second);

        java.util.Map<Locomotive, List<Edge>> active =
            (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
        java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
            (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

        active.put(first, Arrays.asList(layout.getEdge("RK_a", "RK_x"), layout.getEdge("RK_x", "RK_b")));
        milestones.put(first, new java.util.concurrent.CopyOnWriteArrayList<>(
            Arrays.asList(layout.getPoint("RK_a"))));

        try
        {
            assertEquals(layout.getLastPointsReached().get(first), layout.getPoint("RK_x"), "the first train, last seen"
                + " where it set off - where the second has since stopped - is not kept on RK_x, the first station ahead"
                + " it holds (RLV9-C4)");

            java.util.Map<String, String[]> read = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(layout);

            assertEquals(read.get(first.getName()) == null ? null : read.get(first.getName())[0], "RK_x", "the carry"
                + " reads the first train on no point, or not on RK_x (RLV9-C4)");

            assertEquals(read.get(second.getName()) == null ? null : read.get(second.getName())[0], "RK_a", "the carry"
                + " does not read the second train where it stands");

            java.lang.reflect.Method kept = Layout.class.getMethod("toJSON", java.util.Map.class);

            java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt =
                new java.util.HashMap<>(layout.getLastPointsReached());

            // A TRAIN THE RAILWAY SHOWS NOWHERE, kept at an empty station (RLV9-C7)
            keptAt.put(third, layout.getPoint("RK_e"));

            org.json.JSONObject written = new org.json.JSONObject((String) kept.invoke(layout, keptAt));

            java.util.Map<String, String> standing = new java.util.HashMap<>();

            for (Object point : written.getJSONArray("points"))
            {
                org.json.JSONObject p = (org.json.JSONObject) point;

                if (p.has("loc")) standing.put(p.getString("name"), p.getJSONObject("loc").getString("name"));
            }

            assertEquals(standing.get("RK_x"), first.getName(), "a fold does not write the first train on RK_x, the"
                + " station ahead it holds (RLV9-C4): " + standing);

            assertEquals(java.util.Collections.frequency(standing.values(), first.getName()), 1, "a fold writes the"
                + " first train on other than one point (RLV9-C4): " + standing);

            assertEquals(standing.get("RK_a"), second.getName(), "a fold does not write the second train where it"
                + " stands: " + standing);

            assertFalse(standing.containsKey("RK_e"), "a fold writes a train the railway shows nowhere, because it was"
                + " kept there (RLV9-C7): " + standing);
        }
        finally
        {
            active.clear();
            milestones.clear();
        }
    }

    /**
     * The station ahead a train is kept on is not one another train under way is kept at, whichever of the two the map
     * yields first (RLV10-C2); and it is a station, not the first point it holds (RLV10-C5).
     *
     * A has set off from TA, past no sensor, along TA-TJ-TS-TB, and B has since stopped on TA; C set off from TS towards
     * TC, past no sensor, TS released behind it and then locked through for A.  A's station ahead that it holds is TS -
     * where C is kept - so which of the two kept it followed the map's order, and in about half the orders C was read
     * nowhere and a fold wrote it on no point.  Over every ordered pair of seven locomotives as A and C, both orders
     * come up.  TJ, a point that is no station, is held by A and nearer.
     *
     * MUTATION: decide against the map as it is rewritten, keep a train on a station another is kept at, or on the first
     * point ahead it holds, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testTheStationAheadIsNotAnotherTrainsKeptPoint() throws Exception
    {
        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        java.lang.reflect.Method kept = Layout.class.getMethod("toJSON", java.util.Map.class);

        List<String> wrong = new ArrayList<>();

        int pairs = 0;

        List<String> names = model.getLocList().subList(0, Math.min(7, model.getLocList().size()));

        for (String nameA : names)
        {
            for (String nameC : names)
            {
                if (nameA.equals(nameC)) continue;

                String nameB = null;

                for (String other : names)
                {
                    if (nameB == null && !other.equals(nameA) && !other.equals(nameC)) nameB = other;
                }

                Locomotive a = model.getLocByName(nameA);
                Locomotive b = model.getLocByName(nameB);
                Locomotive c = model.getLocByName(nameC);

                Layout layout = new Layout(model);

                layout.createPoint("TA", true, "194");
                layout.createPoint("TJ", false, null);
                layout.createPoint("TS", true, "195");
                layout.createPoint("TB", true, "196");
                layout.createPoint("TC", true, "197");

                layout.createEdge("TA", "TJ");
                layout.createEdge("TJ", "TS");
                layout.createEdge("TS", "TB");
                layout.createEdge("TS", "TC");

                reserve.invoke(layout.getPoint("TC"), c);
                reserve.invoke(layout.getPoint("TJ"), a);
                reserve.invoke(layout.getPoint("TS"), a);
                reserve.invoke(layout.getPoint("TB"), a);

                layout.getPoint("TA").setLocomotive(b);

                java.util.Map<Locomotive, List<Edge>> active =
                    (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
                java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
                    (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

                active.put(a, Arrays.asList(layout.getEdge("TA", "TJ"), layout.getEdge("TJ", "TS"),
                    layout.getEdge("TS", "TB")));
                milestones.put(a, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("TA"))));

                active.put(c, Arrays.asList(layout.getEdge("TS", "TC")));
                milestones.put(c, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("TS"))));

                pairs++;

                java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt = layout.getLastPointsReached();

                java.util.Map<String, String[]> read = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(layout);

                java.util.Map<String, String> written = new java.util.HashMap<>();

                for (Object point : new org.json.JSONObject((String) kept.invoke(layout, keptAt)).getJSONArray("points"))
                {
                    org.json.JSONObject p = (org.json.JSONObject) point;

                    if (p.has("loc")) written.put(p.getJSONObject("loc").getString("name"), p.getString("name"));
                }

                String said = "A=" + nameA + " kept at " + (keptAt.get(a) == null ? null : keptAt.get(a).getName())
                    + ", C=" + nameC + " kept at " + (keptAt.get(c) == null ? null : keptAt.get(c).getName())
                    + "; the carry reads A at " + (read.get(nameA) == null ? null : read.get(nameA)[0]) + ", C at "
                    + (read.get(nameC) == null ? null : read.get(nameC)[0]) + "; the fold writes " + written;

                if (keptAt.get(a) != layout.getPoint("TB") || keptAt.get(c) != layout.getPoint("TS")
                    || read.get(nameA) == null || !"TB".equals(read.get(nameA)[0])
                    || read.get(nameC) == null || !"TS".equals(read.get(nameC)[0])
                    || !"TB".equals(written.get(nameA)) || !"TS".equals(written.get(nameC))
                    || !"TA".equals(written.get(nameB)))
                {
                    wrong.add(said);
                }

                active.clear();
                milestones.clear();
            }
        }

        assertTrue(pairs >= 6, "precondition: fewer than three locomotives to pair");

        assertTrue(wrong.isEmpty(), wrong.size() + " of " + pairs + " orders keep A on TS, where C is kept, or on TJ,"
            + " which is no station, or read or write one of them on no point (RLV10-C2, RLV10-C5): " + wrong);
    }

    /**
     * A train bound for the station another train was last seen at takes it, and the other - past it, since the station
     * was released behind it and then locked for the first - is kept on the station ahead it holds (RLV11-C1).  The rule
     * ahead passed over another's kept point, so where that was the only station the first train held, its destination,
     * it stayed where a third train now stands and a fold erased it, in every order.
     *
     * A set off from TA along TA-TJ-TS, past no sensor, and B has since stopped on TA; C set off from TS towards TC, past
     * no sensor, and TS was released behind it and locked for A.
     *
     * MUTATION: pass over another's kept point with no way to move that one on, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testATrainBoundForAnothersKeptStationTakesItAndTheOtherGoesOn() throws Exception
    {
        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        java.lang.reflect.Method kept = Layout.class.getMethod("toJSON", java.util.Map.class);

        List<String> wrong = new ArrayList<>();

        int pairs = 0;

        List<String> names = model.getLocList().subList(0, Math.min(7, model.getLocList().size()));

        for (String nameA : names)
        {
            for (String nameC : names)
            {
                if (nameA.equals(nameC)) continue;

                String nameB = null;

                for (String other : names)
                {
                    if (nameB == null && !other.equals(nameA) && !other.equals(nameC)) nameB = other;
                }

                Locomotive a = model.getLocByName(nameA);
                Locomotive b = model.getLocByName(nameB);
                Locomotive c = model.getLocByName(nameC);

                Layout layout = new Layout(model);

                layout.createPoint("TA", true, "194");
                layout.createPoint("TJ", false, null);
                layout.createPoint("TS", true, "195");
                layout.createPoint("TC", true, "197");

                layout.createEdge("TA", "TJ");
                layout.createEdge("TJ", "TS");
                layout.createEdge("TS", "TC");

                reserve.invoke(layout.getPoint("TC"), c);
                reserve.invoke(layout.getPoint("TJ"), a);
                reserve.invoke(layout.getPoint("TS"), a);

                layout.getPoint("TA").setLocomotive(b);

                java.util.Map<Locomotive, List<Edge>> active =
                    (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
                java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
                    (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

                active.put(a, Arrays.asList(layout.getEdge("TA", "TJ"), layout.getEdge("TJ", "TS")));
                milestones.put(a, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("TA"))));

                active.put(c, Arrays.asList(layout.getEdge("TS", "TC")));
                milestones.put(c, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("TS"))));

                pairs++;

                String said = keptReadAndWritten(layout, kept, nameA, nameB, nameC);

                if (!said.equals("A@TS C@TC | carry A@TS C@TC | fold A@TS B@TA C@TC")) wrong.add(nameA + "/" + nameC + ": " + said);

                active.clear();
                milestones.clear();
            }
        }

        assertTrue(pairs >= 6, "precondition: fewer than three locomotives to pair");

        assertTrue(wrong.isEmpty(), wrong.size() + " of " + pairs + " orders do not keep A on TS, its destination, and"
            + " C on TC ahead of it - one of them is kept, read or written nowhere (RLV11-C1): " + wrong);
    }

    /**
     * Two trains under way last seen at one station that neither holds and nobody stands on are both kept, on different
     * points, and the carry and a fold keep the same ones (RLV11-C2): one keeps the station, the other the station ahead
     * it holds.  Both were kept at the station, the carry read one and a fold wrote the other, and one of them was erased
     * from the configuration in every order.
     *
     * K1 set off from KP towards KS1 and K2 towards KS2, each past no sensor, and KP was released behind both.
     *
     * MUTATION: leave two trains kept at one point, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testTwoTrainsLastSeenAtOneStationAreBothKept() throws Exception
    {
        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        java.lang.reflect.Method kept = Layout.class.getMethod("toJSON", java.util.Map.class);

        List<String> wrong = new ArrayList<>();

        int pairs = 0;

        List<String> names = model.getLocList().subList(0, Math.min(7, model.getLocList().size()));

        for (String name1 : names)
        {
            for (String name2 : names)
            {
                if (name1.equals(name2)) continue;

                Locomotive k1 = model.getLocByName(name1);
                Locomotive k2 = model.getLocByName(name2);

                Layout layout = new Layout(model);

                layout.createPoint("KP", true, "194");
                layout.createPoint("KJ1", false, null);
                layout.createPoint("KJ2", false, null);
                layout.createPoint("KS1", true, "195");
                layout.createPoint("KS2", true, "196");

                layout.createEdge("KP", "KJ1");
                layout.createEdge("KJ1", "KS1");
                layout.createEdge("KP", "KJ2");
                layout.createEdge("KJ2", "KS2");

                reserve.invoke(layout.getPoint("KJ1"), k1);
                reserve.invoke(layout.getPoint("KS1"), k1);
                reserve.invoke(layout.getPoint("KJ2"), k2);
                reserve.invoke(layout.getPoint("KS2"), k2);

                java.util.Map<Locomotive, List<Edge>> active =
                    (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
                java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
                    (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

                active.put(k1, Arrays.asList(layout.getEdge("KP", "KJ1"), layout.getEdge("KJ1", "KS1")));
                milestones.put(k1, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("KP"))));

                active.put(k2, Arrays.asList(layout.getEdge("KP", "KJ2"), layout.getEdge("KJ2", "KS2")));
                milestones.put(k2, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("KP"))));

                pairs++;

                java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt = layout.getLastPointsReached();

                java.util.Map<String, String[]> read = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(layout);

                java.util.Map<String, String> written = new java.util.HashMap<>();

                for (Object point : new org.json.JSONObject((String) kept.invoke(layout, keptAt)).getJSONArray("points"))
                {
                    org.json.JSONObject p = (org.json.JSONObject) point;

                    if (p.has("loc")) written.put(p.getJSONObject("loc").getString("name"), p.getString("name"));
                }

                String at1 = keptAt.get(k1) == null ? null : keptAt.get(k1).getName();
                String at2 = keptAt.get(k2) == null ? null : keptAt.get(k2).getName();

                boolean apart = at1 != null && at2 != null && !at1.equals(at2);
                boolean eachOwn = ("KP".equals(at1) || "KS1".equals(at1)) && ("KP".equals(at2) || "KS2".equals(at2));
                boolean readSo = read.get(name1) != null && at1 != null && at1.equals(read.get(name1)[0])
                    && read.get(name2) != null && at2 != null && at2.equals(read.get(name2)[0]);
                boolean writtenSo = at1 != null && at1.equals(written.get(name1)) && at2 != null
                    && at2.equals(written.get(name2));

                if (!apart || !eachOwn || !readSo || !writtenSo)
                {
                    wrong.add(name1 + "@" + at1 + ", " + name2 + "@" + at2 + "; read " + (read.get(name1) == null ? null
                        : read.get(name1)[0]) + "/" + (read.get(name2) == null ? null : read.get(name2)[0])
                        + "; written " + written);
                }

                active.clear();
                milestones.clear();
            }
        }

        assertTrue(pairs >= 6, "precondition: fewer than two locomotives to pair");

        assertTrue(wrong.isEmpty(), wrong.size() + " of " + pairs + " orders keep two trains last seen at KP on one"
            + " point, or read and write them differently (RLV11-C2): " + wrong);
    }

    /**
     * A chain of trains under way is moved on along it (RLV12-C1).  Z stands on P1, where A set off; B set off from P2,
     * which A now holds, and C from P3, which B now holds.  A can take P2 only if B goes on to P3, and B only if C goes on
     * to P4.  One step was the limit: A stayed on Z's station, and the fold wrote it nowhere, in every order.
     *
     * MUTATION: move a train on one step only, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testAChainOfTrainsUnderWayIsMovedOnAlongIt() throws Exception
    {
        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        List<String> wrong = new ArrayList<>();

        int orders = 0;

        List<String> names = model.getLocList().subList(0, Math.min(5, model.getLocList().size()));

        for (String nA : names) for (String nB : names) for (String nC : names) for (String nZ : names)
        {
            if (new java.util.HashSet<>(Arrays.asList(nA, nB, nC, nZ)).size() < 4) continue;

            Locomotive a = model.getLocByName(nA);
            Locomotive b = model.getLocByName(nB);
            Locomotive c = model.getLocByName(nC);
            Locomotive z = model.getLocByName(nZ);

            Layout layout = new Layout(model);

            layout.createPoint("CP1", true, "194");
            layout.createPoint("CJ1", false, null);
            layout.createPoint("CP2", true, "195");
            layout.createPoint("CJ2", false, null);
            layout.createPoint("CP3", true, "196");
            layout.createPoint("CJ3", false, null);
            layout.createPoint("CP4", true, "197");

            layout.createEdge("CP1", "CJ1");
            layout.createEdge("CJ1", "CP2");
            layout.createEdge("CP2", "CJ2");
            layout.createEdge("CJ2", "CP3");
            layout.createEdge("CP3", "CJ3");
            layout.createEdge("CJ3", "CP4");

            reserve.invoke(layout.getPoint("CJ1"), a);
            reserve.invoke(layout.getPoint("CP2"), a);
            reserve.invoke(layout.getPoint("CJ2"), b);
            reserve.invoke(layout.getPoint("CP3"), b);
            reserve.invoke(layout.getPoint("CJ3"), c);
            reserve.invoke(layout.getPoint("CP4"), c);

            layout.getPoint("CP1").setLocomotive(z);

            java.util.Map<Locomotive, List<Edge>> active =
                (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
            java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
                (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

            active.put(a, Arrays.asList(layout.getEdge("CP1", "CJ1"), layout.getEdge("CJ1", "CP2")));
            milestones.put(a, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("CP1"))));
            active.put(b, Arrays.asList(layout.getEdge("CP2", "CJ2"), layout.getEdge("CJ2", "CP3")));
            milestones.put(b, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("CP2"))));
            active.put(c, Arrays.asList(layout.getEdge("CP3", "CJ3"), layout.getEdge("CJ3", "CP4")));
            milestones.put(c, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("CP3"))));

            orders++;

            java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt = layout.getLastPointsReached();
            java.util.Map<String, String[]> read = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(layout);
            java.util.Map<String, String> written = foldOf(layout, keptAt);

            String said = "kept A@" + nameOf(keptAt.get(a)) + " B@" + nameOf(keptAt.get(b)) + " C@" + nameOf(keptAt.get(c))
                + " | carry A@" + at(read, nA) + " B@" + at(read, nB) + " C@" + at(read, nC) + " Z@" + at(read, nZ)
                + " | fold A@" + written.get(nA) + " B@" + written.get(nB) + " C@" + written.get(nC) + " Z@"
                + written.get(nZ);

            if (!said.equals("kept A@CP2 B@CP3 C@CP4 | carry A@CP2 B@CP3 C@CP4 Z@CP1 | fold A@CP2 B@CP3 C@CP4 Z@CP1"))
            {
                wrong.add(nA + "/" + nB + "/" + nC + "/" + nZ + ": " + said);
            }

            active.clear();
            milestones.clear();
        }

        assertTrue(orders >= 24, "precondition: fewer than four locomotives to order");

        assertTrue(wrong.isEmpty(), wrong.size() + " of " + orders + " orders do not move the chain on - A left on Z's"
            + " station is kept, read and written nowhere (RLV12-C1): " + wrong);
    }

    /**
     * A train under way is not kept on a square a standing train stands on, whichever copy either is on (RLV12-C2).  SE
     * and SW are two copies of one square; A set off from SE towards T, past no sensor, and SE was released behind it; Z
     * has since stopped on SW.  Kept at SE, A stood on Z's square: the fold wrote two trains on one square and the carry,
     * putting A back, took Z off the railway.
     *
     * MUTATION: keep trains apart by point rather than by square, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testATrainUnderWayIsNotKeptOnTheSquareAStandingTrainStandsOn() throws Exception
    {
        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        List<String> wrong = new ArrayList<>();

        int pairs = 0;

        List<String> names = model.getLocList().subList(0, Math.min(7, model.getLocList().size()));

        for (String nA : names) for (String nZ : names)
        {
            if (nA.equals(nZ)) continue;

            Locomotive a = model.getLocByName(nA);
            Locomotive z = model.getLocByName(nZ);

            Layout layout = aSplitSquare();

            reserve.invoke(layout.getPoint("SJ"), a);
            reserve.invoke(layout.getPoint("ST"), a);

            layout.getPoint("SW").setLocomotive(z);

            java.util.Map<Locomotive, List<Edge>> active =
                (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
            java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
                (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

            active.put(a, Arrays.asList(layout.getEdge("SE", "SJ"), layout.getEdge("SJ", "ST")));
            milestones.put(a, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("SE"))));

            pairs++;

            java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt = layout.getLastPointsReached();
            java.util.Map<String, String[]> read = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(layout);
            java.util.Map<String, String> written = foldOf(layout, keptAt);

            // THE CARRY onto a railway rebuilt with the same squares
            Layout built = aSplitSquare();

            org.traincontrol.gui.TrainControlUI.putTheTrainsBack(built, read, line -> { }, null);

            String said = "kept A@" + nameOf(keptAt.get(a)) + " | carry A@" + at(read, nA) + " Z@" + at(read, nZ)
                + " | fold A@" + written.get(nA) + " Z@" + written.get(nZ) + " | after the carry A@"
                + nameOf(built.getLocomotiveLocation(a)) + " Z@" + nameOf(built.getLocomotiveLocation(z));

            if (!said.equals("kept A@ST | carry A@ST Z@SW | fold A@ST Z@SW | after the carry A@ST Z@SW"))
            {
                wrong.add(nA + "/" + nZ + ": " + said);
            }

            active.clear();
            milestones.clear();
        }

        assertTrue(pairs >= 6, "precondition: fewer than two locomotives to pair");

        assertTrue(wrong.isEmpty(), wrong.size() + " of " + pairs + " orders keep A on the square Z stands on, and a fold"
            + " or the carry then loses one of them (RLV12-C2): " + wrong);
    }

    /**
     * Two trains under way last seen at the two copies of one square are not both kept there (RLV12-C2): one keeps its
     * copy and the other the station ahead it holds.  Both were kept, the fold wrote two trains on one square, and the
     * carry left one of them off the railway.
     *
     * MUTATION: keep trains apart by point rather than by square, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testTwoTrainsUnderWayAreNotKeptOnOneSquare() throws Exception
    {
        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        List<String> wrong = new ArrayList<>();

        int pairs = 0;

        List<String> names = model.getLocList().subList(0, Math.min(7, model.getLocList().size()));

        for (String nA : names) for (String nB : names)
        {
            if (nA.equals(nB)) continue;

            Locomotive a = model.getLocByName(nA);
            Locomotive b = model.getLocByName(nB);

            Layout layout = aSplitSquareWithTwoWaysOut();

            reserve.invoke(layout.getPoint("SJ"), a);
            reserve.invoke(layout.getPoint("ST"), a);
            reserve.invoke(layout.getPoint("SK"), b);
            reserve.invoke(layout.getPoint("SU"), b);

            java.util.Map<Locomotive, List<Edge>> active =
                (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
            java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
                (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

            active.put(a, Arrays.asList(layout.getEdge("SE", "SJ"), layout.getEdge("SJ", "ST")));
            milestones.put(a, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("SE"))));
            active.put(b, Arrays.asList(layout.getEdge("SW", "SK"), layout.getEdge("SK", "SU")));
            milestones.put(b, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("SW"))));

            pairs++;

            java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt = layout.getLastPointsReached();
            java.util.Map<String, String[]> read = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(layout);
            java.util.Map<String, String> written = foldOf(layout, keptAt);

            Layout built = aSplitSquareWithTwoWaysOut();

            org.traincontrol.gui.TrainControlUI.putTheTrainsBack(built, read, line -> { }, null);

            org.traincontrol.automation.Point keptA = keptAt.get(a);
            org.traincontrol.automation.Point keptB = keptAt.get(b);

            boolean apart = keptA != null && keptB != null && !keptA.isSamePlaceAs(keptB);
            boolean each = ("SE".equals(nameOf(keptA)) || "ST".equals(nameOf(keptA)))
                && ("SW".equals(nameOf(keptB)) || "SU".equals(nameOf(keptB)));
            boolean readSo = nameOf(keptA).equals(at(read, nA)) && nameOf(keptB).equals(at(read, nB));
            boolean writtenSo = nameOf(keptA).equals(written.get(nA)) && nameOf(keptB).equals(written.get(nB));
            boolean carried = nameOf(keptA).equals(nameOf(built.getLocomotiveLocation(a)))
                && nameOf(keptB).equals(nameOf(built.getLocomotiveLocation(b)));

            if (!apart || !each || !readSo || !writtenSo || !carried)
            {
                wrong.add(nA + "@" + nameOf(keptA) + ", " + nB + "@" + nameOf(keptB) + "; read " + at(read, nA) + "/"
                    + at(read, nB) + "; written " + written + "; after the carry " + nameOf(built.getLocomotiveLocation(a))
                    + "/" + nameOf(built.getLocomotiveLocation(b)));
            }

            active.clear();
            milestones.clear();
        }

        assertTrue(pairs >= 6, "precondition: fewer than two locomotives to pair");

        assertTrue(wrong.isEmpty(), wrong.size() + " of " + pairs + " orders keep two trains on the two copies of one"
            + " square, and a fold or the carry then loses one of them (RLV12-C2): " + wrong);
    }

    /**
     * Of two trains last seen at one station, the one the station still records keeps it (RLV12-C7).  X tripped OP's
     * sensor and is passing through it, and holds it; Y set off from OP before X locked it.  X is kept at OP and Y goes on
     * to the station ahead it holds - in every order of their names, which is what the rule's first half is for: the
     * claims before this had no point that recorded the train kept there, and a rule by name alone passed them.
     *
     * MUTATION: prefer by name alone, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testTheTrainItsStationStillRecordsKeepsIt() throws Exception
    {
        java.lang.reflect.Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod("reserve",
            Locomotive.class);

        reserve.setAccessible(true);

        List<String> wrong = new ArrayList<>();

        int pairs = 0;

        List<String> names = model.getLocList().subList(0, Math.min(7, model.getLocList().size()));

        for (String nX : names) for (String nY : names)
        {
            if (nX.equals(nY)) continue;

            Locomotive x = model.getLocByName(nX);
            Locomotive y = model.getLocByName(nY);

            Layout layout = new Layout(model);

            layout.createPoint("OX", true, "194");
            layout.createPoint("OP", true, "195");
            layout.createPoint("OJ", false, null);
            layout.createPoint("OT", true, "196");
            layout.createPoint("OK", false, null);
            layout.createPoint("OU", true, "197");

            layout.createEdge("OX", "OP");
            layout.createEdge("OP", "OJ");
            layout.createEdge("OJ", "OT");
            layout.createEdge("OP", "OK");
            layout.createEdge("OK", "OU");

            reserve.invoke(layout.getPoint("OP"), x);
            reserve.invoke(layout.getPoint("OJ"), x);
            reserve.invoke(layout.getPoint("OT"), x);
            reserve.invoke(layout.getPoint("OK"), y);
            reserve.invoke(layout.getPoint("OU"), y);

            java.util.Map<Locomotive, List<Edge>> active =
                (java.util.Map<Locomotive, List<Edge>>) field(layout, "activeLocomotives");
            java.util.Map<Locomotive, List<org.traincontrol.automation.Point>> milestones =
                (java.util.Map<Locomotive, List<org.traincontrol.automation.Point>>) field(layout, "locomotiveMilestones");

            active.put(x, Arrays.asList(layout.getEdge("OX", "OP"), layout.getEdge("OP", "OJ"), layout.getEdge("OJ", "OT")));
            milestones.put(x, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("OX"),
                layout.getPoint("OP"))));
            active.put(y, Arrays.asList(layout.getEdge("OP", "OK"), layout.getEdge("OK", "OU")));
            milestones.put(y, new java.util.concurrent.CopyOnWriteArrayList<>(Arrays.asList(layout.getPoint("OP"))));

            pairs++;

            java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt = layout.getLastPointsReached();

            if (!"OP".equals(nameOf(keptAt.get(x))) || !"OU".equals(nameOf(keptAt.get(y))))
            {
                wrong.add(nX + "@" + nameOf(keptAt.get(x)) + ", " + nY + "@" + nameOf(keptAt.get(y)));
            }

            active.clear();
            milestones.clear();
        }

        assertTrue(pairs >= 6, "precondition: fewer than two locomotives to pair");

        assertTrue(wrong.isEmpty(), wrong.size() + " of " + pairs + " orders do not keep X, which OP still records, at OP"
            + " and Y on the station ahead it holds (RLV12-C7): " + wrong);
    }

    /** SE and SW, two copies of one square; SE leads on to ST through SJ, and SW is reached from SX. */
    private static Layout aSplitSquare() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("SE", true, "194");
        layout.createPoint("SW", true, "194");
        layout.createPoint("SJ", false, null);
        layout.createPoint("ST", true, "195");
        layout.createPoint("SX", false, null);

        layout.getPoint("SE").setBlock("S");
        layout.getPoint("SW").setBlock("S");

        layout.createEdge("SE", "SJ");
        layout.createEdge("SJ", "ST");
        layout.createEdge("SX", "SW");

        return layout;
    }

    /** The same, with SW leading on to SU through SK. */
    private static Layout aSplitSquareWithTwoWaysOut() throws Exception
    {
        Layout layout = aSplitSquare();

        layout.createPoint("SK", false, null);
        layout.createPoint("SU", true, "196");

        layout.createEdge("SW", "SK");
        layout.createEdge("SK", "SU");

        return layout;
    }

    /** Each train a fold writes, against the point it writes it on. */
    private static java.util.Map<String, String> foldOf(Layout layout,
        java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt) throws Exception
    {
        java.util.Map<String, String> written = new java.util.TreeMap<>();

        for (Object point : new org.json.JSONObject(layout.toJSON(keptAt)).getJSONArray("points"))
        {
            org.json.JSONObject p = (org.json.JSONObject) point;

            if (p.has("loc")) written.put(p.getJSONObject("loc").getString("name"), p.getString("name"));
        }

        return written;
    }

    /** A point's name, or null. */
    private static String nameOf(org.traincontrol.automation.Point point)
    {
        return point == null ? "null" : point.getName();
    }

    /** Where the carry reads a train, or null. */
    private static String at(java.util.Map<String, String[]> read, String name)
    {
        return read.get(name) == null ? "null" : read.get(name)[0];
    }

    /** Where A, B and C are kept, read by the carry and written by a fold, as one line. */
    private static String keptReadAndWritten(Layout layout, java.lang.reflect.Method kept, String nameA, String nameB,
        String nameC) throws Exception
    {
        java.util.Map<Locomotive, org.traincontrol.automation.Point> keptAt = layout.getLastPointsReached();

        java.util.Map<String, String[]> read = org.traincontrol.gui.TrainControlUI.whereTheTrainsAre(layout);

        java.util.TreeMap<String, String> written = new java.util.TreeMap<>();

        for (Object point : new org.json.JSONObject((String) kept.invoke(layout, keptAt)).getJSONArray("points"))
        {
            org.json.JSONObject p = (org.json.JSONObject) point;

            if (!p.has("loc")) continue;

            String who = p.getJSONObject("loc").getString("name");

            written.put(who.equals(nameA) ? "A" : who.equals(nameB) ? "B" : who.equals(nameC) ? "C" : who, p.getString("name"));
        }

        Locomotive a = model.getLocByName(nameA);
        Locomotive c = model.getLocByName(nameC);

        StringBuilder fold = new StringBuilder();

        for (java.util.Map.Entry<String, String> w : written.entrySet())
        {
            if (fold.length() > 0) fold.append(' ');

            fold.append(w.getKey()).append('@').append(w.getValue());
        }

        return "A@" + (keptAt.get(a) == null ? null : keptAt.get(a).getName()) + " C@"
            + (keptAt.get(c) == null ? null : keptAt.get(c).getName()) + " | carry A@"
            + (read.get(nameA) == null ? null : read.get(nameA)[0]) + " C@"
            + (read.get(nameC) == null ? null : read.get(nameC)[0]) + " | fold " + fold;
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

    /**
     * A tail answered while Return Home is planning is not written (RLV13-C3).  Return Home plans with nothing yet
     * running, so the railway's `isRunning` reads false while the flow is under way; every other refusal of a setup edit
     * asks whether autonomy is busy, which counts the planning.  `whereTheAnswerGoes` asked `isRunning` alone.
     *
     * MUTATION: let `whereTheAnswerGoes` answer while a staging flow owns the railway, and this fails.
     *
     * @throws Exception from the fixture
     */
    @Test
    public void testATailAnsweredWhileReturnHomePlansIsNotWritten() throws Exception
    {
        Layout layout = new Layout(model);

        layout.createPoint("TQ", true, "194");

        org.traincontrol.automation.Point at = layout.getPoint("TQ");

        Locomotive train = model.getLocByName(model.getLocList().get(0));

        at.setLocomotive(train);

        assertEquals(org.traincontrol.gui.TailCrossedPrompt.whereTheAnswerGoes(layout, at, train, at.getArrivedFrom(),
            at.getArrivedAlong(), true), at, "precondition: at rest, the answer does not go to the square it was asked"
            + " about");

        layout.setStagingInProgress(true);

        try
        {
            assertEquals(org.traincontrol.gui.TailCrossedPrompt.whereTheAnswerGoes(layout, at, train, at.getArrivedFrom(),
                at.getArrivedAlong(), true), null, "a tail answered while Return Home plans - nothing running yet - was"
                + " written into the setup and onto the railway (RLV13-C3)");
        }
        finally
        {
            layout.setStagingInProgress(false);
        }
    }

    /**
     * A retired railway's journey ends with its railway (RSA-B1).  The reload's and Unload's Yes stop every train between
     * two sensors, and each journey's thread went on waiting, with no time limit, for a sensor its stopped train would not
     * reach - until any train occupied it, when the thread stopped its locomotive: by then, perhaps, a train the next
     * railway was driving, left standing mid-route with its route held.  A retirement now wakes every wait, and a journey
     * whose railway has gone ends there.
     *
     * MUTATION: let a retirement wake nothing, or let the wait go on past its railway, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testARetiredRailwaysJourneyEndsWithItsRailway() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1801, 4);

        final Locomotive x = model.getLocByName(model.getLocList().get(0));

        Thread before = null;
        Thread after = null;

        try
        {
            x.setSpeed(0);

            // THE OLD RAILWAY: GA -> GB -> GC, and GA -> GD
            final Layout old = aForkedRailway(s);

            old.getPoint("GA").setLocomotive(x);

            before = sendOn(old, Arrays.asList(old.getEdge("GA", "GB"), old.getEdge("GB", "GC")), x);

            assertTrue(waitFor(() -> x.getSpeed() == 30 && old.getActiveLocomotives().containsKey(x), 10000),
                "precondition: the train was not sent on the old railway");

            // THE YES, as the reload's door answers it, and then the next railway
            old.stopLocomotives();

            for (Locomotive active : old.getActiveLocomotives().keySet()) active.setSpeed(0);

            final Layout fresh = aForkedRailway(s);

            fresh.getPoint("GA").setLocomotive(x);

            assertFalse(old.isCurrentLayout(), "precondition: the old railway was not retired");

            // THE OLD JOURNEY ENDS WITH ITS RAILWAY, with no sensor occupied
            final Thread waiting = before;

            // Two seconds, under the wait's five-second poll: it is the retirement that must end it, not the clock
            assertTrue(waitFor(() -> !waiting.isAlive(), 2000), "the retired railway's journey is still waiting on "
                + s[1].getName() + ", a sensor its stopped train will not reach, and when anything occupies it the"
                + " thread will stop its locomotive - whichever railway is driving it then (RSA-B1)");

            // THE NEXT RAILWAY SENDS THE SAME TRAIN ELSEWHERE, and another train then crosses the old journey's sensor
            after = sendOn(fresh, Arrays.asList(fresh.getEdge("GA", "GD")), x);

            assertTrue(waitFor(() -> x.getSpeed() > 0 && fresh.getActiveLocomotives().containsKey(x), 10000),
                "precondition: the next railway did not send the train");

            final int driven = x.getSpeed();

            model.setFeedbackState(s[1].getName(), true);

            Thread.sleep(1500);

            assertEquals(x.getSpeed(), driven, "a sensor the retired railway's journey had waited on stopped a train the"
                + " next railway is driving (RSA-B1)");
        }
        finally
        {
            model.setFeedbackState(s[1].getName(), false);
            model.setFeedbackState(s[3].getName(), true);

            if (after != null) after.join(10000);

            letGo(s, x, before);
        }
    }

    /**
     * The reload's Yes stops a train still claiming its route (RSA-C1, GS-C1).  The Yes stopped the trains already under
     * way; one still locking its route - seconds, on a path with switches - was not among them, and when the lock came
     * through it set off.  A claim that completes after the Yes is given back unrun, and the train stands where it was.
     *
     * MUTATION: let a claim completed after the Yes go on to run, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheYesStopsATrainStillClaimingItsRoute() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1811, 5);

        final Locomotive y = model.getLocByName(model.getLocList().get(1));

        Thread claiming = null;

        final Layout rail = aLine(s, "HA", "HB", "HC", "HD", "HE");

        final List<String> logged = java.util.Collections.synchronizedList(new ArrayList<>());

        final java.util.logging.Handler tap = aTapOn(logged);

        try
        {
            y.setSpeed(0);

            rail.getPoint("HA").setLocomotive(y);

            // THE RAILWAY'S MONITOR HELD, so the dispatch waits to claim its route - as it does behind a slow switch
            synchronized (rail)
            {
                claiming = sendOn(rail, through(rail, "HA", "HB", "HC", "HD", "HE"), y);

                final Thread blocked = claiming;

                assertTrue(waitFor(() -> blocked.getState() == Thread.State.BLOCKED, 5000),
                    "precondition: the dispatch did not reach its claim");

                // THE YES
                theYes(rail);
            }

            assertFalse(waitFor(() -> y.getSpeed() > 0, 2000), "a train still claiming its route when the operator"
                + " answered Yes set off once the claim came through (RSA-C1, GS-C1)");

            assertFalse(rail.getActiveLocomotives().containsKey(y), "a claim completed after the Yes was kept as a"
                + " journey, holding its route with its train stopped at the start (RSA-C1)");

            assertEquals(rail.getPoint("HA").getCurrentLocomotive(), y, "the train given back is not standing where it"
                + " was (RSA-C1)");

            // AND SAYS SO (RSA2-C8): a hand send answers the false with "check log"
            assertTrue(logged.contains(org.traincontrol.util.I18n.f("autolayout.log.notSentAfterTheStop", y.getName())),
                "a claim given back after the operator's Yes said nothing in the log (RSA2-C8): " + logged);
        }
        finally
        {
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName())
                .removeHandler(tap);

            letGo(s, y, claiming);
        }
    }

    /**
     * The reload's Yes stops a train whose journey has just been counted and not yet set off (RSA-C1, GS-C1): the
     * departure's speed is asked of the Yes as every other speed is.
     *
     * MUTATION: let the departure write its speed whatever the Yes said, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheYesStopsATrainAtItsDeparture() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1841, 3);

        final Locomotive w = model.getLocByName(model.getLocList().get(4));

        Thread departing = null;

        final Layout rail = aLine(s, "LA", "LB", "LC");

        final java.util.function.Consumer<Locomotive> startWas = w.hasCallback(Layout.CB_ROUTE_START)
            ? w.getCallback(Layout.CB_ROUTE_START) : l -> { };

        try
        {
            w.setSpeed(0);

            rail.getPoint("LA").setLocomotive(w);

            // THE YES, answered as the journey is counted - the route-start callback runs between that and the departure
            w.setCallback(Layout.CB_ROUTE_START, l -> theYesUnchecked(rail));

            departing = sendOn(rail, through(rail, "LA", "LB", "LC"), w);

            assertTrue(waitFor(() -> rail.getActiveLocomotives().containsKey(w), 5000), "precondition: the journey was not"
                + " counted");

            assertFalse(waitFor(() -> w.getSpeed() > 0, 2000), "a train whose journey was counted when the operator"
                + " answered Yes set off after it (RSA-C1, GS-C1)");
        }
        finally
        {
            w.setCallback(Layout.CB_ROUTE_START, startWas);

            letGo(s, w, departing);
        }
    }

    /**
     * The reload's Yes is not undone at the next sensor (RSA-C1).  A train the Yes stopped between two sensors, braking
     * onto the one ahead before any load had retired its railway, was given its speed back by its own journey - the next
     * leg's, and then the pre-arrival speed.
     *
     * MUTATION: let the next leg's speed, or the pre-arrival speed, be written whatever the Yes said, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheYesIsNotUndoneAtTheNextSensor() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1821, 4);

        final Locomotive z = model.getLocByName(model.getLocList().get(2));

        Thread running = null;

        final Layout rail = aLine(s, "IA", "IB", "IC", "ID");

        try
        {
            z.setSpeed(0);

            rail.getPoint("IA").setLocomotive(z);

            running = sendOn(rail, through(rail, "IA", "IB", "IC", "ID"), z);

            assertTrue(waitFor(() -> z.getSpeed() == 30 && rail.getActiveLocomotives().containsKey(z), 10000),
                "precondition: the train was not sent");

            theYes(rail);

            assertEquals(z.getSpeed(), 0, "precondition: the Yes did not stop the train");

            // It brakes onto the sensor ahead, before any load has retired this railway: the next leg
            model.setFeedbackState(s[1].getName(), true);

            Thread.sleep(1200);

            assertEquals(z.getSpeed(), 0, "a train the operator's Yes had stopped was given the next leg's speed back"
                + " by its own journey at the sensor ahead (RSA-C1)");

            // And the one after it: the leg into the destination
            model.setFeedbackState(s[1].getName(), false);
            model.setFeedbackState(s[2].getName(), true);

            Thread.sleep(1200);

            assertEquals(z.getSpeed(), 0, "a train the operator's Yes had stopped was given the pre-arrival speed back by"
                + " its own journey (RSA-C1)");
        }
        finally
        {
            letGo(s, z, running);
        }
    }

    /**
     * The reload's Yes stops a train still choosing its route (RSA-C1).  Autonomy's thread for a train chooses a route
     * and then sends it, and it did not ask again in between whether the railway was still running - so a train whose
     * choice was under way when the Yes came was sent after it.
     *
     * MUTATION: let autonomy's thread send the route it chose without asking whether the railway still runs, and this
     * fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheYesStopsATrainStillChoosingItsRoute() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1831, 2);

        final Locomotive v = model.getLocByName(model.getLocList().get(3));

        final int preferredWas = v.getPreferredSpeed();

        final Layout rail = aLine(s, "KA", "KB");

        try
        {
            v.setSpeed(0);
            v.setPreferredSpeed(30);

            rail.getPoint("KA").setLocomotive(v);
            rail.setLocomotivesToRun(Arrays.asList(v));

            // THE ROUTE'S RAIL HELD, so autonomy's thread waits in the middle of choosing - where its check of the
            // route asks the rail whether it is occupied
            synchronized (rail.getEdge("KA", "KB"))
            {
                rail.runLocomotives();

                assertTrue(waitFor(() -> aThreadIsBlockedIn("pickPath"), 5000), "precondition: autonomy's thread did"
                    + " not reach its choice");

                theYes(rail);
            }

            assertFalse(waitFor(() -> v.getSpeed() > 0 || rail.isAlreadyUnderway(v), 2000), "a train autonomy was"
                + " choosing a route for when the operator answered Yes was sent along it after the Yes (RSA-C1)");
        }
        finally
        {
            rail.stopLocomotives();

            v.setPreferredSpeed(preferredWas);

            letGo(s, v, null);
        }
    }

    /**
     * The reload's door stops the trains through the railway's own stop, the one every journey asks (RSA-C1).
     *
     * MUTATION: let the door stop the trains itself, one speed at a time, and this fails.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheReloadsYesIsTheRailwaysOwnStop() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")), java.nio.charset.StandardCharsets.UTF_8);

        int at = source.indexOf("public boolean prepareAutonomyReload()");

        assertTrue(at >= 0, "cannot find prepareAutonomyReload");

        String door = source.substring(at, source.indexOf("resetLayoutStationLabels();", at));

        assertTrue(door.contains(".stopEveryTrainWhereItIs()"), "the reload's Yes does not stop the trains through the"
            + " railway's own stop, so a journey still claiming, choosing or reaching its next sensor goes on (RSA-C1)");
    }

    /** New sensors, all clear, at consecutive addresses from `first`. */
    private static org.traincontrol.marklin.MarklinFeedback[] sensors(int first, int count)
    {
        org.traincontrol.marklin.MarklinFeedback[] s = new org.traincontrol.marklin.MarklinFeedback[count];

        for (int i = 0; i < count; i++)
        {
            s[i] = model.newFeedback(first + i, null);

            model.setFeedbackState(s[i].getName(), false);
        }

        return s;
    }

    /** A railway, made current: GA -> GB -> GC, and GA -> GD, on the four sensors given. */
    private static Layout aForkedRailway(org.traincontrol.marklin.MarklinFeedback[] s) throws Exception
    {
        Layout railway = new Layout(model);

        railway.createPoint("GA", true, s[0].getName());
        railway.createPoint("GB", false, s[1].getName());
        railway.createPoint("GC", true, s[2].getName());
        railway.createPoint("GD", true, s[3].getName());
        railway.createEdge("GA", "GB");
        railway.createEdge("GB", "GC");
        railway.createEdge("GA", "GD");
        railway.makeCurrent();

        return railway;
    }

    /** A railway, made current: one line through the points named, one sensor each, its two ends stations. */
    private static Layout aLine(org.traincontrol.marklin.MarklinFeedback[] s, String... names) throws Exception
    {
        Layout railway = new Layout(model);

        for (int i = 0; i < names.length; i++)
        {
            railway.createPoint(names[i], i == 0 || i == names.length - 1, s[i].getName());
        }

        for (int i = 0; i + 1 < names.length; i++) railway.createEdge(names[i], names[i + 1]);

        railway.makeCurrent();

        return railway;
    }

    /** The path along the points named, in order. */
    private static List<Edge> through(Layout railway, String... names)
    {
        List<Edge> path = new ArrayList<>();

        for (int i = 0; i + 1 < names.length; i++) path.add(railway.getEdge(names[i], names[i + 1]));

        return path;
    }

    /** Sends a train along a path on its own thread, as a door does. */
    private static Thread sendOn(final Layout railway, final List<Edge> path, final Locomotive loc)
    {
        Thread thread = new Thread(() -> railway.executePath(path, loc, 30, null), "claim-journey-" + loc.getName());

        thread.setDaemon(true);
        thread.start();

        return thread;
    }

    /** The reload's and Unload's Yes, as the door answers it: the railway's own stop. */
    private static void theYes(Layout railway) throws Exception
    {
        Layout.class.getMethod("stopEveryTrainWhereItIs").invoke(railway);
    }

    /** The same, from a callback that cannot throw. */
    private static void theYesUnchecked(Layout railway)
    {
        try
        {
            theYes(railway);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /** Whether some thread is waiting for a monitor inside the method named. */
    private static boolean aThreadIsBlockedIn(String method)
    {
        for (java.util.Map.Entry<Thread, StackTraceElement[]> each : Thread.getAllStackTraces().entrySet())
        {
            if (each.getKey().getState() != Thread.State.BLOCKED) continue;

            for (StackTraceElement frame : each.getValue())
            {
                if (method.equals(frame.getMethodName())) return true;
            }
        }

        return false;
    }

    /** Waits for a condition, polling. */
    private static boolean waitFor(java.util.function.BooleanSupplier condition, long ms) throws InterruptedException
    {
        long until = System.currentTimeMillis() + ms;

        while (System.currentTimeMillis() < until)
        {
            if (condition.getAsBoolean()) return true;

            Thread.sleep(20);
        }

        return condition.getAsBoolean();
    }

    /** Ends whatever a claim left: a newer railway retires its journeys, the sensors clear, the train stands. */
    private static void letGo(org.traincontrol.marklin.MarklinFeedback[] s, Locomotive loc, Thread journey)
        throws InterruptedException
    {
        new Layout(model).makeCurrent();

        for (org.traincontrol.marklin.MarklinFeedback each : s) model.setFeedbackState(each.getName(), true);

        if (journey != null) journey.join(5000);

        for (org.traincontrol.marklin.MarklinFeedback each : s) model.setFeedbackState(each.getName(), false);

        loc.setSpeed(0);
    }

    /**
     * A route may not end at, or pass, a sensor another train's route is still waiting on (RSA2-B1).  Two places on one
     * sensor - a feedback double curve's two arcs, which are two pieces of metal (Adam, OB-238) with one contact - were
     * kept apart by nothing: two trains were sent to them at once, the first arrival ended both journeys, and the second
     * was recorded as arrived with its route handed back while it was still on it.  A journey waits on every sensor of
     * its route it has not reached, so no other route may wait on one of them.
     *
     * MUTATION: let a route wait on a sensor another train's route is waiting on, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testARouteMayNotWaitOnASensorAnotherTrainAwaits() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1851, 5);

        final Locomotive x = model.getLocByName(model.getLocList().get(5));
        final Locomotive y = model.getLocByName(model.getLocList().get(6));

        Thread sent = null;

        // Two places on ONE sensor, s[1]: ONE and TWO, as a feedback double curve's arcs are; and PASS, on the same sensor,
        // on the way to FAR
        final Layout rail = new Layout(model);

        try
        {
            x.setSpeed(0);
            y.setSpeed(0);

            rail.createPoint("SXA", true, s[0].getName());
            rail.createPoint("ONE", true, s[1].getName());
            rail.createPoint("SYA", true, s[2].getName());
            rail.createPoint("TWO", true, s[1].getName());
            rail.createPoint("PASS", false, s[1].getName());
            rail.createPoint("FAR", true, s[4].getName());
            rail.createEdge("SXA", "ONE");
            rail.createEdge("SYA", "TWO");
            rail.createEdge("SYA", "PASS");
            rail.createEdge("PASS", "FAR");
            rail.makeCurrent();

            rail.getPoint("SXA").setLocomotive(x);
            rail.getPoint("SYA").setLocomotive(y);

            final List<Edge> toTheOtherArc = through(rail, "SYA", "TWO");
            final List<Edge> pastIt = through(rail, "SYA", "PASS", "FAR");

            assertTrue(rail.isPathClear(toTheOtherArc, y, false), "precondition: with nothing under way, the other arc"
                + " is not clear - so this claim cannot tell the rule from the fixture: " + Layout.getLastError());

            // X SENT TO ONE: its journey now waits on the shared sensor
            sent = sendOn(rail, through(rail, "SXA", "ONE"), x);

            assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                "precondition: the first train was not sent");

            assertFalse(rail.isPathClear(toTheOtherArc, y, false), "a second train was offered the other place on the"
                + " sensor the first train's journey is waiting on - the first arrival would end both journeys (RSA2-B1)");

            assertFalse(rail.isPathClear(pastIt, y, false), "a second train was offered a route past the sensor the first"
                + " train's journey is waiting on (RSA2-B1)");

            // It arrives: its journey waits on nothing now, and the sensor clears behind it
            model.setFeedbackState(s[1].getName(), true);

            sent.join(10000);

            model.setFeedbackState(s[1].getName(), false);

            assertTrue(rail.isPathClear(toTheOtherArc, y, false), "the other arc stays refused once the first train has"
                + " arrived and its journey waits on nothing: " + Layout.getLastError());
        }
        finally
        {
            letGo(s, x, sent);

            y.setSpeed(0);
        }
    }

    /**
     * The reload's Yes ends a Return Home that was still planning (RSA2-C1).  Return Home plans on a worker with nothing
     * yet moving; a load chosen then asked the Yes, and where the load was then refused, nothing was retired and the plan
     * ran - trains moving after a Yes that said the run was abandoned.  The timetable is run with the stops counted when
     * Return Home was pressed, and a stop ordered since starts nothing.
     *
     * MUTATION: let a timetable start after a stop ordered since it was chosen, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheYesEndsAReturnHomeStillPlanning() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1861, 2);

        final Locomotive r = model.getLocByName(model.getLocList().get(7));

        final int preferredWas = r.getPreferredSpeed();

        final Layout rail = aLine(s, "RHA", "RHB");

        final boolean[] completed = new boolean[1];

        Thread running = null;

        try
        {
            r.setSpeed(0);

            // A SPEED IT MAY RUN AT, so a leg refused after the Yes is tried again - as it would be - rather than given
            // up at once as a train that cannot run, which would end the run and hide the question asked here
            r.setPreferredSpeed(30);

            rail.getPoint("RHA").setLocomotive(r);

            rail.setTimetable(new ArrayList<>(Arrays.asList(new org.traincontrol.automation.TimetablePath(r,
                through(rail, "RHA", "RHB"), 0))));

            // THE PRESS: the stops counted then
            final int atThePress = (Integer) Layout.class.getMethod("stopsOrdered").invoke(rail);

            // THE YES, while it plans
            theYes(rail);

            // AND THE PLAN RUN AFTER IT, as the worker runs it when the load is refused
            final java.lang.reflect.Method execute = Layout.class.getMethod("executeTimetable", int.class);

            running = new Thread(() ->
            {
                try
                {
                    completed[0] = (Boolean) execute.invoke(rail, atThePress);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            }, "return-home-after-the-yes");

            running.setDaemon(true);
            running.start();

            final Thread timetable = running;

            assertTrue(waitFor(() -> !timetable.isAlive(), 5000), "the timetable Return Home planned did not end after the"
                + " operator's Yes: it keeps trying to send trains (RSA2-C1)");

            assertTrue(completed[0], "the timetable after the Yes reported a move abandoned, where it should have started"
                + " nothing (RSA2-C1)");

            assertFalse(r.getSpeed() > 0 || rail.isAlreadyUnderway(r), "Return Home sent a train after the operator's Yes"
                + " (RSA2-C1)");
        }
        finally
        {
            rail.stopLocomotives();

            if (running != null) running.join(5000);

            r.setPreferredSpeed(preferredWas);

            letGo(s, r, null);
        }
    }

    /**
     * A journey chosen before the Yes is not sent after it (RSA2-C2, RSA2-C8).  Autonomy's thread asked whether it still
     * ran, and the journey read the stops only when it began - so a thread held between the two sent its train after the
     * Yes.  A journey carries the stops counted where it was chosen, and one chosen before a stop sends nothing and says
     * so in the log.
     *
     * MUTATION: let a journey chosen before a stop claim its route, or send it without a word, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAJourneyChosenBeforeTheYesIsNotSent() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1871, 2);

        final Locomotive q = model.getLocByName(model.getLocList().get(8));

        final Layout rail = aLine(s, "CHA", "CHB");

        final List<String> logged = java.util.Collections.synchronizedList(new ArrayList<>());

        final java.util.logging.Handler tap = aTapOn(logged);

        Thread journey = null;

        try
        {
            q.setSpeed(0);

            rail.getPoint("CHA").setLocomotive(q);

            // A SWITCH ON ITS WAY, which claiming the route throws: a journey turned back at the start never throws it,
            // where one given back after its claim already has
            final org.traincontrol.marklin.MarklinAccessory onTheWay = model.newSwitch(283,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, false);

            onTheWay.setSwitched(false);

            rail.getEdge("CHA", "CHB").addConfigCommand(onTheWay.getName(),
                org.traincontrol.base.Accessory.accessorySetting.TURN);

            // CHOSEN: the stops counted where the journey was chosen
            final int chosen = (Integer) Layout.class.getMethod("stopsOrdered").invoke(rail);

            // THE YES
            theYes(rail);

            // SENT, carrying the count it was chosen with - on a thread of its own, as a door sends it, so a journey that
            // does set off waits on its sensor there rather than holding this claim
            final java.lang.reflect.Method execute = Layout.class.getMethod("executePath", List.class, Locomotive.class,
                int.class, org.traincontrol.automation.TimetablePath.class, Layout.ReversalPolicy.class, int.class);

            final Object[] sent = new Object[1];

            journey = new Thread(() ->
            {
                try
                {
                    sent[0] = execute.invoke(rail, through(rail, "CHA", "CHB"), q, 30, null, Layout.ALWAYS_REVERSE,
                        chosen);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            }, "chosen-before-the-yes");

            journey.setDaemon(true);
            journey.start();

            final Thread sending = journey;

            assertTrue(waitFor(() -> !sending.isAlive(), 5000), "a journey chosen before the operator's Yes was sent"
                + " after it, and is under way (RSA2-C2)");

            assertEquals(sent[0], Boolean.FALSE, "a journey chosen before the operator's Yes was sent after it"
                + " (RSA2-C2)");

            assertFalse(q.getSpeed() > 0 || rail.isAlreadyUnderway(q), "a journey chosen before the operator's Yes"
                + " claimed its route or set off after it (RSA2-C2)");

            // NOTHING CLAIMED AT ALL
            assertFalse(onTheWay.isSwitched(), "a journey chosen before the operator's Yes claimed its route, throwing its"
                + " switch, before it was turned back (RSA2-C2)");

            assertTrue(logged.contains(org.traincontrol.util.I18n.f("autolayout.log.notSentAfterTheStop", q.getName())),
                "a journey the Yes overtook was not sent and the log does not say why - a hand send then says \"check"
                + " log\" over a log with no line (RSA2-C8): " + logged);
        }
        finally
        {
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName())
                .removeHandler(tap);

            letGo(s, q, journey);
        }
    }

    /**
     * A turn's wait for its resumed speed gives up on a stop or a retirement (RSA2-C2).  A turn resumes its speed and
     * waits for the train to reach it; a Yes landing between the two left the wait untimed and woken by nothing, so the
     * thread outlived its railway until the next driver gave the train that speed - and then stopped it.
     *
     * MUTATION: let the wait ignore what it is told to give up on, or let a retirement not wake it, and this fails.
     *
     * @throws Exception from the locomotive
     */
    @Test
    public void testATurnsResumeWaitGivesUpWhenItIsTold() throws Exception
    {
        final Locomotive t = model.getLocByName(model.getLocList().get(9));

        final java.util.concurrent.atomic.AtomicBoolean gone = new java.util.concurrent.atomic.AtomicBoolean(false);

        final java.lang.reflect.Method wait = Locomotive.class.getMethod("waitForSpeedAtOrAbove", int.class,
            java.util.function.BooleanSupplier.class);

        t.setSpeed(0);

        Thread waiting = new Thread(() ->
        {
            try
            {
                java.util.function.BooleanSupplier told = gone::get;

                wait.invoke(t, 50, told);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        }, "resume-wait");

        waiting.setDaemon(true);
        waiting.start();

        try
        {
            Thread.sleep(300);

            assertTrue(waiting.isAlive(), "precondition: the wait ended before the train reached the speed or was told");

            // TOLD TO GIVE UP, and a retirement wakes it - with no speed written
            gone.set(true);

            Locomotive.wakeEveryWait();

            final Thread asked = waiting;

            assertTrue(waitFor(() -> !asked.isAlive(), 1000), "a turn's wait for its resumed speed went on after it was"
                + " told to give up and woken, waiting for a speed only the next driver would give (RSA2-C2)");
        }
        finally
        {
            gone.set(true);

            t.setSpeed(0);

            waiting.join(2000);
        }
    }

    /**
     * Each retirement wakes the journeys waiting on its railway, once their waits have gone untimed (RSA2-C4).  A wait
     * polls until its advisory is given and then waits untimed, so after five minutes only a wake ends it.  The round 16
     * claim retired by building a railway and making it current, which wake twice over; each door is claimed alone here,
     * with the advisory lowered so the wait is untimed at once.
     *
     * MUTATION: take the wake out of Unload's retirement, a load made current, or a railway built, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testEachRetirementWakesAnUntimedWait() throws Exception
    {
        assertTrue(anUntimedJourneyEndsWhen("Unload's retirement", 1881, null, () -> Layout.retireEveryLayout()),
            "Unload's retirement left a journey waiting on a sensor its stopped train will not reach (RSA2-C4, RSA-B1)");

        final Layout[] waitingToBeLoaded = new Layout[1];

        assertTrue(anUntimedJourneyEndsWhen("a load made current", 1883, () ->
        {
            try
            {
                waitingToBeLoaded[0] = new Layout(model);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }, () -> waitingToBeLoaded[0].makeCurrent()),
            "a load made current left the retired railway's journey waiting (RSA2-C4, RSA-B1)");

        assertTrue(anUntimedJourneyEndsWhen("a railway built", 1885, null, () ->
        {
            try
            {
                new Layout(model);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }), "a railway built left the retired railway's journey waiting (RSA2-C4, RSA-B1)");
    }

    /**
     * A journey sent on a railway of its own, its wait untimed at once, and then one retirement: whether the journey
     * ends within two seconds.
     */
    private static boolean anUntimedJourneyEndsWhen(String door, int firstSensor, Runnable before, Runnable retire)
        throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(firstSensor, 2);

        final Locomotive w = model.getLocByName(model.getLocList().get(10));

        final long advisoryWas = Locomotive.FEEDBACK_ADVISORY_MS;

        Thread journey = null;

        try
        {
            w.setSpeed(0);

            // UNTIMED AT ONCE, as every real wait is after five minutes
            Locomotive.FEEDBACK_ADVISORY_MS = 50;

            if (before != null) before.run();

            final Layout rail = aLine(s, "UWA" + firstSensor, "UWB" + firstSensor);

            rail.getPoint("UWA" + firstSensor).setLocomotive(w);

            journey = sendOn(rail, through(rail, "UWA" + firstSensor, "UWB" + firstSensor), w);

            assertTrue(waitFor(() -> w.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(w), 10000),
                "precondition (" + door + "): the train was not sent");

            // Past its advisory: the wait is untimed now
            Thread.sleep(600);

            retire.run();

            assertFalse(rail.isCurrentLayout(), "precondition (" + door + "): the railway was not retired");

            final Thread waiting = journey;

            return waitFor(() -> !waiting.isAlive(), 2000);
        }
        finally
        {
            Locomotive.FEEDBACK_ADVISORY_MS = advisoryWas;

            letGo(s, w, journey);
        }
    }

    /** A handler on the model's own logger, collecting every message into `into` - removed by the caller. */
    private static java.util.logging.Handler aTapOn(final List<String> into)
    {
        java.util.logging.Handler tap = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                if (record != null && record.getMessage() != null) into.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName()).addHandler(tap);

        return tap;
    }

    /**
     * A sensor another train's journey has passed is free again for other routes once the rail to it is given back
     * (RSA2-B1; Adam, 2026-09-29: *"that is OK as long as non-atomic rules are respected (it should be allowed once
     * unlocked)"*).  Held from the head's arrival until that release (RSA3-C3, `testASensorIsHeldUntilTheRailToItIsGivenBack`);
     * the track here unmeasured, the release comes one sensor on.
     *
     * MUTATION: let a journey count a sensor it has passed as one it still waits on, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testASensorAJourneyHasPassedIsFreeAgain() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1891, 5);

        final Locomotive x = model.getLocByName(model.getLocList().get(11));
        final Locomotive y = model.getLocByName(model.getLocList().get(12));

        Thread sent = null;

        // X runs PXA -> PXM -> PXN -> PXB, past PXM on the shared sensor s[1]; Y's road ends at PYP, a different place
        // on the same sensor
        final Layout rail = new Layout(model);

        try
        {
            x.setSpeed(0);
            y.setSpeed(0);

            rail.createPoint("PXA", true, s[0].getName());
            rail.createPoint("PXM", false, s[1].getName());
            rail.createPoint("PXN", false, s[4].getName());
            rail.createPoint("PXB", true, s[2].getName());
            rail.createPoint("PYA", true, s[3].getName());
            rail.createPoint("PYP", true, s[1].getName());
            rail.createEdge("PXA", "PXM");
            rail.createEdge("PXM", "PXN");
            rail.createEdge("PXN", "PXB");
            rail.createEdge("PYA", "PYP");
            rail.setAtomicRoutes(false);
            rail.makeCurrent();

            rail.getPoint("PXA").setLocomotive(x);
            rail.getPoint("PYA").setLocomotive(y);

            final List<Edge> toTheSharedSensor = through(rail, "PYA", "PYP");

            sent = sendOn(rail, through(rail, "PXA", "PXM", "PXN", "PXB"), x);

            assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                "precondition: the first train was not sent");

            assertFalse(rail.isPathClear(toTheSharedSensor, y, false), "precondition: the sensor ahead of the first"
                + " train is not refused to the second, so this claim cannot tell");

            // ITS HEAD PASSES THE SHARED SENSOR
            model.setFeedbackState(s[1].getName(), true);

            assertTrue(waitFor(() -> rail.getReachedMilestones(x) != null
                && rail.getReachedMilestones(x).contains(rail.getPoint("PXM")), 5000),
                "precondition: the first train's journey did not record passing the shared sensor");

            model.setFeedbackState(s[1].getName(), false);

            // AND ONE SENSOR ON, where the rail to the shared sensor is given back
            model.setFeedbackState(s[4].getName(), true);

            assertTrue(waitFor(() -> !rail.getEdge("PXA", "PXM").isRunOver(x), 5000), "precondition: the rail to the"
                + " shared sensor was not given back one sensor on");

            model.setFeedbackState(s[4].getName(), false);

            // THE RELEASE RECORDED a moment after the rail reads free: until then the sensor is refused, the safe way
            waitFor(() -> rail.isPathClear(toTheSharedSensor, y, false), 5000);

            assertTrue(rail.isPathClear(toTheSharedSensor, y, false), "a sensor the first train has already passed is"
                + " still refused to another route, though its journey waits on it no more: " + Layout.getLastError());
        }
        finally
        {
            letGo(s, x, sent);

            y.setSpeed(0);
        }
    }

    /**
     * The sensor rule sees a train handing over from claim to journey (RSA3-C1): a check made across the hand-over is
     * refused, as it is before and after it.
     *
     * The hand-over writes the train into the journeys and then takes its claim away; the rule read the journeys first
     * and the claims second.  A check whose first read came before the hand-over and whose second came after saw the
     * train in neither, and RSA2-B1's two trains on one sensor came back.  Held here exactly: X at the first statement of
     * its hand-over, and the check let on to its second read only once the hand-over has finished.
     *
     * MUTATION: read the journeys before the claims again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheSensorRuleSeesATrainHandingOver() throws Exception
    {
        Object answer = acrossAHandOver(1901, "whoseJourneyAwaits", (rail, x, y) ->
            rail.isPathClear(through(rail, "PYA", "PYP"), y, false));

        assertEquals(answer, Boolean.FALSE, "a route to the other place on a sensor is admitted by a check made across"
            + " the first train's hand-over from claim to journey (RSA3-C1): " + Layout.getLastError());
    }

    /**
     * A train handing over from claim to journey is under way throughout (RSA3-C1, AUT-C1's family): asked across the
     * hand-over, it is still one a second dispatch must refuse.
     *
     * MUTATION: ask the journeys before the claims again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATrainHandingOverIsStillUnderWay() throws Exception
    {
        Object answer = acrossAHandOver(1911, "isAlreadyUnderway", (rail, x, y) -> rail.isAlreadyUnderway(x));

        assertEquals(answer, Boolean.TRUE, "a train handing over from claim to journey reads as not under way, so a"
            + " second dispatch of it would be let through (RSA3-C1)");
    }

    /**
     * A train handing over from claim to journey is counted, for the cap on trains out (RSA3-C1).
     *
     * MUTATION: count the journeys before the claims again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATrainHandingOverIsCounted() throws Exception
    {
        final java.lang.reflect.Method count = Layout.class.getDeclaredMethod("trainsUnderway");

        count.setAccessible(true);

        Object answer = acrossAHandOver(1921, "trainsUnderway", (rail, x, y) -> count.invoke(rail));

        assertEquals(answer, 1, "a train handing over from claim to journey is not counted among the trains out"
            + " (RSA3-C1)");
    }

    /**
     * A route may not wait on a sensor another train is still CLAIMING a route to (RSA3-C2): the claim half of the sensor
     * rule, which is what keeps two trains off one sensor while a route's switches are confirmed.
     *
     * On a real railway the claim lasts as long as the Central Station takes to confirm the route's accessories; in a
     * simulation it has no length, so no claim reached it.  Held here at its end: the first train locked, not yet a
     * journey.
     *
     * MUTATION: leave the claims out of the sensor rule, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testARouteMayNotWaitOnASensorAClaimAwaits() throws Exception
    {
        Object answer = acrossAHandOver(1931, null, (rail, x, y) ->
            rail.isPathClear(through(rail, "PYA", "PYP"), y, false));

        assertEquals(answer, Boolean.FALSE, "a route to the other place on a sensor is admitted while the first train is"
            + " still claiming its route there (RSA3-C2): " + Layout.getLastError());
    }

    /** A question asked of a railway while one train is held at its hand-over from claim to journey. */
    private interface AskedAcross
    {
        Object ask(Layout rail, Locomotive x, Locomotive y) throws Exception;
    }

    /**
     * X claims PXA -> PXM and is held at the first statement of its hand-over; Y stands at PYA, whose road ends at PYP, a
     * second place on PXM's sensor.  Then the question is asked - on this thread, and where a method is named, with the
     * hand-over let run to its end the moment the method reads the second of the two maps, after the first.
     *
     * @return the answer
     */
    private static Object acrossAHandOver(int firstSensor, String inside, AskedAcross question) throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(firstSensor, 3);

        final Locomotive x = model.getLocByName(model.getLocList().get(13));
        final Locomotive y = model.getLocByName(model.getLocList().get(14));

        final HandOver held = new HandOver(x, inside);

        Thread sent = null;

        final Layout rail = new Layout(model);

        try
        {
            x.setSpeed(0);
            y.setSpeed(0);

            rail.createPoint("PXA", true, s[0].getName());
            rail.createPoint("PXM", true, s[1].getName());
            rail.createPoint("PYA", true, s[2].getName());
            rail.createPoint("PYP", true, s[1].getName());
            rail.createEdge("PXA", "PXM");
            rail.createEdge("PYA", "PYP");
            rail.makeCurrent();

            swap(rail, "locomotiveMilestones", held.milestones);
            swap(rail, "activeLocomotives", held.journeys);
            swap(rail, "takingPath", held.claims);

            rail.getPoint("PXA").setLocomotive(x);
            rail.getPoint("PYA").setLocomotive(y);

            sent = sendOn(rail, through(rail, "PXA", "PXM"), x);

            assertTrue(held.atHandOver.await(10, TimeUnit.SECONDS), "precondition: the first train never reached its"
                + " hand-over from claim to journey");

            assertTrue(held.claims.containsKey(x) && !held.journeys.containsKey(x), "precondition: the first train is not"
                + " held as a claim");

            held.checker = Thread.currentThread();

            try
            {
                return question.ask(rail, x, y);
            }
            finally
            {
                held.checker = null;
            }
        }
        finally
        {
            held.letGo.countDown();

            letGo(s, x, sent);

            y.setSpeed(0);
        }
    }

    /**
     * The three maps a hand-over from claim to journey writes, as a railway's own - and a way to hold one there.
     */
    private static final class HandOver
    {
        final Locomotive holding;
        final String inside;

        volatile Thread checker;
        volatile boolean fired;

        final java.util.concurrent.CountDownLatch atHandOver = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch letGo = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch handedOver = new java.util.concurrent.CountDownLatch(1);

        final java.util.Set<Object> read = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        final Held milestones = new Held();
        final Watched journeys = new Watched(false);
        final Watched claims = new Watched(true);

        HandOver(Locomotive holding, String inside)
        {
            this.holding = holding;
            this.inside = inside;
        }

        /** A read of one of the two maps: the second, inside the method named, first lets the hand-over finish. */
        void reading(Object map)
        {
            if (inside == null || Thread.currentThread() != checker || !calledFrom(inside)) return;

            if (!fired && !read.isEmpty() && !read.contains(map))
            {
                fired = true;

                letGo.countDown();

                try
                {
                    handedOver.await(10, TimeUnit.SECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            read.add(map);
        }

        /** `locomotiveMilestones`: its first write is the hand-over's first statement. */
        final class Held extends java.util.concurrent.ConcurrentHashMap<Locomotive, List<org.traincontrol.automation.Point>>
        {
            private static final long serialVersionUID = 1L;

            @Override
            public List<org.traincontrol.automation.Point> put(Locomotive key, List<org.traincontrol.automation.Point> value)
            {
                if (key == holding && atHandOver.getCount() > 0)
                {
                    atHandOver.countDown();

                    try
                    {
                        letGo.await(15, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                }

                return super.put(key, value);
            }
        }

        /** `activeLocomotives` and `takingPath`: they say when they are read, and the claims when X's is taken away. */
        final class Watched extends java.util.concurrent.ConcurrentHashMap<Locomotive, List<Edge>>
        {
            private static final long serialVersionUID = 1L;

            private final boolean isTheClaims;

            Watched(boolean isTheClaims)
            {
                this.isTheClaims = isTheClaims;
            }

            @Override
            public boolean containsKey(Object key)
            {
                reading(this);

                return super.containsKey(key);
            }

            /** Read by `get` too, as the tail rule reads it (RSA4-C3) - for the train held, and no other. */
            @Override
            public List<Edge> get(Object key)
            {
                if (key == holding) reading(this);

                return super.get(key);
            }

            /** A copy made by a map's constructor asks the size first, and reads no entry of an empty map (RSA4-C3). */
            @Override
            public int size()
            {
                reading(this);

                return super.size();
            }

            @Override
            public java.util.Set<java.util.Map.Entry<Locomotive, List<Edge>>> entrySet()
            {
                reading(this);

                return super.entrySet();
            }

            @Override
            public java.util.concurrent.ConcurrentHashMap.KeySetView<Locomotive, List<Edge>> keySet()
            {
                reading(this);

                return super.keySet();
            }

            @Override
            public List<Edge> remove(Object key)
            {
                List<Edge> was = super.remove(key);

                if (isTheClaims && key == holding) handedOver.countDown();

                return was;
            }
        }
    }

    private static boolean calledFrom(String method)
    {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace())
        {
            if (method.equals(frame.getMethodName())) return true;
        }

        return false;
    }

    private static void swap(Layout layout, String field, Object value) throws Exception
    {
        java.lang.reflect.Field f = Layout.class.getDeclaredField(field);

        f.setAccessible(true);

        f.set(layout, value);
    }

    /**
     * A sensor is held for the train whose route runs to it until the rail to it is given back, not just until its head
     * has reached it (RSA3-C3; Adam, 2026-09-29, on RSA2-B1: *"it should be allowed once unlocked"*).
     *
     * Between the head's arrival and the release, only the sensor's own reading kept a second train off the other place
     * on it, and a gap between two axles reads clear: the second train was sent, and the first train's next axle ended
     * its journey at once.  Non-atomic, the track unmeasured: the rail to the shared sensor is given back one sensor on,
     * which `testASensorAJourneyHasPassedIsFreeAgain` holds to.
     *
     * MUTATION: free the sensor when the head reaches it, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testASensorIsHeldUntilTheRailToItIsGivenBack() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1941, 5);

        final Locomotive x = model.getLocByName(model.getLocList().get(15));
        final Locomotive y = model.getLocByName(model.getLocList().get(16));

        Thread sent = null;

        final Layout rail = new Layout(model);

        try
        {
            x.setSpeed(0);
            y.setSpeed(0);

            // X runs PXA -> PXM -> PXN -> PXB; PYP, where Y's road ends, is a second place on PXM's sensor
            rail.createPoint("PXA", true, s[0].getName());
            rail.createPoint("PXM", false, s[1].getName());
            rail.createPoint("PXN", false, s[2].getName());
            rail.createPoint("PXB", true, s[3].getName());
            rail.createPoint("PYA", true, s[4].getName());
            rail.createPoint("PYP", true, s[1].getName());
            rail.createEdge("PXA", "PXM");
            rail.createEdge("PXM", "PXN");
            rail.createEdge("PXN", "PXB");
            rail.createEdge("PYA", "PYP");
            rail.setAtomicRoutes(false);
            rail.makeCurrent();

            rail.getPoint("PXA").setLocomotive(x);
            rail.getPoint("PYA").setLocomotive(y);

            final List<Edge> toTheSharedSensor = through(rail, "PYA", "PYP");
            final Edge railToIt = rail.getEdge("PXA", "PXM");

            sent = sendOn(rail, through(rail, "PXA", "PXM", "PXN", "PXB"), x);

            assertTrue(waitFor(() -> x.getSpeed() > 0 && rail.getActiveLocomotives().containsKey(x), 10000),
                "precondition: the first train was not sent");

            // ITS HEAD REACHES THE SHARED SENSOR, and a gap between two axles reads clear
            model.setFeedbackState(s[1].getName(), true);

            assertTrue(waitFor(() -> rail.getReachedMilestones(x) != null
                && rail.getReachedMilestones(x).contains(rail.getPoint("PXM")), 5000),
                "precondition: the first train's journey did not record reaching the shared sensor");

            model.setFeedbackState(s[1].getName(), false);

            assertTrue(railToIt.isRunOver(x), "precondition: the rail to the shared sensor was given back at the head, so"
                + " the claim below cannot tell");

            assertFalse(rail.isPathClear(toTheSharedSensor, y, false), "a sensor whose rail is still held for the first"
                + " train is admitted to a second the moment it reads clear (RSA3-C3) - Adam: \"it should be allowed once"
                + " unlocked\"");
        }
        finally
        {
            letGo(s, x, sent);

            y.setSpeed(0);
        }
    }

    /**
     * The Yes takes no lock an arriving journey holds (RSA3-C4): on the event thread, it may not wait for another train
     * to finish throwing its switches.
     *
     * An arrival takes the journeys' lock and then the railway's monitor, which a dispatch holds for as long as it throws
     * a route's switches; the Yes took the journeys' lock too, on the event thread, so the window froze and every train
     * ran on for the length of the wait.  Held here: the journeys' lock taken by another thread, and the Yes asked.
     *
     * MUTATION: take the journeys' lock in the Yes again, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheYesWaitsForNoLockAnArrivalHolds() throws Exception
    {
        final Layout rail = new Layout(model);

        final Object journeys = field(rail, "activeLocomotives");

        final java.util.concurrent.CountDownLatch holding = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        Thread arrival = new Thread(() ->
        {
            synchronized (journeys)
            {
                holding.countDown();

                try
                {
                    release.await(20, TimeUnit.SECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }, "an arrival holding the journeys' lock");

        arrival.setDaemon(true);
        arrival.start();

        try
        {
            assertTrue(holding.await(5, TimeUnit.SECONDS), "precondition: the journeys' lock was not taken");

            final int before = rail.stopsOrdered();

            Thread yes = new Thread(() -> theYesUnchecked(rail), "the Yes");

            yes.setDaemon(true);
            yes.start();

            yes.join(3000);

            assertFalse(yes.isAlive(), "the Yes waited for the lock an arrival holds - on the event thread, the window is"
                + " frozen and every train runs on for as long as another train throws its switches (RSA3-C4)");

            assertEquals(rail.stopsOrdered(), before + 1, "the Yes did not count its stop");
        }
        finally
        {
            release.countDown();

            arrival.join(5000);
        }
    }

    /**
     * A timetable the stop count refuses at its start leaves nothing running (RSA3-C4): it sets `running` before it asks
     * the count - so a Yes between the two is seen either way - and clears it again where the count has moved.
     *
     * MUTATION: return without clearing it, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATimetableTheCountRefusesLeavesNothingRunning() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1961, 2);

        final Locomotive t = model.getLocByName(model.getLocList().get(17));

        final Layout rail = new Layout(model);

        try
        {
            t.setSpeed(0);

            rail.createPoint("TA", true, s[0].getName());
            rail.createPoint("TB", true, s[1].getName());
            rail.createEdge("TA", "TB");
            rail.makeCurrent();

            rail.getPoint("TA").setLocomotive(t);

            List<org.traincontrol.automation.TimetablePath> table = new ArrayList<>();

            table.add(new org.traincontrol.automation.TimetablePath(t, Arrays.asList(rail.getEdge("TA", "TB")), 0));

            rail.setTimetable(table);

            // CHOSEN, THEN THE YES, THEN THE START
            final int chosen = rail.stopsOrdered();

            theYes(rail);

            assertTrue(rail.executeTimetable(chosen), "precondition: the refused start did not answer as having started"
                + " nothing");

            assertFalse(rail.isAutoRunning(), "a timetable the stop count refused left the railway running (RSA3-C4)");
            assertFalse(rail.isRunning(), "a timetable the stop count refused left the railway busy (RSA3-C4)");
        }
        finally
        {
            rail.stopLocomotives();

            new Layout(model).makeCurrent();

            t.setSpeed(0);
        }
    }

    /**
     * A timetable entry is running until it has left (RSA3-C5): after a stop, as it finishes, the railway is not yet
     * idle - so no door can start the next run while it is still there.  Held here at its last line, where it says its
     * path is finished.
     *
     * MUTATION: count an entry not at all, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAnEntryIsRunningUntilItHasLeft() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1971, 2);

        final Locomotive t = model.getLocByName(model.getLocList().get(17));
        final Locomotive w = model.getLocByName(model.getLocList().get(18));

        final int preferredWas = t.getPreferredSpeed();

        final Layout rail = new Layout(model);

        final String finished = org.traincontrol.util.I18n.t("autolayout.infoTimetablePathFinished");

        final java.util.concurrent.CountDownLatch atItsEnd = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        java.util.logging.Handler hold = new java.util.logging.Handler()
        {
            @Override
            public void publish(java.util.logging.LogRecord record)
            {
                if (record == null || record.getMessage() == null || !record.getMessage().contains(finished)) return;

                atItsEnd.countDown();

                try
                {
                    release.await(10, TimeUnit.SECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName()).addHandler(hold);

        Thread executor = null;

        try
        {
            t.setSpeed(0);
            w.setSpeed(0);
            t.setPreferredSpeed(30);

            rail.createPoint("TA", true, s[0].getName());
            rail.createPoint("TB", true, s[1].getName());
            rail.createEdge("TA", "TB");
            rail.setMaxDelay(3);
            rail.setMinDelay(3);
            rail.makeCurrent();

            // THE ENTRY REFUSES: TB holds another train
            rail.getPoint("TA").setLocomotive(t);
            rail.getPoint("TB").setLocomotive(w);

            List<org.traincontrol.automation.TimetablePath> table = new ArrayList<>();

            table.add(new org.traincontrol.automation.TimetablePath(t, Arrays.asList(rail.getEdge("TA", "TB")), 0));

            rail.setTimetable(table);

            executor = new Thread(() -> rail.executeTimetable(), "a timetable run");
            executor.setDaemon(true);
            executor.start();

            Thread.sleep(600);

            theYes(rail);

            assertTrue(atItsEnd.await(5, TimeUnit.SECONDS), "precondition: the entry did not reach its end after the Yes");

            assertTrue(rail.isRunning(), "a timetable entry still there after the Yes is not counted as running, so the"
                + " next run could begin while it is (RSA3-C5)");
        }
        finally
        {
            release.countDown();

            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName())
                .removeHandler(hold);

            rail.stopLocomotives();

            new Layout(model).makeCurrent();

            if (executor != null) executor.join(5000);

            t.setPreferredSpeed(preferredWas);
            t.setSpeed(0);
            w.setSpeed(0);
        }
    }

    /**
     * A timetable entry the Yes found refusing leaves with its run (RSA3-C5): it does not wake into the next run and stop
     * it at its stuck limit, blaming the track.
     *
     * The entry paused between refusals, counted by nothing, so the run read idle, its call returned and the next run
     * began; the entry then woke into that run, could never be sent, and at its stuck limit stopped it.  Now an entry is
     * counted while it lives, and a stop wakes it to leave.
     *
     * MUTATION: let a stop not wake a pausing entry, or count it not at all, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testAnEntryTheYesFoundRefusingLeavesWithItsRun() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1951, 2);

        final Locomotive t = model.getLocByName(model.getLocList().get(17));
        final Locomotive w = model.getLocByName(model.getLocList().get(18));

        final int preferredWas = t.getPreferredSpeed();
        final long stuckWas = Layout.TIMETABLE_STUCK_MS;

        final Layout rail = new Layout(model);

        final List<String> said = java.util.Collections.synchronizedList(new ArrayList<String>());

        java.util.logging.Handler tap = aTapOn(said);

        Thread executor = null;

        try
        {
            t.setSpeed(0);
            w.setSpeed(0);
            t.setPreferredSpeed(30);

            Layout.TIMETABLE_STUCK_MS = 4000;

            rail.createPoint("TA", true, s[0].getName());
            rail.createPoint("TB", true, s[1].getName());
            rail.createEdge("TA", "TB");
            // the most first, as the least may not exceed it
            rail.setMaxDelay(3);
            rail.setMinDelay(3);
            rail.makeCurrent();

            // THE ENTRY REFUSES: TB holds another train
            rail.getPoint("TA").setLocomotive(t);
            rail.getPoint("TB").setLocomotive(w);

            List<org.traincontrol.automation.TimetablePath> table = new ArrayList<>();

            table.add(new org.traincontrol.automation.TimetablePath(t, Arrays.asList(rail.getEdge("TA", "TB")), 0));

            rail.setTimetable(table);

            executor = new Thread(() -> rail.executeTimetable(), "a timetable run");
            executor.setDaemon(true);
            executor.start();

            Thread.sleep(600);

            assertTrue(rail.isAutoRunning(), "precondition: the timetable is not running");

            // THE YES
            final long yes = System.currentTimeMillis();

            theYes(rail);

            executor.join(5000);

            assertFalse(executor.isAlive(), "precondition: the timetable's call did not return after the Yes");

            assertTrue(System.currentTimeMillis() - yes < 2000, "the timetable's call returned "
                + (System.currentTimeMillis() - yes) + " ms after the Yes: its refusing entry was not woken to leave with"
                + " it, and waited out its pause (RSA3-C5)");

            said.clear();

            // THE NEXT RUN
            rail.setLocomotivesToRun(new ArrayList<Locomotive>());
            rail.runLocomotives();

            Thread.sleep(7000);

            assertTrue(rail.isAutoRunning(), "the next run was stopped by an entry of the run the Yes ended (RSA3-C5):"
                + " " + said);

            synchronized (said)
            {
                for (String line : said)
                {
                    assertFalse(line.contains(t.getName()), "an entry of the run the Yes ended went on after it: " + line);
                }
            }
        }
        finally
        {
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName())
                .removeHandler(tap);

            rail.stopLocomotives();

            new Layout(model).makeCurrent();

            if (executor != null) executor.join(5000);

            Layout.TIMETABLE_STUCK_MS = stuckWas;

            t.setPreferredSpeed(preferredWas);
            t.setSpeed(0);
            w.setSpeed(0);
        }
    }

    /**
     * The route guard's list of switches in use sees a train handing over from claim to journey (RSA4-C3): a route set
     * by a sensor asks it before it throws a switch, and a train in neither map had its route's switches missing.
     *
     * MUTATION: read the journeys before the claims in `getActiveAccs`, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheRouteGuardSeesATrainHandingOver() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(1981, 2);

        final Locomotive x = model.getLocByName(model.getLocList().get(19));

        final HandOver held = new HandOver(x, "getActiveAccs");

        Thread sent = null;

        final Layout rail = new Layout(model);

        try
        {
            x.setSpeed(0);

            rail.createPoint("GXA", true, s[0].getName());
            rail.createPoint("GXB", true, s[1].getName());
            rail.createEdge("GXA", "GXB");
            rail.makeCurrent();

            final org.traincontrol.marklin.MarklinAccessory onThePath = model.newSwitch(297,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, false);

            onThePath.setSwitched(false);

            // THE CENTRAL STATION'S ECHO, which a simulated station does not send: confirmed at TURN, so the claim's
            // actuation check passes at once, as on a railway that answers
            java.lang.reflect.Field confirmed = org.traincontrol.base.Accessory.class.getDeclaredField("actuationConfirmed");
            java.lang.reflect.Field lastState = org.traincontrol.base.Accessory.class.getDeclaredField("stateAtLastActuation");

            confirmed.setAccessible(true);
            lastState.setAccessible(true);

            confirmed.set(onThePath, true);
            lastState.set(onThePath, true);

            rail.getEdge("GXA", "GXB").addConfigCommand(onThePath.getName(),
                org.traincontrol.base.Accessory.accessorySetting.TURN);

            swap(rail, "locomotiveMilestones", held.milestones);
            swap(rail, "activeLocomotives", held.journeys);
            swap(rail, "takingPath", held.claims);

            rail.getPoint("GXA").setLocomotive(x);

            sent = sendOn(rail, through(rail, "GXA", "GXB"), x);

            assertTrue(held.atHandOver.await(10, TimeUnit.SECONDS), "precondition: the train never reached its hand-over");

            held.checker = Thread.currentThread();

            java.util.Set<org.traincontrol.base.Accessory> active;

            try
            {
                active = rail.getActiveAccs();
            }
            finally
            {
                held.checker = null;
            }

            assertTrue(active.contains(onThePath), "the switch on a route handing over from claim to journey is missing"
                + " from the route guard's list, so a route set by a sensor may throw it (RSA4-C3): " + active);
        }
        finally
        {
            held.letGo.countDown();

            letGo(s, x, sent);
        }
    }

    /**
     * The tail rule sees a train handing over from claim to journey (RSA4-C3): the track behind its start, which its
     * body lies across, stays refused to a second train's route throughout.
     *
     * Across the hand-over the walk found the train in neither map and anchored its tail at whichever reserved Point
     * the railway's map gave first - the start is named so that another comes first - and the track behind the train's
     * start was claimed by nobody.
     *
     * MUTATION: read the journeys before the claims for the path a train holds, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheTailRuleSeesATrainHandingOver() throws Exception
    {
        Object answer = acrossATailedHandOver(1991, "walkStandingTrains", (rail, x, y, start) ->
            rail.isPathClear(Arrays.asList(rail.getEdge("PU", "PV"), rail.getEdge("PV", "PW")), y, false));

        assertEquals(answer, Boolean.FALSE, "a route over the track a train handing over from claim to journey lies"
            + " across is admitted (RSA4-C3)");
    }

    /**
     * Where a train handing over from claim to journey is, for the tail overlay, is its start (RSA4-C3).
     *
     * MUTATION: read the journeys before the claims for the path a train holds, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testWhereATrainHandingOverIsIsItsStart() throws Exception
    {
        final String[] start = new String[1];

        Object answer = acrossATailedHandOver(2001, "whereTheTrainIs", (rail, x, y, from) ->
        {
            start[0] = from;

            org.traincontrol.automation.Point at = rail.whereTheTrainIs(x);

            return at == null ? null : at.getName();
        });

        assertEquals(answer, start[0], "a train handing over from claim to journey is placed somewhere other than its"
            + " start (RSA4-C3)");
    }

    /** A question asked across a hand-over, of a railway where the train's tail lies behind its start. */
    private interface AskedAcrossATail
    {
        Object ask(Layout rail, Locomotive x, Locomotive y, String start) throws Exception;
    }

    /**
     * PV - PW - start - PXM - PXN in a line, every rail 100 long; X, 150 long, stands at the start come in from the west,
     * its tail over PW -> start and PV -> PW; Y stands at PU, whose road runs PU -> PV -> PW.  X is sent start -> PXM ->
     * PXN and held at its hand-over, and the question is asked with the hand-over let run to its end the moment the
     * method named reads the second of the two maps.  The start is named so that it is not the first of X's reserved
     * Points in the railway's map.
     */
    private static Object acrossATailedHandOver(int firstSensor, String inside, AskedAcrossATail question) throws Exception
    {
        String start = null;

        for (String candidate : new String[] {"PXA", "QXA", "RXA", "SXA", "KXA", "LXA", "MXA", "NXA", "OXA", "WXA"})
        {
            java.util.Map<String, Object> order = new java.util.HashMap<>();

            for (String name : new String[] {"PV", "PW", candidate, "PXM", "PXN", "PU"}) order.put(name, name);

            for (String name : order.keySet())
            {
                if (name.equals(candidate)) break;

                if (name.equals("PXM") || name.equals("PXN"))
                {
                    start = candidate;
                    break;
                }
            }

            if (start != null) break;
        }

        assertNotNullHere(start, "precondition: no start name puts another reserved Point first");

        org.traincontrol.marklin.MarklinFeedback[] s = sensors(firstSensor, 6);

        final Locomotive x = model.getLocByName(model.getLocList().get(20));
        final Locomotive y = model.getLocByName(model.getLocList().get(21));

        final Integer xLengthWas = x.getTrainLength();
        final Integer yLengthWas = y.getTrainLength();

        final HandOver held = new HandOver(x, inside);

        Thread sent = null;

        final Layout rail = new Layout(model);

        try
        {
            x.setSpeed(0);
            y.setSpeed(0);

            x.setTrainLength(150);
            y.setTrainLength(0);

            rail.createPoint("PV", true, s[0].getName());
            rail.createPoint("PW", true, s[1].getName());
            rail.createPoint(start, true, s[2].getName());
            rail.createPoint("PXM", false, s[3].getName());
            rail.createPoint("PXN", true, s[4].getName());
            rail.createPoint("PU", true, s[5].getName());
            rail.createEdge("PV", "PW");
            rail.createEdge("PW", start);
            rail.createEdge(start, "PXM");
            rail.createEdge("PXM", "PXN");
            rail.createEdge("PU", "PV");

            for (Edge e : rail.getEdges()) e.setLength(100);

            rail.getEdge("PW", start).setEntrySide("W");

            rail.makeCurrent();

            rail.getPoint(start).setLocomotive(x);
            rail.getPoint(start).setArrivedFrom("W");
            rail.getPoint("PU").setLocomotive(y);

            assertFalse(rail.isPathClear(Arrays.asList(rail.getEdge("PU", "PV"), rail.getEdge("PV", "PW")), y, false),
                "precondition: at rest, the track X's body lies across is not refused, so the claim cannot tell");

            swap(rail, "locomotiveMilestones", held.milestones);
            swap(rail, "activeLocomotives", held.journeys);
            swap(rail, "takingPath", held.claims);

            sent = sendOn(rail, through(rail, start, "PXM", "PXN"), x);

            assertTrue(held.atHandOver.await(10, TimeUnit.SECONDS), "precondition: the train never reached its hand-over");

            held.checker = Thread.currentThread();

            try
            {
                return question.ask(rail, x, y, start);
            }
            finally
            {
                held.checker = null;
            }
        }
        finally
        {
            held.letGo.countDown();

            letGo(s, x, sent);

            x.setTrainLength(xLengthWas);
            y.setTrainLength(yLengthWas);

            y.setSpeed(0);
        }
    }

    private static void assertNotNullHere(Object value, String message)
    {
        assertTrue(value != null, message);
    }

    /**
     * A timetable's own loop is running until it has stopped dispatching, and a stop wakes its pause (RSA4-C1): after a
     * stop with no train moving the railway reads busy while the loop is still inside the timetable, so no door starts a
     * run it could join, and the loop leaves at once.
     *
     * Two entries, the second five seconds after the first, the operator's delays three seconds: the first run and
     * arrived, the loop pausing before the second.  Held here with the pause's monitor taken, so the stop has cleared
     * `running` and the loop cannot yet leave.
     *
     * MUTATION: count the loop not at all, or pause it in a sleep no stop wakes, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testATimetablesLoopIsRunningUntilItHasStopped() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(2011, 4);

        final Locomotive t = model.getLocByName(model.getLocList().get(22));
        final Locomotive u = model.getLocByName(model.getLocList().get(23));

        final int tWas = t.getPreferredSpeed();
        final int uWas = u.getPreferredSpeed();

        final Layout rail = new Layout(model);

        Thread executor = null;
        Thread stopping = null;
        Thread holding = null;

        final java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        try
        {
            t.setSpeed(0);
            u.setSpeed(0);
            t.setPreferredSpeed(30);
            u.setPreferredSpeed(30);

            rail.createPoint("TA", true, s[0].getName());
            rail.createPoint("TB", true, s[1].getName());
            rail.createPoint("TC", true, s[2].getName());
            rail.createPoint("TD", true, s[3].getName());
            rail.createEdge("TA", "TB");
            rail.createEdge("TC", "TD");
            rail.setMaxDelay(3);
            rail.setMinDelay(3);
            rail.makeCurrent();

            rail.getPoint("TA").setLocomotive(t);
            rail.getPoint("TC").setLocomotive(u);

            List<org.traincontrol.automation.TimetablePath> table = new ArrayList<>();

            table.add(new org.traincontrol.automation.TimetablePath(t, Arrays.asList(rail.getEdge("TA", "TB")), 0));
            table.add(new org.traincontrol.automation.TimetablePath(u, Arrays.asList(rail.getEdge("TC", "TD")), 0));

            // the second entry five seconds after the first sets off (the field is milliseconds)
            table.get(1).setSecondsToNext(5000);

            rail.setTimetable(table);

            executor = new Thread(() -> rail.executeTimetable(), "a timetable run");
            executor.setDaemon(true);
            executor.start();

            // THE FIRST ENTRY RUNS AND ARRIVES, its sensor left occupied: the train stands there
            assertTrue(waitFor(() -> rail.getActiveLocomotives().containsKey(t), 10000), "precondition: the first entry"
                + " was not sent");

            model.setFeedbackState(s[1].getName(), true);

            assertTrue(waitFor(() -> !rail.getActiveLocomotives().containsKey(t), 10000), "precondition: the first entry"
                + " did not arrive");

            Thread.sleep(500);

            assertTrue(executor.isAlive(), "precondition: the timetable's call returned before its second entry");

            // THE PAUSE'S MONITOR TAKEN, so the stop below clears `running` and the loop cannot yet leave
            final Object pause = field(rail, "entryPause");

            holding = new Thread(() ->
            {
                synchronized (pause)
                {
                    held.countDown();

                    try
                    {
                        release.await(20, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "holding the pause");

            holding.setDaemon(true);
            holding.start();

            assertTrue(held.await(5, TimeUnit.SECONDS), "precondition: the pause's monitor was not taken");

            // A STOP, with nothing moving
            stopping = new Thread(rail::stopLocomotives, "a graceful stop");
            stopping.setDaemon(true);
            stopping.start();

            assertTrue(waitFor(() -> !rail.isAutoRunning(), 5000), "precondition: the stop did not clear running");

            assertTrue(rail.isRunning(), "a timetable's loop still inside the timetable after a stop is not counted as"
                + " running, so Start can come back and begin a run the loop then joins (RSA4-C1)");

            // AND LET GO: the loop, woken by the stop, leaves at once
            final long letGo = System.currentTimeMillis();

            release.countDown();

            executor.join(5000);

            assertFalse(executor.isAlive(), "the timetable's call never returned after the stop");

            assertTrue(System.currentTimeMillis() - letGo < 1500, "the timetable's loop waited out its pause after the stop"
                + " rather than being woken by it (RSA4-C1): " + (System.currentTimeMillis() - letGo) + " ms");

            assertFalse(rail.getActiveLocomotives().containsKey(u), "the second entry was sent after the stop");
        }
        finally
        {
            release.countDown();

            rail.stopLocomotives();

            new Layout(model).makeCurrent();

            for (org.traincontrol.marklin.MarklinFeedback each : s) model.setFeedbackState(each.getName(), true);

            if (executor != null) executor.join(5000);
            if (stopping != null) stopping.join(5000);

            for (org.traincontrol.marklin.MarklinFeedback each : s) model.setFeedbackState(each.getName(), false);

            t.setPreferredSpeed(tWas);
            u.setPreferredSpeed(uWas);
            t.setSpeed(0);
            u.setSpeed(0);
        }
    }

    /**
     * The Yes and a timetable's start cannot straddle each other (RSA4-C2): a Yes that has cleared `running` and not yet
     * counted holds back a timetable starting, which then reads the new count and starts nothing.
     *
     * Held here exactly: the Yes is made with the pause's monitor taken, so it blocks between clearing `running` and
     * counting; a timetable is started meanwhile, with the count read before the Yes; then the Yes is let go.
     *
     * MUTATION: let the timetable set `running` and read the count with nothing held, and this fails.
     *
     * @throws Exception from the railway
     */
    @Test
    public void testTheYesAndATimetablesStartCannotStraddle() throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] s = sensors(2021, 2);

        final Locomotive t = model.getLocByName(model.getLocList().get(22));

        final int tWas = t.getPreferredSpeed();
        final long stuckWas = Layout.TIMETABLE_STUCK_MS;

        final Layout rail = new Layout(model);

        Thread yes = null;
        Thread starting = null;
        Thread holding = null;

        final java.util.concurrent.CountDownLatch held = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        final boolean[] refused = new boolean[1];

        try
        {
            t.setSpeed(0);
            t.setPreferredSpeed(30);

            Layout.TIMETABLE_STUCK_MS = 3000;

            rail.createPoint("TA", true, s[0].getName());
            rail.createPoint("TB", true, s[1].getName());
            rail.createEdge("TA", "TB");
            rail.makeCurrent();

            rail.getPoint("TA").setLocomotive(t);

            List<org.traincontrol.automation.TimetablePath> table = new ArrayList<>();

            table.add(new org.traincontrol.automation.TimetablePath(t, Arrays.asList(rail.getEdge("TA", "TB")), 0));

            rail.setTimetable(table);

            // CHOSEN BEFORE THE YES
            final int chosen = rail.stopsOrdered();

            final Object pause = field(rail, "entryPause");

            holding = new Thread(() ->
            {
                synchronized (pause)
                {
                    held.countDown();

                    try
                    {
                        release.await(20, TimeUnit.SECONDS);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "holding the pause");

            holding.setDaemon(true);
            holding.start();

            assertTrue(held.await(5, TimeUnit.SECONDS), "precondition: the pause's monitor was not taken");

            // THE YES: `running` cleared, then held before it counts
            yes = new Thread(() -> theYesUnchecked(rail), "the Yes");
            yes.setDaemon(true);
            yes.start();

            final Thread asking = yes;

            assertTrue(waitFor(() -> asking.getState() == Thread.State.BLOCKED, 5000), "precondition: the Yes did not"
                + " block between clearing running and counting");

            assertEquals(rail.stopsOrdered(), chosen, "precondition: the Yes counted before it was held");

            // THE START, meanwhile
            starting = new Thread(() -> refused[0] = rail.executeTimetable(chosen), "a timetable start");
            starting.setDaemon(true);
            starting.start();

            Thread.sleep(500);

            // AND THE YES LET GO
            release.countDown();

            yes.join(5000);
            starting.join(8000);

            assertFalse(rail.isAutoRunning(), "a timetable started while the Yes was between clearing running and"
                + " counting is running after the Yes - its entry refused on the count until the stuck limit (RSA4-C2)");

            assertFalse(starting.isAlive(), "the timetable's call did not return: its run started after the Yes"
                + " (RSA4-C2)");
        }
        finally
        {
            release.countDown();

            rail.stopLocomotives();

            new Layout(model).makeCurrent();

            for (org.traincontrol.marklin.MarklinFeedback each : s) model.setFeedbackState(each.getName(), true);

            if (yes != null) yes.join(5000);
            if (starting != null) starting.join(8000);

            for (org.traincontrol.marklin.MarklinFeedback each : s) model.setFeedbackState(each.getName(), false);

            Layout.TIMETABLE_STUCK_MS = stuckWas;

            t.setPreferredSpeed(tWas);
            t.setSpeed(0);
        }
    }
}
