package org.traincontrol.gui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.gui.layouteditor.LayoutEditor;
import org.traincontrol.gui.widgets.QuickFileChooser;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * The layout's folder and its pages: Duplicate, Rename and Add Blank Page, Combine Linked Pages, the page index with
 * its arrows kept aimed, a new layout from the demo archive, and the Central Station's layout downloaded.
 *
 * Not the locomotive mapping pages (copy, paste and clear), which stay on the window.
 */
final class LayoutFileActions
{
    private final TrainControlUI ui;

    LayoutFileActions(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Writes the page index and re-aims every arrow on the layout at the page it was aimed at.
     *
     * **A LINK TILE HOLDS A POSITION IN THE NAME-SORTED PAGE LIST**, so every operation that changes the set of page names
     * changes what the arrows mean (Add, Rename, Duplicate, Delete and Combine); nothing re-aimed them, and adding one page repointed
     * FOUR OF SEVEN arrows on the sample layout.  **Delete and Combine go through here**; Add, Duplicate and Rename go through
     * `LayoutPageEdit`, which re-aims the arrows before it writes the page and then writes the index itself
     * (`LayoutDiagram.writeLayoutIndex`).  The order the arrows were written against is worked out here rather than by the caller,
     * and taken from the PAGES this session holds, not from the index file, because an index naming two pages the same aliases them
     * onto one entry.  A page that has gone leaves its arrows pointing at nothing rather than at whatever slid into
     * its place: see `LayoutDiagramComponent.setLinkedPageIndex`.
     *
     * @param layoutList the new page list, name-sorted, as writeLayoutIndex takes it
     * @param renamed old name -&gt; new name, or null - which both callers pass; a rename goes through `LayoutPageEdit`
     * @param keepAbsent pages to hold in the index though they are not loaded (FR-018), or null
     * @throws java.io.IOException if the index cannot be written, which is the caller's to report
     */
    void writeIndexAndKeepLinksAimed(List<String> layoutList, Map<String, String> renamed,
        java.util.Collection<String> keepAbsent) throws java.io.IOException
    {
        // The rule itself is in LayoutDiagram, because LayoutPageEdit writes this index too - see that
        // method.  What is here is collecting the pages, saving the ones that moved, and the logging.
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

    /**
     * Unzips an empty layout to the current folder and returns the path
     * @return
     */
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
            this.unzipFile(Paths.get(to.getPath()), outputFolder.getAbsolutePath());
            to.delete();

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

    /**
     * Copies a JAR resource file to the specified path
     * @param resource
     * @param output
     * @throws IOException 
     */
    private void copyResource(String resource, File output) throws IOException
    {
        InputStream is = TrainControlUI.class.getResource(resource).openStream();
        OutputStream os = new FileOutputStream(output.getPath());

        byte[] b = new byte[2048];
        int length;

        while ((length = is.read(b)) != -1)
        {
            os.write(b, 0, length);
        }

        is.close();
        os.close();
    }

    /**
     * Unzips a file
     * https://howtodoinjava.com/java/io/unzip-file-with-subdirectories/
     * @param filePathToUnzip
     * @throws java.io.IOException
     */
    private void unzipFile(Path filePathToUnzip, String folderName)
    {
        Path parentDir = filePathToUnzip.getParent();
        String fileName = filePathToUnzip.toFile().getName();
        Path targetDir = Paths.get(folderName);

        //Open the file
        try (ZipFile zip = new ZipFile(filePathToUnzip.toFile()))
        {
            FileSystem fileSystem = FileSystems.getDefault();
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

                    try(InputStream in = zip.getInputStream(entry))
                    {
                        Files.copy(in, f.toPath());
                    }
                    catch (Exception e)
                    {
                        this.ui.getModel().log(e);
                    }
                }
            }
        } 
        catch (IOException e)
        {
            this.ui.getModel().log(e);
        }
    }

