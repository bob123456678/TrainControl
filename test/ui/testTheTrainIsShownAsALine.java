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
     * And it is a LINE - most of the square is drawn exactly as it is with nothing there.
     */
    @Test(dependsOnMethods = "testACoveredSquareIsMarkedInOrange")
    public void testTheMarkIsALineAndNotAWash()
    {
        int changed = differingPixels(withTheTrain, without);

        int all = TILE * TILE;

        assertTrue(changed > 0, "the two pictures are identical, so nothing marks the square at all");

        assertTrue(changed < all / 2,
            changed + " of the tile's " + all + " pixels change when a train stands behind this "
            + "square, which is a wash over the whole of it rather than a line along the track. Adam: "
            + "\"instead of shading the entire tiles, we need to draw a line ... graying makes it look "
            + "confusing on double curve tiles\"");
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
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        return r > 190 && g > 70 && g < 190 && b < 90 && r - g > 60;
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
}
