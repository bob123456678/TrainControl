package regression;

import java.io.File;
import java.util.*;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;

/**
 * OB-127: a failed switch to a Central Station layout asked about pages that had just been loaded.
 *
 * Adam: "several offers to init a new layout when I select 'switch to CS layout' and it fails.  then a
 * warning window showing the invalid 5 page list pops up and vanishes on its own."
 *
 * The switch stores an EMPTY STRING in the layout-path preference and clears the layout list. Two
 * methods then read that preference and disagreed about what the empty string meant: `isLocalLayout`
 * read it with an empty-string default and answered "not local", while `getLocalLayoutPath` read it
 * with a null default and handed the empty string straight back. An empty path is not nothing -
 * `new File("")` is the working directory - so the index question was asked about whatever index was
 * lying there, against a layout list that had just been emptied, and every page in it came back
 * absent.
 *
 * Two halves, tested separately because they fail separately: the mechanism, and the agreement.
 *
 * @author Adam
 */
public class testSwitchingToACentralStationLayout
{
    /**
     * An index plus an emptied layout list reports every page in the index as absent.
     *
     * This is the reproduction, and it needs no Central Station and no window: the pages Adam was
     * shown are exactly what this returns. It is not asserting a defect - this function is behaving
     * correctly - it is pinning WHY the wrong caller must never reach it.
     */
    @Test
    public void testAnEmptyLayoutListMakesEveryIndexedPageAbsent() throws Exception
    {
        File folder = java.nio.file.Files.createTempDirectory("ob127").toFile();

        folder.deleteOnExit();

        List<String> five = Arrays.asList("Main", "Yard", "Depot", "Upper", "Branch");

        LayoutDiagram.writeLayoutIndex(folder.getAbsolutePath(), five, null, 0);

        assertEquals(LayoutDiagram.readLayoutIndexIds(folder.getAbsolutePath()).size(), 5,
            "the fixture index was not written, so nothing below is asking anything");

        List<String> absent = LayoutDiagram.pagesTheIndexWouldDrop(
            folder.getAbsolutePath(), new ArrayList<String>(), null, null);

        assertEquals(absent.size(), 5,
            "an index of five pages asked against an empty layout list no longer reports five "
            + "absences - the reproduction for OB-127 has stopped reproducing, so read the ticket "
            + "before assuming this is an improvement: " + absent);

        // And the path the switch actually leaves behind names no index, so the question is empty.
        assertTrue(LayoutDiagram.pagesTheIndexWouldDrop(null, new ArrayList<String>(), null, null)
            .isEmpty(), "a null layout path finds an index somewhere, which is the failure mode "
            + "OB-127 is about - it should find nothing at all");
    }

    /**
     * The two readers of the layout-path preference agree about the empty string.
     *
     * `isLocalLayout` is the one that was right. Whatever `getLocalLayoutPath` hands back has to mean
     * the same thing, because eight callers use it and only three of them defended against the empty
     * string by hand.
     *
     * MUTATION: putting the null default back on `getLocalLayoutPath` fails this.
     */
    @Test
    public void testTheLayoutPathReadersAgreeAboutEmpty() throws Exception
    {
        String ui = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int accessor = ui.indexOf("private String getLocalLayoutPath()");

        assertTrue(accessor >= 0, "getLocalLayoutPath is gone, so this test reads nothing");

        String body = ui.substring(accessor, ui.indexOf("}", ui.indexOf("{", accessor)) + 1);

        assertTrue(body.contains("isEmpty()"),
            "getLocalLayoutPath hands the stored value back without asking whether it is empty. "
            + "Switching to a Central Station layout stores an empty string there, and an empty path "
            + "is the working directory - so the page-index question gets asked about whatever index "
            + "is lying in it (OB-127)");

        // The gate all three page writers come through refuses when there is no local layout.
        int settle = ui.indexOf("private java.util.Collection<String> settleAbsentPages(");

        assertTrue(settle >= 0, "settleAbsentPages is gone");

        String gate = ui.substring(settle, ui.indexOf("final java.util.List<String> absent", settle));

        assertTrue(gate.contains("!isLocalLayout()"),
            "settleAbsentPages no longer refuses when the layout is not a local one, so a failed "
            + "switch to a Central Station layout can reach the index question again and ask the "
            + "operator whether pages they were looking at a moment ago have been deleted - and "
            + "answering wrongly retires the ids their autonomy settings hang off");
    }

    /**
     * A greyed diagram tab is not opened by the program either (OB-128).
     *
     * Adam: "go to manage pages -&gt; add blank page.  then just close the popup.  layout tab is opened,
     * with old track diagram still visible in the window."
     *
     * `setEnabledAt` stops the USER picking a tab and does nothing about the program picking it. Add
     * Blank Page selected the tab as its FIRST statement, before asking for a name, so closing the
     * prompt left it open on the railway a failed Central Station switch had just unloaded. Nine
     * methods picked that tab by index; they now come through this one.
     *
     * MUTATION: dropping the isEnabledAt guard fails this. Reverting any of the nine call sites to
     * setSelectedIndex fails the structural test below.
     */
    @Test
    public void testAGreyedDiagramTabIsNotOpenedByTheProgram() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        try
        {
            // Inside the try, so nothing can leave the preference behind (TSX-B8).
            sandbox = support.LayoutSandbox.open();

            final org.traincontrol.gui.TrainControlUI[] built =
                new org.traincontrol.gui.TrainControlUI[1];

            javax.swing.SwingUtilities.invokeAndWait(
                () -> built[0] = new org.traincontrol.gui.TrainControlUI());

            org.traincontrol.gui.TrainControlUI ui = built[0];

            javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) field(ui, "KeyboardTab");
            java.awt.Component panel = (java.awt.Component) field(ui, "layoutPanel");

            int index = tabs.indexOfComponent(panel);

            assertTrue(index >= 0,
                "the track diagram panel is not in the tab pane, so this test is driving nothing");

            assertTrue(tabs.getTabCount() > 1 && index != 0,
                "there is no other tab to be left on, so the assertions below cannot tell a refusal "
                + "from a selection");

