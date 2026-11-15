package support;

import org.testng.SkipException;

/** A claim or class that needs a screen. */
public final class Display
{
    private Display()
    {
    }

    /** Skips, with this reason, when the JVM has no display: a window cannot be built there. */
    public static void needed(String why)
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException(why);
    }
}
