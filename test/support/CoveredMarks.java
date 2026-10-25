package support;

import java.lang.reflect.Method;
import org.traincontrol.gui.TrainControlUI;

/**
 * Asking the window to recompute the marks a standing train puts on the diagram, and WAITING for the answer (OB-192).
 *
 * `TrainControlUI.refreshCoveredTrack` is private, and every test of the orange line or the gray wash calls it by
 * reflection to get at the recompute the window makes on every refresh of a running layout.  Since OB-192 the work is
 * on a worker, so the answer lands slightly later than the ask and a test that reads `coveredTrack` straight afterwards
 * reads whatever was there before.  The wait lives here once rather than in each of the five classes: forgotten at one
 * call site, a class does not fail, it passes or fails on the state before the gesture.
 *
 * @author Adam
 */
public final class CoveredMarks
{
    /**
     * How long a refresh is given to land; generous, so a worker that has died reports as a stuck refresh and not as a
     * hang.
     */
    public static final long PATIENCE_MS = 60000;

    private CoveredMarks()
    {
    }

    /** Recomputes the covered and blocked sets and waits until the window has them. */
    public static void refresh(TrainControlUI ui) throws Exception
    {
        Method refresh = TrainControlUI.class.getDeclaredMethod("refreshCoveredTrack");

        refresh.setAccessible(true);
        refresh.invoke(ui);

        settle(ui);
    }

    /**
     * WAITS for a refresh somebody else asked for, without asking for one: the wait half of `refresh`, for the tests
     * that drive a production DOOR.  They must NOT call `refresh`, which invokes the window's own recompute and so
     * would prove only that the recompute works, not that the door asked for it.
     */
    public static void settle(TrainControlUI ui) throws Exception
    {
        Method settled = TrainControlUI.class.getDeclaredMethod("awaitCoveredTrack", long.class);

        settled.setAccessible(true);

        if (!Boolean.TRUE.equals(settled.invoke(ui, PATIENCE_MS)))
        {
            throw new IllegalStateException("the window did not finish working out the covered and"
                + " blocked squares within " + PATIENCE_MS + "ms, so everything asked of it below is"
                + " about the state BEFORE this refresh");
        }
    }
}
