import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.TileSelection;

/**
 * The arithmetic behind selecting and dragging a group of squares in the diagram editor.
 *
 * Tested apart from the editor because this is the half that goes wrong quietly.  A gesture that
 * selects the wrong square is obvious the moment it happens; a drag that pushes two squares off the
 * edge of the diagram, or a copy that takes a shape with holes in it, is not noticed until somebody
 * looks for track that is no longer there.
 */
public class testTileSelection
{
    /**
     * Picking is a toggle, so the same gesture corrects itself.
     */
    @Test
    public void testPickingTwiceUnpicks()
    {
        TileSelection selection = new TileSelection();

        selection.toggle(3, 4);

        assertTrue(selection.contains(3, 4));
        assertEquals(selection.size(), 1);

        selection.toggle(3, 4);

        assertFalse(selection.contains(3, 4),
            "shift-clicking a square already picked must unpick it - otherwise one square too many "
            + "means starting again");

        assertTrue(selection.isEmpty());
    }

    /**
     * Escape lets everything go.
     */
    @Test
    public void testClearLetsEverythingGo()
    {
        TileSelection selection = new TileSelection();

        selection.addRectangle(0, 0, 3, 3);

        assertEquals(selection.size(), 16);

        selection.clear();

        assertTrue(selection.isEmpty(), "Escape must leave nothing picked");
    }

    /**
     * A box drags in whichever direction suits, and selects the same squares either way.
     */
    @Test
    public void testABoxSelectsTheSameEitherWayRound()
    {
        TileSelection forwards = new TileSelection();
        forwards.addRectangle(2, 2, 4, 5);

        TileSelection backwards = new TileSelection();
        backwards.addRectangle(4, 5, 2, 2);

        assertEquals(backwards.size(), forwards.size(),
            "a box dragged up and to the left must select what the same box dragged down and to the "
            + "right does");

        for (TileSelection.At at : forwards.all())
        {
            assertTrue(backwards.contains(at.getX(), at.getY()));
        }
    }

    /**
     * Moving reports where the squares would land, without moving them.
     */
    @Test
    public void testMovingIsAskedBeforeItIsDone()
    {
        TileSelection selection = new TileSelection();

        selection.add(1, 1);
        selection.add(2, 1);

        List<TileSelection.At> moved = selection.movedBy(3, 2);

        assertEquals(moved.size(), 2);

        assertTrue(selection.contains(1, 1),
            "asking where a move would land must not perform it - the caller has to be able to refuse");

        boolean found = false;

        for (TileSelection.At at : moved)
        {
            if (at.getX() == 4 && at.getY() == 3) found = true;
        }

        assertTrue(found, "1,1 moved by 3,2 should land at 4,3");
    }

    /**
     * A group dragged past the edge is refused WHOLE, rather than clipped.
     *
     * Clipping would silently drop track off the side of the diagram: the user drags a yard two squares
     * left, one column of it ceases to exist, and nothing says so.
     */
    @Test
    public void testAGroupThatWouldLeaveTheDiagramIsRefused()
    {
        TileSelection selection = new TileSelection();

        selection.add(0, 5);
        selection.add(1, 5);

        assertTrue(selection.fitsAfterMove(1, 0, 10, 10), "one to the right is inside a 10x10 diagram");

        assertFalse(selection.fitsAfterMove(-1, 0, 10, 10),
            "one to the LEFT puts the square at column 0 outside the diagram, so the whole move must "
            + "be refused rather than dropping that square");

        assertFalse(selection.fitsAfterMove(0, 5, 10, 10),
            "and five down runs off the bottom");
    }

    /**
     * A copy takes the bounding box, holes included.
     *
     * Copying only the picked squares would paste a shape with gaps in it, and a piece of railway with
     * gaps is not the piece the user pointed at.
     */
    @Test
    public void testTheBoundsCoverTheWholeShapeIncludingGaps()
    {
        TileSelection selection = new TileSelection();

        selection.add(2, 3);
        selection.add(5, 7);

        int[] bounds = selection.bounds();

        assertNotNull(bounds);
        assertEquals(bounds[0], 2, "left");
        assertEquals(bounds[1], 3, "top");
        assertEquals(bounds[2], 5, "right");
        assertEquals(bounds[3], 7, "bottom");
    }

    /**
     * Nothing picked has no bounds, rather than a rectangle at the origin.
     */
    @Test
    public void testNothingPickedHasNoBounds()
    {
        assertNull(new TileSelection().bounds(),
            "an empty selection must not report a rectangle - a caller that pasted it would write at "
            + "0,0 having been told nothing was chosen");
    }
}
