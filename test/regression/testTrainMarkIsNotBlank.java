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

    /**
     * What a tool can click is outlined in the selection's orange at its edge, thicker than a selection, with no wash,
     * and the square clicked turns blue (OB-395).
     *
     * MUTATION: draw CLICKABLE with the blue frame or the selection's 2-pixel outline in
     * `TileAnnotation.paintToolBorder`, give it a wash in `paintToolFill`, or give PICKED no blue in `markColour`, and
     * this fails.
     */
    @Test
    public void testTheClickableSquaresAreOrangeAndTheClickedOneBlue()
    {
        java.awt.image.BufferedImage clickable = painted(TileAnnotation.ToolMark.CLICKABLE);

        assertEquals(clickable.getRGB(1, 15) & 0xFFFFFF, 0xFF8C00, "a square that can be clicked is not outlined in"
            + " orange at its edge");

        assertEquals(clickable.getRGB(2, 15) & 0xFFFFFF, 0xFF8C00,
            "the orange outline is no thicker than a selection's");

        assertEquals(clickable.getRGB(15, 15) & 0xFFFFFF, 0xFFFFFF, "a square that can be clicked is washed over");

        java.awt.Color middle = new java.awt.Color(painted(TileAnnotation.ToolMark.PICKED).getRGB(15, 15));

        assertTrue(middle.getBlue() > middle.getRed() + 20, "the square clicked is not washed in blue: " + middle);
    }

    /** A 30-pixel white square painted with nothing but this mark. */
    private java.awt.image.BufferedImage painted(TileAnnotation.ToolMark mark)
    {
        java.awt.image.BufferedImage tile = new java.awt.image.BufferedImage(30, 30,
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D g = tile.createGraphics();

        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 30, 30);

        plain().markedByATool(mark).paint(g, 30, 30);

        g.dispose();

        return tile;
    }

    /** An annotation with nothing on it at all. */
    private TileAnnotation plain()
    {
        return new TileAnnotation(Collections.<TileAnnotation.Mark>emptyList(), -1, false);
    }
}
