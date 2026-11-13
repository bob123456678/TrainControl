package regression;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.RightClickTips;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.Display;
import support.Reflect;

/**
 * The main window's menu bar as the owner's answers to the menu review arrange it (D40 to D52 on the Decisions page,
 * FR-198 for the Central Station menu not built): every item that moved, was renamed or is new does what it did before,
 * or what the answer says it does now, found by its text in the real menu bar and clicked.
 *
 * One window over the live snapshot for the class, in the run's own preference node; each claim puts back what it sets.
 */
public class testTheMenuBarIsWired
{
    private static final long PATIENCE_MS = 20000;

    private static support.SandboxedWindow sandboxed;
    private static TrainControlUI ui;
    private static String recentWas;

    @BeforeClass(timeOut = 180000)
    public static void setUpClass() throws Exception
    {
        Display.needed("the menu bar is part of the window, and the window needs a display");

        recentWas = prefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, null);

        // No other layout recorded: the run's node starts as a copy of the owner's, whose list would be listed.
        prefs().put(TrainControlUI.RECENT_LAYOUTS_PREF, "");

        sandboxed = new support.SandboxedWindow();

        sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();

        ui = sandboxed.hold(testTheImportDoorReadsAnOldFile.openTheWindow());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        TrainControlUI.browserForTests = null;
        TrainControlUI.releaseInfoForTests = null;

