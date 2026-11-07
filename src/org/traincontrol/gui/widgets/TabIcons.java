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

    /** What a tab icon draws, in a box of {@code w} by {@code h} pixels. */
    private interface Art
    {
        void draw(Graphics2D g, int w, int h);
    }

    private static Icon icon(Art art)
    {
        return new RowIcons.Painted(UIScale.scale(SIZE))
        {
            @Override
            void draw(Graphics2D g, int w, int h)
            {
                art.draw(g, w, h);
            }
        };
    }

    /** A locomotive: cab, boiler, chimney and wheels on a rail, as the main window's Locomotive tab. */
    public static Icon locomotive()
    {
        return icon((g, w, h) ->
        {
            g.setColor(INK);
            shapes(g, w, h,
                "0.06 0.30 0.30 0.44, 0.30 0.42 0.64 0.32, 0.02 0.22 0.38 0.10, 0.64 0.22 0.16 0.22, 0 0.90 1 0.08",
                "0.12 0.60 0.26, 0.46 0.64 0.20, 0.70 0.64 0.20");
        });
    }

    /** Two switches, one on and one off: the locomotive's functions. */
    public static Icon functions()
    {
        return icon((g, w, h) ->
        {
            g.setColor(INK);
            g.fill(new RoundRectangle2D.Float(w * 0.04f, h * 0.10f, w * 0.92f, h * 0.38f, h * 0.38f, h * 0.38f));
            g.setStroke(new BasicStroke(Math.max(1.2f, w * 0.08f)));
            g.draw(new RoundRectangle2D.Float(w * 0.08f, h * 0.58f, w * 0.84f, h * 0.28f, h * 0.28f, h * 0.28f));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(w * 0.54f, h * 0.15f, h * 0.28f, h * 0.28f));
            g.setColor(INK);
            g.fill(new Ellipse2D.Float(w * 0.12f, h * 0.60f, h * 0.24f, h * 0.24f));
        });
    }

    /** A page with a folded corner and lines of writing. */
    public static Icon notes()
    {
        return icon((g, w, h) ->
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
        });
    }

    /** Two cars coupled together: a train made of several locomotives. */
    public static Icon multiUnit()
    {
        return icon((g, w, h) ->
        {
            g.setColor(INK);
            shapes(g, w, h, "0.02 0.28 0.40 0.44, 0.58 0.28 0.40 0.44, 0.40 0.46 0.20 0.08",
                "0.08 0.70 0.18, 0.20 0.70 0.18, 0.64 0.70 0.18, 0.76 0.70 0.18");
            g.setColor(Color.WHITE);
            shapes(g, w, h, "0.08 0.36 0.12 0.14, 0.64 0.36 0.12 0.14", "");
        });
    }

    /** Fills boxes ("x y width height") and circles ("x y diameter"), as fractions of the icon, in the current color. */
    private static void shapes(Graphics2D g, int w, int h, String boxes, String circles)
    {
        for (String part : (boxes + "," + circles).split(","))
        {
            String[] v = part.trim().split(" ");

            if (v.length == 4) g.fill(new RoundRectangle2D.Float(w * f(v[0]), h * f(v[1]), w * f(v[2]), h * f(v[3]), 3f, 3f));
            else if (v.length == 3) g.fill(new Ellipse2D.Float(w * f(v[0]), h * f(v[1]), w * f(v[2]), w * f(v[2])));
        }
    }

    private static float f(String number)
    {
        return Float.parseFloat(number);
    }
}
