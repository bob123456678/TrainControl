package org.traincontrol.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * The application's half of a one-click update (FR-125): which jar is running, whether a download is whole, and
 * starting `JarUpdater` so that it can replace the jar once TrainControl has closed.
 *
 * Adam, 2026-10-12: *"after downloading the new jar, it should take a backup, then start the update script and close
 * TrainControl.  If no jar in current folder, it shouldn't be offered as a menu option."*
 *
 * **"The current folder" is the jar's own folder, not the working directory.**  A shortcut can start TrainControl
 * anywhere, and the jar to replace is the one it was loaded from - `TrainControlUI.class`'s code source.  Run from an
 * IDE or `build/classes` that is a folder, and there is nothing to replace.
 */
public final class SelfUpdate
{
    /**
     * Where the updater's copies of the jar are made, beside the jar, by name, so a later start can tidy them.
     */
    public static final String UPDATER_COPY_PREFIX = "TrainControl-updater-";

    /**
     * How long the exit waits for the updater to say it is running before it gives up and stays open.
     */
    public static final long UPDATER_WAIT_MS = 15000;

    /**
     * TrainControl's own command-line arguments, as `main` was given them.
     */
    private static volatile List<String> arguments = Collections.emptyList();

    /**
     * The exit lock, its channel and its file, held from just before the updater starts until this JVM ends.  Never
     * closed on the way out: the operating system lets go as the process ends, and that is the signal the updater waits
     * for.
     */
    private static FileChannel exitChannel;
    private static FileLock exitLock;
    private static File exitLockFile;

    /**
     * The updater, once started.
     */
    public interface Updater
    {
        /**
         * @return whether it is still running
         */
        boolean isAlive();

        /**
         * Ends it, when it never said it was running: left alone it could take the lock later and swap the jar under
         * a TrainControl that stayed open.
         */
        void stop();
    }

    /**
     * Starts the updater and does not wait for it.
     */
    public interface ProcessStarter
    {
        /**
         * @param command the program and its arguments
         * @param folder its working directory
         * @return the started program
         * @throws IOException when it could not be started
         */
        Updater start(List<String> command, File folder) throws IOException;
    }

    /**
     * The update the exit is to start: the jar to replace, the download to replace it with, and its version.
     */
    public static final class Pending
    {
        public final File jar;
        public final File download;
        public final String version;

        public Pending(File jar, File download, String version)
        {
            this.jar = jar;
            this.download = download;
            this.version = version;
        }
    }

    /**
     * One Download & Update at a time, from its question to the exit (FR-125).
     *
     * `begin` refuses a second while one is under way, so two are never stacked; `exitWith` arms the update, runs the
     * exit, and disarms it in a `finally` - so an exit that throws cannot leave an update armed for a later, ordinary
     * exit to start.  The exit takes it with `take`, once, after everything has agreed to close.
     */
    public static final class HandOver
    {
        private final AtomicBoolean underWay = new AtomicBoolean();

        private Pending armed;

        /**
         * @return false when an update is already under way, which refuses this one
         */
        public boolean begin()
        {
            return this.underWay.compareAndSet(false, true);
        }

        /**
         * Ends an update that will not reach the exit.
         */
        public synchronized void end()
        {
            this.armed = null;
            this.underWay.set(false);
        }

        /**
         * @return whether an update is under way
         */
        public boolean isUnderWay()
        {
            return this.underWay.get();
        }

        /**
         * The exit's half: the update to start, taken so that it is started once.
         *
         * @return the update, or null when none is armed
         */
        public synchronized Pending take()
        {
            Pending taken = this.armed;

            this.armed = null;

            return taken;
        }

        /**
         * Arms the update, runs the exit, and disarms it whatever the exit did.
         *
         * @param pending the update
         * @param exit the application's exit, which takes the update when nothing refuses it
         * @return true when the exit came back without taking it: it was cancelled
         */
        public boolean exitWith(Pending pending, Runnable exit)
        {
            synchronized (this)
            {
                this.armed = pending;
            }

            boolean cancelled = true;

            try
            {
                exit.run();
            }
            finally
            {
                synchronized (this)
                {
                    cancelled = this.armed != null;

                    this.armed = null;
                }

                this.underWay.set(false);
            }

            return cancelled;
        }
    }

