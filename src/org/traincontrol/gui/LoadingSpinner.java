package org.traincontrol.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * The "still drawing" mark shown in place of a track diagram while its tiles are being decoded.
 *
 * A diagram used to appear in two stages: its text labels at once, because text needs no image, and
 * the track a second or so later as the decodes came back off the pool.  The order is an accident of
 * how the work is split, but it reads as the diagram being wrong and then correcting itself - the
 * labels look like they are floating on nothing.  Showing one thing that says "not yet" is honest
 * about the same wait.
 *
 * Drawn rather than loaded from a GIF, because an animation file is one more asset to ship, scale for
 * a high-DPI screen and keep in step with the theme.  An arc and a timer are fewer moving parts.
 *
 * The timer is started and stopped with the component's visibility on screen, so a spinner left in a
 * window nobody is looking at is not repainting a hidden pixel forty times a second.
 */
public class LoadingSpinner extends JPanel
{
    /**
     * How often the arc advances.  Twelve steps a second is smooth enough to read as motion and slow
     * enough that it costs nothing; a spinner is not something anybody studies.
     */
    private static final int FRAME_MS = 80;

    private static final int STEP_DEGREES = 30;

    private final Timer timer;

    private int angle;

    public LoadingSpinner()
    {
        setOpaque(false);

        // Matches the diagram's own background so the swap to the finished grid is not also a flash of
        // a different colour
        setBackground(Color.WHITE);

        timer = new Timer(FRAME_MS, e ->
        {
            angle = (angle + STEP_DEGREES) % 360;
            repaint();
        });

        timer.setRepeats(true);
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify()
    {
        timer.stop();
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(120, 120);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int span = Math.max(16, Math.min(getWidth(), getHeight()) / 3);
            int x = (getWidth() - span) / 2;
            int y = (getHeight() - span) / 2;

            g2.setStroke(new BasicStroke(Math.max(2f, span / 10f), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

            // The faint full ring says how far round the moving part goes, so a single arc on white
            // does not read as a stray mark
            g2.setColor(new Color(0, 0, 0, 30));
            g2.drawOval(x, y, span, span);

            g2.setColor(new Color(0, 0, 0, 130));
            g2.drawArc(x, y, span, span, angle, 90);
        }
        finally
        {
            g2.dispose();
        }
    }
}
