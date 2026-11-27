package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
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
import javax.swing.SwingUtilities;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.locomotive.CurrentKeyRow;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;
import org.traincontrol.util.ImageUtil;

/** The locomotive keyboard's pages: their names and how the tabs and label word them, clearing one, the tab badge. */
public final class LocomotiveKeyboardPages
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
        /** The keys of the keyboard, in the order they lie on it. */
        Set<JButton> keyButtons();
        /** The railway's model: the locomotives by name. */
        ViewListener getModel();
        /** The keys, on any page, that hold `l`. */
        List<String> getAllLocButtonMappings(Locomotive l);
        /** Shows the locomotive on the Q key, the first. */
        void showQKey();
        /** Makes page `to` a copy of page `from`. */
        void replacePage(int to, int from);
        /** Shows the locomotive of the current key again when the key is on the page showing. */
        void redisplayCurrentKey();
    }

    private final Keyboard ui;
    /** The page on the page clipboard, or null. */
    private Integer pageToCopy = null;
    /** "Show Page Numbers"; null until `installShowPageNumbersMenuItem` builds it. */
    private JCheckBoxMenuItem numbersItem;

    LocomotiveKeyboardPages(Keyboard ui)
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
     * Adds a page at the end (the digit keys reach pages by number) and goes to it.  Capped here only: loading may
     * grow the count past MAX_LOC_MAPPINGS.  Refusals are asked over `over`.
     */
    public void addLocMappingPage(Component over)
    {
        // Refused here too, not only grayed in the menu.
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

    /** Deletes the empty page showing, above the minimum; later names move up.  Refusals are asked over `over`. */
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

    /** Puts the page showing on the page clipboard. */
    void copyCurrentPage()
    {
        this.pageToCopy = ui.getLocMappingNumber();
    }

    /** Whether anything is on the page clipboard. */
    boolean pageCopied()
    {
        return this.pageToCopy != null;
    }

    /** Replaces the page showing with the copied one once the user says Yes over `over`; not on the copied page. */
    void pasteCopiedPage(Component over)
    {
        if (pageCopied() && ui.getLocMappingNumber() != this.pageToCopy)
        {
            int dialogResult = JOptionPane.showOptionDialog(
                over,
                I18n.f("page.ui.confirmReplaceMappings", getPageName(ui.getLocMappingNumber(), false),
                    getPageName(this.pageToCopy, false)),
                I18n.t("page.ui.dialogPasteMappings"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                TrainControlUI.YES_NO_OPTS,
                // Default to No: this replaces a page of mappings
                TrainControlUI.YES_NO_OPTS[1]
            );
            if (dialogResult == JOptionPane.YES_OPTION) pasteConfirmed();
        }
    }

    /** The Yes of the paste question: copies the copied page onto the page showing and empties the clipboard. */
    void pasteConfirmed()
    {
        ui.replacePage(ui.getLocMappingNumber(), this.pageToCopy);
        this.pageToCopy = null;
        ui.repaintMappings();

        ui.redisplayCurrentKey();
    }

    /** Fills the free keys of the page showing with the locomotives mapped nowhere, A to Z; event thread, later. */
    void mapUnassignedLocomotives()
    {
        SwingUtilities.invokeLater(() ->
        {
            for (JButton key : ui.keyButtons())
            {
                // Is the key free?
                if (ui.currentLocMapping().get(key) == null
                    || ui.getModel().getLocByName(ui.currentLocMapping().get(key).getName()) == null)
                {
                    List<String> locs = ui.getModel().getLocList();
                    Collections.sort(locs);

                    for (String s : locs)
                    {
                        Locomotive l = ui.getModel().getLocByName(s);

                        // Is the locomotive mapped anywhere?
                        if (l != null && ui.getAllLocButtonMappings(l).isEmpty())
                        {
                            ui.currentLocMapping().put(key, l);
                            break;
                        }
                    }
                }
            }

            ui.repaintMappings();
        });
    }

    /** Clears page `pageNumber`, puts `locs` on its keys in keyboard order, skipping nulls; shows the Q key. */
    void mapLocomotivesToPage(int pageNumber, List<Locomotive> locs)
    {
        SwingUtilities.invokeLater(() ->
        {
            ui.switchLocMapping(pageNumber);
            doClearCurrentPage();

            Iterator<Locomotive> iterator = locs.iterator();

            for (JButton key : ui.keyButtons())
            {
                if (!iterator.hasNext()) break;

                Locomotive l = iterator.next();

                if (l != null && ui.currentLocMapping().get(key) == null) ui.currentLocMapping().put(key, l);
            }

            ui.repaintMappings();
            ui.showQKey();
        });
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
