package org.traincontrol.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
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
     * Where the updater's temporary copies of the jar are made, by name, so the next update can tidy the last one's.
     */
    public static final String UPDATER_COPY_PREFIX = "TrainControl-updater-";

    /**
     * TrainControl's own command-line arguments, as `main` was given them.
     */
    private static volatile List<String> arguments = Collections.emptyList();

    /**
     * The exit lock and its channel, held from just before the updater starts until this JVM ends.  Never closed:
     * the operating system lets go of both as the process ends, and that is the signal the updater waits for.
     */
    private static FileChannel exitChannel;
    private static FileLock exitLock;

    /**
     * Starts a program and does not wait for it.
     */
    public interface ProcessStarter
    {
        /**
         * @param command the program and its arguments
         * @param folder its working directory
         * @throws IOException when it could not be started
         */
        void start(List<String> command, File folder) throws IOException;
    }

    private SelfUpdate()
    {
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
     * The jar this class was loaded from, if it is one an update can replace.
     *
     * @param anchor a class of the application, whose code source is the jar
     * @return the jar, or null when running from a folder of classes or from a jar whose folder cannot be written
     */
    public static File runningJar(Class<?> anchor)
    {
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
        List<String> out = new ArrayList<>();

        for (String option : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments())
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
     * Takes the exit lock and starts `JarUpdater`, from a temporary copy of the jar, to swap in the new one once this
     * JVM has ended.
     *
     * Called from the exit itself, once everything has been saved and nothing can refuse the exit any more - so a
     * cancelled exit never leaves an updater waiting.  The lock is held from here until the process ends, and the
     * updater waits until it can take it.
     *
     * On a failure the lock is let go again and the copy removed, and nothing has been changed.
     *
     * @param jar the running jar
     * @param newJar the checked download, beside it
     * @param oldVersion the running version
     * @param starter what starts the updater
     * @throws IOException when the updater could not be started
     */
    public static synchronized void startUpdater(File jar, File newJar, String oldVersion, ProcessStarter starter)
        throws IOException
    {
        File folder = jar.getAbsoluteFile().getParentFile();
        File lock = new File(folder, JarUpdater.LOCK_NAME);

        holdTheExitLock(lock);

        File copy = null;

        try
        {
            tidyOldCopies();

            copy = File.createTempFile(UPDATER_COPY_PREFIX, ".jar");

            Files.copy(jar.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING);

            JarUpdater.Request request = new JarUpdater.Request(jar, newJar, lock, oldVersion,
                new File(System.getProperty("user.dir")), javaExecutable(), javaOptions(), getArguments());

            List<String> command = new ArrayList<>();

            command.add(javaExecutable());
            command.add("-cp");
            command.add(copy.getAbsolutePath());
            command.add(JarUpdater.class.getName());
            command.addAll(request.toArguments());

            starter.start(command, folder);
        }
        catch (IOException | RuntimeException e)
        {
            letGoOfTheExitLock();

            if (copy != null) copy.delete();

            throw e;
        }
    }

    /**
     * The real starter: the updater's output goes nowhere, because nothing will be left to read it.
     *
     * @param command the program and its arguments
     * @param folder its working directory
     * @throws IOException when it could not be started
     */
    public static void startDetached(List<String> command, File folder) throws IOException
    {
        File nowhere = new File(File.separatorChar == '\\' ? "NUL" : "/dev/null");

        new ProcessBuilder(command)
            .directory(folder)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(nowhere))
            .redirectInput(ProcessBuilder.Redirect.from(nowhere))
            .start();
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

    private static void holdTheExitLock(File lock) throws IOException
    {
        letGoOfTheExitLock();

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

        exitLock = null;
        exitChannel = null;
    }

    /**
     * Removes the copies earlier updates left in the temporary folder.  The updater runs from its copy, so it cannot
     * remove that itself; one that is still in use simply stays.
     */
    private static void tidyOldCopies()
    {
        File[] old = new File(System.getProperty("java.io.tmpdir")).listFiles(
            (dir, name) -> name.startsWith(UPDATER_COPY_PREFIX) && name.endsWith(".jar"));

        if (old == null) return;

        for (File each : old) each.delete();
    }
}
