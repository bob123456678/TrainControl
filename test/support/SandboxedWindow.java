package support;

import javax.swing.SwingUtilities;
import org.traincontrol.gui.TrainControlUI;

/**
 * One claim's main window over its own sandbox, and the cleanup such a claim ends with.
 *
 * Made where the claim starts, it reads the file chooser's folder then.  Inside the try, hand it the sandbox as
 * `LayoutSandbox.open` returns it and the window as it is built, so each is held before anything else can throw;
 * `close()` in that try's finally puts the folder back, disposes the window on the event thread and closes the sandbox,
 * in that order, skipping what was never handed over; a step that throws leaves the later ones undone.
 *
 *     final support.SandboxedWindow sandboxed = new support.SandboxedWindow();
 *     try
 *     {
 *         sandboxed.hold(support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"))).simulated();
 *         ui[0] = sandboxed.hold(openTheWindow());
 *         ...
 *     }
 *     finally
 *     {
 *         sandboxed.close();
 *     }
 */
public final class SandboxedWindow
{
    private final String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

    private LayoutSandbox sandbox;

    private TrainControlUI ui;

    /** Holds the sandbox for {@link #close}, and returns it. */
    public LayoutSandbox hold(LayoutSandbox opened)
    {
        sandbox = opened;

        return opened;
    }

    /** Holds the window for {@link #close}, and returns it. */
    public TrainControlUI hold(TrainControlUI opened)
    {
        ui = opened;

        return opened;
    }

    /** Builds the main window on a station, as every window test does, holds it for {@link #close}, and returns it. */
    public TrainControlUI openWindow(org.traincontrol.marklin.MarklinControlStation station) throws java.io.IOException
    {
        TrainControlUI opened = Components.onTheEventThread(TrainControlUI::new);

        opened.setViewListener(station, new java.util.concurrent.CountDownLatch(1));

        return hold(opened);
    }

    /** The sandbox held, or null before one is. */
    public LayoutSandbox sandbox()
    {
        return sandbox;
    }

    /**
     * Puts the folder back, then disposes the window held and closes the sandbox held.
     */
    public void close() throws Exception
    {
        putTheFolderBack(folderWas);

        if (ui != null)
        {
            final TrainControlUI closing = ui;

            SwingUtilities.invokeAndWait(() -> closing.dispose());
        }

        if (sandbox != null) sandbox.close();
    }

    /**
     * A main window with no model, built on the event thread while a sandbox holds the layout preference (OB-111), so
     * its constructor never reads the operator's railway; the sandbox is closed again before this returns.
     */
    public static TrainControlUI build() throws Exception
    {
        LayoutSandbox sandbox = null;

        final TrainControlUI[] built = new TrainControlUI[1];

        try
        {
            // Inside the try, so nothing between the open and the close can leave the preference behind.
            sandbox = LayoutSandbox.open();

            SwingUtilities.invokeAndWait(() -> built[0] = new TrainControlUI());
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }

        return built[0];
    }

    /** The chooser remembers the folder a file was chosen in; puts the operator's back, unset included. */
    public static void putTheFolderBack(String folderWas)
    {
        if (folderWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_FOLDER);
        else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, folderWas);
    }
}
