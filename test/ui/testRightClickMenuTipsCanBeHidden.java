package ui;

import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.RightClickTips;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import support.AskingWindows;
import support.Components;
import support.EditorsOpen;
import support.Mouse;
import support.Reflect;

/**
 * Preferences > Hide Hover Tooltips (FR-168, FR-199): off, menus keep their tooltips and keys; on, every item of any
 * menu (the menu bar's and left-click pop-ups included, D37 on MT-910) shows no key, and every enabled one no tooltip; a
 * closed menu's items have their own tips and keys back; the keys act either way; the choice is stored; tooltips
 * outside menus are untouched.  The menus are opened as a user opens them: right-clicks, clicks on the menu bar and on
 * a button.
 */
public class testRightClickMenuTipsCanBeHidden
{
    private static final long PATIENCE_MS = 20000;

    private static final Pattern HOTKEY = Pattern.compile("(?:(?:Control|Alt)[+-]\\w+|Delete)");

    private static AskingWindows.Railway railway;
    private static TrainControlUI ui;
    private static LayoutPopupUI page;
    private static TileKey mainB;
    private static String wasStored;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        railway = AskingWindows.openRailway("live-snapshot", null);

        ui = railway.ui;

        Components.settle();

        mainB = AskingWindows.stationNamed(ui.getAutonomySession(), "BottomMainB");

        AskingWindows.showThePageOf(ui, mainB);

        page = AskingWindows.openThePage(ui);

        wasStored = TrainControlUI.getPrefs().get(RightClickTips.PREF, null);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            putBack();

            EditorsOpen.closeAll();

            AskingWindows.closeThePage(page);

            if (ui != null) SwingUtilities.invokeAndWait(ui::dispose);

