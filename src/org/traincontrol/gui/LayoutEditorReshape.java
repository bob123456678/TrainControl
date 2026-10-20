package org.traincontrol.gui;

import javax.swing.JOptionPane;
import org.traincontrol.util.I18n;

/**
 * What the track editor does to the whole page at once: shifting everything past the hovered square up, down, left or
 * right, and growing or shrinking the page by an edge (FR-136 Phase 4; the bodies are `LayoutEditor`'s, moved
 * unchanged).
 *
 * The page, the hovered square and the undo history stay on the editor and are read through `editor`; each public
 * method of the editor that does one of these keeps its name and calls in here, because the right-click menu and the
 * autonomy editor call them on the editor.  Nothing here is `synchronized`, and nothing was before the move.
 */
final class LayoutEditorReshape
{
    private final LayoutEditor editor;

    LayoutEditorReshape(LayoutEditor editor)
    {
        this.editor = editor;
    }

    // The four "shift the whole diagram" wrappers used to live here, then didn't, then did again.
    //
    // Taken off the menu when multi-select arrived, on the reasoning that making room by dragging
    // what is in the way out of it can be seen before it happens. Restored below - see shiftUp's own
    // javadoc - once that reasoning turned out to describe a different job: dragging moves the
    // squares you picked, this moves everything below or to the right of one, which on a diagram of
    // two hundred tiles is not a selection anybody wants to make by hand. Left here as a record that
    // "no caller" was not, and is not, the same test as "not wanted" - check before deleting a set of
    // methods a second time.


    /**
     * Whether shiftUp would do anything.
     *
     * Public because the menu asks it before offering the item, and the method asks it before acting -
     * ONE predicate, asked in both places. Written out twice, the menu and the method drift, and the
     * drift is exactly this finding: the item was offered on the last row, where it returned in
     * silence.
     *
     * False on the edge, where there is no the row below it to move up into.
     *
     * @return true when there is a hovered square this can work from
     */
    boolean canShiftUp()
    {
        return !(this.editor.lastHoveredY < 0 || this.editor.lastHoveredY > this.editor.layout.getSy() - 2);
    }

    /**
     * Whether the page has room to grow by this much.
     *
     * **The ceiling had three copies and two of the gestures that need it had none.**  It was written
     * out in `growEdges`, again in `addRowsAndColumns`, and a third time in the right-click menu that
     * greys Increase Size - while `shiftDown` and `shiftRight`, two items away on the same popup, grow
     * the page by a row or a column and asked nothing.  `LayoutDiagram.shiftDown` opens with
     * `addRowsAndColumns(1, 0)`, and neither it nor the model's own grower knows the limit exists.
     *
     * So at the ceiling the menu greyed one item with a tooltip explaining why, and offered another
     * that did the same thing and worked.  That was one submenu
     * expressing one rule two ways.
     *
     * Public because the menu asks it before offering an item and the methods ask it before acting:
     * ONE predicate, asked in both places, which is the arrangement.
     *
     * @param rows how many rows the gesture would add
     * @param columns how many columns it would add
     * @return true when the result would still be within MAX_SIZE
     */
    boolean roomToGrow(int rows, int columns)
    {
        // EACH DIMENSION ONLY WHERE THE GESTURE ADDS TO IT.
        //
        // Written as one conjunction over both, `roomToGrow(0, 1)` still asked `getSy() <= MAX_SIZE` -
        // so a page taller than the ceiling refused a COLUMN shift, which adds nothing to its height,
        // and a page wider than it refused a row shift.  That is not hypothetical here: a page's size
        // comes from its largest element coordinate with nothing clamping it, and three fixture pages
        // in this repository parse at 17 x 129.
        //
        // Asking about a dimension the caller is not growing is asking whether the page is already too
        // big, which is a different question and not one a shift can make worse.
        if (rows > 0 && this.editor.layout.getSy() + rows > LayoutEditor.MAX_SIZE) return false;

        if (columns > 0 && this.editor.layout.getSx() + columns > LayoutEditor.MAX_SIZE) return false;

        return true;
    }

