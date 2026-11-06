package ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.udp.CS2Message;
import support.Answering;
import support.Components;
import support.Keys;
import support.WireRecorder;

/**
 * One representative of every family of main-window shortcut, pressed as a real key on the window, asserting what the
 * train, the button or the tab now is (OB-331).  Black box: it names no row, so it runs unchanged on the if/else
 * chain the table replaced and must pass on both.
 *
 * Shortcuts that open a dialog (Control+A, D, F, L, N, R) or write a preference (Control+M) are not pressed here.
 */
public class testEveryKindOfShortcutOnTheWindow
{
    private static final int ALT = InputEvent.ALT_DOWN_MASK;
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;

    private static final String ONE = "C8 shortcut one";
    private static final String TWO = "C8 shortcut two";
    private static final String DOOMED = "C8 shortcut doomed";

    private static MarklinLocomotive one;
    private static MarklinLocomotive two;

    private static WireRecorder wire;
    private static TrainControlUI ui;
    private static MarklinControlStation model;
    private static support.LayoutSandbox sandbox;

    private static final long WAIT = 5000;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("a keyboard needs a window");

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedWithWindowInDebug();

        wire = WireRecorder.open();

        one = model.newMFXLocomotive(ONE, 3151);
        two = model.newMFXLocomotive(TWO, 3152);

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (wire != null) wire.close();

