package org.traincontrol.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.LayoutPageEdit;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.widgets.BusyDialog;
import org.traincontrol.gui.widgets.QuickFileChooser;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * The layout's folder and its pages: Duplicate, Rename and Add Blank Page, Combine Linked Pages, the page index with
 * its arrows kept aimed, a new layout from the demo archive, and the Central Station's layout downloaded.
 *
 * Not the locomotive mapping pages (copy, paste and clear), which stay on the window.
 */
public final class LayoutFileActions
{
    private final TrainControlUI ui;

    LayoutFileActions(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Writes the page index and re-aims every arrow on the layout at the page it was aimed at, as a link tile holds a
     * position in the name-sorted page list.  Delete and Combine go through here; Add, Duplicate and Rename go through
     * `LayoutPageEdit`.  The order is taken from the pages this session holds, not the index file (FR-018).  `renamed`
     * is old name to new name, or null (both callers pass null: a rename goes through `LayoutPageEdit`); `keepAbsent`
     * is pages to hold in the index though they are not loaded (FR-018), or null.
     *
     * @throws java.io.IOException if the index cannot be written, which is the caller's to report
     */
    void writeIndexAndKeepLinksAimed(List<String> layoutList, Map<String, String> renamed,
        Collection<String> keepAbsent) throws IOException
    {
        // The rule is in LayoutDiagram, because LayoutPageEdit writes this index too.
        for (LayoutDiagram changed : LayoutDiagram.writeIndexAndKeepLinksAimed(
            this.ui.localLayoutPath(), layoutList, renamed, this.ui.pageIdFloor(), keepAbsent,
            this.everyLoadedPage()))
        {
            try
            {
                changed.saveChanges(null, false);
            }
            catch (Exception cannotSave)
            {
                // The arrows are right in memory either way, and the page list has already been
                // written; refusing here would leave the index and the pages disagreeing.
                this.ui.getModel().log(cannotSave);
            }
        }
    }

    /** Delete Current Page once confirmed; `from` (the editor that asked, or null) is told which page to show. */
    void deleteConfirmedPage(String going, LayoutEditor from)
    {
        final Component over = from == null ? this.ui : from;

        boolean arriving = false;

        try
        {
            List<String> layoutList = this.ui.getModel().getLayoutList();

            // Other missing pages are asked about BEFORE the file goes (FR-018); the deleted page is not.
            java.util.Collection<String> keepAbsent = this.ui.settleTheAbsentPages(layoutList, null,
                java.util.Arrays.asList(going), over);

            if (keepAbsent == null) return;

            // The page FIRST: deleteLayoutFile can fail, and then the setup must not be forgotten.
            this.ui.getModel().getLayout(going).deleteLayoutFile();

            // Only a session already built and a setup already there: a save would create the folder and file.
            org.traincontrol.automationui.AutonomySession session = this.ui.autonomySession();

            try
            {
                if (session == null)
                {
                    org.traincontrol.automationui.AutonomyCompanionStore.deletePageOnDisk(
                        new java.io.File(this.ui.localLayoutPath()), going);
                }
                else
                {
                    // Before forgetting the page, or the capture would write its settings back.
                    this.ui.captureRunningLayout();

                    // The session's page list loses it too, or the reset below would write it back.
                    int forgotten = session.deletePage(going);

                    if (forgotten > 0)
                    {
                        this.ui.getModel().logf("layout.infoPageSetupForgotten", forgotten, going);
                    }

                    if (session.exists()) session.saveWithoutReconciling();
                }
            }
            catch (java.io.IOException e)
            {
                // The page is going either way; only the record of it is at risk
                this.ui.getModel().log(e);
            }

            layoutList.remove(going);

            // No rename map: the deleted page's id is retired.
            this.writeIndexAndKeepLinksAimed(layoutList, null, keepAbsent);

            this.ui.layoutEditingComplete(from == null ? null
                : () -> from.arriveOn(String.valueOf(this.ui.layoutList().getSelectedItem())));

            arriving = true;
        }
        catch (IOException | java.net.URISyntaxException | RuntimeException ex)
        {
            JOptionPane.showMessageDialog(
                over,
                I18n.f("layout.ui.errorSavingLayoutWithMessage", ex.getMessage())
            );
            this.ui.getModel().log(ex);
        }
        finally
        {
            if (from != null && !arriving) from.arriveOn(null);
        }
    }

    /** Unzips an empty layout to the current folder and returns the path. */
    String initializeEmptyLayout(String folderName)
    {
        try
        {
            String from = TrainControlUI.RESOURCE_PATH + TrainControlUI.DEMO_LAYOUT_ZIP;
            File to = new File(TrainControlUI.DEMO_LAYOUT_ZIP);

            copyResource(from, to);

            this.ui.getModel().logf(
                "layout.ui.infoAttemptingExtractDemoLayout",
                to.getAbsolutePath()
            );

            File outputFolder = new File(folderName);

            try
            {
                this.unzipFile(Paths.get(to.getPath()), outputFolder.getAbsolutePath());
            }
            finally
            {
                to.delete();
            }

            return outputFolder.getAbsolutePath();
        }
        catch (IOException ex)
        {
            this.ui.getModel().logf(
                "layout.ui.errorDemoLayoutExtraction",
                ex.getMessage()
            );

            this.ui.getModel().log(ex);
        }
        
        return null;
    }

    /** The preferences a switch of the layout in use writes (`TrainControlUI.layoutFolderIs`). */
    private static final String[] SWITCH_PREFS = {TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF,
        TrainControlUI.LAST_LOCAL_LAYOUT_PREF, TrainControlUI.RECENT_LAYOUTS_PREF};

    /**
     * Makes `folder` the layout in use; asked after its sync, the answer is whether it read, and if not puts all back.
     */
    java.util.function.BooleanSupplier switchTo(String folder)
    {
        final String[] was = new String[SWITCH_PREFS.length];

        for (int i = 0; i < was.length; i++) was[i] = TrainControlUI.getPrefs().get(SWITCH_PREFS[i], null);

        final List<LayoutDiagram> pages = everyLoadedPage();

        TrainControlUI.layoutFolderIs(folder);

        return () ->
        {
            // A read puts new page objects in; a failed one (the folder changed after its check) keeps the old, or falls
            // back to the station's, and the layout in use and its own pages go back as they were (FR-245)
            List<LayoutDiagram> now = everyLoadedPage();
            boolean same = now.size() == pages.size();

            for (int i = 0; same && i < now.size(); i++) same = now.get(i) == pages.get(i);

            if (!same && folder.equals(TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "")))
                return true;

            for (int i = 0; i < was.length; i++)
            {
                if (was[i] == null) TrainControlUI.getPrefs().remove(SWITCH_PREFS[i]);
                else TrainControlUI.getPrefs().put(SWITCH_PREFS[i], was[i]);
            }

            // Read again, as after a page edit: the session and the page list follow the new objects
            if (!same) this.ui.layoutEditingComplete(null);

            return false;
        };
    }

