package regression;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * PROOF OF CONCEPT for the Layout.activeLocomotives concurrency race.
 *
 * THE BUG: executePath() mutates Layout.activeLocomotives inside
 * synchronized (this.activeLocomotives) { ... put / remove ... }, but the UI reads the
 * same map with NO lock via getActiveAccs() (iterates values()), getActiveLocomotives()
 * (leaks the live map; callers iterate keySet()) and getReachedMilestones()
 * - see LayoutLabel, AutoLocomotiveStatus, TrainControlUI.  Different lock on the writer
 * side, no lock on the reader side => a reader iterating the map can observe it
 * mid-structural-modification and throw ConcurrentModificationException.
 *
 * WHY THIS DRIVES THE MAP DIRECTLY (rather than full autonomy):
 * getActiveLocomotives() returns the *live* internal map (Layout.java line 332), and
 * synchronized(that reference) locks the *same* monitor executePath uses.  So the writer
 * thread below performs precisely the operations executePath performs on the map, under
 * precisely the same lock.  The reader thread calls the real, unmodified production
 * accessors.  This reproduces the exact concurrency contract deterministically, without
 * depending on a fully accessory/feedback-complete simulated layout (which invalidates
 * itself on the bare test model when an edge references a non-existent accessory).
 *
 * EXPECTED RESULT:
 *   - BEFORE the fix (plain HashMap): FAILS within milliseconds - the poller catches a
 *     ConcurrentModificationException from getActiveAccs()'s values() iteration (or from
 *     the keySet() copy).
 *   - AFTER the fix (activeLocomotives made a ConcurrentHashMap): PASSES - weakly
 *     consistent iteration never throws, while the retained synchronized(activeLocomotives)
 *     blocks continue to give the writers their compound atomicity.
 *
 * Note: this exercises the primary activeLocomotives / getActiveAccs race.  The sibling
 * locomotiveMilestones race behind getReachedMilestones is the same pattern on a private
 * map that only executePath can populate, so it is addressed by the same fix plus the
 * defensive-copy return (validated separately by inspection).
 */
public class testAutoLayoutRace
{
    public static MarklinControlStation model;

    private static final long RUN_MILLIS = 4000;

    @Test
    public void testConcurrentActiveLocomotivesAccess() throws Exception
    {
        final Layout layout = model.getAutoLayout();
        final Locomotive loc = model.getLocByName("Race loc A");

        // The real internal map.  getActiveLocomotives() returns it directly, so this is
        // the same object (and monitor) that executePath synchronizes on.
        final Map<Locomotive, List<Edge>> active = layout.getActiveLocomotives();

        // Path contents are irrelevant to the map-level race; an empty list keeps the test
        // free of any graph/accessory dependency.
        final List<Edge> path = new ArrayList<>();

        final AtomicReference<Throwable> race = new AtomicReference<>(null);
        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicLong writes = new AtomicLong(0);
        final AtomicLong reads  = new AtomicLong(0);

        // WRITER: churns the map exactly as executePath does - same monitor, same ops.
        Thread writer = new Thread(() ->
        {
            while (!stop.get() && race.get() == null)
            {
                synchronized (active)
                {
                    active.put(loc, path);
                }
                synchronized (active)
                {
                    active.remove(loc);
                }
                writes.incrementAndGet();
            }
        }, "race-writer");

        // READER: the real, unsynchronized production accessors, as the UI calls them.
        Thread poller = new Thread(() ->
        {
            while (!stop.get() && race.get() == null)
            {
                try
                {
                    // As in LayoutLabel repaint - iterates activeLocomotives.values() with no lock
                    layout.getActiveAccs();

                    // As in AutoLocomotiveStatus / TrainControlUI - the copy constructor iterates
                    // the live keySet of the leaked map with no lock
                    for (Locomotive l : new ArrayList<>(layout.getActiveLocomotives().keySet()))
                    {
                        layout.getActiveLocomotives().get(l);
                    }

                    reads.incrementAndGet();
                }
                catch (ConcurrentModificationException e)
                {
                    race.compareAndSet(null, e);
                }
                catch (Throwable t)
                {
                    race.compareAndSet(null, t);
                }
            }
        }, "race-poller");

        writer.start();
        poller.start();

        long endAt = System.currentTimeMillis() + RUN_MILLIS;
        while (System.currentTimeMillis() < endAt && race.get() == null)
        {
            Thread.sleep(10);
        }

        stop.set(true);
        writer.join(2000);
        poller.join(2000);

        System.out.println("Writer cycles=" + writes.get() + "  reader cycles=" + reads.get()
            + "  race=" + (race.get() == null ? "none" : race.get().getClass().getSimpleName()));

        // Sanity: both threads actually ran (guards against a vacuous pass)
        assertTrue(writes.get() > 0 && reads.get() > 0, "Writer/reader threads did not run");

        if (race.get() != null)
        {
            race.get().printStackTrace();
        }

        assertNull(race.get(),
            "Concurrent unsynchronized read of activeLocomotives threw: " + race.get());
    }

