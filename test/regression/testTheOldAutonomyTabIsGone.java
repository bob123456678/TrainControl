package regression;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
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
 * Autonomy needs a layout stored on this computer: the old JSON tab and its file are gone (OB-254), and a Central
 * Station layout's Autonomy menu offers the way in.
 *
 * The Load Autonomy Configuration tab - a JSON text area, Validate, Load, Export, a blank graph, the hidden auto-save
 * box - was shown wherever a layout could not hold a diagram setup, which in practice is a Central Station layout never
 * downloaded.  It was a second autonomy system with a file of its own, autonomy.json beside the application, read on
 * every start and written on every exit.  Adam, 2026-09-24: *"Remove it, require a local copy for autonomy."*
 *
 * Then, the same day, of that layout's Autonomy menu - greyed whole there since OB-202, which left the download offer
 * (OB-093) and Documentation where nobody could press them: *"Yes, open the autonomy menu with everything but those 2
 * greyed."*
 *
 * On a layout with no local copy, reached as a window that STARTED on one has it: the Autonomy slot still holding the
 * placeholder the form puts there, and the switch's own reset.  A window cannot start on a Central Station layout in
 * simulation - with no station to read pages from it makes the demo layout and switches to that - so it is started on
 * a sandbox and put back into that state.
 *
 * MUTATION: put the tab back for a layout with no local copy, or write autonomy.json on exit again, or grey the menu
 * there again, or leave it unbuilt for a layout with no session, and the matching claim fails.
 *
 * @author Adam
 */
public class testTheOldAutonomyTabIsGone
{
    /**
     * The old tab's title, in English: its bundle key went with it, so it is named here.  The battery runs in the
     * machine's language, which on Adam's machine is English.
     */
    private static final String OLD_TAB = "Load Autonomy Configuration";

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

        // NOW A LAYOUT WITH NO LOCAL COPY: the preference switching to a Central Station layout stores; the sandbox
        // puts the real value back.
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                // AS A WINDOW THAT STARTED THERE HAS IT: the form's placeholder in the Autonomy slot, and no menu built.
                // Started on the sandbox, the real menu replaced the placeholder; this puts it back.
                JMenuBar bar = (JMenuBar) field("mainMenuBar");
                JMenu built = (JMenu) field("autonomyMenu");
                JMenu placeholder = (JMenu) field("autonomyTopMenu");

                if (built != null)
                {
                    int at = bar.getComponentIndex(built);

                    bar.remove(built);
                    bar.add(placeholder, at);

                    Field menu = TrainControlUI.class.getDeclaredField("autonomyMenu");

                    menu.setAccessible(true);
                    menu.set(ui, null);
                }

                // What the switch does to the autonomy controls, and what the end of start-up asks of the slot.
                ui.resetAutonomySession();
                ui.refreshAutonomyAnchor();
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        settle();
    }

    private static Object field(String name) throws ReflectiveOperationException
    {
        Field field = TrainControlUI.class.getDeclaredField(name);

        field.setAccessible(true);

        return field.get(ui);
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

        JTabbedPane tabs = (JTabbedPane) field("locCommandPanels");

        List<String> titles = new ArrayList<>();

        for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i));

        assertFalse(titles.contains(OLD_TAB), "a Central Station layout still shows the Load Autonomy Configuration"
            + " tab, the old JSON autonomy Adam asked to remove (2026-09-24: \"Remove it, require a local copy for"
            + " autonomy\").  Tabs: " + titles);
    }

    /**
     * The tab strip is not announced under the deleted tab's name (Adam, 2026-09-24: *"can you clear the accessible
     * name?"*).
     *
     * The form gave the strip the old tab's title as its accessible name, and it outlived the tab: a screen reader
     * would have called the whole strip "Load Autonomy Configuration".
     *
     * @throws Exception from reflection
     */
    @Test
    public void testTheTabStripIsNotNamedAfterTheOldTab() throws Exception
    {
        JTabbedPane tabs = (JTabbedPane) field("locCommandPanels");

        assertNotEquals(tabs.getAccessibleContext().getAccessibleName(), OLD_TAB, "the tab strip is still announced"
            + " as the deleted Load Autonomy Configuration tab - its accessibleName in the form");
    }

    /**
     * On a Central Station layout the Autonomy menu can be opened.
     *
     * @throws Exception from reflection
     */
    @Test
    public void testTheAutonomyMenuOpensOnACentralStationLayout() throws Exception
    {
        assertTrue(ui.isRemoteLayout(), "precondition: the layout is not read as a Central Station one");

        JMenu menu = theAutonomySlot();

        assertTrue(menu.isEnabled(), "on a Central Station layout the Autonomy menu is greyed, so its offer to"
            + " download the layout (OB-093) and its Documentation cannot be pressed - Adam, 2026-09-24: \"open the"
            + " autonomy menu with everything but those 2 greyed\"");
    }

    /**
     * Opened there, Documentation can be chosen, and nothing can but it and the download.
     *
     * The download is offered only when a Central Station is connected with pages to fetch (DW-C2); in a simulation
     * it is not, and the greyed sentence stands in its place.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testOnlyTheDownloadAndTheGuideCanBeChosenThere() throws Exception
    {
        final JMenu menu = theAutonomySlot();

        SwingUtilities.invokeAndWait(() ->
        {
            for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);
        });

        String guide = I18n.t("ui.main.documentation");
        String download = I18n.t("autosetup.ui.menuNoSetupPossibleDownload");

        JMenuItem documentation = null;
        List<String> items = new ArrayList<>();
        List<String> live = new ArrayList<>();

        for (int i = 0; i < menu.getItemCount(); i++)
        {
            JMenuItem item = menu.getItem(i);

            if (item == null) continue;

            items.add(item.getText());

            if (guide.equals(item.getText())) documentation = item;
            else if (item.isEnabled() && !download.equals(item.getText())) live.add(item.getText());
        }

        assertNotNull(documentation, "the Autonomy menu on a Central Station layout has no Documentation.  Items: "
            + items);

        assertTrue(documentation.isEnabled(), "the Autonomy menu's Documentation is greyed on a Central Station layout");

        assertTrue(items.size() > 1, "precondition: the menu holds nothing but Documentation, so there is nothing"
            + " for the greying to be asked of.  Items: " + items);

        assertTrue(live.isEmpty(), "on a Central Station layout the Autonomy menu offers more than the download and"
            + " the guide - Adam, 2026-09-24: \"everything but those 2 greyed\".  Live: " + live);
    }

    /**
     * Whatever stands in the menu bar's Autonomy slot: the form's placeholder, or the menu built in its place.
     */
    private static JMenu theAutonomySlot() throws Exception
    {
        JMenuBar bar = (JMenuBar) field("mainMenuBar");

        String heading = I18n.t("autosetup.ui.menuAutonomy");

        for (int i = 0; i < bar.getMenuCount(); i++)
        {
            JMenu menu = bar.getMenu(i);

            if (menu != null && heading.equals(menu.getText())) return menu;
        }

        throw new AssertionError("precondition: the menu bar has no Autonomy menu at all");
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
