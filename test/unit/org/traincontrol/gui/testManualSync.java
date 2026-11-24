package org.traincontrol.gui;

import java.awt.Component;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.base.RenameProposals.Row;
import org.traincontrol.gui.SyncDifferencesWindow.Answer;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinLocomotiveCsv;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import static support.Components.onTheEventThread;

/**
 * `ManualSync` on its own (FR-249): how the Sync table's answers are applied, the menu's mark, an import's mark and the
 * closing message, through a fake of what they need of the main window.  The pressed Sync itself (holding the sync,
 * the busy sign, the table, the second read, the rename's questions) is in its integration tests,
 * `ui.testTheSyncDifferencesWindow`, `ui.testTheSyncAsksAboutRenamedLocomotives` and
 * `regression.testLocomotiveCsvImportDoor`.
 */
public class testManualSync
{
    /**
     * Each answer does what it says: Rename renames, Keep As Is keeps the station's name or address so it is not asked
     * again, Change Address takes the station's address for this sync (forgetting a Keep of it), Ignore ignores, Ask
     * Again leaves it, and Keep Both adds the station's locomotive (or leaves it waiting when that is refused); any
     * answer but Ignore lifts an Ignore.  The counts are added, renamed, kept, ignored, waiting and by hand.
     *
     * MUTATION: in `ManualSync.apply`, leave out Keep's `setDeclinedStationAddress(r.stationAddress)`, and Uri's
     * address is asked again.
     */
    @Test
    public void testEachAnswerDoesWhatItSays()
    {
        Fake ui = new Fake();
        Locomotive kept = ui.loco("Kept"), uri = ui.loco("Uri"), mover = ui.loco("Mover"), ign = ui.loco("Ign"),
            later = ui.loco("Later");
        kept.setSyncIgnored(true);
        mover.setDeclinedStationAddress(31);
        ui.refusedAdds.add("W2");
        Map<Row, Answer> answers = new LinkedHashMap<>();
        answers.put(row(ui.loco("Shunter"), "V 60", null), Answer.RENAME);
        answers.put(row(kept, "K2", null), Answer.KEEP);
        answers.put(row(uri, null, 22), Answer.KEEP);
        answers.put(row(mover, null, 31), Answer.ADDRESS);
        answers.put(row(ign, "I2", null), Answer.IGNORE);
        answers.put(row(later, "L2", null), Answer.ASK);
        answers.put(row(ui.loco("Twin"), "T2", null), Answer.BOTH);
        answers.put(row(ui.loco("Wall"), "W2", null), Answer.BOTH);
        List<Row> taken = new ArrayList<>();

        int[] n = ui.apply(null, answers, taken);

        assertEquals(n, new int[] {1, 1, 2, 1, 2, 0}, "added, renamed, kept, ignored, waiting, by hand: " + ui.said);
        assertEquals(ui.said, Arrays.asList("asked whether a run refuses", "rename Shunter to V 60",
            "loc.renameApplied [Shunter, V 60]", "add T2", "add W2", "loc.renameKept [Kept, K2]",
            "loc.syncIgnored [Ign]", "loc.syncLeftToAsk [Later]"));
        assertEquals(Arrays.asList(kept.getDeclinedStationName(), kept.isSyncIgnored(), uri.getDeclinedStationAddress(),
            ign.isSyncIgnored(), ui.byName.get("Twin").getDeclinedStationName(), ui.byName.get("Wall")
            .getDeclinedStationName()), Arrays.asList("K2", false, 22, true, "T2", null), "what each locomotive keeps");
        assertEquals(taken.size() == 1 && taken.get(0).loco == mover && mover.takesStationAddress(31)
            && mover.getDeclinedStationAddress() == null, true, "Mover does not take address 31 for this sync");
    }

