package regression;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Autonomy needs a layout stored on this computer: the old JSON tab and its file are gone (OB-254).
 *
 * The Load Autonomy Configuration tab - a JSON text area, Validate, Load, Export, a blank graph, the hidden auto-save
 * box - was shown wherever a layout could not hold a diagram setup, which in practice is a Central Station layout never
 * downloaded.  It was a second autonomy system with a file of its own, autonomy.json beside the application, read on
 * every start and written on every exit.  Adam, 2026-09-24: *"Remove it, require a local copy for autonomy."*  A
 * Central Station layout's Autonomy menu says so, and Layouts > Download is the way in; an old autonomy.json is still
 * imported from the Autonomy menu once a local copy exists.
 *
 * On a layout with no local copy - where the tab used to come back - reached as switching to a Central Station layout
 * reaches it.
 *
 * MUTATION: put the tab back for a layout with no local copy, or write autonomy.json on exit again, and the matching
 * claim fails.
 *
 * @author Adam
 */
public class testTheOldAutonomyTabIsGone
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the tab is part of the window, and the window needs a display");
        }

        if (System.getProperty(Util.DATA_DIR_PROPERTY) == null)
        {
            throw new SkipException("this writes a backup, so it runs only where the run has its own data folder"
                + " (one.sh, battery.sh)");
        }

        // BEFORE init, which opens whatever the layout preference names (OB-111).
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        settle();

        // NOW A LAYOUT WITH NO LOCAL COPY, as switching to a Central Station layout leaves the window: the preference
        // it stores, no session to build a setup from, and the autonomy controls mounted again.  Started that way in
        // simulation the window offers a demo layout and takes it, so it is switched here instead.  The sandbox puts the
        // preference back.
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        Field session = TrainControlUI.class.getDeclaredField("autonomySession");

        session.setAccessible(true);
        session.set(ui, null);

        SwingUtilities.invokeAndWait(() -> ui.mountAutonomyControls());

        settle();
    }

    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 8; pass++) SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
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

    /**
     * A Central Station layout's window has no Load Autonomy Configuration tab.
     *
     * @throws Exception from reflection
     */
    @Test
    public void testACentralStationLayoutHasNoOldAutonomyTab() throws Exception
    {
        assertTrue(ui.isRemoteLayout(), "precondition: the layout is not read as a Central Station one");

        Field field = TrainControlUI.class.getDeclaredField("locCommandPanels");

        field.setAccessible(true);

        JTabbedPane tabs = (JTabbedPane) field.get(ui);

        List<String> titles = new ArrayList<>();

        for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i));

        assertFalse(titles.contains(I18n.t("ui.main.autoConfig")), "a Central Station layout still shows the Load"
            + " Autonomy Configuration tab, the old JSON autonomy Adam asked to remove (2026-09-24: \"Remove it, require"
            + " a local copy for autonomy\").  Tabs: " + titles);
    }

    /**
     * Nothing reads autonomy.json at start or loads the old graph from it.
     *
     * Read from the source, because on the unfixed code the load opens dialogs a test cannot answer - and a removal is
     * what is claimed.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testNothingLoadsTheOldGraphAtStart() throws Exception
    {
        String code = new String(Files.readAllBytes(Paths.get("src/org/traincontrol/gui/TrainControlUI.java")),
            StandardCharsets.UTF_8).replaceAll("(?s)/[*].*?[*]/", " ").replaceAll("//[^\\r\\n]*", " ");

        assertFalse(code.contains("resumesFromJsonAtStart"), "the start-up still has a way to load the old JSON graph"
            + " (resumesFromJsonAtStart)");

        assertFalse(code.contains("Paths.get(TrainControlUI.AUTONOMY_FILE_NAME)"), "the start-up still reads"
            + " autonomy.json from beside the application");
    }

    /**
     * The save writes its other backups, and no autonomy.json among them - on a Central Station layout too.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testNoLayoutWritesAutonomyJson() throws Exception
    {
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
            // Deleted with the tab: nothing is left to write.
        }

        File folder = new File(Util.dataPath(Util.BACKUP_FOLDER));

        String[] had = folder.list();

        List<String> before = had == null ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(had));

        SwingUtilities.invokeAndWait(() -> ui.saveState(true, false));

        String[] now = folder.list();

        List<String> written = now == null ? new ArrayList<String>() : new ArrayList<>(Arrays.asList(now));

        written.removeAll(before);

        boolean state = false;
        boolean autonomy = false;

        for (String name : written)
        {
            if (name.endsWith("UIState.data")) state = true;
            if (name.endsWith(TrainControlUI.AUTONOMY_FILE_NAME)) autonomy = true;
        }

        assertTrue(state, "control: the backup save wrote no UIState.data, so it did not run: " + written);

        assertFalse(autonomy, "a Central Station layout had autonomy.json written on exit - the old JSON autonomy's"
            + " file, which nothing should write any more (OB-254)");
    }
}
