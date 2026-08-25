package org.traincontrol.automationui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.model.ViewListener;

/**
 * Renaming, duplicating and blanking a layout page, as one operation that can be run without a window.
 *
 * Adam, 2026-08-24: "Do you have tests that try to load an entire config, and then trigger a rename via
 * the same function that the UI calls, and then rename it back and test along the way?  This is the only
 * way to catch bugs across these complex types of features."
 *
 * We did not, and the reason is the shape this class exists to change. All of this lived in the body of
 * a private method on TrainControlUI, inside an invokeLater, so the only way to reach it was to have a
 * window. The tests therefore stopped one layer lower, at AutonomyCompanionStore.renamePage, and
 * hand-rolled the two steps around it in whatever order the test author believed was right.
 *
 * That is worse than no coverage, because it reads as coverage. A test that performs its own version of
 * the sequence agrees with itself. Every rename defect this project has had - MT-135, OB-049, OB-092 -
 * lived in the ORDER of the steps below or in a step that was not called at all, and not one of them
 * would have been caught by a test that supplied its own order.
 *
 * So nothing here decides anything the UI used to decide. The window still owns the refusals and the
 * dialogs, which are about whether to ask at all; this owns the sequence, which is about what happens
 * once the answer is yes. The comments are carried across as they stood - each of them is the record of
 * something that went wrong once, and moving code away from its reasons is how it goes wrong again.
 */
public class LayoutPageEdit
{
    /**
     * Not instantiable: this is a sequence, not a thing.
     */
    private LayoutPageEdit()
    {
    }

    /**
     * The floor `writeLayoutIndex` must not issue a fresh page id at or below (IAR-A1).
     *
     * The index remembers a retired id for exactly one write - the one that drops the page - and hands
     * it out again afterwards. Harmless for a page that was DELETED, because deletePage forgets its
     * settings first. Not harmless for a page whose FILE was merely absent when the index was written:
     * its settings are still in the autonomy setup, held under its old number, and the next new page
     * would collect a stranger's stations, lengths and exclusions with nothing reporting a renumber.
     *
     * The setup is the thing that remembers, so it is the thing that is asked. Zero when there is no
     * session or no setup, which is exactly the behaviour this had before.
     *
     * Takes the session rather than reading a field, which is the whole of SV-B2 made structural: the
     * caller must hand over the session it ALREADY BUILT, and there is no lazy getter in scope here to
     * reach for by mistake.
     *
     * @param session the session already built, or null
     * @return the highest page id the autonomy setup has ever recorded, or 0
     */
    public static int pageIdFloor(AutonomySession session)
    {
        try
        {
            return session == null ? 0 : session.getStore().highestPageIdSeen();
        }
        catch (RuntimeException e)
        {
            // A floor is a safety margin, not a correctness requirement of the write itself. Failing
            // to work one out must not stop the index being written at all.
            return 0;
        }
    }