    /**
     * A row is left when its locomotive was renamed or replaced while the table was open, or the station's row changed
     * or went; one whose locomotive an earlier answer took the name from (a rename onto it) is said so and not counted.
     *
     * MUTATION: in `ManualSync.apply`, leave out the `getLocByName(name) != r.loco` check, and Away's Keep is applied.
     */
    @Test
    public void testARowChangedSinceAskedIsLeft()
    {
        Fake ui = new Fake();
        Locomotive away = ui.loco("Away"), moved = ui.loco("Moved"), gone = ui.loco("Gone"), held = ui.loco("Held"),
            readdressed = ui.loco("Readdr");
        ui.byName.put("Away 2", ui.byName.remove("Away"));
        Map<Row, Answer> answers = new LinkedHashMap<>();
        answers.put(row(away, "A2", null), Answer.KEEP);
        answers.put(row(moved, "M2", null), Answer.RENAME);
        answers.put(row(gone, "G2", null), Answer.IGNORE);
        answers.put(row(held, "H2", null), Answer.KEEP);
        answers.put(row(readdressed, null, 40), Answer.KEEP);
        answers.put(row(ui.loco("Taker"), "Held", null), Answer.RENAME);
        Map<Locomotive, Row> now = new HashMap<>();
        for (Row r : answers.keySet()) now.put(r.loco, r);
        now.put(moved, row(moved, "M3", null));
        now.put(readdressed, row(readdressed, null, 41));
        now.remove(gone);

        int[] n = ui.apply(null, answers, now.values(), new ArrayList<>());

        assertEquals(n, new int[] {0, 1, 0, 0, 4, 0}, "added, renamed, kept, ignored, waiting, by hand: " + ui.said);
        assertEquals(ui.said, Arrays.asList("asked whether a run refuses", "loc.syncRowChanged [Moved]",
            "rename Taker to Held", "loc.renameApplied [Taker, Held]", "loc.syncRowChanged [Away]",
            "loc.syncRowChanged [Gone]", "loc.syncRowAnsweredAway [Held]", "loc.syncRowChanged [Readdr]"));
        assertNull(away.getDeclinedStationName(), "Away's Keep was applied");
    }

    /**
     * Renames go first, in the order the station's proposals give (so one that frees a name comes before the one that
     * takes it), then Keep Both, then the rest.
     *
     * MUTATION: in `ManualSync.apply`, leave out the sort, and A is renamed before B.
     */
    @Test
    public void testRenamesGoFirstInTheProposalsOrder()
    {
        Fake ui = new Fake();
        Map<Row, Answer> answers = new LinkedHashMap<>();
        answers.put(row(ui.loco("Keepy"), "K2", null), Answer.KEEP);
        answers.put(row(ui.loco("A"), "A2", null), Answer.RENAME);
        answers.put(row(ui.loco("Twin"), "T2", null), Answer.BOTH);
        answers.put(row(ui.loco("B"), "B2", null), Answer.RENAME);
        ui.proposals.add(new String[] {"B", "B2"});
        ui.proposals.add(new String[] {"A", "A2"});

        ui.apply(null, answers, new ArrayList<>());

        assertEquals(ui.said, Arrays.asList("asked whether a run refuses", "rename B to B2", "loc.renameApplied [B, B2]",
            "rename A to A2", "loc.renameApplied [A, A2]", "add T2", "loc.renameKept [Keepy, K2]"));
    }

    /**
     * A run begun since the read refuses every rename, asked once and only when something is to be renamed; a rename
     * the row refuses says why; what the rename itself answers is counted as renamed, kept or waiting.
     *
     * MUTATION: in `ManualSync.apply`, leave out `answers.containsValue(Answer.RENAME) &&`, and a sync with no rename
     * asks whether a run refuses.
     */
    @Test
    public void testARunOrTheRowRefusesARenameAndTheRenameIsCounted()
    {
        Fake running = new Fake();
        running.running = true;
        Map<Row, Answer> renames = new LinkedHashMap<>();
        renames.put(row(running.loco("A"), "A2", null), Answer.RENAME);
        renames.put(row(running.loco("B"), "B2", null), Answer.RENAME);
        Fake notRenaming = new Fake();
        notRenaming.running = true;
        Fake each = new Fake();
        each.outcomes.put("K", "loc.renameKept");
        each.outcomes.put("C", "loc.renameClosed");
        Map<Row, Answer> answered = new LinkedHashMap<>();
        for (String name : new String[] {"R", "K", "C"}) answered.put(row(each.loco(name), name + "2", null),
            Answer.RENAME);
        Locomotive locked = each.loco("Locked");
        answered.put(new Row(locked, "L2", null, "Locked's new name is taken", null, null, null, 0, 0), Answer.RENAME);

        assertEquals(running.apply(null, renames, new ArrayList<>()), new int[] {0, 0, 0, 0, 2, 0}, "under a run");
        assertEquals(running.said, Arrays.asList("asked whether a run refuses", I18n.f("loc.renameNotApplied", "A",
            "A2"), I18n.f("loc.renameNotApplied", "B", "B2")), "under a run");
        notRenaming.apply(null, Collections.singletonMap(row(notRenaming.loco("I"), "I2", null),
            Answer.IGNORE), new ArrayList<>());
        assertFalse(notRenaming.said.contains("asked whether a run refuses"), "asked with nothing to rename");
        assertEquals(each.apply(null, answered, new ArrayList<>()), new int[] {0, 1, 1, 0, 2, 0}, "each outcome");
        assertEquals(each.said, Arrays.asList("asked whether a run refuses", "rename R to R2",
            "loc.renameApplied [R, R2]", "rename K to K2", "loc.renameKept [K, K2]", "rename C to C2",
            "loc.renameClosed [C, C2]", "Locked's new name is taken"), "each outcome");
    }

