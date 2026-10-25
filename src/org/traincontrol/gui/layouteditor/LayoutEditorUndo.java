package org.traincontrol.gui.layouteditor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.traincontrol.base.LayoutDiagramComponent;

/**
 * The track editor's undo and redo: taking a snapshot of the page and of its station captions before an edit, and
 * stepping back and forward through them (FR-136 Phase 4).
 *
 * The four stacks stay on the editor (`previousLayoutComponents`, `previousLayoutComponentsRedo`, `previousCaptions`,
 * `previousCaptionsRedo`), because a test reflects on them and `autonomyLocomotiveRenamed` repairs the snapshots on
 * them.  `snapshotLayout`, `undo` and `redo` are `synchronized` on the editor and stay so there; their bodies here run
 * under that monitor and take no lock of their own, and nothing else here is `synchronized`.
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

        // And nothing outside the page: a snapshot taken before a shrink holds captions on the row and column the shrink
        // removed, and undo does not give those back (the page SIZE is not part of an undo entry).  Restored blind, the
        // name would return to a square that no longer exists: present, never drawn, with nowhere to click to remove
        // it.  A caption outside the page is never valid, so it is enforced here rather than by teaching undo about page
        // size, which would change what an undo entry is on the path every edit goes through.
        forgetCaptionsOutsideThePage();

        // Straight to disk, as the move itself is (see rememberAutonomy): an undo that only reached memory would be
        // forgotten by the reset after the next edit, and the thing it undid would come back.  The answer is read, as
        // rememberAutonomy reads it: disk is authoritative after the session rebuild, so a failed write would make the
        // undo not stick, silently.
        if (!autonomy.saveQuietly() && this.editor.parent.getModel() != null)
        {
            this.editor.parent.getModel().logf("autosetup.log.saveFailedAfterUndo");
        }
    }

    /**
     * Drops any caption whose square is off the page.
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
        // A pending cut is not stood down here.  clipboardWasCut says the squares the clipboard came from are EMPTY, a
        // claim about EACH square, and cutMoves asks it per origin square at the moment it matters (cut a set-up yard,
        // press "+" to make room, paste: the move must still be a move).
        
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
        // A cut that has been undone is no longer a move, and cutMoves settles that per square: stepping through history
        // can restore the squares the clipboard came from (cut, Ctrl+Z, paste), and moving the setup off the restored
        // originals would strip squares that still hold track.  Redo empties them again, so there is nothing to undo of
        // an undoing.  The tiles stay on the clipboard either way; pasting them is still a good copy.

        try
        {     
            if (!this.editor.previousLayoutComponents.isEmpty())
            {         
                List<LayoutDiagramComponent> history = this.editor.previousLayoutComponents.pop();
                this.editor.previousLayoutComponentsRedo.push(deepCopyLayout());

                // The names on the squares travel with the squares.  A caption belongs to the SETUP rather than the tile
                // (so a rename does not rewrite every page) and does not ride the component snapshot, but this editor moves
                // and deletes captions as it moves and deletes tiles, so Ctrl+Z must restore them too.
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

                // The setup is put back AFTER the diagram: restoreCaptions rebuilds the graph from the pages, so doing it
                // first derives a graph from the pre-undo diagram against the post-undo setup, and nothing rebuilds it
                // afterwards.  moveSelection and the shift operations have the same ordering.
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
        // As in undo(): the cut flag is not stood down here, since cutMoves asks each square.

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
