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
 * **"The current folder" is the jar's own folder, not the working directory.**  A shortcut can start TrainControl
 * anywhere, and the jar to replace is the one `TrainControlUI.class` was loaded from.  Run from an IDE or
 * `build/classes` that is a folder, and there is nothing to replace.
 */
public final class SelfUpdate
{
    /** Where the updater's copies of the jar are made, beside the jar, by name, so a later start can tidy them. */
    public static final String UPDATER_COPY_PREFIX = "TrainControl-updater-";

    /** How long the exit waits for the updater to say it is running before it gives up and stays open. */
    public static final long UPDATER_WAIT_MS = 15000;

    /** TrainControl's own command-line arguments, as `main` was given them. */
    private static volatile List<String> arguments = Collections.emptyList();

    /**
     * The exit lock, its channel and its file, held from just before the updater starts until this JVM ends.  Never
     * closed on the way out: the operating system lets go as the process ends, which is the signal the updater waits
     * for.
     */
    private static FileChannel exitChannel;
    private static FileLock exitLock;
    private static File exitLockFile;

    /** The updater, once started. */
    public interface Updater
    {
        /** Whether it is still running. */
        boolean isAlive();

        /**
         * Ends it, when it never said it was running: left alone it could take the lock later and swap the jar under an
         * open TrainControl.
         */
        void stop();
    }

    /** Starts the updater and does not wait for it. */
    public interface ProcessStarter
    {
        /** Starts the program; IOException when it could not be started. */
        Updater start(List<String> command, File folder) throws IOException;
    }

    /** The update the exit is to start: the jar to replace, the download to replace it with, and its version. */
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
     * One Download & Update at a time, from its question to the exit (FR-125).  `begin` refuses a second; `exitWith`
     * arms the update, runs the exit, and disarms it in a `finally`, so an exit that throws cannot leave an update
     * armed for a later, ordinary exit.  The exit takes it with `take`, once, after everything has agreed to close.
     */
    public static final class HandOver
    {
        private final AtomicBoolean underWay = new AtomicBoolean();

        private Pending armed;

        /** Returns false when an update is already under way, which refuses this one. */
        public boolean begin()
        {
            return this.underWay.compareAndSet(false, true);
        }

        /** Ends an update that will not reach the exit. */
        public synchronized void end()
        {
            this.armed = null;
            this.underWay.set(false);
        }

        /** Whether an update is under way. */
        public boolean isUnderWay()
        {
            return this.underWay.get();
        }

        /** The exit's half: the update to start, taken so that it is started once; null when none is armed. */
        public synchronized Pending take()
        {
            Pending taken = this.armed;

            this.armed = null;

            return taken;
        }

        /**
         * Arms the update, runs the exit, and disarms it whatever the exit did; true when the exit came back without
         * taking it (canceled).
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
     * Tells an update that is waiting to hear from this version that it has started (FR-125).  Called once the main
     * window is on screen, and when the user leaves by canceling the IP address or giving up the first sync: a version
     * that asks a question runs.  Not earlier, or a version that fails in its own start-up would be kept.
     *
     * Only when `JarUpdater.STARTED_ENVIRONMENT` names a file.  Never throws: nothing may stop TrainControl starting.
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

    /** `reportStarted` with the marker's path given (null or blank for none), which is how a claim calls it. */
    public static void reportStarted(String marker)
    {
        if (marker != null && !marker.trim().isEmpty()) JarUpdater.writeMarker(new File(marker.trim()));
    }

    /** Remembers TrainControl's command-line arguments, so an update starts the new version with the same ones. */
    public static void rememberArguments(String[] args)
    {
        arguments = args == null ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<>(Arrays.asList(args)));
    }

    /** TrainControl's command-line arguments. */
    public static List<String> getArguments()
    {
        return arguments;
    }

    /**
     * TEST HOOK, with no effect unless a test sets it: the jar `runningJar` reports in place of the one the program was
     * loaded from, because a test runs from a folder of classes with no jar (FR-136 Phase 6; Adam: "OK on download
     * update test hooks").
     */
    private static volatile File runningJarForTests;

    /**
     * For tests: makes `runningJar` report this throwaway jar, or the real answer for null.  Reset it with null in a
     * finally.
     */
    public static void runningJarForTests(File jar)
    {
        runningJarForTests = jar;
    }

    /**
     * The jar this class was loaded from, or null when running from a folder of classes or from a jar whose folder
     * cannot be written.
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
     * Whether a code source is a jar an update can replace: a regular `.jar` file whose folder can be written, since
     * the new jar is downloaded beside it and the old one is kept beside it.  Under Program Files or on a read-only
     * share the question offers Download alone.
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
     * Where a downloaded version goes: `TrainControl_v<version>.jar`, beside the running jar when there is one, else in
     * the working directory.  Never the running jar itself (a jar renamed by hand to the new version's name): a
     * download over it cannot be written, and an update from it renames the only copy away.
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
     * Whether a download is a whole TrainControl jar, before anything is replaced with it: null when it is, else what
     * is wrong, to show.  The release's size and digest (`sha256:<hex>`) are checked where it gives them; the jar check
     * catches what they cannot, a download cut short (the zip's directory is written last) or an error page saved in
     * its place.
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

    /** A file's SHA-256, in lower-case hex. */
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
     * The Java this JVM runs on: `javaw.exe` on Windows, so the updater and the restart open no console window, else
     * `java`.
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
     * The Java options this JVM was started with (a forced language, a memory size), so the restarted version has them
     * too, less a debugger or agent attached to this run: a debugger waiting on a port would hold the restarted
     * TrainControl.
     */
    public static List<String> javaOptions()
    {
        return javaOptions(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    /** `javaOptions` of a given list, which is how a claim asks it. */
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
     * JVM has ended; and waits until the updater says it is running.  Called from the exit itself, once everything is
     * saved and nothing can refuse the exit, so a canceled exit never leaves an updater waiting.
     *
     * **The exit goes on only once the updater has said it is running** (`JarUpdater.RUNNING_NAME`, its first act):
     * `ProcessBuilder.start` returning proves nothing, since a Java that cannot run the copy ends at once.  An updater
     * that ends without saying so, or says nothing within `waitMs`, is ended, the lock is let go and the copy removed,
     * and this throws: TrainControl stays open.
     *
     * The copy goes beside the jar rather than in the temporary folder: on Windows, Java 8 passes a command line in the
     * system's code page, and a user folder named outside it cannot be passed.  A second call while the lock is held is
     * refused, or it would let go of the first one's lock and that updater would swap the jar under a running
     * TrainControl.
     *
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

    /** Whether the updater wrote its running marker before it ended or the wait ran out. */
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
     * The real starter: the updater's output goes nowhere, and the started-marker variable, which belongs to the start
     * that set it, is removed.
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

    /** Whether this JVM holds the exit lock: true from when the updater was started until the JVM ends. */
    public static synchronized boolean isHoldingTheExitLock()
    {
        return exitLock != null && exitLock.isValid();
    }

    /** Lets go of the exit lock, for a test that took it through `startUpdater` and is not about to end its JVM. */
    public static synchronized void releaseTheExitLockForTests()
    {
        letGoOfTheExitLock();
    }

    /**
     * Reads and removes the note a rolled-back update left beside the jar, for this start to show once (FR-125).
     * Answers {the version that could not start, the old version, and null when the old jar is back under its own name
     * or else the file it is running from}, or null when there is no note.
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
     * Removes the copies earlier updates left beside the jar; one still in use stays until the next time, since the
     * updater cannot remove its own copy.
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
