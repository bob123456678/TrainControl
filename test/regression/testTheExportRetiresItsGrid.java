package regression;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.DiagramExport;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * An export that fails hands its grid back, exactly as one that works does.
 *
 * **The leak is written out beside the discard itself** (NR-3): the export builds a grid into a panel
 * nobody will ever show, and every caption it registers stays in the window's label table for the rest
 * of the session unless the grid is retired - keeping that whole grid, its tiles and its listeners
 * reachable.  The panel is a local, so no later export will ever match its owner and prune them.
 *
 * The discard sat after the paint with no `finally` (GUX-C4).  Two statements above it can leave by
 * the other door: the wait for tile images throws `InterruptedException`, and the paint runs under
 * `invokeAndWait`, which rethrows whatever the paint threw.
 *
 * **Driven, not read.**  A source-shape version of this rule passed against the unfixed file - the
 * paint's own `try`/`finally` around its `Graphics2D` sits between the two, so "there is a finally
 * before the discard" was true either way.
 *
 * **And made to fail through a seam, not by an interrupt.**  Interrupting the calling thread was tried
 * first: it lands at whichever statement it reaches, so the render sometimes threw before the grid
 * existed and the test passed for the wrong reason.  `stumbleForTest` throws at one known point, after
 * the grid is built and its captions registered.
 *
 * @author Adam
 */
public class testTheExportRetiresItsGrid
{
    /**
     * A failed export leaves no captions behind in the window's label table.
     *
     * @throws Exception from start-up
     */
    @Test
    public void testAnInterruptedExportStillHandsBackItsCaptions() throws Exception
    {
        Started up = start();

        try
        {
            int before = quiesced(up.ui);

            // THE PAGE HAS CAPTIONS TO LOSE (OP3-C5).  Both claims below compare a count with itself, so
            // a sandbox page with no station squares would make them pass about nothing at all.
            assertTrue(before > 0,
                "the first page of the sandbox registered no caption labels, so this class is comparing"
                + " zero with zero and says nothing about whether an export hands its own back");

            boolean threw = false;

            DiagramExport.stumbleForTest = () ->
            {
                throw new IllegalStateException("GUX-C4: the render is made to fail here");
            };

            try
            {
                DiagramExport.render(up.page, 30, up.ui);
            }
            catch (IllegalStateException e)
            {
                threw = true;
            }
            finally
            {
                DiagramExport.stumbleForTest = null;
            }

            assertTrue(threw,
                "precondition: the render finished normally although it was told to fail, so this test"
                + " is no longer exercising a failed export at all");

            assertEquals(quiesced(up.ui), before,
                "a failed export left its caption labels in the window's table.  They are keyed by an"
                + " owner that is a local panel, so nothing will ever prune them and the whole grid"
                + " stays reachable through them for the rest of the session (NR-3, GUX-C4)");
        }
        finally
        {
            up.close();
        }
    }

    /**
     * And the export that works still hands them back, which is the case NR-3 covered.
     *
     * Here because the fix moves the discard into a `finally`: a mistake there would take the working
     * path with it, and this is the half of the behaviour that was never in doubt.
     *
     * @throws Exception from start-up
     */
    @Test
    public void testAnExportThatWorksStillHandsThemBack() throws Exception
    {
        Started up = start();

        try
        {
            int before = quiesced(up.ui);

            assertTrue(DiagramExport.render(up.page, 30, up.ui) != null,
                "precondition: the export produced no picture");

            assertEquals(quiesced(up.ui), before,
                "an export that worked left its caption labels in the window's table (NR-3)");
        }
        finally
        {
            up.close();
        }
    }

