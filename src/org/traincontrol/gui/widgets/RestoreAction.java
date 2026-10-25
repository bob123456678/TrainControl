package org.traincontrol.gui.widgets;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * File &gt; Restore from Backup (FR-124): puts back what `BackupAction` wrote.  The archive is read whole and checked
 * before anything is written, the user is shown what each part replaces, and a full backup of the data as it is now is
 * taken first, named as taken before a restore, so the restore can be undone by restoring that file.
 *
 * Nothing is reloaded in place: the locomotive database is read only by the model's constructor and the window state
 * only as the window starts.  So TrainControl closes without the exit's saves once anything is written, and nothing it
 * holds in memory can be saved over the restored files.
 */
public final class RestoreAction
{
    /** Ends the name of the backup taken before a restore. */
    public static final String BEFORE_RESTORE_SUFFIX = "-before-restore.zip";

    private final TrainControlUI ui;

    public RestoreAction(TrainControlUI ui)
    {
        this.ui = ui;
    }

    /** Asks for an archive, shows what it holds, and restores it on Restore.  The event thread; run gates asked first. */
    public void restoreFromBackup()
    {
        final String title = I18n.t("ui.main.toolbar.restoreData");

        JFileChooser fc = new QuickFileChooser(new File(Util.getBackupPath("x")).getAbsoluteFile().getParent());

        fc.setDialogTitle(title);
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new FileNameExtensionFilter(I18n.t("ui.zipFile"), "zip"));

        if (fc.showOpenDialog(this.ui) != JFileChooser.APPROVE_OPTION) return;

        final File chosen = fc.getSelectedFile();

        final Archive archive;

        try
        {
            archive = Archive.read(chosen, this.ui.getModel());
        }
        catch (Refused refused)
        {
            JOptionPane.showMessageDialog(this.ui, refused.getMessage(), title, JOptionPane.ERROR_MESSAGE);

            return;
        }

        final File layoutTo = archive.layoutName == null ? null
            : layoutDestination(archive.layoutName, this.ui.localLayoutPath());

        Object[] choices = {I18n.t("ui.restoreButton"), I18n.t("ui.cancel")};

        if (JOptionPane.showOptionDialog(this.ui, I18n.f("ui.confirmRestore", chosen.getName(), archive.parts(layoutTo)),
            title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[1]) != 0) return;

        // A route can start while the question is up; the exit's question about unsaved windows, as this ends in one
        if (this.ui.refuseWhileARunHoldsTheSetup(this.ui) || !this.ui.everyOpenWindowMaySettle()) return;

        final String[] said = new String[1];

        final boolean[] wrote = new boolean[1];

