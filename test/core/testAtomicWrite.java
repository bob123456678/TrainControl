package core;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.util.Util;

/**
 * Tests Util.writeAtomically - the guarantee that a failed write cannot destroy what was there before.
 *
 * Three files carry the operator's accumulated work and are written exactly once per session, in the
 * window-close handler: the locomotive database, the UI state, and autonomy.json.  All three used to be
 * opened for writing directly, which empties the target immediately - so from the first byte until the
 * last was flushed the only copy of the data was incomplete.  Dying in that window did not leave the
 * database stale, it left it destroyed, and silently: an unreadable database reads as a first launch,
 * and the next Central Station sync repopulates the locomotive list, so the customizations look
 * mislaid rather than lost.
 *
 * A real crash cannot be staged in a unit test, so these provoke the same window the other way, with a
 * write that throws part way through.  The observable property is identical and is the one that
 * matters: after a write that did not complete, the previous contents are still there.
 *
 * To see these fail, replace writeAtomically's body with a direct write to the target - which is what
 * the three writers did before.
 */
public class testAtomicWrite
{
    private static final String BEFORE = "the previous contents, which must survive";

    private Path dir;
    private File target;

    @BeforeMethod
    public void setUp() throws Exception
    {
        dir = Files.createTempDirectory("tc-atomic");
        target = new File(dir.toFile(), "state.dat");

        Files.write(target.toPath(), BEFORE.getBytes(StandardCharsets.UTF_8));
    }

    @AfterMethod
    public void tearDown() throws Exception
    {
        Files.walk(dir)
            .sorted(java.util.Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete);
    }

