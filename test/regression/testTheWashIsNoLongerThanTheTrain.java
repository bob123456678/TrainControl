package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
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
 * MT-309, second half: the wash is as long as the train, not as long as the edge.  Adam, after a Return Home run:
 * *"after the parking completed, all of bottommaina stayed shaded, which it shouldn't as I set the length of EN57-203
 * to 1."*
 *
 * Coverage is recorded per EDGE (Adam: "because the points are technically unoccupied. But the blocked edges should
 * prevent routing to the covered points"), and the tail walk stops when the train's length is used up, so a train of
 * length 1 claims one edge; the DRAWING then painted every tile of it, and an edge can be a dozen squares long.  The
 * railway still blocks the whole edge (nothing here touches `Layout.edgesCoveredByStandingTrains`); the diagram says
 * WHERE THE TRAIN IS, so the wash is a subset of what is blocked, the safe direction.
 *
 * Every tile is measured at ONE here, so a length is a number of squares: a train of length 1 covers one square.
 *
 * MUTATION: put the whole-edge loop back in `AutonomySession.tilesCoveredByStandingTrains` and
 * `testATrainOfLengthOneCoversOneSquare` fails with the number of squares in the edge behind the train.  Stop the walk
 * one tile early and `testALongerTrainCoversMoreOfIt` fails.
 *
 * @author Adam
 */
public class testTheWashIsNoLongerThanTheTrain
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;
    private static ExecutorService watchdog;

    /** The parent of the labels this test registers, so the window's own tiles and these never evict each other. */
    private static final JPanel OURS = new JPanel();

    /**
     * One unit per square, so "length" is a count of squares; almost nothing on the real railway carries a length and
     * the tail walk stops at the first unmeasured segment.
     */
    private static final int TILE_LENGTH = 1;

    private static Locomotive train;
    private static Integer trainLengthWas;

    /** Put back in teardown: a static this class turns ON leaks into every later class in the JVM. */
    private static boolean simulatingWas;
    private static Point home;

    /** Where the train came to rest after the run, and the squares the OLD rule washed behind it. */
    private static Point arrived;
    private static int wholeEdgeBehind;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The frozen copy, not the railway he is operating (OB-111, `test/layouts/live-snapshot`):
        // `cs2_sample_layout`'s placements and lengths move as Adam runs trains, and a class that skips everything
        // reads as green.  `Scenario.folderFor` cannot spell its way out to the operator's own railway, and
        // `LayoutSandbox` copies what it names.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        // showUI, because the subject is what is drawn.  debug on, so the simulated Central Station
        // echoes the accessory commands a path configuration waits for.
        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);
        simulatingWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so nothing here can reach the drawing path");

        // init returns with display() still queued.
        pump();
        pump();

        session = ui.getAutonomySession();

        assertNotNull(session, "the sandbox copy holds no autonomy setup");

        watchdog = Executors.newSingleThreadExecutor();

        SwingUtilities.invokeAndWait(() -> measureEveryTileAs(TILE_LENGTH));

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the setup did not build");

        oneTrainOnly();

        assertNotNull(train, "no locomotive is standing on this railway");

        // Short enough to go somewhere, on a railway measured at one unit a tile: the room rule is asked wherever a
        // train comes to rest, so a long train is refused everywhere and the run below never starts.  Each claim sets
        // its own length and `tearDownClass` puts the real one back.
        train.setTrainLength(1);

        home = layout.getLocomotiveLocation(train);

        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        // The production monitor, pointed at the layout this class parsed - so the recompute and the
        // redraw below are the ones a run really goes through rather than a call made by the test.
        SwingUtilities.invokeAndWait(() ->
        {
            ui.getDiagramMonitorDriver().bind(session);
            ui.getDiagramMonitorDriver().attach();
            ui.getDiagramMonitorDriver().start();
        });

        registerLabels();

        // A REAL RUN, because Adam's report is about what is on the screen after one.
        arrived = runToTheNextStation();

        assertNotNull(arrived, "the train never left " + home.getName());

        settle();

        wholeEdgeBehind = squaresOfTheEdgeBehindTheTrain();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            // The global first: a throw in a later statement would leave mock packets echoing for every later class.
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = simulatingWas;

            if (layout != null) layout.stopLocomotives();

            // PUT BACK: init() opens Adam's own locomotive database, and a length this test chose is
            // not a measurement of his train.
            if (train != null) train.setTrainLength(trainLengthWas == null ? 0 : trainLengthWas);
        }
        finally
        {
            if (watchdog != null) watchdog.shutdownNow();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The defect, in Adam's own words: a train one square long shades one square, the one it stands on.  Every tile
     * measures one, so a one-unit train fits on its own square and nothing behind it is washed.  Adam (OB-278):
     * *"the 2 length tile with the s88 consumes 2 units of the train"*.
     */
    @Test
    public void testATrainOfLengthOneCoversOneSquare() throws Exception
    {
        assertTrue(wholeEdgeBehind > 1,
            "the edge behind " + arrived.getName() + " is " + wholeEdgeBehind + " square(s) long, so "
            + "the whole-edge answer and the one-square answer are the same number and this test "
            + "cannot tell them apart");

        washWith(1);

        Set<TileKey> washed = washedBehindTheTrain();

        assertTrue(washed.isEmpty(),
            "the train is one square long, stands on a square measuring one, and " + washed.size()
            + " squares behind it are shaded: " + washed + ". Adam: \"all of bottommaina stayed shaded"
            + " ... as I set the length of EN57-203 to 1\" - and the square it stands on consumes its one"
            + " unit (OB-278)");

        TileKey standingOn = session.getStationIndex().squareOf(arrived.getName());

        assertTrue(ui.isTrackCovered(standingOn),
            "a one-unit train shades nothing at all - not even the square it stands on - so the claim above"
            + " would pass equally well with the wash switched off altogether");
    }

    /** CONTROL: a longer train shades more of it; otherwise the fix could be "shade one square, whatever is there". */
    @Test(dependsOnMethods = "testATrainOfLengthOneCoversOneSquare")
    public void testALongerTrainCoversMoreOfIt() throws Exception
    {
        // ITS OWN SQUARE AND THE REST BEHIND IT: a train n squares long lies on the one it stands on and n - 1
        // behind (OB-278).
        int longer = Math.min(3, wholeEdgeBehind + 1);

        assertTrue(longer > 2, "the edge behind the train is too short to lengthen the train into");

        washWith(longer);

        Set<TileKey> washed = washedBehindTheTrain();

        assertEquals(washed.size(), longer - 1,
            "a train " + longer + " squares long, on a square measuring one, shades " + washed.size()
            + " squares behind it: " + washed + ". The wash has to follow the length rather than being one"
            + " square whatever is there");
    }

    /**
     * CONTROL: the picture never claims track the railway has not blocked; the wash is a subset of the covered edges.
     */
    @Test(dependsOnMethods = "testALongerTrainCoversMoreOfIt")
    public void testTheWashNeverClaimsMoreThanTheRailwayBlocks() throws Exception
    {
        washWith(2);

        Set<TileKey> washed = washedBehindTheTrain();

        assertFalse(washed.isEmpty(), "nothing is shaded, so the containment below is vacuous");

        assertTrue(everySquareTheRailwayHoldsCovered().containsAll(washed),
            "the diagram is shading track the railway does not hold covered: " + washed
            + " against " + everySquareTheRailwayHoldsCovered());
    }

    /**
     * The square the train stands on is drawn with the orange line (OB-277).  Adam: *"when we draw orange lines, they
     * don't overlap with sensors"*, on every sensor, occupied or not.
     */
    @Test
    public void testTheSquareTheTrainStandsOnIsOrange() throws Exception
    {
        washWith(1);

        TileKey standingOn = session.getStationIndex().squareOf(arrived.getName());

        assertTrue(ui.isTrackCovered(standingOn),
            "the train stands on " + standingOn + " and the orange line leaves that square out (OB-277)");

        assertFalse(ui.coveredRoutesAt(standingOn).isEmpty(),
            "the standing square is covered but no road of it is named, so the tile has nothing to draw the line along");

        for (LayoutLabel label : labelsFor(java.util.Collections.singleton(standingOn)))
        {
            assertTrue(support.Rendered.showsTheTrainMark(label),
                "the standing square is in the covered set and is still drawn without the orange line");
        }
    }

    /**
     * A sensor the train's tail lies across is drawn with the orange line, from the length that reaches it and not
     * before (OB-277).  The length is found: the shortest train whose line reaches a sensor square behind the one it
     * stands on.  The CONTROL is the same train one unit shorter, without which a walk marking every sensor of every
     * covered edge would pass.
     */
    @Test
    public void testASensorTheTailLiesAcrossIsOrange() throws Exception
    {
        TileKey standingOn = session.getStationIndex().squareOf(arrived.getName());

        java.util.Set<TileKey> sensors = session.getReducer().getPoints().keySet();

        TileKey reached = null;
        int length = 0;

        for (int l = 1; l <= 40 && reached == null; l++)
        {
            washWith(l);

            for (TileKey square : washedBehindTheTrain())
            {
                if (sensors.contains(square) && !square.equals(standingOn))
                {
                    reached = square;
                    length = l;

                    break;
                }
            }
        }

        assertNotNull(reached, "no train up to 40 units long reaches a sensor behind " + standingOn
            + " with the orange line, so the tail's sensor squares are never drawn (OB-277)");

        assertFalse(ui.coveredRoutesAt(reached).isEmpty(),
            "the sensor " + reached + " is covered but no road of it is named, so nothing is drawn on it");

        for (LayoutLabel label : labelsFor(java.util.Collections.singleton(reached)))
        {
            assertTrue(support.Rendered.showsTheTrainMark(label),
                "the sensor " + reached + " is in the covered set and is drawn without the orange line");
        }

        washWith(length - 1);

        assertFalse(ui.isTrackCovered(reached),
            "a train one unit too short to reach " + reached + " is drawn across it anyway - the line follows the"
            + " covered edges rather than the train's length");
    }

    /**
     * A path locked ahead of the train does not move the wash (MT-438).  `AutonomySession.walkBackFrom` anchored at
     * `Layout.getLocomotiveLocation`, the FIRST Point in `HashMap` order that holds the locomotive, while a locked path
     * reserves every Point on it, so the anchor could be the destination the train has not reached.  Adam: *"why is the
     * tail from bottommainapre to bottommaina not orange.  it's almost as if you are shifting the location of the
     * train"*.  `Layout.whereTheTrainIs` is now the one rule for this walk, `DiagramMonitor` and
     * `AutoLocomotiveStatus`.
     *
     * The arrangement is sought, not hoped for: the test tries candidate destinations until the anchor moves off the
     * square the train stands on, and fails if none can.  A LOCK rather than a run, because a lock returns with the
     * train still standing.  `unlockPath` keeps the ARRIVAL of the path it is given, so the placement AND the arrival
     * record are captured before the lock and written back, and the last thing this does is prove the wash is back.
     *
     * MUTATION: anchor `walkBackFrom` at `getLocomotiveLocation` again and this goes red: the wash moves onto the
     * locked path ahead, which `edgesCoveredByStandingTrains` does not hold covered.
     */
    @Test(dependsOnMethods = "testALongerTrainCoversMoreOfIt")
    public void testAPathLockedAheadDoesNotMoveTheWash() throws Exception
    {
        washWith(2);

        Point standing = layout.getLocomotiveLocation(train);

        assertNotNull(standing, "precondition: nothing holds the train, so there is no anchor to move");

        Set<TileKey> before = washedBehindTheTrain();

        assertFalse(before.isEmpty(),
            "precondition: nothing is shaded before the lock, so this claim could not see it move");

        // WHAT THE LOCK MUST NOT COST, captured first.
        String sideWas = standing.getArrivedFrom();
        List<Edge> roadWas = standing.getArrivedAlong();

        // Which path would have moved the old anchor, worked out rather than tried: locking a candidate and unlocking
        // again took the train off its square (`unlockPath` sweeps the locomotive off every Point but the path's
        // arrival).  The old anchor was the first Point in the graph's iteration order that holds the locomotive, so a
        // path moves it exactly when one of its edges ends at an earlier Point; the lock below happens once.
        List<Point> inOrder = new ArrayList<>(layout.getPoints());

        int standingAt = inOrder.indexOf(standing);

        assertTrue(standingAt >= 0, "the square the train stands on is not in the graph's own point list");

        List<Edge> locked = null;

        for (Point to : inOrder)
        {
            if (to.isSamePlaceAs(standing)) continue;

            List<Edge> candidate;

            try
            {
                candidate = layout.bfs(standing, to, null);
            }
            catch (Exception noRoad)
            {
                continue;
            }

            if (candidate == null || candidate.isEmpty()) continue;

            boolean wouldMoveIt = false;

            for (Edge leg : candidate)
            {
                int reserved = inOrder.indexOf(leg.getEnd());

                if (reserved >= 0 && reserved < standingAt) wouldMoveIt = true;
            }

            if (!wouldMoveIt) continue;

                if (!layout.configureAndLockPath(candidate, train)) continue;

            locked = candidate;

            break;
        }

        

        if (locked == null)
        {
            throw new AssertionError("no path from " + standing.getName() + " reserves a Point that comes"
                + " before it in the graph's own iteration order, so the anchor this is about cannot be"
                + " made to move on this railway - the claim would pass without asking anything");
        }

        boolean putBack = false;

        try
        {
            // Measured, not derived, and inside the block that puts the railway back: the loop above predicts by the
            // graph's point order, sound only while `getPoints()` and `getLocomotiveLocation` walk the same collection
            // the same way.  By place, not by object: a square is several Points, and a first leg onto the other copy
            // of the train's square reserves a different Point on the same track, which is not the anchor moving.
            // Null-safe, because this is read when something has gone wrong and an NPE would replace the diagnosis.
            Point held = layout.getLocomotiveLocation(train);

            assertNotNull(held, "the lock left no Point holding the train at all, which is a worse"
                + " failure than the one this claim is about: the record the wash is computed from"
                + " has been swept by a path being reserved");

            assertFalse(held.isSamePlaceAs(standing),
                "the lock reserved no Point on another square, so the anchor this claim is about did"
                + " not move and the claim would pass without asking anything.  The prediction above"
                + " and `getLocomotiveLocation` have stopped agreeing about the graph's order");

            washWith(2);

            Set<TileKey> washed = washedBehindTheTrain();

            Point anchoredAt = layout.whereTheTrainIs(train);

            String anchor = anchoredAt == null ? "nothing at all" : anchoredAt.getName();

            assertFalse(washed.isEmpty(),
                "with a path locked ahead of it the train has no wash at all: the walk anchored"
                + " somewhere the covered track cannot be reached from, so the operator loses the one"
                + " mark that says which track is blocked.  covered=" + layout.edgesCoveredByStandingTrains().keySet() + " side=" + standing.getArrivedFrom() + " road=" + standing.getArrivedAlong() + " milestones=" + layout.getReachedMilestones(train) + "  Anchor: "
                + anchor + ", standing at " + standing.getName());

            assertTrue(everySquareTheRailwayHoldsCovered().containsAll(washed),
                "with a path locked ahead of it, the diagram shades track the railway does not hold"
                + " covered: " + washed + " against " + everySquareTheRailwayHoldsCovered()
                + ".  The train has not moved - the lock only reserved the road - so the wash cannot"
                + " have moved either.  Anchor: " + anchor
                + ", standing at " + standing.getName() + " (VD12-B1)");

            assertEquals(washed, before,
                "the wash moved when a path was locked ahead of the train.  Before: " + before
                + ", after: " + washed + ".  The lock reserves the road; it does not move the train,"
                + " and the picture has to follow the train");
        }
        finally
        {
            if (locked != null) layout.unlockPath(locked, train);

            // Back where it was, with the record the wash is computed from: `unlockPath` keeps the path's arrival and
            // `setLocomotive` clears the arrival side on a change of occupant, so both halves are written back in this
            // order.  Nothing is asserted in here, which would replace the body's failure with its own.
            boolean back = layout.moveLocomotive(train.getName(), standing.getName(), false);

            standing.setArrivedFrom(sideWas);
            standing.setArrivedAlong(roadWas);

            washWith(2);

            putBack = back && before.equals(washedBehindTheTrain());
        }

        assertTrue(putBack,
            "this claim has not put the railway back: the train is at " + describeLocation() + " and the"
            + " wash is " + washedBehindTheTrain() + " against " + before + " before the lock.  Every"
            + " claim after it in this class would be measuring the leftovers of this one");
    }

    /** Where the train stands, for the restore's own failure message. */
    private static String describeLocation()
    {
        Point at = layout.getLocomotiveLocation(train);

        return at == null ? "(nowhere)" : at.getName();
    }

    // ---------------------------------------------------------------- the railway

    /**
     * Sets the train's length and lets the window catch up with it: the layout is told through the callback a train
     * movement fires.
     */
    private static void washWith(int squares) throws Exception
    {
        train.setTrainLength(squares);

        // The recompute the window does on every refresh, asked directly: a length is not a movement, so nothing fires
        // the monitor's callback.  And waited for (OB-192): the window works the marks out on a worker, so asking and
        // reading are two moments, and `support.CoveredMarks` knows it.
        support.CoveredMarks.refresh(ui);

        pump();
    }

    /**
     * Every square the window draws the train on BEHIND it: all but the one it stands on (OB-277 put the orange there).
     */
    private static Set<TileKey> washedBehindTheTrain()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        TileKey standingOn = session.getStationIndex().squareOf(arrived.getName());

        for (TileKey tile : everyTile())
        {
            if (tile.equals(standingOn)) continue;

            if (ui.isTrackCovered(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * Every square the train can lie on while the railway holds these edges covered: their tiles and the sensor squares
     * at their ends.
     */
    private static Set<TileKey> everySquareTheRailwayHoldsCovered()
    {
        Set<TileKey> out = everyTileOfTheCoveredEdges();

        for (Edge covered : layout.edgesCoveredByStandingTrains().keySet())
        {
            if (covered.getStart() == null || covered.getEnd() == null) continue;

            TileKey from = session.getStationIndex().squareOf(covered.getStart().getName());
            TileKey to = session.getStationIndex().squareOf(covered.getEnd().getName());

            if (from != null) out.add(from);
            if (to != null) out.add(to);
        }

        return out;
    }

    /** Every tile of every edge the RAILWAY holds covered - the old, whole-edge answer. */
    private static Set<TileKey> everyTileOfTheCoveredEdges()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (Edge covered : layout.edgesCoveredByStandingTrains().keySet())
        {
            if (covered.getStart() == null || covered.getEnd() == null) continue;

            TileKey from = session.getStationIndex().squareOf(covered.getStart().getName());
            TileKey to = session.getStationIndex().squareOf(covered.getEnd().getName());

            if (from == null || to == null) continue;

            for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
            {
                boolean sameWay = from.equals(edge.getStart()) && to.equals(edge.getEnd());
                boolean otherWay = to.equals(edge.getStart()) && from.equals(edge.getEnd());

                if (!sameWay && !otherWay) continue;

                for (GraphReducer.TileStep step : edge.getPath())
                {
                    if (step.getTile() == null) continue;

                    if (step.getTile().equals(from) || step.getTile().equals(to)) continue;

                    out.add(step.getTile());
                }
            }
        }

        return out;
    }

    /** How many squares the edge the train arrived along has, taken with the train long enough to claim all of it. */
    private static int squaresOfTheEdgeBehindTheTrain() throws Exception
    {
        train.setTrainLength(500);

        return everyTileOfTheCoveredEdges().size();
    }

    private static Point runToTheNextStation() throws Exception
    {
        for (Point candidate : layout.getPoints())
        {
            if (candidate == home || !candidate.isDestination()) continue;

            // Neither turns the train round on arrival, which would put its tail on the other side of
            // it - a different covered set for an honest reason.
            if (candidate.isTerminus() || candidate.isReversing()) continue;

            List<Edge> out = layout.bfs(home, candidate, null);

            if (out == null || out.isEmpty()) continue;

            if (!layout.isPathClear(out, train, false)) continue;

            if (!runLeg(out)) continue;

            return layout.getLocomotiveLocation(train);
        }

        return null;
    }

    private static boolean runLeg(final List<Edge> path) throws Exception
    {
        Future<Boolean> leg = watchdog.submit(new Callable<Boolean>()
        {
            @Override
            public Boolean call() throws Exception
            {
                return layout.executePath(path, train, 30, null);
            }
        });

        try
        {
            return leg.get(90, TimeUnit.SECONDS);
        }
        catch (java.util.concurrent.TimeoutException wedged)
        {
            layout.stopLocomotives();

            fail("a leg never arrived, so this run would have hung rather than failed");

            return false;
        }
    }

    // ---------------------------------------------------------------- the diagram

    private static Set<TileKey> everyTile()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey tile : session.getReducer().getPoints().keySet()) out.add(tile);

        for (GraphReducer.ReducedEdge edge : session.getReducer().getEdges())
        {
            for (GraphReducer.TileStep step : edge.getPath())
            {
                if (step.getTile() != null) out.add(step.getTile());
            }
        }

        return out;
    }

    /** Puts a real tile on every square of the railway, so there is something for the redraw to reach. */
    private static void registerLabels() throws Exception
    {
        for (TileKey square : everyTile())
        {
            org.traincontrol.base.LayoutDiagram page = model.getLayout(square.getPage());

            if (page == null) continue;

            LayoutDiagramComponent component = page.getComponent(square.getX(), square.getY());

            // A caption is not track and refuses the wash on purpose.
            if (component == null || component.isText()) continue;

            LayoutLabel label = new LayoutLabel(component, OURS, 30, ui, false);

            label.setSquare(square);

            ui.getDiagramTileRegistry().register(square, label);
        }

        pump();
    }

    /** The tiles this test registered for these squares, drawn and ready to be asked what they show. */
    private static List<LayoutLabel> labelsFor(Set<TileKey> squares) throws Exception
    {
        List<LayoutLabel> out = new ArrayList<>();

        long until = System.currentTimeMillis() + 30000;

        for (TileKey square : squares)
        {
            for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(square))
            {
                while (bareIconOf(label) == null && System.currentTimeMillis() < until)
                {
                    pump();
                }

                if (bareIconOf(label) == null) continue;

                label.refreshCoveredMark();

                out.add(label);
            }
        }

        pump();

        assertFalse(out.isEmpty(),
            "none of " + squares + " has a drawn tile, so there is nothing an operator could have "
            + "seen and nothing to assert about");

        return out;
    }

    /**
     * Whether this tile is drawing the mark that says a train is lying across it: painted and looked at, as the mark is
     * a line over the icon.
     */
    private static boolean isWashed(LayoutLabel label) throws Exception
    {
        return support.Rendered.showsTheTrainMark(label);
    }

    private static Icon bareIconOf(LayoutLabel label) throws Exception
    {
        java.lang.reflect.Field lastIcon = LayoutLabel.class.getDeclaredField("lastIcon");

        lastIcon.setAccessible(true);

        return (Icon) lastIcon.get(label);
    }

    /** Lets the production monitor tick and the event thread catch up. */
    private static void settle() throws Exception
    {
        long until = System.currentTimeMillis() + 2000;

        while (System.currentTimeMillis() < until)
        {
            Thread.sleep(50);

            pump();
        }
    }

    private static void pump() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> { });
    }

    // ---------------------------------------------------------------- the fixture

    /** Leaves one train on the railway, so a square that stops being shaded stopped for a reason this test caused. */
    private static void oneTrainOnly()
    {
        Point keep = null;

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() == null) continue;

            if (keep == null && point.getArrivedFrom() != null) keep = point;
        }

        if (keep == null) return;

        for (Point point : layout.getPoints())
        {
            if (point == keep || point.getCurrentLocomotive() == null) continue;

            layout.moveLocomotive(null, point.getName(), true);
        }

        train = keep.getCurrentLocomotive();

        trainLengthWas = train.getTrainLength();
    }

    /**
     * Measures every tile, because the tail walk stops at the first unmeasured segment; written after collecting, as
     * setTileLength rebuilds the reducer.
     */
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
}
