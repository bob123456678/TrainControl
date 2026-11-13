package ui;

import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JTextField;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import support.Reflect;

/**
 * Find a Point... on the autonomy editor's Tools menu, and Control+F (FR-055), and a finding that names a point on
 * another page.  Every claim opens the editor as the main window does, presses the real key or menu item, answers the
 * real prompt, and reads what the user would see: the page that opened, the square outlined and flashed.
 *
 * @author Adam
 */
public class testFindAPoint
{
    private static final support.SandboxedWindow held = new support.SandboxedWindow();
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;

    private static final String PAGE = "1 - Main";
    private static final String OTHER_PAGE = "2 - Bottom";

    private static final String HERE = "Qx Here Siding";
    private static final String THERE = "Qx There Siding";
    private static final String TWIN = "Qx Twin";

    private static TileKey here;
    private static TileKey there;
    private static TileKey twinHere;
    private static TileKey twinThere;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the autonomy editor needs a display");

        held.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot")));

        model = support.TestStation.simulatedInDebug();

        ui = held.openWindow(model);

        session = ui.getAutonomySession();

        assertNotNull(session, "no setup, so there is no editor to open");

        List<TileKey> onMain = new ArrayList<>();
        List<TileKey> onOther = new ArrayList<>();

        for (TileKey tile : session.getReducer().getPoints().keySet())
        {
            if (PAGE.equals(tile.getPage())) onMain.add(tile);
            else if (OTHER_PAGE.equals(tile.getPage())) onOther.add(tile);
        }

        assertTrue(onMain.size() >= 2 && onOther.size() >= 2, "precondition: the layout has fewer than two points on a"
            + " page: " + onMain.size() + " and " + onOther.size());

        here = onMain.get(0);
        twinHere = onMain.get(1);
        there = onOther.get(0);
        twinThere = onOther.get(1);

        session.getStore().setPointName(here, HERE);
        session.getStore().setPointName(twinHere, TWIN);
        session.getStore().setPointName(there, THERE);
        session.getStore().setPointName(twinThere, TWIN);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            closeThem();

