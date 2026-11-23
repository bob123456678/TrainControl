package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.locomotive.CurrentKeyRow;
import org.traincontrol.util.I18n;
import org.traincontrol.util.ImageUtil;

/** The locomotive keyboard's pages: their names and how the tabs and label word them, clearing one, the tab badge. */
final class LocMappingPages
{
    /** The fewest pages: next/previous, the tab strip and copying between pages mean nothing with one. */
    static final int MIN_LOC_MAPPINGS = 2;

    /** FlatLightLaf's accent blue, written down because the UIManager key has changed name between FlatLaf releases. */
    private static final Color PAGE_BADGE_BLUE = new Color(0x26, 0x75, 0xBF);

    private final TrainControlUI ui;

    /** "Show Page Numbers"; null until `installShowPageNumbersMenuItem` builds it. */
    private JCheckBoxMenuItem numbersItem;

    LocMappingPages(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Whether the page showing now could be deleted: it must be EMPTY, since later pages shift up and a full one would
     * lose mappings that are not on screen, and the count must stay above the minimum.
     */
    boolean canDeleteCurrentPage()
    {
        if (ui.getNumLocMappings() <= MIN_LOC_MAPPINGS) return false;

        // Empty means no locomotive on it: clearing a page nulls the values but keeps the keys, so isEmpty() is false.
        for (Locomotive on : ui.locMapping.get(ui.getLocMappingNumber() - 1).values())
        {
            if (on != null) return false;
        }
        return true;
    }

    /**
     * The caption on a locomotive mapping page's tab, worded by `CurrentKeyRow.pageWords` like the line over the
     * locomotive's name and the page label (FR-128); only the NAME is cut, to `MAX_PAGE_NAME_LENGTH_TAB` letters.
     */
    String getLocMappingPageTabTitle(int tabNumber)
    {
        return CurrentKeyRow.pageWords(tabNumber, getPageName(tabNumber, true), pageNumbersShown(),
            name -> name.length() > TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB
                ? name.substring(0, TrainControlUI.MAX_PAGE_NAME_LENGTH_TAB) : name);
    }

    /** Sets the name of the currently active mapping page. */
    void setPageName(String name)
    {
        if (name == null || "".equals(name.trim())) ui.pageNames.remove(ui.getLocMappingNumber());
        else ui.pageNames.put(ui.getLocMappingNumber(), name.trim());
        ui.locMappingNumberLabel().setText(getPageLabelText(ui.getLocMappingNumber()));
        ui.repaintLoc(true, null);
    }

    /**
     * "Show Page Numbers" in the Preferences menu's Locomotive submenu (FR-128), built here as the form is generated.
     * It is the setting for the page tabs and label as well as the line over the name, so a click re-words them all.
     */
    JCheckBoxMenuItem installShowPageNumbersMenuItem()
    {
        numbersItem = new JCheckBoxMenuItem(I18n.t("ui.main.toolbar.showPageNumbers"));
        numbersItem.setToolTipText(
            AutonomyEditorPanel.wrappedIfLong(I18n.t("ui.main.toolbar.tooltip.showPageNumbers")));
        numbersItem.addActionListener(event ->
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.SHOW_PAGE_NUMBERS, numbersItem.isSelected());

            // The page tabs and label are set when a page is named or chosen, so neither would notice this change.
            retitlePages();

            // The line over the name: what it says includes this setting, so the render's check finds it changed.
            ui.repaintLoc();
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

        menu.insert(numbersItem, at);
        return numbersItem;
    }

    /** Whether page numbers go before page names ("Show Page Numbers", FR-128); false before the item exists. */
    boolean pageNumbersShown()
    {
        return numbersItem != null && numbersItem.isSelected();
    }

    /**
     * Words every page tab and label again as the setting now has it (FR-128), since they are set when a page is
     * named or chosen.  The label is left alone when it shows nothing.  On the event thread.
     */
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

    /**
     * Gets the name of the specified mapping page in the program's own words: "Page 5 (Example)" (the name cut to
     * `MAX_PAGE_NAME_LENGTH_TOP` letters), or "Page 5" with no name.  Menus and dialogs ignore "Show Page Numbers".
     * @param raw - the name as typed, or empty for none, without the program's words round it
     */
    String getPageName(int mappingNumber, boolean raw)
    {
        if (ui.pageNames.containsKey(mappingNumber))
        {
            String name = ui.pageNames.get(mappingNumber);
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

    /**
     * The words on the page label by the arrows, by `CurrentKeyRow.pageWords` (FR-128), the rule the page tabs and the
     * line over the name follow.  One line, as the label is one line high; the name is cut to `MAX_PAGE_NAME_LENGTH`.
     */
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

        for (Map.Entry<Integer, String> page : ui.pageNames.entrySet())
        {
            if (page.getKey() == null || page.getKey() < 1) continue;
            if (page.getValue() != null) names.add(page.getValue().trim());
        }
        return names;
    }

    /** Prompts the user for the name of the current page. */
    void renameCurrentPage()
    {
        int page = ui.getLocMappingNumber();
        String input = JOptionPane.showInputDialog(ui, I18n.f("page.ui.promptEnterPageName", page),
            getPageName(page, true));

        if (input != null)
        {
            input = input.trim();
            // The page names only: `pageNames` also holds the active page and button key code under keys -1 and -2.
            if (namesOfPages().contains(input) && !input.equals(getPageName(page, true).trim()))
            {
                JOptionPane.showMessageDialog(ui, I18n.t("page.ui.errorDuplicatePageName"));
            }
            else
            {
                setPageName(input);
            }
        }
    }

    /** Resets the mappings on the current page, once the user says Yes. */
    void clearCurrentPage()
    {
        // The dialog and the mappings it clears both belong on the event thread
        javax.swing.SwingUtilities.invokeLater(() ->
        {
            int dialogResult = JOptionPane.showOptionDialog(
                ui,
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
        // The active locomotive is let go only if it was on this page: clearing page 3 must not take the keyboard from
        // a train selected on page 1.  Asked of the buttons: this page's mapping IS the list of what is about to be let
        // go.
        Map<JButton, Locomotive> page = ui.currentLocMapping();
        if (ui.activeLoc != null && page.containsValue(ui.activeLoc)) ui.activeLoc = null;

        for (JButton key : page.keySet())
        {
            page.put(key, null);
        }

        ui.repaintMappings();
        ui.repaintLoc();
    }

    /**
     * Draws the page number as a blue circle centered on the locomotive tab icon (nothing for page 1, a capsule for
     * more digits), onto a COPY since the tab icon is a shared static.
     */
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

        // Center on the digits' ink, not font metrics: getHeight() includes leading and stringWidth the side bearings,
        // which put the digits a pixel off.
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
