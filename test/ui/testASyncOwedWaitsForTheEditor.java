package ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Accessory;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.util.I18n;
import support.AskingWindows;
import support.Answering;
import support.Components;
import support.Reflect;
import support.StandInStation;
import support.TestStationAddress;

/**
 * A Central Station sync a door runs after its own edit, made while an editor is open, waits for the editor to close and
 * then runs once, saying nothing; a Sync the user presses meanwhile is refused aloud and not run later.  The syncs are
 * counted at a stand-in station, one device-file request each.
 *
 * @author Adam
 */
public class testASyncOwedWaitsForTheEditor
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static AutonomySession session;
    private static org.json.JSONObject asOpened;
    private static StandInStation station;
    private static String addressWas;

    private static final String PAGE = "1 - Main";
    private static final String PREFIX = "owed sync ";

    /** The first file every sync asks for. */
    private static final String DEVICE = "/config/geraet.vrs";

    /** Long enough for a sync the door started to reach the station, which on loopback takes milliseconds. */
    private static final long QUIET_MS = 2500;

    /** While set, the stand-in holds every answer until it opens: a sync then stays in flight. */
    private static volatile java.util.concurrent.CountDownLatch holding;

    private LayoutEditor editor;

    private final List<String> made = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        // A Central Station 2 with no routes file: every sync asks for the device file, then fails before importing
        station = new StandInStation((path, n) ->
        {
            final java.util.concurrent.CountDownLatch held = holding;

            try
            {
                if (held != null) held.await(30, java.util.concurrent.TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }

            return DEVICE.equals(path)
                ? "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(StandardCharsets.UTF_8) : null;
        });

        addressWas = TestStationAddress.get();

        TestStationAddress.set(station.address());

        Components.settle();

        session = ui.getAutonomySession();

        assertNotNull(session, "no autonomy setup on this railway");

        asOpened = Components.onTheEventThread(session::snapshotSetup);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        TestStationAddress.set(addressWas);

        try
        {
            if (station != null) station.close();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @BeforeMethod
    public void putTheSetupBack() throws Exception
    {
        support.FrozenEditor.putBack(ui, session, asOpened);

        station.forget();
    }

    @AfterMethod(alwaysRun = true)
    public void closeWhatIsLeft() throws Exception
    {
        support.FrozenEditor.dispose(editor);

        editor = null;

        AskingWindows.closeEveryDialog();

        // A sync owed by a red run would otherwise reach the next claim
        Components.waitFor(() -> false, QUIET_MS);

        for (String name : made) if (model.getRoute(name) != null) model.deleteRoute(name);

        made.clear();

        Components.onTheEventThread(() -> { ui.refreshRouteList(); return null; });
    }

    /** An armed route the station carried (locked), so disarming it syncs; listed in the window. */
    private String aStationRoute(String suffix, int sensor, int address) throws Exception
    {
        final String name = PREFIX + suffix;

        model.receiveMessage(support.Packets.s88(sensor, false));

        assertTrue(Components.waitFor(() -> model.isFeedbackSet(Integer.toString(sensor)), 5000),
            "precondition: sensor " + sensor + " is not known");

        if (model.getRoute(name) != null) model.deleteRoute(name);

        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(address, Accessory.accessoryDecoderType.MM2, true));

        assertTrue(model.newRoute(name, commands, sensor, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, true, null),
            "precondition: the route " + name + " was not made");

        made.add(name);

        ((MarklinRoute) model.getRoute(name)).setLocked(true);

        Components.onTheEventThread(() -> { ui.refreshRouteList(); return null; });

        Components.settle();

        return name;
    }

    /** Disable Auto Execution from the route's right-click menu, waited out until the route is disarmed. */
    private void disarm(String route) throws Exception
    {
        AbstractButton disable = Components.withText(support.RouteListDoors.rightClick(ui, route),
            I18n.t("route.ui.menuDisableAutoExecution"));

        assertNotNull(disable, "precondition: the armed route's menu has no Disable Auto Execution");

        SwingUtilities.invokeLater(disable::doClick);

        assertTrue(Components.waitFor(() -> model.getRoute(route) != null && !model.getRoute(route).isEnabled(), 10000),
            "Disable Auto Execution with the editor open did not disarm " + route);

        support.Mouse.closeMenus();
    }

    private void openTheEditor()
    {
        editor = support.FrozenEditor.editorOn(model, ui, session, PAGE);
    }

    /** How the editor is closed. */
    private enum Closing { SAVE, CANCEL, CLOSE_BOX }

    /** Closes the editor that way, answering Yes to anything asked, and waits until it has gone. */
    private void close(Closing how) throws Exception
    {
        final LayoutEditor closing = editor;

        try (Answering answering = Answering.start(message -> Answering.press(AskingWindows.YES)))
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    if (how == Closing.SAVE)
                    {
                        Components.withText(closing, I18n.t("layout.ui.saveChanges")).doClick();
                    }
                    else if (how == Closing.CANCEL)
                    {
                        Reflect.method(LayoutEditor.class, "confirmExit").invoke(closing);
                    }
                    else
                    {
                        closing.dispatchEvent(new java.awt.event.WindowEvent(closing,
                            java.awt.event.WindowEvent.WINDOW_CLOSING));
                    }
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertTrue(Components.waitFor(() -> !closing.isDisplayable(), 20000), how + " did not close the editor");
        }

        editor = null;
    }

    /** Syncs the station has seen since the claim began. */
    private static int syncs()
    {
        return station.requests(DEVICE);
    }

    /**
     * Disarming a station's route with the editor open is written at once; its sync does not run while the editor is
     * open, says nothing, and runs exactly once after the editor closes.
     */
    private void anEditsSyncWaitsFor(Closing how) throws Exception
    {
        final String route = aStationRoute(how.name().toLowerCase(), 4781, 241);

        openTheEditor();

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            disarm(route);

            assertFalse(Components.waitFor(() -> syncs() > 0, QUIET_MS), "the disarm synced with the editor open: "
                + station.log());

            close(how);

            assertTrue(Components.waitFor(() -> syncs() > 0, 20000), "no sync ran after " + how
                + " closed the editor, which the disarm owed; said " + answering.asked());

            Components.waitFor(() -> false, QUIET_MS);

            assertEquals(syncs(), 1, "the syncs after " + how + ": " + station.log());
            assertEquals(answering.asked(), new ArrayList<String>(), "the disarm or its sync said something");
        }
    }

    /**
     * Save: the disarm made with the editor open syncs once Save has closed it, not before.
     *
     * MUTATION: in `TrainControlUI.editorGone`, leave the posted `doSync(null)` out, and no sync runs after Save.
     */
    @Test(timeOut = 120000)
    public void testAnEditsSyncRunsOnceAfterSave() throws Exception
    {
        anEditsSyncWaitsFor(Closing.SAVE);
    }

    /**
     * Cancel: the same.
     *
     * MUTATION: in `TrainControlUI.runTheOwedSync`, drop `anEditorIsOpenOrOnItsWay() ||`, and the disarm syncs with the
     * editor open.
     */
    @Test(timeOut = 120000)
    public void testAnEditsSyncRunsOnceAfterCancel() throws Exception
    {
        anEditsSyncWaitsFor(Closing.CANCEL);
    }

    /**
     * The window's close box: the same.
     *
     * MUTATION: in `EditorWindows.editorWindowClosed`, leave out `ui.editorGone()`, and no sync runs after the close box.
     */
    @Test(timeOut = 120000)
    public void testAnEditsSyncRunsOnceAfterTheCloseBox() throws Exception
    {
        anEditsSyncWaitsFor(Closing.CLOSE_BOX);
    }

    /**
     * Two disarms with the editor open owe one sync: one runs after Cancel, not two, and the next editor closing runs
     * none.
     *
     * MUTATION: in `TrainControlUI.syncNow`, leave out `syncOwed.set(false)`, so the debt is never cleared, and the next
     * Cancel syncs again.
     */
    @Test(timeOut = 120000)
    public void testTwoEditsOweOneSync() throws Exception
    {
        final String first = aStationRoute("first of two", 4782, 242);
        final String second = aStationRoute("second of two", 4783, 243);

        openTheEditor();

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            disarm(first);
            disarm(second);

            assertFalse(Components.waitFor(() -> syncs() > 0, QUIET_MS), "a disarm synced with the editor open: "
                + station.log());

            close(Closing.CANCEL);

            assertTrue(Components.waitFor(() -> syncs() > 0, 20000), "no sync ran after Cancel; said " + answering.asked());

            Components.waitFor(() -> false, QUIET_MS);

            assertEquals(syncs(), 1, "two disarms with the editor open made these syncs after Cancel: " + station.log());

            // Paid once: the next editor closing, with nothing owed, runs none
            openTheEditor();
            close(Closing.CANCEL);

            assertFalse(Components.waitFor(() -> syncs() > 1, QUIET_MS), "the sync paid at the first Cancel ran again at"
                + " the next: " + station.log());
        }
    }

    /**
     * The main window's Sync, pressed with the editor open, is refused with the message and is not run when the editor
     * closes.
     *
     * MUTATION: in `TrainControlUI.doSync`, owe the sync when it refuses (`syncOwed.set(true)` before its `return`), and
     * a sync runs after Cancel.
     */
    @Test(timeOut = 120000)
    public void testAPressedSyncIsRefusedAndNotRunAtClose() throws Exception
    {
        openTheEditor();

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            SwingUtilities.invokeLater(() -> ui.doSync(ui));

            assertTrue(answering.awaitAsked(1, 8000), "the Sync pressed with the editor open said nothing");

            assertEquals(answering.asked().get(0), I18n.t("loc.ui.errorEditorOpenNoLinkOrSync"),
                "the Sync pressed with the editor open was not refused for it");

            close(Closing.CANCEL);

            assertFalse(Components.waitFor(() -> syncs() > 0, QUIET_MS * 2), "the refused Sync ran after Cancel: "
                + station.log());
        }
    }

    /**
     * An edit's sync asked for while another sync is running, which may have read the station before the edit, is kept
     * and runs once that sync ends, quietly: a sync that started after the edit is what pays for it.
     *
     * MUTATION: in `TrainControlUI.syncNow`'s `finally`, leave out `if (syncOwed.get()) editorGone();`, and no second
     * sync runs.
     */
    @Test(timeOut = 120000)
    public void testAnEditsSyncIsKeptPastASyncAlreadyRunning() throws Exception
    {
        holding = new java.util.concurrent.CountDownLatch(1);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            // A sync from File > Sync's door, held at the station's first file
            new Thread(() -> ui.syncWithCS2(), "the sync already running").start();

            assertTrue(Components.waitFor(() -> syncs() == 1, 20000), "precondition: the first sync never reached the"
                + " station: " + station.log());

            // The edit's door, off the event thread as Locomotive Settings' reset is
            Thread door = new Thread(ui::syncAfterAnEdit, "the edit's door");

            door.start();
            door.join(20000);

            holding.countDown();

            assertTrue(Components.waitFor(() -> syncs() > 1, 30000), "the edit's sync was spent on the sync already"
                + " running, which started before the edit; syncs: " + station.log() + ", said " + answering.asked());

            Components.waitFor(() -> false, QUIET_MS);

            assertEquals(syncs(), 2, "the syncs: " + station.log());
            assertEquals(answering.asked(), new ArrayList<String>(), "the owed sync said something");
        }
        finally
        {
            holding.countDown();
            holding = null;
        }
    }

    private static LayoutEditor editorShowing()
    {
        for (java.awt.Frame frame : java.awt.Frame.getFrames())
        {
            if (frame instanceof LayoutEditor && frame.isDisplayable() && frame.isShowing()) return (LayoutEditor) frame;
        }

        return null;
    }

    /**
     * A layout-switch door's gate, asked in the same event as Edit, while the editor is on its way and not yet built,
     * refuses.
     *
     * MUTATION: in `RunGates.refuseWhileEditorOpen`, ask `ui.isLayoutEditorOpen()` again, and this fails.
     */
    @Test(timeOut = 120000)
    public void testASwitchDoorIsRefusedWhileAnEditorIsOnItsWay() throws Exception
    {
        final boolean[] out = new boolean[3];
        final Exception[] failed = new Exception[1];

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    ui.openLayoutEditor(PAGE, Boolean.TRUE, null);

                    out[0] = (Boolean) Reflect.method(TrainControlUI.class, "anEditorIsOpenOrOnItsWay").invoke(ui);
                    out[1] = ui.isLayoutEditorOpen();
                    out[2] = (Boolean) Reflect.method(TrainControlUI.class, "refuseWhileEditorOpen").invoke(ui);
                }
                catch (Exception e)
                {
                    failed[0] = e;
                }
            });

            if (failed[0] != null) throw failed[0];

            assertTrue(Components.waitFor(() -> editorShowing() != null, 20000), "precondition: the editor never came");

            editor = editorShowing();

            assertTrue(out[0], "precondition: no editor was on its way after Edit");
            assertFalse(out[1], "precondition: the editor was already open in the event that asked for it");
            assertTrue(out[2], "refuseWhileEditorOpen let a layout switch through with an editor on its way; said "
                + answering.asked());
        }
    }

    /**
     * The flag sync threads read to decide whether a sync runs is volatile.
     *
     * MUTATION: drop `volatile` from `TrainControlUI.editorOnItsWay`, and this fails.
     */
    @Test
    public void testTheOnItsWayFlagIsVolatile() throws Exception
    {
        assertTrue(java.lang.reflect.Modifier.isVolatile(TrainControlUI.class.getDeclaredField("editorOnItsWay")
            .getModifiers()), "editorOnItsWay is read by sync threads and is not volatile");
    }

    /**
     * A sync owed that a run refuses (the rename-from-station door's tail) is not owed any more: stopping the run and
     * opening and canceling an editor starts no sync.
     *
     * MUTATION: in `doSync`, leave the `syncOwed.set(false)` out of the autonomy refusal, and a sync follows the close.
     */
    @Test(timeOut = 180000)
    public void testASyncARunRefusedIsNotOwedToTheNextEditor() throws Exception
    {
        final java.util.concurrent.atomic.AtomicBoolean owed = (java.util.concurrent.atomic.AtomicBoolean)
            Reflect.get(TrainControlUI.class, ui, "syncOwed");
        final java.lang.reflect.Field running = Reflect.field(org.traincontrol.automation.Layout.class, "running");

        running.setBoolean(model.getAutoLayout(), true);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            owed.set(true);

            SwingUtilities.invokeLater(() -> ui.doSync(null));

            Components.waitFor(() -> !owed.get(), 20000);
            Components.settle();

            running.setBoolean(model.getAutoLayout(), false);

            assertFalse(owed.get(), "the refused sync is still owed");

            openTheEditor();

            close(Closing.CANCEL);

            assertFalse(Components.waitFor(() -> syncs() > 0, QUIET_MS),
                "a sync a run refused ran at the next editor's close: " + station.log());
        }
        finally
        {
            running.setBoolean(model.getAutoLayout(), false);
        }
    }

    /**
     * A sync owed that a run refuses shows no dialog, since nobody pressed Sync and no locomotive is being edited, and
     * the log says it was left for the next Sync.
     *
     * MUTATION: in `doSync`, refuse the owed sync through `refuseWhileAutonomyRunning(c)` as a pressed Sync is, and the
     * "cannot edit locomotives" message shows.
     */
    @Test(timeOut = 120000)
    public void testAnOwedSyncARunRefusesIsLoggedNotShown() throws Exception
    {
        final java.util.concurrent.atomic.AtomicBoolean owed = (java.util.concurrent.atomic.AtomicBoolean)
            Reflect.get(TrainControlUI.class, ui, "syncOwed");
        final java.lang.reflect.Field running = Reflect.field(org.traincontrol.automation.Layout.class, "running");
        final String left = I18n.t("ui.infoOwedSyncLeftWhileRunning");

        running.setBoolean(model.getAutoLayout(), true);

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            owed.set(true);

            SwingUtilities.invokeLater(() -> ui.doSync(null));

            assertTrue(Components.waitFor(() -> logText().contains(left), 20000), "the log does not say the owed sync was"
                + " left for the next Sync; said " + answering.asked());

            Components.settle();

            assertEquals(answering.asked(), new ArrayList<String>(), "the run's refusal of an owed sync showed a dialog");
            assertEquals(syncs(), 0, "the refused sync ran: " + station.log());
        }
        finally
        {
            running.setBoolean(model.getAutoLayout(), false);
        }
    }

    /** The main window's log as it shows. */
    private static String logText()
    {
        return Components.onTheEventThread(() -> (String) Reflect.method(TrainControlUI.class, "logText").invoke(ui));
    }

    /**
     * Presses a layout-switch door and, in the same event, puts an editor on its way, as an Edit queued behind the
     * click does; lets the door's posted work run, and gives what was said.
     */
    private List<String> pressedWithAnEditorOnItsWay(String door) throws Exception
    {
        final javax.swing.JMenuItem item = (javax.swing.JMenuItem) Reflect.get(TrainControlUI.class, ui, door);
        final boolean enabled = Components.onTheEventThread(item::isEnabled);
        final java.lang.reflect.Field onItsWay = Reflect.field(TrainControlUI.class, "editorOnItsWay");

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.onTheEventThread(() ->
            {
                item.setEnabled(true);
                item.doClick();

                onItsWay.setBoolean(ui, true);

                return null;
            });

            answering.awaitAsked(1, 20000);
            answering.awaitAsked(2, 2000);
            Components.settle();

            return answering.asked();
        }
        finally
        {
            Reflect.set(TrainControlUI.class, ui, "editorOnItsWay", false);

            SwingUtilities.invokeAndWait(() -> item.setEnabled(enabled));
        }
    }

    /**
     * Switch to Central Station Layout with an editor on its way by the time its posted work runs is refused there,
     * with the editor's message alone: the folder and the pages are kept.
     *
     * MUTATION: leave the `refuseWhileEditorOpen()` out of the task `switchCSLayoutMenuItemActionPerformed` posts, and
     * the folder is cleared and the sync refused.
     */
    @Test(timeOut = 120000)
    public void testTheSwitchToTheStationsLayoutAsksAgainInItsPostedWork() throws Exception
    {
        final String folder = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
        final int pages = model.getLayoutList().size();

        assertFalse(folder.isEmpty() || pages == 0, "precondition: no local layout is loaded");

        try
        {
            List<String> said = pressedWithAnEditorOnItsWay("switchCSLayoutMenuItem");

            assertEquals(said, java.util.Collections.singletonList(I18n.t("autosetup.ui.menuEditorOpen")),
                "the switch's posted work went on under an editor on its way");
            assertEquals(model.getLayoutList().size(), pages, "the switch cleared the pages");
            assertEquals(TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""), folder,
                "the switch cleared the layout folder");
        }
        finally
        {
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);
        }
    }

    /**
     * Create New Layout: the same, before its message and folder chooser.
     *
     * MUTATION: leave the `refuseWhileEditorOpen()` out of the task
     * `LayoutFileActions.initializeLocalLayoutMenuItemActionPerformed` posts, and its message shows.
     */
    @Test(timeOut = 120000)
    public void testCreateNewLayoutAsksAgainInItsPostedWork() throws Exception
    {
        assertEquals(pressedWithAnEditorOnItsWay("initializeLocalLayoutMenuItem"),
            java.util.Collections.singletonList(I18n.t("autosetup.ui.menuEditorOpen")),
            "Create New Layout's posted work went on under an editor on its way");
    }

    /**
     * Download Central Station Layout, from a station's layout (no folder): the same, before its message and chooser.
     *
     * MUTATION: leave the `refuseWhileEditorOpen()` out of the task `LayoutFileActions.downloadCSLayoutMenuItemActionPerformed`
     * posts, and its message shows.
     */
    @Test(timeOut = 120000)
    public void testDownloadAsksAgainInItsPostedWork() throws Exception
    {
        final String folder = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        // The door downloads only from a station's layout: no folder, pages held
        TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        try
        {
            assertEquals(pressedWithAnEditorOnItsWay("downloadCSLayoutMenuItem"),
                java.util.Collections.singletonList(I18n.t("autosetup.ui.menuEditorOpen")),
                "Download's posted work went on under an editor on its way");
        }
        finally
        {
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, folder);
        }
    }

    /**
     * The locomotive CSV import that would change an address is refused with an editor open, with the editor's message,
     * and nothing is written.
     *
     * MUTATION: leave the two `refuseALinkOrSyncWhileEditorOpen` asks out of `LocomotiveCsvDoor`, and the address changes.
     */
    @Test(timeOut = 180000)
    public void testTheCsvImportDoesNotReaddressUnderAnEditor() throws Exception
    {
        final String name = "owed sync csv loco";
        final int was = model.newMM2Locomotive(name, 20).getAddress();
        final java.io.File file = java.io.File.createTempFile("tc-owed", ".csv");

        java.nio.file.Files.write(file.toPath(), ("Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,"
            + "Start Year,End Year,RailwayName,Notes\n" + name + ",,DCC,21,0,0:00:00,0,0,,\n")
            .getBytes(StandardCharsets.UTF_8));

        try
        {
            openTheEditor();

            try (Answering answering = Answering.start(message -> Answering.pressAt(0)).choosing(file))
            {
                final javax.swing.JMenuItem door = (javax.swing.JMenuItem) Reflect.get(TrainControlUI.class, ui,
                    "importLocsFromCSVMenuItem");

                SwingUtilities.invokeLater(door::doClick);

                assertTrue(answering.awaitAsked(1, 30000), "the import said nothing");

                Components.settle();

                List<String> said = new ArrayList<>(answering.asked());

                assertEquals(said.get(0), I18n.t("loc.ui.errorEditorOpenNoLinkOrSync"), "the import said " + said);
                assertEquals(said.size(), 1, "the import said more than the refusal: " + said);
                assertEquals(model.getLocByName(name).getAddress(), was, "the import re-addressed under an open editor");
            }
        }
        finally
        {
            model.deleteLoc(name);
            file.delete();
        }
    }
}
