package core;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;

/**
 * When an edge behind a train may be let go, at several train lengths.
 *
 * Adam, 2026-08-28: "I added train lengths to the layout, so make some tests that include various
 * size trains."
 *
 * **This rule was dead code as far as the suite was concerned.** `Locomotive.trainLength` defaults to
 * zero and no test that executes a path ever set it, so `waiting[1] < loc.getTrainLength()` compared
 * against zero every time, the holding branch was never entered, and a regression in it reached a
 * commit with a green battery behind it. The one test that set a train length is about whether a
 * station will accept one.
 *
 * The regression: the escape for unmeasured track was tested against an accumulator that runs the
 * whole path, so once ANY measured edge had been traversed it could never fire again - and an edge
 * with no measured length adds nothing to the distance behind, so on a path of mixed lengths every
 * edge queued after the first measured one was held until the run ended. On a railway with lengths on
 * its platforms and nowhere else - which is Adam's - that is every run.
 *
 * What it cost with atomicRoutes on: a held edge never reaches `clearedEdges`, so `getActiveAccs` goes
 * on reporting the accessories behind the train as active and route triggers refuse to change those
 * signals for the rest of the run.
 *
 * The rule is asked directly because it cannot be asked any other way without a railway, a locomotive
 * with a length, and a path with lengths on some of its edges. The call site is checked separately, in
 * `testTheClearingLoopAsksTheRule` below - the half that is usually forgotten.
 *
 * @author Adam
 */
public class testTrainTailClearsEdges
{
    /**
     * A train is not let off an edge its tail is still standing on, whatever length it is.
     *
     * MUTATION: comparing with `>` instead of `>=` releases a train whose tail ends exactly at the
     * join, and fails the boundary case below.
     */
    @Test
    public void testTheTailHoldsTheEdgeUntilItHasPassed()
    {
        // A measured path: 200 has been travelled, and this edge was left 40 ago.
        for (int length : new int[] { 60, 150, 400 })
        {
            assertTrue(Layout.tailMayStillBeOn(200, 40, 1, 40, length),
                "a train " + length + " long was let off an edge only 40 behind its head, so a "
                + "turnout under the middle of it could be thrown");
        }

        // Far enough back for a short train, not for a long one.
        assertFalse(Layout.tailMayStillBeOn(500, 200, 3, 60, 150),
            "a 150 train is still held 200 behind the head, so nothing behind a short train is ever "
            + "released and its signals stay refused");

        assertTrue(Layout.tailMayStillBeOn(500, 200, 3, 60, 400),
            "a 400 train was released with only 200 behind the head - its tail is still there");

        // The boundary: the tail ends exactly at the join.
        assertFalse(Layout.tailMayStillBeOn(500, 150, 2, 50, 150),
            "a train exactly its own length behind the head is still held, so an edge is never "
            + "released at the moment the tail clears it");

        // A train with no length recorded is not a reason to hold anything.
        assertFalse(Layout.tailMayStillBeOn(500, 0, 1, 0, 0),
            "an edge is held for a train whose length nobody has entered");

        assertFalse(Layout.tailMayStillBeOn(500, 0, 1, 0, null),
            "a null train length throws or holds, when it means the same as not knowing");
    }