    /**
     * A grid can be retired by the panel it was built into, without a reference to it (FNL-C1).
     *
     * **This is the door the export's bracket could not cover.**  `LayoutGrid`'s constructor registers itself
     * against its panel before it builds anything, and assigns the caller's reference only by returning - so a
     * constructor that threw part-way left captions registered with nothing for a `finally` to discard.  The
     * export retires by panel now, and this is what says that works.
     *
     * Driven on a grid that was built normally, because a constructor made to throw part-way needs a hook
     * `LayoutGrid` does not have.  What is pinned is the mechanism the bracket depends on.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAGridCanBeRetiredByItsPanel() throws Exception
    {
        Started up = start();

        try
        {
            int before = quiesced(up.ui);

            final javax.swing.JPanel host = new javax.swing.JPanel();

            SwingUtilities.invokeAndWait(() ->
                new org.traincontrol.gui.LayoutGrid(up.page, 30, host, null, true, up.ui));

            assertTrue(quiesced(up.ui) > before,
                "precondition: building a grid into a panel registered no captions at all, so retiring it"
                + " cannot be shown to hand any back");

            // BY THE PANEL, with no reference to the grid - which is all a failed constructor leaves.
            SwingUtilities.invokeAndWait(() -> org.traincontrol.gui.LayoutGrid.retire(host));

            assertEquals(quiesced(up.ui), before,
                "retiring by the panel left the grid's caption labels in the window's table, so the export's"
                + " bracket cannot clean up after a constructor that threw part-way (FNL-C1)");
        }
        finally
        {
            up.close();
        }
    }

    /**
     * How many caption labels the window is holding, once the count has stopped moving.
     *
     * **Start-up registers captions on its own, asynchronously**, so a count taken the moment
     * `setViewListener` returns is a count of part of the window's own work - and the first draft of
     * this class reported sixteen labels appearing across an export that had handed every one of its
     * own back.  Three identical samples in a row is what counts as finished.
     *
     * @param ui the window
     * @return the settled total
     * @throws Exception from the reflection or the sleep
     */
    private static int quiesced(TrainControlUI ui) throws Exception
    {
        int same = 0;
        int last = -1;

        for (int waited = 0; waited < 20000; waited += 250)
        {
            SwingUtilities.invokeAndWait(() -> { });

            int now = captions(ui);

            same = now == last ? same + 1 : 0;
            last = now;

            if (same >= 3) return now;

            Thread.sleep(250);
        }

        return last;
    }

    /**
     * How many caption labels the window is holding.
     *
     * @param ui the window
     * @return the total across every square
     * @throws Exception from the reflection
     */
    @SuppressWarnings("unchecked")
    private static int captions(TrainControlUI ui) throws Exception
    {
        Field table = TrainControlUI.class.getDeclaredField("layoutStations");

        table.setAccessible(true);

        int total = 0;

        for (Set<JLabel> labels : ((Map<Object, Set<JLabel>>) table.get(ui)).values())
        {
            total += labels.size();
        }

        return total;
    }

    /**
     * A window, a model and a page to draw.
     */
    private static final class Started
    {
        support.LayoutSandbox sandbox;

        MarklinControlStation model;

        TrainControlUI ui;

        LayoutDiagram page;

        void close() throws Exception
        {
            if (ui != null)
            {
                final TrainControlUI window = ui;

                SwingUtilities.invokeAndWait(() -> window.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Sandbox, model, window and the first page of the sample layout.
     *
     * @return the started application
     * @throws Exception from start-up
     */
    private static Started start() throws Exception
    {
        Started up = new Started();

        try
        {
            up.sandbox = support.LayoutSandbox.open();

            up.model = MarklinControlStation.init(null, true, false, false, false);

            final MarklinControlStation model = up.model;
            final TrainControlUI[] made = new TrainControlUI[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    made[0] = new TrainControlUI();
                    made[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            up.ui = made[0];

            assertTrue(!up.model.getLayoutList().isEmpty(),
                "precondition: the sandbox opened with no pages, so there is nothing to export");

            up.page = up.model.getLayout(up.model.getLayoutList().get(0));

            return up;
        }
        catch (Exception e)
        {
            up.close();

            throw e;
        }
    }
}
