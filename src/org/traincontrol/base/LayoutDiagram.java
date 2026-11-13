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
import java.util.Set;
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

    // The id gleisbild.cs2 gives this page.  Autonomy stores its setup against pages, and keeping the id lets that
    // setup survive a rename.
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

    // Path to the layout file
    private final String url;
    
    // Are we in edit mode?
    private boolean edit = false;
    private boolean editHideText = false;
    private boolean showAddress = false;

    /**
     * Constructor
     * @param sx size x
     * @param sy size y
     */
    public LayoutDiagram(String name, int sx, int sy, String url, ViewListener network)
    {
        this.name = name;
        this.sx = sx;
        this.sy = sy;
        this.maxx = sx;
        this.maxy = sy;
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
     * The id of this page in gleisbild.cs2, or null.  The Central Station can renumber pages, so also record the name.
     */
    public String getPageId()
    {
        return pageId;
    }

    public void setPageId(String pageId)
    {
        this.pageId = pageId;
    }

    /** The page's ID as a number, 0 when the index gave none or a damaged one (see `pageIdOrPosition`). */
    public int getPageIdNumber()
    {
        return pageIdOrPosition(this.pageId, 0);
    }

    /**
     * Gives pages that share an ID IDs of their own, all but one: the page the setup records under the ID keeps it,
     * else the first by name; the others take fresh IDs above `floor` and every one in use.  Writes nothing.
     */
    public static Map<String, Integer> dedupeIds(Map<String, Integer> nameToId, Map<Integer, String> setupNames,
        int floor)
    {
        Map<Integer, List<String>> sharing = new TreeMap<>();

        for (Map.Entry<String, Integer> each : nameToId.entrySet())
        {
            if (each.getValue() != null) sharing.computeIfAbsent(each.getValue(), k -> new ArrayList<>()).add(each.getKey());
        }

        int next = floor + 1;

        for (Integer used : sharing.keySet()) if (used >= next) next = used + 1;

        Map<String, Integer> out = new LinkedHashMap<>(nameToId);

        for (Map.Entry<Integer, List<String>> group : sharing.entrySet())
        {
            if (group.getValue().size() < 2) continue;

            List<String> names = new ArrayList<>(group.getValue());

            java.util.Collections.sort(names);

            String keeper = names.contains(setupNames.get(group.getKey())) ? setupNames.get(group.getKey()) : names.get(0);

            for (String name : names)
            {
                if (!name.equals(keeper)) out.put(name, next++);
            }
        }

        return out;
    }

    /**
     * The page names the autonomy setup records under each page ID, read from the file; empty when none or unreadable.
     */
    public static Map<Integer, String> readSetupPageNames(String path)
    {
        Map<Integer, String> out = new TreeMap<>();

        if (path == null) return out;

        try
        {
            File setup = new File(Paths.get(path, "config", "autonomy", "setup.json").toString());

            if (!setup.isFile()) return out;

            org.json.JSONObject pages = new org.json.JSONObject(
                new String(Files.readAllBytes(setup.toPath()), java.nio.charset.StandardCharsets.UTF_8)).optJSONObject("pages");

            if (pages == null) return out;

            for (String id : pages.keySet())
            {
                try
                {
                    out.put(Integer.valueOf(id), pages.getString(id));
                }
                catch (NumberFormatException | org.json.JSONException notAPage)
                {
                }
            }
        }
        catch (IOException | org.json.JSONException unreadable)
        {
            // A setup that cannot be read knows no pages; the tie is then broken by name
        }

        return out;
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
        int[] bounds = boundsFor(edit);

        minx = bounds[0];
        miny = bounds[1];
        maxx = bounds[2];
        maxy = bounds[3];
    }

    /** Whether the running diagram draws this square.  A caption on a square outside it shows in the editor only. */
    public boolean isDrawnWhenRunning(int x, int y)
    {
        int[] bounds = boundsFor(false);

        return x >= bounds[0] && x <= bounds[2] && y >= bounds[1] && y <= bounds[3];
    }

    /** The bounds of what is drawn: {first column, first row, last column, last row}. */
    private int[] boundsFor(boolean editing)
    {
        int minx;
        int miny;

        if (IGNORE_PADDING && !editing)
        {
            minx = sx;
            miny = sy;
        }
        else
        {
            minx = 0;
            miny = 0;
        }
        
        int maxx = 0;
        int maxy = 0;
        
        for (int x = 0; x < sx; x++)
        {            
            for (int y = 0; y < sy; y++)
            {
                if (this.getComponent(x, y) != null || editing) // This will persist the grid in edit mode
                {
                    if (x < minx)
                    {
                        if (IGNORE_PADDING && !editing)
                        {
                            minx = x;
                        }
                    }
                    
                    if (y < miny)
                    {
                        if (IGNORE_PADDING && !editing)
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

        // An empty page keeps both seeds; each axis falls back to 0 so LayoutGrid's size is never negative.
        if (maxx < minx) minx = 0;
        if (maxy < miny) miny = 0;

        return new int[] {minx, miny, maxx, maxy};
    }

    public int getSx()
    {
        return sx;
    }

    public int getSy()
    {
        return sy;
    }
    
    @Override
    public String toString()
    {        
        return I18n.f("layout.dimensions", this.name, Integer.toString(sx), Integer.toString(sy));
    }
    
    /**
     * Exports this layout to the CS2 format
     * @throws Exception 
     */
    public String exportToCS2TextFormat() throws Exception
    {
        StringBuilder builder = new StringBuilder();

        builder.append("[gleisbildseite]\n");

        // The left-margin keys (`page=1` on a genuine export) go first, where a station puts them; by key, not by line.
        for (Map.Entry<String, String> bare : bareKeys.entrySet())
        {
            builder.append(bare.getKey()).append("=").append(bare.getValue()).append("\n");
        }

        // The blocks above the elements are put back as read, so a later firmware's block survives a save.
        if (unmodelledBlocks.isEmpty())
        {
            builder.append("version\n .major=1\n");
        }
        else
        {
            for (Map<String, String> block : unmodelledBlocks)
            {
                builder.append(block.containsKey("_typeAsWritten")
                    ? block.get("_typeAsWritten") : block.get("_type")).append("\n");

                for (Map.Entry<String, String> entry : block.entrySet())
                {
                    if ("_type".equals(entry.getKey())) continue;

                    if ("_typeAsWritten".equals(entry.getKey())) continue;

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

        // Elements this build could not model are put back as read: saving must not lose scenery or a later component.
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

    /** Elements read from the file that no component could be made of, kept verbatim so saving does not delete them. */
    private final List<Map<String, String>> unmodelledElements = new ArrayList<>();

    /** The blocks of the file that are not elements (version, groesse, a later firmware's), in the order read. */
    private final List<Map<String, String>> unmodelledBlocks = new ArrayList<>();

    /**
     * Keys at the left margin of a page file (`page=1` on a genuine export), which the first save would otherwise
     * delete.
     */
    private final Map<String, String> bareKeys = new LinkedHashMap<>();

    /**
     * Remembers a left-margin key so it survives a save.  Kept verbatim: it may disagree with the index, and neither
     * decides anything.
     */
    public void addBareKey(String key, String value)
    {
        if (key == null || key.isEmpty() || key.startsWith("_")) return;

        bareKeys.put(key, value == null ? "" : value);
    }

    /** Remembers a block that is not an element, so saving does not delete it. */
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
     * Remembers an element this program could not model, so it survives a save; keys come back sorted, id and typ
     * first.
     */
    public void addUnmodelledElement(Map<String, String> element)
    {
        if (element == null) return;

        Map<String, String> kept = new LinkedHashMap<>();

        if (element.containsKey("id")) kept.put("id", element.get("id"));
        if (element.containsKey("typ")) kept.put("typ", element.get("typ"));

        for (Map.Entry<String, String> entry : new TreeMap<>(element).entrySet())
        {
            if ("_type".equals(entry.getKey())) continue;

            if (!kept.containsKey(entry.getKey())) kept.put(entry.getKey(), entry.getValue());
        }

        if (!kept.isEmpty()) unmodelledElements.add(kept);
    }
    
    /**
     * Gets the path to the layout file
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
     * Writes the page index and re-aims every arrow at the page it was aimed at, saving what changed.  The one place
     * the rule lives: a link holds a POSITION in the name-sorted page list, so adding, renaming, duplicating, deleting
     * or combining pages changes what every arrow means.  The OLD order comes from the pages this session holds.
     *
     * @param floor the id floor, as `writeLayoutIndex` takes it
     * @param keepAbsent pages to hold in the index though they are not loaded (FR-018), or null
     * @return the pages whose arrows changed, which the caller must save
     * @throws IOException if the index could not be written, in which case nothing was re-aimed
     */
    public static List<LayoutDiagram> writeIndexAndKeepLinksAimed(String path, List<String> layoutList,
        Map<String, String> renamed, int floor, java.util.Collection<String> keepAbsent,
        java.util.Collection<LayoutDiagram> pages) throws IOException
    {
        // Both ends are the sorted list a link's number indexes into; `before` comes from the PAGES, not `layoutList`.
        List<LayoutDiagram> changed = repointLinksForNewOrder(pages, layoutList, renamed);

        writeLayoutIndex(path, layoutList, renamed, floor, keepAbsent);

        return changed;
    }

    /**
     * Re-aims every arrow for a new page order, in memory, and says which pages changed.  Separate from the index write
     * so a caller that writes a page file in the same gesture (`LayoutPageEdit`) can re-aim first.
     *
     * @return the pages whose arrows changed, which the caller must save
     */
    public static List<LayoutDiagram> repointLinksForNewOrder(java.util.Collection<LayoutDiagram> pages,
        List<String> layoutList, Map<String, String> renamed)
    {
        List<String> before = new ArrayList<>();

        if (pages != null)
        {
            for (LayoutDiagram page : pages)
            {
                if (page != null) before.add(page.getName());
            }
        }

        java.util.Collections.sort(before);

        List<String> after = new ArrayList<>(layoutList);

        java.util.Collections.sort(after);

        if (renamed != null)
        {
            for (Map.Entry<String, String> each : renamed.entrySet())
            {
                int at = before.indexOf(each.getKey());

                if (at >= 0) before.set(at, each.getValue());
            }
        }

        return repointLinksAcross(pages, before, after);
    }

    /**
     * Repoints the links on every given page (an arrow on ANY page can point at the one that moved); returns the
     * changed pages.
     */
    public static List<LayoutDiagram> repointLinksAcross(java.util.Collection<LayoutDiagram> pages,
        List<String> before, List<String> after)
    {
        List<LayoutDiagram> changed = new ArrayList<>();

        if (pages == null) return changed;

        for (LayoutDiagram page : pages)
        {
            if (page != null && page.repointPageLinks(before, after)) changed.add(page);
        }

        return changed;
    }

    /**
     * Rewrites every link on this page so it still points at the page it pointed at.  A link holds a position in the
     * NAME-SORTED list, so the destination is identified by NAME; a deleted page leaves its arrows pointing at nothing.
     * A link that points nowhere or whose number is not in the OLD list is left alone.  Returns true when one changed.
     */
    public boolean repointPageLinks(List<String> before, List<String> after)
    {
        if (before == null || after == null) return false;

        boolean changed = false;

        for (int y = 0; y < this.getSy(); y++)
        {
            for (int x = 0; x < this.getSx(); x++)
            {
                LayoutDiagramComponent tile = this.getComponent(x, y);

                if (tile == null || !tile.isLink() || tile.linksNowhere()) continue;

                int was = tile.getRawAddress();

                if (was < 0 || was >= before.size()) continue;

                int now = after.indexOf(before.get(was));

                if (now == was) continue;

                if (now < 0) tile.setLinkTarget(null, null, -1);
                else tile.setLinkedPageIndex(now);

                changed = true;
            }
        }

        return changed;
    }

    /** Whether this page stands in for one whose file would not read. */
    private boolean unreadable = false;

    /**
     * Marks this page as standing in for one whose file would not read: it holds its place in the list and is never
     * written.
     */
    public void markUnreadable()
    {
        this.unreadable = true;
    }

    public boolean isUnreadable()
    {
        return this.unreadable;
    }

    /**
     * Saves this layout to the existing path. Should only be called if stored locally.
     * @param filename the new filename (without extension) or null to use the original filename
     * @param duplicate true to avoid deleting the original file when renaming
     * @throws Exception
     */
    public void saveChanges(String filename, boolean duplicate) throws Exception
    {
        // A placeholder is never written: its file is the one being recovered.
        if (this.unreadable) throw new Exception(I18n.f("layout.errorPageWasNotReadSoNotSaved", this.getName()));

        try
        {
            // Retrieve the export data
            String data = exportToCS2TextFormat();

            // Sanitized: the reader locates a page by the sanitized name in the index.
            String renaming = filename == null ? null
                : org.traincontrol.util.Util.sanitizeFilename(filename.trim());

            Path originalFilePath = getFilePath();
            Path newFilePath = (renaming != null && !"".equals(renaming))
                    ? originalFilePath.resolveSibling(renaming + ".cs2")
                    : originalFilePath;

            // Staged and moved into place, never truncated where it stands; a copy is kept beside it the first time a
            // page is rewritten.
            backUpOnce(newFilePath);

            final String contents = data;

            org.traincontrol.util.Util.writeAtomically(newFilePath.toFile(), out ->
            {
                out.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });

            // Not when it IS the new file: on Windows and macOS "Main" and "MAIN" name one file.
            if (filename != null && !duplicate)
            {
                if (Files.isSameFile(originalFilePath, newFilePath))
                {
                    // Only the spelling is left, through a temporary name: a move onto an "identical" path is rejected.
                    Path staged = originalFilePath.resolveSibling(renaming + ".cs2.renaming");

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

    /** Copies a page file beside itself as `.bak` the first time it is rewritten. */
    private static void backUpOnce(Path file)
    {
        Path backup = file.resolveSibling(file.getFileName() + ".bak");

        if (Files.exists(file) && !Files.exists(backup))
        {
            try
            {
                Files.copy(file, backup);
            }
            catch (IOException backupFailed)
            {
            }
        }
    }

    /**
     * Whether the rightmost column and the bottom row are both empty.
     *
     * Asked before trimming them, so a diagram is never made smaller by throwing track away.  The
     * answer is what lets the editor refuse the whole operation rather than removing what it can.
     */
    synchronized public boolean edgesAreEmpty()
    {
        if (sx < 2 || sy < 2) return false;

        for (int y = 0; y < sy; y++)
        {
            if (grid.get(sx - 1).get(y) != null) return false;
        }

        for (int x = 0; x < sx; x++)
        {
            if (grid.get(x).get(sy - 1) != null) return false;
        }

        return true;
    }

    /**
     * Removes the rightmost column and the bottom row.
     *
     * The mirror of what a single "+" adds, so the two undo each other exactly - a diagram grown by
     * one press and shrunk by the next is the diagram it started as, with every square still where it
     * was.
     *
     * Refuses unless {@link #edgesAreEmpty} - a smaller diagram must never mean less railway.
     */
    synchronized public void trimEdges() throws IOException
    {
        if (!edgesAreEmpty()) return;

        // The rightmost column
        grid.remove(sx - 1);
        sx -= 1;

        // The bottom row
        for (List<LayoutDiagramComponent> col : grid)
        {
            col.remove(col.size() - 1);
        }

        sy -= 1;

        // Nothing MOVES.  Removing only the far edges leaves every remaining square where it was,
        // which is what keeps every coordinate anything else has stored about this page - stations,
        // signal pairings, arrival restrictions, captions - pointing at the same square it did before.
        maxx = sx - 1;
        maxy = sy - 1;

        checkBounds();
    }

    /**
     * Expands the layout by the specified number of rows and columns
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
     * Turns edit mode on or off.  A window that closes the editor without saving must clear it on the SHARED diagram.
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
    
    
    /**
     * Adds a new column to the layout at the specified index and shifts all existing components one column to the right.
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

            shiftUnmodelledElements(true, startCol, true);

            this.checkBounds();
        }
    }
    
    /**
    * Adds a new row to the layout at the specified index and shifts all existing components one row downward.
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

            shiftUnmodelledElements(false, startRow, true);

           this.checkBounds();
        }
    }
    
    /**
    * Removes a row from the layout at the specified index and shifts all existing components one row upward.
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

        shiftUnmodelledElements(false, startRow, false);

        this.checkBounds();
    }
    
    /**
    * Removes a column from the layout at the specified index and shifts all existing components one column to the left.
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

        shiftUnmodelledElements(true, startCol, false);

        this.checkBounds();
    }
    
    /**
     * Moves the elements this build could not model with a shift.  Each is written back verbatim and its id IS its
     * square (x + (y shifted left 8)); one ON a removed row or column is KEPT where it is.
     */
    private void shiftUnmodelledElements(boolean columns, int start, boolean inserted)
    {
        for (int i = 0; i < unmodelledElements.size(); i++)
        {
            Map<String, String> element = unmodelledElements.get(i);

            String id = element.get("id");

            int coordinate;

            try
            {
                coordinate = id == null ? 0 : Integer.valueOf(id.replace("0x", ""), 16);
            }
            catch (NumberFormatException unreadable)
            {
                continue;
            }

            int x = coordinate % 256;
            int y = (coordinate >> 8) % 256;

            int at = columns ? x : y;

            int to = inserted ? (at >= start ? at + 1 : at) : (at > start ? at - 1 : at);

            if (to == at) continue;

            x = columns ? to : x;
            y = columns ? y : to;

        // Written as a tile's is: the id first, none at 0,0.
            Map<String, String> moved = new LinkedHashMap<>();

            if (x != 0 || y != 0) moved.put("id", "0x" + Integer.toHexString(x + (y << 8)));

            for (Map.Entry<String, String> entry : element.entrySet())
            {
                if (!"id".equals(entry.getKey())) moved.put(entry.getKey(), entry.getValue());
            }

            unmodelledElements.set(i, moved);
        }
    }

    /**
     * Writes a file with the list of all layout pages
     * We just piggyback off Marklin's CS2 format to simplify compatibility
     * @throws IOException 
     */
    public static void writeLayoutIndex(String path, List<String> layoutList) throws IOException
    {
        writeLayoutIndex(path, layoutList, null);
    }

    /**
     * What id a page has, given its `.id=` line and its position.  An absent `.id` is ZERO (the owner's ruling: *"no
     * .id means id 0 implicitly."*): reading it as the POSITION would give two pages one id.  A CORRUPT `.id=` falls
     * back to the position.
     */
    public static int pageIdOrPosition(String idText, int position)
    {
        if (idText == null) return 0;

        try
        {
            return Integer.parseInt(idText.trim());
        }
        catch (NumberFormatException notANumber)
        {
            return position;
        }
    }

    /**
     * The page id each name has in the index on disk, parsed from the FILE so the setup's keys are written back
     * unchanged.
     */
    public static Map<String, Integer> readLayoutIndexIds(String path)
    {
        unreadableIndex = null;

        Map<String, Integer> out = new java.util.LinkedHashMap<>();

        File index = new File(Paths.get(path, "config", "gleisbild.cs2").toString());

        if (!index.isFile()) return out;

        try
        {
            int position = 0;
            String idText = null;

            boolean inPage = false;

            for (String line : readIndexLines(index))
            {
                String trimmed = line.trim();

                // Which block we are in: `zuletztBenutzt` has its own `.name=` that is not a page.
                if (trimmed.length() > 0 && !trimmed.startsWith(".") && !trimmed.startsWith("["))
                {
                    // Case-insensitive, as the other readers are: `Seite` must still give pages ids.
                    inPage = "seite".equalsIgnoreCase(trimmed);

                    if (inPage)
                    {
                        position++;
                        idText = null;
                    }
                }
                else if (inPage && trimmed.toLowerCase().startsWith(".id="))
                {
                    idText = trimmed.substring(4);
                }
                else if (inPage && trimmed.toLowerCase().startsWith(".name="))
                {
                    out.put(trimmed.substring(6), pageIdOrPosition(idText, position));
                }
            }
        }
        catch (IOException e)
        {
            // An unreadable index is NOT the same as none (a sync client's lock would renumber the layout): recorded,
            // not thrown.
            unreadableIndex = e;

            return new java.util.LinkedHashMap<>();
        }

        return out;
    }

    /**
     * The keys inside a page's own `seite` block that TrainControl does not model (`.xoffset`, `.yoffset`), kept so
     * the writer can put them back.  Keyed by page NAME, not id (two pages can share an id).  Best-effort: an
     * unreadable index is reported by `readLayoutIndexIds`.
     */
    public static Map<String, List<String>> readLayoutIndexPageExtras(String path)
    {
        Map<String, List<String>> out = new LinkedHashMap<>();

        File index = new File(Paths.get(path, "config", "gleisbild.cs2").toString());

        if (!index.exists()) return out;

        try
        {
            boolean inPage = false;

            // By name: `Oles kreds` opens with a `seite` carrying `.xoffset` and no `.id`.
            String name = null;

            List<String> extras = null;

            for (String line : readIndexLines(index))
            {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) continue;

                if (!trimmed.startsWith("."))
                {
                    // A block name or the file's opening line closes the open page; held until here since `.name` may
                    // not come first.
                    attribute(out, inPage ? name : null, inPage ? extras : null);

                    inPage = "seite".equalsIgnoreCase(trimmed);

                    name = null;
                    extras = inPage ? new ArrayList<>() : null;

                    continue;
                }

                if (!inPage || extras == null) continue;

                String key = trimmed.substring(1);

                // The name keys this map and the id is reissued each write, so neither is put back.
                if (key.toLowerCase().startsWith("id=")) continue;

                if (key.toLowerCase().startsWith("name="))
                {
                    name = key.substring(5);

                    continue;
                }

                extras.add(" " + trimmed);
            }

            attribute(out, inPage ? name : null, inPage ? extras : null);
        }
        catch (IOException e)
        {
            return new LinkedHashMap<>();
        }

        return out;
    }

    /** Files one page's unmodeled keys under its name; a block with no name cannot be matched, so it is not filed. */
    private static void attribute(Map<String, List<String>> out, String name, List<String> extras)
    {
        if (name == null) return;

        if (extras != null && !extras.isEmpty()) out.put(name, extras);
    }

    /**
     * The blocks in `gleisbild.cs2` that TrainControl does not model (`zuletztBenutzt`), kept so a page edit does not
     * delete them; the keys inside a `seite` block are `readLayoutIndexPageExtras`' business.  Best-effort.
     */
    public static List<List<String>> readLayoutIndexExtras(String path)
    {
        List<List<String>> out = new ArrayList<>();

        File index = new File(Paths.get(path, "config", "gleisbild.cs2").toString());

        if (!index.exists()) return out;

        // The block names this method regenerates, matched without case (the genuine export spells it `Version`).
        final java.util.Set<String> modelled = new java.util.HashSet<>(
            java.util.Arrays.asList("version", "groesse", "seite"));

        try
        {
            List<String> current = null;

            for (String line : readIndexLines(index))
            {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) continue;

                if (trimmed.startsWith("[")) { current = null; continue; }

                if (!trimmed.startsWith("."))
                {
                    if (modelled.contains(trimmed.toLowerCase()))
                    {
                        current = null;
                    }
                    else
                    {
                        current = new ArrayList<>();
                        current.add(trimmed);
                        out.add(current);
                    }

                    continue;
                }

                if (current != null) current.add(" " + trimmed);
            }
        }
        catch (IOException e)
        {
            // Nothing: an unreadable index is reported by readLayoutIndexIds, and the save has already stopped.
            return new ArrayList<>();
        }

        return out;
    }

    /** The index block that marks a local layout whose arrows go by `.zielseite` (FR-175); 3.0.8 keeps it unread. */
    private static final String LINKS_BY_ID_BLOCK = "traincontrol";

    private static final String LINKS_BY_ID_KEY = " .linksByPageId=1";

    /** Whether a local layout's arrows go by the page ID they carry; until marked, by `.artikel`, as 3.0.8 reads it. */
    public static boolean linksGoByPageId(String path)
    {
        if (path == null) return false;

        for (List<String> block : readLayoutIndexExtras(path))
        {
            if (block.get(0).equalsIgnoreCase(LINKS_BY_ID_BLOCK) && block.contains(LINKS_BY_ID_KEY)) return true;
        }

        return false;
    }

    /**
     * Marks the layout's index as going by page ID: the marker block goes before the first page, every other byte as it
     * was.
     */
    public static void markLinksByPageId(String path) throws IOException
    {
        File index = new File(Paths.get(path, "config", "gleisbild.cs2").toString());

        String text = new String(Files.readAllBytes(index.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1);

        String nl = text.contains("\r\n") ? "\r\n" : "\n";

        String marker = LINKS_BY_ID_BLOCK + nl + LINKS_BY_ID_KEY + nl;

        java.util.regex.Matcher page = java.util.regex.Pattern.compile("(?im)^seite\\r?$").matcher(text);

        final String marked = page.find() ? text.substring(0, page.start()) + marker + text.substring(page.start())
            : text + (text.isEmpty() || text.endsWith("\n") ? "" : nl) + marker;

        org.traincontrol.util.Util.writeAtomically(index, out ->
            out.write(marked.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
    }

    /**
     * A page file's text with each arrow's `.zielseite` set to what `idFor` gives for its keys, and `.artikel` kept
     * as `.zielartikel`, or both taken out where it gives null.  Other lines stay as they were.
     *
     * @return the text, unchanged when no arrow's ID changed
     */
    public static String withLinkPageIds(String text, java.util.function.Function<Map<String, String>, Integer> idFor)
    {
        List<String> lines = java.util.Arrays.asList(text.split("\n", -1));

        List<String> out = new ArrayList<>();

        int start = 0;

        while (start < lines.size())
        {
            int end = start + 1;

            while (end < lines.size() && !lines.get(end).replace("\r", "").matches("^[a-zA-Z]+$")) end++;

            out.addAll(arrowWithId(lines.subList(start, end), idFor));

            start = end;
        }

        return String.join("\n", out);
    }

    /** One block of `withLinkPageIds`, its `.zielseite` set when it is an arrow. */
    private static List<String> arrowWithId(List<String> block, java.util.function.Function<Map<String, String>, Integer> idFor)
    {
        if (!block.get(0).replace("\r", "").equalsIgnoreCase("element")) return block;

        Map<String, String> keys = new java.util.HashMap<>();

        int last = 0;
        int artikel = -1;

        for (int i = 1; i < block.size(); i++)
        {
            String line = block.get(i).replace("\r", "");

            if (!line.matches("^ \\.[a-z0-9A-Z]+=.+$")) continue;

            String[] parts = line.substring(2).split("=", 2);

            keys.put(parts[0], parts[1]);

            if (!parts[0].equals("zielseite") && !parts[0].equals("zielartikel")) last = i;

            if (parts[0].equals("artikel")) artikel = i;
        }

        if (artikel >= 0) last = artikel;

        String nl = block.get(0).endsWith("\r") ? "\r" : "";

        if (!"pfeil".equals(keys.get("typ"))) return block;

        Integer id = idFor.apply(keys);

        List<String> out = new ArrayList<>();

        int at = 0;

        for (int i = 0; i < block.size(); i++)
        {
            String line = block.get(i).replace("\r", "");

            if (!line.startsWith(" .zielseite=") && !line.startsWith(" .zielartikel=")) out.add(block.get(i));

            if (i == last) at = out.size();
        }

        if (id != null)
        {
            out.add(at, " .zielseite=" + id + nl);

            out.add(at + 1, " .zielartikel=" + LayoutDiagramComponent.artikelNumber(keys.get("artikel")) + nl);
        }

        return out;
    }

    /**
     * Writes into this page's file the page ID each arrow holds in memory (FR-175's one-time conversion); returns how
     * many.
     */
    public int writeLinkPageIdsIntoFile() throws Exception
    {
        if (this.unreadable) return 0;

        Path file = getFilePath();

        String before = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1);

        int[] given = {0};

        final String after = withLinkPageIds(before, keys ->
        {
            Integer id = null;

            try
            {
                int coord = keys.get("id") == null ? 0 : Integer.valueOf(keys.get("id").replace("0x", ""), 16);

                LayoutDiagramComponent tile = coord % 256 < getSx() && (coord >> 8) % 256 < getSy()
                    ? getComponent(coord % 256, (coord >> 8) % 256) : null;

                if (tile != null && tile.isLink()) id = tile.getLinkPageId();
            }
            catch (NumberFormatException unreadCoordinate)
            {
            }

            if (id != null) given[0]++;

            return id;
        });

        if (!after.equals(before))
        {
            backUpOnce(file);

            org.traincontrol.util.Util.writeAtomically(file.toFile(), out ->
                out.write(after.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)));
        }

        return given[0];
    }

    /**
     * The index file's lines, decoded by something that will not refuse them: older builds wrote the platform's
     * encoding, so UTF-8 can throw and ISO-8859-1 is the fallback.  A name that decodes differently renumbers its page.
     *
     * @throws IOException only when the file genuinely cannot be read
     */
    private static List<String> readIndexLines(File index) throws IOException
    {
        try
        {
            return java.nio.file.Files.readAllLines(index.toPath(),
                java.nio.charset.StandardCharsets.UTF_8);
        }
        catch (java.nio.charset.MalformedInputException notUtf8)
        {
            return java.nio.file.Files.readAllLines(index.toPath(),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        }
    }

    /** Why the last index read failed, or null.  Static and best-effort, not a lock: `writeLayoutIndex` reads it. */
    private static volatile IOException unreadableIndex;

    /** Whether the last call to readLayoutIndexIds failed to read the file at all; the failure, or null. */
    public static IOException getUnreadableIndex()
    {
        return unreadableIndex;
    }

    /**
     * Writes the index, optionally told that a page has been renamed.
     *
     * @param renamedFromTo old name -> new name so a renamed page keeps its id, or null
     */
    public static void writeLayoutIndex(String path, List<String> layoutList,
        Map<String, String> renamedFromTo) throws IOException
    {
        writeLayoutIndex(path, layoutList, renamedFromTo, 0);
    }

    /**
     * The same, with a floor below which a fresh id will not be issued: a retired id is free again, fine for a DELETED
     * page but not one whose file was merely absent.  The floor comes from the setup; zero when none.
     */
    public static void writeLayoutIndex(String path, List<String> layoutList,
        Map<String, String> renamedFromTo, int floor) throws IOException
    {
        writeLayoutIndex(path, layoutList, renamedFromTo, floor, null);
    }

    /**
     * Which pages this index holds that a write would drop (FR-018).  Retiring the id of a missing page is deliberate
     * (MT-135), but a deleted page looks like one whose file will not parse; the caller asks the user and answers
     * through `keepAbsent`.
     *
     * @param renamedFromTo old name -&gt; new name, whose OLD names are not absences
     * @param deliberatelyRemoved names the caller is removing on purpose; may be null
     * @return the names nobody has accounted for, in index order
     */
    public static List<String> pagesTheIndexWouldDrop(String path, List<String> layoutList,
        Map<String, String> renamedFromTo, java.util.Collection<String> deliberatelyRemoved)
    {
        List<String> dropped = new java.util.ArrayList<>();

        Map<String, Integer> existing = readLayoutIndexIds(path);

        if (existing.isEmpty()) return dropped;

        for (String known : existing.keySet())
        {
            if (layoutList != null && layoutList.contains(known)) continue;

            if (deliberatelyRemoved != null && deliberatelyRemoved.contains(known)) continue;

            if (renamedFromTo != null && renamedFromTo.containsKey(known)) continue;

            dropped.add(known);
        }

        return dropped;
    }

    /**
     * The lines of a page's `seite` block this writer does not model, written back with it; `renamedFromTo` may be
     * null.
     */
    private static void appendPageExtras(StringBuilder contents,
        Map<String, List<String>> pageExtras, String name, Map<String, String> renamedFromTo)
    {
        if (pageExtras == null || name == null) return;

        List<String> extras = pageExtras.get(name);

        if (extras == null && renamedFromTo != null)
        {
            for (Map.Entry<String, String> renamed : renamedFromTo.entrySet())
            {
                if (name.equals(renamed.getValue())) extras = pageExtras.get(renamed.getKey());
            }
        }

        if (extras == null) return;

        for (String line : extras) contents.append(line).append("\n");
    }

    /**
     * The same, told which absent pages to hold on to rather than retire (FR-018): `keepAbsent` (null or empty for the
     * old behavior) names pages coming back, written with the id they already have.
     */
    public static void writeLayoutIndex(String path, List<String> layoutList,
        Map<String, String> renamedFromTo, int floor,
        java.util.Collection<String> keepAbsent) throws IOException
    {
        // Ensure the directory exists
        File directory = new File(Paths.get(path, "config").toString());
        if (!directory.exists())
        {
            directory.mkdirs();
        }

        // Construct the file path
        String filePath = Paths.get(path, "config", "gleisbild.cs2").toString();
        
        // Built in memory and written atomically, as saveChanges does; UTF-8, as CS2File.fetchURL reads these files.
        StringBuilder contents = new StringBuilder();

        contents.append("[gleisbild]\n");
        contents.append("version\n");
        contents.append(" .major=1\n");
        contents.append("groesse\n");

        // Whatever else the station put here (`zuletztBenutzt`), after the modeled header and before the pages.
        for (List<String> block : readLayoutIndexExtras(path))
        {
            for (String line : block) contents.append(line).append("\n");
        }

        // Each page keeps the id it had: an id is a page's IDENTITY (MT-135), kept while it is listed, retired when it
        // goes.
        Map<String, Integer> existing = readLayoutIndexIds(path);

        Map<String, List<String>> pageExtras = readLayoutIndexPageExtras(path);

        // Refuse to renumber the layout because the index could not be READ (a sync client may hold it open).
        if (existing.isEmpty() && getUnreadableIndex() != null)
        {
            // Only for a file that cannot be READ (an undecodable one falls back to a total charset).
            throw new IOException("The layout index could not be read, so the pages were not renumbered."
                + "  Nothing was written.  Try again in a moment: " + getUnreadableIndex().getMessage());
        }

        // Pages sharing an id are told apart by the rule, not by where the caller's list puts them.
        existing = dedupeIds(existing, readSetupPageNames(path), floor);

        int next = floor + 1;

        for (Integer taken : existing.values())
        {
            if (taken != null && taken >= next) next = taken + 1;
        }

        // Every id issued in THIS write, so two pages cannot leave holding one: the LATER page gets a number of its
        // own.
        Set<Integer> issued = new java.util.LinkedHashSet<>();

        for (String layout : layoutList)
        {
            Integer id = existing.get(layout);

            if (id == null && renamedFromTo != null)
            {
                for (Map.Entry<String, String> renamed : renamedFromTo.entrySet())
                {
                    if (layout.equals(renamed.getValue())) id = existing.get(renamed.getKey());
                }
            }

            if (id == null) id = next++;

            while (!issued.add(id)) id = next++;

            contents.append("seite\n");

            // Always written: an absent id reads as ZERO (see `pageIdOrPosition`).
            contents.append(" .id=").append(id).append("\n");
            contents.append(" .name=").append(layout).append("\n");

            appendPageExtras(contents, pageExtras, layout, renamedFromTo);
        }

        // Pages the operator said are coming back (FR-018), after the ones that are here; only a name with an id
        // already.
        if (keepAbsent != null)
        {
            for (String held : keepAbsent)
            {
                Integer id = existing.get(held);

                if (id == null || layoutList.contains(held)) continue;

                if (!issued.add(id)) continue;

                contents.append("seite\n");
                contents.append(" .id=").append(id).append("\n");
                contents.append(" .name=").append(held).append("\n");

                appendPageExtras(contents, pageExtras, held, renamedFromTo);
            }
        }

        final byte[] bytes = contents.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        org.traincontrol.util.Util.writeAtomically(new File(filePath), out -> out.write(bytes));
    }
}
