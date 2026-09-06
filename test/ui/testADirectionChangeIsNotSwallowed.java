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
 * there was nothing left to follow.  The change was not deferred until it was safe; it was swallowed,
 * and the diagram stayed wrong until somebody reversed the train again by hand.
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
