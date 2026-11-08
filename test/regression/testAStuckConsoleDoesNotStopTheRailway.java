package regression;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;
import support.Components;
import support.Packets;

/**
 * A console that stops taking output (a selection in a Windows console, a full pipe) does not stop the railway
 * (OB-421): what the Central Station sends is still acted on, a train waiting for a sensor still wakes, and the thread
 * that logs never waits.  Once the console takes output again, the lines come out in order, reading as they always did.
 *
 * The console is the station's own console handler, built while `System.out` is a stream that holds every write
 * while stuck.  Each claim sticks it, acts, and releases it in `finally`; `tearDownClass` releases it too, so a red
 * claim leaves no thread stuck.
 */
public class testAStuckConsoleDoesNotStopTheRailway
{
    /** Sensors and a locomotive of this class only, clear of the fixture's own. */
    private static final int FIRST = 4790;
    private static final int SECOND = 4791;
    private static final int THIRD = 4792;
    private static final int FOURTH = 4793;
    private static final String TRAIN = "CON train";

    /** The short bound everything must happen within while the console is stuck. */
    private static final long BOUND_MS = 3000;

    private static final long CLAIM_MS = 60000;

    private static final Pattern STAMPED = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}) (.*)");

    private static final StuckConsole console = new StuckConsole();

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static MarklinLocomotive train;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        PrintStream out = System.out;

        System.setOut(new PrintStream(console, true));

        try
        {
            model = support.TestStation.simulated();
        }
        finally
        {
            System.setOut(out);
        }

        train = model.getLocByName(TRAIN) != null ? model.getLocByName(TRAIN) : model.newMM2Locomotive(TRAIN, 79);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        console.release();

        try
        {
            if (model != null)
            {
                model.debug(false);

                if (model.getLocByName(TRAIN) != null) model.deleteLoc(TRAIN);

                model.stop();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A sensor change is read, and a train waiting for the sensor wakes, while the console is stuck on the line about
     * the sensor before it; the two lines then come out in order, stamped with the time they were logged.
     *
     * MUTATION: in `QueuedLogHandler.publish`, call `this.target.publish(record)` instead of queueing it.
     */
    @Test(timeOut = CLAIM_MS)
    public void testASensorIsReadWhileTheConsoleIsStuck() throws Exception
    {
        known(FIRST, SECOND);

        final String second = Integer.toString(SECOND);

        Thread waiter = new Thread(() -> train.waitForOccupiedFeedback(second, 0), "a train waiting for " + second);
        waiter.setDaemon(true);

        long releasedAt;

        console.stick();

        try
        {
            model.receiveMessage(Packets.s88(FIRST, true));

            assertTrue(console.awaitStuck(BOUND_MS), "precondition: nothing wrote to the console, so it was never stuck");

            waiter.start();

            model.receiveMessage(Packets.s88(SECOND, true));

            assertTrue(Components.waitFor(() -> model.getFeedbackState(second), BOUND_MS), "the station said sensor "
                + SECOND + " is made while the console was stuck, and the model still reads it clear: the sensor"
                + " thread is waiting on the console, and a train runs past its sensor");

            waiter.join(BOUND_MS);

            assertFalse(waiter.isAlive(), "a train waiting for sensor " + SECOND + " did not wake while the console was"
                + " stuck");

            // So that a line stamped when it is written, not when it was logged, is told apart
            Thread.sleep(50);

            releasedAt = System.currentTimeMillis();
        }
        finally
        {
            console.release();
        }

        List<String> lines = console.linesOnceFlushed(stateLine(SECOND));

        int first = indexOf(lines, stateLine(FIRST));
        int then = indexOf(lines, stateLine(SECOND));

        assertTrue(first >= 0 && then >= 0, "the lines about the two sensors did not reach the console once it took"
            + " output again: " + lines);

        assertTrue(first < then, "the console's lines are out of order: " + lines);

        // MUTATION: in the station's console formatter, `record.getMillis()` to `System.currentTimeMillis()`.
        assertTrue(stampOf(lines.get(first)) < releasedAt, "the line about sensor " + FIRST + " is stamped "
            + lines.get(first) + ", after the console was released at "
            + new SimpleDateFormat("HH:mm:ss.SSS").format(releasedAt) + ": the time is the write's, not the log's");
    }

    /**
     * In debug, the thread that hands the station's frames in returns at once while the console is stuck, though it
     * logs every frame, and the frames are acted on; then every frame's line and every sensor's line comes out.
     *
     * MUTATION: in `QueuedLogHandler.publish`, call `this.target.publish(record)` instead of queueing it.
     */
    @Test(timeOut = CLAIM_MS)
    public void testAFrameIsTakenInWhileTheConsoleIsStuckInDebug() throws Exception
    {
        known(THIRD, FOURTH);

        final CS2Message third = Packets.s88(THIRD, true);
        final CS2Message fourth = Packets.s88(FOURTH, true);

        Thread receiver = new Thread(() ->
        {
            model.receiveMessage(third);
            model.receiveMessage(fourth);
        }, "a CAN listener");

        receiver.setDaemon(true);

        model.debug(true);

        console.stick();

        try
        {
            receiver.start();

            assertTrue(console.awaitStuck(BOUND_MS), "precondition: nothing wrote to the console, so it was never stuck");

            receiver.join(BOUND_MS);

            assertFalse(receiver.isAlive(), "in debug, the thread taking the station's frames in is waiting on the"
                + " console, so nothing else the station sends is heard");

            assertTrue(Components.waitFor(() -> model.getFeedbackState(Integer.toString(FOURTH)), BOUND_MS),
                "the station said sensor " + FOURTH + " is made while the console was stuck, and the model still reads"
                + " it clear");
        }
        finally
        {
            console.release();

            model.debug(false);
        }

        String text = String.join("\n", console.linesOnceFlushed(stateLine(FOURTH)));

        for (CS2Message frame : Arrays.asList(third, fourth))
        {
            assertTrue(text.contains(frame.toString()), "a frame's debug line did not reach the console as it reads: "
                + frame);
        }

        assertTrue(text.indexOf(stateLine(THIRD)) >= 0 && text.indexOf(stateLine(THIRD)) < text.indexOf(stateLine(FOURTH)),
            "the lines about the two sensors are missing or out of order: " + text);
    }

    /**
     * A console stuck for longer than the queue holds drops the newest lines rather than make the thread that logs
     * wait; what was kept comes out in order, then one line saying how many were left out.
     *
     * MUTATION: in `QueuedLogHandler.publish`, `this.queue.offer(...)` to `this.queue.put(...)` (an overflow waits).
     */
    @Test(timeOut = CLAIM_MS)
    public void testAFullQueueDropsLinesRatherThanWait() throws Exception
    {
        final int many = org.traincontrol.util.QueuedLogHandler.CAPACITY + 50;

        Thread logging = new Thread(() ->
        {
            model.log("CON first");

            for (int i = 0; i < many; i++) model.log("CON line " + i);
        }, "a thread that logs");

        logging.setDaemon(true);

        console.stick();

        try
        {
            logging.start();

            assertTrue(console.awaitStuck(BOUND_MS), "precondition: nothing wrote to the console, so it was never stuck");

            logging.join(BOUND_MS);

            assertFalse(logging.isAlive(), "with the console stuck and its queue full, the thread that logs waits");
        }
        finally
        {
            console.release();
        }

        // Any count: which lines were kept is read from the console
        String[] template = I18n.f("log.consoleLinesDropped", "\u0001").split("\u0001");

        List<String> lines = console.linesOnceFlushed(line -> STAMPED.matcher(line).matches()
            && line.endsWith(template[template.length - 1]) && line.contains(template[0]));

        List<String> said = new ArrayList<>();

        for (String line : lines)
        {
            Matcher m = STAMPED.matcher(line);

            if (m.matches()) said.add(m.group(2));
        }

        Set<String> written = new HashSet<>(said);

        int kept = 0;

        while (written.contains("CON line " + kept)) kept++;

        assertTrue(kept > 0 && kept < many, "precondition: " + kept + " of " + many + " lines were kept, so none were"
            + " dropped");

        for (int i = kept; i < many; i++)
        {
            assertFalse(written.contains("CON line " + i), "line " + i + " was written after line " + kept + " was"
                + " dropped: the queue kept an older line's place for a newer one");
        }

        int last = said.indexOf("CON line " + (kept - 1));

        assertTrue(said.indexOf("CON first") < said.indexOf("CON line 0") && said.indexOf("CON line 0") < last,
            "the kept lines are out of order");

        String dropped = I18n.f("log.consoleLinesDropped", many - kept);

        int note = said.indexOf(dropped);

        assertTrue(note > last, "no line after the last kept one says \"" + dropped + "\"");

        assertEquals(said.lastIndexOf(dropped), note, "the dropped lines were reported twice");
    }

    // ---------------------------------------------------------------- the railway and the console

    /** The sensors, reported clear and known to the model, and the console's earlier lines written and forgotten. */
    private static void known(int... sensors) throws Exception
    {
        for (int sensor : sensors)
        {
            model.receiveMessage(Packets.s88(sensor, false));

            assertTrue(Components.waitFor(() -> model.isFeedbackSet(Integer.toString(sensor))
                && !model.getFeedbackState(Integer.toString(sensor)), BOUND_MS),
                "precondition: sensor " + sensor + " is not known, or not clear");
        }

        console.linesOnceFlushed(stateLine(sensors[sensors.length - 1]).replace(I18n.t("acc.stateSet"),
            I18n.t("acc.stateNotSet")));

        console.clear();
    }

    /** The log's line about a sensor made, as `MarklinFeedback.parseMessage` says it. */
    private static String stateLine(int sensor)
    {
        return I18n.f("acc.feedbackState", Integer.toString(sensor), "", I18n.t("acc.stateSet"));
    }

    /** Where the console's line saying this text is, or -1. */
    private static int indexOf(List<String> lines, String text)
    {
        for (int i = 0; i < lines.size(); i++)
        {
            Matcher m = STAMPED.matcher(lines.get(i));

            if (m.matches() && m.group(2).equals(text)) return i;
        }

        return -1;
    }

    private static long stampOf(String line) throws Exception
    {
        Matcher m = STAMPED.matcher(line);

        assertTrue(m.matches(), "a console line is not stamped as the station stamps it: " + line);

        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").parse(m.group(1)).getTime();
    }

    /** A console that, once stuck, holds every write until released, as a console with a selection in it does. */
    private static final class StuckConsole extends OutputStream
    {
        private final ByteArrayOutputStream kept = new ByteArrayOutputStream();

        private volatile CountDownLatch gate = new CountDownLatch(0);

        private volatile CountDownLatch entered = new CountDownLatch(1);

        void stick()
        {
            this.entered = new CountDownLatch(1);
            this.gate = new CountDownLatch(1);
        }

        void release()
        {
            this.gate.countDown();
        }

        boolean awaitStuck(long ms) throws InterruptedException
        {
            return this.entered.await(ms, TimeUnit.MILLISECONDS);
        }

        void clear()
        {
            synchronized (this.kept)
            {
                this.kept.reset();
            }
        }

        /**
         * The lines written, once the last expected one is there: each flush is the queue's own drain, bounded, so a
         * long backlog takes several.
         */
        List<String> linesOnceFlushed(String last)
        {
            return linesOnceFlushed(line -> line.endsWith(last));
        }

        List<String> linesOnceFlushed(Predicate<String> last)
        {
            for (int turn = 0; turn < 10; turn++)
            {
                for (Handler h : Logger.getLogger(MarklinControlStation.class.getName()).getHandlers()) h.flush();

                if (lines().stream().anyMatch(last)) break;
            }

            return lines();
        }

        List<String> lines()
        {
            synchronized (this.kept)
            {
                return Arrays.asList(new String(this.kept.toByteArray(), Charset.defaultCharset()).split("\\r?\\n"));
            }
        }

        @Override
        public void write(int b)
        {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len)
        {
            CountDownLatch holding = this.gate;

            if (holding.getCount() > 0)
            {
                this.entered.countDown();

                try
                {
                    // Bounded, so that no claim can leave a JVM stuck for good
                    holding.await(CLAIM_MS, TimeUnit.MILLISECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            synchronized (this.kept)
            {
                this.kept.write(b, off, len);
            }
        }
    }
}
