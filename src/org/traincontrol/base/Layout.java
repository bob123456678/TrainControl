package org.traincontrol.base;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * Base layout
 */
public class Layout
{    
    /**
     * Writes a file with the list of all layout pages
     * We just piggyback off Marklin's CS2 format to simplify compatibility
     * @param path
     * @param layoutList
     * @throws IOException 
     */
    public static void writeLayoutIndex(String path, List<String> layoutList) throws IOException
    {
        // Ensure the directory exists
        File directory = new File(Paths.get(path, "config").toString());
        if (!directory.exists())
        {
            directory.mkdirs();
        }

        // Construct the file path
        String filePath = Paths.get(path, "config", "gleisbild.cs2").toString();
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath)))
        {
            // Write header and static content
            writer.write("[gleisbild]\n");
            writer.write("version\n");
            writer.write(" .major=1\n");
            writer.write("groesse\n");
            
            // Write layout details
            int id = 1;
            for (String layout : layoutList)
            {
                writer.write("seite\n");
                if (id != 1) { // Skip ID for the first layout
                    writer.write(" .id=" + id + "\n");
                }
                
                writer.write(" .name=" + layout + "\n");
                id++;
            }
        }
    }
}
