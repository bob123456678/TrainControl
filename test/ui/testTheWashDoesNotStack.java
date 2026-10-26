package ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileAnnotation;
import org.traincontrol.automationui.TileOverlay;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * A square is faded once, however many reasons there are to fade it (OB-212: *"keep one level of opacity, dont
 * stack."*).
 *
 * Two washes, each right on its own: `LayoutLabel` draws the tile art at `BLOCKED_ALPHA` where the railway refuses the
 * square, and `TileAnnotation` lays a white wash at `DIM` under its arrows so thin arrows lift off busy art. Neither
 * knows about the other, so a square both blocked and annotated is faded twice. The annotation's wash is the one to
 * drop, since a tile drawn at 40% is not busy art competing with an arrow, while dropping the other would lose what the
 * fade says (the railway will not use this square).
 *
 * Painted rather than reasoned about: whether a wash lands is decided inside `paint`, and every model-level question
 * answers the same with the stacking present and absent (`support.Rendered`).
 *
 * MUTATION: paint the wash regardless of the flag and the second claim fails; skip it always and the first fails,
 * because an annotated tile that is NOT blocked loses the lift its arrows need.
 *
 * @author Adam
 */
public class testTheWashDoesNotStack
{
    /** A tile with one road across it, which is enough to make the annotation draw its wash */
    private static TileAnnotation annotated()
    {
        List<TileAnnotation.Mark> marks = new ArrayList<>();

        marks.add(new TileAnnotation.Mark(Side.W, Side.E, Direction.BOTH));

        return new TileAnnotation(marks, -1, false);
    }

    /** On an ordinary square the annotation still lays its wash, which is what lifts the arrows. */
    @Test
    public void testAnOrdinarySquareStillGetsTheWash() throws Exception
    {
        assertTrue(paintedOn(Color.BLACK, false) > 0,
            "the annotation laid no wash on an ordinary square, so nothing lifts its arrows off the"
            + " tile art beneath them - which is what the wash is for");
    }

    /** And on a square the railway has already faded, it does not - one level, not two. */
    @Test
    public void testAnAlreadyFadedSquareIsNotWashedAgain() throws Exception
    {
        assertEquals(paintedOn(Color.BLACK, true), 0,
            "the annotation washed a square that LayoutLabel had already drawn faint, so the two fades"
            + " compound and the square reads as further gone than either reason says. Adam, OB-212:"
            + " \"keep one level of opacity, dont stack\"");
    }

    /**
     * And the THIRD wash - track an active route is holding - gives way too (MT-375: *"I still see two levels of fade
     * when a train locks the same section a second time"*). `LayoutLabel` fades refused track, `TileAnnotation` washes
     * under its arrows and `TileOverlay` pales out held track, and the first fix addressed only the second; a class
     * that tests one source of a stacking bug reports clean about the other two.
     */
    @Test
    public void testHeldTrackIsNotWashedOverAFadedTile() throws Exception
    {
        assertTrue(heldWashOn(false) > 0,
            "an ordinary square carrying held track was not paled out at all, so nothing says a route"
            + " is holding it and the claim below would pass on any code");

        assertEquals(heldWashOn(true), 0,
            "a square the railway already draws faint was washed AGAIN because a route is holding it,"
            + " so the two fades compound. Adam, MT-375: \"I still see two levels of fade when a train"
            + " locks the same section a second time... you can see the difference in the tone of"
            + " 13,11 and 13,12\", and MT-373: \"greyed out means either edge locked or path"
            + " blocked\" - one grey, either reason");
    }

    /** How much lighter the HELD-track overlay made a black ground. */
    private static int heldWashOn(boolean alreadyFaded) throws Exception
    {
        final int size = 30;

        java.util.List<TileOverlay.Segment> segments = new ArrayList<>();

        segments.add(new TileOverlay.Segment(Side.W, Side.E, TileOverlay.State.LOCKED));

        TileOverlay held = new TileOverlay(TileOverlay.State.LOCKED, false, segments);

        BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, size, size);

            held.paint(g, size, size, null, alreadyFaded);
        }
        finally
        {
            g.dispose();
        }

        int lightened = 0;

        // The corners again: the run line is drawn along the rails, and the wash covers the square.
        for (int[] corner : new int[][] {{0, 0}, {size - 1, 0}, {0, size - 1}, {size - 1, size - 1}})
        {
            int rgb = shot.getRGB(corner[0], corner[1]);

            lightened += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
        }

        return lightened;
    }

    /**
     * How much lighter the annotation made a black ground, in total across the tile. The wash is white at partial
     * alpha, so it can only lighten; this measures the four BACKGROUND corners the marks never reach, not the whole
     * square.
     */
    private static int paintedOn(Color ground, boolean alreadyFaded) throws Exception
    {
        final int size = 30;

        BufferedImage shot = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = shot.createGraphics();

        try
        {
            g.setColor(ground);
            g.fillRect(0, 0, size, size);

            annotated().paint(g, size, size, alreadyFaded);
        }
        finally
        {
            g.dispose();
        }

        int lightened = 0;

        for (int[] corner : new int[][] {{0, 0}, {size - 1, 0}, {0, size - 1}, {size - 1, size - 1}})
        {
            int rgb = shot.getRGB(corner[0], corner[1]);

            lightened += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
        }

        return lightened;
    }
}
