package core;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import static org.traincontrol.base.Accessory.accessoryDecoderType;
import static org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * Growing a track diagram by one all round, and shrinking it back.
 *
 * The editor's "+" and "-".  These have to be exact mirrors of each other: a user who presses "+" and
 * then changes their mind presses "-", and what they get back has to be the diagram they had - same
 * size, same track, in the same squares.  A "-" that took a row from a different edge than "+" added
 * one to would move every tile on the diagram by one, quietly, and every stored coordinate with it.
 *
 * The other half is the refusal.  Shrinking removes the rightmost column and the bottom row, and if
 * either holds track then making the diagram smaller means having less railway.  That has to be
 * refused rather than done, because nothing on screen would say what was lost.
 *
 * The TOP row is deliberately not touched - see testTheTopRowIsNotAnEdge, and growEdges for why.
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

    /**
     * A page with nothing on it has bounds a grid can be built from.
     *
     * UR-8, from the uninformed review. `checkBounds` seeds `minx = sx` and `maxx = 0` outside edit
     * mode and only ever lowers `minx` for a square that HOLDS something, so a page with no components
     * keeps both seeds. `LayoutGrid` then computes `maxx - minx + 1 + 1`, which on a thirty-wide page is
     * -28, and `new LayoutLabel[width][height]` throws NegativeArraySizeException.
     *
     * One component anywhere is enough to make it right, because the `x < minx` branch pulls the seed
     * down to it - so this is the empty page and nothing else. `LayoutEditor.clear()` empties a page and
     * `saveChanges` has no emptiness guard, and closing the editor calls `setEdit(false)`, which is the
     * transition that turns the seeds back on.
     *
     * The consequence is worse than one exception: the grid is registered in LayoutGrid's static LIVE
     * map before it finishes building, so a constructor that throws leaves a half-built grid registered
     * against that panel. The track diagram tab goes blank and stays blank.
     *
     * Each axis on its own, because they fail separately: components along one row leave `miny` seeded
     * while `minx` was pulled down.
     */
    @Test
    public void testAnEmptyPageHasBoundsAGridCanBeBuiltFrom()
    {
        LayoutDiagram empty = new LayoutDiagram("blank", 30, 10, null, null);

        empty.setEdit(false);
        empty.checkBounds();

        assertTrue(empty.getMaxx() - empty.getMinx() + 1 > 0,
            "an empty page has a negative width - " + empty.getMinx() + ".." + empty.getMaxx()
            + " - and LayoutGrid builds its array from exactly this, so the diagram tab throws "
            + "NegativeArraySizeException and stays blank (UR-8)");

        assertTrue(empty.getMaxy() - empty.getMiny() + 1 > 0,
            "an empty page has a negative height - " + empty.getMiny() + ".." + empty.getMaxy());
    }

    /**
     * A shift moves what this build cannot draw along with the track, by the rule it moves a tile by (GSP-C2).
     *
     * An element whose type TrainControl does not know is kept as the file had it and written back on save - id and
     * all, and its id IS its square, x + (y << 8).  The four shifts moved every tile and left these on their old ids, so
     * the next save put scenery the Central Station draws onto squares the track had moved away from.
     *
     * One element at 3,2 and one shift at column or row 1: right and down move it one on, left and up one back.  And one
     * ON the column or row a left or up shift takes out stays where it is, on what moved into its place, rather than
     * being deleted unseen.
     *
     * MUTATION: leave the elements out of `shiftRight`, and this fails on its first assertion.
     *
     * @throws Exception from the export
     */
    @Test
    public void testAShiftMovesTheElementsItCannotDraw() throws Exception
    {
        assertEquals(idAfter("right", 3, 2), "0x204", "shiftRight(1) moved the track and left an element this build"
            + " cannot draw on its old square (GSP-C2)");

        assertEquals(idAfter("down", 3, 2), "0x303", "shiftDown(1) left an element this build cannot draw on its old"
            + " square (GSP-C2)");

        assertEquals(idAfter("left", 3, 2), "0x202", "shiftLeft(1) left an element this build cannot draw on its old"
            + " square (GSP-C2)");

        assertEquals(idAfter("up", 3, 2), "0x103", "shiftUp(1) left an element this build cannot draw on its old square"
            + " (GSP-C2)");

        assertEquals(idAfter("left", 1, 2), "0x201", "an element on the column shiftLeft(1) takes out was moved or dropped"
            + " rather than kept where it is (GSP-C2)");

        assertEquals(idAfter("up", 3, 1), "0x103", "an element on the row shiftUp(1) takes out was moved or dropped"
            + " rather than kept where it is (GSP-C2)");
    }

    /**
     * The id one element this build cannot draw is written back with, after one shift at column or row 1 of a page
     * carrying a straight at 3,3 (GSP-C2).
     *
     * @param shift "right", "down", "left" or "up"
     * @param x the element's column before the shift
     * @param y its row
     * @return the id the export writes for it, or null when it is written with none
     * @throws Exception from the shift or the export
     */
    private static String idAfter(String shift, int x, int y) throws Exception
    {
        LayoutDiagram page = new LayoutDiagram("test", 6, 6, null, null);

        page.addComponent(new LayoutDiagramComponent(
            LayoutDiagramComponent.componentType.STRAIGHT, 3, 3, 0, 0, 12, 11,
            Accessory.accessoryDecoderType.MM2), 3, 3);

        // As testGrowingAndShrinkingAreMirrors says: the constructor's bounds are one too large until this has run
        page.checkBounds();

        java.util.Map<String, String> element = new java.util.LinkedHashMap<>();

        element.put("id", "0x" + Integer.toHexString(x + (y << 8)));
        element.put("typ", "gspc2scenery");

        page.addUnmodelledElement(element);

        if ("right".equals(shift)) page.shiftRight(1);
        if ("down".equals(shift)) page.shiftDown(1);
        if ("left".equals(shift)) page.shiftLeft(1);
        if ("up".equals(shift)) page.shiftUp(1);

        String written = page.exportToCS2TextFormat();

        int typ = written.indexOf(" .typ=gspc2scenery");

        assertTrue(typ > 0, "the element was not written at all after shift " + shift + ": " + written);

        int id = written.lastIndexOf(" .id=", typ);

        // ITS OWN id: the line before its typ, inside the same element
        if (id < 0 || written.substring(id, typ).contains("element")) return null;

        return written.substring(id + " .id=".length(), typ).trim();
    }

    /**
     * And a page with everything in one row is not mistaken for an empty one.
     */
    @Test
    public void testOneRowOfComponentsStillMeasuresThatRow() throws java.io.IOException
    {
        LayoutDiagram page = new LayoutDiagram("row", 30, 10, null, null);

        page.addComponent(componentType.FEEDBACK, 4, 6, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 7, 6, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setEdit(false);
        page.checkBounds();

        assertEquals(page.getMiny(), 6, "the row's own y was lost");
        assertEquals(page.getMaxy(), 6);
        assertEquals(page.getMinx(), 4, "the row's own x was lost");
        assertEquals(page.getMaxx(), 7);
    }
}
