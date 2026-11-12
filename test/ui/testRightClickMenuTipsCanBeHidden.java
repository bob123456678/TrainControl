package ui;

import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
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
 * Preferences > Hide Menu Tips (FR-168): off, menus keep their tooltips; on, every enabled item of any menu, the menu
 * bar's and left-click pop-ups included (D37 on MT-910), loses its tooltip but a hotkey's, which shows exactly the
 * hotkey; a closed menu's items have their own tips back; the choice is stored; tooltips outside menus are untouched.
 * The menus are opened by right-clicks on the loc buttons, the diagram in the main window and in a page window, and
 * the track editor, and by clicks on the menu bar and a button.
 *
 * MUTATIONS, each seen red: the strip skipped (the on claims); the strip made even when off (the off claim); hotkeys
 * stripped too (the hotkey claim); the choice not written to the preferences (the persistence claim); right-click
 * menus only (the menu bar and left-click claims); tips put back on the next opening only (the closed-menu claim).
 */
public class testRightClickMenuTipsCanBeHidden
{
    private static final long PATIENCE_MS = 20000;

    private static final Pattern HOTKEY = Pattern.compile("(?:(?:Control|Alt)[+-]\\w+|Delete)");

    private static final Pattern BRACKETED = Pattern.compile("\\(((?:Control|Alt)[+-]\\w+)\\)\\s*(?:</\\w+>)*$");

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

