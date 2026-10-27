package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.gui.widgets.RestoreAction;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.MarklinSimpleComponent;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import support.Answering;
import support.Components;

/**
 * File &gt; Restore from Backup puts back what Backup TrainControl Data wrote, keeps a backup of the data it replaces,
 * and closes TrainControl without saving the state from before the restore over the restored files (FR-124).
 *
 * Each claim stands a window up over a sandboxed copy of the test layout and clicks the menu items, answering the real
 * dialogs.  Backups go to the run's own data folder; the exit is refused by a security manager and recorded.  The run's
 * data files are put back after the class, and every archive a claim made is deleted.
 */
public class testRestoreFromBackup
{
    private static final String PROBE = "FR-124 probe route";
    private static final String RUN_TRAIN = "FR-124 run train";
    private static final String OTHER_LAYOUT = "FR-124 another layout";

    private static support.LayoutSandbox sandbox;

    private static boolean dataKept;
    private static byte[] locomotivesWere;
    private static byte[] windowStateWas;
    private static String lastLocalWas;
    private static String recentWas;

    private MarklinControlStation model;
    private TrainControlUI ui;
    private NoExit guard;
    private Set<String> backupsBefore;
    private final List<File> made = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        if (System.getProperty(Util.DATA_DIR_PROPERTY, "").trim().isEmpty())
        {
            throw new SkipException("a restore writes the data folder, and this run has none of its own"
                + " (one.sh and battery.sh give it one)");
        }

        locomotivesWere = bytesIfThere(MarklinControlStation.DATA_FILE_NAME);
        windowStateWas = bytesIfThere(TrainControlUI.DATA_FILE_NAME);
        dataKept = true;
        lastLocalWas = TrainControlUI.getPrefs().get(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, null);
        recentWas = TrainControlUI.getPrefs().get(TrainControlUI.RECENT_LAYOUTS_PREF, null);

        sandbox = support.LayoutSandbox.open();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            // Only what was read: a class that failed first must not delete the run's data files
            if (dataKept)
            {
                putBack(new File(Util.dataPath(MarklinControlStation.DATA_FILE_NAME)), locomotivesWere);
                putBack(new File(Util.dataPath(TrainControlUI.DATA_FILE_NAME)), windowStateWas);
            }

            putBack(TrainControlUI.LAST_LOCAL_LAYOUT_PREF, lastLocalWas);
            putBack(TrainControlUI.RECENT_LAYOUTS_PREF, recentWas);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @BeforeMethod
    public void openTheWindow() throws Exception
    {
        backupsBefore = new HashSet<>(Arrays.asList(backupFolder().list() == null ? new String[0] : backupFolder().list()));

        model = support.TestStation.simulatedWithWindow();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();

        guard = new NoExit();

        System.setSecurityManager(guard);
    }

    @AfterMethod(alwaysRun = true)
    public void closeTheWindow() throws Exception
    {
        System.setSecurityManager(null);

        try
        {
            if (model.getRoute(PROBE) != null)
            {
                model.getRoute(PROBE).stopExecuting();
                model.deleteRoute(PROBE);
            }

            final TrainControlUI closing = ui;

            if (closing != null) SwingUtilities.invokeAndWait(closing::dispose);

            model.stop();
        }
        finally
        {
            for (String name : backupFolder().list() == null ? new String[0] : backupFolder().list())
            {
                if (!backupsBefore.contains(name)) made.add(new File(backupFolder(), name));
            }

            for (File file : made) Files.deleteIfExists(file.toPath());

            made.clear();
        }
    }

