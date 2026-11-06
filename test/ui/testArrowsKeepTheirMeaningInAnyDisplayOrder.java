package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.layouteditor.LayoutEditorAddressPopup;
import org.traincontrol.util.I18n;
import support.Components;
import support.Mouse;

/**
 * FR-175: pages are SHOWN in the order of the layout file, but an arrow's number stays a place in the NAME-SORTED list,
 * in the file, in memory and in the autonomy graph, as it was and as 3.0.8 reads it.  The user clicks an arrow, picks
 * a page for one, and moves pages with the editor's arrows; what comes of it is the page shown, the number stored, the
 * files, and the graph.
 */
public class testArrowsKeepTheirMeaningInAnyDisplayOrder extends support.PageWindowTest
{
    private static final List<String> NOT_BY_NAME = Arrays.asList("3 - Top Parking", "1 - Main", "5 - Test",
        "2 - Bottom", "4 - Combined");

    @Override
    protected File fixtureFor(java.lang.reflect.Method test)
    {
        // A Central Station layout's index, which is not in name order: Startpackung, Main, Something, Main Layout
        return new File(test.getName().contains("Station") ? "test/cs3_v261" : "test/test_layout");
    }

    /**
     * On a local layout shown in file order, clicking an arrow shows the page the name-sorted list says, in the main
     * window and in a page popup, and the autonomy session is built over the name-sorted pages.
     *
     * MUTATION: `goToLayoutPage` in `TrainControlUI` or `LayoutPopupUI` indexes the shown list instead of the
     * name-sorted one, or `AutonomyLifecycle` feeds the session the shown list, and this fails.
     */
    @Test
    public void testClickingAnArrowOnALocalLayoutShownInFileOrder() throws Exception
    {
        writeIndex(NOT_BY_NAME);

        startTheWindow(5);

        checkTheDoors();
    }

    /**
     * The same on a copy of the Central Station layout in test/cs3_v261, whose index is not in name order, with an
     * arrow added to the first page that leads to the page the name-sorted list puts second.
     *
     * MUTATION: as above.
     */
    @Test
    public void testClickingAnArrowOnAStationLayoutShownInFileOrder() throws Exception
    {
        File page = new File(sandbox.getFolder(), "config/gleisbilder/Startpackung.cs2");

        Files.write(page.toPath(), (new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8)
            + "\nelement\n .id=0x101\n .typ=pfeil\n .drehung=1\n .artikel=1\n").getBytes(StandardCharsets.UTF_8));

        startTheWindow(4);

