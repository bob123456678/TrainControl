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
 * FR-109: the layout editor's Manage Pages menu adds, renames and deletes pages with the Layouts menu's own actions,
 * on the page being edited, in the same window.  The user opens the editor, picks the item and answers the real
 * dialogs; what comes of it is the layout's folder and the window: the same window throughout, never closed or
 * hidden, on the new page, the renamed one or another one, and on the same page with nothing changed when the action
 * is refused or canceled.  Unsaved work is asked about only once nothing can refuse the action, and every question
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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
     *
     * @throws Exception from the window
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

        Components.select(box, page);

        assertTrue(Components.waitFor(() -> page.equals(String.valueOf(box.getSelectedItem())), 5000),
            "precondition: the page list would not show " + page);

        Components.settle();
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
