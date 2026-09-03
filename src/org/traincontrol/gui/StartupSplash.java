package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Says the application is starting, during the part of start-up that has no window yet (FR-041).
 *
 * Adam: "while connecting to the CS and before the main UI loads, show a loading popup/overlay like
 * the cs2 sync one."
 *
 * Connecting to a Central Station that is not answering takes as long as its timeout, and until the
 * main window is built there is nothing on screen at all - so the application looks like it failed to
 * start rather than like it is working. That is the same complaint BusyDialog was written for, in the
 * one stretch BusyDialog cannot cover: it is modal on a parent window, and here there is no window to
 * be modal on.
 *
 * A JWindow rather than a JDialog for exactly that reason - it needs no owner. Undecorated and
 * always on top, so it reads as a splash rather than as something to be dismissed, and it has no close
 * box because closing it would not stop what it is reporting.
 *
 * NOT shown when the caller did not ask for a window. Every test builds its model with showUI false,
 * and a suite that puts windows on the operator's screen is a suite he stops running - a modal one
 * did exactly that on 2026-08-28.
 */
public final class StartupSplash
{
    /**
     * A way to take the splash out without deleting it, kept because it earned its keep once.
     *
     * **It answered OB-170.**  Seven passes changed how the splash or the window behaves and asked
     * Adam to judge the result; six were wrong.  Switching the splash off answered the question in one
     * run - *"it works now"* - and turned a hypothesis about Windows into a fact about this
     * application.
     *
     * False, and it should stay false: a start-up that shows nothing at all for the length of a
     * Central Station timeout is the complaint FR-041 was written for, and the application looks like
     * it failed to start.  What the splash was actually doing wrong is at `closeIfShown`'s call site in
     * `MarklinControlStation.init`, and it was a matter of WHEN rather than whether.
     */
    public static final boolean SUPPRESSED = false;

    private final java.awt.Window window;

    private StartupSplash(java.awt.Window window)
    {
        this.window = window;
    }

    /**
     * Puts the splash on screen, or answers null when there is nowhere to put one.
     *
     * Built on the event thread whichever thread asks, because start-up runs on the main thread and a
     * window put together off the event thread is a race that usually wins.
     *
     * @param message what is happening, in words - a spinner alone says only "wait"
     * @return a handle to close it with, or null when no splash was shown
     */
    public static StartupSplash show(String message)
    {
        return show(message, null);
    }

    /**
     * The same, owned by the window it is standing in for (OB-170, 2026-09-03).
     *
     * **Ownership is the whole of this overload.**  An unowned `JWindow` is a top-level window in its
     * own right, and when it is destroyed the platform hands the foreground to the next window in the
     * Z-order - which is whatever the operator was using, because our own window is not up yet or has
     * only just arrived.  A dialog owned by the frame belongs to that frame: its activation is the
     * owner's, and when it goes there is an owner to fall back to rather than a stranger.
     *
     * Adam measured what the unowned version cost: with the splash suppressed entirely the start-up
     * keyboard works, and with it shown - closed early or closed late - it does not.
     *
     * @param message what is happening, in words
     * @param owner the window this is standing in for, or null when there is none yet
     * @return a handle to close it with, or null when no splash was shown
     */
    public static StartupSplash show(String message, java.awt.Window owner)
    {
        if (GraphicsEnvironment.isHeadless()) return null;

        // The switch above, and the whole of what it does.  Nothing else in this class or its callers
        // needs to know: `closeIfShown` already takes null, because the start-up path has several ways
        // out and one of them is "no splash was built".
        if (SUPPRESSED) return null;

        final java.awt.Window[] built = new java.awt.Window[1];

        Runnable make = () ->
        {
            // OWNED WHERE THERE IS AN OWNER.  A JDialog is the only Swing window that can have one,
            // and undecorated it looks exactly like the JWindow it replaces.
            java.awt.Window w;

            if (owner != null)
            {
                javax.swing.JDialog dialog = new javax.swing.JDialog(owner);

                dialog.setUndecorated(true);

                w = dialog;
            }
            else
            {
                w = new JWindow();
            }

            JPanel content = new JPanel(new BorderLayout(0, 8));

            content.setBackground(Color.WHITE);
            content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(18, 28, 18, 28)));

            LoadingSpinner spinner = new LoadingSpinner();
            spinner.setPreferredSize(new java.awt.Dimension(64, 64));

            JLabel says = new JLabel(message, SwingConstants.CENTER);
            says.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            content.add(spinner, BorderLayout.CENTER);
            content.add(says, BorderLayout.SOUTH);

            ((javax.swing.RootPaneContainer) w).setContentPane(content);

            // AND IT NEVER TAKES THE FOREGROUND (OB-170, sixth pass).
            //
            // Adam asked on his first report whether the splash was involved, and I said it could not
            // be because it closes before the window is shown.  That answered a question about
            // ORDERING; the cost is not in the order.
            //
            // Windows gives a process ONE chance to put a window in the foreground when the user
            // starts it, and showing a top-level window spends it.  A splash that is always-on-top,
            // visible for the whole of the connect and then destroyed spends that right and hands the
            // foreground back to whatever was there before - which is the application the operator
            // launched us from, and which is exactly what he reported four times: "the previous active
            // application window retains focus", "rather the parent app like the python or netbeans
            // remains".
            //
            // 2.8.1 has no splash, and 2.8.1's keyboard works on startup.  That is the whole of the
            // evidence and it is the first piece any of the five earlier passes had.
            //
            // Both lines, because they are different questions: the first is the platform's
            // no-activate window style, the second is whether showing it asks for activation.  A
            // splash wants neither - it cannot be typed into and it cannot be clicked.
            w.setFocusableWindowState(false);
            w.setAutoRequestFocus(false);

            w.setAlwaysOnTop(true);
            w.pack();
            w.setLocationRelativeTo(null);
            w.setVisible(true);

            built[0] = w;
        };

        try
        {
            if (SwingUtilities.isEventDispatchThread()) make.run();
            else SwingUtilities.invokeAndWait(make);
        }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();

            return null;
        }
        catch (java.lang.reflect.InvocationTargetException couldNotBuild)
        {
            // Nothing here is worth failing a start-up over: the application works perfectly well
            // without a splash, and the alternative is refusing to start because the reassurance
            // could not be drawn.
            return null;
        }

        return built[0] == null ? null : new StartupSplash(built[0]);
    }

    /**
     * Takes it off the screen.
     *
     * Safe to call from any thread and more than once, because the caller that closes this is a
     * start-up path with several ways out - and a splash left on screen over a working application is
     * a worse fault than never showing one.
     */
    public void close()
    {
        Runnable go = () ->
        {
            window.setVisible(false);
            window.dispose();
        };

        if (SwingUtilities.isEventDispatchThread()) go.run();
        else SwingUtilities.invokeLater(go);
    }

    /**
     * Closes a splash that may not exist.
     *
     * @param splash the handle from {@link #show}, or null
     */
    public static void closeIfShown(StartupSplash splash)
    {
        if (splash != null) splash.close();
    }
}
