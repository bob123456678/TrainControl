package org.traincontrol.util;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.json.JSONObject;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.model.ViewListener;

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
     * Fetches the latest release name from github
     * @param repo
     * @param model - for logging purposes
     * @return 
     */
    public static String getLatestReleaseVersion(String repo, ViewListener model)
    {
        try
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
            JSONObject jsonResponse = new JSONObject(content.toString());
            return jsonResponse.getString("name");
        }
        catch (Exception e)
        {
            if (model != null)
            {
                model.log("Failed to check for latest version.");

                if (model.isDebug())
                {
                    model.log(e);
                }
            }
            else
            {
                System.out.println("Failed to check for latest version.");
            }
            
            return null;
        }
    }
}
