package org.traincontrol.gui;

import java.io.IOException;
import javax.swing.JOptionPane;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.util.I18n;

/**
 * What the track editor does with the squares it is pointed at or has picked: the single-tile clipboard (copy, move
 * and the tool a palette tile arms), the group clipboard (copy, cut and paste), and moving, filling, deleting and
 * rotating a selection or one square (FR-136 Phase 4).
 *
 * The state stays on the editor: the selection, the clipboards, `pauseRepaint` and the layout are read and written
 * through `editor`, so a test that reflects on those fields finds them there.  The editor's `synchronized` methods
 * stay on the editor and call in here, so the monitor is the editor's; a call from here to one of them goes back
 * through the editor so that the lock is taken again (it is re-entrant) whatever the caller held.
 */
final class LayoutEditorSelection
{
    private final LayoutEditor editor;

    LayoutEditorSelection(LayoutEditor editor)
    {
        this.editor = editor;
    }

    /**
     * Tells the setup that these squares have been built over, once for the whole gesture.  Per square, each call
     * rebuilt the whole graph twice and wrote every file of the setup to disk, which on a select-all paste is thousands
     * of rebuilds and writes on the event thread.  Collect, tell once, and save only if anything changed: the shape
     * moveSelection and the shift operations use.
     *
     * @param builtOver the squares whose track has been replaced
     */
    private void forgetBuiltOver(java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> builtOver)
    {
        if (builtOver == null || builtOver.isEmpty()) return;

        org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

        if (autonomy == null) return;

        if (autonomy.forgetTiles(builtOver)) this.editor.rememberAutonomy(autonomy);
    }

