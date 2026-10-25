package org.traincontrol.automationui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.model.ViewListener;

/**
 * Renaming, duplicating and blanking a layout page, as one operation that can be run without a window.
 *
 * Adam: "Do you have tests that try to load an entire config, and then trigger a rename via the same function that the
 * UI calls, and then rename it back and test along the way?  This is the only way to catch bugs across these complex
 * types of features."  This owns the sequence (what happens once the answer is yes); the window still owns the
 * refusals and dialogs.  Every rename defect so far (MT-135, OB-049, OB-092) lived in the ORDER of the steps below or
 * in a step not called, so a test that supplies its own order proves nothing.  The comments below record something
 * that went wrong once.
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
     * The floor `writeLayoutIndex` must not issue a fresh page id at or below.  The index remembers a retired id for
     * one write and hands it out again afterwards: harmless for a DELETED page, but not for a page whose FILE was
     * merely absent, whose settings are still in the autonomy setup under its old number, so the next new page would
     * collect a stranger's stations, lengths and exclusions.  The setup is what remembers, so it is asked: the session
     * ALREADY BUILT (no lazy getter in scope to reach for by mistake), zero when there is none.
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
     * The page whose file a page of this name would be written to, other than the one named except, or null.  A page
     * lives in `sanitizeFilename(name) + ".cs2"`, and two names can come to one file: names that differ only in case,
     * or in a character a file name cannot hold; writing one replaced the other page's file.  So every door that names
     * a page (Rename, Duplicate, Add Blank Page, Combine Linked Pages) asks this, as the configurations ask
     * `fileNameTaken`.
     *
     * @param except the page being renamed, which may keep its own file under another case, or null
     * @return the other page already using that file, or null
     */
    public static String pageUsingTheFileOf(List<String> layoutList, String name, String except)
    {
        if (layoutList == null || name == null) return null;

        String wanted = org.traincontrol.util.Util.sanitizeFilename(name);

        for (String page : layoutList)
        {
            if (page == null || page.equals(except)) continue;

            if (org.traincontrol.util.Util.sanitizeFilename(page).equalsIgnoreCase(wanted)) return page;
        }

        return null;
    }

    /**
     * Renames, duplicates or blanks a page, carrying its id and its autonomy setup with it.  The caller has already
     * decided that this may happen (no editor open, no trains running, the layout local, the new name not taken).
     *
     * **Handed the pages rather than asked for them**: the path and the diagrams are two arguments nothing makes agree,
     * and `saveChanges` writes where the DIAGRAM came from, not where the path points.  Taking the list and the diagram
     * makes a mismatch unrepresentable (a test once passed a temp copy as the path and renamed a page in the
     * repository's sample data).
     *
     * @param layoutList the page names in the order they are to appear - MUTATED, as the caller's own live list
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
        renameOrDuplicate(layoutList, page, layoutPath, currentLayout, newLayoutName, rename,
            duplicate, blank, session, log, null);
    }

    /**
     * The same, told which absent pages the operator has said are coming back (FR-018).  An overload because the answer
     * can only come from a person; callers without one keep the old behavior, an absent page's id retired.
     *
     * @param keepAbsent names to keep in the index though they are not in the list; null for the behavior above
     */
    public static void renameOrDuplicate(List<String> layoutList, LayoutDiagram page,
        String layoutPath, String currentLayout, String newLayoutName, boolean rename,
        boolean duplicate, boolean blank, AutonomySession session, ViewListener log,
        java.util.Collection<String> keepAbsent) throws Exception
    {

        // WHERE it sits, so the file stays in the order the user sees (no longer load-bearing for ids since MT-135,
        // kept because a file whose order jumps around is harder to read).
        //
        // The autonomy setup is keyed by page ID on disk, so renaming one page silently reattached the whole setup to
        // the wrong pages, and ids that shift by one round-trip unchanged: the file looked consistent while meaning
        // something else.  The next save reconciled the settings away as deleted squares.  Adam, MT-135: "Immediately
        // after rename, all stations are gone... Renaming the page back did not restore the stations."
        int renamedAt = rename ? layoutList.indexOf(currentLayout) : -1;

        if (rename)
        {
            layoutList.remove(currentLayout);
        }

        // Back in its own slot, so the page keeps its id; a duplicate or new page goes at the end.  MOVED ABOVE THE
        // WRITE: the arrows must be re-aimed before anything is written and the re-aim needs the final list.
        if (renamedAt >= 0 && renamedAt <= layoutList.size())
        {
            layoutList.add(renamedAt, newLayoutName);
        }
        else
        {
            layoutList.add(newLayoutName);
        }

        // Told what was renamed, so the page keeps its ID under the new name and the arrows that point AT it follow it
        // (built before the re-aim below).  Ids are read back from the index by NAME, so a rename is the one operation
        // where the name an id belongs to changes; without this the page would take a fresh id and orphan its setup.
        Map<String, String> renamed = new LinkedHashMap<>();

        if (rename) renamed.put(currentLayout, newLayoutName);

        // AND THE ARROWS ARE RE-AIMED BEFORE ANY PAGE IS WRITTEN.  A link tile holds the destination's place in the
        // name-sorted page list, so all three gestures change what every arrow means.  Re-aimed at the end, Add Page
        // had blanked the current page's object so the ORIGINAL file was never corrected, Duplicate's copy was not in
        // the model, and Rename excluded its own page on the ground that the fix "travels with it" - true of the
        // object, false of the ORDER.  Doing it here fixes all three, being upstream of every write.
        List<LayoutDiagram> pages = loadedPages(log);

        // HELD, so the page this gesture is about can be asked the same question.
        List<LayoutDiagram> changed = LayoutDiagram.repointLinksForNewOrder(pages, layoutList, renamed);

        for (LayoutDiagram moved : changed)
        {
            // The page this gesture is about is written below, under its new name, carrying the tiles
            // just corrected - so saving it HERE as well would write the same correction to its old
            // path, which for a rename is the file the rename is about to delete.
            if (moved == page) continue;

            try
            {
                moved.saveChanges(null, false);
            }
            catch (Exception cannotSave)
            {
                // The arrows are right in memory either way; refusing here would leave this gesture
                // half done.
                if (log != null) log.log(cannotSave);
            }
        }

        // A PAGE THAT KEEPS ITS OWN FILE IS SAVED TO IT: Add and Duplicate write a NEW file and leave the current
        // page's own alone, so the correction above would not reach disk.  AND ONLY WHEN THE RE-AIM ACTUALLY CHANGED
        // IT: the loop above saves exactly the pages `repointLinksForNewOrder` reported as changed, and this is the one
        // page it cannot see; rewriting an unchanged page touches a file on OneDrive and makes a `.bak` for nothing.
        if (!rename && changed.contains(page))
        {
            try
            {
                page.saveChanges(null, false);
            }
            catch (Exception cannotSave)
            {
                if (log != null) log.log(cannotSave);
            }
        }

        if (blank)
        {
            page.clear();
        }

        page.saveChanges(newLayoutName, duplicate);

        // The autonomy setup keys everything by PAGE NAME, so it has to be told (OB-049):
        // AutonomyCompanionStore.renamePage rekeys all eleven collections and the tile keys in every configuration.
        // Unrenamed, the next reconcile treats every square of the page as deleted and drops its point name and
        // station.  Adam: "CRITICAL: renaming a layout page disconnects its autonomy config. stations and links are
        // broken."  Here and not after saveChanges succeeds: the store must be rekeyed before the caller rebuilds the
        // session from the renamed pages, which is what reconciles.
        if (rename)
        {
            // Only written to a setup that is already there.  The session is handed in ALREADY BUILT because
            // TrainControlUI's getAutonomySession() builds one on demand (opens every page, runs the caption migration,
            // and save() writes setup.json unconditionally), so renaming a page on a layout where autonomy had never
            // been touched invented a setup out of nothing.
            if (session != null)
            {
                // In memory always: it costs nothing and is right either way.  The setup's squares, and the
                // railway's, which a carry reads.
                session.renamePage(currentLayout, newLayoutName);

                // The session now holds page objects that describe a layout that no longer exists under those names:
                // everything derived from them still says the old name while the store says the new one.  Say so here
                // rather than leave each reader to notice: one that did not wrote every placement back under the old
                // page name, and a locomotive recorded in two places fails the entire setup (MT-135).  See
                // `markPagesStale`.
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
                    // WITHOUT reconciling, like its twin in the delete path.  save() prunes the setup against the pages
                    // this session holds, which still carry the OLD name now (saveChanges writes a new FILE, it never
                    // renames the object), so every name, station, length and caption on the renamed page would read as
                    // deleted track and be dropped: the MT-135 loss by a second route.
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

        // AND EVERY ARROW KEEPS ITS AIM: a link tile holds a POSITION in the name-sorted page list, so adding, renaming
        // or duplicating a page changes what every arrow means.  The rule is in LayoutDiagram because TrainControlUI
        // writes this same index for Delete and Combine.  The arrows were re-aimed and saved above, before the page
        // files were written; what is left here is the index itself.
        LayoutDiagram.writeLayoutIndex(layoutPath, layoutList, renamed, pageIdFloor(session),
            keepAbsent);
    }

    /**
     * Every page of the layout, as objects.
     *
     * @param model the ViewListener this was handed, null in the overload that takes none (nothing to re-aim)
     * @return the loaded pages
     */
    private static List<LayoutDiagram> loadedPages(ViewListener model)
    {
        List<LayoutDiagram> pages = new java.util.ArrayList<>();

        if (model == null) return pages;

        for (String name : model.getLayoutList())
        {
            LayoutDiagram each = model.getLayout(name);

            if (each != null) pages.add(each);
        }

        return pages;
    }
}
