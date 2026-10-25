package ui;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.widgets.StationCaption;
import support.SourceOf;

/**
 * FR-035, and the arrow that moves to the other side of the label.
 *
 * Adam: "If going south or west, move the arrow to the other side of the label." That rule is pure (a string and a
 * glyph), so it is driven directly.  And: "In the autonomy editor ONLY, make it possible to move around station labels
 * (only) by clicking and dragging them. Do not make tiles or anything else movable." A drag cannot be performed
 * without a window, a railway and a mouse, so what is checked is the part that can be wrong unnoticed: WHAT the
 * gesture is attached to, and whether the mark drawn during the drag asks the same question the drop will.
 *
 * @author Adam
 */
public class testStationLabelDrag
{
    /**
     * A south or west arrow leads the name; north and east follow it.
     *
     * It falls out right for a rotated caption without a special case: a rotated caption reads bottom to top, so the
     * START of the string is the BOTTOM of the pill, where a downward arrow belongs.
     *
     * MUTATION: having `arrowLeads` answer for north and east instead puts every arrow on the wrong
     * side and fails both halves.
     */
    @Test
    public void testTheArrowLeadsWhenItPointsBackwards()
    {
        assertTrue(StationCaption.arrowLeads(StationCaption.ARROW_S),
            "a south arrow follows the name, so it points away from the label it belongs to");

        assertTrue(StationCaption.arrowLeads(StationCaption.ARROW_W),
            "a west arrow follows the name, so it points off across whatever is drawn to the right");

        assertFalse(StationCaption.arrowLeads(StationCaption.ARROW_N), "a north arrow should follow");
        assertFalse(StationCaption.arrowLeads(StationCaption.ARROW_E), "an east arrow should follow");

        assertEquals(StationCaption.withArrow("Bahnhof", StationCaption.ARROW_N),
            "Bahnhof " + StationCaption.ARROW_N, "a north arrow is not after the name");

        assertEquals(StationCaption.withArrow("Bahnhof", StationCaption.ARROW_S),
            StationCaption.ARROW_S + " Bahnhof", "a south arrow is not before the name");

        // The arrow is carried with a leading space by the callers, which must not double up.
        assertEquals(StationCaption.withArrow("Bahnhof", " " + StationCaption.ARROW_W),
            StationCaption.ARROW_W + " Bahnhof",
            "the space the caller carries the arrow with came through as a second space");

        assertEquals(StationCaption.withArrow("Bahnhof", ""), "Bahnhof",
            "a station with no known facing gained a space it did not ask for");

        assertEquals(StationCaption.withArrow("Bahnhof", null), "Bahnhof",
            "a null arrow was not treated as no arrow");
    }

    /**
     * Both places that join a name to an arrow go through the one rule: the caption showing one train, and the crowded
     * caption showing two.  They used to join with `+` in different files, and two spellings of one rule are two rules.
     *
     * MUTATION: putting `cut + arrow` back in LayoutGrid fails this.
     */
    @Test
    public void testBothCaptionsJoinTheArrowTheSameWay() throws Exception
    {
        assertTrue(SourceOf.occurrences("StationCaption.withArrow(cut, arrow)") >= 1,
            "the single caption joins its arrow with something other than the shared rule, so which "
            + "side the arrow goes on is now decided in two places");

        assertTrue(SourceOf.occurrences("StationCaption.withArrow(")
                > SourceOf.occurrences("StationCaption.withArrow(cut, arrow)"),
            "the crowded caption - two trains on one platform - joins its arrows its own way, so a "
            + "south-facing train has its arrow in front when it is alone and behind when it is not");
    }

    /**
     * The drag is attached to station captions in the autonomy editor, and to nothing else.
     *
     * Adam: "do not make tiles or anything else movable." The label added a few lines further down the same method is
     * the ADDRESS label (the sensor's number), which must not become draggable by being caught in the same net.
     *
     * MUTATION: dropping `captioned != null` from the guard, or attaching the gesture at the second
     * add, fails this.
     */
    @Test
    public void testOnlyEditorCaptionsCanBeDragged() throws Exception
    {
        String grid = SourceOf.allCode();

        assertTrue(grid.contains("if (autonomyEditor && captioned != null"),
            "the drag is no longer limited to captions in the autonomy editor - either the running "
            + "diagram's labels have become draggable, or the user's own writing has");

        // TWO handles, named: "have it fire on the label or the tile".  Counted AND named, since a bare count would be
        // raised by somebody who attached the gesture to the wrong thing (the sensor's ADDRESS label below).
        assertTrue(grid.contains("dragCaption(text, text,"),
            "the label itself is no longer a handle, so a caption cannot be picked up by the pill");

        assertTrue(grid.contains("dragCaption(text, grid[x][y],"),
            "the square under the caption is no longer a handle - which is the whole of the second "
            + "half of FR-035, since a pill is too small a target to find");

        int attached = SourceOf.occurrences("dragCaption(");

        // The definition, and those two calls.
        assertEquals(attached, 3,
            "dragCaption appears " + attached + " times rather than a definition and the two handles "
            + "named above - the gesture has been attached to something else, and the label added "
            + "just after the caption is the sensor's address");
    }

