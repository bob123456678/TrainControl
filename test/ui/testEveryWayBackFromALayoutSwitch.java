package ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.gui.LayoutPopupUI;
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
 * MT-996's layout switches, each followed by every other way to another layout, in a window started on a stand-in
 * CS3's layout: the layout chosen is the one shown, and no page window of the layout left stays open.  A and B are two
 * copies of the test layout, so their pages share names, the case where a stale page window would find a page.
 */
public class testEveryWayBackFromALayoutSwitch
{
    private File root;
    private StationRig rig;
    private TrainControlUI ui;
    private File a;
    private File b;
    private int made;
    private String recentWas;
    private String lastLocalWas;

    @BeforeMethod
    public void openOnTheStation() throws Exception
    {
        support.Display.needed("the doors are menu items of the main window");

        recentWas = TrainControlUI.getPrefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, null);
        lastLocalWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, null);
        root = Files.createTempDirectory("tc-way-back").toFile();
        a = copyOfTheTestLayout(new File(root, "A"));
        b = copyOfTheTestLayout(new File(root, "B"));

        rig = new StationRig(root, StandInStation.cs3("260", null), true, true);
        ui = (TrainControlUI) rig.model.getGUI();

        assertTrue(Components.waitFor(() -> !rig.model.getLayoutList().isEmpty(), 30000),
            "precondition: the window did not read the station's pages");

        TrainControlUI.getPrefs().put(TrainControlUI.RECENT_LAYOUTS_PREF, a.getPath() + "\n" + b.getPath());

        Components.settle();
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

                SwingUtilities.invokeAndWait(() ->
                {
                    for (java.awt.Window w : java.awt.Window.getWindows()) if (w instanceof LayoutPopupUI) w.dispose();

                    closing.dispose();
                });
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

    /**
     * With two page windows open before every switch, each switch of MT-996 (Open Layout, Recent Layouts, Create New
     * Layout, Switch to Central Station Layout, Download) is followed by each way back (Recent Layouts, Open Layout,
     * Create New Layout, Switch to Central Station Layout), and every one lands on the layout chosen.
     *
     * MUTATION: leave out the `initializeTrackDiagram(true)` of `TrainControlUI.openLayoutFolder`'s event-thread half,
     * and the window keeps the station's page box after Open Layout, so the doors that follow fail.
     */
    @Test(timeOut = 360000)
    public void testEverySwitchWithPageWindowsOpenLeavesEveryWayBack() throws Exception
    {
        everySwitchThenEveryWayBack(true);
    }

    /**
     * The same with no page window open.
     *
     * MUTATION: as above.
     */
    @Test(timeOut = 360000)
    public void testEverySwitchLeavesEveryWayBack() throws Exception
    {
        everySwitchThenEveryWayBack(false);
    }

    /**
     * Opening a layout while a sync runs is refused on screen, at Recent Layouts, at Create New Layout and at Switch to Central Station Layout, and the
     * layout open stays the one open: the sync it needs would be turned away, reading nothing, while the window took
     * the new folder for the layout and logged it loaded (MT-996).  The log says which folder Recent Layouts was asked
     * for, before any gate, so a choice that seems to do nothing shows whether it arrived.
     *
     * MUTATION: leave out the `refuseWhileASyncRuns()` of `TrainControlUI.openLayoutFolder`, and Recent Layouts says
     * nothing and records B as the layout open over A's pages; leave it out of `createAndApplyEmptyLayout`, and Create
     * New Layout does the same; leave out the log line of `LayoutMenus.openRecentLayout`, and the log does not name B; leave it out
     * of `switchCSLayoutMenuItemActionPerformed`, and Switch to Central Station Layout clears the pages and says nothing.
     */
    @Test(timeOut = 120000)
    public void testOpeningALayoutWhileASyncRunsSaysSoAndChangesNothing() throws Exception
    {
        landed(Door.RECENT.go(this, a), "setting up on A");

        AtomicBoolean syncing = (AtomicBoolean) Reflect.get(TrainControlUI.class, ui, "syncInFlight");
        String running = I18n.t("ui.infoSyncAlreadyRunning");

        syncing.set(true);
        rig.takeLog();

        try
        {
            for (Door door : new Door[] {Door.RECENT, Door.CREATE, Door.STATION})
            {
                File target = door.target(this, b);

                try (Answering answering = Answering.start(message -> Answering.pressAt(0)).choosing(target))
                {
                    door.press(this, target);

                    assertTrue(answering.awaitAsked(door == Door.CREATE ? 2 : 1, 15000)
                        && answering.asked().contains(running), door + " did not say a sync is running: "
                        + answering.asked());

                    Components.settle();
                }

                assertEquals(String.valueOf(ui.localLayoutPath()), a.getPath(),
                    door + " while a sync runs took another folder for the layout open");

                if (door == Door.RECENT)
                {
                    String asked = I18n.f("ui.main.toolbar.infoOpeningRecentLayout", b.getPath());
                    List<String> log = rig.takeLog();

                    assertTrue(log.stream().anyMatch(line -> line.endsWith(asked)), "the log does not say " + asked
                        + ": " + log);
                }
            }
        }
        finally
        {
            syncing.set(false);
        }

        landed(Door.RECENT.go(this, b), "Recent Layouts once the sync had ended");
    }

    /**
     * After Recent Layouts says a folder is gone, each other folder it lists still opens from it (MT-996): Adam chose a
     * folder that had been removed, was told so, and from then on nothing happened when he chose another.
     *
     * MUTATION: take the `!` off the `equals` in `LayoutMenus.forgetRecentLayout`, so it keeps the gone folder alone,
     * and Recent Layouts no longer shows B.
     */
    @Test(timeOut = 120000)
    public void testAfterAGoneFolderTheOthersStillOpen() throws Exception
    {
        landed(Door.RECENT.go(this, a), "setting up on A");

        File gone = new File(root, "gone");

        TrainControlUI.getPrefs().put(TrainControlUI.RECENT_LAYOUTS_PREF,
            a.getPath() + "\n" + gone.getPath() + "\n" + b.getPath());

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            chooseFromRecentLayouts(gone);

            assertTrue(answering.awaitAsked(1, 15000), "choosing a folder that is gone said nothing");
            assertEquals(answering.asked().get(0), I18n.f("ui.main.toolbar.errorRecentLayoutGone", gone.getPath()));

            Components.settle();
        }

        landed(Door.RECENT.go(this, b), "Recent Layouts after a gone folder was said");
        landed(Door.RECENT.go(this, a), "Recent Layouts a second time after a gone folder was said");
    }

    private void everySwitchThenEveryWayBack(boolean withWindows) throws Exception
    {
        List<String> wrong = new ArrayList<>();

        for (Door first : Door.values())
        {
            for (Door back : new Door[] {Door.RECENT, Door.OPEN, Door.CREATE, Door.STATION})
            {
                if (first == Door.STATION && back == Door.STATION) continue;

                // Download needs the station's layout, every other switch a local one
                if (first == Door.DOWNLOAD && !ui.isRemoteLayout())
                {
                    landed(Door.STATION.go(this, null), "setting up on the station's layout");
                }
                else if (first != Door.DOWNLOAD && !a.getPath().equals(ui.localLayoutPath()))
                {
                    landed(Door.OPEN.go(this, a), "setting up on A");
                }

                if (withWindows) twoPageWindows();
                String one = Door.check(this, first, first.go(this, other()));

                if (withWindows) twoPageWindows();
                String two = Door.check(this, back, back.go(this, other()));

                if (one != null || two != null) wrong.add(first + " then " + back + ": " + one + " / " + two);
            }
        }

        assertEquals(wrong, new ArrayList<String>(), "a way back did not land on the layout chosen");
    }

    /** A, unless A is open; then B. */
    private File other()
    {
        return a.getPath().equals(ui.localLayoutPath()) ? b : a;
    }

    private static void landed(String wrong, String what)
    {
        assertEquals(wrong, null, what);
    }

    /** The doors, as the user presses them: each its menu item's own handler, its questions answered. */
    private enum Door
    {
        OPEN("chooseLocalDataFolderMenuItem"), RECENT(null), CREATE("initializeLocalLayoutMenuItem"),
        STATION("switchCSLayoutMenuItem"), DOWNLOAD("downloadCSLayoutMenuItem");

        private final String item;

        Door(String item)
        {
            this.item = item;
        }

        /** The folder a press toward `asked` opens: a new one for Create New Layout and Download, none for the station. */
        File target(testEveryWayBackFromALayoutSwitch t, File asked)
        {
            if (this == STATION) return null;

            return this == CREATE || this == DOWNLOAD ? new File(t.root, name() + (++t.made)) : asked;
        }

        /** Presses the door toward `asked` and waits for it to settle; null when it landed, else what it left. */
        String go(testEveryWayBackFromALayoutSwitch t, File asked) throws Exception
        {
            File folder = target(t, asked);

            if (this == DOWNLOAD) folder.mkdirs();

            try (Answering answering = Answering.start(message -> Answering.pressAt(0)).choosing(folder))
            {
                press(t, folder);

                String where = folder == null ? "" : folder.getPath();

                boolean switched = Components.waitFor(() -> where.equals(t.ui.isRemoteLayout() ? ""
                    : String.valueOf(t.ui.localLayoutPath())) && !t.rig.model.getLayoutList().isEmpty(), 20000);

                Components.waitFor(testEveryWayBackFromALayoutSwitch::loading, 2000);
                Components.waitFor(() -> !loading(), 60000);
                Components.settle();

                return switched ? null : "not on " + (folder == null ? "the station" : folder.getName()) + " but "
                    + t.state() + ", asked " + answering.asked();
            }
        }

        /** What the switch left wrong besides where it went: a page box of the layout left, or its page windows. */
        static String check(testEveryWayBackFromALayoutSwitch t, Door door, String went)
        {
            if (went != null) return went;

            List<String> listed = t.listed();
            List<String> pages = t.rig.model.getLayoutList();

            if (!listed.equals(pages)) return door + " lists " + listed + " for the pages " + pages;

            return t.windows().isEmpty() ? null : door + " left the page windows " + t.windows();
        }

        void press(testEveryWayBackFromALayoutSwitch t, File folder)
        {
            if (this == RECENT)
            {
                t.chooseFromRecentLayouts(folder);
            }
            else
            {
                SwingUtilities.invokeLater(() -> t.fire(this.item));
            }
        }
    }

    /**
     * Chooses the folder from Layouts &gt; Recent Layouts with the mouse, as a person does: a click on Layouts, the
     * pointer onto Recent Layouts until its submenu shows, then a click on the item showing in it.
     */
    private void chooseFromRecentLayouts(File folder)
    {
        final JMenu layouts = (JMenu) Components.withText(ui, I18n.t("ui.main.toolbar.layouts"));

        support.Mouse.click(layouts);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(layouts::isPopupMenuVisible), 5000),
            "a click on Layouts did not open it");

        final JMenu recent = Components.onTheEventThread(() ->
        {
            for (java.awt.Component each : layouts.getMenuComponents())
            {
                if (each instanceof JMenu && I18n.t("ui.main.toolbar.recentLayouts").equals(((JMenu) each).getText()))
                    return (JMenu) each;
            }

            return null;
        });

        assertTrue(recent != null, "the Layouts menu on screen has no Recent Layouts");

        support.Mouse.enter(recent);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(recent::isPopupMenuVisible), 5000),
            "pointing at Recent Layouts did not open it");

        JMenuItem item = Components.onTheEventThread(() ->
        {
            for (java.awt.Component each : recent.getPopupMenu().getComponents())
            {
                if (each instanceof JMenuItem && each.isShowing() && ((JMenuItem) each).getToolTipText() != null
                    && ((JMenuItem) each).getToolTipText().replaceAll("<[^>]*>", "").trim().equals(folder.getPath()))
                    return (JMenuItem) each;
            }

            return null;
        });

        assertTrue(item != null, "Recent Layouts does not show " + folder);

        support.Mouse.click(item);
    }

    /** S then M on the page shown, as the user opens two page windows. */
    private void twoPageWindows() throws Exception
    {
        support.AskingWindows.openThePage(ui);

        Components.press((AbstractButton) Reflect.get(TrainControlUI.class, ui, "mediumButton"));

        assertTrue(Components.waitFor(() -> windows().size() == 2, 10000), "two page windows did not open: " + windows());
    }

    private List<String> windows()
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

    /** The page box's items. */
    private List<String> listed()
    {
        return Components.onTheEventThread(() ->
        {
            List<String> names = new ArrayList<>();
            javax.swing.JComboBox<?> box = (javax.swing.JComboBox<?>) Reflect.get(TrainControlUI.class, ui, "LayoutList");

            for (int i = 0; i < box.getItemCount(); i++) names.add(String.valueOf(box.getItemAt(i)));

            return names;
        });
    }

    private String state()
    {
        return "folder " + ui.localLayoutPath() + ", pages " + rig.model.getLayoutList() + ", page windows " + windows();
    }

    /** The menu item's own handler: the station's items are grayed in simulation. */
    private void fire(String field)
    {
        JMenuItem door;

        try
        {
            door = (JMenuItem) Reflect.get(TrainControlUI.class, ui, field);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException(e);
        }

        for (java.awt.event.ActionListener listener : door.getActionListeners())
        {
            listener.actionPerformed(new java.awt.event.ActionEvent(door, java.awt.event.ActionEvent.ACTION_PERFORMED,
                door.getActionCommand()));
        }
    }

    private static boolean loading()
    {
        for (java.awt.Window w : java.awt.Window.getWindows())
        {
            if (w instanceof BusyDialog && w.isShowing()) return true;
        }

        return false;
    }

    private static File copyOfTheTestLayout(File copy) throws java.io.IOException
    {
        Path from = new File("test/test_layout").toPath();

        try (java.util.stream.Stream<Path> walk = Files.walk(from))
        {
            for (Path p : (Iterable<Path>) walk::iterator)
            {
                Path to = copy.toPath().resolve(from.relativize(p).toString());

                if (Files.isDirectory(p)) Files.createDirectories(to);
                else Files.copy(p, to);
            }
        }

        return copy;
    }

    private static void putBack(String key, String was)
    {
        if (was == null) TrainControlUI.getPrefs().remove(key);
        else TrainControlUI.getPrefs().put(key, was);
    }
}
