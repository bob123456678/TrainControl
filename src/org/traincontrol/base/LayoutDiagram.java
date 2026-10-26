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
     * The id of this page in gleisbild.cs2, or null if the index did not give one.  Reordering pages on the Central
     * Station can renumber them, so anything storing against it should record the name too and check the two agree.
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
        int[] bounds = boundsFor(edit);

        minx = bounds[0];
        miny = bounds[1];
        maxx = bounds[2];
        maxy = bounds[3];
    }

    /**
     * Whether the running diagram draws this square: the part of the page its components cover, whatever mode the page
     * is in.  A caption filed on a square outside it is drawn in the editor and never when trains run.
     *
     * @param x the column
     * @param y the row
     * @return true when the running grid draws it
     */
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

        // A page with nothing on it keeps both seeds (only a square that HOLDS something lowers minx); each axis falls
        // back to 0 on its own, since the array LayoutGrid builds from maxx - minx + 2 would otherwise go negative.
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

        // The left-margin keys (`page=1` on a genuine export) are written back first, where a station puts them.  By
        // key and rule, not by line: a repeated key keeps its last value and one below the blocks comes back above
        // them.
        for (Map.Entry<String, String> bare : bareKeys.entrySet())
        {
            builder.append(bare.getKey()).append("=").append(bare.getValue()).append("\n");
        }

        // The blocks above the elements are put back as read, so anything beyond .major or a later firmware's block
        // survives a save; the hardcoded header is the fallback for a page built in memory.
        if (unmodelledBlocks.isEmpty())
        {
            builder.append("version\n .major=1\n");
        }
        else
        {
            for (Map<String, String> block : unmodelledBlocks)
            {
                // The spelling the file used (`Version` stays `Version`), where it differed from the lower-cased form.
                builder.append(block.containsKey("_typeAsWritten")
                    ? block.get("_typeAsWritten") : block.get("_type")).append("\n");

                for (Map.Entry<String, String> entry : block.entrySet())
                {
                    if ("_type".equals(entry.getKey())) continue;

                    // Bookkeeping of ours, not a key the file had.
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

        // The elements this build could make nothing of, put back as read.  Saving regenerates the page from the model
        // without a save button, so losing scenery or a later firmware's component is not a trade anybody agreed to.
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
     * Keys at the left margin of a page file, between the header and the first block.  A genuine export has exactly
     * one, `page=1`, which `CS2File.parseFileContents` would otherwise drop and the first save delete.
     */
    private final Map<String, String> bareKeys = new LinkedHashMap<>();

    /**
     * Remembers a key at the left margin of a page file, so that it survives a save.
     *
     * Preserved verbatim, and it may disagree with the index: `writeLayoutIndex` can reissue the page's id, so `page=1`
     * may sit beside `.id=8`.  Neither number decides anything (pages sort by name; the id keys the autonomy setup), so
     * rewriting the line from the id would assert an ordering the program does not use.
     *
     * @param key the name, `page` on a real export
     * @param value what follows the equals sign
     */
    public void addBareKey(String key, String value)
    {
        if (key == null || key.isEmpty() || key.startsWith("_")) return;

        bareKeys.put(key, value == null ? "" : value);
    }

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
     * Remembers an element this program could not model, so that it survives a save.  The keys come back sorted (the
     * parser's map is unordered), id and typ first as the Central Station writes them.
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
     * Writes the page index and re-aims every arrow at the page it was aimed at, saving what changed.
     *
     * The one place the rule lives: a link tile holds a POSITION in the name-sorted page list, so adding, renaming,
     * duplicating, deleting or combining pages changes what every arrow means, and the re-aim is the one step a caller
     * could get wrong unnoticed.  The OLD order comes from the pages this session holds, not the index file (`layoutDB`
     * is keyed by name, so an index naming two pages alike aliases them onto one entry).
     *
     * A rename carries the arrows with it; a page that has gone leaves its arrows pointing at nothing (see
     * `LayoutDiagramComponent.setLinkedPageIndex`).
     *
     * @param path the layout folder
     * @param layoutList the new page list, name-sorted
     * @param renamed old name -&gt; new name, or null
     * @param floor the id floor, as `writeLayoutIndex` takes it
     * @param keepAbsent pages to hold in the index though they are not loaded (FR-018), or null
     * @param pages every page of the layout, whose arrows are re-aimed
     * @return the pages whose arrows changed, which the caller must save
     * @throws IOException if the index could not be written, in which case nothing was re-aimed
     */
    public static List<LayoutDiagram> writeIndexAndKeepLinksAimed(String path, List<String> layoutList,
        Map<String, String> renamed, int floor, java.util.Collection<String> keepAbsent,
        java.util.Collection<LayoutDiagram> pages) throws IOException
    {
        // Both ends are the sorted list, the only thing a link's number indexes into: `layoutList` is not it (a rename
        // keeps the old slot, add/duplicate/combine append), and `before` comes from the PAGES so two pages with one
        // name do not make it longer than the list the arrows index into.
        List<LayoutDiagram> changed = repointLinksForNewOrder(pages, layoutList, renamed);

        writeLayoutIndex(path, layoutList, renamed, floor, keepAbsent);

        return changed;
    }

    /**
     * Re-aims every arrow for a new page order, in memory, and says which pages changed.
     *
     * Separate from the index write because a caller that writes a page file in the same gesture (`LayoutPageEdit`:
     * Add, Duplicate, Rename) must re-aim before it writes; `writeIndexAndKeepLinksAimed` is the whole job for Delete
     * and Combine.  Both ends are sorted, and `before` comes from the PAGES, not from any list.
     *
     * @param pages every page of the layout
     * @param layoutList the new page list, in whatever order the caller holds it
     * @param renamed old name -&gt; new name, or null
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

        // A rename is the same page under another name: substituted on the OLD list, which an arrow's number indexes.
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
     * Repoints the links on every given page, and says which pages changed: an arrow on ANY page can point at the one
     * that moved.
     *
     * @param pages every page of the layout
     * @param before the page order the arrows were written against - the OLD page list, sorted
     * @param after the page order as it now is
     * @return the pages whose arrows changed, which are the ones the caller has to save
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
     * Rewrites every link on this page so it still points at the page it pointed at before.
     *
     * A link holds a position in the NAME-SORTED page list, so any change to that list would silently change what the
     * arrow means.  The destination is identified by NAME across the change; a page not in the new list (deleted)
     * leaves its arrows pointing at nothing rather than at whatever slid into its place.  A link that already points
     * nowhere, or whose number is not in the OLD list, is left alone.
     *
     * @param before the page list as it was, in the order the links were written against
     * @param after the page list as it now is
     * @return true when at least one link on this page changed, so the caller knows to save it
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

                tile.setLinkedPageIndex(now);

                changed = true;
            }
        }

        return changed;
    }

    /** Whether this page stands in for one whose file would not read. */
    private boolean unreadable = false;

    /**
     * Marks this page as standing in for one whose file would not read.  It holds the page's place in the list so every
     * link after it still resolves, and it must never be written: it is blank and the file it would go over is the
     * page the operator is trying to recover.
     */
    public void markUnreadable()
    {
        this.unreadable = true;
    }

    /** @return true when this page stands in for one whose file would not read */
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
        // A placeholder is never written: it is blank, and the file it would go over is the one the operator is trying
        // to get back.  Refused here, at the one method that writes a page.
        if (this.unreadable)
        {
            throw new Exception(I18n.f("layout.errorPageWasNotReadSoNotSaved", this.getName()));
        }

        try
        {
            // Retrieve the export data
            String data = exportToCS2TextFormat();

            // Determine the file path.
            // Sanitized because the reader locates a page by the sanitized name in the index; a slash would be read by
            // resolveSibling as a directory.
            String renaming = filename == null ? null
                : org.traincontrol.util.Util.sanitizeFilename(filename.trim());

            Path originalFilePath = getFilePath();
            Path newFilePath = (renaming != null && !"".equals(renaming))
                    ? originalFilePath.resolveSibling(renaming + ".cs2")
                    : originalFilePath;

            // Staged and moved into place, never truncated where it stands: pages are rewritten by things the user did
            // not ask for (the caption migration), so a crash mid-write must not lose the page.  A copy of what was
            // there is kept beside it the first time a page is rewritten, so a migration that strips something is
            // recoverable.
            Path backup = newFilePath.resolveSibling(newFilePath.getFileName() + ".bak");

            if (Files.exists(newFilePath) && !Files.exists(backup))
            {
                try
                {
                    Files.copy(newFilePath, backup);
                }
                catch (IOException backupFailed)
                {
                    // Not fatal: a backup that cannot be written is worth less than the save that follows.
                }
            }

            final String contents = data;

            org.traincontrol.util.Util.writeAtomically(newFilePath.toFile(), out ->
            {
                out.write(contents.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            });

            // Delete the original unless it IS the new file: on Windows and macOS "Main" and "MAIN" name one file, and
            // deleting the "original" would delete the only copy of the page.
            if (filename != null && !duplicate)
            {
                if (Files.isSameFile(originalFilePath, newFilePath))
                {
                    // The bytes are already right; only the spelling is left, through a temporary name because a direct
                    // move onto a path the filesystem considers identical is rejected.
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
     * Turns edit mode on or off.  A window that opens the editor and closes without saving must clear the flag, or it
     * stays set on the SHARED diagram and the main window rebuilds its grid in edit mode.
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

            // Unmodelled elements shift too.
            shiftUnmodelledElements(true, startCol, true);

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

            // Unmodelled elements shift too.
            shiftUnmodelledElements(false, startRow, true);

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

        // Unmodelled elements shift too.
        shiftUnmodelledElements(false, startRow, false);

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

        // Unmodelled elements shift too.
        shiftUnmodelledElements(true, startCol, false);

        this.checkBounds();
    }
    
    /**
     * Moves the elements this build could not model with a shift, as the shift moves the tiles.
     *
     * Such an element is written back verbatim, its id with it, and the id IS its square: x + (y << 8).  Inserting at
     * `start` moves everything at or past it one further on; removing `start` moves everything past it one back.  An
     * element ON a removed row or column is KEPT where it is rather than dropped, since this program cannot draw it and
     * nobody removing the row could see it was there.  An id that does not read as `CS2File` reads one is left alone.
     *
     * @param columns true for a shift along x (right, left), false for one along y (down, up)
     * @param start the first column or row the shift moved, after the shift's own normalizing
     * @param inserted true for a shift that inserts (right, down), false for one that removes (left, up)
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
                // AS `CS2File` READS IT: hex after the 0x, and none meaning 0,0
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

            // WRITTEN AS A TILE'S IS: the id first, and none at 0,0 - and the rest of the element exactly as it was
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
     * @param path
     * @param layoutList
     * @throws IOException 
     */
    public static void writeLayoutIndex(String path, List<String> layoutList) throws IOException
    {
        writeLayoutIndex(path, layoutList, null);
    }

    /**
     * What id a page in the index has, given what its `.id=` line said and where it sits.
     *
     * An absent `.id` is ZERO (the owner's ruling: *"no .id means id 0 implicitly."*), the CS2 convention and what the
     * files say.  Reading it as the page's POSITION would give two pages one id, and one page's autonomy settings would
     * come back on the other's track.  Both this file and `CS2File` ask this, so the setup is not keyed by ids the
     * index does not believe.
     *
     * A CORRUPT `.id=` line falls back to the position: the file is damaged rather than terse, and zero would give
     * every damaged page the same id.
     *
     * @param idText what the `.id=` line said, or null when the page had none
     * @param position the page's place in the file, counting from one
     * @return the id to use
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
     * The page id each name has in the index as it stands on disk, parsed from the FILE rather than the running model
     * so the ids the autonomy setup is keyed by are written back unchanged.  An absent id reads as ZERO (see
     * `pageIdOrPosition`).
     *
     * @param path the layout folder
     * @return name -> id, empty when there is no index yet
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

                // Which block we are in, not just whether a line looks like a page: the genuine export opens with
                // `zuletztBenutzt`, whose own `.name=` must not be read as a page at position 0.
                if (trimmed.length() > 0 && !trimmed.startsWith(".") && !trimmed.startsWith("["))
                {
                    // Case-insensitive, as the other two readers of this file are: an index spelling the block `Seite`
                    // would otherwise give no page an id, and a write would reissue 1..n across the layout.
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
                    // Through the shared rule rather than inline.
                    out.put(trimmed.substring(6), pageIdOrPosition(idText, position));
                }
            }
        }
        catch (IOException e)
        {
            // An unreadable index is NOT the same as not having one: with no ids to keep, writeLayoutIndex would
            // renumber the whole layout after a transient lock by a sync client.  Not thrown (a page nobody can save is
            // worse than a renumber that recovers), but recorded so the caller is told.
            unreadableIndex = e;

            return new java.util.LinkedHashMap<>();
        }

        return out;
    }

    /**
     * The keys inside a page's own `seite` block that TrainControl does not model, such as the `.xoffset` and
     * `.yoffset` a Central Station writes, kept so the writer can put them back.
     *
     * Keyed by the page's NAME, not its id: two pages can resolve to one id (the genuine export here has such a pair).
     * The writer is handed the rename map so a renamed page still finds its own lines.  The `.id` line is not read
     * here.
     *
     * Best-effort: an unreadable index is already reported by `readLayoutIndexIds`, and this must not add a second way
     * to fail a save.
     *
     * @param path the layout folder
     * @return each page NAME against the lines of its block the writer does not emit, in file order
     */
    public static Map<String, List<String>> readLayoutIndexPageExtras(String path)
    {
        Map<String, List<String>> out = new LinkedHashMap<>();

        File index = new File(Paths.get(path, "config", "gleisbild.cs2").toString());

        if (!index.exists()) return out;

        try
        {
            boolean inPage = false;

            // Keyed by the page's name, not its id: `Oles kreds` in the genuine export opens with a `seite` carrying
            // `.xoffset=1`, `.yoffset=3` and no `.id`, and the next page states `.id=1`, so keyed by id one would lose.
            String name = null;

            List<String> extras = null;

            for (String line : readIndexLines(index))
            {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) continue;

                if (!trimmed.startsWith("."))
                {
                    // A block name, or the file's own opening line, closes whatever page was open.  Held until here
                    // rather than emitted at `.name`, since nothing promises the name comes first.
                    attribute(out, inPage ? name : null, inPage ? extras : null);

                    inPage = "seite".equalsIgnoreCase(trimmed);

                    name = null;
                    extras = inPage ? new ArrayList<>() : null;

                    continue;
                }

                if (!inPage || extras == null) continue;

                String key = trimmed.substring(1);

                // The two keys the writer emits for itself.  The name is what this map is keyed by and
                // the id is reissued on every write, so neither is a line to put back.
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

    /**
     * Files one page's unmodeled keys under its name.  A block with no name cannot be matched to a page on the way back
     * out, so it is not filed.
     *
     * @param out what has been filed so far
     * @param name the page's name, or null when there is no page open
     * @param extras its unmodeled lines, or null
     */
    private static void attribute(Map<String, List<String>> out, String name, List<String> extras)
    {
        if (name == null) return;

        if (extras != null && !extras.isEmpty()) out.put(name, extras);
    }

    /**
     * The blocks in `gleisbild.cs2` that TrainControl does not model (the genuine export carries `zuletztBenutzt`),
     * kept so a page edit does not delete them: what TrainControl does not understand it is not entitled to throw away.
     *
     * Not kept here: `[gleisbild]`, `version`, `groesse` and `seite`, which the writer regenerates from what the layout
     * is now.  The other keys inside a `seite` block (the scroll position) are `readLayoutIndexPageExtras`' business.
     *
     * Best-effort: an unreadable index is already reported by `readLayoutIndexIds`; this returns nothing rather than
     * add a second way to fail a save.
     *
     * @param path the layout folder
     * @return the unmodeled blocks, each as its lines in file order, outermost first
     */
    public static List<List<String>> readLayoutIndexExtras(String path)
    {
        List<List<String>> out = new ArrayList<>();

        File index = new File(Paths.get(path, "config", "gleisbild.cs2").toString());

        if (!index.exists()) return out;

        // The block names this method regenerates (`[gleisbild]` has its own branch), matched without regard to case
        // because the genuine export spells it `Version`; carrying one over would give the file a second copy.
        final java.util.Set<String> modelled = new java.util.HashSet<>(
            java.util.Arrays.asList("version", "groesse", "seite"));

        try
        {
            List<String> current = null;

            for (String line : readIndexLines(index))
            {
                String trimmed = line.trim();

                if (trimmed.isEmpty()) continue;

                // The file's own opening line, which the writer emits itself.
                if (trimmed.startsWith("[")) { current = null; continue; }

                // A block NAME - anything at the left margin that is not a key.
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

                // A key, kept with the block it belongs to and dropped when that block is modeled.
                if (current != null) current.add(" " + trimmed);
            }
        }
        catch (IOException e)
        {
            // Nothing, deliberately: readLayoutIndexIds reports an unreadable index and writeLayoutIndex refuses to
            // write on it, so by here the save has already stopped.
            return new ArrayList<>();
        }

        return out;
    }
    /**
     * The index file's lines, decoded by something that will not refuse them: older builds wrote it in the platform's
     * own encoding, so UTF-8 can throw `MalformedInputException`, and ISO-8859-1 is the total fallback that round-trips
     * Latin-1 bytes.  A name that decodes differently does not match, and a page that does not match is renumbered.
     *
     * @param index the file
     * @return its lines
     * @throws IOException only when the file genuinely cannot be read - missing, locked, unreadable
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

    /**
     * Why the last index read failed, or null if it did not.  Static and best-effort: `writeLayoutIndex` asks whether
     * the map it reasons from came from a file it could read.  It is not a lock.
     */
    private static volatile IOException unreadableIndex;

    /**
     * Whether the last call to readLayoutIndexIds failed to read the file at all.
     *
     * @return the failure, or null
     */
    public static IOException getUnreadableIndex()
    {
        return unreadableIndex;
    }

    /**
     * Writes the index, optionally told that a page has been renamed.
     *
     * @param path the layout folder
     * @param layoutList the pages, in the order they are to appear
     * @param renamedFromTo old name -> new name, so a renamed page keeps its id; null when nothing was renamed, and a
     *        page nobody recognizes then gets a fresh id
     * @throws IOException
     */
    public static void writeLayoutIndex(String path, List<String> layoutList,
        Map<String, String> renamedFromTo) throws IOException
    {
        writeLayoutIndex(path, layoutList, renamedFromTo, 0);
    }

    /**
     * The same, with a floor below which a fresh id will not be issued.
     *
     * A retired id is free again after the write that dropped it, which is fine for a DELETED page but not for one
     * whose file was merely absent: its settings are still in the autonomy setup under the old number, and the next new
     * page would collect them.  The floor comes from the setup, which remembers every id it has written; zero when
     * none.
     *
     * @param floor no fresh id will be at or below this
     */
    public static void writeLayoutIndex(String path, List<String> layoutList,
        Map<String, String> renamedFromTo, int floor) throws IOException
    {
        writeLayoutIndex(path, layoutList, renamedFromTo, floor, null);
    }

    /**
     * Which pages this index holds that a write would drop (FR-018).
     *
     * Retiring the id of a page not in the list is deliberate (MT-135), but it cannot tell a deleted page from one
     * whose file merely will not parse or is not there (on OneDrive an unhydrated placeholder, or a file the sync
     * client holds open): written then, the index retires the id and the page returns as a NEW page, its settings
     * stranded under the old id.  So this reports, the caller asks the user, and the answer goes back in through
     * `keepAbsent`.
     *
     * Deliberate removals are subtracted here rather than at each call site, since every caller has some and one that
     * forgot would ask about the very thing the operator just did.
     *
     * @param path the layout folder
     * @param layoutList the pages the caller can see
     * @param renamedFromTo old name -&gt; new name, whose OLD names are not absences
     * @param deliberatelyRemoved names the caller is removing on purpose, which are not absences
     *        either.  May be null
     * @return the names in the index that nobody has accounted for, in index order, empty when the
     *         picture is complete
     */
    public static List<String> pagesTheIndexWouldDrop(String path, List<String> layoutList,
        Map<String, String> renamedFromTo, java.util.Collection<String> deliberatelyRemoved)
    {
        List<String> dropped = new java.util.ArrayList<>();

        Map<String, Integer> existing = readLayoutIndexIds(path);

        // An index that could not be READ says nothing about what is absent; writeLayoutIndex refuses that case too.
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
     * The lines of a page's `seite` block that this writer does not model, written back with it.
     *
     * @param contents the file being built
     * @param pageExtras what `readLayoutIndexPageExtras` found, keyed by page name
     * @param name the page being written
     * @param renamedFromTo old name -&gt; new name, so a renamed page keeps its own lines.  May be null
     */
    private static void appendPageExtras(StringBuilder contents,
        Map<String, List<String>> pageExtras, String name, Map<String, String> renamedFromTo)
    {
        if (pageExtras == null || name == null) return;

        List<String> extras = pageExtras.get(name);

        // A rename is the one case where the writer holds a name the index has never seen; the rename map says so.
        if (extras == null && renamedFromTo != null)
        {
            for (Map.Entry<String, String> renamed : renamedFromTo.entrySet())
            {
                if (name.equals(renamed.getValue())) extras = pageExtras.get(renamed.getKey());
            }
        }

        if (extras == null) return;

        for (String line : extras)
        {
            contents.append(line).append("\n");
        }
    }

    /**
     * The same again, told which absent pages to hold on to rather than retire (FR-018).
     *
     * `keepAbsent` names pages the operator has said are coming back: they are written out with the id they already
     * have, so when the file returns it is the SAME page.  Anything absent and not named is retired, which is what
     * every caller gets by passing null (the startup path and tests are never asked anything).
     *
     * @param keepAbsent names to keep in the index though they are not in the list.  Null or empty
     *        for the old behavior
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
        
        // Built in memory and written ATOMICALLY, as saveChanges does: a write that failed part way would leave a
        // header naming no pages at all.  UTF-8, as CS2File.fetchURL reads these files, so non-ASCII page names
        // survive.
        StringBuilder contents = new StringBuilder();

        contents.append("[gleisbild]\n");
        contents.append("version\n");
        contents.append(" .major=1\n");
        contents.append("groesse\n");

        // Whatever else the station put here (the genuine export opens with `zuletztBenutzt`), kept because the header
        // is regenerated on every write: after the modeled header and before the pages, where the sample file has it.
        for (List<String> block : readLayoutIndexExtras(path))
        {
            for (String line : block)
            {
                contents.append(line).append("\n");
            }
        }

        // Write layout details, keeping the id each page already had: an id is a page's IDENTITY (MT-135), kept for
        // every page still in the list, retired when a page goes, issued fresh only for a page that never had one.  The
        // autonomy setup is keyed by it, so an id that was a page's place in the alphabet would reattach settings
        // wrongly.
        Map<String, Integer> existing = readLayoutIndexIds(path);

        // What the station wrote inside each page's block, emitted with each page below.
        Map<String, List<String>> pageExtras = readLayoutIndexPageExtras(path);

        // Refuse to renumber the whole layout because the index could not be READ: with `existing` empty every page
        // would be issued a fresh id, which is wrong when the file is there and a sync client holds it open.  The page
        // the caller wanted saved is not saved, the lesser harm.
        if (existing.isEmpty() && getUnreadableIndex() != null)
        {
            // Reachable only for a file that cannot be READ.  One this build cannot DECODE does not come here
            // (readIndexLines falls back to a total charset): refusing what a retry can fix is a delay, refusing what
            // it cannot is a layout nobody can save.
            throw new IOException("The layout index could not be read, so the pages were not renumbered."
                + "  Nothing was written.  Try again in a moment: " + getUnreadableIndex().getMessage());
        }

        // Zero is a real id: the first id issued is one above the floor and above every id already in the file.
        int next = floor + 1;

        for (Integer taken : existing.values())
        {
            if (taken != null && taken >= next) next = taken + 1;
        }

        // Every id issued in THIS write, so two pages cannot leave holding one: `setPageIds` inverts name->id, so a
        // duplicate would make the second page silently win.  Reissued rather than refused (a page that cannot be saved
        // is worse); the LATER page loses and gets a number of its own.
        Set<Integer> issued = new java.util.LinkedHashSet<>();

        for (String layout : layoutList)
        {
            Integer id = existing.get(layout);

            // A rename is the one case where a page keeps its id under a new name, and only the caller knows.
            if (id == null && renamedFromTo != null)
            {
                for (Map.Entry<String, String> renamed : renamedFromTo.entrySet())
                {
                    if (layout.equals(renamed.getValue())) id = existing.get(renamed.getKey());
                }
            }

            if (id == null) id = next++;

            // Taken already, by a page written a moment ago in this same loop.
            while (!issued.add(id))
            {
                id = next++;
            }

            contents.append("seite\n");

            // ALWAYS written: an absent id reads as ZERO (see `pageIdOrPosition`), so omitting it would silently give a
            // page with another id the id 0.
            contents.append(" .id=").append(id).append("\n");
            contents.append(" .name=").append(layout).append("\n");

            appendPageExtras(contents, pageExtras, layout, renamedFromTo);
        }

        // Pages the operator said are coming back (FR-018), after the ones that are here (position means nothing now
        // that ids are identities).  Only a name the index already has an id for; inventing one for a page nobody can
        // see is the opposite of the point.
        if (keepAbsent != null)
        {
            for (String held : keepAbsent)
            {
                Integer id = existing.get(held);

                if (id == null || layoutList.contains(held)) continue;

                // The same gate as the loop above: a held-back page whose id has since been issued would duplicate it.
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
