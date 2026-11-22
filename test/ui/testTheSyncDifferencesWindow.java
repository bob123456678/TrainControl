package ui;

import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.StandInStation;
import support.StationRig;
import support.SyncTable;
import static ui.testTheSyncSaysWhenPartOfTheDataWasNotRead.syncEnded;

/**
 * The manual Sync's table (FR-249): one row per locomotive the Central Station differs on, answered before anything
 * changes, with Set all, Cancel and Continue Sync, and the Sync held from the read until the answers are applied.
 * Through the real window against a stand-in CS3 whose locomotive list each claim writes (MM2 addresses 20 to 77).
 */
public class testTheSyncDifferencesWindow
{
    private static final String FUNCTION = "[{\"nr\":0,\"typ\":1,\"fs\":0,\"typ2\":1,\"dauer\":0,\"state\":0,\"isMoment\":false}]";

    /**
     * Each row names the locomotive (decoder address, "+N" for a multi-unit head) and what the station has, the kind in a
     * gray label; Apply says the whole change, twice for a locomotive differing in both, and Keep Both is offered on name
     * rows only.  Rows are in name order with the ignored last on Ignore, the rest on Ask Again, under a heading that
     * counts them (E17, E18, E20, E26, E29).
     *
     * MUTATION: in `SyncDifferencesWindow.addRow`, select Ask Again on an ignored row too.
     */
    @Test(timeOut = 180000)
    public void testARowNamesTheLocomotiveAndWhatTheStationHas() throws Exception
    {
        AtomicReference<String> list = new AtomicReference<>("[]");
        inAWindow(list, (rig, ui) ->
        {
            here(rig, "SW Shunter", 20, "SW Uri", 21, "SW Both", 23, "SW Ignored", 25, "SW Head", 26,
                "SW Member", 27);
            rig.model.getLocByName("SW Ignored").setSyncIgnored(true);
            rig.model.getLocByName("SW Head").setLinkedLocomotives(java.util.Collections.singletonMap("SW Member", 1.0));
            list.set(station("SW V 60", 20, "SW Uri", 22, "SW Other", 23, "SW Both", 24, "SW Ign2", 25,
                "SW Head2", 26, "SW Member", 27));

            List<String> problems = new ArrayList<>();
            SyncTable.Shown s = pressSync(rig, ui, SyncTable.canceling(t ->
            {
                check(problems, t.heading, I18n.f("sync.ui.heading", 5), "heading");
                check(problems, t.rows, Arrays.asList("SW Both", "SW Head", "SW Shunter", "SW Uri", "SW Ignored"), "rows");
                check(problems, t.startsOn.get("SW Ignored"), "IGNORE", "ignored row");
                check(problems, t.startsOn.get("SW Shunter"), "ASK", "name row");
                check(problems, text(t, "SW Shunter", "RENAME"), I18n.f("sync.ui.rename", "SW Shunter", "SW V 60"), "rename");
                check(problems, text(t, "SW Uri", "ADDRESS"), I18n.f("sync.ui.changeAddress", "SW Uri", 22), "address");
                check(problems, Arrays.asList(text(t, "SW Both", "RENAME"), text(t, "SW Both", "ADDRESS")), Arrays.asList(
                    I18n.f("sync.ui.rename", "SW Both", "SW Other"), I18n.f("sync.ui.changeAddress", "SW Both", 24)),
                    "both-ways row");
                check(problems, Arrays.asList(t.in("SW Shunter", "BOTH", AbstractButton.class) != null,
                    t.in("SW Uri", "BOTH", AbstractButton.class) != null), Arrays.asList(true, false), "Keep Both offered");
                check(problems, labels(t).contains("SW Head +1"), true, "head's +N");
                check(problems, labels(t).contains("MM2 20"), true, "decoder address");
                JLabel kind = Components.find(t.dialog, JLabel.class, l -> I18n.t("sync.ui.name").equals(l.getText()));
                check(problems, kind != null && !java.awt.Color.BLACK.equals(kind.getForeground()), true, "gray kind");
            }), null);

            assertNotNull(s, "the table did not open");
            assertTrue(problems.isEmpty(), problems.toString());
        });
    }