    /**
     * What the drag mark says and what the drop does are one question.
     *
     * The mark asks `canDropCaption`, and `canDropCaption` and `moveCaption` both ask `refuseCaptionDrop`.  A highlight
     * with a rule of its own would offer a square the drop then refused (OB-057, OB-090).
     *
     * MUTATION: having the mark answer `over != null` fails the first assertion; giving moveCaption
     * its own copy of the checks fails the count.
     *
     * CANNOT SEE: a mark that still CALLS canDropCaption inside a neutered expression (`over != null && false && ...`)
     * passes, because a source scan reads names, not meaning.  It catches the call being removed or replaced, not the
     * answer being thrown away; only driving a real drag would.
     */
    @Test
    public void testTheDragMarkAsksTheDropItsOwnQuestion() throws Exception
    {
        String panel = SourceOf.allCode();

        assertTrue(SourceOf.declaration("private static void dragCaption(").contains("canDropCaption("),
            "the square marked under the pointer is chosen by something other than the rule that "
            + "will decide the drop, so a square can be shown as a target and then refuse");

        int asks = SourceOf.occurrences("refuseCaptionDrop(from, to, onTarget)");

        assertEquals(asks, 2,
            "the refusal rule is consulted " + asks + " times rather than by both canDropCaption and "
            + "moveCaption - one of them has its own copy of the checks, and the two will disagree");

        // And every refusal says something, except the one that has nothing to say.
        assertTrue(panel.contains("autosetup.ui.errorDropOffDiagram")
            && panel.contains("autosetup.ui.errorDropOnControl")
            && panel.contains("autosetup.ui.errorDropOccupied"),
            "a drop can now be refused without saying why, which is indistinguishable from the "
            + "window having failed to notice the drag at all");
    }

