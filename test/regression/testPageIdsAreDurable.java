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
 * A page's id is its identity, and it survives what happens to other pages: nothing one page does can change another
 * page's id (MT-135).
 *
 * The autonomy setup is keyed by page ID on disk, and ids once were each page's place in a sorted list, so any change
 * to the set of names renumbered other pages and reattached their settings to whatever page took the number.
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
     * Deleting a page leaves every other page's id alone.  Its id is retired, not handed on; a renumber after a delete
     * could not even be reported, because the old name is gone, which is also what a rename looks like.
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
     * A renamed page keeps its id: the index is read back by NAME, so only the caller can say it was a rename.
     */
    @Test
    public void testARenamedPageKeepsItsId() throws IOException
    {
        write("Alpha", "Bravo");

        // Renamed to sort FIRST: the page list arrives sorted, so every page's id would change if ids followed position
        // (MT-135).
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
     * Deleting the page holding the HIGHEST id does reissue its number, since the index is the only record.  What keeps
     * that safe is `deletePage` forgetting the page's settings first, which the next test pins.
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
     * A page that reuses a retired id inherits nothing from the page that had it.
     *
     * The index alone cannot prevent the reuse, so the safety is `deletePage` forgetting the settings.  If it stops
     * being called (as `renamePage` once was, MT-135) this fails and says what the consequence is.
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
     * A page named "1" does not collect the settings of the page whose id is 1 (OB-067).
     *
     * On disk the page part of a key is an ID and in memory a NAME; an entry for a page that is not loaded stays in id
     * form, and writing it back through the NAME map handed it to a live page called "1".  Adam ruled the name stays
     * legal: "A page should be allowed to be named 2 - let FR-013 dissolve it."  Both losses are asserted: the live
     * "1" inherits nothing, and the absent page keeps everything.
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

        // Ghost's page file goes away (an unhydrated placeholder) and a new page arrives called "1".
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha", "1")), null);

        assertNotEquals(ids().get("1"), Integer.valueOf(1),
            "the fixture needs the page NAMED 1 to hold some other id, or the collision it is here "
            + "to reproduce is not set up");

        // Loaded and saved with Ghost absent: its entry cannot be resolved to a name, so it stays in id form.
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

        // The absent page's entry is still in the file under its old id after a save made while it was away.  Asserted
        // on the FILE: a page whose file returns is a NEW page with a new id, so reading it back is another question.
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

    private File setupFile()
    {
        return new File(new File(new File(layout, "config"), "autonomy"), "setup.json");
    }

    /**
     * A retired id stays retired for longer than one write.
     *
     * `next` is derived from the ids present in the FILE, so the write after the one that drops a page would hand its
     * id to a new page.  For a page whose file was merely absent nothing forgot its settings (OB-067), so the newcomer
     * would inherit a stranger's stations, lengths and exclusions.  The index cannot record the floor itself without
     * a new field in `gleisbild.cs2`, which real hardware reads, so the autonomy setup passes it in.
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

        // Bravo's FILE goes missing (nothing told to forget it) and the index is written twice: once while it is on
        // record, once after.
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
     * The held-entries mechanism (OB-067) is one map of field names and SHAPES (whether values name squares); a wrong
     * shape cannot be caught by name-matching, only by behavior.  Put something on a page, take that page's file away,
     * load and save, and it is all still in the file.
     *
     * Each of these fails it: not merging "blockedPoints" back; downgrading `captions` from SQUARE_VALUE to PLAIN
     * (caught only because the fixture puts a caption on a LOADED page pointing at a station on the absent one);
     * removing a field from `HELD_FIELDS`.
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

        // A square on the LIVE page whose VALUE points at the absent one: without it the key side decides every entry
        // and the SHAPE half of HELD_FIELDS is never exercised.
        TileKey liveCaption = new TileKey("Alpha", 7, 7);

        // One of each shape the hold classifies, so a field dropped or misclassified shows up.
        store.setPointName(ghost, "Ghost Platform");
        store.setStation(ghost, true);
        store.setTileLength(ghost, 42);
        store.setPointName(ghostSignal, "Ghost Signal");
        store.setProtectingSignals(ghost, java.util.Arrays.asList(ghostSignal));
        store.setBlockingPoints(ghost, java.util.Arrays.asList(ghostSignal));
        store.setCaption(ghostCaption, ghost);
        store.setCaption(liveCaption, ghost);
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

        // Ghost's file goes away; nothing is told to forget it.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha")), null, store.highestPageIdSeen());

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        reopened.setPageIds(idsAsNameToId());
        reopened.load();

        // The entries really ARE held: a file that still has them passes just as well when they were never taken out.
        assertNull(reopened.getPointName(ghost),
            "an entry for a page that is not loaded is in the live collections. Holding it is what "
            + "keeps a page id out of a page-name field, so this is the mechanism not running at all");

        assertNull(reopened.getCaptionTarget(liveCaption),
            "a caption on a LOADED page pointing at a station on an absent one was not held. Its KEY "
            + "resolves, so only the value-side check can catch it - which is the shape half of "
            + "HELD_FIELDS, and the half no amount of name-matching can verify (SV-C1)");

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
     * Entries in a named collection of a written setup, counted crudely as commas plus one.
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

    private String read(File file) throws IOException
    {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * An index written in the platform's encoding is read, not refused.
     *
     * `writeLayoutIndex` once used a default-encoding `FileWriter`, so a page called "Bahnhof Süd" written by an
     * older build on Windows is not valid UTF-8.  Refusing to renumber on a failed read made the index permanently
     * unwritable, so this asserts both that the ids survive the read and that the write goes through.
     */
    @Test
    public void testAnIndexInThePlatformEncodingIsStillReadableAndWritable() throws IOException
    {
        write("Alpha", "Bahnhof Sud");

        File index = new File(new File(layout, "config"), "gleisbild.cs2");

        assertTrue(index.isFile(), "the fixture wrote no index");

        // Rewrite it as an old build would have, with one name carrying a byte that is not valid UTF-8.
        String asWritten = new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8)
            .replace("Bahnhof Sud", "Bahnhof S\u00fcd");

        Files.write(index.toPath(), asWritten.getBytes(StandardCharsets.ISO_8859_1));

        // The precondition that makes this test mean anything: strict UTF-8 really does refuse it.
        try
        {
            Files.readAllLines(index.toPath(), StandardCharsets.UTF_8);

            fail("the fixture is not actually invalid UTF-8, so this test would pass without the fix");
        }
        catch (java.nio.charset.MalformedInputException expected)
        {
        }

        Map<String, Integer> ids = ids();

        assertEquals(ids.get("Alpha"), Integer.valueOf(1),
            "the ids were lost reading an index written in the platform encoding, so every page is "
            + "about to be renumbered and every stored setting reattached (SV-B1).  Got: " + ids);

        assertEquals(ids.size(), 2, "both pages should have come back.  Got: " + ids);

        // The ACCENTED page by name; Alpha is pure ASCII, survives any decoding and so could not fail.
        assertEquals(ids.get("Bahnhof Süd"), Integer.valueOf(2),
            "the page whose name carries a non-ASCII byte did not come back under its own name, so "
            + "its id is lost and everything keyed to it is about to be reattached to whatever takes "
            + "that number. This is the failure the fallback exists for, and asserting Alpha alone "
            + "could not see it (TA-A1).  Got: " + ids);

        // And the write must go through: a retry cannot clear what it refuses on.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha", "Bahnhof S\u00fcd", "Zulu")), null, 0);

        Map<String, Integer> after = ids();

        assertEquals(after.get("Alpha"), Integer.valueOf(1),
            "Alpha was renumbered by a write that should have kept every id it could read.  Got: "
            + after);

        assertNotNull(after.get("Zulu"), "the new page was not added.  Got: " + after);

        // AND THE ACCENTED PAGE KEEPS ITS ID ACROSS THE WRITE, which is the half the guard was
        // missing and the half that costs the operator something.
        //
        // The read is asserted twenty lines up.  A write is where the loss actually happens: if the
        // name comes back mangled, `writeLayoutIndex`'s lookup of the existing entry misses it, the
        // page is issued a FRESH id, and every stored setting keyed to the old one reattaches to
        // whatever now holds that number.  That is the loss this class documents at length,
        // and it was reproducible with this guard green.
        //
        // WHAT IS AND IS NOT PROVEN HERE, because claiming more would repeat the guard's blind spot.
        //
        // The finding's mutation - swapping the ISO-8859-1 fallback for a UTF-8 decode - now turns
        // this class RED, which it did not when the finding was written.  But the assertion that
        // catches it is the READ one twenty lines above: with the fallback gone the ids never come
        // back at all, so the write is never reached.
        //
        // This assertion covers the write side, which no mutation currently exercises.  It is cheap
        // and it is correct, and a reader should know it is defensive rather than load-bearing -
        // the mutation that would prove it mangles the name only on the WRITE path, and nobody has
        // written that one.
        assertEquals(after.get("Bahnhof S\u00fcd"), Integer.valueOf(2),
            "the accented page was renumbered by the write. Its settings are now keyed to an id"
            + " nothing holds, and whatever took that number has inherited them (TA-A1 / SV-B1).  Got: "
            + after);
    }

    /**
     * Renaming a page to something that looks like another page's id moves nothing but that page.
     *
     * OB-092: renaming "Test" to "5" made the page holding id 5 (Main) excluded from autonomy.  `renamePage` left the
     * store's own numbering stale, so the next save wrote the bare NAME into `excludedPages`, which `untranslatePages`
     * reads as an ID.  No earlier rename test used a numeric page name, so the collision could not arise.  Every
     * collection is checked, and the OTHER page as hard as the renamed one.
     */
    @Test
    public void testRenamingAPageToAnotherPagesIdMovesOnlyThatPage() throws IOException
    {
        // Five pages before it, so "Main" holds id 5 and the rename below can collide with it.
        write("a", "b", "c", "d", "Main", "Test");

        assertEquals(ids().get("Main"), Integer.valueOf(5),
            "the fixture needs Main to hold id 5, since renaming the other page TO \"5\" is the "
            + "collision under test.  Got: " + ids());

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        TileKey onMain = new TileKey("Main", 3, 3);
        TileKey mainSignal = new TileKey("Main", 4, 4);
        TileKey onTest = new TileKey("Test", 7, 7);

        // Everything the store holds, on the page that must NOT move.
        store.setPointName(onMain, "Main Platform");
        store.setStation(onMain, true);
        store.setTileLength(onMain, 42);
        store.setPointName(mainSignal, "Main Signal");
        store.setProtectingSignals(onMain, java.util.Arrays.asList(mainSignal));
        store.setBlockingPoints(onMain, java.util.Arrays.asList(mainSignal));
        store.setLinkName(onMain, "Main Link");

        // And on the page being renamed, including the exclusion that broke.
        store.setPointName(onTest, "Test Platform");
        store.setStation(onTest, true);
        store.setPageExcluded("Test", true);

        store.createConfiguration("Only", null);
        store.save();

        // THE MUTATION: rename it to a string that is also a live id.
        store.renamePage("Test", "5");

        java.util.Map<String, String> renamed = new java.util.LinkedHashMap<>();
        renamed.put("Test", "5");

        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("a", "b", "c", "d", "Main", "5")),
            renamed, store.highestPageIdSeen());

        store.save();

        // THE CHECK, before reloading: the rename took, in memory.
        assertTrue(store.getExcludedPages().contains("5"),
            "the renamed page lost its exclusion in memory.  Got: " + store.getExcludedPages());

        assertFalse(store.getExcludedPages().contains("Main"),
            "renaming one page excluded another, in memory.  Got: " + store.getExcludedPages());

        AutonomyCompanionStore back = new AutonomyCompanionStore(layout);

        back.setPageIds(idsAsNameToId());
        back.load();

        // THE MUTATION IS STILL THERE.
        assertTrue(back.getExcludedPages().contains("5"),
            "the renamed page is no longer excluded after a save and load, so it has silently "
            + "rejoined autonomy (OB-092).  Got: " + back.getExcludedPages());

        assertEquals(back.getPointName(new TileKey("5", 7, 7)), "Test Platform",
            "the renamed page lost its station name.  ");

        assertTrue(back.isStation(new TileKey("5", 7, 7)),
            "the renamed page lost its station");

        // AND THE REST STAYED THE SAME. This is the half that failed on Adam's railway.
        assertFalse(back.getExcludedPages().contains("Main"),
            "the page whose ID matches the new NAME was excluded from autonomy, and it was never "
            + "touched. Everything on it then disappears from the graph, which is how this arrived: "
            + "\"the main page became excluded and lost all its train placement\" (OB-092).  Got: "
            + back.getExcludedPages());

        assertEquals(back.getPointName(onMain), "Main Platform", "Main lost its station name");
        assertTrue(back.isStation(onMain), "Main lost its station");
        assertEquals(back.getTileLength(onMain), 42, "Main lost its length");
        assertEquals(back.getPointName(mainSignal), "Main Signal", "Main lost its signal's name");
        assertEquals(back.getLinkName(onMain), "Main Link", "Main lost its link name");

        assertEquals(back.getProtectingSignals(onMain), java.util.Arrays.asList(mainSignal),
            "Main lost its protecting signal");

        assertEquals(back.getBlockingPoints(onMain), java.util.Arrays.asList(mainSignal),
            "Main lost the square that holds it back");

        // And the id itself did not move: a rename is the one thing ids exist to survive.
        assertEquals(ids().get("Main"), Integer.valueOf(5), "Main's id moved during another page's "
            + "rename, which reattaches its whole setup.  Got: " + ids());
    }

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
     * The first page's id is written out rather than assumed: CS2File reads an absent id as ZERO, and with a retired
     * id gap an omitted id would read as 1 and claim page 1's settings.
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
     * Deleting a page forgets that page's setup, and only that page's.  `renamePage` has had a caller since OB-049;
     * `deletePage` never had one, so the setup kept everything keyed to the deleted page.
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

    /**
     * The index says which pages a write would drop, counting only SURPRISES (FR-018).
     *
     * A delete and a rename both make a page absent on purpose; asking about those would make the dialog worthless in
     * the case it fires most often.
     *
     * MUTATION: dropping either the `deliberatelyRemoved` or the `renamedFromTo` test from
     * pagesTheIndexWouldDrop fails this.
     */
    @Test
    public void testTheIndexSaysWhichPagesAWriteWouldDrop() throws IOException
    {
        write("Alpha", "Bravo", "Charlie");

        // Bravo's file did not load this time.  Nobody asked for that.
        assertEquals(LayoutDiagram.pagesTheIndexWouldDrop(layout.getAbsolutePath(),
            Arrays.asList("Alpha", "Charlie"), null, null),
            Arrays.asList("Bravo"),
            "a page that vanished from the list on its own was not reported");

        // The same absence, this time because the operator deleted it.
        assertTrue(LayoutDiagram.pagesTheIndexWouldDrop(layout.getAbsolutePath(),
            Arrays.asList("Alpha", "Charlie"), null, Arrays.asList("Bravo")).isEmpty(),
            "a page the caller said it was deleting was reported as a surprise");

        // And renamed away, which is an absence under the old name by construction.
        Map<String, String> renamed = new LinkedHashMap<>();
        renamed.put("Bravo", "Bruno");

        assertTrue(LayoutDiagram.pagesTheIndexWouldDrop(layout.getAbsolutePath(),
            Arrays.asList("Alpha", "Bruno", "Charlie"), renamed, null).isEmpty(),
            "a renamed page was reported as a surprise under its old name");
    }

    /**
     * A page kept while its file is away comes back as the SAME page; a page NOT named is retired as before (MT-135),
     * and a fix that disabled that would be worse than the bug.
     *
     * MUTATION: dropping the keepAbsent loop from writeLayoutIndex fails the first assertion; making
     * it keep every absent page regardless fails the second.
     */
    @Test
    public void testAKeptPageComesBackAsItself() throws IOException
    {
        write("Alpha", "Bravo", "Charlie");

        // Bravo and Charlie are both missing from the list.  Only Bravo is coming back.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha")), null, 0, Arrays.asList("Bravo"));

        assertEquals(ids(), map("Alpha", 1, "Bravo", 2),
            "the page held back did not keep its number, or the page nobody held back kept one");

        // Bravo's file hydrates and the layout is written again with everything present.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha", "Bravo")), null, 0, null);

        assertEquals(ids(), map("Alpha", 1, "Bravo", 2),
            "a page that came back was issued a fresh id, so its settings - still keyed to 2 - now "
            + "belong to nothing");
    }

    /**
     * Holding a page back does not let it collide with a page created while it was away: the fresh number must not be
     * worked out from the list alone, or it would be the held page's.
     */
    @Test
    public void testAPageAddedWhileAnotherIsHeldDoesNotTakeItsNumber() throws IOException
    {
        write("Alpha", "Bravo");

        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha", "Delta")), null, 0, Arrays.asList("Bravo"));

        Map<String, Integer> after = ids();

        assertEquals(after.size(), 3, "a page went missing: " + after);

        assertEquals(after.get("Alpha"), (Integer) 1, "Alpha moved");
        assertEquals(after.get("Bravo"), (Integer) 2, "the held page did not keep its number");

        assertNotEquals(after.get("Delta"), after.get("Bravo"),
            "the new page took the held page's number, so both answer to it and the held page's "
            + "settings would be read as the new page's");
    }

    /**
     * Told a page is gone for good, the setup lets go of it and of nothing else (FR-018).
     *
     * Holding an absent page's settings for ever is what makes a OneDrive placeholder survivable, and it also leaves a
     * deleted page's settings in setup.json; only the person who deleted the page can tell the cases apart.
     *
     * Two absent pages, only one pruned: with one, emptying the whole hold and filtering it give the same file.
     * Asserted in order:
     *   1. An entry on a page still here whose VALUE points at the gone page goes too; releasing it into memory would
     *      put the gone page's id in a page-NAME field.
     *   2. The OTHER absent page keeps everything.
     *   3. Entries on the gone page go, and the live page's own settings stay.
     *
     * MUTATIONS, all three run and all three fail this test: checking only the key and not the value
     * leaves the caption behind; clearing the hold outright rather than filtering it takes the other
     * absent page with it; and dropping the `pageNamesWhenWritten` removal leaves the deleted page
     * still reported as one that is merely not loaded, so the operator would be asked about it again
     * on every save for ever.
     */
    @Test
    public void testAPageSaidToBeGoneStopsBeingHeld() throws IOException
    {
        write("Ghost", "Wraith", "Alpha");

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        TileKey ghost = new TileKey("Ghost", 3, 3);
        TileKey ghostLink = new TileKey("Ghost", 5, 5);

        // Absent as well, and NOT being pruned - the page that proves this filters rather than empties.
        TileKey wraith = new TileKey("Wraith", 4, 4);

        // On the staying page, so a prune that takes everything is not mistaken for the right one.
        TileKey live = new TileKey("Alpha", 8, 8);

        // On the staying page, POINTING at the going one: its key resolves; only its value names the pruned page.
        TileKey liveCaption = new TileKey("Alpha", 7, 7);

        store.setPointName(ghost, "Ghost Platform");
        store.setStation(ghost, true);
        store.setLinkName(ghostLink, "Ghost Link");
        store.setPointName(wraith, "Wraith Platform");
        store.setStation(wraith, true);
        store.setCaption(liveCaption, ghost);
        store.setPointName(live, "Alpha Platform");
        store.setStation(live, true);
        store.save();

        String before = read(setupFile());

        for (String must : new String[] {"Ghost Platform", "Ghost Link", "Wraith Platform",
            "Alpha Platform"})
        {
            assertTrue(before.contains(must),
                "the fixture did not take - " + must + " is not in the file, so this test would pass "
                + "by having written nothing.  File:\n" + before);
        }

        // Both files go away and neither is kept in the index, so the setup holds two unresolvable pages.  (A page the
        // index still lists resolves and holds nothing: that is "Keep it", tested above.)
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha")), null, store.highestPageIdSeen());

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        reopened.setPageIds(idsAsNameToId());
        reopened.load();

        // Both really are held, or the prune below is operating on an empty hold and proves nothing.
        assertNull(reopened.getPointName(ghost), "the fixture did not hold the page being pruned");
        assertNull(reopened.getPointName(wraith), "the fixture did not hold the page being kept");

        int pruned = reopened.forgetHeldPages(Arrays.asList("Ghost"));

        assertTrue(pruned > 0, "nothing was pruned, so this test has not exercised anything");

        reopened.save();

        String after = read(setupFile());

        // The caption is keyed "<Alpha's id>:7,7"; nothing else sits on 7,7.
        assertFalse(after.contains("7,7"),
            "a caption on a page that is still here, pointing at a station on the deleted page, "
            + "survived - so it is either held for ever or about to come back into memory with a page "
            + "id standing where a page name belongs.  File:\n" + after);

        // (2) The page nobody said anything about is untouched.
        assertTrue(after.contains("Wraith Platform"),
            "the OTHER absent page lost its settings.  Nothing said that page was deleted - it is a "
            + "file that has not come back yet, which is the case the whole hold exists for.  "
            + "File:\n" + after);

        // (3) The rest.
        assertFalse(after.contains("Ghost Platform"),
            "a deleted page's point name is still held, under an id no page can ever have again.  "
            + "File:\n" + after);

        assertFalse(after.contains("Ghost Link"),
            "a deleted page's link name is still held.  File:\n" + after);

        assertTrue(after.contains("Alpha Platform"),
            "the live page's own settings were pruned along with the deleted page's.  File:\n" + after);

        // The deleted page stops being reported as merely not loaded; Wraith still is.
        assertEquals(reopened.pagesNotLoaded(Arrays.asList("Alpha")), Arrays.asList("Wraith"),
            "after the prune the setup should know about exactly one page it cannot see");
    }

    /**
     * Two pages cannot come out of one write holding the same id.
     *
     * Nothing compared them: `setPageIds` inverts name-to-id so the second page silently wins the number, and
     * `pageIdConflicts` fires only when an id's name changed.  The later claimant loses; refusing the write would
     * leave a page that cannot be saved.
     *
     * MUTATION: removing the `issued` gate from the allocation loop fails this test.
     */
    @Test
    public void testTwoPagesCannotShareAnId() throws IOException
    {
        // Written by hand, since writeLayoutIndex will not produce a duplicate; a corrupt file or older build leaves
        // it.
        Files.write(new File(new File(layout, "config"), "gleisbild.cs2").toPath(),
            ("[gleisbild]\nversion\n .major=1\ngroesse\n"
            + "seite\n .id=4\n .name=Alpha\n"
            + "seite\n .id=4\n .name=Bravo\n").getBytes(StandardCharsets.UTF_8));

        Map<String, Integer> read = ids();

        assertEquals(read.get("Alpha"), read.get("Bravo"),
            "the fixture is not a duplicate, so this test proves nothing: " + read);

        // Any ordinary write - a page added, renamed, deleted - goes through here.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha", "Bravo")), null, 0);

        Map<String, Integer> after = ids();

        assertEquals(after.size(), 2, "a page went missing: " + after);

        assertEquals(after.get("Alpha"), (Integer) 4,
            "the FIRST claimant lost its id.  Its settings are keyed to 4 and would now be read as "
            + "belonging to whatever page holds that number: " + after);

        assertNotEquals(after.get("Bravo"), after.get("Alpha"),
            "two pages still answer to one id, so half of one page's setup resolves to the other and "
            + "nothing anywhere says so: " + after);
    }

    /**
     * A deleted page stops being reported as one that merely did not load.
     *
     * `deletePage` left the setup's record of what that page id was CALLED, which survives every reload, so
     * `pagesNotLoaded` named the deleted page for ever, `AutonomySession.pagesSafeToJudge` stayed false, and every
     * editor save raised a warning about it.  FR-018 cannot clear it: a deleted page is not in the index.
     *
     * MUTATION: removing the `pageNameToId` / `pageIdToName` removals from `deletePage` fails this.
     *
     * Not the `pageNamesWhenWritten` line: it is inert here, since `sharedFields` rebuilds the "pages" map from the
     * live index.
     */
    @Test
    public void testADeletedPageIsNotReportedAsMerelyMissing() throws IOException
    {
        write("Alpha", "Ghost");

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        store.setPointName(new TileKey("Ghost", 3, 3), "Ghost Platform");
        store.setPointName(new TileKey("Alpha", 4, 4), "Alpha Platform");
        store.save();

        // Deleted through the door the menu uses, in the order the menu uses it.
        store.deletePage("Ghost");
        store.save();

        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha")), null, store.highestPageIdSeen());

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        reopened.setPageIds(idsAsNameToId());
        reopened.load();

        assertTrue(reopened.pagesNotLoaded(Arrays.asList("Alpha")).isEmpty(),
            "a page that was DELETED is still reported as one that merely did not load, so the setup "
            + "can never be judged again - it will not reconcile, and it warns on every save about a "
            + "page the operator got rid of on purpose.  Got: "
            + reopened.pagesNotLoaded(Arrays.asList("Alpha")));

        assertEquals(reopened.getPointName(new TileKey("Alpha", 4, 4)), "Alpha Platform",
            "the page that stayed lost its settings, which is a different and worse bug");
    }

    /**
     * A page named after another page's id keeps its own held settings when that other page goes.
     *
     * `forgetHeldPages` matched held keys against both page NAMES and the ids written for them, so declaring the page
     * NAMED "5" gone deleted every held entry of the page with ID 5.  Names can never match a legitimate held key
     * (`pageIsHere` answers true for any bare unrecognized name), so that half only produced this false positive.
     *
     * MUTATION: putting the page names back into `parts` fails this test.
     */
    @Test
    public void testDeletingAPageNamedAfterAnIdLeavesThatIdAlone() throws IOException
    {
        // "5" is a page name; Main is the page that actually holds id 5.
        write("5", "Beta", "Gamma", "Delta", "Main");

        assertEquals(ids().get("Main"), (Integer) 5,
            "the fixture is not what it says it is: Main must hold id 5 for this to test the pun");
        assertEquals(ids().get("5"), (Integer) 1, "the page NAMED 5 must not also hold id 5");

        AutonomyCompanionStore store = new AutonomyCompanionStore(layout);

        store.setPageIds(idsAsNameToId());

        store.setPointName(new TileKey("5", 1, 1), "Five Platform");
        store.setPointName(new TileKey("Main", 2, 2), "Main Platform");
        store.save();

        // Both pages go missing, so both are HELD - which is the only state forgetHeldPages acts on.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Beta")), null, store.highestPageIdSeen());

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        reopened.setPageIds(idsAsNameToId());
        reopened.load();

        assertNull(reopened.getPointName(new TileKey("Main", 2, 2)),
            "the fixture did not take: Main's entry is not being held, so nothing below is tested");

        // Only the page NAMED "5" is gone.
        reopened.forgetHeldPages(Arrays.asList("5"));
        reopened.save();

        String after = read(setupFile());

        assertFalse(after.contains("Five Platform"),
            "the page that was actually declared gone kept its settings.  File:\n" + after);

        assertTrue(after.contains("Main Platform"),
            "deleting the page NAMED \"5\" took the settings of the page whose ID is 5.  That is the "
            + "id-as-name pun the whole hold exists to prevent, arriving through the method written "
            + "to empty it.  File:\n" + after);
    }

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
     * A page keeps its identity through a whole sequence of page operations: rename, add, delete, add again, with both
     * watched pages checked after EVERY step and the ids of surviving pages never moving.
     *
     * The defects were in what one operation did to a page it was not about, which only a sequence catches.
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

        // 1. rename a page to something that sorts to the FRONT
        Map<String, String> renamed = new LinkedHashMap<>();
        renamed.put("Bravo", "Aardvark");

        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Aardvark", "Alpha", "Charlie")), renamed);

        AutonomyCompanionStore afterRename = reload();

        assertUntouched(afterRename, onAlpha, onCharlie, alphaId, charlieId, "after renaming Bravo");

        // 2. add a page
        write("Aardvark", "Alpha", "Charlie", "Delta");

        assertUntouched(reload(), onAlpha, onCharlie, alphaId, charlieId, "after adding Delta");

        // 3. delete the page that was renamed
        AutonomyCompanionStore before = reload();

        before.deletePage("Aardvark");
        before.save();

        write("Alpha", "Charlie", "Delta");

        assertUntouched(reload(), onAlpha, onCharlie, alphaId, charlieId, "after deleting Aardvark");

        // 4. and add another, which may take the retired id
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
