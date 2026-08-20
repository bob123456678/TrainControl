package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;

/**
 * One piece of a route's logic, drawn as an oval.
 *
 * The logic is built rather than typed, so every part of it has to be a thing on screen that can be
 * pointed at: a term, a joining word, a bracket. An oval says "this is one object" in a way a
 * rectangle beside other rectangles does not - a row of square buttons reads as a toolbar, and a
 * toolbar is a set of actions rather than a sentence.
 *
 * Three states, and they are the whole of the interaction: ordinary, picked out, and about to be
 * deleted. Picked is filled in the interface's blue with white on it, which is the strongest signal
 * available without inventing a colour; deleting shows a cross, and only while the mode is on, so
 * nothing can be removed by a stray click at any other time.
 */
public class Bubble extends JButton
{
    private static final Color LINE = new Color(150, 150, 150);
    private static final Color PICKED = new Color(0, 0, 155);
    private static final Color DELETING = new Color(170, 60, 60);

    private boolean picked;
    private boolean deleting;

    /**
     * @param text what it says
     */
    public Bubble(String text)
    {
        super(text);

        setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 12));
        setFocusable(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setMargin(new java.awt.Insets(2, 10, 2, 10));
    }

    /**
     * @param picked whether it is picked out for grouping
     */
    public void setPicked(boolean picked)
    {
        this.picked = picked;

        repaint();
    }

    /**
     * @return whether it is picked out
     */
    public boolean isPicked()
    {
        return picked;
    }

    /**
     * @param deleting whether the window is in its deleting mode, so this shows a cross
     */
    public void setDeleting(boolean deleting)
    {
        this.deleting = deleting;

        repaint();
    }

    @Override
    public Dimension getPreferredSize()
    {
        Dimension out = super.getPreferredSize();

        // Room for the cross, and for the rounding, so a bubble does not change size when the mode
        // changes - a row that reflows as a button is pressed is a row nobody can aim at
        return new Dimension(out.width + 18, out.height + 4);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // A full-height arc, so the ends are semicircles rather than rounded corners
            g2.setColor(picked ? PICKED : Color.WHITE);
            g2.fillRoundRect(0, 0, width - 1, height - 1, height, height);

            g2.setColor(picked ? PICKED : LINE);
            g2.drawRoundRect(0, 0, width - 1, height - 1, height, height);

            g2.setColor(picked ? Color.WHITE : (isEnabled() ? Color.BLACK : Color.GRAY));
            g2.setFont(getFont());

            java.awt.FontMetrics metrics = g2.getFontMetrics();

            String text = getText();

            int textWidth = metrics.stringWidth(text);
            int room = deleting ? width - 16 : width;

            g2.drawString(text, (room - textWidth) / 2,
                (height - metrics.getHeight()) / 2 + metrics.getAscent());

            if (deleting)
            {
                int size = Math.max(6, height / 3);
                int right = width - size - 6;
                int top = (height - size) / 2;

                g2.setColor(picked ? Color.WHITE : DELETING);
                g2.setStroke(new java.awt.BasicStroke(1.6f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));

                g2.drawLine(right, top, right + size, top + size);
                g2.drawLine(right + size, top, right, top + size);
            }
        }
        finally
        {
            g2.dispose();
        }
    }
}
