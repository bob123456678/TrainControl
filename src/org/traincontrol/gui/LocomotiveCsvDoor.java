package org.traincontrol.gui;

import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.widgets.ChoiceTable;
import org.traincontrol.gui.widgets.DetailsArea;
import org.traincontrol.gui.widgets.UiStyle;
import org.traincontrol.marklin.MarklinLocomotiveCsv;
import org.traincontrol.marklin.MarklinLocomotiveCsv.Choice;
import org.traincontrol.marklin.MarklinLocomotiveCsv.Duplicate;
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
        Map<Integer, MarklinLocomotiveCsv.Why> skippedLines = csv.skipped(model);
        int skipped = skippedLines.size();

        if (added == 0 && existing.isEmpty())
        {
            List<String> lines = new ArrayList<>();

            for (Map.Entry<Integer, MarklinLocomotiveCsv.Why> e : skippedLines.entrySet())
            {
                lines.add(skippedLine(e.getKey(), e.getValue(), csv.badCells(e.getKey())));
            }

            String said = I18n.f("loc.ui.infoImportCsvNothing", skipped);

            JOptionPane.showMessageDialog(ui, lines.isEmpty() ? said : DetailsArea.message(said, lines));
            return;
        }

        // A route drives one of the locomotives to be merged: the same refusal the other edit doors give.
        if (RunGates.refuseWhileARouteDrivesAny(ui, ui, existing)) return;

        // The addresses are decided once, here; the question shows the plan and the import carries out exactly it.
        MarklinLocomotiveCsv.Plan plan = csv.plan(model);

        // A train with a speed keeps its address: the file would send its commands to another decoder.
        if (refuseWhileMoving(ui, csv, plan, model)) return;

        // A changed address is refused while an editor is open (D125)
        if (!plan.moves.isEmpty() && RunGates.refuseALinkOrSyncWhileEditorOpen(ui, ui)) return;

        List<MarklinLocomotiveCsv.Move> changing = plan.moves;
        List<MarklinLocomotiveCsv.Shared> shared = csv.sharedAddresses(model, plan);
        List<Duplicate> duplicates = csv.duplicates(model, plan);
        int sharing = 0;

        for (MarklinLocomotiveCsv.Shared group : shared) sharing += group.names.size();

        // Possible duplicates are counted in their table, not with the new ones
        String question = I18n.f("loc.ui.confirmImportCsv", added - duplicates.size(), existing.size())
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
        Duplicates table = duplicates.isEmpty() ? null : new Duplicates(duplicates);
        Object asked = table == null ? lines.isEmpty() ? question : DetailsArea.message(question, lines)
            : ChoiceTable.lines(DetailsArea.message(question, lines), (JComponent) Box.createVerticalStrut(12),
            SyncDifferencesWindow.headed(I18n.f("loc.ui.importCsvDuplicates", duplicates.size()),
            I18n.t("loc.ui.importCsvDuplicatesInfo")), (JComponent) Box.createVerticalStrut(6), table.table);
        if (JOptionPane.showOptionDialog(ui, asked, I18n.t("loc.ui.importCsvTitle"), JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE, null, options, options[0]) != JOptionPane.YES_OPTION)
        {
            return;
        }

        // The question was modal; a run or a route may have begun behind it.
        if (RunGates.refuseWhileAutonomyRunning(ui, source) || RunGates.refuseWhileARouteDrivesAny(ui, ui,
            csv.existingNames(model)) || refuseWhileMoving(ui, csv, plan, model)
            || !plan.moves.isEmpty() && RunGates.refuseALinkOrSyncWhileEditorOpen(ui, ui))
        {
            return;
        }

        MarklinLocomotiveCsv.Result result = csv.apply(model, plan, table == null ? new HashMap<>() : table.picks());

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
        if (!result.renamed.isEmpty()) said += "\n" + I18n.f("loc.ui.infoImportCsvRenamed", result.renamed.size());
        if (result.keptOld > 0) said += "\n" + I18n.f("loc.ui.infoImportCsvKeptOld", result.keptOld);
        List<String> lines = new ArrayList<>();

        for (MarklinLocomotiveCsv.Kept kept : result.kept) lines.add(keptLine(kept));

        for (Map.Entry<Integer, MarklinLocomotiveCsv.Why> e : result.skipped.entrySet())
        {
            lines.add(skippedLine(e.getKey(), e.getValue(), result.badCells.get(e.getKey())));
        }

        if (!result.skipped.isEmpty()) said += "\n\n" + I18n.f("loc.ui.infoImportCsvSkipped", result.skipped.size());

        JOptionPane.showMessageDialog(ui, lines.isEmpty() ? said : DetailsArea.message(said, lines));
    }

    /** A locomotive that keeps its address, by name and why. */
    private static String keptLine(MarklinLocomotiveCsv.Kept kept)
    {
        String why = kept.why == MarklinLocomotiveCsv.Keep.CONSIST ? "loc.ui.importCsvKeptConsist"
            : kept.why == MarklinLocomotiveCsv.Keep.STANDING ? "loc.ui.importCsvKeptStanding"
            : kept.why == MarklinLocomotiveCsv.Keep.HELD ? "loc.ui.importCsvKeptHeld" : "loc.ui.importCsvKeptMoving";

        return I18n.f("loc.ui.importCsvKeptLine", kept.name, I18n.t(why));
    }

    private static String skippedLine(int line, MarklinLocomotiveCsv.Why why, List<String[]> cells)
    {
        return I18n.f("loc.ui.importCsvSkippedLine", line) + ": " + reason(why, cells);
    }

    /** Why a line was skipped; an unreadable one names the cells that were wrong. */
    private static String reason(MarklinLocomotiveCsv.Why why, List<String[]> cells)
    {
        if (why == MarklinLocomotiveCsv.Why.UNREADABLE && cells != null && !cells.isEmpty())
        {
            List<String> named = new ArrayList<>();

            for (String[] cell : cells) named.add(I18n.f("loc.ui.importCsvBadCell", cell[0], cell[1]));

            return I18n.f("loc.ui.importCsvReasonCells", String.join(", ", named));
        }

        return I18n.t(reasonKey(why));
    }

    private static String reasonKey(MarklinLocomotiveCsv.Why why)
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

    /** What the address-change and rename doors refresh, once for the import; a standing train is never swept off. */
    private static void refresh(TrainControlUI ui, ViewListener model, MarklinLocomotiveCsv.Result result)
    {
        boolean changed = !result.readdressed.isEmpty() || !result.renamed.isEmpty();
        if (changed)
        {
            ui.clearCopyTarget();
            ui.repaintLoc(true, null);
            ui.repaintMappings(null, false);
            ui.refreshRouteList();
        }

        ui.getLocSelector().refreshLocSelectorList();

        if (changed && model.hasAutoLayout())
        {
            for (String name : result.renamed) model.getAutoLayout().sanitizeMultiUnits(model.getLocByName(name));
            model.getAutoLayout().refreshUI();
            ui.updateVisiblePoints();
            ui.repaintAutoLocList(false);
            ui.repaintTimetable();
        }
    }

    /** The possible duplicates' table (FR-249): one row per file row, each starting on Keep Both. */
    private static final class Duplicates
    {
        private final Map<Duplicate, Map<Choice, JRadioButton>> choices = new LinkedHashMap<>();
        /** "(can't apply)" on a Keep New another row's Keep New took. */
        private final Map<Duplicate, JLabel> taken = new HashMap<>();
        // A linked heading sets every row it can, as a row's own pick would
        final ChoiceTable table = new ChoiceTable(UiStyle.blackLabel(I18n.t("loc.ui.importCsvInFile")),
            UiStyle.blackLabel(I18n.t("sync.ui.thisComputer")),
            SyncDifferencesWindow.head(setAllTo("sync.ui.keepBoth", Choice.BOTH), "loc.ui.importCsvTipBoth"),
            SyncDifferencesWindow.head(setAllTo("loc.ui.importCsvKeepOld", Choice.OLD), "loc.ui.importCsvTipOld"),
            SyncDifferencesWindow.head(setAllTo("loc.ui.importCsvKeepNew", Choice.NEW), "loc.ui.importCsvTipNew"));

        Duplicates(List<Duplicate> rows)
        {
            for (Duplicate d : rows) addRow(d);
            refresh();
        }

        /** A row, named for the tests by the file's name: each side's Name and Address lines, then the three radios. */
        private void addRow(Duplicate d)
        {
            String name = d.row.name, old = d.here.get(0).getName(), kName = I18n.t("sync.ui.name");
            String why = d.whyNotNew == MarklinLocomotiveCsv.NotNew.SEVERAL ? I18n.f("loc.ui.importCsvWhySeveral",
                d.address) : d.whyNotNew == MarklinLocomotiveCsv.NotNew.LISTED ? I18n.f("loc.ui.importCsvWhyListed", old)
                : d.whyNotNew != null ? I18n.f("loc.ui.importCsvWhyRunning", old) : null;
            JLabel took = SyncDifferencesWindow.text(I18n.t("sync.ui.cantApply"), SyncDifferencesWindow.GRAYED);
            took.setName(name + ":taken");
            took.setVisible(false);
            this.taken.put(d, took);
            List<JComponent> here = new ArrayList<>();
            for (Locomotive l : d.here) here.add(SyncDifferencesWindow.line(this.table, kName, l.getName()
                + (l.hasLinkedLocomotives() ? " +" + l.getLinkedLocomotives().size() : ""), true, here.isEmpty() ? why
                : null, here.isEmpty() ? took : null));
            here.add(address(d));
            ButtonGroup group = new ButtonGroup();
            Map<Choice, JRadioButton> radios = new EnumMap<>(Choice.class);
            radio(d, radios, group, Choice.BOTH, I18n.f("sync.ui.keepBothSaid", old, name));
            radio(d, radios, group, Choice.OLD, I18n.f("loc.ui.importCsvKeepOldSaid", name));
            radio(d, radios, group, Choice.NEW, why != null ? why : I18n.f("loc.ui.importCsvKeepNewSaid", old, name))
                .setEnabled(why == null);
            radios.get(Choice.BOTH).setSelected(true);
            radios.values().forEach(b -> b.addItemListener(e -> refresh()));
            this.choices.put(d, radios);
            this.table.addRow(ChoiceTable.lines(SyncDifferencesWindow.line(this.table, kName, name, true, null, null),
                address(d)), ChoiceTable.lines(here.toArray(new JComponent[0])), radios.get(Choice.BOTH),
                radios.get(Choice.OLD), radios.get(Choice.NEW));
        }

        private JComponent address(Duplicate d)
        {
            return SyncDifferencesWindow.line(this.table, I18n.t("sync.ui.address"), d.address, false, null, null);
        }

        /** A row's radio, saying `said` on hover and to a screen reader. */
        private JRadioButton radio(Duplicate d, Map<Choice, JRadioButton> radios, ButtonGroup group, Choice c,
            String said)
        {
            JRadioButton b = this.table.choice(group);
            b.setName(d.row.name + ":" + c.name());
            b.setToolTipText(said);
            b.getAccessibleContext().setAccessibleName(said);
            radios.put(c, b);
            return b;
        }

        /** One Keep New per locomotive here: another row's is grayed, saying which row took it. */
        private void refresh()
        {
            Map<Locomotive, Duplicate> takers = new HashMap<>();
            this.choices.forEach((d, r) -> { if (r.get(Choice.NEW).isSelected()) takers.put(d.here.get(0), d); });
            this.choices.forEach((d, r) ->
            {
                Duplicate taker = takers.get(d.here.get(0));
                if (d.whyNotNew != null) return;
                boolean free = taker == null || taker == d;
                r.get(Choice.NEW).setEnabled(free);
                this.taken.get(d).setVisible(!free);
                this.taken.get(d).setToolTipText(free ? null : I18n.f("loc.ui.importCsvWhyTaken", taker.row.name,
                    d.here.get(0).getName()));
            });
        }

        private JButton setAllTo(String key, Choice to)
        {
            return ChoiceTable.link(I18n.t(key), () -> this.choices.values().forEach(r ->
            {
                if (r.get(to).isEnabled()) r.get(to).setSelected(true);
            }));
        }

        /** Each file row's answer. */
        Map<MarklinLocomotiveCsv.Row, Choice> picks()
        {
            Map<MarklinLocomotiveCsv.Row, Choice> out = new HashMap<>();
            this.choices.forEach((d, r) -> r.forEach((c, b) -> { if (b.isSelected()) out.put(d.row, c); }));
            return out;
        }
    }
}
