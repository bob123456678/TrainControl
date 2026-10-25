package support;

import java.util.function.BooleanSupplier;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Locomotive;

/**
 * A simulated run waited out by the railway's own state, never by a sleep of a guessed length.
 *
 * The railway is idle when `Layout.isRunning()` says so: autonomy's flag is down, no train holds a path, no journey thread
 * is choosing one and no timetable entry is pending.  That is the question the window asks before it gives Start back, so
 * a test that waits on it waits exactly as long as the program would.  Then the event thread is let finish what the end
 * of the run posted to it (`Components.settle`).
 *
 * A run to completion, as Graceful Stop gives one:
 *
 *     Settle.simulate(layout);
 *     layout.runLocomotives();
 *     assertTrue(Settle.journeys(train, 2, Settle.PATIENCE_MS), "the train made fewer than two journeys");
 *     assertTrue(Settle.gracefully(layout, Settle.PATIENCE_MS), "the run never came to rest");
 *
 * The private `settle` methods of the suite (fixed sleeps, or a few turns of the event thread) can be folded onto
 * {@link #idle} where they wait for a run, and onto `Components.settle` where they wait for the window.
 */
public final class Settle
{
    /**
     * How long a simulated run is given at most, in milliseconds: a ceiling, not a pause, as every wait here ends the
     * moment its condition holds.
     */
    public static final long PATIENCE_MS = 120000;

    /** How often a condition is asked, in milliseconds. */
    private static final long ASK_EVERY_MS = 20;

    private Settle()
    {
    }

    /**
     * Makes a railway simulate its trains with no pauses: each sensor on a train's way is set as it reaches it and
     * cleared behind it, so a journey ends in a few milliseconds.  Refused by the railway while it runs, and outside
     * debug mode.
     */
    public static void simulate(Layout layout) throws Exception
    {
        // The minimum first: the railway refuses a maximum below it
        layout.setMinDelay(0);
        layout.setMaxDelay(0);
        layout.setSimulate(true);
    }

    /**
     * Waits until the railway is idle by its own word, then lets the event thread finish; says whether it came to rest
     * in time.
     */
    public static boolean idle(final Layout layout, long atMostMs)
    {
        boolean rested = until(() -> !layout.isRunning(), atMostMs);

        Components.settle();

        return rested;
    }

    /**
     * Stops the run as Graceful Stop does - no train is sent again, and each one under way finishes its journey - and
     * waits until the railway is idle.
     */
    public static boolean gracefully(Layout layout, long atMostMs)
    {
        layout.stopLocomotives();

        return idle(layout, atMostMs);
    }

    /**
     * Waits until a train has finished at least this many journeys in all, by the count the railway keeps
     * (`Locomotive.getNumPaths`), which it moves once the journey's track is given back.
     */
    public static boolean journeys(final Locomotive train, final int total, long atMostMs)
    {
        return until(() -> train.getNumPaths() >= total, atMostMs);
    }

    /** Waits until a train is under way: the railway holds a path for it. */
    public static boolean underWay(final Layout layout, final Locomotive train, long atMostMs)
    {
        return until(() -> layout.getActiveLocomotives().containsKey(train), atMostMs);
    }

    /** Waits for a condition of the program's state, asking it every 20 ms; says whether it came true in time. */
    public static boolean until(BooleanSupplier condition, long atMostMs)
    {
        long end = System.currentTimeMillis() + atMostMs;

        while (!condition.getAsBoolean())
        {
            if (System.currentTimeMillis() >= end) return condition.getAsBoolean();

            try
            {
                Thread.sleep(ASK_EVERY_MS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return condition.getAsBoolean();
            }
        }

        return true;
    }
}
