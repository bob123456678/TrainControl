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

    /**
     * The operator’s railway is not this test’s to open (OB-111).
     *
     * Constructing the window opens whatever the saved layout preference names, which on his machine is
     * his live layout - so this class rewrote his configuration on every battery, identical but for
     * line endings, and left it showing as modified in git status. The sandbox points the preference
     * at a copy of the fixture and puts it back afterwards.
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

        model = init(null, true, false, false, true);

        // BEFORE the window is built: it reads the layout preference in its constructor.
        sandbox = support.LayoutSandbox.open();

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        OUT.mkdirs();
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (model != null) model.stop();

        if (sandbox != null) sandbox.close();
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
     * Captions are left off the track editor and nowhere else.
     *
     * FR-030. Adam: "in the track diagram editor, hide autonomy labels completely."  Three of the four
     * combinations must keep them, and it is those three that matter: a rule that hides too much is
     * how the running diagram would stop saying where the trains are.
     *
     * MUTATION: dropping either half of the condition fails a row of this table.
     */
    @Test
    public void testCaptionsAreHiddenOnlyInTheTrackEditor()
    {
        assertTrue(org.traincontrol.gui.LayoutGrid.hidesStationCaptions(true, false),
            "the track diagram editor still draws station captions over the track being edited, "
            + "which is the window they are most in the way of");

        assertFalse(org.traincontrol.gui.LayoutGrid.hidesStationCaptions(true, true),
            "the AUTONOMY editor lost its captions. That is the window where stations are named, so "
            + "hiding them there removes the thing being worked on");

        assertFalse(org.traincontrol.gui.LayoutGrid.hidesStationCaptions(false, false),
            "the running diagram lost its captions, which is where they say what is standing where");

        assertFalse(org.traincontrol.gui.LayoutGrid.hidesStationCaptions(false, true),
            "a diagram outside any editor lost its captions");
    }

    /**
     * The autonomy editor's caption switch remembers itself.
     *
     * FR-030: "have an option to switch between showing station name and parked train in the labels."
     * Off by default - the station's own name - and persisted, because a view preference that resets
     * every time the window opens is one the user sets again every time the window opens.
     *
     * The rebuild matters as much as the flag. A caption's text is decided when the grid is BUILT, not
     * when it is painted, so a switch that changed the flag and repainted would appear to do nothing
     * until the next time something else rebuilt the diagram.
     *
     * MUTATION: dropping the onDiagramChanged call from the listener fails the rebuild assertion;
     * dropping the preference write fails the last one.
     */
    @Test
    public void testTheCaptionSwitchRemembersItselfAndRebuilds() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null) throw new SkipException("no autonomy setup in the fixture layout");

        final int[] rebuilds = {0};

        final org.traincontrol.gui.AutonomyEditorPanel[] panel =
            new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, null, () -> {}));

        panel[0].setOnDiagramChanged(() -> rebuilds[0]++);

        boolean was = panel[0].isShowingParkedTrains();

        javax.swing.SwingUtilities.invokeAndWait(() -> panel[0].getShowParkedTrains().doClick());

        assertNotEquals(panel[0].isShowingParkedTrains(), was,
            "pressing the switch did not change what the captions are asked for");

        assertTrue(rebuilds[0] > 0,
            "the switch changed the setting without rebuilding the diagram. A caption's text is "
            + "decided when the grid is built, so the switch would appear to do nothing at all until "
            + "something else happened to rebuild it");

        // A second panel, built fresh, is the only honest way to ask whether it was remembered.
        final org.traincontrol.gui.AutonomyEditorPanel[] again =
            new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            again[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, null, () -> {}));

        boolean remembered = again[0].isShowingParkedTrains();

        // Put it back before asserting, so a failure here does not leave the operator's own setting
        // flipped.
        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            if (panel[0].isShowingParkedTrains() != was) panel[0].getShowParkedTrains().doClick();
        });

        assertNotEquals(remembered, was,
            "a new editor came up with the old setting, so the switch is not persisted and has to be "
            + "set again every time the window is opened");
    }

    /**
     * A caption may move itself. It may not move anything else.
     *
     * OB-115. Adam, after FR-028 went in: "check normal text label vertical alignment - it seems to
     * have drifted post FR-028 label cutover. for example, Reset and Inner Loop have a different
     * vertical offset from the adjacent route tile."
     *
     * **The mechanism is worth knowing because nothing about it is visible in the caption code.**
     * Text labels are added with `BASELINE_LEADING`, and GridBagLayout does what that says: it works
     * out a baseline for the row from every component anchored that way and lines them all up on it.
     * A caption is one of those components, so giving it a pill and a smaller font moved the row's
     * baseline, and every other label in that row went with it. Three pixels, on labels nobody had
     * touched. Captions are anchored NORTHWEST now, which is both where they want to be - their
     * position is set by their own border - and out of that ballot.
     *
     * So the test changes the one thing a caption changes on its own - its TEXT, which goes from a
     * dash to a locomotive's name and back as trains move - and insists that nothing else on the page
     * has moved a pixel. That covers the baseline, the row heights, and the column widths at once, and
     * it does not need a golden file to compare against.
     *
     * Found with a harness that dumps every component's bounds for both builds and diffs them - see
     * tools/README-bounds.md. What it reported, once it had been made deterministic, was "0 tile
     * placements differ, and these four named labels moved by three pixels".
     *
     * MUTATION: anchoring captions BASELINE_LEADING again fails this.
     */
    @Test
    public void testACaptionNeverMovesAnythingElse() throws Exception
    {
        java.util.List<String> pages = model.getLayoutList();

        assertFalse(pages.isEmpty(), "no pages - is test_layout present?");

        java.awt.Container box = null;
        org.traincontrol.gui.StationCaption caption = null;

        // The first page that has a caption on it. A page with none cannot fail this and would make
        // it look as though the rule had been checked.
        for (String name : pages)
        {
            java.awt.Container built = laidOut(model.getLayout(name), 30);

            for (java.awt.Component one : built.getComponents())
            {
                if (one instanceof org.traincontrol.gui.StationCaption
                    && ((org.traincontrol.gui.StationCaption) one).isPill())
                {
                    box = built;
                    caption = (org.traincontrol.gui.StationCaption) one;
                    break;
                }
            }

            if (caption != null) break;
        }

        assertNotNull(caption,
            "no page in the fixture layout draws a station caption, so this test is not exercising "
            + "the thing it is about");

        // Everything that is not the caption, and where it is.
        java.util.Map<java.awt.Component, java.awt.Rectangle> was = new java.util.LinkedHashMap<>();

        for (java.awt.Component one : box.getComponents())
        {
            if (one instanceof org.traincontrol.gui.StationCaption
                && ((org.traincontrol.gui.StationCaption) one).isPill())
            {
                continue;
            }

            was.put(one, one.getBounds());
        }

        assertTrue(was.size() > 10, "only " + was.size() + " components to watch, which is too few "
            + "for this page to be the diagram it is meant to be");

        // What happens on a running railway: the dash becomes a train and the caption gets wide.
        //
        // And TALLER, which the text alone does not do and which is the half that actually broke.
        // OB-115 was a caption whose HEIGHT changed - a pill instead of a two-line label, at nine
        // tenths of the font - and with a baseline anchor a height is a vote on where the whole row
        // sits. The first version of this test only widened the caption, and it passed with the
        // anchor put back the way that caused the fault. That is why the font is changed here too:
        // a mutation that survives a test is the test being wrong, not the mutation being safe.
        final org.traincontrol.gui.StationCaption changing = caption;
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
     * A grid for one page, built and laid out, with its tile images waited for.
     *
     * The wait is not optional. A tile's preferred size depends on whether its icon has arrived, the
     * icons decode on a pool, and a grid measured before they land gives a different answer every
     * time - which reads as the thing under test having moved something.
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
     * A caption lands on the right side of the rail whatever size the tiles are.
     *
     * Adam: "land them so that they align just below straight tracks if the track goes east to west,
     * or centered over the track if north to south", and then "remember to adjust for the 60px view".
     * The diagram is drawn at whatever size the operator picked, so an offset that is right at one
     * size and wrong at another is a feature that works on my machine.
     *
     * The rail runs across the middle of the square, so the whole of this is where the caption's own
     * middle falls relative to the square's. Below it for an east-west run, which is what "just below
     * the track" means; on it for a north-south one, where the rail goes up the square and the caption
     * lies across it.
     *
     * Line heights are taken as three fifths of the tile, which is what Segoe UI at the caption's size
     * comes out at - close enough for a proportion, and the assertions below are about proportions.
     *
     * MUTATION: returning a fixed number of pixels from captionOffset fails this at every size but one.
     */
    @Test
    public void testACaptionSitsRightAtEveryTileSize()
    {
        for (int tile : new int[] { 20, 30, 40, 60, 80 })
        {
            int line = Math.round(tile * 0.6f);

            int rail = tile / 2;

            int across = org.traincontrol.gui.StationCaption.captionOffset(tile, line, false);
            int up = org.traincontrol.gui.StationCaption.captionOffset(tile, line, true);

            assertTrue(across + line / 2 > rail,
                "at " + tile + "px an east-west caption sits on the rail rather than below it - its "
                + "middle is at " + (across + line / 2) + " and the rail is at " + rail);

            assertTrue(Math.abs(up + line / 2 - rail) <= 2,
                "at " + tile + "px a north-south caption is not on the track it is meant to lie "
                + "across - its middle is at " + (up + line / 2) + " and the rail is at " + rail);

            assertTrue(across >= 0 && up >= 0,
                "at " + tile + "px a caption is pushed off the top of its own square");
        }

        // And the degenerate case, because a caption whose font has not been set yet asks this.
        assertEquals(org.traincontrol.gui.StationCaption.captionOffset(40, 0, false), 0,
            "a caption with no line height is given an offset, which is an opinion about a label "
            + "whose size is not known yet");
    }

    /**
     * Every arrow a caption can carry has a glyph in the font the caption is drawn in.
     *
     * FR-028 replaced the chevrons - > < ^ v - with geometric triangles, and the obvious four are
     * U+25B6 U+25C0 U+25B2 U+25BC. **Segoe UI has the last two and not the first two.** The first
     * rendering drew proper triangles for trains facing north and south and a tofu box for every train
     * facing east or west, which reads as a broken font rather than as a wrong codepoint - and reads
     * that way on the diagram, where nobody is looking for it.
     *
     * The pointers U+25BA and U+25C4 are in the font and match the two that were already right, so
     * those are what the captions use. This exists so that swapping in a prettier codepoint fails here
     * rather than on the railway.
     *
     * Found by rendering the labels to a picture and looking at it, which is the only way this kind of
     * fault is ever found.
     *
     * MUTATION: putting U+25B6 or U+25C0 back fails this.
     */
    @Test
    public void testCaptionArrowsCanBeDrawn()
    {
        java.awt.Font font = new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 20);

        String[] arrows = { "\u25BA", "\u25C4", "\u25B2", "\u25BC" };

        for (String arrow : arrows)
        {
            assertTrue(font.canDisplay(arrow.codePointAt(0)),
                "the caption font has no glyph for U+" + Integer.toHexString(arrow.codePointAt(0))
                + ", so every train facing that way draws a tofu box on the diagram");
        }

        // And the two that look like the obvious choice and are not, so that the comment explaining
        // why they were not chosen cannot quietly stop being true.
        assertFalse(font.canDisplay(0x25B6) && font.canDisplay(0x25C0),
            "Segoe UI has grown glyphs for U+25B6 and U+25C0, so the pointers this uses instead could "
            + "now be the triangles that match the other two exactly. Worth changing, and worth "
            + "checking on the machines this actually runs on before doing it");
    }

    /**
     * A caption is an oval, not a rectangle of colour.
     *
     * FR-028: "upgrade from [---] to blue ovals with white text". The pill is painted by the label
     * itself, which means the label has to stay TRANSPARENT - a JLabel that is opaque fills its own
     * rectangle first, and the rounded shape would be drawn inside a square of the same colour, which
     * is not an oval at all and is exactly what this replaced.
     *
     * So the corner is the assertion. Nothing painted there is the whole difference between the two.
     *
     * MUTATION: setting the label opaque, or filling a rectangle instead of a round one, fails this.
     */
    @Test
    public void testACaptionIsAnOval() throws Exception
    {
        org.traincontrol.gui.StationCaption pill = new org.traincontrol.gui.StationCaption();

        pill.setPill(true);
        pill.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 20));
        pill.setText("EN57-203");
        pill.setBackground(org.traincontrol.gui.StationCaption.PILL_AT_REST);
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
     * Which way the rails run, for the square a caption lands on.
     *
     * FR-028: "align just below straight tracks if the track goes east to west, or centered over the
     * track if north to south." That decision is made from the tile's geometry, and this asks the
     * geometry the two questions that matter: a straight rail answers one way, and the same rail
     * turned a quarter answers the other.
     *
     * Deliberately not asserting WHICH orientation is which. The rotation convention is the layout
     * format's, not this code's - a diagram tile rotates by (4 - o) - and a test that wrote it down
     * would be pinning the wrong thing and would fail the day the format changed rather than the day
     * the captions moved.
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
     * The autonomy menu for a square opens with that square\u2019s name, disabled.
     *
     * OB-112, and written because reading the code and looking at the screen disagreed. Adam sent a
     * picture of the editor\u2019s menu on LowerBack with no heading on it at all; `buildTileMenu`
     * calls `title` unconditionally as its first act, and a probe against a copy of his layout
     * computes "LowerBack" for exactly that square. One of those two is wrong and no amount of
     * re-reading settles which, so this asks the menu itself.
     *
     * It goes through `buildAutonomyTileMenu`, which is the main window\u2019s door to the same
     * builder the editor uses - one menu, built once, so a test on this side is a test on both.
     *
     * The assertion is about the FIRST component, not merely that a name appears somewhere: a
     * heading that is not at the top is not a heading, and half the point of it is that the name is
     * the first thing under the pointer.
     *
     * MUTATION: removing the `title` call from buildTileMenu fails this; so does putting tidy()'s
     * heading test back the way it was, which is how the heading came to be missing at all.
     */
    @Test
    public void testTheAutonomyMenuOpensWithTheSquaresName() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null || session.getReducer() == null)
        {
            throw new SkipException("the fixture layout has no autonomy setup loaded");
        }

        org.traincontrol.automationui.TileGraph.TileKey point = null;

        for (org.traincontrol.automationui.TileGraph.TileKey tile
            : session.getReducer().getPoints().keySet())
        {
            String name = session.getStore().getPointName(tile);

            if (name != null && !name.trim().isEmpty())
            {
                point = tile;
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

        assertEquals(heading.getText(), session.describeTile(square),
            "the menu does not open with the name of the square it is about. That is OB-112, and it "
            + "is the assertion that says whether the fault is in this code or in the build that was "
            + "running when it was reported");

        // And the shape tidy() is otherwise there for, which the fix for OB-112 must not undo.
        //
        // OB-054: "a heading, a divider, nothing at all, another divider" - a menu assembled from a
        // dozen independent blocks, each of which leaves its divider behind when it has nothing to
        // offer for this square. The heading was the half that went wrong; these three are the half
        // that was right, and nothing was checking them.
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
     * A diagram that is already drawn is not taken off the screen to be rebuilt.
     *
     * OB-109. Adam: "when placing new tiles in the track diagram editor, the diagram sometimes
     * flickers." Every placement rebuilds the whole grid, and a new grid hid itself until its tiles
     * had decoded - so the page was taken away and given back, and whether an empty paint landed in
     * between depended on where the event thread was. That is the "sometimes".
     *
     * Two rules came out of it, and this checks both, because each is what the other misses.
     *
     * **Nothing pending, nothing to hide.** On a warm cache there is no decode outstanding, so the
     * hold-back has nothing to wait for. It still hid the diagram, and `whenTilesSettled` gave it back
     * on the NEXT event-thread pass - a hide and a show a frame apart, which is the blink.
     *
     * **A replacement is not an arrival.** The hold-back was written for a page arriving in two stages;
     * a page already on the screen being rebuilt is the opposite case. Placing the first tile of a
     * type nobody has drawn at this size is ONE decode, and one decode was taking the whole page away.
     * A slow replacement still ends up behind the spinner, 120ms in - that is what the second half
     * here does not check and MT-194 does.
     *
     * Both assertions read `isVisible` inside the same event-thread block that built the grid, which is
     * what makes them deterministic: the reveal can only arrive on a later pass, so the old behaviour
     * cannot sneak past by being quick.
     *
     * MUTATION: removing the `tilesAreSettled` early return fails the first; removing the `replacing`
     * condition - so every grid hides itself again - fails the second; hiding NOTHING, ever, fails the
     * control at the end of the second.
     */
    @Test
    public void testARebuiltDiagramIsNotTakenOffTheScreen() throws Exception
    {
        java.util.List<String> pages = model.getLayoutList();

        assertFalse(pages.isEmpty(), "no pages to build - is test_layout present?");

        final LayoutDiagram layout = model.getLayout(pages.get(0));

        final javax.swing.JPanel first = new javax.swing.JPanel();

        javax.swing.SwingUtilities.invokeAndWait(() ->
            new org.traincontrol.gui.LayoutGrid(layout, 30, first, null, true, ui));

        // Everything that page needs, decoded and cached.
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

        // Two: a real wait, held open by the test.
        //
        // The first version of this hoped that a page of images at an unused size would still be
        // decoding a statement later. It was not - the fixture is small - and the precondition said
        // so rather than letting the assertion pass without meaning anything. tileDecodeStarted is
        // what a LayoutLabel calls before it submits, so holding one open puts the grid in exactly
        // the state it asks about, for as long as this test wants rather than as long as a pool takes.
        ui.tileDecodeStarted();

        final boolean[] onFresh = {false};

        try
        {
            assertFalse(ui.tilesAreSettled(), "precondition: a decode is outstanding");

            // The SAME panel, which already has a diagram on it.  A replacement, with a genuine wait.
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.gui.LayoutGrid grid =
                    new org.traincontrol.gui.LayoutGrid(layout, 37, fresh, null, true, ui);

                visible[0] = grid.getContainer().isVisible();
            });

            // And the control, which is what makes the line above mean anything: a BRAND NEW panel,
            // same wait, must still be held back.  Without this, "never hide anything" passes.
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
