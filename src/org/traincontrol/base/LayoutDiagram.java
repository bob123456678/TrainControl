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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.I18n;

/**
 * Layout container with grid and size info
 * @author Adam
 */
public class LayoutDiagram
{
    private final String name;

    // The id gleisbild.cs2 gives this page.  The Central Station uses it for ordering and TrainControl
    // has never needed it - but autonomy stores its setup against pages, and a name is what a user
    // renames while an id is not, so keeping it lets that setup survive a rename.
    private String pageId;
    
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
    
    /**
     * The id of this page in gleisbild.cs2, or null if the index did not give one.
     *
     * Not an identity to lean on blindly: the Central Station uses it for sorting, so reordering pages
     * there could renumber them.  Anything storing against it should record the name too and check the
     * two still agree, or a renumber would silently reattach data to the wrong page - which is worse
     * than losing it, because nothing looks wrong.
     * @return
     */
    public String getPageId()
    {
        return pageId;
    }

    public void setPageId(String pageId)
    {
        this.pageId = pageId;
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

        builder.append("[gleisbildseite]\n");

        // The blocks above the elements, put back as they were read.
        //
        // This used to emit a hardcoded version block, which is the same loss the elements themselves
        // suffered one level up: a version carrying anything besides .major, or any block a later
        // Central Station firmware writes into a page, was deleted the first time the page was saved.
        // The hardcoded header is still the fallback, for a page built in memory rather than read.
        if (unmodelledBlocks.isEmpty())
        {
            builder.append("version\n .major=1\n");
        }
        else
        {
            for (Map<String, String> block : unmodelledBlocks)
            {
                builder.append(block.get("_type")).append("\n");

                for (Map.Entry<String, String> entry : block.entrySet())
                {
                    if ("_type".equals(entry.getKey())) continue;

                    LayoutDiagramComponent.appendPreservedKey(builder, entry.getKey(),
                        entry.getValue());
                }
            }
        }
        
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

        // The elements this build could make nothing of, put back exactly as they were read.
        //
        // Saving regenerates the whole page from this model, and an element whose type TrainControl does
        // not recognise never entered the model - so writing the file deleted it.  That is done on the
        // user's behalf, without a save button being pressed: naming one station writes its page out,
        // and renaming a point writes out every page carrying that caption.  Losing scenery, or a
        // component a later Central Station firmware added, because this program had not heard of it is
        // not a trade anybody agreed to.
        for (Map<String, String> element : unmodelledElements)
        {
            builder.append("element\n");

            for (Map.Entry<String, String> entry : element.entrySet())
            {
                LayoutDiagramComponent.appendPreservedKey(builder, entry.getKey(), entry.getValue());
            }
        }

        return builder.toString().trim();
    }

    /**
     * Elements read from the file that no component could be made of, kept verbatim so that saving does
     * not delete them.  See exportToCS2TextFormat.
     */
    private final List<Map<String, String>> unmodelledElements = new ArrayList<>();

    /**
     * The blocks of the file that are not elements - version, groesse, and whatever a later firmware
     * adds - in the order they were read.  See exportToCS2TextFormat.
     */
    private final List<Map<String, String>> unmodelledBlocks = new ArrayList<>();

    /**
     * Remembers a block that is not an element, so that saving does not delete it.
     *
     * @param block the raw keys of one block, including the parser's _type marker naming it
     */
    public void addUnmodelledBlock(Map<String, String> block)
    {
        if (block == null || block.get("_type") == null) return;

        Map<String, String> kept = new LinkedHashMap<>();

        kept.put("_type", block.get("_type"));

        for (Map.Entry<String, String> entry : new TreeMap<>(block).entrySet())
        {
            if (!"_type".equals(entry.getKey())) kept.put(entry.getKey(), entry.getValue());
        }

        unmodelledBlocks.add(kept);
    }

    /**
     * Remembers an element this program could not model, so that it survives a save.
     *
     * Ordering of the keys is not preserved - the parser reads them into an unordered map - so they are
     * written back sorted, with id and typ first as the Central Station writes them.  The CS2 format is
     * key/value pairs within an element, so what matters is that none of them is lost.
     *
     * @param element the raw keys of one element, including the parser's own _type marker
     */
    public void addUnmodelledElement(Map<String, String> element)
    {
        if (element == null) return;

        Map<String, String> kept = new LinkedHashMap<>();

        // These two first, as the Central Station writes them
        if (element.containsKey("id")) kept.put("id", element.get("id"));
        if (element.containsKey("typ")) kept.put("typ", element.get("typ"));

        for (Map.Entry<String, String> entry : new TreeMap<>(element).entrySet())
        {
            // the parser's marker for which block this was, not a key of the file
            if ("_type".equals(entry.getKey())) continue;

            if (!kept.containsKey(entry.getKey())) kept.put(entry.getKey(), entry.getValue());
        }

        if (!kept.isEmpty()) unmodelledElements.add(kept);
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

            // Staged and moved into place, never truncated where it stands.
            //
            // This is a page of somebody's track diagram, and it is rewritten by things they did not
            // ask for - the caption migration runs on the first repaint after an upgrade.  A plain
            // truncate-in-place turns a crash or a power cut during that write into a lost page, and
            // the same care is already taken over the locomotive database and the UI state.
            //
            // A copy of what was there is kept beside it the first time a page is rewritten, so that
            // a migration which strips something a user wanted is recoverable by hand.  Only the
            // first: the point is the state before this build touched it, not before the last save.
            Path backup = newFilePath.resolveSibling(newFilePath.getFileName() + ".bak");

            if (Files.exists(newFilePath) && !Files.exists(backup))
            {
                try
                {
                    Files.copy(newFilePath, backup);
                }
                catch (IOException backupFailed)
                {
                    // Not fatal.  A backup that cannot be written is worth less than the save that
                    // follows it, and refusing to save because of it would be the wrong trade.
                }
            }

            final String contents = data;

            org.traincontrol.util.Util.writeAtomically(newFilePath.toFile(), out ->
            {
                out.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });

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
        setEdit(true);
    }

    /**
     * Turns edit mode on or off.
     *
     * There was no way to turn it OFF, because the only thing that ever cleared it was re-parsing the
     * pages after a diagram edit.  A window that opens the editor and closes without saving - which
     * autonomy mode always does - therefore left this flag set on the SHARED diagram, and the main
     * window then rebuilt its grid in edit mode: labels wired to an editor that is not there, station
     * labels gone, and a ClassCastException on the next click.
     *
     * @param edit
     */
    public void setEdit(boolean edit)
    {
        this.edit = edit;
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
        
        // Files.newBufferedWriter for UTF-8, matching saveChanges above and the encoding
        // CS2File.fetchURL reads these files back in.  FileWriter used the platform default charset,
        // so a page name with a non-ASCII character did not survive the round trip.
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filePath)))
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
