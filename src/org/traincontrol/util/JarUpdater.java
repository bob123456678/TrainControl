package org.traincontrol.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Swaps a new TrainControl jar in for the old one once TrainControl has closed, then starts it again (FR-125).
 *
 * Adam, 2026-10-11: *"one-click updates.  Downloads the new jar, makes a script to swap it with the current one in the
 * current folder (if it exists), and runs that script then exits so it can be done, then restarts."*  And 2026-10-12:
 * *"perhaps using java, since it would be on the system"*.  So the script is this class, run by the same Java the
 * application runs on, which works the same way on Windows, macOS and Linux where a .bat and a shell script would be
 * two programs to keep in step.
 *
 * **It never runs from the jar it replaces.**  On Windows a running jar is locked, so the application copies itself to
 * a temporary file and starts this class from the copy (`SelfUpdate.startUpdater`).
 *
 * **It waits for TrainControl to have closed, without asking for its process.**  Java 8 has no `ProcessHandle`, so the
 * application holds a lock on a file beside the jar from just before it starts this until its JVM ends, and this waits
 * until it can take that lock.  Past `LOCK_TIMEOUT_MS` it changes nothing, says why in the log and starts nothing:
 * TrainControl is then still open, and a second copy is the last thing wanted.
 *
 * **Then, in order:** the old jar is renamed to `<name>_v<old version>_old.jar` (never over an existing file: a number
 * is added instead), the new jar is moved to the old jar's exact name so shortcuts keep working, and it is started with
 * the same Java, the same Java options and the same arguments, in the working directory TrainControl had.  The old jar
 * is kept, so the update can be undone by hand (Adam, 2026-10-12).
 *
 * **Any failure puts the old jar back and starts it**, including a new version that stops at once with an error (a
 * release built for a newer Java, say).  Every step is written to `update.log` beside the jar, which is the only place
 * anybody can read what happened: there is no window while this runs.
 *
 * **Self-contained on purpose**: it uses nothing of TrainControl's, so nothing the application loads at start-up can
 * stop it part way through a swap.  For the same reason its log is in English rather than through the message bundles.
 *
 * The file work is in static methods that take paths, a mover and a launcher, which is how
 * `core.testTheUpdaterSwapsTheJar` runs every step without starting a process or touching a real jar.
 */
public final class JarUpdater
{
    /**
     * The log written beside the jar.
     */
    public static final String LOG_NAME = "update.log";

    /**
     * The file TrainControl holds a lock on until it has exited, beside the jar.
     */
    public static final String LOCK_NAME = "TrainControl-update.lock";

    /**
     * How long to wait for TrainControl to close: generous, because the exit saves the locomotive database, the
     * window state and the layout first, and a large layout on a slow disk takes time.
     */
    public static final long LOCK_TIMEOUT_MS = 5 * 60 * 1000L;

    /**
     * How often to try for the lock while waiting.
     */
    public static final long LOCK_POLL_MS = 250;

    /**
     * How long a freshly started version is watched.  One that ends with an error inside this time did not start, and
     * the old one goes back.
     *
     * Long enough for somebody to read and dismiss the Java launcher's own error window, which is what a jar built for
     * a newer Java than this one shows before it exits.
     */
    public static final long START_GRACE_MS = 60 * 1000L;

    /**
     * How many times a rename is tried before it counts as failed, and how long apart.  The lock is let go as the old
     * JVM ends, and Windows can take a moment longer to let go of the jar itself.
     */
    public static final int MOVE_ATTEMPTS = 20;
    public static final long MOVE_PAUSE_MS = 250;

    /**
     * What happened, for the caller and for the tests.
     */
    public enum Outcome
    {
        /** The new version is in place and was started. */
        UPDATED,
        /** TrainControl never closed, so nothing was changed and nothing was started. */
        GAVE_UP,
        /** Something failed: the old version was put back and started. */
        ROLLED_BACK
    }

    /**
     * Starts a command.
     */
    public interface Launcher
    {
        /**
         * @param command the program and its arguments
         * @param folder the working directory
         * @param graceMs how long to watch it
         * @return its exit code if it ended within graceMs, or null while it is still running
         * @throws IOException when it could not be started at all
         */
        Integer start(List<String> command, File folder, long graceMs) throws IOException;
    }

    /**
     * Renames a file.
     */
    public interface Mover
    {
        /**
         * @param from the file
         * @param to its new name, which must not exist
         * @throws IOException when it could not be moved
         */
        void move(Path from, Path to) throws IOException;
    }

