package support;

import java.util.Map;
import org.traincontrol.marklin.MarklinRoute;

/**
 * Whether an armed route is watching its sensor yet (FR-136), and cutting a running route's pause short.
 *
 * Arming a route starts its monitor thread, which then has to reach its wait for the sensor.  A sensor the station
 * reports before that is missed, so a claim that fires a route by its sensor first waits until this says yes.
 */
public final class RouteMonitor
{
    private RouteMonitor()
    {
    }

    /**
     * Whether the route's monitor is parked on its sensor, waiting for it to be made: the thread the route names after
     * itself (`MarklinRoute.enable`), waiting inside the occupied wait.
     *
     * @param route the route, or null
     * @return whether its monitor is waiting for the sensor
     */
    public static boolean watching(MarklinRoute route)
    {
        return waitingFor(route, true);
    }

    /** Whether the route's monitor is parked waiting for its sensor to be made (`occupied`) or to clear. */
    public static boolean waitingFor(MarklinRoute route, boolean occupied)
    {
        if (route == null) return false;

        String name = "route monitor " + route.getId() + " " + route.getName();
        String waiting = occupied ? "waitForOccupiedFeedback" : "waitForClearFeedback";

        for (Map.Entry<Thread, StackTraceElement[]> t : Thread.getAllStackTraces().entrySet())
        {
            if (!name.equals(t.getKey().getName()) || t.getKey().getState() != Thread.State.WAITING) continue;

            for (StackTraceElement frame : t.getValue())
            {
                if (waiting.equals(frame.getMethodName())) return true;
            }
        }

        return false;
    }

    /**
     * Cuts short the pause of every route running in this JVM, so a claim that held a route running with a long delay
     * need not wait it out once it has asserted: the interrupted pause ends, and the route finishes as it would.
     */
    public static void endTheRunningPauses()
    {
        for (Map.Entry<Thread, StackTraceElement[]> t : Thread.getAllStackTraces().entrySet())
        {
            for (StackTraceElement frame : t.getValue())
            {
                if (MarklinRoute.class.getName().equals(frame.getClassName())
                    && frame.getMethodName().startsWith("lambda$execRoute$"))
                {
                    t.getKey().interrupt();

                    break;
                }
            }
        }
    }
}
