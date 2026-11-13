package org.traincontrol.gui;

import java.awt.Component;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;

/**
 * The "Hide Hover Tooltips" setting (FR-168, FR-199): when on, every open menu, the menu bar's and pop-ups alike, shows
 * no shortcut keys and no tooltips on its enabled items; a grayed item keeps the tip that says why.  Both are taken off
 * only while their menu is open and put back as it closes, so code that saves a tooltip or reads a key never keeps a
 * hidden one.  Only the display changes: the window's own key handling acts on the keys either way.
 */
public final class RightClickTips
{
    /** The preference that holds the setting.  Off by default; its name stays, so a saved choice carries over. */
    public static final String PREF = "HideRightClickMenuTips";

    private static final String HELD_TIP = "RightClickTips.held";

    private static final String HELD_KEY = "RightClickTips.key";

    private static boolean installed;

    /** The items whose tips or keys this setting has taken off, to put back once their menu closes. */
    private static final java.util.Set<JMenuItem> stripped = new java.util.HashSet<>();

    private RightClickTips()
    {
    }

    /** Whether the setting is on. */
    public static boolean hidden()
    {
        return TrainControlUI.getPrefs() != null
            && TrainControlUI.getPrefs().getBoolean(PREF, false);
    }

    /** Starts watching the menus that open; once per program. */
    public static synchronized void install()
    {
        if (installed) return;

        installed = true;

        MenuSelectionManager.defaultManager().addChangeListener(e -> apply());
    }

    /** Hides the tips and keys of the menus now open, and puts back those of every menu that has closed. */
    private static void apply()
    {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();

        java.util.Set<JMenuItem> open = new java.util.HashSet<>();

        for (MenuElement each : path)
        {
            // A menu's items before its pop-up shows, so that the pop-up is sized without the keys.
            if (each instanceof JMenu) collect(((JMenu) each).getMenuComponents(), open);

            if (each instanceof JPopupMenu) collect(((JPopupMenu) each).getComponents(), open);
        }

        boolean resized = false;

        for (JMenuItem item : stripped) if (!open.contains(item)) putBack(item);

        stripped.retainAll(open);

        boolean hide = hidden();

        for (JMenuItem item : open)
        {
            resized |= hide ? strip(item) : putBack(item);
        }

        // A pop-up the keyboard opened was sized before this: it is sized again to what its items now show.
        for (MenuElement each : path)
        {
            if (resized && each instanceof JPopupMenu && ((JPopupMenu) each).isShowing()) ((JPopupMenu) each).pack();
        }
    }

    private static void collect(Component[] items, java.util.Set<JMenuItem> into)
    {
        for (Component each : items)
        {
            if (!(each instanceof JMenuItem)) continue;

            into.add((JMenuItem) each);

            if (each instanceof JMenu) collect(((JMenu) each).getMenuComponents(), into);
        }
    }

    /** Takes off the item's key, and its tip unless it is grayed; whether the key changed. */
    private static boolean strip(JMenuItem item)
    {
        // A grayed item's tip says why, which is live information.
        if (item.isEnabled())
        {
            String now = item.getToolTipText();

            Object held = item.getClientProperty(HELD_TIP);

            // A tip set while the menu is open is the new original.
            String original = now == null && held instanceof String ? (String) held : now;

            if (original != null)
            {
                item.putClientProperty(HELD_TIP, original);

                if (now != null) item.setToolTipText(null);

                stripped.add(item);
            }
        }
        else putBackTip(item);

        KeyStroke key = item.getAccelerator();

        if (key == null) return false;

        item.putClientProperty(HELD_KEY, key);

        item.setAccelerator(null);

        stripped.add(item);

        return true;
    }

    /** Puts back what the setting took off; whether the key changed. */
    private static boolean putBack(JMenuItem item)
    {
        putBackTip(item);

        Object key = item.getClientProperty(HELD_KEY);

        if (!(key instanceof KeyStroke)) return false;

        item.putClientProperty(HELD_KEY, null);

        if (item.getAccelerator() != null) return false;

        item.setAccelerator((KeyStroke) key);

        return true;
    }

    private static void putBackTip(JMenuItem item)
    {
        Object held = item.getClientProperty(HELD_TIP);

        if (!(held instanceof String)) return;

        if (item.getToolTipText() == null) item.setToolTipText((String) held);

        item.putClientProperty(HELD_TIP, null);
    }
}
