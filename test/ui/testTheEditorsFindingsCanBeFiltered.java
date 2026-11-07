package ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * The autonomy editor's "Things to look at" list has a filter box (FR-073).  Every claim types into the box the
 * editor mounts and reads the list the user sees; the rows and words come from the railway itself.
 *
 * @author Adam
 */
public class testTheEditorsFindingsCanBeFiltered
{
    private static final support.SandboxedWindow held = new support.SandboxedWindow();
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutDiagram page;

    private static final String PAGE = "1 - Main";
    private static final String OTHER_PAGE = "2 - Bottom";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the autonomy editor needs a display");

        held.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot")));

        model = support.TestStation.simulatedInDebug();

        ui = held.hold(support.Components.onTheEventThread(TrainControlUI::new));
        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        assertNotNull(session, "no setup, so there is no editor to open");
        assertNotNull(session.getStore().getActiveConfiguration(), "no configuration, so there is no editor to open");

        page = model.getLayout(PAGE);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null) model.stop();
        }
        finally
        {
            held.close();
        }
    }

    // ---- claims

    /**
     * Typing narrows the rows to those containing the text, and each heading says how many of its rows are left.
     *
     * MUTATION: in `AutonomyEditorPanel.section`, keep every row (drop the `contains` test), or print the count
     * without "of"; this fails.
     */
    @Test
    public void testTypingNarrowsTheRowsAndTheCountsSay() throws Exception
    {
        LayoutEditor editor = anEditor();

        try
        {
            List<String> all = rows(editor);
            String word = aWord(all, false);

            type(editor, word);

            List<String> shown = rows(editor);

            for (String row : findings(all))
            {
                assertEquals(shown.contains(row), row.toLowerCase().contains(word),
                    "the row \"" + row + "\" with \"" + word + "\" typed");
            }

            for (String heading : headings(all))
            {
                int total = findings(under(all, heading)).size();
                int left = 0;

                for (String row : findings(under(all, heading))) if (row.toLowerCase().contains(word)) left++;

                if (left == 0) continue;

                assertTrue(shown.contains(headingOf(heading, left, total)),
                    "the heading of " + total + " rows with " + left + " left, among " + shown);
            }

            assertTrue(findings(shown).size() < findings(all).size(), "precondition: the word narrowed nothing");
        }
        finally
        {
            close(editor);
        }
    }

    /**
     * A heading none of whose rows match goes, and the others stay.
     *
     * MUTATION: in `AutonomyEditorPanel.section`, add the heading before the empty-rows return; this fails.
     */
    @Test
    public void testAHeadingWithNoMatchesGoes() throws Exception
    {
        LayoutEditor editor = anEditor();

        try
        {
            List<String> all = rows(editor);
            String word = aWord(all, true);

            type(editor, word);

            List<String> shown = rows(editor);
            int headingsLeft = 0;

            for (String heading : headings(all))
            {
                boolean any = false;

                for (String row : findings(under(all, heading))) any |= row.toLowerCase().contains(word);

                if (any) headingsLeft++;
            }

            assertEquals(headings(shown).size(), headingsLeft, "the headings left with \"" + word + "\" typed: " + shown);
            assertTrue(headingsLeft < headings(all).size(), "precondition: every heading had a match");
        }
        finally
        {
            close(editor);
        }
    }

    /**
     * Nothing matching says so, and capitals do not matter.
     *
     * MUTATION: drop the `findingsNoMatch` row from `showFindings` (empty list), or compare without
     * `toLowerCase`; this fails.
     */
    @Test
    public void testNothingMatchingSaysSoAndCapitalsAreIgnored() throws Exception
    {
        LayoutEditor editor = anEditor();

        try
        {
            List<String> all = rows(editor);

            type(editor, "zzqqxx");

            List<String> shown = rows(editor);

            assertEquals(shown.size(), 1, "the list with nothing matching: " + shown);
            assertEquals(shown.get(0), I18n.f("autosetup.ui.findingsNoMatch", "zzqqxx"));

            String word = aWord(all, false);

            type(editor, word);

            List<String> lower = rows(editor);

            type(editor, word.toUpperCase());

            assertEquals(rows(editor), lower, "capitals typed");
            assertTrue(findings(lower).size() > 0, "precondition: the word matched nothing");
        }
        finally
        {
            close(editor);
        }
    }

    /**
     * The x clears the box and brings every row back.
     *
     * MUTATION: make the button's action do nothing; this fails.
     */
    @Test
    public void testTheCrossClearsTheFilter() throws Exception
    {
        LayoutEditor editor = anEditor();

        try
        {
            List<String> all = rows(editor);

            type(editor, aWord(all, false));

            assertTrue(rows(editor).size() < all.size(), "precondition: typing narrowed nothing");

            final javax.swing.JButton cross = theCross(editor);

            SwingUtilities.invokeAndWait(() -> cross.doClick());

            assertEquals(box(editor).getText(), "", "the box after the cross");
            assertEquals(rows(editor), all, "the list after the cross");
        }
        finally
        {
            close(editor);
        }
    }

    /**
     * Typing selects no row: nothing jumps or is revealed, and the clipboard keeps what it had.
     *
     * MUTATION: select a row at the end of `showFindings`; this fails.
     */
    @Test
    public void testTypingNeverSelectsNorCopiesNorJumps() throws Exception
    {
        LayoutEditor editor = anEditor();
        String was = clipboard();

        try
        {
            final List<String> events = new ArrayList<>();
            AutonomyEditorPanel panel = editor.getAutonomyPanel();

            panel.setOnReveal(tile -> events.add("reveal " + tile));
            panel.setOnJumpToPage(tile -> events.add("page " + tile));
            panel.setOnJumpToNotice((tile, named) -> events.add("notice " + tile));

            String sentinel = "FR-073 sentinel " + System.nanoTime();

            boolean held = setClipboard(sentinel);

            List<String> all = rows(editor);
            String word = aWord(all, false);

            for (char c : word.toCharArray()) type(editor, box(editor).getText() + c);

            assertEquals(list(editor).getSelectedIndex(), -1, "the row selected after typing");

            if (held) assertEquals(clipboard(), sentinel, "the clipboard after typing");
            assertTrue(events.isEmpty(), "what happened while typing: " + events);
            assertTrue(findings(rows(editor)).size() > 0, "precondition: nothing was left to select");
        }
        finally
        {
            setClipboard(was);

            close(editor);
        }
    }

    /**
     * A click on a filtered row reaches that row's finding, the one the same row reaches unfiltered.
     *
     * MUTATION: in `section`, skip `findingTiles.add(...)` for the rows after the first; this fails.
     */
    @Test
    public void testAClickOnAFilteredRowReachesItsFinding() throws Exception
    {
        LayoutEditor editor = anEditor();
        String was = clipboard();

        try
        {
            final List<Object> reached = new ArrayList<>();
            AutonomyEditorPanel panel = editor.getAutonomyPanel();

            panel.setOnReveal(tile -> reached.add(tile));
            panel.setOnJumpToPage(tile -> reached.add(tile));
            panel.setOnJumpToNotice((tile, named) -> reached.add(tile));

            // What each row reaches with nothing typed
            Map<String, Object> expected = new LinkedHashMap<>();
            List<String> all = rows(editor);

            for (String row : findings(all))
            {
                reached.clear();

                click(editor, all.indexOf(row));

                if (reached.size() == 1) expected.put(row, reached.get(0));

                SwingUtilities.invokeAndWait(() -> list(editor).clearSelection());
            }

            assertTrue(expected.size() >= 2, "precondition: fewer than two rows reach a square: " + all);

            String word = aWord(all, false);

            type(editor, word);

            List<String> shown = rows(editor);
            int clicked = 0;

            for (String row : findings(shown))
            {
                if (!expected.containsKey(row)) continue;

                reached.clear();

                click(editor, shown.indexOf(row));

                assertEquals(reached, java.util.Collections.singletonList(expected.get(row)),
                    "the square the filtered row \"" + row + "\" reached");

                clicked++;

                SwingUtilities.invokeAndWait(() -> list(editor).clearSelection());
            }

            assertTrue(clicked > 0, "precondition: no filtered row reaches a square");
        }
        finally
        {
            setClipboard(was);

            close(editor);
        }
    }

    /**
     * The list is rebuilt on every refresh, and the filter is applied again; the banner counts everything.
     *
     * MUTATION: in `refresh`, fill the list without `showFindings` (the unfiltered rows), or make the banner count
     * from the filtered rows; this fails.
     */
    @Test
    public void testARebuildKeepsTheFilterAndTheBannerCountsAll() throws Exception
    {
        LayoutEditor editor = anEditor();

        try
        {
            final AutonomyEditorPanel panel = editor.getAutonomyPanel();
            List<String> all = rows(editor);
            String banner = panel.getBanner().getText();

            type(editor, aWord(all, false));

            List<String> shown = rows(editor);

            assertEquals(panel.getBanner().getText(), banner, "the banner after typing");

            SwingUtilities.invokeAndWait(() -> panel.refresh());

            assertEquals(rows(editor), shown, "the list after a rebuild");
            assertEquals(panel.getBanner().getText(), banner, "the banner after a rebuild with the filter on");
        }
        finally
        {
            close(editor);
        }
    }

    /**
     * The filter stays across a page change in place and across each door that closes the editor and opens another.
     *
     * MUTATION: in `LayoutEditor`, drop `setFindingFilter(findingFilter)` (page change in place), or pass null for
     * `typed` in any of the three reopenings; this fails.
     */
    @Test
    public void testThePageChangeKeepsTheFilter() throws Exception
    {
        List<LayoutEditor> opened = new ArrayList<>();

        try
        {
            LayoutEditor editor = anEditor();

            opened.add(editor);

            String word = aWord(rows(editor), false);

            type(editor, word);

            // IN PLACE
            final java.lang.reflect.Method leave =
                LayoutEditor.class.getDeclaredMethod("leaveFor", String.class, boolean.class);

            leave.setAccessible(true);
            support.Components.onTheEventThread(() -> leave.invoke(editor, OTHER_PAGE, true));

            settle();

            assertTrue(editor.isDisplayable(), "precondition: the page change closed the window");
            assertEquals(editor.getAutonomyPanel().getFindingFilter(), word, "the box after a page change in place");

            List<String> narrowed = rows(editor);

            for (String row : findings(narrowed)) assertTrue(row.toLowerCase().contains(word), row);

            // THE DOORS THAT REOPEN, all asked before one is reported
            LayoutEditor from = editor;
            StringBuilder wrong = new StringBuilder();

            for (String door : new String[] {"notice", "finding", "link"})
            {
                LayoutEditor to = throughDoor(from, door, new TileKey(PAGE, 1, 1));

                opened.add(to);

                if (!word.equals(to.getAutonomyPanel().getFindingFilter()))
                {
                    wrong.append(" the box after the ").append(door).append("'s door is \"")
                        .append(to.getAutonomyPanel().getFindingFilter()).append("\";");

                    // So the next door is asked on its own
                    type(to, word);
                }

                from = to;
            }

            assertEquals(wrong.toString(), "", "the filter \"" + word + "\" typed before the doors:");
        }
        finally
        {
            for (LayoutEditor each : opened) close(each);

            SwingUtilities.invokeAndWait(() -> ui.setEditLayoutEnabled(true));
        }
    }

    /**
     * Closing the editor clears the filter: the next editor opens with an empty box.
     *
     * MUTATION: in `EditorWindows.openCarryingTheClick`, leave `filterCarried` set after the opening; this fails.
     */
    @Test
    public void testClosingTheEditorClearsTheFilter() throws Exception
    {
        List<LayoutEditor> opened = new ArrayList<>();

        try
        {
            // Opened as the main window opens it, so a closing tells it
            LayoutEditor first = openedBy(() -> ui.openAutonomyEditor(new TileKey(PAGE, 1, 1)), null);

            opened.add(first);

            List<String> unfiltered = rows(first);

            type(first, aWord(unfiltered, false));

            // Through a door, which hands the text on; the editor it opens is the one closed
            LayoutEditor second = throughDoor(first, "notice", new TileKey(PAGE, 1, 1));

            opened.add(second);

            assertTrue(!box(second).getText().isEmpty(), "precondition: the door did not carry the text");

            close(second, true);

            LayoutEditor next = openedBy(() -> ui.openAutonomyEditor(new TileKey(PAGE, 1, 1)), second);

            opened.add(next);

            assertEquals(box(next).getText(), "", "the box of an editor opened after the first was closed");
            assertEquals(rows(next), unfiltered, "the list of an editor opened after the first was closed");
        }
        finally
        {
            for (LayoutEditor each : opened) close(each);

            SwingUtilities.invokeAndWait(() -> ui.setEditLayoutEnabled(true));
        }
    }

    /**
     * The box leaves the keyboard with the window as every other control does: it cannot be focused until it is
     * clicked, and Escape clears it and gives the keyboard back.
     *
     * MUTATION: `setFocusable(true)` in the field's constructor, or no `letGo` on Escape; this fails.
     */
    @Test
    public void testTheBoxGivesTheKeyboardBack() throws Exception
    {
        LayoutEditor editor = anEditor();

        try
        {
            final javax.swing.JTextField box = box(editor);

            assertFalse(box.isFocusable(), "the box before it is clicked");

            SwingUtilities.invokeAndWait(() -> box.dispatchEvent(new java.awt.event.MouseEvent(box,
                java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 2, 2, 1, false)));

            assertTrue(box.isFocusable(), "the box once clicked");

            type(editor, "ab");

            SwingUtilities.invokeAndWait(() -> box.dispatchEvent(new java.awt.event.KeyEvent(box,
                java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ESCAPE,
                (char) 27)));

            assertEquals(box.getText(), "", "the box after Escape");
            assertFalse(box.isFocusable(), "the box after Escape");
        }
        finally
        {
            close(editor);
        }
    }

    // ---- helpers

    private static LayoutEditor anEditor() throws Exception
    {
        LayoutEditor made = support.Components.onTheEventThread(() ->
        {
            LayoutEditor fresh = new LayoutEditor(page, 30, ui, 0);

            fresh.render();
            fresh.setAutonomyMode(session);

            return fresh;
        });

        settle();

        return made;
    }

    private static void close(LayoutEditor on) throws Exception
    {
        close(on, false);
    }

    /** Puts an editor away: by its X when the main window must be told, else a bare dispose. */
    private static void close(LayoutEditor on, boolean asTheUserDoes) throws Exception
    {
        if (on == null || !on.isDisplayable()) return;

        support.Components.onTheEventThread(() ->
        {
            if (asTheUserDoes) on.dispatchEvent(new java.awt.event.WindowEvent(on, java.awt.event.WindowEvent.WINDOW_CLOSING));
            else on.dispose();

            return null;
        });

        settle();
    }

    /** What a window posts while it opens or closes. */
    private static void settle() throws Exception
    {
        support.Components.settle();
    }

    private static javax.swing.JList<?> list(LayoutEditor editor)
    {
        return (javax.swing.JList<?>) editor.getAutonomyPanel().getFindingsPanel().getViewport().getView();
    }

    private static List<String> rows(LayoutEditor editor) throws Exception
    {
        final List<String> out = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.ListModel<?> m = list(editor).getModel();

            for (int i = 0; i < m.getSize(); i++) out.add(String.valueOf(m.getElementAt(i)));
        });

        return out;
    }

    private static List<String> findings(List<String> rows)
    {
        List<String> out = new ArrayList<>();

        for (String row : rows) if (row.startsWith("   ")) out.add(row);

        return out;
    }

    private static List<String> headings(List<String> rows)
    {
        List<String> out = new ArrayList<>();

        for (String row : rows) if (!row.startsWith("   ")) out.add(row);

        return out;
    }

    /** The finding rows under one heading. */
    private static List<String> under(List<String> rows, String heading)
    {
        List<String> out = new ArrayList<>();
        boolean in = false;

        for (String row : rows)
        {
            if (!row.startsWith("   ")) in = row.equals(heading);
            else if (in) out.add(row);
        }

        return out;
    }

    /** The heading text of a filtered section, by whichever key the unfiltered heading was built from. */
    private static String headingOf(String unfiltered, int left, int total)
    {
        String[] keys = {"Errors", "WarningsShort", "Notices"};

        for (String key : keys)
        {
            if (unfiltered.equals(I18n.f("autosetup.ui.heading" + key, total)))
            {
                return I18n.f("autosetup.ui.heading" + key + "Of", left, total);
            }
        }

        throw new AssertionError("precondition: not a heading: " + unfiltered);
    }

    /**
     * A word out of the rows that matches some finding rows but not all; with {@code oneHeading}, one that also
     * leaves a whole heading without a match.
     */
    private static String aWord(List<String> all, boolean oneHeading)
    {
        List<String> found = findings(all);

        for (String row : found)
        {
            for (String word : row.toLowerCase().split("[^a-z0-9]+"))
            {
                // Letters only, so capitals can differ
                if (!word.matches("[a-z]{4,}")) continue;

                int matches = 0;
                java.util.Set<String> sections = new java.util.HashSet<>();

                for (String heading : headings(all))
                {
                    for (String each : findings(under(all, heading)))
                    {
                        if (each.toLowerCase().contains(word))
                        {
                            matches++;
                            sections.add(heading);
                        }
                    }
                }

                if (matches == 0 || matches == found.size()) continue;

                if (oneHeading && sections.size() == headings(all).size()) continue;

                return word;
            }
        }

        throw new AssertionError("precondition: no word narrows the rows" + (oneHeading ? " and drops a heading" : "")
            + ": " + all);
    }

    private static javax.swing.JTextField box(LayoutEditor editor)
    {
        return support.Components.inside(editor.getAutonomyPanel().getFindingsBox(), javax.swing.JTextField.class);
    }

    private static javax.swing.JButton theCross(LayoutEditor editor)
    {
        return support.Components.inside(editor.getAutonomyPanel().getFindingsBox(), javax.swing.JButton.class);
    }

    /** Typing: the text set in the box, as the document sees it. */
    private static void type(LayoutEditor editor, String text) throws Exception
    {
        final javax.swing.JTextField box = box(editor);

        assertNotNull(box, "the filter box");

        SwingUtilities.invokeAndWait(() -> box.setText(text));
    }

    /** A click on a row of the list: its selection changes. */
    private static void click(LayoutEditor editor, int row) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> list(editor).setSelectedIndex(row));
    }

    private static String clipboard()
    {
        try
        {
            return (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    /** Best effort: another program may hold the clipboard; whether it took is the answer. */
    private static boolean setClipboard(String text)
    {
        java.awt.datatransfer.StringSelection s = new java.awt.datatransfer.StringSelection(text == null ? "" : text);

        return support.Components.waitFor(() ->
        {
            try
            {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(s, s);

                return true;
            }
            catch (IllegalStateException busy)
            {
                return false;
            }
        }, 500);
    }

    /** The listener a panel holds for one of the editor's own reopenings, by its field's name. */
    private static Object doorOf(LayoutEditor from, String field) throws Exception
    {
        java.lang.reflect.Field held = AutonomyEditorPanel.class.getDeclaredField(field);

        held.setAccessible(true);

        return held.get(from.getAutonomyPanel());
    }

    @SuppressWarnings("unchecked")
    private static LayoutEditor throughDoor(LayoutEditor from, String door, TileKey tile) throws Exception
    {
        Runnable opening;

        if ("finding".equals(door))
        {
            opening = () -> ((java.util.function.Consumer<TileKey>) doorUnchecked(from, "onJumpToPage")).accept(tile);
        }
        else if ("notice".equals(door))
        {
            opening = () -> ((java.util.function.BiConsumer<TileKey, List<TileKey>>) doorUnchecked(from, "onJumpToNotice"))
                .accept(tile, new ArrayList<TileKey>());
        }
        else
        {
            opening = () -> from.jumpToSquare(tile);
        }

        LayoutEditor opened = openedBy(opening, from);

        assertFalse(from.isDisplayable(), "precondition: the " + door + "'s door did not close the window it left");

        return opened;
    }

    private static Object doorUnchecked(LayoutEditor from, String field)
    {
        try
        {
            return doorOf(from, field);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    /** The editor the main window shows once {@code opening} has run, a different window from {@code not}. */
    private static LayoutEditor openedBy(Runnable opening, LayoutEditor not) throws Exception
    {
        support.Components.onTheEventThread(() ->
        {
            ui.setEditLayoutEnabled(true);
            opening.run();

            return null;
        });

        final java.lang.reflect.Field shown = TrainControlUI.class.getDeclaredField("openEditor");

        shown.setAccessible(true);

        final LayoutEditor[] now = new LayoutEditor[1];

        boolean came = support.Components.waitFor(() ->
        {
            try
            {
                Object open = shown.get(ui);

                now[0] = open instanceof LayoutEditor && open != not ? (LayoutEditor) open : null;
            }
            catch (IllegalAccessException e)
            {
                throw new IllegalStateException(e);
            }

            return now[0] != null && now[0].isDisplayable() && now[0].isAutonomyMode() && now[0].getAutonomyPanel() != null;
        }, 20000);

        assertTrue(came, "precondition: no autonomy editor was opened");

        settle();

        return now[0];
    }
}
