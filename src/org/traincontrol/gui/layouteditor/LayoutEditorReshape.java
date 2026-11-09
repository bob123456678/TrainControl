package org.traincontrol.gui.layouteditor;

import javax.swing.JOptionPane;
import org.traincontrol.util.I18n;

/**
 * What the track editor does to the whole page at once: shifting everything past the hovered square up, down, left or
 * right, and growing or shrinking the page by an edge (FR-136 Phase 4).
 *
 * The page, the hovered square and the undo history stay on the editor and are read through `editor`; each public
 * method of the editor that does one of these keeps its name and calls in here, because the right-click menu and the
 * autonomy editor call them on the editor.  Nothing here is `synchronized`.
 */
final class LayoutEditorReshape
{
    private final LayoutEditor editor;

    LayoutEditorReshape(LayoutEditor editor)
    {
        this.editor = editor;
    }

    /**
     * Whether shiftUp would do anything.  Public because the menu asks it before offering the item and the method asks
     * it before acting: ONE predicate, so the two cannot drift.  False on the edge, where there is no row below to move
     * up into.
     */
    boolean canShiftUp()
    {
        return !(this.editor.lastHoveredY < 0 || this.editor.lastHoveredY > this.editor.layout.getSy() - 2);
    }

    /**
     * Whether the page has room to grow by `rows` and `columns` (the result stays within MAX_SIZE): ONE predicate for
     * every gesture that grows the page, asked by the menu before offering an item and by the methods before acting.
     * Neither `LayoutDiagram.shiftDown` nor the model's own grower knows the limit.
     */
    boolean roomToGrow(int rows, int columns)
    {
        // Each dimension only where the gesture adds to it.  A page's size comes from its largest element coordinate
        // with nothing clamping it (fixture pages parse at 17 x 129), so asking about a dimension the caller is not
        // growing would refuse a column shift on a tall page and a row shift on a wide one.
        if (rows > 0 && this.editor.layout.getSy() + rows > LayoutEditor.MAX_SIZE) return false;

        if (columns > 0 && this.editor.layout.getSx() + columns > LayoutEditor.MAX_SIZE) return false;

        return true;
    }

    /**
     * Whether shiftLeft would do anything.  Public because the menu asks it before offering the item and the method
     * asks it before acting: ONE predicate, so the two cannot drift.  False on the edge, where there is no column to
     * its right to move left into.
     */
    boolean canShiftLeft()
    {
        return !(this.editor.lastHoveredX < 0 || this.editor.lastHoveredX > this.editor.layout.getSx() - 2);
    }

    /**
     * Whether shiftDown would do anything.  Asked by addShift alongside canShiftUp and canShiftLeft, and the answer
     * goes straight to setEnabled; nearly always true, because opening the popup sets the hovered position.
     */
    boolean canShiftDown()
    {
        // And the ceiling: this grows the page by a row, and Increase Size beside it on the same popup is grayed at the
        // limit with a tooltip saying why.
        return this.editor.lastHoveredY >= 0 && roomToGrow(1, 0);
    }

    /** Whether shiftRight would do anything; asked as canShiftDown is. */
    boolean canShiftRight()
    {
        // And the ceiling, as in canShiftDown: this grows the page by a column.
        return this.editor.lastHoveredX >= 0 && roomToGrow(0, 1);
    }

    /**
     * Inserts a row or a column at the hovered square and pushes everything past it along.  Undoable: each one takes a
     * snapshot first.  The autonomy setup FOLLOWS the shift: everything it holds about a page is keyed by SQUARE, so
     * moving the track without moving those keys leaves every station, name and facing on coordinates the track left.
     */
    void shiftUp()
    {
        // Refused on the last row, rather than quietly meaning something else: LayoutDiagram.shiftUp normalizes any start
        // row past sy - 2 to the FIRST row, so hovering the bottom row shifted the entire page up and destroyed row 0,
        // and the map handed to the setup came out empty, leaving the page's setup attached to the wrong tiles.
        if (!canShiftUp()) return;

        this.editor.snapshotLayout();

        shiftAndCarry(false, -1, line -> this.editor.layout.shiftUp(line));
    }

