package regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Accessory.accessoryDecoderType;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * MT-074 and MT-075, which Adam could not run by hand and asked to have tested instead.
 *
 * Both are Tier 2 - data safety - and both are about what is left on DISK afterwards, which is the
 * half a person at the screen cannot see. That is why they were awkward by hand and why they are
 * worth automating: "it loaded and looked right" is not the claim either test is making.
 *
 * MT-074 adds a requirement Adam wrote when he deferred it: "validate that the source files are
 * unchanged afterwards". So the round trip is checked byte-for-byte against copies taken before it,
 * not by reading the setup back through the same code that wrote it.
 *
 * @author Adam
 */
public class testDataSafetyRoundTrips
{
    private File folder;

    /**
     * Something no save writes, so its presence in the .bak proves the backup is the state this build
     * FOUND rather than one it produced.
     */
    private static final String MARKER = "tc-test-marker-do-not-write";

    @BeforeMethod
    public void setUp() throws IOException
    {
        folder = Files.createTempDirectory("tc-data-safety").toFile();
    }

    @AfterMethod
    public void tearDown()
    {
        delete(folder);
    }

    // ---- MT-074 ---------------------------------------------------------------------------------

    /**
     * Export the setup, re-import it, and get the same setup - with the files untouched.
     *
     * The named regression is that the block field was not written, so a bundle exported before
     * 18 August came back without the sensor each Point was about. Anything keyed by square survives an
     * export only if the export writes it, and the way that fails is silent: the file is valid JSON, it
     * imports without complaint, and what is missing is only visible later.
     */
    @Test
    public void testTheExportedBundleComesBackWholeAndChangesNothing() throws IOException
    {
        AutonomySession session = furnished();

        Path setup = folder.toPath().resolve("config/autonomy/setup.json");

        Path configuration = folder.toPath().resolve("config/autonomy/configuration-Only.json");

        assertTrue(Files.exists(setup), "no setup.json was written, so this test proves nothing");

        byte[] setupBefore = Files.readAllBytes(setup);
        byte[] configurationBefore = Files.readAllBytes(configuration);

        org.json.JSONObject bundle = session.getStore().exportBundle("Only");

        assertNotNull(bundle, "nothing was exported");

        // Adam's own requirement: exporting is a READ, and a read that rewrites the files is a way to
        // lose a setup while believing you were backing it up.
        assertEquals(Files.readAllBytes(setup), setupBefore,
            "exporting rewrote setup.json");

        assertEquals(Files.readAllBytes(configuration), configurationBefore,
            "exporting rewrote the configuration file");

        // What the bundle must carry. Keyed by square, so an export that drops the key drops the
        // Point's identity and the import has nothing to hang the settings on.
        String text = bundle.toString();

        assertTrue(text.contains("Bahnhof"), "the point name is not in the bundle: " + brief(text));
        assertTrue(text.contains("BR 218"), "the placement is not in the bundle: " + brief(text));
        assertTrue(text.contains("main:1,1"), "the square is not in the bundle: " + brief(text));

        // And back again, into a folder that has never seen it
        File second = Files.createTempDirectory("tc-data-safety-import").toFile();

        try
        {
            AutonomySession arriving = new AutonomySession(second);

            arriving.open(Arrays.asList(page()));

            arriving.getStore().importBundle("Only", bundle);
            arriving.getStore().setActiveConfiguration("Only");

            TileKey sensor = new TileKey("main", 1, 1);

            assertEquals(arriving.getStore().getPointName(sensor), "Bahnhof",
                "the name did not survive the round trip");

            assertTrue(arriving.getStore().isStation(sensor),
                "the station designation did not survive the round trip");

            assertEquals(arriving.getLocomotiveNameAt(sensor), "BR 218",
                "the placement did not survive the round trip");
        }
        finally
        {
            delete(second);
        }
    }

    // ---- MT-075 ---------------------------------------------------------------------------------

