package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Cut or Copy while the pointer is not over a square must not arm a paste with nothing in it.  The Control+V branch
 * null-checks the hovered label; Control+X and Control+C did not, so `initCopy` discarded a group already on the
 * clipboard and armed `toolFlag` with no component behind it, and the right-click Paste then reached `new
 * LayoutDiagramComponent(null)` (shown as a dialog with no message, with an undo entry for an edit that never
 * happened).
 *
 * The guard is narrower than the obvious one, and the two controls forced it there: the palette hands over a component
 * with a label whose coordinates are -1 on purpose, and a column drag hands over a real grid label and NO component.
 * What no caller ever means is both at once.  On the frozen railway, never `cs2_sample_layout` (OB-111).
 *
 * MUTATION: remove `if (label == null && component == null) return;` from `initCopy` and both tests
 * fail; widen it back to the component alone and the blank-square claim does.
 *
 * @author Adam
 */
public class testCutWithNothingHovered
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static LayoutEditor editor;
    private static LayoutDiagram page;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the layout editor needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        page = model.getLayout(PAGE);

        if (page == null) throw new AssertionError("no page " + PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
        });

        editor = built[0];
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (editor != null)
            {
                final LayoutEditor closing = editor;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Cut with the pointer off the grid keeps the group already on the clipboard: both paste paths prefer the group, so
     * a gesture that picks nothing must not empty it.
     */
    @Test
    public void testItDoesNotDiscardTheGroupOnTheClipboard() throws Exception
    {
        final boolean[] answer = new boolean[2];

        SwingUtilities.invokeAndWait(() ->
        {
            editor.clearSelection();
            editor.selectRow(rowWithTrack());

            answer[0] = editor.copySelection() && editor.hasGroupClipboard();

            editor.clearSelection();

            // The gesture: Control+X with the pointer over the palette hands `initCopy` the null the key handler never
            // checked for.
            editor.initCopy(null, null, true);

            answer[1] = editor.hasGroupClipboard();
        });

        assertTrue(answer[0], "precondition: a group has to reach the clipboard, or nothing below is "
            + "about losing one");

        assertTrue(answer[1], "cutting with the pointer off the grid threw away the group already on "
            + "the clipboard.  Both paste paths prefer the group, so the next paste put down a single "
            + "tile that was never picked up (X8-B1)");
    }

    /**
     * And it arms nothing, the half that reached the null dereference; the claims share one editor and the control
     * below is the only thing here that arms anything.
     */
    @Test
    public void testItArmsNothingToPaste() throws Exception
    {
        final boolean[] answer = new boolean[3];

        final java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

        gridField.setAccessible(true);

        // The arming flag is disarmed between the claims, because `initCopy` refusing does not CLEAR what is armed.
        final java.lang.reflect.Field toolField = LayoutEditor.class.getDeclaredField("toolFlag");

        toolField.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                editor.clearSelection();

                org.traincontrol.gui.LayoutGrid grid =
                    (org.traincontrol.gui.LayoutGrid) gridField.get(editor);

                toolField.set(editor, null);

                editor.initCopy(null, null, true);

                answer[0] = editor.hasToolFlag();

                // THE CONTROL, the shape the palette uses: a component handed over directly, with no label and
                // coordinates of -1.
                toolField.set(editor, null);

                int[] square = squareWithAComponent();

                editor.initCopy(null, page.getComponent(square[0], square[1]), false);

                answer[1] = editor.hasToolFlag();

                // AND THE OTHER CALLER THE NARROW GUARD IS FOR: a real grid label on a square with no track, which a
                // column drag hands over for every blank square.
                toolField.set(editor, null);

                int[] blank = blankSquare(grid);

                if (blank == null) throw new AssertionError("the fixture page has no blank square");

                editor.initCopy(grid.getValueAt(blank[0], blank[1]), null, true);

                answer[2] = editor.hasToolFlag();
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        assertFalse(answer[0], "cutting with the pointer off the grid armed the paste tool with no "
            + "component behind it, so Control+V and the right-click Paste item were both offered and "
            + "both reached new LayoutDiagramComponent(null) (X8-B1)");

        assertTrue(answer[1], "control: handing initCopy a real component armed nothing either, so "
            + "the assertion above is about a guard that refuses everything - including placing from "
            + "the palette, which passes coordinates of -1 on purpose");

        assertTrue(answer[2], "control: picking up a real grid square that holds no track armed "
            + "nothing.  A column being dragged carries its blank squares, and refusing them is how "
            + "the first version of this fix broke the column drag (X8-B1)");
    }

    /** A square of the fixture page that holds no track, which is what a column drag carries. */
    private static int[] blankSquare(org.traincontrol.gui.LayoutGrid grid)
    {
        for (int x = 0; x < page.getSx(); x++)
        {
            for (int y = 0; y < page.getSy(); y++)
            {
                if (page.getComponent(x, y) != null) continue;

                if (grid.getValueAt(x, y) == null) continue;

                return new int[]{x, y};
            }
        }

        return null;
    }

    /** A row of the fixture page that actually holds track, so the group copy has something in it. */
    private static int rowWithTrack()
    {
        for (int y = 0; y < page.getSy(); y++)
        {
            for (int x = 0; x < page.getSx(); x++)
            {
                if (page.getComponent(x, y) != null) return y;
            }
        }

        throw new AssertionError("the fixture page holds no components at all");
    }

    /** A square of the fixture page that holds a component. */
    private static int[] squareWithAComponent()
    {
        for (int x = 0; x < page.getSx(); x++)
        {
            for (int y = 0; y < page.getSy(); y++)
            {
                if (page.getComponent(x, y) != null) return new int[]{x, y};
            }
        }

        throw new AssertionError("the fixture page holds no components at all");
    }
}
