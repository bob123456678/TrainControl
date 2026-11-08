package ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * A reversal made while a train is running is not recorded as one already dealt with.
 *
 * Adam, 2026-09-06: **"when the route finishes, the reversal isn't painted/visible"**.
 *
 * **The order of two lines was the whole defect.**  `followDirectionChanges` keeps
 * `lastSeenDirection`, a map of the direction the window has SEEN AND FOLLOWED for each locomotive,
 * and it declines to follow anything while the layout is running - the graph must not be rewritten
 * under a train that is moving.  But the recording came first and the guard came second, so a reversal
 * made during a journey was written into the map and then dropped one line later.  The graph never
 * heard about it, while the window had already noted the new direction as the one it had acted on.
 *
 * When the run finished, the next echo carried that same direction, `was == forward` was true, and
 * there was nothing left to follow.
 *
 * The ruling this was written under has since been withdrawn and the mechanism kept: Adam, *"reversals during the run
 * should be ignored and not queued.  Only count reversals when nothing is running.  That way, there is no backlog."*
 *
 * What is still pinned is the ordering, which is only half the rule: recording before the guard writes a direction the
 * window has not acted on, which corrupts the baseline.  The other half, `reconcileFacingWhenIdle`, levels it when the
 * railway goes idle, and is asserted below.
 *
 * **Why this is checked as an ordering rather than run.**  `followDirectionChanges` is an instance
 * method of the main window and reads three things a test cannot cheaply stand up: a built layout, a
 * live autonomy session, and a Central Station feeding locomotive echoes on its own thread.  The
 * behavior that remains is a race - it needs an echo to arrive during a journey and another after it -
 * and a test that only sometimes reproduces is worse than no test.  `testTheRebuildIsOnePass` split on
 * the same line for the same reason and says so.
 *
 * `guard-knows-only-what-it-lists` applies and is worth stating: this knows about `lastSeenDirection`
 * being written by `put`.  A future rewrite that records the direction some other way would satisfy
 * this check and could still swallow the change.  It is a guard against the regression that happened,
 * not a proof that the class is correct.
 *
 * MUTATION, confirmed: move the `lastSeenDirection.put` line back above the `isRunning()` guard and
 * this goes red.
 *
 * @author Adam
 */
public class testADirectionChangeIsNotSwallowed
{
    /**
     * Inside `followDirectionChanges`, nothing is recorded before the decision not to act.
     */
    @Test
    public void testTheRunningGuardComesBeforeTheRecording() throws Exception
    {
        String body = SourceOf.declaration("private void followDirectionChanges(");

        int guard = body.indexOf("isRunning()");
        int record = body.indexOf("lastSeenDirection.put(");

        assertTrue(guard > 0, "the running guard has gone from followDirectionChanges - the graph must"
            + " not be rewritten under a train that is moving");

        assertTrue(record > 0, "nothing records the direction any more; this check knows only about"
            + " lastSeenDirection.put and can no longer see what it was written to guard");

        assertTrue(guard < record,
            "followDirectionChanges records the direction at offset " + record + " before deciding"
            + " whether to act on it at offset " + guard + ". That is the defect Adam reported: a"
            + " reversal made during a journey is noted as seen, dropped by the running guard, and"
            + " then matches the first echo after the run - so the diagram is never repainted.");
    }

    /**
     * The other half of "no backlog": the baseline is leveled when the railway goes idle.  `reconcileFacingWhenIdle`
     * brings it level with the live state once nothing is running; deleting it would leave both methods above green
     * while restoring the backlog.  Source-level, since the behavior is a race between an echo and a repaint.  The
     * exception is asserted too: a turn the railway made at a destination must be WRITTEN before the leveling wipes the
     * evidence of it.
     */
    @Test
    public void testTheBaselineIsLevelledWhenTheRailwayGoesIdle() throws Exception
    {
        assertTrue(SourceOf.occurrences("    void reconcileFacingWhenIdle()") == 1,
            "reconcileFacingWhenIdle has gone. The ordering guard above then passes while the backlog"
            + " it half-prevents is back: the pre-run direction stays in the baseline and the first"
            + " echo after the run re-follows a reversal the railway already made (REG8-C4)");

        // INSIDE THE METHOD, not across the file: `takeReversalsOnArrival()` appears twice in TrainControlUI.java (also
        // in `takeThePendingTurns`), so an `indexOf` over the whole file compared lines in two different methods.
        // Scoped as `testTheRunningGuardComesBeforeTheRecording` below scopes its own.
        String body = SourceOf.declaration("    void reconcileFacingWhenIdle()");

        int levels = body.indexOf("lastSeenDirection.put(loc.getName(), loc.goingForward())");

        assertTrue(levels > 0,
            "nothing levels the baseline from live state any more, so the reconcile no longer does the"
            + " job its name claims");

        // THE DRAIN IS `writeTheTurns`, which this method calls and the doors that leave a railway during a run share
        // - so the call is looked for here, and the drain in that method's own body
        int writes = body.indexOf("writeTheTurns(built, session)");

        assertTrue(writes > 0,
            "reconcileFacingWhenIdle no longer drains the destination turns at all. The behaviour is"
            + " really pinned by regression.testTheTurnAtTheDestinationReachesTheDiagram, which drives"
            + " it; this assertion only says the mechanism is still in the method it belongs to");

        assertTrue(SourceOf.declaration("private void writeTheTurns(").contains("takeReversalsOnArrival()"),
            "writeTheTurns, which reconcileFacingWhenIdle calls to drain the destination turns, drains nothing");

        assertTrue(writes < levels,
            "the reversals the railway made at a destination are not written to the graph before the"
            + " baseline is levelled - so the levelling wipes the only record of them, which is the"
            + " defect IND9-B4 fixed. Offsets within reconcileFacingWhenIdle: the write is at " + writes
            + " and the levelling at " + levels);
    }

    /**
     * And a train first met mid-journey still gets a baseline, or the first echo after the run is
     * skipped as "never seen" and the same symptom comes back by another route.
     */
    @Test
    public void testATrainMetWhileRunningStillGetsABaseline() throws Exception
    {
        String body = SourceOf.declaration("private void followDirectionChanges(");

        assertTrue(body.contains("lastSeenDirection.putIfAbsent("),
            "the guarded branch must still seed a locomotive it has never seen - without it a train"
            + " first met during a journey has no recorded direction, and the first echo after the run"
            + " reads as \"never seen\" and is skipped, which is the reported symptom again. And it must"
            + " be putIfAbsent: a plain put here overwrites the baseline and swallows the change.");
    }
}
