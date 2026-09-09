package ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
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
 * MT-309, third ruling - **the drawing says "track is blocked" again, and it says it in grey**.
 *
 * Adam, 2026-09-09: *"'train is here' should also mean 'track is blocked' - that is the whole point.
 * it's the same as greying out edges, just in a different way."*  And, choosing between widening the
 * line, narrowing the guard, and carrying two marks at once: *"Let's do c, but can we just grey out
 * the tiles just like blocked edges while autonomy is running?  That plus the line, drawn and
 * refreshed carefully, should do the trick."*
 *
 * **What went wrong, and why a test could not see it.**  The wash used to wash the whole EDGE - one
 * hop between two sensors, which can be a dozen squares - so a train of length one greyed seventeen.
 * On 2026-09-08 the wash was replaced by an orange line as long as the train, which answered that
 * report and Adam's separate complaint about double curves.  It also made the picture a strict SUBSET
 * of what routing refuses, and behaviour.md 5c was changed to say so in bold.  That is the half being
 * reversed here: both marks now stand together, and between them the drawing and the guard agree
 * again.
 *
 * **Three kinds of square, and each has to look different:**
 *
 * - orange on it - a train is lying there;
 * - grey and no orange - track a train's presence has made unusable;
 * - neither - free.
 *
 * **And the grey is NOT bounded to while autonomy is running, which it was until W7B-B1.**  The
 * bound was Adam's own - *"while autonomy is running"* - and the refusal it draws never had one, so a
 * stopped railway refused manual sends over track this drew as free.  Adam, 2026-09-09: *"yes, this
 * greyout should appear at idle and be regenerated if a placement or train/track length is changed."*
 * This class keeps its name and its subject - while autonomy runs, blocked track is grey - and
 * `ui.testTheGreyAppearsAtIdleToo` carries the idle half.
 *
 * **The blocked extent is worked out here from the railway rather than asked of the window**, which
 * is deliberate: the claim is that what is drawn is what `Layout.edgesCoveredByStandingTrains`
 * refuses, and asking the window would be asking the thing under test what the right answer is.  The
 * endpoint squares are excluded, which is Adam's own ruling about the covered set - *"edges, because
 * the points are technically unoccupied"* - and the same exclusion `AutonomySession.pathBetween`
 * makes.
 *
 * **Asserted in pixels**, because none of it can be read off the model.  Whether a square is greyed
 * is decided when the tile is PAINTED; every model-level assertion available answers identically with
 * the grey present and absent, which is the lesson OB-180 and `support.Rendered` were written for.
 *
 * MUTATION: fence the wash on `isAutonomyBusy()` again and `testAStoppedRailwayShowsTheSameGrey`
 * fails.  Draw it only where the line is drawn - the state before the grey came back - and
 * `testBlockedTrackIsGreyedWhileAutonomyRuns` fails.  Draw the wash OVER the line and
 * `testASquareWithATrainOnItIsStillOrange` fails.  Wash the whole page and
 * `testFreeTrackIsNotTouched` fails.
 *
 * @author Adam
 */
