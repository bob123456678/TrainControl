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

/**
 * When an edge behind a train may be handed back, at several train lengths.
 *
 * Adam, 2026-08-28: "I added train lengths to the layout, so make some tests that include various
 * size trains."
 *
 * **This rule was dead code as far as the suite was concerned.** `Locomotive.trainLength` defaults to
 * zero and no test that executes a path ever set it, so the comparison was against zero every time,
 * the holding branch was never entered, and a regression in it reached a commit with a green battery
 * behind it.
 *
 * The rule then got a second life as TWO rules, for a few hours, and that was the worse mistake. A
 * looser companion released an edge when the last edge traversed had no measured length - nothing
 * better could be known over that stretch - and it was allowed to decide whether an edge was reported
 * CLEAR, on the reasoning that being early there only moves a signal.
 *
 * It does not. `clearedEdges` is read by `Layout.getActiveAccs`, which `MarklinRoute.heldReason`
 * consults per command to refuse a route that would set an accessory on an active path. With
 * atomicRoutes on - Adam's configuration - the lock is held for the whole run by design, so being
 * reported clear is the ONLY thing that drops an edge's protection. An early clear lets a route throw
 * a TURNOUT on track the train is still standing on, which is AU-A2 by another door (WK-B1).
 *
 * So there is one rule, and "cannot be known" means "assume the train is still there". The single
 * escape that is not a guess about this train is a path with no measured lengths anywhere, where
 * distance can never accumulate and holding would hold until the route ended.
 *
 * The rule is asked directly because it cannot be asked any other way without a railway, a locomotive
 * with a length, and a path with lengths on some of its edges. The call site is checked separately -
 * the half that is usually forgotten, and the half that produced two defects here already.
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
        // BEFORE the model, not after it.
        //
        // `init` reads the machine-global layout preference and loads whatever it names, which on
        // Adam's machine is his real railway. Opened afterwards it protects nothing - three classes
        // had it in that order on 2026-08-28 and had been opening his layout on every battery.
        sandbox = support.LayoutSandbox.open();

        model = init(null, true, false, false, true);
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
     * A path with no measured lengths anywhere still hands its edges back.
     *
     * The one escape that is NOT a guess about where this train is: where nothing has a length,
     * distance can never accumulate, so holding would hold until the route ended. That is how this
     * railway behaved before any of the tail bookkeeping existed.
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
     * Unmeasured track HOLDS, and that is the point (WK-B1).
     *
     * The commit before this one released here, on the reasoning that nothing better could be known
     * over an unmeasured stretch. Nothing better can be known - which is exactly why the answer has to
     * be "the train may still be there". The consumer is a guard that refuses to throw a turnout under
     * a train.
     *
     * MUTATION: restoring the escape - release when the last edge had no length and the head has moved
     * on - fails every assertion here.
     */
    @Test
    public void testUnmeasuredTrackIsNotProof()
    {
        // The commit's own example: edges [100, 100, 0] with a train of 250. When the head finishes
        // the unmeasured third edge, edge 0 has 100 behind it - and 150 of the train on it.
        assertFalse(Layout.tailHasProvablyPassed(false, 100, 250),
            "100 measured units behind a 250 train counts as the tail having passed, so a route may "
            + "throw a turnout on an edge the train is still standing on");

        // Some distance banked, still short, and the head then runs on over unmeasured track: the
        // distance stays exactly where it was, and that is not proof of anything.
        for (int length : new int[] { 60, 150, 400 })
        {
            assertFalse(Layout.tailHasProvablyPassed(false, 40, length),
                "an edge with 40 measured units behind it is handed back to a " + length + " train, "
                + "on a path where some edges are measured and some are not - which is Adam's "
                + "railway, and every run on it");
        }

        // VAL-A1: THE UNMEASURED FIRST EDGE, which is what the running total got wrong.
        //
        // The escape used to read "how much has the head covered so far", which is zero at the start
        // of every run - so on edges [0, 100, 100] with a 250 train, edge 0 was handed back on the
        // first step with the whole train standing on it. The path HAS measured edges; only the head
        // had not reached one yet.
        assertFalse(Layout.tailHasProvablyPassed(false, 0, 250),
            "a path that has measured edges is treated as unmeasured because the head has not reached "
            + "one yet, so the first edge is handed back with the whole train on it - which on a "
            + "railway with lengths on its platforms and nowhere else is most runs (VAL-A1)");

        // And the edge the head has only just left, where nothing has accrued because nothing has
        // happened yet.
        assertFalse(Layout.tailHasProvablyPassed(false, 0, 150),
            "the edge the head has only just left is handed back immediately to a 150 train");
    }

    /**
     * The clearing loop asks this rule, and gives it the right three things.
     *
     * The other half of pulling a rule out of the place that used it. This project has lost several
     * defects to a rule that was tested while nothing called it, or called it with the wrong
     * arguments.
     *
     * MUTATION: passing a constant for the distance behind, or dropping the accumulator that feeds it,
     * fails this.
     */
    @Test
    public void testTheClearingLoopAsksTheRule() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automation/Layout.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(source.contains("tailHasProvablyPassed(pathIsUnmeasured, waiting[1],"),
            "the clearing loop no longer asks tailHasProvablyPassed with the path total and the "
            + "distance behind - so the rule is tested here and something else decides what actually "
            + "happens on the railway");

        // THE WHOLE CALL, not the argument on its own (TCX-A3, 2026-09-01).
        //
        // This asked whether `loc.getTrainLength()` appears anywhere in Layout.java at all.  It did,
        // here, and on 2026-09-01 it started appearing somewhere else as well: the reversal-room rule
        // added that day reads the same length twice, at what are now lines 2330 and 2352.  From that
        // moment the assertion could not fail - the clearing loop's argument could be deleted outright
        // and the other file's two occurrences would keep it green.
        //
        // A whole-file `contains` is only a test while the string is unique to the thing it is about,
        // and nothing warns when it stops being.  Anchored on the call now, the way the assertion above
        // it already was.
        // WHITESPACE-INSENSITIVE, because the file's line endings are git's to choose (TSX-C9).
        //
        // This pinned a literal newline and the wrapped line's thirty-two spaces.  The repository is
        // checked out with `core.autocrlf=true` and nothing under `src/` is pinned in `.gitattributes`,
        // so a fresh clone gets CRLF here and the `contains` finds nothing - the assertion then passes
        // on a file it could not read, which is the same shape as the blessed baseline that cried wolf
        // this morning.  Re-wrapping the call in the editor would do it too.
        String call = source.replaceAll("\\s+", " ");

        // THE LENGTH AS THE JOURNEY WAS DISPATCHED (RSA-A1): read once, so a length written while the train runs
        // cannot shorten what it holds - and read from the locomotive, which is the half this asks.
        assertTrue(call.contains("tailHasProvablyPassed(pathIsUnmeasured, waiting[1], lengthAtDispatch)")
            && call.contains("final Integer lengthAtDispatch = loc.getTrainLength();"),
            "the clearing loop no longer passes the locomotive's length to tailHasProvablyPassed, so "
            + "the rule compares against nothing and every edge is handed back the moment the head "
            + "leaves it");
    }

    /**
     * ONE rule decides both, because both are safety-relevant (WK-B1).
     *
     * Reporting an edge clear is what stops `heldReason` refusing a route that would set an accessory
     * on it; unlocking hands the rails to another train. A second, looser rule for the first of those
     * is what this test exists to stop coming back.
     *
     * MUTATION: reintroducing a separate predicate for the clear, however named, fails the count.
     */
    @Test
    public void testTheClearAndTheUnlockAskTheSameQuestion() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automation/Layout.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int clears = source.indexOf("if (cleared != null) cleared.add(path.get(waiting[0]));");
        // setUnoccupied, not setLockedEdgeUnoccupied (OB-164).
        //
        // The early release used to give up the edge and KEEP the throats it had locked until
        // the whole path finished, which is the bug Adam reported: in non-atomic mode the
        // edges came free and the crossings did not.  It gives up what it took now.  What this
        // test is about - one rule, asked before either the clear or the unlock - is unchanged.
        int unlocks = source.indexOf("path.get(waiting[0]).setUnoccupied();");
        int asks = source.indexOf("tailHasProvablyPassed(pathIsUnmeasured, waiting[1],");

        assertTrue(clears >= 0, "the clearing loop no longer reports any edge clear");
        assertTrue(unlocks >= 0, "the clearing loop no longer unlocks any edge");
        assertTrue(asks >= 0, "nothing asks the rule");

        assertTrue(asks < clears && asks < unlocks,
            "the rule is asked after an edge has already been reported clear or unlocked");

        // COUNTED, not positioned.
        //
        // The line above pins where one statement sits. A mutation that ADDS a second write to
        // clearedEdges in front of the gate - which is exactly the shape WK-B1 describes - leaves that
        // statement where it was and passes. Exactly one edge is ever added to that set.
        int adds = 0;

        for (int at = source.indexOf(".add(path.get(waiting[0]))"); at >= 0;
            at = source.indexOf(".add(path.get(waiting[0]))", at + 1))
        {
            adds++;
        }

        assertEquals(adds, 1,
            "expected exactly one place where an edge is reported clear, and found " + adds
            + " - a second one in front of the gate hands an edge back without proof, which is the "
            + "whole of WK-B1");

        // Exactly one gate, not two. Two is how the looser rule got back in last time.
        int gates = 0;

        for (int at = source.indexOf("tailHasProvablyPassed("); at >= 0;
            at = source.indexOf("tailHasProvablyPassed(", at + 1))
        {
            gates++;
        }

        assertEquals(gates, 2,
            "expected the rule's definition and exactly one call, and found " + gates
            + " - a second predicate deciding one half of this is what WK-B1 was about");

        assertFalse(source.contains("tailMayStillBeOn"),
            "the looser companion rule is back. It may not decide whether an edge is reported clear: "
            + "with atomicRoutes on that is the only thing protecting the turnouts under a train");
    }

    /**
     * TST-A3: the gate is proved present, positioned and unique above - by source alone - but nothing
     * until this test RUNS the clearing loop and watches what it actually does to a locked edge.
     *
     * A mutation that keeps `if (!tailHasProvablyPassed(...))` exactly as written and empties its body
     * - deleting the `continue;` inside it - leaves every count and position
     * `testTheClearAndTheUnlockAskTheSameQuestion` checks exactly where it was: the `if` is still
     * there, asking the same question, in the same place, called once. What breaks is that the answer
     * stops mattering - an edge the rule refuses to clear gets cleared and unlocked anyway. Only
     * running the loop can see that.
     *
     * This drives a real three-edge path - Adam's own worked example, edges of 100, 100 and an
     * unmeasured 0, with a 250-length train - through the real `Layout.executePath`, in simulate mode,
     * and reads the answer the same way the railway does: whether the first edge's own accessory is
     * still reported ACTIVE by `getActiveAccs()` once the whole path has run. Still active means still
     * held; dropping out of that set means cleared and unlocked.
     *
     * The control is the same three edges with none of them measured at all - `pathIsUnmeasured` true
     * for the whole path - where `tailHasProvablyPassed` returns true unconditionally
     * (`Layout.java:3440`) and the edge MUST be cleared quickly. Without it, "still held" above could
     * mean nothing more than "this harness cannot observe a clear happening at all".
     */
    @Test
    public void testAnEdgeTheRuleRefusesToClearStaysHeldWhileARealPathRuns() throws Exception
    {
        MarklinAccessory behind =
            model.newSwitch(SWITCH_ADDRESS, Accessory.accessoryDecoderType.MM2, false);

        behind.setSwitched(false);

        try
        {
            // THE FINDING'S OWN SCENARIO. 100 measured units behind the first edge, 250 of train: the
            // tail cannot possibly have passed it yet, so it must still be reported held.
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

            // CONTROL: proves the observation above can see a PRESENCE (a genuine clear), not only an
            // absence. Nothing here is measured, so pathIsUnmeasured is true for the whole path and
            // tailHasProvablyPassed returns true unconditionally (Layout.java:3440) - the edge must be
            // cleared, whatever the train's length. If this failed, "held" above would not be something
            // this test could actually tell from "always reports held".
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
     * An edge a non-atomic run gave back on the way is not given back again when the run ends (GUI2-C2).
     *
     * `unlockPath` skips the edges a run recorded in `releasedEarly` as its tail cleared them, and gives back the rest.
     * Every other claim on that rule seeds the record by reflection, so deleting the one line that writes it left the
     * suite green - and the unlock then released each early-given-back edge a second time, taking away the claim of
     * whichever train had locked it since (GUI-A1).  This runs a real path, non-atomic and unmeasured so each edge is
     * given back as soon as it is passed, and has another train claim the first edge at the route's end, just before
     * the unlock: the unlock must leave that claim alone.
     *
     * MUTATION: stop recording the early release (`released.add(givenBack)`) and this fails.
     *
     * @throws Exception from the fixture
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
     * An edge is handed back at the sensor where the train's tail has passed it, not one sensor later (GS-B3; Adam,
     * 2026-10-10, asked: into 3.0.0).
     *
     * When the sensor at the end of edge i answers, the head has entered that sensor's square, so edge i-1 is behind it by
     * edge i less that square - all of edge i here, where the edges record no squares (a hand-written configuration; the
     * square is `testASensorsOwnSquareIsNotCountedBehindTheHeadYet`'s).
     * The run counted edge i-1's length instead, and queued edge i-1 with nothing behind it: every edge was short of its
     * distance by the edge just driven, and on a path of three edges or fewer the first was held to the end of the run
     * whatever the train's length, its turnouts refused to every other route for the whole journey.
     *
     * A-B-C-D, ten units an edge.  At C the head is ten units past the end of A-B, so a five-unit train is clear of it, and
     * a ten-unit one too - its tail ends at the join, and `tailHasProvablyPassed` reads `>=`.  At B nothing behind the head
     * has been passed yet.
     *
     * MUTATION: count the head one sensor short again - `headSeenAt` set to where the edge just left ends, rather than to
     * where the edge just driven ends - and this fails.
     *
     * @throws Exception from the railway
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
     * But a sensor's own square is not counted behind the head until the next sensor answers (GS-B3): a sensor answers
     * when the head ENTERS its square, so when C's answers all that is known to be behind the head is B-C up to C's square.
     * A release must never be early - the fix was put to Adam as releasing track "earlier, never unsafe", and he chose
     * it (2026-10-10).
     *
     * A-B-C-D, ten units an edge, C's square the last 3 of B-C.  At C the head is known to be 7 past the end of A-B: a
     * 7-unit train is clear of it and A-B is handed back; an 8-unit train may still have a unit on it, and A-B is held.
     * Read as the head at the far end of C's square, A-B would go back under the 8-unit train.
     *
     * MUTATION: credit the arriving square at once - `headSeenAt = pathRunTo` - and the 8-unit half fails.
     *
     * @throws Exception from the railway
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
     * And never before the tail has passed it (GS-B3): not while any of the train still lies on the edge, and not on the
     * strength of a Point no sensor saw the head reach.
     *
     * A-B-C-D, ten units an edge.  An eleven-unit train at C still has a unit on A-B, and A-B is held to the end.  With no
     * sensor at C the run passes C without waiting - nothing says the head got there - so even a five-unit train's A-B is
     * held at C: the head has only been seen at B, nothing past the end of A-B.  (At D the run ends, and gives everything
     * back as it always has.)  The control is the same no-sensor path with nothing measured, where the unmeasured escape
     * hands A-B back at C whatever the train's length - so this harness can see a release in that shape.
     *
     * Green before the fix as after it: the arithmetic that counted the head one sensor short was late, never early.  It is
     * here for the fix as first filed - count edge i's length at every step - which at a Point with no sensor counts track
     * nobody saw the head cross.
     *
     * MUTATION: move `headSeenAt` at a Point with no sensor too (drop the `seenHere` test), and this fails.
     *
     * @throws Exception from the railway
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
     * Builds a fresh A-B-C-D path with the given edge lengths, dispatches the first locomotive in the
     * database down it with the given train length, and records - once per leg, via the real
     * `getActiveAccs()` - whether the first edge's own accessory is still reported active.
     *
     * A fresh {@code Layout} every call: {@code model.clearAutoLayout()} first, so two calls in the same
     * test cannot see each other's points, edges or locked accessory state.
     */
    private List<Boolean> runThreeEdgePath(MarklinAccessory behind, int abLength, int bcLength,
        int cdLength, int trainLength) throws Exception
    {
        return runThreeEdgePath(behind, abLength, bcLength, cdLength, trainLength, true);
    }

    /**
     * The same, with or without a sensor at C (GS-B3).  Without one the run passes C without waiting, as it passes every
     * Point with no sensor - which a hand-written configuration may have - so nothing says where the head is between B
     * and D.
     *
     * @param sensorAtC whether C carries a sensor
     * @return whether the first edge's accessory was still held, once per leg
     * @throws Exception from the railway
     */
    private List<Boolean> runThreeEdgePath(MarklinAccessory behind, int abLength, int bcLength,
        int cdLength, int trainLength, boolean sensorAtC) throws Exception
    {
        return runThreeEdgePath(behind, abLength, bcLength, cdLength, trainLength, sensorAtC, 0);
    }

    /**
     * The same, with C's own square recorded on B-C as the builder records an edge's places - the steps, then the square
     * arrived at (GS-B3).
     *
     * @param squareAtC the length of C's square, the last place of B-C; 0 for no places, as a hand-written edge has
     * @return whether the first edge's accessory was still held, once per leg
     * @throws Exception from the railway
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
     * A train's length changed while it runs does not shorten what the run holds (RSA-A1).  With Atomic Routes off, a run
     * hands the track behind a train back once the head is the train's length past it, and the locomotive's menu could
     * set that length to 0 ("not set") mid-run: from the next sensor every edge the head had finished was given back with
     * the train still lying on it, and a road across its switch read clear for another train.  The run reads the length
     * once, when it is dispatched.  The same run with the length left at 4 is the control.
     *
     * MUTATION: let the run read the train's length at every sensor again, and this fails.
     *
     * @throws Exception from the railway
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
     * Atomic Routes is read by every running train at every sensor and written by the atomic-routes gate on the event
     * thread while trains run, so it is volatile, as the other flags a driving thread reads are (RSA-C5).
     *
     * MUTATION: take `volatile` off `Layout.atomicRoutes`, and this fails.
     *
     * @throws Exception from reflection
     */
    @Test
    public void testTheAtomicRoutesSettingIsVolatile() throws Exception
    {
        assertTrue(java.lang.reflect.Modifier.isVolatile(Layout.class.getDeclaredField("atomicRoutes").getModifiers()),
            "Layout.atomicRoutes is not volatile: the gate writes it on the event thread while trains run, and a train"
            + " already driving need never see the write (RSA-C5)");
    }

    /**
     * The rest of the state a driving thread reads while the event thread or the network writes it is volatile too
     * (GST-C1): whether the layout is valid, a locomotive's speed, direction, length and reversibility, and a sensor's
     * state.  Without it a thread may go on reading what it cached - a train driving on a layout declared invalid, a
     * length changed on the event thread unseen by the train it describes.
     *
     * MUTATION: take `volatile` off any one, and this names it.
     *
     * @throws Exception from reflection
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
     * The two doors that add to the graph take the layout's monitor, as the doors that take from it do (GST-C5):
     * `createPoint` and `createEdge` beside `deletePoint`, `deleteEdge`, `renamePoint` and `copyEdge`.  Today they are
     * called while a layout is being built, before anything else holds it; the monitor is what keeps that true of a
     * caller added later.
     *
     * MUTATION: take `synchronized` off either, and this names it.
     *
     * @throws Exception from reflection
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
     * shares the first rail's metal.  After its first sensor its length is set to 0, or left alone; at its second sensor -
     * the head one unit past the end of the first rail - the first rail is read.
     *
     * @param base the first of the six sensor addresses this run uses
     * @param clear whether to set the length to 0 after the first sensor
     * @return "held and refused" where the first rail is still held and the crossing road refused, else what was seen
     * @throws Exception from the railway
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

            // Its first sensor
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
            // It arrives
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