    /**
     * Saving a page leaves a backup beside it, once, and never a corrupt page.
     *
     * "Once" is the part worth pinning. The backup is the state before this BUILD touched the page -
     * a migration that strips something a user wanted is recoverable by hand from it - and a backup
     * rewritten on every save is a copy of the last save, which is no use for that at all.
     */
    @Test
    public void testSavingAPageLeavesOneBackupAndAWholeFile() throws Exception
    {
        LayoutDiagram page = page();

        // Kept here rather than asked for: the page derives its path from the URL it was built with,
        // and getFilePath is private to it.
        Path file = pageFile();

        // Something already there, so the save has a previous state worth backing up
        Files.write(file, (MARKER + "\n").getBytes(StandardCharsets.UTF_8));

        page.saveChanges(null, false);

        assertTrue(Files.exists(file), "the page itself is gone after a save");

        Path backup = file.resolveSibling(file.getFileName() + ".bak");

        assertTrue(Files.exists(backup),
            "no .bak was written beside the page, so a migration that strips something is not "
            + "recoverable");

        // What the backup must hold: the marker that was in the file BEFORE the first save, and which
        // no save writes. Compared by content rather than by byte array - an earlier version of this
        // test compared the array against a copy taken after the first save, and passed against a
        // mutant that rewrote the backup every time, because both saves happened to leave it the same.
        String backedUp = new String(Files.readAllBytes(backup), StandardCharsets.UTF_8);

        assertTrue(backedUp.contains(MARKER),
            "the backup does not hold what was in the file before the first save: " + brief(backedUp));

        // A second save must not move the backup on
        page.addComponent(componentType.STRAIGHT, 4, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);

        page.saveChanges(null, false);

        backedUp = new String(Files.readAllBytes(backup), StandardCharsets.UTF_8);

        assertTrue(backedUp.contains(MARKER),
            "the backup was rewritten by a later save, so it is now a copy of a state this build "
            + "produced rather than the one it found - which is the only thing it is for. Holds: "
            + brief(backedUp));

        // And the page is readable, which is what "nothing is corrupted" means
        String written = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertFalse(written.trim().isEmpty(), "the page was truncated");

        assertTrue(written.contains("element") || written.contains("id"),
            "the page does not look like a diagram file: " + brief(written));

        // Nothing left half-written beside it
        for (File stray : file.getParent().toFile().listFiles())
        {
            assertFalse(stray.getName().endsWith(".tmp"),
                "a temporary file was left behind: " + stray.getName());
        }
    }

    // ------------------------------------------------------------------------------------------

    private AutonomySession furnished() throws IOException
    {
        AutonomySession session = new AutonomySession(folder);

        session.open(Arrays.asList(page()));

        session.getStore().createConfiguration("Only", null);
        session.getStore().setActiveConfiguration("Only");

        TileKey sensor = new TileKey("main", 1, 1);

        session.setPointName(sensor, "Bahnhof");
        session.getStore().setStation(sensor, true);
        session.placeLocomotive(sensor, "BR 218");

        session.saveWithoutReconciling();

        return session;
    }

    private Path pageFile()
    {
        File pages = new File(folder, "config/gleisbilder");

        pages.mkdirs();

        return new File(pages, "main.cs2").toPath();
    }

    private LayoutDiagram page() throws IOException
    {
        LayoutDiagram page = new LayoutDiagram("main", 8, 4,
            pageFile().toUri().toURL().toString(), null);

        page.addComponent(componentType.FEEDBACK, 1, 1, 0, 0, 5, 11, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.STRAIGHT, 2, 1, 0, 0, 0, 0, accessoryDecoderType.MM2, null);
        page.addComponent(componentType.FEEDBACK, 3, 1, 0, 0, 6, 12, accessoryDecoderType.MM2, null);

        page.setPageId("1");

        return page;
    }

    private String brief(String text)
    {
        return text.length() < 300 ? text : text.substring(0, 300) + "...";
    }

    private void delete(File f)
    {
        if (f.isDirectory())
        {
            File[] kids = f.listFiles();

            if (kids != null) for (File kid : kids) delete(kid);
        }

        f.delete();
    }
}
