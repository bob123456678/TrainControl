package org.traincontrol.gui.locomotive;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicGraphicsUtils;

/**
 * Toggle buttons drawn as one rounded outline with a thin divider between segments, the chosen segment filled with the
 * theme's accent.  FlatLaf 3.7.2 has no segmented buttons, so the outline, dividers and fills are painted here, every
 * color asked of the look and feel at paint time.  Left and Right move the choice from a focused segment.
 */
public class SegmentedControl extends JPanel
{
    private final ButtonGroup group = new ButtonGroup();

    public SegmentedControl()
    {
        super(new GridLayout(1, 0, 0, 0));

        setOpaque(false);
    }

    /**
     * Adds a segment at the right-hand end.
     *
     * @param text what it says
     * @param name its component name
     * @param chosen whether it is the chosen one
     * @return the segment, whose action listeners are the caller's
     */
    public JToggleButton addSegment(String text, String name, boolean chosen)
    {
        Segment segment = new Segment(text);

        segment.setName(name);
        segment.setSelected(chosen);

        for (int[] key : new int[][] {{java.awt.event.KeyEvent.VK_LEFT, -1}, {java.awt.event.KeyEvent.VK_RIGHT, 1}})
        {
            String action = "segment." + key[1];

            segment.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key[0], 0), action);
            segment.getActionMap().put(action, new AbstractAction()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    int to = indexOf(segment) + key[1];

                    if (to >= 0 && to < getComponentCount())
                    {
                        JToggleButton neighbor = (JToggleButton) getComponent(to);

                        neighbor.doClick(0);
                        neighbor.requestFocusInWindow();
                    }
                }
            });
        }

        group.add(segment);
        add(segment);

        return segment;
    }

    private int indexOf(Segment segment)
    {
        for (int i = 0; i < getComponentCount(); i++)
        {
            if (getComponent(i) == segment) return i;
        }

        return -1;
    }

    /** The oval all the segments sit in, as wide and tall as the control, with the outline's half pixel left out. */
    private Shape oval(double inset)
    {
        double arc = getHeight() - 2 * inset;

        return new RoundRectangle2D.Double(inset, inset, getWidth() - 2 * inset, getHeight() - 2 * inset, arc, arc);
    }

    private static Color themeColor(Color fallback, String... keys)
    {
        for (String key : keys)
        {
            Color c = UIManager.getColor(key);

            if (c != null) return c;
        }

        return fallback;
    }

    /** The color of the outline and of the dividers. */
    public Color lineColor()
    {
        return themeColor(Color.GRAY, "Component.borderColor", "Button.borderColor", "Separator.foreground");
    }

    /** The color of the chosen segment. */
    public Color chosenColor()
    {
        return themeColor(new Color(0x26, 0x75, 0xBF), "Component.accentColor", "Button.default.background");
    }

    @Override
    protected void paintChildren(Graphics g)
    {
        super.paintChildren(g);

        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(lineColor());

            for (int i = 1; i < getComponentCount(); i++)
            {
                float x = getComponent(i).getX() + 0.5f;

                g2.draw(new Line2D.Float(x, 0.5f, x, getHeight() - 0.5f));
            }

            g2.draw(oval(0.5));
        }
        finally
        {
            g2.dispose();
        }
    }

    /** One segment: paints its own fill, clipped to the control's oval, and its text; the control paints the lines. */
    private final class Segment extends JToggleButton
    {
        private Segment(String text)
        {
            super(text);
        }

        @Override
        public void updateUI()
        {
            super.updateUI();

            setBorder(null);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
        }

        @Override
        public Dimension getPreferredSize()
        {
            FontMetrics fm = getFontMetrics(getFont());

            return new Dimension(fm.stringWidth(getText()) + 32, new JButton(getText()).getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();

            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color fill = themeColor(Color.WHITE, "Button.background");

                if (isSelected()) fill = chosenColor();
                else if (getModel().isPressed()) fill = themeColor(fill, "Button.pressedBackground");
                else if (getModel().isRollover()) fill = themeColor(fill, "Button.hoverBackground");

                // The control's oval, in this segment's own coordinates, keeps the end segments round
                g2.clip(AffineTransform.getTranslateInstance(-getX(), -getY()).createTransformedShape(oval(0)));
                g2.setColor(fill);
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setColor(isSelected() ? readableOn(fill) : themeColor(getForeground(), "Button.foreground"));
                g2.setFont(getFont());

                FontMetrics fm = g2.getFontMetrics();

                BasicGraphicsUtils.drawStringUnderlineCharAt(g2, getText(), -1, (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());

                // The chosen segment is where the arrow keys leave the focus, and is plain enough already
                if (isFocusOwner() && !isSelected())
                {
                    g2.setClip(null);
                    g2.setColor(themeColor(Color.BLUE, "Component.focusColor"));
                    g2.draw(new RoundRectangle2D.Double(2.5, 2.5, getWidth() - 5, getHeight() - 5, getHeight() - 5,
                        getHeight() - 5));
                }
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    /** Black or white, whichever reads on this color. */
    private static Color readableOn(Color c)
    {
        double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;

        return luminance > 0.6 ? Color.BLACK : Color.WHITE;
    }
}
