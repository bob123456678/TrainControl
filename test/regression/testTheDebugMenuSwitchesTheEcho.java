package regression;

import java.lang.reflect.Field;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.SourceOf;

/**
 * Preferences > Debug switches the echo of sent commands, in debug and simulation only (Adam, 2026-09-24).
 *
 * *"it would be handy to have a preference option (only when in debug+simulate mode) to turn on and off echoing, so we
 * don't need to recompile every time"* - then *"this could go in the preferences menu, under a debug heading visible only
 * in this mode"*, and *"I'd rather have debug be a non-bold heading with a submenu that has the options"*.  Echoing is
 * `MarklinControlStation.DEBUG_SIMULATE_PACKETS`: with it on, a simulation answers every command as a Central Station
 * would, which is what lets the window follow a throttle reversal there (MT-488).
 *
 * In a window started simulating and in debug, on a sandbox.  A test run is unattended, so the stored choice is neither
 * read into the flag nor written from it - a test must not switch echo on the operator's next launch.
 *
 * MUTATION: leave the checkbox out of the flag, or mount the menu whatever the mode, and a claim fails.
 *
 * And what a Central Station sends shows in that window (FR-136 Phase 6): the power cut at the station, a turnout thrown
 * there, a sensor made, each sent as the station sends it (`support.Packets`) and read off the window and the diagram;
 * and Sync Full Function State asks the station, or does not, as its question is answered.
 *
 * @author Adam
 */