    private String contents() throws Exception
    {
        return new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * The ordinary case: a write that completes replaces the file.
     *
     * First, so that a helper which never wrote anything at all could not pass the failure tests below
     * by doing nothing.
     */
    @Test
    public void testACompletedWriteReplacesTheFile() throws Exception
    {
        Util.writeAtomically(target, out -> out.write("new contents".getBytes(StandardCharsets.UTF_8)));

        assertEquals(contents(), "new contents", "a completed write must land");
    }

    /**
     * A write that throws part way leaves the previous file untouched.
     */
    @Test
    public void testAFailedWriteLeavesThePreviousFileIntact() throws Exception
    {
        try
        {
            Util.writeAtomically(target, out ->
            {
                // Enough to have reached the disk had the target been opened directly
                out.write(new byte[64 * 1024]);
                out.flush();

                throw new IOException("interrupted part way, as a dying process would be");
            });

            fail("the failure must be reported to the caller, not swallowed");
        }
        catch (IOException expected)
        {
            // The point of the test is what is on disk afterwards
        }

        assertEquals(contents(), BEFORE,
            "a write that did not complete destroyed the previous contents - which for the locomotive "
                + "database is the operator's entire accumulated customization, lost silently");
    }

    /**
     * The same guarantee for the shape the locomotive database and the UI state actually use: an object
     * stream over the file, with serialization failing mid-stream.
     */
    @Test
    public void testAFailedObjectSerializationLeavesThePreviousFileIntact() throws Exception
    {
        List<Object> payload = Arrays.asList("a harmless first element", new Unserializable());

        try
        {
            Util.writeAtomically(target, out ->
            {
                try (ObjectOutputStream objects = new ObjectOutputStream(out))
                {
                    objects.writeObject(payload);
                }
            });

            fail("serialization of an unserializable member must fail");
        }
        catch (IOException | RuntimeException expected)
        {
            // As above - the assertion that matters is on disk
        }

        assertEquals(contents(), BEFORE,
            "a serialization failure part way through the list destroyed the previous database");
    }

    /**
     * The staging file must not be left behind, or it accumulates beside every file this protects.
     */
    @Test
    public void testAFailedWriteLeavesNoStagingFileBehind() throws Exception
    {
        try
        {
            Util.writeAtomically(target, out ->
            {
                throw new IOException("failed immediately");
            });

            fail("the failure must be reported");
        }
        catch (IOException expected)
        {
        }

        File staging = new File(target.getAbsolutePath() + Util.PARTIAL_DOWNLOAD_SUFFIX);

        assertFalse(staging.exists(), "the staging file must be cleaned up: " + staging);
    }

    /**
     * Writing a file that does not exist yet is the first-run case, and must simply create it.
     */
    @Test
    public void testWritingANewFileWorks() throws Exception
    {
        File fresh = new File(dir.toFile(), "brand-new.dat");

        Util.writeAtomically(fresh, out -> out.write("created".getBytes(StandardCharsets.UTF_8)));

        assertEquals(new String(Files.readAllBytes(fresh.toPath()), StandardCharsets.UTF_8), "created");
    }

    /**
     * Not serializable, so writeObject throws once the stream reaches it - after the header and the
     * first element are already in the stream, which is the mid-write case.
     */
    private static final class Unserializable implements Serializable
    {
        @SuppressWarnings("unused")
        private final Object trouble = new Object();
    }

    /**
     * The backup archive holds the whole layout folder, not a file from it.
     *
     * FR-015. The autonomy setup is keyed by PAGE ID, and those ids are defined by
     * `config/gleisbild.cs2` - so `setup.json` on its own means nothing, and a `gleisbild.cs2` from a
     * different day silently reattaches every station to the wrong page. That is not a hypothetical:
     * it is what the 23 August restore had to undo, and the reason it took a bracketed comparison of
     * three snapshots to do it.
     *
     * So what this pins is not "a zip was written" but that the pieces which are only meaningful
     * together are in the same archive, under paths that say where they came from.
     */
    @Test
    public void testTheBackupArchiveHoldsEveryPieceOfTheState() throws Exception
    {
        File config = new File(dir.toFile(), "config");
        File pages = new File(config, "gleisbilder");
        File autonomy = new File(config, "autonomy");

        assertTrue(pages.mkdirs() && autonomy.mkdirs(), "could not build the fixture");

        Files.write(new File(dir.toFile(), "UIState.data").toPath(),
            "ui".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(dir.toFile(), "LocDB.data").toPath(),
            "locs".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(config, "gleisbild.cs2").toPath(),
            "index".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(pages, "1 - Main.cs2").toPath(),
            "page".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(autonomy, "setup.json").toPath(),
            "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(autonomy, "configuration-Only.json").toPath(),
            "{}".getBytes(StandardCharsets.UTF_8));

        java.util.Map<String, File> state = new java.util.LinkedHashMap<>();

        state.put("UIState.data", new File(dir.toFile(), "UIState.data"));
        state.put("LocDB.data", new File(dir.toFile(), "LocDB.data"));
        state.put("config", config);

        // and one that is not there, which is ordinary - a layout held on the Central Station has no
        // local config, and a first run has no UI state
        state.put("NotThere.data", new File(dir.toFile(), "NotThere.data"));

        File zip = new File(dir.toFile(), "backup.zip");

        List<String> failed = org.traincontrol.util.Util.zipInto(zip, state);

        assertEquals(failed, new java.util.ArrayList<String>(),
            "the archive reported failures: " + failed);

        assertTrue(zip.isFile() && zip.length() > 0, "no archive was written");

        java.util.Set<String> entries = new java.util.LinkedHashSet<>();

        try (java.util.zip.ZipFile read = new java.util.zip.ZipFile(zip))
        {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> all = read.entries();

            while (all.hasMoreElements()) entries.add(all.nextElement().getName());
        }

        assertTrue(entries.contains("UIState.data"), "the UI state is not in the backup: " + entries);
        assertTrue(entries.contains("LocDB.data"),
            "the locomotive database is not in the backup: " + entries);
        assertTrue(entries.contains("config/gleisbild.cs2"),
            "the page index is not in the backup - without it the autonomy setup's page ids mean "
            + "nothing, which is the whole reason this is one archive: " + entries);
        assertTrue(entries.contains("config/gleisbilder/1 - Main.cs2"),
            "the track diagram pages are not in the backup: " + entries);
        assertTrue(entries.contains("config/autonomy/setup.json"),
            "the autonomy setup is not in the backup: " + entries);
        assertTrue(entries.contains("config/autonomy/configuration-Only.json"),
            "the configurations are not in the backup: " + entries);

        assertFalse(entries.contains("NotThere.data"),
            "a source that does not exist was added as an empty entry rather than skipped");
    }
}
