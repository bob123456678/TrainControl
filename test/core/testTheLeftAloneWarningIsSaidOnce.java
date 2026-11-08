package core;

import java.util.Arrays;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore.Reconciliation;
import org.traincontrol.gui.autonomy.AutonomyReport;

/**
 * "The setup was left alone" is said once for a set of missing pages, not on every save (MT-380): a given set is
 * warned about once in a session, and again only when the set changes.
 *
 * Asked of `AutonomyReport.worthSaying`, the rule the dialog asks, so no dialog is shown here.  Each claim uses page
 * names of its own: the memory of what has been said lasts the session.
 *
 * @author Adam
 */
public class testTheLeftAloneWarningIsSaidOnce
{
    /**
     * The first refusal for a set of pages is said; the same refusal again is not.
     *
     * MUTATION, run: making `worthSaying` answer `report.wasDeclined()` alone - which is what the dialog
     * did before - fails the second assertion.
     */
    @Test
    public void testTheSameMissingPagesAreSaidOnce()
    {
        Reconciliation first = Reconciliation.declined(Arrays.asList("Once A", "Once B"));

        assertTrue(AutonomyReport.worthSaying(first),
            "the first refusal to tidy around two missing pages was not said at all, so the warning"
            + " that tells somebody a page has not loaded has stopped working");

        assertFalse(AutonomyReport.worthSaying(Reconciliation.declined(Arrays.asList("Once A", "Once B"))),
            "the same refusal was said again. Adam, MT-380: it \"shows up after every time the autonomy"
            + " editor is opened\" - and it \"should stop repeating\"");

        assertFalse(AutonomyReport.worthSaying(Reconciliation.declined(Arrays.asList("Once B", "Once A"))),
            "the same two pages in the other order were said again, so the memory is of how the report"
            + " happened to list them rather than of what it is about");
    }

    /**
     * A different set of missing pages is a new thing to say: the control, since a warning that learned to say nothing
     * ever again would pass the claim above.
     */
    @Test
    public void testADifferentSetIsSaidAgain()
    {
        assertTrue(AutonomyReport.worthSaying(Reconciliation.declined(Arrays.asList("Change A"))),
            "precondition: a first refusal was not said, so nothing below is about a change");

        assertTrue(AutonomyReport.worthSaying(
            Reconciliation.declined(Arrays.asList("Change A", "Change B"))),
            "a second page went missing and nothing was said, because the first warning was taken as"
            + " covering it. A different set of missing pages is news.");
    }

    /** And a save that was not declined is never a reason to say it. */
    @Test
    public void testASaveThatTidiedSaysNothingHere()
    {
        assertFalse(AutonomyReport.worthSaying(new Reconciliation()),
            "a save that was not refused was reported as one worth warning about");

        assertFalse(AutonomyReport.worthSaying(null), "a missing report was reported as worth saying");
    }
}
