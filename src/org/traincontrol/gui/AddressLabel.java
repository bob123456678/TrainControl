package org.traincontrol.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import javax.swing.JLabel;

/**
 * A square's address on the diagram: red letters with a white halo round each one, and nothing behind the rest (Adam,
 * 2026-10-09: "Can we prettify address labels to have a white halo outline around the red text rather than a
 * rectangle around them?").
 *
 * It was a label with a translucent white box behind it, which covered the corner of the square's track and signal
 * art.  A halo keeps the number readable on black track and on white alike and covers only what the letters would.
 *
 * Still a JLabel, so that the grid's mouse handling, its tooltip and its place in the layout are as they were.  Its
 * text is the address and, where the decoder is not MM2, the protocol on a second line.
 */
public final class AddressLabel extends JLabel
{
    /** The white of the halo, nearly opaque, so the letters part from whatever is under them. */
    private static final Color HALO = new Color(255, 255, 255, 235);

    /** The lines drawn, top to bottom. */
    private String[] lines = new String[0];

    AddressLabel()
    {
        setOpaque(false);
    }

    /**
     * The address and, where there is one, the protocol under it.
     *
     * @param lines the lines, top to bottom; null or empty ones are left out
     */
    void setLines(String... lines)
    {
        java.util.List<String> kept = new java.util.ArrayList<>();

        for (String line : lines)
        {
            if (line != null && !line.isEmpty()) kept.add(line);
        }

        this.lines = kept.toArray(new String[0]);

        // the text a tooltip, a test or an accessibility reader asks for: the lines, a space apart
        setText(String.join(" ", this.lines));

        revalidate();
        repaint();
    }

    /** How wide the halo's stroke is: two-sevenths of the letters' size, and never so thin it disappears. */
    private float haloWidth()
    {
        return Math.max(2.5f, getFont().getSize2D() / 3.5f);
    }

    /** The room left round the letters for the halo. */
    private int pad()
    {
        return (int) Math.ceil(haloWidth() / 2.0);
    }

    @Override
    public Dimension getPreferredSize()
    {
        if (getFont() == null || lines.length == 0) return super.getPreferredSize();

        FontMetrics fm = getFontMetrics(getFont());

        int wide = 0;

        for (String line : lines) wide = Math.max(wide, fm.stringWidth(line));

        return new Dimension(wide + 2 * pad(), fm.getHeight() * lines.length + 2 * pad());
    }

    @Override
    public Dimension getMinimumSize()
    {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if (getFont() == null || lines.length == 0) return;

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            Font font = getFont();
            FontMetrics fm = g2.getFontMetrics(font);

            int pad = pad();

            for (int i = 0; i < lines.length; i++)
            {
                TextLayout layout = new TextLayout(lines[i], font, g2.getFontRenderContext());

                Shape letters = layout.getOutline(AffineTransform.getTranslateInstance(pad,
                    pad + fm.getAscent() + i * fm.getHeight()));

                // THE HALO FIRST, the letters over it: a round-joined stroke round each outline, so it follows the
                // letters' shapes rather than boxing them
                g2.setColor(HALO);
                g2.setStroke(new BasicStroke(haloWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(letters);

                g2.setColor(getForeground());
                g2.fill(letters);
            }
        }
        finally
        {
            g2.dispose();
        }
    }
}
