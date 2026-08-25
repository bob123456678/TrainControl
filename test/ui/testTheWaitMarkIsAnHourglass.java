package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.AutonomyEditorPanel;
import org.traincontrol.gui.LoadingSpinner;

/**
 * What the "still drawing" mark actually draws, and where the signal window actually goes.
 *
 * Two things Adam reported by looking at them (FR-024 and OB-107), so they are checked here by looking
 * at them too - the drawing is rendered to an image and the image is measured, and the placement is a
 * function of two rectangles that can simply be called.
 *
 * **Why this is not a source-reading guard.** The obvious way to hold FR-024 would be to assert that
 * the paint method mentions an hourglass, and the obvious way to hold OB-107 would be to assert that
 * the dialog code no longer says setLocationRelativeTo. Neither would have caught the two things that
 * actually went wrong while this was being built: a silhouette that came out as a bow tie, and a
 * second attempt that came out as a rectangle with an X in it. Both of those mention nothing and read
 * perfectly well. Only the pixels knew.
 *
 * The load-bearing assertion is the sand count: sand is conserved, so what leaves the upper bulb has
 * to arrive in the lower one, and that is a statement about the drawing which the drawing code does
 * not itself make anywhere.
 *
 * Its limit is written out on the method, because it is narrower than it first looks and a later
 * reader should not have to rediscover that.
 *
 * @author Adam
 */
public class testTheWaitMarkIsAnHourglass
{
    /** Big enough that a few pixels of antialiasing either way cannot change a count materially. */
    private static final int SIZE = 240;

    /**
     * Paints the mark at a given frame of its cycle, offscreen.
     *
     * @param frames how many frames to advance before painting
     * @return the picture, on white
     */
    private BufferedImage shoot(int frames) throws Exception
    {
        LoadingSpinner mark = new LoadingSpinner();
        mark.setSize(SIZE, SIZE);

        for (int i = 0; i < frames; i++)
        {
            mark.advanceOneFrame();
        }

        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, SIZE, SIZE);
        mark.paint(g);
        g.dispose();