    /**
     * Everything the updater is told, and how it travels on a command line.
     */
    public static final class Request
    {
        /** The jar TrainControl was started from: the one being replaced. */
        public final File jar;
        /** The downloaded jar, in the same folder. */
        public final File newJar;
        /** The file TrainControl holds a lock on until it has exited. */
        public final File lock;
        /** The version being replaced, for the old jar's new name. */
        public final String oldVersion;
        /** The working directory TrainControl had. */
        public final File workingDirectory;
        /** The Java to start it with. */
        public final String java;
        /** The Java options it was started with. */
        public final List<String> javaOptions;
        /** Its own command-line arguments. */
        public final List<String> arguments;

        public Request(File jar, File newJar, File lock, String oldVersion, File workingDirectory, String java,
            List<String> javaOptions, List<String> arguments)
        {
            this.jar = jar.getAbsoluteFile();
            this.newJar = newJar.getAbsoluteFile();
            this.lock = lock.getAbsoluteFile();
            this.oldVersion = oldVersion == null ? "" : oldVersion;
            this.workingDirectory = workingDirectory.getAbsoluteFile();
            this.java = java;
            this.javaOptions = Collections.unmodifiableList(new ArrayList<>(javaOptions));
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        }

        /**
         * As `main` reads them: six fixed values, the number of Java options, the options, then the arguments.
         *
         * @return the command-line form
         */
        public List<String> toArguments()
        {
            List<String> out = new ArrayList<>();

            out.add(this.jar.getPath());
            out.add(this.newJar.getPath());
            out.add(this.lock.getPath());
            out.add(this.oldVersion);
            out.add(this.workingDirectory.getPath());
            out.add(this.java);
            out.add(Integer.toString(this.javaOptions.size()));
            out.addAll(this.javaOptions);
            out.addAll(this.arguments);

            return out;
        }

        /**
         * The other half of `toArguments`.
         *
         * @param args the command line
         * @return the request
         * @throws IllegalArgumentException when the command line is not one `toArguments` wrote
         */
        public static Request parse(String[] args)
        {
            if (args == null || args.length < 7)
            {
                throw new IllegalArgumentException("expected at least 7 arguments, got "
                    + (args == null ? 0 : args.length));
            }

            int options = Integer.parseInt(args[6]);

            if (options < 0 || 7 + options > args.length)
            {
                throw new IllegalArgumentException("the option count " + options + " does not fit "
                    + args.length + " arguments");
            }

            return new Request(new File(args[0]), new File(args[1]), new File(args[2]), args[3], new File(args[4]),
                args[5], Arrays.asList(args).subList(7, 7 + options),
                Arrays.asList(args).subList(7 + options, args.length));
        }

        /**
         * The command that starts a jar the way TrainControl was started.
         *
         * @param which the jar
         * @return the command
         */
        public List<String> commandFor(File which)
        {
            List<String> out = new ArrayList<>();

            out.add(this.java);
            out.addAll(this.javaOptions);
            out.add("-jar");
            out.add(which.getAbsolutePath());
            out.addAll(this.arguments);

            return out;
        }
    }

    /**
     * A line-at-a-time log, appended to and closed after every line, so whatever happens next the lines so far are on
     * disk.
     */
    public static final class Log
    {
        private final File file;

        public Log(File file)
        {
            this.file = file;
        }

        /**
         * @return the file written to
         */
        public File getFile()
        {
            return this.file;
        }

