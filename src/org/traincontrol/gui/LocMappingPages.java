package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.locomotive.CurrentKeyRow;
import org.traincontrol.util.I18n;
import org.traincontrol.util.ImageUtil;

/** The locomotive keyboard's pages: their names and how the tabs and label word them, clearing one, the tab badge. */
public final class LocMappingPages
{
    /** The fewest pages: next/previous, the tab strip and copying between pages mean nothing with one. */
    static final int MIN_LOC_MAPPINGS = 2;

    /** FlatLightLaf's accent blue, written down because the UIManager key has changed name between FlatLaf releases. */
    private static final Color PAGE_BADGE_BLUE = new Color(0x26, 0x75, 0xBF);

    /** What the pages read and change of the main window. */
    interface Keyboard
    {
        /** How many pages there are. */
        int getNumLocMappings();
        /** The page showing, from 1. */
        int getLocMappingNumber();
        /** The page showing: each key's locomotive or null. */
        Map<JButton, Locomotive> currentLocMapping();
        /** The pages' names by number. */
        Map<Integer, String> pageNames();
        /** The locomotive the keyboard drives, or null. */
        Locomotive getActiveLoc();
        /** Lets go of the locomotive the keyboard drives. */
        void releaseActiveLoc();
        /** The page label by the arrows. */
        JLabel locMappingNumberLabel();
        /** The page tabs. */
        JTabbedPane locKeyTabs();
        /** The menu holding the page items. */
        JMenu locomotiveControlMenu();
        /** "Show Page Tabs", which "Show Page Numbers" follows. */
        JCheckBoxMenuItem showPageTabsPreference();
        /** Redraws the active locomotive's controls. */
        void repaintLoc(boolean force, List<Locomotive> updatedLocs);
        /** Redraws the keys. */
        void repaintMappings();
        /** Whether another page may be added. */
        boolean canAddLocMappingPage();
        /** Adds an empty page at the end and stores the new count. */
        void appendPage();
        /** Takes page `number` out and stores the new count. */
        void removePage(int number);
        /** Shows page `number`, its tab, label and arrows. */
        void switchLocMapping(int number);
    }

    private final Keyboard ui;
    /** "Show Page Numbers"; null until `installShowPageNumbersMenuItem` builds it. */
    private JCheckBoxMenuItem numbersItem;

    LocMappingPages(Keyboard ui)
    {
        this.ui = ui;
    }

    /** Whether the page showing could be deleted: only an EMPTY one (later pages shift up), above the minimum. */
    boolean canDeleteCurrentPage()
    {
        if (ui.getNumLocMappings() <= MIN_LOC_MAPPINGS) return false;
        // Empty means no locomotive on it: clearing a page nulls the values but keeps the keys, so isEmpty() is false.
        for (Locomotive on : ui.currentLocMapping().values())
        {
            if (on != null) return false;
        }
        return true;
    }

    /**
     * Adds a page at the end and goes to it: pages are reached by NUMBER (the digit keys), so inserting in the middle
     * would renumber the rest.  Capped HERE ONLY: `setViewListener` grows the count past MAX_LOC_MAPPINGS, as clamping
     * on load would throw away the mappings on every page past the fiftieth.  Refusals are asked over `over`.
     */
    public void addLocMappingPage(Component over)
    {
        // Refused here too, not only grayed in the menu: the next caller of this public method lacks the menu's guard.
        if (!ui.canAddLocMappingPage())
        {
            JOptionPane.showMessageDialog(over, I18n.f("page.ui.errorTooManyPages", TrainControlUI.MAX_LOC_MAPPINGS));

            return;
        }

        ui.appendPage();

        ui.locKeyTabs().add(getLocMappingPageTabTitle(ui.getNumLocMappings()), new JPanel());

        TrainControlUI.adjustTabbedPaneHeight(ui.locKeyTabs());

        ui.switchLocMapping(ui.getNumLocMappings());

        ui.repaintMappings();
    }

