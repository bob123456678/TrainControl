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
 * The door is `AutonomyViewerPanel.importConfiguration` - the file chooser, the name prompt, the question about a configuration of that name and the
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

            // MT-582 STEP 1: LOAD AUTONOMY TICKED - read as found, an untick on a machine where it is already off would
            // leave it as it was, and pass (RLD-C5).  Put back in the finally.
            TrainControlUI.getPrefs().putBoolean(TrainControlUI.AUTO_LOAD_AUTONOMY, true);

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

            // MT-582: LOAD AUTONOMY STILL TICKED, AND THE LOG SAYS NOTHING ABOUT IT.
            assertTrue(TrainControlUI.getPrefs().getBoolean(TrainControlUI.AUTO_LOAD_AUTONOMY, false), "the import"
                + " unticked Preferences > Startup > Load Autonomy (MT-582)");

            String loadAutonomy = I18n.t("ui.main.toolbar.loadAutonomy");

            assertFalse(log.contains(loadAutonomy), "the import's log says something about " + loadAutonomy + " (MT-582): "
                + log);
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
     * The second import is answered Yes when the door asks whether to add what the file has and the configuration
     * does not.
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

            // THE LOG FROM HERE ON, the class's other imports left out - as text, and entry by entry
            final int logFrom = logged().length();
            final int entryFrom = loggedEntries().size();

            List<String> first = importFromTheMenu(ui[0], MT298, "MT-298 import");

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
            // AND SAYS WHAT COUNTS AS NOT SET (RLA3-B1; Adam, 2026-09-28): a setting at its default, a home taken off or
            // an emptied exclusion list reads as a gap, and takes the file's
            // THE WHOLE QUESTION, since `said` holds whole dialogs (RLV12-C6 added its third sentence)
            assertTrue(said.contains(I18n.f("autosetup.ui.confirmImportFillsGaps", "MT-298 import") + "  "
                + I18n.t("autosetup.ui.importTakesTheFilesForDefaults") + "  "
                + I18n.f("autosetup.ui.importKeepsTheStationMaximums", "MT-298 import")), "importing an old file into a configuration of"
                + " that name did not ask whether to fill in what it does not have, saying what counts as not having it"
                + " (RLA3-B1): " + said);

            // AND THE ONE SETTING A DEFAULT DOES NOT GIVE UP (RLV12-C6): every station of a configuration that has been
            // loaded records its maximum, no limit included, so the file's maximum is kept out
            assertTrue(said.stream().anyMatch(q -> q.contains(I18n.t("autosetup.ui.importTakesTheFilesForDefaults") + "  "
                + I18n.f("autosetup.ui.importKeepsTheStationMaximums", "MT-298 import"))), "the question said a setting at"
                + " its default takes the file's value, and did not say a station's maximum is kept (RLV12-C6): " + said);

            assertFalse(said.contains(I18n.f("autosetup.ui.confirmImportOverwrites", "MT-298 import")), "importing an old"
                + " file into a configuration of that name asked to replace it, and then filled gaps: " + said);

            // AND COUNTS IN SQUARES (RSA45-C1): every square the first import met has a name now, and is counted once in {4}
            // however many old points stand on it; the old points that shared a square are said again
            String imported = "autosetup.ui.infoLegacyImported", shared = "autosetup.ui.importSharedSquares";

            Integer namedThen = numberIn(first, imported, 0), hadThen = numberIn(first, imported, 4);
            Integer namedNow = numberIn(said, imported, 0), hadNow = numberIn(said, imported, 4);

            assertNotNull(namedThen, "precondition: the first import's message is not the import's: " + first);
            assertNotNull(namedNow, "precondition: the second import's message is not the import's: " + said);

            int sharedThen = countIn(first, shared) == null ? 0 : countIn(first, shared);
            int sharedNow = countIn(said, shared) == null ? 0 : countIn(said, shared);

            assertTrue(sharedThen > 0, "the first import's message does not say the old points that shared a square (RSA44-C3,"
                + " RSA45-C1): " + first);

            // HIS RAILWAY HAS ITS NAMES, so the first import meets squares named before it - and counts them, each once
            assertTrue(hadThen > 0, "the first import into his named railway says no square already had a name (RSA45-C1): "
                + first);

            assertEquals(namedNow, Integer.valueOf(0), "precondition: the second import named a square: " + said);

            assertEquals(hadNow.intValue(), namedThen + hadThen, "the second import's message says " + hadNow + " squares"
                + " already had a name, where the first met " + (namedThen + hadThen) + " - a square counted once for each"
                + " old point on it (RSA45-C1): " + said);

            assertEquals(sharedNow, sharedThen, "the second import's message does not say again the old points that shared a"
                + " square (RSA45-C1): " + said);

            // AND THE LOG LISTS THEM (RSA45-C2): under its heading, one line for each point the two messages counted
            String log = logged().substring(logFrom);

            assertTrue(log.contains(I18n.t("autosetup.ui.importSharedSquaresHeading")), "the log has no heading for the old"
                + " points that shared a square (RSA44-C3): " + log);

            assertEquals(sharedLinesIn(log), sharedThen + sharedNow, "the log does not list each old point the messages say"
                + " shared a square (RSA44-C3): " + log);

            // AND EACH LIST ONE ENTRY, ITS HEADING FIRST (Adam, 2026-10-06: "Fix them", on RSA45's notes): the main window's
            // log puts its newest entry on top, so a heading logged as an entry of its own before its lines stood below
            // them, and the list read upwards.  One entry is put on top whole, and reads down from its heading.
            List<String> entries = loggedEntries().subList(entryFrom, loggedEntries().size());

            String sharedEntry = lastStartingWith(entries, I18n.t("autosetup.ui.importSharedSquaresHeading"));

            assertNotNull(sharedEntry, "the log has no entry for the old points that shared a square: " + entries);

            assertEquals(sharedEntry.split("\n", -1).length - 1, sharedNow, "the log's entry for the old points that shared"
                + " a square does not hold the " + sharedNow + " its message counts under its heading - an entry each, which"
                + " the log shows above the heading: " + sharedEntry);

            Integer leftNow = countIn(said, "autosetup.ui.leftBehind");

            assertNotNull(leftNow, "precondition: the second import left nothing behind, so its list cannot be read: " + said);

            String leftEntry = lastStartingWith(entries, I18n.t("autosetup.ui.leftBehindHeading"));

            assertNotNull(leftEntry, "the log has no entry for what the import left behind: " + entries);

            assertEquals(leftEntry.split("\n", -1).length - 1, leftNow.intValue(), "the log's entry for what the import"
                + " left behind does not hold the " + leftNow + " its message counts under its heading - an entry each,"
                + " which the log shows above the heading: " + leftEntry);

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
     * An import while autonomy runs is refused before it asks for anything, whatever it would be imported into, as
     * Delete beside it is (RSA4-C5; Adam, 2026-09-28: *"There should be no setup edit possible during a run"*).
     *
     * Into a configuration not in use it went on while trains ran: a bundle, or an old file, fills the gaps of the
     * setup's shared half - a station's name among them - and saves, and the reload it then asks may be declined.
     * Nothing marked the setup newer than the running railway, so the next fold read the running railway through names
     * the import had changed, and a train on a square it renamed was written nowhere.  Refused with it is the case
     * RLD-C3 was: a declined reload after an import while trains moved, which left the imported configuration chosen
     * for the next start.
     *
     * The trains are "moving" as a Return Home in progress makes them: the flag the window asks.
     *
     * MUTATION: let an import into another configuration in while autonomy runs, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnImportIsRefusedWhileAutonomyRuns() throws Exception
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

            final String inUse = session.getStore().getActiveConfiguration();

            assertNotNull(inUse, "precondition: the frozen railway has no configuration in use");

            String before = session.getStore().getConfiguration(inUse).toString();
            List<String> namesBefore = new ArrayList<>(session.getStore().getConfigurationNames());
            String onDiskBefore = setupOnDisk(session).toString();

            staging.set(ui[0], true);

            Answerer asked = pressImport(ui[0], MT491, "MT-491 while running", Collections.<String>emptySet());

            session = ui[0].getAutonomySession();

            assertTrue(asked.said.contains(I18n.t("autosetup.ui.errorImportWhileRunning")), "an old file was imported"
                + " into another configuration while autonomy ran (RSA4-C5), or refused without saying what to do"
                + " instead: " + asked.said);

            assertFalse(asked.chose, "the import asked for a file it then refused, where Delete refuses at once");

            assertEquals(new ArrayList<>(session.getStore().getConfigurationNames()), namesBefore, "an import refused"
                + " left a configuration behind");

            assertEquals(session.getStore().getConfiguration(inUse).toString(), before, "an import refused changed "
                + inUse);

            assertEquals(setupOnDisk(session).toString(), onDiskBefore, "an import refused changed the setup on disk");
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
     * A bundle imported into the configuration in use, by its name, is refused and changes nothing: it is imported under
     * another name and chosen from there (RLA2-B1, RLD3-C2).  The case is the backup round trip: Export suggests the
     * configuration's name and Import suggests the file's.
     *
     * A bundle replaces the configuration it is imported into, and the reload after it captured the running railway
     * straight back over its settings and timetable - so "Replace it?" answered Yes did not replace; and its placements
     * are not where the running railway's trains stand, which is the railway's to say (OB-183).  An old file under that
     * name is not refused: it fills gaps and places no train (RLD3-C1), as the claims below say.
     *
     * MUTATION: import into the configuration in use again, or refuse and then ask to replace it anyway (RLD4-C4), and
     * this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnImportIntoTheConfigurationInUseIsRefused() throws Exception
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

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not the"
                + " configuration running");

            String before = session.getStore().getConfiguration(inUse).toString();
            List<String> namesBefore = new ArrayList<>(session.getStore().getConfigurationNames());
            String onDiskBefore = setupOnDisk(session).toString();

            // HIS OWN CONFIGURATION, EXPORTED AS A BUNDLE - as Export writes it.
            File bundle = File.createTempFile("tc-bundle", ".json");

            bundle.deleteOnExit();

            java.nio.file.Files.write(bundle.toPath(), session.getStore().exportBundle(inUse).toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

            List<String> said = importFromTheMenu(ui[0], bundle, inUse);

            session = ui[0].getAutonomySession();

            assertEquals(session.getStore().getConfiguration(inUse).toString(), before, "an import into " + inUse
                + ", the configuration in use, by its name, changed it: " + said);

            assertEquals(new ArrayList<>(session.getStore().getConfigurationNames()), namesBefore, "an import refused"
                + " left a configuration behind");

            assertEquals(setupOnDisk(session).toString(), onDiskBefore, "an import refused changed the setup on disk");

            String refused = I18n.f("autosetup.ui.errorImportIntoConfigurationInUse", inUse, whereChosen(inUse));

            assertTrue(said.contains(refused), "the door did not say it will not import into the configuration in use,"
                + " and what to do instead: " + said);

            // A bundle of the configuration itself changes nothing the comparisons above can see, so the question is read
            assertFalse(said.contains(I18n.f("autosetup.ui.confirmImportOverwrites", inUse)), "the door refused " + inUse
                + " and then asked to replace it (RLD4-C4): " + said);
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
     * An old file imported into the configuration in use, by its name, fills what it does not have and places none of the
     * file's trains: where a train stands on the railway running is the railway's to say, and a train moved on it before
     * the import is where it was moved (RLD3-C1, RLA2-B3, RLD2-C3, OB-183).
     *
     * Round 2 refused the name outright, which MT-298's own steps cannot get past: a hand change is made in the
     * configuration chosen, and the second import is into that one.
     *
     * And what the file brings to it stays: a priority the configuration had lost is there after the reload, which does
     * not capture again (RLU4-C2, RLA4-C2, RLD4-C2) - the moved train is kept by either capture, so it pins neither.  The
     * question asked says the file's trains are not placed (RLU4-C3).
     *
     * MUTATION: refuse the name again, place the file's trains on the running railway, leave the capture to the reload,
     * or ask the question any configuration is asked, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnOldFileIntoTheConfigurationInUseLeavesItsTrainsToTheRailway() throws Exception
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

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            // A SETTING THE FILE CARRIES, taken out of the configuration in use and the railway rebuilt without a capture:
            // what the import brings is then visible, and a capture after the import would take it away again.
            final Object[] carried = priorityTheFileCarries(session, MT491);

            assertNotNull(carried, "precondition: the MT-491 file gives no named square of his railway a priority");

            final TileKey prioritised = (TileKey) carried[0];
            final AutonomySession clearing = session;

            SwingUtilities.invokeAndWait(() ->
            {
                clearing.setPointProperty(prioritised, "priority", null);
                ui[0].getAutonomyViewerPanel().load(inUse, false, false);
            });

            session = ui[0].getAutonomySession();

            assertEquals(priorityIn(session, inUse, prioritised), null, "precondition: the priority could not be taken out"
                + " of " + inUse);

            // ONE STANDING TRAIN MOVED ON THE RUNNING RAILWAY, to an empty station - as a run leaves it, not written to
            // the configuration.
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            org.traincontrol.automation.Point from = null;
            org.traincontrol.automation.Point to = null;

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (point.getCurrentLocomotive() != null && from == null) from = point;
            }

            assertNotNull(from, "precondition: no train stands on his railway");

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (to == null && point.isDestination() && point.isActive() && point.getCurrentLocomotive() == null
                    && !point.isSamePlaceAs(from) && session.getStationIndex().squareOf(point.getName()) != null)
                {
                    to = point;
                }
            }

            assertNotNull(to, "precondition: no empty station on his railway");

            final String moved = from.getCurrentLocomotive().getName();
            final String toName = to.getName();

            SwingUtilities.invokeAndWait(() -> railway.moveLocomotive(moved, toName, false));

            Set<String> before = standingIn(session, inUse);

            List<String> said = importFromTheMenu(ui[0], MT491, inUse);

            session = ui[0].getAutonomySession();

            Integer placed = placedIn(said);

            assertNotNull(placed, "the old file was not imported into " + inUse + ", the configuration in use: " + said);

            assertEquals(placed, Integer.valueOf(0), "the import placed the file's trains in " + inUse + ", the"
                + " configuration running, where nothing knows where they are: " + said);

            assertEquals(standingIn(session, inUse), before, "the trains " + inUse + " has standing changed with the"
                + " import: " + said);

            TileKey movedTo = session.getStationIndex().squareOf(toName);

            org.json.JSONObject extras = session.getStore().getConfiguration(inUse).getJSONObject("points")
                .optJSONObject(movedTo.toString());

            assertTrue(extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE)
                && moved.equals(extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")), moved + ", moved on"
                + " the running railway to " + toName + " before the import, is not there in " + inUse + " after it - the"
                + " reload put it back where it set off (RLD2-C3)");

            String notPlaced = before(I18n.f("autosetup.ui.infoLegacyNotPlacedInUse", "@@@", inUse), "@@@");

            assertTrue(said.stream().anyMatch(message -> message.contains(notPlaced)), "the import did not say why it"
                + " placed none of the file's trains: " + said);

            // AND A DOOR THAT PUTS ONE DOWN (RLU5-C1): the train chosen in the main window, then "Place <train>" on its
            // station - the placement dialog lists only trains already standing, and these are not
            String sentence = I18n.f("autosetup.ui.infoLegacyNotPlacedInUse", "@@@", inUse,
                I18n.f("layout.ui.menuPlaceLocomotive", String.valueOf((char) 0x2026)));

            final String remedy = sentence.substring(sentence.indexOf("@@@") + 3);

            assertTrue(said.stream().anyMatch(message -> message.contains(remedy)), "the not-placed line does not end"
                + " with a door that puts one of those trains down (RLU5-C1): " + said);

            // WHAT THE IMPORT BROUGHT STAYS: the reload after it does not capture the railway back over it (RLA-C2)
            assertEquals(priorityIn(session, inUse, prioritised), carried[1], "the priority the old file carried into "
                + inUse + " was captured over by the reload after the import (RLU4-C2, RLA-C2): " + said);

            // AND THE QUESTION SAID NONE WOULD BE PLACED (RLU4-C3)
            // THE WHOLE QUESTION, since `said` holds whole dialogs (RLV12-C6 added its third sentence)
            assertTrue(said.contains(I18n.f("autosetup.ui.confirmImportFillsGapsInUse", inUse) + "  "
                + I18n.t("autosetup.ui.importTakesTheFilesForDefaults") + "  "
                + I18n.f("autosetup.ui.importKeepsTheStationMaximums", inUse)), "the question into " + inUse + ", the"
                + " configuration in use, promised the file's trains, and Yes placed none (RLU4-C3), or did not say what"
                + " counts as not set (RLA3-B1): " + said);

            // AND THAT THE STATIONS KEEP THEIR MAXIMA (RLV12-C6): into the configuration in use, the file's never arrive
            assertTrue(said.stream().anyMatch(q -> q.contains(I18n.t("autosetup.ui.importTakesTheFilesForDefaults") + "  "
                + I18n.f("autosetup.ui.importKeepsTheStationMaximums", inUse))), "the question into " + inUse + " said a"
                + " setting at its default takes the file's value, and did not say its stations keep their maxima"
                + " (RLV12-C6): " + said);
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
     * MT-298 as its steps run it: the imported configuration chosen, a maximum changed by hand in it, and the same file
     * imported again into it by name - which fills gaps and keeps the hand change (RLD3-C1).
     *
     * MUTATION: refuse the configuration in use, or overwrite what is there, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testASecondImportIntoTheConfigurationInUseKeepsAHandMadeChange() throws Exception
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

            importFromTheMenu(ui[0], MT298, "MT-298 run");

            // CHOSEN, as Autonomy > Configuration chooses it: loaded and running.
            SwingUtilities.invokeAndWait(() -> ui[0].getAutonomyViewerPanel().load("MT-298 run", false));

            AutonomySession session = ui[0].getAutonomySession();

            assertEquals(ui[0].getActiveDiagramConfiguration(), "MT-298 run", "precondition: the imported configuration"
                + " could not be chosen");

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

            assertNotNull(named, "precondition: the import gave no named station a maximum on this diagram");

            // THE HAND CHANGE, as the editor makes it: written, then the railway rebuilt from it.
            final TileKey changed = named;
            final int corrected = maxOf(session, changed) + 3;
            final AutonomySession editing = session;

            SwingUtilities.invokeAndWait(() ->
            {
                editing.setPointProperty(changed, "maxTrainLength", corrected);
                ui[0].getAutonomyViewerPanel().load("MT-298 run", false, false);
            });

            List<String> said = importFromTheMenu(ui[0], MT298, "MT-298 run");

            session = ui[0].getAutonomySession();

            assertNotNull(placedIn(said), "the second import into the configuration in use did not happen: " + said);

            assertEquals(maxOf(session, changed), corrected, "the second import into the configuration in use replaced"
                + " the maximum set by hand between the two (MT-298): " + said);
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
     * A configuration of the older, bare shape - the setup folder's own configuration file, or an export from before
     * bundles - imported into the configuration in use by its name is refused as a bundle is, before anything is asked
     * or written (RLU4-B1, RLA2-B1).  It replaces the configuration as a bundle does, and the reload after it captured
     * the running railway straight back over it, after a message saying it was imported.
     *
     * MUTATION: refuse only a bundle, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testABareConfigurationIntoTheConfigurationInUseIsRefused() throws Exception
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

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            String before = session.getStore().getConfiguration(inUse).toString();
            List<String> namesBefore = new ArrayList<>(session.getStore().getConfigurationNames());
            String onDiskBefore = setupOnDisk(session).toString();

            // HIS CONFIGURATION AS ITS OWN FILE HOLDS IT, with one change the running layout does not carry.
            org.json.JSONObject bare = new org.json.JSONObject(before);

            String first = bare.getJSONObject("points").keys().next();

            bare.getJSONObject("points").getJSONObject(first).put("priority", 7);

            assertEquals(AutonomySession.detectImportFormat(bare), AutonomySession.ImportFormat.CONFIGURATION,
                "precondition: the configuration's own shape is not read as a configuration on its own");

            File file = File.createTempFile("tc-configuration", ".json");

            file.deleteOnExit();

            java.nio.file.Files.write(file.toPath(), bare.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

            List<String> said = importFromTheMenu(ui[0], file, inUse);

            session = ui[0].getAutonomySession();

            assertTrue(said.contains(I18n.f("autosetup.ui.errorImportIntoConfigurationInUse", inUse, whereChosen(inUse))),
                "a configuration of the older shape was imported over " + inUse + ", the configuration in use - the reload"
                + " then captured the running railway back over it (RLU4-B1): " + said);

            assertFalse(said.contains(I18n.f("autosetup.ui.confirmImportOverwrites", inUse)), "the door asked to replace "
                + inUse + " after refusing it: " + said);

            assertEquals(session.getStore().getConfiguration(inUse).toString(), before, "an import refused changed " + inUse);

            assertEquals(new ArrayList<>(session.getStore().getConfigurationNames()), namesBefore, "an import refused"
                + " left a configuration behind");

            assertEquals(setupOnDisk(session).toString(), onDiskBefore, "an import refused changed the setup on disk");
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
     * An old file into the configuration in use while autonomy runs is refused, as Delete beside it is, and changes
     * nothing (RLU4-C1, RLA4-C1, RLD4-C1).
     *
     * The capture before the import is skipped while autonomy is busy - the reload stops the trains and captures where
     * they stopped - so the reload's capture took back the homes and settings the import had just written and counted;
     * and with the stop declined, the next fold did the same.
     *
     * The trains are "moving" as a Return Home in progress makes them: the flag the window asks.
     *
     * MUTATION: let it in while autonomy runs, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnOldFileIntoTheConfigurationInUseIsRefusedWhileAutonomyRuns() throws Exception
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

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            String before = session.getStore().getConfiguration(inUse).toString();
            List<String> namesBefore = new ArrayList<>(session.getStore().getConfigurationNames());
            String onDiskBefore = setupOnDisk(session).toString();

            staging.set(ui[0], true);

            // REFUSED AT ONCE, into any configuration (RSA4-C5), and saying what to do instead (RLU5-C2, RLA5-C1)
            List<String> said = pressImport(ui[0], MT491, inUse, Collections.<String>emptySet()).said;

            session = ui[0].getAutonomySession();

            assertTrue(said.contains(I18n.t("autosetup.ui.errorImportWhileRunning")), "an old file was taken into "
                + inUse + ", the configuration in use, while autonomy runs (RLU4-C1), or refused without saying what to"
                + " do instead (RLU5-C2, RLA5-C1): " + said);

            assertEquals(session.getStore().getConfiguration(inUse).toString(), before, "an import refused changed " + inUse);

            assertEquals(new ArrayList<>(session.getStore().getConfigurationNames()), namesBefore, "an import refused"
                + " left a configuration behind");

            assertEquals(setupOnDisk(session).toString(), onDiskBefore, "an import refused changed the setup on disk");
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
     * A setup edit a run declined to apply - written to the file, not to the running layout, and waiting for the rebuild
     * that carries it - survives an old file's import, into the configuration in use or beside it (RLD4-C3, ACC-B3,
     * WKW-B2).
     *
     * The import into the configuration in use captured the running layout into it first, and the reload after an import
     * into another captured it into the one running: each a fold of the layout built before the edit back over it, which
     * the exit save and the editor doors refuse while such an edit waits.
     *
     * MUTATION: fold without asking whether an edit waits - before the import, or at the reload - and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnImportDoesNotFoldAnEditWaitingForItsRebuild() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(MT491.isFile(), "precondition: the MT-491 file is not in docs/manual-tests/files");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            final TileKey edited = aStationTheFileGivesNoPriority(session, MT491);

            assertNotNull(edited, "precondition: the MT-491 file gives a priority to every station of his railway");

            // THE EDIT A RUN DECLINED: written into the configuration, the running layout not rebuilt, the flag up.
            final AutonomySession editing = session;

            SwingUtilities.invokeAndWait(() -> editing.setPointProperty(edited, "priority", 5));

            declined.set(ui[0], true);

            List<String> said = importFromTheMenu(ui[0], MT491, inUse);

            session = ui[0].getAutonomySession();

            assertNotNull(placedIn(said), "precondition: the old file was not imported into " + inUse + ": " + said);

            assertEquals(priorityIn(session, inUse, edited), Integer.valueOf(5), "an edit waiting for its rebuild was"
                + " folded away by the import into " + inUse + ", the configuration in use (RLD4-C3): " + said);

            // AND BESIDE IT: the reload of the configuration in use, after an import into another.
            final AutonomySession again = session;

            SwingUtilities.invokeAndWait(() -> again.setPointProperty(edited, "priority", 6));

            declined.set(ui[0], true);

            said = importFromTheMenu(ui[0], MT491, "MT-491 beside");

            session = ui[0].getAutonomySession();

            assertTrue(session.getStore().getConfigurationNames().contains("MT-491 beside"), "precondition: the import"
                + " made no configuration of the name given: " + said);

            assertEquals(priorityIn(session, inUse, edited), Integer.valueOf(6), "an edit waiting for its rebuild was"
                + " folded away by the reload of " + inUse + " after an import into another configuration (RLD4-C3): "
                + said);
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * Reloading the configuration running while a setup edit a run declined waits for its rebuild keeps every train where
     * the run left it, and the edit is then carried (RLA5-B1, RLU5-B2, RLD5-B1).
     *
     * RLD4-C3 kept `load` from folding the older running layout over the edit, and `load` then rebuilt the railway from
     * the setup with each train back where it stood before the run - so choosing the configuration from the Autonomy
     * menu, ticking a page out of autonomy, or the reload after an import modelled a moved train on the square it had
     * left, and the square it stood on read free.  It now rebuilds as the editor doors do, carrying the trains across,
     * and lowers the flag.
     *
     * MUTATION: reload without carrying the trains, or leave the flag up, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAReloadWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            // THE EDIT THAT WAITS, written into the configuration and not the running layout (RLV7-C5)
            final Object[] edit = anEditWaits(ui[0], session, inUse);

            // ONE STANDING TRAIN MOVED ON THE RUNNING RAILWAY, to an empty station, as a run leaves it
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            org.traincontrol.automation.Point from = null;
            org.traincontrol.automation.Point to = null;

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (point.getCurrentLocomotive() != null && from == null) from = point;
            }

            assertNotNull(from, "precondition: no train stands on his railway");

            for (org.traincontrol.automation.Point point : railway.getPoints())
            {
                if (to == null && point.isDestination() && point.isActive() && point.getCurrentLocomotive() == null
                    && !point.isSamePlaceAs(from) && session.getStationIndex().squareOf(point.getName()) != null)
                {
                    to = point;
                }
            }

            assertNotNull(to, "precondition: no empty station on his railway");

            final String moved = from.getCurrentLocomotive().getName();
            final String fromName = from.getName();
            final String toName = to.getName();

            SwingUtilities.invokeAndWait(() -> railway.moveLocomotive(moved, toName, false));

            assertTheConfigurationHasItWhereItSetOff(session, inUse, new String[] {moved, fromName, toName});

            // THE EDIT A RUN DECLINED: the flag up, as a race-declined edit leaves it
            declined.set(ui[0], true);

            // CHOSEN FROM THE AUTONOMY MENU
            SwingUtilities.invokeAndWait(() -> ui[0].getAutonomyViewerPanel().load(inUse, false));

            org.traincontrol.automation.Layout after = ui[0].getModel().getAutoLayout();

            org.traincontrol.automation.Point where = after.getLocomotiveLocation(ui[0].getModel().getLocByName(moved));

            assertNotNull(where, moved + " is off the railway after the reload");

            assertEquals(where.getName(), toName, moved + ", moved by the run to " + toName + ", is back on " + fromName
                + " after a reload made while an edit waited - the square it stands on reads free (RLA5-B1)");

            assertFalse((Boolean) declined.get(ui[0]), "the reload carried the edit and left the flag up, so every later"
                + " reload repeats this");

            assertTheEditStands(ui[0].getAutonomySession(), inUse, edit, "after a reload made while it waited (RLA5-B1)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * The reload after a track-diagram edit or a page operation, while a setup edit a run declined waits, keeps every
     * train where the run left it (RLV6-B1, door a).  (A route editor's Save reached it too, until RLV7-A1.)  That reset forgets the configuration's
     * name before it reloads, so round 5's fix, which asked for the configuration running by its name, missed it.
     *
     * MUTATION: carry the trains only for a load that names the configuration running, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnEditCompletedWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            // THE EDIT THAT WAITS, written into the configuration and not the running layout (RLV7-C5)
            final Object[] edit = anEditWaits(ui[0], session, inUse);

            final String[] move = moveAStandingTrain(ui[0], session);

            assertTheConfigurationHasItWhereItSetOff(session, inUse, move);

            declined.set(ui[0], true);

            // AS THE ROUTE EDITOR'S SAVE ENDS: the window's own completion of an edit
            final CountDownLatch done = new CountDownLatch(1);

            SwingUtilities.invokeAndWait(() -> ui[0].layoutEditingComplete(done::countDown));

            assertTrue(done.await(180, TimeUnit.SECONDS), "precondition: the edit's completion never finished");

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertStandsWhereItWasMoved(ui[0], move, "after an edit completed while a setup edit waited (RLV6-B1, a)");

            assertFalse((Boolean) declined.get(ui[0]), "the reload carried the edit and left the flag up");

            assertTheEditStands(ui[0].getAutonomySession(), inUse, edit, "after an edit completed while it waited"
                + " (RLV6-B1, a)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * Choosing another configuration while a setup edit a run declined waits leaves the configuration it leaves with every
     * train where the run left it (RLV6-B1, door b): the one running is carried first - the edit and where the trains stand
     * - and then folded as any configuration left is.
     *
     * MUTATION: load the other configuration without carrying the one running, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testChoosingAnotherConfigurationWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains() throws Exception
    {
        assertTrue(MT491.isFile(), "precondition: the MT-491 file is not in docs/manual-tests/files");

        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            // A SECOND CONFIGURATION, made before any edit waits
            importFromTheMenu(ui[0], MT491, "MT-491 other");

            session = ui[0].getAutonomySession();

            assertTrue(session.getStore().getConfigurationNames().contains("MT-491 other"), "precondition: the import made"
                + " no second configuration");

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: the import changed the configuration"
                + " running");

            // THE EDIT THAT WAITS, written into the configuration and not the running layout (RLV7-C5)
            final Object[] edit = anEditWaits(ui[0], session, inUse);

            final String[] move = moveAStandingTrain(ui[0], session);

            assertTheConfigurationHasItWhereItSetOff(session, inUse, move);

            declined.set(ui[0], true);

            SwingUtilities.invokeAndWait(() -> ui[0].getAutonomyViewerPanel().load("MT-491 other", false));

            session = ui[0].getAutonomySession();

            TileKey movedTo = session.getStationIndex().squareOf(move[2]);

            org.json.JSONObject extras = session.getStore().getConfiguration(inUse).getJSONObject("points")
                .optJSONObject(movedTo.toString());

            assertTrue(extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE)
                && move[0].equals(extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")), move[0] + ", moved by"
                + " the run to " + move[2] + ", is not there in " + inUse + " after another configuration was chosen while"
                + " a setup edit waited - " + inUse + " keeps it where it set off (RLV6-B1, b)");

            assertFalse((Boolean) declined.get(ui[0]), "choosing another configuration left the flag up");

            assertTheEditStands(session, inUse, edit, "after another configuration was chosen while it waited - the"
                + " configuration left was folded from the layout built before it (RLV6-B1, b; RLD4-C3)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * A reload of the configuration running, confirmed while autonomy is busy and a setup edit a run declined waits,
     * replaces the running layout with every train carried across (RLV6-B1, door c).  It went to the editor doors'
     * rebuild, which does nothing while autonomy reads busy - and a train stopped between sensors keeps the old layout
     * reading busy - so the confirmed reload did nothing, and autonomy stayed busy until a restart.
     *
     * Busy as a Return Home in progress makes it: the flag the window asks.
     *
     * MUTATION: send the reload through a door that refuses while busy, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAReloadConfirmedWhileBusyAndAnEditWaitsIsMade() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        java.lang.reflect.Field staging = TrainControlUI.class.getDeclaredField("stagingFlowActive");

        staging.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            // THE EDIT THAT WAITS, written into the configuration and not the running layout (RLV7-C5)
            final Object[] edit = anEditWaits(ui[0], session, inUse);

            final String[] move = moveAStandingTrain(ui[0], session);

            assertTheConfigurationHasItWhereItSetOff(session, inUse, move);

            final Object before = ui[0].getModel().getAutoLayout();

            staging.set(ui[0], true);

            declined.set(ui[0], true);

            List<String> asked = answeringYes(() -> ui[0].getAutonomyViewerPanel().load(inUse, true));

            assertTrue(asked.contains(I18n.t("autolayout.ui.confirmReloadJsonStopsRunningLocomotives")), "precondition: the"
                + " reload did not ask to stop the trains: " + asked);

            assertTrue(ui[0].getModel().getAutoLayout() != before, "the reload the operator confirmed, while autonomy was"
                + " busy and a setup edit waited, did nothing - the old layout runs on, reading busy (RLV6-B1, c)");

            assertStandsWhereItWasMoved(ui[0], move, "after a reload confirmed while busy (RLV6-B1, c)");

            assertTheEditStands(ui[0].getAutonomySession(), inUse, edit, "after a reload confirmed while busy"
                + " (RLV6-B1, c)");
        }
        finally
        {
            if (ui[0] != null) staging.set(ui[0], false);
            if (ui[0] != null) declined.set(ui[0], false);

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
     * A reload the operator asks for, while a setup edit a run declined waits, stops a train driven by hand, as the same
     * reload does with nothing waiting (RLV6-C1, VD11-A2: *"whether the OPERATOR asked for a different railway.  Choosing
     * a configuration is that"*).  It went through the editor doors' quiet rebuild, which stops nothing.
     *
     * MUTATION: load quietly while an edit waits, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAReloadAskedForWhileAnEditWaitsStopsTheTrainsAsAnyOtherDoes() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            // THE EDIT THAT WAITS, written into the configuration and not the running layout (RLV7-C5)
            final Object[] edit = anEditWaits(ui[0], session, inUse);

            final org.traincontrol.base.Locomotive driven = ui[0].getModel().getLocByName(
                ui[0].getModel().getLocList().get(0));

            driven.setSpeed(40);

            assertEquals(driven.getSpeed(), 40, "precondition: the locomotive's speed could not be set by hand");

            declined.set(ui[0], true);

            answeringYes(() -> ui[0].getAutonomyViewerPanel().load(inUse, true));

            Thread.sleep(1000);

            assertEquals(driven.getSpeed(), 0, driven.getName() + ", driven by hand, rolls on across a reload the operator"
                + " chose while a setup edit waited - the same reload with nothing waiting stops it (RLV6-C1)");

            assertTheEditStands(ui[0].getAutonomySession(), inUse, edit, "after a reload asked for while it waited"
                + " (RLV6-C1)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * Saving a route while autonomy runs leaves the run alone (RLV7-A1).
     *
     * The route editor's Save ended by completing a diagram edit, whose reset forgot the configuration running and whose
     * reload asked to abandon the run.  Yes rebuilt the railway with every train the run had moved back where it set off,
     * the square it stood on reading free; No left the run going with no configuration to fold it into.  A route is no
     * page of the diagram - its tiles follow it by id (`rebindRouteTiles`) - so there is nothing of autonomy to rebuild.
     *
     * Running as `isRunning` reads it, set after the move since a move is refused while running.
     *
     * MUTATION: complete a diagram edit after the route editor's Save, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testSavingARouteWhileAutonomyRunsLeavesTheRunAlone() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        final String routeName = "RLV7-A1 route";

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field running = org.traincontrol.automation.Layout.class.getDeclaredField("running");

        running.setAccessible(true);

        java.lang.reflect.Field noEditorOpen = TrainControlUI.class.getDeclaredField("noEditorOpen");

        noEditorOpen.setAccessible(true);

        org.traincontrol.automation.Layout layout = null;

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            final String[] move = moveAStandingTrain(ui[0], session);

            layout = ui[0].getModel().getAutoLayout();

            // A ROUTE OF HIS OWN, switched off, to open in the editor and save unchanged
            List<org.traincontrol.base.RouteCommand> commands = new ArrayList<>();

            commands.add(org.traincontrol.base.RouteCommand.RouteCommandAccessory(93,
                org.traincontrol.base.Accessory.accessoryDecoderType.MM2, true));

            assertTrue(ui[0].getModel().newRoute(routeName, commands, 0,
                org.traincontrol.base.Route.s88Triggers.CLEAR_THEN_OCCUPIED, false, null), "precondition: the route was"
                + " not made");

            final Object before = ui[0].getModel().getRoute(routeName);

            running.set(layout, true);

            assertTrue(ui[0].getModel().isAutonomyRunning(), "precondition: autonomy does not read as running");

            assertTrue((Boolean) noEditorOpen.get(ui[0]), "precondition: the window reads an editor as open");

            final org.traincontrol.gui.RouteEditorFrame[] editor = new org.traincontrol.gui.RouteEditorFrame[1];

            SwingUtilities.invokeAndWait(() -> editor[0] = new org.traincontrol.gui.RouteEditorFrame(ui[0], routeName,
                ui[0].getModel().getRoute(routeName)));

            final java.lang.reflect.Method save = org.traincontrol.gui.RouteEditorFrame.class.getDeclaredMethod("onSave");

            save.setAccessible(true);

            startAnsweringYes(asked, going);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    save.invoke(editor[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            // A DIAGRAM EDIT'S COMPLETION, where the Save starts one: it lowers the window's editing controls as it
            // starts and raises them as it ends, after its reload
            long until = System.currentTimeMillis() + 180000;

            while (!(Boolean) noEditorOpen.get(ui[0]) && System.currentTimeMillis() < until) Thread.sleep(100);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue(ui[0].getModel().getRoute(routeName) != null && ui[0].getModel().getRoute(routeName) != before,
                "precondition: the route editor's Save did not save the route: " + asked);

            assertTrue(asked.isEmpty(), "saving a route while autonomy runs asked to abandon the run (RLV7-A1): " + asked);

            assertTrue(ui[0].getModel().getAutoLayout() == layout, "saving a route while autonomy runs replaced the"
                + " running layout (RLV7-A1)");

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "saving a route while autonomy runs forgot the"
                + " configuration running, so nothing can fold where the run leaves the trains (RLV7-A1)");

            assertStandsWhereItWasMoved(ui[0], move, "after a route was saved while autonomy ran (RLV7-A1)");
        }
        finally
        {
            going.set(false);

            if (layout != null) running.set(layout, false);

            if (ui[0] != null && ui[0].getModel().getRoute(routeName) != null) ui[0].getModel().deleteRoute(routeName);

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
     * A reload the operator confirms while a train is under way is made, with that train where it set off (RLV7-B1,
     * RLV7-C1).
     *
     * Yes stops the trains and releases nothing: a train stopped between sensors keeps its path locked, and a locked path
     * holds every point on it for its train.  The load then folded that layout into the configuration, placing the train
     * on each of those squares, and the rebuild was refused as one locomotive in two places - the old layout staying,
     * reading busy, until a restart.  A layout holding a path is now folded with each train under way written on one
     * point - the last station whose sensor it has tripped, or where it set off - so the Auto tab's settings and a
     * captured timetable are kept too (RLV8-B1); and the destination turns the railway has made and not yet written down
     * are kept across it (RLV9-C1).
     *
     * MUTATION: fold each point's occupant, read each point's occupant, or leave the turns on the old railway, and this
     * fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAReloadConfirmedWhileATrainIsUnderWayIsMade() throws Exception
    {
        reloadConfirmedWhileATrainIsUnderWay(false);
    }

    /**
     * The same while a setup edit a run declined waits, which carried the train to whichever point of its path the layout
     * met last - its destination, as here - with the square it stood on reading free (RLV7-C1).
     *
     * MUTATION: read each point's occupant, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAReloadWhileAnEditWaitsKeepsATrainUnderWayWhereItSetOff() throws Exception
    {
        reloadConfirmedWhileATrainIsUnderWay(true);
    }

    /** The two above: a train dispatched and stopped short of any sensor, then a reload of the configuration running. */
    private static void reloadConfirmedWhileATrainIsUnderWay(boolean editWaits) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            final Object[] edit = editWaits ? anEditWaits(ui[0], session, inUse) : null;

            // AND ONE THE RUN HAS MOVED, standing: carried across, it stays where it was moved; rebuilt from the setup
            // without a fold, it goes back where it set off
            final String[] move = moveAStandingTrain(ui[0], session);

            assertTheConfigurationHasItWhereItSetOff(session, inUse, move);

            // AND WHAT ONLY A FOLD KEEPS (RLV8-B1): the Auto tab's settings changed since the last save, and a
            // timetable captured this session - set while idle, as the sliders are, before the train sets off
            final org.traincontrol.automation.Layout idle = ui[0].getModel().getAutoLayout();

            if (!editWaits)
            {
                idle.setMaxDelay(7);
                idle.setMaxActiveTrains(5);
                idle.setTimetableCapture(true);
            }

            final Object[] dispatched = dispatchATrain(ui[0], move[0]);

            driving = (Thread) dispatched[3];

            final String train = (String) dispatched[0];
            final String from = (String) dispatched[1];
            final String bound = (String) dispatched[2];

            final org.traincontrol.automation.Layout before = ui[0].getModel().getAutoLayout();

            assertTrue(ui[0].getModel().isAutonomyRunning(), "precondition: autonomy does not read busy with " + train
                + " under way");

            final int captured = before.getTimetableSnapshot().size();

            if (!editWaits)
            {
                assertTrue(captured > 0, "precondition: capture was on and " + train + " was sent, and the timetable"
                    + " captured nothing");
            }

            // AND A TURN THE RAILWAY MADE AND HAS NOT YET WRITTEN DOWN (RLV9-C1): turns are written only while the railway
            // is idle, so during a run every one made since it started is still owed.  One nothing can write, so it stays
            // owed wherever it is kept
            java.util.Map<String, String> owed = new java.util.HashMap<>();

            owed.put("RLV9-C1 nobody", "RLV9-C1 nowhere");

            before.restoreReversalsOnArrival(owed);

            // THE RULE (RLV7-C1), as every carry reads it
            String[] read = TrainControlUI.whereTheTrainsAre(before).get(train);

            assertEquals(read == null ? null : read[0], from, train + ", under way from " + from + " to " + bound + " and"
                + " past no sensor yet, is read as standing on " + (read == null ? "no point" : read[0]) + " (RLV7-C1)");

            if (editWaits) declined.set(ui[0], true);

            List<String> asked = answeringYes(() -> ui[0].getAutonomyViewerPanel().load(inUse, true));

            assertTrue(asked.contains(I18n.t("autolayout.ui.confirmReloadJsonStopsRunningLocomotives")), "precondition: the"
                + " reload did not ask to stop the trains: " + asked);

            assertTrue(ui[0].getModel().getAutoLayout() != before, "the reload the operator confirmed with " + train
                + " under way was not made - the railway was folded with it on every point of its path, and the rebuild"
                + " refused one locomotive in two places (RLV7-B1): " + asked);

            org.traincontrol.automation.Point where = ui[0].getModel().getAutoLayout()
                .getLocomotiveLocation(ui[0].getModel().getLocByName(train));

            assertNotNull(where, train + " is off the railway after the reload");

            assertEquals(where.getName(), from, train + ", stopped on its way from " + from + " to " + bound + " short of"
                + " any sensor, is modelled on " + where.getName() + " after the reload (RLV7-C1)");

            assertStandsWhereItWasMoved(ui[0], move, "after a reload confirmed with a train under way (RLV7-B1)");

            assertFalse(ui[0].getModel().isAutonomyRunning(), "autonomy still reads busy after the reload");

            assertEquals(ui[0].getModel().getAutoLayout().turnedOnArrivalAt("RLV9-C1 nobody"), "RLV9-C1 nowhere", "a turn"
                + " the railway owed when the reload was confirmed with " + train + " under way is gone - dropped with the"
                + " railway the reload replaced (RLV9-C1)");

            assertEquals(squaresPlacing(ui[0].getAutonomySession(), inUse, train), 1, inUse + " places " + train + " on"
                + " other than one square after the reload (RLV7-B1)");

            if (!editWaits)
            {
                org.traincontrol.automation.Layout after = ui[0].getModel().getAutoLayout();

                assertEquals(after.getMaxDelay(), 7, "the reload with " + train + " under way put the Auto tab's maximum"
                    + " delay back to the file's (RLV8-B1)");

                assertEquals(after.getMaxActiveTrains(), 5, "the reload with " + train + " under way put the Auto tab's"
                    + " maximum active trains back to the file's (RLV8-B1)");

                assertEquals(after.getTimetableSnapshot().size(), captured, "the reload with " + train + " under way"
                    + " lost the timetable captured this session (RLV8-B1)");
            }

            if (editWaits)
            {
                assertFalse((Boolean) declined.get(ui[0]), "the reload carried the edit and left the flag up");

                assertTheEditStands(ui[0].getAutonomySession(), inUse, edit, "after a reload with a train under way");
            }
        }
        finally
        {
            if (driving != null) driving.interrupt();

            if (ui[0] != null) declined.set(ui[0], false);

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
     * Choosing another configuration while a train is under way leaves the configuration running with that train on one
     * square, where it set off (RLV7-B1).  The fold into the configuration left placed it on every square of its path,
     * and that configuration then refused to load until each was cleared by hand.
     *
     * MUTATION: fold a layout that holds a path, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testChoosingAnotherConfigurationWhileATrainIsUnderWayStandsItOnce() throws Exception
    {
        assertTrue(MT491.isFile(), "precondition: the MT-491 file is not in docs/manual-tests/files");

        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            importFromTheMenu(ui[0], MT491, "MT-491 other");

            session = ui[0].getAutonomySession();

            assertTrue(session.getStore().getConfigurationNames().contains("MT-491 other"), "precondition: the import made"
                + " no second configuration");

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: the import changed the configuration"
                + " running");

            // AND ONE THE RUN HAS MOVED, standing, which the configuration left must have where it was moved
            final String[] move = moveAStandingTrain(ui[0], session);

            final Object[] dispatched = dispatchATrain(ui[0], move[0]);

            driving = (Thread) dispatched[3];

            final String train = (String) dispatched[0];
            final String from = (String) dispatched[1];

            List<String> asked = answeringYes(() -> ui[0].getAutonomyViewerPanel().load("MT-491 other", true));

            assertTrue(asked.contains(I18n.t("autolayout.ui.confirmReloadJsonStopsRunningLocomotives")), "precondition: the"
                + " load did not ask to stop the trains: " + asked);

            assertEquals(ui[0].getActiveDiagramConfiguration(), "MT-491 other", "precondition: the configuration chosen"
                + " is not running: " + asked);

            session = ui[0].getAutonomySession();

            assertEquals(squaresPlacing(session, inUse, train), 1, inUse + ", left while " + train + " was under way,"
                + " places it on other than one square, and will not load again until each is cleared by hand"
                + " (RLV7-B1)");

            org.json.JSONObject extras = session.getStore().getConfiguration(inUse).getJSONObject("points")
                .optJSONObject(session.getStationIndex().squareOf(from).toString());

            assertTrue(extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE)
                && train.equals(extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")), inUse + " does not"
                + " have " + train + " where it set off, " + from + ", short of any sensor (RLV7-C1)");

            org.json.JSONObject moved = session.getStore().getConfiguration(inUse).getJSONObject("points")
                .optJSONObject(session.getStationIndex().squareOf(move[2]).toString());

            assertTrue(moved != null && moved.has(AutonomyBuilder.LOCOMOTIVE)
                && move[0].equals(moved.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")), inUse + " does not"
                + " have " + move[0] + " where the run moved it, " + move[2] + ", after another configuration was chosen"
                + " with a train under way - it was neither carried nor folded (RLV7-B1)");
        }
        finally
        {
            if (driving != null) driving.interrupt();

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
     * Choosing another configuration while a setup edit waits, where the configuration running cannot be used with that
     * edit, is refused and says why (RLV7-C4).  Here the edit places a train twice: an error, and a setup with errors
     * still loads so that it can be fixed (SVN-B10), so the configuration running stays loaded with its errors.
     *
     * The choice went ahead: the configuration left kept where its trains stood before the run, with nothing said; the
     * log said it "could not be loaded at startup", about a load nobody made at start-up; and the flag stayed up over the
     * configuration chosen, so nothing it did was folded until something rebuilt it.
     *
     * MUTATION: load the configuration chosen whether or not the one running was carried, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testChoosingAnotherConfigurationWhileAnEditThatBreaksTheOneRunningWaitsIsRefused() throws Exception
    {
        assertTrue(MT491.isFile(), "precondition: the MT-491 file is not in docs/manual-tests/files");

        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            importFromTheMenu(ui[0], MT491, "MT-491 other");

            session = ui[0].getAutonomySession();

            assertTrue(session.getStore().getConfigurationNames().contains("MT-491 other"), "precondition: the import made"
                + " no second configuration");

            final String[] move = moveAStandingTrain(ui[0], session);

            // THE EDIT THAT WAITS, and it stops the configuration running from building: the moved train placed on a
            // second station, written to the file as a declined edit is
            final TileKey second = anEmptyStationSquare(ui[0], session, move);

            final AutonomySession editing = session;

            SwingUtilities.invokeAndWait(() ->
            {
                editing.setPointProperty(second, AutonomyBuilder.LOCOMOTIVE, new org.json.JSONObject().put("name", move[0]));

                try
                {
                    editing.getStore().save();
                }
                catch (java.io.IOException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(squaresPlacing(session, inUse, move[0]), 2, "precondition: the edit did not place " + move[0]
                + " a second time in " + inUse + " - the store's active configuration is "
                + session.getStore().getActiveConfiguration());

            declined.set(ui[0], true);

            final int logFrom = logged().length();

            List<String> asked = answeringYes(() -> ui[0].getAutonomyViewerPanel().load("MT-491 other", true));

            final String refusal = I18n.f("autosetup.ui.errorCannotKeepTrainsBeforeChoosing", inUse, "MT-491 other");

            assertTrue(asked.stream().anyMatch(said -> said.startsWith(refusal)), "choosing another configuration, while"
                + " the edit that waits stops " + inUse + " from building, did not say why it was refused (RLV7-C4): "
                + asked);

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "MT-491 other was loaded, and " + inUse + " was left"
                + " with its trains where they stood before the run (RLV7-C4)");

            assertFalse(asked.stream().anyMatch(said -> said.contains(" 0 ")), "the refusal counts nothing to deal with: "
                + asked);

            assertEquals(squaresPlacing(ui[0].getAutonomySession(), inUse, move[0]), 2, "the edit that waits in " + inUse
                + " was folded away by the refused choice");

            assertTrue((Boolean) declined.get(ui[0]), "the flag came down, though the edit it guards still waits");

            assertFalse(logged().substring(logFrom).contains(I18n.f("autosetup.ui.infoResumeFailed", inUse)), "the log"
                + " says " + inUse + " could not be loaded at startup, about a load nobody made at start-up (RLV7-C4)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** A station square no train stands on, in the running layout or where the moved train set off. */
    private static TileKey anEmptyStationSquare(TrainControlUI ui, AutonomySession session, String[] move)
    {
        final org.traincontrol.automation.Layout railway = ui.getModel().getAutoLayout();

        final org.traincontrol.automation.Point setOff = railway.getPoint(move[1]);

        for (org.traincontrol.automation.Point point : railway.getPoints())
        {
            if (point.isDestination() && point.isActive() && point.getCurrentLocomotive() == null
                && (setOff == null || !point.isSamePlaceAs(setOff))
                && session.getStationIndex().squareOf(point.getName()) != null)
            {
                return session.getStationIndex().squareOf(point.getName());
            }
        }

        throw new AssertionError("precondition: no empty station on his railway");
    }

    /**
     * A switch of railway forgets the railway it leaves (RLV7-C2): the flag a declined edit raised, and the name of the
     * configuration the reset forgot.  The load that followed read "no configuration running" as the reset after an
     * edit, and carried the previous railway's trains across by Point name.  And only when the railway changes
     * (RLV8-C5): choosing the folder already in use reloads the same railway, whose trains are carried as any reload
     * carries them.
     *
     * The door every switch of layout source ends in, `initializeTrackDiagram`: first over the same folder, then over a
     * source named differently, then back to the first - another railway again, since the one shown is the second's
     * (RLV9-C7).
     *
     * MUTATION: forget the railway on every switch, or on none, or leave the source switched to unrecorded, and this
     * fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testASwitchOfRailwayForgetsTheRailwayItLeaves() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            final String[] move = moveAStandingTrain(ui[0], session);

            java.lang.reflect.Field forgotten = TrainControlUI.class.getDeclaredField("forgottenByTheReset");

            forgotten.setAccessible(true);

            // THE SAME FOLDER, chosen again (RLV8-C5): the same railway, so nothing is forgotten, and the load carries
            declined.set(ui[0], true);

            SwingUtilities.invokeAndWait(() -> ui[0].initializeTrackDiagram(false));

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertEquals(ui[0].getActiveDiagramConfiguration(), null, "precondition: the switch left a configuration"
                + " running");

            assertTrue((Boolean) declined.get(ui[0]), "choosing the folder already in use forgot the edit that waits, as"
                + " if it were another railway (RLV8-C5)");

            assertEquals(forgotten.get(ui[0]), inUse, "choosing the folder already in use forgot which configuration it"
                + " ran (RLV8-C5)");

            // AND THE FILE THE NEXT START READS has the train where the run left it (RSA21-A1): no configuration is named
            // after the reset, so no door after it would fold the railway
            assertTheFileHasItWhereItWasMoved(sandbox.getFolder(), inUse, session, move, "after the folder in use was"
                + " chosen again while an edit waited (RSA21-A1)");

            answeringYes(() -> ui[0].getAutonomyViewerPanel().load(inUse, true));

            assertStandsWhereItWasMoved(ui[0], move, "after the folder in use was chosen again and loaded (RLV8-C5)");

            // ANOTHER SOURCE (RLV7-C2): a copy of this railway's folder, made before the run moved the train, stands for a
            // railway that is not this one - a folder of its own, so the fold into the railway left (RSA21-A1) is not read
            // off the next one as a carry.  Another train, so that where its configuration has it and where it was moved
            // to differ
            final String source = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            final File another = aCopyOf(new File(source));

            final AutonomySession leaving = ui[0].getAutonomySession();

            final String[] moved = moveAStandingTrain(ui[0], leaving, move[0]);

            assertTheConfigurationHasItWhereItSetOff(leaving, inUse, moved);

            declined.set(ui[0], true);

            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, another.getAbsolutePath());

            try
            {
                SwingUtilities.invokeAndWait(() -> ui[0].initializeTrackDiagram(false));

                for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

                assertFalse((Boolean) declined.get(ui[0]), "a switch of railway left the flag up, about a railway no"
                    + " longer shown (RLV7-C2)");

                assertEquals(forgotten.get(ui[0]), null, "a switch of railway remembers the configuration the previous"
                    + " one ran, for a load to carry its trains across (RLV7-C2)");

                // AND THE RAILWAY LEFT'S OWN FILE has the train where the run left it (RSA21-A1)
                assertTheFileHasItWhereItWasMoved(new File(source), inUse, leaving, moved, "after a switch to another"
                    + " railway while an edit waited (RSA21-A1)");

                answeringYes(() -> ui[0].getAutonomyViewerPanel().load(inUse, true));

                org.traincontrol.automation.Point where = ui[0].getModel().getAutoLayout()
                    .getLocomotiveLocation(ui[0].getModel().getLocByName(moved[0]));

                assertNotNull(where, moved[0] + " is off the railway after the load");

                assertEquals(where.getName(), moved[1], "a load after a switch of railway carried " + moved[0] + " across"
                    + " from the railway left, by Point name (RLV7-C2)");

                // AND BACK TO THE FIRST (RLV9-C7): the source switched to is the one the next switch compares with, so the
                // first chosen again is another railway than the one shown
                declined.set(ui[0], true);

                TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, source);

                SwingUtilities.invokeAndWait(() -> ui[0].initializeTrackDiagram(false));

                for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

                assertFalse((Boolean) declined.get(ui[0]), "a switch back to the first source left the flag up, as if the"
                    + " railway shown were still the first's - the source switched to before was not remembered"
                    + " (RLV9-C7)");
            }
            finally
            {
                TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, source);

                deleteTheCopy(another);
            }
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * Unload retires the railway it unloads (RLV8-A1): a train its thread was driving, stopped by Unload's Yes between
     * two sensors, is not driven on when it reaches the next one.  And the refresh that railway fires when its last
     * thread ends builds no empty railway (RLV9-C2): the Return Home button asked the model for its layout, which makes
     * one where there is none, and `hasAutoLayout` then answered yes about nothing.
     *
     * A path's thread asks, at each sensor, whether its railway is still the current one, and every load replaces it
     * with a newer one.  Unload replaced it with nothing, and the retirement came by accident - the autonomy panel's list
     * built an empty railway as Unload remade it.  RLV7-C2 removed that, and the unloaded railway stayed current.
     *
     * MUTATION: drop the railway at Unload without retiring it, or ask `getAutoLayout` for the Return Home button's
     * layout, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnUnloadRetiresTheRailwayItUnloads() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final Object[] dispatched = dispatchATrain(ui[0], null);

            driving = (Thread) dispatched[3];

            final org.traincontrol.automation.Layout unloaded = ui[0].getModel().getAutoLayout();

            assertTrue(unloaded.isCurrentLayout(), "precondition: the railway running is not the current one");

            List<String> asked = answeringYes(() -> ui[0].unloadAutonomy());

            assertTrue(asked.contains(I18n.t("autolayout.ui.confirmReloadJsonStopsRunningLocomotives")), "precondition:"
                + " Unload did not ask to stop the trains: " + asked);

            assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: Unload left a railway loaded");

            assertFalse(unloaded.isCurrentLayout(), "the railway Unload dropped is still the current one, so the thread"
                + " driving " + dispatched[0] + " drives it on at line speed when it reaches the sensor it waits for"
                + " (RLV8-A1)");

            // THE REFRESH ITS LAST THREAD FIRES AS IT ENDS (RLV9-C2), fired here as that thread's end fires it
            java.lang.reflect.Method finished = org.traincontrol.automation.Layout.class
                .getDeclaredMethod("announceRunFinished");

            finished.setAccessible(true);

            finished.invoke(unloaded);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(ui[0].getModel().hasAutoLayout(), "the refresh the unloaded railway fires as its last thread ends"
                + " built an empty railway, which hasAutoLayout then answers yes about (RLV9-C2)");
        }
        finally
        {
            if (driving != null) driving.interrupt();

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
     * Deleting the configuration running forgets the railway, as Unload does (RLV7-C2, RLV8-C7): the flag and the name
     * the reset forgot.
     *
     * MUTATION: leave the delete door's forgetting out, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testADeletedSetupForgetsTheRailway() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        java.lang.reflect.Field forgotten = TrainControlUI.class.getDeclaredField("forgottenByTheReset");

        forgotten.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            declined.set(ui[0], true);

            SwingUtilities.invokeAndWait(() -> ui[0].autonomySetupDeleted());

            for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: the delete door left a railway loaded");

            assertFalse((Boolean) declined.get(ui[0]), "deleting the setup left the flag up, with no railway for an edit"
                + " to wait for (RLV8-C7)");

            assertEquals(forgotten.get(ui[0]), null, "deleting the setup remembers the configuration it ran, for a load"
                + " to carry its trains across (RLV8-C7)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * Unload forgets the railway it unloads (RLV7-C2): the flag, and the name the reset forgot.  And nothing brings an
     * empty railway into being afterwards - asking the model for its layout builds one, and `hasAutoLayout` then answered
     * yes about nothing (CS3-C4): the autonomy panel's list of where the trains are did, as Unload remade the panel, and
     * so would the carry.
     *
     * MUTATION: leave the flag up at Unload, or ask `getAutoLayout` for the layout in the panel's list or before the
     * carry, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnUnloadForgetsTheRailway() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            java.lang.reflect.Field covered = TrainControlUI.class.getDeclaredField("coveredTrack");

            covered.setAccessible(true);

            long until = System.currentTimeMillis() + 20000;

            while (((java.util.Map<?, ?>) covered.get(ui[0])).isEmpty() && System.currentTimeMillis() < until)
            {
                Thread.sleep(100);
            }

            assertFalse(((java.util.Map<?, ?>) covered.get(ui[0])).isEmpty(), "precondition: no track is washed as"
                + " covered by a standing train");

            declined.set(ui[0], true);

            answeringYes(() -> ui[0].unloadAutonomy());

            assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: Unload left a railway loaded");

            // NO WASH (RLV8-C1): with nothing loaded no track is covered by a standing train
            until = System.currentTimeMillis() + 10000;

            while (!((java.util.Map<?, ?>) covered.get(ui[0])).isEmpty() && System.currentTimeMillis() < until)
            {
                Thread.sleep(100);
            }

            assertTrue(((java.util.Map<?, ?>) covered.get(ui[0])).isEmpty(), "after Unload the diagram still washes the"
                + " track the unloaded configuration's trains covered (RLV8-C1)");

            // NOR DOES THE LATENCY PING, OR A TIMETABLE REPAINT, BUILD ONE (RLV8-C2): with the power on, it asked the
            // model for its railway, which makes one where there is none
            java.lang.reflect.Field power = MarklinControlStation.class.getDeclaredField("powerState");

            power.setAccessible(true);

            Object powerWas = power.get(ui[0].getModel());

            try
            {
                power.set(ui[0].getModel(), true);

                java.lang.reflect.Method ping = TrainControlUI.class.getDeclaredMethod("checkAutoLayoutLatency",
                    double.class);

                ping.setAccessible(true);

                ping.invoke(ui[0], 0.0);

                assertFalse(ui[0].getModel().hasAutoLayout(), "after Unload the latency ping built a railway, which"
                    + " hasAutoLayout then answers yes about (RLV8-C2)");

                java.lang.reflect.Method repaint = TrainControlUI.class.getDeclaredMethod("repaintTimetable");

                repaint.setAccessible(true);

                repaint.invoke(ui[0]);

                for (int turn = 0; turn < 4; turn++) SwingUtilities.invokeAndWait(() -> { });

                assertFalse(ui[0].getModel().hasAutoLayout(), "after Unload a timetable repaint - every milestone's"
                    + " refresh - built a railway (RLV8-C2)");
            }
            finally
            {
                power.set(ui[0].getModel(), powerWas);
            }

            assertFalse((Boolean) declined.get(ui[0]), "Unload left the flag up, with no railway for an edit to wait for"
                + " (RLV7-C2)");

            java.lang.reflect.Field forgotten = TrainControlUI.class.getDeclaredField("forgottenByTheReset");

            forgotten.setAccessible(true);

            assertEquals(forgotten.get(ui[0]), null, "Unload remembers the configuration it unloaded, for a load to carry"
                + " its trains across (RLV7-C2)");

            final java.lang.reflect.Method carry = TrainControlUI.class.getDeclaredMethod("carryTheTrainsAcross",
                Runnable.class);

            carry.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    carry.invoke(ui[0], (Runnable) () -> { });
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertFalse(ui[0].getModel().hasAutoLayout(), "a carry with no railway loaded built an empty one, which"
                + " hasAutoLayout then answers yes about (RLV7-C2, CS3-C4)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * Stop Using Autonomy keeps what the run did (RLV9-A1): the configuration loaded again has a train the run moved
     * where it was moved, the Auto tab's settings as they were, and a train under way at Unload once, where it set off.
     *
     * Unload cleared the model before the reset, whose fold then found no railway - so nothing was folded, and the next
     * load built every train back where the file had it before the run, with the square each really stands on reading
     * free (DW-A1's shape).
     *
     * MUTATION: leave Unload's fold out, or fold each point's occupant, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnUnloadKeepsWhereTheRunLeftTheTrains() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            // ONE THE RUN HAS MOVED, standing, and a setting changed on the Auto tab
            final String[] move = moveAStandingTrain(ui[0], session);

            assertTheConfigurationHasItWhereItSetOff(session, inUse, move);

            final org.traincontrol.automation.Layout running = ui[0].getModel().getAutoLayout();

            final int delay = running.getMaxDelay() == 7 ? 8 : 7;

            running.setMaxDelay(delay);

            // AND ONE UNDER WAY
            final Object[] dispatched = dispatchATrain(ui[0], move[0]);

            driving = (Thread) dispatched[3];

            final String train = (String) dispatched[0];
            final String from = (String) dispatched[1];

            List<String> asked = answeringYes(() -> ui[0].unloadAutonomy());

            assertTrue(asked.contains(I18n.t("autolayout.ui.confirmReloadJsonStopsRunningLocomotives")), "precondition:"
                + " Unload did not ask to stop the trains: " + asked);

            assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: Unload left a railway loaded");

            answeringYes(() -> ui[0].getAutonomyViewerPanel().load(inUse, true));

            assertTrue(ui[0].getModel().hasAutoLayout(), "precondition: " + inUse + " did not load again");

            assertStandsWhereItWasMoved(ui[0], move, "after Stop Using Autonomy and a load (RLV9-A1)");

            assertEquals(ui[0].getModel().getAutoLayout().getMaxDelay(), delay, "Stop Using Autonomy put the Auto tab's"
                + " maximum delay back to the file's (RLV9-A1)");

            org.traincontrol.automation.Point where = ui[0].getModel().getAutoLayout()
                .getLocomotiveLocation(ui[0].getModel().getLocByName(train));

            assertNotNull(where, train + " is off the railway after Stop Using Autonomy and a load");

            assertEquals(where.getName(), from, train + ", under way from " + from + " when Stop Using Autonomy stopped it,"
                + " is modelled on " + where.getName() + " after a load (RLV9-A1)");

            assertEquals(squaresPlacing(ui[0].getAutonomySession(), inUse, train), 1, inUse + " places " + train + " on"
                + " other than one square after Stop Using Autonomy (RLV9-A1)");
        }
        finally
        {
            if (driving != null) driving.interrupt();

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
     * A reload confirmed while Return Home runs keeps the configuration's own timetable (RLV9-B1).  Return Home borrows
     * the timetable for its plan and hands it back when it ends; the fold a reload makes with a train under way (RLV8-B1)
     * wrote the plan into the configuration as its timetable, marked to run one train at a time, and saved it.
     *
     * The plan loaded as Return Home loads it, and a train sent as its first move would be.
     *
     * MUTATION: fold the timetable the railway holds, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAReloadDuringReturnHomeKeepsHisTimetable() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // HIS OWN TIMETABLE
            List<org.json.JSONObject> his = new ArrayList<>();

            for (org.traincontrol.automation.TimetablePath entry : railway.getTimetable()) his.add(entry.toJSON());

            assertFalse(his.isEmpty(), "precondition: " + inUse + " has no timetable of its own");

            org.traincontrol.automation.HomeStaging.Plan plan = railway.loadReturnToHomeTimetable();

            assertTrue(plan.isPossible() && railway.isTimetableSequential(), "precondition: Return Home's plan did not"
                + " take the timetable (" + plan.getMoves().size() + " moves)");

            final Object[] dispatched = dispatchATrain(ui[0], null);

            driving = (Thread) dispatched[3];

            List<String> asked = answeringYes(() -> ui[0].getAutonomyViewerPanel().load(inUse, true));

            assertTrue(ui[0].getModel().getAutoLayout() != railway, "precondition: the reload was not made: " + asked);

            org.json.JSONObject globals = ui[0].getAutonomySession().getStore().getConfiguration(inUse)
                .optJSONObject("globals");

            assertNotNull(globals, "precondition: " + inUse + " has no settings after the reload");

            assertFalse(globals.optBoolean("timetableSequential", false), "a reload confirmed while Return Home ran wrote"
                + " its plan into " + inUse + " as the timetable, marked to run one train at a time (RLV9-B1)");

            org.json.JSONArray stored = globals.optJSONArray("timetable");

            assertNotNull(stored, inUse + " has no timetable after a reload confirmed while Return Home ran (RLV9-B1)");

            assertEquals(stored.length(), his.size(), inUse + "'s timetable after a reload confirmed while Return Home ran"
                + " is not his (RLV9-B1): " + stored);

            for (int i = 0; i < his.size(); i++)
            {
                assertTrue(his.get(i).similar(stored.getJSONObject(i)), inUse + "'s timetable entry " + i + " after a"
                    + " reload confirmed while Return Home ran is not his (RLV9-B1): " + stored.getJSONObject(i)
                    + " for " + his.get(i));
            }

            assertFalse(ui[0].getModel().getAutoLayout().isTimetableSequential(), "the railway loaded after a reload"
                + " confirmed while Return Home ran runs its timetable one train at a time (RLV9-B1)");
        }
        finally
        {
            if (driving != null) driving.interrupt();

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
     * The folder the Central Station's layout is downloaded into is the source a later switch compares with (RLV9-C8).
     * The Download door writes the source and never reaches `initializeTrackDiagram`, so the source remembered stayed the
     * Central Station's, and choosing that folder again read as a switch to another railway: the flag an edit a run
     * declined raised came down, and every train the run moved stood back where it set off.
     *
     * The door's half after the download itself, over the folder in use as if the window had shown the Central Station's
     * layout; then that folder chosen again.  Read by reflection, as the claim came first.
     *
     * MUTATION: leave the door's source unrecorded, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheFolderDownloadedIntoIsTheSourceASwitchComparesWith() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        final String source = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final String folder = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

            assertFalse(folder.isEmpty(), "precondition: the sandbox's railway is not a folder of his");

            java.lang.reflect.Field remembered = TrainControlUI.class.getDeclaredField("railwaySource");

            remembered.setAccessible(true);

            // AS THE CENTRAL STATION'S LAYOUT LEFT IT
            remembered.set(ui[0], "");

            final java.lang.reflect.Method downloaded = TrainControlUI.class.getDeclaredMethod("useTheDownloadedLayout",
                File.class);

            downloaded.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    downloaded.invoke(ui[0], new File(folder));
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(remembered.get(ui[0]), TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF,
                ""), "after a download the source a later switch compares with is not the folder downloaded into"
                + " (RLV9-C8)");

            // THAT FOLDER CHOSEN AGAIN: the same railway, so nothing is forgotten
            declined.set(ui[0], true);

            SwingUtilities.invokeAndWait(() -> ui[0].initializeTrackDiagram(false));

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertTrue((Boolean) declined.get(ui[0]), "the folder a download went into, chosen again, forgot the edit that"
                + " waits, as if it were another railway (RLV9-C8)");
        }
        finally
        {
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, source);

            if (ui[0] != null) declined.set(ui[0], false);

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
     * Stop Using Autonomy while an edit a run declined waits keeps where the trains stand, and the edit (RLV10-B1).  Its
     * fold is not made then - it would take the edit away - and nothing else was: the model was cleared, the flag
     * lowered, and the next load built every train the run moved back where the file had it, as the loads did before
     * they carried the trains (RLA5-B1, RLV6-B1).
     *
     * MUTATION: keep nothing at Unload while an edit waits, or fold the whole running layout then, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnUnloadWhileAnEditWaitsKeepsWhereTheRunLeftTheTrains() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            assertEquals(ui[0].getActiveDiagramConfiguration(), inUse, "precondition: " + inUse + " is not running");

            final Object[] edit = anEditWaits(ui[0], session, inUse);

            final String[] move = moveAStandingTrain(ui[0], session);

            assertTheConfigurationHasItWhereItSetOff(session, inUse, move);

            declined.set(ui[0], true);

            answeringYes(() -> ui[0].unloadAutonomy());

            assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: Unload left a railway loaded");

            answeringYes(() -> ui[0].getAutonomyViewerPanel().load(inUse, true));

            assertTrue(ui[0].getModel().hasAutoLayout(), "precondition: " + inUse + " did not load again");

            assertStandsWhereItWasMoved(ui[0], move, "after Stop Using Autonomy while an edit waited, and a load"
                + " (RLV10-B1)");

            assertTheEditStands(ui[0].getAutonomySession(), inUse, edit, "after Stop Using Autonomy and a load"
                + " (RLV10-B1)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * A turn the railway owes when Stop Using Autonomy is confirmed during a run is written into the configuration it
     * leaves (RLV10-C1): the train faces the way it came in.  The turns are written only while the railway is idle, so
     * during a run every one made since it started is still owed, and Unload dropped them with the railway: the
     * configuration had the turned train facing the way it arrived, and the next dispatch was offered paths for that
     * heading (OB-189).
     *
     * MUTATION: leave the turns on the railway Unload drops, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnUnloadWritesTheTurnsTheRailwayOwes() throws Exception
    {
        turnOwedThen(true);
    }

    /**
     * The same where another configuration is chosen (RLV10-C1): the turns are the configuration left's, written into it
     * with its fold.
     *
     * MUTATION: leave the turns on the railway the choice replaces, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testChoosingAnotherConfigurationWritesTheTurnsTheRailwayOwes() throws Exception
    {
        turnOwedThen(false);
    }

    /** The two above: a turn owed during a run, then Unload, or another configuration chosen. */
    private static void turnOwedThen(boolean unload) throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            if (!unload) addAConfigurationLikeTheOneInUse("Other");

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            final Object[] dispatched = dispatchATrain(ui[0], null);

            driving = (Thread) dispatched[3];

            // A STANDING TRAIN ON A SPLIT SQUARE, with the side it came in by recorded and the setup facing another way
            org.traincontrol.automation.Point at = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                org.traincontrol.base.Locomotive l = p.getCurrentLocomotive();

                if (at != null || l == null || l.getName().equals(dispatched[0]) || p.getArrivedFrom() == null) continue;

                if (railway.getActiveLocomotives().containsKey(l)) continue;

                TileKey square = session.getStationIndex().squareOf(p.getName());

                if (square == null || p.getArrivedFrom().equals(facingIn(session, inUse, square))) continue;

                for (org.traincontrol.automation.Point q : railway.getPoints())
                {
                    if (q != p && p.isSamePlaceAs(q)) at = p;
                }
            }

            assertNotNull(at, "precondition: no standing train on a split square with a side recorded that its setup"
                + " facing is not");

            final String turned = at.getCurrentLocomotive().getName();
            final String came = at.getArrivedFrom();
            final TileKey square = session.getStationIndex().squareOf(at.getName());

            java.util.Map<String, String> owed = new java.util.HashMap<>();

            owed.put(turned, at.getName());

            railway.restoreReversalsOnArrival(owed);

            // THE DRAIN DECLINES WHILE THE RAILWAY RUNS: still owed
            SwingUtilities.invokeAndWait(() -> ui[0].reconcileFacingWhenIdle());

            assertEquals(railway.turnedOnArrivalAt(turned), at.getName(), "precondition: the turn was written while "
                + dispatched[0] + " was under way");

            if (unload)
            {
                answeringYes(() -> ui[0].unloadAutonomy());

                assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: Unload left a railway loaded");
            }
            else
            {
                answeringYes(() -> ui[0].getAutonomyViewerPanel().load("Other", true));

                assertEquals(ui[0].getActiveDiagramConfiguration(), "Other", "precondition: Other was not chosen");
            }

            assertEquals(facingIn(ui[0].getAutonomySession(), inUse, square), came, inUse + " has " + turned + ", turned"
                + " round at " + square + " during the run, facing the way it arrived after "
                + (unload ? "Stop Using Autonomy" : "another configuration was chosen") + " - the turn it owed was"
                + " dropped with the railway (RLV10-C1)");
        }
        finally
        {
            if (driving != null) driving.interrupt();

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The facing a configuration records for a square, or null. */
    private static String facingIn(AutonomySession session, String configuration, TileKey square)
    {
        org.json.JSONObject points = session.getStore().getConfiguration(configuration).optJSONObject("points");

        org.json.JSONObject extras = points == null ? null : points.optJSONObject(square.toString());

        return extras == null || !extras.has(AutonomyBuilder.FACING) ? null : extras.getString(AutonomyBuilder.FACING);
    }

    /** A second configuration in the sandbox, a copy of the one in use under another name. */
    private static void addAConfigurationLikeTheOneInUse(String name) throws Exception
    {
        String root = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        assertTrue(root.contains("tc-sandbox-layout"), "precondition: the layout preference is not the sandbox: " + root);

        File folder = new File(root, "config/autonomy");

        File[] configurations = folder.listFiles((dir, file) -> file.startsWith("configuration-") && file.endsWith(".json"));

        assertTrue(configurations != null && configurations.length > 0, "precondition: the sandbox has no configuration");

        org.json.JSONObject copy = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(
            configurations[0].toPath()), java.nio.charset.StandardCharsets.UTF_8));

        copy.put("name", name);

        java.nio.file.Files.write(new File(folder, "configuration-" + name + ".json").toPath(),
            copy.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * The editor opened for a finding outlines what the finding names - its own square and those beside it - and a
     * finding that names nothing beside its own is shown, not outlined (RLV10-C3, RLV10-C5): the editor's own list does
     * the same.  The Auto tab's list opens the editor this way, with each row's squares, an empty list for most.
     *
     * MUTATION: open without the outline, or outline a finding that names nothing, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheEditorOpenedForAFindingOutlinesWhatItNames() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            // Two stations of one page
            TileKey first = null;
            TileKey second = null;

            for (org.traincontrol.automation.Point p : ui[0].getModel().getAutoLayout().getPoints())
            {
                TileKey square = p.isDestination() ? session.getStationIndex().squareOf(p.getName()) : null;

                if (square == null) continue;

                if (first == null) first = square;
                else if (second == null && !square.equals(first) && square.getPage().equals(first.getPage())) second = square;
            }

            assertNotNull(second, "precondition: no two stations on one page");

            org.traincontrol.gui.AutonomyEditorPanel shown = openTheEditorFor(ui[0], first, Collections.emptyList());

            assertFalse(shown.isOutlined(first), "the editor opened for a finding that names nothing beside its own"
                + " outlines its square, as it outlines the bulk selection - the editor's own list only shows it"
                + " (RLV10-C3)");

            closeTheEditor(ui[0]);

            shown = openTheEditorFor(ui[0], first, Collections.singletonList(second));

            assertTrue(shown.isOutlined(first) && shown.isOutlined(second), "the editor opened for a finding that names"
                + " another square does not outline both (RLV9-C5, RLV10-C5)");

            closeTheEditor(ui[0]);
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

    /** Opens the autonomy editor at a square, as a finding's click does, and waits for it to settle. */
    static org.traincontrol.gui.AutonomyEditorPanel openTheEditorFor(TrainControlUI ui, TileKey at,
        List<TileKey> related) throws Exception
    {
        answeringYes(() -> ui.openAutonomyEditor(at, related));

        java.lang.reflect.Field open = TrainControlUI.class.getDeclaredField("openEditor");

        open.setAccessible(true);

        org.traincontrol.gui.LayoutEditor editor = null;

        long until = System.currentTimeMillis() + 20000;

        while (System.currentTimeMillis() < until)
        {
            SwingUtilities.invokeAndWait(() -> { });

            editor = (org.traincontrol.gui.LayoutEditor) open.get(ui);

            if (editor != null && editor.getAutonomyPanel() != null) break;

            Thread.sleep(100);
        }

        assertTrue(editor != null && editor.getAutonomyPanel() != null, "precondition: the autonomy editor did not open");

        // The reveal and the outline are posted twice over behind the editor's own build
        for (int turn = 0; turn < 10; turn++) SwingUtilities.invokeAndWait(() -> { });

        Thread.sleep(500);

        for (int turn = 0; turn < 10; turn++) SwingUtilities.invokeAndWait(() -> { });

        return editor.getAutonomyPanel();
    }

    /** Closes the editor `openTheEditorFor` opened. */
    static void closeTheEditor(TrainControlUI ui) throws Exception
    {
        java.lang.reflect.Field open = TrainControlUI.class.getDeclaredField("openEditor");

        open.setAccessible(true);

        final org.traincontrol.gui.LayoutEditor editor = (org.traincontrol.gui.LayoutEditor) open.get(ui);

        if (editor == null) return;

        answeringYes(() ->
        {
            editor.dispose();

            ui.autonomyEditorClosed();
        });

        for (int turn = 0; turn < 10; turn++) SwingUtilities.invokeAndWait(() -> { });

        open.set(ui, null);
    }

    /**
     * No setup edit is made while trains run (Adam, 2026-09-28: *"There should be no setup edit possible during a
     * run"*).  The diagram's Autonomy Setup menu is offered only at rest; one opened then and clicked after a run began
     * wrote the setup, and the rebuild it asked for was declined - an edit waiting against a running railway, which every
     * door that keeps where the trains stand then had to work around (RLV11-B1, RLV11-B2).  Refused at the click, and
     * said.
     *
     * MUTATION: let the menu's items write while trains run, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testASetupMenuOpenedAtRestRefusesOnceTrainsRun() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            // A STATION'S SETUP MENU, opened at rest
            TileKey station = null;

            for (org.traincontrol.automation.Point p : ui[0].getModel().getAutoLayout().getPoints())
            {
                TileKey s = p.isDestination() ? session.getStationIndex().squareOf(p.getName()) : null;

                if (station == null && s != null) station = s;
            }

            assertNotNull(station, "precondition: no station on his railway");

            final TileKey at = station;

            final javax.swing.JPopupMenu[] menu = new javax.swing.JPopupMenu[1];

            SwingUtilities.invokeAndWait(() -> menu[0] = ui[0].buildAutonomyTileMenu(at));

            final javax.swing.JMenuItem choose = itemCalled(menu[0], I18n.t("autosetup.ui.menuAutoDestination"));

            assertNotNull(choose, "precondition: the setup menu of " + at + " has no item for whether autonomy may choose"
                + " it");

            final boolean was = session.isAutoDestination(at);

            // THEN A RUN BEGINS
            final Object[] dispatched = dispatchATrain(ui[0], null);

            driving = (Thread) dispatched[3];

            assertTrue(ui[0].getModel().isAutonomyRunning(), "precondition: autonomy does not read busy with " + dispatched[0]
                + " under way");

            List<String> asked = answeringYes(() -> choose.doClick());

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertEquals(session.isAutoDestination(at), was, "a setup menu opened at rest wrote the setup when clicked"
                + " while " + dispatched[0] + " was under way - no setup edit is made during a run: " + asked);

            assertFalse((Boolean) declined.get(ui[0]), "a setup menu clicked during a run left an edit waiting against"
                + " the running railway");

            assertTrue(asked.contains(I18n.t("autolayout.errorCannotEditWhileRunning")), "a setup menu clicked during a"
                + " run did not say why it did nothing: " + asked);
        }
        finally
        {
            if (driving != null) driving.interrupt();

            if (ui[0] != null) declined.set(ui[0], false);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** The first item anywhere in this menu with this text. */
    static javax.swing.JMenuItem itemCalled(java.awt.Container menu, String text)
    {
        if (menu == null) return null;

        java.awt.Component[] parts = menu instanceof javax.swing.JMenu
            ? ((javax.swing.JMenu) menu).getMenuComponents() : menu.getComponents();

        for (java.awt.Component part : parts)
        {
            if (part instanceof javax.swing.JMenu)
            {
                javax.swing.JMenuItem found = itemCalled((javax.swing.JMenu) part, text);

                if (found != null) return found;
            }
            else if (part instanceof javax.swing.JMenuItem && text.equals(((javax.swing.JMenuItem) part).getText()))
            {
                return (javax.swing.JMenuItem) part;
            }
        }

        return null;
    }

    /**
     * Writing the turns the railway owes during a run leaves a train that turned and has set off again on its path
     * (RLV11-C3).  A train's locked path records it on its start, where it turned, so the write took it for one still
     * standing there and stood it on the copy facing the way it came in - which swept it off every other point of its
     * path while it was under way.
     *
     * MUTATION: write a turn for a train under way, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testWritingTheTurnsLeavesATrainUnderWayOnItsPath() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        Thread driving = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // A STANDING TRAIN ON A SPLIT SQUARE whose recorded side has a copy of its own, not this one, and a path away
            org.traincontrol.automation.Point at = null;
            List<org.traincontrol.automation.Edge> path = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                org.traincontrol.base.Locomotive l = p.getCurrentLocomotive();

                if (at != null || l == null || p.getArrivedFrom() == null || railway.isAlreadyUnderway(l)) continue;

                TileKey square = session.getStationIndex().squareOf(p.getName());

                if (square == null) continue;

                org.traincontrol.automationui.TilePorts.Side came = null;

                for (org.traincontrol.automationui.TilePorts.Side s : org.traincontrol.automationui.TilePorts.Side.values())
                {
                    if (s.name().equals(p.getArrivedFrom())) came = s;
                }

                if (came == null) continue;

                org.traincontrol.automation.Point target = session.copyFacing(square, came, railway);

                if (target == null || target == p) continue;

                List<org.traincontrol.automation.Edge> away = null;

                for (List<org.traincontrol.automation.Edge> candidate : railway.getPossiblePaths(l, false))
                {
                    if (away == null && candidate != null && candidate.size() >= 2 && candidate.get(0).getStart() == p)
                    {
                        away = candidate;
                    }
                }

                if (away == null) continue;

                at = p;
                path = away;
            }

            assertNotNull(at, "precondition: no standing train on a split square whose side has another copy, with a"
                + " path away");

            final org.traincontrol.base.Locomotive train = at.getCurrentLocomotive();
            final List<org.traincontrol.automation.Edge> route = path;

            java.util.Map<String, String> owed = new java.util.HashMap<>();

            owed.put(train.getName(), at.getName());

            railway.restoreReversalsOnArrival(owed);

            // IT SETS OFF AGAIN from where it turned, and waits on a sensor nothing sets
            driving = new Thread(() -> railway.executePath(route, train, 30, null), "dispatched by the claim");

            driving.setDaemon(true);
            driving.start();

            long until = System.currentTimeMillis() + 60000;

            while (!railway.getActiveLocomotives().containsKey(train) && driving.isAlive()
                && System.currentTimeMillis() < until)
            {
                Thread.sleep(50);
            }

            Thread.sleep(1000);

            assertTrue(railway.isAlreadyUnderway(train), "precondition: " + train.getName() + " did not get under way");

            final int before = pointsHeldBy(railway, train);

            assertTrue(before >= 2, "precondition: " + train.getName() + " under way holds " + before + " points");

            final java.lang.reflect.Method write = TrainControlUI.class.getDeclaredMethod("writeTheTurnsOwed");

            write.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    write.invoke(ui[0]);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            assertEquals(pointsHeldBy(railway, train), before, "writing the turns during a run stood "
                + train.getName() + ", under way from where it turned, on another copy of that square - sweeping it off"
                + " the rest of its path, which then reads free (RLV11-C3)");
        }
        finally
        {
            if (driving != null) driving.interrupt();

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** How many points of the railway record this train. */
    static int pointsHeldBy(org.traincontrol.automation.Layout railway, org.traincontrol.base.Locomotive train)
    {
        int held = 0;

        for (org.traincontrol.automation.Point p : railway.getPoints())
        {
            if (p.getCurrentLocomotive() == train) held++;
        }

        return held;
    }

    /**
     * A train's arrival after Unload builds no railway (RLV11-C5).  Its thread runs the arrival callback after its last
     * check of whether its railway is still the current one, and the callback asked the model for its railway to decide
     * whether to switch the train's functions off - which builds an empty one where there is none, and `hasAutoLayout`
     * then answered yes about nothing.  The callbacks are fired here as the train's thread fires them.
     *
     * MUTATION: ask `getAutoLayout` in the arrival or the departure callback, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testATrainArrivingAfterUnloadBuildsNoRailway() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            org.traincontrol.base.Locomotive train = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (train == null && p.getCurrentLocomotive() != null) train = p.getCurrentLocomotive();
            }

            assertNotNull(train, "precondition: no train on his railway");

            java.util.function.Consumer<org.traincontrol.base.Locomotive> arrived =
                train.getCallback(org.traincontrol.automation.Layout.CB_ROUTE_END);
            java.util.function.Consumer<org.traincontrol.base.Locomotive> departed =
                train.getCallback(org.traincontrol.automation.Layout.CB_ROUTE_START);

            assertTrue(arrived != null && departed != null, "precondition: " + train.getName() + " has no arrival or"
                + " departure callback");

            // No waiting in the callbacks' delays
            railway.setMinDelay(0);
            railway.setMaxDelay(0);

            answeringYes(() -> ui[0].unloadAutonomy());

            assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: Unload left a railway loaded");

            arrived.accept(train);

            assertFalse(ui[0].getModel().hasAutoLayout(), "a train's arrival after Unload built an empty railway, which"
                + " hasAutoLayout then answers yes about (RLV11-C5)");

            departed.accept(train);

            assertFalse(ui[0].getModel().hasAutoLayout(), "a train's departure after Unload built an empty railway"
                + " (RLV11-C5)");
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
     * Nothing the station labels ask after Unload builds a railway (RLV11-C6): the update a train's arrival or a
     * placement posts, run after Unload has cleared the model, as it can land.  RLV10-B1's claim caught the labels'
     * builder only when that timing happened; this runs the label code itself.
     *
     * MUTATION: ask `getAutoLayout` in the labels' arrow, their tile lookups or their update, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheStationLabelsBuildNoRailwayAfterUnload() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            org.traincontrol.automation.Point standing = null;

            for (org.traincontrol.automation.Point p : ui[0].getModel().getAutoLayout().getPoints())
            {
                if (standing == null && p.getCurrentLocomotive() != null
                    && session.getStationIndex().squareOf(p.getName()) != null)
                {
                    standing = p;
                }
            }

            assertNotNull(standing, "precondition: no train standing on a station");

            final org.traincontrol.automation.Point at = standing;
            final TileKey square = session.getStationIndex().squareOf(at.getName());

            answeringYes(() -> ui[0].unloadAutonomy());

            assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: Unload left a railway loaded");

            final java.lang.reflect.Method arrow = TrainControlUI.class.getDeclaredMethod("facingArrowOf",
                org.traincontrol.automation.Point.class, TileKey.class);
            final java.lang.reflect.Method labels = TrainControlUI.class.getDeclaredMethod("updateStationLabels",
                org.traincontrol.automation.Point.class);

            arrow.setAccessible(true);
            labels.setAccessible(true);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    labels.invoke(ui[0], at);
                    arrow.invoke(ui[0], at, square);
                    ui[0].getAutonomyPointForTile(square);
                    ui[0].getAutonomyOccupantsForTile(square);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(ui[0].getModel().hasAutoLayout(), "the station labels, updated after Unload, built an empty"
                + " railway, which hasAutoLayout then answers yes about (RLV10-B1's builder, RLV11-C6)");
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
     * Stop Using Autonomy while an edit a run declined waits writes what the railway owns - where the trains stand: the
     * train, the side and road of its tail, and the facing of the copy it stands on (RLV11-C6); and the settings the
     * railway changed since it was built, as every other fold then does (RSA21-C2) - and nothing of the setup's: not a
     * square whose tile is gone, not a home's facing.  RLV10-B1's claim moved a train with no tail onto an unsplit square
     * and waited on a priority, so it could see none of these.
     *
     * MUTATION: write no tail or no facing, or none of the settings, prune squares gone or clear a home's facing in that
     * write, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAnUnloadWhileAnEditWaitsWritesWhatTheRailwayOwnsAndNothingElse() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        java.lang.reflect.Field declined = TrainControlUI.class.getDeclaredField("setupEditDeclinedDuringRun");

        declined.setAccessible(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            // A TRAIN WITH A TAIL on a split square
            org.traincontrol.automation.Point at = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (at != null || p.getCurrentLocomotive() == null || p.getArrivedFrom() == null) continue;

                if (session.getStationIndex().squareOf(p.getName()) == null) continue;

                for (org.traincontrol.automation.Point q : railway.getPoints())
                {
                    if (q != p && p.isSamePlaceAs(q)) at = p;
                }
            }

            assertNotNull(at, "precondition: no train with a tail on a split square");

            final TileKey square = session.getStationIndex().squareOf(at.getName());

            org.json.JSONObject railwaysPoint = null;

            for (Object point : new org.json.JSONObject(railway.toJSON()).getJSONArray("points"))
            {
                if (at.getName().equals(((org.json.JSONObject) point).optString("name")))
                {
                    railwaysPoint = (org.json.JSONObject) point;
                }
            }

            assertTrue(railwaysPoint != null && railwaysPoint.has("arrivedFrom"), "precondition: the railway does not"
                + " write " + at.getName() + "'s side");

            final String side = railwaysPoint.getString("arrivedFrom");
            final Object road = railwaysPoint.opt("arrivedAlong");
            final String facing = session.getStationIndex().facingsAt(square).get(at.getName()) == null ? null
                : session.getStationIndex().facingsAt(square).get(at.getName()).name();

            assertNotNull(facing, "precondition: " + at.getName() + " is a copy with no facing");

            org.json.JSONObject configuration = session.getStore().getConfiguration(inUse);
            org.json.JSONObject points = configuration.getJSONObject("points");

            // THE CONFIGURATION WITHOUT THEM, so the write is what puts them there
            org.json.JSONObject extras = points.getJSONObject(square.toString());

            extras.remove("arrivedFrom");
            extras.remove("arrivedAlong");
            extras.remove(AutonomyBuilder.FACING);

            // A SQUARE WHOSE TILE IS GONE, on a page in play
            final String gone = square.getPage() + ":99,99";

            points.put(gone, new org.json.JSONObject().put("priority", 3));

            // A HOME'S FACING on a station that has no home, which only the whole fold clears
            TileKey other = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                TileKey s = p.isDestination() ? session.getStationIndex().squareOf(p.getName()) : null;

                if (other == null && s != null && !s.equals(square)
                    && (points.optJSONObject(s.toString()) == null || !points.getJSONObject(s.toString()).has("home")))
                {
                    other = s;
                }
            }

            assertNotNull(other, "precondition: no station without a home");

            if (points.optJSONObject(other.toString()) == null) points.put(other.toString(), new org.json.JSONObject());

            points.getJSONObject(other.toString()).put(AutonomyBuilder.HOME_FACING, "N");

            // AND A SETTING the run changed
            final int delayWas = configuration.optJSONObject("globals") == null ? -1
                : configuration.getJSONObject("globals").optInt("maxDelay", -1);

            railway.setMaxDelay(delayWas == 7 ? 8 : 7);

            declined.set(ui[0], true);

            answeringYes(() -> ui[0].unloadAutonomy());

            assertFalse(ui[0].getModel().hasAutoLayout(), "precondition: Unload left a railway loaded");

            org.json.JSONObject after = ui[0].getAutonomySession().getStore().getConfiguration(inUse);
            org.json.JSONObject written = after.getJSONObject("points").getJSONObject(square.toString());

            assertEquals(written.optString("arrivedFrom", null), side, "Stop Using Autonomy while an edit waited did not"
                + " write " + at.getCurrentLocomotive().getName() + "'s tail side on " + square + " (RLV11-C6)");

            if (road != null)
            {
                assertEquals(String.valueOf(written.opt("arrivedAlong")), String.valueOf(road), "Stop Using Autonomy"
                    + " while an edit waited did not write " + at.getCurrentLocomotive().getName() + "'s tail road"
                    + " (RLV11-C6)");
            }

            assertEquals(written.optString(AutonomyBuilder.FACING, null), facing, "Stop Using Autonomy while an edit"
                + " waited did not write the facing of the copy " + at.getCurrentLocomotive().getName() + " stands on"
                + " (RLV11-C6)");

            assertTrue(after.getJSONObject("points").has(gone), "Stop Using Autonomy while an edit waited pruned a"
                + " square whose tile is gone - the setup's business, not where the trains stand (RLV11-C6)");

            assertTrue(after.getJSONObject("points").getJSONObject(other.toString()).has(AutonomyBuilder.HOME_FACING),
                "Stop Using Autonomy while an edit waited cleared a home's facing (RLV11-C6)");

            assertEquals(after.optJSONObject("globals") == null ? -1
                : after.getJSONObject("globals").optInt("maxDelay", -1), delayWas == 7 ? 8 : 7, "Stop Using Autonomy"
                + " while an edit waited dropped a setting the railway changed, which every other fold then keeps"
                + " (RSA21-C2)");
        }
        finally
        {
            if (ui[0] != null) declined.set(ui[0], false);

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
     * An edit a run declined, as RLD4-C3's claim makes one: a priority written into the configuration and not into the
     * running layout, on a station of his railway - and to the file, as a declined edit is, so a reset that reads the
     * setup again finds it.
     *
     * @return the square and the priority written
     */
    private static Object[] anEditWaits(TrainControlUI ui, AutonomySession session, String configuration)
        throws Exception
    {
        TileKey square = null;

        java.util.TreeSet<String> stations = new java.util.TreeSet<>();

        for (org.traincontrol.automation.Point point : ui.getModel().getAutoLayout().getPoints())
        {
            if (point.isDestination()) stations.add(point.getName());
        }

        for (String station : stations)
        {
            square = session.getStationIndex().squareOf(station);

            if (square != null) break;
        }

        assertNotNull(square, "precondition: no station of his railway has a square");

        Integer was = priorityIn(session, configuration, square);

        final int edit = Integer.valueOf(7).equals(was) ? 8 : 7;

        final TileKey at = square;

        SwingUtilities.invokeAndWait(() ->
        {
            session.setPointProperty(at, "priority", edit);

            try
            {
                session.getStore().save();
            }
            catch (java.io.IOException e)
            {
                throw new IllegalStateException(e);
            }
        });

        assertEquals(priorityIn(session, configuration, square), Integer.valueOf(edit), "precondition: the edit was not"
            + " written into " + configuration);

        return new Object[] {square, edit};
    }

    /** The edit `anEditWaits` wrote is still in the configuration. */
    private static void assertTheEditStands(AutonomySession session, String configuration, Object[] edit, String when)
    {
        assertEquals(priorityIn(session, configuration, (TileKey) edit[0]), edit[1], "the edit waiting in " + configuration
            + " for its rebuild is gone " + when + " - the layout built before it was folded over it (RLV7-C5, RLD4-C3)");
    }

    /** The configuration still has the moved train where it set off: only the running layout was moved. */
    private static void assertTheConfigurationHasItWhereItSetOff(AutonomySession session, String configuration,
        String[] move)
    {
        TileKey was = session.getStationIndex().squareOf(move[1]);

        assertNotNull(was, "precondition: " + move[1] + " has no square");

        org.json.JSONObject extras = session.getStore().getConfiguration(configuration).getJSONObject("points")
            .optJSONObject(was.toString());

        assertTrue(extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE)
            && move[0].equals(extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")), "precondition: "
            + configuration + " does not have " + move[0] + " where it set off, " + move[1] + ", so a rebuild from it"
            + " could not tell a carried train from a rebuilt one (RLV7-C5)");
    }

    /** How many squares of the configuration place this train. */
    private static int squaresPlacing(AutonomySession session, String configuration, String train)
    {
        int count = 0;

        org.json.JSONObject points = session.getStore().getConfiguration(configuration).optJSONObject("points");

        if (points == null) return 0;

        for (String square : points.keySet())
        {
            org.json.JSONObject extras = points.optJSONObject(square);

            if (extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE)
                && train.equals(extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")))
            {
                count++;
            }
        }

        return count;
    }

    /**
     * Sends one standing train off along a path it would have been misread on (RLV7-C1): one whose destination, a
     * station, is the point of the path the layout's own order meets last - the one the old reading kept it on.  It waits
     * on a sensor nothing here sets, so it stays under way, holding every point of its path.
     *
     * @param notThis a train to leave standing
     * @return its name, where it set off, where it is bound, and the thread driving it
     */
    static Object[] dispatchATrain(TrainControlUI ui, String notThis) throws Exception
    {
        final org.traincontrol.automation.Layout railway = ui.getModel().getAutoLayout();

        final List<org.traincontrol.automation.Point> order = new ArrayList<>(railway.getPoints());

        for (org.traincontrol.automation.Point standing : order)
        {
            final org.traincontrol.base.Locomotive train = standing.getCurrentLocomotive();

            if (train == null || train.getName().equals(notThis)) continue;

            for (final List<org.traincontrol.automation.Edge> path : railway.getPossiblePaths(train, false))
            {
                if (path == null || path.isEmpty()) continue;

                org.traincontrol.automation.Point from = path.get(0).getStart();
                org.traincontrol.automation.Point bound = path.get(path.size() - 1).getEnd();

                org.traincontrol.automation.Point last = from;

                for (org.traincontrol.automation.Edge e : path)
                {
                    if (order.indexOf(e.getEnd()) > order.indexOf(last)) last = e.getEnd();
                }

                if (last != bound || !bound.isDestination() || from == bound) continue;

                Thread driving = new Thread(() -> railway.executePath(path, train, 30, null), "dispatched by the claim");

                driving.setDaemon(true);
                driving.start();

                long until = System.currentTimeMillis() + 60000;

                while (!railway.getActiveLocomotives().containsKey(train) && driving.isAlive()
                    && System.currentTimeMillis() < until)
                {
                    Thread.sleep(50);
                }

                // still under way a moment later: nothing it waits on is set
                Thread.sleep(1000);

                if (railway.getActiveLocomotives().containsKey(train) && driving.isAlive())
                {
                    return new Object[] {train.getName(), from.getName(), bound.getName(), driving};
                }

                driving.interrupt();
            }
        }

        throw new AssertionError("precondition: no standing train of his could be sent along a path the old reading"
            + " misplaces it on");
    }

    /** Answers Yes to every question asked, until `going` is lowered; what was asked goes into `asked`. */
    static Thread startAnsweringYes(final List<String> asked, final java.util.concurrent.atomic.AtomicBoolean going)
    {
        Thread answering = new Thread(() ->
        {
            Set<Object> handled = Collections.newSetFromMap(new java.util.IdentityHashMap<>());

            while (going.get())
            {
                try
                {
                    Thread.sleep(150);
                }
                catch (InterruptedException stop)
                {
                    return;
                }

                // ASKED AGAIN AFTER THE SLEEP (RSA18-C3): told to stop during it, one more scan answered the next door's
                // dialog - a timetable's stop - which the claim waiting for it then never saw
                if (!going.get()) return;

                for (Window window : Window.getWindows())
                {
                    if (!window.isShowing() || !(window instanceof JDialog)) continue;

                    final JOptionPane pane = find(((JDialog) window).getContentPane(), JOptionPane.class);

                    if (pane == null || !handled.add(pane)) continue;

                    asked.add(String.valueOf(pane.getMessage()));

                    final Object[] options = pane.getOptions();

                    SwingUtilities.invokeLater(() -> pane.setValue(options != null && options.length > 0
                        ? options[0] : Integer.valueOf(JOptionPane.OK_OPTION)));
                }
            }
        }, "answering yes");

        answering.setDaemon(true);
        answering.start();

        return answering;
    }

    /** Moves one standing train, on the running railway only, to an empty station; its name, where it was, where it is. */
    private static String[] moveAStandingTrain(TrainControlUI ui, AutonomySession session) throws Exception
    {
        return moveAStandingTrain(ui, session, null);
    }

    /** The same, leaving the named train where it is. */
    private static String[] moveAStandingTrain(TrainControlUI ui, AutonomySession session, String notThis)
        throws Exception
    {
        final org.traincontrol.automation.Layout railway = ui.getModel().getAutoLayout();

        org.traincontrol.automation.Point from = null;
        org.traincontrol.automation.Point to = null;

        for (org.traincontrol.automation.Point point : railway.getPoints())
        {
            if (point.getCurrentLocomotive() != null && from == null
                && (notThis == null || !notThis.equals(point.getCurrentLocomotive().getName())))
            {
                from = point;
            }
        }

        assertNotNull(from, "precondition: no train stands on his railway");

        for (org.traincontrol.automation.Point point : railway.getPoints())
        {
            if (to == null && point.isDestination() && point.isActive() && point.getCurrentLocomotive() == null
                && !point.isSamePlaceAs(from) && session.getStationIndex().squareOf(point.getName()) != null)
            {
                to = point;
            }
        }

        assertNotNull(to, "precondition: no empty station on his railway");

        final String moved = from.getCurrentLocomotive().getName();
        final String toName = to.getName();

        SwingUtilities.invokeAndWait(() -> railway.moveLocomotive(moved, toName, false));

        return new String[] {moved, from.getName(), toName};
    }

    /** The moved train stands, on the running railway, where it was moved. */
    private static void assertStandsWhereItWasMoved(TrainControlUI ui, String[] move, String when)
    {
        org.traincontrol.automation.Layout after = ui.getModel().getAutoLayout();

        org.traincontrol.automation.Point where = after.getLocomotiveLocation(ui.getModel().getLocByName(move[0]));

        assertNotNull(where, move[0] + " is off the railway " + when);

        assertEquals(where.getName(), move[2], move[0] + ", moved by the run to " + move[2] + ", is back on " + move[1] + " "
            + when + " - the square it stands on reads free");
    }

    /** Runs this on the event thread, answering Yes to every question it asks; what it asked. */
    static List<String> answeringYes(Runnable onTheEventThread) throws Exception
    {
        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        Thread answering = startAnsweringYes(asked, going);

        final CountDownLatch done = new CountDownLatch(1);

        try
        {
            SwingUtilities.invokeLater(() ->
            {
                try
                {
                    onTheEventThread.run();
                }
                finally
                {
                    done.countDown();
                }
            });

            assertTrue(done.await(180, TimeUnit.SECONDS), "the load did not finish; asked: " + asked);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            return new ArrayList<>(asked);
        }
        finally
        {
            going.set(false);

            // AND GONE BEFORE THE NEXT DOOR (RSA18-C3)
            answering.join(1000);
        }
    }

    /**
     * A train an old file places, on a square a train facing the other way stood on since, faces the way the file ran
     * it (RLA2-B3).
     *
     * The import asked the file for a placed train's facing only where the square had none recorded, and an empty square
     * keeps its last occupant's - so a second import into a configuration that had been run stood its train facing the
     * way another train had stood, and autonomy would drive it off the wrong end.  A facing on an empty square is no
     * evidence about the train the file puts there.
     *
     * MUTATION: ask the file only where the square has no facing, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testASecondImportFacesItsTrainTheWayTheFileRanIt() throws Exception
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

            importFromTheMenu(ui[0], MT491, "MT-491 facings");

            AutonomySession session = ui[0].getAutonomySession();

            org.json.JSONObject points = session.getStore().getConfiguration("MT-491 facings").getJSONObject("points");

            // A PLACED TRAIN ON A SQUARE THAT HAS COPIES BOTH WAYS, and the way the file ran it.
            TileKey square = null;
            String train = null;
            String ran = null;
            String other = null;

            for (TileKey key : session.getReducer().getPoints().keySet())
            {
                org.json.JSONObject extras = points.optJSONObject(key.toString());

                if (extras == null || !extras.has(AutonomyBuilder.LOCOMOTIVE) || !extras.has(AutonomyBuilder.FACING)) continue;

                String facing = extras.getString(AutonomyBuilder.FACING);

                for (Object side : session.facingsFor(key).values())
                {
                    if (!String.valueOf(side).equals(facing) && other == null)
                    {
                        square = key;
                        train = extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).getString("name");
                        ran = facing;
                        other = String.valueOf(side);
                    }
                }

                if (square != null) break;
            }

            assertNotNull(square, "precondition: no train the file placed stands on a square with copies both ways");

            // TAKEN OFF, and the square left facing the other way - as a train standing there the other way leaves it.
            points.getJSONObject(square.toString()).remove(AutonomyBuilder.LOCOMOTIVE);
            points.getJSONObject(square.toString()).put(AutonomyBuilder.FACING, other);

            importFromTheMenu(ui[0], MT491, "MT-491 facings");

            session = ui[0].getAutonomySession();

            org.json.JSONObject after = session.getStore().getConfiguration("MT-491 facings").getJSONObject("points")
                .getJSONObject(square.toString());

            assertEquals(after.getJSONObject(AutonomyBuilder.LOCOMOTIVE).getString("name"), train, "precondition: the"
                + " second import did not place " + train + " on its square again");

            assertEquals(after.getString(AutonomyBuilder.FACING), ran, "the second import stood " + train + " facing "
                + after.getString(AutonomyBuilder.FACING) + ", the way the square's last occupant faced, where the file"
                + " ran it " + ran + " (RLA2-B3)");
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
     * A home the configuration already has for a train is kept, and the message says the file's was not taken
     * (RLA2-C3, RLD2-C6).
     *
     * The file gives the train it places at Tunnel a home at BottomMainB; the configuration it was imported into has since moved that home to
     * BottomMainC; a second import leaves one home, at BottomMainC, and names the train.  The home half of RLA-B2 had no
     * claim, and the dropped home was counted where nothing read it.
     *
     * MUTATION: start the homes check empty again, or leave the kept homes unsaid, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testASecondImportKeepsAHomeTheConfigurationHas() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        assertTrue(MT298.isFile(), "precondition: the MT-298 file is not in docs/manual-tests/files");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        // HIS FILE, WITH A HOME AT BOTTOMMAINB FOR THE TRAIN IT PLACES AT TUNNEL - named from the file, since a
        // locomotive's name written here reads as a finding's id to the citation census.
        File homed = File.createTempFile("tc-homed-autonomy", ".json");

        org.json.JSONObject file = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(MT298.toPath()),
            java.nio.charset.StandardCharsets.UTF_8));

        String atTunnel = null;

        for (int i = 0; i < file.getJSONArray("points").length(); i++)
        {
            org.json.JSONObject point = file.getJSONArray("points").getJSONObject(i);

            if ("Tunnel".equals(point.optString("name")) && point.has("loc"))
            {
                atTunnel = point.getJSONObject("loc").getString("name");
            }
        }

        assertNotNull(atTunnel, "precondition: the MT-298 file places no train at Tunnel");

        final String train = atTunnel;

        boolean given = false;

        for (int i = 0; i < file.getJSONArray("points").length(); i++)
        {
            org.json.JSONObject point = file.getJSONArray("points").getJSONObject(i);

            if ("BottomMainB".equals(point.optString("name"))) { point.put("home", train); given = true; }
        }

        assertTrue(given, "precondition: the MT-298 file has no BottomMainB to give a home");

        java.nio.file.Files.write(homed.toPath(), file.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            importFromTheMenu(ui[0], homed, "MT-298 homes");

            AutonomySession session = ui[0].getAutonomySession();

            TileKey mainB = null;
            TileKey mainC = null;

            for (TileKey key : session.getStore().getNamedTiles())
            {
                if ("BottomMainB".equals(session.getStore().getPointName(key))) mainB = key;
                if ("BottomMainC".equals(session.getStore().getPointName(key))) mainC = key;
            }

            assertTrue(mainB != null && mainC != null, "precondition: no BottomMainB or BottomMainC on his railway");

            org.json.JSONObject points = session.getStore().getConfiguration("MT-298 homes").getJSONObject("points");

            assertEquals(homesOf(points, train), Collections.singletonList(mainB.toString()), "precondition: the first"
                + " import did not give " + train + " its home at BottomMainB");

            // THE HOME MOVED, in that configuration, to BottomMainC.
            points.getJSONObject(mainB.toString()).remove("home");

            if (!points.has(mainC.toString())) points.put(mainC.toString(), new org.json.JSONObject());

            points.getJSONObject(mainC.toString()).put("home", train);

            List<String> said = importFromTheMenu(ui[0], homed, "MT-298 homes");

            session = ui[0].getAutonomySession();

            points = session.getStore().getConfiguration("MT-298 homes").getJSONObject("points");

            assertEquals(homesOf(points, train), Collections.singletonList(mainC.toString()), "the second import gave "
                + train + " a second home, or took the one the configuration had (RLA-B2)");

            String kept = I18n.f("autosetup.ui.infoLegacyHomesKept", train);

            assertTrue(said.stream().anyMatch(message -> message.contains(kept)), "the import did not say it kept the home"
                + " the configuration already had for " + train + " (RLA2-C3): " + said);
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

            if (!homed.delete()) homed.deleteOnExit();
        }
    }

    /**
     * The first square of his railway the old file gives a priority - by the name the file and the setup share - and
     * that priority, or null.
     */
    private static Object[] priorityTheFileCarries(AutonomySession session, File file) throws Exception
    {
        org.json.JSONArray points = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),
            java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("points");

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.getJSONObject(i);

            if (point.optInt("priority", 0) == 0) continue;

            for (TileKey key : session.getStore().getNamedTiles())
            {
                if (point.optString("name").equals(session.getStore().getPointName(key)))
                {
                    return new Object[] {key, Integer.valueOf(point.getInt("priority"))};
                }
            }
        }

        return null;
    }

    /** A station of his railway the old file names with no priority, or null. */
    private static TileKey aStationTheFileGivesNoPriority(AutonomySession session, File file) throws Exception
    {
        org.json.JSONArray points = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),
            java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("points");

        Set<String> prioritised = new java.util.HashSet<>();

        for (int i = 0; i < points.length(); i++)
        {
            if (points.getJSONObject(i).optInt("priority", 0) != 0) prioritised.add(points.getJSONObject(i).optString("name"));
        }

        for (TileKey key : session.getStore().getNamedTiles())
        {
            String name = session.getStore().getPointName(key);

            if (name != null && !prioritised.contains(name) && session.getStore().isStation(key)) return key;
        }

        return null;
    }

    /** The priority a configuration gives a square, or null where it states none. */
    private static Integer priorityIn(AutonomySession session, String configuration, TileKey square)
    {
        org.json.JSONObject point = session.getStore().getConfiguration(configuration).getJSONObject("points")
            .optJSONObject(square.toString());

        return point == null || !point.has("priority") ? null : Integer.valueOf(point.getInt("priority"));
    }

    /** The squares a configuration gives this train as its home. */
    private static List<String> homesOf(org.json.JSONObject points, String train)
    {
        List<String> out = new ArrayList<>();

        for (String square : points.keySet())
        {
            org.json.JSONObject extras = points.optJSONObject(square);

            if (extras != null && train.equals(extras.optString("home", null))) out.add(square);
        }

        return out;
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
    static TrainControlUI openTheWindow() throws Exception
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
     * Presses Import on the Autonomy menu's panel and answers everything it asks: the file, the name, Yes to the question
     * about a configuration of that name, and OK to every message - whose texts are returned, in order.
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
        Answerer answerer = pressImport(ui, file, name, no);

        assertTrue(answerer.chose, "precondition: the import never showed the file chooser");
        assertTrue(answerer.named, "precondition: the import never asked for the configuration's name");

        return new ArrayList<>(answerer.said);
    }

    /**
     * Presses Import and answers whatever it asks, as above - where it may refuse before it asks anything (RSA4-C5).
     *
     * @return what it was answered: what it said, and whether it asked for a file and a name
     */
    private static Answerer pressImport(TrainControlUI ui, File file, String name, Set<String> no) throws Exception
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

            return answerer;
        }
        finally
        {
            answerer.running = false;

            // AND GONE before anything else is pressed, so it cannot answer the next import's dialogs (OB-323)
            answering.join(5000);
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

                // TOLD TO STOP WHILE ASLEEP: the next import's dialogs are not this one's to answer (OB-323 - its chooser
                // now shows in tens of milliseconds, inside this sleep)
                if (!running) return;

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
        return numberIn(said, "autosetup.ui.infoLegacyImported", 1);
    }

    /**
     * The number a sentence of the bundle says in one of its places, read from the first message holding the sentence -
     * or null where none does.
     *
     * @param said the messages
     * @param key the sentence's key
     * @param which the place, as its {n}
     */
    private static Integer numberIn(List<String> said, String key, int which)
    {
        String template = I18n.t(key);

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

            if (String.valueOf(which).equals(slot.group(1))) placedGroup = group;

            from = slot.end();
        }

        regex.append(Pattern.quote(template.substring(from)));

        Pattern pattern = Pattern.compile(regex.toString(), Pattern.DOTALL);

        for (String message : said)
        {
            Matcher m = pattern.matcher(message);

            if (m.find() && placedGroup > 0) return Integer.valueOf(m.group(placedGroup).replaceAll("[^0-9]", ""));
        }

        return null;
    }

    /** The number a counted sentence says, in whichever form the number took (`I18n.countForm`) - or null where none does. */
    private static Integer countIn(List<String> said, String key)
    {
        for (String form : new String[] {"", "One", "Few"})
        {
            Integer n = numberIn(said, key + form, 0);

            if (n != null) return n;
        }

        return null;
    }

    /** The last of these entries that starts with a line, or null. */
    private static String lastStartingWith(List<String> entries, String line)
    {
        String found = null;

        for (String entry : entries)
        {
            if (entry.equals(line) || entry.startsWith(line + "\n")) found = entry;
        }

        return found;
    }

    /** The lines of the import's list of old points that shared a square, in a log. */
    private static int sharedLinesIn(String log)
    {
        String line = I18n.t("autosetup.ui.importSharedSquareLine");

        String regex = "(?m)^  " + Pattern.quote(line.substring(0, line.indexOf("{0}"))) + ".+"
            + Pattern.quote(line.substring(line.indexOf("{0}") + 3, line.indexOf("{1}"))) + ".+"
            + Pattern.quote(line.substring(line.indexOf("{1}") + 3)) + "$";

        Matcher m = Pattern.compile(regex).matcher(log);

        int lines = 0;

        while (m.find()) lines++;

        return lines;
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
    static void putTheFolderBack(String folderWas)
    {
        if (folderWas == null) TrainControlUI.getPrefs().remove(TrainControlUI.LAST_USED_FOLDER);
        else TrainControlUI.getPrefs().put(TrainControlUI.LAST_USED_FOLDER, folderWas);
    }

    // ---------------------------------------------------------------- the log

    /** What the model has logged since this class was loaded - every line the operator's log shows. */
    private static final StringBuilder LOGGED = new StringBuilder();

    /** The same, entry by entry: the main window's log puts each on top whole. */
    private static final List<String> ENTRIES = new ArrayList<>();

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

                        ENTRIES.add(record.getMessage());
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

    /** What the model has logged, entry by entry, once the event thread has caught up. */
    static List<String> loggedEntries() throws Exception
    {
        logged();

        synchronized (LOGGED)
        {
            return new ArrayList<>(ENTRIES);
        }
    }

    static String logged() throws Exception
    {
        for (int turn = 0; turn < 3; turn++) SwingUtilities.invokeAndWait(() -> { });

        synchronized (LOGGED)
        {
            return LOGGED.toString();
        }
    }

    /**
     * A train the run moved onto a station is still on it after that station is renamed from the diagram's setup menu
     * (RSA4-A1): the rebuild the rename makes puts each train back by the name the rename gave the Point it stands on.
     *
     * The door's own path, on his railway: the setup's rename, then the rebuild every setup gesture on the diagram makes
     * - which captures nothing, so where the run left the train lives on the running layout alone.  Put back by the
     * name recorded, the train was left where the setup last had it, and the station it stands on read free.
     *
     * MUTATION: put the trains back at the door by the names recorded alone, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARenameAfterARunKeepsTheTrainOnTheStation() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            // THE RUN'S RESULT, on the running railway alone
            final String[] move = moveAStandingTrain(ui[0], session);

            final TileKey square = session.getStationIndex().squareOf(move[2]);

            assertNotNull(square, "precondition: " + move[2] + " is no square of the diagram");

            final Object before = ui[0].getModel().getAutoLayout();

            final String renamed = "RSA4 renamed station";

            // THE RENAME, as the diagram's setup menu makes it, and the rebuild after it
            SwingUtilities.invokeAndWait(() ->
            {
                session.setPointName(square, renamed);

                ui[0].rebuildRunningLayoutFromSetup(true, null);
            });

            assertTrue(ui[0].getModel().getAutoLayout() != before, "precondition: the rename rebuilt nothing");

            org.traincontrol.automation.Point where = ui[0].getModel().getAutoLayout().getLocomotiveLocation(
                ui[0].getModel().getLocByName(move[0]));

            assertNotNull(where, move[0] + " is off the railway after its station was renamed (RSA4-A1)");

            assertEquals(ui[0].getAutonomySession().getStationIndex().squareOf(where.getName()), square, move[0]
                + ", moved by the run to " + move[2] + ", is on " + where.getName() + " once that station was renamed "
                + renamed + " - where the setup last had it, the station it stands on reading free (RSA4-A1)");

            assertTrue(where.getName().startsWith(renamed), move[0] + " is on " + where.getName() + ", not the renamed"
                + " copy of " + move[2]);
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
     * Clear empties the timetable the configuration keeps as well as the one running, at once (RSA5-C1): the fold keeps
     * the entries of a page ticked out of autonomy for its return, so a Clear pressed while it was out came back with
     * the page.  Cleared in the configuration too, nothing is kept for any page.
     *
     * MUTATION: clear the running timetable alone, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testClearEmptiesTheTimetableTheConfigurationKeeps() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean going = new java.util.concurrent.atomic.AtomicBoolean(true);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            final AutonomySession session = ui[0].getAutonomySession();

            String kept = session.getGlobal("timetable");

            assertTrue(kept != null && new org.json.JSONArray(kept).length() > 0, "precondition: his railway keeps no"
                + " timetable: " + kept);

            startAnsweringYes(asked, going);

            SwingUtilities.invokeAndWait(() -> ui[0].clearTimetable());

            long until = System.currentTimeMillis() + 10000;

            while (System.currentTimeMillis() < until && !ui[0].getModel().getAutoLayout().getTimetable().isEmpty())
            {
                Thread.sleep(50);
            }

            assertTrue(ui[0].getModel().getAutoLayout().getTimetable().isEmpty(), "precondition: Clear did not clear the"
                + " running timetable; asked: " + asked);

            for (int turn = 0; turn < 6; turn++) SwingUtilities.invokeAndWait(() -> { });

            String after = session.getGlobal("timetable");

            assertTrue(after == null || new org.json.JSONArray(after).length() == 0, "Clear left the timetable the"
                + " configuration keeps, which comes back with a page ticked out of autonomy (RSA5-C1): " + after);
        }
        finally
        {
            going.set(false);

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
     * The configuration in the folder's own file - what the next start reads - has the moved train where it was moved.
     *
     * @param folder the railway's folder
     * @param configuration the configuration
     * @param naming a session of that railway, for the square of the Point it was moved to
     * @param move the train, where it was, where it is
     * @param when said when it fails
     * @throws Exception reading the file
     */
    private static void assertTheFileHasItWhereItWasMoved(File folder, String configuration, AutonomySession naming,
        String[] move, String when) throws Exception
    {
        org.traincontrol.automationui.AutonomyCompanionStore onDisk =
            new org.traincontrol.automationui.AutonomyCompanionStore(folder);

        onDisk.load();

        TileKey to = naming.getStationIndex().squareOf(move[2]);

        assertNotNull(to, "precondition: " + move[2] + " has no square");

        org.json.JSONObject points = onDisk.getConfiguration(configuration) == null ? null
            : onDisk.getConfiguration(configuration).optJSONObject("points");

        org.json.JSONObject extras = points == null ? null : points.optJSONObject(to.toString());

        assertTrue(extras != null && extras.has(AutonomyBuilder.LOCOMOTIVE)
            && move[0].equals(extras.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")), "the file the next"
            + " start reads does not have " + move[0] + " where the run left it, " + move[2] + ", " + when + " - it"
            + " stands where it set off there, and the square it stands on reads free");
    }

    /**
     * A copy of a railway's folder, for a test that needs another railway.
     *
     * @param folder the folder
     * @return the copy, in a temporary folder of its own
     * @throws java.io.IOException copying
     */
    private static File aCopyOf(File folder) throws java.io.IOException
    {
        final java.nio.file.Path from = folder.toPath();
        final java.nio.file.Path to = java.nio.file.Files.createTempDirectory("tc-another-railway");

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(from))
        {
            for (java.nio.file.Path each : (Iterable<java.nio.file.Path>) walk::iterator)
            {
                java.nio.file.Path target = to.resolve(from.relativize(each).toString());

                if (java.nio.file.Files.isDirectory(each)) java.nio.file.Files.createDirectories(target);
                else java.nio.file.Files.copy(each, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return to.toFile();
    }

    /**
     * Deletes a copy `aCopyOf` made.
     *
     * @param copy the copy
     */
    private static void deleteTheCopy(File copy)
    {
        if (copy == null || !copy.exists()) return;

        try (java.util.stream.Stream<java.nio.file.Path> walk = java.nio.file.Files.walk(copy.toPath()))
        {
            walk.sorted(java.util.Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete);
        }
        catch (java.io.IOException e)
        {
            // a temporary folder left behind is the system's to clear
        }
    }

    /**
     * Layouts > Delete Current Page leaves nothing of the page in the file the next start reads (RSA22-C2, MT-176: "The
     * deleted page's settings must stay gone"): the fold behind the reset after it wrote the page's trains and its
     * stations' lengths, priorities and homes back under its name.
     *
     * MUTATION: delete the page in the store without marking the session's pages stale, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testADeletedPageLeavesNothingBehindInTheFile() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        final List<String> asked = Collections.synchronizedList(new ArrayList<>());
        final java.util.concurrent.atomic.AtomicBoolean answering = new java.util.concurrent.atomic.AtomicBoolean(true);

        Thread answers = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            ui[0] = openTheWindow();

            AutonomySession session = ui[0].getAutonomySession();

            final String inUse = session.getStore().getActiveConfiguration();

            // A PAGE WITH A SETUP OF ITS OWN, and no train: the trains stand on 1 - Main
            final String going = "2 - Bottom";

            assertTrue(onThePage(session.getStore().getConfiguration(inUse), going) > 0, "precondition: " + inUse
                + " holds nothing on " + going);

            // AND WHAT THE RUN DID ELSEWHERE, on the railway alone (RSA23-C3): a train moved on another page and an entry
            // captured - which the delete's own fold, before it, is now the one thing to write
            final org.traincontrol.automation.Layout railway = ui[0].getModel().getAutoLayout();

            org.traincontrol.automation.Point from = null;
            org.traincontrol.automation.Point to = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (p.getSquare() == null || p.getSquare().startsWith(going + ":")) continue;

                if (from == null && p.getCurrentLocomotive() != null) from = p;
            }

            assertNotNull(from, "precondition: no train stands off " + going);

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (p.getSquare() == null || p.getSquare().startsWith(going + ":")) continue;

                if (to == null && p.isDestination() && p.isActive() && p.getCurrentLocomotive() == null
                    && !p.isSamePlaceAs(from) && session.getStationIndex().squareOf(p.getName()) != null)
                {
                    to = p;
                }
            }

            assertNotNull(to, "precondition: no empty station off " + going);

            final String moved = from.getCurrentLocomotive().getName();
            final String toName = to.getName();
            final TileKey toSquare = session.getStationIndex().squareOf(toName);
            final boolean[] done = new boolean[1];

            SwingUtilities.invokeAndWait(() -> done[0] = railway.moveLocomotive(moved, toName, false));

            assertTrue(done[0], "precondition: " + moved + " was not moved to " + toName);

            java.util.List<org.traincontrol.automation.Edge> path = null;
            org.traincontrol.base.Locomotive driving = null;

            for (org.traincontrol.automation.Point p : railway.getPoints())
            {
                if (path != null || p.getCurrentLocomotive() == null) continue;

                java.util.List<java.util.List<org.traincontrol.automation.Edge>> paths =
                    railway.getPossiblePaths(p.getCurrentLocomotive(), false);

                if (paths != null && !paths.isEmpty())
                {
                    path = paths.get(0);
                    driving = p.getCurrentLocomotive();
                }
            }

            assertNotNull(path, "precondition: no train on the railway has a path to capture");

            final org.traincontrol.automation.TimetablePath entry =
                new org.traincontrol.automation.TimetablePath(driving, path, 0L);

            SwingUtilities.invokeAndWait(() -> railway.getTimetable().add(entry));

            final int entries = railway.getTimetable().size();

            java.lang.reflect.Field list = TrainControlUI.class.getDeclaredField("LayoutList");

            list.setAccessible(true);

            final javax.swing.JComboBox<?> pages = (javax.swing.JComboBox<?>) list.get(ui[0]);

            SwingUtilities.invokeAndWait(() -> pages.setSelectedItem(going));

            assertEquals(String.valueOf(pages.getSelectedItem()), going, "precondition: " + going + " is not the page"
                + " shown");

            java.lang.reflect.Method delete = TrainControlUI.class.getDeclaredMethod("deleteLayoutMenuItemActionPerformed",
                java.awt.event.ActionEvent.class);

            delete.setAccessible(true);

            answers = startAnsweringYes(asked, answering);

            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    delete.invoke(ui[0], (Object) null);
                }
                catch (ReflectiveOperationException e)
                {
                    throw new IllegalStateException(e);
                }
            });

            long until = System.currentTimeMillis() + 30000;

            while (ui[0].getModel().getLayoutList().contains(going) && System.currentTimeMillis() < until)
            {
                Thread.sleep(200);
            }

            for (int turn = 0; turn < 10; turn++) SwingUtilities.invokeAndWait(() -> { });

            assertFalse(ui[0].getModel().getLayoutList().contains(going), "precondition: " + going + " was not deleted: "
                + asked);

            // THE FILE THE NEXT START READS
            org.traincontrol.automationui.AutonomyCompanionStore onDisk =
                new org.traincontrol.automationui.AutonomyCompanionStore(sandbox.getFolder());

            onDisk.load();

            assertEquals(onThePage(onDisk.getConfiguration(inUse), going), 0, "the file the next start reads still holds "
                + going + "'s trains and settings after the page was deleted - the fold behind the reset wrote them back"
                + " (RSA22-C2, MT-176)");

            // AND KEEPS WHAT THE RUN DID ELSEWHERE (RSA23-C3)
            org.json.JSONObject there = onDisk.getConfiguration(inUse).getJSONObject("points")
                .optJSONObject(toSquare.toString());

            assertTrue(there != null && there.has(AutonomyBuilder.LOCOMOTIVE)
                && moved.equals(there.getJSONObject(AutonomyBuilder.LOCOMOTIVE).optString("name")), "the file the next"
                + " start reads does not have " + moved + " where the run left it, " + toSquare + ", after a page was"
                + " deleted (RSA23-C3)");

            org.json.JSONObject globals = onDisk.getConfiguration(inUse).optJSONObject("globals");

            assertEquals(globals == null || !globals.has("timetable") ? 0 : globals.getJSONArray("timetable").length(),
                entries, "the file the next start reads lost the timetable the run captured after a page was deleted"
                + " (RSA23-C3)");
        }
        finally
        {
            answering.set(false);

            if (answers != null) answers.join(5000);

            putTheFolderBack(folderWas);

            if (ui[0] != null)
            {
                final TrainControlUI closing = ui[0];

                SwingUtilities.invokeAndWait(() -> closing.dispose());
            }

            if (sandbox != null) sandbox.close();
        }
    }

    /** How many squares of a configuration are on the page by that name. */
    private static int onThePage(org.json.JSONObject configuration, String page)
    {
        org.json.JSONObject points = configuration == null ? null : configuration.optJSONObject("points");

        int count = 0;

        if (points != null)
        {
            for (String key : points.keySet())
            {
                if (key.startsWith(page + ":")) count++;
            }
        }

        return count;
    }
}
