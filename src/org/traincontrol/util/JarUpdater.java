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
 * a file beside the jar and starts this class from the copy (`SelfUpdate.startUpdater`).  The first thing `main` does
 * is write `RUNNING_NAME` beside the jar, which is how the application knows this started before it exits.
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
 * **"Started" means the new version said so, not that it ran for a while.**  It is given the path of a marker file in
 * the `STARTED_ENVIRONMENT` environment variable, and writes that file once its main window is on screen - or when the
 * user leaves it from a question before then (the IP address canceled, the first sync given up), which is a version
 * that runs (`SelfUpdate.reportStarted`).  So a version that fails in its own start-up, before its window, is put
 * back, whatever its exit code; one that is up and then exits stays.  An exit code could not tell those apart:
 * TrainControl ends with 1 on purpose in several places.  An environment variable rather than a -D option, because the
 * Java options are carried into every later restart and the marker belongs to this one.  One that says nothing within
 * `START_CAP_MS` is kept, and the log says so: a version still starting is not killed on a guess.  The Readme says
 * the same (FR-125, 2026-10-13).
 *
 * **A failure puts the old jar back and starts it**, and leaves `ROLLBACK_NOTE_NAME` beside the jar for the old version
 * to say so on its next start.  The note says the old version was put back only when its jar really is back under its
 * own name; when putting it back stopped half way, the note says where it is running from instead (FR-125, 2026-10-13).  Every
 * step is written to `update.log` beside the jar.
 *
 * **Self-contained on purpose**: it uses nothing of TrainControl's, so nothing the application loads at start-up can
 * stop it part way through a swap.  For the same reason its log is in English; the one sentence it shows, in a small
 * window while it waits and swaps, is handed to it already translated.
 *
 * The file work is in static methods that take paths, a mover and a launcher, which is how
 * `core.testTheUpdaterSwapsTheJar` runs every step without starting TrainControl or touching a real jar.
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
     * The file this writes beside the jar as soon as it is running, for the application to see before it exits.
     */
    public static final String RUNNING_NAME = "TrainControl-updater.running";

    /**
     * The file the new version writes beside the jar as soon as it is running.
     */
    public static final String STARTED_NAME = "TrainControl-update.started";

    /**
     * The environment variable that tells a starting TrainControl where to write `STARTED_NAME`.
     */
    public static final String STARTED_ENVIRONMENT = "TRAINCONTROL_UPDATE_STARTED";

    /**
     * The note a rollback leaves beside the jar for the old version to read on its next start: `to=` the version that
     * could not start, `from=` the old one, and `at=` the file the old one is running from when it could not be put
     * back under its own name.
     */
    public static final String ROLLBACK_NOTE_NAME = "TrainControl-update-failed.txt";

    /**
     * How long to wait for TrainControl to close: generous, because the exit saves the locomotive database, the
     * window state and the layout first, and a large layout on a slow disk takes time.
     */
    public static final long LOCK_TIMEOUT_MS = 5 * 60 * 1000L;

    /**
     * How often to look, while waiting for the lock or for the new version to say it has started.
     */
    public static final long POLL_MS = 250;

    /**
     * How long the new version has to say it has started.  Long, because the Java launcher's own error window, which
     * is what a jar built for a newer Java shows, waits for somebody to read it; past this the new version is kept.
     */
    public static final long START_CAP_MS = 10 * 60 * 1000L;

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
        /** The new version is in place and said it had started. */
        UPDATED,
        /** The new version is in place and still running, but did not say it had started within the cap: kept. */
        UNCONFIRMED,
        /** TrainControl never closed, so nothing was changed and nothing was started. */
        GAVE_UP,
        /** Something failed: the old version was put back and started. */
        ROLLED_BACK
    }

    /**
     * How a started version answered.
     */
    public enum Start
    {
        /** It wrote the marker. */
        STARTED,
        /** It ended without writing the marker. */
        ENDED,
        /** It is still running and has not written the marker. */
        NO_WORD
    }

    /**
     * A program that was started.
     */
    public interface Started
    {
        /**
         * @return whether it is still running
         */
        boolean isAlive();
    }

    /**
     * Starts a command.
     */
    public interface Launcher
    {
        /**
         * @param command the program and its arguments
         * @param folder the working directory
         * @param startedMarker the file a starting TrainControl is to write, passed in `STARTED_ENVIRONMENT`; null
         * for none, and then the variable is removed
         * @return the program
         * @throws IOException when it could not be started at all
         */
        Started start(List<String> command, File folder, File startedMarker) throws IOException;
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
        /** How many values come before the Java options' count. */
        private static final int FIXED = 9;

        /** The jar TrainControl was started from: the one being replaced. */
        public final File jar;
        /** The downloaded jar, in the same folder. */
        public final File newJar;
        /** The file TrainControl holds a lock on until it has exited. */
        public final File lock;
        /** The file this writes as soon as it is running. */
        public final File running;
        /** The version being replaced, for the old jar's new name. */
        public final String oldVersion;
        /** The version being installed, for the rollback note. */
        public final String newVersion;
        /** The working directory TrainControl had. */
        public final File workingDirectory;
        /** The Java to start it with. */
        public final String java;
        /** What the small window says while this waits and swaps, already translated. */
        public final String windowText;
        /** The Java options it was started with. */
        public final List<String> javaOptions;
        /** Its own command-line arguments. */
        public final List<String> arguments;

        public Request(File jar, File newJar, File lock, File running, String oldVersion, String newVersion,
            File workingDirectory, String java, String windowText, List<String> javaOptions, List<String> arguments)
        {
            this.jar = jar.getAbsoluteFile();
            this.newJar = newJar.getAbsoluteFile();
            this.lock = lock.getAbsoluteFile();
            this.running = running.getAbsoluteFile();
            this.oldVersion = nonEmpty(oldVersion);
            this.newVersion = nonEmpty(newVersion);
            this.workingDirectory = workingDirectory.getAbsoluteFile();
            this.java = java;
            this.windowText = nonEmpty(windowText);
            this.javaOptions = Collections.unmodifiableList(new ArrayList<>(javaOptions));
            this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        }

        /**
         * An empty argument does not survive a Windows command line under Java 8 - it is dropped, and every value
         * after it would move up one place - so none is ever written.
         */
        private static String nonEmpty(String value)
        {
            return value == null || value.isEmpty() ? "?" : value;
        }

        /**
         * As `main` reads them: the fixed values, the number of Java options, the options, then the arguments.
         *
         * @return the command-line form
         */
        public List<String> toArguments()
        {
            List<String> out = new ArrayList<>();

            out.add(this.jar.getPath());
            out.add(this.newJar.getPath());
            out.add(this.lock.getPath());
            out.add(this.running.getPath());
            out.add(this.oldVersion);
            out.add(this.newVersion);
            out.add(this.workingDirectory.getPath());
            out.add(this.java);
            out.add(this.windowText);
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
            if (args == null || args.length < FIXED + 1)
            {
                throw new IllegalArgumentException("expected at least " + (FIXED + 1) + " arguments, got "
                    + (args == null ? 0 : args.length));
            }

            int options = Integer.parseInt(args[FIXED]);

            int first = FIXED + 1;

            if (options < 0 || first + options > args.length)
            {
                throw new IllegalArgumentException("the option count " + options + " does not fit "
                    + args.length + " arguments");
            }

            return new Request(new File(args[0]), new File(args[1]), new File(args[2]), new File(args[3]), args[4],
                args[5], new File(args[6]), args[7], args[8], Arrays.asList(args).subList(first, first + options),
                Arrays.asList(args).subList(first + options, args.length));
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
            // Nowhere to write a log without the jar's folder, so the error stream is all there is.  No running marker
            // either, so TrainControl stays open and says the updater did not start.
            System.err.println("TrainControl updater: " + e.getMessage());

            return;
        }

        // FIRST, so TrainControl knows this is running before it exits (it waits a few seconds for this file)
        writeMarker(request.running);

        Log log = new Log(new File(request.jar.getParentFile(), LOG_NAME));

        final javax.swing.JWindow[] window = {showTheWindow(request.windowText)};

        try
        {
            update(request, LOCK_TIMEOUT_MS, POLL_MS, MOVE_ATTEMPTS, MOVE_PAUSE_MS, START_CAP_MS,
                JarUpdater::startProcess, JarUpdater::rename, log, () ->
                {
                    closeTheWindow(window[0]);

                    window[0] = null;
                });
        }
        catch (RuntimeException | Error e)
        {
            log.say("The updater stopped with an error: " + e);
        }
        finally
        {
            closeTheWindow(window[0]);
        }

        // The window's event thread would otherwise keep this JVM alive; what it started is a process of its own
        System.exit(0);
    }

    /**
     * The whole update: wait for TrainControl to close, swap the jars, start the new one, and put the old one back if
     * it does not start.
     *
     * @param request what to update
     * @param lockTimeoutMs how long to wait for TrainControl to close
     * @param pollMs how often to look, for the lock and for the started marker
     * @param moveAttempts how many times a rename is tried
     * @param movePauseMs how long between tries
     * @param startCapMs how long the new version has to say it has started
     * @param launcher what starts a program
     * @param mover what renames a file
     * @param log where each step is written
     * @param launched run once a version has been launched, new or old, so the window can close; may be null
     * @return what happened
     */
    public static Outcome update(Request request, long lockTimeoutMs, long pollMs, int moveAttempts, long movePauseMs,
        long startCapMs, Launcher launcher, Mover mover, Log log, Runnable launched)
    {
        log.say("Updating " + request.jar + " (v" + request.oldVersion + ") to v" + request.newVersion + " from "
            + request.newJar.getName());

        if (!waitForLock(request.lock, lockTimeoutMs, pollMs))
        {
            log.say("TrainControl did not close within " + (lockTimeoutMs / 1000) + " seconds, so nothing was"
                + " changed.  The new version is still at " + request.newJar);

            // As tidy as it can be: where TrainControl still holds the file, the delete simply fails
            request.lock.delete();

            return Outcome.GAVE_UP;
        }

        log.say("TrainControl has closed");

        request.lock.delete();

        File backup;

        try
        {
            backup = swap(request.jar, request.newJar, request.oldVersion, mover, moveAttempts, movePauseMs, log);
        }
        catch (SwapFailed e)
        {
            log.say("The update failed: " + e.getMessage());

            return rollBack(request, e.oldIsAt, launcher, log, launched);
        }

        File started = new File(request.jar.getParentFile(), STARTED_NAME);

        started.delete();

        Started process;

        try
        {
            process = launcher.start(request.commandFor(request.jar), request.workingDirectory, started);
        }
        catch (IOException e)
        {
            log.say("The update failed: the new version could not be started: " + e.getMessage());

            return rollBack(request, undo(request.jar, request.newJar, backup, mover, moveAttempts, movePauseMs, log),
                launcher, log, launched);
        }

        if (launched != null) launched.run();

        // A VERSION OLDER THAN THE MARKER CANNOT SAY IT STARTED (MT-737, Adam 2026-10-13: "after the newly updated app
        // opens, is used by the user, and is closed, the old version is put back and opened, with a message saying that
        // the update failed").  Waiting for its word took every exit within the cap for a failed start, so a user who
        // closed it was put back.  It is kept as soon as it runs, as a version that says nothing by the cap is.
        if (!marksItsStart(request.newVersion))
        {
            started.delete();

            log.say("The new version is running.  It is older than " + FIRST_TO_MARK_ITS_START + " and cannot say it has"
                + " started, so it was kept.  The old one is kept as " + backup.getName());

            return Outcome.UNCONFIRMED;
        }

        switch (waitForTheStart(started, process, startCapMs, pollMs))
        {
            case STARTED:
                started.delete();

                log.say("The new version has started.  The old one is kept as " + backup.getName());

                return Outcome.UPDATED;

            case NO_WORD:
                log.say("The new version is running but did not say it had started within " + (startCapMs / 1000)
                    + " seconds, so it was kept.  The old one is kept as " + backup.getName());

                return Outcome.UNCONFIRMED;

            default:
                log.say("The update failed: the new version ended before it had started");

                return rollBack(request,
                    undo(request.jar, request.newJar, backup, mover, moveAttempts, movePauseMs, log),
                    launcher, log, launched);
        }
    }

    /** The first release that writes the started marker. */
    public static final String FIRST_TO_MARK_ITS_START = "3.0.1";

    /**
     * Whether a version writes the started marker: 3.0.1 or later, compared number by number.  A version that cannot
     * be read is taken to write it, which is what every release this updater can be handed does.
     *
     * @param version the version, as "3.0.1"
     * @return whether to wait for its word
     */
    public static boolean marksItsStart(String version)
    {
        String[] have = version == null ? new String[0] : version.trim().split("\\.");
        String[] first = FIRST_TO_MARK_ITS_START.split("\\.");

        try
        {
            for (int i = 0; i < Math.max(have.length, first.length); i++)
            {
                int a = i < have.length ? Integer.parseInt(have[i].replaceAll("[^0-9].*$", "")) : 0;
                int b = i < first.length ? Integer.parseInt(first[i]) : 0;

                if (a != b) return a > b;
            }

            return true;
        }
        catch (NumberFormatException unreadable)
        {
            return true;
        }
    }

    /**
     * Waits for a started version to write its marker, or to end without it, or for the cap.
     *
     * The marker is looked for once more after the process has ended: one that wrote it and quit at once (the IP
     * question canceled) has started.
     *
     * @param marker the file it is to write
     * @param process the started version
     * @param capMs how long to wait
     * @param pollMs how often to look
     * @return how it answered
     */
    public static Start waitForTheStart(File marker, Started process, long capMs, long pollMs)
    {
        long until = System.currentTimeMillis() + capMs;

        while (true)
        {
            if (marker.isFile()) return Start.STARTED;

            if (!process.isAlive()) return marker.isFile() ? Start.STARTED : Start.ENDED;

            if (System.currentTimeMillis() >= until) return Start.NO_WORD;

            try
            {
                Thread.sleep(pollMs);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                return Start.NO_WORD;
            }
        }
    }

    /**
     * After a failure: leaves the note the old version reads on its next start, and starts it.
     *
     * The note says the old version is back only when its jar is back under its own name - the name a shortcut
     * starts.  Otherwise it says which file the old version is running from, so it is not told it was put back while
     * the shortcut's name still holds the version that did not start (FR-125, 2026-10-13).
     */
    private static Outcome rollBack(Request request, File old, Launcher launcher, Log log, Runnable launched)
    {
        File note = new File(request.jar.getParentFile(), ROLLBACK_NOTE_NAME);

        boolean putBack = old.getAbsoluteFile().equals(request.jar) && request.jar.isFile();

        try
        {
            Files.write(note.toPath(), ("to=" + request.newVersion + "\nfrom=" + request.oldVersion + "\n"
                + (putBack ? "" : "at=" + old.getName() + "\n")).getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e)
        {
            log.say("Could not leave a note for the old version: " + e.getMessage());
        }

        try
        {
            launcher.start(request.commandFor(old), request.workingDirectory, null);

            log.say("Started the old version again from " + old.getName());
        }
        catch (IOException e)
        {
            log.say("Could not start the old version either: " + e.getMessage());
        }

        if (launched != null) launched.run();

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

            return backup.isFile() ? backup : jar;
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
     * Writes a marker file.  Never throws: a marker that cannot be written is a start nobody hears about, which the
     * waiting side already handles.
     *
     * @param marker the file
     */
    public static void writeMarker(File marker)
    {
        try
        {
            Files.write(marker.toPath(), "started".getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException | RuntimeException e)
        {
            System.err.println("TrainControl updater: could not write " + marker + ": " + e);
        }
    }

    /**
     * The real rename.  No REPLACE_EXISTING: the target never exists, and if it somehow did it must not be lost.
     */
    private static void rename(Path from, Path to) throws IOException
    {
        Files.move(from, to);
    }

    /**
     * The real launcher, which `main` hands to `update`.  Its output goes nowhere, as it would for a jar started by a
     * double click: this JVM exits soon, and a pipe with no reader would stop the new version the first time it wrote
     * to it.  Public so `regression.testTheUpdateIsWiredEndToEnd` can start a plain JVM through it.
     *
     * @param command the program and its arguments
     * @param folder the working directory
     * @param startedMarker the file a starting TrainControl is to write, or null for none
     * @return the started program
     * @throws IOException when it could not be started
     */
    public static Started startProcess(List<String> command, File folder, File startedMarker) throws IOException
    {
        final Process process = startDetached(command, folder, startedMarker);

        return process::isAlive;
    }

    /**
     * Starts a program whose input and output are nowhere, for a starter whose own JVM exits soon after.
     *
     * @param command the program and its arguments
     * @param folder the working directory
     * @param startedMarker the file a starting TrainControl is to write, or null for none (`prepareEnvironment`)
     * @return the started program
     * @throws IOException when it could not be started
     */
    public static Process startDetached(List<String> command, File folder, File startedMarker) throws IOException
    {
        File nowhere = new File(File.separatorChar == '\\' ? "NUL" : "/dev/null");

        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(folder)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(nowhere))
            .redirectInput(ProcessBuilder.Redirect.from(nowhere));

        prepareEnvironment(builder.environment(), startedMarker);

        return builder.start();
    }

    /**
     * Sets `STARTED_ENVIRONMENT` to the marker for the start that is to write it, and removes it for every other: an
     * inherited one names a marker that belongs to an earlier start.
     *
     * @param environment the environment of a program about to be started
     * @param startedMarker the marker, or null for none
     */
    public static void prepareEnvironment(java.util.Map<String, String> environment, File startedMarker)
    {
        if (startedMarker == null)
        {
            environment.remove(STARTED_ENVIRONMENT);
        }
        else
        {
            environment.put(STARTED_ENVIRONMENT, startedMarker.getAbsolutePath());
        }
    }

    /**
     * A small undecorated window saying the update is under way, while TrainControl's own window is gone and the new
     * one has not appeared (FR-125).  None on a headless system, and none if Swing cannot make one: the window is a
     * courtesy, and nothing about the update may depend on it.
     *
     * @param text what it says
     * @return the window, or null
     */
    private static javax.swing.JWindow showTheWindow(final String text)
    {
        if (java.awt.GraphicsEnvironment.isHeadless()) return null;

        try
        {
            final javax.swing.JWindow[] made = new javax.swing.JWindow[1];

            javax.swing.SwingUtilities.invokeAndWait(() ->
            {
                javax.swing.JLabel label = new javax.swing.JLabel(text);

                label.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createLineBorder(java.awt.Color.GRAY),
                    javax.swing.BorderFactory.createEmptyBorder(16, 28, 16, 28)));

                javax.swing.JWindow window = new javax.swing.JWindow();

                window.getContentPane().add(label);
                window.pack();
                window.setLocationRelativeTo(null);
                window.setVisible(true);

                made[0] = window;
            });

            return made[0];
        }
        catch (Exception | Error e)
        {
            return null;
        }
    }

    /**
     * Closes the window, if there is one.
     */
    private static void closeTheWindow(final javax.swing.JWindow window)
    {
        if (window == null) return;

        try
        {
            javax.swing.SwingUtilities.invokeLater(window::dispose);
        }
        catch (RuntimeException | Error e)
        {
            // A courtesy that could not be taken back; the process ends anyway
        }
    }
}
