package ui;

import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Mouse;

/**
 * A click on a turnout or signal on the main window's track diagram, and the two questions it can ask first:
 * "the power is off, so this only updates the window" and "this signal protects a platform a train is standing at"
 * (FR-136 Phase 6).
 *
 * Each claim clicks the real square with real mouse events, answers the real question, and reads what reached the
 * railway - every command handed to the control station (`setSentMessageObserver`) - and the accessory's state.
 *
 * ON THE SINGLE-SWITCH RAILWAY, in a sandbox, with one signal added to the sandbox's copy of the page beside the
 * branch's buffer stop.  Echo Sent Commands is on, so the power state follows the commands as it would on a Central
 * Station.
 */
public class testADiagramClickAsksFirst
{
    private static final String PAGE = "1 - Junction";

    private static final int SWITCH_X = 5, SWITCH_Y = 3;
    private static final int SIGNAL_X = 8, SIGNAL_Y = 2;

    /** Plain straight track */
    private static final int TRACK_X = 4, TRACK_Y = 3;

    private static final String PLATFORM = "MainPlatform";

    private static final long PATIENCE_MS = 15000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static boolean echoWas;

    private static final List<CS2Message> sent = Collections.synchronizedList(new ArrayList<>());

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the diagram is a window, and its squares take the mouse");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        // A signal past the branch's buffer stop, in the sandbox's copy only: the scenario has none
        Files.write(new File(sandbox.getFolder(), "config/gleisbilder/" + PAGE + ".cs2").toPath(),
            "element\n .id=0x10208\n .typ=signal\n .artikel=40\n".getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.APPEND);

        model = support.TestStation.simulatedWithWindowInDebug();

        model.setNetworkCommState(false);

        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        assertTrue(Components.waitFor(() -> tile(SWITCH_X, SWITCH_Y) != null && tile(SIGNAL_X, SIGNAL_Y) != null,
            PATIENCE_MS), "the diagram does not show the turnout and the signal");

        model.setSentMessageObserver(sent::add);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            if (model != null)
            {
                model.setSentMessageObserver(null);

                model.clearAutoLayout();
            }
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Power on, autonomy stopped, no train at the platform, nothing left open.
     */
    @AfterMethod(alwaysRun = true)
    public void putTheRailwayBack() throws Exception
    {
        Mouse.closeMenus();

        Components.onTheEventThread(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof JDialog && window.isShowing()) window.dispose();
            }

