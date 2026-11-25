package org.traincontrol.gui.autonomy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.DiagramMonitor;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * `DiagramMonitorDriver`'s timer on its own: no window, no railway.  Its integration test is
 * `regression.testTheOverlayBoxDuringARun`.
 */
public class testDiagramMonitorDriver
{
    /**
     * After an Error has ended the timer's thread, start() (as a load calls it) gets the ticks running again: the dead
     * timer refuses every task, and the diagram would stop following the trains for good.
     *
     * MUTATION: in `DiagramMonitorDriver.start`, return whenever `timer` is set, and this fails.
     */
    @Test
    public void testStartReplacesATimerAnErrorEnded() throws Exception
    {
        DiagramMonitorDriver driver = new DiagramMonitorDriver(null, null);

        try
        {
            driver.start();

            Timer first = (Timer) field(driver, "timer").get(driver);

            first.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    throw new AssertionError("ends the timer's thread, as any Error out of a tick does");
                }
            }, 0);

            assertTrue(support.Components.waitFor(() -> refusesTasks(first), 10000),
                "precondition: the Error did not end the timer");

            // A tick asks the monitor's railway; that asking is what is watched for
            final CountDownLatch ticked = new CountDownLatch(1);

            DiagramMonitor monitor = new DiagramMonitor(() ->
            {
                ticked.countDown();
                return null;
            }, new LinkedHashMap<String, ReducedEdge>(), Collections.<String, TileKey>emptyMap(), null);

            field(driver, "monitor").set(driver, monitor);
            monitor.markDirty();

            driver.start();

            assertTrue(ticked.await(5, TimeUnit.SECONDS), "start() after the timer ended left the monitor never ticking");
        }
        finally
        {
            driver.stop();
        }
    }

    private static java.lang.reflect.Field field(DiagramMonitorDriver driver, String name)
        throws ReflectiveOperationException
    {
        java.lang.reflect.Field field = DiagramMonitorDriver.class.getDeclaredField(name);

        field.setAccessible(true);

        return field;
    }

    private static boolean refusesTasks(Timer timer)
    {
        try
        {
            timer.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                }
            }, 60000);

            return false;
        }
        catch (IllegalStateException dead)
        {
            return true;
        }
    }
}
