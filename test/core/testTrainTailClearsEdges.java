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
        assertTrue(source.contains("tailHasProvablyPassed(pathIsUnmeasured, waiting[1],\n"
            + "                                loc.getTrainLength())"),
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
        layout.createPoint("TL_C", false, S88_MID2);
        layout.createPoint("TL_D", true, S88_END);

        Edge ab = layout.createEdge("TL_A", "TL_B");
        Edge bc = layout.createEdge("TL_B", "TL_C");
        Edge cd = layout.createEdge("TL_C", "TL_D");

        ab.setLength(abLength);
        bc.setLength(bcLength);
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
}
