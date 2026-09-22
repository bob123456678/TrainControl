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
 * MT-309, second half - the wash is as long as the train, not as long as the edge.
 *
 * Adam, 2026-09-08, after a Return Home run: *"after the parking completed, all of bottommaina stayed
 * shaded, which it shouldn't as I set the length of EN57-203 to 1."*
 *
 * **The covered set was recomputed, and it was recomputed to the wrong answer.** Measured on his own
 * railway on 2026-09-08: with every tile measured at 10 and the train's length set to 1, the window
 * shaded SEVENTEEN squares, and `tilesCoveredByStandingTrains` agreed with it - so nothing was stale
 * and nothing had failed to repaint.
 *
 * Coverage is recorded per EDGE, which is Adam's own ruling for the RAILWAY - "because the points are
 * technically unoccupied. But the blocked edges should prevent routing to the covered points" - and
 * the tail walk stops as soon as the train's length is used up, so a train of length 1 claims exactly
 * one edge. What the DRAWING then did was paint every tile of that edge. An edge is one hop between
 * two sensors and can be a dozen squares long; its `length` is the sum of the lengths assigned to
 * those squares, and on a railway where one square in the run carries a 1 the whole run was washed for
 * a train one square long.
 *
 * **So the picture and the railway are answering different questions, and only now do they say so.**
 * The railway blocks the whole edge, and still does - nothing here touches
 * `Layout.edgesCoveredByStandingTrains` or any guard that reads it. The diagram says WHERE THE TRAIN
 * IS, which is what Adam asked the indicator to mean: *"we need to draw a line ... to show that the
 * train is there."* The wash is a subset of what is blocked by construction, which is the direction
 * that costs nothing - a picture that claimed MORE than the railway is the dangerous way round.
 *
 * Every tile is measured at ONE here, so a length is a number of squares and the assertion is the
 * sentence Adam wrote: a train of length 1 covers one square.
 *
 * MUTATION: put the whole-edge loop back in `AutonomySession.tilesCoveredByStandingTrains` and
 * `testATrainOfLengthOneCoversOneSquare` fails with the number of squares in the edge behind the
 * train. Stop the walk one tile early and `testALongerTrainCoversMoreOfIt` fails.
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

    /**
     * The parent of the labels this test registers, so the window's own tiles and these can never
     * evict each other - see DiagramTileRegistry.register.
     */
    private static final JPanel OURS = new JPanel();

    /**
     * One unit per square, so "length" is a count of squares and the assertions below are Adam's own
     * sentence rather than an arithmetic about units.  Almost nothing on the real railway carries a
     * length, and the tail walk stops at the first unmeasured segment - so without this every set
     * here would be empty and every assertion would be about nothing.
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
        // The operator's own railway, COPIED (OB-111): the wash is a product of the diagram, so a
        // hand-built graph has no tiles for it to be drawn on.
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

        // showUI, because the subject is what is drawn.  debug on, so the simulated Central Station
        // echoes the accessory commands a path configuration waits for.
        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);
        simulatingWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so nothing here can reach the drawing path");

        // init returns with display() still queued - see testTheShadingFollowsTheTrain, which died of
        // two AutonomySession rebuilds at once the first time it was run.
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

        // SHORT ENOUGH TO GO SOMEWHERE, on a railway measured at one unit a tile.
        //
        // The length was whatever Adam's database says, and the room rule is asked wherever a train
        // comes to rest - its destination and any square it turns at (behaviour.md 5a) - so a long train
        // on a railway this tightly measured is refused everywhere, the one real run below never starts,
        // and every claim after it skips.  A class that skips everything reads as a green one.
        //
        // This is the class's to set: each claim sets its own length, and `tearDownClass` puts the
        // real one back.
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
            // THE GLOBAL FIRST (VD13-T5).  It was restored third, after two statements that can throw -
            // and a throw there leaves mock packets echoing for every later class in the JVM, which is
            // the leak this restore exists to prevent.
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
     * The defect, in Adam's own words: a train one square long shades one square.
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

        assertEquals(washed.size(), 1,
            "the train is one square long and " + washed.size() + " squares behind it are shaded: "
            + washed + ". The whole of the edge it arrived along is washed however short the train is,"
            + " which is Adam's \"all of bottommaina stayed shaded ... as I set the length of"
            + " EN57-203 to 1\"");

        for (LayoutLabel label : labelsFor(washed))
        {
            assertTrue(isWashed(label),
                "the one square the railway says is covered is drawn without the wash, so this test "
                + "would pass equally well with the wash switched off altogether");
        }
    }

    /**
     * CONTROL - and a longer train shades more of it.
     *
     * Without this the fix could be "shade one square, whatever is standing there", which satisfies
     * the test above and is not what the wash is for.
     */
    @Test(dependsOnMethods = "testATrainOfLengthOneCoversOneSquare")
    public void testALongerTrainCoversMoreOfIt() throws Exception
    {
        int longer = Math.min(3, wholeEdgeBehind);

        assertTrue(longer > 1, "the edge behind the train is too short to lengthen the train into");

        washWith(longer);

        Set<TileKey> washed = washedBehindTheTrain();

        assertEquals(washed.size(), longer,
            "a train " + longer + " squares long shades " + washed.size() + " squares: " + washed
            + ". The wash has to follow the length rather than being one square whatever is there");
    }

    /**
     * CONTROL - the picture never claims track the railway has not blocked.
     *
     * The wash is now a SUBSET of the covered edges rather than all of them, and a subset is the safe
     * direction: a diagram showing more blocked track than the railway holds is the failure mode
     * `Layout.edgesCoveredByStandingTrains` was widened for in the first place.
     */
    @Test(dependsOnMethods = "testALongerTrainCoversMoreOfIt")
    public void testTheWashNeverClaimsMoreThanTheRailwayBlocks() throws Exception
    {
        washWith(2);

        Set<TileKey> washed = washedBehindTheTrain();

        assertFalse(washed.isEmpty(), "nothing is shaded, so the containment below is vacuous");

        assertTrue(everyTileOfTheCoveredEdges().containsAll(washed),
            "the diagram is shading track the railway does not hold covered: " + washed
            + " against " + everyTileOfTheCoveredEdges());
    }

    /**
     * A PATH LOCKED AHEAD OF THE TRAIN DOES NOT MOVE THE WASH (VD12-B1).
     *
     * **The half of MT-438 that the guard's fix did not reach.**  `Layout.walkStandingTrains` now walks
     * one tail per train, anchored at the last milestone of a run.  This picture is computed separately,
     * by `AutonomySession.walkBackFrom`, and it anchored at `Layout.getLocomotiveLocation` - the FIRST
     * Point in `HashMap` iteration order that holds the locomotive.  A locked path reserves every Point
     * on it (`Point.reserve` deliberately does not sweep, because that reservation is what holds a
     * junction behind the train), so during a run the locomotive holds several Points and that anchor is
     * an arbitrary one of them: it can be the destination the train has not reached.  The walk then
     * spends the train's length from there - which is Adam's *"why is the tail from bottommainapre to
     * bottommaina not orange.  it's almost as if you are shifting the location of the train"*, said of a
     * picture, about a walk that had indeed shifted the train.
     *
     * **Two other doors already knew this** and say so in the same words: `DiagramMonitor` places the
     * train marker at the last milestone *"because getLocomotiveLocation returns an ARBITRARY one of the
     * several points a running train reserves at once"*, and `AutoLocomotiveStatus` reads the same
     * milestone for its @-station line.  This walk was the third site of one rule, and
     * `Layout.whereTheTrainIs` is now that rule, asked by all three.
     *
     * **THE ARRANGEMENT IS SOUGHT, NOT HOPED FOR.**  Whether the old anchor picked a wrong Point
     * depended on `HashMap` order, so this tries candidate destinations until the anchor actually moves
     * off the square the train stands on, and only then makes its claim; if no path can move it, it says
     * so and skips rather than passing on a fixture that cannot show it.  A LOCK rather than a run,
     * because a lock reserves the whole road and returns with the train still standing - the state under
     * test, and deterministic.
     *
     * **WHAT IT PUTS BACK.**  Lock-then-unlock is not a gesture a person can make: `unlockPath` keeps
     * the ARRIVAL of the path it is given, so calling it without ever running leaves the model thinking
     * the train is at the far end.  The first draft of this test did exactly that and the next claim in
     * this class found nothing shaded at all - so the placement AND the arrival record are captured
     * before the lock and written back afterwards, and the last thing this does is prove the wash is
     * back.
     *
     * MUTATION: anchor `walkBackFrom` at `getLocomotiveLocation` again and this goes red - the wash
     * moves onto the locked path ahead, which `edgesCoveredByStandingTrains` does not hold covered.
     *
     * @throws Exception from the lock or the event thread
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

        // WHICH PATH WOULD HAVE MOVED THE OLD ANCHOR, WORKED OUT RATHER THAN TRIED.
        //
        // The second draft of this test locked a candidate, asked where the anchor had landed, and
        // unlocked again if it had not moved - and that is what made it fail for a reason of its own:
        // `unlockPath` sweeps the locomotive off every Point but the path's arrival, so the retry took
        // the train off the square it was standing on, `setLocomotive` cleared the arrival record with
        // it, and the covered set went empty.  Measured, not guessed: `covered=[] side=null road=null`.
        //
        // Nothing needs locking to know the answer.  The old anchor was the first Point in the graph's
        // own iteration order that holds the locomotive, so a path moves it exactly when one of its
        // edges ends at a Point earlier in that order than the square the train stands on.  That is a
        // question about the graph, asked without touching it, and the lock below happens once.
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

        // MEASURED, NOT DERIVED (VD13-T8).  The loop above predicts which path moves the old anchor by
        // comparing positions in the graph's own point order, and that prediction is sound only while
        // `getPoints()` and `getLocomotiveLocation` walk the same collection in the same order - which
        // was not true for a few hours on 2026-08-24, when `getPoints()` returned a copy.  One line
        // turns the prediction into an observation, and makes a silent skip impossible.
        if (locked != null)
        {
            assertNotSame(layout.getLocomotiveLocation(train), standing,
                "the lock reserved no Point that comes before " + standing.getName() + " in the graph's"
                + " own order, so the anchor this claim is about did not move and the claim would pass"
                + " without asking anything.  The prediction above and `getLocomotiveLocation` have"
                + " stopped agreeing about that order");
        }

        if (locked == null)
        {
            throw new SkipException("no path from " + standing.getName() + " reserves a Point that comes"
                + " before it in the graph's own iteration order, so the anchor this is about cannot be"
                + " made to move on this railway - the claim would pass without asking anything");
        }

        boolean putBack = false;

        try
        {
            washWith(2);

            Set<TileKey> washed = washedBehindTheTrain();

            assertFalse(washed.isEmpty(),
                "with a path locked ahead of it the train has no wash at all: the walk anchored"
                + " somewhere the covered track cannot be reached from, so the operator loses the one"
                + " mark that says which track is blocked.  covered=" + layout.edgesCoveredByStandingTrains().keySet() + " side=" + standing.getArrivedFrom() + " road=" + standing.getArrivedAlong() + " milestones=" + layout.getReachedMilestones(train) + "  Anchor: "
                + layout.whereTheTrainIs(train).getName() + ", standing at " + standing.getName());

            assertTrue(everyTileOfTheCoveredEdges().containsAll(washed),
                "with a path locked ahead of it, the diagram shades track the railway does not hold"
                + " covered: " + washed + " against " + everyTileOfTheCoveredEdges()
                + ".  The train has not moved - the lock only reserved the road - so the wash cannot"
                + " have moved either.  Anchor: " + layout.whereTheTrainIs(train).getName()
                + ", standing at " + standing.getName() + " (VD12-B1)");

            assertEquals(washed, before,
                "the wash moved when a path was locked ahead of the train.  Before: " + before
                + ", after: " + washed + ".  The lock reserves the road; it does not move the train,"
                + " and the picture has to follow the train");
        }
        finally
        {
            if (locked != null) layout.unlockPath(locked, train);

            // BACK WHERE IT WAS, and with the record the wash is computed from.  `unlockPath` keeps the
            // path's arrival, and `setLocomotive` clears the arrival side on a change of occupant, so
            // both halves have to be written back in this order.
            //
            // NOTHING IS ASSERTED IN HERE (VD13-T3).  An assertion in a `finally` replaces the body's
            // own failure with its own, so a real defect arrives as "this claim left no wash" instead
            // of the diagnosis.  What the restore achieved is recorded and judged after the block.
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
     * Sets the train's length and lets the window catch up with it, the way a run does.
     *
     * The monitor is what drives the recompute on a running layout and it only looks when something
     * has moved, so the layout is told - through the same callback a train movement fires.
     *
     * @param squares how long the train is
     * @throws Exception on an event-thread failure
     */
    private static void washWith(int squares) throws Exception
    {
        train.setTrainLength(squares);

        // The recompute the window does on every refresh of a running layout.  Asked for directly:
        // a length is not a movement, so nothing fires the monitor's callback, and what this test
        // is about is the ANSWER rather than which door asks for it.
        //
        // AND WAITED FOR (OB-192).  The window works the marks out on a worker now, because asking
        // for them on the event thread meant waiting on the `Layout` monitor a dispatch holds - so
        // asking and reading are two moments, and `support.CoveredMarks` is the one place that knows
        // it.
        support.CoveredMarks.refresh(ui);

        pump();
    }

    /** Every square the window is washing, which on this railway is all behind the one train. */
    private static Set<TileKey> washedBehindTheTrain()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey tile : everyTile())
        {
            if (ui.isTrackCovered(tile)) out.add(tile);
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

    /**
     * How many squares the edge the train arrived along actually has.
     *
     * Taken with the train long enough to claim the whole of it, so it is the number the old rule
     * washed - the figure the assertions above are told apart from.
     *
     * @return the count
     */
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

    /**
     * Puts a real tile on every square of the railway, so there is something for the redraw to reach.
     */
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

    /**
     * The tiles this test registered for these squares, drawn and ready to be asked what they show.
     */
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
     * Whether this tile is drawing the mark that says a train is lying across it.
     *
     * PAINTED AND LOOKED AT since MT-309.  The mark used to be the tile's ICON - a greyed copy of it -
     * so this could be an identity comparison against `lastIcon`.  It is a line drawn over the icon
     * now, which is nowhere in the object, and the only honest question left is what the square looks
     * like.  `support.Rendered.showsTheTrainMark` paints it and looks for the orange.
     *
     * @param label the tile
     * @return true when the mark is on it
     * @throws Exception on an event-thread failure
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

    /**
     * Leaves one train on the railway, so a square that stops being shaded stopped for a reason this
     * test caused.
     */
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
     * Measures every tile, because almost nothing on this railway carries a length and the tail walk
     * stops at the first unmeasured segment.
     *
     * Collected first and written afterwards: setTileLength rebuilds the reducer, so walking its own
     * collections while writing to them re-derives the graph under the iterator.
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
