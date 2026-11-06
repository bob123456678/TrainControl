package org.traincontrol.gui.widgets;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * The small marks that live inside a table row: delete, add, and the two move arrows.  Drawn rather than loaded: the
 * application ships no icon art to match, and a glyph font renders the trash can and arrows unevenly on Segoe UI on
 * Java 8.  Sized from the row height by the caller, so they stay in proportion if the table's font changes.
 */
public final class RowIcons
{
    /** Gray enough to read as a control rather than as content. */
    private static final Color QUIET = new Color(110, 110, 110);

    /** For delete, which is the one action here somebody might regret. */
    private static final Color WARN = new Color(170, 60, 60);

    private RowIcons()
    {
    }

    /**
     * A waste basket: lid, body, and two lines down it.
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon trash(final int size)
    {
        return trash(size, WARN);
    }

    /** The same, in a color of the caller's choosing - see plus(int, Color). */
    public static Icon trash(final int size, final Color colour)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setColor(colour);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int left = width / 5;
                int right = width - left;
                int lid = height / 4;

                // The lid, and the handle above it
                g.drawLine(left - 1, lid, right + 1, lid);
                g.drawLine(width / 2 - width / 8, lid - height / 8,
                    width / 2 + width / 8, lid - height / 8);
                g.drawLine(width / 2 - width / 8, lid - height / 8, width / 2 - width / 8, lid);
                g.drawLine(width / 2 + width / 8, lid - height / 8, width / 2 + width / 8, lid);

                // The body, narrowing slightly towards the bottom as a real one does
                g.drawLine(left, lid, left + 1, height - lid / 2);
                g.drawLine(right, lid, right - 1, height - lid / 2);
                g.drawLine(left + 1, height - lid / 2, right - 1, height - lid / 2);

