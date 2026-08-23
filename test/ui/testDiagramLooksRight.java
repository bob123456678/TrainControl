package ui;

import java.awt.image.BufferedImage;
import java.io.File;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.DiagramExport;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Renders the real diagram to a picture, so that questions about how it LOOKS can be answered by
 * looking rather than by reading the painting code.
 *
 * Written 2026-08-22 after a run of defects that were all about pixels - a caption three tiles wide, a
 * star hidden under a badge, a star swallowed by its own outline - each of which took two or three
 * rounds because they were diagnosed by reading `paint` methods and reasoning. Every one of them would
 * have been obvious in a picture.
 *
 * `DiagramExport.render` already builds one offscreen: it is what the export feature uses, it goes
 * through the same LayoutGrid and the same TileAnnotation as the window, and it needs no visible frame.
 *
 * **This is a tool as much as a test.** The assertions below are deliberately weak - they check the
 * picture exists and is not blank - because their job is to keep the harness working. The value is the
 * PNG it leaves in the build folder, which a person or an agent can then open.
 *
 * @author Adam
 */
public class testDiagramLooksRight
{
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    /** Where the pictures land, for anybody who wants to look at them */
    private static final File OUT = new File(System.getProperty("java.io.tmpdir"), "tc-diagram-shots");

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        model = init(null, true, false, false, true);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        OUT.mkdirs();
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (model != null) model.stop();
    }

    /**
     * Every page of the sample layout, at two tile sizes, written out as PNGs.
     *
     * Two sizes because the defects that got here twice were both size-dependent: a mark floored at a
     * fixed stroke width looks right at 60px and vanishes at 30px, which is exactly what OB-037 was.
     */
    @Test
    public void testEveryPageRendersToAPictureWorthLooking()
    throws Exception
    {
        assertFalse(javax.swing.SwingUtilities.isEventDispatchThread(),
            "the export waits for tile images, so it cannot run on the event thread");

        java.util.List<String> pages = model.getLayoutList();

        assertFalse(pages.isEmpty(), "no pages to render - is cs2_sample_layout present?");

        int written = 0;

        for (String name : pages)
        {
            LayoutDiagram page = model.getLayout(name);

            if (page == null) continue;

            for (int size : new int[] {30, 60})
            {
                BufferedImage shot = DiagramExport.render(page, size, ui);

                assertNotNull(shot, name + " at " + size + "px rendered nothing");

                assertTrue(shot.getWidth() > 0 && shot.getHeight() > 0,
                    name + " at " + size + "px rendered an empty picture");

                assertTrue(colours(shot) > 2,
                    name + " at " + size + "px is all one colour, so nothing was drawn on it - the "
                    + "same failure testDiagramExport exists to catch");

                File to = new File(OUT,
                    name.replaceAll("[^A-Za-z0-9]+", "-") + "-" + size + ".png");

                javax.imageio.ImageIO.write(shot, "png", to);

                written++;
            }
        }

        assertTrue(written > 0, "nothing was written");

        System.out.println("diagram pictures written to " + OUT.getAbsolutePath()
            + " (" + written + " files)");
    }

    /**
     * A claimed path drawn across a CURVED tile, which is what OB-026 is about.
     *
     * "When arriving at a curved station the red trace draws a straight line on the tile, rather than
     * following the shape of the station."
     *
     * `DiagramMonitor.lay` is public for exactly this - "so the geometry can be tested without a
     * railway. Everything above it needs a running Layout with trains on it and cannot be reached from
     * a test at all; this needs a list of squares." So the run is built by hand from real squares of
     * the real layout, laid the way the monitor lays it, and published to the registry the way the
     * monitor publishes it. What comes out is the picture the running diagram would draw.
     *
     * The tile has to be a CURVE and it has to be in the middle of the run: a segment's two sides come
     * from the neighbouring squares, so an end tile draws a stub rather than a crossing.
     */
    @Test
    public void testAClaimedPathAcrossACurveIsDrawn() throws Exception
    {
        for (String name : model.getLayoutList())
        {
            LayoutDiagram page = model.getLayout(name);

            if (page == null) continue;

            java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run = curveWithNeighbours(page);

            if (run == null) continue;

            java.util.List<org.traincontrol.automationui.TileOverlay.State> states =
                java.util.Arrays.asList(
                    org.traincontrol.automationui.TileOverlay.State.REACHED,
                    org.traincontrol.automationui.TileOverlay.State.REACHED,
                    org.traincontrol.automationui.TileOverlay.State.ACTIVE);

            java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                org.traincontrol.automationui.TileOverlay> overlays = new java.util.LinkedHashMap<>();

            org.traincontrol.automationui.DiagramMonitor.lay(overlays, run, states);

            assertEquals(overlays.size(), 3, "the run did not lay three squares: " + run);

            // Published BEFORE the grid is built: a label picks up the last published overlay for its
            // square as it registers, which is how a page coming back from the cache is redrawn with
            // the run still on it.
            javax.swing.SwingUtilities.invokeAndWait(() ->
                ui.getDiagramTileRegistry().publish(overlays));

            BufferedImage shot = DiagramExport.render(page, 60, ui);

            File to = new File(OUT, "curve-run-" + name.replaceAll("[^A-Za-z0-9]+", "-") + ".png");

            javax.imageio.ImageIO.write(shot, "png", to);

            System.out.println("claimed path across a curve at " + run.get(1) + " -> " + to);

            return;
        }

        throw new SkipException("no curved tile with two neighbours on this layout");
    }

    /**
     * A curved square with track either side of it, as a three-square run.
     */
    private java.util.List<org.traincontrol.automationui.TileGraph.TileKey> curveWithNeighbours(
        LayoutDiagram page)
    {
        for (org.traincontrol.base.LayoutDiagramComponent c : page.getAll())
        {
            if (c == null || !isCurve(c.getType())) continue;

            // Left and right of it, which is the commonest shape and the one Adam described
            org.traincontrol.base.LayoutDiagramComponent before =
                page.getComponent(c.getX() - 1, c.getY());

            org.traincontrol.base.LayoutDiagramComponent after =
                page.getComponent(c.getX(), c.getY() + 1);

            if (before == null || after == null) continue;

            return java.util.Arrays.asList(
                new org.traincontrol.automationui.TileGraph.TileKey(page.getName(), c.getX() - 1, c.getY()),
                new org.traincontrol.automationui.TileGraph.TileKey(page.getName(), c.getX(), c.getY()),
                new org.traincontrol.automationui.TileGraph.TileKey(page.getName(), c.getX(), c.getY() + 1));
        }

        return null;
    }

    private boolean isCurve(org.traincontrol.base.LayoutDiagramComponent.componentType type)
    {
        return type == org.traincontrol.base.LayoutDiagramComponent.componentType.CURVE
            || type == org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK_CURVE
            || type == org.traincontrol.base.LayoutDiagramComponent.componentType.DOUBLE_CURVE
            || type == org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK_DOUBLE_CURVE;
    }

    /**
     * How many distinct colours, up to the point where the answer stops mattering.
     */
    private int colours(BufferedImage image)
    {
        java.util.Set<Integer> seen = new java.util.HashSet<>();

        for (int x = 0; x < image.getWidth(); x += 2)
        {
            for (int y = 0; y < image.getHeight(); y += 2)
            {
                seen.add(image.getRGB(x, y));

                if (seen.size() > 3) return seen.size();
            }
        }

        return seen.size();
    }
}
