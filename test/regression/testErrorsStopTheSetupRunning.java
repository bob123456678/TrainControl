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

/**
 * An error stops the setup running, and everything that offers to run it asks the same question.
 *
 * Adam, OB-090: "the setup refuses to run when there are errors.  it runs on warnings, but on errors
 * it should say fix it."
 *
 * He was right about the rule and I had written the opposite into the ticket. Starting autonomy IS
 * refused on any ERROR finding - that is OB-057, and refuseAutonomyStartWhileBroken has done it ever
 * since. What had never happened is that the things which OFFER to start it were told. The diagram
 * strip decided what to show from hasBlockingProblems(), which asks the GRAPH only - scissors
 * crossings, unaddressed switches, unpaired links - and knows nothing of the CHECKS. Four unnamed
 * stations are four errors and no blocking problem, so the strip went on showing a live Start button
 * while every press of it was refused with a dialog.
 *
 * That is the OB-057 shape a third time: "it says there are errors, but the start autonomy button is
 * still visible." The fix is not another guard at another press site - it is that there is now ONE
 * definition of broken, AutonomySession.errorCount(), which both the refusal and the strip ask.
 *
 * **What this fixture can and cannot show.** The sample layout is parsed with no Central Station on
 * the other end, so every switch on it is unaddressed and all 79 of its errors are blocking graph
 * problems. There is therefore no way to reach the state "graph builds cleanly, setup still must not
 * run" on this fixture, and a test that asserted hasBlockingProblems() were false would be asserting
 * something about the network being down. So the gap is shown as a DELTA instead, which is stronger
 * anyway: a mutation that adds an error WITHOUT adding a blocking problem proves there are errors the
 * narrow question cannot see, whatever the starting counts happen to be.
 *
 * Every assertion here is a delta for the same reason. What the sample layout contains today is not
 * the subject, and a test that pins it becomes a test about the fixture the first time it is edited.
 */