    /**
     * Restoring a backup puts back the locomotive database, the window state and the layout folder as the archive holds
     * them (a page added since is gone), keeps the data it replaced in a backup named as taken before a restore, names
     * that file, and ends TrainControl without the exit's saves: nothing is written over the restored files afterwards.
     *
     * MUTATION: in `RestoreAction.closeWithoutSaving`, close through the window's close box (dispatch WINDOW_CLOSING)
     * instead of `dispose` and `System.exit`, and the exit's save writes the added locomotive back.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARestorePutsTheBackupBackAndSavesNothingOverIt() throws Exception
    {
        File archive = backUp();

        Map<String, byte[]> held = entriesOf(archive);

        File pages = new File(sandbox.getFolder(), "config/gleisbilder");
        File added = new File(pages, "FR-124 added since.cs2");
        File changed = new File(pages, "5 - Test.cs2");

        model.newMM2Locomotive("FR-124 added since", 77);
        Files.write(added.toPath(), "added".getBytes(StandardCharsets.UTF_8));
        Files.write(changed.toPath(), "changed".getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);

        List<String> said = restore(archive, I18n.t("ui.restoreButton"), 2, 1000);

        assertTrue(Components.waitFor(() -> guard.exits().contains(0), 30000), "TrainControl did not close after the"
            + " restore.  Asked: " + said);

        assertFalse(Components.onTheEventThread(ui::isDisplayable), "the window is still open after the restore");

        File shadow = newBeforeRestore();

        assertTrue(said.get(1).contains(shadow.getName()), "the end of the restore does not name the backup taken before"
            + " it: " + said.get(1));

        Components.settle();

        assertRestored(held);

        Map<String, byte[]> before = entriesOf(shadow);

        String layout = sandbox.getFolder().getName();

        assertTrue(before.containsKey(layout + "/config/gleisbilder/" + added.getName()), "the backup taken before the"
            + " restore does not hold the layout as it was.  Holds: " + before.keySet());

        assertFalse(Arrays.equals(before.get(MarklinControlStation.DATA_FILE_NAME), held.get(MarklinControlStation.DATA_FILE_NAME)),
            "the backup taken before the restore holds the restored locomotive database, not the one replaced");

        List<String> names = new ArrayList<>();

        for (MarklinSimpleComponent c : model.restoreState(Util.dataPath(MarklinControlStation.DATA_FILE_NAME)))
        {
            names.add(c.getName());
        }

        assertFalse(names.contains("FR-124 added since"), "a locomotive added after the backup is in the restored database");
    }

    /**
     * An archive that is not a whole, readable TrainControl backup is refused with the reason before anything is asked or
     * written: no backup is taken, the data files stay as they are, and the station's own unreadable-database mark is
     * left as it was.
     *
     * MUTATION: make `Archive.readsAsALocomotiveDatabase` answer true, and the broken database is offered for restore.
     *
     * @throws Exception from the window
     */
    @Test
    public void testABrokenArchiveIsRefusedAndChangesNothing() throws Exception
    {
        Path folder = Files.createTempDirectory("tc-restore-broken");

        try
        {
            Map<String, Map<String, byte[]>> archives = new TreeMap<>();

            archives.put("broken-database.zip", Collections.singletonMap(MarklinControlStation.DATA_FILE_NAME,
                "not a database".getBytes(StandardCharsets.UTF_8)));
            archives.put("nothing-of-ours.zip", Collections.singletonMap("readme.txt", new byte[] {1}));

            Map<String, byte[]> outside = new TreeMap<>();

            outside.put(MarklinControlStation.DATA_FILE_NAME, Files.readAllBytes(new File(Util.dataPath(
                MarklinControlStation.DATA_FILE_NAME)).toPath()));
            outside.put("layout/config/../../escaped.txt", new byte[] {1});

            archives.put("leaves-its-folder.zip", outside);

            Map<String, String> expected = new TreeMap<>();

            expected.put("broken-database.zip", I18n.f("ui.errorRestoreUnreadable", "broken-database.zip",
                MarklinControlStation.DATA_FILE_NAME));
            expected.put("nothing-of-ours.zip", I18n.f("ui.errorRestoreNotABackup", "nothing-of-ours.zip"));
            expected.put("leaves-its-folder.zip", I18n.f("ui.errorRestoreNotABackup", "leaves-its-folder.zip"));

            boolean markWas = model.isDatabaseLoadFailed();

            byte[] locomotives = Files.readAllBytes(new File(Util.dataPath(MarklinControlStation.DATA_FILE_NAME)).toPath());

            for (Map.Entry<String, Map<String, byte[]>> each : archives.entrySet())
            {
                File zip = new File(folder.toFile(), each.getKey());

                writeZip(zip, each.getValue());

                List<String> said = restore(zip, I18n.t("ui.restoreButton"), 1, 2000);

                assertEquals(said, Collections.singletonList(expected.get(each.getKey())), each.getKey() + " was not"
                    + " refused with its reason before anything was asked");
            }

            Components.settle();

            assertEquals(newBackups(), new ArrayList<String>(), "a refused archive took a backup");

            assertTrue(Arrays.equals(Files.readAllBytes(new File(Util.dataPath(MarklinControlStation.DATA_FILE_NAME))
                .toPath()), locomotives), "a refused archive changed the locomotive database");

            assertEquals(model.isDatabaseLoadFailed(), markWas, "checking the archive changed the station's mark for its"
                + " own database, which decides whether the exit keeps a copy of it");

            assertTrue(guard.exits().isEmpty() && Components.onTheEventThread(ui::isDisplayable), "a refused archive"
                + " closed TrainControl");
        }
        finally
        {
            Util.deleteTree(folder.toFile(), 0);
        }
    }

