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
import support.Reflect;

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
     * A train already under way does not queue behind one that is still being dispatched: `configureAndLockPath` holds
     * the layout monitor across its lock loop (seconds on a long path), and `updatePendingS88`, which every running
     * locomotive calls before waiting for its next sensor, must not want that monitor, or the train could clear its
     * sensor and `waitForOccupiedFeedback` (it tests a LEVEL) would wait for a sensor it had already passed.  Driven
     * directly: the subject is which monitor guards what, not timing.
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
     * A train that never arrives is said out loud, and the wait carries on: the wait is rightly endless (only a sensor
     * says where a train is), but the operator must be told when a locomotive failed to start or took another route.
     * The threshold is turned down rather than waiting three real minutes, as Layout.TIMETABLE_STUCK_MS is.
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
     * Waiting for the power to come on gives up, rather than parking the only switching thread.
     *
     * Only the Central Station's echo writes the power state, and a datagram sent to a switched-off station succeeds
     * and disappears, so nothing local can release an untimed wait.
     */
    @Test
    public void testWaitingForPowerGivesUp() throws Exception
    {
        // Power believed OFF, the state a tile click that has to turn the power on first is in.  Set directly: whether
        // a STOP echoes back at all is what is being simulated away.
        java.lang.reflect.Field field = Reflect.field(MarklinControlStation.class, "powerState");
        field.setBoolean(model, false);

        long began = System.currentTimeMillis();

        boolean reached = model.waitForPowerState(true, 400);

        long took = System.currentTimeMillis() - began;

        assertFalse(reached, "the power came on with nothing there to turn it on");

        assertTrue(took >= 300 && took < 3000,
            "waited " + took + "ms for a deadline of 400ms");
    }

    /** updatePendingS88 is private bookkeeping; reached the way testImportRename reaches its internals. */
    private static void pendingS88(Layout layout, Locomotive loc, String s88) throws Exception
    {
        java.lang.reflect.Method method =
            Reflect.method(Layout.class, "updatePendingS88", Locomotive.class, String.class);
        method.invoke(layout, loc, s88);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        testAutoLayoutRace.model = support.TestStation.simulatedInDebug();
        model.stop();

        model.newMM2Locomotive("Race loc A", 2);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null) model.deleteLoc("Race loc A");
    }
}
