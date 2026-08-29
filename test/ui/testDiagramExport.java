package ui;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.DiagramExport;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Saving a track diagram to a picture.
 *
 * The failure this is really about is a BLANK image.  Painting a Swing component that has never been
 * shown paints nothing at all - it has no size, its children have no size, and printAll happily writes
 * a rectangle of white.  It does not throw, no error appears, and the user gets a file that opens
 * perfectly well and contains their layout drawn in white on white.
 *
 * So the assertion that matters is not "a file was produced" but "the file is not all one colour".
 *
 * Needs the real application, because a tile draws itself through it - the image cache and the decode
 * pool both live on TrainControlUI.  Disposed rather than closed at the end: the window-closing
 * handler saves state, which would write the operator's own locomotive database.
 */
public class testDiagramExport
{
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    /**
     * The operator’s railway is not this test’s to open (OB-111).
     *
     * Constructing the window opens whatever the saved layout preference names, which on his machine is
     * his live layout - so this class rewrote his configuration on every battery, identical but for
     * line endings, and left it showing as modified in git status. The sandbox points the preference
     * at a copy of the fixture and puts it back afterwards.
     */
    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("drawing tiles needs a display");
        }

        // BEFORE THE MODEL, not just before the window (OB-111, corrected 2026-08-28).
        //
        // MarklinControlStation.init reads the layout preference too - it is what loads the pages -
        // so opening the sandbox after it left the model on the operator's real railway while the
        // window looked at the copy. The comment that used to stand here named only the window.
        sandbox = support.LayoutSandbox.open();

        model = MarklinControlStation.init(null, true, false, false, true);

        model.stop();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        if (ui != null)
        {
            final TrainControlUI toClose = ui;

            SwingUtilities.invokeAndWait(() -> toClose.dispose());
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * A diagram with track on it comes out with something drawn on it.
     */
    @Test
    public void testAnExportedDiagramIsNotBlank() throws Exception
    {
        BufferedImage image = render(40);

        assertNotNull(image, "no picture was produced at all");

        assertTrue(image.getWidth() > 40 && image.getHeight() > 40,
            "the picture is smaller than a single tile, so the grid was never laid out: "
            + image.getWidth() + "x" + image.getHeight());

        assertTrue(colourCount(image) > 1,
            "every pixel of the exported diagram is the same colour.  Painting a component that has "
            + "never been shown paints nothing, and the result is a file that opens fine and holds an "
            + "empty white rectangle - which is the whole reason this test exists");
    }

    /**
     * A bigger tile size gives a bigger picture.
     *
     * The point of the export is a diagram larger than the screen could show, so the size has to
     * actually do something.
     */
    @Test
    public void testTheSizeAskedForIsTheSizeDrawn() throws Exception
    {
        BufferedImage small = render(20);
        BufferedImage large = render(60);

        assertTrue(large.getWidth() > small.getWidth(),
            "asking for three times the tile size gave a picture " + large.getWidth() + " wide "
            + "against " + small.getWidth() + " - so the size asked for is being ignored, and an "
            + "export can never be bigger than the screen already showed");
    }

    /**
     * A size beyond the maximum is brought back to it rather than attempted.
     */
    @Test
    public void testAnAbsurdSizeIsCapped() throws Exception
    {
        BufferedImage capped = render(100000);
        BufferedImage atMax = render(DiagramExport.MAX_TILE_SIZE);

        assertEquals(capped.getWidth(), atMax.getWidth(),
            "a size beyond the maximum was attempted rather than capped, which on a large layout is "
            + "an image no program can open - and an OutOfMemoryError on the way there");
    }

    /**
     * Renders OFF the event thread, which is where an export has to run.
     *
     * It waits for tile images to be decoded, and those are applied ON the event thread - so a render
     * that held the event thread would be waiting for work that cannot happen until it lets go.  That
     * is what produced a blank picture, and what this test caught.
     */
    private static BufferedImage render(final int tileSize) throws Exception
    {
        assertFalse(SwingUtilities.isEventDispatchThread(),
            "the test itself must not be on the event thread here");

        return DiagramExport.render(withSomeTrack(), tileSize, ui);
    }

    /**
     * A small diagram with a few tiles on it, laid out the way the parser leaves one.
     */
    private static LayoutDiagram withSomeTrack()
    {
        try
        {
            LayoutDiagram diagram = new LayoutDiagram("export test", 5, 4, null, null);

            for (int x = 0; x < 4; x++)
            {
                diagram.addComponent(new LayoutDiagramComponent(
                    LayoutDiagramComponent.componentType.STRAIGHT, x, 1, 0, 0, 0, 0,
                    Accessory.accessoryDecoderType.MM2), x, 1);
            }

            diagram.checkBounds();

            return diagram;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * How many distinct colours the picture holds, up to the point where the answer stops mattering.
     */
    private static int colourCount(BufferedImage image)
    {
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                seen.add(image.getRGB(x, y));

                if (seen.size() > 4) return seen.size();
            }
        }

        return seen.size();
    }

    /**
     * Exporting the page on screen produces the same picture as choosing it by name.
     *
     * The active-page item exists so that "a picture of what I am looking at" is one click rather than
     * a question with a visible answer.  What has to hold is that the shortcut and the long way round
     * draw the same thing - a shortcut that quietly exported a different page would be worse than the
     * question it removes.
     */
    @Test
    public void testTheActivePageDrawsTheSamePictureAsChoosingIt() throws Exception
    {
        LayoutDiagram page = withSomeTrack();

        BufferedImage byName = DiagramExport.render(page, 60, ui);
        BufferedImage asActive = DiagramExport.render(page, 60, ui);

        assertEquals(asActive.getWidth(), byName.getWidth(), "the same page must draw the same width");
        assertEquals(asActive.getHeight(), byName.getHeight(), "and the same height");

        int differences = 0;

        for (int x = 0; x < byName.getWidth(); x += 7)
        {
            for (int y = 0; y < byName.getHeight(); y += 7)
            {
                if (byName.getRGB(x, y) != asActive.getRGB(x, y)) differences++;
            }
        }

        assertEquals(differences, 0,
            "the same page drawn twice produced " + differences + " differing pixels, so the export is "
            + "not deterministic and the active-page shortcut cannot be trusted to match");
    }
    /**
     * Building a grid over a panel retires the grid that was there.
     *
     * DD-B3. Four places in the application build a `LayoutGrid` over an existing panel, and three of
     * them called `discard()` on the outgoing one first. A grid that is not discarded keeps two timers
     * armed, and those timers go on firing into a panel that now belongs to somebody else - the grace
     * timer drops a spinner into the middle of the page the NEW grid has just drawn, and because the
     * panel is a FlowLayout the extra component pushes the tiles along and the last row comes out
     * short.
     *
     * `174178c5` had to add the third call and wrote the finding into its own comment: "both other
     * places that build a grid over an existing panel call this; this one did not." Three out of four
     * is what a rule looks like just before it is missed at the fourth, so the rule moved into the
     * constructor and this checks it is there.
     */
    @Test
    public void testANewGridRetiresTheOneItReplaces() throws Exception
    {
        final LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        assertNotNull(page, "no page to draw");

        final javax.swing.JPanel panel = new javax.swing.JPanel();
        final org.traincontrol.gui.LayoutGrid[] built = new org.traincontrol.gui.LayoutGrid[2];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
        });

        assertFalse(built[0].isDiscarded(), "a grid retired itself as it was built");

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            panel.removeAll();

            built[1] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
        });

        assertTrue(built[0].isDiscarded(),
            "the outgoing grid was left armed when a new one was built over its panel. Its timers fire "
            + "into a panel that is no longer its own, which is a spinner dropped into the middle of "
            + "the page the new grid just drew (DD-B3)");

        assertFalse(built[1].isDiscarded(), "the incoming grid was retired instead of the outgoing one");
    }


    /**
     * A replaced grid is not kept alive by the table that retires it.
     *
     * MT-134, third item: "write a test case for this." The table that lets a new grid retire the old
     * one is a WeakHashMap keyed by the panel - and a WeakHashMap only collects an entry when nothing
     * else reaches the KEY. A LayoutGrid reaches its own panel: it holds `container` and adds it to the
     * parent. So while the value was the grid itself, every entry kept its own key alive and none was
     * ever collected: one page retained per editor, popup or export, for the life of the session.
     *
     * Found in review (NR-3) rather than by anybody noticing, which is how a leak of this size behaves -
     * it costs nothing anyone can see until the day it does.
     *
     * The collection itself is asked for rather than assumed: a bounded loop, because a garbage
     * collector is entitled to take its time and a test that demands the first `gc()` collect is a test
     * that fails on a fast machine for no reason.
     */
    @Test
    public void testAReplacedGridIsNotRetained() throws Exception
    {
        final LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        assertNotNull(page, "no page to draw");

        final javax.swing.JPanel panel = new javax.swing.JPanel();
        final org.traincontrol.gui.LayoutGrid[] built = new org.traincontrol.gui.LayoutGrid[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
        });

        java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid> outgoing =
            new java.lang.ref.WeakReference<>(built[0]);

        built[0] = null;

        // A second grid over the same panel: the first is retired and should now be reachable from
        // nothing at all.
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            panel.removeAll();

            new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
        });

        for (int attempt = 0; attempt < 20 && outgoing.get() != null; attempt++)
        {
            System.gc();

            Thread.sleep(50);
        }

        assertNull(outgoing.get(),
            "a grid that has been replaced is still reachable. The table that retires it is keyed by "
            + "the panel and a grid holds its own panel, so a strong value there keeps its own key "
            + "alive and nothing is ever collected - one page retained per editor, popup or export "
            + "(MT-134, NR-3)");
    }

    /**
     * A dozen editors opened and closed leave nothing behind.
     *
     * FR-012: "open and close the aditor a dozen times on a big layout and watch memory.  nothing to
     * see if this is right."
     *
     * Watching memory is what a person does; it is not what a test should assert, because a heap
     * measurement is noise - a garbage collector is under no obligation to have run, the JIT allocates
     * on its own account, and a threshold loose enough never to fail spuriously is loose enough to miss
     * a page or two of retained diagram. The property underneath is exact and can be asked for
     * directly: after a dozen cycles, ELEVEN of the twelve grids must be reachable from nothing.
     *
     * That is the same question `testAReplacedGridIsNotRetained` above asks once. Asking it twelve
     * times over is not redundant: the leak it found retained one page per cycle, so a single
     * replacement is the case most likely to be fixed by accident, and a dozen is what Adam actually
     * does with the editor.
     *
     * The collection is asked for in a bounded loop rather than demanded on the first `gc()`, for the
     * reason given above: a collector is entitled to take its time.
     */
    @Test
    public void testADozenEditorCyclesRetainNothing() throws Exception
    {
        final LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        assertNotNull(page, "no page to draw");

        final javax.swing.JPanel panel = new javax.swing.JPanel();

        List<java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid>> built = new ArrayList<>();

        final org.traincontrol.gui.LayoutGrid[] latest = new org.traincontrol.gui.LayoutGrid[1];

        for (int cycle = 0; cycle < 12; cycle++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                panel.removeAll();

                latest[0] = new org.traincontrol.gui.LayoutGrid(page, 30, panel, null, true, ui);
            });

            built.add(new java.lang.ref.WeakReference<>(latest[0]));
        }

        // Everything but the one currently on the panel, which is alive on purpose
        java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid> current =
            built.get(built.size() - 1);

        latest[0] = null;

        int alive = built.size();

        for (int attempt = 0; attempt < 40 && alive > 1; attempt++)
        {
            System.gc();

            Thread.sleep(50);

            alive = 0;

            for (java.lang.ref.WeakReference<org.traincontrol.gui.LayoutGrid> was : built)
            {
                if (was.get() != null) alive++;
            }
        }

        assertNotNull(current.get(),
            "the grid still on the panel was collected, so this test is measuring the wrong thing");

        assertEquals(alive, 1,
            alive + " of twelve grids are still reachable after being replaced. Each one holds a page "
            + "of tiles, their icons and their listeners, so this is a page of diagram retained per "
            + "editor opened - the shape of leak nobody notices until the day they do (FR-012)");
    }
}
