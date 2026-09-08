package core;

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
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * A train runs to the next station and back, and the diagram's grey wash follows it (OB-180).
 *
 * Adam: **"when a train is manually moved to a new station in the track diagram viewer using control+X
 * and V, its former shaded icons are not reset."**
 *
 * **What the defect actually was, because it decides what this test has to touch.**  Neither
 * `Layout.edgesCoveredByStandingTrains` nor `AutonomySession.tilesCoveredByStandingTrains` was ever
 * wrong: the model knew perfectly well which squares a moved train had stopped covering.  What was
 * missing is that nothing REDREW them.  The wash is applied when a tile is drawn, and a tile is drawn
 * only when its own accessory, feedback or route changes - so a train moving, which changes nothing
 * about any tile it is not standing on, updated the set and left the screen alone.
 *
 * A test that asserts only on `tilesCoveredByStandingTrains` therefore passes identically with the
 * defect present and with it repaired, however faithfully the train runs.  So this one goes all the way
 * to the ICONS: real `LayoutLabel`s, registered in the real `DiagramTileRegistry`, redrawn by the real
 * `TrainControlUI.refreshCoveredTrack`, and asked afterwards which image they are actually showing.
 *
 * **How far that is, exactly.**  `refreshCoveredWash` sets a tile's icon to `addCoveredOverlay(lastIcon)`
 * when its square is covered and to `lastIcon` itself when it is not, so the question "is this tile
 * greyed" has a plain answer in the object and that is what is asked here.  What is NOT checked is
 * pixels on a screen: no window is photographed and none needs to be, because everything between the
 * train moving and the icon changing - the recompute, the symmetric difference, the registry lookup,
 * the event-thread hop, the overlay - is production code being run.  Confirmed by mutation on
 * 2026-09-08: with `repaintTheWashWhereItChanged(was, coveredTrack)` deleted from `refreshCoveredTrack`
 * this class stops at the icon assertion in stage 2 - having passed every model-level assertion above
 * it, including the one saying those same squares are no longer covered - which is OB-180 restored and
 * is the gap between the two that no other test in this suite closes.
 *
 * **The fixture is Adam's own railway, in a sandbox copy (OB-111), and never the folder itself.**  A
 * hand-built `Layout` would have been easier to make deterministic, and it was rejected: the squares
 * that grey are a product of the diagram - `AutonomySession` needs a reducer and a station index to
 * turn a runtime `Edge` into the tiles lying along it - and the registry keys labels by page and
 * square.  A synthetic graph has no diagram behind it, so the half of the mechanism this defect lives
 * in would not exist.
 *
 * **The lengths are assigned here**, as Adam asked: almost nothing on his layout carries one, and the
 * tail walk stops at the first unmeasured segment, so with no measurements nothing is ever covered and
 * every assertion below would be about an empty set.  Every tile of the reduced graph is measured at
 * {@link #TILE_LENGTH}, which is not a railway anybody would build and is the only way to be sure the
 * wash comes from the measurement rather than from the walk never being reached.
 *
 * **The train is driven by `executePath`, not by `runLocomotives`.**  `runLocomotives` is autonomy's
 * own Start: it dispatches every configured locomotive on a thread that picks its own destinations.
 * Measured on this railway, that races with anything the test asks for - the same outward leg reported
 * success on one run and refusal on the next, with the train never leaving its platform.  `executePath`
 * is the method those autonomy threads call to move a train, so the railway does exactly what it does
 * under autonomy - the switches are thrown, the path is locked, the sensors are tripped, the arrival is
 * recorded - and the test knows where the train is going.
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

    /**
     * The parent of the labels this test registers, and the reason they are never pruned.
     *
     * `DiagramTileRegistry.register` drops the labels of the SAME window when a new one arrives for a
     * square, judged by `sharesWindowWith`, which compares the parent container.  A parent of this
     * test's own means the window's real labels and these can never evict each other.
     */
    private static final JPanel OURS = new JPanel();

    /** Every tile measured the same, so the tail's reach is arithmetic rather than a property of his railway. */
    private static final int TILE_LENGTH = 10;

    /**
     * Short, so the tail reaches back a segment or two rather than across the whole railway.
     *
     * Not zero and not enormous, both of which would make this test say nothing: a train with no
     * recorded length covers nothing at all, and one long enough to reach everywhere would shade so
     * much of the diagram that "the shading moved" and "the shading is still there" become the same
     * picture.  Set here rather than read from the locomotive database, because the length decides how
     * far the tail reaches and a test whose subject is a number the operator may edit tomorrow is a
     * test that goes red for a reason that has nothing to do with it.
     */
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
        // BEFORE init, because showUI below opens whatever the layout preference names and that is
        // Adam's real railway on his machine (OB-111).  The sandbox redirects the preference to a copy.
        sandbox = support.LayoutSandbox.open(new File("cs2_sample_layout"));

        // showUI, because the defect is in the drawing.  debug on, so the simulated Central Station
        // echoes back the accessory commands a path configuration waits for.
        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so nothing in this class can reach the drawing path");

        // THE WINDOW HAS TO HAVE FINISHED STARTING (measured, 2026-09-08).
        //
        // `init` waits for the window to be BUILT and then posts `display()` to the event thread
        // without waiting for it, so it returns with the diagram still being drawn.  Everything below
        // rebuilds the autonomy session, and so does the start-up - two threads inside one
        // `AutonomySession.rebuild`, which is not written to survive that: the first run of this class
        // died with a ConcurrentModificationException raised from `AutonomyBuilder.splitSides`
        // iterating `reducer.getEdges()` while the other rebuild replaced it.
        //
        // invokeAndWait behind the posted `display()` is that task having run, not a sleep.  Twice,
        // because `display()` posts work of its own.
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
     * The whole journey: standing, away, and home again, with the icons checked at each stop.
     *
     * One method rather than four, because these are stages of one run on one railway and TestNG does
     * not promise the order of anything else.  Split into methods sharing static state, a reordering
     * would assert about the wash at a station the train had not reached.
     *
     * WHAT IT PINS, in order:
     *
     * 1. A train standing somewhere covers track, and the labels for that track are drawn WITH the
     *    wash.  Without this the rest is a test that nothing is ever shaded, which passes on a railway
     *    where the feature does not work at all.
     * 2. Once it has run to the next station, the squares behind where it USED to stand are no longer
     *    covered AND their labels have been redrawn without the wash.  That second half is OB-180: the
     *    model was always right about this and the screen was not.
     * 3. Where it is standing now covers something, so the wash moved rather than merely vanishing.
     * 4. Sent home and round the SAME circuit a second time, the covered set and the icons come back
     *    to exactly what they were after the first return, and are still not empty - the shading is a
     *    function of where trains are standing, not a residue of where they have been.
     *
     * **Why stage 4 compares two returns rather than the start (measured, 2026-09-08).**  Adam asked
     * for the set at the beginning to be compared with the set at the end, and the first version of
     * this did exactly that and went red - correctly.  The train comes home to the same square, facing
     * the same way, with the same length, and its tail is on DIFFERENT track: ten squares up one
     * approach at the start, five down another after the run.  Two roads converge two squares short of
     * that platform, the setup's recorded arrival side names one of them and the route back drives the
     * other, and `arrivedFrom` is what the tail walk follows.  So the two states are not the same state
     * and the railway is right to shade them differently; demanding equality there would have been
     * asserting that the diagram should ignore which way a train came in, which is the opposite of what
     * `arrivedFrom` is for.
     *
     * What Adam is buying - that a journey leaves the shading where it found it - survives that
     * intact, and is what the second circuit tests: a baseline taken after a real arrival, and the same
     * circuit driven again, with nothing but the running to tell them apart.
     *
     * MUTATIONS that fail it: deleting `repaintTheWashWhereItChanged(was, coveredTrack)` from
     * `TrainControlUI.refreshCoveredTrack` fails stage 2's icon assertion while every model-level
     * assertion here still passes - which is the defect exactly.  Making `repaintTheWashWhereItChanged`
     * take the intersection rather than the symmetric difference fails stage 2 as well.  Making
     * `Layout.edgesCoveredByStandingTrains` keep the departed train's edges fails stage 2's model
     * assertion, and leaving `arrivedFrom` set on the square a train drove away from fails stage 4.
     *
     * @throws Exception on a failure to build or run
     */
    @Test
    public void testTheWashLeavesTheOldStationAndComesBackWithTheTrain() throws Exception
    {
        // ON THE EVENT THREAD, because the window rebuilds this same session from there and
        // `AutonomySession.rebuild` is not safe against two of them at once - see the note in the
        // set-up about the ConcurrentModificationException this class raised on its first run.
        SwingUtilities.invokeAndWait(new Runnable()
        {
            @Override
            public void run()
            {
                measureEveryTileAs(TILE_LENGTH);
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
                "a square the railway says is covered is drawn without the wash. Nothing below can"
                + " tell the wash being TAKEN OFF from its never having been put on");
        }

        // ---------------------------------------------------------------- 2. away to the next station

        Point next = runToTheNextStation();

        assertNotNull(next, "the train never left " + home.getName());

        Set<TileKey> atNext = washAsDrawn();

        // THE MODEL: the old squares are no longer covered.
        for (TileKey wasCovered : atHome)
        {
            assertFalse(ui.isTrackCovered(wasCovered),
                "the square " + wasCovered + " is still reported as covered although " + train.getName()
                + " has driven from " + home.getName() + " to " + next.getName() + ". The tail is being"
                + " left behind on the track it used to lie across");
        }

        // AND THE SCREEN, WHICH IS THE DEFECT (OB-180).
        //
        // The two are not the same claim and the whole of OB-180 lives in the gap: the set above was
        // right before the fix as well as after it, and the icons were stale either way, because a
        // tile is only redrawn when its own accessory, feedback or route changes and a train moving
        // changes none of those.
        for (LayoutLabel label : homeLabels)
        {
            assertFalse(isWashed(label),
                "the square behind where " + train.getName() + " used to stand is still drawn greyed"
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

        // THE SAME TWO PATHS, because `bfs` is a question about the graph and the graph has not
        // changed - so anything different about the second circuit is the running, which is what is
        // being tested.
        assertTrue(runLeg(outward, "second outward"),
            "the train would not run out to " + next.getName() + " a second time. Layout says: "
            + Layout.getLastError());

        // AND THE WASH LEFT HOME AGAIN, which is the same door as stage 2 and is checked again here
        // because a second circuit that never moved the shading would satisfy the comparison below
        // simply by nothing ever changing.
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

    /**
     * Drives the train to the first station it can reach that it can also get back from.
     *
     * Searched rather than named, because a station name is a fact about the layout file and the
     * property under test is not.  The search only chooses WHERE; every assertion is about what the
     * shading does, and a candidate that will not run is skipped before the train has moved.
     *
     * @return where it ended up, or null if it never went anywhere
     * @throws Exception on a failure to run
     */
    private static Point runToTheNextStation() throws Exception
    {
        for (Point candidate : layout.getPoints())
        {
            if (candidate == home || !candidate.isDestination()) continue;

            // NOT A TERMINUS AND NOT A REVERSING POINT, deliberately.  Both turn the train round on
            // arrival, and a turned train's tail lies on the other side of it - a different covered
            // set for an honest reason, which would make the round trip below untestable rather than
            // failing.  What this test is about is the wash following an ordinary run.
            if (candidate.isTerminus() || candidate.isReversing()) continue;

            List<Edge> out = layout.bfs(home, candidate, null);

            if (out == null || out.isEmpty()) continue;

            // A ROUTE BACK HAS TO EXIST BEFORE WE LEAVE.  Asked of the graph while the train is still
            // at home, so a candidate it could not return from is skipped rather than discovered once
            // the train is standing there.
            List<Edge> back = layout.bfs(candidate, home, null);

            if (back == null || back.isEmpty()) continue;

            if (!layout.isPathClear(out, train, false)) continue;

            if (!runLeg(out, "outward")) continue;

            outward = out;

            return layout.getLocomotiveLocation(train);
        }

        return null;
    }

    /**
     * Runs one leg, and fails with something legible rather than hanging.
     *
     * @param path the route
     * @param which which leg, for the message
     * @return whether the train got there
     * @throws Exception on a failure to run
     */
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
     * Recomputes the wash the way the window does, and waits for the tiles to catch up.
     *
     * `refreshCoveredTrack` is the window's own entry point - private, because nothing outside it has
     * any business recomputing the set - and it is what every repaint of a running layout calls.  Going
     * through it rather than round it is the point: the redraw this test exists for is in its `finally`.
     *
     * @return the squares the window now holds as covered
     * @throws Exception on a reflection or event-thread failure
     */
    private static Set<TileKey> washAsDrawn() throws Exception
    {
        java.lang.reflect.Method refresh =
            TrainControlUI.class.getDeclaredMethod("refreshCoveredTrack");

        refresh.setAccessible(true);
        refresh.invoke(ui);

        // The labels repaint themselves through invokeLater, so nothing on screen has changed until the
        // event thread has run.  invokeAndWait behind them is the queue emptying, not a sleep.
        pumpTheEventThread();

        return session.tilesCoveredByStandingTrains(layout);
    }

    /**
     * Registers a real tile for each square, so there is something on the diagram to redraw.
     *
     * These are production `LayoutLabel`s in the production registry, built from the components of the
     * real page and pointed at the real window - what they are not is attached to a visible grid, which
     * the redraw does not care about.  Registered rather than found because which page the window
     * happens to be showing is not something a test should depend on, and a square with no registered
     * tile is silently skipped by the redraw.
     *
     * @param squares the squares to put tiles on
     * @return the labels, one per square that the diagram actually has a component for
     * @throws Exception on an event-thread failure
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

        // The icons are decoded off the event thread and applied on it, so a label asked for its image
        // straight after construction has none.  Waited for rather than assumed: a null icon makes
        // `refreshCoveredWash` return without doing anything, and every assertion about the wash would
        // then be an assertion about a tile that was never drawn.
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

        // Drawn now, so the wash each one is showing is the answer to the CURRENT covered set rather
        // than to whatever it was when the label was constructed.
        for (LayoutLabel label : out) label.refreshCoveredWash();

        pumpTheEventThread();

        return out;
    }

    /**
     * Whether this tile is showing the greyed image or the bare one.
     *
     * `refreshCoveredWash` sets the icon to `addCoveredOverlay(lastIcon)` when the square is covered and
     * to `lastIcon` itself when it is not, so identity against `lastIcon` is the question exactly.  Read
     * fresh each time rather than remembered, because an ordinary redraw replaces `lastIcon` with a new
     * object for the same picture.
     *
     * @param label the tile
     * @return true when the wash is on it
     * @throws Exception on a reflection failure
     */
    private static boolean isWashed(LayoutLabel label) throws Exception
    {
        return label.getIcon() != bareIconOf(label);
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
     * Leaves one train on the railway, which is what Adam asked for and is also what makes the
     * assertions attributable: with four trains standing about, a square that stops being shaded could
     * have stopped for a reason nothing in this test did.
     *
     * The one kept is the first one found standing somewhere that records which way it came in - the
     * tail walk needs that side to choose its first hop, and a train nobody has told the railway about
     * covers nothing at all.
     *
     * AND THAT ACTUALLY COVERS SOMETHING, which recording an arrival side turns out not to guarantee
     * (2026-09-08).  This class went red on a fixture it had never been changed against: Adam placed a
     * locomotive at `1 - Main:0,11`, that Point sorted first among those with an `arrivedFrom`, and the
     * side it records leaves by track the graph cannot follow - so the railway covered NO edge at all
     * and stage 1 failed saying nothing was shaded.  Diagnosed by reverting this class's subject to
     * HEAD and watching it fail identically.
     *
     * A test that picks its subject out of the operator's live railway has to pick one that can answer
     * the question, and "records an arrival side" was a proxy for that rather than the thing itself.
     * The railway is simply asked - with the length this class uses, because coverage depends on it.
     */
    private static void oneTrainOnly()
    {
        Point keep = null;

        for (Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() == null || point.getArrivedFrom() == null) continue;

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
     * Measures every tile of the reduced graph, because almost nothing on this railway carries a length.
     *
     * Adam, on the same railway: **"For testing, it should be 1 between BottomMainA and BottomMainPost
     * ... It may not be realistic, but it will allow us whether the guards work correctly."**  The tail
     * walk stops at the first segment without a positive length, so unmeasured track means nothing is
     * ever covered - measured on 2026-09-06, four fifty-unit trains on this layout covered no edge at
     * all.
     *
     * Collected first and written afterwards: `setTileLength` rebuilds the reducer, so walking its own
     * collections while writing to them re-derives the graph under the iterator.
     *
     * @param units how long to call each tile
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