    /**
     * Renames, duplicates or blanks a page, carrying its id and its autonomy setup with it.
     *
     * The caller has already decided that this may happen - no editor is open, no trains are running,
     * the layout is local, and the new name is not taken. This does not re-ask any of that.
     *
     * **Handed the pages rather than asked for them.** The first version of this took the control
     * station and called getLayoutList() and getLayout(name) on it, which is what the UI method did
     * when the code was inside it. In the window that is safe, because the model was parsed from the
     * very folder `layoutPath` names. As a method it is a trap: the path and the diagrams are two
     * separate arguments and nothing makes them agree, and `saveChanges` writes where the DIAGRAM came
     * from, not where the path points.
     *
     * The first test written against it fell straight in - it passed a temp copy as the path while the
     * model still held the real layout, and renamed a page in the repository's sample data. Passing the
     * list and the diagram makes that mismatch unrepresentable: they are the same objects the caller
     * already has, from wherever it got them.
     *
     * @param layoutList the page names in the order they are to appear - MUTATED, as the caller's own
     *        live list, because the rename has to be visible to whatever holds it
     * @param page the diagram being renamed, which is also where the file is written
     * @param layoutPath the layout folder, which must be the one `page` was parsed from
     * @param currentLayout the page as it is named now
     * @param newLayoutName what it is to be called
     * @param rename true to rename the page rather than add one
     * @param duplicate true to leave the original in place and write a copy
     * @param blank true to empty the page before writing it
     * @param session the autonomy session ALREADY BUILT, or null if none is
     * @param log where to report a setup that could not be written, or null
     * @throws Exception if the page file cannot be written
     */
    public static void renameOrDuplicate(List<String> layoutList, LayoutDiagram page,
        String layoutPath, String currentLayout, String newLayoutName, boolean rename,
        boolean duplicate, boolean blank, AutonomySession session, ViewListener log) throws Exception
    {

        // WHERE it sits, so the file stays in the order the user sees.
        //
        // This began as the fix for MT-135: writeLayoutIndex numbered pages by position, so removing
        // the name and adding the new one put the renamed page last and gave every page after its old
        // slot a different id.  Ids no longer come from the position, so this is no longer
        // load-bearing - it is kept because a file whose order jumps around is harder to read, and
        // because getLayoutList is sorted, so the slack costs nothing.
        //
        // The autonomy setup is keyed by page ID on disk.  So renaming one page silently reattached
        // the whole setup to the wrong pages - and because ids that shift by one round-trip unchanged
        // (id 1 reads as the page now called 1, writes back as 1), the file looked consistent while
        // meaning something else entirely.  The coordinates of one page's settings do not exist on the
        // next page along, so the following save reconciled them away as deleted squares.
        //
        // Adam, MT-135: "Immediately after rename, all stations are gone... Renaming the page back did
        // not restore the stations."  It could not: they had already been pruned and written.  He lost
        // 19 point names, 14 stations, 22 directions and 15 captions to one rename on 2026-08-23.
        int renamedAt = rename ? layoutList.indexOf(currentLayout) : -1;

        if (rename)
        {
            layoutList.remove(currentLayout);
        }

        if (blank)
        {
            page.clear();
        }

        page.saveChanges(newLayoutName, duplicate);

        // Back in its own slot, so the page keeps its id.  A duplicate or a new page has no slot of its
        // own and goes at the end, which is where a page that did not exist before belongs.
        if (renamedAt >= 0 && renamedAt <= layoutList.size())
        {
            layoutList.add(renamedAt, newLayoutName);
        }
        else
        {
            layoutList.add(newLayoutName);
        }

        // The autonomy setup keys everything by PAGE NAME, so it has to be told (OB-049).
        //
        // AutonomyCompanionStore.renamePage rekeys all eleven collections and the tile keys inside
        // every configuration, and its comments record two earlier defects it was extended to cover. It
        // had no caller. Nothing in the application had ever invoked it, so a rename left every key
        // pointing at a page that no longer exists - and the next reconcile, doing exactly what it
        // should with a square that has been deleted, removed the point name and the station for every
        // one of them.
        //
        // Adam: "CRITICAL: renaming a layout page disconnects its autonomy config. stations and links
        // are broken." Renaming back could not undo it, because they were already gone.
        //
        // Here rather than after saveChanges succeeds: the rename has to reach the store before the
        // caller rebuilds the session from the renamed pages, because that rebuild is what reconciles -
        // and reconciling against a store still keyed by the old name is the whole of the bug.
        if (rename)
        {
            // Only written to a setup that is already there.
            //
            // The session is handed in ALREADY BUILT for this reason. TrainControlUI's
            // getAutonomySession() builds one on demand: it opens every page, runs the caption
            // migration - which rewrites gleisbild files and can raise a dialog - and save() then does
            // folder().mkdirs() and writes setup.json unconditionally. So renaming a page on a layout
            // where autonomy had never been touched invented a setup out of nothing and attributed it
            // to a gesture with nothing to do with autonomy.
            //
            // repairAutonomyLocomotive guards exactly this, twice over, and says why. Its two siblings
            // - this and the delete - were written in the same series and had neither guard. Found by
            // review.
            if (session != null)
            {
                // In memory always: it costs nothing and is right either way.
                session.getStore().renamePage(currentLayout, newLayoutName);

                // And the session is now holding page objects that describe a layout that no longer
                // exists under those names.  Everything derived from them - the graph, the reducer,
                // the naming captureFromLayout works its tile keys out from - was built before this
                // line and still says the old name, while the store now says the new one.
                //
                // Saying so here rather than leaving each reader to notice: the one reader that did
                // not notice wrote every placement back a second time under the old page name, and a
                // locomotive recorded in two places fails the entire setup (MT-135).
                session.markPagesStale();
            }

            try
            {
                if (session == null)
                {
                    // No session: rewrite the file itself, which writes nothing at all unless the setup
                    // is already there.
                    AutonomyCompanionStore.renamePageOnDisk(new java.io.File(layoutPath),
                        currentLayout, newLayoutName);
                }
                else if (session.exists())
                {
                    // WITHOUT reconciling, like its twin in the delete path.
                    //
                    // save() prunes the setup against the pages this session holds - and those pages
                    // still carry the OLD name at this moment. LayoutDiagram.saveChanges writes a new
                    // FILE; it never renames the object, and refreshLayouts does not run until the
                    // caller finishes, later.
                    //
                    // So the store has just been rekeyed to the new name and reconcile is handed a set
                    // of squares under the old one: every name, station, length, direction, signal,
                    // caption and disabled link on the renamed page reads as track that has been
                    // deleted, and is dropped and written. That is the MT-135 loss exactly, by a second
                    // route - and the report that would have said so is discarded at this call site.
                    //
                    // The delete path already used saveWithoutReconciling for this reason. I rewrote
                    // this block in the same commit and left the reconciling call in it; review caught
                    // it.
                    session.saveWithoutReconciling();
                }
            }
            catch (java.io.IOException e)
            {
                // The rekey stands in memory either way; only the record of it is at risk, and failing
                // to write it must not stop the page being renamed.
                if (log != null) log.log(e);
            }
        }

        // Told what was renamed, so the page keeps its ID under the new name.
        //
        // Ids are a page's identity now rather than its place in the list, and they are read back from
        // the index by NAME - so a rename is the one operation where the name the id belongs to
        // changes.  Without this the renamed page would be a name nobody has seen, take a fresh id, and
        // leave its whole setup keyed to an id that no page holds any more: orphaned, and pruned by the
        // next reconcile.
        Map<String, String> renamed = new LinkedHashMap<>();

        if (rename) renamed.put(currentLayout, newLayoutName);

        LayoutDiagram.writeLayoutIndex(layoutPath, layoutList, renamed, pageIdFloor(session));
    }
}
