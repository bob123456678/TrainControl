package org.traincontrol.gui;

import java.awt.Component;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;

/**
 * The "Hide Menu Tips" setting (FR-168): when on, every enabled item of an open menu, the menu bar's and pop-ups alike,
 * shows no tooltip but its hotkey; a grayed item keeps the tip that says why.  The tips are taken off only while their
 * menu is open and put back as it closes, so code that saves and restores a tooltip never keeps a hidden one.
 */
public final class RightClickTips
{
    /** The preference that holds the setting.  Off by default; its name stays, so a saved choice carries over. */
    public static final String PREF = "HideRightClickMenuTips";

    private static final String HELD = "RightClickTips.held";

    private static final Pattern HOTKEY = Pattern.compile(
        "(?:(?:Control|Ctrl|Alt|Shift)[+-]\\S+|Delete)(?: / (?:(?:Control|Alt|Shift)[+-])?\\S+)*");

    private static boolean installed;

    /** The items whose tips this setting has taken off, to put back once their menu closes. */
    private static final java.util.Set<JMenuItem> stripped = new java.util.HashSet<>();

    private RightClickTips()
    {
    }

    /** What an item's tooltip was before the setting changed it, and what the setting put there. */
    private static final class Held
    {
        final String original;
        final String applied;

        Held(String original, String applied)
        {
            this.original = original;
            this.applied = applied;
        }
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

    /**
     * The hotkey a tooltip names, such as "Control+E": the whole tooltip, or the one in brackets at its end.
     * @param tip a tooltip, or null
     * @return the hotkey text, or null when the tooltip is not or does not end in one
     */
    static String hotkeyIn(String tip)
    {
        if (tip == null) return null;

        String text = tip.replaceAll("<[^>]*>", "").trim();

        if (HOTKEY.matcher(text).matches()) return text;

        int open = text.lastIndexOf('(');

        if (open >= 0 && text.endsWith(")"))
        {
            String inside = text.substring(open + 1, text.length() - 1).trim();

            if (HOTKEY.matcher(inside).matches()) return inside;
        }

        return null;
    }

    /** Hides the tips of the menus now open, and puts back those of every menu that has closed. */
    private static void apply()
    {
        java.util.Set<JMenuItem> open = new java.util.HashSet<>();

        for (MenuElement each : MenuSelectionManager.defaultManager().getSelectedPath())
        {
            if (each instanceof JPopupMenu) collect(((JPopupMenu) each).getComponents(), open);
        }

        for (JMenuItem item : stripped) if (!open.contains(item)) putBack(item);

        stripped.retainAll(open);

        boolean hide = hidden();

        for (JMenuItem item : open)
        {
            // A grayed item's tip says why, which is live information.
            if (hide && item.isEnabled()) strip(item);
            else putBack(item);
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

    private static void strip(JMenuItem item)
    {
        String now = item.getToolTipText();

        Object held = item.getClientProperty(HELD);

        // A tip set while the menu is open is the new original.
        String original = held instanceof Held && Objects.equals(now, ((Held) held).applied)
            ? ((Held) held).original : now;

        String shown = hotkeyIn(original);

        if (!Objects.equals(now, shown)) item.setToolTipText(shown);

        item.putClientProperty(HELD, new Held(original, shown));

        stripped.add(item);
    }

    private static void putBack(JMenuItem item)
    {
        Object held = item.getClientProperty(HELD);

        if (!(held instanceof Held)) return;

        if (Objects.equals(item.getToolTipText(), ((Held) held).applied)) item.setToolTipText(((Held) held).original);

        item.putClientProperty(HELD, null);
    }
}
