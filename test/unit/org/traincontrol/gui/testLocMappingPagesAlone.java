package org.traincontrol.gui;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import static support.Components.onTheEventThread;

/**
 * `LocMappingPages` on its own: which page may be deleted, how a page is named and worded, clearing one, the tab badge,
 * through a fake of what it reads of the main window.  Its integration tests are `ui.testLocMappingPages`,
 * `ui.testTheKeyboardPages` and `ui.testMappingPagesAndLocomotiveDoors`.
 */
public class testLocMappingPagesAlone
{
    /** TrainControlUI.MAX_PAGE_NAME_LENGTH_TOP, _TAB and the label's MAX_PAGE_NAME_LENGTH, which a unit may not name. */
    private static final int TOP = 12, TAB = 14, LABEL = 40;

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    /**
     * A page may be deleted only with no train on it, though clearing keeps its keys, and only above the fewest pages:
     * the empty page of three may go, the same page with a train may not, nor an empty one of two.
     *
     * MUTATION: in `LocMappingPages.canDeleteCurrentPage`, compare with `<` in place of `<=`, and one of two pages may
     * be deleted; or leave out the `on != null` test, and the page with a train may.
     */
    @Test
    public void testOnlyAnEmptyPageAboveTheFewestMayBeDeleted()
    {
        List<Boolean> may = onTheEventThread(() ->
        {
            Fake ui = new Fake(3);
            ui.showing = 2;
            ui.put(1, train("Elsewhere"));
            List<Boolean> out = new ArrayList<>();
            out.add(new LocMappingPages(ui).canDeleteCurrentPage());

            ui.put(2, train("Shunter"));
            out.add(new LocMappingPages(ui).canDeleteCurrentPage());
            out.add(new LocMappingPages(new Fake(LocMappingPages.MIN_LOC_MAPPINGS)).canDeleteCurrentPage());
            return out;
        });

        assertEquals(may, Arrays.asList(true, false, false), "empty of three, with a train, empty of two");
    }

    /**
     * A page's name is as typed (`raw`, empty for none), or for menus and dialogs "Page 5 (Example)", the name cut to
     * MAX_PAGE_NAME_LENGTH_TOP letters and "..." only when longer, and "Page 8" for none.
     *
     * MUTATION: in `LocMappingPages.getPageName`, cut at `>=` in place of `>`, and the name of exactly TOP letters is
     * cut too.
     */
    @Test
    public void testAPageIsNamedAsTypedOrCutForMenus()
    {
        Fake ui = new Fake(8);
        ui.names.put(5, "Example");
        ui.names.put(6, LETTERS.substring(0, TOP + 1));
        ui.names.put(7, LETTERS.substring(0, TOP));
        LocMappingPages pages = new LocMappingPages(ui);

        assertEquals(Arrays.asList(pages.getPageName(5, true), pages.getPageName(8, true)), Arrays.asList("Example",
            ""), "raw");
        assertEquals(Arrays.asList(pages.getPageName(5, false), pages.getPageName(6, false), pages.getPageName(7, false),
            pages.getPageName(8, false)), Arrays.asList(I18n.f("page.ui.plainPageName", 5, "Example"),
            I18n.f("page.ui.plainPageName", 6, LETTERS.substring(0, TOP) + "..."), I18n.f("page.ui.plainPageName", 7,
            LETTERS.substring(0, TOP)), I18n.f("page.ui.pageNo", 8)), "for menus");
    }

    /**
     * Tabs cut a name to MAX_PAGE_NAME_LENGTH_TAB letters and the label to MAX_PAGE_NAME_LENGTH; "Show Page Numbers",
     * put right after "Show Page Tabs" (or last without it), puts "2. " before a name once ticked, and never before
     * "Page 3".
     *
     * MUTATION: in `LocMappingPages.installShowPageNumbersMenuItem`, insert at `i` in place of `i + 1`, and the item
     * goes before Show Page Tabs.
     */
    @Test
    public void testTheTabsAndTheLabelFollowShowPageNumbers()
    {
        List<String> said = onTheEventThread(() ->
        {
            Fake ui = new Fake(4);
            ui.names.put(2, "Yard");
            ui.names.put(4, LETTERS);
            ui.menu.add(new JMenuItem("before"));
            ui.menu.add(ui.showTabs);
            ui.menu.add(new JMenuItem("after"));
            LocMappingPages pages = new LocMappingPages(ui);
            List<String> out = new ArrayList<>(words(pages));

            JCheckBoxMenuItem item = pages.installShowPageNumbersMenuItem();
            out.add(ui.menu.getMenuComponent(2) == item ? "after Show Page Tabs" : "elsewhere");
            out.add(String.valueOf(pages.pageNumbersShown()));
            item.setSelected(true);
            out.addAll(words(pages));

            Fake without = new Fake(2);
            without.menu.add(new JMenuItem("only"));
            out.add(new LocMappingPages(without).installShowPageNumbersMenuItem() == without.menu.getMenuComponent(1)
                ? "last" : "elsewhere");
            return out;
        });

        assertEquals(said, Arrays.asList("Yard", "Page 3", LETTERS.substring(0, TAB), LETTERS.substring(0, LABEL),
            "after Show Page Tabs", "false", "2. Yard", "Page 3", "4. " + LETTERS.substring(0, TAB), "4. "
            + LETTERS.substring(0, LABEL), "last"), "unticked, the item, ticked, a menu without Show Page Tabs");
    }

