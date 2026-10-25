package ui;

import java.awt.image.BufferedImage;
import java.io.File;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.autonomy.DiagramExport;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinControlStation;
import support.SourceOf;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Renders the real diagram to a picture (`DiagramExport.render`, the export feature's own path, through the same
 * LayoutGrid and TileAnnotation as the window), so that how it LOOKS is answered by looking.
 *
 * **This is a tool as much as a test.** The assertions are deliberately weak, because their job is to keep the
 * harness working; the value is the PNG it leaves in the build folder.
 *
 * @author Adam
 */
public class testDiagramLooksRight
{

    /**
     * The icons each claim draws: FR-118's for a claim marked `@DrawsFR118Icons`, the classic ones for every other.
     *
     * @param claim the claim about to run
     */
    @org.testng.annotations.BeforeMethod(alwaysRun = true)
    public void theIconsItDraws(java.lang.reflect.Method claim)
    {
        org.traincontrol.automationui.TileAnnotation.setStationIcons(claim.isAnnotationPresent(support.DrawsFR118Icons.class)
            ? org.traincontrol.automationui.TileAnnotation.StationIcons.SHAPES
            : org.traincontrol.automationui.TileAnnotation.StationIcons.CLASSIC);
    }

    /** And the classic icons again after it, whatever it set. */
    @org.testng.annotations.AfterMethod(alwaysRun = true)
    public void theClassicIconsAgain()
    {
        org.traincontrol.automationui.TileAnnotation.setStationIcons(null);
    }

    private static MarklinControlStation model;
    private static TrainControlUI ui;

    /**
     * The operator's railway is not this test's to open (OB-111): the sandbox points the layout preference at a copy
     * of the fixture and puts it back afterwards.
     */
    private static support.LayoutSandbox sandbox;

    /** Where the pictures land, for anybody who wants to look at them */
    private static final File OUT = new File(System.getProperty("java.io.tmpdir"), "tc-diagram-shots");

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        // BEFORE THE MODEL, not just before the window (OB-111): MarklinControlStation.init reads the layout
        // preference too, so the model would stay on the operator's real railway.
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedInDebug();

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        OUT.mkdirs();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        // THE WINDOW TOO: a real top-level window with its listeners and grid, so it is disposed here.
        if (ui != null)
        {
            final TrainControlUI closing = ui;

            javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
    }

