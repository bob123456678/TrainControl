package org.traincontrol.gui.widgets;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Says the application is starting, during the part of start-up that has nothing to show yet (FR-041): "while
 * connecting to the CS and before the main UI loads, show a loading popup/overlay like the cs2 sync one."
 *
 * Not a window of its own (OB-170): a second start-up window spends the one chance a process gets to put a window in
 * the foreground, so the main window arrived behind the application it was launched from and the keyboard never came
 * back.  So the process shows exactly one window, and this is only the notice for `TrainControlUI.showConnecting` to
 * put on it.  Nothing in this class opens anything.
 */
public final class StartupSplash
{
    private StartupSplash()
    {
    }

    /**
     * The notice - a spinner and a line of words - for the window that is going to wear it.
     *
     * Centered in whatever it is given, because it is given a whole window now rather than a small box
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
