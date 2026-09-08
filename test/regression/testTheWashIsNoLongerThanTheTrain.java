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
    private static Point home;

    /** Where the train came to rest after the run, and the squares the OLD rule washed behind it. */
    private static Point arrived;
    private static int wholeEdgeBehind;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The operator's own railway, COPIED (OB-111): the wash is a product of the diagram, so a
        // hand-built graph has no tiles for it to be drawn on.
        sandbox = support.LayoutSandbox.open(new File("cs2_sample_layout"));

        // showUI, because the subject is what is drawn.  debug on, so the simulated Central Station
        // echoes the accessory commands a path configuration waits for.
        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);
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

        // The recompute the window does on every refresh of a running layout.  Called rather than
        // waited for: a length is not a movement, so nothing fires the monitor's callback, and what
        // this test is about is the ANSWER rather than which door asks for it.
        java.lang.reflect.Method refresh =
            TrainControlUI.class.getDeclaredMethod("refreshCoveredTrack");

        refresh.setAccessible(true);
        refresh.invoke(ui);

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
