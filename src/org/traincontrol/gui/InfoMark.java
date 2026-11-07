package org.traincontrol.gui;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;

/**
 * The "there is a reason, hover me" mark, for any window: an image rather than a character, as U+24D8 has no glyph in
 * the UI font on some machines (OB-063).
 */
public final class InfoMark
{
    private static final ImageIcon ICON = load();

    /** Long enough to read a sentence; Swing's 4 seconds is not. */
    private static final int DISMISS_MS = 30000;

    private InfoMark()
    {
    }

    private static ImageIcon load()
    {
        java.net.URL url = TrainControlUI.class.getResource("resources/info.gif");

        return url == null ? null : new ImageIcon(url);
    }

    /** The one icon; null when the image is missing, which setIcon accepts. */
    public static ImageIcon icon()
    {
        return ICON;
    }

    /**
     * A mark whose tooltip shows the sentence, wrapped, and stays long enough to read it.
     *
     * @param text what the mark explains
     */
    public static JLabel label(String text)
    {
        final JLabel mark = new JLabel(ICON);

        mark.setToolTipText(AutonomyEditorPanel.wrapped(text));

        mark.addMouseListener(new java.awt.event.MouseAdapter()
        {
            private int was;

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e)
            {
                was = ToolTipManager.sharedInstance().getDismissDelay();
                ToolTipManager.sharedInstance().setDismissDelay(DISMISS_MS);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e)
            {
                ToolTipManager.sharedInstance().setDismissDelay(was);
            }
        });

        return mark;
    }
}
