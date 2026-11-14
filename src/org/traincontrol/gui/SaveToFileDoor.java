package org.traincontrol.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import org.traincontrol.util.I18n;
import java.util.function.BiFunction;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

/**
 * The save chooser behind the exports that write one file (FR-178, FR-051).  A file that cannot be written is said, and
 * the chooser comes back for another place.  Event thread only.
 */
final class SaveToFileDoor
{
    private SaveToFileDoor()
    {
    }

    /**
     * A plain reason a write failed, never just the path (which is all the message of a missing folder or a refused
     * write holds).
     */
    static String reason(File file, IOException e)
    {
        if (e instanceof NoSuchFileException) return I18n.t("ui.reasonFolderMissing");

        if (e instanceof AccessDeniedException) return I18n.t("ui.reasonNoPermission");

        String said = e instanceof FileSystemException ? ((FileSystemException) e).getReason() : e.getMessage();

        if (said == null || said.trim().isEmpty() || said.contains(file.getName()) || said.contains(file.getPath()))
        {
            return I18n.t("ui.reasonNotWritten");
        }

        return said;
    }

    /**
     * @param extension without the dot; added when the user's name lacks it
     * @param whyNot the message for a write of this file that failed
     * @return the file written, or null when the user canceled
     */
    static File save(TrainControlUI ui, byte[] bytes, String extension, String suggestedName,
        BiFunction<File, IOException, String> whyNot)
    {
        JFileChooser fc = ui.getFileChooser(JFileChooser.SAVE_DIALOG, extension);

        fc.setSelectedFile(new File(suggestedName));

        while (fc.showSaveDialog(ui) == JFileChooser.APPROVE_OPTION)
        {
            File chosen = fc.getSelectedFile();

            if (!chosen.getName().toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) chosen = new File(chosen.getPath() + "." + extension);

            try
            {
                Files.write(chosen.toPath(), bytes);

                if (chosen.getParent() != null) TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, chosen.getParent());

                return chosen;
            }
            catch (IOException e)
            {
                ui.getModel().log(e);

                JOptionPane.showMessageDialog(ui, whyNot.apply(chosen, e));
            }
        }

        return null;
    }
}
