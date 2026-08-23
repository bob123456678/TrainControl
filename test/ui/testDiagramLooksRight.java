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
     * A REAL autonomy path, ending at a curved station, drawn the way the running diagram draws it.
     *
     * OB-026: "when arriving at a curved station the red trace draws a straight line on the tile,
     * rather than following the shape of the station. Running through curves looks OK."
     *
     * The first version of this laid three squares I picked myself, which was worthless: a run that
     * does not follow real track says nothing about how real track is drawn. Adam's correction - "you
     * need to have an autonomy locomotive heading to that curved station as a destination" - is the
     * whole point, so the path here comes from `getPossiblePaths`, which is what the right-click menu
     * offers and what autonomy itself chooses between.
     *
     * `DiagramMonitor.lay` is public "so the geometry can be tested without a railway", so the run is
     * laid and published exactly as the monitor would, and rendered through the same LayoutGrid the
     * window uses. What comes out is the picture a train on that path would produce.
     */
    @Test
    public void testARealPathToACurvedStationIsDrawn() throws Exception
    {
        org.traincontrol.automation.Layout auto = model.getAutoLayout();

        if (auto == null) throw new SkipException("no autonomy configuration on this layout");

        java.util.Map<String, org.traincontrol.automationui.TileGraph.TileKey> tiles = pointTiles();

        if (tiles.isEmpty()) throw new SkipException("no derived graph to map Points onto tiles");

        for (org.traincontrol.base.Locomotive loc : auto.getLocomotivesToRun())
        {
            for (java.util.List<org.traincontrol.automation.Edge> path : auto.getPossiblePaths(loc, true))
            {
                java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run = asTiles(path, tiles);

                if (run.size() < 2) continue;

                org.traincontrol.automationui.TileGraph.TileKey last = run.get(run.size() - 1);

                if (!isCurveAt(last)) continue;

                draw(loc, run, last);

                return;
            }
        }

        throw new SkipException("no path on this layout ends at a curved square - OB-026 needs one, "
            + "and s88 1015 is the one Adam named");
    }

    /**
     * Lays the run, publishes it, renders the page and says where the picture went.
     */
    private void draw(org.traincontrol.base.Locomotive loc,
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run,
        org.traincontrol.automationui.TileGraph.TileKey last) throws Exception
    {
        java.util.List<org.traincontrol.automationui.TileOverlay.State> states =
            new java.util.ArrayList<>();

        // The first half reached, the rest still claimed - which is what a train part way along looks
        // like, and puts a colour change where the eye can see both.
        for (int i = 0; i < run.size(); i++)
        {
            states.add(i < run.size() / 2
                ? org.traincontrol.automationui.TileOverlay.State.REACHED
                : org.traincontrol.automationui.TileOverlay.State.ACTIVE);
        }

        java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
            org.traincontrol.automationui.TileOverlay> overlays = new java.util.LinkedHashMap<>();

        org.traincontrol.automationui.DiagramMonitor.lay(overlays, run, states);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui.getDiagramTileRegistry().publish(overlays));

        LayoutDiagram page = model.getLayout(last.getPage());

        assertNotNull(page, "the run ends on a page that is not loaded: " + last);

        BufferedImage shot = DiagramExport.render(page, 60, ui);

        File to = new File(OUT, "curve-arrival.png");

        javax.imageio.ImageIO.write(shot, "png", to);

        System.out.println("REAL path: " + loc.getName() + " over " + run.size()
            + " squares, ending on the curve at " + last + " -> " + to);
    }

    /**
     * Which tile each Point of the derived graph sits on.
     *
     * The station index answers square -> names, so this inverts it. The monitor is handed the same
     * map by its driver; building it here rather than reaching for the monitor keeps this test clear
     * of the running machinery it is trying to take a picture of.
     */
    private java.util.Map<String, org.traincontrol.automationui.TileGraph.TileKey> pointTiles()
    {
        java.util.Map<String, org.traincontrol.automationui.TileGraph.TileKey> out =
            new java.util.LinkedHashMap<>();

        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null || session.getReducer() == null) return out;

        for (org.traincontrol.automationui.TileGraph.TileKey tile
            : session.getReducer().getPoints().keySet())
        {
            for (String name : session.getStationIndex().pointNamesAt(tile))
            {
                out.put(name, tile);
            }
        }

        return out;
    }

    /**
     * A path as the squares it runs over, in order and without repeats.
     */
    private java.util.List<org.traincontrol.automationui.TileGraph.TileKey> asTiles(
        java.util.List<org.traincontrol.automation.Edge> path,
        java.util.Map<String, org.traincontrol.automationui.TileGraph.TileKey> tiles)
    {
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> out = new java.util.ArrayList<>();

        for (org.traincontrol.automation.Edge edge : path)
        {
            for (String name : new String[] {edge.getStart().getName(), edge.getEnd().getName()})
            {
                org.traincontrol.automationui.TileGraph.TileKey tile = tiles.get(name);

                if (tile != null && (out.isEmpty() || !tile.equals(out.get(out.size() - 1))))
                {
                    out.add(tile);
                }
            }
        }

        return out;
    }

    /**
     * Whether the square carries curved track.
     */
    private boolean isCurveAt(org.traincontrol.automationui.TileGraph.TileKey tile)
    {
        LayoutDiagram page = model.getLayout(tile.getPage());

        if (page == null) return false;

        org.traincontrol.base.LayoutDiagramComponent c = page.getComponent(tile.getX(), tile.getY());

        return c != null && isCurve(c.getType());
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
