package ui;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.image.BufferedImage;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.widgets.HandPointer;

/**
 * The hand pointer TrainControl draws for itself on Java 8 (FR-224): a picture of the pointer box Windows asks for,
 * pointing at its hotspot, with a hard outer edge (Windows keeps only a 1-bit mask) and a smooth inside.  No window.
 *
 * @author Adam
 */
public class testTheHandPointerIsDrawnSmooth
{
    /** Windows' pointer boxes on Java 8: 100% and 125%, 150% and 175%, 200%. */
    private static final int[] SIZES = {32, 48, 64};

    private static final String JAVA = System.getProperty("java.specification.version");

    /**
     * Java 8 refuses a hotspot outside the picture and rescales a picture of the wrong size without moving the
     * hotspot, so both must match the box; the hotspot is the fingertip, the topmost opaque pixel.
     *
     * MUTATION: drawing the hand in a 32-pixel square whatever the size asked fails the size assertion at 48.
     */
    @Test
    public void testTheHandFillsTheBoxAndPointsWithItsFingertip()
    {
        for (int size : SIZES)
        {
            BufferedImage img = HandPointer.image(size);
            Point hot = HandPointer.hotspot(size);
            String what = "at " + size;

            assertEquals(img.getWidth(), size, what + ": width");
            assertEquals(img.getHeight(), size, what + ": height");
            assertEquals(hot.y, 0, what + ": the hotspot is on the top row");
            assertTrue(opaque(img, hot.x, 0), what + ": the hotspot " + hot + " is on the hand");

            int left = size, right = -1;

            for (int x = 0; x < size; x++)
            {
                if (opaque(img, x, 0))
                {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            assertTrue(Math.abs(hot.x - (left + right) / 2.0) <= 1, what + ": the hotspot " + hot.x
                + " is the middle of the fingertip, " + left + " to " + right);
            assertTrue(height(img) >= size * 0.7, what + ": the hand is " + height(img) + " tall in " + size);
        }
    }

    /**
     * Windows shows any pixel that is not fully clear as opaque, laid on black, so a soft outer edge would come out as
     * a dark fringe: every pixel must be fully clear or fully opaque, and the smoothness lives inside the outline.
     *
     * MUTATION: removing the cutEdge call in HandPointer.image fails the first assertion.
     */
    @Test
    public void testTheEdgeIsHardAndTheInsideSmooth()
    {
        for (int size : SIZES)
        {
            BufferedImage img = HandPointer.image(size);
            int between = 0;

            for (int y = 0; y < size; y++)
            {
                for (int x = 0; x < size; x++)
                {
                    int argb = img.getRGB(x, y), alpha = argb >>> 24;

                    assertTrue(alpha == 0 || alpha == 255, "at " + size + ": alpha " + alpha + " at "
                        + x + "," + y);
                    int gray = argb & 0xFF;

                    if (alpha == 255 && gray > 40 && gray < 215)
                    {
                        between++;
                    }
                }
            }
            assertTrue(between >= size / 2, "at " + size + ": only " + between
                + " antialiased pixels inside");
        }
    }

    /**
     * Java 8's own hand is a jagged 1-bit picture, so there the hand is TrainControl's; later Java's own is Windows'
     * hand at the user's size and color, and stays.  Either way the pointer is recognized as the hand.
     *
     * MUTATION: returning Java's own hand from HandPointer.cursor() on every Java fails the first assertion on Java 8.
     */
    @Test
    public void testJava8AloneGetsTheDrawnHand()
    {
        Cursor hand = HandPointer.cursor();

        assertEquals(hand.getType() == Cursor.CUSTOM_CURSOR, "1.8".equals(JAVA), "on Java " + JAVA
            + " the hand pointer is " + hand);
        assertSame(HandPointer.cursor(), hand, "each call makes a new pointer");
        assertTrue(HandPointer.isHand(hand), "the drawn hand is not recognized as the hand");
        assertFalse(HandPointer.isHand(Cursor.getDefaultCursor()), "the arrow is taken for a hand");
        assertFalse(HandPointer.isHand(null), "no pointer is taken for a hand");
    }

    /**
     * A site that sets Java's own hand shows the jagged one on Java 8, so there it must not pass for the hand; on later
     * Java the two are the same pointer.
     *
     * MUTATION: accepting any pointer of type HAND_CURSOR in HandPointer.isHand fails this on Java 8.
     */
    @Test
    public void testJavasOwnHandIsTheHandOnlyAfterJava8()
    {
        assertEquals(HandPointer.isHand(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)), !"1.8".equals(JAVA),
            "on Java " + JAVA + " Java's own hand is taken for the hand pointer, or the other way round");
    }

    private static boolean opaque(BufferedImage img, int x, int y)
    {
        return (img.getRGB(x, y) >>> 24) != 0;
    }

    private static int height(BufferedImage img)
    {
        int bottom = 0;

        for (int y = 0; y < img.getHeight(); y++)
        {
            for (int x = 0; x < img.getWidth(); x++)
            {
                if (opaque(img, x, y))
                {
                    bottom = y;
                }
            }
        }
        return bottom + 1;
    }
}