            if (model != null) model.stop();
        }
        finally
        {
            held.close();
        }
    }

    // ---- claims

    /**
     * Control+F opens a prompt over the editor listing every named point of every page, each with its page; two points
     * with one name are both there; typing narrows the list with capitals ignored; nothing matching says so.
     *
     * MUTATION: in `FindAPointPrompt.narrow`, compare `entry.name.contains(wanted)` without `toLowerCase`; this fails.
     */
    @Test
    public void testControlFListsEveryNamedPointAndTypingNarrowsIt() throws Exception
    {
        LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            JDialog prompt = pressControlF(editor);

            assertSame(prompt.getOwner(), editor, "the prompt does not hang off the editor window");

            List<String> rows = rows(prompt);

            assertTrue(rows.contains(rowOf(HERE, here)), "the point on this page is not listed: " + rows);
            assertTrue(rows.contains(rowOf(THERE, there)), "the point on the other page is not listed: " + rows);
            assertTrue(rows.contains(rowOf(TWIN, twinHere)) && rows.contains(rowOf(TWIN, twinThere)),
                "the two points called alike are not both listed, each with its page: " + rows);

            assertEquals(rows.size(), session.getStore().getNamedTiles().size(), "an unnamed point is listed, or a named"
                + " one is missing: " + rows);

            type(prompt, "qX tHeRe");

            assertEquals(rows(prompt), java.util.Arrays.asList(rowOf(THERE, there)), "the list typed to \"qX tHeRe\"");

            type(prompt, "zzqqxx");

            assertTrue(rows(prompt).isEmpty(), "rows are left for a name nothing matches");
            assertEquals(statusOf(prompt), I18n.f("layout.ui.findPointNoMatch", "zzqqxx"), "the empty state");

            // Enter with nothing to choose does nothing at all
            support.Keys.press(boxOf(prompt), KeyEvent.VK_ENTER);

            assertTrue(prompt.isShowing(), "Enter with nothing chosen closed the prompt");

            // Escape cancels
            support.Keys.press(boxOf(prompt), KeyEvent.VK_ESCAPE);

            assertFalse(prompt.isShowing(), "Escape did not close the prompt");
            assertTrue(editor.isShowing(), "Escape closed the editor too");
        }
        finally
        {
            closeThem();
        }
    }

    /**
     * The prompt reads the points when it is used, not as it opened: a point renamed while it is open is found by its
     * new name and no longer by its old one, and choosing the old row goes nowhere.
     *
     * MUTATION: have `FindAPointPrompt` keep the points it opened with, and this fails.
     */
    @Test
    public void testThePromptReadsThePointsWhenItIsUsed() throws Exception
    {
        LayoutEditor editor = anAutonomyEditor(PAGE);

        String renamed = "Qx Renamed Siding";

        // The editor's own setup, which it copied as it opened
        AutonomySession editing = (AutonomySession) Reflect.get(AutonomyEditorPanel.class, editor.getAutonomyPanel(),
            "session");

        try
        {
            JDialog prompt = pressControlF(editor);

            type(prompt, "qx here");

            assertEquals(rows(prompt), java.util.Arrays.asList(rowOf(HERE, here)), "precondition: the list typed to \"qx here\"");

            support.Components.onTheEventThread(() -> { editing.getStore().setPointName(here, renamed); return null; });

            // Enter on the row read before the rename: the prompt reads again rather than go to a name now gone
            support.Keys.press(boxOf(prompt), KeyEvent.VK_ENTER);

            assertTrue(prompt.isShowing(), "Enter went to a point by the name it no longer has");
            assertTrue(rows(prompt).isEmpty(), "the old name is still listed: " + rows(prompt));

            type(prompt, "renamed");

            assertEquals(rows(prompt), java.util.Arrays.asList(rowOf(renamed, here)), "the new name is not found");
        }
        finally
        {
            support.Components.onTheEventThread(() -> { editing.getStore().setPointName(here, HERE); return null; });

            closeThem();
        }
    }

    /**
     * The points are listed by their letters, as Sort Pages orders pages (`Util.byLetters`): a space counts, so "Main
     * Yard" comes before "Mainline", and "\u00d6lberg" sorts among the O's, before "Zebra", on a machine set to Swedish too.
     *
     * MUTATION: compare the names with `compareToIgnoreCase` in `FindAPointPrompt.narrow`, and this fails.
     */
    @Test
    public void testThePointsAreListedByTheirLetters() throws Exception
    {
        LayoutEditor editor = anAutonomyEditor(PAGE);

        AutonomySession editing = (AutonomySession) Reflect.get(AutonomyEditorPanel.class, editor.getAutonomyPanel(),
            "session");

        TileKey[] tiles = {twinThere, here, there, twinHere};
        String[] names = {"Qy Zebra", "Qy Mainline", "Qy \u00d6lberg", "Qy Main Yard"};

        java.util.Locale was = java.util.Locale.getDefault();

        try
        {
            java.util.Locale.setDefault(new java.util.Locale("sv"));

            support.Components.onTheEventThread(() ->
            {
                for (int i = 0; i < tiles.length; i++) editing.getStore().setPointName(tiles[i], names[i]);

                return null;
            });

            JDialog prompt = pressControlF(editor);

            type(prompt, "qy");

            assertEquals(rows(prompt), java.util.Arrays.asList(rowOf(names[3], twinHere), rowOf(names[1], here),
                rowOf(names[2], there), rowOf(names[0], twinThere)), "the points are not in their letters' order");
        }
        finally
        {
            java.util.Locale.setDefault(was);

            support.Components.onTheEventThread(() ->
            {
                String[] named = {TWIN, HERE, THERE, TWIN};

                for (int i = 0; i < tiles.length; i++) editing.getStore().setPointName(tiles[i], named[i]);

                return null;
            });

            closeThem();
        }
    }

    /**
     * Choosing a point on another page opens that page's editor with the square outlined and flashing.
     *
     * MUTATION: in `LayoutEditor.findAPoint`, call `autonomyPanel.outlineTheNotice(tile, null)` in place of `goToNotice`,
     * and this fails (no page opens).  MUTATION: in `LayoutEditor.reveal`, make the wait for the square's picture never
     * happen (`false && tried < REVEAL_TRIES`), and the flash assertion fails: a freshly opened editor has no picture
     * on the square yet.
     */
    @Test
    public void testChoosingAPointOnAnotherPageOpensItAndHighlightsIt() throws Exception
    {
        LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            JDialog prompt = pressControlF(editor);

            type(prompt, THERE.toLowerCase());
            support.Keys.press(boxOf(prompt), KeyEvent.VK_ENTER);

            LayoutEditor opened = editorAfterTheSwitch(OTHER_PAGE, there);

            assertFalse(editor.isDisplayable(), "the editor on the first page is still there");
            assertHighlighted(opened, there, prompt);
        }
        finally
        {
            closeThem();
        }
    }

    /**
     * Control+F to a point on another page, with unsaved work in the editor, asks the page switch's question first, as a
     * link's other end does: Cancel stays on the page with the work, Discard throws it away and goes (FR-055).
     *
     * MUTATION: leave `settleUnsavedWork` out of the editor's `onJumpToNotice`, and the jump goes without asking.
     */
    @Test(timeOut = 180000)
    public void testControlFToAnotherPageAsksAboutUnsavedWork() throws Exception
    {
        LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            renameTwinHere(editor);

            JDialog prompt = pressControlF(editor);

            type(prompt, THERE.toLowerCase());

            try (support.Answering answering = support.Answering.start(m -> support.Answering.press(
                I18n.t("layout.ui.switchCancel"))))
            {
                support.Keys.press(boxOf(prompt), KeyEvent.VK_ENTER);

                support.Components.settle();

                assertEquals(answering.asked(), java.util.Arrays.asList(I18n.t("layout.ui.confirmSwitchWithUnsavedWork")),
                    "Control+F to another page did not ask the page switch's question about the unsaved work");
            }

            assertTrue(editor.isDisplayable() && editor == support.EditorsOpen.editorOn(PAGE), "Cancel left the page");
            assertEquals(nameOfTwinHere(), "Qx Edited", "Cancel threw the work away");

            prompt = pressControlF(editor);

            type(prompt, THERE.toLowerCase());

            try (support.Answering answering = support.Answering.start(m -> support.Answering.press(
                I18n.t("layout.ui.switchDiscard"))))
            {
                support.Keys.press(boxOf(prompt), KeyEvent.VK_ENTER);

                editorAfterTheSwitch(OTHER_PAGE, there);
            }

            assertEquals(nameOfTwinHere(), TWIN, "Discard kept the work done before the jump");
        }
        finally
        {
            closeThem();

            session.getStore().setPointName(twinHere, TWIN);
        }
    }

    /**
     * A finding clicked on another page asks the same question before the editor goes (FR-055): Discard throws the
     * work away, where the jump used to write it out of Cancel's reach.
     *
     * MUTATION: leave `settleUnsavedWork` out of the editor's `onJumpToNotice`, and the rename is kept unasked.
     */
    @Test(timeOut = 180000)
    public void testAFindingOnAnotherPageAsksAboutUnsavedWork() throws Exception
    {
        final LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            renameTwinHere(editor);

            final AutonomyEditorPanel panel = editor.getAutonomyPanel();

            final JList<?> findings = support.Components.onTheEventThread(
                () -> (JList<?>) panel.getFindingsPanel().getViewport().getView());

            try (support.Answering answering = support.Answering.start(m -> support.Answering.press(
                I18n.t("layout.ui.switchDiscard"))))
            {
                support.Components.onTheEventThread(() ->
                {
                    try
                    {
                        @SuppressWarnings("unchecked")
                        javax.swing.DefaultListModel<String> rows = (javax.swing.DefaultListModel<String>)
                            field(panel, "findingsModel");

                        rows.clear();
                        ((List<?>) field(panel, "findingTiles")).clear();
                        ((List<?>) field(panel, "findingRelated")).clear();
                        ((List<?>) field(panel, "findingSeverity")).clear();

                        rows.addElement("a finding on another page");

                        @SuppressWarnings("unchecked") List<TileKey> tiles = (List<TileKey>) field(panel, "findingTiles");
                        @SuppressWarnings("unchecked") List<List<TileKey>> related =
                            (List<List<TileKey>>) field(panel, "findingRelated");

                        tiles.add(there);
                        related.add(null);
                        ((List<Object>) field(panel, "findingSeverity")).add(null);

                        // Posted: the question it asks is modal
                        javax.swing.SwingUtilities.invokeLater(() -> findings.setSelectedIndex(0));
                    }
                    catch (ReflectiveOperationException e)
                    {
                        throw new IllegalStateException(e);
                    }

                    return null;
                });

                editorAfterTheSwitch(OTHER_PAGE, there);

                assertEquals(answering.asked(), java.util.Arrays.asList(I18n.t("layout.ui.confirmSwitchWithUnsavedWork")),
                    "a finding on another page did not ask the page switch's question about the unsaved work");
            }

            assertEquals(nameOfTwinHere(), TWIN, "Discard kept the work done before the jump");
        }
        finally
        {
            closeThem();

            session.getStore().setPointName(twinHere, TWIN);
        }
    }

    /** Renames a point on the first page through the store, as unsaved work in this editor. */
    private static void renameTwinHere(LayoutEditor editor) throws Exception
    {
        support.Components.onTheEventThread(() ->
        {
            session.getStore().setPointName(twinHere, "Qx Edited");

            return null;
        });

        java.lang.reflect.Method unsaved = Reflect.method(LayoutEditor.class, "hasUnsavedAutonomyWork");

        assertTrue(support.Components.onTheEventThread(() ->
        {
            try { return (Boolean) unsaved.invoke(editor); } catch (Exception e) { throw new RuntimeException(e); }
        }), "precondition: the rename is not unsaved work in the editor");
    }

    private static String nameOfTwinHere()
    {
        return support.Components.onTheEventThread(() -> session.getStore().getPointName(twinHere));
    }

    /**
     * A double-click chooses too, and a point on the page being edited is outlined without the window reopening.
     *
     * MUTATION: in `FindAPointPrompt`'s mouse listener, require three clicks (`e.getClickCount() < 3`); this fails.
     */
    @Test
    public void testDoubleClickChoosesAPointOnThisPageWithoutReopening() throws Exception
    {
        LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            JDialog prompt = pressControlF(editor);

            type(prompt, HERE);

            final JList<?> list = support.Components.find(prompt, JList.class, l -> true);

            support.Components.onTheEventThread(() ->
            {
                java.awt.Rectangle row = list.getCellBounds(0, 0);
                java.awt.Point at = new java.awt.Point(row.x + 5, row.y + 5);

                for (int count = 1; count <= 2; count++)
                {
                    list.dispatchEvent(new java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), 0, at.x, at.y, count, false));
                }

                return null;
            });

            support.Components.settle();

            assertTrue(editor.isDisplayable() && editor.isShowing(), "the window went for a point on its own page");
            assertHighlighted(editor, here, prompt);
        }
        finally
        {
            closeThem();
        }
    }

    /**
     * Find a Point... is a Tools item in the autonomy editor, shown with its shortcut named, and in the Track Diagram
     * editor it is not shown at all (the Tools menu has no stray separator either), and Control+F does nothing there.
     * Switching the open editor between the two modes shows and hides it again.
     *
     * MUTATION: in `LayoutEditor.refreshFindPointItem`, `findPointItem.setVisible(true)`; this fails.
     */
    @Test
    public void testTrackDiagramModeHidesItAndControlFDoesNothing() throws Exception
    {
        LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            JMenuItem item = findItemOf(editor);

            assertTrue(item.isEnabled() && item.isVisible(), "Find a Point... is not shown in the autonomy editor");
            assertSame(item.getAccelerator(), LayoutEditor.FIND_A_POINT_KEY, "Find a Point... does not show its key");

            // Opened by the menu as well as by the key
            support.Components.onTheEventThread(() ->
            {
                item.doClick();

                return null;
            });

            support.Components.settle();

            JDialog byMenu = aPrompt();

            assertNotNull(byMenu, "the menu item opened no prompt");

            support.Keys.press(boxOf(byMenu), KeyEvent.VK_ESCAPE);
        }
        finally
        {
            closeThem();
        }

        LayoutEditor track = aTrackEditor(PAGE);

        try
        {
            JMenuItem item = findItemOf(track);

            assertFalse(item.isVisible(), "Find a Point... is shown in the Track Diagram editor");
            assertFalse(lastToolsItemIsASeparator(track), "the Tools menu ends in a separator");

            support.Keys.press(track, KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);

            assertNull(aPrompt(), "Control+F opened the prompt in the Track Diagram editor");
        }
        finally
        {
            closeThem();
        }
    }

    /**
     * A finding that names a point on another page opens that page and outlines the point after the window switch.
     *
     * MUTATION: in `EditorWindows.openLayoutEditor`, leave out the `outlineTheNotice` call, and this fails; with
     * `LayoutEditor.reveal`'s wait for the square's picture removed, the flash assertion fails (the outline alone
     * survived the switch before FR-055).
     */
    @Test
    public void testAFindingOnAnotherPageHighlightsItAfterTheSwitch() throws Exception
    {
        final LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            final AutonomyEditorPanel panel = editor.getAutonomyPanel();

            // A finding row naming the other page's point and a neighbor, in the list the user clicks
            final JList<?> findings = support.Components.onTheEventThread(
                () -> (JList<?>) panel.getFindingsPanel().getViewport().getView());

            support.Components.onTheEventThread(() ->
            {
                try
                {
                    @SuppressWarnings("unchecked")
                    javax.swing.DefaultListModel<String> rows = (javax.swing.DefaultListModel<String>)
                        field(panel, "findingsModel");

                    rows.clear();
                    ((List<?>) field(panel, "findingTiles")).clear();
                    ((List<?>) field(panel, "findingRelated")).clear();
                    ((List<?>) field(panel, "findingSeverity")).clear();

                    rows.addElement("a finding on another page");

                    @SuppressWarnings("unchecked") List<TileKey> tiles = (List<TileKey>) field(panel, "findingTiles");
                    @SuppressWarnings("unchecked") List<List<TileKey>> related =
                        (List<List<TileKey>>) field(panel, "findingRelated");
                    @SuppressWarnings("unchecked") List<Object> severity = (List<Object>) field(panel, "findingSeverity");

                    tiles.add(there);
                    related.add(java.util.Arrays.asList(twinThere));
                    severity.add(null);

                    findings.setSelectedIndex(0);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }

                return null;
            });

            LayoutEditor opened = editorAfterTheSwitch(OTHER_PAGE, there);

            assertFalse(editor.isDisplayable(), "the editor on the first page is still there");
            assertTrue(opened.getAutonomyPanel().isOutlined(there), "the finding's point is not outlined after the switch");
            assertTrue(opened.getAutonomyPanel().isOutlined(twinThere), "the finding's other square is not outlined");
            assertTrue(isFlashing(opened, there), "the finding's point is not flashing after the switch");
        }
        finally
        {
            closeThem();
        }
    }

    // ---- helpers

    /** Closes every editor by its X, as the user does, so the main window is told and opens the next. */
    private static void closeThem() throws Exception
    {
        for (final LayoutEditor editor : support.EditorsOpen.editors())
        {
            support.Components.onTheEventThread(() ->
            {
                editor.dispatchEvent(new java.awt.event.WindowEvent(editor, java.awt.event.WindowEvent.WINDOW_CLOSING));

                return null;
            });
        }

        support.Components.waitFor(() -> support.EditorsOpen.editors().isEmpty(), 5000);
        support.Components.settle();
    }

    private static Object field(Object on, String name) throws ReflectiveOperationException
    {
        return Reflect.get(on.getClass(), on, name);
    }

    /** Opens the autonomy editor as the main window's Edit does, and waits for its build. */
    private static LayoutEditor anAutonomyEditor(String page) throws Exception
    {
        closeThem();

        support.Components.onTheEventThread(() ->
        {
            ui.openLayoutEditor(page, Boolean.TRUE, null);

            return null;
        });

        assertTrue(support.Components.waitFor(() -> editorReady(page), 15000), "the autonomy editor on " + page
            + " did not open: " + support.EditorsOpen.titles());

        return support.EditorsOpen.editorOn(page);
    }

    private static LayoutEditor aTrackEditor(String page) throws Exception
    {
        closeThem();

        support.Components.onTheEventThread(() ->
        {
            ui.openLayoutEditor(page, Boolean.FALSE, null);

            return null;
        });

        assertTrue(support.Components.waitFor(() -> support.EditorsOpen.editorOn(page) != null
            && support.EditorsOpen.editorOn(page).getJMenuBar() != null, 15000), "the track editor did not open: "
            + support.EditorsOpen.titles());

        support.Components.settle();

        return support.EditorsOpen.editorOn(page);
    }

    private static boolean editorReady(String page)
    {
        LayoutEditor editor = support.EditorsOpen.editorOn(page);

        return editor != null && editor.isAutonomyMode() && editor.getJMenuBar() != null;
    }

    /** The editor the main window opened on a page after a jump, once it is built and has outlined the square. */
    private static LayoutEditor editorAfterTheSwitch(String page, TileKey outlined) throws Exception
    {
        assertTrue(support.Components.waitFor(() -> editorReady(page)
            && support.EditorsOpen.editorOn(page).getAutonomyPanel().isOutlined(outlined), 15000),
            "no editor opened on " + page + " with the square outlined: " + support.EditorsOpen.titles());

        return support.EditorsOpen.editorOn(page);
    }

    private static boolean isFlashing(LayoutEditor editor, TileKey tile) throws Exception
    {
        java.lang.reflect.Field gridField = Reflect.field(LayoutEditor.class, "grid");

        final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) gridField.get(editor);

        final boolean[] seen = new boolean[1];

        // The flash is the editor's own work after the switch, posted behind the build; a moment, not instantly
        support.Components.waitFor(() ->
        {
            seen[0] = support.Components.onTheEventThread(() ->
            {
                LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

                return label != null && label.isFlashOutstanding();
            });

            return seen[0];
        }, 1500);

        return seen[0];
    }

    /** Whether the last shown part of the Tools menu is a separator, which would hang under nothing. */
    private static boolean lastToolsItemIsASeparator(LayoutEditor editor) throws Exception
    {
        return support.Components.onTheEventThread(() ->
        {
            java.awt.Component[] parts = editor.getJMenuBar().getMenu(0).getMenuComponents();

            for (int i = parts.length - 1; i >= 0; i--)
            {
                if (parts[i].isVisible()) return parts[i] instanceof javax.swing.JSeparator;
            }

            return false;
        });
    }

    /**
     * Switching an open editor between the modes shows Find a Point... in autonomy mode and hides it in Track Diagram
     * mode, with the Tools menu built once.
     *
     * MUTATION: leave `refreshFindPointItem` out of `setAutonomyMode` and this fails.
     */
    @Test
    public void testSwitchingModesShowsAndHidesIt() throws Exception
    {
        LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            JMenuItem item = findItemOf(editor);

            support.Components.press(support.Components.withText(editor, I18n.t("layout.ui.sidebarTrack")));
            support.Components.waitFor(() -> !editor.isAutonomyMode(), 15000);
            support.Components.settle();

            assertFalse(findItemOf(editor).isVisible(), "still shown after switching to Track Diagram mode");

            support.Components.press(support.Components.withText(editor, I18n.t("layout.ui.sidebarAutonomy")));
            support.Components.waitFor(() -> editor.isAutonomyMode(), 15000);
            support.Components.settle();

            assertTrue(findItemOf(editor).isVisible(), "not shown again after switching back");
        }
        finally
        {
            closeThem();
        }
    }

    /**
     * Find a Point... shows the editor's own Control+F on its right and its tooltip keeps only
     * its sentence; the menu binds the key to nothing, so Control+F runs Find a Point once, through the editor's key
     * handler, which still opens the prompt.
     *
     * MUTATION: drop the "none" binding in `TrainControlUI.showShortcut`, and the menu bar clicks the item on Control+F.
     */
    @Test
    public void testTheItemShowsTheKeyAndBindsNothing() throws Exception
    {
        final LayoutEditor editor = anAutonomyEditor(PAGE);

        try
        {
            final JMenuItem item = findItemOf(editor);

            assertSame(item.getAccelerator(), LayoutEditor.FIND_A_POINT_KEY, "the item does not show the key the"
                + " editor acts on");

            assertEquals(item.getToolTipText(), I18n.t("layout.ui.tooltipFindAPoint"), "the tooltip is not its sentence");

            // What the window's key handling asks of its menu bar for a key no component took (Swing's
            // KeyboardManager), asked directly; the item's own handler is taken off first, so the mutation opens nothing.
            final java.lang.reflect.Method bindings = javax.swing.JMenuBar.class.getDeclaredMethod("processKeyBinding",
                javax.swing.KeyStroke.class, KeyEvent.class, int.class, boolean.class);

            bindings.setAccessible(true);

            final int[] clicked = new int[1];

            support.Components.onTheEventThread(() ->
            {
                java.awt.event.ActionListener[] own = item.getActionListeners();
                java.awt.event.ActionListener counting = e -> clicked[0]++;

                for (java.awt.event.ActionListener each : own) item.removeActionListener(each);

                item.addActionListener(counting);

                bindings.invoke(editor.getJMenuBar(), LayoutEditor.FIND_A_POINT_KEY, new KeyEvent(editor.getRootPane(),
                    KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_F,
                    KeyEvent.CHAR_UNDEFINED), javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW, true);

                item.removeActionListener(counting);

                for (java.awt.event.ActionListener each : own) item.addActionListener(each);

                return null;
            });

            assertEquals(clicked[0], 0, "the menu acted on Control+F itself: with the editor's handler, it acts twice");

            support.Keys.press(boxOf(pressControlF(editor)), KeyEvent.VK_ESCAPE);
        }
        finally
        {
            closeThem();
        }
    }

    private static JMenuItem findItemOf(LayoutEditor editor) throws Exception
    {
        return support.Components.onTheEventThread(() ->
        {
            javax.swing.JMenu tools = editor.getJMenuBar().getMenu(0);

            for (int i = 0; i < tools.getItemCount(); i++)
            {
                JMenuItem item = tools.getItem(i);

                if (item != null && I18n.t("layout.ui.menuFindAPoint").equals(item.getText())) return item;
            }

            throw new AssertionError("no Find a Point... on the Tools menu");
        });
    }

    /** The Control+F the user presses; the prompt it opens. */
    private static JDialog pressControlF(LayoutEditor editor) throws Exception
    {
        support.Keys.press(editor, KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);

        assertTrue(support.Components.waitFor(() -> aPrompt() != null, 5000), "Control+F opened no prompt");

        return aPrompt();
    }

    private static JDialog aPrompt()
    {
        return support.Components.onTheEventThread(() ->
        {
            for (Window window : Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()
                    && I18n.t("layout.ui.findPointTitle").equals(((JDialog) window).getTitle()))
                {
                    return (JDialog) window;
                }
            }

            return null;
        });
    }

    private static JTextField boxOf(JDialog prompt)
    {
        return support.Components.find(prompt, JTextField.class, f -> true);
    }

    private static void type(JDialog prompt, final String text)
    {
        final JTextField box = boxOf(prompt);

        support.Components.onTheEventThread(() ->
        {
            box.setText(text);

            return null;
        });

        support.Components.settle();
    }

    /** What the user chose is drawn outlined and flashing in the window that shows its page, and the prompt is gone. */
    private static void assertHighlighted(LayoutEditor shown, TileKey tile, JDialog prompt) throws Exception
    {
        assertTrue(shown.getAutonomyPanel().isOutlined(tile), "the chosen point is not outlined");
        assertTrue(isFlashing(shown, tile), "the chosen point is not flashing");
        assertFalse(prompt.isShowing(), "the prompt is still up");
    }

    private static List<String> rows(JDialog prompt)
    {
        final JList<?> list = support.Components.find(prompt, JList.class, l -> true);

        return support.Components.onTheEventThread(() -> java.util.stream.IntStream.range(0, list.getModel().getSize())
            .mapToObj(i -> String.valueOf(list.getModel().getElementAt(i))).collect(java.util.stream.Collectors.toList()));
    }

    private static String statusOf(JDialog prompt)
    {
        return support.Components.onTheEventThread(() ->
        {
            javax.swing.JLabel last = null;

            for (javax.swing.JLabel label : support.Components.all(prompt.getContentPane(), javax.swing.JLabel.class,
                l -> true)) last = label;

            return last.getText();
        });
    }

    private static String rowOf(String name, TileKey tile)
    {
        return I18n.f("layout.ui.findPointRow", name, tile.getPage());
    }
}