    /**
     * Where nothing is measured, the railway clears the moment the front passes - as it always has.
     *
     * Two shapes of that, and the second is the regression. A path with no lengths at all was handled;
     * a path with SOME lengths left every later edge held for ever, because the escape was asked of an
     * accumulator that never went back to zero.
     *
     * MUTATION: dropping the `behind == 0 && edgesSince > 0` clause restores the regression and fails
     * the mixed case.
     */
    @Test
    public void testUnmeasuredTrackDoesNotHoldForEver()
    {
        // Nothing measured anywhere: the historical trade, and the case that was covered.
        //
        // BOTH the just-queued edge and one the head has moved on from. The mutation run found that
        // only the second was tested: with `edgesSince` above zero the unmeasured-stretch clause
        // answers as well, so deleting the whole-path escape changed nothing and the test stayed
        // green. The edge the head has only just left is the one that needs this clause.
        for (int length : new int[] { 60, 150, 400 })
        {
            assertFalse(Layout.tailMayStillBeOn(0, 0, 0, 0, length),
                "a path with no measured lengths held the edge the head had just left, for a "
                + length + " train - so on a railway where nobody has measured anything the first "
                + "edge behind the train is never released");

            assertFalse(Layout.tailMayStillBeOn(0, 0, 1, 0, length),
                "a path with no measured lengths held an edge for a " + length + " train, so on a "
                + "railway where nobody has measured anything nothing behind a train is ever released");
        }

        // THE REGRESSION. Some measured length on the path, but none since this edge was queued: the
        // head has moved on over track nobody has measured, so where the tail is cannot be known.
        for (int length : new int[] { 60, 150, 400 })
        {
            assertFalse(Layout.tailMayStillBeOn(200, 0, 1, 0, length),
                "an edge is held although nothing measurable has happened since the head left it - a "
                + "path with lengths on its platforms and nowhere else therefore holds every edge "
                + "after the first measured one until the run ends, and with atomic routes on that "
                + "leaves every signal behind the train refused");
        }

        // THE CASE A REVIEWER FOUND, which the first version of this rule got wrong.
        //
        // Some distance has accrued - short of the train - and then the head runs on over unmeasured
        // track. Asking the running total, as the first version did, could only ever escape while that
        // total had stayed at zero, so this held for the rest of the run. Edges [1, 1, 0, 0, 0] with a
        // train of 3, which is a combination his own railway has.
        for (int length : new int[] { 60, 150, 400 })
        {
            assertFalse(Layout.tailMayStillBeOn(200, 40, 3, 0, length),
                "an edge with some distance behind it - short of a " + length + " train - is held "
                + "although the edge just travelled was unmeasured. Every later unmeasured edge "
                + "leaves the distance exactly where it was, so this is held until the run ends");
        }

        // But an edge the head has only JUST left is a different thing: no distance yet because
        // nothing has happened, not because nothing is measured.
        assertTrue(Layout.tailMayStillBeOn(200, 0, 0, 90, 150),
            "the edge the head has only just left is released immediately, so a turnout directly "
            + "under the train can be thrown");
    }

    /**
     * The clearing loop asks this rule, and gives it the right four things.
     *
     * The other half of pulling a rule out of the place that used it. This project has lost four
     * defects to a rule that was tested while nothing called it, and this one had to be extracted
     * because it cannot be reached any other way.
     *
     * MUTATION: passing a constant for `edgesSince`, or dropping the counter that feeds it, fails this.
     */
    @Test
    public void testTheClearingLoopAsksTheRule() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automation/Layout.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(source.contains("tailMayStillBeOn(travelledOnThisPath, waiting[1], waiting[2],"),
            "the clearing loop no longer asks tailMayStillBeOn with the path total, the distance "
            + "behind and how many edges ago - so the rule is tested here and something else decides "
            + "what actually happens on the railway");

        // AND the edge just travelled, which is the argument the escape actually turns on.
        //
        // Without it the rule is back to asking a running total that never resets - the fault a
        // reviewer found in the first version of this fix, which is the same fault the fix was for.
        assertTrue(source.contains("justTravelled, loc.getTrainLength()"),
            "the rule is no longer told the length of the edge just travelled, so its escape is back "
            + "to reading an accumulator that never resets - and an edge with some distance behind it "
            + "is held for the rest of the run");

        assertTrue(source.contains("waiting[2]++"),
            "nothing counts how many edges have passed since an entry was queued, so the rule cannot "
            + "tell an edge the head has only just left from one it has walked away from over "
            + "unmeasured track - and those two want opposite answers");

