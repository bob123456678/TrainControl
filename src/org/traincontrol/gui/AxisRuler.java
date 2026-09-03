package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.function.IntFunction;
import javax.swing.border.Border;

/**
 * Column and row numbers along the top and the left of a track diagram (FR-057).
 *
 * Adam: **"coordinates are referenced in issues but not visible to the user.  add a grid around the
 * diagram"**, and **"axis labels can be printed in both autonomy editor and track diagram editor,
 * with an optional toggle"**.
 *
 * Every warning the autonomy editor raises, every finding in a review and every square named in a
 * manual test is written as `page:x,y` - and until now the only way to work out which square that was
 * was to count cells from the corner. The numbers are on the diagram now.
 *
 * **A Border rather than two more rows of components.** The diagram is a `GridBagLayout` whose cells
 * are addressed by their own coordinates, with a spacer row and column at the far edge and a
 * `gridwidth = 0` rule for squares whose text overflows. Putting a ruler INTO that grid means shifting
 * every cell by one and re-deriving four other things from the new origin - and `LayoutGrid` hands out
 * `getValueAt(x, y)` and `getCoordinates(label)` to callers all over the application, so the shift
 * would have to be undone again at every one of them. A border paints in the margin the layout manager
 * already reserves for it, and the grid inside is untouched.
 *
 * **It asks the grid where the cells ARE.  It does not work them out from the tile size.**
 *
 * That is the second version of this class and the reason is Adam's: *"the axis numbers drift and are
 * out of alignment.  The first few are centered, but the rest aren't.  That's why it looks off by
 * one."*  The first version multiplied the tile size by the column index, which is right only while
 * every cell is exactly a tile wide - and with the grey grid switched on each square wears a line
 * border that reserves room, so the real pitch is a pixel or two more. The error is cumulative: the
 * first few numbers sit over their squares and the twentieth is a whole square out.
 *
 * A ruler that measures cannot drift, whatever a square turns out to be made of.
 *
 * **It draws the diagram's own numbers, not the cell indices.** A diagram whose left-most track is at
 * x = 4 is rendered from x = 4, and the offsets are what make the printed number the one an issue
 * would quote.
 *
 * @author Adam
 */
public class AxisRuler implements Border
{
    /**
     * How much room the numbers get, at the top and on the left.
     *
     * One size for both, so that the corner is square and the columns line up with the rows however
     * wide the numbers are. Two digits is what a diagram of any ordinary size needs; three fit, and a
     * fourth would be a diagram nobody could read anyway.
     */
    private static final int GUTTER = 18;

    /** So the numbers do not crowd the first square. */
    private static final int PAD = 2;

    private final int offsetX;

    private final int offsetY;

    private final int columns;

    private final int rows;

    private final IntFunction<Rectangle> columnAt;

    private final IntFunction<Rectangle> rowAt;

    /**
     * @param offsetX the x the left-most column of the grid actually is
     * @param offsetY the y the top row of the grid actually is
     * @param columns how many columns the grid draws
     * @param rows how many rows the grid draws
     * @param columnAt where a column actually sits, in the container's own pixels, or null if it does
     *  not exist yet - asked at PAINT time, which is the whole point
     * @param rowAt the same for a row
     */
    public AxisRuler(int offsetX, int offsetY, int columns, int rows,
        IntFunction<Rectangle> columnAt, IntFunction<Rectangle> rowAt)
    {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.columns = columns;
        this.rows = rows;
        this.columnAt = columnAt;
        this.rowAt = rowAt;
    }

    /**
     * A ruler over cells that really are exactly one tile apart.
     *
     * For tests, and for any caller that knows its grid has no borders on it. The real diagram does
     * not use this: whether a square is a tile wide depends on whether the grey grid is switched on,
     * which is a setting, which is exactly the assumption that put the numbers out of alignment.
     *
     * @param size the width and height of one square, in pixels
     * @param offsetX the x the left-most column actually is
     * @param offsetY the y the top row actually is
     * @param columns how many columns
     * @param rows how many rows
     * @return a ruler that assumes a uniform pitch
     */
    public static AxisRuler uniform(final int size, int offsetX, int offsetY, int columns, int rows)
    {
        return new AxisRuler(offsetX, offsetY, columns, rows,
            column -> new Rectangle(GUTTER + column * size, GUTTER, size, size),
            row -> new Rectangle(GUTTER, GUTTER + row * size, size, size));
    }

    @Override
    public Insets getBorderInsets(Component c)
    {
        return new Insets(GUTTER, GUTTER, 0, 0);
    }

    @Override
    public boolean isBorderOpaque()
    {
        return false;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
    {
        Color was = g.getColor();
        Font wasFont = g.getFont();

        // Smaller than the diagram's own text and in the grid's grey, because this is scenery for
        // reading coordinates off rather than part of the railway.
        g.setFont(wasFont.deriveFont(Font.PLAIN, 10f));
        g.setColor(Color.GRAY);

        FontMetrics metrics = g.getFontMetrics();

        // ACROSS THE TOP.
        //
        // Centred over the square as it actually is, and skipped when the number is wider than that
        // square - a number that overhangs its neighbour points at the wrong square, which is worse
        // than a gap.
        for (int column = 0; column < columns; column++)
        {
            Rectangle cell = columnAt == null ? null : columnAt.apply(column);

            if (cell == null || cell.width <= 0) continue;

            String label = Integer.toString(column + offsetX);

            int textWidth = metrics.stringWidth(label);

            if (textWidth > cell.width) continue;

            g.drawString(label, x + cell.x + (cell.width - textWidth) / 2,
                y + GUTTER - PAD - metrics.getDescent());
        }

        // AND DOWN THE LEFT, right-aligned into the gutter so the digits line up under each other
        // whether they are one, two or three wide.
        for (int row = 0; row < rows; row++)
        {
            Rectangle cell = rowAt == null ? null : rowAt.apply(row);

            if (cell == null || cell.height <= 0) continue;

            if (metrics.getHeight() > cell.height) continue;

            String label = Integer.toString(row + offsetY);

            int textWidth = metrics.stringWidth(label);

            g.drawString(label, x + GUTTER - PAD - textWidth,
                y + cell.y + (cell.height + metrics.getAscent()) / 2);
        }

        g.setFont(wasFont);
        g.setColor(was);
    }
}
