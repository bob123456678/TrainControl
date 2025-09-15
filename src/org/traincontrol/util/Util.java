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
     * Downloads a file from a URL
     * @param fileURL
     * @param saveOutputFile
     * @throws IOException 
     */
    public static void downloadFile(String fileURL, File saveOutputFile) throws IOException
    {
        URL url = new URL(fileURL);
        URLConnection connection = url.openConnection();
        InputStream inputStream = new BufferedInputStream(connection.getInputStream());
        FileOutputStream outputStream = new FileOutputStream(saveOutputFile.getAbsolutePath());

        byte[] buffer = new byte[1024];
        int bytesRead = -1;
        while ((bytesRead = inputStream.read(buffer)) != -1)
        {
            outputStream.write(buffer, 0, bytesRead);
        }

        outputStream.close();
        inputStream.close();
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
