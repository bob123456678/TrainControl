package regression;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.LayoutDiagram;

/**
 * A page's id is its identity, and it survives what happens to other pages.
 *
 * The autonomy setup is keyed by page ID on disk, and those ids used to be nothing but each page's
 * place in a list - a list that arrives sorted, so really its place in the alphabet. Anything that
 * changed the set of names therefore renumbered other pages, and every setting they held was silently
 * reattached to whatever page had taken the number.
 *
 * Adam, MT-135: "Immediately after rename, all stations are gone." He lost 19 point names, 14 stations,
 * 22 tile directions and 15 captions to one rename on 2026-08-23, and renaming the page back could not
 * bring them back - the following save had already pruned them as squares that no longer exist.
 *
 * So the rule these tests hold is not "the fix works" but the property the fix exists to give: nothing
 * one page does can change another page's id.
 *
 * @author Adam
 */
public class testPageIdsAreDurable
{
    private File layout;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-page-ids").toFile();

        new File(layout, "config").mkdirs();
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * Deleting a page leaves every other page's id alone.
     *
     * The id of the deleted page is retired rather than handed on. Before, the list closed up and every
     * page below moved up one - so on the next load the setup of page 3 was read as belonging to page
     * 2, whose coordinates it does not fit, and reconcile deleted the difference.
     *
     * Worse than losing them: nothing looks wrong. And the renumber could not even be REPORTED, because
     * the test for one is whether the old name still exists somewhere - and after a delete it does not,
     * which is exactly what a rename looks like.
     */
    @Test
    public void testDeletingAPageDoesNotRenumberTheOthers() throws IOException
    {
        write("Alpha", "Bravo", "Charlie");

        assertEquals(ids(), map("Alpha", 1, "Bravo", 2, "Charlie", 3),
            "the fixture is not what it says it is, so nothing below tests anything");

        // Bravo goes
        write("Alpha", "Charlie");

        assertEquals(ids(), map("Alpha", 1, "Charlie", 3),
            "deleting a page moved another page's id. Every setting on Charlie is keyed to 3, and the "
            + "next load would read it as belonging to whatever page now holds that number");
    }

    /**
     * And a renamed page keeps its id, which is the one case only the caller can know about.
     *
     * The index is read back by NAME, so after a rename the page is a name nothing has seen before. Told
     * about the rename it keeps its number; not told, it would take a fresh one and leave its entire
     * setup keyed to an id no page holds any more.
     */
    @Test
    public void testARenamedPageKeepsItsId() throws IOException
    {
        write("Alpha", "Bravo");

        // Renamed to something that sorts FIRST, which is the case that bit: the page list arrives
        // sorted, so a rename that changes a page's place in the alphabet used to change its id and
        // everybody else's. Adam renamed "1 - Main and neighbours" to "1 - Main", which moved it from
        // last to first.
        Map<String, String> renamed = new LinkedHashMap<>();
        renamed.put("Bravo", "Aardvark");

        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Aardvark", "Alpha")), renamed);

        assertEquals(ids(), map("Aardvark", 2, "Alpha", 1),
            "a renamed page did not keep its id, so its whole setup is keyed to a number that now "
            + "belongs to a different page - and the page it belongs to does not have those "
            + "coordinates, so the next save reconciles them away");
    }

    /**
     * A new page never takes an id a LIVE page is using.
     *
     * This is the property, and it is weaker than the one this test used to claim. The first version
     * said "above every id ever issued" and then deleted a MIDDLE page to prove it - which the
     * mechanism does handle, because the highest id was still in the file. Delete the page holding the
     * HIGHEST id and its number is reissued: the index is the only record, and a number that is gone
     * from it cannot be told from a number that was never used.
     *
     * Found by review, and worth stating plainly rather than papering over: what keeps that safe is
     * not the numbering but `deletePage`, which forgets the deleted page's settings before its id
     * becomes available again. The test below pins exactly that, because it is now the thing the
     * safety rests on.
     */
    @Test
    public void testANewPageNeverTakesALivePagesId() throws IOException
    {
        write("Alpha", "Bravo", "Charlie");

        write("Alpha", "Charlie");           // Bravo deleted, 2 retired

        write("Alpha", "Charlie", "Delta");  // and a new page arrives

        Map<String, Integer> now = ids();

        assertEquals(now.get("Alpha"), Integer.valueOf(1), "a surviving page moved");
        assertEquals(now.get("Charlie"), Integer.valueOf(3), "a surviving page moved");

        assertFalse(now.get("Delta").equals(now.get("Alpha"))
            || now.get("Delta").equals(now.get("Charlie")),
            "a new page was given an id a live page is using, so the two now share a setup: " + now);
    }

