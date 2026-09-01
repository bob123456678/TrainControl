package org.traincontrol.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A stand-in picture for a locomotive that has none (FR-054).
 *
 * Adam: "for locomotives without an icon, add a placeholder rather than nothing.  the placeholder
 * should be light gray and a simple electric locomotive consisting of: a main rectangle, trapezoid
 * that 1/3 it's height, 2 small closed pantographs, and 4 1/5 height circular wheels in sets of two on
 * each side, spaced evenly apart."
 *
 * And, having looked at the first one: "add some padding so the wheels don't get clipped.  make the
 * end of the trapezoid match the end of the rectangles on the x axis, and increase the angle of the
 * slope to be 45 degrees.  make the pantograph visible (zoom out) and remove the visible top edge of
 * the rectangle.  increase the height of the trapezoid by 15%."
 *
 * **Two of those settle each other.** With the trapezoid's base flush to the body and its sides at 45
 * degrees, the top width is no longer a number to choose: it is the base less twice the height, because
 * that is what a 45-degree slope means. So raising the roof by 15% also narrows its top, and there is
 * one dimension here rather than two that have to be kept in agreement.
 *
 * **Drawn rather than shipped as a file.** Every place that shows a locomotive asks for a different
 * width - 142 in the selector, and whatever the icon panel is sized to - and a bitmap scaled to all of
 * them looks worse at each. The shapes are computed from the size asked for, so the outline stays one
 * pixel at every one.
 *
 * @author Adam
 */
public class LocomotivePlaceholder
{
    /**
     * The light grey Adam asked for, and a slightly darker one for the outlines.
     *
     * Two greys rather than one: a single flat fill loses the wheels into the body and the trapezoid
     * into the roof, which is a silhouette rather than the locomotive he described. The outline is
     * close enough to stay "light gray" as a whole - it is the difference between a shape and a
     * smudge, not a second colour.
     *
     * **Opaque, and lighter instead.** They were 40% transparent for a few hours, and a translucent
     * locomotive is see-through wherever its own parts overlap: the body let the tops of the wheels
     * behind it show, which reads as a fault rather than as a faint picture. Fading the finished
     * drawing as a whole would have fixed that and kept the alpha, and Adam declined it - "try the
     * other way" - so this is lighter paint and no transparency at all.
     *
     * As light as it can be and still be a locomotive: the outline has to separate the wheels from the
     * body, or the shape becomes a smudge.
     */
    private static final Color BODY = new Color(238, 238, 238);

    private static final Color OUTLINE = new Color(203, 203, 203);

    /**
     * How tall the picture is against its width.
     *
     * The Central Station's own locomotive pictures are long and low, and this sits beside them in the
     * same lists - a placeholder in a different shape would make the list jump wherever one appears.
     */
    private static final float ASPECT = 0.4f;

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

            // A THIRTY-SECOND OF THE HEIGHT, not a twenty-fourth (Adam, 2026-09-01: "make the edges of
            // the placeholder locomotive icon slightly thinner").  This is a placeholder standing where
            // a photograph goes, so its lines should be quieter than the picture that replaces it - a
            // heavy outline reads as a drawing somebody meant, rather than as an empty frame.
            //
            // Still scaled by the height rather than fixed, so the shape holds together at the small
            // sizes the locomotive panel draws it at, and still floored at one pixel: below that the
            // line disappears in places and the body looks torn.
            g.setStroke(new BasicStroke(Math.max(1f, h / 32f)));

            // PADDING ALL ROUND, so nothing is cut off at the edges.
            //
            // The wheels sat on the bottom row and the pantographs on the top one, and both lost their
            // outermost pixel to the edge of the picture - which is what "the wheels get clipped" and
            // "make the pantograph visible" are between them. Everything below is drawn inside this.
            // A little more than the first draft, on Adam seeing it: "reduce the overall size slightly
            // to accommodate the pantographs".  The locomotive gives up a row at each end and the
            // pantographs get somewhere to stand.
            int pad = Math.max(2, Math.round(h / 10f));

            // Bottom up: the wheels are measured against the whole picture and everything else against
            // what is left.
            int wheel = Math.max(3, Math.round(h / 5f));

            int wheelTop = h - pad - wheel;
            int wheelMiddle = wheelTop + wheel / 2;

            // Room for the pantographs to be SEEN, which is more than they had.
            int pantograph = Math.max(3, Math.round(h / 8f));

            int forBoth = wheelMiddle - pad - pantograph;

            // A third of the body, and then 15% more of it, which is what he asked for. Rounded up to
            // at least 2 so the slope has somewhere to go on a small icon.
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

            // THE TRAPEZOID: base flush with the body, sides at 45 degrees.
            //
            // At 45 degrees the horizontal step in equals the vertical rise, so the top is the base
            // less twice the height and there is nothing else to choose. On a very wide, very short
            // icon that would leave nothing at the top, so it is floored at a fifth of the base - the
            // shape stops being 45 degrees before it stops being a trapezoid.
            int roofTopWidth = Math.max(Math.round(bodyWidth * 0.2f), bodyWidth - 2 * roof);

            int roofTopLeft = bodyLeft + (bodyWidth - roofTopWidth) / 2;

            // ONE SILHOUETTE, not a roof drawn on top of a body.
            //
            // Adam: "where the trapezoid meets the square has a rough edge."  It did, and filling two
            // antialiased shapes that share an edge is why: each lays down its own soft border along
            // the join, and the two together read as a seam through the middle of one object.  Drawn
            // as a single closed outline there is no join to soften, and the body's top edge is gone
            // for the same reason rather than by leaving a line out.
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

    /**
     * One folded pantograph, drawn as a diamond.
     *
     * Adam: "the pantographs need to be diamond - right now the top is flat."  They were a collector
     * bar with two arms under it, which is a pantograph seen from the side but reads as a flat-topped
     * hat at this size. A closed pantograph folds into a lozenge - the two arms meeting above and the
     * two below - and that is a shape somebody recognises in twelve pixels.
     */
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
