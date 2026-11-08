package org.traincontrol.gui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.traincontrol.util.I18n;

/**
 * File > Export Log (FR-051): saves the log as it shows, stamps included, to a text file.  A file that cannot be
 * written is said, and the chooser comes back for another place.  Event thread only.
 */
final class LogExportDoor
{
    private LogExportDoor()
    {
    }

    static void save(TrainControlUI ui)
    {
        String text = ui.logText().replace("\n", System.lineSeparator());

        JFileChooser fc = ui.getFileChooser(JFileChooser.SAVE_DIALOG, "txt");

        fc.setSelectedFile(new File("TrainControl-log-" + new SimpleDateFormat("yyyy-MM-dd-HHmm").format(new Date())
            + ".txt"));

        while (fc.showSaveDialog(ui) == JFileChooser.APPROVE_OPTION)
        {
            File chosen = fc.getSelectedFile();

            if (!chosen.getName().toLowerCase().endsWith(".txt")) chosen = new File(chosen.getPath() + ".txt");

            try
            {
                Files.write(chosen.toPath(), text.getBytes(StandardCharsets.UTF_8));

                if (chosen.getParent() != null) TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, chosen.getParent());

                ui.log(I18n.f("ui.infoLogSaved", chosen.getAbsolutePath()));

                return;
            }
            catch (IOException e)
            {
                ui.getModel().log(e);

                JOptionPane.showMessageDialog(ui, I18n.f("ui.errorLogNotSaved", chosen.getAbsolutePath(), e.getMessage()));
            }
        }
    }
}
