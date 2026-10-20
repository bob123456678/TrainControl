package org.traincontrol.gui;

import java.io.IOException;
import javax.swing.JOptionPane;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.util.I18n;

/**
 * What the track editor does with the squares it is pointed at or has picked: the single-tile clipboard (copy, move
 * and the tool a palette tile arms), the group clipboard (copy, cut and paste), and moving, filling, deleting and
 * rotating a selection or one square (FR-136 Phase 4; the bodies are `LayoutEditor`'s, moved unchanged).
 *
 * The state stays on the editor: the selection, the clipboards, `pauseRepaint` and the layout are read and written
 * through `editor`, so a test that reflects on those fields finds them where it always did.  The editor's
 * `synchronized` methods stay on the editor and call in here, so the monitor is the editor's, as before; a body that
 * ran under it says so, and a call from here to one of them goes back through the editor so that the lock is taken
 * again (it is re-entrant) whatever the caller held.
 */
final class LayoutEditorSelection
{
    private final LayoutEditor editor;

    LayoutEditorSelection(LayoutEditor editor)
    {
        this.editor = editor;
    }

    /**
     * Tells the setup that these squares have been built over - once, for the whole gesture.
     *
     * Per square it was ruinous, and invisibly so.  Each call rebuilt the entire graph TWICE - moveTiles
     * calls touched(), which rebuilds, and then rebuilds again - and wrote the whole setup to disk,
     * every file of it, atomically.  Select all, copy, paste is three clicks and covers the bounding
     * box INCLUDING blank squares: on a sixty by thirty page that is eighteen hundred iterations, some
     * thirty-six hundred graph rebuilds and eighteen hundred whole-setup disk writes, on the event
     * thread, with repainting suppressed so nothing on screen moves while it happens.  The layout
     * folder is under OneDrive here, so each write may also wake a sync client.
     *
     * The shape moveSelection and the four shift operations use for their gestures: collect, tell once, and save
     * only if anything actually changed.
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

            // A STRAIGHT PUT DOWN BETWEEN TWO PIECES OF TRACK LIES ALONG THEM (OB-292; Adam, 2026-09-24: *"when new
            // straight tracks are placed ... and they would connect two other tracks, they are automatically oriented
            // to connect rather than not."*).  Only a tile newly placed from the palette: a move is the same piece of
            // track, and a pasted copy carries the way round the user gave it (Adam, OB-360: *"only turn the first time
            // the user places it, if it's already placed, don't change it."*).
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

            // Everything autonomy had written about that square goes with it.
            //
            // None of it is part of the diagram - the station designation, the name, the facings, the
            // arrival restrictions, the length, the caption - and every one of them is keyed by the
            // SQUARE, so moving the tile used to leave the lot behind on coordinates that now hold no
            // track.  The next reconcile then found a station on a square with no sensor and dropped
            // it, which is a setup destroyed by nudging a tile one square left.
            //
            // The caption alone used to follow, which was worse than nothing following: the name
            // moved and the station under it did not, so the diagram looked right and the setup was
            // in pieces.
            //
            // Only on a MOVE.  Copying a tile does not copy what was written about the square it
            // came from: two squares cannot both be one station.
            //
            // A copy still has to clear the square it lands ON, which a move does through moveTile.
            // Dropping a tile from the palette onto a set-up station used to replace the sensor and
            // leave the station, its name, its length and its facings behind, describing a sensor that
            // was no longer there - and reconcile cannot find that, because the square is not empty.
            if (!move)
            {
                org.traincontrol.automationui.AutonomySession landing = this.editor.parent.getAutonomySession();

                if (landing != null)
                {
                    // ONLY IF IT CHANGED ANYTHING.
                    //
                    // `delete` was fixed for exactly this and said why: "forgetCaptionsAt returns
                    // whether it changed anything, and this ignored it - so deleting a square that had
                    // no caption still wrote the whole setup to disk, every file of it."  Its sibling
                    // one method away was not swept, and this is the more expensive of the two: it is
                    // reached on every single-tile paste and every palette drop onto a blank square,
                    // and the layout folder is under OneDrive.
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

        // The group goes too.  It was never cleared anywhere, and paste prefers it - so ONE group copy
        // hijacked every later paste for the rest of the session: a user who cut a single tile and
        // pasted it got the old group back instead, spread over its whole bounding box, while the cut
        // tile stayed where it was.
        this.editor.groupClipboard = null;
        this.editor.clearBordersFromChildren(this.editor.newComponents());
    }

    /**
     * Whether a tile picked out of the palette is waiting to be placed.
     *
     * The clipboard says where it came from: initCopy records the label's square, and the palette's
     * labels are the ones that report -1,-1 because they are not on the grid at all.  A COPY armed
     * from a diagram tile - Ctrl+C on a square - is deliberately not this: pressing on track after
     * copying a tile should still pick that track up, which is what it has always done.
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
        // WORK OUT WHAT IS BEING PICKED UP BEFORE ANYTHING IS PUT DOWN.
        //
        // Control+X and Control+C pass `getLastHoveredLabel()` with no null check, and that is null
        // whenever the pointer is over the palette: `lastHoveredX/Y` are set back to -1 every time the
        // pointer leaves the grid, and `LayoutGrid.getValueAt` answers null for a negative coordinate.
        // The gesture then did three things, all wrong - discarded a group already on the clipboard,
        // set the coordinates to -1, and armed `toolFlag` with `lastComponent` null.
        //
        // Arming is what enables the right-click Paste item and what Control+V tests, so both then
        // reached `new LayoutDiagramComponent(null)`, which dereferences `original.type` on its first
        // line.  Through the menu that is caught and shown as a dialog with no message in it, and
        // `executeTool` has already taken a snapshot, so undo gains an entry for an edit that never
        // happened.
        //
        // GUARDED ON "NOTHING AT ALL", WHICH IS NARROWER THAN IT LOOKS.  Two other callers pass one
        // half of this legitimately and must keep working:
        //
        //   the palette      hands over a COMPONENT with a label whose `getX` is -1 on purpose -
        //                    `placingFromPalette()` is built on exactly that state - so a guard on the
        //                    coordinates would refuse a working feature;
        //   a column drag    hands over a real grid LABEL and no component, because a column being
        //                    dragged carries its blank squares too.  A guard on the component refuses
        //                    that, which is how this fix was first written and what
        //                    `testLayoutEditorBulkEdits` said about it.
        //
        // What no caller ever means is both: a null label carries no square, and with no component
        // there is nothing to arm the paste with.  That is only reachable from the two key branches.
        if (label == null && component == null) return;

        int x = this.editor.getX(label);
        int y = this.editor.getY(label);

        // Picking up a single tile puts the group down.
        //
        // Clearing it only in resetClipboard was not enough: that runs on Escape and on a click that
        // turns out not to be a drag, but NOT on cut or copy - and both paste paths prefer the group.
        // So copying a group, then later cutting one tile and pasting, stamped the old group's whole
        // bounding box over the layout and left the cut tile where it was.
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
        
        // Delete after pasting instead
        /* if (move)
        {
            delete(label);
        }*/
        
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
     * A square that is not moving is left out: it already holds its own setup, and moving a square
     * onto itself would be work with nothing to show for it.
     *
     * THAT SENTENCE USED TO END "at best wasted work", AND IT WAS THE WRONG REASON. Skipping
     * the identity case is right; what was wrong was the belief that skipping it cost nothing. Every
     * square in a block shares one offset, so a paste back over the origin makes the map EMPTY, and an
     * empty map used to fall to the branch that forgets what it built over - which in that case is the
     * whole block. The consequence of a skip lives at the call site, not here.
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
        // THE FLAG IS NOT RE-READ HERE.
        //
        // snapshotLayout used to stand the flag down, and pasteSelection snapshots before it gets this
        // far - so a guard that consulted the field was false on every paste, this returned null every
        // time, and the rest of the method was unreachable while every test stayed green,
        // because a source scan cannot see reachability.
        //
        // That stand-down is gone, so the field would answer correctly today.  The argument
        // stays: the caller has already decided, and a decision passed in cannot go stale between
        // being made and being used.
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

