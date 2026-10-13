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
 * jar aside, moves the new one to its name, starts it, and puts the old one back on any failure.  The application's
 * half is `SelfUpdate`: whether a download is whole, and starting the updater from a copy of the jar.
 *
 * **Nothing here starts a process, ends the JVM or touches a real jar.**  Every jar is built in a temporary folder;
 * the updater's renames and its launcher are handed in, so a launch is a recorded command and a failed rename is one
 * the test throws.  The lock TrainControl holds until it exits is held by this test instead, in this JVM - which
 * `waitForLock` sees as held, as it sees another process's.
 *
 * MUTATIONS, each seen red: `backupName` not looking for a free name (the swap claim); `swap` not moving the backup
 * back when the second rename fails (the rollback claim); `update` ignoring a non-zero exit code (the failed-start
 * claim); `waitForLock` returning true at once (the lock, timeout and copy claims); `whyNotAWholeJar` skipping the jar
 * check (the truncated and HTML claim); `startUpdater` putting the jar itself on the updater's classpath, or keeping
 * the lock when its starter fails (the copy claim); `Request.parse` dropping the Java options (the command-line
 * claim); `downloadTarget` returning the running jar's own name (the download claim).
 */
public class testTheUpdaterSwapsTheJar
{
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

        JarUpdater.Outcome outcome = JarUpdater.update(request(), 5000, 10, 1, 0, 1000,
            recording(started, null), failsTheSecond, log);

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
     * A new version that stops at once with an error, such as one built for a newer Java, is taken back out: the old
     * jar returns to its name and is started.
     *
     * @throws Exception from the files
     */
    @Test
    public void testANewVersionThatStopsAtOnceIsTakenBack() throws Exception
    {
        List<String> started = new ArrayList<>();

        // The new one ends at once with 1; the old one, started after, keeps running
        JarUpdater.Outcome outcome = JarUpdater.update(request(), 5000, 10, 1, 0, 1000,
            recording(started, 1), rename(), log);

        assertEquals(outcome, JarUpdater.Outcome.ROLLED_BACK);

        assertEquals(started, Arrays.asList("new", "old"), "the new version failed to start and the old one was not"
            + " put back and started");

        assertEquals(read(jar), "old");
        assertEquals(read(download), "new", "the failed download should be back under its own name");
    }

    /**
     * The updater waits while TrainControl holds its lock, changes nothing meanwhile, and swaps and starts the new
     * version once the lock is let go - with the same Java, options, arguments and working directory.
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

            updater = new Thread(() -> outcome.set(JarUpdater.update(request, 30000, 20, 1, 0, 1000,
                (command, where, grace) ->
                {
                    commands.add(command);
                    folders.add(where);
                    started.add(read(new File(command.get(command.indexOf("-jar") + 1))));

                    return null;
                }, rename(), log)));

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

            outcome = JarUpdater.update(request, 300, 20, 1, 0, 1000, recording(started, null), rename(),
                log);

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
        assertEquals(got.oldVersion, "3.0.1");
        assertEquals(got.workingDirectory, sent.workingDirectory);
        assertEquals(got.java, sent.java);
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
     * The updater runs from a copy of the jar, never the jar it replaces (a running jar is locked on Windows), with the
     * Java this JVM runs on - and the exit lock is held from then on.  A starter that fails lets go of the lock and
     * leaves no copy behind.
     *
     * @throws Exception from the files
     */
    @Test
    public void testTheUpdaterRunsFromACopyWithTheSameJava() throws Exception
    {
        buildJar(jar, "TrainControl");

        SelfUpdate.rememberArguments(new String[] {"192.168.1.5", "debug"});

        final List<List<String>> asked = new ArrayList<>();

        File copy = null;

        try
        {
            SelfUpdate.startUpdater(jar, download, "3.0.1", (command, where) -> asked.add(command));

            assertEquals(asked.size(), 1, "the updater was not started");

            List<String> command = asked.get(0);

            assertEquals(command.get(0), SelfUpdate.javaExecutable());
            assertTrue(command.get(0).startsWith(new File(System.getProperty("java.home")).getPath()),
                "the updater is not run by the Java TrainControl runs on: " + command.get(0));

            assertEquals(command.get(1), "-cp");

            copy = new File(command.get(2));

            assertNotEquals(copy.getAbsoluteFile(), jar.getAbsoluteFile(), "the updater runs from the jar it replaces,"
                + " which Windows keeps locked while it runs");
            assertTrue(Arrays.equals(Files.readAllBytes(copy.toPath()), Files.readAllBytes(jar.toPath())),
                "the updater's copy is not a copy of the jar");

            assertEquals(command.get(3), JarUpdater.class.getName());

            JarUpdater.Request request = JarUpdater.Request.parse(
                command.subList(4, command.size()).toArray(new String[0]));

            assertEquals(request.jar, jar.getAbsoluteFile());
            assertEquals(request.newJar, download.getAbsoluteFile());
            assertEquals(request.arguments, Arrays.asList("192.168.1.5", "debug"), "the new version would start"
                + " without TrainControl's own arguments");
            assertEquals(request.lock.getParentFile(), folder.getAbsoluteFile());

            assertTrue(SelfUpdate.isHoldingTheExitLock(), "the exit lock is not held, so the updater would not wait");
            assertFalse(JarUpdater.waitForLock(request.lock, 0, 10), "the updater could take the lock while"
                + " TrainControl is still running");
        }
        finally
        {
            SelfUpdate.releaseTheExitLockForTests();

            if (copy != null) copy.delete();
        }

        try
        {
            SelfUpdate.startUpdater(jar, download, "3.0.1", (command, where) ->
            {
                asked.add(command);

                throw new java.io.IOException("refused by the test");
            });

            fail("a starter that failed was not reported");
        }
        catch (java.io.IOException expected)
        {
            // as it should
        }

        assertFalse(SelfUpdate.isHoldingTheExitLock(), "the exit lock is still held after the updater failed to"
            + " start, so a later update would wait on it");
        assertFalse(new File(asked.get(1).get(2)).exists(), "the updater's copy was left behind");
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

    // ---------------------------------------------------------------------------------------------

    private JarUpdater.Request request()
    {
        return new JarUpdater.Request(jar, download, new File(folder, JarUpdater.LOCK_NAME), "3.0.1",
            new File(folder, "working"), "C:/the/same/javaw.exe", Collections.singletonList("-Duser.language=de"),
            Arrays.asList("192.168.1.5", "debug"));
    }

    /**
     * A launcher that records what the jar it was asked to start held, and answers with an exit code the first time.
     */
    private static JarUpdater.Launcher recording(final List<String> started, final Integer firstExit)
    {
        return (command, where, grace) ->
        {
            started.add(read(new File(command.get(command.indexOf("-jar") + 1))));

            return started.size() == 1 ? firstExit : null;
        };
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
