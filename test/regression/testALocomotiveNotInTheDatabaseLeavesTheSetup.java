package regression;

import java.awt.Container;
import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.util.I18n;
import static regression.testTheImportDoorReadsAnOldFile.importFromTheMenu;
import static regression.testTheImportDoorReadsAnOldFile.loggedEntries;
import static regression.testTheImportDoorReadsAnOldFile.openTheWindow;
import static regression.testTheImportDoorReadsAnOldFile.putTheFolderBack;
import static regression.testTheImportDoorReadsAnOldFile.textOf;

/**
 * A locomotive the database does not hold leaves the setup when a configuration naming it is loaded or imported (OB-349).
 *
 * Adam: *"suppose we import an autonomy layout with a locomotive that is not currently in the locomotive database placed on
 * a station.  when loaded, the station correctly shows empty on its label in the track diagram viewer, but right-clicking
 * that square still shows the "&lt;locomotive&gt; is facing" option, greyed out, with the name of the nonexistent
 * locomotive."*  The loader dropped the train from the railway and the setup kept it, so every reader of the setup showed
 * it, and every save wrote it back.  His ruling: *"make sure the removed locomotives are logged and that the user is
 * advised in a popup on import (manage its height if there a many locs)."*
 *
 * On the live snapshot's window, with the import door's helpers.  A class of its own, as the import door's is full.
 *
 * @author Adam
 */
public class testALocomotiveNotInTheDatabaseLeavesTheSetup
{
    /** A name no fixture database holds. */
    private static final String GONE = "OB-349 Gone";

    /** Where the frozen railway stands 75 407 DB, and homes it. */
    private static final TileKey STANDS = new TileKey("1 - Main", 20, 12);

    /** Where it stands 2-8-4 3505 SP, a train the database does hold, and homes it. */
    private static final TileKey KEPT = new TileKey("1 - Main", 0, 11);

    /** A station with a list of locomotives kept out. */
    private static final TileKey LISTED = new TileKey("1 - Main", 18, 7);

    /**
     * Loaded at start, the configuration lets go of a train standing on a station, its home and its name on another's list
     * - and holds nothing else less: the station keeps its name and its settings, and a train the database does hold is
     * where it was.  The facing menu and the captions are gone with it, the file no longer names it, and the log says so
     * once and one dialog does, naming it.  Then an ordinary reload does the same for a home given to another such name,
     * and says nothing more of the first.
     *
     * MUTATION: take the call out of `AutonomyViewerPanel.loadPrepared`, or the dialog out of it, and this fails.
     *
     * @throws Exception from the window
     */
    @Test
    public void testAStartUpLetsGoOfAStandingTrainTheDatabaseDoesNotHold() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String loadAutonomyWas = TrainControlUI.getPrefs().get(TrainControlUI.AUTO_LOAD_AUTONOMY, null);

        Dismisser dialogs = null;

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            File file = new File(sandbox.getFolder(), "config/autonomy/configuration-Main.json");

            org.json.JSONObject configuration = read(file);

            org.json.JSONObject points = configuration.getJSONObject("points");

            org.json.JSONObject stands = points.getJSONObject(STANDS.toString());

            assertEquals(stands.getJSONObject("loc").getString("name"), "75 407 DB", "precondition: the frozen railway no"
                + " longer stands 75 407 DB at " + STANDS);
            assertEquals(stands.getString("home"), "75 407 DB", "precondition: the frozen railway no longer homes 75 407 DB"
                + " at " + STANDS);
            assertNotNull(stands.opt("arrivedFrom"), "precondition: the frozen railway records no arrival at " + STANDS);

            // RENAMED, as a locomotive sold since the file was written: standing, homed and on another station's list
            stands.getJSONObject("loc").put("name", GONE);
            stands.put("home", GONE);

            org.json.JSONArray listed = points.getJSONObject(LISTED.toString()).getJSONArray("excludedLocs");

            List<Object> listedBefore = listed.toList();

            listed.put(GONE);

            Files.write(file.toPath(), configuration.toString(4).getBytes(StandardCharsets.UTF_8));

            TrainControlUI.getPrefs().putBoolean(TrainControlUI.AUTO_LOAD_AUTONOMY, true);

