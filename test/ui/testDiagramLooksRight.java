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

        // BEFORE THE MODEL, not just before the window (OB-111, corrected 2026-08-28).
        //
        // MarklinControlStation.init reads the layout preference too - it is what loads the pages -
        // so opening the sandbox after it left the model on the operator's real railway while the
        // window looked at the copy. The comment that used to stand here named only the window.
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, true);

        javax.swing.SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        OUT.mkdirs();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        // THE WINDOW TOO (TSX-C6).
        //
        // This class builds a `TrainControlUI` in its set-up and was the only one of its siblings that
        // never disposed it - a real top-level window, with its listeners and its grid, alive for the
        // rest of the JVM.  Bounded by the JVM in a battery, and not bounded at all for anybody
        // running this class from an IDE.
        if (ui != null)
        {
            final TrainControlUI closing = ui;

            javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

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

        // ADDRESSES OFF, whatever the operator's Show Addresses says (the run's preferences start as his): an address
        // label stands in front of its square, as OB-259 has it, and its box covers the line there, which is not what
        // this measures
        final boolean addressesWere = page.getShowAddress();

        page.setShowAddress(false);

        try
        {
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

            // A STATION'S SQUARE ANYWHERE ON THE RUN, as the run's ends below: its icon is painted over the line there
            // (MT-076), and on a curve it is its full size since round 119 (Adam, 2026-10-10: "make the stations spill
            // over onto adjacent tiles"), covering most of a curve's short chord.  Found in the picture with no run on it,
            // by the station blue: a square showing it carries a station's icon.
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

                // The two ENDS are allowed to be hidden: a run stops at a station, and the station's badge
                // is painted OVER the line there (MT-076), so the stub can be entirely covered.
                if (tile.equals(run.get(0)) || tile.equals(run.get(run.size() - 1))) continue;

                if (stations.contains(square)) continue;

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
     * Whether this pixel is a colour the run is drawn in - the path ahead or the track driven, from `DiagramColours`
     * (FR-106), so a change of palette there is followed here.
     *
     * Loose on purpose - the edges of a stroke are blended with whatever is under them - but tight enough to exclude the
     * white chevrons and the black and white train dot, none of which are claims about where the track goes.  The station
     * badges are the path's blue, and are the same in both renders, so the difference this is asked of leaves them out.
     */
    private boolean isRunInk(int rgb)
    {
        return near(rgb, org.traincontrol.automationui.DiagramColours.PATH_AHEAD)
            || near(rgb, org.traincontrol.automationui.DiagramColours.PATH_DRIVEN);
    }

    /** Whether a pixel is within a blend's reach of a colour. */
    private static boolean near(int rgb, java.awt.Color colour)
    {
        return Math.abs(((rgb >> 16) & 0xFF) - colour.getRed()) < 60
            && Math.abs(((rgb >> 8) & 0xFF) - colour.getGreen()) < 60
            && Math.abs((rgb & 0xFF) - colour.getBlue()) < 60;
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
     * Station captions are blue or light grey, whichever the operator asked for, and readable either
     * way.
     *
     * FR-031. Adam: "add a jmenu (preferences) setting for the station labels to be blue (default) or
     * light gray (non default).  persist as with other settings."
     *
     * The colour is ASKED FOR at the moment a caption is coloured rather than read once into a
     * constant, which is what makes the menu switch take effect without restarting the application -
     * and that is the whole of what "persist as with other settings" has to mean for a switch sitting
     * in a menu.
     *
     * The readability half is not decoration. The text colour is derived from the fill by perceived
     * brightness, so a grey chosen a few shades lighter would silently take the captions from white
     * text to black - which is correct, and worth having a test say out loud, because a grey chosen a
     * few shades DARKER would leave black text on a dark ground and nobody would notice until they
     * looked at a diagram.
     *
     * The operator's own setting is put back at the end whatever happens. It is a real preference in a
     * real preference node, not a fixture.
     *
     * MUTATION: making restingFill ignore the preference fails the second assertion; making
     * readableOn return WHITE always fails the fourth.
     */
    @Test
    public void testStationLabelsFollowTheColourPreference()
    {
        // WHETHER IT WAS STORED, not what the accessor answers.
        //
        // Capturing the accessor captures its DEFAULT when nothing is stored, and writing that back
        // materialises the preference on a machine that never set it. Two sibling tests were fixed for
        // exactly this earlier today; this was the third, and nobody swept it (reviewer, 2026-08-28).
        boolean had = TrainControlUI.getPrefs().get(TrainControlUI.STATION_LABELS_GREY, null) != null;

        boolean was = TrainControlUI.stationLabelsAreGrey();

        try
        {
            // GREY WHEN NOTHING HAS BEEN CHOSEN (Adam, 2026-10-03: "let's make gray station labels default, but blue being
            // the optional setting to change via the existing preference")
            TrainControlUI.getPrefs().remove(TrainControlUI.STATION_LABELS_GREY);

            assertEquals(org.traincontrol.gui.StationCaption.restingFill(),
                org.traincontrol.gui.StationCaption.PILL_GREY,
                "with nothing chosen the station labels are not grey, which is the default now");

            TrainControlUI.getPrefs().putBoolean(TrainControlUI.STATION_LABELS_GREY, false);

            assertEquals(org.traincontrol.gui.StationCaption.restingFill(),
                org.traincontrol.gui.StationCaption.PILL_AT_REST,
                "the preference switched off and the captions are not blue, so blue cannot be chosen");

            assertEquals(org.traincontrol.gui.StationCaption.readableOn(
                org.traincontrol.gui.StationCaption.restingFill()), java.awt.Color.WHITE,
                "white text on the blue is the look that was asked for");

            TrainControlUI.getPrefs().putBoolean(TrainControlUI.STATION_LABELS_GREY, true);

            assertEquals(org.traincontrol.gui.StationCaption.restingFill(),
                org.traincontrol.gui.StationCaption.PILL_GREY,
                "the preference was set and the captions are still blue, so the menu switch changes a "
                + "stored value and nothing on the diagram");

            assertEquals(org.traincontrol.gui.StationCaption.readableOn(
                org.traincontrol.gui.StationCaption.restingFill()), java.awt.Color.BLACK,
                "white text on the light grey, which cannot be read. The text colour is worked out "
                + "from the fill for exactly this reason, and a grey this light has to take black");
        }
        finally
        {
            // Put back as it was, INCLUDING never having been set.
            if (had) TrainControlUI.getPrefs().putBoolean(TrainControlUI.STATION_LABELS_GREY, was);
            else TrainControlUI.getPrefs().remove(TrainControlUI.STATION_LABELS_GREY);
        }

        // The finally above has just written this, so comparing them proves nothing about the code -
        // only that the restore ran. Kept as a guard on the RESTORE, and said to be that.
        assertEquals(TrainControlUI.stationLabelsAreGrey(), was,
            "this test left the operator's own preference changed");
    }

    /**
     * A placeholder is drawn dimmer than a name, on either pill.
     *
     * The editors say "this square is a PLACEHOLDER, not an answer" with grey: an unnamed station's
     * em-dash, a yard name that is not what the autonomy editor is for. On a white label that reads
     * exactly as intended. On a pill it did not read at all - grey is not red, so `onPill` let it fall
     * through to `readableOn`, and `readableOn` answers by the FILL, so a placeholder and a name came
     * back the same white on navy and the same black on grey.
     *
     * The comment in LayoutGrid beside the call said the opposite - "a placeholder is drawn dimmer than
     * a station that has one" - and said it for two days. A reviewer disbelieved it, wrote a probe
     * against the compiled tree, and got white and white. This file is full of comments that are
     * treated as load-bearing; one of them was load-bearing and false.
     *
     * Both fills, because they are the two the operator can actually choose and the answer has to hold
     * on each: on navy the readable colour is white and dimming moves it DOWN towards the fill, on pale
     * grey it is black and dimming moves it UP. A rule that only dimmed one way would look right to
     * whoever wrote it and wrong to whoever had the other preference set.
     *
     * The separation is asserted as a distance and not as a constant. What matters is that the two are
     * far enough apart to see and that the placeholder is still legible; the exact blend is a taste
     * question and pinning it here would make every future adjustment a test edit.
     *
     * MUTATION: returning `readableOn(fill)` for grey - which is what it did - fails the first
     * assertion on both fills.
     */
    @Test
    public void testAPlaceholderStaysDimmerThanAName()
    {
        // The grey LayoutGrid writes out longhand wherever it means "placeholder".
        java.awt.Color placeholder = new java.awt.Color(150, 150, 150);

        for (java.awt.Color fill : new java.awt.Color[] {
            org.traincontrol.gui.StationCaption.PILL_AT_REST,
            org.traincontrol.gui.StationCaption.PILL_GREY })
        {
            java.awt.Color name = org.traincontrol.gui.StationCaption.onPill(
                fill, java.awt.Color.BLACK);

            java.awt.Color dim = org.traincontrol.gui.StationCaption.onPill(fill, placeholder);

            assertNotEquals(dim, name,
                "a placeholder and a name came back the same colour on " + fill + ", so the two "
                + "states the editor draws are one state on the screen");

            // Dimmer means NEARER THE PILL, which is a different sentence on each fill: darker on the
            // navy, lighter on the pale grey.  Distance to the fill is the one way to say it that is
            // true of both.
            assertTrue(distance(dim, fill) < distance(name, fill),
                "the placeholder is further from the pill than the name is on " + fill + ", which is "
                + "the opposite of dim");

            // And still readable.  Dimming that goes all the way to the fill is not dimming, it is
            // erasing - an unnamed station would vanish rather than look unfinished.
            assertTrue(distance(dim, fill) > 40,
                "the placeholder is within 40 of the pill colour on " + fill + " and has effectively "
                + "disappeared into it");
        }

        // The ANSWER colours are not dimmed.  Black and white are what an ordinary caption asks for,
        // and a rule keyed on "all three channels equal" catches both unless it says otherwise.
        assertEquals(
            org.traincontrol.gui.StationCaption.onPill(
                org.traincontrol.gui.StationCaption.PILL_AT_REST, java.awt.Color.BLACK),
            org.traincontrol.gui.StationCaption.readableOn(
                org.traincontrol.gui.StationCaption.PILL_AT_REST),
            "a plain caption was dimmed, so every station name on the diagram is now a placeholder");

        // And red still means red, which is the distinction that already worked.
        java.awt.Color notReached = org.traincontrol.gui.StationCaption.onPill(
            org.traincontrol.gui.StationCaption.PILL_AT_REST, new java.awt.Color(255, 0, 0));

        assertTrue(notReached.getRed() > notReached.getGreen() + 60,
            "red stopped being red on the pill - that is the timetable's \"not reached yet\", and it "
            + "was working before this rule was added beside it");
    }

    /**
     * How far apart two colours are, plainly.
     *
     * @param one a colour
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
     * The four facing arrows are rotations of each other, not two pairs of different shapes.
     *
     * OB-116. Adam: "while the up and down arrows look good, the left and right arrows on autonomy
     * labels are too wide for how short they are.  make them look more symmetrical and a hair taller."
     *
     * That was a measurement, not an impression. In Segoe UI the up arrow U+25B2 is 78.3 by 70.0 and
     * the right arrow U+25BA is 70.0 by 35.5 - half the height. And there was nothing better to reach
     * for: those two are the ONLY horizontal triangles Segoe UI can draw, which is why they were
     * chosen in the first place, U+25B6 and U+25C0 having come out as empty boxes.
     *
     * The font was the answer rather than the character. This asserts the property that matters - that
     * a left or right arrow is about as tall as an up or down one - rather than pinning the code
     * points, so the arrows can be changed again without editing a test, as long as they still match.
     *
     * SKIPPED where the matched font is not installed, which is the same condition the code uses to
     * decide whether to use it. On such a machine the squat pointers are deliberately still in use,
     * because a squat arrow beats an empty box, and asserting otherwise would fail for being right.
     *
     * MUTATION: putting U+25BA and U+25C4 back fails the height comparison at better than three to
     * one.
     */
    @Test
    public void testTheFourFacingArrowsAreOneMatchedSet()
    {
        if (!"Segoe UI Symbol".equals(org.traincontrol.gui.StationCaption.LABEL_FONT))
        {
            throw new SkipException("Segoe UI Symbol is not installed, so the pointers are correct "
                + "here - the arrows only have to match on a machine that can draw the triangles");
        }

        java.awt.Font font = new java.awt.Font(
            org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 100);

        java.awt.font.FontRenderContext frc =
            new java.awt.font.FontRenderContext(null, true, true);

        double up = height(font, frc, org.traincontrol.gui.StationCaption.ARROW_N);
        double down = height(font, frc, org.traincontrol.gui.StationCaption.ARROW_S);
        double east = height(font, frc, org.traincontrol.gui.StationCaption.ARROW_E);
        double west = height(font, frc, org.traincontrol.gui.StationCaption.ARROW_W);

        assertTrue(up > 1 && down > 1 && east > 1 && west > 1,
            "one of the arrows has no shape at all, which is what an empty box measures as: "
            + up + " " + down + " " + east + " " + west);

        assertEquals(east, west, 0.01, "the left and right arrows are different sizes");
        assertEquals(up, down, 0.01, "the up and down arrows are different sizes");

        // The complaint, as a number.  A horizontal arrow half the height of a vertical one is what
        // this test exists to stop coming back; a fifth either way is the tolerance.
        assertTrue(east > up * 0.8 && east < up * 1.25,
            "the sideways arrow is " + east + " tall against the up arrow's " + up + ", which is the "
            + "mismatch OB-116 was filed for - it reads as too wide for its height");
    }

    /**
     * How tall a string is drawn, ignoring the space the font reserves around it.
     */
    private double height(java.awt.Font font, java.awt.font.FontRenderContext frc, String text)
    {
        return font.createGlyphVector(frc, text.trim()).getVisualBounds().getHeight();
    }

    /**
     * Java source with its comments stripped, so a scan reads code and not the prose about it.
     *
     * TST-C10: a body scan that does not strip comments passes on the strength of a comment
     * describing the removed call, not the call itself.
     */
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
     * OB-117. Adam: "on route departure from 1016 as the origin station, the locomotive icon covers the autonomy label
     * with a blank white space."  That was the tile, lifted to the front for its train: a tile is opaque, so it painted
     * the caption out.  The lift went in OB-259 - the diagram's panel paints every train after all of its components
     * (OB-159), so nothing needs to move - and this holds the order a running train leaves behind it: the captions in
     * the order they were built, the address label, and the tile behind them all.
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
        org.traincontrol.gui.StationCaption caption = new org.traincontrol.gui.StationCaption();
        org.traincontrol.gui.StationCaption second = new org.traincontrol.gui.StationCaption();
        org.traincontrol.gui.StationCaption third = new org.traincontrol.gui.StationCaption();

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

        // A TRAIN STARTS ON THE SQUARE
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
     * An empty caption sits over the middle of its square, not at the left of it.
     *
     * OB-118. Adam: "when a position is empty, try to better center it over the track."
     *
     * The placement arithmetic only ever subtracted. It took half of the caption's OVERFLOW past one
     * tile and shifted left by that much - so a caption WIDER than its square was centred, and a
     * caption NARROWER than its square was left exactly where its cell began, hard against the left
     * edge. An empty station shows a dash and is narrow; the locomotive name that replaces it a moment
     * later is wide. So the same caption was centred or not depending on whether a train was there.
     *
     * The two cases are one question with a difference that is negative half the time, and the fix is
     * to stop clamping it to zero before using it.
     *
     * MUTATION: restoring `Math.max(0, (wide - tile) / 2)` and subtracting it fails the dash case
     * while leaving the long-name case passing.
     */
    @Test
    public void testAnEmptyCaptionIsCentredOnItsSquare()
    {
        int tile = 60;
        int backShift = tile;

        org.traincontrol.gui.StationCaption caption = new org.traincontrol.gui.StationCaption();

        caption.setPill(true);
        caption.setFont(new java.awt.Font(
            org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, tile / 2));

        // Empty: the dash a station shows when no train is standing on it.
        caption.setText(org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY);
        caption.setTileGeometry(tile, backShift, 0, 0);

        int dash = caption.getBorder().getBorderInsets(caption).left;

        assertTrue(dash > backShift,
            "an empty caption starts at " + dash + " with its square beginning at " + backShift
            + ", so it is hard against the left edge of the square rather than over the middle of it");

        // And a LONG name still behaves as it did - centred by pulling left, clamped at the cell.
        caption.setText("Hauptbahnhof Nord 12");

        int name = caption.getBorder().getBorderInsets(caption).left;

        assertTrue(name < dash,
            "a caption too wide for its square is placed further right than a narrow one, which is "
            + "backwards - a wide one has to pull LEFT to stay centred");

        assertTrue(name >= 0, "a caption was placed before the start of its own cell");
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

        // And a page left out of autonomy, which overrides all of the above (B6).
        //
        // A caption there names nothing: the graph is built without that page, so there is no Point
        // behind it, and neither visibility switch could reach it because both walk the registry of
        // captions and an excluded page's caption is never registered. The one square autonomy will
        // most certainly never use was the one whose label could not be turned off.
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
     * The caption choice remembers itself and rebuilds the diagram.
     *
     * A caption's text is decided when the grid is BUILT, so a control that changed the setting and
     * only repainted would appear to do nothing at all until something else happened to rebuild.
     * That is the property worth pinning and it has not changed.
     *
     * **What changed is the control** (FR-061, 2026-09-07). This drove `getShowParkedTrains()`, a tick
     * box that is no longer mounted anywhere: what a caption says is one dropdown now, with four
     * options that exclude each other, and the two boxes survive only as the internal representation
     * that the drawing code reads. Clicking an unmounted box changed a field and rebuilt nothing, so
     * this test failed - correctly, and for a reason that was about the test rather than the railway.
     *
     * Driving the dropdown instead is not a weakening: it is the control a person actually has, and
     * asserting through it covers the box as well, because the box is set from it.
     *
     * MUTATION: dropping the onDiagramChanged call from the listener fails the rebuild assertion;
     * dropping the preference write fails the last one.
     *
     * @throws Exception on a failure to build the panel
     */
    @Test
    public void testTheCaptionChoiceRemembersItselfAndRebuilds() throws Exception
    {
        org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session == null) throw new SkipException("no autonomy setup in the fixture layout");

        final int[] rebuilds = {0};

        final org.traincontrol.gui.AutonomyEditorPanel[] panel =
            new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            panel[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, null, () -> {}));

        panel[0].setOnDiagramChanged(() -> rebuilds[0]++);

        // WHETHER IT WAS STORED, not what the accessor answers (TSX-B3).
        //
        // Selecting below WRITES the preference, so a machine that had never set it ends up with it
        // set - and putting the old value back writes the default, which is not the same as leaving
        // it alone. The key lives in the panel's own node.
        java.util.prefs.Preferences viewPrefs = org.traincontrol.gui.TrainControlUI.getPrefs();

        boolean modeStored = viewPrefs.get("autonomyEditorCaptionMode", null) != null;

        final javax.swing.JComboBox<String> choice = panel[0].getCaptionChoice();

        final int was = choice.getSelectedIndex();

        // Any option but the current one. Parked rather than None, so the box the drawing code reads
        // changes too and the assertion below is about both halves.
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

        // A second panel, built fresh, is the only honest way to ask whether it was remembered.
        final org.traincontrol.gui.AutonomyEditorPanel[] again =
            new org.traincontrol.gui.AutonomyEditorPanel[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
            again[0] = new org.traincontrol.gui.AutonomyEditorPanel(session, null, () -> {}));

        int remembered = again[0].getCaptionChoice().getSelectedIndex();

        // Put it back before asserting, so a failure here does not leave the operator's own setting
        // changed.
        javax.swing.SwingUtilities.invokeAndWait(() -> choice.setSelectedIndex(was));

        // And if nothing was stored before this test ran, nothing is stored after it.
        if (!modeStored) viewPrefs.remove("autonomyEditorCaptionMode");

        assertEquals(remembered, wanted,
            "a new editor came up with the old choice, so it is not persisted and has to be set again"
            + " every time the window is opened");
    }

    /**
     * Parked Locs labels only the stations a locomotive is parked at (OB-312).
     *
     * Adam, 2026-10-03: *"When in text labels-parked locomotive, dont show labels without a locomotive."*  A station with
     * none drew the empty placeholder; Home Locs already draws nothing where there is no home.
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

        // A page with a station nobody is parked at
        String pageName = null;

        for (org.traincontrol.automationui.TileGraph.TileKey station : session.getLabelledStationTiles())
        {
            if (pageName == null && session.getLocomotiveNameAt(station) == null) pageName = station.getPage();
        }

        assertNotNull(pageName, "precondition: every captioned station has a locomotive parked at it");

        final LayoutDiagram page = model.getLayout(pageName);

        java.util.prefs.Preferences viewPrefs = TrainControlUI.getPrefs();

        final boolean modeStored = viewPrefs.get("autonomyEditorCaptionMode", null) != null;

        final org.traincontrol.gui.LayoutEditor[] editor = new org.traincontrol.gui.LayoutEditor[1];

        javax.swing.SwingUtilities.invokeAndWait(() ->
        {
            editor[0] = new org.traincontrol.gui.LayoutEditor(page, 30, ui, 0);
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
            if (c instanceof org.traincontrol.gui.StationCaption && ((org.traincontrol.gui.StationCaption) c).isPill())
            {
                out.add(((org.traincontrol.gui.StationCaption) c).getText());
            }

            if (c instanceof java.awt.Container) collectPills((java.awt.Container) c, out);
        }
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
     * docs/tools/README-bounds.md. What it reported, once it had been made deterministic, was "0 tile
     * placements differ, and these four named labels moved by three pixels".
     *
     * MUTATION: anchoring captions BASELINE_LEADING again fails this.
     */
    @Test
    public void testACaptionNeverMovesAnythingElse() throws Exception
    {
        java.util.List<String> pages = model.getLayoutList();

        assertFalse(pages.isEmpty(), "no pages - is test/test_layout present?");

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
     * Changing what trains may do at a station leaves its caption where it is (OB-310).
     *
     * Adam, 2026-10-02: *"when changing topmainr1 from a station to no trains can pass, the label gets rotated from
     * being vertical (correct) to being horizontal (wrong) and back again."*  On the track diagram, through its own
     * right-click menu.
     *
     * Each answer under Station went through `setStation(tile, true)`, which placed the station's caption - and
     * `placeCaption` MOVES a caption the station already has, passing over the square it is on because that square is
     * taken.  So every change sent the name to the next free square beside the platform, and the next change sent it
     * back: below TopMainR1 on its north-south rails, drawn on end, then above it on blank space, drawn flat.  The
     * rename had the same fault (MT-116) and has the same answer: a caption is placed only where the station has none.
     *
     * Every named station with a caption, both changes, through `buildAutonomyTileMenu` - the main window's door to
     * the menu Adam used.  Then a station like his, captioned on north-south rails, drawn after "No - Nothing Can
     * Pass": that is the state he saw flat, and after both changes the second move can have put it back.
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

        // A STATION LIKE HIS: captioned on a square whose rails run north-south, so drawn on end
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

        // Closing whatever comes up, so a question cannot hang the click
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
            if (one instanceof org.traincontrol.gui.StationCaption && ui.getLayoutStations(station).contains(one))
            {
                found.add(((org.traincontrol.gui.StationCaption) one).isRotated() ? "on end" : "flat");
            }
        }

        return found.size() == 1 ? found.get(0) : String.valueOf(found);
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
     * A caption sits clear of its rail at every tile size, whichever way the rail runs.
     *
     * One rule since 2026-08-27. It used to be two, and this test used to assert the second of them -
     * that a north-south caption lies ACROSS its rail - which is exactly what Adam asked to stop:
     * "rotate them 90 degrees counterclockwise so they land just to the right of the tile, similar to
     * how horizontal labels land between tracks". A caption that lies across the track it names is the
     * one caption on the diagram that covers its own subject.
     *
     * So the property is now the same in both directions and stated once: the caption starts past the
     * rail, and not so far past it that it stops reading as a label for that track. The second half is
     * the one that matters - it is what stops the offset being increased a few pixels at a time until
     * the captions float free of the diagram, which is a thing that has already happened twice.
     *
     * Line heights are taken as three fifths of the tile, which is what the caption font comes out at.
     *
     * MUTATION: returning a fixed number of pixels from captionOffset fails this at every size but
     * one; dropping the nudge fails the first assertion at 20px.
     */
    @Test
    public void testACaptionSitsRightAtEveryTileSize()
    {
        for (int tile : new int[] { 20, 30, 40, 60, 80 })
        {
            int line = Math.round(tile * 0.6f);

            int rail = tile / 2;

            int offset = org.traincontrol.gui.StationCaption.captionOffset(tile, line);

            // PAST the rail, so the caption is beside the track rather than on it.
            assertTrue(offset > rail,
                "at " + tile + "px a caption starts at " + offset + " and the rail is at " + rail
                + ", so it is drawn over the track it names rather than beside it");

            // And not so far past that it has left its own square behind.
            //
            // A cap rather than a restatement of the offset: a test that computes the same expression
            // as the code agrees with it by construction and can never disagree, which is no test at
            // all. What this is for is stopping the offset growing without anybody looking.
            assertTrue(offset <= tile + line,
                "at " + tile + "px a caption starts " + offset + " past its square's edge, which is "
                + "far enough from the tile it belongs to that it reads as a label for the next one");

            assertTrue(offset >= 0,
                "at " + tile + "px a caption is pushed off the leading edge of its own square");
        }

        // And the degenerate case, because a caption whose font has not been set yet asks this.
        assertEquals(org.traincontrol.gui.StationCaption.captionOffset(40, 0), 0,
            "a caption with no line height is given an offset, which is an opinion about a label "
            + "whose size is not known yet");
    }

    /**
     * The four arrows a caption can end in can all be drawn in the font captions are drawn in.
     *
     * A tofu box on a diagram is the failure this exists to stop, and it is exactly the failure the
     * first version of these arrows had - U+25B6 and U+25C0 are the obvious geometric triangles and
     * Segoe UI cannot draw either.
     *
     * **It asks StationCaption for both halves, and that is the point.** This test used to name the
     * font and the codepoints itself, so when OB-116 fixed the mismatched arrows by changing the FONT
     * rather than the character, the test went on happily checking a font the captions no longer used.
     * It even closed with an assertion arguing against making that change. A test that names its own
     * constants is a test that watches something other than the code.
     *
     * MUTATION: setting either arrow constant to a codepoint LABEL_FONT cannot draw fails this.
     */
    @Test
    public void testCaptionArrowsCanBeDrawn()
    {
        java.awt.Font font = new java.awt.Font(
            org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 20);

        String[] arrows = {
            org.traincontrol.gui.StationCaption.ARROW_N,
            org.traincontrol.gui.StationCaption.ARROW_S,
            org.traincontrol.gui.StationCaption.ARROW_E,
            org.traincontrol.gui.StationCaption.ARROW_W
        };

        for (String arrow : arrows)
        {
            assertTrue(font.canDisplay(arrow.codePointAt(0)),
                "the caption font " + org.traincontrol.gui.StationCaption.LABEL_FONT + " has no glyph "
                + "for U+" + Integer.toHexString(arrow.codePointAt(0)) + ", so every train facing that "
                + "way draws a tofu box on the diagram");
        }
    }

    /**
     * A rotated caption's arrow still points where the train is going.
     *
     * Adam, 2026-08-27: "then we just need to also apply the same logical rotation to the arrow."
     *
     * This is the assertion the rest of the rotation cannot make. Turning the drawing turns everything
     * in it, so an arrow keeps its meaning only if the GLYPH is chosen for where it will end up: a
     * quarter turn anticlockwise sends right to up, so a caption that means north is drawn with the
     * east glyph. Get that backwards and every caption is in exactly the right place with every train
     * appearing to run the other way, and no check on an inset would see it.
     *
     * Decided by painting and reading the ink, because it is a question about the picture. A triangle
     * pointing up has its widest row at the BOTTOM of its ink; one pointing down has it at the top.
     * That holds for either pair of glyphs, which matters - which pair gets used depends on the fonts
     * installed on the machine.
     *
     * MUTATION: cycling the arrows the other way - north to west rather than north to east - swaps
     * both answers and fails both halves. Not rotating them at all fails both as well: an unturned
     * north glyph is drawn pointing left.
     */
    @Test
    public void testARotatedArrowStillPointsWhereTheTrainIsGoing() throws Exception
    {
        int up = widestInkRow(org.traincontrol.gui.StationCaption.ARROW_N);
        int down = widestInkRow(org.traincontrol.gui.StationCaption.ARROW_S);

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
     * Two things that have to agree with a third. Painting, sizing and hit-testing all ask
     * `pillBounds` for one rectangle, and the reason they do is the defect that turned up when they
     * did not: the flat pill was painted inside its insets and hit-tested over the whole component,
     * and swallowed every click on the tile it had borrowed centring room from. A rotated caption
     * borrows a whole ROW instead, so the same mistake would eat a click on the square above.
     *
     * MUTATION: having `contains` fall back to `super.contains` fails the last assertion; returning
     * the flat preferred size when rotated fails the first.
     */
    @Test
    public void testARotatedCaptionStandsOnEnd() throws Exception
    {
        org.traincontrol.gui.StationCaption pill = onEnd("Ostbahnhof");

        java.awt.Dimension size = pill.getPreferredSize();

        assertTrue(size.height > size.width,
            "a rotated caption asks for a box " + size.width + " by " + size.height + ", which is "
            + "wider than it is tall - it is being measured along its text as though it were flat, "
            + "and will draw itself straight out of the bottom of the room it was given");

        org.traincontrol.gui.StationCaption flat = new org.traincontrol.gui.StationCaption();

        flat.setPill(true);
        flat.setFont(pill.getFont());
        flat.setText("Ostbahnhof");

        java.awt.Dimension across = flat.getPreferredSize();

        assertTrue(across.width > across.height,
            "a caption that was NOT rotated is taller than it is wide, so the rotation is being "
            + "applied to every caption rather than to the ones on north-south track");

        // The pill is one line THICK, wherever the insets put it - so a point a good way past that
        // thickness is not on the caption, and the tile there belongs to the diagram.
        int left = pill.getInsets().left;

        assertTrue(pill.contains(left + pill.lineHeight() / 2, pill.getInsets().top + 5),
            "the middle of the drawn pill does not take the mouse, so a caption cannot be clicked");

        assertFalse(pill.contains(left + pill.lineHeight() + 8, pill.getInsets().top + 5),
            "a rotated caption takes the mouse well past the pill it draws, on track that belongs to "
            + "the diagram underneath - which is how a switch stops throwing when it is clicked");
    }

    /**
     * A rotated caption carrying one arrow, painted, with the row of its ink that is widest.
     *
     * @param arrow the direction the caption means
     * @return where the widest row of ink falls, 0 at the top of the ink and 100 at the bottom
     */
    private int widestInkRow(String arrow) throws Exception
    {
        org.traincontrol.gui.StationCaption pill = onEnd(arrow);

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

                // The TEXT, which is white on a navy pill - not the pill itself.
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

    /**
     * A caption stood on end, coloured so its text can be told from its pill.
     */
    private org.traincontrol.gui.StationCaption onEnd(String text)
    {
        org.traincontrol.gui.StationCaption pill = new org.traincontrol.gui.StationCaption();

        pill.setPill(true);
        pill.setRotated(true);
        pill.setFont(new java.awt.Font(
            org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 24));
        pill.setBackground(org.traincontrol.gui.StationCaption.PILL);
        pill.setForeground(java.awt.Color.WHITE);
        pill.setText(text);
        pill.setTileGeometry(60, 0, 60,
            org.traincontrol.gui.StationCaption.captionOffset(60, pill.lineHeight()));

        return pill;
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
     * A sensor's caption knows which way its rails run whatever the sensor was reporting when the layout was saved
     * (OB-263; Adam, 2026-10-06: "Fix OB-263").
     *
     * A sensor tile has one state in the port table, and the state a Central Station saves for it is whether a train
     * stood on it at the time: 1 found no ports at all, so a vertical station saved occupied read as running east to
     * west and its caption lay flat - persistently, and differently for two identical stations.
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

        // Compared against the name read independently above (TST-C9), not against
        // session.describeTile(square) again - both sides of that comparison come from the same
        // method under test, so a broken describeTile would move with the heading and still match.
        assertEquals(heading.getText(), expectedName,
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
     * And it is not taken away 120ms later either, which is the half OB-109 left behind (MT-273).
     *
     * Adam, testing MT-273: **"Track diagram viewer works.  The editor and autonomy still has the
     * flicker."**
     *
     * `OB-109` stopped a rebuild HIDING the page it was replacing - the test above pins that - and
     * said nothing to the grace timer that runs 120ms later. So the page stayed up, and was then
     * taken away and put behind a spinner anyway. The symptom moved rather than went: from an instant
     * blink to a blink a moment later, which no longer matched the description of the bug that had
     * been closed. That is why it survived a fix, a validation round and four review passes.
     *
     * **Why the editor and not the viewer.** The editor rebuilds the whole grid after every
     * placement, and the tile just placed is precisely the decode that keeps the outstanding count
     * above zero. So the timer fired on the commonest action there, and almost never in the viewer.
     *
     * **What the rule is now.** A rebuild at the SAME tile size is not an arrival: every square is a
     * cache hit at that key, so the page is complete when it is mounted. A rebuild at a DIFFERENT
     * size is an arrival wearing a rebuild's clothes - the size is part of the cache key, so nothing
     * is cached and the page really does come in square by square. The second half of this test is
     * the control that keeps that distinction alive.
     *
     * MUTATION: remove the `sameSizeRebuild` early return and the first assertion fails; make it
     * unconditional on `replacing` and the control fails instead.
     */
    @Test
    public void testARebuiltDiagramIsNotTakenAwayAMomentLater() throws Exception
    {
        final LayoutDiagram layout = model.getLayout(model.getLayoutList().get(0));

        final javax.swing.JPanel panel = new javax.swing.JPanel();

        // A first grid, so that everything after it is a replacement over a panel that has a diagram.
        javax.swing.SwingUtilities.invokeAndWait(() ->
            new org.traincontrol.gui.LayoutGrid(layout, 30, panel, null, true, ui));

        final java.util.concurrent.CountDownLatch settled =
            new java.util.concurrent.CountDownLatch(1);

        javax.swing.SwingUtilities.invokeLater(() -> ui.whenTilesSettled(settled::countDown));

        assertTrue(settled.await(30, java.util.concurrent.TimeUnit.SECONDS),
            "the tiles never finished decoding, so nothing below is a check");

        // Held open for the whole of both halves: this is what a placement does - one tile of a type
        // not yet drawn at this size - and it is what keeps the timer armed.
        ui.tileDecodeStarted();

        final boolean[] sameSize = {false};
        final int[] sameSizeChildren = {0};
        final boolean[] newSize = {true};

        try
        {
            final org.traincontrol.gui.LayoutGrid[] built = new org.traincontrol.gui.LayoutGrid[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
                built[0] = new org.traincontrol.gui.LayoutGrid(layout, 30, panel, null, true, ui));

            // PAST THE GRACE TIMER, which is the whole point - the assertion above this one in the
            // class reads visibility in the same pass that built the grid, and so cannot see this.
            Thread.sleep(400);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                sameSize[0] = built[0].getContainer().isVisible();
                sameSizeChildren[0] = panel.getComponentCount();
            });

            // THE CONTROL: a different size IS an arrival, and must still be held back.  Without
            // this, "never hide anything, ever" passes the assertion above.
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

        assertFalse(pages.isEmpty(), "no pages to build - is test/test_layout present?");

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

    /**
     * The train is drawn over the station caption, and the caption is still there beside it.
     *
     * OB-159. Adam: "it is a z order issue.  The stations paint over the locomotives."
     *
     * This is the other half of the test above, and the two cannot both be satisfied by an ORDER.
     * A station caption and the tile under it are separate components; put the caption in front and
     * the locomotive standing on that platform is painted over, which is what he reported; put the
     * tile in front and the NAME is painted out, because a tile is opaque, which is OB-117.
     *
     * So the trains are no longer a z-order at all. The container paints its children in the order it
     * always did - tiles, then captions - and then walks them once more asking each for its train, so
     * the locomotive lands above both and the name survives everywhere the locomotive is not.
     *
     * Asserted as a DIFFERENCE rather than against a colour: the same arrangement is rendered with a
     * train published and without one, and the middle of the square has to change. That is true of
     * every correct drawing and says nothing about which shade of what the locomotive is.
     *
     * MUTATION this catches: drop the paintTrainOverCaptions loop from newDiagramContainer and the
     * middle of the square is the caption in both renders, so nothing changes and the first assertion
     * fails. Paint the train inside the tile again instead and it fails the same way, because the
     * caption is in front of the tile.
     */
    @Test
    public void testTheTrainIsDrawnOverTheStationCaption() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("rendering a diagram needs a display");
        }

        // A big tile, so there is room to sample the caption somewhere the locomotive does not reach.
        // The icon is drawn centred at 76% of the square, and the run outline is a line on the very
        // edge, so a point near the left edge and low down is inside the caption and outside both.
        final int size = 120;

        org.traincontrol.gui.LayoutLabel tile =
            new org.traincontrol.gui.LayoutLabel(null, null, size, null, false);

        org.traincontrol.gui.StationCaption caption = new org.traincontrol.gui.StationCaption();

        caption.setText("Bottom Main C");
        caption.setOpaque(true);
        // OPAQUE, deliberately (TCS-A2).
        //
        // The resting fill has an alpha, so a caption drawn over a train still lets 18% of the train
        // through - and an exact-RGB comparison would then find the two pictures different and pass
        // whichever way round they were painted.  A solid pill is the honest question: with the train
        // in front the pixel changes, with the train behind it cannot.
        caption.setBackground(new java.awt.Color(0, 0, 115));

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size, size);
        grid.setBackground(java.awt.Color.WHITE);

        tile.setBounds(0, 0, size, size);

        // Where a pill actually sits: below the rail, over the lower part of the square.
        caption.setBounds(0, size * 2 / 3, size, size / 4);

        grid.add(caption);
        grid.add(tile);

        // The order the diagram is built in: captions in front of tiles.
        grid.setComponentZOrder(caption, 0);

        BufferedImage empty = shotOf(grid, size);

        // A train standing on this square, and running - which is the case that gets the icon.
        tile.setAutonomyOverlay(new org.traincontrol.automationui.TileOverlay(
            org.traincontrol.automationui.TileOverlay.State.ACTIVE, true, true, null));

        BufferedImage withTrain = shotOf(grid, size);

        javax.imageio.ImageIO.write(empty, "png", new File(OUT, "train-over-caption-empty.png"));
        javax.imageio.ImageIO.write(withTrain, "png", new File(OUT, "train-over-caption-train.png"));

        // SAMPLED WHERE THE CAPTION AND THE ICON OVERLAP (TCS-A2).
        //
        // This used to sample the middle of the square, (60, 60) at this size - which is above the
        // pill entirely, since the caption sits at (0, 80, 120, 30).  The caption never painted there
        // under either arrangement, so the pixel was identical whether the train was drawn over the
        // captions or back inside its tile, and the test passed with OB-159 put back.
        //
        // Three quarters of the way down is inside the pill and inside the icon, which spans 76% of
        // the square about its centre.  It is the only place the question can be asked.
        // THE CONTROL FIRST, because it is what tells the two failures apart.
        //
        // If the icon file cannot be loaded the overlay draws a plain dot instead, which does not
        // reach the pill - and the overlap assertion below would then fail saying the caption is
        // painted over the locomotive, which would be a confident wrong diagnosis. Ordered so the
        // honest message comes out first.
        assertNotEquals(withTrain.getRGB(size / 2, size / 2), empty.getRGB(size / 2, size / 2),
            "no train was drawn on the square at all, so nothing below this can mean anything");

        // Then the overlap, which is the question OB-159 is about.
        //
        // Two thirds of the way down rather than three quarters: the pill runs from 2/3 to 11/12 of
        // the square, and the icon's PAINTED extent - not its bounding box, which is what a previous
        // note here described - ends around 0.765 of the way down. At 3/4 the sample sat about a pixel
        // inside the art at every size, which is a test one icon redraw away from a mystery.
        int overlapX = size / 2;
        int overlapY = size * 17 / 24;

        assertNotEquals(withTrain.getRGB(overlapX, overlapY), empty.getRGB(overlapX, overlapY),
            "the station caption is painted over the locomotive - which is what Adam reported, and "
            + "what the third painting pass exists to stop");

        // AND THE CAPTION IS STILL THERE, which is the half OB-117 is about.
        //
        // Sampled inside the pill and outside both the icon - which spans 76% of the square, centred -
        // and the run outline, which is a line on the very edge.  If the train were painting the
        // whole square this pixel would have changed with it.
        int atX = size / 12;
        int atY = size * 3 / 4;

        // AND THE CALL SITE, which the pixels cannot reach (TCS-A2).
        //
        // This method asks `LayoutGrid.newDiagramContainer()` itself, so the container it photographs
        // is the right one however the application builds its own.  Put a plain JPanel back at the
        // one place that calls the factory and every pixel above is unchanged - the defect moves to
        // the call, which is exactly what happened to testDiagramExport.
        String gridSource = new String(java.nio.file.Files.readAllBytes(
            new File("src/org/traincontrol/gui/LayoutGrid.java").toPath()), "UTF-8");

        // The FACTORY is what matters, not what the variable is called - this named the local and
        // would have failed a rename that broke nothing.
        assertTrue(gridSource.split("newDiagramContainer\\(").length > 2,
            "the diagram is no longer built from newDiagramContainer(), so the third painting pass "
            + "that draws trains over captions is not in the container the application shows - and "
            + "every pixel this test compares came from a container it made itself");

        assertEquals(withTrain.getRGB(atX, atY), empty.getRGB(atX, atY),
            "the caption changed where the locomotive does not reach, so the train is painting over "
            + "more than itself - and a locomotive that paints out the name is OB-117 coming back "
            + "the other way");
    }

    /**
     * Lays out a panel and paints it into an image, which is the only way to ask what a drawing does.
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
     * A caption on a running route is in the route's colours: the blue of the path ahead until the train gets there (Adam,
     * 2026-10-09: "Build 2,3,5,6" - the third of the diagram look proposals).  The captions had kept the red the route
     * itself was drawn in before FR-106 moved the route to blue ahead and grey behind.
     *
     * On both pills the operator can choose, because the blue has to read on each: the path's own blue on the pale grey,
     * lifted to a light blue on the blue pill, where the path's own blue would disappear.
     *
     * MUTATION: let `onPill` answer the route's blue with plain black or white - what it did - and this fails.
     */
    @Test
    public void testARouteCaptionStaysInTheRoutesBlueOnEitherPill()
    {
        java.awt.Color ahead = org.traincontrol.automationui.DiagramColours.PATH_AHEAD;

        for (java.awt.Color fill : new java.awt.Color[] {
            org.traincontrol.gui.StationCaption.PILL_GREY, org.traincontrol.gui.StationCaption.PILL_AT_REST })
        {
            java.awt.Color drawn = org.traincontrol.gui.StationCaption.onPill(fill, ahead);

            assertTrue(drawn.getBlue() > drawn.getRed() + 60 && drawn.getBlue() > drawn.getGreen() + 40,
                "a caption the train has not reached yet is drawn in " + drawn + " on " + fill + ", not the route's blue");

            assertTrue(distance(drawn, fill) > 90, "the route's blue cannot be read on " + fill + ": " + drawn);
        }

        assertEquals(org.traincontrol.gui.StationCaption.onPill(org.traincontrol.gui.StationCaption.PILL_GREY, ahead),
            ahead, "on the pale grey pill the caption is not the route's own blue but a cousin of it");
    }

    /**
     * The blue captions, the station badges, the route and the Auto tab's station badge are one blue (the sixth look
     * proposal).  The captions were navy, rgb(0,0,115), beside badges and a route of rgb(0,0,200): two blues a shade
     * apart, which the caption's own note says look like a mistake rather than a family.
     *
     * MUTATION: put the navy back and this fails.
     */
    @Test
    public void testTheCaptionsAreTheBadgesBlue()
    {
        assertEquals(org.traincontrol.gui.StationCaption.PILL.getRGB() & 0xFFFFFF,
            org.traincontrol.automationui.DiagramColours.STATION.getRGB() & 0xFFFFFF,
            "the blue captions are " + org.traincontrol.gui.StationCaption.PILL + " beside badges of "
            + org.traincontrol.automationui.DiagramColours.STATION + " - two blues a shade apart");
    }

    /**
     * A destination is ringed in a dark orange, not the route's blue (Adam, 2026-10-09: "give the pill a dark orange
     * border instead of the blue border"); the fill says "destination" and the pill draws the ring whenever it has that
     * fill.
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
        org.traincontrol.gui.StationCaption pill = new org.traincontrol.gui.StationCaption();

        pill.setPill(true);
        pill.setFont(new java.awt.Font(org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 20));
        pill.setBackground(org.traincontrol.gui.StationCaption.DESTINATION_FILL);
        pill.setForeground(java.awt.Color.BLACK);
        pill.setText("Carlton");
        pill.setTileGeometry(60, 0, 0, org.traincontrol.gui.StationCaption.captionOffset(60, pill.lineHeight()));

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
     * A pill's text sits in the middle of it, as much room at the right end as at the left (Adam, 2026-10-09: "In the
     * station label pills, there has always been a padding issue on the right ... Fix it").
     *
     * The pill was sized from the text's width as measured for layout, and the text drawn by the label's own painter,
     * centred by that same measurement; painted at a larger scale - a display scaled to 125 or 150 per cent, or the 2x
     * the guide's pictures are made at - the glyphs come out wider than measured, and all of the difference lands at the
     * right end.  Painted here at 2x, the room either side of the ink is measured: with the arrow on the left, on the
     * right, and none.
     *
     * MUTATION: draw the text where the label's layout put it again, and this fails.
     */
    @Test
    public void testAPillsTextSitsInTheMiddleOfIt()
    {
        String[] texts = {
            org.traincontrol.gui.StationCaption.withArrow("ICE 3", " " + org.traincontrol.gui.StationCaption.ARROW_W),
            org.traincontrol.gui.StationCaption.withArrow("ICE 3", " " + org.traincontrol.gui.StationCaption.ARROW_E),
            "Carlton", "75 407 DB" };

        for (double scale : new double[] {1.25, 1.5, 2.0})
        for (int points : new int[] {12, 15})
        {
            for (String text : texts)
            {
                org.traincontrol.gui.StationCaption pill = new org.traincontrol.gui.StationCaption();

                pill.setPill(true);
                pill.setFont(new java.awt.Font(org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, points));
                pill.setBackground(org.traincontrol.gui.StationCaption.PILL_GREY);
                pill.setForeground(java.awt.Color.BLACK);
                pill.setText(text);
                pill.setTileGeometry(30, 0, 0, org.traincontrol.gui.StationCaption.captionOffset(30, pill.lineHeight()));

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

                // The pill's ends, along its middle row
                int mid = (int) ((drawn.y + drawn.height / 2.0) * scale), left = -1, right = -1;

                for (int x = 0; x < shot.getWidth(); x++)
                {
                    if (((shot.getRGB(x, mid) >>> 24) & 0xFF) > 100)
                    {
                        if (left < 0) left = x;
                        right = x;
                    }
                }

                // The ink, anywhere across the pill's height
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

                // AND UP AND DOWN (Adam, 2026-10-09: "make sure the pill label padding issue on the right/lower-right is
                // addressed").  Asked of the two names without an arrow: neither has a letter that hangs below the
                // line, so their ink is the capitals' height and should sit in the middle of the pill.  An arrow is a
                // symbol from another font and sits where that font puts it.
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
     * A caption that names a train has a faint edge, so it parts from a signal or a switch drawn under it; a placeholder
     * dash has none, so an empty station gets no louder (D2 of the look's design pass; Adam, 2026-10-09: "Do D1 and D2").
     *
     * MUTATION: drop the edge, or draw it on the dash too, and this fails.
     */
    @Test
    public void testANamedCaptionHasAnEdgeAndADashHasNone()
    {
        java.awt.Color fill = org.traincontrol.gui.StationCaption.PILL_GREY;

        int[] edge = new int[2];

        String[] texts = {"Carlton", org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY};

        for (int i = 0; i < texts.length; i++)
        {
            org.traincontrol.gui.StationCaption pill = new org.traincontrol.gui.StationCaption();

            pill.setPill(true);
            pill.setFont(new java.awt.Font(org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 15));
            pill.setBackground(fill);
            pill.setForeground(java.awt.Color.BLACK);
            pill.setText(texts[i]);
            pill.setTileGeometry(30, 0, 0, org.traincontrol.gui.StationCaption.captionOffset(30, pill.lineHeight()));

            java.awt.Dimension size = pill.getPreferredSize();

            pill.setBounds(0, 0, size.width, size.height);

            java.awt.Rectangle drawn = pill.drawnBounds();

            java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(
                size.width, size.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);

            java.awt.Graphics2D g = shot.createGraphics();

            pill.paint(g);

            g.dispose();

            // The top edge, a quarter of the way along: darker than the fill by how much?
            java.awt.Color top = new java.awt.Color(shot.getRGB(drawn.x + drawn.width / 2, drawn.y), true);

            edge[i] = (fill.getRed() + fill.getGreen() + fill.getBlue()) / 3 - (top.getRed() + top.getGreen() + top.getBlue()) / 3;
        }

        assertTrue(edge[0] > 30, "a caption naming a train has no edge: its top is only " + edge[0] + " darker than its fill");

        assertTrue(edge[1] < 15, "an empty station's dash pill has an edge " + edge[1] + " darker than its fill - it is"
            + " louder, where D1 made it quieter");
    }

    /**
     * The curve and switch tiles are the mirror images their shapes say they are, at both sizes (Adam, 2026-10-09:
     * "make a pass on the curve and switch tile to make sure they are symmetrical.  It looks like there are some pixel
     * artifacts, as they were made in raster programs"; "extend it to the others too, like the 4-way").
     *
     * A curve between the south and east sides is its own mirror image across the diagonal; a left switch is a right
     * switch turned over; a crossing is the same turned a quarter.  Drawn by hand, they were not: a set sensor on a curve
     * differed from its own mirror image in 78 pixels at 30 and 230 at 60, the crossing switch from itself turned half
     * round in 164 and 256.  They are drawn from their geometry now (`docs/tools/tile-icons.py`), and a pixel more than
     * a shade out (12 of 255) on either side of a mirror is a pixel this counts.
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
     * A pill's edge (D2) and a destination's ring are as thick on the right and the bottom as on the left and the top
     * (Adam, 2026-10-09: "make sure the pill label padding issue on the right/lower-right is addressed").
     *
     * They were drawn as `drawRoundRect(x, y, w - 1, h - 1)` - the whole-pixel way of keeping a one-pixel outline inside
     * a box, which with anti-aliasing puts the left and top of the outline half outside the fill and the right and
     * bottom wholly inside it, a sliver of fill showing past them.  Painted at twice the size, where the pill's ends
     * fall on whole pixels, the four sides of an even outline read the same from the outside in.
     *
     * MUTATION: draw the outline the old way and this fails.
     */
    @Test
    public void testAPillsOutlineIsEvenAllRound()
    {
        java.awt.Color[] fills = {org.traincontrol.gui.StationCaption.PILL_GREY, org.traincontrol.gui.StationCaption.DESTINATION_FILL};

        for (java.awt.Color fill : fills)
        {
            org.traincontrol.gui.StationCaption pill = new org.traincontrol.gui.StationCaption();

            pill.setPill(true);
            pill.setFont(new java.awt.Font(org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 15));
            pill.setBackground(fill);
            pill.setForeground(java.awt.Color.BLACK);
            pill.setText("Carlton");
            pill.setTileGeometry(30, 0, 0, org.traincontrol.gui.StationCaption.captionOffset(30, pill.lineHeight()));

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

            // the middle of the long sides: a column a third of the way along, clear of the text
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
     * A square's address is red letters with a white halo round them, and no box behind them (Adam, 2026-10-09: "Can we
     * prettify address labels to have a white halo outline around the red text rather than a rectangle around them?").
     *
     * Each address label on a page with Show Addresses on is painted alone onto black.  A box behind the text lights the
     * label's corners; a halo leaves them black and puts white only round the letters.
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

                // and the letters, red on white
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
                && !(c instanceof org.traincontrol.gui.StationCaption))
            {
                String text = ((javax.swing.JLabel) c).getText();

                if (text != null && text.replaceAll("<[^>]*>", "").trim().matches("\\d+[rg]?(\\s.*)?")) into.add((javax.swing.JLabel) c);
            }

            if (c instanceof java.awt.Container) collectAddresses((java.awt.Container) c, into);
        }
    }

    /**
     * A side trains may not arrive by is marked with the same grey chevron at the large size as at the small one (OB-326;
     * Adam, 2026-10-09: "In 60px view, the trains can't arrive from this direction arrow's have an odd shape.  Make it
     * consistent with the 30px, and gray.").
     *
     * The mark is painted on a 30-pixel square and on a 60-pixel one.  Every pixel it inks is grey - as much red as green
     * as blue - and the large one, shrunk to half, carries the ink the small one does: a chevron whose arms scale with
     * the square, not a hollow shape whose strokes come apart at the larger size.
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
     * A caption stood on end beside a station's vertical track clears that station's badge, and the badge of a station on
     * the next square over (OB-327; Adam, 2026-10-09: "narrow/nudge so that pills rotated vertically don't touch adjacent
     * stations or terminuses").
     *
     * The badge is MEASURED, painted as the diagram paints a station on vertical track, so this follows whatever size the
     * badge rule gives; the caption is built and placed as the grid builds and places one, at both tile sizes, empty and
     * with a train's name.  It stands to the right of its own track, so its near edge must clear its own badge and its
     * far edge the same badge one square to the right, each by a pixel at 30 and two at 60.
     *
     * MUTATION: place it by the flat caption's offset again, or give it the full line height, and this fails.
     */
    @Test
    public void testAPillOnEndClearsTheStationsBesideIt()
    {
        String[] texts = {org.traincontrol.gui.LayoutGrid.LAYOUT_STATION_EMPTY, "75 407 DB",
            org.traincontrol.gui.StationCaption.withArrow("EN57-947", " " + org.traincontrol.gui.StationCaption.ARROW_W)};

        for (int tile : new int[] {30, 60})
        {
            // The station's badge, as drawn, measured across its middle row
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

            int margin = tile >= 60 ? 2 : 1;

            for (String text : texts)
            {
                org.traincontrol.gui.StationCaption pill = new org.traincontrol.gui.StationCaption();

                // as the grid builds one: the caption font, a tenth or so smaller, then stood on end and placed
                java.awt.Font font = new java.awt.Font(org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN,
                    tile / 2);

                pill.setPill(true);
                pill.setFont(font.deriveFont(font.getSize2D() * org.traincontrol.gui.StationCaption.FONT_SCALE));
                pill.setBackground(org.traincontrol.gui.StationCaption.PILL_GREY);
                pill.setText(text);
                pill.setRotated(true);
                pill.setTileGeometry(tile, 0, 0, org.traincontrol.gui.StationCaption.captionOffset(tile, pill.lineHeight()));

                java.awt.Dimension size = pill.getPreferredSize();

                pill.setBounds(0, 0, size.width, size.height);

                java.awt.Rectangle drawn = pill.drawnBounds();

                // the cell of a caption on end starts at its own square, so these are from the square's left edge
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
     * A caption stood on end is drawn at the size of one lying flat (FR-118; Adam, 2026-10-09: *"the small font on the
     * vertical labels is really hard to read.  Add an FR to revert their size"*).  OB-327 drew it 15% smaller so that it
     * cleared the station badges either side; the new badges, no taller than the sensor's contact, leave room for it -
     * `testAPillOnEndClearsTheStationsBesideIt`.  Measured as painted: the letters across their line, flat and on end.
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
        org.traincontrol.gui.StationCaption pill = new org.traincontrol.gui.StationCaption();

        java.awt.Font font = new java.awt.Font(org.traincontrol.gui.StationCaption.LABEL_FONT, java.awt.Font.PLAIN, tile / 2);

        pill.setPill(true);
        pill.setFont(font.deriveFont(font.getSize2D() * org.traincontrol.gui.StationCaption.FONT_SCALE));
        pill.setBackground(org.traincontrol.gui.StationCaption.PILL_GREY);
        pill.setForeground(java.awt.Color.BLACK);
        pill.setText("Hgjy Halt");
        pill.setRotated(rotated);
        pill.setTileGeometry(tile, 0, 0, org.traincontrol.gui.StationCaption.captionOffset(tile, pill.lineHeight()));

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
     * A square's address is drawn over the locomotive standing on it, not under it (FR-116; Adam, 2026-10-09: "Red
     * address labels look good, but make sure they are rendered on top of the autonomy locomotive icons").
     *
     * The diagram's container paints its children and then every tile's train over them (OB-159), so an address,
     * being a child, was painted over by the icon.  Here an address lies across the middle of a square with a running
     * train on it, where the icon is; its red letters must all still be there with the train drawn.
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

        org.traincontrol.gui.AddressLabel address = new org.traincontrol.gui.AddressLabel();

        address.setForeground(java.awt.Color.RED);
        address.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, size / 3));
        address.setLines("88");

        javax.swing.JPanel grid = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        grid.setLayout(null);
        grid.setSize(size, size);
        grid.setBackground(java.awt.Color.WHITE);

        tile.setBounds(0, 0, size, size);

        java.awt.Dimension d = address.getPreferredSize();

        // across the middle of the square, where the icon is drawn
        address.setBounds((size - d.width) / 2, (size - d.height) / 2, d.width, d.height);

        grid.add(address);
        grid.add(tile);

        // the order the grid builds them in: an address in front of its tile
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
     * The mark beside "No available paths" on the Auto tab is the dark grey of the window's side tabs' icons, not blue
     * (Adam, 2026-10-09: "the (i) icon in no available paths should be dark grey, matching the color in the sidebar
     * tabs").
     *
     * The card's own icon, read off the class that draws it, against the commonest colour of the side tabs' pictures.
     *
     * @throws Exception from reading the icons
     */
    @Test
    public void testTheNoPathsMarkIsTheSideTabsGrey() throws Exception
    {
        java.lang.reflect.Field field = org.traincontrol.gui.AutoLocomotiveStatus.class.getDeclaredField("INFO_ICON");

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

    /** The commonest opaque colour in a picture that is not white. */
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
     * A running train's destination is drawn in the orange the train's line shows on white, solid (Adam, 2026-10-09:
     * "Make the yellow labels (trains on their way somewhere) have the same orange background color as occupied train
     * tiles, just without the fading", then "Go with as the line actually looks on white").
     *
     * The colour against the train's see-through line laid on white - worked out here from the line's own colour and
     * see-through - and the pill painted on white: its commonest colour, the fill between the ring and the letters, is
     * that orange exactly, with nothing more of the white through it.
     *
     * MUTATION: put the bright orange or the warm yellow back, or draw the destination see-through, and this fails.
     */
    @Test
    public void testADestinationIsTheLineAsItLooksOnWhite()
    {
        java.awt.Color fill = org.traincontrol.gui.StationCaption.DESTINATION_FILL;
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
    public static final class CountingAddress extends org.traincontrol.gui.AddressLabel
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
     * A repaint of a corner of the diagram paints the addresses in that corner and no others (RSA60-C1): the pass that
     * draws the addresses over the trains took every one on the page, whatever was being repainted - with Show Addresses
     * on, a hover's 3 x 3 repaint cost a pass over all 112 on Adam's main page.
     *
     * Forty addresses across a diagram, a repaint clipped to the first one's corner: it is painted, by the ordinary pass
     * and the one over the trains, and nothing else is.
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
     * over squares that paint their own white (Adam, 2026-10-10: *"can we instead make the stations spill over onto
     * adjacent tiles?"*).  Nine squares of 30 pixels in the diagram's own container, a station on an E-S curve in the
     * middle: its blue reaches the squares to its right and below, which were painted after it.
     *
     * MUTATION: take the spill pass out of `LayoutGrid.newDiagramContainer`, and this fails.
     */
    @Test
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

            // painted after the station, as a later square on the page is
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
     * And a station that spills repaints what it spills onto: when its square is repainted, or its icon is taken away, the
     * diagram is asked to repaint the squares around it too - the diagram paints a square's neighbours only where it is
     * asked to, and the old spill would stay there.  Asked of Swing's repaint queue.
     *
     * MUTATION: repaint only the square itself again, and this fails.
     */
    @Test
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
     * A curve's station spills onto the squares beside it at the middle of their edges, which is where their arrows are
     * (Adam, 2026-10-10: *"make sure that the optional ingress/egress arrows remain visible, especially on curves"*).  The
     * arrows of a square a spill reaches are drawn back over it.  Nine squares in the diagram's own container, a station
     * on an E-S curve in the middle, every kind - the may-turn and must-turn icons and a terminus are longer than the
     * curve's chord - and the square to its right a one-way road from W to E, or the square below from N to S, at 30 and
     * 60 pixels: that square's arrows have all their pixels with the station beside it as without.
     *
     * MUTATION: take the arrows' pass over the spill out of `LayoutGrid.newDiagramContainer`, and this fails.
     */
    @Test
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
     * The tunnel portal's light grey wall is a pixel wider each side at 30 and two at 60 - two pixels and five - and the
     * arch between the walls is still the track's width, 8 and 16 (Adam, 2026-10-10: *"make the light gray tunnel wall on
     * tunnel icons about 1px wider on each side in the 30px version, and correspondingly"* at 60).  The wall grew
     * outward: the track through the arch is where it was.
     *
     * Read across the portal below the arch's curve - row 12 at 30, row 25 at 60.
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

            int row = size >= 60 ? 25 : 12, wall = size >= 60 ? 5 : 2, arch = size >= 60 ? 16 : 8;

            java.util.List<int[]> runs = new java.util.ArrayList<>();

            for (int x = 0; x < size; x++)
            {
                int kind = (art.getRGB(x, row) & 0xFFFFFF) == 0xBBBBBB ? 1 : (art.getRGB(x, row) & 0xFFFFFF) == 0 ? 2 : 0;

                if (!runs.isEmpty() && runs.get(runs.size() - 1)[0] == kind) runs.get(runs.size() - 1)[1]++;
                else runs.add(new int[] {kind, 1});
            }

            StringBuilder said = new StringBuilder();

            for (int[] run : runs) said.append(run[0] == 1 ? "L" : run[0] == 2 ? "#" : ".").append(run[1]).append(' ');

            java.util.List<Integer> walls = new java.util.ArrayList<>();

            for (int[] run : runs) if (run[0] == 1) walls.add(run[1]);

            assertEquals(walls, java.util.Arrays.asList(wall, wall), "the tunnel's wall at " + size + " pixels, across row "
                + row + ": " + said + "- Adam asked for it a pixel wider each side at 30, correspondingly at 60");

            int between = 0;

            for (int i = 0; i < runs.size(); i++)
            {
                if (runs.get(i)[0] == 1 && i + 2 < runs.size() && runs.get(i + 2)[0] == 1) between = runs.get(i + 1)[1];
            }

            assertEquals(between, arch, "the tunnel's arch at " + size + " pixels is no longer the track's width: " + said);
        }
    }
}
