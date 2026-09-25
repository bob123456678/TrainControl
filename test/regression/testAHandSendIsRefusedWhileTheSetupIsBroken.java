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
            final String[] outcome = new String[9];

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
                outcome[7] = String.valueOf(ui[0].whyStartAndAHandSendAreRefused()[1]);

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
                        // From the one reading the menu takes (ADU2-C5), checked against the two rules it stands for.
                        String[] both = ui[0].whyStartAndAHandSendAreRefused();

                        outcome[8] = String.valueOf(both[0].equals(ui[0].whyAutonomyWillNotStart())
                            && both[1] != null && both[1].equals(ui[0].whyAHandSendIsRefused()));

                        add.invoke(null, menu, ui[0], both[1]);

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

            assertEquals(outcome[7], "null", "the menu's one reading refuses a hand send over a setup nothing is wrong"
                + " with (ADU2-C5)");

            assertEquals(outcome[8], "true", "over a broken setup, the menu's one reading does not say what Start's rule"
                + " and the hand doors' rule say (ADU2-C5)");

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

        assertTrue(menu.contains("canStart ? null : ui.whyStartAndAHandSendAreRefused()"), "the right-click menu does"
            + " not work Start's and the hand doors' sentences out together from Start's answer (ADU-C3, ADU2-C5)");

        // Over the block that builds Start's item and Return Home's.  A destination item asks again when it is CLICKED,
        // which is its guard (MT-263) and stays.
        int from = menu.indexOf("boolean canStart = ui.canStartAutonomy();");
        int to = menu.indexOf("HomeLocomotiveMenu.addReturnHomeItem(this, ui, broken)");

        assertTrue(from >= 0 && to > from, "precondition: the block that builds Start's item and Return Home's is not"
            + " where it was");

        String building = menu.substring(from, to);

        assertFalse(building.contains("ui.whyAutonomyWillNotStart()") || building.contains("ui.whyAHandSendIsRefused()"),
            "the right-click menu asks the setup again for one of the two sentences, over a broken setup (ADU2-C5)");

        assertTrue(menu.contains("HomeLocomotiveMenu.addReturnHomeItem(this, ui, broken)"), "the right-click menu does not"
            + " hand the Return Home item the sentence it worked out (ADU-C3)");

        // AND EACH SENTENCE GOES WHERE IT BELONGS: Start's to Start's tooltip, the hand doors' to Return Home (ADU2-C5).
        assertTrue(building.contains("AutonomyEditorPanel.wrapped(why[0])") && menu.contains("String broken = why == null ? null : why[1];"),
            "the right-click menu hands one of the two sentences to the wrong item, or drops it (ADU2-C5)");

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

    /**
     * Over the break MT-263, MT-573 and MT-580 make - 4 - Combined switched back on, whose sensors repeat the other
     * pages' - every door that would move a train says why it will not, each in its own words, and no train moves (Adam,
     * 2026-09-25: automated tests supersede the MTs they answer).
     *
     * The claim above reads the Return Home item as `addReturnHomeItem` builds it, over a station left unnamed.  This one
     * takes the entries' own break and their own gestures: the track diagram's right-click menu on a station, built as
     * `showFor` builds it for a right-click, with Start and Return Home read off it; then the Start Autonomy,
     * Return Home and Execute Timetable buttons pressed, with what each says read off the dialog it raises.
     *
     * MUTATION: take the refusal out of Return Home or Execute Timetable, hand Return Home's item Start's sentence, offer
     * Start's item live, or have Start's press say something other than its tooltip, and this fails naming it.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testTheEntriesBreakIsRefusedAtEveryDoor() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new org.testng.SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        final Dismisser dismisser = new Dismisser();

        final Thread answering = new Thread(dismisser, "testTheEntriesBreakIsRefusedAtEveryDoor dialogs");

        answering.setDaemon(true);

        final java.util.Map<String, String> seen = new java.util.concurrent.ConcurrentHashMap<>();

        final org.traincontrol.automationui.TileGraph.TileKey[] station = new org.traincontrol.automationui.TileGraph.TileKey[1];

        final boolean[] broke = new boolean[1];

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            final org.traincontrol.marklin.MarklinControlStation model =
                org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

            javax.swing.SwingUtilities.invokeAndWait(() -> ui[0] = new TrainControlUI());

            ui[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);

            ui[0].whenTilesSettled(() -> settled.countDown());

            settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

            for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

            // RETURN HOME OFFERED BEFORE ANYTHING IS BROKEN (ADU-C6, and MT-573's comment of 2026-09-24): a train is away
            // from its home, so a greyed item or a refused press below is about the setup.
            Method triage = TrainControlUI.class.getDeclaredMethod("awaitReturnHomeTriage", long.class);

            triage.setAccessible(true);
            triage.invoke(ui[0], 30000L);

            answering.start();

            // THE ENTRIES' BREAK, on the event thread where the editor makes it: the Exclude Page box's own call.
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                org.traincontrol.automationui.AutonomySession session = ui[0].getAutonomySession();

                if (session == null) return;

                seen.put("excluded", String.valueOf(session.getStore().getExcludedPages().contains(COMBINED)));
                seen.put("offered", String.valueOf(returnHomeOffered(ui[0])));

                for (org.traincontrol.automationui.TileGraph.TileKey key : session.getStore().getNamedTiles())
                {
                    if (session.getStore().isStation(key)) station[0] = key;
                }

                session.setPageExcluded(COMBINED, false);
                session.rebuild();

                broke[0] = true;

                seen.put("errors", String.valueOf(ui[0].autonomyErrorCount()));
                seen.put("start", String.valueOf(ui[0].whyAutonomyWillNotStart()));
                seen.put("hand", String.valueOf(ui[0].whyAHandSendIsRefused()));
            });

            assertEquals(seen.get("excluded"), "true", "precondition: the frozen railway does not have " + COMBINED
                + " switched off, so switching it on is not the entries' break");

            assertEquals(seen.get("offered"), "true", "precondition: Return Home is not offered on the frozen railway"
                + " before anything is broken, so a greyed item or a refused press says nothing about the setup (ADU-C6)");

            assertNotNull(station[0], "precondition: the frozen railway has no station to right-click");

            final int errors = Integer.parseInt(seen.get("errors"));

            assertTrue(errors > 0, "precondition: switching " + COMBINED + " on is no error on the frozen railway (OB-150),"
                + " so nothing below is about the entries' break");

            final java.util.Map<String, String> before = whereTheTrainsAre(model);

            // THE RIGHT-CLICK MENU, built as `showFor` - the diagram's own door - builds it: the Point on the square
            // resolved on the event thread, the paths gathered off it, the menu made on it.  Not shown: the test's window
            // is not on screen, and `showFor` shows only over a showing window.
            final Class<?> menuClass = Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu");

            final Method gather = menuClass.getDeclaredMethod("gatherPathOptions", TrainControlUI.class,
                org.traincontrol.automation.Point.class);

            gather.setAccessible(true);

            final java.lang.reflect.Constructor<?> make = menuClass.getDeclaredConstructor(TrainControlUI.class,
                org.traincontrol.automationui.TileGraph.TileKey.class, org.traincontrol.automationui.TileGraph.TileKey.class,
                Class.forName("org.traincontrol.gui.LayoutRightclickAutonomyMenu$PathOptions"));

            make.setAccessible(true);

            final org.traincontrol.automation.Point[] standing = new org.traincontrol.automation.Point[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> standing[0] = ui[0].getAutonomyPointForTile(station[0]));

            final Object options = gather.invoke(null, ui[0], standing[0]);

            final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    menu[0] = (javax.swing.JPopupMenu) make.newInstance(ui[0], station[0], station[0], options);
                }
                catch (ReflectiveOperationException failed)
                {
                    seen.put("shown", String.valueOf(failed));
                }
            });

            assertNotNull(menu[0], "the track diagram's right-click menu on a station could not be built over the broken"
                + " setup: " + seen.get("shown"));

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                seen.put("startItem", itemSays(menu[0], I18n.t("autolayout.ui.menuStartAutonomy")));
                seen.put("homeItem", itemSays(menu[0], I18n.t("autolayout.ui.menuReturnToHome")));

            });

            // THE BUTTONS, pressed.  Each is left live over a broken setup and explains at the press (TDU2-C3).
            for (String button : new String[] {"startAutonomy", "returnHomeButton", "executeTimetable"})
            {
                press(ui[0], button, dismisser, seen);
            }

            // NOTHING DISPATCHED: a refused press that had sent a train anyway would show it here by now.
            Thread.sleep(2000);

            javax.swing.SwingUtilities.invokeAndWait(() -> { });

            org.traincontrol.automation.Layout layout = model.getAutoLayout();

            seen.put("running", String.valueOf(layout.isRunning()));
            seen.put("active", String.valueOf(layout.getActiveLocomotives().keySet()));

            final String start = seen.get("start");
            final String hand = seen.get("hand");

            // MT-263, as amended on 2026-09-25: Start names the error count, and never says to wait for the trains.
            assertEquals(start, I18n.f("autolayout.ui.errorCannotStartWithErrors", errors), "over the entries' break Start's"
                + " sentence does not name the " + errors + " error(s) (MT-263, ADD2-C1)");

            assertNotEquals(start, I18n.t("autolayout.errorUnableToStartAutonomyWaitForTrains"), "over the entries' break"
                + " Start says to wait for trains that are not running (MT-263)");

            assertEquals(seen.get("startItem"), "false|" + start, "over the entries' break the right-click Start item is"
                + " not greyed with Start's sentence (MT-263 step 2, MT-580 step 4)");

            assertEquals(seen.get("startAutonomy.pressed"), "true", "precondition: the Start Autonomy button is greyed over"
                + " a broken setup, where it is left live to explain (OB-050)");

            assertEquals(seen.get("startAutonomy.said"), start, "pressing Start over the entries' break does not say what"
                + " its greyed item says (MT-263 step 3)");

            assertEquals(seen.get("startAutonomy.after"), "true", "a refused Start press left the button dead");

            // MT-573 and MT-580: the setup's own sentence, which names how many things there are, and not Start's.
            assertEquals(hand, TrainControlUI.whyAHandSendIsRefused(errors, 0), "over the entries' break the doors that"
                + " move trains without Start are not refused in the setup's own words, with the count");

            assertNotEquals(hand, start, "Return Home's refusal is Start's sentence, which is about the wrong button"
                + " (ADU-C5)");

            assertEquals(seen.get("homeItem"), "false|" + hand, "over the entries' break the right-click Return Home item is"
                + " not greyed with the setup's sentence (MT-580 step 4)");

            assertEquals(seen.get("returnHomeButton.pressed"), "true", "precondition: the Return Home button is greyed with a"
                + " train away from home, so pressing it says nothing about the setup");

            assertEquals(seen.get("returnHomeButton.said"), hand, "pressing Return Home over the entries' break does not"
                + " say the setup cannot be used yet (MT-573 step 3)");

            assertEquals(seen.get("executeTimetable.pressed"), "true", "precondition: Execute Timetable is greyed on the"
                + " frozen railway, so pressing it says nothing about the setup");

            assertEquals(seen.get("executeTimetable.said"), hand, "pressing Execute Timetable over the entries' break does"
                + " not say what Return Home says (MT-573 step 4)");

            assertEquals(seen.get("executeTimetable.after"), "true", "a refused Execute Timetable press left the button"
                + " dead (MT-573 step 4)");

            assertEquals(seen.get("running"), "false", "a train is running after every door refused (MT-573 step 3)");

            assertEquals(seen.get("active"), "[]", "a train was dispatched after every door refused (MT-573 step 3)");

            assertEquals(whereTheTrainsAre(model), before, "a train moved after every door refused (MT-573 step 3)");

            assertTrue(dismisser.said.size() == 3, "the three presses raised other dialogs as well, or fewer: "
                + dismisser.said);
        }
        finally
        {
            dismisser.running = false;

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    org.traincontrol.automationui.AutonomySession session = closing.getAutonomySession();

                    if (broke[0] && session != null)
                    {
                        session.setPageExcluded(COMBINED, true);
                        session.rebuild();
                    }

                    closing.dispose();
                });
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The page MT-263, MT-573 and MT-580 switch back on to break the setup: it repeats the other pages' sensors. */
    private static final String COMBINED = "4 - Combined";

    /**
     * Presses one of the window's buttons as a click does, and records whether it could be pressed, the first thing the
     * dialog it raised said, and whether it is still live afterwards.
     */
    private static void press(TrainControlUI ui, String field, Dismisser dismisser, java.util.Map<String, String> seen)
        throws Exception
    {
        java.lang.reflect.Field found = TrainControlUI.class.getDeclaredField(field);

        found.setAccessible(true);

        final javax.swing.JButton button = (javax.swing.JButton) found.get(ui);

        final boolean[] live = new boolean[1];

        javax.swing.SwingUtilities.invokeAndWait(() -> live[0] = button.isEnabled());

        seen.put(field + ".pressed", String.valueOf(live[0]));

        if (!live[0]) return;

        int count = dismisser.said.size();

        javax.swing.SwingUtilities.invokeLater(button::doClick);

        for (long end = System.currentTimeMillis() + 30000L; dismisser.said.size() <= count
            && System.currentTimeMillis() < end; ) Thread.sleep(100);

        // The dialog has closed once the event thread is back.
        for (int turn = 0; turn < 4; turn++) javax.swing.SwingUtilities.invokeAndWait(() -> { });

        seen.put(field + ".said", dismisser.said.size() > count ? dismisser.said.get(count) : "nothing");

        javax.swing.SwingUtilities.invokeAndWait(() -> seen.put(field + ".after", String.valueOf(button.isEnabled())));
    }

    private static boolean returnHomeOffered(TrainControlUI ui)
    {
        try
        {
            Method offered = TrainControlUI.class.getDeclaredMethod("isReturnHomeOffered");

            offered.setAccessible(true);

            return (Boolean) offered.invoke(ui);
        }
        catch (ReflectiveOperationException failed)
        {
            return false;
        }
    }

    /** Every train autonomy runs, and the Point it stands at. */
    private static java.util.Map<String, String> whereTheTrainsAre(org.traincontrol.marklin.MarklinControlStation model)
    {
        java.util.Map<String, String> where = new java.util.TreeMap<>();

        org.traincontrol.automation.Layout layout = model.getAutoLayout();

        for (org.traincontrol.base.Locomotive loc : layout.getLocomotivesToRun())
        {
            org.traincontrol.automation.Point at = layout.getLocomotiveLocation(loc);

            where.put(loc.getName(), at == null ? "nowhere" : at.getName());
        }

        return where;
    }

    /** "enabled|tooltip as read", with the tooltip's markup taken off, or "none" where the menu has no such item. */
    private static String itemSays(javax.swing.JPopupMenu menu, String label)
    {
        for (java.awt.Component component : menu.getComponents())
        {
            if (!(component instanceof javax.swing.JMenuItem) || !label.equals(((javax.swing.JMenuItem) component).getText()))
            {
                continue;
            }

            javax.swing.JMenuItem item = (javax.swing.JMenuItem) component;

            String tip = item.getToolTipText();

            return item.isEnabled() + "|" + (tip == null ? "null" : tip.replaceAll("<[^>]*>", "").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&"));
        }

        return "none";
    }

    /** Reads every message dialog that opens, and answers it as OK does. */
    private static final class Dismisser implements Runnable
    {
        private final java.util.List<String> said = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        private final java.util.Set<Object> handled = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private volatile boolean running = true;

        @Override
        public void run()
        {
            while (running)
            {
                try
                {
                    Thread.sleep(150);
                }
                catch (InterruptedException stop)
                {
                    return;
                }

                for (java.awt.Window window : java.awt.Window.getWindows())
                {
                    if (!window.isShowing() || !(window instanceof javax.swing.JDialog)) continue;

                    final javax.swing.JOptionPane pane = paneIn(((javax.swing.JDialog) window).getContentPane());

                    if (pane == null || !handled.add(pane)) continue;

                    said.add(String.valueOf(pane.getMessage()));

                    javax.swing.SwingUtilities.invokeLater(() -> pane.setValue(Integer.valueOf(javax.swing.JOptionPane.OK_OPTION)));
                }
            }
        }

        private static javax.swing.JOptionPane paneIn(java.awt.Container container)
        {
            for (java.awt.Component component : container.getComponents())
            {
                if (component instanceof javax.swing.JOptionPane) return (javax.swing.JOptionPane) component;

                if (component instanceof java.awt.Container)
                {
                    javax.swing.JOptionPane found = paneIn((java.awt.Container) component);

                    if (found != null) return found;
                }
            }

            return null;
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
