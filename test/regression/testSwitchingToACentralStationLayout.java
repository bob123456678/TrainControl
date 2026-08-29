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
        support.LayoutSandbox sandbox = support.LayoutSandbox.open();

        try
        {
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
            sandbox.close();
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
        support.LayoutSandbox sandbox = support.LayoutSandbox.open();

        try
        {
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
            sandbox.close();
        }
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
            // parse test_layout directly on purpose - and a check that fails for twenty classes
            // nobody has looked at is a check that gets disabled.
            // THE WINDOW is a hard rule: it has no offenders, and it is the case that writes the
            // folder and can raise a modal dialog into the middle of a battery.
            //
            // The MODEL is a ratchet instead, below. `MarklinControlStation.init` reads the same
            // preference and is the half that actually loads the pages, so those classes open the
            // operator\u2019s railway too - but 56 of them do, and a rule that fails for 56 pre-existing
            // violations is one somebody deletes rather than obeys.
            int opens = code.indexOf("new TrainControlUI()");

            if (opens < 0) continue;

            checked++;

            int sandbox = code.indexOf("LayoutSandbox.open()");

            if (sandbox < 0)
            {
                offenders.add(f.getName() + " builds a window and never opens a sandbox");
            }
            else if (sandbox > opens)
            {
                offenders.add(f.getName() + " opens its sandbox AFTER the thing that reads the "
                    + "preference, so the redirection comes too late");
            }
        }

        // A scan that matched nothing would pass while proving nothing.
        assertTrue(checked >= 5,
            "only " + checked + " test classes were found to build a window, which is fewer than "
            + "there are - the pattern this looks for has gone stale and it is now checking almost "
            + "nothing");

        // AND THE MODEL HALF, ratcheted (2026-08-28).
        //
        // These read the same preference and load the same railway; they simply cannot raise a dialog,
        // and there are too many to fix in one round. Pinned so the number can only fall - a new one
        // fails this, and repairing one is a matter of lowering MODELS_WITHOUT_A_SANDBOX by one.
        int loose = 0;

        for (java.io.File f : filesUnder(root))
        {
            if (f.getName().equals("LayoutSandbox.java")) continue;

            String code = stripComments(new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8));

            int builds = earliest(code, "MarklinControlStation.init(", "= init(null");

            if (builds < 0) continue;

            int sandbox = code.indexOf("LayoutSandbox.open()");

            if (sandbox < 0 || sandbox > builds) loose++;
        }

        assertTrue(loose <= MODELS_WITHOUT_A_SANDBOX,
            "there are now " + loose + " test classes that build a model without pointing the layout "
            + "preference at a sandbox first, up from " + MODELS_WITHOUT_A_SANDBOX
            + ". Every one of them loads whatever layout the machine has, which on Adam\u2019s is his "
            + "real railway");

        assertEquals(loose, MODELS_WITHOUT_A_SANDBOX,
            loose + " such classes remain, fewer than the " + MODELS_WITHOUT_A_SANDBOX
            + " recorded. Lower MODELS_WITHOUT_A_SANDBOX to " + loose + " so the improvement is kept.");

        assertEquals(offenders.toString(), "[]",
            "these tests open whatever layout the machine has, which on Adam\u2019s is his real "
            + "railway - they read it, write it back, and can raise a modal dialog that stalls the "
            + "battery: " + offenders);
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
     * The start-up splash is shown only when a window was asked for, and always taken down (FR-041).
     *
     * Adam: "while connecting to the CS and before the main UI loads, show a loading popup/overlay
     * like the cs2 sync one."
     *
     * Tested by reading rather than by showing one. A test that put a splash on screen would do it on
     * every battery, which is precisely the complaint he made today about a modal dialog appearing
     * while one ran - so the two properties worth pinning are the ones that keep it out of his way:
     * it is gated on `showUI`, which every test passes as false, and it is closed in a `finally` so a
     * failed start-up cannot leave one standing over nothing.
     */
    @Test
    public void testTheStartupSplashIsGatedAndAlwaysClosed() throws Exception
    {
        String init = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/marklin/MarklinControlStation.java")),
            java.nio.charset.StandardCharsets.UTF_8);

        int shown = init.indexOf("StartupSplash.show(");

        assertTrue(shown >= 0,
            "nothing shows a start-up splash, so connecting to a station that does not answer looks "
            + "like an application that failed to start (FR-041)");

        // GATED. Without this every test that builds a model would open a window on his screen.
        String around = init.substring(Math.max(0, shown - 200), shown);

        assertTrue(around.contains("showUI"),
            "the splash is no longer gated on showUI, so it appears whenever a model is built - "
            + "including in every test in this suite, on the operator's own screen");

        // BEFORE the connection it is reporting.
        int connects = init.indexOf("new NetworkProxy(");

        assertTrue(connects >= 0, "the connection has moved, so the ordering below means nothing");

        assertTrue(shown < connects,
            "the splash goes up after the connection is attempted, which is the whole of the wait it "
            + "exists to cover");

        // AND ALWAYS TAKEN DOWN.
        assertTrue(count(init, "StartupSplash.closeIfShown(splash)") >= 2,
            "the splash is closed on fewer paths than it was - the build runs on the event thread "
            + "and can throw, and a splash left standing over a working application is worse than "
            + "never having shown one");

        // THE PROPERTY, not a byte distance.
        //
        // This used to require the first close to sit within 900 characters of a `finally`, which is a
        // fact about formatting rather than about behaviour - and it failed on an IMPROVEMENT, when
        // the splash was additionally closed before rethrowing so the "already running" dialog is no
        // longer hidden behind it. What has to be true is that it comes down on the normal path and on
        // the exception path.
        assertTrue(init.contains("finally") && count(init, "StartupSplash.closeIfShown(splash)") >= 2,
            "the splash is closed on fewer paths than it was - the window build runs on the event "
            + "thread and can throw, and a splash left standing over a working application is worse "
            + "than never having shown one");

        String betweenShowAndAwait = init.substring(shown, init.indexOf("latch.await()", shown));

        assertTrue(betweenShowAndAwait.contains("closeIfShown")
                || init.indexOf("closeIfShown", init.indexOf("latch.await()", shown)) >= 0,
            "nothing takes the splash down anywhere near the window build");

        // And the words exist to put in it.
        String bundle = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/org/traincontrol/resources/messages.properties")),
            java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(bundle.contains("\nui.splashConnecting="),
            "the splash asks for a message key that is not in the bundle, so it would show the key");
    }
}