    /**
     * Every page of the sample layout, at two tile sizes, written out as PNGs.  Some defects depend on size: a mark
     * floored at a fixed stroke width vanishes at 30px (OB-037).
     */
    @Test
    public void testEveryPageRendersToAPictureWorthLooking()
    throws Exception
    {
        assertFalse(javax.swing.SwingUtilities.isEventDispatchThread(),
            "the export waits for tile images, so it cannot run on the event thread");

        java.util.List<String> pages = model.getLayoutList();

        assertFalse(pages.isEmpty(), "no pages to render - is test/test_layout present?");

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
     * A REAL autonomy path, ending at a curved station, drawn the way the running diagram draws it (OB-026: the red
     * trace must follow the station's shape, not draw a straight line).
     *
     * The path comes from `getPossiblePaths`, as the right-click menu offers.  The sample layout has no locomotives,
     * so one is placed on each candidate start until one reaches a curved station.  `DiagramMonitor.lay` lays and
     * publishes the run as the monitor would.
     */
    @Test
    public void testARealPathToACurvedStationIsDrawn() throws Exception
    {
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run = realRunEndingOnACurve();

        System.out.println("placed " + runLocomotive.getName() + " for a run of " + run.size()
            + " squares ending at " + run.get(run.size() - 1));

        draw(runLocomotive, run, run.get(run.size() - 1), "curve-arrival");
    }

    /**
     * Every pixel the run paints lands on track (OB-026, MT-124, MT-127).
     *
     * An INVARIANT rather than a golden image: render the page bare and with a real run laid on it, and look only at
     * the pixels that CHANGED to a run color.  Each must be within `TOLERANCE` of dark ink in the bare image.  The
     * difference cancels tile art, badges, captions and lamps, and the chevrons and train dot are black and white.
     *
     * The tolerance covers the stroke (a seventh of a tile, centered on the rail) and its anti-aliasing, and is far
     * too narrow to reach the middle of a curve from the rail that cuts its corner.
     */
    @Test
    public void testEveryPixelOfARunLandsOnTrack() throws Exception
    {
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run = realRunEndingOnACurve();

        LayoutDiagram page = model.getLayout(run.get(0).getPage());

        assertNotNull(page, "the run is on a page that is not loaded");

        // ADDRESSES OFF, whatever the operator's Show Addresses says: an address label covers the line in front of
        // its square (OB-259), which is not what this measures
        final boolean addressesWere = page.getShowAddress();

        page.setShowAddress(false);

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.getDiagramTileRegistry().publish(
                new java.util.LinkedHashMap<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileOverlay>()));

            BufferedImage bare = DiagramExport.render(page, 60, ui);

            java.util.List<org.traincontrol.automationui.TileOverlay.State> states =
                new java.util.ArrayList<>();

            for (int i = 0; i < run.size(); i++)
            {
                states.add(i < run.size() / 2
                    ? org.traincontrol.automationui.TileOverlay.State.REACHED
                    : org.traincontrol.automationui.TileOverlay.State.ACTIVE);
            }

            final java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                org.traincontrol.automationui.TileOverlay> overlays = new java.util.LinkedHashMap<>();

            org.traincontrol.automationui.DiagramMonitor.lay(overlays, run, states);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.getDiagramTileRegistry().publish(overlays));

            BufferedImage drawn = DiagramExport.render(page, 60, ui);

            // Both pictures kept, always: when this fails the first question is "show me".
            javax.imageio.ImageIO.write(bare, "png", new File(OUT, "run-bare.png"));
            javax.imageio.ImageIO.write(drawn, "png", new File(OUT, "run-drawn.png"));

            assertEquals(drawn.getWidth(), bare.getWidth(), "the two renders are different sizes");
            assertEquals(drawn.getHeight(), bare.getHeight(), "the two renders are different sizes");

            java.util.Map<String, Integer> inkPerSquare = new java.util.LinkedHashMap<>();

            int offArt = 0;
            int strayed = 0;

            java.util.Set<String> onTheRun = new java.util.HashSet<>();

            for (org.traincontrol.automationui.TileGraph.TileKey tile : run)
            {
                onTheRun.add(tile.getX() + "," + tile.getY());
            }

            for (int y = 0; y < drawn.getHeight(); y++)
            {
                for (int x = 0; x < drawn.getWidth(); x++)
                {
                    int now = drawn.getRGB(x, y);

                    if (now == bare.getRGB(x, y) || !isRunInk(now)) continue;

                    String square = (x / 60) + "," + (y / 60);

                    if (!onTheRun.contains(square)) strayed++;

                    Integer had = inkPerSquare.get(square);
                    inkPerSquare.put(square, had == null ? 1 : had + 1);

                    if (!nearTrack(bare, x, y)) offArt++;
                }
            }

            // 1. It was drawn at all.
            int total = 0;

            for (int count : inkPerSquare.values()) total += count;

            assertTrue(total > 200, "only " + total + " pixels of route ink over " + run.size()
                + " squares - the run was barely drawn, so nothing below would mean anything");

            // 2. Every square of the run carries some: a square the line skips is a gap in a route.
            java.util.List<String> blank = new java.util.ArrayList<>();

            // A STATION'S SQUARE ANYWHERE ON THE RUN, as the run's ends below: its icon is painted over the line there
            // (MT-076), covering most of a curve's short chord.
            java.util.Set<String> stations = new java.util.HashSet<>();

            for (org.traincontrol.automationui.TileGraph.TileKey tile : run)
            {
                int blue = 0;

                for (int y = tile.getY() * 60; y < tile.getY() * 60 + 60 && y < bare.getHeight(); y++)
                {
                    for (int x = tile.getX() * 60; x < tile.getX() * 60 + 60 && x < bare.getWidth(); x++)
                    {
                        int rgb = bare.getRGB(x, y);

                        if ((rgb & 0xFF) > 150 && ((rgb >> 16) & 0xFF) < 90 && ((rgb >> 8) & 0xFF) < 90) blue++;
                    }
                }

                if (blue >= 20) stations.add(tile.getX() + "," + tile.getY());
            }

            for (org.traincontrol.automationui.TileGraph.TileKey tile : run)
            {
                String square = tile.getX() + "," + tile.getY();

                // The two ENDS may be hidden: a station's badge is painted over the line there (MT-076).
                if (tile.equals(run.get(0)) || tile.equals(run.get(run.size() - 1))) continue;

                if (stations.contains(square)) continue;

                Integer here = inkPerSquare.get(square);

                // A REAL segment's worth, not a few pixels: a neighbor's segment bleeds single figures over the
                // shared edge, a full segment is several hundred.
                if (here == null || here < MINIMUM_INK_PER_SQUARE) blank.add(square + "=" + here);
            }

            assertEquals(blank, new java.util.ArrayList<String>(),
                "the route line is missing from squares it runs over: " + blank);

            // 3. And none of it landed anywhere else: ink outside the run is a line drawn where no train is going.
            assertEquals(strayed, 0,
                strayed + " pixels of route ink were painted on squares the run does not use");

            // NOT asserted: "every pixel lies on track art".  The line is a straight chord between edge midpoints on
            // purpose, so on switches, crossings and scissors it legitimately cuts across the art, and rails are
            // outlines with a pale interior.  That leaves only a tolerance, and one tuned until green checks nothing,
            // so the number is REPORTED, not asserted: a few hundred pixels of sixteen thousand, all on multi-road
            // squares.
            System.out.println("run ink: " + total + " pixels over " + inkPerSquare.size()
                + " squares, " + offArt + " of them not within " + TOLERANCE + "px of tile art");
        }
        finally
        {
            page.setShowAddress(addressesWere);
        }
    }

    /** The window's session, for asking a square how many roads it has */
    private org.traincontrol.automationui.AutonomySession session()
    {
        return ui.getAutonomySession();
    }

    /**
     * Whether this pixel is a run color (the path ahead or the track driven, from `DiagramColours`, FR-106).
     *
     * Loose, since stroke edges blend, but tight enough to exclude the white chevrons and the black and white train
     * dot.  The station badges are the path's blue in both renders, so the difference leaves them out.
     */
    private boolean isRunInk(int rgb)
    {
        return near(rgb, org.traincontrol.automationui.DiagramColours.PATH_AHEAD)
            || near(rgb, org.traincontrol.automationui.DiagramColours.PATH_DRIVEN);
    }

    /** Whether a pixel is within a blend's reach of a color. */
    private static boolean near(int rgb, java.awt.Color colour)
    {
        return Math.abs(((rgb >> 16) & 0xFF) - colour.getRed()) < 60
            && Math.abs(((rgb >> 8) & 0xFF) - colour.getGreen()) < 60
            && Math.abs((rgb & 0xFF) - colour.getBlue()) < 60;
    }

    /** Whether the UNDRAWN page has track ink within a few pixels of here. */
    private boolean nearTrack(BufferedImage bare, int atX, int atY)
    {
        for (int y = Math.max(0, atY - TOLERANCE); y <= Math.min(bare.getHeight() - 1, atY + TOLERANCE); y++)
        {
            for (int x = Math.max(0, atX - TOLERANCE); x <= Math.min(bare.getWidth() - 1, atX + TOLERANCE); x++)
            {
                int rgb = bare.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Any art at all, not just the black rails: a switch draws its other road in pale gray,
                // and gray is still track. The background is what the line must not be drawn on.
                if (r < 236 || g < 236 || b < 236) return true;
            }
        }

        return false;
    }

    /**
     * How far route ink may sit from its rail, in pixels of a 60px tile: the stroke is a seventh of a tile and
     * anti-aliased.
     */
    private static final int TOLERANCE = 3;

    /**
     * The least route ink a square the run crosses may carry: a full segment is several hundred pixels, a neighbor's
     * bleed single figures.
     */
    private static final int MINIMUM_INK_PER_SQUARE = 50;

    /** The locomotive the last call to realRunEndingOnACurve placed, for messages */
    private org.traincontrol.base.Locomotive runLocomotive;

    /**
     * A real autonomy path, over real track, ending on a curved square.
     *
     * Shared by the tests that DRAW it and the one that measures the ink: finding it is most of the work, and two
     * copies could conclude different things about one railway.  The layout is left exactly as found.
     *
     * @return the squares of the run, never empty
     */
    private java.util.List<org.traincontrol.automationui.TileGraph.TileKey> realRunEndingOnACurve()
        throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null || session.getStore().getActiveConfiguration() == null)
        {
            throw new AssertionError("no active autonomy configuration to derive a graph from");
        }

        // The diagram's graph has to be PARSED before it is a railway: `getAutoLayout` stays empty until then.
        model.parseAuto(session.buildConfiguration());

        org.traincontrol.automation.Layout auto = model.getAutoLayout();

        if (auto == null) throw new AssertionError("no autonomy configuration on this layout");

        // The squares each edge covers, which turns a path between STATIONS into a line along TRACK; read through
        // the session as DiagramMonitorDriver reads it.
        java.util.Map<String, org.traincontrol.automationui.GraphReducer.ReducedEdge> edges =
            session.builder(null).edgesByName();

        assertFalse(auto.getPoints().isEmpty(), "the diagram produced a graph with no points");

        java.util.Map<String, org.traincontrol.automationui.TileGraph.TileKey> tiles = pointTiles();

        assertFalse(tiles.isEmpty(), "no derived graph to map Points onto tiles");

        java.util.Set<String> curved = new java.util.HashSet<>();

        for (java.util.Map.Entry<String, org.traincontrol.automationui.TileGraph.TileKey> e
            : tiles.entrySet())
        {
            if (isCurveAt(e.getValue())) curved.add(e.getKey());
        }

        assertFalse(curved.isEmpty(), "no Point on this layout sits on a curve");

        runLocomotive = model.getLocByName(model.getLocList().get(0));

        assertNotNull(runLocomotive, "no locomotive to place");

        // Every train taken off first, and put back afterwards: a start with a locomotive on it is skipped and an
        // occupied destination is not offered, so parked trains would silently decide whether this test runs.
        java.util.Map<org.traincontrol.automation.Point, org.traincontrol.base.Locomotive> parked =
            new java.util.LinkedHashMap<>();

        for (org.traincontrol.automation.Point p : auto.getPoints())
        {
            if (p.getCurrentLocomotive() != null) parked.put(p, p.getCurrentLocomotive());
        }

        for (org.traincontrol.automation.Point p : parked.keySet()) p.setLocomotive(null);

        try
        {
            return searchForARun(auto, edges, tiles, curved);
        }
        finally
        {
            for (java.util.Map.Entry<org.traincontrol.automation.Point,
                org.traincontrol.base.Locomotive> was : parked.entrySet())
            {
                was.getKey().setLocomotive(was.getValue());
            }
        }
    }

    /** The search itself, on a railway with nothing standing on it. */
    private java.util.List<org.traincontrol.automationui.TileGraph.TileKey> searchForARun(
        org.traincontrol.automation.Layout auto,
        java.util.Map<String, org.traincontrol.automationui.GraphReducer.ReducedEdge> edges,
        java.util.Map<String, org.traincontrol.automationui.TileGraph.TileKey> tiles,
        java.util.Set<String> curved)
    {
        int busy = 0, unmapped = 0;

        for (org.traincontrol.automation.Point from : starts(auto))
        {
            if (from.isOccupied()) { busy++; continue; }

            if (!tiles.containsKey(from.getName())) { unmapped++; continue; }

            if (curved.contains(from.getName())) continue;

            try
            {
                from.setLocomotive(runLocomotive);

                for (java.util.List<org.traincontrol.automation.Edge> path
                    : auto.getPossiblePaths(runLocomotive, true))
                {
                    if (path.isEmpty()) continue;

                    if (!curved.contains(path.get(path.size() - 1).getEnd().getName())) continue;

                    java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run =
                        asTiles(path, edges);

                    if (run.size() >= 2) return run;
                }
            }
            finally
            {
                from.setLocomotive(null);
            }
        }

        throw new AssertionError("no start can reach a curved station - " + auto.getPoints().size()
            + " points, " + busy + " occupied, " + unmapped + " not on a tile, " + curved.size()
            + " curved");
    }

    /** Candidate starting squares, the BottomMain ones first: they are known to reach the curved stations. */
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

    /** Lays the run, publishes it, renders the page and says where the picture went. */
    private void draw(org.traincontrol.base.Locomotive loc,
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run,
        org.traincontrol.automationui.TileGraph.TileKey last, String called) throws Exception
    {
        java.util.List<org.traincontrol.automationui.TileOverlay.State> states =
            new java.util.ArrayList<>();

        // The first half reached, the rest still claimed: a train part way along, with a color change to see.
        for (int i = 0; i < run.size(); i++)
        {
            states.add(i < run.size() / 2
                ? org.traincontrol.automationui.TileOverlay.State.REACHED
                : org.traincontrol.automationui.TileOverlay.State.ACTIVE);
        }

        java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
            org.traincontrol.automationui.TileOverlay> overlays = new java.util.LinkedHashMap<>();

        org.traincontrol.automationui.DiagramMonitor.lay(overlays, run, states);

        // Neighbors, or the line has nothing to be drawn along: a run mapped from Points to squares gives scattered
        // marks that look healthy in everything but the picture.
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

        // A blank overlay is dropped by the label, so a run of blanks renders an empty picture with no complaint.
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
     * Which tile each Point of the derived graph sits on: the station index inverted, apart from the running monitor.
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
     * Mapping each Edge's two POINTS to squares would give unconnected stubs: the track between two stations is
     * everything the edge steps over, and a line needs neighbors.  This walks the reduced edges as
     * `DiagramMonitor.append` does: start Point, every step, end Point.
     */
    private java.util.List<org.traincontrol.automationui.TileGraph.TileKey> asTiles(
        java.util.List<org.traincontrol.automation.Edge> path,
        java.util.Map<String, org.traincontrol.automationui.GraphReducer.ReducedEdge> edges)
    {
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> out = new java.util.ArrayList<>();

        // append() colors as it goes; this test decides the states later, by position
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

    /** Whether the square carries curved track. */
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
     * Station captions are blue or light gray, whichever the operator asked for, and readable either way (FR-031).
     *
     * The color is ASKED FOR each time a caption is colored, so the menu switch takes effect without a restart.  The
     * readability half is not decoration: the text color follows the fill's brightness, so a gray a few shades DARKER
     * would leave black text on a dark ground and nobody would notice until they looked.
     *
     * The operator's own setting is put back at the end, as a real preference node.
     *
     * MUTATION: making restingFill ignore the preference fails the second assertion; making readableOn return WHITE
     * always fails the fourth.
     */
    @Test
    public void testStationLabelsFollowTheColourPreference()
    {
        // WHETHER IT WAS STORED, not what the accessor answers: writing back a captured default would materialize the
        // preference on a machine that never set it.
        boolean had = TrainControlUI.getPrefs().get(TrainControlUI.STATION_LABELS_GREY, null) != null;

        boolean was = TrainControlUI.stationLabelsAreGrey();

        try
        {
            // GRAY WHEN NOTHING HAS BEEN CHOSEN, blue being the optional setting via the existing preference
            TrainControlUI.getPrefs().remove(TrainControlUI.STATION_LABELS_GREY);

            assertEquals(org.traincontrol.gui.widgets.StationCaption.restingFill(),
                org.traincontrol.gui.widgets.StationCaption.PILL_GREY,
                "with nothing chosen the station labels are not grey, which is the default now");

            TrainControlUI.getPrefs().putBoolean(TrainControlUI.STATION_LABELS_GREY, false);

            assertEquals(org.traincontrol.gui.widgets.StationCaption.restingFill(),
                org.traincontrol.gui.widgets.StationCaption.PILL_AT_REST,
                "the preference switched off and the captions are not blue, so blue cannot be chosen");

            assertEquals(org.traincontrol.gui.widgets.StationCaption.readableOn(
                org.traincontrol.gui.widgets.StationCaption.restingFill()), java.awt.Color.WHITE,
                "white text on the blue is the look that was asked for");

            TrainControlUI.getPrefs().putBoolean(TrainControlUI.STATION_LABELS_GREY, true);

            assertEquals(org.traincontrol.gui.widgets.StationCaption.restingFill(),
                org.traincontrol.gui.widgets.StationCaption.PILL_GREY,
                "the preference was set and the captions are still blue, so the menu switch changes a "
                + "stored value and nothing on the diagram");

            assertEquals(org.traincontrol.gui.widgets.StationCaption.readableOn(
                org.traincontrol.gui.widgets.StationCaption.restingFill()), java.awt.Color.BLACK,
                "white text on the light grey, which cannot be read. The text colour is worked out "
                + "from the fill for exactly this reason, and a grey this light has to take black");
        }
        finally
        {
            // Put back as it was, INCLUDING never having been set.
            if (had) TrainControlUI.getPrefs().putBoolean(TrainControlUI.STATION_LABELS_GREY, was);
            else TrainControlUI.getPrefs().remove(TrainControlUI.STATION_LABELS_GREY);
        }

        // The finally above has just written this, so this guards only that the RESTORE ran.
        assertEquals(TrainControlUI.stationLabelsAreGrey(), was,
            "this test left the operator's own preference changed");
    }

    /**
     * A placeholder is drawn dimmer than a name, on either pill.
     *
     * The editors show "this square is a PLACEHOLDER" in gray.  On a pill, gray is not red, so `onPill` must not fall
     * through to `readableOn`, which answers by the FILL and would give a placeholder and a name the same color.
     *
     * Both fills: on navy the readable color is white and dimming moves it DOWN towards the fill, on pale gray it is
     * black and dimming moves it UP.  The separation is asserted as a distance, not a constant, because the exact blend
     * is a matter of taste.
     *
     * MUTATION: returning `readableOn(fill)` for gray fails the first assertion on both fills.
     */
    @Test
    public void testAPlaceholderStaysDimmerThanAName()
    {
        // The gray LayoutGrid writes out longhand wherever it means "placeholder".
        java.awt.Color placeholder = new java.awt.Color(150, 150, 150);

        for (java.awt.Color fill : new java.awt.Color[] {
            org.traincontrol.gui.widgets.StationCaption.PILL_AT_REST,
            org.traincontrol.gui.widgets.StationCaption.PILL_GREY })
        {
            java.awt.Color name = org.traincontrol.gui.widgets.StationCaption.onPill(
                fill, java.awt.Color.BLACK);

            java.awt.Color dim = org.traincontrol.gui.widgets.StationCaption.onPill(fill, placeholder);

            assertNotEquals(dim, name,
                "a placeholder and a name came back the same colour on " + fill + ", so the two "
                + "states the editor draws are one state on the screen");

            // Dimmer means NEARER THE PILL (darker on navy, lighter on pale gray); distance to the fill is true of
            // both.
            assertTrue(distance(dim, fill) < distance(name, fill),
                "the placeholder is further from the pill than the name is on " + fill + ", which is "
                + "the opposite of dim");

            // And still readable: dimming all the way to the fill would erase an unnamed station.
            assertTrue(distance(dim, fill) > 40,
                "the placeholder is within 40 of the pill colour on " + fill + " and has effectively "
                + "disappeared into it");
        }

        // The ANSWER colors are not dimmed: a rule keyed on "all three channels equal" catches black and white too.
        assertEquals(
            org.traincontrol.gui.widgets.StationCaption.onPill(
                org.traincontrol.gui.widgets.StationCaption.PILL_AT_REST, java.awt.Color.BLACK),
            org.traincontrol.gui.widgets.StationCaption.readableOn(
                org.traincontrol.gui.widgets.StationCaption.PILL_AT_REST),
            "a plain caption was dimmed, so every station name on the diagram is now a placeholder");

        // And red still means red, which is the distinction that already worked.
        java.awt.Color notReached = org.traincontrol.gui.widgets.StationCaption.onPill(
            org.traincontrol.gui.widgets.StationCaption.PILL_AT_REST, new java.awt.Color(255, 0, 0));

        assertTrue(notReached.getRed() > notReached.getGreen() + 60,
            "red stopped being red on the pill - that is the timetable's \"not reached yet\", and it "
            + "was working before this rule was added beside it");
    }

    /**
     * An old "Point:" label is drawn as it was written, prefix and all (OB-344).
     *
     * A layout served by the Central Station still carries those labels and has no setup to turn them into captions,
     * so the grid draws a label as written unless autonomy has a caption for its square.  The page is one square built
     * in memory under a name no setup knows, so there is nothing to restore.
     *
     * MUTATION: putting back the branch that stripped the "Point:" prefix fails this with "Bahnhof".
     *
     * @throws Exception if Swing cannot build the grid
     */
    @Test
    public void testAnOldPointLabelIsDrawnAsWritten() throws Exception
    {
        final String written = "Point:Bahnhof";

        org.traincontrol.base.LayoutDiagram page =
            new org.traincontrol.base.LayoutDiagram("OB-344 scratch page", 1, 1, null, null);

        org.traincontrol.base.LayoutDiagramComponent label =
            new org.traincontrol.base.LayoutDiagramComponent(
                org.traincontrol.base.LayoutDiagramComponent.componentType.TEXT, 0, 0, 0, 0, 0, 0, null);

        label.setLabel(written);

        page.addComponent(label, 0, 0);

        assertNull(ui.autonomyCaptionAt(new org.traincontrol.automationui.TileGraph.TileKey(page.getName(), 0, 0)),
            "the square has an autonomy caption, so this is not the case the claim is about");

        java.awt.Container box = laidOut(page, 30);

        java.util.List<String> drawn = new java.util.ArrayList<>();

        for (java.awt.Component one : box.getComponents())
        {
            if (one instanceof org.traincontrol.gui.widgets.StationCaption)
            {
                drawn.add(((org.traincontrol.gui.widgets.StationCaption) one).getText());
            }
        }

        assertEquals(drawn.size() == 1 ? drawn.get(0) : String.valueOf(drawn), written,
            "an old Point: label is drawn without its prefix (OB-344)");
    }

    /**
     * How far apart two colors are, plainly.
     *
     * @param one a color
     * @param other another
     * @return the straight-line distance between them in RGB
     */
    private double distance(java.awt.Color one, java.awt.Color other)
    {
        int r = one.getRed() - other.getRed();
        int g = one.getGreen() - other.getGreen();
        int b = one.getBlue() - other.getBlue();

        return Math.sqrt(r * r + g * g + b * b);
    }

    /**
     * The four facing arrows are rotations of each other, not two pairs of different shapes (OB-116).
     *
     * In Segoe UI the right arrow U+25BA is half the height of the up arrow U+25B2, and those are the only horizontal
     * triangles it can draw (U+25B6 and U+25C0 come out as empty boxes), so the font was the answer.  This asserts the
     * property, a left or right arrow about as tall as an up or down one, not the code points.
     *
     * SKIPPED where the matched font is not installed, the same condition the code uses; a squat arrow beats an empty
     * box.
     *
     * MUTATION: putting U+25BA and U+25C4 back fails the height comparison at better than three to one.
     */
    @Test
    public void testTheFourFacingArrowsAreOneMatchedSet()
    {
        if (!"Segoe UI Symbol".equals(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT))
        {
            throw new SkipException("Segoe UI Symbol is not installed, so the pointers are correct "
                + "here - the arrows only have to match on a machine that can draw the triangles");
        }

        java.awt.Font font = new java.awt.Font(
            org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 100);

        java.awt.font.FontRenderContext frc =
            new java.awt.font.FontRenderContext(null, true, true);

        double up = height(font, frc, org.traincontrol.gui.widgets.StationCaption.ARROW_N);
        double down = height(font, frc, org.traincontrol.gui.widgets.StationCaption.ARROW_S);
        double east = height(font, frc, org.traincontrol.gui.widgets.StationCaption.ARROW_E);
        double west = height(font, frc, org.traincontrol.gui.widgets.StationCaption.ARROW_W);

        assertTrue(up > 1 && down > 1 && east > 1 && west > 1,
            "one of the arrows has no shape at all, which is what an empty box measures as: "
            + up + " " + down + " " + east + " " + west);

        assertEquals(east, west, 0.01, "the left and right arrows are different sizes");
        assertEquals(up, down, 0.01, "the up and down arrows are different sizes");

        // The complaint, as a number: a horizontal arrow half the height of a vertical one; a fifth either way is
        // allowed.
        assertTrue(east > up * 0.8 && east < up * 1.25,
            "the sideways arrow is " + east + " tall against the up arrow's " + up + ", which is the "
            + "mismatch OB-116 was filed for - it reads as too wide for its height");
    }

    /** How tall a string is drawn, ignoring the space the font reserves around it. */
    private double height(java.awt.Font font, java.awt.font.FontRenderContext frc, String text)
    {
        return font.createGlyphVector(frc, text.trim()).getVisualBounds().getHeight();
    }

    /** Java source with its comments stripped, so a scan passes on the call and not on a comment describing it. */
    private String withoutComments(String body)
    {
        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            char next = i + 1 < body.length() ? body.charAt(i + 1) : ' ';

            if (inLine)
            {
                if (c == '\n') { inLine = false; out.append(c); }
            }
            else if (inBlock)
            {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            }
            else if (c == '/' && next == '/')
            {
                inLine = true;
            }
            else if (c == '/' && next == '*')
            {
                inBlock = true;
                i++;
            }
            else
            {
                out.append(c);
            }
        }

        return out.toString();
    }

    /**
     * A square with a train running on it stays below the station captions and the address label over it (OB-117,
     * OB-259).
     *
     * A tile is opaque, so a tile lifted to the front for its train paints the caption out.  Nothing lifts now: the
     * panel paints every train after all its components (OB-159).  This holds the order a running train leaves: the
     * captions in build order, the address label, and the tile behind them all.
     *
     * MUTATION: lift the square of a running train again, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheTrainIconDoesNotPaintOutACaption() throws Exception
    {
        javax.swing.JPanel grid = new javax.swing.JPanel(null);

        // THREE captions, so an order among them that changed would show
        org.traincontrol.gui.widgets.StationCaption caption = new org.traincontrol.gui.widgets.StationCaption();
        org.traincontrol.gui.widgets.StationCaption second = new org.traincontrol.gui.widgets.StationCaption();
        org.traincontrol.gui.widgets.StationCaption third = new org.traincontrol.gui.widgets.StationCaption();

        org.traincontrol.gui.LayoutLabel tile = new org.traincontrol.gui.LayoutLabel(null, null, 30, null, false);
        javax.swing.JLabel address = new javax.swing.JLabel("86");

        grid.add(caption);
        grid.add(tile);
        grid.add(second);
        grid.add(address);
        grid.add(third);

        grid.setComponentZOrder(caption, 0);
        grid.setComponentZOrder(second, 1);
        grid.setComponentZOrder(third, 2);
        grid.setComponentZOrder(address, 3);

        tile.setAutonomyOverlay(new org.traincontrol.automationui.TileOverlay(
            org.traincontrol.automationui.TileOverlay.State.IDLE, true, true, null));

        javax.swing.SwingUtilities.invokeAndWait(() -> { });

        assertTrue(grid.getComponentZOrder(caption) < grid.getComponentZOrder(tile)
                && grid.getComponentZOrder(second) < grid.getComponentZOrder(tile)
                && grid.getComponentZOrder(third) < grid.getComponentZOrder(tile),
            "a square with a train running on it came in front of a station caption, and a tile is opaque: it paints the"
            + " name out and leaves its own background, the blank white space of OB-117");

        assertTrue(grid.getComponentZOrder(address) < grid.getComponentZOrder(tile), "a square with a train running on it"
            + " came in front of its address label, painting the number out (OB-259)");

        assertTrue(grid.getComponentZOrder(caption) < grid.getComponentZOrder(second)
                && grid.getComponentZOrder(second) < grid.getComponentZOrder(third),
            "the captions are no longer in the order they were built");
    }

    /**
     * An empty caption sits over the middle of its square, not at the left of it (OB-118).
     *
     * The placement shifts left by half the caption's overflow past one tile, which is NEGATIVE for a caption narrower
     * than its square and must not be clamped to zero: the dash of an empty station is narrow, the name that replaces
     * it is wide, and both must be centered.
     *
     * MUTATION: restoring `Math.max(0, (wide - tile) / 2)` and subtracting it fails the dash case while the
     * long-name case still passes.
     */
    @Test
    public void testAnEmptyCaptionIsCentredOnItsSquare()
    {
        int tile = 60;
        int backShift = tile;

        org.traincontrol.gui.widgets.StationCaption caption = new org.traincontrol.gui.widgets.StationCaption();

        caption.setPill(true);
        caption.setFont(new java.awt.Font(
            org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, tile / 2));

        caption.setText(org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY);
        caption.setTileGeometry(tile, backShift, 0, 0);

        int dash = caption.getBorder().getBorderInsets(caption).left;

        assertTrue(dash > backShift,
            "an empty caption starts at " + dash + " with its square beginning at " + backShift
            + ", so it is hard against the left edge of the square rather than over the middle of it");

        // And a LONG name still behaves as it did: centered by pulling left, clamped at the cell.
        caption.setText("Hauptbahnhof Nord 12");

        int name = caption.getBorder().getBorderInsets(caption).left;

        assertTrue(name < dash,
            "a caption too wide for its square is placed further right than a narrow one, which is "
            + "backwards - a wide one has to pull LEFT to stay centred");

        assertTrue(name >= 0, "a caption was placed before the start of its own cell");
    }

    /**
     * Captions are left off the track editor and nowhere else (FR-030).
     *
     * Three of the four combinations must keep them: a rule that hides too much stops the running diagram saying
     * where the trains are.
     *
     * MUTATION: dropping either half of the condition fails a row of this table.
     */
    @Test
    public void testCaptionsAreHiddenOnlyInTheTrackEditor()
    {
        assertTrue(org.traincontrol.gui.LayoutGrid.hidesStationCaptions(true, false, false),
            "the track diagram editor still draws station captions over the track being edited, "
            + "which is the window they are most in the way of");

        assertFalse(org.traincontrol.gui.LayoutGrid.hidesStationCaptions(true, true, false),
            "the AUTONOMY editor lost its captions. That is the window where stations are named, so "
            + "hiding them there removes the thing being worked on");

        assertFalse(org.traincontrol.gui.LayoutGrid.hidesStationCaptions(false, false, false),
            "the running diagram lost its captions, which is where they say what is standing where");

        assertFalse(org.traincontrol.gui.LayoutGrid.hidesStationCaptions(false, true, false),
            "a diagram outside any editor lost its captions");

        // And a page left out of autonomy overrides all of the above: it has no Point behind its caption, and neither
        // visibility switch reached it because both walk the registry, where an excluded page's caption is never
        // registered.
        for (boolean inEditor : new boolean[] { true, false })
        {
            for (boolean autonomyMode : new boolean[] { true, false })
            {
                assertTrue(
                    org.traincontrol.gui.LayoutGrid.hidesStationCaptions(inEditor, autonomyMode, true),
                    "a page excluded from autonomy still draws captions (inEditor=" + inEditor
                    + ", autonomyMode=" + autonomyMode + "). There is no Point behind them and no "
                    + "switch that can reach them");
            }
        }
    }

    /**
     * The caption choice remembers itself and rebuilds the diagram (FR-061).
     *
     * A caption's text is decided when the grid is BUILT, so a control that only repainted would appear to do nothing.
     * The control is one dropdown of four exclusive options; the two tick boxes the drawing code reads are set from
     * it, so driving the dropdown covers them too.
     *
     * MUTATION: dropping the onDiagramChanged call from the listener fails the rebuild assertion; dropping the
     * preference write fails the last one.
     *
     * @throws Exception on a failure to build the panel
     */
    @Test
    public void testTheCaptionChoiceRemembersItselfAndRebuilds() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null) throw new AssertionError("no autonomy setup in the fixture layout");

        final int[] rebuilds = {0};

        final org.traincontrol.gui.AutonomyEditorPanel[] panel =
            new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, null, () -> {}));

        panel[0].setOnDiagramChanged(() -> rebuilds[0]++);

        // WHETHER IT WAS STORED, not what the accessor answers: putting the old value back writes the default, which
        // is not the same as leaving it alone.  The key lives in the panel's own node.
        java.util.prefs.Preferences viewPrefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        boolean modeStored = viewPrefs.get("autonomyEditorCaptionMode", null) != null;

        final javax.swing.JComboBox<String> choice = panel[0].getCaptionChoice();

        final int was = choice.getSelectedIndex();

        // Any option but the current one; Parked rather than None, so the box the drawing code reads changes too.
        final int wanted = was == org.traincontrol.gui.AutonomyEditorPanel.CAPTIONS_PARKED
            ? org.traincontrol.gui.AutonomyEditorPanel.CAPTIONS_HOMES
            : org.traincontrol.gui.AutonomyEditorPanel.CAPTIONS_PARKED;

        javax.swing.SwingUtilities.invokeAndWait(() -> choice.setSelectedIndex(wanted));

        assertEquals(choice.getSelectedIndex(), wanted,
            "selecting a caption option did not take");

        assertEquals(panel[0].isShowingParkedTrains(),
            wanted == org.traincontrol.gui.AutonomyEditorPanel.CAPTIONS_PARKED,
            "the choice did not reach the flag the drawing code reads, so the menu says one thing and"
            + " the captions draw another");

        assertTrue(rebuilds[0] > 0,
            "the choice changed the setting without rebuilding the diagram. A caption's text is"
            + " decided when the grid is built, so it would appear to do nothing at all until"
            + " something else happened to rebuild it");

        final org.traincontrol.gui.AutonomyEditorPanel[] again =
            new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            again[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, null, () -> {}));

        int remembered = again[0].getCaptionChoice().getSelectedIndex();

        // Put it back before asserting, so a failure here does not leave the operator's setting changed.
        javax.swing.SwingUtilities.invokeAndWait(() -> choice.setSelectedIndex(was));

        if (!modeStored) viewPrefs.remove("autonomyEditorCaptionMode");

        assertEquals(remembered, wanted,
            "a new editor came up with the old choice, so it is not persisted and has to be set again"
            + " every time the window is opened");
    }

    /**
     * Parked Locs labels only the stations a locomotive is parked at (OB-312).
     *
     * MUTATION: put the placeholder back, and this fails.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testParkedLocsLabelsOnlyWhereATrainIsParked() throws Exception
    {
        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        assertNotNull(session, "precondition: no autonomy setup in the fixture layout");

        String pageName = null;

        for (org.traincontrol.automationui.TileGraph.TileKey station : session.getLabelledStationTiles())
        {
            if (pageName == null && session.getLocomotiveNameAt(station) == null) pageName = station.getPage();
        }

        assertNotNull(pageName, "precondition: every captioned station has a locomotive parked at it");

        final LayoutDiagram page = model.getLayout(pageName);

        java.util.prefs.Preferences viewPrefs = TrainControlUI.getPrefs();

        final boolean modeStored = viewPrefs.get("autonomyEditorCaptionMode", null) != null;

        final org.traincontrol.gui.layouteditor.LayoutEditor[] editor = new org.traincontrol.gui.layouteditor.LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            editor[0] = new org.traincontrol.gui.layouteditor.LayoutEditor(page, 30, ui, 0);
            editor[0].render();
            editor[0].setAutonomyMode(session);
        });

        int was = -1;

        final javax.swing.JComboBox<String>[] choice = new javax.swing.JComboBox[1];

        try
        {
            settleTheEditor();

            choice[0] = editor[0].getAutonomyPanel().getCaptionChoice();

            was = choice[0].getSelectedIndex();

            javax.swing.SwingUtilities.invokeAndWait(() ->
                choice[0].setSelectedIndex(org.traincontrol.gui.AutonomyEditorPanel.CAPTIONS_PARKED));

            settleTheEditor();

            final java.util.List<String> drawn = new java.util.ArrayList<>();

            javax.swing.SwingUtilities.invokeAndWait(() -> collectPills(editor[0].getContentPane(), drawn));

            assertFalse(drawn.isEmpty(), "precondition: the editor drew no station labels at all");

            assertFalse(drawn.contains(org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY), "Parked Locs labels a station"
                + " with no locomotive parked at it (OB-312): " + drawn);
        }
        finally
        {
            if (was >= 0)
            {
                final int back = was;

                javax.swing.SwingUtilities.invokeAndWait(() -> choice[0].setSelectedIndex(back));
            }

            if (!modeStored) viewPrefs.remove("autonomyEditorCaptionMode");

            javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
        }
    }

    /**
     * The Track Directions box is as wide as the Station Labels box above it in every language, and its widest item
     * does not widen the editor (OB-368).
     *
     * MUTATION: dropping the `choice.setMaximumSize` line in LayoutEditor fails the width assertion.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testTheDirectionsBoxKeepsTheLabelsBoxWidthInEveryLanguage() throws Exception
    {
        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        assertNotNull(session, "precondition: no autonomy setup in the fixture layout");

        String pageName = null;

        for (org.traincontrol.automationui.TileGraph.TileKey station : session.getLabelledStationTiles())
        {
            if (pageName == null) pageName = station.getPage();
        }

        assertNotNull(pageName, "precondition: no station in the fixture layout");

        final LayoutDiagram page = model.getLayout(pageName);

        final java.util.Locale was = java.util.Locale.getDefault();

        final int[] english = editorWidths(page, new java.util.Locale("en"));

        try
        {
            final int[] danish = editorWidths(page, new java.util.Locale("da"));

            assertEquals(english[1], english[0], "precondition: the two boxes differ in English");

            assertEquals(danish[1], danish[0], "the Track Directions box is not as wide as the Station Labels"
                + " box above it in Danish, so its widest phrase stretches the column");

            assertEquals(danish[2], english[2], "the editor window is wider in Danish than in English");

            assertTrue(danish[3] > danish[1], "the opened popup's list is no wider than the box, so a long phrase cannot be read in full");
        }
        finally
        {
            java.util.Locale.setDefault(was);
            org.traincontrol.util.I18n.setLocale(was);
        }
    }

    /**
     * Opens the autonomy editor in a language and returns the Station Labels box width, the Track Directions box
     * width, the packed window width and the width the Track Directions popup's list shows once opened.
     */
    private int[] editorWidths(LayoutDiagram page, java.util.Locale locale) throws Exception
    {
        java.util.Locale.setDefault(locale);
        org.traincontrol.util.I18n.setLocale(locale);

        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        final org.traincontrol.gui.layouteditor.LayoutEditor[] editor = new org.traincontrol.gui.layouteditor.LayoutEditor[1];

        final int[] widths = new int[4];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            editor[0] = new org.traincontrol.gui.layouteditor.LayoutEditor(page, 30, ui, 0);
            editor[0].render();
            editor[0].setAutonomyMode(session);
        });

        try
        {
            settleTheEditor();

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                editor[0].pack();
                editor[0].validate();

                org.traincontrol.gui.AutonomyEditorPanel panel = editor[0].getAutonomyPanel();

                widths[0] = panel.getCaptionChoice().getWidth();
                widths[1] = panel.getShowDirections().getWidth();
                widths[2] = editor[0].getWidth();
                javax.swing.JComboBox<String> directions = panel.getShowDirections();

                directions.showPopup();

                try
                {
                    javax.swing.plaf.basic.BasicComboPopup popup =
                        (javax.swing.plaf.basic.BasicComboPopup) directions.getAccessibleContext().getAccessibleChild(0);

                    popup.validate();

                    // The list's own width follows its widest phrase; the user sees the window it scrolls in
                    widths[3] = popup.getList().getParent().getWidth();
                }
                finally
                {
                    directions.hidePopup();
                }
            });

            return widths;
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
        }
    }

    /**
     * The editor's right column is as wide as its dropdowns, in English and in Danish: the Station Labels and Track
     * Directions boxes, Save, Cancel and the tool buttons share one left and one right edge (OB-375: "the overall
     * width should be the width of the dropdown").
     *
     * MUTATION: dropping `choice.setMinimumSize` in LayoutEditor widens Save and Cancel past the boxes in Danish.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testTheRightColumnIsAsWideAsItsDropdowns() throws Exception
    {
        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        assertNotNull(session, "precondition: no autonomy setup in the fixture layout");

        String pageName = null;

        for (org.traincontrol.automationui.TileGraph.TileKey station : session.getLabelledStationTiles())
        {
            if (pageName == null) pageName = station.getPage();
        }

        assertNotNull(pageName, "precondition: no station in the fixture layout");

        final LayoutDiagram page = model.getLayout(pageName);

        final java.util.Locale was = java.util.Locale.getDefault();

        try
        {
            for (String language : new String[] {"en", "da"})
            {
                java.util.Map<String, java.awt.Rectangle> column = rightColumn(page, new java.util.Locale(language),
                    new java.util.HashMap<String, Integer>());

                java.awt.Rectangle labels = column.get("Station Labels");

                assertTrue(column.size() >= 6, "precondition: the column holds the two boxes, Save, Cancel and the tool"
                    + " buttons, found " + column.keySet() + " in " + language);

                for (java.util.Map.Entry<String, java.awt.Rectangle> control : column.entrySet())
                {
                    java.awt.Rectangle r = control.getValue();

                    assertEquals(r.x, labels.x, control.getKey() + " starts at " + r.x + ", not at the Station Labels"
                        + " box's left edge " + labels.x + ", in " + language + ": " + column);

                    assertEquals(r.x + r.width, labels.x + labels.width, control.getKey() + " ends at "
                        + (r.x + r.width) + ", not at the Station Labels box's right edge " + (labels.x + labels.width)
                        + ", in " + language + ", so the column is wider than its dropdowns: " + column);
                }
            }
        }
        finally
        {
            java.util.Locale.setDefault(was);
            org.traincontrol.util.I18n.setLocale(was);
        }
    }

    /**
     * The tool buttons and Save and Cancel read in full in English and in Danish: none is narrower than its text needs
     * (OB-377).
     *
     * MUTATION: put `autosetup.ui.toolWhy` in messages_da.properties back to "Hvorfor kører det ikke?": red in
     * Danish.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testTheRightColumnsButtonsReadInFull() throws Exception
    {
        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        assertNotNull(session, "precondition: no autonomy setup in the fixture layout");

        String pageName = null;

        for (org.traincontrol.automationui.TileGraph.TileKey station : session.getLabelledStationTiles())
        {
            if (pageName == null) pageName = station.getPage();
        }

        assertNotNull(pageName, "precondition: no station in the fixture layout");

        final LayoutDiagram page = model.getLayout(pageName);

        final java.util.Locale was = java.util.Locale.getDefault();

        try
        {
            for (String language : new String[] {"en", "da"})
            {
                java.util.Map<String, Integer> needs = new java.util.LinkedHashMap<>();
                java.util.Map<String, java.awt.Rectangle> column = rightColumn(page, new java.util.Locale(language), needs);

                assertTrue(needs.size() >= 4, "precondition: Save, Cancel and the tool buttons were not found in "
                    + language + ": " + needs.keySet());

                for (java.util.Map.Entry<String, Integer> button : needs.entrySet())
                {
                    assertTrue(button.getValue() <= column.get(button.getKey()).width, "\"" + button.getKey()
                        + "\" needs " + button.getValue() + " pixels and has " + column.get(button.getKey()).width
                        + " in " + language + ", so it is cut short with \"...\"");
                }
            }
        }
        finally
        {
            java.util.Locale.setDefault(was);
            org.traincontrol.util.I18n.setLocale(was);
        }
    }

    /**
     * The right column ends where its widest text ends, in English and in Danish: no wider than its widest heading,
     * check box, radio button or button needs, plus {@code TEXT_ALLOWANCE} (MT-825).  The dropdowns' phrases do not
     * count: a long one is cut in the box and read in its list.
     *
     * MUTATION: pin the column in `LayoutEditor.setAutonomyMode` to 150 pixels again: red, as English's widest text
     * needs 144.
     *
     * @throws Exception from the editor
     */
    @Test
    public void testTheRightColumnEndsWhereItsTextEnds() throws Exception
    {
        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        assertNotNull(session, "precondition: no autonomy setup in the fixture layout");

        String pageName = null;

        for (org.traincontrol.automationui.TileGraph.TileKey station : session.getLabelledStationTiles())
        {
            if (pageName == null) pageName = station.getPage();
        }

        assertNotNull(pageName, "precondition: no station in the fixture layout");

        final LayoutDiagram page = model.getLayout(pageName);

        final java.util.Locale was = java.util.Locale.getDefault();

        try
        {
            for (String language : new String[] {"en", "da"})
            {
                java.util.Map<String, Integer> texts = new java.util.LinkedHashMap<>();
                java.util.Map<String, java.awt.Rectangle> column = rightColumn(page, new java.util.Locale(language),
                    new java.util.HashMap<String, Integer>(), texts);

                assertTrue(texts.size() >= 12, "precondition: the column's headings, check boxes, radio buttons and"
                    + " buttons were not found in " + language + ": " + texts.keySet());

                int widest = java.util.Collections.max(texts.values());

                int width = column.get("Station Labels").width;

                assertTrue(width <= widest + TEXT_ALLOWANCE, "the right column is " + width + " pixels wide in "
                    + language + " and its widest text needs " + widest + ", so it runs past its words: " + texts);
            }
        }
        finally
        {
            java.util.Locale.setDefault(was);
            org.traincontrol.util.I18n.setLocale(was);
        }
    }

    /** How far past its widest text the right column may run: a border's rounding, not a gap anyone sees. */
    private static final int TEXT_ALLOWANCE = 4;

    /**
     * Opens the autonomy editor in a language, packed, and returns the bounds of the right column's dropdowns and
     * buttons; each button's minimum width, what its text needs, goes into {@code needs}.
     */
    private java.util.Map<String, java.awt.Rectangle> rightColumn(LayoutDiagram page, java.util.Locale locale,
        final java.util.Map<String, Integer> needs) throws Exception
    {
        return rightColumn(page, locale, needs, new java.util.HashMap<String, Integer>());
    }

    /** The same, and what each visible label, check box, radio and button needs into {@code texts}. */
    private java.util.Map<String, java.awt.Rectangle> rightColumn(LayoutDiagram page, java.util.Locale locale,
        final java.util.Map<String, Integer> needs, final java.util.Map<String, Integer> texts) throws Exception
    {
        java.util.Locale.setDefault(locale);
        org.traincontrol.util.I18n.setLocale(locale);

        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        final org.traincontrol.gui.layouteditor.LayoutEditor[] editor = new org.traincontrol.gui.layouteditor.LayoutEditor[1];

        final java.util.Map<String, java.awt.Rectangle> column = new java.util.LinkedHashMap<>();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            editor[0] = new org.traincontrol.gui.layouteditor.LayoutEditor(page, 30, ui, 0);
            editor[0].render();
            editor[0].setAutonomyMode(session);
        });

        try
        {
            settleTheEditor();

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                editor[0].pack();
                editor[0].validate();

                java.awt.Container pane = editor[0].getContentPane();
                org.traincontrol.gui.AutonomyEditorPanel panel = editor[0].getAutonomyPanel();

                column.put("Station Labels", inPane(panel.getCaptionChoice(), pane));
                column.put("Track Directions", inPane(panel.getShowDirections(), pane));

                // Save and Cancel: the visible push buttons of the form that holds the boxes' column
                java.awt.Container form = panel.getCaptionChoice().getParent().getParent();

                for (java.awt.Component c : form.getComponents())
                {
                    if (c instanceof javax.swing.JButton && c.isVisible())
                    {
                        column.put(((javax.swing.JButton) c).getText(), inPane(c, pane));
                        needs.put(((javax.swing.JButton) c).getText(), c.getMinimumSize().width);
                    }
                }

                collectToolButtons(panel, pane, column, needs);

                int columnX = panel.getCaptionChoice().getParent().getX();

                for (java.awt.Component c : form.getComponents())
                {
                    if (c.getX() >= columnX - 2) collectTexts(c, texts);
                }
            });

            return column;
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> editor[0].dispose());
        }
    }

    private static java.awt.Rectangle inPane(java.awt.Component c, java.awt.Container pane)
    {
        return javax.swing.SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), pane);
    }

    /** The panel's visible push and toggle buttons (not its check boxes or radio buttons). */
    private static void collectToolButtons(java.awt.Container in, java.awt.Container pane,
        java.util.Map<String, java.awt.Rectangle> out, java.util.Map<String, Integer> needs)
    {
        for (java.awt.Component c : in.getComponents())
        {
            if (!c.isVisible()) continue;

            if (c instanceof javax.swing.JButton || c.getClass() == javax.swing.JToggleButton.class)
            {
                out.put(((javax.swing.AbstractButton) c).getText(), inPane(c, pane));
                needs.put(((javax.swing.AbstractButton) c).getText(), c.getMinimumSize().width);
            }
            else if (c instanceof java.awt.Container && !(c instanceof javax.swing.JComboBox))
            {
                collectToolButtons((java.awt.Container) c, pane, out, needs);
            }
        }
    }

    /** Each visible label's and button's text and the width it needs, dropdowns left out. */
    private static void collectTexts(java.awt.Component c, java.util.Map<String, Integer> out)
    {
        if (!c.isVisible() || c instanceof javax.swing.JComboBox) return;

        String text = c instanceof javax.swing.AbstractButton ? ((javax.swing.AbstractButton) c).getText()
            : c instanceof javax.swing.JLabel ? ((javax.swing.JLabel) c).getText() : null;

        if (text != null && !text.isEmpty()) out.put(text, c.getMinimumSize().width);
        else if (c instanceof java.awt.Container && !(c instanceof javax.swing.JScrollPane))
        {
            for (java.awt.Component child : ((java.awt.Container) c).getComponents()) collectTexts(child, out);
        }
    }

    /** Lets an editor's posted builds and its tiles finish. */
    private void settleTheEditor() throws Exception
    {
        for (int i = 0; i < 10; i++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

        final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);

        javax.swing.SwingUtilities.invokeLater(() -> ui.whenTilesSettled(settled::countDown));

        settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

        Thread.sleep(500);

        for (int i = 0; i < 10; i++) javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /** The text of every station label drawn as a pill under a container. */
    private static void collectPills(java.awt.Container in, java.util.List<String> out)
    {
        for (java.awt.Component c : in.getComponents())
        {
            if (c instanceof org.traincontrol.gui.widgets.StationCaption && ((org.traincontrol.gui.widgets.StationCaption) c).isPill())
            {
                out.add(((org.traincontrol.gui.widgets.StationCaption) c).getText());
            }

            if (c instanceof java.awt.Container) collectPills((java.awt.Container) c, out);
        }
    }

    /**
     * A caption may move itself.  It may not move anything else (OB-115): the text labels' vertical alignment must not
     * drift when a caption becomes a pill.
     *
     * **Nothing about the mechanism is visible in the caption code.**  GridBagLayout lines every `BASELINE_LEADING`
     * component up on one baseline for the row, so a caption anchored that way would move the row when given a pill
     * and a smaller font.  Captions are anchored NORTHWEST, out of that ballot.  The test changes the one thing a
     * caption changes on its own, its TEXT, and insists that nothing else has moved a pixel, with no golden file.
     *
     * MUTATION: anchoring captions BASELINE_LEADING again fails this.
     */
    @Test
    public void testACaptionNeverMovesAnythingElse() throws Exception
    {
        java.util.List<String> pages = model.getLayoutList();

        assertFalse(pages.isEmpty(), "no pages - is test/test_layout present?");

        java.awt.Container box = null;
        org.traincontrol.gui.widgets.StationCaption caption = null;

        // The first page that has a caption on it: a page with none cannot fail this.
        for (String name : pages)
        {
            java.awt.Container built = laidOut(model.getLayout(name), 30);

            for (java.awt.Component one : built.getComponents())
            {
                if (one instanceof org.traincontrol.gui.widgets.StationCaption
                    && ((org.traincontrol.gui.widgets.StationCaption) one).isPill())
                {
                    box = built;
                    caption = (org.traincontrol.gui.widgets.StationCaption) one;
                    break;
                }
            }

            if (caption != null) break;
        }

        assertNotNull(caption,
            "no page in the fixture layout draws a station caption, so this test is not exercising "
            + "the thing it is about");

        java.util.Map<java.awt.Component, java.awt.Rectangle> was = new java.util.LinkedHashMap<>();

        for (java.awt.Component one : box.getComponents())
        {
            if (one instanceof org.traincontrol.gui.widgets.StationCaption
                && ((org.traincontrol.gui.widgets.StationCaption) one).isPill())
            {
                continue;
            }

            was.put(one, one.getBounds());
        }

        assertTrue(was.size() > 10, "only " + was.size() + " components to watch, which is too few "
            + "for this page to be the diagram it is meant to be");

        // The dash becomes a train and the caption gets wide, and TALLER, which is the half that matters: OB-115 was a
        // caption whose HEIGHT changed, and with a baseline anchor a height is a vote on where the row sits.  Widening
        // alone passes with the old anchor, so the font changes too.
        final org.traincontrol.gui.widgets.StationCaption changing = caption;
        final java.awt.Container laid = box;

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            changing.setText("065 001-0 \u25BA");
            changing.setFont(changing.getFont().deriveFont(
                changing.getFont().getSize2D() * 2f));

            laid.doLayout();
            laid.doLayout();
        });

        int moved = 0;

        StringBuilder detail = new StringBuilder();

        for (java.util.Map.Entry<java.awt.Component, java.awt.Rectangle> one : was.entrySet())
        {
            if (one.getKey().getBounds().equals(one.getValue())) continue;

            moved++;

            if (moved <= 5)
            {
                String text = one.getKey() instanceof javax.swing.JLabel
                    ? ((javax.swing.JLabel) one.getKey()).getText() : "(a tile)";

                detail.append("\n  ").append(text).append(": ").append(one.getValue())
                    .append(" -> ").append(one.getKey().getBounds());
            }
        }

        assertEquals(moved, 0,
            "putting a train's name on one caption moved " + moved + " other things on the diagram. "
            + "A caption is drawn on top of a railway somebody has arranged; it does not get to "
            + "rearrange it." + detail);
    }

    /**
     * Changing what trains may do at a station leaves its caption where it is (OB-310).
     *
     * Each answer under Station goes through `setStation(tile, true)`, which must place a caption only where the
     * station has none: `placeCaption` MOVES one it already has, so every change would send the name to the next
     * free square beside the platform and the next change back, from on end to flat.  The rename has the same rule
     * (MT-116).
     *
     * Every named station with a caption, both changes, through `buildAutonomyTileMenu`; then a station captioned on
     * north-south rails, drawn after the answer that lets no train pass, where a second move could put it back flat.
     *
     * MUTATION: dropping the "has none" test from `AutonomyEditorPanel.setStation` fails both halves.
     */
    @Test
    public void testChangingWhatTrainsMayDoLeavesTheCaptionWhereItIs() throws Exception
    {
        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        assertNotNull(session, "no autonomy session over the fixture layout");

        final String neither = org.traincontrol.util.I18n.t("autosetup.ui.menuNeither");
        final String stop = org.traincontrol.util.I18n.t("autosetup.ui.menuCanStop");

        final org.json.JSONObject asItWas = session.snapshotSetup();

        java.util.List<String> moved = new java.util.ArrayList<>();
        java.util.List<String> said = new java.util.ArrayList<>();

        int stations = 0;

        org.traincontrol.automationui.TileGraph.TileKey onEnd = null;
        org.traincontrol.automationui.TileGraph.TileKey onEndCaption = null;

        for (org.traincontrol.automationui.TileGraph.TileKey station : session.getLabelledStationTiles())
        {
            if (onEnd != null || !session.getStore().isStation(station)) continue;

            LayoutDiagram page = model.getLayout(station.getPage());

            for (org.traincontrol.automationui.TileGraph.TileKey where : session.captionsFor(station))
            {
                if (page != null && !where.equals(station) && org.traincontrol.gui.LayoutGrid.runsNorthSouth(
                    page.getComponent(where.getX(), where.getY())))
                {
                    onEnd = station;
                    onEndCaption = where;
                }
            }
        }

        assertNotNull(onEnd, "precondition: no station in the fixture is captioned beside itself on north-south rails,"
            + " which is the shape of TopMainR1");

        String drawnFirst = drawnOnEnd(onEnd);

        assertEquals(drawnFirst, "on end", "precondition: " + onEnd + "'s caption on " + onEndCaption + " is not drawn"
            + " on end to begin with");

        String drawnShut = null;

        try
        {
            for (org.traincontrol.automationui.TileGraph.TileKey station
                : new java.util.ArrayList<>(session.getLabelledStationTiles()))
            {
                String name = session.getStore().getPointName(station);

                if (!session.getStore().isStation(station) || name == null || name.trim().isEmpty()) continue;

                stations++;

                final java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> was =
                    new java.util.LinkedHashSet<>(session.captionsFor(station));

                choose(station, neither, said);

                if (!session.captionsFor(station).equals(was))
                {
                    moved.add(name + ": " + was + " -> " + session.captionsFor(station) + " at " + neither);
                }

                if (station.equals(onEnd)) drawnShut = drawnOnEnd(station);

                choose(station, stop, said);

                if (!session.captionsFor(station).equals(was))
                {
                    moved.add(name + ": " + was + " -> " + session.captionsFor(station) + " at " + stop);
                }
            }
        }
        finally
        {
            session.restoreSetup(asItWas);
        }

        assertTrue(stations > 5, "precondition: only " + stations + " named stations with a caption in the fixture");

        assertEquals(said, new java.util.ArrayList<String>(), "choosing under Station put up a dialog");

        assertEquals(drawnShut, "on end", onEnd + "'s caption is not drawn on end once it is set to " + neither
            + " (OB-310)");

        assertEquals(moved, new java.util.ArrayList<String>(), "changing what trains may do at a station moved its"
            + " caption to another square - so a name drawn on end beside north-south rails lies flat on the next"
            + " square, and goes back on the next change (OB-310)");
    }

    /**
     * Chooses one answer under Station on a square's right-click menu, as the track diagram builds it.
     *
     * @param station the square
     * @param answer the item's text
     * @param said the text of any dialog that came up, which is closed
     */
    private void choose(final org.traincontrol.automationui.TileGraph.TileKey station, final String answer,
        final java.util.List<String> said) throws Exception
    {
        final String[] missing = new String[1];

        final Runnable click = () ->
        {
            javax.swing.JPopupMenu menu = ui.buildAutonomyTileMenu(station);

            if (menu == null)
            {
                missing[0] = "no menu for " + station;

                return;
            }

            for (java.awt.Component part : menu.getComponents())
            {
                if (!(part instanceof javax.swing.JMenu)) continue;

                for (java.awt.Component item : ((javax.swing.JMenu) part).getMenuComponents())
                {
                    if (item instanceof javax.swing.AbstractButton
                        && answer.equals(((javax.swing.AbstractButton) item).getText()))
                    {
                        ((javax.swing.AbstractButton) item).doClick();

                        return;
                    }
                }
            }

            missing[0] = "no \"" + answer + "\" under Station for " + station;
        };

        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();

        Thread closer = new Thread(() ->
        {
            while (!done.get())
            {
                try
                {
                    Thread.sleep(200);
                }
                catch (InterruptedException e)
                {
                    return;
                }

                for (java.awt.Window w : java.awt.Window.getWindows())
                {
                    if (w instanceof javax.swing.JDialog && w.isShowing())
                    {
                        said.add(String.valueOf(((javax.swing.JDialog) w).getTitle()));

                        javax.swing.SwingUtilities.invokeLater(w::dispose);
                    }
                }
            }
        }, "OB-310 dialog closer");

        closer.setDaemon(true);
        closer.start();

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(click);
        }
        finally
        {
            done.set(true);
        }

        assertNull(missing[0], missing[0]);
    }

    /**
     * How a station's caption is drawn on a freshly built grid of its page.
     *
     * @param station the square
     * @return "on end", "flat", or what was found instead
     */
    private String drawnOnEnd(org.traincontrol.automationui.TileGraph.TileKey station) throws Exception
    {
        java.awt.Container box = laidOut(model.getLayout(station.getPage()), 30);

        java.util.List<String> found = new java.util.ArrayList<>();

        for (java.awt.Component one : box.getComponents())
        {
            if (one instanceof org.traincontrol.gui.widgets.StationCaption && ui.getLayoutStations(station).contains(one))
            {
                found.add(((org.traincontrol.gui.widgets.StationCaption) one).isRotated() ? "on end" : "flat");
            }
        }

        return found.size() == 1 ? found.get(0) : String.valueOf(found);
    }

    /**
     * A grid for one page, built and laid out, with its tile images waited for: a tile's preferred size depends on
     * whether its icon has arrived, so an unwaited grid measures differently every time.
     */
    private java.awt.Container laidOut(final LayoutDiagram page, final int size) throws Exception
    {
        final javax.swing.JPanel panel = new javax.swing.JPanel();
        final org.traincontrol.gui.LayoutGrid[] grid = new org.traincontrol.gui.LayoutGrid[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            grid[0] = new org.traincontrol.gui.LayoutGrid(page, size, panel, null, true, ui));

        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        javax.swing.SwingUtilities.invokeLater(() -> ui.whenTilesSettled(settled::countDown));

        assertTrue(settled.await(30, java.util.concurrent.TimeUnit.SECONDS),
            "the tiles never finished decoding, so the bounds below are of a half-built grid");

        final java.awt.Container box = grid[0].getContainer();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            box.setSize(box.getPreferredSize());
            box.doLayout();
            box.doLayout();
        });

        return box;
    }

    /**
     * A caption sits clear of its rail at every tile size, whichever way the rail runs.
     *
     * A caption lying across the track it names would cover its own subject.  The caption starts past the rail, and
     * not so far past it that it stops reading as that track's label; the second half stops the offset being
     * increased a few pixels at a time until the captions float free.  Line heights are three fifths of the tile.
     *
     * MUTATION: returning a fixed number of pixels from captionOffset fails this at every size but one; dropping the
     * nudge fails the first assertion at 20px.
     */
    @Test
    public void testACaptionSitsRightAtEveryTileSize()
    {
        for (int tile : new int[] { 20, 30, 40, 60, 80 })
        {
            int line = Math.round(tile * 0.6f);

            int rail = tile / 2;

            int offset = org.traincontrol.gui.widgets.StationCaption.captionOffset(tile, line);

            // PAST the rail, so the caption is beside the track rather than on it.
            assertTrue(offset > rail,
                "at " + tile + "px a caption starts at " + offset + " and the rail is at " + rail
                + ", so it is drawn over the track it names rather than beside it");

            // And not so far past that it has left its own square behind.  A cap, not a restatement of the offset: a
            // test computing the code's own expression agrees with it by construction.
            assertTrue(offset <= tile + line,
                "at " + tile + "px a caption starts " + offset + " past its square's edge, which is "
                + "far enough from the tile it belongs to that it reads as a label for the next one");

            assertTrue(offset >= 0,
                "at " + tile + "px a caption is pushed off the leading edge of its own square");
        }

        // And the degenerate case: a caption whose font has not been set yet asks this.
        assertEquals(org.traincontrol.gui.widgets.StationCaption.captionOffset(40, 0), 0,
            "a caption with no line height is given an offset, which is an opinion about a label "
            + "whose size is not known yet");
    }

    /**
     * The four arrows a caption can end in can all be drawn in the font captions are drawn in.
     *
     * **It asks StationCaption for both halves, and that is the point.**  A test naming its own font and codepoints
     * watches something other than the code: OB-116 fixed the arrows by changing the FONT, not the character.
     * A tofu box on a diagram is the failure this stops.
     *
     * MUTATION: setting either arrow constant to a codepoint LABEL_FONT cannot draw fails this.
     */
    @Test
    public void testCaptionArrowsCanBeDrawn()
    {
        java.awt.Font font = new java.awt.Font(
            org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 20);

        String[] arrows = {
            org.traincontrol.gui.widgets.StationCaption.ARROW_N,
            org.traincontrol.gui.widgets.StationCaption.ARROW_S,
            org.traincontrol.gui.widgets.StationCaption.ARROW_E,
            org.traincontrol.gui.widgets.StationCaption.ARROW_W
        };

        for (String arrow : arrows)
        {
            assertTrue(font.canDisplay(arrow.codePointAt(0)),
                "the caption font " + org.traincontrol.gui.widgets.StationCaption.LABEL_FONT + " has no glyph "
                + "for U+" + Integer.toHexString(arrow.codePointAt(0)) + ", so every train facing that "
                + "way draws a tofu box on the diagram");
        }
    }

    /**
     * A rotated caption's arrow still points where the train is going.
     *
     * Turning the drawing turns everything in it, so an arrow keeps its meaning only if the GLYPH is chosen for where
     * it ends up: a quarter turn anticlockwise sends right to up, so a caption that means north is drawn with the east
     * glyph.  Get that backwards and every caption sits in the right place with every train appearing to run the
     * other way.  Decided by painting and reading the ink: a triangle pointing up has its widest row at the BOTTOM of
     * its ink, whichever pair of glyphs the installed fonts give.
     *
     * MUTATION: cycling the arrows the other way - north to west rather than north to east - fails both halves; not
     * rotating them at all fails both as well.
     */
    @Test
    public void testARotatedArrowStillPointsWhereTheTrainIsGoing() throws Exception
    {
        int up = widestInkRow(org.traincontrol.gui.widgets.StationCaption.ARROW_N);
        int down = widestInkRow(org.traincontrol.gui.widgets.StationCaption.ARROW_S);

        // 0 is the top of the ink, 100 the bottom.
        assertTrue(up > 55,
            "a caption meaning NORTH draws an arrow whose widest part is " + up + "% down its ink, so "
            + "it is not a triangle pointing up - the rotation has been applied to the arrow the "
            + "wrong way round, or not at all, and every train on a vertical track now appears to be "
            + "running the other way");

        assertTrue(down < 45,
            "a caption meaning SOUTH draws an arrow whose widest part is " + down + "% down its ink, "
            + "so it is not a triangle pointing down");

        assertTrue(up > down,
            "north and south draw the same arrow once rotated, so the caption says nothing about "
            + "which way the train is going");
    }

    /**
     * A rotated caption is stood on end, and takes the mouse only where it is drawn.
     *
     * Painting, sizing and hit-testing all ask `pillBounds` for one rectangle: a flat pill painted inside its insets
     * but hit-tested over the whole component swallows every click on the tile it borrowed centring room from, and a
     * rotated caption borrows a whole ROW, so it would eat a click on the square above.
     *
     * MUTATION: having `contains` fall back to `super.contains` fails the last assertion; returning the flat preferred
     * size when rotated fails the first.
     */
    @Test
    public void testARotatedCaptionStandsOnEnd() throws Exception
    {
        org.traincontrol.gui.widgets.StationCaption pill = onEnd("Ostbahnhof");

        java.awt.Dimension size = pill.getPreferredSize();

        assertTrue(size.height > size.width,
            "a rotated caption asks for a box " + size.width + " by " + size.height + ", which is "
            + "wider than it is tall - it is being measured along its text as though it were flat, "
            + "and will draw itself straight out of the bottom of the room it was given");

        org.traincontrol.gui.widgets.StationCaption flat = new org.traincontrol.gui.widgets.StationCaption();

        flat.setPill(true);
        flat.setFont(pill.getFont());
        flat.setText("Ostbahnhof");

        java.awt.Dimension across = flat.getPreferredSize();

        assertTrue(across.width > across.height,
            "a caption that was NOT rotated is taller than it is wide, so the rotation is being "
            + "applied to every caption rather than to the ones on north-south track");

        // The pill is one line THICK, wherever the insets put it: a point well past that is the diagram's.
        int left = pill.getInsets().left;

        assertTrue(pill.contains(left + pill.lineHeight() / 2, pill.getInsets().top + 5),
            "the middle of the drawn pill does not take the mouse, so a caption cannot be clicked");

        assertFalse(pill.contains(left + pill.lineHeight() + 8, pill.getInsets().top + 5),
            "a rotated caption takes the mouse well past the pill it draws, on track that belongs to "
            + "the diagram underneath - which is how a switch stops throwing when it is clicked");
    }

    /**
     * A rotated caption carrying one arrow, painted.
     *
     * @param arrow the direction the caption means
     * @return where the widest row of ink falls, 0 at the top of the ink and 100 at the bottom
     */
    private int widestInkRow(String arrow) throws Exception
    {
        org.traincontrol.gui.widgets.StationCaption pill = onEnd(arrow);

        java.awt.Dimension size = pill.getPreferredSize();

        pill.setBounds(0, 0, size.width, size.height);

        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        pill.paint(g);

        g.dispose();

        int[] perRow = new int[size.height];

        int first = -1, last = -1;

        for (int y = 0; y < size.height; y++)
        {
            for (int x = 0; x < size.width; x++)
            {
                int argb = image.getRGB(x, y);

                // The TEXT, which is white on a navy pill, not the pill itself.
                if (((argb >>> 24) & 0xFF) > 200 && ((argb >> 16) & 0xFF) > 200
                    && ((argb >> 8) & 0xFF) > 200 && (argb & 0xFF) > 200)
                {
                    perRow[y]++;
                }
            }

            if (perRow[y] > 0)
            {
                if (first < 0) first = y;

                last = y;
            }
        }

        assertTrue(first >= 0 && last > first,
            "nothing was drawn for " + arrow + " at all, so this measures an empty picture");

        int widest = first;

        for (int y = first; y <= last; y++)
        {
            if (perRow[y] > perRow[widest]) widest = y;
        }

        return (widest - first) * 100 / (last - first);
    }

    /** A caption stood on end, colored so its text can be told from its pill. */
    private org.traincontrol.gui.widgets.StationCaption onEnd(String text)
    {
        org.traincontrol.gui.widgets.StationCaption pill = new org.traincontrol.gui.widgets.StationCaption();

        pill.setPill(true);
        pill.setRotated(true);
        pill.setFont(new java.awt.Font(
            org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 24));
        pill.setBackground(org.traincontrol.gui.widgets.StationCaption.PILL);
        pill.setForeground(java.awt.Color.WHITE);
        pill.setText(text);
        pill.setTileGeometry(60, 0, 60,
            org.traincontrol.gui.widgets.StationCaption.captionOffset(60, pill.lineHeight()));

        return pill;
    }

    /**
     * A caption is an oval, not a rectangle of color (FR-028).
     *
     * The pill is painted by the label itself, so the label has to stay TRANSPARENT: an opaque JLabel fills its own
     * rectangle first and the rounded shape is drawn inside a square of the same color.  The corner is the assertion.
     *
     * MUTATION: setting the label opaque, or filling a rectangle instead of a round one, fails this.
     */
    @Test
    public void testACaptionIsAnOval() throws Exception
    {
        org.traincontrol.gui.widgets.StationCaption pill = new org.traincontrol.gui.widgets.StationCaption();

        pill.setPill(true);
        pill.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 20));
        pill.setText("EN57-203");
        pill.setBackground(org.traincontrol.gui.widgets.StationCaption.PILL_AT_REST);
        pill.setForeground(java.awt.Color.WHITE);

        java.awt.Dimension size = pill.getPreferredSize();

        pill.setBounds(0, 0, size.width, size.height);

        BufferedImage image = new BufferedImage(size.width, size.height,
            BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = image.createGraphics();

        pill.paint(g);

        g.dispose();

        assertEquals((image.getRGB(0, 0) >>> 24) & 0xFF, 0,
            "the top left corner is painted, so the caption is a rectangle with rounded ends drawn "
            + "inside it rather than an oval - which is what a JLabel does when it is left opaque");

        assertTrue(((image.getRGB(size.width / 2, size.height / 2) >>> 24) & 0xFF) > 0,
            "nothing is painted in the middle of the caption, so there is no pill at all");

        assertFalse(pill.isOpaque(),
            "the caption is opaque, so Swing fills its rectangle before the pill is drawn - the "
            + "corner check above only passes today because the fill happens to be transparent");
    }

    /**
     * Which way the rails run, for the square a caption lands on (FR-028).
     *
     * The decision is made from the tile's geometry: a straight rail answers one way, and the same rail turned a
     * quarter answers the other.  Deliberately not asserting WHICH orientation is which: the rotation convention is the
     * layout format's (a diagram tile rotates by (4 - o)), and pinning it would fail the day the format changed.
     *
     * MUTATION: making runsNorthSouth return a constant fails this whichever constant it returns.
     */
    @Test
    public void testACaptionKnowsWhichWayTheRailsRun() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("orientation", 4, 4, null, null);

        page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT,
            1, 1, 0, 0, 0, 0, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);

        page.addComponent(org.traincontrol.base.LayoutDiagramComponent.componentType.STRAIGHT,
            2, 2, 1, 0, 0, 0, org.traincontrol.base.Accessory.accessoryDecoderType.MM2, null);

        boolean flat = org.traincontrol.gui.LayoutGrid.runsNorthSouth(page.getComponent(1, 1));
        boolean turned = org.traincontrol.gui.LayoutGrid.runsNorthSouth(page.getComponent(2, 2));

        assertNotEquals(flat, turned,
            "a straight rail and the same rail turned a quarter are given the same answer, so the "
            + "caption lands in the same place on both - and one of those two is wrong");

        assertFalse(org.traincontrol.gui.LayoutGrid.runsNorthSouth(null),
            "a square with nothing on it is reported as running north to south, which is an opinion "
            + "about a square that has no rails at all");
    }

    /**
     * A sensor's caption knows which way its rails run whatever the sensor was reporting when the layout was saved
     * (OB-263).
     *
     * The state a Central Station saves for a sensor tile is whether a train stood on it, and state 1 found no ports at
     * all: a vertical station saved occupied read as running east to west and its caption lay flat.
     *
     * MUTATION: ask the port table with the sensor's saved state again, and this fails.
     */
    @Test
    public void testASensorSavedOccupiedKnowsWhichWayItsRailsRun() throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("occupied", 4, 4, null, null);

        org.traincontrol.base.LayoutDiagramComponent.componentType sensor =
            org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK;

        for (int o = 0; o < 2; o++)
        {
            for (int state = 0; state < 2; state++)
            {
                page.addComponent(sensor, o, state, o, state, 0, 0, org.traincontrol.base.Accessory.accessoryDecoderType.MM2,
                    null);
            }
        }

        boolean flatFree = org.traincontrol.gui.LayoutGrid.runsNorthSouth(page.getComponent(0, 0));
        boolean turnedFree = org.traincontrol.gui.LayoutGrid.runsNorthSouth(page.getComponent(1, 0));

        assertNotEquals(flatFree, turnedFree, "precondition: a free sensor and the same sensor turned a quarter are given"
            + " the same answer, so this cannot tell the two apart");

        assertEquals(org.traincontrol.gui.LayoutGrid.runsNorthSouth(page.getComponent(0, 1)), flatFree, "a sensor saved"
            + " occupied is said to run a different way from the same sensor saved free (OB-263)");

        assertEquals(org.traincontrol.gui.LayoutGrid.runsNorthSouth(page.getComponent(1, 1)), turnedFree, "a turned"
            + " sensor saved occupied is said to run a different way from the same sensor saved free, so its station's"
            + " caption lies flat (OB-263)");
    }

    /**
     * The autonomy menu for a square opens with that square's name, disabled (OB-112).
     *
     * Checked on the menu itself, through `buildAutonomyTileMenu`, the main window's door to the one builder the editor
     * uses.  The assertion is about the FIRST component: a heading that is not at the top is not a heading.
     *
     * MUTATION: removing the `title` call from buildTileMenu fails this; so does putting tidy()'s heading test back
     * the way it was.
     */
    @Test
    public void testTheAutonomyMenuOpensWithTheSquaresName() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null || session.getReducer() == null)
        {
            throw new AssertionError("the fixture layout has no autonomy setup loaded");
        }

        org.traincontrol.automationui.TileGraph.TileKey point = null;
        String expectedName = null;

        for (org.traincontrol.automationui.TileGraph.TileKey tile
            : session.getReducer().getPoints().keySet())
        {
            String name = session.getStore().getPointName(tile);

            if (name != null && !name.trim().isEmpty())
            {
                point = tile;
                expectedName = name.trim();
                break;
            }
        }

        assertNotNull(point, "no named point in the fixture, so there is nothing to head a menu with");

        final org.traincontrol.automationui.TileGraph.TileKey square = point;
        final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> menu[0] = ui.buildAutonomyTileMenu(square));

        assertNotNull(menu[0], "no menu at all for a named point");
        assertTrue(menu[0].getComponentCount() > 0, "the menu came out empty");

        java.awt.Component first = menu[0].getComponent(0);

        assertTrue(first instanceof javax.swing.JMenuItem,
            "the first thing on the menu is a " + first.getClass().getSimpleName()
            + ", not an item - so whatever heads this menu, it is not a heading");

        javax.swing.JMenuItem heading = (javax.swing.JMenuItem) first;

        assertFalse(heading.isEnabled(),
            "the first item on the menu is live, so it is a command and not a name. A heading has to "
            + "be unclickable or it is one more thing to press by accident");

        // Compared against the name read independently above, not against session.describeTile(square): both sides
        // would come from the method under test, so a broken describeTile would move with the heading and still match.
        assertEquals(heading.getText(), expectedName,
            "the menu does not open with the name of the square it is about. That is OB-112, and it "
            + "is the assertion that says whether the fault is in this code or in the build that was "
            + "running when it was reported");

        // And the shape tidy() is otherwise there for (OB-054): a menu of independent blocks, each leaving its divider
        // behind when it has nothing to offer, reads "a heading, a divider, nothing at all, another divider".
        java.awt.Component last = menu[0].getComponent(menu[0].getComponentCount() - 1);

        assertFalse(menu[0].getComponent(0) instanceof javax.swing.JSeparator,
            "the menu starts with a divider, which has nothing above it to separate");

        assertFalse(last instanceof javax.swing.JSeparator,
            "the menu ends with a divider, which has nothing below it to separate");

        for (int at = 1; at < menu[0].getComponentCount(); at++)
        {
            boolean two = menu[0].getComponent(at) instanceof javax.swing.JSeparator
                && menu[0].getComponent(at - 1) instanceof javax.swing.JSeparator;

            assertFalse(two, "two dividers in a row at item " + at + " - an empty band between two "
                + "lines, which is the shape a section with nothing to offer leaves behind");
        }
    }

    /**
     * A diagram already drawn is not taken away 120ms later either, which is the half OB-109 left behind (MT-273).
     *
     * **Why the editor and not the viewer.** The editor rebuilds the whole grid after every placement, and the tile
     * just placed is the decode that keeps the outstanding count above zero, so the grace timer fires on the commonest
     * action there.
     *
     * **The rule.** A rebuild at the SAME tile size is not an arrival: every square is a cache hit, so the page is
     * complete when mounted.  A rebuild at a DIFFERENT size is one: the size is part of the cache key, so the page
     * really does come in square by square.  The second half is the control that keeps that distinction alive.
     *
     * MUTATION: remove the `sameSizeRebuild` early return and the first assertion fails; make it unconditional on
     * `replacing` and the control fails instead.
     */
    @Test
    public void testARebuiltDiagramIsNotTakenAwayAMomentLater() throws Exception
    {
        final LayoutDiagram layout = model.getLayout(model.getLayoutList().get(0));

        final javax.swing.JPanel panel = new javax.swing.JPanel();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            new org.traincontrol.gui.LayoutGrid(layout, 30, panel, null, true, ui));

        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        javax.swing.SwingUtilities.invokeLater(() -> ui.whenTilesSettled(settled::countDown));

        assertTrue(settled.await(30, java.util.concurrent.TimeUnit.SECONDS),
            "the tiles never finished decoding, so nothing below is a check");

        // Held open for both halves, as a placement does with one tile of a type not yet drawn: it keeps the timer
        // armed.
        ui.tileDecodeStarted();

        final boolean[] sameSize = {false};
        final int[] sameSizeChildren = {0};
        final boolean[] newSize = {true};

        try
        {
            final org.traincontrol.gui.LayoutGrid[] built = new org.traincontrol.gui.LayoutGrid[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
                built[0] = new org.traincontrol.gui.LayoutGrid(layout, 30, panel, null, true, ui));

            // PAST THE GRACE TIMER, which is the whole point: the assertion above reads visibility in the same pass
            // that built the grid, and cannot see this.
            Thread.sleep(400);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                sameSize[0] = built[0].getContainer().isVisible();
                sameSizeChildren[0] = panel.getComponentCount();
            });

            // THE CONTROL: a different size IS an arrival and must still be held back; else "never hide anything"
            // passes.
            final javax.swing.JPanel resized = new javax.swing.JPanel();

            javax.swing.SwingUtilities.invokeAndWait(() ->
                new org.traincontrol.gui.LayoutGrid(layout, 30, resized, null, true, ui));

            final org.traincontrol.gui.LayoutGrid[] bigger = new org.traincontrol.gui.LayoutGrid[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
                bigger[0] = new org.traincontrol.gui.LayoutGrid(layout, 37, resized, null, true, ui));

            Thread.sleep(400);

            javax.swing.SwingUtilities.invokeAndWait(() ->
                newSize[0] = bigger[0].getContainer().isVisible());
        }
        finally
        {
            ui.tileDecodeFinished();
        }

        assertTrue(sameSize[0],
            "a rebuild at the same tile size was taken off the screen 120ms after it went up.  "
            + "OB-109 stopped the immediate hide and never told the grace timer, so the blink moved "
            + "rather than went - and it fires on every tile placement in the editor, which is where "
            + "Adam still saw it after the fix (MT-273)");

        assertEquals(sameSizeChildren[0], 1,
            "the panel holds " + sameSizeChildren[0] + " children.  A spinner was mounted over a page "
            + "that was already complete - and `parent` is a FlowLayout, so it does not sit behind "
            + "the diagram, it sits beside it and pushes the tiles along");

        assertFalse(newSize[0],
            "a rebuild at a DIFFERENT tile size was left up.  The size is part of the image cache "
            + "key, so nothing is cached and the page really does arrive square by square - that is "
            + "the case the hold-back exists for, and it must keep it");
    }
    /**
     * A diagram that is already drawn is not taken off the screen to be rebuilt (OB-109).
     *
     * Every placement in the track diagram editor rebuilds the whole grid; a new grid that hid itself until its tiles
     * had decoded would blink, depending on where the event thread was.  Two rules, each what the other misses.
     *
     * **Nothing pending, nothing to hide.** On a warm cache the hold-back has nothing to wait for.
     *
     * **A replacement is not an arrival.** Placing the first tile of a type nobody has drawn at this size is ONE
     * decode, and it must not take the whole page away.  A slow replacement still ends behind the spinner, 120ms in;
     * MT-194 checks that, not this.
     *
     * Both assertions read `isVisible` inside the event-thread block that built the grid, which makes them
     * deterministic: the reveal can only arrive on a later pass.
     *
     * MUTATION: removing the `tilesAreSettled` early return fails the first; removing the `replacing` condition fails
     * the second; hiding NOTHING, ever, fails the control at the end of the second.
     */
    @Test
    public void testARebuiltDiagramIsNotTakenOffTheScreen() throws Exception
    {
        java.util.List<String> pages = model.getLayoutList();

        assertFalse(pages.isEmpty(), "no pages to build - is test/test_layout present?");

        final LayoutDiagram layout = model.getLayout(pages.get(0));

        final javax.swing.JPanel first = new javax.swing.JPanel();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            new org.traincontrol.gui.LayoutGrid(layout, 30, first, null, true, ui));

        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        javax.swing.SwingUtilities.invokeLater(() -> ui.whenTilesSettled(settled::countDown));

        assertTrue(settled.await(30, java.util.concurrent.TimeUnit.SECONDS),
            "the tiles never finished decoding, so nothing below is a check");

        assertTrue(ui.tilesAreSettled(), "precondition: the cache is warm at this size");

        // One: a fresh panel, warm cache.  Nothing is pending, so nothing may be hidden.
        final javax.swing.JPanel fresh = new javax.swing.JPanel();
        final boolean[] visible = {false};

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            org.traincontrol.gui.LayoutGrid grid =
                new org.traincontrol.gui.LayoutGrid(layout, 30, fresh, null, true, ui);

            visible[0] = grid.getContainer().isVisible();
        });

        assertTrue(visible[0],
            "a grid built with no decode outstanding hid itself anyway. It is given back on the next "
            + "event-thread pass, which is a hide and a show one frame apart - the blink OB-109 is "
            + "about, and the commonest case in the editor because every rebuild after the first is "
            + "a cache hit");

        // Two: a real wait, held open by the test.  tileDecodeStarted is what a LayoutLabel calls before it submits, so
        // this holds the grid in the state it asks about for as long as the test wants, not as long as a pool takes.
        ui.tileDecodeStarted();

        final boolean[] onFresh = {false};

        try
        {
            assertFalse(ui.tilesAreSettled(), "precondition: a decode is outstanding");

            // The SAME panel, which already has a diagram on it: a replacement, with a genuine wait.
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.gui.LayoutGrid grid =
                    new org.traincontrol.gui.LayoutGrid(layout, 37, fresh, null, true, ui);

                visible[0] = grid.getContainer().isVisible();
            });

            // And the control: a BRAND NEW panel, same wait, must still be held back.
            final javax.swing.JPanel arriving = new javax.swing.JPanel();

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.gui.LayoutGrid grid =
                    new org.traincontrol.gui.LayoutGrid(layout, 37, arriving, null, true, ui);

                onFresh[0] = grid.getContainer().isVisible();
            });
        }
        finally
        {
            ui.tileDecodeFinished();
        }

        assertTrue(visible[0],
            "a rebuild over a panel that already had a diagram on it took that diagram off the "
            + "screen. The hold-back is for a page ARRIVING; in the editor this is one placement, and "
            + "one uncached tile was blanking the whole page");

        assertFalse(onFresh[0],
            "a diagram ARRIVING on a panel that had nothing on it was shown while its tiles were "
            + "still decoding. That is the staging the hold-back exists to hide - labels floating on "
            + "nothing - and giving it up would trade OB-109 for the report that came before it");
    }

    /** How many distinct colors, up to the point where the answer stops mattering. */
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

    /**
     * The train is drawn over the station caption, and the caption is still there beside it (OB-159, OB-117).
     *
     * The caption and the tile under it are separate components, and no ORDER satisfies both: caption in front paints
     * over the locomotive, tile in front paints the NAME out because a tile is opaque.  So the container paints its
     * children as always, then walks them once more asking each for its train, and the locomotive lands above both.
     *
     * Asserted as a DIFFERENCE, not a color: the same arrangement is rendered with a train and without, and the middle
     * of the square has to change.
     *
     * MUTATION: drop the paintTrainOverCaptions loop from newDiagramContainer and nothing changes, so the first
     * assertion fails; paint the train inside the tile again and it fails the same way.
     */
    @Test
    public void testTheTrainIsDrawnOverTheStationCaption() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        // A big tile, so there is room to sample the caption where the locomotive does not reach: the icon is drawn at
        // 76% of the square and the run outline is on the very edge.
        final int size = 120;

        org.traincontrol.gui.LayoutLabel tile =
            new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        org.traincontrol.gui.widgets.StationCaption caption = new org.traincontrol.gui.widgets.StationCaption();

        caption.setText("Bottom Main C");
        caption.setOpaque(true);
        // OPAQUE, deliberately: the resting fill has an alpha, so an exact-RGB comparison would find the two pictures
        // different whichever way round they were painted.
        caption.setBackground(new java.awt.Color(0, 0, 115));

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size, size);
        grid.setBackground(java.awt.Color.WHITE);

        tile.setBounds(0, 0, size, size);

        caption.setBounds(0, size * 2 / 3, size, size / 4);

        grid.add(caption);
        grid.add(tile);

        grid.setComponentZOrder(caption, 0);

        BufferedImage empty = shotOf(grid, size);

        tile.setAutonomyOverlay(new org.traincontrol.automationui.TileOverlay(
            org.traincontrol.automationui.TileOverlay.State.ACTIVE, true, true, null));

        BufferedImage withTrain = shotOf(grid, size);

        javax.imageio.ImageIO.write(empty, "png", new File(OUT, "train-over-caption-empty.png"));
        javax.imageio.ImageIO.write(withTrain, "png", new File(OUT, "train-over-caption-train.png"));

        // SAMPLED WHERE THE CAPTION AND THE ICON OVERLAP, the only place the question can be asked: the middle of the
        // square is above the pill, so the pixel would be identical either way.
        //
        // THE CONTROL FIRST: if the icon file cannot be loaded the overlay draws a plain dot that does not reach the
        // pill, and the overlap assertion would confidently blame the caption order.
        assertNotEquals(withTrain.getRGB(size / 2, size / 2), empty.getRGB(size / 2, size / 2),
            "no train was drawn on the square at all, so nothing below this can mean anything");

        // Then the overlap (OB-159).  Two thirds of the way down: the pill runs from 2/3 to 11/12 of the square and the
        // icon's PAINTED extent ends around 0.765, so 3/4 sat about a pixel inside the art at every size.
        int overlapX = size / 2;
        int overlapY = size * 17 / 24;

        assertNotEquals(withTrain.getRGB(overlapX, overlapY), empty.getRGB(overlapX, overlapY),
            "the station caption is painted over the locomotive - which is what Adam reported, and "
            + "what the third painting pass exists to stop");

        // AND THE CAPTION IS STILL THERE (OB-117): sampled inside the pill and outside both the icon and the run
        // outline.
        int atX = size / 12;
        int atY = size * 3 / 4;

        // AND THE CALL SITE, which the pixels cannot reach: the container photographed must come from
        // `LayoutGrid.newDiagramContainer()`, called by the grid's own constructor, the one place the application
        // builds its diagram (FR-136).
        String gridBuilt = SourceOf.withoutComments(SourceOf.body("public LayoutGrid(LayoutDiagram layout, int size,"
            + " JPanel parent, Container master, boolean popup, TrainControlUI ui)"));

        assertTrue(gridBuilt.contains("newDiagramContainer()"),
            "the diagram is no longer built from newDiagramContainer(), so the third painting pass "
            + "that draws trains over captions is not in the container the application shows - and "
            + "every pixel this test compares came from a container it made itself");

        assertEquals(withTrain.getRGB(atX, atY), empty.getRGB(atX, atY),
            "the caption changed where the locomotive does not reach, so the train is painting over "
            + "more than itself - and a locomotive that paints out the name is OB-117 coming back "
            + "the other way");
    }

    /**
     * The locomotive on a square is drawn ten per cent clear, so a caption under it still shows faintly (FR-130).
     *
     * The engine's black over white comes out a dark gray a tenth of the way to white, never pure black.  Nothing adds
     * a glow: every label that can lie under the engine already has a light surround, which is what shows through.
     *
     * MUTATION: draw the engine opaque again and its darkest pixel is the icon's own black, and this fails.
     */
    @Test
    public void testTheLocomotiveIsTenPerCentClear()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        final int size = 120;

        org.traincontrol.gui.LayoutLabel tile = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size, size);
        grid.setBackground(java.awt.Color.WHITE);

        tile.setBounds(0, 0, size, size);
        grid.add(tile);

        tile.setAutonomyOverlay(new org.traincontrol.automationui.TileOverlay(
            org.traincontrol.automationui.TileOverlay.State.IDLE, true, true, null));

        BufferedImage shot = shotOf(grid, size);

        int darkest = 255 * 3;

        for (int x = 0; x < size; x++)
        {
            for (int y = 0; y < size; y++)
            {
                int rgb = shot.getRGB(x, y);
                int sum = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);

                darkest = Math.min(darkest, sum);
            }
        }

        // THE CONTROL: the engine is there, and dark
        assertTrue(darkest < 3 * 90, "no locomotive was drawn on the square, so its transparency cannot be asked");

        // ten per cent of white is 25 in every channel
        assertTrue(darkest >= 3 * 20, "the locomotive is drawn solid: its darkest pixel is " + darkest / 3
            + " over a white ground, and a caption under it cannot be seen through it (FR-130)");
    }

    /**
     * Lays out a panel and paints it into an image, the only way to ask what a drawing does.
     *
     * @param panel the panel
     * @param size its side in pixels
     * @return the picture
     */
    private BufferedImage shotOf(javax.swing.JPanel panel, int size)
    {
        panel.setSize(size, size);
        panel.doLayout();

        BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size, size);

            panel.paint(g);
        }
        finally
        {
            g.dispose();
        }

        return shot;
    }

    /**
     * A caption on a running route is in the route's colors: the blue of the path ahead until the train gets there
     * (FR-106).
     *
     * On both pills the operator can choose, because the blue has to read on each: the path's own blue on the pale
     * gray, lifted to a light blue on the blue pill, where the path's own blue would disappear.
     *
     * MUTATION: let `onPill` answer the route's blue with plain black or white, and this fails.
     */
    @Test
    public void testARouteCaptionStaysInTheRoutesBlueOnEitherPill()
    {
        java.awt.Color ahead = org.traincontrol.automationui.DiagramColours.PATH_AHEAD;

        for (java.awt.Color fill : new java.awt.Color[] {
            org.traincontrol.gui.widgets.StationCaption.PILL_GREY, org.traincontrol.gui.widgets.StationCaption.PILL_AT_REST })
        {
            java.awt.Color drawn = org.traincontrol.gui.widgets.StationCaption.onPill(fill, ahead);

            assertTrue(drawn.getBlue() > drawn.getRed() + 60 && drawn.getBlue() > drawn.getGreen() + 40,
                "a caption the train has not reached yet is drawn in " + drawn + " on " + fill + ", not the route's blue");

            assertTrue(distance(drawn, fill) > 90, "the route's blue cannot be read on " + fill + ": " + drawn);
        }

        assertEquals(org.traincontrol.gui.widgets.StationCaption.onPill(org.traincontrol.gui.widgets.StationCaption.PILL_GREY, ahead),
            ahead, "on the pale grey pill the caption is not the route's own blue but a cousin of it");
    }

    /**
     * The blue captions, the station badges, the route and the Auto tab's station badge are one blue: two blues a shade
     * apart look like a mistake.
     *
     * MUTATION: put the navy back and this fails.
     */
    @Test
    public void testTheCaptionsAreTheBadgesBlue()
    {
        assertEquals(org.traincontrol.gui.widgets.StationCaption.PILL.getRGB() & 0xFFFFFF,
            org.traincontrol.automationui.DiagramColours.STATION.getRGB() & 0xFFFFFF,
            "the blue captions are " + org.traincontrol.gui.widgets.StationCaption.PILL + " beside badges of "
            + org.traincontrol.automationui.DiagramColours.STATION + " - two blues a shade apart");
    }

    /**
     * A destination is ringed in a dark orange, not the route's blue: the fill says "destination" and the pill draws
     * the ring whenever it has that fill.
     *
     * MUTATION: drop the ring from the pill's painting, or draw it in the route's blue again, and this fails.
     */
    @Test
    public void testADestinationIsRingedInDarkOrange()
    {
        java.awt.image.BufferedImage shot = destinationPill();

        int ring = 0, blue = 0;

        for (int y = 0; y < shot.getHeight(); y++)
        {
            for (int x = 0; x < shot.getWidth(); x++)
            {
                java.awt.Color c = new java.awt.Color(shot.getRGB(x, y), true);

                if (c.getAlpha() <= 120) continue;

                if (c.getRed() >= 170 && c.getRed() <= 235 && c.getGreen() >= 55 && c.getGreen() <= 115 && c.getBlue() < 45)
                {
                    ring++;
                }

                if (c.getBlue() > 140 && c.getRed() < 100 && c.getGreen() < 110) blue++;
            }
        }

        assertTrue(ring > 60, "a destination caption has " + ring + " pixels of dark orange round it - it is not ringed"
            + " in the dark orange Adam asked for");

        assertEquals(blue, 0, "a destination caption still has " + blue + " pixels of the route's blue round it");
    }

    /** A destination's pill with a name on it, painted. */
    private static java.awt.image.BufferedImage destinationPill()
    {
        org.traincontrol.gui.widgets.StationCaption pill = new org.traincontrol.gui.widgets.StationCaption();

        pill.setPill(true);
        pill.setFont(new java.awt.Font(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 20));
        pill.setBackground(org.traincontrol.gui.widgets.StationCaption.DESTINATION_FILL);
        pill.setForeground(java.awt.Color.BLACK);
        pill.setText("Carlton");
        pill.setTileGeometry(60, 0, 0, org.traincontrol.gui.widgets.StationCaption.captionOffset(60, pill.lineHeight()));

        java.awt.Dimension size = pill.getPreferredSize();

        pill.setBounds(0, 0, size.width, size.height);

        java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(
            size.width, size.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = shot.createGraphics();

        pill.paint(g);

        g.dispose();

        return shot;
    }

    /**
     * A pill's text sits in the middle of it, as much room at the right end as at the left.
     *
     * The pill is sized from the text's width as measured for layout.  Painted at a larger scale (a display at 125 or
     * 150 per cent, or the 2x the guide's pictures use) the glyphs come out wider than measured and must still sit
     * centered.  Painted here at 2x, the room either side of the ink is measured, with the arrow on the left, on the
     * right, and none.
     *
     * MUTATION: draw the text where the label's layout put it again, and this fails.
     */
    @Test
    public void testAPillsTextSitsInTheMiddleOfIt()
    {
        String[] texts = {
            org.traincontrol.gui.widgets.StationCaption.withArrow("ICE 3", " " + org.traincontrol.gui.widgets.StationCaption.ARROW_W),
            org.traincontrol.gui.widgets.StationCaption.withArrow("ICE 3", " " + org.traincontrol.gui.widgets.StationCaption.ARROW_E),
            "Carlton", "75 407 DB" };

        for (double scale : new double[] {1.25, 1.5, 2.0})
        for (int points : new int[] {12, 15})
        {
            for (String text : texts)
            {
                org.traincontrol.gui.widgets.StationCaption pill = new org.traincontrol.gui.widgets.StationCaption();

                pill.setPill(true);
                pill.setFont(new java.awt.Font(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, points));
                pill.setBackground(org.traincontrol.gui.widgets.StationCaption.PILL_GREY);
                pill.setForeground(java.awt.Color.BLACK);
                pill.setText(text);
                pill.setTileGeometry(30, 0, 0, org.traincontrol.gui.widgets.StationCaption.captionOffset(30, pill.lineHeight()));

                java.awt.Dimension size = pill.getPreferredSize();

                pill.setBounds(0, 0, size.width, size.height);

                java.awt.Rectangle drawn = pill.drawnBounds();

                java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(
                    (int) Math.ceil(size.width * scale) + 2, (int) Math.ceil(size.height * scale) + 2,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);

                java.awt.Graphics2D g = shot.createGraphics();

                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.scale(scale, scale);
                g.setClip(0, 0, size.width, size.height);

                pill.paint(g);

                g.dispose();

                int mid = (int) ((drawn.y + drawn.height / 2.0) * scale), left = -1, right = -1;

                for (int x = 0; x < shot.getWidth(); x++)
                {
                    if (((shot.getRGB(x, mid) >>> 24) & 0xFF) > 100)
                    {
                        if (left < 0) left = x;
                        right = x;
                    }
                }

                int inkLeft = Integer.MAX_VALUE, inkRight = -1, inkTop = Integer.MAX_VALUE, inkBottom = -1;
                int pillTop = Integer.MAX_VALUE, pillBottom = -1;

                for (int y = 0; y < shot.getHeight(); y++)
                {
                    for (int x = 0; x < shot.getWidth(); x++)
                    {
                        java.awt.Color c = new java.awt.Color(shot.getRGB(x, y), true);

                        if (c.getAlpha() > 100)
                        {
                            pillTop = Math.min(pillTop, y);
                            pillBottom = Math.max(pillBottom, y);
                        }

                        if (c.getAlpha() > 100 && c.getRed() < 90 && c.getGreen() < 90 && c.getBlue() < 90)
                        {
                            inkLeft = Math.min(inkLeft, x);
                            inkRight = Math.max(inkRight, x);
                            inkTop = Math.min(inkTop, y);
                            inkBottom = Math.max(inkBottom, y);
                        }
                    }
                }

                assertTrue(left >= 0 && inkRight >= 0, "precondition: nothing was drawn for \"" + text + "\"");

                int before = inkLeft - left, after = right - inkRight;

                assertTrue(Math.abs(before - after) <= 3, "\"" + text + "\" at " + points + " points, painted at " + scale
                    + "x, has " + before + " pixels of pill before its text and " + after + " after it - the text is not in"
                    + " the middle");

                // AND UP AND DOWN, asked of the two names without an arrow: neither has a letter that hangs below the
                // line, so their ink should sit in the middle of the pill.  An arrow comes from another font.
                int above = inkTop - pillTop, below = pillBottom - inkBottom;

                if (text.equals("Carlton") || text.equals("75 407 DB"))
                {
                    assertTrue(Math.abs(above - below) <= 2, "\"" + text + "\" at " + points + " points, painted at "
                        + scale + "x, has " + above + " pixels of pill above its text and " + below + " below it - the text"
                        + " sits" + (above > below ? " low" : " high") + " in the pill");
                }

                assertTrue(after >= 2 * scale, "\"" + text + "\" at " + points + " points runs to within " + after
                    + " pixels of the pill's right end");
            }
        }
    }

    /**
     * A caption that names a train has a faint edge, so it parts from a signal or a switch drawn under it; a
     * placeholder dash has none, so an empty station gets no louder.
     *
     * MUTATION: drop the edge, or draw it on the dash too, and this fails.
     */
    @Test
    public void testANamedCaptionHasAnEdgeAndADashHasNone()
    {
        java.awt.Color fill = org.traincontrol.gui.widgets.StationCaption.PILL_GREY;

        int[] edge = new int[2];

        String[] texts = {"Carlton", org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY};

        for (int i = 0; i < texts.length; i++)
        {
            org.traincontrol.gui.widgets.StationCaption pill = new org.traincontrol.gui.widgets.StationCaption();

            pill.setPill(true);
            pill.setFont(new java.awt.Font(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 15));
            pill.setBackground(fill);
            pill.setForeground(java.awt.Color.BLACK);
            pill.setText(texts[i]);
            pill.setTileGeometry(30, 0, 0, org.traincontrol.gui.widgets.StationCaption.captionOffset(30, pill.lineHeight()));

            java.awt.Dimension size = pill.getPreferredSize();

            pill.setBounds(0, 0, size.width, size.height);

            java.awt.Rectangle drawn = pill.drawnBounds();

            java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(
                size.width, size.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = shot.createGraphics();

            pill.paint(g);

            g.dispose();

            java.awt.Color top = new java.awt.Color(shot.getRGB(drawn.x + drawn.width / 2, drawn.y), true);

            edge[i] = (fill.getRed() + fill.getGreen() + fill.getBlue()) / 3 - (top.getRed() + top.getGreen() + top.getBlue()) / 3;
        }

        assertTrue(edge[0] > 30, "a caption naming a train has no edge: its top is only " + edge[0] + " darker than its fill");

        assertTrue(edge[1] < 15, "an empty station's dash pill has an edge " + edge[1] + " darker than its fill - it is"
            + " louder, where D1 made it quieter");
    }

    /**
     * The curve and switch tiles are the mirror images their shapes say they are, at both sizes.
     *
     * A curve between the south and east sides is its own mirror image across the diagonal; a left switch is a right
     * switch turned over; a crossing is the same turned a quarter.  The icons are drawn from their geometry
     * (`docs/tools/tile-icons.py`), and a pixel more than a shade out (12 of 255) on either side of a mirror is
     * counted.
     *
     * MUTATION: put any one of the old icons back and this fails.
     *
     * @throws Exception reading the icons
     */
    @Test
    public void testTheCurveAndSwitchTilesAreMirrorImages() throws Exception
    {
        // tile, what it is the mirror image of: "T" across its diagonal, "H" turned half round, "LR" left to right,
        // "TB" top to bottom, or another tile's name to be that tile turned over left to right
        String[][] checks = {
            {"curve", "T"}, {"curve_parallel", "H"}, {"curve_parallel", "T"}, {"s88_curve", "T"}, {"s88_curve_active", "T"},
            {"s88_double_curve", "T"}, {"s88_double_curve_active", "T"},
            {"switch_left", "switch_right"}, {"switch_left_active", "switch_right_active"}, {"switch_y", "switch_y_active"},
            {"threeway", "LR"}, {"threeway_active", "threeway_active2"},
            {"cross", "LR"}, {"cross", "TB"}, {"cross", "T"}, {"crossswitch", "H"}, {"crossswitch", "T"},
            {"crossswitch_active", "H"}, {"crossswitch_active", "T"},
            {"custom_scissors", "TB"}, {"custom_scissors_active", "TB"}, {"custom_perm_scissors", "TB"},
            {"custom_perm_left", "custom_perm_right"}, {"custom_perm_y", "LR"}, {"custom_perm_threeway", "LR"},
            {"s88", "LR"}, {"s88", "TB"}, {"s88_active", "LR"}, {"s88_active", "TB"}, {"end", "LR"}};

        java.util.List<String> off = new java.util.ArrayList<>();

        for (int size : new int[] {30, 60})
        {
            for (String[] check : checks)
            {
                BufferedImage a = icon(size, check[0]);
                String how = check[1];
                BufferedImage b = how.length() <= 2 ? a : icon(size, how);
                int n = a.getWidth(), wrong = 0;

                for (int y = 0; y < n; y++)
                {
                    for (int x = 0; x < n; x++)
                    {
                        int mx, my;

                        switch (how)
                        {
                            case "T": mx = y; my = x; break;
                            case "H": mx = n - 1 - x; my = n - 1 - y; break;
                            case "TB": mx = x; my = n - 1 - y; break;
                            default: mx = n - 1 - x; my = y;
                        }

                        int p = a.getRGB(x, y), q = b.getRGB(mx, my), most = 0;

                        for (int shift = 0; shift <= 16; shift += 8)
                        {
                            most = Math.max(most, Math.abs(((p >> shift) & 0xFF) - ((q >> shift) & 0xFF)));
                        }

                        if (most > 12) wrong++;
                    }
                }

                if (wrong > 0) off.add(check[0] + " at " + size + " vs " + how + ": " + wrong + " pixels");
            }
        }

        assertTrue(off.isEmpty(), "tiles that are not the mirror images their shapes say they are: " + off);
    }

    /** A tile's icon, as the diagram loads it. */
    private static BufferedImage icon(int size, String name) throws Exception
    {
        java.net.URL at = TrainControlUI.class.getResource("/org/traincontrol/gui/resources/icons" + size + "/" + name + ".gif");

        assertNotNull(at, "no icon " + name + " at " + size);

        return javax.imageio.ImageIO.read(at);
    }

    /**
     * A pill's edge and a destination's ring are as thick on the right and the bottom as on the left and the top.
     *
     * `drawRoundRect(x, y, w - 1, h - 1)` with anti-aliasing puts the left and top of the outline half outside the fill
     * and the right and bottom wholly inside it.  Painted at twice the size, where the pill's ends fall on whole
     * pixels, the four sides of an even outline read the same from the outside in.
     *
     * MUTATION: draw the outline the old way and this fails.
     */
    @Test
    public void testAPillsOutlineIsEvenAllRound()
    {
        java.awt.Color[] fills = {org.traincontrol.gui.widgets.StationCaption.PILL_GREY, org.traincontrol.gui.widgets.StationCaption.DESTINATION_FILL};

        for (java.awt.Color fill : fills)
        {
            org.traincontrol.gui.widgets.StationCaption pill = new org.traincontrol.gui.widgets.StationCaption();

            pill.setPill(true);
            pill.setFont(new java.awt.Font(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 15));
            pill.setBackground(fill);
            pill.setForeground(java.awt.Color.BLACK);
            pill.setText("Carlton");
            pill.setTileGeometry(30, 0, 0, org.traincontrol.gui.widgets.StationCaption.captionOffset(30, pill.lineHeight()));

            java.awt.Dimension size = pill.getPreferredSize();

            pill.setBounds(0, 0, size.width, size.height);

            java.awt.Rectangle drawn = pill.drawnBounds();

            java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(size.width * 2, size.height * 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g = shot.createGraphics();

            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, shot.getWidth(), shot.getHeight());
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.scale(2, 2);
            g.setClip(0, 0, size.width, size.height);

            pill.paint(g);

            g.dispose();

            int midRow = (drawn.y * 2 + (drawn.y + drawn.height) * 2) / 2;
            int left = drawn.x * 2, right = (drawn.x + drawn.width) * 2 - 1;
            int top = drawn.y * 2, bottom = (drawn.y + drawn.height) * 2 - 1;

            int col = left + (right - left) / 6;

            for (int in = 0; in < 3; in++)
            {
                int l = grey(shot.getRGB(left + in, midRow)), r = grey(shot.getRGB(right - in, midRow));
                int t = grey(shot.getRGB(col, top + in)), b = grey(shot.getRGB(col, bottom - in));

                assertTrue(Math.abs(l - r) <= 24, "a pill filled " + fill + " is not as dark " + in + " pixels in from its right"
                    + " end (" + r + ") as from its left (" + l + ") - its outline sits differently on the two sides");

                assertTrue(Math.abs(t - b) <= 24, "a pill filled " + fill + " is not as dark " + in + " pixels in from its"
                    + " bottom (" + b + ") as from its top (" + t + ") - its outline sits differently on the two sides");
            }
        }
    }

    private static int grey(int rgb)
    {
        return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
    }

    /**
     * A square's address is red letters with a white halo round them, and no box behind them.
     *
     * Each address label on a page with Show Addresses on is painted alone onto black: a box behind the text lights
     * the label's corners, a halo leaves them black and puts white only round the letters.
     *
     * MUTATION: put the box back and this fails.
     *
     * @throws Exception from Swing
     */
    @Test
    public void testAnAddressHasAHaloAndNoBox() throws Exception
    {
        LayoutDiagram page = model.getLayout(model.getLayoutList().get(0));

        final boolean addressesWere = page.getShowAddress();

        page.setShowAddress(true);

        try
        {
            support.Rendered drawn = support.Rendered.open(page, ui);

            drawn.snapshot();

            java.util.List<javax.swing.JLabel> addresses = new java.util.ArrayList<>();

            collectAddresses(drawn.host(), addresses);

            assertFalse(addresses.isEmpty(), "precondition: no address label on " + page.getName() + " with Show Addresses on");

            int checked = 0;

            for (final javax.swing.JLabel label : addresses)
            {
                final BufferedImage[] shot = new BufferedImage[1];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    java.awt.Dimension d = label.getSize();

                    shot[0] = new BufferedImage(Math.max(1, d.width), Math.max(1, d.height), BufferedImage.TYPE_INT_RGB);

                    java.awt.Graphics2D g = shot[0].createGraphics();

                    g.setColor(java.awt.Color.BLACK);
                    g.fillRect(0, 0, shot[0].getWidth(), shot[0].getHeight());

                    label.paint(g);

                    g.dispose();
                });

                BufferedImage s = shot[0];

                if (s.getWidth() < 4 || s.getHeight() < 4) continue;

                int red = 0, white = 0;

                for (int y = 0; y < s.getHeight(); y++)
                {
                    for (int x = 0; x < s.getWidth(); x++)
                    {
                        int rgb = s.getRGB(x, y), r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

                        if (r > 180 && g < 80 && b < 80) red++;
                        if (r > 200 && g > 200 && b > 200) white++;
                    }
                }

                String what = "the address \"" + label.getText() + "\" on " + page.getName();

                for (int[] corner : new int[][] {{0, 0}, {s.getWidth() - 1, 0}, {0, s.getHeight() - 1},
                    {s.getWidth() - 1, s.getHeight() - 1}})
                {
                    int rgb = s.getRGB(corner[0], corner[1]);

                    int brightest = Math.max((rgb >> 16) & 0xFF, Math.max((rgb >> 8) & 0xFF, rgb & 0xFF));

                    assertTrue(brightest < 40, what + " paints its corner (" + corner[0] + "," + corner[1] + ") - a box"
                        + " behind the text, where Adam asked for a halo round the letters");
                }

                assertTrue(red > 0, what + " draws nothing red");

                assertTrue(white > 0, what + " has no white round its letters");

                checked++;
            }

            assertTrue(checked > 0, "precondition: no address label big enough to look at on " + page.getName());
        }
        finally
        {
            page.setShowAddress(addressesWere);
        }
    }

    /** Every address label under a component: a plain label, not a square's tile or a station's caption. */
    private static void collectAddresses(java.awt.Container under, java.util.List<javax.swing.JLabel> into)
    {
        for (java.awt.Component c : under.getComponents())
        {
            if (c instanceof javax.swing.JLabel && !(c instanceof org.traincontrol.gui.LayoutLabel)
                && !(c instanceof org.traincontrol.gui.widgets.StationCaption))
            {
                String text = ((javax.swing.JLabel) c).getText();

                if (text != null && text.replaceAll("<[^>]*>", "").trim().matches("\\d+[rg]?(\\s.*)?")) into.add((javax.swing.JLabel) c);
            }

            if (c instanceof java.awt.Container) collectAddresses((java.awt.Container) c, into);
        }
    }

    /**
     * A side trains may not arrive by is marked with the same gray chevron at the large size as at the small one
     * (OB-326).
     *
     * The mark is painted on a 30-pixel square and a 60-pixel one.  Every pixel it inks is gray, and the large one,
     * shrunk to half, carries the ink the small one does: a chevron whose arms scale with the square, not a hollow
     * shape whose strokes come apart at the larger size.
     *
     * MUTATION: put the old strokes or the old tan back and this fails.
     */
    @Test
    public void testABarredArrivalIsTheSameGreyChevronAtBothSizes()
    {
        org.traincontrol.automationui.TileAnnotation barred = new org.traincontrol.automationui.TileAnnotation(null, 0,
            false, null, false, false, false, null, false, java.util.Collections.singletonList(
                new org.traincontrol.automationui.TileAnnotation.Arrival(
                    org.traincontrol.automationui.TilePorts.Side.N, false)));

        BufferedImage small = paintedOnWhite(barred, 30), large = paintedOnWhite(barred, 60);

        for (BufferedImage shot : new BufferedImage[] {small, large})
        {
            for (int y = 0; y < shot.getHeight(); y++)
            {
                for (int x = 0; x < shot.getWidth(); x++)
                {
                    int rgb = shot.getRGB(x, y), r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));

                    // the red direction arrow a barred side also carries is its own mark, not this one
                    if (isArrowRed(rgb)) continue;

                    assertTrue(255 - min < 30 || max - min <= 16, "the barred-arrival mark at " + shot.getWidth()
                        + " pixels is rgb(" + r + "," + g + "," + b + ") at " + x + "," + y + ", not grey");
                }
            }
        }

        double inkSmall = ink(small), inkLarge = ink(large) / 4.0;

        assertTrue(inkSmall > 0, "precondition: the barred-arrival mark drew nothing at 30 pixels");

        assertTrue(Math.abs(inkLarge - inkSmall) <= inkSmall * 0.12, "the barred-arrival mark at 60 pixels, taken to"
            + " half size, carries " + Math.round(inkLarge) + " of ink against " + Math.round(inkSmall) + " at 30 - not"
            + " the same shape scaled");
    }

    /** An annotation painted alone on a white square of this size. */
    private static BufferedImage paintedOnWhite(org.traincontrol.automationui.TileAnnotation annotation, int size)
    {
        BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, size, size);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);

        annotation.paint(g, size, size);

        g.dispose();

        return shot;
    }

    /** A pixel of a red direction arrow: more red than green or blue, which are alike. */
    private static boolean isArrowRed(int rgb)
    {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

        // by hue, so its anti-aliased pink edge counts too; the old tan, rgb(150,140,100), is not red
        return r - g > 20 && r - b > 20 && Math.abs(g - b) <= 12;
    }

    /** How much darker than white a picture is, in all, leaving out a red direction arrow. */
    private static double ink(BufferedImage shot)
    {
        double sum = 0;

        for (int y = 0; y < shot.getHeight(); y++)
        {
            for (int x = 0; x < shot.getWidth(); x++)
            {
                int rgb = shot.getRGB(x, y);

                if (isArrowRed(rgb)) continue;

                sum += 255 - (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3.0;
            }
        }

        return sum;
    }

    /**
     * A caption stood on end beside a station's vertical track clears that station's badge, and the badge of a station
     * on the next square over (OB-327).
     *
     * The badge is MEASURED, painted as the diagram paints a station on vertical track, and the caption is built and
     * placed as the grid does, at both tile sizes, empty and with a train's name.  Its near edge must clear its own
     * badge and its far edge the same badge one square to the right, by a pixel (the white rim).
     *
     * MUTATION: place it by the flat caption's offset again, or give it the full line height, and this fails.
     */
    @Test
    public void testAPillOnEndClearsTheStationsBesideIt()
    {
        String[] texts = {org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY, "75 407 DB",
            org.traincontrol.gui.widgets.StationCaption.withArrow("EN57-947", " " + org.traincontrol.gui.widgets.StationCaption.ARROW_W)};

        for (int tile : new int[] {30, 60})
        {
            BufferedImage square = paintedOnWhite(new org.traincontrol.automationui.TileAnnotation(null, 0, false,
                new org.traincontrol.automationui.TileAnnotation.Badge(true, false, false, false, true,
                    org.traincontrol.automationui.TilePorts.Side.N, org.traincontrol.automationui.TilePorts.Side.S),
                false, false, false, null), tile);

            int badgeLeft = -1, badgeRight = -1;

            for (int x = 0; x < tile; x++)
            {
                int rgb = square.getRGB(x, tile / 2);

                if (255 - Math.min((rgb >> 16) & 0xFF, Math.min((rgb >> 8) & 0xFF, rgb & 0xFF)) > 40)
                {
                    if (badgeLeft < 0) badgeLeft = x;
                    badgeRight = x;
                }
            }

            assertTrue(badgeLeft >= 0, "precondition: no station badge drawn at " + tile + " pixels");

            int margin = 1;

            for (String text : texts)
            {
                org.traincontrol.gui.widgets.StationCaption pill = new org.traincontrol.gui.widgets.StationCaption();

                java.awt.Font font = new java.awt.Font(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN,
                    tile / 2);

                pill.setPill(true);
                pill.setFont(font.deriveFont(font.getSize2D() * org.traincontrol.gui.widgets.StationCaption.FONT_SCALE));
                pill.setBackground(org.traincontrol.gui.widgets.StationCaption.PILL_GREY);
                pill.setText(text);
                pill.setRotated(true);
                pill.setTileGeometry(tile, 0, 0, org.traincontrol.gui.widgets.StationCaption.captionOffset(tile, pill.lineHeight()));

                java.awt.Dimension size = pill.getPreferredSize();

                pill.setBounds(0, 0, size.width, size.height);

                java.awt.Rectangle drawn = pill.drawnBounds();

                int near = drawn.x, far = drawn.x + drawn.width - 1;

                assertTrue(near >= badgeRight + 1 + margin, "\"" + text + "\" stood on end at " + tile + " pixels starts"
                    + " at " + near + ", on or against its own station's badge, which reaches " + badgeRight);

                assertTrue(far <= tile + badgeLeft - 1 - margin, "\"" + text + "\" stood on end at " + tile + " pixels"
                    + " reaches " + far + ", on or against the badge of a station one square over, which starts at "
                    + (tile + badgeLeft));
            }
        }
    }

    /**
     * A caption stood on end is drawn at the size of one lying flat (FR-118).  OB-327 drew it 15% smaller to clear the
     * station badges; the new badges leave room (`testAPillOnEndClearsTheStationsBesideIt`).  Measured as painted: the
     * letters across their line, flat and on end.
     *
     * MUTATION: put the 0.85 back and this fails.
     */
    @Test
    public void testAPillOnEndIsFullSize()
    {
        for (int tile : new int[] {30, 60})
        {
            int flat = lettersAcross(tile, false), onEnd = lettersAcross(tile, true);

            assertTrue(flat > 0, "precondition: no letters painted on a flat caption at " + tile + " pixels");

            assertTrue(Math.abs(flat - onEnd) <= 1, "a caption's letters stood on end at " + tile + " pixels are " + onEnd
                + " across, against " + flat + " lying flat - Adam: \"the small font on the vertical labels is really"
                + " hard to read\"");
        }
    }

    /** How many pixels across its line a caption's letters span, painted flat or stood on end, as the grid builds one. */
    private static int lettersAcross(int tile, boolean rotated)
    {
        org.traincontrol.gui.widgets.StationCaption pill = new org.traincontrol.gui.widgets.StationCaption();

        java.awt.Font font = new java.awt.Font(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, tile / 2);

        pill.setPill(true);
        pill.setFont(font.deriveFont(font.getSize2D() * org.traincontrol.gui.widgets.StationCaption.FONT_SCALE));
        pill.setBackground(org.traincontrol.gui.widgets.StationCaption.PILL_GREY);
        pill.setForeground(java.awt.Color.BLACK);
        pill.setText("Hgjy Halt");
        pill.setRotated(rotated);
        pill.setTileGeometry(tile, 0, 0, org.traincontrol.gui.widgets.StationCaption.captionOffset(tile, pill.lineHeight()));

        java.awt.Dimension size = pill.getPreferredSize();

        pill.setBounds(0, 0, size.width, size.height);

        BufferedImage shot = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height), BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, shot.getWidth(), shot.getHeight());

        pill.paint(g);

        g.dispose();

        int lo = Integer.MAX_VALUE, hi = -1;

        for (int x = 0; x < shot.getWidth(); x++)
        {
            for (int y = 0; y < shot.getHeight(); y++)
            {
                int rgb = shot.getRGB(x, y);

                if (((rgb >> 16) & 0xFF) < 90 && ((rgb >> 8) & 0xFF) < 90 && (rgb & 0xFF) < 90)
                {
                    lo = Math.min(lo, rotated ? x : y);
                    hi = Math.max(hi, rotated ? x : y);
                }
            }
        }

        return hi < 0 ? 0 : hi - lo + 1;
    }

    /**
     * A square's address is drawn over the locomotive standing on it, not under it (FR-116).
     *
     * The diagram's container paints its children and then every tile's train over them (OB-159), so an address, being
     * a child, was painted over by the icon.  Here an address lies across the middle of a square with a running train
     * on it; its red letters must all still be there with the train drawn.
     *
     * MUTATION: drop the last pass that paints the addresses over the trains and this fails.
     */
    @Test
    public void testAnAddressIsDrawnOverTheTrain() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        final int size = 120;

        org.traincontrol.gui.LayoutLabel tile = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        org.traincontrol.gui.widgets.AddressLabel address = new org.traincontrol.gui.widgets.AddressLabel();

        address.setForeground(java.awt.Color.RED);
        address.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, size / 3));
        address.setLines("88");

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size, size);
        grid.setBackground(java.awt.Color.WHITE);

        tile.setBounds(0, 0, size, size);

        java.awt.Dimension d = address.getPreferredSize();

        address.setBounds((size - d.width) / 2, (size - d.height) / 2, d.width, d.height);

        grid.add(address);
        grid.add(tile);

        grid.setComponentZOrder(address, 0);

        BufferedImage alone = shotOf(grid, size);

        tile.setAutonomyOverlay(new org.traincontrol.automationui.TileOverlay(
            org.traincontrol.automationui.TileOverlay.State.ACTIVE, true, true, null));

        BufferedImage withTrain = shotOf(grid, size);

        // CONTROL: the train is drawn where the address is, or nothing below could tell the orders apart
        assertTrue(differs(alone, withTrain, address.getBounds()), "precondition: the train's icon does not reach the"
            + " address in the middle of the square, so the order they are drawn in cannot show");

        int red = 0, kept = 0;

        java.awt.Rectangle at = address.getBounds();

        for (int y = at.y; y < at.y + at.height; y++)
        {
            for (int x = at.x; x < at.x + at.width; x++)
            {
                if (isAddressRed(alone.getRGB(x, y)))
                {
                    red++;

                    if (isAddressRed(withTrain.getRGB(x, y))) kept++;
                }
            }
        }

        assertTrue(red > 0, "precondition: the address drew nothing red");

        assertTrue(kept == red, "with a train on the square, " + (red - kept) + " of the address's " + red + " red pixels are"
            + " painted over - the locomotive's icon is drawn on top of the address");
    }

    private static boolean isAddressRed(int rgb)
    {
        return ((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) < 60 && (rgb & 0xFF) < 60;
    }

    private static boolean differs(BufferedImage a, BufferedImage b, java.awt.Rectangle within)
    {
        for (int y = within.y; y < within.y + within.height; y++)
        {
            for (int x = within.x; x < within.x + within.width; x++)
            {
                if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
            }
        }

        return false;
    }

    /**
     * The mark beside "No available paths" on the Auto tab is the dark gray of the window's side tabs' icons, not blue:
     * the card's own icon, read off the class that draws it, against the commonest color of the side tabs' pictures.
     *
     * @throws Exception from reading the icons
     */
    @Test
    public void testTheNoPathsMarkIsTheSideTabsGrey() throws Exception
    {
        java.lang.reflect.Field field = org.traincontrol.gui.autonomy.AutoLocomotiveStatus.class.getDeclaredField("INFO_ICON");

        field.setAccessible(true);

        javax.swing.ImageIcon mark = (javax.swing.ImageIcon) field.get(null);

        assertNotNull(mark, "precondition: the Auto tab has no info mark to draw");

        BufferedImage drawn = new BufferedImage(mark.getIconWidth(), mark.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = drawn.createGraphics();

        g.drawImage(mark.getImage(), 0, 0, null);
        g.dispose();

        int ink = commonestInk(drawn);

        BufferedImage tab = javax.imageio.ImageIO.read(org.traincontrol.gui.TrainControlUI.class.getResource(
            "resources/tabs/loc.png"));

        int tabs = commonestInk(tab);

        assertEquals(Integer.toHexString(ink), Integer.toHexString(tabs), "the mark beside \"No available paths\" is"
            + " rgb(" + ((ink >> 16) & 0xFF) + "," + ((ink >> 8) & 0xFF) + "," + (ink & 0xFF) + "), where the side tabs'"
            + " icons are rgb(" + ((tabs >> 16) & 0xFF) + "," + ((tabs >> 8) & 0xFF) + "," + (tabs & 0xFF) + ")");
    }

    /** The commonest opaque color in a picture that is not white. */
    private static int commonestInk(BufferedImage image)
    {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();

        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int argb = image.getRGB(x, y);

                if (((argb >>> 24) & 0xFF) < 200 || (argb & 0xFFFFFF) == 0xFFFFFF) continue;

                counts.merge(argb & 0xFFFFFF, 1, Integer::sum);
            }
        }

        assertFalse(counts.isEmpty(), "precondition: a picture with nothing drawn in it");

        return java.util.Collections.max(counts.entrySet(), java.util.Map.Entry.comparingByValue()).getKey();
    }

    /**
     * A running train's destination is drawn in the orange the train's line shows on white, solid.
     *
     * The orange is worked out from the line's own color and see-through, and the pill is painted on white: its
     * commonest color, the fill between the ring and the letters, is that orange exactly.
     *
     * MUTATION: put the bright orange or the warm yellow back, or draw the destination see-through, and this fails.
     */
    @Test
    public void testADestinationIsTheLineAsItLooksOnWhite()
    {
        java.awt.Color fill = org.traincontrol.gui.widgets.StationCaption.DESTINATION_FILL;
        java.awt.Color line = org.traincontrol.automationui.DiagramColours.TRAIN_SEE_THROUGH;

        double a = line.getAlpha() / 255.0;

        int expected = (int) Math.round(line.getRed() * a + 255 * (1 - a)) << 16
            | (int) Math.round(line.getGreen() * a + 255 * (1 - a)) << 8
            | (int) Math.round(line.getBlue() * a + 255 * (1 - a));

        assertEquals(Integer.toHexString(fill.getRGB() & 0xFFFFFF), Integer.toHexString(expected), "a destination is "
            + fill + ", not the train's line as it shows on white");

        assertEquals(fill.getAlpha(), 255, "a destination is drawn faded, at " + fill.getAlpha() + " of 255");

        java.awt.image.BufferedImage shot = destinationPill();

        java.awt.image.BufferedImage onWhite = new java.awt.image.BufferedImage(shot.getWidth(), shot.getHeight(),
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = onWhite.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, shot.getWidth(), shot.getHeight());
        g.drawImage(shot, 0, 0, null);
        g.dispose();

        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();

        for (int y = 0; y < onWhite.getHeight(); y++)
        {
            for (int x = 0; x < onWhite.getWidth(); x++)
            {
                int rgb = onWhite.getRGB(x, y) & 0xFFFFFF;

                if (rgb != 0xFFFFFF) counts.merge(rgb, 1, Integer::sum);
            }
        }

        int commonest = java.util.Collections.max(counts.entrySet(), java.util.Map.Entry.comparingByValue()).getKey();

        assertEquals(Integer.toHexString(commonest), Integer.toHexString(expected), "a destination's pill painted on white"
            + " is mostly rgb(" + ((commonest >> 16) & 0xFF) + "," + ((commonest >> 8) & 0xFF) + "," + (commonest & 0xFF)
            + "), not the train's line as it shows on white");
    }

    /** An address that counts its paints. */
    public static final class CountingAddress extends org.traincontrol.gui.widgets.AddressLabel
    {
        static int painted;

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            painted++;

            super.paintComponent(g);
        }
    }

    /**
     * A repaint of a corner of the diagram paints the addresses in that corner and no others: the pass that draws the
     * addresses over the trains must take only those inside the clip.  Forty addresses, a repaint clipped to the first
     * one's corner: it is painted, by the ordinary pass and the one over the trains, and nothing else is.
     *
     * MUTATION: paint every address in the pass over the trains, whatever the clip, and this fails.
     */
    @Test
    public void testASmallRepaintPaintsOnlyTheAddressesInIt()
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("painting a diagram needs a display");

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(1200, 1200);

        for (int i = 0; i < 40; i++)
        {
            CountingAddress address = new CountingAddress();

            address.setForeground(java.awt.Color.RED);
            address.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
            address.setLines(String.valueOf(100 + i));

            java.awt.Dimension d = address.getPreferredSize();

            address.setBounds((i % 8) * 150, (i / 8) * 150, d.width, d.height);

            grid.add(address);
        }

        CountingAddress.painted = 0;

        java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(1200, 1200,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = shot.createGraphics();

        g.setClip(0, 0, 60, 60);

        grid.paint(g);

        g.dispose();

        assertTrue(CountingAddress.painted >= 1, "precondition: the address in the corner repainted was not painted");

        assertTrue(CountingAddress.painted <= 2, "a repaint of one corner of the diagram painted addresses "
            + CountingAddress.painted + " times, where one address lies in it - the pass over the trains painted every"
            + " address on the page (RSA60-C1)");
    }

    /**
     * A station on a curve spills onto the squares beside it on the diagram itself: drawn once every square is drawn,
     * over squares that paint their own white.  Nine squares of 30 pixels in the diagram's own container, a station on
     * an E-S curve in the middle: its blue reaches the squares to its right and below, which were painted after it.
     *
     * MUTATION: take the spill pass out of `LayoutGrid.newDiagramContainer`, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testAStationOnACurveSpillsOntoTheSquaresBesideIt()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        final int size = 30;

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size * 3, size * 3);
        grid.setBackground(java.awt.Color.WHITE);

        org.traincontrol.gui.LayoutLabel station = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        station.setBounds(size, size, size, size);
        station.setAutonomyAnnotation(aCurvedStation());

        grid.add(station);

        for (int i = 0; i < 9; i++)
        {
            if (i == 4) continue;

            org.traincontrol.gui.LayoutLabel beside = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

            beside.setOpaque(true);
            beside.setBackground(java.awt.Color.WHITE);
            beside.setBounds((i % 3) * size, (i / 3) * size, size, size);

            grid.add(beside, 0);
        }

        BufferedImage shot = new BufferedImage(size * 3, size * 3, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size * 3, size * 3);

            grid.paint(g);
        }
        finally
        {
            g.dispose();
        }

        int inside = 0, beside = 0;

        for (int x = 0; x < size * 3; x++)
        {
            for (int y = 0; y < size * 3; y++)
            {
                int rgb = shot.getRGB(x, y);

                if ((rgb & 0xFF) > 150 && ((rgb >> 16) & 0xFF) < 90 && ((rgb >> 8) & 0xFF) < 90)
                {
                    if (x >= size && x < 2 * size && y >= size && y < 2 * size) inside++;
                    else beside++;
                }
            }
        }

        assertTrue(inside > 0, "precondition: the station's square drew no blue");

        assertTrue(beside > 0, "a station on a curve drew nothing on the squares beside it - its icon is cut off at its"
            + " square's edges, where Adam asked: \"make the stations spill over onto adjacent tiles\"");
    }

    /**
     * A station that spills repaints what it spills onto: when its square is repainted, or its icon taken away, the
     * diagram is asked to repaint the squares around it too, or the old spill would stay there.  Asked of Swing's
     * repaint queue.
     *
     * MUTATION: repaint only the square itself again, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testAStationThatSpillsRepaintsWhatItSpillsOnto() throws Exception
    {
        final int size = 30;

        final java.util.List<java.awt.Rectangle> asked = new java.util.ArrayList<>();

        final java.util.List<java.awt.Rectangle> onRepaint = new java.util.ArrayList<>(), onRemoval = new java.util.ArrayList<>();

        final javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            javax.swing.RepaintManager was = javax.swing.RepaintManager.currentManager(grid);

            javax.swing.RepaintManager.setCurrentManager(new javax.swing.RepaintManager()
            {
                @Override
                public void addDirtyRegion(javax.swing.JComponent c, int x, int y, int w, int h)
                {
                    if (c == grid) asked.add(new java.awt.Rectangle(x, y, w, h));
                }
            });

            try
            {
                grid.setLayout(null);
                grid.setSize(size * 3, size * 3);

                org.traincontrol.gui.LayoutLabel station =
                    new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

                station.setBounds(size, size, size, size);

                grid.add(station);

                station.setAutonomyAnnotation(aCurvedStation());

                asked.clear();

                station.repaint();

                onRepaint.addAll(asked);

                asked.clear();

                station.setAutonomyAnnotation(null);

                onRemoval.addAll(asked);
            }
            finally
            {
                javax.swing.RepaintManager.setCurrentManager(was);
            }
        });

        java.awt.Rectangle around = new java.awt.Rectangle(size / 2, size / 2, size * 2, size * 2);

        assertTrue(onRepaint.stream().anyMatch(r -> r.contains(around)), "a station that spills was repainted alone - what"
            + " it drew on the squares beside it was not asked to be redrawn: " + onRepaint);

        assertTrue(onRemoval.stream().anyMatch(r -> r.contains(around)), "a station that spilled was taken away and only"
            + " its own square repainted - its spill stays on the squares beside it: " + onRemoval);
    }

    /**
     * A caption over the square a curve's station spills onto stays in front of the spill (OB-117), and the arrows
     * drawn again over the spill go no further than the spill.  Nine squares in the diagram's own container, a station
     * on an E-S curve in the middle, the square to its right a one-way road under an opaque yellow caption covering it:
     * none of the station's blue and none of the arrows' red or green shows on that square.
     *
     * MUTATION: draw the spill over the captions again, or the arrows again over the whole square, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testASpillIsDrawnUnderTheCaptionsOverIt()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        final int size = 30;

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size * 3, size * 3);
        grid.setBackground(java.awt.Color.WHITE);

        org.traincontrol.gui.LayoutLabel middle = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        middle.setBounds(size, size, size, size);
        middle.setAutonomyAnnotation(aCurvedStation());

        org.traincontrol.gui.LayoutLabel right = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        right.setOpaque(true);
        right.setBackground(java.awt.Color.WHITE);
        right.setBounds(2 * size, size, size, size);
        right.setAutonomyAnnotation(new org.traincontrol.automationui.TileAnnotation(java.util.Arrays.asList(
            new org.traincontrol.automationui.TileAnnotation.Mark(org.traincontrol.automationui.TilePorts.Side.W,
                org.traincontrol.automationui.TilePorts.Side.E, org.traincontrol.automationui.TileGraph.Direction.TOWARD_B)),
            -1, false, null, false, false, false, null, true, null));

        org.traincontrol.gui.widgets.StationCaption caption = new org.traincontrol.gui.widgets.StationCaption();

        caption.setOpaque(true);
        caption.setBackground(new java.awt.Color(255, 220, 0));
        caption.setBounds(2 * size, size, size, size);

        // in the order the diagram builds them: a caption in front of every tile
        grid.add(caption);
        grid.add(right);
        grid.add(middle);

        int[] covered = blueAndArrowsBeside(grid, size);

        int blue = covered[0], arrows = covered[1];

        assertEquals(blue, 0, "the curve's station spilled over the caption on the square beside it - " + blue + " pixels of"
            + " its blue in front of the caption, where on its own square it is behind one (RSA61-C2)");

        assertEquals(arrows, 0, "the arrows drawn again over the spill were drawn over the caption on their square - "
            + arrows + " pixels of red and green in front of it (RSA61-C2)");

        // THE CONTROL: the same square with the caption taken off shows both, so the zeros above counted colors the
        // square is painted in
        grid.remove(caption);

        int[] open = blueAndArrowsBeside(grid, size);

        assertTrue(open[0] > 0, "precondition: none of the station's blue is seen on the square beside it with no caption"
            + " over it, so the count of zero above says nothing about the caption");

        assertTrue(open[1] > 0, "precondition: none of the arrows' red or green is seen on the square with no caption over"
            + " it, so the count of zero above says nothing about the caption (the arrows are not the colours this looks for)");
    }

    /**
     * The grid painted over white, and on the middle row's right-hand square the pixels that are the station's blue and
     * the pixels that are the one-way arrows' red or green.
     *
     * @return the blue pixels, then the arrow pixels
     */
    private static int[] blueAndArrowsBeside(javax.swing.JPanel grid, int size)
    {
        BufferedImage shot = new BufferedImage(size * 3, size * 3, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size * 3, size * 3);

            grid.paint(g);
        }
        finally
        {
            g.dispose();
        }

        // THE ARROWS' COLORS ARE THE PROGRAM'S OWN, so a color changed there is followed and not left uncounted
        java.awt.Color oneWay = (java.awt.Color) support.ProgramConstants.get(
            org.traincontrol.automationui.TileAnnotation.class, "ONE_WAY");
        java.awt.Color closedWay = (java.awt.Color) support.ProgramConstants.get(
            org.traincontrol.automationui.TileAnnotation.class, "CLOSED");

        int blue = 0, arrows = 0;

        for (int x = 2 * size; x < 3 * size; x++)
        {
            for (int y = size; y < 2 * size; y++)
            {
                int rgb = shot.getRGB(x, y), r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

                if (b > 150 && r < 90 && gr < 90) blue++;

                if (near(r, gr, b, closedWay, 30, 40, 40) || near(r, gr, b, oneWay, 30, 30, 30)) arrows++;
            }
        }

        return new int[] {blue, arrows};
    }

    /** Whether a pixel is within these margins of a color, channel by channel. */
    private static boolean near(int r, int g, int b, java.awt.Color colour, int marginR, int marginG, int marginB)
    {
        return Math.abs(r - colour.getRed()) < marginR && Math.abs(g - colour.getGreen()) < marginG
            && Math.abs(b - colour.getBlue()) < marginB;
    }

    /**
     * The whole of every kind of station's spill is under the captions, at both sizes, not only the icon's OUTLINE (a
     * ring): at 60 pixels the body of a block or a terminus would otherwise be drawn in front of a caption.  Nine
     * squares, a station on an E-S curve in the middle, opaque yellow captions over the squares to its right and below:
     * none of the station's blue on either.
     *
     * MUTATION: make the region the icon's outline alone again, and this fails at 60 pixels.
     */
    @Test
    @support.DrawsFR118Icons
    public void testEveryKindOfSpillIsUnderTheCaptionsAtBothSizes()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        java.util.List<String> wrong = new java.util.ArrayList<>();

        for (int size : new int[] {30, 60})
        {
            for (org.traincontrol.automationui.TileAnnotation station : curvedStationsOfEveryKind())
            {
                int blue = spilledInFrontOfCaptions(size, station);

                if (blue > 0) wrong.add(station + " at " + size + " pixels: " + blue + " pixels of its blue");
            }
        }

        assertTrue(wrong.isEmpty(), "a curve's station spilled in front of the captions on the squares beside it, where on"
            + " its own square it is behind one (RSA62-C1):\n  " + String.join("\n  ", wrong));
    }

    /**
     * A station's spill region is built once for a size, and quickly: building it on every paint, as an `Area` of the
     * icon's stroked outline, stalls the window.  A named block on an N-E curve is asked twice and answers the same
     * region, built in well under a frame.
     *
     * MUTATION: build the region afresh on every call, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testASpillsRegionIsBuiltOnceAndQuickly()
    {
        org.traincontrol.automationui.TilePorts.Side n = org.traincontrol.automationui.TilePorts.Side.N;
        org.traincontrol.automationui.TilePorts.Side e = org.traincontrol.automationui.TilePorts.Side.E;

        for (int size : new int[] {30, 60})
        {
            org.traincontrol.automationui.TileAnnotation station = new org.traincontrol.automationui.TileAnnotation(
                java.util.Arrays.asList(new org.traincontrol.automationui.TileAnnotation.Mark(n, e, null)), -1, false,
                new org.traincontrol.automationui.TileAnnotation.Badge(true, false, false, false, true, n, e, false, false,
                    null), false, true, false, null, true, null);

            BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g = shot.createGraphics();

            try
            {
                station.paint(g, size, size);
            }
            finally
            {
                g.dispose();
            }

            long began = System.nanoTime();

            java.awt.Shape first = station.spillShape(size, size);

            long took = (System.nanoTime() - began) / 1_000_000;

            assertNotNull(first, "precondition: a named block on an N-E curve at " + size + " pixels spills nothing");

            assertSame(station.spillShape(size, size), first, "a station's spill region at " + size + " pixels is built"
                + " again each time it is asked - on every paint of the diagram (RSA62-B1)");

            assertTrue(took < 100, "a station's spill region at " + size + " pixels took " + took + " ms to build - Adam's"
                + " window stalls while it does (RSA62-B1)");
        }
    }

    /**
     * The pixels of this station's blue on the squares to the right of and below the middle one, each under an opaque
     * caption, in the diagram's own container with the station on an E-S curve in the middle.
     */
    private static int spilledInFrontOfCaptions(int size, org.traincontrol.automationui.TileAnnotation station)
    {
        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size * 3, size * 3);
        grid.setBackground(java.awt.Color.WHITE);

        java.awt.Rectangle[] covered = {new java.awt.Rectangle(2 * size, size, size, size),
            new java.awt.Rectangle(size, 2 * size, size, size)};

        // in the order the diagram builds them: a caption in front of every tile
        for (java.awt.Rectangle square : covered)
        {
            org.traincontrol.gui.widgets.StationCaption caption = new org.traincontrol.gui.widgets.StationCaption();

            caption.setOpaque(true);
            caption.setBackground(new java.awt.Color(255, 220, 0));
            caption.setBounds(square);

            grid.add(caption);
        }

        for (java.awt.Rectangle square : covered)
        {
            org.traincontrol.gui.LayoutLabel beside = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

            beside.setOpaque(true);
            beside.setBackground(java.awt.Color.WHITE);
            beside.setBounds(square);

            grid.add(beside);
        }

        org.traincontrol.gui.LayoutLabel middle = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        middle.setBounds(size, size, size, size);
        middle.setAutonomyAnnotation(station);

        grid.add(middle);

        BufferedImage shot = new BufferedImage(size * 3, size * 3, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size * 3, size * 3);

            grid.paint(g);
        }
        finally
        {
            g.dispose();
        }

        int blue = 0;

        for (java.awt.Rectangle square : covered)
        {
            for (int x = square.x; x < square.x + square.width; x++)
            {
                for (int y = square.y; y < square.y + square.height; y++)
                {
                    int rgb = shot.getRGB(x, y), r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

                    if (b > 150 && r < 90 && gr < 90) blue++;
                }
            }
        }

        return blue;
    }

    /**
     * A curve's station spills onto the squares beside it at the middle of their edges, where their arrows are, and the
     * ingress/egress arrows must stay visible, especially on curves: the arrows of a square a spill reaches are drawn
     * back over it.  Nine squares, a station of every kind on an E-S curve in the middle (the may-turn and must-turn
     * icons and a terminus are longer than the curve's chord), the square to its right a one-way road from W to E, or
     * the one below from N to S, at 30 and 60 pixels: that square's arrows have all their pixels with the station as
     * without.
     *
     * MUTATION: take the arrows' pass over the spill out of `LayoutGrid.newDiagramContainer`, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testANeighboursArrowsAreDrawnOverASpill()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        for (int size : new int[] {30, 60})
        {
            for (boolean below : new boolean[] {false, true})
            {
                int alone = arrowsBesideACurvedStation(size, below, null);

                assertTrue(alone > 0, "precondition: the square " + (below ? "below" : "beside") + " the station drew no"
                    + " arrows at " + size + " pixels");

                for (org.traincontrol.automationui.TileAnnotation station : curvedStationsOfEveryKind())
                {
                    int beside = arrowsBesideACurvedStation(size, below, station);

                    assertTrue(beside >= alone * 0.95, "the curve's " + station + " spilled over the arrows of the square "
                        + (below ? "below" : "beside") + " it at " + size + " pixels: " + beside + " of their " + alone
                        + " pixels showing - Adam: \"make sure that the optional ingress/egress arrows remain visible,"
                        + " especially on curves\"");
                }
            }
        }
    }

    /** A named station on an E-S curve of every kind: plain, may turn, must turn, a terminus each way. */
    private static java.util.List<org.traincontrol.automationui.TileAnnotation> curvedStationsOfEveryKind()
    {
        org.traincontrol.automationui.TilePorts.Side e = org.traincontrol.automationui.TilePorts.Side.E;
        org.traincontrol.automationui.TilePorts.Side s = org.traincontrol.automationui.TilePorts.Side.S;

        java.util.List<org.traincontrol.automationui.TileAnnotation> kinds = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.TileAnnotation.Badge badge : new org.traincontrol.automationui.TileAnnotation.Badge[] {
            new org.traincontrol.automationui.TileAnnotation.Badge(true, false, false, false, true, e, s, false, false, null),
            new org.traincontrol.automationui.TileAnnotation.Badge(true, true, false, false, true, e, s, true, false, null),
            new org.traincontrol.automationui.TileAnnotation.Badge(true, true, false, false, true, e, s, false, false, null),
            new org.traincontrol.automationui.TileAnnotation.Badge(true, false, false, false, true, e, s, false, false, e),
            new org.traincontrol.automationui.TileAnnotation.Badge(true, false, false, false, true, e, s, false, false, s)})
        {
            kinds.add(new org.traincontrol.automationui.TileAnnotation(java.util.Arrays.asList(
                new org.traincontrol.automationui.TileAnnotation.Mark(e, s, null)), -1, false, badge, false, false, false,
                null, true, null));
        }

        return kinds;
    }

    /**
     * The red and green pixels of the arrows on the square to the right of the middle one - or below it, a road from N to
     * S - in the diagram's own container, with a station on an E-S curve in the middle, or none.
     */
    private static int arrowsBesideACurvedStation(int size, boolean below,
        org.traincontrol.automationui.TileAnnotation station)
    {

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size * 3, size * 3);
        grid.setBackground(java.awt.Color.WHITE);

        org.traincontrol.gui.LayoutLabel middle = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        middle.setBounds(size, size, size, size);

        if (station != null) middle.setAutonomyAnnotation(station);

        grid.add(middle);

        org.traincontrol.gui.LayoutLabel right = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        right.setOpaque(true);
        right.setBackground(java.awt.Color.WHITE);
        right.setBounds(below ? size : 2 * size, below ? 2 * size : size, size, size);
        right.setAutonomyAnnotation(new org.traincontrol.automationui.TileAnnotation(java.util.Arrays.asList(
            new org.traincontrol.automationui.TileAnnotation.Mark(
                below ? org.traincontrol.automationui.TilePorts.Side.N : org.traincontrol.automationui.TilePorts.Side.W,
                below ? org.traincontrol.automationui.TilePorts.Side.S : org.traincontrol.automationui.TilePorts.Side.E,
                org.traincontrol.automationui.TileGraph.Direction.TOWARD_B)),
            -1, false, null, false, false, false, null, true, null));

        // painted after the station, as a later square on the page is
        grid.add(right, 0);

        BufferedImage shot = new BufferedImage(size * 3, size * 3, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size * 3, size * 3);

            grid.paint(g);
        }
        finally
        {
            g.dispose();
        }

        int n = 0;

        for (int x = right.getX(); x < right.getX() + size; x++)
        {
            for (int y = right.getY(); y < right.getY() + size; y++)
            {
                int rgb = shot.getRGB(x, y), r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

                // the arrows' own red and green, solid
                if ((Math.abs(r - 200) < 30 && gr < 40 && b < 40) || (Math.abs(r - 70) < 30 && Math.abs(gr - 205) < 30
                    && Math.abs(b - 90) < 30)) n++;
            }
        }

        return n;
    }

    /**
     * Writing of the user's own on the diagram has a white halo round its letters, as an address has (a smaller
     * outline), so it can be read over black track.  A label as the grid builds one, painted over black, shows white
     * round its letters at 30 and 60 pixels, and the halo is thinner than an address's: no wider than a sixth of their
     * size.
     *
     * MUTATION: draw the label without its halo, and this fails.
     */
    @Test
    public void testTheDiagramsOwnWritingHasAHalo()
    {
        for (int size : new int[] {30, 60})
        {
            org.traincontrol.gui.widgets.StationCaption text = new org.traincontrol.gui.widgets.StationCaption();

            text.setText("Yard");
            text.setForeground(java.awt.Color.BLACK);
            text.setFont(new java.awt.Font(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, size / 2));
            text.setHalo(true);
            text.setSize(text.getPreferredSize());

            BufferedImage shot = new BufferedImage(text.getWidth(), text.getHeight(), BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g = shot.createGraphics();

            try
            {
                g.setColor(java.awt.Color.BLACK);
                g.fillRect(0, 0, shot.getWidth(), shot.getHeight());

                text.paint(g);
            }
            finally
            {
                g.dispose();
            }

            int white = 0, widest = 0;

            for (int y = 0; y < shot.getHeight(); y++)
            {
                int run = 0;

                for (int x = 0; x < shot.getWidth(); x++)
                {
                    int rgb = shot.getRGB(x, y);

                    boolean light = ((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) > 200 && (rgb & 0xFF) > 200;

                    if (light) white++;

                    run = light ? run + 1 : 0;
                    widest = Math.max(widest, run);
                }
            }

            assertTrue(white > 20, "the diagram's own writing at " + size + " pixels showed " + white + " pixels of white"
                + " over black - no halo, so it cannot be read over track (Adam: \"Give regular text labels on the track"
                + " diagram (.text) the same halo as address labels\")");

            // a halo stroke round a letter: its two sides and the thickness between - well under a whole word's width
            assertTrue(widest < text.getWidth() / 2, "the halo at " + size + " pixels ran " + widest + " pixels across in"
                + " one row - a box behind the writing rather than a halo round its letters");
        }
    }

    /**
     * The outline of a square a curve station's spill reaches is drawn over the spill, as its arrows are, or the
     * selection outline would be broken where the station reached it.  Nine squares, a station on an E-S curve in the
     * middle, the square to its right selected: all of its outline's orange with the station as without.
     *
     * MUTATION: leave the outline out of what is drawn again over a spill, and this fails.
     */
    @Test
    @support.DrawsFR118Icons
    public void testANeighboursOutlineIsDrawnOverASpill()
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        for (int size : new int[] {30, 60})
        {
            int alone = outlineBesideACurvedStation(size, null);

            assertTrue(alone > 0, "precondition: the selected square drew no outline at " + size + " pixels");

            for (org.traincontrol.automationui.TileAnnotation station : curvedStationsOfEveryKind())
            {
                int beside = outlineBesideACurvedStation(size, station);

                assertEquals(beside, alone, "the curve's " + station + " spilled over the outline of the selected square"
                    + " beside it at " + size + " pixels (RSA63-C4)");
            }
        }
    }

    /** The orange of a selection outline on the square to the right of the middle one, a station on its curve or none. */
    private static int outlineBesideACurvedStation(int size, org.traincontrol.automationui.TileAnnotation station)
    {
        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size * 3, size * 3);
        grid.setBackground(java.awt.Color.WHITE);

        org.traincontrol.gui.LayoutLabel middle = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        middle.setBounds(size, size, size, size);

        if (station != null) middle.setAutonomyAnnotation(station);

        grid.add(middle);

        org.traincontrol.gui.LayoutLabel right = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        right.setOpaque(true);
        right.setBackground(java.awt.Color.WHITE);
        right.setBounds(2 * size, size, size, size);
        right.setAutonomyAnnotation(new org.traincontrol.automationui.TileAnnotation(java.util.Arrays.asList(
            new org.traincontrol.automationui.TileAnnotation.Mark(org.traincontrol.automationui.TilePorts.Side.W,
                org.traincontrol.automationui.TilePorts.Side.E, org.traincontrol.automationui.TileGraph.Direction.BOTH)),
            -1, true, null, false, false, false, null, true, null));

        grid.add(right);

        BufferedImage shot = new BufferedImage(size * 3, size * 3, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size * 3, size * 3);

            grid.paint(g);
        }
        finally
        {
            g.dispose();
        }

        int orange = 0;

        for (int x = 2 * size; x < 3 * size; x++)
        {
            for (int y = size; y < 2 * size; y++)
            {
                int rgb = shot.getRGB(x, y), r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

                if (r > 230 && Math.abs(gr - 140) < 30 && b < 40) orange++;
            }
        }

        return orange;
    }

    /**
     * The Medium size is between Small and Large in the size list, 46 pixels a square (FR-122): the list names Small,
     * Medium and Large in that order, and they are 30, 46 and 60.
     *
     * MUTATION: give Medium Large's size, and this fails.
     *
     * @throws Exception reflection
     */
    @Test
    public void testTheMediumSizeIsBetweenSmallAndLarge() throws Exception
    {
        java.lang.reflect.Field sizesField = TrainControlUI.class.getDeclaredField("layoutSizes");
        sizesField.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> sizes = (java.util.Map<String, Integer>) sizesField.get(ui);

        assertEquals(new java.util.ArrayList<>(sizes.keySet()), java.util.Arrays.asList(
            org.traincontrol.util.I18n.t("layout.ui.small"), org.traincontrol.util.I18n.t("layout.ui.medium"),
            org.traincontrol.util.I18n.t("layout.ui.large")), "the sizes are not Small, Medium and Large, in that order");

        assertEquals(new java.util.ArrayList<>(sizes.values()), java.util.Arrays.asList(30, 46, 60),
            "Small, Medium and Large are not 30, 46 and 60 pixels a square - Adam: \"Let's implement the 46px diagram\"");

        java.lang.reflect.Field listField = TrainControlUI.class.getDeclaredField("SizeList");
        listField.setAccessible(true);

        javax.swing.JComboBox<?> list = (javax.swing.JComboBox<?>) listField.get(ui);

        java.util.List<String> offered = new java.util.ArrayList<>();

        for (int i = 0; i < list.getItemCount(); i++) offered.add(String.valueOf(list.getItemAt(i)));

        assertEquals(offered, new java.util.ArrayList<>(sizes.keySet()), "the size list does not offer the three sizes");
    }

    /**
     * A square at the Medium size is drawn from the Large art scaled down (FR-122, the cheap 60px scaling), its own
     * picture 46 pixels across, cached apart from the Large one it comes from; checked for a sensor, a switch, a curve
     * and a signal.
     *
     * MUTATION: look for a 46 pixel icon set, or cache a Medium picture under its Large one's key, and this fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    public void testAMediumSquareIsTheLargeArtScaledDown() throws Exception
    {
        org.traincontrol.base.LayoutDiagramComponent.componentType[] kinds = {
            org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK,
            org.traincontrol.base.LayoutDiagramComponent.componentType.SWITCH_LEFT,
            org.traincontrol.base.LayoutDiagramComponent.componentType.CURVE,
            org.traincontrol.base.LayoutDiagramComponent.componentType.SIGNAL};

        for (org.traincontrol.base.LayoutDiagramComponent.componentType kind : kinds)
        {
            org.traincontrol.base.LayoutDiagramComponent c = new org.traincontrol.base.LayoutDiagramComponent(kind, 0, 0,
                1, 0, kind == org.traincontrol.base.LayoutDiagramComponent.componentType.CURVE ? 0 : 5, 10,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2);

            // loaded before it is measured: a scaled picture may still be arriving
            java.awt.Image image = new javax.swing.ImageIcon(c.getImage(46, true)).getImage();

            java.awt.image.BufferedImage drawn = new java.awt.image.BufferedImage(46, 46, BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g = drawn.createGraphics();

            try
            {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, 46, 46);

                g.drawImage(image, 0, 0, null);
            }
            finally
            {
                g.dispose();
            }

            assertEquals(image.getWidth(null), 46, kind + " at the Medium size is not 46 pixels across");

            int dark = 0;

            for (int x = 0; x < 46; x++)
            {
                for (int y = 0; y < 46; y++)
                {
                    int rgb = drawn.getRGB(x, y);

                    if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) < 200) dark++;
                }
            }

            assertTrue(dark > 46, kind + " at the Medium size drew no track: " + dark + " dark pixels");

            assertTrue(c.getImageName(46, true).contains("icons60"), kind + " at the Medium size is not drawn from the"
                + " Large art: " + c.getImageName(46, true));

            assertFalse(c.getImageKey(46, true).equals(c.getImageKey(60, true)), kind + " at the Medium size is cached"
                + " under its Large picture's key, so one size draws the other's picture");
        }
    }

    /**
     * The Medium pop-up button opens the page in a window at the Medium size (FR-122): its squares 46 pixels across.
     *
     * MUTATION: leave the button's handler empty again, and this fails.
     *
     * @throws Exception reflection, the event thread
     */
    @Test
    public void testTheMediumButtonOpensAMediumPopUp() throws Exception
    {
        java.lang.reflect.Field buttonField = TrainControlUI.class.getDeclaredField("mediumButton");
        buttonField.setAccessible(true);

        final javax.swing.JButton medium = (javax.swing.JButton) buttonField.get(ui);

        java.util.Set<java.awt.Window> before = new java.util.HashSet<>(java.util.Arrays.asList(java.awt.Window.getWindows()));

        javax.swing.SwingUtilities.invokeAndWait(medium::doClick);

        org.traincontrol.gui.LayoutPopupUI opened = null;

        for (long end = System.currentTimeMillis() + 15000; opened == null && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (w instanceof org.traincontrol.gui.LayoutPopupUI && w.isDisplayable() && !before.contains(w))
                {
                    opened = (org.traincontrol.gui.LayoutPopupUI) w;
                }
            }
        }

        assertNotNull(opened, "the Medium button opened no pop-up window");

        final org.traincontrol.gui.LayoutPopupUI popup = opened;

        try
        {
            Thread.sleep(1500);

            final int[] width = {-1};

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                java.util.Deque<java.awt.Component> todo = new java.util.ArrayDeque<>();

                todo.add(popup.getContentPane());

                while (!todo.isEmpty() && width[0] < 0)
                {
                    java.awt.Component c = todo.pop();

                    if (c instanceof org.traincontrol.gui.LayoutLabel && ((org.traincontrol.gui.LayoutLabel) c).getComponent() != null)
                    {
                        width[0] = c.getWidth();
                    }

                    if (c instanceof java.awt.Container) for (java.awt.Component k : ((java.awt.Container) c).getComponents()) todo.add(k);
                }
            });

            assertEquals(width[0], 46, "the Medium button's pop-up draws its squares " + width[0] + " pixels across");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(popup::dispose);
        }
    }

    /**
     * A pop-up's title names the configuration that is loaded, beside the page, and follows it (FR-127).
     *
     * A pop-up open while the configuration is unloaded, loaded, switched, renamed and unloaded is asked for its title
     * after each; a title with nothing loaded is today's.  Run last (priority 1) because unloading rebuilds the session
     * the other claims ask the window for.
     *
     * MUTATION: build the title from the page alone, or leave a rename or an unload out of the places that set it
     * again, and this fails.
     *
     * @throws Exception reflection, the store, the event thread
     */
    @Test(priority = 1)
    public void testAPopUpTitleNamesTheLoadedConfiguration() throws Exception
    {
        final String first = theResumedConfiguration();

        assertNotNull(first, "precondition: the start-up resume loaded no configuration, so there is none to name");

        final org.traincontrol.gui.LayoutPopupUI popup = openAMediumPopUp();

        try
        {
            // OPENED WITH IT LOADED: named from the start
            popUpTitleIs(popup, first, "of a pop-up opened with " + first + " loaded");

            // UNLOADED: today's title
            unloadAutonomy();

            popUpTitleIs(popup, null, "after " + first + " was unloaded");

            // LOADED again: the title names it
            loadConfiguration(first);

            assertEquals(ui.getActiveDiagramConfiguration(), first, "precondition: " + first + " did not load");

            popUpTitleIs(popup, first, "after " + first + " was loaded");

            // SWITCHED to another configuration
            final String other = "FR-127 Yard";

            ui.getAutonomySession().getStore().createConfiguration(other, first, true);
            ui.getAutonomySession().save();

            loadConfiguration(other);

            assertEquals(ui.getActiveDiagramConfiguration(), other, "precondition: " + other + " did not load");

            popUpTitleIs(popup, other, "after switching to " + other);

            // RENAMED while loaded, the way the Rename door does it
            final String renamed = "FR-127 Siding";

            ui.getAutonomySession().getStore().renameConfiguration(other, renamed);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui.autonomySetupRenamed(other, renamed));

            popUpTitleIs(popup, renamed, "after " + other + " was renamed");

            // UNLOADED: today's title again
            unloadAutonomy();

            popUpTitleIs(popup, null, "after autonomy was unloaded");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(popup::dispose);

            // As the window started: the configuration loaded
            if (ui.getActiveDiagramConfiguration() == null) loadConfiguration(first);
        }
    }

    /**
     * The window's own render of the active locomotive keeps the configuration in a pop-up's title (FR-127).
     *
     * Two places set the title from that render: the one with a locomotive active, which adds its text after the page,
     * and the one with none, which takes it off.  Each is reached by making a locomotive active and then none, the
     * title set to something else first so that only the render can have written what is read, and the render waited
     * for through the window's own futures, since pumping the event thread does not wait for the worker that posts it.
     *
     * MUTATION: set either title from the page alone (`popup.getLayoutTitle() + ...`) in the render, and this fails.
     *
     * @throws Exception reflection, the event thread
     */
    @Test(priority = 1)
    public void testALocomotiveRenderKeepsTheConfigurationInAPopUpTitle() throws Exception
    {
        final String loaded = theResumedConfiguration();

        assertNotNull(loaded, "precondition: the start-up resume loaded no configuration, so there is none to keep");

        java.lang.reflect.Field activeField = TrainControlUI.class.getDeclaredField("activeLoc");
        activeField.setAccessible(true);

        java.lang.reflect.Field tickField = TrainControlUI.class.getDeclaredField("activeLocInTitle");
        tickField.setAccessible(true);

        final javax.swing.JCheckBoxMenuItem tick = (javax.swing.JCheckBoxMenuItem) tickField.get(ui);
        final org.traincontrol.base.Locomotive was = (org.traincontrol.base.Locomotive) activeField.get(ui);
        final boolean ticked = tick.isSelected();

        org.traincontrol.base.Locomotive shown = was;

        if (shown == null && !model.getLocList().isEmpty()) shown = model.getLocByName(model.getLocList().get(0));

        if (shown == null) throw new AssertionError("the fixture has no locomotive to make active");

        final org.traincontrol.base.Locomotive loc = shown;

        final org.traincontrol.gui.LayoutPopupUI popup = openAMediumPopUp();

        try
        {
            // The tick set, and not toggled: the setting is the operator's
            javax.swing.SwingUtilities.invokeAndWait(() -> tick.setSelected(true));

            final String page = popup.getLayoutTitle();

            // WITH A LOCOMOTIVE: the page, the configuration, then the locomotive's text
            waitForTheRender();
            activeField.set(ui, loc);
            javax.swing.SwingUtilities.invokeAndWait(() -> popup.setTitle("stale"));
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.repaintLoc(true, null));
            waitForTheRender();

            final String[] title = new String[2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                title[0] = popup.getTitle();
                title[1] = ui.getWindowTitleString();
            });

            assertTrue(title[1].contains(loc.getName()), "precondition: the window's title text does not carry the active"
                + " locomotive " + loc.getName() + ": '" + title[1] + "'");

            assertEquals(title[0], org.traincontrol.util.I18n.f("app.ui.windowPopupTitleConfiguration", page, loaded)
                + title[1], "the pop-up's title after the render with " + loc.getName() + " active");

            // WITH NONE: the locomotive's text off, the configuration still in
            waitForTheRender();
            activeField.set(ui, null);
            javax.swing.SwingUtilities.invokeAndWait(() -> popup.setTitle("stale"));
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.repaintLoc(true, null));
            waitForTheRender();

            javax.swing.SwingUtilities.invokeAndWait(() -> title[0] = popup.getTitle());

            assertEquals(title[0], org.traincontrol.util.I18n.f("app.ui.windowPopupTitleConfiguration", page, loaded),
                "the pop-up's title after the render with no locomotive active");
        }
        finally
        {
            waitForTheRender();
            activeField.set(ui, was);
            javax.swing.SwingUtilities.invokeAndWait(() -> tick.setSelected(ticked));
            javax.swing.SwingUtilities.invokeAndWait(() -> ui.repaintLoc(true, null));
            waitForTheRender();

            javax.swing.SwingUtilities.invokeAndWait(popup::dispose);
        }
    }

    /**
     * Until every locomotive render the window has queued is finished and the event thread has run what each posted.
     *
     * @throws Exception reflection, the event thread, a render that does not finish
     */
    @SuppressWarnings("unchecked")
    private void waitForTheRender() throws Exception
    {
        java.lang.reflect.Field futures = TrainControlUI.class.getDeclaredField("locFutures");
        futures.setAccessible(true);

        long deadline = System.currentTimeMillis() + 10000;

        while (System.currentTimeMillis() < deadline)
        {
            boolean done = true;

            synchronized (ui)
            {
                for (java.util.concurrent.Future<?> f
                    : new java.util.ArrayList<>((java.util.List<java.util.concurrent.Future<?>>) futures.get(ui)))
                {
                    done &= f.isDone();
                }
            }

            if (done)
            {
                // A render posts its painting before its future is done, so the painting is ahead of these in the queue
                javax.swing.SwingUtilities.invokeAndWait(() -> { });
                javax.swing.SwingUtilities.invokeAndWait(() -> { });

                return;
            }

            Thread.sleep(20);
        }

        throw new AssertionError("the window's locomotive render did not finish within ten seconds");
    }

    /**
     * The configuration the start-up resume loaded, waited for: it is posted behind the window's set-up, and a claim
     * run on its own reaches it first.
     *
     * @return its name, or null if nothing loaded in fifteen seconds
     * @throws Exception from the event thread
     */
    private static String theResumedConfiguration() throws Exception
    {
        for (long end = System.currentTimeMillis() + 15000;
            ui.getActiveDiagramConfiguration() == null && System.currentTimeMillis() < end; )
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });

            Thread.sleep(100);
        }

        return ui.getActiveDiagramConfiguration();
    }

    /**
     * Stops using autonomy, the way the Autonomy menu does.
     *
     * @throws Exception from the event thread
     */
    private void unloadAutonomy() throws Exception
    {
        final boolean[] unloaded = new boolean[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> unloaded[0] = ui.unloadAutonomy());

        for (int i = 0; i < 10; i++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

        assertTrue(unloaded[0], "precondition: Stop Using Autonomy was declined");

        assertNull(ui.getActiveDiagramConfiguration(), "precondition: a configuration is still loaded");
    }

    /**
     * Opens a pop-up with the Medium button and waits for it.
     *
     * @return the window
     * @throws Exception reflection, the event thread
     */
    private org.traincontrol.gui.LayoutPopupUI openAMediumPopUp() throws Exception
    {
        java.lang.reflect.Field buttonField = TrainControlUI.class.getDeclaredField("mediumButton");
        buttonField.setAccessible(true);

        final javax.swing.JButton medium = (javax.swing.JButton) buttonField.get(ui);

        java.util.Set<java.awt.Window> before = new java.util.HashSet<>(java.util.Arrays.asList(java.awt.Window.getWindows()));

        javax.swing.SwingUtilities.invokeAndWait(medium::doClick);

        for (long end = System.currentTimeMillis() + 15000; System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (w instanceof org.traincontrol.gui.LayoutPopupUI && w.isDisplayable() && !before.contains(w))
                {
                    javax.swing.SwingUtilities.invokeAndWait(() -> { });

                    return (org.traincontrol.gui.LayoutPopupUI) w;
                }
            }
        }

        fail("the Medium button opened no pop-up window");

        return null;
    }

    /**
     * Loads a configuration the way the Autonomy menu does.
     *
     * @param name the configuration
     * @throws Exception from the event thread
     */
    private void loadConfiguration(final String name) throws Exception
    {
        javax.swing.SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(name, false));

        for (int i = 0; i < 10; i++) javax.swing.SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * What a pop-up's title says about the configuration (FR-127).
     *
     * @param popup the window
     * @param configuration the one loaded, or null for none
     * @param when what has just happened, for the message
     * @throws Exception from the event thread
     */
    private void popUpTitleIs(final org.traincontrol.gui.LayoutPopupUI popup, String configuration, String when)
        throws Exception
    {
        for (int i = 0; i < 10; i++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

        final String[] title = new String[2];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            title[0] = popup.getTitle();
            title[1] = popup.getLayoutTitle();
        });

        if (configuration != null)
        {
            // The page taken out first: a configuration called "Main" is in every title of the page "1 - Main"
            assertTrue(title[0].contains(title[1]) && title[0].replace(title[1], "").contains(configuration), "the"
                + " pop-up's title '" + title[0] + "' does not name both the page " + title[1] + " and the configuration "
                + configuration + " " + when + " (FR-127)");
        }

        String expected = configuration == null ? title[1]
            : org.traincontrol.util.I18n.f("app.ui.windowPopupTitleConfiguration", title[1], configuration);

        assertEquals(title[0], expected + ui.getWindowTitleString(), "the pop-up's title " + when);
    }

    /**
     * The main window's diagram scrolls half a square at a time on both axes, at each of the three sizes (MT-726,
     * MT-727): each scroll bar steps half the shown size, and a notch of the wheel moves the diagram three of those
     * steps.
     *
     * MUTATION: leave the main window's scroll bars at their pixel step, step a whole square, or step every size as
     * Small, and this fails.
     *
     * @throws Exception reflection, the event thread
     */
    @Test
    public void testTheDiagramScrollsHalfASquareAtATime() throws Exception
    {
        java.lang.reflect.Field listField = TrainControlUI.class.getDeclaredField("SizeList");
        listField.setAccessible(true);

        @SuppressWarnings("unchecked")
        final javax.swing.JComboBox<Object> list = (javax.swing.JComboBox<Object>) listField.get(ui);

        java.lang.reflect.Field sizesField = TrainControlUI.class.getDeclaredField("layoutSizes");
        sizesField.setAccessible(true);

        @SuppressWarnings("unchecked")
        java.util.Map<String, Integer> sizes = (java.util.Map<String, Integer>) sizesField.get(ui);

        java.lang.reflect.Field areaField = TrainControlUI.class.getDeclaredField("LayoutArea");
        areaField.setAccessible(true);

        final javax.swing.JScrollPane area = (javax.swing.JScrollPane) areaField.get(ui);

        final Object was = list.getSelectedItem();

        try
        {
            for (int i = 0; i < list.getItemCount(); i++)
            {
                final Object choice = list.getItemAt(i);

                final int size = sizes.get(String.valueOf(choice));

                javax.swing.SwingUtilities.invokeAndWait(() -> list.setSelectedItem(choice));

                settleTheDiagram();

                final int[] seen = new int[4];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    javax.swing.JScrollBar down = area.getVerticalScrollBar();

                    seen[0] = down.getUnitIncrement(1);
                    seen[1] = area.getHorizontalScrollBar().getUnitIncrement(1);

                    // and a notch of the wheel, where the diagram runs on below the window
                    seen[2] = -1;

                    if (down.isVisible() && down.getBlockIncrement(1) >= 3 * (size / 2))
                    {
                        down.setValue(0);

                        area.dispatchEvent(new java.awt.event.MouseWheelEvent(area, java.awt.event.MouseEvent.MOUSE_WHEEL,
                            System.currentTimeMillis(), 0, 10, 10, 0, false, java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,
                            3, 1));

                        seen[2] = down.getValue();
                        seen[3] = Math.min(3 * (size / 2), down.getMaximum() - down.getVisibleAmount());

                        down.setValue(0);
                    }
                });

                assertEquals(seen[0], size / 2, "at " + choice + " the diagram scrolls down " + seen[0] + " pixels a step,"
                    + " not half a square - Adam: \"Make it half a square at a time\"");

                assertEquals(seen[1], size / 2, "at " + choice + " the diagram scrolls across " + seen[1] + " pixels a"
                    + " step, not half a square");

                if (seen[2] >= 0)
                {
                    assertEquals(seen[2], seen[3], "at " + choice + " a notch of the wheel moved the diagram " + seen[2]
                        + " pixels, not three half squares");
                }
            }
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> list.setSelectedItem(was));

            settleTheDiagram();
        }
    }

    /**
     * A diagram popped out of the main window scrolls half a square at a time on both axes too (MT-726, MT-727): the
     * Medium button's window steps 23 pixels each way.
     *
     * MUTATION: leave the pop-up's scroll bars at their pixel step, or step it down only, and this fails.
     *
     * @throws Exception reflection, the event thread
     */
    @Test
    public void testAPoppedOutDiagramScrollsHalfASquareAtATime() throws Exception
    {
        java.lang.reflect.Field buttonField = TrainControlUI.class.getDeclaredField("mediumButton");
        buttonField.setAccessible(true);

        final javax.swing.JButton medium = (javax.swing.JButton) buttonField.get(ui);

        java.util.Set<java.awt.Window> before = new java.util.HashSet<>(java.util.Arrays.asList(java.awt.Window.getWindows()));

        javax.swing.SwingUtilities.invokeAndWait(medium::doClick);

        org.traincontrol.gui.LayoutPopupUI opened = null;

        for (long end = System.currentTimeMillis() + 15000; opened == null && System.currentTimeMillis() < end; )
        {
            Thread.sleep(100);

            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (w instanceof org.traincontrol.gui.LayoutPopupUI && w.isDisplayable() && !before.contains(w))
                {
                    opened = (org.traincontrol.gui.LayoutPopupUI) w;
                }
            }
        }

        assertNotNull(opened, "precondition: the Medium button opened no pop-up window");

        final org.traincontrol.gui.LayoutPopupUI popup = opened;

        try
        {
            java.lang.reflect.Field paneField = org.traincontrol.gui.LayoutPopupUI.class.getDeclaredField("jScrollPane1");
            paneField.setAccessible(true);

            final javax.swing.JScrollPane pane = (javax.swing.JScrollPane) paneField.get(popup);

            final int[] seen = new int[2];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                seen[0] = pane.getVerticalScrollBar().getUnitIncrement(1);
                seen[1] = pane.getHorizontalScrollBar().getUnitIncrement(1);
            });

            assertEquals(seen[0], TrainControlUI.MEDIUM_TILE_SIZE / 2, "the Medium pop-up scrolls down " + seen[0]
                + " pixels a step, not half a square");

            assertEquals(seen[1], TrainControlUI.MEDIUM_TILE_SIZE / 2, "the Medium pop-up scrolls across " + seen[1]
                + " pixels a step, not half a square");
        }
        finally
        {
            javax.swing.SwingUtilities.invokeAndWait(popup::dispose);
        }
    }

    /**
     * Lets a size or page change finish on the main window: the posted repaint, the grid renderer's job behind it, and
     * what that job posts in turn.
     *
     * @throws Exception reflection, the event thread
     */
    private static void settleTheDiagram() throws Exception
    {
        java.lang.reflect.Field rendererField = TrainControlUI.class.getDeclaredField("LayoutGridRenderer");
        rendererField.setAccessible(true);

        java.util.concurrent.ExecutorService renderer = (java.util.concurrent.ExecutorService) rendererField.get(ui);

        for (int round = 0; round < 3; round++)
        {
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            javax.swing.SwingUtilities.invokeAndWait(() -> { });

            renderer.submit(() -> { }).get(30, java.util.concurrent.TimeUnit.SECONDS);

            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
        }
    }

    /**
     * The autonomy editor's Show Trains switch hides the trains on its diagram (FR-119), independently of the viewer: a
     * square with a train standing on it draws it while its switch says yes and not while it says no, and a square no
     * switch was given, as on the track diagram, draws it.
     *
     * MUTATION: let a square draw its train whatever its switch says, and this fails.
     *
     * @throws Exception reflection
     */
    @Test
    public void testTheEditorsShowTrainsSwitchHidesItsTrains() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        java.lang.reflect.Method shown = org.traincontrol.gui.LayoutLabel.class.getMethod("setTrainsShown",
            java.util.function.BooleanSupplier.class);

        for (int size : new int[] {30, 60})
        {
            int drawn = trainInk(size, null, shown);

            assertTrue(drawn > 0, "precondition: a square with a train standing on it at " + size + " pixels drew nothing");

            assertEquals(trainInk(size, (java.util.function.BooleanSupplier) () -> true, shown), drawn, "a square whose"
                + " switch says to show the trains drew them differently at " + size + " pixels");

            assertEquals(trainInk(size, (java.util.function.BooleanSupplier) () -> false, shown), 0, "a square whose switch"
                + " says to hide the trains still drew its train at " + size + " pixels - Adam: \"have a \"show trains\""
                + " checkbox that toggles the locomotive icons\"");
        }
    }

    /**
     * The non-white pixels of a diagram of one square with a train standing on it, the square given this switch, or none.
     */
    private static int trainInk(int size, java.util.function.BooleanSupplier switched, java.lang.reflect.Method shown)
        throws Exception
    {
        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size * 3, size * 3);
        grid.setBackground(java.awt.Color.WHITE);

        org.traincontrol.gui.LayoutLabel square = new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        square.setBounds(size, size, size, size);
        square.setAutonomyOverlay(org.traincontrol.automationui.TileOverlay.parked(
            org.traincontrol.automationui.TilePorts.Side.E));

        if (switched != null) shown.invoke(square, switched);

        grid.add(square);

        BufferedImage shot = new BufferedImage(size * 3, size * 3, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, size * 3, size * 3);

            grid.paint(g);
        }
        finally
        {
            g.dispose();
        }

        int ink = 0;

        for (int x = 0; x < size * 3; x++)
        {
            for (int y = 0; y < size * 3; y++)
            {
                if ((shot.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) ink++;
            }
        }

        return ink;
    }

    /**
     * The halo is drawn round writing that shares its square with track (MT-716).
     *
     * Writing on a square that also has track or a sensor is set as HTML (`<html><br>` and the words) to stand a line
     * below the square, so the halo has to be drawn from the words inside the HTML too.  It lies round the words where
     * the HTML draws them: white on black, and the words' own blue inside it, a line down.
     *
     * MUTATION: skip HTML again, and this fails.
     */
    @Test
    public void testWritingThatSharesItsSquareHasAHaloToo()
    {
        for (int size : new int[] {30, 60})
        {
            org.traincontrol.gui.widgets.StationCaption text = new org.traincontrol.gui.widgets.StationCaption();

            text.setText("<html><br>Yard&nbsp;Two</html>");
            text.setForeground(java.awt.Color.BLUE);
            text.setFont(new java.awt.Font(org.traincontrol.gui.widgets.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, size / 2));
            text.setHalo(true);
            text.setSize(text.getPreferredSize());

            BufferedImage shot = new BufferedImage(text.getWidth(), text.getHeight(), BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g = shot.createGraphics();

            try
            {
                g.setColor(java.awt.Color.BLACK);
                g.fillRect(0, 0, shot.getWidth(), shot.getHeight());

                text.paint(g);
            }
            finally
            {
                g.dispose();
            }

            int white = 0, blue = 0, blueTop = Integer.MAX_VALUE, blueBottom = -1, whiteTop = Integer.MAX_VALUE,
                whiteBottom = -1, blueLeft = Integer.MAX_VALUE, whiteLeft = Integer.MAX_VALUE;

            for (int y = 0; y < shot.getHeight(); y++)
            {
                for (int x = 0; x < shot.getWidth(); x++)
                {
                    int rgb = shot.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, gr = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

                    if (r > 200 && gr > 200 && b > 200)
                    {
                        white++;
                        whiteTop = Math.min(whiteTop, y);
                        whiteBottom = Math.max(whiteBottom, y);
                        whiteLeft = Math.min(whiteLeft, x);
                    }
                    else if (b > 200 && r < 90 && gr < 90)
                    {
                        blue++;
                        blueTop = Math.min(blueTop, y);
                        blueBottom = Math.max(blueBottom, y);
                        blueLeft = Math.min(blueLeft, x);
                    }
                }
            }

            assertTrue(blue > 10, "precondition: the writing's own blue does not show at " + size + " pixels");

            assertTrue(blueTop > text.getHeight() / 3, "precondition: the HTML did not put the writing a line down at "
                + size + " pixels - its top is at " + blueTop + " of " + text.getHeight());

            assertTrue(white > 20, "writing that shares its square with track, at " + size + " pixels, showed " + white
                + " pixels of white over black - no halo (Adam: \"Check tiles that are not just only text, but have"
                + " something else on them plus text.\")");

            // round the words where they are drawn: its top and bottom a stroke beyond theirs, its left beside theirs
            int reach = Math.max(2, size / 6) + 2;

            assertTrue(whiteTop <= blueTop && blueTop - whiteTop <= reach && whiteBottom >= blueBottom
                && whiteBottom - blueBottom <= reach && Math.abs(whiteLeft - blueLeft) <= reach,
                "the halo at " + size + " pixels is not round the words: white rows " + whiteTop + "-" + whiteBottom
                + " from column " + whiteLeft + ", the words' rows " + blueTop + "-" + blueBottom + " from column "
                + blueLeft);
        }
    }

    /**
     * A train standing at its home is shown by a little house on the Auto tab, not by a teal badge (MT-709), in the
     * same spot as the (i).
     *
     * `AutoLocomotiveStatus.markFor` chooses the card's mark: the house at home, the (i) where there is a reason to
     * hover for, both where both, nothing otherwise; the badge is the tab's blue at home as everywhere else.
     *
     * MUTATION: put the teal back, or the house away, and this fails.
     *
     * @throws Exception reflection
     */
    @Test
    public void testATrainAtHomeShowsAHouseNotTeal() throws Exception
    {
        java.lang.reflect.Method markFor = org.traincontrol.gui.autonomy.AutoLocomotiveStatus.class.getDeclaredMethod("markFor",
            boolean.class, boolean.class);

        markFor.setAccessible(true);

        javax.swing.Icon house = (javax.swing.Icon) markFor.invoke(null, true, false);
        javax.swing.Icon info = (javax.swing.Icon) markFor.invoke(null, false, true);
        javax.swing.Icon both = (javax.swing.Icon) markFor.invoke(null, true, true);

        assertNull(markFor.invoke(null, false, false), "a card with nothing to say carries a mark");

        assertNotNull(house, "a train at its home carries no mark - Adam: \"put a little house icon below it\"");
        assertNotNull(info, "a card with a reason to hover for lost its (i)");
        assertNotNull(both, "a train at its home with a reason to hover for carries no mark");

        assertTrue(both.getIconWidth() > Math.max(house.getIconWidth(), info.getIconWidth()),
            "a train at its home with a reason to hover for does not show both the house and the (i)");

        assertTrue(house.getIconHeight() <= 16 && house.getIconHeight() >= 10, "the house is " + house.getIconHeight()
            + " pixels tall, not the (i)'s size");

        // drawn, and in the tab's blue
        BufferedImage shot = new BufferedImage(house.getIconWidth(), house.getIconHeight(), BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = shot.createGraphics();

        try
        {
            house.paintIcon(null, g, 0, 0);
        }
        finally
        {
            g.dispose();
        }

        int ink = 0;

        for (int x = 0; x < shot.getWidth(); x++)
        {
            for (int y = 0; y < shot.getHeight(); y++)
            {
                int argb = shot.getRGB(x, y);

                if (((argb >>> 24) & 0xFF) > 200 && (argb & 0xFF) > ((argb >> 16) & 0xFF) + 60) ink++;
            }
        }

        assertTrue(ink > 20, "the house draws " + ink + " pixels of blue");

        // and no teal: the card's at-home branch no longer paints the badge in COLOR_AT_HOME
        assertFalse(SourceOf.allCode().contains("setBackground(TrainControlUI.COLOR_AT_HOME)"), "the Auto tab still paints a train at"
            + " its home teal - Adam: \"instead of making home locations teal, put a little house icon\"");
    }

    /**
     * The grid gives the halo to every label it does not make a pill - the user's own writing, and an old file's
     * station label autonomy cannot act on - and to no pill, which has its own fill.  Asked of the grid's source, where
     * every label on the diagram is built.
     *
     * MUTATION: build the labels without the halo, and this fails.
     *
     * @throws Exception from reading the source
     */
    @Test
    public void testTheGridGivesEveryLabelThatIsNotAPillAHalo() throws Exception
    {
        assertTrue(SourceOf.allCode().contains("text.setHalo(!text.isPill());"), "the grid builds its labels without a halo - Adam:"
            + " \"Give regular text labels on the track diagram (.text) the same halo as address labels\"");
    }

    /** A named station on an E-S curve, as the diagram builds one. */
    private static org.traincontrol.automationui.TileAnnotation aCurvedStation()
    {
        org.traincontrol.automationui.TilePorts.Side e = org.traincontrol.automationui.TilePorts.Side.E;
        org.traincontrol.automationui.TilePorts.Side s = org.traincontrol.automationui.TilePorts.Side.S;

        return new org.traincontrol.automationui.TileAnnotation(java.util.Arrays.asList(
            new org.traincontrol.automationui.TileAnnotation.Mark(e, s, null)), -1, false,
            new org.traincontrol.automationui.TileAnnotation.Badge(true, false, false, false, true, e, s, false, false, null),
            false, false, false, null, true, null);
    }

    /**
     * The tunnel portal's wall is near white, #EEEEEE, and a pixel wider each side at 30 and two at 60 (three pixels
     * and seven), and the arch between the walls is still the track's width, 8 and 16 (FR-134).  The wall grew outward:
     * the track through the arch is where it was.  Read across the portal below the arch's curve, row 12 at 30, row 25
     * at 60.
     *
     * MUTATION: put the old tunnel art back, and this fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    public void testTheTunnelsWallIsWider() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            BufferedImage art = icon(size, "tunnel");

            int row = size >= 60 ? 25 : 12, wall = size >= 60 ? 7 : 3, arch = size >= 60 ? 16 : 8;

            java.util.List<int[]> runs = new java.util.ArrayList<>();

            for (int x = 0; x < size; x++)
            {
                int kind = (art.getRGB(x, row) & 0xFFFFFF) == 0xEEEEEE ? 1 : (art.getRGB(x, row) & 0xFFFFFF) == 0 ? 2 : 0;

                if (!runs.isEmpty() && runs.get(runs.size() - 1)[0] == kind) runs.get(runs.size() - 1)[1]++;
                else runs.add(new int[] {kind, 1});
            }

            StringBuilder said = new StringBuilder();

            for (int[] run : runs) said.append(run[0] == 1 ? "L" : run[0] == 2 ? "#" : ".").append(run[1]).append(' ');

            java.util.List<Integer> walls = new java.util.ArrayList<>();

            for (int[] run : runs) if (run[0] == 1) walls.add(run[1]);

            assertEquals(walls, java.util.Arrays.asList(wall, wall), "the tunnel's wall at " + size + " pixels, across row "
                + row + ": " + said + "- Adam asked for it #EEEEEE, and a pixel wider each side again at 30, two at 60 (\"Go for"
                + " C at +1\")");

            int between = 0;

            for (int i = 0; i < runs.size(); i++)
            {
                if (runs.get(i)[0] == 1 && i + 2 < runs.size() && runs.get(i + 2)[0] == 1) between = runs.get(i + 1)[1];
            }

            assertEquals(between, arch, "the tunnel's arch at " + size + " pixels is no longer the track's width: " + said);
        }
    }

    /**
     * The link and the two signal tiles meet the straight beside them with no seam: the band's rows where the track
     * comes in are the straight's own, at both sizes (FR-135).
     *
     * The track's band has a one-pixel edge in a dark gray (#333 at 30, #666 at 60), so an edge row of another shade on
     * a signal would be a lighter stripe between two straights.  The link is asked at its open side, the column the
     * track comes in by; a signal along both its edge rows, end to end.
     *
     * MUTATION: put the old Large signal back, or draw the link's shaft pure black to its edge, and this fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    public void testTheLinkAndTheSignalsMeetTheStraightWithNoSeam() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            BufferedImage straight = icon(size, "straight");

            java.util.List<Integer> band = new java.util.ArrayList<>();

            for (int y = 0; y < size; y++) if ((straight.getRGB(0, y) & 0xFFFFFF) != 0xFFFFFF) band.add(y);

            assertFalse(band.isEmpty(), "precondition: the straight at " + size + " has no band at its west edge");

            BufferedImage link = icon(size, "link");

            for (int y : band)
            {
                assertEquals(String.format("%06X", link.getRGB(0, y) & 0xFFFFFF), String.format("%06X",
                    straight.getRGB(0, y) & 0xFFFFFF), "the link at " + size + " meets the track with a seam at row " + y
                    + ": its shaft is not the straight's band there (FR-135)");
            }

            int top = band.get(0), bottom = band.get(band.size() - 1);

            for (String name : new String[] {"signal", "signal_active"})
            {
                BufferedImage signal = icon(size, name);

                for (int edge : new int[] {top, bottom})
                {
                    for (int x = 0; x < size; x++)
                    {
                        assertEquals(String.format("%06X", signal.getRGB(x, edge) & 0xFFFFFF), String.format("%06X",
                            straight.getRGB(0, edge) & 0xFFFFFF), "the " + name + " tile at " + size + " has its band's"
                            + " edge row " + edge + " in another shade at column " + x + " - a stripe against the"
                            + " straights either side (FR-135)");
                    }
                }
            }
        }
    }

    /**
     * The link's arrow is the same drawing above and below the track, and a signal's bar is centered with both its ends
     * alike: the drawn art is symmetric (FR-135).
     *
     * MUTATION: put the old Small link or signal art back, and this fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    public void testTheLinkAndTheSignalsBarAreSymmetric() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            BufferedImage link = icon(size, "link");

            for (int x = 0; x < size; x++)
            {
                for (int y = 0; y < size / 2; y++)
                {
                    assertEquals(link.getRGB(x, y) & 0xFFFFFF, link.getRGB(x, size - 1 - y) & 0xFFFFFF, "the link at "
                        + size + " is not the same above the track as below it, at column " + x + ", rows " + y + " and "
                        + (size - 1 - y) + " (FR-135)");
                }
            }

            BufferedImage straight = icon(size, "straight");

            for (String name : new String[] {"signal", "signal_active"})
            {
                BufferedImage signal = icon(size, name);

                for (int y = 0; y < size; y++)
                {
                    if ((straight.getRGB(0, y) & 0xFFFFFF) == 0xFFFFFF) continue;

                    for (int x = 0; x < size / 2; x++)
                    {
                        assertEquals(signal.getRGB(x, y) & 0xFFFFFF, signal.getRGB(size - 1 - x, y) & 0xFFFFFF, "the "
                            + name + " tile's bar at " + size + " is not centred, or its two ends differ, at row " + y
                            + ", columns " + x + " and " + (size - 1 - x) + " (FR-135)");
                    }
                }
            }
        }
    }

    /**
     * Green is the head's right-hand lamp and red its left, as the two lamps of a signal head - the redrawing kept them
     * where the shipped art had them (FR-135).
     *
     * MUTATION: draw both lamps in the same end, and this fails.
     *
     * @throws Exception from reading the art
     */
    @Test
    public void testTheSignalsLampsAreInTheirOwnEnds() throws Exception
    {
        for (int size : new int[] {30, 60})
        {
            for (String[] lamp : new String[][] {{"signal", "G", "right"}, {"signal_active", "R", "left"}})
            {
                BufferedImage signal = icon(size, lamp[0]);

                long sum = 0, count = 0;

                // the head, above the band
                for (int y = 0; y < size / 3; y++)
                {
                    for (int x = 0; x < size; x++)
                    {
                        int rgb = signal.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF;

                        boolean lit = "G".equals(lamp[1]) ? g > 200 && r < 60 : r > 200 && g < 60;

                        if (lit)
                        {
                            sum += x;
                            count++;
                        }
                    }
                }

                assertTrue(count > 0, "precondition: no lamp in the " + lamp[0] + " head at " + size);

                double centre = sum / (double) count;

                assertTrue("right".equals(lamp[2]) ? centre > size * 0.5 : centre < size * 0.5, "the " + lamp[0]
                    + " lamp at " + size + " is not in the head's " + lamp[2] + " end: its middle is at " + centre
                    + " (FR-135)");
            }
        }
    }
}
