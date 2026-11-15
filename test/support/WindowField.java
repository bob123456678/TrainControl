package support;

import java.lang.reflect.Field;
import org.traincontrol.gui.TrainControlUI;

/** Reads a private field of the main window, for a test to press or read what the operator sees. */
public final class WindowField
{
    private WindowField()
    {
    }

    /** The value of the named field of {@link TrainControlUI}. */
    @SuppressWarnings("unchecked")
    public static <T> T of(TrainControlUI ui, String name)
    {
        try
        {
            Field f = TrainControlUI.class.getDeclaredField(name);

            f.setAccessible(true);

            return (T) f.get(ui);
        }
        catch (ReflectiveOperationException missing)
        {
            throw new AssertionError(name + " is not on the window: " + missing, missing);
        }
    }
}
