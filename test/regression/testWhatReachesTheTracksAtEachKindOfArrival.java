package regression;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinFeedback;

/**
 * A reversal command reaches the tracks at every kind of square that should turn a train, and at no
 * other kind.
 *
 * Adam, 2026-09-08: *"in your tests for arrival, make sure you check for an emitted reversal command to
 * the tracks."*  And on 2026-09-10, asking what the suite actually covered: *"Do we have tests
 * validating that reversal commands are being issued when intended at terminuses/reversing
 * stations/points, and not otherwise?"*
 *
 * It half did.  `testTheReversalReachesTheTracks` watched the wire, but on ONE square kind - a
 * reversing point - and made a COMPARATIVE claim: the turned journey sends more direction commands than
 * the kept one.  `testAReversalCommandIsEmitted` covers four cases and reads `goingForward()`, which is
 * the application's belief and not the cable.  A terminus had no emission test at all, in this class or
 * any other: nine classes build `setTerminus(true)` fixtures and not one of them asserts a direction
 * command or a direction after arrival.
 *
 * **What makes an absolute claim possible is the window.**  Dispatch sets a locomotive's direction as it
 * departs, so every journey puts direction commands on the wire - counting from zero over a whole
 * journey cannot tell an arrival that turned the train from one that did not, and reading it that way
 * is how the older test first got its own evidence wrong.  `Layout.CB_PRE_ARRIVAL` fires exactly once,
 * on the last leg, after the pre-arrival speed reduction and before the destination's sensor.  A mark
 * taken there and a count taken after the run is the arrival and nothing else.
 *
 * **The rule these pin** is one statement in `Layout.executePath`:
 * `if (arrived.isTerminus() || shouldReverseAt(arrived, arrived, loc, reversals))`.  So there are
 * exactly three arrivals worth asking about, and all three are here, with the negative:
 *
 * | the square | what should reach the tracks |
 * |---|---|
 * | a terminus | one direction command, always |
 * | a reversing point the policy answers YES for | one |
 * | a reversing point the policy answers NO for | none |
 * | a plain station | none |
 * | a may-reverse square PASSED THROUGH on the way | none at the arrival |
 *
 * **A compulsory turn is a terminus by the time the railway sees it**, so the first row covers it:
 * `AutonomyBuilder` emits a `mustReverse` square with `terminus:true`, which
 * `core.testAutonomyDiagramReversal` and `core.testACompulsoryTurnIsChosenLikeAnyOtherStation` both
 * assert.  Between them and the first row, "every train that arrives at a compulsory turn is reversed"
 * is pinned end to end without this class needing to run the builder.
 *
 * The train is this class's own and is deleted in teardown; `init()` opens Adam's real locomotive
 * database, so one left behind is one on his railway.
 *
 * @author Adam
 */
