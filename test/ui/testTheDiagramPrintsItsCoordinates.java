package ui;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.AxisRuler;

/**
 * FR-057: the column and row numbers around a track diagram.
 *
 * Adam: **"coordinates are referenced in issues but not visible to the user.  add a grid around the
 * diagram"**, and **"axis labels can be printed in both autonomy editor and track diagram editor,
 * with an optional toggle"**.
 *
 * **The one thing that can be wrong here and look right is the NUMBER.** A diagram is drawn from its
 * left-most and top-most track rather than from zero, so a ruler that printed cell indices would be
 * neat, aligned, plausible, and off by the offset - which is the exact opposite of what this feature
 * exists for, since every warning names a square as `page:x,y` in the diagram's own numbering.
 *
 * So the assertions below read the digits back off a painted image rather than trusting the drawing
 * code. Painting into a `BufferedImage` needs no window, so this class runs anywhere.
 *
 * @author Adam
 */
public class testTheDiagramPrintsItsCoordinates
{
    /** A square, big enough for two digits at the ruler's own font size. */
    private static final int SIZE = 30;

    /**
     * The gutter is on the top and the left, and nowhere else.
     *
     * The numbers go in the space the layout manager reserves for the border, so an inset on the wrong
     * side moves the whole diagram and the numbers land beside the wrong squares.
     */
    @Test
    public void testTheGutterIsOnTheTopAndTheLeft()
    {
        java.awt.Insets insets = AxisRuler.uniform(SIZE, 0, 0, 4, 4).getBorderInsets(null);

        assertTrue(insets.top > 0, "no room was reserved above the diagram for the column numbers");
        assertTrue(insets.left > 0, "no room was reserved beside the diagram for the row numbers");

        assertEquals(insets.top, insets.left,
            "the two gutters differ, so the corner is not square and the columns no longer line up "
            + "with the rows");

        assertEquals(insets.bottom, 0, "the ruler reserved room below the diagram, where it draws none");
        assertEquals(insets.right, 0, "the ruler reserved room beside the diagram, where it draws none");
    }

    /**
     * The numbers are the DIAGRAM's, not the cell indices.
     *
     * A page whose left-most track sits at x = 12 is drawn from x = 12, and this is the assertion that
     * a ruler printing 0, 1, 2 - which is what the loop variable is - would fail.
     *
     * MUTATION this catches: dropping `offsetX`/`offsetY` from the two `Integer.toString` calls.
     */
    @Test
    public void testTheNumbersAreTheDiagramsOwn()
    {
        String zeroBased = paint(AxisRuler.uniform(SIZE, 0, 0, 4, 3));
        String offset = paint(AxisRuler.uniform(SIZE, 12, 7, 4, 3));

        assertFalse(zeroBased.equals(offset),
            "a diagram drawn from 12,7 printed the same ruler as one drawn from 0,0 - so the numbers "
            + "are the cell indices, which is every coordinate in every warning off by the offset");

        assertTrue(digitsIn(zeroBased).contains("3"),
            "a four-column ruler starting at zero never printed a 3.  What it printed: "
            + digitsIn(zeroBased));

        assertTrue(digitsIn(offset).contains("15"),
            "a four-column ruler starting at twelve never printed a 15.  What it printed: "
            + digitsIn(offset));

        assertTrue(digitsIn(offset).contains("9"),
            "a three-row ruler starting at seven never printed a 9.  What it printed: "
            + digitsIn(offset));
    }

    /**
     * A square too small for its number prints nothing rather than something misleading.
     *
     * A number wider than the square it belongs to overhangs its neighbour, and a number pointing at
     * the wrong square is worse than a gap - this is a feature for finding a square by its coordinate.
     */
    @Test
    public void testANumberTooWideForItsSquareIsLeftOut()
    {
        // THE READER FIRST (TSX-C7).
        //
        // `digitsIn` recognises numbers by re-rendering each candidate with a font it chooses itself
        // and sliding the stamp over the painted ink.  If that font ever stops matching the one the
        // ruler draws with, it recognises NOTHING - and an assertion that nothing was drawn is then
        // satisfied by a reader that cannot read.  A sibling two methods up would fail loudly, which
        // is a rescue rather than a guard.
        String big = paint(AxisRuler.uniform(30, 100, 100, 6, 6));

        assertFalse(digitsIn(big).isEmpty(),
            "the reader found no numbers on a ruler with thirty pixels a square, where they certainly "
            + "fit - so it is not reading, and the assertion below would pass whatever was drawn");

        // Four pixels a square: nothing fits, so nothing should be drawn.
        String tiny = paint(AxisRuler.uniform(4, 100, 100, 6, 6));

        assertEquals(digitsIn(tiny), java.util.Collections.emptyList(),
            "three-digit numbers were printed into four-pixel squares, so each one overhangs its "
            + "neighbours and points at the wrong square");
    }

