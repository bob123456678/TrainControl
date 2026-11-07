package org.traincontrol.gui.widgets;

import com.formdev.flatlaf.util.UIScale;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * Icons for tabs that carry text, in the dark gray of the main window's tab icons.  Drawn, and sized in screen pixels
 * from {@code size} at 100%, so they keep their proportion at any interface scale.
 */
public final class TabIcons
{
    /** The edge of a tab icon at 100%: smaller than the main window's 30, which has no text beside it. */
    public static final int SIZE = 20;

    private static final Color INK = new Color(51, 51, 51);

    private TabIcons()
    {
    }

    /** A locomotive: cab, boiler, chimney and wheels on a rail, as the main window's Locomotive tab. */
    public static Icon locomotive()
    {
        return new RowIcons.Painted(UIScale.scale(SIZE))
        {
            @Override
            void draw(Graphics2D g, int w, int h)
            {
                g.setColor(INK);
                g.fill(new RoundRectangle2D.Float(w * 0.06f, h * 0.30f, w * 0.30f, h * 0.44f, 2f, 2f));
                g.fill(new RoundRectangle2D.Float(w * 0.30f, h * 0.42f, w * 0.64f, h * 0.32f, 3f, 3f));
                g.fill(new java.awt.geom.Rectangle2D.Float(w * 0.02f, h * 0.22f, w * 0.38f, h * 0.10f));
                g.fill(new java.awt.geom.Rectangle2D.Float(w * 0.64f, h * 0.22f, w * 0.16f, h * 0.22f));
                g.fill(new Ellipse2D.Float(w * 0.12f, h * 0.60f, w * 0.26f, h * 0.26f));
                g.fill(new Ellipse2D.Float(w * 0.46f, h * 0.64f, w * 0.20f, h * 0.20f));
                g.fill(new Ellipse2D.Float(w * 0.70f, h * 0.64f, w * 0.20f, h * 0.20f));
                g.fill(new RoundRectangle2D.Float(0, h * 0.90f, w, h * 0.08f, 2f, 2f));
            }
        };
    }

    /** Two switches, one on and one off: the locomotive's functions. */
    public static Icon functions()
    {
        return new RowIcons.Painted(UIScale.scale(SIZE))
        {
            @Override
            void draw(Graphics2D g, int w, int h)
            {
                g.setColor(INK);
                g.fill(new RoundRectangle2D.Float(w * 0.04f, h * 0.10f, w * 0.92f, h * 0.38f, h * 0.38f, h * 0.38f));
                g.setStroke(new BasicStroke(Math.max(1.2f, w * 0.08f)));
                g.draw(new RoundRectangle2D.Float(w * 0.08f, h * 0.58f, w * 0.84f, h * 0.28f, h * 0.28f, h * 0.28f));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Float(w * 0.54f, h * 0.15f, h * 0.28f, h * 0.28f));
                g.setColor(INK);
                g.fill(new Ellipse2D.Float(w * 0.12f, h * 0.60f, h * 0.24f, h * 0.24f));
            }
        };
    }

    /** A page with a folded corner and lines of writing. */
    public static Icon notes()
    {
        return new RowIcons.Painted(UIScale.scale(SIZE))
        {
            @Override
            void draw(Graphics2D g, int w, int h)
            {
                Path2D.Float page = new Path2D.Float();

                page.moveTo(w * 0.16f, h * 0.04f);
                page.lineTo(w * 0.64f, h * 0.04f);
                page.lineTo(w * 0.86f, h * 0.26f);
                page.lineTo(w * 0.86f, h * 0.96f);
                page.lineTo(w * 0.16f, h * 0.96f);
                page.closePath();

                g.setColor(INK);
                g.fill(page);
                g.setColor(Color.WHITE);

                for (int line = 0; line < 3; line++)
                {
                    g.fill(new java.awt.geom.Rectangle2D.Float(w * 0.28f, h * (0.42f + line * 0.18f), w * 0.46f, h * 0.07f));
                }

                Path2D.Float fold = new Path2D.Float();

                fold.moveTo(w * 0.64f, h * 0.04f);
                fold.lineTo(w * 0.64f, h * 0.26f);
                fold.lineTo(w * 0.86f, h * 0.26f);
                fold.closePath();

                g.setColor(new Color(150, 150, 150));
                g.fill(fold);
            }
        };
    }

    /** Two cars coupled together: a train made of several locomotives. */
    public static Icon multiUnit()
    {
        return new RowIcons.Painted(UIScale.scale(SIZE))
        {
            @Override
            void draw(Graphics2D g, int w, int h)
            {
                g.setColor(INK);
                g.fill(new RoundRectangle2D.Float(w * 0.02f, h * 0.28f, w * 0.40f, h * 0.44f, 3f, 3f));
                g.fill(new RoundRectangle2D.Float(w * 0.58f, h * 0.28f, w * 0.40f, h * 0.44f, 3f, 3f));
                g.fill(new java.awt.geom.Rectangle2D.Float(w * 0.40f, h * 0.46f, w * 0.20f, h * 0.08f));
                g.fill(new Ellipse2D.Float(w * 0.08f, h * 0.70f, w * 0.18f, h * 0.18f));
                g.fill(new Ellipse2D.Float(w * 0.20f, h * 0.70f, w * 0.18f, h * 0.18f));
                g.fill(new Ellipse2D.Float(w * 0.64f, h * 0.70f, w * 0.18f, h * 0.18f));
                g.fill(new Ellipse2D.Float(w * 0.76f, h * 0.70f, w * 0.18f, h * 0.18f));
                g.setColor(Color.WHITE);
                g.fill(new java.awt.geom.Rectangle2D.Float(w * 0.08f, h * 0.36f, w * 0.12f, h * 0.14f));
                g.fill(new java.awt.geom.Rectangle2D.Float(w * 0.64f, h * 0.36f, w * 0.12f, h * 0.14f));
            }
        };
    }
}
