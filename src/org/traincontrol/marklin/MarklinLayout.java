package org.traincontrol.marklin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Layout container with grid and size info
 * @author Adam
 */
public class MarklinLayout
{
    private final String name;
    
    // Size
    private int sx;
    private int sy;
    
    // Set to true to trim layouts around the top/left edges & center in the UI
    public static final boolean IGNORE_PADDING = true;
    
    int minx = 0;
    int miny = 0;
    int maxx;
    int maxy;
    
    public int getMinx()
    {
        return minx;
    }

    public int getMiny()
    {
        return miny;
    }

    public int getMaxx()
    {
        return maxx;
    }

    public int getMaxy()
    {
        return maxy;
    }
   
    // Corresponding accessory reference
    private final List<List<MarklinLayoutComponent>> grid;
    
    // Network reference
    private final MarklinControlStation network;
    
    // Path to the layout file
    private final String url;
    
    // Are we in edit mode?
    private boolean edit = false;
    private boolean editHideText = false;
    private boolean editShowAddress = false;

    /**
     * Constructor
     * @param name
     * @param sx size x
     * @param sy size y
     * @param url
     * @param network 
     */
    public MarklinLayout(String name, int sx, int sy, String url, MarklinControlStation network)
    {
        this.name = name;
        this.sx = sx;
        this.sy = sy;
        this.maxx = sx;
        this.maxy = sy;
        this.network = network;
        this.url = url;
                
        this.grid = new ArrayList<>();
        
        for (int i = 0; i < sx; i++)
        {            
            List<MarklinLayoutComponent> l = 
                    new ArrayList<>();
            
            for (int j = 0; j < sy; j++)
            {
                l.add(null);
            }
            
            grid.add(l);
        }
    }
    
    public String getUrl()
    {
        return url;
    }
    
    public void addComponent(MarklinLayoutComponent.componentType t, 
            int x, int y, int orient, int state, int address, int rawAddresss, String text) throws IOException
    {
        assert x < sx;
        assert y < sy;
                
        grid.get(x).set(y, new MarklinLayoutComponent(t, x, y, orient, state, address, rawAddresss));
        
        if (text != null)
        {
            this.getComponent(x, y).setLabel(text);
        }
    }
    
    public void addComponent(MarklinLayoutComponent l, int x, int y) throws IOException
    {
        assert x < sx;
        assert y < sy;
                
        grid.get(x).set(y, l);
    }
    
    public String getName()
    {
        return this.name;
    }
    
    public MarklinLayoutComponent getComponent(int x, int y)
    {
        if (x < 0 || y < 0 || x >= this.grid.size() || y >= this.grid.get(0).size()) return null;
        
        return this.grid.get(x).get(y);
    }
    
    public List<MarklinLayoutComponent> getAll()
    {
        List<MarklinLayoutComponent> out = new ArrayList<>();
        
        for (int x = 0; x < sx; x++)
        {            
            for (int y = 0; y < sy; y++)
            {
                if (this.getComponent(x, y) != null)
                {
                    out.add(this.getComponent(x, y));
                }
            }
        }
        
        return out;
    }
    
    public void checkBounds()
    {
        if (IGNORE_PADDING && !edit)
        {
            minx = sx;
            miny = sy;
        }
        else
        {
            minx = 0;
            miny = 0;
        }
        
        maxx = 0;
        maxy = 0;
        
        for (int x = 0; x < sx; x++)
        {            
            for (int y = 0; y < sy; y++)
            {
                if (this.getComponent(x, y) != null)
                {
                    if (x < minx)
                    {
                        if (IGNORE_PADDING && !edit)
                        {
                            minx = x;
                        }
                    }
                    
                    if (y < miny)
                    {
                        if (IGNORE_PADDING && !edit)
                        {
                            miny = y;
                        }
                    }
                    
                    if (x > maxx)
                    {
                        maxx = x;
                    }
                    
                    if (y > maxy)
                    {
                        maxy = y;
                    }
                }
            }            
        }  
    }

    public int getSx()
    {
        return sx;
    }

    public int getSy()
    {
        return sy;
    }
    
    public MarklinControlStation getControl()
    {
        return this.network;
    }
    
    @Override
    public String toString()
    {        
        return "Layout " + this.name + " (" + Integer.toString(sx) + "x" + Integer.toString(sy) + ")";
    }
    
    /**
     * Exports this layout to the CS2 format
     * @return
     * @throws Exception 
     */
    public String exportToCS2TextFormat() throws Exception
    {
        StringBuilder builder = new StringBuilder();

        builder.append("[gleisbildseite]\n" +
            "version\n" +
            " .major=1\n");
        
        for (int y = 0; y < sy; y++)
        {
            for (int x = 0; x < sx; x++)
            {            
                if (this.getComponent(x, y) != null)
                {
                    builder.append(this.getComponent(x, y).exportToCS2TextFormat());
                    builder.append("\n");
                }
            }
        }
        
        return builder.toString().trim();
    }
    
    /**
     * Gets the path to the layout file
     * @return
     * @throws MalformedURLException
     * @throws URISyntaxException 
     */
    private Path getFilePath() throws MalformedURLException, URISyntaxException
    {
        String layoutUrl = url.replaceAll(" ", "%20");
        return Paths.get(new URL(layoutUrl).toURI());
    }
    
