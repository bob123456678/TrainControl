package org.traincontrol.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.traincontrol.base.LayoutDiagramComponent;

/**
 * The track editor's undo and redo: taking a snapshot of the page and of its station captions before an edit, and
 * stepping back and forward through them (FR-136 Phase 4; the bodies are `LayoutEditor`'s, moved unchanged).
 *
 * The four stacks stay on the editor (`previousLayoutComponents`, `previousLayoutComponentsRedo`, `previousCaptions`,
 * `previousCaptionsRedo`), because a test reflects on them and `autonomyLocomotiveRenamed` repairs the snapshots on
 * them.  `snapshotLayout`, `undo` and `redo` are `synchronized` on the editor and stay so there; their bodies here run
 * under that monitor and take no lock of their own, and nothing else here was ever `synchronized`.
 */
final class LayoutEditorUndo
{
    private final LayoutEditor editor;

    LayoutEditorUndo(LayoutEditor editor)
    {
        this.editor = editor;
    }

    /**
     * What the setup currently says about this page's captions.
     *
     * @return caption square to station square, or an empty map when there is no setup to ask
     */
    private java.util.Map<String, Object> captionSnapshot()
    {
        org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

        return autonomy == null
            ? new java.util.LinkedHashMap<String, Object>()
            : autonomy.snapshotPage(this.editor.layout.getName());
    }

    /**
     * Puts this page's captions back as a snapshot found them.
     *
     * @param captions a snapshot, or null to do nothing - a null means the stacks disagreed, and
     *        leaving the captions alone is a better answer than clearing them
     */
    private void restoreCaptions(java.util.Map<String, Object> captions)
    {
        if (captions == null) return;

        org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

        if (autonomy == null) return;

        autonomy.restorePage(this.editor.layout.getName(), captions);

        // AND NOTHING OUTSIDE THE PAGE (RC-B1).
        //
        // A snapshot taken before a shrink holds captions on the row and column the shrink then
        // removed, and an undo does not give those back - the page SIZE is not part of an undo entry,
        // and a shrink is only allowed when that edge holds no track, so no component pins it back
        // either. Restored blind, the name returns to a square that no longer exists: present, never
        // drawn, and with nowhere to click to remove it. That is exactly the state LE-B1 was raised
        // for, reached through undo instead of through the shrink.
        //
        // A caption outside the page is never valid, whatever put it there, so it is enforced here
        // rather than by teaching undo about page size - which would change what an undo entry is, on
        // the path every edit in this editor goes through.
        forgetCaptionsOutsideThePage();

        // Straight to disk, for the same reason the move itself goes straight to disk - see
        // rememberAutonomy.  An undo that only reached memory would be forgotten by the reset that
        // follows the next edit, and the thing it undid would come back.
        //
        // AND THE ANSWER IS READ, as its sibling reads it (ACC-C6).  This discarded the boolean, so a
        // write that failed at exactly this moment left the undo not sticking and said nothing - and
        // disk is authoritative after the session rebuild, so what comes back is the thing that was
        // undone.  `rememberAutonomy` has logged this since it was written; this door did not.
        if (!autonomy.saveQuietly() && this.editor.parent.getModel() != null)
        {
            this.editor.parent.getModel().logf("autosetup.log.saveFailedAfterUndo");
        }
    }

    /**
     * Drops any caption whose square is off the page (RC-B1).
     *
     * Reuses the sweep the shrink itself does, widened from the edge to everything beyond it: after a
     * shrink and an undo, a caption can sit anywhere outside the current bounds, not only on the row
     * that was trimmed.
     */
    private void forgetCaptionsOutsideThePage()
    {
        org.traincontrol.automationui.AutonomySession autonomy = this.editor.parent.getAutonomySession();

        if (autonomy == null) return;

        java.util.List<org.traincontrol.automationui.TileGraph.TileKey> gone = new java.util.ArrayList<>();

        for (org.traincontrol.automationui.TileGraph.TileKey tile
            : autonomy.captionsOnPage(this.editor.layout.getName()).keySet())
        {
            if (tile.getX() >= this.editor.layout.getSx() || tile.getY() >= this.editor.layout.getSy()
                || tile.getX() < 0 || tile.getY() < 0)
            {
                gone.add(tile);
            }
        }

        boolean changed = false;

        for (org.traincontrol.automationui.TileGraph.TileKey tile : gone)
        {
            changed |= autonomy.forgetCaptionsAt(tile);
        }

        if (changed) this.editor.rememberAutonomy(autonomy);
    }

