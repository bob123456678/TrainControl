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
final class LocomotiveCsvImport
{
    private LocomotiveCsvImport()
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

        // Possible duplicates are counted in their table, not with the new ones; a count of 0 says nothing
        int fresh = added - duplicates.size();
        List<String> asks = new ArrayList<>();
        asks.add((fresh == 0 ? "" : count(fresh, "loc.ui.confirmImportCsvOne", "loc.ui.confirmImportCsv")) + (fresh == 0
            || existing.isEmpty() ? "" : " ") + (existing.isEmpty() ? "" : count(existing.size(),
            "loc.ui.confirmImportCsvMergeOne", "loc.ui.confirmImportCsvMerge")));
        if (!changing.isEmpty()) asks.add(I18n.f("loc.ui.confirmImportCsvReaddress", changing.size()));
        if (!plan.kept.isEmpty()) asks.add(I18n.f("loc.ui.confirmImportCsvKept", plan.kept.size()));
        if (sharing > 0) asks.add(I18n.f("loc.ui.confirmImportCsvShared", sharing));
        if (skipped > 0) asks.add(I18n.f("loc.ui.confirmImportCsvUnreadable", skipped));
        asks.removeIf(String::isEmpty);
        String question = String.join("\n\n", asks);

        // Every line, in order: the area scrolls
        List<String> lines = new ArrayList<>();

        for (MarklinLocomotiveCsv.Move move : changing) lines.add(I18n.f("loc.ui.importCsvChangeLine", move.name, move.from, move.to));

        for (MarklinLocomotiveCsv.Kept kept : plan.kept) lines.add(keptLine(kept));

        for (MarklinLocomotiveCsv.Shared group : shared) lines.add(I18n.f("loc.ui.importCsvSharedLine", group.address, String.join(", ", group.names)));

        Object[] options = {I18n.t("loc.ui.importCsvButton"), I18n.t("ui.cancel")};
        Duplicates table = duplicates.isEmpty() ? null : new Duplicates(duplicates);
        // No wider than the main window less the popup's icon and margins (72 scaled) and frame (18); the rest scrolls
        if (table != null)
        {
            int room = ui.isShowing() ? ui.getWidth() : java.awt.Toolkit.getDefaultToolkit().getScreenSize().width;
            table.table.limitWidth(room - com.formdev.flatlaf.util.UIScale.scale(72) - 18);
        }
        // With nothing to say above the table, the heading starts the popup
        boolean bare = question.isEmpty() && lines.isEmpty();
        Object asked = table == null ? lines.isEmpty() ? question : DetailsArea.message(question, lines)
            : ChoiceTable.lines(bare ? (JComponent) Box.createVerticalStrut(0) : DetailsArea.message(question, lines),
            (JComponent) Box.createVerticalStrut(bare ? 0 : 6),
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

        ui.autonomyLifecycle.forgetUnrepaired();
        MarklinLocomotiveCsv.Result result = csv.apply(model, plan, table == null ? new HashMap<>() : table.picks());

        TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, chosen.getParent());

        // Once for the import; only the renamed have their multi-units checked, so a standing train is never swept off
        if (result.readdressed.isEmpty() && result.renamed.isEmpty()) ui.getLocSelector().refreshLocSelectorList();
        else ui.locomotiveActions.refreshAfterEdit(result.renamed.stream().map(model::getLocByName)
            .collect(java.util.stream.Collectors.toList()));
        ManualSync.imported(ui, result);

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
        int merged = result.renamed.size() - result.replaced;
        if (merged > 0) said += "\n" + count(merged, "loc.ui.infoImportCsvRenamedOne", "loc.ui.infoImportCsvRenamed");
        if (result.replaced > 0) said += "\n" + count(result.replaced, "loc.ui.infoImportCsvReplacedOne", "loc.ui.infoImportCsvReplaced");
        if (result.ignored > 0) said += "\n" + count(result.ignored, "loc.ui.infoImportCsvIgnoredOne", "loc.ui.infoImportCsvIgnored");
        if (!result.notRenamed.isEmpty()) said += "\n" + I18n.f("loc.ui.infoImportCsvNotRenamed", notRenamed(result));
        if (!result.notLeftOut.isEmpty()) said += "\n" + I18n.f("loc.ui.infoImportCsvNotLeftOut", String.join(", ", result.notLeftOut));
        List<String> lines = new ArrayList<>();

