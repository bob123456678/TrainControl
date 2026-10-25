package support;

import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.Util;

/**
 * Turns off the window's check for a new release in a test run's own preference node, before any test runs.
 *
 * A window asks api.github.com for the latest release at start-up when Check for Updates is ticked, and the run's node
 * starts as a copy of Adam's, where it is.  Battery 104 asked 2,279 times from 86 classes; GitHub refuses past 60 an
 * hour, and no test is about that check (the update tests serve their own release).
 *
 * The runners name it with TestNG's `-listener` (docs/tools/one.sh, battery.sh).  It does nothing without
 * `traincontrol.preferences`: the node would then be Adam's own.
 */
public class NoUpdateCheck implements ISuiteListener
{
    @Override
    public void onStart(ISuite suite)
    {
        String run = System.getProperty(Util.PREFERENCES_PROPERTY);

        if (run == null || run.trim().isEmpty()) return;

        // a class literal, which does not start the window class; the node is copied from Adam's here if it is new
        java.util.prefs.Preferences node = Util.preferencesFor(TrainControlUI.class);

        node.putBoolean(TrainControlUI.CHECK_FOR_UPDATES, false);

        try
        {
            node.flush();
        }
        catch (java.util.prefs.BackingStoreException e)
        {
            throw new IllegalStateException("could not turn off the update check in " + node.absolutePath(), e);
        }
    }

    @Override
    public void onFinish(ISuite suite)
    {
    }
}