    /**
     * Checks if there is any history to undo
     * @return 
     */
    boolean canUndo()
    {
        return !this.editor.previousLayoutComponents.isEmpty();
    }

    /**
     * Checks if there is any history to redo
     * @return 
     */
    boolean canRedo()
    {
        return !this.editor.previousLayoutComponentsRedo.isEmpty();
    }

    /**
     * Creates a copy of the current layout
     * @return 
     */
    private List<LayoutDiagramComponent> deepCopyLayout()
    {
        List<LayoutDiagramComponent> history = new ArrayList<>();
        
        for (LayoutDiagramComponent lc : this.editor.layout.getAll())
        {
            try
            {
                history.add(new LayoutDiagramComponent(lc));
            }
            catch (IOException ex)
            {
                this.editor.parent.getModel().log(ex);
            }
        }
        
        return history;
    }

    /**
     * Saves a previous version of the layout
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.snapshotLayout`, which is `synchronized`;
     * any other caller must take it first.
     */
    void snapshotLayout()
    {
        // A PENDING CUT IS NO LONGER STOOD DOWN HERE (RC-A1, replacing LE-A6).
        //
        // clipboardWasCut asserts that the squares the clipboard came from are EMPTY, and LE-A6
        // asserted that invariant in this method because every edit that can change the diagram
        // snapshots first - so no door that could falsify it had to be listed.
        //
        // That was true and far too wide.  The claim is about EACH square, and standing the whole
        // clipboard down gives it up for all of them: cut a set-up yard, press "+" to make room, paste
        // it, and the move had become a copy with the setup abandoned on the squares the cut emptied.
        // Growing the diagram moves nothing and falsifies nothing.
        //
        // cutMoves asks the real question instead, per origin square, at the moment it matters.  It
        // needs no list of doors either, and it does not punish nineteen innocent squares for the one
        // that was refilled.
        
        // Enforce size limit
        if (this.editor.previousLayoutComponents.size() >= LayoutEditor.MAX_UNDO_HISTORY)
        {
            this.editor.previousLayoutComponents.removeLast();
        }
                
        this.editor.previousLayoutComponents.push(deepCopyLayout());
        this.editor.previousCaptions.push(captionSnapshot());

        while (this.editor.previousCaptions.size() > this.editor.previousLayoutComponents.size())
        {
            this.editor.previousCaptions.removeLast();
        }

        this.editor.previousLayoutComponentsRedo.clear();
        this.editor.previousCaptionsRedo.clear();
    }

