package ui;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSliderUI;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.AccessoryKeyboard;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.I18n;
import support.Answering;
import support.Components;
import support.Keys;
import support.WireRecorder;
import support.WireRecorder.Sent;

/**
 * Driving a train from the main window, as the operator does every session: the keys, the buttons and the sliders of
 * the keyboard tab, and what reaches the railway when they are used.  Each claim acts as the operator (`support.Keys`,
 * a click, a drag, an answered dialog) and asserts both what the locomotive or accessory now is and the command the
 * control station was given (`support.WireRecorder`), so a key that changed the window and sent nothing, or the wrong
 * thing, fails.
 *
 * The railway is a sandbox with no Central Station: the model runs in debug, so every command it would send is
 * logged and read back, and the power is switched by packets given to `receiveMessage`.
 *
 * MUTATION: each claim names the change to the program that turns it red.
 *
 * @author Adam
 */
public class testDrivingFromTheKeyboard
{
    private static final String ONE = "P6 keyboard one";
    private static final String TWO = "P6 keyboard two";

    /** MFX, so that a stop is the stop command and nothing else (an MM2 stop turns the train twice first). */
    private static final int ONE_ADDRESS = 3101;
    private static final int TWO_ADDRESS = 3102;

    private static final long WAIT = 5000;

    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static WireRecorder wire;

    private static MarklinLocomotive one;
    private static MarklinLocomotive two;

    /** The letters the two locomotives are on. */
    private static int oneKey;
    private static int twoKey;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the keyboard tab is on a window");

        sandbox = support.LayoutSandbox.open();

        // In debug, so that what the station would have been sent is logged (WireRecorder); the window is the model's own,
        // so the power's echo reaches it as it does in the application
        model = support.TestStation.simulatedWithWindowInDebug();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();

        wire = WireRecorder.open();

        for (String name : new String[] {ONE, TWO})
        {
            if (model.getLocByName(name) != null) model.deleteLoc(name);
        }

        one = model.newMFXLocomotive(ONE, ONE_ADDRESS);
        two = model.newMFXLocomotive(TWO, TWO_ADDRESS);

        Map<Integer, JButton> letters = letters();

        oneKey = KeyEvent.VK_A;
        twoKey = KeyEvent.VK_S;

        assertNotNull(letters.get(oneKey), "precondition: A is not a locomotive key");
        assertNotNull(letters.get(twoKey), "precondition: S is not a locomotive key");

        put(oneKey, one);
        put(twoKey, two);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (wire != null) wire.close();

