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
     * A wrapper that throws in its constructor does not leak the stream underneath it.  `restoreState` opened the
     * database as `new ObjectInputStream(new FileInputStream(file))` in one try-with-resources; the constructor THROWS
     * on a corrupt header, so nothing is closed and the `FileInputStream` stays open.  Then the
     * keep-aside-and-write-fresh recovery fails, because `writeAtomically` ends with `Files.move(REPLACE_EXISTING)` and
     * Windows will not replace a file somebody holds open: the corrupt file stays, and each run adds another copy to
     * `tc_backup`.
     *
     * Tested directly (a wrapper that throws mid-construction, then the same `writeAtomically`) rather than through
     * `restoreState`, which would need a corrupt `LocDB.data` in the operator's own working directory.
     *
     * MUTATION: put the stream back inside the wrapper's argument list - one resource instead of two
     * - and the write fails with "being used by another process".
     */
    @Test
    public void testAThrowingWrapperDoesNotHoldTheFileOpen() throws Exception
    {
        final java.io.File folder = java.nio.file.Files.createTempDirectory("tc-leak").toFile();

        try
        {
            final java.io.File target = new java.io.File(folder, "LocDB.data");

            // Not a serialized stream, so ObjectInputStream throws while reading the header - which is
            // exactly what a corrupt database does.
            java.nio.file.Files.write(target.toPath(),
                "this is not a serialized object stream".getBytes("UTF-8"));

            boolean threw = false;

            // THE SHAPE THE FIX INTRODUCED: the stream in its own resource, so it closes even though
            // the wrapper never came into existence.
            try (java.io.FileInputStream in = new java.io.FileInputStream(target);
                java.io.ObjectInputStream obj = new java.io.ObjectInputStream(in))
            {
                obj.readObject();
            }
            catch (java.io.IOException expected)
            {
                threw = true;
            }

            assertTrue(threw,
                "precondition: reading a corrupt file has to fail, or nothing below is about the "
                + "failure path at all");

            // AND THE CONSEQUENCE, through the very method the recovery uses.  A leaked handle does
            // not announce itself; what it does is stop this.
            org.traincontrol.util.Util.writeAtomically(target,
                out -> out.write("recovered".getBytes("UTF-8")));

            assertEquals(new String(java.nio.file.Files.readAllBytes(target.toPath()), "UTF-8"),
                "recovered",
                "the file could not be replaced after a failed read.  That is the leaked handle: the "
                + "keep-aside-and-write-fresh recovery ends in Files.move(REPLACE_EXISTING), Windows "
                + "will not replace a file somebody has open, and the session's database changes are "
                + "lost with one log line while the corrupt file stays put for the next run (AC3-B1)");

            // AND NO STAGING FILE LEFT BESIDE IT, which the same probe found.
            assertFalse(new java.io.File(folder, "LocDB.data.part").exists(),
                "the staging file was left behind beside the target");
        }
        finally
        {
            for (java.io.File f : folder.listFiles()) f.delete();

            folder.delete();
        }
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
     * The backup archive holds the whole layout folder, not a file from it (FR-015): what this pins is that the pieces
     * which are only meaningful together are in the same archive, under paths that say where they came from.
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

    /**
     * A file the backup cannot read (a sync client holding a lock on it) is named and left out, not stored as an empty
     * entry that a restore would write over the real file.
     *
     * MUTATION: in `Util.addToZip`, begin the entry before reading the file, and an empty entry is left.
     */
    @Test
    public void testAFileThatWillNotReadLeavesNoEmptyEntry() throws Exception
    {
        File locked = new File(dir.toFile(), "setup.json");

        Files.write(locked.toPath(), "{}".getBytes(StandardCharsets.UTF_8));

        java.util.Map<String, File> state = new java.util.LinkedHashMap<>();

        state.put("state.dat", target);
        state.put("setup.json", locked);

        File zip = new File(dir.toFile(), "backup.zip");

        List<String> failed;

        try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(locked, "rw");
            java.nio.channels.FileLock lock = file.getChannel().lock())
        {
            failed = Util.zipInto(zip, state);
        }

        assertEquals(failed.size(), 1, "the locked file was not named as left out: " + failed);
        assertTrue(failed.get(0).startsWith("setup.json"), "the wrong file was named: " + failed);

        assertEquals(entriesOf(zip), Arrays.asList("state.dat"), "the file that could not be read is in the archive"
            + " as an empty entry, which a restore writes over the real file");
    }

    /**
     * An archive is written under another name and moved to its own once whole, so a backup cut off part way (the
     * program closed under it) leaves no file that looks like a backup.  Watched from inside the write: the source's
     * `exists()` asks whether anything is under the archive's name yet.
     *
     * MUTATION: in `Util.zipInto`, write the zip straight to its name, and the name exists while it is written.
     */
    @Test
    public void testAnArchiveHasItsNameOnlyOnceWhole() throws Exception
    {
        File zip = new File(dir.toFile(), "backup.zip");

        boolean[] namedWhileWriting = {false};

        File watched = new File(target.getPath())
        {
            @Override
            public boolean isFile()
            {
                namedWhileWriting[0] |= zip.exists();

                return super.isFile();
            }

            @Override
            public boolean isDirectory()
            {
                namedWhileWriting[0] |= zip.exists();

                return super.isDirectory();
            }
        };

        java.util.Map<String, File> state = new java.util.LinkedHashMap<>();

        state.put("state.dat", watched);

        assertEquals(Util.zipInto(zip, state), new java.util.ArrayList<String>(), "the archive reported failures");

        assertFalse(namedWhileWriting[0], "the archive was under its own name while it was still being written, so a"
            + " backup cut off part way looks like a whole one");

        assertEquals(entriesOf(zip), Arrays.asList("state.dat"), "the archive is not whole once written");
    }

    /**
     * The staged write forces its bytes to disk before it moves them over the target: renamed first, a power cut can
     * leave the target empty with the old file already gone.  Read, not run: a power cut cannot be staged.
     *
     * MUTATION: drop the `force(true)` from `Util.writeAtomically`, and this fails.
     */
    @Test
    public void testTheStagedWriteReachesTheDiskBeforeTheMove() throws Exception
    {
        String body = support.SourceOf.withoutComments(support.SourceOf.body(
            "public static void writeAtomically(File target, StreamWriter body)"));

        int forced = Math.max(body.indexOf("getFD().sync()"), body.indexOf(".force(true)"));

        assertTrue(forced >= 0 && forced < body.indexOf("Files.move("), "the staged file is moved over the target"
            + " without first being forced to disk");
    }

    /**
     * A drive that refuses to force the staged file to disk still gets the file, and says so in the log once, not at
     * every save: refused, every save would fail, and the exit would ask Retry for ever.  The refusal is staged by
     * making the staging file read-only from inside the write, so the forcing handle cannot be opened.
     *
     * MUTATION: in `Util.writeAtomically`, drop the catch around the force, and the save fails.
     */
    @Test
    public void testADriveThatRefusesTheForceStillGetsTheFile() throws Exception
    {
        List<String> logged = java.util.Collections.synchronizedList(new java.util.ArrayList<String>());

        java.util.logging.Handler tap = support.LogTap.everyMessageInto(logged);
        java.util.logging.Logger log = java.util.logging.Logger.getLogger(Util.class.getName());

        log.addHandler(tap);

        try
        {
            for (String content : new String[] {"first", "second"})
            {
                Util.writeAtomically(target, out ->
                {
                    out.write(content.getBytes(StandardCharsets.UTF_8));

                    for (File staging : dir.toFile().listFiles((folder, name) -> name.endsWith(".part")))
                    {
                        assertTrue(staging.setReadOnly(), "could not stage the refusal");
                    }
                });

                assertEquals(contents(), content, "the save did not reach the file where the force was refused");

                assertTrue(target.setWritable(true), "could not undo the fixture");
            }
        }
        finally
        {
            log.removeHandler(tap);

            target.setWritable(true);
        }

        // Once in this JVM, which no other claim refuses in
        assertEquals(logged.size(), 1, "the refusal was not logged once: " + logged);
    }

    /**
     * Restoring an archive whose entry is empty (an older backup stored a file it could not read that way) keeps the
     * file there now when it has content, says so in the question, and restores the rest.
     *
     * MUTATION: make `RestoreAction.Archive.keepsWhatIsThere` answer false, and the setup is emptied.
     */
    @Test
    public void testARestoreNeverEmptiesAFileWithAnEmptyEntry() throws Exception
    {
        java.io.ByteArrayOutputStream windowState = new java.io.ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(windowState))
        {
            out.writeObject(new java.util.ArrayList<Object>());
        }

        File zip = new File(dir.toFile(), "old-backup.zip");

        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip)))
        {
            String[][] entries = {{"UIState.data", null}, {"Yard/config/gleisbild.cs2", "index from the backup"},
                {"Yard/config/autonomy/setup.json", ""}};

            for (String[] entry : entries)
            {
                out.putNextEntry(new java.util.zip.ZipEntry(entry[0]));
                out.write(entry[1] == null ? windowState.toByteArray() : entry[1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }

        File yard = new File(dir.toFile(), "Yard");
        File setup = new File(yard, "config/autonomy/setup.json");

        assertTrue(setup.getParentFile().mkdirs(), "could not build the fixture");
        Files.write(setup.toPath(), "{\"the setup\": \"now\"}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(yard, "config/gleisbild.cs2").toPath(), "index now".getBytes(StandardCharsets.UTF_8));

        Class<?> archiveClass = Class.forName("org.traincontrol.gui.widgets.RestoreAction$Archive");

        Object archive = support.Reflect.method(archiveClass, "read", File.class,
            org.traincontrol.model.ViewListener.class).invoke(null, zip, null);

        String question = (String) support.Reflect.method(archiveClass, "parts", File.class).invoke(archive, yard);

        support.Reflect.method(archiveClass, "writeLayout", File.class).invoke(archive, yard);

        assertEquals(new String(Files.readAllBytes(setup.toPath()), StandardCharsets.UTF_8), "{\"the setup\": \"now\"}",
            "the restore wrote the backup's empty setup.json over the setup there now");

        assertEquals(new String(Files.readAllBytes(new File(yard, "config/gleisbild.cs2").toPath()),
            StandardCharsets.UTF_8), "index from the backup", "the rest of the layout was not restored");

        assertTrue(question.contains(org.traincontrol.util.I18n.f("ui.restoreKeptAsNow", "autonomy/setup.json")),
            "the question does not say the empty file is kept as it is now: " + question);
    }

    /** The entry names of a zip, in order. */
    private static List<String> entriesOf(File zip) throws Exception
    {
        try (java.util.zip.ZipFile read = new java.util.zip.ZipFile(zip))
        {
            return read.stream().map(java.util.zip.ZipEntry::getName).collect(java.util.stream.Collectors.toList());
        }
    }
}
