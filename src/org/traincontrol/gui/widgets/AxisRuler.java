package org.traincontrol.gui.widgets;

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
 * Adam: **"coordinates are referenced in issues but not visible to the user.  add a grid around the diagram"**, and
 * **"axis labels can be printed in both autonomy editor and track diagram editor, with an optional toggle"**.
 *
 * Every warning, finding and manual test names a square as `page:x,y`, and the numbers let a reader find it without
 * counting cells from the corner.
 *
 * **A Border rather than two more rows of components.** The diagram is a `GridBagLayout` whose cells are addressed by
 * their own coordinates; putting a ruler INTO that grid would shift every cell by one, and `LayoutGrid` hands out
 * `getValueAt(x, y)` and `getCoordinates(label)` to callers all over the application, each of which would have to undo
 * the shift.  A border paints in the margin the layout manager already reserves, and the grid inside is untouched.
 *
 * **It asks the grid where the cells ARE; it does not work them out from the tile size.**  Multiplying the tile size by
 * the column index is right only while every cell is exactly a tile wide, and with the gray grid on each square wears a
 * line border that reserves room, so the error is cumulative: the first numbers sit over their squares and the
 * twentieth is a whole square out.  A ruler that measures cannot drift.
 *
 * **It draws the diagram's own numbers, not the cell indices**: a diagram whose left-most track is at x = 4 is rendered
 * from x = 4, so the printed number is the one an issue would quote.
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

    /** The numbers' size in points: smaller than the diagram's own text. */
    private static final float FONT_SIZE = 10f;

    /** And the grid's gray they are drawn in. */
    private static final Color INK = Color.GRAY;

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
     * The first usable rectangle among several places one might be found (OB-172).
     *
     * A column's number was positioned from the one cell in the top row (a row's from the left-hand column), and
     * `paintBorder` skips on `cell == null || cell.width <= 0`, so one absent or not-yet-sized square deleted the label
     * for the whole column (Adam: *"some axis labels vanish when switching between track diagram editor and autonomy
     * editor"*).  The caller offers a few places to look, all the same column, so any gives the same x; this just stops
     * one missing square being fatal.
     *
     * @param at the places to look, in order of preference
     * @param index the column or row being asked about
     * @return the first rectangle with a real size, or null when none of them has one
     */
    public static Rectangle firstUsable(java.util.List<IntFunction<Rectangle>> at, int index)
    {
        if (at == null) return null;

        Rectangle best = null;

        for (IntFunction<Rectangle> source : at)
        {
            if (source == null) continue;

            Rectangle found = source.apply(index);

            // The same emptiness `paintBorder` refuses, so a zero-sized cell falls through rather
            // than being handed on as an answer.
            if (found == null || found.width <= 0 || found.height <= 0) continue;

            // The biggest, not the first (OB-172): a spacer is not empty, it is a real component with a tiny size (two
            // pixels tall against a fourteen-pixel font), so a zero-check lets it through and the method answered with
            // the one candidate that could not carry a number.  Every candidate describes the same row or column, so
            // only the size differs and taking the largest is free.
            if (best == null || (long) found.width * found.height
                > (long) best.width * best.height)
            {
                best = found;
            }
        }

        return best;
    }

    /**
     * A ruler that looks in several rows for each column, and several columns for each row (OB-172).
     *
     * @param offsetX the x the left-most column actually is
     * @param offsetY the y the top row actually is
     * @param columns how many columns
     * @param rows how many rows
     * @param columnSources where to look for a column's position, in order of preference
     * @param rowSources the same for a row
     * @return a ruler no single missing square can silence
     */
    public static AxisRuler overRows(int offsetX, int offsetY, int columns, int rows,
        final java.util.List<IntFunction<Rectangle>> columnSources,
        final java.util.List<IntFunction<Rectangle>> rowSources)
    {
        return new AxisRuler(offsetX, offsetY, columns, rows,
            column -> firstUsable(columnSources, column),
            row -> firstUsable(rowSources, row));
    }
    /**
     * A ruler over cells that really are exactly one tile apart.
     *
     * For tests, and for any caller that knows its grid has no borders on it.  The real diagram does not use this:
     * whether a square is a tile wide depends on whether the gray grid is switched on, the assumption that put the
     * numbers out of alignment.
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

        // Smaller than the diagram's own text and in the grid's gray, because this is scenery for
        // reading coordinates off rather than part of the railway.
        g.setFont(wasFont.deriveFont(Font.PLAIN, FONT_SIZE));
        g.setColor(INK);

        FontMetrics metrics = g.getFontMetrics();

        // Across the top, centered over the square as it actually is, and skipped when the number is wider than that
        // square: a number that overhangs its neighbor points at the wrong square, which is worse than a gap.
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