    /**
     * Before anything is written the question lists every part the archive holds and where its layout goes, and names
     * what it will not put back; Cancel there leaves everything as it was, with no backup taken.
     *
     * MUTATION: treat any answer but Restore as Restore in `restoreFromBackup`, and a backup is taken.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheQuestionSaysWhatGoesWhereAndCancelChangesNothing() throws Exception
    {
        File taken = backUp();

        // Taken before the question, so it is not one the question made
        made.add(taken);
        backupsBefore.add(taken.getName());

        Map<String, byte[]> held = entriesOf(taken);

        Map<String, byte[]> elsewhere = new TreeMap<>();

        String layout = sandbox.getFolder().getName();

        for (Map.Entry<String, byte[]> entry : held.entrySet())
        {
            elsewhere.put(entry.getKey().replace(layout + "/config/", "FR-124 elsewhere/config/"), entry.getValue());
        }

        elsewhere.put(Util.LOC_ICON_FOLDER + "/crop.png", new byte[] {1});
        elsewhere.put("central-station-layout/config/gleisbild.cs2", new byte[] {1});

        Path folder = Files.createTempDirectory("tc-restore-question");

        try
        {
            File zip = new File(folder.toFile(), "question.zip");

            writeZip(zip, elsewhere);

            byte[] locomotives = Files.readAllBytes(new File(Util.dataPath(MarklinControlStation.DATA_FILE_NAME)).toPath());

            // Long enough for a backup to be taken and the restore to end, were Cancel to go ahead
            List<String> said = restore(zip, I18n.t("ui.cancel"), 1, 20000);

            assertEquals(said.size(), 1, "something was asked after Cancel: " + said);

            String to = new File(Util.dataPath("FR-124 elsewhere")).getAbsolutePath();

            for (String part : new String[] {I18n.t("ui.restorePartLocomotives"), I18n.t("ui.restorePartWindowState"),
                I18n.t("ui.restorePartIcons"), I18n.f("ui.restorePartLayout", "FR-124 elsewhere", to),
                I18n.f("ui.restoreNotRestored", "central-station-layout")})
            {
                assertTrue(said.get(0).contains(part), "the question before a restore does not say \"" + part + "\": "
                    + said.get(0));
            }

            Components.settle();

            assertEquals(newBackups(), new ArrayList<String>(), "Cancel took a backup");

            assertTrue(Arrays.equals(Files.readAllBytes(new File(Util.dataPath(MarklinControlStation.DATA_FILE_NAME))
                .toPath()), locomotives), "Cancel changed the locomotive database");

            assertFalse(new File(to).exists(), "Cancel wrote the layout");

            assertTrue(guard.exits().isEmpty(), "Cancel closed TrainControl");
        }
        finally
        {
            Util.deleteTree(folder.toFile(), 0);
        }
    }

    /**
     * While a route runs the restore refuses with the run's sentence before its chooser opens, as Open Layout does.
     *
     * MUTATION: take `refuseWhileARunHoldsTheSetup` out of the Restore from Backup item's listener.
     *
     * @throws Exception from the window
     */
    @Test
    public void testARestoreWaitsForARun() throws Exception
    {
        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(39, Accessory.accessoryDecoderType.MM2, true));

        assertTrue(model.newRoute(PROBE, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
            "precondition: the probe route could not be made");

        assertTrue(model.getRoute(PROBE).setExecuting(), "precondition: the probe route would not run");

        List<String> said = restore(new File(backupFolder(), "none.zip"), I18n.t("ui.restoreButton"), 1, 2000);

        assertEquals(said, Collections.singletonList(I18n.t("ui.main.errorNoEditsWhileRunning")), "Restore from Backup"
            + " did not refuse, before asking anything, while a route runs");
    }

    /**
     * The backup taken before a restore holds where a stopped run left the trains, so undoing the restore does not put
     * a train back on the square it set off from.
     *
     * MUTATION: take `captureRunningLayout()` out of `restoreFromBackup`, and the backup holds the pre-run setup.
     */
    @Test
    public void testTheBackupBeforeARestoreHoldsWhereTheRunLeftTheTrains() throws Exception
    {
        File archive = backUp();

        String[] where = leaveATrainWhereARunWould(model, ui, RUN_TRAIN);

        restore(archive, I18n.t("ui.restoreButton"), 2, 1000);

        assertTrue(Components.waitFor(() -> guard.exits().contains(0), 30000), "TrainControl did not close after the"
            + " restore");

        assertEquals(trainOn(entriesOf(newBeforeRestore()), sandbox.getFolder().getName(), where), RUN_TRAIN, "the"
            + " backup taken before the restore does not have " + RUN_TRAIN + " on " + where[1] + ", where the run left"
            + " it");
    }

