package org.traincontrol.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Says the application is starting, during the part of start-up that has nothing to show yet (FR-041).
 *
 * Adam: "while connecting to the CS and before the main UI loads, show a loading popup/overlay like
 * the cs2 sync one."
 *
 * Connecting to a Central Station that is not answering takes as long as its timeout, and until the
 * main window has a model there is nothing to put in it - so the application looks like it failed to
 * start rather than like it is working. That is the same complaint BusyDialog was written for, in the
 * one stretch BusyDialog cannot cover: it is modal on a parent window, and here the window it would be
 * modal on is the one that is not ready.
 *
 * **THIS CLASS USED TO BE A WINDOW, AND THAT WAS OB-170 (2026-09-03).**
 *
 * It was an undecorated always-on-top JWindow, which is what a splash normally is. Showing one during
 * start-up spends the single chance a process gets to put a window in the foreground, so the main
 * window arrived into somebody else's foreground - the application it was launched from - and no
 * amount of raising or focusing got the keyboard back, because a process that is not in the foreground
 * is not allowed to put itself there.
 *
 * Seven passes went at that from the window's end. Adam's own experiment settled it in one run:
 * suppress the splash and the start-up keyboard works. Shown and closed before the window it does not;
 * shown and closed after the window it does not.
 *
 * So the process shows exactly one window, which is what 2.8.1 does and why 2.8.1 works, and what is
 * left here is the notice itself for `TrainControlUI.showConnecting` to put on that one window. Nothing
 * in this class opens anything; if a second start-up window ever comes back, so does OB-170.
 */
public final class StartupSplash
{
    private StartupSplash()
    {
    }

    /**
     * The notice - a spinner and a line of words - for the window that is going to wear it.
     *
     * Centred in whatever it is given, because it is given a whole window now rather than a small box
     * packed around it.
     *
     * @param message what is happening, in words - a spinner alone says only "wait"
     * @return the panel, ready to be made somebody's content
     */
    public static JPanel panel(String message)
    {
        JPanel content = new JPanel(new BorderLayout(0, 8));

        content.setBackground(Color.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(18, 28, 18, 28));

        LoadingSpinner spinner = new LoadingSpinner();
        spinner.setPreferredSize(new java.awt.Dimension(64, 64));

        JLabel says = new JLabel(message, SwingConstants.CENTER);
        says.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setBackground(Color.WHITE);
        middle.add(spinner, BorderLayout.CENTER);
        middle.add(says, BorderLayout.SOUTH);

        JPanel centred = new JPanel(new java.awt.GridBagLayout());
        centred.setBackground(Color.WHITE);
        centred.add(middle);

        content.add(centred, BorderLayout.CENTER);

        return content;
    }
}