    /**
     * Set all to Apply picks only what needs no human pick: not a grayed, triangle or deleting Apply, not a row differing
     * in both, never an ignored row; Keep As Is and Ask Again override every other row.  The triangle follows the other
     * rows' choices (E34, E37, E45, E79, E80, E82).
     *
     * MUTATION: in `SyncDifferencesWindow`, Set all to Apply every row with an enabled Apply (drop `applicable`); or in
     * `refresh`, judge the triangle on the row alone.
     */
    @Test(timeOut = 180000)
    public void testSetAllPicksOnlyWhatNeedsNoHumanPickAndTheMarksFollow() throws Exception
    {
        AtomicReference<String> list = new AtomicReference<>("[]");
        inAWindow(list, (rig, ui) ->
        {
            here(rig, "SW A", 30, "SW B", 31, "SW T", 33, "SW Holder", 34, "SW C", 35, "SW D", 36, "SW Held", 36,
                "SW E", 37, "SW Both", 38, "SW I", 39, "SW F", 41);
            rig.model.getLocByName("SW I").setSyncIgnored(true);
            // A's address is B's, which the station renames and moves; T's is the Holder's, which it renames
            list.set(station("SW A", 31, "SW B", 32, "SW T", 34, "SW C2", 35, "SW Held", 36, "SW E, 1", 37,
                "SW Other", 38, "SW Both", 40, "SW I2", 39, "SW F", 42));

            List<String> problems = new ArrayList<>();
            pressSync(rig, ui, SyncTable.canceling(t ->
            {
                check(problems, visible(t, "SW A", "collision"), true, "A's triangle with B unmoved");
                check(problems, t.in("SW D", "deletes", JLabel.class).getText(), I18n.f("sync.ui.deletes", "SW Held"),
                    "D's deleting mark");
                Components.withText(t.dialog, I18n.t("sync.ui.apply")).doClick();
                check(problems, selected(t), rowsOn("ASK", "ASK", "ASK", "RENAME", "ASK", "ASK", "ADDRESS", "ASK", "ASK"),
                    "Set all to Apply");
                check(problems, visible(t, "SW T", "collision"), true, "T's triangle");
                t.in("SW B", "ADDRESS", AbstractButton.class).doClick();
                check(problems, visible(t, "SW A", "collision"), false, "A's triangle with B moved");
                t.in("SW B", "KEEP", AbstractButton.class).doClick();
                check(problems, visible(t, "SW A", "collision"), true, "A's triangle once B is kept");
                Components.withText(t.dialog, I18n.t("sync.ui.keep")).doClick();
                check(problems, selected(t), rowsOn("KEEP", "KEEP", "KEEP", "KEEP", "KEEP", "KEEP", "KEEP", "KEEP",
                    "KEEP"), "Set all to Keep As Is");
                Components.withText(t.dialog, I18n.t("sync.ui.keepBoth")).doClick();
                check(problems, selected(t), rowsOn("KEEP", "KEEP", "BOTH", "BOTH", "KEEP", "BOTH", "KEEP", "KEEP",
                    "KEEP"), "Set all to Keep Both");
                Components.withText(t.dialog, I18n.t("sync.ui.askAgain")).doClick();
                check(problems, selected(t), rowsOn("ASK", "ASK", "ASK", "ASK", "ASK", "ASK", "ASK", "ASK", "ASK"),
                    "Set all to Ask Again");
            }), null);

            assertTrue(problems.isEmpty(), problems.toString());
        });
    }

