package org.traincontrol.gui.widgets;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The hand pointer for everything a click acts on (FR-224).  Java 8's own is a jagged 1-bit picture, so there it is
 * TrainControl's smooth bold hand; later Java already shows Windows' own hand at the user's size and color.  Windows
 * keeps only a 1-bit mask of a custom pointer, so the outer edge is cut at half coverage and only the inside is
 * antialiased: the picture returned is exactly what Windows shows.
 */
public final class HandPointer
{
    /** Size of the hand in the box, and its outline width, in design units. */
    private static final double SCALE = 1.1, OUTLINE = 1.4;

    /** The design box the hand is drawn in: a 32-pixel pointer at 100%. */
    private static final double BOX = 32;

    /** Where the fingertip is across the hand, in design units. */
    private static final double TIP_X = 6;

    private static final Cursor HAND = make();

    private HandPointer()
    {
    }

    /** The hand pointer, one shared instance; set it, never compare it by type (see {@link #isHand}). */
    public static Cursor cursor()
    {
        return HAND;
    }

    /** True for {@link #cursor()} and for Java's own hand, whose type a custom pointer does not have. */
    public static boolean isHand(Cursor c)
    {
        return c == HAND || c != null && c.getType() == Cursor.HAND_CURSOR;
    }

    private static Cursor make()
    {
        if ("1.8".equals(System.getProperty("java.specification.version")))
        {
            try
            {
                Toolkit kit = Toolkit.getDefaultToolkit();
                int size = kit.getBestCursorSize(32, 32).width;

                if (size > 0) return kit.createCustomCursor(image(size), hotspot(size), "TrainControl hand");
            }
            catch (RuntimeException | Error e)
            {
                // Headless or no custom pointers: Java's own hand below.
            }
        }
        return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    /** The hand in a square of {@code size} pixels, the pointer box Windows asks for. */
    public static BufferedImage image(int size)
    {
        double s = size / BOX * SCALE;
        double w = size / BOX * OUTLINE;
        AffineTransform at = new AffineTransform(s, 0, 0, s, left(size), w);
        Area hand = new Area(at.createTransformedShape(outline()));
        Area silhouette = new Area(new BasicStroke((float) (2 * w), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            .createStrokedShape(hand));
        silhouette.add(hand);

        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.BLACK);
        g.fill(silhouette);
        cutEdge(out);

        g.setColor(Color.WHITE);
        g.fill(hand);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke((float) (0.8 * w), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[] l : new double[][] {{8, 6.6, 12.6}, {11.6, 7.6, 12.6}, {15.2, 9.2, 12.6}})
        {
            g.draw(at.createTransformedShape(new Line2D.Double(l[0], l[1], l[0], l[2])));
        }
        g.dispose();
        return out;
    }

    /** The fingertip: where the hand points, on the top row. */
    public static Point hotspot(int size)
    {
        return new Point((int) Math.round(left(size) + TIP_X * size / BOX * SCALE), 0);
    }

    private static double left(int size)
    {
        return size / BOX * (OUTLINE + 0.5);
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
