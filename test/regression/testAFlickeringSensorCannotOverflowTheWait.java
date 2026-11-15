package regression;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * A sensor that keeps flickering restarts a debounced wait each time; a restart is a turn of a loop, not a deeper call,
 * so a route monitor or a journey on a small stack lives through thousands of flickers and still returns once the
 * sensor holds (OB-422).  StackOverflowError is not an Exception, so a thread that hit it died with its route still
 * shown enabled, or with its train running.
 */
public class testAFlickeringSensorCannotOverflowTheWait
{
    /** Failed debounces per wait: a 256 KB stack held about 1,000 restarts as calls. */
    private static final int FLICKERS = 5000;

    /** A sensor read in the held state when the wait looks and in the other when its debounce checks, then held. */
    private static final class Flicker extends MarklinLocomotive
    {
        final AtomicInteger reads = new AtomicInteger();

        private final boolean held;

        Flicker(boolean held)
        {
            super(null, 2, MarklinLocomotive.decoderType.MM2, "Flicker");
            this.held = held;
        }

        @Override
        public boolean isFeedbackSet(String name)
        {
            return true;
        }

        @Override
        public boolean getFeedbackState(String name)
        {
            int n = this.reads.getAndIncrement();
            return (n >= 2 * FLICKERS || n % 2 == 0) == this.held;
        }

        // The restarts are what is counted, not the debounce's time
        @Override
        public Locomotive delay(long ms)
        {
            return this;
        }
    }

    /** Runs the wait on a 256 KB stack: it returns, with every flicker read and nothing thrown. */
    private static void outlives(boolean held, Function<Flicker, Locomotive> wait) throws Exception
    {
        Flicker loc = new Flicker(held);
        AtomicReference<Throwable> died = new AtomicReference<>();
        AtomicBoolean returned = new AtomicBoolean();

        Thread waiting = new Thread(null, () ->
        {
            try
            {
                wait.apply(loc);
                returned.set(true);
            }
            catch (Throwable t)
            {
                died.set(t);
            }
        }, "flickering-wait", 256 * 1024);

        waiting.setDaemon(true);
        waiting.start();
        waiting.join(60000);

        assertNull(died.get(), "the wait died after " + loc.reads.get() / 2 + " flickers");
        assertTrue(returned.get(), "the wait did not return once the sensor held");
        assertEquals(loc.reads.get(), 2 * FLICKERS + 2, "the wait did not read the sensor through every flicker");
    }

    /**
     * MUTATION: in `Locomotive.waitForOccupiedFeedback`, the `continue;` to
     * `return this.waitForOccupiedFeedback(name, minDuration, adviseAfterMs, abandon);`.
     */
    @Test(timeOut = 120000)
    public void testTheOccupiedWaitOutlivesAFlickeringSensor() throws Exception
    {
        outlives(true, loc -> loc.waitForOccupiedFeedback("1", 1));
    }

    /**
     * MUTATION: in `Locomotive.waitForClearFeedback`, the `continue;` to
     * `return this.waitForClearFeedback(name, minDuration);`.
     */
    @Test(timeOut = 120000)
    public void testTheClearWaitOutlivesAFlickeringSensor() throws Exception
    {
        outlives(false, loc -> loc.waitForClearFeedback("1", 1));
    }
}
