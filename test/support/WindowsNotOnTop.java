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
    }

    @Override
    public void onFinish(ISuite suite)
    {
    }
}