    /**
     * The picture taken of a dragged label is the PILL, not the component around it.
     *
     * Adam: "snapshot the label so users can see it is being moved (make it follow the cursor while held down)."  A
     * caption's cell runs to the bottom of the diagram, so photographing all of it would give a ghost hundreds of
     * pixels tall hanging a long way from the pointer.
     *
     * MUTATION: having `drawnBounds` return the component's own bounds fails the crop assertion - the
     * ink lands in a corner instead of the middle.
     */
    @Test
    public void testTheDragPictureIsCroppedToThePill() throws Exception
    {
        StationCaption pill = new StationCaption();

        pill.setPill(true);
        pill.setFont(new java.awt.Font(StationCaption.LABEL_FONT, java.awt.Font.PLAIN, 20));
        pill.setBackground(StationCaption.PILL);
        pill.setForeground(java.awt.Color.WHITE);
        pill.setText("Ostbahnhof");

        // Placed the way the grid places one: pushed down its square by a top inset.
        pill.setTileGeometry(60, 0, 0, StationCaption.captionOffset(60, pill.lineHeight()));

        java.awt.Dimension size = pill.getPreferredSize();

        pill.setBounds(0, 0, size.width, size.height);

        java.awt.Rectangle drawn = pill.drawnBounds();

        assertNotNull(drawn, "a caption with text on it reports nothing drawn, so there is no picture "
            + "to pick up");

        assertTrue(drawn.y > 0,
            "the drawn pill starts at the top of its component, so the offset that places it on its "
            + "square has been lost - and a crop taken from here would be of the wrong part");

        assertTrue(drawn.height < size.height,
            "the pill is as tall as its whole component, so cropping to it saves nothing and the "
            + "ghost would carry the empty room the caption is placed with");

        java.awt.image.BufferedImage shot = new java.awt.image.BufferedImage(
            drawn.width, drawn.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        java.awt.Graphics2D g = shot.createGraphics();

        g.translate(-drawn.x, -drawn.y);

        pill.paint(g);

        g.dispose();

        int middle = shot.getRGB(shot.getWidth() / 2, shot.getHeight() / 2);

        assertTrue(((middle >>> 24) & 0xFF) > 0,
            "the middle of the picture the drag carries is transparent, so what follows the cursor is "
            + "an empty rectangle rather than the label");
    }

    /**
     * The picked-up label is always put back down.
     *
     * A release arrives whether or not a drag started and whether or not the drop is accepted; if putting it down were
     * conditional on any of that, a refused drop or a press that was a click would leave a floating copy on the
     * window's drag layer until the editor was rebuilt.
     *
     * MUTATION: moving `hideCaptionGhost` below the early return fails this.
     *
     * `hideCaptionGhost()` is called TWICE in the method (inside the wrong-button branch, and unconditionally ahead of
     * the real "did this actually move anything" question), so anchoring on the first occurrence of each name can never
     * fail.  Anchored instead on `boolean moved = dragging[0];`, directly above the real put-down and nowhere else in
     * the method.
     */
    @Test
    public void testThePickedUpLabelIsAlwaysPutDown() throws Exception
    {
        String drag = SourceOf.declaration("private static void dragCaption(");

        String released = SourceOf.declarationIn(drag, "public void mouseReleased(MouseEvent e)");

        int movedRead = released.indexOf("boolean moved = dragging[0];");

        assertTrue(movedRead >= 0, "cannot find where the method reads whether a drag actually "
            + "happened - has it been renamed or restructured?");

        int down = released.indexOf("hideCaptionGhost()", movedRead);
        int leaves = released.indexOf("if (!moved", movedRead);

        // Proved present, and past the wrong-button branch's own pairing, before they are ordered.
        assertTrue(down > movedRead, "the floating copy of the label is never removed after a "
            + "real drag ends, so it stays on the window after the drag ends");

        assertTrue(leaves > movedRead, "the early-out that skips committing a move which never "
            + "happened is gone, so every release now tries to commit one");

        assertTrue(down < leaves,
            "the label is put down after the handler can already have returned, so a click that was "
            + "not a drag, or a drop that was refused, leaves a copy of the caption stuck over the "
            + "diagram");
    }

    /**
     * Only the LEFT button picks a station label up.
     *
     * Every button started a drag, so a right-press on a captioned square that wandered past DRAG_SLOP moved the label
     * instead of opening the properties menu, and `mouseClicked` never fired either.  The click path in the same
     * adapter already made the distinction.
     *
     * MUTATION: dropping the button test lets any press begin a drag and fails this.
     */
    @Test
    public void testOnlyTheLeftButtonPicksALabelUp() throws Exception
    {
        String drag = SourceOf.declaration("private static void dragCaption(");

        String pressed = SourceOf.declarationIn(drag, "public void mousePressed(MouseEvent e)");

        assertTrue(pressed.contains("isLeftMouseButton"),
            "any mouse button now starts a station-label drag, so a right-press that wanders a few "
            + "pixels moves the label and swallows the menu that was being asked for");

        // "dragging[0] = false" occurs TWICE in this method (the wrong-button branch and the ordinary path), so
        // searching the whole body would pass on the second alone; scoped to just the wrong-button branch.
        String wrongButton = SourceOf.declarationIn(pressed,
            "if (!javax.swing.SwingUtilities.isLeftMouseButton(e))");

        assertTrue(wrongButton.contains("dragging[0] = false"),
            "a press of the wrong button clears the start point but leaves the drag armed, so the "
            + "ghost freezes and the eventual release still commits a move");

        // AND THE RELEASE, which is where the move is committed: a right-release part way through a left-drag must not
        // still drop the label.
        String released = SourceOf.declarationIn(drag, "public void mouseReleased(MouseEvent e)");

        assertTrue(released.contains("isLeftMouseButton"),
            "the release commits a caption move whatever button was let go, so pressing another "
            + "button mid-drag - which is how a person cancels - drops the label instead");
    }

    /**
     * A label cannot be dropped on the grid's spacers, which are not squares.
     *
     * The grid ends in a dummy row and column that stop long labels misaligning it, and they are LayoutLabels with real
     * bounds, so the drop accepted them; the mark showed GREEN first (a spacer carries no component, and a square with
     * nothing on it may hold a caption), and the label then vanished, stored against a square the drawing loop skips.
     * The strip along the bottom is exactly where somebody would drop a label for a station on the last row.
     *
     * Read rather than run: reaching `tileUnder` needs a built grid, a window and a drag in progress.
     *
     * MUTATION: removing the spacer check from tileUnder fails this.
     */
    @Test
    public void testTheGridsSpacersAreNotDropTargets() throws Exception
    {
        String under = SourceOf.declaration("private static LayoutLabel tileUnder(");

        assertTrue(under.contains("isSpacer()"),
            "the search for the tile under the pointer no longer skips the grid's spacers, so a "
            + "station label dropped on the strip along the bottom or the right edge is accepted - "
            + "the drop mark even shows green - and then stored against a square the diagram never "
            + "draws, where it vanishes");
    }

}
