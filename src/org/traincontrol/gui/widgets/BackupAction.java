package org.traincontrol.gui.widgets;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.swing.JOptionPane;
import org.traincontrol.gui.TrainControlUI;
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
public final class BackupAction
{
    private final TrainControlUI ui;

    public BackupAction(TrainControlUI ui)
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
     * The archive's file, `-2`, `-3` added while a backup made in the same second has the name.  Callers hold
     * `oneBackupAtATime`, so no other backup takes the name between the check and the write.
     */
    private static File freeArchiveName(String prefix)
    {
        File archive = new File(Util.getBackupPath(prefix + "-TrainControl.zip"));

        for (int n = 2; archive.exists(); n++) archive = new File(Util.getBackupPath(prefix + "-TrainControl-" + n + ".zip"));

        return archive;
    }

    /**
     * `backupData`'s body, which only `backupData` calls.  It takes no lock of its own: it is called only through
     * `TrainControlUI.backupData`, which holds `oneBackupAtATime` around it.
     *
     * @return the archive, or no archive, and what could not be saved
     */
    public BackupOutcome writeTheBackup()
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

        // Given back whatever happens.  It is switched off so a second backup cannot start while one is running, and
        // everything between here and the end of the try can throw; left off, the only way to back up again is to
        // restart the application, and the user is not told why.
        try
        {
            // Written LIVE first, so the archive holds the state as it is now.  WITHOUT the session capture: that would
            // commit whatever was open in the autonomy editor from a thread that is not the event thread, and the
            // editor's Cancel could not undo it (discardEdits re-reads the file just written).  Callers fold the run
            // first.
            this.ui.saveState(false, false);
            this.ui.getModel().saveState(false);

            // One archive, holding all of it (FR-015): the autonomy setup is keyed by PAGE ID and those ids are defined
            // by config/gleisbild.cs2, so setup.json alone means nothing and a gleisbild.cs2 from a different day
            // silently reattaches every station to the wrong page.  These files are only a backup together.
            java.util.Map<String, File> state = new java.util.LinkedHashMap<>();

            state.put(TrainControlUI.DATA_FILE_NAME, new File(Util.dataPath(TrainControlUI.DATA_FILE_NAME)));
            state.put(org.traincontrol.marklin.MarklinControlStation.DATA_FILE_NAME,
                new File(Util.dataPath(org.traincontrol.marklin.MarklinControlStation.DATA_FILE_NAME)));

            // TrainControl's OWN routes, exported as JSON (FR-021): the `fahrstrassen.cs2` in the config folder is the
            // CENTRAL STATION's copy, and this application's route database is what runs.  Written through the door the
            // Export Routes menu item uses, so Import Routes reads it back.  A failure here is named and the rest of
            // the archive still written.
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

            // The LEGACY autonomy file, which an operator coming from the hand-authored graph imports their setup from
            // (OB-254): in neither half of the backup otherwise.  Skipped silently when it is not there, like every
            // other source.
            state.put(TrainControlUI.AUTONOMY_FILE_NAME,
                new File(TrainControlUI.AUTONOMY_FILE_NAME));

            String localLayout = this.ui.localLayoutPath();

            // The layout lives on the CENTRAL STATION, so there is nothing local to archive (FR-020): asked rather than
            // assumed, because it is a network fetch of every page.  Declining leaves the archive as it was, and the
            // dialog at the end says what is not in it either way.  No autonomy setup pairs with these
            // (`AutonomyCompanionStore` refuses a layout that is not local).  Not offered without a station answering
            // (OB-098): asking is a promise to fetch.  Through the one predicate, like the other two offers:
            // `getNetworkCommState` alone reports whether the last SYNC succeeded, and a sync reads a local layout
            // folder happily, so a simulated session calls itself connected.
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
                // The whole folder, under the LAYOUT's own name (MT-159): a bare `config` leaves two backups of two
                // layouts indistinguishable, and restoring needs these files beside a `gleisbild.cs2` of the same
                // vintage.
                File layoutFolder = new File(localLayout).getAbsoluteFile();

                String named = layoutFolder.getName();

                state.put((named == null || named.isEmpty() ? "layout" : named) + "/config",
                    new File(layoutFolder, "config"));
            }

            // The cropped locomotive icons: `LocDB.data` records each locomotive's `localImageURL`, which for a crop
            // names a file in `tc_loc_icons`, so a restore without the folder pointed at files it lacked.  Only if it
            // exists.
            File icons = new File(Util.LOC_ICON_FOLDER).getAbsoluteFile();

            if (icons.isDirectory()) state.put(Util.LOC_ICON_FOLDER, icons);

            backupArchive = freeArchiveName(prefixForBackup());

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
            // The scratch download goes, whether or not the archive was written: a stale copy of the diagram in a
            // temporary folder is a second copy.
            if (centralStationCopy != null) Util.deleteTree(centralStationCopy, 0);

            if (routesExport != null) routesExport.delete();

            this.ui.backupDataMenuItem().setEnabled(true);
        }

        return new BackupOutcome(made[0], unsaved);
    }

    /**
     * What `backupData` wrote, and what it could not (FR-125).
     */
    public static final class BackupOutcome
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

        /** Adds, first, what could not be saved before the backup began; null adds nothing. */
        BackupOutcome after(String failed)
        {
            if (failed != null) this.unsaved.add(0, failed);

            return this;
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
     * Folds where a stopped run left the trains into the setup, so a backup holds it (`TrainControlUI.foldForABackup`).
     * On the event thread.
     *
     * @return why the fold could not be written, for the backup to name as a part it could not save; null otherwise
     */
    public static String foldTheStoppedRun(TrainControlUI ui)
    {
        Exception failed;

        try
        {
            failed = ui.foldForABackup();
        }
        catch (RuntimeException e)
        {
            failed = e;
        }

        if (failed == null) return null;

        if (ui.getModel() != null) ui.getModel().log(failed);

        return String.valueOf(failed);
    }

    /**
     * File > Backup TrainControl Data: writes the backup off the event thread and says what it holds.
     */
    public void backupDataMenuItemActionPerformed()
    {
        final String folded = foldTheStoppedRun(this.ui);

        new Thread(() ->
        {
            // The writing is backupData's, which an update takes too (FR-125); the reporting is the menu's alone.
            final BackupOutcome done = this.ui.backupData().after(folded);

            final List<String> unsaved = done.unsaved;

            // Read from the reporting lambda below, which needs it effectively final
            final File[] made = {done.archive};

            // Derived from an actual backup path rather than BACKUP_FOLDER: getBackupPath falls back to the working
            // directory when the folder cannot be created.
            String backupFolder = new File(Util.getBackupPath("x")).getAbsoluteFile().getParent();

            // The full path goes to the LOG, not into the dialog (FR-019): a backup path is long enough to stretch the
            // dialog across the screen.
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