public class testTheDebugMenuSwitchesTheEcho
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static boolean echoWas;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the menu is part of the window, and the window needs a display");
        }

        echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

        // Simulating, with a window, in debug.
        model = support.TestStationAddress.withNoStation(
            () -> MarklinControlStation.init(null, true, true, false, true));

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "there is no window");

        for (int pass = 0; pass < 6; pass++) SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

        if (sandbox != null) sandbox.close();
    }

    /**
     * Preferences has a Debug submenu, in the ordinary face, holding the echo switch.
     *
     * @throws Exception from reflection
     */
    @Test
    public void testPreferencesHasADebugSubmenu() throws Exception
    {
        JMenu debug = debugMenu();

        assertNotNull(debug, "in debug and simulation the Preferences menu has no Debug submenu.  Adam, 2026-09-24:"
            + " \"a preference option (only when in debug+simulate mode) to turn on and off echoing\"");

        assertFalse(debug.getFont().isBold(), "Debug is drawn bold - Adam: \"I'd rather have debug be a non-bold heading"
            + " with a submenu that has the options\"");

        assertNotNull(echoItem(debug), "the Debug submenu has no Echo Sent Commands");
    }

    /**
     * Ticking it switches echo on, and unticking switches it off.
     *
     * @throws Exception from the event thread
     */
    @Test(dependsOnMethods = "testPreferencesHasADebugSubmenu")
    public void testTheSwitchTurnsTheEchoOnAndOff() throws Exception
    {
        final JCheckBoxMenuItem echo = echoItem(debugMenu());

        final boolean was = echo.isSelected();

        assertEquals(was, MarklinControlStation.DEBUG_SIMULATE_PACKETS, "the switch does not show whether echo is on");

        SwingUtilities.invokeAndWait(() -> echo.doClick());

        assertEquals(MarklinControlStation.DEBUG_SIMULATE_PACKETS, !was, "choosing Echo Sent Commands did not switch"
            + " echo " + (was ? "off" : "on"));

        SwingUtilities.invokeAndWait(() -> echo.doClick());

        assertEquals(MarklinControlStation.DEBUG_SIMULATE_PACKETS, was, "choosing it again did not switch echo back");
    }

    /**
     * Mounted only in debug and simulation, and the stored choice applied there - asked of the source, since this window
     * is in that mode and a second window in another is a second start in one JVM.
     *
     * @throws Exception if the source cannot be read
     */
    @Test
    public void testItIsOfferedOnlyInDebugAndSimulation() throws Exception
    {
        String body = SourceOf.withoutComments(SourceOf.declaration("private void mountDebugMenu()"));

        assertTrue(body.contains("isDebug()") && body.contains("isSimulation()"), "the Debug submenu is not held to"
            + " debug and simulation - \"visible only in this mode\": " + body);

        assertTrue(body.contains("ECHO_COMMANDS_PREF"), "the stored choice is not read when the menu is mounted, so it"
            + " has to be made again on every launch - \"so we don't need to recompile every time\"");
    }

    /**
     * The power cut at the station shows in the window: Power Off greyed and Power On offered; restored, the other way
     * round.
     *
     * MUTATION: in `MarklinControlStation.receiveMessage`, take out the STOP branch's
     * `if (this.view != null) this.view.updatePowerState();`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testAPowerCutAtTheStationShowsInTheWindow() throws Exception
    {
        final javax.swing.AbstractButton off = (javax.swing.AbstractButton) windowField("PowerOff");
        final javax.swing.AbstractButton on = (javax.swing.AbstractButton) windowField("OnButton");

        try
        {
            model.receiveMessage(support.Packets.powerGo());

            assertTrue(model.waitForPowerState(true, 10000), "precondition: the power is not on");

            assertTrue(support.Components.waitFor(() -> enabled(off) && !enabled(on), 10000), "precondition: the window"
                + " does not show the power on");

            model.receiveMessage(support.Packets.powerStop());

            assertTrue(model.waitForPowerState(false, 10000), "precondition: the station's stop did not cut the power");

            assertTrue(support.Components.waitFor(() -> !enabled(off) && enabled(on), 10000), "the station cut the power"
                + " and the window still offers Power Off, not Power On");

            model.receiveMessage(support.Packets.powerGo());

            assertTrue(support.Components.waitFor(() -> enabled(off) && !enabled(on), 10000), "the station restored the"
                + " power and the window still offers Power On");
        }
        finally
        {
            model.receiveMessage(support.Packets.powerGo());
        }
    }

    /**
     * A turnout thrown at the station shows on its key of the keyboard, and put straight again shows straight.
     *
     * MUTATION: in `MarklinControlStation.receiveMessage`, take out
     * `this.view.repaintSwitch(accessory.getAddress() + 1, accessory.getDecoderType());`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testATurnoutThrownAtTheStationShowsOnItsKey() throws Exception
    {
        final org.traincontrol.marklin.MarklinAccessory turnout = model.getAccessoryByAddressIfPresent(1,
            org.traincontrol.base.Accessory.accessoryDecoderType.MM2);

        assertNotNull(turnout, "precondition: the scenario's turnout, accessory 1, is not in the model");

        final javax.swing.AbstractButton key = (javax.swing.AbstractButton) windowField("SwitchButton1");

        assertEquals(support.Components.onTheEventThread(key::getText), "1", "precondition: the keyboard does not show"
            + " accessory 1 on its first key");

        try
        {
            model.receiveMessage(support.Packets.accessoryEcho(turnout, false));

            assertTrue(support.Components.waitFor(() -> !selected(key), 10000), "precondition: the key does not show the"
                + " turnout straight");

            model.receiveMessage(support.Packets.accessoryEcho(turnout, true));

            assertTrue(support.Components.waitFor(() -> selected(key), 10000), "the station threw the turnout and its key"
                + " still shows it straight");

            model.receiveMessage(support.Packets.accessoryEcho(turnout, false));

            assertTrue(support.Components.waitFor(() -> !selected(key), 10000), "the station put the turnout straight and"
                + " its key still shows it thrown");
        }
        finally
        {
            model.receiveMessage(support.Packets.accessoryEcho(turnout, false));
        }
    }

    /**
     * A sensor made at the station is drawn made on the track diagram.
     *
     * MUTATION: in `MarklinFeedback.parseMessage`, take out `this.updateTiles();`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testASensorMadeAtTheStationIsDrawnOnTheDiagram() throws Exception
    {
        // WestEnd's sensor, 10, at 1,3 of the scenario's one page
        final int sensor = 10;

        support.Rendered view = support.Rendered.open(model.getLayout(model.getLayoutList().get(0)), ui);

        model.receiveMessage(support.Packets.s88(sensor, false));

        assertTrue(support.Components.waitFor(() -> !model.getFeedbackState(Integer.toString(sensor)), 10000),
            "precondition: the sensor is not clear");

        java.awt.image.BufferedImage clear = support.Rendered.tile(view.snapshot(), 1, 3);

        model.receiveMessage(support.Packets.s88(sensor, true));

        assertTrue(support.Components.waitFor(() -> model.getFeedbackState(Integer.toString(sensor)), 10000),
            "precondition: the station's report did not make the sensor");

        assertTrue(support.Components.waitFor(() -> differs(view, clear), 10000), "the station made sensor " + sensor
            + " and its square on the diagram is drawn as before");

        model.receiveMessage(support.Packets.s88(sensor, false));
    }

    /**
     * Sync Full Function State, answered Yes, turns every locomotive's functions off and asks the station for each
     * locomotive's state.
     *
     * MUTATION: in `TrainControlUI.syncFullLocStateMenuItemActionPerformed`, take out `this.model.allFunctionsOff();`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testSyncFullFunctionStateAnsweredYesAsksTheStation() throws Exception
    {
        final org.traincontrol.marklin.MarklinLocomotive train = aTrainWithItsLightsOn("SYNC asked", 68);

        final java.util.List<org.traincontrol.marklin.udp.CS2Message> sent =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        model.setSentMessageObserver(sent::add);

        try (support.Answering answering = support.Answering.start(message -> message.equals(
            I18n.t("loc.ui.confirmQueryCentralStationFunctions")) ? support.Answering.press(I18n.t("ui.yes"))
                : support.Answering.pressAt(0)))
        {
            support.Components.press(syncFullFunctionState());

            assertTrue(answering.awaitAsked(1, 10000), "Sync Full Function State asked nothing");

            assertEquals(answering.asked().get(0), I18n.t("loc.ui.confirmQueryCentralStationFunctions"));

            assertTrue(support.Components.waitFor(() -> askedForTheSpeedOf(sent, train), 30000), "Sync Full Function"
                + " State answered Yes did not ask the station for the train's state");
        }
        finally
        {
            model.setSentMessageObserver(null);
        }

        assertFalse(train.getF(0), "Sync Full Function State answered Yes left the train's lights on");
    }

    /**
     * The same question answered No changes nothing and asks the station nothing.
     *
     * MUTATION: in `TrainControlUI.syncFullLocStateMenuItemActionPerformed`,
     * `if(dialogResult == JOptionPane.YES_OPTION)` to `if(true)`.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testSyncFullFunctionStateAnsweredNoChangesNothing() throws Exception
    {
        final org.traincontrol.marklin.MarklinLocomotive train = aTrainWithItsLightsOn("SYNC not asked", 69);

        final java.util.List<org.traincontrol.marklin.udp.CS2Message> sent =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        model.setSentMessageObserver(sent::add);

        try (support.Answering answering = support.Answering.start(message -> support.Answering.press(I18n.t("ui.no"))))
        {
            support.Components.press(syncFullFunctionState());

            assertTrue(answering.awaitAsked(1, 10000), "Sync Full Function State asked nothing");

            // Its queries run on a worker: given the time to, had they been sent
            Thread.sleep(1000);

            support.Components.settle();
        }
        finally
        {
            model.setSentMessageObserver(null);
        }

        assertTrue(train.getF(0), "Sync Full Function State answered No turned the train's lights off");

        assertFalse(askedForTheSpeedOf(sent, train), "Sync Full Function State answered No asked the station anyway");
    }

    private static javax.swing.AbstractButton syncFullFunctionState()
    {
        javax.swing.AbstractButton item = support.Components.withText(ui, I18n.t("ui.main.toolbar.syncFullFunctionState"));

        assertNotNull(item, "the window has no Sync Full Function State");

        return item;
    }

    /** A locomotive of this class's own, with F0 on. */
    private static org.traincontrol.marklin.MarklinLocomotive aTrainWithItsLightsOn(String name, int address)
    {
        org.traincontrol.marklin.MarklinLocomotive train = model.getLocByName(name) != null ? model.getLocByName(name)
            : model.newMM2Locomotive(name, address);

        train.setF(0, true);

        assertTrue(train.getF(0), "precondition: the train's lights are not on");

        return train;
    }

    /** Whether a speed query - the velocity command with no speed in it - went to the railway for this train. */
    private static boolean askedForTheSpeedOf(java.util.List<org.traincontrol.marklin.udp.CS2Message> sent,
        org.traincontrol.marklin.MarklinLocomotive train)
    {
        synchronized (sent)
        {
            for (org.traincontrol.marklin.udp.CS2Message m : sent)
            {
                if (m.getCommand() == org.traincontrol.marklin.udp.CS2Message.CMD_LOCO_VELOCITY && m.getLength() == 4
                    && m.extractUID() == train.getIntUID()) return true;
            }
        }

        return false;
    }

    private static Object windowField(String name) throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField(name);

        field.setAccessible(true);

        return field.get(ui);
    }

    private static boolean enabled(javax.swing.AbstractButton button)
    {
        return support.Components.onTheEventThread(button::isEnabled);
    }

    private static boolean selected(javax.swing.AbstractButton button)
    {
        return support.Components.onTheEventThread(button::isSelected);
    }

    /** Whether the sensor's square is drawn otherwise than in this picture. */
    private static boolean differs(support.Rendered view, java.awt.image.BufferedImage before)
    {
        try
        {
            return !support.Rendered.same(support.Rendered.tile(view.snapshot(), 1, 3), before);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static JMenu debugMenu() throws Exception
    {
        Field field = TrainControlUI.class.getDeclaredField("interfaceMenu");

        field.setAccessible(true);

        JMenu preferences = (JMenu) field.get(ui);

        String name = I18n.t("ui.main.toolbar.debug");

        for (int i = 0; i < preferences.getItemCount(); i++)
        {
            JMenuItem item = preferences.getItem(i);

            if (item instanceof JMenu && name.equals(item.getText())) return (JMenu) item;
        }

        return null;
    }

    private static JCheckBoxMenuItem echoItem(JMenu debug)
    {
        String name = I18n.t("ui.main.toolbar.echoCommands");

        for (int i = 0; i < debug.getItemCount(); i++)
        {
            JMenuItem item = debug.getItem(i);

            if (item instanceof JCheckBoxMenuItem && name.equals(item.getText())) return (JCheckBoxMenuItem) item;
        }

        return null;
    }
}
