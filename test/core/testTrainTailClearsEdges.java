package core;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import support.SourceOf;

/**
 * When an edge behind a train may be handed back, at several train lengths.
 *
 * Adam: "I added train lengths to the layout, so make some tests that include various size trains."
 *
 * `Locomotive.trainLength` defaults to zero and no earlier test that executed a path set it, so the holding branch was
 * never entered.  There is ONE rule, and "cannot be known" means "assume the train is still there": `clearedEdges` is
 * read by `Layout.getActiveAccs`, which `MarklinRoute.heldReason` consults per command, and with atomicRoutes on
 * (Adam's configuration) being reported clear is the ONLY thing that drops an edge's protection.  An early clear lets
 * a route throw a TURNOUT on track the train is still standing on.  The single escape that is not a guess about this
 * train is a path with no measured lengths anywhere, where holding would hold until the route ended.
 *
 * The rule is asked directly, since asking it any other way needs a railway, a locomotive with a length and a path
 * with some measured edges; the call site is checked separately, the half that is usually forgotten.
 *
 * @author Adam
 */
public class testTrainTailClearsEdges
{
    private static MarklinControlStation model;

    /** Nothing else in test/core uses this address; this class gets its own JVM in the battery. */
    private static final int SWITCH_ADDRESS = 187;

    private static final String S88_MID = "48601";
    private static final String S88_MID2 = "48602";
    private static final String S88_END = "48603";

