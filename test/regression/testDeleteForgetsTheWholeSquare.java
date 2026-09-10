package regression;



import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * X8-B4: deleting a square from the diagram forgets everything the setup wrote about it.
 *
 * **It forgot the caption and left the rest.** `delete` called `forgetCaptionsAt`, which touches the
 * captions map alone, so a deleted platform kept its station membership, point name, length, facing,
 * barred arrivals, signal pairing, blocked flag, portal, link name and its `configurations.points`
 * block - all of it keyed to a square that now holds nothing. Every sibling gesture already reached
 * `forgetTiles`: copy, paste, fill and clear.
 *
 * **What made it a defect rather than a decision** is `clear()`'s own comment, which contrasts itself
 * with this method: *"Deleting one square tells the setup so; emptying the whole page told it nothing
 * at all."* That was not true of `delete`, and `clear`'s argument for why a reconciling save does not
 * rescue it - the window's non-reconciling write on the way out commits the orphans first - applies
 * here word for word.
 *
 * **The caption half is asserted too**, because the fix replaces the call that was doing it. A repair
 * that forgets more must not forget less.
 *
 * ON THE FROZEN RAILWAY, never `cs2_sample_layout` (OB-111).
 *
 * MUTATION: put `forgetCaptionsAt` back in `delete` and the length and name claims fail while the
 * caption claim passes.
 *
 * @author Adam
 */
public class testDeleteForgetsTheWholeSquare
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
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

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is no setup to forget");
        }

        page = model.getLayout(PAGE);

        if (page == null) throw new SkipException("no page " + PAGE);

        final LayoutEditor[] built = new LayoutEditor[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutEditor(page, 30, ui, 0);
            built[0].render();
            built[0].setAutonomyMode(session);
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
     * A square carrying a length, a point name and a caption loses all three when its track is deleted.
     *
     * Three, not one, and they are three different collections: the length and the name are facts about
     * the square, and the caption is a reference to it from somewhere else.
     */
    @Test
    public void testEverythingWrittenAboutTheSquareGoesWithIt() throws Exception
    {
        // The grid is private and the delete door takes a LABEL, which is how every gesture in the
        // window reaches it.  Read the same way `testThePaletteStillPlacesTiles` reads it, and the
        // square is chosen from the grid rather than named: the editor answers "which square is this
        // label on" by scanning for the label object, and the blank labels that hold the layout
        // together are shared, so a square is only usable here if the two directions agree about it.
        java.lang.reflect.Field gridField = LayoutEditor.class.getDeclaredField("grid");

        gridField.setAccessible(true);

        final org.traincontrol.gui.LayoutLabel[] found = new org.traincontrol.gui.LayoutLabel[1];
        final TileKey[] chosen = new TileKey[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                org.traincontrol.gui.LayoutGrid grid =
                    (org.traincontrol.gui.LayoutGrid) gridField.get(editor);

                if (grid == null) return;

                for (int x = 0; x < page.getSx() && found[0] == null; x++)
                {
                    for (int y = 0; y + 1 < page.getSy(); y++)
                    {
                        if (page.getComponent(x, y) == null) continue;

                        org.traincontrol.gui.LayoutLabel label = grid.getValueAt(x, y);

                        if (label == null) continue;

                        // The editor has to agree that this label is on this square, or `delete` looks
                        // somewhere else and does nothing.
                        if (editor.getGridX(label) != x || editor.getGridY(label) != y) continue;

                        found[0] = label;
                        chosen[0] = new TileKey(PAGE, x, y);
                        break;
                    }
                }
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        if (found[0] == null) throw new SkipException("no square on " + PAGE + " the editor agrees about");

        final TileKey square = chosen[0];

        TileKey label = new TileKey(PAGE, square.getX(), square.getY() + 1);

        session.getStore().setTileLength(square, 7);
        session.getStore().setPointName(square, "X8B4 Platform");
        session.getStore().setCaption(label, square);

        assertEquals(session.getStore().getTileLength(square), 7, "precondition: the length is set");

        assertEquals(session.getStore().getPointName(square), "X8B4 Platform",
            "precondition: the name is set");

        assertEquals(session.getStore().getCaptionTarget(label), square,
            "precondition: the caption points at the square");

        // THE LABEL IS RE-FETCHED HERE, not reused from the block above.  `render()` and every repaint
        // rebuild the grid, so a label held across two EDT blocks is a label the editor no longer
        // recognises - `getCoordinates` scans for the object and answers -1,-1 - and `delete` then
        // looks at the wrong square and quietly does nothing.
        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                org.traincontrol.gui.LayoutGrid grid =
                    (org.traincontrol.gui.LayoutGrid) gridField.get(editor);

                editor.delete(grid.getValueAt(square.getX(), square.getY()));
            }
            catch (ReflectiveOperationException e)
            {
                throw new RuntimeException(e);
            }
        });

        assertTrue(page.getComponent(square.getX(), square.getY()) == null,
            "precondition: the delete did not remove the track at " + square);

        assertEquals(session.getStore().getTileLength(square), 0,
            "the deleted square kept its measured length.  `delete` forgot the caption and left every "
            + "other collection keyed to track that no longer exists (X8-B4)");

        assertEquals(session.getStore().getPointName(square), null,
            "the deleted square kept its point name (X8-B4)");

        assertEquals(session.getStore().getCaptionTarget(label), null,
            "the caption naming the deleted square survived, so the repair that forgets more forgot "
            + "the one thing the old call did (X8-B4)");
    }

}
