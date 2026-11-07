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
 * The "Hide Right-Click Menu Tips" setting (FR-168): when on, every item of a right-click menu loses its tooltip as the
 * menu opens, except an item with a hotkey, which keeps just the hotkey.  Done here for every pop-up menu rooted on a
 * component, so no menu builder knows of it; menu bar menus are untouched.
 */
public final class RightClickTips
{
    /** The preference that holds the setting.  Off by default. */
    public static final String PREF = "HideRightClickMenuTips";

    private static final String HELD = "RightClickTips.held";

    private static final Pattern HOTKEY = Pattern.compile(
        "(?:(?:Control|Ctrl|Alt|Shift)[+-]\\S+|Delete)(?: / (?:(?:Control|Alt|Shift)[+-])?\\S+)*");

    private static boolean installed;

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

    /** Sets the tooltips of the right-click menu now open, or puts back those this setting hid. */
    private static void apply()
    {
        MenuElement[] path = MenuSelectionManager.defaultManager().getSelectedPath();

        if (path.length == 0 || !(path[0] instanceof JPopupMenu)) return;

        JPopupMenu root = (JPopupMenu) path[0];

        if (root.getInvoker() instanceof JMenu) return;

        apply(root.getComponents(), hidden());
    }

    private static void apply(Component[] items, boolean hide)
    {
        for (Component each : items)
        {
            if (!(each instanceof JMenuItem)) continue;

            JMenuItem item = (JMenuItem) each;

            String now = item.getToolTipText();

            Object held = item.getClientProperty(HELD);

            // A tip set since the last open is the new original.
            String original = held instanceof Held && Objects.equals(now, ((Held) held).applied)
                ? ((Held) held).original : now;

            if (hide)
            {
                String shown = hotkeyIn(original);

                if (!Objects.equals(now, shown)) item.setToolTipText(shown);

                item.putClientProperty(HELD, new Held(original, shown));
            }
            else if (held instanceof Held)
            {
                if (Objects.equals(now, ((Held) held).applied)) item.setToolTipText(original);

                item.putClientProperty(HELD, null);
            }

            if (item instanceof JMenu) apply(((JMenu) item).getMenuComponents(), hide);
        }
    }
}
