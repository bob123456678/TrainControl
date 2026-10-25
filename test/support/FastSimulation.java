package support;

import org.traincontrol.automation.Layout;

/**
 * Takes the operator's pace out of a railway, for a test whose claims are not about it (FR-136).
 *
 * The pace is the railway's Minimum and Maximum Delay: a run waits between them, in whole seconds, at each simulated
 * sensor, before a turn at a terminus and between retries.  A built setup has 1 to 5 by default, so every turn in a test
 * cost up to five seconds that nothing asserted.  Both are instance settings with setters, so a test can change them;
 * with both at zero the railway's own waits fall back to its poll, so nothing spins.
 *
 * The other fixed pauses cannot be set from a test.  `Layout.CONFIGURE_SLEEP`, `COMPLETION_POLL`, `NO_PATH_IDLE_MS`,
 * `STAGING_RETRY_PAUSE`, `HomeStaging.SEARCH_BUDGET_MS`, `Locomotive.FEEDBACK_DURATION_THRESHOLD`,
 * `CS2File.CONNECT_TIMEOUT_MS` and the second after a turn (`delay(1000)`) are compile-time constants or literals:
 * javac writes the number into each use, no bytecode reads the field, and setting it by reflection changes nothing.
 * Making them settable is a production change.
 *
 * Use it with try-with-resources, so the pace is put back:
 *
 *     try (FastSimulation fast = FastSimulation.unpace(railway)) { ... }
 *
 * Not for a test that asserts a wait, a retry or the pace itself.
 */
public final class FastSimulation implements AutoCloseable
{
    private final Layout railway;

    private final int minWas;

    private final int maxWas;

    private FastSimulation(Layout railway)
    {
        this.railway = railway;
        this.minWas = railway.getMinDelay();
        this.maxWas = railway.getMaxDelay();
    }

    /**
     * Sets a railway's Minimum and Maximum Delay to zero.
     *
     * @param railway the built railway
     * @return what puts the pace back when closed
     * @throws Exception from the railway's setters
     */
    public static FastSimulation unpace(Layout railway) throws Exception
    {
        FastSimulation fast = new FastSimulation(railway);

        // the least first: neither setter accepts a value that crosses the other
        railway.setMinDelay(0);
        railway.setMaxDelay(0);

        if (railway.getMinDelay() != 0 || railway.getMaxDelay() != 0)
        {
            throw new IllegalStateException("the railway kept its pace of " + railway.getMinDelay() + " to "
                + railway.getMaxDelay());
        }

        return fast;
    }

    /**
     * Puts the pace back, the greatest first.
     *
     * @throws Exception from the railway's setters
     */
    @Override
    public void close() throws Exception
    {
        railway.setMaxDelay(maxWas);
        railway.setMinDelay(minWas);
    }
}
