package regression;

import java.lang.reflect.Method;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import support.SourceOf;

/**
 * The three doors that refuse to start autonomy say the same thing, and it names the count (MT-263).
 *
 * Two of the three told the operator to wait for trains that were not running: autonomy refuses on `hasErrors()`, which
 * covers a graph that will not build at all, and there the error COUNT is zero because nothing turned the problem into
 * a finding.  The grayed Start item's tooltip and the scripting API's exception chose their wording on that count alone
 * and fell through to "wait for the trains to stop".  Then, even once that was closed, those two read "one thing has to
 * be dealt with first" for ANY number of blocking problems, because neither asked how many (MT-263's step 5: "with
 * three blocking problems, the start door should say three, not 'one thing'"; the shape OB-090 wore twice).
 *
 * **Asked of the arithmetic, and then of the doors:** the rule is asked directly with the numbers put in, so no railway
 * has to be broken three ways; the second claim reads the three call sites, because pinning the rule alone would leave
 * what was actually wrong unchecked.
 *
 * @author Adam
 */
public class testTheRefusalToStartSaysWhichThing
{
    /** The four answers, in the order the rule has to ask them. */
    @Test
    public void testTheWordingFollowsWhatIsActuallyWrong() throws Exception
    {
        // ERROR FINDINGS: name how many, because the editor can take him to them.
        assertEquals(said(2, 0, true), I18n.f("autolayout.ui.errorCannotStartWithErrors", 2),
            "with two error findings the refusal does not name them");

        // THREE BLOCKING PROBLEMS AND NO FINDINGS: name how many (MT-263's step 5); the graph will not build, so the
        // count is zero because nothing made it a finding.
        assertEquals(said(0, 3, true), I18n.f("autosetup.ui.errorCannotBuildDetail", 3),
            "three blocking problems are reported as one thing to deal with, which is what DY3-C7 filed"
            + " and what two of the three doors went on doing");

        // EXACTLY ONE: the singular of the same sentence.
        assertEquals(said(0, 1, true), I18n.t("autosetup.ui.errorCannotBuildDetailOne"),
            "one blocking problem is not reported in the singular");

        // A SETUP THAT WILL NOT RUN FOR SOME OTHER REASON still does not send him to the trains:
        // `hasErrors()` is wider than either count, and that width is the whole finding.
        assertEquals(said(0, 0, true), I18n.t("autosetup.ui.errorCannotBuildDetailOne"),
            "a setup that hasErrors() with neither count above zero fell through to the wait-for-trains"
            + " sentence, which is the defect V31-C1 and V32-C1 both named");

        // NOTHING WRONG WITH THE SETUP: the only state where waiting for trains is the answer.
        assertEquals(said(0, 0, false), I18n.t("autolayout.errorUnableToStartAutonomyWaitForTrains"),
            "with nothing wrong with the setup the refusal no longer says the trains are still running,"
            + " which is the only thing left for it to be about");

        // AND THE FOUR ANSWERS ARE FOUR.  Without this the claims above would pass on a rule that had
        // collapsed into one sentence for every state.
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(
            said(2, 0, true), said(0, 3, true), said(0, 1, true),
            said(0, 0, false))).size(), 4,
            "two of the four states give the same sentence, so the rule is not telling them apart");
    }

    /**
     * All three doors take their words from that one rule.  Read rather than driven (one is a modal dialog, one a
     * grayed tooltip built inside a menu, the third throws from the scripting API): none may word the refusal itself
     * any more, so this looks for the ternaries that are gone, not only for the call that replaced them.
     */
    @Test
    public void testNoDoorWordsItItself() throws Exception
    {
        // The program's code, comments blanked: two mentions of the singular sentence in the main window are comments
        // about where the choosing was, and a count that reads those counts history.
        String ui = SourceOf.allCode();

        // The diagram menu's file, whole; the autonomy page has a sentence of its own, so it is left out of the counts
        // that are about the main window.
        String menu = SourceOf.withoutComments(SourceOf.fileDeclaring("final class LayoutRightclickAutonomyMenu extends"));

        String page = SourceOf.withoutComments(SourceOf.fileDeclaring("public class AutonomyViewerPanel extends"));

        assertTrue(ui.contains("whyAutonomyWillNotStart(int errors, int blocking, boolean broken)"),
            "the one rule has gone, so there is nothing for the three doors to agree through");

        // The wait-for-trains sentence may be named ONCE - inside the rule - and nowhere else.
        int waits = SourceOf.occurrences("errorUnableToStartAutonomyWaitForTrains");

        assertEquals(waits, 1,
            "the wait-for-trains sentence is chosen in " + waits + " places, so a door can send"
            + " the operator to the trains again without the rule saying so");

        assertEquals(count(menu, "errorUnableToStartAutonomyWaitForTrains"), 0,
            "the diagram menu still words the refusal itself, and that is the door that read 'one thing'"
            + " for any number of blocking problems");

        // Directly, or through the one reading of Start's sentence and the hand doors', which asks the rule.
        assertTrue(menu.contains("whyAutonomyWillNotStart()") || (menu.contains("whyStartAndAHandSendAreRefused()")
            && ui.contains("whyAutonomyWillNotStart(errors, blocking, broken)")),
            "the greyed Start item's tooltip does not come from the rule");

        int singular = SourceOf.occurrences("errorCannotBuildDetailOne") - count(page, "errorCannotBuildDetailOne");

        assertEquals(singular, 1,
            "the singular sentence is chosen in " + singular + " places outside the autonomy page"
            + " (which words its own); it belongs to the rule alone");
    }

    /** What the rule says for a given state. */
    private static String said(int errors, int blocking, boolean broken) throws Exception
    {
        Method rule = TrainControlUI.class.getDeclaredMethod("whyAutonomyWillNotStart",
            int.class, int.class, boolean.class);

        rule.setAccessible(true);

        return (String) rule.invoke(null, errors, blocking, broken);
    }

    /** How many times a name appears in a file. */
    private static int count(String source, String what)
    {
        int seen = 0;

        for (int at = source.indexOf(what); at >= 0; at = source.indexOf(what, at + 1)) seen++;

        return seen;
    }
}