    /**
     * Cancel stops the whole Sync: nothing renamed, added or forgotten, the sync does not run and says nothing, and one
     * line is logged; closing the window is the same (E83, E84, E87).
     *
     * MUTATION: in `TrainControlUI.pressedSync`, run the sync after a canceled table (`n = new int[0]`).
     */
    @Test(timeOut = 180000)
    public void testCancelOrClosingStopsTheWholeSync() throws Exception
    {
        AtomicReference<String> list = new AtomicReference<>("[]");
        inAWindow(list, (rig, ui) ->
        {
            here(rig, "SW X", 50, "SW K", 51);
            rig.model.getLocByName("SW K").setDeclinedStationName("SW Gone");
            list.set(station("SW Y", 50, "SW K", 51, "SW New", 52));

            for (int closing = 0; closing < 2; closing++)
            {
                final boolean close = closing == 1;
                int syncs = rig.station.requests("/config/geraet.vrs");
                rig.takeLog();
                Consumer<SyncTable.Shown> look = t ->
                {
                    t.in("SW X", "RENAME", AbstractButton.class).doClick();
                    if (close) t.dialog.dispatchEvent(new WindowEvent(t.dialog, WindowEvent.WINDOW_CLOSING));
                };
                List<String> said = new ArrayList<>();
                assertNotNull(pressSync(rig, ui, close ? SyncTable.answering(SyncTable.answer(), look)
                    : SyncTable.canceling(look), said), "the table did not open");

                assertNotNull(rig.model.getLocByName("SW X"), "canceled, it renamed (closing " + close + ")");
                assertNull(rig.model.getLocByName("SW New"), "canceled, the sync added a locomotive");
                assertEquals(rig.station.requests("/config/geraet.vrs"), syncs, "canceled, the sync ran");
                assertEquals(said, new ArrayList<String>(), "canceled, the sync said something");
                assertEquals(rig.model.getLocByName("SW K").getDeclinedStationName(), "SW Gone", "canceled, a Keep was"
                    + " forgotten");
                // The model does not log a line twice running
                assertTrue(close || rig.takeLog().contains(I18n.t("loc.syncCanceled")), "the cancel was not logged");
            }
        });
    }

    /**
     * Continue applies each answer: Keep As Is keeps, Ignore ignores, Keep Both adds the station's locomotive beside this
     * one at its address, Apply renames or re-addresses, Ask Again does nothing, and the finishing message counts each.
     * The next Sync shows only what is left: the waiting row, and the ignored one last on Ignore, which Ask Again
     * un-ignores (E19, E24, E69, E73, E136, Keep Both).
     *
     * MUTATION: in `TrainControlUI.apply`, leave out Keep Both's `addStationLocomotive`, or Keep Both's
     * `setDeclinedStationName`, and the next Sync offers the pair as a deleting rename.
     */
    @Test(timeOut = 240000)
    public void testTheAnswersAreAppliedAndCounted() throws Exception
    {
        AtomicReference<String> list = new AtomicReference<>("[]");
        inAWindow(list, (rig, ui) ->
        {
            here(rig, "SW Keep", 60, "SW Ign", 61, "SW Both", 62, "SW Ren", 63, "SW Wait", 64, "SW Move", 65);
            list.set(station("SW Keep2", 60, "SW Ign2", 61, "SW Both2", 62, "SW Ren2", 63, "SW Wait2", 64,
                "SW Move", 66));

            List<String> said = new ArrayList<>();
            assertNotNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer("SW Keep", "KEEP", "SW Ign", "IGNORE",
                "SW Both", "BOTH", "SW Ren", "RENAME", "SW Move", "ADDRESS")), said), "the table did not open");

