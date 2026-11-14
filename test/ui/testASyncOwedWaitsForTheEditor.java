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

    private LayoutEditor editor;

    private final List<String> made = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        AskingWindows.Railway opened = AskingWindows.openRailway("live-snapshot", null);

        sandbox = opened.sandbox;
        model = opened.model;
        ui = opened.ui;

        Components.settle();

        session = ui.getAutonomySession();

        assertNotNull(session, "no autonomy setup on this railway");

        asOpened = Components.onTheEventThread(() -> session.snapshotSetup());

        // A Central Station 2 with no routes file: every sync asks for the device file, then fails before importing
        station = new StandInStation((path, n) -> DEVICE.equals(path)
            ? "[geraet]\ngeraet\n .produkt=Central Station 2\n".getBytes(StandardCharsets.UTF_8) : null);

        addressWas = TestStationAddress.get();

        TestStationAddress.set(station.address());
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            TestStationAddress.set(addressWas);

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
     * MUTATION: in `TrainControlUI.runTheOwedSync`, replace `!syncOwed.compareAndSet(true, false)` with
     * `!syncOwed.get()`, so the debt is never cleared, and the next Cancel syncs again.
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
}
