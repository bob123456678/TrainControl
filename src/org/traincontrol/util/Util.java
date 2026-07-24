package org.traincontrol.util;

import java.awt.Desktop;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    // Timeouts for downloads.  The read timeout applies to each individual read, so a stalled transfer
    // fails instead of leaving the download running indefinitely.
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 5000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 15000;

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
