package regression;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.lang.reflect.Field;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Under Visible Elements, the first control is as far from the heading as under the editor's other headings (OB-289),
 * in both editors: the autonomy editor hides Text Labels (FR-061), so the gap above Grid must not stand at the top of
 * the column.  And the column is all there at the window's smallest size (OB-342), and the autonomy editor's notices
 * float over the bottom of the diagram without moving it (OB-366).
 *
 * MUTATION: leave the gap showing when Text Labels is hidden, and the autonomy claim fails.
 *
 * @author Adam
 */
public class testTheVisibleElementsHeadingIsSpacedLikeTheOthers
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutDiagram page;

    /** The run's own copy of "remember window location" for this folder, put back at the end. */
    private static String rememberedWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor is a window, and the window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        // THE EDITOR AT ITS OWN SIZE, NOT THE OPERATOR'S: the run copies his settings for this folder, and with "remember window
        // location" on, the editor opens at his saved bounds, where nothing may lie under the band.  Set, never toggled.
        rememberedWas = TrainControlUI.getPrefs().get(org.traincontrol.gui.widgets.PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, null);

        TrainControlUI.getPrefs().putBoolean(org.traincontrol.gui.widgets.PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, false);

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null) throw new AssertionError("the sandbox holds no autonomy setup, so there is no autonomy editor");

        page = model.getLayout(model.getLayoutList().get(0));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (rememberedWas == null) TrainControlUI.getPrefs().remove(org.traincontrol.gui.widgets.PositionAwareJFrame.REMEMBER_WINDOW_LOCATION);
            else TrainControlUI.getPrefs().put(org.traincontrol.gui.widgets.PositionAwareJFrame.REMEMBER_WINDOW_LOCATION, rememberedWas);

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * In the track diagram editor the column is right: Visible Elements has the gap Diagram Size has over its buttons.
     */
    @Test
    public void testInTheTrackEditor() throws Exception
    {
        int[] gaps = gaps(false);

        assertEquals(gaps[1], gaps[0], "in the track diagram editor the first control under Visible Elements is " + gaps[1]
            + " pixels below it, and the first under Diagram Size " + gaps[0]);
    }

    /** And in the autonomy editor, where Adam saw it, Visible Elements has that same gap. */
    @Test
    public void testInTheAutonomyEditor() throws Exception
    {
        int standard = gaps(false)[1];

        int here = gaps(true)[1];

        assertEquals(here, standard, "in the autonomy editor the first control under Visible Elements is " + here
            + " pixels below it, and " + standard + " in the track diagram editor - OB-289, \"slightly too much"
            + " spacing/padding below visible elements\"");
    }

    /**
     * The checkboxes under Visible Elements are evenly spaced, and spaced alike in both editors (OB-312): the form
     * leaves 4 pixels between its own checkboxes, so the columns built beside it must not leave the heading's gap
     * between them.  Track Lengths and Show Unmeasured stay together with no gap, as one subject.
     *
     * MUTATION: leave a heading's gap between two of the checkboxes, and this fails.
     */
    @Test
    public void testTheCheckboxesUnderItAreEvenlySpaced() throws Exception
    {
        java.util.List<String> track = checkboxGaps(false);
        java.util.List<String> autonomy = checkboxGaps(true);

        assertTrue(track.size() >= 2 && autonomy.size() >= 2, "precondition: too few checkboxes found under Visible"
            + " Elements: " + track + ", " + autonomy);

        java.util.Set<String> sizes = new java.util.TreeSet<>();

        for (String gap : track) sizes.add(gap.substring(gap.lastIndexOf(' ') + 1));
        for (String gap : autonomy) sizes.add(gap.substring(gap.lastIndexOf(' ') + 1));

        assertEquals(sizes.size(), 1, "the checkboxes under Visible Elements are not evenly spaced (OB-312) - track"
            + " editor " + track + ", autonomy editor " + autonomy);
    }

    /**
     * The autonomy editor, shrunk as far as it will go in both directions, still shows every control (OB-342): HEIGHT
     * shows Cancel, the last control, and the status line; WIDTH, the status line's minimum must not be its text's (the
     * sentence here is longer than any language's, so the claim does not depend on the railway's words); SCALE, the
     * floor is in pixels and the window is not, so it is asked at 100%, 125% and 150% (`flatlaf.uiScale`), with the
     * look and feel installed again and put back at the end.  Asked at each of the three diagram sizes, of every
     * control outside the scroll panes.
     *
     * MUTATION: take out the floor the window's own layout asks for in `sizeForDiagram`, or give the status line in
     * `AutonomyEditorPanel` its text's width as its minimum again, and this fails.
     */
    @Test
    public void testTheAutonomyEditorShowsEveryControlAtItsSmallestSize() throws Exception
    {
        assertNothingIsCutOffAtTheSmallestSize(true);
    }

    /**
     * And the track diagram editor, which has the same floor: its Cancel button must not be cut off (OB-342).
     *
     * MUTATION: put `EXTRA_MINIMUM_HEIGHT` back to 50, or take out the floor the window's own layout asks for in
     * `sizeForDiagram`, and this fails.
     */
    @Test
    public void testTheTrackEditorShowsEveryControlAtItsSmallestSize() throws Exception
    {
        assertNothingIsCutOffAtTheSmallestSize(false);
    }

    /**
     * The notices float over the bottom of the diagram, across its visible width, and the diagram never moves for them:
     * not with none, nor when one of one, two or six lines arrives, nor when it is dismissed (OB-366).  The band lies
     * inside the diagram's viewport on its bottom edge, and is what a point in it finds first.
     *
     * MUTATION: mount the notices under the scroll pane again (`foot.add(autonomyBanner, BorderLayout.NORTH)` in
     * `LayoutEditor.setAutonomyMode`) and this fails.
     */
    @Test
    public void testANoticeFloatsOverTheDiagramAndNeverMovesIt() throws Exception
    {
        final LayoutEditor editor = autonomyEditor();

        try
        {
            final org.traincontrol.gui.autonomy.AutonomyBanner notices = notices(editor);
            final javax.swing.JViewport diagram = diagram(editor);

            final java.awt.Rectangle before = onScreen(diagram);

            assertTrue(before.height > 0, "precondition: the diagram is not on screen: " + before);

            assertFalse(showing(notices), "with nothing to say, the notices are still showing");

            for (int count : new int[] {1, 2, 6})
            {
                say(editor, notices, lines(count));

                java.awt.Rectangle after = onScreen(diagram);

                assertEquals(after, before, "a notice of " + count + " line(s) moved or resized the diagram, from "
                    + before + " to " + after + " (OB-366, \"2-line notices shift the track down\")");

                assertTrue(showing(notices), "a notice of " + count + " line(s) is not showing");

                java.awt.Rectangle band = onScreen(notices);

                assertTrue(before.contains(band), "a notice of " + count + " line(s) is not inside the diagram's visible"
                    + " area: the band is " + band + " and the diagram " + before + " (OB-366, \"have the band float\")");

                assertEquals(band.y + band.height, before.y + before.height, "a notice of " + count + " line(s) does not"
                    + " sit on the diagram's bottom edge: the band is " + band + " and the diagram " + before);

                assertEquals(band.x, before.x, "the band does not start at the diagram's left edge: " + band + ", "
                    + before);

                assertEquals(band.width, before.width, "the band is not the diagram's visible width: " + band + ", "
                    + before);

                assertTrue(foundFirstIn(editor, notices), "a point in the band finds the diagram rather than the"
                    + " band, so the notice of " + count + " line(s) is under the track rather than over it");
            }

            dismiss(editor, notices);

            assertFalse(showing(notices), "pressing the X left the notice showing (OB-366, \"an x to dismiss\")");

            assertEquals(onScreen(diagram), before, "dismissing a notice moved or resized the diagram");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * A notice longer than four lines scrolls inside a band four lines tall, never sideways, and a shorter one gets a
     * band its own height (OB-366; MT-864), measured in the notice text's font.
     *
     * MUTATION: raise `AutonomyBanner.MOST_LINES` to 6, and this fails.
     */
    @Test
    public void testALongNoticeScrollsWithinFourLines() throws Exception
    {
        final LayoutEditor editor = autonomyEditor();

        try
        {
            final org.traincontrol.gui.autonomy.AutonomyBanner notices = notices(editor);

            say(editor, notices, lines(1));

            final int one = onScreen(notices).height;

            say(editor, notices, lines(6));

            SwingUtilities.invokeAndWait(() ->
            {
                javax.swing.JScrollPane area = scrollerIn(notices);

                assertNotNull(area, "the notices have nothing in them that scrolls");

                java.awt.Component text = area.getViewport().getView();

                int line = text.getFontMetrics(org.traincontrol.gui.autonomy.AutonomyBanner.MESSAGE_FONT).getHeight();

                java.awt.Insets pad = notices.getInsets();

                assertTrue(area.getViewport().getHeight() <= 4 * line, "a six-line notice is shown "
                    + area.getViewport().getHeight() + " pixels tall, more than four lines of " + line + " (MT-864)");

                assertTrue(notices.getHeight() <= 4 * line + pad.top + pad.bottom, "the band is " + notices.getHeight()
                    + " pixels tall for a six-line notice, more than four lines of " + line + " and its padding");

                assertTrue(area.getVerticalScrollBar().isShowing(), "a six-line notice in a band four lines tall has"
                    + " no scrollbar, so the rest of it cannot be reached (OB-366)");

                assertFalse(area.getHorizontalScrollBar().isShowing(), "the notices scroll sideways; they wrap");

                assertTrue(one < notices.getHeight(), "a one-line notice's band is " + one + " pixels tall and a"
                    + " six-line one's " + notices.getHeight() + ": the band does not fit what it says");
            });

            say(editor, notices, lines(4));

            SwingUtilities.invokeAndWait(() ->
            {
                javax.swing.JScrollPane area = scrollerIn(notices);

                assertFalse(area.getVerticalScrollBar().isShowing(), "a four-line notice does not fit the band: "
                    + area.getViewport().getHeight() + " pixels for a text "
                    + area.getViewport().getView().getPreferredSize().height + " tall (MT-864, \"4 lines\")");
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * The X at the band's top right, clicked where it is drawn, hides the notice and leaves the diagram as it was: its
     * place on screen and the part of the railway it shows (OB-366).  The next notice shows the band again, even one
     * saying the same thing, as does a timed one: dismissed is until the next notice.  Tab reaches the X and Enter
     * presses it, but a click never takes the editor's keyboard (OB-200).
     *
     * MUTATION: make the X do nothing (an empty action in `AutonomyBanner.floatOver`), or never clear `dismissed` when
     * a notice arrives (in `AutonomyBanner.hold`), and this fails.
     */
    @Test
    public void testTheXHidesTheNoticeUntilTheNextOne() throws Exception
    {
        final LayoutEditor editor = autonomyEditor();

        try
        {
            final org.traincontrol.gui.autonomy.AutonomyBanner notices = notices(editor);
            final javax.swing.JViewport diagram = diagram(editor);

            say(editor, notices, lines(4));

            final java.awt.Rectangle place = onScreen(diagram);
            final java.awt.Point shown = viewPosition(diagram);

            assertTrue(showing(notices), "precondition: the notice is not showing, so there is nothing to dismiss");

            dismiss(editor, notices);

            assertFalse(showing(notices), "pressing the X left the notice showing (OB-366, \"an x to dismiss\")");

            assertEquals(onScreen(diagram), place, "pressing the X moved or resized the diagram");
            assertEquals(viewPosition(diagram), shown, "pressing the X scrolled the diagram");
            assertFalse(clickedASquare(editor), "pressing the X clicked the square under it too");

            say(editor, notices, lines(4));

            assertTrue(showing(notices), "the same notice said again after a dismiss stays hidden, and dismissing is until"
                + " the next notice (OB-366, \"each time it surfaces\")");

            // AND BY KEYBOARD: Tab reaches the X and Enter presses it, and a click never takes the window's keyboard
            final String tip = org.traincontrol.util.I18n.t("autosetup.ui.tooltipDismissNotice");

            SwingUtilities.invokeAndWait(() ->
            {
                AbstractButton x = buttonWithTip(notices, tip);

                assertTrue(x.isFocusable(), "the X cannot be reached with Tab");
                assertFalse(x.isRequestFocusEnabled(), "a click on the X takes the keyboard from the editor (OB-200)");

                x.dispatchEvent(new java.awt.event.KeyEvent(x, java.awt.event.KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_ENTER, '\n'));
            });

            settle(editor);

            assertFalse(showing(notices), "Enter on the X left the notice showing");

            SwingUtilities.invokeAndWait(() -> notices.show(lines(2)));

            settle(editor);

            assertTrue(showing(notices), "a timed notice after a dismiss stays hidden (OB-366, \"each time it surfaces\")");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * A click on the band stays on the band: on its padding, on its text or on its scrollbar, the square under it is
     * not clicked.  And a click on the diagram away from the band still reaches the square there.
     *
     * MUTATION: take the band's own mouse listener out of `AutonomyBanner.floatOver`, and this fails on the padding.
     */
    @Test
    public void testAClickOnTheBandNeverReachesTheDiagram() throws Exception
    {
        final LayoutEditor editor = autonomyEditor();

        try
        {
            final org.traincontrol.gui.autonomy.AutonomyBanner notices = notices(editor);
            final javax.swing.JViewport diagram = diagram(editor);

            say(editor, notices, lines(6));

            final java.awt.Rectangle band = new java.awt.Rectangle();

            SwingUtilities.invokeAndWait(() -> band.setBounds(SwingUtilities.convertRectangle(notices.getParent(),
                notices.getBounds(), diagram)));

            final javax.swing.JScrollPane area = scrollerIn(notices);

            final java.awt.Rectangle bar = new java.awt.Rectangle();

            SwingUtilities.invokeAndWait(() -> bar.setBounds(SwingUtilities.convertRectangle(area,
                area.getVerticalScrollBar().getBounds(), diagram)));

            java.util.Map<String, java.awt.Point> onTheBand = new java.util.LinkedHashMap<>();

            // Each over a square, so a click that went through would reach one
            onTheBand.put("its padding", squareUnder(diagram, band, band.y + band.height - 2));
            onTheBand.put("its text", squareUnder(diagram, band, band.y + band.height / 2));
            onTheBand.put("its scrollbar", new java.awt.Point(bar.x + bar.width / 2, bar.y + bar.height / 2));

            for (java.util.Map.Entry<String, java.awt.Point> each : onTheBand.entrySet())
            {
                assertNotNull(each.getValue(), "precondition: no square lies under the band's " + each.getKey());

                click(editor, diagram, each.getValue());

                assertFalse(clickedASquare(editor), "a click on the band's " + each.getKey() + " at " + each.getValue()
                    + " clicked the square under the band");

                assertTrue(showing(notices), "precondition: the click on " + each.getKey() + " closed the notice");
            }

            final java.awt.Point away = blankSquareAbove(diagram, band.y);

            assertNotNull(away, "precondition: the diagram shows no empty square above the band to click");

            click(editor, diagram, away);

            assertTrue(clickedASquare(editor), "a click on the diagram away from the band at " + away + " did not reach"
                + " the square there");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * Scrolled to the bottom with a notice showing, the diagram's last row lies above the notice, not under it: the
     * diagram gains the notice's height below while the notice shows, and loses it when it goes (OB-389).
     *
     * MUTATION: leave the notice's height out of `LayoutEditor.sizeTheDiagram`, and this fails.
     */
    @Test
    public void testTheLastRowScrollsClearOfANotice() throws Exception
    {
        final LayoutEditor editor = autonomyEditor();

        try
        {
            final org.traincontrol.gui.autonomy.AutonomyBanner notices = notices(editor);
            final javax.swing.JViewport diagram = diagram(editor);
            final org.traincontrol.gui.LayoutGrid grid = (org.traincontrol.gui.LayoutGrid) field(editor, "grid");

            final int[] quiet = new int[1];

            SwingUtilities.invokeAndWait(() -> quiet[0] = diagram.getView().getPreferredSize().height);

            say(editor, notices, lines(3));

            SwingUtilities.invokeAndWait(() -> diagram.setViewPosition(new java.awt.Point(diagram.getViewPosition().x,
                Math.max(0, diagram.getViewSize().height - diagram.getExtentSize().height))));

            settle(editor);

            java.awt.Rectangle band = onScreen(notices);
            java.awt.Rectangle rows = onScreen(grid.getContainer());

            int lastRow = rows.y + grid.maxHeight;

            java.awt.Rectangle shown = onScreen(diagram);

            assertTrue(lastRow > shown.y && lastRow <= shown.y + shown.height, "precondition: scrolled to the bottom,"
                + " the diagram's last row is not on screen; the rows end at " + lastRow + ", the diagram shows " + shown
                + ", the rows are " + rows);

            assertTrue(lastRow <= band.y, "scrolled to the bottom, the diagram's last row ends at " + lastRow
                + ", under the notice, which starts at " + band.y + " (OB-389)");

            dismiss(editor, notices);
            settle(editor);

            final int[] after = new int[1];

            SwingUtilities.invokeAndWait(() -> after[0] = diagram.getView().getPreferredSize().height);

            assertEquals(after[0], quiet[0], "the diagram kept the notice's height after the notice went (OB-389)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * An autonomy editor on a 768-pixel screen (720 pixels of work area, under Windows' 48-pixel taskbar) is no taller
     * than the work area, and Save Changes and Cancel lie wholly inside its content, as it opens and shrunk to its
     * smallest size (OB-399; Adam: "ensure the autonomy editor window has a min height to prevent the cancel button from
     * being hidden").  The smallest size is the column's own height, so it fits the screen rather than being cut to it.
     *
     * MUTATION: put the 630-pixel floor back for the autonomy editor in `LayoutEditor.sizeForDiagram`, or the 640 and
     * 240 pixels in `AutonomyEditorPanel.setColumnWidth`, and this fails.
     */
    @Test
    public void testCancelStaysInsideTheWindowOnA768PixelScreen() throws Exception
    {
        final java.awt.Rectangle workArea = new java.awt.Rectangle(0, 0, 1366, 720);

        final OnAScreen editor = autonomyEditorOn(oneScreen(workArea));

        try
        {
            settle(editor);

            assertTrue(editor.getHeight() <= workArea.height, "the editor opened " + editor.getHeight() + " pixels tall on a"
                + " work area " + workArea.height + " tall (OB-399)");

            assertTrue(editor.getMinimumSize().height <= workArea.height, "the editor's smallest height is "
                + editor.getMinimumSize().height + ", more than the work area's " + workArea.height);

            assertEquals(outsideTheContent(editor), "", "as it opened, at " + editor.getSize() + " (OB-399)");

            SwingUtilities.invokeAndWait(() -> editor.setSize(editor.getMinimumSize()));

            settle(editor);

            assertEquals(outsideTheContent(editor), "", "at its smallest size, " + editor.getSize() + " (OB-399)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * The findings under the diagram show 7 rows where the window has room and 5 where it is short, and change as the
     * window is resized: at its smallest size, just under the height that gives up two rows' worth to the list, at
     * that height, and tall, then short again (OB-399; Adam: "Things to look at can be fewer lines (5 instead of 7) on
     * smaller screens, resizing itself").  The list scrolls past its rows either way.
     *
     * MUTATION: pin the list at one height (`list.setPreferredSize(new java.awt.Dimension(100, 190))` in
     * `LayoutEditor.setAutonomyMode`, no `sizeTheFindings`), or make `FINDINGS_ROWS_SHORT` 7, and this fails.
     */
    @Test
    public void testTheFindingsShowSevenRowsWhenTallAndFiveWhenShort() throws Exception
    {
        final OnAScreen editor = autonomyEditorOn(null);

        try
        {
            settle(editor);

            final javax.swing.JScrollPane scroll = editor.getAutonomyPanel().getFindingsPanel();
            final javax.swing.JList<?> list = (javax.swing.JList<?>) scroll.getViewport().getView();

            assertTrue(list.getModel().getSize() > 7, "precondition: the sandbox's setup has " + list.getModel().getSize()
                + " findings, too few to fill 7 rows");

            final int chrome = editor.getHeight() - editor.getContentPane().getHeight();
            final int[] least = new int[1];
            final int[] extra = new int[1];

            SwingUtilities.invokeAndWait(() ->
            {
                least[0] = editor.getContentPane().getMinimumSize().height;
                extra[0] = 2 * list.getCellBounds(0, 0).height;
            });

            assertEquals(resizedTo(editor, least[0] + chrome, list), 5, "rows shown at the smallest height");

            assertEquals(resizedTo(editor, least[0] + extra[0] - 1 + chrome, list), 5,
                "rows shown a pixel under the height that gives the list two more rows");

            assertEquals(resizedTo(editor, least[0] + extra[0] + chrome, list), 7,
                "rows shown at the height that gives the list two more rows");

            assertEquals(resizedTo(editor, least[0] + extra[0] + 300 + chrome, list), 7, "rows shown when tall");

            assertEquals(resizedTo(editor, least[0] + chrome, list), 5, "rows shown after shrinking again");

            assertTrue(scroll.getVerticalScrollBar().isShowing(), "the list has more findings than rows and no way to"
                + " scroll to them");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> editor.dispose());
        }
    }

    /**
     * A remembered window bigger than the screen's work area (set on a larger screen) opens no taller than the work
     * area (OB-399).  The window's remembered entry is written, then removed.
     *
     * MUTATION: take `keepTheAutonomyEditorOnTheScreen` out of `LayoutEditor.render`, and this fails.
     */
    @Test
    public void testARememberedSizeTallerThanTheScreenIsBroughtDown() throws Exception
    {
        final java.awt.Rectangle workArea = new java.awt.Rectangle(0, 0, 1366, 720);

        java.awt.Rectangle opened = openedFromMemory(new java.awt.Rectangle(0, 0, 1300, 1100), 0, oneScreen(workArea), false);

        assertTrue(opened.height <= workArea.height, "a window remembered 1100 pixels tall opened "
            + opened.height + " pixels tall on a work area " + workArea.height + " tall (OB-399)");
    }

    /**
     * A remembered window is measured against the screen it opens on, the one its bounds overlap most, with that
     * screen's taskbar, and the default screen when it overlaps none: on a taller second screen it keeps its height,
     * on a shorter one it is brought down and into it (OB-399).  The default screen here is a short one at the left.
     *
     * MUTATION: measure the screen the window was built on (`getGraphicsConfiguration()`) in
     * `PositionAwareJFrame.usableScreen`, and this fails.
     */
    @Test
    public void testARememberedWindowIsMeasuredOnItsOwnScreen() throws Exception
    {
        final java.awt.Insets taskbar = new java.awt.Insets(0, 0, 48, 0);
        final java.awt.Rectangle remembered = new java.awt.Rectangle(100, 0, 900, 1000);
        final java.awt.Rectangle shortDefault = new java.awt.Rectangle(-1366, 0, 1366, 768);
        final StringBuilder wrong = new StringBuilder();

        java.util.Map<java.awt.Rectangle, java.awt.Insets> screens = new java.util.LinkedHashMap<>();
        screens.put(shortDefault, taskbar);
        screens.put(new java.awt.Rectangle(0, 0, 1920, 1248), taskbar);

        java.awt.Rectangle opened = openedFromMemory(remembered, 0, screens, false);

        if (opened.height != 1000) wrong.append("on a taller second screen: ").append(opened).append("; ");

        screens.remove(new java.awt.Rectangle(0, 0, 1920, 1248));
        screens.put(new java.awt.Rectangle(0, 100, 1920, 768), taskbar);

        opened = openedFromMemory(remembered, 0, screens, false);

        if (opened.height != 720 || opened.y != 100) wrong.append("on a shorter second screen at y=100: ").append(opened)
            .append("; ");

        screens.remove(new java.awt.Rectangle(0, 100, 1920, 768));
        screens.put(new java.awt.Rectangle(3000, 0, 1920, 1248), taskbar);

        opened = openedFromMemory(remembered, 0, screens, false);

        if (opened.height != 720 || opened.y != 0) wrong.append("on no screen, so the default: ").append(opened);

        assertEquals(wrong.toString(), "", "a window remembered at " + remembered + ", opened where its work area is not"
            + " the one it was measured against");
    }

    /**
     * A window remembered maximized keeps its remembered size under the maximize, as a maximized one does: brought back
     * to normal, it has that size, not the work area's height (OB-399).
     *
     * MUTATION: ask only `getExtendedState()` for the maximize in `LayoutEditor.keepTheAutonomyEditorOnTheScreen`, and
     * this fails.
     */
    @Test
    public void testARememberedMaximizedWindowKeepsItsSizeUnderneath() throws Exception
    {
        java.awt.Rectangle restored = openedFromMemory(new java.awt.Rectangle(0, 0, 1300, 900),
            java.awt.Frame.MAXIMIZED_BOTH, oneScreen(new java.awt.Rectangle(0, 0, 1366, 720)), true);

        assertEquals(restored.height, 900, "a window remembered maximized over a size 900 pixels tall, on a work area"
            + " 720 tall, came back to " + restored);
    }

    /**
     * The bounds of an autonomy editor opened where it is remembered at these bounds and state, on these screens (null
     * for the machine's), and, with `unmaximized`, then brought back to normal.  The window's remembered entry is
     * written, then removed.
     */
    private static java.awt.Rectangle openedFromMemory(final java.awt.Rectangle remembered, final int state,
        final java.util.Map<java.awt.Rectangle, java.awt.Insets> screens, final boolean unmaximized) throws Exception
    {
        final java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();
        final String entry = OnAScreen.class.getSimpleName() + "_editor_";
        final String rememberKey = org.traincontrol.gui.widgets.PositionAwareJFrame.REMEMBER_WINDOW_LOCATION;
        final String rememberWas = prefs.get(rememberKey, null);
        final java.awt.Rectangle[] opened = new java.awt.Rectangle[1];

        OnAScreen editor = null;

        try
        {
            prefs.putInt(entry + "x", remembered.x);
            prefs.putInt(entry + "y", remembered.y);
            prefs.putInt(entry + "width", remembered.width);
            prefs.putInt(entry + "height", remembered.height);
            prefs.putInt(entry + "state", state);
            prefs.putBoolean(rememberKey, true);

            editor = autonomyEditorOn(screens);

            settle(editor);

            final OnAScreen shown = editor;

            if (unmaximized)
            {
                assertTrue((shown.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0, "precondition: the window"
                    + " remembered maximized opened " + shown.getBounds() + ", not maximized");

                SwingUtilities.invokeAndWait(() -> shown.setExtendedState(java.awt.Frame.NORMAL));

                settle(editor);
            }

            SwingUtilities.invokeAndWait(() -> opened[0] = shown.getBounds());
        }
        finally
        {
            final OnAScreen closing = editor;

            if (closing != null) SwingUtilities.invokeAndWait(() -> closing.dispose());

            for (String each : new String[] {"x", "y", "width", "height", "state"}) prefs.remove(entry + each);

            if (rememberWas == null) prefs.remove(rememberKey);
            else prefs.put(rememberKey, rememberWas);
        }

        return opened[0];
    }

    /** An editor on screens of the test's own, the default first, to stand for screens the machine does not have. */
    public static class OnAScreen extends LayoutEditor
    {
        private final java.util.Map<java.awt.Rectangle, java.awt.Insets> screens;

        OnAScreen(LayoutDiagram page, TrainControlUI ui, java.util.Map<java.awt.Rectangle, java.awt.Insets> screens)
        {
            super(page, 30, ui, 0);

            this.screens = screens;
        }

        @Override
        protected java.util.Map<java.awt.Rectangle, java.awt.Insets> screens()
        {
            return screens == null ? super.screens() : screens;
        }
    }

    /** One screen with this work area and no taskbar. */
    private static java.util.Map<java.awt.Rectangle, java.awt.Insets> oneScreen(java.awt.Rectangle workArea)
    {
        return java.util.Collections.singletonMap(workArea, new java.awt.Insets(0, 0, 0, 0));
    }

    /** An autonomy editor on the screens given (null for the machine's own), opened. */
    private static OnAScreen autonomyEditorOn(final java.util.Map<java.awt.Rectangle, java.awt.Insets> screens)
        throws Exception
    {
        final OnAScreen[] built = new OnAScreen[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new OnAScreen(page, ui, screens);
            built[0].render();
            built[0].setAutonomyMode(session);
        });

        for (int pass = 0; pass < 10; pass++) SwingUtilities.invokeAndWait(() -> { });

        assertTrue(built[0].isShowing(), "precondition: the autonomy editor did not open");

        return built[0];
    }

    /** Which of Save Changes and Cancel lie even partly outside the window's content, or "" for neither. */
    private static String outsideTheContent(final LayoutEditor editor) throws Exception
    {
        final StringBuilder out = new StringBuilder();

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                java.awt.Rectangle content = new java.awt.Rectangle(editor.getContentPane().getSize());

                for (String name : new String[] {"saveButton", "cancelButton"})
                {
                    Component button = (Component) field(editor, name);

                    java.awt.Rectangle at = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(),
                        editor.getContentPane());

                    if (!content.contains(at)) out.append(name).append(' ').append(at).append(" outside the content ")
                        .append(content).append("; ");
                }
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        return out.toString();
    }

    /** How many findings the list shows once the window is this tall, in rows. */
    private static int resizedTo(final LayoutEditor editor, final int height, final javax.swing.JList<?> list)
    throws Exception
    {
        final int[] rows = new int[1];

        SwingUtilities.invokeAndWait(() -> editor.setSize(editor.getWidth(), height));

        settle(editor);

        SwingUtilities.invokeAndWait(() -> rows[0] = list.getLastVisibleIndex() - list.getFirstVisibleIndex() + 1);

        return rows[0];
    }

    /** An autonomy editor, on screen and settled. */
    private static LayoutEditor autonomyEditor() throws Exception
    {
        return autonomyEditorOn(null);
    }

    private static org.traincontrol.gui.autonomy.AutonomyBanner notices(LayoutEditor editor) throws ReflectiveOperationException
    {
        return (org.traincontrol.gui.autonomy.AutonomyBanner) field(editor, "autonomyBanner");
    }

    /** What the diagram shows on screen: its scroll pane's viewport. */
    private static javax.swing.JViewport diagram(LayoutEditor editor) throws ReflectiveOperationException
    {
        return ((javax.swing.JScrollPane) field(editor, "jScrollPane1")).getViewport();
    }

    /** Says a notice that stays, and lets the window lay out what it now holds. */
    private static void say(final LayoutEditor editor, final org.traincontrol.gui.autonomy.AutonomyBanner notices,
        final String notice) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> notices.showUntilChanged(notice));

        settle(editor);
    }

    private static void settle(final LayoutEditor editor) throws Exception
    {
        for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });

        SwingUtilities.invokeAndWait(() -> editor.validate());

        for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });
    }

    /** Presses the band's X where it is drawn, found by its tooltip, as a pointer would. */
    private static void dismiss(final LayoutEditor editor, final org.traincontrol.gui.autonomy.AutonomyBanner notices)
        throws Exception
    {
        final String tip = org.traincontrol.util.I18n.t("autosetup.ui.tooltipDismissNotice");

        final AbstractButton[] x = new AbstractButton[1];

        SwingUtilities.invokeAndWait(() -> x[0] = buttonWithTip(notices, tip));

        assertNotNull(x[0], "the notices have no button with the tooltip \"" + tip + "\" to dismiss them (OB-366, \"an x"
            + " to dismiss\")");

        assertTrue(x[0].isShowing(), "the notices' X is not showing");

        click(editor, x[0], new java.awt.Point(x[0].getWidth() / 2, x[0].getHeight() / 2));
    }

    private static AbstractButton buttonWithTip(Container in, String tip)
    {
        for (Component child : in.getComponents())
        {
            if (child instanceof AbstractButton && tip.equals(((AbstractButton) child).getToolTipText()))
            {
                return (AbstractButton) child;
            }

            if (child instanceof Container)
            {
                AbstractButton deeper = buttonWithTip((Container) child, tip);

                if (deeper != null) return deeper;
            }
        }

        return null;
    }

    /**
     * A left click at a point of a component, handed to the window at the window's own coordinates, so Swing finds the
     * component under the pointer as it does for a real one, without moving the real pointer.
     */
    private static void click(final LayoutEditor editor, final Component on, final java.awt.Point at) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            java.awt.Point p = SwingUtilities.convertPoint(on, at, editor);

            long now = System.currentTimeMillis();

            editor.dispatchEvent(new java.awt.event.MouseEvent(editor, java.awt.event.MouseEvent.MOUSE_MOVED, now, 0,
                p.x, p.y, 0, false, java.awt.event.MouseEvent.NOBUTTON));
            editor.dispatchEvent(new java.awt.event.MouseEvent(editor, java.awt.event.MouseEvent.MOUSE_PRESSED, now,
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK, p.x, p.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
            editor.dispatchEvent(new java.awt.event.MouseEvent(editor, java.awt.event.MouseEvent.MOUSE_RELEASED, now, 0,
                p.x, p.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
            editor.dispatchEvent(new java.awt.event.MouseEvent(editor, java.awt.event.MouseEvent.MOUSE_CLICKED, now, 0,
                p.x, p.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        });

        settle(editor);
    }

    /** Whether a square of this editor has been clicked since it opened (the editor's own record of it). */
    private static boolean clickedASquare(LayoutEditor editor) throws ReflectiveOperationException
    {
        return (Boolean) field(editor, "squareClicked");
    }

    /** The middle of an empty square showing in the diagram wholly above a line, in the viewport's coordinates. */
    private static java.awt.Point blankSquareAbove(final javax.swing.JViewport diagram, final int line) throws Exception
    {
        final java.awt.Point[] out = new java.awt.Point[1];

        SwingUtilities.invokeAndWait(() -> out[0] = blankSquareIn((Container) diagram.getView(), diagram, line));

        return out[0];
    }

    private static java.awt.Point blankSquareIn(Container in, javax.swing.JViewport diagram, int line)
    {
        for (Component child : in.getComponents())
        {
            if (child instanceof org.traincontrol.gui.LayoutLabel && child.isShowing()
                && ((org.traincontrol.gui.LayoutLabel) child).getComponent() == null)
            {
                java.awt.Rectangle square = SwingUtilities.convertRectangle(in, child.getBounds(), diagram);

                if (square.y >= 0 && square.x >= 0 && square.x + square.width <= diagram.getWidth()
                    && square.y + square.height < line)
                {
                    return new java.awt.Point(square.x + square.width / 2, square.y + square.height / 2);
                }
            }
            else if (child instanceof Container)
            {
                java.awt.Point deeper = blankSquareIn((Container) child, diagram, line);

                if (deeper != null) return deeper;
            }
        }

        return null;
    }

    /** A point on a line across the band, in the viewport's coordinates, with a square of the diagram under it. */
    private static java.awt.Point squareUnder(final javax.swing.JViewport diagram, final java.awt.Rectangle band,
        final int y) throws Exception
    {
        final java.awt.Point[] out = new java.awt.Point[1];

        SwingUtilities.invokeAndWait(() ->
        {
            for (int x = band.x + 12; x < band.x + band.width / 2 && out[0] == null; x += 5)
            {
                java.awt.Point in = SwingUtilities.convertPoint(diagram, x, y, diagram.getView());

                Component under = SwingUtilities.getDeepestComponentAt(diagram.getView(), in.x, in.y);

                if (under instanceof org.traincontrol.gui.LayoutLabel) out[0] = new java.awt.Point(x, y);
            }
        });

        return out[0];
    }

    /** Whether the middle of the band, asked of the window, finds the band before anything under it. */
    private static boolean foundFirstIn(final LayoutEditor editor, final Component band) throws Exception
    {
        final boolean[] out = new boolean[1];

        SwingUtilities.invokeAndWait(() ->
        {
            java.awt.Container content = editor.getContentPane();

            java.awt.Point middle = SwingUtilities.convertPoint(band, band.getWidth() / 2, band.getHeight() / 2, content);

            Component hit = SwingUtilities.getDeepestComponentAt(content, middle.x, middle.y);

            out[0] = hit != null && SwingUtilities.isDescendingFrom(hit, band);
        });

        return out[0];
    }

    private static boolean showing(final Component component) throws Exception
    {
        final boolean[] out = new boolean[1];

        SwingUtilities.invokeAndWait(() -> out[0] = component.isShowing() && component.getHeight() > 0);

        return out[0];
    }

    private static java.awt.Point viewPosition(final javax.swing.JViewport diagram) throws Exception
    {
        final java.awt.Point[] out = new java.awt.Point[1];

        SwingUtilities.invokeAndWait(() -> out[0] = diagram.getViewPosition());

        return out[0];
    }

    /** A notice of this many short lines. */
    private static String lines(int count)
    {
        StringBuilder out = new StringBuilder("Line 1 of the notice");

        for (int at = 2; at <= count; at++) out.append("<br>Line ").append(at).append(" of the notice");

        return out.toString();
    }

    private static java.awt.Rectangle onScreen(final Component component) throws Exception
    {
        final java.awt.Rectangle[] out = new java.awt.Rectangle[1];

        SwingUtilities.invokeAndWait(() -> out[0] = new java.awt.Rectangle(component.getLocationOnScreen(),
            component.getSize()));

        return out[0];
    }

    private static javax.swing.JScrollPane scrollerIn(Container in)
    {
        return support.Components.inside(in, javax.swing.JScrollPane.class);
    }

    /** A status sentence longer than any language's: 521 pixels is English and 655 German, and this is over 900 */
    private static final String A_STATUS_LONGER_THAN_ANY_LANGUAGES = "This setup will run, with warnings.  13 of 33 stations"
        + " can be chosen in full autonomy, and the others are held back for reasons that the findings above give one at a time.";

    private static void assertNothingIsCutOffAtTheSmallestSize(boolean autonomy) throws Exception
    {
        int examined = 0;

        String was = System.getProperty("flatlaf.uiScale");

        try
        {
            // 100%, 125% and 150% - where the floor, in pixels, was short (OB-342)
            for (String scale : new String[] {"1.0", "1.25", "1.5"})
            {
                SwingUtilities.invokeAndWait(() ->
                {
                    System.setProperty("flatlaf.uiScale", scale);

                    com.formdev.flatlaf.FlatLightLaf.setup();
                });

                assertEquals(com.formdev.flatlaf.util.UIScale.getUserScaleFactor(), Float.parseFloat(scale), 0.001f,
                    "precondition: the interface did not take the scale " + scale + ", so nothing is asked of it");

                for (int size : new int[] {30, 46, 60})
                {
                    int[] floor = new int[2];

                    java.util.List<String> cut = cutOffAtTheSmallestSize(autonomy, size, floor);

                    // A floor the screen holds down cannot be raised, and says nothing about the floor
                    if (cut == null) continue;

                    examined++;

                    assertTrue(cut.isEmpty(), "at its smallest size - " + floor[0] + " by " + floor[1] + " pixels, at diagram"
                        + " size " + size + " and an interface scaled to " + scale + " - the " + (autonomy ? "autonomy"
                        : "track diagram") + " editor cuts off controls, and nothing in the window can bring them back (OB-342"
                        + (autonomy ? ", \"the min height of the autonomy editor window needs to be a few more pixels taller\""
                        : ", the same floor") + "): " + cut);
                }
            }
        }
        finally
        {
            // The scale the run began with, for the claims after this one
            SwingUtilities.invokeAndWait(() ->
            {
                if (was == null) System.clearProperty("flatlaf.uiScale");
                else System.setProperty("flatlaf.uiScale", was);

                com.formdev.flatlaf.FlatLightLaf.setup();
            });
        }

        if (examined == 0) throw new SkipException("the screen is too small for the editor's smallest size at any diagram size");
    }

    /** The editor of the page at a diagram size, rendered and, when asked, in autonomy mode, built on the event thread. */
    private static LayoutEditor builtEditor(final boolean autonomy, final int size) throws Exception
    {
        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, size, ui, 0);
            built[0].render();
            if (autonomy) built[0].setAutonomyMode(session);
        });

        return built[0];
    }

    /**
     * What an editor's window cuts off of its controls once it is shrunk to its smallest size, each as "Cancel: 13 of 23
     * pixels high"; null where the screen, not the editor's own floor, is what held the floor down.
     *
     * @param floor receives the smallest width and height the window allowed
     */
    private static java.util.List<String> cutOffAtTheSmallestSize(final boolean autonomy, final int size, final int[] floor)
    throws Exception
    {
        final LayoutEditor built = builtEditor(autonomy, size);
        final java.util.List<String> out = new java.util.ArrayList<>();
        final boolean[] heldDown = new boolean[1];

        try
        {
            for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    LayoutEditor editor = built;

                    // The words are the railway's and the language's; the window is asked about the longest there could be
                    if (autonomy) editor.getAutonomyPanel().getBanner().setText(A_STATUS_LONGER_THAN_ANY_LANGUAGES);

                    java.awt.GraphicsConfiguration screen = editor.getGraphicsConfiguration();

                    java.awt.Insets taskbar = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(screen);

                    floor[0] = editor.getMinimumSize().width;
                    floor[1] = editor.getMinimumSize().height;

                    heldDown[0] = floor[0] >= screen.getBounds().width - taskbar.left - taskbar.right
                        || floor[1] >= screen.getBounds().height - taskbar.top - taskbar.bottom;

                    editor.setSize(editor.getMinimumSize());

                    editor.validate();

                    if (!((javax.swing.JButton) field(editor, "cancelButton")).isShowing())
                    {
                        out.add("precondition: Cancel is not showing, so nothing is asked of the controls");
                    }

                    cutOff(editor.getRootPane(), out);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> built.dispose());
        }

        return heldDown[0] ? null : out;
    }

    /**
     * Every control showing under a container, outside the scroll panes, that its ancestors clip - the window's edge among
     * them - with how much of it is missing.
     */
    private static void cutOff(Container in, java.util.List<String> out)
    {
        for (Component child : in.getComponents())
        {
            if (!child.isShowing()) continue;

            String text = child instanceof AbstractButton ? ((AbstractButton) child).getText()
                : child instanceof JLabel ? ((JLabel) child).getText() : null;

            boolean control = child instanceof AbstractButton || child instanceof JComboBox
                || (child instanceof JLabel && text != null && !text.isEmpty());

            if (control && child instanceof javax.swing.JComponent)
            {
                javax.swing.JComponent each = (javax.swing.JComponent) child;

                java.awt.Rectangle shown = each.getVisibleRect();

                int high = each.getHeight() - shown.height;
                int wide = each.getWidth() - shown.width;

                if (high > 0 || wide > 0)
                {
                    out.add(child.getClass().getSimpleName() + (text == null ? "" : " '"
                        + (text.length() > 30 ? text.substring(0, 30) + "..." : text) + "'") + ": "
                        + (high > 0 ? high + " of " + each.getHeight() + " pixels high" : "")
                        + (high > 0 && wide > 0 ? ", " : "")
                        + (wide > 0 ? wide + " of " + each.getWidth() + " pixels wide" : ""));
                }
            }

            // The controls inside a control are its own, and a scroll pane has a way to reach what it holds
            if (child instanceof Container && !(child instanceof AbstractButton) && !(child instanceof JComboBox)
                && !(child instanceof javax.swing.JScrollPane))
            {
                cutOff((Container) child, out);
            }
        }
    }

    /**
     * The gaps between one checkbox and the next under Visible Elements, top to bottom, in one editor: "A - B: n".  Not
     * the pair Track Lengths and Show Unmeasured, which are one subject.
     */
    private static java.util.List<String> checkboxGaps(final boolean autonomy) throws Exception
    {
        final LayoutEditor built = builtEditor(autonomy, 30);
        final java.util.List<String> out = new java.util.ArrayList<>();

        try
        {
            for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    Container pane = (Container) field(built, "formPane");
                    JLabel heading = (JLabel) field(built, "toggleVisibility");
                    Point at = SwingUtilities.convertPoint(heading.getParent(), heading.getLocation(), pane);

                    java.util.List<javax.swing.JCheckBox> boxes = new java.util.ArrayList<>();

                    checkboxes(pane, pane, at.x, at.y + heading.getHeight(), boxes);

                    boxes.sort((a, b) -> Integer.compare(SwingUtilities.convertPoint(a.getParent(), a.getLocation(), pane).y,
                        SwingUtilities.convertPoint(b.getParent(), b.getLocation(), pane).y));

                    javax.swing.JCheckBox unmeasured = autonomy ? built.getAutonomyPanel().getShowUnmeasured() : null;

                    for (int i = 1; i < boxes.size(); i++)
                    {
                        javax.swing.JCheckBox above = boxes.get(i - 1);
                        javax.swing.JCheckBox below = boxes.get(i);

                        int gap = SwingUtilities.convertPoint(below.getParent(), below.getLocation(), pane).y
                            - (SwingUtilities.convertPoint(above.getParent(), above.getLocation(), pane).y + above.getHeight());

                        // The column ends where the next control is far below it
                        if (gap > 30) break;

                        if (below == unmeasured) continue;

                        out.add(above.getText() + " - " + below.getText() + ": " + gap);
                    }
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> built.dispose());
        }

        return out;
    }

    private static void checkboxes(Container root, Container c, int x, int below, java.util.List<javax.swing.JCheckBox> out)
    {
        for (Component child : c.getComponents())
        {
            if (!child.isShowing()) continue;

            Point at = SwingUtilities.convertPoint(child.getParent(), child.getLocation(), root);

            if (child instanceof javax.swing.JCheckBox && Math.abs(at.x - x) < 40 && at.y >= below)
            {
                out.add((javax.swing.JCheckBox) child);
            }

            if (child instanceof Container && !(child instanceof javax.swing.JScrollPane))
            {
                checkboxes(root, (Container) child, x, below, out);
            }
        }
    }

    /**
     * The gap under Diagram Size (-1 where it is hidden), and under Visible Elements, in one editor.
     */
    private static int[] gaps(final boolean autonomy) throws Exception
    {
        final LayoutEditor built = builtEditor(autonomy, 30);
        final int[] out = new int[2];

        try
        {
            for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    Container pane = (Container) field(built, "formPane");

                    JLabel size = (JLabel) field(built, "diagramSize");

                    out[0] = size.isShowing() ? gapUnder(pane, size) : -1;
                    out[1] = gapUnder(pane, (JLabel) field(built, "toggleVisibility"));
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> built.dispose());
        }

        assertTrue(out[1] >= 0 && (autonomy || out[0] >= 0), "precondition: nothing was found under one of the"
            + " headings: " + out[0] + ", " + out[1]);

        return out;
    }

    private static Object field(LayoutEditor editor, String name) throws ReflectiveOperationException
    {
        Field field = LayoutEditor.class.getDeclaredField(name);

        field.setAccessible(true);

        return field.get(editor);
    }

    /**
     * How far below a heading the nearest control in its column begins - a box, a list, a button, or a labeled
     * panel's first child - or -1 when there is none.
     */
    private static int gapUnder(Container pane, JLabel heading)
    {
        Point at = SwingUtilities.convertPoint(heading.getParent(), heading.getLocation(), pane);

        int bottom = at.y + heading.getHeight();

        int[] best = { Integer.MAX_VALUE };

        nearest(pane, pane, at.x, bottom, best);

        return best[0] == Integer.MAX_VALUE ? -1 : best[0] - bottom;
    }

    private static void nearest(Container root, Container c, int x, int below, int[] best)
    {
        for (Component child : c.getComponents())
        {
            if (!child.isShowing()) continue;

            Point at = SwingUtilities.convertPoint(child.getParent(), child.getLocation(), root);

            boolean control = child instanceof AbstractButton || child instanceof JComboBox
                || (child instanceof JLabel && ((JLabel) child).getText() != null && !((JLabel) child).getText().isEmpty());

            if (control && Math.abs(at.x - x) < 40 && at.y >= below && at.y < best[0]) best[0] = at.y;

            if (child instanceof Container && !(child instanceof javax.swing.JScrollPane)) nearest(root, (Container) child, x,
                below, best);
        }
    }
}
