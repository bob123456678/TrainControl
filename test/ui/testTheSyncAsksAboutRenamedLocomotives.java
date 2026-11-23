package ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
import support.SettingsWindow;
import support.Settle;
import support.SyncTable;
import support.StandInStation;
import support.StationRig;
import static ui.testTheSyncSaysWhenPartOfTheDataWasNotRead.syncEnded;

/**
 * A pressed Sync asks about locomotives the Central Station holds under another name at the same address, once, as
 * it starts, so the sync reads them under their new names and no second sync is needed (FR-220): the separate Check
 * for Renamed Locomotives is gone.  Through the real window against a stand-in CS3, whose MM2 locomotive at address 10
 * is "BR 10 001".
 *
 * @author Adam
 */
public class testTheSyncAsksAboutRenamedLocomotives
{
    private static final String INFO = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    /** The fixture's name for the MM2 locomotive at address 10. */
    private static final String STATION_NAME = "BR 10 001";

    /** The fixture's MM2 locomotive at address 3. */
    private static final String AT_3 = "BR 103 137-6";

    /**
     * A pressed sync that finds a locomotive held here under another name shows it once in the table, whose Apply says
     * the rename; Apply renames it, the sync adds no copy under the station's name, and no second sync follows (the
     * device file is read once per sync).
     *
     * MUTATION: in `TrainControlUI.askAndApply`, return `new int[0]` before the table, and nothing is asked.
     */
    @Test(timeOut = 180000)
    public void testASyncThatFindsARenameAsksOnceAndRenames() throws Exception
    {
        final String old = "FR-220 old name";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            // The start-up sync may have read the station already: then its locomotive is the one renamed here
            if (rig.model.getLocByName(STATION_NAME) == null) rig.model.newMM2Locomotive(old, 10);
            else rig.model.renameLoc(STATION_NAME, old);

            int syncs = rig.station.requests("/config/geraet.vrs");

            pressSync(rig, ui, SyncTable.answer(old, "RENAME"), m -> Answering.pressAt(0),
                testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertEquals(rowsFor(old).size(), 1, "the rename was not in one table: " + tables.size());
            assertEquals(text(rowsFor(old).get(0), old, "RENAME"), I18n.f("sync.ui.rename", old, STATION_NAME),
                "Apply does not say the rename");

            assertNotNull(rig.model.getLocByName(STATION_NAME), "Apply did not rename " + old + " to " + STATION_NAME);
            assertNull(rig.model.getLocByName(old), old + " is still there beside the renamed locomotive");

            assertEquals(rig.model.getLocList().stream().filter(STATION_NAME::equals).count(), 1L,
                "the sync added a copy under the station's name");

            assertEquals(rig.station.requests("/config/geraet.vrs") - syncs, 1, "a second sync ran after the rename");
        });
    }

    /**
     * Renaming onto a name another locomotive holds says in its row what it deletes, and Apply asks once, in the delete
     * door's own question led by the rename it is for: it counts the routes that lose their commands and starts on No,
     * and No keeps both locomotives and the route's command.
     *
     * MUTATION: in `TrainControlUI.renamed`, delete without the question (`deleteLocAlreadyAsked`), or keep nothing
     * after the delete question's No.
     */
    @Test(timeOut = 180000)
    public void testARenameOntoAHeldNameAsksTheDeleteQuestionOnce() throws Exception
    {
        final String old = "D137 old";
        final String route = "D137 route";
        final String no = String.valueOf(TrainControlUI.YES_NO_OPTS[1]);

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            rig.model.newMM2Locomotive(old, 10);

            if (rig.model.getLocByName(STATION_NAME) == null) rig.model.newMM2Locomotive(STATION_NAME, 10);

            List<RouteCommand> commands = new ArrayList<>(Collections.singletonList(
                RouteCommand.RouteCommandFunction(STATION_NAME, 1, true)));

            rig.model.newRoute(route, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null);

            List<Answering.Question> asked = new ArrayList<>();

            pressSync(rig, ui, SyncTable.answer(old, "RENAME"), m -> m != null && m.contains(old) ? Answering.press(no)
                : Answering.pressAt(0), answering ->
                {
                    asked.clear();

                    for (Answering.Question q : answering.questions())
                    {
                        if (q.message != null && q.message.contains(old)) asked.add(q);
                    }

                    return !asked.isEmpty();
                });

            assertEquals(asked.size(), 1, "renaming onto a held name asked other than once: " + asked.size());
            assertEquals(Components.onTheEventThread(() -> rowsFor(old).get(0).in(old, "deletes", javax.swing.JLabel.class)
                .getText()), I18n.f("sync.ui.deletes", STATION_NAME), "the row does not say what the rename deletes");

            assertTrue(asked.get(0).message.contains(I18n.f("ui.confirmDeleteFromDatabaseWithRoutesOne",
                STATION_NAME, 1)), "the question does not count the route that loses its command: "
                + asked.get(0).message);

            assertTrue(asked.get(0).message.contains(I18n.f("loc.ui.confirmRenameOntoLocomotive", old,
                STATION_NAME)), "the question does not say the rename it deletes for: " + asked.get(0).message);

            assertEquals(asked.get(0).startsOn, no, "the question does not start on No");

            assertNotNull(rig.model.getLocByName(old), "No took away " + old);
            assertNotNull(rig.model.getLocByName(STATION_NAME), "No deleted " + STATION_NAME);

            assertTrue(rig.model.getRoute(route).commandsDrive(STATION_NAME), "No took the route's command for "
                + STATION_NAME);

            assertEquals(rig.model.getLocByName(old).getDeclinedStationName(), STATION_NAME, "No to the delete question"
                + " was not kept");
        });
    }

    /**
     * The rename read waits on the station behind the busy sign, off the event thread (OB-475): Sync pressed in the
     * Locomotive Database window, while a slow station holds back its locomotive list the window still answers, with
     * the busy sign up over the window that asked, and once the list arrives the sync goes on and ends.
     *
     * MUTATION: in `TrainControlUI.doSync`, call `pressedSync(c, answered)` on the event thread, just before the worker
     * starts, instead of inside it; or, in `readBehindTheSpinner`, show the busy sign over `this`.
     */
    @Test(timeOut = 180000)
    public void testTheRenameReadLeavesTheWindowFreeWhileTheStationIsSlow() throws Exception
    {
        final CountDownLatch asked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean holding = new AtomicBoolean();
        inAWindow(holdingTheRenameRead(asked, release, holding), (rig, ui) ->
        {
            rig.model.setNetworkCommState(true);

            SwingUtilities.invokeAndWait(() -> ui.showLocSelector(false));

            java.awt.Window database = ui.getLocSelector();

            assertTrue(Components.waitFor(() -> Components.onTheEventThread(database::isShowing), 10000),
                "precondition: the Locomotive Database window did not open");

            holding.set(true);

            try (Answering answering = Answering.start(m -> Answering.pressAt(0));
                SyncTable table = SyncTable.answering(SyncTable.answer()))
            {
                SwingUtilities.invokeLater(() -> ui.doSync(database));

                assertTrue(asked.await(60, TimeUnit.SECONDS), "the sync did not read the station's locomotives");

                List<String> signs = Components.busySignsIfAnswering(3000);

                assertNotNull(signs, "the window froze while the rename read waited on the station");
                assertFalse(signs.isEmpty(), "no busy sign showed while the rename read waited on the station");

                assertTrue(Components.onTheEventThread(() -> java.util.Arrays.stream(java.awt.Window.getWindows())
                    .anyMatch(w -> w instanceof org.traincontrol.gui.widgets.BusyDialog && w.isShowing()
                    && w.getOwner() == database)), "the rename read's busy sign is not over the Locomotive Database"
                    + " window");

                release.countDown();

                assertTrue(Components.waitFor(() -> saidDone(answering), 60000), "the sync did not end once the list"
                    + " arrived: " + answering.asked());

                assertTrue(syncEnded(ui), "a sync never ended");
            }
            finally
            {
                release.countDown();

                SwingUtilities.invokeAndWait(() -> database.setVisible(false));
            }
        });
    }

    /**
     * A rename read the station answers with something unreadable is logged and takes its busy sign down, and the sync
     * goes on and ends: a sign left up is a modal window nothing closes.
     *
     * MUTATION: in `TrainControlUI.readBehindTheSpinner`, take out the `finally` that closes the busy sign.
     */
    @Test(timeOut = 180000)
    public void testAFailedRenameReadTakesItsBusySignDown() throws Exception
    {
        final AtomicBoolean failing = new AtomicBoolean();
        final BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", INFO);

        inAWindow((path, n) -> "/app/api/locos".equals(path) && failing.getAndSet(false)
            ? "<html>not json</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8) : cs3.apply(path, n), (rig, ui) ->
        {
            rig.model.setNetworkCommState(true);
            failing.set(true);

            try (Answering answering = Answering.start(m -> Answering.pressAt(0));
                SyncTable table = SyncTable.answering(SyncTable.answer()))
            {
                SwingUtilities.invokeLater(() -> ui.doSync(ui));

                assertTrue(Components.waitFor(() -> saidDone(answering), 60000), "the sync did not end after the rename"
                    + " read failed: " + answering.asked());

                assertFalse(failing.get(), "precondition: the rename read was not answered unreadably");
                assertTrue(table.shown().isEmpty(), "a table opened after the rename read failed");

                assertTrue(Components.waitFor(() -> new ArrayList<>().equals(Components.busySignsIfAnswering(3000)),
                    30000), "a busy sign stayed up after the rename read failed");
            }
        });
    }

    /**
     * Only a pressed Sync asks: the sync an edit owes, run as an editor closes, leaves a locomotive renamed at the
     * station as it is, adds no copy, opens no table, logs only that it held the station's locomotive back, and reads
     * the station's locomotives once, for itself (D171, FR-249).
     *
     * MUTATION: in `TrainControlUI.doSync`, ask on every sync (`pressedSync(c != null ? c : this, answered)`).
     */
    @Test(timeOut = 180000)
    public void testAnOwedSyncAsksNothingAboutRenames() throws Exception
    {
        final String old = "FR-220 owed";
        final String no = String.valueOf(TrainControlUI.YES_NO_OPTS[1]);

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            if (rig.model.getLocByName(STATION_NAME) == null) rig.model.newMM2Locomotive(old, 10);
            else rig.model.renameLoc(STATION_NAME, old);

            rig.model.setNetworkCommState(true);

            int locos = rig.station.requests("/app/api/locos");
            rig.takeLog();

            try (Answering answering = Answering.start(m -> m != null && m.contains(old) ? Answering.press(no)
                : Answering.pressAt(0)); SyncTable table = SyncTable.answering(SyncTable.answer()))
            {
                owedSync(rig, ui);

                List<String> log = rig.takeLog();
                org.traincontrol.base.Locomotive here = rig.model.getLocByName(old);
                assertEquals(Collections.frequency(log, I18n.f("loc.renamePendingNotAdded", STATION_NAME, old,
                    here.getDecoderTypeLabel(), here.getAddress())), 1, "the owed sync did not log the hold-back once");
                assertEquals(renameLines(log, old, STATION_NAME), new ArrayList<String>(), "the owed sync logged the"
                    + " rename check");
                assertTrue(table.shown().isEmpty(), "the owed sync opened the table");
                assertNull(rig.model.getLocByName(STATION_NAME), "the owed sync added a copy");

                assertFalse(answering.asked().stream().anyMatch(m -> m.contains(old)), "the owed sync asked about the"
                    + " rename: " + answering.asked());
            }

            assertEquals(rig.station.requests("/app/api/locos") - locos, 1, "the owed sync read the station's"
                + " locomotives twice");

            assertNotNull(rig.model.getLocByName(old), "the owed sync renamed " + old);
        });
    }

    /**
     * A pressed Sync's row starts on Ask Again; Keep As Is keeps the name, adds no copy under the station's, and is
     * logged.  The same name is then not shown again, only logged as answered before, until the station gives the
     * locomotive another name, which is shown.
     *
     * MUTATION: in `RenameProposals.Row.asksName`, leave out `!isNameKept()`; or in `TrainControlUI.apply`, leave out the
     * `loc.renameKeptAsAnswered` line; in `MarklinControlStation.syncThroughTheNewParser`, leave out `!nameTurnedDown(l)`.
     */
    @Test(timeOut = 240000)
    public void testANoIsKeptUntilTheStationOffersAnotherName() throws Exception
    {
        final String old = "FR-220 kept";
        final String renamed = STATION_NAME + " B";
        final AtomicReference<String> stationName = new AtomicReference<>(STATION_NAME);

        inAWindow(renaming(stationName), (rig, ui) ->
        {
            assertEquals(keptOnce(rig, ui, old).startsOn.get(old), "ASK", "the row does not start on Ask Again");

            assertNotNull(rig.model.getLocByName(old), "Keep renamed " + old);
            assertNull(rig.model.getLocByName(STATION_NAME), "the sync added a copy under the name kept against");

            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f("loc.renameKept", old,
                STATION_NAME)), "the pressed sync did not log the Keep");

            assertEquals(answering(rig, ui, old, "KEEP").size(), 0, "a Keep was asked again for the same name");

            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f(
                "loc.renameKeptAsAnswered", old, STATION_NAME)), "the pressed sync did not log the Keep kept");

            stationName.set(renamed);

            List<SyncTable.Shown> again = answering(rig, ui, old, "KEEP");

            assertEquals(again.size(), 1, "the station's new name was not asked about");
            assertEquals(text(again.get(0), old, "RENAME"), I18n.f("sync.ui.rename", old, renamed),
                "the row is not about the new name");
        });
    }

    /**
     * A Keep outlasts a restart: saved with the locomotive, it is not asked again by the restarted program's Sync, which
     * logs it as answered before.
     *
     * MUTATION: in `MarklinControlStation.newLocomotive(MarklinSimpleComponent)`, leave out
     * `setDeclinedStationName`.
     */
    @Test(timeOut = 240000)
    public void testANoSurvivesARestart() throws Exception
    {
        final String old = "FR-220 restart";
        final java.io.File root = Files.createTempDirectory("tc-sync-restart").toFile();
        final BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", INFO);

        inAWindow(root, cs3, (rig, ui) ->
        {
            keptOnce(rig, ui, old);

            assertNull(rig.model.saveState(false), "the saved data was not written");
        });

        inAWindow(root, cs3, (rig, ui) ->
        {
            assertNotNull(rig.model.getLocByName(old), "precondition: the restart did not read " + old);

            rig.takeLog();

            assertEquals(answering(rig, ui, old, "KEEP").size(), 0, "the restarted program asked the Keep again");

            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f(
                "loc.renameKeptAsAnswered", old, STATION_NAME)), "the restarted program did not log the No kept");
        });
    }

    /**
     * The fixture, with the locomotive list it is asked for while `holding` is set held back until `release`, and
     * `asked` counted down as it is.
     */
    private static BiFunction<String, Integer, byte[]> holdingTheRenameRead(CountDownLatch asked,
        CountDownLatch release, AtomicBoolean holding)
    {
        final BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", INFO);

        return (path, n) ->
        {
            if ("/app/api/locos".equals(path) && holding.getAndSet(false))
            {
                asked.countDown();

                try
                {
                    release.await(60, TimeUnit.SECONDS);
                }
                catch (InterruptedException stop)
                {
                    Thread.currentThread().interrupt();
                }
            }

            return cs3.apply(path, n);
        };
    }

    /** The fixture, with the station's name for the locomotive at address 10 as `name` holds it now. */
    private static BiFunction<String, Integer, byte[]> renaming(AtomicReference<String> name)
    {
        return rewriting(list -> list.replace("\"" + STATION_NAME + "\"", "\"" + name.get() + "\""));
    }

    /** The fixture, with its locomotive list rewritten by `locos` each time it is asked for. */
    private static BiFunction<String, Integer, byte[]> rewriting(java.util.function.UnaryOperator<String> locos)
    {
        final BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", INFO);

        return (path, n) ->
        {
            byte[] body = cs3.apply(path, n);

            return !"/app/api/locos".equals(path) || body == null ? body
                : locos.apply(new String(body, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
        };
    }

    /** Runs a sync owed by an edit, as `editorGone` runs it with no window to tell, to its end. */
    private static void owedSync(StationRig rig, TrainControlUI ui) throws Exception
    {
        int syncs = rig.station.requests("/config/geraet.vrs");

        ((AtomicBoolean) support.Reflect.field(TrainControlUI.class, "syncOwed").get(ui)).set(true);

        SwingUtilities.invokeLater(() -> ui.doSync(null));

        assertTrue(Settle.until(() -> rig.station.requests("/config/geraet.vrs") > syncs, 60000),
            "the owed sync never ran");
        assertTrue(syncEnded(ui), "the owed sync never ended");

        Components.settle();
    }

    /** The station's locomotive at address 10 held here as `old`, as though renamed at the station since. */
    private static void renamedHere(StationRig rig, String old)
    {
        // The start-up sync may have read the station already: then its locomotive is the one renamed here
        if (rig.model.getLocByName(STATION_NAME) == null) rig.model.newMM2Locomotive(old, 10);
        else rig.model.renameLoc(STATION_NAME, old);

        rig.takeLog();
    }

    /**
     * Presses Sync answering `old`'s row `answer` and OK to the rest, until the sync says it is done; the tables that
     * had a row for it.
     */
    private static List<SyncTable.Shown> answering(StationRig rig, TrainControlUI ui, String old, String answer)
        throws Exception
    {
        pressSync(rig, ui, SyncTable.answer(old, answer), m -> Answering.pressAt(0),
            testTheSyncAsksAboutRenamedLocomotives::saidDone);
        return rowsFor(old);
    }

    /** `old` held here as renamed at the station, kept as it is in one table: that table. */
    private static SyncTable.Shown keptOnce(StationRig rig, TrainControlUI ui, String old) throws Exception
    {
        renamedHere(rig, old);
        List<SyncTable.Shown> asked = answering(rig, ui, old, "KEEP");
        assertEquals(asked.size(), 1, "precondition: the rename was not asked once");
        return asked.get(0);
    }

    /** `old` held here as renamed at the station, left on Ask Again in one table, with no copy added. */
    private static void leftToAsk(StationRig rig, TrainControlUI ui, String old) throws Exception
    {
        renamedHere(rig, old);
        assertEquals(answering(rig, ui, old, "ASK").size(), 1, "precondition: the rename was not asked");
        assertNull(rig.model.getLocByName(STATION_NAME), "Ask Again added a copy under the station's name");
    }

    /** A row's two Apply choices' texts in a table shown. */
    private static List<String> applies(SyncTable.Shown s, String row) throws Exception
    {
        return Arrays.asList(text(s, row, "RENAME"), text(s, row, "ADDRESS"));
    }

    /** What an answer's radio says to a screen reader, in a table shown. */
    private static String text(SyncTable.Shown s, String row, String answer) throws Exception
    {
        return Components.onTheEventThread(() -> s.in(row, answer, javax.swing.AbstractButton.class)
            .getAccessibleContext().getAccessibleName());
    }

    /** The tables the last pressSync showed that had a row for this locomotive. */
    private static List<SyncTable.Shown> rowsFor(String name)
    {
        List<SyncTable.Shown> with = new ArrayList<>();
        for (SyncTable.Shown s : tables) if (s.rows.contains(name)) with.add(s);
        return with;
    }

    /** The rename check's own lines about this offer, and its "nothing to rename", logged since the last take. */
    private static List<String> renameLines(StationRig rig, String old, String station)
    {
        return renameLines(rig.takeLog(), old, station);
    }

    /** The same, of these log lines. */
    private static List<String> renameLines(List<String> log, String old, String station)
    {
        List<String> lines = new ArrayList<>(log);

        lines.retainAll(Arrays.asList(I18n.f("loc.renameApplied", old, station), I18n.f("loc.renameKept", old, station),
            I18n.f("loc.renameKeptAsAnswered", old, station), I18n.f("loc.renameNotApplied", old, station),
            I18n.f("loc.renameClosed", old, station), I18n.t("loc.ui.infoNoLocomotivesToRename"),
            I18n.f("loc.syncLeftToAsk", old), I18n.f("loc.syncRowChanged", old)));

        return lines;
    }

    /** Nothing renamed, kept or copied under the station's name for this offer; its one log line is `key`. */
    private static void notRenamed(StationRig rig, String old, String station, String key)
    {
        assertNotNull(rig.model.getLocByName(old), old + " was renamed to " + station);
        assertNull(rig.model.getLocByName(old).getDeclinedStationName(), "the offer of " + station + " was kept");
        assertNull(rig.model.getLocByName(station), "the sync added a copy under " + station);
        assertEquals(renameLines(rig, old, station), Collections.singletonList(I18n.f(key, old, station)), "the offer"
            + " was not logged as " + key);
    }

    /** Whether the sync has said it is complete, with or without the table's account after. */
    private static boolean saidDone(Answering answering)
    {
        Pattern done = Pattern.compile(Pattern.quote(I18n.t("ui.infoSyncCompleteItemsAdded")).replace("{0}",
            "\\E\\d+\\Q"));

        return answering.asked().stream().anyMatch(m -> done.matcher(m).lookingAt());
    }

    /**
     * A delete the program refuses is not the user's No: while the locomotive holding the station's name is moving with
     * the power on, Apply's rename onto it is refused without a question, logged as not renamed and kept nowhere, and
     * once it stops the next Sync's Apply asks the delete question.
     *
     * MUTATION: in `TrainControlUI.renamed`, keep a Keep whenever the holder is still there (drop the
     * `answer == JOptionPane.NO_OPTION` test); or, in `LocomotiveActions.deleteLoc`, leave out the `answered` reset, and
     * an earlier No to another delete is kept.
     */
    @Test(timeOut = 240000)
    public void testARefusedDeleteIsNotANo() throws Exception
    {
        final String old = "FR-220 refused";
        final String onto = I18n.f("loc.ui.confirmRenameOntoLocomotive", old, STATION_NAME);

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            renamedHere(rig, old);

            // The station's name held by another locomotive, at another address, moving
            rig.model.newMM2Locomotive(STATION_NAME, 233);

            org.traincontrol.base.Locomotive holder = rig.model.getLocByName(STATION_NAME);

            // A No to another locomotive's delete first, which a refused delete must not inherit
            rig.model.newMM2Locomotive(old + " other", 234);

            try (Answering answering = Answering.start(m -> Answering.press(String.valueOf(
                TrainControlUI.YES_NO_OPTS[1]))))
            {
                SwingUtilities.invokeLater(() -> ui.deleteLoc(old + " other"));

                assertTrue(answering.awaitAsked(1, 30000), "precondition: the delete question was not asked");

                AskingWindows.awaitAnswered();
            }

            support.SettingsWindow.power(ui, rig.model, true);
            holder.setSpeed(40);
            rig.takeLog();

            pressSync(rig, ui, SyncTable.answer(old, "RENAME"), m -> Answering.pressAt(0),
                testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertNull(rig.model.getLocByName(old).getDeclinedStationName(), "the refused delete was kept");
            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f("loc.renameNotApplied",
                old, STATION_NAME)), "the refused delete was not logged as not renamed");

            holder.setSpeed(0);

            List<Answering.Question> asked = pressSync(rig, ui, SyncTable.answer(old, "RENAME"), m -> m != null
                && m.contains(old) ? Answering.press(String.valueOf(TrainControlUI.YES_NO_OPTS[1])) : Answering.pressAt(0),
                testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertTrue(asked.stream().anyMatch(q -> q.message.startsWith(onto)), "the delete question was not asked"
                + " once the locomotive had stopped");
        });
    }

    /**
     * A row left on Ask Again renames nothing and keeps nothing: it is logged as left to ask, the sync adds no copy
     * under the station's name, and the next Sync shows the row again.
     *
     * MUTATION: in `TrainControlUI.apply`, treat Ask Again as Keep; or in
     * `MarklinControlStation.syncThroughTheNewParser`, leave out `setHeldBackStationName`.
     */
    @Test(timeOut = 240000)
    public void testAClosedQuestionKeepsNothing() throws Exception
    {
        final String old = "FR-220 closed";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            leftToAsk(rig, ui, old);

            assertNotNull(rig.model.getLocByName(old), "Ask Again renamed " + old);
            assertNull(rig.model.getLocByName(old).getDeclinedStationName(), "Ask Again was kept");
            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f("loc.syncLeftToAsk",
                old)), "Ask Again was not logged");
            assertEquals(answering(rig, ui, old, "ASK").size(), 1, "the next Sync did not ask again");
        });
    }

    /**
     * A rename left unanswered gets no copy under the station's name from any sync until it is answered: the sync an
     * edit owes next holds the station's locomotive back as a pending rename, and says so in the log (FR-249).
     *
     * MUTATION: in `MarklinControlStation.syncThroughTheNewParser`, leave out `setHeldBackStationName`, and this fails.
     */
    @Test(timeOut = 240000)
    public void testAskAgainAddsNoCopyUntilAnswered() throws Exception
    {
        final String old = "FR-220 closed, then owed";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            leftToAsk(rig, ui, old);

            rig.takeLog();

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
            {
                owedSync(rig, ui);
            }

            org.traincontrol.base.Locomotive here = rig.model.getLocByName(old);

            assertNull(rig.model.getLocByName(STATION_NAME), "the owed sync added a copy of the rename still unanswered");
            assertNotNull(here, "the owed sync renamed " + old);
            assertTrue(rig.takeLog().contains(I18n.f("loc.renamePendingNotAdded", STATION_NAME, old,
                here.getDecoderTypeLabel(), here.getAddress())), "the owed sync did not log the rename it held back");
        });
    }

    /**
     * A row whose locomotive is renamed away while the table is open is left, with a log line, and nothing is held back
     * but by rule: the sync an edit owes next still adds no copy of the other row's station locomotive, a rename still
     * pending (FR-249).
     *
     * MUTATION: in `TrainControlUI.apply`, leave out the `getLocByName(name) != r.loco` check, and the rename of the
     * locomotive gone throws; or in `MarklinControlStation.syncThroughTheNewParser`, leave out `setHeldBackStationName`.
     */
    @Test(timeOut = 240000)
    public void testQuestionsThatThrowPartwayHoldNothingBackAfter() throws Exception
    {
        final String first = "THR first";
        final String firstAtTheStation = "1047 005-2";
        final String gone = "THR gone";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            renamedHere(rig, gone);

            // MM2 address 16, which the station lists before BR 10 001
            if (rig.model.getLocByName(firstAtTheStation) == null) rig.model.newMM2Locomotive(first, 16);
            else rig.model.renameLoc(firstAtTheStation, first);

            pressSync(rig, ui, SyncTable.answer(gone, "RENAME"), s -> rig.model.renameLoc(gone, gone + " away"),
                m -> Answering.pressAt(0), testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertEquals(rowsFor(first).size(), 1, "precondition: the first row was not shown");
            assertNull(rig.model.getLocByName(gone), "precondition: the second row's locomotive is still there");
            assertNull(rig.model.getLocByName(firstAtTheStation), "precondition: Ask Again added a copy");
            assertTrue(rig.takeLog().contains(I18n.f("loc.syncRowChanged", gone)), "the row whose locomotive changed"
                + " was not logged as left");
            assertNull(rig.model.getLocByName(STATION_NAME), "the row whose locomotive changed was renamed");

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
            {
                owedSync(rig, ui);
            }

            assertNull(rig.model.getLocByName(firstAtTheStation), "the owed sync added a copy of the rename still"
                + " pending");
            assertNotNull(rig.model.getLocByName(first), "the owed sync renamed " + first);
        });
    }

    /**
     * A Central Station multi-unit still lists a member whose rename was answered Keep As Is: the station names the
     * member by the name kept against, which is the locomotive here that kept its own.
     *
     * MUTATION: in `MarklinLocomotive.getModelMultiUnitLocomotives`, look each member up by its name alone again.
     */
    @Test(timeOut = 240000)
    public void testAMultiUnitStillListsAMemberWhoseRenameWasAnsweredNo() throws Exception
    {
        final String head = "MF+ER";
        final String member = "MF 5028 DSB";
        final String old = "XFF member";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            if (rig.model.getLocByName(head) == null)
            {
                try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
                {
                    owedSync(rig, ui);
                }
            }

            assertNotNull(rig.model.getLocByName(member), "precondition: the station's member is not here");

            rig.model.renameLoc(member, old);

            assertEquals(answering(rig, ui, old, "KEEP").size(), 1, "precondition: the rename was not asked");
            assertNull(rig.model.getLocByName(member), "precondition: the sync added a copy under the name kept against");

            assertEquals(rig.model.getLocByName(head).getModelMultiUnitLocomotives(), Collections.singletonList(
                rig.model.getLocByName(old)), "the multi-unit does not list the member that kept its own name");
        });
    }

    /**
     * Closing the delete question Apply's rename onto a held name asks is logged as closed, and keeps nothing.
     *
     * MUTATION: in `TrainControlUI.renamed`, leave out the `loc.renameClosed` outcome of the delete question.
     */
    @Test(timeOut = 180000)
    public void testAClosedDeleteQuestionIsLoggedAsClosed() throws Exception
    {
        final String old = "FR-220 closed delete";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            renamedHere(rig, old);

            if (rig.model.getLocByName(STATION_NAME) == null) rig.model.newMM2Locomotive(STATION_NAME, 10);

            rig.takeLog();

            pressSync(rig, ui, SyncTable.answer(old, "RENAME"), m -> m != null && m.contains(old) ? Answering.pressAt(99)
                : Answering.pressAt(0), testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertNull(rig.model.getLocByName(old).getDeclinedStationName(), "closing the delete question kept it");
            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f("loc.renameClosed", old,
                STATION_NAME)), "closing the delete question was not logged as closed");
        });
    }

    /**
     * A rename to a station name with a comma, which no route condition could hold, is grayed in its row with the
     * reason, so the row stays on Ask Again; the sync adds no copy under that name, and the next Sync offers it again.
     *
     * MUTATION: in `MarklinControlStation.differences`, leave out the `isNameUsable` reason; or in
     * `MarklinControlStation.syncThroughTheNewParser`, leave out `setHeldBackStationName`.
     */
    @Test(timeOut = 240000)
    public void testARenameRefusedAfterYesAddsNoCopy() throws Exception
    {
        final String old = "FR-220 unusable";
        final String unusable = "BR 10, 001";
        final AtomicReference<String> stationName = new AtomicReference<>(STATION_NAME);

        inAWindow(renaming(stationName), (rig, ui) ->
        {
            renamedHere(rig, old);
            stationName.set(unusable);

            assertEquals(answering(rig, ui, old, "RENAME").size(), 1, "precondition: the rename was not asked");
            javax.swing.AbstractButton apply = rowsFor(old).get(0).in(old, "RENAME", javax.swing.AbstractButton.class);
            assertFalse(Components.onTheEventThread(apply::isEnabled), "Apply was not grayed");
            assertEquals(Components.onTheEventThread(apply::getToolTipText),
                I18n.t("loc.ui.errorLocomotiveNameUnusable"), "the grayed Apply does not say why");

            notRenamed(rig, old, unusable, "loc.syncLeftToAsk");
        });
    }

    /**
     * A run that begins while the table is open stops Apply's rename: the refusal is said once, nothing is renamed,
     * kept or copied, and it is logged as not renamed.
     *
     * MUTATION: in `TrainControlUI.apply`, leave out `RunGates.refuseWhileAutonomyRunning`.
     */
    @Test(timeOut = 240000)
    public void testARunBegunMeanwhileStopsTheRename() throws Exception
    {
        final String old = "FR-220 run";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            renamedHere(rig, old);

            org.traincontrol.automation.Layout railway = new org.traincontrol.automation.Layout(rig.model);
            List<Answering.Question> asked;

            try
            {
                // The run begins while the table is open
                asked = pressSync(rig, ui, SyncTable.answer(old, "RENAME"), s ->
                {
                    try
                    {
                        support.Reflect.set(MarklinControlStation.class, rig.model, "autoLayout", railway);
                    }
                    catch (ReflectiveOperationException e)
                    {
                        throw new IllegalStateException(e);
                    }
                    railway.setStagingInProgress(true);
                }, m -> Answering.pressAt(0), testTheSyncAsksAboutRenamedLocomotives::saidDone);
            }
            finally
            {
                railway.setStagingInProgress(false);
                support.Reflect.set(MarklinControlStation.class, rig.model, "autoLayout", null);
            }

            assertEquals(rowsFor(old).size(), 1, "precondition: the rename was not in the table");
            assertEquals(asked.stream().filter(q -> I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning")
                .equals(q.message)).count(), 1L, "the run's refusal was not said once");

            notRenamed(rig, old, STATION_NAME, "loc.renameNotApplied");
        });
    }

    /**
     * Apply to a new name forgets the Keep for the old one and is logged as renamed: when the station later gives the
     * locomotive the name kept against before, that is asked about again.
     *
     * MUTATION: in `TrainControlUI.renamed`, leave out `l.setDeclinedStationName(null)` after the rename, or return
     * `loc.renameNotApplied` in place of `loc.renameApplied`.
     */
    @Test(timeOut = 240000)
    public void testAYesForgetsTheNo() throws Exception
    {
        final AtomicReference<String> stationName = new AtomicReference<>(STATION_NAME);
        final String old = "FR-220 yes";
        final String renamed = STATION_NAME + " B";

        inAWindow(renaming(stationName), (rig, ui) ->
        {
            keptOnce(rig, ui, old);

            rig.takeLog();
            stationName.set(renamed);

            answering(rig, ui, old, "RENAME");

            assertNotNull(rig.model.getLocByName(renamed), "Apply did not rename " + old + " to " + renamed);
            assertEquals(renameLines(rig, old, renamed), Collections.singletonList(I18n.f("loc.renameApplied", old,
                renamed)), "the rename was not logged as renamed");

            stationName.set(STATION_NAME);

            assertEquals(answering(rig, ui, renamed, "KEEP").size(), 1, "the name kept against before was not asked"
                + " about again after Apply");
        });
    }

    /**
     * A Keep is forgotten once the station no longer offers the name: the station giving the locomotive its name here
     * logs "No locomotives to rename", and when it then offers the name kept against again, that is asked again.
     *
     * MUTATION: in `TrainControlUI.apply`, keep the Keep of a locomotive the station no longer offers a name for (leave
     * out its `setDeclinedStationName(null)`), or in `askAndApply` leave out the "nothing to rename" line.
     */
    @Test(timeOut = 240000)
    public void testANoIsForgottenWhenTheStationStopsOffering() throws Exception
    {
        final String old = "FR-220 forgotten";
        final AtomicReference<String> stationName = new AtomicReference<>(STATION_NAME);
        final BiFunction<String, Integer, byte[]> renamingIt = renaming(stationName);

        // The station's list cut to its locomotive at address 10, so no other offer or refusal is logged
        inAWindow((path, n) ->
        {
            byte[] body = renamingIt.apply(path, n);

            if (!"/app/api/locos".equals(path) || body == null) return body;

            org.json.JSONArray all = new org.json.JSONArray(new String(body, StandardCharsets.UTF_8));
            org.json.JSONArray one = new org.json.JSONArray();

            for (int i = 0; i < all.length(); i++)
            {
                if ("0xa".equals(all.getJSONObject(i).optString("uid"))) one.put(all.get(i));
            }

            return one.toString().getBytes(StandardCharsets.UTF_8);
        }, (rig, ui) ->
        {
            keptOnce(rig, ui, old);

            rig.takeLog();
            stationName.set(old);

            assertEquals(answering(rig, ui, old, "KEEP").size(), 0, "a name the station has was asked about");
            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.t(
                "loc.ui.infoNoLocomotivesToRename")), "nothing to rename was not logged");

            stationName.set(STATION_NAME);

            assertEquals(answering(rig, ui, old, "KEEP").size(), 1, "the Keep was kept after the station stopped"
                + " offering the name");
        });
    }

    /**
     * A pressed Sync shows a locomotive held here at another address than the station's in one row, on Ask Again, whose
     * Apply says the change, and Apply gives it the station's address (FR-246).
     *
     * MUTATION: in `MarklinControlStation.differences`, put nothing in `addressedAt`, and nothing is asked.
     */
    @Test(timeOut = 180000)
    public void testAPressedSyncAsksAboutAnotherAddressAndUsesItWhenTold() throws Exception
    {
        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            movedHere(rig);

            SyncTable.Shown shown = pressedOnce(rig, ui, "ADDRESS");
            assertEquals(shown.startsOn.get(STATION_NAME), "ASK", "the row does not start on Ask Again");
            assertEquals(text(shown, STATION_NAME, "ADDRESS"), I18n.f("sync.ui.changeAddress", STATION_NAME, 10));

            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 10, "Apply did not give it the station's"
                + " address");
            assertEquals(rig.model.getLocList().stream().filter(STATION_NAME::equals).count(), 1L,
                "the sync added a copy under the station's name");
        });
    }

    /**
     * Keep leaves the address and is not asked again, across a restart, until the station's address for the
     * locomotive changes, which is asked about (FR-246).
     *
     * MUTATION: in `TrainControlUI.apply`, leave out Keep's `setDeclinedStationAddress`, or in
     * `MarklinControlStation.newLocomotive(MarklinSimpleComponent)` leave out `setDeclinedStationAddress`.
     */
    @Test(timeOut = 300000)
    public void testAKeptAddressIsNotAskedAgainUntilTheStationsAddressChanges() throws Exception
    {
        final java.io.File root = Files.createTempDirectory("tc-sync-address").toFile();
        final AtomicInteger at = new AtomicInteger(10);
        final BiFunction<String, Integer, byte[]> cs3 = addressing(at);
        final String keep = "KEEP";

        inAWindow(root, cs3, (rig, ui) ->
        {
            movedHere(rig);

            assertEquals(pressing(rig, ui, keep).size(), 1, "precondition: the address was not asked about");
            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 70, "Keep gave it the station's address");
            assertTrue(rig.takeLog().contains(I18n.f("loc.addressKept", STATION_NAME, 10, 70)), "the sync did not log"
                + " the address kept");

            assertNull(rig.model.saveState(false), "the saved data was not written");
        });

        inAWindow(root, cs3, (rig, ui) ->
        {
            assertEquals(pressing(rig, ui, keep).size(), 0, "a kept address was asked again");

            at.set(12);

            assertEquals(text(pressedOnce(rig, ui, keep), STATION_NAME, "ADDRESS"), I18n.f("sync.ui.changeAddress",
                STATION_NAME, 12));
            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 70, "Keep gave it the station's address");
        });
    }

    /**
     * A locomotive held here at 10 that the station has at 3, while it calls the one at 10 another name, is one row with
     * two Apply choices, each doing only what it says: the rename leaves the station's locomotive at 3 to be added, the
     * address 3 frees 10 for the station's locomotive there in the same sync, Keep changes nothing and is not asked
     * again (FR-246).
     *
     * MUTATION: in `SyncDifferencesWindow.addRow`, leave out the address choice when the row also asks the name.
     */
    @Test(timeOut = 360000)
    public void testARenamedAndReAddressedLocomotiveIsOneQuestion() throws Exception
    {
        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            renamedAndMovedHere(rig);

            assertEquals(applies(pressedOnce(rig, ui, "RENAME"), AT_3), Arrays.asList(I18n.f("sync.ui.rename", AT_3,
                STATION_NAME), I18n.f("sync.ui.changeAddress", AT_3, 3)), "the row does not offer both changes");

            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 10, "the rename did not rename it");
            assertEquals(rig.model.getLocByName(AT_3).getAddress(), 3, "the station's locomotive at 3 was not added");

            rig.model.deleteLoc(AT_3);
            rig.model.renameLoc(STATION_NAME, AT_3);
            renamedAndMovedHere(rig);

            assertEquals(pressing(rig, ui, "ADDRESS").size(), 1, "precondition: the address was not asked");
            assertEquals(rig.model.getLocByName(AT_3).getAddress(), 3, "the address did not move it");
            assertNotNull(rig.model.getLocByName(STATION_NAME), "the sync did not add the station's locomotive at the"
                + " address it left");

            renamedAndMovedHere(rig);

            assertEquals(pressing(rig, ui, "KEEP").size(), 1, "precondition: Keep was not asked");
            assertEquals(rig.model.getLocByName(AT_3).getAddress(), 10, "Keep moved it");
            assertNull(rig.model.getLocByName(STATION_NAME), "Keep renamed it or the sync added a copy");
            assertEquals(pressing(rig, ui, "KEEP").size(), 0, "Keep was asked again");
        });
    }

    /**
     * A sync nobody pressed changes no address: the owed sync of an edit and the model's own sync, as at start-up or
     * a reconnect, leave a locomotive held here at another address as it is, and log it (FR-246).
     *
     * MUTATION: in `MarklinControlStation.adoptCentralStationAddress`, leave out the `takesStationAddress` check.
     */
    @Test(timeOut = 180000)
    public void testASyncNobodyPressedChangesNoAddress() throws Exception
    {
        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            movedHere(rig);
            owedSync(rig, ui);

            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 70, "the owed sync of an edit gave it the"
                + " station's address");
            assertTrue(rig.takeLog().contains(I18n.f("loc.addressDiffersAtStation", STATION_NAME, 10, 70)),
                "the owed sync did not log the station's other address");

            assertTrue(rig.model.syncWithCS2() >= 0, "precondition: the model's own sync failed");
            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 70, "the model's own sync gave it the"
                + " station's address");
        });
    }

    /**
     * Locomotive Settings' Sync asks the same about its own locomotive, over that window, Keep first, and Use address
     * gives it the station's for this sync alone; nothing else is asked (FR-246).
     *
     * MUTATION: in `TrainControlUI.syncLocomotiveFrom`, leave out `askAboutItsAddress`, and nothing is asked; or its
     * `setTakenStationAddress(null)`, and the sync's answer outlives it.
     */
    @Test(timeOut = 180000)
    public void testLocomotiveSettingsSyncAsksAboutItsAddress() throws Exception
    {
        final String use = I18n.f("loc.ui.optionUseAddress", 10);

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            movedHere(rig);
            rig.model.setNetworkCommState(true);

            final org.traincontrol.base.Locomotive loc = rig.model.getLocByName(STATION_NAME);
            final CountDownLatch synced = new CountDownLatch(1);

            assertTrue(SettingsWindow.open(ui, loc, org.traincontrol.gui.LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null),
                "precondition: Locomotive Settings did not open");

            try (Answering answering = Answering.start(m -> m != null && m.contains(STATION_NAME)
                ? Answering.press(use) : Answering.pressAt(0)))
            {
                SwingUtilities.invokeLater(() -> ui.syncLocomotiveFrom(loc, SettingsWindow.showing(), synced::countDown));

                assertTrue(synced.await(60, TimeUnit.SECONDS), "the sync never ended");
                AskingWindows.awaitAnswered();

                assertTrue(askedOnce(answering.questions(), I18n.f("loc.ui.askStationAddress", STATION_NAME, 10, 70),
                    I18n.t("loc.ui.optionKeep"), use).owner instanceof org.traincontrol.gui.LocomotiveSettingsFrame,
                    "not asked over Locomotive Settings");
            }
            finally
            {
                SettingsWindow.closeIfOpen();
            }

            assertEquals(loc.getAddress(), 10, "Use address did not give it the station's address");
            assertFalse(loc.takesStationAddress(10), "the answer outlived its sync");
        });
    }

    /**
     * Locomotive Settings' Sync reads the station's addresses off the event thread: while the station holds back its
     * locomotive list (up to 5 s), the program still answers, and the question still comes (FR-246).
     *
     * MUTATION: in `TrainControlUI.syncLocomotiveFrom`, call `stationAddresses` inside the `invokeLater`, and the event
     * thread waits out the station.
     */
    @Test(timeOut = 180000)
    public void testLocomotiveSettingsSyncLeavesTheProgramAnsweringWhileTheStationIsSlow() throws Exception
    {
        final AtomicBoolean slow = new AtomicBoolean(false);
        final CountDownLatch listing = new CountDownLatch(1), measured = new CountDownLatch(1);
        final BiFunction<String, Integer, byte[]> station = StandInStation.cs3("260", INFO);

        inAWindow((path, n) ->
        {
            if (slow.get() && path.startsWith("/app/api/lo"))
            {
                listing.countDown();

                try
                {
                    measured.await(5, TimeUnit.SECONDS);
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                }
            }

            return station.apply(path, n);
        }, (rig, ui) ->
        {
            movedHere(rig);
            rig.model.setNetworkCommState(true);

            final org.traincontrol.base.Locomotive loc = rig.model.getLocByName(STATION_NAME);
            final CountDownLatch synced = new CountDownLatch(1);
            long blocked;

            assertTrue(SettingsWindow.open(ui, loc, org.traincontrol.gui.LocomotiveSettingsFrame.Tab.LOCOMOTIVE, null),
                "precondition: Locomotive Settings did not open");

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
            {
                slow.set(true);
                SwingUtilities.invokeLater(() -> ui.syncLocomotiveFrom(loc, SettingsWindow.showing(), synced::countDown));
                assertTrue(listing.await(30, TimeUnit.SECONDS), "precondition: the station was not asked for its list");

                long start = System.nanoTime();
                SwingUtilities.invokeAndWait(() -> { });
                blocked = (System.nanoTime() - start) / 1000000;
                measured.countDown();

                assertTrue(synced.await(90, TimeUnit.SECONDS), "the sync never ended");
                AskingWindows.awaitAnswered();

                askedOnce(answering.questions(), I18n.f("loc.ui.askStationAddress", STATION_NAME, 10, 70),
                    I18n.t("loc.ui.optionKeep"), I18n.f("loc.ui.optionUseAddress", 10));
            }
            finally
            {
                slow.set(false);
                measured.countDown();
                SettingsWindow.closeIfOpen();
            }

            assertTrue(blocked < 1500, "the event thread waited " + blocked + " ms for the station");
        });
    }

    /** BR 10 001 held here at address 70, the station's at 10, as though its address was changed here since. */
    private static void movedHere(StationRig rig) throws Exception
    {
        if (rig.model.getLocByName(STATION_NAME) == null) rig.model.newMM2Locomotive(STATION_NAME, 70);
        else rig.model.changeLocAddress(STATION_NAME, 70, MarklinLocomotive.decoderType.MM2);

        rig.takeLog();
    }

    /** {@link #AT_3} held here at 10 and no BR 10 001 here, as though renamed and re-addressed at the station since. */
    private static void renamedAndMovedHere(StationRig rig) throws Exception
    {
        if (rig.model.getLocByName(STATION_NAME) != null) rig.model.deleteLoc(STATION_NAME);

        if (rig.model.getLocByName(AT_3) == null) rig.model.newMM2Locomotive(AT_3, 10);
        else rig.model.changeLocAddress(AT_3, 10, MarklinLocomotive.decoderType.MM2);

        rig.takeLog();
    }

    /** The fixture, with BR 10 001 at the station at the address `address` holds now. */
    private static BiFunction<String, Integer, byte[]> addressing(AtomicInteger address)
    {
        return rewriting(list ->
        {
            org.json.JSONArray all = new org.json.JSONArray(list);

            for (int i = 0; i < all.length(); i++)
            {
                org.json.JSONObject l = all.getJSONObject(i);

                // An MM2 locomotive's uid is its address
                if (STATION_NAME.equals(l.optString("name")))
                {
                    l.put("address", address.get()).put("uid", "0x" + Integer.toHexString(address.get()));
                }
            }

            return all.toString();
        });
    }

    /** The one question asked is `message` with these buttons, and starts on the first. */
    private static Answering.Question askedOnce(List<Answering.Question> asked, String message, String... buttons)
    {
        assertEquals(asked.size(), 1, "not asked once: " + asked.size());
        assertEquals(asked.get(0).message, message);
        assertEquals(asked.get(0).buttons, Arrays.asList(buttons));
        assertEquals(asked.get(0).startsOn, buttons[0], "the question does not start on " + buttons[0]);
        return asked.get(0);
    }

    /** As `pressing`, where exactly one table must have shown them. */
    private static SyncTable.Shown pressedOnce(StationRig rig, TrainControlUI ui, String answer) throws Exception
    {
        List<SyncTable.Shown> shown = pressing(rig, ui, answer);
        assertEquals(shown.size(), 1, "not asked in one table");
        return shown.get(0);
    }

    /** Presses Sync answering the rows of the locomotives of these claims so, OK to the rest; the tables with them. */
    private static List<SyncTable.Shown> pressing(StationRig rig, TrainControlUI ui, String answer) throws Exception
    {
        pressSync(rig, ui, SyncTable.answer(STATION_NAME, answer, AT_3, answer), m -> Answering.pressAt(0),
            testTheSyncAsksAboutRenamedLocomotives::saidDone);
        List<SyncTable.Shown> with = rowsFor(STATION_NAME);
        with.addAll(rowsFor(AT_3));
        return with;
    }

    private interface Claim
    {
        void run(StationRig rig, TrainControlUI ui) throws Exception;
    }

    /** The claim in the real window against a stand-in answering so, once the window's start-up sync has ended. */
    private static void inAWindow(BiFunction<String, Integer, byte[]> answer, Claim claim) throws Exception
    {
        inAWindow(Files.createTempDirectory("tc-sync-renames").toFile(), answer, claim);
    }

    /** The same over this folder's saved data, so a second window over it is a restart. */
    private static void inAWindow(java.io.File root, BiFunction<String, Integer, byte[]> answer, Claim claim)
        throws Exception
    {
        try (StationRig rig = new StationRig(root, answer, true))
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

    /** The tables the last pressSync showed. */
    private static List<SyncTable.Shown> tables = new ArrayList<>();

    private static List<Answering.Question> pressSync(StationRig rig, TrainControlUI ui, java.util.Map<String, String> table,
        java.util.function.Function<String, Answering.Answer> rule,
        java.util.function.Predicate<Answering> seen) throws Exception
    {
        return pressSync(rig, ui, table, null, rule, seen);
    }

    /**
     * Presses Sync with a station connected (the table opens only then), answers the table as `table` says after
     * `look`, and the questions by `rule` until `seen` holds, then waits for every sync to end and gives back what was
     * asked, the questions counted after the wait; the tables shown go in `tables`.
     */
    private static List<Answering.Question> pressSync(StationRig rig, TrainControlUI ui, java.util.Map<String, String> table,
        java.util.function.Consumer<SyncTable.Shown> look, java.util.function.Function<String, Answering.Answer> rule,
        java.util.function.Predicate<Answering> seen) throws Exception
    {
        rig.model.setNetworkCommState(true);

        try (Answering answering = Answering.start(rule); SyncTable answers = SyncTable.answering(table, look))
        {
            SwingUtilities.invokeLater(() -> ui.doSync(ui));

            assertTrue(Components.waitFor(() -> seen.test(answering), 60000), "the sync did not ask about the rename: "
                + answering.asked());

            AskingWindows.awaitAnswered();

            assertTrue(syncEnded(ui), "a sync never ended");

            Components.settle();

            assertTrue(Settle.until(() -> seen.test(answering), 1000), "what was asked is gone");

            tables = answers.shown();
            return new ArrayList<>(answering.questions());
        }
    }
}