    /**
     * Copies lastComponent on the clipboard to the location designated by destLabel
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.execCopy`, which is `synchronized`; any
     * other caller must take it first.
     *
     * @param destLabel
     * @param move
     */
    void execCopy(LayoutLabel destLabel, boolean move)
    {        
        try
        {
            // We need to duplicate the component, otherwise its coordinates won't actually change
            LayoutDiagramComponent newComponent = new LayoutDiagramComponent(this.editor.lastComponent);
            newComponent.setX(this.editor.getX(destLabel));
            newComponent.setY(this.editor.getY(destLabel));

            // A new straight from the palette between two pieces of track lies along them (OB-292: *"when new straight
            // tracks are placed ... and they would connect two other tracks, they are automatically oriented to connect
            // rather than not."*).  A move is the same piece of track and a pasted copy keeps the orientation the user
            // gave it (OB-360).
            if (placingFromPalette())
            {
                newComponent.setOrientation(LayoutEditor.orientationThatJoins(this.editor.layout, newComponent,
                    this.editor.getX(destLabel), this.editor.getY(destLabel)));
            }

            if (newComponent.isText() && this.editor.layout.getEditHideText())
            {
                this.editor.toggleText();
            }
   
            if (move)
            {
                this.editor.layout.addComponent(null, this.editor.lastX, this.editor.lastY);
            }
            
            this.editor.layout.addComponent(newComponent, this.editor.getX(destLabel), this.editor.getY(destLabel));

            // Everything autonomy wrote about a square (station, name, facings, arrival restrictions, length, caption) is
            // keyed by the SQUARE, so a moved tile must carry it (below), or the next reconcile finds a station on a
            // square with no sensor and drops it.  Only on a MOVE: a copy does not copy the source's setup, since two
            // squares cannot both be one station.
            // A copy still has to clear the square it lands ON, which a move does through moveTile: a palette drop onto
            // a set-up station otherwise left the station describing a sensor that was gone, and reconcile cannot find
            // that because the square is not empty.
            if (!move)
            {
                org.traincontrol.automationui.AutonomySession landing = this.editor.parent.getAutonomySession();

                if (landing != null)
                {
                    // Only if it changed anything: this is reached on every single-tile paste and palette drop, and an
                    // unconditional write puts every file of the setup on disk (the layout folder is under OneDrive).
                    if (landing.forgetTiles(java.util.Collections.singletonList(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            this.editor.layout.getName(), this.editor.getX(destLabel), this.editor.getY(destLabel)))))
                    {
                        this.editor.rememberAutonomy(landing);
                    }
                }
            }

            if (move && this.editor.lastX >= 0 && this.editor.lastY >= 0)
            {
                org.traincontrol.automationui.AutonomySession autonomy =
                    this.editor.parent.getAutonomySession();

                if (autonomy != null)
                {
                    autonomy.moveTile(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            this.editor.layout.getName(), this.editor.lastX, this.editor.lastY),
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            this.editor.layout.getName(), this.editor.getX(destLabel), this.editor.getY(destLabel)));

                    this.editor.rememberAutonomy(autonomy);
                }
            }
            
            // Avoid clearing if we are placing new items
            if (this.editor.lastX != -1 || this.editor.lastY != -1)
            {
                this.editor.clearBordersFromChildren(this.editor.newComponents());
                
                if (move)
                {
                    this.editor.resetClipboard();
                    // reset tool now in clipboard
                }
            }
        }
        catch (IOException ex)
        {
            this.editor.parent.getModel().log(ex.getMessage());
            this.editor.parent.getModel().log(ex);
        }
                        
        // Re-highlight copied tile
        this.editor.clearBordersFromChildren(this.editor.grid.getContainer());
        
        this.editor.refreshGrid();
    }

    /**
     * Resets the contents of the clipboard
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.resetClipboard`, which is `synchronized`;
     * any other caller must take it first.
     */
    void resetClipboard()
    {
        this.editor.lastX = -1;
        this.editor.lastY = -1;
        this.editor.lastComponent = null;
        this.editor.toolFlag = null;

        // The group goes too: paste prefers it, so a stale group copy would hijack every later paste of a single tile.
        this.editor.groupClipboard = null;
        this.editor.clearBordersFromChildren(this.editor.newComponents());
    }

    /**
     * Whether a tile picked out of the palette is waiting to be placed.
     *
     * The palette's labels report -1,-1 because they are not on the grid.  A COPY armed from a diagram tile (Ctrl+C on
     * a square) is deliberately not this: pressing on track after copying a tile still picks that track up.
     *
     * @return true when the next click on a square should place rather than drag
     */
    boolean placingFromPalette()
    {
        return this.editor.toolFlag == LayoutEditor.tool.COPY && this.editor.lastX == -1 && this.editor.lastY == -1;
    }

    /**
     * Adds this label's location to the clipboard
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.initCopy`, which is `synchronized`; any
     * other caller must take it first.
     *
     * @param label
     * @param component - the component at the label location, or one that's specified
     * @param move
     */
    void initCopy(LayoutLabel label, LayoutDiagramComponent component, boolean move)
    {
        // Guarded on "nothing at all", no narrower.  Ctrl+X and Ctrl+C pass the hovered label unchecked, and it is null
        // while the pointer is over the palette; arming then left `lastComponent` null, so the paste threw and undo
        // gained an entry for an edit that never happened.  Two callers legitimately pass one half:
        //   the palette      a COMPONENT with a label whose `getX` is -1 (placingFromPalette is built on that state),
        //                    so a guard on the coordinates would refuse a working feature;
        //   a column drag    a real grid LABEL and no component, since a dragged column carries its blank squares too,
        //                    so a guard on the component would refuse it (testLayoutEditorBulkEdits).
        // No caller ever means both missing; that is reachable only from the two key branches.
        if (label == null && component == null) return;

        int x = this.editor.getX(label);
        int y = this.editor.getY(label);

        // Picking up a single tile puts the group down: paste prefers the group, and resetClipboard does not run on cut or
        // copy, so a stale group would otherwise be stamped over the layout.
        this.editor.groupClipboard = null;

        this.editor.lastX = x;
        this.editor.lastY = y;
        this.editor.pauseRepaint = false;

        if (component != null)
        {
            this.editor.lastComponent = component;
        }
        else
        {
            this.editor.lastComponent = this.editor.layout.getComponent(x, y);
        }

        this.editor.toolFlag = move ? LayoutEditor.tool.MOVE : LayoutEditor.tool.COPY;
        
        // For colored border highlight
        this.editor.clearBordersFromChildren(this.editor.grid.getContainer());
        
        this.editor.clearBordersFromChildren(this.editor.newComponents());
    }

    /**
     * Takes a copy of everything picked, as the rectangle it occupies.
     *
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.copySelection`, which is `synchronized`; any
     * other caller must take it first.
     * @return true if there was something to copy
     */
    boolean copySelection()
    {
        int[] bounds = this.editor.selection.bounds();

        if (bounds == null) return false;

        java.util.List<LayoutEditor.CarriedTile> taken = new java.util.ArrayList<>();

        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> origins =
            new java.util.ArrayList<>();

        try
        {
            for (int x = bounds[0]; x <= bounds[2]; x++)
            {
                for (int y = bounds[1]; y <= bounds[3]; y++)
                {
                    LayoutDiagramComponent lc = this.editor.layout.getComponent(x, y);

                    taken.add(new LayoutEditor.CarriedTile(x - bounds[0], y - bounds[1],
                        lc == null ? null : new LayoutDiagramComponent(lc)));

                    // Square for square with `taken`, including the empty ones - an empty square can
                    // still hold a caption, so it has a setup worth moving.
                    origins.add(new org.traincontrol.automationui.TileGraph.TileKey(
                        this.editor.layout.getName(), x, y));
                }
            }
        }
        catch (IOException ex)
        {
            this.editor.parent.getModel().log(ex);

            return false;
        }

        this.editor.groupClipboard = taken;
        this.editor.clipboardOrigins = origins;
        this.editor.clipboardCutSquares = null;
        this.editor.clipboardCutHadTiles = null;

        // A copy is not a move: the original keeps its setup, so pasting one only clears what it
        // builds over, exactly as it always did.
        this.editor.clipboardWasCut = false;

        return true;
    }

    /**
     * Where each cut square is landing, or null when what is on the clipboard was copied.
     *
     * A square that is not moving is left out.  Skipping it is right, but its consequence lives at the call site: every
     * square in a block shares one offset, so a paste back over the origin makes the map EMPTY, which is not a move
     * (see pasteSelection).
     *
     * @param atX the column the block is being pasted at
     * @param atY the row
     * @param wasCut whether the clipboard was cut, decided by the caller BEFORE it snapshotted - this
     *        must not be re-read from the field, which the snapshot has by then cleared
     * @return origin square to landing square, empty when there is nothing to carry
     */
    private java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
        org.traincontrol.automationui.TileGraph.TileKey> cutMoves(int atX, int atY, boolean wasCut,
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> vacated)
    {
        // The flag is passed in, not re-read: the caller decides before it snapshots, and a decision passed in cannot go
        // stale between being made and being used.
        if (!wasCut || vacated == null || this.editor.clipboardOrigins == null
            || this.editor.groupClipboard == null
            || this.editor.clipboardOrigins.size() != this.editor.groupClipboard.size())
        {
            return null;
        }

        java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
            org.traincontrol.automationui.TileGraph.TileKey> moves =
                new java.util.LinkedHashMap<>();

        for (int at = 0; at < this.editor.groupClipboard.size(); at++)
        {
            LayoutEditor.CarriedTile tile = this.editor.groupClipboard.get(at);

            org.traincontrol.automationui.TileGraph.TileKey to =
                new org.traincontrol.automationui.TileGraph.TileKey(
                    this.editor.layout.getName(), atX + tile.dx, atY + tile.dy);

            org.traincontrol.automationui.TileGraph.TileKey origin = this.editor.clipboardOrigins.get(at);

            // Only squares the cut actually emptied.  The clipboard covers a bounding box; the cut
            // emptied the picked squares inside it, and the rest still hold their track.
            if (this.editor.clipboardCutSquares != null && !this.editor.clipboardCutSquares.contains(origin))
            {
                continue;
            }

            // And only squares the cut left empty, on this page, asked of the caller before anything is placed (as the
            // flag is): pasteSelection places its tiles first, so an origin that is also a landing is full again by then,
            // and asking the layout would skip it and then forget it as "built over".  Asked per square, not assumed for
            // the whole clipboard, because any edit snapshots and would falsify a flag.
            // A setup is not carried ACROSS pages: paste snapshots only the destination page and arriveAt discards the
            // source page's undo history, so a cross-page move could not be undone.  It stays on the source page, and
            // pasting after coming back to that page picks it up.
            if (!vacated.contains(origin))
            {
                continue;
            }

            if (!to.equals(origin)) moves.put(origin, to);
        }

        return moves;
    }

    /**
     * Which of the cut squares are still empty, on this page.
     *
     * Taken by pasteSelection BEFORE it places anything: the paste refills any origin that is also a landing, and an
     * origin read as "full" after that would be skipped by cutMoves and then forgotten as built over.  The page test
     * is a fact about the world at the moment the gesture was made.
     *
     * @return the cut squares that are on this page and still empty, never null
     */
    private java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> emptyCutOrigins()
    {
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> empty =
            new java.util.LinkedHashSet<>();

        if (this.editor.clipboardCutSquares == null) return empty;

        // An undone cut is outstanding nowhere.  The set above is every picked square, occupied or not (an empty square
        // can hold a caption worth moving), and a square that was always empty reports empty whether the cut was undone
        // or not; a column selection is mostly such squares.  Undo is atomic: if any square the cut emptied OF A TILE
        // is full again, the cut has been put back and there is nothing for a paste to carry.  Conservative when a user
        // redrew one square by hand: the setup then stays where it is, which loses nothing.
        if (this.editor.clipboardCutHadTiles != null)
        {
            for (org.traincontrol.automationui.TileGraph.TileKey square : this.editor.clipboardCutHadTiles)
            {
                if (this.editor.layout.getName().equals(square.getPage())
                    && this.editor.layout.getComponent(square.getX(), square.getY()) != null)
                {
                    return empty;
                }
            }
        }

        for (org.traincontrol.automationui.TileGraph.TileKey square : this.editor.clipboardCutSquares)
        {
            if (this.editor.layout.getName().equals(square.getPage())
                && this.editor.layout.getComponent(square.getX(), square.getY()) == null)
            {
                empty.add(square);
            }
        }

        return empty;
    }

    /**
     * Pastes a copied group with its top left corner at a square, as ONE undoable step.
     *
     * OVERWRITES what is there: merging would mean deciding per square whether the copy or the original wins, and
     * overwriting is the answer the user can predict and undo takes back in one step.
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.pasteSelection`, which is `synchronized`;
     * any other caller must take it first.
     * @param atX the column to put the top left corner on
     * @param atY the row
     * @return true if anything was pasted
     */
    boolean pasteSelection(int atX, int atY)
    {
        if (!this.editor.hasGroupClipboard() || atX < 0 || atY < 0) return false;

        int width = 0;
        int height = 0;

        for (LayoutEditor.CarriedTile tile : this.editor.groupClipboard)
        {
            width = Math.max(width, tile.dx);
            height = Math.max(height, tile.dy);
        }

        // Squares, not pixels - see moveSelection
        if (atX + width >= this.editor.layout.getSx() || atY + height >= this.editor.layout.getSy())
        {
            javax.swing.JOptionPane.showMessageDialog(this.editor,
                I18n.t("layout.ui.errorPasteWouldLeaveTheDiagram"));

            return false;
        }

        // Both halves of the decision are taken before anything is placed and passed down, never re-read: the flag
        // because a guard consulting the field after the snapshot was false on every paste, and the vacated squares
        // because the placement loop below refills any origin that is also a landing (cut a column, paste it one
        // square down), which would then read as "not empty" and have its setup forgotten as built over.
        boolean wasCut = this.editor.clipboardWasCut;

        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> vacated = emptyCutOrigins();

        this.editor.snapshotLayout();

        // Every square this writes over, told to the setup once when the gesture is done (see forgetBuiltOver).
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> builtOver =
            new java.util.LinkedHashSet<>();

        boolean was = this.editor.pauseRepaint;

        this.editor.pauseRepaint = true;

        try
        {
            org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

            for (LayoutEditor.CarriedTile tile : this.editor.groupClipboard)
            {
                int x = atX + tile.dx;
                int y = atY + tile.dy;

                LayoutDiagramComponent placing = tile.component == null
                    ? null : new LayoutDiagramComponent(tile.component);

                if (placing != null)
                {
                    placing.setX(x);
                    placing.setY(y);
                }

                this.editor.layout.addComponent(placing, x, y);

                // Collected, not announced (see forgetBuiltOver).
                builtOver.add(new org.traincontrol.automationui.TileGraph.TileKey(
                    this.editor.layout.getName(), x, y));
            }

            // When the track was cut rather than copied, the setup follows it.  moveTiles is given what was built over as
            // well and does both jobs in one write, so calling forgetBuiltOver too would clear the setup just delivered.
            java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                org.traincontrol.automationui.TileGraph.TileKey> moves = wasCut
                    ? cutMoves(atX, atY, wasCut, vacated) : null;

            if (moves != null)
            {
                // A landing square is spared only if it got its own tile back: builtOver is every landing square, and
                // forgetting one whose setup is being carried onto it destroys what the move delivers (a block pasted
                // back where it came from would wipe a whole yard).  Per square and per index, not by subtracting the
                // origins wholesale: the origins are the bounding BOX and only the picked squares moved, so a never-cut
                // square inside the box would escape being forgotten while nothing moved onto it.
                java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> overwritten =
                    new java.util.LinkedHashSet<>(builtOver);

                for (int at = 0; at < this.editor.groupClipboard.size(); at++)
                {
                    org.traincontrol.automationui.TileGraph.TileKey landing =
                        new org.traincontrol.automationui.TileGraph.TileKey(this.editor.layout.getName(),
                            atX + this.editor.groupClipboard.get(at).dx,
                            atY + this.editor.groupClipboard.get(at).dy);

                    if (this.editor.clipboardOrigins != null && at < this.editor.clipboardOrigins.size()
                        && landing.equals(this.editor.clipboardOrigins.get(at)))
                    {
                        overwritten.remove(landing);
                    }
                }

                if (autonomy != null && (!moves.isEmpty() || !overwritten.isEmpty())
                    && autonomy.moveTiles(moves, overwritten))
                {
                    this.editor.rememberAutonomy(autonomy);
                }

                // Used up by a paste that actually moved something.  "Cut then paste twice is a move and then a copy" is
                // the rule.  cutMoves returns an EMPTY map, never null, when no origin is both cut and still empty (after
                // an undo has refilled them, or on another page), and an empty map is not a move: spending the cut on it
                // would send the next paste, the one that should carry the setup, to forgetBuiltOver on the origins.
                if (!moves.isEmpty()) this.editor.clipboardWasCut = false;
            }
            else
            {
                forgetBuiltOver(builtOver);
            }

            // What was just pasted becomes the selection, so it can be nudged into place
            this.editor.selection.clear();

            for (LayoutEditor.CarriedTile tile : this.editor.groupClipboard)
            {
                this.editor.selection.add(atX + tile.dx, atY + tile.dy);
            }
        }
        catch (IOException ex)
        {
            this.editor.parent.getModel().log(ex);
        }
        finally
        {
            this.editor.pauseRepaint = was;
        }

        this.editor.refreshGrid();

        this.editor.refreshSelectionBorders();

        return true;
    }

    /**
     * Moves every picked square by a delta, as ONE undoable step.
     *
     * Read everything first, then clear, then write: a group that overlaps where it is going (every short drag) would
     * otherwise erase tiles it had already placed.
     *
     * Refused WHOLE if any square would land off the diagram, rather than clipped: clipping would drop a column of
     * track off the side with nothing on screen saying so.
     *
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.moveSelection`, which is `synchronized`; any
     * other caller must take it first.
     * @param dx columns to move by
     * @param dy rows to move by
     * @return true if the move happened
     */
    boolean moveSelection(int dx, int dy)
    {
        if (this.editor.selection.isEmpty() || (dx == 0 && dy == 0)) return false;

        // The DIAGRAM's size in squares: LayoutGrid.maxWidth and maxHeight are PIXELS.  The move clears every source
        // square before writing, so a refusal that never fired left the group deleted by the first write past the edge.
        if (!this.editor.selection.fitsAfterMove(dx, dy, this.editor.layout.getSx(), this.editor.layout.getSy()))
        {
            javax.swing.JOptionPane.showMessageDialog(this.editor,
                I18n.t("layout.ui.errorSelectionWouldLeaveTheDiagram"));

            return false;
        }

        this.editor.snapshotLayout();

        boolean was = this.editor.pauseRepaint;

        this.editor.pauseRepaint = true;

        try
        {
            // Read
            java.util.List<org.traincontrol.base.TileSelection.At> from = this.editor.selection.all();
            java.util.List<LayoutDiagramComponent> carried = new java.util.ArrayList<>();

            for (org.traincontrol.base.TileSelection.At at : from)
            {
                LayoutDiagramComponent lc = this.editor.layout.getComponent(at.getX(), at.getY());

                carried.add(lc == null ? null : new LayoutDiagramComponent(lc));
            }

            org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

            // What autonomy holds about these squares moves with them, ALL of it and all at once, not per tile in the
            // loops below: a group dragged one square right has every source square landing on another source square,
            // so a per-tile move reads a store the previous iteration already wrote and the group eats itself.  The
            // whole setup, not the caption alone: station, name, facings, arrival restrictions, length and placement
            // are all keyed by SQUARE.
            java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                org.traincontrol.automationui.TileGraph.TileKey> moving =
                new java.util.LinkedHashMap<>();

            for (org.traincontrol.base.TileSelection.At at : from)
            {
                moving.put(
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        this.editor.layout.getName(), at.getX(), at.getY()),
                    new org.traincontrol.automationui.TileGraph.TileKey(
                        this.editor.layout.getName(), at.getX() + dx, at.getY() + dy));
            }

            // Clear
            for (org.traincontrol.base.TileSelection.At at : from)
            {
                this.editor.layout.addComponent(null, at.getX(), at.getY());
            }

            // Write
            for (int i = 0; i < from.size(); i++)
            {
                org.traincontrol.base.TileSelection.At at = from.get(i);

                int toX = at.getX() + dx;
                int toY = at.getY() + dy;

                LayoutDiagramComponent carrying = carried.get(i);

                if (carrying != null)
                {
                    carrying.setX(toX);
                    carrying.setY(toY);
                }

                this.editor.layout.addComponent(carrying, toX, toY);
            }

            // The setup follows the track, and only once the track has moved: moveTiles rebuilds the graph from the pages,
            // which until then hold every tile at its old square while the store already says the new one.  Written out
            // here too, since nothing else saves this session and the reset after a diagram edit throws it away.
            if (autonomy != null)
            {
                autonomy.moveTiles(moving);

                this.editor.rememberAutonomy(autonomy);
            }

            // The move is finished, so the selection is finished with: one left armed aims the next Delete or Control+X
            // at squares the user has stopped thinking about.
            this.editor.selection.clear();
        }
        catch (IOException ex)
        {
            this.editor.parent.getModel().log(ex);
        }
        finally
        {
            this.editor.pauseRepaint = was;
        }

        this.editor.refreshGrid();

        this.editor.refreshSelectionBorders();

        return true;
    }

    /**
     * Picks out a whole row, or adds it to what is already picked.
     *
     * Naming the row is exact where dragging a box across sixty squares, staying in the row all the way, is not.
     *
     * @param y the row
     */
    void selectRow(int y)
    {
        if (y < 0 || y >= this.editor.layout.getSy()) return;

        this.editor.selection.addRectangle(0, y, this.editor.layout.getSx() - 1, y);

        this.editor.refreshSelectionBorders();
    }

    /**
     * Picks out a whole column.
     *
     * @param x the column
     */
    void selectColumn(int x)
    {
        if (x < 0 || x >= this.editor.layout.getSx()) return;

        this.editor.selection.addRectangle(x, 0, x, this.editor.layout.getSy() - 1);

        this.editor.refreshSelectionBorders();
    }

    /**
     * Picks out everything on the page.
     */
    void selectAll()
    {
        this.editor.selection.addRectangle(0, 0, this.editor.layout.getSx() - 1, this.editor.layout.getSy() - 1);

        this.editor.refreshSelectionBorders();
    }

    /**
     * Copies the picked squares and then clears them, as one step.
     *
     * Copy first and only delete if it took, because a cut that lost the tiles and kept nothing on the clipboard is
     * the one outcome pasting cannot undo.
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.cutSelection`, which is `synchronized`; any
     * other caller must take it first.
     * @return true if anything was cut
     */
    boolean cutSelection()
    {
        if (this.editor.selection.isEmpty()) return false;

        if (!this.editor.copySelection()) return false;

        // Held across the delete and put back afterwards: delete() ends by resetting the clipboard, which would wipe the
        // copy taken one line above and leave a cut with nothing to paste.
        java.util.List<LayoutEditor.CarriedTile> carried = this.editor.groupClipboard;

        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> from = this.editor.clipboardOrigins;

        // Taken BEFORE the delete, which clears the selection, and covering only the picked squares -
        // the clipboard above covers their whole bounding box.
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> emptied =
            new java.util.LinkedHashSet<>();

        // And which of them had a tile on it, which is what says later whether the cut is still
        // outstanding.  Read here because after the delete every one of them is empty.
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> hadTiles =
            new java.util.LinkedHashSet<>();

        for (org.traincontrol.base.TileSelection.At at : this.editor.selection.all())
        {
            org.traincontrol.automationui.TileGraph.TileKey square =
                new org.traincontrol.automationui.TileGraph.TileKey(
                    this.editor.layout.getName(), at.getX(), at.getY());

            emptied.add(square);

            if (this.editor.layout.getComponent(at.getX(), at.getY()) != null) hadTiles.add(square);
        }

        // FALSE: the paste carries the setup, captions included, and forgetting them here would
        // delete the one part of it that is drawn on the diagram.
        boolean cut = this.editor.deleteSelection(false);

        this.editor.groupClipboard = carried;
        this.editor.clipboardOrigins = from;
        this.editor.clipboardCutSquares = emptied;
        this.editor.clipboardCutHadTiles = hadTiles;

        // Set after the delete, not before: delete() resets the clipboard and would clear the flag.  The paste that
        // follows is the move half of this gesture.
        this.editor.clipboardWasCut = cut;

        return cut;
    }

    /**
     * Puts the armed tile on every picked square, as ONE undoable step.
     *
     * Needs a tile armed from the palette; with nothing armed it says so rather than clearing the squares, because
     * "fill with nothing" is Delete and the two should not be one gesture with a hidden mode.
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.fillSelection`, which is `synchronized`; any
     * other caller must take it first.
     * @return true if anything was filled
     */
    boolean fillSelection()
    {
        if (this.editor.selection.isEmpty()) return false;

        if (!this.editor.hasToolFlag() || this.editor.lastComponent == null)
        {
            JOptionPane.showMessageDialog(this.editor, I18n.t("layout.ui.errorNothingToFillWith"));

            return false;
        }

        this.editor.snapshotLayout();

        // Every square this writes over, told to the setup once when the gesture is done (see forgetBuiltOver).
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> builtOver =
            new java.util.LinkedHashSet<>();

        boolean was = this.editor.pauseRepaint;

        this.editor.pauseRepaint = true;

        try
        {
            org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

            for (org.traincontrol.base.TileSelection.At at : this.editor.selection.all())
            {
                LayoutDiagramComponent placing = new LayoutDiagramComponent(this.editor.lastComponent);

                placing.setX(at.getX());
                placing.setY(at.getY());

                this.editor.layout.addComponent(placing, at.getX(), at.getY());

                // Collected, not announced - see pasteSelection
                builtOver.add(new org.traincontrol.automationui.TileGraph.TileKey(
                    this.editor.layout.getName(), at.getX(), at.getY()));
            }

            forgetBuiltOver(builtOver);
        }
        catch (IOException ex)
        {
            this.editor.parent.getModel().log(ex);
        }
        finally
        {
            this.editor.pauseRepaint = was;
        }

        this.editor.refreshGrid();

        this.editor.refreshSelectionBorders();

        return true;
    }

    /**
     * Deletes every picked square, as ONE undoable step.
     *
     * One snapshot for the group rather than one per square: a user who erases a yard and changes
     * their mind means the yard, not the last tile of it.
     *
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.deleteSelection`, which is `synchronized`;
     * any other caller must take it first.
     * @return true if anything was picked to delete
     */
    boolean deleteSelection()
    {
        return this.editor.deleteSelection(true);
    }

    /**
     * Empties every picked square.
     *
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.deleteSelection`, which is `synchronized`;
     * any other caller must take it first.
     * @param tellAutonomy whether the setup should forget what was on those squares: TRUE for a plain delete, FALSE for a
     *        CUT, where the paste that follows carries the setup and forgetting the captions here would destroy the one
     *        part of it drawn on the diagram (forgetCaptionsAt deletes the caption on the square AND every caption
     *        naming it)
     * @return true if anything was picked to delete
     */
    boolean deleteSelection(boolean tellAutonomy)
    {
        if (this.editor.selection.isEmpty()) return false;

        this.editor.snapshotLayout();

        // Suppressed so the per-tile delete does not take a snapshot of its own inside the group one
        boolean was = this.editor.pauseRepaint;

        this.editor.pauseRepaint = true;

        // Autonomy is told once, about the whole selection: every per-square delete rebuilds the tile graph over every
        // page.  The keys come from the SELECTION, not the labels: a label is a view of a square and the grid is
        // rebuilt on every repaint, so one held across the loop reports -1,-1 and the edit lands on the wrong square
        // or none.
        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> emptied =
            new java.util.ArrayList<>();

        try
        {
            for (org.traincontrol.base.TileSelection.At at : this.editor.selection.all())
            {
                LayoutLabel label = this.editor.grid.getValueAt(at.getX(), at.getY());

                if (label == null) continue;

                emptied.add(new org.traincontrol.automationui.TileGraph.TileKey(
                    this.editor.layout.getName(), at.getX(), at.getY()));

                this.editor.delete(label, false);
            }
        }
        finally
        {
            this.editor.pauseRepaint = was;
        }

        // ASKED ONLY WHEN IT WILL BE TOLD (OB-236): a cut is not, and asking builds the session where there is none
        org.traincontrol.automationui.AutonomySession autonomy =
            tellAutonomy && !emptied.isEmpty() ? this.editor.parent.getAutonomySession() : null;

        if (autonomy != null && autonomy.forgetTiles(emptied))
        {
            this.editor.rememberAutonomy(autonomy);
        }

        this.editor.clearSelection();

        this.editor.refreshGrid();

        return true;
    }

    /**
     * Rotates every picked square where it stands, as one undoable step.
     *
     * In place, not around the group: turning a yard through ninety degrees would move every tile of
     * it, which is a different operation and one nobody asked for.
     *
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.rotateSelection`, which is `synchronized`;
     * any other caller must take it first.
     * @return true if anything was picked to rotate
     */
    boolean rotateSelection()
    {
        if (this.editor.selection.isEmpty()) return false;

        this.editor.snapshotLayout();

        boolean was = this.editor.pauseRepaint;

        this.editor.pauseRepaint = true;

        try
        {
            for (org.traincontrol.base.TileSelection.At at : this.editor.selection.all())
            {
                LayoutDiagramComponent lc = this.editor.layout.getComponent(at.getX(), at.getY());

                if (lc == null) continue;

                lc.rotate();

                try
                {
                    this.editor.layout.addComponent(lc, at.getX(), at.getY());
                }
                catch (IOException ex)
                {
                    this.editor.parent.getModel().log(ex);
                }
            }
        }
        finally
        {
            this.editor.pauseRepaint = was;
        }

        this.editor.refreshGrid();

        this.editor.refreshSelectionBorders();

        return true;
    }

    /**
     * Deletes this label from the layout
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.delete`, which is `synchronized`; any other
     * caller must take it first.
     *
     * @param label 
     */
    void delete(LayoutLabel label)
    {
        this.editor.delete(label, true);
    }

    /**
     * Deletes this label from the layout.
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.delete`, which is `synchronized`; any other
     * caller must take it first.
     *
     * @param tellAutonomy false when the CALLER tells the setup what happened.  A cut empties its squares one at a time
     *        and the paste that follows tells the setup where the block went (deleteSelection); announcing each delete
     *        would say the track is gone while it is on its way elsewhere, and forgetCaptionsAt deletes captions the
     *        paste cannot bring back.
     */
    void delete(LayoutLabel label, boolean tellAutonomy)
    {
        LayoutDiagramComponent lc = this.editor.layout.getComponent(this.editor.getX(label), this.editor.getY(label));

        if (lc != null)
        {       
            try
            {
                if (!this.editor.pauseRepaint)
                {
                    this.editor.snapshotLayout();
                }

                this.editor.layout.addComponent(null, this.editor.getX(label), this.editor.getY(label));

                // What autonomy had written about the square goes with it, and so does any caption elsewhere naming it:
                // one left behind would name nothing and look like the label of the next tile put there.
                org.traincontrol.automationui.AutonomySession autonomy =
                    tellAutonomy ? this.editor.parent.getAutonomySession() : null;

                // The whole square, not just its caption (forgetTiles, as execCopy, pasteSelection, fillSelection and
                // clear do): forgetCaptionsAt left a deleted platform's station membership, point name, length, facing,
                // barred arrivals, signal pairing, blocked flag, portal, link name and `configurations.points` block
                // keyed to a square that holds nothing.  Only when something was forgotten, so deleting an empty square
                // writes nothing.  This is the single-square door: deleteSelection makes ONE forgetTiles call and
                // passes tellAutonomy=false to each per-square delete.
                if (autonomy != null && autonomy.forgetTiles(java.util.Collections.singletonList(
                        new org.traincontrol.automationui.TileGraph.TileKey(
                            this.editor.layout.getName(), this.editor.getX(label), this.editor.getY(label)))))
                {
                    this.editor.rememberAutonomy(autonomy);
                }

                this.editor.resetClipboard();
            }
            catch (IOException ex)
            {
                // Logged rather than shown as a dialog: the editor calls this per placement.
                this.editor.parent.getModel().log(ex);
            }

            this.editor.refreshGrid();
        }
    }

    /**
     * Rotates the specified label
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.rotate`, which is `synchronized`; any other
     * caller must take it first.
     *
     * @param label 
     */
    void rotate(LayoutLabel label)
    {       
        LayoutDiagramComponent lc = this.editor.layout.getComponent(this.editor.getX(label), this.editor.getY(label));
        
        if (lc != null)
        {    
            this.editor.snapshotLayout();

            lc.rotate();

            try
            {
                this.editor.layout.addComponent(lc, this.editor.grid.getCoordinates(label)[0], this.editor.grid.getCoordinates(label)[1]);
            }
            catch (IOException ex)
            {
                // Logged rather than shown as a dialog: the editor calls this per placement.
                this.editor.parent.getModel().log(ex);
            }

            this.editor.refreshGrid();
        }
    }
}