            // AND ONLY SQUARES THE CUT LEFT EMPTY, ON THIS PAGE.
            //
            // This is what the flag was standing in for, asked per square instead of assumed for the
            // whole clipboard.  Standing the flag down inside snapshotLayout, on the reasoning that
            // any edit could falsify it and listing the doors would leave the next one unswept, fails:
            // every edit snapshots, so cut, press "+" to make room, paste, and the move had quietly
            // become a delete-and-lose.
            //
            // The question is exact and free.  Undo refills the squares it refilled and no others; a
            // page switch changes which page this is and not which squares are empty; growing the
            // diagram changes neither.  Nothing has to be enumerated, and a square that is full again
            // is skipped on its own rather than taking the other nineteen with it.
            //
            // ASKED OF THE CALLER, NOT OF THE LAYOUT.  pasteSelection places its tiles before
            // it gets here, so an origin that is also a landing is full again by now - and asking the
            // layout at this point skipped it, after which it was forgotten as "built over".  The
            // caller reads the squares before it places anything, exactly as it reads the flag.
            //
            // The page half keeps a deliberate decision: a setup is not carried ACROSS
            // pages, because paste snapshots only the destination page and arriveAt has thrown away
            // the source page's undo history, so a cross-page move could not be undone.  Left on the
            // source page instead, where it is recoverable - and where coming BACK to that page and
            // pasting now picks it up correctly, which the flag could not do.
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
     * Taken by pasteSelection BEFORE it places anything, because the paste refills any origin that is
     * also a landing - and an origin read as "full" after that is skipped by cutMoves and then
     * forgotten as built over, which destroys the setup the cut was carrying.
     *
     * The page test is here rather than in cutMoves for the same reason it is asked at all: it is a
     * fact about the world at the moment the gesture was made.
     *
     * @return the cut squares that are on this page and still empty, never null
     */
    private java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> emptyCutOrigins()
    {
        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> empty =
            new java.util.LinkedHashSet<>();

        if (this.editor.clipboardCutSquares == null) return empty;

        // AN UNDONE CUT IS OUTSTANDING NOWHERE, NOT MOSTLY.
        //
        // The set above is every picked square, occupied or not - deliberately, because an empty
        // square can still hold a caption worth moving.  But a square that was ALWAYS empty answers
        // "I am empty" whether the cut has been undone or not, and a column selection is mostly such
        // squares.  So a cut column, undone, still had fifteen of its sixteen squares saying the cut
        // was outstanding: the move map came back with fifteen entries, the paste was read as the
        // move, and the setup on the one square that had any track was left behind - and then
        // forgotten by the paste that put the block back.
        //
        // Undo is atomic.  If any square the cut emptied OF A TILE is full again, the cut has been put
        // back, and there is nothing outstanding for a paste to carry.  Conservative where a user has
        // redrawn one square by hand rather than undoing: the setup then stays where it is, which is
        // the answer that loses nothing.
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
     * OVERWRITES what is there.  Merging would mean deciding, per square, whether the copy or the
     * original wins - and either answer is wrong half the time.  Overwriting is at least the answer
     * the user can predict, and undo takes it back in one step.
     *
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

        // BOTH HALVES OF THE DECISION ARE TAKEN HERE, before anything is placed.
        //
        // The flag is read here and passed down as an argument, never re-read, because a
        // guard consulting the field after the snapshot was false on every paste.
        //
        // The squares are read here for the same reason and it is easy to miss:
        // cutMoves asking "is this origin still empty?", which is the right question asked too late:
        // the placement loop below runs first, so an origin that is also a LANDING has just been
        // refilled by this very paste. Its setup was then skipped as "not empty" and forgotten as
        // "built over" - destroyed, where it should have moved. Cut a column and paste it
        // one square down and that is every square on it.
        //
        // A decision about the state before the work has to be taken before the work.
        boolean wasCut = this.editor.clipboardWasCut;

        java.util.Set<org.traincontrol.automationui.TileGraph.TileKey> vacated = emptyCutOrigins();

        this.editor.snapshotLayout();

        // Every square this writes over, told to the setup ONCE when the gesture is done - see
        // forgetBuiltOver for what per-square cost
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

                // Collected, not announced.  See below the loop for why.
                builtOver.add(new org.traincontrol.automationui.TileGraph.TileKey(
                    this.editor.layout.getName(), x, y));
            }