            if (ui != null)
            {
                Components.onTheEventThread(() ->
                {
                    ui.stopDrivingTrainsFromTheWholeWindow();
                    ui.dispose();

                    return null;
                });
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /** First page and keyboard, the two trains on A and S, D empty, all standing with functions off, A showing. */
    @BeforeMethod
    public void standStill() throws Exception
    {
        ui.setHoveredDiagramTile(null, -1, -1);

        ui.clearCopyTarget();

        Keys.press(keyboard(), KeyEvent.VK_COMMA, ALT);
        Keys.press(keyboard(), KeyEvent.VK_MINUS, CTRL);

        tabs().setSelectedIndex(0);

        put(KeyEvent.VK_D, null);
        put(KeyEvent.VK_S, two);
        put(KeyEvent.VK_A, one);

        for (MarklinLocomotive loc : new MarklinLocomotive[] {one, two})
        {
            loc.setSpeed(0);
            loc.setDirection(Locomotive.locDirection.DIR_FORWARD);
            loc.functionsOff();
        }

        Components.settle();
    }

    /**
     * Alt letters: Alt+S and Alt+U save the shown train's functions and speed, Alt+O switches its functions off, Alt+P and
     * Alt+V bring back what was saved, and Alt+G sends the power on.
     *
     * MUTATION: in the row "functions off", `applyPreferredFunctions`, and this fails.
     */
    @Test
    public void testAltLetters() throws Exception
    {
        one.setF(3, true);
        two.setF(3, true);
        one.setSpeed(40);

        Keys.press(keyboard(), KeyEvent.VK_S, ALT);

        assertTrue(waitFor(() -> one.getPreferredFunctions()[3]), "Alt+S did not save the function");

        Keys.press(keyboard(), KeyEvent.VK_U, ALT);

        assertTrue(waitFor(() -> one.getPreferredSpeed() == 40), "Alt+U did not save the speed");

        Keys.press(keyboard(), KeyEvent.VK_O, ALT);

        assertTrue(waitFor(() -> !one.getF(3)), "Alt+O left the shown train's function on");
        assertTrue(two.getF(3), "Alt+O reached the other train");

        Keys.press(keyboard(), KeyEvent.VK_P, ALT);

        assertTrue(waitFor(() -> one.getF(3)), "Alt+P did not bring back the function Alt+S saved");

        one.setSpeed(0);

        Keys.press(keyboard(), KeyEvent.VK_V, ALT);

        assertTrue(waitFor(() -> one.getSpeed() == 40), "Alt+V did not bring back the speed Alt+U saved: "
            + one.getSpeed());

        sendsOnce(KeyEvent.VK_G, ALT, CS2Message.CMD_SYSSUB_GO);
    }

    /**
     * Control letters: Control+C and Control+V copy a train to another button, Control+X cuts one off its button, and
     * Control+S swaps.  Control+V with nothing copied changes nothing.
     *
     * MUTATION: in the row "paste onto the button", `doPaste(key.ui.currentButton, false, true)`, and this fails at the
     * copy's source.
     */
    @Test
    public void testControlLetters() throws Exception
    {
        JButton before = currentButton();

        Keys.press(keyboard(), KeyEvent.VK_V, CTRL);

        assertSame(settled(), before, "Control+V with nothing copied moved to another button");

        Keys.press(keyboard(), KeyEvent.VK_C, CTRL);
        Keys.press(keyboard(), KeyEvent.VK_D);
        Keys.press(keyboard(), KeyEvent.VK_V, CTRL);

        assertSame(locOn(KeyEvent.VK_D), one, "Control+C then Control+V did not put the train on D");
        assertSame(locOn(KeyEvent.VK_A), one, "the copy took the train off A");

        Keys.press(keyboard(), KeyEvent.VK_X, CTRL);

        assertNull(locOn(KeyEvent.VK_D), "Control+X left the train on D");

        Keys.press(keyboard(), KeyEvent.VK_A);
        Keys.press(keyboard(), KeyEvent.VK_C, CTRL);
        Keys.press(keyboard(), KeyEvent.VK_S);
        Keys.press(keyboard(), KeyEvent.VK_S, CTRL);

        assertSame(locOn(KeyEvent.VK_S), one, "Control+S did not bring the copied train to S");
        assertSame(locOn(KeyEvent.VK_A), two, "Control+S did not send S's train to A");
    }

    /**
     * Delete clears the button and leaves the locomotive; Control+Delete asks, then deletes the locomotive itself.
     *
     * MUTATION: in "delete the locomotive", `key.ui.setCopyTarget(key.ui.currentButton, true)` for the `deleteLoc`, and
     * this fails.
     */
    @Test
    public void testDeleteWithAndWithoutControl() throws Exception
    {
        MarklinLocomotive doomed = model.newMFXLocomotive(DOOMED, 3153);

        put(KeyEvent.VK_D, doomed);

        Keys.press(keyboard(), KeyEvent.VK_DELETE);

        assertNull(locOn(KeyEvent.VK_D), "Delete left the train on its button");
        assertNotNull(model.getLocByName(DOOMED), "Delete deleted the locomotive");

        put(KeyEvent.VK_D, doomed);

        try (Answering yes = Answering.start(question -> Answering.pressAt(0)))
        {
            Keys.press(keyboard(), KeyEvent.VK_DELETE, CTRL);

            assertTrue(waitFor(() -> model.getLocByName(DOOMED) == null), "Control+Delete did not delete the locomotive");
        }

        assertNull(locOn(KeyEvent.VK_D), "the deleted locomotive stayed on its button");
    }

    /**
     * A bare letter shows its button, on each keyboard layout; Shift and Alt do not stop it, Control does.
     *
     * MUTATION: in "show a letter's button", `Held.ANY`, and this fails at Control+B; `Held.NEITHER`, and it fails at
     * Alt+B.
     */
    @Test
    public void testBareLettersReachTheirButtons() throws Exception
    {
        Keys.press(keyboard(), KeyEvent.VK_S);

        assertSame(settled(), letter(KeyEvent.VK_S), "S did not show its button");
        assertSame(ui.getActiveLoc(), two, "S did not show its train");

        Keys.press(keyboard(), KeyEvent.VK_A, SHIFT);

        assertSame(settled(), letter(KeyEvent.VK_A), "Shift+A did not show A's button");

        Keys.press(keyboard(), KeyEvent.VK_B, ALT);

        assertSame(settled(), letter(KeyEvent.VK_B), "Alt+B did not show B's button");

        Keys.press(keyboard(), KeyEvent.VK_A);

        JButton before = settled();

        Keys.press(keyboard(), KeyEvent.VK_B, CTRL);

        assertSame(settled(), before, "Control+B chose a button");

        String[] layouts = {TrainControlUI.KEYBOARD_QWERTZ, TrainControlUI.KEYBOARD_AZERTY, TrainControlUI.KEYBOARD_QWERTY};

        JButton buttonZ = field("ZButton");
        JButton buttonY = field("YButton");
        JButton buttonW = field("WButton");

        JButton[] onZ = {buttonY, buttonW, buttonZ};

        try
        {
            for (int at = 0; at < layouts.length; at++)
            {
                applyLayout(layouts[at]);

                assertEquals(letters().size(), 26, layouts[at] + " does not map the 26 letters this class's exhaustive"
                    + " sibling assumes");

                for (int key = KeyEvent.VK_A; key <= KeyEvent.VK_Z; key++)
                {
                    assertNotNull(letter(key), layouts[at] + " has no button for key " + key);
                }

                Keys.press(keyboard(), KeyEvent.VK_Z);

                assertSame(settled(), onZ[at], "Z did not show the button " + layouts[at] + " gives it");
            }
        }
        finally
        {
            Method selected = TrainControlUI.class.getDeclaredMethod("getSelectedKeyboardType");

            selected.setAccessible(true);

            int chosen = Components.onTheEventThread(() -> (Integer) selected.invoke(ui));

            applyLayout(TrainControlUI.KEYBOARD_TYPES[Math.max(chosen, 0)]);
        }
    }

    /**
     * Up and Down step the speed by a step, Control by one, Alt by two steps.  Left and Right turn the train, Control
     * forces the direction, and Alt turns the locomotive page instead.
     *
     * MUTATION: swap `altPressed` and `controlPressed` in the `Press` constructor, and this fails at Control+Up; so does
     * `SPEED_STEP` for the `SPEED_STEP * 2` in "speed up", at Alt+Up.
     */
    @Test
    public void testArrowsWithEachModifier() throws Exception
    {
        int step = TrainControlUI.SPEED_STEP;

        int[][] presses = {{KeyEvent.VK_UP, 0, step}, {KeyEvent.VK_UP, CTRL, 1}, {KeyEvent.VK_UP, ALT, 2 * step},
            {KeyEvent.VK_DOWN, 0, -step}, {KeyEvent.VK_DOWN, CTRL, -1}, {KeyEvent.VK_DOWN, ALT, -2 * step}};

        one.setSpeed(50);

        for (int[] press : presses)
        {
            int expected = one.getSpeed() + press[2];

            Keys.press(keyboard(), press[0], press[1]);

            assertTrue(waitFor(() -> one.getSpeed() == expected), KeyEvent.getKeyText(press[0]) + " with " + press[1]
                + " took the speed to " + one.getSpeed() + ", not " + expected);
        }

        Keys.press(keyboard(), KeyEvent.VK_LEFT, CTRL);

        assertTrue(waitFor(() -> !one.goingForward()), "Control+Left did not send the train backward");

        Keys.press(keyboard(), KeyEvent.VK_RIGHT, CTRL);

        assertTrue(waitFor(one::goingForward), "Control+Right did not send the train forward");

        Keys.press(keyboard(), KeyEvent.VK_LEFT);

        assertTrue(waitFor(() -> !one.goingForward()), "a plain Left did not turn the train");

        Keys.press(keyboard(), KeyEvent.VK_RIGHT);

        assertTrue(waitFor(one::goingForward), "a plain Right did not turn the train back");

        pressUntil(ui::getLocMappingNumber, new int[][] {{KeyEvent.VK_RIGHT, ALT, 2}, {KeyEvent.VK_LEFT, ALT, 1}});

        assertTrue(one.goingForward(), "Alt+Right or Alt+Left also turned the train");
    }

    /**
     * Space stops the shown train, and each digit sets its speed.  A digit with Alt or Control is a function, not a speed.
     *
     * MUTATION: swap two names in `SPEED_BUTTONS`, and this fails.
     */
    @Test
    public void testSpaceAndDigits() throws Exception
    {
        one.setSpeed(30);
        two.setSpeed(30);

        Keys.press(keyboard(), KeyEvent.VK_SPACE);

        assertTrue(waitFor(() -> one.getSpeed() == 0), "Space left the shown train at " + one.getSpeed());
        assertEquals(two.getSpeed(), 30, "Space stopped the other train");

        int[] speeds = {100, 0, 11, 22, 33, 44, 55, 66, 77, 88};

        for (int digit = 9; digit >= 0; digit--)
        {
            int expected = speeds[digit];

            Keys.press(keyboard(), KeyEvent.VK_0 + digit);

            assertTrue(waitFor(() -> one.getSpeed() == expected), digit + " took the speed to " + one.getSpeed());
        }

        Keys.press(keyboard(), KeyEvent.VK_5, ALT);

        assertTrue(waitFor(() -> one.getF(5)), "Alt+5 did not switch f5");
        assertEquals(one.getSpeed(), speeds[0], "Alt+5 also set a speed");
    }

    /**
     * Plus and minus turn the keyboard, by four with Control; on the layout tab they turn the diagram page and, at the
     * first page, minus does nothing at all and still takes the key.
     *
     * MUTATION: in the row "previous keyboard or layout page", `+ 4` for `- 4`, and this fails.
     */
    @Test
    public void testMinusAndPlus() throws Exception
    {
        pressUntil(testEveryKindOfShortcutOnTheWindow::keyboardNumber, new int[][] {{KeyEvent.VK_EQUALS, 0, 2},
            {KeyEvent.VK_MINUS, 0, 1}, {KeyEvent.VK_CLOSE_BRACKET, CTRL, 5}, {KeyEvent.VK_OPEN_BRACKET, CTRL, 1},
            {KeyEvent.VK_EQUALS, 0, 2}});

        javax.swing.JComboBox<?> pages = field("LayoutList");

        Components.onTheEventThread(() ->
        {
            tabs().setEnabledAt(1, true);
            tabs().setSelectedIndex(1);

            if (pages.getItemCount() > 0) pages.setSelectedIndex(0);

            return null;
        });

        int page = pages.getSelectedIndex();

        Keys.press(keyboard(), KeyEvent.VK_MINUS);

        Components.settle();

        assertEquals(pages.getSelectedIndex(), page, "- at the first diagram page moved the page");
        assertEquals(keyboardNumber(), 2, "- on the layout tab fell through to the keyboard");
    }

    /**
     * Comma and period turn the locomotive page; with Alt, to the first and the last.
     *
     * MUTATION: in "last locomotive page", `switchLocMapping(1)`, and this fails.
     */
    @Test
    public void testCommaAndPeriod() throws Exception
    {
        assertTrue(ui.getNumLocMappings() > 2, "precondition: too few pages to tell the last from the next");

        pressUntil(ui::getLocMappingNumber, new int[][] {{KeyEvent.VK_PERIOD, 0, 2}, {KeyEvent.VK_COMMA, 0, 1},
            {KeyEvent.VK_PERIOD, ALT, ui.getNumLocMappings()}, {KeyEvent.VK_COMMA, ALT, 1}});
    }

    /**
     * Every way to a function: the number pad, F-keys with anything held, the back quote, and digits with Alt, Control
     * or both.  Each switches its own function and no other.
     *
     * MUTATION: make the function rows' `Held.ANY` `Held.NEITHER`, and this fails at Control+Alt+Shift+F3; `switchF(fn +
     * 1)`, and it fails at the first.
     */
    @Test
    public void testNumpadFKeysAndModifiedDigits() throws Exception
    {
        int[][] presses = {{KeyEvent.VK_NUMPAD5, 0, 5}, {KeyEvent.VK_NUMPAD1, CTRL, 11}, {KeyEvent.VK_NUMPAD2, CTRL | ALT, 22},
            {KeyEvent.VK_F3, 0, 3}, {KeyEvent.VK_F3, CTRL | ALT | SHIFT, 3}, {KeyEvent.VK_F13, SHIFT, 13},
            {KeyEvent.VK_F24, CTRL, 24}, {KeyEvent.VK_BACK_QUOTE, ALT, 0}, {KeyEvent.VK_4, ALT, 4},
            {KeyEvent.VK_1, CTRL, 11}, {KeyEvent.VK_1, CTRL | ALT, 21}, {KeyEvent.VK_9, CTRL | ALT, 29}};

        for (int[] press : presses)
        {
            one.functionsOff();

            assertTrue(waitFor(() -> !one.getF(press[2])), "precondition: f" + press[2] + " stayed on");

            Keys.press(keyboard(), press[0], press[1]);

            assertTrue(waitFor(() -> one.getF(press[2])), KeyEvent.getKeyText(press[0]) + " with " + press[1]
                + " did not switch f" + press[2]);

            for (int fn = 0; fn < 30; fn++)
            {
                assertEquals(one.getF(fn), fn == press[2], KeyEvent.getKeyText(press[0]) + " with " + press[1]
                    + " left f" + fn + " wrong");
            }
        }
    }

    /**
     * Escape sends the power off, Shift brings the shown train to 0, and Enter stops every train.
     *
     * MUTATION: in "power off", `key.ui.go()`, and this fails; drop the row "speed zero", and this fails at Shift.
     */
    @Test
    public void testEscapeShiftAndEnter() throws Exception
    {
        sendsOnce(KeyEvent.VK_ESCAPE, 0, CS2Message.CMD_SYSSUB_STOP);

        one.setSpeed(40);
        two.setSpeed(40);

        Keys.press(keyboard(), KeyEvent.VK_SHIFT, SHIFT);

        assertTrue(waitFor(() -> one.getSpeed() == 0), "Shift left the shown train at " + one.getSpeed());
        assertEquals(two.getSpeed(), 40, "Shift stopped the other train");

        one.setSpeed(40);

        Keys.press(keyboard(), KeyEvent.VK_ENTER);

        assertTrue(waitFor(() -> one.getSpeed() == 0 && two.getSpeed() == 0), "Enter left a train running: "
            + one.getSpeed() + ", " + two.getSpeed());
    }

    /**
     * Caps Lock and Backspace bring the next keyboard tab forward, with Alt the one before; slash and less-than cycle
     * the function tabs.
     *
     * MUTATION: in "next keyboard tab", `+ 2`; make "next function tab" `Held.NEITHER`.  Each fails.
     */
    @Test
    public void testTabKeys() throws Exception
    {
        JTabbedPane tabs = tabs();

        for (int key : new int[] {KeyEvent.VK_BACK_SPACE, KeyEvent.VK_CAPS_LOCK})
        {
            int next = nextEnabled(tabs);

            Keys.press(keyboard(), key);

            assertEquals(tabs.getSelectedIndex(), next, KeyEvent.getKeyText(key) + " did not bring the next tab");

            Keys.press(keyboard(), key, ALT);

            assertEquals(tabs.getSelectedIndex(), 0, "Alt+" + KeyEvent.getKeyText(key) + " did not bring the tab before");
        }

        JTabbedPane functions = field("FunctionTabs");

        int count = functions.getComponentCount();

        for (int key : new int[] {KeyEvent.VK_SLASH, KeyEvent.VK_LESS})
        {
            int was = functions.getSelectedIndex();

            Keys.press(keyboard(), key, ALT);

            assertEquals(functions.getSelectedIndex(), (was + 1) % count, KeyEvent.getKeyText(key)
                + " did not cycle the function tabs");
        }
    }

    /**
     * A key no row takes does nothing, with or without a modifier.
     *
     * MUTATION: a row taking `VK_PAUSE` and switching a function, and this fails.
     */
    @Test
    public void testAKeyNoRowTakesDoesNothing() throws Exception
    {
        one.setSpeed(33);

        List<Object> before = everything();

        for (int modifiers : new int[] {0, ALT, CTRL, SHIFT, ALT | CTRL})
        {
            Keys.press(keyboard(), KeyEvent.VK_PAUSE, modifiers);

            assertEquals(everything(), before, "Pause with " + modifiers + " changed something");
        }
    }

    /**
     * With the pointer on the diagram and autonomy loaded, the gesture takes Delete before the table; off the diagram,
     * the same key clears the button.
     *
     * MUTATION: take the `locomotiveGestureOnDiagram` line out of `LocControlPanelKeyPressed`, and this fails.
     */
    @Test
    public void testTheDiagramGestureTakesTheKeyFirst() throws Exception
    {
        model.parseAuto("{\"points\": [], \"edges\": [], \"minDelay\": 0, \"maxDelay\": 0, \"defaultLocSpeed\": 30}");

        assertTrue(model.hasAutoLayout(), "precondition: no autonomy layout loaded");

        ui.setHoveredDiagramTile("no such page", 0, 0);

        Keys.press(keyboard(), KeyEvent.VK_DELETE);

        assertSame(locOn(KeyEvent.VK_A), one, "Delete over a diagram square reached the button instead of the gesture");

        ui.setHoveredDiagramTile(null, -1, -1);

        Keys.press(keyboard(), KeyEvent.VK_DELETE);

        assertNull(locOn(KeyEvent.VK_A), "Delete off the diagram did not clear the button");
    }

    /** Presses each {key, modifiers, value}, in turn, and waits for `now` to read that value. */
    private static void pressUntil(java.util.function.IntSupplier now, int[][] presses)
    {
        for (int[] press : presses)
        {
            Keys.press(keyboard(), press[0], press[1]);

            assertTrue(waitFor(() -> now.getAsInt() == press[2]), KeyEvent.getKeyText(press[0]) + " with " + press[1]
                + " left " + now.getAsInt() + ", not " + press[2]);
        }
    }

    /** The key sends this system command to the station, once. */
    private static void sendsOnce(int key, int modifiers, int command)
    {
        int mark = wire.mark();

        Keys.press(keyboard(), key, modifiers);

        assertTrue(waitFor(() -> wire.systemCommandsSince(mark, command).size() == 1), KeyEvent.getKeyText(key)
            + " did not send command " + command + " once: " + wire.since(mark));
    }

    /** All of what a stray key could have changed. */
    private static List<Object> everything() throws Exception
    {
        return Components.onTheEventThread(() -> Arrays.<Object>asList(ui.getActiveLoc(), one.getSpeed(), two.getSpeed(),
            one.goingForward(), one.getF(0), one.getF(1), currentButton(), keyboardNumber(), ui.getLocMappingNumber(),
            tabs().getSelectedIndex(), TrainControlUI.class.getName()));
    }

    private static int nextEnabled(JTabbedPane tabs) throws Exception
    {
        return Components.onTheEventThread(() ->
        {
            int at = tabs.getSelectedIndex();

            do
            {
                at = (at + 1) % tabs.getComponentCount();
            }
            while (!tabs.isEnabledAt(at));

            return at;
        });
    }

    /** Runs the window's own layout switch, which saves no preference. */
    private static void applyLayout(String layout) throws Exception
    {
        Method apply = TrainControlUI.class.getDeclaredMethod("applyKeyboardType", String.class);

        apply.setAccessible(true);

        Components.onTheEventThread(() -> apply.invoke(ui, layout));

        Components.settle();
    }

    /** Puts a locomotive on a letter, or clears it with Delete, by pressing the letter first. */
    private static void put(int key, MarklinLocomotive loc) throws Exception
    {
        Keys.press(keyboard(), key);

        assertSame(settled(), letter(key), "precondition: the letter did not choose its button");

        if (loc == null)
        {
            Keys.press(keyboard(), KeyEvent.VK_DELETE);
        }
        else
        {
            Components.onTheEventThread(() ->
            {
                if (ui.getButtonLocomotive(letter(key)) != loc) ui.mapLocToCurrentButton(loc.getName());

                return null;
            });
        }

        Components.settle();

        assertSame(locOn(key), loc, "precondition: the button does not hold what was put there");
    }

    private static Locomotive locOn(int key) throws Exception
    {
        Components.settle();

        return Components.onTheEventThread(() -> ui.getButtonLocomotive(letter(key)));
    }

    private static JButton letter(int key)
    {
        return letters().get(key);
    }

    private static Map<Integer, JButton> letters()
    {
        return field("buttonMapping");
    }

    private static JButton currentButton()
    {
        return field("currentButton");
    }

    /** The button showing, once the press's queued work is done. */
    private static JButton settled() throws Exception
    {
        Components.settle();

        return currentButton();
    }

    private static int keyboardNumber()
    {
        return field("keyboardNumber");
    }

    private static JTabbedPane tabs()
    {
        return field("KeyboardTab");
    }

    /** The tabbed pane holds the window's key map, as it does for the operator. */
    private static JTabbedPane keyboard()
    {
        return tabs();
    }

    private static <T> T field(String name)
    {
        return support.WindowField.of(ui, name);
    }

    private static boolean waitFor(BooleanSupplier condition)
    {
        return Components.waitFor(condition, WAIT);
    }
}
