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
     *
     * @throws Exception from the window
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
     * Five are listed, most recent first, without the open layout; with none to list the submenu is grayed.
     *
     * MUTATION: drop the cut to `RECENT_LAYOUTS_SHOWN` in `refreshRecentLayouts`, and seven are listed.
     *
     * @throws Exception from the window
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
     * A folder that has gone is said once, with its path, taken off the list, and the open layout stays.
     *
     * MUTATION: drop `forgetRecentLayout` from `openRecentLayout`, and the gone folder stays listed.
     *
     * @throws Exception from the window
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
     * While a route runs Recent Layouts is grayed with the run's reason, and a recent layout refuses as Open Layout does.
     *
     * MUTATION: take `refuseWhileARunHoldsTheSetup` out of `openRecentLayout`.
     *
     * @throws Exception from the window
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
        final JMenu layouts = (JMenu) Components.withText(ui, I18n.t("ui.main.toolbar.layouts"));

        assertNotNull(layouts, "the window has no Layouts menu");

        return Components.onTheEventThread(() ->
        {
            layouts.setSelected(true);
            layouts.setSelected(false);

            List<String> paths = new ArrayList<>();

            for (int i = 0; i < recentMenu().getItemCount(); i++) paths.add(recentMenu().getItem(i).getToolTipText());

            return paths;
        });
    }

    /** Clicks the listed folder, and gives back what was asked. */
    private static List<String> click(String folder) throws Exception
    {
        JMenuItem item = Components.onTheEventThread(() ->
        {
            for (int i = 0; i < recentMenu().getItemCount(); i++)
            {
                if (folder.equals(recentMenu().getItem(i).getToolTipText())) return recentMenu().getItem(i);
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
        List<String> said = new ArrayList<>();

        JMenuItem item = Components.onTheEventThread(() ->
        {
            for (int i = 0; i < recentMenu().getItemCount(); i++)
            {
                if (folder.equals(recentMenu().getItem(i).getToolTipText())) return recentMenu().getItem(i);
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
