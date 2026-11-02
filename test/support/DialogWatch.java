package support;

import java.awt.Window;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JDialog;

/**
 * The loop of a thread that watches for the program's dialogs and answers them by rules of its own (`support.Answering`
 * is the whole answerer, for a test that needs no rules of its own).
 */
public final class DialogWatch
{
    private DialogWatch()
    {
    }

    /**
     * While `going` holds: sleeps `everyMs`, then hands each showing `JDialog` to `look`, on the calling thread.  Returns
     * once `going` fails or the sleep is interrupted; what `look` throws ends it.  A dialog seen again on the next look
     * is handed over again, so `look` keeps its own record of those it answered.
     */
    public static void until(BooleanSupplier going, long everyMs, Consumer<JDialog> look)
    {
        watch(going, everyMs, false, look);
    }

    /** The same, asking `going` again after each sleep, so a watcher told to stop while asleep looks no more. */
    public static void untilStillGoing(BooleanSupplier going, long everyMs, Consumer<JDialog> look)
    {
        watch(going, everyMs, true, look);
    }

    private static void watch(BooleanSupplier going, long everyMs, boolean askAfterTheSleep, Consumer<JDialog> look)
    {
        while (going.getAsBoolean())
        {
            try
            {
                Thread.sleep(everyMs);
            }
            catch (InterruptedException stop)
            {
                return;
            }

            if (askAfterTheSleep && !going.getAsBoolean()) return;

            for (Window window : Window.getWindows())
            {
                if (!window.isShowing() || !(window instanceof JDialog)) continue;

                look.accept((JDialog) window);
            }
        }
    }
}
