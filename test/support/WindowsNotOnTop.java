package support;

import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.Util;

/**
 * Turns off Always on Top in a test run's own preference node, before any test runs, so the windows a run opens stay
 * behind whatever Adam is using (Adam: "The windows popping up on my screen are annoying, so we should either make them
 * not always on top (unless the test is testing that)").  A claim about Always on Top sets the setting itself.
 *
 * The app also puts windows on top in code and can leave them there (a route's confirmation), so it then turns back any
 * window still on top after `STUCK_MS`, unless one of the run's classes is about Always on Top: its source names
 * `AlwaysOnTop`, `ONTOP_SETTING_PREF` or `ON_TOP_KEY`.  The start-up raise is left alone: it is on top for at most
 * two 400 ms tries, hands the flag back itself, and cutting it short leaves the window inactive.
 *
 * The runners name it with TestNG's `-listener` (docs/tools/one.sh, battery.sh).  It does nothing without
 * `traincontrol.preferences`: the node would then be Adam's own.
 */
public class WindowsNotOnTop implements ISuiteListener
{
    /**
     * `TrainControlUI.ONTOP_SETTING_PREF`, built the same way: reading the field itself would start the window class
     * (its look and feel included) in every run, the window-free ones too.
     */
    public static final String ON_TOP_KEY = "OnTop" + Conversion.getFolderHash(10);

    @Override
    public void onStart(ISuite suite)
    {
        String run = System.getProperty(Util.PREFERENCES_PROPERTY);

        if (run == null || run.trim().isEmpty()) return;

        // a class literal, which does not start the window class; the node is copied from Adam's here if it is new
        java.util.prefs.Preferences node = Util.preferencesFor(TrainControlUI.class);

        node.putBoolean(ON_TOP_KEY, false);

        try
        {
            node.flush();
        }
        catch (java.util.prefs.BackingStoreException e)
        {
            throw new IllegalStateException("could not turn off Always on Top in " + node.absolutePath(), e);
        }

        if (!java.awt.GraphicsEnvironment.isHeadless() && !aboutAlwaysOnTop(suite)) keepWindowsOffTop();
    }

    /** Whether any class in the run tests Always on Top, read from its source; unreadable source counts as yes. */
    private static boolean aboutAlwaysOnTop(ISuite suite)
    {
        java.util.Set<Class<?>> classes = new java.util.HashSet<>();

        for (org.testng.ITestNGMethod method : suite.getAllMethods()) classes.add(method.getRealClass());

        for (Class<?> c : classes)
        {
            java.io.File source = new java.io.File("test", c.getName().replace('.', '/') + ".java");

            try
            {
                String text = new String(java.nio.file.Files.readAllBytes(source.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);

                if (text.contains("AlwaysOnTop") || text.contains("ONTOP_SETTING_PREF") || text.contains("ON_TOP_KEY"))
                {
                    return true;
                }
            }
            catch (java.io.IOException | RuntimeException cannot)
            {
                return true;
            }
        }

        return false;
    }

    /** Longer than the start-up raise's two tries. */
    private static final long STUCK_MS = 1500;

    private static void keepWindowsOffTop()
    {
        Thread watching = new Thread(() ->
        {
            java.util.Map<java.awt.Window, Long> onTopSince = new java.util.WeakHashMap<>();

            while (true)
            {
                long now = System.currentTimeMillis();

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!window.isAlwaysOnTop())
                    {
                        onTopSince.remove(window);
                    }
                    else if (now - onTopSince.computeIfAbsent(window, w -> now) >= STUCK_MS)
                    {
                        onTopSince.remove(window);

                        javax.swing.SwingUtilities.invokeLater(() -> window.setAlwaysOnTop(false));
                    }
                }

                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException stop)
                {
                    return;
                }
            }
        }, "windows not on top");

        watching.setDaemon(true);
        watching.start();
    }

    @Override
    public void onFinish(ISuite suite)
    {
    }
}
