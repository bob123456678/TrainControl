package ui;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.gui.LocomotiveSettingsFrame;
import org.traincontrol.gui.SyncDifferencesWindow;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.locomotive.LocomotiveSelector;
import org.traincontrol.gui.widgets.ChoiceTable;
import org.traincontrol.gui.widgets.HandPointer;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.SettingsWindow;
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
     * Each row shows this computer's locomotive and the station's in one format, a line for the name ("+N" for a
     * multi-unit head here) and one for the decoder address, the kind in a gray label and the value that differs bold
     * on both sides, the station's decoder type its own (no "MU"); Apply's radio names the whole change to a screen
     * reader, a locomotive differing in both has none, and Keep Both is lit on name rows only.  The columns run This
     * Computer (Keep As Is), Central Station (Apply Change), Keep Both, Ignore, Ask Again.  Rows are in name order
     * with the ignored last on Ignore, the rest on Ask Again, under a heading that counts them, as far in as the
     * table (E17, E18, E20, E26, E29).
     *
     * MUTATION: in `SyncDifferencesWindow.addRow`, select Ask Again on an ignored row too; or in `kindAndValue`, never
     * bold.
     */
    @Test(timeOut = 180000)
    public void testARowNamesTheLocomotiveAndWhatTheStationHas() throws Exception
    {
        inAWindow((rig, ui, list) ->
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
                    null, null), "the by-hand row's Apply");
                check(problems, labels(t).contains("SW Head +1"), true, "head's +N");
                check(problems, Arrays.asList(java.util.Collections.frequency(labels(t), I18n.f("loc.multiUnitLinked",
                    "MM2") + " 26"), labels(t).contains("MM2 26")), Arrays.asList(1, true), "the station's decoder type");
                check(problems, at(t, Components.find(t.dialog, JLabel.class, l -> t.heading.equals(l.getText()))).x,
                    at(t, Components.find(t.dialog, ChoiceTable.class, c -> true)).x, "the heading's indent");
                check(problems, Arrays.asList(t.in("SW Shunter", "BOTH", AbstractButton.class).isEnabled(),
                    t.in("SW Uri", "BOTH", AbstractButton.class).isEnabled()), Arrays.asList(true, false), "Keep Both lit");
                check(problems, labels(t).contains("MM2 20"), true, "decoder address");
                check(problems, Arrays.asList(bold(t, "SW Shunter"), bold(t, "SW V 60"), bold(t, "MM2 21"),
                    bold(t, "MM2 22"), bold(t, "SW Uri"), bold(t, "MM2 20")),
                    Arrays.asList(true, true, true, true, false, false), "bold where the sides differ");
                List<Integer> across = new ArrayList<>();
                for (String key : new String[] {"sync.ui.thisComputer", "sync.ui.centralStation", "sync.ui.keepBoth",
                    "sync.ui.ignore", "sync.ui.askAgain"}) across.add(x(t, key));
                List<Integer> sorted = new ArrayList<>(across);
                java.util.Collections.sort(sorted);
                check(problems, across, sorted, "the columns' order");
                JLabel kind = Components.find(t.dialog, JLabel.class, l -> I18n.t("sync.ui.name").equals(l.getText()));
                check(problems, kind != null && !java.awt.Color.BLACK.equals(kind.getForeground()), true, "gray kind");
            }), null);

            assertNotNull(s, "the table did not open");
            assertTrue(problems.isEmpty(), problems.toString());
        });
    }

    /**
     * Set all, a linked heading clicked, to Apply (Central Station) picks only what needs no human pick: not a grayed,
     * triangle or deleting Apply, not a by-hand row, never an ignored row; Keep As Is (This Computer) and Ask
     * Again override every other row.  The headings are links the keyboard reaches, Enter pressing them.  The triangle
     * follows the other rows' choices and names who else would answer that decoder (E34, E37, E45, E79, E80, E82).
     *
     * MUTATION: in `SyncDifferencesWindow`, Set all to Apply every row with an enabled Apply (drop `applicable`); or in
     * `refresh`, judge the triangle on the row alone.
     */
    @Test(timeOut = 180000)
    public void testSetAllPicksOnlyWhatNeedsNoHumanPickAndTheMarksFollow() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW A", 30, "SW B", 31, "SW T", 33, "SW Holder", 34, "SW C", 35, "SW D", 36, "SW Held", 36,
                "SW E", 37, "SW Both", 38, "SW I", 39, "SW F", 41);
            rig.model.getLocByName("SW I").setSyncIgnored(true);
            // A's address is B's, which the station moves (two there, so B is not renamed); T's is the Holder's, which
            // it renames
            list.set(station("SW A", 31, "SW Z", 31, "SW B", 32, "SW T", 34, "SW C2", 35, "SW Held", 36, "SW E, 1", 37,
                "SW Other", 38, "SW Both", 40, "SW I2", 39, "SW F", 42));

            List<String> problems = new ArrayList<>();
            assertNotNull(pressSync(rig, ui, SyncTable.canceling(t ->
            {
                check(problems, visible(t, "SW A", "collision"), true, "A's triangle with B unmoved");
                check(problems, t.in("SW A", "collision", JLabel.class).getToolTipText(), I18n.f("sync.ui.collision", 31,
                    "SW B"), "A's triangle names B");
                AbstractButton apply = Components.withText(t.dialog, I18n.t("sync.ui.centralStation"));
                check(problems, Arrays.asList(SwingUtilities.getAncestorOfClass(ChoiceTable.class, apply) != null,
                    HandPointer.isHand(apply.getCursor()), apply.isFocusable(), apply.getToolTipText()), Arrays.asList(
                    true, true, true, null), "Apply's heading a link, its (i) saying what a click does");
                // Said only while D's Apply is picked: not on Ask Again, there after Apply, gone on Keep As Is
                check(problems, visible(t, "SW D", "deletes"), false, "D's deleting mark on Ask Again");
                apply.doClick();
                check(problems, selected(t), rowsOn("ADDRESS", "ADDRESS", "RENAME", "ASK", "ASK", "ADDRESS", "ASK",
                    "ASK"), "Set all to Apply");
                t.in("SW D", "RENAME", AbstractButton.class).doClick();
                check(problems, Arrays.asList(visible(t, "SW D", "deletes"), t.in("SW D", "deletes", JLabel.class)
                    .getText()), Arrays.asList(true, I18n.f("sync.ui.deletes", "SW Held", "MM2 36")),
                    "D's deleting mark on Apply");
                t.in("SW D", "KEEP", AbstractButton.class).doClick();
                check(problems, visible(t, "SW D", "deletes"), false, "D's deleting mark on Keep As Is");
                t.in("SW D", "ASK", AbstractButton.class).doClick();
                check(problems, visible(t, "SW T", "collision"), true, "T's triangle");
                t.in("SW B", "ADDRESS", AbstractButton.class).doClick();
                check(problems, visible(t, "SW A", "collision"), false, "A's triangle with B moved");
                t.in("SW B", "KEEP", AbstractButton.class).doClick();
                check(problems, visible(t, "SW A", "collision"), true, "A's triangle once B is kept");
                // Each Set all in turn, over the one before
                for (String[] each : new String[][] {{"sync.ui.thisComputer", "KEEP KEEP KEEP KEEP KEEP KEEP KEEP"
                    + " KEEP"}, {"sync.ui.askAgain", "ASK ASK ASK ASK ASK ASK ASK ASK"}})
                {
                    // Enter on the heading, as the keyboard would
                    AbstractButton head = Components.withText(t.dialog, I18n.t(each[0]));
                    head.getActionMap().get(head.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)))
                        .actionPerformed(new ActionEvent(head, ActionEvent.ACTION_PERFORMED, null));
                    check(problems, selected(t), rowsOn(each[1].split(" ")), "Set all to " + each[0]);
                }
            }), null), "the table did not open");

            assertTrue(problems.isEmpty(), problems.toString());
        });
    }

    /**
     * Cancel stops the whole Sync: nothing renamed, added or forgotten, the sync does not run and says nothing, and one
     * line is logged; closing the window is the same (E83, E84, E87).
     *
     * MUTATION: in `ManualSync.pressedSync`, run the sync after a canceled table (`n = new int[0]`).
     */
    @Test(timeOut = 180000)
    public void testCancelOrClosingStopsTheWholeSync() throws Exception
    {
        inAWindow((rig, ui, list) ->
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
        inAWindow((rig, ui, list) ->
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
            assertEquals(said, Arrays.asList(I18n.t("ui.infoSyncComplete") + "\n\n" + I18n.f(
                "ui.infoSyncLocomotiveAdded", 1) + "\n\n" + String.join("\n", I18n.f("ui.infoSyncRenamed", 1), I18n.f(
                "ui.infoSyncMoved", 1), I18n.f("ui.infoSyncKept", 1), I18n.f("ui.infoSyncIgnored", 1), I18n.f(
                "ui.infoSyncWaiting", 1))), "the finishing message");

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
     * MUTATION: in `ManualSync.pressedSync`, take the flag only for the sync itself (`return syncNow()` in place of
     * `syncHolding()`, without the compareAndSet before the table).
     */
    @Test(timeOut = 180000)
    public void testSyncsWaitForTheOpenTable() throws Exception
    {
        inAWindow((rig, ui, list) ->
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
        inAWindow((rig, ui, list) ->
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
     * MUTATION: in `ManualSync.askAndApply`, give up when the second read fails (`return new int[0]` in its catch).
     */
    @Test(timeOut = 180000)
    public void testALostStationAfterTheTableKeepsTheAnswersAndOffersRetry() throws Exception
    {
        AtomicBoolean down = new AtomicBoolean();
        inAWindow(down, (rig, ui, list) ->
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

    /**
     * The table hangs off the window that asked while it stands, and off the main window once that is gone, never off a
     * closed one (E96).
     *
     * MUTATION: in `ManualSync.askAndApply`, open the table over `asking` whether or not it is showing.
     */
    @Test(timeOut = 180000)
    public void testATableForAClosedWindowHangsOffTheMainWindow() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW Gone", 52);
            list.set(station("SW Gone2", 52));
            JDialog[] asking = new JDialog[1];
            SwingUtilities.invokeAndWait(() ->
            {
                asking[0] = new JDialog(ui, "asking");
                asking[0].setVisible(true);
            });
            try
            {
                SyncTable.Shown open = pressSync(rig, ui, asking[0], SyncTable.canceling(null), null);
                assertNotNull(open, "the table did not open");
                assertTrue(open.dialog.getOwner() == asking[0], "the table does not hang off the window that asked");
                SwingUtilities.invokeAndWait(asking[0]::dispose);
                SyncTable.Shown gone = pressSync(rig, ui, asking[0], SyncTable.canceling(null), null);
                assertNotNull(gone, "the table did not open for a closed window");
                assertTrue(gone.dialog.getOwner() == ui, "the table for a closed window hangs off " + gone.dialog.getOwner());
            }
            finally
            {
                SwingUtilities.invokeAndWait(asking[0]::dispose);
            }
        });
    }

    /**
     * An open Locomotive Settings window follows an Apply on its locomotive, so its Save cannot write the old address
     * back; one holding typing is left as typed, as the Sync does not ask about it (E66).
     *
     * MUTATION: in `ManualSync.pressedSync`, leave out the `followTheSync` call.
     */
    @Test(timeOut = 240000)
    public void testAnOpenSettingsWindowFollowsAnAppliedAddress() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW Open", 53, "SW Typed", 55);
            list.set(station("SW Open", 54, "SW Typed2", 55));
            try
            {
                assertTrue(SettingsWindow.open(ui, rig.model.getLocByName("SW Open"), LocomotiveSettingsFrame.Tab.LOCOMOTIVE,
                    null), "precondition: Locomotive Settings did not open");
                SettingsWindow.awaited();
                assertNotNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer("SW Open", "ADDRESS")), null),
                    "the table did not open");
                assertEquals(rig.model.getLocByName("SW Open").getAddress(), 54, "precondition: the address was not applied");
                assertTrue(Components.waitFor(() -> "54".equals(SettingsWindow.text("settings.address")), 10000),
                    "the open window still shows " + SettingsWindow.text("settings.address"));

                assertTrue(SettingsWindow.open(ui, rig.model.getLocByName("SW Typed"), LocomotiveSettingsFrame.Tab.LOCOMOTIVE,
                    null), "precondition: Locomotive Settings did not open");
                SettingsWindow.awaited();
                SettingsWindow.type("settings.name", "SW Mine");
                assertNotNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer("SW Typed", "RENAME")), null),
                    "the table did not open");
                assertNotNull(rig.model.getLocByName("SW Typed2"), "precondition: the rename was not applied");
                Components.settle();
                assertEquals(SettingsWindow.text("settings.name"), "SW Mine", "the typing was thrown away");
            }
            finally
            {
                SettingsWindow.cancelDiscarding();
            }
        });
    }

    /**
     * A thousand rows open in a bounded time inside the screen: the list scrolls, and the buttons stay in view (E129),
     * as do the headings at the end of the list.
     *
     * MUTATION: in `ChoiceTable`, leave out `getPreferredSize`, and the window grows past the screen.
     */
    @Test(timeOut = 180000)
    public void testAThousandRowsScrollInsideTheScreen() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            RenameProposals many = made(rig, 1000, false, "");
            List<String> problems = new ArrayList<>();
            long started = System.nanoTime();
            AtomicReference<Long> opened = new AtomicReference<>();
            showing(ui, many, t ->
            {
                opened.set(System.nanoTime() - started);
                Rectangle screen = t.dialog.getGraphicsConfiguration().getBounds();
                JScrollPane scrolling = Components.find(t.dialog, ChoiceTable.class, c -> true);
                check(problems, screen.contains(t.dialog.getBounds()), true, "window inside the screen");
                check(problems, scrolling.getVerticalScrollBar().isVisible(), true, "the list scrolls");
                for (Component c : new Component[] {Components.withText(t.dialog, I18n.t("sync.ui.continue")),
                    Components.withText(t.dialog, I18n.t("ui.cancel"))})
                {
                    check(problems, new Rectangle(t.dialog.getContentPane().getSize()).contains(SwingUtilities
                        .convertRectangle(c.getParent(), c.getBounds(), t.dialog.getContentPane())), true, "buttons in view");
                }
                scrolling.getVerticalScrollBar().setValue(scrolling.getVerticalScrollBar().getMaximum());
                Component head = heading(t, "sync.ui.askAgain");
                check(problems, new Rectangle(scrolling.getSize()).contains(SwingUtilities.convertRectangle(
                    head.getParent(), head.getBounds(), scrolling)), true, "the headings in view at the end");
            });
            assertTrue(opened.get() < 20_000_000_000L, "a thousand rows took " + opened.get() / 1_000_000 + " ms to open");
            assertTrue(problems.isEmpty(), problems.toString());
        });
    }

    /**
     * Set all on a thousand rows answers at once: a heading sets every row it can and judges the marks once, not once a
     * row, so a click takes well under the two seconds allowed here, where it took 6 to 22 (E130).
     *
     * MUTATION: in `SyncDifferencesWindow.setAllTo`, judge `applicable` once for each row, and refresh the marks on
     * every radio's change (leave out `settingAll`).
     */
    @Test(timeOut = 240000)
    public void testSetAllOnAThousandRowsAnswersAtOnce() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            List<Long> took = new java.util.concurrent.CopyOnWriteArrayList<>();
            AtomicInteger moved = new AtomicInteger();
            showing(ui, made(rig, 1000, true, ""), t ->
            {
                for (String key : new String[] {"sync.ui.centralStation", "sync.ui.thisComputer", "sync.ui.askAgain"})
                {
                    long started = System.nanoTime();
                    Components.withText(t.dialog, I18n.t(key)).doClick(0);
                    took.add((System.nanoTime() - started) / 1_000_000);
                    if (moved.get() == 0)
                        moved.set((int) selected(t).stream().filter(s -> s.endsWith(":ADDRESS")).count());
                }
            });
            assertEquals(took.size(), 3, "the headings were not all clicked");
            assertEquals(moved.get(), 1000, "Set all to Apply did not take every row's address");
            assertTrue(took.stream().allMatch(ms -> ms < 2000), "Set all on a thousand rows took (ms) " + took);
        });
    }

    /**
     * Set all to Apply judges against the addresses already picked by hand: an ignored row, which Set all leaves alone,
     * set by hand to the address the station lists twice, keeps Set all from giving that address to the other row.
     *
     * MUTATION: in `SyncDifferencesWindow.setAllTo`, judge `applicable` against no picks (`Collections.emptyList()`).
     */
    @Test(timeOut = 180000)
    public void testSetAllApplyHonorsAnAddressPickedByHand() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW X", 50, "SW Y", 51);
            rig.model.getLocByName("SW X").setSyncIgnored(true);
            // The station has both X and Y at 52
            list.set(station("SW X", 52, "SW Y", 52));

            List<String> problems = new ArrayList<>();
            assertNotNull(pressSync(rig, ui, SyncTable.canceling(t ->
            {
                AbstractButton apply = Components.withText(t.dialog, I18n.t("sync.ui.centralStation"));
                apply.doClick();
                check(problems, selected(t), Arrays.asList("SW Y:ADDRESS", "SW X:IGNORE"), "Set all, nothing picked");
                Components.withText(t.dialog, I18n.t("sync.ui.askAgain")).doClick();
                t.in("SW X", "ADDRESS", AbstractButton.class).doClick();
                apply.doClick();
                check(problems, selected(t), Arrays.asList("SW Y:ASK", "SW X:ADDRESS"), "Set all after X's by hand");
                check(problems, visible(t, "SW X", "collision"), false, "X's triangle");
            }), null), "the table did not open");
            assertEquals(problems, new ArrayList<String>(), "Set all over a pick by hand");
        });
    }

    /**
     * Apply renames in the safe order, so a chain asks no delete question, and a rename onto a held name goes through
     * the delete question: its mark counts the multi-unit it deletes, Yes deletes it and renames, and the deleted
     * locomotive's own row is left with a line saying an earlier answer took it, not counted as still to answer (E39,
     * E49).
     *
     * MUTATION: in `TrainControlUI.apply`, leave out the sort, so P's rename deletes Q; or log every row left as
     * changed meanwhile (leave out `loc.syncRowAnsweredAway`).
     */
    @Test(timeOut = 180000)
    public void testAppliedRenamesFollowTheChainAndTheDeleteQuestion() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW Q", 81, "SW D", 82, "SW Held", 83, "SW Member", 84);
            rig.model.newDCCLocomotive("SW P", 80);
            rig.model.getLocByName("SW Held").setLinkedLocomotives(
                java.util.Collections.singletonMap("SW Member", 1.0));
            Locomotive p = rig.model.getLocByName("SW P"), q = rig.model.getLocByName("SW Q"),
                d = rig.model.getLocByName("SW D"), held = rig.model.getLocByName("SW Held");
            // The station renamed P to Q, Q to R and D to Held; P is DCC, so Q has no other address of its own there
            list.set(station("SW Q", MarklinLocomotive.DCC_BASE + 80, "SW R", 81, "SW Held", 82, "SW Member", 84));

            List<String> problems = new ArrayList<>(), said = new ArrayList<>();
            assertNotNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer("SW P", "RENAME", "SW Q", "RENAME",
                "SW D", "RENAME", "SW Held", "KEEP"), t ->
                {
                    check(problems, visible(t, "SW D", "deletes"), false, "D's mark before its Apply");
                    t.in("SW D", "RENAME", AbstractButton.class).doClick();
                    check(problems, t.in("SW D", "deletes", JLabel.class).getText(), I18n.f("sync.ui.deletes",
                        "SW Held +1", "MM2 83"), "D's mark");
                }), said), "the table did not open");

            List<String> log = rig.takeLog();
            assertEquals(Arrays.asList(p.getName(), q.getName(), d.getName()), Arrays.asList("SW Q", "SW R", "SW Held"),
                "the renames: " + said);
            assertFalse(rig.model.getLocomotives().contains(held), "Yes to the delete question did not delete SW Held");
            assertTrue(log.contains(I18n.f("loc.syncRowAnsweredAway", "SW Held"))
                && !log.contains(I18n.f("loc.syncRowChanged", "SW Held")), "SW Held's row: " + log);
            assertTrue(!said.isEmpty() && said.get(said.size() - 1).endsWith("\n\n" + I18n.f("ui.infoSyncRenamed",
                3)), "the finishing message: " + said);
            assertTrue(problems.isEmpty(), problems.toString());
        });
    }

    /**
     * The list shows scroll bars wherever it does not fit: a row wider than the screen opens inside it, scrolling
     * sideways; in a window resized smaller both bars show, and the list follows a larger window.
     *
     * MUTATION: in `ChoiceTable.getPreferredSize`, leave out the cap on the width, and the window opens wider than the
     * screen.
     */
    @Test(timeOut = 180000)
    public void testTheListScrollsWhereverItDoesNotFit() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            List<String> problems = new ArrayList<>();
            showing(ui, made(rig, 10, false, String.join(" ", java.util.Collections.nCopies(150, "Wide"))), t ->
            {
                Rectangle screen = t.dialog.getGraphicsConfiguration().getBounds();
                JScrollPane scrolling = Components.find(t.dialog, ChoiceTable.class, c -> true);
                check(problems, Arrays.asList(screen.contains(t.dialog.getBounds()),
                    scrolling.getHorizontalScrollBar().isVisible(), t.dialog.isResizable()),
                    Arrays.asList(true, true, true), "a wide row: inside the screen, scrolling sideways, resizable");
                t.dialog.setSize(420, 260);
                t.dialog.validate();
                check(problems, Arrays.asList(scrolling.getHorizontalScrollBar().isVisible(),
                    scrolling.getVerticalScrollBar().isVisible()), Arrays.asList(true, true),
                    "a small window's scroll bars");
                int small = scrolling.getWidth();
                t.dialog.setSize(820, 600);
                t.dialog.validate();
                check(problems, scrolling.getWidth() - small, 400, "the list's width following the window's");
            });
            assertTrue(problems.isEmpty(), problems.toString());
        });
    }

    /**
     * An mfx locomotive the station has under the same name at another address starts on Apply Change; not a DCC one,
     * not an ignored one (on Ignore, last), and not one whose address another locomotive here keeps (Ask Again, with
     * its triangle).
     *
     * MUTATION: in `SyncDifferencesWindow`, leave out the mfx preselection; or preselect without `applicable`.
     */
    @Test(timeOut = 180000)
    public void testAnMfxAddressStartsOnApplyWhereSetAllWould() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            for (Object[] l : new Object[][] {{"SW Mfx", 40}, {"SW Ign", 42}, {"SW Col", 46}, {"SW Holder", 47}})
                rig.model.newMFXLocomotive((String) l[0], (Integer) l[1]);
            rig.model.newDCCLocomotive("SW Dcc", 44);
            rig.model.getLocByName("SW Ign").setSyncIgnored(true);
            int mfx = MarklinLocomotive.MFX_BASE;
            // The Holder keeps 47, which the station gives Col
            list.set(station("SW Mfx", mfx + 41, "SW Ign", mfx + 43, "SW Col", mfx + 47, "SW Dcc",
                MarklinLocomotive.DCC_BASE + 45));

            List<String> problems = new ArrayList<>();
            SyncTable.Shown s = pressSync(rig, ui, SyncTable.canceling(t -> check(problems, visible(t, "SW Col",
                "collision"), true, "Col's triangle")), null);
            assertNotNull(s, "the table did not open");
            assertEquals(s.rows, Arrays.asList("SW Col", "SW Dcc", "SW Holder", "SW Mfx", "SW Ign"), "the rows");
            assertEquals(s.startsOn.toString(),
                "{SW Col=ASK, SW Dcc=ASK, SW Holder=ASK, SW Mfx=ADDRESS, SW Ign=IGNORE}", "what each row starts on");
            assertEquals(problems, new ArrayList<String>(), "the marks");
        });
    }

    /**
     * Only a press asks: a route edit's sync, the owed sync and the model's own sync, with the station differing on a
     * name and an address, open no table and change neither, while the Locomotive Selector's Sync opens the table as
     * the menu's does.
     *
     * MUTATION: in `TrainControlUI.doSync`, ask on every sync (`pressedSync(c != null ? c : this, answered)`); or in
     * `LocomotiveSelector.SyncWithCSActionPerformed`, sync without asking (`doSync(null)`).
     */
    @Test(timeOut = 240000)
    public void testOnlyAPressedSyncAsks() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW Auto", 90, "SW Far", 91);
            list.set(station("SW Auto2", 90, "SW Far", 92));
            rig.model.setNetworkCommState(true);
            try (SyncTable watching = SyncTable.answering(SyncTable.answer()))
            {
                // As a route edit syncs after its write, off the event thread
                assertTrue(ui.syncAfterAnEdit() >= 0, "precondition: the route edit's sync failed");
                ((AtomicBoolean) support.Reflect.field(TrainControlUI.class, "syncOwed").get(ui)).set(true);
                int syncs = rig.station.requests("/config/geraet.vrs");
                SwingUtilities.invokeLater(() -> ui.doSync(null));
                assertTrue(Components.waitFor(() -> rig.station.requests("/config/geraet.vrs") > syncs, 60000),
                    "the owed sync never ran");
                assertTrue(syncEnded(ui), "the owed sync never ended");
                assertTrue(rig.model.syncWithCS2() >= 0, "precondition: the model's own sync failed");
                Components.settle();
                assertEquals(watching.shown().size(), 0, "a sync nobody pressed opened the table");
            }
            assertEquals(Arrays.asList(rig.model.getLocByName("SW Auto") != null, rig.model.getLocByName("SW Auto2"),
                rig.model.getLocByName("SW Far").getAddress()), Arrays.asList(true, null, 91),
                "a sync nobody pressed changed a name or an address");

            try (Answering answering = Answering.start(m -> Answering.pressAt(0));
                SyncTable table = SyncTable.canceling(null))
            {
                AbstractButton sync = (AbstractButton) support.Reflect.get(LocomotiveSelector.class,
                    ui.getLocSelector(), "SyncWithCS");
                SwingUtilities.invokeLater(sync::doClick);
                assertTrue(Components.waitFor(() -> !table.shown().isEmpty(), 60000),
                    "the Locomotive Selector's Sync opened no table");
                assertTrue(syncEnded(ui), "the pressed sync never ended");
                AskingWindows.awaitAnswered();
                assertEquals(table.shown().get(0).rows, Arrays.asList("SW Auto", "SW Far"), "the selector's table");
            }
        });
    }

    /**
     * Keep As Is is the radio centered beside this computer's lines, Apply the one beside the station's, exclusive with
     * the row's other answers; each radio says its whole answer on hover and to a screen reader, and Keep Both's,
     * Ignore's and Ask Again's sit centered under their headings.  This Computer, Central Station and Ask Again head
     * the columns as links, Keep Both and Ignore as plain labels.
     *
     * MUTATION: in `SyncDifferencesWindow.addRow`, lead a single Apply's Name line with its radio, or put Use the
     * address in a group of its own.
     */
    @Test(timeOut = 180000)
    public void testTheRadiosSitBesideTheValuesTheyChoose() throws Exception
    {
        List<String> problems = new ArrayList<>();
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW Shunter", 20, "SW Uri", 21);
            list.set(station("SW V 60", 20, "SW Uri", 22));
            assertNotNull(pressSync(rig, ui, SyncTable.canceling(t ->
            {
                int computer = x(t, "sync.ui.thisComputer"), station = x(t, "sync.ui.centralStation");
                for (String row : new String[] {"SW Shunter", "SW Uri"})
                {
                    Rectangle keep = at(t, t.in(row, "KEEP", AbstractButton.class));
                    check(problems, keep.x >= computer && keep.x + keep.width < station, true, row + "'s Keep under "
                        + "This Computer");
                }
                // Each radio left of its values (text#occurrence), level with their middle
                for (String[] each : new String[][] {{"SW Shunter", "KEEP", "SW Shunter#0", "MM2 20#0"}, {"SW Shunter",
                    "RENAME", "SW V 60#0", "MM2 20#1"}, {"SW Uri", "KEEP", "SW Uri#0", "MM2 21#0"}, {"SW Uri", "ADDRESS",
                    "SW Uri#1", "MM2 22#0"}})
                {
                    Rectangle b = at(t, t.in(each[0], each[1], AbstractButton.class));
                    List<Rectangle> v = new ArrayList<>();
                    for (int i = 2; i < each.length; i++)
                    {
                        String[] value = each[i].split("#");
                        v.add(at(t, Components.all(t.dialog, JLabel.class, l -> value[0].equals(l.getText())).get(
                            Integer.parseInt(value[1]))));
                    }
                    double middle = (v.get(0).getCenterY() + v.get(v.size() - 1).getCenterY()) / 2;
                    check(problems, Arrays.asList(b.x + b.width <= v.get(0).x, Math.abs(b.getCenterY() - middle) <= 1),
                        Arrays.asList(true, true), each[0] + ":" + each[1] + " beside " + Arrays.toString(each));
                    if (!"KEEP".equals(each[1])) check(problems, b.x >= station, true, each[0] + ":" + each[1]
                        + " under Central Station");
                }
                check(problems, Arrays.asList(t.in("SW Shunter", "KEEP", AbstractButton.class).getToolTipText(),
                    t.in("SW Shunter", "KEEP", AbstractButton.class).getAccessibleContext().getAccessibleName(),
                    t.in("SW Uri", "ADDRESS", AbstractButton.class).getToolTipText()), Arrays.asList(
                    I18n.f("sync.ui.keepAsIs", "SW Shunter"), I18n.f("sync.ui.keepAsIs", "SW Shunter"),
                    I18n.f("sync.ui.changeAddress", "SW Uri", 22)), "what the radios say");
                for (String[] each : new String[][] {{"BOTH", "sync.ui.keepBoth"}, {"IGNORE", "sync.ui.ignore"},
                    {"ASK", "sync.ui.askAgain"}})
                {
                    double under = at(t, heading(t, each[1]).getParent()).getCenterX();
                    for (String row : new String[] {"SW Shunter", "SW Uri"})
                    {
                        Rectangle r = at(t, t.in(row, each[0], AbstractButton.class));
                        check(problems, Math.abs(r.getCenterX() - under) <= 1 && Math.abs(r.getCenterY() - at(t, t.in(
                            row, "KEEP", AbstractButton.class)).getCenterY()) <= 1, true, row + ":" + each[0]
                            + " centered under its heading and in its row");
                    }
                }
                List<String> says = new ArrayList<>();
                for (String a : new String[] {"BOTH", "IGNORE", "ASK"})
                {
                    AbstractButton b = t.in("SW Shunter", a, AbstractButton.class);
                    says.addAll(Arrays.asList(b.getToolTipText(), b.getAccessibleContext().getAccessibleName()));
                }
                String both = I18n.f("sync.ui.keepBothSaid", "SW Shunter", "SW V 60"), ignore = I18n.f(
                    "sync.ui.ignoreSaid", "SW Shunter"), ask = I18n.f("sync.ui.askAgainSaid", "SW Shunter");
                check(problems, says, Arrays.asList(both, both, ignore, ignore, ask, ask), "what the other radios say");
                for (String each : new String[] {"ADDRESS", "KEEP", "IGNORE"})
                {
                    t.in("SW Uri", each, AbstractButton.class).doClick();
                    check(problems, selected(t).stream().filter(s -> s.startsWith("SW Uri:")).collect(java.util.stream
                        .Collectors.toList()), Arrays.asList("SW Uri:" + each), "one answer a row");
                }
                // Every heading is a link with an (i) after it saying what a click does
                List<Boolean> links = new ArrayList<>();
                for (String key : new String[] {"sync.ui.thisComputer", "sync.ui.centralStation", "sync.ui.keepBoth",
                    "sync.ui.ignore", "sync.ui.askAgain"})
                {
                    AbstractButton link = Components.withText(t.dialog, I18n.t(key));
                    JLabel mark = link == null ? null : (JLabel) link.getParent().getComponent(2);
                    links.add(mark != null && mark.getToolTipText() != null && HandPointer.isHand(link.getCursor()));
                }
                check(problems, links, Arrays.asList(true, true, true, true, true), "the linked headings and their (i)");
                ((AbstractButton) heading(t, "sync.ui.askAgain")).doClick();
                ((AbstractButton) heading(t, "sync.ui.ignore")).doClick();
                check(problems, selected(t), Arrays.asList("SW Shunter:IGNORE", "SW Uri:IGNORE"), "Ignore's heading");
            }), null), "the table did not open");
        });
        assertTrue(problems.isEmpty(), problems.toString());
    }

    /**
     * Rows alternate: every other one a very light step off the table's background, a hairline rule between rows, and
     * a stronger rule under the headings.
     *
     * MUTATION: in `ChoiceTable.paintRows`, leave out the fill; or leave out the rule.
     */
    @Test(timeOut = 180000)
    public void testTheRowsAlternateWithARuleBetween() throws Exception
    {
        List<String> problems = new ArrayList<>();
        inAWindow((rig, ui, list) -> showing(ui, made(rig, 4, false, ""), t ->
            {
                ChoiceTable table = Components.find(t.dialog, ChoiceTable.class, c -> true);
                java.awt.Container grid = (java.awt.Container) ((java.awt.Container) table.getViewport().getView())
                    .getComponent(0);
                Component top = table.getColumnHeader().getView();
                java.awt.image.BufferedImage img = image(grid), heads = image(top);
                // Down the right edge, clear of every cell: from the headings to their foot, and the middle of each row
                int edge = grid.getWidth() - 3;
                Component head = heading(t, "sync.ui.askAgain");
                List<Integer> middles = new ArrayList<>();
                for (int i = 1; i <= 4; i++)
                {
                    AbstractButton keep = t.in("SW " + i, "KEEP", AbstractButton.class);
                    middles.add((int) SwingUtilities.convertRectangle(keep.getParent(), keep.getBounds(), grid)
                        .getCenterY());
                }
                List<Integer> rows = new ArrayList<>(), darkest = new ArrayList<>(Arrays.asList(darkest(heads,
                    top.getWidth() - 3, SwingUtilities.convertRectangle(head.getParent(), head.getBounds(), top).y,
                    top.getHeight())));
                for (int i = 0; i < 4; i++) rows.add(img.getRGB(edge, middles.get(i)) & 0xffffff);
                for (int i = 0; i < 3; i++) darkest.add(darkest(img, edge, middles.get(i), middles.get(i + 1)));
                int bg = grid.getBackground().getRGB() & 0xffffff, stripe = rows.get(1);
                check(problems, Arrays.asList(rows.get(0) == bg, stripe != bg, rows.get(2) == bg, rows.get(3) == stripe,
                    (stripe & 0xff) >= 235), Arrays.asList(true, true, true, true, true), "the stripes " + rows);
                check(problems, Arrays.asList(darkest.get(1) < (stripe & 0xff), darkest.get(2) < (stripe & 0xff),
                    darkest.get(3) < (stripe & 0xff), darkest.get(0) < darkest.get(1)), Arrays.asList(true, true, true,
                    true), "the rules' darkest " + darkest);
            }));
        assertTrue(problems.isEmpty(), problems.toString());
    }

    /**
     * Rows whose Apply is grayed (a name swap across decoder types, a name the station lists twice, a moving
     * locomotive) leave the table whole: every answer column is on screen without scrolling sideways and the last row
     * shows in full, each reason a short gray mark saying it in full on hover, as the grayed radio does.  A row too
     * wide for the screen scrolls sideways under a bar that hides none of the rows.
     *
     * MUTATION: in `SyncDifferencesWindow.line`, show `whyNot` itself in place of the mark; or in
     * `ChoiceTable.getPreferredSize`, leave out the horizontal bar's height.
     */
    @Test(timeOut = 240000)
    public void testGrayedRowsKeepTheTableWhole() throws Exception
    {
        List<String> problems = new ArrayList<>();
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW A", 20, "SW Moving", 24, "SW Twice", 22);
            rig.model.newDCCLocomotive("SW B", 21);
            list.set(station("SW B", 20, "SW A", MarklinLocomotive.DCC_BASE + 21, "SW Mover", 24, "SW Dup", 22,
                "SW Dup", 26));
            Locomotive moving = rig.model.getLocByName("SW Moving");
            moving.setSpeed(40);
            try
            {
                assertNotNull(pressSync(rig, ui, SyncTable.canceling(t ->
                {
                    fitsWhole(problems, t);
                    List<String> why = new ArrayList<>(), marks = new ArrayList<>();
                    for (AbstractButton b : Components.all(t.dialog, AbstractButton.class, b -> !b.isEnabled()
                        && b.getName() != null && b.getName().matches(".*:(RENAME|ADDRESS)"))) why.add(b.getToolTipText());
                    for (JLabel l : Components.all(t.dialog, JLabel.class, l -> I18n.t("sync.ui.cantApply").equals(
                        l.getText()))) marks.add(l.getToolTipText());
                    check(problems, why.size(), 4, "the grayed Applies " + why);
                    check(problems, marks, why, "each grayed Apply's mark");
                }), null), "the table did not open");
            }
            finally
            {
                moving.setSpeed(0);
            }
            showing(ui, made(rig, 3, false, String.join(" ", java.util.Collections.nCopies(150, "Wide"))), t ->
            {
                JScrollPane scrolling = Components.find(t.dialog, ChoiceTable.class, c -> true);
                check(problems, Arrays.asList(scrolling.getHorizontalScrollBar().isVisible(),
                    scrolling.getVerticalScrollBar().isVisible()), Arrays.asList(true, false),
                    "a wide row: scrolling sideways, every row in view");
            });
        });
        assertTrue(problems.isEmpty(), problems.toString());
    }

    /**
     * A locomotive two station locomotives answer to (here A and B swapped; X's name reused at 23, X at 25 there) is a
     * row with no control, saying to fix it by hand and naming both with their addresses, a red triangle after them
     * saying why.  No heading picks for it, Continue Sync changes neither, the log names it and both once, and the
     * closing message counts it as to fix by hand, while a one-to-one rename beside it applies.
     *
     * MUTATION: in the `RenameProposals.Row` constructor, set `byHand` false, and the rows offer Apply again; or drop
     * the gaps in `SyncDifferencesWindow.addRow`, and the by-hand rows' blocks start flush left; or paint its triangle
     * yellow (`warning(16, false)`).
     */
    @Test(timeOut = 180000)
    public void testARowThatDiffersTwoWaysIsLeftToFixByHand() throws Exception
    {
        List<String> problems = new ArrayList<>(), said = new ArrayList<>();
        inAWindow((rig, ui, list) ->
        {
            here(rig, "SW A", 20, "SW B", 21, "SW X", 23, "SW Shunter", 27);
            list.set(station("SW B", 20, "SW A", 21, "SW Y", 23, "SW X", 25, "SW V 60", 27));
            // Each row, the station's locomotive at its address, and where the station has it by name
            String[][] rows = {{"SW A", "SW B", "MM2 20", "MM2 21"}, {"SW B", "SW A", "MM2 21", "MM2 20"},
                {"SW X", "SW Y", "MM2 23", "MM2 25"}};
            assertNotNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer("SW Shunter", "RENAME"), t ->
            {
                check(problems, t.rows, Arrays.asList("SW A", "SW B", "SW Shunter", "SW X"), "the rows");
                ((AbstractButton) heading(t, "sync.ui.centralStation")).doClick();
                check(problems, selected(t), Arrays.asList("SW Shunter:RENAME"), "Set all to Apply");
                for (String[] each : rows)
                {
                    check(problems, Components.all(t.dialog, javax.swing.JComponent.class, c -> c.getName() != null
                        && c.getName().startsWith(each[0] + ":") && !c.getName().endsWith(":byHand")).size(), 0,
                        each[0] + "'s controls");
                    check(problems, t.in(each[0], "byHand", JLabel.class).getText(), I18n.f("sync.ui.byHandWhere",
                        each[1], each[2], each[0], each[3]), each[0] + "'s station locomotives");
                }
                check(problems, java.util.Collections.frequency(labels(t), I18n.t("sync.ui.byHand")), 3, "fix by hand");
                JLabel x = t.in("SW X", "byHand", JLabel.class);
                check(problems, Arrays.asList(x.getHorizontalTextPosition(), x.getToolTipText(), red(x)), Arrays.asList(
                    JLabel.LEADING, I18n.t("sync.ui.byHandTip"), true), "X's red triangle after its text");
                // The by-hand rows' blocks start where the other row's radios leave room: two Name columns, no more
                java.util.Set<Integer> names = new java.util.TreeSet<>();
                for (JLabel l : Components.all(t.dialog, JLabel.class, l -> I18n.t("sync.ui.name").equals(l.getText())))
                    names.add(at(t, l).x);
                check(problems, names.size(), 2, "Name labels' columns " + names);
                check(problems, at(t, Components.find(t.dialog, JLabel.class, l -> I18n.t("sync.ui.byHand").equals(
                    l.getText()))).x, java.util.Collections.max(names), "the by-hand text under the station's Name");
            }), said), "the table did not open");
            List<String> log = rig.takeLog();
            for (String[] each : rows)
            {
                Locomotive l = rig.model.getLocByName(each[0]);
                check(problems, l == null ? null : l.getDecoderTypeLabel() + " " + l.getAddress(), each[2], each[0]
                    + " changed");
                check(problems, java.util.Collections.frequency(log, I18n.f("loc.syncByHand", each[0], each[1],
                    Integer.valueOf(each[2].substring(4)), Integer.valueOf(each[3].substring(4)))), 1, each[0]
                    + "'s log line " + log);
            }
            check(problems, Arrays.asList(rig.model.getLocByName("SW Y"), rig.model.getLocByName("SW Shunter"),
                rig.model.getLocByName("SW V 60") != null), Arrays.asList(null, null, true), "Y added, or no rename");
            check(problems, said, Arrays.asList(I18n.t("ui.infoSyncComplete") + "\n\n" + I18n.f("ui.infoSyncRenamed",
                1) + "\n" + I18n.f("ui.infoSyncByHand", 3)), "the closing message");
        });
        assertTrue(problems.isEmpty(), problems.toString());
    }

    /**
     * Keep Both shows on every row, grayed and never picked where it cannot apply, its hover and screen reader saying
     * why: a row differing by address only, or a name another locomotive here holds.  No heading's Set all picks it.
     *
     * MUTATION: in `SyncDifferencesWindow.addRow`, give Keep Both no reason (null), and it is lit on every row.
     */
    @Test(timeOut = 240000)
    public void testKeepBothIsGrayedWhereItCannotApply() throws Exception
    {
        List<String> problems = new ArrayList<>();
        inAWindow((rig, ui, list) ->
        {
            // The station renames Shunter, moves Uri, and renames D onto Held, which holds that name here
            list.set(station("SW V 60", 20, "SW Uri", 22, "SW Held", 36));
            here(rig, "SW Shunter", 20, "SW Uri", 21, "SW D", 36, "SW Held", 36);
            assertNotNull(pressSync(rig, ui, SyncTable.canceling(t ->
            {
                String lit = I18n.f("sync.ui.keepBothSaid", "SW Shunter", "SW V 60"), address = I18n.t(
                    "sync.ui.bothOnlyRenamed"), held = I18n.f("sync.ui.bothNameTaken", "SW Held");
                check(problems, Arrays.asList(both(t, "SW Shunter"), both(t, "SW Uri"), both(t, "SW D")), Arrays.asList(
                    Arrays.asList(true, lit, lit), Arrays.asList(false, address, address), Arrays.asList(false, held,
                    held)), "Keep Both on a name row, an address row and a name held here");
                List<Long> picked = new ArrayList<>();
                for (String row : new String[] {"SW Uri", "SW D"}) t.in(row, "BOTH", AbstractButton.class).doClick();
                picked.add(selected(t).stream().filter(n -> n.endsWith(":BOTH")).count());
                for (String key : new String[] {"sync.ui.thisComputer", "sync.ui.centralStation", "sync.ui.askAgain"})
                {
                    ((AbstractButton) heading(t, key)).doClick();
                    picked.add(selected(t).stream().filter(n -> n.endsWith(":BOTH")).count());
                }
                check(problems, picked, Arrays.asList(0L, 0L, 0L, 0L), "Keep Both picked by a click or a Set all");
                ((AbstractButton) heading(t, "sync.ui.keepBoth")).doClick();
                check(problems, selected(t).stream().filter(n -> n.endsWith(":BOTH")).collect(java.util.stream
                    .Collectors.toList()), Arrays.asList("SW Shunter:BOTH"), "Keep Both's heading, only where lit");
            }), null), "the table did not open");
        });
        assertTrue(problems.isEmpty(), problems.toString());
    }

    /**
     * The finishing message says only what happened: with nothing different and nothing new, that everything matches;
     * otherwise what was added, by kind, and only the answers that are not zero; a table of by-hand rows alone counts
     * them, never saying everything matches.
     *
     * MUTATION: in `ManualSync.syncSummary`, list every answer, zero or not; or leave out the everything-matches line;
     * or in `ManualSync.apply`, count no by-hand rows.
     */
    @Test(timeOut = 240000)
    public void testTheFinishingMessageSaysOnlyWhatHappened() throws Exception
    {
        inAWindow((rig, ui, list) ->
        {
            list.set(station("SW Same", 70));
            here(rig, "SW Same", 70, "SW Old", 71);
            List<String> said = new ArrayList<>();
            assertNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer()), said), "a table opened");
            list.set(station("SW Same", 70, "SW New", 71, "SW Added", 72));
            assertNotNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer("SW Old", "RENAME")), said),
                "the rename's table did not open");
            // X's name reused at its address, X itself at another: a by-hand row alone
            here(rig, "SW X", 73);
            list.set(station("SW Same", 70, "SW New", 71, "SW Added", 72, "SW Y", 73, "SW X", 75));
            assertNotNull(pressSync(rig, ui, SyncTable.answering(SyncTable.answer()), said), "X's table did not open");
            assertEquals(said, Arrays.asList(I18n.t("ui.infoSyncNothingDiffers"), I18n.t("ui.infoSyncComplete") + "\n\n"
                + I18n.f("ui.infoSyncLocomotiveAdded", 1) + "\n\n" + I18n.f("ui.infoSyncRenamed", 1), I18n.t(
                "ui.infoSyncComplete") + "\n\n" + I18n.f("ui.infoSyncByHand", 1)), "the messages");
        });
    }

    /** Whether l's icon paints a strongly red pixel. */
    private static boolean red(JLabel l)
    {
        javax.swing.Icon icon = l.getIcon();
        if (icon == null) return false;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
            java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        icon.paintIcon(l, g, 0, 0);
        g.dispose();
        for (int x = 0; x < img.getWidth(); x++)
            for (int y = 0; y < img.getHeight(); y++)
            {
                java.awt.Color c = new java.awt.Color(img.getRGB(x, y), true);
                if (c.getAlpha() > 200 && c.getRed() > 180 && c.getGreen() < 80 && c.getBlue() < 80) return true;
            }
        return false;
    }

    /** c as it paints. */
    private static java.awt.image.BufferedImage image(Component c)
    {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(c.getWidth(), c.getHeight(),
            java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        c.printAll(g);
        g.dispose();
        return img;
    }

    /** The darkest blue level down column x of img, from `from` to just above `to`. */
    private static int darkest(java.awt.image.BufferedImage img, int x, int from, int to)
    {
        int dark = 255;
        for (int y = from; y < to; y++) dark = Math.min(dark, img.getRGB(x, y) & 0xff);
        return dark;
    }

    /** Where c is, in the dialog's coordinates. */
    private static Rectangle at(SyncTable.Shown t, Component c)
    {
        return SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), t.dialog);
    }

    /** n DCC locomotives here, the first's name ending in `tail`, each differing by name, or by a free address. */
    private static RenameProposals made(StationRig rig, int n, boolean addressed, String tail)
    {
        List<RenameProposals.Row> rows = new ArrayList<>();
        java.util.Map<Integer, List<MarklinLocomotive>> held = new java.util.HashMap<>();
        for (int i = 1; i <= n; i++)
        {
            MarklinLocomotive l = new MarklinLocomotive(rig.model, i, Locomotive.decoderType.DCC, "SW " + i
                + (i == 1 ? tail : ""));
            MarklinLocomotive at = new MarklinLocomotive(rig.model, i + 5000, Locomotive.decoderType.DCC, "SW " + i);
            held.put(l.getIntUID(), new ArrayList<>(Arrays.asList(l)));
            rows.add(new RenameProposals.Row(l, addressed ? null : "SW Station " + i, addressed ? i + 5000 : null, null,
                null, null, null, l.getIntUID(), (addressed ? at : l).getIntUID()));
        }
        return new RenameProposals(new ArrayList<>(), 0, new java.util.HashMap<>(), rows, held);
    }

    /** Opens the table on these rows over ui, hands it to `look`, cancels it and waits until that is done. */
    private static void showing(TrainControlUI ui, RenameProposals rows, Consumer<SyncTable.Shown> look)
    {
        try (SyncTable table = SyncTable.canceling(look))
        {
            SwingUtilities.invokeLater(() -> SyncDifferencesWindow.ask(ui, rows));
            assertTrue(Components.waitFor(() -> !table.shown().isEmpty(), 60000), "the table did not open");
            Components.settle();
        }
    }

    /** Whether the first label reading `text` is bold. */
    private static boolean bold(SyncTable.Shown t, String text)
    {
        return Components.find(t.dialog, JLabel.class, l -> text.equals(l.getText())).getFont().isBold();
    }

    /** Where the heading reading key's text starts, across the window. */
    private static int x(SyncTable.Shown t, String key)
    {
        return at(t, heading(t, key)).x;
    }

    /** The heading reading key's text: a label, or a link. */
    private static Component heading(SyncTable.Shown t, String key)
    {
        return Components.find(t.dialog, javax.swing.JComponent.class, c -> I18n.t(key).equals(c instanceof JLabel
            ? ((JLabel) c).getText() : c instanceof AbstractButton ? ((AbstractButton) c).getText() : null));
    }

    /** Every row's Ask Again on screen without scrolling sideways, and the last row in full, as the table opens. */
    private static void fitsWhole(List<String> problems, SyncTable.Shown t)
    {
        JScrollPane scrolling = Components.find(t.dialog, ChoiceTable.class, c -> true);
        Component rows = scrolling.getViewport().getView();
        Rectangle seen = scrolling.getViewport().getViewRect();
        for (String row : t.rows)
        {
            AbstractButton ask = t.in(row, "ASK", AbstractButton.class);
            check(problems, seen.contains(SwingUtilities.convertRectangle(ask.getParent(), ask.getBounds(), rows)), true,
                row + "'s Ask Again on screen");
        }
        check(problems, rows.getPreferredSize().height <= seen.height, true, "the last row in full");
    }

    /** Whether a row's Keep Both is lit, what it says on hover, and to a screen reader. */
    private static List<Object> both(SyncTable.Shown t, String row)
    {
        AbstractButton b = t.in(row, "BOTH", AbstractButton.class);
        return b == null ? null : Arrays.asList(b.isEnabled(), b.getToolTipText(), b.getAccessibleContext()
            .getAccessibleName());
    }

    private static void check(List<String> problems, Object got, Object expected, String what)
    {
        if (!java.util.Objects.equals(got, expected)) problems.add(what + ": " + got + " not " + expected);
    }

    /** The rows of the Set all claim on these answers, top to bottom, the ignored last on Ignore; SW Both has none. */
    private static List<String> rowsOn(String... answers)
    {
        List<String> rows = Arrays.asList("SW A", "SW B", "SW C", "SW D", "SW E", "SW F", "SW Holder", "SW T");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) out.add(rows.get(i) + ":" + answers[i]);
        out.add("SW I:IGNORE");
        return out;
    }

    /** What an answer's radio says to a screen reader. */
    private static String text(SyncTable.Shown t, String row, String what)
    {
        AbstractButton b = t.in(row, what, AbstractButton.class);
        return b == null ? null : b.getAccessibleContext().getAccessibleName();
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

    /** The station's locomotive list: names each followed by an MM2 address, or an mfx or DCC one as its UID. */
    private static String station(Object... entries)
    {
        List<String> records = new ArrayList<>();
        for (int i = 0; i < entries.length; i += 2)
        {
            String name = (String) entries[i];
            int uid = (Integer) entries[i + 1];
            String type = uid >= MarklinLocomotive.DCC_BASE ? "dcc"
                : uid >= MarklinLocomotive.MFX_BASE ? "mfx" : "mm2_prg";
            records.add("{\"name\":\"" + name + "\",\"internname\":\"" + name + "\",\"uid\":\"0x"
                + Integer.toHexString(uid) + "\",\"dectyp\":\"" + type + "\",\"funktionen\":" + FUNCTION + "}");
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
        return pressSync(rig, ui, ui, table, said);
    }

    /** The same, pressed in `asking`. */
    private static SyncTable.Shown pressSync(StationRig rig, TrainControlUI ui, Component asking, SyncTable table,
        List<String> said) throws Exception
    {
        rig.model.setNetworkCommState(true);
        int syncs = rig.station.requests("/app/api/locos");
        try (Answering answering = Answering.start(m -> Answering.pressAt(0)); SyncTable t = table)
        {
            SwingUtilities.invokeLater(() -> ui.doSync(asking));
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
        /** @param list the station's locomotive list as it is when asked, empty at start-up */
        void run(StationRig rig, TrainControlUI ui, AtomicReference<String> list) throws Exception;
    }

    private static void inAWindow(Claim claim) throws Exception
    {
        inAWindow(new AtomicBoolean(), claim);
    }

    /**
     * The claim in the real window against a stand-in that drops every connection while `down` is set, once the
     * window's start-up sync has ended.
     */
    private static void inAWindow(AtomicBoolean down, Claim claim) throws Exception
    {
        AtomicReference<String> list = new AtomicReference<>("[]");
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
                claim.run(rig, ui, list);
            }
            finally
            {
                SwingUtilities.invokeAndWait(ui::dispose);
            }
        }
    }
}
