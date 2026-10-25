package org.traincontrol.automationui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.model.ViewListener;

/**
 * Renaming, duplicating and blanking a layout page, as one operation that can be run without a window.
 *
 * This owns the sequence (what happens once the answer is yes); the window still owns the refusals and dialogs.  Every
 * rename defect (MT-135, OB-049, OB-092) lived in the ORDER of the steps or in a step not called, so a test must run
 * this same function rather than supply its own order.
 */
public class LayoutPageEdit
{
    /** Not instantiable: this is a sequence, not a thing. */
    private LayoutPageEdit()
    {
    }

    /**
     * The floor `writeLayoutIndex` must not issue a fresh page id at or below.  The index hands a retired id out
     * again after one write: harmless for a DELETED page, not for a page whose FILE was merely absent, whose settings
     * are still in the setup under its old number.  The setup remembers, so it is asked; zero when there is no session.
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
            // A floor is a safety margin; failing to work one out must not stop the index being written.
            return 0;
        }
    }

    /**
     * The page whose file a page of this name would be written to, other than `except`, or null.  A page lives in
     * `sanitizeFilename(name) + ".cs2"`, and names differing only in case or in a character a file name cannot hold
     * come to one file, so every door that names a page asks this.
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
     * Handed the pages rather than asked for them: `saveChanges` writes where the DIAGRAM came from, not where the
     * path points, so taking the list and the diagram makes a mismatch unrepresentable.
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

        // Where it sits keeps the file in the order the user sees.  The autonomy setup is keyed by page ID on disk,
        // so ids that shift would reattach the whole setup to the wrong pages (MT-135).
        int renamedAt = rename ? layoutList.indexOf(currentLayout) : -1;

        if (rename)
        {
            layoutList.remove(currentLayout);
        }

        // Back in its own slot so the page keeps its id; a duplicate or new page goes at the end.  This is above the
        // write because the arrows must be re-aimed first and that needs the final list.
        if (renamedAt >= 0 && renamedAt <= layoutList.size())
        {
            layoutList.add(renamedAt, newLayoutName);
        }
        else
        {
            layoutList.add(newLayoutName);
        }

        // Told what was renamed, so the page keeps its ID under the new name and the arrows pointing AT it follow.
        // Ids are read back from the index by NAME; without this a rename would orphan the setup.
        Map<String, String> renamed = new LinkedHashMap<>();

        if (rename) renamed.put(currentLayout, newLayoutName);

        // Arrows are re-aimed BEFORE any page is written: a link tile holds the destination's place in the
        // name-sorted page list, and re-aiming upstream of every write fixes Add Page, Duplicate and Rename at once.
        List<LayoutDiagram> pages = loadedPages(log);

        // Held, so the page this gesture is about can be asked the same question.
        List<LayoutDiagram> changed = LayoutDiagram.repointLinksForNewOrder(pages, layoutList, renamed);

        for (LayoutDiagram moved : changed)
        {
            // The page this gesture is about is written below under its new name; saving it here would also write
            // the correction to its old path, which a rename is about to delete.
            if (moved == page) continue;

            try
            {
                moved.saveChanges(null, false);
            }
            catch (Exception cannotSave)
            {
                // The arrows are right in memory either way; refusing would leave the gesture half done.
                if (log != null) log.log(cannotSave);
            }
        }

        // A page that keeps its own file is saved to it, but only when the re-aim changed it: Add and Duplicate write
        // a NEW file, and the loop above saves exactly the pages `repointLinksForNewOrder` reported, not this one.
        // Rewriting an unchanged page would make a `.bak` on OneDrive for nothing.
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

        // The autonomy setup keys everything by PAGE NAME (OB-049): rekey it before the caller rebuilds the session
        // from the renamed pages, or the next reconcile treats every square of the page as deleted.
        if (rename)
        {
            // Only written to a setup that is already there.  The session is handed in ALREADY BUILT because
            // getAutonomySession() builds one on demand and save() writes setup.json unconditionally.
            if (session != null)
            {
                // In memory always: right either way, and a carry reads it.
                session.renamePage(currentLayout, newLayoutName);

                // The session's pages still describe the old names; mark them stale rather than leave each reader to
                // notice (one that did not wrote placements under the old name, MT-135).  See `markPagesStale`.
            }

            try
            {
                if (session == null)
                {
                    // No session: rewrite the file itself, which writes nothing unless the setup is already there.
                    AutonomyCompanionStore.renamePageOnDisk(new java.io.File(layoutPath),
                        currentLayout, newLayoutName);
                }
                else if (session.exists())
                {
                    // WITHOUT reconciling, like its twin in the delete path: save() would prune against pages that
                    // still carry the OLD name and drop every setting on the renamed page (MT-135).
                    session.saveWithoutReconciling();
                }
            }
            catch (java.io.IOException e)
            {
                // The rekey stands in memory either way; failing to write it must not stop the rename.
                if (log != null) log.log(e);
            }
        }

        // A link tile holds a POSITION in the name-sorted page list, so each gesture changes what every arrow means.
        // The arrows were re-aimed above; the index is what is left.  The rule lives in LayoutDiagram because
        // TrainControlUI writes the same index for Delete and Combine.
        LayoutDiagram.writeLayoutIndex(layoutPath, layoutList, renamed, pageIdFloor(session),
            keepAbsent);
    }

    /** Every page of the layout as objects; empty when there is no model (nothing to re-aim). */
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
