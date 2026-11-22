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
     * A pressed sync that finds a locomotive held here under another name asks once whether to rename it, Yes renames
     * it, the sync adds no copy under the station's name, and no second sync follows (the device file is read once per
     * sync).
     *
     * MUTATION: in `TrainControlUI.doSync`, leave out `askAboutRenames(c)`, and nothing is asked.
     */
    @Test(timeOut = 180000)
    public void testASyncThatFindsARenameAsksOnceAndRenames() throws Exception
    {
        final String old = "FR-220 old name";
        final String rename = I18n.f("loc.ui.confirmRenameLocomotive", old, STATION_NAME);
        final String yes = String.valueOf(TrainControlUI.YES_NO_OPTS[0]);

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            // The start-up sync may have read the station already: then its locomotive is the one renamed here
            if (rig.model.getLocByName(STATION_NAME) == null) rig.model.newMM2Locomotive(old, 10);
            else rig.model.renameLoc(STATION_NAME, old);

            int syncs = rig.station.requests("/config/geraet.vrs");

            List<String> asked = new ArrayList<>();

            for (Answering.Question q : pressSync(rig, ui, m -> rename.equals(m) ? Answering.press(yes)
                : Answering.pressAt(0), answering -> answering.asked().contains(rename))) asked.add(q.message);

            assertEquals(Collections.frequency(asked, rename), 1, "the rename was asked about other than once: "
                + asked);

            assertNotNull(rig.model.getLocByName(STATION_NAME), "Yes did not rename " + old + " to " + STATION_NAME);
            assertNull(rig.model.getLocByName(old), old + " is still there beside the renamed locomotive");

            assertEquals(rig.model.getLocList().stream().filter(STATION_NAME::equals).count(), 1L,
                "the sync added a copy under the station's name");

            assertEquals(rig.station.requests("/config/geraet.vrs") - syncs, 1, "a second sync ran after the rename");
        });
    }

    /**
     * Renaming onto a name another locomotive holds asks once, in the delete door's own question led by the rename it
     * is for: it counts the routes that lose their commands and starts on No, and No keeps both locomotives and the
     * route's command.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, put back the rename's own question for
     * a held name and the deletion without a question of its own (`deleteLocAlreadyAsked`), or keep nothing after the
     * delete question's No.
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

            pressSync(rig, ui, m -> m != null && m.contains(old) ? Answering.press(no) : Answering.pressAt(0),
                answering ->
                {
                    asked.clear();

                    for (Answering.Question q : answering.questions())
                    {
                        if (q.message != null && q.message.contains(old)) asked.add(q);
                    }

                    return !asked.isEmpty();
                });

            assertEquals(asked.size(), 1, "renaming onto a held name asked other than once: " + asked.size());

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
     * MUTATION: in `TrainControlUI.doSync`, call `askAboutRenames(c)` on the event thread, just before the worker starts,
     * instead of inside it; or, in `askAboutRenames(Component)`, show the busy sign over `this`.
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

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
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
     * MUTATION: in `TrainControlUI.askAboutRenames(Component)`, take out the `finally` that closes the busy sign.
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

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
            {
                SwingUtilities.invokeLater(() -> ui.doSync(ui));

                assertTrue(Components.waitFor(() -> saidDone(answering), 60000), "the sync did not end after the rename"
                    + " read failed: " + answering.asked());

                assertFalse(failing.get(), "precondition: the rename read was not answered unreadably");

                assertTrue(Components.waitFor(() -> new ArrayList<>().equals(Components.busySignsIfAnswering(3000)),
                    30000), "a busy sign stayed up after the rename read failed");
            }
        });
    }

    /**
     * Only a pressed Sync asks: the sync an edit owes, run as an editor closes, leaves a locomotive renamed at the
     * station as it is, logs and asks nothing about it, and reads the station's locomotives once, for itself
     * (D171).
     *
     * MUTATION: in `TrainControlUI.doSync`, ask on every sync (`askAboutRenames(c != null ? c : this)`).
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
                : Answering.pressAt(0)))
            {
                owedSync(rig, ui);

                assertEquals(renameLines(rig, old, STATION_NAME), new ArrayList<String>(), "the owed sync logged the"
                    + " rename check");

                assertFalse(answering.asked().stream().anyMatch(m -> m.contains(old)), "the owed sync asked about the"
                    + " rename: " + answering.asked());
            }

            assertEquals(rig.station.requests("/app/api/locos") - locos, 1, "the owed sync read the station's"
                + " locomotives twice");

            assertNotNull(rig.model.getLocByName(old), "the owed sync renamed " + old);
        });
    }

    /**
     * A pressed Sync's rename question starts on No (D171); No keeps the name, adds no copy under the station's, and is
     * logged.  The same offer is then not asked again, only logged as answered before, until the station gives the
     * locomotive another name, which is asked about.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, start the question on Yes
     * (`YES_NO_OPTS[0]`), or leave out the `getDeclinedStationName` check, or its `loc.renameKept` log line; in
     * `MarklinControlStation.syncThroughTheNewParser`, leave out `!nameTurnedDown(l)`.
     */
    @Test(timeOut = 240000)
    public void testANoIsKeptUntilTheStationOffersAnotherName() throws Exception
    {
        final String old = "FR-220 kept";
        final String renamed = STATION_NAME + " B";
        final AtomicReference<String> stationName = new AtomicReference<>(STATION_NAME);

        inAWindow(renaming(stationName), (rig, ui) ->
        {
            renamedHere(rig, old);

            List<Answering.Question> asked = about(answeringNo(rig, ui, old), old);

            assertEquals(asked.size(), 1, "the rename was not asked once");
            assertEquals(asked.get(0).startsOn, String.valueOf(TrainControlUI.YES_NO_OPTS[1]), "the rename question"
                + " does not start on No");

            assertNotNull(rig.model.getLocByName(old), "No renamed " + old);
            assertNull(rig.model.getLocByName(STATION_NAME), "the sync added a copy under the name turned down");

            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f("loc.renameKept", old,
                STATION_NAME)), "the pressed sync did not log the No");

            assertEquals(about(answeringNo(rig, ui, old), old).size(), 0, "a No was asked again for the same name");

            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f(
                "loc.renameKeptAsAnswered", old, STATION_NAME)), "the pressed sync did not log the No kept");

            stationName.set(renamed);

            asked = about(answeringNo(rig, ui, old), old);

            assertEquals(asked.size(), 1, "the station's new name was not asked about");
            assertTrue(asked.get(0).message.contains(renamed), "the question is not about the new name: "
                + asked.get(0).message);
        });
    }

    /**
     * A No outlasts a restart: saved with the locomotive, it is not asked again by the restarted program's Sync, which
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
            renamedHere(rig, old);

            assertEquals(about(answeringNo(rig, ui, old), old).size(), 1, "precondition: the rename was not asked");

            assertNull(rig.model.saveState(false), "the saved data was not written");
        });

        inAWindow(root, cs3, (rig, ui) ->
        {
            assertNotNull(rig.model.getLocByName(old), "precondition: the restart did not read " + old);

            rig.takeLog();

            assertEquals(about(answeringNo(rig, ui, old), old).size(), 0, "the restarted program asked the No again");

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

    /** Presses Sync answering No about `old` and OK to the rest, until the sync says it is done. */
    private static List<Answering.Question> answeringNo(StationRig rig, TrainControlUI ui, String old) throws Exception
    {
        String no = String.valueOf(TrainControlUI.YES_NO_OPTS[1]);

        return pressSync(rig, ui, m -> m != null && m.contains(old) ? Answering.press(no) : Answering.pressAt(0),
            testTheSyncAsksAboutRenamedLocomotives::saidDone);
    }

    /** The questions that name this locomotive. */
    private static List<Answering.Question> about(List<Answering.Question> questions, String name)
    {
        List<Answering.Question> named = new ArrayList<>();

        for (Answering.Question q : questions) if (q.message != null && q.message.contains(name)) named.add(q);

        return named;
    }

    /** The rename check's own lines about this offer, and its "nothing to rename", logged since the last take. */
    private static List<String> renameLines(StationRig rig, String old, String station)
    {
        List<String> lines = new ArrayList<>(rig.takeLog());

        lines.retainAll(Arrays.asList(I18n.f("loc.renameApplied", old, station), I18n.f("loc.renameKept", old, station),
            I18n.f("loc.renameKeptAsAnswered", old, station), I18n.f("loc.renameNotApplied", old, station),
            I18n.f("loc.renameClosed", old, station), I18n.t("loc.ui.infoNoLocomotivesToRename")));

        return lines;
    }

    /** Nothing renamed, kept as a No or copied under the station's name for this offer; its one log line is `key`. */
    private static void notRenamed(StationRig rig, String old, String station, String key)
    {
        assertNotNull(rig.model.getLocByName(old), old + " was renamed to " + station);
        assertNull(rig.model.getLocByName(old).getDeclinedStationName(), "the offer of " + station + " was kept as a"
            + " No");
        assertNull(rig.model.getLocByName(station), "the sync added a copy under " + station);
        assertEquals(renameLines(rig, old, station), Collections.singletonList(I18n.f(key, old, station)), "the offer"
            + " was not logged as " + key);
    }

    /** Whether the sync has said it is complete. */
    private static boolean saidDone(Answering answering)
    {
        Pattern done = Pattern.compile(Pattern.quote(I18n.t("ui.infoSyncCompleteItemsAdded")).replace("{0}",
            "\\E\\d+\\Q"));

        return answering.asked().stream().anyMatch(m -> done.matcher(m).matches());
    }

    /**
     * A delete the program refuses is not the user's No: while the locomotive holding the station's name is moving with
     * the power on, the rename onto it is refused without a question, logged as not renamed and kept nowhere, and once
     * it stops the next Sync asks the delete question.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, keep a No whenever the holder is still
     * there (drop the `answered == JOptionPane.NO_OPTION` test); or, in `LocomotiveActions.deleteLoc`, leave out the
     * `answered` reset, and an earlier No to another delete is kept.
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

            pressSync(rig, ui, m -> Answering.pressAt(0), testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertNull(rig.model.getLocByName(old).getDeclinedStationName(), "the refused delete was kept as a No");
            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f("loc.renameNotApplied",
                old, STATION_NAME)), "the refused delete was not logged as not renamed");

            holder.setSpeed(0);

            List<Answering.Question> asked = about(answeringNo(rig, ui, old), old);

            assertTrue(asked.stream().anyMatch(q -> q.message.startsWith(onto)), "the delete question was not asked"
                + " once the locomotive had stopped");
        });
    }

    /**
     * Closing the rename question without an answer renames nothing and keeps nothing: it is logged as closed, the sync
     * adds no copy under the station's name, and the next Sync asks the rename question again.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, keep a No for any answer but Yes, or
     * leave out `setHeldBackStationName` in the `finally`.
     */
    @Test(timeOut = 240000)
    public void testAClosedQuestionKeepsNothing() throws Exception
    {
        final String old = "FR-220 closed";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            renamedHere(rig, old);

            // No such button: the question is closed as its close box closes it
            List<Answering.Question> asked = about(pressSync(rig, ui, m -> m != null && m.contains(old)
                ? Answering.pressAt(99) : Answering.pressAt(0), testTheSyncAsksAboutRenamedLocomotives::saidDone), old);

            assertEquals(asked.size(), 1, "precondition: the rename was not asked");

            assertNotNull(rig.model.getLocByName(old), "closing the question renamed " + old);
            assertNull(rig.model.getLocByName(old).getDeclinedStationName(), "closing the question was kept as a No");
            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f("loc.renameClosed",
                old, STATION_NAME)), "closing the question was not logged as closed");
            assertNull(rig.model.getLocByName(STATION_NAME), "closing the question added a copy under the station's"
                + " name");

            List<Answering.Question> next = about(answeringNo(rig, ui, old), old);

            assertEquals(next.size(), 1, "the next Sync did not ask again");
            assertEquals(next.get(0).message, I18n.f("loc.ui.confirmRenameLocomotive", old, STATION_NAME),
                "the next Sync did not ask the rename question");
        });
    }

    /**
     * A closed rename question holds its offer back from its own sync alone: the sync an edit owes next treats the
     * offer as one never asked about, and adds the station's locomotive under the station's name.
     *
     * MUTATION: in `TrainControlUI.doSync`, leave out the `finally` that clears each held-back offer, and this fails.
     */
    @Test(timeOut = 240000)
    public void testAClosedQuestionHoldsItsOfferBackFromItsOwnSyncAlone() throws Exception
    {
        final String old = "FR-220 closed, then owed";

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            renamedHere(rig, old);

            assertEquals(about(pressSync(rig, ui, m -> m != null && m.contains(old) ? Answering.pressAt(99)
                : Answering.pressAt(0), testTheSyncAsksAboutRenamedLocomotives::saidDone), old).size(), 1,
                "precondition: the rename was not asked");
            assertNull(rig.model.getLocByName(STATION_NAME), "precondition: closing the question added a copy");

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
            {
                owedSync(rig, ui);
            }

            assertNotNull(rig.model.getLocByName(STATION_NAME), "the owed sync still held back the offer the closed"
                + " question's sync held back");
            assertNotNull(rig.model.getLocByName(old), "the owed sync renamed " + old);
        });
    }

    /**
     * Questions that end in a throw partway still hold their offers back from their own sync alone: here the second
     * offer's locomotive is renamed away while the first is asked, and the sync an edit owes next adds the first
     * offer's locomotive under the station's name.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component)`, leave out the catch's `addAll` of every locomotive, and
     * the first offer stays held back for the session.
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

            // MM2 address 16, which the station lists before BR 10 001, so it is asked first
            if (rig.model.getLocByName(firstAtTheStation) == null) rig.model.newMM2Locomotive(first, 16);
            else rig.model.renameLoc(firstAtTheStation, first);

            List<Answering.Question> asked = pressSync(rig, ui, m ->
            {
                if (m == null || !m.contains(first)) return Answering.pressAt(0);

                rig.model.renameLoc(gone, gone + " away");

                return Answering.pressAt(99);
            }, testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertEquals(about(asked, first).size(), 1, "precondition: the first offer was not asked");
            assertNull(rig.model.getLocByName(gone), "precondition: the second offer's locomotive is still there");
            assertNull(rig.model.getLocByName(firstAtTheStation), "precondition: closing the question added a copy");

            assertFalse(rig.model.getLocByName(first).keepsItsNameOver(firstAtTheStation), "the offer is still held back"
                + " after the sync");

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
            {
                owedSync(rig, ui);
            }

            assertNotNull(rig.model.getLocByName(firstAtTheStation), "the owed sync still held back the offer");
        });
    }

    /**
     * A Central Station multi-unit still lists a member whose rename was answered No: the station names the member by
     * the name turned down, which is the locomotive here that kept its own.
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

            assertEquals(about(answeringNo(rig, ui, old), old).size(), 1, "precondition: the rename was not asked");
            assertNull(rig.model.getLocByName(member), "precondition: the sync added a copy under the name turned down");

            assertEquals(rig.model.getLocByName(head).getModelMultiUnitLocomotives(), Collections.singletonList(
                rig.model.getLocByName(old)), "the multi-unit does not list the member that kept its own name");
        });
    }

    /**
     * Closing the delete question a rename onto a held name asks is logged as closed, and keeps nothing.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, leave out the `loc.renameClosed`
     * outcome of the delete question.
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

            pressSync(rig, ui, m -> m != null && m.contains(old) ? Answering.pressAt(99) : Answering.pressAt(0),
                testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertNull(rig.model.getLocByName(old).getDeclinedStationName(), "closing the delete question kept a No");
            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.f("loc.renameClosed", old,
                STATION_NAME)), "closing the delete question was not logged as closed");
        });
    }

    /**
     * A rename answered Yes but refused after, here for a station name with a comma no route condition could hold, is
     * logged as not renamed, and the sync adds no copy under that name: the next Sync offers the rename again.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, leave out `setHeldBackStationName`
     * in the `finally`.
     */
    @Test(timeOut = 240000)
    public void testARenameRefusedAfterYesAddsNoCopy() throws Exception
    {
        final String old = "FR-220 unusable";
        final String unusable = "BR 10, 001";
        final String yes = String.valueOf(TrainControlUI.YES_NO_OPTS[0]);
        final AtomicReference<String> stationName = new AtomicReference<>(STATION_NAME);

        inAWindow(renaming(stationName), (rig, ui) ->
        {
            renamedHere(rig, old);
            stationName.set(unusable);

            assertEquals(about(pressSync(rig, ui, m -> m != null && m.contains(old) ? Answering.press(yes)
                : Answering.pressAt(0), testTheSyncAsksAboutRenamedLocomotives::saidDone), old).size(), 1,
                "precondition: the rename was not asked");

            notRenamed(rig, old, unusable, "loc.renameNotApplied");
        });
    }

    /**
     * A run that begins after Sync is pressed and before the question stops the rename: it is not asked, the refusal is
     * said, nothing is renamed, kept or copied, and it is logged as not renamed.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, leave out `if (refused) continue;`.
     */
    @Test(timeOut = 240000)
    public void testARunBegunMeanwhileStopsTheRename() throws Exception
    {
        final String old = "FR-220 run";
        final CountDownLatch asked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean holding = new AtomicBoolean();
        inAWindow(holdingTheRenameRead(asked, release, holding), (rig, ui) ->
        {
            renamedHere(rig, old);

            rig.model.setNetworkCommState(true);
            holding.set(true);

            org.traincontrol.automation.Layout railway = new org.traincontrol.automation.Layout(rig.model);

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
            {
                SwingUtilities.invokeLater(() -> ui.doSync(ui));

                assertTrue(asked.await(60, TimeUnit.SECONDS), "the sync did not read the station's locomotives");

                // The run begins while the rename read waits on the station
                support.Reflect.set(MarklinControlStation.class, rig.model, "autoLayout", railway);
                railway.setStagingInProgress(true);
                release.countDown();

                assertTrue(Components.waitFor(() -> saidDone(answering), 60000), "the sync did not end: "
                    + answering.asked());
                assertTrue(syncEnded(ui), "a sync never ended");

                assertFalse(answering.asked().contains(I18n.f("loc.ui.confirmRenameLocomotive", old, STATION_NAME)),
                    "the rename was asked while a run was going");
                assertTrue(answering.asked().contains(I18n.t("autolayout.ui.errorCannotEditLocomotivesWhileRunning")),
                    "the run's refusal was not said: " + answering.asked());
            }
            finally
            {
                release.countDown();
                railway.setStagingInProgress(false);
                support.Reflect.set(MarklinControlStation.class, rig.model, "autoLayout", null);
            }

            notRenamed(rig, old, STATION_NAME, "loc.renameNotApplied");
        });
    }

    /**
     * Yes to a new name forgets the No kept for the old one and is logged as renamed: when the station later gives the
     * locomotive the name turned down before, that is asked about again.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, leave out
     * `l.setDeclinedStationName(null)` after the rename, or log `loc.renameNotApplied` in place of `loc.renameApplied`.
     */
    @Test(timeOut = 240000)
    public void testAYesForgetsTheNo() throws Exception
    {
        final String old = "FR-220 yes";
        final String renamed = STATION_NAME + " B";
        final String yes = String.valueOf(TrainControlUI.YES_NO_OPTS[0]);
        final AtomicReference<String> stationName = new AtomicReference<>(STATION_NAME);

        inAWindow(renaming(stationName), (rig, ui) ->
        {
            renamedHere(rig, old);

            assertEquals(about(answeringNo(rig, ui, old), old).size(), 1, "precondition: the rename was not asked");

            rig.takeLog();
            stationName.set(renamed);

            pressSync(rig, ui, m -> m != null && m.contains(old) ? Answering.press(yes) : Answering.pressAt(0),
                testTheSyncAsksAboutRenamedLocomotives::saidDone);

            assertNotNull(rig.model.getLocByName(renamed), "Yes did not rename " + old + " to " + renamed);
            assertEquals(renameLines(rig, old, renamed), Collections.singletonList(I18n.f("loc.renameApplied", old,
                renamed)), "the rename was not logged as renamed");

            stationName.set(STATION_NAME);

            assertEquals(about(answeringNo(rig, ui, renamed), renamed).size(), 1, "the name turned down before was not"
                + " asked about again after a Yes");
        });
    }

    /**
     * A No is forgotten once the station no longer offers the name: the station giving the locomotive its name here
     * logs "No locomotives to rename", and when it then offers the name turned down again, that is asked again.
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, keep the No of a locomotive the
     * station no longer offers a name for (empty the forgetting loop), or leave out the "nothing to rename" line.
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
            renamedHere(rig, old);

            assertEquals(about(answeringNo(rig, ui, old), old).size(), 1, "precondition: the rename was not asked");

            rig.takeLog();
            stationName.set(old);

            assertEquals(about(answeringNo(rig, ui, old), old).size(), 0, "a name the station has was asked about");
            assertEquals(renameLines(rig, old, STATION_NAME), Collections.singletonList(I18n.t(
                "loc.ui.infoNoLocomotivesToRename")), "nothing to rename was not logged");

            stationName.set(STATION_NAME);

            assertEquals(about(answeringNo(rig, ui, old), old).size(), 1, "the No was kept after the station stopped"
                + " offering the name");
        });
    }

    /**
     * A pressed Sync asks once about a locomotive held here at another address than the station's, Keep first, and
     * "Use address" gives it the station's (FR-246).
     *
     * MUTATION: in `MarklinControlStation.getRenameProposals`, put nothing in `addresses`, and nothing is asked.
     */
    @Test(timeOut = 180000)
    public void testAPressedSyncAsksAboutAnotherAddressAndUsesItWhenTold() throws Exception
    {
        String use = I18n.f("loc.ui.optionUseAddress", 10);
        String asks = I18n.f("loc.ui.askStationAddress", STATION_NAME, 10, 70);

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            movedHere(rig);

            askedOnce(about(pressing(rig, ui, use), STATION_NAME), asks, I18n.t("loc.ui.optionKeep"), use);

            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 10, "Use address did not give it the"
                + " station's address");
            assertEquals(rig.model.getLocList().stream().filter(STATION_NAME::equals).count(), 1L,
                "the sync added a copy under the station's name");
        });
    }

    /**
     * Keep leaves the address and is not asked again, across a restart, until the station's address for the
     * locomotive changes, which is asked about (FR-246).
     *
     * MUTATION: in `TrainControlUI.askAboutAddress`, leave out `setDeclinedStationAddress`, or in
     * `MarklinControlStation.newLocomotive(MarklinSimpleComponent)` leave out `setDeclinedStationAddress`.
     */
    @Test(timeOut = 300000)
    public void testAKeptAddressIsNotAskedAgainUntilTheStationsAddressChanges() throws Exception
    {
        final java.io.File root = Files.createTempDirectory("tc-sync-address").toFile();
        final AtomicInteger at = new AtomicInteger(10);
        final BiFunction<String, Integer, byte[]> cs3 = addressing(at);
        final String keep = I18n.t("loc.ui.optionKeep");

        inAWindow(root, cs3, (rig, ui) ->
        {
            movedHere(rig);

            assertEquals(about(pressing(rig, ui, keep), STATION_NAME).size(), 1, "precondition: the address was not"
                + " asked about");
            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 70, "Keep gave it the station's address");
            assertTrue(rig.takeLog().contains(I18n.f("loc.addressKept", STATION_NAME, 10, 70)), "the sync did not log"
                + " the address kept");

            assertNull(rig.model.saveState(false), "the saved data was not written");
        });

        inAWindow(root, cs3, (rig, ui) ->
        {
            assertEquals(about(pressing(rig, ui, keep), STATION_NAME).size(), 0, "a kept address was asked again");

            at.set(12);

            askedOnce(about(pressing(rig, ui, keep), STATION_NAME), I18n.f("loc.ui.askStationAddress", STATION_NAME, 12,
                70), keep, I18n.f("loc.ui.optionUseAddress", 12));
            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 70, "Keep gave it the station's address");
        });
    }

    /**
     * A locomotive held here at 10 that the station has at 3, while it calls the one at 10 another name, is one
     * question with three answers, Keep first, each doing only what it says: Rename leaves the station's locomotive at
     * 3 to be added, Use address 3 frees 10 for the station's locomotive there in the same sync, Keep changes nothing
     * and is not asked again (FR-246).
     *
     * MUTATION: in `TrainControlUI.askAboutRenames(Component, RenameProposals)`, drop the `askAboutAddress` branch of
     * the rename's question, and the plain rename is asked.
     */
    @Test(timeOut = 360000)
    public void testARenamedAndReAddressedLocomotiveIsOneQuestion() throws Exception
    {
        final String asks = I18n.f("loc.ui.askStationAddressOrName", AT_3, 3, 10, STATION_NAME);
        final String keep = I18n.t("loc.ui.optionKeep");
        final String rename = I18n.f("loc.ui.optionRenameTo", STATION_NAME);
        final String use = I18n.f("loc.ui.optionUseAddress", 3);

        inAWindow(StandInStation.cs3("260", INFO), (rig, ui) ->
        {
            renamedAndMovedHere(rig);

            askedOnce(about(pressing(rig, ui, rename), AT_3), asks, keep, rename, use);

            assertEquals(rig.model.getLocByName(STATION_NAME).getAddress(), 10, "Rename did not rename it");
            assertEquals(rig.model.getLocByName(AT_3).getAddress(), 3, "the station's locomotive at 3 was not added");

            rig.model.deleteLoc(AT_3);
            rig.model.renameLoc(STATION_NAME, AT_3);
            renamedAndMovedHere(rig);

            assertEquals(about(pressing(rig, ui, use), AT_3).size(), 1, "precondition: Use address was not asked");
            assertEquals(rig.model.getLocByName(AT_3).getAddress(), 3, "Use address did not move it");
            assertNotNull(rig.model.getLocByName(STATION_NAME), "the sync did not add the station's locomotive at the"
                + " address it left");

            renamedAndMovedHere(rig);

            assertEquals(about(pressing(rig, ui, keep), AT_3).size(), 1, "precondition: Keep was not asked");
            assertEquals(rig.model.getLocByName(AT_3).getAddress(), 10, "Keep moved it");
            assertNull(rig.model.getLocByName(STATION_NAME), "Keep renamed it or the sync added a copy");
            assertEquals(about(pressing(rig, ui, keep), AT_3).size(), 0, "Keep was asked again");
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

    /** Presses Sync pressing `button` on each question naming a locomotive of these claims, OK on the rest. */
    private static List<Answering.Question> pressing(StationRig rig, TrainControlUI ui, String button) throws Exception
    {
        return pressSync(rig, ui, m -> m != null && (m.contains(STATION_NAME) || m.contains(AT_3))
            ? Answering.press(button) : Answering.pressAt(0), testTheSyncAsksAboutRenamedLocomotives::saidDone);
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

    /**
     * Presses Sync with a station connected (renames are asked about only then) and answers by `rule` until `seen`
     * holds, then waits for every sync to end and gives back what was asked, the questions counted after the wait.
     */
    private static List<Answering.Question> pressSync(StationRig rig, TrainControlUI ui,
        java.util.function.Function<String, Answering.Answer> rule,
        java.util.function.Predicate<Answering> seen) throws Exception
    {
        rig.model.setNetworkCommState(true);

        try (Answering answering = Answering.start(rule))
        {
            SwingUtilities.invokeLater(() -> ui.doSync(ui));

            assertTrue(Components.waitFor(() -> seen.test(answering), 60000), "the sync did not ask about the rename: "
                + answering.asked());

            AskingWindows.awaitAnswered();

            assertTrue(syncEnded(ui), "a sync never ended");

            Components.settle();

            assertTrue(Settle.until(() -> seen.test(answering), 1000), "what was asked is gone");

            return new ArrayList<>(answering.questions());
        }
    }
}
