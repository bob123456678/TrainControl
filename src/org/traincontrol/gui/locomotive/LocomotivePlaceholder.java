package org.traincontrol.gui.locomotive;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A stand-in picture for a locomotive that has none (FR-054): "light gray and a simple electric locomotive consisting
 * of: a main rectangle, trapezoid that 1/3 it's height, 2 small closed pantographs, and 4 1/5 height circular wheels
 * in sets of two on each side, spaced evenly apart."
 *
 * With the trapezoid's base flush to the body and its sides at 45 degrees, the top width is the base less twice the
 * height, so there is one dimension to keep in agreement.  Drawn rather than shipped as a file: every place that shows
 * a locomotive asks for a different width, and the shapes are computed from the size asked for, so the outline stays
 * one pixel at every one.
 *
 * @author Adam
 */
public class LocomotivePlaceholder
{
    /**
     * Light gray with a slightly darker outline: a single flat fill loses the wheels into the body and the trapezoid
     * into the roof.  Opaque, not translucent, which shows the wheels through the body ("try the other way" to fading
     * the whole drawing); then a tenth lighter, faded as a whole (FR-104: "increase the opacity of the default
     * locomotive icon by 10%, so it's slightly lighter."), see `FADE`.
     */
    private static final Color BODY = new Color(238, 238, 238);

    private static final Color OUTLINE = new Color(203, 203, 203);

    /**
     * How tall the picture is against its width: long and low like the Central Station's pictures, so lists do not
     * jump.
     */
    private static final float ASPECT = 0.4f;

    /** How much of solid the finished picture is (FR-104): a tenth of what is behind it shows through. */
    private static final float FADE = 0.9f;

    /**
     * A placeholder locomotive of the given width.
     *
     * @param width how wide, in pixels; the height follows from it
     * @return the picture, with a transparent background
     */
    public static BufferedImage image(int width)
    {
        return image(width, Math.max(10, Math.round(width * ASPECT)));
    }

    /**
     * A placeholder locomotive drawn to fill the given box.
     *
     * @param width how wide, in pixels
     * @param height how tall, in pixels
     * @return the picture, with a transparent background
     */
    public static BufferedImage image(int width, int height)
    {
        int w = Math.max(10, width);
        int h = Math.max(10, height);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = out.createGraphics();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // A thirty-second of the height ("make the edges of the placeholder locomotive icon slightly thinner"):
            // quieter than the picture that replaces it.  Scaled by the height, floored at one pixel.
            g.setStroke(new BasicStroke(Math.max(1f, h / 32f)));

            // Padding all round, so nothing is cut off at the edges: the wheels and pantographs lost their outermost
            // pixel ("reduce the overall size slightly to accommodate the pantographs").
            int pad = Math.max(2, Math.round(h / 10f));

            // Bottom up: the wheels are measured against the whole picture and everything else against
            // what is left.
            int wheel = Math.max(3, Math.round(h / 5f));

            int wheelTop = h - pad - wheel;
            int wheelMiddle = wheelTop + wheel / 2;

            // Room for the pantographs to be SEEN, which is more than they had.
            int pantograph = Math.max(3, Math.round(h / 8f));

            int forBoth = wheelMiddle - pad - pantograph;

            // A third of the body, and 15% more, at least 2 so the slope has somewhere to go.
            int roof = Math.max(2, Math.round(forBoth / 4f * 1.15f));

            int bodyHeight = Math.max(3, forBoth - roof);

            int bodyTop = wheelMiddle - bodyHeight;
            int bodyBottom = wheelMiddle;
            int roofTop = bodyTop - roof;

            int margin = Math.max(1, Math.round(w / 20f));

            int bodyLeft = margin;
            int bodyWidth = w - 2 * margin;

            // The wheels first, so the body sits over them and they show below it like running gear
            // rather than beside it like buttons.
            wheels(g, bodyLeft, bodyWidth, wheelTop, wheel);

            // The trapezoid: base flush with the body, sides at 45 degrees, so the top is the base less twice the
            // height; floored at a fifth of the base on very wide, short icons.
            int roofTopWidth = Math.max(Math.round(bodyWidth * 0.2f), bodyWidth - 2 * roof);

            int roofTopLeft = bodyLeft + (bodyWidth - roofTopWidth) / 2;

            // One silhouette, not a roof drawn on top of a body: two antialiased shapes sharing an edge read as a seam
            // ("where the trapezoid meets the square has a rough edge").
            Polygon body = new Polygon();

            body.addPoint(bodyLeft, bodyBottom);
            body.addPoint(bodyLeft, bodyTop);
            body.addPoint(roofTopLeft, roofTop);
            body.addPoint(roofTopLeft + roofTopWidth, roofTop);
            body.addPoint(bodyLeft + bodyWidth, bodyTop);
            body.addPoint(bodyLeft + bodyWidth, bodyBottom);

            g.setColor(BODY);
            g.fillPolygon(body);
            g.setColor(OUTLINE);
            g.drawPolygon(body);

            // The two pantographs, folded down into diamonds - what a closed one looks like.
            pantograph(g, roofTopLeft + roofTopWidth / 4, roofTop, roofTopWidth / 3, pantograph);
            pantograph(g, roofTopLeft + roofTopWidth - roofTopWidth / 4, roofTop, roofTopWidth / 3,
                pantograph);


        }
        finally
        {
            g.dispose();
        }

        return faded(out);
    }

    /**
     * The finished drawing, faded AS A WHOLE to `FADE` (FR-104): drawn once onto a clear picture at that strength, so an
     * overlap is as faint as a single part rather than showing the part behind it.
     */
    private static BufferedImage faded(BufferedImage drawn)
    {
        BufferedImage out = new BufferedImage(drawn.getWidth(), drawn.getHeight(), BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = out.createGraphics();

        try
        {
            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, FADE));
            g.drawImage(drawn, 0, 0, null);
        }
        finally
        {
            g.dispose();
        }

        return out;
    }

    /**
     * Four wheels, two at each end, evenly spaced within their pair.
     */
    private static void wheels(Graphics2D g, int bodyLeft, int bodyWidth, int top, int size)
    {
        float[] at = {0.14f, 0.31f, 0.69f, 0.86f};

        for (float where : at)
        {
            int x = bodyLeft + Math.round(bodyWidth * where) - size / 2;

            g.setColor(BODY);
            g.fillOval(x, top, size, size);
            g.setColor(OUTLINE);
            g.drawOval(x, top, size, size);
        }
    }

    /** One folded pantograph, drawn as a diamond: "the pantographs need to be diamond - right now the top is flat." */
    private static void pantograph(Graphics2D g, int centre, int roofTop, int width, int height)
    {
        int half = Math.max(2, width / 2);

        int top = roofTop - height;

        int middle = roofTop - height / 2;

        Polygon diamond = new Polygon();

        diamond.addPoint(centre, top);
        diamond.addPoint(centre + half, middle);
        diamond.addPoint(centre, roofTop);
        diamond.addPoint(centre - half, middle);

        g.setColor(BODY);
        g.fillPolygon(diamond);
        g.setColor(OUTLINE);
        g.drawPolygon(diamond);
    }
}
