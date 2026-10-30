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

        second = Files.createTempDirectory("tc-recent-layout");

        Path from = new File("test/test_layout").toPath();

        try (java.util.stream.Stream<Path> walk = Files.walk(from))
        {
            for (Path p : (Iterable<Path>) walk::iterator)
            {
                Path to = second.resolve(from.relativize(p).toString());

                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }

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

        shown();

        assertFalse(Components.onTheEventThread(recentMenu()::isEnabled), "Recent Layouts is not grayed with nothing to"
            + " list");
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
     * Two dated copies of one long-named folder still read apart once cut (OB-390): the cut keeps the folder that
     * differs.
     *
     * MUTATION: cut at `RECENT_LABEL_MOST / 3` whatever the label in `LayoutMenus.recentLabel`, and this fails.
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
            "precondition: the first copy is not cut with its end kept: " + texts.get(0));

        assertFalse(texts.get(0).substring(3).equals(texts.get(1).substring(3)), "the two copies read the same: "
            + texts);
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
     * While a route runs Recent Layouts is grayed with the run's reason, and a recent layout refuses as Open Layout
     * does.
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
            assertTrue(model.getRoute(PROBE).setExecuting(), "precondition: the probe route would not run");

            assertEquals(shown(), Collections.singletonList(b), "precondition: Recent Layouts does not list B");

            assertFalse(Components.onTheEventThread(recentMenu()::isEnabled), "Recent Layouts is not grayed while a"
                + " route runs");

            List<String> said = click(b);

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

            for (int i = 0; i < recentMenu().getItemCount(); i++) read.add(reading.apply(recentMenu().getItem(i)));

            return read;
        });
    }

    /** Clicks the listed folder, and gives back what was asked. */
    private static List<String> click(String folder) throws Exception
    {
        JMenuItem item = Components.onTheEventThread(() ->
        {
            for (int i = 0; i < recentMenu().getItemCount(); i++)
            {
                if (folder.equals(pathOf(recentMenu().getItem(i)))) return recentMenu().getItem(i);
            }

            return null;
        });

        assertNotNull(item, "Recent Layouts does not list " + folder);

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

        JMenuItem item = Components.onTheEventThread(() ->
        {
            for (int i = 0; i < recentMenu().getItemCount(); i++)
            {
                if (folder.equals(pathOf(recentMenu().getItem(i)))) return recentMenu().getItem(i);
            }

            return null;
        });

        assertNotNull(item, "Recent Layouts does not list " + folder);

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
