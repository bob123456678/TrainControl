package support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.Conversion;

/**
 * What the control station would have put on the wire, in order.
 *
 * Adam, 2026-09-08: **"in your tests for arrival, make sure you check for an emitted reversal command
 * to the tracks."**  The application's own belief about a locomotive - `goingForward()` - is the right
 * question for *was the decision taken* and the wrong one for *did anything happen*: a `_setDirection`
 * with no `exec` behind it satisfies every assertion about the model while the railway sits still.
 *
 * **How it can see the wire at all.**  `MarklinLocomotive.setDirection` ends in
 * `network.exec(new CS2Message(CMD_LOCO_DIRECTION, ...))`.  With no Central Station connected,
 * `MarklinControlStation.exec` takes the not-connected branch and LOGS the message it would have sent -
 * `DEBUG_LOG_NETWORK` is on by default - through `java.util.logging`.  So a handler on that logger sees
 * exactly what would have gone down the cable, and nothing in production changes to allow it.
 *
 * **The model has to be in debug**, because that branch only logs while debugging:
 * `init(null, true, false, false, true)`.  A recorder attached to a model built any other way hears
 * nothing, which is why `size()` is worth asserting before anything else.
 *
 * **Marks, because the interesting window is the ARRIVAL and not the journey.**  Dispatch sets a
 * locomotive's direction as it departs, so every journey puts direction commands on the wire and a test
 * that counts from zero cannot tell an arrival that turned the train from one that did not.  The whole
 * point of `Layout.CB_PRE_ARRIVAL` is that it fires once, on the last leg, after the pre-arrival speed
 * reduction and before the destination's sensor - so a mark taken there and a count taken after the run
 * is exactly the arrival, and nothing else.
 *
 * **WHAT THE WIRE DROPS, and it is a lower bound rather than a count** (E8-C3).
 * `MarklinControlStation.log(String)` is `if (message != null && !message.equals(this.lastMessage))` -
 * two IDENTICAL, ADJACENT messages reach this recorder as one line.  That dedupe is right for a log a
 * person reads and there is nothing to fix in it, but it means a claim of "exactly one direction
 * command" would also hold if the rule emitted two of them back to back.
 *
 * It does not weaken the claims that matter.  A direction command carries the direction, so the two
 * commands a double reversal would send are not identical; and every absolute claim here is about the
 * ARRIVAL window, where one is expected and zero is the alternative.  Where a count is used as an upper
 * bound on identical commands, read it as "at least this many".
 *
 * Lifted out of `regression.testTheReversalReachesTheTracks`, which had all of this privately and could
 * therefore only make the one comparative claim it makes.
 *
 * @author Adam
 */
public final class WireRecorder
{
    /**
     * What a direction command looks like in the log, built from the constant it IS.
     *
     * `CS2Message.toString` prints the command as HEX - "Command: 0x5" - so a needle typed by hand
     * reads as "nothing was sent" the moment the constant moves.  This one cannot drift from it.
     */
    private static final String WIRE_DIRECTION =
        "Command: " + Conversion.intToHex(CS2Message.CMD_LOCO_DIRECTION);

    /**
     * Every line the control station logged while this recorder was attached.
     *
     * Copy-on-write because the logging happens on the locomotive's own thread and the assertions on
     * the test's.
     */
    private final List<String> heard = new CopyOnWriteArrayList<>();

    private final Handler handler;

    private final Logger listening;

    private WireRecorder()
    {
        this.handler = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                if (record != null && record.getMessage() != null) heard.add(record.getMessage());
            }

            @Override
            public void flush() { }

            @Override
            public void close() { }
        };

        this.handler.setLevel(Level.ALL);

        this.listening = Logger.getLogger(MarklinControlStation.class.getName());

        this.listening.setLevel(Level.ALL);
        this.listening.addHandler(this.handler);
    }

    /**
     * Starts listening.
     *
     * @return the recorder, to be closed in a teardown
     */
    public static WireRecorder open()
    {
        return new WireRecorder();
    }

    /**
     * Stops listening.  Safe to call twice.
     */
    public void close()
    {
        this.listening.removeHandler(this.handler);
    }

    /**
     * Forgets everything heard so far.
     */
    public void clear()
    {
        this.heard.clear();
    }

    /**
     * A position in the log, to count from later.
     *
     * @return where the log has reached
     */
    public int mark()
    {
        return this.heard.size();
    }

    /**
     * How many lines have been logged at all.
     *
     * Worth asserting non-zero before any other claim: a recorder attached to a model that is not in
     * debug hears nothing, and every count below it would then be zero for a reason that has nothing
     * to do with the railway.
     *
     * @return the number of lines heard
     */
    public int size()
    {
        return this.heard.size();
    }

    /**
     * How many direction commands the control station has tried to send since a mark.
     *
     * @param from a position returned by {@link #mark()}
     * @return the count
     */
    public int directionCommandsSince(int from)
    {
        int seen = 0;

        List<String> snapshot = new java.util.ArrayList<>(this.heard);

        for (int i = Math.max(0, from); i < snapshot.size(); i++)
        {
            String line = snapshot.get(i);

            if (line != null && line.contains(WIRE_DIRECTION) && line.contains("Type: Loc")) seen++;
        }

        return seen;
    }

    /**
     * How many direction commands have been sent since the recorder was cleared.
     *
     * @return the count
     */
    public int directionCommands()
    {
        return directionCommandsSince(0);
    }

    /**
     * What was heard since a mark, for a failure message.
     *
     * @param from a position returned by {@link #mark()}
     * @return the lines, newest last
     */
    public List<String> since(int from)
    {
        List<String> snapshot = new java.util.ArrayList<>(this.heard);

        return snapshot.subList(Math.min(Math.max(0, from), snapshot.size()), snapshot.size());
    }
}
