package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * Layouts > Modify Layout edits the layout's folder: a page duplicated, renamed, added, combined or deleted is a file
 * written, renamed or removed, the page list and the index say the same, and the autonomy setup follows the page.  A
 * refusal changes nothing in the folder.
 *
 * Each claim has a window of its own over a fresh sandboxed copy of the five-page test layout, picks a page in the
 * page list, clicks the menu item, and answers the real dialogs.
 *
 * MUTATIONS, each seen red: the duplicate written blank (the duplicate claim); the rename made a duplicate (the rename
 * claim); the blank page given the current page's squares (the blank claim); the delete's file removal skipped (the
 * delete claim); the default of the delete question taken as Yes (the No claim); the taken-name check dropped (the
 * taken-name claim); the combined page left in autonomy (the combine claim); the no-links refusal dropped (the no-links
 * claim).
 */
public class testThePageMenuEditsTheLayoutFolder
{
    private support.LayoutSandbox sandbox;
    private MarklinControlStation model;
    private TrainControlUI ui;

    @BeforeMethod
    public void openTheWindow() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        sandbox = support.LayoutSandbox.open();

        model = support.TestStationAddress.withNoStation(() -> MarklinControlStation.init(null, true, true, false, false));

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        assertTrue(Components.waitFor(() -> model.getLayoutList().size() == 5, 10000), "precondition: the test layout"
            + " does not have its five pages: " + model.getLayoutList());

