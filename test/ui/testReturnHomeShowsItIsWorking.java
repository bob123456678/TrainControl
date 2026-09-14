package ui;

import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Return Home shows that it is working while its plan is worked out, and stops when there is an answer.
 *
 * Adam, FR-077, 2026-09-13: *"there needs to be a spinner on the return home button while it is
 * calculating"*.
 *
 * **Through the real button's door**, `requestReturnToHome`, with the planning held up rather than hoped
 * to be slow: the test holds the railway's monitor, which the plan's first step - `HomeStaging.snapshot`,
 * reading the homes - needs, so the plan is certainly still being worked out while the button is looked
 * at.  Released, the plan completes, and the mark has to go.
 *
 * Nothing is asked of the event thread while the monitor is held - a repaint that wanted it would wait
 * for this test, and this test would wait for the repaint.  The button is read directly, which is safe
 * for a reference read of an icon.
 *
 * On the frozen railway (OB-111).  Whatever the plan concludes is shown in a dialog, which is closed at
 * the end without being read - what it says is `testReturnHomeSaysWhy`'s business.
 *
 * MUTATION: delete `this.showReturnHomeWorking(true)` from `requestReturnToHome` and the first claim
 * fails; delete both `showReturnHomeWorking(false)` calls and the second does.
 *
 * @author Adam
 */
public class testReturnHomeShowsItIsWorking
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless())
        {
            throw new SkipException("the main window needs a display");
        }

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = init(null, true, false, false, true);

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            closeDialogs();

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * The mark is on the button while the plan is held up, and gone once the plan has an answer.
     *
     * @throws Exception from the event thread or reflection
     */
    @Test
    public void testTheButtonTurnsWhileThePlanIsWorkedOut() throws Exception
    {
        if (!model.hasAutoLayout()) throw new SkipException("the frozen railway built no autonomy layout");

        final Layout layout = model.getAutoLayout();

        powerOn();

        assertTrue(model.getPowerState(), "precondition: the power is off, so Return Home refuses before planning");

        assertTrue(!ui.isReturnHomeShowingWork(), "precondition: the button was already turning before it was pressed");

        synchronized (layout)
        {
            SwingUtilities.invokeLater(() -> ui.requestReturnToHome());

            assertTrue(waitFor(() -> ui.isReturnHomeShowingWork(), 10000),
                "Return Home was pressed and its plan is still being worked out - this test holds the"
                + " railway the plan must read - and the button shows nothing. Adam, FR-077: \"there needs"
                + " to be a spinner on the return home button while it is calculating\"");
        }

        assertTrue(waitFor(() -> !ui.isReturnHomeShowingWork(), 60000),
            "the plan was released and the button is still turning a minute later, so the mark outlives"
            + " the calculation it was put up for");
    }

    // ---------------------------------------------------------------------------------------------

    private static void powerOn() throws Exception
    {
        try
        {
            model.go();
        }
        catch (RuntimeException ignored)
        {
            // Simulated: the command has nowhere to go.  The state is set below either way.
        }

        if (!model.getPowerState())
        {
            java.lang.reflect.Field power = MarklinControlStation.class.getDeclaredField("powerState");

            power.setAccessible(true);
            power.set(model, true);
        }
    }

    private static void closeDialogs() throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (java.awt.Window window : java.awt.Window.getWindows())
            {
                if (window instanceof javax.swing.JDialog && window.isShowing()) window.dispose();
            }
        });
    }

    private static boolean waitFor(BooleanSupplier until, long ms)
    {
        long deadline = System.currentTimeMillis() + ms;

        while (System.currentTimeMillis() < deadline)
        {
            if (until.getAsBoolean()) return true;

            try
            {
                Thread.sleep(25);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return false;
            }
        }

        return until.getAsBoolean();
    }
}
