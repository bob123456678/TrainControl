package support;

import java.lang.reflect.Method;
import org.traincontrol.gui.TrainControlUI;

/**
 * Asking the window to recompute the marks a standing train puts on the diagram, and WAITING for the
 * answer (OB-192).
 *
 * `TrainControlUI.refreshCoveredTrack` is private, because nothing outside that window has any business
 * recomputing the set, and every test of the orange line or the grey wash calls it by reflection to get
 * at the recompute the window itself makes on every refresh of a running layout.
 *
 * **Since OB-192 asking and reading are two moments.**  That refresh used to be done on the calling
 * thread, and on the event thread it took the `Layout` monitor - which a dispatch holds for the whole
 * of `configureAndLockPath` - so the window froze the moment autonomy started while the trains carried
 * on.  The work moved to a worker; the answer therefore lands slightly later than the ask, and a test
 * that reads `coveredTrack` straight afterwards is reading whatever was there before.
 *
 * **Here, once, rather than in each of the five classes that do this.**  The wait is the sort of thing
 * that gets pasted at four call sites and forgotten at the fifth, and a class that forgets it does not
 * fail - it reads the previous answer and passes or fails on the state before the gesture, which is
 * the least useful kind of red there is.
 *
 * @author Adam
 */
public final class CoveredMarks
{
    /**
     * How long a refresh is given to land.  Generous: the point of a bound is that a worker which has
     * died reports as a stuck refresh rather than as a test that hangs for ever.
     */
    public static final long PATIENCE_MS = 60000;

    private CoveredMarks()
    {
    }

    /**
     * Recomputes the covered and blocked sets and waits until the window has them.
     *
     * @param ui the window
     * @throws Exception on a reflection failure, or if the refresh never landed
     */
    public static void refresh(TrainControlUI ui) throws Exception
    {
        Method refresh = TrainControlUI.class.getDeclaredMethod("refreshCoveredTrack");

        refresh.setAccessible(true);
        refresh.invoke(ui);

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
