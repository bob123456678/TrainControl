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

            final CountDownLatch ticked = watched(driver);

            driver.start();

            assertTrue(ticked.await(5, TimeUnit.SECONDS), "start() after the timer ended left the monitor never ticking");
        }
        finally
        {
            driver.stop();
        }
    }

    /**
     * start() on a timer still running (a load with monitoring on) looks at once, rather than leaving the setup just
     * loaded unshown until a pass that may not come.
     *
     * MUTATION: in `DiagramMonitorDriver.start`, leave out `timer.schedule(aTick(), 0)`, and this fails.
     */
    @Test
    public void testStartOnARunningTimerLooksAtOnce() throws Exception
    {
        DiagramMonitorDriver driver = new DiagramMonitorDriver(null, null);

        try
        {
            // A live timer with nothing scheduled, so only start()'s own look can tick
            field(driver, "timer").set(driver, new Timer("idle", true));

            final CountDownLatch ticked = watched(driver);

            driver.start();

            assertTrue(ticked.await(5, TimeUnit.SECONDS), "start() on a running timer did not look at once");
        }
        finally
        {
            driver.stop();
        }
    }

    /** Hands the driver a dirty monitor whose tick (it asks the monitor's railway) counts the latch down. */
    private static CountDownLatch watched(DiagramMonitorDriver driver) throws ReflectiveOperationException
    {
        final CountDownLatch ticked = new CountDownLatch(1);

        DiagramMonitor monitor = new DiagramMonitor(() ->
        {
            ticked.countDown();
            return null;
        }, new LinkedHashMap<String, ReducedEdge>(), Collections.<String, TileKey>emptyMap(), null);

        field(driver, "monitor").set(driver, monitor);
        monitor.markDirty();

        return ticked;
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
