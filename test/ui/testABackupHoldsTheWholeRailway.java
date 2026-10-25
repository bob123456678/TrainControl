package ui;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import support.Answering;
import support.Components;

/**
 * File > Backup TrainControl Data writes one archive of the whole railway as it is now, and says where it is (FR-015,
 * FR-019, FR-021, MT-159).
 *
 * Adam (FR-015): "the backup menu option should export a zip file with the locdb and uistate files, track diagram
 * files, and autonomy files - effectively, all state."
 *
 * Each claim clicks the menu item on a window over a sandboxed copy of the test layout and answers the real dialog
 * that ends the backup.  The archive is written to the run's own data folder, and each claim deletes what it made.
 *
 * MUTATIONS, each seen red: the locomotive database left out of the archive (the data claim); the routes export left
 * out (the routes claim); the layout folder stored as "config" rather than under its own name (the layout claim);
 * Show Files answered as OK (the folder claim).
 */
public class testABackupHoldsTheWholeRailway
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static TrainControlUI ui;

    /** The archives a claim made, deleted after it. */
    private final List<File> made = new ArrayList<>();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new SkipException("the main window needs a display");

        // The archive goes into the data folder; only the runners give a test run one of its own
        if (System.getProperty(Util.DATA_DIR_PROPERTY, "").trim().isEmpty())
        {
            throw new SkipException("a backup is written to the data folder, and this run has none of its own"
                + " (one.sh and battery.sh give it one)");
        }

        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedWithWindow();

        ui = (TrainControlUI) model.getGUI();

        assertNotNull(ui, "the model has no window");

        Components.settle();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            Util.fileManagerForTests(null);

            if (ui != null)
            {
                final TrainControlUI closing = ui;

                SwingUtilities.invokeAndWait(closing::dispose);
            }

            if (model != null) model.stop();
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tidy()
    {
        Util.fileManagerForTests(null);

        for (File archive : made) archive.delete();

        made.clear();
    }

    /**
     * The backup says the archive's name, and the archive holds the locomotive database and the window's state.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheBackupNamesItsArchiveAndHoldsTheData() throws Exception
    {
        List<String> said = new ArrayList<>();

        File archive = backUp(Answering.press(I18n.t("ui.ok")), said);

        assertEquals(said.get(0), I18n.f("ui.infoBackupCompleteSavedTo", archive.getName()), "the end of the backup"
            + " does not name the archive it wrote (FR-019)");

        Map<String, byte[]> held = entriesOf(archive);

        assertTrue(held.containsKey(MarklinControlStation.DATA_FILE_NAME), "the backup has no locomotive database in"
            + " it.  Holds: " + held.keySet());

        assertTrue(held.containsKey("UIState.data"), "the backup has no window state in it.  Holds: " + held.keySet());

        assertTrue(held.get(MarklinControlStation.DATA_FILE_NAME).length > 0, "the backed-up locomotive database is empty");
    }

    /**
     * The backup holds the routes as they are at the moment it is taken: a route made just before is in it.
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheBackupHoldsTheRoutesAsTheyAreNow() throws Exception
    {
        final String route = "P6 backup " + System.currentTimeMillis();

        List<RouteCommand> commands = new ArrayList<>();

        commands.add(RouteCommand.RouteCommandAccessory(250, Accessory.accessoryDecoderType.MM2, true));

        assertTrue(model.newRoute(route, commands, 0, MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED, false, null),
            "precondition: the route was not made");

        try
        {
            File archive = backUp(Answering.press(I18n.t("ui.ok")), new ArrayList<>());

            Map<String, byte[]> held = entriesOf(archive);

            assertTrue(held.containsKey("routes.json"), "the backup has no routes in it (FR-021).  Holds: "
                + held.keySet());

            String routes = new String(held.get("routes.json"), StandardCharsets.UTF_8);

            assertTrue(routes.contains(route), "a route made just before the backup is not in it (FR-021)");
        }
        finally
        {
            model.deleteRoute(route);
        }
    }

    /**
     * The backup holds the layout's whole folder under the layout's own name: every page and the autonomy setup beside
     * them, each as it is on disk (FR-015, MT-159).
     *
     * @throws Exception from the window
     */
    @Test
    public void testTheBackupHoldsTheLayoutUnderItsOwnName() throws Exception
    {
        File archive = backUp(Answering.press(I18n.t("ui.ok")), new ArrayList<>());

        Map<String, byte[]> held = entriesOf(archive);

        File folder = sandbox.getFolder();

        Path config = new File(folder, "config").toPath();

        List<Path> files = new ArrayList<>();

        try (java.util.stream.Stream<Path> walk = Files.walk(config))
        {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }

        assertTrue(files.size() > 5, "precondition: the layout folder holds too little to be worth checking: " + files);

        List<String> missing = new ArrayList<>();
        List<String> different = new ArrayList<>();

        for (Path file : files)
        {
            String name = folder.getName() + "/config/" + config.relativize(file).toString().replace('\\', '/');

            if (!held.containsKey(name)) missing.add(name);
            else if (!Arrays.equals(held.get(name), Files.readAllBytes(file))) different.add(name);
        }

        assertEquals(missing, new ArrayList<String>(), "the backup is missing these files of the layout, kept under"
            + " the layout's own name (MT-159).  Holds: " + held.keySet());

        assertEquals(different, new ArrayList<String>(), "these files of the layout differ in the backup from the folder");
    }

    /**
     * Show Files, at the end of the backup, opens the folder the archive is in (OB-311).
     *
     * @throws Exception from the window
     */
    @Test
    public void testShowFilesOpensTheBackupFolder() throws Exception
    {
        final List<String> opened = java.util.Collections.synchronizedList(new ArrayList<>());

        Util.fileManagerForTests(opened::add);

        File archive = backUp(Answering.press(I18n.t("ui.showFiles")), new ArrayList<>());

        assertTrue(Components.waitFor(() -> !opened.isEmpty(), 10000), "Show Files opened nothing");

        assertEquals(opened, Arrays.asList("open " + archive.getParentFile().getAbsolutePath()), "Show Files did not"
            + " open the folder the backup is in (OB-311)");
    }

    /**
     * Clicks Backup TrainControl Data, answers its last question so, and gives back the archive that question named.
     */
    private File backUp(Answering.Answer answer, List<String> said) throws Exception
    {
        final File folder = new File(Util.dataPath(Util.BACKUP_FOLDER)).getAbsoluteFile();

        final Set<String> before = new HashSet<>(Arrays.asList(folder.list() == null ? new String[0] : folder.list()));

        final AbstractButton item = Components.withText(ui, I18n.t("ui.main.toolbar.backupData"));

        assertNotNull(item, "the window has no Backup TrainControl Data");

        try (Answering answering = Answering.start(message -> answer))
        {
            SwingUtilities.invokeLater(item::doClick);

            assertTrue(answering.awaitAsked(1, 120000), "the backup never finished");

            said.addAll(answering.asked());
        }

        // The question names the archive by its file name
        File archive = null;

        for (String name : folder.list() == null ? new String[0] : folder.list())
        {
            if (said.get(0).contains(name) && name.endsWith(".zip")) archive = new File(folder, name);
        }

        assertNotNull(archive, "no archive in " + folder + " is the one the backup named: " + said);

        made.add(archive);

        assertTrue(!before.contains(archive.getName()) || archive.lastModified() > System.currentTimeMillis() - 120000,
            "the archive the backup named is an old one");

        assertTrue(Components.waitFor(item::isEnabled, 10000), "Backup TrainControl Data stays greyed after a backup");

        return archive;
    }

    /** Every file in an archive, by its name there. */
    private static Map<String, byte[]> entriesOf(File archive) throws Exception
    {
        Map<String, byte[]> held = new TreeMap<>();

        try (ZipFile zip = new ZipFile(archive))
        {
            for (ZipEntry entry : java.util.Collections.list(zip.entries()))
            {
                if (entry.isDirectory()) continue;

                try (java.io.InputStream in = zip.getInputStream(entry))
                {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

                    byte[] buffer = new byte[8192];

                    for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);

                    held.put(entry.getName(), out.toByteArray());
                }
            }
        }

        return held;
    }
}
