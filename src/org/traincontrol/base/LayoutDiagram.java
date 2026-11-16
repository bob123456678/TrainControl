package org.traincontrol.base;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;

/**
 * Layout container with grid and size info
 * @author Adam
 */
public class LayoutDiagram
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
    private final List<List<LayoutDiagramComponent>> grid;
    
    // Network reference
    private final ViewListener network;
    
    // Path to the layout file
    private final String url;
    
    // Are we in edit mode?
    private boolean edit = false;
    private boolean editHideText = false;
    private boolean showAddress = false;

    /**
     * Constructor
     * @param name
     * @param sx size x
     * @param sy size y
     * @param url
     * @param network 
     */
    public LayoutDiagram(String name, int sx, int sy, String url, ViewListener network)
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
            List<LayoutDiagramComponent> l = 
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
    
    public void addComponent(LayoutDiagramComponent.componentType t, 
            int x, int y, int orient, int state, int address, int rawAddresss, Accessory.accessoryDecoderType protocol, String text) throws IOException
    {
        assert x < sx;
        assert y < sy;
                
        grid.get(x).set(y, new LayoutDiagramComponent(t, x, y, orient, state, address, rawAddresss, protocol));
        
        if (text != null)
        {
            this.getComponent(x, y).setLabel(text);
        }
    }
    
    public void addComponent(LayoutDiagramComponent l, int x, int y) throws IOException
    {
        assert x < sx;
        assert y < sy;
                
        grid.get(x).set(y, l);
    }
    
    public String getName()
    {
        return this.name;
    }
    
    public LayoutDiagramComponent getComponent(int x, int y)
    {
        if (x < 0 || y < 0 || x >= this.grid.size() || y >= this.grid.get(0).size()) return null;
        
        return this.grid.get(x).get(y);
    }
    
    public List<LayoutDiagramComponent> getAll()
    {
        List<LayoutDiagramComponent> out = new ArrayList<>();
        
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
                if (this.getComponent(x, y) != null || edit) // This will persist the grid in edit mode
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
    
    public ViewListener getControl()
    {
        return this.network;
    }
    
    @Override
    public String toString()
    {        
        return I18n.f("layout.dimensions", this.name, Integer.toString(sx), Integer.toString(sy));
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
     * A page file's text with each accessory square's `.prot` as a save of this page writes it (OB-465).  Other lines
     * stay as they were.
     */
    public String withSavedProtocols(String text)
    {
        List<String> out = new ArrayList<>();
        List<String> block = new ArrayList<>();

        for (String line : (text + "\nend").split("\n", -1))
        {
            // A line naming a block ends the one before it
            if (line.matches("^[a-zA-Z]+$") && !block.isEmpty())
            {
                out.addAll(withSavedProtocol(block));
                block.clear();
            }

            block.add(line);
        }

        return String.join("\n", out);
    }

    /** One block of withSavedProtocols, its `.prot` set, before its `.artikel`, when it is an accessory square. */
    private List<String> withSavedProtocol(List<String> block)
    {
        java.util.Map<String, String> keys = new java.util.HashMap<>();
        int at = block.size();

        for (int i = block.size() - 1; i > 0; i--)
        {
            String[] parts = block.get(i).replaceFirst("^ \\.", "").split("=", 2);

            if (parts.length == 2) keys.putIfAbsent(parts[0], parts[1]);
            if (parts[0].equals("artikel") || parts[0].equals("prot")) at = i;
        }

        LayoutDiagramComponent tile = null;

        try
        {
            int coord = keys.get("id") == null ? 0 : Integer.valueOf(keys.get("id").replace("0x", ""), 16);

            if (block.get(0).equalsIgnoreCase("element")) tile = getComponent(coord % 256, (coord >> 8) % 256);

            // A square changed since this page was read is written as served
            if (tile != null && !String.valueOf(tile.getRawAddress()).equals(keys.get("artikel"))) tile = null;
        }
        catch (NumberFormatException unreadCoordinate)
        {
            // No square to take a protocol from
        }

        Accessory.accessoryDecoderType saved = tile == null || !tile.isAccessory() ? null : tile.savedProtocol();

        if (saved == null) return block;

        List<String> out = new ArrayList<>(block);
        out.removeIf(line -> line.startsWith(" .prot="));
        out.add(Math.min(at, out.size()), " .prot=" + saved);
        return out;
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

            // If filename is not null, delete the original file - unless it IS the new file.
            //
            // On Windows and macOS "Main" and "MAIN" name one file, so the writer above did not create
            // a second one, it reopened this one and wrote it.  Deleting the "original" then deleted
            // the only copy of the page, and the index was left naming a file that does not exist.
            // Renaming is offered only for local layouts, so there is no Central Station copy to fall
            // back on - the page was simply gone.
            if (filename != null && !duplicate)
            {
                if (Files.isSameFile(originalFilePath, newFilePath))
                {
                    // The bytes are already right; what is left is the spelling.  Through a temporary
                    // name because a direct move onto a path the filesystem considers identical is
                    // rejected rather than treated as a rename.
                    Path staged = originalFilePath.resolveSibling(filename.trim() + ".cs2.renaming");

                    Files.move(originalFilePath, staged);
                    Files.move(staged, newFilePath);
                }
                else
                {
                    Files.delete(originalFilePath);
                }
            }
        } 
        catch (IOException | URISyntaxException e)
        {
            throw new Exception(I18n.f("error.savingFileChanges", url), e);
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
        if (numColumns < 0) numColumns = 0;
        if (numRows < 0) numRows = 0;

        for (int x = 0; x < numColumns; x++)
        {
            List<LayoutDiagramComponent> newColumn = new ArrayList<>();

            // A column is sy cells tall.  Sizing it by sx made the new column the wrong length, and on
            // a layout taller than it is wide it was too short - getComponent bounds-checks y against
            // column 0, so reading the new column then threw IndexOutOfBounds
            for (int i = 0; i < sy; i++)
            {
                newColumn.add(null);
            }

            grid.add(newColumn);
            
            sx+=1;
            maxx+=1;
        }
        
        for (int x = 0; x < numRows; x++)
        {
            for (List<LayoutDiagramComponent> col : grid)
            {
                col.add(null);
            }

            sy+=1;
            maxy+=1;
        }
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
        
        // Do not reset sx and sy unless we also shrink the arrays...
        // this.sx = 0;
        // this.sy = 0;
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
    
    public boolean getShowAddress()
    {
        return showAddress;
    }

    public void setShowAddress(boolean showAddress)
    {
        this.showAddress = showAddress;
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
                for (int y = 0; y <= maxy; y++)
                {
                    LayoutDiagramComponent component = getComponent(x, y);

                    if (component != null) component.setX(x + 1);
                    addComponent(null, x, y); // Clear the original cell
                    addComponent(component, x + 1, y); // Move the component to the right
                }
            }

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
        this.addRowsAndColumns(1, 0);

        if (startRow == 0 || startRow > sy - 2)
        {
            startRow = miny;
        }

        // Shift all existing components one row downward
        if (sy >= 2)
        {
            for (int y = maxy - 1; y >= startRow; y--)
            { // Start from the last row and move upward
                for (int x = 0; x <= maxx; x++)
                {
                   LayoutDiagramComponent component = getComponent(x, y);

                   if (component != null) component.setY(y + 1); // Update the component's row position
                   addComponent(null, x, y); // Clear the original cell
                   addComponent(component, x, y + 1); // Move the component downward
                }
            }

           this.checkBounds();
        }
    }
    
    /**
    * Removes a row from the layout at the specified index and shifts all existing components one row upward.
    * @param startRow
    * @throws IOException
    */
    public void shiftUp(int startRow) throws IOException 
    {
        if (sy < 2) return; // Ensure there's enough rows to shift up

        if (startRow == 0 || startRow > sy - 2) 
        {
            startRow = miny; // Normalize startRow
        }

        // Shift all existing components one row upward
        for (int y = startRow; y < maxy; y++) 
        { 
            for (int x = 0; x <= maxx; x++) 
            {
                LayoutDiagramComponent component = getComponent(x, y + 1);

                if (component != null) component.setY(y); // Update the component's row position
                addComponent(null, x, y + 1); // Clear the original cell
                addComponent(component, x, y); // Move the component upward
            }
        }

        this.checkBounds();
    }
    
    /**
    * Removes a column from the layout at the specified index and shifts all existing components one column to the left.
    * @param startCol
    * @throws IOException
    */
    public void shiftLeft(int startCol) throws IOException 
    {    
        if (sx < 2) return; // Ensure there are enough columns to shift left

        if (startCol == 0 || startCol > sx - 2) 
        {
            startCol = minx; // Normalize startCol
        }

        // Shift all existing components one column to the left
        for (int x = startCol; x < maxx; x++) 
        { 
            for (int y = 0; y <= maxy; y++) 
            {
                LayoutDiagramComponent component = getComponent(x + 1, y);

                if (component != null) component.setX(x); // Update the component's column position
                addComponent(null, x + 1, y); // Clear the original cell
                addComponent(component, x, y); // Move the component left
            }
        }

        this.checkBounds();
    }
    
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
        
        // Built in memory and then written ATOMICALLY, through the same door saveChanges uses.
        //
        // This is the index of every page there is, and it was the one file still truncated in place:
        // a write that failed part way through (the folder held by a sync client, a full disk, the
        // process dying) left a header naming no pages at all.  Every page file would still be on
        // disk, unreferenced, and the next start would show an empty layout.
        //
        // UTF-8, matching saveChanges above and the encoding CS2File.fetchURL reads these files back
        // in.  FileWriter used the platform default charset, so a page name with a non-ASCII
        // character did not survive the round trip.
        StringBuilder contents = new StringBuilder();

        // Write header and static content
        contents.append("[gleisbild]\n");
        contents.append("version\n");
        contents.append(" .major=1\n");
        contents.append("groesse\n");

        // Write layout details
        int id = 1;
        for (String layout : layoutList)
        {
            contents.append("seite\n");
            if (id != 1) { // Skip ID for the first layout
                contents.append(" .id=").append(id).append("\n");
            }

            contents.append(" .name=").append(layout).append("\n");
            id++;
        }

        final byte[] bytes = contents.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        org.traincontrol.util.Util.writeAtomically(new File(filePath), out -> out.write(bytes));
    }
}
