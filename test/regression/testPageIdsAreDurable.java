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
     * that page's file away, load and save, and it is all still in the file.
     *
     * **What it catches, measured rather than argued.** Three mutations, each compiled and run:
     *
     * - Held but not merged back - the mutation the finding itself used - fails it, naming the
     *   collection: `the "blockedPoints" collection lost entries to one save while a page's file was
     *   missing`.
     * - A field's SHAPE downgraded, `captions` from SQUARE_VALUE to PLAIN, fails it on the caption
     *   assertion. That is the half no name-matching can check, and it only fails because the fixture
     *   puts a caption on a LOADED page pointing at a station on the absent one. With every square on
     *   the absent page the key-side check decides everything, the shape is never exercised, and all
     *   four square-valued fields could be downgraded with this test still green (SV-C1).
     * - A field removed from `HELD_FIELDS` altogether fails it too, on the same assertion.
     *
     * The version of this comment written a few hours earlier said the third of those did NOT fail,
     * and explained at length why that was acceptable. The reasoning was sound and the conclusion was
     * stale: it was true of the weaker fixture. Which is the argument for measuring a claim about a
     * test instead of deriving it.
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

        // A square on the LIVE page whose VALUE points at the absent one (SV-C1).
        //
        // Without this every square in the fixture sat on the absent page, so the key-side check alone
        // decided every entry and the SHAPE half of HELD_FIELDS was never exercised - downgrading all
        // four square-valued fields to PLAIN left this test green, while its own javadoc claimed only
        // behaviour could catch that. A caption on a loaded page pointing at a station on an absent
        // one is the ordinary way that happens, and it is the entry a wrong shape lets through.
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

        // Ghost's file goes away. Not deleted - nothing is told to forget it - which is the whole
        // point: a page that will not load is an everyday thing here.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha")), null, store.highestPageIdSeen());

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        reopened.setPageIds(idsAsNameToId());
        reopened.load();

        // The entries really ARE held, which nothing here asserted before (SV-C1). A test that only
        // checks the file still has them passes just as well when they were never taken out - and
        // "taken out" is the whole mechanism.
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
     * An index written in the platform's encoding is read, not refused.
     *
     * SV-B1. `writeLayoutIndex` used a `FileWriter` until 2026-07-27, so it wrote this file in
     * whatever encoding the machine defaulted to. Reading it strictly as UTF-8 throws
     * `MalformedInputException` on any byte that is not valid UTF-8 - and a page called
     * "Bahnhof S\u00fcd" written by an older TrainControl on a Windows box is exactly that.
     *
     * That was survivable while an unreadable index simply meant "no index": the pages were renumbered
     * and the file rewritten as UTF-8, which healed it. DR-B4 added a refusal to renumber on a failed
     * read - right for a locked file, and catastrophic here, because it made the condition permanent.
     * The index could never be written again, and the message said to try again in a moment.
     *
     * So this asserts the two things that matter and are easy to get wrong separately: the ids survive
     * the read, and the write goes through.
     */
    @Test
    public void testAnIndexInThePlatformEncodingIsStillReadableAndWritable() throws IOException
    {
        write("Alpha", "Bahnhof Sud");

        File index = new File(new File(layout, "config"), "gleisbild.cs2");

        assertTrue(index.isFile(), "the fixture wrote no index");

        // Rewrite it the way a pre-2026-07-27 build would have: the same content, with one name
        // carrying a byte that is not valid UTF-8.
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
            // what the old build's file looks like
        }

        Map<String, Integer> ids = ids();

        assertEquals(ids.get("Alpha"), Integer.valueOf(1),
            "the ids were lost reading an index written in the platform encoding, so every page is "
            + "about to be renumbered and every stored setting reattached (SV-B1).  Got: " + ids);

        assertEquals(ids.size(), 2, "both pages should have come back.  Got: " + ids);

        // The ACCENTED page by name, which is the whole point and which this test did not ask for
        // until TA-A1 (2026-08-24) demonstrated the hole: a decoder that mangles the name still
        // returns two entries with Alpha at 1, so a lenient decode that renumbers every page and
        // reattaches every setting passed this test 11 of 11.
        //
        // Alpha is the control - it is pure ASCII and survives any decoding - so asserting only Alpha
        // was asserting the half that cannot fail.
        assertEquals(ids.get("Bahnhof Süd"), Integer.valueOf(2),
            "the page whose name carries a non-ASCII byte did not come back under its own name, so "
            + "its id is lost and everything keyed to it is about to be reattached to whatever takes "
            + "that number. This is the failure the fallback exists for, and asserting Alpha alone "
            + "could not see it (TA-A1).  Got: " + ids);

        // And the write must go through. Before the fallback this threw, permanently, because the
        // condition it refuses on is one that a retry cannot clear.
        LayoutDiagram.writeLayoutIndex(layout.getAbsolutePath(),
            new ArrayList<>(Arrays.asList("Alpha", "Bahnhof S\u00fcd", "Zulu")), null, 0);

        Map<String, Integer> after = ids();

        assertEquals(after.get("Alpha"), Integer.valueOf(1),
            "Alpha was renumbered by a write that should have kept every id it could read.  Got: "
            + after);

        assertNotNull(after.get("Zulu"), "the new page was not added.  Got: " + after);
    }

    /**
     * Renaming a page to something that looks like another page's id moves nothing but that page.
     *
     * OB-092, and it is Adam's own words: "When I renamed '5 - Test' to 5, the main page (1 - Main,
     * id 5) became excluded from autonomy and lost all its train placement."
     *
     * `renamePage` rekeyed every collection and then left the store's OWN numbering stale, so it still
     * believed the page was called what it used to be. The next save asked `pageNameToId` about the
     * new name, got nothing, and fell back to writing the bare NAME into `excludedPages` - where
     * `untranslatePages` reads every value as an ID. A page called "5" came back as whichever page
     * holds id 5. The exclusion is only the visible half: an excluded page is not in the graph, so
     * every placement on it goes with it.
     *
     * **Why the existing rename tests did not catch it.** There are several, and they are thorough
     * about what a rename must carry. Not one of them renamed a page to a string that is also a live
     * id, because no fixture anywhere had a page named like a number - so the collision that makes
     * this fail could not arise. That is the same shape as TA-A1 and CR-C3: the fixture decided the
     * answer before the assertions did.
     *
     * So this checks every collection rather than the one that broke, and it checks the OTHER page as
     * hard as the renamed one. A rename that carries everything correctly and quietly empties a
     * different page is not a rename that worked.
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

        // THE LOAD.
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

    /**
     * The index can say which pages a write would drop, and does not count the ones somebody meant.
     *
     * The question FR-018 turns on. Retiring an absent page's id is correct for a page that was
     * deleted and wrong for a page whose file merely would not load, and the index cannot tell the
     * two apart - so it stops guessing and reports, and the caller asks the one participant who
     * knows. What this holds is that the report is about SURPRISES only: a delete and a rename both
     * make a page absent from the list on purpose, and asking Adam about the page he just deleted
     * would make the dialog worthless in exactly the case it fires most often.
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
     * A page kept while its file is away comes back as the SAME page.
     *
     * This is the whole of what "Keep it" buys. Without it the id is retired, and the settings that
     * are still sitting in setup.json under that number attach to nothing when the file returns -
     * Adam gets a new page with a new number and a blank setup, and nothing anywhere says why.
     *
     * The second half matters as much: a page NOT named is retired exactly as before, because that is
     * the behaviour protecting against MT-135, and a fix for one of these that quietly disabled the
     * other would be a worse bug than the one being fixed.
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
     * Holding a page back does not let it collide with a page created while it was away.
     *
     * The failure this could have introduced. A held page keeps its number; a page added in the
     * meantime is issued a fresh one, and if the fresh number were worked out from the list alone it
     * would be the held page's. Two pages on one id is the misattachment class this whole file is
     * about, arriving by a door the fix itself opened.
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
     * Told a page is gone for good, the setup lets go of it - and of nothing else (FR-018).
     *
     * The counterpart to {@link #testASaveWhileAPageIsAbsentLosesNothingOfIt}, and the two together
     * are the whole of this feature: holding an absent page's settings for ever is what makes a
     * OneDrive placeholder survivable, and it is also what leaves a genuinely deleted page's settings
     * in setup.json for ever, under an id that can never attach to anything again.
     *
     * Adam settled which of the two it is by refusing the question as posed: "if we are talking about
     * orphaned data, why not warn the user and then prune?" The application cannot tell the cases
     * apart. The person who deleted the page can.
     *
     * **Two absent pages, and only one of them is being pruned.** That is what makes the fixture worth
     * having. With a single absent page, forgetting the entire hold and forgetting the right entries
     * produce the same file, so an implementation that simply emptied everything would pass - which is
     * exactly what the first version of this test did allow.
     *
     * The three things asserted, in order of how easy they are to get wrong:
     *
     *   1. An entry anchored on a page that is STILL HERE, whose value points at the gone page, goes
     *      too. It is a pointer to nothing. Leaving it held would be the same leak; releasing it into
     *      memory would be worse, because the gone page's id would then stand in a page-NAME field -
     *      the id-as-name pun the hold exists to prevent.
     *   2. The OTHER absent page keeps everything. It is not gone, it is merely away.
     *   3. Entries anchored on the gone page go, and the live page's own settings stay.
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

        // On the page that is staying, so a prune that takes everything is not mistaken for one that
        // takes the right things.
        TileKey live = new TileKey("Alpha", 8, 8);

        // On the page that is staying, POINTING at the page that is going.  Its key resolves; only its
        // value names the page being pruned.
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

        // Both files go away, and neither is kept in the index - so the setup is holding two pages it
        // cannot resolve, which is the state a prune has to be selective inside.
        //
        // Note that keeping a page in the index is NOT this state: a page the index still lists
        // resolves, its entries load normally and there is nothing held to prune.  That is what "Keep
        // it" buys, and it is tested next door.
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

        // (1) The caption is keyed "<Alpha's id>:7,7", so its coordinates appearing anywhere means it
        // survived.  Nothing else in this fixture sits on 7,7.
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

        // And the deleted page stops being reported as one that is simply not loaded, so the operator
        // is asked about it once rather than on every save from now on.  Wraith still is.
        assertEquals(reopened.pagesNotLoaded(Arrays.asList("Alpha")), Arrays.asList("Wraith"),
            "after the prune the setup should know about exactly one page it cannot see");
    }

    /**
     * Two pages cannot come out of one write holding the same id (DR-B4).
     *
     * Nothing in the chain ever compared them. `writeLayoutIndex` wrote a duplicate straight back out;
     * `setPageIds` inverts name-to-id into id-to-name, so the second page silently wins the number and
     * the first page's settings resolve to the SECOND page's track; and `pageIdConflicts` - the one
     * thing that reports a misnumbering - only fires when an id's name has changed, which for a
     * straight duplicate it has not. Half of one page's setup attaches to another page with nothing to
     * fire.
     *
     * The later claimant is the one that loses. Refusing the write would mean a page that cannot be
     * saved, which is worse than the defect; and the first page keeps the id it has always had, so its
     * settings stay where they are. The second page's settings were already unreachable, because the
     * inversion was answering with the first page.
     *
     * MUTATION: removing the `issued` gate from the allocation loop fails this test.
     */
    @Test
    public void testTwoPagesCannotShareAnId() throws IOException
    {
        // Written by hand, because writeLayoutIndex is the thing being tested and will not produce a
        // duplicate to start from.  This is the state a corrupt file, or an older build, leaves.
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
     * A deleted page stops being reported as one that merely did not load (store review, B1).
     *
     * `deletePage` cleared the twelve collections, the excluded-page set and the configurations, and
     * left the setup's own record of what that page id was CALLED - which is written back out as the
     * file's "pages" map and therefore survives every reload.
     *
     * What that costs is not a lost setting but a store that stops working. `pagesNotLoaded` walks
     * that record, so a page the operator deliberately deleted is named for ever as one that failed to
     * load; `AutonomySession.pagesSafeToJudge` is therefore false for the rest of the layout's life;
     * and a session that cannot judge never reconciles anything again. Since DR-B10 it also raises a
     * warning dialog on every editor save, naming the page that was deleted on purpose.
     *
     * FR-018 cannot clear it either - it offers the "it was deleted" answer for pages the INDEX still
     * holds, and a deleted page is not in the index. So there is no way out of the state at all.
     *
     * `forgetHeldPages` had the line and `deletePage` did not, three hundred lines apart, which is
     * this repository's most-repeated defect shape applied to a rule invented the same day.
     *
     * MUTATION: removing `pageNamesWhenWritten.values().remove(page)` from `deletePage` fails this.
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
     * A page named after another page’s id keeps its own held settings when that other page goes.
     *
     * The id-as-name pun, arriving through the method written to close it (store review, C1).
     * `forgetHeldPages` matched held keys against a set holding both the page NAMES it was given and
     * the ids those names were written under. A page may legally be called "5" - Adam ruled it must
     * stay legal, "A page should be allowed to be named 2 - let FR-013 dissolve it" - so declaring the
     * page NAMED "5" gone deleted every held entry belonging to the page with ID 5.
     *
     * The names half could never match anything legitimate anyway: `pageIsHere` answers true for any
     * bare unrecognised name, so a name-keyed entry is never held in the first place. It could only
     * ever produce this false positive.
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
