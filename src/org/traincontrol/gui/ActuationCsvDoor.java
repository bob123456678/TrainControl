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
 * The keyboard tab's Export Counts button (FR-178): saves every accessory's actuation count as a CSV file.  A file that
 * cannot be written is said, and the chooser comes back for another place.  Event thread only.
 */
final class ActuationCsvDoor
{
    private ActuationCsvDoor()
    {
    }

    static void save(TrainControlUI ui)
    {
        String csv = ui.getModel().exportAccessoryActuationsToCSV();

        JFileChooser fc = ui.getFileChooser(JFileChooser.SAVE_DIALOG, "csv");

        fc.setSelectedFile(new File("TC_actuations_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv"));

        while (fc.showSaveDialog(ui) == JFileChooser.APPROVE_OPTION)
        {
            File chosen = fc.getSelectedFile();

            if (!chosen.getName().toLowerCase().endsWith(".csv")) chosen = new File(chosen.getPath() + ".csv");

            try
            {
                Files.write(chosen.toPath(), csv.getBytes(StandardCharsets.UTF_8));

                TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, chosen.getParent());

                return;
            }
            catch (IOException e)
            {
                ui.getModel().log(e);

                JOptionPane.showMessageDialog(ui, I18n.t("error.writingToFile"));
            }
        }
    }
}