            return null;
        });

        Layout layout = model.getAutoLayoutIfLoaded();

        if (layout != null)
        {
            layout.stopLocomotives();

            Point platform = layout.getPoint(PLATFORM);

            if (platform != null) platform.setLocomotive(null);
        }

        model.go();

        assertTrue(model.waitForPowerState(true, PATIENCE_MS), "the power did not come back on");
    }

    // ---------------------------------------------------------------- the power is off

    /**
     * With the power off, a click on the turnout asks first, and Cancel sends nothing and throws nothing.
     *
     * MUTATION: skip the question when the power is off, and the turnout is thrown with no question asked.
     */
    @Test
    public void testWithThePowerOffCancelThrowsNothing() throws Exception
    {
        powerOff();

        Accessory turnout = tile(SWITCH_X, SWITCH_Y).getComponent().getAccessory();

        boolean was = turnout.isSwitched();

        int mark = sent.size();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            Mouse.click(tile(SWITCH_X, SWITCH_Y));

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "a click with the power off asked nothing");

            assertEquals(answering.asked().get(0), I18n.t("layout.ui.confirmAccessorySwitchPowerOff"));
        }

        settleTheSwitching();

        assertEquals(turnout.isSwitched(), was, "Cancel threw the turnout");
        assertEquals(accessoryCommandsSince(mark), 0, "Cancel sent a command: " + sent.subList(mark, sent.size()));
        assertFalse(model.getPowerState(), "Cancel turned the power on");
    }

    /**
     * Proceed throws the turnout with the power still off: its command is sent, and the power stays off.
     *
     * MUTATION: make Proceed return, as Cancel does, and the turnout stays.
     */
    @Test
    public void testWithThePowerOffProceedThrowsAndLeavesThePowerOff() throws Exception
    {
        powerOff();

        Accessory turnout = tile(SWITCH_X, SWITCH_Y).getComponent().getAccessory();

        boolean was = turnout.isSwitched();

        int mark = sent.size();

        try (Answering answering = Answering.start(
            message -> Answering.press(I18n.t("layout.ui.optionProceed"))))
        {
            Mouse.click(tile(SWITCH_X, SWITCH_Y));

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "a click with the power off asked nothing");
        }

        assertTrue(Components.waitFor(() -> turnout.isSwitched() != was, PATIENCE_MS), "Proceed did not throw it");

        assertTrue(accessoryCommandsSince(mark) >= 1, "the turnout's command was not sent");
        assertEquals(systemCommandsSince(mark, CS2Message.CMD_SYSSUB_GO), 0, "Proceed turned the power on");
        assertFalse(model.getPowerState(), "the power came on");
    }

    /**
     * Turn Power On & Proceed sends the power on first, then throws the turnout.
     *
     * MUTATION: drop `tcUI.getModel().go()` from the worker and the power stays off.
     */
    @Test
    public void testTurnPowerOnAndProceedDoesBoth() throws Exception
    {
        powerOff();

        Accessory turnout = tile(SWITCH_X, SWITCH_Y).getComponent().getAccessory();

        boolean was = turnout.isSwitched();

        int mark = sent.size();

        try (Answering answering = Answering.start(
            message -> Answering.press(I18n.t("layout.ui.optionTurnPowerOnAndProceed"))))
        {
            Mouse.click(tile(SWITCH_X, SWITCH_Y));

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "a click with the power off asked nothing");
        }

        assertTrue(Components.waitFor(() -> turnout.isSwitched() != was, PATIENCE_MS), "the turnout was not thrown");

        assertTrue(model.waitForPowerState(true, PATIENCE_MS), "the power was not turned on");

        assertEquals(systemCommandsSince(mark, CS2Message.CMD_SYSSUB_GO), 1, "no power-on command was sent");

        assertTrue(firstGoBeforeFirstAccessory(mark), "the turnout was sent before the power: "
            + sent.subList(mark, sent.size()));
    }

    /**
     * With the power on, a click throws at once and asks nothing: the control for the three above.
     *
     * MUTATION: ask the power question whatever the power, and the click waits on it (answered Cancel here) and throws
     * nothing.
     */
    @Test
    public void testWithThePowerOnAClickThrowsWithoutAsking() throws Exception
    {
        assertTrue(model.getPowerState(), "precondition: the power is on");

        Accessory turnout = tile(SWITCH_X, SWITCH_Y).getComponent().getAccessory();

        boolean was = turnout.isSwitched();

        int mark = sent.size();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            Mouse.click(tile(SWITCH_X, SWITCH_Y));

            assertTrue(Components.waitFor(() -> turnout.isSwitched() != was, PATIENCE_MS), "the click threw nothing");

            assertEquals(answering.asked(), Collections.emptyList(), "a click with the power on asked a question");
        }

        assertTrue(accessoryCommandsSince(mark) >= 1, "the turnout's command was not sent");
    }

    // ---------------------------------------------------------------- the protecting signal

    /**
     * Setting a protecting signal green while a train stands at its platform asks first; Cancel leaves it red and sends
     * nothing.
     *
     * MUTATION: drop the protection half of the question (`|| protecting`) and nothing is asked.
     */
    @Test
    public void testAProtectingSignalOverAStandingTrainAsksAndCancelKeepsItRed() throws Exception
    {
        Accessory signal = protectingTheOccupiedPlatform();

        int mark = sent.size();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            Mouse.click(tile(SIGNAL_X, SIGNAL_Y));

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "nothing was asked before clearing the protection");

            assertEquals(answering.asked().get(0), I18n.t("layout.ui.confirmAccessoryProtecting"));
        }

        settleTheSwitching();

        assertTrue(signal.isSwitched(), "Cancel set the protecting signal green");
        assertEquals(accessoryCommandsSince(mark), 0, "Cancel sent a command: " + sent.subList(mark, sent.size()));
    }

    /**
     * And OK sets it green: the operator was asked about this very signal, so the worker does not refuse it.
     *
     * MUTATION: drop `askedAboutProtection = protecting`, and the worker refuses the click it was told to make.
     */
    @Test
    public void testOkSetsTheProtectingSignalGreen() throws Exception
    {
        Accessory signal = protectingTheOccupiedPlatform();

        int mark = sent.size();

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.ok"))))
        {
            Mouse.click(tile(SIGNAL_X, SIGNAL_Y));

            assertTrue(answering.awaitAsked(1, PATIENCE_MS), "nothing was asked before clearing the protection");
        }

        assertTrue(Components.waitFor(() -> !signal.isSwitched(), PATIENCE_MS), "OK did not set the signal green");

        assertTrue(accessoryCommandsSince(mark) >= 1, "the signal's command was not sent");
    }

    /**
     * Setting it red over the same train asks nothing: red is what protection itself commands.
     *
     * MUTATION: drop the aspect from the question (ask as if every click set it green), and setting it red asks and is
     * cancelled.
     */
    @Test
    public void testSettingTheProtectingSignalRedAsksNothing() throws Exception
    {
        Accessory signal = protectingTheOccupiedPlatform();

        signal.setSwitched(false);

        try (Answering answering = Answering.start(message -> Answering.press(I18n.t("ui.cancel"))))
        {
            Mouse.click(tile(SIGNAL_X, SIGNAL_Y));

            assertTrue(Components.waitFor(signal::isSwitched, PATIENCE_MS), "the click did not set the signal red");

            assertEquals(answering.asked(), Collections.emptyList(), "setting a protecting signal red asked");
        }
    }

    // ---------------------------------------------------------------- right-clicks

    /**
     * A right-click on the turnout opens the square's autonomy menu, and throws nothing.
     *
     * MUTATION: let the station menu fall through to the switching, and the turnout is thrown.
     */
    @Test
    public void testARightClickOnTheTurnoutOpensItsMenuAndThrowsNothing() throws Exception
    {
        Accessory turnout = tile(SWITCH_X, SWITCH_Y).getComponent().getAccessory();

        boolean was = turnout.isSwitched();

        int mark = sent.size();

        JPopupMenu menu = Mouse.rightClick(tile(SWITCH_X, SWITCH_Y), PATIENCE_MS);

        assertNotNull(menu, "a right-click on the turnout opened no menu");

        settleTheSwitching();

        assertEquals(turnout.isSwitched(), was, "a right-click threw the turnout");
        assertEquals(accessoryCommandsSince(mark), 0, "a right-click sent a command");
    }

    /**
     * A right-click on plain track opens the square's autonomy menu too.
     *
     * MUTATION: drop the plain-track listener and nothing opens.
     */
    @Test
    public void testARightClickOnPlainTrackOpensItsMenu() throws Exception
    {
        LayoutLabel track = tile(TRACK_X, TRACK_Y);

        assertNotNull(track, "there is no plain track at " + TRACK_X + "," + TRACK_Y);

        assertNotNull(Mouse.rightClick(track, PATIENCE_MS), "a right-click on plain track opened no menu");
    }

    // ---------------------------------------------------------------- the railway

    /** The power cut as the Central Station reports it: the stop command, echoed. */
    private static void powerOff() throws Exception
    {
        model.stop();

        assertTrue(model.waitForPowerState(false, PATIENCE_MS), "the power did not go off");
    }

    /**
     * Autonomy running, a train standing at the main platform, the added signal protecting that platform, and the signal
     * red - so a click on it would set it green.
     */
    private static Accessory protectingTheOccupiedPlatform() throws Exception
    {
        Accessory signal = tile(SIGNAL_X, SIGNAL_Y).getComponent().getAccessory();

        assertNotNull(signal, "the added signal has no accessory");

        Layout layout = model.getAutoLayout();

        Point platform = layout.getPoint(PLATFORM);

        assertNotNull(platform, "there is no " + PLATFORM + " in the loaded configuration");

        platform.setProtectingSignal(signal.getName());

        Locomotive train = model.getLocByName(model.getLocList().get(0));

        platform.setLocomotive(train);

        signal.setSwitched(true);

        // Started with nobody to run: running, and nothing moves
        layout.runLocomotives();

        assertTrue(model.isAutonomyRunning(), "precondition: autonomy is running");

        assertTrue(layout.clearsProtection(signal, true), "precondition: green would clear the protection");

        return signal;
    }

    private static int accessoryCommandsSince(int mark)
    {
        int seen = 0;

        for (CS2Message m : snapshot(mark))
        {
            if (m.getCommand() == CS2Message.CMD_ACC_SWITCH) seen++;
        }

        return seen;
    }

    private static int systemCommandsSince(int mark, int sub)
    {
        int seen = 0;

        for (CS2Message m : snapshot(mark))
        {
            if (m.getCommand() == CS2Message.CMD_SYSTEM && m.getSubCommand() == sub) seen++;
        }

        return seen;
    }

    private static boolean firstGoBeforeFirstAccessory(int mark)
    {
        for (CS2Message m : snapshot(mark))
        {
            if (m.getCommand() == CS2Message.CMD_SYSTEM && m.getSubCommand() == CS2Message.CMD_SYSSUB_GO) return true;

            if (m.getCommand() == CS2Message.CMD_ACC_SWITCH) return false;
        }

        return false;
    }

    private static List<CS2Message> snapshot(int mark)
    {
        synchronized (sent)
        {
            return new ArrayList<>(sent.subList(Math.min(mark, sent.size()), sent.size()));
        }
    }

    /**
     * Lets a click's work on the event thread finish.  A refused click - Cancel, or a right-click - returns there,
     * before anything is handed to the switching worker, so once the event thread is idle nothing more can come of it.
     */
    private static void settleTheSwitching()
    {
        Components.settle();
    }

    /** The square on the main window's diagram, as drawn now. */
    private static LayoutLabel tile(int x, int y)
    {
        for (LayoutLabel label : ui.getDiagramTileRegistry().labelsFor(new TileKey(PAGE, x, y)))
        {
            if (label.getComponent() != null && label.isShowing()) return label;
        }

        return null;
    }
}