    /**
     * Leaves a new train where a stopped run would, on a free station the setup does not name it on; answers {the
     * configuration, the square}.
     */
    static String[] leaveATrainWhereARunWould(MarklinControlStation model, TrainControlUI ui, String train)
        throws Exception
    {
        final String active = ui.getAutonomySession() == null ? null
            : ui.getAutonomySession().getStore().getActiveConfiguration();

        assertNotNull(active, "precondition: the test layout's setup has no active configuration");

        SwingUtilities.invokeAndWait(() -> ui.getAutonomyViewerPanel().load(active, false));

        assertTrue(Components.waitFor(() -> Components.onTheEventThread(ui::getActiveDiagramConfiguration) != null,
            30000), "precondition: the configuration " + active + " did not load");

        Components.settle();

        model.newMM2Locomotive(train, 78);

        final String[] square = new String[1];

        SwingUtilities.invokeAndWait(() ->
        {
            Layout running = model.getAutoLayout();

            for (Point point : running.getPoints())
            {
                if (point.isDestination() && !point.isOccupied())
                {
                    point.setLocomotive(model.getLocByName(train));

                    square[0] = String.valueOf(ui.getAutonomySession().tileForPointName(point.getName()));

                    break;
                }
            }
        });

        assertNotNull(square[0], "precondition: the running layout has no free station to leave a train on");

        assertFalse(model.getAutoLayout().isRunning(), "precondition: the railway is not at rest");

        return new String[] {active, square[0]};
    }

    /** Takes a train `leaveATrainWhereARunWould` left off the railway and out of the database. */
    static void takeTheTrainAway(MarklinControlStation model, String train) throws Exception
    {
        SwingUtilities.invokeAndWait(() ->
        {
            for (Point point : model.getAutoLayout().getPoints())
            {
                if (point.isOccupied() && train.equals(point.getCurrentLocomotive().getName())) point.setLocomotive(null);
            }
        });

        model.deleteLoc(train);
    }

    /** The train an archive's setup names on the square `leaveATrainWhereARunWould` answered, or null. */
    static String trainOn(Map<String, byte[]> archive, String layout, String[] where)
    {
        String setup = layout + "/config/autonomy/configuration-" + where[0] + ".json";

        assertNotNull(archive.get(setup), "the archive has no " + setup + ": " + archive.keySet());

        org.json.JSONObject points = new org.json.JSONObject(new String(archive.get(setup), StandardCharsets.UTF_8))
            .getJSONObject("points");

        org.json.JSONObject loc = points.optJSONObject(where[1]) == null ? null
            : points.getJSONObject(where[1]).optJSONObject("loc");

        return loc == null ? null : loc.optString("name");
    }

    /**
     * Checking an archive's locomotive database logs no load, readable or broken: the archive is only inspected.
     *
     * MUTATION: in `Archive.readsAsALocomotiveDatabase`, call `station.restoreState`, and a load is logged.
     */
    @Test
    public void testCheckingAnArchiveLogsNoDatabaseLoad() throws Exception
    {
        File good = backUp();

        Path folder = Files.createTempDirectory("tc-restore-log");

        final List<String> logged = Collections.synchronizedList(new ArrayList<>());

        Handler listening = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                logged.add(record.getMessage());
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        };

        Logger station = Logger.getLogger(MarklinControlStation.class.getName());

        station.addHandler(listening);

        try
        {
            File broken = new File(folder.toFile(), "broken-database.zip");

            writeZip(broken, Collections.singletonMap(MarklinControlStation.DATA_FILE_NAME,
                "not a database".getBytes(StandardCharsets.UTF_8)));

            restore(broken, I18n.t("ui.restoreButton"), 1, 2000);

            restore(good, I18n.t("ui.cancel"), 1, 2000);
        }
        finally
        {
            station.removeHandler(listening);

            Util.deleteTree(folder.toFile(), 0);
        }

