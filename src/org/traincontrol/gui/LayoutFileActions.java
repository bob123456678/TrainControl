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
     * Writes the page index and re-aims every arrow on the layout at the page it was aimed at.  A link tile holds a
     * position in the name-sorted page list, so any change to the set of page names changes what the arrows mean.
     * Delete and Combine go through here; Add, Duplicate and Rename go through `LayoutPageEdit`.  The order is taken
     * from the pages this session holds, not the index file, because an index naming two pages the same aliases them.
     * A page that has gone leaves its arrows pointing at nothing (`LayoutDiagramComponent.setLinkedPageIndex`).
     *
     * @param layoutList the new page list, name-sorted, as writeLayoutIndex takes it
     * @param renamed old name -&gt; new name, or null - which both callers pass; a rename goes through `LayoutPageEdit`
     * @param keepAbsent pages to hold in the index though they are not loaded (FR-018), or null
     * @throws java.io.IOException if the index cannot be written, which is the caller's to report
     */
    void writeIndexAndKeepLinksAimed(List<String> layoutList, Map<String, String> renamed,
        java.util.Collection<String> keepAbsent) throws java.io.IOException
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
        // Not while autonomy runs, before its folder window (MT-141): a folder chosen would switch the layout and reset
        // the session under moving trains.
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
     * Builds one page showing this page and every page it links to, stacked one under another with a blank row between,
     * in the order the links appear reading left to right and top to bottom.  One level, deliberately: following links
     * recursively produces every page, in an order nobody chose.  The result is excluded from autonomy as it is
     * written, not optionally: left included, every square would mint a second Point for a sensor and a parallel set of
     * edges.
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

        // And no other page's file: a character a file name cannot hold comes to the same file as a page with an
        // underscore there.
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

        // The ceiling, asked before anything is made, and not in `fillCombinedPage`, which runs after the page is
        // written and indexed.  The same arithmetic: the widest source page, and every height plus a blank row between.
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

        // On a worker, because it parses every page twice.  And a refresh under way from here: a run started before
        // `layoutEditingComplete` would run the railway from before and be forgotten by the reset.
        this.ui.refreshesUnderWay.incrementAndGet();

        new Thread(() ->
        {
            try
            {
                // Asked before the new page is written (FR-018), as the delete path does: a Cancel at the index write
                // would leave a page nothing references.
                java.util.Collection<String> keepAbsent = this.ui.settleTheAbsentPages(
                    this.ui.getModel().getLayoutList(), null, null);

                if (keepAbsent == null) return;

                // Made as every other page is, by saving an existing one under a new name (the Duplicate Page call): a
                // hand-built LayoutDiagram cannot save itself.
                this.ui.getModel().getLayout(from).saveChanges(combined, true);

                List<String> layoutList = this.ui.getModel().getLayoutList();

                layoutList.add(combined);

                writeIndexAndKeepLinksAimed(layoutList, null, keepAbsent);

                // Excluded before the pages are re-read, so no build sees it as a page to walk.  Only a setup that
                // already exists: the lazy getter builds a session and saveQuietly writes setup.json, so combining on a
                // layout without autonomy would create one.  excludeRepeatedSensorPages shuts a combined page when a
                // setup is first created.
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

        // Not while an editor holds the diagram: both finish by calling layoutEditingComplete, which rebuilds the
        // autonomy session from pages just replaced, so under an open editor a second editor opens on the same diagram.
        // Refused at the entry, as by the end the page is written.
        if (from == null && this.ui.refuseWhileEditorOpen()) return;

        // And not while trains are running (MT-141): renaming a page rebuilds the session underneath whatever is
        // moving.
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
                // The sequence lives in LayoutPageEdit, handed the session already built; what is above is about
                // whether to ask at all and puts dialogs on screen.  The ORDER of a rename is where every rename defect
                // lived, and a test could not reach it in here ("trigger a rename via the same function that the UI
                // calls...").
                String currentLayout = this.ui.layoutList().getSelectedItem().toString();

                // What the last run did, folded in while the naming still agrees with the store: after the rename the
                // store is rekeyed and this session's pages are not.
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
     * The page itself, then every page its links lead to, without repeats.  Read off the tiles (a link carries the
     * INDEX of its page), so it works whether or not autonomy has been set up on this layout.
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
     * Fills the new page in: each source page's squares, stacked with a blank row between, written square by square
     * because a LayoutDiagramComponent carries its own coordinates.  The copy is written from the source page before
     * the arrows are re-aimed, so the file on disk is briefly stale; `refreshLayouts` re-reads the corrected sources
     * and this refills the copy from them through the copy constructor, which carries `rawAddress`, so a link keeps its
     * destination.
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
                    org.traincontrol.base.LayoutDiagramComponent tile = page.getComponent(x, y);

                    if (tile == null) continue;

                    org.traincontrol.base.LayoutDiagramComponent copy =
                        new org.traincontrol.base.LayoutDiagramComponent(tile);

                    copy.setX(x);
                    copy.setY(y + top);

                    // Nothing autonomy has written comes across: this page is excluded from autonomy for good, so a
                    // station label would name a station not on it, and a "Point:" label would be migrated into the
                    // setup.  The user's own text stays.
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