    /**
     * Every number sits over its own square, even where the squares are not a tile apart (FR-057).
     *
     * Adam: *"the axis numbers drift and are out of alignment.  The first few are centered, but the
     * rest aren't.  That's why it looks off by one."*
     *
     * **The first version worked the positions out as `column * size`**, and that is right only while
     * every cell is exactly one tile wide.  With the grey grid switched on each square wears a line
     * border that reserves room, so the real pitch is a pixel or two more - and the error is
     * cumulative.  The first few numbers land over their squares; the twentieth is a whole square out.
     *
     * Every other test in this class used a uniform pitch, so every one of them agreed with the
     * arithmetic that was wrong.  This one gives the ruler cells that are thirty-one pixels apart and
     * thirty wide - which is exactly what the grid with its borders on looks like - and then reads back
     * where the ink landed.
     *
     * MUTATION this catches: going back to `column * size`, or centring on the pitch rather than on
     * the cell.
     */
    @Test
    public void testTheNumbersFollowTheSquaresRatherThanTheTileSize()
    {
        final int pitch = 31;
        final int cell = 30;
        final int gutter = 18;
        final int columns = 12;

        AxisRuler drifting = new AxisRuler(0, 0, columns, 0,
            column -> new java.awt.Rectangle(gutter + column * pitch, gutter, cell, cell),
            row -> null);

        java.util.List<int[]> ink = inkGroups(paint(drifting));

        assertEquals(ink.size(), columns,
            "a twelve-column ruler painted " + ink.size() + " numbers");

        for (int column = 0; column < columns; column++)
        {
            int[] group = ink.get(column);

            int left = gutter + column * pitch;

            assertTrue(group[0] >= left && group[1] <= left + cell,
                "the number for column " + column + " was painted at " + group[0] + ".." + group[1]
                + ", and its square is " + left + ".." + (left + cell) + " - which is the drift Adam "
                + "reported: the first few are centred and the rest walk off their squares");
        }
    }

    /**
     * The ranges of x where the top strip has ink on it, one per number.
     *
     * A gap of a pixel or two inside a two-digit number would split it in two, so groups are joined
     * across small gaps: what is being measured is which SQUARE a number sits over, not how its digits
     * are spaced.
     */
    private static java.util.List<int[]> inkGroups(String ink)
    {
        java.util.List<int[]> out = new java.util.ArrayList<>();

        int start = -1, last = -1;

        for (int x = 0; x < 400; x++)
        {
            boolean inked = false;

            for (int y = 0; y < 18; y++)
            {
                if (ink.charAt(y * 400 + x) == '#') inked = true;
            }

            if (inked)
            {
                if (start < 0) start = x;

                last = x;
            }
            else if (start >= 0 && x - last > 3)
            {
                out.add(new int[] { start, last });

                start = -1;
            }
        }

        if (start >= 0) out.add(new int[] { start, last });

        return out;
    }

    /**
     * Paints a ruler onto a blank image and hands back the ink, as a string of pixel rows.
     *
     * Compared rather than decoded: two rulers that print different numbers cannot paint identical
     * ink, and that is the whole of what the offset assertion needs.
     */
    private static String paint(AxisRuler ruler)
    {
        java.awt.image.BufferedImage image =
            new java.awt.image.BufferedImage(400, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = image.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 400, 300);

        ruler.paintBorder(new javax.swing.JPanel(), g, 0, 0, 400, 300);

        g.dispose();

        StringBuilder ink = new StringBuilder();

        for (int y = 0; y < 300; y++)
        {
            for (int x = 0; x < 400; x++)
            {
                ink.append(image.getRGB(x, y) == java.awt.Color.WHITE.getRGB() ? '.' : '#');
            }
        }

        return ink.toString();
    }

    /**
     * Which numbers a painted ruler actually shows, read back off the image.
     *
     * Crude on purpose: the glyphs are not decoded, they are re-rendered one candidate at a time and
     * matched by their ink. That is enough to say "15 appears somewhere on this ruler" without a font
     * library, and it fails honestly if the drawing font ever changes - by finding nothing rather than
     * by finding the wrong thing.
     */
    private static java.util.List<String> digitsIn(String ink)
    {
        java.util.List<String> found = new java.util.ArrayList<>();

        for (int n = 0; n <= 30; n++)
        {
            if (appears(ink, Integer.toString(n))) found.add(Integer.toString(n));
        }

        return found;
    }

    /**
     * Whether this number's glyphs appear in the painted ink.
     *
     * Rendered with the same font and size the ruler uses and slid across the image, which is the only
     * way to ask the question without decoding what was drawn.
     */
    private static boolean appears(String ink, String number)
    {
        java.awt.image.BufferedImage stamp =
            new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = stamp.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 40, 20);
        g.setFont(new javax.swing.JPanel().getFont().deriveFont(java.awt.Font.PLAIN, 10f));
        g.setColor(java.awt.Color.GRAY);
        g.drawString(number, 2, 14);
        g.dispose();

        java.awt.Rectangle box = inkBounds(stamp, 40, 20);

        if (box == null) return false;

        for (int y = 0; y + box.height <= 300; y++)
        {
            for (int x = 0; x + box.width <= 400; x++)
            {
                if (matches(ink, stamp, box, x, y)) return true;
            }
        }

        return false;
    }

    /** The smallest rectangle holding everything drawn on an image. */
    private static java.awt.Rectangle inkBounds(java.awt.image.BufferedImage image, int w, int h)
    {
        int minX = w, minY = h, maxX = -1, maxY = -1;

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                if (image.getRGB(x, y) == java.awt.Color.WHITE.getRGB()) continue;

                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        return maxX < 0 ? null : new java.awt.Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Whether the stamp's ink sits at this position in the painted image. */
    private static boolean matches(String ink, java.awt.image.BufferedImage stamp,
        java.awt.Rectangle box, int atX, int atY)
    {
        for (int y = 0; y < box.height; y++)
        {
            for (int x = 0; x < box.width; x++)
            {
                boolean wanted =
                    stamp.getRGB(box.x + x, box.y + y) != java.awt.Color.WHITE.getRGB();

                boolean there = ink.charAt((atY + y) * 400 + (atX + x)) == '#';

                if (wanted != there) return false;
            }
        }

        return true;
    }
}
