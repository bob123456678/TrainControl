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
 * A Return Home plan never sends a train over a tail an earlier move of the same plan left behind (OB-228).
 *
 * Adam, on MT-335, 2026-09-15: *"The 335 park works, but I get: Could not run EN57-203 from BottomInner (northbound) to
 * TopMainR0Park - the path stayed blocked."*  His log: the plan parked 75 407 DB at BottomMainA first, then routed
 * EN57-203 over BottomMainAPre -> RampDown, and the runtime refused it - *"75 407 DB is standing across BottomMainAPre ->
 * RampDown"*.  The planner modelled only the tails of trains that had not moved yet; a train it has moved has a known
 * road, the route the plan gave it, and its tail lies back along that route.
 *
 * **The shape, on sensors of its own.**  Both trains' routes home share T -> P.  Train A comes from Y and parks at A,
 * three units long behind a two-unit approach, so its tail lies back over T -> P.  Train B comes from X and goes on past
 * P to its home H.  Moved in that order, B runs into A's tail; moved the other way round, both get home.
 *
 * **Asked the way the railway would.**  Each move of the plan is driven onto the layout as a run leaves a train - the
 * locomotive moved, the side it came in by and the road it came along recorded - and before each move the edges the
 * trains already standing cover are asked of `Layout.edgesCoveredByStandingTrains`, the runtime's own walk.
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
        model = MarklinControlStation.init(null, true, false, false, false);

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

    /**
     * Every move of the plan is clear of the tails of the trains the plan has already put down.
     *
     * @throws Exception from the railway
     */
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
     * A plan found only one way round is found, though the other way round reaches the same squares first (MFR-B2).
     *
     * The search keyed an arrangement by where the trains stand, and a move's tail by the one chain of moves it kept -
     * so two orders reaching the same squares with different tails were one arrangement, and whichever was found first
     * decided.  Here X has two roads home: the short one past SY, whose tail lies over SY -> P, and a long one.  With Y
     * still at SY, X takes the long road; with Y gone, the short one.  Z needs SX and SY empty and SY -> P clear.  So
     * the only order that brings all three home is X, Y, Z - and Y, X reaches the same squares with X's tail across Z's
     * road.
     *
     * @throws Exception from the railway
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
     * The retry from the start still has time when the first search spends its share (MFV-B1, claimed as MFW-C3).
     *
     * OB-228 added a second search, from the railway as it stands, for the arrangement the greedy pass boxes itself
     * into; MFR-C2 then put both searches on one deadline, so a first search that ran to it left the retry nothing,
     * and a plan the retry would have found came back NO_PLAN_FOUND.  The budget is shared out now.  The X, Y, Z
     * railway of the claim above needs the retry; sixty sidings Z can wander along keep the first search from running
     * out of arrangements before its time does; and the search's clock is stepped 300 ms a read, so the fifteen-second
     * budget is fifty reads rather than fifteen seconds.
     *
     * @throws Exception from the railway or the reflection
     */
    @Test
    public void testTheRetryFromTheStartHasTimeLeft() throws Exception
    {
        // THE ORDER THE GREEDY PASS MEETS THE TRAINS is the order of Layout's point map, a HashMap by name - and this
        // claim needs Y met before X, so the greedy pass boxes itself in and the retry is needed.  The names are chosen
        // for that (Java's String hash, 128 buckets for these seventy points); the "precondition" below says so if a
        // change to the map or the names ever lets the greedy pass succeed.
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

        // THE SIDINGS: a line of stations off SZ, none of them anybody's home.
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

        // A CLOCK THAT MOVES 300 MS EVERY TIME THE SEARCH LOOKS AT IT.
        final java.util.concurrent.atomic.AtomicLong now = new java.util.concurrent.atomic.AtomicLong();

        java.lang.reflect.Field clock = HomeStaging.class.getDeclaredField("clock");

        clock.setAccessible(true);
        clock.set(staging, (java.util.function.LongSupplier) () -> now.addAndGet(300));

        HomeStaging.Plan plan = staging.plan();

        // NOT VACUOUS: more than the first search's share was read - a third of the budget since OB-230 - so the greedy
        // pass did not simply succeed and the retry from the start ran.
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
     * A road that shares metal with a parked train's tail is refused, and the build's places say when it does not
     * (AMH-C2).
     *
     * `passesTheTailsOfTrainsItHasMoved` has two arms.  The first - the edge the mover wants IS one the tail covers -
     * is what the claim above pins.  The second was exercised by nothing: an edge the tail does not cover but which is
     * LOCKED against one it does, which is how the model says "these are the same piece of metal".  Two edges over one
     * bridge, one platform road reached two ways, the two halves of a scissors: the runtime refuses the second because
     * the first is occupied, and a planner that does not model it plans a move the railway will not run - OB-073's
     * shape, and the same defect OB-228 was.
     *
     * **And the refinement on top of it.**  Where BOTH edges carry places from the build, the lock alone is not enough:
     * the tail has to actually lie on a place the mover's edge runs over.  Two roads can be locked against each other
     * because they share one square at one end while the tail sits at the far end of the other, and refusing that is a
     * planner stricter than its railway.  So three arrangements are asserted here, and they differ in nothing else:
     * the lock with no places refuses, the lock with places the tail is not on allows, and the lock with the place the
     * tail IS on refuses again.
     *
     * The railway forces the order.  A starts on B's home, so A must move first; A's home is beyond the shared metal,
     * and parking it there three units long leaves its tail over `RS_T -> RS_HA`; B's only road home runs over
     * `RS_P -> RS_Y`, the other edge on that metal.  So B can only go home over A's tail, and the plan for both is
     * possible exactly when the shared metal is not modelled.
     *
     * MUTATION, both run: dropping the lock-edge loop from `passesTheTailsOfTrainsItHasMoved` fails the first
     * arrangement.  Dropping the `tailLiesOn` continue instead fails all four claims in this class, the control
     * in this one first - `createEdge` locks edges that share a point, so without the places refinement a tail
     * anywhere closes every road that touches its own, and ordinary railways stop being plannable.  That is a
     * stronger result than the prediction it replaces, and it is why the refinement is not optional.
     */
    @Test
    public void testARoadSharingMetalWithAParkedTailIsRefused() throws Exception
    {
        trainA.setTrainLength(3);
        trainB.setTrainLength(1);

        // THE CONTROL FIRST: the same railway with the shared metal not declared.  Without this, every refusal below
        // could be the fixture being unsolvable rather than the rule under test.
        assertEquals(planWithBothTrains(twoRoadsOverOneBridge(2460, false, null, null)).getOutcome(),
            HomeStaging.Outcome.READY,
            "control: with no lock between the two roads there is no plan home at all, so nothing below shows the"
            + " lock-edge rule doing anything");

        // 1. THE LOCK, WITH NO PLACES: the build said these two edges are one piece of metal and said nothing about
        // squares, so a tail on either closes both.
        assertEquals(planWithBothTrains(twoRoadsOverOneBridge(2470, true, null, null)).getOutcome(),
            HomeStaging.Outcome.NO_PLAN_FOUND,
            "the plan sends B over metal A's tail is lying on: the two roads are locked against each other and only"
            + " the edge itself was being checked, so the railway would refuse the move the plan promises (AMH-C2)");

        // 2. THE LOCK, WITH PLACES THE TAIL IS NOT ON: locked at one end, the tail at the other.  Refusing this makes
        // the planner stricter than the runtime, which is the failure OB-073 names.
        assertEquals(planWithBothTrains(twoRoadsOverOneBridge(2480, true, "RS_bridge", "RS_elsewhere")).getOutcome(),
            HomeStaging.Outcome.READY,
            "both roads name the squares they run over and A's tail is on neither of B's, so the lock alone refused a"
            + " move the railway allows");

        // 3. THE LOCK, WITH THE PLACE THE TAIL IS ON: the refinement must not swallow the rule it narrows.
        assertEquals(planWithBothTrains(twoRoadsOverOneBridge(2490, true, "RS_bridge", "RS_bridge")).getOutcome(),
            HomeStaging.Outcome.NO_PLAN_FOUND,
            "B's road runs over the very square A's tail is standing on and the plan sent it anyway, so the places"
            + " refinement has swallowed the rule it was meant to narrow");
    }

    /**
     * Stands A and B where the fixture wants them, homes them, and plans.
     *
     * @param layout one of the three railways above
     * @return the plan
     */
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
     * metal.  A is three units long and the approach into HA measures two, so its tail lies back over `T -> HA`.
     *
     * @param s88 the first of five sensor numbers
     * @param locked whether the two roads are locked against each other
     * @param aPlace the place `T -> HA` runs over BEHIND the berth, or null for a build that named none.  An
     *        edge's places are path-ordered with the square it arrives at last, so the berth is named separately
     *        and this is the one a three-unit tail reaches past it
     * @param bPlace the corresponding place on `P -> Y`, or null for the same
     * @return the graph
     */
    private static Layout twoRoadsOverOneBridge(int s88, boolean locked, String aPlace, String bPlace)
        throws Exception
    {
        Layout built = new Layout(model);

        built.setDefaultLocSpeed(30);

        String[] names = { "RS_Y", "RS_T", "RS_HA", "RS_X", "RS_P" };

        // RS_T and RS_P are junctions, so neither train can be parked on one and wait the other out - which would
        // give the search an order that never puts a tail on the bridge at all.
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

        // TWO PLACES EACH, because a tail's first square is the berth it stands on: a single place IS that berth,
        // the walk spends it, and nothing further back is ever claimed - measured, on the first version of this
        // fixture, as the third arrangement coming back READY with the tail on the shared square.
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
     * Drives each move of the plan onto the layout as a run leaves a train, failing on a move over another train's tail.
     *
     * @param layout the railway
     * @param plan the plan
     * @return the moves made
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

            // DRIVEN ONTO THE LAYOUT AS A RUN LEAVES A TRAIN: moved, the side it came in by, and the road it came along.
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

    /**
     * Y -> T and X -> T; T -> P; P -> A (the platform, entered from the west) and P -> H.  Every edge measured.
     *
     * @param s88 the first of six sensor numbers
     * @return the graph
     */
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
