package regression;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.Util;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A layout stored on this computer never has autonomy.json written, loaded setup or not (OB-254).
 *
 * autonomy.json is the old graph's file, beside the application rather than in the layout folder.  The save on exit
 * wrote it back whenever no diagram configuration was loaded - which on a local layout is any session started with
 * Load Autonomy unticked, as every legacy import now leaves it (REG2-C3).  Adam, 2026-09-24: *"We should only write to
 * the new save format in the layout folder, IMO."*  The same day the old window was removed from every layout, and
 * no layout writes the file (testTheOldAutonomyTabIsGone asks it of a Central Station one).
 *
 * Asked of the backup save, which writes the same files under a timestamp into the run's own backup folder - never over
 * the application's autonomy.json, whichever way the claim comes out.
 *
 * MUTATION: write autonomy.json when no configuration is loaded again, and this fails.
 *
 * @author Adam
 */
public class testALocalLayoutNeverWritesAutonomyJson
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static String autoLoadWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the save runs from the window, and the window needs a display");
        }

        if (System.getProperty(Util.DATA_DIR_PROPERTY) == null)
        {
            throw new SkipException("this writes a backup, so it runs only where the run has its own data folder"
                + " (one.sh, battery.sh)");
        }

        // BEFORE init, which opens whatever the layout preference names (OB-111).
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        // NO CONFIGURATION LOADED AT START: Load Autonomy unticked, as a legacy import leaves it.
        autoLoadWas = TrainControlUI.getPrefs().get(TrainControlUI.AUTO_LOAD_AUTONOMY, null);

        TrainControlUI.getPrefs().putBoolean(TrainControlUI.AUTO_LOAD_AUTONOMY, false);

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        for (int pass = 0; pass < 8; pass++) SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (autoLoadWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.AUTO_LOAD_AUTONOMY);
            else TrainControlUI.getPrefs().put(TrainControlUI.AUTO_LOAD_AUTONOMY, autoLoadWas);

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    private static List<String> backups(File folder)
    {
        String[] names = folder.list();

        return names == null ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(names));
    }

    /**
     * The save writes its other backups, and no autonomy.json among them.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheSaveWritesNoAutonomyJson() throws Exception
    {
        Method local = TrainControlUI.class.getDeclaredMethod("isLocalLayout");

        local.setAccessible(true);

        assertTrue((Boolean) local.invoke(ui), "precondition: the sandbox is not a layout stored on this computer");

        assertNull(ui.getActiveDiagramConfiguration(), "precondition: a configuration was loaded at start, so this is"
            + " not the session OB-254 is about");

        // What the old window held on every start, where it still exists: autonomy.json, read in whether used or not.
        try
        {
            Field field = TrainControlUI.class.getDeclaredField("autonomyJSON");

            field.setAccessible(true);

            final JTextArea text = (JTextArea) field.get(ui);

            SwingUtilities.invokeAndWait(() -> text.setText("{\"points\": [], \"edges\": []}"));
        }
        catch (NoSuchFieldException gone)
        {
            // Deleted with the tab (OB-254): nothing is left to write.
        }

        File folder = new File(Util.dataPath(Util.BACKUP_FOLDER));

        List<String> before = backups(folder);

        SwingUtilities.invokeAndWait(() -> ui.saveState(true, false));

        List<String> written = backups(folder);

        written.removeAll(before);

        boolean state = false;
        boolean autonomy = false;

        for (String name : written)
        {
            if (name.endsWith("UIState.data")) state = true;
            if (name.endsWith(TrainControlUI.AUTONOMY_FILE_NAME)) autonomy = true;
        }

        assertTrue(state, "control: the backup save wrote no UIState.data, so it did not run: " + written);

        assertFalse(autonomy, "a layout stored on this computer had autonomy.json written, because no configuration"
            + " was loaded (OB-254; Adam, 2026-09-24: \"We should only write to the new save format in the layout"
            + " folder\")");
    }
}
