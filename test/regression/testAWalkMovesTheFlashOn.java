package regression;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.LayoutEditor;
import org.traincontrol.gui.LayoutGrid;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Revealing a square ends the flash on the square revealed before it.
 *
 * Adam, on MT-454, 2026-09-16: *"When iterating quickly on the assign lengths popup (like by clicking skip), it does not
 * clear the prior highlights."*  Each step of Mass Assign Lengths - and of Name Everything, which shares the door -
 * calls `LayoutEditor.reveal`, which gives the square the diagram's yellow flash for 2.25 seconds by swapping its icon
 * and starting a timer.  Nothing ended the previous flash when the walk moved on, so pressing Skip faster than that left
 * a trail of yellow squares, each one looking like the square being asked about.
 *
 * Asked of the real editor window on a copy of the live snapshot, because the fault is in how two reveals in a row
 * leave the diagram - there is no smaller piece of it to ask.
 *
 * @author Adam
 */
public class testAWalkMovesTheFlashOn
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static LayoutEditor editor;

    private static final String PAGE = "1 - Main";

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the autonomy editor needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());
        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no autonomy configuration, so there is no editor walk to reveal squares");
        }

        final LayoutDiagram page = model.getLayout(PAGE);

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

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Two reveals in a row: the first square is back to its own icon the moment the second is revealed.
     *
     * MUTATION: drop the `endFlash` call from `reveal` and the first square is still yellow.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testRevealingASquareEndsThePreviousFlash() throws Exception
    {
        final List<Throwable> failed = new ArrayList<>();

        // THE IMAGES ARRIVE LATER.  A tile's picture is made on a worker and set through invokeLater, and a square
        // with no picture yet carries an EmptyIcon that `flashHighlight` rightly leaves alone - so until the page has
        // drawn, there is nothing to flash and nothing this test can see.  Waited for, with a bound.
        long giveUp = System.currentTimeMillis() + 15000;

        while (System.currentTimeMillis() < giveUp && drawnTrackSquares() < 2)
        {
            Thread.sleep(100);
        }

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                LayoutGrid grid = gridOf(editor);

                List<TileKey> track = new ArrayList<>();
                java.util.Map<String, Integer> seen = new java.util.TreeMap<>();

                for (TileKey tile : session.getGraph().getTiles().keySet())
                {
                    LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

                    String kind = tile.getPage() + " / " + (label == null ? "no label"
                        : label.getIcon() == null ? "no icon" : label.getIcon().getClass().getSimpleName());

                    seen.merge(kind, 1, Integer::sum);

                    if (!PAGE.equals(tile.getPage())) continue;

                    if (label != null && label.getIcon() instanceof ImageIcon && track.size() < 2) track.add(tile);
                }

                assertTrue(track.size() == 2, "precondition: fewer than two drawn track squares on " + PAGE + ": " + seen
                    + " (grid null: " + (grid == null) + ")");

                LayoutLabel first = grid.getValueAt(track.get(0).getX(), track.get(0).getY());
                LayoutLabel second = grid.getValueAt(track.get(1).getX(), track.get(1).getY());

                Icon firstPlain = first.getIcon();
                Icon secondPlain = second.getIcon();

                editor.reveal(track.get(0));

                assertNotSame(first.getIcon(), firstPlain,
                    "precondition: revealing the first square did not flash it, so nothing below is tested");

                editor.reveal(track.get(1));

                assertSame(first.getIcon(), firstPlain,
                    "the square revealed before is still yellow after the walk moved on - Adam: \"it does not clear the"
                    + " prior highlights\"");

                assertNotSame(second.getIcon(), secondPlain, "control: the square just revealed is not flashing");
            }
            catch (Throwable t)
            {
                failed.add(t);
            }
        });

        if (!failed.isEmpty())
        {
            if (failed.get(0) instanceof AssertionError) throw (AssertionError) failed.get(0);

            throw new AssertionError(failed.get(0));
        }
    }

    /** How many squares on the page have their picture yet, asked on the event thread. */
    private static int drawnTrackSquares() throws Exception
    {
        final int[] count = new int[1];

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                LayoutGrid grid = gridOf(editor);

                for (TileKey tile : session.getGraph().getTiles().keySet())
                {
                    if (!PAGE.equals(tile.getPage())) continue;

                    LayoutLabel label = grid.getValueAt(tile.getX(), tile.getY());

                    if (label != null && label.getIcon() instanceof ImageIcon) count[0]++;
                }
            }
            catch (Exception e)
            {
                count[0] = 0;
            }
        });

        return count[0];
    }

    private static LayoutGrid gridOf(LayoutEditor editor) throws Exception
    {
        java.lang.reflect.Field field = LayoutEditor.class.getDeclaredField("grid");

        field.setAccessible(true);

        return (LayoutGrid) field.get(editor);
    }
}
