package org.traincontrol.gui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.swing.JOptionPane;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * The data backup: File > Backup TrainControl Data, and the backup Download & Update takes before it replaces anything
 * (FR-125).  One archive in `tc_backup` holds the locomotive database, the window state, the routes, the layout's folder
 * and the cropped icons.
 *
 * The lock that keeps two backups apart is the window's (`TrainControlUI.backupData`); this writes one inside it.
 */
final class BackupAction
{
    private final TrainControlUI ui;

    BackupAction(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * Puts a yes/no question on screen from whatever thread is asking, and waits for the answer.  The backup runs on its own
     * thread and can neither show a dialog directly nor carry on without the answer, so the worker blocks and the event thread
     * asks.  Must not be called ON the event thread, where invokeAndWait deadlocks outright.
     *
     * @param question what to ask
     * @return true only for an explicit yes
     */
    private boolean askOnEventThread(String question)
    {
        final boolean[] yes = new boolean[1];

        Runnable ask = () -> yes[0] = JOptionPane.showConfirmDialog(this.ui, question,
            I18n.t("ui.main.toolbar.backupData"), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;

        if (javax.swing.SwingUtilities.isEventDispatchThread())
        {
            ask.run();

            return yes[0];
        }

        try
        {
            javax.swing.SwingUtilities.invokeAndWait(ask);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (java.lang.reflect.InvocationTargetException e)
        {
            // A question nobody could be asked is a no: the caller is about to do something optional
            // and expensive, and doing it unasked is the worse of the two mistakes.
            if (this.ui.getModel() != null) this.ui.getModel().log(e);
        }

        return yes[0];
    }

    /**
     * The timestamp the other backup files are named with, so a folder copy sits beside them and is
     * obviously part of the same backup rather than a stray directory.
     */
    private String prefixForBackup()
    {
        return "backup" + Conversion.convertSecondsToDatetime(System.currentTimeMillis())
            .replace(':', '-').replace(' ', '_');
    }

    /**
     * `backupData`'s body, which only `backupData` calls.  It takes no lock of its own: it is called only through
     * `TrainControlUI.backupData`, which holds `oneBackupAtATime` around it.
     *
     * @return the archive, or no archive, and what could not be saved
     */
    BackupOutcome writeTheBackup()
    {
        // The track diagram and the autonomy setup beside it: for a locally stored layout, config/ holds the pages AND config/autonomy holds
        // every station name, direction, caption and configuration, and that folder is the only copy of them anywhere.
        List<String> unsaved = new java.util.ArrayList<>();

        File backupArchive = null;

        // Set only once the archive has been written, so a failure part way reports no archive
        final File[] made = new File[1];

        // The temporary copy of a Central Station layout, if one was fetched (FR-020). Removed in
        // the finally, whatever happens to the archive.
        File centralStationCopy = null;

        // And TrainControl's OWN route database, exported (FR-021). Same treatment.
        File routesExport = null;

        this.ui.backupDataMenuItem().setEnabled(false);

        // Given back whatever happens.  It is switched off so a second backup cannot start while
        // one is running, and everything between here and the end of the try can throw - a full
        // disk, a locked file, a tree deep enough to overflow the stack.  Left off, the only way to
        // back up again is to restart the application, and the user is not told why.
        try
        {
            // Written LIVE first, so the archive holds the state as it is now rather than as it was when something last happened to save it.
            // WITHOUT the session capture (IAR-A2): writing live must not imply it, or this menu item would commit whatever was open in the
            // autonomy editor to setup.json and every configuration file from a thread that is not the event thread, and the editor's Cancel
            // could not undo it (discardEdits re-reads the file just written).  Taking a copy must not change anything.
            this.ui.saveState(false, false);
            this.ui.getModel().saveState(false);

            // One archive, holding all of it (FR-015; Adam: "the backup menu option should export a zip file with the locdb and uistate files,
            // track diagram files, and autonomy files - effectively, all state."): the autonomy setup is keyed by PAGE ID and those ids are
            // defined by config/gleisbild.cs2, so setup.json alone means nothing and a gleisbild.cs2 from a different day silently reattaches
            // every station to the wrong page.  These files are only a backup together.
            java.util.Map<String, File> state = new java.util.LinkedHashMap<>();

            state.put(TrainControlUI.DATA_FILE_NAME, new File(Util.dataPath(TrainControlUI.DATA_FILE_NAME)));
            state.put(org.traincontrol.marklin.MarklinControlStation.DATA_FILE_NAME,
                new File(Util.dataPath(org.traincontrol.marklin.MarklinControlStation.DATA_FILE_NAME)));

            // TrainControl's OWN routes, exported as JSON (FR-021; Adam: "OB 90 should run an export of our route db."): the `fahrstrassen.cs2`
            // in the config folder is the CENTRAL STATION's copy, and routes are edited in this application, whose route database is what runs.
            // Written through the door the Export Routes menu item uses, so Import Routes will read it back.  A failure here is named and the
            // rest of the archive still written: losing the whole backup because one part could not be serialised is the wrong trade.
            try
            {
                routesExport = java.io.File.createTempFile("tc-routes", ".json");

                java.nio.file.Files.write(routesExport.toPath(),
                    this.ui.getModel().exportRoutes().getBytes(StandardCharsets.UTF_8));

                state.put("routes.json", routesExport);
            }
            catch (Exception routes)
            {
                unsaved.add("routes.json: " + routes);

                if (routesExport != null)
                {
                    routesExport.delete();

                    routesExport = null;
                }
            }

            // The LEGACY autonomy file (IAR-B1): for an operator coming from the hand-authored graph this is what their setup is imported from
            // (nothing writes it any more, OB-254), and it was in neither half of the backup (this archive, or the copy into the backup folder,
            // which only a timestamped save makes).  Skipped silently when it is not there, like every other source.
            state.put(TrainControlUI.AUTONOMY_FILE_NAME,
                new File(TrainControlUI.AUTONOMY_FILE_NAME));

            String localLayout = this.ui.localLayoutPath();

            // The layout lives on the CENTRAL STATION, so there is nothing local to put in the archive (FR-020; Adam: "for cs2 files, we have a
            // top menu option to download a layout from the station. so a backup run in this mode could ask the user if they want to include
            // this data."): asked rather than assumed, because it is a network fetch of every page, pointless if the Central Station is off.
            // Declining leaves the archive as it was, and the dialog at the end says what is not in it either way.  There is no autonomy setup to
            // pair these with (`AutonomyCompanionStore` refuses a layout that is not local), so an archive holding only them is complete.
            // Not offered without a station answering (OB-098; Adam: "backup up files from cs should not be offered in this case"): asking is a
            // promise to fetch.  Through the one predicate, like the other two offers: `getNetworkCommState` alone reports whether the last SYNC
            // succeeded, and a sync reads through CS2File, which reads a local layout folder happily, so a simulated session calls itself
            // connected (Adam: "switch to central station layout is NOT greyed out in debug/simulate mode.").
            if ((localLayout == null || localLayout.isEmpty())
                && this.ui.isCentralStationConnected()
                && askOnEventThread(I18n.t("ui.askBackupIncludeCentralStationLayout")))
            {
                try
                {
                    centralStationCopy = java.nio.file.Files
                        .createTempDirectory("tc-cs-backup").toFile();

                    this.ui.getModel().downloadLayout(centralStationCopy);

                    state.put("central-station-layout", centralStationCopy);
                }
                catch (Exception cs)
                {
                    // Named in the incomplete-backup dialog like any other source that could not
                    // be read. The rest of the archive is still worth writing.
                    unsaved.add(I18n.t("ui.main.toolbar.downloadCSLayout") + ": " + cs);

                    if (centralStationCopy != null)
                    {
                        Util.deleteTree(centralStationCopy, 0);

                        centralStationCopy = null;
                    }
                }
            }

            if (localLayout != null && !localLayout.isEmpty())
            {
                // The whole folder, under the LAYOUT's own name (MT-159; Adam: "we are missing the folder name of the active layout - the zip contains
                // 'config' instead of 'cs2_sample_layout'"): restoring means putting these files back beside a `gleisbild.cs2` of the same vintage,
                // and a bare `config` leaves two backups of two layouts indistinguishable once they are off this machine.
                File layoutFolder = new File(localLayout).getAbsoluteFile();

                String named = layoutFolder.getName();

                state.put((named == null || named.isEmpty() ? "layout" : named) + "/config",
                    new File(layoutFolder, "config"));
            }

            // The cropped locomotive icons (reviewer): `LocDB.data` records each locomotive's `localImageURL`, which for a cropped icon names a
            // file in `tc_loc_icons`, a folder this archive never held and git does not track, so a restored backup pointed every crop-carrying
            // locomotive at a file it did not contain.  Only if it exists: an installation where nobody has cropped anything gains no empty folder.
            File icons = new File(Util.LOC_ICON_FOLDER).getAbsoluteFile();

            if (icons.isDirectory()) state.put(Util.LOC_ICON_FOLDER, icons);

            backupArchive = new File(Util.getBackupPath(prefixForBackup() + "-TrainControl.zip"));

            unsaved.addAll(Util.zipInto(backupArchive, state));

            made[0] = backupArchive;
        }
        catch (RuntimeException | StackOverflowError e)
        {
            unsaved.add(String.valueOf(e));

            if (this.ui.getModel() != null && this.ui.getModel().isDebug()) this.ui.getModel().log(String.valueOf(e));
        }
        finally
        {
            // The scratch download goes, whether or not the archive was written. A stale copy of
            // the diagram left in a temporary folder is exactly the sort of second copy this
            // application keeps being bitten by.
            if (centralStationCopy != null) Util.deleteTree(centralStationCopy, 0);

            if (routesExport != null) routesExport.delete();

            this.ui.backupDataMenuItem().setEnabled(true);
        }

        return new BackupOutcome(made[0], unsaved);
    }

    /**
     * What `backupData` wrote, and what it could not (FR-125).
     */
    static final class BackupOutcome
    {
        /** The archive, or null when none was written. */
        final File archive;

        /** What could not be saved, each with its reason; empty when the backup is whole. */
        final List<String> unsaved;

        BackupOutcome(File archive, List<String> unsaved)
        {
            this.archive = archive;
            this.unsaved = unsaved;
        }

        /**
         * @return whether the archive was written with everything in it
         */
        boolean isComplete()
        {
            return this.archive != null && this.archive.isFile() && this.unsaved.isEmpty();
        }
    }

    /**
     * File > Backup TrainControl Data: writes the backup off the event thread and says what it holds.
     */
    void backupDataMenuItemActionPerformed()
    {
        new Thread(() ->
        {
            // The writing is backupData's, which an update takes too (FR-125); the reporting is the menu's alone.
            final BackupOutcome done = this.ui.backupData();

            final List<String> unsaved = done.unsaved;

            // Read from the reporting lambda below, which needs it effectively final
            final File[] made = {done.archive};

            // Derived from an actual backup path rather than from BACKUP_FOLDER: getBackupPath falls
            // back to the working directory when the folder cannot be created, and naming the folder
            // unconditionally pointed the user at a directory the backups are not in - in exactly the
            // failure mode the fallback exists for.
            String backupFolder = new File(Util.getBackupPath("x")).getAbsoluteFile().getParent();

            // The full path goes to the LOG, not into the dialog (FR-019; Adam: "you no longer need to print the path - just the filename in the
            // popup, leaving the full path in the log."): a backup path on this machine is long enough to stretch the dialog across the screen.
            final String fullPath = made[0] != null && made[0].isFile()
                ? made[0].getAbsolutePath() : backupFolder;

            if (this.ui.getModel() != null) this.ui.getModel().logf("ui.infoBackupCompleteSavedTo", fullPath);

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                // The file's NAME in the dialog, its folder behind the button.
                final String shown = made[0] != null && made[0].isFile()
                    ? made[0].getName() : backupFolder;

                Object[] choices =
                {
                    I18n.t("ui.ok"),
                    I18n.t("ui.showFiles")
                };

                int chose = JOptionPane.showOptionDialog(this.ui,
                    unsaved.isEmpty()
                        ? I18n.f("ui.infoBackupCompleteSavedTo", shown)
                        : I18n.f("ui.warnBackupIncomplete", shown, String.join(", ", unsaved)),
                    I18n.t("ui.main.toolbar.backupData"),
                    JOptionPane.YES_NO_OPTION,
                    unsaved.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE,
                    null, choices, choices[0]);

                if (chose != 1) return;

                // Off the event thread: opening a file manager waits on the shell, which on a cold Explorer is seconds, and this dialog is dismissed by
                // then.  Its FOLDER, opened as a folder (OB-311): asking Explorer to select the file opened Documents wherever it could not take the path.
                final File made0 = made[0];

                new Thread(() -> Util.showInFileManager(made0, backupFolder)).start();
            });
        }).start();
    }
}