    /** Copies a JAR resource file to the specified path. */
    private void copyResource(String resource, File output) throws IOException
    {
        InputStream is = TrainControlUI.class.getResource(resource).openStream();
        OutputStream os = new FileOutputStream(output.getPath());

        byte[] b = new byte[2048];
        int length;

        while ((length = is.read(b)) != -1) os.write(b, 0, length);

        is.close();
        os.close();
    }

    /**
     * Unzips a file (after https://howtodoinjava.com/java/io/unzip-file-with-subdirectories/), throwing at the first file
     * it cannot write, so a half-unpacked folder is never taken for a layout.  A file already there is kept.
     */
    private void unzipFile(Path filePathToUnzip, String folderName) throws IOException
    {
        Path targetDir = Paths.get(folderName);

        try (ZipFile zip = new ZipFile(filePathToUnzip.toFile()))
        {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            //We will unzip files in this folder
            if (!targetDir.toFile().isDirectory() && !targetDir.toFile().mkdirs()) 
            {
                throw new IOException(
                    I18n.f("error.failedToCreateDirectory", targetDir)
                );
            }

            //Iterate over entries
            while (entries.hasMoreElements())
            {
                ZipEntry entry = entries.nextElement();

                File f = new File(targetDir.resolve(Paths.get(entry.getName())).toString());

                // If directory then create a new directory in uncompressed folder
                if (entry.isDirectory())
                {
                    if (!f.isDirectory() && !f.mkdirs())
                    {
                        throw new IOException(
                            I18n.f("error.failedToCreateDirectory", f)
                        );
                    }
                }

                // Else create the file
                else
                {
                    File parent = f.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs())
                    {
                        throw new IOException(
                            I18n.f("error.failedToCreateDirectory", parent)
                        );
                    }

                    if (f.exists()) continue;

                    try (InputStream in = zip.getInputStream(entry))
                    {
                        Files.copy(in, f.toPath());
                    }
                }
            }
        }
    }

    /**
     * Layouts > Create New Layout: `TrainControlUI.initializeLocalLayoutMenuItemActionPerformed`'s body.
     */
    void initializeLocalLayoutMenuItemActionPerformed()
    {
        // Not while autonomy runs, before its folder window (MT-141): a folder chosen would switch the layout and reset
        // the session under moving trains.
        if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;

        // Nor under an open editor, whose Cancel would put trains back on a railway that is gone.
        if (this.ui.refuseWhileEditorOpen()) return;

        SwingUtilities.invokeLater(() ->
        {
            // Asked again: an Edit queued behind the click puts an editor on its way before this runs
            if (this.ui.refuseWhileEditorOpen()) return;

            JOptionPane.showMessageDialog(
                this.ui,
                I18n.t("layout.ui.infoSelectFolderForNewLayout")
            );

            // BESIDE THE LAST LAYOUT FOLDER (OB-308), where a new one is likely to go
            JFileChooser fc = this.ui.layoutFolderChooser(false);
            int i = fc.showOpenDialog(this.ui);
            if (i == JFileChooser.APPROVE_OPTION)
            {
                File f = fc.getSelectedFile();
                String filepath = f.getPath();

                // The folder becomes the layout once it has unpacked whole: createAndApplyEmptyLayout switches to it
                this.ui.initializeLocalLayoutMenuItem().setEnabled(false);

                this.ui.createAndApplyEmptyLayout(filepath, false);
            }
        });
    }

    /**
     * Builds one page showing this page and every page it links to, stacked one under another with a blank row between,
     * in the order the links appear reading left to right and top to bottom.  One level, deliberately: following links
     * recursively produces every page, in an order nobody chose.  The result is excluded from autonomy as it is
     * written, not optionally: left included, every square would mint a second Point for a sensor and a parallel set of
     * edges.
     */
    void combineLinkedPages(final LayoutEditor editor)
    {
        final Component over = editor == null ? this.ui : editor;

        // The same rule as deleting and renaming: this writes a new page and rebuilds the session.
        if (this.ui.refuseWhileARunHoldsTheSetup(over)) return;

        if (editor == null && this.ui.refuseWhileEditorOpen()) return;

        if (!this.ui.isLocalLayout())
        {
            JOptionPane.showMessageDialog(over,
                I18n.t("layout.ui.errorCannotManipulateRemoteLayouts"));
            return;
        }

        final String from = this.ui.layoutList().getSelectedItem().toString();

        final List<String> pages = pagesLinkedFrom(from);

        if (pages.size() < 2)
        {
            JOptionPane.showMessageDialog(over, I18n.t("layout.ui.infoNothingLinkedFromHere"));
            return;
        }

        String name = JOptionPane.showInputDialog(over,
            I18n.t("layout.ui.promptCombinedPageName"),
            I18n.f("layout.ui.combinedPageName", from));

        if (name == null || name.trim().isEmpty()) return;

        final String combined = name.trim();

        // And no other page's file: a character a file name cannot hold comes to the same file as a page with an
        // underscore there.
        String usesThatFile = LayoutPageEdit.pageUsingTheFileOf(this.ui.getModel().getLayoutList(), combined, null);

        if (usesThatFile != null)
        {
            JOptionPane.showMessageDialog(over,
                I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst", usesThatFile));

            return;
        }

        if (this.ui.getModel().getLayoutList().contains(combined))
        {
            JOptionPane.showMessageDialog(over,
                I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst", combined));
            return;
        }

        // The ceiling, asked before anything is made, and not in `fillCombinedPage`, which runs after the page is
        // written and indexed.
        Dimension wouldBe = combinedSize(pages);

        if (wouldBe.height > LayoutEditor.MAX_SIZE || wouldBe.width > LayoutEditor.MAX_SIZE)
        {
            JOptionPane.showMessageDialog(over,
                I18n.f("layout.ui.errorMaxSizeExceeded", LayoutEditor.MAX_SIZE));
            return;
        }

        final Runnable work = () -> combineOnAWorker(from, combined, pages, editor);

        if (editor == null) work.run();
        else editor.stepOutFor(work);
    }

    /** The combined page's size: the widest source page, and every height plus a blank row between. */
    private Dimension combinedSize(List<String> pages)
    {
        Dimension size = new Dimension();

        for (String each : pages)
        {
            LayoutDiagram page = this.ui.getModel().getLayout(each);

            if (page == null) continue;

            size.width = Math.max(size.width, page.getSx());
            size.height += page.getSy() + 1;
        }

        return size;
    }

    /** Combine Linked Pages once nothing can refuse it; `editor` is told which page to show however this ends. */
    private void combineOnAWorker(final String from, final String combined, final List<String> pages,
        final LayoutEditor editor)
    {
        // On a worker, because it parses every page twice.  And a refresh under way from here: a run started before
        // `layoutEditingComplete` would run the railway from before and be forgotten by the reset.
        this.ui.refreshesUnderWay.incrementAndGet();

        new Thread(() ->
        {
            boolean arriving = false;

            try
            {
                // Asked before the new page is written (FR-018), as the delete path does: a Cancel at the index write
                // would leave a page nothing references.
                Collection<String> keepAbsent = this.ui.settleTheAbsentPages(
                    this.ui.getModel().getLayoutList(), null, null, editor == null ? this.ui : editor);

                if (keepAbsent == null) return;

                // Made as every other page is, by saving an existing one under a new name (the Duplicate Page call): a
                // hand-built LayoutDiagram cannot save itself.
                this.ui.getModel().getLayout(from).saveChanges(combined, true);

                List<String> layoutList = this.ui.getModel().getLayoutList();

                layoutList.add(combined);

                writeIndexAndKeepLinksAimed(layoutList, null, keepAbsent);

                // Excluded before the pages are re-read, so no build sees it as a page to walk.  Only a setup that
                // already exists: the lazy getter would create one (and write setup.json) on a layout without
                // autonomy; excludeRepeatedSensorPages shuts a combined page when a setup is first created.
                AutonomySession session = this.ui.autonomySession();

                if (session != null)
                {
                    session.setPageExcluded(combined, true);

                    // And the answer is read here too: a failed write here means the combined page is excluded in
                    // memory and not on disk, so the next load offers it to autonomy.
                    if (session.exists() && !session.saveQuietly() && this.ui.getModel() != null)
                    {
                        this.ui.getModel().logf(java.util.logging.Level.SEVERE, "autosetup.log.saveFailedAfterCombine");
                    }
                }

                // Re-read, so the new page exists as an object with its own file behind it
                this.ui.getModel().refreshLayouts();

                fillCombinedPage(combined, pages);

                SwingUtilities.invokeLater(() -> this.ui.layoutEditingComplete(() ->
                {
                    this.ui.layoutList().setSelectedItem(combined);
                    this.ui.repaintLayout();

                    if (editor != null) editor.arriveOn(String.valueOf(this.ui.layoutList().getSelectedItem()));
                }));

                arriving = true;
            }
            catch (Exception ex)
            {
                this.ui.getModel().log(ex);

                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(editor == null ? this.ui : editor,
                        I18n.f("layout.ui.errorSavingLayoutWithMessage", ex.getMessage())));
            }
            finally
            {
                if (editor != null && !arriving) editor.arriveOn(null);

                // Posted after the hand-over above, so `layoutEditingComplete` has counted its own before this one goes
                SwingUtilities.invokeLater(() -> this.ui.refreshesUnderWay.updateAndGet(n -> Math.max(0, n - 1)));
            }
        }).start();
    }

    /**
     * Moves the selected page one place up or down the page list and saves at once (FR-175).  Silent at either end,
     * where the editor grays the arrow.  `by` is -1 for up, 1 for down.
     */
    public void movePage(final int by, final LayoutEditor from)
    {
        final String page = String.valueOf(this.ui.layoutList().getSelectedItem());

        if (moved(this.ui.getModel().getLayoutList(), page, by) == null) return;

        reorder(pages -> moved(pages, page, by), page, page, from);
    }

    /** The pages with this one moved by `by` places, or null at either end. */
    private static List<String> moved(List<String> pages, String page, int by)
    {
        int at = pages.indexOf(page);
        int to = at + by;

        if (at < 0 || to < 0 || to >= pages.size()) return null;

        List<String> out = new ArrayList<>(pages);

        java.util.Collections.swap(out, at, to);

        return out;
    }

    /** The pages in name order by their letters (`Util.byLetters`; ties in a fixed order); null when they are. */
    private static List<String> sorted(List<String> pages)
    {
        List<String> out = new ArrayList<>(pages);
        Comparator<String> letters = Util::byLetters;

        out.sort(letters.thenComparing(Comparator.naturalOrder()));

        return out.equals(pages) ? null : out;
    }

    /** Writes the pages in name order once (FR-175); nothing is written when they already are. */
    public void sortPagesByName(final LayoutEditor from)
    {
        if (sorted(this.ui.getModel().getLayoutList()) == null) return;

        // The layout editor arrives on its own page, and the main window is put back on the page it showed
        reorder(LayoutFileActions::sorted, String.valueOf(this.ui.layoutList().getSelectedItem()), null, from);
    }

    /**
     * Writes the index in a new page order: every page keeps its id, and no page file and not the setup is touched.
     * Refused as the other page edits are.  `order` is worked out from the pages as they are when the write goes ahead
     * (a page may have been added meanwhile); null when there is nothing to change.  `editorPage` is the page the
     * layout editor that asked arrives on, null for the one it left.
     */
    private void reorder(final java.util.function.Function<List<String>, List<String>> order, final String page,
        final String editorPage, final LayoutEditor from)
    {
        if (refusedAsAPageEdit(from)) return;

        runPageEdit(from, page, editorPage, () ->
        {
            List<String> next = order.apply(this.ui.getModel().getLayoutList());

            if (next == null) return false;

            this.ui.captureRunningLayout();

            Collection<String> keepAbsent = this.ui.settleTheAbsentPages(
                this.ui.getModel().getLayoutList(), null, null, from == null ? this.ui : from);

            if (keepAbsent == null) return false;

            // Only the order of the index changes: an arrow's number indexes the name-sorted list, which does not move,
            // and each page keeps its id.
            LayoutDiagram.writeLayoutIndex(this.ui.localLayoutPath(), next, null, this.ui.pageIdFloor(), keepAbsent);

            return true;
        });
    }

    /**
     * The refusals every page edit that rewrites the index shares, in the order they are asked: an open editor (the
     * Layouts menu's edits only; the editor that asked steps aside), a running railway, and a Central Station layout.
     * True when the edit is refused, the user told why.
     */
    private boolean refusedAsAPageEdit(LayoutEditor from)
    {
        final Component over = from == null ? this.ui : from;

        // Not while an editor holds the diagram: the edit ends in layoutEditingComplete, which rebuilds the autonomy
        // session from pages just replaced, so under an open editor a second editor opens on the same diagram.
        if (from == null && this.ui.refuseWhileEditorOpen()) return true;

        // And not while trains are running (MT-141): the session is rebuilt underneath whatever is moving.
        if (this.ui.refuseWhileARunHoldsTheSetup(over)) return true;

        if (!this.ui.isLocalLayout())
        {
            JOptionPane.showMessageDialog(over, I18n.t("layout.ui.errorCannotManipulateRemoteLayouts"));

            return true;
        }

        return false;
    }

    /** What a page edit does once it is allowed: true when it wrote the layout, false when the user stopped it. */
    private interface PageEdit
    {
        boolean write() throws Exception;
    }

    /**
     * Runs a page edit where the Layouts menu or the layout editor that asked (which steps aside first) runs it, then
     * refreshes the layout and shows `show`; a failure is reported over the asker and the editor told to arrive back.
     */
    private void runPageEdit(final LayoutEditor from, final String show, final PageEdit edit)
    {
        runPageEdit(from, show, show, edit);
    }

    /** The same, with the layout editor arriving on `editorShows` (null: the page it left) and the main window on `show`. */
    private void runPageEdit(final LayoutEditor from, final String show, final String editorShows, final PageEdit edit)
    {
        final Component over = from == null ? this.ui : from;

        final Runnable work = () ->
        {
            boolean arriving = false;

            try
            {
                if (edit.write())
                {
                    this.ui.layoutEditingComplete(() ->
                    {
                        // Posted, so it lands after the two below
                        if (from != null) from.arriveOn(editorShows);

                        this.ui.layoutList().setSelectedItem(show);
                        this.ui.repaintLayout();

                        // The editor's arrival selects its page in the main window; this lands after it
                        if (from != null)
                        {
                            SwingUtilities.invokeLater(() -> this.ui.layoutList().setSelectedItem(show));
                        }
                    });

                    arriving = true;
                }
            }
            catch (Exception ex)
            {
                JOptionPane.showMessageDialog(over, I18n.f("layout.ui.errorSavingLayoutWithMessage", ex.getMessage()));
                this.ui.getModel().log(ex);
            }
            finally
            {
                if (from != null && !arriving) from.arriveOn(null);
            }
        };

        if (from == null) SwingUtilities.invokeLater(work);
        else from.stepOutFor(work);
    }

    /**
     * Duplicates or renames the current layout page, optionally clearing it; `from` is the layout editor that asked
     * (FR-109), or null.
     */
    void duplicateOrRenameCurrentLayout(String newLayoutName, boolean rename, boolean duplicate, boolean blank,
        final LayoutEditor from)
    {
        final Component over = from == null ? this.ui : from;

        // Refused at the entry, as by the end the page is written.
        if (refusedAsAPageEdit(from)) return;

        if (this.ui.getModel().getLayoutList().contains(newLayoutName))
        {
            JOptionPane.showMessageDialog(
                over,
                I18n.f(
                    "layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst",
                    newLayoutName
                )
            );
            return;
        }

        // NOR ONE WHOSE FILE ANOTHER PAGE USES: a name differing only in case, or in a character a file name cannot
        // hold, is written to that page's file.  A page renamed to its own name in another case keeps its own file.
        String usesThatFile = LayoutPageEdit.pageUsingTheFileOf(this.ui.getModel().getLayoutList(),
            newLayoutName, rename ? this.ui.layoutList().getSelectedItem().toString() : null);

        if (usesThatFile != null)
        {
            JOptionPane.showMessageDialog(over, I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst", usesThatFile));

            return;
        }

        runPageEdit(from, newLayoutName, () ->
        {
            // The sequence lives in LayoutPageEdit, handed the session already built; what is above is about
            // whether to ask at all and puts dialogs on screen.  The ORDER of a rename is where every rename
            // defect lived, and a test could not reach it in here.
            String currentLayout = this.ui.layoutList().getSelectedItem().toString();

            // What the last run did, folded in while the naming still agrees with the store: after the rename the
            // store is rekeyed and this session's pages are not.
            this.ui.captureRunningLayout();

            // A rename makes the old name absent by construction, so it is told about that and
            // asks only about the rest (FR-018).
            Map<String, String> renaming = new java.util.LinkedHashMap<>();

            if (rename) renaming.put(currentLayout, newLayoutName);

            Collection<String> keepAbsent = this.ui.settleTheAbsentPages(
                this.ui.getModel().getLayoutList(), renaming, null, over);

            if (keepAbsent == null) return false;

            LayoutPageEdit.renameOrDuplicate(
                this.ui.getModel().getLayoutList(), this.ui.getModel().getLayout(currentLayout),
                this.ui.localLayoutPath(), currentLayout, newLayoutName,
                rename, duplicate, blank, this.ui.autonomySession(), this.ui.getModel(), keepAbsent);

            return true;
        });
    }

    /**
     * Layouts > Download Central Station Layout: `TrainControlUI.downloadCSLayoutMenuItemActionPerformed`'s body.
     */
    void downloadCSLayoutMenuItemActionPerformed()
    {
        // The download switches the layout, which an open editor must not see.
        if (this.ui.refuseWhileEditorOpen()) return;

        if (!this.ui.isLocalLayout() && !this.ui.getModel().getLayoutList().isEmpty())
        {
            SwingUtilities.invokeLater(() ->
            {
                // Asked again, as Create New Layout does
                if (this.ui.refuseWhileEditorOpen()) return;

                // One at a time: a press queued before the spinner went up runs inside its modal loop
                if (!this.downloading.compareAndSet(false, true))
                {
                    JOptionPane.showMessageDialog(this.ui, I18n.t("layout.ui.infoDownloadAlreadyRunning"));
                    return;
                }

                try
                {
                    JOptionPane.showMessageDialog(
                        this.ui,
                        I18n.t("layout.ui.infoChooseFolderForCentralStationDownload")
                    );

                    // Prompt the user to choose a folder
                    JFileChooser fc = new QuickFileChooser(
                        TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, new File(".").getAbsolutePath())
                    );

                    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

                    if (fc.showOpenDialog(this.ui) == JFileChooser.APPROVE_OPTION && replaceIfAsked(fc.getSelectedFile()))
                    {
                        downloadAndOpen(fc.getSelectedFile());
                    }
                }
                finally
                {
                    // After the whole download: BusyDialog.run returns once its event-thread half has run
                    this.downloading.set(false);
                }
            });
        } 
        else
        {
            JOptionPane.showMessageDialog(
                this.ui,
                I18n.t("layout.ui.errorNoCentralStationLayoutAvailable")
            );
        }
    }

    /** Whether a download from the station is under way, from its folder chooser to the copy opened. */
    private final AtomicBoolean downloading = new AtomicBoolean();

    /** False when `folder` holds a layout and the user would not replace it; asked before anything is fetched. */
    private boolean replaceIfAsked(File folder)
    {
        if (!CS2File.holdsALayout(folder)) return true;

        Object[] options = {I18n.t("layout.ui.replace"), I18n.t("ui.cancel")};

        return JOptionPane.showOptionDialog(this.ui,
            I18n.f("layout.ui.confirmReplaceLayoutInFolder", folder.getAbsolutePath()),
            I18n.t("layout.ui.titleReplaceLayout"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null,
            options, options[1]) == 0;
    }

    /** Fetches the station's layout into `path` off the event thread, behind a spinner, then opens the copy. */
    private void downloadAndOpen(File path)
    {
        // Held rather than thrown: the work half must not raise a dialog
        final Exception[] failed = new Exception[1];

        BusyDialog.run(this.ui, I18n.t("layout.ui.busyDownloadingLayout"), () ->
        {
            try
            {
                this.ui.getModel().downloadLayout(path);
            }
            catch (Exception e)
            {
                failed[0] = e;
            }
        }, () ->
        {
            if (failed[0] != null)
            {
                this.ui.getModel().logf("layout.errorDownloadingCentralStationLayout");
                this.ui.getModel().log(failed[0]);

                JOptionPane.showMessageDialog(
                    this.ui,
                    I18n.f("layout.ui.errorDownloadFailedWithMessage", failed[0].getMessage())
                );
            }
            // An editor on its way meanwhile must not see the switch; the copy stays on disk for Open Layout
            else if (!this.ui.refuseWhileEditorOpen())
            {
                // Opened as Open Layout opens a folder, which checks it first, says why when it will not open, and
                // otherwise rebuilds the page list, the autonomy session and the Autonomy menu and records the source
                if (this.ui.openLayoutFolder(path.getAbsoluteFile()))
                {
                    this.ui.showLayoutTab();

                    JOptionPane.showMessageDialog(
                        this.ui,
                        I18n.f("layout.ui.infoDownloadCompleteLayoutSavedIn", path.getAbsolutePath())
                    );
                }
            }
        });
    }

    /** Every page of the local layout, as objects, in the order the window lists them. */
    private List<LayoutDiagram> everyLoadedPage()
    {
        List<LayoutDiagram> pages = new ArrayList<>();

        for (String name : this.ui.getModel().getLayoutList())
        {
            LayoutDiagram page = this.ui.getModel().getLayout(name);

            if (page != null) pages.add(page);
        }

        return pages;
    }

    /**
     * The page itself, then every page its links lead to, without repeats.  Read off the tiles (a link carries the
     * INDEX of its page), so it works whether or not autonomy has been set up on this layout.
     */
    private List<String> pagesLinkedFrom(String from)
    {
        List<String> out = new ArrayList<>();

        out.add(from);

        LayoutDiagram page = this.ui.getModel().getLayout(from);

        if (page == null) return out;

        List<String> all = this.ui.getModel().getLayoutListByName();

        // Left to right, top to bottom, so the same diagram always produces the same page
        for (int y = 0; y < page.getSy(); y++)
        {
            for (int x = 0; x < page.getSx(); x++)
            {
                LayoutDiagramComponent tile = page.getComponent(x, y);

                if (tile == null || !tile.isLink()) continue;

                int index = tile.getRawAddress();

                // The page the link goes to by its ID; one with none is a place in the name-sorted list
                String target = tile.getLinkPageName() != null ? tile.getLinkPageName()
                    : index >= 0 && index < all.size() ? all.get(index) : null;

                if (target == null) continue;

                if (!out.contains(target)) out.add(target);
            }
        }

        return out;
    }

    /**
     * Fills the new page in: each source page's squares, stacked with a blank row between, written square by square
     * because a LayoutDiagramComponent carries its own coordinates.  The file on disk is briefly stale (written before
     * the arrows are re-aimed); `refreshLayouts` re-reads the corrected sources and this refills the copy from them
     * through the copy constructor, which carries `rawAddress`, so a link keeps its destination.  `pages` has the one
     * being combined first.
     */
    private void fillCombinedPage(String name, List<String> pages) throws Exception
    {
        LayoutDiagram made = this.ui.getModel().getLayout(name);

        if (made == null) throw new Exception(I18n.f("layout.ui.errorLayoutPageDoesNotExist", name));

        Dimension size = combinedSize(pages);

        int width = size.width;
        int height = size.height;

        // The ceiling every other growing gesture asks: addRowsAndColumns has none and can grow a page most (24 x 51
        // against 60).  Refused before the page is emptied, since a page above the ceiling can never be grown again.
        if (height > LayoutEditor.MAX_SIZE || width > LayoutEditor.MAX_SIZE)
        {
            throw new Exception(I18n.f("layout.ui.errorMaxSizeExceeded", LayoutEditor.MAX_SIZE));
        }

        made.clear();

        // Grown rather than built to size: the copy started as whichever page was being combined, and
        // a page can only be made bigger from here
        made.addRowsAndColumns(Math.max(0, height - made.getSy()), Math.max(0, width - made.getSx()));

        int top = 0;

        for (String each : pages)
        {
            LayoutDiagram page = this.ui.getModel().getLayout(each);

            if (page == null) continue;

            for (int y = 0; y < page.getSy(); y++)
            {
                for (int x = 0; x < page.getSx(); x++)
                {
                    LayoutDiagramComponent tile = page.getComponent(x, y);

                    if (tile == null) continue;

                    LayoutDiagramComponent copy = new LayoutDiagramComponent(tile);

                    copy.setX(x);
                    copy.setY(y + top);

                    // Nothing autonomy has written comes across: this page is excluded from autonomy for good, so a
                    // station label would name a station not on it, and a "Point:" label would be migrated into the
                    // setup.  The user's own text stays.
                    if (copy.getLabel() != null && copy.getLabel().startsWith(
                        AutonomySession.STATION_LABEL_PREFIX))
                    {
                        copy.setLabel("");
                    }

                    made.addComponent(copy, x, y + top);
                }
            }

            top += page.getSy() + 1;
        }

        // In place, under its own name
        made.saveChanges(null, false);
    }
}