        try
        {
            AskingWindows.closeEveryDialog();

            if (sandboxed != null) sandboxed.close();
        }
        finally
        {
            putBack(TrainControlUI.RECENT_LAYOUTS_PREF, recentWas);
        }
    }

    /**
     * Each menu reads as the answers arrange it, which is the menu review's inventory changed only as they say: File
     * as it was with the address item renamed; Locomotives with no Utilities drawer (D42); Layouts with no Data Source
     * and no Pop-up All, its heading in use marked and the folder under Recent Layouts (D43, D46, D52); Preferences
     * with its headings, names and order (D47); Help with the guides and the update check (D48); "..." where an item
     * asks (D49).  Nothing reachable before is lost, and nothing is there twice.
     *
     * MUTATION: leave `arrangeMainMenus` out of the constructor (Utilities and the old Preferences order are back).
     */
    @Test(timeOut = 120000)
    public void testEachMenuReadsAsTheAnswersArrangeIt() throws Exception
    {
        String gray = " [gray]";

        assertEquals(read(menu("ui.main.toolbar.file")), keys("ui.main.toolbar.syncDBWithCS", "ui.main.toolbar.changeIP",
            "---", "ui.main.toolbar.backupData", "ui.main.toolbar.restoreData", "---", "ui.main.toolbar.exportLog", "---",
            "ui.main.toolbar.exit"), "File");

        assertEquals(read(menu("ui.main.toolbar.locomotives")), keys("ui.main.toolbar.quickFindLoc",
            "ui.main.toolbar.browseDatabase", "ui.main.toolbar.addLoc", "---", "ui.main.toolbar.turnOffFunctions",
            "ui.main.toolbar.turnOnLights", "ui.main.toolbar.syncFullFunctionState", "ui.main.toolbar.checkForRenamedLocs",
            "---", "ui.main.toolbar.exportToCSV", "ui.main.toolbar.importFromCSV"), "Locomotives");

        List<String> layouts = keys("ui.main.toolbar.openLayout", "ui.main.toolbar.recentLayouts", "ui.main.toolbar.managePages",
            "ui.main.toolbar.createLayout", "---");

        layouts.add(0, I18n.f("ui.main.toolbar.headingInUse", I18n.t("ui.main.toolbar.headingLocalLayout")) + gray);
        layouts.set(2, layouts.get(2) + " >");
        layouts.add(I18n.t("ui.main.toolbar.headingCentralStationLayout") + gray);

        // Never connected in a test, so the three Central Station items are grayed.
        for (String key : new String[] {"ui.main.toolbar.switchToCSLayout", "ui.main.toolbar.downloadCSLayout",
            "ui.main.toolbar.openCS3WebApp"})
        {
            layouts.add(I18n.t(key) + gray);
        }

        layouts.addAll(keys("---", "layout.ui.menuExportActiveDiagram"));

        assertEquals(read(menu("ui.main.toolbar.layouts")), layouts, "Layouts");

        assertEquals(read(submenu(menu("ui.main.toolbar.layouts"), "ui.main.toolbar.recentLayouts")),
            Arrays.asList(I18n.t("ui.main.toolbar.noOtherLayouts") + gray, "---",
                I18n.t("ui.main.toolbar.showCurrentLayoutFolder")), "Layouts > Recent Layouts, with no other layout");

        JMenu preferences = menu("ui.main.toolbar.preferences");

        List<String> top = keys("ui.main.toolbar.winOnTop", "ui.main.toolbar.rememberWindowLoc",
            "ui.main.toolbar.hideRightClickTips", "---");

        for (String key : new String[] {"ui.main.toolbar.locControl", "ui.main.toolbar.trackDiagramPreferences",
            "autolayout.ui.tabAutonomy", "ui.main.toolbar.startup", "ui.main.toolbar.debug"})
        {
            top.add(I18n.t(key) + " >");
        }

        assertEquals(read(preferences), top, "Preferences (Debug is there because the class runs in debug)");

        List<String> control = keys("ui.main.toolbar.slidersChangeActiveLoc", "ui.main.toolbar.showKeyboardControlHints",
            "ui.main.toolbar.activeLocInPopupTitles", "ui.main.toolbar.showPagesAsTabs", "ui.main.toolbar.showPageNumbers",
            "---", "ui.main.toolbar.qwertyKeyboard", "ui.main.toolbar.qwertzKeyboard", "ui.main.toolbar.azertyKeyboard");

        control.add(6, I18n.t("ui.main.toolbar.headingKeyboardLayout") + gray);

        assertEquals(read(submenu(preferences, "ui.main.toolbar.locControl")), control, "Preferences > Locomotive Control");

        assertEquals(read(submenu(preferences, "ui.main.toolbar.trackDiagramPreferences")),
            keys("ui.main.toolbar.showAddresses"), "Preferences > Track Diagram");

        assertEquals(read(submenu(preferences, "autolayout.ui.tabAutonomy")), keys(
            "autolayout.ui.tabAutonomyEnhancedValidation", "---", "autolayout.ui.menuShowInactiveLabels",
            "ui.main.toolbar.greyStationLabels", "ui.main.toolbar.restrictionArrows", "ui.main.toolbar.allowedDirections"),
            "Preferences > Autonomy");

        List<String> startup = keys("ui.main.toolbar.powerOn", "ui.main.toolbar.powerOff", "ui.main.toolbar.doNothing",
            "---", "ui.main.toolbar.loadAutonomy", "---", "ui.main.toolbar.checkForUpdates");

        startup.add(0, I18n.t("ui.main.toolbar.headingTrackPowerAtStartup") + gray);

        assertEquals(read(submenu(preferences, "ui.main.toolbar.startup")), startup, "Preferences > Startup");

        // Download Update is there, hidden, until a check finds a release.
        assertEquals(read(menu("ui.main.toolbar.help")), keys("ui.main.userGuide", "ui.menu.autonomyGuide", "---",
            "ui.main.checkForUpdatesNow", "ui.main.viewReleases", "---", "ui.main.aboutReadme"), "Help");
    }

    /**
     * The items that moved or were renamed are the same items, so each reaches the handler it reached before: the
     * generated block's listener, or the hand-built one, travels with the item (D41, D42, D43, D47, D48, D49).
     *
     * MUTATION: build Locomotive Database... as a new item in `arrangeMainMenus` (with the same text) instead of
     * moving `viewDatabaseMenuItem`, and its row fails.
     */
    @Test(dataProvider = "movedItems", timeOut = 60000)
    public void testAMovedItemIsTheSameDoor(String[] path, String field) throws Exception
    {
        JMenuItem found = itemAt(path);

        assertNotNull(found, "no item " + Arrays.toString(path));

        assertSame(found, Reflect.get(TrainControlUI.class, ui, field), String.join(" > ", path) + " is not the item "
            + field + " was, so it does not reach that item's handler");
    }

    /** A row: the item's path of keys from the menu bar ("ui.main.toolbar." left off), then the field it was. */
    @DataProvider
    public static Object[][] movedItems()
    {
        return rows("file>changeIP changeIPMenuItem; file>restoreData restoreDataMenuItem;"
            + " locomotives>quickFindLoc quickFindMenuItem; locomotives>browseDatabase viewDatabaseMenuItem;"
            + " locomotives>addLoc addLocomotiveMenuItem; locomotives>turnOffFunctions turnOffFunctionsMenuItem;"
            + " locomotives>turnOnLights turnOnLightsMenuItem; locomotives>syncFullFunctionState syncFullLocStateMenuItem;"
            + " locomotives>checkForRenamedLocs checkForRenameMenuItem; locomotives>exportToCSV exportLocsToCSVMenuItem;"
            + " layouts>createLayout initializeLocalLayoutMenuItem; layouts>downloadCSLayout downloadCSLayoutMenuItem;"
            + " layouts>recentLayouts>showCurrentLayoutFolder showCurrentLayoutFolderMenuItem;"
            + " preferences>locControl>slidersChangeActiveLoc slidersChangeActiveLocMenuItem;"
            + " preferences>locControl>qwertyKeyboard keyboardQwertyMenuItem;"
            + " preferences>locControl>qwertzKeyboard keyboardQwertzMenuItem;"
            + " preferences>locControl>azertyKeyboard keyboardAzertyMenuItem;"
            + " preferences>trackDiagramPreferences layoutMenuItem; preferences>startup startupToolbarMenu;"
            + " preferences>startup>powerOn powerOnStartup; preferences>startup>powerOff powerOffStartup;"
            + " preferences>startup>doNothing powerNoChangeStartup; help>ui.main.viewReleases viewReleasesMenuItem;"
            + " help>ui.main.aboutReadme aboutMenuItem");
    }

    /**
     * Quick Find Locomotive..., Locomotive Database... and Add Locomotive... show their keys on the right, taken from
     * the window's shortcut table, and have no tooltip that only named the key; the menu does not act on the key a
     * second time, as the table already does (D40).
     *
     * MUTATION: drop the "none" binding in `showShortcut`, and a Control+D the window's bindings see clicks the item.
     */
    @Test(timeOut = 60000)
    public void testTheShortcutsShowAndActOnlyThroughTheTable() throws Exception
    {
        String[][] rows = {{"ui.main.toolbar.quickFindLoc", "find a locomotive"},
            {"ui.main.toolbar.browseDatabase", "choose a locomotive"}, {"ui.main.toolbar.addLoc", "add a locomotive"}};

        JMenuBar bar = Components.onTheEventThread(ui::getJMenuBar);

        // What the window's key handling asks of its menu bar for a key no component took (Swing's KeyboardManager);
        // asked directly, as a test window may not be the one on screen.
        java.lang.reflect.Method bindings = JMenuBar.class.getDeclaredMethod("processKeyBinding",
            javax.swing.KeyStroke.class, KeyEvent.class, int.class, boolean.class);

        bindings.setAccessible(true);

        for (String[] row : rows)
        {
            JMenuItem item = itemAt(new String[] {"ui.main.toolbar.locomotives", row[0]});

            javax.swing.KeyStroke keys = TrainControlUI.KeyShortcuts.named(row[1]).stroke();

            assertEquals(item.getAccelerator(), keys, row[0] + " does not show the table's keys for \"" + row[1] + "\"");

            assertNull(item.getToolTipText(), row[0] + " still has a tooltip");

            final int[] clicked = new int[1];

            ActionListener counting = e -> clicked[0]++;

            // Taken off before the key, so the item's own handler is not run if the mutation clicks it.
            ActionListener[] own = item.getActionListeners();

            Components.onTheEventThread(() ->
            {
                for (ActionListener each : own) item.removeActionListener(each);

                item.addActionListener(counting);

                bindings.invoke(bar, keys, new KeyEvent(ui.getRootPane(), KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), keys.getModifiers(), keys.getKeyCode(), KeyEvent.CHAR_UNDEFINED),
                    JComponent.WHEN_IN_FOCUSED_WINDOW, true);

                item.removeActionListener(counting);

                for (ActionListener each : own) item.addActionListener(each);

                return null;
            });

            assertEquals(clicked[0], 0, row[0] + " acted on " + keys + " itself: with the table, the key acts twice");
        }
    }

    /**
     * File > Change Central Station Address... asks with the stored address filled in; OK stores what was typed and
     * says it is used from the next start; Cancel changes nothing (D41).
     *
     * MUTATION: put back the old handler body (it erases the address without asking).
     */
    @Test(timeOut = 120000)
    public void testChangeCentralStationAddressAsksAndStores() throws Exception
    {
        String was = prefs().get(TrainControlUI.IP_PREF, null);

        try
        {
            prefs().put(TrainControlUI.IP_PREF, "192.168.1.50");

            JMenuItem item = itemAt(new String[] {"ui.main.toolbar.file", "ui.main.toolbar.changeIP"});

            final String[] shown = new String[1];

            List<String> asked = choose(item, message ->
            {
                if (!message.equals(I18n.t("ui.main.promptCentralStationAddress"))) return null;

                AskingWindows.inTheQuestion(JTextField.class, field -> true, field -> shown[0] = field.getText());

                return Answering.type("192.168.1.60");
            }, 2);

            assertEquals(shown[0], "192.168.1.50", "the question does not start from the stored address");

            assertEquals(prefs().get(TrainControlUI.IP_PREF, null), "192.168.1.60", "OK did not store the address");

            assertEquals(asked.get(1), I18n.t("ui.main.infoAddressFromNextStart"), "OK did not say when it applies");

            choose(item, message -> Answering.pressAt(1), 1);

            assertEquals(prefs().get(TrainControlUI.IP_PREF, null), "192.168.1.60", "Cancel changed the address");
        }
        finally
        {
            putBack(TrainControlUI.IP_PREF, was);
        }
    }

    /**
     * While a run would refuse them, Sync Database w/ Central Station, Restore from Backup..., Add Locomotive..., Import
     * from CSV... and Sync Full Function State w/ Central Station... are grayed with the refusal's own sentence, and lit
     * again with their own tooltips after it (D45).
     *
     * MUTATION: leave out `greyWhatARunRefuses` on the File and Locomotives menus.
     */
    @Test(timeOut = 120000)
    public void testWhatARunRefusesIsGrayedWithItsReason() throws Exception
    {
        org.traincontrol.automation.Layout railway = ui.getModel().getAutoLayout();

        assertNotNull(railway, "precondition: no autonomy setup to run");

        String[][] items = {{"ui.main.toolbar.file", "ui.main.toolbar.syncDBWithCS"},
            {"ui.main.toolbar.locomotives", "ui.main.toolbar.addLoc"},
            {"ui.main.toolbar.locomotives", "ui.main.toolbar.importFromCSV"},
            {"ui.main.toolbar.locomotives", "ui.main.toolbar.syncFullFunctionState"}};

        String[] restore = {"ui.main.toolbar.file", "ui.main.toolbar.restoreData"};

        String restoreTip = itemAt(restore).getToolTipText();

        try
        {
            railway.setStagingInProgress(true);

            assertTrue(ui.getModel().isAutonomyRunning(), "precondition: the run is not seen");

            for (String[] each : items)
            {
                JMenuItem item = itemAt(each);

                assertFalse(item.isEnabled(), each[1] + " is live while a run would refuse it");

                assertEquals(plain(item.getToolTipText()), plain(I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning")),
                    each[1] + " does not say the refusal's reason");
            }

            JMenuItem item = itemAt(restore);

            assertFalse(item.isEnabled(), "Restore from Backup... is live while a run holds the setup");

            assertEquals(plain(item.getToolTipText()), plain(I18n.t("ui.main.errorNoEditsWhileRunning")),
                "Restore from Backup... does not say the refusal's reason");
        }
        finally
        {
            railway.setStagingInProgress(false);
        }

        for (String[] each : items) assertTrue(itemAt(each).isEnabled(), each[1] + " stays grayed after the run");

        assertTrue(itemAt(restore).isEnabled(), "Restore from Backup... stays grayed after the run");

        assertEquals(itemAt(restore).getToolTipText(), restoreTip, "Restore from Backup... did not get its tooltip back");
    }

    /**
     * Layouts marks the heading of the layout in use, and the grayed items say why: on this computer's layout with no
     * Central Station, Switch and Download say what they need, Open CS3 Web App names the Central Station 3, and Recent
     * Layouts stays live, saying there is no other layout (D43, D46).  On a Central Station layout the other heading is
     * marked, Switch is already in use, Edit/Add Pages... says only local files can be edited, and there is no layout
     * folder to show.
     *
     * MUTATION: leave out the `sayWhy` calls in `repaintPathLabel` and `applyLayoutEditingAvailability`.
     */
    @Test(timeOut = 120000)
    public void testTheLayoutsMenuSaysWhatIsInUseAndWhyItemsAreGray() throws Exception
    {
        JMenu layouts = menu("ui.main.toolbar.layouts");

        read(layouts);

        assertTip(layouts, "ui.main.toolbar.switchToCSLayout", "ui.main.toolbar.tooltip.centralStationNotConnected");
        assertTip(layouts, "ui.main.toolbar.downloadCSLayout", "ui.main.toolbar.tooltip.downloadNeedsCSLayout");
        assertTip(layouts, "ui.main.toolbar.openCS3WebApp", "ui.main.toolbar.tooltip.needsCS3");

        assertTrue(submenu(layouts, "ui.main.toolbar.recentLayouts").isEnabled(), "Recent Layouts is grayed with no"
            + " other layout");

        String folder = prefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, null);

        try
        {
            // What switching to a Central Station layout stores, as testTheOldAutonomyTabIsGone does.
            prefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            ui.repaintPathLabel();

            Components.drain(4);

            List<String> read = read(menu("ui.main.toolbar.layouts"));

            String gray = " [gray]";

            assertTrue(read.contains(I18n.f("ui.main.toolbar.headingInUse",
                I18n.t("ui.main.toolbar.headingCentralStationLayout")) + gray), "the Central Station heading is not"
                + " marked in use: " + read);

            assertTrue(read.contains(I18n.t("ui.main.toolbar.headingLocalLayout") + gray), "the local heading is"
                + " still marked: " + read);

            assertTip(layouts, "ui.main.toolbar.switchToCSLayout", "ui.main.toolbar.tooltip.alreadyInUse");
            assertTip(layouts, "ui.main.toolbar.managePages", "layout.ui.errorEditingOnlySupportedForLocalFiles");

            assertFalse(read(submenu(layouts, "ui.main.toolbar.recentLayouts")).contains(
                I18n.t("ui.main.toolbar.showCurrentLayoutFolder")), "a Central Station layout offers a layout folder");
        }
        finally
        {
            putBack(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);

            ui.repaintPathLabel();

            Components.drain(4);

            menu("ui.main.toolbar.layouts");
        }
    }

    /**
     * Every Preferences checkbox and radio shows the stored choice and, ticked, stores it under the same preference as
     * before; ticked back, the choice is back (D47: headings, names and order changed, the stored preferences did
     * not).  Echo Sent Commands is left out: an unattended run never stores it.
     *
     * MUTATION: point Startup's Off at `AUTO_POWER_ON` 2 (Leave as It Was's value), and its row fails.
     */
    @Test(dataProvider = "preferences", timeOut = 60000)
    public void testAPreferenceStoresWhatItShows(String[] path, String preference, Object onValue) throws Exception
    {
        final JMenuItem item = itemAt(path);

        String name = String.join(" > ", path);

        String before = prefs().get(preference, null);

        try
        {
            if (item instanceof javax.swing.JRadioButtonMenuItem)
            {
                JMenuItem chosen = selectedIn((javax.swing.JRadioButtonMenuItem) item);

                // Shows what is stored: the radio of the stored value is the chosen one, the first for none.
                assertEquals(item.isSelected(), prefs().getInt(preference, 0) == (Integer) onValue, name
                    + " does not show the stored choice");

                Components.onTheEventThread(() -> { item.doClick(0); return null; });

                assertTrue(item.isSelected(), name + " is not chosen once clicked");
                assertEquals(prefs().get(preference, null), String.valueOf(onValue), name + " stored the wrong choice");

                if (chosen != null && chosen != item) Components.onTheEventThread(() -> { chosen.doClick(0); return null; });
            }
            else
            {
                // Shows what is stored, read with the window's own default for a choice never made.
                assertEquals(item.isSelected(), prefs().getBoolean(preference, (Boolean) onValue), name
                    + " does not show the stored choice");

                boolean was = item.isSelected();

                Components.onTheEventThread(() -> { item.doClick(0); return null; });

                assertEquals(prefs().getBoolean(preference, !was), !was, name + " did not store its new tick");

                Components.onTheEventThread(() -> { item.doClick(0); return null; });

                assertEquals(prefs().getBoolean(preference, was), was, name + " did not store its tick taken back");
            }
        }
        finally
        {
            putBack(preference, before);
        }
    }

    /**
     * A row: the item's path, the window's constant for its preference, and for a checkbox the default the window reads
     * it with, for a radio the value it stores.
     */
    @DataProvider
    public static Object[][] preferences() throws Exception
    {
        Object[][] rows = rows("preferences>winOnTop ONTOP_SETTING_PREF ONTOP_SETTING_DEFAULT;"
            + " preferences>rememberWindowLoc REMEMBER_WINDOW_LOCATION false;"
            + " preferences>hideRightClickTips RightClickTips.PREF false;"
            + " preferences>locControl>slidersChangeActiveLoc SLIDER_SETTING_PREF false;"
            + " preferences>locControl>showKeyboardControlHints SHOW_KEYBOARD_HINTS_PREF true;"
            + " preferences>locControl>activeLocInPopupTitles ACTIVE_LOC_IN_TITLE true;"
            + " preferences>locControl>showPagesAsTabs TABS_SETTING_PREF false;"
            + " preferences>locControl>showPageNumbers SHOW_PAGE_NUMBERS false;"
            + " preferences>locControl>qwertyKeyboard KEYBOARD_LAYOUT 0; preferences>locControl>qwertzKeyboard KEYBOARD_LAYOUT 1;"
            + " preferences>locControl>azertyKeyboard KEYBOARD_LAYOUT 2;"
            + " preferences>trackDiagramPreferences>showAddresses LAYOUT_SHOW_ADDRESSES false;"
            + " preferences>autolayout.ui.tabAutonomy>autolayout.ui.tabAutonomyEnhancedValidation ENHANCED_PATH_VALIDATION true;"
            + " preferences>autolayout.ui.tabAutonomy>autolayout.ui.menuShowInactiveLabels SHOW_INACTIVE_LABELS_PREF"
            + " SHOW_INACTIVE_LABELS_DEFAULT; preferences>autolayout.ui.tabAutonomy>greyStationLabels STATION_LABELS_GREY true;"
            + " preferences>autolayout.ui.tabAutonomy>restrictionArrows DIAGRAM_RESTRICTION_ARROWS true;"
            + " preferences>autolayout.ui.tabAutonomy>allowedDirections DIAGRAM_ALLOWED_DIRECTIONS false;"
            + " preferences>startup>powerOn AUTO_POWER_ON 0; preferences>startup>powerOff AUTO_POWER_ON 1;"
            + " preferences>startup>doNothing AUTO_POWER_ON 2; preferences>startup>loadAutonomy AUTO_LOAD_AUTONOMY true;"
            + " preferences>startup>checkForUpdates CHECK_FOR_UPDATES true");

        for (Object[] row : rows)
        {
            row[1] = "RightClickTips.PREF".equals(row[1]) ? RightClickTips.PREF : constant((String) row[1]);

            String value = (String) row[2];

            row[2] = value.matches("\\d+") ? (Object) Integer.valueOf(value)
                : value.equals("true") || value.equals("false") ? (Object) Boolean.valueOf(value) : constant(value);
        }

        return rows;
    }

    /** One row per ";": its path of keys (">"; "ui.main.toolbar." where a key has no dot), then its other words. */
    private static Object[][] rows(String table)
    {
        String[] lines = table.split(";");

        Object[][] rows = new Object[lines.length][];

        for (int i = 0; i < lines.length; i++)
        {
            String[] words = lines[i].trim().split(" ");
            String[] path = words[0].split(">");

            for (int k = 0; k < path.length; k++) if (!path[k].contains(".")) path[k] = "ui.main.toolbar." + path[k];

            rows[i] = new Object[words.length];
            rows[i][0] = path;

            System.arraycopy(words, 1, rows[i], 1, words.length - 1);
        }

        return rows;
    }

    /** A public constant of the window, by name. */
    private static Object constant(String name)
    {
        try
        {
            return TrainControlUI.class.getField(name).get(null);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("the window has no constant " + name, e);
        }
    }

    /**
     * Help > User Guide opens the Readme, the page About's link opens, and Help > Autonomy Guide the guide the Autonomy
     * menu opens (D48).
     *
     * MUTATION: swap the two URLs in `arrangeHelpMenu`.
     */
    @Test(timeOut = 60000)
    public void testTheGuidesOpenTheirPages() throws Exception
    {
        final List<String> opened = Collections.synchronizedList(new ArrayList<>());

        TrainControlUI.browserForTests = opened::add;

        try
        {
            Components.onTheEventThread(() ->
            {
                itemAt(new String[] {"ui.main.toolbar.help", "ui.main.userGuide"}).doClick(0);
                itemAt(new String[] {"ui.main.toolbar.help", "ui.menu.autonomyGuide"}).doClick(0);

                return null;
            });

            assertEquals(opened, Arrays.asList(TrainControlUI.USER_GUIDE_URL, TrainControlUI.README_URL),
                "the guides open the wrong pages");
        }
        finally
        {
            TrainControlUI.browserForTests = null;
        }
    }

    /**
     * Help > Check for Updates Now runs the start-up check: a newer release shows Download Update and says how to get
     * it; the release in use says TrainControl is up to date (D48).
     *
     * MUTATION: drop the message in `checkForUpdate` (`asked` ignored), and nothing is said.
     */
    @Test(timeOut = 120000)
    public void testCheckForUpdatesNowSaysWhatItFound() throws Exception
    {
        JMenuItem download = (JMenuItem) Reflect.get(TrainControlUI.class, ui, "downloadUpdateMenuItem");
        JMenuItem releases = (JMenuItem) Reflect.get(TrainControlUI.class, ui, "viewReleasesMenuItem");
        JMenu help = (JMenu) Reflect.get(TrainControlUI.class, ui, "helpMenu");

        String releasesWas = releases.getText();
        String helpWas = help.getText();
        String urlWas = TrainControlUI.UPDATE_URL;

        JMenuItem item = itemAt(new String[] {"ui.main.toolbar.help", "ui.main.checkForUpdatesNow"});

        try
        {
            TrainControlUI.releaseInfoForTests = () -> release("TrainControl v99.0.0");

            List<String> asked = choose(item, message -> null, 1);

            assertEquals(asked.get(0), I18n.f("ui.main.infoUpdateFound", "99.0.0", I18n.t("ui.main.downloadUpdate"),
                I18n.t("ui.main.toolbar.help")), "a newer release is not said");

            assertTrue(Components.waitFor(() -> Components.onTheEventThread(download::isVisible), PATIENCE_MS),
                "Download Update is not shown for a newer release");

            TrainControlUI.releaseInfoForTests = () -> release("TrainControl v" + MarklinControlStation.RAW_VERSION);

            asked = choose(item, message -> null, 1);

            assertEquals(asked.get(0), I18n.f("ui.main.infoUpToDate", MarklinControlStation.RAW_VERSION),
                "the release in use is not called up to date");
        }
        finally
        {
            TrainControlUI.releaseInfoForTests = null;
            TrainControlUI.UPDATE_URL = urlWas;

            Components.onTheEventThread(() ->
            {
                download.setVisible(false);
                releases.setText(releasesWas);
                help.setText(helpWas);

                return null;
            });
        }
    }

    /**
     * Help > About TrainControl opens the About box, as About / Readme did (D48).
     *
     * MUTATION: empty `aboutMenuItemActionPerformed`.
     */
    @Test(timeOut = 60000)
    public void testAboutTrainControlOpensTheAboutBox() throws Exception
    {
        JMenuItem item = itemAt(new String[] {"ui.main.toolbar.help", "ui.main.aboutReadme"});

        try (Answering answering = Answering.start(message -> null))
        {
            Components.press(item);

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "About TrainControl showed nothing");

            assertEquals(answering.questions().get(0).title, I18n.t("ui.about"), "About TrainControl showed another box");
        }
    }

    /**
     * The Layout tab's All button, after S, M and L, pops up every page, as Layouts > Pop-up all Layout Pages did
     * (D52); the menu item is gone (the census claim).
     *
     * MUTATION: wire the button to `layoutNewWindowActionPerformed` (one page) instead.
     */
    @Test(timeOut = 120000)
    public void testTheAllButtonPopsUpEveryPage() throws Exception
    {
        JButton all = Components.named(ui, "popUpAllButton", JButton.class);

        assertNotNull(all, "the Layout tab has no All button");

        assertEquals(all.getText(), I18n.t("ui.main.allLayoutsButton"));

        JButton large = (JButton) Reflect.get(TrainControlUI.class, ui, "layoutNewWindow");

        assertSame(all.getParent(), large.getParent(), "All is not beside L");

        int pages = ui.getModel().getLayoutList().size();

        assertTrue(pages > 1, "precondition: one page cannot tell All from one");

        try
        {
            Components.press(all);

            assertTrue(Components.waitFor(() -> popups().size() == pages, PATIENCE_MS), "All popped up " + popups().size()
                + " of " + pages + " pages");
        }
        finally
        {
            for (LayoutPopupUI each : popups()) SwingUtilities.invokeAndWait(each::dispose);
        }
    }

    // ------------------------------------------------------------------------------------------------

    private static List<LayoutPopupUI> popups()
    {
        return Components.onTheEventThread(() ->
        {
            List<LayoutPopupUI> found = new ArrayList<>();

            for (java.awt.Window each : java.awt.Window.getWindows())
            {
                if (each instanceof LayoutPopupUI && each.isShowing()) found.add((LayoutPopupUI) each);
            }

            return found;
        });
    }

    private static org.json.JSONObject release(String name)
    {
        return new org.json.JSONObject().put("name", name).put("html_url", "https://example.invalid/releases")
            .put("assets", new org.json.JSONArray().put(new org.json.JSONObject()
                .put("browser_download_url", "https://example.invalid/TrainControl.jar").put("size", 1)));
    }

    /** Clicks the item, answering by the rule, until this many questions were asked; their messages. */
    private static List<String> choose(JMenuItem item, java.util.function.Function<String, Answering.Answer> rule,
        int questions)
    {
        try (Answering answering = Answering.start(rule))
        {
            Components.press(item);

            assertTrue(answering.awaitAsked(questions, PATIENCE_MS), "\"" + item.getText() + "\" asked "
                + answering.asked());

            Components.settle();

            return answering.asked();
        }
    }

    private static void assertTip(JMenu menu, String itemKey, String whyKey)
    {
        JMenuItem item = Components.onTheEventThread(() -> child(menu, I18n.t(itemKey)));

        assertNotNull(item, "no " + itemKey);

        assertFalse(item.isEnabled(), "precondition: " + itemKey + " is live");

        assertEquals(plain(item.getToolTipText()), plain(I18n.t(whyKey)), itemKey + " grayed does not say why");
    }

    /** A tooltip's words, without the wrapping's tags and line breaks. */
    private static String plain(String tip)
    {
        return String.valueOf(tip).replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private static JMenuItem selectedIn(javax.swing.JRadioButtonMenuItem radio)
    {
        javax.swing.ButtonModel model = radio.getModel();

        javax.swing.ButtonGroup group = model instanceof javax.swing.DefaultButtonModel
            ? ((javax.swing.DefaultButtonModel) model).getGroup() : null;

        if (group == null) return null;

        for (java.util.Enumeration<javax.swing.AbstractButton> each = group.getElements(); each.hasMoreElements(); )
        {
            javax.swing.AbstractButton button = each.nextElement();

            if (button.isSelected()) return (JMenuItem) button;
        }

        return null;
    }

    /** A menu bar menu by its key, opened as a click opens it, so its listeners bring it up to date. */
    private static JMenu menu(String key)
    {
        JMenu found = Components.onTheEventThread(() ->
        {
            JMenuBar bar = ui.getJMenuBar();

            for (int i = 0; i < bar.getMenuCount(); i++)
            {
                JMenu each = bar.getMenu(i);

                if (each != null && each.getText().replace("*", "").equals(I18n.t(key)))
                {
                    each.setSelected(true);
                    each.setSelected(false);

                    return each;
                }
            }

            return null;
        });

        assertNotNull(found, "the menu bar has no " + key);

        return found;
    }

    private static JMenu submenu(JMenu menu, String key)
    {
        JMenuItem found = Components.onTheEventThread(() -> child(menu, I18n.t(key)));

        assertTrue(found instanceof JMenu, menu.getText() + " has no submenu " + key);

        return (JMenu) found;
    }

    /** The item down this path of keys from the menu bar, its menus opened on the way. */
    private static JMenuItem itemAt(String[] path)
    {
        JMenuItem at = menu(path[0]);

        for (int i = 1; i < path.length; i++)
        {
            final JMenuItem in = at;
            final String text = I18n.t(path[i]);

            at = Components.onTheEventThread(() -> child((JMenu) in, text));

            assertNotNull(at, "no " + path[i] + " under " + in.getText());
        }

        return at;
    }

    private static JMenuItem child(JMenu menu, String text)
    {
        for (Component each : menu.getMenuComponents())
        {
            if (each instanceof JMenuItem && each.isVisible() && text.equals(((JMenuItem) each).getText()))
            {
                return (JMenuItem) each;
            }
        }

        return null;
    }

    /** What a menu shows, top to bottom: "---" for a divider, " >" after a submenu, " [gray]" after a grayed item. */
    private static List<String> read(JMenu menu)
    {
        return Components.onTheEventThread(() ->
        {
            List<String> shown = new ArrayList<>();

            for (Component each : menu.getMenuComponents())
            {
                if (!each.isVisible()) continue;

                if (each instanceof javax.swing.JSeparator)
                {
                    shown.add("---");

                    continue;
                }

                JMenuItem item = (JMenuItem) each;

                shown.add(item.getText() + (item instanceof JMenu ? " >" : "") + (item.isEnabled() ? "" : " [gray]"));
            }

            return shown;
        });
    }

    private static List<String> keys(String... keys)
    {
        List<String> texts = new ArrayList<>();

        for (String key : keys) texts.add(key.equals("---") ? key : I18n.t(key));

        return texts;
    }

    private static Preferences prefs()
    {
        return TrainControlUI.getPrefs();
    }

    private static void putBack(String key, String value)
    {
        if (value == null) prefs().remove(key);
        else prefs().put(key, value);
    }
}
