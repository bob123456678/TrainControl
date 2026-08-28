package ui;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.gui.StationCaption;

/**
 * FR-035, and the arrow that moves to the other side of the label.
 *
 * Two things Adam asked for on 2026-08-27:
 *
 * "If going south or west, move the arrow to the other side of the label." That rule is pure - a
 * string and a glyph - so it is driven directly below.
 *
 * "In the autonomy editor ONLY, make it possible to move around station labels (only) by clicking and
 * dragging them. Do not make tiles or anything else movable." A drag gesture cannot be performed by a
 * test without a window, a railway and a mouse, so what is checked here is the part that can be wrong
 * without anybody noticing: WHAT the gesture is attached to, and whether the mark drawn during the
 * drag asks the same question the drop will.
 *
 * @author Adam
 */
public class testStationLabelDrag
{
    /**
     * A south or west arrow leads the name; north and east follow it.
     *
     * Adam: "if going south or west, move the arrow to the other side of the label."
     *
     * It falls out right for a rotated caption without a special case, which is worth knowing rather
     * than rediscovering: a rotated caption reads bottom to top, so the START of the string is the
     * BOTTOM of the pill - exactly where a downward arrow belongs.
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

        // And the join itself.
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
     * Both places that join a name to an arrow go through the one rule.
     *
     * There are two: the caption showing one train, and the crowded caption showing two. They used to
     * join with `+`, in different files, and two spellings of one rule are two rules - a platform with
     * one train on it would have got the new placement and a platform with two would have kept the old.
     *
     * MUTATION: putting `cut + arrow` back in LayoutGrid fails this.
     */
    @Test
    public void testBothCaptionsJoinTheArrowTheSameWay() throws Exception
    {
        String grid = read("src/org/traincontrol/gui/LayoutGrid.java");
        String ui = read("src/org/traincontrol/gui/TrainControlUI.java");

        assertTrue(grid.contains("StationCaption.withArrow(cut, arrow)"),
            "the single caption joins its arrow with something other than the shared rule, so which "
            + "side the arrow goes on is now decided in two places");

        assertTrue(ui.contains("StationCaption.withArrow("),
            "the crowded caption - two trains on one platform - joins its arrows its own way, so a "
            + "south-facing train has its arrow in front when it is alone and behind when it is not");
    }

    /**
     * The drag is attached to station captions in the autonomy editor, and to nothing else.
     *
     * Adam: "do not make tiles or anything else movable." The guard is the whole of that promise, and
     * the label added a few lines further down the same method is the ADDRESS label - the sensor's
     * number - which must not become draggable by being caught in the same net.
     *
     * MUTATION: dropping `captioned != null` from the guard, or attaching the gesture at the second
     * add, fails this.
     */
    @Test
    public void testOnlyEditorCaptionsCanBeDragged() throws Exception
    {
        String grid = read("src/org/traincontrol/gui/LayoutGrid.java");

        assertTrue(grid.contains("if (autonomyEditor && captioned != null"),
            "the drag is no longer limited to captions in the autonomy editor - either the running "
            + "diagram's labels have become draggable, or the user's own writing has");

        // TWO handles, named (Adam, 2026-08-27: "have it fire on the label or the tile").
        //
        // Counted AND named. A bare count would have to be raised every time a handle is added, and
        // raising it is exactly what somebody would do after attaching the gesture to the wrong thing
        // - the label added a few lines further down this method is the sensor's ADDRESS.
        assertTrue(grid.contains("dragCaption(text, text,"),
            "the label itself is no longer a handle, so a caption cannot be picked up by the pill");

        assertTrue(grid.contains("dragCaption(text, grid[x][y],"),
            "the square under the caption is no longer a handle - which is the whole of the second "
            + "half of FR-035, since a pill is too small a target to find");

        int attached = 0;

        for (int at = grid.indexOf("dragCaption("); at >= 0;
            at = grid.indexOf("dragCaption(", at + 1))
        {
            attached++;
        }

        // The definition, and those two calls.
        assertEquals(attached, 3,
            "dragCaption appears " + attached + " times rather than a definition and the two handles "
            + "named above - the gesture has been attached to something else, and the label added "
            + "just after the caption is the sensor's address");
    }

    /**
     * What the drag mark says and what the drop does are one question.
     *
     * The mark drawn under the pointer while dragging asks `canDropCaption`, and `canDropCaption` and
     * `moveCaption` both ask `refuseCaptionDrop`. A highlight with a rule of its own would offer a
     * square the drop then refused, which is the fault behind OB-057, OB-090 and one of this morning's
     * review findings - the third time this year.
     *
     * MUTATION: having the mark answer `over != null` fails the first assertion; giving moveCaption
     * its own copy of the checks fails the count.
     *
     * WHAT THIS CANNOT SEE, stated because it was found by trying it: a mark that still CALLS
     * canDropCaption inside an expression that has been neutered - `over != null && false && ...` -
     * passes, because the name is still in the file. A source scan reads names, not meaning. It
     * catches the call being removed or replaced, which is how this would actually regress; it does
     * not catch the answer being thrown away. Only driving a real drag would, and that needs a window,
     * a railway and a mouse.
     */
    @Test
    public void testTheDragMarkAsksTheDropItsOwnQuestion() throws Exception
    {
        String grid = read("src/org/traincontrol/gui/LayoutGrid.java");
        String panel = read("src/org/traincontrol/gui/AutonomyEditorPanel.java");

        assertTrue(grid.contains("canDropCaption("),
            "the square marked under the pointer is chosen by something other than the rule that "
            + "will decide the drop, so a square can be shown as a target and then refuse");

        int asks = 0;

        for (int at = panel.indexOf("refuseCaptionDrop(from, to, onTarget)"); at >= 0;
            at = panel.indexOf("refuseCaptionDrop(from, to, onTarget)", at + 1))
        {
            asks++;
        }

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
     * Adam: "snapshot the label so users can see it is being moved (make it follow the cursor while
     * held down)."
     *
     * A caption's cell runs to the bottom of the diagram - that is how it gets positioned, with a top
     * inset - so the component is mostly empty. Photographing all of it would give a ghost hundreds of
     * pixels tall whose visible part hangs a long way from the pointer, which reads as the drag having
     * picked up nothing.
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

        // And the crop really does hold the pill: paint it the way the drag does and look.
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
     * A release arrives whether or not a drag ever started, and whether or not the drop is accepted.
     * If putting it down were conditional on any of that, a refused drop - or a press that turned out
     * to be a click - would leave a floating copy of the caption on the window's drag layer with
     * nothing left to move it, sitting over the diagram until the editor was rebuilt.
     *
     * MUTATION: moving `hideCaptionGhost` below the early return fails this.
     */
    @Test
    public void testThePickedUpLabelIsAlwaysPutDown() throws Exception
    {
        String grid = read("src/org/traincontrol/gui/LayoutGrid.java");

        int released = grid.indexOf("public void mouseReleased(");

        assertTrue(released > 0, "the drag no longer handles the mouse coming up");

        int down = grid.indexOf("hideCaptionGhost()", released);
        int leaves = grid.indexOf("return;", released);

        assertTrue(down > 0, "the floating copy of the label is never removed, so it stays on the "
            + "window after the drag ends");

        assertTrue(down < leaves,
            "the label is put down after the handler can already have returned, so a click that was "
            + "not a drag, or a drop that was refused, leaves a copy of the caption stuck over the "
            + "diagram");
    }

    private String read(String path) throws Exception
    {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path)),
            java.nio.charset.StandardCharsets.UTF_8);
    }
}
