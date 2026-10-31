package regression;

import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * The four arrow buttons do what their own tooltips say a Control or Alt click does.
 *
 * `ui.main.tooltip.switchDir` says "(+Control to force reverse)" and `incrSpeed` "(+Control to fine-tune, +Alt for 2x
 * increment)", but the listeners never looked at the `ActionEvent`; only the keyboard delivered it.  Driven at the
 * listeners with events that carry the modifiers (`doClick()` cannot carry a mask, and a `KeyEvent` would go to the key
 * handler, which was never the wrong half); the keyboard's own arms pass `null` for the plain case, so `null` is tested
 * too.  The window is built but not displayed, since `display()` takes the keyboard away from whatever Adam is doing.
 *
 * @author Adam
 */
public class testTheToolbarButtonsHonourTheirTooltips
{
    /** How long to wait for a locomotive to answer; every door here hands the work to a thread. */
    private static final int PATIENCE = 4000;

    /**
     * How long to let the thread a door starts run before believing what the locomotive says: a check taken at once
     * reads the state the previous press left, which is how a force-reverse test could pass against a toggle.
     */
    private static final int SETTLE = 500;

    /** A window, a model and a locomotive on the throttle. */
    private static final class Started
    {
        support.LayoutSandbox sandbox;

        MarklinControlStation model;

        TrainControlUI ui;

        MarklinLocomotive loc;

        /** How many direction commands have been sent since it was last zeroed. */
        final java.util.concurrent.atomic.AtomicInteger directions =
            new java.util.concurrent.atomic.AtomicInteger();

