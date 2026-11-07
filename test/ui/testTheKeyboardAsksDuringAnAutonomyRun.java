package ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.AccessoryKeyboard;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;

/**
 * A click on a half of an accessory keyboard key while autonomy runs (FR-050): the accessory on a path a train holds,
 * and a green click that would clear the protection of an occupied platform, ask first; the same click on the half
 * already lit asks too, since it is a command to the railway; an accessory neither on a path nor protecting asks
 * nothing.  Each claim clicks the half, answers the real question, and reads every command handed to the control
 * station (`setSentMessageObserver`) and what the key shows.
 */
public class testTheKeyboardAsksDuringAnAutonomyRun
{
    private static final long WAIT = 15000;

    /** On a path a train holds; protecting the occupied platform; on no path and protecting nothing. */
    private static final int ON_A_PATH = 20, PROTECTING = 21, FREE = 22;

    private static final String PLATFORM = "MainPlatform";
    private static final String ASKS_PATH = I18n.t("layout.ui.confirmAccessoryActiveRoute");
    private static final String ASKS_PROTECTION = I18n.t("layout.ui.confirmAccessoryProtecting");

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static boolean echoWas;

    private static Edge pathEdge;
    private static Locomotive train;

    private static final List<CS2Message> sent = Collections.synchronizedList(new ArrayList<>());

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the keyboard tab is on a window");

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("single-switch"));

            echoWas = MarklinControlStation.DEBUG_SIMULATE_PACKETS;
            model = support.TestStation.simulatedWithWindowInDebug();
            ui = (TrainControlUI) model.getGUI();

            Components.settle();

            model.setSentMessageObserver(sent::add);
            model.setNetworkCommState(false);
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = true;

            Layout layout = model.getAutoLayout();

            layout.setSimulate(true);

            for (int address : new int[] {ON_A_PATH, PROTECTING, FREE})
            {
                model.newSwitch(address, Accessory.accessoryDecoderType.MM2, false);
            }

            layout.createPoint("KB_A", false, null);
            layout.createPoint("KB_B", false, null);

            pathEdge = layout.createEdge("KB_A", "KB_B");
            pathEdge.addConfigCommand(accessory(ON_A_PATH).getName(), Accessory.accessorySetting.STRAIGHT);

            train = model.getLocByName(model.getLocList().get(0));

            layout.getPoint(PLATFORM).setProtectingSignal(accessory(PROTECTING).getName());

            press(window("MM2"));
            press(((AccessoryKeyboard) window("keyboardKeys")).tab(1));
        }
        catch (Throwable failed)
        {
            tearDownClass();

            throw failed;
        }
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null)
            {
                model.setSentMessageObserver(null);
                model.clearAutoLayout();
            }
        }
        finally
        {
            MarklinControlStation.DEBUG_SIMULATE_PACKETS = echoWas;

            if (sandbox != null) sandbox.close();
        }
    }

    /** Nothing held, nothing standing, no question left open, the power on. */
    @AfterMethod(alwaysRun = true)
    public void putTheRailwayBack() throws Exception
    {
        Components.onTheEventThread(() ->
        {
            java.util.Arrays.stream(java.awt.Window.getWindows())
                .filter(w -> w instanceof JDialog && w.isShowing()).forEach(java.awt.Window::dispose);

            return null;
        });

        Layout layout = model.getAutoLayoutIfLoaded();

        if (layout != null && pathEdge != null)
        {
            underway(layout).clear();
            setOccupancy(0);
            layout.getPoint(PLATFORM).setLocomotive(null);
            layout.stopLocomotives();
        }

        model.go();

        assertTrue(model.waitForPowerState(true, WAIT), "the power did not come back on");
    }

    /**
     * On a path a train holds, a click on the half NOT lit asks; Cancel and a closed question both send nothing and leave
     * the key as it was.
     *
     * MUTATION: `if (!protecting) return true;` for `if (!onAPath && !protecting) return true;` in
     * `keyboardAutonomyAllows`, and nothing is asked.
     */
    @Test
    public void testAnAccessoryOnAPathAsksAndNoLeavesTheKeyAsItWas() throws Exception
    {
        holdAPath();
        settleAt(ON_A_PATH, false);

        for (String refusal : new String[] {I18n.t("ui.cancel"), "closed"})
        {
            int mark = sent.size();

            asks(ON_A_PATH, true, refusal, ASKS_PATH);

            assertTrue(commandsTo(ON_A_PATH, mark).isEmpty(), refusal + " sent a command");
            assertTrue(lit(ON_A_PATH, false) && !lit(ON_A_PATH, true), refusal + " changed the key");
            assertTrue(!accessory(ON_A_PATH).isSwitched(), refusal + " changed the accessory");
        }
    }

    /**
     * OK on the same question sends the color clicked, and the key shows it.
     *
     * MUTATION: `return false;` for `return true;` after `if (choice == 0)` in `keyboardAutonomyAllows`, and this fails.
     */
    @Test
    public void testOkSendsTheColorClicked() throws Exception
    {
        holdAPath();
        settleAt(ON_A_PATH, false);

        int mark = sent.size();

        asks(ON_A_PATH, true, I18n.t("ui.ok"), ASKS_PATH);

        assertTrue(Components.waitFor(() -> commandsTo(ON_A_PATH, mark).equals(Collections.singletonList(0)), WAIT),
            "OK did not send red: " + commandsTo(ON_A_PATH, mark));
        assertTrue(Components.waitFor(() -> lit(ON_A_PATH, true), WAIT), "OK did not light red");
    }

    /**
     * A click on the half already lit is a command to the railway too, so it asks the same question: No sends nothing,
     * OK sends that color once.
     *
     * MUTATION: in `keyboardKeyClicked` skip the question when the half is already lit
     * (`keyboardKeys.isLit(key, red) ? KeyboardSwitch.PROCEED : keyboardSwitchAllowed(address, red)`), and this fails.
     */
    @Test
    public void testReFiringTheLitHalfAsksTheSameQuestion() throws Exception
    {
        holdAPath();
        settleAt(ON_A_PATH, false);

        int mark = sent.size();

        asks(ON_A_PATH, false, I18n.t("ui.cancel"), ASKS_PATH);

        assertTrue(commandsTo(ON_A_PATH, mark).isEmpty(), "No sent a re-fire");

        asks(ON_A_PATH, false, I18n.t("ui.ok"), ASKS_PATH);

        assertTrue(Components.waitFor(() -> commandsTo(ON_A_PATH, mark).equals(Collections.singletonList(1)), WAIT),
            "OK on a re-fire did not send green once: " + commandsTo(ON_A_PATH, mark));
        assertTrue(lit(ON_A_PATH, false), "green is not lit after the re-fire");
    }

    /**
     * A green click that would clear the protection of an occupied platform asks; red on the same signal does not.
     *
     * MUTATION: `clearsProtection(accessory, red)` for `clearsProtection(accessory, !red)` in `keyboardAutonomyAllows`,
     * and this fails.
     */
    @Test
    public void testGreenOverAProtectedPlatformAsksAndRedDoesNot() throws Exception
    {
        occupyThePlatform();
        settleAt(PROTECTING, true);

        int mark = sent.size();

        asks(PROTECTING, false, I18n.t("ui.cancel"), ASKS_PROTECTION);

        assertTrue(commandsTo(PROTECTING, mark).isEmpty(), "Cancel sent a command");
        assertTrue(lit(PROTECTING, true), "Cancel changed the key");

        asks(PROTECTING, false, I18n.t("ui.ok"), ASKS_PROTECTION);

        assertTrue(Components.waitFor(() -> commandsTo(PROTECTING, mark).equals(Collections.singletonList(1)), WAIT),
            "OK did not send green: " + commandsTo(PROTECTING, mark));

        int before = sent.size();

        try (Answering answering = Answering.start(asked -> Answering.press(I18n.t("ui.cancel"))))
        {
            click(PROTECTING, true);

            assertTrue(Components.waitFor(() -> commandsTo(PROTECTING, before).equals(Collections.singletonList(0)), WAIT),
                "red over a protected platform was not sent");
            assertEquals(answering.asked(), Collections.emptyList(), "red over a protected platform asked");
        }
    }

    /**
     * An accessory on no path that protects nothing asks nothing, whichever half is clicked: the control for the above.
     *
     * MUTATION: `if (!onAPath && !protecting) return true;` for `if (false) return true;` in `keyboardAutonomyAllows`,
     * and a question is asked.
     */
    @Test
    public void testAnAccessoryOnNoPathAsksNothing() throws Exception
    {
        holdAPath();
        occupyThePlatform();
        settleAt(FREE, false);

        try (Answering answering = Answering.start(asked -> Answering.press(I18n.t("ui.cancel"))))
        {
            for (boolean red : new boolean[] {true, false, false})
            {
                int mark = sent.size();

                click(FREE, red);

                assertTrue(Components.waitFor(() -> commandsTo(FREE, mark).equals(Collections.singletonList(red ? 0 : 1)), WAIT),
                    "a click on an accessory on no path was not sent: " + commandsTo(FREE, mark));
            }

            assertEquals(answering.asked(), Collections.emptyList(), "an accessory on no path asked a question");
        }
    }

    // ---------------------------------------------------------------- the railway

    /** A train holding the path over the turnout: autonomy is running and the turnout is on its path. */
    @SuppressWarnings("unchecked")
    private static void holdAPath() throws Exception
    {
        Layout layout = model.getAutoLayout();

        setOccupancy(1);

        ((java.util.Map<Locomotive, List<Edge>>) underway(layout)).put(train, Collections.singletonList(pathEdge));

        assertTrue(layout.getActiveAccs().contains(accessory(ON_A_PATH)), "precondition: the turnout is not on a path");
        assertTrue(model.isAutonomyRunning(), "precondition: autonomy is not running");
    }

    /** Autonomy running, and a train standing at the platform the signal protects. */
    private static void occupyThePlatform() throws Exception
    {
        Layout layout = model.getAutoLayout();

        layout.getPoint(PLATFORM).setLocomotive(train);

        if (!model.isAutonomyRunning()) layout.runLocomotives();

        assertTrue(model.isAutonomyRunning(), "precondition: autonomy is not running");
        assertTrue(layout.clearsProtection(accessory(PROTECTING), true), "precondition: green would clear a protection");
    }

    @SuppressWarnings("rawtypes")
    private static java.util.Map underway(Layout layout) throws Exception
    {
        java.lang.reflect.Field f = Layout.class.getDeclaredField("activeLocomotives");

        f.setAccessible(true);

        return (java.util.Map) f.get(layout);
    }

    private static void setOccupancy(int value) throws Exception
    {
        java.lang.reflect.Field f = Edge.class.getDeclaredField("occupancy");

        f.setAccessible(true);
        f.setInt(pathEdge, value);
    }

    /** The accessory in a position, as the station reports it, and its key showing that. */
    private static void settleAt(int address, boolean red) throws Exception
    {
        MarklinAccessory acc = accessory(address);

        acc.setSwitched(red);

        assertTrue(Components.waitFor(() -> lit(address, red), WAIT), "precondition: the key does not show " + red);
    }

    /** A click on a half, the question it asks answered with a button, and a check it was the one expected. */
    private static void asks(int address, boolean red, String button, String question) throws Exception
    {
        Answering.Answer answer = button.equals("closed") ? Answering.press("no such button") : Answering.press(button);

        try (Answering answering = Answering.start(asked -> answer))
        {
            click(address, red);

            assertTrue(answering.awaitAsked(1, WAIT), "the click on " + address + " asked nothing");
            assertEquals(answering.asked().get(0), question, "the click asked something else");
        }

        Components.settle();
    }

    private static List<Integer> commandsTo(int address, int mark)
    {
        int uid = MarklinAccessory.UIDfromAddress(address - 1, Accessory.accessoryDecoderType.MM2);

        return support.AccessoryCommands.positionsTo(new ArrayList<>(sent.subList(Math.min(mark, sent.size()), sent.size())), uid);
    }

    private static MarklinAccessory accessory(int address)
    {
        return (MarklinAccessory) model.getAccessoryByAddressIfPresent(address, Accessory.accessoryDecoderType.MM2);
    }

    // ---------------------------------------------------------------- the keyboard

    private static <T> T window(String name)
    {
        return support.WindowField.of(ui, name);
    }

    private static void click(int address, boolean red)
    {
        AccessoryKeyboard keys = window("keyboardKeys");
        int key = Components.onTheEventThread(() -> keys.keyOf(address));

        press(keys.half(key, red));
    }

    private static boolean lit(int address, boolean red)
    {
        AccessoryKeyboard keys = window("keyboardKeys");

        return Components.onTheEventThread(() -> keys.isLit(keys.keyOf(address), red));
    }

    private static void press(AbstractButton button)
    {
        Components.press(button);
        Components.settle();
    }
}
