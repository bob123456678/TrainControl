package regression;

import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * The Autonomy menu's Import, driven as Adam drives it, on a sandbox copy of his railway with his own 2.7.4c file (Adam,
 * 2026-09-25: automated tests supersede the MTs they answer).
 *
 * The door is `AutonomyViewerPanel.importConfiguration` - the file chooser, the name prompt, the replace question and the
 * messages it shows - answered from a thread of its own, as he would answer them.  What each import then says, logs and
 * leaves in the setup is what MT-491, MT-501, MT-502, MT-582 and MT-298 ask him to read.
 *
 * @author Adam
 */
public class testTheImportDoorReadsAnOldFile
{
    /** His 2.7.4c file, as the MTs hand it to him. */
    private static final File MT491 = new File("docs/manual-tests/files/MT-491-autonomy-2.7.4c.json");
    private static final File MT298 = new File("docs/manual-tests/files/MT-298-autonomy-2.7.4c.json");

    /**
     * An old autonomy.json imported from the menu into a new configuration: the message says it placed his four trains
     * (MT-491), the log says the 176 pieces of track it ran one way were left as the diagram has them and changes no
     * direction (MT-501), ParkingTrack7 arrives as a station trains can stop at and autonomy does not choose (MT-502), Load
     * Autonomy is as it was (MT-582) - and the configuration in use before is untouched (Adam, 2026-09-25: *"(a)"*).
     *
     * MUTATION: import into the configuration in use, untick Load Autonomy, carry the old file's directions, or place the
     * trains facing a guessed way, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnOldFileFromTheMenuGoesIntoANewConfiguration() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(MT491.isFile(), "precondition: the MT-491 file is not in docs/manual-tests/files");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        // HIS OWN PREFERENCE, put back whatever the import does to it: this check must not leave it changed.
        String loadAutonomyWas = TrainControlUI.getPrefs().get(TrainControlUI.AUTO_LOAD_AUTONOMY, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            assertNotNull(session, "precondition: the frozen railway opened no autonomy setup");

            String inUse = session.getStore().getActiveConfiguration();

            assertNotNull(inUse, "precondition: the frozen railway has no configuration in use");

            String inUseBefore = session.getStore().getConfiguration(inUse).toString();
            String directionsBefore = directionsHeld(session);
            String loadAutonomyBefore = TrainControlUI.getPrefs().get(TrainControlUI.AUTO_LOAD_AUTONOMY, "unset");

            List<String> said = importFromTheMenu(ui[0], MT491, "MT-491 import");

            session = ui[0].getAutonomySession();

            // INTO THE NEW CONFIGURATION, AND NOT THE ONE IN USE ("(a)") - which stays in use: importing is not a request
            // to switch (`configurationToLoadAfterImport`).
            assertTrue(session.getStore().getConfigurationNames().contains("MT-491 import"), "the import made no"
                + " configuration of the name given at the prompt: " + session.getStore().getConfigurationNames());

            assertEquals(session.getStore().getActiveConfiguration(), inUse, "the import switched away from " + inUse
                + ", the configuration in use");

            assertEquals(session.getStore().getConfiguration(inUse).toString(), inUseBefore, "the import wrote into "
                + inUse + ", the configuration in use, where another was named");

            // AND IT SAYS WHERE THEY WENT (RLA-C1): into the configuration named, with the one in use still in use - the
            // diagram shows none of the trains it says it placed.
            String where = I18n.f("autosetup.ui.infoLegacyImportedNotInUse", "MT-491 import", inUse, whereChosen(inUse));

            assertTrue(said.stream().anyMatch(message -> message.contains(where)), "the import's message does not name the"
                + " configuration its trains went into, or say " + inUse + " is still the one in use (RLA-C1): " + said);

            // MT-491: PLACED 4, AND FACED THE WAY THE OLD FILE RAN THEM.
            Integer placed = placedIn(said);

            assertEquals(placed, Integer.valueOf(4), "the import's message does not say it placed his 4 locomotives"
                + " (MT-491): " + said);

            String log = logged();

            assertFalse(log.contains(between(I18n.t("autosetup.ui.facingsGuessed"), "{0}")), "the log says trains had the"
                + " way they face chosen for them, where the file says which way all four ran (MT-491): " + log);

            assertFalse(log.contains(before(I18n.t("autosetup.ui.facingsNotHeld"), "{0}")), "the log says the old file"
                + " ran trains a way the diagram does not let them arrive (MT-491): " + log);

            // MT-501: THE DIAGRAM'S DIRECTIONS LEFT AS THEY ARE, AND SAID SO.
            assertTrue(log.contains(I18n.f("autosetup.ui.directionsNotCarried", 176)), "the log does not say the 176"
                + " pieces of track the old file ran one way were left as the diagram has them (MT-501): " + log);

            assertEquals(directionsHeld(session), directionsBefore, "the import changed the diagram's directions (MT-501)");

            // MT-502: PARKINGTRACK7 A STATION TRAINS CAN STOP AT, NOT CHOSEN BY AUTONOMY.
            TileKey parking = null;

            for (TileKey key : session.getStore().getNamedTiles())
            {
                if ("ParkingTrack7".equals(session.getStore().getPointName(key))) parking = key;
            }

            assertNotNull(parking, "precondition: no ParkingTrack7 on his railway");

            assertTrue(session.getStore().isStation(parking), "ParkingTrack7 is not a station trains can stop at after the"
                + " import (MT-502)");

            // READ IN THE IMPORTED CONFIGURATION, as its Station menu shows it once it is the one chosen.
            session.getStore().setActiveConfiguration("MT-491 import");

            try
            {
                assertFalse(session.isAutoDestination(parking), "ParkingTrack7, switched off in the old file, can be chosen"
                    + " in full autonomy in the imported configuration (MT-502)");

                // AND BECAUSE THE OLD FILE SWITCHED IT OFF, not only because it is a square trains turn at, which unticks
                // the same box: the translation itself, "on, but not one autonomy chooses" (REG-B1).
                assertEquals(session.getPointProperty(parking, org.traincontrol.automationui.AutonomyBuilder.AUTO_DESTINATION),
                    Boolean.FALSE, "the old file's switched-off ParkingTrack7 was not carried as a station autonomy does"
                    + " not choose (MT-502)");

                assertFalse(Boolean.FALSE.equals(session.getPointProperty(parking, "active")), "the old file's switched-off"
                    + " ParkingTrack7 arrived switched off, where it should arrive as one trains can stop at (MT-502)");

                assertEquals(session.placementsAutonomyWillWrite().size(), 4, "the imported configuration does not hold"
                    + " his four trains (MT-491): " + session.placementsAutonomyWillWrite());
            }
            finally
            {
                session.getStore().setActiveConfiguration(inUse);
            }

            // MT-582: LOAD AUTONOMY AS IT WAS.
            assertEquals(TrainControlUI.getPrefs().get(TrainControlUI.AUTO_LOAD_AUTONOMY, "unset"), loadAutonomyBefore,
                "the import changed Preferences > Startup > Load Autonomy (MT-582)");
        }
        finally
        {
            putTheFolderBack(folderWas);

            if (loadAutonomyWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.AUTO_LOAD_AUTONOMY);
            else TrainControlUI.getPrefs().put(TrainControlUI.AUTO_LOAD_AUTONOMY, loadAutonomyWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A second import of the same file, from the menu into the same configuration, keeps a change made by hand between the
     * two: it fills what is missing and does not replace what is there (MT-298).
     *
     * The change is a station's maximum train length, one the file has an opinion about, made the way the editor makes it.
     * The second import is answered Yes when the door asks whether to replace the configuration of that name.
     *
     * MUTATION: have the second import overwrite what is there, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testASecondImportFromTheMenuKeepsAHandMadeChange() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(MT298.isFile(), "precondition: the MT-298 file is not in docs/manual-tests/files");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            importFromTheMenu(ui[0], MT298, "MT-298 import");

            session = ui[0].getAutonomySession();

            assertTrue(session.getStore().getConfigurationNames().contains("MT-298 import"), "precondition: the first"
                + " import did not go into its own configuration");

            // THE HAND-MADE CHANGE IS MADE IN THE IMPORTED CONFIGURATION, chosen as the operator chooses it.
            final AutonomySession choosing = session;

            SwingUtilities.invokeAndWait(() ->
            {
                choosing.getStore().setActiveConfiguration("MT-298 import");
                choosing.rebuild();
            });

            // A STATION THE FILE HAS AN OPINION ABOUT: one it gave a maximum.
            TileKey named = null;

            for (TileKey square : session.getReducer().getPoints().keySet())
            {
                String station = session.getStore().getPointName(square);

                if (station != null && !station.trim().isEmpty() && session.getStore().isStation(square)
                    && maxOf(session, square) > 0)
                {
                    named = square;

                    break;
                }
            }

            if (named == null) throw new SkipException("the import gave no named station a maximum on this diagram");

            final TileKey changed = named;
            final int corrected = maxOf(session, changed) + 3;
            final AutonomySession editing = session;

            SwingUtilities.invokeAndWait(() ->
            {
                editing.setPointProperty(changed, "maxTrainLength", corrected);
                editing.getStore().setActiveConfiguration(inUse);
                editing.rebuild();
            });

            List<String> said = importFromTheMenu(ui[0], MT298, "MT-298 import");

            // ASKED WHAT IT DOES (RLA-B2, RLU-B1, RLD-C4): an old file fills gaps, so the door does not ask to replace.
            assertTrue(said.contains(I18n.f("autosetup.ui.confirmImportFillsGaps", "MT-298 import")), "importing an old"
                + " file into a configuration of that name did not ask whether to fill in what it does not have: " + said);

            assertFalse(said.contains(I18n.f("autosetup.ui.confirmImportOverwrites", "MT-298 import")), "importing an old"
                + " file into a configuration of that name asked to replace it, and then filled gaps: " + said);

            session = ui[0].getAutonomySession();

            session.getStore().setActiveConfiguration("MT-298 import");

            try
            {
                assertEquals(maxOf(session, changed), corrected, "the second import from the menu replaced the maximum set"
                    + " by hand between the two (MT-298): " + said);
            }
            finally
            {
                session.getStore().setActiveConfiguration(inUse);
            }
        }
        finally
        {
            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * A second import of the same file, into a configuration where one of its trains has been moved since, does not stand
     * that train on two squares: it stays where it was moved to, and the message says it was not placed (RLA-B2).
     *
     * One locomotive stands in one place was kept across the file only, so the square the train had left read as a gap
     * and the import put it there as well - and a configuration naming one train on two squares is refused whole
     * (`autosetup.ui.checkDuplicateLocomotive`).
     *
     * MUTATION: start the one-place check empty again, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testASecondImportDoesNotStandATrainTwice() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(MT298.isFile(), "precondition: the MT-298 file is not in docs/manual-tests/files");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            importFromTheMenu(ui[0], MT298, "MT-298 import");

            AutonomySession session = ui[0].getAutonomySession();

            org.json.JSONObject points = session.getStore().getConfiguration("MT-298 import").getJSONObject("points");

            // ONE OF ITS TRAINS MOVED, in that configuration, to a station with none.
            String from = null;
            String train = null;

            for (String square : points.keySet())
            {
                org.json.JSONObject extras = points.optJSONObject(square);

                if (extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE))
                {
                    from = square;
                    train = extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).getString("name");

                    break;
                }
            }

            assertNotNull(from, "precondition: the first import placed no train");

            String to = null;

            for (TileKey square : session.getReducer().getPoints().keySet())
            {
                org.json.JSONObject extras = points.optJSONObject(square.toString());

                if (!square.toString().equals(from) && session.getStore().isStation(square)
                    && (extras == null || !extras.has(AutonomyBuilder.LOCOMOTIVE)))
                {
                    to = square.toString();

                    break;
                }
            }

            assertNotNull(to, "precondition: no empty station to move " + train + " to");

            Object standing = points.getJSONObject(from).remove(AutonomyBuilder.LOCOMOTIVE);

            if (!points.has(to)) points.put(to, new org.json.JSONObject());

            points.getJSONObject(to).put(AutonomyBuilder.LOCOMOTIVE, standing);

            List<String> said = importFromTheMenu(ui[0], MT298, "MT-298 import");

            session = ui[0].getAutonomySession();

            points = session.getStore().getConfiguration("MT-298 import").getJSONObject("points");

            List<String> standsOn = new ArrayList<>();

            for (String square : points.keySet())
            {
                org.json.JSONObject extras = points.optJSONObject(square);

                if (extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE)
                    && train.equals(extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")))
                {
                    standsOn.add(square);
                }
            }

            assertEquals(standsOn, Collections.singletonList(to), "the second import stood " + train + ", moved from "
                + from + " to " + to + " since the first, on " + standsOn + " - one train in two places, which refuses the"
                + " whole configuration (RLA-B2)");

            String notPlaced = I18n.f("autosetup.ui.infoLegacyAlreadyPlaced", train);

            assertTrue(said.stream().anyMatch(message -> message.contains(notPlaced)), "the import did not say it left "
                + train + " where the configuration already has it: " + said);
        }
        finally
        {
            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * An old file the import cannot finish reading leaves the setup as it was: no configuration made of it, the one in use
     * still chosen, and nothing of it saved (RLA-C3, RLU-C1).
     *
     * The door chose the new configuration before reading, and saved before the last of its reads - so a file whose
     * timetable was not a list was imported, saved with its configuration chosen, and then reported as unreadable, and the
     * next start resumed that configuration instead of the one that had been running.
     *
     * MUTATION: save before the file is read to its end, or leave the new configuration chosen on a failure, and this
     * fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAFileItCannotReadLeavesTheSetupAsItWas() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(MT491.isFile(), "precondition: the MT-491 file is not in docs/manual-tests/files");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        // HIS FILE, WITH A TIMETABLE THAT IS NOT A LIST - as a hand edit can leave it.
        File broken = File.createTempFile("tc-broken-autonomy", ".json");

        org.json.JSONObject file = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(MT491.toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        file.put("timetable", new org.json.JSONObject());

        java.nio.file.Files.write(broken.toPath(), file.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            String inUse = session.getStore().getActiveConfiguration();

            assertNotNull(inUse, "precondition: the frozen railway has no configuration in use");

            List<String> said = importFromTheMenu(ui[0], broken, "MT-491 broken");

            String unreadable = before(I18n.t("autosetup.ui.errorImportUnreadable"), "{0}");

            assertTrue(said.stream().anyMatch(message -> message.startsWith(unreadable)), "precondition: the import did"
                + " not say the file could not be read: " + said);

            session = ui[0].getAutonomySession();

            assertFalse(session.getStore().getConfigurationNames().contains("MT-491 broken"), "a file the import could not"
                + " read left a configuration made of it: " + session.getStore().getConfigurationNames());

            assertEquals(session.getStore().getActiveConfiguration(), inUse, "a file the import could not read left its"
                + " configuration chosen, in place of " + inUse + " (RLA-C3, RLU-C1)");

            assertEquals(setupOnDisk(session).optString("activeConfiguration"), inUse, "a file the import could not read"
                + " was saved as the configuration the next start resumes (RLA-C3, RLU-C1)");
        }
        finally
        {
            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();

            if (!broken.delete()) broken.deleteOnExit();
        }
    }

    /**
     * An import while trains are moving, whose reload is then declined, leaves the configuration in use as the one the
     * next start resumes (RLD-C3).
     *
     * The door saved with the imported configuration chosen and put the running one back only by reloading it - so
     * answering No to "reloading stops running locomotives" left setup.json naming the imported one.
     *
     * The trains are "moving" as a Return Home in progress makes them: the flag the window asks.
     *
     * MUTATION: save with the imported configuration chosen, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testADeclinedReloadLeavesHisConfigurationChosen() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(MT491.isFile(), "precondition: the MT-491 file is not in docs/manual-tests/files");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field staging = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        staging.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            String inUse = session.getStore().getActiveConfiguration();

            assertNotNull(inUse, "precondition: the frozen railway has no configuration in use");

            staging.set(ui[0], true);

            String stopsThem = I18n.t("autolayout.ui.confirmReloadJsonStopsRunningLocomotives");

            List<String> said = importFromTheMenu(ui[0], MT491, "MT-491 declined", Collections.singleton(stopsThem));

            assertTrue(said.contains(stopsThem), "precondition: the reload never asked whether to stop the trains: " + said);

            session = ui[0].getAutonomySession();

            assertTrue(session.getStore().getConfigurationNames().contains("MT-491 declined"), "precondition: the import made"
                + " no configuration of the name given");

            assertEquals(setupOnDisk(session).optString("activeConfiguration"), inUse, "with the reload declined, the"
                + " setup the next start resumes names the imported configuration in place of " + inUse + " (RLD-C3)");
        }
        finally
        {
            if (ui[0] != null) staging.set(ui[0], false);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * An old file imported into the configuration in use, by its name, keeps what it brought: the reload after it does
     * not write the running railway back over it (RLA-C2).
     *
     * The reload captured the running layout into that configuration first, and the capture takes a train off every
     * square where the running layout has none standing - so a train the import had just placed, and counted, was gone
     * again.  Homes went the same way.  The running layout's state now goes in before the import, which then fills only
     * what is left, and the reload does not capture again.
     *
     * MUTATION: reload after the import with the capture, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnImportIntoTheConfigurationInUseKeepsWhatItBrought() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(MT491.isFile(), "precondition: the MT-491 file is not in docs/manual-tests/files");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertNotNull(inUse, "precondition: the frozen railway has no configuration in use");

            Set<String> before = standingIn(session, inUse);

            List<String> said = importFromTheMenu(ui[0], MT491, inUse);

            Integer placed = placedIn(said);

            assertNotNull(placed, "precondition: the import said nothing about what it placed: " + said);

            assertTrue(placed > 0, "precondition: the import placed none of the file's trains in " + inUse + ", so there"
                + " is nothing for the reload to take back: " + said);

            session = ui[0].getAutonomySession();

            assertEquals(session.getStore().getActiveConfiguration(), inUse, "precondition: the import left " + inUse
                + " no longer the one chosen");

            Set<String> after = standingIn(session, inUse);

            Set<String> brought = new java.util.TreeSet<>(after);

            brought.removeAll(before);

            assertTrue(after.containsAll(before), "the import into " + inUse + " took off a train that stood there: "
                + before + " then " + after);

            assertEquals(brought.size(), placed.intValue(), "the import said it placed " + placed + " in " + inUse + ", and"
                + " after the reload " + inUse + " has " + brought + " more - the reload wrote the running railway back"
                + " over what the import brought (RLA-C2)");
        }
        finally
        {
            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The locomotives a configuration has standing somewhere. */
    private static Set<String> standingIn(AutonomySession session, String configuration)
    {
        Set<String> out = new java.util.TreeSet<>();

        org.json.JSONObject points = session.getStore().getConfiguration(configuration).optJSONObject("points");

        if (points == null) return out;

        for (String square : points.keySet())
        {
            org.json.JSONObject extras = points.optJSONObject(square);

            if (extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE))
            {
                out.add(extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name"));
            }
        }

        return out;
    }

