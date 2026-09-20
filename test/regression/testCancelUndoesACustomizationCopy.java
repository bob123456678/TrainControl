package regression;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import javax.swing.SwingUtilities;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.LocomotiveFunctionAssign;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

/**
 * Cancel on the function editor undoes a Copy Customizations, as it already undoes the two slots.
 *
 * **The dialog's Cancel branch claimed everything in it waits for OK, and one thing did not** (GUX-C3).
 * `copyCustomizationsActionPerformed` writes the other locomotive's function types, triggers and
 * custom flag straight onto this one - deliberately, so the panel can show them at once, which is the
 * same reason the departure and arrival slots write straight through.  Those two are put back on
 * Cancel and this was not, so Cancel kept the copy: press Copy Customizations, change your mind, and
 * the locomotive keeps somebody else's functions and icons.
 *
 * What makes it worth a test rather than a reading is the comment: SVN-B14 wrote *"everything else in
 * this dialog waits for OK"* directly above the restore, so the next reader has been told this door
 * does not exist.
 *
 * @author Adam
 */
public class testCancelUndoesACustomizationCopy
{
    /**
     * Where the Cancel branch lives, for the call-site check below.
     */
    private static final String MENU = "src/org/traincontrol/gui/RightClickFunctionMenu.java";

    /**
     * A copy pressed and then cancelled leaves the locomotive exactly as it was.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testACancelledCopyLeavesTheFunctionsAlone() throws Exception
    {
        Started up = start();

        try
        {
            int[] wereTypes = Arrays.copyOf(up.loc.getFunctionTypes(), up.loc.getFunctionTypes().length);

            assertFalse(up.loc.isCustomFunctions(),
                "precondition: the locomotive counted as customized before anything was copied");

            press(up);

            // THE COPY HAPPENED.  Without this the test below would pass against a button that does
            // nothing at all.
            assertTrue(up.loc.isCustomFunctions(),
                "precondition: Copy Customizations did not copy anything, so there is nothing to undo");

            assertFalse(Arrays.equals(up.loc.getFunctionTypes(), wereTypes),
                "precondition: the copy left the function types unchanged, so the target's were the same");

            SwingUtilities.invokeAndWait(() -> up.panel.undoCopiedCustomizations());

            assertFalse(up.loc.isCustomFunctions(),
                "Cancel left the locomotive marked as customized after a Copy Customizations that was"
                + " cancelled");

            assertEquals(up.loc.getFunctionTypes(), wereTypes,
                "Cancel left the copied function types on the locomotive; it puts the two autonomy"
                + " slots back and this door writes through in exactly the same way");
        }
        finally
        {
            up.close();
        }
    }

    /**
     * A copy that was never pressed is not undone into something else.
     *
     * @throws Exception from the event thread
     */
    @Test
    public void testACancelWithoutACopyChangesNothing() throws Exception
    {
        Started up = start();

        try
        {
            int[] wereTypes = Arrays.copyOf(up.loc.getFunctionTypes(), up.loc.getFunctionTypes().length);

            SwingUtilities.invokeAndWait(() -> up.panel.undoCopiedCustomizations());

            assertEquals(up.loc.getFunctionTypes(), wereTypes,
                "a Cancel with no copy behind it rewrote the function types");

            assertFalse(up.loc.isCustomFunctions(), "and it marked the locomotive as customized");
        }
        finally
        {
            up.close();
        }
    }

    /**
     * The Cancel branch actually calls the undo.
     *
     * The panel's method is only half the fix: pinning it alone would leave the call site - the one
     * place that was wrong - as the only thing nothing checks.  Read rather than driven, because the
     * branch sits inside a modal `showOptionDialog` that a test cannot answer without a window.
     *
     * @throws Exception from the file
     */
    @Test
    public void testTheCancelBranchCallsIt() throws Exception
    {
        String source = new String(Files.readAllBytes(new File(MENU).toPath()), StandardCharsets.UTF_8);

        int cancel = source.indexOf("activeLoc.setDepartureFunc(departureWas)");

        assertTrue(cancel > 0,
            "the Cancel branch no longer restores the departure slot, so this test is reading for"
            + " something that has moved and knows nothing about where it went");

        assertTrue(source.indexOf("undoCopiedCustomizations()", cancel) > 0,
            "the Cancel branch puts the two slots back and does not undo a Copy Customizations"
            + " (GUX-C3)");
    }

    /**
     * A window, a model, a locomotive, something to copy from, and the panel.
     */
    private static final class Started
    {
        support.LayoutSandbox sandbox;

        MarklinControlStation model;

        TrainControlUI ui;

        MarklinLocomotive loc;

        LocomotiveFunctionAssign panel;

        void close() throws Exception
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

    /**
     * Presses Copy Customizations.
     *
     * @param up the fixture
     * @throws Exception from the event thread
     */
    private static void press(Started up) throws Exception
    {
        final Method door = LocomotiveFunctionAssign.class.getDeclaredMethod(
            "copyCustomizationsActionPerformed", java.awt.event.ActionEvent.class);

        door.setAccessible(true);

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                door.invoke(up.panel, (java.awt.event.ActionEvent) null);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Builds the fixture: a locomotive with ordinary functions and a copy target with odd ones.
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

            up.loc = new MarklinLocomotive(up.model, 81, MarklinLocomotive.decoderType.MM2,
                "Copy Test Loc");

            MarklinLocomotive from = new MarklinLocomotive(up.model, 82,
                MarklinLocomotive.decoderType.MM2, "Copy Test Source");

            int[] types = new int[from.getNumF()];
            int[] triggers = new int[from.getNumF()];

            Arrays.fill(types, 32);
            Arrays.fill(triggers, 1);

            from.setFunctionTypes(types, triggers);

            Field target = TrainControlUI.class.getDeclaredField("copyTarget");

            target.setAccessible(true);
            target.set(up.ui, (Locomotive) from);

            final Started fixture = up;

            SwingUtilities.invokeAndWait(() ->
            {
                fixture.panel = new LocomotiveFunctionAssign(fixture.loc, fixture.ui, 0, true);
            });

            return up;
        }
        catch (Exception e)
        {
            up.close();

            throw e;
        }
    }
}
