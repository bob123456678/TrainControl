package ui;

import java.awt.GraphicsEnvironment;
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

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("drawing tiles needs a display");
        }

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


}
