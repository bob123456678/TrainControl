package support;

import org.traincontrol.automation.Layout;

/**
 * Takes the operator's pace out of a railway (its Minimum and Maximum Delay), for a test whose claims are not about it
 * (FR-136).  The other fixed pauses (`Layout.CONFIGURE_SLEEP` and the like) are compile-time constants and cannot be
 * set from a test.
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
     */
    @Override
    public void close() throws Exception
    {
        railway.setMaxDelay(maxWas);
        railway.setMinDelay(minWas);
    }
}
