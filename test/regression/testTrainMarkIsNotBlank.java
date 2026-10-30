package regression;

import java.util.Collections;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automationui.TileAnnotation;

/**
 * A square whose only annotation is "a train is standing here" is not a blank square (OB-007: "add a small white * on
 * top of the station icons to show there is a train there").  `paint()` opens with `if (isBlank()) return;` and
 * `isBlank()` lists every field that counts as content except `occupied`, so an annotation carrying nothing but a train
 * was blank and the star was never painted.  The field was added to equals and hashCode and missed here; it only bit
 * where the train was the ONLY thing to say about a square.
 *
 * @author Adam
 */
public class testTrainMarkIsNotBlank
{
    /** The whole bug. */
    @Test
    public void testATrainIsWorthDrawing()
    {
        TileAnnotation nothing = plain();

        assertTrue(nothing.isBlank(),
            "an annotation with nothing on it should be blank - if this fails the test below proves "
            + "nothing, because everything is unblank");

        assertFalse(plain().withTrain().isBlank(),
            "a square with a train on it and nothing else was treated as blank, so paint() returned "
            + "before it could draw the train mark");
    }

    /**
     * And withTrain is what the editor actually calls: it returns `this`, so a call site that discards the return value
     * keeps working.
     */
    @Test
    public void testWithTrainMarksTheAnnotationItWasCalledOn()
    {
        TileAnnotation annotation = plain();

        TileAnnotation returned = annotation.withTrain();

        assertSame(returned, annotation,
            "withTrain returns a copy now, so the editor - which discards the return value - has "
            + "stopped marking trains at all");

        assertFalse(annotation.isBlank(), "the annotation it was called on was not marked");
    }

    /**
     * A train and a badge together are still not blank, and neither is a train on an ignored square: `ignored` returns
     * early in paint() before the train mark, deliberately, but the annotation must not be called blank or the graying
     * is lost.
     */
    @Test
    public void testTheOtherCombinationsAreStillDrawn()
    {
        assertFalse(plain().withTrain().isBlank(), "a train");

        TileAnnotation ignored = new TileAnnotation(
            Collections.<TileAnnotation.Mark>emptyList(), -1, false, null, true);

        assertFalse(ignored.isBlank(), "an ignored square is drawn - it is greyed and hatched");

        assertFalse(ignored.withTrain().isBlank(), "an ignored square with a train is still drawn");
    }

    /**
     * A tool's mark on a square (FR-159) is drawn when it is all there is, and each mark is told apart from the
     * others and from none, as `LayoutLabel` asks `equals` whether a redraw is needed.
     *
     * MUTATION: leave `toolMark` out of `TileAnnotation.isBlank`, or out of `equals`, or its border out of
     * `paintArrowsAgain`, and this fails.
     */
    @Test
    public void testAToolMarkIsDrawn()
    {
        for (TileAnnotation.ToolMark mark : TileAnnotation.ToolMark.values())
        {
            TileAnnotation marked = plain().markedByATool(mark);

            assertFalse(marked.isBlank(), "a square marked " + mark + " and nothing else was treated as blank");

            assertNotEquals(marked, plain(), "a square marked " + mark + " equals an unmarked one, so it is never"
                + " redrawn");

            for (TileAnnotation.ToolMark other : TileAnnotation.ToolMark.values())
            {
                if (other != mark)
                {
                    assertNotEquals(marked, plain().markedByATool(other), mark + " equals " + other);
                }
            }

            java.awt.image.BufferedImage tile = new java.awt.image.BufferedImage(30, 30,
                java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g = tile.createGraphics();

            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 30, 30);

            marked.paint(g, 30, 30);

            g.dispose();

            int inked = 0;

            for (int x = 0; x < 30; x++)
            {
                for (int y = 0; y < 30; y++)
                {
                    if ((tile.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) inked++;
                }
            }

            assertTrue(inked > 0, "a square marked " + mark + " painted nothing");

            // And over a neighbor's curved station spilling onto it
            tile = new java.awt.image.BufferedImage(30, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
            g = tile.createGraphics();

            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, 30, 30);

            marked.paintArrowsAgain(g, 30, 30);

            g.dispose();

            assertTrue(tile.getRGB(1, 15) != java.awt.Color.WHITE.getRGB(), "a square marked " + mark + " has no border"
                + " drawn back over a neighbor's spill");
        }
    }

    /** An annotation with nothing on it at all. */
    private TileAnnotation plain()
    {
        return new TileAnnotation(Collections.<TileAnnotation.Mark>emptyList(), -1, false);
    }
}
