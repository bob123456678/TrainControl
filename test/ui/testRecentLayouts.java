package ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutPopupUI;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import support.Answering;
import support.Components;

/**
 * Layouts &gt; Recent Layouts lists the last five layout folders opened, most recent first, without the open one, and
 * opens one by Open Layout's own rules (FR-145).
 *
 * The window runs over a sandboxed copy of the test layout (A) and a second copy (B) in a temporary folder.  The list
 * is what `layoutFolderIs` records in the run's own preferences; each claim sets it and the class puts it back.
 */
public class testRecentLayouts
{
    private static final String PROBE = "FR-145 probe route";

    private static support.LayoutSandbox sandbox;
    private static Path second;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static String recentWas;
    private static String lastLocalWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        recentWas = TrainControlUI.getPrefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, null);
        lastLocalWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, null);

        sandbox = support.LayoutSandbox.open();

        second = copyOfTheTestLayout("tc-recent-layout");

        model = support.TestStation.simulatedWithWindow();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }

            if (model != null) model.stop();

            if (second != null) Util.deleteTree(second.toFile(), 0);
        }
        finally
        {
            putBack(TrainControlUI.RECENT_LAYOUTS_PREF, recentWas);
            putBack(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, lastLocalWas);

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Opening a recent layout switches to it and loads it; the menu then lists the layout left, not the one open, and
     * the record has the opened folder first, each folder once.
     *
     * MUTATION: return from `layoutFolderIs` before it writes Recent Layouts, and the record keeps the order it was
     * given rather than the folders opened.
     */
    @Test
    public void testARecentLayoutOpensAndTheOneLeftIsListed() throws Exception
    {
        String a = sandbox.getFolder().getAbsolutePath();
        String b = second.toFile().getAbsolutePath();

        record(b, a);

        assertEquals(shown(), Collections.singletonList(b), "Recent Layouts does not list B alone while A is open");

        open(b);

        assertEquals(shown(), Collections.singletonList(a), "after opening B, Recent Layouts does not list A alone");

        open(a);

        assertEquals(shown(), Collections.singletonList(b), "after opening A again, Recent Layouts does not list B alone");

        assertEquals(recorded(), Arrays.asList(a, b), "the record is not the folders opened, most recent first, once each");
    }

    /**
     * Opening another layout closes the page windows of the one left and forgets them, so a start restores none of them
     * on the layout opened (OB-466), though B's pages have A's names.
     *
     * MUTATION: leave out `closeThePageWindows()` in `TrainControlUI.sourceIsNow`, and A's page window stays open.
     */
    @Test
    public void testOpeningAnotherLayoutClosesThePageWindowsOfTheOneLeft() throws Exception
    {
        String a = sandbox.getFolder().getAbsolutePath();
        String b = second.toFile().getAbsolutePath();
        boolean rememberWas = TrainControlUI.getPrefs().getBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, false);

        assertEquals(new File(String.valueOf(ui.localLayoutPath())).getAbsolutePath(), a, "precondition: A is not open");

        TrainControlUI.getPrefs().putBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, true);

        try
        {
            record(b);

            LayoutPopupUI window = support.AskingWindows.openThePage(ui);

            // Remembered as the application's exit remembers it
            SwingUtilities.invokeAndWait(() -> invoke("saveLayoutTitles"));

            open(b);

            assertEquals(pageWindows(), new ArrayList<String>(), "a page window of the layout left is still open");
            assertFalse(window.isShowing(), "the page window of the layout left is still open");

            // What a start does with the page windows remembered
            SwingUtilities.invokeAndWait(ui::restoreLayoutTitles);
            Components.settle();

            assertEquals(pageWindows(), new ArrayList<String>(), "a start reopens the page windows of the layout left");
        }
        finally
        {
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.REMEMBER_WINDOW_LOCATION, rememberWas);

            SwingUtilities.invokeAndWait(() ->
            {
                for (java.awt.Window w : java.awt.Window.getWindows()) if (w instanceof LayoutPopupUI) w.dispose();
            });

            if (!a.equals(new File(String.valueOf(ui.localLayoutPath())).getAbsolutePath())) open(a);
        }
    }

    /** The pages of the page windows showing. */
    private static List<String> pageWindows()
    {
        return Components.onTheEventThread(() ->
        {
            List<String> pages = new ArrayList<>();

            for (java.awt.Window w : java.awt.Window.getWindows())
            {
                if (w instanceof LayoutPopupUI && w.isShowing()) pages.add(((LayoutPopupUI) w).getLayoutTitle());
            }

            return pages;
        });
    }

    private static void invoke(String method)
    {
        try
        {
            support.Reflect.method(TrainControlUI.class, method).invoke(ui);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The layout open when TrainControl started is listed once another is opened, though it was never recorded
     * (OB-378).
     *
     * MUTATION: drop the layout being left from `layoutFolderIs`'s record, and Recent Layouts is empty after B opens.
     */
    @Test
    public void testTheLayoutOpenAtStartIsListedOnceAnotherOpens() throws Exception
    {
        String a = sandbox.getFolder().getAbsolutePath();
        String b = second.toFile().getAbsolutePath();

        if (!a.equals(new File(String.valueOf(ui.localLayoutPath())).getAbsolutePath()))
        {
            record(a);

            open(a);
        }

        // A is open and was never recorded, as at start-up
        record(b);

        open(b);

        assertEquals(shown(), Collections.singletonList(a), "after opening B, Recent Layouts does not list A, the"
            + " layout open before it");

        open(a);

        assertEquals(shown(), Collections.singletonList(b), "after opening A again, Recent Layouts does not list B alone");
    }

    /**
     * Five are listed, most recent first, without the open layout; with none to list the submenu is grayed.
     *
     * MUTATION: drop the cut to `RECENT_LAYOUTS_SHOWN` in `refreshRecentLayouts`, and seven are listed.
     */
    @Test
    public void testFiveAreListedMostRecentFirst() throws Exception
    {
        String a = sandbox.getFolder().getAbsolutePath();

        List<String> seven = new ArrayList<>();

        for (int i = 1; i <= 7; i++) seven.add(new File(second.toFile(), "gone " + i).getAbsolutePath());

        List<String> stored = new ArrayList<>(seven);

        stored.add(2, a);

        record(stored.toArray(new String[0]));

        assertEquals(shown(), seven.subList(0, 5), "Recent Layouts does not list the five most recent, without the open"
            + " one");

        record(a);

        assertEquals(shown(), new ArrayList<String>(), "Recent Layouts lists the open layout");

        // Live, with one gray line saying why there is nothing to open.
        assertTrue(Components.onTheEventThread(recentMenu()::isEnabled), "Recent Layouts is grayed with nothing to"
            + " list");

        JMenuItem first = Components.onTheEventThread(() -> recentMenu().getItem(0));

        assertEquals(first.getText(), I18n.t("ui.main.toolbar.noOtherLayouts"), "Recent Layouts does not say it has"
            + " no other layout");

        assertFalse(first.isEnabled(), "the line saying there is no other layout can be chosen");
    }

    /**
     * Each item reads its place in the list ("1. ", "2. ", FR-148) then its label (OB-390).  What an item opens is still
     * the folder its tooltip names.
     *
     * MUTATION: drop the number from the item's text in `refreshRecentLayouts`, and the items read as before.
     */
    @Test
    public void testEachItemIsNumberedInTheOrderShown() throws Exception
    {
        File x = new File(second.toFile(), "x" + File.separator + "same");
        File y = new File(second.toFile(), "y" + File.separator + "same");
        File lone = new File(second.toFile(), "lone");
        File other = new File(second.toFile(), "other");

        record(lone.getAbsolutePath(), x.getAbsolutePath(), y.getAbsolutePath(), other.getAbsolutePath(),
            sandbox.getFolder().getAbsolutePath(), new File(second.toFile(), "last").getAbsolutePath());

        String apart = "\u2026" + File.separator;

        assertEquals(listed(JMenuItem::getText), Arrays.asList("1. lone", "2. " + apart + "x" + File.separator + "same",
            "3. " + apart + "y" + File.separator + "same", "4. other", "5. last"), "Recent Layouts does not number its"
            + " items in the order shown, over the name or the folders that tell two of one name apart");

        assertEquals(shown(), Arrays.asList(lone.getAbsolutePath(), x.getAbsolutePath(), y.getAbsolutePath(),
            other.getAbsolutePath(), new File(second.toFile(), "last").getAbsolutePath()),
            "numbering changed what the items open or their order");
    }

    /**
     * A long path never makes the menu wide (OB-390, "full paths can make the window extremely wide, manage this"): two
     * folders of one name deep under long folders, and one long name, each read no more than `RECENT_LABEL_MOST`
     * characters after the number, the long name cut in the middle with its end kept, and their tooltips hold the whole
     * path.
     *
     * MUTATION: return the whole path in `LayoutMenus.recentLabel` where two folders share a name, or skip the cut past
     * `RECENT_LABEL_MOST`, and this fails.
     */
    @Test
    public void testALongPathIsShortened() throws Exception
    {
        String deep = "a-folder-with-a-long-name-for-the-menu" + File.separator + "and-another-long-one-below-it";

        File x = new File(second.toFile(), deep + File.separator + "first" + File.separator + "same");
        File y = new File(second.toFile(), deep + File.separator + "second" + File.separator + "same");
        File long_ = new File(second.toFile(), "a-layout-folder-whose-own-name-is-far-longer-than-fifty-characters");

        record(x.getAbsolutePath(), y.getAbsolutePath(), long_.getAbsolutePath(), sandbox.getFolder().getAbsolutePath());

        List<String> texts = listed(JMenuItem::getText);
        List<String> tips = listed(JMenuItem::getToolTipText);

        assertEquals(texts.size(), 3, "precondition: Recent Layouts does not list the three folders: " + texts);

        // LayoutMenus.RECENT_LABEL_MOST, which the package keeps to itself
        int most = 50;

        for (int i = 0; i < texts.size(); i++)
        {
            String label = texts.get(i).substring((i + 1 + ". ").length());

            assertTrue(label.length() <= most, "item " + (i + 1) + " reads " + label.length() + " characters, more than"
                + " " + most + ": " + label);
        }

        assertEquals(texts.get(0), "1. \u2026" + File.separator + "first" + File.separator + "same", "the first of two"
            + " folders of one name does not read the one folder that tells them apart");

        assertEquals(texts.get(1), "2. \u2026" + File.separator + "second" + File.separator + "same", "the second of"
            + " two folders of one name does not read the one folder that tells them apart");

        assertTrue(texts.get(2).endsWith("than-fifty-characters") && texts.get(2).contains("\u2026"), "a long name is"
            + " not cut in the middle with its end kept: " + texts.get(2));

        for (int i = 0; i < tips.size(); i++)
        {
            String whole = new File[] {x, y, long_}[i].getAbsolutePath();

            assertTrue(String.valueOf(tips.get(i)).replaceAll("<[^>]*>", "").replace("&nbsp;", " ")
                .replaceAll("\\s+", "").contains(whole.replaceAll("\\s+", "")), "item " + (i + 1) + "'s tooltip does"
                + " not hold the whole path " + whole + ": " + tips.get(i));
        }
    }

    /**
     * Two dated copies of one long-named folder still read apart once cut to 50 characters (OB-390): the cut keeps the
     * folder that differs.
     *
     * MUTATION: skip the head's run past the first difference in `LayoutMenus.cut`, and this fails.
     */
    @Test
    public void testTwoLongCopiesStayApart() throws Exception
    {
        String name = "Basement railway CS3 layout files";

        File x = new File(second.toFile(), "Backup 2026-10-01" + File.separator + name);
        File y = new File(second.toFile(), "Backup 2026-10-08" + File.separator + name);

        record(x.getAbsolutePath(), y.getAbsolutePath(), sandbox.getFolder().getAbsolutePath());

        List<String> texts = listed(JMenuItem::getText);

        assertEquals(texts.size(), 2, "precondition: Recent Layouts does not list the two copies: " + texts);

        assertTrue(texts.get(0).length() - "1. ".length() <= 50 && texts.get(0).endsWith("layout files"),
            "the first copy is not cut to 50 characters with its end kept: " + texts.get(0));

        assertFalse(texts.get(0).substring(3).equals(texts.get(1).substring(3)), "the two copies read the same: "
            + texts);
    }

    /**
     * Two copies whose folders differ only past where a cut could keep them are left whole, not cut alike (OB-390).
     *
     * MUTATION: return the cut label whatever it reads like in `LayoutMenus.recentLabel`, and this fails.
     */
    @Test
    public void testCopiesThatDifferLateAreLeftWhole() throws Exception
    {
        File x = new File(second.toFile(), "Basement railway CS3 layout files 2026-10-01" + File.separator + "TrainControl");
        File y = new File(second.toFile(), "Basement railway CS3 layout files 2026-10-08" + File.separator + "TrainControl");

        record(x.getAbsolutePath(), y.getAbsolutePath(), sandbox.getFolder().getAbsolutePath());

        List<String> texts = listed(JMenuItem::getText);

        assertEquals(texts.size(), 2, "precondition: Recent Layouts does not list the two copies: " + texts);

        assertEquals(texts.get(0), "1. \u2026" + File.separator + x.getParentFile().getName() + File.separator
            + "TrainControl", "the first copy is not left whole");

        assertEquals(texts.get(1), "2. \u2026" + File.separator + y.getParentFile().getName() + File.separator
            + "TrainControl", "the second copy is not left whole");
    }

    /**
     * A folder that has gone is said once, with its path, taken off the list, and the open layout stays.
     *
     * MUTATION: drop `forgetRecentLayout` from `openRecentLayout`, and the gone folder stays listed.
     */
    @Test
    public void testAGoneFolderIsSaidOnceAndDropped() throws Exception
    {
        String a = sandbox.getFolder().getAbsolutePath();
        String b = second.toFile().getAbsolutePath();
        String gone = new File(second.toFile(), "no such layout").getAbsolutePath();

        record(gone, b, a);

        assertEquals(shown(), Arrays.asList(gone, b), "precondition: Recent Layouts does not list the gone folder and B");

        List<String> said = click(gone);

        assertEquals(said, Collections.singletonList(I18n.f("ui.main.toolbar.errorRecentLayoutGone", gone)),
            "a gone folder was not said once, with its path");

        assertEquals(shown(), Collections.singletonList(b), "the gone folder is still on Recent Layouts");

        assertEquals(new File(ui.localLayoutPath()).getAbsolutePath(), a, "the open layout changed");
    }

    /**
     * While a route runs each listed layout is grayed with the run's reason, the folder under them staying live, and a
     * recent layout refuses as Open Layout does.
     *
     * MUTATION: take `refuseWhileARunHoldsTheSetup` out of `openRecentLayout`.
     */
    @Test
    public void testARecentLayoutWaitsForARun() throws Exception
    {
        String a = sandbox.getFolder().getAbsolutePath();
        String b = second.toFile().getAbsolutePath();

        record(b, a);

        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));

        assertTrue(model.newRoute(PROBE, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
            "precondition: the probe route could not be made");

        try
        {
            assertEquals(shown(), Collections.singletonList(b), "precondition: Recent Layouts does not list B");

            assertTrue(model.getRoute(PROBE).setExecuting(), "precondition: the probe route would not run");

            // The run's reason replaces the tip that names the folder, so B is the one item listed.
            assertEquals(listed(item -> item.isEnabled() + " " + item.getToolTipText().replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ").trim()), Collections.singletonList("false " + I18n.t("ui.main.errorNoEditsWhileRunning")),
                "a recent layout is not grayed with the run's reason while a route runs");

            JMenuItem folder = Components.onTheEventThread(() ->
                recentMenu().getItem(recentMenu().getItemCount() - 1));

            assertEquals(folder.getText(), I18n.t("ui.main.toolbar.showCurrentLayoutFolder"));

            assertTrue(folder.isEnabled(), "Show Current Layout Folder is grayed while a route runs");

            // The click's own refusal is the backstop behind the gray.
            JMenuItem waiting = Components.onTheEventThread(() -> recentMenu().getItem(0));

            Components.onTheEventThread(() -> { waiting.setEnabled(true); return null; });

            List<String> said = click(waiting);

            assertEquals(said, Collections.singletonList(I18n.t("ui.main.errorNoEditsWhileRunning")), "a recent layout"
                + " did not refuse, before anything else, while a route runs");

            assertEquals(new File(ui.localLayoutPath()).getAbsolutePath(), a, "a recent layout opened while a route runs");
        }
        finally
        {
            model.getRoute(PROBE).stopExecuting();
            model.deleteRoute(PROBE);
        }
    }

    /**
     * A folder that holds no layout is refused with the message before anything changes: the layout in use and its
     * pages stay; going ahead, the failed read would send the sync to the Central Station's layout.
     *
     * MUTATION: drop the `config/gleisbild.cs2` check at the top of `TrainControlUI.openLayoutFolder`.
     */
    @Test(timeOut = 120000)
    public void testAFolderWithNoLayoutIsRefusedAndNothingChanges() throws Exception
    {
        File empty = Files.createTempDirectory("tc-not-a-layout").toFile();

        try
        {
            refusedAndNothingChanges(empty, "a folder with no layout");
        }
        finally
        {
            Util.deleteTree(empty, 0);
        }
    }

    /**
     * A folder with a page index whose every page fails to read is refused the same way, before anything changes:
     * going ahead, the sync's failed read would send it to the Central Station's layout.
     *
     * MUTATION: in `TrainControlUI.openLayoutFolder`, ask `CS2File.holdsALayout` again instead of `opensAsALayout`.
     */
    @Test(timeOut = 120000)
    public void testAFolderWhosePagesWillNotReadIsRefusedAndNothingChanges() throws Exception
    {
        File broken = Files.createTempDirectory("tc-pages-unread").toFile();

        try
        {
            File config = new File(broken, "config");

            assertTrue(config.mkdirs(), "cannot make " + config);

            Files.copy(new File("test/test_layout/config/gleisbild.cs2").toPath(),
                new File(config, "gleisbild.cs2").toPath());

            refusedAndNothingChanges(broken, "a folder whose pages will not read");
        }
        finally
        {
            Util.deleteTree(broken, 0);
        }
    }

    /** Opens the folder from Recent Layouts, with A open: refused with the message, and the layout and pages stay. */
    private static void refusedAndNothingChanges(File folder, String what) throws Exception
    {
        String a = sandbox.getFolder().getAbsolutePath();
        String path = folder.getAbsolutePath();

        record(path, a);

        assertEquals(shown(), Collections.singletonList(path), "precondition: Recent Layouts does not list the folder");

        List<String> pagesWere = new ArrayList<>(model.getLayoutList());

        assertEquals(click(path), Collections.singletonList(I18n.t("layout.ui.errorInvalidPathOrCorruptData")),
            what + " was not refused with the message");

        nothingChanged(a, pagesWere, "opening " + what);
    }

    /** The layout in use is still A, with the pages it had. */
    private static void nothingChanged(String a, List<String> pagesWere, String doing)
    {
        assertEquals(new File(String.valueOf(ui.localLayoutPath())).getAbsolutePath(), a,
            doing + " changed the layout in use");

        assertEquals(new ArrayList<>(model.getLayoutList()), pagesWere, doing + " changed the pages");
    }

    /**
     * A folder with one page that will not read still opens, with a placeholder in that page's place: an unreadable
     * page costs that page only (OB-408), so the refusal above is for a folder where no page reads.
     *
     * MUTATION: in `CS2File.readNoPage`, ask `anyMatch` instead of `allMatch`, and the folder is refused.
     */
    @Test(timeOut = 180000)
    public void testAFolderWithOneUnreadablePageStillOpens() throws Exception
    {
        String a = sandbox.getFolder().getAbsolutePath();
        Path partial = copyOfTheTestLayout("tc-one-page-unread");

        try
        {
            Files.delete(partial.resolve("config/gleisbilder/5 - Test.cs2"));

            String path = partial.toFile().getAbsolutePath();

            record(path, a);

            open(path);

            assertEquals(model.getLayoutList().size(), 5, "the pages that read, and the placeholder, are not all there: "
                + model.getLayoutList());

            assertTrue(model.getLayout("5 - Test").isUnreadable(), "the page that will not read has no placeholder");
        }
        finally
        {
            try
            {
                if (!a.equals(new File(String.valueOf(ui.localLayoutPath())).getAbsolutePath())) open(a);
            }
            finally
            {
                Util.deleteTree(partial.toFile(), 0);
            }
        }
    }

    /** A copy of the test layout in a temporary folder of its own. */
    private static Path copyOfTheTestLayout(String prefix) throws java.io.IOException
    {
        Path copy = Files.createTempDirectory(prefix);
        Path from = new File("test/test_layout").toPath();

        try (java.util.stream.Stream<Path> walk = Files.walk(from))
        {
            for (Path p : (Iterable<Path>) walk::iterator)
            {
                Path to = copy.resolve(from.relativize(p).toString());

                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }

        return copy;
    }

    private static void record(String... folders)
    {
        TrainControlUI.getPrefs().put(TrainControlUI.RECENT_LAYOUTS_PREF, String.join("\n", folders));
    }

    private static List<String> recorded()
    {
        return Arrays.asList(TrainControlUI.getPrefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, "").split("\n"));
    }

    private static JMenu recentMenu()
    {
        JMenu menu = (JMenu) Components.withText(ui, I18n.t("ui.main.toolbar.recentLayouts"));

        assertNotNull(menu, "the Layouts menu has no Recent Layouts");

        return menu;
    }

    /** Opens the Layouts menu as a click does, so it is brought up to date, and gives the listed folders' paths. */
    private static List<String> shown()
    {
        return listed(testRecentLayouts::pathOf);
    }

    /** Opens the Layouts menu as a click does, and gives what the function reads off each Recent Layouts item. */
    private static List<String> listed(java.util.function.Function<JMenuItem, String> reading)
    {
        final JMenu layouts = (JMenu) Components.withText(ui, I18n.t("ui.main.toolbar.layouts"));

        assertNotNull(layouts, "the window has no Layouts menu");

        return Components.onTheEventThread(() ->
        {
            layouts.setSelected(true);
            layouts.setSelected(false);

            List<String> read = new ArrayList<>();

            for (JMenuItem item : layoutItems()) read.add(reading.apply(item));

            return read;
        });
    }

    /** The Recent Layouts item that opens the folder; fails when there is none. */
    private static JMenuItem listedItem(String folder)
    {
        JMenuItem item = Components.onTheEventThread(() ->
        {
            for (JMenuItem each : layoutItems())
            {
                if (folder.equals(pathOf(each))) return each;
            }

            return null;
        });

        assertNotNull(item, "Recent Layouts does not list " + folder);

        return item;
    }

    /** The items that each open a listed layout, numbered; not the line for none, nor the folder under them. */
    private static List<JMenuItem> layoutItems()
    {
        List<JMenuItem> items = new ArrayList<>();

        for (int i = 0; i < recentMenu().getItemCount(); i++)
        {
            JMenuItem item = recentMenu().getItem(i);

            if (item != null && item.getText().matches("\\d+\\. .*")) items.add(item);
        }

        return items;
    }

    /** Clicks the listed folder, and gives back what was asked. */
    private static List<String> click(String folder) throws Exception
    {
        return click(listedItem(folder));
    }

    /** Clicks the item, and gives back what was asked. */
    private static List<String> click(JMenuItem item) throws Exception
    {

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            SwingUtilities.invokeAndWait(item::doClick);

            answering.awaitAsked(1, 5000);

            Components.settle();

            return answering.asked();
        }
    }

    /** Opens a listed folder and waits until its pages are loaded. */
    private static void open(String folder) throws Exception
    {
        // The submenu is filled as the Layouts menu opens
        shown();

        List<String> said = new ArrayList<>();

        JMenuItem item = listedItem(folder);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(item);

            assertTrue(Components.waitFor(() -> folder.equals(new File(String.valueOf(ui.localLayoutPath()))
                .getAbsolutePath()), 10000), "the recent layout " + folder + " did not open");

            // The load runs behind a spinner, which may have come and gone already
            Components.waitFor(testRecentLayouts::loading, 3000);

            assertTrue(Components.waitFor(() -> !loading(), 60000), "the recent layout " + folder + " never loaded");

            Components.settle();

            assertFalse(model.getLayoutList().isEmpty(), "the recent layout " + folder + " has no pages loaded");

            said.addAll(answering.asked());
        }

        assertEquals(said, new ArrayList<String>(), "opening a recent layout asked something");
    }

    /** The folder an item opens, which its tooltip says in full (wrapped as HTML when long). */
    private static String pathOf(JMenuItem item)
    {
        return item.getToolTipText().replaceAll("<[^>]*>", "").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&amp;", "&").trim();
    }

    private static boolean loading()
    {
        for (java.awt.Window w : java.awt.Window.getWindows())
        {
            if (w instanceof org.traincontrol.gui.widgets.BusyDialog && w.isShowing()) return true;
        }

        return false;
    }

    private static void putBack(String key, String value)
    {
        if (value == null) TrainControlUI.getPrefs().remove(key);
        else TrainControlUI.getPrefs().put(key, value);
    }
}