            if (model != null)
            {
                for (String name : new String[] {ONE, TWO})
                {
                    if (model.getLocByName(name) != null) model.deleteLoc(name);
                }
            }

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() ->
                {
                    closing.stopDrivingTrainsFromTheWholeWindow();
                    closing.dispose();
                });
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** Both trains standing, facing forward, every function off, the power on, and the first one shown. */
    @BeforeMethod
    public void standStill() throws Exception
    {
        if (!model.getPowerState()) fromTheRailway(CS2Message.CMD_SYSSUB_GO);

        assertTrue(waitFor(model::getPowerState), "precondition: the power did not come back on");

        for (MarklinLocomotive loc : new MarklinLocomotive[] {one, two})
        {
            loc.setSpeed(0);
            loc.setDirection(Locomotive.locDirection.DIR_FORWARD);
            loc.functionsOff();
        }

        show(oneKey, one);
    }

    // Direction

    /**
     * Reverse stops the train and turns it backwards, on the railway and on the buttons; Forward turns it back.
     *
     * MUTATION: `DIR_FORWARD` for `DIR_BACKWARD` in `backwardLoc`, and this fails.
     */
    @Test
    public void testReverseAndForwardTurnTheTrain() throws Exception
    {
        JToggleButton reverse = (JToggleButton) field("Backward");
        JToggleButton forward = (JToggleButton) field("Forward");

        int mark = wire.mark();

        Components.press(reverse);

        assertTrue(waitFor(() -> !one.goingForward()), "Reverse left the train facing forward");
        assertTrue(waitFor(() -> lastDirection(mark, one) == 2), "Reverse sent no backward command: " + wire.since(mark));
        assertTrue(waitFor(() -> reverse.isSelected() && !forward.isSelected()), "Reverse is not shown pressed");

        int back = wire.mark();

        Components.press(forward);

        assertTrue(waitFor(one::goingForward), "Forward left the train facing backward");
        assertTrue(waitFor(() -> lastDirection(back, one) == 1), "Forward sent no forward command: " + wire.since(back));
        assertTrue(waitFor(() -> forward.isSelected() && !reverse.isSelected()), "Forward is not shown pressed");
    }

    /**
     * Control with the arrows forces a direction; a plain arrow turns the train whichever way it faces.
     *
     * MUTATION: `forwardLoc()` in the Control+LEFT arm of `LocControlPanelKeyPressed`, and this fails.
     */
    @Test
    public void testTheArrowKeysTurnTheTrain() throws Exception
    {
        int mark = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK);

        assertTrue(waitFor(() -> !one.goingForward() && lastDirection(mark, one) == 2),
            "Control+Left did not send the train backwards: " + wire.since(mark));

        int again = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK);

        assertTrue(waitFor(() -> lastDirection(again, one) == 2), "a second Control+Left sent nothing");
        assertFalse(one.goingForward(), "a second Control+Left turned the train round: it forces reverse, it does not toggle");

        int right = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK);

        assertTrue(waitFor(() -> one.goingForward() && lastDirection(right, one) == 1),
            "Control+Right did not send the train forwards: " + wire.since(right));

        int plain = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_RIGHT);

        assertTrue(waitFor(() -> !one.goingForward() && lastDirection(plain, one) == 2),
            "a plain Right on a train facing forward did not turn it");
    }

    // Functions

    /**
     * A function button switches its function on, and a second click off, on the railway as well as the window.
     *
     * MUTATION: `!b.isSelected()` for the state in `ProcessFunction`, and this fails.
     */
    @Test
    public void testAFunctionButtonSwitchesItsFunction() throws Exception
    {
        JToggleButton f1 = (JToggleButton) field("F1");

        assertTrue(f1.isEnabled(), "precondition: F1 is greyed for an MFX locomotive");
        assertFalse(f1.isSelected(), "precondition: F1 is shown on with every function off");

        int mark = wire.mark();

        Components.press(f1);

        assertTrue(waitFor(() -> one.getF(1)), "clicking F1 did not switch f1 on");
        assertTrue(waitFor(() -> lastFunction(mark, one, 1) == 1), "clicking F1 sent no f1-on command: " + wire.since(mark));

        int off = wire.mark();

        Components.press(f1);

        assertTrue(waitFor(() -> !one.getF(1)), "a second click on F1 did not switch f1 off");
        assertTrue(waitFor(() -> lastFunction(off, one, 1) == 0), "a second click on F1 sent no f1-off command");

        assertTrue(wire.functionCommandsSince(mark, two.getIntUID()).isEmpty(),
            "F1 on the first train's panel switched a function of the second");
    }

    /**
     * Every kind of function shortcut reaches its own function: the back quote for f0, F1-F24, Alt plus a digit for
     * f0-f9, Control plus a digit for f10-f19, Control and Alt plus a digit for f20-f29, and the number pad.
     *
     * MUTATION: `switchF(12)` in the Control+1 arm of `LocControlPanelKeyPressed`, and this fails at Control+1.
     */
    @Test
    public void testEveryKindOfFunctionShortcutReachesItsFunction() throws Exception
    {
        int alt = InputEvent.ALT_DOWN_MASK;
        int ctrl = InputEvent.CTRL_DOWN_MASK;

        Object[][] shortcuts = {
            {"`", KeyEvent.VK_BACK_QUOTE, 0, 0},
            {"F1", KeyEvent.VK_F1, 0, 1},
            {"Alt+3", KeyEvent.VK_3, alt, 3},
            {"Alt+8", KeyEvent.VK_8, alt, 8},
            {"Numpad 5", KeyEvent.VK_NUMPAD5, 0, 5},
            {"Control+1", KeyEvent.VK_1, ctrl, 11},
            {"F12", KeyEvent.VK_F12, 0, 12},
            {"Control+9", KeyEvent.VK_9, ctrl, 19},
            {"Control+Alt+1", KeyEvent.VK_1, ctrl | alt, 21},
            {"F24", KeyEvent.VK_F24, 0, 24},
            {"Control+Alt+9", KeyEvent.VK_9, ctrl | alt, 29},
        };

        for (Object[] s : shortcuts)
        {
            String name = (String) s[0];
            int key = (Integer) s[1];
            int modifiers = (Integer) s[2];
            int fn = (Integer) s[3];

            int mark = wire.mark();

            Keys.press(keyboard(), key, modifiers);

            assertTrue(waitFor(() -> one.getF(fn)), name + " did not switch f" + fn + " on");
            assertTrue(waitFor(() -> lastFunction(mark, one, fn) == 1), name + " sent no f" + fn + "-on command: "
                + wire.functionCommandsSince(mark, one.getIntUID()));

            for (Sent sent : wire.functionCommandsSince(mark, one.getIntUID()))
            {
                assertEquals(sent.fifth(), fn, name + " also switched f" + sent.fifth());
            }

            int off = wire.mark();

            Keys.press(keyboard(), key, modifiers);

            assertTrue(waitFor(() -> !one.getF(fn)), name + " pressed again did not switch f" + fn + " off");
            assertTrue(waitFor(() -> lastFunction(off, one, fn) == 0), name + " pressed again sent no f" + fn + "-off command");
        }
    }

    // Speed

    /**
     * Each digit key sets the speed its button on screen sets, 1 stops the train, 0 is full speed, and the digits
     * between climb in order.
     *
     * MUTATION: `SixButtonActionPerformed` in the VK_5 arm of `LocControlPanelKeyPressed`, and this fails at 5.
     */
    @Test
    public void testTheDigitKeysSetTheSpeedTheirButtonsSet() throws Exception
    {
        String[] buttons = {"ZeroButton", "OneButton", "TwoButton", "ThreeButton", "FourButton", "FiveButton", "SixButton",
            "SevenButton", "EightButton", "NineButton"};

        int[] byKey = new int[10];

        for (int digit = 0; digit <= 9; digit++)
        {
            JButton button = (JButton) field(buttons[digit]);

            assertEquals(button.getText(), String.valueOf(digit), "precondition: the digit buttons are not where this expects");

            // The button first, to learn the speed it sets...
            one.setSpeed(5);

            final int before = one.getSpeed();

            Components.press(button);

            assertTrue(waitFor(() -> one.getSpeed() != before), "the " + digit + " button changed nothing");

            int byButton = one.getSpeed();

            // ...then the key, from somewhere else
            one.setSpeed(byButton == 50 ? 51 : 50);

            final int moved = one.getSpeed();

            int mark = wire.mark();

            Keys.press(keyboard(), KeyEvent.VK_0 + digit);

            assertTrue(waitFor(() -> one.getSpeed() != moved), "the " + digit + " key changed nothing");

            final int speed = one.getSpeed();

            byKey[digit] = speed;

            assertEquals(speed, byButton, "the " + digit + " key sets another speed than the " + digit
                + " button beside it");

            assertTrue(waitFor(() -> lastSpeed(mark, one) == speed), "the " + digit + " key sent no speed "
                + speed + ": " + wire.speedCommandsSince(mark, one.getIntUID()));
        }

        assertEquals(byKey[1], 0, "1 does not stop the train");
        assertEquals(byKey[0], 100, "0 is not full speed");

        for (int digit = 2; digit <= 9; digit++)
        {
            assertTrue(byKey[digit] > byKey[digit - 1], digit + " is not faster than " + (digit - 1));
        }

        assertTrue(byKey[0] > byKey[9], "0 is not faster than 9");
    }

    /**
     * Up and Down step the speed by the program's step, Control by one, and Alt by two steps; each step reaches the
     * railway.
     *
     * MUTATION: `SPEED_STEP * 3` for `SPEED_STEP * 2` in the Alt+UP arm, and this fails.
     */
    @Test
    public void testUpAndDownStepTheSpeed() throws Exception
    {
        int step = support.ProgramConstants.integer(TrainControlUI.class, "SPEED_STEP");

        one.setSpeed(50);

        Object[][] presses = {
            {"Up", KeyEvent.VK_UP, 0, step},
            {"Control+Up", KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK, 1},
            {"Alt+Up", KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK, 2 * step},
            {"Down", KeyEvent.VK_DOWN, 0, -step},
            {"Control+Down", KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK, -1},
            {"Alt+Down", KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK, -2 * step},
        };

        for (Object[] p : presses)
        {
            final int expected = one.getSpeed() + (Integer) p[3];

            int mark = wire.mark();

            Keys.press(keyboard(), (Integer) p[1], (Integer) p[2]);

            assertTrue(waitFor(() -> one.getSpeed() == expected), p[0] + " took the speed to " + one.getSpeed()
                + ", not " + expected);
            assertTrue(waitFor(() -> lastSpeed(mark, one) == expected), p[0] + " sent no speed " + expected);
        }
    }

    /**
     * Shift brings the shown train to speed 0, and leaves the other one running.
     *
     * MUTATION: `setLocSpeed(1)` in `ShiftButtonActionPerformed`, and this fails.
     */
    @Test
    public void testShiftBringsTheShownTrainToZero() throws Exception
    {
        one.setSpeed(44);
        two.setSpeed(44);

        int mark = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK);

        assertTrue(waitFor(() -> one.getSpeed() == 0), "Shift left the shown train at " + one.getSpeed());
        assertTrue(waitFor(() -> lastSpeed(mark, one) == 0), "Shift sent no speed 0: " + wire.since(mark));

        assertEquals(two.getSpeed(), 44, "Shift stopped a train that is not the one shown");
    }

    /**
     * Space stops the shown train with the stop command, and only that train: the other keeps running and no
     * emergency stop is sent.
     *
     * MUTATION: `stopAllLocs()` for `stopLoc()` in `SpacebarButtonActionPerformed`, and this fails.
     */
    @Test
    public void testSpaceStopsOnlyTheShownTrain() throws Exception
    {
        one.setSpeed(44);
        two.setSpeed(44);

        int mark = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_SPACE);

        assertTrue(waitFor(() -> one.getSpeed() == 0), "Space left the shown train at " + one.getSpeed());
        assertTrue(waitFor(() -> stopsTo(mark, one) == 1), "Space sent no stop command for the shown train: "
            + wire.since(mark));

        assertEquals(two.getSpeed(), 44, "Space stopped a train that is not the one shown");
        assertTrue(wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_HALT).isEmpty(),
            "Space stopped every train on the railway");
        assertEquals(stopsTo(mark, two), 0, "Space sent a stop to the other train");
    }

    /**
     * Enter stops every train on the railway: the emergency stop is sent and each running train is brought to 0.
     *
     * MUTATION: take the `stopAllLocs()` call out of `AltEmergencyStopActionPerformed`, and this fails.
     */
    @Test
    public void testEnterStopsEveryTrain() throws Exception
    {
        everyTrainStops(() -> Keys.press(keyboard(), KeyEvent.VK_ENTER), "Enter");
    }

    /**
     * The Enter button on screen does what the key does.
     *
     * MUTATION: as for the key.
     */
    @Test
    public void testTheEmergencyStopButtonStopsEveryTrain() throws Exception
    {
        JButton stop = (JButton) field("AltEmergencyStop");

        assertEquals(stop.getText(), I18n.t("ui.main.enterButton"), "precondition: this is not the emergency stop");

        everyTrainStops(() -> Components.press(stop), "the emergency stop button");
    }

    private static void everyTrainStops(Runnable press, String what) throws Exception
    {
        one.setSpeed(44);
        two.setSpeed(66);

        int mark = wire.mark();

        press.run();

        assertTrue(waitFor(() -> one.getSpeed() == 0 && two.getSpeed() == 0), what + " left the trains at "
            + one.getSpeed() + " and " + two.getSpeed());

        assertTrue(waitFor(() -> wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_HALT).size() == 1),
            what + " sent no emergency stop: " + wire.since(mark));

        assertTrue(waitFor(() -> lastSpeed(mark, two) == 0), what + " told the railway nothing about the train not shown");
    }

    // Power

    /**
     * Power OFF sends the power-off command; when the railway says the power is off, OFF grays and ON lights.  Power ON
     * then sends the power-on command, and the railway's answer grays it again.
     *
     * MUTATION: `go()` for `stop()` in `PowerOffActionPerformed`, and this fails.
     */
    @Test
    public void testPowerOffAndOnReachTheRailway() throws Exception
    {
        JButton off = (JButton) field("PowerOff");
        JButton on = (JButton) field("OnButton");

        assertTrue(waitFor(off::isEnabled), "precondition: Power OFF is greyed with the power on");

        int mark = wire.mark();

        Components.press(off);

        assertTrue(waitFor(() -> wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_STOP).size() == 1),
            "Power OFF sent no power-off command: " + wire.since(mark));
        assertTrue(wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_GO).isEmpty(), "Power OFF sent a power-on command");

        fromTheRailway(CS2Message.CMD_SYSSUB_STOP);

        assertTrue(waitFor(() -> !model.getPowerState()), "precondition: the railway's power-off was not heard");
        assertTrue(waitFor(() -> !off.isEnabled() && on.isEnabled()),
            "with the power off, Power OFF is still lit or Power ON is greyed");

        int back = wire.mark();

        Components.press(on);

        assertTrue(waitFor(() -> wire.systemCommandsSince(back, CS2Message.CMD_SYSSUB_GO).size() == 1),
            "Power ON sent no power-on command: " + wire.since(back));

        fromTheRailway(CS2Message.CMD_SYSSUB_GO);

        assertTrue(waitFor(() -> off.isEnabled() && !on.isEnabled()), "with the power back on, the buttons did not swap back");
    }

    /**
     * Alt+G turns the power on and Escape turns it off.
     *
     * MUTATION: `stop()` for `go()` in the Alt+G arm of `LocControlPanelKeyPressed`, and this fails.
     */
    @Test
    public void testAltGAndEscapeSwitchThePower() throws Exception
    {
        int mark = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_ESCAPE);

        assertTrue(waitFor(() -> wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_STOP).size() == 1),
            "Escape sent no power-off command: " + wire.since(mark));
        assertTrue(wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_GO).isEmpty(), "Escape sent a power-on command");

        fromTheRailway(CS2Message.CMD_SYSSUB_STOP);

        assertTrue(waitFor(() -> !model.getPowerState()), "precondition: the railway's power-off was not heard");

        int back = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_G, InputEvent.ALT_DOWN_MASK);

        assertTrue(waitFor(() -> wire.systemCommandsSince(back, CS2Message.CMD_SYSSUB_GO).size() == 1),
            "Alt+G sent no power-on command: " + wire.since(back));
        assertTrue(wire.systemCommandsSince(back, CS2Message.CMD_SYSSUB_STOP).isEmpty(), "Alt+G sent a power-off command");
    }

    // Sliders

    /**
     * Dragging the main slider sets the shown train to the slider's speed when it is let go.
     *
     * MUTATION: `setLocSpeed(SpeedSlider.getValue() / 2)` in `SpeedSliderDragged`, and this fails.
     */
    @Test
    public void testTheMainSliderSetsTheSpeedWhereItIsLetGo() throws Exception
    {
        JSlider slider = (JSlider) field("SpeedSlider");

        int mark = wire.mark();

        drag(slider, 60);

        int value = slider.getValue();

        assertTrue(value > 30, "precondition: the drag did not move the slider (" + value + ")");

        assertTrue(waitFor(() -> one.getSpeed() == value), "the main slider was let go at " + value
            + " and the train runs at " + one.getSpeed());
        assertTrue(waitFor(() -> lastSpeed(mark, one) == value), "the main slider sent no speed " + value);
        assertEquals(two.getSpeed(), 0, "the main slider moved a train that is not the one shown");
    }

    /**
     * A double right-click on the main slider turns the shown train round.
     *
     * MUTATION: `setLocSpeed(...)` for `switchDirection()` in `MainSpeedSliderClicked`, and this fails.
     */
    @Test
    public void testADoubleRightClickOnTheMainSliderTurnsTheTrain() throws Exception
    {
        JSlider slider = (JSlider) field("SpeedSlider");

        showing(slider);

        int mark = wire.mark();

        doubleRightClick(slider);

        assertTrue(waitFor(() -> !one.goingForward()), "a double right-click on the main slider did not turn the train");
        assertTrue(waitFor(() -> lastDirection(mark, one) == 2), "the turn reached no railway: " + wire.since(mark));
    }

    /**
     * The slider under a key drives that key's train, whichever train is shown.
     *
     * MUTATION: drive `activeLoc` instead of the slider's own locomotive in `updateSliderSpeed`, and this fails.
     */
    @Test
    public void testAKeysSliderDrivesItsOwnTrain() throws Exception
    {
        JSlider slider = sliderOf(twoKey);

        int mark = wire.mark();

        drag(slider, 60);

        int value = slider.getValue();

        assertTrue(value > 30, "precondition: the drag did not move the slider (" + value + ")");

        assertTrue(waitFor(() -> two.getSpeed() == value), "S's slider was let go at " + value + " and its train runs at "
            + two.getSpeed());
        assertTrue(waitFor(() -> lastSpeed(mark, two) == value), "S's slider sent no speed " + value + " to its train");

        assertEquals(one.getSpeed(), 0, "S's slider moved the train shown, which is on A");
        assertTrue(wire.speedCommandsSince(mark, one.getIntUID()).isEmpty(), "S's slider sent a speed to the train on A");
    }

    /**
     * A double right-click on a key's slider stops that key's train and turns it round.
     *
     * MUTATION: take `l.switchDirection()` out of `sliderClickedSynced`, and this fails.
     */
    @Test
    public void testADoubleRightClickOnAKeysSliderStopsAndTurnsItsTrain() throws Exception
    {
        two.setSpeed(44);

        JSlider slider = sliderOf(twoKey);

        showing(slider);

        int mark = wire.mark();

        doubleRightClick(slider);

        assertTrue(waitFor(() -> two.getSpeed() == 0), "a double right-click on S's slider left its train at " + two.getSpeed());
        assertTrue(waitFor(() -> !two.goingForward()), "a double right-click on S's slider did not turn its train");
        assertTrue(waitFor(() -> lastDirection(mark, two) == 2 && lastSpeed(mark, two) == 0),
            "the stop and the turn did not both reach the railway: " + wire.since(mark));
        assertTrue(one.goingForward(), "the train shown, on A, was turned");
    }

    // Choosing a train, the keyboard pages and the tabs

    /**
     * Clicking a letter key on screen shows the train on it, and marks that key as the one in use.
     *
     * MUTATION: show `this.currentButton` instead of the key clicked in `LetterButtonPressed`, and this fails.
     */
    @Test
    public void testClickingALetterKeyShowsItsTrain() throws Exception
    {
        JButton s = letters().get(twoKey);

        Components.press(s);

        assertTrue(waitFor(() -> ui.getActiveLoc() == two), "clicking S did not show the train on it");
        assertTrue(waitFor(() -> !s.isEnabled()), "the key clicked is not marked as the one in use");

        JButton a = letters().get(oneKey);

        Components.press(a);

        assertTrue(waitFor(() -> ui.getActiveLoc() == one), "clicking A did not show the train on it");

        int mark = wire.mark();

        Keys.press(keyboard(), KeyEvent.VK_5);

        assertTrue(waitFor(() -> one.getSpeed() > 0 && lastSpeed(mark, one) == one.getSpeed()),
            "after A was clicked, a speed key did not drive A's train");
        assertEquals(two.getSpeed(), 0, "after A was clicked, a speed key drove S's train");
    }

    /**
     * The 16 tabs are ranges of 128 addresses, 1-128 up to 1921-2048, and each shows its first and last address on its
     * first and last key; the + and - keys move between them, and Control moves four at a time.
     *
     * MUTATION: `keyboardNumber + 2` in `NextKeyboardActionPerformed`, and this fails.
     */
    @Test
    public void testTheAccessoryTabsAreRangesOfAddresses() throws Exception
    {
        AccessoryKeyboard keys = keys();

        assertEquals(keys.tab(1).getText(), "1-128", "the first tab is not named for its range");
        assertEquals(keys.tab(2).getText(), "129-256", "the second tab is not named for its range");
        assertEquals(keys.tab(16).getText(), "1921-2048", "the last tab is not named for its range");

        for (int page : new int[] {1, 2, 9, 16})
        {
            goToPage(page);

            assertEquals(Components.onTheEventThread(() -> keys.numberOf(1)), String.valueOf(128 * (page - 1) + 1),
                "tab " + page + " does not start where its range does");
            assertEquals(Components.onTheEventThread(() -> keys.numberOf(128)), String.valueOf(128 * page),
                "tab " + page + " does not end where its range does");
        }

        assertEquals(Components.onTheEventThread(() -> keys.numberOf(128)), "2048", "2048 is not on the last tab");

        // On the first tab, where + and - mean pages: on the diagram tab they step through its layouts
        javax.swing.JTabbedPane main = (javax.swing.JTabbedPane) keyboard();
        int was = Components.onTheEventThread(main::getSelectedIndex);

        Components.onTheEventThread(() ->
        {
            main.setSelectedIndex(0);

            return null;
        });

        try
        {
            goToPage(1);

            Keys.press(keyboard(), KeyEvent.VK_EQUALS);

            assertTrue(waitFor(() -> keys.getPage() == 2), "+ did not bring the second tab forward");
            assertEquals(Components.onTheEventThread(() -> keys.numberOf(1)), "129", "+ landed on the wrong range");

            Keys.press(keyboard(), KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK);

            assertTrue(waitFor(() -> keys.getPage() == 6), "Control + did not move four tabs");

            Keys.press(keyboard(), KeyEvent.VK_MINUS);

            assertTrue(waitFor(() -> keys.getPage() == 5), "- did not bring the tab before forward");

            Keys.press(keyboard(), KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK);

            assertTrue(waitFor(() -> keys.getPage() == 1), "Control - did not move four tabs back");
            assertTrue(Components.onTheEventThread(() -> keys.tab(1).isSelected()), "the tab for the range shown is not the one chosen");

            Keys.press(keyboard(), KeyEvent.VK_MINUS);

            assertEquals(keys.getPage(), 1, "- went past the first tab");
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                main.setSelectedIndex(was);

                return null;
            });
        }
    }

    /**
     * Backspace brings the next tab of the keyboard forward, and Alt+Backspace the one before.
     *
     * MUTATION: `getSelectedIndex() + 2` in the BACK_SPACE arm of `LocControlPanelKeyPressed`, and this fails.
     */
    @Test
    public void testBackspaceCyclesTheKeyboardTabs() throws Exception
    {
        javax.swing.JTabbedPane tabs = (javax.swing.JTabbedPane) keyboard();

        int was = Components.onTheEventThread(tabs::getSelectedIndex);

        int expected = Components.onTheEventThread(() ->
        {
            int at = was;

            do
            {
                at = (at + 1) % tabs.getTabCount();
            }
            while (!tabs.isEnabledAt(at));

            return at;
        });

        try
        {
            Keys.press(tabs, KeyEvent.VK_BACK_SPACE);

            assertEquals((int) Components.onTheEventThread(tabs::getSelectedIndex), expected,
                "Backspace did not bring the next tab forward");

            Keys.press(tabs, KeyEvent.VK_BACK_SPACE, InputEvent.ALT_DOWN_MASK);

            assertEquals((int) Components.onTheEventThread(tabs::getSelectedIndex), was,
                "Alt+Backspace did not bring the tab before forward again");
        }
        finally
        {
            Components.onTheEventThread(() ->
            {
                tabs.setSelectedIndex(was);

                return null;
            });
        }
    }

    /**
     * Locomotives > Turn Off All Functions turns off what is on, on every train, and tells the railway.
     *
     * MUTATION: take `this.model.allFunctionsOff()` out of `turnOffFunctionsMenuItemActionPerformed`, and this fails.
     */
    @Test
    public void testTurnOffAllFunctionsReachesEveryTrain() throws Exception
    {
        one.setF(1, true);
        two.setF(2, true);

        AbstractButton item = Components.withText(ui, I18n.t("ui.main.toolbar.turnOffFunctions"));

        assertNotNull(item, "there is no Turn Off All Functions in the menus");

        int mark = wire.mark();

        SwingUtilities.invokeLater(item::doClick);

        assertTrue(Components.waitFor(() -> !one.getF(1) && !two.getF(2), WAIT * 4),
            "Turn Off All Functions left a train's function on");
        assertTrue(waitFor(() -> lastFunction(mark, one, 1) == 0 && lastFunction(mark, two, 2) == 0),
            "Turn Off All Functions did not tell the railway: " + wire.since(mark));
    }

    // The accessory keyboard

    /**
     * A click on a half sends exactly that color, on MM2 and on DCC, and the half then lit is the one clicked.  Red is
     * the turned position, which goes out as 0; green goes out as 1.
     *
     * MUTATION: `!red` for the state in `keyboardKeyClicked`'s `setAccessoryState`, and this fails.
     */
    @Test
    public void testAClickOnAHalfSendsThatColor() throws Exception
    {
        goToPage(1);

        try
        {
            for (boolean dcc : new boolean[] {false, true})
            {
                chooseProtocol(dcc);

                int address = 5;

                for (boolean red : new boolean[] {true, false, false, true})
                {
                    List<Integer> sent = sentWhile(address, () -> click(address, red));

                    assertEquals(sent, java.util.Collections.singletonList(red ? 0 : 1),
                        (dcc ? "DCC" : "MM2") + ": a click on the " + (red ? "red" : "green")
                        + " half did not send exactly " + (red ? "red" : "green"));
                    assertTrue(waitFor(() -> lit(address, red) && !lit(address, !red)),
                        "the half clicked is not the one lit");
                    assertEquals(model.getAccessoryState(address, keyboardProtocol()), red,
                        "the accessory is not in the position clicked");
                }
            }
        }
        finally
        {
            chooseProtocol(false);
        }
    }

    /**
     * Clicking the lit half again sends that color again, directly: one command, and no other color before it.  The
     * station's count of actuations does not rise, since the position did not change.
     *
     * MUTATION: `if (key > 0 && this.keyboardKeys.isLit(key, red)) return;` at the top of `keyboardKeyClicked`, and
     * this fails.
     */
    @Test
    public void testClickingTheLitHalfAgainSendsAgainWithNoGreenFirst() throws Exception
    {
        goToPage(1);

        int address = 7;

        assertEquals(sentWhile(address, () -> click(address, true)), java.util.Collections.singletonList(0),
            "precondition: the first click did not send red");
        assertTrue(waitFor(() -> lit(address, true)), "precondition: red is not lit");

        MarklinAccessory acc = (MarklinAccessory) model.getAccessoryByAddressIfPresent(address, keyboardProtocol());

        assertNotNull(acc, "precondition: the click made no accessory");

        int before = acc.getNumActuations();

        model.receiveMessage(support.Packets.accessoryEcho(acc, true));

        // The station's echo is read on its own thread: wait for it to count before counting again
        assertTrue(waitFor(() -> acc.getNumActuations() > before), "precondition: the first echo did not count");

        Components.settle();

        int counted = acc.getNumActuations();

        List<Integer> sent = sentWhile(address, () -> click(address, true));

        assertEquals(sent, java.util.Collections.singletonList(0),
            "re-firing red did not send exactly one red command, and no other color");

        model.receiveMessage(support.Packets.accessoryEcho(acc, true));

        Thread.sleep(500);

        assertEquals(acc.getNumActuations(), counted, "re-firing red counted as an actuation");
        assertTrue(lit(address, true), "red is not lit after it was fired again");

        click(address, false);
    }

    /**
     * An echo from the station repaints its key, and one for an address on another tab shows when that tab is come
     * back to.
     *
     * MUTATION: in `repaintSwitches`, pass `false` for the state to `setKey`, and this fails.
     */
    @Test
    public void testAnEchoRepaintsTheKeyEvenOnAnotherTab() throws Exception
    {
        goToPage(2);

        int address = 130;

        sentWhile(address, () -> click(address, true));

        assertTrue(waitFor(() -> lit(address, true)), "precondition: red is not lit");

        MarklinAccessory acc = (MarklinAccessory) model.getAccessoryByAddressIfPresent(address, keyboardProtocol());

        assertNotNull(acc, "precondition: the click made no accessory");

        model.receiveMessage(support.Packets.accessoryEcho(acc, false));

        assertTrue(waitFor(() -> lit(address, false)), "the station put it green and its key still shows red");

        goToPage(1);

        model.receiveMessage(support.Packets.accessoryEcho(acc, true));

        Components.settle();

        goToPage(2);

        assertTrue(waitFor(() -> lit(address, true) && !lit(address, false)),
            "the station threw it while another tab was showing, and its key shows green on coming back");

        click(address, false);
    }

    /**
     * Address 2048 is the last key of the last tab, DCC only, and a click on it goes to the railway.  The MM2 addresses
     * end at 320: the keys past it do nothing, and a tab wholly past it is DCC.
     *
     * MUTATION: `page * KEYS` for `(page - 1) * KEYS` in `AccessoryKeyboard.rangeStart`, and this fails.
     */
    @Test
    public void testAddress2048IsOnTheLastTabAndMM2StopsAt320() throws Exception
    {
        AccessoryKeyboard keys = keys();

        try
        {
            goToPage(16);

            assertTrue(waitFor(() -> keyboardProtocol() == Accessory.accessoryDecoderType.DCC),
                "a tab past the MM2 addresses is not DCC");

            int mark = wire.mark();

            click(2048, true);

            assertTrue(waitFor(() -> lastAccessory(mark, 2048) == 0), "a click on 2048 sent nothing: " + wire.since(mark));
            assertTrue(waitFor(() -> lit(2048, true)), "2048 does not show red");

            click(2048, false);

            goToPage(3);
            chooseProtocol(false);

            assertTrue(waitFor(() -> keyboardProtocol() == Accessory.accessoryDecoderType.MM2), "MM2 was not chosen");
            assertTrue(Components.onTheEventThread(() -> keys.half(64, true).isEnabled()), "MM2 320 is not usable");
            assertFalse(Components.onTheEventThread(() -> keys.half(65, true).isEnabled()), "MM2 321 is usable");

            int quiet = wire.mark();

            click(321, true);

            Thread.sleep(300);

            assertTrue(wire.accessoryCommandsSince(quiet, MarklinAccessory.UIDfromAddress(320, keyboardProtocol())).isEmpty(),
                "a click past the MM2 addresses sent a command");

            chooseProtocol(true);

            assertTrue(Components.onTheEventThread(() -> keys.half(65, true).isEnabled()), "DCC 321 is not usable");
        }
        finally
        {
            chooseProtocol(false);
        }
    }

    /**
     * A page number past the last tab (the keyboard once had 32 pages) lands on the last tab.
     *
     * MUTATION: take the `Math.min` out of `switchKeyboard`, and this fails.
     */
    @Test
    public void testAPagePastTheLastTabLandsOnTheLast() throws Exception
    {
        AccessoryKeyboard keys = keys();

        Components.onTheEventThread(() ->
        {
            try
            {
                java.lang.reflect.Method m = TrainControlUI.class.getDeclaredMethod("switchKeyboard", int.class);

                m.setAccessible(true);
                m.invoke(ui, 32);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }

            return null;
        });

        assertTrue(waitFor(() -> keys.getPage() == 16), "page 32 did not land on the last tab");
        assertEquals(Components.onTheEventThread(() -> keys.numberOf(128)), "2048", "the last tab does not end at 2048");
        assertEquals((int) (Integer) field("keyboardNumber"), 16, "the window's page is not the last tab");

        goToPage(1);
    }

    /**
     * The lit half is a clear color and the other is very light gray; pointed at, the gray half shows a pale hint of its
     * own color.  Painted, since the look is the claim.
     *
     * MUTATION: swap the two hint colors in `AccessoryKeyboard.lookOf`, and this fails.
     */
    @Test
    public void testTheLitHalfIsColoredAndTheOtherIsLightGrayWithAHint() throws Exception
    {
        goToPage(1);
        click(9, true);

        assertTrue(waitFor(() -> lit(9, true)), "precondition: red is not lit");

        AccessoryKeyboard keys = keys();

        java.awt.Color lightGray = new java.awt.Color(0xF0F0F0);

        assertEquals(paintedColor(keys.half(9, true)), new java.awt.Color(0xFF6666), "the lit red half is not the mockup's red");
        assertEquals(paintedColor(keys.half(9, false)), lightGray, "the half not lit is not very light gray");

        support.Mouse.enter(keys.half(9, false));

        assertEquals(paintedColor(keys.half(9, false)), new java.awt.Color(0xC8EFC8),
            "pointing at the gray half shows no pale hint of green");

        support.Mouse.exit(keys.half(9, false));

        assertEquals(paintedColor(keys.half(9, false)), lightGray, "the hint stayed after the pointer left");

        click(9, false);

        assertTrue(waitFor(() -> lit(9, false)), "green is not lit");
        assertEquals(paintedColor(keys.half(9, false)), new java.awt.Color(0x58C858), "the lit green half is not the mockup's green");
        assertEquals(paintedColor(keys.half(9, true)), lightGray, "the red half stayed lit after green was clicked");

        support.Mouse.enter(keys.half(9, true));

        assertEquals(paintedColor(keys.half(9, true)), new java.awt.Color(0xFBCACA), "pointing at the gray half shows no pale hint of red");

        support.Mouse.exit(keys.half(9, true));
    }

    /** The color at the middle of a half as it paints. */
    private static java.awt.Color paintedColor(AbstractButton half) throws Exception
    {
        return Components.onTheEventThread(() ->
        {
            half.setSize(22, 26);

            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(22, 26, java.awt.image.BufferedImage.TYPE_INT_RGB);

            half.paint(image.getGraphics());

            return new java.awt.Color(image.getRGB(11, 13));
        });
    }

    /**
     * With the power off, a click asks first; Cancel leaves both halves as they were and sends nothing.
     *
     * MUTATION: let a Cancel through in `keyboardSwitchAllowed` (answer `0` for any), and this fails.
     */
    @Test
    public void testWithThePowerOffCancelLeavesTheAccessoryAlone() throws Exception
    {
        int address = powerOffOnTheFirstTab();

        boolean was = lit(address, true);
        boolean state = model.getAccessoryState(address, keyboardProtocol());

        int mark = wire.mark();

        try (Answering answering = Answering.start(asked -> Answering.press(I18n.t("ui.cancel"))))
        {
            click(address, !was);

            assertTrue(answering.awaitAsked(1, WAIT), "with the power off, the accessory key asked nothing");
            assertEquals(answering.asked().get(0), I18n.t("layout.ui.confirmAccessorySwitchPowerOff"),
                "the accessory key asked something else");
        }

        Components.settle();

        assertEquals(lit(address, true), was, "Cancel changed the red half");
        assertEquals(lit(address, false), !was, "Cancel changed the green half");
        assertEquals(model.getAccessoryState(address, keyboardProtocol()), state, "Cancel switched the accessory");

        Thread.sleep(300);

        assertTrue(wire.accessoryCommandsSince(mark, uidOf(address)).isEmpty(), "Cancel sent the accessory's command");
        assertTrue(wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_GO).isEmpty(), "Cancel turned the power on");
    }

    /**
     * With the power off, "Proceed" switches the accessory and leaves the power off.
     *
     * MUTATION: answer 1 as a refusal in `keyboardSwitchAllowed`, and this fails.
     */
    @Test
    public void testWithThePowerOffProceedSwitchesAndLeavesThePowerOff() throws Exception
    {
        int address = powerOffOnTheFirstTab();

        boolean was = lit(address, true);

        int mark = wire.mark();

        pressAnsweringWith(address, !was, I18n.t("layout.ui.optionProceed"));

        assertTrue(waitFor(() -> model.getAccessoryState(address, keyboardProtocol()) == !was),
            "Proceed did not switch the accessory");
        assertTrue(waitFor(() -> lastAccessory(mark, address) == (was ? 1 : 0)), "Proceed sent no command: " + wire.since(mark));
        assertTrue(wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_GO).isEmpty(), "Proceed turned the power on");
    }

    /**
     * With the power off, "Turn the power on and proceed" sends the power-on command, and the accessory's after it.
     *
     * MUTATION: take `this.model.go()` out of `keyboardKeyClicked`, and this fails.
     */
    @Test
    public void testWithThePowerOffTurningItOnComesFirst() throws Exception
    {
        int address = powerOffOnTheFirstTab();

        boolean was = lit(address, true);

        int mark = wire.mark();

        pressAnsweringWith(address, !was, I18n.t("layout.ui.optionTurnPowerOnAndProceed"));

        assertTrue(waitFor(() -> lastAccessory(mark, address) == (was ? 1 : 0)), "the accessory's command was not sent");

        List<Sent> sent = wire.sentSince(mark);

        int go = -1;
        int switched = -1;

        for (int i = 0; i < sent.size(); i++)
        {
            Sent s = sent.get(i);

            if (go < 0 && s.command == CS2Message.CMD_SYSTEM && s.fifth() == CS2Message.CMD_SYSSUB_GO) go = i;

            if (switched < 0 && s.command == CS2Message.CMD_ACC_SWITCH && s.uid() == uidOf(address)) switched = i;
        }

        assertTrue(go >= 0, "the power was not turned on: " + sent);
        assertTrue(go < switched, "the accessory was sent before the power came on: " + sent);
    }

    /**
     * What an action sends to one accessory, as the station would be sent it, read at `exec`: the log collapses a line
     * repeated twice running, so it cannot count a re-fire.  Waits for the first command, then a moment for any more.
     */
    private static List<Integer> sentWhile(int address, ThrowingRunnable action) throws Exception
    {
        final List<CS2Message> seen = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        model.setSentMessageObserver(seen::add);

        try
        {
            action.run();

            waitFor(() -> !support.AccessoryCommands.positionsTo(seen, uidOf(address)).isEmpty());

            Thread.sleep(300);
        }
        finally
        {
            model.setSentMessageObserver(null);
        }

        return support.AccessoryCommands.positionsTo(seen, uidOf(address));
    }

    private interface ThrowingRunnable
    {
        void run() throws Exception;
    }

    /** A click on a half of the key for an address on the tab showing, as the operator's click does. */
    private static void click(int address, boolean red) throws Exception
    {
        AccessoryKeyboard keys = keys();
        int key = Components.onTheEventThread(() -> keys.keyOf(address));

        assertTrue(key > 0, "address " + address + " is not on the tab showing");

        Components.press(keys.half(key, red));
        Components.settle();
    }

    /** Whether the red or the green half of an address's key is the lit one. */
    private static boolean lit(int address, boolean red)
    {
        AccessoryKeyboard keys = keys0();

        return Components.onTheEventThread(() -> keys.isLit(keys.keyOf(address), red));
    }

    private static void goToPage(int page) throws Exception
    {
        AccessoryKeyboard keys = keys();

        Components.press(keys.tab(page));

        assertTrue(waitFor(() -> keys.getPage() == page), "tab " + page + " did not come forward");

        Components.settle();
    }

    private static void chooseProtocol(boolean dcc) throws Exception
    {
        Components.press((AbstractButton) field(dcc ? "DCC" : "MM2"));
        Components.settle();
    }

    private static AccessoryKeyboard keys() throws Exception
    {
        return (AccessoryKeyboard) field("keyboardKeys");
    }

    private static AccessoryKeyboard keys0()
    {
        return (AccessoryKeyboard) field0("keyboardKeys");
    }

    /** The key clicked and the question it asks answered by pressing the named option; fails when nothing is asked. */
    private static void pressAnsweringWith(int address, boolean red, String option) throws Exception
    {
        try (Answering answering = Answering.start(asked -> Answering.press(option)))
        {
            click(address, red);

            assertTrue(answering.awaitAsked(1, WAIT), "with the power off, the accessory key asked nothing");
        }
    }

    /** The power off, as the railway reports it, the first tab showing; answers an address on it. */
    private static int powerOffOnTheFirstTab() throws Exception
    {
        goToPage(1);

        fromTheRailway(CS2Message.CMD_SYSSUB_STOP);

        assertTrue(waitFor(() -> !model.getPowerState()), "precondition: the railway's power-off was not heard");

        Components.settle();

        return 1;
    }

    /** A system packet as a Central Station sends one back, read as every packet from it is. */
    private static void fromTheRailway(int subCommand)
    {
        CS2Message command = new CS2Message(CS2Message.CMD_SYSTEM, new byte[] {0, 0, 0, 0, (byte) subCommand});

        model.receiveMessage(new CS2Message(command.getCommand(), command.getHash(), true, command.getData()));
    }

    /** The accessory keyboard's protocol, as its radio buttons show it. */
    private static Accessory.accessoryDecoderType keyboardProtocol()
    {
        JRadioButton dcc = (JRadioButton) field0("DCC");
        JRadioButton mm2 = (JRadioButton) field0("MM2");

        return Accessory.stringToAccessoryDecoderType(dcc.isSelected() ? dcc.getText() : mm2.getText());
    }

    private static int uidOf(int address)
    {
        return MarklinAccessory.UIDfromAddress(address - 1, keyboardProtocol());
    }

    /** The last position sent to an accessory since a mark: 0 turned, 1 straight, -1 none. */
    private static int lastAccessory(int mark, int address)
    {
        List<Sent> sent = wire.accessoryCommandsSince(mark, uidOf(address));

        return sent.isEmpty() ? -1 : sent.get(sent.size() - 1).fifth();
    }

    private static int lastDirection(int mark, MarklinLocomotive loc)
    {
        List<Sent> sent = wire.directionCommandsSince(mark, loc.getIntUID());

        return sent.isEmpty() ? -1 : sent.get(sent.size() - 1).fifth();
    }

    private static int lastSpeed(int mark, MarklinLocomotive loc)
    {
        List<Sent> sent = wire.speedCommandsSince(mark, loc.getIntUID());

        return sent.isEmpty() ? -1 : sent.get(sent.size() - 1).speed();
    }

    /** The last state sent for one function since a mark: 1 on, 0 off, -1 none. */
    private static int lastFunction(int mark, MarklinLocomotive loc, int fn)
    {
        int last = -1;

        for (Sent s : wire.functionCommandsSince(mark, loc.getIntUID()))
        {
            if (s.fifth() == fn) last = s.sixth();
        }

        return last;
    }

    /** How many stop commands one train has been sent since a mark. */
    private static int stopsTo(int mark, MarklinLocomotive loc)
    {
        int count = 0;

        for (Sent s : wire.systemCommandsSince(mark, CS2Message.CMD_SYSSUB_TRAINSTOP))
        {
            if (s.uid() == loc.getIntUID()) count++;
        }

        return count;
    }

    /** The tabbed pane that holds the keyboard, where the window's key map listens. */
    private static Component keyboard()
    {
        return (Component) field0("KeyboardTab");
    }

    private static Object field(String name) throws Exception
    {
        Field f = TrainControlUI.class.getDeclaredField(name);

        f.setAccessible(true);

        return f.get(ui);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, JButton> letters() throws Exception
    {
        return (Map<Integer, JButton>) field("buttonMapping");
    }

    @SuppressWarnings("unchecked")
    private static JSlider sliderOf(int key) throws Exception
    {
        return ((Map<JButton, JSlider>) field("sliderMapping")).get(letters().get(key));
    }

    /** Puts a locomotive on a letter of the page showing, as the selector does once the letter is chosen. */
    private static void put(int key, Locomotive loc) throws Exception
    {
        JButton button = letters().get(key);

        Keys.press(keyboard(), key);

        assertTrue(waitFor(() -> field0("currentButton") == button), "precondition: the letter did not choose its key");

        Components.onTheEventThread(() ->
        {
            if (ui.getButtonLocomotive(button) != loc) ui.mapLocToCurrentButton(loc.getName());

            return null;
        });

        assertTrue(waitFor(() -> ui.getButtonLocomotive(button) == loc), "precondition: the locomotive is not on its key");
    }

    /** Shows the train on a letter, by pressing the letter. */
    private static void show(int key, Locomotive loc) throws Exception
    {
        Keys.press(keyboard(), key);

        assertTrue(waitFor(() -> ui.getActiveLoc() == loc), "precondition: pressing the letter did not show its train");

        Components.settle();
    }

    private static Object field0(String name)
    {
        try
        {
            return field(name);
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static boolean waitFor(BooleanSupplier condition)
    {
        return Components.waitFor(condition, WAIT);
    }

    /** Brings the tabs holding a component forward and waits until it is on screen with a size. */
    private static void showing(Component c)
    {
        Components.select(c);

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(() -> c.isShowing() && c.getWidth() > 0), WAIT),
            "precondition: " + c.getName() + " never came on screen");
    }

    /**
     * Drags a slider's thumb to a value with the left button, as a hand does: pressed on the thumb, dragged, let go.
     * The slider's own look and feel moves the value; the program's listener hears the release.
     */
    private static void drag(final JSlider slider, final int toValue) throws Exception
    {
        showing(slider);

        Components.onTheEventThread(() ->
        {
            BasicSliderUI look = (BasicSliderUI) slider.getUI();

            Field thumbField = BasicSliderUI.class.getDeclaredField("thumbRect");

            thumbField.setAccessible(true);

            java.awt.Rectangle thumb = (java.awt.Rectangle) thumbField.get(look);

            boolean across = slider.getOrientation() == JSlider.HORIZONTAL;

            int fromX = thumb.x + thumb.width / 2;
            int fromY = thumb.y + thumb.height / 2;

            // Protected in BasicSliderUI: where the look and feel itself puts a value
            java.lang.reflect.Method at = BasicSliderUI.class.getDeclaredMethod(
                across ? "xPositionForValue" : "yPositionForValue", int.class);

            at.setAccessible(true);

            int toX = across ? (Integer) at.invoke(look, toValue) : fromX;
            int toY = across ? fromY : (Integer) at.invoke(look, toValue);

            long now = System.currentTimeMillis();

            slider.dispatchEvent(new MouseEvent(slider, MouseEvent.MOUSE_PRESSED, now, InputEvent.BUTTON1_DOWN_MASK,
                fromX, fromY, 1, false, MouseEvent.BUTTON1));
            slider.dispatchEvent(new MouseEvent(slider, MouseEvent.MOUSE_DRAGGED, now + 20, InputEvent.BUTTON1_DOWN_MASK,
                (fromX + toX) / 2, (fromY + toY) / 2, 0, false, MouseEvent.NOBUTTON));
            slider.dispatchEvent(new MouseEvent(slider, MouseEvent.MOUSE_DRAGGED, now + 40, InputEvent.BUTTON1_DOWN_MASK,
                toX, toY, 0, false, MouseEvent.NOBUTTON));
            slider.dispatchEvent(new MouseEvent(slider, MouseEvent.MOUSE_RELEASED, now + 60, 0,
                toX, toY, 1, false, MouseEvent.BUTTON1));

            return null;
        });

        Components.settle();
    }

    /**
     * The second click of a right-button double click: what the program's handlers read.  The presses and releases
     * around it are left out, because on a key's slider each release sets the speed again, on a thread of its own.
     */
    private static void doubleRightClick(final Component on)
    {
        Components.onTheEventThread(() ->
        {
            on.dispatchEvent(new MouseEvent(on, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                on.getWidth() / 2, on.getHeight() / 2, 2, false, MouseEvent.BUTTON3));

            return null;
        });

        Components.settle();
    }
}
