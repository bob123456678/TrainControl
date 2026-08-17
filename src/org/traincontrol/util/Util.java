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
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.json.JSONObject;
import org.traincontrol.marklin.file.CS2File;

/**
 * Helper functions
 */
public class Util
{
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

    /**
     * Returns the path a backup file should be written to.  Backups go into a dedicated subfolder
     * ({@link #BACKUP_FOLDER}), which is created if it does not exist.  If the folder cannot be
     * created, the original file name is returned so the backup falls back to the current directory.
     * @param fileName the intended backup file name
     * @return the path to write the backup to
     */
    public static String getBackupPath(String fileName)
    {
        File dir = new File(BACKUP_FOLDER);

        if (dir.isDirectory() || dir.mkdirs())
        {
            return new File(dir, fileName).getPath();
        }

        // Folder unavailable - fall back to the current directory (the original behaviour)
        return fileName;
    }

    /**
     * Copies a folder and everything under it into the backup area.
     *
     * Added because the backup did not cover the track diagrams or the autonomy setup - it saved the
     * locomotive database, the window state and autonomy.json, which was the whole story while the
     * diagram lived on the Central Station and autonomy lived in one file.  It is not the whole story
     * now: station captions, names, directions and every configuration live in config/autonomy beside
     * the diagram, and for a locally stored layout that folder is the only copy there is.
     *
     * Best effort per file.  A backup that stops at the first unreadable file is a backup of nothing,
     * and the names of what it could not take are more use than an exception.
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

        copyInto(source, target, failed);

        return failed;
    }

    private static void copyInto(File source, File target, List<String> failed)
    {
        if (source.isDirectory())
        {
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
                copyInto(child, new File(target, child.getName()), failed);
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
            failed.add(source.getName());
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
     * Writes a file without ever leaving the target truncated.
     *
     * Opening a file for writing empties it immediately, so from the first byte until the last is
     * flushed the only copy of the data is incomplete - and if the process dies in that window, or the
     * write throws part way, what was there before is gone.  For the files this exists to protect
     * (the locomotive database, the UI state, autonomy.json) that is the operator's accumulated work,
     * and the loss is silent: an unreadable database reads as a first launch, and the next Central
     * Station sync repopulates the locomotive list so the customizations look mislaid rather than
     * destroyed.
     *
     * The content is therefore staged in a sibling file and moved into place only once it is complete
     * and closed - the same shape downloadFile uses so that an interrupted download never looks like a
     * finished one.  A failed write deletes the staging file and leaves the previous contents exactly
     * as they were.  REPLACE_EXISTING rather than ATOMIC_MOVE: the rename window is nanoseconds
     * against a write window of milliseconds to seconds, and ATOMIC_MOVE is not supported everywhere.
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

        Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
     * Parses the release version from getLatestReleaseInfo (i.e. TrainControl v2.3.0 -> 2.3.0)
     * @param gitHubReleaseInfo
     * @return 
     */
    public static String parseReleaseVersion(JSONObject gitHubReleaseInfo)
    {
        return gitHubReleaseInfo.getString("name").split("v")[1];
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

        BufferedReader in = CS2File.fetchURL(apiUrl);
        StringBuilder content = new StringBuilder();
        String inputLine;

        while ((inputLine = in.readLine()) != null)
        {
            content.append(inputLine);
        }

        in.close();

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
