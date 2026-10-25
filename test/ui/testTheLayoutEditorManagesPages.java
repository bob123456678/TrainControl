package ui;

import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * FR-109: the layout editor's Manage Pages menu adds, renames, duplicates, combines and deletes pages with the main
 * window's own actions, on the page being edited, in the same window; FR-146: Layouts > Manage Pages... opens it.  The
 * user opens the editor, picks the item and answers the real dialogs; what comes of it is the layout's folder and the
 * window: the same window throughout, never closed or hidden, on the new page, the renamed one or another one, and on
 * the same page with nothing changed when the action is refused or canceled.  Unsaved work is asked about only once nothing can refuse the action, and every question
 * hangs off the editor.
 *
 * Each claim has a window of its own over a fresh sandboxed copy of the five-page test layout.
 */
public class testTheLayoutEditorManagesPages
{
    private static final String PROBE = "FR-109 probe route";

    private support.LayoutSandbox sandbox;
    private MarklinControlStation model;
    private TrainControlUI ui;

    @BeforeMethod
    public void openTheWindow() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedWithWindow();

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
            if (model != null && model.getRoute(PROBE) != null)
            {
                model.getRoute(PROBE).stopExecuting();

                model.deleteRoute(PROBE);
            }

            for (LayoutEditor editor : editors())
            {
                final LayoutEditor closing = editor;

                SwingUtilities.invokeAndWait(closing::dispose);
            }

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
     * Add Blank Page, answered with a name, writes an empty page of that name, and the same window shows it.
     *
     * MUTATION: the add arrives on the page it left (`from.arriveOn(null)` in LayoutFileActions), and this fails.
     */
    @Test
    public void testAddBlankPageShowsTheNewPageInTheSameWindow() throws Exception
    {
        final String blank = "P6 Blank";

        Watched editor = editPage("1 - Main", false);

        try (Answering answering = Answering.start(message -> Answering.type(blank)))
        {
            click(editor, "ui.main.toolbar.addBlackPage");

            assertTrue(answering.awaitAsked(1, 10000), "Add Blank Page in the editor asked for no name");

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(blank), 15000), "Add Blank Page made no"
                + " page called " + blank + ": " + model.getLayoutList());

