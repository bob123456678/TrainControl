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
     * The second version still found nothing, and the reason was worth the trip: the sample layout has
     * NO locomotives placed, so `getLocomotivesToRun` is empty and there are no paths to search at all.
     * A search that finds nothing because its input was empty looks exactly like a search that finds
     * nothing because the thing is not there - and I had already written a skip message blaming the
     * fixture for lacking curved stations. It has two, `TopMainR2Inter` and `TopMainR1Inter`, both
     * FEEDBACK_CURVE, both named by Adam off the top of his head.
     *
     * So a locomotive is placed here, on each candidate start in turn, until one of them can reach a
     * curved station. That is a real destination for a real train over real track; the only thing
     * arranged by hand is which square it starts from.
     *
     * `DiagramMonitor.lay` is public "so the geometry can be tested without a railway", so the run is
     * laid and published exactly as the monitor would, and rendered through the same LayoutGrid the
     * window uses. What comes out is the picture a train on that path would produce.
     */
    @Test
    public void testARealPathToACurvedStationIsDrawn() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("no active autonomy configuration to derive a graph from");
        }

        // The diagram's graph has to be PARSED before it is a railway. The window's session holds the
        // reduced graph, but `getAutoLayout` stays empty until the configuration it produces is parsed
        // - which is why the first search here found nothing and I nearly blamed the fixture: an empty
        // list of starts and a fixture with no curved stations look identical from the outside.
        model.parseAuto(session.buildConfiguration());

        org.traincontrol.automation.Layout auto = model.getAutoLayout();

        if (auto == null) throw new SkipException("no autonomy configuration on this layout");

        // The squares each edge covers, which is what turns a path between STATIONS into a line along
        // TRACK. Read from the builder the way DiagramMonitorDriver reads it, through the session, so
        // this cannot drift from what the running overlay would draw.
        java.util.Map<String, org.traincontrol.automationui.GraphReducer.ReducedEdge> edges =
            session.builder(null).edgesByName();

        assertFalse(auto.getPoints().isEmpty(), "the diagram produced a graph with no points");

        java.util.Map<String, org.traincontrol.automationui.TileGraph.TileKey> tiles = pointTiles();

        if (tiles.isEmpty()) throw new SkipException("no derived graph to map Points onto tiles");

        // Every Point that sits on curved track - the destinations this test is about
        java.util.Set<String> curved = new java.util.HashSet<>();

        for (java.util.Map.Entry<String, org.traincontrol.automationui.TileGraph.TileKey> e
            : tiles.entrySet())
        {
            if (isCurveAt(e.getValue())) curved.add(e.getKey());
        }

        if (curved.isEmpty()) throw new SkipException("no Point on this layout sits on a curve");

        org.traincontrol.base.Locomotive loc = model.getLocByName(model.getLocList().get(0));

        assertNotNull(loc, "no locomotive to place");

        int busy = 0, unmapped = 0;

        for (org.traincontrol.automation.Point from : starts(auto))
        {
            if (from.isOccupied()) { busy++; continue; }

            if (!tiles.containsKey(from.getName())) { unmapped++; continue; }

            if (curved.contains(from.getName())) continue;

            try
            {
                from.setLocomotive(loc);

                java.util.List<java.util.List<org.traincontrol.automation.Edge>> paths =
                    auto.getPossiblePaths(loc, true);

                for (java.util.List<org.traincontrol.automation.Edge> path : paths)
                {
                    if (path.isEmpty()) continue;

                    String arrivesAt = path.get(path.size() - 1).getEnd().getName();

                    if (!curved.contains(arrivesAt)) continue;

                    java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run =
                        asTiles(path, edges);

                    if (run.size() < 2) continue;

                    System.out.println("placed " + loc.getName() + " at " + from.getName()
                        + ", heading for " + arrivesAt);

                    draw(loc, run, run.get(run.size() - 1), "curve-arrival");

                    return;
                }
            }
            finally
            {
                // Whatever happens, the layout goes back as it was found - this is the shared
                // autonomy configuration, not a fixture of its own.
                from.setLocomotive(null);
            }
        }

        throw new SkipException("no start can reach a curved station - " + auto.getPoints().size()
            + " points, " + busy + " occupied, " + unmapped + " not on a tile, " + curved.size()
            + " curved");
    }

    /**
     * Candidate starting squares, the ones Adam named first.
     *
     * "Place at BottomMainB or A, then route to either of the TopMainR1/2 Inter." He knows this layout;
     * trying his squares before the other seventy saves a search and makes a failure mean something.
     */
    private java.util.List<org.traincontrol.automation.Point> starts(
        org.traincontrol.automation.Layout auto)
    {
        java.util.List<org.traincontrol.automation.Point> out = new java.util.ArrayList<>();

        for (org.traincontrol.automation.Point p : auto.getPoints())
        {
            if (p.getName().startsWith("BottomMain")) out.add(p);
        }

        for (org.traincontrol.automation.Point p : auto.getPoints())
        {
            if (!out.contains(p)) out.add(p);
        }

        return out;
    }

    /**
     * Lays the run, publishes it, renders the page and says where the picture went.
     */
    private void draw(org.traincontrol.base.Locomotive loc,
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run,
        org.traincontrol.automationui.TileGraph.TileKey last, String called) throws Exception
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

        // Neighbours, or the line has nothing to be drawn along.
        //
        // This is the assertion that would have caught the first version, which mapped Points to squares
        // and produced six unconnected marks scattered over the page. Everything else about that run
        // looked healthy - six squares, six overlays, none of them blank - and only the picture showed
        // it was not a path at all.
        for (int i = 1; i < run.size(); i++)
        {
            org.traincontrol.automationui.TileGraph.TileKey a = run.get(i - 1), b = run.get(i);

            assertEquals(a.getPage(), b.getPage(), "the run steps between pages at " + i);

            assertEquals(Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()), 1,
                "the run jumps from " + a + " to " + b + ", which are not neighbours");
        }

        int blank = 0;

        for (org.traincontrol.automationui.TileOverlay o : overlays.values())
        {
            if (o.isBlank()) blank++;
        }

        // A blank overlay is dropped by the label, so a run that lays only blanks renders a picture
        // with nothing on it and no complaint anywhere - which is what the first version of this did.
        assertEquals(blank, 0, "the run laid " + blank + " blank overlays of " + overlays.size());

        assertEquals(overlays.size(), run.size(), "a square of the run was not laid");

        javax.swing.SwingUtilities.invokeAndWait(() -> ui.getDiagramTileRegistry().publish(overlays));

        LayoutDiagram page = model.getLayout(last.getPage());

        assertNotNull(page, "the run ends on a page that is not loaded: " + last);

        BufferedImage shot = DiagramExport.render(page, 60, ui);

        File to = new File(OUT, called + ".png");

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
     *
     * The first version of this mapped each Edge's two POINTS to their squares, which produced six
     * squares scattered across the page and six unconnected stubs - because a Point is a station, and
     * the track between two stations is everything the edge steps over on the way. A line has to know
     * what is on either side of a square to be drawn through it, so a run of squares that are not
     * neighbours degenerates into a mark per square, and the picture said nothing about geometry.
     *
     * `DiagramMonitor.append` is public and does the joining, and the reduced edges carry the steps, so
     * this walks them exactly as the monitor does: start Point, every step, end Point.
     */
    private java.util.List<org.traincontrol.automationui.TileGraph.TileKey> asTiles(
        java.util.List<org.traincontrol.automation.Edge> path,
        java.util.Map<String, org.traincontrol.automationui.GraphReducer.ReducedEdge> edges)
    {
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> out = new java.util.ArrayList<>();

        // append() colours as it goes; the states this test wants are decided later, by position
        java.util.List<org.traincontrol.automationui.TileOverlay.State> ignored =
            new java.util.ArrayList<>();

        for (org.traincontrol.automation.Edge edge : path)
        {
            if (edge == null) continue;

            org.traincontrol.automationui.GraphReducer.ReducedEdge reduced = edges.get(edge.getName());

            if (reduced == null) continue;

            org.traincontrol.automationui.DiagramMonitor.append(out, ignored, reduced.getStart(),
                org.traincontrol.automationui.TileOverlay.State.ACTIVE);

            for (org.traincontrol.automationui.GraphReducer.TileStep step : reduced.getPath())
            {
                org.traincontrol.automationui.DiagramMonitor.append(out, ignored, step.getTile(),
                    org.traincontrol.automationui.TileOverlay.State.ACTIVE);
            }

            org.traincontrol.automationui.DiagramMonitor.append(out, ignored, reduced.getEnd(),
                org.traincontrol.automationui.TileOverlay.State.ACTIVE);
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
