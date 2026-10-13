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
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Under Visible Elements, the first control is as far from the heading as under the editor's other headings (OB-289).
 *
 * Adam, 2026-09-24: *"there is slightly too much spacing/padding below "visible elements" in autonomy editor.  make it
 * be consistent with other labels"*.  The first row there is a column of Text Labels, a gap, and Grid (FR-006); the
 * autonomy editor hides Text Labels, which its caption choice replaces (FR-061), and the gap was left standing at the
 * top of the column - twice the space every other heading has under it.
 *
 * In both editors, because the plain one is where the column is right, and it has to stay right.
 *
 * And the column is all there at the window's smallest height (OB-342): the floor under that height is what shows its
 * last control.
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

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the editor is a window, and the window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null) throw new SkipException("the sandbox holds no autonomy setup, so there is no autonomy editor");

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
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * In the track diagram editor the column is right: Visible Elements has the gap Diagram Size has over its buttons.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testInTheTrackEditor() throws Exception
    {
        int[] gaps = gaps(false);

        assertEquals(gaps[1], gaps[0], "in the track diagram editor the first control under Visible Elements is " + gaps[1]
            + " pixels below it, and the first under Diagram Size " + gaps[0]);
    }

    /**
     * And in the autonomy editor - where Adam saw it - Visible Elements has that same gap.
     *
     * @throws Exception from the event thread or reflection
     */
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
     * The checkboxes under Visible Elements are evenly spaced, and spaced alike in both editors (OB-312).
     *
     * Adam, 2026-10-03: *"check spacing between visible elements checkboxes"*.  They were 6, 4 and 6 pixels apart: the
     * columns built beside the form's own checkboxes left the heading's gap between them, where the form leaves 4.  Track
     * Lengths and Show Unmeasured stay together with no gap, as Adam asked on 2026-09-16 - one subject.
     *
     * MUTATION: leave a heading's gap between two of the checkboxes, and this fails.
     *
     * @throws Exception from the event thread or reflection
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
     * The autonomy editor, shrunk as far as it will go, still shows every control (OB-342; Adam, 2026-10-12: *"It looks like
     * the min height of the autonomy editor window needs to be a few more pixels taller"*).
     *
     * At Small, the window would shrink to 680 pixels and the column of tools needs 693 to show Cancel, the last control in
     * it, and the status line under the findings (measured at 100% display scaling).  Asked at each of the three diagram
     * sizes (Small, Medium, Large: the floor grows with the size), of every control outside the scroll panes, as wide as
     * the layout needs so that the question is only about the height.
     *
     * MUTATION: put `EXTRA_MINIMUM_HEIGHT` in `LayoutEditor` back to 50, and this fails.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheAutonomyEditorShowsEveryControlAtItsSmallestHeight() throws Exception
    {
        assertNothingIsCutOffAtTheSmallestHeight(true);
    }

    /**
     * And the track diagram editor, which has the same floor: its Cancel button was cut off by up to 25 pixels (OB-342).
     *
     * MUTATION: as above.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheTrackEditorShowsEveryControlAtItsSmallestHeight() throws Exception
    {
        assertNothingIsCutOffAtTheSmallestHeight(false);
    }

    private static void assertNothingIsCutOffAtTheSmallestHeight(boolean autonomy) throws Exception
    {
        int examined = 0;

        for (int size : new int[] {30, 46, 60})
        {
            int[] floor = new int[1];

            java.util.List<String> cut = cutOffAtTheSmallestHeight(autonomy, size, floor);

            // A floor the screen holds down cannot be raised, and says nothing about the floor
            if (cut == null) continue;

            examined++;

            assertTrue(cut.isEmpty(), "at its smallest height - " + floor[0] + " pixels, at diagram size " + size + " - the "
                + (autonomy ? "autonomy" : "track diagram") + " editor cuts off controls, and nothing in the window can bring"
                + " them back (OB-342" + (autonomy ? ", \"the min height of the autonomy editor window needs to be a few more"
                + " pixels taller\"" : ", the same floor") + "): " + cut);
        }

        if (examined == 0) throw new SkipException("the screen is too short for the editor's smallest height at any size");
    }

    /**
     * What an editor's window cuts off of its controls once it is shrunk to its smallest height, each as "Cancel: 13 of 23
     * pixels"; null where the screen's height, not the editor's own floor, is what held the floor down.
     *
     * @param floor receives the smallest height the window allowed
     */
    private static java.util.List<String> cutOffAtTheSmallestHeight(final boolean autonomy, final int size, final int[] floor)
    throws Exception
    {
        final LayoutEditor[] built = new LayoutEditor[1];
        final java.util.List<String> out = new java.util.ArrayList<>();
        final boolean[] heldDown = new boolean[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, size, ui, 0);
            built[0].render();
            if (autonomy) built[0].setAutonomyMode(session);
        });

        try
        {
            for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    LayoutEditor editor = built[0];

                    java.awt.GraphicsConfiguration screen = editor.getGraphicsConfiguration();

                    java.awt.Insets taskbar = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(screen);

                    floor[0] = editor.getMinimumSize().height;

                    heldDown[0] = floor[0] >= screen.getBounds().height - taskbar.top - taskbar.bottom;

                    // As wide as the layout needs, so that the width is not what cuts a control off
                    editor.setSize(Math.max(editor.getMinimumSize().width, editor.getContentPane().getMinimumSize().width + 40),
                        floor[0]);

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
            SwingUtilities.invokeAndWait(() -> built[0].dispose());
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

                int missing = each.getHeight() - each.getVisibleRect().height;

                if (missing > 0) out.add(child.getClass().getSimpleName() + (text == null ? "" : " '" + text + "'") + ": "
                    + missing + " of " + each.getHeight() + " pixels");
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
        final LayoutEditor[] built = new LayoutEditor[1];
        final java.util.List<String> out = new java.util.ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            if (autonomy) built[0].setAutonomyMode(session);
        });

        try
        {
            for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    Container pane = (Container) field(built[0], "formPane");
                    JLabel heading = (JLabel) field(built[0], "toggleVisibility");
                    Point at = SwingUtilities.convertPoint(heading.getParent(), heading.getLocation(), pane);

                    java.util.List<javax.swing.JCheckBox> boxes = new java.util.ArrayList<>();

                    checkboxes(pane, pane, at.x, at.y + heading.getHeight(), boxes);

                    boxes.sort((a, b) -> Integer.compare(SwingUtilities.convertPoint(a.getParent(), a.getLocation(), pane).y,
                        SwingUtilities.convertPoint(b.getParent(), b.getLocation(), pane).y));

                    javax.swing.JCheckBox unmeasured = autonomy ? built[0].getAutonomyPanel().getShowUnmeasured() : null;

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
            SwingUtilities.invokeAndWait(() -> built[0].dispose());
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
        final LayoutEditor[] built = new LayoutEditor[1];
        final int[] out = new int[2];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);

            built[0].render();

            if (autonomy) built[0].setAutonomyMode(session);
        });

        try
        {
            for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    Container pane = (Container) field(built[0], "formPane");

                    JLabel size = (JLabel) field(built[0], "diagramSize");

                    out[0] = size.isShowing() ? gapUnder(pane, size) : -1;
                    out[1] = gapUnder(pane, (JLabel) field(built[0], "toggleVisibility"));
                }
                catch (ReflectiveOperationException e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> built[0].dispose());
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
     * How far below a heading the nearest control in its column begins - a box, a list, a button, or a labelled
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