    /**
     * A Keep As Is the station no longer offers is forgotten, so nothing holds back a later offer, and one it still
     * offers is said unless the locomotive is ignored; on a partial read a locomotive with no row keeps its Keep, as its
     * record may be what did not read.
     *
     * MUTATION: in `ManualSync.apply`, leave out the `asked.partial` skip, and Old's Keep is forgotten on a partial
     * read.
     */
    @Test
    public void testAKeepTheStationNoLongerOffersIsForgotten()
    {
        List<String> kept = new ArrayList<>();
        for (boolean partial : new boolean[] {false, true})
        {
            Fake ui = new Fake();
            ui.partial = partial;
            Locomotive old = ui.loco("Old"), same = ui.loco("Same"), other = ui.loco("Other"),
                addressed = ui.loco("Adr");
            old.setDeclinedStationName("O2");
            old.setDeclinedStationAddress(23);
            same.setDeclinedStationName("S2");
            same.setDeclinedStationAddress(24);
            other.setDeclinedStationName("X2");
            addressed.setDeclinedStationAddress(22);
            Locomotive quiet = ui.loco("Quiet");
            quiet.setSyncIgnored(true);
            quiet.setDeclinedStationName("Q2");
            ui.rows.addAll(Arrays.asList(row(same, "S2", null), row(other, "X3", null), row(addressed, null, 22),
                row(quiet, "Q2", null)));

            ui.apply(null, new HashMap<>(), new ArrayList<>());

            kept.add(Arrays.asList(old.getDeclinedStationName(), old.getDeclinedStationAddress(), same
                .getDeclinedStationName(), same.getDeclinedStationAddress(), other.getDeclinedStationName(),
                addressed.getDeclinedStationAddress(),
                quiet.getDeclinedStationName()) + " " + ui.said);
        }

        assertEquals(kept, Arrays.asList("[null, null, S2, null, null, 22, Q2] [loc.renameKeptAsAnswered [Same, S2]]",
            "[O2, 23, S2, null, null, 22, Q2] [loc.renameKeptAsAnswered [Same, S2]]"), "a full read, then a partial one");
    }

    /**
     * Rows left to fix by hand are counted, only the one asked about's when the Sync asks about one locomotive (its
     * Locomotive Settings' Sync).
     *
     * MUTATION: in `ManualSync.apply`, count `r.byHand` alone, and Other's row is counted for Twice's Sync.
     */
    @Test
    public void testRowsByHandAreCountedForTheOneAskedAbout()
    {
        Fake ui = new Fake();
        Locomotive twice = ui.loco("Twice");
        ui.rows.addAll(Arrays.asList(row(twice, "T2", 51), row(ui.loco("Other"), "O2", 52), row(ui.loco("Plain"), "P2",
            null)));

        assertEquals(ui.apply(null, new HashMap<>(), new ArrayList<>())[5], 2, "every locomotive");
        assertEquals(ui.apply(twice, new HashMap<>(), new ArrayList<>())[5], 1, "Twice's Sync");
    }

