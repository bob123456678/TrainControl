package regression;

import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
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
 * One ceiling, asked by everything that grows the page, about the right dimension. The limit on a page's size lived in
 * `growEdges`, in `addRowsAndColumns` and in the right-click menu that grays Increase Size, while Shift Down and Shift
 * Right grew the page and asked nothing. The one predicate that replaced them took both dimensions as one conjunction,
 * so `roomToGrow(0, 1)` still asked whether the page was too TALL and refused a column shift; that shape is real (a
 * page's size comes from its largest element coordinate, and three fixture pages parse at 17 x 129). It is a pure
 * function of two ints and the page's size, about the cheapest thing in this window to pin.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111), though nothing here reads the fixture's contents, only its
 * size.
 *
 * MUTATION: charge each increment against the other dimension and `testATallPageMayStillGrowSideways` fails; drop the
 * ceiling out of `canShiftDown` and `testTheShiftGesturesAskTheCeiling` does.
 *
 * @author Adam
 */
public class testTheDiagramCeiling
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the layout editor needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
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
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A page too tall to grow downwards may still grow sideways, and the other way round: a shift has to ask about the
     * dimension it ADDS to, since the other one is already as big as a shift can make it.
     */
    @Test
    public void testATallPageMayStillGrowSideways() throws Exception
    {
        // Taller than the ceiling and far narrower than it.
        LayoutEditor tall = editorOver(10, LayoutEditor.MAX_SIZE + 5);

        try
        {
            assertFalse(tall.roomToGrow(1, 0),
                "precondition: a page past the ceiling must refuse another row, or nothing below is "
                + "about a page that is too tall");

            assertTrue(tall.roomToGrow(0, 1),
                "a page with " + LayoutEditor.MAX_SIZE + " or more rows refused a COLUMN, which adds "
                + "nothing to its height.  The ceiling was written as one conjunction over both "
                + "dimensions, so each increment was charged against the other one (X8V-C2)");
        }
        finally
        {
            dispose(tall);
        }

        // And the mirror, the half a single-dimension fix is most likely to leave out.
        LayoutEditor wide = editorOver(LayoutEditor.MAX_SIZE + 5, 10);

        try
        {
            assertFalse(wide.roomToGrow(0, 1),
                "precondition: a page past the ceiling must refuse another column");

            assertTrue(wide.roomToGrow(1, 0),
                "a page with " + LayoutEditor.MAX_SIZE + " or more columns refused a ROW (X8V-C2)");
        }
        finally
        {
            dispose(wide);
        }
    }

    /** A page at the ceiling refuses both, which is the claim. */
    @Test
    public void testAPageAtTheCeilingRefusesToGrow() throws Exception
    {
        LayoutEditor full = editorOver(LayoutEditor.MAX_SIZE, LayoutEditor.MAX_SIZE);

        try
        {
            assertFalse(full.roomToGrow(1, 1),
                "a page already at the ceiling offered to grow.  Increase Size is greyed here with a "
                + "tooltip saying why, and Shift Down two items away did it anyway (X8-C5)");

            assertFalse(full.roomToGrow(1, 0), "nor by one row");
            assertFalse(full.roomToGrow(0, 1), "nor by one column");
        }
        finally
        {
            dispose(full);
        }
    }

    /**
     * And the shift gestures ask it, which makes the predicate more than a graying rule: `shiftDown` and `shiftRight`
     * each open with `if (!canShift...()) return;`.
     */
    @Test
    public void testTheShiftGesturesAskTheCeiling() throws Exception
    {
        LayoutEditor full = editorOver(LayoutEditor.MAX_SIZE, LayoutEditor.MAX_SIZE);

        try
        {
            // The hover has to be somewhere real, or the gestures refuse for the other reason.
            hoverAt(full, 2, 2);

            assertFalse(full.canShiftDown(),
                "Shift Down was offered on a page at the ceiling, and it grows the page by a row "
                + "(X8-C5)");

            assertFalse(full.canShiftRight(),
                "Shift Right was offered on a page at the ceiling, and it grows the page by a column "
                + "(X8-C5)");

            // The control: the same gestures on a page with room are offered, so the assertions above are not about a
            // predicate that refuses everything.
            LayoutEditor small = editorOver(10, 10);

            try
            {
                hoverAt(small, 2, 2);

                assertTrue(small.canShiftDown(), "control: Shift Down was refused on a page with room");

                assertTrue(small.canShiftRight(),
                    "control: Shift Right was refused on a page with room");
            }
            finally
            {
                dispose(small);
            }
        }
        finally
        {
            dispose(full);
        }
    }

    /**
     * A page at the ceiling in ONE dimension can still grow in the other (at the call site). The test below pins the
     * predicate, which was not what changed: `addRowsAndColumns` asked it about `(1, 1)` and then added `rows` and
     * `cols`, so that test passes with the defect put back.
     *
     * Asserted in the SUCCESS direction, on purpose: the refusal branch is the modal `JOptionPane.showMessageDialog`,
     * which parks the event thread until somebody clicks OK. A page at the row ceiling growing sideways is refused by
     * `roomToGrow(1, 1)` and allowed by `roomToGrow(0, 5)`, so this separates the two without reaching the dialog.
     *
     * MUTATION: ask `roomToGrow(1, 1)` again and this does not fail, it HANGS (the refusal opens the modal dialog); the
     * harness reports a class that prints no summary, so the regression is caught noisily rather than cleanly.
     */
    @Test
    public void testAPageAtTheRowCeilingStillGrowsSideways() throws Exception
    {
        final LayoutDiagram page = new LayoutDiagram("Ceiling sideways", 10, LayoutEditor.MAX_SIZE,
            null, null);

        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() -> built[0] = new LayoutEditor(page, 8, ui, 0));

        LayoutEditor editor = built[0];

        try
        {
            assertEquals(page.getSy(), LayoutEditor.MAX_SIZE, "precondition: the page is at the ceiling "
                + "in rows");

            assertFalse(editor.roomToGrow(1, 1),
                "precondition: the question the guard used to ask - room for one more of EACH - is no "
                + "on this page, which is what makes this test able to tell the two apart");

            // Measured either side of the call, in one task: building an editor also grows the page to fit its viewport
            // (`growEdges`) by an amount that depends on the window's geometry, which differs in a battery from a class
            // run alone, so reading the width before the call and comparing after it measured that growth as well.
            // Taken inside the same invokeAndWait, the assertion is about the difference rather than an absolute width
            // this test does not control.
            final int[] wide = new int[2];
            final int[] tall = new int[2];

            SwingUtilities.invokeAndWait(() ->
            {
                wide[0] = page.getSx();
                tall[0] = page.getSy();

                editor.addRowsAndColumns(0, 5);

                wide[1] = page.getSx();
                tall[1] = page.getSy();
            });

            assertEquals(wide[1], wide[0] + 5,
                "a page at the row ceiling was refused five more COLUMNS, which it has room for.  The "
                + "guard asked whether there was room for one more of each and then added what it was "
                + "given (NSV-C3)");

            assertEquals(tall[1], tall[0],
                "and it did not grow in the dimension that was already full");

            assertEquals(tall[1], LayoutEditor.MAX_SIZE,
                "the page is no longer at the row ceiling, so the case this test is about was not the "
                + "one exercised");
        }
        finally
        {
            dispose(editor);
        }
    }

    /**
     * The ceiling is asked about the amount being added, not about one: `LayoutEditor.addRowsAndColumns(rows, cols)`
     * asked `roomToGrow(1, 1)` and then added `rows` and `cols`. It cannot overflow today because its only caller pads
     * a blank page to 16 x 21, so this is a trap rather than a defect.
     *
     * Asserted at the predicate, because the refusal cannot be driven from a test: its failure branch is the modal
     * `JOptionPane.showMessageDialog`, so driving it hangs rather than fails. What is pinned is that the predicate
     * DISCRIMINATES BY AMOUNT on the page the caller would be asking about; that the caller passes its own arguments is
     * not covered.
     *
     * MUTATION: make `roomToGrow` ignore its arguments and use 1 and this fails.
     */
    @Test
    public void testTheCeilingIsAskedAboutTheAmount() throws Exception
    {
        // One row short of the ceiling: room for one more, and not for twenty.
        LayoutEditor editor = editorOver(10, LayoutEditor.MAX_SIZE - 1);

        try
        {
            assertTrue(editor.roomToGrow(1, 0),
                "a page one row below the ceiling has room for one more row");

            assertFalse(editor.roomToGrow(20, 0),
                "a page one row below the ceiling was told it had room for twenty more.  The guard in "
                + "addRowsAndColumns asked exactly this, about ONE, and then added twenty (NSV-C3)");

            // The same in the other dimension, so this is not passing because rows are special.
            assertFalse(editor.roomToGrow(0, LayoutEditor.MAX_SIZE),
                "a page was told it had room for another " + LayoutEditor.MAX_SIZE + " columns");
        }
        finally
        {
            dispose(editor);
        }
    }

    /** An editor over a page of exactly this size, built in memory. */
    private static LayoutEditor editorOver(int columns, int rows) throws Exception
    {
        final LayoutDiagram page = new LayoutDiagram("Ceiling " + columns + "x" + rows,
            columns, rows, null, null);

        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 8, ui, 0);
        });

        return built[0];
    }

    /**
     * Lets everything the editor posted for itself finish: several passes, because a posted task may post another.
     */
    private static void settle() throws Exception
    {
        for (int pass = 0; pass < 5; pass++)
        {
            SwingUtilities.invokeAndWait(() -> { });
        }
    }

    /** Puts the editor's idea of where the pointer is onto a real square (private fields a mouse event sets). */
    private static void hoverAt(LayoutEditor editor, int x, int y) throws Exception
    {
        java.lang.reflect.Field fx = LayoutEditor.class.getDeclaredField("lastHoveredX");
        java.lang.reflect.Field fy = LayoutEditor.class.getDeclaredField("lastHoveredY");

        fx.setAccessible(true);
        fy.setAccessible(true);

        fx.set(editor, x);
        fy.set(editor, y);
    }

    private static void dispose(LayoutEditor editor) throws Exception
    {
        if (editor == null) return;

        final LayoutEditor closing = editor;

        SwingUtilities.invokeAndWait(() -> closing.dispose());
    }
}
