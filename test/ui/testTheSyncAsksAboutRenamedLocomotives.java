package ui;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

        inAWindow((rig, ui) ->
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

        inAWindow((rig, ui) ->
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

    private interface Claim
    {
        void run(StationRig rig, TrainControlUI ui) throws Exception;
    }

    /** The claim in the real window against the stand-in, once the window's start-up sync has ended. */
    private static void inAWindow(Claim claim) throws Exception
    {
        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-sync-renames").toFile(),
            StandInStation.cs3("260", INFO), true))
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