            if (railway != null) railway.model.stop();
        }
        finally
        {
            if (railway != null) railway.sandbox.close();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void closeMenus()
    {
        Mouse.closeMenus();

        putBack();
    }

    private static void putBack()
    {
        if (wasStored == null) TrainControlUI.getPrefs().remove(RightClickTips.PREF);
        else TrainControlUI.getPrefs().put(RightClickTips.PREF, wasStored);
    }

    private static void hide(boolean on)
    {
        TrainControlUI.getPrefs().putBoolean(RightClickTips.PREF, on);
    }

    // ------------------------------------------------------------------------------------------ the menus

    private enum Door { LOC_BUTTON, MAIN_DIAGRAM, PAGE_DIAGRAM, EDITOR }

    /** The doors that need no editor: an open editor changes what the diagram menus offer. */
    private static final Door[] DIAGRAM_AND_BUTTON = {Door.LOC_BUTTON, Door.MAIN_DIAGRAM, Door.PAGE_DIAGRAM};

    /** Right-clicks a door as a user does and answers every item's tooltip by its text, then closes the menu. */
    private static Map<String, String> tipsOn(Door door) throws Exception
    {
        Component target;

        switch (door)
        {
            case LOC_BUTTON:
                target = support.SettingsWindow.key(ui, java.awt.event.KeyEvent.VK_A);

                Components.select(target);
                break;

            case MAIN_DIAGRAM:
                Components.select((Component) field("LayoutList"));

                target = AskingWindows.awaitTileIn(ui, ui, mainB);
                break;

            case PAGE_DIAGRAM:
                target = AskingWindows.awaitTileIn(ui, page, mainB);
                break;

            default:
                target = anEditorTile();
                break;
        }

        assertNotNull(target, "precondition: nothing to right-click for " + door);

        JPopupMenu menu = Mouse.rightClick(target, PATIENCE_MS);

        assertNotNull(menu, "a right-click for " + door + " opened no menu");

        Map<String, String> tips = tipsOf(menu);

        Mouse.closeMenus();

        Components.settle();

        return tips;
    }

    /** The items the last read found grayed: they keep their tips, which say why. */
    private static final java.util.Set<String> grayed = new java.util.HashSet<>();

    /** The keys the last read found shown on the items' right, by item: ticked, the setting hides them (FR-199). */
    private static final Map<String, javax.swing.KeyStroke> shownKeys = new java.util.HashMap<>();

    private static void collect(Component[] items, Map<String, String> into)
    {
        for (Component each : items)
        {
            if (!(each instanceof JMenuItem)) continue;

            JMenuItem item = (JMenuItem) each;

            into.put(item.getText(), item.getToolTipText());

            if (!item.isEnabled()) grayed.add(item.getText());

            if (item.getAccelerator() != null) shownKeys.put(item.getText(), item.getAccelerator());

            if (item instanceof JMenu) collect(((JMenu) item).getMenuComponents(), into);
        }
    }

    private static Component anEditorTile() throws Exception
    {
        if (EditorsOpen.editors().isEmpty())
        {
            Components.press(Components.withText(ui, I18n.t("ui.main.toolbar.managePages")));

            assertTrue(Components.waitFor(() -> !EditorsOpen.editors().isEmpty(), PATIENCE_MS),
                "precondition: Manage Pages opened no editor");

            Components.settle();
        }

        LayoutEditor editor = EditorsOpen.editors().get(0);

        assertTrue(Components.waitFor(() -> Components.find(editor, LayoutLabel.class, Component::isShowing) != null,
            PATIENCE_MS), "precondition: the editor draws no squares");

        return Components.find(editor, LayoutLabel.class, Component::isShowing);
    }

    /** The same items as before hiding, each showing what hiding leaves: a grayed one its reason, any other nothing. */
    private static void assertOnlyReasonsLeft(String which, Map<String, String> before, java.util.Set<String> gray,
        Map<String, String> after)
    {
        assertEquals(after.keySet(), before.keySet(), which + " offers other items on the second opening");

        for (Map.Entry<String, String> item : before.entrySet())
        {
            assertEquals(after.get(item.getKey()), gray.contains(item.getKey()) ? item.getValue() : null, "\""
                + item.getKey() + "\" was \"" + item.getValue() + "\" and now shows the wrong thing");
        }
    }

    private static boolean isHotkey(String tip)
    {
        return tip != null && HOTKEY.matcher(tip).matches();
    }

    // ---------------------------------------------------------------------------------------------- claims

    /**
     * Off, which is how it starts, every right-click menu keeps its tooltips: the diagram menus' sentences.  The
     * locomotive button's menu shows its keys on the items' right, so no tooltip there names one.
     *
     * MUTATION: strip the tips even when the setting is off.
     */
    @Test
    public void testOffTheMenusKeepTheirTips() throws Exception
    {
        TrainControlUI.getPrefs().remove(RightClickTips.PREF);

        assertFalse(org.traincontrol.gui.RightClickTips.hidden(), "the setting is not off by default");

        for (Door door : DIAGRAM_AND_BUTTON)
        {
            shownKeys.clear();

            Map<String, String> tips = tipsOn(door);

            if (door == Door.LOC_BUTTON)
            {
                assertFalse(shownKeys.isEmpty(), door + ": no item shows its key");
                assertFalse(tips.values().stream().anyMatch(t -> isHotkey(t)), door + ": a tooltip names a key: " + tips);
            }
            else
            {
                assertTrue(tips.values().stream().anyMatch(t -> t != null && !isHotkey(t)), door + ": no sentence"
                    + " tooltip: " + tips);
            }
        }
    }

    /**
     * On, no enabled item of any right-click menu has a tooltip, and the locomotive button's menu shows no key on its
     * items' right (FR-199).
     *
     * MUTATION: skip the strip of the keys (the key menu shows its keys), or of the tips (the sentences stay).
     */
    @Test
    public void testNoTipOrKeyRemainsWhenHidden() throws Exception
    {
        for (Door door : DIAGRAM_AND_BUTTON)
        {
            hide(false);

            shownKeys.clear();

            Map<String, String> before = tipsOn(door);

            Map<String, javax.swing.KeyStroke> keysBefore = new java.util.HashMap<>(shownKeys);

            java.util.Set<String> gray = new java.util.HashSet<>(grayed);

            hide(true);

            shownKeys.clear();

            Map<String, String> after = tipsOn(door);

            assertEquals(after.keySet(), before.keySet(), door + ": the menu offers other items on the second opening");

            int sentences = 0;

            for (Map.Entry<String, String> item : before.entrySet())
            {
                String expected = gray.contains(item.getKey()) ? item.getValue() : null;

                assertEquals(after.get(item.getKey()), expected, door + ": \"" + item.getKey() + "\" was \""
                    + item.getValue() + "\" and now shows the wrong thing");

                if (expected == null && item.getValue() != null) sentences++;
            }

            if (door == Door.LOC_BUTTON)
            {
                assertFalse(keysBefore.isEmpty(), door + ": no item shows a key to prove");
                assertEquals(shownKeys, java.util.Collections.emptyMap(), door + ": the menu still shows keys");
            }
            else
            {
                assertTrue(sentences > 0, door + ": no sentence tooltip to prove hidden: " + before);
            }
        }
    }

    /**
     * The track editor's right-click menu: hidden, its items show no key on their right and its enabled items no tip;
     * off again, the very keys and tips are back.
     *
     * MUTATION: skip the strip of the keys (the keys stay while hidden).
     */
    @Test
    public void testTheTrackEditorsMenuFollowsTheSetting() throws Exception
    {
        try
        {
            hide(false);

            shownKeys.clear();

            Map<String, String> before = tipsOn(Door.EDITOR);

            Map<String, javax.swing.KeyStroke> keysBefore = new java.util.HashMap<>(shownKeys);

            java.util.Set<String> gray = new java.util.HashSet<>(grayed);

            assertFalse(keysBefore.isEmpty(), "the editor menu shows no key");

            hide(true);

            shownKeys.clear();

            Map<String, String> hidden = tipsOn(Door.EDITOR);

            assertOnlyReasonsLeft("the editor menu", before, gray, hidden);

            assertEquals(shownKeys, java.util.Collections.emptyMap(), "hidden, the editor menu still shows keys");

            hide(false);

            shownKeys.clear();

            assertEquals(tipsOn(Door.EDITOR), before, "the editor menu's tips did not come back");

            assertEquals(shownKeys, keysBefore, "the editor menu's keys did not come back");
        }
        finally
        {
            closeEditors();
        }
    }

    /**
     * The menu bar's Locomotives menu, built at start-up and opened by a click on its title: ticked through the
     * Preferences checkbox, its items show no key; cleared, they show the very keys the window's shortcut table acts on,
     * without a restart (FR-199).
     *
     * MUTATION: skip the strip of the keys (the keys stay while ticked).
     */
    @Test
    public void testTheMenuBarShowsItsKeysOnlyWhileTipsShow() throws Exception
    {
        AbstractButton tick = Components.withText(ui, I18n.t("ui.main.toolbar.hideRightClickTips"));

        JMenu locomotives = (JMenu) field("locomotiveMenu");

        Map<String, String> rows = new LinkedHashMap<>();

        rows.put("quickFindMenuItem", "find a locomotive");
        rows.put("viewDatabaseMenuItem", "choose a locomotive");
        rows.put("addLocomotiveMenuItem", "add a locomotive");

        try
        {
            hide(false);

            if (tick.isSelected()) SwingUtilities.invokeAndWait(tick::doClick);

            keysOnTheBar(locomotives, rows, true);

            SwingUtilities.invokeAndWait(tick::doClick);

            keysOnTheBar(locomotives, rows, false);

            SwingUtilities.invokeAndWait(tick::doClick);

            keysOnTheBar(locomotives, rows, true);
        }
        finally
        {
            if (tick.isSelected()) SwingUtilities.invokeAndWait(tick::doClick);
        }
    }

    /** Opens the bar's menu by a click, checks each row's item shows its table key or none, and closes it. */
    private static void keysOnTheBar(JMenu title, Map<String, String> rows, boolean shown) throws Exception
    {
        Mouse.click(title);

        try
        {
            assertTrue(Components.waitFor(title::isPopupMenuVisible, PATIENCE_MS), "the menu did not open");

            Components.settle();

            for (Map.Entry<String, String> row : rows.entrySet())
            {
                JMenuItem item = (JMenuItem) field(row.getKey());

                assertEquals(Components.onTheEventThread(item::getAccelerator),
                    shown ? TrainControlUI.KeyShortcuts.named(row.getValue()).stroke() : null,
                    "\"" + item.getText() + "\" shows the wrong key " + (shown ? "with tips shown" : "while ticked"));
            }
        }
        finally
        {
            Mouse.closeMenus();

            Components.settle();
        }
    }

    /**
     * Ticked, a menu bar menu the keyboard opens (Right or Left arrow across an open bar sets the menu and its pop-up in
     * one step, so the pop-up is sized with its keys first) is as wide as its items without the key column (FR-199).
     *
     * MUTATION: leave out `RightClickTips.apply`'s `pack()` of a showing pop-up, and it keeps the keys' width.
     */
    @Test(timeOut = 120000)
    public void testAMenuTheKeyboardOpensIsSizedWithoutItsKeys() throws Exception
    {
        // A stand-in bar whose items are short, so the key column is most of the width
        javax.swing.JFrame[] frame = new javax.swing.JFrame[1];
        JMenu menu = new JMenu("Stand-in");
        JMenuItem keyed = new JMenuItem("Go");

        hide(true);

        try
        {
            Components.onTheEventThread(() ->
            {
                TrainControlUI.showShortcut(keyed, javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1,
                    java.awt.event.InputEvent.CTRL_DOWN_MASK));

                menu.add(keyed);

                javax.swing.JMenuBar bar = new javax.swing.JMenuBar();

                bar.add(menu);

                frame[0] = new javax.swing.JFrame("stand-in");
                frame[0].setJMenuBar(bar);
                frame[0].setSize(300, 200);
                frame[0].setVisible(true);

                return null;
            });

            Components.settle();

            Components.onTheEventThread(() ->
            {
                javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(new javax.swing.MenuElement[] {
                    frame[0].getJMenuBar(), menu, menu.getPopupMenu()});

                return null;
            });

            assertTrue(Components.waitFor(menu::isPopupMenuVisible, PATIENCE_MS), "the menu did not open");

            Components.settle();

            assertNull(Components.onTheEventThread(keyed::getAccelerator), "precondition: the menu shows its key");

            int width = Components.onTheEventThread(() -> menu.getPopupMenu().getWidth());
            int wanted = Components.onTheEventThread(() -> menu.getPopupMenu().getPreferredSize().width);

            assertEquals(width, wanted, "the menu the keyboard opened is as wide as its key made it");
        }
        finally
        {
            Mouse.closeMenus();

            if (frame[0] != null) Components.onTheEventThread(() -> { frame[0].dispose(); return null; });

            Components.settle();
        }
    }

    /**
     * Ticked, with a key's menu open and showing no keys, Control+C still copies the key's train: only the display
     * changes, never what the keys do.
     *
     * MUTATION: the setting turns the keys off with their hints (`row.run` skipped while it is on).
     */
    @Test
    public void testAKeyActsWhileItsHintIsHidden() throws Exception
    {
        Component keyboard = (Component) field("KeyboardTab");

        javax.swing.JButton s = support.SettingsWindow.key(ui, java.awt.event.KeyEvent.VK_S);

        javax.swing.JButton a = support.SettingsWindow.key(ui, java.awt.event.KeyEvent.VK_A);

        // S copied first, so that a copy of A is seen to happen
        support.Keys.press(keyboard, java.awt.event.KeyEvent.VK_S);
        support.Keys.press(keyboard, java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK);

        assertSame(field("copyTargetButton"), s, "precondition: Control+C did not copy S");

        hide(true);

        support.Keys.press(keyboard, java.awt.event.KeyEvent.VK_A);

        Components.select(a);

        JPopupMenu menu = Mouse.rightClick(a, PATIENCE_MS);

        assertNotNull(menu, "the right-click opened no menu");

        shownKeys.clear();

        tipsOf(menu);

        assertEquals(shownKeys, java.util.Collections.emptyMap(), "precondition: the key's menu shows keys");

        support.Keys.press(keyboard, java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK);

        assertSame(field("copyTargetButton"), a, "Control+C did not copy A while its menu hid the key");
    }

    /**
     * The checkbox in Preferences writes the choice, and it starts off.
     *
     * MUTATION: the item's action leaves the preference alone.
     */
    @Test
    public void testTheSettingIsStoredByItsCheckbox() throws Exception
    {
        TrainControlUI.getPrefs().remove(RightClickTips.PREF);

        AbstractButton item = Components.withText(ui, I18n.t("ui.main.toolbar.hideRightClickTips"));

        assertNotNull(item, "Preferences has no Hide Right-Click Menu Tips");

        assertFalse(item.isSelected(), "the setting does not start off");

        SwingUtilities.invokeAndWait(item::doClick);

        assertTrue(TrainControlUI.getPrefs().getBoolean(RightClickTips.PREF, false),
            "ticking the item stored nothing");

        assertTrue(org.traincontrol.gui.RightClickTips.hidden(), "ticked, the setting is not on");

        SwingUtilities.invokeAndWait(item::doClick);

        assertFalse(TrainControlUI.getPrefs().getBoolean(RightClickTips.PREF, true),
            "clearing the item stored nothing");
    }

    /**
     * On, a button's tooltip is as it was while right-click menus and a menu bar menu are open and after they close.
     *
     * MUTATION: strip the tip of every component in an open menu's window, not only the menu's items.
     */
    @Test
    public void testTipsOutsideMenusAreUntouched() throws Exception
    {
        hide(false);

        AbstractButton button = (AbstractButton) field("OnButton");

        String buttonTip = button.getToolTipText();

        assertNotNull(buttonTip, "precondition: the button has no tooltip");

        hide(true);

        tipsOn(Door.LOC_BUTTON);

        tipsOn(Door.MAIN_DIAGRAM);

        // The Preferences menu, opened as a click on it does.
        JMenu preferences = (JMenu) field("interfaceMenu");

        SwingUtilities.invokeAndWait(preferences::doClick);

        Components.settle();

        assertEquals(button.getToolTipText(), buttonTip, "a button lost its tooltip while a menu was open");

        Mouse.closeMenus();

        assertEquals(button.getToolTipText(), buttonTip, "a button lost its tooltip");
    }

    /**
     * As the owner tests it (D37 on MT-910): ticked through the Preferences checkbox, the track diagram's autonomy menu
     * over a station with a train and the menu bar's Preferences menu, each hovered item by item through its window,
     * show only grayed items' reasons; cleared, both show every tip they showed before, without a restart.
     *
     * MUTATION: hide the tips in right-click menus only (the Preferences menu keeps its sentences while ticked).
     */
    @Test(timeOut = 300000)
    public void testTheDiagramAndTheMenuBarFollowTheCheckbox() throws Exception
    {
        AbstractButton tick = Components.withText(ui, I18n.t("ui.main.toolbar.hideRightClickTips"));

        JMenu preferences = (JMenu) field("interfaceMenu");

        ToolTipManager tips = ToolTipManager.sharedInstance();

        int delay = tips.getInitialDelay();

        try
        {
            hide(false);

            if (tick.isSelected()) SwingUtilities.invokeAndWait(tick::doClick);

            tips.setInitialDelay(0);

            Components.select((Component) field("LayoutList"));

            Component station = AskingWindows.awaitTileIn(ui, ui, aStationWithATrain());

            Map<String, String> diagram = hovered(station);

            java.util.Set<String> diagramGray = new java.util.HashSet<>(grayed);

            Map<String, String> bar = hoveredBar(preferences);

            java.util.Set<String> barGray = new java.util.HashSet<>(grayed);

            for (Map<String, String> before : java.util.Arrays.asList(diagram, bar))
            {
                assertTrue(before.values().stream().anyMatch(t -> t != null && !isHotkey(t)),
                    "precondition: no item shows a sentence: " + before);
            }

            SwingUtilities.invokeAndWait(tick::doClick);

            assertOnlyReasonsLeft("hovered while ticked, the diagram's menu", diagram, diagramGray, hovered(station));

            assertOnlyReasonsLeft("hovered while ticked, the Preferences menu", bar, barGray, hoveredBar(preferences));

            SwingUtilities.invokeAndWait(tick::doClick);

            assertEquals(hovered(station), diagram, "cleared, the diagram menu's hovered tips are not as they were");

            assertEquals(hoveredBar(preferences), bar, "cleared, the Preferences menu's hovered tips are not as they were");
        }
        finally
        {
            tips.setInitialDelay(delay);

            if (tick.isSelected()) SwingUtilities.invokeAndWait(tick::doClick);
        }
    }

    /** A station in the main window's diagram with a train standing on it, whose menu has a sentence to hide. */
    private static TileKey aStationWithATrain()
    {
        org.traincontrol.automation.Layout layout = ui.getModel().getAutoLayoutIfLoaded();

        for (TileKey key : ui.getAutonomySession().getStore().getNamedTiles())
        {
            String name = ui.getAutonomySession().getStore().getPointName(key);

            for (org.traincontrol.automation.Point point : layout.getPoints())
            {
                if (name.equals(point.getName()) && point.getCurrentLocomotive() != null
                    && AskingWindows.tileIn(ui, ui, key) != null) return key;
            }
        }

        throw new AssertionError("precondition: no train stands at a station drawn in the main window");
    }

    /** {@link #hovered}, for a menu of the menu bar opened by a click on its title. */
    private static Map<String, String> hoveredBar(JMenu title) throws Exception
    {
        return hovered(() ->
        {
            Mouse.click(title);

            return Components.waitFor(title::isPopupMenuVisible, PATIENCE_MS) ? title.getPopupMenu() : null;
        });
    }

    /**
     * Every item of the track diagram's Bulk Tools submenu follows the setting: ticked, an enabled item shows no
     * sentence and a grayed one keeps its reason; cleared, every tip is back as it was.
     *
     * MUTATION: build the Bulk Tools submenu after the tips are taken off, and its items keep their sentences.
     */
    @Test
    public void testEveryBulkToolsItemFollowsTheSetting() throws Exception
    {
        String[] keys = {"autosetup.ui.menuMassAssignLengths", "autosetup.ui.menuMassAssignMaxTrainLengths",
            "autosetup.ui.menuMassAssignTrainLengthsCounted", "autolayout.ui.menuClearAllTrackLengths",
            "autolayout.ui.menuClearAllMaxTrainLengths", "autolayout.ui.menuAllowEveryPath",
            "autolayout.ui.menuHomeEveryTrainWhereItStands", "autolayout.ui.menuPlaceEveryTrainAtHome",
            "autolayout.ui.menuClearAllHomeLocomotives", "autolayout.ui.menuClearLocomotives",
            "autosetup.ui.menuActivateAllTrains", "autosetup.ui.menuDeactivateAllTrains"};

        try
        {
            hide(false);

            grayed.clear();

            Map<String, String> off = tipsOn(Door.MAIN_DIAGRAM);

            java.util.Set<String> gray = new java.util.HashSet<>(grayed);

            hide(true);

            Map<String, String> on = tipsOn(Door.MAIN_DIAGRAM);

            int enabled = 0;
            int grayedOnes = 0;

            for (String key : keys)
            {
                String[] around = I18n.f(key, "@@").split("@@", -1);

                for (String label : off.keySet())
                {
                    // The diagram's menu has no page, so the two Mass Assign lengths are not on it
                    if (label == null || !label.startsWith(around[0]) || !label.endsWith(around[around.length - 1])) continue;

                    boolean isGray = gray.contains(label);

                    if (isGray) grayedOnes++;
                    else enabled++;

                    assertEquals(on.get(label), isGray ? off.get(label) : null,
                        "\"" + label + "\" does not follow the setting while it is ticked");
                }
            }

            assertTrue(enabled > 0 && grayedOnes > 0, "precondition: the diagram's Bulk Tools has " + enabled
                + " enabled and " + grayedOnes + " grayed items");

            hide(false);

            assertEquals(tipsOn(Door.MAIN_DIAGRAM), off, "cleared, the Bulk Tools tips are not as they were");
        }
        finally
        {
            hide(false);
        }
    }

    /**
     * Ticked, a closed menu's items have their own tips and keys back at once, not only when it next opens: code that
     * saves an item's tooltip, as graying a Layout menu item for a run does, must never save a hidden one.
     *
     * MUTATION: put the tips back on the next opening instead of at the close.
     */
    @Test
    public void testAClosedMenuHasItsOwnTipsBack() throws Exception
    {
        javax.swing.JFrame[] frame = new javax.swing.JFrame[1];

        try
        {
            hide(true);

            JPopupMenu menu = openedByALeftClick(frame);

            assertNull(tipsOf(menu).get("Sentence"), "precondition: the open menu kept its sentence");

            JMenuItem accelerated = AskingWindows.itemOf(menu, "Accelerated");

            assertNull(accelerated.getAccelerator(), "precondition: the open menu kept its key");

            Mouse.closeMenus();

            Components.settle();

            Map<String, String> tips = tipsOf(menu);

            assertEquals(tips.get("Sentence"), "An explanation.", "closed, an item has not got its own tip back");
            assertEquals(tips.get("Grayed"), "Close the editor first.", "closed, a grayed item lost its tip");
            assertEquals(tips.get("Keyed"), "Control+Q", "closed, a hotkey item has not got its own tip back");
            assertEquals(accelerated.getAccelerator(), CONTROL_W, "closed, an item has not got its own key back");
        }
        finally
        {
            Mouse.closeMenus();

            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    /** The stand-in's pop-up, opened by a left-click on its button. */
    private static JPopupMenu openedByALeftClick(javax.swing.JFrame[] frame) throws Exception
    {
        javax.swing.JButton button = standIn(false, frame);

        Mouse.enter(button);
        Mouse.click(button);

        assertTrue(Components.waitFor(() -> Components.popupShowing() != null, PATIENCE_MS),
            "the left-click opened no pop-up");

        return Components.popupShowing();
    }

    private static Map<String, String> tipsOf(JPopupMenu menu)
    {
        return Components.onTheEventThread(() ->
        {
            Map<String, String> read = new LinkedHashMap<>();

            collect(menu.getComponents(), read);

            return read;
        });
    }

    private static final javax.swing.KeyStroke CONTROL_W = javax.swing.KeyStroke.getKeyStroke(
        java.awt.event.KeyEvent.VK_W, java.awt.event.InputEvent.CTRL_DOWN_MASK);

    /** A window with one button that opens a pop-up of four items, on a right-click or on a left-click as asked. */
    private static javax.swing.JButton standIn(boolean onRightClick, javax.swing.JFrame[] frame) throws Exception
    {
        org.traincontrol.gui.TrainControlUI.installLookAndFeel();

        javax.swing.JButton button = new javax.swing.JButton("stand-in");

        Components.onTheEventThread(() ->
        {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem sentence = new JMenuItem("Sentence");
            sentence.setToolTipText("An explanation.");
            menu.add(sentence);

            JMenuItem grayed = new JMenuItem("Grayed");
            grayed.setToolTipText("Close the editor first.");
            grayed.setEnabled(false);
            menu.add(grayed);

            JMenuItem key = new JMenuItem("Keyed");
            key.setToolTipText("Control+Q");
            menu.add(key);

            JMenuItem accelerated = new JMenuItem("Accelerated");
            accelerated.setToolTipText("Closes it.  Shortcut: Control+Q.");
            accelerated.setAccelerator(CONTROL_W);
            menu.add(accelerated);

            if (onRightClick)
            {
                button.addMouseListener(new java.awt.event.MouseAdapter()
                {
                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e)
                    {
                        if (e.isPopupTrigger()) menu.show(button, e.getX(), e.getY());
                    }
                });
            }
            else button.addActionListener(e -> menu.show(button, 0, button.getHeight()));

            frame[0] = new javax.swing.JFrame("stand-in");
            frame[0].add(button);
            frame[0].pack();
            frame[0].setVisible(true);

            return null;
        });

        Components.settle();

        return button;
    }

    /**
     * A grayed item keeps its tooltip, which says why it is grayed; an enabled item beside it loses its sentence, and
     * one whose tip names a key loses that too.
     *
     * MUTATION: strip grayed items too.
     */
    @Test
    public void testAGrayedItemKeepsItsTip() throws Exception
    {
        javax.swing.JFrame[] frame = new javax.swing.JFrame[1];

        try
        {
            hide(true);

            JPopupMenu menu = Mouse.rightClick(standIn(true, frame), PATIENCE_MS);

            assertNotNull(menu, "the right-click opened no menu");

            Map<String, String> tips = tipsOf(menu);

            assertEquals(tips.get("Grayed"), "Close the editor first.", "a grayed item lost its reason");
            assertNull(tips.get("Sentence"), "an enabled item kept its sentence");
            assertNull(tips.get("Keyed"), "an item kept a tip that names a key");
        }
        finally
        {
            Mouse.closeMenus();

            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    /**
     * An item with a shortcut of its own (an accelerator) shows neither the shortcut nor its tip.
     *
     * MUTATION: skip the strip of the keys.
     */
    @Test
    public void testAnItemsOwnShortcutGoesWithItsTip() throws Exception
    {
        javax.swing.JFrame[] frame = new javax.swing.JFrame[1];

        try
        {
            hide(true);

            JPopupMenu menu = Mouse.rightClick(standIn(true, frame), PATIENCE_MS);

            assertNotNull(menu, "the right-click opened no menu");

            assertNull(tipsOf(menu).get("Accelerated"), "the item kept its tip");

            assertNull(AskingWindows.itemOf(menu, "Accelerated").getAccelerator(), "the item still shows its shortcut");
        }
        finally
        {
            Mouse.closeMenus();

            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    /**
     * Ticked, the autonomy editor's menu bar, hovered through its window, shows Find a Point... with no key and no tip
     * (its tip names the key).
     *
     * MUTATION: skip the strip of the keys (Ctrl+F shows).
     */
    @Test(timeOut = 300000)
    public void testTheEditorsMenuBarHidesItsKey() throws Exception
    {
        ToolTipManager tips = ToolTipManager.sharedInstance();

        int delay = tips.getInitialDelay();

        try
        {
            hide(true);

            tips.setInitialDelay(0);

            LayoutEditor autonomy = openedEditor(Boolean.TRUE);

            shownKeys.clear();

            Map<String, String> shown = hoveredBar(Components.onTheEventThread(() -> autonomy.getJMenuBar().getMenu(0)));

            String find = I18n.t("layout.ui.menuFindAPoint");

            assertTrue(shown.containsKey(find), "precondition: no Find a Point...: " + shown);

            assertNull(shown.get(find), "Find a Point... shows a tip");

            assertEquals(shownKeys, java.util.Collections.emptyMap(), "the editor's menu shows keys");
        }
        finally
        {
            tips.setInitialDelay(delay);

            closeEditors();
        }
    }

    /** Closes every editor by its close box, as the user does: disposed, the main window would still count it open. */
    private static void closeEditors()
    {
        for (LayoutEditor editor : EditorsOpen.editors())
        {
            SwingUtilities.invokeLater(() ->
                editor.dispatchEvent(new java.awt.event.WindowEvent(editor, java.awt.event.WindowEvent.WINDOW_CLOSING)));
        }

        // Until the main window would open one again: its menus gray what an editor holds, and give it back late.
        assertTrue(Components.waitFor(() -> EditorsOpen.editors().isEmpty() && ui.whyAutonomyEditorCannotOpen() == null,
            PATIENCE_MS), "an editor did not close: " + EditorsOpen.titles() + " " + ui.whyAutonomyEditorCannotOpen());

        Components.settle();
    }

    /** An editor on the page of BottomMainB, in the mode asked for, once it draws its squares. */
    private static LayoutEditor openedEditor(Boolean autonomy) throws Exception
    {
        closeEditors();

        String page = mainB.getPage();

        Components.onTheEventThread(() ->
        {
            ui.openLayoutEditor(page, autonomy, null);

            return null;
        });

        assertTrue(Components.waitFor(() ->
        {
            LayoutEditor editor = EditorsOpen.editorOn(page);

            return editor != null && editor.getJMenuBar() != null && editor.isAutonomyMode() == autonomy
                && Components.find(editor, LayoutLabel.class, Component::isShowing) != null;
        }, PATIENCE_MS), "precondition: no editor opened on " + page + ": " + EditorsOpen.titles());

        Components.settle();

        return EditorsOpen.editorOn(page);
    }

    /**
     * A pop-up opened by a left-click, such as a button's drop-down, hides its tips as a right-click menu does (D37 on
     * MT-910): a grayed item keeps its reason, and an item whose tip names a key shows nothing.
     *
     * MUTATION: hide the tips in right-click menus only.
     */
    @Test
    public void testALeftClickPopupHidesItsTipsToo() throws Exception
    {
        javax.swing.JFrame[] frame = new javax.swing.JFrame[1];

        try
        {
            hide(true);

            JPopupMenu menu = openedByALeftClick(frame);

            Map<String, String> tips = tipsOf(menu);

            assertNull(tips.get("Sentence"), "a left-click pop-up kept a sentence");
            assertEquals(tips.get("Grayed"), "Close the editor first.", "a left-click pop-up's grayed item lost its reason");
            assertNull(tips.get("Keyed"), "a left-click pop-up's item kept a tip that names a key");
        }
        finally
        {
            Mouse.closeMenus();

            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    /**
     * The setting sits under Remember Window Locations, above the divider (MT-910).
     *
     * MUTATION: add the item at the end of Preferences again.
     */
    @Test
    public void testTheSettingSitsUnderRememberWindowLocations() throws Exception
    {
        JMenu preferences = (JMenu) field("interfaceMenu");

        List<Component> items = Components.onTheEventThread(() ->
            java.util.Arrays.asList(preferences.getMenuComponents()));

        int remember = items.indexOf(Components.withText(preferences.getPopupMenu(),
            I18n.t("ui.main.toolbar.rememberWindowLoc")));

        int hide = items.indexOf(Components.withText(preferences.getPopupMenu(),
            I18n.t("ui.main.toolbar.hideRightClickTips")));

        assertTrue(remember >= 0 && hide >= 0, "precondition: Preferences lacks one of the two items");

        assertEquals(hide, remember + 1, "the setting is not directly under Remember Window Locations");

        assertTrue(items.get(hide + 1) instanceof JPopupMenu.Separator, "the divider does not follow the setting");
    }

    /**
     * As the owner tests it: the Preferences checkbox ticked, then cleared, with the track editor's right-click menu
     * hovered item by item through its window each time, its Bulk Tools submenu opened by hovering, and the tip the
     * tooltip manager shows read off it.  Ticked, an enabled item shows nothing, a grayed one its reason; cleared, every
     * item shows what it showed before.
     *
     * MUTATION: skip the strip (the sentences show while ticked).
     *
     * Leaves the editor open for testTheTrackEditorsMenuFollowsTheSetting, later by name: a disposed editor is not
     * reopened.
     */
    @Test(timeOut = 300000)
    public void testTheHoveredTipsFollowTheCheckbox() throws Exception
    {
        AbstractButton tick = Components.withText(ui, I18n.t("ui.main.toolbar.hideRightClickTips"));

        ToolTipManager tips = ToolTipManager.sharedInstance();

        int delay = tips.getInitialDelay();

        try
        {
            hide(false);

            if (tick.isSelected()) SwingUtilities.invokeAndWait(tick::doClick);

            tips.setInitialDelay(0);

            Component tile = anEditorTile();

            Map<String, String> before = hovered(tile);

            assertTrue(before.keySet().stream().anyMatch(k -> k.contains(" > ")), "precondition: no submenu was hovered: "
                + before);

            assertTrue(before.values().stream().anyMatch(t -> t != null && !isHotkey(t)),
                "precondition: no item shows a sentence: " + before);

            java.util.Set<String> gray = new java.util.HashSet<>(grayed);

            SwingUtilities.invokeAndWait(tick::doClick);

            Map<String, String> ticked = hovered(tile);

            assertOnlyReasonsLeft("hovered while ticked, the menu", before, gray, ticked);

            SwingUtilities.invokeAndWait(tick::doClick);

            assertEquals(hovered(tile), before, "cleared, the hovered tips are not as they were");
        }
        finally
        {
            tips.setInitialDelay(delay);

            if (tick.isSelected()) SwingUtilities.invokeAndWait(tick::doClick);
        }
    }

    /** {@link #hovered(java.util.concurrent.Callable)}, for the menu a right-click on the square opens. */
    private static Map<String, String> hovered(Component target) throws Exception
    {
        return hovered(() -> Mouse.rightClick(target, PATIENCE_MS));
    }

    /**
     * Opens the menu, then moves the pointer over each item through the window it is drawn in, as a real pointer does,
     * opening submenus by hovering them; answers the tip the tooltip manager shows over each item.
     */
    private static Map<String, String> hovered(java.util.concurrent.Callable<JPopupMenu> open) throws Exception
    {
        // A window taking the focus, such as another test run's, closes the menu under the pointer: walk again.
        for (int attempt = 1; ; attempt++)
        {
            grayed.clear();

            JPopupMenu menu = open.call();

            Map<String, String> shown = new LinkedHashMap<>();

            try
            {
                // A walk that left a submenu unopened is partial: the menu is walked again, not compared as it is
                if (menu != null && hoverAll("", menu, shown) && menu.isShowing()) return shown;

                assertTrue(attempt < 5, "the menu closed before every item was hovered, five times: " + shown);
            }
            finally
            {
                Mouse.closeMenus();

                Components.settle();
            }
        }
    }

    /** Hovers each item; whether every submenu opened under the pointer, so that the walk is whole. */
    private static boolean hoverAll(String path, JPopupMenu menu, Map<String, String> into) throws Exception
    {
        for (Component each : Components.onTheEventThread(menu::getComponents))
        {
            if (!(each instanceof JMenuItem) || !each.isShowing()) continue;

            JMenuItem item = (JMenuItem) each;

            if (!item.isEnabled()) grayed.add(path + item.getText());

            into.put(path + item.getText(), tipShownOver(item));

            javax.swing.KeyStroke key = Components.onTheEventThread(item::getAccelerator);

            if (key != null) shownKeys.put(path + item.getText(), key);

            if (item instanceof JMenu)
            {
                JMenu sub = (JMenu) item;

                // Not opened is a closed menu, or a submenu missing from what the claim compares: walked again.
                if (!Components.waitFor(sub::isPopupMenuVisible, 3000)) return false;

                if (!hoverAll(path + item.getText() + " > ", sub.getPopupMenu(), into)) return false;

                moveTo(item);
            }
        }

        return true;
    }

    /** The tip on screen once the pointer rests on the item, or null when none is shown for it. */
    private static String tipShownOver(JMenuItem item) throws Exception
    {
        // Hovered afresh, as after a pause: moving straight on from a tip, the manager may still show the last item's.
        Components.onTheEventThread(() ->
        {
            ToolTipManager.sharedInstance().mousePressed(null);

            return null;
        });

        moveTo(item);

        // Read the moment it shows, as the manager takes it down again after its dismiss delay.
        final String[] seen = new String[1];

        java.util.function.BooleanSupplier showing = () ->
        {
            Object tip = tipManager("tip");

            if (tipManager("insideComponent") == item && Boolean.TRUE.equals(tipManager("tipShowing"))
                && tip instanceof javax.swing.JToolTip && ((javax.swing.JToolTip) tip).getComponent() == item)
            {
                seen[0] = ((javax.swing.JToolTip) tip).getTipText();
            }

            return seen[0] != null;
        };

        // The manager shows tips only for the items it listens to; for those, its timer may take a while under load.
        if (Components.onTheEventThread(() -> java.util.Arrays.asList(item.getMouseListeners())
            .contains(ToolTipManager.sharedInstance())))
        {
            Components.waitFor(showing, PATIENCE_MS);
        }

        // An item that has a tip and showed none was missed by the manager, not hidden: hover it again rather than
        // record a null that reads as hidden
        boolean hasTip = Components.onTheEventThread(() -> item.getToolTipText() != null);

        for (int again = 0; seen[0] == null && hasTip && again < 2; again++)
        {
            Components.onTheEventThread(() ->
            {
                ToolTipManager.sharedInstance().mousePressed(null);

                return null;
            });

            moveTo(item);

            Components.waitFor(showing, PATIENCE_MS);
        }

        if (seen[0] == null && hasTip) throw new AssertionError("the tip of " + item.getText() + " never showed");

        return seen[0];
    }

    /** A field of the tooltip manager, which keeps what it shows to itself. */
    private static Object tipManager(String name)
    {
        try
        {
            return Reflect.get(ToolTipManager.class, ToolTipManager.sharedInstance(), name);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** A pointer move to the item's middle, posted to the window it is drawn in, which passes it on as AWT does. */
    private static void moveTo(Component item)
    {
        SwingUtilities.invokeLater(() ->
        {
            java.awt.Window window = SwingUtilities.getWindowAncestor(item);

            java.awt.Point at = SwingUtilities.convertPoint(item, item.getWidth() / 2, item.getHeight() / 2, window);

            java.awt.Point screen = new java.awt.Point(at);

            SwingUtilities.convertPointToScreen(screen, window);

            java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new java.awt.event.MouseEvent(window,
                java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, at.x, at.y, screen.x, screen.y,
                0, false, java.awt.event.MouseEvent.NOBUTTON));
        });

        Components.settle();
    }

    private static Object field(String name) throws Exception
    {
        return Reflect.get(TrainControlUI.class, ui, name);
    }
}
