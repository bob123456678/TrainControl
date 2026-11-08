package org.traincontrol.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

            if (!chosen.getName().toLowerCase().endsWith("." + extension)) chosen = new File(chosen.getPath() + "." + extension);

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
