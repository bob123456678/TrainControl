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
        java.awt.Insets insets = new AxisRuler(SIZE, 0, 0, 4, 4).getBorderInsets(null);

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
        String zeroBased = paint(new AxisRuler(SIZE, 0, 0, 4, 3));
        String offset = paint(new AxisRuler(SIZE, 12, 7, 4, 3));

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
        // Four pixels a square: nothing fits, so nothing should be drawn.
        String tiny = paint(new AxisRuler(4, 100, 100, 6, 6));

        assertEquals(digitsIn(tiny), java.util.Collections.emptyList(),
            "three-digit numbers were printed into four-pixel squares, so each one overhangs its "
            + "neighbours and points at the wrong square");
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
