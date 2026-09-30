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
     * Opened there, Documentation can be chosen, and nothing can but it and the offer of a layout.
     *
     * The offer is a submenu since MT-548's note - the download and Create New Layout - and its own claim is
     * `testWithNothingToDownloadANewLayoutIsOffered`; here it is the one other thing that may be live.
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
        String download = I18n.t("autosetup.ui.menuNoSetupPossible");

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
     * Where there is no Central Station layout to download, the Autonomy menu offers a new layout - the Layouts menu's
     * own Create New Layout (MT-548's note).
     *
     * Adam, 2026-09-29, on MT-548: *"works, but what is the cs3 has no layout?  give the user the choice to either
     * download or create a new one, which redirects to the corresponding option under layouts."*  The offer was the
     * download alone, and where there was nothing to download the menu said only that autonomy needs a layout.  Both are
     * offered now, under the Layouts menu's own names, each greyed exactly when its Layouts item would do nothing.
     *
     * MUTATION: offer the download alone again, or send Create New Layout anywhere but the Layouts menu's own, and this
     * fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testWithNothingToDownloadANewLayoutIsOffered() throws Exception
    {
        final JMenu menu = theAutonomySlot();

        SwingUtilities.invokeAndWait(() ->
        {
            for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);
        });

        JMenu offer = null;
        List<String> items = new ArrayList<>();

        for (int i = 0; i < menu.getItemCount(); i++)
        {
            JMenuItem item = menu.getItem(i);

            if (item == null) continue;

            items.add(item.getText());

            if (item instanceof JMenu && I18n.t("autosetup.ui.menuNoSetupPossible").equals(item.getText()))
            {
                offer = (JMenu) item;
            }
        }

        assertNotNull(offer, "on a Central Station layout with nothing to download, the Autonomy menu offers no choice of"
            + " a layout - Adam, MT-548: \"give the user the choice to either download or create a new one\".  Items: "
            + items);

        assertTrue(offer.isEnabled(), "the offer of a layout is greyed");

        JMenuItem download = null;
        JMenuItem create = null;

        for (int i = 0; i < offer.getItemCount(); i++)
        {
            JMenuItem item = offer.getItem(i);

            if (item == null) continue;

            if (I18n.t("ui.main.toolbar.downloadCSLayout").equals(item.getText())) download = item;
            if (I18n.t("ui.main.toolbar.createLayout").equals(item.getText())) create = item;
        }

        assertNotNull(download, "the offer has no Download Central Station Layout Files");
        assertNotNull(create, "the offer has no Create New Layout");

        // NOTHING TO DOWNLOAD in a simulation: greyed, saying why
        assertFalse(ui.hasPagesToDownload() && ui.isCentralStationConnected(), "precondition: this simulation has a"
            + " Central Station layout to download, so the claim below is about the other case");

        assertFalse(download.isEnabled(), "the download is offered with nothing to download (DW-C2)");

        assertTrue(download.getToolTipText() != null && !download.getToolTipText().trim().isEmpty(), "the greyed download"
            + " does not say why");

        assertTrue(create.isEnabled(), "Create New Layout is greyed where there is nothing to download - the one way left"
            + " to a layout autonomy can use (MT-548)");

        // AND IT IS THE LAYOUTS MENU'S OWN: its first words, then its folder chooser
        final JMenuItem creating = create;

        SwingUtilities.invokeLater(creating::doClick);

        javax.swing.JOptionPane first = awaitAnOptionPane(10000);

        assertNotNull(first, "Create New Layout from the Autonomy menu did nothing (MT-548)");

        String said = String.valueOf(first.getMessage());

        dismiss(first);

        assertEquals(said, I18n.t("layout.ui.infoSelectFolderForNewLayout"), "Create New Layout from the Autonomy menu"
            + " is not the Layouts menu's own (MT-548)");

        javax.swing.JFileChooser chooser = awaitAChooser(10000);

        assertNotNull(chooser, "Create New Layout asked for no folder");

        final javax.swing.JFileChooser cancelling = chooser;

        SwingUtilities.invokeAndWait(cancelling::cancelSelection);

        settle();
    }

    /**
     * Open Layout starts at this computer's last layout folder, even with the Central Station's layout switched to
     * (OB-308).
     *
     * Adam, 2026-09-29: *"layouts -> open layout should default to the last used local layout folder.  this seems like a
     * regression"*.  The chooser started at the layout folder preference, which switching to the Central Station's layout
     * empties - and a chooser started at nothing opens in Documents.  The last folder is remembered on its own now, and
     * the chooser opens beside it with it selected: opening it again is one click, and another folder is a step away.
     *
     * This class's window started on a sandbox folder and was then switched as the Central Station switch switches it.
     *
     * MUTATION: forget the folder with the switch, or open the chooser inside it, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testOpenLayoutStartsAtTheLastLayoutFolder() throws Exception
    {
        assertTrue(ui.isRemoteLayout(), "precondition: the layout is not read as a Central Station one");

        final File last = sandbox.getFolder().getAbsoluteFile();

        final java.lang.reflect.Method door = TrainControlUI.class.getDeclaredMethod(
            "chooseLocalDataFolderMenuItemActionPerformed", java.awt.event.ActionEvent.class);

        door.setAccessible(true);

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                door.invoke(ui, (java.awt.event.ActionEvent) null);
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        javax.swing.JFileChooser chooser = awaitAChooser(10000);

        assertNotNull(chooser, "Open Layout... asked for no folder");

        final javax.swing.JFileChooser open = chooser;
        final File[] at = new File[2];

        SwingUtilities.invokeAndWait(() ->
        {
            at[0] = open.getCurrentDirectory();
            at[1] = open.getSelectedFile();

            open.cancelSelection();
        });

        settle();

        assertEquals(canonical(at[0]), canonical(last.getParentFile()), "Open Layout... does not start beside the last"
            + " layout folder of this computer's, " + last + ", once the Central Station's layout is switched to - it"
            + " starts in " + at[0] + " (OB-308)");

        assertEquals(canonical(at[1]), canonical(last), "Open Layout... does not have the last layout folder selected");

        // AND A FOLDER CHOSEN SINCE IS THE ONE REMEMBERED.  Every door that names a layout folder - Open Layout, Create
        // New Layout, the download, the demo layout - writes it through the one method that remembers it, so that
        // method is asked here and the doors are counted.
        String code = new String(Files.readAllBytes(Paths.get("src/org/traincontrol/gui/TrainControlUI.java")),
            StandardCharsets.UTF_8);

        assertEquals(code.split("prefs\\.put\\(LAYOUT_OVERRIDE_PATH_PREF", -1).length - 1, 1, "a door writes the layout"
            + " folder without going through `layoutFolderIs`, so the folder it names is not remembered (OB-308)");

        final java.lang.reflect.Method folderIs = TrainControlUI.class.getDeclaredMethod("layoutFolderIs", String.class);

        folderIs.setAccessible(true);

        File other = Files.createTempDirectory("ob308").toFile();

        other.deleteOnExit();

        try
        {
            folderIs.invoke(null, other.getAbsolutePath());

            // AND THE SWITCH, as it switches
            folderIs.invoke(null, "");

            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    door.invoke(ui, (java.awt.event.ActionEvent) null);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });

            final javax.swing.JFileChooser again = awaitAChooser(10000);

            assertNotNull(again, "Open Layout... asked for no folder the second time");

            SwingUtilities.invokeAndWait(() ->
            {
                at[1] = again.getSelectedFile();

                again.cancelSelection();
            });

            settle();

            assertEquals(canonical(at[1]), canonical(other), "a layout folder named since is not the one Open Layout..."
                + " starts at after the switch (OB-308)");
        }
        finally
        {
            // PUT BACK as this class's window has it: the sandbox remembered, and no local layout
            folderIs.invoke(null, last.getAbsolutePath());
            folderIs.invoke(null, "");

            other.delete();
        }
    }

    private static String canonical(File file) throws java.io.IOException
    {
        return file == null ? null : file.getCanonicalPath();
    }

    /** A showing window's option pane, within the time, or null. */
    private static javax.swing.JOptionPane awaitAnOptionPane(long millis) throws Exception
    {
        long giveUp = System.currentTimeMillis() + millis;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                javax.swing.JOptionPane pane = componentIn(((javax.swing.JDialog) window).getContentPane(),
                    javax.swing.JOptionPane.class);

                if (pane != null) return pane;
            }

            Thread.sleep(50);
        }

        return null;
    }

    /** A file chooser showing in a window, within the time, or null. */
    private static javax.swing.JFileChooser awaitAChooser(long millis) throws Exception
    {
        long giveUp = System.currentTimeMillis() + millis;

        while (System.currentTimeMillis() < giveUp)
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (!(window instanceof javax.swing.JDialog) || !window.isShowing()) continue;

                javax.swing.JFileChooser found = componentIn(((javax.swing.JDialog) window).getContentPane(),
                    javax.swing.JFileChooser.class);

                if (found != null) return found;
            }

            Thread.sleep(50);
        }

        return null;
    }

    private static <T> T componentIn(java.awt.Container container, Class<T> type)
    {
        for (java.awt.Component child : container.getComponents())
        {
            if (type.isInstance(child)) return type.cast(child);

            if (child instanceof java.awt.Container)
            {
                T found = componentIn((java.awt.Container) child, type);

                if (found != null) return found;
            }
        }

        return null;
    }

    private static void dismiss(final javax.swing.JOptionPane pane) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> pane.setValue(javax.swing.JOptionPane.OK_OPTION));
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
