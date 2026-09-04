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
     * A column keeps its number when the one cell the ruler measured is not there (OB-172).
     *
     * Adam: *"some axis labels vanish when switching between track diagram editor and autonomy
     * editor - 1 and 3 in my case.  they reappear if the grid setting is cycled."*
     *
     * **The ruler was told to measure one cell per column** - `getValueAt(column, 0)`, the top row -
     * and one per row, the left-hand column. `paintBorder` skips on `cell == null || cell.width <= 0`,
     * so any reason that single square is absent, or has no bounds yet, deletes the label for the
     * whole column. One square decided a number about twenty-three others.
     *
     * **What I could NOT establish is that this is the mechanism he saw**, and it is written down
     * rather than glossed. His `1 - Main` page does have tiles in its top row at both columns he
     * named, so an always-empty square is ruled out. A square whose bounds are still zero when the
     * border first paints after an editor swap fits the symptom - including its going away when the
     * grid setting is cycled and everything is rebuilt - but reproducing that needs the two editors in
     * front of me. This closes the class of fault; whether it closes his is `MT-268`.
     *
     * MUTATION: going back to a single source per column fails the second assertion, and the first
     * assertion is what stops that mutation passing by drawing nothing at all.
     */
    @Test
    public void testAColumnKeepsItsNumberWhenOneCellIsMissing()
    {
        final int pitch = 31;
        final int cell = 30;
        final int gutter = 18;
        final int columns = 6;

        // The top row has nothing usable at columns 1 and 3 - the two he lost - and the row under it
        // has everything.
        java.util.function.IntFunction<java.awt.Rectangle> gappy =
            column -> (column == 1 || column == 3)
                ? null
                : new java.awt.Rectangle(gutter + column * pitch, gutter, cell, cell);

        java.util.function.IntFunction<java.awt.Rectangle> whole =
            column -> new java.awt.Rectangle(gutter + column * pitch, gutter, cell, cell);

        // THE CONTROL FIRST: asking only the gappy row loses exactly two numbers.  Without this, a
        // ruler that never had the fault would satisfy the assertion below.
        assertEquals(inkGroups(paint(new AxisRuler(0, 0, columns, 0, gappy, row -> null))).size(),
            columns - 2,
            "the fixture is wrong: one gappy row should cost exactly the two numbers whose cell is "
            + "absent, so the fallback below would be proving nothing");

        java.util.List<java.util.function.IntFunction<java.awt.Rectangle>> sources =
            java.util.Arrays.asList(gappy, whole);

        java.util.List<java.util.function.IntFunction<java.awt.Rectangle>> noRows =
            java.util.Collections.emptyList();

        assertEquals(inkGroups(paint(AxisRuler.overRows(0, 0, columns, 0, sources, noRows))).size(),
            columns,
            "a column lost its number because the one cell the ruler measures was not there.  Every "
            + "column on that diagram exists; one absent square should not delete a label (OB-172)");
    }

    /**
     * A zero-sized cell falls through to the next place to look, rather than being handed on (OB-172).
     *
     * The half that matters for the symptom Adam actually described. An absent square is a null; a
     * square that exists but has not been laid out yet is a `Rectangle` of no size, and `paintBorder`
     * refuses both. `firstUsable` therefore has to refuse both too - returning an empty rectangle
     * because it was not null would move the skip one level up and change nothing.
     *
     * MUTATION: testing only `found != null` fails this.
     */
    @Test
    public void testAnUnlaidOutCellIsNotAnAnswer()
    {
        java.awt.Rectangle real = new java.awt.Rectangle(10, 10, 30, 30);

        java.util.function.IntFunction<java.awt.Rectangle> notYet =
            index -> new java.awt.Rectangle(0, 0, 0, 0);

        java.util.function.IntFunction<java.awt.Rectangle> absent = index -> null;

        assertEquals(AxisRuler.firstUsable(java.util.Arrays.asList(notYet, index -> real), 0), real,
            "a cell that exists but has no size yet was accepted as the answer, so the number is "
            + "still skipped - which is the state an editor swap leaves behind");

        assertEquals(AxisRuler.firstUsable(java.util.Arrays.asList(absent, index -> real), 0), real,
            "an absent cell was not fallen through");

        assertEquals(AxisRuler.firstUsable(java.util.Arrays.asList(absent, notYet), 0), null,
            "with nowhere usable to look this must say so, not invent a rectangle");
    }
    /**
     * A child standing in the gutter does not rub out the number underneath it (OB-172).
     *
     * **This is what OB-172 actually was, after two wrong answers.** Adam lost axis numbers on the two
     * pages whose columns start at zero, and they came back when the grid setting was cycled. I looked
     * for a number being SKIPPED twice - a missing cell, then an unmeasured one - and measured his own
     * page to settle it: every cell the ruler asks about is present and exactly 30x30, against a
     * 14-pixel font. Not one of `paintBorder`'s three skip clauses can fire there. The numbers were
     * never being skipped.
     *
     * `JComponent.paint` calls `paintBorder` and THEN `paintChildren`, so any child reaching into the
     * eighteen pixels the ruler reserves is drawn over the number. A `StationCaption` is 38 pixels tall
     * and up to 55 wide against a 30-pixel cell - which is why one of them takes out two rows' numbers,
     * and why the top-left one takes the `0` off the column axis as well.
     *
     * `LayoutGrid.newDiagramContainer` now paints the ruler again at the end of its `paint()`
     * override, which is unambiguously after background, border and children.
     *
     * It was first put at the end of `paintChildren` - the hook that already draws trains over
     * captions - and that did not work: the override ran, the border was the ruler and the clip was
     * the whole component, and the numbers were still rubbed out.  Swing hands each child its own
     * graphics inside `paintChildren`.  This sentence described that first attempt for a while after
     * it was reverted (FR3-C4).
     *
     * MUTATION: removing that block fails this. The first assertion is what stops the mutation passing
     * by painting nothing at all.
     */
    @Test
    public void testAChildInTheGutterDoesNotRubOutTheNumbers() throws Exception
    {
        final int size = 30;
        final int columns = 5;

        final javax.swing.JPanel container = org.traincontrol.gui.LayoutGrid.newDiagramContainer();

        container.setLayout(null);

        // WHITE, so that "ink" means a number rather than the panel's own grey.  The first version of
        // this test left the default background and counted 3024 non-white pixels in an 18x168 strip -
        // which is every pixel of it, and told me nothing about the numbers at all.
        container.setOpaque(true);
        container.setBackground(java.awt.Color.WHITE);

        container.setBorder(AxisRuler.uniform(size, 0, 0, columns, columns));

        container.setBounds(0, 0, 18 + columns * size, 18 + columns * size);

        // AN OPAQUE CHILD ACROSS THE WHOLE GUTTER, which is what a caption at the edge amounts to.
        javax.swing.JPanel intruder = new javax.swing.JPanel();

        intruder.setOpaque(true);
        intruder.setBackground(java.awt.Color.WHITE);
        intruder.setBounds(0, 0, container.getWidth(), container.getHeight());

        container.add(intruder);

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
            container.getWidth(), container.getHeight(),
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = img.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());

        container.paint(g);

        g.dispose();

        int inkInGutter = 0;

        for (int x = 0; x < img.getWidth(); x++)
        {
            for (int y = 0; y < 18; y++)
            {
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) inkInGutter++;
            }
        }

        assertTrue(inkInGutter > 0,
            "nothing at all was painted in the gutter, so this test would pass against a ruler that "
            + "never drew anything - check the fixture before reading the failure below");

        // And the same container WITHOUT the intruder, as the yardstick.
        container.remove(intruder);

        java.awt.image.BufferedImage clean = new java.awt.image.BufferedImage(
            container.getWidth(), container.getHeight(),
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g2 = clean.createGraphics();

        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, clean.getWidth(), clean.getHeight());

        container.paint(g2);

        g2.dispose();

        int inkWithout = 0;

        for (int x = 0; x < clean.getWidth(); x++)
        {
            for (int y = 0; y < 18; y++)
            {
                if ((clean.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) inkWithout++;
            }
        }


        assertEquals(inkInGutter, inkWithout,
            "a child covering the gutter rubbed out " + (inkWithout - inkInGutter) + " pixels of the "
            + "axis numbers.  A Border is painted before the container's children, so the ruler has to "
            + "be drawn again after them - which is what Adam lost on the two pages whose columns "
            + "start at zero (OB-172)");
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
