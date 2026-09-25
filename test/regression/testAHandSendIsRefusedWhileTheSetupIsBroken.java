package regression;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 * A train is not sent by hand while the setup has errors, any more than autonomy is started (MT-263).
 *
 * Adam, 2026-09-24: *"Start autonomy doesn't run autonomy, as expected - I get an error message saying errors must
 * first be fixed.  Good.  But trains can still be moved manually via both the track diagram viewer and the autonomy
 * tab, which should throw an error instead."*  A hand send runs over the railway the same setup built, so the setup
 * that stops autonomy starting stops a hand send too: the same question, `autonomyHasErrors`, at both hand doors - the
 * diagram's right-click destinations and the Auto tab's list of paths - and at the two run doors that dispatch without
 * Start, Execute Timetable and Return Home (TDU-B1).
 *
 * **Asked of the words, then of the doors**, as `testTheRefusalToStartSaysWhichThing` asks Start's: a behavioural
 * claim would have to raise the modal dialog each door shows, which is how a runner was stranded on 2026-09-09.
 *
 * MUTATION: take the refusal out of either door, or ask it after the reversal question, and the second claim fails
 * naming the door; make the window's rule answer nothing for a broken setup, or invert its guard, and the window's
 * claim does (TDD-C8, TDU-C3 - the first claim asks only the words).
 *
 * @author Adam
 */
public class testAHandSendIsRefusedWhileTheSetupIsBroken
{
    private static final String REFUSAL = "whyAHandSendIsRefused()";

    /**
     * The setup's own words, with however many things there are to deal with - and never autonomy's.
     *
     * @throws Exception from the reflection
     */
    @Test
    public void testTheRefusalSaysWhatIsWrongWithTheSetup() throws Exception
    {
        assertEquals(said(2, 0), I18n.f("autosetup.ui.errorCannotBuildDetail", 2),
            "with two error findings a hand send is not refused naming the two things to deal with");

        assertEquals(said(0, 3), I18n.f("autosetup.ui.errorCannotBuildDetail", 3),
            "with three problems that stop the setup being built, a hand send does not name the three");

        assertEquals(said(0, 1), I18n.t("autosetup.ui.errorCannotBuildDetailOne"),
            "one problem is not named in the singular");

        // A SETUP THAT WILL NOT BUILD WITH NO FINDING MADE OF IT is still refused, and in words: `hasErrors()` is wider
        // than either count, which is MT-263's own first finding.
        assertEquals(said(0, 0), I18n.t("autosetup.ui.errorCannotBuildDetailOne"),
            "a broken setup with neither count above zero is not refused in the setup's words");

        for (int errors = 0; errors < 3; errors++)
        {
            String words = said(errors, 0);

            assertNotEquals(words, I18n.f("autolayout.ui.errorCannotStartWithErrors", errors),
                "a hand send is refused in Start's words, which say AUTONOMY cannot start - not what was pressed");

            assertNotEquals(words, I18n.t("autolayout.errorUnableToStartAutonomyWaitForTrains"),
                "a hand send over a broken setup is told to wait for the trains");
        }
    }

