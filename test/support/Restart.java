package support;

import javax.swing.SwingUtilities;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The program started with its window, closed as the window's close box closes it, and started again over the same
 * railway and the same saved data: a restart, inside one test.
 *
 * The close is the window's own exit (`TrainControlUI.WindowClosed`) without its `System.exit`: the model saves the
 * locomotive database, the window saves its state and folds the running autonomy layout back into the setup, and the
 * window goes.  The start is `MarklinControlStation.init` with a window, in simulation and with no Central Station,
 * which reads the layout the sandbox points at and the database the run's data folder holds; and, as the window does
 * at start when Autonomy &gt; Load at Start is ticked, the setup's active configuration is loaded.
 *
 * Open a `LayoutSandbox` (or a `Scenario`'s folder) first: the start reads the layout preference.  Every window this
 * starts is the model's own (`getGUI`), so a class using it does not build a `TrainControlUI` itself.
 *
 * Written for list 2's restart rows (FR-136 Phase 6): what stands where, what is claimed, the switches and the
 * statistics, read back after the restart.
 *
 * @author Adam
 */
public final class Restart
{
    /** How long the window is given to load the setup's active configuration. */
    private static final long LOAD_MS = 30000;

    private Restart()
    {
    }

    /**
     * Starts the program with its window, as a launch does, and loads the setup's active configuration if there is one.
     *
     * @return the model; its window is `getGUI()`
     * @throws Exception if the model cannot be built
     */
    public static MarklinControlStation start() throws Exception
    {
        MarklinControlStation model = TestStationAddress.withNoStation(
            () -> MarklinControlStation.init(null, true, true, false, true));

        final TrainControlUI ui = (TrainControlUI) model.getGUI();

        if (ui == null) throw new IllegalStateException("the program started without its window");

        // `init` posts the window's display, which posts work of its own
        Components.settle();

        final org.traincontrol.automationui.AutonomySession session = ui.getAutonomySession();

        if (session != null && session.getStore().getActiveConfiguration() != null
            && ui.getActiveDiagramConfiguration() == null)
        {
            final String active = session.getStore().getActiveConfiguration();

            SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

            if (!Components.waitFor(() -> ui.getActiveDiagramConfiguration() != null, LOAD_MS))
            {
                throw new IllegalStateException("the setup's configuration " + active + " did not load");
            }
        }

        Components.settle();

        return model;
    }

    /**
     * Closes the program as its window's close box does, and starts it again over the same railway and data.
     *
     * @param running the model to close, whose window is closed with it
     * @return the new model
     * @throws Exception if the close or the start fails
     */
    public static MarklinControlStation again(MarklinControlStation running) throws Exception
    {
        close(running);

        return start();
    }

    /**
     * Closes the program as its window's close box does: the database saved, the window's state saved, the window gone.
     * The model is not used again.
     *
     * @param running the model to close
     * @throws Exception from the event thread
     */
    public static void close(MarklinControlStation running) throws Exception
    {
        if (running == null) return;

        final TrainControlUI ui = (TrainControlUI) running.getGUI();

        SwingUtilities.invokeAndWait(() ->
        {
            running.saveState(false);

            if (ui != null)
            {
                ui.saveState(false);

                ui.dispose();
            }
        });

        Components.settle();
    }

    /**
     * Throws the window away without saving anything, for a teardown.
     *
     * @param running the model, or null
     * @throws Exception from the event thread
     */
    public static void discard(MarklinControlStation running) throws Exception
    {
        if (running == null) return;

        final TrainControlUI ui = (TrainControlUI) running.getGUI();

        if (ui != null) SwingUtilities.invokeAndWait(ui::dispose);
    }
}
