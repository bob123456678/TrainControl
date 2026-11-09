package core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.assertNotNull;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CS2Message;

/**
 * An error thrown while a sensor message is handled reaches the thread's uncaught-exception handler, so it is
 * printed and logged rather than lost in a Future nobody reads.
 *
 * MUTATION: in `MarklinControlStation.receiveMessage`, `feedbackMessageProcessor.execute(` to `.submit(`.
 *
 * @author Adam
 */
public class testASensorMessageFailureIsReported
{
    private static support.LayoutSandbox sandbox;
    private static Thread.UncaughtExceptionHandler handlerWas;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();
        handlerWas = Thread.getDefaultUncaughtExceptionHandler();
        model = support.TestStation.simulated();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        Thread.setDefaultUncaughtExceptionHandler(handlerWas);

        if (sandbox != null) sandbox.close();
    }

    @Test
    public void testAFailureInASensorTaskReachesTheHandler() throws Exception
    {
        final CompletableFuture<Throwable> reported = new CompletableFuture<>();

        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> reported.complete(failure));

        // A sensor frame with no payload: the task reads the sensor number from it and fails on the message thread.
        model.receiveMessage(new CS2Message(CS2Message.CMD_ACC_SENSOR, 0x7b34, true, new byte[0]));

        assertNotNull(reported.get(30, TimeUnit.SECONDS),
            "an error thrown while a sensor message was handled never reached the uncaught-exception handler: "
            + "submit keeps it in a Future nobody reads, so nothing is printed or logged");
    }
}