        for (String key : new String[] {"log.databaseLoadedFromFile", "log.databaseInitDefault", "log.databaseBadDataFile"})
        {
            assertFalse(logged.contains(I18n.t(key)), "checking an archive logged \"" + I18n.t(key) + "\": " + logged);
        }
    }

    /**
     * Restoring the backup taken before a restore that moved TrainControl to another layout's folder takes it back to
     * the folder it left, not to a copy of it beside the data files.
     *
     * MUTATION: in `layoutDestination`, ignore the folder the archive names, and the undo ends on a new folder.
     */
    @Test
    public void testUndoingARestoreReturnsToTheLayoutFolderItLeft() throws Exception
    {
        String layoutWas = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        String layout = sandbox.getFolder().getName();

        File other = new File(Util.dataPath(OTHER_LAYOUT)).getAbsoluteFile();

        Path folder = Files.createTempDirectory("tc-restore-undo");

        try
        {
            Map<String, byte[]> elsewhere = new TreeMap<>();

            for (Map.Entry<String, byte[]> entry : entriesOf(backUp()).entrySet())
            {
                elsewhere.put(entry.getKey().replace(layout + "/config/", OTHER_LAYOUT + "/config/"), entry.getValue());
            }

            File zip = new File(folder.toFile(), "other-layout.zip");

            writeZip(zip, elsewhere);

            restore(zip, I18n.t("ui.restoreButton"), 2, 1000);

            assertTrue(Components.waitFor(() -> guard.exits().size() == 1, 30000), "TrainControl did not close after"
                + " the restore");

            assertEquals(new File(TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""))
                .getAbsoluteFile(), other, "precondition: the restore did not move TrainControl to the other layout");

            File undo = newBeforeRestore();

            SwingUtilities.invokeAndWait(ui::dispose);

            model.stop();

            model = support.TestStation.simulatedWithWindow();

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "the model has no window");

            Components.settle();

            restore(undo, I18n.t("ui.restoreButton"), 2, 1000);

            assertTrue(Components.waitFor(() -> guard.exits().size() == 2, 30000), "TrainControl did not close after"
                + " restoring " + undo.getName());

            assertEquals(new File(TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""))
                .getAbsoluteFile(), sandbox.getFolder().getAbsoluteFile(), "restoring the backup taken before the"
                + " restore did not take TrainControl back to the layout folder it left");
        }
        finally
        {
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, layoutWas);

            Util.deleteTree(folder.toFile(), 0);
            Util.deleteTree(other, 0);

            // Where the undo would have put a copy; never the sandbox itself
            File copy = new File(Util.dataPath(layout)).getAbsoluteFile();

            if (!copy.equals(sandbox.getFolder().getAbsoluteFile())) Util.deleteTree(copy, 0);
        }
    }

    /**
     * The same undo returns to a layout folder whose `config` is a link, the link followed when the folder is checked
     * for the archived layout.
     *
     * MUTATION: in `Archive.isHeldBy`, walk `config` without following it, and the undo ends on a new folder.
     */
    @Test
    public void testUndoingARestoreReturnsToALayoutWhoseConfigIsALink() throws Exception
    {
        String layoutWas = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");

        String layout = sandbox.getFolder().getName();

        File other = new File(Util.dataPath(OTHER_LAYOUT)).getAbsoluteFile();

        Path folder = Files.createTempDirectory("tc-restore-undo-linked");

        Path shared = linkTheConfig();

        try
        {
            Map<String, byte[]> elsewhere = new TreeMap<>();

            for (Map.Entry<String, byte[]> entry : entriesOf(backUp()).entrySet())
            {
                elsewhere.put(entry.getKey().replace(layout + "/config/", OTHER_LAYOUT + "/config/"), entry.getValue());
            }

            File zip = new File(folder.toFile(), "other-layout.zip");

            writeZip(zip, elsewhere);

            restore(zip, I18n.t("ui.restoreButton"), 2, 1000);

            assertTrue(Components.waitFor(() -> guard.exits().size() == 1, 30000), "TrainControl did not close after"
                + " the restore");

            assertEquals(new File(TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""))
                .getAbsoluteFile(), other, "precondition: the restore did not move TrainControl to the other layout");

            File undo = newBeforeRestore();

            SwingUtilities.invokeAndWait(ui::dispose);

            model.stop();

            model = support.TestStation.simulatedWithWindow();

            ui = (TrainControlUI) model.getGUI();

            assertNotNull(ui, "the model has no window");

            Components.settle();

            restore(undo, I18n.t("ui.restoreButton"), 2, 1000);

            assertTrue(Components.waitFor(() -> guard.exits().size() == 2, 30000), "TrainControl did not close after"
                + " restoring " + undo.getName());

            assertEquals(new File(TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, ""))
                .getAbsoluteFile(), sandbox.getFolder().getAbsoluteFile(), "restoring the backup taken before the"
                + " restore did not take TrainControl back to the layout folder it left, whose config is a link");

            assertTrue(Util.isLink(new File(sandbox.getFolder(), "config")), "the undo replaced the linked config");
        }
        finally
        {
            TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, layoutWas);

            Util.deleteTree(folder.toFile(), 0);
            Util.deleteTree(other, 0);

            for (String name : new String[] {layout, layout + "-restored"})
            {
                File copy = new File(Util.dataPath(name)).getAbsoluteFile();

                if (!copy.equals(sandbox.getFolder().getAbsoluteFile())) Util.deleteTree(copy, 0);
            }

            unlinkTheConfig(shared);
        }
    }

    /**
     * Emptying the layout's folder for a restore deletes a folder link in it, not what the link points at.
     *
     * MUTATION: in `Util.deleteTree`, descend into a link as into a folder, and the shared folder's file is deleted.
     */
    @Test
    public void testEmptyingTheLayoutDeletesALinkNotWhatItPointsAt() throws Exception
    {
        File archive = backUp();

        Path shared = Files.createTempDirectory("tc-restore-shared");

        File kept = new File(shared.toFile(), "kept.txt");

        Files.write(kept.toPath(), "shared".getBytes(StandardCharsets.UTF_8));

        Path link = new File(sandbox.getFolder(), "config/FR-124 linked").toPath();

        try
        {
            linkTo(link, shared);

            restore(archive, I18n.t("ui.restoreButton"), 2, 1000);

            assertTrue(Components.waitFor(() -> guard.exits().contains(0), 30000), "TrainControl did not close after"
                + " the restore");

            assertTrue(kept.isFile(), "emptying the layout's folder deleted a file in the folder a link there points at");

            assertFalse(Files.exists(link, LinkOption.NOFOLLOW_LINKS), "the link is still in the restored layout");
        }
        finally
        {
            if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) Files.delete(link);

            Util.deleteTree(shared.toFile(), 0);
        }
    }

    /**
     * A layout whose `config` is a link is restored through the link: `config` is still the link afterwards, and the
     * folder it points at holds the backup's files.
     *
     * MUTATION: in `Archive.writeLayout`, `deleteTree` the linked `config` as any other, and the link becomes a folder.
     */
    @Test
    public void testALinkedConfigFolderIsRestoredThroughTheLink() throws Exception
    {
        File archive = backUp();

        // Read now: a backup taken in the same second, as the restore's own can be, is written under its name
        Map<String, byte[]> held = entriesOf(archive);

        String page = sandbox.getFolder().getName() + "/config/gleisbild.cs2";

        assertNotNull(held.get(page), "precondition: the backup has no " + page + ": " + held.keySet());

        Path config = new File(sandbox.getFolder(), "config").toPath();

        Path shared = linkTheConfig();

        File added = new File(shared.toFile(), "added since the backup.txt");

        try
        {
            Files.write(added.toPath(), "added".getBytes(StandardCharsets.UTF_8));

            restore(archive, I18n.t("ui.restoreButton"), 2, 1000);

            assertTrue(Components.waitFor(() -> guard.exits().contains(0), 30000), "TrainControl did not close after"
                + " the restore");

            assertTrue(Util.isLink(config.toFile()), "the restore replaced the linked config with a folder of its own");

            assertFalse(added.exists(), "a file added to the linked folder since the backup is still there");

            assertTrue(Arrays.equals(Files.readAllBytes(shared.resolve("gleisbild.cs2")), held.get(page)), "the linked"
                + " folder does not hold the backup's gleisbild.cs2");
        }
        finally
        {
            Files.deleteIfExists(added.toPath());

            unlinkTheConfig(shared);
        }
    }

    /**
     * Makes the sandbox's `config` a link to a copy of itself; skips, `config` untouched, where no folder link can be
     * made.  Answers the linked folder, for `unlinkTheConfig`.
     */
    private static Path linkTheConfig() throws Exception
    {
        Path config = new File(sandbox.getFolder(), "config").toPath();

        Path shared = Files.createTempDirectory("tc-restore-shared-config");

        copyTree(config, shared);

        Path probe = sandbox.getFolder().toPath().resolve("config-link-probe");

        String failed = link(probe, shared);

        if (failed != null)
        {
            Util.deleteTree(shared.toFile(), 0);

            throw new SkipException("no folder link can be made here: " + failed);
        }

        Files.delete(probe);

        try
        {
            assertTrue(Util.deleteTree(config.toFile(), 0), "precondition: the layout's config could not be moved");

            linkTo(config, shared);
        }
        catch (Exception | AssertionError e)
        {
            unlinkTheConfig(shared);

            throw e;
        }

        return shared;
    }

    /**
     * Gives the sandbox a real `config` again, unless it already is a folder holding `gleisbild.cs2`; only then deletes
     * `shared`, which may be the only copy.
     */
    private static void unlinkTheConfig(Path shared) throws Exception
    {
        Path config = new File(sandbox.getFolder(), "config").toPath();

        if (Util.isLink(config.toFile()) || !Files.isRegularFile(config.resolve("gleisbild.cs2")))
        {
            if (Util.isLink(config.toFile()) && Files.exists(config, LinkOption.NOFOLLOW_LINKS)) Files.delete(config);
            else if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)) Util.deleteTree(config.toFile(), 0);

            copyTree(shared, config);
        }

        if (!Util.isLink(config.toFile()) && Files.isRegularFile(config.resolve("gleisbild.cs2")))
        {
            Util.deleteTree(shared.toFile(), 0);
        }
    }

    private static void copyTree(Path from, Path to) throws Exception
    {
        try (java.util.stream.Stream<Path> walk = Files.walk(from))
        {
            for (Path each : (Iterable<Path>) walk::iterator)
            {
                Path into = to.resolve(from.relativize(each).toString());

                if (Files.isDirectory(each)) Files.createDirectories(into);
                else Files.copy(each, into, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** A folder link: a junction on Windows, which needs no administrator, else a symbolic link. */
    private static void linkTo(Path link, Path target) throws Exception
    {
        String failed = link(link, target);

        assertTrue(failed == null, "precondition: no junction could be made: " + failed);
    }

    /** Makes a folder link as `linkTo` does; answers why it could not, or null. */
    private static String link(Path link, Path target) throws Exception
    {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows"))
        {
            try
            {
                Files.createSymbolicLink(link, target);

                return null;
            }
            catch (java.io.IOException | UnsupportedOperationException e)
            {
                return String.valueOf(e);
            }
        }

        Process made = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
            .redirectErrorStream(true).start();

        String said = new String(toBytes(made.getInputStream()), StandardCharsets.UTF_8);

        return made.waitFor() == 0 ? null : said;
    }

    /** The newest backup named as taken before a restore that this claim made. */
    private File newBeforeRestore()
    {
        File shadow = null;

        for (String name : newBackups())
        {
            if (name.endsWith(RestoreAction.BEFORE_RESTORE_SUFFIX))
            {
                File found = new File(backupFolder(), name);

                if (shadow == null || found.lastModified() > shadow.lastModified()) shadow = found;
            }
        }

        assertNotNull(shadow, "no backup named as taken before a restore is in " + backupFolder());

        return shadow;
    }

    /**
     * Clicks Restore from Backup, picks `archive`, answers its question with `button`, and the rest with their first.
     * After the questions expected it waits `quietMillis` more, so one asked later (a backup's end) is seen too.
     */
    private List<String> restore(File archive, String button, int questions, long quietMillis) throws Exception
    {
        AbstractButton item = Components.withText(ui, I18n.t("ui.main.toolbar.restoreData"));

        assertNotNull(item, "the File menu has no Restore from Backup");

        try (Answering answering = Answering.start(message -> message.contains("•")
            ? Answering.press(button) : Answering.pressAt(0)).choosing(archive))
        {
            Components.press(item);

            assertTrue(answering.awaitAsked(questions, 120000), "the restore asked " + answering.asked());

            answering.awaitAsked(questions + 1, quietMillis);

            Components.settle();

            return answering.asked();
        }
    }

    /** Clicks Backup TrainControl Data, answers OK, and gives back the archive it wrote. */
    private File backUp() throws Exception
    {
        Set<String> before = new HashSet<>(Arrays.asList(backupFolder().list() == null ? new String[0] : backupFolder().list()));

        try (Answering answering = Answering.start(message -> Answering.pressAt(0)))
        {
            Components.press(Components.withText(ui, I18n.t("ui.main.toolbar.backupData")));

            assertTrue(answering.awaitAsked(1, 120000), "the backup never finished");
        }

        for (String name : backupFolder().list())
        {
            if (!before.contains(name) && name.endsWith(".zip"))
            {
                File archive = new File(backupFolder(), name);

                // These come from the working directory, where a restore would write them back: a checkout that holds
                // them (the main one can) skips rather than risk them
                for (String entry : entriesOf(archive).keySet())
                {
                    if (entry.startsWith(Util.LOC_ICON_FOLDER + "/") || entry.equals(TrainControlUI.AUTONOMY_FILE_NAME))
                    {
                        archive.delete();

                        throw new SkipException("the working directory holds " + entry + ", which a restore would write"
                            + " over");
                    }
                }

                return archive;
            }
        }

        throw new AssertionError("the backup wrote no archive into " + backupFolder());
    }

    /** The data files and the layout's folder hold exactly what the archive does. */
    private static void assertRestored(Map<String, byte[]> held) throws Exception
    {
        assertTrue(Arrays.equals(Files.readAllBytes(new File(Util.dataPath(MarklinControlStation.DATA_FILE_NAME)).toPath()),
            held.get(MarklinControlStation.DATA_FILE_NAME)), "the locomotive database is not the one in the backup");

        assertTrue(Arrays.equals(Files.readAllBytes(new File(Util.dataPath(TrainControlUI.DATA_FILE_NAME)).toPath()),
            held.get(TrainControlUI.DATA_FILE_NAME)), "the window state is not the one in the backup");

        String prefix = sandbox.getFolder().getName() + "/config/";

        Map<String, byte[]> onDisk = new TreeMap<>();

        Path config = new File(sandbox.getFolder(), "config").toPath();

        try (java.util.stream.Stream<Path> walk = Files.walk(config))
        {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator)
            {
                onDisk.put(prefix + config.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
            }
        }

        Map<String, byte[]> archived = new TreeMap<>(held);

        archived.keySet().removeIf(name -> !name.startsWith(prefix));

        assertEquals(onDisk.keySet(), archived.keySet(), "the layout folder does not hold the files the backup does");

        for (String name : archived.keySet())
        {
            assertTrue(Arrays.equals(onDisk.get(name), archived.get(name)), name + " is not as the backup holds it");
        }
    }

    private List<String> newBackups()
    {
        List<String> added = new ArrayList<>();

        for (String name : backupFolder().list() == null ? new String[0] : backupFolder().list())
        {
            if (!backupsBefore.contains(name)) added.add(name);
        }

        return added;
    }

    private static File backupFolder()
    {
        return new File(Util.dataPath(Util.BACKUP_FOLDER)).getAbsoluteFile();
    }

    private static byte[] bytesIfThere(String name) throws Exception
    {
        File file = new File(Util.dataPath(name));

        return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
    }

    private static void putBack(File file, byte[] content) throws Exception
    {
        if (content == null) Files.deleteIfExists(file.toPath());
        else Files.write(file.toPath(), content);
    }

    private static void putBack(String key, String value)
    {
        if (value == null) TrainControlUI.getPrefs().remove(key);
        else TrainControlUI.getPrefs().put(key, value);
    }

    static Map<String, byte[]> entriesOf(File archive) throws Exception
    {
        Map<String, byte[]> held = new TreeMap<>();

        try (ZipFile zip = new ZipFile(archive))
        {
            for (ZipEntry entry : Collections.list(zip.entries()))
            {
                if (!entry.isDirectory()) held.put(entry.getName(), toBytes(zip.getInputStream(entry)));
            }
        }

        return held;
    }

    private static byte[] toBytes(java.io.InputStream in) throws Exception
    {
        try (java.io.InputStream from = in)
        {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

            byte[] buffer = new byte[8192];

            for (int n; (n = from.read(buffer)) > 0; ) out.write(buffer, 0, n);

            return out.toByteArray();
        }
    }

    private static void writeZip(File zip, Map<String, byte[]> entries) throws Exception
    {
        try (ZipOutputStream out = new ZipOutputStream(new java.io.FileOutputStream(zip)))
        {
            for (Map.Entry<String, byte[]> entry : entries.entrySet())
            {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    /** Refuses, and records, an exit; everything else is allowed. */
    private static final class NoExit extends SecurityManager
    {
        private final List<Integer> exits = Collections.synchronizedList(new ArrayList<>());

        List<Integer> exits()
        {
            synchronized (exits)
            {
                return new ArrayList<>(exits);
            }
        }

        @Override
        public void checkExit(int status)
        {
            exits.add(status);

            throw new SecurityException("no exit in a test");
        }

        @Override
        public void checkPermission(Permission permission)
        {
        }

        @Override
        public void checkPermission(Permission permission, Object context)
        {
        }
    }
}
