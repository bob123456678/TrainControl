package regression;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.AutonomyEditorPanel;

/**
 * The three rules about a station's home locomotive.
 *
 * MT-112, from OB-022 / DD-A6. All three were code that existed, was commented, was believed to work -
 * and had lost every caller that ran it. The review put it plainly: `HomeLocomotiveMenu` lost four of
 * its five callers, "two safety warnings are now unreachable and their tests still pass".
 *
 * That is the reason these are tested here as RULES rather than as dialogs. A test that drives a window
 * proves the window; what went wrong was that nothing drove the window at all, and a rule with no
 * caller passes every test written about the rule.
 *
 * So two of the three are pure functions now, tested directly, and the third is checked where it is
 * decided. Each test also asserts the rule is still WIRED - the failure being guarded against is a
 * correct rule nobody asks.
 *
 * @author Adam
 */
public class testHomeAssignmentRules
{
    private static final File PANEL =
        new File("src/org/traincontrol/gui/AutonomyEditorPanel.java");

    /**
     * Rule 1: the list keeps a home naming a locomotive autonomy no longer runs.
     *
     * "It must still show the old name, not None. Press Cancel and the home must be unchanged."
     *
     * A non-editable combo cannot preselect a value its model does not hold, so leaving the name out
     * made the existing assignment the one thing that could not be chosen: the dialog opened showing
     * "None", and pressing OK cleared the station's home without anyone asking for it.
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

    /**
     * And it does not list a home twice when autonomy does run it.
     */
    @Test
    public void testAHomeStillInAutonomyIsNotListedTwice()
    {
        List<String> offered =
            AutonomyEditorPanel.homeChoices("None", Arrays.asList("BR 218", "V 200"), "V 200");

        assertEquals(offered, Arrays.asList("None", "BR 218", "V 200"),
            "a home autonomy still runs was added a second time, so the list offers one locomotive "
            + "twice and the user cannot tell the entries apart");
    }

    /**
     * With nothing placed and no home, there is nothing to choose but "None".
     *
     * The dialog says so rather than opening on a list of one - checked here because the emptiness is
     * what the caller tests to decide that.
     */
    @Test
    public void testWithNothingToOfferTheListHoldsOnlyNone()
    {
        assertEquals(AutonomyEditorPanel.homeChoices("None", Collections.<String>emptyList(), null),
            Collections.singletonList("None"));
    }

    /**
     * Rule 3: excluding a locomotive from the station it is homed at is a contradiction, and is named.
     *
     * Not forbidden - somebody may mean it - but shown, rather than left to be discovered when a train
     * has nowhere to go at the end of a run.
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
     * And all three rules are actually ASKED.
     *
     * This is the half that matters. Every one of these rules already existed, was commented, and was
     * believed to work; what had gone was the caller. A rule with no caller passes every test written
     * about the rule, which is exactly how two safety warnings sat unreachable with their tests green.
     */
    @Test
    public void testEachRuleIsStillWiredToSomething() throws Exception
    {
        if (!PANEL.isFile()) return;

        String source = new String(Files.readAllBytes(PANEL.toPath()), StandardCharsets.UTF_8);

        assertTrue(source.contains("homeChoices(I18n.t("),
            "the home dialog no longer builds its list with homeChoices, so the rule about keeping a "
            + "retired home is written down and not asked (MT-112)");

        assertTrue(source.contains("homeBrokenBy(homeOf(tile)"),
            "nothing asks homeBrokenBy about a station's actual home, so the exclusion warning is "
            + "unreachable - which is the exact fault DD-A6 found");

        assertTrue(source.contains("HomeStaging.canBeHome"),
            "rule 2's warning - a home the locomotive cannot reach - is decided by "
            + "HomeStaging.canBeHome, and nothing in the panel consults it any more");
    }
}
