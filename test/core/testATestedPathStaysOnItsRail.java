package core;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * A tested path's line stays on its rail, however many times the path crosses the square.
 *
 * Adam, OB-216, 2026-09-13: *"When running Test a path from BottomMainB to BottomMainC, the orange lines
 * over the switch at 12,13 are misaligned.  A small offset as on the other tiles is OK."*
 *
 * **What the square was given.**  On his railway the route out from BottomMainB leaves west straight
 * through the switch at 12,13, runs the whole loop, and comes back in from the west to take the
 * diverging leg down to BottomMainC; the route back does the same the other way round.  So that one
 * square carries four segments - E to W and W to S going out, S to W and W to E coming back - where an
 * ordinary square on the route carries two, one each way.
 *
 * **Why it drifted.**  Each segment after the first was pushed sideways by a ninth of the tile times its
 * position in the list, so the fourth - W to E, the straight rail again - went two and a half ninths off
 * its rail, which is the line Adam saw hanging below the track.  An ordinary square's second segment
 * goes half a ninth, which is the small offset he is content with.
 *
 * **Read off the painted pixels.**  Only the eastern quarter of the square is examined, which the
 * diverging leg - drawn from the west edge to the south edge - never reaches, so everything there in a
 * line colour belongs to the straight rail and must lie on it.
 *
 * @author Adam
 */
public class testATestedPathStaysOnItsRail
{
    private static final int SIZE = 60;

    /** The line colours: out, back.  Chevrons are drawn in a third, and are not the line. */
    private static final int[] LINE = {0xFFD600, 0xFF9628};

    /**
     * Four passes over a switch keep the straight rail's line as close to it as an ordinary square's.
     */
    @Test
    public void testASwitchCrossedFourTimesKeepsItsLineOnTheRail()
    {
        List<TileAnnotation.Trace> atTheSwitch = Arrays.asList(
            new TileAnnotation.Trace(Side.E, Side.W, true),
            new TileAnnotation.Trace(Side.W, Side.S, true),
            new TileAnnotation.Trace(Side.S, Side.W, false),
            new TileAnnotation.Trace(Side.W, Side.E, false));

        int furthest = furthestFromTheRail(atTheSwitch);

        int ordinary = furthestFromTheRail(Arrays.asList(
            new TileAnnotation.Trace(Side.E, Side.W, true),
            new TileAnnotation.Trace(Side.W, Side.E, false)));

        assertTrue(ordinary >= 0, "precondition: nothing was painted on the ordinary square");
        assertTrue(furthest >= 0, "precondition: nothing was painted on the switch's straight rail");

        assertTrue(furthest <= ordinary,
            "the straight rail through a switch the tested path crosses four times was drawn " + furthest
            + "px from the rail, and an ordinary square's two passes only " + ordinary + "px. Adam,"
            + " OB-216: \"the orange lines over the switch at 12,13 are misaligned. A small offset as on"
            + " the other tiles is OK\"");
    }

    /**
     * The control: an ordinary square's offset is the small one Adam accepted, not something larger.
     *
     * Without this, a painter that pushed EVERY square's second pass far off its rail would satisfy the
     * claim above by making the comparison meaningless.
     */
    @Test
    public void testAnOrdinarySquaresOffsetIsSmall()
    {
        int ordinary = furthestFromTheRail(Arrays.asList(
            new TileAnnotation.Trace(Side.E, Side.W, true),
            new TileAnnotation.Trace(Side.W, Side.E, false)));

        // Half the stroke (a seventh of the tile), plus the half-ninth offset, plus a pixel of rim.
        int bound = (int) Math.ceil(SIZE / 7.0 / 2 + SIZE / 18.0 + 1);

        assertTrue(ordinary >= 0 && ordinary <= bound,
            "an ordinary square's line reaches " + ordinary + "px from its rail; the small offset is at"
            + " most " + bound);
    }

    /**
     * Paints one square and measures how far from the rail's centre line any line-coloured pixel in its
     * eastern quarter lies.
     *
     * @return the largest distance in pixels, or -1 when nothing line-coloured was painted there
     */
    private static int furthestFromTheRail(List<TileAnnotation.Trace> traces)
    {
        TileAnnotation annotation = new TileAnnotation(null, -1, false, null, false, false, false,
            traces);

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();

        try
        {
            annotation.paint(g, SIZE, SIZE);
        }
        finally
        {
            g.dispose();
        }

        int furthest = -1;

        for (int x = SIZE * 3 / 4; x < SIZE; x++)
        {
            for (int y = 0; y < SIZE; y++)
            {
                int argb = image.getRGB(x, y);

                if (((argb >>> 24) & 0xFF) < 250) continue;

                int rgb = argb & 0xFFFFFF;

                if (rgb != LINE[0] && rgb != LINE[1]) continue;

                furthest = Math.max(furthest, Math.abs(y - SIZE / 2));
            }
        }

        return furthest;
    }
}