            int logBefore = loggedEntries().size();

            dialogs = Dismisser.start();

            ui[0] = openTheWindow();

            for (int wait = 0; wait < 200 && ui[0].getActiveDiagramConfiguration() == null; wait++)
            {
                Thread.sleep(100);
            }

            assertEquals(ui[0].getActiveDiagramConfiguration(), "Main", "precondition: the start did not load Main");

            final AutonomySession session = ui[0].getAutonomySession();

            // GONE FROM THE SETUP: the train, its facing and arrival, its home, its name on the list
            assertNull(session.getLocomotiveNameAt(STANDS), "the setup still stands " + GONE + ", a locomotive the database"
                + " does not hold, at " + STANDS + " after the start loaded it");
            assertNull(session.getPointProperty(STANDS, "home"), "the setup still homes " + GONE + " at " + STANDS);
            assertNull(session.getPointProperty(STANDS, "arrivedFrom"), "the setup kept the arrival of a train it no longer"
                + " stands at " + STANDS);
            assertNull(session.getPointProperty(STANDS, "facing"), "the setup kept the facing of a train it no longer"
                + " stands at " + STANDS);
            assertEquals(((org.json.JSONArray) session.getPointProperty(LISTED, "excludedLocs")).toList(), listedBefore,
                "the list at " + LISTED + " still names " + GONE + ", or lost a locomotive the database holds");

            // AND EVERY READER WITH IT: the facing menu, the captions, the placements a build writes
            final javax.swing.JMenu[] facing = new javax.swing.JMenu[1];

            SwingUtilities.invokeAndWait(() -> facing[0] = ui[0].buildAutonomyFacingMenu(STANDS));

            assertTrue(facing[0] == null || !String.valueOf(facing[0].getText()).contains(GONE), "the right-click menu at "
                + STANDS + " still offers " + (facing[0] == null ? null : facing[0].getText()) + " (OB-349)");
            assertNull(ui[0].autonomyCaptionTextAt(STANDS), "the editor's caption at " + STANDS + " still names a train");
            assertNull(ui[0].autonomyHomeAt(STANDS), "the home caption at " + STANDS + " still names " + GONE);
            assertFalse(session.tilesWithALocomotive().contains(STANDS), "the setup's placements still include " + STANDS);
            assertFalse(session.placementsAutonomyWillWrite().containsValue(GONE), "a build would still place " + GONE);

            // THE REST OF THE SQUARE KEPT, and a train the database does hold untouched
            assertTrue(session.getStore().getPointName(STANDS) != null && session.getStore().isStation(STANDS),
                "the station at " + STANDS + " lost its name or stopped being a station with the train");
            assertEquals(session.getPointProperty(STANDS, "maxTrainLength"), stands.opt("maxTrainLength"),
                "the station at " + STANDS + " lost its maximum train length with the train");
            assertEquals(session.getLocomotiveNameAt(KEPT), "2-8-4 3505 SP", "a train the database holds was taken off "
                + KEPT);
            assertEquals(session.getPointProperty(KEPT, "home"), "2-8-4 3505 SP", "a home the database holds was taken"
                + " off " + KEPT);