    /**
     * The Locomotives menu and its Sync item wear " *" while something is unsettled, the tooltip saying how many, or
     * that an import may differ (-1); a tooltip already there (a refusal's) is kept, and with none unsettled or no
     * railway there is no mark.
     *
     * MUTATION: in `ManualSync.mark`, leave out the `getToolTipText() == null` test, and the refusal is overwritten.
     */
    @Test
    public void testTheMarkSaysWhatIsUnsettled()
    {
        List<String> said = onTheEventThread(() ->
        {
            List<String> out = new ArrayList<>();
            for (Object[] c : new Object[][] {{null, null}, {0, null}, {1, null}, {3, null}, {-1, null}, {2, "refused"}})
            {
                Fake ui = new Fake();
                ui.unsettled = c[0] == null ? 0 : (Integer) c[0];
                JMenu menu = new JMenu();
                JMenuItem sync = new JMenuItem();
                sync.setToolTipText((String) c[1]);
                ManualSync.mark(c[0] == null ? null : ui.getModel(), menu, sync);
                out.add(menu.getText() + "|" + sync.getText() + "|" + sync.getToolTipText());
            }
            return out;
        });

        String menu = I18n.t("ui.main.toolbar.locomotives"), sync = I18n.t("ui.main.toolbar.syncDBWithCS");
        String plain = menu + "|" + sync + "|null", marked = menu + " *|" + sync + " *|";
        assertEquals(said, Arrays.asList(plain, plain, marked + I18n.t("sync.ui.markOne"), marked
            + I18n.f("sync.ui.markMany", 3), marked + I18n.t("sync.ui.markImported"), marked + "refused"));
    }

    /**
     * An import that re-addressed or renamed a locomotive here marks the menu (-1) and has it grayed and marked anew;
     * one that did neither leaves it.
     *
     * MUTATION: in `ManualSync.imported`, leave out `result.renamed.isEmpty() &&`, and a rename alone marks nothing.
     */
    @Test
    public void testAnImportThatChangedALocomotiveMarksTheMenu()
    {
        List<String> said = new ArrayList<>();
        for (String changed : new String[] {"readdressed", "renamed", "neither"})
        {
            Fake ui = new Fake();
            MarklinLocomotiveCsv.Result result = new MarklinLocomotiveCsv.Result();
            if (changed.equals("readdressed")) result.readdressed.add("A");
            if (changed.equals("renamed")) result.renamed.add("B");
            result.added = 1;
            ManualSync.imported(ui, result);
            said.add(changed + " " + ui.unsettled + " " + ui.greyed);
        }

        assertEquals(said, Arrays.asList("readdressed -1 1", "renamed -1 1", "neither 0 0"));
    }

    /**
     * The closing message lists the locomotives and routes added, then the answers that are not zero, then the partial
     * read's sentence; with none of these, that everything matches.
     *
     * MUTATION: in `ManualSync.syncSummary`, list every answer, zero or not, and the zeros are said.
     */
    @Test
    public void testTheSummarySaysWhatTheSyncDid()
    {
        String done = I18n.t("ui.infoSyncComplete");

        assertEquals(ManualSync.syncSummary(0, 0, false, 0, 0, 0, 0, 0, 0), I18n.t("ui.infoSyncNothingDiffers"));
        assertEquals(ManualSync.syncSummary(0, 0, true), done + "\n\n" + I18n.t("ui.infoSyncPartial"));
        assertEquals(ManualSync.syncSummary(1, 2, false, 0, 3, 0, 0, 1, 0), done + "\n\n"
            + I18n.t("ui.infoSyncLocomotiveAdded") + "\n" + I18n.f("ui.infoSyncRoutesAdded", 2) + "\n\n"
            + I18n.f("ui.infoSyncMoved", 3) + "\n" + I18n.f("ui.infoSyncWaiting", 1));
        assertEquals(ManualSync.syncSummary(2, 1, true, 1, 0, 2, 4, 0, 5), done + "\n\n"
            + I18n.f("ui.infoSyncLocomotivesAdded", 2) + "\n" + I18n.t("ui.infoSyncRouteAdded") + "\n\n"
            + I18n.f("ui.infoSyncRenamed", 1) + "\n" + I18n.f("ui.infoSyncKept", 2) + "\n"
            + I18n.f("ui.infoSyncIgnored", 4) + "\n" + I18n.f("ui.infoSyncByHand", 5) + "\n\n"
            + I18n.t("ui.infoSyncPartial"));
    }

