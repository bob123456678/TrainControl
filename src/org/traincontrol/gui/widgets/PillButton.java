package org.traincontrol.gui.widgets;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JToggleButton;
import org.traincontrol.gui.locomotive.SegmentedControl;
import org.traincontrol.util.ImageUtil;

/** A toggle drawn as a pill: quiet with a light outline, filled with the theme's accent blue while selected. */
public class PillButton extends JToggleButton
{
    public PillButton(String text)
    {
        super(text);

        setFocusable(false);
        updateLook();
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public void updateUI()
    {
        super.updateUI();
        updateLook();
    }

    private void updateLook()
    {
        setFont(UiStyle.BUTTON_FONT);
        setBorder(null);
        setContentAreaFilled(false);
        setOpaque(false);
        setRolloverEnabled(true);
    }

    /** The toolkit reads the cursor field itself, so the hand is set, not computed. */
    @Override
    public void setEnabled(boolean enabled)
    {
        super.setEnabled(enabled);
        setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null);
    }

    @Override
    public Dimension getPreferredSize()
    {
        FontMetrics fm = getFontMetrics(getFont());

        return new Dimension(fm.stringWidth(getText()) + 24, fm.getHeight() + 8);
    }

    /** The fill: the accent while selected, else white, a touch darker under the pointer. */
    public Color fillColor()
    {
        if (isSelected()) return SegmentedControl.accent();
        if (isEnabled() && getModel().isPressed()) return new Color(0xDD, 0xE6, 0xF0);
        if (isEnabled() && getModel().isRollover()) return new Color(0xEE, 0xF3, 0xF8);

        return Color.WHITE;
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();

        try
        {
            ImageUtil.smooth(g2);

            Color fill = fillColor();
            Color line = isSelected() ? fill : SegmentedControl.themeColor(new Color(0xB8, 0xBE, 0xC6), "Component.borderColor", "Button.borderColor");
            double arc = getHeight() - 1;

            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, arc, arc));
            g2.setColor(line);
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, arc, arc));

            Color ink = isSelected() ? Color.WHITE : Color.BLACK;

            // A disabled pill stays legible: a little softer, never the theme's washed-out gray
            if (!isEnabled()) ink = isSelected() ? new Color(0xF2, 0xF2, 0xF2) : new Color(0x66, 0x66, 0x66);

            SegmentedControl.drawCentered(g2, this, ink);
        }
        finally
        {
            g2.dispose();
        }
    }
}
