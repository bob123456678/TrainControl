package ui;

import java.awt.event.MouseWheelEvent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import support.Components;
import support.FrozenEditor;
import support.Reflect;
import support.Teardown;

/**
 * A wheel turned with Shift held on the Log tab moves its horizontal scroll bar and not its vertical one; a plain turn
 * still scrolls up and down, and a new log message keeps the sideways position (OB-471).
 *
 * MUTATION: drop the restore of the horizontal value in `TrainControlUI.log`, and the message claims, the burst claim
 * included, fail.  The Shift
 * claims pin what Java 8's own scroll pane already does; they are green without a change.
 */
public class testTheLogTabScrollsSideways
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;
    private static JScrollPane pane;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        FrozenEditor frozen = FrozenEditor.window("single-switch");

        sandbox = frozen.sandbox;
        model = frozen.model;
        ui = frozen.ui;
        pane = (JScrollPane) Reflect.get(TrainControlUI.class, ui, "jScrollPane3");

        // Lines wider and more numerous than the pane, laid out as a shown pane would be
        Components.onTheEventThread(() ->
        {
            JTextArea log = (JTextArea) Reflect.get(TrainControlUI.class, ui, "debugArea");
            StringBuilder text = new StringBuilder();

            for (int i = 0; i < 80; i++) text.append(new String(new char[400]).replace('\0', 'x')).append('\n');

            log.setText(text.toString());
            pane.setSize(300, 200);
            pane.validate();

            return null;
        });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        Teardown.close(sandbox, model, ui);
    }

    private static void turn(int modifiers, int notches) throws Exception
    {
        SwingUtilities.invokeAndWait(() -> pane.dispatchEvent(new MouseWheelEvent(pane, MouseWheelEvent.MOUSE_WHEEL,
            System.currentTimeMillis(), modifiers, 10, 10, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches)));
    }

    /** Shift and the wheel scroll the Log tab sideways, and only sideways. */
    @Test
    public void testShiftAndTheWheelScrollTheLogSideways() throws Exception
    {
        JScrollBar across = pane.getHorizontalScrollBar();
        JScrollBar down = pane.getVerticalScrollBar();

        assertTrue(across.isVisible() && across.getMaximum() > across.getVisibleAmount(),
            "precondition: the Log tab has nothing to scroll sideways");

        int acrossBefore = across.getValue();
        int downBefore = down.getValue();

        turn(java.awt.event.InputEvent.SHIFT_DOWN_MASK, 2);

        assertTrue(across.getValue() > acrossBefore, "Shift and the wheel did not move the Log tab sideways");
        assertEquals(down.getValue(), downBefore, "Shift and the wheel also scrolled the Log tab up and down");

        // And back
        int out = across.getValue();

        turn(java.awt.event.InputEvent.SHIFT_DOWN_MASK, -1);

        assertTrue(across.getValue() < out, "Shift and the wheel turned the other way did not scroll back");
    }

    /** A message logged while the Log tab is scrolled sideways leaves it where it was. */
    @Test
    public void testANewMessageKeepsTheSidewaysPosition() throws Exception
    {
        JScrollBar across = pane.getHorizontalScrollBar();

        Components.onTheEventThread(() ->
        {
            across.setValue(across.getMaximum() / 2);

            return null;
        });

        int before = across.getValue();

        assertTrue(before > 0, "precondition: the Log tab could not be scrolled sideways");

        ui.log("a new message");

        Components.settle();

        assertEquals(across.getValue(), before, "a new log message snapped the Log tab back to the left");
    }

    /** A burst of messages, logged before the event thread gets to any, leaves the sideways position too. */
    @Test
    public void testABurstOfMessagesKeepsTheSidewaysPosition() throws Exception
    {
        JScrollBar across = pane.getHorizontalScrollBar();

        Components.onTheEventThread(() ->
        {
            across.setValue(across.getMaximum() / 3);

            return null;
        });

        int before = across.getValue();

        assertTrue(before > 0, "precondition: the Log tab could not be scrolled sideways");

        for (int i = 0; i < 20; i++) ui.log("burst " + i);

        Components.settle();

        assertEquals(across.getValue(), before, "a burst of log messages snapped the Log tab back to the left");
    }

    /** The wheel alone still scrolls up and down, and leaves the sideways position. */
    @Test
    public void testTheWheelAloneStillScrollsTheLogDown() throws Exception
    {
        JScrollBar across = pane.getHorizontalScrollBar();
        JScrollBar down = pane.getVerticalScrollBar();

        int acrossBefore = across.getValue();
        int downBefore = down.getValue();

        turn(0, 3);

        assertTrue(down.getValue() > downBefore, "the wheel alone no longer scrolls the Log tab down");
        assertEquals(across.getValue(), acrossBefore, "the wheel alone moved the Log tab sideways");
    }
}
