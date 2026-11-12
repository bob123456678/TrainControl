package org.traincontrol.gui.widgets;

import com.formdev.flatlaf.util.UIScale;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
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

    private static final String LOCOMOTIVE_BOXES =
        "0.06 0.30 0.30 0.44, 0.30 0.42 0.64 0.32, 0.02 0.22 0.38 0.10, 0.64 0.22 0.16 0.22";

    private static final String LOCOMOTIVE_WHEELS = "0.12 0.60 0.26, 0.46 0.64 0.20, 0.70 0.64 0.20";

    private static final String RAIL = "0 0.90 1 0.08";

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
            shapes(g, w, h, LOCOMOTIVE_BOXES, LOCOMOTIVE_WHEELS);
            shapes(g, w, h, RAIL, "");
        });
    }

    /** A station: a building under a pitched roof, its doorway open, on the locomotive's rail. */
    public static Icon station()
    {
        return icon((g, w, h) ->
        {
            g.setColor(INK);
            g.fillPolygon(new int[] {Math.round(w * 0.04f), Math.round(w * 0.50f), Math.round(w * 0.96f)},
                new int[] {Math.round(h * 0.40f), Math.round(h * 0.08f), Math.round(h * 0.40f)}, 3);
            shapes(g, w, h, "0.14 0.38 0.72 0.46, " + RAIL, "");
            g.setColor(Color.WHITE);
            shapes(g, w, h, "0.42 0.56 0.16 0.28", "");
        });
    }

    /** Two switches, one on and one off: the locomotive's functions.  Same size, the knobs the same distance from their ends. */
    public static Icon functions()
    {
        return icon((g, w, h) ->
        {
            g.setColor(INK);
            g.fill(new RoundRectangle2D.Float(w * 0.04f, h * 0.10f, w * 0.92f, h * 0.36f, h * 0.36f, h * 0.36f));
            g.setStroke(new BasicStroke(w * 0.08f));
            g.draw(new RoundRectangle2D.Float(w * 0.08f, h * 0.58f, w * 0.84f, h * 0.28f, h * 0.28f, h * 0.28f));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(w * 0.64f, h * 0.14f, h * 0.28f, h * 0.28f));
            g.setColor(INK);
            g.fill(new Ellipse2D.Float(w * 0.08f, h * 0.58f, h * 0.28f, h * 0.28f));
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

    /** One locomotive behind another, the front one edged in white, and a plus: several locomotives as one. */
    public static Icon multiUnit()
    {
        return icon((g, w, h) ->
        {
            g.setColor(new Color(150, 150, 150));
            placed(g, w, h, 0.20f, 0.12f, 0.62f, () -> shapes(g, w, h, LOCOMOTIVE_BOXES, LOCOMOTIVE_WHEELS));

            placed(g, w, h, 0f, 0.26f, 0.74f, () ->
            {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(w * 0.06f / 0.74f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                for (Shape part : parts(w, h, LOCOMOTIVE_BOXES, LOCOMOTIVE_WHEELS)) g.draw(part);

                g.setColor(INK);
                shapes(g, w, h, LOCOMOTIVE_BOXES, LOCOMOTIVE_WHEELS);
            });

            shapes(g, w, h, RAIL + ", 0.74 0.095 0.24 0.07, 0.825 0.01 0.07 0.24", "");
        });
    }

    /**
     * The main window's Autonomy tab picture, a triangle ring round a filled one, drawn for the tab's size: the
     * picture's own gap is under a pixel at 20, so a shrunk copy smeared the inside.
     */
    public static Icon autonomy()
    {
        return icon((g, w, h) ->
        {
            g.setColor(INK);

            java.awt.geom.Area ring = new java.awt.geom.Area(play(w, h, 0));

            ring.subtract(new java.awt.geom.Area(play(w, h, 0.07)));
            g.fill(ring);
            g.fill(play(w, h, 0.12));
        });
    }

    /** The picture's triangle, moved in by {@code inset} of the icon's height on every side (about its in-centre). */
    private static Shape play(int w, int h, double inset)
    {
        double centre = 0.4317, radius = 0.1897, k = (radius - inset) / radius;

        Path2D p = new Path2D.Double();

        p.moveTo(w * (centre + (0.242 - centre) * k), h * (0.5 - 0.34 * k));
        p.lineTo(w * (centre + (0.242 - centre) * k), h * (0.5 + 0.34 * k));
        p.lineTo(w * (centre + (0.793 - centre) * k), h * 0.5);
        p.closePath();

        return p;
    }

    /** Draws in a smaller copy of the icon's box, moved to (left, top) and scaled by {@code scale}. */
    private static void placed(Graphics2D g, int w, int h, float left, float top, float scale, Runnable draw)
    {
        java.awt.geom.AffineTransform was = g.getTransform();

        g.translate(w * left, h * top);
        g.scale(scale, scale);
        draw.run();
        g.setTransform(was);
    }

    /** Fills boxes ("x y width height") and circles ("x y diameter"), as fractions of the icon, in the current color. */
    private static void shapes(Graphics2D g, int w, int h, String boxes, String circles)
    {
        for (Shape part : parts(w, h, boxes, circles)) g.fill(part);
    }

    private static java.util.List<Shape> parts(int w, int h, String boxes, String circles)
    {
        java.util.List<Shape> out = new java.util.ArrayList<>();

        for (String part : (boxes + "," + circles).split(","))
        {
            String[] v = part.trim().split(" ");

            if (v.length == 4) out.add(new RoundRectangle2D.Float(w * f(v[0]), h * f(v[1]), w * f(v[2]), h * f(v[3]), 3f, 3f));
            else if (v.length == 3) out.add(new Ellipse2D.Float(w * f(v[0]), h * f(v[1]), w * f(v[2]), w * f(v[2])));
        }

        return out;
    }

    private static float f(String number)
    {
        return Float.parseFloat(number);
    }
}