        /**
         * Writes one line, with the time.  Never throws: a log that cannot be written must not stop the update.
         *
         * @param line what happened
         */
        public void say(String line)
        {
            String stamped = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "  " + line;

            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(this.file, true), StandardCharsets.UTF_8)))
            {
                out.println(stamped);
            }
            catch (IOException | RuntimeException e)
            {
                System.err.println(stamped);
            }
        }
    }

    /**
     * A swap that did not happen, and where it left the old jar.
     */
    public static final class SwapFailed extends IOException
    {
        /** Where the old jar is: its own name, unless putting it back failed too. */
        public final File oldIsAt;

        SwapFailed(String message, File oldIsAt, Throwable cause)
        {
            super(message, cause);

            this.oldIsAt = oldIsAt;
        }
    }

    private JarUpdater()
    {
    }

    /**
     * Runs the update `SelfUpdate.startUpdater` asked for.
     *
     * @param args as `Request.toArguments` wrote them
     */
    public static void main(String[] args)
    {
        Request request;

        try
        {
            request = Request.parse(args);
        }
        catch (RuntimeException e)
        {
            // Nowhere to write a log without the jar's folder, so the error stream is all there is.
            System.err.println("TrainControl updater: " + e.getMessage());

            return;
        }

        Log log = new Log(new File(request.jar.getParentFile(), LOG_NAME));

        try
        {
            update(request, LOCK_TIMEOUT_MS, LOCK_POLL_MS, MOVE_ATTEMPTS, MOVE_PAUSE_MS, START_GRACE_MS,
                JarUpdater::startProcess, JarUpdater::rename, log);
        }
        catch (RuntimeException e)
        {
            log.say("The updater stopped with an error: " + e);
        }
    }

    /**
     * The whole update: wait for TrainControl to close, swap the jars, start the new one, and put the old one back if
     * anything fails.
     *
     * @param request what to update
     * @param lockTimeoutMs how long to wait for TrainControl to close
     * @param lockPollMs how often to try for the lock
     * @param moveAttempts how many times a rename is tried
     * @param movePauseMs how long between tries
     * @param graceMs how long the started version is watched
     * @param launcher what starts a program
     * @param mover what renames a file
     * @param log where each step is written
     * @return what happened
     */
    public static Outcome update(Request request, long lockTimeoutMs, long lockPollMs, int moveAttempts,
        long movePauseMs, long graceMs, Launcher launcher, Mover mover, Log log)
    {
        log.say("Updating " + request.jar + " (v" + request.oldVersion + ") with " + request.newJar.getName());

        if (!waitForLock(request.lock, lockTimeoutMs, lockPollMs))
        {
            log.say("TrainControl did not close within " + (lockTimeoutMs / 1000) + " seconds, so nothing was"
                + " changed.  The new version is still at " + request.newJar);

            return Outcome.GAVE_UP;
        }

        log.say("TrainControl has closed");

        File backup;

        try
        {
            backup = swap(request.jar, request.newJar, request.oldVersion, mover, moveAttempts, movePauseMs, log);
        }
        catch (SwapFailed e)
        {
            log.say("The update failed: " + e.getMessage());

            startTheOld(request, e.oldIsAt, launcher, graceMs, log);

            request.lock.delete();

            return Outcome.ROLLED_BACK;
        }

        String failed;

        try
        {
            Integer ended = launcher.start(request.commandFor(request.jar), request.workingDirectory, graceMs);

            failed = ended == null || ended == 0 ? null : "the new version stopped at once with exit code " + ended;
        }
        catch (IOException e)
        {
            failed = "the new version could not be started: " + e.getMessage();
        }

        if (failed == null)
        {
            log.say("Started the new version.  The old one is kept as " + backup.getName());

            request.lock.delete();

            return Outcome.UPDATED;
        }

        log.say("The update failed: " + failed);

        File old = undo(request.jar, request.newJar, backup, mover, moveAttempts, movePauseMs, log);

        startTheOld(request, old, launcher, graceMs, log);

        request.lock.delete();

        return Outcome.ROLLED_BACK;
    }

    /**
     * Waits until the lock can be taken, which is when the process that held it has ended.
     *
     * A lock held by this same JVM throws rather than answering null, and counts as held: that is how a test holds it.
     *
     * @param lock the lock file
     * @param timeoutMs how long to wait
     * @param pollMs how often to try
     * @return true once it was taken and let go again; false on timeout
     */
    public static boolean waitForLock(File lock, long timeoutMs, long pollMs)
    {
        long until = System.currentTimeMillis() + timeoutMs;

        while (true)
        {
            try (RandomAccessFile file = new RandomAccessFile(lock, "rw");
                FileChannel channel = file.getChannel())
            {
                FileLock taken = channel.tryLock();

                if (taken != null)
                {
                    taken.release();

                    return true;
                }
            }
            catch (OverlappingFileLockException | IOException heldOrNotYetThere)
            {
                // Still held - by this JVM, or by another on a platform that says so with an error
            }

            if (System.currentTimeMillis() >= until) return false;

            try
            {
                Thread.sleep(pollMs);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return false;
            }
        }
    }

    /**
     * The name the old jar is kept under: `<name>_v<version>_old.jar` beside it, or with `_2`, `_3` and so on added
     * when that is taken.  Never a file that exists.
     *
     * A name that already ends in its version (`TrainControl_v3_0_1.jar`, which is what Download saves) does not get
     * it twice.
     *
     * @param jar the jar being replaced
     * @param version its version
     * @return a free name in the same folder
     */
    public static File backupName(File jar, String version)
    {
        File folder = jar.getAbsoluteFile().getParentFile();

        String name = jar.getName();
        String base = name.toLowerCase().endsWith(".jar") ? name.substring(0, name.length() - 4) : name;

        String tag = version == null ? "" : version.replaceAll("[^0-9A-Za-z]+", "_");
        String stem = tag.isEmpty() || base.endsWith("_v" + tag) ? base : base + "_v" + tag;

        File candidate = new File(folder, stem + "_old.jar");

        for (int n = 2; candidate.exists(); n++)
        {
            candidate = new File(folder, stem + "_old_" + n + ".jar");
        }

        return candidate;
    }

    /**
     * Renames the old jar to its backup name, then moves the new one to the old one's name.  When the second step
     * fails the first is undone, so a failure leaves the old jar where it was.
     *
     * @param jar the jar being replaced
     * @param newJar the downloaded one
     * @param version the version being replaced
     * @param mover what renames a file
     * @param attempts how many times each rename is tried
     * @param pauseMs how long between tries
     * @param log where each step is written
     * @return where the old jar now is
     * @throws SwapFailed when the swap failed, saying where the old jar is: back in its place unless the log says
     * otherwise
     */
    public static File swap(File jar, File newJar, String version, Mover mover, int attempts, long pauseMs, Log log)
        throws SwapFailed
    {
        if (!newJar.isFile()) throw new SwapFailed("the new version is not there: " + newJar, jar, null);

        if (!jar.isFile()) throw new SwapFailed("the jar to replace is not there: " + jar, jar, null);

        File backup = backupName(jar, version);

        try
        {
            move(mover, jar, backup, attempts, pauseMs);
        }
        catch (IOException e)
        {
            throw new SwapFailed(e.getMessage(), jar, e);
        }

        log.say("Kept the old version as " + backup.getName());

        try
        {
            move(mover, newJar, jar, attempts, pauseMs);
        }
        catch (IOException e)
        {
            File oldIsAt = jar;

            try
            {
                move(mover, backup, jar, attempts, pauseMs);

                log.say("Put the old version back as " + jar.getName());
            }
            catch (IOException cannotUndo)
            {
                log.say("COULD NOT put the old version back.  It is " + backup + ": rename it to " + jar.getName());

                oldIsAt = backup;
            }

            throw new SwapFailed(e.getMessage(), oldIsAt, e);
        }

        log.say("Moved the new version to " + jar.getName());

        return backup;
    }

    /**
     * Takes a swap back: the new jar returns to its downloaded name and the old one to its own.
     *
     * @return where the old jar is now
     */
    private static File undo(File jar, File newJar, File backup, Mover mover, int attempts, long pauseMs, Log log)
    {
        try
        {
            move(mover, jar, newJar, attempts, pauseMs);
            move(mover, backup, jar, attempts, pauseMs);

            log.say("Put the old version back as " + jar.getName() + "; the new one is at " + newJar.getName());

            return jar;
        }
        catch (IOException e)
        {
            log.say("COULD NOT put the old version back (" + e.getMessage() + ").  It is " + backup
                + ": rename it to " + jar.getName());

            return backup;
        }
    }

    /**
     * Starts the old version again, after a failure.
     */
    private static void startTheOld(Request request, File old, Launcher launcher, long graceMs, Log log)
    {
        try
        {
            Integer ended = launcher.start(request.commandFor(old), request.workingDirectory, graceMs);

            log.say(ended == null || ended == 0 ? "Started the old version again from " + old.getName()
                : "The old version stopped too, with exit code " + ended);
        }
        catch (IOException e)
        {
            log.say("Could not start the old version either: " + e.getMessage());
        }
    }

    /**
     * One rename, tried up to `attempts` times.
     */
    private static void move(Mover mover, File from, File to, int attempts, long pauseMs) throws IOException
    {
        if (to.exists()) throw new IOException(to + " is already there, and is not overwritten");

        IOException last = null;

        for (int attempt = 0; attempt < Math.max(1, attempts); attempt++)
        {
            try
            {
                mover.move(from.toPath(), to.toPath());

                return;
            }
            catch (IOException e)
            {
                last = e;
            }

            try
            {
                Thread.sleep(pauseMs);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                break;
            }
        }

        throw new IOException("could not move " + from.getName() + " to " + to.getName() + ": " + last, last);
    }

    /**
     * The real rename.  No REPLACE_EXISTING: the target never exists, and if it somehow did it must not be lost.
     */
    private static void rename(Path from, Path to) throws IOException
    {
        Files.move(from, to);
    }

    /**
     * The real launcher.  Its output goes nowhere, as it would for a jar started by a double click: this JVM exits
     * soon, and a pipe with no reader would stop the new version the first time it wrote to it.
     */
    private static Integer startProcess(List<String> command, File folder, long graceMs) throws IOException
    {
        File nowhere = new File(File.separatorChar == '\\' ? "NUL" : "/dev/null");

        Process started = new ProcessBuilder(command)
            .directory(folder)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(nowhere))
            .redirectInput(ProcessBuilder.Redirect.from(nowhere))
            .start();

        try
        {
            return started.waitFor(graceMs, TimeUnit.MILLISECONDS) ? started.exitValue() : null;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();

            return null;
        }
    }
}