    void shiftDown()
    {
        // No range refusal here, unlike shiftUp and shiftLeft: this pair calls addRowsAndColumns BEFORE the range is
        // checked, so the start cannot be out of range from a hover (LayoutDiagram normalizes it to the FIRST row or
        // column otherwise).  Guarded before the snapshot, which would otherwise push an undo entry for an edit that
        // never happened, clearing the redo stack and making the editor ask about saving work nobody did.
        if (!canShiftDown()) return;

        this.editor.snapshotLayout();

        shiftAndCarry(false, 1, line -> this.editor.layout.shiftDown(line));
    }

    void shiftLeft()
    {
        // Refused on the last column - see shiftUp, which has the same normalization behind it
        if (!canShiftLeft()) return;

        this.editor.snapshotLayout();

        shiftAndCarry(true, -1, line -> this.editor.layout.shiftLeft(line));
    }

    void shiftRight()
    {
        // No range refusal here, for the reason given in shiftDown.  Guarded before the snapshot, which would otherwise
        // push an undo entry for an edit that never happened.
        if (!canShiftRight()) return;

        this.editor.snapshotLayout();

        shiftAndCarry(true, 1, line -> this.editor.layout.shiftRight(line));
    }

    /** One of the four LayoutDiagram shifts, given the hovered row or column. */
    private interface Shift
    {
        void at(int line) throws java.io.IOException;
    }

    /**
     * Shifts everything past the hovered row or column by one and carries the setup's keys with it, so every station,
     * name, facing and restriction stays on its track.  `across` is true for columns, false for rows; `step` is -1 to
     * shift up or left (the lines after the hovered one move), 1 for down or right (from it on).
     */
    private void shiftAndCarry(boolean across, int step, Shift shift)
    {
        try
        {
            int hovered = across ? this.editor.lastHoveredX : this.editor.lastHoveredY;

            if (hovered > -1)
            {
                // Worked out BEFORE the shift: shifting down or right grows the page, so afterwards the dimensions
                // describe a diagram this map is not about.
                java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                    org.traincontrol.automationui.TileGraph.TileKey> moving =
                    setupShift(across, step < 0 ? hovered + 1 : hovered,
                        (across ? this.editor.layout.getSx() : this.editor.layout.getSy()) - 1,
                        across ? step : 0, across ? 0 : step);

                shift.at(hovered);

                // And applied after, so the graph is rebuilt from a diagram that has already moved.
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
     * Which squares a shift moves, and where to: the whole page from row or column `from` to `to`, each moved by (`dx`,
     * `dy`).  A square not in the range is left out rather than mapped to itself, so moveTiles can tell a square that
     * moved from one something arrived on.
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
     * Grows the diagram by one: a column on the right and a row at the bottom.  NOT a row at the top: that would move
     * every tile down while everything autonomy knows about the page is keyed by SQUARE and would not move, leaving
     * every station one row above its platform.  Growing at the right and the bottom moves nothing, and keeps the
     * mirror property that shrinkEdges takes away exactly these two.
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

            // The picked squares are let go of: a selection that outlived a resize would name coordinates by number,
            // and after a SHRINK some of those are off the diagram.  Growing does the same so the two behave alike.
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
     * Shrinks the diagram by one, undoing exactly what growEdges adds.  Refused outright when either edge holds track:
     * trimming what it could would take a piece of railway off the diagram to save the user a scroll, so this asks, by
     * refusing and saying why.
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
            // The edge may hold a caption even when it holds no track: edgesAreEmpty looks at the grid.  Forgotten
            // rather than refused: the caption is where a name is DRAWN, not the name, so the station keeps its name
            // and the user is offered the caption again.
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
        // Asked about what is being added: roomToGrow(1, 1) asks only whether there is room for one more of each, which
        // a call adding twenty rows can pass and then overflow.
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