    /**
     * Deletes the current layout file
     * @throws MalformedURLException
     * @throws URISyntaxException
     * @throws IOException 
     */
    public void deleteLayoutFile() throws MalformedURLException, URISyntaxException, IOException
    {
        Files.delete(this.getFilePath());
    }
    
    /**
     * Saves this layout to the existing path. Should only be called if stored locally.
     * @param filename the new filename (without extension) or null to use the original filename
     * @param duplicate true to avoid deleting the original file when renaming
     * @throws Exception
     */
    public void saveChanges(String filename, boolean duplicate) throws Exception
    {
        try
        {
            // Retrieve the export data
            String data = exportToCS2TextFormat();

            // Determine the file path
            Path originalFilePath = getFilePath();
            Path newFilePath = (filename != null && !"".equals(filename.trim()))
                    ? originalFilePath.resolveSibling(filename.trim() + ".cs2")
                    : originalFilePath;

            // Write the data to the file using Files.newBufferedWriter()
            try (BufferedWriter writer = Files.newBufferedWriter(newFilePath))
            {
                writer.write(data);
            }

            // If filename is not null, delete the original file
            if (filename != null && !duplicate)
            {
                Files.delete(originalFilePath);
            }
        } 
        catch (IOException | URISyntaxException e)
        {
            throw new Exception("Error saving changes to file: " + url, e);
        }
    }
    
    /**
     * Expands the layout by the specified number of rows and columns
     * @param numRows
     * @param numColumns
     * @throws IOException 
     */
    synchronized public void addRowsAndColumns(int numRows, int numColumns) throws IOException
    {
        for (int x = 0; x < numColumns; x++)
        {
            List<MarklinLayoutComponent> newColumn = new ArrayList<>();
            
            for (int i = 0; i < sx; i++)
            {
                newColumn.add(null);
            }

            grid.add(newColumn);
            
            sx+=1;
            maxx+=1;
        }
        
        for (int x = 0; x < numRows; x++)
        {
            for (List<MarklinLayoutComponent> col : grid)
            {
                col.add(null);
            }

            sy+=1;
            maxy+=1;
        }
        
        /*addComponent(
            MarklinLayoutComponent.componentType.TEXT,
            sx-1, sy-1, 0, 0, 0, 0, "x"
         );*/
        //checkBounds();
        //addComponent(null,0,sy-1);
    }
    
    /**
     * Clears the layout
     * @throws IOException 
     */
    synchronized public void clear() throws IOException
    {
        for (int y = 0; y < sy; y++)
        {
            for (int x = 0; x < sx; x++)
            {   
                addComponent(null, x, y);
            }
        }
        
        this.sx = 0;
        this.sy = 0;
        this.checkBounds();
    }
    
    public void setEdit()
    {
        this.edit = true;
        this.checkBounds();
    }
    
    public boolean getEdit()
    {
        return this.edit;
    }
    
    public boolean getEditHideText()
    {
        return editHideText;
    }

    public void setEditHideText(boolean editHideText)
    {
        this.editHideText = editHideText;
    }
    
    public boolean getEditShowAddress()
    {
        return editShowAddress;
    }

    public void setEditShowAddress(boolean editShowAddress)
    {
        this.editShowAddress = editShowAddress;
    }
    
    /**
     * Writes a file with the list of all layout pages
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
    
    // We don't use these methods in the UI becuase we would also need shiftLeft and shiftDown for completeness
    
    /**
     * Adds a new column to the layout at the specified index and shifts all existing components one column to the right.
     * @param startCol
     * @throws IOException
     */
    public void shiftRight(int startCol) throws IOException
    {    
        this.addRowsAndColumns(0, 1);

        if (startCol == 0 || startCol > sx - 2)
        {
            startCol = minx;
        }

        // Shift all existing components one column to the right
        if (sx >= 2)
        {
             for (int x = maxx - 1; x >= startCol; x--)
             { // Start from the second-to-last column and move backward
                 for (int y = 0; y < maxy; y++)
                 {
                     MarklinLayoutComponent component = getComponent(x, y);

                     if (component != null) component.setX(x + 1);
                     addComponent(null, x, y); // Clear the original cell
                     addComponent(component, x + 1, y); // Move the component to the right
                 }
             }

             this.checkBounds();
             this.addRowsAndColumns(0, 1);
             this.checkBounds();
        }
    }
    
    /**
    * Adds a new row to the layout at the specified index and shifts all existing components one row downward.
    * @param startRow
    * @throws IOException
    */
    public void shiftDown(int startRow) throws IOException
    {
       // Add a new row to the layout
       this.addRowsAndColumns(1, 1);

       if (startRow == 0 || startRow > sy - 2)
       {
           startRow = miny;
       }

       // Shift all existing components one row downward
       if (sy >= 2)
       {
           for (int y = maxy - 1; y >= startRow; y--)
           { // Start from the last row and move upward
               for (int x = 0; x < maxx; x++)
               {
                   MarklinLayoutComponent component = getComponent(x, y);

                   if (component != null) component.setY(y + 1); // Update the component's row position
                   addComponent(null, x, y); // Clear the original cell
                   addComponent(component, x, y + 1); // Move the component downward
               }
           }

           this.checkBounds();
           this.addRowsAndColumns(1, 0);
           this.checkBounds();
       }
   }
}