    /**
     * Naming the page showing keeps the name trimmed, words the label by it and redraws the locomotive's controls in
     * full; a blank name takes the name away.
     *
     * MUTATION: in `LocMappingPages.setPageName`, store the name untrimmed, and "  Yard " is kept with its spaces.
     */
    @Test
    public void testNamingThePageShowingTrimsAndWordsTheLabel()
    {
        List<String> said = onTheEventThread(() ->
        {
            Fake ui = new Fake(3);
            ui.showing = 2;
            LocMappingPages pages = new LocMappingPages(ui);

            pages.setPageName("  Yard ");
            List<String> out = new ArrayList<>(Arrays.asList(ui.names.toString(), ui.label.getText()));
            pages.setPageName("   ");
            out.addAll(Arrays.asList(ui.names.toString(), ui.label.getText(), ui.did.toString()));
            return out;
        });

        assertEquals(said, Arrays.asList("{2=Yard}", "Yard", "{}", "Page 2", "[repaintLoc true, repaintLoc true]"));
    }

    /**
     * The names of the pages leave out the two sentinels `saveState` keeps under -1 and -2, and a page with no name;
     * each is trimmed.
     *
     * MUTATION: in `LocMappingPages.namesOfPages`, leave out the `page.getKey() < 1` test, and the sentinels are names.
     */
    @Test
    public void testThePageNamesLeaveOutTheSentinels()
    {
        Fake ui = new Fake(3);
        ui.names.put(1, " Yard ");
        ui.names.put(2, null);
        ui.names.put(3, "Shed");
        ui.names.put(-1, "4");
        ui.names.put(-2, "1");

        assertEquals(new LocMappingPages(ui).namesOfPages(), new HashSet<>(Arrays.asList("Yard", "Shed")));
    }

    /**
     * Clearing the page showing empties its keys and keeps them, and lets the active locomotive go only from its own
     * page; then redraws the keys and the controls.
     *
     * MUTATION: in `LocMappingPages.doClearCurrentPage`, leave out `page.containsValue(...)`, and clearing another
     * page lets the train go.
     */
    @Test
    public void testClearingAPageLetsItsOwnTrainGoOnly()
    {
        List<String> said = onTheEventThread(() ->
        {
            Fake ui = new Fake(2);
            Locomotive mine = train("Mine");
            ui.put(1, mine);
            ui.put(2, train("Other"));
            ui.active = mine;
            ui.showing = 2;
            LocMappingPages pages = new LocMappingPages(ui);

            pages.doClearCurrentPage();
            List<String> out = new ArrayList<>(Arrays.asList(ui.pages.get(1).values().toString(), name(ui.active),
                ui.did.toString()));
            ui.showing = 1;
            pages.doClearCurrentPage();
            out.addAll(Arrays.asList(ui.pages.get(0).values().toString(), name(ui.active)));
            return out;
        });

        assertEquals(said, Arrays.asList("[null, null, null]", "Mine", "[repaintMappings, repaintLoc false]",
            "[null, null, null]", "null"), "another page's train stays, its own goes");
    }

    /**
     * The tabs are worded afresh, and the label too unless it says nothing.
     *
     * MUTATION: in `LocMappingPages.retitlePages`, leave out the `isEmpty()` test, and an empty label is worded.
     */
    @Test
    public void testRetitlingWordsEveryTabAndALabelThatSaysSomething()
    {
        List<String> said = onTheEventThread(() ->
        {
            Fake ui = new Fake(3);
            ui.names.put(2, "Yard");
            ui.showing = 2;
            ui.label.setText("old");
            LocMappingPages pages = new LocMappingPages(ui);

            pages.retitlePages();
            List<String> out = new ArrayList<>(Arrays.asList(ui.tabs.getTitleAt(0), ui.tabs.getTitleAt(1),
                ui.tabs.getTitleAt(2), ui.label.getText()));
            ui.label.setText("");
            pages.retitlePages();
            out.add(ui.label.getText());
            return out;
        });

        assertEquals(said, Arrays.asList("Page 1", "Yard", "Page 3", "Yard", ""));
    }