    /**
     * Layouts > Create New Layout: `TrainControlUI.initializeLocalLayoutMenuItemActionPerformed`'s body.
     */
    void initializeLocalLayoutMenuItemActionPerformed()
    {
        // NOT WHILE AUTONOMY RUNS, before its folder window, as every other Layout menu door that changes the
        // diagram is not (MT-141): a folder chosen switched the layout, synced with the Central Station and reset the
        // autonomy session under the moving trains
        if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;

        javax.swing.SwingUtilities.invokeLater(() -> 
        {
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

                TrainControlUI.layoutFolderIs(filepath);

                // The editing controls answer differently now (OB-126): this is what decides whether
                // there is a layout of his own to edit.
                this.ui.applyLayoutEditingAvailability();
                
                this.ui.initializeLocalLayoutMenuItem().setEnabled(false);

                this.ui.createAndApplyEmptyLayout(filepath, false);
            }
        });
    }

    /**
     * Builds one page showing this page and every page it links to: a link is a hole in the diagram that comes out somewhere else, and
     * following one means losing sight of where you were, so this lays the pages the current one reaches out one under another.
     *
     * ONE LEVEL, deliberately: following links recursively on a layout whose pages all reach each other produces every page, in an order
     * nobody chose.  The result is EXCLUDED FROM AUTONOMY as it is written, and this is not optional: every square on it is a redrawing
     * of a square that exists elsewhere, so left included it would mint a second Point for every sensor and a parallel set of edges (the
     * "Combined page problem" of the diagram-autonomy plan).  Pages are stacked with a blank row between them, in the order the links
     * appear when the page is read left to right and top to bottom, so two runs over the same diagram produce the same page.
     */
    void combineLinkedPages()
    {
        // The same rule as deleting and renaming: this writes a new page and rebuilds the session.
        if (this.ui.refuseWhileARunHoldsTheSetup(this.ui)) return;

        if (this.ui.refuseWhileEditorOpen()) return;

        if (!this.ui.isLocalLayout())
        {
            JOptionPane.showMessageDialog(this.ui,
                I18n.t("layout.ui.errorCannotManipulateRemoteLayouts"));
            return;
        }

        final String from = this.ui.layoutList().getSelectedItem().toString();

        final List<String> pages = pagesLinkedFrom(from);

        if (pages.size() < 2)
        {
            JOptionPane.showMessageDialog(this.ui, I18n.t("layout.ui.infoNothingLinkedFromHere"));
            return;
        }

        String name = JOptionPane.showInputDialog(this.ui,
            I18n.t("layout.ui.promptCombinedPageName"),
            I18n.f("layout.ui.combinedPageName", from));

        if (name == null || name.trim().isEmpty()) return;

        final String combined = name.trim();

        // AND NO OTHER PAGE'S FILE: this name is typed freely, so a character a file name cannot hold comes to the
        // same file as the page whose name has an underscore there
        String usesThatFile = org.traincontrol.automationui.LayoutPageEdit.pageUsingTheFileOf(this.ui.getModel().getLayoutList(),
            combined, null);

        if (usesThatFile != null)
        {
            JOptionPane.showMessageDialog(this.ui,
                I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst", usesThatFile));

            return;
        }

        if (this.ui.getModel().getLayoutList().contains(combined))
        {
            JOptionPane.showMessageDialog(this.ui,
                I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst", combined));
            return;
        }

        // The ceiling, asked before anything is made: the check must not live in `fillCombinedPage`, which runs after the page
        // has been written, entered in the index, excluded and the layouts refreshed, so refusing would leave an unfilled copy of the page combined
        // from.  The size is the same arithmetic `fillCombinedPage` does: the widest source page, and every source page's height plus a blank
        // row between them.
        int wouldBeWide = 0;
        int wouldBeHigh = 0;

        for (String each : pages)
        {
            LayoutDiagram page = this.ui.getModel().getLayout(each);

            if (page == null) continue;

            wouldBeWide = Math.max(wouldBeWide, page.getSx());
            wouldBeHigh += page.getSy() + 1;
        }

        if (wouldBeHigh > LayoutEditor.MAX_SIZE || wouldBeWide > LayoutEditor.MAX_SIZE)
        {
            JOptionPane.showMessageDialog(this.ui,
                I18n.f("layout.ui.errorMaxSizeExceeded", LayoutEditor.MAX_SIZE));
            return;
        }

        // On a worker, because it parses every page of the layout twice over.  AND A REFRESH UNDER WAY FROM HERE:
        // the worker writes the page, the index and the setup before it hands over to `layoutEditingComplete`, and a run
        // started meanwhile ran the railway from before and was forgotten by the reset at the end
        this.ui.refreshesUnderWay.incrementAndGet();

        new Thread(() ->
        {
            try
            {
                // Asked BEFORE the new page is written (FR-018), as the delete path does: a Cancel offered at the index write would come after a page
                // file had appeared on disk, leaving a page nothing references.  Nothing is being removed here, so every absence is a surprise.
                java.util.Collection<String> keepAbsent = this.ui.settleTheAbsentPages(
                    this.ui.getModel().getLayoutList(), null, null);

                if (keepAbsent == null) return;

                // Made the way every other page is made, by saving an existing one under a new name: a LayoutDiagram built by hand cannot save itself,
                // since it writes by resolving its new filename against the path it was constructed with, and only a page read from disk carries the
                // path of a FILE.  So the page is created by the call the Duplicate Page menu item uses, and then filled in.
                this.ui.getModel().getLayout(from).saveChanges(combined, true);

                List<String> layoutList = this.ui.getModel().getLayoutList();

                layoutList.add(combined);

                writeIndexAndKeepLinksAimed(layoutList, null, keepAbsent);

                // Excluded BEFORE the pages are re-read, so no build ever sees it as a page to walk: included for even one build, every sensor on it
                // becomes a second Point for a sensor that already has one.  The session ALREADY BUILT, and only a setup that is already there: the
                // lazy getter builds a session (opening every page and running the caption migration) and saveQuietly then writes setup.json
                // unconditionally, so combining two pages on a layout that never had autonomy created one out of a drawing gesture.  Nothing is lost
                // by declining: excludeRepeatedSensorPages shuts a combined page automatically the first time a setup IS created.
                org.traincontrol.automationui.AutonomySession session = this.ui.autonomySession();

                if (session != null)
                {
                    session.setPageExcluded(combined, true);

                    // And the answer is read here too: a failed write here means the combined page is excluded in
                    // memory and not on disk, so the next load offers it to autonomy.
                    if (session.exists() && !session.saveQuietly() && this.ui.getModel() != null)
                    {
                        this.ui.getModel().logf("autosetup.log.saveFailedAfterCombine");
                    }
                }

                // Re-read, so the new page exists as an object with its own file behind it
                this.ui.getModel().refreshLayouts();

                fillCombinedPage(combined, pages);

                javax.swing.SwingUtilities.invokeLater(() -> this.ui.layoutEditingComplete(() ->
                {
                    this.ui.layoutList().setSelectedItem(combined);
                    this.ui.repaintLayout();
                }));
            }
            catch (Exception ex)
            {
                this.ui.getModel().log(ex);

                javax.swing.SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this.ui,
                        I18n.f("layout.ui.errorSavingLayoutWithMessage", ex.getMessage())));
            }
            finally
            {
                // Posted after the hand-over above, so `layoutEditingComplete` has counted its own before this one goes
                javax.swing.SwingUtilities.invokeLater(() -> this.ui.refreshesUnderWay.updateAndGet(n -> Math.max(0, n - 1)));
            }
        }).start();
    }

    /**
     * Duplicates or renames the current layout page, optionally clearing it
     * @param newLayoutName - name of the new layout
     * @param rename - true if renaming
     * @param duplicate - true if duplicating
     * @param blank - true to clear
     * @param from the layout editor that asked (FR-109), or null for the Layouts menu: the one editor not refused as
     *        open; the questions hang off it, it steps aside once nothing can refuse, and it is told which page to
     *        show however this ends
     */
    void duplicateOrRenameCurrentLayout(String newLayoutName, boolean rename, boolean duplicate, boolean blank,
        final LayoutEditor from)
    {
        final java.awt.Component over = from == null ? this.ui : from;

        // Not while an editor holds the diagram: both of these finish by calling layoutEditingComplete, which re-enables the Edit button and
        // rebuilds the autonomy session from pages it has just replaced, so under an open editor it fails (a second
        // editor opens on the same diagram, and closing either unsets the other's edit flag).  Refused at the entry rather than guarded at
        // the end, because by the end the page has already been written.
        if (from == null && this.ui.refuseWhileEditorOpen()) return;

        // And not while trains are running (MT-141): an editor being closed does not mean the railway is standing still, and renaming a page
        // still rebuilds the session underneath whatever is moving.
        if (this.ui.refuseWhileARunHoldsTheSetup(over)) return;

        if (!this.ui.isLocalLayout())
        {
            JOptionPane.showMessageDialog(
                over,
                I18n.t("layout.ui.errorCannotManipulateRemoteLayouts")
            );
            return;
        }

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
        String usesThatFile = org.traincontrol.automationui.LayoutPageEdit.pageUsingTheFileOf(this.ui.getModel().getLayoutList(),
            newLayoutName, rename ? this.ui.layoutList().getSelectedItem().toString() : null);

        if (usesThatFile != null)
        {
            JOptionPane.showMessageDialog(over, I18n.f("layout.ui.errorPageAlreadyExistsDeleteOrRenameFirst", usesThatFile));

            return;
        }

        final Runnable work = () ->
        {
            boolean arriving = false;

            try
            {
                // The sequence itself lives in LayoutPageEdit, and this hands it the session it has ALREADY BUILT rather than letting it reach
                // for the lazy getter.  Everything above this line is about whether to ask at all (an open editor, running trains, a remote layout, a
                // name already taken) and stays here because it puts dialogs on screen; everything below it was the ORDER in which a rename has to
                // happen, where every rename defect has lived, and which could not be reached by a test while it was in here (Adam: "trigger a rename
                // via the same function that the UI calls... This is the only way to catch bugs across these complex types of features.").
                String currentLayout = this.ui.layoutList().getSelectedItem().toString();

                // What the last run did, folded in while the naming still agrees with the store: after the rename below the store is rekeyed
                // and the pages this session holds are not, which is why the capture that follows is refused.  Doing it here means the run's
                // placements are IN the configuration by the time renamePage rekeys it.
                this.ui.captureRunningLayout();

                // A rename makes the old name absent by construction, so it is told about that and
                // asks only about the rest (FR-018).
                java.util.Map<String, String> renaming = new java.util.LinkedHashMap<>();

                if (rename) renaming.put(currentLayout, newLayoutName);

                java.util.Collection<String> keepAbsent = this.ui.settleTheAbsentPages(
                    this.ui.getModel().getLayoutList(), renaming, null);

                if (keepAbsent == null) return;

                org.traincontrol.automationui.LayoutPageEdit.renameOrDuplicate(
                    this.ui.getModel().getLayoutList(), this.ui.getModel().getLayout(currentLayout),
                    this.ui.localLayoutPath(), currentLayout, newLayoutName,
                    rename, duplicate, blank, this.ui.autonomySession(), this.ui.getModel(), keepAbsent);

                this.ui.layoutEditingComplete(() ->
                {
                    // Posted, so it lands after the two below
                    if (from != null) from.arriveOn(newLayoutName);

                    this.ui.layoutList().setSelectedItem(newLayoutName);
                    this.ui.repaintLayout();
                });

                arriving = true;
            }
            catch (Exception ex)
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
        };

        if (from == null) javax.swing.SwingUtilities.invokeLater(work);
        else from.stepOutFor(work);
    }

    /**
     * Layouts > Download Central Station Layout: `TrainControlUI.downloadCSLayoutMenuItemActionPerformed`'s body.
     */
    void downloadCSLayoutMenuItemActionPerformed()
    {

        if (!this.ui.isLocalLayout() && !this.ui.getModel().getLayoutList().isEmpty())
        {
            javax.swing.SwingUtilities.invokeLater(() ->
            {
                JOptionPane.showMessageDialog(
                    this.ui,
                    I18n.t("layout.ui.infoChooseFolderForCentralStationDownload")
                );

                try
                {
                    // Prompt the user to choose a folder
                    JFileChooser fc = new QuickFileChooser(
                        TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, new File(".").getAbsolutePath())
                    );

                    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

                    int i = fc.showOpenDialog(this.ui);

                    if (i == JFileChooser.APPROVE_OPTION)
                    {
                        File path = fc.getSelectedFile();

                        this.ui.getModel().downloadLayout(path);

                        // Load the layout - the source a later switch of source compares with, too
                        this.ui.useTheDownloadedLayout(path);

                        // The editing controls answer differently now (OB-126): this is what decides whether
                        // there is a layout of his own to edit.
                        this.ui.applyLayoutEditingAvailability();
                        this.ui.syncWithCS2();
                        this.ui.repaintLayout();
                        this.ui.showLayoutTab();

                        JOptionPane.showMessageDialog(
                            this.ui,
                            I18n.f("layout.ui.infoDownloadCompleteLayoutSavedIn", path.getAbsolutePath())
                        );
                    }
                }
                catch (Exception e)
                {
                    this.ui.getModel().logf("layout.errorDownloadingCentralStationLayout");
                    this.ui.getModel().log(e);

                    JOptionPane.showMessageDialog(
                        this.ui,
                        I18n.f("layout.ui.errorDownloadFailedWithMessage", e.getMessage())
                    );
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

    /**
     * Every page of the local layout, as objects.
     *
     * @return the loaded pages, in the order the window lists them
     */
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
     * The page itself, then every page its links lead to, without repeats.  A link carries the INDEX of the page it jumps to (the number
     * goToLayoutPage takes), so the answer is read off the tiles rather than out of the autonomy setup, and works whether or not autonomy
     * has ever been set up on this layout.
     *
     * @param from the page to start at
     * @return that page first, then the pages it reaches
     */
    private List<String> pagesLinkedFrom(String from)
    {
        List<String> out = new java.util.ArrayList<>();

        out.add(from);

        LayoutDiagram page = this.ui.getModel().getLayout(from);

        if (page == null) return out;

        List<String> all = this.ui.getModel().getLayoutList();

        // Left to right, top to bottom, so the same diagram always produces the same page
        for (int y = 0; y < page.getSy(); y++)
        {
            for (int x = 0; x < page.getSx(); x++)
            {
                org.traincontrol.base.LayoutDiagramComponent tile = page.getComponent(x, y);

                if (tile == null || !tile.isLink()) continue;

                int index = tile.getRawAddress();

                if (index < 0 || index >= all.size()) continue;

                String target = all.get(index);

                if (!out.contains(target)) out.add(target);
            }
        }

        return out;
    }

    /**
     * Fills the new page in: each source page's squares, stacked with a blank row between.  The page already exists (made as a copy of the
     * one being combined), so this empties it, grows it to fit, and writes the squares in square by square rather than by reference,
     * because a LayoutDiagramComponent carries its own coordinates.
     *
     * **A LINK ON A COMBINED PAGE DOES KEEP ITS DESTINATION, AND THE SEQUENCE IS WHY** (TWV): the copy is written from the source
     * page BEFORE the arrows are re-aimed, so the file on disk is briefly stale; `refreshLayouts` then re-reads every page, including the
     * corrected sources; and this method clears the copy and refills it from those sources through `LayoutDiagramComponent`'s copy
     * constructor, which carries `rawAddress` across, so the squares written here are the corrected ones and the save at the end commits
     * them.  The stale intermediate write makes this look broken to anybody who reads only the first step.
     *
     * @param name the page just created
     * @param pages the source pages, the one being combined first
     */
    private void fillCombinedPage(String name, List<String> pages) throws Exception
    {
        LayoutDiagram made = this.ui.getModel().getLayout(name);

        if (made == null) throw new Exception(I18n.f("layout.ui.errorLayoutPageDoesNotExist", name));

        int width = 0;
        int height = 0;

        for (String each : pages)
        {
            LayoutDiagram page = this.ui.getModel().getLayout(each);

            if (page == null) continue;

            width = Math.max(width, page.getSx());
            height += page.getSy() + 1;
        }

        // The ceiling every other growing gesture asks: this goes straight to LayoutDiagram.addRowsAndColumns, which has no ceiling
        // and it can grow a page MOST (combining the three pages that 1 - Main links to comes
        // out 24 x 51 against a ceiling of 60).  Refused BEFORE the page is emptied: `made.clear()` below throws away the page this was
        // copied from, and a page above the ceiling can never be grown again (Increase Size is grayed on it).
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
                    org.traincontrol.base.LayoutDiagramComponent tile = page.getComponent(x, y);

                    if (tile == null) continue;

                    org.traincontrol.base.LayoutDiagramComponent copy =
                        new org.traincontrol.base.LayoutDiagramComponent(tile);

                    copy.setX(x);
                    copy.setY(y + top);

                    // Nothing autonomy has written comes across: this page is a picture, excluded from autonomy for good, so a station label copied onto
                    // it names a station that is nowhere on this page, and a "Point:" label (the OLD way of storing a caption) would be picked up by the
                    // migration and written into the setup for squares on an excluded page.  The user's own text stays: a yard name or a note is part of
                    // the drawing.
                    if (copy.getLabel() != null && copy.getLabel().startsWith(
                        org.traincontrol.automationui.AutonomySession.STATION_LABEL_PREFIX))
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
