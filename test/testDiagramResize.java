import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;

/**
 * Growing a track diagram by one all round, and shrinking it back.
 *
 * The editor's "+" and "-".  These have to be exact mirrors of each other: a user who presses "+" and
 * then changes their mind presses "-", and what they get back has to be the diagram they had - same
 * size, same track, in the same squares.  A "-" that took a row from a different edge than "+" added
 * one to would move every tile on the diagram by one, quietly, and every stored coordinate with it.
 *
 * The other half is the refusal.  Shrinking removes the rightmost column and the top and bottom rows,
 * and if any of those hold track then making the diagram smaller means having less railway.  That has
 * to be refused rather than done, because nothing on screen would say what was lost.
 */
public class testDiagramResize
{
    /**
     * Grow then shrink is the diagram you started with.
     */
    @Test
    public void testGrowingAndShrinkingAreMirrors() throws Exception
    {
        LayoutDiagram diagram = new LayoutDiagram("test", 6, 6, null, null);

        // A recognisable tile, away from every edge so that neither operation touches it
        diagram.addComponent(new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.STRAIGHT, 2, 3, 0, 0, 12, 11,
            Accessory.accessoryDecoderType.MM2), 2, 3);

        // What the parser does after building one, and what every diagram in the application has had
        // done to it.  The constructor sets maxy to the row COUNT while checkBounds sets it to the
        // last row INDEX, so a diagram that has never been through checkBounds has a maxy one too
        // large - and shiftDown walks from maxy - 1 downward and writes one row below that.
        diagram.checkBounds();

        int wasX = diagram.getSx();
        int wasY = diagram.getSy();

        // "+": a column on the right and a row at the bottom.  NOT a row at the top - see growEdges:
        // everything autonomy knows about a page is keyed by square, so moving every tile down one
        // would leave every station, signal pairing and caption naming the wrong square.
        diagram.addRowsAndColumns(1, 1);

        assertEquals(diagram.getSx(), wasX + 1, "one column wider");
        assertEquals(diagram.getSy(), wasY + 1, "one row taller");

        assertNotNull(diagram.getComponent(2, 3),
            "growing must not MOVE anything - a tile that changed square would take every coordinate "
            + "stored about it out of step");

        assertTrue(diagram.edgesAreEmpty(),
            "the three edges just added are empty, so shrinking must be allowed");

        // "-": the same three away again
        diagram.trimEdges();

        assertEquals(diagram.getSx(), wasX, "back to the width it started at");
        assertEquals(diagram.getSy(), wasY, "and the height");

        assertNotNull(diagram.getComponent(2, 3),
            "the tile did not come back to the square it started on - so a grow and a shrink between "
            + "them moved every tile on the diagram, and every coordinate anything else stored about "
            + "them");

        assertEquals(diagram.getComponent(2, 3).getY(), 3,
            "the tile's own stored row changed, which nothing about growing or shrinking should do");
    }

    /**
     * Track on an edge means the diagram cannot shrink.
     */
    @Test
    public void testShrinkingIsRefusedWhenAnEdgeHoldsTrack() throws Exception
    {
        LayoutDiagram diagram = new LayoutDiagram("test", 6, 6, null, null);

        diagram.addComponent(new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.STRAIGHT, 5, 2, 0, 0, 12, 11,
            Accessory.accessoryDecoderType.MM2), 5, 2);

        assertFalse(diagram.edgesAreEmpty(),
            "the rightmost column holds track, so the diagram must not be shrinkable");

        int wasX = diagram.getSx();

        diagram.trimEdges();

        assertEquals(diagram.getSx(), wasX,
            "the diagram shrank anyway, which took a piece of railway off the right-hand edge with "
            + "nothing on screen saying so");

        assertNotNull(diagram.getComponent(5, 2), "and the track is still there");
    }

    /**
     * A row on the bottom edge stops it too, not only the right-hand column.
     */
    @Test
    public void testTheBottomRowCountsAsAnEdge() throws Exception
    {
        LayoutDiagram diagram = new LayoutDiagram("test", 6, 6, null, null);

        diagram.addComponent(new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.STRAIGHT, 2, 5, 0, 0, 12, 11,
            Accessory.accessoryDecoderType.MM2), 2, 5);

        assertFalse(diagram.edgesAreEmpty(),
            "checking only the right-hand column would let the bottom row be thrown away");
    }

    /**
     * The TOP row does not stop it, because a shrink no longer touches the top row.
     *
     * Worth pinning rather than leaving implicit.  The first version of this took a row off the top as
     * well, which moved every remaining square up by one - and everything autonomy knows about a page
     * is keyed by square, so every station, signal pairing, arrival restriction and caption would have
     * been left naming the square below the one it meant.  Growing and shrinking now happen only at
     * the far edges, where nothing moves.
     */
    @Test
    public void testTheTopRowIsNotAnEdge() throws Exception
    {
        LayoutDiagram diagram = new LayoutDiagram("test", 6, 6, null, null);

        diagram.addComponent(new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.STRAIGHT, 2, 0, 0, 0, 12, 11,
            Accessory.accessoryDecoderType.MM2), 2, 0);

        assertTrue(diagram.edgesAreEmpty(),
            "track on the TOP row must not block a shrink, because a shrink takes nothing from the "
            + "top - if this ever fails again, check that trimEdges has not gone back to moving "
            + "squares");
    }

    /**
     * A diagram with a single column or a single row cannot shrink to nothing.
     */
    @Test
    public void testATinyDiagramCannotShrink() throws Exception
    {
        assertFalse(new LayoutDiagram("test", 1, 4, null, null).edgesAreEmpty(),
            "a one-column diagram must refuse, or the shrink removes the only column it has");

        assertFalse(new LayoutDiagram("test", 4, 1, null, null).edgesAreEmpty(),
            "and a one-row diagram must refuse for the same reason");
    }
}