            Locomotive kept = rig.model.getLocByName("SW Keep"), both = rig.model.getLocByName("SW Both"),
                added = rig.model.getLocByName("SW Both2");
            assertEquals(kept.getDeclinedStationName(), "SW Keep2", "Keep As Is was not kept");
            assertTrue(rig.model.getLocByName("SW Ign").isSyncIgnored(), "Ignore was not saved");
            assertNotNull(added, "Keep Both did not add the station's locomotive");
            assertEquals(Arrays.asList(both.getAddress(), added.getAddress()), Arrays.asList(62, 62),
                "Keep Both's two do not share the address");
            assertNotNull(rig.model.getLocByName("SW Ren2"), "Apply did not rename");
            assertEquals(rig.model.getLocByName("SW Move").getAddress(), 66, "Apply did not change the address: "
                + said + rig.takeLog());
            assertNull(rig.model.getLocByName("SW Wait2"), "Ask Again added a copy");
            assertEquals(said, Arrays.asList(I18n.f("ui.infoSyncCompleteItemsAdded", 1) + "\n\n"
                + I18n.f("ui.infoSyncAnswers", 1, 1, 1, 1, 1)), "the finishing message");

            SyncTable.Shown next = pressSync(rig, ui, SyncTable.answering(SyncTable.answer("SW Ign", "ASK")), null);
            assertNotNull(next, "the next Sync showed no table");
            assertEquals(next.rows, Arrays.asList("SW Wait", "SW Ign"), "the next Sync's rows");
            assertEquals(next.startsOn.get("SW Ign"), "IGNORE");
            assertFalse(rig.model.getLocByName("SW Ign").isSyncIgnored(), "Ask Again did not un-ignore");
        });
    }

    /**
     * The Sync holds its running flag from the read until the answers are applied: a second Sync pressed while the
     * table is open is turned away with no second table, and a sync an edit owes meanwhile waits, the Sync's own read
     * after the table taking it (E85, E88, E89).
     *
     * MUTATION: in `TrainControlUI.pressedSync`, take the flag only for the sync itself (`return syncNow()` in place of
     * `syncHolding()`, without the compareAndSet before the table).
     */
    @Test(timeOut = 180000)
    public void testSyncsWaitForTheOpenTable() throws Exception
    {
        AtomicReference<String> list = new AtomicReference<>("[]");
        inAWindow(list, (rig, ui) ->
        {
            here(rig, "SW Wait", 70);
            list.set(station("SW Wait2", 70));
            int syncs = rig.station.requests("/config/geraet.vrs");
            AtomicInteger owed = new AtomicInteger(), during = new AtomicInteger();

            rig.model.setNetworkCommState(true);
            try (Answering answering = Answering.start(m -> Answering.pressAt(0));
                SyncTable table = SyncTable.answering(SyncTable.answer(), t ->
                {
                    ui.doSync(ui);
                    owed.set(ui.syncAfterAnEdit());
                    during.set(rig.station.requests("/config/geraet.vrs"));
                }))
            {
                SwingUtilities.invokeLater(() -> ui.doSync(ui));
                assertTrue(Components.waitFor(() -> rig.station.requests("/config/geraet.vrs") > syncs, 60000),
                    "the Sync never ran after the table: " + answering.asked());
                assertTrue(syncEnded(ui), "a sync never ended");
                assertTrue(Components.waitFor(() -> answering.asked().contains(I18n.t("ui.infoSyncAlreadyRunning")),
                    30000), "the second Sync was not turned away: " + answering.asked());
                AskingWindows.awaitAnswered();
                Components.settle();

                assertEquals(table.shown().size(), 1, "a second table opened");
                assertEquals(owed.get(), TrainControlUI.SYNC_OWED, "the edit's sync did not wait");
                assertEquals(during.get(), syncs, "a sync ran while the table was open");
                assertTrue(syncEnded(ui), "a sync never ended");
                assertEquals(rig.station.requests("/config/geraet.vrs"), syncs + 1, "not the pressed sync alone");
            }
        });
    }

    /**
     * A row whose locomotive or station value changed while the table was open is left, logged: one deleted here, one
     * the station renamed again, one the station moved again (E64, E91, E93).
     *
     * MUTATION: in `TrainControlUI.apply`, leave out the comparison with the rows read again (`f.stationName`,
     * `f.stationAddress`).
     */
    @Test(timeOut = 180000)
    public void testARowThatChangedMeanwhileIsLeft() throws Exception
    {
        AtomicReference<String> list = new AtomicReference<>("[]");
        inAWindow(list, (rig, ui) ->
        {
            here(rig, "SW Del", 71, "SW Ren", 72, "SW Move", 73);
            list.set(station("SW Del2", 71, "SW Ren2", 72, "SW Move", 74));

            assertNotNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer("SW Ren", "RENAME", "SW Move", "ADDRESS",
                "SW Del", "KEEP"), t ->
                {
                    rig.model.deleteLoc("SW Del");
                    list.set(station("SW Del2", 71, "SW Ren3", 72, "SW Move", 75));
                }), null), "the table did not open");

            List<String> log = rig.takeLog();
            assertNotNull(rig.model.getLocByName("SW Ren"), "a rename the station changed meanwhile was applied");
            assertEquals(rig.model.getLocByName("SW Move").getAddress(), 73, "an address the station changed meanwhile"
                + " was taken");
            for (String name : Arrays.asList("SW Del", "SW Ren", "SW Move"))
                assertTrue(log.contains(I18n.f("loc.syncRowChanged", name)), name + " was not logged as left");
        });
    }

    /**
     * A station lost after the table: the local answers are applied (a Keep, a rename), then the sync fails with
     * today's Retry (E90).
     *
     * MUTATION: in `TrainControlUI.askAndApply`, give up when the second read fails (`return new int[0]` in its catch).
     */
    @Test(timeOut = 180000)
    public void testALostStationAfterTheTableKeepsTheAnswersAndOffersRetry() throws Exception
    {
        AtomicReference<String> list = new AtomicReference<>("[]");
        AtomicBoolean down = new AtomicBoolean();
        inAWindow(list, down, (rig, ui) ->
        {
            here(rig, "SW Lost", 76, "SW LostRen", 77);
            list.set(station("SW Lost2", 76, "SW LostRen2", 77));
            String retry = Pattern.quote(I18n.t("ui.syncFailedRetry")).replace("{0}", "\\E.*\\Q");

            rig.model.setNetworkCommState(true);
            try (Answering answering = Answering.start(m -> m != null && m.matches("(?s)" + retry)
                ? Answering.press(I18n.t("ui.syncFailedCloseButton")) : Answering.pressAt(0));
                SyncTable table = SyncTable.answering(SyncTable.answer("SW Lost", "KEEP", "SW LostRen", "RENAME"),
                    t -> down.set(true)))
            {
                SwingUtilities.invokeLater(() -> ui.doSync(ui));
                assertTrue(Components.waitFor(() -> answering.asked().stream().anyMatch(m -> m.matches("(?s)" + retry)),
                    60000), "no Retry was offered: " + answering.asked());
                assertTrue(syncEnded(ui), "a sync never ended");
                AskingWindows.awaitAnswered();
                assertEquals(table.shown().size(), 1, "precondition: the table did not open");
            }
            finally
            {
                down.set(false);
            }

            assertEquals(rig.model.getLocByName("SW Lost").getDeclinedStationName(), "SW Lost2", "the Keep was lost");
            assertNotNull(rig.model.getLocByName("SW LostRen2"), "the rename was lost");
        });
    }

    private static void check(List<String> problems, Object got, Object expected, String what)
    {
        if (!java.util.Objects.equals(got, expected)) problems.add(what + ": " + got + " not " + expected);
    }

    /** The rows of the Set all claim on these answers, top to bottom, the ignored one last on Ignore. */
    private static List<String> rowsOn(String... answers)
    {
        List<String> rows = Arrays.asList("SW A", "SW B", "SW Both", "SW C", "SW D", "SW E", "SW F", "SW Holder", "SW T");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) out.add(rows.get(i) + ":" + answers[i]);
        out.add("SW I:IGNORE");
        return out;
    }

    private static String text(SyncTable.Shown t, String row, String what)
    {
        AbstractButton b = t.in(row, what, AbstractButton.class);
        return b == null ? null : b.getText();
    }

    private static boolean visible(SyncTable.Shown t, String row, String what)
    {
        return t.in(row, what, JLabel.class).isVisible();
    }

    private static List<String> labels(SyncTable.Shown t)
    {
        List<String> out = new ArrayList<>();
        for (JLabel l : Components.all(t.dialog, JLabel.class, l -> l.getText() != null)) out.add(l.getText());
        return out;
    }

    /** Each row's selected answer, as "row:ANSWER", top to bottom. */
    private static List<String> selected(SyncTable.Shown t)
    {
        List<String> out = new ArrayList<>();
        for (AbstractButton b : Components.all(t.dialog, AbstractButton.class, b -> b.getName() != null
            && b.isSelected())) out.add(b.getName());
        return out;
    }

    /** Adds locomotives here, by name and MM2 address. */
    private static void here(StationRig rig, Object... namesAndAddresses)
    {
        for (int i = 0; i < namesAndAddresses.length; i += 2)
            rig.model.newMM2Locomotive((String) namesAndAddresses[i], (Integer) namesAndAddresses[i + 1]);
        rig.takeLog();
    }

    /** The station's locomotive list: names each followed by an MM2 address. */
    private static String station(Object... entries)
    {
        List<String> records = new ArrayList<>();
        for (int i = 0; i < entries.length; i += 2)
        {
            String name = (String) entries[i];
            records.add("{\"name\":\"" + name + "\",\"internname\":\"" + name + "\",\"uid\":\"0x"
                + Integer.toHexString((Integer) entries[i + 1]) + "\",\"dectyp\":\"mm2_prg\",\"funktionen\":" + FUNCTION
                + "}");
        }
        return "[" + String.join(",", records) + "]";
    }

    /**
     * Presses Sync with a station connected, `table` answering the table, OK to every question, until every sync has
     * ended; the questions' messages go in `said` when given.  The one table shown, or null.
     */
    private static SyncTable.Shown pressSync(StationRig rig, TrainControlUI ui, SyncTable table, List<String> said)
        throws Exception
    {
        rig.model.setNetworkCommState(true);
        int syncs = rig.station.requests("/app/api/locos");
        try (Answering answering = Answering.start(m -> Answering.pressAt(0)); SyncTable t = table)
        {
            SwingUtilities.invokeLater(() -> ui.doSync(ui));
            assertTrue(Components.waitFor(() -> rig.station.requests("/app/api/locos") > syncs, 60000),
                "the Sync never read the station");
            assertTrue(syncEnded(ui), "a sync never ended");
            AskingWindows.awaitAnswered();
            Components.settle();
            if (said != null) said.addAll(answering.asked());
            List<SyncTable.Shown> shown = t.shown();
            assertTrue(shown.size() <= 1, "more than one table opened");
            return shown.isEmpty() ? null : shown.get(0);
        }
    }

    private interface Claim
    {
        void run(StationRig rig, TrainControlUI ui) throws Exception;
    }

    private static void inAWindow(AtomicReference<String> list, Claim claim) throws Exception
    {
        inAWindow(list, new AtomicBoolean(), claim);
    }

    /**
     * The claim in the real window against a stand-in whose locomotive list is `list` as it is when asked, and that
     * drops every connection while `down` is set, once the window's start-up sync has ended.
     */
    private static void inAWindow(AtomicReference<String> list, AtomicBoolean down, Claim claim) throws Exception
    {
        BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("71", null);
        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-sync-window").toFile(), (path, n) ->
        {
            if (down.get()) throw new IllegalStateException("the stand-in is down");
            return "/app/api/locos".equals(path) ? list.get().getBytes(StandardCharsets.UTF_8) : cs3.apply(path, n);
        }, true))
        {
            TrainControlUI ui = (TrainControlUI) rig.model.getGUI();
            try
            {
                assertTrue(syncEnded(ui), "precondition: the window's start-up sync never ended");
                claim.run(rig, ui);
            }
            finally
            {
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }
}