        return out;
    }

    /**
     * Counts pixels that are neither white nor nearly white, above and below the middle.
     *
     * @param shot a picture from {@link #shoot}
     * @param top true to count the upper half, false for the lower half
     * @return how many pixels carry ink
     */
    private int ink(BufferedImage shot, boolean top)
    {
        int from = top ? 0 : SIZE / 2;
        int to = top ? SIZE / 2 : SIZE;

        int count = 0;

        for (int y = from; y < to; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                int rgb = shot.getRGB(x, y);

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Grey of any darkness, which is all this draws.  The threshold is well clear of the
                // antialiased edge between the sand and the white behind it.
                if (r < 230 && g < 230 && b < 230) count++;
            }
        }

        return count;
    }

    /**
     * The mark draws something, and it is grey rather than the black the turning arc used.
     *
     * The weakest assertion here, and it is only worth having because a drawing method that throws or
     * that draws nothing is a real failure mode for a component nobody looks at in a test.
     */
    @Test
    public void testItDrawsSomethingGrey() throws Exception
    {
        BufferedImage shot = shoot(0);

        int drawn = ink(shot, true) + ink(shot, false);

        assertTrue(drawn > 500, "the wait mark drew almost nothing: " + drawn + " pixels");

        // Not black.  Adam asked for grey specifically, and the arc this replaced drew in black with
        // alpha, which composites to grey against white - so a colour check has to look at the
        // DARKEST pixel rather than the average, or the two are indistinguishable.
        int darkest = 255;

        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                darkest = Math.min(darkest, shot.getRGB(x, y) & 0xFF);
            }
        }

        assertTrue(darkest > 80, "the wait mark is drawing too dark to be called grey: " + darkest);
    }

    /**
     * Sand is conserved: what drains out of the top arrives in the bottom.
     *
     * The assertion this class exists for. Every other check here would still pass if the two bulbs
     * were driven by unrelated formulae - the picture would still be an hourglass, still be grey,
     * still change frame to frame, and the sand would simply appear and vanish as it fell.
     *
     * The band is a twelfth, chosen from measurement rather than taste. Over a real cycle the count
     * runs 7176, 7504, 7479, 7455, 7417, 7382, 7399, 7338, 7213 - a spread of 4.4%, which is the
     * curved outline and the falling stream being counted as ink alongside the sand. Driving the lower
     * bulb from its own level instead of the shared one takes the spread to 20%.
     *
     * MUTATION: replacing the lower bulb's rectangle with one on a level of its own - `bulbH * (1.0 -
     * drained * 0.5)` for `bulbH * remaining` - fails this test. Measured: the count falls away to
     * 5984 by the end of the cycle instead of holding near 7200.
     *
     * WHAT THIS DOES NOT COVER, recorded so nobody assumes otherwise: the square root in `remaining`.
     * Both bulbs read the same variable, so whatever curve it follows the areas still sum to the same
     * constant - replacing the root with the plain fraction passes all seven tests here. The root is
     * how the sand LOOKS as it falls, and nothing in this battery can see that.
     */
    @Test
    public void testTheSandIsConserved() throws Exception
    {
        int[] frames = {0, 12, 25, 37, 49};

        int[] totals = new int[frames.length];

        for (int i = 0; i < frames.length; i++)
        {
            BufferedImage shot = shoot(frames[i]);

            totals[i] = ink(shot, true) + ink(shot, false);
        }

        int smallest = Integer.MAX_VALUE;
        int largest = 0;

        for (int total : totals)
        {
            smallest = Math.min(smallest, total);
            largest = Math.max(largest, total);
        }

        assertTrue(largest - smallest < largest / 12,
            "the amount of sand changes as it falls, from " + smallest + " to " + largest
            + " - the two bulbs are not agreeing about the level");
    }

    /**
     * It starts full at the top and ends full at the bottom, which is the direction sand falls in.
     *
     * A drawing that ran the animation backwards, or that filled both bulbs at once, would satisfy the
     * conservation check above and fail this one.
     */
    @Test
    public void testItRunsDownwards() throws Exception
    {
        BufferedImage full = shoot(0);
        BufferedImage empty = shoot(49);

        assertTrue(ink(full, true) > ink(full, false),
            "at the start of the cycle the sand should be in the upper bulb");

        assertTrue(ink(empty, false) > ink(empty, true),
            "by the end of the cycle the sand should have fallen to the lower bulb");
    }

    /**
     * The picture changes from frame to frame, so the animation is animation and not a still.
     *
     * Adam asked for it to move: "animate if possible."
     */
    @Test
    public void testItMoves() throws Exception
    {
        BufferedImage first = shoot(0);
        BufferedImage later = shoot(20);

        int different = 0;

        for (int y = 0; y < SIZE; y++)
        {
            for (int x = 0; x < SIZE; x++)
            {
                if (first.getRGB(x, y) != later.getRGB(x, y)) different++;
            }
        }

        assertTrue(different > 200, "the wait mark is not moving: " + different + " pixels differ");
    }

    /**
     * Nothing is drawn outside the component, at any point in the cycle including the turn.
     *
     * The turn is the frame that can break this: the glass is rotated about the centre, so a shape
     * sized to fill the space upright sweeps outside it halfway round. Checking a border of pixels
     * says the drawing left itself room to turn in.
     */
    @Test
    public void testItStaysInsideItsBounds() throws Exception
    {
        for (int frame = 0; frame < 62; frame += 3)
        {
            BufferedImage shot = shoot(frame);

            for (int at = 0; at < SIZE; at++)
            {
                assertEquals(shot.getRGB(at, 0), Color.WHITE.getRGB(),
                    "frame " + frame + " drew on the top edge at " + at);

                assertEquals(shot.getRGB(at, SIZE - 1), Color.WHITE.getRGB(),
                    "frame " + frame + " drew on the bottom edge at " + at);

                assertEquals(shot.getRGB(0, at), Color.WHITE.getRGB(),
                    "frame " + frame + " drew on the left edge at " + at);

                assertEquals(shot.getRGB(SIZE - 1, at), Color.WHITE.getRGB(),
                    "frame " + frame + " drew on the right edge at " + at);
            }
        }
    }

    /**
     * The signal window does not open over the middle of the diagram it is describing (OB-107).
     *
     * Adam: "the signal protecting this station pops up over the middle of the diagram. see if you can
     * offset it." The window's own text tells the reader the signals are outlined on the diagram
     * BEHIND it, so centring hides the thing it is pointing at.
     *
     * MUTATION: making besideOwner return the centred point - the behaviour this replaced - fails
     * this test.
     */
    @Test
    public void testTheSignalWindowDoesNotSitOnTheDiagram()
    {
        Rectangle window = new Rectangle(100, 80, 1400, 900);
        Dimension dialog = new Dimension(420, 300);

        Point at = AutonomyEditorPanel.besideOwner(window, dialog);

        int centredX = window.x + (window.width - dialog.width) / 2;
        int centredY = window.y + (window.height - dialog.height) / 2;

        assertTrue(Math.abs(at.x - centredX) > dialog.width / 2
            || Math.abs(at.y - centredY) > dialog.height / 2,
            "the dialog is still effectively centred, at " + at);

        // And it is still ON the window it belongs to, which is what stops "not centred" being
        // answered by throwing it into a corner of the desktop.
        assertTrue(window.contains(new Rectangle(at.x, at.y, dialog.width, dialog.height)),
            "the dialog landed outside the window it belongs to, at " + at);
    }

    /**
     * A dialog bigger than the window it belongs to still starts on-screen.
     *
     * The clamp has two ends and only one of them is exercised by the ordinary case. Pushed by the
     * "keep it inside" rule alone, a dialog wider than its parent would be moved to a NEGATIVE offset
     * from the parent's left edge - which on a maximised window is off the side of the display, where
     * the buttons cannot be reached.
     */
    @Test
    public void testAnOversizedDialogStillStartsOnScreen()
    {
        Rectangle window = new Rectangle(60, 40, 300, 200);
        Dimension dialog = new Dimension(500, 400);

        Point at = AutonomyEditorPanel.besideOwner(window, dialog);

        assertTrue(at.x >= window.x, "the dialog starts left of its window, at x=" + at.x);
        assertTrue(at.y >= window.y, "the dialog starts above its window, at y=" + at.y);
    }
}
