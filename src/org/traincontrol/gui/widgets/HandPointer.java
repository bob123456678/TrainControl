package org.traincontrol.gui.widgets;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The hand pointer, drawn smooth by TrainControl instead of Java 8's jagged built-in one (FR-224).  Windows keeps only
 * a 1-bit mask of a custom pointer, so the outer edge is cut at half coverage and only the inside is antialiased:
 * the picture returned is exactly what Windows shows.
 */
public final class HandPointer
{
    /** The candidate looks, until Adam picks one. */
    public enum Look
    {
        /** White with a black outline, the proportions of the Windows hand. */
        CLASSIC(1.0, 1.15, Color.WHITE, Color.BLACK),
        /** As CLASSIC with a thinner outline. */
        THIN(1.0, 0.8, Color.WHITE, Color.BLACK),
        /** As CLASSIC, larger and with a heavier outline, filling the pointer box. */
        BOLD(1.1, 1.4, Color.WHITE, Color.BLACK),
        /** Black with a white outline, as the Windows black pointers, at BOLD's size. */
        DARK(1.1, 1.4, new Color(20, 20, 20), Color.WHITE);

        final double scale;
        final double outline;
        final Color fill;
        final Color ink;

        Look(double scale, double outline, Color fill, Color ink)
        {
            this.scale = scale;
            this.outline = outline;
            this.fill = fill;
            this.ink = ink;
        }
    }

    /** The design box the hand is drawn in: a 32-pixel pointer at 100%. */
    private static final double BOX = 32;

    /** Where the fingertip is across the hand, in design units. */
    private static final double TIP_X = 6;

    private HandPointer()
    {
    }

    /** The hand in a square of {@code size} pixels, the pointer box Windows asks for. */
    public static BufferedImage image(Look look, int size)
    {
        double s = size / BOX * look.scale;
        double w = size / BOX * look.outline;
        AffineTransform at = new AffineTransform(s, 0, 0, s, left(look, size), w);
        Area hand = new Area(at.createTransformedShape(outline()));
        Area silhouette = new Area(new BasicStroke((float) (2 * w), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            .createStrokedShape(hand));
        silhouette.add(hand);

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(look.ink);
        g.fill(silhouette);
        cutEdge(out);

        g.setColor(look.fill);
        g.fill(hand);
        g.setColor(look.ink);
        g.setStroke(new BasicStroke((float) (0.8 * w), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] l : new double[][] {{8, 6.6, 12.6}, {11.6, 7.6, 12.6}, {15.2, 9.2, 12.6}})
        {
            g.draw(at.createTransformedShape(new Line2D.Double(l[0], l[1], l[0], l[2])));
        }
        g.dispose();
        return out;
    }

    /** The fingertip: where the hand points, on the top row. */
    public static Point hotspot(Look look, int size)
    {
        return new Point((int) Math.round(left(look, size) + TIP_X * size / BOX * look.scale), 0);
    }

    private static double left(Look look, int size)
    {
        return size / BOX * (look.outline + 0.5);
    }

    /** Opaque where at least half covered, clear elsewhere, as Windows will mask it. */
    private static void cutEdge(BufferedImage img)
    {
        for (int y = 0; y < img.getHeight(); y++)
        {
            for (int x = 0; x < img.getWidth(); x++)
            {
                int argb = img.getRGB(x, y);
                img.setRGB(x, y, (argb >>> 24) >= 128 ? argb | 0xFF000000 : 0);
            }
        }
    }

    /** A right hand from the back, index finger up, in design units (about 19 wide and 26 tall). */
    private static Shape outline()
    {
        Area a = new Area(new RoundRectangle2D.Double(4, 0, 4, 17, 4, 4));
        a.add(new Area(new RoundRectangle2D.Double(8, 6, 3.6, 10, 3.6, 3.6)));
        a.add(new Area(new RoundRectangle2D.Double(11.6, 7, 3.6, 9, 3.6, 3.6)));
        a.add(new Area(new RoundRectangle2D.Double(15.2, 8.6, 3.4, 8, 3.4, 3.4)));

        Path2D palm = new Path2D.Double();
        palm.moveTo(4, 12);
        palm.lineTo(18.6, 12);
        palm.lineTo(18.6, 19.5);
        palm.curveTo(18.6, 23.5, 16.4, 26, 13, 26);
        palm.lineTo(9.6, 26);
        palm.curveTo(7.6, 26, 6.6, 25, 5.4, 23.4);
        palm.lineTo(4, 21);
        palm.closePath();
        a.add(new Area(palm));

        Shape thumb = new RoundRectangle2D.Double(-1.8, -1.8, 10.9, 3.6, 3.6, 3.6);
        AffineTransform t = AffineTransform.getTranslateInstance(7.4, 21.5);
        t.rotate(Math.toRadians(-130.5));
        a.add(new Area(t.createTransformedShape(thumb)));
        return a;
    }
}
