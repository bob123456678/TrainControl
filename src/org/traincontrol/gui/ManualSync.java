package org.traincontrol.gui;

import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.gui.SyncDifferencesWindow.Answer;
import org.traincontrol.gui.widgets.BusyDialog;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;

/** The pressed Sync (FR-249): reads the station, asks in the Sync table, applies the answers, syncs and says so. */
public final class ManualSync
{
    private final TrainControlUI ui;
    private final ViewListener model;
    /** askAndApply's answer when the table was canceled. */
    private static final int[] CANCELED = {};

    ManualSync(TrainControlUI ui)
    {
        this.ui = ui;
        this.model = ui.getModel();
    }

    /** Says on c, under the Sync's title, what the sync just ended added, the answers given that are not zero. */
    void saySynced(Component c, int synced, int... answers)
    {
        String text = syncSummary(synced - model.getLastSyncRoutes(), model.getLastSyncRoutes(),
            model.wasLastSyncPartial(), answers);
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(c, text, I18n.t("sync.ui.title"),
            JOptionPane.INFORMATION_MESSAGE));
    }

    /**
     * A sync's closing message: the locomotives and routes it added, then of the table's answers (renamed, re-addressed,
     * kept, ignored, waiting) those not zero, and the partial read's sentence; with none of these, that all matches.
     */
    public static String syncSummary(int locomotives, int routes, boolean partial, int... answers)
    {
        List<String> added = new ArrayList<>(), said = new ArrayList<>();
        if (locomotives > 0) added.add(I18n.f(locomotives == 1 ? "ui.infoSyncLocomotiveAdded"
            : "ui.infoSyncLocomotivesAdded", locomotives));
        if (routes > 0) added.add(I18n.f(routes == 1 ? "ui.infoSyncRouteAdded" : "ui.infoSyncRoutesAdded", routes));
        String[] kinds = {"ui.infoSyncRenamed", "ui.infoSyncMoved", "ui.infoSyncKept", "ui.infoSyncIgnored",
            "ui.infoSyncWaiting", "ui.infoSyncByHand"};
        for (int i = 0; i < answers.length; i++) if (answers[i] > 0) said.add(I18n.f(kinds[i], answers[i]));
        if (!partial && added.isEmpty() && said.isEmpty()) return I18n.t("ui.infoSyncNothingDiffers");
        List<String> parts = new ArrayList<>(Arrays.asList(I18n.t("ui.infoSyncComplete")));
        if (!added.isEmpty()) parts.add(String.join("\n", added));
        if (!said.isEmpty()) parts.add(String.join("\n", said));
        if (partial) parts.add(I18n.t("ui.infoSyncPartial"));
        return String.join("\n\n", parts);
    }

    /**
     * The pressed Sync (FR-249): read, table, answers and sync under one hold of the flag; SYNC_REFUSED when canceled.
     */
    int pressedSync(Component c, int[] answered)
    {
        return ui.holdingTheSync(() ->
        {
            List<RenameProposals.Row> taken = new ArrayList<>();
            try
            {
                int[] n = askAndApply(c, taken);
                if (n == CANCELED)
                {
                    model.logf("loc.syncCanceled");
                    return TrainControlUI.SYNC_REFUSED;
                }
                int synced = RunGates.refuseALinkOrSyncWhileEditorOpen(ui, null) ? TrainControlUI.SYNC_REFUSED
                    : ui.syncHolding();
                final LocomotiveSettingsFrame open = ui.locomotiveSettings;
                if (open != null) SwingUtilities.invokeLater(open::followTheSync);
                if (n.length == 0 || synced < 0) return synced;
                int moved = (int) taken.stream().filter(r -> r.loco.getAddress() == r.stationAddress).count();
                System.arraycopy(new int[] {n[1], moved, n[2], n[3], n[4] + taken.size() - moved, n[5]}, 0, answered,
                    0, 6);
                return synced + n[0];
            }
            finally
            {
                // A station's address this Sync was told to use is for this sync alone
                for (RenameProposals.Row r : taken) r.loco.setTakenStationAddress(null);
            }
        });
    }

    /**
     * Reads, asks over c and applies, re-addressed rows into `taken`: apply's counts, none unconnected, or CANCELED.
     */
    private int[] askAndApply(Component c, List<RenameProposals.Row> taken)
    {
        if (!model.getNetworkCommState()) return new int[0];
        try
        {
            RenameProposals asked = readBehindTheSpinner(c, true);
            // Each row's locomotive by its name as asked, so one renamed or replaced meanwhile is left
            Map<Locomotive, String> names = new HashMap<>();
            for (RenameProposals.Row r : asked.getRows()) names.put(r.loco, r.loco.getName());
            Window asking = c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c);
            List<Map<RenameProposals.Row, Answer>> answers = new ArrayList<>();
            // Over the window that asked, or this one once that is gone
            SwingUtilities.invokeAndWait(() -> answers.add(SyncDifferencesWindow.ask(asking != null
                && asking.isShowing() ? asking : ui, asked)));
            Map<RenameProposals.Row, Answer> a = answers.get(0);
            if (a == null) return CANCELED;
            if (asked.getRows().isEmpty() && !asked.hasRefusals()) model.logf("loc.ui.infoNoLocomotivesToRename");
            // Read again once anything was answered, so a row the station changed meanwhile is left; unread, the
            // locomotives alone are checked
            RenameProposals again = a.values().stream().anyMatch(x -> x != Answer.ASK)
                ? readBehindTheSpinner(c, false) : null;
            RenameProposals current = again != null ? again : asked;
            List<int[]> n = new ArrayList<>();
            SwingUtilities.invokeAndWait(() -> n.add(apply(c, asked, current, a, names, taken)));
            return n.get(0);
        }
        catch (Exception e)
        {
            // Answers applied before a throw stay, and the sync still runs
            model.log(e);
            return new int[0];
        }
    }

    /** The station's rename proposals, read behind the spinner over c's window; quietly, null if unread, unless `log`. */
    private RenameProposals readBehindTheSpinner(Component c, boolean log) throws Exception
    {
        BusyDialog.Closer busy = BusyDialog.showUntilClosed(c instanceof Window ? (Window) c
            : SwingUtilities.getWindowAncestor(c), I18n.t("ui.busySyncingWithCS"));
        try
        {
            return model.getRenameProposals(log);
        }
        finally
        {
            // Posted before the questions, so it is down before they come up
            busy.close();
        }
    }

    /**
     * Applies the answers over c, renames first, and leaves a row changed since asked; the counts pressedSync reads.
     */
    private int[] apply(Component c, RenameProposals asked, RenameProposals now,
        Map<RenameProposals.Row, Answer> answers, Map<Locomotive, String> names, List<RenameProposals.Row> taken)
    {
        // A Keep the station no longer offers is forgotten, so nothing is left to hold back a later offer; on a partial
        // read, one with no row is kept, as its record may be what did not read
        Map<Locomotive, RenameProposals.Row> differing = new HashMap<>(), current = new HashMap<>();
        for (RenameProposals.Row r : asked.getRows()) differing.put(r.loco, r);
        for (RenameProposals.Row r : now.getRows()) current.put(r.loco, r);
        for (Locomotive l : model.getLocomotives())
        {
            RenameProposals.Row r = differing.get(l);
            if (r == null && asked.partial) continue;
            String kept = l.getDeclinedStationName();
            if (kept != null && (r == null || !kept.equals(r.stationName))) l.setDeclinedStationName(null);
            else if (kept != null && !l.isSyncIgnored()) model.logf("loc.renameKeptAsAnswered", l.getName(), kept);
            if (r == null || !Objects.equals(r.stationAddress, l.getDeclinedStationAddress()))
                l.setDeclinedStationAddress(null);
        }
        // A run begun since the read refuses every rename, said once
        boolean running = answers.containsValue(Answer.RENAME) && RunGates.refuseWhileAutonomyRunning(ui, c);
        List<String> order = new ArrayList<>();
        for (String[] p : asked.getProposals()) order.add(p[0]);
        List<RenameProposals.Row> rows = new ArrayList<>(answers.keySet());
        rows.sort(Comparator.comparingInt(r -> answers.get(r) == Answer.RENAME ? order.indexOf(names.get(r.loco))
            : answers.get(r) == Answer.BOTH ? order.size() : order.size() + 1));
        // Each locomotive still under its name as asked, so one an earlier answer below takes it from is said so
        Set<Locomotive> unchanged = new HashSet<>();
        for (RenameProposals.Row r : rows)
            if (model.getLocByName(names.get(r.loco)) == r.loco) unchanged.add(r.loco);
        int[] n = {0, 0, 0, 0, 0, (int) asked.getRows().stream().filter(r -> r.byHand).count()};
        for (RenameProposals.Row r : rows)
        {
            Answer a = answers.get(r);
            String name = names.get(r.loco);
            RenameProposals.Row f = current.get(r.loco);
            boolean gone = model.getLocByName(name) != r.loco;
            if (a != Answer.IGNORE) r.loco.setSyncIgnored(false);
            if (a != Answer.ASK && gone && unchanged.contains(r.loco))
            {
                model.logf("loc.syncRowAnsweredAway", name);
                continue;
            }
            if (a == Answer.ASK || gone || f == null || !Objects.equals(f.stationName, r.stationName)
                || !Objects.equals(f.stationAddress, r.stationAddress))
            {
                model.logf(a == Answer.ASK ? "loc.syncLeftToAsk" : "loc.syncRowChanged", name);
                n[4]++;
                continue;
            }
            switch (a)
            {
                case KEEP:
                    if (r.asksName()) model.logf(keptAfterNo(r.loco, r.stationName), name, r.stationName);
                    else r.loco.setDeclinedStationAddress(r.stationAddress);
                    n[2]++;
                    break;
                case IGNORE:
                    r.loco.setSyncIgnored(true);
                    model.logf("loc.syncIgnored", name);
                    n[3]++;
                    break;
                case BOTH:
                    // Its name kept, so the two at one address are not offered as a rename next time
                    boolean added = model.addStationLocomotive(f.station) != null;
                    if (added) r.loco.setDeclinedStationName(r.stationName);
                    n[added ? 0 : 4]++;
                    break;
                case ADDRESS:
                    r.loco.setDeclinedStationAddress(null);
                    r.loco.setTakenStationAddress(r.stationAddress);
                    taken.add(r);
                    break;
                default:
                    String why = running ? I18n.f("loc.renameNotApplied", name, r.stationName) : f.whyNot(true);
                    String outcome = why != null ? null : renamed(c, r);
                    if (why != null) model.log(why);
                    else model.logf(outcome, name, r.stationName);
                    n["loc.renameApplied".equals(outcome) ? 1 : "loc.renameKept".equals(outcome) ? 2 : 4]++;
            }
        }
        return n;
    }

    /** Renames r's locomotive to the station's name, through the delete question for a name held here; the log key. */
    private String renamed(Component c, RenameProposals.Row r)
    {
        String currentName = r.loco.getName(), newName = r.stationName;
        Locomotive l = r.loco;
        // Like the other doors into `renameLoc`, asked of both names just before it: a route may have begun meanwhile
        if (RunGates.refuseWhileARouteDrivesIt(ui, c, currentName)
            || RunGates.refuseWhileARouteDrivesIt(ui, c, newName)) return "loc.renameNotApplied";
        if (model.getLocByName(newName) != null) ui.locomotiveActions.deleteLocBecause(
            I18n.f("loc.ui.confirmRenameOntoLocomotive", currentName, newName), newName, c);
        if (model.getLocByName(newName) != null)
        {
            // Only a No to the delete question is kept, never a refusal the user could not answer
            int answer = ui.locomotiveActions.answered;
            return answer == JOptionPane.NO_OPTION ? keptAfterNo(l, newName)
                : answer == JOptionPane.CLOSED_OPTION ? "loc.renameClosed" : "loc.renameNotApplied";
        }
        model.renameLoc(currentName, newName);
        l.setDeclinedStationName(null);
        ui.clearCopyTarget();
        ui.repaintLoc(true, null);
        ui.repaintMappings(null, false);
        ui.refreshRouteList();
        ui.getLocSelector().refreshLocSelectorList();
        if (model.hasAutoLayout())
        {
            model.getAutoLayout().sanitizeMultiUnits(l);
            model.getAutoLayout().refreshUI();
            // And the station labels, as the rename dialog does (OB-081)
            ui.updateVisiblePoints();
            ui.repaintAutoLocList(false);
            ui.repaintTimetable();
        }
        return "loc.renameApplied";
    }

    /** Asks over c: Keep (first) or Use the station's address, and marks l so. */
    private static void askAboutAddress(Component c, Locomotive l, int address)
    {
        String keep = I18n.t("loc.ui.optionKeep"), use = I18n.f("loc.ui.optionUseAddress", address);
        Object[] options = {keep, use};
        int answer = JOptionPane.showOptionDialog(c, I18n.f("loc.ui.askStationAddress", l.getName(), address,
            l.getAddress()), I18n.t("loc.ui.dialogStationAddress"), JOptionPane.DEFAULT_OPTION,
            JOptionPane.PLAIN_MESSAGE, null, options, keep);
        if (answer == 0) l.setDeclinedStationAddress(address);
        if (answer == 1) l.setTakenStationAddress(address);
    }

    /** The station's addresses by name, read over c as `readBehindTheSpinner`; null when not connected or not read. */
    Map<String, Integer> stationAddresses(Component c)
    {
        if (!model.getNetworkCommState()) return null;
        try
        {
            RenameProposals read = readBehindTheSpinner(c, true);
            Map<String, Integer> addresses = new java.util.HashMap<>(read.getAddresses());
            // A locomotive the table leaves to fix by hand is asked nothing here either
            for (RenameProposals.Row r : read.getRows()) if (r.byHand) addresses.remove(r.loco.getName());
            return addresses;
        }
        catch (Exception e)
        {
            model.log(e);
            return null;
        }
    }

    /** Asks over c about l's address alone (FR-246). */
    static void askAboutItsAddress(Component c, Locomotive l, Map<String, Integer> addresses)
    {
        if (addresses == null) return;
        Integer at = addresses.get(l.getName());
        // A kept address is not asked again; one the station no longer has is forgotten
        if (Objects.equals(at, l.getDeclinedStationAddress())) return;
        l.setDeclinedStationAddress(null);
        if (at != null) askAboutAddress(c, l, at);
    }

    /** A rename answered No, kept on the locomotive so the same offer is not asked again; its log line's key. */
    private static String keptAfterNo(Locomotive l, String newName)
    {
        l.setDeclinedStationName(newName);
        return "loc.renameKept";
    }
}
