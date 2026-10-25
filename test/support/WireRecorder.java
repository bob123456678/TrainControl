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
 * `TestStation.simulatedInDebug()`.  A recorder attached to a model built any other way hears
 * nothing, which is why `size()` is worth asserting before anything else.
 *
 * **Marks, because the interesting window is the ARRIVAL and not the journey.**  Dispatch sets a
 * locomotive's direction as it departs, so every journey puts direction commands on the wire and a test
 * that counts from zero cannot tell an arrival that turned the train from one that did not.  The whole
 * point of `Layout.CB_PRE_ARRIVAL` is that it fires once, on the last leg, after the pre-arrival speed
 * reduction and before the destination's sensor - so a mark taken there and a count taken after the run
 * is exactly the arrival, and nothing else.
 *
 * **WHAT THE WIRE DROPS, and it is a lower bound rather than a count**.
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
 * **Every kind of command, read back** (FR-136 Phase 6): {@link #sentSince} parses each logged command into a
 * {@link Sent}, and the filters pick the speed, function, direction, accessory and system (power, stop) commands, to
 * one locomotive or accessory where a UID is given.
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
     * One command the control station tried to send, read back from its log line.
     *
     * The line is `CS2Message.toString`, which prints the command in hex ("Command: 0x6") and the data as hex bytes
     * ("Data:   0x00|0x00|0x40|0x05|0x01|0x01"), so both are read back as the station would have been given them.
     */
    public static final class Sent
    {
        /** The command, as `CS2Message.CMD_*` names it. */
        public final int command;

        private final int[] data;

        private Sent(int command, int[] data)
        {
            this.command = command;
            this.data = data;
        }

        /**
         * @return how many data bytes it carries; a question to the station carries fewer than a command does
         */
        public int length()
        {
            return this.data.length;
        }

        /**
         * @return the locomotive's or accessory's UID, the first four bytes; 0 for the whole railway
         */
        public int uid()
        {
            return this.data.length < 4 ? -1
                : (this.data[0] << 24) | (this.data[1] << 16) | (this.data[2] << 8) | this.data[3];
        }

        /**
         * @return the byte after the UID: a function's number, an accessory's position, a direction, a system
         * sub-command
         */
        public int fifth()
        {
            return this.data.length < 5 ? -1 : this.data[4];
        }

        /**
         * @return the byte after that: 1 for a function switched on
         */
        public int sixth()
        {
            return this.data.length < 6 ? -1 : this.data[5];
        }

        /**
         * @return a speed command's speed, as the locomotive's 0 to 100 (the wire carries ten times it)
         */
        public int speed()
        {
            return this.data.length < 6 ? -1 : ((this.data[4] << 8) | this.data[5]) / 10;
        }

        @Override
        public String toString()
        {
            StringBuilder s = new StringBuilder("0x" + Integer.toHexString(this.command) + " [");

            for (int i = 0; i < this.data.length; i++) s.append(i == 0 ? "" : " ").append(this.data[i]);

            return s.append("]").toString();
        }
    }

    /**
     * Every command the control station tried to send since a mark, in order.
     *
     * Only the lines it logs for a command it could not send are read: a packet handed to `receiveMessage` can be
     * logged in the same format, and is not something the program sent.
     *
     * @param from a position returned by {@link #mark()}
     * @return the commands
     */
    public List<Sent> sentSince(int from)
    {
        String unsent = org.traincontrol.util.I18n.f("network.transmissionDisabled", "");

        List<Sent> sent = new java.util.ArrayList<>();

        for (String line : since(from))
        {
            if (line == null || !line.startsWith(unsent)) continue;

            java.util.regex.Matcher command = COMMAND.matcher(line);
            java.util.regex.Matcher data = DATA.matcher(line);

            if (!command.find() || !data.find()) continue;

            List<Integer> values = new java.util.ArrayList<>();

            java.util.regex.Matcher hex = BYTE.matcher(data.group(1));

            while (hex.find()) values.add(Integer.parseInt(hex.group(1), 16));

            int[] bytes = new int[values.size()];

            for (int i = 0; i < bytes.length; i++) bytes[i] = values.get(i);

            sent.add(new Sent(Integer.parseInt(command.group(1), 16), bytes));
        }

        return sent;
    }

    /**
     * The speed commands sent to one locomotive since a mark: commands, not the questions a sync asks.
     *
     * @param from a position returned by {@link #mark()}
     * @param uid the locomotive's UID (`MarklinLocomotive.getIntUID`)
     * @return the commands, oldest first; {@link Sent#speed()} reads each one's speed
     */
    public List<Sent> speedCommandsSince(int from, int uid)
    {
        return to(from, CS2Message.CMD_LOCO_VELOCITY, uid, 6);
    }

    /**
     * The function commands sent to one locomotive since a mark.
     *
     * @param from a position returned by {@link #mark()}
     * @param uid the locomotive's UID
     * @return the commands; {@link Sent#fifth()} is the function, {@link Sent#sixth()} 1 for on
     */
    public List<Sent> functionCommandsSince(int from, int uid)
    {
        return to(from, CS2Message.CMD_LOCO_FUNCTION, uid, 6);
    }

    /**
     * The direction commands sent to one locomotive since a mark.
     *
     * @param from a position returned by {@link #mark()}
     * @param uid the locomotive's UID
     * @return the commands; {@link Sent#fifth()} is 1 for forward, 2 for backward
     */
    public List<Sent> directionCommandsSince(int from, int uid)
    {
        return to(from, CS2Message.CMD_LOCO_DIRECTION, uid, 5);
    }

    /**
     * The commands sent since a mark to one accessory.
     *
     * @param from a position returned by {@link #mark()}
     * @param uid the accessory's UID (`MarklinAccessory.UIDfromAddress(address - 1, type)`)
     * @return the commands; {@link Sent#fifth()} is the position, 0 for turned (red) and 1 for straight (green)
     */
    public List<Sent> accessoryCommandsSince(int from, int uid)
    {
        return to(from, CS2Message.CMD_ACC_SWITCH, uid, 6);
    }

    /**
     * The system commands of one kind sent since a mark, to the whole railway or to one locomotive.
     *
     * @param from a position returned by {@link #mark()}
     * @param subCommand `CS2Message.CMD_SYSSUB_STOP` (power off), `CMD_SYSSUB_GO` (power on), `CMD_SYSSUB_HALT`
     * (every train stops) or `CMD_SYSSUB_TRAINSTOP` (one train stops)
     * @return the commands; {@link Sent#uid()} is the locomotive's for a train stop
     */
    public List<Sent> systemCommandsSince(int from, int subCommand)
    {
        List<Sent> found = new java.util.ArrayList<>();

        for (Sent s : sentSince(from))
        {
            if (s.command == CS2Message.CMD_SYSTEM && s.fifth() == subCommand) found.add(s);
        }

        return found;
    }

    /** The commands of one kind to one UID that carry at least this many bytes. */
    private List<Sent> to(int from, int command, int uid, int length)
    {
        List<Sent> found = new java.util.ArrayList<>();

        for (Sent s : sentSince(from))
        {
            if (s.command == command && s.uid() == uid && s.length() >= length) found.add(s);
        }

        return found;
    }

    /** "Command: 0x6", as `CS2Message.toString` prints it. */
    private static final java.util.regex.Pattern COMMAND = java.util.regex.Pattern.compile("Command: 0x([0-9a-fA-F]+)");

    /** "Data:   0x00|0x00|...", as `CS2Message.toString` prints it, to the end of its line. */
    private static final java.util.regex.Pattern DATA = java.util.regex.Pattern.compile("Data:([^\r\n]*)");

    /** One byte of it. */
    private static final java.util.regex.Pattern BYTE = java.util.regex.Pattern.compile("0x([0-9a-fA-F]{2})");

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
