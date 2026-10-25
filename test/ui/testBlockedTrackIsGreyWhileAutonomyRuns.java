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
 * MT-309, third ruling: the drawing says "track is blocked" again, and says it in gray (Adam: *"'train is here' should
 * also mean 'track is blocked' - that is the whole point."*). The orange line as long as the train and the gray on the
 * tiles stand together, so the drawing and the guard agree: orange on a square (a train lies there), gray and no orange
 * (track a train's presence has made unusable), neither (free).
 *
 * The gray is not bounded to a run, as the refusal it draws never was; `ui.testTheGreyAppearsAtIdleToo` carries the
 * idle half. The blocked extent is worked out here from the railway, not asked of the window under test: what is drawn
 * must be what `Layout.edgesCoveredByStandingTrains` refuses, endpoint squares excluded. Asserted in pixels, because
 * whether a square is grayed is decided when the tile is PAINTED (OB-180, `support.Rendered`).
 *
 * MUTATION: fence the wash on `isAutonomyBusy()` again and `testAStoppedRailwayShowsTheSameGrey` fails. Draw it only
 * where the line is drawn and `testBlockedTrackIsGreyedWhileAutonomyRuns` fails. Draw the wash OVER the line and
 * `testASquareWithATrainOnItIsStillOrange` fails. Wash the whole page and `testFreeTrackIsNotTouched` fails.
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

    /** The two arrow settings as the run found them, null where unset - put back by the teardown. */
    private static String restrictionsWere;
    private static String allowedWere;

    /** Why the set-up found no fixture, or null; each claim asks it, so it fails rather than skips. */
    private static String unbuilt;

    /** Fails the claim when the set-up found no fixture: a skip or an assert in `setUpClass` reads as skipped tests. */
    private static void assertBuilt()
    {
        if (unbuilt != null) throw new AssertionError(unbuilt);
    }

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("painting a tile needs a display");
        }

        // The diagram's arrows as they are by default, not as the operator last left them: a run's preferences start as
        // a copy of his, and since MT-675 the green arrows can be drawn on every open square, on top of the fade this
        // class measures. Put back by the teardown.
        java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

        restrictionsWere = prefs.get(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, null);
        allowedWere = prefs.get(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, null);

        prefs.putBoolean(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, true);
        prefs.putBoolean(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, false);

        // The frozen copy, not the railway he is operating (OB-111, and `test/layouts/live-snapshot`):
        // `cs2_sample_layout`'s placements and lengths move as Adam runs trains on it, and a class that skips
        // everything reads as a green one. `Scenario.folderFor` is the only naming of a fixture folder that cannot
        // spell its way out to the operator's own railway.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        pump();
        pump();

        session = ui.getAutonomySession();

        if (session == null)
        {
            unbuilt = "no autonomy setup on this railway";
            return;
        }

        SwingUtilities.invokeAndWait(() -> measureEveryTileAs(1));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        oneTrainThatCoversSomething();

        if (train == null)
        {
            unbuilt = "no standing train covers any track here";
            return;
        }

        if (!chooseTheThreeSquares())
        {
            unbuilt =
                "no train on this railway blocks a square it is not drawn on, so the two marks "
                + "cannot be told apart here";
            return;
        }

        coveredTile = drawnTileFor(coveredSquare);
        blockedTile = drawnTileFor(blockedSquare);
        freeTile = drawnTileFor(freeSquare);

        // STOPPED, with the train exactly where it is. Both marks are drawn.
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

        // And running with nothing standing anywhere, the reference every "is this square marked at all" question is
        // asked against. A length of zero is how this railway says "not set", and an unmeasured train covers nothing.
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

            java.util.prefs.Preferences prefs = TrainControlUI.getPrefs();

            if (restrictionsWere == null) prefs.remove(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS);
            else prefs.put(TrainControlUI.DIAGRAM_RESTRICTION_ARROWS, restrictionsWere);

            if (allowedWere == null) prefs.remove(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS);
            else prefs.put(TrainControlUI.DIAGRAM_ALLOWED_DIRECTIONS, allowedWere);
        }
    }

    // ---------------------------------------------------------------- the four claims

    /** A square nothing is drawn on, but which the railway refuses, is grayed while autonomy runs. */
    @Test
    public void testBlockedTrackIsGreyedWhileAutonomyRuns()
    {
        assertBuilt();

        // The orange is expected here (MT-373): the square is the one the train stands on, since the wash was narrowed
        // to the train. The claim that matters is that the tile is drawn FAINTER when the railway refuses it.
        assertTrue(orangePixels(runningBlocked) > 0,
            "the orange line is not drawn on " + blockedSquare + ", so the train is not shown where"
            + " this class thinks it is and the fade below is about some other square");

        assertTrue(contrast(runningBlocked) < contrast(bareBlocked) - 2,
            "the square " + blockedSquare + " is drawn no fainter while a train blocks it ("
            + contrast(runningBlocked) + ") than with nothing standing anywhere ("
            + contrast(bareBlocked) + "), so nothing says the track is unusable. Adam: \"'train is "
            + "here' should also mean 'track is blocked' - that is the whole point\"");
    }

    /** And it is gray rather than another line: the whole square is faded, not a stroke drawn across it. */
    @Test(dependsOnMethods = "testBlockedTrackIsGreyedWhileAutonomyRuns")
    public void testTheBlockedMarkIsTheWholeSquareAndNotAStrokeAcrossIt()
    {
        assertBuilt();

        int changed = differingPixels(runningBlocked, bareBlocked);

        int drawn = drawnPixels(bareBlocked);

        // A real fraction of the square, not one pixel: `drawn > 0` admits a tile with eight drawn pixels, and the
        // claim is meant to tell a fade of the whole tile from a stroke across part of it.
        assertTrue(drawn > TILE * TILE / 10,
            "almost nothing is drawn on " + blockedSquare + " when it is free, so there is nothing "
            + "for the fade to move and this claim would pass on a blank square");

        assertTrue(changed >= drawn * 9 / 10,
            "only " + changed + " of the " + drawn + " drawn pixels on the square change when it "
            + "becomes blocked, so the mark is a stroke across part of it rather than the tile "
            + "itself. Adam: \"can we just grey out the tiles just like blocked edges\", and later "
            + "\"the same tile with more transparency\"");
    }

    /**
     * And a stopped railway shows exactly the same gray: the mark is NOT bounded to a run. `Layout.isPathClear` sweeps
     * the covered edges in every tier at every time, because a tail lying across the rail is physical, so at idle a
     * manual send across a parked train's tail was refused over track this drew as free. Adam: *"this greyout should
     * appear at idle and be regenerated if a placement or train/track length is changed."*
     * `ui.testTheGreyAppearsAtIdleToo` carries the regeneration.
     */
    @Test
    public void testAStoppedRailwayShowsTheSameGrey()
    {
        assertBuilt();

        assertTrue(support.Rendered.same(stoppedBlocked, runningBlocked),
            "the square " + blockedSquare + " is drawn differently with autonomy stopped than with it "
            + "running, though the train has not moved and the railway refuses the same track either "
            + "way.  The grey is fenced on a running railway again, and at idle the operator is "
            + "offered a destination the send will refuse (W7B-B1).  Adam: \"yes, this greyout should "
            + "appear at idle\"");

        assertTrue(contrast(stoppedBlocked) < contrast(bareBlocked) - 2,
            "the square " + blockedSquare + " is drawn no fainter with a train blocking it and "
            + "autonomy STOPPED (" + contrast(stoppedBlocked) + ") than with no train at all ("
            + contrast(bareBlocked) + "), so nothing on a stopped railway says the track is "
            + "unusable");
    }

    /** The orange line is untouched by any of it - drawn stopped, drawn running, and the same line. */
    @Test
    public void testTheOrangeLineIsDrawnEitherWay()
    {
        assertBuilt();

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

    /** A square that is both - the train is on it AND it is blocked - reads as both. */
    @Test(dependsOnMethods = "testTheOrangeLineIsDrawnEitherWay")
    public void testASquareWithATrainOnItIsStillOrange()
    {
        assertBuilt();

        // Against the bare picture: the gray is no longer fenced on a running railway, so "stopped" and "running" are
        // identical by design, and the square with nothing standing anywhere is the picture that has no wash on it.
        assertTrue(contrast(runningCovered) < contrast(bareCovered) - 2,
            "the square the train stands on is drawn no fainter while a train covers it ("
            + contrast(runningCovered) + ") than with nothing standing anywhere ("
            + contrast(bareCovered) + "), so the square carrying the train is the one square "
            + "that does not say it is blocked");

        assertTrue(orangePixels(runningCovered) > 0,
            "the train on " + coveredSquare + " cannot be seen through the wash laid over it");
    }

    /**
     * CONTROL: track no train blocks is left alone, running or not. Without it every assertion above is satisfied by
     * graying the whole page while autonomy runs.
     */
    @Test
    public void testFreeTrackIsNotTouched()
    {
        assertBuilt();

        assertTrue(support.Rendered.same(runningFree, bareFree),
            "the square " + freeSquare + ", which no train covers or blocks, is drawn differently "
            + "while a train stands elsewhere - the wash is being laid over track that is free");

        assertTrue(support.Rendered.same(stoppedFree, bareFree),
            "the square " + freeSquare + " changes with autonomy stopped as well");
    }

    // ---------------------------------------------------------------- the railway's own answer

    /**
     * The squares the railway holds blocked, worked out from the covered EDGES, not asked of the window (which would
     * agree with itself whatever it says). This is the translation `AutonomySession` performs, with the endpoints
     * excluded because a train at a sensor is shown standing there.
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
     * Finds a train length at which the railway blocks a square the train is not drawn on: both marks must be visible
     * at once, and the line is as long as the train while the blocked extent is whole edges, so a short train on a long
     * edge separates them.
     */
    private static boolean chooseTheThreeSquares() throws Exception
    {
        for (int length = 1; length <= 6; length++)
        {
            train.setTrainLength(length);

            refreshCoveredTrack();

            Set<TileKey> blocked = blockedSquares();

            TileKey onTheTrain = null;

            for (TileKey square : everyTile())
            {
                if (ui.isTrackCovered(square) && drawable(square))
                {
                    onTheTrain = square;

                    break;
                }
            }

            // A square that is gray and not orange no longer exists for a standing train, by Adam's ruling (MT-373:
            // *"grey out the blocked path only if on a double curve.  otherwise, greyed out means either edge locked or
            // path blocked."*). The gray's own meaning is EDGE LOCKED, a square an active route is holding with no
            // train on it: that is `TileOverlay`'s wash, tested in `ui.testTheWashDoesNotStack`, and needs a running
            // route this class does not have. The square this class claims is the one the train is on.
            TileKey blockedOnly = onTheTrain;

            if (onTheTrain == null) continue;

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
     * Whether this color is the orange a person would call orange: by hue, as `support.Rendered` and
     * `testTheTrainIsShownAsALine` do, not against production's own constant, which would agree with any value it was
     * changed to.
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


    /**
     * How many pixels of this tile carry anything but its own background, the commonest color on the square (the panel
     * showing through). A wash paints every pixel and a fade moves only those that were not background, so "the whole
     * tile is marked" is counted against the DRAWN pixels.
     */
    private static int drawnPixels(java.awt.image.BufferedImage image)
    {
        java.util.Map<Integer, Integer> seen = new java.util.HashMap<>();

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                int rgb = image.getRGB(x, y);

                seen.put(rgb, seen.containsKey(rgb) ? seen.get(rgb) + 1 : 1);
            }
        }

        int background = 0;

        for (java.util.Map.Entry<Integer, Integer> each : seen.entrySet())
        {
            if (each.getValue() > background) background = each.getValue();
        }

        return image.getWidth() * image.getHeight() - background;
    }

    /** Brightest minus darkest pixel on the tile: its own contrast, independent of the theme. */
    private static int contrast(java.awt.image.BufferedImage image)
    {
        int darkest = 255;
        int lightest = 0;

        for (int x = 0; x < image.getWidth(); x++)
        {
            for (int y = 0; y < image.getHeight(); y++)
            {
                int rgb = image.getRGB(x, y);

                int shade = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;

                darkest = Math.min(darkest, shade);
                lightest = Math.max(lightest, shade);
            }
        }

        return lightest - darkest;
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
     * Paints one tile, on its own, exactly as the diagram paints it: `paint` and not `printAll`, which do nothing for a
     * component that is not on screen.
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
     * Sets the railway's own running flag, the way `executeTimetable` sets it, by reflection: `runLocomotives`
     * dispatches trains down Adam's real railway, and what is under test is a drawing rule fenced on the flag.
     */
    private static void setRunning(boolean value) throws Exception
    {
        Field running = Layout.class.getDeclaredField("running");

        running.setAccessible(true);
        running.setBoolean(layout, value);
    }

    private static void refreshCoveredTrack() throws Exception
    {
        // And waited for (OB-192): the window works the marks out on a worker, so asking and reading are two moments,
        // and `support.CoveredMarks` is the one place that knows it.
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

    /** A real tile for a square, drawn and ready to be painted. */
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
     * Leaves one train standing, and only one that actually covers track: a train whose recorded arrival side leads
     * nowhere covers nothing.
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
