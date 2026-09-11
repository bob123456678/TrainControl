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
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * X8-C5 and X8V-C2: one ceiling, asked by everything that grows the page, about the right dimension.
 *
 * **Three copies became one, and the one was wrong in a new way.** The limit on a page's size lived in
 * `growEdges`, again in `addRowsAndColumns`, and a third time in the right-click menu that greys
 * Increase Size - while Shift Down and Shift Right, two items away on the same submenu, grow the page by
 * a row or a column and asked nothing. At the ceiling the menu greyed one item with a tooltip explaining
 * why and offered another that did the same thing and worked (`X8-C5`).
 *
 * The predicate that replaced them took both dimensions and was written as one conjunction, so
 * `roomToGrow(0, 1)` still asked whether the page was too TALL - and refused a column shift, which adds
 * nothing to a page's height (`X8V-C2`). That shape is real here: a page's size comes from its largest
 * element coordinate with nothing clamping it, and three fixture pages in this repository parse at
 * 17 x 129.
 *
 * **Nothing pinned any of it**, which is why this class exists: `X8-C5` changed behaviour and
 * `grep -rn "roomToGrow" test/` found nothing. It is a pure function of two ints and the page's size,
 * which is about the cheapest thing in this window to pin.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111) - though nothing here reads the fixture's
 * contents, only its size, and the pages below are built in memory.
 *
 * MUTATION: charge each increment against the other dimension and `testATallPageMayStillGrowSideways`
 * fails; drop the ceiling out of `canShiftDown` and `testTheShiftGesturesAskTheCeiling` does.
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

        model = init(null, true, false, false, true);

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
     * A page too tall to grow downwards may still grow sideways, and the other way round.
     *
     * The question a shift has to ask is about the dimension it ADDS to. Asking about the other one is
     * asking whether the page is already too big, which a shift cannot make worse.
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

        // And the mirror, which is the half a single-dimension fix is most likely to leave out.
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

    /**
     * A page at the ceiling refuses both, which is the claim `X8-C5` was about.
     */
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
     * And the shift gestures ask it, which is what makes the predicate more than a greying rule.
     *
     * `shiftDown` and `shiftRight` each open with `if (!canShift...()) return;`, so the answer decides
     * the gesture and not only whether the menu item is enabled.
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

            // THE CONTROL: the same gestures on a page with room are offered, so the assertions above
            // are not about a predicate that refuses everything.
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
     * A page at the ceiling in ONE dimension can still grow in the other (NSV-C3, at the call site).
     *
     * **The test below this one pins the predicate, and the predicate was not what changed.** `X8V-C2`
     * had already made `roomToGrow` per-dimension; what `NSV-C3` fixed is that `addRowsAndColumns`
     * asked it about `(1, 1)` and then added `rows` and `cols`. So that test passes with the defect put
     * back - measured: 4 tests, 0 failures - and this one is the one that does not (FV3).
     *
     * **Asserted in the SUCCESS direction, on purpose.** The refusal branch is
     * `JOptionPane.showMessageDialog`, which is modal: a test that drives a refusal parks the event
     * thread until somebody clicks OK, and that stranded the runner twice in one day. A page at the row
     * ceiling growing sideways is refused by `roomToGrow(1, 1)` - rows are full - and allowed by
     * `roomToGrow(0, 5)`, so it separates the two without ever reaching the dialog.
     *
     * MUTATION: ask `roomToGrow(1, 1)` again and this does not fail, it HANGS - the refusal opens the
     * modal dialog. The harness reports a class that prints no summary, so the regression is still
     * caught; it is caught noisily rather than cleanly, and that is a property of the dialog rather
     * than of this test.
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

            final int wasWide = page.getSx();

            SwingUtilities.invokeAndWait(() -> editor.addRowsAndColumns(0, 5));

            assertEquals(page.getSx(), wasWide + 5,
                "a page at the row ceiling was refused five more COLUMNS, which it has room for.  The "
                + "guard asked whether there was room for one more of each and then added what it was "
                + "given (NSV-C3)");

            assertEquals(page.getSy(), LayoutEditor.MAX_SIZE,
                "and it did not grow in the dimension that was already full");
        }
        finally
        {
            dispose(editor);
        }
    }

    /**
     * The ceiling is asked about the amount being added, not about one (NSV-C3).
     *
     * `LayoutEditor.addRowsAndColumns(rows, cols)` asked `roomToGrow(1, 1)` - "is there room for one
     * more of each" - and then added `rows` and `cols`. It is `X8-C5`'s own one-predicate rule asked
     * about the wrong amount, in one of the methods `X8-C5` touched. It cannot overflow today because
     * its only caller pads a blank page to 16 x 21, which is why this is a trap rather than a defect.
     *
     * **Asserted at the predicate, because the refusal cannot be driven from a test.** The guard's
     * failure branch is `JOptionPane.showMessageDialog`, which is modal: calling `addRowsAndColumns`
     * with an amount it refuses blocks the event thread until somebody clicks OK, so a test that drives
     * it hangs rather than fails. That is not hypothetical - it hung this class for ten minutes and left
     * a JVM holding the runner's lock. What is pinned here is that the predicate DISCRIMINATES BY
     * AMOUNT on the page the caller would be asking about; that the caller passes its own arguments is
     * one line above it and is not covered.
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

    /**
     * An editor over a page of exactly this size, built in memory.
     */
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
     * Puts the editor's idea of where the pointer is onto a real square.
     *
     * The hover fields are private and are normally set by a mouse event; the shift predicates ask them
     * before they ask the ceiling, so a test of the ceiling has to get past that first.
     */
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