    /**
     * Restores previous layout state
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.undo`, which is `synchronized`; any other
     * caller must take it first.
     */
    void undo()
    {
        // A CUT THAT HAS BEEN UNDONE IS NO LONGER A MOVE (LE-A3), and cutMoves is where that is
        // settled now (RC-A1).
        //
        // clipboardWasCut means "the squares these tiles came from are empty now, so the setup should
        // follow the paste", and stepping through history can make that false: cut, Ctrl+Z - the track
        // and its setup come back - then paste, and the paste would move the setup off the restored
        // originals onto the copy, stripping squares that still visibly hold track.  Worse than the
        // defect LE-A1 fixed, where the setup was at least orphaned on squares that really were empty.
        //
        // Standing the flag down here said that about every square on the clipboard.  cutMoves asks
        // each square whether it is still empty, so the ones this restored are skipped and the ones it
        // did not restore are still carried - and redo, which empties them again, needs no undoing of
        // an undoing.
        //
        // The tiles stay on the clipboard either way: pasting them is still a perfectly good copy.

        try
        {     
            if (!this.editor.previousLayoutComponents.isEmpty())
            {         
                List<LayoutDiagramComponent> history = this.editor.previousLayoutComponents.pop();
                this.editor.previousLayoutComponentsRedo.push(deepCopyLayout());

                // The names on the squares travel with the squares.
                //
                // A caption belongs to the SETUP rather than to the tile - that is what stops a rename
                // rewriting every page - so it does not ride the component snapshot.  But this editor
                // moves and deletes captions as it moves and deletes tiles, so without this Ctrl+Z
                // brought a deleted platform back with no name on it, or moved a tile back and left
                // its name at the square it had been dragged to.
                java.util.Map<String, Object> captionsBefore = captionSnapshot();

                java.util.Map<String, Object> captionsToRestore =
                    this.editor.previousCaptions.isEmpty() ? null : this.editor.previousCaptions.pop();

                this.editor.previousCaptionsRedo.push(captionsBefore);
                
                // Delete all existing components
                for (LayoutDiagramComponent lc : this.editor.layout.getAll())
                {
                    this.editor.layout.addComponent(null, lc.getX(), lc.getY());
                }

                // Placed previous components
                for (LayoutDiagramComponent lc : history)
                {
                    this.editor.layout.addComponent(lc, lc.getX(), lc.getY());
                }

                // The setup is put back AFTER the diagram, not before it.
                //
                // restoreCaptions rebuilds the graph from the pages, so doing it first derived a graph
                // from the pre-undo diagram against the post-undo setup - the two disagreeing about
                // every square the undo touched - and nothing rebuilt afterwards.  It is the same
                // ordering moveSelection and the four shift operations were fixed for, and the comment
                // there ("it took a corrupted layout to notice") applies word for word.
                restoreCaptions(captionsToRestore);

                this.editor.refreshGrid();
            }
        }
        catch (IOException ex)
        {
            this.editor.parent.getModel().log(ex);
        }
    }

    /**
     * Restores previous layout state
     *
     * Runs under the editor's monitor: reached only through `LayoutEditor.redo`, which is `synchronized`; any other
     * caller must take it first.
     */
    void redo()
    {
        // A CUT THAT HAS BEEN UNDONE IS NO LONGER A MOVE (LE-A3), and cutMoves is where that is
        // settled now (RC-A1).
        //
        // clipboardWasCut means "the squares these tiles came from are empty now, so the setup should
        // follow the paste", and stepping through history can make that false: cut, Ctrl+Z - the track
        // and its setup come back - then paste, and the paste would move the setup off the restored
        // originals onto the copy, stripping squares that still visibly hold track.  Worse than the
        // defect LE-A1 fixed, where the setup was at least orphaned on squares that really were empty.
        //
        // Standing the flag down here said that about every square on the clipboard.  cutMoves asks
        // each square whether it is still empty, so the ones this restored are skipped and the ones it
        // did not restore are still carried - and redo, which empties them again, needs no undoing of
        // an undoing.
        //
        // The tiles stay on the clipboard either way: pasting them is still a perfectly good copy.

        try
        {     
            if (!this.editor.previousLayoutComponentsRedo.isEmpty())
            {         
                List<LayoutDiagramComponent> history = this.editor.previousLayoutComponentsRedo.pop();
                this.editor.previousLayoutComponents.push(deepCopyLayout());

                // The captions go forward with it, exactly as they came back
                java.util.Map<String, Object> captionsBefore = captionSnapshot();

java.util.Map<String, Object> captionsToRestore = this.editor.previousCaptionsRedo.isEmpty()
                    ? null : this.editor.previousCaptionsRedo.pop();

                this.editor.previousCaptions.push(captionsBefore);
                
                // Enforce undo limit
                if (this.editor.previousLayoutComponents.size() >= LayoutEditor.MAX_UNDO_HISTORY)
                {
                    this.editor.previousLayoutComponents.removeLast();
                }

                while (this.editor.previousCaptions.size() > this.editor.previousLayoutComponents.size())
                {
                    this.editor.previousCaptions.removeLast();
                }
                
                // Delete all existing components
                for (LayoutDiagramComponent lc : this.editor.layout.getAll())
                {
                    this.editor.layout.addComponent(null, lc.getX(), lc.getY());
                }

                // Placed previous components
                for (LayoutDiagramComponent lc : history)
                {
                    this.editor.layout.addComponent(lc, lc.getX(), lc.getY());
                }

                // The setup after the diagram - see undo() above for why
                restoreCaptions(captionsToRestore);

                this.editor.refreshGrid();
            }
        }
        catch (IOException ex)
        {
            this.editor.parent.getModel().log(ex);
        }
    }
}
