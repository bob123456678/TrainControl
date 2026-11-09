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
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Reflect;

/**
 * FR-175: pages follow the order of the `seite` entries in gleisbild.cs2, not their names and not their ids.  The user
 * opens the layout editor and presses the up and down arrows under the Page heading; what comes of it is the layout's
 * index, the setup file, and the order every door shows.
 *
 * Each claim has a window of its own over a fresh sandboxed copy of the five-page test layout, whose index is in name
 * order with ids 5, 1, 2, 3, 4 (so name order, file order and id order are three different things).
 */
public class testPagesFollowTheFileOrder extends support.PageWindowTest
{
    private static final List<String> BY_NAME = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking",
        "4 - Combined", "5 - Test");

    /** Opens the window over the sandbox, whose index first lists the pages in this order (null: as it is). */
    private void open(List<String> fileOrder) throws Exception
    {
        if (fileOrder != null) writeIndex(fileOrder);

        startTheWindow(5);
    }

    /** Presses an arrow in the editor on this page and waits until the index lists the pages in this order. */
    private void movePage(String page, String arrow, List<String> expected) throws Exception
    {
        edit(page);

        press(editorOn(page), arrow);

        assertTrue(Components.waitFor(() -> namesInTheIndex().equals(expected), 15000), "the index is not in the new"
            + " order: " + namesInTheIndex());

        Components.settle();
    }

    /** Opens the editor on this page, in track mode, and waits for it. */
    private void edit(String page) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor(page, false, null));

        assertTrue(Components.waitFor(() -> editorOn(page) != null, 15000), "no editor opened on " + page);

        Components.settle();
    }

    /**
     * The layout whose pages are in file order 3, 1, 5, 2, 4: not name order, not id order.  The order every door shows
     * is the file's.
     *
     * MUTATION: `getLayoutList` sorts by name again (`Collections.sort` back in MarklinControlStation), and this fails
     * at the model.
     */
    @Test
    public void testALayoutNotInNameOrderShowsItsFileOrderInEveryDoor() throws Exception
    {
        List<String> order = Arrays.asList("3 - Top Parking", "1 - Main", "5 - Test", "2 - Bottom", "4 - Combined");

        open(order);

        assertEquals(model.getLayoutList(), order, "the model does not list the pages in file order");

        assertEquals(comboOrder(), order, "the main window's page list is not in file order");

        assertEquals(editMenuOrder(), order, "Layouts > Edit Layout Page does not list the pages in file order");

        edit("3 - Top Parking");

        assertEquals(sidebarOrder(editorOn("3 - Top Parking")), order, "the editor's page list is not in file order");

        assertTrue(!arrow(editorOn("3 - Top Parking"), "layout.ui.tooltipPageIsFirst").isEnabled(),
            "the first page's up arrow is lit");

        assertTrue(arrow(editorOn("3 - Top Parking"), "layout.ui.tooltipMovePageDown").isEnabled(),
            "the first page's down arrow is grayed");
    }

    /**
     * A layout already in name order shows no change: opening it adds only the mark of the one-time link conversion to
     * its index (FR-175), and the list is the name order the old code showed.
     *
     * MUTATION: write the index when a layout is loaded (any other write in `syncLayouts`), and the bytes differ.
     */
    @Test
    public void testALayoutAlreadyInNameOrderShowsNoChange() throws Exception
    {
        String before = new String(Files.readAllBytes(indexFile().toPath()), java.nio.charset.StandardCharsets.ISO_8859_1);

        startTheWindow(5);

        assertEquals(model.getLayoutList(), BY_NAME, "the list of a layout in name order changed");

        assertEquals(comboOrder(), BY_NAME, "the main window's list of a layout in name order changed");

        String after = new String(Files.readAllBytes(indexFile().toPath()), java.nio.charset.StandardCharsets.ISO_8859_1);

        assertEquals(after.replaceFirst("traincontrol\\r?\\n \\.linksByPageId=1\\r?\\n", ""), before,
            "opening a layout in name order rewrote its index");
    }

    /**
     * Down arrow on 1 - Main, in the editor: saved at once; the index lists 2 - Bottom first, every page keeps its id,
     * the setup file is byte for byte as it was, and the editor stays on 1 - Main with the new order in its list.
     *
     * MUTATION: the writer renumbers pages by position (a fresh `.id` per `seite` in `writeLayoutIndex`), and the ids
     * and the setup's keys no longer agree.
     */
    @Test
    public void testMovingAPageKeepsEveryIdAndTheSetup() throws Exception
    {
        open(null);

        Map<String, String> ids = idsInTheIndex();

        byte[] setup = Files.readAllBytes(setupFile().toPath());

        edit("1 - Main");

        press(editorOn("1 - Main"), "layout.ui.tooltipMovePageDown");

        List<String> moved = Arrays.asList("2 - Bottom", "1 - Main", "3 - Top Parking", "4 - Combined", "5 - Test");

        assertTrue(Components.waitFor(() -> namesInTheIndex().equals(moved), 15000), "the index is not in the new order: "
            + namesInTheIndex());

        assertTrue(Components.waitFor(() -> model.getLayoutList().equals(moved), 15000), "the model is not in the new"
            + " order: " + model.getLayoutList());

        Components.settle();

        assertEquals(idsInTheIndex(), ids, "a page changed its id");

        assertEquals(Files.readAllBytes(setupFile().toPath()), setup, "moving a page rewrote the autonomy setup");

        assertTrue(Components.waitFor(() -> editorOn("1 - Main") != null, 15000), "the editor left 1 - Main: "
            + titles());

        assertEquals(sidebarOrder(editorOn("1 - Main")), moved, "the editor's page list is not in the new order");

        assertEquals(comboOrder(), moved, "the main window's page list is not in the new order");

        assertEquals(editMenuOrder(), moved, "Layouts > Edit Layout Page is not in the new order");

        assertEquals(editors().size(), 1, "more than one editor is open: " + titles());
    }

    /**
     * Moving 3 - Top Parking up one place leaves every arrow's number as it was: a number is a place in the name-sorted
     * list, which does not move, so no page file is rewritten.
     *
     * MUTATION: `reorder` writes the index through `writeIndexAndKeepLinksAimed` after sorting nothing (re-aiming against
     * the shown order), and the numbers on pages with arrows to the moved page change.
     */
    @Test
    public void testMovingAPageLeavesEveryArrowNumberAlone() throws Exception
    {
        open(null);

        Map<String, List<Integer>> before = new LinkedHashMap<>();

        for (String page : BY_NAME) before.put(page, arrowsIn(page));

        movePage("3 - Top Parking", "layout.ui.tooltipMovePageUp",
            Arrays.asList("1 - Main", "3 - Top Parking", "2 - Bottom", "4 - Combined", "5 - Test"));

        for (String page : BY_NAME) assertEquals(arrowsIn(page), before.get(page), "an arrow on " + page + " was renumbered");
    }

    /**
     * A rearranged layout is the same after the window is closed and the layout opened again.
     *
     * MUTATION: the order is kept only in memory (the index written in name order), and the reopened list differs.
     */
    @Test
    public void testARearrangedLayoutSurvivesCloseAndReopen() throws Exception
    {
        open(null);

        final List<String> moved = Arrays.asList("1 - Main", "2 - Bottom", "3 - Top Parking", "5 - Test", "4 - Combined");

        movePage("5 - Test", "layout.ui.tooltipMovePageUp", moved);

        stopTheWindow();

        startTheWindow(5);

        assertEquals(model.getLayoutList(), moved, "the reopened layout is not in the rearranged order");

        assertEquals(comboOrder(), moved, "the reopened window's page list is not in the rearranged order");
    }

    /**
     * What 3.0.8 does with a rearranged layout is safe: it lists pages by name, and every name still has the id it had,
     * so the autonomy setup finds its settings.
     *
     * MUTATION: as for the ids above.
     */
    @Test
    public void testAnOlderVersionSortingByNameStillFindsEveryId() throws Exception
    {
        open(null);

        Map<String, String> ids = idsInTheIndex();

        edit("2 - Bottom");

        press(editorOn("2 - Bottom"), "layout.ui.tooltipMovePageDown");

        assertTrue(Components.waitFor(() -> namesInTheIndex().indexOf("3 - Top Parking")
            < namesInTheIndex().indexOf("2 - Bottom"), 15000), "the page did not move: " + namesInTheIndex());

        Components.settle();

        List<String> byName = new ArrayList<>(namesInTheIndex());

        Collections.sort(byName);

        assertEquals(byName, BY_NAME, "the index lost or gained a page");

        for (String name : byName) assertEquals(idsInTheIndex().get(name), ids.get(name), name + " has another id");
    }

    /**
     * A new page goes at the end, and so does a copy, whatever their names sort to.
     *
     * MUTATION: Add puts the page where its name sorts (the list sorted before the write), and the new page is first.
     */
    @Test
    public void testANewPageAndACopyGoAtTheEnd() throws Exception
    {
        open(Arrays.asList("3 - Top Parking", "1 - Main", "5 - Test", "2 - Bottom", "4 - Combined"));

        edit("1 - Main");

        try (Answering answering = Answering.start(message -> Answering.type("0 - First")))
        {
            press(editorOn("1 - Main"), "ui.main.toolbar.addBlackPage");

            assertTrue(Components.waitFor(() -> namesInTheIndex().contains("0 - First"), 15000), "no page was added");
        }

        assertEquals(namesInTheIndex().get(5), "0 - First", "the new page is not last: " + namesInTheIndex());

        assertTrue(Components.waitFor(() -> editorOn("0 - First") != null, 15000), "the editor did not show the new page");

        Components.settle();

        press(editorOn("0 - First"), "ui.main.toolbar.duplicateCurrentPage");

        assertTrue(Components.waitFor(() -> namesInTheIndex().size() == 7, 15000), "no copy was made");

        assertEquals(namesInTheIndex().get(6), "0 - First copy", "the copy is not last: " + namesInTheIndex());
    }

    /**
     * Sort Pages by Name writes name order once, on a layout in file order, keeping every id and the setup as it was,
     * and leaves the main window on the page it showed while the editor is on another.
     *
     * MUTATION: `sortPagesByName` does nothing, and the index stays in file order; the main window put back after the
     * editor arrives (the posted `setSelectedItem` in `runPageEdit`) left out, and the main window follows the editor
     * to 1 - Main.
     */
    @Test
    public void testSortPagesByNameWritesNameOrderOnce() throws Exception
    {
        open(Arrays.asList("3 - Top Parking", "1 - Main", "5 - Test", "2 - Bottom", "4 - Combined"));

        Map<String, String> ids = idsInTheIndex();

        byte[] setup = Files.readAllBytes(setupFile().toPath());

        edit("1 - Main");

        final javax.swing.JComboBox<?> shown = Components.find(ui, javax.swing.JComboBox.class,
            b -> b.getItemCount() == 5 && "3 - Top Parking".equals(String.valueOf(b.getItemAt(0))));

        Components.select(shown, "5 - Test");

        Components.settle();

        javax.swing.JMenu tools = Components.find(editorOn("1 - Main"), javax.swing.JMenu.class,
            m -> I18n.t("layout.ui.menuTools").equals(m.getText()));

        assertNotNull(tools, "the editor has no Tools menu");

        Components.press(tools);

        Components.press(Components.withText(editorOn("1 - Main"), I18n.t("ui.main.toolbar.sortPages")));

        assertTrue(Components.waitFor(() -> namesInTheIndex().equals(BY_NAME), 15000), "the index is not in name order: "
            + namesInTheIndex());

        Components.settle();

        assertEquals(idsInTheIndex(), ids, "a page changed its id");

        assertEquals(Files.readAllBytes(setupFile().toPath()), setup, "sorting rewrote the autonomy setup");

        // The model follows the file by a posted refresh, not with it
        assertTrue(Components.waitFor(() -> model.getLayoutList().equals(BY_NAME), 15000),
            "the model is not in name order: " + model.getLayoutList());

        assertTrue(editorOn("1 - Main") != null, "the editor left its page");

        assertFalse(Components.waitFor(() -> !"5 - Test".equals(String.valueOf(shown.getSelectedItem())), 1500),
            "Sort Pages by Name moved the main window off 5 - Test to " + shown.getSelectedItem());
    }

    /**
     * Sort Pages by Name sorts by the letters, capitals and accents aside: a page added as "apple yard" goes before one
     * added as "Zebra", and "\u00d6lberg" between them, on any machine: here one set to Swedish, whose own order puts
     * \u00d6 after Z.
     *
     * MUTATION: sort with `Collections.sort` alone, or `String.CASE_INSENSITIVE_ORDER`, in `LayoutFileActions.sorted`, and
     * this fails.
     */
    @Test
    public void testSortPagesByNameIgnoresCapitals() throws Exception
    {
        open(null);

        edit("1 - Main");

        String on = "1 - Main";

        for (String name : new String[] {"Zebra", "\u00d6lberg", "apple yard"})
        {
            try (Answering answering = Answering.start(message -> Answering.type(name)))
            {
                press(editorOn(on), "ui.main.toolbar.addBlackPage");

                assertTrue(Components.waitFor(() -> namesInTheIndex().contains(name), 15000), "no page was added");
            }

            assertTrue(Components.waitFor(() -> editorOn(name) != null, 15000), "the editor did not show " + name);

            Components.settle();

            on = name;
        }

        javax.swing.JMenu tools = Components.find(editorOn(on), javax.swing.JMenu.class,
            m -> I18n.t("layout.ui.menuTools").equals(m.getText()));

        Components.press(tools);

        java.util.Locale was = java.util.Locale.getDefault();
        List<String> expected = new ArrayList<>(BY_NAME);

        expected.addAll(Arrays.asList("apple yard", "\u00d6lberg", "Zebra"));

        try
        {
            java.util.Locale.setDefault(new java.util.Locale("sv"));

            Components.press(Components.withText(editorOn(on), I18n.t("ui.main.toolbar.sortPages")));

            assertTrue(Components.waitFor(() -> namesInTheIndex().equals(expected), 15000), "the index is not in the"
                + " letters' order: " + namesInTheIndex());
        }
        finally
        {
            java.util.Locale.setDefault(was);
        }
    }

    /**
     * On a Central Station layout (not the user's own folder) the arrows are refused as the other page edits are, and
     * nothing is written.
     *
     * MUTATION: the remote-layout refusal dropped from `LayoutFileActions.reorder`, and the index is rewritten.
     */
    @Test
    public void testAStationLayoutRefusesTheArrows() throws Exception
    {
        open(null);

        edit("1 - Main");

        byte[] before = Files.readAllBytes(indexFile().toPath());

        java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        String folder = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            try
            {
                prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

                press(editorOn("1 - Main"), "layout.ui.tooltipMovePageDown");

                assertTrue(answering.awaitAsked(1, 10000), "the arrow said nothing on a Central Station layout");
            }
            finally
            {
                prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);
            }

            assertEquals(answering.asked(), Collections.singletonList(
                I18n.t("layout.ui.errorCannotManipulateRemoteLayouts")), "the arrow did not refuse as the other edits do");
        }

        Components.settle();

        assertEquals(Files.readAllBytes(indexFile().toPath()), before, "a refused arrow rewrote the index");
    }

    // ------------------------------------------------------------------------------------------------ the doors

    private List<String> comboOrder() throws Exception
    {
        java.lang.reflect.Field field = Reflect.field(TrainControlUI.class, "LayoutList");

        final javax.swing.JComboBox<?> box = (javax.swing.JComboBox<?>) field.get(ui);

        return Components.onTheEventThread(() ->
        {
            List<String> out = new ArrayList<>();

            for (int i = 0; i < box.getItemCount(); i++) out.add(String.valueOf(box.getItemAt(i)));

            return out;
        });
    }

    private List<String> editMenuOrder() throws Exception
    {
        final javax.swing.JMenu menu = (javax.swing.JMenu) Reflect.get(TrainControlUI.class, ui, "editPageMenu");

        assertNotNull(menu, "the Layouts menu has no Edit Layout Page");

        return Components.onTheEventThread(() ->
        {
            List<String> out = new ArrayList<>();

            for (int i = 0; i < menu.getItemCount(); i++) out.add(menu.getItem(i).getText());

            return out;
        });
    }

    private static List<String> sidebarOrder(LayoutEditor editor)
    {
        javax.swing.JList<?> list = Components.find(editor, javax.swing.JList.class, null);

        assertNotNull(list, "the editor has no page list");

        return Components.onTheEventThread(() ->
        {
            List<String> out = new ArrayList<>();

            for (int i = 0; i < list.getModel().getSize(); i++) out.add(String.valueOf(list.getModel().getElementAt(i)));

            return out;
        });
    }

    /** The strip's icon with this bundle key as its tooltip. */
    private static AbstractButton arrow(LayoutEditor editor, String key)
    {
        for (AbstractButton button : Components.all(editor, AbstractButton.class,
            b -> "toolBarButton".equals(b.getClientProperty("JButton.buttonType"))))
        {
            if (I18n.t(key).equals(button.getToolTipText())) return button;
        }

        throw new AssertionError("the editor's strip has no icon for " + I18n.t(key));
    }

    private static void press(LayoutEditor editor, String key)
    {
        Components.press(arrow(editor, key));
    }

    // ------------------------------------------------------------------------------------------------ the folder

    private File indexFile()
    {
        return new File(sandbox.getFolder(), "config/gleisbild.cs2");
    }

    private File setupFile()
    {
        return new File(sandbox.getFolder(), "config/autonomy/setup.json");
    }

    /** The index's pages, in file order, with their ids. */
    private Map<String, String> idsInTheIndex()
    {
        Map<String, String> ids = new LinkedHashMap<>();

        String id = null;

        try
        {
            for (String line : Files.readAllLines(indexFile().toPath(), StandardCharsets.UTF_8))
            {
                if (line.startsWith(" .id=")) id = line.substring(5).trim();
                else if (line.startsWith(" .name=")) ids.put(line.substring(7).trim(), id);
            }
        }
        catch (java.nio.file.NoSuchFileException replacedJustNow)
        {
            // The writer swaps the file in; asked again by the wait
            return new LinkedHashMap<>();
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }

        return ids;
    }

    /** The `.artikel` of every `pfeil` tile in a page's file, in file order. */
    private List<Integer> arrowsIn(String page) throws Exception
    {
        File file = new File(sandbox.getFolder(), "config/gleisbilder/" + page + ".cs2");

        List<Integer> out = new ArrayList<>();

        boolean arrow = false;

        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
        {
            if (line.trim().equals("element")) arrow = false;
            else if (line.trim().equals(".typ=pfeil")) arrow = true;
            else if (arrow && line.trim().startsWith(".artikel=")) out.add(Integer.parseInt(line.trim().substring(9)));
        }

        return out;
    }

    private List<String> namesInTheIndex()
    {
        return new ArrayList<>(idsInTheIndex().keySet());
    }

    /** Rewrites the sandbox's index with its pages in this order and the ids they had. */
    private void writeIndex(List<String> order) throws Exception
    {
        Map<String, String> ids = idsInTheIndex();

        StringBuilder out = new StringBuilder("[gleisbild]\nversion\n .major=1\ngroesse\n");

        for (String name : order) out.append("seite\n .id=").append(ids.get(name)).append("\n .name=").append(name).append("\n");

        Files.write(indexFile().toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------------------------------------ the windows

    private static List<LayoutEditor> editors()
    {
        return support.EditorsOpen.editors();
    }

    private static List<String> titles()
    {
        return support.EditorsOpen.titles();
    }

    private static LayoutEditor editorOn(String page)
    {
        return support.EditorsOpen.editorOn(page);
    }
}