    /**
     * Whether shiftLeft would do anything.
     *
     * Public because the menu asks it before offering the item, and the method asks it before acting -
     * ONE predicate, asked in both places. Written out twice, the menu and the method drift, and the
     * drift is exactly this finding: the item was offered on the last row, where it returned in
     * silence.
     *
     * False on the edge, where there is no the column to its right to move left into.
     *
     * @return true when there is a hovered square this can work from
     */
    boolean canShiftLeft()
    {
        return !(this.editor.lastHoveredX < 0 || this.editor.lastHoveredX > this.editor.layout.getSx() - 2);
    }

    /**
     * Whether shiftDown would do anything.
     *
     * The twin of canShiftUp, and it was missed when that one was written - so the same submenu
     * expressed one rule two ways, which is drift.
     *
     * Asked by addShift alongside canShiftUp and canShiftLeft, and the answer goes straight to
     * setEnabled - so this is live, not dormant. It is
     * nearly always true, because opening the popup sets the hovered position, but "nearly always" is
     * not the same as "never asked".
     *
     * @return true when there is a hovered square this can work from
     */
    boolean canShiftDown()
    {
        // AND THE CEILING.  This grows the page by a row, and Increase Size beside it on the
        // same popup is greyed at the limit with a tooltip saying why.
        return this.editor.lastHoveredY >= 0 && roomToGrow(1, 0);
    }

    /**
     * Whether shiftRight would do anything.
     *
     * The twin of canShiftUp, and it was missed when that one was written - so the same submenu
     * expressed one rule two ways, which is drift.
     *
     * Asked by addShift alongside canShiftUp and canShiftLeft, and the answer goes straight to
     * setEnabled - so this is live, not dormant. It is
     * nearly always true, because opening the popup sets the hovered position, but "nearly always" is
     * not the same as "never asked".
     *
     * @return true when there is a hovered square this can work from
     */
    boolean canShiftRight()
    {
        // AND THE CEILING, for the same reason as canShiftDown: this grows the page by a
        // column.
        return this.editor.lastHoveredX >= 0 && roomToGrow(0, 1);
    }

    /**
     * Inserts a row or a column at the hovered square and pushes everything past it along.
     *
     * Restored after being taken out with the rest of the bulk operations when multi-select arrived.
     * The reasoning then was that making room by dragging what is in the way out of it can be seen
     * before it happens, and that is still true - but it is a different job.  Dragging moves the
     * squares you picked; this moves everything below or to the right of one square, which on a
     * diagram of two hundred tiles is not a selection anybody wants to make by hand.
     *
     * Undoable, which is what makes it safe to reach for: each one takes a snapshot first, so a
     * mis-aimed shift is one Control+Z away.
     *
     * The autonomy setup FOLLOWS the shift.  Everything it holds about a page is keyed by SQUARE, so
     * moving the track without moving those keys leaves every station, name, facing and restriction on
     * coordinates the track has walked away from - which is what these four did until moveTiles was
     * added below.
     *
     * That sentence used to read the other way round: "the reason these do not appear in autonomy mode
     * is that shifting moves the track without moving those keys." It was true when it was written and
     * false eighteen lines later in the same method, and it was stated as established fact, which is
     * the form a reader trusts without checking.
     */
    void shiftUp()
    {
        // Refused on the last row, rather than quietly meaning something else.
        //
        // LayoutDiagram.shiftUp normalises any start row past sy - 2 to the FIRST row, so hovering the
        // bottom row - the natural gesture for "take this empty row away" - shifted the entire page up
        // by one and destroyed row 0.  The map handed to the setup is built from the row the user
        // pointed at, so it came out empty: the track moved and every station, name, length and
        // pairing on the page stayed on the square it used to be on.  Nothing was dropped by the next
        // reconcile either, because every square still had a tile - the whole page's setup was simply
        // attached to the wrong tiles, silently.
        if (!canShiftUp()) return;

        this.editor.snapshotLayout();

        try
        {
            if (this.editor.lastHoveredY > -1)
            {
                // Worked out BEFORE the shift, from the dimensions as they stand: shifting down or
                // right grows the page, so afterwards the numbers describe a diagram this map is not
                // about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(false, this.editor.lastHoveredY + 1, this.editor.layout.getSy() - 1, 0, -1);

                this.editor.layout.shiftUp(this.editor.lastHoveredY);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
                //
                // These four move every tile past one square and told the setup nothing at all, which
                // is the same fault a dragged tile had - every station, name, facing and restriction
                // left behind on coordinates the track had walked away from, and the next reconcile
                // dropping them.  Worse here than for a drag: one menu click moves half the diagram.
                org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTiles(moving);

                    this.editor.rememberAutonomy(autonomy);
                }

                this.editor.refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.editor.parent.getModel().log(e.getMessage());
            this.editor.parent.getModel().log(e);
        }
    }