public class testBlockedTrackIsGreyWhileAutonomyRuns
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

    /** A square the train is drawn on, one that is only blocked by it, and one that is neither. */
    private static TileKey coveredSquare;
    private static TileKey blockedSquare;
    private static TileKey freeSquare;

    private static LayoutLabel coveredTile;
    private static LayoutLabel blockedTile;
    private static LayoutLabel freeTile;

    /** Each of the three squares painted in each of the three states. */
    private static BufferedImage runningCovered;
    private static BufferedImage runningBlocked;
    private static BufferedImage runningFree;

    private static BufferedImage stoppedCovered;
    private static BufferedImage stoppedBlocked;
    private static BufferedImage stoppedFree;

    private static BufferedImage bareCovered;
    private static BufferedImage bareBlocked;
    private static BufferedImage bareFree;

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

        if (!chooseTheThreeSquares())
        {
            throw new SkipException(
                "no train on this railway blocks a square it is not drawn on, so the two marks "
                + "cannot be told apart here");
        }

        coveredTile = drawnTileFor(coveredSquare);
        blockedTile = drawnTileFor(blockedSquare);
        freeTile = drawnTileFor(freeSquare);

        // STOPPED, with the train exactly where it is.  Both marks are drawn (W7B-B1).
        setRunning(false);

        refreshCoveredTrack();

        repaintTheThree();

        stoppedCovered = paint(coveredTile);
        stoppedBlocked = paint(blockedTile);
        stoppedFree = paint(freeTile);

        // RUNNING, same train, same squares.
        setRunning(true);

        refreshCoveredTrack();

        repaintTheThree();

        runningCovered = paint(coveredTile);
        runningBlocked = paint(blockedTile);
        runningFree = paint(freeTile);

        // AND RUNNING WITH NOTHING STANDING ANYWHERE, which is the reference every "is this square
        // marked at all" question is asked against.  A length of zero is how this railway says "not
        // set", and an unmeasured train covers nothing.
        train.setTrainLength(0);

        refreshCoveredTrack();

        repaintTheThree();

        bareCovered = paint(coveredTile);
        bareBlocked = paint(blockedTile);
        bareFree = paint(freeTile);

        train.setTrainLength(trainLength);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (layout != null)
            {
                setRunning(false);

                layout.stopLocomotives();
            }

            // PUT BACK: init() opens Adam's own locomotive database.
            if (train != null) train.setTrainLength(trainLengthWas == null ? 0 : trainLengthWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    // ---------------------------------------------------------------- the four claims

    /**
     * A square nothing is drawn on, but which the railway refuses, is greyed while autonomy runs.
     */
    @Test
    public void testBlockedTrackIsGreyedWhileAutonomyRuns()
    {
        assertEquals(orangePixels(runningBlocked), 0,
            "the orange line is drawn on " + blockedSquare + ", so it is a square the train is shown "
            + "on rather than one merely blocked by it, and this test is asking about the wrong tile");

        assertTrue(brightness(runningBlocked) < brightness(bareBlocked) - 2.0,
            "the square " + blockedSquare + " is drawn no darker while a train blocks it ("
            + brightness(runningBlocked) + ") than with nothing standing anywhere ("
            + brightness(bareBlocked) + "), so nothing says the track is unusable. Adam: \"'train is "
            + "here' should also mean 'track is blocked' - that is the whole point\"");
    }

    /**
     * And it is grey rather than another line: the whole square is darkened, not a stroke across it.
     */
    @Test(dependsOnMethods = "testBlockedTrackIsGreyedWhileAutonomyRuns")
    public void testTheBlockedMarkIsAWashOverTheWholeSquare()
    {
        int changed = differingPixels(runningBlocked, bareBlocked);

        int all = TILE * TILE;

        assertTrue(changed > all / 2,
            "only " + changed + " of the square's " + all + " pixels change when it becomes blocked, "
            + "so the mark is a line along part of it rather than the tile being greyed. Adam: "
            + "\"can we just grey out the tiles just like blocked edges\"");
    }

    /**
     * And a stopped railway shows exactly the same grey - the mark is NOT bounded to a run (W7B-B1).
     *
     * **This test asserted the opposite until 2026-09-09, and the reversal is the point of it.**  The
     * bound was Adam's own sentence - *"can we just grey out the tiles just like blocked edges while
     * autonomy is running?"* - and the reasoning written down for it was that blocked track is a fact
     * about routing, and nothing is routing when nothing is running.
     *
     * That reasoning is wrong about this railway, and the review that found it says how: the REFUSAL
     * was never fenced.  `Layout.isPathClear` sweeps the covered edges in every tier at every time,
     * correctly, because a tail lying across the rail is physical.  So at idle a right-click manual
     * send across a parked train's tail was refused over track this drew as free - which is the same
     * complaint that brought the grey back for the running case, arriving through the other door.
     *
     * Adam, asked about the idle case directly, 2026-09-09: *"yes, this greyout should appear at idle
     * and be regenerated if a placement or train/track length is changed."*
     *
     * **What this class still claims** is its own name: while autonomy runs, blocked track is grey.
     * That was true before and is true now.  What has gone is the word ONLY.
     * `ui.testTheGreyAppearsAtIdleToo` carries the idle half, including the regeneration Adam asked
     * for in the same sentence.
     */
    @Test
    public void testAStoppedRailwayShowsTheSameGrey()
    {
        assertTrue(support.Rendered.same(stoppedBlocked, runningBlocked),
            "the square " + blockedSquare + " is drawn differently with autonomy stopped than with it "
            + "running, though the train has not moved and the railway refuses the same track either "
            + "way.  The grey is fenced on a running railway again, and at idle the operator is "
            + "offered a destination the send will refuse (W7B-B1).  Adam: \"yes, this greyout should "
            + "appear at idle\"");

        assertTrue(brightness(stoppedBlocked) < brightness(bareBlocked) - 2.0,
            "the square " + blockedSquare + " is drawn no darker with a train blocking it and "
            + "autonomy STOPPED (" + brightness(stoppedBlocked) + ") than with no train at all ("
            + brightness(bareBlocked) + "), so nothing on a stopped railway says the track is "
            + "unusable");
    }

    /**
     * The orange line is untouched by any of it - drawn stopped, drawn running, and the same line.
     */
    @Test
    public void testTheOrangeLineIsDrawnEitherWay()
    {
        int stopped = orangePixels(stoppedCovered);
        int running = orangePixels(runningCovered);

        assertTrue(stopped > 0,
            "no orange is drawn on " + coveredSquare + " with autonomy stopped, so the train is not "
            + "shown at all and every comparison below says nothing");

        assertTrue(running > 0,
            "the orange line on " + coveredSquare + " disappears when autonomy starts - the grey has "
            + "been drawn over the train rather than under it");

        assertTrue(running >= stopped * 0.8 && running <= stopped * 1.25,
            "the line on " + coveredSquare + " is " + running + " orange pixels while running and "
            + stopped + " while stopped, so starting autonomy changed the line itself rather than "
            + "adding a wash beneath it");
    }

    /**
     * A square that is both - the train is on it AND it is blocked - reads as both.
     */
    @Test(dependsOnMethods = "testTheOrangeLineIsDrawnEitherWay")
    public void testASquareWithATrainOnItIsStillOrange()
    {
        // AGAINST THE BARE PICTURE, and it used to be against the STOPPED one (W7B-B1).
        //
        // While the grey was fenced on a running railway, "stopped" was the same square with no wash
        // on it and that comparison said what this claim means.  The fence is gone: the two pictures
        // are now identical by design, and the difference this looked for could only ever be zero.
        // The square with nothing standing anywhere is the picture that has no wash on it now.
        assertTrue(brightness(runningCovered) < brightness(bareCovered) - 2.0,
            "the square the train stands on is drawn no darker while a train covers it ("
            + brightness(runningCovered) + ") than with nothing standing anywhere ("
            + brightness(bareCovered) + "), so the square carrying the train is the one square "
            + "that does not say it is blocked");

        assertTrue(orangePixels(runningCovered) > 0,
            "the train on " + coveredSquare + " cannot be seen through the wash laid over it");
    }

    /**
     * CONTROL - track no train blocks is left alone, running or not.
     *
     * Without this every assertion above is satisfied by greying the whole page while autonomy runs.
     */
    @Test
    public void testFreeTrackIsNotTouched()
    {
        assertTrue(support.Rendered.same(runningFree, bareFree),
            "the square " + freeSquare + ", which no train covers or blocks, is drawn differently "
            + "while a train stands elsewhere - the wash is being laid over track that is free");

        assertTrue(support.Rendered.same(stoppedFree, bareFree),
            "the square " + freeSquare + " changes with autonomy stopped as well");
    }

    // ---------------------------------------------------------------- the railway's own answer

    /**
     * The squares the railway holds blocked, worked out from the covered EDGES.
     *
     * Not asked of the window: the window is what is under test, and a test that asks it what the
     * answer is agrees with it whatever it says.  This is the translation `AutonomySession` performs -
     * a covered `Edge` runs between two `Point`s, each `Point` maps back to a square, and the reducer
     * holds the tiles lying between two squares - with the endpoints excluded, because a train
     * standing at a sensor is shown standing there and the points are technically unoccupied.
     *
     * @return the blocked squares
     */
    private static Set<TileKey> blockedSquares()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (Edge edge : layout.edgesCoveredByStandingTrains().keySet())
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            TileKey from = session.getStationIndex().squareOf(edge.getStart().getName());
            TileKey to = session.getStationIndex().squareOf(edge.getEnd().getName());

            if (from == null || to == null) continue;

            for (GraphReducer.ReducedEdge reduced : session.getReducer().getEdges())
            {
                boolean sameWay = from.equals(reduced.getStart()) && to.equals(reduced.getEnd());
                boolean otherWay = to.equals(reduced.getStart()) && from.equals(reduced.getEnd());

                if (!sameWay && !otherWay) continue;

                for (GraphReducer.TileStep step : reduced.getPath())
                {
                    if (step.getTile() == null) continue;

                    if (step.getTile().equals(from) || step.getTile().equals(to)) continue;

                    out.add(step.getTile());
                }

                break;
            }
        }

        return out;
    }

    /** The train length that gave the three squares, kept so the bare picture can be undone. */
    private static int trainLength;

    /**
     * Finds a train length at which the railway blocks a square the train is not drawn on.
     *
     * Both marks have to be visible at once for any of this to be assertable, and how far each of them
     * reaches depends on the length: the line is as long as the train, the blocked extent is whole
     * edges.  A short train on a long edge separates them, and how short depends on the railway.
     *
     * @return true when all three squares were found
     */
    private static boolean chooseTheThreeSquares() throws Exception
    {
        for (int length = 1; length <= 6; length++)
        {
            train.setTrainLength(length);

            refreshCoveredTrack();

            Set<TileKey> blocked = blockedSquares();

            TileKey onTheTrain = null;
            TileKey blockedOnly = null;

            for (TileKey square : everyTile())
            {
                if (ui.isTrackCovered(square))
                {
                    if (onTheTrain == null && drawable(square)) onTheTrain = square;
                }
                else if (blocked.contains(square))
                {
                    if (blockedOnly == null && drawable(square)) blockedOnly = square;
                }
            }

            if (onTheTrain == null || blockedOnly == null) continue;

            TileKey free = null;

            for (TileKey square : everyTile())
            {
                if (ui.isTrackCovered(square) || blocked.contains(square)) continue;

                if (drawable(square))
                {
                    free = square;

                    break;
                }
            }

            if (free == null) continue;

            coveredSquare = onTheTrain;
            blockedSquare = blockedOnly;
            freeSquare = free;
            trainLength = length;

            return true;
        }

        return false;
    }

    private static boolean drawable(TileKey square)
    {
        org.traincontrol.base.LayoutDiagram page = model.getLayout(square.getPage());

        if (page == null) return false;

        LayoutDiagramComponent component = page.getComponent(square.getX(), square.getY());

        return component != null && !component.isText();
    }

    // ---------------------------------------------------------------- looking at the picture

    /**
     * Whether this colour is the orange a person would call orange.
     *
     * The same test `support.Rendered` and `testTheTrainIsShownAsALine` use, by hue rather than
     * against production's own constant: the claim is about what somebody looking at the diagram
     * sees, and a test reading the constant would agree with any value it was changed to.
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

    private static double brightness(BufferedImage image)
    {
        return support.Rendered.brightness(image);
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
     * Paints one tile, on its own, exactly as the diagram paints it.
     *
     * `paint` and not `printAll`: both of those begin with an isShowing() check and do nothing for a
     * component that is not on screen, which is every component here.
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

    /**
     * Sets the railway's own running flag, the way `executeTimetable` sets it.
     *
     * By reflection, like `testHomeStaging`: `runLocomotives` dispatches trains down Adam's real
     * railway, and what is under test is a drawing rule fenced on the flag rather than the dispatch.
     */
    private static void setRunning(boolean value) throws Exception
    {
        Field running = Layout.class.getDeclaredField("running");

        running.setAccessible(true);
        running.setBoolean(layout, value);
    }

    private static void refreshCoveredTrack() throws Exception
    {
        // AND WAITED FOR (OB-192).  The window works the marks out on a worker now, because asking
        // for them on the event thread meant waiting on the `Layout` monitor a dispatch holds - so
        // asking and reading are two moments, and `support.CoveredMarks` is the one place that knows
        // it.
        support.CoveredMarks.refresh(ui);

        pump();
    }

    private static void repaintTheThree() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            coveredTile.refreshCoveredMark();
            blockedTile.refreshCoveredMark();
            freeTile.refreshCoveredMark();
        });

        pump();
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