    private static support.LayoutSandbox sandbox;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // BEFORE the model, not after: `init` loads the machine-global layout preference, which on Adam's machine is
        // his real railway, and opened afterwards the sandbox protects nothing.
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedInDebug();
        model.stop();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (model != null)
        {
            model.clearAutoLayout();
            model.stop();
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * A train is not let off an edge its tail is still standing on, whatever length it is.
     *
     * MUTATION: comparing with `>` instead of `>=` releases a train whose tail ends exactly at the
     * join, and fails the boundary case below.
     */
    @Test
    public void testTheTailHoldsTheEdgeUntilItHasPassed()
    {
        for (int length : new int[] { 40, 60, 150, 400 })
        {
            assertFalse(Layout.tailHasProvablyPassed(false, length - 1, length),
                "an edge one unit short of a " + length + " train's length is handed back, so the "
                + "tail is still on it while another route is offered its turnouts");

            assertTrue(Layout.tailHasProvablyPassed(false, length, length),
                "an edge exactly a " + length + " train's length behind is still held - the tail "
                + "ends at the join, which is past it");

            assertTrue(Layout.tailHasProvablyPassed(false, length + 1, length),
                "an edge more than a " + length + " train's length behind is still held");
        }

        // No length set is the ordinary case, and it must not hold anything.
        assertTrue(Layout.tailHasProvablyPassed(false, 0, 0),
            "a locomotive with no length recorded holds every edge it passes, which is every "
            + "locomotive on a railway nobody has measured");

        assertTrue(Layout.tailHasProvablyPassed(false, 0, null),
            "a null train length holds every edge it passes");
    }

    /**
     * A path with no measured lengths anywhere still hands its edges back (distance can never accumulate, so holding
     * would hold until the route ended).
     */
    @Test
    public void testAnUnmeasuredPathDoesNotHoldForEver()
    {
        for (Integer length : new Integer[] { null, 0, 60, 150, 400 })
        {
            assertTrue(Layout.tailHasProvablyPassed(true, 0, length),
                "a path with no measured lengths anywhere holds its edges for a train of "
                + length + ", so on the ordinary unmeasured railway every edge of every route is "
                + "held until the route ends - a heavier regression than the one being fixed");
        }
    }

    /**
     * Unmeasured track HOLDS: nothing better can be known there, so the answer has to be "the train may still be
     * there".  The consumer is a guard that refuses to throw a turnout under a train.
     *
     * MUTATION: restoring the escape - release when the last edge had no length and the head has moved
     * on - fails every assertion here.
     */
    @Test
    public void testUnmeasuredTrackIsNotProof()
    {
        // Edges [100, 100, 0], train of 250: when the head finishes the unmeasured third edge, edge 0 has 100 behind
        // it.
        assertFalse(Layout.tailHasProvablyPassed(false, 100, 250),
            "100 measured units behind a 250 train counts as the tail having passed, so a route may "
            + "throw a turnout on an edge the train is still standing on");

        // Some distance banked, still short, then the head runs on over unmeasured track: the distance stays put.
        for (int length : new int[] { 60, 150, 400 })
        {
            assertFalse(Layout.tailHasProvablyPassed(false, 40, length),
                "an edge with 40 measured units behind it is handed back to a " + length + " train, "
                + "on a path where some edges are measured and some are not - which is Adam's "
                + "railway, and every run on it");
        }

        // THE UNMEASURED FIRST EDGE: the escape once read "how much has the head covered so far", zero at the start of
        // every run, so on edges [0, 100, 100] with a 250 train edge 0 went back at the first step.  Only the head had
        // not reached a measured edge yet.
        assertFalse(Layout.tailHasProvablyPassed(false, 0, 250),
            "a path that has measured edges is treated as unmeasured because the head has not reached "
            + "one yet, so the first edge is handed back with the whole train on it - which on a "
            + "railway with lengths on its platforms and nowhere else is most runs (VAL-A1)");

        // And the edge the head has only just left, where nothing has accrued yet.
        assertFalse(Layout.tailHasProvablyPassed(false, 0, 150),
            "the edge the head has only just left is handed back immediately to a 150 train");
    }

    /**
     * The clearing loop asks this rule, and gives it the right three things (a rule tested while nothing calls it, or
     * calls it with the wrong arguments, is a defect this project has lost before).
     *
     * MUTATION: passing a constant for the distance behind, or dropping the accumulator that feeds it, fails this.
     */
    @Test
    public void testTheClearingLoopAsksTheRule() throws Exception
    {
        String source = SourceOf.declaration("private boolean executePathInternal(");

        assertTrue(source.contains("tailHasProvablyPassed(pathIsUnmeasured, waiting[1],"),
            "the clearing loop no longer asks tailHasProvablyPassed with the path total and the "
            + "distance behind - so the rule is tested here and something else decides what actually "
            + "happens on the railway");

        // THE WHOLE CALL, not the argument alone: a whole-file `contains` of `loc.getTrainLength()` stopped being
        // unique once the reversal-room rule read the same length, so the clearing loop's argument could be deleted and
        // it stayed green.  WHITESPACE-INSENSITIVE, because the file's line endings are git's to choose
        // (`core.autocrlf=true`): a pinned newline finds nothing in a CRLF checkout, and the assertion then passes on a
        // file it could not read.
        String call = source.replaceAll("\\s+", " ");

        // THE LENGTH AS THE JOURNEY WAS DISPATCHED: read once, so a length written while the train runs
        // cannot shorten what it holds - and read from the locomotive, which is the half this asks.
        assertTrue(call.contains("tailHasProvablyPassed(pathIsUnmeasured, waiting[1], lengthAtDispatch)")
            && call.contains("final Integer lengthAtDispatch = loc.getTrainLength();"),
            "the clearing loop no longer passes the locomotive's length to tailHasProvablyPassed, so "
            + "the rule compares against nothing and every edge is handed back the moment the head "
            + "leaves it");
    }

    /**
     * ONE rule decides both reporting an edge clear (which stops `heldReason` refusing a route that would set an
     * accessory on it) and unlocking it (which hands the rails to another train); a second, looser rule for the first
     * is what this test exists to stop coming back.
     *
     * MUTATION: reintroducing a separate predicate for the clear, however named, fails the count.
     */
    @Test
    public void testTheClearAndTheUnlockAskTheSameQuestion() throws Exception
    {
        String source = SourceOf.declaration("private boolean executePathInternal(");

        int clears = source.indexOf("if (cleared != null) cleared.add(path.get(waiting[0]));");
        // setUnoccupied, not setLockedEdgeUnoccupied (OB-164): the early release gives up the throats it locked too.
        int unlocks = source.indexOf("path.get(waiting[0]).setUnoccupied();");
        int asks = source.indexOf("tailHasProvablyPassed(pathIsUnmeasured, waiting[1],");

        assertTrue(clears >= 0, "the clearing loop no longer reports any edge clear");
        assertTrue(unlocks >= 0, "the clearing loop no longer unlocks any edge");
        assertTrue(asks >= 0, "nothing asks the rule");

        assertTrue(asks < clears && asks < unlocks,
            "the rule is asked after an edge has already been reported clear or unlocked");

        // COUNTED, not positioned: a mutation that ADDS a second write to clearedEdges in front of the gate leaves the
        // statement above where it was and passes.  Exactly one edge is ever added to that set.
        int adds = SourceOf.occurrences(".add(path.get(waiting[0]))");

        assertEquals(adds, 1,
            "expected exactly one place where an edge is reported clear, and found " + adds
            + " - a second one in front of the gate hands an edge back without proof, which is the "
            + "whole of WK-B1");

        // Exactly one gate, not two: two is how the looser rule got back in.
        int gates = SourceOf.occurrences("tailHasProvablyPassed(");

        assertEquals(gates, 2,
            "expected the rule's definition and exactly one call, and found " + gates
            + " - a second predicate deciding one half of this is what WK-B1 was about");

        assertFalse(SourceOf.allCode().contains("tailMayStillBeOn"),
            "the looser companion rule is back. It may not decide whether an edge is reported clear: "
            + "with atomicRoutes on that is the only thing protecting the turnouts under a train");
    }

    /**
     * The gate is proved present, positioned and unique by source alone, but nothing until the clearing loop RUNS: a
     * mutation that keeps `if (!tailHasProvablyPassed(...))` and deletes the `continue;` inside it leaves every count
     * and position in `testTheClearAndTheUnlockAskTheSameQuestion` as it was, while an edge the rule refuses to clear
     * is cleared and unlocked anyway.
     *
     * Drives a real three-edge path (100, 100 and an unmeasured 0, with a 250-length train) through the real
     * `Layout.executePath` in simulate mode, and reads whether the first edge's accessory is still ACTIVE in
     * `getActiveAccs()` once the path has run.  The control is the same edges with none measured, where
     * `tailHasProvablyPassed` returns true unconditionally and the edge MUST be cleared; without it, "still held" could
     * mean only that this harness cannot observe a clear at all.
     */
    @Test
    public void testAnEdgeTheRuleRefusesToClearStaysHeldWhileARealPathRuns() throws Exception
    {
        MarklinAccessory behind =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        behind.setSwitched(false);

        try
        {
            // 100 measured units behind the first edge, 250 of train: the tail cannot have passed it, so it is held.
            List<Boolean> measured = runThreeEdgePath(behind, 100, 100, 0, 250);

            assertTrue(measured.size() >= 2,
                "the run reported fewer than two legs, so there was never a moment after the first "
                + "edge was queued for release - got " + measured.size());

            assertTrue(measured.get(0),
                "precondition: when the train sets off the path must own the first edge's accessory, "
                + "or there is nothing here for a mutation to hand back early");

            assertTrue(measured.get(measured.size() - 1),
                "the first edge stopped being held with 150 units of a 250-length train still standing "
                + "on it - tailHasProvablyPassed(false, 100, 250) says the tail has not passed, and the "
                + "clearing loop cleared and unlocked the edge anyway.  That is exactly what emptying "
                + "the `if (!tailHasProvablyPassed(...))` body would do, invisibly to every source scan "
                + "in this file");

            // CONTROL: proves the observation can see a genuine clear, not only an absence.  Nothing is measured, so
            // tailHasProvablyPassed returns true unconditionally and the edge must be cleared whatever the length.
            List<Boolean> unmeasured = runThreeEdgePath(behind, 0, 0, 0, 250);

            assertTrue(unmeasured.size() >= 2,
                "control: the run reported fewer than two legs - got " + unmeasured.size());

            assertFalse(unmeasured.get(unmeasured.size() - 1),
                "control: a path with no measured lengths anywhere must clear the first edge quickly "
                + "regardless of the train's length - tailHasProvablyPassed returns true unconditionally "
                + "when the path is unmeasured.  If this control cannot fail, the assertion above proves "
                + "nothing about the rule actually being obeyed");
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    /**
     * An edge a non-atomic run gave back on the way is not given back again when the run ends.
     *
     * `unlockPath` skips the edges a run recorded in `releasedEarly`.  Every other claim seeds that record by
     * reflection, so deleting the line that writes it left the suite green, and the unlock then released each
     * early-given-back edge a second time, taking away the claim of whichever train had locked it since.  This runs a
     * real path, non-atomic and unmeasured, and has another train claim the first edge at the route's end.
     *
     * MUTATION: stop recording the early release (`released.add(givenBack)`) and this fails.
     */
    @Test
    public void testAnEdgeGivenBackOnTheWayIsNotGivenBackAgain() throws Exception
    {
        for (String s : new String[] { S88_MID, S88_MID2, S88_END })
        {
            if (!model.isFeedbackSet(s)) model.newFeedback(Integer.parseInt(s), null);

            model.setFeedbackState(s, false);
        }

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);
        layout.setAtomicRoutes(false);

        layout.createPoint("TG_A", false, null);
        layout.createPoint("TG_B", false, S88_MID);
        layout.createPoint("TG_C", false, S88_MID2);
        layout.createPoint("TG_D", true, S88_END);

        final Edge ab = layout.createEdge("TG_A", "TG_B");
        Edge bc = layout.createEdge("TG_B", "TG_C");
        Edge cd = layout.createEdge("TG_C", "TG_D");

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        Integer wasLength = loc.getTrainLength();

        final java.util.function.Consumer<Locomotive> wasEnd =
            loc.hasCallback(Layout.CB_ROUTE_END) ? loc.getCallback(Layout.CB_ROUTE_END) : null;

        final int[] beforeTheEnd = { -1 };

        try
        {
            loc.setTrainLength(250);

            layout.getPoint("TG_A").setLocomotive(loc);

            // ANOTHER TRAIN TAKES THE FIRST EDGE at the route's end, after this run gave it back and before its unlock.
            loc.setCallback(Layout.CB_ROUTE_END, l ->
            {
                beforeTheEnd[0] = occupancy(ab);

                ab.setOccupied();

                if (wasEnd != null) wasEnd.accept(l);
            });

            assertTrue(layout.executePath(Arrays.asList(ab, bc, cd), loc, 30, null), "the dispatch did not complete, so"
                + " nothing here tests anything");

            assertEquals(beforeTheEnd[0], 0, "precondition: the run had not given the first edge back by its end - an"
                + " unmeasured path gives back each edge as soon as it is passed - so there is no early release here");

            assertEquals(occupancy(ab), 1, "the run's unlock gave back the first edge a second time: it had given it back"
                + " when its tail passed it, and the claim another train made since was taken away (GUI-A1, GUI2-C2)");
        }
        finally
        {
            loc.setTrainLength(wasLength);

            // No way to remove a callback: an absent one is put back as one that does nothing.
            loc.setCallback(Layout.CB_ROUTE_END, wasEnd != null ? wasEnd : l -> { });

            model.clearAutoLayout();
        }
    }

    /**
     * An edge is handed back at the sensor where the train's tail has passed it, not one sensor later.
     *
     * When the sensor at the end of edge i answers, the head has entered that sensor's square, so edge i-1 is behind it
     * by edge i less that square (all of edge i where the edges record no squares, as in a hand-written configuration).
     * The run counted edge i-1's length instead, so on a path of three edges or fewer the first was held to the end of
     * the run whatever the train's length.
     *
     * A-B-C-D, ten units an edge.  At C the head is ten units past the end of A-B, so a five-unit train is clear of it,
     * and a ten-unit one too (`tailHasProvablyPassed` reads `>=`).  At B nothing behind the head has been passed yet.
     *
     * MUTATION: count the head one sensor short again - `headSeenAt` set to where the edge just left ends, rather than
     * to where the edge just driven ends - and this fails.
     */
    @Test
    public void testAnEdgeIsHandedBackOnceTheTailHasPassedIt() throws Exception
    {
        MarklinAccessory behind =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        behind.setSwitched(false);

        try
        {
            for (int train : new int[] { 5, 10 })
            {
                List<Boolean> held = runThreeEdgePath(behind, 10, 10, 10, train);

                assertTrue(held.size() >= 4, "the run reported fewer than four legs - setting off, B, C and D - so there"
                    + " was never a moment at C to read (a train of " + train + "): " + held);

                assertTrue(held.get(0), "precondition: when the train sets off the path must own the first edge's"
                    + " accessory, or there is nothing here to hand back: " + held);

                assertTrue(held.get(1), "the first edge was handed back at B, where the head has only just finished it and"
                    + " a " + train + "-unit train is still lying on it: " + held);

                assertFalse(held.get(2), "at C the head is ten units past the end of A-B and a " + train + "-unit train is"
                    + " clear of it, but A-B was still held - its turnout refused to every other route until the train"
                    + " arrived.  The run counts the head one sensor short (GS-B3): " + held);
            }
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    /**
     * But a sensor's own square is not counted behind the head until the next sensor answers: a sensor answers when the
     * head ENTERS its square, and a release must never be early.
     *
     * A-B-C-D, ten units an edge, C's square the last 3 of B-C.  At C the head is known to be 7 past the end of A-B: a
     * 7-unit train is clear of it; an 8-unit train may still have a unit on it, and A-B is held.
     *
     * MUTATION: credit the arriving square at once - `headSeenAt = pathRunTo` - and the 8-unit half fails.
     */
    @Test
    public void testASensorsOwnSquareIsNotCountedBehindTheHeadYet() throws Exception
    {
        MarklinAccessory behind =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        behind.setSwitched(false);

        try
        {
            List<Boolean> seven = runThreeEdgePath(behind, 10, 10, 10, 7, true, 3);

            assertTrue(seven.size() >= 4, "the run reported fewer than four legs: " + seven);

            assertFalse(seven.get(2), "at C a 7-unit train is 7 units past A-B - B-C up to C's square - and A-B was still"
                + " held (GS-B3): " + seven);

            List<Boolean> eight = runThreeEdgePath(behind, 10, 10, 10, 8, true, 3);

            assertTrue(eight.size() >= 4, "the run reported fewer than four legs: " + eight);

            assertTrue(eight.get(2), "at C A-B was handed back under an 8-unit train that may still lie a unit over it: C's"
                + " sensor says only that the head has entered C's square, and the run counted the square behind it"
                + " (GS-B3): " + eight);
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    /**
     * And never before the tail has passed it: not while any of the train still lies on the edge, and not on the
     * strength of a Point no sensor saw the head reach.
     *
     * A-B-C-D, ten units an edge.  An eleven-unit train at C still has a unit on A-B, which is held to the end.  With
     * no sensor at C the head has only been seen at B, so even a five-unit train's A-B is held at C (at D the run ends
     * and gives everything back).  The control is the same no-sensor path with nothing measured, where the unmeasured
     * escape hands A-B back at C whatever the length.  Green before the fix as after it: the old arithmetic was late,
     * never early.
     *
     * MUTATION: move `headSeenAt` at a Point with no sensor too (drop the `seenHere` test), and this fails.
     */
    @Test
    public void testAnEdgeIsNotHandedBackBeforeTheTailHasPassedIt() throws Exception
    {
        MarklinAccessory behind =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        behind.setSwitched(false);

        try
        {
            List<Boolean> eleven = runThreeEdgePath(behind, 10, 10, 10, 11);

            assertTrue(eleven.size() >= 4, "the run reported fewer than four legs: " + eleven);

            for (int leg = 0; leg < eleven.size(); leg++)
            {
                assertTrue(eleven.get(leg), "an eleven-unit train still lying one unit over A-B had it handed back at leg "
                    + leg + " - a route may then throw a turnout under it: " + eleven);
            }

            List<Boolean> unseen = runThreeEdgePath(behind, 10, 10, 10, 5, false);

            assertTrue(unseen.size() >= 4, "the run reported fewer than four legs: " + unseen);

            for (int leg = 0; leg < unseen.size(); leg++)
            {
                assertTrue(unseen.get(leg), "A-B was handed back at leg " + leg + " on the strength of C, which has no"
                    + " sensor: the run passes C without waiting, the head was last seen at B, and a five-unit train may"
                    + " still be lying over A-B (GS-B3): " + unseen);
            }

            // CONTROL: the same shape with nothing measured, where the escape hands A-B back at C whatever the length
            List<Boolean> unmeasured = runThreeEdgePath(behind, 0, 0, 0, 5, false);

            assertTrue(unmeasured.size() >= 4, "control: the run reported fewer than four legs: " + unmeasured);

            assertFalse(unmeasured.get(2), "control: on a path with nothing measured A-B is handed back at C whatever the"
                + " train's length, and here it was not - so this harness cannot see a release where C has no sensor, and"
                + " the claims above prove nothing: " + unmeasured);
        }
        finally
        {
            model.clearAutoLayout();
        }
    }

    private static int occupancy(Edge edge)
    {
        try
        {
            java.lang.reflect.Field field = Edge.class.getDeclaredField("occupancy");

            field.setAccessible(true);

            return field.getInt(edge);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("Edge has no occupancy count to read", e);
        }
    }

    /**
     * Builds a fresh A-B-C-D path with the given edge lengths (`model.clearAutoLayout()` first, so two calls in a test
     * cannot see each other's state), dispatches the first locomotive in the database down it with the given train
     * length, and records once per leg whether the first edge's accessory is still reported active by
     * `getActiveAccs()`.
     */
    private List<Boolean> runThreeEdgePath(MarklinAccessory behind, int abLength, int bcLength,
        int cdLength, int trainLength) throws Exception
    {
        return runThreeEdgePath(behind, abLength, bcLength, cdLength, trainLength, true);
    }

    /**
     * The same, with or without a sensor at C; without one the run passes C without waiting, as it passes every Point
     * with no sensor.
     */
    private List<Boolean> runThreeEdgePath(MarklinAccessory behind, int abLength, int bcLength,
        int cdLength, int trainLength, boolean sensorAtC) throws Exception
    {
        return runThreeEdgePath(behind, abLength, bcLength, cdLength, trainLength, sensorAtC, 0);
    }

    /**
     * The same, with C's own square recorded on B-C as the builder records an edge's places; 0 for no places, as a
     * hand-written edge has.
     */
    private List<Boolean> runThreeEdgePath(MarklinAccessory behind, int abLength, int bcLength,
        int cdLength, int trainLength, boolean sensorAtC, int squareAtC) throws Exception
    {
        for (String s : new String[] { S88_MID, S88_MID2, S88_END })
        {
            if (!model.isFeedbackSet(s)) model.newFeedback(Integer.parseInt(s), null);

            model.setFeedbackState(s, false);
        }

        model.clearAutoLayout();

        Layout layout = model.getAutoLayout();

        layout.setSimulate(true);
        layout.setAtomicRoutes(true);

        layout.createPoint("TL_A", false, null);
        layout.createPoint("TL_B", false, S88_MID);
        layout.createPoint("TL_C", false, sensorAtC ? S88_MID2 : null);
        layout.createPoint("TL_D", true, S88_END);

        Edge ab = layout.createEdge("TL_A", "TL_B");
        Edge bc = layout.createEdge("TL_B", "TL_C");
        Edge cd = layout.createEdge("TL_C", "TL_D");

        ab.setLength(abLength);
        bc.setLength(bcLength);

        if (squareAtC > 0) bc.setPlaces(Arrays.asList("TL_B-C", "TL_C"), Arrays.asList(bcLength - squareAtC, squareAtC));
        cd.setLength(cdLength);

        ab.addConfigCommand(behind.getName(), Accessory.accessorySetting.STRAIGHT);

        Locomotive loc = model.getLocByName(model.getLocList().get(0));

        Integer wasLength = loc.getTrainLength();

        List<Boolean> behindHeld = new ArrayList<>();

        try
        {
            loc.setTrainLength(trainLength);

            layout.getPoint("TL_A").setLocomotive(loc);

            layout.setCallback("tail-clears-edges outcome probe", (edges, l, started) ->
            {
                if (!Boolean.TRUE.equals(started)) return null;

                behindHeld.add(layout.getActiveAccs().contains(behind));

                return null;
            });

            assertTrue(layout.executePath(Arrays.asList(ab, bc, cd), loc, 30, null),
                "the dispatch did not complete, so nothing here tests anything (edges " + abLength + "/"
                + bcLength + "/" + cdLength + ", train " + trainLength + ")");
        }
        finally
        {
            loc.setTrainLength(wasLength);
        }

        return behindHeld;
    }

    /**
     * A train's length changed while it runs does not shorten what the run holds.  The locomotive's menu could set the
     * length to 0 mid-run, and from the next sensor every edge the head had finished was given back with the train
     * still lying on it.  The run reads the length once, when it is dispatched; the same run with the length left at 4
     * is the control.
     *
     * MUTATION: let the run read the train's length at every sensor again, and this fails.
     */
    @Test
    public void testALengthChangedDuringARunDoesNotShortenWhatTheTrainHolds() throws Exception
    {
        assertEquals(runWithTheLengthChanged(1741, false), "held and refused", "precondition: with the length left at 4"
            + " the first rail was not held at the second sensor, so this fixture cannot tell a shortened hold from a"
            + " broken one");

        assertEquals(runWithTheLengthChanged(1751, true), "held and refused", "a train's length set to 0 while it ran"
            + " handed back the rail it was lying across at its next sensor, and a road over that rail's switch read"
            + " clear for another train (RSA-A1)");
    }

    /**
     * Atomic Routes is read by every running train at every sensor and written on the event thread, so it is volatile.
     *
     * MUTATION: take `volatile` off `Layout.atomicRoutes`, and this fails.
     */
    @Test
    public void testTheAtomicRoutesSettingIsVolatile() throws Exception
    {
        assertTrue(java.lang.reflect.Modifier.isVolatile(Layout.class.getDeclaredField("atomicRoutes").getModifiers()),
            "Layout.atomicRoutes is not volatile: the gate writes it on the event thread while trains run, and a train"
            + " already driving need never see the write (RSA-C5)");
    }

    /**
     * The rest of the state a driving thread reads while the event thread or the network writes it is volatile too:
     * whether the layout is valid, a locomotive's speed, direction, length and reversibility, and a sensor's state.
     *
     * MUTATION: take `volatile` off any one, and this names it.
     */
    @Test
    public void testTheStateADrivingThreadReadsIsVolatile() throws Exception
    {
        Object[][] fields = {{Layout.class, "isValid"}, {Locomotive.class, "speed"}, {Locomotive.class, "direction"},
            {Locomotive.class, "trainLength"}, {Locomotive.class, "reversible"},
            {org.traincontrol.base.Feedback.class, "set"}};

        for (Object[] field : fields)
        {
            java.lang.reflect.Field f = ((Class<?>) field[0]).getDeclaredField((String) field[1]);

            assertTrue(java.lang.reflect.Modifier.isVolatile(f.getModifiers()), ((Class<?>) field[0]).getSimpleName()
                + "." + field[1] + " is not volatile, though a driving thread reads it while another writes it (GST-C1)");
        }
    }

    /**
     * The two doors that add to the graph take the layout's monitor, as the doors that take from it do: `createPoint`
     * and `createEdge` beside `deletePoint`, `deleteEdge`, `renamePoint` and `copyEdge`.
     *
     * MUTATION: take `synchronized` off either, and this names it.
     */
    @Test
    public void testTheDoorsThatAddToTheGraphTakeTheMonitor() throws Exception
    {
        java.lang.reflect.Method point = Layout.class.getMethod("createPoint", String.class, boolean.class, String.class);
        java.lang.reflect.Method edge = Layout.class.getMethod("createEdge", String.class, String.class);

        for (java.lang.reflect.Method door : new java.lang.reflect.Method[] {point, edge})
        {
            assertTrue(java.lang.reflect.Modifier.isSynchronized(door.getModifiers()), "Layout." + door.getName()
                + " does not take the layout's monitor, which the doors that take from the graph do (GST-C5)");
        }
    }

    /**
     * A 4-unit train sent over three measured rails of 1, 1 and 4 units on a non-atomic railway, beside a road that
     * shares the first rail's metal.  After its first sensor its length is set to 0, or left alone; at its second
     * sensor the first rail is read.  Returns "held and refused" where it is still held and the crossing road refused.
     */
    private static String runWithTheLengthChanged(int base, boolean clear) throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] f = new org.traincontrol.marklin.MarklinFeedback[6];

        for (int i = 0; i < 6; i++)
        {
            f[i] = model.newFeedback(base + i, null);
            model.setFeedbackState(f[i].getName(), false);
        }

        final Locomotive x = model.getLocByName(model.getLocList().get(clear ? 3 : 4));
        final Locomotive y = model.getLocByName(model.getLocList().get(5));

        final Integer xWas = x.getTrainLength();
        final Integer yWas = y.getTrainLength();

        final Layout rail = new Layout(model);
        final String p = clear ? "RLK" : "RLC";

        Thread driving = null;

        try
        {
            x.setSpeed(0);
            x.setTrainLength(4);
            y.setTrainLength(1);

            rail.createPoint(p + "0", true, f[0].getName());
            rail.createPoint(p + "1", false, f[1].getName());
            rail.createPoint(p + "2", false, f[2].getName());
            rail.createPoint(p + "3", true, f[3].getName());
            rail.createPoint(p + "Y", true, f[4].getName());
            rail.createPoint(p + "Z", true, f[5].getName());
            rail.createEdge(p + "0", p + "1");
            rail.createEdge(p + "1", p + "2");
            rail.createEdge(p + "2", p + "3");
            rail.createEdge(p + "Y", p + "Z");

            final Edge first = rail.getEdge(p + "0", p + "1");
            final Edge crossing = rail.getEdge(p + "Y", p + "Z");

            final List<Edge> path = Arrays.asList(first, rail.getEdge(p + "1", p + "2"), rail.getEdge(p + "2", p + "3"));

            path.get(0).setLength(1);
            path.get(1).setLength(1);
            path.get(2).setLength(4);
            crossing.setLength(1);

            // The crossing road shares the first rail's metal - the other leg of its switch - as the reducer's lock edges say
            first.addLockEdge(crossing);
            crossing.addLockEdge(first);

            rail.setAtomicRoutes(false);
            rail.makeCurrent();
            rail.getPoint(p + "0").setLocomotive(x);

            driving = new Thread(() -> rail.executePath(path, x, 30, null), "length-claim-" + x.getName());
            driving.setDaemon(true);
            driving.start();

            long until = System.currentTimeMillis() + 10000;

            while (!(x.getSpeed() == 30 && rail.getActiveLocomotives().containsKey(x)) && System.currentTimeMillis() < until)
            {
                Thread.sleep(20);
            }

            assertTrue(rail.getActiveLocomotives().containsKey(x), "precondition: the 4-unit train was not dispatched: "
                + Layout.getLastError());

            model.setFeedbackState(f[1].getName(), true);
            Thread.sleep(700);
            model.setFeedbackState(f[1].getName(), false);

            // THE MENU'S WRITE, as TrainControlUI.applyTrainLength made it while trains ran
            if (clear) x.setTrainLength(0);

            // Its second sensor: the head one unit past the end of the first rail, a 4-unit train still over it
            model.setFeedbackState(f[2].getName(), true);
            Thread.sleep(700);

            boolean held = first.isLockHeld(y);
            boolean refused = !rail.isPathClear(Arrays.asList(crossing), y, false);

            return held && refused ? "held and refused" : "first rail held=" + held + ", crossing refused=" + refused;
        }
        finally
        {
            for (int i = 0; i < 3; i++) model.setFeedbackState(f[i].getName(), false);

            model.setFeedbackState(f[3].getName(), true);

            if (driving != null) driving.join(10000);

            for (org.traincontrol.marklin.MarklinFeedback each : f) model.setFeedbackState(each.getName(), false);

            x.setSpeed(0);
            x.setTrainLength(xWas);
            y.setTrainLength(yWas);
        }
    }

    /**
     * A sensor two Points in a row share is no proof the head has reached the second: its wait begins with the sensor
     * already occupied for the first and returns at once, so crediting the head at the second Point's square handed the
     * track behind back with the whole train still on it.  A 5-unit train on A-B-C-D, ten units an edge, C's square the
     * last 3 of B-C, a road over A-B's switch: with the head only entering B's square A-B is held and the road refused,
     * and with B and C on their own sensors (the control) the same.
     *
     * MUTATION: credit a sensor that already read occupied when its wait began, and this fails.
     */
    @Test
    public void testASensorTwoPointsShareIsNotProofTheHeadReachedTheSecond() throws Exception
    {
        assertEquals(runPastASharedSensor(1781, false), "held and refused", "precondition: with B and C on their own"
            + " sensors the first rail was not held while the head was only entering B's square, so this fixture cannot tell"
            + " an early release from a broken one");

        assertEquals(runPastASharedSensor(1791, true), "held and refused", "B and C share one sensor, and with the head"
            + " only entering B's square the run handed back A-B - the whole 5-unit train still on it, and a road over its"
            + " switch clear for another train (RSA61-A1)");
    }

    /**
     * A 5-unit train sent A-B-C-D on a non-atomic railway, the head only entering B's square, beside a road that shares
     * A-B's metal; B and C on one sensor, or each on its own.  Returns "held and refused" where A-B is still held and
     * the road refused.
     */
    private static String runPastASharedSensor(int base, boolean shared) throws Exception
    {
        org.traincontrol.marklin.MarklinFeedback[] f = new org.traincontrol.marklin.MarklinFeedback[6];

        for (int i = 0; i < 6; i++)
        {
            f[i] = model.newFeedback(base + i, null);
            model.setFeedbackState(f[i].getName(), false);
        }

        final Locomotive x = model.getLocByName(model.getLocList().get(shared ? 6 : 7));
        final Locomotive y = model.getLocByName(model.getLocList().get(8));

        final Integer xWas = x.getTrainLength();
        final Integer yWas = y.getTrainLength();

        final Layout rail = new Layout(model);
        final String p = shared ? "SSS" : "SSD";

        Thread driving = null;

        try
        {
            x.setSpeed(0);
            x.setTrainLength(5);
            y.setTrainLength(1);

            rail.createPoint(p + "A", true, f[0].getName());
            rail.createPoint(p + "B", false, f[1].getName());
            rail.createPoint(p + "C", false, shared ? f[1].getName() : f[2].getName());
            rail.createPoint(p + "D", true, f[3].getName());
            rail.createPoint(p + "Y", true, f[4].getName());
            rail.createPoint(p + "Z", true, f[5].getName());
            rail.createEdge(p + "A", p + "B");
            rail.createEdge(p + "B", p + "C");
            rail.createEdge(p + "C", p + "D");
            rail.createEdge(p + "Y", p + "Z");

            final Edge first = rail.getEdge(p + "A", p + "B");
            final Edge second = rail.getEdge(p + "B", p + "C");
            final Edge crossing = rail.getEdge(p + "Y", p + "Z");

            final List<Edge> path = Arrays.asList(first, second, rail.getEdge(p + "C", p + "D"));

            first.setLength(10);
            second.setLength(10);
            second.setPlaces(Arrays.asList(p + "B-C", p + "C"), Arrays.asList(7, 3));
            path.get(2).setLength(10);
            crossing.setLength(1);

            // The road shares A-B's metal - the other leg of its switch - as the reducer's lock edges say
            first.addLockEdge(crossing);
            crossing.addLockEdge(first);

            rail.setAtomicRoutes(false);
            rail.makeCurrent();
            rail.getPoint(p + "A").setLocomotive(x);

            driving = new Thread(() -> rail.executePath(path, x, 30, null), "shared-sensor-claim-" + x.getName());
            driving.setDaemon(true);
            driving.start();

            long until = System.currentTimeMillis() + 10000;

            while (!(x.getSpeed() == 30 && rail.getActiveLocomotives().containsKey(x)) && System.currentTimeMillis() < until)
            {
                Thread.sleep(20);
            }

            assertTrue(rail.getActiveLocomotives().containsKey(x), "precondition: the 5-unit train was not dispatched: "
                + Layout.getLastError());

            // THE HEAD ENTERS B'S SQUARE: its sensor reads occupied, and stays so while the train runs over the contact
            model.setFeedbackState(f[1].getName(), true);
            Thread.sleep(1500);

            boolean held = first.isLockHeld(y);
            boolean refused = !rail.isPathClear(Arrays.asList(crossing), y, false);

            return held && refused ? "held and refused" : "A-B held=" + held + ", road refused=" + refused;
        }
        finally
        {
            for (int i = 0; i < 3; i++) model.setFeedbackState(f[i].getName(), false);

            model.setFeedbackState(f[3].getName(), true);

            if (driving != null) driving.join(10000);

            for (org.traincontrol.marklin.MarklinFeedback each : f) model.setFeedbackState(each.getName(), false);

            x.setSpeed(0);
            x.setTrainLength(xWas);
            y.setTrainLength(yWas);
        }
    }
}
