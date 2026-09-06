package ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Pressing Escape on the reversal prompt does not turn a train (ACC4-1).
 *
 * Adam's ruling was **"a yes/no keep direction, with no meaning change, and yes being default"**, and
 * for half a day the code did the opposite of its own comment.
 *
 * **`showOptionDialog` has three answers, not two.**  It returns an index into the options array, or
 * `JOptionPane.CLOSED_OPTION` - which is -1 - when the dialog is dismissed with Escape or the X.  The
 * mapping read `chose != 0`, so -1 was "not Yes", and dismissing the question reversed the train at
 * every asking square on the journey.  The comment three lines above promised that dismissing it was
 * safe, in the same commit that made it unsafe.
 *
 * **Where it came from is the part worth keeping.**  While the question was "should it change
 * direction here?", the action sat on Yes, and `chose == 0` happened to be dismiss-safe: -1 is not 0,
 * so an unanswered dialog did nothing.  Rewording it to "keep direction?" moved the action onto No and
 * the negation moved with it - and that promoted every non-Yes answer, including "I did not answer",
 * to the one that moves a train.  A bare integer comparison silently changed meaning when the
 * sentence around it changed.
 *
 * So the rule is written as the answer rather than as its opposite: only an explicit No reverses.
 */
public class testDismissingTheReversalPromptKeepsTheDirection
{
    /**
     * The three answers a dialog can give, and only one of them moves a train.
     */
    @Test
    public void testOnlyAnExplicitNoReverses() throws Exception
    {
        assertFalse(org.traincontrol.gui.ManualReversalPrompt.reverseFor(0),
            "Yes means keep the direction, so it must not reverse");

        assertTrue(org.traincontrol.gui.ManualReversalPrompt.reverseFor(1),
            "No means do not keep the direction, so it must reverse - if this fails the prompt now"
            + " does nothing at all and the ruling is unimplemented rather than inverted");

        assertFalse(org.traincontrol.gui.ManualReversalPrompt.reverseFor(
            javax.swing.JOptionPane.CLOSED_OPTION),
            "ACC4-1: Escape and the X answer CLOSED_OPTION (-1), and an operator who dismisses a"
            + " question has not asked for anything to happen. This reversed the train at every"
            + " asking square on the journey, while the comment above it said dismissal was safe.");
    }

    /**
     * And nothing else a dialog could return moves one either.
     *
     * `guard-knows-only-what-it-lists`: checking -1, 0 and 1 leaves the rule free to say yes to
     * anything else, and the defect was precisely a value nobody had thought about being swept into
     * the acting branch. This sweeps the range instead of naming three points in it.
     */
    @Test
    public void testNoOtherAnswerMovesATrain() throws Exception
    {
        for (int chose = -20; chose <= 20; chose++)
        {
            if (chose == 1) continue;

            assertFalse(org.traincontrol.gui.ManualReversalPrompt.reverseFor(chose),
                "an answer of " + chose + " reverses a train. Only No does, and No is index 1 in"
                + " TrainControlUI.YES_NO_OPTS - every other value is either a dialog that was not"
                + " answered or one nobody has thought about, and neither is a request to turn.");
        }
    }

    /**
     * The dialog's answer reaches that rule unchanged.
     *
     * `extracted-rule-moves-the-bug-to-the-call`: pulling the mapping out to where it can be run
     * leaves one line behind that no test covers, and that line is where this defect lived. So the
     * call site is asserted as a call site - the dialog's return value goes straight in, with nothing
     * done to it on the way.
     *
     * MUTATION: change the call site back to `answer[0] = chose != 0;` and this goes red.
     */
    @Test
    public void testTheDialogAnswerIsReadThroughThisRule() throws Exception
    {
        String source = new String(Files.readAllBytes(
            Paths.get("src/org/traincontrol/gui/ManualReversalPrompt.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("answer[0] = reverseFor(chose);"),
            "the dialog's answer no longer goes straight through reverseFor. Whatever replaced it is"
            + " the one line in this class that nothing can test, which is where ACC4-1 lived.");

        // And the value handed in is the dialog's own, not something derived from it.
        int assigned = source.indexOf("answer[0] = reverseFor(chose);");
        int dialog = source.indexOf("int chose = JOptionPane.showOptionDialog(");

        assertTrue(dialog > 0 && dialog < assigned,
            "reverseFor is no longer being handed what showOptionDialog returned");

        assertEquals(source.split("answer\\[0\\] =", -1).length - 1, 1,
            "the answer is written in more than one place, so one of them is not covered by the rule"
            + " above - which is the shape this defect had");
    }
}