    void shiftDown()
    {
        // No refusal here, unlike shiftUp and shiftLeft, and the reason is worth stating rather than
        // being left to be worked out from a side effect two files away.
        //
        // LayoutDiagram normalises an out-of-range start to the FIRST row or column in all four
        // directions, which is what made the destructive pair dangerous.  This pair calls
        // addRowsAndColumns BEFORE the range is checked, so the page has already grown by one and the
        // out-of-range start cannot be reached from a hover.  If that ever stops being true - if the
        // growth moves or becomes conditional - this needs shiftUp's guard, which costs nothing.
        // GUARDED BEFORE THE SNAPSHOT.
        //
        // Snapshotting first pushed an undo entry for an edit that never happened: with no hovered
        // square this method does nothing, but the entry clears the redo stack and makes the editor
        // ask about saving work nobody did. shiftUp and shiftLeft already checked first.
        if (!canShiftDown()) return;

        this.editor.snapshotLayout();
        
        try
        {
            if (this.editor.lastHoveredY > -1)
            {
                // Worked out BEFORE the shift, from the dimensions as they stand: shifting down or
                // right grows the page, so afterwards the numbers describe a diagram this map is not
                // about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(false, this.editor.lastHoveredY, this.editor.layout.getSy() - 1, 0, 1);

                this.editor.layout.shiftDown(this.editor.lastHoveredY);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
                //
                // These four move every tile past one square and told the setup nothing at all, which
                // is the same fault a dragged tile had - every station, name, facing and restriction
                // left behind on coordinates the track had walked away from, and the next reconcile
                // dropping them.  Worse here than for a drag: one menu click moves half the diagram.
                org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTiles(moving);

                    this.editor.rememberAutonomy(autonomy);
                }

                this.editor.refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.editor.parent.getModel().log(e.getMessage());
            this.editor.parent.getModel().log(e);
        }
    }

    void shiftLeft()
    {
        // Refused on the last column - see shiftUp, which has the same normalisation behind it
        if (!canShiftLeft()) return;

        this.editor.snapshotLayout();

        try
        {
            if (this.editor.lastHoveredX > -1)
            {
                // Worked out BEFORE the shift, from the dimensions as they stand: shifting down or
                // right grows the page, so afterwards the numbers describe a diagram this map is not
                // about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(true, this.editor.lastHoveredX + 1, this.editor.layout.getSx() - 1, -1, 0);

                this.editor.layout.shiftLeft(this.editor.lastHoveredX);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
                //
                // These four move every tile past one square and told the setup nothing at all, which
                // is the same fault a dragged tile had - every station, name, facing and restriction
                // left behind on coordinates the track had walked away from, and the next reconcile
                // dropping them.  Worse here than for a drag: one menu click moves half the diagram.
                org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTiles(moving);

                    this.editor.rememberAutonomy(autonomy);
                }

                this.editor.refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.editor.parent.getModel().log(e.getMessage());
            this.editor.parent.getModel().log(e);
        }
    }

