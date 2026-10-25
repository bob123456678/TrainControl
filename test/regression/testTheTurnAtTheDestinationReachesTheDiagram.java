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
 * OB-190 / OB-189.  Adam: he sends a train to a square trains may turn at, answers **"no, do not keep the direction"**,
 * the locomotive physically turns, and the diagram goes on drawing it facing the way it set off.
 *
 * `TrainControlUI.reconcileFacingWhenIdle`, the drain that carries a destination turn from the railway into the setup,
 * was reached by no executed test: `testADirectionChangeIsNotSwallowed` reads `TrainControlUI.java` as a STRING, so the
 * statement it proves is present is the one the defect lives in.  This class runs a real journey on Adam's own railway
 * (sandbox copy, OB-111), turns the train at its destination the way the manual door does, drains, and asks the SETUP
 * and the RUNNING LAYOUT what they now believe.
 *
 * Two ways it went wrong, which are the two journeys below.  Both come from the drain pivoting on
 * `AutonomySession.getFacing(tile)`, the setup's STORED facing for the arrival square, which behaviour.md 6a declares
 * stale at that moment (and `captureFromLayout` never clears it, so an empty square carries the last occupant's
 * answer):
 *
 * 1. The turn is lost.  No stored facing means `flipFacing` returns null, and the drain removed the pending record
 *    anyway, so no later refresh can heal it.
 * 2. The turn is written backwards.  A stored facing that is the POST-turn side (the systematic case at a terminus)
 * flips
 *    to the PRE-turn side, and `moveOntoFacingCopy` stands the locomotive on the copy for the wrong facing.
 *
 * What is reliable at that moment is behaviour.md 4: a train faces the way it will leave, and once turned round the two
 * point the same way while the carriages have not moved.  So the facing of a train that has just turned at its
 * destination IS the side it arrived by, which the arrival wrote down itself: an absolute answer that needs no record
 * in sync.  The third test is the other half: a turn the drain could NOT write must still be owed.
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
     * Short enough for the track-room rule (behaviour.md 5a) at the may-reverse platform, which measures one unit on
     * the run in; the real length is restored in the teardown, as `init` opens Adam's own locomotive database.
     */
    private static final int TRAIN_LENGTH = 1;

    /** How long one leg may take before it is called wedged rather than waited for. */
    private static final int LEG_TIMEOUT_SECONDS = 90;

    /** What the fixture's locomotive said about reversing before this class changed it. */
    private static boolean reversibleWas;

    /**
     * The manual door's answer to "no, do not keep the direction": `asksAbout` is true everywhere (it answers about the
     * railway, not the answer) and `shouldReverse` carries the decision, or `shouldReverseAt` exits at the plain copy
     * and the train never turns.
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
        // THE FROZEN COPY, NOT THE RAILWAY HE IS OPERATING (OB-111, `test/layouts/live-snapshot`): the window opens
        // whatever the layout preference names, which on Adam's machine is his real railway.  The SHAPE is what this
        // class is about; where his trains stand and how long they are move, and a fixture that moves makes every class
        // over it say something different every week.
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        // showUI, because `reconcileFacingWhenIdle` is a method of the window; debug on, because
        // `Layout.setSimulate(true)` refuses outside debug mode.
        model = support.TestStation.simulatedWithWindowInDebug();
        model.setNetworkCommState(false);
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window, so the drain this class is about cannot be reached");

        // THE WINDOW HAS TO HAVE FINISHED STARTING: `init` posts `display()` to the event thread without waiting, and
        // the start-up is still building the session that everything below rebuilds (two threads in
        // `AutonomySession.rebuild`).
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

        assertNotNull(train, "this fixture has no locomotive to drive");

        // AND IT HAS TO BE ABLE TO TURN, or there is no turn to record (MT-368).  A train that cannot reverse is never
        // turned where turning is OPTIONAL, so the first locomotive in the database (not reversible) could not be
        // turned at BottomMainB and `TURN_IT_ROUND` would describe a state the interface cannot produce.  Saved and put
        // back in the teardown, as the locomotive database is shared with the rest of the suite.
        reversibleWas = train.isReversible();

        train.setReversible(true);

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

            // PUT BACK: `init` opens Adam's own locomotive database, and the length above is this class's convenience.
            if (train != null) train.setTrainLength(trainLengthWas == null ? 0 : trainLengthWas);

            // And the reversing flag, made true so there would be a turn to record (MT-368).
            if (train != null) train.setReversible(reversibleWas);
        }
        finally
        {
            if (watchdog != null) watchdog.shutdownNow();

            if (sandbox != null) sandbox.close();
        }
    }

    /** A journey ending at the plain copy of a square trains may turn at, and what it turned into. */
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
     * Case 1.  A square only gets a `FACING` from `captureFromLayout`, which runs when the editor is opened or the
     * program exits, so an arrival square no train was captured on has none, and `flipFacing` returns null.
     *
     * MUTATION, run: pivoting the write on `getFacing(tile)` again - which is what `flipFacing` does -
     * fails this at the first assertion with `facing=null`.
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
     * Case 2.  `FACING` is never cleared, so the arrival square commonly carries the previous occupant's answer, and
     * these squares are exactly the ones whose occupants alternate facing.  Where that stale value is the POST-turn
     * side, flipping it writes the PRE-turn side as the correction, and `moveOntoFacingCopy` moves the locomotive off
     * the copy that was right.
     *
     * The stale value is seeded BEFORE the run: the window refreshes off the run's own completion announcement, so a
     * value written afterwards can be overtaken by the drain.
     *
     * MUTATION, run: pivoting on `getFacing(tile)` fails this with the facing left on the plain copy's
     * side and the locomotive standing on the plain copy.
     */
    @Test
    public void testAStaleFacingDoesNotWriteTheTurnBackwards() throws Exception
    {
        Turn turn = aJourneyEndingInATurn();

        // ALREADY THE POST-TURN SIDE, left behind by whoever stood here last (the systematic case at a terminus).
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
     * The other half: the drain removed the pending record whether or not anything was written, so every way of failing
     * to write destroyed the record of a turn the railway really made.  The unwritable case is manufactured: a name
     * owed a turn at a Point that does not exist is exactly the shape of "the write cannot be made".
     *
     * MUTATION, run: removing the pending entry unconditionally - the shipped behavior - fails this.
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
     * A kept record has to be able to become wrong: somebody putting the train down says where it is and which way
     * round, both newer than a pending turn, and applying the turn afterwards would write over a placement the operator
     * chose (Adam's OB-183 ruling one door further on: for a train whose placement the gesture was about, the gesture
     * is the newer answer).
     *
     * MUTATION, run: dropping the `reversedOnArrival.remove` from `Layout.moveLocomotive` fails this.
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
     * Searched, not named: the search insists on a square the build split into exactly two copies, a plain one and a
     * turning one, with the journey ending at the PLAIN copy, which is where a manual "no, do not keep the direction"
     * leaves a train and the one arrival the running layout does not correct for itself.  Every other train is taken
     * off first.  It fails rather than skipping, since a class that quietly finds no candidate asserts nothing.
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
            // rather than the copy, which is the only way to recognize one from the runtime.
            if (!layout.mayReverseAt(plain)) { count(why, "no turning sibling"); continue; }

            TileKey square = session.getStationIndex().squareOf(plain.getName());

            if (square == null) { count(why, "no square"); continue; }

            Map<String, Side> copies = session.facingsFor(square);

            // EXACTLY TWO FACINGS, so "the other one" means something, which `flipFacing` needs before it writes at
            // all.  Not two COPIES: a may-reverse square with two arrival sides builds to FOUR Points here, holding two
            // facings.
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

                // A COPY HAS TO HOLD THE POST-TURN FACING, or there is nowhere for the corrected train to stand, and it
                // must not be the copy the train is arriving ON, or the turn changes nothing.
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

    /** Drives the journey with the operator's "no, do not keep the direction", and checks it turned. */
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

        // ON A COPY OF THE SQUARE, already the one for its new facing: the arrival stands a turned train there itself.
        Point standing = standingOn(turn);

        assertNotNull(standing, "the train is on no copy of " + turn.square + " after the run");

        assertEquals(standing.getArrivedFrom(), turn.arrival.name(),
            "the arrival recorded a different side from the one the path came in by, so the"
            + " expectations below are about a journey that did not happen");
    }

    /** The copy of the journey's square the train stands on, or null. */
    private static Point standingOn(Turn turn)
    {
        for (Map.Entry<String, Side> copy : session.facingsFor(turn.square).entrySet())
        {
            Point point = layout.getPoint(copy.getKey());

            if (point != null && point.getCurrentLocomotive() != null
                && turn.train.getName().equals(point.getCurrentLocomotive().getName())) return point;
        }

        return null;
    }

    /** Runs the drain the way an idle refresh does. */
    private static void drain() throws Exception
    {
        ui.reconcileFacingWhenIdle();

        pumpTheEventThread();
    }

    /**
     * The running layout stands the turned train on the copy for its new facing, tail and all.
     *
     * The move is the half the diagram and the next dispatch read: which copy a train is on IS its direction.  The
     * arrival side goes with it: `Point.setLocomotive` clears `arrivedFrom` on every change of occupant, which is wrong
     * for the same train re-stood on a sibling copy, since turning does not move the carriages (behaviour.md 4).
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

        // AND THE ROUTE IT DROVE: a train driven here follows that route back past a junction (Adam, MT-335: "Follow
        // its last route"), and this re-stand carried the side and not the route.
        assertNotNull(standing.getArrivedAlong(),
            "the train drove here, turned, and was re-stood on the copy for its new facing WITHOUT the"
            + " route it arrived along - so its tail stops at the first junction behind it instead of"
            + " following the road it came in on (TDR-B2)");
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
