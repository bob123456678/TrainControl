package org.traincontrol.gui.locomotive;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import org.traincontrol.util.ImageUtil;

/**
 * Toggle buttons drawn as one rounded outline with a thin divider between segments, the chosen segment filled with the
 * theme's accent.  FlatLaf 3.7.2 has no segmented buttons, so the outline, dividers and fills are painted here, every
 * color asked of the look and feel at paint time.  Not focusable, as the window's other buttons are not.
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
     * @return the segment, whose action listeners are the caller's
     */
    public JToggleButton addSegment(String text, String name, boolean chosen)
    {
        Segment segment = new Segment(text);

        segment.setName(name);
        segment.setSelected(chosen);
        segment.setFocusable(false);

        group.add(segment);
        add(segment);

        return segment;
    }

    /** The oval all the segments sit in, as wide and tall as the control, with the outline's half pixel left out. */
    private Shape oval(double inset)
    {
        double arc = getHeight() - 2 * inset;

        return new RoundRectangle2D.Double(inset, inset, getWidth() - 2 * inset, getHeight() - 2 * inset, arc, arc);
    }

    /** The first of the look and feel's colors under these keys, else the fallback. */
    public static Color themeColor(Color fallback, String... keys)
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
        return accent();
    }

    /** The theme's accent blue, as the chosen segment and a lit pill wear it. */
    public static Color accent()
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
            ImageUtil.smooth(g2);
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

            setFont(org.traincontrol.gui.widgets.UiStyle.BUTTON_FONT);
            setBorder(null);
            setContentAreaFilled(false);
            setOpaque(false);
            setRolloverEnabled(true);
        }

        @Override
        public Dimension getPreferredSize()
        {
            FontMetrics fm = getFontMetrics(getFont());

            return new Dimension(fm.stringWidth(getText()) + 32, org.traincontrol.gui.widgets.UiStyle.button(new JButton(getText())).getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();

            try
            {
                ImageUtil.antialias(g2);

                Color fill = themeColor(Color.WHITE, "Button.background");

                if (isSelected()) fill = chosenColor();
                else if (getModel().isPressed()) fill = themeColor(fill, "Button.pressedBackground");
                else if (getModel().isRollover()) fill = themeColor(fill, "Button.hoverBackground");

                // The control's oval, in this segment's own coordinates, keeps the end segments round
                g2.clip(AffineTransform.getTranslateInstance(-getX(), -getY()).createTransformedShape(oval(0)));
                g2.setColor(fill);
                g2.fillRect(0, 0, getWidth(), getHeight());

                drawCentered(g2, this, isSelected() ? inkOn(fill) : themeColor(getForeground(), "Button.foreground"));
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    /** Draws a button's text in this color, centered in the button, in the button's font. */
    public static void drawCentered(Graphics2D g2, javax.swing.AbstractButton button, Color ink)
    {
        g2.setColor(ink);
        g2.setFont(button.getFont());

        FontMetrics fm = g2.getFontMetrics();

        BasicGraphicsUtils.drawStringUnderlineCharAt(g2, button.getText(), -1,
            (button.getWidth() - fm.stringWidth(button.getText())) / 2,
            (button.getHeight() - fm.getHeight()) / 2 + fm.getAscent());
    }

    /** Black or white, whichever reads on this color. */
    private static Color inkOn(Color c)
    {
        double luminance = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255;

        return luminance > 0.6 ? Color.BLACK : Color.WHITE;
    }
}
