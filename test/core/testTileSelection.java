package core;

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

    /**
     * A whole row is one rectangle, however wide the diagram is.
     *
     * The gesture this backs is the answer to a real question: a diagram may be sixty squares across,
     * and picking a row by clicking each square is not a feature.  Pinned here because the editor's
     * selectRow is a one-liner over this method, and a one-liner is exactly the kind of thing that
     * gets its bounds wrong by one and nobody notices until a row is short.
     */
    @Test
    public void testAWholeRowIsOneRectangle()
    {
        TileSelection selection = new TileSelection();

        // A row of a 60-wide diagram: columns 0..59 inclusive
        selection.addRectangle(0, 7, 59, 7);

        assertEquals(selection.size(), 60,
            "a row of a sixty-wide diagram must hold sixty squares - an off-by-one here is a row that "
            + "silently stops one short of the edge");

        assertTrue(selection.contains(0, 7), "the first column");
        assertTrue(selection.contains(59, 7), "and the last");
        assertFalse(selection.contains(0, 6), "and nothing in the row above");
        assertFalse(selection.contains(0, 8), "or below");
    }

    /**
     * Picking a second row adds to the first rather than replacing it.
     *
     * "Rows" was the question, plural.  Two calls have to give two rows.
     */
    @Test
    public void testRowsAccumulate()
    {
        TileSelection selection = new TileSelection();

        selection.addRectangle(0, 3, 9, 3);
        selection.addRectangle(0, 4, 9, 4);

        assertEquals(selection.size(), 20,
            "picking a second row replaced the first instead of adding to it");

        assertTrue(selection.contains(5, 3) && selection.contains(5, 4));
    }

    /**
     * Columns accumulate the same way rows do.
     *
     * Asked outright, so pinned outright: selectRow and selectColumn are both one line over
     * addRectangle, and "does the same convenience apply to columns" should be answerable by a test
     * rather than by reading the two methods and hoping they match.
     */
    @Test
    public void testColumnsAccumulateToo()
    {
        TileSelection selection = new TileSelection();

        // Two columns of a 12-row diagram, picked one after the other
        selection.addRectangle(3, 0, 3, 11);
        selection.addRectangle(7, 0, 7, 11);

        assertEquals(selection.size(), 24,
            "picking a second column replaced the first instead of adding to it");

        assertTrue(selection.contains(3, 0) && selection.contains(3, 11), "all of the first column");
        assertTrue(selection.contains(7, 0) && selection.contains(7, 11), "and all of the second");
        assertFalse(selection.contains(5, 5), "and nothing between them");
    }

    /**
     * A row and a column together give a cross, with the crossing square counted once.
     */
    @Test
    public void testARowAndAColumnCrossCleanly()
    {
        TileSelection selection = new TileSelection();

        selection.addRectangle(0, 5, 9, 5);
        selection.addRectangle(5, 0, 5, 9);

        assertEquals(selection.size(), 19,
            "ten plus ten less the one square they share - counting the crossing twice would apply a "
            + "group operation to it twice");
    }

    /**
     * A box that overlaps what is already picked does not double-count it.
     */
    @Test
    public void testOverlappingBoxesDoNotDoubleCount()
    {
        TileSelection selection = new TileSelection();

        selection.addRectangle(0, 0, 4, 4);
        selection.addRectangle(2, 2, 6, 6);

        // 25 + 25 - 9 shared
        assertEquals(selection.size(), 41,
            "the overlap was counted twice, which would make a group operation act on a square twice");
    }

    /**
     * The grip sits at the top right of what is picked.
     *
     * With picking switched on every drag draws a new box - that is what the mode is - so the
     * "start a drag on a picked square" gesture could not be reached without turning the mode off
     * first, which works and which nobody would guess.  One square of the selection is a grip
     * instead, and where it is has to be somewhere a user can predict.
     */
    @Test
    public void testTheGripIsTheTopRightCorner()
    {
        TileSelection selection = new TileSelection();

        selection.addRectangle(3, 7, 9, 11);

        assertEquals(selection.handle()[0], 9, "the grip is not at the right-hand edge");
        assertEquals(selection.handle()[1], 7, "nor at the top");
    }

    /**
     * And it does not care which corner the box was dragged out from.
     *
     * A rectangle dragged up and to the left is the same rectangle, so its grip is in the same place
     * - otherwise the control moves depending on how the selection happened to be made, which is the
     * one thing a grip must not do.
     */
    @Test
    public void testTheGripDoesNotDependOnHowTheBoxWasDrawn()
    {
        TileSelection forwards = new TileSelection();
        TileSelection backwards = new TileSelection();

        forwards.addRectangle(3, 7, 9, 11);
        backwards.addRectangle(9, 11, 3, 7);

        assertEquals(backwards.handle()[0], forwards.handle()[0]);
        assertEquals(backwards.handle()[1], forwards.handle()[1]);
    }

    /**
     * An L-shaped selection has an empty corner, and the grip goes there anyway.
     *
     * The corner of the BOX rather than the top right picked square: being able to grab the corner
     * of the rectangle you can see is worth more than the grip always sitting on something chosen,
     * and every square in the editor answers a drag whether or not it holds track.
     */
    @Test
    public void testTheGripIsTheCornerOfTheBoxNotOfTheSquares()
    {
        TileSelection selection = new TileSelection();

        selection.addRectangle(0, 0, 0, 4);
        selection.addRectangle(0, 4, 4, 4);

        assertFalse(selection.contains(4, 0), "the test needs a selection with an empty corner");

        assertEquals(selection.handle()[0], 4);
        assertEquals(selection.handle()[1], 0);
    }

    /**
     * Nothing picked is no grip, rather than a grip at nowhere.
     */
    @Test
    public void testNothingPickedHasNoGrip()
    {
        assertNull(new TileSelection().handle(),
            "a grip drawn with nothing picked would be a control that moves nothing");
    }
}