        assertTrue(source.contains("new int[] { i - 1, 0, 0 }"),
            "an entry is queued without room for the edge counter, so it is either missing or being "
            + "read from somewhere that does not hold it");
    }

    /**
     * Two standards of proof, and the case where they must disagree.
     *
     * The release does two things. Reporting an edge CLEAR moves a signal behind a train that has gone
     * by, and being early about it costs a signal. UNLOCKING it lets another route be sent onto that
     * edge, and being early about it puts a second train on track the first is standing on.
     *
     * The escape for unmeasured track is a guess - it fires because the last edge had no length, which
     * says nothing about where the tail is. I wired it into both, and the validator caught it. It now
     * decides only the first.
     *
     * MUTATION: giving `tailHasProvablyPassed` the same escape, or writing it as
     * `return !tailMayStillBeOn(...)`, fails the first block. Removing the gate at the call site fails
     * the second.
     */
    @Test
    public void testOnlyProofUnlocksTrack() throws Exception
    {
        // THE DEFECT. Edges [100, 100, 0], train of 250: when the head finishes the unmeasured third
        // edge, edge 0 has 100 behind it and the escape fires - with 150 of the train still on it.
        assertFalse(Layout.tailMayStillBeOn(200, 100, 2, 0, 250),
            "the unmeasured-track escape no longer clears, so a signal behind a train that has gone "
            + "by stays refused on a railway with lengths in some places and not others");

        assertFalse(Layout.tailHasProvablyPassed(200, 100, 250),
            "100 measured behind a 250 train counts as PROOF the tail is past, so non-atomic mode "
            + "unlocks an edge with 150 of the train standing on it and another route may be sent "
            + "into it");

        // Said once more as the disagreement it is, so that making the two agree cannot pass.
        assertTrue(Layout.tailHasProvablyPassed(200, 100, 250)
                != !Layout.tailMayStillBeOn(200, 100, 2, 0, 250),
            "the guess and the proof now answer alike for [100, 100, 0] with a train of 250 - one of "
            + "them has been made to delegate to the other, and whichever way round that happened "
            + "either the signals stopped moving or the locks stopped waiting");

        // PROOF, when the measurement really does cover the train.
        assertTrue(Layout.tailHasProvablyPassed(500, 200, 150),
            "200 measured behind a 150 train is not enough to unlock, so an edge the train has "
            + "provably left is held to the end of the route");

        assertTrue(Layout.tailHasProvablyPassed(500, 150, 150),
            "exactly the train's length behind is not enough to unlock - the tail sits on the "
            + "boundary and the edge is never released early");

        // The unmeasured-path fallback is NOT the same kind of guess and stays on both sides: where
        // nothing has a length, distance can never accumulate and holding would hold to the end of
        // every route, which is how this railway ran before any of the bookkeeping existed.
        for (Integer length : new Integer[] { null, 0, 150, 400 })
        {
            assertTrue(Layout.tailHasProvablyPassed(0, 0, length),
                "a path with no measured lengths anywhere no longer unlocks as it goes, so on the "
                + "ordinary unmeasured railway every edge of every route is now held until the route "
                + "ends - which is a heavier regression than the one being fixed");
        }
    }

    /**
     * The call site gates the UNLOCK on proof, and does not gate the clear.
     *
     * Both halves matter and they fail in opposite directions, so both are asserted. Extracting a rule
     * moves the bug to the call.
     */
    @Test
    public void testTheUnlockBranchWaitsForProof() throws Exception
    {
        String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/automation/Layout.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int clears = source.indexOf("if (cleared != null) cleared.add(path.get(waiting[0]));");
        int proves = source.indexOf("if (!tailHasProvablyPassed(travelledOnThisPath, waiting[1],");
        int unlocks = source.indexOf("path.get(waiting[0]).setLockedEdgeUnoccupied();");

        // BOTH PRESENT before either is ordered: indexOf answers -1 for something absent, and -1 is
        // less than every real index, so an ordering test alone passes when the thing it is protecting
        // has been deleted outright.
        assertTrue(clears >= 0, "the clearing loop no longer reports any edge clear");
        assertTrue(unlocks >= 0, "the clearing loop no longer unlocks any edge");

        assertTrue(proves >= 0,
            "the unlock is no longer gated on tailHasProvablyPassed, so the guess about unmeasured "
            + "track decides a lock again and another train can be routed onto track this one is "
            + "still standing on");

        assertTrue(clears < proves,
            "the clear now happens after the proof gate, so signals behind the train stay refused on "
            + "unmeasured track - which is the FR this was written for");

        assertTrue(proves < unlocks,
            "the proof gate no longer stands in front of the unlock");
    }
}