    private SelfUpdate()
    {
    }

    /**
     * Tells an update that is waiting to hear from this version that it has started (FR-125).
     *
     * Called once the main window is on screen, and on the two ways the user leaves before then - cancelling the IP
     * address, giving up the first sync - because a version that asks a question runs.  Not as `main`'s first act any
     * more (FR-125, 2026-10-13): a version that fails in its own start-up after that had "started" and was kept, while the
     * Readme promised an update that cannot start is undone.
     *
     * Only when `JarUpdater.STARTED_ENVIRONMENT` names a file, which the updater sets for the version it starts and for
     * nothing else.  Never throws: nothing may stop TrainControl starting.
     */
    public static void reportStarted()
    {
        try
        {
            reportStarted(System.getenv(JarUpdater.STARTED_ENVIRONMENT));
        }
        catch (RuntimeException | Error e)
        {
            // An update that never hears keeps the new version after its cap, which is the safe side
        }
    }

    /**
     * `reportStarted` with the marker's path given, which is how a claim calls it without an environment of its own.
     *
     * @param marker the marker's path, or null or blank for none
     */
    public static void reportStarted(String marker)
    {
        if (marker != null && !marker.trim().isEmpty()) JarUpdater.writeMarker(new File(marker.trim()));
    }

    /**
     * Remembers TrainControl's command-line arguments (an IP address, debug, simulate), so an update starts the new
     * version with the same ones.
     *
     * @param args as `main` was given them
     */
    public static void rememberArguments(String[] args)
    {
        arguments = args == null ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(Arrays.asList(args)));
    }

    /**
     * @return TrainControl's command-line arguments
     */
    public static List<String> getArguments()
    {
        return arguments;
    }

    /**
     * TEST HOOK, with no effect unless a test sets it: the jar `runningJar` reports in place of the one the program was
     * loaded from (FR-136 Phase 6; Adam, 2026-10-19: "OK on download update test hooks").  A test runs from a folder of
     * classes, where there is no jar, so Download & Update could not be reached from one.  Null, as it always is outside
     * a test, asks the code source as before.
     */
    private static volatile File runningJarForTests;

    /**
     * For tests: makes `runningJar` report this jar, a throwaway one in the test's own folder, so that Download & Update
     * is offered and replaces that rather than anything real.  Reset it with null in a finally.
     *
     * @param jar the jar to pretend the program runs from, or null for the real answer
     */
    public static void runningJarForTests(File jar)
    {
        runningJarForTests = jar;
    }

    /**
     * The jar this class was loaded from, if it is one an update can replace.
     *
     * @param anchor a class of the application, whose code source is the jar
     * @return the jar, or null when running from a folder of classes or from a jar whose folder cannot be written
     */
    public static File runningJar(Class<?> anchor)
    {
        final File pretend = runningJarForTests;

        if (pretend != null) return pretend;

        try
        {
            CodeSource source = anchor.getProtectionDomain().getCodeSource();

            return jarAt(source == null ? null : source.getLocation());
        }
        catch (SecurityException e)
        {
            return null;
        }
    }

    /**
     * Whether a code source is a jar an update can replace.
     *
     * Only a regular `.jar` file counts, and only where its folder can be written: the new jar is downloaded beside it
     * and renamed into its place, and the old one is kept beside it too.  A jar under Program Files, or on a share
     * the user can only read, is a jar this cannot update, and the question then offers Download alone.
     *
     * @param location the code source's location
     * @return the jar, or null
     */
    public static File jarAt(URL location)
    {
        if (location == null) return null;

        File file;

        try
        {
            // Through Paths rather than new File(URI), which refuses a jar on a network share
            file = Paths.get(location.toURI()).toFile().getAbsoluteFile();
        }
        catch (Exception notAFile)
        {
            return null;
        }

        if (!file.isFile() || !file.getName().toLowerCase().endsWith(".jar")) return null;

        File folder = file.getParentFile();

        if (folder == null || !Files.isWritable(folder.toPath())) return null;

        return file;
    }

    /**
     * Where a downloaded version goes: `TrainControl_v<version>.jar`, beside the running jar when there is one, and in
     * the working directory (as it always was) when there is not.
     *
     * Never the running jar itself, which a jar renamed by hand to the new version's name would otherwise be: a
     * download over it is a file that cannot be written, and an update from it renames the only copy away.
     *
     * @param jar the running jar, or null
     * @param version the new version
     * @return the file to download to
     */
    public static File downloadTarget(File jar, String version)
    {
        String name = "TrainControl_v" + version.replace(".", "_");

        if (jar == null) return new File(name + ".jar");

        File target = new File(jar.getParentFile(), name + ".jar");

        return target.getAbsoluteFile().equals(jar.getAbsoluteFile())
            ? new File(jar.getParentFile(), name + "_new.jar") : target;
    }

    /**
     * Whether a download is a whole TrainControl jar, before anything is replaced with it.
     *
     * The size and checksum come from the release, where it gives them.  The jar check catches what they cannot:
     * a download cut short has lost the zip's directory, which is written last, and an error page saved in its place
     * is not a zip at all.  Either way it would not start, and the old version would have been renamed away for it.
     *
     * @param file the download
     * @param expectedSize the release's size in bytes, or zero or less when unknown
     * @param digest the release's digest, `sha256:<hex>`, or null when unknown
     * @return null when it is whole, or what is wrong with it, to show
     */
    public static String whyNotAWholeJar(File file, long expectedSize, String digest)
    {
        if (!file.isFile()) return I18n.t("ui.update.notWholeNotAJar");

        if (expectedSize > 0 && file.length() != expectedSize)
        {
            return I18n.f("ui.update.notWholeSize", file.length(), expectedSize);
        }

        if (digest != null && digest.trim().toLowerCase().startsWith("sha256:"))
        {
            String wanted = digest.trim().substring("sha256:".length()).trim();

            try
            {
                if (!sha256Of(file).equalsIgnoreCase(wanted)) return I18n.t("ui.update.notWholeChecksum");
            }
            catch (IOException e)
            {
                return I18n.t("ui.update.notWholeNotAJar");
            }
        }

        try (JarFile jar = new JarFile(file))
        {
            Manifest manifest = jar.getManifest();

            String main = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);

            if (main == null || main.trim().isEmpty()) return I18n.t("ui.update.notWholeNotAJar");
        }
        catch (IOException | SecurityException e)
        {
            return I18n.t("ui.update.notWholeNotAJar");
        }

        return null;
    }

    /**
     * A file's SHA-256, in lower-case hex.
     */
    private static String sha256Of(File file) throws IOException
    {
        MessageDigest digest;

        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IOException(e);
        }

        try (InputStream in = Files.newInputStream(file.toPath()))
        {
            byte[] buffer = new byte[65536];

            for (int read = in.read(buffer); read != -1; read = in.read(buffer))
            {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder hex = new StringBuilder();

        for (byte b : digest.digest()) hex.append(String.format("%02x", b & 0xff));

        return hex.toString();
    }

    /**
     * The Java this JVM runs on: `javaw.exe` on Windows, so that the updater and the restarted TrainControl open no
     * console window, and `java` elsewhere.
     *
     * @return its path
     */
    public static String javaExecutable()
    {
        File bin = new File(System.getProperty("java.home"), "bin");

        if (File.separatorChar == '\\')
        {
            File windowed = new File(bin, "javaw.exe");

            return windowed.isFile() ? windowed.getPath() : new File(bin, "java.exe").getPath();
        }

        return new File(bin, "java").getPath();
    }

    /**
     * The Java options this JVM was started with, such as a forced language (`-Duser.language=de`) or a memory size,
     * so the restarted version has them too.
     *
     * Less a debugger or agent attached to this run, which belongs to whoever attached it: a debugger waiting on a
     * port would hold the restarted TrainControl until somebody connected to it.
     *
     * @return the options
     */
    public static List<String> javaOptions()
    {
        return javaOptions(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    /**
     * `javaOptions` of a given list, which is how a claim asks it.
     *
     * @param given the options a JVM was started with
     * @return those to carry into a restart
     */
    public static List<String> javaOptions(List<String> given)
    {
        List<String> out = new ArrayList<>();

        for (String option : given)
        {
            if (option.startsWith("-agentlib:") || option.startsWith("-agentpath:")
                || option.startsWith("-javaagent:") || option.startsWith("-Xrunjdwp") || option.equals("-Xdebug"))
            {
                continue;
            }

            out.add(option);
        }

        return out;
    }

    /**
     * Takes the exit lock and starts `JarUpdater`, from a copy of the jar beside it, to swap in the new one once this
     * JVM has ended; and waits until the updater says it is running.
     *
     * Called from the exit itself, once everything has been saved and nothing can refuse the exit any more - so a
     * cancelled exit never leaves an updater waiting.  The lock is held from here until the process ends, and the
     * updater waits until it can take it.
     *
     * **The exit goes on only once the updater has said it is running** (`JarUpdater.RUNNING_NAME`, its first act).
     * `ProcessBuilder.start` returning proves nothing: a Java that cannot run the copy ends at once, and TrainControl
     * would have closed for an update that never happens.  An updater that ends without saying so, or says nothing
     * within `waitMs`, is ended, the lock is let go and the copy removed, and this throws - TrainControl stays open.
     *
     * The copy goes beside the jar, which is writable by definition, rather than in the temporary folder: on Windows,
     * Java 8 passes a command line in the system's code page, and a user folder named outside it cannot be passed.
     *
     * A second call while the lock is held is refused rather than stacked: it would otherwise let go of the first
     * one's lock, and that updater would swap the jar under a running TrainControl.
     *
     * @param jar the running jar
     * @param newJar the checked download, beside it
     * @param oldVersion the running version
     * @param newVersion the version being installed
     * @param windowText what the updater's small window says, translated
     * @param starter what starts the updater
     * @param waitMs how long to wait for the updater to say it is running
     * @throws IOException when the updater could not be started, or did not say it was running
     */
    public static synchronized void startUpdater(File jar, File newJar, String oldVersion, String newVersion,
        String windowText, ProcessStarter starter, long waitMs) throws IOException
    {
        if (exitLock != null) throw new IOException(I18n.t("ui.update.alreadyUnderWay"));

        File folder = jar.getAbsoluteFile().getParentFile();
        File lock = new File(folder, JarUpdater.LOCK_NAME);
        File running = new File(folder, JarUpdater.RUNNING_NAME);

        holdTheExitLock(lock);

        File copy = null;

        try
        {
            tidyOldCopies(folder);

            running.delete();

            copy = File.createTempFile(UPDATER_COPY_PREFIX, ".jar", folder);

            Files.copy(jar.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);

            JarUpdater.Request request = new JarUpdater.Request(jar, newJar, lock, running, oldVersion, newVersion,
                new File(System.getProperty("user.dir")), javaExecutable(), windowText, javaOptions(), getArguments());

            List<String> command = new ArrayList<>();

            command.add(javaExecutable());
            command.add("-cp");
            command.add(copy.getAbsolutePath());
            command.add(JarUpdater.class.getName());
            command.addAll(request.toArguments());

            Updater updater = starter.start(command, folder);

            if (!heardFrom(running, updater, waitMs))
            {
                updater.stop();

                throw new IOException(I18n.f("ui.update.updaterSilent", waitMs / 1000));
            }

            running.delete();
        }
        catch (IOException | RuntimeException | Error e)
        {
            letGoOfTheExitLock();

            if (copy != null) copy.delete();

            running.delete();

            throw e;
        }
    }

    /**
     * Whether the updater wrote its running marker before it ended or the wait ran out.
     */
    private static boolean heardFrom(File running, Updater updater, long waitMs)
    {
        long until = System.currentTimeMillis() + waitMs;

        while (true)
        {
            if (running.isFile()) return true;

            if (!updater.isAlive()) return running.isFile();

            if (System.currentTimeMillis() >= until) return false;

            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return running.isFile();
            }
        }
    }

    /**
     * The real starter: the updater's output goes nowhere, because nothing will be left to read it.  Without the
     * started-marker variable, which belongs to the start that set it.
     *
     * @param command the program and its arguments
     * @param folder its working directory
     * @return the started updater
     * @throws IOException when it could not be started
     */
    public static Updater startDetached(List<String> command, File folder) throws IOException
    {
        final Process process = JarUpdater.startDetached(command, folder, null);

        return new Updater()
        {
            @Override
            public boolean isAlive()
            {
                return process.isAlive();
            }

            @Override
            public void stop()
            {
                process.destroy();
            }
        };
    }

    /**
     * Whether this JVM holds the exit lock.
     *
     * @return true from when the updater was started until the JVM ends
     */
    public static synchronized boolean isHoldingTheExitLock()
    {
        return exitLock != null && exitLock.isValid();
    }

    /**
     * Lets go of the exit lock, for a test that took it through `startUpdater` and is not about to end its JVM.
     */
    public static synchronized void releaseTheExitLockForTests()
    {
        letGoOfTheExitLock();
    }

    /**
     * Reads and removes the note a rolled-back update left beside the jar, for this start to show once (FR-125).
     *
     * @param folder the jar's folder
     * @return {the version that could not start, the old version, and null when the old jar is back under its own name
     * or else the file it is running from}, or null when there is no note
     */
    public static String[] takeRollbackNote(File folder)
    {
        File note = new File(folder, JarUpdater.ROLLBACK_NOTE_NAME);

        if (!note.isFile()) return null;

        String to = "?";
        String from = "?";
        String at = null;

        try
        {
            for (String line : Files.readAllLines(note.toPath(), StandardCharsets.UTF_8))
            {
                if (line.startsWith("to=")) to = line.substring(3).trim();
                if (line.startsWith("from=")) from = line.substring(5).trim();
                if (line.startsWith("at=")) at = line.substring(3).trim();
            }
        }
        catch (IOException | RuntimeException e)
        {
            // Still worth saying, with what could be read
        }

        // Shown once: a note that cannot be removed would say the same thing at every start
        if (!note.delete()) return null;

        return new String[] {to, from, at};
    }

    /**
     * Removes the copies earlier updates left beside the jar.  The updater runs from its copy, so it cannot remove that
     * itself; one that is still in use simply stays until the next time.
     *
     * @param folder the jar's folder
     */
    public static void tidyOldCopies(File folder)
    {
        File[] old = folder.listFiles((dir, name) -> name.startsWith(UPDATER_COPY_PREFIX) && name.endsWith(".jar"));

        if (old == null) return;

        for (File each : old) each.delete();
    }

    private static void holdTheExitLock(File lock) throws IOException
    {
        RandomAccessFile file = new RandomAccessFile(lock, "rw");

        FileLock taken;

        try
        {
            taken = file.getChannel().tryLock();
        }
        catch (IOException | RuntimeException e)
        {
            file.close();

            throw e;
        }

        if (taken == null)
        {
            file.close();

            throw new IOException(lock + " is held by another program");
        }

        exitChannel = file.getChannel();
        exitLock = taken;
        exitLockFile = lock;
    }

    private static void letGoOfTheExitLock()
    {
        try
        {
            if (exitLock != null) exitLock.release();
        }
        catch (IOException ignored)
        {
            // Closing the channel below lets go of it anyway
        }

        try
        {
            if (exitChannel != null) exitChannel.close();
        }
        catch (IOException ignored)
        {
            // Nothing else holds it; the process ending will
        }

        if (exitLockFile != null) exitLockFile.delete();

        exitLock = null;
        exitChannel = null;
        exitLockFile = null;
    }
}