        Components.settle();
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheWindow() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }

            if (model != null) model.stop();
        }
        finally
        {
            ui = null;
            model = null;

            if (sandbox != null) sandbox.close();

            sandbox = null;
        }
    }

    /**
     * Duplicate Current Page writes a copy of the page under a new name: the same squares, its own file, in the index
     * and the page list, and shown.
     *
     * @throws Exception from the window
     */
    @Test
    public void testDuplicateCurrentPageWritesACopyOfThePage() throws Exception
    {
        final String page = "3 - Top Parking";
        final String copy = page + " copy";

        onPage(page);

        try (Answering answering = Answering.start(message -> null))
        {
            SwingUtilities.invokeLater(menuItem("ui.main.toolbar.duplicateCurrentPage")::doClick);

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(copy), 15000), "Duplicate Current Page"
                + " made no page called " + copy + ": " + model.getLayoutList());

            assertTrue(Components.waitFor(() -> copy.equals(pageList().getSelectedItem()), 15000), "the copy is not"
                + " shown in the page list: " + pageList().getSelectedItem());

            assertEquals(answering.asked(), Collections.emptyList(), "Duplicate Current Page asked something");
        }

        assertTrue(pageFile(copy).isFile(), "the copy has no file of its own: " + pageFile(copy));

        assertEquals(tilesIn(pageFile(copy)), tilesIn(pageFile(page)), "the copy's squares are not the page's");

        assertTrue(tilesIn(pageFile(page)).size() > 10, "precondition: the page has too few squares to compare");

        assertTrue(index().contains(".name=" + copy), "the layout's index does not list the copy");

        assertTrue(pageFile(page).isFile(), "the page duplicated is gone");
    }

    /**
     * Rename Current Page, answered with a new name, moves the page's file, the index entry and the autonomy setup's
     * page name over to it, and keeps nothing under the old one.
     *
     * @throws Exception from the window
     */
    @Test
    public void testRenameCurrentPageMovesItsFileAndItsSetup() throws Exception
    {
        final String page = "5 - Test";
        final String renamed = "5 - Renamed";

        String id = idInTheSetup(page);

        assertNotNull(id, "precondition: the setup does not know the page " + page);

        onPage(page);

        try (Answering answering = Answering.start(message -> Answering.type(renamed)))
        {
            SwingUtilities.invokeLater(menuItem("ui.main.toolbar.renameCurrentPage")::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Rename Current Page asked for no name");

            assertTrue(answering.asked().get(0).contains(page), "the name offered is not the page's own: "
                + answering.asked());

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(renamed)
                && !model.getLayoutList().contains(page), 15000), "the page list after the rename: "
                + model.getLayoutList());
        }

        assertTrue(pageFile(renamed).isFile(), "the renamed page has no file: " + pageFile(renamed));

        assertFalse(pageFile(page).exists(), "the page's file is still there under its old name");

        assertTrue(index().contains(".name=" + renamed) && !index().contains(".name=" + page + "\n"),
            "the layout's index does not follow the rename:\n" + index());

        assertTrue(Components.waitFor(() -> renamed.equals(namesInTheSetup().get(id)), 10000), "the autonomy setup"
            + " does not call the page by its new name: " + namesInTheSetup());
    }

    /**
     * Add Blank Page, answered with a name, writes a page of that name with nothing on it.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAddBlankPageWritesAnEmptyPage() throws Exception
    {
        final String blank = "P6 Blank";

        onPage("1 - Main");

        try (Answering answering = Answering.start(message -> Answering.type(blank)))
        {
            SwingUtilities.invokeLater(menuItem("ui.main.toolbar.addBlackPage")::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Add Blank Page asked for no name");

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(blank), 15000), "Add Blank Page made no"
                + " page called " + blank + ": " + model.getLayoutList());
        }

        assertTrue(pageFile(blank).isFile(), "the blank page has no file: " + pageFile(blank));

        assertEquals(tilesIn(pageFile(blank)), Collections.emptyList(), "the blank page has squares on it");

        assertTrue(index().contains(".name=" + blank), "the layout's index does not list the blank page");
    }

    /**
     * Delete Current Page, answered Yes, removes the page's file, its index entry and its name in the setup.
     *
     * @throws Exception from the window
     */
    @Test
    public void testDeleteCurrentPageAnsweredYesRemovesThePage() throws Exception
    {
        final String page = "4 - Combined";

        onPage(page);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            SwingUtilities.invokeLater(menuItem("ui.main.toolbar.deleteCurrentPage")::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Delete Current Page asked nothing");

            assertEquals(answering.asked().get(0), I18n.f("layout.ui.confirmDeleteLayoutPageAreYouSure", page));

            assertTrue(Components.waitFor(() -> !model.getLayoutList().contains(page), 15000), "the page is still"
                + " listed after the delete: " + model.getLayoutList());
        }

        assertFalse(pageFile(page).exists(), "the deleted page's file is still there");

        assertFalse(index().contains(".name=" + page), "the layout's index still lists the deleted page");

        assertTrue(Components.waitFor(() -> !namesInTheSetup().containsValue(page), 10000), "the autonomy setup still"
            + " names the deleted page: " + namesInTheSetup());

        assertEquals(model.getLayoutList().size(), 4, "the delete took more than the one page: " + model.getLayoutList());
    }

    /**
     * Delete Current Page answered No leaves the folder as it was, byte for byte.
     *
     * @throws Exception from the window
     */
    @Test
    public void testDeleteAnsweredNoLeavesTheFolderAsItWas() throws Exception
    {
        onPage("2 - Bottom");

        Map<String, String> before = fingerprint();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
        {
            SwingUtilities.invokeLater(menuItem("ui.main.toolbar.deleteCurrentPage")::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Delete Current Page asked nothing");

            Components.settle();
        }

        assertEquals(model.getLayoutList().size(), 5, "No deleted a page: " + model.getLayoutList());

        assertEquals(fingerprint(), before, "No changed the layout's folder");
    }

    /**
     * Renaming a page to another page's name is refused, naming the page in the way, and the folder is left as it was.
     *
     * @throws Exception from the window
     */
    @Test
    public void testRenameToATakenNameIsRefusedAndChangesNothing() throws Exception
    {
        final String taken = "1 - Main";

        onPage("2 - Bottom");

        Map<String, String> before = fingerprint();

        try (Answering answering = Answering.start(message -> message.contains("2 - Bottom")
            ? Answering.type(taken) : Answering.press(I18n.t("ui.ok"))))
        {
            SwingUtilities.invokeLater(menuItem("ui.main.toolbar.renameCurrentPage")::doClick);

            assertTrue(answering.awaitAsked(2, 10000), "a rename to a taken name was not refused: " + answering.asked());

            assertEquals(answering.asked().get(1), I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst", taken));

            Components.settle();
        }

        assertEquals(fingerprint(), before, "a refused rename changed the layout's folder");
    }

    /**
     * Combine Linked Pages, from a page with links, answered with a name, writes one page holding this page and every
     * page it links to, and keeps it out of autonomy, since its squares are drawings of squares on the other pages.
     *
     * @throws Exception from the window
     */
    @Test
    public void testCombineLinkedPagesWritesOnePageOutOfAutonomy() throws Exception
    {
        final String page = "1 - Main";
        final String combined = "P6 Combined";

        onPage(page);

        try (Answering answering = Answering.start(message -> Answering.type(combined)))
        {
            SwingUtilities.invokeLater(menuItem("layout.ui.menuCombineLinkedPages")::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Combine Linked Pages asked for no name");

            assertEquals(answering.asked().get(0).split("\n")[0], I18n.t("layout.ui.promptCombinedPageName"),
                "Combine Linked Pages did not ask what to call the page");

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(combined), 15000), "Combine Linked"
                + " Pages made no page called " + combined + ": " + model.getLayoutList());
        }

        assertTrue(pageFile(combined).isFile(), "the combined page has no file: " + pageFile(combined));

        assertTrue(tilesIn(pageFile(combined)).size() > tilesIn(pageFile(page)).size(), "the combined page holds no"
            + " more than the page it was combined from");

        assertTrue(Components.waitFor(() -> idInTheSetup(combined) != null
            && excludedInTheSetup().contains(idInTheSetup(combined)), 10000), "the combined page is not kept out of"
            + " autonomy: excluded " + excludedInTheSetup() + ", pages " + namesInTheSetup());
    }

    /**
     * Combine Linked Pages on a page with no links says there is nothing to combine, and changes nothing.
     *
     * @throws Exception from the window
     */
    @Test
    public void testCombineWithNoLinksSaysSoAndChangesNothing() throws Exception
    {
        onPage("5 - Test");

        Map<String, String> before = fingerprint();

        try (Answering answering = Answering.start(message -> null))
        {
            SwingUtilities.invokeLater(menuItem("layout.ui.menuCombineLinkedPages")::doClick);

            assertTrue(answering.awaitAsked(1, 10000), "Combine Linked Pages said nothing on a page with no links");

            assertEquals(answering.asked().get(0), I18n.t("layout.ui.infoNothingLinkedFromHere"));

            Components.settle();
        }

        assertEquals(model.getLayoutList().size(), 5, "a page was made from a page with no links");

        assertEquals(fingerprint(), before, "the refusal changed the layout's folder");
    }

    /** The page list: the combo box holding the layout's pages. */
    private JComboBox<?> pageList()
    {
        final String first = model.getLayoutList().get(0);

        JComboBox<?> box = Components.find(ui, JComboBox.class, b ->
        {
            for (int i = 0; i < b.getItemCount(); i++)
            {
                if (first.equals(String.valueOf(b.getItemAt(i)))) return true;
            }

            return false;
        });

        assertNotNull(box, "the window has no page list");

        return box;
    }

    /** Picks a page in the page list, as the user does. */
    private void onPage(final String page) throws Exception
    {
        Components.select(pageList(), page);

        assertTrue(Components.waitFor(() -> page.equals(String.valueOf(pageList().getSelectedItem())), 5000),
            "precondition: the page list would not show " + page);

        Components.settle();
    }

    /** The menu item whose text is this key's. */
    private AbstractButton menuItem(String key)
    {
        final AbstractButton item = Components.withText(ui, I18n.t(key));

        assertNotNull(item, "the window has no " + I18n.t(key));

        return item;
    }

    private File pageFile(String page)
    {
        return new File(sandbox.getFolder(), "config/gleisbilder/" + page + ".cs2");
    }

    /** The layout's index of pages. */
    private String index() throws Exception
    {
        return new String(Files.readAllBytes(new File(sandbox.getFolder(), "config/gleisbild.cs2").toPath()),
            StandardCharsets.UTF_8).replace("\r", "");
    }

    /** A page file's squares, each as its kind, place and turn, sorted. */
    private static List<String> tilesIn(File page) throws Exception
    {
        List<String> tiles = new ArrayList<>();

        StringBuilder tile = null;

        for (String line : Files.readAllLines(page.toPath(), StandardCharsets.UTF_8))
        {
            if (line.trim().equals("element"))
            {
                if (tile != null) tiles.add(tile.toString());

                tile = new StringBuilder();
            }
            else if (tile != null && (line.startsWith(" .typ=") || line.startsWith(" .drehung=")))
            {
                tile.append(line.trim()).append(' ');
            }
        }

        if (tile != null) tiles.add(tile.toString());

        Collections.sort(tiles);

        return tiles;
    }

    /** The autonomy setup's page names, by page id. */
    private Map<String, String> namesInTheSetup()
    {
        Map<String, String> names = new TreeMap<>();

        org.json.JSONObject pages = setup().optJSONObject("pages");

        if (pages != null) for (String id : pages.keySet()) names.put(id, pages.getString(id));

        return names;
    }

    private String idInTheSetup(String page)
    {
        for (Map.Entry<String, String> entry : namesInTheSetup().entrySet())
        {
            if (entry.getValue().equals(page)) return entry.getKey();
        }

        return null;
    }

    private List<String> excludedInTheSetup()
    {
        List<String> excluded = new ArrayList<>();

        org.json.JSONArray ids = setup().optJSONArray("excludedPages");

        if (ids != null) for (int i = 0; i < ids.length(); i++) excluded.add(String.valueOf(ids.get(i)));

        return excluded;
    }

    private org.json.JSONObject setup()
    {
        try
        {
            return new org.json.JSONObject(new String(Files.readAllBytes(
                new File(sandbox.getFolder(), "config/autonomy/setup.json").toPath()), StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            return new org.json.JSONObject();
        }
    }

    /** Every file in the layout's folder, by its path there, with a digest of its bytes. */
    private Map<String, String> fingerprint() throws Exception
    {
        Map<String, String> files = new TreeMap<>();

        Path root = sandbox.getFolder().toPath();

        List<Path> found = new ArrayList<>();

        try (java.util.stream.Stream<Path> walk = Files.walk(root))
        {
            walk.filter(Files::isRegularFile).forEach(found::add);
        }

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");

        for (Path file : found)
        {
            files.put(root.relativize(file).toString(),
                java.util.Base64.getEncoder().encodeToString(digest.digest(Files.readAllBytes(file))));
        }

        return files;
    }
}