    void shiftRight()
    {
        // No refusal here, unlike shiftUp and shiftLeft, and the reason is worth stating rather than
        // being left to be worked out from a side effect two files away.
        //
        // LayoutDiagram normalises an out-of-range start to the FIRST row or column in all four
        // directions, which is what made the destructive pair dangerous.  This pair calls
        // addRowsAndColumns BEFORE the range is checked, so the page has already grown by one and the
        // out-of-range start cannot be reached from a hover.  If that ever stops being true - if the
        // growth moves or becomes conditional - this needs shiftUp's guard, which costs nothing.
        // GUARDED BEFORE THE SNAPSHOT.
        //
        // Snapshotting first pushed an undo entry for an edit that never happened: with no hovered
        // square this method does nothing, but the entry clears the redo stack and makes the editor
        // ask about saving work nobody did. shiftUp and shiftLeft already checked first.
        if (!canShiftRight()) return;

        this.editor.snapshotLayout();

        try
        {
            if (this.editor.lastHoveredX > -1)
            {
                // Worked out BEFORE the shift, from the dimensions as they stand: shifting down or
                // right grows the page, so afterwards the numbers describe a diagram this map is not
                // about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(true, this.editor.lastHoveredX, this.editor.layout.getSx() - 1, 1, 0);

                this.editor.layout.shiftRight(this.editor.lastHoveredX);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
                //
                // These four move every tile past one square and told the setup nothing at all, which
                // is the same fault a dragged tile had - every station, name, facing and restriction
                // left behind on coordinates the track had walked away from, and the next reconcile
                // dropping them.  Worse here than for a drag: one menu click moves half the diagram.
                org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTiles(moving);

                    this.editor.rememberAutonomy(autonomy);
                }

                this.editor.refreshGrid();
            }
        }
        catch (Exception e)
        {
            this.editor.parent.getModel().log(e.getMessage());
            this.editor.parent.getModel().log(e);
        }
    }

    /**
     * Which squares a shift moves, and where to.
     *
     * The whole page from one row or column onwards, moved by one.  A square that is not in the
     * range is left out entirely rather than mapped to itself, so moveTiles can tell the difference
     * between a square that moved and a square something arrived on.
     *
     * @param across true to shift columns, false to shift rows
     * @param from the first row or column that moves
     * @param to the last one
     * @param dx how far each moves horizontally
     * @param dy and vertically
     * @return the moves
     */
    private java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
        org.traincontrol.automationui.TileGraph.TileKey> setupShift(boolean across, int from, int to,
        int dx, int dy)
    {
        java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
            org.traincontrol.automationui.TileGraph.TileKey> moving = new java.util.LinkedHashMap<>();

        int otherEnd = across ? this.editor.layout.getSy() - 1 : this.editor.layout.getSx() - 1;

        for (int line = from; line <= to; line++)
        {
            for (int other = 0; other <= otherEnd; other++)
            {
                int x = across ? line : other;
                int y = across ? other : line;

                moving.put(
                    new org.traincontrol.automationui.TileGraph.TileKey(this.editor.layout.getName(), x, y),
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        this.editor.layout.getName(), x + dx, y + dy));
            }
        }

        return moving;
    }

    /**
     * Grows the diagram by one: a column on the right and a row at the bottom.
     *
     * NOT a row at the top, which is what was asked for and what the first version of this did.
     *
     * Inserting a row at the top moves every tile on the page down by one - and everything autonomy
     * knows about that page is keyed by SQUARE. Stations, protecting signals, barred arrival sides,
     * parking and reversing marks, home locomotives, station captions: all of them name a square, and
     * none of them would move. A user with a set-up page who pressed "+" to make room would find
     * every station one row above its platform, every signal pairing pointing at plain track, and
     * every arrival restriction applied to the wrong square - silently, with the diagram still
     * looking exactly right.
     *
     * Doing it properly means rewriting every key the companion store holds for that page, which is a
     * change with its own test suite and not one to make in passing. Growing at the right and the
     * bottom moves nothing, so it is safe today; the mirror property that made top-and-bottom
     * attractive is kept, because shrinkEdges takes away exactly these two.
     *
     * FOR ADAM: the top row is deliberately not done. Say the word and it becomes a proper
     * shift-the-page operation with the store rewritten to match.
     *
     * (This block used to sit 280 lines away, above shiftUp, attached to nothing - Java
     * attaches only the last javadoc before a declaration, and a second block followed it before this
     * method ever got one. Moved here, onto the method it is actually about.)
     */
    void growEdges()
    {
        if (!roomToGrow(1, 1))  // ONE PREDICATE
        {
            JOptionPane.showMessageDialog(this.editor, I18n.f("layout.ui.errorMaxSizeExceeded", LayoutEditor.MAX_SIZE));

            return;
        }

        this.editor.snapshotLayout();

        try
        {
            this.editor.layout.addRowsAndColumns(1, 1);

            this.editor.showDiagramSize();

            // The picked squares are let go of.  A selection that outlived a resize would still name
            // coordinates by number, and after a SHRINK some of those numbers are off the diagram -
            // the next group move would then read a square that no longer exists.  Growing is safe
            // today, but the two have to behave the same way or the difference becomes a trap.
            this.editor.clearSelection();

            this.editor.refreshGrid();
        }
        catch (Exception e)
        {
            this.editor.parent.getModel().log(e.getMessage());
            this.editor.parent.getModel().log(e);
        }
    }

    /**
     * Drops any caption sitting on the row and column a shrink is about to remove.
     *
     * Called before trimEdges, while those squares still exist to be named.
     */
    private void forgetCaptionsOnTrimmedEdge()
    {
        org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

        if (autonomy == null) return;

        boolean changed = false;

        for (int y = 0; y < this.editor.layout.getSy(); y++)
        {
            changed |= autonomy.forgetCaptionsAt(new org.traincontrol.automationui.TileGraph.TileKey(
                this.editor.layout.getName(), this.editor.layout.getSx() - 1, y));
        }

        for (int x = 0; x < this.editor.layout.getSx(); x++)
        {
            changed |= autonomy.forgetCaptionsAt(new org.traincontrol.automationui.TileGraph.TileKey(
                this.editor.layout.getName(), x, this.editor.layout.getSy() - 1));
        }

        if (changed) this.editor.rememberAutonomy(autonomy);
    }

    /**
     * Shrinks the diagram by one, undoing exactly what growEdges adds.
     *
     * Refused outright when either edge holds track. Trimming what it could would take a piece of
     * railway off the diagram to save the user a scroll, which is not a trade anybody would accept if
     * they were asked - so this asks, by refusing and saying why.
     */
    void shrinkEdges()
    {
        if (!this.editor.layout.edgesAreEmpty())
        {
            JOptionPane.showMessageDialog(this.editor, I18n.t("layout.ui.errorEdgesNotEmpty"));

            return;
        }

        this.editor.snapshotLayout();

        try
        {
            // THE EDGE MAY HOLD A NAME EVEN WHEN IT HOLDS NO TRACK.
            //
            // edgesAreEmpty looks at the grid, which is track. A station's caption is placed on a blank
            // square by preference - placeCaption looks for "an empty square next to it" - and the
            // bottom row and right column are blank squares like any other. Trimmed away underneath
            // one, the caption kept coordinates that are now off the page: not drawn on the diagram,
            // still present, so the "station is not shown anywhere" check stayed quiet, and no square
            // remained for the user to click to move or remove it.
            //
            // Forgotten rather than refused. The caption is where a name is DRAWN, not the name
            // itself: the station keeps its name, the check notices it has nowhere to show it, and the
            // user is offered it again. Refusing the shrink instead would block a tidy-up over a label
            // that can be re-placed in one click.
            forgetCaptionsOnTrimmedEdge();

            this.editor.layout.trimEdges();

            this.editor.showDiagramSize();

            this.editor.clearSelection();

            this.editor.refreshGrid();
        }
        catch (Exception e)
        {
            this.editor.parent.getModel().log(e.getMessage());
            this.editor.parent.getModel().log(e);
        }
    }

    void addRowsAndColumns(int rows, int cols)
    {
        // ASKED ABOUT WHAT IS BEING ADDED.  `roomToGrow(1, 1)` is the question "is there room
        // for one more of each", which a call adding twenty rows can pass and then overflow.  It could
        // not today - the one caller pads a blank page to 16 x 21 - and it is the "one
        // predicate" rule asked about the wrong amount.
        if (!roomToGrow(rows, cols))  // ONE PREDICATE
        {
            JOptionPane.showMessageDialog(
                this.editor,
                I18n.f("layout.ui.errorMaxSizeExceeded", LayoutEditor.MAX_SIZE)
            );
            return;
        }
        
        try
        {
            this.editor.layout.addRowsAndColumns(rows, cols);
            
            this.editor.refreshGrid();
        }
        catch (Exception e)
        {
            this.editor.parent.getModel().log(e.getMessage());
            this.editor.parent.getModel().log(e);
        }
    }
}
