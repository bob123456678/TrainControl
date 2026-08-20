package org.traincontrol.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * The small marks that live inside a table row: delete, add, and the two move arrows.
 *
 * Drawn rather than loaded, for two reasons. There is no icon art in this application to match - the
 * only images it ships are track tiles and the window icon - and a glyph font would have been the
 * other option, but the trash can and the arrows live in parts of Unicode that Segoe UI on Java 8
 * renders unevenly or not at all. Twenty lines of Java2D always look the same.
 *
 * Sized from the row height by the caller, so they stay in proportion if the table's font changes.
 */
public final class RowIcons
{
    /** Grey enough to read as a control rather than as content. */
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
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setColor(WARN);
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

    /**
     * A plus.
     *
     * @param size how many pixels square
     * @return the icon
     */
    public static Icon plus(final int size)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setColor(new Color(0, 120, 40));
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
     * The same, in a given colour.
     *
     * A selected row is painted in the table's selection colours, and a mark drawn in a fixed grey
     * disappears into it - which is exactly when somebody is most likely to be reaching for it, since
     * selecting a row is how you decide which one to move.
     *
     * @param size how many pixels square
     * @param up true for up
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
     * An arrow into or out of an indent, drawn as a bar and a chevron pointing at it.
     *
     * @param size how many pixels square
     * @param inward true to indent, false to bring back out
     * @return the icon
     */
    public static Icon indent(final int size, final boolean inward)
    {
        return indent(size, inward, QUIET);
    }

    /**
     * The same, in a given colour.
     *
     * @param size how many pixels square
     * @param inward true to indent
     * @param colour what to draw it in
     * @return the icon
     */
    public static Icon indent(final int size, final boolean inward, final Color colour)
    {
        return new Painted(size)
        {
            @Override
            void draw(Graphics2D g, int width, int height)
            {
                g.setColor(colour);
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int pad = width / 5;
                int middle = height / 2;

                // The bar the row moves towards
                int bar = inward ? width - pad : pad;

                g.drawLine(bar, pad, bar, height - pad);

                // And the chevron pointing at it
                int tip = inward ? bar - pad : bar + pad;
                int tail = inward ? pad : width - pad;

                g.drawLine(tail, middle, tip, middle);
                g.drawLine(tip, middle, tip - (inward ? pad : -pad), middle - pad);
                g.drawLine(tip, middle, tip - (inward ? pad : -pad), middle + pad);
            }
        };
    }

    /**
     * The faint vertical lines that show how deep a row sits.
     *
     * Indentation on its own is a distance, and a distance is hard to count - two levels and three
     * look much the same at a glance, and there is nothing to say which rows share a level. A line
     * per level turns it into something countable, which is what every outliner and every code editor
     * draws for the same reason.
     *
     * @param depth how many levels in
     * @param step how many pixels one level is worth
     * @return a border that draws them and leaves room for the text
     */
    public static javax.swing.border.Border guides(final int depth, final int step)
    {
        return new javax.swing.border.Border()
        {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
            {
                Graphics2D g2 = (Graphics2D) g.create();

                try
                {
                    g2.setColor(new Color(200, 200, 200));

                    for (int at = 0; at < depth; at++)
                    {
                        int line = x + 6 + at * step;

                        g2.drawLine(line, y, line, y + height - 1);
                    }
                }
                finally
                {
                    g2.dispose();
                }
            }

            @Override
            public java.awt.Insets getBorderInsets(Component c)
            {
                return new java.awt.Insets(0, 6 + depth * step + 4, 0, 0);
            }

            @Override
            public boolean isBorderOpaque()
            {
                return false;
            }
        };
    }

    /**
     * An icon that paints itself, antialiased, on a graphics it is handed.
     */
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
