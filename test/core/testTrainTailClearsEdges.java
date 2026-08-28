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
}