    /**
     * And a page that DOES reuse a retired id inherits nothing from the page that had it.
     *
     * The case above cannot be prevented by the index alone - a retired highest id looks exactly like
     * an id never issued. So the safety has to come from the other end: the deleted page's settings
     * are forgotten at the moment it goes, so there is nothing left for its number to carry.
     *
     * This is the test that matters of the two. If `deletePage` ever stops being called - and its
     * counterpart `renamePage` went weeks with no caller at all (MT-135) - this fails, and the failure
     * says what the consequence is rather than that a number changed.
     */
    @Test
    public void testAPageReusingARetiredIdInheritsNothing() throws IOException
    {
        write("Alpha", "Bravo");

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        TileKey doomed = new TileKey("Bravo", 3, 3);

        store.setPointName(doomed, "Bravo Platform");
        store.setStation(doomed, true);
        store.createConfiguration("Only", null);
        store.save();

        // Bravo goes, the way the window does it: the setup is told, and then the index is rewritten
        store.deletePage("Bravo");
        store.save();

        write("Alpha");

        // a new page arrives and takes the retired id - the case the index cannot prevent
        write("Alpha", "Zulu");

        assertEquals(ids().get("Zulu"), Integer.valueOf(2),
            "the fixture did not reuse the retired id, so this tests nothing");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(idsAsNameToId());
        reloaded.load();

        TileKey sameSquare = new TileKey("Zulu", 3, 3);

        assertNull(reloaded.getPointName(sameSquare),
            "a brand new page arrived carrying the deleted page's station name. Its number was reused "
            + "- which the index cannot help - so the only thing standing between the two was "
            + "deletePage having forgotten the old page's settings");

        assertFalse(reloaded.isStation(sameSquare),
            "a brand new page arrived already carrying a station");
    }

    /**
     * The index as the store wants it: name -> id.
     */
    private Map<String, String> idsAsNameToId()
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> page : ids().entrySet())
        {
            out.put(page.getKey(), String.valueOf(page.getValue()));
        }

        return out;
    }

    /**
     * The first page's id is written out, rather than left to be assumed.
     *
     * CS2File reads an absent id as the page's POSITION. That was harmless while ids and positions were
     * the same thing; with a retired id there is a gap, and an omitted id would read as 1 - so the first
     * page in the file would claim page 1's settings.
     */
    @Test
    public void testEveryPageWritesItsIdIncludingTheFirst() throws IOException
    {
        write("Alpha", "Bravo");

        String contents = new String(Files.readAllBytes(
            new File(layout, "config/gleisbild.cs2").toPath()), StandardCharsets.UTF_8);

        assertTrue(contents.contains(".id=1"),
            "the first page's id is not in the file, so it is read as a position - and after a delete "
            + "the first page in the file is not necessarily page 1: " + contents);

        assertEquals(contents.split("\\.id=").length - 1, 2,
            "every page must write its id, not just the ones that differ from their position: "
            + contents);
    }

    /**
     * Deleting a page forgets that page's setup, and only that page's.
     *
     * renamePage has had a caller since OB-049; its counterpart never had one. The file went, the index
     * was rewritten, and everything the setup knew about that page stayed behind keyed to a page that
     * no longer existed.
     */
    @Test
    public void testDeletingAPageForgetsItsSetupAndNothingElse() throws IOException
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        TileKey going = new TileKey("Bravo", 3, 4);
        TileKey staying = new TileKey("Alpha", 3, 4);

        store.setPointName(going, "Doomed");
        store.setStation(going, true);
        store.setTileLength(going, 7);

        store.setPointName(staying, "Survivor");
        store.setStation(staying, true);
        store.setTileLength(staying, 9);

        store.setPageExcluded("Bravo", true);

        store.createConfiguration("Only", null);
        store.getConfiguration("Only").put("points", new org.json.JSONObject()
            .put("Bravo:3,4", new org.json.JSONObject().put("home", "BR 232"))
            .put("Alpha:3,4", new org.json.JSONObject().put("home", "MY 1106")));

        int forgotten = store.deletePage("Bravo");

        assertTrue(forgotten > 0, "nothing was forgotten, so nothing below tests anything");

        assertNull(store.getPointName(going), "the deleted page's name survived the page");
        assertFalse(store.isStation(going), "the deleted page's station survived the page");
        assertEquals(store.getTileLength(going), 0, "the deleted page's length survived the page");

        assertFalse(store.getExcludedPages().contains("Bravo"),
            "the deleted page is still excluded from autonomy - and a page later created with that "
            + "name would silently start out excluded, because nothing ever prunes this");

        assertFalse(store.getConfiguration("Only").getJSONObject("points").has("Bravo:3,4"),
            "the configuration still places a locomotive on the deleted page. parseAuto answers a "
            + "point it cannot resolve by invalidating the whole layout, days later, with nothing "
            + "connecting it to the deletion");

        // and the other page is untouched
        assertEquals(store.getPointName(staying), "Survivor", "another page's name was forgotten too");
        assertTrue(store.isStation(staying), "another page's station was forgotten too");
        assertEquals(store.getTileLength(staying), 9, "another page's length was forgotten too");
        assertTrue(store.getConfiguration("Only").getJSONObject("points").has("Alpha:3,4"),
            "another page's placement was dropped too");
    }

    // ---------------------------------------------------------------------------------------------

    private void write(String... pages) throws IOException
    {
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList(pages)));
    }

    private Map<String, Integer> ids()
    {
        return LayoutDiagram.readLayoutIndexIds(layout.getAbsolutePath());
    }

    private Map<String, Integer> map(Object... pairs)
    {
        Map<String, Integer> out = new LinkedHashMap<>();

        for (int at = 0; at < pairs.length; at += 2)
        {
            out.put((String) pairs[at], (Integer) pairs[at + 1]);
        }

        return out;
    }

    private void delete(File f)
    {
        if (f == null) return;

        if (f.isDirectory())
        {
            File[] kids = f.listFiles();

            if (kids != null)
            {
                for (File kid : kids) delete(kid);
            }
        }

        f.delete();
    }
}
