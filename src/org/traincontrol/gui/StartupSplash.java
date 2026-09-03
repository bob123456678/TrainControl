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
     * **TEMPORARY, 2026-09-03: the splash is switched off while OB-170 is settled.**
     *
     * Adam, after seven attempts at the start-up keyboard: *"try temporarily turning off the startup
     * overlap."*  It is the experiment that settles it either way, and it is the one thing none of the
     * seven passes did - each of them changed how the splash or the window behaves and then asked him
     * to judge the result, which is the slowest possible way to test a hypothesis.
     *
     * **Read the answer like this.**  If the keyboard works on start-up with this true, the splash is
     * the cause and what remains is to find a way of reassuring the operator during a slow connect that
     * does not spend the process's one chance at the foreground - showing it AFTER the main window is
     * up, or drawing it into that window rather than into one of its own.  If the keyboard is still
     * dead, the splash is innocent, seven passes have been aimed at the wrong thing, and the next step
     * is to bisect the commits between 2.8.1 and here rather than to reason about Windows.
     *
     * **Set this back to false either way.**  A start-up that shows nothing at all for the length of a
     * Central Station timeout is the complaint FR-041 was written for, and it is a real one: the
     * application looks like it failed to start.  This is a diagnostic, not a decision.
     */
    public static final boolean SUPPRESSED = true;

    private final JWindow window;

    private StartupSplash(JWindow window)
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
        if (GraphicsEnvironment.isHeadless()) return null;

        // The switch above, and the whole of what it does.  Nothing else in this class or its callers
        // needs to know: `closeIfShown` already takes null, because the start-up path has several ways
        // out and one of them is "no splash was built".
        if (SUPPRESSED) return null;

        final JWindow[] built = new JWindow[1];

        Runnable make = () ->
        {
            JWindow w = new JWindow();

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

            w.setContentPane(content);

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