                // And the two lines down it, which are what make it read as a basket rather than a cup
                g.drawLine(width / 2 - width / 10, lid + height / 6,
                    width / 2 - width / 10, height - lid);
                g.drawLine(width / 2 + width / 10, lid + height / 6,
                    width / 2 + width / 10, height - lid);
            }
        };
    }

    /** Two overlapping sheets, for the mark that copies a row. */
    public static Icon copy(final int size)
    {
        return copy(size, QUIET);
    }

    /** The same, in a color of the caller's choosing - see plus(int, Color). */
    public static Icon copy(final int size, final Color colour)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setStroke(new BasicStroke(1.3f));

                Rectangle front = frontSheet(width, height);

                int w = front.width;
                int h = front.height;
                int gap = front.x - 1;

                // The sheet behind, only where it shows (the front one hides its lower right corner).  Not by filling
                // the front one white: on a selected row that was a solid white sheet (OB-258).
                g.setColor(colour);
                g.drawLine(1, 1, 1 + w, 1);
                g.drawLine(1, 1, 1, 1 + h);
                g.drawLine(1 + w, 1, 1 + w, 1 + gap);
                g.drawLine(1, 1 + h, 1 + gap, 1 + h);

                g.drawRect(1 + gap, 1 + gap, w, h);
            }
        };
    }

    /**
     * Where the copy mark's front sheet is drawn in an icon of this size; the sheet behind is drawn from the same
     * figures, a gap up and to the left.
     *
     * @param width the icon's width
     * @param height the icon's height
     * @return the front sheet's outline
     */
    static Rectangle frontSheet(int width, int height)
    {
        int gap = Math.max(2, Math.round(width * 0.20f));

        return new Rectangle(1 + gap, 1 + gap, Math.round(width * 0.52f), Math.round(height * 0.62f));
    }

    /**
     * A plus.
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon plus(final int size)
    {
        return plus(size, new Color(0, 120, 40));
    }

    /**
     * The same, in a color of the caller's choosing, for a SELECTED row: a mid-green plus is nearly invisible against
     * the look-and-feel's selection blue.
     *
     * @param colour what to draw it in
     * @return the icon
     */
    public static Icon plus(final int size, final Color colour)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setColor(colour);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int pad = width / 5;

                g.drawLine(width / 2, pad, width / 2, height - pad);
                g.drawLine(pad, height / 2, width - pad, height / 2);
            }
        };
    }

    /**
     * A solid triangle pointing up or down.
     *
     * @param size how many pixels square
     * @param up true for up
     * @return the icon
     */
    public static Icon arrow(final int size, final boolean up)
    {
        return arrow(size, up, QUIET);
    }

    /**
     * The same, in a given color: a mark in a fixed gray disappears into a selected row's selection colors.
     *
     * @param colour what to draw it in
     * @return the icon
     */
    public static Icon arrow(final int size, final boolean up, final Color colour)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setColor(colour);

                int pad = width / 4;

                int[] x = new int[]{pad, width - pad, width / 2};
                int[] y = up
                    ? new int[]{height - pad, height - pad, pad}
                    : new int[]{pad, pad, height - pad};

                g.fillPolygon(x, y, 3);
            }
        };
    }

    /**
     * Six dots in two columns, the grip a row is dragged by.
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon grip(final int size)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setColor(new Color(150, 150, 150));

                for (int column = -1; column <= 1; column += 2)
                {
                    for (int row = -1; row <= 1; row++)
                    {
                        g.fill(new java.awt.geom.Ellipse2D.Float(width / 2f + column * 2.5f - 1.1f,
                            height / 2f + row * 4f - 1.1f, 2.2f, 2.2f));
                    }
                }
            }
        };
    }

    /**
     * Two lines inside a rounded outline: put rows in a group of their own.
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon group(final int size)
    {
        return group(size, QUIET);
    }

    /**
     * The same, in a given color.
     *
     * @param size how many pixels square
     * @param colour what to draw it in
     * @return the icon
     */
    public static Icon group(final int size, final Color colour)
    {
        return outlined(size, colour, false);
    }

    /**
     * The same two lines with the outline broken: take a group apart.
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon ungroup(final int size)
    {
        return ungroup(size, QUIET);
    }

    /**
     * The same, in a given color.
     *
     * @param size how many pixels square
     * @param colour what to draw it in
     * @return the icon
     */
    public static Icon ungroup(final int size, final Color colour)
    {
        return outlined(size, colour, true);
    }

    /**
     * Two small outlines one above the other, a line in each: cut a group in two (OB-396).
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon split(final int size)
    {
        return split(size, QUIET);
    }

    /**
     * The same, in a given color.
     *
     * @param size how many pixels square
     * @param colour what to draw it in
     * @return the icon
     */
    public static Icon split(final int size, final Color colour)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                int half = (height - 3) / 2;

                g.setColor(colour);
                g.setStroke(new BasicStroke(1.2f));

                for (int part = 0; part < 2; part++)
                {
                    int y = 1 + part * (half + 1);

                    g.drawRoundRect(1, y, width - 3, half - 1, 4, 4);
                    g.drawLine(width / 4 + 1, y + half / 2, width * 3 / 4 - 1, y + half / 2);
                }
            }
        };
    }

    /**
     * A hooked arrow pointing back: Undo (FR-161).
     *
     * @param size how many pixels square
     * @param colour what to draw it in
     * @return the icon
     */
    public static Icon undo(final int size, final Color colour)
    {
        return hooked(size, colour, false);
    }

    /**
     * Undo's arrow, mirrored: Redo (FR-161).
     *
     * @param size how many pixels square
     * @param colour what to draw it in
     * @return the icon
     */
    public static Icon redo(final int size, final Color colour)
    {
        return hooked(size, colour, true);
    }

    private static Icon hooked(final int size, final Color colour, final boolean forward)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                if (forward)
                {
                    g.translate(width, 0);
                    g.scale(-1, 1);
                }

                java.awt.geom.Path2D.Float hook = new java.awt.geom.Path2D.Float();

                hook.moveTo(width * 0.32f, height * 0.38f);
                hook.lineTo(width * 0.58f, height * 0.38f);
                hook.curveTo(width * 0.92f, height * 0.38f, width * 0.92f, height * 0.84f, width * 0.58f, height * 0.84f);
                hook.lineTo(width * 0.36f, height * 0.84f);

                g.setColor(colour);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(hook);

                java.awt.geom.Path2D.Float head = new java.awt.geom.Path2D.Float();

                head.moveTo(width * 0.08f, height * 0.38f);
                head.lineTo(width * 0.38f, height * 0.16f);
                head.lineTo(width * 0.38f, height * 0.60f);
                head.closePath();

                g.fill(head);
            }
        };
    }

    private static Icon outlined(final int size, final Color colour, final boolean broken)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setColor(colour);
                g.setStroke(broken ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
                    new float[]{2.5f, 2f}, 0f) : new BasicStroke(1.2f));
                g.drawRoundRect(1, 2, width - 3, height - 5, 4, 4);

                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(width / 4 + 1, height * 2 / 5, width * 3 / 4 - 1, height * 2 / 5);
                g.drawLine(width / 4 + 1, height * 3 / 5 + 1, width * 3 / 4 - 1, height * 3 / 5 + 1);
            }
        };
    }

    /**
     * A warning triangle: amber, a dark edge and an exclamation mark - beside a line that is wrong.
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon warning(final int size)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                java.awt.geom.Path2D.Float triangle = new java.awt.geom.Path2D.Float();

                triangle.moveTo(width / 2f, 1f);
                triangle.lineTo(width - 1f, height - 1.5f);
                triangle.lineTo(1f, height - 1.5f);
                triangle.closePath();

                g.setColor(new Color(250, 190, 40));
                g.fill(triangle);

                g.setColor(new Color(150, 95, 0));
                g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(triangle);

                float middle = width / 2f;

                g.setColor(new Color(40, 30, 0));
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Line2D.Float(middle, height * 0.38f, middle, height * 0.64f));
                g.fill(new java.awt.geom.Ellipse2D.Float(middle - 0.95f, height * 0.73f, 1.9f, 1.9f));
            }
        };
    }

    /**
     * A wrench, head to the upper right, for the mark that opens a row to be edited (FR-164).
     *
     * @param size how many pixels square
     * @param colour what it is drawn in
     * @return the icon
     */
    public static Icon wrench(final int size, final Color colour)
    {
        java.awt.geom.Area shape = new java.awt.geom.Area(
            new java.awt.geom.Ellipse2D.Float(-3.3f, -7.8f, 6.6f, 6.6f));

        shape.add(new java.awt.geom.Area(
            new java.awt.geom.RoundRectangle2D.Float(-1.35f, -5f, 2.7f, 12.5f, 2.7f, 2.7f)));
        shape.subtract(new java.awt.geom.Area(
            new java.awt.geom.Rectangle2D.Float(-1.3f, -8.5f, 2.6f, 4.4f)));

        return turned(size, colour, shape);
    }

    /**
     * A shape drawn upright on a 13-unit grid around the origin, filled once turned a half right angle and scaled to
     * the icon.
     */
    private static Icon turned(final int size, final Color colour, final java.awt.Shape upright)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                java.awt.geom.AffineTransform turn = new java.awt.geom.AffineTransform();

                turn.translate(width / 2.0, height / 2.0);
                turn.scale(width / 13.0, height / 13.0);
                turn.rotate(Math.PI / 4);

                g.setColor(colour);
                g.fill(turn.createTransformedShape(upright));
            }
        };
    }

    /**
     * A pencil, tip to the lower left, for the mark that renames.
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon pencil(final int size)
    {
        return pencil(size, QUIET);
    }

    /**
     * The same, in a color of the caller's choosing.
     *
     * @param size how many pixels square
     * @param colour what it is drawn in
     * @return the icon
     */
    public static Icon pencil(final int size, final Color colour)
    {
        // Tip down: the body, the tip cut from it, and a gap where the eraser is set off
        java.awt.geom.Area shape = new java.awt.geom.Area(
            new java.awt.geom.RoundRectangle2D.Float(-2f, -6.5f, 4f, 10f, 1.6f, 1.6f));

        java.awt.geom.Path2D.Float tip = new java.awt.geom.Path2D.Float();

        tip.moveTo(-2f, 3f);
        tip.lineTo(2f, 3f);
        tip.lineTo(0f, 6.8f);
        tip.closePath();

        shape.add(new java.awt.geom.Area(tip));
        shape.subtract(new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Float(-2.2f, -4.6f, 4.4f, 0.9f)));

        return turned(size, colour, shape);
    }

    /** An icon that paints itself, antialiased, on a graphics it is handed. */
    private abstract static class Painted implements Icon
    {
        private final int size;

        Painted(int size)
        {
            this.size = size;
        }

        abstract void draw(Graphics2D g, int width, int height);

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D g2 = (Graphics2D) g.create(x, y, size, size);

            try
            {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

                draw(g2, size, size);
            }
            finally
            {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth()
        {
            return size;
        }

        @Override
        public int getIconHeight()
        {
            return size;
        }
    }
}