    // ---------------------------------------------------------------- the door

    /** The main window over the sandbox, settled. */
    private static TrainControlUI openTheWindow() throws Exception
    {
        MarklinControlStation model = MarklinControlStation.init(null, true, false, false, true);

        final TrainControlUI[] made = new TrainControlUI[1];

        SwingUtilities.invokeAndWait(() -> made[0] = new TrainControlUI());

        made[0].setViewListener(model, new CountDownLatch(1));

        final CountDownLatch settled = new CountDownLatch(1);

        made[0].whenTilesSettled(() -> settled.countDown());

        settled.await(30, TimeUnit.SECONDS);

        for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });

        assertNotNull(made[0].getAutonomyViewerPanel(), "precondition: the window has no autonomy panel to import from");

        return made[0];
    }

    /**
     * Presses Import on the Autonomy menu's panel and answers everything it asks: the file, the name, Yes to replacing a
     * configuration of that name, and OK to every message - whose texts are returned, in order.
     */
    private static List<String> importFromTheMenu(TrainControlUI ui, File file, String name) throws Exception
    {
        return importFromTheMenu(ui, file, name, Collections.<String>emptySet());
    }

    /**
     * The same, answering No where the question is one of these.
     */
    private static List<String> importFromTheMenu(TrainControlUI ui, File file, String name, Set<String> no)
        throws Exception
    {
        Answerer answerer = new Answerer(file.getAbsoluteFile(), name, no);

        Thread answering = new Thread(answerer, "import door answerer");

        answering.setDaemon(true);
        answering.start();

        final CountDownLatch done = new CountDownLatch(1);

        try
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    ui.getAutonomyViewerPanel().importConfiguration();
                }
                finally
                {
                    done.countDown();
                }
            });

            assertTrue(done.await(180, TimeUnit.SECONDS), "the import did not finish; showing: " + answerer.said);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(answerer.chose, "precondition: the import never showed the file chooser");
            assertTrue(answerer.named, "precondition: the import never asked for the configuration's name");

            return new ArrayList<>(answerer.said);
        }
        finally
        {
            answerer.running = false;
        }
    }

    /** Answers the import's dialogs as they appear. */
    private static final class Answerer implements Runnable
    {
        private final File file;
        private final String name;
        private final Set<String> no;
        private final List<String> said = Collections.synchronizedList(new ArrayList<>());
        private final Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private volatile boolean running = true;
        private volatile boolean chose = false;
        private volatile boolean named = false;

        Answerer(File file, String name, Set<String> no)
        {
            this.file = file;
            this.name = name;
            this.no = no;
        }

        @Override
        public void run()
        {
            while (running)
            {
                try
                {
                    Thread.sleep(150);
                }
                catch (InterruptedException stop)
                {
                    return;
                }

                for (Window window : Window.getWindows())
                {
                    if (!window.isShowing() || !(window instanceof JDialog)) continue;

                    Container content = ((JDialog) window).getContentPane();

                    final JFileChooser chooser = find(content, JFileChooser.class);

                    if (chooser != null)
                    {
                        if (!handled.add(chooser)) continue;

                        chose = true;

                        SwingUtilities.invokeLater(() ->
                        {
                            chooser.setSelectedFile(file);
                            chooser.approveSelection();
                        });

                        continue;
                    }

                    final JOptionPane pane = find(content, JOptionPane.class);

                    if (pane == null || !handled.add(pane)) continue;

                    final String text = String.valueOf(pane.getMessage());

                    if (I18n.t("autosetup.ui.promptImportName").equals(text))
                    {
                        named = true;

                        SwingUtilities.invokeLater(() ->
                        {
                            pane.setInputValue(name);
                            pane.setValue(Integer.valueOf(JOptionPane.OK_OPTION));
                        });

                        continue;
                    }

                    said.add(text);

                    final Object[] options = pane.getOptions();

                    final int answer = no.contains(text) ? 1 : 0;

                    SwingUtilities.invokeLater(() -> pane.setValue(options != null && options.length > answer
                        ? options[answer] : Integer.valueOf(JOptionPane.OK_OPTION)));
                }
            }
        }
    }

    /** The first component of a type inside a container, however deep. */
    private static <T> T find(Container container, Class<T> type)
    {
        for (Component child : container.getComponents())
        {
            if (type.isInstance(child)) return type.cast(child);

            if (child instanceof Container)
            {
                T deeper = find((Container) child, type);

                if (deeper != null) return deeper;
            }
        }

        return null;
    }

    // ---------------------------------------------------------------- what it said

    /** The locomotives the import's message says it placed - its {1} - or null where no message is the import's. */
    private static Integer placedIn(List<String> said)
    {
        String template = I18n.t("autosetup.ui.infoLegacyImported");

        StringBuilder regex = new StringBuilder();

        Matcher slot = Pattern.compile("\\{(\\d)\\}").matcher(template);

        int from = 0;
        int placedGroup = -1;
        int group = 0;

        while (slot.find())
        {
            regex.append(Pattern.quote(template.substring(from, slot.start())));
            regex.append("([0-9.,\\u00a0]+)");

            group++;

            if ("1".equals(slot.group(1))) placedGroup = group;

            from = slot.end();
        }

        regex.append(Pattern.quote(template.substring(from)));

        Pattern pattern = Pattern.compile(regex.toString(), Pattern.DOTALL);

        for (String message : said)
        {
            Matcher m = pattern.matcher(message);

            if (m.lookingAt() && placedGroup > 0) return Integer.valueOf(m.group(placedGroup).replaceAll("[^0-9]", ""));
        }

        return null;
    }

    /** The text of a sentence before a placeholder. */
    private static String before(String sentence, String placeholder)
    {
        return sentence.substring(0, sentence.indexOf(placeholder)).trim();
    }

    /** The text of a sentence after a placeholder, up to its first full stop or comma. */
    private static String between(String sentence, String placeholder)
    {
        String after = sentence.substring(sentence.indexOf(placeholder) + placeholder.length());

        int stop = after.indexOf(',');

        return (stop > 0 ? after.substring(0, stop) : after).trim();
    }

    /** The diagram's directions, as the setup holds them. */
    private static String directionsHeld(AutonomySession session)
    {
        return String.valueOf(session.snapshotSetup().getJSONObject("shared").opt("tileDirections"));
    }

    /** A square's maximum train length in the configuration in use, 0 for none. */
    private static int maxOf(AutonomySession session, TileKey square)
    {
        Object value = session.getPointProperty(square, "maxTrainLength");

        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /** Where the Autonomy menu offers the configurations to choose from, as its labels read with this one chosen. */
    private static String whereChosen(String chosen)
    {
        return I18n.t("autosetup.ui.menuAutonomy") + " > " + I18n.f("autosetup.ui.menuConfigurations", chosen);
    }

    /** The setup file as the store last saved it - what the next start reads. */
    private static org.json.JSONObject setupOnDisk(AutonomySession session) throws Exception
    {
        java.lang.reflect.Method setupFile = session.getStore().getClass().getDeclaredMethod("setupFile");

        setupFile.setAccessible(true);

        File file = (File) setupFile.invoke(session.getStore());

        return new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),
            java.nio.charset.StandardCharsets.UTF_8));
    }

    /** The chooser remembers the folder a file was chosen in; put the operator's back. */
    private static void putTheFolderBack(String folderWas)
    {
        if (folderWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_FOLDER);
        else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, folderWas);
    }

    // ---------------------------------------------------------------- the log

    /** What the model has logged since this class was loaded - every line the operator's log shows. */
    private static final StringBuilder LOGGED = new StringBuilder();

    static
    {
        java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).addHandler(
            new java.util.logging.Handler()
            {
                @Override
                public void publish(java.util.logging.LogRecord record)
                {
                    synchronized (LOGGED)
                    {
                        LOGGED.append(record.getMessage()).append('\n');
                    }
                }

                @Override
                public void flush()
                {
                }

                @Override
                public void close()
                {
                }
            });
    }

    private static String logged() throws Exception
    {
        for (int turn = 0; turn < 3; turn++) SwingUtilities.invokeAndWait(() -> { });

        synchronized (LOGGED)
        {
            return LOGGED.toString();
        }
    }
}
