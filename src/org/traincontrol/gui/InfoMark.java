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

    /** The disk's dark pixels become this gray, so the mark does not draw the eye; alpha is kept. */
    static final int GRAY = 0xCCCCCC;

    private static ImageIcon load()
    {
        java.net.URL url = TrainControlUI.class.getResource("resources/info.gif");

        if (url == null) return null;

        try
        {
            java.awt.image.BufferedImage read = javax.imageio.ImageIO.read(url);
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(read.getWidth(), read.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);

            int[] pixels = read.getRGB(0, 0, read.getWidth(), read.getHeight(), null, 0, read.getWidth());

            for (int i = 0; i < pixels.length; i++)
            {
                // The dark disk (a GIF's pixels are one gray or white, so any channel tells)
                if ((pixels[i] & 0xff) < 128) pixels[i] = (pixels[i] & 0xff000000) | GRAY;
            }

            out.setRGB(0, 0, read.getWidth(), read.getHeight(), pixels, 0, read.getWidth());

            return new ImageIcon(out);
        }
        catch (java.io.IOException | RuntimeException ex)
        {
            return new ImageIcon(url);
        }
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
