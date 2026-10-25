package regression;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import support.SourceOf;

/**
 * The three rules about a station's home locomotive (MT-112).
 *
 * `HomeLocomotiveMenu` lost four of its five callers, so two safety warnings were unreachable while their tests passed.
 * A test that drives a window proves the window, and a rule with no caller passes every test written about the rule, so
 * two of the three are pure functions tested directly and the third is checked where it is decided.  Each test also
 * asserts the rule is still wired: the failure guarded against is a correct rule nobody asks.
 *
 * @author Adam
 */
public class testHomeAssignmentRules
{
    /**
     * Rule 1: the list keeps a home naming a locomotive autonomy no longer runs.  A non-editable combo cannot preselect
     * a value its model does not hold, so leaving the name out opened the dialog on "None" and OK cleared the home.
     */
    @Test
    public void testTheHomeListKeepsAHomeAutonomyNoLongerRuns()
    {
        List<String> placed = Arrays.asList("BR 218", "V 200");

        List<String> offered = AutonomyEditorPanel.homeChoices("None", placed, "BR 03 - retired");

        assertTrue(offered.contains("BR 03 - retired"),
            "the station's own home is missing from the list of homes it may be given. A combo cannot "
            + "preselect what it does not hold, so the dialog opens on \"None\" and OK clears the "
            + "home nobody asked to clear (MT-112)");

        assertEquals(offered.get(0), "None", "\"None\" must stay first - it is the way to clear a home");

        assertEquals(offered.get(1), "BR 03 - retired",
            "the current home belongs second, where the eye lands, next to the only other answer that "
            + "is not a locomotive");

        assertEquals(offered.subList(2, offered.size()), placed,
            "the locomotives autonomy runs were reordered or lost");
    }

    /** And it does not list a home twice when autonomy does run it. */
    @Test
    public void testAHomeStillInAutonomyIsNotListedTwice()
    {
        List<String> offered =
            AutonomyEditorPanel.homeChoices("None", Arrays.asList("BR 218", "V 200"), "V 200");

        assertEquals(offered, Arrays.asList("None", "BR 218", "V 200"),
            "a home autonomy still runs was added a second time, so the list offers one locomotive "
            + "twice and the user cannot tell the entries apart");
    }

    /** With nothing placed and no home, there is nothing to choose but "None"; the caller tests the emptiness. */
    @Test
    public void testWithNothingToOfferTheListHoldsOnlyNone()
    {
        assertEquals(AutonomyEditorPanel.homeChoices("None", Collections.<String>emptyList(), null),
            Collections.singletonList("None"));
    }

    /**
     * Rule 3: excluding a locomotive from the station it is homed at is a contradiction, and is named, not forbidden.
     */
    @Test
    public void testExcludingALocomotiveFromItsOwnHomeIsReported()
    {
        assertEquals(AutonomyEditorPanel.homeBrokenBy("BR 218", Arrays.asList("V 200", "BR 218")),
            "BR 218", "shutting a locomotive out of the station it is homed at was not reported");

        assertNull(AutonomyEditorPanel.homeBrokenBy("BR 218", Arrays.asList("V 200")),
            "an exclusion that has nothing to do with the home was reported as breaking it");

        assertNull(AutonomyEditorPanel.homeBrokenBy(null, Arrays.asList("V 200")),
            "a station with no home cannot have one broken");

        assertNull(AutonomyEditorPanel.homeBrokenBy("BR 218", null));
    }

    /**
     * And where the list names the only locomotives allowed (FR-121), the home it breaks is the one it leaves OUT.
     *
     * MUTATION: read an allow-only list as a list of exclusions when asking about the home, and this fails.
     */
    @Test
    public void testAnAllowOnlyListThatLeavesOutTheHomeIsReported()
    {
        assertEquals(AutonomyEditorPanel.homeBrokenBy("BR 218", Arrays.asList("V 200"), true), "BR 218",
            "letting in only locomotives other than the one homed at the station was not reported");

        assertNull(AutonomyEditorPanel.homeBrokenBy("BR 218", Arrays.asList("V 200", "BR 218"), true),
            "an allow-only list that names the home was reported as breaking it");
    }

    /** And all three rules are actually asked: what had gone was the caller. */
    @Test
    public void testEachRuleIsStillWiredToSomething() throws Exception
    {
        // The program's code, wherever the panel keeps these calls: a comment naming one is not a caller.
        String source = SourceOf.allCode();

        assertTrue(source.contains("homeChoices(I18n.t("),
            "the home dialog no longer builds its list with homeChoices, so the rule about keeping a "
            + "retired home is written down and not asked (MT-112)");

        assertTrue(source.contains("homeBrokenBy(homeOf(tile)"),
            "nothing asks homeBrokenBy about a station's actual home, so the exclusion warning is "
            + "unreachable - which is the exact fault DD-A6 found");

        // whyNotAHome, not canBeHome: canBeHome answers false for two unrelated reasons, and rolling them together
        // warned "no train can come to rest here" about a square that is two places.  The message key is named as well
        // as the method, because the method alone is satisfied by a panel that consults it and then acts on the wrong
        // half.
        assertTrue(source.contains("HomeStaging.whyNotAHome"),
            "rule 2's warning - a home the locomotive cannot reach - is decided by "
            + "HomeStaging.whyNotAHome, and nothing in the panel consults it any more");

        assertTrue(source.contains("autolayout.errorHomeCannotRestHere"),
            "the panel consults whyNotAHome but no longer names the reason it is about, so it is "
            + "warning about whichever reason came back - including the one the model refuses "
            + "outright, which is what LD-9 was");

        // The call sites, not only the bodies: the checks above find tokens inside private helpers, so deleting
        // `mayRestHere`'s single caller leaves them satisfied and rule 2 unreachable.  A private helper cannot be
        // reached from this package, so this reads the source one level up: proof the path is written down, not that it
        // runs.
        assertTrue(source.contains("mayRestHere(tile, picked)"),
            "nothing calls mayRestHere, so rule 2's warning - a home the locomotive cannot reach - "
            + "cannot be produced however correct the helper is. The token check above passes on the "
            + "helper's own body, which is why it did not notice (TA-B4, DD-A6)");

        // with the list's mode since FR-121: the home a list breaks depends on whether it keeps its locomotives out
        assertTrue(source.contains("homeBrokenBy(tile, picked, allowOnly)"),
            "nothing calls the homeBrokenBy overload that takes a tile, so the exclusion warning is "
            + "unreachable from the assignment path (TA-B4)");

        assertTrue(source.contains("() -> editor.promptHome("),
            "nothing opens the home dialog, so all three of these rules are unreachable at once and "
            + "the checks above would still pass (TA-B4)");
    }
}
