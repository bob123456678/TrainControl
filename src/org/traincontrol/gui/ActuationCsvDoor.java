package org.traincontrol.gui;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.traincontrol.util.I18n;

/**
 * The keyboard tab's Export Counts button (FR-178): saves every accessory's actuation count as a CSV file.  Event
 * thread only.
 */
final class ActuationCsvDoor
{
    private ActuationCsvDoor()
    {
    }

    static void save(TrainControlUI ui)
    {
        String csv = ui.getModel().exportAccessoryActuationsToCSV();

        SaveToFileDoor.save(ui, csv.getBytes(StandardCharsets.UTF_8), "csv",
            "TC_actuations_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv",
            (file, e) -> I18n.t("error.writingToFile"));
    }
}
