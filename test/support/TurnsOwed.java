package support;

import javax.swing.SwingUtilities;
import org.traincontrol.gui.TrainControlUI;

/** Has the main window write the turns it owes the setup, on the event thread, as its own doors do.  Not a test. */
public final class TurnsOwed
{
    private TurnsOwed()
    {
    }

    /** `DiagramLocGestures.writeTheTurnsOwed` on the window's collaborator, waited for. */
    public static void write(TrainControlUI ui) throws Exception
    {
        final Object gestures = Reflect.get(TrainControlUI.class, ui, "diagramLocGestures");
        final java.lang.reflect.Method write = Reflect.method(gestures.getClass(), "writeTheTurnsOwed");

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                write.invoke(gestures);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });
    }
}