    /**
     * Pages after the first wear their number as a blue badge on a copy of the icon, a circle for one digit and longer
     * for two; the first page and no icon get none.
     *
     * MUTATION: in `LocMappingPages.withPageBadge`, make the badge `tall` wide whatever the number, and page 12's is a
     * circle too.
     */
    @Test
    public void testThePageNumberIsABadgeOnACopy()
    {
        BufferedImage icon = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        BufferedImage three = LocMappingPages.withPageBadge(icon, 3), twelve = LocMappingPages.withPageBadge(icon, 12);

        assertSame(LocMappingPages.withPageBadge(icon, 1), icon, "page 1 wears a badge");
        assertNull(LocMappingPages.withPageBadge(null, 3), "no icon");
        assertEquals(painted(icon, true), 0, "the shared icon was painted on");
        assertTrue(colors(three).contains(0xFF2675BF) && colors(three).contains(0xFFFFFFFF), "no blue badge with a "
            + "white number: " + colors(three));
        assertTrue(Math.abs(painted(three, true) - painted(three, false)) <= 1, "page 3's badge is not round: "
            + painted(three, true) + " wide, " + painted(three, false) + " tall");
        assertTrue(painted(twelve, true) > painted(three, true) + 2, "page 12's badge is no longer than page 3's");
    }

    /** A locomotive with no station behind it, at an address of its own. */
    private static Locomotive train(String name)
    {
        return new MarklinLocomotive(null, 3 + name.length(), Locomotive.decoderType.DCC, name);
    }

    private static String name(Locomotive l)
    {
        return l == null ? "null" : l.getName();
    }

    /** Pages 2, 3 and 4's tab titles, then page 4's label, as the pages word them now. */
    private static List<String> words(LocMappingPages pages)
    {
        return Arrays.asList(pages.getLocMappingPageTabTitle(2), pages.getLocMappingPageTabTitle(3),
            pages.getLocMappingPageTabTitle(4), pages.getPageLabelText(4));
    }

    /** How many pixels are painted across the picture's middle row (`across`), or down its middle column. */
    private static int painted(BufferedImage picture, boolean across)
    {
        int n = 0;
        for (int i = 0; i < (across ? picture.getWidth() : picture.getHeight()); i++)
            if ((across ? picture.getRGB(i, picture.getHeight() / 2) : picture.getRGB(picture.getWidth() / 2, i))
                >>> 24 != 0) n++;
        return n;
    }

    /** The opaque colors in the picture. */
    private static Set<Integer> colors(BufferedImage picture)
    {
        Set<Integer> out = new HashSet<>();
        for (int x = 0; x < picture.getWidth(); x++)
            for (int y = 0; y < picture.getHeight(); y++)
                if (picture.getRGB(x, y) >>> 24 == 0xFF) out.add(picture.getRGB(x, y));
        return out;
    }

    /** The main window as the pages see it: pages of three keys, a tab each, the label, the menu; what it was told. */
    private static final class Fake implements LocMappingPages.Keyboard
    {
        final List<Map<JButton, Locomotive>> pages = new ArrayList<>();
        final Map<Integer, String> names = new HashMap<>();
        final JLabel label = new JLabel("");
        final JTabbedPane tabs = new JTabbedPane();
        final JMenu menu = new JMenu();
        final JCheckBoxMenuItem showTabs = new JCheckBoxMenuItem("Show Page Tabs");
        final List<String> did = new ArrayList<>();
        int showing = 1;
        Locomotive active;

        Fake(int count)
        {
            for (int page = 0; page < count; page++)
            {
                Map<JButton, Locomotive> keys = new LinkedHashMap<>();
                for (String key : new String[] {"A", "S", "D"}) keys.put(new JButton(key), null);
                this.pages.add(keys);
                this.tabs.addTab("?", new JPanel());
            }
        }

        /** Puts l on the first key of page. */
        void put(int page, Locomotive l)
        {
            this.pages.get(page - 1).entrySet().iterator().next().setValue(l);
        }

        @Override
        public int getNumLocMappings()
        {
            return this.pages.size();
        }

        @Override
        public int getLocMappingNumber()
        {
            return this.showing;
        }

        @Override
        public Map<JButton, Locomotive> currentLocMapping()
        {
            return this.pages.get(this.showing - 1);
        }

        @Override
        public Map<Integer, String> pageNames()
        {
            return this.names;
        }

        @Override
        public Locomotive getActiveLoc()
        {
            return this.active;
        }

        @Override
        public void releaseActiveLoc()
        {
            this.active = null;
        }

        @Override
        public JLabel locMappingNumberLabel()
        {
            return this.label;
        }

        @Override
        public JTabbedPane locKeyTabs()
        {
            return this.tabs;
        }

        @Override
        public JMenu locomotiveControlMenu()
        {
            return this.menu;
        }

        @Override
        public JCheckBoxMenuItem showPageTabsPreference()
        {
            return this.showTabs;
        }

        @Override
        public void repaintLoc(boolean force, List<Locomotive> updatedLocs)
        {
            this.did.add("repaintLoc " + force);
        }

        @Override
        public void repaintMappings()
        {
            this.did.add("repaintMappings");
        }
    }
}