            // GREYED, as a failed switch to a Central Station layout leaves it.
            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                tabs.setSelectedIndex(0);
                tabs.setEnabledAt(index, false);
            });

            javax.swing.SwingUtilities.invokeAndWait(ui::showLayoutTab);

            assertEquals(tabs.getSelectedIndex(), 0,
                "the program opened a greyed diagram tab, so Add Blank Page still lands the user on "
                + "the railway that was just unloaded - which is exactly OB-128");

            // THE CONTROL. Without it a showLayoutTab that never does anything would pass.
            javax.swing.SwingUtilities.invokeAndWait(() -> tabs.setEnabledAt(index, true));

            javax.swing.SwingUtilities.invokeAndWait(ui::showLayoutTab);

            assertEquals(tabs.getSelectedIndex(), index,
                "showLayoutTab no longer shows the tab when it IS available, so every door that "
                + "used to reach the diagram now goes nowhere");
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The Layout menu asks when it opens, and an unloaded layout empties the panel (OB-128).
     *
     * Adam: "grey out inaccessible options: manage, edit, popup all layouts.  You can make this check
     * when the layout menu opens, and before any menu option is clicked.  Also, properly deactivate
     * the layout panel."
     */
    @Test
    public void testTheLayoutMenuAndPanelAreDeactivatedTogether() throws Exception
    {
        String ui = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int guard = ui.indexOf("private void guardLayoutMenu()");

        assertTrue(guard >= 0, "guardLayoutMenu is gone, so nothing checks the menu when it opens");

        String body = ui.substring(guard, ui.indexOf("\n    }", guard));

        // MANAGE PAGES IS NOT NAMED HERE ANY MORE (UXR-B2).
        //
        // This used to require guardLayoutMenu to write `modifyLocalLayoutMenu.setEnabled` itself,
        // which made it a second writer of a property `applyLayoutEditingAvailability` already owns -
        // and the two asked different questions, so they disagreed during every diagram rebuild. What
        // Adam asked for was the answer being FRESH when the menu opens, not this method doing the
        // writing, and both hold once the menu asks the owner. That is asserted below instead.
        for (String item : new String[] { "editPageMenu", "popUpAllMenuItem" })
        {
            assertTrue(body.contains(item + ".setEnabled("),
                "guardLayoutMenu no longer decides whether " + item + " is available, so it stays "
                + "clickable with no layout to act on - which is how Add Blank Page was reached "
                + "after the diagram had been unloaded (OB-128)");
        }

        assertTrue(body.contains("applyLayoutEditingAvailability()"),
            "guardLayoutMenu no longer asks the owner of the editing controls when the menu opens, so "
            + "Manage Pages shows whatever it was left at - which is the staleness OB-128 was filed "
            + "about, arrived at from the other side");

        // Every door goes through the guarded helper rather than picking the tab by index.
        assertEquals(count(ui, "KeyboardTab.setSelectedIndex(1)"), 0,
            "something selects the track diagram tab by index again, so it reaches straight past "
            + "the greying - and assumes the diagram is tab 1 while it is at it");

        assertTrue(count(ui, "showLayoutTab()") >= 9,
            "fewer doors go through showLayoutTab than the nine that were converted, so one has "
            + "found another way to open the tab");

        // And the panel is emptied, not merely greyed.
        assertTrue(ui.contains("clearLayoutPanel();"),
            "nothing empties the track diagram panel when the layout list is emptied, so the tab "
            + "keeps the previous railway mounted and it looks entirely live");
    }

    /** How many test classes build a model without a sandbox today - see the ratchet above. */
    private static final int MODELS_WITHOUT_A_SANDBOX = 56;

    /**
     * WHICH classes, not just how many (VAL-C8).
     *
     * A bare count can absorb a repair and a new violation in the same round: fix one class, break a
     * different one, and 56 stays 56 while the test stays green. Pinning the names means a swap shows
     * up as a failure naming both the file that is missing and the file that is not.
     */
    private static final String[] MODELS_WITHOUT_A_SANDBOX_NAMES = {
        "testAMovedTileCarriesItsSetup.java",
        "testARouteDoesNotThrowSwitchesUnderATrain.java",
        "testARunSurvivesAPageRename.java",
        "testAccessory.java",
        "testAdvancedRoutes.java",
        "testAutoLayout.java",
        "testAutoLayoutRace.java",
        "testAutonomyDiagramSampleLayout.java",
        "testAutonomyGroundTruth.java",
        "testAutonomySimulationSanity.java",
        "testBothProtectingSignalsAreThrown.java",
        "testConfirmedGoodState.java",
        "testControlStationFaults.java",
        "testDiscardedEditsDoNotDeleteSetup.java",
        "testErrorsStopTheSetupRunning.java",
        "testFacingFollowsTheTrack.java",
        "testHomeStaging.java",
        "testImportRename.java",
        "testInvalidInput.java",
        "testLayoutBfs.java",
        "testLayoutBfsEquivalence.java",
        "testLayoutFolderRobustness.java",
        "testLayoutPickPath.java",
        "testLayoutReloadFence.java",
        "testLayoutRenameKeys.java",
        "testLayoutTimetable.java",
        "testLoadData.java",
        "testLocDB.java",
        "testLocomotive.java",
        "testLocomotiveIdentityPropagates.java",
        "testMaxActiveTrains.java",
        "testMockCentralStation.java",
        "testMultiUnitMembership.java",
        "testNetworkProxy.java",
        "testNonReversibleTrains.java",
        "testParseCS2Layout.java",
        "testParseCS2Routes.java",
        "testParseCS3Routes.java",
        "testRenameRoundTripThroughTheUIPath.java",
        "testReturnHomeOnRealLayout.java",
        "testRouteInventory.java",
        "testRoutePicking.java",
        "testRouteReachesTheRails.java",
        "testRouteRoundTrip.java",
        "testRoutes.java",
        "testStationBlockedByAnotherPoint.java",
        "testStuckTrainAdvisory.java",
        "testTheCheckerAgreesWithTheBuild.java",
        "testTheGoldenLayoutHoldsTogether.java",
        "testTimetableCapture.java",
        "testTimetableCaptureThroughARealRun.java",
        "testTimetableOnDerivedGraph.java",
        "testTracedPathIsContinuous.java",
        "testTriggerWaitsSayNothing.java",
        "testUiStateIsNotLostWhenUnreadable.java",
        "testWhyStuck.java",
    };

    private static String stripComments(String source)
    {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }

    private static int count(String haystack, String needle)
    {
        int n = 0;

        for (int at = haystack.indexOf(needle); at >= 0;
            at = haystack.indexOf(needle, at + needle.length()))
        {
            n++;
        }

        return n;
    }

    private static Object field(Object target, String name) throws Exception
    {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);

        f.setAccessible(true);

        return f.get(target);
    }

    /**
     * "Is a layout loaded" is stored once and read everywhere (OB-127, OB-128).
     *
     * Adam: "we just need a layout loaded flag in the TrainControlUI class, rather than something that
     * infers" - after watching the window show a track diagram while telling him no layout was loaded
     * and offering, every few seconds, to make him one.
     *
     * `getLayoutList().isEmpty()` was asked at sixteen places, each answering for whatever instant its
     * own code happened to run in. The diagram was painted when the list was full; the offer was made
     * when it was empty; neither was wrong about its own moment.
     *
     * The invariant is ONE WRITER. A cached answer with several writers is worse than the inference it
     * replaced, because the inference was at least current for the instant it ran.
     *
     * MUTATION: a second assignment to layoutLoaded fails this, and so does a reader going back to
     * asking the model.
     */
    @Test
    public void testWhetherALayoutIsLoadedHasOneWriter() throws Exception
    {
        String ui = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(count(ui, "this.layoutLoaded ="), 1,
            "the layout-loaded flag is assigned in more than one place, so two answers can be stored "
            + "and the window is back to disagreeing with itself - only updateLayoutLoaded may write "
            + "it");

        assertEquals(count(ui, "layoutLoaded ="), 2,
            "expected the declaration and the single assignment and found something else - a writer "
            + "has been added or the field has been renamed");

        assertTrue(count(ui, "updateLayoutLoaded();") >= 4,
            "fewer places refresh the flag than the moments the layout set can change - start-up, "
            + "the diagram rebuild, and the clear a Central Station switch performs. A flag that is "
            + "not refreshed where the truth moves is staler than the inference it replaced");

        // And the properties that used to have two writers have one.
        assertEquals(count(ui, "popUpAllMenuItem.setEnabled("), 1,
            "All Layouts is set from more than one place again. repaintPathLabel used to set it TRUE "
            + "unconditionally for a local layout and re-enable it moments after the menu guard had "
            + "greyed it - the same two-writers fault a reviewer already fixed for Manage Pages");
    }

    /**
     * The stored answer agrees with the model it was taken from.
     *
     * The structural test above cannot catch a flag that is written once and written WRONG.
     */
    @Test
    public void testTheStoredAnswerMatchesTheModel() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        try
        {
            // Inside the try, so nothing can leave the preference behind (TSX-B8).
            sandbox = support.LayoutSandbox.open();

            org.traincontrol.marklin.MarklinControlStation model =
                org.traincontrol.marklin.MarklinControlStation.init(null, true, false, false, true);

            model.stop();

            final org.traincontrol.gui.TrainControlUI[] built =
                new org.traincontrol.gui.TrainControlUI[1];

            javax.swing.SwingUtilities.invokeAndWait(
                () -> built[0] = new org.traincontrol.gui.TrainControlUI());

            org.traincontrol.gui.TrainControlUI ui = built[0];

            ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

            // The fixture has pages, so this is the loaded case - and the control for it is that the
            // model really does have some.
            assertFalse(model.getLayoutList().isEmpty(),
                "the sandbox fixture has no pages, so this test cannot tell a correct flag from one "
                + "that is stuck at false");

            assertTrue(ui.isLayoutLoaded(),
                "the window says no layout is loaded while the model has "
                + model.getLayoutList().size() + " pages - which is exactly the state Adam saw: the "
                + "diagram on screen and the application offering to create a track diagram");

            javax.swing.SwingUtilities.invokeAndWait(ui::dispose);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The scanner that decides what is code does not lose its place (2026-08-31).
     *
     * Both cases here are real code from this suite, and each hid a window from the check that keeps
     * the battery off Adam's own railway:
     *
     *   - a CHARACTER LITERAL holding a quote, at testARenameReachesTheTimetableOnScreen.java:96,
     *     which a scanner that does not know what a character literal is reads as the start of a
     *     string - blanking the rest of the file, and with it that class's window;
     *   - a STRING holding a line-comment marker, which the regex that used to strip comments first
     *     cut in half, leaving every quote after it meaning the opposite of what it says.
     *
     * Twenty-three files in this suite have odd quote parity under that old two-step parse.
     *
     * MUTATION: reading the two in the other order, or dropping the character-literal mode, fails
     * one of the first two assertions; blanking nothing fails the third.
     */
    @Test
    public void testTheCodeScannerKeepsItsPlace() throws Exception
    {
        String afterAChar = "char c = '\"'; Object w = new org.traincontrol.gui.TrainControlUI();";

        assertTrue(WINDOW_BUILT.matcher(withoutStringsAndComments(afterAChar)).find(),
            "a window built after a character literal holding a quote is invisible to the check, "
            + "which is exactly how a class went unguarded before");

        String afterASlashString = "String s = \"file:///tmp\"; Object w = new TrainControlUI();";

        assertTrue(WINDOW_BUILT.matcher(withoutStringsAndComments(afterASlashString)).find(),
            "a window built after a string containing a line-comment marker is invisible, which is "
            + "what stripping comments before strings did to twenty-three files here");

        // AND THE OTHER DIRECTION, which matters as much: the check must not INVENT windows out of
        // text that only mentions one. This file's own needles are string literals in this file.
        String mentionedOnly = "String needle = \"new TrainControlUI()\";";

        assertFalse(WINDOW_BUILT.matcher(withoutStringsAndComments(mentionedOnly)).find(),
            "a window named in a string was counted as one built, so the check reports offenders "
            + "that are not offenders - and this file would report itself");

        String inAComment = "// new org.traincontrol.gui.TrainControlUI() explained here";

        assertFalse(WINDOW_BUILT.matcher(withoutStringsAndComments(inAComment)).find(),
            "a window named in a comment was counted as one built");

        // An escaped quote must not end its string early.
        String escaped = "String q = \"\\\"\"; Object w = new TrainControlUI();";

        assertTrue(WINDOW_BUILT.matcher(withoutStringsAndComments(escaped)).find(),
            "an escaped quote ended its string early, so everything after it is being read as the "
            + "wrong kind of text");

        // A quote inside a block comment must not open a string.
        String blockComment = "/* he said \"hi\" */ Object w = new TrainControlUI();";

        assertTrue(WINDOW_BUILT.matcher(withoutStringsAndComments(blockComment)).find(),
            "a quote inside a block comment left the scanner inside a string");
    }

    /**
     * A class is exempt from the count rule only when its own sandbox is in a setup method.
     *
     * The exemption switches off the rule that a class needs as many sandbox opens as it builds
     * windows. It first asked whether the file held any @Before annotation at all - true of seven of
     * the sixteen windowed classes - and then looked back a fixed distance from the enclosing
     * method's header, which reaches over the PREVIOUS method's body and finds ITS annotation. Both
     * let a plain test with three windows and one sandbox through.
     *
     * The fixture is that second case, in this codebase's own formatting.
     *
     * MUTATION: putting back a fixed-distance lookback fails the first assertion; bounding it so
     * tightly that a method's own annotations fall outside fails the second.
     */
    @Test
    public void testOnlyASandboxInSetupExemptsAClass() throws Exception
    {
        String twoMethods =
              "    @BeforeClass" + System.lineSeparator()
            + "    public static void setUpClass() throws Exception" + System.lineSeparator()
            + "    {" + System.lineSeparator()
            + "        nothing();" + System.lineSeparator()
            + "    }" + System.lineSeparator()
            + System.lineSeparator()
            + "    @Test" + System.lineSeparator()
            + "    public void aTest() throws Exception" + System.lineSeparator()
            + "    {" + System.lineSeparator()
            + "        LayoutSandbox sandbox = LayoutSandbox.open();" + System.lineSeparator()
            + "    }" + System.lineSeparator();

        int open = twoMethods.indexOf("LayoutSandbox.open()");

        assertTrue(open > 0, "the fixture did not take");

        assertFalse(inASetupMethod(twoMethods, open),
            "a sandbox opened in a plain test was read as being in a setup method, because the "
            + "lookback reached over the method above it and found that method's @BeforeClass - so "
            + "the class is exempted from the count rule and may build any number of unguarded "
            + "windows");

        // And the case the exemption is FOR, which must still work.
        String inSetup =
              "    @BeforeClass" + System.lineSeparator()
            + "    public static void setUpClass() throws Exception" + System.lineSeparator()
            + "    {" + System.lineSeparator()
            + "        sandbox = LayoutSandbox.open();" + System.lineSeparator()
            + "    }" + System.lineSeparator();

        int inside = inSetup.indexOf("LayoutSandbox.open()");

        assertTrue(inASetupMethod(inSetup, inside),
            "a sandbox opened in @BeforeClass was not recognised, so a class that sets one up once "
            + "for all its methods would be reported as an offender");
    }

    /**
     * Nothing in the suite opens the operator\u2019s own railway (OB-111).
     *
     * Building a `TrainControlUI`, or calling `MarklinControlStation.init`, reads the layout
     * preference - and on Adam\u2019s machine that names his live layout. The suite then reads it,
     * writes it back with different line endings, and on 2026-08-28 raised a MODAL offer to create a
     * track diagram in the middle of a battery, which waits for a click no test will make. He watched
     * it happen: "The layout popup is coming up as the battery runs."
     *
     * Asserted over every test file rather than fixed one class at a time, which is how it kept coming
     * back: today alone, two classes had the sandbox AFTER the model instead of before it, one had
     * none, and one more built a window with none.
     *
     * MUTATION: removing any sandbox, or moving one below the thing it protects, fails this.
     */
    @Test
    public void testNoTestOpensTheOperatorsRailway() throws Exception
    {
        java.util.List<String> offenders = new java.util.ArrayList<>();

        int checked = 0;

        java.io.File root = new java.io.File("test");

        assertTrue(root.isDirectory(), "the test sources are not where this expects them");

        for (java.io.File f : filesUnder(root))
        {
            String body = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            // The support class itself is what does the pointing.
            if (f.getName().equals("LayoutSandbox.java")) continue;

            String code = body.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");

            // THE WINDOW, which is what reads AND writes the layout and can raise the modal offer.
            //
            // Deliberately not every class that builds a MODEL. Those read the operator’s layout
            // too and it is worth settling, but it is a wider sweep than this rule - some of them
            // parse test/test_layout directly on purpose - and a check that fails for twenty classes
            // nobody has looked at is a check that gets disabled.
            // THE WINDOW is a hard rule: it has no offenders, and it is the case that writes the
            // folder and can raise a modal dialog into the middle of a battery.
            //
            // The MODEL is a ratchet instead, below. `MarklinControlStation.init` reads the same
            // preference and is the half that actually loads the pages, so those classes open the
            // operator\u2019s railway too - but 56 of them do, and a rule that fails for 56 pre-existing
            // violations is one somebody deletes rather than obeys.
            // QUALIFIED OR NOT, AND EVERY ONE OF THEM, NOT THE FIRST (2026-08-31).
            //
            // This was `code.indexOf("new TrainControlUI()")` and it asked two questions wrongly.
            //
            // It matched only the UNQUALIFIED constructor, and seven of the sixteen classes that
            // build a window write `new org.traincontrol.gui.TrainControlUI()` - among them one with
            // five sandbox/window pairs - so the half this file calls a hard rule never saw them.
            //
            // And it compared FIRST occurrences, so a class with several test methods was checked
            // once: add a method that builds a window, forget the sandbox, and the first pair still
            // reads correctly while the new one opens the operator's own layout.
            //
            // That is not a hypothetical route in. It is how his railway was damaged on 2026-08-30,
            // and this check is what was written afterwards to stop it happening again.
            //
            // String literals are blanked as well as comments, because this method's own needles are
            // string literals in this file and would otherwise count as windows built here.
            // THE RAW SOURCE, not the comment-stripped copy (2026-08-31, second round).
            //
            // Stripping comments with a regex first is what broke this: `"file:///" + x` loses its
            // closing quote to the // rule, and every quote after it in the file then means the
            // opposite of what it says. Twenty-three files in this suite have odd quote parity that
            // way. One scanner does both, in the order the compiler does.
            String scan = withoutStringsAndComments(body);

            java.util.List<Integer> windows = indicesOf(scan, WINDOW_BUILT);
            java.util.List<Integer> sandboxes = indicesOf(scan, SANDBOX_OPENED);

            if (windows.isEmpty()) continue;

            checked++;

            // Both scoping patterns are in use and both are correct: testBusyDialogInteraction and
            // testRenderingCost open one in @BeforeClass and build windows in several methods;
            // testLayoutEditorBulkEdits opens one per method. Counting tells those apart from the
            // case that is wrong - a window nobody guarded - without having to know which method
            // anything sits in, which lexical scoping gets wrong for a window built in a helper.
            // A SANDBOX INSIDE A SETUP METHOD, not merely a file that has one somewhere.
            //
            // This asked `SETUP_METHOD.matcher(scan).find()` over the whole file, which is true of
            // seven of the sixteen classes that build a window - so for those the count rule was off
            // altogether, and the case this check was repaired to catch was still uncaught.
            // testUiStateIsNotLostWhenUnreadable is the demonstration: it has a @BeforeClass for
            // something else and opens its sandbox outside it, with a comment saying so.
            boolean setUpForTheClass = false;

            for (int open : sandboxes)
            {
                if (inASetupMethod(scan, open))
                {
                    setUpForTheClass = true;
                    break;
                }
            }

            if (sandboxes.isEmpty())
            {
                offenders.add(f.getName() + " builds a window and never opens a sandbox");
            }
            else if (sandboxes.get(0) > windows.get(0))
            {
                offenders.add(f.getName() + " opens its sandbox AFTER the thing that reads the "
                    + "preference, so the redirection comes too late");
            }
            else if (!setUpForTheClass && sandboxes.size() < windows.size())
            {
                offenders.add(f.getName() + " builds " + windows.size() + " windows behind only "
                    + sandboxes.size() + " sandbox opens, and has no setup method opening one for "
                    + "the whole class - so at least one window reads the operator's own layout");
            }
        }

        // A scan that matched nothing would pass while proving nothing.
        // THE NUMBER, not a floor (2026-08-31, third round).
        //
        // A floor three below the real count can absorb three classes silently dropping out of view,
        // which is the staleness it exists to catch.  The model half of this same test pins NAMES for
        // that reason.  If this fails because a class was legitimately added or removed, change the
        // number and say so in the commit - that is the point of it being exact.
        // 20 as of 2026-09-02, and the two added that day are both windows on purpose:
        // testTheWindowTakesTheKeyboard calls display() because OB-170 is about what START-UP does to
        // the keyboard, and testTheAutonomyEditorKnowsWhichSquare builds a LayoutEditor because
        // MT-258's shortcut reads a field only the editor's own hover handler sets. Both open their
        // sandbox before the model, as the rule above requires. (18 was testThePaletteStillPlacesTiles,
        // added the same day for OB-169.)
        assertEquals(checked, 20,
            checked + " test classes were found to build a window, not the 20 there were when this "
            + "was pinned. Fewer means the pattern has gone stale and is checking less than it "
            + "thinks; more means a new class builds a window and this line wants updating");

        // AND THE MODEL HALF, ratcheted (2026-08-28).
        //
        // These read the same preference and load the same railway; they simply cannot raise a dialog,
        // and there are too many to fix in one round. Pinned so the number can only fall - a new one
        // fails this, and repairing one is a matter of lowering MODELS_WITHOUT_A_SANDBOX by one.
        int loose = 0;

        java.util.List<String> looseNames = new java.util.ArrayList<>();

        for (java.io.File f : filesUnder(root))
        {
            if (f.getName().equals("LayoutSandbox.java")) continue;

            String raw = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            // ONE SCAN FOR BOTH INDICES.
            //
            // These are compared with each other, so they have to come from the same string - a first
            // attempt took `builds` from the comment-stripped copy and the sandbox from the scanned
            // one, which are different lengths, and the comparison became meaningless: 56 loose files
            // became 77 with nothing having changed in the suite.
            String code = withoutStringsAndComments(raw);

            int builds = earliest(code, "MarklinControlStation.init(", "= init(null");

            if (builds < 0) continue;

            // The same needle the window half uses, so an overload with arguments counts (2026-08-31).
            //
            // This was the literal "LayoutSandbox.open()", empty parentheses and all, so the first
            // class to call LayoutSandbox.open(folder) read as a class with no sandbox and pushed this
            // ratchet one over its pin. The two halves must agree about what a sandbox looks like.
            java.util.List<Integer> opens = indicesOf(code, SANDBOX_OPENED);

            int sandbox = opens.isEmpty() ? -1 : opens.get(0);

            if (sandbox < 0 || sandbox > builds)
            {
                loose++;
                looseNames.add(f.getName());
            }
        }

        assertTrue(loose <= MODELS_WITHOUT_A_SANDBOX,
            "there are now " + loose + " test classes that build a model without pointing the layout "
            + "preference at a sandbox first, up from " + MODELS_WITHOUT_A_SANDBOX
            + ". Every one of them loads whatever layout the machine has, which on Adam\u2019s is his "
            + "real railway");

        assertEquals(loose, MODELS_WITHOUT_A_SANDBOX,
            loose + " such classes remain, fewer than the " + MODELS_WITHOUT_A_SANDBOX
            + " recorded. Lower MODELS_WITHOUT_A_SANDBOX to " + loose + " so the improvement is kept.");

        // The count alone can absorb a repair and a new violation in the same round (VAL-C8): fix one
        // class, break a different one, and the number never moves. Pin WHICH classes too, so a swap
        // fails and names both files.
        java.util.Collections.sort(looseNames);

        java.util.List<String> pinned = new java.util.ArrayList<>(
            java.util.Arrays.asList(MODELS_WITHOUT_A_SANDBOX_NAMES));

        java.util.Collections.sort(pinned);

        assertEquals(looseNames, pinned,
            "the set of test classes that build a model without a sandbox has changed even though the "
            + "count may not have: now missing from the pinned list: "
            + diff(pinned, looseNames) + "; newly appearing: " + diff(looseNames, pinned)
            + ". If a class was genuinely fixed, remove it from MODELS_WITHOUT_A_SANDBOX_NAMES and "
            + "lower MODELS_WITHOUT_A_SANDBOX; if a new one appeared, give it a sandbox instead of "
            + "adding it to the list.");

        assertEquals(offenders.toString(), "[]",
            "these tests open whatever layout the machine has, which on Adam\u2019s is his real "
            + "railway - they read it, write it back, and can raise a modal dialog that stalls the "
            + "battery: " + offenders);
    }

    /**
     * Where a window is built, qualified or not - the hole that hid seven classes from this check.
     */
    private static final java.util.regex.Pattern WINDOW_BUILT =
        java.util.regex.Pattern.compile("new\\s+(?:org\\.traincontrol\\.gui\\.)?TrainControlUI\\s*\\(");

    /**
     * Where a sandbox is opened - with or WITHOUT arguments.
     *
     * This required empty parentheses until 2026-08-31, when LayoutSandbox gained an overload that
     * takes the folder to copy, so that a test can reason about the operator's own stations from a
     * copy of them. The first class to use it read as a class with no sandbox at all, which is the
     * failure mode this whole check exists to prevent - reported by the model ratchet, one file over
     * its pinned count.
     */
    private static final java.util.regex.Pattern SANDBOX_OPENED =
        java.util.regex.Pattern.compile("LayoutSandbox\\.open\\s*\\(");

    private static final java.util.regex.Pattern SETUP_METHOD =
        java.util.regex.Pattern.compile("@Before(?:Class|Method|Test)");

    /**
     * Every index at which a pattern matches, in order.
     */
    private static java.util.List<Integer> indicesOf(String body, java.util.regex.Pattern p)
    {
        java.util.List<Integer> out = new java.util.ArrayList<>();

        java.util.regex.Matcher m = p.matcher(body);

        while (m.find()) out.add(m.start());

        return out;
    }

    /**
     * String literals, character literals and comments blanked, in one pass over the raw source.
     *
     * Every other character keeps its place, so indices taken from the result can be compared with
     * one another - which is all this check does with them.
     *
     * Written as one scanner because doing it in two steps is what went wrong. A regex that strips
     * comments first eats the closing quote of any string containing a line-comment marker, and a
     * scanner that then looks for quotes has its state inverted from there on. And a character
     * literal holding a quote - which is real code in this suite, in a replace() call - does the same
     * thing to a scanner that does not know what a character literal is. A class that builds a window
     * went unchecked for exactly that reason.
     *
     * @param raw the file, as it is on disk
     * @return the same length of text, with everything that is not code blanked
     */
    private static String withoutStringsAndComments(String raw)
    {
        StringBuilder out = new StringBuilder(raw.length());

        int mode = 0;   // 0 code, 1 string, 2 char, 3 line comment, 4 block comment

        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);

            char next = i + 1 < raw.length() ? raw.charAt(i + 1) : 0;

            if (mode == 0)
            {
                if (c == SLASH && next == SLASH) { mode = 3; out.append("  "); i++; continue; }

                if (c == SLASH && next == STAR) { mode = 4; out.append("  "); i++; continue; }

                if (c == QUOTE) { mode = 1; out.append(c); continue; }

                if (c == TICK) { mode = 2; out.append(c); continue; }

                out.append(c);
                continue;
            }

            if (mode == 3)
            {
                if (c == NEWLINE) { mode = 0; out.append(c); }
                else out.append(' ');

                continue;
            }

            if (mode == 4)
            {
                if (c == STAR && next == SLASH) { mode = 0; out.append("  "); i++; }
                else out.append(c == NEWLINE ? c : ' ');

                continue;
            }

            // inside a string or a character literal: a backslash hides whatever follows it
            if (c == BACKSLASH)
            {
                out.append(' ');

                if (i + 1 < raw.length()) { out.append(' '); i++; }

                continue;
            }

            if (mode == 1 && c == QUOTE) { mode = 0; out.append(c); continue; }

            if (mode == 2 && c == TICK) { mode = 0; out.append(c); continue; }

            out.append(c == NEWLINE ? c : ' ');
        }

        return out.toString();
    }

    /**
     * Whether the code at this index sits inside a method carrying a @Before annotation.
     *
     * The enclosing method is found by this codebase's own formatting - a method header is a line
     * beginning with four spaces and a modifier - and the annotations sit immediately above it.
     */
    private static boolean inASetupMethod(String scan, int at)
    {
        java.util.regex.Matcher m = METHOD_HEADER.matcher(scan);

        int header = -1;

        while (m.find() && m.start() < at) header = m.start();

        if (header < 0) return false;

        // BOUNDED AT THE PREVIOUS METHOD'S CLOSING BRACE, not at a fixed distance.
        //
        // A fixed lookback runs backwards over the previous method's BODY and finds that method's
        // annotation: a short @BeforeClass above a window-building @Test made the whole class look
        // set up, and three windows behind one sandbox were waved through.  That is the same hole
        // this method replaced, one step narrower.
        //
        // A method's annotations sit between the end of the method before it and its own header, so
        // that is the region to read - and nothing outside it can belong to this method.
        int from = scan.lastIndexOf(CLOSING_BRACE, header);

        if (from < 0) from = 0;

        return SETUP_METHOD.matcher(scan.substring(from, header)).find();
    }

    private static final java.util.regex.Pattern METHOD_HEADER =
        java.util.regex.Pattern.compile("\\n    (?:public|private|protected|static|synchronized)");

    private static final String CLOSING_BRACE = "\n    }";

    private static final char SLASH = '/';
    private static final char STAR = '*';
    private static final char QUOTE = '\"';
    private static final char TICK = '\'';
    private static final char BACKSLASH = '\\';
    private static final char NEWLINE = '\n';


    /** Entries in {@code from} that are not in {@code excluding}, for a readable failure message. */
    private static java.util.List<String> diff(java.util.List<String> from, java.util.List<String> excluding)
    {
        java.util.List<String> out = new java.util.ArrayList<>(from);

        out.removeAll(excluding);

        return out;
    }

    private static int earliest(String body, String... needles)
    {
        int best = -1;

        for (String needle : needles)
        {
            int at = body.indexOf(needle);

            if (at >= 0 && (best < 0 || at < best)) best = at;
        }

        return best;
    }

    private static java.util.List<java.io.File> filesUnder(java.io.File dir)
    {
        java.util.List<java.io.File> out = new java.util.ArrayList<>();

        java.io.File[] children = dir.listFiles();

        if (children == null) return out;

        for (java.io.File child : children)
        {
            if (child.isDirectory()) out.addAll(filesUnder(child));
            else if (child.getName().endsWith(".java")) out.add(child);
        }

        return out;
    }

    /**
     * A first launch makes a track diagram; every later start still asks.
     *
     * Adam: "if the app is started FOR THE FIRST TIME (there exist no DB files yet) and there is no
     * layout, create one instead of prompting the user."
     *
     * The half worth writing down is PRESENT versus READABLE. A database file that exists and will not
     * load is not a first launch - it is a machine whose state we have just failed to read, and
     * treating it as empty is how a setup gets replaced by a blank one. `restoreState` draws the same
     * line in its own words, and `testUiStateIsNotLostWhenUnreadable` is what happens when it is drawn
     * wrongly.
     *
     * MUTATION: passing a constant at the call site fails the last assertion - which is the mutation
     * that passed when only the rule was tested.
     */
    @Test
    public void testAFirstLaunchIsNotAsked() throws Exception
    {
        java.io.File dir = java.nio.file.Files.createTempDirectory("firstrun").toFile();

        dir.deleteOnExit();

        java.io.File uiState = new java.io.File(dir, "UIState.data");
        java.io.File locos = new java.io.File(dir, "LocDB.data");

        assertTrue(org.traincontrol.gui.TrainControlUI.isFirstLaunch(uiState, locos),
            "neither database file exists and this is not being treated as a first launch, so a new "
            + "user is asked whether to create the track diagram they have not seen yet");

        // ANY of them present means somebody has been here before.
        write(locos, "not really a database");

        assertFalse(org.traincontrol.gui.TrainControlUI.isFirstLaunch(uiState, locos),
            "a locomotive database is present and this still reports a first launch - a track "
            + "diagram would be created without asking on a machine that already has a setup, and "
            + "an UNREADABLE database is exactly the case where that is most destructive");

        write(uiState, "not really a state file");

        assertFalse(org.traincontrol.gui.TrainControlUI.isFirstLaunch(uiState, locos),
            "both database files are present and this reports a first launch");

        assertTrue(locos.delete(), "could not remove the fixture database");

        assertFalse(org.traincontrol.gui.TrainControlUI.isFirstLaunch(uiState, locos),
            "saved window state is present and this reports a first launch, so the application has "
            + "run here before and is being treated as though it had not");

        // AND THE CALL SITE, which is where the rule stops being an opinion.
        String ui = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/gui/TrainControlUI.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(ui.contains("DEMO_LAYOUT_OUTPUT_PATH, !isFirstLaunch())"),
            "the start-up offer no longer asks whether this is a first launch, so either every new "
            + "user is asked a question they cannot answer, or every existing one has a track "
            + "diagram created without being asked");
    }

    private static void write(java.io.File f, String text) throws Exception
    {
        java.nio.file.Files.write(f.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Start-up shows the notice on its own window, and never hides that window on the way in.
     *
     * Adam: "while connecting to the CS and before the main UI loads, show a loading popup/overlay
     * like the cs2 sync one" (FR-041), and then **"can we avoid having the window close and then
     * reopen?"** (2026-09-03) - which is the second half of this test and was a real defect: the call
     * that takes the notice down sat in the `finally` of the window build, so it ran on the SUCCESS
     * path too, hid the window a moment after showing it, and `display()` showed it again.
     *
     * Read rather than run, for the reason FR-041's test gave: a test that put a start-up notice on
     * the screen would do it on every battery, which is the complaint he made about a modal dialog
     * appearing while one ran.  Every test builds its model with showUI false, and the gate below is
     * what keeps it that way.
     *
     * MUTATION this catches: putting `connectingFailed()` back in the finally after
     * `latch.countDown()`, which is exactly the defect he reported.
     */
    @Test
    public void testTheStartupNoticeGoesOnTheWindowAndStaysThere() throws Exception
    {
        String init = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/marklin/MarklinControlStation.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        // NO SECOND WINDOW, WHICH IS OB-170 (2026-09-03).
        //
        // The notice used to be a splash - a top-level window of its own - and showing one during
        // start-up spends the single chance a process gets to put a window in the foreground, so the
        // main window arrived into the foreground of whatever launched us and the keyboard was dead.
        // Seven passes went at that from the window's end; Adam's own experiment settled it in one
        // run, by suppressing the splash.
        assertFalse(init.contains("StartupSplash.show("),
            "start-up shows a second window again, which is OB-170: it spends the one chance this "
            + "process gets at the foreground, and the main window then arrives into somebody else's");

        int shown = init.indexOf("showConnecting(");

        assertTrue(shown >= 0,
            "nothing shows a start-up notice, so connecting to a station that does not answer looks "
            + "like an application that failed to start (FR-041)");

        // GATED. Without this every test that builds a model would open a window on his screen.
        String around = init.substring(Math.max(0, shown - 400), shown);

        assertTrue(around.contains("showUI"),
            "the notice is no longer gated on showUI, so it appears whenever a model is built - "
            + "including in every test in this suite, on the operator's own screen");

        // BEFORE the connection it is reporting.
        int connects = init.indexOf("new NetworkProxy(");

        assertTrue(connects >= 0, "the connection has moved, so the ordering below means nothing");

        assertTrue(shown < connects,
            "the notice goes up after the connection is attempted, which is the whole of the wait it "
            + "exists to cover");

        // AND TAKEN DOWN WHEN THE START-UP FAILS.  It is on the window now, so taking it down means
        // taking the window with it - which is right for a failure and wrong for anything else.
        assertTrue(init.contains("!built.get() && theUI != null) theUI.connectingFailed()"),
            "a failed window build leaves a window standing that says it is connecting, with the "
            + "error dialog in front of it - which is the symptom FR-041 exists to remove, wearing "
            + "the fix's own clothes");

        // AND NOT ON THE WAY IN.  This is the defect Adam reported: **"can we avoid having the window
        // close and then reopen?"**
        //
        // The `finally` below runs on every path out of the window build, success included.  Anything
        // in it that hides the window hides one that is about to be shown again by `display()`.
        int countDown = init.indexOf("latch.countDown();");

        assertTrue(countDown > 0, "the latch has moved, so the region below means nothing");

        String restOfTheFinally = init.substring(countDown, init.indexOf("latch.await()", countDown));

        assertFalse(restOfTheFinally.contains("connectingFailed()"),
            "the notice is taken down in the finally of the window build, which runs on the SUCCESS "
            + "path too - so the window is hidden a moment after it is shown and shown again by "
            + "display(), which is what the operator sees as the window closing and reopening");

        // AND NOTHING AFTER display() TAKES IT AWAY EITHER.
        int displays = init.indexOf("theUI.display();");

        assertTrue(displays > 0, "nothing calls display(), so the ordering below means nothing");

        String afterDisplay = init.substring(displays,
            Math.min(init.length(), init.indexOf("});", displays) + 3));

        assertFalse(afterDisplay.contains("connectingFailed"),
            "the window is hidden immediately after being shown.  What follows display(): "
            + afterDisplay);

        // And the words exist to put in it.
        String bundle = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/resources/messages.properties")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(bundle.contains("\nui.splashConnecting="),
            "the notice asks for a message key that is not in the bundle, so it would show the key");
    }

    /**
     * A sandbox is opened inside a `try` (TSX-B8).
     *
     * **That is what this checks, and it is weaker than "closed on every path", which is what it used
     * to be called** (`VD9-C15`). An open inside a `try` whose handler closes nothing would pass. What
     * the shape buys is that there IS a handler to put the close in, and that the close cannot be
     * skipped by an early return between the two - which is how all eight offenders failed.
     *
     * `close()` is what returns the layout preference to whatever the operator had, and the
     * preference is machine-global: a run that does not close leaves TrainControl opening a folder
     * under %TEMP% the next time Adam starts it.  The rule above - `testNoTestOpensTheOperatorsRailway`
     * - says a sandbox must be opened BEFORE the window.  This is the other half: that having opened
     * one, nothing can get out of the method without closing it.
     *
     * **Four ways out that are easy to miss**, and all four were in the suite when this was written:
     * a window constructor that throws, `MarklinControlStation.init` failing to bind its port, an
     * assertion in the set-up part of a test - and `SkipException`, which is not a failure at all but
     * an ordinary outcome, thrown on any machine where the icon folder cannot be created.
     *
     * The shape asked for is one sentence long, so it can be obeyed without reading this: **the open
     * goes inside a `try`.**  Setup methods are exempt because their `@AfterClass(alwaysRun = true)`
     * teardown runs even when the set-up threw - which is only true since TSX-C18, and is the reason
     * that exemption is safe to grant.
     *
     * MUTATION: moving any of these opens back above its `try` fails this.
     */
    @Test
    public void testEverySandboxIsOpenedInsideATry() throws Exception
    {
        java.util.List<String> offenders = new java.util.ArrayList<>();

        int checked = 0;

        java.io.File root = new java.io.File("test");

        assertTrue(root.isDirectory(), "the test sources are not where this expects them");

        for (java.io.File f : filesUnder(root))
        {
            // The support class is what does the opening and closing.
            if (f.getName().equals("LayoutSandbox.java")) continue;

            String raw = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            if (!raw.contains("LayoutSandbox.open(")) continue;

            String scan = withoutStringsAndComments(raw);

            for (int at = scan.indexOf("LayoutSandbox.open("); at >= 0;
                 at = scan.indexOf("LayoutSandbox.open(", at + 1))
            {
                checked++;

                // A setup method hands its sandbox to a teardown that always runs.
                if (inASetupMethod(scan, at)) continue;

                if (!insideATry(scan, at))
                {
                    offenders.add(f.getName() + ", in " + methodAround(scan, at));
                }
            }
        }

        assertTrue(checked >= 40,
            "only " + checked + " sandbox opens were found in the whole suite, which is fewer than "
            + "there were when this was written - so this check is reading the wrong thing and is "
            + "passing because it looked at almost nothing");

        assertEquals(offenders.toString(), "[]",
            offenders.size() + " sandbox(es) are opened outside a try, so anything that throws "
            + "between the open and the close - a window constructor, init binding its port, a "
            + "failed assertion, or a SkipException, which is not a failure at all - leaves the "
            + "machine-global layout preference pointing at a folder under %TEMP%.  That is the "
            + "railway TrainControl opens the next time it starts (TSX-B8, OB-111).  Put the open "
            + "inside the try");
    }

    /**
     * Whether an offset sits inside a `try` block.
     *
     * By walking the braces from the top of the file rather than by looking for the word nearby: a
     * `try` five lines above may belong to a block that has already closed, and a `try` far above may
     * still be open.  Only the stack knows.
     *
     * @param scan the source with its strings and comments already removed
     * @param at where to ask about
     * @return true if some enclosing block is a try
     */
    private static boolean insideATry(String scan, int at)
    {
        java.util.List<Boolean> stack = new java.util.ArrayList<>();

        for (int i = 0; i < at; i++)
        {
            char c = scan.charAt(i);

            if (c == '{')
            {
                stack.add(opensATry(scan, i));
            }
            else if (c == '}' && !stack.isEmpty())
            {
                stack.remove(stack.size() - 1);
            }
        }

        return stack.contains(Boolean.TRUE);
    }

    /**
     * Whether the brace at {@code brace} is the one that opens a try block.
     *
     * Both spellings: `try {`, and `try (...) {` for try-with-resources, which closes its own
     * subject and would otherwise read as an ordinary block and hide a real enclosing try.
     */
    private static boolean opensATry(String scan, int brace)
    {
        int j = brace - 1;

        while (j >= 0 && Character.isWhitespace(scan.charAt(j))) j--;

        if (j >= 0 && scan.charAt(j) == ')')
        {
            int depth = 0;

            while (j >= 0)
            {
                if (scan.charAt(j) == ')') depth++;

                else if (scan.charAt(j) == '(' && --depth == 0) break;

                j--;
            }

            j--;

            while (j >= 0 && Character.isWhitespace(scan.charAt(j))) j--;
        }

        // j is now on the last character of the keyword, if there is one
        return j >= 2 && scan.startsWith("try", j - 2)
            && (j - 3 < 0 || !Character.isJavaIdentifierPart(scan.charAt(j - 3)));
    }

    /** The method header above an offset, so a failure names something findable. */
    private static String methodAround(String scan, int at)
    {
        java.util.regex.Matcher m = METHOD_HEADER.matcher(scan);

        String header = "(no method header above it)";

        while (m.find() && m.start() < at)
        {
            int ends = scan.indexOf('\n', m.start() + 1);

            header = scan.substring(m.start() + 1, ends < 0 ? scan.length() : ends).trim();
        }

        return header;
    }
}
