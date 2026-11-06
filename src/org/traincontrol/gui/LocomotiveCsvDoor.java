package org.traincontrol.gui;

import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            text = MarklinLocomotiveCsv.decode(Files.readAllBytes(chosen.toPath()));
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
        int skipped = csv.skipped(model).size();

        if (added == 0 && existing.isEmpty())
        {
            JOptionPane.showMessageDialog(ui, I18n.f("loc.ui.infoImportCsvNothing", skipped));
            return;
        }

        // A route drives one of the locomotives to be merged: the same refusal the other edit doors give.
        if (RunGates.refuseWhileARouteDrivesAny(ui, ui, existing)) return;

        // The addresses are decided once, here; the question shows the plan and the import carries out exactly it.
        MarklinLocomotiveCsv.Plan plan = csv.plan(model);

        // A train with a speed keeps its address: the file would send its commands to another decoder.
        if (refuseWhileMoving(ui, csv, plan, model)) return;

        List<MarklinLocomotiveCsv.Move> changing = plan.moves;
        List<MarklinLocomotiveCsv.Shared> shared = csv.sharedAddresses(model, plan);
        int sharing = 0;

        for (MarklinLocomotiveCsv.Shared group : shared) sharing += group.names.size();

        String question = I18n.f("loc.ui.confirmImportCsv", added, existing.size())
            + (changing.isEmpty() ? "" : "\n\n" + I18n.f("loc.ui.confirmImportCsvReaddress", changing.size()))
            + (plan.kept.isEmpty() ? "" : "\n\n" + I18n.f("loc.ui.confirmImportCsvKept", plan.kept.size()))
            + (sharing == 0 ? "" : "\n\n" + I18n.f("loc.ui.confirmImportCsvShared", sharing))
            + (skipped > 0 ? "\n\n" + I18n.f("loc.ui.confirmImportCsvUnreadable", skipped) : "");

        // Every line, in order: the area scrolls
        List<String> lines = new ArrayList<>();

        for (MarklinLocomotiveCsv.Move move : changing) lines.add(I18n.f("loc.ui.importCsvChangeLine", move.name, move.from, move.to));

        for (MarklinLocomotiveCsv.Kept kept : plan.kept) lines.add(keptLine(kept));

        for (MarklinLocomotiveCsv.Shared group : shared) lines.add(I18n.f("loc.ui.importCsvSharedLine", group.address, String.join(", ", group.names)));

        Object[] options = {I18n.t("loc.ui.importCsvButton"), I18n.t("ui.cancel")};

        if (JOptionPane.showOptionDialog(ui, lines.isEmpty() ? question : DetailsArea.message(question, lines),
            I18n.t("loc.ui.importCsvTitle"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options,
            options[0]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        // The question was modal; a run or a route may have begun behind it.
        if (RunGates.refuseWhileAutonomyRunning(ui, source) || RunGates.refuseWhileARouteDrivesAny(ui, ui,
            csv.existingNames(model)) || refuseWhileMoving(ui, csv, plan, model))
        {
            return;
        }

        MarklinLocomotiveCsv.Result result = csv.apply(model, plan);

        TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, chosen.getParent());

        refresh(ui, model, result);

        tell(ui, result);
    }

    /** What the import did, with the lines it skipped and why in a list under it. */
    private static void tell(TrainControlUI ui, MarklinLocomotiveCsv.Result result)
    {
        String said = I18n.f("loc.ui.infoImportCsvDone", result.added, result.merged);

        if (!result.readdressed.isEmpty()) said += "\n" + I18n.f("loc.ui.infoImportCsvReaddressed", result.readdressed.size());

        if (!result.kept.isEmpty()) said += "\n" + I18n.f("loc.ui.infoImportCsvReaddressRefused", result.kept.size());

        if (result.badRuntime > 0) said += "\n" + I18n.f("loc.ui.infoImportCsvBadRuntime", result.badRuntime);

        if (result.addressInvalid > 0) said += "\n" + I18n.f("loc.ui.infoImportCsvAddressInvalid", result.addressInvalid);

        if (result.addedWithoutRuntime > 0) said += "\n" + I18n.f("loc.ui.infoImportCsvNoRuntime", result.addedWithoutRuntime);

        if (result.badLastRun > 0) said += "\n" + I18n.f("loc.ui.infoImportCsvBadLastRun", result.badLastRun);

        if (result.leftOutMultiUnit > 0) said += "\n" + I18n.f("loc.ui.infoImportCsvLeftOutMultiUnit", result.leftOutMultiUnit);

        List<String> lines = new ArrayList<>();

        for (MarklinLocomotiveCsv.Kept kept : result.kept) lines.add(keptLine(kept));

        for (Map.Entry<Integer, MarklinLocomotiveCsv.Why> e : result.skipped.entrySet())
        {
            lines.add(I18n.f("loc.ui.importCsvSkippedLine", e.getKey()) + ": " + I18n.t(reason(e.getValue())));
        }

        if (!result.skipped.isEmpty()) said += "\n\n" + I18n.f("loc.ui.infoImportCsvSkipped", result.skipped.size());

        JOptionPane.showMessageDialog(ui, lines.isEmpty() ? said : DetailsArea.message(said, lines));
    }

    /** A locomotive that keeps its address, by name and why. */
    private static String keptLine(MarklinLocomotiveCsv.Kept kept)
    {
        String why = kept.why == MarklinLocomotiveCsv.Keep.CONSIST ? "loc.ui.importCsvKeptConsist"
            : kept.why == MarklinLocomotiveCsv.Keep.STANDING ? "loc.ui.importCsvKeptStanding" : "loc.ui.importCsvKeptMoving";

        return I18n.f("loc.ui.importCsvKeptLine", kept.name, I18n.t(why));
    }

    private static String reason(MarklinLocomotiveCsv.Why why)
    {
        switch (why)
        {
            case NAME: return "loc.ui.importCsvReasonName";
            case MULTI_UNIT: return "loc.ui.importCsvReasonMultiUnit";
            case ADDRESS: return "loc.ui.importCsvReasonAddress";
            default: return "loc.ui.importCsvReasonUnreadable";
        }
    }

    /** Says which train with a speed the file would re-address; true when the import must not go on. */
    private static boolean refuseWhileMoving(TrainControlUI ui, MarklinLocomotiveCsv csv, MarklinLocomotiveCsv.Plan plan,
        ViewListener model)
    {
        List<String> moving = csv.movingToReaddress(plan, model);

        if (moving.isEmpty()) return false;

        JOptionPane.showMessageDialog(ui, I18n.f("loc.ui.errorImportCsvMoving", moving.get(0)));

        return true;
    }

    /** What the address-change door refreshes, once for the import; a standing train is never swept off the layout. */
    private static void refresh(TrainControlUI ui, ViewListener model, MarklinLocomotiveCsv.Result result)
    {
        if (!result.readdressed.isEmpty())
        {
            ui.clearCopyTarget();
            ui.repaintLoc(true, null);
            ui.repaintMappings(null, false);
            ui.refreshRouteList();
        }

        ui.getLocSelector().refreshLocSelectorList();

        if (!result.readdressed.isEmpty() && model.hasAutoLayout())
        {
            model.getAutoLayout().refreshUI();
            ui.updateVisiblePoints();
            ui.repaintAutoLocList(false);
            ui.repaintTimetable();
        }
    }
}