public class testWhatReachesTheTracksAtEachKindOfArrival
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static support.WireRecorder wire;

    private static final String DRIVER = "arrival wire probe";

    private static final int ADDRESS = 65;

    /**
     * Where this class's feedback addresses start.
     *
     * Clear of `testTheReversalReachesTheTracks`, which takes 8440 and 8444, so a stray fixture left by
     * a crash is identifiable and the two classes cannot describe each other's sensors.
     */
    private static final int FIRST_SENSOR = 8460;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Before the model: init reads the layout preference and would otherwise open Adam's own
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        // DEBUG, and it is not optional: `setSimulate` refuses outside it, and the not-connected branch
        // of `exec` only logs the message it would have sent while debugging - which is the whole of
        // how this class can see the wire.
        model = init(null, true, false, false, true);
        model.stop();

        assertNotNull(model.newMM2Locomotive(DRIVER, ADDRESS),
            "the class could not create its own train, so every claim below would be about whatever"
            + " the database happened to contain");

        wire = support.WireRecorder.open();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (wire != null) wire.close();

        if (model != null)
        {
            try
            {
                model.deleteLoc(DRIVER);
            }
            catch (Exception alreadyGone)
            {
            }
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * The recorder hears the railway at all.
     *
     * Asserted first and on its own, because every count below is a number of lines matching a needle:
     * a recorder attached to a model that is not in debug hears nothing, and all five claims would
     * then be zero for a reason that has nothing to do with any arrival.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testTheRecorderHearsTheRailway() throws Exception
    {
        wire.clear();

        Ride ride = ride("HEARS", 0, Kind.PLAIN);

        run(ride, null, "the hearing check");

        assertTrue(wire.size() > 0,
            "the control station logged nothing at all during a whole journey, so this class is"
            + " watching the wrong logger, or the model is not in debug - and every count below would"
            + " be zero without any arrival having been tested");

        assertTrue(wire.directionCommands() > 0,
            "a journey ran and no direction command was sent at all. Dispatch sets a locomotive's"
            + " direction as it departs, so at least one is expected on any journey - the needle this"
            + " class counts with is matching nothing");
    }

    /**
     * A plain station turns nobody round.
     *
     * The negative half of Adam's question, and the one the older test could not make: it compared two
     * journeys rather than looking at one arrival.
     *
     * MUTATION: dropping the `arrived.isTerminus() ||` guard in `Layout.executePath` does not move
     * this (a plain station is neither); replacing the whole condition with `true` does.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testAPlainArrivalPutsNoDirectionCommandOnTheWire() throws Exception
    {
        Ride ride = ride("PLAIN", 10, Kind.PLAIN);

        int atTheArrival = run(ride, null, "the plain journey");

        assertEquals(wire.directionCommandsSince(atTheArrival), 0,
            "a train arriving at a plain station put a direction command on the wire: "
            + wire.since(atTheArrival) + ". Nothing about that square asks for a turn");
    }

    /**
     * A terminus turns every train that arrives, and nobody is asked.
     *
     * This is the case that had no emission test anywhere in the suite.
     *
     * MUTATION, and this is the one it exists for: removing `arrived.isTerminus() ||` from
     * `Layout.executePath`'s arrival condition fails this and leaves every other claim here green.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testATerminusArrivalAlwaysPutsOneOnTheWire() throws Exception
    {
        Ride ride = ride("TERM", 20, Kind.TERMINUS);

        assertTrue(ride.destination.isTerminus(),
            "the fixture's destination is not a terminus, so this claim is about some other square");

        int atTheArrival = run(ride, null, "the terminus journey");

        assertEquals(wire.directionCommandsSince(atTheArrival), 1,
            "a train arriving at a TERMINUS put " + wire.directionCommandsSince(atTheArrival)
            + " direction commands on the wire rather than one: " + wire.since(atTheArrival)
            + ". Adam's rule is that a terminus always reverses at the end, and nobody is asked");
    }

    /**
     * A reversing point the policy answers YES for turns the train.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testAReversingPointAnsweredYesPutsOneOnTheWire() throws Exception
    {
        Ride ride = ride("REVYES", 30, Kind.REVERSING);

        int atTheArrival = run(ride, answering(true, ride.destination), "the answered-yes journey");

        assertEquals(wire.directionCommandsSince(atTheArrival), 1,
            "a train arriving at a reversing point the operator said YES to put "
            + wire.directionCommandsSince(atTheArrival) + " direction commands on the wire rather"
            + " than one: " + wire.since(atTheArrival));
    }

    /**
     * And answered NO, it does not.
     *
     * The absolute form of what `testTheReversalReachesTheTracks` asserts comparatively - that test
     * says the turned journey sends MORE, this one says this arrival sends NONE.
     *
     * MUTATION: making `shouldReverseAt` ignore the policy and answer `at.isReversing()` fails this and
     * leaves the answered-yes claim green, which is the pair that says the answer is what decides.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testAReversingPointAnsweredNoPutsNoneOnTheWire() throws Exception
    {
        Ride ride = ride("REVNO", 40, Kind.REVERSING);

        int atTheArrival = run(ride, answering(false, ride.destination), "the answered-no journey");

        assertEquals(wire.directionCommandsSince(atTheArrival), 0,
            "a train arriving at a reversing point the operator said NO to still put a direction"
            + " command on the wire: " + wire.since(atTheArrival) + ". Adam's rule is that a"
            + " reversing point turns a train only when the turn is intended there");
    }

    /**
     * A may-reverse square PASSED THROUGH leaves the arrival beyond it alone.
     *
     * The mid-path half of *"don't reverse if passing onwards"*. The turn, if there is one, belongs to
     * the square in the middle; what this asserts is that nothing extra reaches the tracks when the
     * train comes to rest at a plain station beyond it.
     *
     * @throws Exception on a failure to run the journey
     */
    @Test
    public void testPassingThroughAMayReverseSquareLeavesTheArrivalAlone() throws Exception
    {
        Ride ride = ride("THROUGH", 50, Kind.REVERSING_MIDDLE);

        assertTrue(ride.middle.isReversing(),
            "the fixture's middle square is not a reversing point, so nothing is being passed through");

        int atTheArrival = run(ride, answering(false, ride.middle), "the pass-through journey");

        assertEquals(wire.directionCommandsSince(atTheArrival), 0,
            "a train that ran THROUGH a may-reverse square and came to rest at a plain station beyond"
            + " it put a direction command on the wire at that arrival: " + wire.since(atTheArrival));
    }

    /**
     * What the destination of a fixture is.
     */
    private enum Kind
    {
        /** A destination that is neither a terminus nor a reversing point. */
        PLAIN,

        /** A dead end: every train that arrives turns, and nobody is asked. */
        TERMINUS,

        /** A square trains MAY turn at, so the policy decides. */
        REVERSING,

        /** A plain destination, with a may-reverse square on the way to it. */
        REVERSING_MIDDLE
    }

    /**
     * One built fixture and the journey across it.
     */
    private static final class Ride
    {
        private Layout layout;

        private List<Edge> path;

        private Point middle;

        private Point destination;
    }

    /**
     * A straight run of two legs, with the destination made whatever kind is asked for.
     *
     * @param tag distinguishes this fixture's squares from the others
     * @param offset how far along the x axis and the sensor range this one sits
     * @param kind what the destination is
     * @return the fixture, with the driver standing at START
     * @throws Exception on a failure to build it
     */
    private static Ride ride(String tag, int offset, Kind kind) throws Exception
    {
        Ride ride = new Ride();

        ride.layout = new Layout(model);

        int first = FIRST_SENSOR + offset;

        MarklinFeedback a = model.newFeedback(first, null);
        MarklinFeedback b = model.newFeedback(first + 1, null);
        MarklinFeedback c = model.newFeedback(first + 2, null);

        model.setFeedbackState(a.getName(), true);
        model.setFeedbackState(b.getName(), false);
        model.setFeedbackState(c.getName(), false);

        ride.layout.createPoint(tag + "_START", true, a.getName());
        ride.layout.createPoint(tag + "_MIDDLE", true, b.getName());
        ride.layout.createPoint(tag + "_END", true, c.getName());

        // COORDINATES, because `sideTowards` answers from them and the turn rule asks it. A hand-built
        // graph has none, and without them the rule cannot tell which side a leg uses.
        place(ride.layout, tag + "_START", offset, 0);
        place(ride.layout, tag + "_MIDDLE", offset + 1, 0);
        place(ride.layout, tag + "_END", offset + 2, 0);

        ride.middle = ride.layout.getPoint(tag + "_MIDDLE");
        ride.destination = ride.layout.getPoint(tag + "_END");

        if (kind == Kind.TERMINUS) ride.destination.setTerminus(true);

        if (kind == Kind.REVERSING) ride.destination.setReversing(true);

        if (kind == Kind.REVERSING_MIDDLE) ride.middle.setReversing(true);

        ride.layout.createEdge(tag + "_START", tag + "_MIDDLE");
        ride.layout.createEdge(tag + "_MIDDLE", tag + "_END");

        ride.layout.getPoint(tag + "_START").setLocomotive(model.getLocByName(DRIVER));

        ride.layout.setSimulate(true);
        ride.layout.setMinDelay(0);
        ride.layout.setMaxDelay(0);

        ride.path = new ArrayList<>();

        ride.path.add(ride.layout.getEdge(tag + "_START", tag + "_MIDDLE"));
        ride.path.add(ride.layout.getEdge(tag + "_MIDDLE", tag + "_END"));

        return ride;
    }

    private static void place(Layout layout, String name, int x, int y)
    {
        layout.getPoint(name).setX(x);
        layout.getPoint(name).setY(y);
    }

    /**
     * The answer, for one square, as the prompt's policy expresses it.
     *
     * @param turn what to answer
     * @param asked the one square it is answered about
     * @return the policy
     */
    private static Layout.ReversalPolicy answering(final boolean turn, final Point asked)
    {
        return new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return turn && at == asked;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == asked;
            }
        };
    }

    /**
     * Runs the journey and returns the mark taken at its pre-arrival.
     *
     * **The mark is what makes every claim in this class absolute.** `CB_PRE_ARRIVAL` fires once, on
     * the last leg, after the speed reduction and before the destination's sensor - so counting from
     * it excludes the direction command dispatch sends at departure, which every journey has.
     *
     * @param ride the fixture
     * @param answered the reversal policy, or null for the default
     * @param which names the journey in a failure message
     * @return the position in the log at the pre-arrival
     * @throws Exception on a failure to run it
     */
    private static int run(Ride ride, Layout.ReversalPolicy answered, String which) throws Exception
    {
        Locomotive driver = model.getLocByName(DRIVER);

        assertNotNull(driver, "this class's train is gone from the database");

        // THE SAME STARTING DIRECTION EVERY TIME. The fixtures share one locomotive, so without this
        // each journey inherits whatever the last one left - and a departure that happens to need no
        // correction looks exactly like an arrival that sent nothing.
        driver.setDirection(Locomotive.locDirection.DIR_FORWARD);

        final int[] atTheArrival = { -1 };

        driver.setCallback(Layout.CB_PRE_ARRIVAL, (l) -> atTheArrival[0] = wire.mark());

        ride.layout.runLocomotives();

        ExecutorService watchdog = Executors.newSingleThreadExecutor();

        try
        {
            Future<Boolean> leg = watchdog.submit(
                () -> ride.layout.executePath(ride.path, driver, 20, null, answered));

            assertTrue(leg.get(30, TimeUnit.SECONDS),
                which + " reported failure rather than completing");
        }
        catch (TimeoutException wedged)
        {
            ride.layout.stopLocomotives();

            fail(which + " never arrived");
        }
        finally
        {
            watchdog.shutdownNow();

            ride.layout.stopLocomotives();
        }

        assertTrue(atTheArrival[0] >= 0,
            which + " never reached its pre-arrival, so no window was taken and the count below would"
            + " be over the whole journey - which every journey fails, because dispatch sets a"
            + " direction as the train departs");

        return atTheArrival[0];
    }
}
