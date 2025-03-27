package org.traincontrol.marklin;

import java.io.BufferedWriter;
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
        if (IGNORE_PADDING)
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
                        if (IGNORE_PADDING)
                        {
                            minx = x;
                        }
                    }
                    
                    if (y < miny)
                    {
                        if (IGNORE_PADDING)
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
     * Saves this layout to the existing path.  Should only be called if stored locally.
     * @throws Exception 
     */
    public void saveChanges() throws Exception
    {
        try
        {
            // Retrieve the export data
            String data = exportToCS2TextFormat();

            // Write the data to the file using Files.newBufferedWriter()
            try (BufferedWriter writer = Files.newBufferedWriter(getFilePath()))
            {
                writer.write(data);
            }
        }
        catch (IOException | URISyntaxException e)
        {
            throw new Exception("Error saving changes to file: " + url, e);
        }
    }
    
    /**
     * Expands the layout by the specified number of rows and columns
     * @param num
     * @throws IOException 
     */
    public void addRowsAndColumns(int num) throws IOException
    {
        for (int x = 0; x < num; x++)
        {
            List<MarklinLayoutComponent> newRow = new ArrayList<>();
            
            for (int i = 0; i < sx; i++)
            {
                newRow.add(null);
            }

            grid.add(newRow);

            for (List<MarklinLayoutComponent> row : grid)
            {
                row.add(null);
            }

            sy+=1;
            maxy+=1;
            sx+=1;
            maxx+=1;
        }
        
        /*addComponent(
            MarklinLayoutComponent.componentType.TEXT,
            sx-1, sy-1, 0, 0, 0, 0, "x"
         );*/
        //checkBounds();
        //addComponent(null,0,sy-1);
    }
}