        void close() throws Exception
        {
            if (model != null) model.setSentMessageObserver(null);

            if (ui != null)
            {
                final TrainControlUI window = ui;

                SwingUtilities.invokeAndWait(() -> window.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Control on Switch Direction forces a direction instead of toggling one.  Twice in each direction: a force that
     * has something to do and one that has nothing to do, which is the half a toggle gets wrong.
     */
    @Test
    public void testControlOnADirectionButtonForcesThatDirection() throws Exception
    {
        Started up = start();

        try
        {
            up.loc.setDirection(Locomotive.locDirection.DIR_FORWARD);

            press(up, "LeftArrowLetterButtonPressed", ActionEvent.CTRL_MASK);

            assertTrue(awaitDirection(up.loc, false),
                "Control plus the reverse button left the locomotive going forward; the tooltip on that"
                + " button says it forces reverse");

            // And again, with nothing to do: a toggle would send it forward here.  Waited for by the command, not by a
            // clock, because the state it should leave (still reversed) is the one the press before it left.
            up.directions.set(0);

            press(up, "LeftArrowLetterButtonPressed", ActionEvent.CTRL_MASK);

            assertTrue(awaitACommand(up),
                "Control plus the reverse button sent no direction command at all, so nothing here says"
                + " whether it forces or toggles");

            assertTrue(awaitDirection(up.loc, false),
                "Control plus the reverse button TOGGLED a locomotive that was already reversed; the"
                + " tooltip says force, and a force is not a toggle");

            press(up, "RightArrowLetterButtonPressed", ActionEvent.CTRL_MASK);

            assertTrue(awaitDirection(up.loc, true),
                "Control plus the forward button did not send the locomotive forward");

            press(up, "RightArrowLetterButtonPressed", ActionEvent.CTRL_MASK);

            assertTrue(awaitDirection(up.loc, true),
                "Control plus the forward button toggled a locomotive that was already going forward");
        }
        finally
        {
            up.close();
        }
    }

    /** A plain click on either direction button still toggles, as it always has. */
    @Test
    public void testAPlainClickOnADirectionButtonStillToggles() throws Exception
    {
        Started up = start();

        try
        {
            up.loc.setDirection(Locomotive.locDirection.DIR_FORWARD);

            press(up, "LeftArrowLetterButtonPressed", 0);

            assertTrue(awaitDirection(up.loc, false), "a plain click no longer toggles the direction");

            press(up, "LeftArrowLetterButtonPressed", 0);

            assertTrue(awaitDirection(up.loc, true), "a plain click no longer toggles the direction back");

            // And the keyboard's own call, which hands over no event at all.
            press(up, "RightArrowLetterButtonPressed", null);

            assertTrue(awaitDirection(up.loc, false),
                "the keyboard's plain arrow, which calls this with a null event, stopped toggling");
        }
        finally
        {
            up.close();
        }
    }

    /**
     * Alt doubles the step and Control fine-tunes it, on both speed buttons: `SPEED_STEP * 2` for Alt and `1` for
     * Control, as the key handler has it.
     */
    @Test
    public void testTheSpeedButtonsReadAltAndControl() throws Exception
    {
        Started up = start();

        try
        {
            assertTrue(awaitSpeed(up.loc, 0), "precondition: the locomotive did not start at a stand");

            press(up, "UpArrowLetterButtonPressed", ActionEvent.ALT_MASK);

            assertTrue(awaitSpeed(up.loc, TrainControlUI.SPEED_STEP * 2),
                "Alt plus Increase Speed moved " + up.loc.getSpeed() + " instead of "
                + (TrainControlUI.SPEED_STEP * 2) + "; the tooltip on that button promises a 2x"
                + " increment, and the keyboard's Alt+Up has always given one");

            press(up, "DownArrowLetterButtonPressed", ActionEvent.CTRL_MASK);

            assertTrue(awaitSpeed(up.loc, TrainControlUI.SPEED_STEP * 2 - 1),
                "Control plus Decrease Speed moved to " + up.loc.getSpeed() + " rather than one step"
                + " down; the tooltip promises fine-tuning");

            press(up, "UpArrowLetterButtonPressed", ActionEvent.CTRL_MASK);

            assertTrue(awaitSpeed(up.loc, TrainControlUI.SPEED_STEP * 2),
                "Control plus Increase Speed did not move by one");

            press(up, "DownArrowLetterButtonPressed", ActionEvent.ALT_MASK);

            assertTrue(awaitSpeed(up.loc, 0), "Alt plus Decrease Speed did not take two steps off");
        }
        finally
        {
            up.close();
        }
    }

    /** A plain click, and the keyboard's null event, still move one ordinary step. */
    @Test
    public void testAPlainClickOnASpeedButtonStillMovesOneStep() throws Exception
    {
        Started up = start();

        try
        {
            press(up, "UpArrowLetterButtonPressed", 0);

            assertTrue(awaitSpeed(up.loc, TrainControlUI.SPEED_STEP),
                "a plain click on Increase Speed no longer moves one step");

            press(up, "DownArrowLetterButtonPressed", null);

            assertTrue(awaitSpeed(up.loc, 0),
                "the keyboard's plain Down, which calls this with a null event, no longer moves one step");
        }
        finally
        {
            up.close();
        }
    }

    /** Presses one of the four buttons with the given modifier mask, or null for the keyboard's own plain call. */
    private static void press(Started up, String listener, Integer modifiers) throws Exception
    {
        final Method door = TrainControlUI.class.getDeclaredMethod(listener, ActionEvent.class);

        door.setAccessible(true);

        final Field active = TrainControlUI.class.getDeclaredField("activeLoc");

        active.setAccessible(true);

        final ActionEvent event = modifiers == null ? null
            : new ActionEvent(up.ui, ActionEvent.ACTION_PERFORMED, "pressed", modifiers);

        final TrainControlUI ui = up.ui;
        final MarklinLocomotive loc = up.loc;

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                // The throttle is set in the same runnable as the press: start-up puts the locomotive it remembers back
                // on the throttle on the event thread some time after `setViewListener` returns.
                active.set(ui, loc);

                door.invoke(ui, event);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    /** Waits until a direction command has actually been sent. */
    private static boolean awaitACommand(Started up) throws Exception
    {
        for (int waited = 0; waited < PATIENCE; waited += 25)
        {
            if (up.directions.get() > 0) return true;

            Thread.sleep(25);
        }

        return false;
    }

    /** Waits for the locomotive to reach a speed; every one of these doors works on its own thread. */
    private static boolean awaitSpeed(MarklinLocomotive loc, int speed) throws Exception
    {
        Thread.sleep(SETTLE);

        for (int waited = 0; waited < PATIENCE; waited += 50)
        {
            if (loc.getSpeed() == speed) return true;

            Thread.sleep(50);
        }

        return false;
    }

    /** Waits for the locomotive to face a direction. */
    private static boolean awaitDirection(MarklinLocomotive loc, boolean forward) throws Exception
    {
        Thread.sleep(SETTLE);

        for (int waited = 0; waited < PATIENCE; waited += 50)
        {
            if (loc.goingForward() == forward) return true;

            Thread.sleep(50);
        }

        return false;
    }

    /**
     * A short tooltip on the Layouts menu is exactly as wide as its text, and a long one still wraps (OB-358).  The
     * reference is the same sentence shown plain: a tooltip that holds it on one line is that size.
     */
    @Test
    public void testAShortLayoutTooltipIsAsWideAsItsText() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("a tooltip is drawn on a window");
        }

        Started up = start();

        try
        {
            // The item shown: the Manage Pages menu behind it is hidden
            Field field = TrainControlUI.class.getDeclaredField("managePagesItem");

            field.setAccessible(true);

            javax.swing.JMenuItem create = (javax.swing.JMenuItem) field.get(up.ui);

            assertTrue(create != null && create.getText().equals(I18n.t("ui.main.toolbar.managePages")),
                "precondition: the item found is not Manage Pages");

            String sentence = I18n.t("ui.main.toolbar.tooltip.modifyLayout");

            assertEquals(tooltipSize(create.getToolTipText()).width, tooltipSize(sentence).width,
                "the tooltip on Manage Pages is drawn " + tooltipSize(create.getToolTipText()).width
                + "px wide for a sentence that is " + tooltipSize(sentence).width + "px on one line, so the"
                + " box has room to spare at its end: " + create.getToolTipText());

            // The other side: a long one still wraps, so it is taller than one line.
            String long1 = "Creates a blank track diagram that you can edit visually, with every track, signal and"
                + " switch placed where you put it and nothing else.";

            String wrapped = org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel.wrappedIfLong(long1);

            assertTrue(tooltipSize(wrapped).height > tooltipSize(long1).height,
                "a long tooltip did not wrap at all, so it runs off the edge of the display: " + wrapped);
        }
        finally
        {
            up.close();
        }
    }

    /** The size Swing lays a tooltip with this text out at. */
    private static java.awt.Dimension tooltipSize(String text)
    {
        javax.swing.JToolTip tip = new javax.swing.JToolTip();

        tip.setTipText(text);

        return tip.getPreferredSize();
    }

    /**
     * Sandbox, model, window, and a locomotive on the throttle.  The locomotive is built rather than borrowed: the
     * model's database is Adam's own.
     */
    private static Started start() throws Exception
    {
        Started up = new Started();

        try
        {
            up.sandbox = support.LayoutSandbox.open();

            up.model = support.TestStation.simulated();

            final MarklinControlStation model = up.model;
            final TrainControlUI[] made = new TrainControlUI[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    made[0] = new TrainControlUI();
                    made[0].setViewListener(model, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            up.ui = made[0];

            up.loc = new MarklinLocomotive(up.model, 80, MarklinLocomotive.decoderType.MM2,
                "Tooltip Test Loc");

            final Started counting = up;

            up.model.setSentMessageObserver(m ->
            {
                if (m.getCommand() != null
                    && m.getCommand() == org.traincontrol.marklin.udp.CS2Message.CMD_LOCO_DIRECTION)
                {
                    counting.directions.incrementAndGet();
                }
            });

            assertEquals(up.loc.getSpeed(), 0, "precondition: a new locomotive is not at a stand");

            return up;
        }
        catch (Exception e)
        {
            up.close();

            throw e;
        }
    }
}