    private static void collect(Component[] items, Map<String, String> into)
    {
        for (Component each : items)
        {
            if (!(each instanceof JMenuItem)) continue;

            JMenuItem item = (JMenuItem) each;

            into.put(item.getText(), item.getToolTipText());

            if (!item.isEnabled()) grayed.add(item.getText());

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

    /** What a tooltip should come to when only a hotkey is allowed; written here, not asked of the code. */
    private static String hotkeyOnly(String tip)
    {
        if (tip == null) return null;

        if (HOTKEY.matcher(tip).matches()) return tip;

        Matcher bracketed = BRACKETED.matcher(tip);

        return bracketed.find() ? bracketed.group(1) : null;
    }

    /** The same items as before hiding, each showing what hiding leaves: a grayed one its reason, any other its hotkey. */
    private static void assertOnlyHotkeysLeft(String which, Map<String, String> before, java.util.Set<String> gray,
        Map<String, String> after)
    {
        assertEquals(after.keySet(), before.keySet(), which + " offers other items on the second opening");

        for (Map.Entry<String, String> item : before.entrySet())
        {
            assertEquals(after.get(item.getKey()),
                gray.contains(item.getKey()) ? item.getValue() : hotkeyOnly(item.getValue()), "\"" + item.getKey()
                + "\" was \"" + item.getValue() + "\" and now shows the wrong thing");
        }
    }

    private static boolean isHotkey(String tip)
    {
        return tip != null && HOTKEY.matcher(tip).matches();
    }

    // ---------------------------------------------------------------------------------------------- claims

    /**
     * Off, which is how it starts, every right-click menu keeps its tooltips: the locomotive button's hotkeys and the
     * diagram menus' sentences.
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
            Map<String, String> tips = tipsOn(door);

            assertTrue(tips.values().stream().anyMatch(t -> t != null), door + ": no item has a tooltip with the"
                + " setting off: " + tips);

            if (door == Door.LOC_BUTTON)
            {
                assertTrue(tips.values().stream().anyMatch(t -> isHotkey(t)), door + ": no hotkey tooltip: " + tips);
            }
            else
            {
                assertTrue(tips.values().stream().anyMatch(t -> t != null && !isHotkey(t)), door + ": no sentence"
                    + " tooltip: " + tips);
            }
        }
    }

    /**
     * On, no item of any right-click menu has a tooltip but its hotkey, and an item that had a hotkey shows exactly it.
     *
     * MUTATION: skip the strip (the sentences stay); strip hotkeys too (the hotkeys go).
     */
    @Test
    public void testOnlyHotkeysRemainWhenTheTipsAreHidden() throws Exception
    {
        for (Door door : DIAGRAM_AND_BUTTON)
        {
            hide(false);

            Map<String, String> before = tipsOn(door);

            java.util.Set<String> gray = new java.util.HashSet<>(grayed);

            hide(true);

            Map<String, String> after = tipsOn(door);

            assertEquals(after.keySet(), before.keySet(), door + ": the menu offers other items on the second opening");

            int hotkeys = 0;
            int sentences = 0;

            for (Map.Entry<String, String> item : before.entrySet())
            {
                String expected = gray.contains(item.getKey()) ? item.getValue() : hotkeyOnly(item.getValue());

                assertEquals(after.get(item.getKey()), expected, door + ": \"" + item.getKey() + "\" was \""
                    + item.getValue() + "\" and now shows the wrong thing");

                if (expected != null) hotkeys++;
                else if (item.getValue() != null) sentences++;
            }

            if (door == Door.LOC_BUTTON) assertTrue(hotkeys > 0, door + ": no hotkey item to prove");
            else assertTrue(sentences > 0, door + ": no sentence tooltip to prove hidden: " + before);
        }
    }

    /**
     * The track editor's menu, opened once: its hotkey items keep exactly their hotkeys when the tips are hidden, and
     * its other tooltips go; off again, all are back.
     *
     * MUTATION: strip hotkeys too, or skip the strip, as above.
     */
    @Test
    public void testTheTrackEditorsMenuFollowsTheSetting() throws Exception
    {
        try
        {
            hide(false);

            Map<String, String> before = tipsOn(Door.EDITOR);

            java.util.Set<String> gray = new java.util.HashSet<>(grayed);

            assertTrue(before.values().stream().anyMatch(t -> isHotkey(t)), "the editor menu has no hotkey tooltip: "
                + before);

            hide(true);

            Map<String, String> hidden = tipsOn(Door.EDITOR);

            assertOnlyHotkeysLeft("the editor menu", before, gray, hidden);

            hide(false);

            assertEquals(tipsOn(Door.EDITOR), before, "the editor menu's tips did not come back");
        }
        finally
        {
            EditorsOpen.closeAll();
        }
    }

    /** Turning it off again brings the tooltips back on the next opening of a menu that was opened while hidden. */
    @Test
    public void testTurningItOffBringsTheTipsBack() throws Exception
    {
        hide(false);

        Map<String, String> original = tipsOn(Door.MAIN_DIAGRAM);

        hide(true);

        tipsOn(Door.MAIN_DIAGRAM);

        hide(false);

        assertEquals(tipsOn(Door.MAIN_DIAGRAM), original, "the tips did not come back");
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
     * show only hotkeys and grayed items' reasons; cleared, both show every tip they showed before, without a restart.
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
                assertTrue(before.values().stream().anyMatch(t -> t != null && hotkeyOnly(t) == null),
                    "precondition: no item shows a sentence: " + before);
            }

            SwingUtilities.invokeAndWait(tick::doClick);

            assertOnlyHotkeysLeft("hovered while ticked, the diagram's menu", diagram, diagramGray, hovered(station));

            assertOnlyHotkeysLeft("hovered while ticked, the Preferences menu", bar, barGray, hoveredBar(preferences));

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
     * Ticked, a closed menu's items have their own tips back at once, not only when it next opens: code that saves an
     * item's tooltip, as graying a Layout menu item for a run does, must never save a hidden one.
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

            Mouse.closeMenus();

            Components.settle();

            Map<String, String> tips = tipsOf(menu);

            assertEquals(tips.get("Sentence"), "An explanation.", "closed, an item has not got its own tip back");
            assertEquals(tips.get("Grayed"), "Close the editor first.", "closed, a grayed item lost its tip");
            assertEquals(tips.get("Keyed"), "Control+Q", "closed, a hotkey item has not got its own tip back");
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

    /** A window with one button that opens a pop-up of three items, on a right-click or on a left-click as asked. */
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
     * A grayed item keeps its tooltip, which says why it is grayed; an enabled item beside it loses its sentence and
     * keeps its hotkey.
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
            assertEquals(tips.get("Keyed"), "Control+Q", "a hotkey item does not show exactly its hotkey");
        }
        finally
        {
            Mouse.closeMenus();

            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    /**
     * A pop-up opened by a left-click, such as a button's drop-down, hides its tips as a right-click menu does (D37 on
     * MT-910, which replaced the ruling that it keeps them): a grayed item keeps its reason, a hotkey item its hotkey.
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
            assertEquals(tips.get("Keyed"), "Control+Q", "a left-click pop-up's hotkey item does not show its hotkey");
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
     * tooltip manager actually shows read off it.  Ticked, an enabled item shows only its hotkey or nothing, a grayed
     * one its reason; cleared, every item shows what it showed before ticking.
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

            assertTrue(before.values().stream().anyMatch(t -> t != null && hotkeyOnly(t) == null),
                "precondition: no item shows a sentence: " + before);

            java.util.Set<String> gray = new java.util.HashSet<>(grayed);

            SwingUtilities.invokeAndWait(tick::doClick);

            Map<String, String> ticked = hovered(tile);

            assertOnlyHotkeysLeft("hovered while ticked, the menu", before, gray, ticked);

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
                if (menu != null) hoverAll("", menu, shown);

                if (menu != null && menu.isShowing()) return shown;

                assertTrue(attempt < 5, "the menu closed before every item was hovered, five times: " + shown);
            }
            finally
            {
                Mouse.closeMenus();

                Components.settle();
            }
        }
    }

    private static void hoverAll(String path, JPopupMenu menu, Map<String, String> into) throws Exception
    {
        for (Component each : Components.onTheEventThread(menu::getComponents))
        {
            if (!(each instanceof JMenuItem) || !each.isShowing()) continue;

            JMenuItem item = (JMenuItem) each;

            if (!item.isEnabled()) grayed.add(path + item.getText());

            into.put(path + item.getText(), tipShownOver(item));

            if (item instanceof JMenu)
            {
                JMenu sub = (JMenu) item;

                // Not opened is a closed menu, walked again, or a submenu missing from what the claim compares.
                if (!Components.waitFor(sub::isPopupMenuVisible, 3000)) return;

                hoverAll(path + item.getText() + " > ", sub.getPopupMenu(), into);

                moveTo(item);
            }
        }
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
