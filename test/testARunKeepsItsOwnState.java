import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.Util;

/**
 * A test run can name a data folder and a preference node of its own, and with neither named the
 * application uses exactly what 2.8.2 used.
 *
 * 2.8.2's tests read and wrote the owner's real preference node and the working folder's LocDB.data
 * and UIState.data.  docs/tools/one.sh now starts every test JVM with traincontrol.dataDir and
 * traincontrol.preferences, and the application finds its files and its node through Util.dataPath
 * and Util.preferencesFor (backported from dev, 6ce9d625 and its refinements; owner's ruling D149).
 *
 * Run outside one.sh, this class names a temporary folder and node of its own before the window
 * class loads, so the second claim proves the same thing either way.
 *
 * MUTATION: have dataPath or preferencesFor ignore its property and the second claim fails; have
 * either change the answer when no property is set and the first claim fails.
 */
public class testARunKeepsItsOwnState
{
    /** The node 2.8.2 opened: Preferences.userNodeForPackage(TrainControlUI.class). */
    private static final String DEFAULT_NODE = "/org/traincontrol/gui";

    /** The working folder's state files, by the names 2.8.2 used. */
    private static final String[] STATE = {MarklinControlStation.DATA_FILE_NAME, "UIState.data",
        TrainControlUI.AUTONOMY_FILE_NAME, Util.BACKUP_FOLDER};

    /** A node this class named itself, to remove afterwards; null when the runner named one. */
    private static String ownNode;

    static
    {
        try
        {
            if (unset(Util.DATA_DIR_PROPERTY))
            {
                System.setProperty(Util.DATA_DIR_PROPERTY,
                    Files.createTempDirectory("tc-own-state").toFile().getAbsolutePath());
            }

            if (unset(Util.PREFERENCES_PROPERTY))
            {
                ownNode = Util.ISOLATED_PREFERENCES_ROOT + "/claim-" + System.nanoTime();
                System.setProperty(Util.PREFERENCES_PROPERTY, ownNode);
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * With neither property set, the node and every file name are 2.8.2's.  Only the node's path is
     * read; nothing is written to it.
     */
    @Test
    public void testWithNoPropertiesTheNodeAndTheFilesAreUnchanged() throws Exception
    {
        String dir = System.clearProperty(Util.DATA_DIR_PROPERTY);
        String run = System.clearProperty(Util.PREFERENCES_PROPERTY);

        try
        {
            assertEquals(Util.preferencesFor(TrainControlUI.class).absolutePath(), DEFAULT_NODE,
                "with no test run named, the settings must come from the node 2.8.2 used");

            for (String name : STATE)
            {
                assertEquals(Util.dataPath(name), name,
                    "with no test run named, " + name + " must be the working folder's, as in 2.8.2");

                assertEquals(Util.dataPath("backup2026-11-15_10-00-00" + name), "backup2026-11-15_10-00-00" + name,
                    "a prefixed name (a backup's) must be unchanged too");
            }
        }
        finally
        {
            if (dir != null) System.setProperty(Util.DATA_DIR_PROPERTY, dir);
            if (run != null) System.setProperty(Util.PREFERENCES_PROPERTY, run);
        }
    }

    /**
     * With both set, the settings go to the run's node and the real one never sees them, and the
     * locomotive database, the UI state, autonomy.json and their backups are written to and read from
     * the run's folder while the working folder's files stay exactly as they were.
     */
    @Test
    public void testARunWithThePropertiesLeavesTheRealNodeAndFilesAlone() throws Exception
    {
        File folder = new File(System.getProperty(Util.DATA_DIR_PROPERTY).trim()).getCanonicalFile();

        // The settings
        Preferences used = TrainControlUI.getPrefs();

        assertTrue(used.absolutePath().startsWith("/" + Util.ISOLATED_PREFERENCES_ROOT + "/"),
            "the run named its own node and the window uses " + used.absolutePath());

        String key = "isolationProbe" + System.nanoTime();

        used.put(key, "written by a test run");
        used.flush();

        try
        {
            // Read only, and only if the node is already there: asking for a missing node creates it
            if (Preferences.userRoot().nodeExists(DEFAULT_NODE))
            {
                assertNull(Preferences.userRoot().node(DEFAULT_NODE).get(key, null),
                    "a setting this run wrote reached the real node");
            }
        }
        finally
        {
            used.remove(key);
        }

        // The files
        TreeMap<String, String> before = fingerprint(new File("."));

        MarklinControlStation model = MarklinControlStation.init(null, true, false, false, false);

        model.saveState(false);
        model.saveState(true);

        TrainControlUI ui = testMainWindowFaults.windowless();

        testMainWindowFaults.set(ui, "model", model);
        testMainWindowFaults.set(ui, "buttonMapping", new HashMap<Integer, JButton>());
        testMainWindowFaults.set(ui, "pageNames", new HashMap<Integer, String>());
        testMainWindowFaults.set(ui, "autosave", new JCheckBox());
        testMainWindowFaults.set(ui, "autonomyJSON", new JTextArea("{}"));

        List<HashMap<JButton, Locomotive>> pages = new ArrayList<>();
        pages.add(new HashMap<>());
        testMainWindowFaults.set(ui, "locMapping", pages);

        ui.saveState(false);
        ui.saveState(true);

        for (String name : new String[] {MarklinControlStation.DATA_FILE_NAME, "UIState.data",
            TrainControlUI.AUTONOMY_FILE_NAME})
        {
            assertTrue(new File(folder, name).isFile(), name + " was not written to the run's folder " + folder);
        }

        String[] backups = new File(folder, Util.BACKUP_FOLDER).list();

        assertTrue(backups != null && backups.length >= 3,
            "the three backups were not written to the run's folder");

        assertFalse(ui.restoreState().isEmpty(), "the UI state was not read back from the run's folder");
        assertEquals(fingerprint(new File(".")), before,
            "a run with its own folder wrote the working folder's state files");
    }

    @AfterClass(alwaysRun = true)
    public void removeOwnNode() throws Exception
    {
        if (ownNode != null && Preferences.userRoot().nodeExists(ownNode))
        {
            Preferences.userRoot().node(ownNode).removeNode();
        }
    }

    private static boolean unset(String property)
    {
        String value = System.getProperty(property);

        return value == null || value.trim().isEmpty();
    }

    /**
     * Each state file in the folder, and each file in its backup folder, with its size and time.
     */
    private static TreeMap<String, String> fingerprint(File folder)
    {
        TreeMap<String, String> out = new TreeMap<>();

        for (String name : STATE)
        {
            File f = new File(folder, name);

            out.put(name, f.exists() ? f.length() + "@" + f.lastModified() : "absent");

            String[] inside = f.isDirectory() ? f.list() : null;

            if (inside != null)
            {
                for (String child : inside)
                {
                    File c = new File(f, child);
                    out.put(name + "/" + child, c.length() + "@" + c.lastModified());
                }
            }
        }

        return out;
    }
}