            // THE SETUP FOLLOWS THE TRACK, when the track was cut rather than copied.
            //
            // moveTiles is given what was built over as well, so it does both jobs in one write: the
            // landing squares are cleared of whatever they held and the carried setup lands on them.
            // Calling forgetBuiltOver as well would clear the setup this has just delivered.
            java.util.Map<org.traincontrol.automationui.TileGraph.TileKey,
                org.traincontrol.automationui.TileGraph.TileKey> moves = wasCut
                    ? cutMoves(atX, atY, wasCut, vacated) : null;

            if (moves != null)
            {
                // A LANDING SQUARE IS SPARED ONLY IF IT GOT ITS OWN TILE BACK.
                //
                // builtOver is every landing square. Forgetting a square whose own setup is being
                // carried onto it destroys what the move is delivering - paste a block back exactly
                // where it came from and EVERY square is one of those, which is how a cut and put back
                // wiped a whole yard.
                //
                // Subtracting the origins wholesale was too broad, and leaked: the origins are
                // the bounding BOX and only the picked squares moved, so a landing square inside the
                // box that was never cut escaped being forgotten while nothing moved onto it - its
                // track replaced and its station, length and facings surviving, describing a tile that
                // is gone.
                //
                // The question is per square and per index: did THIS square receive the tile that was
                // already on it? Nothing else spares it.
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

                // Used up BY A PASTE THAT ACTUALLY MOVED SOMETHING.
                //
                // "Cut then paste twice is a move and then a copy" is still the rule, and it is what
                // the flag is for.  What was wrong is which paste counts as the move: this cleared it
                // for any paste that got here, and getting here means only that cutMoves returned a
                // map.  It returns an EMPTY one - never null - whenever no origin is both cut and
                // still empty: after an undo has refilled them, or on another page, where the setup is
                // deliberately left behind to be picked up on the way back (see cutMoves).
                //
                // So a paste that carried nothing spent the cut, and the next paste - the one that
                // would have carried it - fell into the else below and called forgetBuiltOver on the
                // origins.  Pasting a block back where it came from then DESTROYED its setup instead
                // of restoring it, which is the loss arriving through a door this rewrite opened.
                //
                // An empty map is not a move.  The per-square question in cutMoves has already decided
                // that, and this line now agrees with it.
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
     * Read everything first, then clear, then write.  A group that overlaps where it is going - which
     * is every short drag - would otherwise erase tiles it had already placed: move a row one square
     * right, and clearing the second source square deletes the tile just written there from the first.
     *
     * Refused WHOLE if any square would land off the diagram, rather than clipped.  Clipping would
     * drop a column of track off the side with nothing on screen saying so.
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

        // The DIAGRAM's size in squares.  LayoutGrid.maxWidth and maxHeight are PIXELS - width times
        // tile size - so passing them here compared a column number against a pixel count and the
        // refusal never fired.  Since the move clears every source square before writing any
        // destination, the first write past the edge threw with the group already deleted.
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

            // What autonomy holds about these squares moves with them, ALL of it and all at once.
            //
            // Not per tile inside the loops below.  A group dragged one square right has every source
            // square landing on another source square, so a per-tile move reads a store that the
            // previous iteration has already written - which made the group eat itself: two set-up
            // squares side by side, dragged right, and the first move overwrote the second square
            // before the second came to read it.  Dragging LEFT happened to work, which made it look
            // intermittent rather than wrong.
            //
            // And the whole setup, not the caption alone.  The station designation, the name, the
            // facings, the arrival restrictions, the length and the placement are all keyed by
            // SQUARE, so a moved tile used to leave every one of them behind on coordinates that now
            // hold no track - and the next reconcile dropped the station for good.
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

            // The setup follows the track, and only once the track has actually moved.
            //
            // This ran BEFORE the two loops above, which was wrong in a way that took a corrupted
            // layout to notice: moveTiles rebuilds the graph from the pages, and at that moment the
            // pages still had every tile at its old square while the store had already been told the
            // stations were at their new ones.  The graph was therefore built from a diagram and a
            // setup that disagreed about every square in the selection - and the result of that was
            // saved.
            //
            // Written out here too, for the same reason: nothing else saves this session, and the
            // reset that follows an edit to a diagram throws it away.
            if (autonomy != null)
            {
                autonomy.moveTiles(moving);

                this.editor.rememberAutonomy(autonomy);
            }

            // The move is finished, so the selection is finished with.
            //
            // It used to travel with the tiles, on the reasoning that a group could then be dragged
            // twice - and dragging is the only thing that calls this, so that was the whole of the
            // argument.  Adam asked for the opposite after using it: a selection that outlives its
            // move is a selection still armed when the user has moved on, and the next Delete or
            // Control+X is aimed at squares they stopped thinking about.  Picking again is three
            // clicks; noticing that twenty squares are still picked is luck.
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
     * Dragging a box works, and on a diagram sixty squares wide dragging one accurately across all
     * sixty is its own small ordeal - the pointer has to stay in the row the whole way. Naming the
     * row is exact, takes one click, and cannot go one square wrong.
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
     * The verb the group was missing.  Copy, paste, rotate, fill and delete were all there and cut
     * was not, so moving a run of track to another part of the diagram meant copying it, pasting it,
     * and going back to delete the originals - three actions for one idea, with the third the easy
     * one to forget and the one that leaves a duplicate railway behind.
     *
     * Copy first and only delete if it took, because a cut that lost the tiles and kept nothing on
     * the clipboard is the one outcome that cannot be undone by pasting.
     *
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.cutSelection`, which is `synchronized`; any
     * other caller must take it first.
     * @return true if anything was cut
     */
    boolean cutSelection()
    {
        if (this.editor.selection.isEmpty()) return false;

        if (!this.editor.copySelection()) return false;

        // Held across the delete and put back afterwards.
        //
        // delete() ends by resetting the clipboard - it has to, because the single-tile clipboard it
        // shares would otherwise go on offering a tile that has been removed - and that wiped the copy
        // taken one line above.  A cut therefore took the track away and left nothing to paste, which
        // is the one outcome this method's own comment says it was written to avoid.
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

        // SET AFTER THE DELETE, not before: delete() ends by resetting the clipboard, which is the
        // reason the list above has to be held across it, and a flag set first would be cleared with
        // it.  The paste that follows is the move half of this gesture.
        this.editor.clipboardWasCut = cut;

        return cut;
    }

    /**
     * Puts the armed tile on every picked square, as ONE undoable step.
     *
     * The verb the selection was missing.  Copy, paste, rotate and delete were all there, and the most
     * ordinary thing anybody wants a row of squares for - laying a run of straight track, or turning a
     * line of plain track into a row of nothing - had to be done a square at a time, which is the
     * thing the selection exists to avoid.
     *
     * Needs a tile armed from the palette; with nothing armed there is nothing to put down, and it
     * says so rather than clearing the squares, because "fill with nothing" is Delete and the two
     * should not be one gesture with a hidden mode.
     *
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

        // Every square this writes over, told to the setup ONCE when the gesture is done - see
        // forgetBuiltOver for what per-square cost
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
     * @param tellAutonomy whether the setup should forget what was on those squares. TRUE for a plain
     *        delete, where the track is going and its name should go with it. FALSE for a CUT, where
     *        the paste that follows carries the setup to wherever the block lands - and where
     *        forgetting the captions here destroys the one part of it that is drawn on the diagram,
     *        because forgetCaptionsAt deletes the caption on the square AND every caption naming it.
     * @return true if anything was picked to delete
     */
    boolean deleteSelection(boolean tellAutonomy)
    {
        if (this.editor.selection.isEmpty()) return false;

        this.editor.snapshotLayout();

        // Suppressed so the per-tile delete does not take a snapshot of its own inside the group one
        boolean was = this.editor.pauseRepaint;

        this.editor.pauseRepaint = true;

        // AUTONOMY IS TOLD ONCE, ABOUT THE WHOLE SELECTION.
        //
        // Every per-square delete used to tell it separately, and each of those calls rebuilds the tile
        // graph over every page - so a rubber-band delete over thirty squares paid for thirty full
        // rebuilds, and, before the write was gated, thirty writes of every file of the setup.
        //
        // The keys come from the SELECTION rather than from the labels, because a label is a view of a
        // square and the grid is rebuilt on every repaint: one held across the loop reports -1,-1 and
        // the edit lands on the wrong square, or silently on none.
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
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.delete`, which is `synchronized`; any other
     * caller must take it first.
     *
     * @param tellAutonomy false when the CALLER is going to tell the setup what happened
     *
     * A cut empties its squares one at a time, and the paste that follows tells the setup where the block
     * went (deleteSelection).  If each of those deletes had already announced itself, the announcement
     * would be wrong: it says the track is gone, and the track is on its way somewhere else.  The captions
     * are the visible half of that - forgetCaptionsAt deletes the caption on the square and every caption
     * naming it, which the paste cannot bring back - and it is why this parameter exists rather than the
     * callers reaching past delete().  It was written for the whole-row and whole-column moves, since removed.
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

                // What autonomy had written about that square goes with it.  A caption on the square,
                // and any caption elsewhere naming it: both are about track that no longer exists.
                // Left behind, the label stayed where it was naming nothing, with no way to remove it
                // - and putting any tile back on that square made it look like the new tile's label.
                org.traincontrol.automationui.AutonomySession autonomy =
                    tellAutonomy ? this.editor.parent.getAutonomySession() : null;

                // THE WHOLE SQUARE, NOT ITS CAPTION.
                //
                // This called `forgetCaptionsAt`, which touches the captions map and nothing else, so a
                // deleted platform kept its station membership, point name, length, facing, barred
                // arrivals, signal pairing, blocked flag, portal, link name and its
                // `configurations.points` block - all of it keyed to a square that now holds nothing.
                //
                // Every sibling gesture already forgets the lot: `execCopy`, `pasteSelection`,
                // `fillSelection` and `clear` all reach `forgetTiles`.  And `clear`'s own comment says
                // this one did too - *"Deleting one square tells the setup so; emptying the whole page
                // told it nothing at all"* - which is the sentence that makes this an oversight rather
                // than a decision.  Its argument holds here word for word: a reconciling save would
                // prune the orphans eventually, and the non-reconciling write on the way out commits
                // them to disk first.
                //
                // Only when something was actually forgotten.  The return value used to be ignored, so
                // deleting a square that had nothing on it still wrote the whole setup to disk, every
                // file of it.
                //
                // This is the SINGLE-SQUARE door.  The sentence here used to be "deleting a
                // selection is one call per square", which was the cost argument for the check above -
                // and `deleteSelection` stopped working that way in the same round it was written:
                // it collects the whole selection into `emptied`, makes ONE `forgetTiles` call, and
                // passes `tellAutonomy=false` to each per-square delete.  So the saving here is one
                // write per stray single delete rather than one per square of a selection, and the
                // check is still worth having for it.
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
                // A tile edit that fails should say so.  This was silent, so the component simply did
                // not appear and nothing explained why.  Logged rather than shown as a dialog: the
                // editor calls this per placement, and a dialog per failed tile would be worse.
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
                // A tile edit that fails should say so.  This was silent, so the component simply did
                // not appear and nothing explained why.  Logged rather than shown as a dialog: the
                // editor calls this per placement, and a dialog per failed tile would be worse.
                this.editor.parent.getModel().log(ex);
            }

            this.editor.refreshGrid();
        }
    }
}
