package regression;

import java.io.File;
import java.util.prefs.Preferences;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.Util;

/**
 * A test run reads and writes a preference node and a data folder of its own, never the ones Adam's running
 * application uses.
 *
 * Adam, 2026-09-23: *"does my app really have to stay closed? I thought the battery got isolated earlier."*  It had
 * not been: every test JVM used the working directory's `LocDB.data` and `UIState.data` and the machine-wide
 * preference node, and `LayoutSandbox` points that node's layout path at a sandbox for the length of a class.
 * `docs/tools/one.sh` and `battery.sh` now copy the two files into a folder of the run's own and name a node of its
 * own, through two system properties; this asks the application whether it listened.
 *
 * **Only under those runners.**  NetBeans starts its test JVMs without the properties, so there the application uses
 * what it always used, and this says so as a skip rather than passing.
 *
 * MUTATION: have `Util.preferencesFor` ignore its property and the first claim fails; `Util.dataPath` ignore its
 * property and the second does.
 *
 * @author Adam
 */
public class testARunKeepsItsOwnState
{
    /**
     * The preference node is the run's own, and what is written there does not reach the real one.
     *
     * @throws Exception from the preference store
     */
    @Test
    public void testThePreferencesAreTheRunsOwn() throws Exception
    {
        String run = runnerOnly(Util.PREFERENCES_PROPERTY);

        Preferences used = TrainControlUI.getPrefs();

        assertTrue(used.absolutePath().startsWith("/" + Util.ISOLATED_PREFERENCES_ROOT + "/" + run + "/"), "the run named"
            + " its own preference node, " + run + ", and the application uses " + used.absolutePath() + " - the node"
            + " Adam's running application reads and writes");

        // AND A WRITE STAYS THERE.  The real node is only read.
        Preferences real = Preferences.userNodeForPackage(TrainControlUI.class);

        String key = "isolationProbe";
        String before = real.get(key, null);

        used.put(key, "written by " + run);
        used.flush();

        try
        {
            assertEquals(real.get(key, null), before, "a preference this run wrote reached the real node");
        }
        finally
        {
            used.remove(key);
        }
    }

    /**
     * The data files are read from, and written to, the run's own folder.
     */
    @Test
    public void testTheDataFilesAreTheRunsOwn() throws Exception
    {
        String folder = runnerOnly(Util.DATA_DIR_PROPERTY);

        File locomotives = new File(Util.dataPath("LocDB.data")).getAbsoluteFile().getParentFile();

        assertEquals(locomotives.getCanonicalFile(), new File(folder).getCanonicalFile(), "the run named its own data"
            + " folder and the locomotive database is read from " + locomotives + " - the working directory, where"
            + " Adam's running application keeps his");

        File backups = new File(Util.getBackupPath("probe")).getAbsoluteFile().getParentFile().getParentFile();

        assertEquals(backups.getCanonicalFile(), new File(folder).getCanonicalFile(), "a backup would be written"
            + " under " + backups + " rather than under the run's own folder");
    }

    private static String runnerOnly(String property)
    {
        String value = System.getProperty(property);

        if (value == null || value.trim().isEmpty())
        {
            throw new SkipException(property + " is not set: this JVM was not started by docs/tools/one.sh or"
                + " battery.sh, which are the runners that give a run state of its own");
        }

        return value.trim();
    }
}
