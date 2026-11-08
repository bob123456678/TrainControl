package core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train runs to the next station and back, and the diagram's gray wash follows it (OB-180).  A test asserting only
 * on the model passes with the defect present or repaired, so this one goes all the way to the ICONS: every tile is
 * PAINTED and looked at (MT-309), and the redraw itself is asked about (`support.RepaintRecorder`).
 *
 * The fixture is Adam's own railway in a sandbox copy (OB-111), never the folder itself.  The train is driven by
 * `executePath`, not `runLocomotives`, whose threads pick their own destinations and raced with the test.
 *
 * @author Adam
 */
public class testTheShadingFollowsTheTrain
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;

    private static ExecutorService watchdog;

    /** The labels' parent, so that they and the window's real labels never evict each other (`sharesWindowWith`). */
    private static final JPanel OURS = new JPanel();

    /** Every tile measured the same; one, so a four-unit train covers its own square and three behind it (OB-278). */
    private static final int TILE_LENGTH = 1;

    /** Short, so the tail reaches a segment or two back; set here, as the operator may edit the database's value. */
    private static final int TRAIN_LENGTH = 4;

    /** How long a leg may take before it is called wedged rather than waited for. */
    private static final int LEG_TIMEOUT_SECONDS = 90;

    private static Locomotive train;
    private static Point home;

    /** The route out, kept so the second circuit is demonstrably the same one. */
    private static List<Edge> outward;
    private static Integer trainLengthWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, `test/layouts/live-snapshot`): showUI opens
        // whatever the layout preference names, and `cs2_sample_layout` changes as he runs trains on it.
        // `Scenario.folderFor` cannot spell its way out to the operator's own railway, and `LayoutSandbox` copies what
        // it names before anything reads it.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        // showUI, because the defect is in the drawing.  debug on, so the simulated Central Station
        // echoes back the accessory commands a path configuration waits for.
        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so nothing in this class can reach the drawing path");

        // THE WINDOW HAS TO HAVE FINISHED STARTING: `init` posts `display()` to the event thread without waiting, and
        // the start-up and this class would both run `AutonomySession.rebuild`, which is not safe against two at once.
        // invokeAndWait behind the posted `display()` is that task having run, not a sleep; twice, because `display()`
        // posts work of its own.
        pumpTheEventThread();
        pumpTheEventThread();

        // The window's OWN session, over the model's own pages - not a second parse of the same files.
        // A re-parse leaves every switch without an accessory and the railway comes apart into
        // fragments; see LayoutSandbox.wiredPages for the measurements.
        session = ui.getAutonomySession();

        assertNotNull(session, "the sandbox copy holds no autonomy setup, so there is no railway to run");

        watchdog = Executors.newSingleThreadExecutor();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (layout != null) layout.stopLocomotives();

            // PUT BACK, because init() opens Adam's own locomotive database rather than a fresh one,
            // and a length this test chose is not a measurement of his train.
            if (train != null) train.setTrainLength(trainLengthWas == null ? 0 : trainLengthWas);
        }
        finally
        {
            if (watchdog != null) watchdog.shutdownNow();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The whole journey: standing, away, and home again, with the icons checked at each stop.  One method, since these
     * are stages of one run and TestNG does not promise the order.  Stage 4 compares two returns rather than the start,
     * because the tail is on DIFFERENT track the second time.
     *
     * MUTATIONS: deleting the `repaintTheWashWhereItChanged(...)` call from `CoveredTrackView.workOutCoveredTrack`
     * fails stage 2's screen assertion while every model-level assertion still passes (the defect exactly); making it
     * take the intersection of the COVERED sets rather than their symmetric difference leaves this green (the blocked
     * half's own difference redraws the square); making `Layout.edgesCoveredByStandingTrains` keep the departed train's
     * edges fails stage 2's model assertion; leaving `arrivedFrom` set on the square a train drove away from fails
     * stage 4.
     */
    @Test
    public void testTheWashLeavesTheOldStationAndComesBackWithTheTrain() throws Exception
    {
        // ON THE EVENT THREAD, because the window rebuilds this same session from there and `AutonomySession.rebuild`
        // is not safe against two of them at once.
        SwingUtilities.invokeAndWait(new Runnable()
        {
            @Override
            public void run()
            {
                measureEveryTileAs(TILE_LENGTH);

                // AND NO STATION'S SIZE: the round trip was refused by BottomInner's maximum, a rule about the number
                // typed.
                session.clearEveryMaxTrainLength();
            }
        });

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the setup did not build, so there is no railway to run a train on");

        oneTrainOnly();

        assertNotNull(train, "no locomotive is standing on this railway, so nothing can be shaded");

        home = layout.getLocomotiveLocation(train);

        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        // ---------------------------------------------------------------- 1. standing at home

        Set<TileKey> atHome = washAsDrawn();

        assertFalse(atHome.isEmpty(),
            "nothing at all is shaded with " + train.getName() + " standing at " + home.getName()
            + " and every tile measured at " + TILE_LENGTH + ". The tail walk is not reaching any"
            + " measured segment, so every assertion below would be about an empty set and this test"
            + " would prove nothing");

        List<LayoutLabel> homeLabels = labelsFor(atHome);

        assertFalse(homeLabels.isEmpty(),
            "none of the shaded squares " + atHome + " has a tile on the diagram, so there is nothing"
            + " the operator could have seen greyed and nothing this test can check");

        for (LayoutLabel label : homeLabels)
        {
            assertTrue(isWashed(label),
                "the square " + squareOf(label) + " (covered roads " + ui.coveredRoutesAt(squareOf(label)) + ", "
                + train.getName() + " at " + home.getName() + ", covered " + atHome + ") is one the railway says is"
                + " covered, and it is drawn without the wash. Nothing below can tell the wash being TAKEN OFF from"
                + " its never having been put on");
        }

        // ---------------------------------------------------------------- 2. away to the next station

        // FROM HERE THE TILES ARE ASKED WHAT THE SCREEN SHOWS (issue 19): every one of them was just drawn with the wash, and
        // a window on screen draws one again only when something asks it to.  `isWashed` paints the label afresh, which
        // answers right whether or not anything asked - so the repaints asked for are written down from here.
        Point next;

        Set<TileKey> atNext;

        Set<javax.swing.JComponent> redrawn;

        try (support.RepaintRecorder screen = support.RepaintRecorder.install())
        {
            next = runToTheNextStation();

            assertNotNull(next, "the train never left " + home.getName());

            atNext = washAsDrawn();

            redrawn = screen.dirtied();
        }

        // THE MODEL: the old squares are no longer covered.
        for (TileKey wasCovered : atHome)
        {
            assertFalse(ui.isTrackCovered(wasCovered),
                "the square " + wasCovered + " is still reported as covered although " + train.getName()
                + " has driven from " + home.getName() + " to " + next.getName() + ". The tail is being"
                + " left behind on the track it used to lie across");
        }

        // AND THE SCREEN, WHICH IS THE DEFECT (OB-180): the model was right before the fix as well as after, and the
        // icons were stale either way, because a tile is only redrawn when its own accessory, feedback or route
        // changes.  AS THE SCREEN SHOWS IT (issue 19): a tile nothing asked to repaint still shows the wash it was
        // drawn with in stage 1.
        for (LayoutLabel label : homeLabels)
        {
            assertFalse(!redrawn.contains(label) || isWashed(label),
                "the square " + squareOf(label) + " (covered roads now " + ui.coveredRoutesAt(squareOf(label))
                + ", home " + home.getName() + ", covered at home " + atHome + ", "
                + (redrawn.contains(label) ? "redrawn" : "never asked to redraw") + ")"
                + " behind where " + train.getName() + " used to stand is still drawn greyed"
                + " after it has run to " + next.getName() + ". The railway knows the track is clear"
                + " and the diagram is still telling the operator it is blocked - which is OB-180"
                + " itself: \"its former shaded icons are not reset\"");
        }

        // AND THE WASH WENT SOMEWHERE, so the assertion above is not passing because nothing is ever
        // shaded any more.
        assertFalse(atNext.isEmpty(),
            "the train is standing at " + next.getName() + " and shading nothing at all, so the"
            + " assertions above are satisfied by the feature having stopped working rather than by"
            + " the wash having moved with the train");

        for (LayoutLabel label : labelsFor(atNext))
        {
            assertTrue(isWashed(label),
                "a square the train is now lying across is drawn without the wash, so the redraw takes"
                + " the wash off and never puts it on - one half of the symmetric difference");
        }

        // ---------------------------------------------------------------- 3. home again, the baseline

        List<Edge> back = layout.bfs(next, home, null);

        assertTrue(back != null && !back.isEmpty(),
            "there is no route from " + next.getName() + " back to " + home.getName() + ", so the"
            + " round trip cannot be made and the property below cannot be tested here");

        assertTrue(runLeg(back, "first return"),
            "the train would not run back from " + next.getName() + " to " + home.getName()
            + ". Layout says: " + Layout.getLastError());

        assertEquals(layout.getLocomotiveLocation(train), home,
            "the return leg reported success and the train is not at " + home.getName());

        Set<TileKey> afterOneCircuit = washAsDrawn();

        assertFalse(afterOneCircuit.isEmpty(),
            "the train is home from " + next.getName() + " and shading nothing at all, so the round"
            + " trip below would be comparing one empty set with another");

        assertNotNull(home.getArrivedFrom(),
            "the arrival did not record which way the train came in, so its tail is on track nothing"
            + " can name and the second circuit has nothing stable to reproduce");

        List<LayoutLabel> baselineLabels = labelsFor(afterOneCircuit);

        // ---------------------------------------------------------------- 4. the same circuit again

        // THE SAME TWO PATHS: `bfs` asks about the graph, which has not changed, so anything different about the second
        // circuit is the running.
        assertTrue(runLeg(outward, "second outward"),
            "the train would not run out to " + next.getName() + " a second time. Layout says: "
            + Layout.getLastError());

        // AND THE WASH LEFT HOME AGAIN, as in stage 2: a second circuit that never moved the shading would satisfy the
        // comparison below by nothing ever changing.
        Set<TileKey> awayAgain = washAsDrawn();

        assertNotEquals(awayAgain, afterOneCircuit,
            "the shading did not change when the train left " + home.getName() + " for the second"
            + " time, so the comparison below is between two identical do-nothings");

        assertTrue(runLeg(back, "second return"),
            "the train would not run back from " + next.getName() + " a second time. Layout says: "
            + Layout.getLastError());

        assertEquals(layout.getLocomotiveLocation(train), home,
            "the second return reported success and the train is not at " + home.getName());

        Set<TileKey> afterTwoCircuits = washAsDrawn();

        assertFalse(afterTwoCircuits.isEmpty(),
            "the train is home again and nothing at all is shaded, so the equality below would hold"
            + " for two empty sets and prove nothing");

        assertEquals(afterTwoCircuits, afterOneCircuit,
            "the same train has driven the same circuit twice and come to rest in the same place both"
            + " times, and the shading is different. The wash has to be a function of where trains are"
            + " STANDING - if driving a lap can leave it changed then part of what is on the screen is"
            + " a residue of where the train has been, which is the family OB-180 belongs to."
            + " After one lap: " + afterOneCircuit + " after two: " + afterTwoCircuits);

        for (LayoutLabel label : baselineLabels)
        {
            assertTrue(isWashed(label),
                "the covered set came back to what it was and the tiles did not, so the squares behind"
                + " the returned train are drawn clear while the railway holds them blocked");
        }
    }

    // ---------------------------------------------------------------- the run

    /** Drives the train to the first station it can reach and get back from; null if it never went anywhere. */
    private static Point runToTheNextStation() throws Exception
    {
        for (Point candidate : layout.getPoints())
        {
            if (candidate == home || !candidate.isDestination()) continue;

            // NOT A TERMINUS OR A REVERSING POINT: both turn the train round, so its tail lies on the other side and
            // the round trip below would be untestable.
            if (candidate.isTerminus() || candidate.isReversing()) continue;

            List<Edge> out = layout.bfs(home, candidate, null);

            if (out == null || out.isEmpty()) continue;

            // A ROUTE BACK HAS TO EXIST BEFORE WE LEAVE, asked while the train is still home, so a candidate it could
            // not return from is skipped.
            List<Edge> back = layout.bfs(candidate, home, null);

            if (back == null || back.isEmpty()) continue;

            if (!layout.isPathClear(out, train, false)) continue;

            if (!runLeg(out, "outward")) continue;

            outward = out;

            return layout.getLocomotiveLocation(train);
        }

        return null;
    }

    /** Runs one leg, and fails with something legible rather than hanging; says whether the train got there. */
    private static boolean runLeg(final List<Edge> path, String which) throws Exception
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
            return leg.get(LEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (TimeoutException wedged)
        {
            layout.stopLocomotives();

            fail("the " + which + " leg never arrived, so this run would have hung rather than failed");

            return false;
        }
    }

    // ---------------------------------------------------------------- the diagram

    /**
     * Recomputes the wash the way the window does (`refreshCoveredTrack`, whose `finally` holds the redraw this test
     * exists for), and waits for the tiles to catch up.
     */
    private static Set<TileKey> washAsDrawn() throws Exception
    {
        // AND WAITED FOR (OB-192): the window works the marks out on a worker, so asking and reading are two moments,
        // and `support.CoveredMarks` is the one place that knows it.
        support.CoveredMarks.refresh(ui);

        // The labels repaint themselves through invokeLater, so nothing on screen has changed until the
        // event thread has run.  invokeAndWait behind them is the queue emptying, not a sleep.
        pumpTheEventThread();

        return session.tilesCoveredByStandingTrains(layout);
    }

    /**
     * Registers a real tile for each square, so there is something on the diagram to redraw: production `LayoutLabel`s
     * in the production registry.  Registered rather than found because a square with no registered tile is silently
     * skipped by the redraw.
     */
    private static List<LayoutLabel> labelsFor(Set<TileKey> squares) throws Exception
    {
        final List<LayoutLabel> out = new ArrayList<>();

        for (TileKey square : squares)
        {
            org.traincontrol.base.LayoutDiagram page = model.getLayout(square.getPage());

            if (page == null) continue;

            LayoutDiagramComponent component = page.getComponent(square.getX(), square.getY());

            // A text caption is not track and refuses the wash on purpose.
            if (component == null || component.isText()) continue;

            LayoutLabel label = new LayoutLabel(component, OURS, 30, ui, false);

            label.setSquare(square);

            ui.getDiagramTileRegistry().register(square, label);

            out.add(label);
        }

        // The icons are decoded off the event thread, so a label has none straight after construction; waited for,
        // since a null icon makes `refreshCoveredMark` do nothing.
        long until = System.currentTimeMillis() + 30000;

        for (LayoutLabel label : out)
        {
            while (bareIconOf(label) == null && System.currentTimeMillis() < until)
            {
                pumpTheEventThread();
            }

            assertNotNull(bareIconOf(label),
                "a tile never drew its image, so it has nothing to lay the wash over and the"
                + " assertions about it would pass whatever the redraw did");
        }

        // Drawn now, so each shows the answer to the CURRENT covered set.
        for (LayoutLabel label : out) label.refreshCoveredMark();

        pumpTheEventThread();

        return out;
    }

    /** The square a label was registered for, read back for a failure message. */
    private static TileKey squareOf(LayoutLabel label) throws Exception
    {
        java.lang.reflect.Field square = LayoutLabel.class.getDeclaredField("square");

        square.setAccessible(true);

        return (TileKey) square.get(label);
    }

    /**
     * Whether this tile is drawing the mark that says a train is lying across it: painted and looked at (MT-309), since
     * the mark is a line drawn over the icon, which is nowhere in the object.
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

    private static void pumpTheEventThread() throws Exception
    {
        SwingUtilities.invokeAndWait(new Runnable()
        {
            @Override
            public void run()
            {
            }
        });
    }

    // ---------------------------------------------------------------- the fixture

    /**
     * Leaves one train on the railway, which makes the assertions attributable: the first found standing where it
     * records its arrival side and that actually covers something (a recorded side does not guarantee it: a locomotive
     * at `1 - Main:0,11` recorded one that leaves by track the graph cannot follow).
     */
    private static void oneTrainOnly()
    {
        Point keep = null;

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() == null || point.getArrivedFrom() == null) continue;

            // AT A STATION AUTONOMY MAY CHOOSE: one it does not choose is drawn gray (MT-492), which a test of the
            // shading cannot tell from shading.
            if (!point.isAutoDestination()) continue;

            Locomotive candidate = point.getCurrentLocomotive();

            Integer was = candidate.getTrainLength();

            candidate.setTrainLength(TRAIN_LENGTH);

            boolean coversTrack = layout.edgesCoveredByStandingTrains().containsValue(candidate);

            candidate.setTrainLength(was);

            if (coversTrack)
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

        train.setTrainLength(TRAIN_LENGTH);
    }

    /**
     * Measures every tile of the reduced graph, because almost nothing on this railway carries a length and the tail
     * walk stops at the first segment without one.  Collected first and written afterwards: `setTileLength` rebuilds
     * the reducer under the iterator.
     */
    private static void measureEveryTileAs(int units)
    {
        support.TileLengths.measureEveryTileAs(session, units);
    }
}
