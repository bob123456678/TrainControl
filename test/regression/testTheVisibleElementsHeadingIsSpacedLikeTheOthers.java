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