    /**
     * Deletes the page showing now, if it is empty and not the last one that may be deleted.  Its NAME goes with it and
     * the later names move up: they are stored by page number.  Refusals are asked over `over`.
     */
    public void deleteCurrentLocMappingPage(Component over)
    {
        int count = ui.getNumLocMappings();

        if (!canDeleteCurrentPage())
        {
            JOptionPane.showMessageDialog(over, count <= MIN_LOC_MAPPINGS
                ? I18n.f("page.ui.errorNeedAtLeast", MIN_LOC_MAPPINGS)
                : I18n.t("page.ui.errorPageNotEmpty"));

            return;
        }

        int going = ui.getLocMappingNumber();
        Map<Integer, String> names = ui.pageNames();

        for (int at = going; at < count; at++)
        {
            String next = names.get(at + 1);

            if (next == null) names.remove(at);
            else names.put(at, next);
        }

        names.remove(count);

        ui.removePage(going);

        ui.locKeyTabs().remove(count - 1);

        for (int at = 0; at < count - 1; at++)
        {
            ui.locKeyTabs().setTitleAt(at, getLocMappingPageTabTitle(at + 1));
        }

        TrainControlUI.adjustTabbedPaneHeight(ui.locKeyTabs());

        ui.switchLocMapping(Math.min(going, count - 1));

        ui.repaintMappings();
    }

    /** A page tab's caption by `CurrentKeyRow.pageWords` (FR-128); the name cut to MAX_PAGE_NAME_LENGTH_TAB. */
    String getLocMappingPageTabTitle(int tabNumber)
    {
        return CurrentKeyRow.pageWords(tabNumber, getPageName(tabNumber, true), pageNumbersShown(),
            name -> name.length() > TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB
                ? name.substring(0, TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB) : name);
    }

    /** Sets the name of the currently active mapping page. */
    void setPageName(String name)
    {
        if (name == null || "".equals(name.trim())) ui.pageNames().remove(ui.getLocMappingNumber());
        else ui.pageNames().put(ui.getLocMappingNumber(), name.trim());
        ui.locMappingNumberLabel().setText(getPageLabelText(ui.getLocMappingNumber()));
        ui.repaintLoc(true, null);
    }

