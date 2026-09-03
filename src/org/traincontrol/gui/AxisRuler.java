package org.traincontrol.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.border.Border;

/**
 * Column and row numbers along the top and the left of a track diagram (FR-057).
 *
 * Adam: **"coordinates are referenced in issues but not visible to the user.  add a grid around the
 * diagram"**, and then **"axis labels can be printed in both autonomy editor and track diagram
 * editor, with an optional toggle"**.
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

    private final int size;

    private final int offsetX;

    private final int offsetY;

    private final int columns;

    private final int rows;

    /**
     * @param size the width and height of one square, in pixels
     * @param offsetX the x the left-most column of the grid actually is
     * @param offsetY the y the top row of the grid actually is
     * @param columns how many columns the grid draws
     * @param rows how many rows the grid draws
     */
    public AxisRuler(int size, int offsetX, int offsetY, int columns, int rows)
    {
        this.size = size;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.columns = columns;
        this.rows = rows;
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
        if (size <= 0) return;

        Color was = g.getColor();
        Font wasFont = g.getFont();

        // Smaller than the diagram's own text and in the grid's grey, because this is scenery for
        // reading coordinates off rather than part of the railway.
        g.setFont(wasFont.deriveFont(Font.PLAIN, 10f));
        g.setColor(Color.GRAY);

        FontMetrics metrics = g.getFontMetrics();

        // ACROSS THE TOP.
        //
        // Centred over its column, and skipped when the number is wider than the square it belongs to -
        // a number that overhangs its neighbour points at the wrong square, which is worse than a gap.
        for (int column = 0; column < columns; column++)
        {
            String label = Integer.toString(column + offsetX);

            int textWidth = metrics.stringWidth(label);

            if (textWidth > size) continue;

            int centre = x + GUTTER + column * size + (size - textWidth) / 2;

            g.drawString(label, centre, y + GUTTER - PAD - metrics.getDescent());
        }

        // AND DOWN THE LEFT, right-aligned into the gutter so the digits line up under each other
        // whether they are one, two or three wide.
        for (int row = 0; row < rows; row++)
        {
            String label = Integer.toString(row + offsetY);

            int textWidth = metrics.stringWidth(label);

            if (metrics.getHeight() > size) continue;

            int right = x + GUTTER - PAD - textWidth;

            int middle = y + GUTTER + row * size + (size + metrics.getAscent()) / 2;

            g.drawString(label, right, middle);
        }

        g.setFont(wasFont);
        g.setColor(was);
    }
}
