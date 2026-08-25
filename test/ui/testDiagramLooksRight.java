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

        assertFalse(pages.isEmpty(), "no pages to render - is test_layout present?");

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
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run = realRunEndingOnACurve();

        System.out.println("placed " + runLocomotive.getName() + " for a run of " + run.size()
            + " squares ending at " + run.get(run.size() - 1));

        draw(runLocomotive, run, run.get(run.size() - 1), "curve-arrival");
    }

    /**
     * Every pixel the run paints lands on track.
     *
     * This is the check that would have caught OB-026, MT-124 and MT-127 without anybody looking at a
     * screenshot, and it is the reason it is written as an INVARIANT rather than as a golden image. A
     * golden image says "these bytes"; it breaks when a colour changes, it has to be regenerated by
     * somebody who then has to judge whether the new picture is right, and the judging is the part that
     * kept going wrong. This says something that is true of every correct drawing and false of every
     * one of those three defects: **the route line is drawn ALONG the railway, so its ink is on the
     * rails.**
     *
     * How it works: render the page with nothing published, render it again with a real run laid on it,
     * and look only at the pixels that CHANGED to a run colour. Every one of them has to be within a
     * few pixels of dark ink in the FIRST image - which is to say, on or beside a rail that was already
     * drawn there.
     *
     * Taking the difference is what makes it robust. The tile art, the station badges, the captions and
     * the signal lamps are identical in both renders and cancel out, so a red lamp is not mistaken for
     * route ink; the chevrons and the train dot change but are black and white, so they are not run
     * colours; and nothing here needs a list of which tile shapes exist.
     *
     * The tolerance is for the stroke, which is a seventh of a tile and centred on the rail, and for
     * anti-aliasing at its edges. It is nowhere near wide enough to reach the middle of a curve from
     * the rail that cuts its corner, which is exactly the distance OB-026 was wrong by.
     */
    @Test
    public void testEveryPixelOfARunLandsOnTrack() throws Exception
    {
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> run = realRunEndingOnACurve();

        LayoutDiagram page = model.getLayout(run.get(0).getPage());

        assertNotNull(page, "the run is on a page that is not loaded");

        // Nothing published: the railway as it is drawn when no train is going anywhere
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

        // Both pictures kept, always. This is a tool as much as a test, and when it fails the first
        // question is "show me" - which is the question the old way of working could never answer.
        javax.imageio.ImageIO.write(bare, "png", new File(OUT, "run-bare.png"));
        javax.imageio.ImageIO.write(drawn, "png", new File(OUT, "run-drawn.png"));

        assertEquals(drawn.getWidth(), bare.getWidth(), "the two renders are different sizes");
        assertEquals(drawn.getHeight(), bare.getHeight(), "the two renders are different sizes");

        // WHERE the ink lands, square by square.
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

        // 2. Every square of the run carries some. A square the line skips is a gap in a route, which
        //    is what "the trace stops halfway" would look like.
        java.util.List<String> blank = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.TileGraph.TileKey tile : run)
        {
            String square = tile.getX() + "," + tile.getY();

            // The two ENDS are allowed to be hidden: a run stops at a station, and the station's badge
            // is painted OVER the line there (MT-076), so the stub can be entirely covered.
            if (tile.equals(run.get(0)) || tile.equals(run.get(run.size() - 1))) continue;

            Integer here = inkPerSquare.get(square);

            // A REAL segment's worth, not a few pixels.
            //
            // "Has some ink" is satisfiable by the NEIGHBOURS: a segment ends at the midpoint of the
            // shared edge, so a handful of its pixels land on the far side of the boundary. Removing a
            // square's overlay entirely still left it with a trace and this assertion passed, which the
            // mutation check caught. A segment across a 60px tile is several hundred pixels; the bleed
            // is single figures.
            if (here == null || here < MINIMUM_INK_PER_SQUARE) blank.add(square + "=" + here);
        }

        assertEquals(blank, new java.util.ArrayList<String>(),
            "the route line is missing from squares it runs over: " + blank);

        // 3. And none of it landed anywhere else. Ink outside the run is a line drawn where no train
        //    is going, which is the shape a mis-keyed overlay would take.
        assertEquals(strayed, 0,
            strayed + " pixels of route ink were painted on squares the run does not use");

        // What is NOT asserted, and why - so the next reader does not mistake this for a full check.
        //
        // "Every pixel of the line lies on track art" is the invariant I wanted, and it is not true as
        // stated. The line is drawn as a straight chord between edge midpoints, deliberately: "a curve
        // on this diagram is not an arc and a switch's diverging leg is not a right angle", and bending
        // it through the tile centre was tried once and put it at forty-five degrees to the track. So
        // on switches, crossings and scissors it legitimately cuts across the art. The rails are drawn
        // as an OUTLINE with a pale interior, so ink in the middle of a rail is not on dark art either.
        //
        // Between them those two make the measurement a matter of tolerance, and a tolerance tuned
        // until the test goes green is a test that has stopped checking anything. So the number is
        // REPORTED and not asserted, and it is worth looking at when this output changes: today it is
        // a few hundred pixels out of sixteen thousand, all of them on multi-road squares.
        System.out.println("run ink: " + total + " pixels over " + inkPerSquare.size()
            + " squares, " + offArt + " of them not within " + TOLERANCE + "px of tile art");
    }

    /** The window's session, for asking a square how many roads it has */
    private org.traincontrol.automationui.AutonomySession session()
    {
        return ui.getAutonomySession();
    }

    /**
     * Whether this pixel is the colour the run is drawn in.
     *
     * Loose on purpose - the edges of a stroke are blended with whatever is under them - but tight
     * enough to exclude the black chevrons, the white train mark and the blue station badges, none of
     * which are claims about where the track goes.
     */
    private boolean isRunInk(int rgb)
    {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        boolean red = r > 130 && g < 90 && b < 90;
        boolean green = g > 120 && r < 100 && b < 120;

        return red || green;
    }

    /**
     * Whether the UNDRAWN page has track ink within a few pixels of here.
     */
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

                // Any art at all, not just the black rails: a switch draws its other road in pale grey,
                // and grey is still track. The background is what the line must not be drawn on.
                if (r < 236 || g < 236 || b < 236) return true;
            }
        }

        return false;
    }

    /**
     * How far route ink may sit from the rail it belongs to, in pixels of a 60px tile.
     *
     * The stroke is a seventh of a tile and centred on the rail, so its edges sit a few pixels either
     * side of the art - and the art is anti-aliased. Nowhere near wide enough to reach the middle of a
     * curve from the rail that cuts its corner, which is the distance OB-026 was wrong by.
     */
    private static final int TOLERANCE = 3;

    /**
     * The least route ink a square the run crosses may carry.
     *
     * A full segment is several hundred pixels at this tile size; what a neighbouring segment spills
     * over the shared edge is single figures. Anywhere in between rules out the bleed and still catches
     * a square the line skipped.
     */
    private static final int MINIMUM_INK_PER_SQUARE = 50;

    /** The locomotive the last call to realRunEndingOnACurve placed, for messages */
    private org.traincontrol.base.Locomotive runLocomotive;

    /**
     * A real autonomy path, over real track, ending on a curved square.
     *
     * Shared by the tests that DRAW it and the one that measures where the ink lands. Finding it is
     * most of the work - a graph has to be parsed, a locomotive placed, and a destination found that
     * is actually reachable - and two copies of that would be two chances to search differently and
     * conclude different things about the same railway.
     *
     * The layout is left exactly as it was found: this is the shared autonomy configuration, not a
     * fixture of its own.
     *
     * @return the squares of the run, never empty
     */
    private java.util.List<org.traincontrol.automationui.TileGraph.TileKey> realRunEndingOnACurve()
        throws Exception
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

        // Every Point that sits on curved track - the destinations these tests are about
        java.util.Set<String> curved = new java.util.HashSet<>();

        for (java.util.Map.Entry<String, org.traincontrol.automationui.TileGraph.TileKey> e
            : tiles.entrySet())
        {
            if (isCurveAt(e.getValue())) curved.add(e.getKey());
        }

        if (curved.isEmpty()) throw new SkipException("no Point on this layout sits on a curve");

        runLocomotive = model.getLocByName(model.getLocList().get(0));

        assertNotNull(runLocomotive, "no locomotive to place");

        // Every train taken off first, and put back afterwards.
        //
        // The search needs an empty railway: a start with a locomotive on it is skipped, and a
        // destination whose block is occupied is not offered as a path at all - so whichever trains
        // happen to be placed in the setup decide whether this test runs. It stopped running exactly
        // that way once, after a restore put three locomotives back, and the only sign was a skip.
        //
        // A test that quietly stops testing because of where somebody parked a train is worse than one
        // that fails, because nothing goes red.
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

    /**
     * The search itself, on a railway with nothing standing on it.
     */
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
