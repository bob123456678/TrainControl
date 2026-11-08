package org.traincontrol.gui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.traincontrol.util.I18n;

/**
 * File > Export Log (FR-051): saves the log as it shows, stamps included, to a text file.  Event thread only.
 */
final class LogExportDoor
{
    private LogExportDoor()
    {
    }

    static void save(TrainControlUI ui)
    {
        String text = ui.logText().replace("\n", System.lineSeparator());

        String name = "TrainControl-log-" + new SimpleDateFormat("yyyy-MM-dd-HHmm").format(new Date()) + ".txt";

        File saved = SaveToFileDoor.save(ui, text.getBytes(StandardCharsets.UTF_8), "txt", name,
            (file, e) -> I18n.f("ui.errorLogNotSaved", file.getAbsolutePath(), SaveToFileDoor.reason(file, e)));

        if (saved != null) ui.log(I18n.f("ui.infoLogSaved", saved.getAbsolutePath()));
    }
}
