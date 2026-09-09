package regression;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
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
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * The turn a train makes at its destination reaches the setup, and reaches it the right way round.
 *
 * OB-190 / OB-189, by way of REV9-A1.  Adam: he sends a train to a square trains may turn at, answers
 * **"no, do not keep the direction"**, the locomotive physically turns, and the diagram goes on drawing
 * it facing the way it set off.
 *
 * **Why nothing caught it before this class.**  `TrainControlUI.reconcileFacingWhenIdle` - the drain
 * that carries a destination turn from the railway into the setup - was reached by no executed test at
 * all.  Its rules were pinned by `testADirectionChangeIsNotSwallowed`, which reads `TrainControlUI.java`
 * as a STRING and compares offsets, so the statement it proves is present is the exact statement the
 * defect lives in (REV9-C3).  `testTheGraphIsToldWhenARunEnds` proves the idle announcement happens and
 * `testAReversalCommandIsEmitted` proves the command reaches the track; the seam between them - what
 * the drain writes, and to which copy - had nothing running over it.
 *
 * So this class runs a real journey on Adam's own railway (in a sandbox copy - OB-111), turns the train
 * at its destination the way the manual door does, drains, and then asks the SETUP and the RUNNING
 * LAYOUT what they now believe.
 *
 * **The two ways it went wrong, which are the two journeys below.**  Both come from the drain's pivot:
 * it flipped `AutonomySession.getFacing(tile)` - the setup's STORED facing for the arrival square -
 * which behaviour.md 6a declares stale at exactly that moment (*"a run moves trains, where they ended
 * up lives only in the running layout, and nothing writes it back to the setup when the run ends"*),
 * and `captureFromLayout` adds that the field is never cleared, so an empty square still carries the
 * last occupant's answer.
 *
 * 1. **The turn is lost.**  No stored facing for the arrival square means `flipFacing` has nothing to
 *    flip: it returns null, and the drain removed the pending record anyway.  The physical train is
 *    reversed and nothing anywhere says so - and because the record is gone, no later refresh can heal
 *    it.
 * 2. **The turn is written backwards.**  A stored facing that happens to be the POST-turn side - which
 *    at a terminus is the systematic case, since that is what the last capture derived from the turning
 *    copy - flips to the PRE-turn side, and `moveOntoFacingCopy` then stands the locomotive on the
 *    copy for the wrong facing, actively moving it off one that was right.
 *
 * **What is reliable at that moment, and is what the fix pivots on.**  Behaviour.md 4: *"a train faces
 * the way it will leave; once it has been turned round the two point the same way while the carriages
 * have not moved."*  So the facing of a train that has just turned at its destination IS the side it
 * arrived by - which the arrival wrote down itself, on the square it stopped at, before turning it.
 * That is an absolute answer, it needs no record to be in sync first, and re-applying it cannot drift.
 *
 * The third test is the other half of REV9-A1: a turn the drain could NOT write must still be owed.
 *
 * @author Adam
 */
public class testTheTurnAtTheDestinationReachesTheDiagram
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static Layout layout;
    private static ExecutorService watchdog;

    /** The one train this class drives, and the length it had before this class shortened it. */
    private static Locomotive train;
    private static Integer trainLengthWas;

    /**
     * Short enough to fit the berths this railway offers, and put back afterwards.
     *
     * The track-room rule judges every destination (behaviour.md 5a), and the may-reverse platform
     * this class needs measures one unit on the run in - so the first locomotive in Adam's database,
     * three units long, was refused all 43 routes to it before anything could be measured.  Its real
     * length is a fact about his train and is restored in the teardown, because `init` opens his own
     * locomotive database rather than a fresh one.
     */
    private static final int TRAIN_LENGTH = 1;

    /** How long one leg may take before it is called wedged rather than waited for. */
    private static final int LEG_TIMEOUT_SECONDS = 90;

    /**
     * The manual door's answer when the operator says "no, do not keep the direction".
     *
     * `asksAbout` answers about the RAILWAY and never about the answer, which is the rule
     * `testTheJourneyPolicyAnswersAsksAboutIndependentlyOfTheAnswer` pins - so it is true everywhere
     * and `shouldReverse` carries the decision.  Without both, `shouldReverseAt` takes the
     * `!mayReverseAt(current) && !asksAbout(current)` exit at the plain copy and the train is never
     * turned, which would make every assertion below an assertion about a journey that did nothing.
     */
    private static final Layout.ReversalPolicy TURN_IT_ROUND = new Layout.ReversalPolicy()
    {
        @Override
        public boolean shouldReverse(Locomotive loc, Point at)
        {
            return true;
        }

        @Override
        public boolean asksAbout(Point at)
        {
            return true;
        }
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE init (OB-111): the window opens whatever the layout preference names, and on Adam's
        // machine that is his real, unrecoverable railway.  The sandbox redirects the preference at a
        // copy.
        sandbox = // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, and `test/layouts/live-snapshot`).
        //
        // The SHAPE is what this class is about, and the shape is the same in both.  Where his trains
        // are standing, how long they are and which side they came in by are not, and reading those
        // off the live folder is how `testTheLengthGuardsOnTheRealLayout` came to assert that the
        // 2-8-4 stood at BottomMainB - it is at BottomMainA now, and that class was red for a reason
        // that had nothing to do with any guard.  A fixture that moves while nobody is looking makes
        // every class over it say something different every week.
        support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        // showUI, because `reconcileFacingWhenIdle` is a method of the window and this class exists to
        // RUN it.  debug on, because `Layout.setSimulate(true)` refuses outside debug mode and without
        // it no journey below ever reaches its destination.
        model = init(null, true, true, false, true);
        model.setNetworkCommState(false);
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so the drain this class is about cannot be reached");

        // THE WINDOW HAS TO HAVE FINISHED STARTING.  `init` posts `display()` to the event thread and
        // returns without waiting for it, and everything below rebuilds the same session the start-up
        // is still building - two threads inside one `AutonomySession.rebuild`, which is how
        // `testTheShadingFollowsTheTrain` died with a ConcurrentModificationException on its first run.
        pumpTheEventThread();
        pumpTheEventThread();

        session = ui.getAutonomySession();

        assertNotNull(session, "the sandbox copy holds no autonomy setup, so there is no railway here");

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        assertNotNull(layout, "the setup did not build, so there is nothing to run a train on");

        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);

        train = model.getLocByName(model.getLocList().get(0));

        assertNotNull(train, "there is no locomotive in the database to drive");

        trainLengthWas = train.getTrainLength();

        train.setTrainLength(TRAIN_LENGTH);

        watchdog = Executors.newSingleThreadExecutor();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (layout != null) layout.stopLocomotives();

            // PUT BACK, because `init` opens Adam's own locomotive database and the length above is
            // this class's convenience rather than a measurement of his train.
            if (train != null) train.setTrainLength(trainLengthWas == null ? 0 : trainLengthWas);
        }
        finally
        {
            if (watchdog != null) watchdog.shutdownNow();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A journey ending at the plain copy of a square trains may turn at, and what it turned into.
     */
    private static final class Turn
    {
        Point start;
        Point plain;
        TileKey square;
        Side arrival;
        List<Edge> path;
        Locomotive train;
    }

    /**
     * The turn is written to the setup even when the arrival square has never recorded a facing.
     *
     * REV9-A1 case 1.  A square only ever gets a `FACING` from `captureFromLayout`, which runs when the
     * editor is opened or the program exits - so an arrival square that no train has been captured on
     * has none, `flipFacing` has nothing to flip, and it says so by returning null.  The drain removed
     * the pending record regardless.
     *
     * MUTATION, run: pivoting the write on `getFacing(tile)` again - which is what `flipFacing` does -
     * fails this at the first assertion with `facing=null`.
     *
     * @throws Exception on a failure to build or run
     */
    @Test
    public void testATurnWritesTheFacingEvenWithNothingRecorded() throws Exception
    {
        Turn turn = aJourneyEndingInATurn();

        // NOTHING RECORDED, which is the ordinary state of a square no capture has ever run over.
        session.setFacing(turn.square, null);

        drive(turn);

        drain();

        assertEquals(session.getFacing(turn.square), turn.arrival,
            "the railway turned " + turn.train.getName() + " round at " + turn.plain.getName()
            + " and the setup was told nothing. `flipFacing` returns null when the square has no"
            + " recorded facing to flip, and the drain forgot the turn anyway - so the physical train"
            + " is reversed, the diagram says it is not, and the record that could have healed it on"
            + " the next refresh has been destroyed (REV9-A1 case 1)");

        assertStandsOnTheTurningCopy(turn);
    }

    /**
     * A stale facing does not send the turn the wrong way.
     *
     * REV9-A1 case 2.  `FACING` is never cleared - *"a square with no train on it still remembers which
     * way the last one was pointing"* - so the arrival square commonly carries the previous occupant's
     * answer, and the squares this feature exists for are exactly the ones whose occupants legitimately
     * alternate facing.  Where that stale value happens to be the POST-turn side, flipping it writes
     * the PRE-turn side as though it were the correction, and `moveOntoFacingCopy` then moves the
     * locomotive off the copy that was right.
     *
     * The stale value is seeded BEFORE the run, not after: the window refreshes off the run's own
     * completion announcement, so a value written afterwards can be overtaken by the drain it is meant
     * to be read by.
     *
     * MUTATION, run: pivoting on `getFacing(tile)` fails this with the facing left on the plain copy's
     * side and the locomotive standing on the plain copy.
     *
     * @throws Exception on a failure to build or run
     */
    @Test
    public void testAStaleFacingDoesNotWriteTheTurnBackwards() throws Exception
    {
        Turn turn = aJourneyEndingInATurn();

        // ALREADY THE POST-TURN SIDE, left behind by whoever stood here last.  At a terminus this is
        // the systematic case rather than the unlucky one.
        session.setFacing(turn.square, turn.arrival);

        drive(turn);

        drain();

        assertEquals(session.getFacing(turn.square), turn.arrival,
            "the setup's stale facing was flipped rather than the truth written, so the turn was"
            + " recorded backwards: " + turn.plain.getName() + " now reads "
            + session.getFacing(turn.square) + " where the train that turned there faces "
            + turn.arrival + ". The stored facing is stale at exactly this moment - behaviour.md 6a"
            + " says so as a rule - and flipping a stale record answers about the previous occupant"
            + " (REV9-A1 case 2)");

        assertStandsOnTheTurningCopy(turn);
    }

    /**
     * A turn the drain could not write is still owed afterwards.
     *
     * The other half of REV9-A1: the drain removed the pending record whether or not anything was
     * written, so every way of failing to write - a locomotive the running layout no longer carries, a
     * square the setup cannot answer for - destroyed the record of a turn the railway really made. The
     * comment above the removal said "Written, and only now forgotten", and it was false whenever the
     * write bailed.
     *
     * The unwritable case is manufactured rather than waited for: a name owed a turn at a Point that
     * does not exist is exactly the shape of "the write cannot be made", and nothing about it is
     * special-cased.
     *
     * MUTATION, run: removing the pending entry unconditionally - the shipped behaviour - fails this.
     *
     * @throws Exception on a reflection failure
     */
    @Test
    public void testATurnThatCouldNotBeWrittenIsNotForgotten() throws Exception
    {
        Map<String, String> owed = layout.takeReversalsOnArrival();

        try
        {
            layout.restoreReversalsOnArrival(java.util.Collections.singletonMap(
                "OB190 nobody", "OB190 no such point"));

            drain();

            assertTrue(layout.takeReversalsOnArrival().containsKey("OB190 nobody"),
                "a turn the drain could not write was forgotten anyway, so the railway's own record"
                + " of a reversal it performed on purpose is destroyed by the first refresh that"
                + " cannot apply it - and being gone, no later refresh can heal it either (REV9-A1)");
        }
        finally
        {
            layout.restoreReversalsOnArrival(owed);
        }
    }

    /**
     * A hand placement supersedes a turn the window has not written yet.
     *
     * The record is kept until it is written, which is the test above - and a record that is kept has
     * to be able to become wrong. Somebody putting the train down says where it is and which way round
     * it is, and both are newer than a pending turn; applying the turn afterwards would write the side
     * the old arrival came in by over a placement the operator chose. This is Adam's OB-183 ruling one
     * door further on: for a train whose placement the gesture was about, the gesture is the newer
     * answer.
     *
     * MUTATION, run: dropping the `reversedOnArrival.remove` from `Layout.moveLocomotive` fails this.
     *
     * @throws Exception on a failure to build or run
     */
    @Test
    public void testAHandPlacementSupersedesAnUnwrittenTurn() throws Exception
    {
        Turn turn = aJourneyEndingInATurn();

        session.setFacing(turn.square, null);

        drive(turn);

        // PUT DOWN BY HAND, on the square it set off from, before any refresh has drained anything.
        assertTrue(layout.moveLocomotive(turn.train.getName(), turn.start.getName(), false),
            "the placement this test is about was refused, so it measured nothing");

        drain();

        assertTrue(layout.takeReversalsOnArrival().isEmpty(),
            "the railway still owes a turn at a square the operator has just taken the train off, so"
            + " the next refresh writes the old arrival's side over their placement (REV9-A1)");
    }

    // ---------------------------------------------------------------- the journey

    /**
     * Puts one train on the railway with a journey ahead of it that ends where trains may turn.
     *
     * SEARCHED, not named: which square is which is a fact about Adam's layout file and the property
     * under test is not.  What the search insists on is the shape the defect needs - a square the build
     * split into exactly two copies, a plain one and a turning one, with the journey ending at the
     * PLAIN copy, because that is where a manual "no, do not keep the direction" leaves a train and is
     * the one arrival the running layout does not correct for itself.
     *
     * Every other train is taken off first.  A railway with four trains standing about refuses paths
     * for reasons that have nothing to do with this class, and the sandbox is a copy.
     *
     * @return the journey, never null - it fails rather than skipping, because a class that quietly
     *         finds no candidate is a class that asserts nothing
     * @throws Exception on a failure to search
     */
    private static Turn aJourneyEndingInATurn() throws Exception
    {
        for (Point point : new ArrayList<>(layout.getPoints()))
        {
            if (point.getCurrentLocomotive() != null) layout.moveLocomotive(null, point.getName(), true);
        }

        List<String> tried = new ArrayList<>();
        java.util.Map<String, Integer> why = new java.util.LinkedHashMap<>();

        for (Point plain : layout.getPoints())
        {
            if (plain.isReversing() || plain.isTerminus()) { count(why, "is a turning copy"); continue; }

            // THE PLAIN COPY OF A SQUARE TRAINS MAY TURN AT.  `mayReverseAt` asks about the PLACE
            // rather than the copy, which is the only way to recognise one from the runtime.
            if (!layout.mayReverseAt(plain)) { count(why, "no turning sibling"); continue; }

            TileKey square = session.getStationIndex().squareOf(plain.getName());

            if (square == null) { count(why, "no square"); continue; }

            Map<String, Side> copies = session.facingsFor(square);

            // EXACTLY TWO FACINGS, so "the other one" means something - which is the state the shipped
            // `flipFacing` needs before it will write at all, and case 2 has to reach the write.  Not
            // two COPIES: a may-reverse square with two arrival sides builds to FOUR Points on this
            // railway - plain and turning for each side - and they hold two distinct facings between
            // them, because a plain copy leaves the way the turning copy for the other side does.
            if (session.facingChoices(square).size() != 2)
            {
                count(why, "facings=" + session.facingChoices(square).size());
                continue;
            }

            for (Point start : layout.getPoints())
            {
                if (start == plain) continue;

                if (!start.isDestination()) { count(why, "start not a destination"); continue; }

                if (session.sameSquare(start.getName(), plain.getName())) continue;

                List<Edge> path = layout.bfs(start, plain, null);

                if (path == null || path.isEmpty()) { count(why, "no route"); continue; }

                String side = layout.entrySideOf(path.get(path.size() - 1), plain);

                if (side == null) { count(why, "no entry side"); continue; }

                Side arrival = sideNamed(side);

                // A COPY HAS TO HOLD THE POST-TURN FACING, or there is nowhere for the corrected train
                // to stand and the move half of this could not be asserted.  And it must not be the
                // copy the train is arriving ON, or the turn changes nothing and every assertion below
                // would pass against a program that did nothing.
                if (arrival == null || !copies.containsValue(arrival)
                    || arrival == copies.get(plain.getName()))
                {
                    count(why, "arrival " + arrival + " against copies " + copies);
                    continue;
                }

                tried.add(start.getName() + " -> " + plain.getName());

                if (!layout.moveLocomotive(train.getName(), start.getName(), false))
                {
                    count(why, "cannot place at the start");
                    continue;
                }

                if (!layout.isPathClear(path, train, false)) { count(why, "path refused"); continue; }

                Turn turn = new Turn();

                turn.start = start;
                turn.plain = plain;
                turn.square = square;
                turn.arrival = arrival;
                turn.path = path;
                turn.train = train;

                return turn;
            }
        }

        fail("no journey on this railway ends at the plain copy of a square trains may turn at, so"
            + " nothing below would have been measured. Candidates rejected after routing: " + tried
            + " and rejected before it: " + why);

        return null;
    }

    private static void count(java.util.Map<String, Integer> why, String reason)
    {
        Integer was = why.get(reason);

        why.put(reason, was == null ? 1 : was + 1);
    }

    /**
     * Drives the journey with the operator's "no, do not keep the direction", and checks it turned.
     *
     * @param turn the journey
     * @throws Exception on a failure to run
     */
    private static void drive(final Turn turn) throws Exception
    {
        Future<Boolean> leg = watchdog.submit(new Callable<Boolean>()
        {
            @Override
            public Boolean call() throws Exception
            {
                return layout.executePath(turn.path, turn.train, 30, null, TURN_IT_ROUND);
            }
        });

        try
        {
            assertTrue(leg.get(LEG_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "the journey reported failure rather than completing, so the arrival never happened");
        }
        catch (TimeoutException wedged)
        {
            layout.stopLocomotives();

            fail("the journey never arrived, so this test never reached the turn it is about");
        }

        assertNotNull(turn.plain.getCurrentLocomotive(),
            "the train is not standing at " + turn.plain.getName() + " after the run");

        assertEquals(turn.plain.getArrivedFrom(), turn.arrival.name(),
            "the arrival recorded a different side from the one the path came in by, so the"
            + " expectations below are about a journey that did not happen");
    }

    /**
     * Runs the drain the way an idle refresh does.
     *
     * @throws Exception on an event-thread failure
     */
    private static void drain() throws Exception
    {
        ui.reconcileFacingWhenIdle();

        pumpTheEventThread();
    }

    /**
     * The running layout stands the turned train on the copy for its new facing, tail and all.
     *
     * The move is the half of this that the diagram and the next dispatch read: which copy a train is
     * on IS its direction, so a setup written correctly and a running layout left on the old copy still
     * offers paths for the wrong heading.
     *
     * And the arrival side goes with it (REV9-B1).  `Point.setLocomotive` clears `arrivedFrom` on every
     * change of occupant - right for a different train, wrong for the same train being re-stood on a
     * sibling copy of one square, which is what this move is.  Turning a train does not move its
     * carriages (behaviour.md 4), so the tail is still lying the way it came in.
     *
     * @param turn the journey
     */
    private static void assertStandsOnTheTurningCopy(Turn turn)
    {
        Point standing = null;

        for (Map.Entry<String, Side> copy : session.facingsFor(turn.square).entrySet())
        {
            Point point = layout.getPoint(copy.getKey());

            if (point == null || point.getCurrentLocomotive() == null) continue;

            if (turn.train.getName().equals(point.getCurrentLocomotive().getName())) standing = point;
        }

        assertNotNull(standing, "the train is on no copy of " + turn.square + " at all");

        assertEquals(session.facingsFor(turn.square).get(standing.getName()), turn.arrival,
            "the setup was corrected and the running layout was not: " + turn.train.getName()
            + " is standing on " + standing.getName() + ", which faces "
            + session.facingsFor(turn.square).get(standing.getName()) + " and leaves the way the"
            + " train came in - so the next dispatch is offered paths for the heading it no longer"
            + " has. Which copy a train is on IS its direction");

        assertEquals(standing.getArrivedFrom(), turn.arrival.name(),
            "the train was re-stood on the copy for its new facing and lost the side it came in by,"
            + " so the track its carriages are lying across stopped being blocked for exactly the"
            + " train that turned. Turning a train does not move its tail (REV9-B1)");
    }

    private static Side sideNamed(String name)
    {
        for (Side side : Side.values())
        {
            if (side.name().equals(name)) return side;
        }

        return null;
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
}
