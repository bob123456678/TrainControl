package org.traincontrol.gui.widgets;

import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import javax.swing.JOptionPane;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.TrainControlUI.UpdateChoice;
import static org.traincontrol.marklin.MarklinControlStation.RAW_VERSION;
import org.traincontrol.util.I18n;
import org.traincontrol.util.SelfUpdate;
import org.traincontrol.util.Util;

/**
 * Help > Download Update (FR-125): the question, the download with its progress on the menu item, and for Download &
 * Update the check, the data backup and the exit the updater takes over from.
 *
 * What the window owns stays there: the hand-over (`TrainControlUI.updateHandOver`), the refusal it asks
 * (`whyThisUpdateMustWait`) and the exit itself (`closeNowForTheUpdate`).
 */
public final class UpdateFlow
{
    private final TrainControlUI ui;

    public UpdateFlow(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /**
     * `TrainControlUI.askAboutTheUpdate`'s body.
     */
    public static UpdateChoice askAboutTheUpdate(Component parent, String latest, String running, boolean canInstall)
    {
        final String download = I18n.t("ui.update.download");
        final String install = I18n.t("ui.update.downloadAndUpdate");

        final Object[] choices = canInstall
            ? new Object[] {download, install, I18n.t("ui.cancel")}
            : new Object[] {download, I18n.t("ui.cancel")};

        // Download, never Download & Update: the default is what a reflex Enter does
        final Object initial = download;

        final java.util.function.BiFunction<Object[], Object, Integer> hook = TrainControlUI.updateQuestionForTests;

        final int chose = hook != null ? hook.apply(choices.clone(), initial) : JOptionPane.showOptionDialog(parent,
            I18n.f("ui.update.available", latest, running)
                + (canInstall ? "\n\n" + I18n.t("ui.update.explainInstall") : ""),
            I18n.t("ui.main.downloadUpdate"),
            JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, initial);

        if (chose == 0) return UpdateChoice.DOWNLOAD;

        if (canInstall && chose == 1) return UpdateChoice.DOWNLOAD_AND_UPDATE;

        return UpdateChoice.CANCEL;
    }

    /**
     * Help > Download Update: asks what to do with the new version, then does it (FR-125).
     *
     * Download & Update is offered only when TrainControl runs from a jar it can replace - the jar it was loaded from,
     * in a folder that can be written.  Run from an IDE or from a folder of classes there is no jar, and the question
     * offers Download alone.
     */
    public void offerTheUpdate()
    {
        final File jar = SelfUpdate.runningJar(TrainControlUI.class);

        final UpdateChoice chose = askAboutTheUpdate(this.ui, TrainControlUI.LATEST_VERSION, RAW_VERSION, jar != null);

        if (chose == UpdateChoice.DOWNLOAD) downloadTheUpdate(jar, false);
        else if (chose == UpdateChoice.DOWNLOAD_AND_UPDATE) downloadTheUpdate(jar, true);
    }

    /**
     * Downloads the new version with its progress on the menu item; then Download says where it is, and Download & Update installs it
     * (FR-125).  Download is what this item always did, saved beside the running jar now where there is one, and in the working
     * directory where there is not.  One Download & Update at a time: a second, while one is downloading, backing up or closing, is
     * refused rather than stacked; whatever ends it short of the exit ends it here, and once it reaches `closeForTheUpdate`, that ends it.
     *
     * @param jar the running jar, or null
     * @param install whether to update as well
     */
    private void downloadTheUpdate(final File jar, final boolean install)
    {
        if (install)
        {
            if (!this.ui.updateHandOver.begin())
            {
                JOptionPane.showMessageDialog(this.ui, I18n.t("ui.update.alreadyUnderWay"));

                return;
            }

            final String wait = this.ui.whyThisUpdateMustWait();

            if (wait != null)
            {
                this.ui.updateHandOver.end();

                JOptionPane.showMessageDialog(this.ui, wait);

                return;
            }
        }

        // Without this, a second click would skip the in-progress download and report the partial file as complete
        this.ui.downloadUpdateMenuItem().setEnabled(false);
        this.ui.getModel().logf("ui.infoDownloadingUpdate");

        new Thread(() ->
        {
            // Whether the update reached closeForTheUpdate, which ends it itself
            boolean handedOver = false;

            try
            {
                final File f = SelfUpdate.downloadTarget(jar, TrainControlUI.LATEST_VERSION);

                try
                {
                    // A file of that name that is not the whole release, an earlier download cut short, is fetched
                    // again rather than installed.  Download alone keeps whatever is there, as it always has.
                    if (install && f.exists()
                        && SelfUpdate.whyNotAWholeJar(f, TrainControlUI.LATEST_DOWNLOAD_SIZE, TrainControlUI.LATEST_DOWNLOAD_DIGEST) != null)
                    {
                        Files.delete(f.toPath());
                    }

                    if (!f.exists())
                    {
                        Util.downloadFile(TrainControlUI.LATEST_DOWNLOAD_URL, f, percent ->
                        {
                            javax.swing.SwingUtilities.invokeLater(() ->
                            {
                                this.ui.downloadUpdateMenuItem().setText(percent < 0
                                    ? I18n.t("ui.infoDownloadingUpdate")
                                    : I18n.f("ui.infoDownloadingUpdatePercent", percent)
                                );
                            });
                        });
                    }
                }
                catch (Exception e)
                {
                    javax.swing.SwingUtilities.invokeLater(() ->
                    {
                        JOptionPane.showMessageDialog(
                            this.ui,
                            I18n.t("ui.errorDownloadingUpdateFile")
                        );
                    });

                    this.ui.getModel().logf("ui.errorDownloadingUpdateFile");

                    if (this.ui.getModel().isDebug())
                    {
                        this.ui.getModel().log(e);
                    }

                    return;
                }

                if (install)
                {
                    handedOver = installTheDownload(jar, f);

                    return;
                }

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    JOptionPane.showMessageDialog(
                        this.ui,
                        I18n.f(
                            "ui.infoDownloadedUpdateReplaceExistingJar",
                            f.getAbsolutePath()
                        )
                    );

                    this.ui.showFileExplorer(new File(f.getAbsoluteFile().toString()).getParentFile());
                });
            }
            // ANYTHING ELSE, an Error included, is said rather than left to end this thread in silence (FR-125): a
            // download that never reports looks like one still going, and an update that never reports, like one that
            // never started.
            catch (RuntimeException | Error e)
            {
                final String why = String.valueOf(e);

                this.ui.getModel().logf("ui.update.errorFailed", why);

                javax.swing.SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this.ui, I18n.f("ui.update.errorFailed", why)));
            }
            finally
            {
                if (install && !handedOver) this.ui.updateHandOver.end();

                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    this.ui.downloadUpdateMenuItem().setText(I18n.t("ui.main.downloadUpdate"));
                    this.ui.downloadUpdateMenuItem().setEnabled(true);
                });
            }
        }).start();
    }

    /**
     * Download & Update, once the file is here (FR-125): checks it is a whole jar, takes the data backup, then closes
     * TrainControl for the updater.  On the download's worker thread, until the exit, which is the event thread's.
     *
     * Each failure stops here, says why in words of its own, and leaves everything as it was: the old jar is not
     * touched until TrainControl has closed.
     *
     * @param jar the running jar
     * @param download the downloaded file, beside it
     * @return true when the exit was asked for, which then ends the update itself
     */
    private boolean installTheDownload(final File jar, final File download)
    {
        final String broken = SelfUpdate.whyNotAWholeJar(download, TrainControlUI.LATEST_DOWNLOAD_SIZE, TrainControlUI.LATEST_DOWNLOAD_DIGEST);

        if (broken != null)
        {
            this.ui.getModel().logf("ui.update.errorNotWhole", broken);

            javax.swing.SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this.ui, I18n.f("ui.update.errorNotWhole", broken)));

            return false;
        }

        // What it is doing now: the backup takes a while, and the item would otherwise still read "Downloading 100%"
        javax.swing.SwingUtilities.invokeLater(() ->
            this.ui.downloadUpdateMenuItem().setText(I18n.t("ui.update.backingUp")));

        // The application's own data backup, and the update goes no further without it (Adam).  Incomplete counts as failed: the backup is
        // what makes an update safe to try, and a part of one is a part of that.  An Error out of it is a failed backup too, and said as one.
        BackupAction.BackupOutcome backup;

        String why;

        try
        {
            backup = this.ui.backupData();

            why = backup.isComplete() ? null
                : backup.unsaved.isEmpty() ? I18n.t("ui.main.toolbar.backupData") : String.join(", ", backup.unsaved);
        }
        catch (RuntimeException | Error e)
        {
            backup = null;

            why = String.valueOf(e);
        }

        if (why != null)
        {
            final String said = why;

            this.ui.getModel().logf("ui.update.errorBackup", said);

            javax.swing.SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this.ui, I18n.f("ui.update.errorBackup", said)));

            return false;
        }

        this.ui.getModel().logf("ui.infoBackupCompleteSavedTo", backup.archive.getAbsolutePath());

        javax.swing.SwingUtilities.invokeLater(() -> closeForTheUpdate(jar, download));

        return true;
    }

    /**
     * `TrainControlUI.whenNoModalDialog`'s body.
     */
    public static boolean whenNoModalDialog(final Runnable then, final java.util.function.BooleanSupplier dialogShowing,
        int pollMs)
    {
        if (!dialogShowing.getAsBoolean())
        {
            then.run();

            return true;
        }

        final javax.swing.Timer later = new javax.swing.Timer(pollMs, null);

        later.addActionListener(e ->
        {
            if (dialogShowing.getAsBoolean()) return;

            later.stop();

            then.run();
        });

        later.start();

        return false;
    }

    /**
     * The last step of Download & Update (FR-125): once no modal dialog is open, leaves through the application's own
     * exit.  The update stays under way while it waits, so a second is still refused.
     *
     * @param jar the running jar
     * @param download the checked download
     */
    private void closeForTheUpdate(final File jar, final File download)
    {
        whenNoModalDialog(() -> this.ui.closeNowForTheUpdate(jar, download), TrainControlUI::aModalDialogIsShowing, 500);
    }
}