        checkTheDoors();
    }

    private void checkTheDoors() throws Exception
    {
        final List<String> byName = sortedByName();

        assertNotEquals(model.getLayoutList(), byName, "precondition: the layout is shown in name order");

        assertEquals(model.getLayoutList().size(), byName.size());

        AutonomySession session = ui.getAutonomySession();

        if (session != null)
        {
            List<String> sessionPages = new ArrayList<>();

            for (org.traincontrol.base.LayoutDiagram each : session.getPages()) sessionPages.add(each.getName());

            assertEquals(sessionPages, byName, "the autonomy session is not built over the name-sorted pages");
        }

        final javax.swing.JComboBox<?> shown = combo();

        SwingUtilities.invokeAndWait(ui::showLayoutTab);

        int clicked = 0;

        for (final String page : model.getLayoutList())
        {
            LayoutDiagramComponent arrow = null;

            for (LayoutDiagramComponent tile : model.getLayout(page).getAll())
            {
                if (arrow == null && tile.isLink() && tile.getRawAddress() >= 0 && !byName.get(tile.getRawAddress()).equals(page)) arrow = tile;
            }

            if (arrow == null) continue;

            final int number = arrow.getRawAddress();

            Components.select(shown, page);

            assertTrue(Components.waitFor(() -> page.equals(String.valueOf(shown.getSelectedItem())), 5000), "the page list"
                + " would not show " + page);

            Components.settle();

            Mouse.click(arrowOn(ui, number));

            assertTrue(Components.waitFor(() -> byName.get(number).equals(String.valueOf(shown.getSelectedItem())), 5000),
                "clicking the arrow numbered " + number + " on " + page + " shows " + shown.getSelectedItem()
                + ", not the page at that place in the name-sorted list, " + byName.get(number));

            // And in a page popup showing the same page
            final LayoutPopupUI popup = showPopup(page);

            Mouse.click(arrowOn(popup, number));

            assertTrue(Components.waitFor(() -> byName.get(number).equals(popupPage(popup)), 5000), "the popup arrow numbered "
                + number + " on " + page + " shows " + popupPage(popup) + ", not " + byName.get(number));

            SwingUtilities.invokeAndWait(popup::dispose);

            clicked++;
        }

        assertTrue(clicked > 0, "precondition: no arrow could be clicked");
    }

    /** The label of the arrow tile with this number, showing in this window. */
    private static LayoutLabel arrowOn(java.awt.Component window, int number)
    {
        assertTrue(Components.waitFor(() -> Components.find(window, LayoutLabel.class, l -> l.isShowing()
            && l.getComponent() != null && l.getComponent().isLink() && l.getComponent().getRawAddress() == number) != null,
            5000), "no arrow numbered " + number + " is showing");

        return Components.find(window, LayoutLabel.class, l -> l.isShowing() && l.getComponent() != null
            && l.getComponent().isLink() && l.getComponent().getRawAddress() == number);
    }

    @SuppressWarnings("unchecked")
    private List<LayoutPopupUI> popups()
    {
        try
        {
            java.lang.reflect.Field field = org.traincontrol.gui.TrainControlUI.class.getDeclaredField("popups");

            field.setAccessible(true);

            return (List<LayoutPopupUI>) field.get(ui);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** Opens a page popup as the Pop Up menu does (on the page list's place, whatever the page) and waits for it. */
    private LayoutPopupUI showPopup(final String page) throws Exception
    {
        final LayoutPopupUI popup = Components.onTheEventThread(() ->
        {
            LayoutPopupUI made = new LayoutPopupUI(model.getLayout(page), 30, ui, combo2().getSelectedIndex());

            made.render();

            // Kept by the window, as `showLayoutPopup` keeps it, so the window's refreshes reach it
            popups().add(made);

            return made;
        });

        assertTrue(Components.waitFor(popup::isShowing, 10000), "the popup did not open");

        Components.settle();

        return popup;
    }

    private javax.swing.JComboBox<?> combo2()
    {
        try
        {
            return combo();
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A page popup whose page is at another place in the shown list than in the name-sorted list stays on its page, and
     * keeps its size and place, when the diagrams are refreshed (a Show Addresses tick, an edit, a page action).
     *
     * MUTATION: `LayoutPopupUI.refreshDiagram` passes its saved shown place to the arrow door again (the place is read
     * as an arrow's number), and the popup jumps to another page.
     */
    @Test
    public void testAPopupStaysOnItsPageWhenTheDiagramsAreRefreshed() throws Exception
    {
        writeIndex(NOT_BY_NAME);

        startTheWindow(5);

        refreshAPopup();
    }

    /** The same on a copy of the Central Station layout. */
    @Test
    public void testAPopupOnAStationLayoutStaysOnItsPageWhenTheDiagramsAreRefreshed() throws Exception
    {
        startTheWindow(4);

        refreshAPopup();
    }

    private void refreshAPopup() throws Exception
    {
        final List<String> byName = sortedByName();

        String page = null;

        for (String each : model.getLayoutList()) if (page == null && model.getLayoutList().indexOf(each) != byName.indexOf(each)) page = each;

        assertNotNull(page, "precondition: every page is at the same place in both lists");

        LayoutPopupUI popup = showPopup(page);

        assertEquals(popupPage(popup), page);

        java.awt.Rectangle bounds = Components.onTheEventThread(popup::getBounds);

        java.lang.reflect.Method refresh = org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod("updatePopups",
            boolean.class);

        refresh.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                refresh.invoke(ui, true);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        Components.settle();

        // The popup draws its page in a posted task: given time to move, if it is going to
        final LayoutPopupUI refreshed = popup;
        final String stays = page;

        assertFalse(Components.waitFor(() -> !stays.equals(popupPage(refreshed)), 1500), "a refresh moved the popup to"
            + " another page");

        assertEquals(popupPage(popup), page, "a refresh moved the popup to another page");

        assertEquals(Components.onTheEventThread(popup::getBounds), bounds, "a refresh changed the popup's size or place");
    }

    /**
     * A page added to the index while the editor asks about its unsaved work is still in the index after the move: the
     * new order is worked out when the write goes ahead.
     *
     * MUTATION: `movePage` works the new order out before it steps out (the old `order` captured early), and the late
     * page is dropped from the index.
     */
    @Test
    public void testAPageAddedWhileTheEditorAsksIsKeptByTheMove() throws Exception
    {
        startTheWindow(5);

        SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor("2 - Bottom", false, null));

        assertTrue(Components.waitFor(() -> support.EditorsOpen.editorOn("2 - Bottom") != null, 15000), "no editor");

        Components.settle();

        final org.traincontrol.gui.layouteditor.LayoutEditor editor = support.EditorsOpen.editorOn("2 - Bottom");

        final org.traincontrol.gui.LayoutLabel square = Components.find(editor, org.traincontrol.gui.LayoutLabel.class,
            l -> l.getComponent() != null && !l.getComponent().isText());

        SwingUtilities.invokeAndWait(() -> editor.rotate(square));

        try (support.Answering answering = support.Answering.start(message ->
        {
            if (!message.equals(I18n.t("layout.ui.confirmPageActionWithUnsavedWork"))) return support.Answering.pressAt(0);

            try
            {
                Files.copy(new File(sandbox.getFolder(), "config/gleisbilder/5 - Test.cs2").toPath(),
                    new File(sandbox.getFolder(), "config/gleisbilder/6 - Late.cs2").toPath());

                File index = new File(sandbox.getFolder(), "config/gleisbild.cs2");

                Files.write(index.toPath(), (new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8)
                    + "seite\n .id=9\n .name=6 - Late\n").getBytes(StandardCharsets.UTF_8));

                model.refreshLayouts();
            }
            catch (java.io.IOException e)
            {
                throw new IllegalStateException(e);
            }

            return support.Answering.press(I18n.t("layout.ui.switchDiscard"));
        }))
        {
            Components.press(Components.all(editor, javax.swing.AbstractButton.class,
                b -> I18n.t("layout.ui.tooltipMovePageDown").equals(b.getToolTipText())).get(0));

            assertTrue(answering.awaitAsked(1, 10000), "the move did not ask about the unsaved work");

            assertTrue(Components.waitFor(() -> namesInTheIndex().equals(Arrays.asList("1 - Main", "3 - Top Parking",
                "2 - Bottom", "4 - Combined", "5 - Test", "6 - Late")), 15000), "the page added meanwhile is not in the"
                + " moved index: " + namesInTheIndex());
        }
    }

    /**
     * The Autonomy menu lists the pages in the order they are shown (its Edit and Pages submenus), and the session's own
     * list, which links index, stays name-sorted.
     *
     * MUTATION: `AutonomyMenu.shownOrder` returns the session's list as it is, and the menus list by name.
     */
    @Test
    public void testTheAutonomyMenuListsPagesInTheOrderTheyAreShown() throws Exception
    {
        writeIndex(NOT_BY_NAME);

        startTheWindow(5);

        java.lang.reflect.Field field = org.traincontrol.gui.TrainControlUI.class.getDeclaredField("autonomyMenu");

        field.setAccessible(true);

        final javax.swing.JMenu menu = (javax.swing.JMenu) field.get(ui);

        SwingUtilities.invokeAndWait(() ->
        {
            menu.setSelected(true);
            menu.setSelected(false);
        });

        Components.settle();

        for (final String submenu : new String[] {"autosetup.ui.menuEditAutonomy", "autosetup.ui.btnExcludePage"})
        {
            final javax.swing.JMenu sub = Components.find(menu, javax.swing.JMenu.class, m -> I18n.t(submenu).equals(m.getText()));

            assertNotNull(sub, "the Autonomy menu has no " + I18n.t(submenu));

            List<String> listed = Components.onTheEventThread(() ->
            {
                List<String> out = new ArrayList<>();

                for (int i = 0; i < sub.getItemCount(); i++) out.add(sub.getItem(i).getText());

                return out;
            });

            List<String> expected = new ArrayList<>(NOT_BY_NAME);

            // Pages autonomy ignores are left out of the Edit submenu only
            if (submenu.equals("autosetup.ui.menuEditAutonomy"))
            {
                expected.removeAll(ui.getAutonomySession().getStore().getExcludedPages());
            }

            assertEquals(listed, expected, I18n.t(submenu) + " does not list the pages in the order they are shown");
        }

        List<String> session = new ArrayList<>();

        for (org.traincontrol.base.LayoutDiagram each : ui.getAutonomySession().getPages()) session.add(each.getName());

        assertEquals(session, sortedByName(), "the menu changed the session's own list");
    }

    /**
     * The autonomy graph is the same whichever order the pages are shown in: the same links, the same reduction.
     *
     * MUTATION: `AutonomyLifecycle` feeds the session the shown list, and the links join other pages' squares.
     */
    @Test
    public void testTheGraphDoesNotDependOnTheDisplayOrder() throws Exception
    {
        startTheWindow(5);

        String inNameOrder = graphFingerprint();

        stopTheWindow();

        writeIndex(NOT_BY_NAME);

        startTheWindow(5);

        assertEquals(graphFingerprint(), inNameOrder, "the graph changed with the order the pages are shown in");
    }

    /**
     * Moving a page changes the index and nothing else in the folder, and the graph built before and after is the
     * same.
     *
     * MUTATION: `reorder` re-aims the arrows (`writeIndexAndKeepLinksAimed` in place of `writeLayoutIndex`) after
     * sorting its lists, or rewrites a page, and a page file differs.
     */
    @Test
    public void testMovingAPageChangesOnlyTheIndexAndNotTheGraph() throws Exception
    {
        startTheWindow(5);

        String graph = graphFingerprint();

        List<String> was = namesInTheIndex();

        SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor("3 - Top Parking", false, null));

        assertTrue(Components.waitFor(() -> support.EditorsOpen.editorOn("3 - Top Parking") != null, 15000), "no editor");

        Components.settle();

        // Taken with the editor open: opening it writes its own undo copy and may refresh the configuration
        Map<String, String> before = fingerprint();

        Components.press(Components.all(support.EditorsOpen.editorOn("3 - Top Parking"), javax.swing.AbstractButton.class,
            b -> I18n.t("layout.ui.tooltipMovePageUp").equals(b.getToolTipText())).get(0));

        assertTrue(Components.waitFor(() -> !namesInTheIndex().equals(was), 15000), "the page did not move");

        Components.settle();

        Map<String, String> after = fingerprint();

        assertNotEquals(after.get("config/gleisbild.cs2"), before.get("config/gleisbild.cs2"), "the index did not change");

        after.remove("config/gleisbild.cs2");
        before.remove("config/gleisbild.cs2");

        assertEquals(after.keySet(), before.keySet(), "moving a page added or removed a file");

        List<String> changed = new ArrayList<>();

        for (String file : before.keySet()) if (!before.get(file).equals(after.get(file))) changed.add(file);

        assertEquals(changed.toString(), "[]", "moving a page changed a file other than the index");

        assertEquals(graphFingerprint(), graph, "moving a page changed the autonomy graph");
    }

    /**
     * A reader that lists pages by name (3.0.8) resolves every arrow to the same page after a move as before it: the
     * page files are the ones it had, and its name-sorted list is the same.
     *
     * MUTATION: as for the test above.
     */
    @Test
    public void testAReaderListingByNameResolvesEveryArrowAfterAMove() throws Exception
    {
        startTheWindow(5);

        Map<String, String> resolvedBefore = resolvedArrows();

        SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor("1 - Main", false, null));

        assertTrue(Components.waitFor(() -> support.EditorsOpen.editorOn("1 - Main") != null, 15000), "no editor");

        Components.settle();

        Components.press(Components.all(support.EditorsOpen.editorOn("1 - Main"), javax.swing.AbstractButton.class,
            b -> I18n.t("layout.ui.tooltipMovePageDown").equals(b.getToolTipText())).get(0));

        assertTrue(Components.waitFor(() -> namesInTheIndex().get(0).equals("2 - Bottom"), 15000), "the page did not move");

        Components.settle();

        assertEquals(resolvedArrows(), resolvedBefore, "an arrow leads to another page for a reader listing by name");
    }

    /**
     * The arrow picker lists the pages in the order they are shown and stores the name-sorted position of the page
     * picked, and shows the page at the stored position.
     *
     * MUTATION: `LayoutEditorAddressPopup` indexes `getLayoutList` instead of `getLayoutListByName`, and this fails.
     */
    @Test
    public void testThePickerShowsPagesInFileOrderAndStoresTheNameSortedPlace() throws Exception
    {
        writeIndex(NOT_BY_NAME);

        startTheWindow(5);

        final List<String> byName = sortedByName();

        LayoutDiagramComponent arrow = null;
        String onPage = null;

        for (String page : model.getLayoutList())
        {
            for (LayoutDiagramComponent tile : model.getLayout(page).getAll())
            {
                if (arrow == null && tile.isLink() && !tile.linksNowhere()) { arrow = tile; onPage = page; }
            }
        }

        assertNotNull(arrow, "precondition: the layout has no arrow");

        final LayoutDiagramComponent tile = arrow;
        final String here = onPage;

        final LayoutEditorAddressPopup picker = Components.onTheEventThread(
            () -> new LayoutEditorAddressPopup(tile, ui, here));

        final javax.swing.JComboBox<?> choices = Components.find(picker, javax.swing.JComboBox.class, null);

        List<String> listed = Components.onTheEventThread(() ->
        {
            List<String> out = new ArrayList<>();

            for (int i = 1; i < choices.getItemCount(); i++) out.add(String.valueOf(choices.getItemAt(i)));

            return out;
        });

        List<String> shownOrder = new ArrayList<>(model.getLayoutList());

        shownOrder.remove(here);

        assertEquals(listed, shownOrder, "the picker does not list the pages in the order they are shown");

        final int stored = tile.getRawAddress();

        SwingUtilities.invokeAndWait(() -> picker.setAddress(Integer.toString(stored + 1)));

        assertEquals(String.valueOf(Components.onTheEventThread(choices::getSelectedItem)), byName.get(stored),
            "the picker shows another page than the name-sorted list has at the arrow's number");

        final String other = shownOrder.get(0);

        SwingUtilities.invokeAndWait(() -> choices.setSelectedItem(other));

        assertEquals(Components.onTheEventThread(picker::getAddress), Integer.toString(byName.indexOf(other) + 1),
            "the picker does not store the name-sorted place of the page picked");
    }

    // ------------------------------------------------------------------------------------------------

    /** The shown pages sorted here, not by the model, so a model that sorts wrongly cannot agree with itself. */
    private List<String> sortedByName()
    {
        List<String> sorted = new ArrayList<>(model.getLayoutList());

        Collections.sort(sorted);

        return sorted;
    }

    private static String popupPage(LayoutPopupUI popup)
    {
        try
        {
            java.lang.reflect.Field field = LayoutPopupUI.class.getDeclaredField("layout");

            field.setAccessible(true);

            return ((org.traincontrol.base.LayoutDiagram) field.get(popup)).getName();
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private javax.swing.JComboBox<?> combo() throws Exception
    {
        java.lang.reflect.Field field = org.traincontrol.gui.TrainControlUI.class.getDeclaredField("LayoutList");

        field.setAccessible(true);

        return (javax.swing.JComboBox<?>) field.get(ui);
    }

    /** The links and the reduction of the autonomy graph, as sorted lines. */
    private String graphFingerprint() throws Exception
    {
        final AutonomySession session = ui.getAutonomySession();

        java.lang.reflect.Field portals = TileGraph.class.getDeclaredField("portals");

        portals.setAccessible(true);

        List<String> lines = new ArrayList<>();

        // The pages the graph is built over, in its order: a link's number indexes them
        List<String> built = new ArrayList<>();

        for (org.traincontrol.base.LayoutDiagram each : session.getPages()) built.add(each.getName());

        lines.add("pages " + built);

        for (Map.Entry<?, ?> link : ((Map<?, ?>) portals.get(session.getGraph())).entrySet())
        {
            lines.add("link " + link.getKey() + " > " + link.getValue());
        }

        for (Map.Entry<TileGraph.TileKey, GraphReducer.ReducedPoint> point : session.getReducer().getPoints().entrySet())
        {
            lines.add("point " + point.getKey() + " " + point.getValue().getName() + " " + point.getValue().getS88());
        }

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges()) lines.add("edge " + edge);

        Collections.sort(lines);

        assertTrue(lines.stream().anyMatch(l -> l.startsWith("link ")), "precondition: the graph has no link");

        return String.join("\n", lines);
    }

    /** Each page's arrows as the page a reader listing by name takes them to. */
    private Map<String, String> resolvedArrows() throws Exception
    {
        List<String> byName = new ArrayList<>(namesInTheIndex());

        Collections.sort(byName);

        Map<String, String> out = new LinkedHashMap<>();

        for (String page : byName)
        {
            List<String> leadsTo = new ArrayList<>();

            boolean arrow = false;

            for (String line : Files.readAllLines(new File(sandbox.getFolder(), "config/gleisbilder/" + page + ".cs2")
                .toPath(), StandardCharsets.UTF_8))
            {
                if (line.trim().equals("element")) arrow = false;
                else if (line.trim().equals(".typ=pfeil")) arrow = true;
                else if (arrow && line.trim().startsWith(".artikel="))
                {
                    int n = Integer.parseInt(line.trim().substring(9));

                    leadsTo.add(n >= 0 && n < byName.size() ? byName.get(n) : "nothing");
                }
            }

            out.put(page, leadsTo.toString());
        }

        return out;
    }

    private List<String> namesInTheIndex()
    {
        List<String> names = new ArrayList<>();

        try
        {
            for (String line : Files.readAllLines(new File(sandbox.getFolder(), "config/gleisbild.cs2").toPath(),
                StandardCharsets.UTF_8))
            {
                if (line.startsWith(" .name=")) names.add(line.substring(7).trim());
            }
        }
        catch (java.nio.file.NoSuchFileException replacedJustNow)
        {
            // The writer swaps the file in; asked again by the wait
            return new ArrayList<>();
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }

        return names;
    }

    /** Rewrites the sandbox's index with its pages in this order and the ids they had. */
    private void writeIndex(List<String> order) throws Exception
    {
        File index = new File(sandbox.getFolder(), "config/gleisbild.cs2");

        Map<String, String> ids = new LinkedHashMap<>();

        String id = null;

        for (String line : Files.readAllLines(index.toPath(), StandardCharsets.UTF_8))
        {
            if (line.startsWith(" .id=")) id = line.substring(5).trim();
            else if (line.startsWith(" .name=")) ids.put(line.substring(7).trim(), id);
        }

        StringBuilder out = new StringBuilder("[gleisbild]\nversion\n .major=1\ngroesse\n");

        for (String name : order) out.append("seite\n .id=").append(ids.get(name)).append("\n .name=").append(name).append("\n");

        Files.write(index.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Every file in the layout's folder, by its path there, with a digest of its bytes. */
    private Map<String, String> fingerprint() throws Exception
    {
        Map<String, String> files = new TreeMap<>();

        java.nio.file.Path root = sandbox.getFolder().toPath();

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");

        List<java.nio.file.Path> found = new ArrayList<>();

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(root))
        {
            walk.filter(Files::isRegularFile).forEach(found::add);
        }

        for (java.nio.file.Path file : found)
        {
            files.put(root.relativize(file).toString().replace('\\', '/'),
                java.util.Base64.getEncoder().encodeToString(digest.digest(Files.readAllBytes(file))));
        }

        return files;
    }
}
