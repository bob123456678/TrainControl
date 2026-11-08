package regression;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.GraphReducer;
import org.traincontrol.automationui.TileGraph;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.AutonomyOverlayToggle;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.file.CS2File;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.SourceOf;

/**
 * An error stops the setup running, and everything that offers to run it asks the same question (OB-090): starting
 * autonomy is refused on any ERROR finding (OB-057), so the diagram strip must not decide from hasBlockingProblems(),
 * which asks the GRAPH only.  ONE definition of broken, AutonomySession.errorCount(), serves both.
 *
 * The sample layout parses with no Central Station, so all its errors are blocking graph problems and "graph builds
 * cleanly, setup still must not run" cannot be reached; every assertion is therefore a delta, so none becomes a test
 * about the fixture.
 */
public class testErrorsStopTheSetupRunning
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = support.TestStation.simulated();
    }

    /**
     * There are errors the blocking question cannot see, which is why the strip must not ask it.  The mutation is a
     * name, so the blocking count comes back identical while the error count goes up by one.
     */
    @Test
    public void testAnUnnamedStationIsAnErrorNoBlockingProblemAccountsFor() throws Exception
    {
        AutonomySession session = openOnACopy();

        int errorsBefore = session.errorCount();
        int blockingBefore = blockingProblems(session);

        TileKey station = aNamedStation(session);

        // THE MUTATION: take a station's name away.  An ERROR, and not a blocking problem.
        session.getStore().setPointName(station, "");
        session.rebuild();

        assertEquals(session.errorCount(), errorsBefore + 1,
            "unnaming a station did not add exactly one error, so either the check is not running or "
            + "the mutation disturbed something else - and both make the rest of this meaningless");

        assertEquals(blockingProblems(session), blockingBefore,
            "the blocking count moved when only a NAME changed.  If that is so this mutation is not "
            + "the clean demonstration it is meant to be");

        assertTrue(session.hasErrors(),
            "the setup has an error and will not admit it.  hasErrors() is asked by the guard and by "
            + "canStartAutonomy() now - see testTheAffordancesAskTheGuardsOwnQuestion below, which is "
            + "what keeps those two together (OB-090, DD-A6, TS3-B6)");

        // The heart of it: an error arrived that no blocking problem accounts for.
        assertTrue(session.errorCount() > blockingProblems(session),
            "there is now an error that is NOT a blocking problem, and the two counts say otherwise. "
            + "hasBlockingProblems() is what the diagram strip used to decide from, and this is the "
            + "gap that let it show a live Start button over a setup that would not start.  errors="
            + session.errorCount() + " blocking=" + blockingProblems(session));

        // And back again: the error is a reason, not a latch.
        session.getStore().setPointName(station, "Named Again");
        session.rebuild();

        assertEquals(session.errorCount(), errorsBefore,
            "naming the station again did not clear the error it caused");
    }

    /**
     * The count the strip shows and the count the refusal uses are the same count: they once were not because each
     * counted for itself, and nothing in the compiler stops that recurring.
     */
    @Test
    public void testTheErrorCountIsExactlyTheErrorFindings() throws Exception
    {
        AutonomySession session = openOnACopy();

        // Something of our own to count, so this is not the trivially-equal case.
        session.getStore().setPointName(aNamedStation(session), "");
        session.rebuild();

        int counted = 0;

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) counted++;
        }

        assertTrue(counted > 0, "the fixture was supposed to have an error in it by now");

        assertEquals(session.errorCount(), counted,
            "errorCount() and the list the editor shows disagree, so the number on the diagram is "
            + "not the number of things the editor will make you fix (OB-090)");
    }

    /**
     * Warnings and notices are not errors, the other half of Adam's rule ("it runs on warnings").  The cheap fix for
     * OB-090, asking whether the checks found ANYTHING, passes the tests above and makes a railway with one unlabeled
     * siding unstartable; the sample layout carries far more soft findings than errors.
     */
    @Test
    public void testWarningsAndNoticesAreNotCountedAsErrors() throws Exception
    {
        AutonomySession session = openOnACopy();

        int errors = 0;
        int soft = 0;

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (finding.getSeverity() == AutonomyChecks.Severity.ERROR) errors++;
            else soft++;
        }

        assertTrue(soft > 0,
            "the sample layout is expected to carry warnings and notices; with none there is nothing "
            + "to prove here");

        assertEquals(session.errorCount(), errors,
            "errorCount() is counting things that are not errors.  " + soft + " of the findings on "
            + "this layout are warnings or notices, and a setup carrying only those has to start");

        assertTrue(session.errorCount() < errors + soft,
            "every finding is being counted as an error, so nothing could ever be merely worth "
            + "looking at");
    }

    /**
     * The affordances that offer to start autonomy ask the same question the refusal asks: `hasErrors()` through
     * `TrainControlUI.autonomyHasErrors()`, not the narrower graph-only question (OB-090).
     *
     * MUTATION this catches: change `canStartAutonomy()`'s last term from `!autonomyHasErrors()` to
     * `autonomyErrorCount() == 0`; a graph that will not build is not an error that count sees.
     */
    @Test
    public void testTheAffordancesAskTheGuardsOwnQuestion() throws Exception
    {
        String canStart = code("public boolean canStartAutonomy()");

        // THE GUARD'S QUESTION AND THE AFFORDANCE'S, PINNED AS A PAIR.  The guard asks the SESSION (`hasErrors()`) and
        // the affordance asks the WINDOW's wrapper (`autonomyHasErrors()`), so the two literals differ by design and no
        // textual comparison can derive one from the other.  Both are named in one place, so widening one and not the
        // other fails here rather than on Adam's railway.
        String guard = code("    String whyAutonomyStartIsRefused()");

        assertTrue(guard.contains("hasErrors()"),
            "the guard no longer asks hasErrors().  If that is deliberate the affordance below has to "
            + "follow it, and this rule is what makes sure it does.  Guard: " + guard);

        assertTrue(canStart.contains("autonomyHasErrors()"),
            "canStartAutonomy() does not ask the guard's own question.  The guard refuses on "
            + "hasErrors(), which also covers a graph that will not build at all, and an affordance "
            + "asking anything narrower shows a live Start over a setup that refuses every press - "
            + "which is OB-090, twice already.  canStartAutonomy: " + canStart);

        assertFalse(canStart.contains("hasBlockingProblems()"),
            "canStartAutonomy() is asking hasBlockingProblems() - the narrower, graph-only question "
            + "OB-090 was about, blind to an unnamed station or any other check-only error");

        // AND THE WRAPPER HAS TO REACH THE SESSION'S QUESTION: gutting it to `return false;` would pass every literal
        // above, with every affordance offering Start over a setup that refuses each press.
        String wrapper = code("public boolean autonomyHasErrors()");

        assertTrue(wrapper.contains("hasErrors()"),
            "autonomyHasErrors() no longer asks the session's own question, so the affordances and the "
            + "guard can agree word for word and mean different things.  Body: " + wrapper);

        // THE OTHER TWO AFFORDANCES: the strip's `fixing` flag is a DECISION, so what must match the guard is what
        // DECIDES; the menu still reads the count for its tooltip.  THE BODY, not the file: a whole-file `contains` is
        // satisfied by the method name appearing in a COMMENT.
        String sync = code("public final void syncRun()");

        assertTrue(sync.contains("autonomyHasErrors()"),
            "the diagram strip decides Start-versus-Fix without asking the guard's own question, so "
            + "it can offer a Start that every press refuses - which is OB-090, at the site OB-090 is "
            + "named for.  Body: " + sync);

        // AND THE ANSWER HAS TO REACH THE DECISION: a body that asks the question and drops the answer
        // (`boolean broken = ui.autonomyHasErrors(); fixing = source == start && errors > 0;`) passes every rule above,
        // so the assignment itself is read.
        int asked = sync.indexOf("autonomyHasErrors()");
        int decided = sync.indexOf("fixing =");

        assertTrue(decided > asked,
            "syncRun no longer decides `fixing` after asking the guard's question - so either the "
            + "decision has moved or the question is being asked and thrown away.  Body: " + sync);

        String decision = sync.substring(decided, Math.min(sync.length(), decided + 200));

        assertTrue(decision.contains("broken"),
            "the strip asks the guard's question and then decides on something else, which is OB-090 "
            + "with the fix's own words still in the file.  The decision reads: " + decision);

        // THE MENU'S HALF (MT-263): the tooltip is carried by `whyAutonomyWillNotStart()`, which holds the wording AND
        // the counting.  The pair is asked of the things that carry it: the item's ENABLED state comes from the guard's
        // own question, its REASON from the one rule, and the one rule reaches the number.  WITHOUT COMMENTS, over the
        // constructor that builds the item, whose prose mentions `autonomyErrorCount` twice.
        String menuCode = code("private LayoutRightclickAutonomyMenu(");

        assertTrue(menuCode.contains("canStartAutonomy()"),
            "the right-click Start item no longer takes its enabled state from the guard's own "
            + "question, so it can offer a Start that every press refuses - OB-090, at the third of "
            + "its three sites");

        // Through Start's rule, or through the one reading that works out Start's sentence and the
        // hand doors' together - which has to reach the rule and the numbers itself.
        assertTrue(menuCode.contains("whyAutonomyWillNotStart()")
            || menuCode.contains("whyStartAndAHandSendAreRefused()"),
            "the right-click Start item is greyed with nothing saying why.  The number used to be read "
            + "here for the tooltip; MT-263 moved the wording and the counting into "
            + "whyAutonomyWillNotStart(), and one of the two has to be present or the operator meets a "
            + "dead item and no reason");

        String both = code("public String[] whyStartAndAHandSendAreRefused()");

        assertTrue(both.contains("whyAutonomyWillNotStart(errors, blocking, broken)")
            && both.contains("errorCount()") && both.contains("blockingProblemCount()"),
            "the reading that gives the menu Start's sentence no longer counts, or no longer asks the one "
            + "rule with what it counted.  Body: " + both);

        // AND THE ONE RULE HAS TO REACH THE NUMBER, which is the correspondence the assertion above
        // cannot see: a `whyAutonomyWillNotStart` that stopped counting would leave every affordance
        // asking it and saying nothing about how many problems there are.
        String why = code("public String whyAutonomyWillNotStart()");

        assertTrue(why.contains("autonomyErrorCount()"),
            "whyAutonomyWillNotStart() no longer asks how many errors there are, so every affordance "
            + "that now takes its reason from it - the menu item, the load door, the scripting API - "
            + "reports a count of nothing.  Body: " + why);
    }

    /**
     * One declaration's code, found wherever the program keeps it, with its comments taken out so a check does not
     * pass on the strength of prose describing code that has gone.
     */
    private static String code(String signature)
    {
        return SourceOf.withoutComments(SourceOf.declaration(signature));
    }

    /**
     * How many problems would stop the graph being built, counted from the graph and the reducer directly: the point of
     * these tests is that it is a different question from check().
     */
    private static int blockingProblems(AutonomySession session)
    {
        int blocking = 0;

        for (TileGraph.Problem problem : session.getGraph().getProblems())
        {
            if (problem.isBlocking()) blocking++;
        }

        for (TileGraph.Problem problem : session.getReducer().getProblems())
        {
            if (problem.isBlocking()) blocking++;
        }

        return blocking;
    }

    /**
     * A station that currently has a name, so that taking it away is a change.
     */
    private static TileKey aNamedStation(AutonomySession session)
    {
        for (Map.Entry<TileKey, GraphReducer.ReducedPoint> entry
            : session.getReducer().getPoints().entrySet())
        {
            GraphReducer.ReducedPoint point = entry.getValue();

            if (point.isStation() && point.getName() != null && !point.getName().trim().isEmpty())
            {
                return entry.getKey();
            }
        }

        fail("the sample layout has no named station, so there is nothing to unname");

        return null;
    }

    /**
     * A session over a throwaway copy of the setup, because these tests write.
     */
    private static AutonomySession openOnACopy() throws Exception
    {
        File folder = new File(System.getProperty("user.dir"), "test/test_layout");

        assertTrue(folder.isDirectory(), "sample layout not found at " + folder.getAbsolutePath());

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = support.LayoutSandbox.wired(model, parser);

        File temp = File.createTempFile("tc-errors", "");

        assertTrue(temp.delete(), "making room for a directory of the same name");
        assertTrue(new File(temp, "config/autonomy").mkdirs(), "could not make the copy");

        File from = new File(folder, "config/autonomy");

        for (File one : from.listFiles())
        {
            if (one.isFile())
            {
                java.nio.file.Files.copy(one.toPath(),
                    new File(temp, "config/autonomy/" + one.getName()).toPath());
            }
        }

        temp.deleteOnExit();

        AutonomySession session = new AutonomySession(temp);

        assertTrue(session.isUsable(), "the sample layout ships a setup for this to open");

        session.open(pages);

        return session;
    }
    /**
     * The strip grays the stop it has already carried out, rather than removing it (OB-143): Graceful Stop disables
     * itself at once while autonomy keeps running, so "neither enabled" must not mean hide there; the last case,
     * "neither enabled" for unrelated causes, must not change.  The rule is exercised rather than the panel.
     *
     * MUTATION: return HIDDEN for the pending case and the third assertion fails; return STOP_PENDING
     * whenever neither is enabled and the last one fails.
     */
    @Test
    public void testAStopBeingCarriedOutIsShownGreyedRatherThanHidden()
    {
        assertEquals(AutonomyOverlayToggle.runButtonFor(true, false, true, false, false),
            AutonomyOverlayToggle.RunButton.STOP,
            "autonomy is running and can be stopped, so the strip must offer the stop");

        assertEquals(AutonomyOverlayToggle.runButtonFor(true, false, true, true, false),
            AutonomyOverlayToggle.RunButton.STOP,
            "with both enabled the stop still has to win - once trains are moving, stopping them is "
            + "the only thing worth offering, and it has to appear where the start was rather than "
            + "beside it");

        assertEquals(AutonomyOverlayToggle.runButtonFor(true, false, false, false, true),
            AutonomyOverlayToggle.RunButton.STOP_PENDING,
            "a graceful stop has been asked for and the trains are still finishing, which is exactly "
            + "the window OB-143 is about: the button must stay where it is and go grey, not vanish "
            + "and be replaced seconds later");

        assertEquals(AutonomyOverlayToggle.runButtonFor(true, false, false, false, false),
            AutonomyOverlayToggle.RunButton.HIDDEN,
            "neither button is available and no stop is pending, so there is nothing to offer - "
            + "showing a greyed Graceful Stop to somebody who has never started anything would be "
            + "worse than showing nothing");

        assertEquals(AutonomyOverlayToggle.runButtonFor(true, false, false, true, false),
            AutonomyOverlayToggle.RunButton.START,
            "autonomy can be started, so the strip must offer the start");

        // Not loaded, and the page left out of the setup: both hide whatever else is true, a pending stop included.
        assertEquals(AutonomyOverlayToggle.runButtonFor(false, false, true, true, true),
            AutonomyOverlayToggle.RunButton.HIDDEN,
            "no setup is loaded, so the strip has nothing to stand in for");

        assertEquals(AutonomyOverlayToggle.runButtonFor(true, true, true, true, true),
            AutonomyOverlayToggle.RunButton.HIDDEN,
            "this page is excluded from the setup, so the strip has nothing to stand in for");
    }

    /**
     * The strip actually asks that question, and grays the button when the answer says to: the rule is only in force if
     * `syncRun` calls it and acts on the answer, so both halves are asserted.
     */
    @Test
    public void testTheStripAsksThatQuestion() throws Exception
    {
        String body = code("public final void syncRun()");

        assertTrue(body.contains("runButtonFor("),
            "syncRun no longer asks runButtonFor, so the rule above is not the one the strip follows "
            + "and could drift from it without anything failing");

        assertTrue(body.contains("setEnabled(showing != RunButton.STOP_PENDING)"),
            "syncRun no longer greys the button for a pending stop. Choosing to show the stop is only "
            + "half of OB-143: shown but still clickable, it invites a second press that "
            + "requestStopAutonomy answers with an error saying the stop was already issued");
    }

    /**
     * The autonomy strip is not the scroll pane's column header (OB-148): a column header scrolls with the view and
     * would repaint a panel as wide as the diagram on every sideways step (Adam: "if a track diagram is wide, the
     * header above a track diagram flickers while scrolling sideways").  The swap is made at runtime through
     * GroupLayout.replace, so this checks it is still there and the column header is not being set again.
     */
    @Test
    public void testTheStripIsNotTheScrollPanesColumnHeader() throws Exception
    {
        String ui = SourceOf.declaration("    void ensureDiagramStrip()");

        int from = ui.indexOf("autonomyDiagramStrip = new javax.swing.JPanel");

        assertTrue(from >= 0, "the strip is no longer built where this test can see it");

        String body = SourceOf.withoutComments(ui.substring(from, Math.min(ui.length(), from + 4000)));

        assertTrue(body.contains(".replace(ui.layoutArea(), stacked)"),
            "the strip is no longer moved out of the scroll pane with GroupLayout.replace, so it is "
            + "back to scrolling sideways with the diagram - which is OB-148's flicker and takes the "
            + "controls off screen with it");

        int header = body.indexOf("setColumnHeaderView(strip)");

        // The fallback still names it, for a form whose layout is no longer a GroupLayout: only reachable when the swap
        // could not be made.
        if (header >= 0)
        {
            assertTrue(body.lastIndexOf("else") < header && body.lastIndexOf("else") > 0,
                "setColumnHeaderView(strip) is called outside the fallback branch, so the strip is "
                + "being made a column header again after being taken out of one");
        }
    }

    /**
     * All three "why can't I start" messages name the third reason: the guard refuses on `hasErrors()`, which covers a
     * graph that will not build, where `autonomyErrorCount()` is zero.  The guard, the API's exception and the grayed
     * item's tooltip all need that arm; asked of the source, since a tooltip cannot be read back without a model.
     *
     * MUTATION this catches: deleting the `autonomyHasErrors()` arm from any one of the three.
     */
    @Test
    public void testEveryRefusalNamesTheReasonACountCannotSee() throws Exception
    {
        String[][] sites =
        {
            {"the guard itself", code("    String whyAutonomyStartIsRefused()")},
            {"the API's exception", code("public void requestStartAutonomy() throws Exception")},
            // THE CONSTRUCTOR for this one: the tooltip is built inside the menu's constructor.
            {"the greyed Start item's tooltip", code("private LayoutRightclickAutonomyMenu(")},
        };

        // ASKED, AND COUNTED.  A site is in scope if it words the refusal ITSELF or delegates to the one rule (MT-263
        // moved the wording into `whyAutonomyWillNotStart`), and the floor below fails if none of them does.
        int asked = 0;

        for (String[] site : sites)
        {
            String where = site[0];
            String body = site[1];

            assertFalse(body.isEmpty(), where + " has moved or been renamed");

            boolean wordsItHere = body.contains("errorCannotStartWithErrors");
            boolean throughTheRule = body.contains("whyAutonomyWillNotStart()")
                || body.contains("whyStartAndAHandSendAreRefused()");

            if (!wordsItHere && !throughTheRule) continue;

            asked++;

            // WHAT IS LEFT FOR THE LOOP TO SAY, once no door words the refusal itself: every refusal reaches the one
            // rule.  The arms are asserted OF THAT RULE below; a branch that `testNoDoorWordsItItself` forbids anything
            // to enter would not be a guard.
        }

        assertTrue(asked == sites.length,
            "only " + asked + " of the " + sites.length + " refusals words the refusal or asks the one"
            + " rule for it, so the rest are saying something this check cannot see.  That is how this"
            + " loop came to assert nothing: the wording moved and every site fell through the gate");

        // AND THE ONE RULE ASKS ALL THREE THINGS: the error count, the blocking count, and whether the setup is broken
        // at all.  The no-arg wrapper is the door every site calls; nothing else asserted its third arm.
        String wrapper = code("public String whyAutonomyWillNotStart()");

        assertTrue(wrapper.contains("autonomyErrorCount()"),
            "the rule every refusal delegates to no longer counts the errors, so all three report a "
            + "count of nothing.  Body: " + wrapper);

        assertTrue(wrapper.contains("blockingProblemCount()"),
            "the rule no longer asks how many problems would stop the setup being built, which is the "
            + "arm that distinguishes 'fix these' from 'wait for trains'.  Body: " + wrapper);

        assertTrue(wrapper.contains("autonomyHasErrors()") || wrapper.contains("hasErrors()"),
            "the rule no longer asks the guard's own question, so a setup that will not build at all - "
            + "where the count is zero - is described to the operator as waiting for trains that will "
            + "never run.  That is OB-090 at the one site all three refusals now share.  Body: "
            + wrapper);

        String rule = code("static String whyAutonomyWillNotStart(int errors, int blocking, boolean broken)");

        assertTrue(rule.contains("errorCannotStartWithErrors"),
            "the one rule no longer words the error-count refusal, so the three doors that delegate to"
            + " it say nothing about how many problems there are.  Body: " + rule);

        assertTrue(rule.contains("errorCannotBuildDetailOne"),
            "the one rule has no wording for the reason a count cannot see - a graph that will not build"
            + " at all, where the count is zero - so every door that delegates to it tells the operator"
            + " to wait for trains that will never run.  Body: " + rule);
    }
}
