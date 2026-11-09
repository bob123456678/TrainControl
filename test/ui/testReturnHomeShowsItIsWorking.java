package ui;

import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import support.Display;
import support.Reflect;
import support.Settle;
import support.Teardown;
import static support.Settle.until;

/**
 * Return Home shows that it is working while its plan is worked out, and stops when there is an answer (FR-077).
 * Through the real button's door, `requestReturnToHome`, with the planning held up rather than hoped to be slow: the
 * test holds the railway's monitor, which the plan's first step (`HomeStaging.snapshot`) needs.  Nothing is asked of
 * the event thread while the monitor is held, since a repaint that wanted it would wait for this test and this test
 * for the repaint.  On the frozen railway (OB-111).
 *
 * MUTATION: delete `this.showReturnHomeWorking(true)` from `requestReturnToHome` and the first claim fails; delete both
 * `showReturnHomeWorking(false)` calls and the second does.
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
        Display.needed("the main window needs a display");

        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        model = support.TestStation.simulatedInDebug();

        SwingUtilities.invokeAndWait(() -> ui = new TrainControlUI());

        ui.setViewListener(model, new java.util.concurrent.CountDownLatch(1));

        // And the startup load of his configuration, which this test is about, waited for as openTheWindow does in the
        // import-door claims.
        final java.util.concurrent.CountDownLatch settled = new java.util.concurrent.CountDownLatch(1);

        ui.whenTilesSettled(() -> settled.countDown());

        settled.await(30, java.util.concurrent.TimeUnit.SECONDS);

        Settle.until(() -> model.hasAutoLayout(), 30000);

        for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            closeDialogs();
        }
        finally
        {
            Teardown.close(sandbox, model, ui);
        }
    }

    /** The mark is on the button while the plan is held up, and gone once the plan has an answer. */
    @Test
    public void testTheButtonTurnsWhileThePlanIsWorkedOut() throws Exception
    {
        assertTrue(model.hasAutoLayout() && model.getAutoLayout().isValid(), "precondition: his railway, frozen, did"
            + " not load its autonomy configuration at start-up");

        final Layout layout = model.getAutoLayout();

        powerOn();

        assertTrue(model.getPowerState(), "precondition: the power is off, so Return Home refuses before planning");

        assertTrue(!ui.isReturnHomeShowingWork(), "precondition: the button was already turning before it was pressed");

        synchronized (layout)
        {
            SwingUtilities.invokeLater(() -> ui.requestReturnToHome());

            assertTrue(until(() -> ui.isReturnHomeShowingWork(), 10000),
                "Return Home was pressed and its plan is still being worked out - this test holds the"
                + " railway the plan must read - and the button shows nothing. Adam, FR-077: \"there needs"
                + " to be a spinner on the return home button while it is calculating\"");
        }

        assertTrue(until(() -> !ui.isReturnHomeShowingWork(), 60000),
            "the plan was released and the button is still turning a minute later, so the mark outlives"
            + " the calculation it was put up for");
    }

    /**
     * And the mark stops with its window, as Start's does: a window closed while the plan is worked out leaves no timer
     * repainting it.  Last, since it closes the window.
     *
     * MUTATION: drop the `isDisplayable()` check from `showReturnHomeWorking`'s timer and this fails.
     */
    @Test(dependsOnMethods = "testTheButtonTurnsWhileThePlanIsWorkedOut", alwaysRun = true)
    public void testTheMarkStopsWithItsWindow() throws Exception
    {
        java.lang.reflect.Method show = TrainControlUI.class.getDeclaredMethod("showReturnHomeWorking", boolean.class);
        java.lang.reflect.Field spinner = TrainControlUI.class.getDeclaredField("returnHomeSpinner");

        show.setAccessible(true);
        spinner.setAccessible(true);

        final javax.swing.Timer[] timer = {null};

        SwingUtilities.invokeAndWait(() ->
        {
            try
            {
                show.invoke(ui, true);
                timer[0] = (javax.swing.Timer) spinner.get(ui);
            }
            catch (ReflectiveOperationException e)
            {
                throw new IllegalStateException(e);
            }
        });

        assertTrue(timer[0] != null && timer[0].isRunning(), "precondition: the mark did not start turning");

        SwingUtilities.invokeAndWait(() -> ui.dispose());

        assertTrue(until(() -> !timer[0].isRunning(), 2000),
            "the window was closed with Return Home's mark turning and its timer is still running, repainting a"
            + " button nobody can see");
    }

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
            Reflect.set(MarklinControlStation.class, model, "powerState", true);
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
}
