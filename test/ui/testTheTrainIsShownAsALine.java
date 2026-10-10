package ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * MT-309, first half - a train is shown as a line along the track, not as a grey wash over the tile.
 *
 * Adam, 2026-09-08: *"instead of shading the entire tiles, we need to draw a line (let's say in
 * orange) to show that the train is there.  graying makes it look confusing on double curve tiles."*
 * Asked whether the line joins the wash or replaces it, he chose **one indicator, not two**: the line
 * replaces the wash entirely.
 *
 * **A double curve is the case that makes the point.** Two separate roads cross that square, and a
 * wash over the whole of it says a train is on both. A line says which.
 *
 * **What is asserted, and why it is pixels.** Two things have to be true and neither can be read off
 * the model: that a covered square is marked at all, and that the mark is a LINE - which is to say
 * that most of the tile is drawn exactly as it would be with nothing standing anywhere. The old wash
 * was `fillRect` over the whole icon, so every pixel of a covered tile differed from the same tile
 * uncovered; a line changes the pixels it runs over and no others. So the same tile is painted twice,
 * once with the train and once without, and the two pictures are compared.
 *
 * The orange is recognised by hue rather than by a constant, deliberately: the assertion is "somebody
 * looking at this square sees orange on it", and a test reading production's own constant would pass
 * for any colour that constant was changed to, including grey.
 *
 * MUTATION: put `ImageUtil.addCoveredOverlay` back and `testTheMarkIsALineAndNotAWash` fails with
 * every pixel of the tile changed. Draw nothing at all and `testACoveredSquareIsMarkedInOrange`
 * fails.
 *
 * @author Adam
 */
