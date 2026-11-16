import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;
import org.traincontrol.util.QueuedLogHandler;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A console that stops taking output does not hold up the railway (OB-421).
 *
 * Every sensor change is logged on the thread that reads sensors, and the log went straight to the console.  A
 * command window that stopped taking output (a click into it starts a selection, which pauses the program's writes)
 * then held every later sensor change, and trains ran past their stops.
 */
public class testAStuckConsoleDoesNotStopTheRailway
{
    private static MarklinControlStation model;
    private static PrintStream realOut;

    private static final Object gate = new Object();

    /** Guarded by gate. */
    private static boolean stuck;

    /** The console: passes output through, except that a write waits while it is stuck. */
    private static class Console extends OutputStream
    {
        @Override
        public void write(int b) throws IOException
        {
            waitWhileStuck();
            realOut.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException
        {
            waitWhileStuck();
            realOut.write(b, off, len);
        }

        @Override
        public void flush()
        {
            realOut.flush();
        }
    }

    private static void waitWhileStuck()
    {
        synchronized (gate)
        {
            while (stuck)
            {
                try
                {
                    gate.wait();
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void setStuck(boolean value)
    {
        synchronized (gate)
        {
            stuck = value;
            gate.notifyAll();
        }
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The station's log writes to whatever System.out is when it is built
        realOut = System.out;
        System.setOut(new PrintStream(new Console(), true));

        model = init(null, true, false, false, false);
    }

    @AfterClass
    public static void tearDownClass()
    {
        setStuck(false);
        System.setOut(realOut);
    }

    /** A sensor frame for contact 1911 (0x0777), occupied or free. */
    private static CS2Message sensor(boolean occupied)
    {
        return new CS2Message(CS2Message.CMD_ACC_SENSOR, 0x4713, true,
            new byte[]{ 0, 0, 0x07, 0x77, 0, (byte) (occupied ? 1 : 0), 0, 0 });
    }

    private static boolean waitFor(boolean occupied) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + 3000;

        while (System.currentTimeMillis() < deadline)
        {
            if (model.isFeedbackSet("1911") && model.getFeedbackState("1911") == occupied) return true;

            Thread.sleep(20);
        }

        return false;
    }

    /**
     * Sensor changes still land while the console is stuck.  The first change is logged into the stuck console; the
     * second must land all the same.
     */
    @Test
    public void testASensorChangeLandsWhileTheConsoleIsStuck() throws Exception
    {
        model.receiveMessage(sensor(true));
        assertTrue(waitFor(true), "precondition: a sensor event creates its sensor, occupied");

        setStuck(true);

        try
        {
            model.receiveMessage(sensor(false));
            assertTrue(waitFor(false), "the first change lands before its log line is written");

            model.receiveMessage(sensor(true));
            assertTrue(waitFor(true), "a sensor change waited for a console that was not taking output: a train "
                + "waiting on this sensor would run past its stop");
        }
        finally
        {
            setStuck(false);
        }
    }

    /**
     * A full queue drops lines rather than wait, says how many it dropped, and keeps the rest in order.
     */
    @Test
    public void testAFullQueueDropsRatherThanWaitsAndSaysHowMany() throws Exception
    {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> written = Collections.synchronizedList(new ArrayList<>());

        QueuedLogHandler queue = new QueuedLogHandler(new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                try
                {
                    entered.countDown();
                    release.await();
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }

                written.add(record.getMessage());
            }

            @Override public void flush() { }
            @Override public void close() { }
        });

        // The first line sticks in the console; then the queue fills, and ten more find no room
        queue.publish(new LogRecord(Level.INFO, "line 0"));
        entered.await();

        int kept = QueuedLogHandler.CAPACITY + 1;
        long start = System.nanoTime();

        for (int i = 1; i < kept + 10; i++)
        {
            queue.publish(new LogRecord(Level.INFO, "line " + i));
        }

        long tookMs = (System.nanoTime() - start) / 1000000;

        // The console takes output again: what was kept is written, then a line published after that
        release.countDown();
        queue.flush();
        queue.publish(new LogRecord(Level.INFO, "after"));
        queue.flush();

        assertTrue(tookMs < 2000, "publishing to a stuck console took " + tookMs + " ms: it must not wait");
        assertEquals(written.size(), kept + 2, "every line kept, one line counting the dropped, and the line after");

        for (int i = 0; i < kept; i++)
        {
            assertEquals(written.get(i), "line " + i, "the lines kept must stay in the order they were published");
        }

        assertEquals(written.get(kept), I18n.f("log.consoleLinesDropped", 10L), "the log must say how many were dropped");
        assertEquals(written.get(kept + 1), "after", "a line published once there is room is written");

        queue.close();
    }
}
