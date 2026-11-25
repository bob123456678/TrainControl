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
            assertEquals(folder.list((at, name) -> name.endsWith(Util.PARTIAL_DOWNLOAD_SUFFIX)).length, 0,
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

        assertEquals(dir.toFile().list((at, name) -> name.endsWith(Util.PARTIAL_DOWNLOAD_SUFFIX)), new String[0],
            "the staging file must be cleaned up");
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
     * Two writes of one file at once (the exit's save and a backup's first save) each get a staging file of their own:
     * sharing one, the second's move fails on the first's open handle, or moves the first's half-written file into
     * place.  Staged by a second write from inside the first; both succeed, the last to finish wins, whole.
     *
     * MUTATION: in `Util.writeAtomically`, stage at `target + ".part"` again, and the inner write fails.
     */
    @Test
    public void testTwoWritesOfOneFileAtOnceDoNotShareAStagingFile() throws Exception
    {
        Util.writeAtomically(target, out ->
        {
            out.write("outer, ".getBytes(StandardCharsets.UTF_8));

            Util.writeAtomically(target, inner -> inner.write("inner".getBytes(StandardCharsets.UTF_8)));

            assertEquals(new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8), "inner",
                "the overlapping write did not reach the file");

            out.write("whole".getBytes(StandardCharsets.UTF_8));
        });

        assertEquals(contents(), "outer, whole", "the write that finished last is not the file, whole");
    }

    /**
     * A write cut off by a kill leaves its staging file, which no later write reuses: the next write of the same target
     * deletes one older than a minute.  A fresh one, which may be a write under way, and another file's are kept.
     *
     * MUTATION: drop the `sweepStaleStaging` call from `Util.writeAtomically`, and the killed write's file stays.
     */
    @Test
    public void testTheNextWriteSweepsAKilledWritesStagingFile() throws Exception
    {
        File killed = staged("state.dat.tmp123.part", true);
        File fresh = staged("state.dat.tmp456.part", false);
        File another = staged("other.dat.tmp789.part", true);
        File named = staged("state.dat.tmpX1.part", true);

        Util.writeAtomically(target, out -> out.write("new".getBytes(StandardCharsets.UTF_8)));

        assertFalse(killed.exists(), "the staging file a killed write left is still there");
        assertTrue(fresh.exists(), "a staging file under a minute old, which may be a write under way, was deleted");
        assertTrue(another.exists(), "another file's staging file was deleted");
        assertTrue(named.exists(), "a file not named as a staging file was deleted");
        assertEquals(contents(), "new", "the write itself did not reach the file");
    }

    /**
     * A write under way keeps its staging file however old it is: a second write of the same target, started inside
     * the first after the first's file has aged, leaves it alone, and both finish.  The body closes its stream, so the
     * file is not held open (an open file cannot be deleted on Windows).
     *
     * MUTATION: in `Util.sweepStaleStaging`, drop the `stagingInProgress` check, and the outer write fails.
     */
    @Test
    public void testAWriteUnderWayKeepsItsStagingFileHoweverOld() throws Exception
    {
        Util.writeAtomically(target, out ->
        {
            out.write("outer".getBytes(StandardCharsets.UTF_8));
            out.close();

            for (File mine : dir.toFile().listFiles((at, name) -> name.endsWith(Util.PARTIAL_DOWNLOAD_SUFFIX)))
            {
                mine.setLastModified(System.currentTimeMillis() - 3_600_000);
            }

            Util.writeAtomically(target, inner -> inner.write("inner".getBytes(StandardCharsets.UTF_8)));
        });

        assertEquals(contents(), "outer", "the write under way lost its staging file to the second write's sweep");
    }

    /**
     * A backup cut off by a kill leaves its archive's staging file, which no later write meets (each archive has a name
     * of its own): the next backup sweeps the backup folder of every staging file a minute old, the data files' too,
     * and keeps a fresh one.
     *
     * MUTATION: drop the `sweepStaleStaging` call from `Util.zipInto`, and the killed backup's file stays.
     */
    @Test
    public void testTheNextBackupSweepsAKilledBackupsStagingFile() throws Exception
    {
        assertTrue(new File(dir.toFile(), Util.BACKUP_FOLDER).mkdir(), "could not build the fixture");

        File killed = staged(Util.BACKUP_FOLDER + "/backup2026-11-14_10-00-00-TrainControl.zip.tmp123.part", true);
        File data = staged(Util.BACKUP_FOLDER + "/backup2026-11-14_10-00-00LocDB.data.tmp45.part", true);
        File fresh = staged(Util.BACKUP_FOLDER + "/backup2026-11-14_10-01-00-TrainControl.zip.tmp678.part", false);

        File archive = new File(dir.toFile(), Util.BACKUP_FOLDER + "/backup2026-11-14_10-02-00-TrainControl.zip");

        assertEquals(Util.zipInto(archive, java.util.Collections.singletonMap("state.dat", target)), new java.util.ArrayList<String>(),
            "the backup could not add its file");

        assertFalse(killed.exists(), "the staging file a killed backup left is still there");
        assertFalse(data.exists(), "the staging file a killed backup of a data file left is still there");
        assertTrue(fresh.exists(), "a staging file under a minute old, which may be a backup under way, was deleted");
        assertTrue(archive.isFile(), "the backup itself was not written");
    }

    /** A file in the test's folder, an hour old when `old`. */
    private File staged(String name, boolean old) throws IOException
    {
        File f = new File(dir.toFile(), name);

        Files.write(f.toPath(), "left".getBytes(StandardCharsets.UTF_8));

        if (old) assertTrue(f.setLastModified(System.currentTimeMillis() - 3_600_000), "cannot age " + f);

        return f;
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

        Class<?> archiveClass = Class.forName("org.traincontrol.gui.app.RestoreAction$Archive");

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

    /**
     * The same for `autonomy.json`, the legacy file a setup is imported from (OB-254): an empty entry keeps the file
     * there now and the question says so.  Only the autonomy part is written; the window state the archive needs to
     * read is taken out first, so the run's own is not touched.
     *
     * MUTATION: in `RestoreAction.Archive.writeData`, write `autonomyFile` without asking `keepsWhatIsThere`.
     */
    @Test
    public void testARestoreNeverEmptiesTheAutonomyFileWithAnEmptyEntry() throws Exception
    {
        java.io.ByteArrayOutputStream windowState = new java.io.ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(windowState))
        {
            out.writeObject(new java.util.ArrayList<Object>());
        }

        File zip = new File(dir.toFile(), "old-backup.zip");

        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip)))
        {
            out.putNextEntry(new java.util.zip.ZipEntry("UIState.data"));
            out.write(windowState.toByteArray());
            out.closeEntry();
            out.putNextEntry(new java.util.zip.ZipEntry("autonomy.json"));
            out.closeEntry();
        }

        File legacy = new File(org.traincontrol.gui.TrainControlUI.AUTONOMY_FILE_NAME).getAbsoluteFile();
        byte[] was = legacy.exists() ? Files.readAllBytes(legacy.toPath()) : null;

        try
        {
            Files.write(legacy.toPath(), "{\"the legacy setup\": \"now\"}".getBytes(StandardCharsets.UTF_8));

            Class<?> archiveClass = Class.forName("org.traincontrol.gui.app.RestoreAction$Archive");

            Object archive = support.Reflect.method(archiveClass, "read", File.class,
                org.traincontrol.model.ViewListener.class).invoke(null, zip, null);

            support.Reflect.set(archiveClass, archive, "windowState", null);

            String question = (String) support.Reflect.method(archiveClass, "parts", File.class)
                .invoke(archive, (Object) null);

            support.Reflect.method(archiveClass, "writeData").invoke(archive);

            assertEquals(new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.UTF_8),
                "{\"the legacy setup\": \"now\"}", "the restore wrote the backup's empty autonomy.json over the file");

            assertTrue(question.contains(org.traincontrol.util.I18n.f("ui.restoreKeptAsNow", "autonomy.json")),
                "the question does not say autonomy.json is kept as it is now: " + question);
        }
        finally
        {
            if (was == null) Files.deleteIfExists(legacy.toPath());
            else Files.write(legacy.toPath(), was);
        }
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
