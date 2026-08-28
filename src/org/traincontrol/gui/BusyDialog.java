package org.traincontrol.gui;

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

/**
 * Says that something slow is happening, and gets out of the way when it stops.
 *
 * Loading a layout from disk and initializing a new one both do their work on a background thread, so
 * the file chooser closed and then nothing happened - for as long as it took to fetch and parse every
 * page - and the first sign of life was the finished diagram appearing. Being asked to pick a folder
 * and then shown nothing at all reads as the command having been ignored, which is the one thing it
 * was not doing.
 *
 * Modal on purpose. The work replaces the locomotive, route and layout databases wholesale, so a user
 * clicking around in the meantime is clicking at things that are about to be swapped underneath them.
 *
 * The split this enforces matters as much as the spinner: the slow half runs on the worker, the half
 * that touches Swing runs in whenDone on the event thread. The path this was first used on ran BOTH on
 * a raw thread, building the track diagram off the EDT.
 */
public final class BusyDialog extends JDialog
{
    private BusyDialog(Window parent, String message)
    {
        super(parent, Dialog.ModalityType.APPLICATION_MODAL);

        // OB-124, found by sweeping the others after the crop window.
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
     * Runs slow work behind a spinner, then hands back to the event thread.
     *
     * @param parent the window to centre on
     * @param message what is happening, in words - a spinner alone says only "wait"
     * @param work the slow part.  Runs off the event thread, so it must not touch Swing
     * @param whenDone what to do with the result, on the event thread.  May be null
     */
    public static void run(Window parent, String message, Runnable work, Runnable whenDone)
    {
        // Called from the event thread, always.
        //
        // The dispose below is posted with invokeLater and the show below that blocks until it runs.
        // Off the EDT those two race: the dispose can run before the dialog is ever shown, and what is
        // then displayed is an undecorated, application-modal window with nothing left alive to close
        // it and no close button - the whole program hangs, unrecoverably.  Bounced rather than
        // refused, so a caller on the wrong thread still works.
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
