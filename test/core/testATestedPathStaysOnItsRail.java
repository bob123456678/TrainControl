package core;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TilePorts.Side;
import support.ProgramConstants;

/**
 * A tested path's line stays on its rail, however many times the path crosses the square.
 *
 * Adam, OB-216: *"When running Test a path from BottomMainB to BottomMainC, the orange lines over the switch at 12,13
 * are misaligned.  A small offset as on the other tiles is OK."*
 *
 * On his railway the route out from BottomMainB leaves west straight through the switch at 12,13, runs the whole loop,
 * and comes back in from the west to take the diverging leg down to BottomMainC, and the route back does the same the
 * other way round, so that one square carries four segments (E to W and W to S going out, S to W and W to E coming
 * back) where an ordinary square carries two.  Each segment after the first was pushed sideways by a ninth of the tile
 * times its position in the list, so the fourth, the straight rail again, went two and a half ninths off its rail,
 * where an ordinary square's second segment goes half a ninth, the small offset he is content with.
 *
 * Read off the painted pixels: only the eastern quarter of the square is examined, which the diverging leg (west edge
 * to south edge) never reaches, so everything there in a line color belongs to the straight rail and must lie on it.
 *
 * @author Adam
 */
public class testATestedPathStaysOnItsRail
{
    private static final int SIZE = 60;

    /** The line colors: out, back.  Chevrons are drawn in a third, and are not the line. */
    private static final int[] LINE =
    {
        ProgramConstants.colour(TileAnnotation.class, "TRACE").getRGB() & 0xFFFFFF,
        ProgramConstants.colour(TileAnnotation.class, "TRACE_RETURN").getRGB() & 0xFFFFFF
    };

    /** Four passes over a switch keep the straight rail's line as close to it as an ordinary square's. */
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
     * The control: an ordinary square's offset is the small one Adam accepted, not something larger; otherwise a
     * painter that pushed EVERY square's second pass far off its rail would satisfy the claim above by making the
     * comparison meaningless.
     */
    @Test
    public void testAnOrdinarySquaresOffsetIsSmall()
    {
        int ordinary = furthestFromTheRail(Arrays.asList(
            new TileAnnotation.Trace(Side.E, Side.W, true),
            new TileAnnotation.Trace(Side.W, Side.E, false)));

        // Half the stroke, plus the offset a second pass over the same rail is moved aside by (OB-216: "a small offset
        // as on the other tiles"), plus a pixel of rim; both are the painter's own.
        double strokeParts = ProgramConstants.real(TileAnnotation.class, "TRACE_STROKE_PARTS");
        double nudgeParts = ProgramConstants.real(TileAnnotation.class, "TRACE_NUDGE_PARTS");

        int bound = (int) Math.ceil(SIZE / strokeParts / 2 + SIZE / nudgeParts + 1);

        assertTrue(ordinary >= 0 && ordinary <= bound,
            "an ordinary square's line reaches " + ordinary + "px from its rail; the small offset is at"
            + " most " + bound);
    }

    /**
     * Paints one square and measures how far from the rail's center line any line-colored pixel in its eastern quarter
     * lies; -1 when nothing line-colored was painted there.
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