    /** Puts "Show Page Numbers" after the page-tabs item (FR-128); a click re-words the tabs, label and name line. */
    JCheckBoxMenuItem installShowPageNumbersMenuItem()
    {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(I18n.t("ui.main.toolbar.showPageNumbers"));
        item.setToolTipText(AutonomyEditorPanel.wrappedIfLong(I18n.t("ui.main.toolbar.tooltip.showPageNumbers")));
        item.addActionListener(event ->
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.SHOW_PAGE_NUMBERS, item.isSelected());

            // Tabs and label are worded only when a page is named or chosen; the name line's render sees the change.
            retitlePages();
            ui.repaintLoc(false, null);
        });

        JMenu menu = ui.locomotiveControlMenu();
        int at = menu.getMenuComponentCount();
        for (int i = 0; i < menu.getMenuComponentCount(); i++)
        {
            if (menu.getMenuComponent(i) == ui.showPageTabsPreference())
            {
                at = i + 1;
                break;
            }
        }

        numbersItem = item;
        menu.insert(item, at);
        return item;
    }

    /** Whether page numbers go before page names ("Show Page Numbers", FR-128); false before the item exists. */
    boolean pageNumbersShown()
    {
        return numbersItem != null && numbersItem.isSelected();
    }

    /** Re-words every page tab, and the label unless it is empty, as the setting now has it (FR-128); event thread. */
    void retitlePages()
    {
        JTabbedPane tabs = ui.locKeyTabs();
        for (int at = 0; at < tabs.getTabCount(); at++)
        {
            tabs.setTitleAt(at, getLocMappingPageTabTitle(at + 1));
        }

        if (!ui.locMappingNumberLabel().getText().isEmpty())
        {
            ui.locMappingNumberLabel().setText(getPageLabelText(ui.getLocMappingNumber()));
        }
    }

    /** A page's name as typed (`raw`, empty for none), or for menus and dialogs: "Page 5 (Example)" or "Page 5". */
    String getPageName(int mappingNumber, boolean raw)
    {
        if (ui.pageNames().containsKey(mappingNumber))
        {
            String name = ui.pageNames().get(mappingNumber);
            if (!raw)
            {
                if (name.length() > TrainControlUI.MAX_PAGE_NAME_LENGTH_TOP)
                {
                    name = name.substring(0, TrainControlUI.MAX_PAGE_NAME_LENGTH_TOP) + "...";
                }
                name = I18n.f("page.ui.plainPageName", mappingNumber, name);
            }
            return name;
        }
        return raw ? "" : I18n.f("page.ui.pageNo", mappingNumber);
    }

    /** The page label by the arrows, by `CurrentKeyRow.pageWords` (FR-128); the name cut to MAX_PAGE_NAME_LENGTH. */
    String getPageLabelText(int mappingNumber)
    {
        return CurrentKeyRow.pageWords(mappingNumber, getPageName(mappingNumber, true), pageNumbersShown(),
            name -> name.length() > TrainControlUI.MAX_PAGE_NAME_LENGTH
                ? name.substring(0, TrainControlUI.MAX_PAGE_NAME_LENGTH) : name);
    }

    /** The page names, without the two sentinels `saveState` stores in `pageNames` under negative keys. */
    Set<String> namesOfPages()
    {
        Set<String> names = new HashSet<>();
        for (Map.Entry<Integer, String> page : ui.pageNames().entrySet())
        {
            if (page.getKey() == null || page.getKey() < 1) continue;
            if (page.getValue() != null) names.add(page.getValue().trim());
        }
        return names;
    }

    /** Asks over `over` for the current page's name. */
    void renameCurrentPage(Component over)
    {
        int page = ui.getLocMappingNumber();
        String input = JOptionPane.showInputDialog(over, I18n.f("page.ui.promptEnterPageName", page),
            getPageName(page, true));

        if (input != null)
        {
            input = input.trim();
            if (namesOfPages().contains(input) && !input.equals(getPageName(page, true).trim()))
            {
                JOptionPane.showMessageDialog(over, I18n.t("page.ui.errorDuplicatePageName"));
            }
            else
            {
                setPageName(input);
            }
        }
    }

    /** Empties the current page once the user says Yes over `over`. */
    void clearCurrentPage(Component over)
    {
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            int dialogResult = JOptionPane.showOptionDialog(
                over,
                I18n.f("page.ui.confirmClearKeyMappings", ui.getLocMappingNumber()),
                I18n.t("page.ui.dialogResetKeyboardMappings"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                // Default to No: a page's whole set of key mappings is destructive to lose to a stray Enter.
                TrainControlUI.YES_NO_OPTS[1]
            );
            if (dialogResult == JOptionPane.YES_OPTION) doClearCurrentPage();
        });
    }

    /** Resets the mappings on the current page without asking. */
    void doClearCurrentPage()
    {
        // Lets the active locomotive go only if it is on this page, not one chosen on another.
        Map<JButton, Locomotive> page = ui.currentLocMapping();
        if (ui.getActiveLoc() != null && page.containsValue(ui.getActiveLoc())) ui.releaseActiveLoc();

        for (JButton key : page.keySet())
        {
            page.put(key, null);
        }

        ui.repaintMappings();
        ui.repaintLoc(false, null);
    }

    /** The page number as a blue badge centered on a COPY of the shared tab icon; none for page 1. */
    static BufferedImage withPageBadge(BufferedImage icon, int page)
    {
        if (icon == null || page <= 1) return icon;

        String text = Integer.toString(page);
        BufferedImage out = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        ImageUtil.antialias(g);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(icon, 0, 0, null);

        // Laid out at the tab icon's size, then scaled to the picture's, which a sharp copy makes larger
        double scale = (double) icon.getHeight() / TrainControlUI.TAB_ICON_CONTROL.getIconHeight();
        g.scale(scale, scale);
        g.setFont(new Font("Segoe UI", Font.BOLD, 10));

        // Centered on the digits' ink: font metrics' leading and side bearings put them a pixel off
        java.awt.font.GlyphVector glyphs = g.getFont().createGlyphVector(g.getFontRenderContext(), text);
        java.awt.geom.Rectangle2D ink = glyphs.getVisualBounds();

        int tall = 13;
        int wide = Math.max(tall, (int) Math.ceil(ink.getWidth()) + 7);

        // Clamped at zero: an icon narrower than its badge would start off the left edge.
        int x = Math.max(0, (TrainControlUI.TAB_ICON_CONTROL.getIconWidth() - wide) / 2);
        int y = Math.max(0, (TrainControlUI.TAB_ICON_CONTROL.getIconHeight() - tall) / 2);

        g.setColor(PAGE_BADGE_BLUE);
        g.fillRoundRect(x, y, wide, tall, tall, tall);
        g.setColor(Color.WHITE);
        g.drawString(text,
            (float) (x + (wide - ink.getWidth()) / 2 - ink.getX()),
            (float) (y + (tall - ink.getHeight()) / 2 - ink.getY()));
        g.dispose();
        return out;
    }
}