    /**
     * A train already under way does not queue behind one that is still being dispatched.
     *
     * `configureAndLockPath` holds the layout monitor across its whole lock loop, deliberately -
     * claiming a path has to be atomic - and that loop sleeps CONFIGURE_SLEEP per edge and again per
     * accessory, so on a long path it is held for seconds.  `updatePendingS88` used to want the same
     * monitor, and every running locomotive calls it immediately before waiting for its next sensor.
     *
     * So a second train could be held here while its own train crossed that sensor AND cleared it
     * again.  `waitForOccupiedFeedback` tests a LEVEL, so it would then find the sensor clear and wait
     * for the next occupancy of a sensor the train had already passed - no slowing, no stopping.
     *
     * This holds the layout monitor for as long as a six-edge path would and asks whether the
     * bookkeeping still runs.  Driven directly rather than through autonomy, because what is being
     * tested is which monitor guards what, and a full simulated run would make the timing the subject.
     */
    @Test
    public void testARunningTrainIsNotBlockedByOneBeingDispatched() throws Exception
    {
        final Layout layout = model.getAutoLayout();
        final Locomotive loc = model.getLocByName("Race loc A");

        // What configureAndLockPath does: the layout monitor, held across the per-command sleeps
        final long held = 6 * 150L;

        final AtomicBoolean holding = new AtomicBoolean(false);

        Thread dispatcher = new Thread(() ->
        {
            synchronized (layout)
            {
                holding.set(true);

                try
                {
                    Thread.sleep(held);
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }, "dispatching-a-path");

        dispatcher.start();

        while (!holding.get())
        {
            Thread.sleep(1);
        }

        // And what a locomotive already under way does immediately before waiting for its next sensor
        long began = System.currentTimeMillis();

        pendingS88(layout, loc, "Race sensor");

        long took = System.currentTimeMillis() - began;

        dispatcher.join(held + 2000);

        System.out.println("bookkeeping under a held layout monitor took " + took + "ms");

        assertTrue(took < held / 2,
            "a locomotive already under way waited " + took + "ms to record which sensor it is "
            + "waiting for, because a path being dispatched held the layout monitor.  Its train can "
            + "cross and clear that sensor in the meantime, and it then waits for a trigger that has "
            + "already happened");
    }

    /**
     * A train that never arrives is said out loud, and the wait carries on.
     *
     * The wait itself is right to be endless - a sensor is the only thing that can say where a train
     * is, and neither driving on blind nor stopping mid-block on a guess is a safe automatic answer -
     * but the operator was told nothing at all.  A locomotive that failed to start, or that took a
     * different route from the one it was given, simply stopped being mentioned.
     *
     * The threshold is turned down here rather than waiting three real minutes; it is not final for
     * that reason, exactly as Layout.TIMETABLE_STUCK_MS is not.
     */
    @Test
    public void testALocomotiveWaitingTooLongForASensorSaysSo() throws Exception
    {
        final Locomotive loc = model.getLocByName("Race loc A");

        final MarklinFeedback sensor = model.newFeedback(9001, null);
        model.setFeedbackState(sensor.getName(), false);

        final AtomicBoolean arrived = new AtomicBoolean(false);

        final java.util.List<String> said = java.util.Collections.synchronizedList(new ArrayList<>());

        // Records what it would have told the operator, rather than telling them
        final MarklinLocomotive dispatched = new MarklinLocomotive(model, 1,
            MarklinLocomotive.decoderType.MM2, "Dispatched")
        {
            @Override
            protected void waitedTooLongFor(String feedbackName, long waitedMs)
            {
                said.add(feedbackName);
            }
        };

        try
        {
            Thread waiting = new Thread(() ->
            {
                // The three-argument wait, which is the one the dispatch loop uses
                dispatched.waitForOccupiedFeedback(sensor.getName(), 0, 200);

                arrived.set(true);
            }, "waiting-for-a-train-that-is-not-coming");

            waiting.setDaemon(true);
            waiting.start();

            // Well past the threshold, and still nothing has happened on the railway
            Thread.sleep(1500);

            assertFalse(arrived.get(),
                "the wait ended without the sensor being occupied, which would mean a train was "
                + "assumed to have arrived somewhere it had not");

            assertTrue(waiting.isAlive(), "the waiting thread gave up, which it must never do");

            assertEquals(said, java.util.Arrays.asList(sensor.getName()),
                "the operator was told " + said + " - it should be the sensor, exactly once.  A train "
                + "that never starts otherwise just stops being mentioned");

            // and it is still released by the thing that is supposed to release it
            model.setFeedbackState(sensor.getName(), true);

            synchronized (Locomotive.monitor)
            {
                Locomotive.monitor.notifyAll();
            }

            waiting.join(5000);

            assertEquals(said.size(), 1, "it said so more than once");

            assertTrue(arrived.get(),
                "the sensor came on and the wait did not end - the poll added for the advisory has "
                + "broken the wait it was supposed to leave alone");
        }
        finally
        {
            // nothing to put back: the threshold is passed in rather than set globally
        }
    }

    /**
     * And it is said ONCE, however the sensor behaves afterwards.
     *
     * The wait restarts itself when the feedback does not stay occupied for minDuration - a flicker is
     * not an arrival - and the advisory's remaining time was carried into that restart by subtraction.
     * Past the threshold the remainder is negative, and flooring it at a millisecond meant every
     * flicker announced the same train again, immediately, as "0 minutes".
     */
    @Test
    public void testTheAdvisoryIsSaidOnceEvenIfTheSensorFlickers() throws Exception
    {
        final MarklinFeedback sensor = model.newFeedback(9003, null);

        model.setFeedbackState(sensor.getName(), false);

        final java.util.List<String> said = java.util.Collections.synchronizedList(new ArrayList<>());

        final MarklinLocomotive dispatched = new MarklinLocomotive(model, 1,
            MarklinLocomotive.decoderType.MM2, "Flickering")
        {
            @Override
            protected void waitedTooLongFor(String feedbackName, long waitedMs)
            {
                said.add(feedbackName);
            }
        };

        // minDuration > 0, which is what makes the wait restart itself
        Thread waiting = new Thread(() ->
            dispatched.waitForOccupiedFeedback(sensor.getName(), 150, 200),
            "waiting-through-a-flicker");

        waiting.setDaemon(true);
        waiting.start();

        Thread.sleep(600);

        assertEquals(said.size(), 1, "the advisory was said " + said.size() + " times before the "
            + "sensor did anything at all");

        // Occupied, but not for long enough to count - so the wait starts over
        for (int flicker = 0; flicker < 3; flicker++)
        {
            model.setFeedbackState(sensor.getName(), true);

            synchronized (Locomotive.monitor)
            {
                Locomotive.monitor.notifyAll();
            }

            Thread.sleep(60);

            model.setFeedbackState(sensor.getName(), false);

            synchronized (Locomotive.monitor)
            {
                Locomotive.monitor.notifyAll();
            }

            Thread.sleep(200);
        }

        assertEquals(said.size(), 1,
            "the advisory was said " + said.size() + " times.  A sensor that flickers is one train "
            + "not arriving, and the operator should hear about it once");

        assertTrue(waiting.isAlive(), "and the train has still not arrived, so it is still waiting");
    }

    /**
     * And a wait that is SUPPOSED to be endless says nothing at all.
     *
     * A route's trigger monitor sits on its sensor for as long as the layout runs - that is the whole
     * job - and it does so on a locomotive called "Dummy Loc" that exists only to borrow these
     * utilities.  An advisory built into the wait itself would therefore have announced, once per
     * route and for ever, that a locomotive nobody owns had failed to arrive.
     *
     * So the advisory is asked for by the caller, and this is the test that keeps it that way.
     */
    @Test
    public void testAWaitThatIsMeantToBeEndlessStaysQuiet() throws Exception
    {
        final MarklinFeedback sensor = model.newFeedback(9002, null);

        model.setFeedbackState(sensor.getName(), false);

        final java.util.List<String> said = java.util.Collections.synchronizedList(new ArrayList<>());

        // Built exactly as MarklinRoute builds its dummy - the constructor registers nothing - but
        // recording what it would have said instead of saying it
        final MarklinLocomotive listening = new MarklinLocomotive(model, 1,
            MarklinLocomotive.decoderType.MM2, "Watcher")
        {
            @Override
            protected void waitedTooLongFor(String feedbackName, long waitedMs)
            {
                said.add(feedbackName);
            }
        };

        Thread waiting = new Thread(() -> listening.waitForOccupiedFeedback(sensor.getName(), 0),
            "a-route-watching-its-sensor");

        waiting.setDaemon(true);
        waiting.start();

        // Comfortably past anything a mutated build would use as a threshold
        Thread.sleep(1500);

        assertTrue(said.isEmpty(),
            "the plain wait announced " + said + " - a route's trigger monitor would say that about "
            + "its dummy locomotive once every few minutes, for every route, for the whole session");

        assertTrue(waiting.isAlive(), "and it must still be waiting");
    }

    /**
     * Waiting for the power to come on gives up, rather than parking the only switching thread.
     *
     * The power state is written in one place - the echo from the Central Station - so nothing local
     * can release this wait, and the socket is unconnected, which means a datagram sent to a station
     * that has been switched off succeeds and disappears.  Untimed, one click on a tile stopped every
     * tile in the application from responding again.
     */
    @Test
    public void testWaitingForPowerGivesUp() throws Exception
    {
        // Power believed OFF, which is the state the wait is entered from - a tile click that has to
        // turn the power on first.  Set directly rather than by sending STOP, because whether a STOP
        // echoes back at all is the very thing being simulated away here: nothing is going to answer.
        java.lang.reflect.Field field = MarklinControlStation.class.getDeclaredField("powerState");

        field.setAccessible(true);
        field.setBoolean(model, false);

        long began = System.currentTimeMillis();

        boolean reached = model.waitForPowerState(true, 400);

        long took = System.currentTimeMillis() - began;

        assertFalse(reached, "the power came on with nothing there to turn it on");

        assertTrue(took >= 300 && took < 3000,
            "waited " + took + "ms for a deadline of 400ms");
    }

    /**
     * updatePendingS88 is private - it is bookkeeping, and nothing outside the driving loop has any
     * business calling it.  Reached here the way testImportRename reaches its internals.
     */
    private static void pendingS88(Layout layout, Locomotive loc, String s88) throws Exception
    {
        java.lang.reflect.Method method = Layout.class.getDeclaredMethod(
            "updatePendingS88", Locomotive.class, String.class);

        method.setAccessible(true);
        method.invoke(layout, loc, s88);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // simulate=true, showUI=false, autoPowerOn=false, debug=true
        testAutoLayoutRace.model = init(null, true, false, false, true);
        model.stop();

        model.newMM2Locomotive("Race loc A", 2);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        model.deleteLoc("Race loc A");
    }
}
