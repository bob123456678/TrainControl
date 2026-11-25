package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
import static org.testng.Assert.assertEquals;
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

    /** How many page requests from now get a page with a square no parser can place, so the copy will not open. */
    private final java.util.concurrent.atomic.AtomicInteger unreadablePages = new java.util.concurrent.atomic.AtomicInteger();

    /** While set, the page index the station serves in place of its own. */
    private volatile String servedIndex;

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
            String index = servedIndex;

            if (index != null && "/config/gleisbild.cs2".equals(path)) return index.getBytes(StandardCharsets.UTF_8);

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

            byte[] page = cs3.apply(path, n);

            if (page != null && path.startsWith("/config/gleisbilder/") && unreadablePages.getAndDecrement() > 0)
            {
                return (new String(page, java.nio.charset.StandardCharsets.UTF_8) + "element\n .id=0xZZ\n .typ=gerade\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }

            return page;
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

    /** A new folder under the test's own, to download into. */
    private File aFolder(String name)
    {
        File into = new File(root, name);

        assertTrue(into.mkdirs(), "precondition: no folder to download into");

        return into;
    }

    private static String completeFor(File into)
    {
        return I18n.f("layout.ui.infoDownloadCompleteLayoutSavedIn", into.getAbsolutePath());
    }

    /** Waits until `said` or "Download complete" is asked, then a moment more for "Download complete" to follow. */
    private static void settledOn(Answering answering, String said, String complete)
    {
        Components.waitFor(() -> answering.asked().contains(said) || answering.asked().contains(complete), 60000);
        Components.waitFor(() -> answering.asked().contains(complete), 2000);
        Components.settle();
    }

    /** Presses the door, choosing `into` in its folder chooser and answering each question by `rule`. */
    private Answering press(File into, java.util.function.Function<String, Answering.Answer> rule) throws Exception
    {
        Answering answering = Answering.start(rule).choosing(into);

        SwingUtilities.invokeLater(this::pressTheDoor);

        return answering;
    }

    /** The menu item's own handler: the item is grayed in simulation, which no station on the wire means. */
    private void pressTheDoor()
    {
        try
        {
            JMenuItem door = (JMenuItem) Reflect.get(TrainControlUI.class, ui, "downloadCSLayoutMenuItem");

            for (java.awt.event.ActionListener listener : door.getActionListeners())
            {
                listener.actionPerformed(new java.awt.event.ActionEvent(door, java.awt.event.ActionEvent.ACTION_PERFORMED,
                    door.getActionCommand()));
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
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
        final File into = aFolder("slow");
        final String complete = completeFor(into);

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
        final File into = aFolder("twice");
        final String complete = completeFor(into);
        final String already = I18n.t("layout.ui.infoDownloadAlreadyRunning");
        final String choose = I18n.t("layout.ui.infoChooseFolderForCentralStationDownload");

        holdThePages();

        try (Answering answering = press(into, message -> Answering.pressAt(0)))
        {
            try
            {
                assertTrue(pageAsked.await(30, TimeUnit.SECONDS), "the download never asked the station for a page: "
                    + answering.asked());

                SwingUtilities.invokeLater(this::pressTheDoor);

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

    /**
     * A folder that already holds a layout is asked about before anything is fetched or written: Cancel leaves its
     * files and the station's layout in use as they were, and fetches nothing; Replace downloads over it.
     *
     * MUTATION: make `LayoutFileActions.replaceIfAsked` return true at once, and the folder is overwritten unasked.
     */
    @Test(timeOut = 120000)
    public void testAFolderHoldingALayoutIsAskedAboutFirst() throws Exception
    {
        final File into = new File(root, "mine");
        final File index = new File(into, "config/gleisbild.cs2");

        assertTrue(index.getParentFile().mkdirs(), "precondition: no folder to download into");

        final byte[] mine = "[gleisbild]\nversion\n .major=1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Files.write(index.toPath(), mine);

        final String question = I18n.f("layout.ui.confirmReplaceLayoutInFolder", into.getAbsolutePath());
        final String complete = I18n.f("layout.ui.infoDownloadCompleteLayoutSavedIn", into.getAbsolutePath());

        rig.station.forget();

        try (Answering answering = press(into, message -> question.equals(message)
            ? Answering.press(I18n.t("ui.cancel")) : Answering.pressAt(0)))
        {
            settledOn(answering, question, complete);

            assertTrue(answering.asked().contains(question), "a folder holding a layout was not asked about: "
                + answering.asked());
            assertFalse(answering.asked().contains(complete), "Cancel still downloaded: " + answering.asked());
            assertTrue(java.util.Arrays.equals(Files.readAllBytes(index.toPath()), mine),
                "Cancel left the folder's layout changed");
            assertTrue(rig.station.requests("/config/gleisbild.cs2") == 0, "the station's layout was fetched though"
                + " the question was answered Cancel: " + rig.station.log());
            assertTrue(ui.isRemoteLayout(), "Cancel left the station's layout");
        }

        try (Answering answering = press(into, message -> question.equals(message)
            ? Answering.press(I18n.t("layout.ui.replace")) : Answering.pressAt(0)))
        {
            assertTrue(Components.waitFor(() -> answering.asked().contains(complete), 60000),
                "Replace did not download: " + answering.asked());

            assertFalse(java.util.Arrays.equals(Files.readAllBytes(index.toPath()), mine),
                "Replace left the folder's old layout");
            assertTrue(!ui.isRemoteLayout(), "the copy is not the layout in use after Replace");
        }
    }

    /**
     * A copy that downloads but will not open says why and not "Download complete", and leaves the window as it was:
     * on the station's layout, with the source a later switch compares with unchanged.
     *
     * MUTATION: show "Download complete" in `LayoutFileActions.downloadAndOpen` whatever `openLayoutFolder` answers, and
     * it is said after the failure.
     */
    @Test(timeOut = 120000)
    public void testACopyThatWillNotOpenIsNotCalledComplete() throws Exception
    {
        final File into = aFolder("unreadable");
        final String complete = completeFor(into);
        final String wontOpen = I18n.t("layout.ui.errorInvalidPathOrCorruptData");
        final Object sourceWas = Reflect.get(TrainControlUI.class, ui, "railwaySource");

        // Every page of the download; the station's own pages read well again after it
        unreadablePages.set(rig.model.getLayoutList().size());

        try (Answering answering = press(into, message -> Answering.pressAt(0)))
        {
            settledOn(answering, wontOpen, complete);

            assertTrue(answering.asked().contains(wontOpen), "a copy that would not open was not said to: "
                + answering.asked());
            assertFalse(answering.asked().contains(complete), "a copy that would not open was called complete: "
                + answering.asked());
            assertTrue(ui.isRemoteLayout(), "the window left the station's layout for a copy that would not open");
            assertTrue(java.util.Objects.equals(Reflect.get(TrainControlUI.class, ui, "railwaySource"), sourceWas),
                "a copy that would not open became the source a later switch compares with");
        }
        finally
        {
            unreadablePages.set(0);
        }
    }

    /**
     * Download, and Open Layout, from the station's layout with a page window open, of a page index that is empty and
     * of one that is text naming no page: each says the copy or folder will not open and changes nothing
     * (`testRecentLayouts.everythingAbout`).
     *
     * MUTATION: in `CS2File.opensAsALayout`, ask `readNoPage` alone again, and the window leaves the station's layout.
     */
    @Test(timeOut = 240000)
    public void testAnIndexNamingNoPageIsRefusedAndNothingChanges() throws Exception
    {
        final String wontOpen = I18n.t("layout.ui.errorInvalidPathOrCorruptData");

        support.AskingWindows.openThePage(ui);

        for (String index : testRecentLayouts.NO_PAGE_INDEXES)
        {
            final File into = aFolder("index" + index.length());
            final String was = testRecentLayouts.everythingAbout(ui);

            servedIndex = index;

            try (Answering answering = press(into, message -> Answering.pressAt(0)))
            {
                settledOn(answering, wontOpen, completeFor(into));

                assertTrue(answering.asked().contains(wontOpen) && !answering.asked().contains(completeFor(into)),
                    "a download of an index of '" + index + "' was not said to fail: " + answering.asked());
            }
            finally
            {
                servedIndex = null;
            }

            assertEquals(testRecentLayouts.everythingAbout(ui), was, "a download of an index of '" + index
                + "' changed it");

            File folder = testRecentLayouts.withTheIndex(index);

            try
            {
                assertTrue(testRecentLayouts.openLayout(ui, folder).contains(wontOpen), "Open Layout did not refuse"
                    + " an index of '" + index + "'");
                assertEquals(testRecentLayouts.everythingAbout(ui), was, "Open Layout on an index of '" + index
                    + "' changed it");
            }
            finally
            {
                Trees.delete(folder);
            }
        }
    }

    /**
     * Open Layout on a folder that then fails in the sync's read, with a station that answers: the layout in use stays,
     * from the station's layout and from a folder of this computer's, with its sensors' states (FR-245).  The session
     * follows pages read again, so it is left out.
     *
     * MUTATION: in `MarklinControlStation.syncLayoutsFromConfiguredSource`, fall back to the station's pages while a
     * layout is in use, and the sensors only the folder draws come back clear.
     */
    @Test(timeOut = 240000)
    public void testAFolderThatFailsInTheSyncLeavesTheLayoutInUse() throws Exception
    {
        final File local = new File(root, "local");
        final File changing = new File(root, "changing");

        Trees.copyForTheRun(new File("test/test_layout"), local);
        Trees.copyForTheRun(new File("test/test_layout"), changing);

        for (boolean fromLocal : new boolean[] {false, true})
        {
            if (fromLocal) openTheFolder(local);

            final String from = fromLocal ? "from a folder" : "from the station's layout";
            final List<Integer> sensors = everySensorSet();
            final String was = withoutTheSession(testRecentLayouts.everythingAbout(ui));
            final String filesWere = testRecentLayouts.filesIn(local);

            try (AutoCloseable hook = testRecentLayouts.indexGoneWhenTheSyncReads(changing))
            {
                assertEquals(testRecentLayouts.openLayout(ui, changing), testRecentLayouts.REFUSED,
                    "a folder that failed in the sync was not said to fail, " + from);
            }

            Components.waitFor(() -> was.equals(withoutTheSession(testRecentLayouts.everythingAbout(ui))), 30000);
            Components.settle();

            assertEquals(withoutTheSession(testRecentLayouts.everythingAbout(ui)), was, "a folder that failed in the"
                + " sync changed it, " + from);
            assertEquals(testRecentLayouts.filesIn(local), filesWere, "a folder that failed in the sync wrote the folder"
                + " in use, " + from);
            assertSet(sensors, "a folder that failed in the sync cleared the sensor, " + from);

            Files.copy(new File("test/test_layout/config/gleisbild.cs2").toPath(),
                new File(changing, "config/gleisbild.cs2").toPath());
        }
    }

    /**
     * A Sync while the layout folder in use will not read keeps the layout, its preference, pages, page window,
     * autonomy and sensors, says so by the folder's name rather than that everything matches, and still syncs the
     * locomotives; once the folder is back, the next Sync reads it (FR-245).
     *
     * MUTATION: in `MarklinControlStation.syncLayoutsFromConfiguredSource`, fall back to the station's pages while a
     * layout is in use, and the Sync switches to the station's layout.
     */
    @Test(timeOut = 240000)
    public void testASyncWhileTheFolderWillNotReadKeepsTheLayoutAndSaysSo() throws Exception
    {
        final File local = new File(root, "local");
        final File away = new File(root, "away");

        Trees.copyForTheRun(new File("test/test_layout"), local);
        openTheFolder(local);
        support.AskingWindows.openThePage(ui);
        Components.settle();

        final String folder = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        final List<Integer> sensors = everySensorSet();
        final String was = testRecentLayouts.everythingAbout(ui);
        final Object page = rig.model.getLayout("1 - Main");

        assertTrue(was.contains("page windows: 1 - Main") && ui.getAutonomySession() != null,
            "precondition: no page window, or no autonomy configuration loaded: " + was);
        assertTrue(rig.model.deleteLoc("ALCO UP"), "precondition: the station's locomotive is not here");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            assertTrue(local.renameTo(away), "precondition: the folder could not be taken away");
            SwingUtilities.invokeLater(() -> ui.doSync(ui));
            assertTrue(answering.awaitAsked(1, 60000), "the Sync said nothing");
            Components.settle();

            String said = answering.asked().get(0);

            assertTrue(said.contains(I18n.f("layout.ui.errorFolderUnreadLayoutKept", folder)), "the Sync did not say the"
                + " folder could not be read: " + said);
            assertFalse(said.contains(I18n.t("ui.infoSyncNothingDiffers")), "the Sync said everything matches: " + said);
            assertEquals(testRecentLayouts.everythingAbout(ui), was, "the Sync changed the layout in use");
            assertSet(sensors, "the Sync cleared the sensor");
            assertTrue(rig.model.getLocByName("ALCO UP") != null, "the Sync did not sync the locomotives");

            assertTrue(away.renameTo(local), "precondition: the folder could not be put back");
            SwingUtilities.invokeLater(() -> ui.doSync(ui));
            assertTrue(answering.awaitAsked(2, 60000), "the second Sync said nothing");

            assertEquals(answering.asked().get(1), I18n.t("ui.infoSyncNothingDiffers"), "the folder back, the Sync says");
            assertTrue(rig.model.getLayout("1 - Main") != page, "the folder back, the Sync did not read it");
        }
    }

    /** Layouts &gt; Open Layout's own method on `folder`, once its pages are in. */
    private void openTheFolder(File folder) throws Exception
    {
        java.lang.reflect.Method open = Reflect.method(TrainControlUI.class, "openLayoutFolder", File.class);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                open.invoke(ui, folder);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        assertTrue(Components.waitFor(() -> rig.model.getLayoutList().contains("1 - Main"), 60000),
            "precondition: the folder of this computer's did not open");

        Components.settle();
    }

    /** Sets every sensor the model knows, and gives back their addresses. */
    private List<Integer> everySensorSet()
    {
        List<Integer> sensors = new ArrayList<>(rig.model.knownSensors());

        for (Integer id : sensors) rig.model.setFeedbackState(String.valueOf(id), true);

        return sensors;
    }

    private void assertSet(List<Integer> sensors, String failure)
    {
        for (Integer id : sensors)
        {
            assertTrue(rig.model.knownSensors().contains(id) && rig.model.getFeedbackState(String.valueOf(id)),
                failure + " " + id);
        }
    }

    private static String withoutTheSession(String about)
    {
        return about.replaceFirst("\nsession: .*$", "");
    }
}