public class testTheTrainIsShownAsALine
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;

    /** The parent these labels are given, so they and the window's own can never evict each other. */
    private static final JPanel OURS = new JPanel();

    private static final int TILE = 30;

    private static Locomotive train;
    private static Integer trainLengthWas;

    /** A square the train is lying across, and the tile drawn on it. */
    private static TileKey covered;
    private static LayoutLabel tile;

    /** The same tile painted with the train standing there, and with it gone. */
    private static BufferedImage withTheTrain;
    private static BufferedImage without;

    /** The tile with the train standing there, drawn in the other style a preference may choose: coaches. */
    private static BufferedImage asCoaches;

    /** And in the other colour a preference may choose: the soft orange, solid. */
    private static BufferedImage asSoftOrange;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("painting a tile needs a display");
        }

        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // This opened `cs2_sample_layout`, whose placements and lengths move as Adam runs trains on it
        // - so on 2026-09-09 this class reported "no standing train covers any track here" against a
        // working tree where every train had been driven away, and a class that skips everything reads
        // as a green one.  `live-snapshot` is the same railway with the clock stopped at `e6f4649c`,
        // which is the state these assertions were written against.
        //
        // `Scenario.folderFor` rather than a path: it is the only naming of a fixture folder that
        // cannot spell its way out to the operator's own railway, and `LayoutSandbox` still copies
        // what it names before anything reads it.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null) throw new SkipException("no autonomy setup on this railway");

        SwingUtilities.invokeAndWait(() -> measureEveryTileAs(1));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        oneTrainThatCoversSomething();

        if (train == null) throw new SkipException("no standing train covers any track here");

        refreshCoveredTrack();

        covered = firstCoveredSquareWithATile();

        if (covered == null) throw new SkipException("no covered square has a tile to draw");

        tile = drawnTileFor(covered);

        withTheTrain = paint(tile);

        // And in the style a preference may choose instead of the line: whole coaches (T2)
        LayoutLabel.setTailStyle(LayoutLabel.TailStyle.COACHES);

        try
        {
            asCoaches = paint(tile);
        }
        finally
        {
            LayoutLabel.setTailStyle(null);
        }

        LayoutLabel.setTailColour(LayoutLabel.TailColour.SOFT);

        try
        {
            asSoftOrange = paint(tile);
        }
        finally
        {
            LayoutLabel.setTailColour(null);
        }

        // AND THE SAME SQUARE WITH NOTHING STANDING ANYWHERE.  A length of zero is how this railway
        // says "not set", and an unmeasured train covers nothing - so the square goes back to being
        // ordinary track without anything else about the diagram changing.
        train.setTrainLength(0);

        refreshCoveredTrack();

        SwingUtilities.invokeAndWait(() -> tile.refreshCoveredMark());

        pump();

        assertFalse(ui.isTrackCovered(covered),
            "the square is still reported as covered with the train's length cleared, so the second "
            + "picture is of the same state as the first");

        without = paint(tile);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (layout != null) layout.stopLocomotives();

            // PUT BACK: init() opens Adam's own locomotive database.
            if (train != null) train.setTrainLength(trainLengthWas == null ? 0 : trainLengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The mark is there, and it is orange.
     */
    @Test
    public void testACoveredSquareIsMarkedInOrange()
    {
        assertEquals(orangePixels(without), 0,
            "the square is drawn with orange on it while nothing is standing anywhere, so the "
            + "assertion below cannot tell the mark from the tile art");

        assertTrue(orangePixels(withTheTrain) > 0,
            "nothing orange is drawn on " + covered + " while a train is lying across it. Adam: "
            + "\"we need to draw a line (let's say in orange) to show that the train is there\"");
    }

    /**
     * And the TRAIN mark is a LINE - it covers a small part of the square, not the whole of it.
     *
     * **MEASURED IN ORANGE, and it used to be measured in changed pixels** (W7B-B1, 2026-09-09).
     *
     * The square this class chooses is a covered one, and a covered square that is an intermediate
     * tile of a covered edge is also a BLOCKED one - so it now carries the grey wash as well as the
     * line. The wash was fenced on a running railway and this class runs at idle, so counting every
     * pixel that differs from the bare square used to count the line alone. It counts both now: 900 of
     * 900, and the class reported that the train was being drawn as a wash. It is not; there are two
     * marks and this one is still a line.
     *
     * Adam's complaint that MT-309 fixed is about the TRAIN mark specifically - *"instead of shading
     * the entire tiles, we need to draw a line (let's say in orange) to show that the train is there.
     * graying makes it look confusing on double curve tiles"* - because a double curve carries two
     * roads and a fill says the train is on both. So the population to measure is the orange, which is
     * what that complaint is about, rather than everything that changed.
     *
     * The wash is not unmeasured as a result: `ui.testBlockedTrackIsGreyWhileAutonomyRuns` and
     * `ui.testTheGreyAppearsAtIdleToo` both assert that it covers the whole square, and the second
     * asserts that the line survives underneath it.
     */
    @Test(dependsOnMethods = "testACoveredSquareIsMarkedInOrange")
    public void testTheMarkIsALineAndNotAWash()
    {
        int changed = differingPixels(withTheTrain, without);

        int all = TILE * TILE;

        assertTrue(changed > 0, "the two pictures are identical, so nothing marks the square at all");

        int line = orangePixels(withTheTrain);

        assertTrue(line < all / 2,
            line + " of the tile's " + all + " pixels are orange when a train is lying across this "
            + "square, which is the train drawn as a wash over the whole of it rather than as a line "
            + "along the track. Adam: \"instead of shading the entire tiles, we need to draw a line "
            + "... graying makes it look confusing on double curve tiles\"");
    }

    /**
     * CONTROL - the mark lands on the square's own track rather than anywhere on the tile.
     *
     * A line drawn from corner to corner would satisfy both tests above and would be worse than the
     * wash. The rails are the darkest thing on a tile, so the mark has to sit where the art is.
     */
    @Test(dependsOnMethods = "testTheMarkIsALineAndNotAWash")
    public void testTheLineFollowsTheTrack()
    {
        int on = 0;
        int off = 0;

        for (int x = 0; x < TILE; x++)
        {
            for (int y = 0; y < TILE; y++)
            {
                if (!isOrange(withTheTrain.getRGB(x, y))) continue;

                if (nearArt(without, x, y)) on++; else off++;
            }
        }

        assertTrue(on > off,
            "only " + on + " of the " + (on + off) + " orange pixels lie near the track art on "
            + covered + ", so the mark is drawn across the square rather than along the rails");
    }

    // ---------------------------------------------------------------- looking at the picture

    /**
     * Whether this colour is the orange a person would call orange.
     *
     * By hue rather than against production's own constant: the claim is about what somebody looking
     * at the diagram sees, and a test reading the constant would agree with any value it was changed
     * to.
     *
     * @param rgb the pixel
     * @return true when it reads as orange
     */
    private static boolean isOrange(int rgb)
    {
        return support.Rendered.isTrainOrange(rgb);
    }

    private static int orangePixels(BufferedImage image)
    {
        int found = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                if (isOrange(image.getRGB(x, y))) found++;
            }
        }

        return found;
    }

    private static int differingPixels(BufferedImage a, BufferedImage b)
    {
        int found = 0;

        for (int x = 0; x < a.getWidth(); x++)
        {
            for (int y = 0; y < a.getHeight(); y++)
            {
                if (a.getRGB(x, y) != b.getRGB(x, y)) found++;
            }
        }

        return found;
    }

    /**
     * Whether the UNMARKED picture has tile art within a couple of pixels of here.
     *
     * The same question testDiagramLooksRight asks of the run line, and for the same reason: the mark
     * is a stroke centred on the rail, so its edges sit a little either side of the art.
     */
    private static boolean nearArt(BufferedImage bare, int atX, int atY)
    {
        for (int y = Math.max(0, atY - 3); y <= Math.min(bare.getHeight() - 1, atY + 3); y++)
        {
            for (int x = Math.max(0, atX - 3); x <= Math.min(bare.getWidth() - 1, atX + 3); x++)
            {
                int rgb = bare.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (r < 236 || g < 236 || b < 236) return true;
            }
        }

        return false;
    }

    /**
     * Paints one tile, on its own, exactly as the diagram paints it.
     *
     * `paint` and not `printAll`: both of those begin with an isShowing() check and do nothing for a
     * component that is not on screen, which is every component here - the same reason support.Rendered
     * gives.
     */
    private static BufferedImage paint(final LayoutLabel label) throws Exception
    {
        final BufferedImage[] shot = new BufferedImage[1];

        SwingUtilities.invokeAndWait(() ->
        {
            label.setSize(TILE, TILE);

            shot[0] = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = shot[0].createGraphics();

            try
            {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, TILE, TILE);

                label.paint(g);
            }
            finally
            {
                g.dispose();
            }
        });

        return shot[0];
    }

    // ---------------------------------------------------------------- the fixture

    private static void refreshCoveredTrack() throws Exception
    {
        // AND WAITED FOR (OB-192).  The window works the marks out on a worker now, because asking
        // for them on the event thread meant waiting on the `Layout` monitor a dispatch holds - so
        // asking and reading are two moments, and `support.CoveredMarks` is the one place that knows
        // it.
        support.CoveredMarks.refresh(ui);

        pump();
    }

    private static TileKey firstCoveredSquareWithATile()
    {
        for (TileKey square : everyTile())
        {
            if (!ui.isTrackCovered(square)) continue;

            org.traincontrol.base.LayoutDiagram page = model.getLayout(square.getPage());

            if (page == null) continue;

            LayoutDiagramComponent component = page.getComponent(square.getX(), square.getY());

            if (component == null || component.isText()) continue;

            return square;
        }

        return null;
    }

    /**
     * A real tile for a square, drawn and ready to be painted.
     */
    private static LayoutLabel drawnTileFor(TileKey square) throws Exception
    {
        org.traincontrol.base.LayoutDiagram page = model.getLayout(square.getPage());

        LayoutDiagramComponent component = page.getComponent(square.getX(), square.getY());

        final LayoutLabel[] built = new LayoutLabel[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutLabel(component, OURS, TILE, ui, false);
            built[0].setSquare(square);
        });

        ui.getDiagramTileRegistry().register(square, built[0]);

        long until = System.currentTimeMillis() + 30000;

        while (built[0].getIcon() == null && System.currentTimeMillis() < until) pump();

        assertNotNull(built[0].getIcon(),
            "the tile never drew its image, so every picture below would be of an empty square");

        SwingUtilities.invokeAndWait(() -> built[0].refreshCoveredMark());

        pump();

        return built[0];
    }

    private static Set<TileKey> everyTile()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) out.add(step.getTile());
            }
        }

        return out;
    }

    /**
     * Leaves one train standing, and only one that actually covers track - a train whose recorded
     * arrival side leads nowhere the graph can follow covers nothing, and every picture here would be
     * of an ordinary square.
     */
    private static void oneTrainThatCoversSomething()
    {
        Point keep = null;

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() == null || point.getArrivedFrom() == null) continue;

            Locomotive candidate = point.getCurrentLocomotive();

            Integer was = candidate.getTrainLength();

            candidate.setTrainLength(4);

            boolean covers = layout.edgesCoveredByStandingTrains().containsValue(candidate);

            candidate.setTrainLength(was);

            if (covers)
            {
                keep = point;

                break;
            }
        }

        if (keep == null) return;

        for (Point point : layout.getPoints())
        {
            if (point == keep || point.getCurrentLocomotive() == null) continue;

            layout.moveLocomotive(null, point.getName(), true);
        }

        train = keep.getCurrentLocomotive();

        trainLengthWas = train.getTrainLength();

        train.setTrainLength(4);
    }

    private static void measureEveryTileAs(int units)
    {
        Set<TileKey> everyTile = new LinkedHashSet<>();

        for (TileKey tile : session.getReducer().getPoints().keySet()) everyTile.add(tile);

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) everyTile.add(step.getTile());
            }
        }

        for (TileKey tile : everyTile) session.setTileLength(tile, units);
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }

    /**
     * The tail shows over a run line too (Adam, FR-106: "when autonomy is moving, we have a green coloring for completed
     * paths and red for pending.  but the train tails aren't shown.  make the tail visible at all times"): a running
     * train's tail lies on the road it has just driven, which the run draws as reached, and the tail was drawn under it.
     *
     * The covered square painted again with the run's reached line along the very road the tail is drawn on: the orange
     * is all still there.
     *
     * MUTATION: draw the tail under the run line alone, and this fails.
     *
     * @throws Exception from the window
     */
    @Test(dependsOnMethods = "testTheLineFollowsTheTrack")
    public void testTheTailShowsOverARunLine() throws Exception
    {
        // THE TRAIN BACK, lying across the square
        train.setTrainLength(4);

        refreshCoveredTrack();

        SwingUtilities.invokeAndWait(() -> tile.refreshCoveredMark());

        pump();

        try
        {
            assertTrue(ui.isTrackCovered(covered), "precondition: the train's length back, the square is not covered");

            int alone = orangePixels(paint(tile));

            assertTrue(alone > 0, "precondition: no orange on the covered square");

            // THE ROAD THE TAIL IS DRAWN ON, and a run's reached line along it
            java.lang.reflect.Method roads = LayoutLabel.class.getDeclaredMethod("coveredRoads");

            roads.setAccessible(true);

            @SuppressWarnings("unchecked")
            java.util.List<org.traincontrol.automationui.TilePorts.Route> covering =
                (java.util.List<org.traincontrol.automationui.TilePorts.Route>) roads.invoke(tile);

            assertFalse(covering.isEmpty(), "precondition: the tile names no road the tail is on");

            org.traincontrol.automationui.TilePorts.Route road = covering.get(0);

            SwingUtilities.invokeAndWait(() -> tile.setAutonomyOverlay(new org.traincontrol.automationui.TileOverlay(
                org.traincontrol.automationui.TileOverlay.State.REACHED, false, java.util.Arrays.asList(
                    new org.traincontrol.automationui.TileOverlay.Segment(road.getA(), road.getB(),
                        org.traincontrol.automationui.TileOverlay.State.REACHED)))));

            pump();

            BufferedImage run = paint(tile);

            int underARun = orangePixels(run);

            assertTrue(underARun * 10 >= alone * 9, "the train's tail on " + covered + " is drawn under the run's line"
                + " along the road it lies on - " + underARun + " orange pixels of " + alone + " - so a running train's tail"
                + " is not shown (FR-106)");
        }
        finally
        {
            SwingUtilities.invokeAndWait(() -> tile.setAutonomyOverlay(null));

            train.setTrainLength(0);

            refreshCoveredTrack();

            SwingUtilities.invokeAndWait(() -> tile.refreshCoveredMark());

            pump();
        }
    }

    /**
     * Where coaches are chosen, a train is drawn as whole coaches, one to a square, each stopping short of the square's
     * edge, so the gaps fall at
     * the joins on a curve as on a straight and no coach is ever cut short (T2 of the look's train tails; Adam,
     * 2026-10-09: "Do T2 only if you can maintain shape continuity across curves rather than a jagged look when it
     * straightens out").
     *
     * A dash pattern laid along the line would restart at every square and leave a stub wherever a square's road is not a
     * whole number of dashes long - the diagonal chord of a curve, about seven-tenths of a straight one.  One coach to a
     * square, its ends trimmed by half a gap, has no stub to leave.  So nothing orange reaches the edge of the square.
     *
     * MUTATION: draw the line edge to edge again and this fails.
     */
    @Test(dependsOnMethods = "testACoveredSquareIsMarkedInOrange")
    public void testTheTrainIsDrawnAsWholeCoaches()
    {
        int atTheEdge = orangeAtTheEdge(asCoaches);

        assertEquals(atTheEdge, 0, atTheEdge + " orange pixels lie on the edge of " + covered + " - the train is drawn"
            + " edge to edge, not as a coach that stops short of the square, so the next square's coach meets it with no"
            + " gap");
    }

    /**
     * The train is drawn as the line, edge to edge along its road, unless a preference chooses coaches (Adam,
     * 2026-10-09, having seen both: "revert back to the original occupied line shape").
     *
     * MUTATION: make coaches the default again, or draw coaches whatever the style, and this fails.
     */
    @Test(dependsOnMethods = "testACoveredSquareIsMarkedInOrange")
    public void testTheTrainIsDrawnAsALineByDefault()
    {
        assertTrue(orangeAtTheEdge(withTheTrain) > 0, "the train on " + covered + " stops short of the square's edge -"
            + " it is drawn as coaches, where Adam chose the line: \"revert back to the original occupied line shape\"");
    }

    /**
     * The train's line is see-through: the track it lies on shows through it (Adam, 2026-10-09, from the tail colour
     * options: "can we change to the see through tail line").
     *
     * Read off the picture, as the commonest orange pixel - the line's own colour where it crosses what is under it,
     * its anti-aliased edges being the minority - rather than off the constant.  A solid line's commonest pixel is the
     * solid colour, as bright as 245 or 255; a see-through one is darkened by the black and grey track under it.
     *
     * MUTATION: draw it solid, or make the soft orange the default, and this fails.
     */
    @Test(dependsOnMethods = "testACoveredSquareIsMarkedInOrange")
    public void testTheTrainIsSeeThrough()
    {
        int commonest = commonestOrange(withTheTrain);

        int brightest = Math.max((commonest >> 16) & 0xFF, Math.max((commonest >> 8) & 0xFF, commonest & 0xFF));

        assertTrue(brightest <= 220, "the train's line on " + covered + " is a solid " + rgbOf(commonest) + " - the"
            + " track does not show through it, where Adam chose the see-through line");
    }

    /**
     * The soft orange, rgb(245,140,60), solid, is still the other colour a preference may choose (Adam, 2026-10-09:
     * "make it easy to switch to that one too").
     *
     * MUTATION: let the switch choose nothing, or the soft orange be another colour, and this fails.
     */
    @Test(dependsOnMethods = "testACoveredSquareIsMarkedInOrange")
    public void testTheSoftOrangeIsStillAChoice()
    {
        int commonest = commonestOrange(asSoftOrange);

        int r = (commonest >> 16) & 0xFF, g = (commonest >> 8) & 0xFF, b = commonest & 0xFF;

        assertTrue(Math.abs(r - 245) <= 4 && Math.abs(g - 140) <= 4 && Math.abs(b - 60) <= 4, "set to the soft orange,"
            + " the train's line on " + covered + " is " + rgbOf(commonest) + ", not rgb(245,140,60)");
    }

    /** The commonest orange pixel in a picture of the square. */
    private static int commonestOrange(BufferedImage shot)
    {
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();

        for (int y = 0; y < shot.getHeight(); y++)
        {
            for (int x = 0; x < shot.getWidth(); x++)
            {
                int rgb = shot.getRGB(x, y) & 0xFFFFFF;

                if (isOrange(rgb)) counts.merge(rgb, 1, Integer::sum);
            }
        }

        assertFalse(counts.isEmpty(), "nothing orange on " + covered + " to read the colour off");

        return java.util.Collections.max(counts.entrySet(), java.util.Map.Entry.comparingByValue()).getKey();
    }

    private static String rgbOf(int rgb)
    {
        return "rgb(" + ((rgb >> 16) & 0xFF) + "," + ((rgb >> 8) & 0xFF) + "," + (rgb & 0xFF) + ")";
    }

    /**
     * The orange pixels on a picture's outermost ring.
     *
     * @param shot the tile, painted
     * @return how many there are
     */
    private static int orangeAtTheEdge(BufferedImage shot)
    {
        int atTheEdge = 0;

        for (int y = 0; y < shot.getHeight(); y++)
        {
            for (int x = 0; x < shot.getWidth(); x++)
            {
                boolean edge = x <= 0 || y <= 0 || x >= shot.getWidth() - 1 || y >= shot.getHeight() - 1;

                if (edge && isOrange(shot.getRGB(x, y))) atTheEdge++;
            }
        }

        return atTheEdge;
    }

    /** The large size, where a cap is wide enough to tell round from flat. */
    private static final int LARGE = 60;

    /**
     * The train's line ends rounded where the train ends, not cut off flat on the square's edge (Adam, 2026-10-09: "Can
     * we make the end of a train (orange line) rounded, not a straight jagged edge?").
     *
     * Each of the train's end squares - covered, beside a covered square, with a side whose square beside the train does
     * not reach - painted at the large size: nothing orange on that side's edge, where a line cut off there lays its
     * whole width.
     *
     * MUTATION: run the line to the edge at the train's end as it runs between squares, and this fails.
     *
     * @throws Exception from the window
     */
    @Test(dependsOnMethods = "testACoveredSquareIsMarkedInOrange")
    public void testTheTrainEndsRounded() throws Exception
    {
        try
        {
            train.setTrainLength(4);

            refreshCoveredTrack();

            int ends = 0;

            for (TileKey square : everyTile())
            {
                if (!ui.isTrackCovered(square)) continue;

                java.util.List<org.traincontrol.automationui.TilePorts.Side> open = new java.util.ArrayList<>();

                boolean runsOn = false;

                for (org.traincontrol.automationui.TilePorts.Side side : org.traincontrol.automationui.TilePorts.Side.values())
                {
                    TileKey beside = besideOn(square, side);

                    if (beside == null) continue;

                    if (ui.isTrackCovered(beside)) runsOn = true;
                    else open.add(side);
                }

                if (!runsOn || open.isEmpty()) continue;

                org.traincontrol.base.LayoutDiagram page = model.getLayout(square.getPage());

                LayoutDiagramComponent component = page == null ? null : page.getComponent(square.getX(), square.getY());

                if (component == null || component.isText()) continue;

                BufferedImage shot = paintAt(drawnTileFor(square, LARGE), LARGE);

                // A square the train covers with no line to draw - a link's - says nothing about how a line ends
                if (orangePixels(shot) == 0) continue;

                ends++;

                for (org.traincontrol.automationui.TilePorts.Side side : open)
                {
                    int onTheEdge = orangeOnTheEdge(shot, side);

                    assertTrue(onTheEdge <= 1, "the train's line on " + square + " is cut off flat on its " + side
                        + " edge (" + onTheEdge + " orange pixels on it), where the train goes no further - it does not"
                        + " end rounded");
                }
            }

            assertTrue(ends > 0, "precondition: the train has no end square with a line on it to look at");
        }
        finally
        {
            train.setTrainLength(0);

            refreshCoveredTrack();
        }
    }

    /**
     * A square whose own line did not change is redrawn when the square beside it changed: the train's last square ends
     * rounded only while the train goes no further, so a train grown by a square has to redraw the square that was its
     * last (the rounded end).
     *
     * Asked of the window's own refresh, through a tile registered for that square, with Swing's repaint requests
     * recorded.
     *
     * MUTATION: redraw only the squares whose roads changed, and this fails.
     *
     * @throws Exception from the window
     */
    @Test(dependsOnMethods = "testACoveredSquareIsMarkedInOrange")
    public void testTheSquareBesideAChangedLineIsRedrawn() throws Exception
    {
        try
        {
            java.util.Map<TileKey, Set<org.traincontrol.automationui.TileGraph.RouteId>> atThree = coveredAt(3);
            java.util.Map<TileKey, Set<org.traincontrol.automationui.TileGraph.RouteId>> atFour = coveredAt(4);

            // THE SQUARE: covered at both lengths along the same roads, beside one only the longer train covers
            TileKey kept = null;

            for (TileKey square : atThree.keySet())
            {
                if (!atThree.get(square).equals(atFour.get(square))) continue;

                for (org.traincontrol.automationui.TilePorts.Side side : org.traincontrol.automationui.TilePorts.Side.values())
                {
                    TileKey beside = besideOn(square, side);

                    if (beside != null && atFour.containsKey(beside) && !atThree.containsKey(beside)) kept = square;
                }
            }

            assertNotNull(kept, "precondition: no square the train covers at a length of 3 lies beside one it covers only"
                + " at 4: " + atThree.keySet() + " / " + atFour.keySet());

            coveredAt(3);

            final LayoutLabel watched = drawnTileFor(kept, TILE);

            final Set<java.awt.Component> asked = java.util.Collections.synchronizedSet(new java.util.HashSet<java.awt.Component>());

            final javax.swing.RepaintManager was = javax.swing.RepaintManager.currentManager(watched);

            SwingUtilities.invokeAndWait(() -> javax.swing.RepaintManager.setCurrentManager(new javax.swing.RepaintManager()
            {
                @Override
                public void addDirtyRegion(javax.swing.JComponent c, int x, int y, int w, int h)
                {
                    asked.add(c);

                    super.addDirtyRegion(c, x, y, w, h);
                }
            }));

            try
            {
                coveredAt(4);
            }
            finally
            {
                SwingUtilities.invokeAndWait(() -> javax.swing.RepaintManager.setCurrentManager(was));
            }

            assertTrue(asked.contains(watched), "the train grew from 3 to 4 and " + kept + ", its last square before,"
                + " was not redrawn - its line stays rounded short of a square the train now covers");
        }
        finally
        {
            train.setTrainLength(0);

            refreshCoveredTrack();
        }
    }

    /** The train at a length, the window's marks worked out again, and the squares it covers along their roads. */
    private static java.util.Map<TileKey, Set<org.traincontrol.automationui.TileGraph.RouteId>> coveredAt(int length)
        throws Exception
    {
        train.setTrainLength(length);

        refreshCoveredTrack();

        java.util.Map<TileKey, Set<org.traincontrol.automationui.TileGraph.RouteId>> out = new java.util.LinkedHashMap<>();

        // THE SENSORS' SQUARES TOO, which `everyTile` - the squares along the roads - leaves out: a train lies across them
        Set<TileKey> squares = new LinkedHashSet<>(everyTile());

        squares.addAll(session.getReducer().getPoints().keySet());

        for (TileKey square : squares)
        {
            if (ui.isTrackCovered(square)) out.put(square, new LinkedHashSet<>(ui.coveredRoutesAt(square)));
        }

        return out;
    }

    /** The square beside one across a side, on the same page. */
    private static TileKey besideOn(TileKey square, org.traincontrol.automationui.TilePorts.Side side)
    {
        switch (side)
        {
            case N: return new TileKey(square.getPage(), square.getX(), square.getY() - 1);
            case S: return new TileKey(square.getPage(), square.getX(), square.getY() + 1);
            case E: return new TileKey(square.getPage(), square.getX() + 1, square.getY());
            case W: return new TileKey(square.getPage(), square.getX() - 1, square.getY());
            default: return null;
        }
    }

    /** The orange pixels on one side's edge of a picture. */
    private static int orangeOnTheEdge(BufferedImage shot, org.traincontrol.automationui.TilePorts.Side side)
    {
        int found = 0;

        int w = shot.getWidth(), h = shot.getHeight();

        for (int i = 0; i < (side == org.traincontrol.automationui.TilePorts.Side.N
            || side == org.traincontrol.automationui.TilePorts.Side.S ? w : h); i++)
        {
            int x, y;

            switch (side)
            {
                case N: x = i; y = 0; break;
                case S: x = i; y = h - 1; break;
                case W: x = 0; y = i; break;
                default: x = w - 1; y = i; break;
            }

            if (isOrange(shot.getRGB(x, y))) found++;
        }

        return found;
    }

    /** A real tile for a square at a size, drawn and ready to be painted. */
    private static LayoutLabel drawnTileFor(TileKey square, int size) throws Exception
    {
        org.traincontrol.base.LayoutDiagram page = model.getLayout(square.getPage());

        LayoutDiagramComponent component = page.getComponent(square.getX(), square.getY());

        final LayoutLabel[] built = new LayoutLabel[1];

        SwingUtilities.invokeAndWait(() ->
        {
            built[0] = new LayoutLabel(component, OURS, size, ui, false);
            built[0].setSquare(square);
        });

        ui.getDiagramTileRegistry().register(square, built[0]);

        long until = System.currentTimeMillis() + 30000;

        while (built[0].getIcon() == null && System.currentTimeMillis() < until) pump();

        assertNotNull(built[0].getIcon(), "the tile on " + square + " never drew its image");

        SwingUtilities.invokeAndWait(() -> built[0].refreshCoveredMark());

        pump();

        return built[0];
    }

    /** A tile painted at a size, as `paint` paints one at the small size. */
    private static BufferedImage paintAt(final LayoutLabel label, final int size) throws Exception
    {
        final BufferedImage[] shot = new BufferedImage[1];

        SwingUtilities.invokeAndWait(() ->
        {
            label.setSize(size, size);

            shot[0] = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = shot[0].createGraphics();

            try
            {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, size, size);

                label.paint(g);
            }
            finally
            {
                g.dispose();
            }
        });

        return shot[0];
    }
}