        BusyDialog.run(this.ui, I18n.t("ui.busyRestoring"), () ->
        {
            BackupAction.BackupOutcome shadow = this.ui.backupData();

            if (!shadow.isComplete())
            {
                said[0] = I18n.f("ui.errorRestoreNoShadowBackup", String.join(", ", shadow.unsaved));

                return;
            }

            File named = new File(shadow.archive.getPath().replaceFirst("\\.zip$", BEFORE_RESTORE_SUFFIX));

            File kept = shadow.archive.renameTo(named) ? named : shadow.archive;

            wrote[0] = true;

            try
            {
                archive.writeLayout(layoutTo);

                if (layoutTo != null) TrainControlUI.layoutFolderIs(layoutTo.getPath());

                archive.writeData();

                said[0] = I18n.f("ui.infoRestoreComplete", chosen.getName(), kept.getName());
            }
            catch (IOException | RuntimeException e)
            {
                said[0] = I18n.f("ui.errorRestoreStopped", String.valueOf(e), kept.getName());
            }

            if (this.ui.getModel() != null) this.ui.getModel().log(said[0]);
        },
        () ->
        {
            JOptionPane.showMessageDialog(this.ui, said[0], title,
                wrote[0] ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);

            if (wrote[0]) closeWithoutSaving();
        });
    }

    /** Ends the program without `WindowClosed`'s saves, which would write the state from before the restore. */
    private void closeWithoutSaving()
    {
        this.ui.dispose();

        System.exit(0);
    }

    /**
     * Where an archived layout goes: the open layout when it has the archived name, else a folder of that name beside
     * the data files, else, when that already holds a layout, a new one beside it.  Only the open layout is replaced,
     * and the backup taken first holds it.  The archive keeps the folder's name, not its place.
     *
     * @param name the layout's folder name in the archive
     * @param open the open layout's folder, or null
     * @return the folder whose `config` the archive's goes into
     */
    private static File layoutDestination(String name, String open)
    {
        if (open != null && !open.isEmpty())
        {
            File current = new File(open).getAbsoluteFile();

            if (current.equals(new File(current.getParentFile(), name))) return current;
        }

        File beside = new File(Util.dataPath(name)).getAbsoluteFile();

        for (int n = 2; new File(beside, "config").exists(); n++)
        {
            beside = new File(Util.dataPath(name + "-restored" + (n == 2 ? "" : "-" + n))).getAbsoluteFile();
        }

        return beside;
    }

    /** Why an archive will not be restored, in words for the user. */
    static final class Refused extends Exception
    {
        Refused(String message)
        {
            super(message);
        }
    }

    /** An archive's parts, read and checked; mirrors what `BackupAction.writeTheBackup` puts in. */
    static final class Archive
    {
        private static final String CONFIG = "config";

        byte[] locomotives;
        byte[] windowState;
        byte[] autonomyFile;
        String layoutName;

        /** Under the layout's `config`, and under the icon folder, by their path there. */
        final Map<String, byte[]> layout = new TreeMap<>();
        final Map<String, byte[]> icons = new TreeMap<>();

        /** What is in the archive and not put back: the Central Station's copy, and anything this version does not know. */
        final Set<String> left = new TreeSet<>();

        static Archive read(File zip, ViewListener model) throws Refused
        {
            Archive read = new Archive();

            String notABackup = I18n.f("ui.errorRestoreNotABackup", zip.getName());

            try (ZipFile file = new ZipFile(zip))
            {
                for (ZipEntry entry : Collections.list(file.entries()))
                {
                    if (entry.isDirectory()) continue;

                    try (InputStream in = file.getInputStream(entry))
                    {
                        if (!read.take(entry.getName(), bytesOf(in))) throw new Refused(notABackup);
                    }
                }
            }
            catch (IOException | RuntimeException unreadable)
            {
                throw new Refused(notABackup);
            }

            if (read.locomotives == null && read.windowState == null) throw new Refused(notABackup);

            if (read.locomotives != null && !readsAsALocomotiveDatabase(read.locomotives, model))
            {
                throw unreadable(zip, MarklinControlStation.DATA_FILE_NAME);
            }

            if (read.windowState != null && !readsAsAList(read.windowState))
            {
                throw unreadable(zip, TrainControlUI.DATA_FILE_NAME);
            }

            if (read.layoutName != null && !read.layout.containsKey("gleisbild.cs2"))
            {
                throw unreadable(zip, read.layoutName + "/" + CONFIG + "/gleisbild.cs2");
            }

            return read;
        }

        private static byte[] bytesOf(InputStream in) throws IOException
        {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

            byte[] buffer = new byte[8192];

            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);

            return out.toByteArray();
        }

        private static Refused unreadable(File zip, String part)
        {
            return new Refused(I18n.f("ui.errorRestoreUnreadable", zip.getName(), part));
        }

        /** Files one entry; false for a name that is no backup's (one leaving its folder, or a second layout). */
        private boolean take(String name, byte[] content)
        {
            String[] at = name.split("/", -1);

            for (String step : at)
            {
                if (step.isEmpty() || ".".equals(step) || "..".equals(step) || step.contains("\\") || step.contains(":"))
                {
                    return false;
                }
            }

            String rest = at.length > 1 ? name.substring(at[0].length() + 1) : "";

            if (at.length == 1 && MarklinControlStation.DATA_FILE_NAME.equals(name)) locomotives = content;
            else if (at.length == 1 && TrainControlUI.DATA_FILE_NAME.equals(name)) windowState = content;
            else if (at.length == 1 && TrainControlUI.AUTONOMY_FILE_NAME.equals(name)) autonomyFile = content;
            else if (at.length == 1 && "routes.json".equals(name))
            {
                // The routes are in the locomotive database too; this export is for Import Routes
            }
            else if (at.length > 1 && Util.LOC_ICON_FOLDER.equals(at[0])) icons.put(rest, content);
            else if ("central-station-layout".equals(at[0])) left.add(at[0]);
            else if (at.length > 2 && CONFIG.equals(at[1]))
            {
                if (layoutName != null && !layoutName.equals(at[0])) return false;

                layoutName = at[0];

                layout.put(rest.substring(CONFIG.length() + 1), content);
            }
            else left.add(at[0]);

            return true;
        }

        /** Through the station's own reader, which knows the older formats, then its mark put back for the live file. */
        private static boolean readsAsALocomotiveDatabase(byte[] content, ViewListener model)
        {
            if (!(model instanceof MarklinControlStation)) return false;

            MarklinControlStation station = (MarklinControlStation) model;

            boolean liveWasUnreadable = station.isDatabaseLoadFailed();

            File staged = null;

            try
            {
                staged = File.createTempFile("tc-restore", ".data");

                Files.write(staged.toPath(), content);

                station.restoreState(staged.getPath());

                boolean reads = !station.isDatabaseLoadFailed();

                // The mark describes the live file and decides whether the exit keeps a copy of it before saving
                if (station.isDatabaseLoadFailed() != liveWasUnreadable)
                {
                    station.restoreState(Util.dataPath(MarklinControlStation.DATA_FILE_NAME));
                }

                return reads;
            }
            catch (IOException e)
            {
                return false;
            }
            finally
            {
                if (staged != null) staged.delete();
            }
        }

        private static boolean readsAsAList(byte[] content)
        {
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(content)))
            {
                return in.readObject() instanceof List;
            }
            catch (IOException | ClassNotFoundException | RuntimeException e)
            {
                return false;
            }
        }

        /** The confirmation's list: each part and what it replaces. */
        String parts(File layoutTo)
        {
            List<String> lines = new ArrayList<>();

            if (locomotives != null) lines.add(I18n.t("ui.restorePartLocomotives"));
            if (windowState != null) lines.add(I18n.t("ui.restorePartWindowState"));
            if (!icons.isEmpty()) lines.add(I18n.t("ui.restorePartIcons"));
            if (autonomyFile != null) lines.add(I18n.t("ui.restorePartAutonomyFile"));
            if (layoutName != null) lines.add(I18n.f("ui.restorePartLayout", layoutName, layoutTo.getPath()));

            StringBuilder said = new StringBuilder();

            for (String line : lines) said.append("• ").append(line).append('\n');

            if (!left.isEmpty()) said.append('\n').append(I18n.f("ui.restoreNotRestored", String.join(", ", left)));

            return said.toString().trim();
        }

        /** The layout's `config` emptied and written again, so a file added since the backup does not stay. */
        void writeLayout(File to) throws IOException
        {
            if (layoutName == null) return;

            File config = new File(to, CONFIG);

            if (config.exists() && !Util.deleteTree(config, 0)) throw new IOException(config.getPath());

            for (Map.Entry<String, byte[]> file : layout.entrySet()) write(new File(config, file.getKey()), file.getValue());
        }

        /** Where `BackupAction` took each from; icons are added to the folder, as other locomotives may use the rest. */
        void writeData() throws IOException
        {
            if (locomotives != null) atomically(Util.dataPath(MarklinControlStation.DATA_FILE_NAME), locomotives);
            if (windowState != null) atomically(Util.dataPath(TrainControlUI.DATA_FILE_NAME), windowState);
            if (autonomyFile != null) atomically(TrainControlUI.AUTONOMY_FILE_NAME, autonomyFile);

            File iconFolder = new File(Util.LOC_ICON_FOLDER).getAbsoluteFile();

            for (Map.Entry<String, byte[]> icon : icons.entrySet()) write(new File(iconFolder, icon.getKey()), icon.getValue());
        }

        private static void atomically(String path, byte[] content) throws IOException
        {
            Util.writeAtomically(new File(path), out -> out.write(content));
        }

        private static void write(File file, byte[] content) throws IOException
        {
            File parent = file.getAbsoluteFile().getParentFile();

            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException(parent.getPath());

            Files.write(file.toPath(), content);
        }
    }
}