public class testErrorsStopTheSetupRunning
{
    private static MarklinControlStation model;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
    }

    /**
     * There are errors the blocking question cannot see, which is why the strip must not ask it.
     *
     * The mutation is a name, so nothing about the TRACK changes: the blocking count must come back
     * identical while the error count goes up by one. An affordance deciding from the blocking count
     * is blind to exactly this, and this is the state Adam was in with his four unnamed stations.
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
     * The count the strip shows and the count the refusal uses are the same count.
     *
     * They were not, which is the whole of OB-090, and they were not because each side counted for
     * itself. Nothing in the compiler stops that recurring - two loops over check() that filter
     * differently look identical at a glance - so the equality is asserted rather than assumed.
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
     * Warnings and notices are not errors, which is the other half of Adam's rule.
     *
     * "it runs on warnings." Worth its own test because the cheap way to fix OB-090 - have the strip
     * ask whether the checks found ANYTHING - passes every assertion in the two tests above and
     * quietly makes a railway with one unlabelled siding unstartable. The sample layout carries far
     * more soft findings than errors, so an errorCount() that had swallowed them would be obvious
     * here.
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
     * The affordances that offer to start autonomy ask the same question the refusal asks.
     *
     * `AutonomySession.hasErrors()` - the method the test above exercises - has zero callers left in
     * `src/`: `grep -rn hasErrors src/` finds only its own declaration. What the guard
     * (`refuseAutonomyStartWhileBroken`) and the affordances (`TrainControlUI.canStartAutonomy`,
     * `AutonomyOverlayToggle`, `LayoutRightclickAutonomyMenu`) actually ask today is
     * `autonomyErrorCount()` / `errorCount()`. A test that only proves `hasErrors()` still computes
     * correctly is a rule tested and its call site left uncovered - DD-A6, named in this class's own
     * javadoc and then done anyway, which is TST-B21.
     *
     * This reads the affordances instead, and asks whether they still consult the guard's own number
     * rather than the narrower graph-only question OB-090 was about.
     *
     * MUTATION this catches: revert `canStartAutonomy()` to
     * `return this.startAutonomy != null && this.startAutonomy.isEnabled() && !session.hasBlockingProblems();`
     * (its shape before OB-090's third occurrence). Nothing above this test notices - the fixture's own
     * javadoc says there is no way to make `hasBlockingProblems()` disagree with the graph on this
     * layout - and OB-090 is back: four unnamed stations are four errors, no blocking problem, and the
     * Start button stays live over a setup that refuses every press.
     */
    @Test
    public void testTheAffordancesAskTheGuardsOwnQuestion() throws Exception
    {
        String ui = read("src/org/traincontrol/gui/TrainControlUI.java");
        String toggle = read("src/org/traincontrol/gui/AutonomyOverlayToggle.java");
        String menu = read("src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java");

        String canStart = withoutComments(bodyOf(ui, "public boolean canStartAutonomy()"));

        assertFalse(canStart.isEmpty(), "canStartAutonomy() has moved or been renamed");

        // THE GUARD'S QUESTION, WHICHEVER ONE THAT IS (TS3-B6).
        //
        // This used to require the literal `autonomyErrorCount()`, and that is how it came to ENFORCE
        // the divergence it exists to catch: the guard was widened to `hasErrors()` and this assertion
        // said the affordance must go on asking the narrower one.  It reads the guard and requires the
        // affordance to ask the same thing.
        String guard = withoutComments(bodyOf(ui, "private boolean refuseAutonomyStartWhileBroken()"));

        assertFalse(guard.isEmpty(), "refuseAutonomyStartWhileBroken() has moved or been renamed");

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

        // THE SAME FOR THE OTHER TWO AFFORDANCES, and asked the same way (V31-B1, V32-B1).
        //
        // These used to require the literal `autonomyErrorCount()`, and that is how this rule came to
        // pin the divergence in place a second time: the strip's `fixing` flag is a DECISION - it
        // decides whether a press opens the editor or clicks Start - and it went on asking the narrow
        // question while the guard asked the wide one.
        //
        // The COUNT is still read by both, and rightly: the strip says how many and colours its band,
        // the menu puts the number in its tooltip.  What must match the guard is what DECIDES.
        assertTrue(toggle.contains("autonomyHasErrors()"),
            "the diagram strip decides Start-versus-Fix without asking the guard's own question, so "
            + "it can offer a Start that every press refuses - which is OB-090, at the site OB-090 is "
            + "named for");

        assertTrue(toggle.contains("autonomyErrorCount()"),
            "AutonomyOverlayToggle no longer reads the error COUNT at all - it needs it to say how "
            + "many and to colour the band");

        assertTrue(menu.contains("autonomyErrorCount()"),
            "LayoutRightclickAutonomyMenu no longer reads autonomyErrorCount() at all - the right-click "
            + "Start item's own OB-090 fix has gone");
    }

    /**
     * A source file, whole.
     */
    private static String read(String path) throws java.io.IOException
    {
        return new String(java.nio.file.Files.readAllBytes(new File(path).toPath()),
            java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * A line with any comment - `//` or `/* *&#47;` - removed, so a check does not pass on the
     * strength of prose describing code that has gone. Copied rather than shared with the other tests
     * that do this: a test helper reaching into another test class is a dependency between things that
     * are supposed to fail independently.
     */
    private static String withoutComments(String body)
    {
        StringBuilder out = new StringBuilder();

        boolean inLine = false, inBlock = false;

        for (int i = 0; i < body.length(); i++)
        {
            char c = body.charAt(i);
            char next = i + 1 < body.length() ? body.charAt(i + 1) : ' ';

            if (inLine)
            {
                if (c == '\n') { inLine = false; out.append(c); }
            }
            else if (inBlock)
            {
                if (c == '*' && next == '/') { inBlock = false; i++; }
            }
            else if (c == '/' && next == '/') inLine = true;
            else if (c == '/' && next == '*') inBlock = true;
            else out.append(c);
        }

        return out.toString();
    }

    /**
     * The body of one method, braces included, or empty when the declaration cannot be found.
     */
    private static String bodyOf(String source, String declaration)
    {
        int at = source.indexOf(declaration);

        if (at < 0) return "";

        int open = source.indexOf('{', at + declaration.length());

        if (open < 0) return "";

        int depth = 0;

        for (int i = open; i < source.length(); i++)
        {
            char c = source.charAt(i);

            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return source.substring(at, i + 1);
        }

        return "";
    }

    /**
     * How many problems would stop the graph being built.
     *
     * Counted from the graph and the reducer directly rather than through check(), because the point
     * of these tests is that the two are different questions - taking this number from the same place
     * the other one comes from would beg it.
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

        List<LayoutDiagram> pages = parser.parseLayout(new LinkedList<MarklinAccessory>());

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
     * The strip greys the stop it has already carried out, rather than removing it (OB-143).
     *
     * Adam: "the button disappears rather than being greyed out prior to replacement by the start
     * button. make it get greyed out and then replaced."
     *
     * The strip mirrors whichever of the two real buttons is ENABLED. Pressing Graceful Stop disables
     * it at once while autonomy keeps running - `stopLocomotives()` returns immediately and the trains
     * coast on to their next station - so for that whole window neither was enabled, and "neither"
     * meant hide. The control vanished from under the hand that had just pressed it and came back
     * seconds later as a different button.
     *
     * The decision is exercised here rather than the panel, because the panel needs a window and the
     * thing that was wrong is a rule, not a paint. `testTheStripAsksThatQuestion` below is the other
     * half: a rule that nobody calls is a rule that is not in force.
     *
     * The case that must NOT change is the last one. "Neither button is enabled" has causes that have
     * nothing to do with stopping - most of them before anything has been started at all - and a
     * greyed Graceful Stop shown to somebody who has never run anything would be worse than the gap.
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

        // Not loaded, and the page left out of the setup: both hide whatever else is true, including
        // a pending stop, because a strip for a page that is not part of the setup has nothing to say
        // about it.
        assertEquals(AutonomyOverlayToggle.runButtonFor(false, false, true, true, true),
            AutonomyOverlayToggle.RunButton.HIDDEN,
            "no setup is loaded, so the strip has nothing to stand in for");

        assertEquals(AutonomyOverlayToggle.runButtonFor(true, true, true, true, true),
            AutonomyOverlayToggle.RunButton.HIDDEN,
            "this page is excluded from the setup, so the strip has nothing to stand in for");
    }

    /**
     * The strip actually asks that question, and greys the button when the answer says to.
     *
     * The rule above is only in force if `syncRun` calls it and acts on the answer. Extracting a rule
     * and testing the rule leaves the call site as the only uncovered part, which is how two defects
     * got into one method earlier in this project - so both halves are asserted.
     */
    @Test
    public void testTheStripAsksThatQuestion() throws Exception
    {
        String strip = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/AutonomyOverlayToggle.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int from = strip.indexOf("public final void syncRun()");

        assertTrue(from >= 0, "syncRun has moved or been renamed - this test is reading nothing");

        String body = withoutComments(strip.substring(from));

        assertTrue(body.contains("runButtonFor("),
            "syncRun no longer asks runButtonFor, so the rule above is not the one the strip follows "
            + "and could drift from it without anything failing");

        assertTrue(body.contains("setEnabled(showing != RunButton.STOP_PENDING)"),
            "syncRun no longer greys the button for a pending stop. Choosing to show the stop is only "
            + "half of OB-143: shown but still clickable, it invites a second press that "
            + "requestStopAutonomy answers with an error saying the stop was already issued");
    }

    /**
     * The autonomy strip is not the scroll pane's column header (OB-148).
     *
     * Adam: "if a track diagram is wide, the header above a track diagram flickers while scrolling
     * sideways" - the whole strip, not just the button on it.
     *
     * A column header is as wide as the view and scrolls horizontally with it. That is right for
     * column labels and wrong for a strip of controls: every sideways scroll step repainted a panel as
     * wide as the diagram, and the checkbox and Start button slid away from under the hand reaching
     * for them. Above the viewport it does neither.
     *
     * The swap is made at runtime through GroupLayout.replace because the container's layout comes
     * from the form, and a hand-edited GEN block is how cropOverlay disappeared twice. So what this
     * checks is that the runtime swap is still there and the column header is not being set again -
     * the two are easy to reintroduce separately, and either alone brings the flicker back.
     */
    @Test
    public void testTheStripIsNotTheScrollPanesColumnHeader() throws Exception
    {
        String ui = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int from = ui.indexOf("autonomyDiagramStrip = new javax.swing.JPanel");

        assertTrue(from >= 0, "the strip is no longer built where this test can see it");

        String body = withoutComments(ui.substring(from, from + 4000));

        assertTrue(body.contains(".replace(this.LayoutArea, stacked)"),
            "the strip is no longer moved out of the scroll pane with GroupLayout.replace, so it is "
            + "back to scrolling sideways with the diagram - which is OB-148's flicker and takes the "
            + "controls off screen with it");

        int header = body.indexOf("setColumnHeaderView(strip)");

        // The fallback still names it, for a form whose layout is no longer a GroupLayout - so this
        // asserts it is only reachable when the swap could not be made, not that the words are absent.
        if (header >= 0)
        {
            assertTrue(body.lastIndexOf("else") < header && body.lastIndexOf("else") > 0,
                "setColumnHeaderView(strip) is called outside the fallback branch, so the strip is "
                + "being made a column header again after being taken out of one");
        }
    }

}
