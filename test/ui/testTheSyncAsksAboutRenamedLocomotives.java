package ui;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import support.Answering;
import support.AskingWindows;
import support.Components;
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

            List<String> asked = pressSync(rig, ui, m -> rename.equals(m) ? Answering.press(yes) : Answering.pressAt(0),
                answering -> answering.asked().contains(rename));

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
     * a held name and the deletion without a question of its own (`deleteLocAlreadyAsked`), and this fails.
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
        });
    }

    /**
     * The rename read waits on the station behind the busy sign, off the event thread (OB-475): while a slow station
     * holds back its locomotive list the window still answers with the busy sign up, and once the list arrives the sync
     * goes on and ends.
     *
     * MUTATION: in `TrainControlUI.doSync`, call `askAboutRenames(c)` on the event thread, just before the worker starts,
     * instead of inside it.
     */
    @Test(timeOut = 180000)
    public void testTheRenameReadLeavesTheWindowFreeWhileTheStationIsSlow() throws Exception
    {
        final CountDownLatch asked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean holding = new AtomicBoolean();
        final BiFunction<String, Integer, byte[]> cs3 = StandInStation.cs3("260", INFO);

        inAWindow((path, n) ->
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
        }, (rig, ui) ->
        {
            rig.model.setNetworkCommState(true);
            holding.set(true);

            try (Answering answering = Answering.start(m -> Answering.pressAt(0)))
            {
                SwingUtilities.invokeLater(() -> ui.doSync(ui));

                assertTrue(asked.await(60, TimeUnit.SECONDS), "the sync did not read the station's locomotives");

                List<String> signs = Components.busySignsIfAnswering(3000);

                release.countDown();

                assertNotNull(signs, "the window froze while the rename read waited on the station");
                assertFalse(signs.isEmpty(), "no busy sign showed while the rename read waited on the station");

                assertTrue(Components.waitFor(() -> saidDone(answering), 60000), "the sync did not end once the list"
                    + " arrived: " + answering.asked());

                assertTrue(syncEnded(ui), "a sync never ended");
            }
            finally
            {
                release.countDown();
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
     * station as it is, asks nothing, and reads the station's locomotives once, for itself.
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
            int syncs = rig.station.requests("/config/geraet.vrs");

            try (Answering answering = Answering.start(m -> m != null && m.contains(old) ? Answering.press(no)
                : Answering.pressAt(0)))
            {
                // As `editorGone` runs it: a sync owed by an edit, with no window to tell
                ((AtomicBoolean) support.Reflect.field(TrainControlUI.class, "syncOwed").get(ui)).set(true);

                SwingUtilities.invokeLater(() -> ui.doSync(null));

                assertTrue(Settle.until(() -> rig.station.requests("/config/geraet.vrs") > syncs, 60000),
                    "the owed sync never ran");

                assertTrue(syncEnded(ui), "the owed sync never ended");

                Components.settle();

                assertFalse(answering.asked().stream().anyMatch(m -> m.contains(old)), "the owed sync asked about the"
                    + " rename: " + answering.asked());
            }

            assertEquals(rig.station.requests("/app/api/locos") - locos, 1, "the owed sync read the station's"
                + " locomotives twice");

            assertNotNull(rig.model.getLocByName(old), "the owed sync renamed " + old);
        });
    }

    /** Whether the sync has said it is complete. */
    private static boolean saidDone(Answering answering)
    {
        Pattern done = Pattern.compile(Pattern.quote(I18n.t("ui.infoSyncCompleteItemsAdded")).replace("{0}",
            "\\E\\d+\\Q"));

        return answering.asked().stream().anyMatch(m -> done.matcher(m).matches());
    }

    private interface Claim
    {
        void run(StationRig rig, TrainControlUI ui) throws Exception;
    }

    /** The claim in the real window against a stand-in answering so, once the window's start-up sync has ended. */
    private static void inAWindow(BiFunction<String, Integer, byte[]> answer, Claim claim) throws Exception
    {
        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-sync-renames").toFile(), answer, true))
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
    private static List<String> pressSync(StationRig rig, TrainControlUI ui,
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

            return new ArrayList<>(answering.asked());
        }
    }
}