    /**
     * Both hand doors ask it, before the reversal question and before anything is dispatched.
     *
     * @throws Exception from the files
     */
    @Test
    public void testBothHandDoorsAskItFirst() throws Exception
    {
        door("src/org/traincontrol/gui/AutoLocomotiveStatus.java", "private void locAvailPathsMouseClicked(",
            "the Auto tab's list of paths");

        door("src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "private JMenuItem destinationItem(",
            "the track diagram's right-click destinations");

        String window = read("src/org/traincontrol/gui/TrainControlUI.java");

        int rule = window.indexOf("public String " + REFUSAL);

        assertTrue(rule >= 0, "the window has no " + REFUSAL + " for the doors to ask");

        String body = window.substring(rule, window.indexOf("\n    }", rule));

        assertTrue(body.contains("autonomyHasErrors()"), REFUSAL + " does not ask autonomyHasErrors(), the question"
            + " Start is refused on - so a hand send and Start can disagree about the same setup: " + body);
    }

    /**
     * The two run doors that move trains without Start - Execute Timetable and Return Home - ask it too, before they grey
     * anything or dispatch (TDU-B1).
     *
     * The rule's own reason is not about the hand: a timetable run and a Return Home run drive over the railway the same
     * setup built, through the same dispatch, started by a button with nobody asking the setup.  With the graph broken,
     * both drove trains over the stale railway the operator had just been told "cannot be used yet".
     *
     * MUTATION: take the refusal out of either run door, and this fails naming it.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheRunDoorsAskItToo() throws Exception
    {
        String window = read("src/org/traincontrol/gui/TrainControlUI.java");

        for (String[] door : new String[][] {
            {"private void executeTimetableActionPerformed(", "this.executeTimetable.setEnabled(false);", "Execute Timetable"},
            {"public void requestReturnToHome()", "loadReturnToHomeTimetable(", "Return Home"}})
        {
            int start = window.indexOf(door[0]);

            assertTrue(start >= 0, "cannot find " + door[0] + " - if the door moved, move this");

            int asked = window.indexOf(REFUSAL, start);
            int acts = window.indexOf(door[1], start);

            assertTrue(acts > start, "precondition: " + door[2] + " no longer does " + door[1]);

            assertTrue(asked > start && asked < acts, door[2] + " runs trains without asking " + REFUSAL + " - over a setup"
                + " Start refuses (TDU-B1)");
        }
    }

    /**
     * The window refuses while its setup has an error, and not once it is mended - asked of a real window, not of the
     * words (TDU-C3).
     *
     * The first claim asks the sentence; the doors ask the window's own method, whose guard decides.  Inverted, or
     * answering nothing, it left both claims above green while no hand send was ever refused.
     *
     * MUTATION: invert the window's guard, or have it answer nothing, and this fails.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testABrokenSetupIsRefusedAndAMendedOneIsNot() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            org.traincontrol.marklin.MarklinControlStation model =
                org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui[0] = new TrainControlUI());

            ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);

            ui[0].whenTilesSettled(() -> settled.countDown());

            settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

            for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            // ON THE EVENT THREAD, where the editor makes every setup change - the window's own work reads the setup
            // there, and a change made from this thread raced it.
            final String[] outcome = new String[7];

            // RETURN HOME OFFERED BEFORE ANYTHING IS BROKEN (ADU-C6): otherwise the item is greyed for that, and the
            // greyed state below says nothing about the setup.
            java.lang.reflect.Method triage = TrainControlUI.class.getDeclaredMethod("awaitReturnHomeTriage", long.class);

            triage.setAccessible(true);
            triage.invoke(ui[0], 30000L);

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.automationui.AutonomySession session = ui[0].getAutonomySession();

                if (session == null) return;

                org.traincontrol.automationui.TileGraph.TileKey station = null;

                for (org.traincontrol.automationui.TileGraph.TileKey key : session.getStore().getNamedTiles())
                {
                    if (session.getStore().isStation(key)) station = key;
                }

                if (station == null) return;

                String name = session.getStore().getPointName(station);

                outcome[0] = String.valueOf(ui[0].whyAHandSendIsRefused());

                try
                {
                    Method offered = TrainControlUI.class.getDeclaredMethod("isReturnHomeOffered");

                    offered.setAccessible(true);

                    outcome[6] = String.valueOf(offered.invoke(ui[0]));
                }
                catch (ReflectiveOperationException failed)
                {
                    outcome[6] = String.valueOf(failed);
                }

                // BROKEN: a station with no name is an error.
                session.getStore().setPointName(station, "");
                session.rebuild();

                try
                {
                    outcome[1] = String.valueOf(ui[0].autonomyHasErrors());
                    outcome[2] = String.valueOf(ui[0].whyAHandSendIsRefused());

                    // AND THE RIGHT-CLICK RETURN HOME ITEM (TDU2-C3): greyed, with the setup's sentence.
                    try
                    {
                        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

                        // Handed the sentence, as the menu hands it where Start is greyed (ADU-C3).
                        Method add = Class.forName("org.traincontrol.gui.HomeLocomotiveMenu").getDeclaredMethod(
                            "addReturnHomeItem", javax.swing.JComponent.class, TrainControlUI.class, String.class);

                        add.setAccessible(true);
                        add.invoke(null, menu, ui[0], ui[0].whyAHandSendIsRefused());

                        javax.swing.JMenuItem item = (javax.swing.JMenuItem) menu.getComponent(0);

                        outcome[4] = String.valueOf(item.isEnabled());
                        outcome[5] = String.valueOf(item.getToolTipText());
                    }
                    catch (ReflectiveOperationException failed)
                    {
                        outcome[4] = String.valueOf(failed);
                    }
                }
                finally
                {
                    session.getStore().setPointName(station, name);
                    session.rebuild();
                }

                outcome[3] = String.valueOf(ui[0].whyAHandSendIsRefused());
            });

            assertNotNull(outcome[0], "precondition: the frozen railway opened no setup with a named station in the window");

            assertEquals(outcome[0], "null", "precondition: the frozen railway's setup is refused before anything is"
                + " broken, so nothing below is about breaking it");

            assertEquals(outcome[1], "true", "precondition: an unnamed station is not an error");

            assertNotEquals(outcome[2], "null", "with the setup broken, the window lets a hand send through - MT-263:"
                + " \"trains can still be moved manually ... which should throw an error instead\"");

            assertEquals(outcome[3], "null", "with the setup mended, a hand send is still refused");

            // Adam, 2026-09-24, TDU2-C3: "Yes, go with your recommendation" - the item greyed with the setup's sentence,
            // the buttons live and explaining, as Start's is.
            assertEquals(outcome[6], "true", "precondition: Return Home is not offered on the frozen railway before anything"
                + " is broken, so its item is greyed for that and nothing below is about the setup (ADU-C6)");

            assertEquals(outcome[4], "false", "with the setup broken, the right-click Return Home item is offered and"
                + " every click refused (TDU2-C3)");

            assertTrue(outcome[5].startsWith("<html"), "the greyed Return Home item's tooltip is not wrapped as Start's is"
                + " (ADU-C4): " + outcome[5]);

            assertEquals(outcome[5].replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&"), outcome[2], "the greyed Return Home item does not say what is wrong with the setup"
                + " (TDU2-C3)");
        }
        finally
        {
            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                javax.swing.SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The right-click menu asks the setup once where Start is offered: the Return Home item is handed the hand doors'
     * sentence, worked out only where Start is greyed (ADU-C3, ADD-C10).
     *
     * `AutonomySession.check()` is not cached, and a popup menu is built on the event thread; LD-C6 brought the walks a
     * right-click costs down to one each, and TDU2-C3's item asked the whole check twice more on every right-click.
     * Start offered means the setup has no errors, so the hand doors' sentence is then null without asking.
     *
     * MUTATION: have the item ask the setup itself again, and this fails.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheMenuAsksTheSetupOnceWhereStartIsOffered() throws Exception
    {
        String menu = read("src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java");
        String item = read("src/org/traincontrol/gui/HomeLocomotiveMenu.java");

        assertTrue(menu.contains("canStart ? null : ui.whyAHandSendIsRefused()"), "the right-click menu does not work the"
            + " hand doors' sentence out from Start's answer - it is asked again whatever Start said (ADU-C3)");

        assertTrue(menu.contains("HomeLocomotiveMenu.addReturnHomeItem(this, ui, broken)"), "the right-click menu does not"
            + " hand the Return Home item the sentence it worked out (ADU-C3)");

        assertFalse(item.contains("whyAHandSendIsRefused("), "the Return Home item asks the whole setup check itself, on"
            + " every right-click (ADU-C3)");
    }

    /**
     * Both hand doors say when a train would run into its own tail, after the berth's refusal and before the reversal
     * question - the third standing refusal with a sentence (TDU-C4).
     *
     * `isPathClear` still refuses at dispatch without it, but with "check the log" in place of the sentence that names
     * the longest train that goes - which is what Adam asked a door to say (MT-262).
     *
     * MUTATION: take the own-tail block out of either door, and this fails naming it.
     *
     * @throws Exception from the files
     */
    @Test
    public void testBothHandDoorsSayWhenATrainWouldMeetItsOwnTail() throws Exception
    {
        for (String[] door : new String[][] {
            {"src/org/traincontrol/gui/AutoLocomotiveStatus.java", "private void locAvailPathsMouseClicked(",
                "the Auto tab's list of paths"},
            {"src/org/traincontrol/gui/LayoutRightclickAutonomyMenu.java", "private JMenuItem destinationItem(",
                "the track diagram's right-click destinations"}})
        {
            String source = read(door[0]);

            int start = source.indexOf(door[1]);

            assertTrue(start >= 0, "cannot find " + door[1] + " in " + door[0]);

            int berth = source.indexOf("whyABerthCannotHoldIt(", start);
            int ownTail = source.indexOf("whyItWouldMeetItsOwnTail(", start);
            int question = source.indexOf("ManualReversalPrompt.forJourney(", start);

            assertTrue(berth > start && question > berth, "precondition: " + door[2] + " no longer asks the berth rule and"
                + " then the reversal question");

            assertTrue(ownTail > berth && ownTail < question, door[2] + " does not say when a train would run into its own"
                + " tail (OB-294) - the send is refused at dispatch with \"check the log\"");
        }
    }

    private static void door(String file, String handler, String what) throws Exception
    {
        String source = read(file);

        int start = source.indexOf(handler);

        assertTrue(start >= 0, "cannot find " + handler + " in " + file + " - if the door moved, move this");

        int end = source.indexOf("\n    }", start);

        String body = source.substring(start, end < 0 ? source.length() : end);

        int asked = body.indexOf(REFUSAL);

        assertTrue(asked >= 0, what + " sends a train without asking " + REFUSAL + " - MT-263, \"trains can still be"
            + " moved manually ... which should throw an error instead\"");

        int question = body.indexOf("ManualReversalPrompt.forJourney(");
        int dispatch = body.indexOf("executePath(");

        assertTrue(question < 0 || asked < question, what + " asks which way the train should face before it"
            + " refuses the send - a question about a journey that is going to be refused reads as answered");

        assertTrue(dispatch >= 0 && asked < dispatch, what + " dispatches before it asks " + REFUSAL);
    }

    private static String said(int errors, int blocking) throws Exception
    {
        Method rule = TrainControlUI.class.getDeclaredMethod("whyAHandSendIsRefused", int.class, int.class);

        rule.setAccessible(true);

        return (String) rule.invoke(null, errors, blocking);
    }

    private static String read(String path) throws Exception
    {
        File f = new File(path);

        assertTrue(f.isFile(), "cannot find " + f.getAbsolutePath());

        String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

        return raw.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }
}
