package org.traincontrol.util;

import java.awt.Desktop;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import org.json.JSONObject;
import org.traincontrol.marklin.file.CS2File;

/**
 * Helper functions
 */
public class Util
{
    /**
     * An empty JSONObject that keeps its keys in insertion order (the library's own map is unordered).
     * @return the object
     * @throws NoSuchFieldException if the library no longer has a "map" field
     * @throws IllegalAccessException if the field cannot be opened
     */
    public static JSONObject orderedJSON() throws NoSuchFieldException, IllegalAccessException
    {
        JSONObject jsonObj = new JSONObject();
        Field map = jsonObj.getClass().getDeclaredField("map");
        map.setAccessible(true);
        map.set(jsonObj, new LinkedHashMap<>());
        map.setAccessible(false);
        return jsonObj;
    }

    /**
     * Opens a URL in a browser
     * @param url
     * @return 
     */
    public static boolean openUrl(String url)
    {
        if(Desktop.isDesktopSupported())
        {
            Desktop desktop = Desktop.getDesktop();

            try
            {
                desktop.browse(new URI(url));
            }
            catch (IOException | URISyntaxException e) {return false;}
        }
        else
        {
            Runtime runtime = Runtime.getRuntime();

            try
            {
                runtime.exec("xdg-open " + url);
            }
            catch (IOException e) {return false;}
        }  
        
        return true;
    }
    
    /**
     * Extension of the temporary file used while a download is in progress
     */
    public static final String PARTIAL_DOWNLOAD_SUFFIX = ".part";

    /**
     * Folder (relative to the working directory) that timestamped backup files are written to.
     */
    public static final String BACKUP_FOLDER = "tc_backup";

    /** The name prefix of a data file's backup, made when the file would not read and is about to be written over. */
    public static final String UNREADABLE_BACKUP_PREFIX = "unreadable";

    /**
     * Where cropped locomotive icons are written (FR-022): beside the application's data, never next to or over the
     * user's own photo.
     */
    public static final String LOC_ICON_FOLDER = "tc_loc_icons";

    /** The system property a test run names its own data folder with; see `dataPath`. */
    public static final String DATA_DIR_PROPERTY = "traincontrol.dataDir";

    /** The system property a test run names its own preference node with; see `preferencesFor`. */
    public static final String PREFERENCES_PROPERTY = "traincontrol.preferences";

    /** Where a test run's own preference nodes live, one per run, under the user root. */
    public static final String ISOLATED_PREFERENCES_ROOT = "traincontrol-test-runs";

    /**
     * Where one of the application's data files is: the working directory, unless a test run named a folder of its own
     * in `traincontrol.dataDir`, which keeps a running application's files out of the tests.
     *
     * @param name the file's name - `LocDB.data`, `UIState.data`, the backup folder
     * @return its path
     */
    public static String dataPath(String name)
    {
        String folder = System.getProperty(DATA_DIR_PROPERTY);

        if (folder == null || folder.trim().isEmpty()) return name;

        return new File(folder.trim(), name).getPath();
    }

    /**
     * The preference node for a class's package: the user node, unless a test run named a node of its own in
     * `traincontrol.preferences`.  That node starts as a copy of the real one, which is only ever read, and a run that
     * cannot build it does not fall back to the real one.
     *
     * @param owner a class in the package
     * @return the node
     */
    public static java.util.prefs.Preferences preferencesFor(Class<?> owner)
    {
        java.util.prefs.Preferences real = java.util.prefs.Preferences.userNodeForPackage(owner);

        String run = System.getProperty(PREFERENCES_PROPERTY);

        if (run == null || run.trim().isEmpty()) return real;

        String path = ISOLATED_PREFERENCES_ROOT + "/" + run.trim() + real.absolutePath();

        try
        {
            java.util.prefs.Preferences root = java.util.prefs.Preferences.userRoot();

            boolean fresh = !root.nodeExists(path);

            java.util.prefs.Preferences isolated = root.node(path);

            if (fresh)
            {
                for (String key : real.keys())
                {
                    String value = real.get(key, null);

                    if (value != null) isolated.put(key, value);
                }

                isolated.flush();
            }

            return isolated;
        }
        catch (java.util.prefs.BackingStoreException | IllegalArgumentException | IllegalStateException cannot)
        {
            throw new IllegalStateException("could not build this run's own preference node " + path, cannot);
        }
    }

