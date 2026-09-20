package regression;

import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.SwingUtilities;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.gui.TrainControlUI;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * The four arrow buttons do what their own tooltips say a Control or Alt click does.
 *
 * **The tooltips have promised this since 2.7.4 and only the keyboard delivered it** (UIX-C5, GUX-C1).
 * `ui.main.tooltip.switchDir` says *"(+Control to force reverse)"*, `incrSpeed` says *"(+Control to
 * fine-tune, +Alt for 2x increment)"*, and all four listeners took the `ActionEvent` and never looked
 * at it: a Control click reversed a locomotive that was already reversed, and an Alt click on Increase
 * Speed moved one step like any other click.  The behaviour existed on `VK_LEFT` and `VK_UP` and
 * nowhere else, so an operator driving with the mouse could not reach it at all.
 *
 * **Driven at the listeners, with events that carry the modifiers.**  That is the seam the fix changes
 * and the one the button uses - `jButton.doClick()` cannot carry a Control mask, and a synthetic
 * `KeyEvent` would go to the key handler, which was never the half that was wrong.  The keyboard's own
 * arms pass `null` here for the plain case, so `null` is tested too.
 *
 * The window is built but not displayed: nothing here looks at a picture, and `display()` takes the
 * machine's keyboard away from whatever Adam is doing.
 *
 * @author Adam
 */
public class testTheToolbarButtonsHonourTheirTooltips
{
    /**
     * How long to wait for a locomotive to answer; every door here hands the work to a thread.
     */
    private static final int PATIENCE = 4000;

    /**
     * How long to let the thread a door starts actually run, before believing what the locomotive says.
     *
     * **A value that is already right proves nothing here.**  Every one of these doors hands its work
     * to a `new Thread`, so a check taken the instant after the press reads the state the PREVIOUS
     * press left - and the first draft of this class passed the force-reverse test against the toggle
     * it was written to catch, because the locomotive was still reversed from the press before.
     */
    private static final int SETTLE = 500;

    /**
     * A window, a model and a locomotive on the throttle.
     */
    private static final class Started
    {
        support.LayoutSandbox sandbox;

        MarklinControlStation model;

        TrainControlUI ui;

        MarklinLocomotive loc;

        /**
         * How many direction commands have been sent since it was last zeroed (FNL-C6).
         */
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
     * Control on Switch Direction forces a direction instead of toggling one.
     *
     * Twice in each direction: a force that has something to do and a force that has nothing to do.
     * The second is the half a toggle gets wrong, and the half the tooltip is about - *"force"* means
     * the locomotive ends up backward whether or not it started forward.
     *
     * @throws Exception from the event thread
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

            // AND AGAIN, with nothing to do.  A toggle would send it forward here.
            //
            // WAITED FOR BY THE COMMAND, not by a clock (FNL-C6).  This is the press that tells a force
            // from a toggle, and the state it should leave - still reversed - is the state the press
            // BEFORE it left.  A `sleep` that ran out before the door's worker did would have seen the
            // right answer for the wrong reason, which is how the first draft of this class passed.
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

    /**
     * A plain click on either direction button still toggles, as it always has.
     *
     * @throws Exception from the event thread
     */
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

            // AND THE KEYBOARD'S OWN CALL, which hands over no event at all.
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
     * Alt doubles the step and Control fine-tunes it, on both speed buttons.
     *
     * The numbers are the key handler's: `SPEED_STEP * 2` for Alt and `1` for Control, which is what
     * the tooltip means by *"2x increment"* and *"fine-tune"*.
     *
     * @throws Exception from the event thread
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

    /**
     * A plain click, and the keyboard's null event, still move one ordinary step.
     *
     * @throws Exception from the event thread
     */
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

    /**
     * Presses one of the four buttons with the given modifier mask, or with no event at all.
     *
     * @param ui the window
     * @param listener which listener to call
     * @param modifiers the mask the click carries, or null for the keyboard's own plain call
     * @throws Exception from the event thread
     */
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
                // THE THROTTLE IS SET IN THE SAME RUNNABLE AS THE PRESS.
                //
                // Start-up puts the locomotive it remembers back on the throttle, on the event thread,
                // some time after `setViewListener` returns - so a fixture that set this field once at
                // the start found one of Adam's own locomotives there by the time it pressed anything,
                // and every press moved nothing this class was watching.
                active.set(ui, loc);

                door.invoke(ui, event);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Waits until a direction command has actually been sent (FNL-C6).
     *
     * @param up the fixture
     * @return whether one arrived
     * @throws Exception from the sleep
     */
    private static boolean awaitACommand(Started up) throws Exception
    {
        for (int waited = 0; waited < PATIENCE; waited += 25)
        {
            if (up.directions.get() > 0) return true;

            Thread.sleep(25);
        }

        return false;
    }

    /**
     * Waits for the locomotive to reach a speed; every one of these doors works on its own thread.
     *
     * @param loc the locomotive
     * @param speed what it should reach
     * @return whether it got there
     * @throws Exception from the sleep
     */
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

    /**
     * Waits for the locomotive to face a direction.
     *
     * @param loc the locomotive
     * @param forward which way it should be facing
     * @return whether it got there
     * @throws Exception from the sleep
     */
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
     * Sandbox, model, window, and a locomotive on the throttle.
     *
     * The locomotive is built rather than borrowed: the model's database is Adam's own, and the speed
     * and direction of one of his locomotives are not this class's to move about.
     *
     * @return the started application
     * @throws Exception from start-up
     */
    private static Started start() throws Exception
    {
        Started up = new Started();

        try
        {
            up.sandbox = support.LayoutSandbox.open();

            up.model = MarklinControlStation.init(null, true, false, false, false);

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
