package org.traincontrol.gui;

import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.traincontrol.gui.widgets.DetailsArea;
import org.traincontrol.marklin.MarklinLocomotiveCsv;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;

/**
 * Locomotives > Import from CSV (FR-049): the door for a file `Export to CSV` wrote.  Reads the whole file, asks once
 * with the counts, and changes nothing until the answer is Import.  Event thread only.
 */
final class LocomotiveCsvDoor
{
    private LocomotiveCsvDoor()
    {
    }

    static void open(TrainControlUI ui, Component source)
    {
        if (RunGates.refuseWhileAutonomyRunning(ui, source)) return;

        JFileChooser fc = ui.getFileChooser(JFileChooser.OPEN_DIALOG, "csv");

        if (fc.showOpenDialog(ui) != JFileChooser.APPROVE_OPTION) return;

        File chosen = fc.getSelectedFile();
        String text;

        try
        {
            text = new String(Files.readAllBytes(chosen.toPath()), StandardCharsets.UTF_8);
        }
        catch (Exception unreadable)
        {
            JOptionPane.showMessageDialog(ui, I18n.f("loc.ui.errorImportCsvUnreadable", unreadable.getMessage()));
            return;
        }

        MarklinLocomotiveCsv csv = MarklinLocomotiveCsv.read(text);

        if (!csv.isLocomotiveFile())
        {
            JOptionPane.showMessageDialog(ui, I18n.t("loc.ui.errorImportCsvNotAnExport"));
            return;
        }

        ViewListener model = ui.getModel();

        List<String> existing = csv.existingNames(model);
        int added = csv.countNew(model);
        int unreadable = csv.skippedLines(model).size();

        if (added == 0 && existing.isEmpty())
        {
            JOptionPane.showMessageDialog(ui, I18n.f("loc.ui.infoImportCsvNothing", unreadable));
            return;
        }

        // A route drives one of the locomotives to be merged: the same refusal the other edit doors give.
        if (RunGates.refuseWhileARouteDrivesAny(ui, ui, existing)) return;

        String question = I18n.f("loc.ui.confirmImportCsv", added, existing.size())
            + (unreadable > 0 ? "\n\n" + I18n.f("loc.ui.confirmImportCsvUnreadable", unreadable) : "");

        Object[] options = {I18n.t("loc.ui.importCsvButton"), I18n.t("ui.cancel")};

        if (JOptionPane.showOptionDialog(ui, question, I18n.t("loc.ui.importCsvTitle"), JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        // The question was modal; a run or a route may have begun behind it.
        if (RunGates.refuseWhileAutonomyRunning(ui, source) || RunGates.refuseWhileARouteDrivesAny(ui, ui,
            csv.existingNames(model)))
        {
            return;
        }

        MarklinLocomotiveCsv.Result result = csv.apply(model);

        TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, chosen.getParent());

        ui.getLocSelector().refreshLocSelectorList();

        String said = I18n.f("loc.ui.infoImportCsvDone", result.added, result.merged);

        if (result.skippedLines.isEmpty())
        {
            JOptionPane.showMessageDialog(ui, said);
            return;
        }

        List<String> lines = new ArrayList<>();

        for (int line : result.skippedLines) lines.add(I18n.f("loc.ui.importCsvSkippedLine", line));

        JOptionPane.showMessageDialog(ui, DetailsArea.message(said + "\n\n"
            + I18n.f("loc.ui.infoImportCsvSkipped", result.skippedLines.size()), lines));
    }
}
