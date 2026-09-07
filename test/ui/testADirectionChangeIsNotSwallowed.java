package ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

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
 * **The ruling this was written under has since been withdrawn, and the mechanism kept** (REG8-C4).
 * `ca0265f4` wanted a mid-run reversal to surface after the run; Adam reversed that nine hours later -
 * *"reversals during the run should be ignored and not queued.  Only count reversals when nothing is
 * running.  That way, there is no backlog."* - and `bc6120f1` implemented it. So the sentence that
 * used to stand here, that the change was "not deferred until it was safe; it was swallowed", now
 * describes the intended behaviour rather than the defect.
 *
 * **What is still worth pinning is the ordering, and it is only half the rule.** Recording before the
 * guard writes a direction the window has not acted on, which corrupts the baseline whichever ruling
 * is in force. The other half is `reconcileFacingWhenIdle`, which levels that baseline when the
 * railway goes idle; the two together are what "no backlog" means, and deleting the reconcile would
 * leave both methods here green while restoring the backlog. That is asserted below rather than left
 * to this comment.
 *
 * **Why this is checked as an ordering rather than run.**  `followDirectionChanges` is an instance
 * method of the main window and reads three things a test cannot cheaply stand up: a built layout, a
 * live autonomy session, and a Central Station feeding locomotive echoes on its own thread.  The
 * behaviour that remains is a race - it needs an echo to arrive during a journey and another after it -
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
        String source = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/TrainControlUI.java")), StandardCharsets.UTF_8);

        int start = source.indexOf("private void followDirectionChanges(");

        assertTrue(start > 0, "followDirectionChanges has been renamed; this check now guards nothing"
            + " and needs rewriting rather than deleting");

        // To the end of the method, taken as the next method declaration at the same indent.
        int end = source.indexOf("\n    private ", start + 20);
        int alt = source.indexOf("\n    public ", start + 20);

        if (alt > 0 && (end < 0 || alt < end)) end = alt;
        if (end < 0) end = source.length();

        String body = source.substring(start, end);

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
     * The other half of "no backlog": the baseline is levelled when the railway goes idle (REG8-C4).
     *
     * The ordering above stops a mid-run change being recorded as acted-on. On its own that is not the
     * ruling Adam gave - it leaves the pre-run direction in the map, so the first echo after the run
     * reads a difference and follows a reversal the railway already made. `reconcileFacingWhenIdle` is
     * what closes that: it brings the baseline level with the live state once nothing is running.
     *
     * **Deleting it would leave both methods above green while restoring the backlog**, which is why
     * this asserts the pair rather than trusting a comment to connect them. Source-level for the same
     * reason the ordering is: the behaviour is a race between an echo and a repaint.
     *
     * The exception is asserted too - a turn the railway made at a destination must be WRITTEN before
     * the levelling wipes the evidence of it (IND9-B4), and "levels everything" would silently undo
     * that.
     *
     * @throws Exception on a failure to read the source
     */
    @Test
    public void testTheBaselineIsLevelledWhenTheRailwayGoesIdle() throws Exception
    {
        String ui = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(ui.contains("private void reconcileFacingWhenIdle()")
            || ui.contains("public void reconcileFacingWhenIdle()"),
            "reconcileFacingWhenIdle has gone. The ordering guard above then passes while the backlog"
            + " it half-prevents is back: the pre-run direction stays in the baseline and the first"
            + " echo after the run re-follows a reversal the railway already made (REG8-C4)");

        int levels = ui.indexOf("lastSeenDirection.put(loc.getName(), loc.goingForward())");

        assertTrue(levels > 0,
            "nothing levels the baseline from live state any more, so the reconcile no longer does the"
            + " job its name claims");

        int writes = ui.indexOf("takeReversalsOnArrival()");

        assertTrue(writes > 0 && writes < levels,
            "the reversals the railway made at a destination are not written to the graph before the"
            + " baseline is levelled - so the levelling wipes the only record of them, which is the"
            + " defect IND9-B4 fixed");
    }

    /**
     * And a train first met mid-journey still gets a baseline, or the first echo after the run is
     * skipped as "never seen" and the same symptom comes back by another route.
     */
    @Test
    public void testATrainMetWhileRunningStillGetsABaseline() throws Exception
    {
        String source = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/TrainControlUI.java")), StandardCharsets.UTF_8);

        int start = source.indexOf("private void followDirectionChanges(");
        int end = source.indexOf("\n    private ", start + 20);

        if (end < 0) end = source.length();

        String body = source.substring(start, end);

        assertTrue(body.contains("lastSeenDirection.putIfAbsent("),
            "the guarded branch must still seed a locomotive it has never seen - without it a train"
            + " first met during a journey has no recorded direction, and the first echo after the run"
            + " reads as \"never seen\" and is skipped, which is the reported symptom again. And it must"
            + " be putIfAbsent: a plain put here overwrites the baseline and swallows the change.");
    }
}
