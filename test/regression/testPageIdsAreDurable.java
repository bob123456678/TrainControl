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
     * A page named "1" does not collect the settings of the page whose id is 1.
     *
     * OB-067, and the thing FR-013 named as its correctness bar. The keys are "page:x,y" strings, and
     * on disk the page part is an ID while in memory it is a NAME. Both halves of the translation are
     * string lookups, so each rests on being handed the kind of string it expects - and the code said
     * as much: "ids are numeric and names are not, so the two never collide". `validateLayoutName`
     * allows digits, so they do. Adam ruled the name stays legal: "A page should be allowed to be
     * named 2 - let FR-013 dissolve it."
     *
     * The reachable way in is a page that is not loaded, which is an ordinary thing on this railway -
     * `pagesNotLoaded` exists because a OneDrive placeholder or a file held by the sync client is
     * enough for CS2File to skip a page. An entry belonging to an absent page cannot be translated to
     * a name, so it stays in the file's own id form in memory, waiting to be written back untouched.
     * Writing it back then ran that id through the NAME map - and if a live page happens to be called
     * "1", the absent page's settings were handed to it.
     *
     * Both directions of the loss matter and both are asserted: the live page named "1" must not
     * inherit anything, and the absent page must still have everything when its file comes back.
     *
     * The shape Adam asked these to take: a mutation, a check, a save, a load, and verification that
     * the mutation is still there while the rest stays the same.
     */
    @Test
    public void testAPageNamedAfterAnotherPagesIdCollectsNothing() throws IOException
    {
        write("Ghost", "Alpha");

        assertEquals(ids().get("Ghost"), Integer.valueOf(1),
            "the fixture needs Ghost to hold id 1, since the collision under test is with a page "
            + "later named \"1\"");

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        TileKey onGhost = new TileKey("Ghost", 3, 3);
        TileKey onAlpha = new TileKey("Alpha", 4, 4);

        store.setPointName(onGhost, "Ghost Platform");
        store.setPointName(onAlpha, "Alpha Platform");
        store.save();

        // Ghost's page file goes away - a placeholder that never hydrated, which is the case
        // pagesNotLoaded was written for - and a new page arrives called "1".
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha", "1")), null);

        assertNotEquals(ids().get("1"), Integer.valueOf(1),
            "the fixture needs the page NAMED 1 to hold some other id, or the collision it is here "
            + "to reproduce is not set up");

        // Loaded and saved with Ghost absent, which is all it takes: the entry cannot be resolved to
        // a name, so it stays in id form, and the save is where it used to be handed away.
        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        reopened.setPageIds(idsAsNameToId());
        reopened.load();
        reopened.save();

        AutonomyCompanionStore after = new AutonomyCompanionStore(layout);

        after.setPageIds(idsAsNameToId());
        after.load();

        assertNull(after.getPointName(new TileKey("1", 3, 3)),
            "the page named \"1\" came back holding the station of the page whose ID is 1. Its name "
            + "was run through the map of page names on the way to disk, and an absent page's whole "
            + "setup was handed to whichever live page happened to be called after its number "
            + "(OB-067)");

        assertEquals(after.getPointName(onAlpha), "Alpha Platform",
            "Alpha's own station did not survive a save with another page absent");

        // And the other half, which is the one that matters: the absent page's entry is still in the
        // file, under the id it was written with, after a save that happened while it was away.
        //
        // Asserted on the FILE rather than by bringing the page back and reading it. Bringing it back
        // is a separate question with a separate answer: writeLayoutIndex retires the id of a page
        // that is not in the list, so a page whose file disappears and returns is a NEW page with a
        // new id, and its old entries stay behind under the old one. That is the id system working as
        // designed - it is what stops a later page inheriting them - but it means a page that goes
        // away and comes back does not automatically pick its settings up again, which is worth
        // knowing and is not this test's subject.
        String written = new String(Files.readAllBytes(setupFile().toPath()), StandardCharsets.UTF_8);

        assertTrue(written.contains("1:3,3"),
            "the absent page's station is no longer in the file at all. One save while its page was "
            + "missing was enough to drop it - the same loss the page-id work was done for, arriving "
            + "through absence rather than through a rename.  File:\n" + written);

        assertTrue(written.contains("Ghost Platform"),
            "the absent page's key survived but its value did not.  File:\n" + written);

        assertEquals(after.getPointName(onAlpha), "Alpha Platform",
            "and Alpha must be untouched throughout");
    }

    /**
     * Where the shared setup is written.
     */
    private File setupFile()
    {
        return new File(new File(new File(layout, "config"), "autonomy"), "setup.json");
    }

    /**
     * A retired id stays retired for longer than one write.
     *
     * IAR-A1, found by an independent review. The closing comment on
     * `testAPageReusingARetiredIdInheritsNothing` says a reused id is "the case the index cannot
     * prevent". The index can prevent it, and until now it did so for exactly one write.
     *
     * `next` is derived from the ids present in the FILE. The write that drops a page still sees its
     * id, so nothing takes it that time; the write after that does not, and hands it to the next new
     * page. For a page that was DELETED that is harmless - `deletePage` forgets its settings first,
     * which is what the sibling test pins. For a page whose FILE was merely absent it is the worst
     * thing this application does: nothing forgot anything, the settings sit in setup.json under that
     * id held verbatim (OB-067), and a brand-new page inherits a stranger's stations, lengths and
     * exclusions with nothing reporting a renumber - because as far as the index is concerned, none
     * happened.
     *
     * A page whose file will not load is ordinary here. CS2File skips one that will not parse or is
     * not there and says so; on this railway, which lives in OneDrive, an unhydrated placeholder is
     * enough.
     *
     * The index cannot remember this by itself without a new field in `gleisbild.cs2`, which real
     * Maerklin hardware reads. The autonomy setup can, and does - so it is passed in as a floor.
     */
    @Test
    public void testARetiredIdIsNotHandedOutTwoWritesLater() throws IOException
    {
        write("Alpha", "Bravo");

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        TileKey onBravo = new TileKey("Bravo", 3, 3);

        store.setPointName(onBravo, "Bravo Platform");
        store.setStation(onBravo, true);
        store.createConfiguration("Only", null);
        store.save();

        assertEquals(ids().get("Bravo"), Integer.valueOf(2), "the fixture did not take");

        int floor = store.highestPageIdSeen();

        assertEquals(floor, 2,
            "the setup does not remember Bravo's id, so it cannot be asked to protect it. That "
            + "record is the only thing standing between an absent page and the next new one");

        // Bravo's FILE goes missing - not deleted, nothing told to forget it - and the index is
        // written twice, which is all it takes: once while it is still on record, once after.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha")), null, floor);

        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha", "Zulu")), null, floor);

        assertNotEquals(ids().get("Zulu"), Integer.valueOf(2),
            "a brand new page was handed the id of a page whose file is merely missing. Its settings "
            + "are still in setup.json under that number, held there precisely because nobody deleted "
            + "them - so Zulu comes up carrying another page's stations, and nothing reports a "
            + "renumber because none happened (IAR-A1)");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);

        reloaded.setPageIds(idsAsNameToId());
        reloaded.load();

        assertNull(reloaded.getPointName(new TileKey("Zulu", 3, 3)),
            "the new page came up holding the absent page's station name");

        assertFalse(reloaded.isStation(new TileKey("Zulu", 3, 3)),
            "the new page came up already carrying a station");
    }

    /**
     * Every held field is declared, and a save with a page absent loses none of them.
     *
     * DR-A1. The held-entries mechanism (OB-067) carried the store's twelve shared collections' field
     * names in three hand-written lists - four shape-classified arrays in `withoutAbsentPages` and a
     * merge array inside `sharedFields` - and no test governed any of them. A reviewer removed one
     * string, `"blockedPoints"` from the merge array, and nothing else: all three ratchets stayed
     * green while one save with a page's file missing deleted that page's FR-001 restrictions from
     * disk.
     *
     * The lists are one map now, so they cannot disagree by count. This is the other half, and it is
     * the half with teeth: the map also encodes each field's SHAPE - whether its values name squares -
     * and a field held with the wrong shape cannot be caught by any amount of name-matching. Only
     * behaviour catches it.
     *
     * The property is the one OB-067 was closed on, stated per field: put something on a page, take
     * that page's file away, load and save, and it is all still in the file. A store that is missing a
     * field from the hold loses it here; one that is missing it from the merge loses it here; one that
     * holds it with the wrong shape loses the half of it that names a square somewhere else.
     */
    @Test
    public void testASaveWhileAPageIsAbsentLosesNothingOfIt() throws IOException
    {
        write("Ghost", "Alpha");

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        TileKey ghost = new TileKey("Ghost", 3, 3);
        TileKey ghostSignal = new TileKey("Ghost", 4, 4);
        TileKey ghostFar = new TileKey("Ghost", 5, 5);
        TileKey ghostCaption = new TileKey("Ghost", 6, 6);

        // One of each shape the hold classifies, so a field dropped or misclassified shows up.
        store.setPointName(ghost, "Ghost Platform");
        store.setStation(ghost, true);
        store.setTileLength(ghost, 42);
        store.setPointName(ghostSignal, "Ghost Signal");
        store.setProtectingSignals(ghost, java.util.Arrays.asList(ghostSignal));
        store.setBlockingPoints(ghost, java.util.Arrays.asList(ghostSignal));
        store.setCaption(ghostCaption, ghost);
        store.setLinkName(ghostFar, "Ghost Link");
        store.pairPortals(ghost, ghostFar);
        store.setPortalDisabled(ghostFar, true);
        store.setPageExcluded("Ghost", true);
        store.createConfiguration("Only", null);
        store.save();

        String before = read(setupFile());

        for (String must : new String[] {"Ghost Platform", "Ghost Signal", "Ghost Link"})
        {
            assertTrue(before.contains(must),
                "the fixture did not take - " + must + " is not in the file, so this test would pass "
                + "by having written nothing.  File:\n" + before);
        }

        // Ghost's file goes away. Not deleted - nothing is told to forget it - which is the whole
        // point: a page that will not load is an everyday thing here.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha")), null, store.highestPageIdSeen());

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        reopened.setPageIds(idsAsNameToId());
        reopened.load();
        reopened.save();

        String after = read(setupFile());

        // Every field, by the value that could only have come from Ghost.
        assertTrue(after.contains("Ghost Platform"),
            "the point name of a page whose file is missing was dropped by one save (DR-A1). "
            + "File:\n" + after);

        assertTrue(after.contains("Ghost Signal"),
            "the protecting signal's name was dropped.  File:\n" + after);

        assertTrue(after.contains("Ghost Link"),
            "the link name was dropped.  File:\n" + after);

        assertTrue(after.contains("\"42\"") || after.contains(": 42"),
            "the tile length was dropped.  File:\n" + after);

        // And the structural ones, by counting entries rather than by name.
        for (String field : new String[] {"stations", "stationSignals", "blockedPoints", "portals",
            "captions", "excludedPages"})
        {
            assertTrue(countIn(after, field) >= countIn(before, field),
                "the \"" + field + "\" collection lost entries to one save while a page's file was "
                + "missing. Nothing deleted that page, so nothing should have forgotten it - and this "
                + "is exactly the loss the held-entries mechanism exists to prevent (DR-A1)."
                + "\n\nBefore:\n" + before + "\n\nAfter:\n" + after);
        }
    }

    /**
     * How many entries a named collection holds in a written setup, without parsing it as JSON.
     *
     * Deliberately crude: it counts the commas plus one inside the field's braces or brackets, which
     * is enough to tell "lost some" from "kept them" and does not need the file's shape to be stable.
     */
    private int countIn(String setup, String field)
    {
        int at = setup.indexOf('"' + field + '"');

        if (at < 0) return 0;

        int open = at;

        while (open < setup.length() && setup.charAt(open) != '{' && setup.charAt(open) != '[') open++;

        if (open >= setup.length()) return 0;

        char closer = setup.charAt(open) == '{' ? '}' : ']';
        int depth = 0;
        int entries = 0;

        for (int i = open; i < setup.length(); i++)
        {
            char c = setup.charAt(i);

            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']')
            {
                depth--;

                if (depth == 0)
                {
                    return setup.substring(open, i).trim().length() <= 1 ? 0 : entries + 1;
                }
            }
            else if (c == ',' && depth == 1) entries++;
        }

        return entries;
    }

    /**
     * A file as text.
     */
    private String read(File file) throws IOException
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
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

    /**
     * A page keeps its identity through a whole sequence of page operations.
     *
     * Adam, after testing MT-142: "This seems to work, but add a thorough test case for it since you
     * already know what should happen."
     *
     * The tests above each isolate one operation. This one does what a person does - rename, add,
     * delete, add again - and checks after EVERY step that the two pages he cares about still carry
     * everything they had, and that the ids of surviving pages never moved.
     *
     * It is written this way because the defects this month were not in any single operation. They were
     * in what one operation did to a page it was not about: a rename renumbered other pages, a delete
     * handed a retired id to a newcomer, a page that failed to load was pruned by a save meant for
     * something else. A sequence is the only shape that catches those.
     */
    @Test
    public void testPageIdentitySurvivesASequenceOfOperations() throws IOException
    {
        write("Alpha", "Bravo", "Charlie");

        Map<String, Integer> issued = ids();

        assertEquals(issued, map("Alpha", 1, "Bravo", 2, "Charlie", 3), "the fixture");

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        TileKey onAlpha = new TileKey("Alpha", 2, 2);
        TileKey onCharlie = new TileKey("Charlie", 5, 5);

        store.setPointName(onAlpha, "Alpha Platform");
        store.setStation(onAlpha, true);
        store.setTileLength(onAlpha, 12);

        store.setPointName(onCharlie, "Charlie Platform");
        store.setStation(onCharlie, true);
        store.setTileLength(onCharlie, 7);

        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");
        place(store.getConfiguration("Only"), "Alpha:2,2", "BR 232");
        place(store.getConfiguration("Only"), "Charlie:5,5", "MY 1106");

        store.save();

        int alphaId = issued.get("Alpha");
        int charlieId = issued.get("Charlie");

        // --- 1. rename a page to something that sorts to the FRONT -------------------------------
        Map<String, String> renamed = new LinkedHashMap<>();
        renamed.put("Bravo", "Aardvark");

        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Aardvark", "Alpha", "Charlie")), renamed);

        AutonomyCompanionStore afterRename = reload();

        assertUntouched(afterRename, onAlpha, onCharlie, alphaId, charlieId, "after renaming Bravo");

        // --- 2. add a page -----------------------------------------------------------------------
        write("Aardvark", "Alpha", "Charlie", "Delta");

        assertUntouched(reload(), onAlpha, onCharlie, alphaId, charlieId, "after adding Delta");

        // --- 3. delete the page that was renamed --------------------------------------------------
        AutonomyCompanionStore before = reload();

        before.deletePage("Aardvark");
        before.save();

        write("Alpha", "Charlie", "Delta");

        assertUntouched(reload(), onAlpha, onCharlie, alphaId, charlieId, "after deleting Aardvark");

        // --- 4. and add another, which may take the retired id -------------------------------------
        write("Alpha", "Charlie", "Delta", "Echo");

        assertUntouched(reload(), onAlpha, onCharlie, alphaId, charlieId, "after adding Echo");
    }

    /**
     * The two pages the sequence is not about must be untouched, every time.
     */
    private void assertUntouched(AutonomyCompanionStore check, TileKey onAlpha, TileKey onCharlie,
        int alphaId, int charlieId, String when)
    {
        assertEquals(check.getPointName(onAlpha), "Alpha Platform", "Alpha's name, " + when);
        assertTrue(check.isStation(onAlpha), "Alpha's station, " + when);
        assertEquals(check.getTileLength(onAlpha), 12, "Alpha's length, " + when);

        assertEquals(check.getPointName(onCharlie), "Charlie Platform", "Charlie's name, " + when);
        assertTrue(check.isStation(onCharlie), "Charlie's station, " + when);
        assertEquals(check.getTileLength(onCharlie), 7, "Charlie's length, " + when);

        assertTrue(check.getConfiguration("Only").getJSONObject("points").has("Alpha:2,2"),
            "Alpha's placement, " + when);
        assertTrue(check.getConfiguration("Only").getJSONObject("points").has("Charlie:5,5"),
            "Charlie's placement, " + when);

        Map<String, Integer> now = ids();

        assertEquals(now.get("Alpha"), Integer.valueOf(alphaId),
            "Alpha's id moved " + when + " - which reattaches its whole setup to another page");
        assertEquals(now.get("Charlie"), Integer.valueOf(charlieId),
            "Charlie's id moved " + when + " - which reattaches its whole setup to another page");
    }

    /**
     * The store as it comes back off disk, under the numbering the index now has.
     */
    private AutonomyCompanionStore reload() throws IOException
    {
        AutonomyCompanionStore fresh = new AutonomyCompanionStore(layout);

        fresh.setPageIds(idsAsNameToId());
        fresh.load();

        return fresh;
    }

    private void place(org.json.JSONObject configuration, String key, String loc)
    {
        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        configuration.getJSONObject("points").put(key, new org.json.JSONObject()
            .put("loc", new org.json.JSONObject().put("name", loc)));
    }
}
