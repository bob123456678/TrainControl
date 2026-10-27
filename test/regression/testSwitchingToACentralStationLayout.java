package regression;

import java.io.File;
import java.util.*;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.LayoutDiagram;
import support.SourceOf;

/**
 * OB-127: a failed switch to a Central Station layout asked about pages that had just been loaded.
 *
 * The switch stores an EMPTY STRING in the layout-path preference and clears the layout list.  `isLocalLayout` and
 * `getLocalLayoutPath` disagreed about what the empty string meant (`new File("")` is the working directory), so the
 * index question was asked about whatever index lay there and every page came back absent.  Two halves, tested
 * separately because they fail separately: the mechanism, and the agreement.
 *
 * @author Adam
 */
public class testSwitchingToACentralStationLayout
{
    /** An index plus an emptied layout list reports every page in the index as absent: the reproduction, no window. */
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
     * The two readers of the layout-path preference agree about the empty string: `isLocalLayout` was right, and
     * `getLocalLayoutPath` must mean the same, because eight callers use it.
     *
     * MUTATION: putting the null default back on `getLocalLayoutPath` fails this.
     */
    @Test
    public void testTheLayoutPathReadersAgreeAboutEmpty() throws Exception
    {
        String body = SourceOf.declaration("private String getLocalLayoutPath()");

        assertTrue(body.contains("isEmpty()"),
            "getLocalLayoutPath hands the stored value back without asking whether it is empty. "
            + "Switching to a Central Station layout stores an empty string there, and an empty path "
            + "is the working directory - so the page-index question gets asked about whatever index "
            + "is lying in it (OB-127)");

        // The gate all three page writers come through refuses when there is no local layout.
        String settle = SourceOf.declaration("private java.util.Collection<String> settleAbsentPages(");

        String gate = settle.substring(0, settle.indexOf("final java.util.List<String> absent"));

        assertTrue(gate.contains("!isLocalLayout()"),
            "settleAbsentPages no longer refuses when the layout is not a local one, so a failed "
            + "switch to a Central Station layout can reach the index question again and ask the "
            + "operator whether pages they were looking at a moment ago have been deleted - and "
            + "answering wrongly retires the ids their autonomy settings hang off");
    }

    /**
     * A grayed diagram tab is not opened by the program either (OB-128): `setEnabledAt` stops only the user, and Add
     * Blank Page used to select the tab before asking for a name.  Nine methods now come through one guarded door.
     *
     * MUTATION: dropping the isEnabledAt guard fails this.  Reverting any of the nine call sites to
     * setSelectedIndex fails the structural test below.
     */
    @Test
    public void testAGreyedDiagramTabIsNotOpenedByTheProgram() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        try
        {
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

            // GRAYED, as a failed switch to a Central Station layout leaves it.
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

    /** The Layout menu asks when it opens, and an unloaded layout empties the panel (OB-128). */
    @Test
    public void testTheLayoutMenuAndPanelAreDeactivatedTogether() throws Exception
    {
        String body = SourceOf.declaration("void guardLayoutMenu()");

        // The answer is fresh when the menu opens because the menu asks `applyLayoutEditingAvailability`, the one owner
        // of that property, rather than guardLayoutMenu writing it too; that is asserted below.
        for (String item : new String[] { "editPageMenu", "popUpAllMenuItem()" })
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

        // Every door goes through the guarded helper rather than picking the tab by index - the field by name or
        // through an accessor, so a door moved out of the window and reaching the tabs that way is seen.
        assertFalse(java.util.regex.Pattern.compile("KeyboardTab(?:\\(\\))?\\.setSelectedIndex\\(1\\)")
                .matcher(SourceOf.allCode()).find(),
            "something selects the track diagram tab by index again, so it reaches straight past "
            + "the greying - and assumes the diagram is tab 1 while it is at it");

        // Counted as CALLS over the program, so the declaration is not one of them: seven doors since OB-359, and the
        // eighth is the autonomy viewer's door (FR-136).
        assertTrue(SourceOf.occurrences("showLayoutTab();") >= 8,
            "fewer doors go through showLayoutTab than the seven left of the nine that were converted, "
            + "and the autonomy viewer's, so one has found another way to open the tab");

        // And the panel is emptied, not merely grayed.
        assertTrue(SourceOf.allCode().contains("clearLayoutPanel();"),
            "nothing empties the track diagram panel when the layout list is emptied, so the tab "
            + "keeps the previous railway mounted and it looks entirely live");
    }

    /**
     * How many test classes build a model without a sandbox today; MODELS_WITHOUT_A_SANDBOX_NAMES pins which.  Lower
     * it when one is repaired.
     */
    private static final int MODELS_WITHOUT_A_SANDBOX = 54;

    /** WHICH classes, so a repair and a new violation in the same round cannot cancel out. */
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
        "testRoutePicking.java",
        "testRouteReachesTheRails.java",
        "testRouteRoundTrip.java",
        "testRoutes.java",
        "testStationBlockedByAnotherPoint.java",
        "testStuckTrainAdvisory.java",
        "testTheCheckerAgreesWithTheBuild.java",
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

    private static Object field(Object target, String name) throws Exception
    {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);

        f.setAccessible(true);

        return f.get(target);
    }