    /**
     * Returns the path a backup file should be written to.  Backups go into a dedicated subfolder
     * ({@link #BACKUP_FOLDER}), which is created if it does not exist.  If the folder cannot be
     * created, the original file name is returned so the backup falls back to the current directory.
     * @param fileName the intended backup file name
     * @return the path to write the backup to
     */
    public static String getBackupPath(String fileName)
    {
        File dir = new File(dataPath(BACKUP_FOLDER));

        if (dir.isDirectory() || dir.mkdirs())
        {
            return new File(dir, fileName).getPath();
        }

        // Folder unavailable - fall back to the current directory (the original behavior)
        return fileName;
    }

    /**
     * The name an unreadable data file is kept under, beside the backups, before a save writes over it:
     * {@link #UNREADABLE_BACKUP_PREFIX}, the time to the second (colons and the space made safe for a file name), then the
     * file's own name.
     * @param timeMillis the time the copy is made
     * @param fileName the data file's own name
     * @return the name to hand {@link #getBackupPath}
     */
    public static String unreadableCopyName(long timeMillis, String fileName)
    {
        return UNREADABLE_BACKUP_PREFIX + Conversion.convertSecondsToDatetime(timeMillis).replace(':', '-')
            .replace(' ', '_') + fileName;
    }

    /**
     * Copies a data file that would not read aside, under {@link #unreadableCopyName}, before a save writes over it.
     *
     * @param fileName the data file's own name
     * @return the copy, or null when there is no file to keep
     * @throws IOException when the copy could not be made
     */
    public static File keepUnreadableCopy(String fileName) throws IOException
    {
        File existing = new File(dataPath(fileName));

        if (!existing.exists()) return null;

        File kept = new File(getBackupPath(unreadableCopyName(System.currentTimeMillis(), fileName)));

        Files.copy(existing.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return kept;
    }

    /** The folder cropped locomotive icons live in: beside the data files, so a test run's stays its own. */
    public static File locIconFolder()
    {
        return new File(dataPath(LOC_ICON_FOLDER));
    }

    /**
     * Returns the file a cropped locomotive icon should be written to, creating {@link #LOC_ICON_FOLDER} if needed.
     * Unlike getBackupPath there is no fall back to the working directory: an icon among the application's own data
     * files would never be cleaned up.
     *
     * @param fileName the intended file name, already sanitized
     * @return the file to write to, or null if the folder could not be created
     */
    public static File getLocIconFile(String fileName)
    {
        File dir = locIconFolder();

        if (dir.isDirectory() || dir.mkdirs())
        {
            return new File(dir, fileName);
        }

        return null;
    }

    /**
     * Whether a URL points at a file this application wrote into {@link #LOC_ICON_FOLDER}: the one question that
     * decides whether an icon file may be deleted.  Everything outside that folder is the user's own picture.
     *
     * @param url the locomotive's local image URL, may be null
     * @return true only if the URL resolves to a file inside the icon folder
     */
    public static boolean isLocIconFile(String url)
    {
        if (url == null) return false;

        try
        {
            File file = new File(new URI(url));

            File dir = locIconFolder().getCanonicalFile();

            // Canonical, and the parent rather than the path: a name that walks out of the folder ("..") cannot pass.
            File parent = file.getCanonicalFile().getParentFile();

            return parent != null && parent.equals(dir);
        }
        catch (URISyntaxException | IllegalArgumentException | IOException e)
        {
            // Not a file URL, or unreadable: anything not positively ours is the user's, the safe answer for a delete
            // gate.
            return false;
        }
    }

    /**
     * Removes a file, or a folder and everything under it.  A link (a symbolic link, or a junction on Windows) is
     * removed itself, never what it points at, so no link can loop it.  It goes no more than 32 folders down, so
     * a deeper tree is not removed whole.  Never throws: a caller tidying up must not fail over it.
     *
     * @param file what to remove
     * @param depth how deep `file` is in the tree being removed; 0 for its root
     * @return whether everything asked for is gone
     */
    public static boolean deleteTree(File file, int depth)
    {
        if (file == null || depth > 32) return false;

        try
        {
            if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return false;

            if (file.isDirectory() && !isLink(file))
            {
                File[] children = file.listFiles();

                if (children != null)
                {
                    for (File child : children) deleteTree(child, depth + 1);
                }
            }

            return file.delete();
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    /**
     * Whether a path is a symbolic link or a Windows junction, which `File.isDirectory` answers true for.
     *
     * @param file the path
     * @return true for a link, and for a path whose kind cannot be read
     */
    public static boolean isLink(File file)
    {
        try
        {
            java.nio.file.attribute.BasicFileAttributes kind = Files.readAttributes(file.toPath(),
                java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            return kind.isSymbolicLink() || kind.isOther();
        }
        catch (IOException | RuntimeException e)
        {
            return true;
        }
    }

    /**
     * For tests: told what `showInFileManager` would do ("open <folder>" or "select <file>"); null for the real one.
     */
    private static volatile java.util.function.Consumer<String> fileManagerForTests;

    /**
     * @param recorder what `showInFileManager` tells instead of starting a file manager, or null for the real one
     */
    public static void fileManagerForTests(java.util.function.Consumer<String> recorder)
    {
        fileManagerForTests = recorder;
    }

    /**
     * Opens the operating system's file manager on the folder a file is in (FR-019).  The folder itself, not the file
     * selected in it (OB-311): Explorer's /select, falls back to Documents, silently, on a path it cannot resolve.
     * Never throws: failing to open a file manager must not turn a successful backup into an error dialog.
     *
     * @param file the file whose folder to open, or null to open the folder given
     * @param folder the folder to fall back to
     */
    public static void showInFileManager(File file, String folder)
    {
        try
        {
            File open = file != null && file.isFile() ? file.getAbsoluteFile().getParentFile()
                : (folder == null ? null : new File(folder).getAbsoluteFile());

            if (open == null || !open.isDirectory()) return;

            if (fileManagerForTests != null)
            {
                fileManagerForTests.accept("open " + open.getAbsolutePath());

                return;
            }

            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(open);
        }
        catch (java.io.IOException | RuntimeException e)
        {
            // Silent beyond the log: the backup succeeded, and a second dialog would be noise.
        }
    }

    /**
     * Writes a set of files and folders into one zip (FR-015).  One archive rather than a folder of copies: the
     * autonomy setup is keyed by page id, defined by `config/gleisbild.cs2`, so the pieces mean nothing apart and only
     * an archive cannot be half-copied or half-restored.  A source that does not exist is skipped, not an error: a
     * layout held on the Central Station has no local config folder, and a first run has no UI state.
     *
     * @param zip where to write
     * @param sources entry name inside the archive -> file or folder on disk
     * @return what could not be added, empty when everything was
     */
    public static List<String> zipInto(File zip, java.util.Map<String, File> sources)
    {
        List<String> failed = new ArrayList<>();

        if (zip == null || sources == null) return failed;

        File parent = zip.getAbsoluteFile().getParentFile();

        if (parent != null) parent.mkdirs();

        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(
            new java.io.BufferedOutputStream(new java.io.FileOutputStream(zip))))
        {
            for (java.util.Map.Entry<String, File> source : sources.entrySet())
            {
                if (source.getValue() == null || !source.getValue().exists()) continue;

                addToZip(out, source.getValue(), source.getKey(), failed, 0);
            }
        }
        catch (IOException | RuntimeException e)
        {
            failed.add(zip.getName() + ": " + e);
        }

        return failed;
    }

    /** One file, or one folder and everything under it; `depth` guards against a symlink loop. */
    private static void addToZip(java.util.zip.ZipOutputStream out, File source, String name,
        List<String> failed, int depth)
    {
        if (depth > 32) return;

        try
        {
            if (source.isDirectory())
            {
                File[] children = source.listFiles();

                if (children == null) return;

                for (File child : children)
                {
                    addToZip(out, child, name + "/" + child.getName(), failed, depth + 1);
                }

                return;
            }

            out.putNextEntry(new java.util.zip.ZipEntry(name));

            java.nio.file.Files.copy(source.toPath(), out);

            out.closeEntry();
        }
        catch (IOException | RuntimeException e)
        {
            // Named individually: a file held open by a sync client is the everyday case, and the user can act on it.
            failed.add(name + ": " + e);
        }
    }

    /**
     * Copies a folder and everything under it into the backup area, best effort per file: a backup that stops at the
     * first unreadable file is a backup of nothing, and the names of what it could not take are more use than an
     * exception.
     *
     * @param source the folder to copy
     * @param intoName what to call it inside the backup folder
     * @return the files it could not copy, empty when all was well
     */
    public static List<String> backupFolder(File source, String intoName)
    {
        List<String> failed = new ArrayList<>();

        if (source == null || !source.isDirectory()) return failed;

        File target = new File(getBackupPath(intoName));

        copyInto(source, target, failed, new java.util.HashSet<String>(), 0);

        return failed;
    }

    /**
     * The deepest a backup will go: a guard against a tree that is not a tree (junctions pointing at each other through
     * paths that never repeat), whose recursion would overflow the stack on the backup thread.
     */
    private static final int MAX_BACKUP_DEPTH = 64;

    private static void copyInto(File source, File target, List<String> failed,
        Set<String> visited, int depth)
    {
        if (source.isDirectory())
        {
            if (depth > MAX_BACKUP_DEPTH)
            {
                failed.add(source.getPath());
                return;
            }

            // Followed once: isDirectory follows junctions and links, so one pointing up its own tree walks forever.
            // The real path tells two names for one directory apart.
            String real;

            try
            {
                real = source.getCanonicalPath();
            }
            catch (IOException e)
            {
                failed.add(source.getPath());
                return;
            }

            if (!visited.add(real)) return;

            if (!target.isDirectory() && !target.mkdirs())
            {
                failed.add(target.getPath());
                return;
            }

            File[] children = source.listFiles();

            if (children == null)
            {
                failed.add(source.getPath());
                return;
            }

            for (File child : children)
            {
                copyInto(child, new File(target, child.getName()), failed, visited, depth + 1);
            }

            return;
        }

        try
        {
            java.nio.file.Files.copy(source.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e)
        {
            // The path, not the name: two same-named files in different folders must be told apart.
            failed.add(source.getPath());
        }
    }

    // Timeouts for downloads.  The read timeout applies to each individual read, so a stalled transfer
    // fails instead of leaving the download running indefinitely.
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 5000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 15000;

    /**
     * Receives the stream that writeAtomically is staging into.  A plain Consumer cannot be used
     * because everything worth writing this way throws IOException.
     */
    @FunctionalInterface
    public interface StreamWriter
    {
        void write(OutputStream out) throws IOException;
    }

    /**
     * Makes a page name safe to use as a local filename.  Page names are free text (the Central Station index, the
     * rename box), and one with a path separator or a forbidden character could land the write outside the layouts
     * folder or leave a half-written layout.
     *
     * Applied on both sides on purpose: the local read locates a page by the name in the index.  Here rather than on
     * CS2File because LayoutDiagram, in base, must not reach into the Marklin package; CS2File.sanitizeFilename
     * delegates here.  Only unusable characters are replaced, so existing local layouts load as before.
     *
     * @param name the page name
     * @return the name with anything unusable replaced by an underscore
     */
    public static String sanitizeFilename(String name)
    {
        if (name == null) return null;

        return name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_");
    }

    /**
     * Writes a file without ever leaving the target truncated: opening a file for writing empties it at once, so a
     * crash or failed write mid-way would silently lose the operator's work (the locomotive database, the UI state,
     * autonomy.json).  The content is staged in a sibling file and moved into place only once complete, as downloadFile
     * does.  A failed write deletes the staging file and leaves the previous contents.  REPLACE_EXISTING rather than
     * ATOMIC_MOVE, which is not supported everywhere and whose window is nanoseconds against seconds.
     *
     * @param target the file to end up with
     * @param body writes the content to the stream it is given; need not close it
     * @throws IOException
     */
    public static void writeAtomically(File target, StreamWriter body) throws IOException
    {
        File staging = new File(target.getAbsolutePath() + PARTIAL_DOWNLOAD_SUFFIX);

        try
        {
            try (FileOutputStream out = new FileOutputStream(staging))
            {
                body.write(out);

                out.flush();
            }
        }
        catch (IOException | RuntimeException e)
        {
            // The half-written staging file is worthless and would otherwise accumulate beside the
            // target.  The target itself has not been touched, which is the point of all this.
            staging.delete();

            throw e;
        }

        // The staging file goes either way: a failed move (a target held open on Windows) must not leave a
        // `LocDB.data.part` beside a corrupt target.  Deleted, not kept as a recovery artifact: nothing reads it, and
        // the caller is told the save failed.
        try
        {
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException moveFailed)
        {
            try
            {
                staging.delete();
            }
            catch (RuntimeException cannotTidy)
            {
                // Nothing: the move's failure is what the caller needs to hear about, not a failure to tidy up after
                // it.
            }

            throw moveFailed;
        }
    }

    /**
     * Downloads a file from a URL
     * @param fileURL
     * @param saveOutputFile
     * @throws IOException
     */
    public static void downloadFile(String fileURL, File saveOutputFile) throws IOException
    {
        downloadFile(fileURL, saveOutputFile, null);
    }

    /**
     * Downloads a file from a URL, reporting progress as it goes.
     * The output file is written under a temporary name and only renamed once complete,
     * so that an interrupted download never looks like a finished one.
     * @param fileURL
     * @param saveOutputFile
     * @param progress - receives the percentage downloaded, or -1 if the size is unknown
     * @throws IOException
     */
    public static void downloadFile(String fileURL, File saveOutputFile, IntConsumer progress) throws IOException
    {
        URL url = new URL(fileURL);
        URLConnection connection = url.openConnection();

        connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);

        // GitHub answers 406 to a release download that does not accept a binary file, as Java's default header does not
        connection.setRequestProperty("Accept", "application/octet-stream");

        File partialFile = new File(saveOutputFile.getAbsolutePath() + PARTIAL_DOWNLOAD_SUFFIX);

        long totalBytes = connection.getContentLengthLong();
        long readBytes = 0;

        // Starts out of range, so that the first read always reports, even when the size is unknown
        int lastPercent = -2;

        try (InputStream inputStream = new BufferedInputStream(connection.getInputStream());
             FileOutputStream outputStream = new FileOutputStream(partialFile.getAbsolutePath()))
        {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1)
            {
                outputStream.write(buffer, 0, bytesRead);
                readBytes += bytesRead;

                if (progress != null)
                {
                    // Capped, in case the server under-reports the content length
                    int percent = totalBytes > 0 ? (int) Math.min(100, readBytes * 100 / totalBytes) : -1;

                    // Only report changes, so that we don't flood the caller
                    if (percent != lastPercent)
                    {
                        lastPercent = percent;
                        progress.accept(percent);
                    }
                }
            }
        }
        catch (IOException e)
        {
            partialFile.delete();

            throw e;
        }

        Files.move(partialFile.toPath(), saveOutputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    
    /**
     * Parses the release version from getLatestReleaseInfo (i.e. TrainControl v2.3.0 -> 2.3.0), found by the number
     * rather than by the letter: a name with no "v", or another "v" ("TrainControl Preview v3.0.0"), must not throw or
     * hand back a piece of the name, which would hide a real update.  Release names are typed by hand at tag time.
     *
     * @param gitHubReleaseInfo
     * @return the version, or null when the name carries no number
     */
    public static String parseReleaseVersion(JSONObject gitHubReleaseInfo)
    {
        String name = gitHubReleaseInfo.getString("name");

        // The number after the "v" first: matching the first number outright gives "2026" for "TrainControl 2026
        // Edition v3.0.0".  The "v" must be the tag marker (a left boundary), not the end of a word ("Nov2026 build"),
        // and the longest anchored match wins, so "Marklin CS3 v2.6 compatibility - TrainControl v3.0.0" does not
        // report 2.6.
        java.util.regex.Matcher tagged = java.util.regex.Pattern
            .compile("(?<![A-Za-z0-9])[vV]([0-9]+(?:[.][0-9]+)*)").matcher(name);

        String anchored = null;

        while (tagged.find())
        {
            if (anchored == null || tagged.group(1).length() > anchored.length())
            {
                anchored = tagged.group(1);
            }
        }

        if (anchored != null) return anchored;

        // No "v" at all: the longest version-shaped number wins, so a three-part version beats a stray year or build
        // number.
        java.util.regex.Matcher version =
            java.util.regex.Pattern.compile("[0-9]+(?:[.][0-9]+)*").matcher(name);

        // Null rather than a guess: a name with no number has no version to report.
        String best = null;

        while (version.find())
        {
            if (best == null || version.group().length() > best.length()) best = version.group();
        }

        return best;
    }
    
    /**
     * Parses the release download URL from getLatestReleaseInfo
     * @param gitHubReleaseInfo
     * @return 
     */
    public static String parseDownloadURL(JSONObject gitHubReleaseInfo)
    {
        return gitHubReleaseInfo.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");
    }
    
    /**
     * The size in bytes of the release download, so an update can tell a whole download from one cut short (FR-125).
     *
     * @param gitHubReleaseInfo
     * @return the size, or -1 when the release does not give one
     */
    public static long parseDownloadSize(JSONObject gitHubReleaseInfo)
    {
        return gitHubReleaseInfo.getJSONArray("assets").getJSONObject(0).optLong("size", -1);
    }

    /**
     * The release download's digest, `sha256:<hex>`, where the release gives one (FR-125).
     *
     * @param gitHubReleaseInfo
     * @return the digest, or null
     */
    public static String parseDownloadDigest(JSONObject gitHubReleaseInfo)
    {
        String digest = gitHubReleaseInfo.getJSONArray("assets").getJSONObject(0).optString("digest", "");

        return digest.trim().isEmpty() ? null : digest.trim();
    }
    
    /**
     * Parses the release page URL from getLatestReleaseInfo
     * @param gitHubReleaseInfo
     * @return 
     */
    public static String parseReleaseURL(JSONObject gitHubReleaseInfo)
    {
        return gitHubReleaseInfo.getString("html_url");
    }
    
    /**
     * Fetches the latest release info from github
     * @param repo "username/repo_name"
     * @return 
     * @throws java.io.IOException 
     */
    public static JSONObject getLatestReleaseInfo(String repo) throws IOException, Exception
    {
        String apiUrl = "https://api.github.com/repos/" + repo + "/releases/latest";

        StringBuilder content = new StringBuilder();

        // Try-with-resources, so a bad response does not keep the connection (readLine can throw before a plain close).
        try (BufferedReader in = CS2File.fetchURL(apiUrl))
        {
            String inputLine;

            while ((inputLine = in.readLine()) != null)
            {
                content.append(inputLine);
            }
        }

        // Parse the JSON response to get the tag_name
        return new JSONObject(content.toString());
    }
    
    /**
     * Escapes data for a CSV
     * @param input
     * @return 
     */
    public static String escapeCsv(String input)
    {
        if (input == null) return "";

        boolean needsQuotes = input.contains(",") || input.contains("\"") || input.contains("\n");
        String escaped = input.replace("\"", "\"\"");

        if (needsQuotes)
        {
            return "\"" + escaped + "\"";
        }
        else
        {
            return escaped;
        }
    }
}
