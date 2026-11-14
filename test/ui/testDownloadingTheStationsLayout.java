package ui;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.MenuListener;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.BusyDialog;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Reflect;
import support.StandInStation;
import support.StationRig;
import support.Trees;

/**
 * Layouts &gt; Download Central Station Layout Files, pressed in a window started on a stand-in CS3's layout (the pages
 * of `test/cs3_v261`): the copy is then the layout in use, as Open Layout would leave it.
 */
public class testDownloadingTheStationsLayout
{
    private File root;
    private StationRig rig;
    private TrainControlUI ui;
    private String recentWas;
    private String lastLocalWas;

    /** While set, the station holds every page request until it is counted down; `pageAsked` says one is held. */
    private volatile CountDownLatch pagesHeld;
    private volatile CountDownLatch pageAsked;

    @BeforeMethod
    public void openOnTheStation() throws Exception
    {
        support.Display.needed("the door is a menu item of the main window");

        recentWas = TrainControlUI.getPrefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, null);
        lastLocalWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, null);
        root = Files.createTempDirectory("tc-download-door").toFile();

        BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", null);

        rig = new StationRig(root, (path, n) ->
        {
            CountDownLatch held = pagesHeld;

            if (held != null && path.startsWith("/config/gleisbilder/"))
            {
                pageAsked.countDown();

                try
                {
                    held.await(20, TimeUnit.SECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            return cs3.apply(path, n);
        }, true, true);
        ui = (TrainControlUI) rig.model.getGUI();

        assertTrue(Components.waitFor(() -> !rig.model.getLayoutList().isEmpty(), 30000),
            "precondition: the window did not read the station's pages");

        Components.settle();

        assertTrue(ui.isRemoteLayout(), "precondition: the window is not on the station's layout");
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception
    {
        try
        {
            support.EditorsOpen.closeAll();

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }
        }
        finally
        {
            if (rig != null) rig.close();

            putBack(TrainControlUI.RECENT_LAYOUTS_PREF, recentWas);
            putBack(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, lastLocalWas);

            Trees.delete(root);
        }
    }

    private static void putBack(String key, String was)
    {
        if (was == null) TrainControlUI.getPrefs().remove(key);
        else TrainControlUI.getPrefs().put(key, was);
    }

    /** Presses the door, choosing `into` in its folder chooser and answering each question by `rule`. */
    private Answering press(File into, java.util.function.Function<String, Answering.Answer> rule) throws Exception
    {
        Answering answering = Answering.start(rule).choosing(into);

        final JMenuItem door = (JMenuItem) Reflect.get(TrainControlUI.class, ui, "downloadCSLayoutMenuItem");

        SwingUtilities.invokeLater(door::doClick);

        return answering;
    }

    /** The Autonomy menu's items as the user opens it. */
    private List<String> autonomyMenu() throws Exception
    {
        final List<String> items = new ArrayList<>();

        SwingUtilities.invokeAndWait(() ->
        {
            JMenuBar bar = ui.getJMenuBar();
            String heading = I18n.t("autosetup.ui.menuAutonomy");

            for (int i = 0; i < bar.getMenuCount(); i++)
            {
                JMenu menu = bar.getMenu(i);

                if (menu == null || !heading.equals(menu.getText())) continue;

                for (MenuListener listener : menu.getMenuListeners()) listener.menuSelected(null);

                for (int at = 0; at < menu.getItemCount(); at++)
                {
                    if (menu.getItem(at) != null) items.add(menu.getItem(at).getText());
                }
            }
        });

        return items;
    }

    /**
     * After the download the Autonomy menu offers autonomy on the copy, not "Autonomy needs a layout stored on this
     * computer", which the window said until a restart; and a folder holding no layout is not asked about.
     *
     * MUTATION: put back the door's own sequence (`syncWithCS2`, `repaintLayout`) in place of `openLayoutFolder`, and the
     * menu still says autonomy needs a layout on this computer.
     */
    @Test(timeOut = 120000)
    public void testAfterTheDownloadAutonomyCanBeSetUpOnTheCopy() throws Exception
    {
        final String needsALocalLayout = I18n.t("autosetup.ui.menuNoSetupPossible");
        final File into = new File(root, "copy");

        assertTrue(into.mkdirs(), "precondition: no folder to download into");
        assertTrue(autonomyMenu().contains(needsALocalLayout),
            "precondition: on the station's layout the Autonomy menu does not say it needs a local layout: "
            + autonomyMenu());

        final String complete = I18n.f("layout.ui.infoDownloadCompleteLayoutSavedIn", into.getAbsolutePath());

        try (Answering answering = press(into, message -> Answering.pressAt(0)))
        {
            assertTrue(Components.waitFor(() -> answering.asked().contains(complete), 60000),
                "the download did not say it was complete: " + answering.asked());

            Components.settle();

            assertTrue(!ui.isRemoteLayout(), "the copy is not the layout in use");
            assertFalse(autonomyMenu().contains(needsALocalLayout),
                "after the download the Autonomy menu still says autonomy needs a layout on this computer: "
                + autonomyMenu());
            assertTrue(answering.asked().size() == 2, "the download asked more than its two notices: "
                + answering.asked());
        }
    }

    /** Whether the busy sign is on screen; asked off the event thread, which may be the one held. */
    private static boolean busyShowing()
    {
        for (java.awt.Window window : java.awt.Window.getWindows())
        {
            if (window instanceof BusyDialog && window.isShowing()) return true;
        }

        return false;
    }

    /** Holds the station's page requests from now until `release`. */
    private void holdThePages()
    {
        pageAsked = new CountDownLatch(1);
        pagesHeld = new CountDownLatch(1);
    }

    private void release()
    {
        CountDownLatch held = pagesHeld;

        pagesHeld = null;

        if (held != null) held.countDown();
    }

    /**
     * While the station is slow to send its pages, the window still answers and shows the busy sign, rather than
     * freezing until the last page arrives; and the copy then opens.
     *
     * MUTATION: run `downloadLayout` on the event thread in `LayoutFileActions.downloadAndOpen` (the work half's body
     * moved into `whenDone`), and the window does not answer while the page is held.
     */
    @Test(timeOut = 120000)
    public void testTheWindowAnswersWhileTheStationIsSlow() throws Exception
    {
        final File into = new File(root, "slow");

        assertTrue(into.mkdirs(), "precondition: no folder to download into");

        final String complete = I18n.f("layout.ui.infoDownloadCompleteLayoutSavedIn", into.getAbsolutePath());

        holdThePages();

        try (Answering answering = press(into, message -> Answering.pressAt(0)))
        {
            boolean answered;
            boolean spinner;

            try
            {
                assertTrue(pageAsked.await(30, TimeUnit.SECONDS), "the download never asked the station for a page: "
                    + answering.asked());

                CountDownLatch turn = new CountDownLatch(1);

                SwingUtilities.invokeLater(turn::countDown);

                answered = turn.await(3, TimeUnit.SECONDS);
                spinner = Components.waitFor(testDownloadingTheStationsLayout::busyShowing, 3000);
            }
            finally
            {
                release();
            }

            assertTrue(Components.waitFor(() -> answering.asked().contains(complete), 60000),
                "the download did not say it was complete: " + answering.asked());

            assertTrue(answered, "the window froze while the station took its time sending a page");
            assertTrue(spinner, "nothing showed the download was under way while the station was slow");
            assertTrue(!ui.isRemoteLayout(), "the copy is not the layout in use after a slow download");
        }
        finally
        {
            release();
        }
    }

    /**
     * A second press while a download is under way says one is already running, and starts no second download: no
     * second folder chooser, one copy.
     *
     * MUTATION: drop the `downloading.compareAndSet` refusal from `LayoutFileActions.downloadCSLayoutMenuItemActionPerformed`,
     * and the second press asks for a folder again.
     */
    @Test(timeOut = 120000)
    public void testASecondPressWhileOneRunsIsRefused() throws Exception
    {
        final File into = new File(root, "twice");

        assertTrue(into.mkdirs(), "precondition: no folder to download into");

        final String complete = I18n.f("layout.ui.infoDownloadCompleteLayoutSavedIn", into.getAbsolutePath());
        final String already = I18n.t("layout.ui.infoDownloadAlreadyRunning");
        final String choose = I18n.t("layout.ui.infoChooseFolderForCentralStationDownload");

        holdThePages();

        try (Answering answering = press(into, message -> Answering.pressAt(0)))
        {
            try
            {
                assertTrue(pageAsked.await(30, TimeUnit.SECONDS), "the download never asked the station for a page: "
                    + answering.asked());

                final JMenuItem door = (JMenuItem) Reflect.get(TrainControlUI.class, ui, "downloadCSLayoutMenuItem");

                SwingUtilities.invokeLater(door::doClick);

                Components.waitFor(() -> answering.asked().contains(already), 5000);
            }
            finally
            {
                release();
            }

            assertTrue(Components.waitFor(() -> answering.asked().contains(complete), 60000),
                "the download did not say it was complete: " + answering.asked());

            // Room for a second download to show itself, had one started
            Components.waitFor(() -> answering.asked().indexOf(choose) != answering.asked().lastIndexOf(choose), 2000);

            List<String> asked = answering.asked();

            assertTrue(asked.contains(already), "a second press while a download ran was not told one is under way: "
                + asked);
            assertTrue(asked.indexOf(choose) == asked.lastIndexOf(choose), "a second press while a download ran"
                + " asked for a folder again: " + asked);
        }
        finally
        {
            release();
        }
    }
}
