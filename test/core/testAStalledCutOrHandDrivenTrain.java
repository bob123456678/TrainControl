package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Logger;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Reflect;
import static support.Settle.until;

/**
 * A journey that does not go to plan still ends cleanly (FR-210): a train that stalls, a power cut, an operator who
 * stops or drives a train autonomy holds, a destination sensor already lit, a failure part way.
 *
 * Every journey here is driven by hand: autonomy's simulation is off, and each claim makes the sensors as the train
 * would, so nothing waits on a guessed time.
 */
public class testAStalledCutOrHandDrivenTrain
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    private static final List<String> logged = new CopyOnWriteArrayList<>();
    private static Handler tap;
    private static long adviseWas;

    /** A ceiling for each wait, never a pause: every wait ends the moment its condition holds. */
    private static final long WAIT_MS = 20000;

    /** The journeys' speed, chosen so no square's multiplier caps it. */
    private static final int SPEED = 30;

    private static int nextSensor = 4810;
    private static int nextTrain = 0;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulated();

        adviseWas = Locomotive.FEEDBACK_ADVISORY_MS;

        tap = support.LogTap.messagesInto(logged);

        Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        Locomotive.FEEDBACK_ADVISORY_MS = adviseWas;

        if (tap != null) Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);

        if (sandbox != null) sandbox.close();
    }

    /**
     * After the stuck-train advisory the journey still holds its route, and a sensor that answers late completes it.
     * The advisory is a notice, not a give-up: "TrainControl keeps waiting for that sensor".
     *
     * MUTATION: in `Locomotive.waitForOccupiedFeedback`, `return this;` straight after `waitedTooLongFor(...)`, so the
     * wait ends at the advisory, and this fails on the route being held.
     */
    @Test(timeOut = 120000)
    public void testAfterTheAdvisoryTheRouteIsHeldAndALateSensorEndsTheJourney() throws Exception
    {
        Journey j = new Journey(null, "SA", "SB", "SC");

        Locomotive.FEEDBACK_ADVISORY_MS = 500;

        try
        {
            j.go().light("SB");

            assertTrue(until(() -> j.reached("SB"), WAIT_MS), "precondition: SB was never recorded");

            String advisory = I18n.f("autolayout.warnLocomotiveWaitingLong", j.train.getName(), j.s88("SC"), 0);

            assertTrue(until(() -> logged.contains(advisory) || !j.thread.isAlive(), WAIT_MS),
                "the advisory for a train that has not reached SC was never said: " + logged);

            assertTrue(j.thread.isAlive() && j.outcome.get() == null, "the journey ended at the advisory, with no "
                + "sensor to say the train had arrived: " + j.outcome.get());

            assertTrue(j.holdsItsRoute(), "after the advisory the route was no longer held for the train, so another "
                + "could be routed onto track it may still be standing on: " + j.heldState());

            assertTrue(j.rail.getActiveLocomotives().containsKey(j.train), "the train is no longer counted as under "
                + "way");

            // THE LATE SENSOR, which must still end the journey as an arrival
            int journeysBefore = j.train.getNumPaths();

            assertEquals(j.light("SC").end(), Boolean.TRUE, "a sensor answering after the advisory did not complete "
                + "the journey");
            assertEquals((int) j.train.getNumPaths(), journeysBefore + 1, "the late arrival was not counted as a "
                + "journey");
            j.assertFreed("SC");
        }
        finally
        {
            Locomotive.FEEDBACK_ADVISORY_MS = adviseWas;

            j.train.setSpeed(0);
        }
    }

    /**
     * Kept by Adam ("A: Keep it"): a destination sensor already lit as the last stretch begins (another train, a dirty
     * contact) stops the train where it is, records it as arrived, and frees the track behind it.
     *
     * MUTATION: in `Layout.executePathInternal`'s destination step, wait for the sensor to clear before waiting for it
     * (`if (loc.getFeedbackState(current.getS88())) loc.waitForClearFeedback(current.getS88(), 0);`), and this fails.
     */
    @Test(timeOut = 120000)
    public void testADestinationSensorLitAsTheLastStretchBeginsEndsTheJourneyThere() throws Exception
    {
        Journey j = new Journey(null, "EA", "EB", "EC");

        try
        {
            // After the dispatch, whose own check refuses a lit destination (see the door claims below)
            j.go();

            int journeysBefore = j.train.getNumPaths();

            assertEquals(j.light("EC").light("EB").end(), Boolean.TRUE, "a destination sensor lit before the last "
                + "stretch did not end the journey: the train was left running toward a sensor that was already made");
            assertEquals(j.train.getSpeed(), 0, "the train was not stopped where it was");
            assertEquals((int) j.train.getNumPaths(), journeysBefore + 1, "the journey was not counted as an arrival");
            j.assertFreed("EC");
        }
        finally
        {
            j.train.setSpeed(0);
        }
    }

    /**
     * A lit destination sensor refuses a journey sent by hand (Adam: "it shouldn't be possible to start a route if the
     * destination is occupied"): the Send To list says why, and the dispatch leaves the train and the track as they
     * were.
     *
     * MUTATION: in `Layout.isPathClear`, skip the `errorFeedbackNotClear` check for the path's last edge, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testALitDestinationSensorRefusesAJourneySentByHand() throws Exception
    {
        // One edge, so a dispatch the check let through ends at once on the lit sensor rather than waiting on another
        Journey j = new Journey(null, "HA", "HC");

        assertNull(j.rail.explainDestinations(j.train, true).get("HC"), "control: HC is offered while its sensor is "
            + "clear");

        j.light("HC");

        try
        {
            assertNotNull(j.rail.explainDestinations(j.train, true).get("HC"),
                "the Send To list offers a station whose sensor is lit");

            assertFalse(j.rail.executePathByHand(j.path, j.train, SPEED, Layout.ALWAYS_REVERSE, -1),
                "a journey by hand to a station whose sensor is lit was dispatched");

            assertEquals(j.train.getSpeed(), 0, "the refused train was set moving");
            assertSame(j.rail.getPoint("HA").getCurrentLocomotive(), j.train, "the refused train is no longer where "
                + "it stands");
            j.assertNothingTaken("the refusal");
        }
        finally
        {
            model.setFeedbackState(j.s88("HC"), false);

            j.train.setSpeed(0);
        }
    }

    /**
     * A timetable entry whose destination sensor is lit waits, the train standing and the track free, and goes once the
     * sensor clears.
     *
     * MUTATION: in `Layout.isPathClear`, skip the `errorFeedbackNotClear` check for the path's last edge, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testATimetableWaitsForALitDestinationSensorThenGoes() throws Exception
    {
        aTimetableWaitsForALitDestination(false, "TA", "TB", "TC");
    }

    /**
     * And the same for Return Home's plan, which runs as a timetable one train at a time and retries on its own pace.
     * The entry is built by hand as a sequential timetable rather than planned by `loadReturnToHomeTimetable`, and the
     * sensor is cleared after the first refusal: this run tries three times, two seconds apart, before it gives up.
     *
     * MUTATION: in `Layout.isPathClear`, skip the `errorFeedbackNotClear` check for the path's last edge, and this
     * fails.
     */
    @Test(timeOut = 120000)
    public void testReturnHomesOneAtATimeRunWaitsForALitDestinationSensorThenGoes() throws Exception
    {
        aTimetableWaitsForALitDestination(true, "RA", "RB", "RC");
    }

    /** One entry from the first point to the last, with the last one's sensor lit until the entry has waited. */
    private void aTimetableWaitsForALitDestination(boolean oneAtATime, String... names) throws Exception
    {
        Journey j = new Journey(null, names);
        String destination = names[names.length - 1];

        int preferredWas = j.train.getPreferredSpeed();
        Thread run = null;

        try
        {
            j.train.setPreferredSpeed(SPEED);
            j.light(destination);

            TimetablePath entry = new TimetablePath(j.train, j.path, 0);

            j.rail.setTimetable(Arrays.asList(entry));
            j.rail.setTimetableSequential(oneAtATime);

            String waiting = I18n.f("autolayout.infoTimetableEntryNotYetExecutable", entry.toString());

            run = new Thread(() -> j.rail.executeTimetable(), "timetable-under-test");
            run.setDaemon(true);
            run.start();

            assertTrue(until(() -> logged.contains(waiting) || j.train.getSpeed() > 0, WAIT_MS),
                "the entry neither waited nor ran: " + logged);

            assertEquals(j.train.getSpeed(), 0, "the entry set off toward a station whose sensor is lit");
            j.assertNothingTaken("the waiting entry");

            // CLEARED: the next attempt goes
            int journeysBefore = j.train.getNumPaths();

            model.setFeedbackState(j.s88(destination), false);

            assertTrue(support.Settle.underWay(j.rail, j.train, WAIT_MS), "the entry did not go once the destination "
                + "sensor cleared: " + logged);

            for (int i = 1; i < names.length; i++) j.light(names[i]);

            assertTrue(support.Settle.journeys(j.train, journeysBefore + 1, WAIT_MS), "the entry never arrived");
            assertTrue(support.Settle.idle(j.rail, WAIT_MS), "the timetable never finished");
            j.assertFreed(destination);
        }
        finally
        {
            j.rail.stopLocomotives();

            if (run != null) run.join(WAIT_MS);

            j.train.setPreferredSpeed(preferredWas);
            j.train.setSpeed(0);
        }
    }

    /**
     * A power cut and restore mid-journey neither ends the journey nor gives its track back twice: a lock another route
     * holds through the same throat is still held once the journey has ended, and is free once that route lets go.
     *
     * MUTATION: in `Layout.executePathInternal`, after the destination's wait, add
     * `if (this.control.getPowerEpoch() != powerAtDispatch) this.unlockPath(path, loc);`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAPowerCutMidJourneyEndsItOnceAtItsDestination() throws Exception
    {
        Journey j = new Journey(null, "PA", "PB", "PC");

        // ANOTHER ROUTE'S CLAIM ON A SHARED THROAT: PY -> PZ, held, locks PX -> PY, which the journey's last edge locks
        // too.  Propagated, so the journey is not refused for it; a second release would free it under the other route.
        for (String point : new String[] {"PX", "PY", "PZ"}) j.rail.createPoint(point, false, aSensor());

        Edge throat = j.rail.createEdge("PX", "PY");
        Edge other = j.rail.createEdge("PY", "PZ");

        other.addLockEdge(throat);
        j.path.get(1).addLockEdge(throat);

        other.setOccupied();

        java.lang.reflect.Method power = Reflect.method(MarklinControlStation.class, "setPowerState", boolean.class);

        try
        {
            j.go();

            power.invoke(model, false);
            power.invoke(model, true);

            j.light("PB");

            assertTrue(until(() -> j.reached("PB"), WAIT_MS), "the journey did not go on after a power cut");

            power.invoke(model, false);
            power.invoke(model, true);

            assertEquals(j.light("PC").end(), Boolean.TRUE, "a journey with a power cut on the way did not end as an "
                + "arrival");
            j.assertFreed("PC");

            assertTrue(throat.isLockHeld(null), "the journey released the throat twice, so it reads free while "
                + "another route still holds it");

            other.setUnoccupied();

            assertFalse(throat.isLockHeld(null), "the journey left its own claim on the throat behind");
        }
        finally
        {
            power.invoke(model, true);

            if (other.isLockHeld(null)) other.setUnoccupied();

            j.train.setSpeed(0);
        }
    }

    /**
     * Kept by Adam ("A: Keep it"): an operator's stop on a train autonomy is running gets no reaction at once.  The
     * train stands, its route still held, and the "has not reached" notice comes after the usual wait.
     *
     * MUTATION: in `Layout.executePathInternal`'s intermediate wait, pass
     * `() -> this.retiredRailway.getAsBoolean() || loc.getSpeed() == 0` as the abandon, so a hand stop ends the wait,
     * and this fails.
     */
    @Test(timeOut = 120000)
    public void testAHandStopLeavesTheTrainStandingOnItsHeldRoute() throws Exception
    {
        Journey j = new Journey(null, "KA", "KB", "KC", "KD");

        Locomotive.FEEDBACK_ADVISORY_MS = 600;

        try
        {
            // Once the journey has set its speed for KB's stretch, so the stop is the last word on it
            j.go().waitsOn("KB");

            // THE OPERATOR'S STOP, through the same call the hand controls make
            j.train.setSpeed(0);

            String advisory = I18n.f("autolayout.warnLocomotiveWaitingLong", j.train.getName(), j.s88("KB"), 0);

            assertTrue(until(() -> logged.contains(advisory) || j.train.getSpeed() > 0 || !j.awaits("KB"), WAIT_MS),
                "nothing happened after a hand stop, not even the notice: " + logged);

            assertEquals(j.train.getSpeed(), 0, "autonomy put its speed back after the operator stopped the train");
            assertTrue(j.awaits("KB") && !j.reached("KB"), "the journey stopped waiting on KB without its sensor");
            assertTrue(logged.contains(advisory), "the notice for a train stopped by hand did not come");
            assertTrue(j.holdsItsRoute(), "a hand stop gave up the train's route: " + j.heldState());

            // And it still ends as a journey once the sensors answer
            assertEquals(j.light("KB").light("KC").light("KD").end(), Boolean.TRUE, "the journey did not end once its "
                + "sensors answered");
            j.assertFreed("KD");
        }
        finally
        {
            Locomotive.FEEDBACK_ADVISORY_MS = adviseWas;

            j.train.setSpeed(0);
        }
    }

    /**
     * The same ruling: a train the operator stops and sets going at a speed of their own keeps that speed until the
     * next sensor, where autonomy puts its own back, and is still stopped at the destination.
     *
     * MUTATION: in `Layout.executePathInternal`'s intermediate step, drop the speed adjustment
     * (`if (false && loc.getSpeed() != calculatedSpeed ...`), and this fails.
     */
    @Test(timeOut = 120000)
    public void testAHandSpeedLastsUntilTheNextSensorAndTheTrainStillStopsAtItsDestination() throws Exception
    {
        Journey j = new Journey(null, "GA", "GB", "GC", "GD");

        final int byHand = 50;

        try
        {
            j.go().waitsOn("GB");

            assertEquals(j.train.getSpeed(), SPEED, "precondition: the train did not set off at the journey's speed");

            j.train.setSpeed(0);
            j.train.setSpeed(byHand);

            assertEquals(j.train.getSpeed(), byHand, "the operator's speed was overridden before the next sensor");

            // GC's stretch is set before its wait begins, so waiting on GC means GB's answer has been acted on
            j.light("GB").waitsOn("GC");

            assertEquals(j.train.getSpeed(), SPEED, "autonomy did not put its own speed back at the next sensor");

            assertEquals(j.light("GC").light("GD").end(), Boolean.TRUE, "the journey did not end at its destination");
            assertEquals(j.train.getSpeed(), 0, "the train was not stopped at its destination");
            j.assertFreed("GD");
        }
        finally
        {
            j.train.setSpeed(0);
        }
    }

    /**
     * A failure thrown from the progress callback after the head has moved gives the track back: nothing is left
     * locked, and the train is not left counted as under way.
     *
     * MUTATION: in `Layout.executePath`'s catch, drop `if (hadItsPath) this.unlockPath(path, loc);`, and this fails.
     */
    @Test(timeOut = 120000)
    public void testAFailureInTheProgressCallbackGivesTheTrackBack() throws Exception
    {
        // A locomotive of the test's own, so the callback it carries cannot reach another class
        Journey j = new Journey(new MarklinLocomotive(model, 77, MarklinLocomotive.decoderType.MM2,
            "FR210 failing train"), "FA", "FB", "FC");

        RuntimeException failure = new IllegalStateException("thrown by the progress callback");

        j.train.setCallback(Layout.CB_ROUTE_PROG, l -> { throw failure; });

        try
        {
            assertSame(j.go().light("FB").end(), failure, "precondition: the callback's failure did not end the "
                + "journey");

            assertEquals(j.train.getSpeed(), 0, "the failed journey left the train running");
            j.assertNothingTaken("the failed journey");

            assertNotSame(j.rail.getPoint("FA").getCurrentLocomotive(), j.train, "the failed journey still holds FA");
            assertNotSame(j.rail.getPoint("FB").getCurrentLocomotive(), j.train, "the failed journey still holds FB");
        }
        finally
        {
            j.train.setSpeed(0);
        }
    }

    /** A new sensor, clear. */
    private static String aSensor()
    {
        MarklinFeedback sensor = model.newFeedback(nextSensor++, null);

        model.setFeedbackState(sensor.getName(), false);

        return sensor.getName();
    }

    /**
     * A train on a railway of its own, made current: one line through the points named, a clear sensor each, its two
     * ends stations, the train standing still on the first.  `go` sends it along the whole line, as a door does.
     */
    private static final class Journey
    {
        final Layout rail;
        final List<Edge> path = new ArrayList<>();
        final Locomotive train;
        final AtomicReference<Object> outcome = new AtomicReference<>();
        Thread thread;

        /** @param train the train, or null for a locomotive of the database, a different one for each claim */
        Journey(Locomotive train, String... names) throws Exception
        {
            rail = new Layout(model);

            for (int i = 0; i < names.length; i++)
            {
                rail.createPoint(names[i], i == 0 || i == names.length - 1, aSensor());
            }

            for (int i = 0; i + 1 < names.length; i++) path.add(rail.createEdge(names[i], names[i + 1]));

            rail.makeCurrent();

            this.train = train != null ? train : model.getLocByName(model.getLocList().get(nextTrain++));

            this.train.setSpeed(0);

            rail.getPoint(names[0]).setLocomotive(this.train);
        }

        /** Sends the train on its own thread; `outcome` gets the answer, or what was thrown.  Returns once it moves. */
        Journey go()
        {
            thread = new Thread(() ->
            {
                try
                {
                    outcome.set(rail.executePath(path, train, SPEED, null));
                }
                catch (RuntimeException e)
                {
                    outcome.set(e);
                }
            }, "journey-" + train.getName());

            thread.setDaemon(true);
            thread.start();

            assertTrue(until(() -> train.getSpeed() > 0, WAIT_MS), "precondition: the train never set off");

            return this;
        }

        /** Waits for the journey's thread to end, and gives its outcome. */
        Object end() throws InterruptedException
        {
            thread.join(WAIT_MS);

            return outcome.get();
        }

        String s88(String point)
        {
            return rail.getPoint(point).getS88();
        }

        /** Makes a point's sensor, as the train would. */
        Journey light(String point)
        {
            model.setFeedbackState(s88(point), true);

            return this;
        }

        /** Whether the journey is waiting on this point's sensor now. */
        boolean awaits(String point)
        {
            try
            {
                java.util.Map<?, ?> pending =
                    (java.util.Map<?, ?>) Reflect.get(Layout.class, rail, "locomotivePendingS88");

                return s88(point).equals(pending.get(train));
            }
            catch (Exception e)
            {
                throw new AssertionError("cannot read which sensor the journey waits on", e);
            }
        }

        /** Returns once the journey is waiting on this point's sensor. */
        Journey waitsOn(String point)
        {
            assertTrue(until(() -> awaits(point), WAIT_MS), "the journey never began waiting on " + point);

            return this;
        }

        boolean reached(String point)
        {
            List<Point> reached = rail.getReachedMilestones(train);

            return reached != null && reached.contains(rail.getPoint(point));
        }

        /** Whether every edge of the path is locked and every point past its start is held for the train. */
        boolean holdsItsRoute()
        {
            for (Edge e : path)
            {
                if (!e.isLockHeld(null) || e.getEnd().getCurrentLocomotive() != train) return false;
            }

            return true;
        }

        String heldState()
        {
            StringBuilder out = new StringBuilder();

            for (Edge e : path)
            {
                out.append(e.getName()).append(e.isLockHeld(null) ? " locked, " : " free, ")
                    .append(e.getEnd().getName()).append(" holds ").append(e.getEnd().getCurrentLocomotive())
                    .append("; ");
            }

            return out.toString();
        }

        /** The train is not under way and no edge of its path is locked; `who` names what should have left it so. */
        void assertNothingTaken(String who)
        {
            assertFalse(rail.getActiveLocomotives().containsKey(train), who + " left the train counted as under way");

            for (Edge e : path) assertFalse(e.isLockHeld(null), who + " left " + e.getName() + " locked");
        }

        /** The journey is over: nothing taken, the train recorded at its destination and nowhere before. */
        void assertFreed(String destination)
        {
            assertNothingTaken("the journey");

            assertSame(rail.getPoint(destination).getCurrentLocomotive(), train, "the train is not recorded at "
                + destination);

            for (Edge e : path)
            {
                assertNotSame(e.getStart().getCurrentLocomotive(), train, "the track behind the train is still held: "
                    + e.getStart().getName());
            }
        }
    }
}