    /**
     * Start is not offered over a layout that lives on the Central Station, which Start refuses.  An `autonomy.json`
     * can enable the button there, and `canStartAutonomy` used to ask only the button and the setup's errors.
     *
     * MUTATION: take `isRemoteLayout` out of `canStartAutonomy` and this fails.
     */
    @Test
    public void testStartIsNotOfferedOverACentralStationLayout() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        try
        {
            sandbox = support.LayoutSandbox.open();

            final org.traincontrol.gui.TrainControlUI[] built = new org.traincontrol.gui.TrainControlUI[1];

            javax.swing.SwingUtilities.invokeAndWait(() -> built[0] = new org.traincontrol.gui.TrainControlUI());

            org.traincontrol.gui.TrainControlUI ui = built[0];

            // WHAT SWITCHING TO A CENTRAL STATION LAYOUT STORES; the sandbox puts the real value back.
            org.traincontrol.gui.TrainControlUI.getPrefs().put(
                org.traincontrol.gui.TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            final javax.swing.JButton start = (javax.swing.JButton) field(ui, "startAutonomy");

            javax.swing.SwingUtilities.invokeAndWait(() -> start.setEnabled(true));

            try
            {
                assertTrue(ui.isRemoteLayout(), "precondition: the layout is not read as a Central Station one");

                assertFalse(ui.canStartAutonomy(), "Start is offered over a layout that lives on the Central"
                    + " Station, and pressing it is refused with 'autonomy needs a layout on this computer' - the"
                    + " menu offering what the handler refuses (REG-C3)");

                // AND THE GRAYED ITEM SAYS THE REFUSAL'S OWN REASON: its tooltip is this sentence.
                assertEquals(ui.whyAutonomyWillNotStart(),
                    org.traincontrol.util.I18n.t("autosetup.ui.menuNoSetupPossible"), "the greyed Start explains"
                    + " itself with a reason that is not the refusal's: over a Central Station layout it is that"
                    + " autonomy needs a layout on this computer, which is what pressing Start says (REG2-C2)");

                // AND PRESSING IT IS REFUSED SO, by the one gate every door that sends a train asks
                final java.lang.reflect.Method gate = org.traincontrol.gui.TrainControlUI.class.getDeclaredMethod(
                    "whyNoTrainMayBeSent", boolean.class);

                gate.setAccessible(true);

                final Object[] said = new Object[1];

                javax.swing.SwingUtilities.invokeAndWait(() ->
                {
                    try
                    {
                        said[0] = gate.invoke(ui, true);
                    }
                    catch (ReflectiveOperationException e)
                    {
                        throw new IllegalStateException(e);
                    }
                });

                assertEquals(said[0], org.traincontrol.util.I18n.t("autosetup.ui.menuNoSetupPossible"), "Start pressed"
                    + " over a layout that lives on the Central Station is not refused with 'autonomy needs a layout on"
                    + " this computer' - the gate asks the editor, the setup and the power, and Start's half of the"
                    + " setup question is where this refusal lives (OB-104, RLV13-C9)");
            }
            finally
            {
                javax.swing.SwingUtilities.invokeAndWait(() -> ui.dispose());
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * "Is a layout loaded" is stored once and read everywhere (OB-127, OB-128).  The invariant is ONE WRITER: a cached
     * answer with several writers is worse than the inference it replaced.
     *
     * MUTATION: a second assignment to layoutLoaded fails this, and so does a reader going back to
     * asking the model.
     */
    @Test
    public void testWhetherALayoutIsLoadedHasOneWriter() throws Exception
    {
        assertEquals(SourceOf.occurrences("this.layoutLoaded ="), 1,
            "the layout-loaded flag is assigned in more than one place, so two answers can be stored "
            + "and the window is back to disagreeing with itself - only updateLayoutLoaded may write "
            + "it");

        assertEquals(SourceOf.occurrences("layoutLoaded ="), 2,
            "expected the declaration and the single assignment and found something else - a writer "
            + "has been added or the field has been renamed");

        assertTrue(SourceOf.occurrences("updateLayoutLoaded();") >= 4,
            "fewer places refresh the flag than the moments the layout set can change - start-up, "
            + "the diagram rebuild, and the clear a Central Station switch performs. A flag that is "
            + "not refreshed where the truth moves is staler than the inference it replaced");

        // And each of those properties has a single writer, by the field or through the window's accessor.
        assertEquals(SourceOf.occurrences("popUpAllMenuItem.setEnabled(")
            + SourceOf.occurrences("popUpAllMenuItem().setEnabled("), 1,
            "All Layouts is set from more than one place again. repaintPathLabel used to set it TRUE "
            + "unconditionally for a local layout and re-enable it moments after the menu guard had "
            + "greyed it - the same two-writers fault a reviewer already fixed for Manage Pages");
    }

    /**
     * The stored answer agrees with the model it was taken from; the structural test cannot catch a flag written WRONG.
     */
    @Test
    public void testTheStoredAnswerMatchesTheModel() throws Exception
    {
        support.LayoutSandbox sandbox = null;

        try
        {
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
     * The scanner that decides what is code does not lose its place: a CHARACTER LITERAL holding a quote (as in
     * testARenameReachesTheTimetableOnScreen) and a STRING holding a line-comment marker each hid a window from the
     * check that keeps the battery off Adam's own railway.
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
     * A class is exempt from the count rule only when its own sandbox is in a setup method.  A file-wide @Before, or a
     * fixed lookback that reaches over the previous method's body, let a plain test with three windows and one sandbox
     * through.
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
     * Nothing in the suite opens the operator's own railway (OB-111): building a `TrainControlUI` or calling
     * `MarklinControlStation.init` reads the layout preference, and a window can raise a MODAL offer no test answers.
     * Asserted over every test file rather than fixed one class at a time.
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

            // THE WINDOW is a hard rule: it has no offenders, and it writes the folder and can raise a modal dialog
            // into the middle of a battery.  The MODEL is a ratchet instead, below: 56 pre-existing violations would
            // make a hard rule one somebody deletes rather than obeys.
            //
            // QUALIFIED OR NOT, AND EVERY ONE OF THEM, NOT THE FIRST: seven of the sixteen windowed classes write
            // `new org.traincontrol.gui.TrainControlUI()`, and a class with several test methods needs each window
            // checked.
            //
            // THE RAW SOURCE, blanked of strings and comments by one scanner in the order the compiler reads: a regex
            // that strips comments first loses the closing quote of `"file:///" + x`, and this method's own needles are
            // string literals.
            String scan = withoutStringsAndComments(body);

            java.util.List<Integer> windows = indicesOf(scan, WINDOW_BUILT);
            java.util.List<Integer> sandboxes = indicesOf(scan, SANDBOX_OPENED);

            if (windows.isEmpty()) continue;

            checked++;

            // Both scoping patterns are in use (one sandbox in @BeforeClass, or one per method), so count rather than
            // locate: a window nobody guarded is the wrong case.  A SANDBOX INSIDE A SETUP METHOD counts for the class,
            // not merely a file that has one somewhere.
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

        // A scan that matched nothing would pass while proving nothing, so the number is exact, not a floor: a class
        // that builds a window opens the operator's railway if it gets the sandbox wrong, so each new one is worth a
        // moment of somebody's attention.  If this fails because a class was legitimately added or removed, change the
        // number in the commit.  The model half pins NAMES for the same reason.

        // AND THE MODEL HALF, ratcheted: it cannot raise a dialog and there are too many to fix at once, so the
        // number can only fall (repairing one lowers MODELS_WITHOUT_A_SANDBOX by one).
        int loose = 0;

        java.util.List<String> looseNames = new java.util.ArrayList<>();

        for (java.io.File f : filesUnder(root))
        {
            if (f.getName().equals("LayoutSandbox.java")) continue;

            // A sample program's helper, not a test: it opens the simulated station for a user's own program, and the
            // one test that calls it (core.testTheApiExamplesRun) opens its sandbox first (FR-138).
            if (f.getName().equals("SimulatedStation.java")) continue;

            // The station's own helper: its callers are counted, each against its own sandbox.
            if (f.getName().equals("TestStation.java")) continue;

            String raw = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);

            // ONE SCAN FOR BOTH INDICES: they are compared with each other, so they must come from the same string.
            String code = withoutStringsAndComments(raw);

            int builds = earliest(code, "MarklinControlStation.init(", "= init(null", "TestStation.simulated");

            if (builds < 0) continue;

            // The same needle the window half uses, so an overload with arguments counts.
            java.util.List<Integer> opens = indicesOf(code, SANDBOX_OPENED);

            int sandbox = opens.isEmpty() ? -1 : opens.get(0);

            if (sandbox < 0 || sandbox > builds)
            {
                loose++;
                looseNames.add(f.getName());
            }
        }

        // THE HARD RULE FIRST.  A window that opens the operator's own layout is the case this
        // method was written for, so it is asserted before anything that is merely a number.
        assertEquals(offenders.toString(), "[]",
            "these tests open whatever layout the machine has, which on Adam\u2019s is his real "
            + "railway - they read it, write it back, and can raise a modal dialog that stalls the "
            + "battery: " + offenders);

        assertTrue(loose <= MODELS_WITHOUT_A_SANDBOX,
            "there are now " + loose + " test classes that build a model without pointing the layout "
            + "preference at a sandbox first, up from " + MODELS_WITHOUT_A_SANDBOX
            + ". Every one of them loads whatever layout the machine has, which on Adam\u2019s is his "
            + "real railway");


        // The count alone can absorb a repair and a new violation in the same round: pin WHICH classes too.
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

        // THE STALENESS RATCHETS, LAST OF ALL: TestNG stops a method at its first failed assertion, so a stale number
        // placed before a safety check hides it.  Every SAFETY assertion comes first and the numbers that merely record
        // where we are come after.  Raise the count when a class that builds a window and opens its sandbox first is
        // added.
        assertEquals(checked, 71,
            checked + " test classes were found to build a window, not the 71 there were when this "
            + "was pinned. Fewer means the pattern has gone stale and is checking less than it "
            + "thinks; more means a new class builds a window and this line wants updating - and "
            + "nothing else in this method is hidden by that any more, because the checks that "
            + "protect the railway all ran before it");

        assertEquals(loose, MODELS_WITHOUT_A_SANDBOX,
            loose + " such classes remain, fewer than the " + MODELS_WITHOUT_A_SANDBOX
            + " recorded. Lower MODELS_WITHOUT_A_SANDBOX to " + loose + " so the improvement is kept.");
    }

    /** Where a window is built, qualified or not. */
    private static final java.util.regex.Pattern WINDOW_BUILT =
        java.util.regex.Pattern.compile("new\\s+(?:org\\.traincontrol\\.gui\\.)?TrainControlUI\\s*\\(");

    /** Where a sandbox is opened, with or without arguments (LayoutSandbox.open(folder) is an overload). */
    private static final java.util.regex.Pattern SANDBOX_OPENED =
        java.util.regex.Pattern.compile("LayoutSandbox\\.open\\s*\\(");

    private static final java.util.regex.Pattern SETUP_METHOD =
        java.util.regex.Pattern.compile("@Before(?:Class|Method|Test)");

    private static java.util.List<Integer> indicesOf(String body, java.util.regex.Pattern p)
    {
        java.util.List<Integer> out = new java.util.ArrayList<>();

        java.util.regex.Matcher m = p.matcher(body);

        while (m.find()) out.add(m.start());

        return out;
    }

    /**
     * String literals, character literals and comments blanked in one pass, every other character keeping its place.
     * One scanner on purpose: stripping comments with a regex first eats the closing quote of a string holding a
     * line-comment marker, and a scanner unaware of character literals reads a quote inside one as a string.
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
     * Whether the code at this index sits inside a method carrying a @Before annotation.  The method is found by this
     * codebase's own formatting: a header is a line beginning with four spaces and a modifier.
     */
    private static boolean inASetupMethod(String scan, int at)
    {
        java.util.regex.Matcher m = METHOD_HEADER.matcher(scan);

        int header = -1;

        while (m.find() && m.start() < at) header = m.start();

        if (header < 0) return false;

        // BOUNDED AT THE PREVIOUS METHOD'S CLOSING BRACE, not at a fixed distance: a fixed lookback reads the previous
        // method's body and finds its annotation.  A method's annotations sit between that brace and its own header.
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
     * A first launch makes a track diagram; every later start still asks.  The half worth writing down is PRESENT
     * versus READABLE: a database file that exists and will not load is not a first launch, and treating it as empty
     * replaces a setup with a blank one (`testUiStateIsNotLostWhenUnreadable`).
     *
     * MUTATION: passing a constant at the call site fails the last assertion.
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
        assertTrue(SourceOf.allCode().contains("DEMO_LAYOUT_OUTPUT_PATH, !isFirstLaunch())"),
            "the start-up offer no longer asks whether this is a first launch, so either every new "
            + "user is asked a question they cannot answer, or every existing one has a track "
            + "diagram created without being asked");
    }

    private static void write(java.io.File f, String text) throws Exception
    {
        java.nio.file.Files.write(f.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Start-up shows the notice on its own window (FR-041), and never hides that window on the way in: the call that
     * takes the notice down sat in the `finally` of the window build, so it ran on the success path too.
     *
     * Read rather than run: a test that put a start-up notice on the screen would do it on every battery.  Every test
     * builds its model with showUI false, and the gate below keeps it that way.
     *
     * MUTATION this catches: putting `connectingFailed()` back in the finally after `latch.countDown()`.
     */
    @Test
    public void testTheStartupNoticeGoesOnTheWindowAndStaysThere() throws Exception
    {
        String init = SourceOf.declaration("public static MarklinControlStation init(String initIP, boolean simulate,"
            + " boolean showUI, boolean autoPowerOn, boolean debug)");

        // NO SECOND WINDOW (OB-170): showing a splash during start-up spends the one chance a process gets to put a
        // window in the foreground, and the keyboard was dead.
        assertEquals(SourceOf.occurrences("StartupSplash.show("), 0,
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

        // AND TAKEN DOWN WHEN THE START-UP FAILS: it is on the window now, so that takes the window with it.
        assertTrue(init.contains("!built.get() && theUI != null) theUI.connectingFailed()"),
            "a failed window build leaves a window standing that says it is connecting, with the "
            + "error dialog in front of it - which is the symptom FR-041 exists to remove, wearing "
            + "the fix's own clothes");

        // AND NOT ON THE WAY IN: the `finally` below runs on every path out of the window build, success included, so
        // anything in it that hides the window hides one `display()` is about to show again.
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
     * A sandbox is opened inside a `try`.  Weaker than "closed on every path": it buys a handler to put the close in,
     * and no early return between the open and that handler can skip it.  It does not check that a `close()` is
     * present.
     *
     * `close()` returns the machine-global layout preference to whatever the operator had.  Easy-to-miss ways out: a
     * window constructor that throws, `MarklinControlStation.init` failing to bind its port, an assertion in the
     * set-up part of a test, and `SkipException`.  Setup methods are exempt because their
     * `@AfterClass(alwaysRun = true)` teardown runs even when the set-up threw.
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

    /** Whether an offset sits inside a `try` block, found by walking the braces from the top of the file. */
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

    /** Whether the brace at {@code brace} opens a try block: `try {` or `try (...) {` (try-with-resources). */
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
