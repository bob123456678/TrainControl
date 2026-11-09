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
 * Preferences > Hide Right-Click Menu Tips (FR-168): off, right-click menus keep their tooltips; on, every item loses
 * its tooltip but a hotkey's, which shows exactly the hotkey; the choice is stored; tooltips outside right-click menus
 * are untouched.  The menus are opened by right-clicks on the loc buttons, the diagram in the main window and in a page
 * window, and the track editor.
 *
 * MUTATIONS, each seen red: the strip skipped (the on claims); the strip made even when off (the off claim); hotkeys
 * stripped too (the hotkey claim); the choice not written to the preferences (the persistence claim); the menu bar's
 * menus stripped too (the outside claim).
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

        Map<String, String> tips = Components.onTheEventThread(() ->
        {
            Map<String, String> read = new LinkedHashMap<>();

            collect(menu.getComponents(), read);

            return read;
        });

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

            assertEquals(hidden.keySet(), before.keySet(), "the editor menu offers other items on the second opening");

            for (Map.Entry<String, String> item : before.entrySet())
            {
                assertEquals(hidden.get(item.getKey()),
                    gray.contains(item.getKey()) ? item.getValue() : hotkeyOnly(item.getValue()), "\"" + item.getKey()
                    + "\" was \"" + item.getValue() + "\" and now shows the wrong thing");
            }

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
     * On, the tooltips of the menu bar's items and of a button are as they were, after right-click menus and a menu
     * bar menu have opened.
     *
     * MUTATION: strip every pop-up menu of the open path, the menu bar's included.
     */
    @Test
    public void testTipsOutsideRightClickMenusAreUntouched() throws Exception
    {
        hide(false);

        AbstractButton bar = Components.withText(ui, I18n.t("ui.main.toolbar.greyStationLabels"));

        assertNotNull(bar, "precondition: no Gray Station Labels item");

        String barTip = bar.getToolTipText();

        assertNotNull(barTip, "precondition: the menu bar item has no tooltip");

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

        assertEquals(bar.getToolTipText(), barTip, "a menu bar item lost its tooltip");

        List<AbstractButton> inBar = Components.all(preferences.getPopupMenu(), AbstractButton.class,
            b -> b.getToolTipText() != null);

        assertFalse(inBar.isEmpty(), "precondition: no Preferences item has a tooltip");

        Mouse.closeMenus();

        assertEquals(button.getToolTipText(), buttonTip, "a button lost its tooltip");
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

            Map<String, String> tips = Components.onTheEventThread(() ->
            {
                Map<String, String> read = new LinkedHashMap<>();

                collect(menu.getComponents(), read);

                return read;
            });

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
     * A pop-up opened by a left-click keeps every tooltip even with the setting on.
     *
     * MUTATION: treat every pop-up as a right-click menu.
     */
    @Test
    public void testALeftClickPopupKeepsItsTips() throws Exception
    {
        javax.swing.JFrame[] frame = new javax.swing.JFrame[1];

        try
        {
            hide(true);

            javax.swing.JButton button = standIn(false, frame);

            Mouse.enter(button);
            Mouse.click(button);

            assertTrue(Components.waitFor(() -> Components.popupShowing() != null, PATIENCE_MS),
                "the left-click opened no pop-up");

            JPopupMenu menu = Components.popupShowing();

            Map<String, String> tips = Components.onTheEventThread(() ->
            {
                Map<String, String> read = new LinkedHashMap<>();

                collect(menu.getComponents(), read);

                return read;
            });

            assertEquals(tips.get("Sentence"), "An explanation.", "a left-click pop-up lost a tip");
            assertEquals(tips.get("Grayed"), "Close the editor first.", "a left-click pop-up lost a tip");
            assertEquals(tips.get("Keyed"), "Control+Q", "a left-click pop-up lost a tip");
        }
        finally
        {
            Mouse.closeMenus();

            if (frame[0] != null) SwingUtilities.invokeAndWait(frame[0]::dispose);
        }
    }

    private static Object field(String name) throws Exception
    {
        return Reflect.get(TrainControlUI.class, ui, name);
    }
}
