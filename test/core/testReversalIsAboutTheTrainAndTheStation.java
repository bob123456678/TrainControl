package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import support.Reflect;

/**
 * Reversal where the locomotive is the subject (OB-456).  The reversible flag gates terminus behavior only: a
 * reversible train may turn at a square the setup lets trains turn at and is then offered, and sent on out the other
 * end; one the setup does not let turn is not turned whatever it can do; a train that cannot reverse is neither offered
 * nor turned at such a square; the copy a turned train stands on is the one the station allows; and `flipFacing`
 * follows the train's real direction whether or not it is reversible, since reversing points are for every locomotive.
 *
 * MUTATION: ignore `isReversible` in `Layout.autonomyMayEndAt` and the offering claim fails; take the turn rule's
 * `!loc.isReversible()` out of `turnsOnArrival` and the non-reversible claim fails; swap the two ranks in
 * `copyAfterTheTurn` and the copy claim fails; make `flipFacing` decline the probe and the flip claim
 * fails (it has no reversible gate to mutate: the claim pins the ruling).
 */
public class testReversalIsAboutTheTrainAndTheStation
{
    private static final String PROBE = "reversal probe";

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        model.stop();

        assertNotNull(model.newMM2Locomotive(PROBE, 2397), "could not create this class's train");
    }

    /** A session over his railway's pages. */
    private static AutonomySession sessionOverHisRailway() throws Exception
    {
        List<LayoutDiagram> pages = new ArrayList<>();

        model.getLayoutList().forEach(name -> pages.add(model.getLayout(name)));

        AutonomySession made = new AutonomySession(sandbox.getFolder());

        made.open(pages);

        return made;
    }

    private static TileKey squareNamed(AutonomySession in, String name)
    {
        return in.getStore().getNamedTiles().stream()
            .filter(key -> name.equals(in.getStore().getPointName(key))).findFirst().orElse(null);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (model != null) model.deleteLoc(PROBE);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    private static Layout.ReversalPolicy answering(final boolean turn, final Point askedAbout)
    {
        return new Layout.ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive train, Point at)
            {
                return turn;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at == askedAbout;
            }
        };
    }

    private static Point station(Layout rail, String name, int feedback, String block, boolean turns) throws Exception
    {
        rail.createPoint(name, true, model.newFeedback(feedback, null).getName());

        Point point = rail.getPoint(name);

        point.setBlock(block);
        point.setTerminus(turns);

        return point;
    }

    private static boolean mayEndAt(Layout rail, Point end, Locomotive loc) throws Exception
    {
        return (Boolean) Reflect.method(Layout.class, "autonomyMayEndAt", Point.class, Locomotive.class)
            .invoke(rail, end, loc);
    }

    /**
     * A reversible train is offered the turning copy of a square whose only copy is the turning one, and autonomy may
     * send it there because a road leads out the other end; one that cannot reverse is offered neither.  A turning copy
     * with no road out is no place to send even a reversible train.
     *
     * MUTATION: ignore `isReversible` in `autonomyMayEndAt`, or the one-hop road test in `leavesForAStationItFits`.
     */
    @Test
    public void testAReversibleTrainIsOfferedATurnAndLeavesTheOtherEnd() throws Exception
    {
        Layout rail = new Layout(model);

        Locomotive loc = model.getLocByName(PROBE);

        Point from = station(rail, "RV_from", 3901, "RVF", false);
        Point turn = station(rail, "RV_turn", 3902, "RVT", true);
        Point out = station(rail, "RV_out", 3903, "RVO", false);
        Point stuck = station(rail, "RV_stuck", 3904, "RVS", true);

        rail.createEdge("RV_from", "RV_turn");
        rail.createEdge("RV_turn", "RV_out");
        rail.createEdge("RV_from", "RV_stuck");

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            assertTrue(rail.isOfferableToOperator(turn, loc), "a reversible train was not offered the turning copy");
            assertTrue(mayEndAt(rail, turn, loc), "autonomy may not send a reversible train to turn at a station with a"
                + " road out the other end");
            assertFalse(mayEndAt(rail, stuck, loc), "a reversible train was sent to turn where no road leads out: it"
                + " would stand there for good");

            // THE ROAD OUT THE OTHER END, from where it stands after the turn
            model.setFeedbackState(turn.getS88(), false);
            turn.setLocomotive(loc);

            boolean leaves = false;

            for (List<Edge> path : rail.getPossiblePaths(loc, false))
            {
                if (path.get(path.size() - 1).getEnd() == out) leaves = true;
            }

            assertTrue(leaves, "a reversible train standing on the turning copy found no route out the other end");

            loc.setReversible(false);

            assertFalse(mayEndAt(rail, turn, loc), "a train that cannot reverse was sent to a terminus it cannot leave");

            assertFalse(rail.isOfferableToOperator(turn, loc), "a train that cannot reverse was offered a turning copy"
                + " no copy of its square faces on from");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * Turning is the setup's say, not the locomotive's: a reversible train is turned at a square the operator says to
     * turn at and kept straight when told keep direction, and a square the setup lets nobody turn at never turns it,
     * whatever answer is offered; a train that cannot reverse is not turned at the optional square even if told to.
     *
     * MUTATION: drop `!loc.isReversible()` from `turnsOnArrival`, or the `mayReverseAt` clause of `shouldReverseAt`.
     */
    @Test
    public void testTurningIsTheSetupsCallAndTheTrainsAbility() throws Exception
    {
        Layout rail = new Layout(model);

        Locomotive loc = model.getLocByName(PROBE);

        Point approach = station(rail, "TR_from", 3911, "TRF", false);
        Point turning = station(rail, "TR_turn", 3912, "TRX", false);
        Point plain = station(rail, "TR_plain", 3913, "TRX", false);
        Point never = station(rail, "TR_never", 3914, "TRN", false);

        turning.setReversing(true);

        rail.createEdge("TR_from", "TR_turn");
        rail.createEdge("TR_from", "TR_plain");
        rail.createEdge("TR_from", "TR_never");

        assertTrue(rail.mayReverseAt(plain), "precondition: the square does not let trains turn");
        assertFalse(rail.mayReverseAt(never), "precondition: the other square lets trains turn");

        boolean was = loc.isReversible();

        try
        {
            loc.setReversible(true);

            assertTrue(rail.turnsOnArrival(turning, loc, answering(true, turning)), "a reversible train told to turn at"
                + " a square the setup lets it turn at was not turned");
            assertFalse(rail.turnsOnArrival(turning, loc, answering(false, turning)), "a reversible train told to keep"
                + " direction was turned");
            assertFalse(rail.turnsOnArrival(never, loc, answering(true, turning)), "a reversible train was turned at a"
                + " square the setup does not let trains turn at, because an answer said so");

            loc.setReversible(false);

            assertFalse(rail.turnsOnArrival(turning, loc, answering(true, turning)), "a train that cannot reverse was"
                + " turned at a square where turning is optional");
            assertFalse(rail.turnsOnArrival(never, loc, answering(true, turning)), "a train that cannot reverse was turned"
                + " at a square nobody turns at");
        }
        finally
        {
            loc.setReversible(was);
        }
    }

    /**
     * The copy a turned train stands on faces the way it came in and is one the station allows: a copy trains may
     * arrive at over a barred one, and a plain one over a turning one when both are allowed.
     *
     * MUTATION: swap the destination and turning weights in `copyAfterTheTurn`, or take the first copy facing that way.
     */
    @Test
    public void testTheTurnedTrainStandsOnTheCopyTheStationAllows() throws Exception
    {
        Layout rail = new Layout(model);

        Point arrived = station(rail, "CP_east", 3921, "CPX", false);
        Point barred = station(rail, "CP_barred", 3922, "CPX", false);
        Point turning = station(rail, "CP_turn", 3923, "CPX", true);

        arrived.setCopyFacing("E");
        barred.setCopyFacing("W");
        turning.setCopyFacing("W");

        barred.setDestination(false);

        java.lang.reflect.Method choose = Reflect.method(Layout.class, "copyAfterTheTurn", Point.class, String.class);

        assertSame(choose.invoke(rail, arrived, "W"), turning, "the train stood on a copy trains may not arrive at, when"
            + " a copy they may arrive at faces the same way");

        // A plain copy trains may arrive at is preferred to a turning one
        Point allowed = station(rail, "CP_plain", 3924, "CPX", false);

        allowed.setCopyFacing("W");

        assertSame(choose.invoke(rail, arrived, "W"), allowed, "the train stood on a turning copy when a plain copy it may"
            + " arrive at faces the same way");
    }

    /**
     * `flipFacing` turns the train in the setup and on the railway whether or not it can reverse: the throttle's
     * direction change is a fact about the decoder, and only terminus behavior is the reversible flag's (the owner's
     * ruling; the brief's "a non-reversible one isn't turned" yields to it).
     *
     * MUTATION: have `flipFacing` return early for a locomotive that is not reversible.
     */
    @Test
    public void testFlipFacingTurnsTheTrainWhetherOrNotItCanReverse() throws Exception
    {
        Locomotive loc = model.getLocByName(PROBE);

        boolean was = loc.isReversible();

        try
        {
            for (boolean reversible : new boolean[] {true, false})
            {
                loc.setReversible(reversible);

                AutonomySession session = sessionOverHisRailway();

                TileKey mainA = squareNamed(session, "BottomMainA");

                assertNotNull(mainA, "his railway has no BottomMainA");

                model.parseAuto(session.buildConfiguration());

                Layout running = model.getAutoLayout();

                assertNotNull(running, "the setup did not build: " + Layout.getLastError());

                session.setRunningLayoutSource(() -> running);

                Point east = null;

                for (Map.Entry<String, Side> copy : session.facingsFor(mainA).entrySet())
                {
                    Point point = running.getPoint(copy.getKey());

                    if (copy.getValue() == Side.E && point != null && point.isDestination()) east = point;
                }

                assertNotNull(east, "precondition: no station copy faces east");
                assertTrue(running.moveLocomotive(PROBE, east.getName(), false), "could not stand the train");

                session.placeLocomotive(mainA, PROBE);
                session.setFacing(mainA, Side.E);

                assertEquals(session.flipFacing(PROBE, running), mainA, "a train with reversible=" + reversible
                    + " was not followed");
                assertEquals(session.getFacing(mainA), Side.W, "the setup did not record the turn, reversible="
                    + reversible);

                Point standing = null;

                for (Point p : running.getPoints())
                {
                    if (p.getCurrentLocomotive() != null && PROBE.equals(p.getCurrentLocomotive().getName())) standing = p;
                }

                assertNotNull(standing, "the flip took the train off the railway");
                assertEquals(session.facingsFor(mainA).get(standing.getName()), Side.W, "the railway's train was not"
                    + " turned with the setup, reversible=" + reversible);
            }
        }
        finally
        {
            loc.setReversible(was);
        }
    }
}
