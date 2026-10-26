package org.traincontrol.gui.widgets;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import org.traincontrol.gui.TrainControlUI;

/**
 * Says that something slow is happening, and gets out of the way when it stops.  Loading or initializing a layout does
 * its work on a background thread, so a closed file chooser followed by nothing reads as the command having been
 * ignored.  Modal on purpose: the work replaces the locomotive, route and layout databases wholesale.  The slow half
 * runs on the worker and the half that touches Swing runs in whenDone on the event thread.
 */
public final class BusyDialog extends JDialog
{
    // The application's look and feel, before this class can be instantiated.  A static block rather than the first
    // line of the constructor: `JDialog`'s own constructor creates the root pane before any constructor body here, and
    // its UI comes from the look and feel installed at that instant.  Built cold, as a test does, the window got a
    // `MetalRootPaneUI` and an operating-system title bar (166x77) where the running program gets FlatLaf's own
    // (166x49).  Headless-guarded because a static initializer that throws leaves the class unusable for the rest of
    // the process.
    static
    {
        if (!java.awt.GraphicsEnvironment.isHeadless()) TrainControlUI.installLookAndFeel();
    }

    private BusyDialog(Window parent, String message)
    {
        super(parent, Dialog.ModalityType.APPLICATION_MODAL);


        // The window icon (OB-124).
        TrainControlUI.applyWindowIcon(this);

        setUndecorated(true);
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(java.awt.Color.GRAY),
            BorderFactory.createEmptyBorder(18, 28, 18, 28)));

        LoadingSpinner spinner = new LoadingSpinner();
        spinner.setPreferredSize(new java.awt.Dimension(64, 64));

        JLabel says = new JLabel(message, SwingConstants.CENTER);
        says.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        content.add(spinner, BorderLayout.CENTER);
        content.add(says, BorderLayout.SOUTH);

        setContentPane(content);
        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * Puts the spinner up now, and hands back the thing that takes it down (OB-140).  `run` owns the work as well as
     * the dialog, which is no use to a caller already on a background thread doing the slow thing itself: `run` would
     * hand the work to a second thread and return before the value arrived.  That is `TrainControlUI.syncWithCS2`'s
     * shape, and sixteen doors go through it.
     *
     * Safe to close from any thread, and before the dialog has appeared: both halves land on the event thread in the
     * order posted, and the flag covers a close queued behind a show that has not run yet.
     *
     * @param parent the window to center on
     * @param message what is happening, in words
     * @return the handle that dismisses it
     */
    public static Closer showUntilClosed(Window parent, String message)
    {
        Closer closer = new Closer(parent, message);

        // Posted rather than run: showing a modal dialog blocks its thread in a nested event loop, and the caller has
        // work to do.
        SwingUtilities.invokeLater(closer::open);

        return closer;
    }

    /**
     * A spinner that is already on screen, and the way to take it down.
     */
    public static final class Closer
    {
        private final Window parent;
        private final String message;

        private BusyDialog dialog;
        private boolean closed;

        private Closer(Window parent, String message)
        {
            this.parent = parent;
            this.message = message;
        }

        /**
         * Shows it, on the event thread, unless it has already been closed.
         */
        private void open()
        {
            synchronized (this)
            {
                // Closed before it ever opened, when the work finishes before this reaches the front of the queue:
                // showing it now would leave an undecorated modal dialog with nothing to dismiss it.
                if (closed) return;

                dialog = new BusyDialog(parent, message);
            }

            // Blocks here, in a nested event loop, until close() disposes it - the same arrangement
            // `run` uses, and the reason the spinner keeps animating.
            dialog.setVisible(true);
        }

        /**
         * Takes it down.  Callable from any thread, and more than once.
         */
        public void close()
        {
            SwingUtilities.invokeLater(() ->
            {
                BusyDialog showing;

                synchronized (this)
                {
                    closed = true;
                    showing = dialog;
                }

                if (showing != null) showing.dispose();
            });
        }
    }

    /**
     * Runs slow work behind a spinner, then hands back to the event thread.
     *
     * @param parent the window to center on
     * @param message what is happening, in words - a spinner alone says only "wait"
     * @param work the slow part.  Runs off the event thread, so it must not touch Swing
     * @param whenDone what to do with the result, on the event thread.  May be null
     */
    public static void run(Window parent, String message, Runnable work, Runnable whenDone)
    {
        // Called from the event thread, always: the dispose below is posted with invokeLater and the show blocks until
        // it runs, so off the EDT the two race, and the dispose can run first, leaving an undecorated application-modal
        // window nothing can close and hanging the program.  Bounced rather than refused, so a caller on the wrong
        // thread still works.
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> run(parent, message, work, whenDone));
            return;
        }

        final BusyDialog dialog = new BusyDialog(parent, message);

        Thread worker = new Thread(() ->
        {
            try
            {
                work.run();
            }
            finally
            {
                // In a finally, or work that throws leaves a modal dialog on screen with no way to
                // dismiss it - the window has no close button, which is the point of it
                SwingUtilities.invokeLater(() ->
                {
                    dialog.dispose();

                    if (whenDone != null) whenDone.run();
                });
            }
        }, "BusyDialog worker");

        worker.setDaemon(true);
        worker.start();

        // Blocks here until the worker disposes it.  Safe because the dialog is shown from the event
        // thread and Swing runs a nested event loop for a modal one, so the spinner still animates.
        dialog.setVisible(true);
    }
}
