package support;

import java.awt.Window;
import javax.swing.SwingUtilities;
import org.traincontrol.marklin.MarklinControlStation;

/** The end of a class that built windows over a model in its own sandbox. */
public final class Teardown
{
    private Teardown()
    {
    }

    /**
     * Disposes each window on the event thread in the order given and stops the model, skipping what is null; the
     * sandbox is closed whatever those do.
     */
    public static void close(LayoutSandbox sandbox, MarklinControlStation model, Window... windows) throws Exception
    {
        try
        {
            for (Window window : windows)
            {
                if (window != null) SwingUtilities.invokeAndWait(window::dispose);
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }
}