    /** A row for l: the station's name and address, each null where they agree; a by-hand row when both differ. */
    private static Row row(Locomotive l, String name, Integer address)
    {
        return new Row(l, name, address, null, null, null, name == null ? null : new MarklinLocomotive(null, 99,
            Locomotive.decoderType.DCC, name), 0, 0);
    }

    /**
     * The main window as the answers see it: a railway of locomotives by name and its log, a run that may refuse
     * renames, and a rename that moves the name here; `said` is everything logged or asked, in order.
     */
    private static final class Fake implements ManualSync.Main
    {
        final Map<String, Locomotive> byName = new LinkedHashMap<>();
        final List<String> said = new ArrayList<>();
        final Set<String> refusedAdds = new HashSet<>();
        /** What the rename answers, by the locomotive's name; renamed otherwise. */
        final Map<String, String> outcomes = new HashMap<>();
        /** The rows the station read differs on beside those answered, and its proposals' order. */
        final List<Row> rows = new ArrayList<>();
        final List<String[]> proposals = new ArrayList<>();
        boolean running, partial;
        int unsettled, greyed;

        Locomotive loco(String name)
        {
            Locomotive l = new MarklinLocomotive(null, 3 + this.byName.size(), Locomotive.decoderType.DCC, name);
            this.byName.put(name, l);
            return l;
        }

        /** Applies answers to the rows as asked, each read again unchanged; the counts. */
        int[] apply(Locomotive only, Map<Row, Answer> answers, List<Row> taken)
        {
            return apply(only, answers, answers.keySet(), taken);
        }

        /** Applies answers to the rows as asked, read again as `now`; the counts. */
        int[] apply(Locomotive only, Map<Row, Answer> answers, Collection<Row> now, List<Row> taken)
        {
            List<Row> asked = new ArrayList<>(this.rows), again = new ArrayList<>(this.rows);
            asked.addAll(answers.keySet());
            again.addAll(now);
            Map<Locomotive, String> names = new HashMap<>();
            for (Row r : asked) names.put(r.loco, r.loco.getName());
            return ManualSync.apply(this, only, null, proposals(asked), proposals(again), answers, names,
                taken);
        }

        private RenameProposals proposals(List<Row> rows)
        {
            return new RenameProposals(this.proposals, 0, rows, new HashMap<>(), this.partial);
        }

        @Override
        public ViewListener getModel()
        {
            return (ViewListener) Proxy.newProxyInstance(ViewListener.class.getClassLoader(), new Class<?>[]
                {ViewListener.class}, (proxy, method, args) ->
            {
                switch (method.getName())
                {
                    case "getLocomotives":
                        return new ArrayList<>(this.byName.values());
                    case "getLocByName":
                        return this.byName.get((String) args[0]);
                    case "addStationLocomotive":
                        Locomotive station = (Locomotive) args[0];
                        this.said.add("add " + station.getName());
                        if (this.refusedAdds.contains(station.getName())) return null;
                        this.byName.put(station.getName(), station);
                        return station;
                    case "logf":
                        this.said.add(args[0] + " " + Arrays.toString((Object[]) args[1]));
                        return null;
                    case "log":
                        this.said.add(String.valueOf(args[0]));
                        return null;
                    case "getUnsettledCount":
                        return this.unsettled;
                    case "setUnsettledCount":
                        this.unsettled = (Integer) args[0];
                        return null;
                    default:
                        throw new AssertionError("the answers asked the railway " + method.getName());
                }
            });
        }

        @Override
        public void greyWhatARunRefuses()
        {
            this.greyed++;
        }

        @Override
        public boolean refuseWhileAutonomyRunning(Component c)
        {
            this.said.add("asked whether a run refuses");
            return this.running;
        }

        @Override
        public String renameAsTheStation(Component c, Row r)
        {
            String outcome = this.outcomes.getOrDefault(r.loco.getName(), "loc.renameApplied");
            this.said.add("rename " + r.loco.getName() + " to " + r.stationName);
            if (outcome.equals("loc.renameApplied")) this.byName.put(r.stationName, this.byName.remove(r.loco
                .getName()));
            return outcome;
        }
    }
}