            assertTrue(Components.waitFor(() -> editor.shows(blank), 15000), "the editor does not show the new page "
                + blank + ": " + titles());
        }

        editor.wasNeverClosed();

        assertTrue(pageFile(blank).isFile(), "the blank page has no file: " + pageFile(blank));

        assertEquals(tilesIn(pageFile(blank)), Collections.emptyList(), "the blank page has squares on it");

        assertTrue(index().contains(".name=" + blank), "the layout's index does not list the blank page");

        assertTrue(pageFile("1 - Main").isFile(), "the page the editor was on is gone");
    }

    /**
     * Rename Current Page offers the page's own name, moves its file, its index entry and its name in the autonomy
     * setup, and the same window shows the page under its new name.
     *
     * MUTATION: the rename arrives on the page it left (`from.arriveOn(null)` in LayoutFileActions), which has gone,
     * and this fails.
     */
    @Test
    public void testRenameShowsTheRenamedPageInTheSameWindow() throws Exception
    {
        final String page = "5 - Test";
        final String renamed = "5 - Renamed";

        String id = idInTheSetup(page);

        assertNotNull(id, "precondition: the setup does not know the page " + page);

        Watched editor = editPage(page, false);

        try (Answering answering = Answering.start(message -> Answering.type(renamed)))
        {
            click(editor, "ui.main.toolbar.renameCurrentPage");

            assertTrue(answering.awaitAsked(1, 10000), "Rename Current Page in the editor asked for no name");

            assertEquals(answering.asked().get(0), page, "the name offered is not the page's own");

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(renamed)
                && !model.getLayoutList().contains(page), 15000), "the page list after the rename: "
                + model.getLayoutList());

            assertTrue(Components.waitFor(() -> editor.shows(renamed), 15000), "the editor does not show the renamed"
                + " page " + renamed + ": " + titles());
        }

        editor.wasNeverClosed();

        assertTrue(pageFile(renamed).isFile(), "the renamed page has no file: " + pageFile(renamed));

        assertFalse(pageFile(page).exists(), "the page's file is still there under its old name");

        assertTrue(Components.waitFor(() -> renamed.equals(namesInTheSetup().get(id)), 10000), "the autonomy setup"
            + " does not call the page by its new name: " + namesInTheSetup());
    }

    /**
     * Delete Current Page, answered Yes to the Layouts menu's own question, removes the page's file, its index entry
     * and its name in the setup, and the same window shows another page.
     *
     * MUTATION: the delete arrives on the deleted page (`from.arriveOn(going)`), which closes the window, and this
     * fails.
     */
    @Test
    public void testDeleteShowsAnotherPageInTheSameWindow() throws Exception
    {
        final String page = "4 - Combined";

        Watched editor = editPage(page, false);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            click(editor, "ui.main.toolbar.deleteCurrentPage");

            assertTrue(answering.awaitAsked(1, 10000), "Delete Current Page in the editor asked nothing");

            assertEquals(answering.asked().get(0), I18n.f("layout.ui.confirmDeleteLayoutPageAreYouSure", page));

            assertTrue(Components.waitFor(() -> !model.getLayoutList().contains(page), 15000), "the page is still"
                + " listed after the delete: " + model.getLayoutList());

            assertTrue(Components.waitFor(() -> editor.page() != null && !page.equals(editor.page()), 15000),
                "the editor does not show another page after the delete: " + titles());
        }

        editor.wasNeverClosed();

        assertFalse(pageFile(page).exists(), "the deleted page's file is still there");

        assertFalse(index().contains(".name=" + page), "the layout's index still lists the deleted page");

        assertTrue(Components.waitFor(() -> !namesInTheSetup().containsValue(page), 10000), "the autonomy setup still"
            + " names the deleted page: " + namesInTheSetup());

        assertEquals(model.getLayoutList().size(), 4, "the delete took more than the one page: "
            + model.getLayoutList());
    }

    /**
     * From the setup editor, Rename Current Page renames, and the same window shows the renamed page in the setup
     * editor still.
     *
     * MUTATION: the arrival always in the track editor (`arriveAt(to, false)` in `arriveOn`), and this fails.
     */
    @Test
    public void testRenameInTheSetupEditorStaysInTheSetupEditor() throws Exception
    {
        final String page = "5 - Test";
        final String renamed = "5 - Renamed";

        Watched editor = editPage(page, true);

        assertTrue(editor.window.isAutonomyMode(), "precondition: the setup editor did not open: " + titles());

        try (Answering answering = Answering.start(message -> Answering.type(renamed)))
        {
            click(editor, "ui.main.toolbar.renameCurrentPage");

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(renamed)
                && !model.getLayoutList().contains(page), 15000), "the page list after the rename: "
                + model.getLayoutList());

            assertTrue(Components.waitFor(() -> editor.shows(renamed), 15000), "the editor does not show the renamed"
                + " page " + renamed + ": " + titles());
        }

        assertTrue(Components.onTheEventThread(editor.window::isAutonomyMode), "the window came back as the track"
            + " editor: " + titles());

        editor.wasNeverClosed();

        assertTrue(pageFile(renamed).isFile(), "the renamed page has no file: " + pageFile(renamed));
    }

    /**
     * Delete answered No, with a square turned and unsaved, asks nothing about the unsaved work, changes nothing in
     * the folder, and leaves the same window on the same page with the turn still there to save.
     *
     * MUTATION: the editor's item settles unsaved work before the handler runs (`settleUnsavedWork()` in
     * `pageMenuItem`), and this fails.
     */
    @Test
    public void testDeleteAnsweredNoLeavesTheWindowAsItWas() throws Exception
    {
        final String page = "2 - Bottom";

        Watched editor = editPage(page, false);

        turnASquare(editor);

        Map<String, String> before = fingerprint();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
        {
            click(editor, "ui.main.toolbar.deleteCurrentPage");

            assertTrue(answering.awaitAsked(1, 10000), "Delete Current Page in the editor asked nothing");

            Components.settle();

            assertEquals(answering.asked(), Collections.singletonList(
                I18n.f("layout.ui.confirmDeleteLayoutPageAreYouSure", page)), "a No to the delete asked more");
        }

        editor.isAsItWas(page);

        assertEquals(model.getLayoutList().size(), 5, "No deleted a page: " + model.getLayoutList());

        assertEquals(fingerprint(), before, "No changed the layout's folder");
    }

    /**
     * A rename to another page's name is refused with the Layouts menu's sentence, before anything is asked about
     * unsaved work; the folder is as it was, and the same window is on the same page with the edit still unsaved.
     *
     * MUTATION: the editor that asked refused as an open editor (`from == null &&` dropped in LayoutFileActions), and
     * this fails.
     */
    @Test
    public void testARenameToATakenNameIsRefusedAndLeavesTheWindowAsItWas() throws Exception
    {
        final String taken = "1 - Main";
        final String page = "2 - Bottom";

        Watched editor = editPage(page, false);

        turnASquare(editor);

        Map<String, String> before = fingerprint();

        try (Answering answering = Answering.start(message -> message.equals(page)
            ? Answering.type(taken) : Answering.pressAt(0)))
        {
            click(editor, "ui.main.toolbar.renameCurrentPage");

            assertTrue(answering.awaitAsked(2, 10000), "a rename to a taken name was not refused: "
                + answering.asked());

            Components.settle();

            assertEquals(answering.asked(), java.util.Arrays.asList(page,
                I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst", taken)), "what the refused rename asked");
        }

        editor.isAsItWas(page);

        assertEquals(fingerprint(), before, "a refused rename changed the layout's folder");
    }

    /**
     * Cancel at the name, with a square turned and unsaved, asks nothing about the unsaved work, and leaves the same
     * window open on the same page with the turn still there to save.
     *
     * MUTATION: the editor's item settles unsaved work before the handler asks for the name (`settleUnsavedWork()` in
     * `pageMenuItem`), and this fails.
     */
    @Test
    public void testACancelAtTheNameLeavesTheSameWindowOnTheSamePage() throws Exception
    {
        final String page = "3 - Top Parking";

        Watched editor = editPage(page, false);

        turnASquare(editor);

        Map<String, String> before = fingerprint();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            click(editor, "ui.main.toolbar.renameCurrentPage");

            assertTrue(answering.awaitAsked(1, 10000), "Rename Current Page in the editor asked for no name");

            Components.settle();

            assertEquals(answering.asked(), Collections.singletonList(page), "a Cancel at the name asked more");
        }

        editor.isAsItWas(page);

        assertEquals(model.getLayoutList().size(), 5, "a Cancel changed the pages: " + model.getLayoutList());

        assertEquals(fingerprint(), before, "a Cancel changed the layout's folder");
    }

    /**
     * While a route runs, each of the three items is refused in one dialog, the run refusal, over the editor: no
     * name is asked for and nothing about unsaved work; the pages and the folder are as they were and the same window
     * is on the same page.
     *
     * MUTATION: the editor's own run refusal dropped (`pageMenuItem`), so Add and Rename ask for a name first, and
     * this fails.
     */
    @Test
    public void testTheItemsRefuseWhileARouteRunsInOneDialog() throws Exception
    {
        final String page = "2 - Bottom";

        Watched editor = editPage(page, false);

        turnASquare(editor);

        Map<String, String> before = fingerprint();

        aRouteRuns();

        String refusal = I18n.t("ui.main.errorNoEditsWhileRunning");

        try (Owners owners = new Owners())
        {
            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                int asked = 0;

                for (String item : new String[] {"ui.main.toolbar.addBlackPage", "ui.main.toolbar.renameCurrentPage",
                    "ui.main.toolbar.deleteCurrentPage"})
                {
                    click(editor, item);

                    asked++;

                    assertTrue(answering.awaitAsked(asked, 10000), I18n.t(item) + " asked nothing while a route runs");

                    Components.settle();

                    assertEquals(answering.asked().size(), asked, I18n.t(item) + " asked more than one question while a"
                        + " route runs: " + answering.asked());

                    assertEquals(answering.asked().get(asked - 1), refusal, I18n.t(item) + " was not refused while a"
                        + " route runs");
                }
            }

            owners.allAre(editor.window);
        }

        editor.isAsItWas(page);

        assertEquals(model.getLayoutList().size(), 5, "a page action changed the pages while a route runs: "
            + model.getLayoutList());

        assertEquals(fingerprint(), before, "a page action changed the layout's folder while a route runs");
    }

    /**
     * With the main window showing another page, Rename and then Delete in the editor act on the page being edited:
     * the name offered and the page renamed are the editor's, and so are the page asked about and deleted; the page
     * the main window shows is untouched.
     *
     * MUTATION: the editor does not select its page in the main window before the action (`pageMenuItem`), and this
     * fails.
     */
    @Test
    public void testTheItemsActOnTheEditedPageWhenTheMainWindowShowsAnother() throws Exception
    {
        final String page = "5 - Test";
        final String renamed = "5 - Renamed";
        final String elsewhere = "2 - Bottom";

        Watched editor = editPage(page, false);

        showInTheMainWindow(elsewhere);

        try (Answering answering = Answering.start(message -> Answering.type(renamed)))
        {
            click(editor, "ui.main.toolbar.renameCurrentPage");

            assertTrue(answering.awaitAsked(1, 10000), "Rename Current Page in the editor asked for no name");

            assertEquals(answering.asked().get(0), page, "the rename offered the main window's page, not the edited"
                + " one");

            assertTrue(Components.waitFor(() -> editor.shows(renamed), 15000), "the editor does not show the renamed"
                + " page " + renamed + ": " + titles());
        }

        assertTrue(model.getLayoutList().contains(elsewhere), "the main window's page was renamed: "
            + model.getLayoutList());

        assertFalse(model.getLayoutList().contains(page), "the edited page was not renamed: " + model.getLayoutList());

        showInTheMainWindow(elsewhere);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.yes"))))
        {
            click(editor, "ui.main.toolbar.deleteCurrentPage");

            assertTrue(answering.awaitAsked(1, 10000), "Delete Current Page in the editor asked nothing");

            assertEquals(answering.asked().get(0), I18n.f("layout.ui.confirmDeleteLayoutPageAreYouSure", renamed),
                "the delete asked about another page than the edited one");

            assertTrue(Components.waitFor(() -> !model.getLayoutList().contains(renamed), 15000), "the edited page is"
                + " still listed after the delete: " + model.getLayoutList());

            assertTrue(Components.waitFor(() -> editor.page() != null && !renamed.equals(editor.page()), 15000),
                "the editor does not show another page after the delete: " + titles());
        }

        assertTrue(model.getLayoutList().contains(elsewhere), "the main window's page was deleted: "
            + model.getLayoutList());

        assertTrue(pageFile(elsewhere).isFile(), "the main window's page has lost its file");

        editor.wasNeverClosed();
    }

    /**
     * A square turned and unsaved, then Rename: the name is asked for first, then the unsaved work, and Save writes the
     * turn into the page that the rename then moves, so the renamed page's file has it.
     *
     * MUTATION: Save's answer not written (`saveBeforeLeaving` returns true without saving), and this fails.
     */
    @Test
    public void testSaveThenRenameKeepsTheEditUnderTheNewName() throws Exception
    {
        final String page = "5 - Test";
        final String renamed = "5 - Renamed";

        Watched editor = editPage(page, false);

        int[] turned = turnASquare(editor);

        final String unsaved = I18n.t("layout.ui.confirmSwitchWithUnsavedWork");

        try (Answering answering = Answering.start(message -> unsaved.equals(message)
            ? Answering.press(I18n.t("layout.ui.switchSave")) : Answering.type(renamed)))
        {
            click(editor, "ui.main.toolbar.renameCurrentPage");

            assertTrue(answering.awaitAsked(2, 10000), "the rename did not ask for a name and about the unsaved"
                + " work: " + answering.asked());

            assertEquals(answering.asked().subList(0, 2), java.util.Arrays.asList(page, unsaved), "the name and the"
                + " unsaved work were not asked in that order");

            assertTrue(Components.waitFor(() -> editor.shows(renamed), 15000), "the editor does not show the renamed"
                + " page " + renamed + ": " + titles());
        }

        editor.wasNeverClosed();

        assertFalse(pageFile(page).exists(), "the page's file is still there under its old name");

        assertEquals(model.getLayout(renamed).getComponent(turned[0], turned[1]).getOrientation(), turned[2], "the"
            + " saved turn is not in the renamed page as read back from its file");

        assertFalse(Components.onTheEventThread(editor.window::canUndo), "the editor still has unsaved work after"
            + " Save");
    }

    /**
     * Every question the items ask hangs off the editor: the name, a refusal, the delete question and the unsaved-work
     * question (answered Cancel, which leaves the window as it was).
     *
     * MUTATION: the name asked over the main window (`promptUserForLayout(this, ...)`), and this fails.
     */
    @Test
    public void testEveryQuestionHangsOffTheEditor() throws Exception
    {
        final String page = "2 - Bottom";
        final String taken = "1 - Main";

        Watched editor = editPage(page, false);

        try (Owners owners = new Owners())
        {
            try (Answering answering = Answering.start(message -> message.equals(page)
                ? Answering.type(taken) : Answering.pressAt(0)))
            {
                click(editor, "ui.main.toolbar.renameCurrentPage");

                assertTrue(answering.awaitAsked(2, 10000), "the rename to a taken name asked: " + answering.asked());
            }

            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.no"))))
            {
                click(editor, "ui.main.toolbar.deleteCurrentPage");

                assertTrue(answering.awaitAsked(1, 10000), "Delete Current Page in the editor asked nothing");
            }

            turnASquare(editor);

            final String unsaved = I18n.t("layout.ui.confirmSwitchWithUnsavedWork");

            try (Answering answering = Answering.start(message -> unsaved.equals(message)
                ? Answering.press(I18n.t("layout.ui.switchCancel")) : Answering.type("P6 Blank")))
            {
                click(editor, "ui.main.toolbar.addBlackPage");

                assertTrue(answering.awaitAsked(2, 10000), "the add did not ask for a name and about the unsaved work: "
                    + answering.asked());

                Components.settle();
            }

            assertEquals(owners.count(), 5, "not every question was seen");

            owners.allAre(editor.window);
        }

        editor.isAsItWas(page);

        assertFalse(model.getLayoutList().contains("P6 Blank"), "a Cancel at the unsaved work added the page");
    }

    /**
     * Duplicate Current Page, with a square turned and unsaved, asks only about the unsaved work; answered Save, it
     * writes a copy of the saved page under the Layouts menu's name for it, and the same window shows the copy.
     *
     * MUTATION: the duplicate hands the layout actions no editor (`pageActionFrom` as null in
     * `duplicateOrRenameCurrentLayout`), so the editor is refused as an open one, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testDuplicateShowsTheCopyInTheSameWindow() throws Exception
    {
        final String page = "3 - Top Parking";
        final String copy = page + " copy";

        Watched editor = editPage(page, false);

        int[] turned = turnASquare(editor);

        final String unsaved = I18n.t("layout.ui.confirmSwitchWithUnsavedWork");

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("layout.ui.switchSave"))))
        {
            click(editor, "ui.main.toolbar.duplicateCurrentPage");

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(copy), 15000), "Duplicate Current Page"
                + " in the editor made no page called " + copy + ": " + model.getLayoutList() + ", asked "
                + answering.asked());

            assertTrue(Components.waitFor(() -> editor.shows(copy), 15000), "the editor does not show the copy "
                + copy + ": " + titles());

            assertEquals(answering.asked(), Collections.singletonList(unsaved), "Duplicate Current Page asked more"
                + " than about the unsaved work");
        }

        editor.wasNeverClosed();

        assertTrue(pageFile(copy).isFile(), "the copy has no file of its own: " + pageFile(copy));

        assertEquals(tilesIn(pageFile(copy)), tilesIn(pageFile(page)), "the copy's squares are not the page's");

        assertEquals(model.getLayout(copy).getComponent(turned[0], turned[1]).getOrientation(), turned[2], "the"
            + " saved turn is not in the copy");

        assertTrue(index().contains(".name=" + copy), "the layout's index does not list the copy");
    }

    /**
     * Combine Linked Pages, from a page with links, answered with a name, writes the combined page, kept out of
     * autonomy, and the same window shows the page the main window ends on, the combined one.
     *
     * MUTATION: the combine arrives on the page it left (`editor.arriveOn(null)` in `combineOnAWorker`), and this
     * fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testCombineShowsTheCombinedPageInTheSameWindow() throws Exception
    {
        final String page = "1 - Main";
        final String combined = "P6 Combined";

        Watched editor = editPage(page, false);

        try (Answering answering = Answering.start(message -> Answering.type(combined)))
        {
            click(editor, "layout.ui.menuCombineLinkedPages");

            assertTrue(answering.awaitAsked(1, 10000), "Combine Linked Pages in the editor asked for no name");

            assertEquals(answering.asked().get(0).split("\n")[0], I18n.t("layout.ui.promptCombinedPageName"),
                "Combine Linked Pages did not ask what to call the page");

            assertTrue(Components.waitFor(() -> model.getLayoutList().contains(combined), 15000), "Combine Linked"
                + " Pages made no page called " + combined + ": " + model.getLayoutList());

            assertTrue(Components.waitFor(() -> editor.shows(combined), 15000), "the editor does not show the"
                + " combined page " + combined + ": " + titles());
        }

        editor.wasNeverClosed();

        assertEquals(String.valueOf(Components.onTheEventThread(() -> pageList().getSelectedItem())), combined,
            "the main window does not end on the combined page");

        assertTrue(pageFile(combined).isFile(), "the combined page has no file: " + pageFile(combined));

        assertTrue(tilesIn(pageFile(combined)).size() > tilesIn(pageFile(page)).size(), "the combined page holds no"
            + " more than the page it was combined from");

        assertTrue(Components.waitFor(() -> idInTheSetup(combined) != null && setup().optJSONArray("excludedPages")
            != null && setup().getJSONArray("excludedPages").toList().stream().map(String::valueOf)
            .anyMatch(idInTheSetup(combined)::equals), 10000), "the combined page is not kept out of autonomy: "
            + setup());
    }

    /**
     * While a route runs, Duplicate and Combine are each refused in one dialog, the run refusal, over the editor:
     * nothing about unsaved work or a name is asked; the pages and the folder are as they were and the same window is
     * on the same page.
     *
     * MUTATION: the editor's own run refusal dropped (`pageMenuItem`) and Combine's asked over the main window
     * (`refuseWhileARunHoldsTheSetup(this.ui)` in `combineLinkedPages`), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testDuplicateAndCombineRefuseWhileARouteRunsInOneDialog() throws Exception
    {
        final String page = "1 - Main";

        Watched editor = editPage(page, false);

        turnASquare(editor);

        Map<String, String> before = fingerprint();

        aRouteRuns();

        String refusal = I18n.t("ui.main.errorNoEditsWhileRunning");

        try (Owners owners = new Owners())
        {
            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                int asked = 0;

                for (String item : new String[] {"ui.main.toolbar.duplicateCurrentPage",
                    "layout.ui.menuCombineLinkedPages"})
                {
                    click(editor, item);

                    asked++;

                    assertTrue(answering.awaitAsked(asked, 10000), I18n.t(item) + " asked nothing while a route runs");

                    Components.settle();

                    assertEquals(answering.asked().size(), asked, I18n.t(item) + " asked more than one question while"
                        + " a route runs: " + answering.asked());

                    assertEquals(answering.asked().get(asked - 1), refusal, I18n.t(item) + " was not refused while a"
                        + " route runs");
                }
            }

            owners.allAre(editor.window);
        }

        editor.isAsItWas(page);

        assertEquals(model.getLayoutList().size(), 5, "a page action changed the pages while a route runs: "
            + model.getLayoutList());

        assertEquals(fingerprint(), before, "a page action changed the layout's folder while a route runs");
    }

    /**
     * Combine's name and its refusal of a taken name, and Duplicate's unsaved-work question (answered Cancel), all hang
     * off the editor, and leave the window as it was.
     *
     * MUTATION: Combine's name asked over the main window (`showInputDialog(this.ui, ...)`), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testCombineAndDuplicateAskOverTheEditor() throws Exception
    {
        final String page = "1 - Main";
        final String taken = "2 - Bottom";

        Watched editor = editPage(page, false);

        final String prompt = I18n.t("layout.ui.promptCombinedPageName");

        try (Owners owners = new Owners())
        {
            try (Answering answering = Answering.start(message -> message.startsWith(prompt)
                ? Answering.type(taken) : Answering.pressAt(0)))
            {
                click(editor, "layout.ui.menuCombineLinkedPages");

                assertTrue(answering.awaitAsked(2, 10000), "a combine to a taken name asked: " + answering.asked());

                Components.settle();

                assertEquals(answering.asked().get(1), I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst",
                    taken), "a combine to a taken name was not refused");
            }

            turnASquare(editor);

            final String unsaved = I18n.t("layout.ui.confirmSwitchWithUnsavedWork");

            try (Answering answering = Answering.start(message -> Answering.press(I18n.t("layout.ui.switchCancel"))))
            {
                click(editor, "ui.main.toolbar.duplicateCurrentPage");

                assertTrue(answering.awaitAsked(1, 10000), "Duplicate with unsaved work asked nothing");

                Components.settle();

                assertEquals(answering.asked(), Collections.singletonList(unsaved), "what Duplicate with unsaved work"
                    + " asked");
            }

            assertEquals(owners.count(), 3, "not every question was seen");

            owners.allAre(editor.window);
        }

        editor.isAsItWas(page);

        assertEquals(model.getLayoutList().size(), 5, "a refused or canceled action changed the pages: "
            + model.getLayoutList());
    }

    /**
     * Combine on a page with no links says so over the editor, and leaves the same window on the same page.
     *
     * MUTATION: the "nothing linked" message shown over the main window (`showMessageDialog(this.ui, ...)` in
     * `combineLinkedPages`), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testCombineWithNoLinksSaysSoOverTheEditor() throws Exception
    {
        final String page = "5 - Test";

        Watched editor = editPage(page, false);

        Map<String, String> before = fingerprint();

        try (Owners owners = new Owners())
        {
            try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
            {
                click(editor, "layout.ui.menuCombineLinkedPages");

                assertTrue(answering.awaitAsked(1, 10000), "Combine on a page with no links said nothing");

                Components.settle();

                assertEquals(answering.asked(),
                    Collections.singletonList(I18n.t("layout.ui.infoNothingLinkedFromHere")),
                    "what Combine on a page with no links asked");
            }

            owners.allAre(editor.window);
        }

        editor.wasNeverClosed();

        assertTrue(editor.shows(page), "the editor left its page: " + titles());

        assertEquals(fingerprint(), before, "Combine with nothing linked changed the layout's folder");
    }

    /**
     * Layouts > Manage Pages... stands where the Manage Pages menu was, which is hidden, and opens the layout editor
     * on the main window's page with the editor's own Manage Pages menu open, its five items showing.
     *
     * MUTATION: the editor's menu left shut (`openPageMenu` does nothing), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testManagePagesOpensTheEditorWithItsMenuOpen() throws Exception
    {
        final String page = "2 - Bottom";

        showInTheMainWindow(page);

        final javax.swing.JMenuItem item = managePages();

        javax.swing.JMenu hidden = field("modifyLocalLayoutMenu", javax.swing.JMenu.class);

        final javax.swing.JMenu layouts = field("layoutMenu", javax.swing.JMenu.class);

        openTheLayoutsMenu();

        assertFalse(hidden.isVisible(), "the Layouts menu still shows the Manage Pages menu");

        List<java.awt.Component> shown = Components.onTheEventThread(() ->
        {
            List<java.awt.Component> visible = new ArrayList<>();

            for (java.awt.Component c : layouts.getMenuComponents()) if (c.isVisible()) visible.add(c);

            return visible;
        });

        int at = shown.indexOf(item);

        assertTrue(at >= 0 && at + 1 < shown.size() && shown.get(at + 1) instanceof javax.swing.JMenu
            && I18n.t("layout.ui.menuEditLayoutPage").equals(((javax.swing.JMenu) shown.get(at + 1)).getText()),
            "Manage Pages... is not where the Manage Pages menu was, above Edit Layout Page");

        assertTrue(item.isEnabled(), "precondition: Manage Pages... is grayed with a page loaded");

        try (Answering answering = Answering.start(message -> null))
        {
            Components.press(item);

            assertTrue(Components.waitFor(() -> editorOn(page) != null, 15000), "Manage Pages... opened no editor on "
                + page + ": " + titles());

            assertTrue(Components.waitFor(() -> pageMenuOpenIn(editorOn(page)), 10000), "the editor's Manage Pages"
                + " menu is not open");

            assertEquals(answering.asked(), Collections.emptyList(), "Manage Pages... asked something");
        }

        assertEquals(editors().size(), 1, "more than one editor is open: " + titles());

        javax.swing.JPopupMenu popup = Components.popupShowing();

        assertNotNull(popup, "no menu is showing");

        for (String key : new String[] {"ui.main.toolbar.addBlackPage", "ui.main.toolbar.renameCurrentPage",
            "ui.main.toolbar.duplicateCurrentPage", "layout.ui.menuCombineLinkedPages",
            "ui.main.toolbar.deleteCurrentPage"})
        {
            assertNotNull(Components.withText(popup, I18n.t(key)), "the open menu has no " + I18n.t(key));
        }

        SwingUtilities.invokeAndWait(javax.swing.MenuSelectionManager.defaultManager()::clearSelectedPath);
    }

    /**
     * With an editor already open on one page and the main window on another, Manage Pages... is lit, brings that
     * editor forward with its Manage Pages menu open, and opens no second editor nor moves it off its page.
     *
     * MUTATION: Manage Pages... grayed with an editor open (`setEnabled(live)` in `applyLayoutEditingAvailability`),
     * and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testManagePagesBringsTheOpenEditorForwardWithItsMenuOpen() throws Exception
    {
        final String page = "5 - Test";

        Watched editor = editPage(page, false);

        showInTheMainWindow("2 - Bottom");

        javax.swing.JMenuItem item = managePages();

        openTheLayoutsMenu();

        assertTrue(item.isEnabled(), "Manage Pages... is grayed with an editor open");

        try (Answering answering = Answering.start(message -> null))
        {
            Components.press(item);

            assertTrue(Components.waitFor(() -> pageMenuOpenIn(editor.window), 10000), "the open editor's Manage"
                + " Pages menu is not open");

            assertEquals(answering.asked(), Collections.emptyList(), "Manage Pages... asked something");
        }

        SwingUtilities.invokeAndWait(javax.swing.MenuSelectionManager.defaultManager()::clearSelectedPath);

        editor.wasNeverClosed();

        assertTrue(editor.shows(page), "the editor left its page: " + titles());
    }

    /**
     * Manage Pages... is grayed when the hidden menu is: during a run, saying why, and given back with its own tooltip
     * after; and with no local layout.  Lit again at rest.
     *
     * MUTATION: Manage Pages... left out of the run's graying (`greyForTheRun(managePagesItem, ...)` in
     * `guardLayoutMenu`), and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testManagePagesIsGrayedWhenTheHiddenMenuWouldBe() throws Exception
    {
        javax.swing.JMenuItem item = managePages();

        javax.swing.JMenu hidden = field("modifyLocalLayoutMenu", javax.swing.JMenu.class);

        openTheLayoutsMenu();

        assertTrue(item.isEnabled() && hidden.isEnabled(), "precondition: grayed at rest");

        final String tip = item.getToolTipText();

        aRouteRuns();

        openTheLayoutsMenu();

        assertFalse(hidden.isEnabled(), "precondition: the hidden menu is lit while a route runs");

        assertFalse(item.isEnabled(), "Manage Pages... is lit while a route runs");

        assertTrue(String.valueOf(item.getToolTipText()).contains(I18n.t("ui.main.errorNoEditsWhileRunning")),
            "Manage Pages... grayed for the run does not say why: " + item.getToolTipText());

        model.getRoute(PROBE).stopExecuting();

        assertTrue(Components.waitFor(() -> !ui.aRunHoldsTheSetup(), 10000), "precondition: the route still holds");

        openTheLayoutsMenu();

        assertTrue(item.isEnabled(), "Manage Pages... stayed grayed after the route stopped");

        assertEquals(item.getToolTipText(), tip, "Manage Pages... did not get its own tooltip back");

        java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        final String folder = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        try
        {
            // A Central Station layout, as switching to one stores it
            prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            openTheLayoutsMenu();

            assertFalse(hidden.isEnabled(), "precondition: the hidden menu is lit with no local layout");

            assertFalse(item.isEnabled(), "Manage Pages... is lit with no local layout");
        }
        finally
        {
            prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);
        }

        openTheLayoutsMenu();

        assertTrue(item.isEnabled(), "Manage Pages... stayed grayed with the local layout back");
    }

    // ------------------------------------------------------------------------------------------------ the window

    /** An editor window, with what has been done to the window itself. */
    private final class Watched
    {
        final LayoutEditor window;
        final AtomicInteger closedOrHidden = new AtomicInteger();

        Watched(LayoutEditor window)
        {
            this.window = window;

            window.addWindowListener(new java.awt.event.WindowAdapter()
            {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e)
                {
                    closedOrHidden.incrementAndGet();
                }

                @Override
                public void windowClosed(java.awt.event.WindowEvent e)
                {
                    closedOrHidden.incrementAndGet();
                }
            });

            window.addComponentListener(new java.awt.event.ComponentAdapter()
            {
                @Override
                public void componentHidden(java.awt.event.ComponentEvent e)
                {
                    closedOrHidden.incrementAndGet();
                }
            });
        }

        /** The page this window shows, from its title, or null when it is not showing. */
        String page()
        {
            for (String page : model.getLayoutList())
            {
                if (shows(page)) return page;
            }

            return null;
        }

        boolean shows(String page)
        {
            return window.isShowing() && (window.getTitle().startsWith(I18n.f("app.ui.windowLayoutEditorTitle", page))
                || window.getTitle().startsWith(I18n.f("autosetup.ui.windowTitle", page)));
        }

        /** The window was never closed, hidden or replaced. */
        void wasNeverClosed()
        {
            assertEquals(closedOrHidden.get(), 0, "the editor window was closed or hidden: " + titles());

            assertTrue(window.isShowing(), "the editor window is gone: " + titles());

            assertEquals(editors().size(), 1, "more than one editor is open: " + titles());
        }

        /** The same window, on the same page, with its unsaved turn still there. */
        void isAsItWas(String page) throws Exception
        {
            wasNeverClosed();

            assertTrue(shows(page), "the editor is not on " + page + ": " + titles());

            assertTrue(Components.onTheEventThread(window::canUndo), "the editor's unsaved work is gone");
        }
    }

    /** The windows the questions hang off, one per question opened while it watches. */
    private static final class Owners implements AutoCloseable
    {
        private final List<Window> owners = Collections.synchronizedList(new ArrayList<>());

        private final java.awt.event.AWTEventListener opened = event ->
        {
            if (event.getID() == java.awt.event.WindowEvent.WINDOW_OPENED && event.getSource() instanceof JDialog
                && Components.find((JDialog) event.getSource(), JOptionPane.class, null) != null)
            {
                owners.add(((JDialog) event.getSource()).getOwner());
            }
        };

        Owners()
        {
            java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(opened, java.awt.AWTEvent.WINDOW_EVENT_MASK);
        }

        @Override
        public void close()
        {
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(opened);
        }

        int count()
        {
            return owners.size();
        }

        void allAre(Window expected)
        {
            synchronized (owners)
            {
                for (Window owner : owners)
                {
                    assertTrue(owner == expected, "a question hangs off " + (owner == null ? null
                        : owner.getName() + " " + owner.getClass().getSimpleName()) + ", not the editor");
                }
            }
        }
    }

    /** Opens the editor on this page in the mode wanted, without remembering the mode, and waits for it. */
    private Watched editPage(final String page, final boolean autonomy) throws Exception
    {
        showInTheMainWindow(page);

        SwingUtilities.invokeAndWait(() -> ui.openLayoutEditor(page, autonomy, null));

        assertTrue(Components.waitFor(() -> editorOn(page) != null, 15000), "the editor did not open on " + page + ": "
            + titles());

        Components.settle();

        return new Watched(editorOn(page));
    }

    private void showInTheMainWindow(final String page)
    {
        javax.swing.JComboBox<?> box = pageList();

        Components.select(box, page);

        assertTrue(Components.waitFor(() -> page.equals(String.valueOf(box.getSelectedItem())), 5000),
            "precondition: the page list would not show " + page);

        Components.settle();
    }

    /** The main window's page list. */
    private javax.swing.JComboBox<?> pageList()
    {
        final String first = model.getLayoutList().get(0);

        javax.swing.JComboBox<?> box = Components.find(ui, javax.swing.JComboBox.class, b ->
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

    /** Layouts > Manage Pages... */
    private javax.swing.JMenuItem managePages()
    {
        AbstractButton item = Components.withText(ui, I18n.t("ui.main.toolbar.managePages"));

        assertTrue(item instanceof javax.swing.JMenuItem, "the Layouts menu has no "
            + I18n.t("ui.main.toolbar.managePages"));

        return (javax.swing.JMenuItem) item;
    }

    /** Opens and shuts the Layouts menu, so its guard runs as it does for the user. */
    private void openTheLayoutsMenu() throws Exception
    {
        final javax.swing.JMenu layouts = field("layoutMenu", javax.swing.JMenu.class);

        SwingUtilities.invokeAndWait(() ->
        {
            layouts.setSelected(true);
            layouts.setSelected(false);
        });

        Components.settle();
    }

    /** Whether this editor's Manage Pages menu is open. */
    private static boolean pageMenuOpenIn(LayoutEditor editor)
    {
        if (editor == null) return false;

        javax.swing.JMenu menu = Components.find(editor, javax.swing.JMenu.class,
            m -> I18n.t("ui.main.toolbar.modifyLayout").equals(m.getText()));

        return menu != null && Components.onTheEventThread(menu::isPopupMenuVisible);
    }

    private <T> T field(String name, Class<T> type) throws Exception
    {
        java.lang.reflect.Field field = TrainControlUI.class.getDeclaredField(name);

        field.setAccessible(true);

        return type.cast(field.get(ui));
    }

    /** Presses the editor's own item for this action. */
    private static void click(Watched editor, String key)
    {
        AbstractButton item = Components.withText(editor.window, I18n.t(key));

        assertNotNull(item, "the layout editor has no " + I18n.t(key));

        Components.press(item);
    }

    /** Turns a track square, so the editor has unsaved work; returns its x, y and new orientation. */
    private static int[] turnASquare(Watched editor) throws Exception
    {
        LayoutLabel square = Components.find(editor.window, LayoutLabel.class,
            l -> l.getComponent() != null && !l.getComponent().isText());

        assertNotNull(square, "precondition: the page has no track square to turn");

        final org.traincontrol.base.LayoutDiagramComponent tile = square.getComponent();

        final int was = tile.getOrientation();

        SwingUtilities.invokeAndWait(() -> editor.window.rotate(square));

        assertTrue(Components.onTheEventThread(editor.window::canUndo), "precondition: the turn left nothing unsaved");

        final int now = Components.onTheEventThread(() -> square.getComponent().getOrientation());

        assertTrue(now != was, "precondition: the turn did not change the square's orientation");

        return new int[] {tile.getX(), tile.getY(), now};
    }

    /** Marks a probe route running, as `MarklinRoute.execRoute` does. */
    private void aRouteRuns()
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));

        assertTrue(model.newRoute(PROBE, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
            "precondition: the probe route could not be made");

        assertTrue(model.getRoute(PROBE).setExecuting(), "precondition: the probe route would not run");
    }

    /** Every editor window that is still there. */
    private static List<LayoutEditor> editors()
    {
        List<LayoutEditor> found = new ArrayList<>();

        for (Frame frame : Frame.getFrames())
        {
            if (frame instanceof LayoutEditor && frame.isDisplayable()) found.add((LayoutEditor) frame);
        }

        return found;
    }

    private static List<String> titles()
    {
        List<String> titles = new ArrayList<>();

        for (LayoutEditor editor : editors()) titles.add(editor.getTitle());

        return titles;
    }

    /** The editor that is showing this page, or null. */
    private static LayoutEditor editorOn(String page)
    {
        for (LayoutEditor editor : editors())
        {
            if (editor.isShowing() && (editor.getTitle().startsWith(I18n.f("app.ui.windowLayoutEditorTitle", page))
                || editor.getTitle().startsWith(I18n.f("autosetup.ui.windowTitle", page)))) return editor;
        }

        return null;
    }

    private File pageFile(String page)
    {
        return new File(sandbox.getFolder(), "config/gleisbilder/" + page + ".cs2");
    }

    private String index() throws Exception
    {
        return new String(Files.readAllBytes(new File(sandbox.getFolder(), "config/gleisbild.cs2").toPath()),
            StandardCharsets.UTF_8).replace("\r", "");
    }

    /** A page file's squares, each as its kind and turn, sorted. */
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
