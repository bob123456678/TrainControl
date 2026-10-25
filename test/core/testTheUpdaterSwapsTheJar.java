package core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.util.JarUpdater;
import org.traincontrol.util.SelfUpdate;

/**
 * One-click updates swap the jar safely, or not at all (FR-125).
 *
 * Adam, 2026-10-12: *"after downloading the new jar, it should take a backup, then start the update script and close
 * TrainControl."*  The updater is `JarUpdater`, run by the same Java once TrainControl has closed: it renames the old
 * jar aside, moves the new one to its name, starts it, and puts the old one back when it does not start.  The
 * application's half is `SelfUpdate`: whether a download is whole, starting the updater from a copy of the jar and
 * hearing that it runs, and one update at a time.
 *
 * **Nothing here starts TrainControl or the updater, ends this JVM or touches a real jar.**  Every jar is built in a
 * temporary folder; the updater's renames and its launcher are handed in, so a launch is a recorded command and a failed
 * rename is one the test throws.  The lock TrainControl holds until it exits is held by this test instead, in this JVM -
 * except in `testALockHeldByAnotherProcessIsSeen`, which starts one plain JVM of its own (`support.HoldsALock`, nothing
 * of TrainControl's) to hold it across a process boundary.
 *
 * MUTATIONS, each seen red: `backupName` not looking for a free name (the swap claim); `swap` not moving the backup
 * back when the second rename fails (the rollback claim); `waitForTheStart` reading an ended process as started, or
 * ignoring the marker, or `update` rolling back on no word (the three started-marker claims); `rollBack` leaving no
 * note, or a note that says put back when the old jar is not (the ended and half-undone claims); `waitForLock`
 * returning true at once (the lock, timeout, copy and process claims);
 * `whyNotAWholeJar` skipping the jar check (the truncated and HTML claim); `startUpdater` putting the jar itself on the
 * updater's classpath (the copy claim); `startUpdater` not waiting to hear from the updater, or keeping the lock when it
 * does not (the updater-did-not-start claim); `HandOver.exitWith` disarming only after a normal return, or `begin`
 * allowing a second, or `startUpdater` letting go of a lock already held (the one-at-a-time claim);
 * `Request.parse` dropping the Java options (the command-line claim); `downloadTarget` returning the running jar's own
 * name (the download claim); `startUpdater` handing on the jar's folder for TrainControl's working directory (the copy
 * claim).
 */
public class testTheUpdaterSwapsTheJar
{
    /**
     * What the new version does when the updater starts it.
     */
    private enum NewVersion
    {
        SAYS_STARTED_AND_RUNS, SAYS_STARTED_AND_ENDS, ENDS_SILENTLY, RUNS_SILENTLY
    }

    private File folder;
    private File jar;
    private File download;
    private JarUpdater.Log log;

