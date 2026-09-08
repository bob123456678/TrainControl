package regression;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.testng.Assert.*;
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
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.Conversion;

/**
 * The reversal reaches the TRACKS, not just the model's idea of the direction.
 *
 * Adam, 2026-09-08: *"in your tests for arrival, make sure you check for an emitted reversal command to
 * the tracks."*
 *
 * `testAReversalCommandIsEmitted` reads `goingForward()`, which is the application's own belief. That
 * is the right question for "was the decision taken" and the wrong one for "did anything happen": a
 * `_setDirection` with no `exec` behind it would satisfy every assertion in that class while the
 * railway sat still. This watches the message instead.
 *
 * **How.** `MarklinLocomotive.setDirection` ends in `network.exec(new CS2Message(CMD_LOCO_DIRECTION,
 * ...))`. With no Central Station connected, `MarklinControlStation.exec` takes the not-connected
 * branch and logs the message it would have sent - `DEBUG_LOG_NETWORK` is on by default - through
 * `java.util.logging`. So a handler on that logger sees exactly what would have gone down the wire, in
 * order, and nothing in production changes to allow it.
 *
 * @author Adam
 */
public class testTheReversalReachesTheTracks
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static final String DRIVER = "OB189 wire";

    private static final int ADDRESS = 64;

    /** Every line the control station logged while a recorder was attached. */
    private static final List<String> HEARD = new CopyOnWriteArrayList<>();

    private static Handler recorder;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Before the model: init reads the layout preference and would otherwise open Adam's own
        // railway (OB-111).
        sandbox = support.LayoutSandbox.open();

        // Debug, because setSimulate refuses outside it and the not-connected branch of `exec` only
        // logs the message when debugging.
        model = init(null, true, false, false, true);
        model.stop();

        model.newMM2Locomotive(DRIVER, ADDRESS);

        recorder = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                if (record != null && record.getMessage() != null) HEARD.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        recorder.setLevel(Level.ALL);

        Logger listening = Logger.getLogger(MarklinControlStation.class.getName());

        listening.setLevel(Level.ALL);
        listening.addHandler(recorder);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (recorder != null)
        {
            Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(recorder);
        }

        if (model != null)
        {
            try { model.deleteLoc(DRIVER); } catch (Exception ignored) { }
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * The answer, for one square, as the prompt's policy expresses it.
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
     * A straight run of two legs, the destination being one trains may turn at.
     *
     * @param tag distinguishes this fixture from the others
     * @param from the first sensor address
     * @param x where the chain starts, so the geometry can answer which side an edge uses
     * @return the layout, with the driver at START
     * @throws Exception on a failure to build it
     */
    private static Layout railway(String tag, int from, int x) throws Exception
    {
        Layout layout = new Layout(model);

        MarklinFeedback a = model.newFeedback(from, null);
        MarklinFeedback b = model.newFeedback(from + 1, null);
        MarklinFeedback c = model.newFeedback(from + 2, null);

        model.setFeedbackState(a.getName(), true);
        model.setFeedbackState(b.getName(), false);
        model.setFeedbackState(c.getName(), false);

        layout.createPoint(tag + "_START", true, a.getName());
        layout.createPoint(tag + "_MIDDLE", true, b.getName());
        layout.createPoint(tag + "_END", true, c.getName());

        // COORDINATES, because `sideTowards` answers from them and `theNextLegNeedsATurn` asks it.
        // A hand-built graph has none, and without them the rule cannot tell which side a leg uses.
        place(layout, tag + "_START", x, 0);
        place(layout, tag + "_MIDDLE", x + 1, 0);
        place(layout, tag + "_END", x + 2, 0);

        layout.getPoint(tag + "_END").setReversing(true);

        layout.createEdge(tag + "_START", tag + "_MIDDLE");
        layout.createEdge(tag + "_MIDDLE", tag + "_END");

        layout.getPoint(tag + "_START").setLocomotive(model.getLocByName(DRIVER));

        layout.setSimulate(true);

        return layout;
    }

    private static void place(Layout layout, String name, int x, int y)
    {
        layout.getPoint(name).setX(x);
        layout.getPoint(name).setY(y);
    }

    /**
     * Runs one leg and insists it arrived.
     */
    private static void run(Layout layout, List<Edge> path, Locomotive driver,
        Layout.ReversalPolicy answered, String which) throws Exception
    {
        ExecutorService watchdog = Executors.newSingleThreadExecutor();

        try
        {
            Future<Boolean> leg = watchdog.submit(
                () -> layout.executePath(path, driver, 20, null, answered));

            assertTrue(leg.get(30, TimeUnit.SECONDS),
                which + " reported failure rather than completing");
        }
        catch (TimeoutException wedged)
        {
            layout.stopLocomotives();

            fail(which + " never arrived");
        }
        finally
        {
            watchdog.shutdownNow();
        }
    }

    /** What a direction command looks like in the log, built from the constant it is. */
    private static final String WIRE_DIRECTION =
        "Command: " + Conversion.intToHex(CS2Message.CMD_LOCO_DIRECTION);

    /** How many direction commands the control station has tried to send. */
    private static int directionCommands()
    {
        int seen = 0;

        for (String line : HEARD)
        {
            //  prints the command as HEX, not by name - "Command: 0x5" - so the
            // needle is built from the constant rather than typed, and cannot drift from it.
            if (line != null && line.contains(WIRE_DIRECTION) && line.contains("Type: Loc")) seen++;
        }

        return seen;
    }

    /**
     * Answering no puts a direction command on the wire; answering yes puts none.
     *
     * This is the assertion Adam asked for. `goingForward()` is the application's belief about the
     * train, and a belief with no message behind it is exactly the failure that would look like a
     * working reversal from inside the process and like a dead one from the platform.
     *
     * MUTATION: replace `switchDirection()` in the arrival block with `_setDirection` and the model's
     * direction still flips - every assertion in `testAReversalCommandIsEmitted` passes - while this
     * fails, because nothing was sent.
     *
     * @throws Exception on a failure to run the journeys
     */
    @Test
    public void testTheAnsweredTurnPutsACommandOnTheWire() throws Exception
    {
        Locomotive driver = model.getLocByName(DRIVER);

        Layout keeping = railway("WIRE_KEEP", 8440, 0);

        List<Edge> kept = new ArrayList<>();

        kept.add(keeping.getEdge("WIRE_KEEP_START", "WIRE_KEEP_MIDDLE"));
        kept.add(keeping.getEdge("WIRE_KEEP_MIDDLE", "WIRE_KEEP_END"));

        keeping.runLocomotives();

        // THE SAME STARTING DIRECTION FOR BOTH, and set BEFORE the recorder is cleared so the command
        // it emits is not counted.  The two journeys share one locomotive, so without this the
        // departure of the second corrects a direction the first left behind - and the counts came
        // out equal for two different reasons, which read as the answered turn sending nothing.
        driver.setDirection(Locomotive.locDirection.DIR_FORWARD);

        HEARD.clear();

        run(keeping, kept, driver, answering(false, keeping.getPoint("WIRE_KEEP_END")),
            "the kept-direction journey");

        int afterKeeping = directionCommands();

        Layout turning = railway("WIRE_TURN", 8444, 10);

        List<Edge> turned = new ArrayList<>();

        turned.add(turning.getEdge("WIRE_TURN_START", "WIRE_TURN_MIDDLE"));
        turned.add(turning.getEdge("WIRE_TURN_MIDDLE", "WIRE_TURN_END"));

        turning.runLocomotives();

        driver.setDirection(Locomotive.locDirection.DIR_FORWARD);

        HEARD.clear();

        run(turning, turned, driver, answering(true, turning.getPoint("WIRE_TURN_END")),
            "the reversed journey");

        int afterTurning = directionCommands();

        assertTrue(HEARD.size() > 0,
            "the control station logged nothing at all during a journey, so this test is watching the"
            + " wrong logger and would pass however little reached the tracks");

        // COMPARED, NOT COUNTED FROM ZERO.  Dispatch sets the direction at departure, so both
        // journeys put direction commands on the wire; what distinguishes them is the extra one at
        // the destination.  Asserting "none when keeping" fails on a working railway, which is how
        // this test first read its own evidence wrong.
        assertTrue(afterTurning > afterKeeping,
            "the two journeys put the SAME number of direction commands on the wire - " + afterTurning
            + " either way - so the answered turn sent nothing extra to the tracks. The operator said"
            + " no, do not keep the direction, and the model believes the train turned: that is the"
            + " failure that looks correct from inside the application and does nothing on the"
            + " railway (OB-189)");

        assertEquals(afterTurning - afterKeeping, 1,
            "the answered turn should be exactly ONE extra direction command. " + afterKeeping
            + " on the kept journey and " + afterTurning + " on the reversed one. More than one means"
            + " the train is being turned twice somewhere, which cancels out and looks to the operator"
            + " like nothing happened at all");
    }
}