            // THE FILE NO LONGER NAMES IT
            assertFalse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).contains(GONE),
                "the saved configuration still names " + GONE);

            // SAID ONCE, IN THE LOG, with what it lost where - and no dialog
            String line = I18n.f("autosetup.ui.leftOutLine", GONE, String.join(", ",
                I18n.f("autosetup.ui.leftOutStanding", session.describeTile(STANDS)),
                I18n.f("autosetup.ui.leftOutHome", session.describeTile(STANDS)),
                I18n.f("autosetup.ui.leftOutExcluded", session.describeTile(LISTED))));

            List<String> naming = entriesNaming(GONE, logBefore);

            assertEquals(naming.size(), 1, "the log does not name " + GONE + " once: " + naming);
            assertTrue(naming.get(0).startsWith(I18n.f("autosetup.ui.locomotivesLeftOut", "Main"))
                && naming.get(0).contains(line), "the log does not say what Main lost of " + GONE + ": " + naming);

            // AND IN ONE DIALOG, as an import's is (Adam: "log works, but show the locomotive info in a popup")
            List<String> startUp = dialogsNaming(dialogs, GONE, 1);

            assertEquals(startUp.size(), 1, "the start did not show exactly one dialog naming " + GONE + ": " + startUp);

            assertTrue(startUp.get(0).contains(I18n.f("autosetup.ui.locomotivesLeftOut", "Main"))
                && startUp.get(0).contains(line), "the start's dialog does not say what Main lost of " + GONE + ": "
                + startUp);

            // AN ORDINARY RELOAD, of a configuration given a home the database does not hold since
            final String later = "OB-349 Later";

            SwingUtilities.invokeAndWait(() -> session.setHome(STANDS, later));

            int reloadBefore = loggedEntries().size();

            SwingUtilities.invokeAndWait(() -> ui[0].getAutonomyViewerPanel().load("Main", true, false));

            AutonomySession reloaded = ui[0].getAutonomySession();

            assertNull(reloaded.getPointProperty(STANDS, "home"), "a reload kept the home of " + later + ", a locomotive"
                + " the database does not hold");

            List<String> reloadNaming = entriesNaming(later, reloadBefore);

            assertEquals(reloadNaming.size(), 1, "the reload's log does not name " + later + " once: " + reloadNaming);

            List<String> reloadShown = dialogsNaming(dialogs, later, 1);

            assertEquals(reloadShown.size(), 1, "the reload did not show exactly one dialog naming " + later + ": "
                + reloadShown);

            // AND NOT THE FIRST AGAIN: what the start removed is gone from the setup, so nothing is removed twice
            assertEquals(dialogs.naming(GONE).size(), 1, "the reload showed " + GONE + " again: " + dialogs.naming(GONE));

            // AND NOTHING AGAINST AN EMPTY DATABASE, which a missing or unread one would look like
            java.util.Map<TileKey, String> standing = new java.util.LinkedHashMap<>(session.placementsAutonomyWillWrite());

            assertFalse(standing.isEmpty(), "precondition: no train stands in the setup");

            assertTrue(session.forgetLocomotivesNotIn("Main", java.util.Collections.<String>emptySet()).isEmpty(),
                "an empty database took trains out of the setup");

            assertEquals(session.placementsAutonomyWillWrite(), standing, "an empty database changed the setup");
        }
        finally
        {
            if (dialogs != null) dialogs.stop();

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

    /** The dialogs naming this, waiting up to ten seconds for this many and then a moment for any more. */
    private static List<String> dialogsNaming(Dismisser dialogs, String name, int expected) throws InterruptedException
    {
        for (int wait = 0; wait < 100 && dialogs.naming(name).size() < expected; wait++) Thread.sleep(100);

        Thread.sleep(800);

        return dialogs.naming(name);
    }

    /**
     * A configuration imported from an exported file, naming a train the database does not hold and forty locomotives on
     * one station's list it does not hold either, arrives without any of them - and the import says so in one dialog,
     * naming each, whose height stays that of a short list.  A train the database holds arrives where the file has it.
     *
     * MUTATION: take the call out of the import door, or show the list without its scrolling, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnImportedFileLeavesOutWhatTheDatabaseDoesNotHoldAndSaysSo() throws Exception
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

            org.json.JSONObject bundle = session.getStore().exportBundle("Main");

            assertNotNull(bundle, "precondition: the frozen railway has no configuration Main to export");

            org.json.JSONObject points = bundle.getJSONObject("configuration").getJSONObject("points");

            org.json.JSONObject stands = pointAt(points, STANDS);

            stands.getJSONObject("loc").put("name", GONE);
            stands.put("home", GONE);

            List<String> sold = new ArrayList<>();

            org.json.JSONArray listed = pointAt(points, LISTED).getJSONArray("excludedLocs");

            for (int one = 1; one <= 40; one++)
            {
                sold.add(String.format("OB-349 Sold %02d", one));

                listed.put(sold.get(sold.size() - 1));
            }

            File file = new File(sandbox.getFolder(), "ob349-export.json");

            Files.write(file.toPath(), bundle.toString(2).getBytes(StandardCharsets.UTF_8));

            int logBefore = loggedEntries().size();

            List<Integer> heights = new ArrayList<>();

            List<String> said = importFromTheMenu(ui[0], file, "OB-349 import", heights);

            session = ui[0].getAutonomySession();

            String heading = I18n.f("autosetup.ui.locomotivesLeftOut", "OB-349 import");

            int shown = -1;

            for (int at = 0; at < said.size(); at++)
            {
                if (said.get(at).startsWith(heading)) shown = at;
            }

            assertTrue(shown >= 0, "the import said nothing of the locomotives it left out: " + said);

            // NAMED, EACH OF THEM, WITH WHAT IT LOST WHERE
            String told = said.get(shown);

            assertTrue(told.contains(I18n.f("autosetup.ui.leftOutLine", GONE, String.join(", ",
                I18n.f("autosetup.ui.leftOutStanding", session.describeTile(STANDS)),
                I18n.f("autosetup.ui.leftOutHome", session.describeTile(STANDS))))), "the import's dialog does not say "
                + GONE + " stood and was homed at " + session.describeTile(STANDS) + ": " + told);

            for (String one : sold)
            {
                assertTrue(told.contains(I18n.f("autosetup.ui.leftOutLine", one,
                    I18n.f("autosetup.ui.leftOutExcluded", session.describeTile(LISTED)))), "the import's dialog does not"
                    + " name " + one + ": " + told);
            }

            // AND ITS HEIGHT KEPT: forty-one lines in a list ten lines high
            assertTrue(heights.get(shown) < 450, "the dialog listing 41 locomotives is " + heights.get(shown)
                + " pixels high; its list should scroll");

            // OUT OF THE CONFIGURATION AND ITS FILE
            String imported = session.getStore().getConfiguration("OB-349 import").toString();

            assertFalse(imported.contains(GONE) || imported.contains("OB-349 Sold"), "the imported configuration still names"
                + " a locomotive the database does not hold: " + imported);

            assertNoFileNames(sandbox.getFolder(), GONE);
            assertNoFileNames(sandbox.getFolder(), "OB-349 Sold");

            org.json.JSONObject kept = pointAt(session.getStore().getConfiguration("OB-349 import")
                .getJSONObject("points"), KEPT);

            assertEquals(kept.getJSONObject("loc").getString("name"), "2-8-4 3505 SP", "a train the database holds did not"
                + " arrive where the file stands it");

            // AND THE LOG, ONCE
            assertEquals(entriesNaming(GONE, logBefore).size(), 1, "the log does not name " + GONE + " once: "
                + entriesNaming(GONE, logBefore));
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
     * An old autonomy.json imported from the menu leaves out the same three things, in the same dialog: a train it would
     * have stood on a station, a home and a name on a station's list.
     *
     * MUTATION: drop the import's own list of trains it did not place, or the call that takes the rest out, and this fails.
     *
     * @throws Exception from the window or the import
     */
    @Test
    public void testAnOldFileLeavesOutWhatTheDatabaseDoesNotHoldAndSaysSo() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the window needs a display");

        File mt491 = new File("test/autonomy_formats/autonomy-2.7.4c.json");

        assertTrue(mt491.isFile(), "precondition: the MT-491 file is not in test/autonomy_formats");

        support.LayoutSandbox sandbox = null;

        final TrainControlUI[] ui = new TrainControlUI[1];

        String folderWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_USED_FOLDER, null);

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

            org.json.JSONObject old = read(mt491);

            String homeless = "OB-349 Homeless";
            String sold = "OB-349 Sold";

            for (Object each : old.getJSONArray("points"))
            {
                org.json.JSONObject point = (org.json.JSONObject) each;

                if ("TopMainR1Inter".equals(point.optString("name"))) point.getJSONObject("loc").put("name", GONE);
                if ("TopMainR1".equals(point.optString("name"))) point.put("home", homeless);
                if ("BottomInner".equals(point.optString("name"))) point.getJSONArray("excludedLocs").put(sold);
            }

            File file = new File(sandbox.getFolder(), "ob349-autonomy.json");

            Files.write(file.toPath(), old.toString(2).getBytes(StandardCharsets.UTF_8));

            ui[0] = openTheWindow();

            int logBefore = loggedEntries().size();

            List<String> said = importFromTheMenu(ui[0], file, "OB-349 old");

            AutonomySession session = ui[0].getAutonomySession();

            String heading = I18n.f("autosetup.ui.locomotivesLeftOut", "OB-349 old");

            List<String> told = new ArrayList<>();

            for (String message : said) if (message.startsWith(heading)) told.add(message);

            assertEquals(told.size(), 1, "the import did not say once which locomotives it left out: " + said);

            for (String one : new String[] { GONE, homeless, sold })
            {
                assertTrue(told.get(0).contains(I18n.f("autosetup.ui.leftOutLine", one, "").replaceAll("\\s+$", "")),
                    "the import's dialog does not list " + one + " on a line of its own: " + told);
            }

            String imported = session.getStore().getConfiguration("OB-349 old").toString();

            for (String one : new String[] { GONE, homeless, sold })
            {
                assertFalse(imported.contains(one), "the configuration imported from the old file names " + one + ", a"
                    + " locomotive the database does not hold: " + imported);

                assertNoFileNames(sandbox.getFolder(), one);
            }

            assertEquals(entriesNaming(homeless, logBefore).size(), 1, "the log does not name " + homeless + " once: "
                + entriesNaming(homeless, logBefore));
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

    // ---------------------------------------------------------------- helpers

    private static org.json.JSONObject read(File file) throws Exception
    {
        return new org.json.JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
    }

    /** A configuration's point at a square, whichever form its key is written in. */
    private static org.json.JSONObject pointAt(org.json.JSONObject points, TileKey square)
    {
        for (String key : points.keySet())
        {
            if (square.equals(org.traincontrol.automationui.AutonomyCompanionStore.parseTileKey(key)))
            {
                return points.getJSONObject(key);
            }
        }

        throw new AssertionError("precondition: no point at " + square + " in " + points.keySet());
    }

    /** No file of the setup names this. */
    private static void assertNoFileNames(File layout, String text) throws Exception
    {
        File[] files = new File(layout, "config/autonomy").listFiles();

        assertNotNull(files, "precondition: the sandbox has no setup folder");

        for (File one : files)
        {
            if (!one.isFile()) continue;

            assertFalse(new String(Files.readAllBytes(one.toPath()), StandardCharsets.UTF_8).contains(text),
                one.getName() + " still names a locomotive the database does not hold (" + text + "...)");
        }
    }

    /** The log's entries since the one at `from` that name this. */
    private static List<String> entriesNaming(String name, int from) throws Exception
    {
        List<String> entries = loggedEntries();

        List<String> out = new ArrayList<>();

        for (String entry : entries.subList(Math.min(from, entries.size()), entries.size()))
        {
            if (entry.contains(name)) out.add(entry);
        }

        return out;
    }

    /** Answers every message dialog that shows, recording what it said, until stopped. */
    private static final class Dismisser implements Runnable
    {
        private final List<String> said = Collections.synchronizedList(new ArrayList<>());
        private final java.util.Set<Object> handled =
            Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private volatile boolean running = true;
        private Thread thread;

        static Dismisser start()
        {
            Dismisser dismisser = new Dismisser();

            dismisser.thread = new Thread(dismisser, "OB-349 dialog answerer");
            dismisser.thread.setDaemon(true);
            dismisser.thread.start();

            return dismisser;
        }

        List<String> naming(String name)
        {
            List<String> out = new ArrayList<>();

            synchronized (said)
            {
                for (String one : said) if (one.contains(name)) out.add(one);
            }

            return out;
        }

        void stop() throws InterruptedException
        {
            running = false;

            thread.join(5000);
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

                    final JOptionPane pane = find(((JDialog) window).getContentPane());

                    if (pane == null || !handled.add(pane)) continue;

                    said.add(textOf(pane.getMessage()));

                    final Object[] options = pane.getOptions();

                    SwingUtilities.invokeLater(() -> pane.setValue(options != null && options.length > 0
                        ? options[0] : Integer.valueOf(JOptionPane.OK_OPTION)));
                }
            }
        }

        private static JOptionPane find(Container container)
        {
            for (java.awt.Component child : container.getComponents())
            {
                if (child instanceof JOptionPane) return (JOptionPane) child;

                if (child instanceof Container)
                {
                    JOptionPane deeper = find((Container) child);

                    if (deeper != null) return deeper;
                }
            }

            return null;
        }
    }
}