    @BeforeMethod
    public void setUp() throws Exception
    {
        folder = Files.createTempDirectory("tc-fr125 ").toFile();

        jar = new File(folder, "TrainControl.jar");
        download = new File(folder, "TrainControl_v3_0_2.jar");

        write(jar, "old");
        write(download, "new");

        log = new JarUpdater.Log(new File(folder, JarUpdater.LOG_NAME));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception
    {
        SelfUpdate.releaseTheExitLockForTests();
        SelfUpdate.rememberArguments(new String[0]);

        if (folder != null && folder.exists())
        {
            Files.walk(folder.toPath())
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }

    /**
     * The old jar is kept under a versioned name, never over a file already there, and the new one takes the old one's
     * exact name so a shortcut to it keeps working.
     *
     * Adam, 2026-10-12: "take a backup" means the old jar kept, renamed, so the update can be undone by hand.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheSwapKeepsTheOldJarAndTakesItsName() throws Exception
    {
        File earlier = new File(folder, "TrainControl_v3_0_1_old.jar");

        write(earlier, "an earlier update's backup");

        File backup = JarUpdater.swap(jar, download, "3.0.1", rename(), 1, 0, log);

        assertEquals(read(jar), "new", "the new version is not under the old jar's name, so a shortcut to "
            + jar.getName() + " no longer starts the version just installed");

        assertEquals(backup.getName(), "TrainControl_v3_0_1_old_2.jar", "the old jar was not kept under the next free"
            + " versioned name");

        assertEquals(read(backup), "old", "the kept copy is not the old version");

        assertEquals(read(earlier), "an earlier update's backup", "an existing file was overwritten by the backup:"
            + " the update must never overwrite, only add a number");

        assertFalse(download.exists(), "the download is still there beside the installed copy");

        // And a jar already named for its version, which is what Download saves, is not named for it twice
        assertEquals(JarUpdater.backupName(new File(folder, "TrainControl_v3_0_0.jar"), "3.0.0").getName(),
            "TrainControl_v3_0_0_old.jar");
    }

    /**
     * When the new jar cannot be moved into place, the old one goes back where it was and nothing is lost, and the
     * old version is the one started.
     *
     * @throws Exception from the files
     */
    @Test
    public void testAFailedMovePutsTheOldJarBack() throws Exception
    {
        // The rename aside works; the move of the new jar into the old one's name fails, as a held file would
        final JarUpdater.Mover failsTheSecond = new JarUpdater.Mover()
        {
            private int calls;

            @Override
            public void move(Path from, Path to) throws java.io.IOException
            {
                if (++calls == 2) throw new java.io.IOException("refused by the test");

                Files.move(from, to);
            }
        };

        List<String> started = new ArrayList<>();

        JarUpdater.Outcome outcome = JarUpdater.update(request(), 5000, 10, 1, 0, 5000,
            launching(started, new ArrayList<>(), new ArrayList<>(), NewVersion.RUNS_SILENTLY), failsTheSecond, log,
            null);

        assertEquals(outcome, JarUpdater.Outcome.ROLLED_BACK);

        assertEquals(read(jar), "old", "the move failed and the old jar was not put back under its own name, so"
            + " TrainControl has no jar to start");

        assertEquals(read(download), "new", "the download was lost");

        assertEquals(backups(), Collections.emptyList(), "a backup copy was left behind although the old jar went"
            + " back into place");

        assertEquals(started, Collections.singletonList("old"), "after a failed update the old version must be"
            + " started again, and only it");

        assertTrue(read(log.getFile()).contains("The update failed"), "the failure is not in " + JarUpdater.LOG_NAME);
    }

    /**
     * The new version has started when it says so - by writing the marker it is told of outside its command line -
     * and that holds when it then ends at once, as it does when its IP question is canceled: an exit code of 1 is
     * not a failed start.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheMarkerMeansStarted() throws Exception
    {
        for (NewVersion behaviour : new NewVersion[] {NewVersion.SAYS_STARTED_AND_RUNS, NewVersion.SAYS_STARTED_AND_ENDS})
        {
            tearDown();
            setUp();

            List<String> started = new ArrayList<>();
            List<List<String>> commands = new ArrayList<>();
            List<File> markers = new ArrayList<>();

            JarUpdater.Outcome outcome = JarUpdater.update(request(), 5000, 10, 1, 0, 5000,
                launching(started, commands, markers, behaviour), rename(), log, null);

            assertEquals(outcome, JarUpdater.Outcome.UPDATED, behaviour + ": a version that said it had started was"
                + " not kept");

            assertEquals(started, Collections.singletonList("new"), behaviour + ": the old version was started too");
            assertEquals(read(jar), "new");

            assertNotNull(markers.get(0), "the new version was not told where to say it had started");

            for (String part : commands.get(0))
            {
                assertFalse(part.contains(JarUpdater.STARTED_NAME), "the started marker travels on the command line,"
                    + " where the Java options carry it into every later restart: " + part);
            }

            assertFalse(new File(folder, JarUpdater.ROLLBACK_NOTE_NAME).exists(), "a rollback note after an update"
                + " that started");

            assertFalse(new File(folder, JarUpdater.LOCK_NAME).exists(), "the lock file was left beside the jar");
        }
    }

    /**
     * A new version that ends before saying it has started is taken back out, whatever its exit code: the old jar
     * returns to its name and is started, without a marker, and a note is left for it to say so once.
     *
     * @throws Exception from the files
     */
    @Test
    public void testAnExitBeforeTheMarkerRollsBack() throws Exception
    {
        List<String> started = new ArrayList<>();
        List<File> markers = new ArrayList<>();

        JarUpdater.Outcome outcome = JarUpdater.update(request(), 5000, 10, 1, 0, 5000,
            launching(started, new ArrayList<>(), markers, NewVersion.ENDS_SILENTLY), rename(), log, null);

        assertEquals(outcome, JarUpdater.Outcome.ROLLED_BACK);

        assertEquals(started, Arrays.asList("new", "old"), "the new version ended without starting and the old one"
            + " was not put back and started");

        assertNull(markers.get(1), "the old version was started with a marker of its own to write");

        assertEquals(read(jar), "old");
        assertEquals(read(download), "new", "the failed download should be back under its own name");

        assertEquals(SelfUpdate.takeRollbackNote(folder), new String[] {"3.0.2", "3.0.1", null}, "the old version is"
            + " not told the update was put back, so it starts as though nothing happened");

        assertNull(SelfUpdate.takeRollbackNote(folder), "the note is said again at the next start");
    }

    /**
     * When putting the old jar back stops half way - the new one cannot be moved off its name, as a just-ended JVM or
     * a virus scanner holding it would cause - the old version is started from its kept copy, and its note says which
     * file that is rather than that it was put back (FR-125, 2026-10-13).
     *
     * @throws Exception from the files
     */
    @Test
    public void testAHalfDoneUndoDoesNotSayPutBack() throws Exception
    {
        // Aside, then in; the undo's first move, the new jar back to its download name, fails
        final JarUpdater.Mover failsTheThird = new JarUpdater.Mover()
        {
            private int calls;

            @Override
            public void move(Path from, Path to) throws java.io.IOException
            {
                if (++calls == 3) throw new java.io.IOException("held by the test");

                Files.move(from, to);
            }
        };

        List<String> started = new ArrayList<>();
        List<List<String>> commands = new ArrayList<>();

        JarUpdater.Outcome outcome = JarUpdater.update(request(), 5000, 10, 1, 0, 5000,
            launching(started, commands, new ArrayList<>(), NewVersion.ENDS_SILENTLY), failsTheThird, log, null);

        assertEquals(outcome, JarUpdater.Outcome.ROLLED_BACK);

        assertEquals(started, Arrays.asList("new", "old"), "the old version was not started from its kept copy");

        File kept = new File(folder, "TrainControl_v3_0_1_old.jar");

        assertEquals(jarIn(commands.get(1)).getAbsoluteFile(), kept.getAbsoluteFile());

        String[] note = SelfUpdate.takeRollbackNote(folder);

        assertNotNull(note, "the old version is not told what happened");

        assertEquals(note[2], kept.getName(), "the old version is told it was put back while the jar's own name still"
            + " holds the version that did not start");
    }

    /**
     * A new version older than the started marker (3.0.0, as in MT-737) is kept once it runs, even when the user closes
     * it at once: it cannot say it started, so its exit is no sign of a failed start (Adam, 2026-10-13: "after the newly
     * updated app opens, is used by the user, and is closed, the old version is put back").
     *
     * @throws Exception from the files
     */
    @Test
    public void testAVersionOlderThanTheMarkerIsKeptWhenClosed() throws Exception
    {
        List<String> started = new ArrayList<>();

        JarUpdater.Request older = new JarUpdater.Request(jar, download, new File(folder, JarUpdater.LOCK_NAME),
            new File(folder, JarUpdater.RUNNING_NAME), "2.9.9", "3.0.0", new File(folder, "working"),
            "C:/the/same/javaw.exe", "Updating TrainControl...", Collections.singletonList("-Duser.language=de"),
            Arrays.asList("192.168.1.5", "debug"));

        JarUpdater.Outcome outcome = JarUpdater.update(older, 5000, 10, 1, 0, 5000,
            launching(started, new ArrayList<>(), new ArrayList<>(), NewVersion.ENDS_SILENTLY), rename(), log, null);

        assertEquals(outcome, JarUpdater.Outcome.UNCONFIRMED, "a 3.0.0 the user closed was taken for a failed start");
        assertEquals(started, Collections.singletonList("new"), "the old version was put back and started");
        assertEquals(read(jar), "new");
        assertFalse(new File(folder, JarUpdater.ROLLBACK_NOTE_NAME).exists(), "a note says the update failed");

        assertTrue(JarUpdater.marksItsStart("3.0.1") && JarUpdater.marksItsStart("3.0.2")
            && JarUpdater.marksItsStart("3.1") && JarUpdater.marksItsStart("4.0.0") && JarUpdater.marksItsStart("?"),
            "a version that writes the marker is not waited for");
        assertFalse(JarUpdater.marksItsStart("3.0.0") || JarUpdater.marksItsStart("2.9.9")
            || JarUpdater.marksItsStart("2.10.4"), "a version older than the marker is waited for");
    }

    /**
     * A new version still running that has said nothing by the cap is kept, and the log says so; nothing is put back.
     *
     * @throws Exception from the files
     */
    @Test
    public void testNoWordByTheCapKeepsTheNewJar() throws Exception
    {
        List<String> started = new ArrayList<>();

        JarUpdater.Outcome outcome = JarUpdater.update(request(), 5000, 10, 1, 0, 300,
            launching(started, new ArrayList<>(), new ArrayList<>(), NewVersion.RUNS_SILENTLY), rename(), log, null);

        assertEquals(outcome, JarUpdater.Outcome.UNCONFIRMED);
        assertEquals(read(jar), "new", "a running version that had not said it started was taken out");
        assertEquals(started, Collections.singletonList("new"));
        assertFalse(new File(folder, JarUpdater.ROLLBACK_NOTE_NAME).exists());
        assertTrue(read(log.getFile()).contains("so it was kept"), "the log does not say the new version was kept"
            + " unconfirmed");
    }

    /**
     * The updater waits while TrainControl holds its lock, changes nothing meanwhile, and swaps and starts the new
     * version once the lock is let go - with the same Java, options, arguments and working directory - and leaves no
     * lock file behind.
     *
     * @throws Exception from the files or the thread
     */
    @Test
    public void testTheUpdaterWaitsForTheLock() throws Exception
    {
        final List<String> started = Collections.synchronizedList(new ArrayList<>());
        final List<List<String>> commands = Collections.synchronizedList(new ArrayList<>());
        final List<File> folders = Collections.synchronizedList(new ArrayList<>());
        final AtomicReference<JarUpdater.Outcome> outcome = new AtomicReference<>();

        final JarUpdater.Request request = request();

        Thread updater;

        try (RandomAccessFile file = new RandomAccessFile(request.lock, "rw");
            FileChannel channel = file.getChannel())
        {
            FileLock held = channel.lock();

            updater = new Thread(() -> outcome.set(JarUpdater.update(request, 30000, 20, 1, 0, 5000,
                (command, where, marker) ->
                {
                    commands.add(command);
                    folders.add(where);
                    started.add(read(jarIn(command)));

                    JarUpdater.writeMarker(marker);

                    return () -> true;
                }, rename(), log, null)));

            updater.start();

            Thread.sleep(1000);

            assertTrue(updater.isAlive(), "the updater did not wait for TrainControl to let go of its lock");
            assertEquals(read(jar), "old", "the jar was changed while TrainControl still held its lock");
            assertTrue(started.isEmpty(), "something was started while TrainControl was still running");

            held.release();
        }

        updater.join(10000);

        assertFalse(updater.isAlive(), "the updater did not finish once the lock was let go");

        assertEquals(outcome.get(), JarUpdater.Outcome.UPDATED);
        assertEquals(read(jar), "new");
        assertEquals(started, Collections.singletonList("new"));

        assertEquals(commands.get(0), Arrays.asList("C:/the/same/javaw.exe", "-Duser.language=de", "-jar",
            jar.getAbsolutePath(), "192.168.1.5", "debug"), "the new version was not started the way TrainControl was");

        assertEquals(folders.get(0), new File(folder, "working").getAbsoluteFile(), "the new version was not started"
            + " in the working directory TrainControl had, which is where its data is read from");
    }

    /**
     * When TrainControl never lets go, the updater gives up after its timeout with nothing changed and nothing
     * started, and says why in the log.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheUpdaterGivesUpWithNothingChanged() throws Exception
    {
        List<String> started = new ArrayList<>();

        JarUpdater.Request request = request();

        JarUpdater.Outcome outcome;

        try (RandomAccessFile file = new RandomAccessFile(request.lock, "rw");
            FileChannel channel = file.getChannel())
        {
            FileLock held = channel.lock();

            outcome = JarUpdater.update(request, 300, 20, 1, 0, 5000,
                launching(started, new ArrayList<>(), new ArrayList<>(), NewVersion.SAYS_STARTED_AND_RUNS), rename(),
                log, null);

            held.release();
        }

        assertEquals(outcome, JarUpdater.Outcome.GAVE_UP);
        assertEquals(read(jar), "old", "the jar was changed although TrainControl never closed");
        assertEquals(read(download), "new");
        assertEquals(backups(), Collections.emptyList(), "a backup was made although nothing was updated");
        assertTrue(started.isEmpty(), "something was started although TrainControl never closed: a second copy");
        assertTrue(read(log.getFile()).contains("did not close"), "the log does not say why nothing happened");
    }

    /**
     * Across a real process boundary: a lock held by another JVM reads as held, and as free once that JVM has ended
     * without letting go - which is how TrainControl's exit releases it.
     *
     * @throws Exception from the files or the process
     */
    @Test
    public void testALockHeldByAnotherProcessIsSeen() throws Exception
    {
        File lock = new File(folder, JarUpdater.LOCK_NAME);
        File ready = new File(folder, "ready");
        File stop = new File(folder, "stop");
        File said = new File(folder, "other.log");

        String classes = new File(support.HoldsALock.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .getPath();

        String java = new File(new File(System.getProperty("java.home"), "bin"),
            File.separatorChar == '\\' ? "java.exe" : "java").getPath();

        Process other = new ProcessBuilder(java, "-cp", classes, "support.HoldsALock", lock.getPath(), ready.getPath(),
            stop.getPath()).redirectErrorStream(true).redirectOutput(said).start();

        try
        {
            long until = System.currentTimeMillis() + 20000;

            while (!ready.isFile() && other.isAlive() && System.currentTimeMillis() < until) Thread.sleep(50);

            assertTrue(ready.isFile(), "precondition: the other JVM did not take the lock: " + read(said));

            assertFalse(JarUpdater.waitForLock(lock, 500, 50), "a lock another process holds read as free, so the"
                + " updater would swap the jar under a running TrainControl");

            write(stop, "stop");

            assertTrue(other.waitFor(20, TimeUnit.SECONDS), "precondition: the other JVM did not end");

            assertTrue(JarUpdater.waitForLock(lock, 5000, 50), "the lock still read as held after the process holding"
                + " it ended");
        }
        finally
        {
            if (other.isAlive()) other.destroyForcibly();
        }
    }

    /**
     * The request survives the command line it travels on, spaces and all.
     */
    @Test
    public void testTheRequestSurvivesTheCommandLine()
    {
        JarUpdater.Request sent = request();

        JarUpdater.Request got = JarUpdater.Request.parse(sent.toArguments().toArray(new String[0]));

        assertEquals(got.jar, sent.jar);
        assertEquals(got.newJar, sent.newJar);
        assertEquals(got.lock, sent.lock);
        assertEquals(got.running, sent.running);
        assertEquals(got.oldVersion, "3.0.1");
        assertEquals(got.newVersion, "3.0.2");
        assertEquals(got.workingDirectory, sent.workingDirectory);
        assertEquals(got.java, sent.java);
        assertEquals(got.windowText, "Updating TrainControl...");
        assertEquals(got.javaOptions, sent.javaOptions);
        assertEquals(got.arguments, sent.arguments);
    }

    /**
     * A download cut short, or a web page saved in the jar's place, is refused before anything is replaced; a whole
     * jar passes, and so does its size and checksum from the release.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheJarCheckRefusesACutDownloadOrAPage() throws Exception
    {
        File whole = new File(folder, "whole.jar");

        buildJar(whole, "TrainControl");

        long size = whole.length();
        String digest = "sha256:" + sha256(whole);

        assertNull(SelfUpdate.whyNotAWholeJar(whole, -1, null), "a whole jar was refused");
        assertNull(SelfUpdate.whyNotAWholeJar(whole, size, digest), "a whole jar with the release's size and"
            + " checksum was refused");

        // Cut short: the first two thirds, which is what a dropped connection leaves
        File cut = new File(folder, "cut.jar");
        byte[] bytes = Files.readAllBytes(whole.toPath());
        Files.write(cut.toPath(), Arrays.copyOf(bytes, bytes.length * 2 / 3));

        assertNotNull(SelfUpdate.whyNotAWholeJar(cut, -1, null), "a download cut short passed the jar check");
        assertNotNull(SelfUpdate.whyNotAWholeJar(cut, size, null), "a download cut short passed the size check");

        File page = new File(folder, "page.jar");
        write(page, "<!DOCTYPE html><html><body>Not Found</body></html>");

        assertNotNull(SelfUpdate.whyNotAWholeJar(page, -1, null), "an HTML page saved as the jar passed the check");

        File noMain = new File(folder, "nomain.jar");
        buildJar(noMain, null);

        assertNotNull(SelfUpdate.whyNotAWholeJar(noMain, -1, null), "a jar with no Main-Class passed the check");

        assertNotNull(SelfUpdate.whyNotAWholeJar(whole, size + 1, null), "a size that differs from the release's"
            + " passed");
        assertNotNull(SelfUpdate.whyNotAWholeJar(whole, size, "sha256:" + sha256(page)), "a checksum that differs"
            + " from the release's passed");
    }

    /**
     * The updater runs from a copy of the jar beside it, never the jar it replaces (a running jar is locked on Windows),
     * with the Java this JVM runs on, once it has said it is running - and the exit lock is held from then on.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheUpdaterRunsFromACopyWithTheSameJava() throws Exception
    {
        buildJar(jar, "TrainControl");

        SelfUpdate.rememberArguments(new String[] {"192.168.1.5", "debug"});

        final List<List<String>> asked = new ArrayList<>();

        SelfUpdate.startUpdater(jar, download, "3.0.1", "3.0.2", "Updating TrainControl...", (command, where) ->
        {
            asked.add(command);

            return sayingItRuns(command);
        }, 5000);

        assertEquals(asked.size(), 1, "the updater was not started");

        List<String> command = asked.get(0);

        assertEquals(command.get(0), SelfUpdate.javaExecutable());
        assertTrue(command.get(0).startsWith(new File(System.getProperty("java.home")).getPath()),
            "the updater is not run by the Java TrainControl runs on: " + command.get(0));

        assertEquals(command.get(1), "-cp");

        File copy = new File(command.get(2));

        assertNotEquals(copy.getAbsoluteFile(), jar.getAbsoluteFile(), "the updater runs from the jar it replaces,"
            + " which Windows keeps locked while it runs");
        assertEquals(copy.getParentFile().getAbsoluteFile(), folder.getAbsoluteFile(), "the updater's copy is not"
            + " beside the jar, whose folder is the one known to be writable");
        assertTrue(Arrays.equals(Files.readAllBytes(copy.toPath()), Files.readAllBytes(jar.toPath())),
            "the updater's copy is not a copy of the jar");

        assertEquals(command.get(3), JarUpdater.class.getName());

        JarUpdater.Request request = JarUpdater.Request.parse(
            command.subList(4, command.size()).toArray(new String[0]));

        assertEquals(request.jar, jar.getAbsoluteFile());
        assertEquals(request.newJar, download.getAbsoluteFile());
        assertEquals(request.newVersion, "3.0.2");
        assertEquals(request.arguments, Arrays.asList("192.168.1.5", "debug"), "the new version would start"
            + " without TrainControl's own arguments");
        assertEquals(request.lock.getParentFile(), folder.getAbsoluteFile());

        assertEquals(request.workingDirectory, new File(System.getProperty("user.dir")).getAbsoluteFile(), "the new"
            + " version would start somewhere other than TrainControl's working directory, which is where its data is"
            + " read from");

        assertTrue(SelfUpdate.isHoldingTheExitLock(), "the exit lock is not held, so the updater would not wait");
        assertFalse(JarUpdater.waitForLock(request.lock, 0, 10), "the updater could take the lock while"
            + " TrainControl is still running");
    }

    /**
     * An updater that does not say it is running keeps TrainControl open: the exit is refused, the lock let go and
     * removed, the copy deleted - whether it ended at once or sat silent past the wait, which also ends it.
     *
     * @throws Exception from the files
     */
    @Test
    public void testAnUpdaterThatDoesNotStartKeepsTrainControlOpen() throws Exception
    {
        buildJar(jar, "TrainControl");

        final List<List<String>> asked = new ArrayList<>();
        final boolean[] stopped = {false};

        for (final boolean endsAtOnce : new boolean[] {true, false})
        {
            stopped[0] = false;

            try
            {
                SelfUpdate.startUpdater(jar, download, "3.0.1", "3.0.2", "Updating TrainControl...", (command, where) ->
                {
                    asked.add(command);

                    return new SelfUpdate.Updater()
                    {
                        @Override
                        public boolean isAlive()
                        {
                            return !endsAtOnce && !stopped[0];
                        }

                        @Override
                        public void stop()
                        {
                            stopped[0] = true;
                        }
                    };
                }, 300);

                fail("an updater that never said it was running was taken as started, and TrainControl would close for"
                    + " an update that never happens");
            }
            catch (java.io.IOException expected)
            {
                // TrainControl stays open
            }

            assertFalse(SelfUpdate.isHoldingTheExitLock(), "the exit lock is still held after the updater failed to"
                + " start, so a later update would wait on it");
            assertFalse(new File(folder, JarUpdater.LOCK_NAME).exists(), "the lock file was left beside the jar");
            assertFalse(new File(asked.get(asked.size() - 1).get(2)).exists(), "the updater's copy was left behind");

            if (!endsAtOnce)
            {
                assertTrue(stopped[0], "an updater that sat silent past the wait was left running, to take the lock"
                    + " later and swap the jar under a TrainControl that stayed open");
            }
        }
    }

    /**
     * One update at a time, and an exit that does not happen leaves none armed: a second Download & Update is refused
     * while one is under way; an exit that throws or is canceled disarms the update; and a second `startUpdater`
     * while the lock is held is refused rather than letting go of the first one's lock.
     *
     * @throws Exception from the files
     */
    @Test
    public void testOneUpdateAtATime() throws Exception
    {
        SelfUpdate.HandOver handOver = new SelfUpdate.HandOver();

        SelfUpdate.Pending pending = new SelfUpdate.Pending(jar, download, "3.0.2");

        assertTrue(handOver.begin());
        assertFalse(handOver.begin(), "a second update was started while one was under way: two would be stacked");

        try
        {
            handOver.exitWith(pending, () ->
            {
                throw new IllegalStateException("the exit threw, as the test makes it");
            });

            fail("precondition: the exit's exception was swallowed");
        }
        catch (IllegalStateException expected)
        {
            // as the test made it
        }

        assertNull(handOver.take(), "an exit that threw left the update armed, so a later ordinary exit would start"
            + " the updater");
        assertTrue(handOver.begin(), "an exit that threw left an update under way for good");

        assertTrue(handOver.exitWith(pending, () -> { }), "an exit that came back without taking the update did not"
            + " read as cancelled");
        assertNull(handOver.take(), "a cancelled exit left the update armed");

        assertTrue(handOver.begin());
        assertFalse(handOver.exitWith(pending, () -> assertSame(handOver.take(), pending)), "an exit that took the"
            + " update read as cancelled");

        // And the lock itself: held once, refused the second time, still held
        buildJar(jar, "TrainControl");

        SelfUpdate.startUpdater(jar, download, "3.0.1", "3.0.2", "Updating TrainControl...",
            (command, where) -> sayingItRuns(command), 5000);

        final int[] second = {0};

        try
        {
            SelfUpdate.startUpdater(jar, download, "3.0.1", "3.0.2", "Updating TrainControl...", (command, where) ->
            {
                second[0]++;

                return sayingItRuns(command);
            }, 5000);

            fail("a second updater was started while the first one's lock was held");
        }
        catch (java.io.IOException expected)
        {
            // refused, not stacked
        }

        assertEquals(second[0], 0, "a second updater was started");
        assertTrue(SelfUpdate.isHoldingTheExitLock(), "the refused second update let go of the first one's lock, so"
            + " the first updater would swap the jar under a running TrainControl");
        assertFalse(JarUpdater.waitForLock(new File(folder, JarUpdater.LOCK_NAME), 0, 10));
    }

    /**
     * The download goes beside the jar, and never over the jar itself.
     */
    @Test
    public void testTheDownloadGoesBesideTheJar()
    {
        assertEquals(SelfUpdate.downloadTarget(jar, "3.0.2"), new File(folder, "TrainControl_v3_0_2.jar"));

        assertEquals(SelfUpdate.downloadTarget(null, "3.0.2"), new File("TrainControl_v3_0_2.jar"), "with no jar the"
            + " download goes to the working directory, as it always has");

        File named = new File(folder, "TrainControl_v3_0_2.jar");

        assertEquals(SelfUpdate.downloadTarget(named, "3.0.2"), new File(folder, "TrainControl_v3_0_2_new.jar"),
            "a running jar already named for the new version would be downloaded over");
    }

    private JarUpdater.Request request()
    {
        return new JarUpdater.Request(jar, download, new File(folder, JarUpdater.LOCK_NAME),
            new File(folder, JarUpdater.RUNNING_NAME), "3.0.1", "3.0.2", new File(folder, "working"),
            "C:/the/same/javaw.exe", "Updating TrainControl...", Collections.singletonList("-Duser.language=de"),
            Arrays.asList("192.168.1.5", "debug"));
    }

    /**
     * A launcher that records what the jar it was asked to start held, its command and its marker; the first start
     * (the new version, unless the swap failed) behaves as asked, and any later one keeps running.
     */
    private static JarUpdater.Launcher launching(final List<String> started, final List<List<String>> commands,
        final List<File> markers, final NewVersion behaviour)
    {
        return (command, where, marker) ->
        {
            started.add(read(jarIn(command)));
            commands.add(command);
            markers.add(marker);

            if (started.size() > 1) return () -> true;

            switch (behaviour)
            {
                case SAYS_STARTED_AND_RUNS:
                    JarUpdater.writeMarker(marker);

                    return () -> true;

                case SAYS_STARTED_AND_ENDS:
                    JarUpdater.writeMarker(marker);

                    return () -> false;

                case ENDS_SILENTLY:
                    return () -> false;

                default:
                    return () -> true;
            }
        };
    }

    /**
     * An updater that writes its running marker, as `JarUpdater.main` does first of all, and keeps running.
     */
    private static SelfUpdate.Updater sayingItRuns(List<String> command)
    {
        JarUpdater.writeMarker(JarUpdater.Request.parse(command.subList(4, command.size()).toArray(new String[0]))
            .running);

        return new SelfUpdate.Updater()
        {
            @Override
            public boolean isAlive()
            {
                return true;
            }

            @Override
            public void stop()
            {
                // nothing to stop
            }
        };
    }

    private static File jarIn(List<String> command)
    {
        return new File(command.get(command.indexOf("-jar") + 1));
    }

    private static JarUpdater.Mover rename()
    {
        return (from, to) -> Files.move(from, to);
    }

    private List<String> backups()
    {
        List<String> out = new ArrayList<>();

        for (String name : folder.list())
        {
            if (name.contains("_old")) out.add(name);
        }

        return out;
    }

    private static void buildJar(File file, String mainClass) throws Exception
    {
        Manifest manifest = new Manifest();

        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        if (mainClass != null) manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);

        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest))
        {
            for (int i = 0; i < 20; i++)
            {
                out.putNextEntry(new JarEntry("org/example/Part" + i + ".class"));

                byte[] filler = new byte[4096];

                new java.util.Random(i).nextBytes(filler);

                out.write(filler);
                out.closeEntry();
            }
        }
    }

    private static String sha256(File file) throws Exception
    {
        StringBuilder hex = new StringBuilder();

        for (byte b : MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())))
        {
            hex.append(String.format("%02x", b & 0xff));
        }

        return hex.toString();
    }

    private static void write(File file, String text) throws java.io.IOException
    {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file)
    {
        try
        {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        }
        catch (java.io.IOException e)
        {
            return "<" + e + ">";
        }
    }
}
