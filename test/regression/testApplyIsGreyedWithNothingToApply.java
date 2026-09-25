package regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.LocomotiveFunctionAssign;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * Customize Function Icons greys Apply while there is nothing to apply, and only then (FR-098).
 *
 * Adam, on MT-466, 2026-09-22: *"there is no cancel button if you go to manage locomotive -> customize function icons,
 * only apply- and closing without clicking on apply still persists the functions here.  That's OK, but just make sure
 * apply is greyed out if there is nothing to apply."*  Nothing to apply is the function on show exactly as the
 * locomotive holds it: its icon, its trigger, and its custom picture.  Both directions are claimed - greying Apply while
 * there IS something to apply would lose the change when the window is closed.
 *
 * MUTATION: leave Apply enabled whatever is shown, or grey it whatever is shown, and this fails.
 *
 * @author Adam
 */
public class testApplyIsGreyedWithNothingToApply
{
    /**
     * Greyed on opening, live once the icon or the trigger is changed, greyed again once it is changed back or applied;
     * live when the custom picture is taken off.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testApplyFollowsWhetherAnythingChanged() throws Exception
    {
        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;
        TrainControlUI ui = null;

        try
        {
            // OPENED INSIDE THE TRY (TSX-B8, OB-111): anything thrown between the open and the close would leave the
            // layout preference pointing at a folder under %TEMP%.
            sandbox = support.LayoutSandbox.open();

            model = MarklinControlStation.init(null, true, false, false, false);

            final MarklinControlStation m = model;
            final TrainControlUI[] made = new TrainControlUI[1];

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    made[0] = new TrainControlUI();
                    made[0].setViewListener(m, new java.util.concurrent.CountDownLatch(1));
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            ui = made[0];

            final MarklinLocomotive loc = new MarklinLocomotive(model, 83, MarklinLocomotive.decoderType.MM2,
                "FR-098 loc");

            // F1 CARRIES A CUSTOM PICTURE, so taking it off is something to apply.
            loc.setLocalFunctionImageURL(1, new java.io.File("FR-098-missing.png").toURI().toString());

            final TrainControlUI window = ui;
            final LocomotiveFunctionAssign[] panel = new LocomotiveFunctionAssign[1];

            SwingUtilities.invokeAndWait(() -> panel[0] = new LocomotiveFunctionAssign(loc, window, 0, false));

            // The icons load in a deferred step of their own.
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });

            final JButton apply = field(panel[0], "applyButton", JButton.class);
            @SuppressWarnings("unchecked")
            final JComboBox<Object> icon = field(panel[0], "fIcon", JComboBox.class);
            @SuppressWarnings("unchecked")
            final JComboBox<String> trigger = field(panel[0], "functionTriggerType", JComboBox.class);
            @SuppressWarnings("unchecked")
            final JComboBox<String> number = field(panel[0], "fNo", JComboBox.class);

            assertEquals(number.getSelectedIndex(), 0, "precondition: the panel did not open on F0");

            assertFalse(on(apply), "Apply is live on a function shown exactly as the locomotive holds it - there is"
                + " nothing to apply (FR-098)");

            // THE ICON CHANGED, AND CHANGED BACK.
            final int was = icon.getSelectedIndex();

            assertTrue(icon.getItemCount() > 1, "precondition: there is only one icon to choose");

            SwingUtilities.invokeAndWait(() -> icon.setSelectedIndex(was == 0 ? 1 : 0));

            assertTrue(on(apply), "Apply is greyed with another icon chosen - closing the window would keep a change"
                + " the operator cannot apply (FR-098)");

            SwingUtilities.invokeAndWait(() -> icon.setSelectedIndex(was));

            assertFalse(on(apply), "Apply stays live with the icon put back as it was (FR-098)");

            // THE TRIGGER CHANGED, THEN APPLIED: written, and the next function shown as held.
            final int triggerWas = trigger.getSelectedIndex();

            SwingUtilities.invokeAndWait(() -> trigger.setSelectedIndex(triggerWas == 0 ? 1 : 0));

            assertTrue(on(apply), "Apply is greyed with another trigger chosen (FR-098)");

            SwingUtilities.invokeAndWait(() -> apply.doClick());

            assertEquals(number.getSelectedIndex(), 1, "precondition: Apply did not move on to F1");

            assertFalse(on(apply), "Apply is live on the function Apply moved on to, which nothing has changed (FR-098)");

            // THE CUSTOM PICTURE TAKEN OFF F1.
            final Method delete = LocomotiveFunctionAssign.class.getDeclaredMethod("deleteCustomIconActionPerformed",
                java.awt.event.ActionEvent.class);

            delete.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    delete.invoke(panel[0], (java.awt.event.ActionEvent) null);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

            assertTrue(on(apply), "Apply is greyed with F1's custom picture taken off - closing the window would keep"
                + " it on (FR-098)");
        }
        finally
        {
            if (ui != null)
            {
                final TrainControlUI window = ui;

                SwingUtilities.invokeAndWait(() -> window.dispose());
            }

            if (model != null) model.stop();

            if (sandbox != null) sandbox.close();
        }
    }

    private static boolean on(JButton button) throws Exception
    {
        final boolean[] enabled = new boolean[1];

        SwingUtilities.invokeAndWait(() -> enabled[0] = button.isEnabled());

        return enabled[0];
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception
    {
        Field f = owner.getClass().getDeclaredField(name);

        f.setAccessible(true);

        return type.cast(f.get(owner));
    }
}