        for (MarklinLocomotiveCsv.Kept kept : result.kept) lines.add(keptLine(kept));

        for (Map.Entry<Integer, MarklinLocomotiveCsv.Why> e : result.skipped.entrySet())
        {
            lines.add(skippedLine(e.getKey(), e.getValue(), result.badCells.get(e.getKey())));
        }

        if (!result.skipped.isEmpty()) said += "\n\n" + I18n.f("loc.ui.infoImportCsvSkipped", result.skipped.size());

        ui.autonomyLifecycle.sayAfterRenames(ui, said, lines, null);
    }

    /** `n` of something, in the singular key when it is 1. */
    private static String count(int n, String one, String many)
    {
        return I18n.f(n == 1 ? one : many, n);
    }

    /** The file's names whose Merge or Replace was not carried out, each with why. */
    private static String notRenamed(MarklinLocomotiveCsv.Result result)
    {
        List<String> out = new ArrayList<>();
        result.notRenamed.forEach((name, why) -> out.add(name + " (" + I18n.t(why == MarklinLocomotiveCsv.NotNew.SEVERAL
            ? "loc.ui.importCsvNotSeveral" : why == MarklinLocomotiveCsv.NotNew.LISTED ? "loc.ui.importCsvNotListed"
            : why == MarklinLocomotiveCsv.NotNew.RUNNING ? "loc.ui.importCsvNotRunning"
            : why == MarklinLocomotiveCsv.NotNew.TAKEN ? "loc.ui.importCsvNotTaken"
            : why == MarklinLocomotiveCsv.NotNew.REFUSED ? "loc.ui.importCsvNotRefused" : "loc.ui.importCsvNotGone") + ")"));
        return String.join(", ", out);
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

    /** The possible duplicates' table (FR-249): one row per file row, on Merge where it can be, else Keep Both. */
    private static final class Duplicates
    {
        private final Map<Duplicate, Map<Choice, JRadioButton>> choices = new LinkedHashMap<>();
        /** "(can't apply)" beside the file's name where another row's Merge or Replace took the locomotive here. */
        private final Map<Duplicate, JLabel> taken = new HashMap<>();
        // A linked heading sets every row it can, as a row's own pick would
        final ChoiceTable table = new ChoiceTable(UiStyle.blackLabel(I18n.t("loc.ui.importCsvInFile")),
            UiStyle.blackLabel(I18n.t("sync.ui.thisComputer")),
            SyncDifferencesWindow.head(setAllTo("loc.ui.importCsvMerge", Choice.MERGE), "loc.ui.importCsvTipMerge"),
            SyncDifferencesWindow.head(setAllTo("loc.ui.importCsvReplace", Choice.REPLACE), "loc.ui.importCsvTipReplace"),
            SyncDifferencesWindow.head(setAllTo("sync.ui.keepBoth", Choice.BOTH), "loc.ui.importCsvTipBoth"),
            SyncDifferencesWindow.head(setAllTo("sync.ui.ignore", Choice.IGNORE), "loc.ui.importCsvTipIgnore"));

        /** Every row starts as the Merge heading leaves it: a grayed or taken Merge stays on Keep Both. */
        Duplicates(List<Duplicate> rows)
        {
            for (Duplicate d : rows) addRow(d);
            setAll(Choice.MERGE);
        }

        /** A row, named for the tests by the file's name: each side's Name and Address lines, then the four radios. */
        private void addRow(Duplicate d)
        {
            String name = d.row.name, old = d.here.get(0).getName(), kName = I18n.t("sync.ui.name");
            String why = d.whyNotNew == MarklinLocomotiveCsv.NotNew.SEVERAL ? I18n.f("loc.ui.importCsvWhySeveral",
                d.address) : d.whyNotNew == MarklinLocomotiveCsv.NotNew.LISTED ? I18n.f("loc.ui.importCsvWhyListed", old)
                : d.whyNotNew != null ? I18n.f("loc.ui.importCsvWhyRunning", old) : null;
            JLabel took = null;
            if (why == null)
            {
                // Always laid out, empty until taken, so the dialog is sized with its text
                took = SyncDifferencesWindow.text(I18n.t("sync.ui.cantApply"), SyncDifferencesWindow.GRAYED);
                took.setPreferredSize(took.getPreferredSize());
                took.setText("");
                took.setName(name + ":taken");
                this.taken.put(d, took);
            }
            List<JComponent> here = new ArrayList<>();
            for (Locomotive l : d.here) here.add(SyncDifferencesWindow.line(this.table, kName, l.getName()
                + (l.hasLinkedLocomotives() ? " +" + l.getLinkedLocomotives().size() : ""), true, null, null));
            here.add(address(d));
            ButtonGroup group = new ButtonGroup();
            Map<Choice, JRadioButton> radios = new EnumMap<>(Choice.class);
            radio(d, radios, group, Choice.MERGE, I18n.f("loc.ui.importCsvMergeSaid", old, name), why);
            radio(d, radios, group, Choice.REPLACE, I18n.f("loc.ui.importCsvReplaceSaid", old, name), why);
            radio(d, radios, group, Choice.BOTH, I18n.f("sync.ui.keepBothSaid", old, name), null);
            radio(d, radios, group, Choice.IGNORE, I18n.f("loc.ui.importCsvIgnoreSaid", name), null);
            radios.get(Choice.BOTH).setSelected(true);
            radios.values().forEach(b -> b.addItemListener(e -> refresh()));
            this.choices.put(d, radios);
            this.table.addRow(ChoiceTable.lines(SyncDifferencesWindow.line(this.table, kName, name, true, why, took),
                address(d)), ChoiceTable.lines(here.toArray(new JComponent[0])), radios.get(Choice.MERGE),
                radios.get(Choice.REPLACE), radios.get(Choice.BOTH), radios.get(Choice.IGNORE));
        }

        private JComponent address(Duplicate d)
        {
            return SyncDifferencesWindow.line(this.table, I18n.t("sync.ui.address"), d.address, false, null, null);
        }

        private void radio(Duplicate d, Map<Choice, JRadioButton> radios, ButtonGroup group, Choice c, String said,
            String whyNot)
        {
            radios.put(c, SyncDifferencesWindow.choice(this.table, group, d.row.name + ":" + c.name(), said, whyNot));
        }

        /** One Merge or Replace per locomotive here: another row's are grayed, saying which row took it. */
        private void refresh()
        {
            Map<Locomotive, Duplicate> takers = new HashMap<>();
            this.choices.forEach((d, r) ->
            {
                if (r.get(Choice.MERGE).isSelected() || r.get(Choice.REPLACE).isSelected()) takers.put(d.here.get(0), d);
            });
            this.taken.forEach((d, mark) ->
            {
                Duplicate taker = takers.get(d.here.get(0));
                boolean free = taker == null || taker == d;
                this.choices.get(d).get(Choice.MERGE).setEnabled(free);
                this.choices.get(d).get(Choice.REPLACE).setEnabled(free);
                mark.setText(free ? "" : I18n.t("sync.ui.cantApply"));
                mark.setToolTipText(free ? null : I18n.f("loc.ui.importCsvWhyTaken", taker.row.name,
                    d.here.get(0).getName()));
            });
        }

        private JButton setAllTo(String key, Choice to)
        {
            return ChoiceTable.link(I18n.t(key), () -> setAll(to));
        }

        private void setAll(Choice to)
        {
            this.choices.values().forEach(r -> { if (r.get(to).isEnabled()) r.get(to).setSelected(true); });
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
