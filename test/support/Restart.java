package support;

import java.io.File;
import javax.swing.SwingUtilities;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * The program started with its window on a throwaway copy of a railway, closed as the window's close box closes it, and
 * started again over the same railway and the same saved data: a restart, inside one test.
 *
 * The close is the window's own exit (`TrainControlUI.WindowClosed`) without its `System.exit`: the model saves the
 * locomotive database, the window saves its state and folds the running autonomy layout back into the setup, and the
 * window goes.  The start is `MarklinControlStation.init` with a window, in simulation and with no Central Station,
 * which reads the layout the sandbox points at and the database the run's data folder holds; and, as the window does
 * at start when Autonomy &gt; Load at Start is ticked, the setup's active configuration is loaded.
 *
 * The sandbox is opened here, before the first model, so the program never reads the operator's own railway (OB-111),
 * and closed by `close()`.  Every window this starts is the model's own (`getGUI`), so a class using it builds no
 * `TrainControlUI` itself.
 *
 *     restart = support.Restart.open(support.Scenario.folderFor("live-snapshot"));
 *     MarklinControlStation first = restart.getModel();
 *     ...
 *     MarklinControlStation second = restart.again();
 *     ...
 *     restart.close();
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

    private final LayoutSandbox sandbox;

    private MarklinControlStation model;

    private Restart(LayoutSandbox sandbox)
    {
        this.sandbox = sandbox;
    }

    /**
     * Copies the railway to a sandbox and starts the program with its window over the copy.
     *
     * @param fixture the railway's folder, as `Scenario.folderFor` names one
     * @return the running program, to be closed when the class is done with it
     * @throws Exception if the folder cannot be copied or the program cannot start
     */
    public static Restart open(File fixture) throws Exception
    {
        LayoutSandbox opened = null;

        try
        {
            // BEFORE the model, always (OB-111)
            opened = LayoutSandbox.open(fixture);

            Restart restart = new Restart(opened);

            restart.model = start();

            return restart;
        }
        catch (Exception | Error failed)
        {
            // The preference goes back before the failure is reported, as Scenario.open does
            if (opened != null) opened.close();

            throw failed;
        }
    }

    /**
     * @return the program running now; its window is `getGUI()`
     */
    public MarklinControlStation getModel()
    {
        return model;
    }

    /**
     * @return the throwaway copy of the railway the program runs over
     */
    public File getFolder()
    {
        return sandbox.getFolder();
    }

    /**
     * Closes the program as its window's close box does, and starts it again over the same railway and data.
     *
     * @return the program started again
     * @throws Exception if the close or the start fails
     */
    public MarklinControlStation again() throws Exception
    {
        final MarklinControlStation running = model;

        final TrainControlUI ui = (TrainControlUI) running.getGUI();

        // The window's exit, without its System.exit: the database saved, the window's state saved, the window gone
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

        model = start();

        return model;
    }

    /**
     * Throws the window away without saving anything, and puts the layout preference back.
     *
     * @throws Exception from the event thread
     */
    public void close() throws Exception
    {
        try
        {
            final TrainControlUI ui = model == null ? null : (TrainControlUI) model.getGUI();

            if (ui != null) SwingUtilities.invokeAndWait(ui::dispose);
        }
        finally
        {
            sandbox.close();
        }
    }

    /**
     * Starts the program with its window, as a launch does, and loads the setup's active configuration if there is one.
     */
    private static MarklinControlStation start() throws Exception
    {
        MarklinControlStation started = TestStation.simulatedWithWindowInDebug();

        final TrainControlUI ui = (TrainControlUI) started.getGUI();

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

        return started;
    }
}
