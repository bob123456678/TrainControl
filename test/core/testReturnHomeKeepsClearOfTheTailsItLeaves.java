package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A Return Home plan never sends a train over a tail an earlier move of the same plan left behind (OB-228). The planner
 * modeled only the tails of trains that had not moved yet; a train it has moved has a known road, the route the plan
 * gave it, and its tail lies back along that route.
 *
 * The shape, on sensors of its own: both trains' routes home share T -> P. Train A comes from Y and parks at A, three
 * units long behind a two-unit approach, so its tail lies back over T -> P. Train B comes from X and goes on past P to
 * its home H. Moved in that order B runs into A's tail; the other way round, both get home.
 *
 * Asked the way the railway would: each move is driven onto the layout as a run leaves a train, and before each move
 * the edges the trains already standing cover are asked of `Layout.edgesCoveredByStandingTrains`, the runtime's own
 * walk.
 *
 * @author Adam
 */
public class testReturnHomeKeepsClearOfTheTailsItLeaves
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static Locomotive trainA;
    private static Locomotive trainB;
    private static Locomotive trainC;
    private static Integer lengthA;
    private static Integer lengthB;
    private static Integer lengthC;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model: `init` reads the machine-global layout preference (OB-111).
        sandbox = support.LayoutSandbox.open();
        model = support.TestStation.simulated();

        assertTrue(model.getLocList().size() >= 3, "precondition: fewer than three locomotives to plan with");

        trainA = model.getLocByName(model.getLocList().get(0));
        trainB = model.getLocByName(model.getLocList().get(1));
        trainC = model.getLocByName(model.getLocList().get(2));
        lengthA = trainA.getTrainLength();
        lengthB = trainB.getTrainLength();
        lengthC = trainC.getTrainLength();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (trainA != null) trainA.setTrainLength(lengthA);
            if (trainB != null) trainB.setTrainLength(lengthB);
            if (trainC != null) trainC.setTrainLength(lengthC);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** Every move of the plan is clear of the tails of the trains the plan has already put down. */
    @Test
    public void testNoMoveRunsOverATailAnEarlierMoveLeft() throws Exception
    {
        Layout layout = twoRoutesSharingARun(2440);

        trainA.setTrainLength(3);
        trainB.setTrainLength(1);

        assertTrue(layout.moveLocomotive(trainA.getName(), "RT_Y", false), "could not stand train A at RT_Y");
        assertTrue(layout.moveLocomotive(trainB.getName(), "RT_X", false), "could not stand train B at RT_X");

        layout.setHomeLocomotive("RT_A", trainA.getName());
        layout.setHomeLocomotive("RT_H", trainB.getName());

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "precondition: no plan brings both trains home, though moving B first does: " + plan.getMoves());

        List<String> run = replay(layout, plan);

        assertTrue(trainA.equals(layout.getPoint("RT_A").getCurrentLocomotive())
            && trainB.equals(layout.getPoint("RT_H").getCurrentLocomotive()),
            "precondition: replaying the plan did not bring both trains home: " + run);
    }

    /**
     * A plan found only one way round is found, though the other way round reaches the same squares first: an
     * arrangement was keyed by where the trains stand, so two orders reaching the same squares with different tails
     * were one arrangement. X has two roads home (the short one past SY, whose tail lies over SY -> P, and a long one);
     * Z needs SX and SY empty and SY -> P clear. So the only order that brings all three home is X, Y, Z, and Y, X
     * reaches the same squares with X's tail across Z's road.
     */
    @Test
    public void testAnOrderThatOnlyWorksOneWayRoundIsFound() throws Exception
    {
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        String[] names = { "RH_SY", "RH_SX", "RH_SZ", "RH_P", "RH_L1", "RH_L2", "RH_L3", "RH_HX", "RH_HY", "RH_HZ" };
        boolean[] stations = { true, true, true, false, false, false, false, true, true, true };

        for (int i = 0; i < names.length; i++)
        {
            layout.createPoint(names[i], stations[i], model.newFeedback(2460 + i, null).getName());
        }

        String[][] rails = { { "RH_SZ", "RH_SX" }, { "RH_SX", "RH_SY" }, { "RH_SY", "RH_P" }, { "RH_P", "RH_HX" },
            { "RH_P", "RH_HY" }, { "RH_P", "RH_HZ" }, { "RH_SX", "RH_L1" }, { "RH_L1", "RH_L2" }, { "RH_L2", "RH_L3" },
            { "RH_L3", "RH_HX" } };

        for (String[] rail : rails)
        {
            layout.createEdge(rail[0], rail[1]);
            layout.getEdge(rail[0], rail[1]).setLength(1);
        }

        for (String home : new String[] { "RH_HX", "RH_HY", "RH_HZ" })
        {
            for (Edge in : layout.getIncomingEdges(layout.getPoint(home))) in.setEntrySide("W");
        }

        trainA.setTrainLength(3);
        trainB.setTrainLength(1);
        trainC.setTrainLength(1);

        assertTrue(layout.moveLocomotive(trainA.getName(), "RH_SX", false), "could not stand X at RH_SX");
        assertTrue(layout.moveLocomotive(trainB.getName(), "RH_SY", false), "could not stand Y at RH_SY");
        assertTrue(layout.moveLocomotive(trainC.getName(), "RH_SZ", false), "could not stand Z at RH_SZ");

        layout.setHomeLocomotive("RH_HX", trainA.getName());
        layout.setHomeLocomotive("RH_HY", trainB.getName());
        layout.setHomeLocomotive("RH_HZ", trainC.getName());

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "X long, then Y, then Z brings all three home, and the search answered " + plan.getOutcome() + ": moving Y"
            + " first reaches the same squares with X's tail across Z's road, and the search kept only that one (MFR-B2)."
            + "  Moves: " + plan.getMoves());

        List<String> run = replay(layout, plan);

        assertTrue(trainA.equals(layout.getPoint("RH_HX").getCurrentLocomotive())
            && trainB.equals(layout.getPoint("RH_HY").getCurrentLocomotive())
            && trainC.equals(layout.getPoint("RH_HZ").getCurrentLocomotive()),
            "replaying the plan did not bring all three home: " + run);
    }

    /**
     * The retry from the start still has time when the first search spends its share (OB-228's second search, for the
     * arrangement the greedy pass boxes itself into). The X, Y, Z railway of the claim above needs the retry; sixty
     * sidings Z can wander along keep the first search from running out of arrangements before its time does; the
     * search's clock is stepped 300 ms a read, so the fifteen-second budget is fifty reads.
     */
    @Test
    public void testTheRetryFromTheStartHasTimeLeft() throws Exception
    {
        // The order the greedy pass meets the trains is the order of Layout's point map, a HashMap by name, and this
        // claim needs Y met before X so that the greedy pass boxes itself in. The names are chosen for that (String
        // hash, 128 buckets for these seventy points); the "precondition" below says so if a change lets the greedy
        // pass succeed.
        Layout layout = new Layout(model);

        layout.setDefaultLocSpeed(30);

        String[] names = { "RS_SYA", "RS_SXA", "RS_SZ", "RS_P", "RS_L1", "RS_L2", "RS_L3", "RS_HX", "RS_HY", "RS_HZ" };
        boolean[] stations = { true, true, true, false, false, false, false, true, true, true };

        for (int i = 0; i < names.length; i++)
        {
            layout.createPoint(names[i], stations[i], model.newFeedback(2500 + i, null).getName());
        }

        String[][] rails = { { "RS_SZ", "RS_SXA" }, { "RS_SXA", "RS_SYA" }, { "RS_SYA", "RS_P" }, { "RS_P", "RS_HX" },
            { "RS_P", "RS_HY" }, { "RS_P", "RS_HZ" }, { "RS_SXA", "RS_L1" }, { "RS_L1", "RS_L2" }, { "RS_L2", "RS_L3" },
            { "RS_L3", "RS_HX" } };

        for (String[] rail : rails)
        {
            layout.createEdge(rail[0], rail[1]);
            layout.getEdge(rail[0], rail[1]).setLength(1);
        }

        // The sidings: a line of stations off SZ, none of them anybody's home.
        int sidings = 60;

        for (int i = 0; i < sidings; i++)
        {
            layout.createPoint(String.format("RS_Q%02d", i), true, model.newFeedback(2520 + i, null).getName());
        }

        layout.createEdge("RS_SZ", "RS_Q00");
        layout.getEdge("RS_SZ", "RS_Q00").setLength(1);

        for (int i = 0; i + 1 < sidings; i++)
        {
            String from = String.format("RS_Q%02d", i);
            String to = String.format("RS_Q%02d", i + 1);

            layout.createEdge(from, to);
            layout.getEdge(from, to).setLength(1);
        }

        for (String home : new String[] { "RS_HX", "RS_HY", "RS_HZ" })
        {
            for (Edge in : layout.getIncomingEdges(layout.getPoint(home))) in.setEntrySide("W");
        }

        trainA.setTrainLength(3);
        trainB.setTrainLength(1);
        trainC.setTrainLength(1);

        assertTrue(layout.moveLocomotive(trainA.getName(), "RS_SXA", false), "could not stand X at RS_SX");
        assertTrue(layout.moveLocomotive(trainB.getName(), "RS_SYA", false), "could not stand Y at RS_SY");
        assertTrue(layout.moveLocomotive(trainC.getName(), "RS_SZ", false), "could not stand Z at RS_SZ");

        layout.setHomeLocomotive("RS_HX", trainA.getName());
        layout.setHomeLocomotive("RS_HY", trainB.getName());
        layout.setHomeLocomotive("RS_HZ", trainC.getName());

        HomeStaging staging = HomeStaging.snapshot(layout);

        // A clock that moves 300 ms every time the search looks at it.
        final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();

        java.lang.reflect.Field clock = HomeStaging.class.getDeclaredField("clock");

        clock.setAccessible(true);
        clock.set(staging, (java.util.function.LongSupplier) () -> now.addAndGet(300));

        HomeStaging.Plan plan = staging.plan();

        // NOT VACUOUS: more than the first search's share was read (a third of the budget since OB-230), so the greedy
        // pass did not simply succeed and the retry ran.
        assertTrue(now.get() > 5000, "precondition: the search read the clock for only " + now.get() + " ms, so the greedy"
            + " pass brought everybody home and neither search ran - this claim is about the second one");

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "the first search spent the time and the retry from the start - which finds X long, Y, Z - had none left:"
            + " NO_PLAN_FOUND after " + now.get() + " ms on the search's clock (MFV-B1).  Moves: " + plan.getMoves());

        List<String> run = replay(layout, plan);

        assertTrue(trainA.equals(layout.getPoint("RS_HX").getCurrentLocomotive())
            && trainB.equals(layout.getPoint("RS_HY").getCurrentLocomotive())
            && trainC.equals(layout.getPoint("RS_HZ").getCurrentLocomotive()),
            "replaying the plan did not bring all three home: " + run);
    }

    /**
     * A road that shares metal with a parked train's tail is refused, and the build's places say when it does not.
     * `passesTheTailsOfTrainsItHasMoved` has two arms: the edge the mover wants IS one the tail covers (pinned above),
     * and an edge the tail does not cover but which is LOCKED against one it does, the model's way of saying "the same
     * piece of metal" (OB-073's shape, as OB-228 was).
     *
     * The refinement: where BOTH edges carry places from the build, the lock alone is not enough; the tail has to lie
     * on a place the mover's edge runs over, or the planner is stricter than its railway. So three arrangements are
     * asserted, differing in nothing else: the lock with no places refuses, the lock with places the tail is not on
     * allows, the lock with the place the tail IS on refuses again. The railway forces the order: A starts on B's home,
     * so A moves first, and its three-unit tail over `RS_T -> RS_HA` is on B's only road home, `RS_P -> RS_Y`, unless
     * the shared metal is not modeled.
     *
     * MUTATION, both run: dropping the lock-edge loop from `passesTheTailsOfTrainsItHasMoved` fails the first
     * arrangement. Dropping the `tailLiesOn` continue instead fails all four claims in this class, the control first:
     * `createEdge` locks edges that share a point, so without the refinement a tail anywhere closes every road that
     * touches its own.
     */
    @Test
    public void testARoadSharingMetalWithAParkedTailIsRefused() throws Exception
    {
        trainA.setTrainLength(3);
        trainB.setTrainLength(1);

        // The control first: the same railway with the shared metal not declared, or every refusal below could be the
        // fixture being unsolvable rather than the rule under test.
        assertEquals(planWithBothTrains(twoRoadsOverOneBridge(2460, false, null, null)).getOutcome(),
            HomeStaging.Outcome.READY,
            "control: with no lock between the two roads there is no plan home at all, so nothing below shows the"
            + " lock-edge rule doing anything");

        // 1. The lock, with no places: the build said these two edges are one piece of metal and said nothing about
        // squares, so a tail on either closes both.
        assertEquals(planWithBothTrains(twoRoadsOverOneBridge(2470, true, null, null)).getOutcome(),
            HomeStaging.Outcome.NO_PLAN_FOUND,
            "the plan sends B over metal A's tail is lying on: the two roads are locked against each other and only"
            + " the edge itself was being checked, so the railway would refuse the move the plan promises (AMH-C2)");

        // 2. The lock, with places the tail is not on: locked at one end, the tail at the other. Refusing this makes
        // the planner stricter than the runtime (OB-073).
        assertEquals(planWithBothTrains(twoRoadsOverOneBridge(2480, true, "RS_bridge", "RS_elsewhere")).getOutcome(),
            HomeStaging.Outcome.READY,
            "both roads name the squares they run over and A's tail is on neither of B's, so the lock alone refused a"
            + " move the railway allows");

        // 3. The lock, with the place the tail is on: the refinement must not swallow the rule it narrows.
        assertEquals(planWithBothTrains(twoRoadsOverOneBridge(2490, true, "RS_bridge", "RS_bridge")).getOutcome(),
            HomeStaging.Outcome.NO_PLAN_FOUND,
            "B's road runs over the very square A's tail is standing on and the plan sent it anyway, so the places"
            + " refinement has swallowed the rule it was meant to narrow");
    }

    /**
     * The tail of a train that never moves closes the roads over its metal, locked or not (OB-184). Here A is already
     * home and never moves, so its tail is the one it left at the START of the plan, and the only check that sees it is
     * `liesAcrossAtStart`, through `Layout.tailLiesOn`, once for the lock partner (the lock, with places on both edges)
     * and once for the other copies of the same metal (no lock at all). The other callers of `tailLiesOn` are covered
     * by `anotherTailOn`.
     *
     * The railway as above, with A standing at `RS_HA` from the start. B's only road home runs over `RS_P -> RS_Y`,
     * which shares the square `RS_bridge` with A's `RS_T -> RS_HA`. The control puts the squares apart, and is READY.
     *
     * MUTATION: make `Layout.tailLiesOn` return false. Both refusals go READY (the planner offers a move
     * `Layout.isPathClear` refuses at dispatch, OB-073's shape), and the control stays READY.
     */
    @Test
    public void testATailAStandingTrainHasNotLeftClosesTheRoadsOverItsMetal() throws Exception
    {
        trainA.setTrainLength(3);
        trainB.setTrainLength(1);

        // The control first: the same two roads, the squares apart, or a refusal below could be the fixture being
        // unsolvable rather than the tail being seen.
        assertEquals(planWithATrainAlreadyHome(twoRoadsOverOneBridge(2500, true, "RS_bridge", "RS_elsewhere")).getOutcome(),
            HomeStaging.Outcome.READY,
            "control: A's tail is on neither of B's squares, so B goes home and nothing below shows the tail being seen");

        // Both refusals are asked before either is asserted, so a failure names every one that was missed.
        HomeStaging.Outcome locked = planWithATrainAlreadyHome(
            twoRoadsOverOneBridge(2510, true, "RS_bridge", "RS_bridge")).getOutcome();
        HomeStaging.Outcome unlocked = planWithATrainAlreadyHome(
            twoRoadsOverOneBridge(2520, false, "RS_bridge", "RS_bridge")).getOutcome();

        String missed = "";

        // The lock, with places on both edges: the tail has to lie on a place B's edge runs over.
        if (locked != HomeStaging.Outcome.NO_PLAN_FOUND)
        {
            missed += " The lock partner's tail was not seen (OB-184): " + locked + ".";
        }

        // No lock at all: the same two roads are two copies of one piece of metal only by their places.
        if (unlocked != HomeStaging.Outcome.NO_PLAN_FOUND)
        {
            missed += " The tail was not seen by place (AUT-B1): " + unlocked + ".";
        }

        assertEquals(missed, "", "B's road runs over the square the tail of A, standing at home from the start, is lying"
            + " on, and the plan sent it anyway:");
    }

    /**
     * Stands A at its home with its tail behind it, stands B, homes both, and plans; A is placed as a run leaves a
     * train (moved, the side it came in by and the road it came along recorded), so its tail is the one the runtime
     * walks.
     */
    private static HomeStaging.Plan planWithATrainAlreadyHome(Layout layout) throws Exception
    {
        assertTrue(layout.moveLocomotive(trainA.getName(), "RS_HA", false), "could not stand train A at RS_HA");
        assertTrue(layout.moveLocomotive(trainB.getName(), "RS_X", false), "could not stand train B at RS_X");

        Edge intoHome = layout.getEdge("RS_T", "RS_HA");
        Point home = layout.getPoint("RS_HA");

        home.setArrivedFrom(layout.entrySideOf(intoHome, home));
        home.setArrivedAlong(java.util.Collections.singletonList(intoHome));

        layout.setHomeLocomotive("RS_HA", trainA.getName());
        layout.setHomeLocomotive("RS_Y", trainB.getName());

        return HomeStaging.snapshot(layout).plan();
    }

    /** Stands A and B where the fixture wants them, homes them, and plans. */
    private static HomeStaging.Plan planWithBothTrains(Layout layout) throws Exception
    {
        assertTrue(layout.moveLocomotive(trainA.getName(), "RS_Y", false), "could not stand train A at RS_Y");
        assertTrue(layout.moveLocomotive(trainB.getName(), "RS_X", false), "could not stand train B at RS_X");

        layout.setHomeLocomotive("RS_HA", trainA.getName());
        layout.setHomeLocomotive("RS_Y", trainB.getName());

        return HomeStaging.snapshot(layout).plan();
    }

    /**
     * A's road Y -> T -> HA and B's road X -> P -> Y, with `T -> HA` and `P -> Y` optionally declared as one piece of
     * metal. A is three units long and the approach into HA measures two, so its tail lies back over `T -> HA`.
     * `aPlace` is the place `T -> HA` runs over BEHIND the berth (an edge's places are path-ordered with the square it
     * arrives at last, so the berth is named separately), or null for a build that named none; `bPlace` is the
     * corresponding place on `P -> Y`.
     */
    private static Layout twoRoadsOverOneBridge(int s88, boolean locked, String aPlace, String bPlace)
        throws Exception
    {
        Layout built = new Layout(model);

        built.setDefaultLocSpeed(30);

        String[] names = { "RS_Y", "RS_T", "RS_HA", "RS_X", "RS_P" };

        // RS_T and RS_P are junctions, so neither train can be parked on one and wait the other out, which would give
        // the search an order that never puts a tail on the bridge.
        boolean[] stations = { true, false, true, true, false };

        for (int i = 0; i < names.length; i++)
        {
            built.createPoint(names[i], stations[i], model.newFeedback(s88 + i, null).getName());
        }

        String[][] rails = { { "RS_Y", "RS_T" }, { "RS_T", "RS_HA" }, { "RS_X", "RS_P" }, { "RS_P", "RS_Y" } };

        int[] lengths = { 1, 2, 1, 1 };

        for (int i = 0; i < rails.length; i++)
        {
            built.createEdge(rails[i][0], rails[i][1]);
            built.getEdge(rails[i][0], rails[i][1]).setLength(lengths[i]);
        }

        Edge tailLiesHere = built.getEdge("RS_T", "RS_HA");
        Edge bMustPass = built.getEdge("RS_P", "RS_Y");

        tailLiesHere.setEntrySide("W");
        bMustPass.setEntrySide("W");

        if (locked)
        {
            tailLiesHere.addLockEdge(bMustPass);
            bMustPass.addLockEdge(tailLiesHere);
        }

        // Two places each, because a tail's first square is the berth it stands on: a single place IS that berth, the
        // walk spends it, and nothing further back is ever claimed.
        if (aPlace != null)
        {
            tailLiesHere.setPlaces(java.util.Arrays.asList(aPlace, "RS_berth"), java.util.Arrays.asList(2, 1));
        }

        if (bPlace != null)
        {
            bMustPass.setPlaces(java.util.Arrays.asList(bPlace, "RS_home"), java.util.Arrays.asList(1, 1));
        }

        return built;
    }

    /**
     * Drives each move of the plan onto the layout as a run leaves a train, failing on a move over another train's
     * tail.
     */
    private static List<String> replay(Layout layout, HomeStaging.Plan plan) throws Exception
    {
        List<String> run = new ArrayList<>();

        for (HomeStaging.Move move : plan.getMoves())
        {
            Map<Edge, Locomotive> covered = layout.edgesCoveredByStandingTrains();

            for (Edge leg : move.getPath())
            {
                Locomotive lyingAcross = covered.get(leg);

                // The message is built only on a failure: it names the train lying there, which is null otherwise.
                if (lyingAcross != null && !lyingAcross.equals(move.getLocomotive()))
                {
                    fail("the plan sends " + move.getLocomotive().getName() + " over " + leg + ", where " + lyingAcross.getName()
                        + " - put down by an earlier move of the same plan - is lying.  The runtime refuses it, which is"
                        + " MT-335's 'the path stayed blocked'.  Moves so far: " + run + "; the plan: " + plan.getMoves());
                }
            }

            // Driven onto the layout as a run leaves a train: moved, the side it came in by, and the road it came
            // along.
            Point end = move.getEnd();
            Edge last = move.getPath().get(move.getPath().size() - 1);

            assertTrue(layout.moveLocomotive(move.getLocomotive().getName(), end.getName(), false),
                "could not replay " + move.getLocomotive().getName() + " -> " + end.getName());

            end.setArrivedFrom(layout.entrySideOf(last, end));
            end.setArrivedAlong(move.getPath());

            run.add(move.getLocomotive().getName() + " -> " + end.getName());
        }

        return run;
    }

    /** Y -> T and X -> T; T -> P; P -> A (the platform, entered from the west) and P -> H. Every edge measured. */
    private static Layout twoRoutesSharingARun(int s88) throws Exception
    {
        Layout built = new Layout(model);

        built.setDefaultLocSpeed(30);

        String[] names = { "RT_Y", "RT_X", "RT_T", "RT_P", "RT_A", "RT_H" };
        boolean[] stations = { true, true, true, false, true, true };

        for (int i = 0; i < names.length; i++)
        {
            built.createPoint(names[i], stations[i], model.newFeedback(s88 + i, null).getName());
        }

        String[][] rails = { { "RT_Y", "RT_T" }, { "RT_X", "RT_T" }, { "RT_T", "RT_P" }, { "RT_P", "RT_A" },
            { "RT_P", "RT_H" } };

        int[] lengths = { 1, 1, 1, 2, 1 };

        for (int i = 0; i < rails.length; i++)
        {
            built.createEdge(rails[i][0], rails[i][1]);
            built.getEdge(rails[i][0], rails[i][1]).setLength(lengths[i]);
        }

        built.getEdge("RT_P", "RT_A").setEntrySide("W");
        built.getEdge("RT_P", "RT_H").setEntrySide("W");

        return built;
    }
}
