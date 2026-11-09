package core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CS2Message;

/**
 * An error thrown while a sensor message is handled is written to TrainControl's log, naming the thread, and its stack
 * trace is printed on the console, rather than being lost in a Future nobody reads.
 *
 * MUTATION: in `MarklinControlStation.receiveMessage`, `feedbackMessageProcessor.execute(` to `.submit(`; or drop
 * `thread.setUncaughtExceptionHandler(THREAD_ERRORS)` from `messageProcessor`.
 *
 * @author Adam
 */
public class testASensorMessageFailureIsReported
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();
        model = support.TestStation.simulated();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (sandbox != null) sandbox.close();
    }

    @Test
    public void testAFailureInASensorTaskReachesTheLog() throws Exception
    {
        final CompletableFuture<String> logged = new CompletableFuture<>();

        Logger logger = Logger.getLogger(MarklinControlStation.class.getName());

        Handler watch = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                if (record.getMessage() != null && record.getMessage().contains("cs2-feedback-messages"))
                {
                    logged.complete(record.getMessage());
                }
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        };

        logger.addHandler(watch);

        try
        {
            // A sensor frame with no payload: the task reads the sensor number from it and fails on the message thread.
            model.receiveMessage(new CS2Message(CS2Message.CMD_ACC_SENSOR, 0x7b34, true, new byte[0]));

            assertNotNull(logged.get(30, TimeUnit.SECONDS), "no failure logged");
        }
        catch (java.util.concurrent.TimeoutException none)
        {
            fail("an error thrown while a sensor message was handled never reached the log: submit keeps it in a "
                + "Future nobody reads, and without the thread's handler nothing is written");
        }
        finally
        {
            logger.removeHandler(watch);
        }
    }
}
