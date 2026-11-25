package org.traincontrol.gui.widgets;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.autonomyeditor.AutonomyEditorPanel;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * The "there is a reason, hover me" mark, for any window: an image rather than a character, as U+24D8 has no glyph in
 * the UI font on some machines (OB-063).
 */
public final class InfoMark
{
    private static final ImageIcon ICON = load();

    /** Long enough to read a sentence; Swing's 4 seconds is not. */
    private static final int DISMISS_MS = 30000;

    /** The mark the pointer is on, and the dismiss delay it found, put back as the pointer leaves or the mark goes. */
    private static JLabel hovered;
    private static int normal;

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
            BufferedImage read = javax.imageio.ImageIO.read(url);
            BufferedImage out = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
            int[] pixels = read.getRGB(0, 0, read.getWidth(), read.getHeight(), null, 0, read.getWidth());

            for (int i = 0; i < pixels.length; i++)
            {
                // The dark disk (a GIF's pixels are one gray or white, so any channel tells)
                if ((pixels[i] & 0xff) < 128) pixels[i] = (pixels[i] & 0xff000000) | GRAY;
            }

            out.setRGB(0, 0, read.getWidth(), read.getHeight(), pixels, 0, read.getWidth());
            return new org.traincontrol.gui.widgets.SharpIcon(out, InfoMark::art,
                org.traincontrol.gui.widgets.SharpIcon.Fit.WHOLE);
        }
        catch (java.io.IOException | RuntimeException ex)
        {
            return new ImageIcon(url);
        }
    }

    /** The mark at `size` screen pixels in the GIF's proportions: its dot and stem on whole pixels, centered (D191). */
    private static java.awt.Image art(int width, int height)
    {
        int size = Math.min(width, height);
        int stem = Math.max(1, 2 * (int) Math.round((2.0 * size / 14 - size % 2) / 2) + size % 2);
        int left = (size - stem) / 2, dot = (int) Math.round(3.0 * size / 14);
        int top = (int) Math.round(6.0 * size / 14), bottom = size - dot;
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(GRAY));
        g.fillOval(0, 0, size, size);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(left, dot, stem, Math.max(1, (int) Math.round(size / 14.0)));
        g.fillRect(left, top, stem, bottom - top);
        g.dispose();
        return out;
    }

    /** The one icon; null when the image is missing, which setIcon accepts. */
    public static ImageIcon icon()
    {
        return ICON;
    }

    /**
     * A mark whose tooltip shows the sentence, wrapped, and stays long enough to read it; a click shows it at once
     * (Adam: "click on any i should immediately show the tooltip, so you don't have to wait for the hover timeout").
     *
     * @param text what the mark explains
     */
    public static JLabel label(String text)
    {
        final JLabel mark = new JLabel(ICON);

        mark.setToolTipText(AutonomyEditorPanel.wrapped(text));

        // A window closed under the pointer sends no exit
        mark.addHierarchyListener(e -> { if (!mark.isShowing()) leave(mark); });
        mark.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (hovered == null) normal = ToolTipManager.sharedInstance().getDismissDelay();
                hovered = mark;
                ToolTipManager.sharedInstance().setDismissDelay(DISMISS_MS);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                leave(mark);
            }

            // Later, as Swing's own press handler hides the tooltip and may run after this one
            @Override
            public void mousePressed(MouseEvent e)
            {
                javax.swing.SwingUtilities.invokeLater(() -> showNow(mark, e.getX(), e.getY()));
            }
        });

        return mark;
    }

    private static void leave(JLabel mark)
    {
        if (hovered != mark) return;
        hovered = null;
        ToolTipManager.sharedInstance().setDismissDelay(normal);
    }

    /** Shows the mark's tooltip as a hover would, without the wait; every other component's wait is left as it was. */
    private static void showNow(JLabel mark, int x, int y)
    {
        ToolTipManager tips = ToolTipManager.sharedInstance();
        int initial = tips.getInitialDelay();
        tips.setInitialDelay(0);

        try
        {
            tips.mouseMoved(new MouseEvent(mark, MouseEvent.MOUSE_MOVED,
                System.currentTimeMillis(), 0, x, y, 0, false));
        }
        finally
        {
            tips.setInitialDelay(initial);
        }
    }
}
