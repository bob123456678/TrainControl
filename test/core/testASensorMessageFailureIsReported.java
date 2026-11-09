package core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinFeedback;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.model.View;

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
        final int address = 8897;
        final CompletableFuture<Throwable> reported = new CompletableFuture<>();
        final RuntimeException boom = new RuntimeException("sensor boom");

        MarklinFeedback feedback = model.newFeedback(address, null);

        assertNotNull(feedback, "the sensor was not created");

        // A view that fails when the sensor is announced, which parseMessage does on the message thread.
        Object failing = Proxy.newProxyInstance(View.class.getClassLoader(), new Class<?>[] { View.class },
            (proxy, method, args) ->
            {
                if ("feedbackChanged".equals(method.getName())) throw boom;

                Class<?> returns = method.getReturnType();

                if (returns == boolean.class) return false;

                return returns.isPrimitive() ? 0 : null;
            });

        Field view = MarklinControlStation.class.getDeclaredField("view");

        view.setAccessible(true);

        Field modifiers = Field.class.getDeclaredField("modifiers");

        modifiers.setAccessible(true);
        modifiers.setInt(view, view.getModifiers() & ~Modifier.FINAL);

        Object viewWas = view.get(model);

        view.set(model, failing);

        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> reported.complete(failure));

        try
        {
            model.receiveMessage(new CS2Message(CS2Message.CMD_ACC_SENSOR, 0x7b34, true,
                new byte[] {0, 2, (byte) (address >> 8), (byte) address, 0, 1, 0, 0}));

            assertSame(reported.get(30, TimeUnit.SECONDS), boom,
                "an error thrown while a sensor message was handled never reached the uncaught-exception handler: "
                + "submit keeps it in a Future nobody reads, so nothing is printed or logged");
        }
        finally
        {
            view.set(model, viewWas);
        }
    }
}
