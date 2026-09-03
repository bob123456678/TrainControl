package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;

/**
 * The autonomy setup files: what the diagram cannot say, kept beside the diagram it describes.
 *
 * Nothing geometric lives here - shape is re-derived from the track every build - so these tests are
 * about the handful of human decisions that would otherwise be lost, and about the ways a diagram can
 * change underneath them.
 *
 * No hardware, no UI: a temporary folder and files.
 *
 * @author Adam
 */
public class testAutonomyDiagramStore
{
    private File layout;
    private AutonomyCompanionStore store;

    @BeforeMethod
    public void setUp() throws IOException
    {
        layout = Files.createTempDirectory("tc-autonomy-store").toFile();
        store = new AutonomyCompanionStore(layout);
    }

    @AfterMethod
    public void tearDown()
    {
        delete(layout);
    }

    /**
     * A layout nobody has set autonomy up on loads as empty rather than failing.  That is the state every
     * layout starts in, so it cannot be an error.
     */
    @Test
    public void testALayoutWithNoSetupLoadsEmpty() throws IOException
    {
        assertTrue(store.isUsable());
        assertFalse(store.exists());

        store.load();

        assertTrue(store.getConfigurationNames().isEmpty());
        assertNull(store.getActiveConfiguration());
    }

    /**
     * A direction belongs to one route across a square, and which route survives being written down.
     *
     * FR-013 stage two. `tileDirections` is keyed by a square AND a route across it - a crossing or a
     * switch carries several, and each has its own direction - and until this conversion that key was
     * the string "page:x,y#state,index", written and read back verbatim. Verbatim is why nothing
     * needed to test it: whatever went out came back, correct or not.
     *
     * A typed key has to be PARSED on the way in, which is a new thing that can be wrong. It was found
     * by breaking it: swapping the two route numbers in `readDirectionMap` passed all 64 tests in this
     * class and all 9 in the settings matrix, because every fixture in the repository used
     * `RouteId(0, 0)` - a pair that reads the same either way round.
     *
     * So this uses routes whose two numbers differ AND differ from each other's, on ONE square, which
     * is the case the compound key exists for. A store that lost the route half would answer the same
     * direction for both.
     *
     * MUTATIONS, both fail this test: swapping `route[0]` and `route[1]` in `readDirectionMap`; and
     * keying by the square alone.
     */
    @Test
    public void testTwoRoutesAcrossOneSquareKeepTheirOwnDirections() throws IOException
    {
        TileKey crossing = new TileKey("1 - Main", 6, 6);

        // Deliberately asymmetric, and deliberately each other's mirror.
        RouteId straight = new RouteId(1, 3);
        RouteId turning = new RouteId(3, 1);

        store.setTileDirection(crossing, straight, Direction.TOWARD_A);
        store.setTileDirection(crossing, turning, Direction.TOWARD_B);

        assertEquals(store.getTileDirection(crossing, straight), Direction.TOWARD_A,
            "the two routes are sharing one entry before anything has even been written - the key is "
            + "not distinguishing them at all");

        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        assertEquals(reloaded.getTileDirection(crossing, straight), Direction.TOWARD_A,
            "the direction of route 1,3 came back wrong - the route half of the key did not survive "
            + "being written down and read back");

        assertEquals(reloaded.getTileDirection(crossing, turning), Direction.TOWARD_B,
            "the direction of route 3,1 came back wrong.  It is route 1,3 reversed, so a reader that "
            + "swaps the two numbers answers this one with the other one's direction");

        // And a route that was never given a direction still has none, so the two above are not
        // simply the same answer handed to everybody.
        assertNull(reloaded.getTileDirection(crossing, new RouteId(2, 2)),
            "a route nobody set a direction for has one, so the key is answering more broadly than it "
            + "should");
    }

    /**
     * Autonomy needs somewhere to put its files, and a layout read from the Central Station has no folder
     * until it is downloaded.  The store says so rather than pretending to save.
     */
    @Test
    public void testALayoutWithNoFolderCannotHoldASetup()
    {
        AutonomyCompanionStore nowhere = new AutonomyCompanionStore(null);

        assertFalse(nowhere.isUsable());
        assertFalse(nowhere.exists());

        try
        {
            nowhere.save();
            fail("saving without a layout folder should fail rather than silently do nothing");
        }
        catch (IOException expected)
        {
            // and it names the reason, so the UI can offer the download
            assertTrue(expected.getMessage().contains("autosetup"), expected.getMessage());
        }
    }

    /**
     * Everything a person decided survives a round trip.  This is the whole job of the class.
     */
    @Test
    public void testEveryAuthoredDecisionSurvivesARoundTrip() throws IOException
    {
        TileKey station = new TileKey("1 - Main", 4, 7);
        TileKey plain = new TileKey("1 - Main", 5, 7);
        TileKey linkA = new TileKey("1 - Main", 9, 1);
        TileKey linkB = new TileKey("2 - Bottom", 2, 3);

        store.setPointName(station, "Track 14 entrance");
        store.setStation(station, true);
        store.setTileLength(plain, 42);
        store.setTileDirection(plain, new RouteId(0, 0), Direction.TOWARD_A);
        store.setLinkName(linkA, "to the yard");
        store.pairPortals(linkA, linkB);
        store.setPageExcluded("4 - Combined", true);
        store.createConfiguration("Evening", null);
        store.setActiveConfiguration("Evening");

        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        assertEquals(reloaded.getPointName(station), "Track 14 entrance");
        assertTrue(reloaded.isStation(station));
        assertEquals(reloaded.getTileLength(plain), 42);
        assertEquals(reloaded.getTileDirection(plain, new RouteId(0, 0)), Direction.TOWARD_A);
        assertEquals(reloaded.getLinkName(linkA), "to the yard");
        assertEquals(reloaded.getPortalPartner(linkA), linkB);
        assertEquals(reloaded.getPortalPartner(linkB), linkA, "a pairing is mutual");
        assertTrue(reloaded.getExcludedPages().contains("4 - Combined"));
        assertEquals(reloaded.getActiveConfiguration(), "Evening");
    }

    /**
     * A default is never written as though it were a choice.  A tile with no length assigned means zero
     * anyway, so storing it would add noise to the file and, worse, make a later change of default look
     * like a decision somebody made.
     */
    @Test
    public void testDefaultsAreNotStored() throws IOException
    {
        TileKey tile = new TileKey("1 - Main", 3, 3);

        store.setTileLength(tile, 5);
        store.setTileLength(tile, 0);
        store.setTileDirection(tile, new RouteId(0, 0), Direction.NONE);
        store.setTileDirection(tile, new RouteId(0, 0), null);
        store.createConfiguration("Default", null);

        store.save();

        String written = new String(Files.readAllBytes(
            new File(layout, "config/autonomy/setup.json").toPath()), StandardCharsets.UTF_8);

        assertFalse(written.contains("1 - Main:3,3"),
            "a tile back at its default should leave nothing behind:\n" + written);
    }

    /**
     * Duplicating a configuration takes everything, because a configuration exists to differ in where the
     * locomotives are - starting blank would mean re-entering every decision that has nothing to do with
     * that.
     */
    @Test
    public void testDuplicatingAConfigurationTakesEverything() throws IOException
    {
        store.createConfiguration("Morning", null);
        store.getConfiguration("Morning").put("placements", new org.json.JSONObject().put("Loc1", "A"));
        store.getConfiguration("Morning").put("globals", new org.json.JSONObject().put("minDelay", 7));

        store.createConfiguration("Evening", "Morning");

        assertEquals(store.getConfiguration("Evening").getJSONObject("globals").getInt("minDelay"), 7);
        assertEquals(store.getConfiguration("Evening").getJSONObject("placements").getString("Loc1"), "A");

        // and it is a copy, not a shared reference - editing one must not edit the other
        store.getConfiguration("Evening").getJSONObject("globals").put("minDelay", 1);

        assertEquals(store.getConfiguration("Morning").getJSONObject("globals").getInt("minDelay"), 7,
            "duplicating must copy, not alias");
    }

    /**
     * Deleting the configuration that is running leaves another one running.
     *
     * This used to also assert that the LAST one could not be deleted, which is no longer true: refusing
     * it made setting autonomy up a one-way door, so a layout somebody had experimented on kept a
     * configuration for ever.  What survives is the half that still holds - the store must never be
     * left pointing at something it has just deleted.  The empty case is testTheLastConfigurationMayBeDeleted.
     */
    @Test
    public void testDeletingTheRunningConfigurationLeavesAnotherRunning() throws IOException
    {
        store.createConfiguration("Only", null);
        store.createConfiguration("Second", "Only");

        store.setActiveConfiguration("Only");
        store.deleteConfiguration("Only");

        assertEquals(store.getConfigurationNames().size(), 1);
        assertEquals(store.getActiveConfiguration(), "Second",
            "deleting the active configuration should leave another one active");
    }

    /**
     * Renaming a configuration does not leave the old file behind, which would come back as a duplicate
     * on the next load.
     */
    /**
     * A configuration file that cannot be parsed leaves the loaded setup exactly as it was.
     *
     * The failure has to arrive as an IOException, because that is what every caller of load catches -
     * discardEdits and open both promise that a failed load changes nothing, and a bare JSONException
     * walks straight out through them.  And the store must still hold what it held, rather than the
     * half of it that was refilled before the bad file was reached.
     */
    @Test
    public void testACorruptConfigurationChangesNothing() throws IOException
    {
        store.createConfiguration("Morning", null);
        store.createConfiguration("Evening", null);
        store.setActiveConfiguration("Morning");
        store.setStation(new TileKey("main", 2, 3), true);
        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        assertEquals(reloaded.getConfigurationNames().size(), 2, "precondition: both were written");

        // One of them is now unreadable
        File broken = new File(new File(layout, "config" + File.separator + "autonomy"),
            "configuration-Evening.json");

        assertTrue(broken.isFile(), "precondition: the file this test corrupts must exist");

        Files.write(broken.toPath(), "{ not json at all".getBytes(StandardCharsets.UTF_8));

        try
        {
            reloaded.load();

            fail("a corrupt configuration must not load quietly");
        }
        catch (IOException expected)
        {
            assertTrue(String.valueOf(expected.getMessage()).contains("Evening"),
                "the message must name the file, or the user cannot act on it: "
                    + expected.getMessage());
        }

        // and the store is untouched - not emptied, and not half refilled
        assertEquals(reloaded.getConfigurationNames().size(), 2,
            "a failed load emptied the store it was supposed to leave alone");

        assertEquals(reloaded.getActiveConfiguration(), "Morning");

        assertTrue(reloaded.isStation(new TileKey("main", 2, 3)),
            "the shared settings went with it");
    }

    @Test
    public void testRenamingAConfigurationDoesNotLeaveTheOldFile() throws IOException
    {
        store.createConfiguration("Morning", null);
        store.setActiveConfiguration("Morning");
        store.save();

        store.renameConfiguration("Morning", "Evening");
        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        assertEquals(reloaded.getConfigurationNames().size(), 1);
        assertEquals(reloaded.getConfigurationNames().get(0), "Evening");
        assertEquals(reloaded.getActiveConfiguration(), "Evening");
    }

    /**
     * Discarding an edit takes a signal pairing with it.
     *
     * load() empties the store and reads the file over it, and the emptying missed the station
     * signals - while readShared only PUTS what the file holds, so an entry the file says nothing
     * about was never overwritten.  A pairing made and then thrown away therefore survived the
     * discard, and the next save wrote it to disk.  From then on a real signal was thrown to red on
     * real hardware for a pairing the user had cancelled.
     *
     * The file has to hold NO signal for that station, which is what makes this different from an
     * edit that changes one: a changed entry was overwritten by the read and looked fine.
     */
    @Test
    public void testDiscardingAnEditForgetsASignalPairedSinceTheLoad() throws IOException
    {
        TileKey station = new TileKey("1 - Main", 4, 7);
        TileKey signal = new TileKey("1 - Main", 5, 7);

        store.setPageIds(onePage());
        store.setStation(station, true);
        store.save();

        // paired, and never saved
        store.setProtectingSignal(station, signal);

        // which is what discarding does
        store.load();

        assertTrue(store.getProtectingSignals(station).isEmpty(),
            "a signal paired after the load survived the discard, and the next save would write it "
            + "to disk - the railway would then hold trains at a platform on a pairing the user had "
            + "thrown away");
    }

    /**
     * A rename whose save then fails leaves the configuration somewhere.
     *
     * The old file used to be deleted the moment the name changed, and the new one is only written by
     * the save that follows - so anything that stopped that save (a sync client on the folder, a full
     * disk, the process dying) destroyed the configuration outright.  load() rebuilds the list by
     * scanning the folder, so there was nothing left to find.
     *
     * Simulated here by simply not saving, which is the same state a failed save leaves behind.
     */
    @Test
    public void testARenameThatIsNeverSavedStillLeavesTheConfigurationOnDisk() throws IOException
    {
        store.createConfiguration("Morning", null);
        store.setActiveConfiguration("Morning");
        store.save();

        store.renameConfiguration("Morning", "Evening");

        // no save: this is the window a failing save leaves open

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        assertEquals(reloaded.getConfigurationNames().size(), 1,
            "the configuration is on disk under neither name, so a rename followed by a failed save "
            + "destroyed it");

        assertEquals(reloaded.getConfigurationNames().get(0), "Evening",
            "the file did not travel with the name");
    }

    /**
     * Two names that would be written to one file are refused.
     *
     * A configuration's file is named after it, and the sanitising is many to one - every character a
     * filename may not hold becomes an underscore.  So two differently-named configurations could share
     * one file: saving wrote both to it, one over the other, and the next load - which rebuilds the
     * list from the folder - came back with one of them simply gone.
     */
    @Test
    public void testTwoNamesCannotShareOneFile() throws IOException
    {
        store.createConfiguration("Night: Yard", null);

        try
        {
            store.createConfiguration("Night_ Yard", null);

            fail("a second configuration was created over the first one's file, which destroys it on "
                + "the next save with nothing said");
        }
        catch (IOException expected)
        {
            // right
        }

        // and the same door, reached by renaming
        store.createConfiguration("Depot", null);

        try
        {
            store.renameConfiguration("Depot", "Night_ Yard");

            fail("a rename put two configurations in one file");
        }
        catch (IOException expected)
        {
            // right
        }

        assertEquals(store.getConfigurationNames().size(), 2,
            "a refused rename changed the store anyway");
    }

    /**
     * An import that cannot be read leaves the setup exactly as it was.
     *
     * The shared half used to be emptied BEFORE the merged object was parsed, and the parse uses the
     * type-strict accessors - so a bundle with the wrong type in it wiped every name, station, length
     * and pairing on the way to reporting that the import had failed.  The user was told nothing had
     * happened while the store stood blank, ready for the next save to write that over setup.json.
     */
    @Test
    public void testAnUnreadableImportChangesNothing() throws Exception
    {
        TileKey station = new TileKey("1 - Main", 4, 7);
        TileKey other = new TileKey("1 - Main", 9, 9);

        store.setPageIds(onePage());
        store.setStation(station, true);
        store.setPointName(station, "Bottom Main");
        store.setPointName(other, "Second Point");
        store.createConfiguration("Mine", null);

        org.json.JSONObject bundle = store.exportBundle("Mine");

        org.json.JSONObject names = bundle.getJSONObject("shared").getJSONObject("pointNames");

        assertFalse(names.keySet().isEmpty(),
            "precondition: the exported bundle names no points, so there is no key to corrupt");

        // Whichever key the export wrote "other" under, found by its value rather than assumed from
        // the stored-key format - the export translates keys through page ids, and hard-coding that
        // shape here is exactly what earlier versions of this fixture got wrong.
        String otherKey = null;

        for (String k : names.keySet())
        {
            if ("Second Point".equals(names.getString(k)))
            {
                otherKey = k;
                break;
            }
        }

        assertNotNull(otherKey, "precondition: the second point's name is in the export under some key");

        // Remove it locally so the merge below has a gap to fill.  Exporting a store and importing the
        // same bundle straight back FILLS NOTHING: every incoming key is already present locally, and
        // `importBundle`'s merge rule (`if (mine.has(inner)) continue;`) skips every one of them before
        // `readShared` is ever reached - a bundle can be as malformed as it likes and nothing will read
        // far enough to notice. That was this test's fixture until now, which is why it had never once
        // exercised the rollback it is named for. With a real gap the merge has something to fill, which
        // is what makes the corrupted value below actually reach the strict accessor.
        store.setPointName(other, null);

        // A number where the reader demands a string, under the key the gap will be filled from.
        // `readSquareMap` is `if (tile != null) into.put(tile, object.getString(key));` - the tile key
        // parses fine, so this is refused by the type-strict accessor rather than skipped ahead of it.
        names.put(otherKey, 12345);

        boolean threw = false;

        try
        {
            store.importBundle("Theirs", bundle);
        }
        catch (RuntimeException expected)
        {
            threw = true;
        }

        // MUTATION this catches: change readShared to use opt* accessors instead of the type-strict
        // ones, so the malformed value above is coerced instead of rejected. Nothing throws, the merge
        // completes, and every assertion below would fail: "Theirs" would exist, and "other" would
        // carry whatever the coercion produced instead of staying unset.
        assertTrue(threw, "a bundle with a genuinely unreadable entry must be refused, not imported "
            + "silently");

        assertEquals(store.getPointName(station), "Bottom Main",
            "a refused import emptied the setup it was refused by");

        assertTrue(store.isStation(station), "the station went with it");

        assertNull(store.getPointName(other),
            "a refused import must not have half-applied the merge it was refused by");

        assertFalse(store.getConfigurationNames().contains("Theirs"),
            "a refused import must not leave its configuration behind");
    }

    /**
     * A page rename costs nothing, because entries are stored against the page id rather than its name.
     *
     * Every key here begins with a page, so keying on the name meant a rename orphaned a whole page of
     * names, lengths, directions and pairings at once - and nothing connected the loss to the rename.
     * The id is what gleisbild.cs2 has always identified a page by, and a user renaming a page does not
     * change it.
     */
    @Test
    public void testRenamingAPageCostsNothingBecauseIdsAreStored() throws IOException
    {
        java.util.Map<String, String> before = new java.util.LinkedHashMap<>();
        before.put("Old Name", "2");

        store.setPageIds(before);

        TileKey tile = new TileKey("Old Name", 4, 7);

        store.setPointName(tile, "Yard throat");
        store.setStation(tile, true);
        store.setTileLength(tile, 12);
        store.setTileDirection(tile, new RouteId(0, 0), Direction.TOWARD_B);
        store.createConfiguration("Default", null);

        store.save();

        // the same page, renamed: same id, new name
        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("New Name", "2");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(after);
        reloaded.load();

        TileKey renamed = new TileKey("New Name", 4, 7);

        assertEquals(reloaded.getPointName(renamed), "Yard throat");
        assertTrue(reloaded.isStation(renamed));
        assertEquals(reloaded.getTileLength(renamed), 12);
        assertEquals(reloaded.getTileDirection(renamed, new RouteId(0, 0)), Direction.TOWARD_B);

        assertTrue(reloaded.getPageIdConflicts().isEmpty(), "a rename is not a conflict");
    }

    /**
     * A page RENUMBERED is reported rather than adopted.
     *
     * The Central Station orders pages by this id, so reordering them there can renumber the pages - and
     * that fails worse than a rename: a page of settings would silently reattach to the WRONG page, with
     * nothing looking amiss.  So the name each id had is recorded, and a mismatch is surfaced.
     */
    @Test
    public void testAPageRenumberIsReportedRatherThanAdopted() throws IOException
    {
        java.util.Map<String, String> before = new java.util.LinkedHashMap<>();
        before.put("Yard", "2");

        store.setPageIds(before);
        store.setPointName(new TileKey("Yard", 1, 1), "Yard throat");
        store.createConfiguration("Default", null);
        store.save();

        // id 2 now belongs to a different page - and crucially "Yard" is STILL THERE, under another id.
        // That is what separates a renumber from a rename: after a rename the old name is simply gone.
        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("Main Line", "2");
        after.put("Yard", "3");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(after);
        reloaded.load();

        assertFalse(reloaded.getPageIdConflicts().isEmpty(),
            "an id belonging to a different page must be reported");
        assertEquals(reloaded.getPageIdConflicts().get("Yard"), "Main Line");
    }

    /**
     * A renumbered page's settings follow the PAGE, not the number.
     *
     * The test above pins that a renumber is REPORTED.  Nothing pinned where the settings went, and
     * they went to the wrong page: fromStored resolved every stored id through the current index, so a
     * page of names, stations, lengths and directions was handed to whatever track holds that number
     * now.  Their coordinates do not exist there, so the next save reconciled them away as deleted
     * squares - and because ids that shift by one round-trip unchanged, the file looked consistent
     * throughout.
     *
     * Adam, MT-135: "Immediately after rename, all stations are gone."  A rename moved one page to the
     * end of the index, which renumbered every page after it; he lost 19 point names, 14 stations, 22
     * directions and 15 captions on 2026-08-23.
     *
     * The pair of them is the whole rule: the test above says the id is not trusted blindly, this one
     * says the name is followed instead, and testRenamingAPageCostsNothingBecauseIdsAreStored says a
     * rename still goes by id - because there the old name is GONE, which is what tells the two apart.
     */
    @Test
    public void testARenumberedPagesSettingsFollowTheNameNotTheNumber() throws IOException
    {
        java.util.Map<String, String> before = new java.util.LinkedHashMap<>();
        before.put("Yard", "2");

        store.setPageIds(before);

        TileKey tile = new TileKey("Yard", 1, 1);

        store.setPointName(tile, "Yard throat");
        store.setStation(tile, true);
        store.setTileLength(tile, 9);
        store.setTileDirection(tile, new RouteId(0, 0), Direction.TOWARD_B);
        store.createConfiguration("Default", null);
        store.save();

        // "Yard" is still here, under a different id - a renumber, not a rename
        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("Main Line", "2");
        after.put("Yard", "3");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(after);
        reloaded.load();

        assertEquals(reloaded.getPointName(tile), "Yard throat",
            "the settings of a renumbered page did not follow it - they were read through whatever "
            + "page holds that id now, which is how a rename came to delete every station on a page");
        assertTrue(reloaded.isStation(tile), "the station did not follow the page");
        assertEquals(reloaded.getTileLength(tile), 9, "the length did not follow the page");
        assertEquals(reloaded.getTileDirection(tile, new RouteId(0, 0)), Direction.TOWARD_B,
            "the direction did not follow the page");

        assertNull(reloaded.getPointName(new TileKey("Main Line", 1, 1)),
            "another page was given this page's settings, which is worse than losing them because "
            + "nothing looks wrong");
        assertFalse(reloaded.isStation(new TileKey("Main Line", 1, 1)),
            "another page was given this page's station");
    }

    /**
     * And an excluded page stays excluded across a renumber.
     *
     * Getting this one wrong is silent in the other direction: a page the user took OUT of autonomy
     * quietly rejoins it, and autonomy starts routing trains over track nobody meant it to touch.
     */
    @Test
    public void testAnExcludedPageStaysExcludedAcrossARenumber() throws IOException
    {
        java.util.Map<String, String> before = new java.util.LinkedHashMap<>();
        before.put("Yard", "2");
        before.put("Main Line", "3");

        store.setPageIds(before);
        store.setPageExcluded("Yard", true);
        store.createConfiguration("Default", null);
        store.save();

        // the two pages swap numbers
        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("Main Line", "2");
        after.put("Yard", "3");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(after);
        reloaded.load();

        assertTrue(reloaded.getExcludedPages().contains("Yard"),
            "the excluded page rejoined autonomy because its exclusion was read by number");
        assertFalse(reloaded.getExcludedPages().contains("Main Line"),
            "a page nobody excluded was excluded instead");
    }

    /**
     * A page rename must carry everything on that page with it.
     *
     * Every key here begins with a page name, so without this the user would see a page worth of names,
     * lengths, directions and pairings vanish at once, with nothing to connect it to the rename.
     */
    @Test
    public void testRenamingAPageCarriesEverythingOnIt() throws IOException
    {
        TileKey before = new TileKey("Old Name", 4, 7);
        TileKey partner = new TileKey("2 - Bottom", 1, 1);

        store.setPointName(before, "Yard throat");
        store.setStation(before, true);
        store.setTileLength(before, 12);
        store.setTileDirection(before, new RouteId(0, 0), Direction.TOWARD_B);
        store.setLinkName(before, "yard link");
        store.pairPortals(before, partner);
        store.setPageExcluded("Old Name", true);
        store.setBarredArrivals(before,
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(TilePorts.Side.N)));
        store.setPortalDisabled(before, true);

        // A caption is the awkward one: it is keyed by the square the text sits on and POINTS at the
        // square of the station, so a rename has to move both halves.
        TileKey plaque = new TileKey("Old Name", 4, 8);

        store.setCaption(plaque, before);

        store.renamePage("Old Name", "New Name");

        TileKey after = new TileKey("New Name", 4, 7);

        assertEquals(store.getPointName(after), "Yard throat");
        assertTrue(store.isStation(after));
        assertEquals(store.getTileLength(after), 12);
        assertEquals(store.getTileDirection(after, new RouteId(0, 0)), Direction.TOWARD_B);
        assertEquals(store.getLinkName(after), "yard link");
        assertTrue(store.getExcludedPages().contains("New Name"));

        // both ends of the pairing follow, including the one recorded on the other page
        assertEquals(store.getPortalPartner(after), partner);
        assertEquals(store.getPortalPartner(partner), after,
            "the partner records the renamed page too");

        assertEquals(store.getBarredArrivals(after),
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(TilePorts.Side.N)));

        assertTrue(store.isPortalDisabled(after),
            "a link switched off came back on, silently, and only on the renamed page");

        TileKey renamedPlaque = new TileKey("New Name", 4, 8);

        assertEquals(store.getCaptionTarget(renamedPlaque), after,
            "the caption has to move AND to point at the station's new key - pointing at the old one "
            + "is a caption the next save deletes as unreconcilable");

        // and nothing is left under the old name
        assertNull(store.getPointName(before));
        assertFalse(store.getExcludedPages().contains("Old Name"));
        assertNull(store.getCaptionTarget(plaque));
        assertFalse(store.isPortalDisabled(before));
    }

    /**
     * A deleted tile takes its length and direction with it.  Those belong to the tile, nothing else in
     * the setup referred to them, and a deleted tile starts over.
     */
    @Test
    public void testADeletedTileTakesItsLengthAndDirection() throws IOException
    {
        TileKey kept = new TileKey("1 - Main", 1, 1);
        TileKey removed = new TileKey("1 - Main", 9, 9);

        store.setTileLength(kept, 3);
        store.setTileLength(removed, 8);
        store.setTileDirection(removed, new RouteId(0, 0), Direction.NONE);

        AutonomyCompanionStore.Reconciliation report = store.reconcile(only(kept));

        assertEquals(store.getTileLength(kept), 3, "a tile still on the diagram keeps everything");
        assertEquals(store.getTileLength(removed), 0);
        assertNull(store.getTileDirection(removed, new RouteId(0, 0)));

        assertEquals(report.getDroppedTileProperties().size(), 2, "both are reported, not just removed");

        // AND THE OPERATOR IS TOLD (IPR-A2).
        //
        // `isClean()` counts this list, and the dialog was built from the other two - so a save whose
        // only casualty was lengths and directions passed `isClean()` false, produced an empty text,
        // and showed nothing at all.  The numbers somebody typed went and the application said not a
        // word.  What is asserted is the WORDS, because the dialog itself cannot be driven from a
        // battery.
        assertTrue(report.getForgottenNames().isEmpty() && report.getNamesStillReferenced().isEmpty(),
            "precondition: this reconciliation must have dropped ONLY tile properties, or the text "
            + "below could be produced by one of the other two lists");

        String said = org.traincontrol.gui.AutonomyReport.describe(report);

        assertFalse(said.isEmpty(),
            "a save that dropped track lengths and directions says nothing at all - isClean() counts "
            + "them, so the dialog is reached, and then there is nothing in it to show");

        assertTrue(said.contains(org.traincontrol.util.I18n.t("autosetup.ui.infoTilePropertiesDropped")),
            "the text does not carry the sentence that explains what was dropped: " + said);
    }

    /**
     * Switching a paired link off switches its partner off too.
     *
     * OB-041, Adam: "if a linked link is turned off, its target isn't."
     *
     * A pair of links is one doorway with an end in two places. Autonomy walks through it in both
     * directions, so a doorway that is closed at one end and open at the other is not a half-closed
     * doorway - it is a route that exists going one way and not the other, which nothing on the diagram
     * says and no train can be told.
     *
     * The same reasoning as OB-031, where pairing two links switches both ends ON rather than refusing
     * because one of them was off: once two squares are paired, a statement about one of them is a
     * statement about the pair.
     */
    @Test
    public void testSwitchingAPairedLinkOffSwitchesItsPartnerOff() throws IOException
    {
        TileKey here = new TileKey("1 - Main", 2, 2);
        TileKey there = new TileKey("2 - Yard", 5, 5);
        TileKey lonely = new TileKey("1 - Main", 8, 8);

        store.pairPortals(here, there);

        store.setPortalDisabled(here, true);

        assertTrue(store.isPortalDisabled(here), "the end that was switched off is off");

        assertTrue(store.isPortalDisabled(there),
            "the far end of the pair is still switched on. The doorway is now open one way and shut "
            + "the other, which is a route no train can be told about (OB-041)");

        // And back on again, from the other end, because a rule that only closes is half a rule
        store.setPortalDisabled(there, false);

        assertFalse(store.isPortalDisabled(there), "the end that was switched on is on");

        assertFalse(store.isPortalDisabled(here),
            "switching a pair back on from one end left the other end off");

        // An unpaired link is nobody else's business
        store.setPortalDisabled(lonely, true);

        assertTrue(store.isPortalDisabled(lonely));
        assertFalse(store.isPortalDisabled(here), "an unpaired link took an unrelated one with it");
    }

    /**
     * A deleted tile takes its link name and its switched-off flag too.
     *
     * DD-A1 found these two missing from `reconcile` - the only two of the eleven kept collections it
     * says nothing about, with no comment claiming that is deliberate while there is one for every
     * other decision in the method.
     *
     * The consequence is not merely untidy. Both are remembered BY SQUARE, so a name and a "this link
     * is switched off" flag for track that no longer exists sit in the file indefinitely, and **a link
     * later drawn on that square inherits both** - arriving pre-named and already disabled, with
     * nothing anywhere saying why.
     *
     * That is the same shape as the four defects `disabledPortals` produced while it was being added:
     * a collection is easy to leave out of one site of fourteen, and the omission shows up as a setting
     * that comes back from the dead.
     */
    @Test
    public void testADeletedTileTakesItsLinkNameAndItsDisabledFlag() throws IOException
    {
        TileKey kept = new TileKey("1 - Main", 1, 1);
        TileKey removed = new TileKey("1 - Main", 9, 9);

        store.setLinkName(kept, "To the yard");
        store.setLinkName(removed, "To nowhere");
        store.setPortalDisabled(removed, true);

        AutonomyCompanionStore.Reconciliation report = store.reconcile(only(kept));

        assertEquals(store.getLinkName(kept), "To the yard", "a tile still on the diagram keeps its name");

        assertNull(store.getLinkName(removed),
            "the name of a link whose square has been deleted is still in the store. The next link "
            + "drawn there inherits it (DD-A1)");

        assertFalse(store.isPortalDisabled(removed),
            "a square with no tile on it is still remembered as a switched-off link. The next link "
            + "drawn there arrives already disabled, and nothing says why (DD-A1)");

        assertTrue(report.getDroppedTileProperties().size() >= 2,
            "both were dropped without being reported. A diagram edit that quietly took a link name "
            + "should be visible rather than discovered later - which is the rule every other "
            + "collection in reconcile already follows");
    }

    /**
     * A name whose tile is gone, that nothing refers to, is forgotten - but said out loud, so a diagram
     * edit that quietly cost a page of names is visible rather than discovered later.
     */
    @Test
    public void testAnUnreferencedNameIsForgottenButReported() throws IOException
    {
        TileKey kept = new TileKey("1 - Main", 1, 1);
        TileKey removed = new TileKey("1 - Main", 9, 9);

        store.createConfiguration("Evening", null);
        store.setPointName(removed, "Was a station once");
        store.setStation(removed, true);

        AutonomyCompanionStore.Reconciliation report = store.reconcile(only(kept));

        assertNull(store.getPointName(removed), "nothing referred to it, so it goes");
        assertFalse(store.isStation(removed));

        assertEquals(report.getForgottenNames().size(), 1);
        assertTrue(report.getForgottenNames().get(0).contains("Was a station once"));
        assertTrue(report.getNamesStillReferenced().isEmpty());
    }

    /**
     * A name whose tile is gone but which a timetable still uses is KEPT, and named.
     *
     * This is the case both obvious answers get wrong.  Dropping it breaks the timetable with no
     * explanation; keeping it silently leaves a Point wired into a timetable no train can reach.  So it
     * survives and is reported, for a person to resolve.
     */
    @Test
    public void testAReferencedNameSurvivesAndIsReported() throws IOException
    {
        TileKey kept = new TileKey("1 - Main", 1, 1);
        TileKey removed = new TileKey("1 - Main", 9, 9);

        store.setPointName(removed, "Yard throat");

        store.createConfiguration("Evening", null);
        store.getConfiguration("Evening").put("timetable",
            new org.json.JSONArray().put(new org.json.JSONObject().put("point", "Yard throat")));

        AutonomyCompanionStore.Reconciliation report = store.reconcile(only(kept));

        assertEquals(store.getPointName(removed), "Yard throat", "something still refers to it");

        assertTrue(report.getForgottenNames().isEmpty());
        assertEquals(report.getNamesStillReferenced().size(), 1);
        assertEquals(report.getNamesStillReferenced().get("Yard throat").get(0), "Evening",
            "the report says WHICH configuration still refers to it");
    }

    /**
     * A pairing with only one end left is worse than no pairing: a train could cross and be unable to
     * return.  So a portal whose partner has gone is released, and reported.
     */
    @Test
    public void testAPairingLosingOneEndIsReleased() throws IOException
    {
        TileKey kept = new TileKey("1 - Main", 1, 1);
        TileKey removed = new TileKey("2 - Bottom", 9, 9);

        store.pairPortals(kept, removed);

        AutonomyCompanionStore.Reconciliation report = store.reconcile(only(kept));

        assertNull(store.getPortalPartner(kept), "half a pairing is released, not left dangling");
        assertFalse(report.getDroppedTileProperties().isEmpty());
    }

    /**
     * A diagram that has not changed reconciles to nothing at all.
     */
    @Test
    public void testAnUnchangedDiagramReconcilesCleanly() throws IOException
    {
        TileKey tile = new TileKey("1 - Main", 1, 1);

        store.setTileLength(tile, 4);
        store.setPointName(tile, "Platform 1");

        assertTrue(store.reconcile(only(tile)).isClean());
        assertEquals(store.getTileLength(tile), 4);
        assertEquals(store.getPointName(tile), "Platform 1");
    }

    /**
     * A file from a newer TrainControl is refused rather than read partially.  Reading what it recognises
     * and dropping the rest would lose the user's work on the next save.
     */
    @Test
    public void testAFileFromANewerVersionIsRefused() throws IOException
    {
        File folder = new File(layout, "config/autonomy");
        folder.mkdirs();

        Files.write(new File(folder, "setup.json").toPath(),
            "{\"version\": 99, \"pointNames\": {}}".getBytes(StandardCharsets.UTF_8));

        try
        {
            store.load();
            fail("a newer schema should be refused");
        }
        catch (IOException expected)
        {
            assertTrue(expected.getMessage().contains("autosetup"), expected.getMessage());
        }
    }

    /**
     * Fields this version does not know are kept, so a layout opened in an older TrainControl and saved
     * again does not come back stripped.
     */
    @Test
    public void testUnknownFieldsSurviveARoundTrip() throws IOException
    {
        File folder = new File(layout, "config/autonomy");
        folder.mkdirs();

        Files.write(new File(folder, "setup.json").toPath(),
            "{\"version\": 1, \"somethingNewer\": {\"a\": 1}}".getBytes(StandardCharsets.UTF_8));

        store.load();
        store.createConfiguration("Default", null);
        store.save();

        String written = new String(Files.readAllBytes(
            new File(folder, "setup.json").toPath()), StandardCharsets.UTF_8);

        assertTrue(written.contains("somethingNewer"),
            "a field from a newer version must not be dropped:\n" + written);
    }

    private Set<TileKey> only(TileKey... tiles)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey tile : tiles)
        {
            out.add(tile);
        }

        return out;
    }

    private void delete(File file)
    {
        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children)
            {
                delete(child);
            }
        }

        file.delete();
    }

    /**
     * A setup that cannot be read leaves the one in memory alone.
     *
     * load() used to empty the store before opening the file, so a read that failed - a sync lock on the
     * folder, a half-written file, a setup from a newer TrainControl - left a live, blank store behind
     * and reported the failure as though nothing had happened.  Everything the user had set up was gone
     * from the screen, and one press of Save away from being gone from the disk.  It is reachable from
     * the editor's own "exit without saving", whose dialog promises the opposite.
     */
    @Test
    public void testAFailedLoadKeepsWhatWasAlreadyThere() throws Exception
    {
        File folder = Files.createTempDirectory("tc-setup").toFile();

        org.traincontrol.automationui.AutonomyCompanionStore store =
            new org.traincontrol.automationui.AutonomyCompanionStore(folder);

        store.setPointName(new TileKey("main", 2, 2), "Bahnhof");
        store.save();

        // and now the file goes bad under it, as a sync or a crashed write would leave it
        File setup = new File(folder, "config/autonomy/setup.json");

        assertTrue(setup.isFile(), "the store did not write " + setup);

        Files.write(setup.toPath(), "{ this is not json".getBytes(StandardCharsets.UTF_8));

        try
        {
            store.load();

            fail("a setup file that is not JSON has to be reported, not accepted");
        }
        catch (IOException expected)
        {
            // which is the point: it throws
        }

        assertEquals(store.getPointName(new TileKey("main", 2, 2)), "Bahnhof",
            "a load that failed must leave the setup exactly as it was");
    }

    // --- captions ----------------------------------------------------------------------------------

    /**
     * A caption survives being written out and read back.
     *
     * It is stored as caption square to sensor square, and both are translated to page ids on the way
     * out - so this also covers a caption surviving the page being renamed, which is what every other
     * per-square setting already does and what a caption in the layout file never could.
     */
    @Test
    public void testACaptionSurvivesASaveAndLoad() throws IOException
    {
        TileKey caption = new TileKey("main", 4, 5);
        TileKey station = new TileKey("main", 4, 4);

        store.setCaption(caption, station);
        store.save();

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);
        reopened.load();

        assertEquals(reopened.getCaptionTarget(caption), station,
            "a caption is part of the setup, so it has to come back with it");

        assertTrue(reopened.captionsFor(station).contains(caption),
            "and the station can find what is showing its name");
    }

    /**
     * A caption made after a LOAD survives the next save.
     *
     * The round trip that matters, and the one the save-then-load test above cannot reach: a store that
     * has read a file keeps every field it does not recognise, and writes them back last so an older
     * TrainControl cannot delete a newer one\u2019s work.  Leave "captions" off the list of fields this
     * version knows about and that mechanism eats it - the stale copy read at load is written over the
     * caption made since.  Every real session is load, edit, save; only a brand new setup is not.
     */
    @Test
    public void testACaptionMadeAfterALoadSurvivesTheNextSave() throws IOException
    {
        TileKey caption = new TileKey("main", 4, 5);
        TileKey station = new TileKey("main", 4, 4);

        // a setup that already exists, so the next store has something to read
        store.setPointName(station, "Bahnhof");
        store.save();

        AutonomyCompanionStore second = new AutonomyCompanionStore(layout);
        second.load();

        second.setCaption(caption, station);
        second.save();

        AutonomyCompanionStore third = new AutonomyCompanionStore(layout);
        third.load();

        assertEquals(third.getCaptionTarget(caption), station,
            "a caption made after a load must still be on disk after the save that followed it");
    }

    /**
     * One square holds one caption, and one station may be captioned in several places.
     *
     * The second half is deliberate rather than tolerated: a long platform is legitimately labelled at
     * both ends, and the running state is written to every label showing it.
     */
    @Test
    public void testASquareHoldsOneCaptionAndAStationMayHaveSeveral()
    {
        TileKey station = new TileKey("main", 4, 4);
        TileKey other = new TileKey("main", 8, 8);

        TileKey west = new TileKey("main", 3, 5);
        TileKey east = new TileKey("main", 5, 5);

        store.setCaption(west, station);
        store.setCaption(east, station);

        assertEquals(store.captionsFor(station).size(), 2, "both ends of the platform name it");

        // and a second caption on one square replaces the first rather than joining it
        store.setCaption(west, other);

        assertEquals(store.getCaptionTarget(west), other);
        assertEquals(store.captionsFor(station).size(), 1);
    }

    /**
     * A caption goes when either end of it does.
     *
     * Its own square, or the sensor it is about - text pointing at track that no longer exists is the
     * orphan this whole design removes, and reconcile is where the diagram gets to say what is left.
     */
    @Test
    public void testReconcileDropsACaptionWhenEitherEndIsGone()
    {
        TileKey caption = new TileKey("main", 4, 5);
        TileKey station = new TileKey("main", 4, 4);

        store.setCaption(caption, station);

        // Asserted before reconciling, so that the two assertNulls below cannot pass by the caption
        // never having existed - which is how a test of an absence quietly stops testing anything.
        assertEquals(store.getCaptionTarget(caption), station, "the fixture did not set the caption");

        // the sensor is deleted from the diagram, its caption square is not
        store.reconcile(new LinkedHashSet<>(java.util.Arrays.asList(caption)));

        assertNull(store.getCaptionTarget(caption),
            "a caption about track that is gone is a caption about nothing");

        store.setCaption(caption, station);

        // And now the other way round - which is DELIBERATELY not symmetric.
        //
        // The sensor stays and the square the text sat on is emptied.  The caption stays with it: a
        // caption is allowed to sit on blank space, and it is the most readable place to put one, so
        // "no component on that square" cannot mean "no caption".  Judging it that way once deleted,
        // on the very next save, both the captions a user had just placed and every caption the
        // migration had just created.
        //
        // What it IS about is the station.  A caption whose sensor has gone is text pointing at track
        // that no longer exists, and that is the case above.
        store.reconcile(new LinkedHashSet<>(java.util.Arrays.asList(station)));

        assertEquals(store.getCaptionTarget(caption), station,
            "a caption on a square with nothing on it is where captions are supposed to go");

        // And the page going takes it, because then neither end is anywhere
        store.reconcile(new LinkedHashSet<TileKey>());

        assertNull(store.getCaptionTarget(caption),
            "a caption whose page has gone is about nothing at all");
    }

    // --- getting back out ---------------------------------------------------------------------------

    /**
     * The last configuration may be deleted.
     *
     * It used to be refused, on the reasoning that a setup with no configurations is a state nothing
     * could act on - which made setting autonomy up a one-way door: a layout somebody had experimented
     * on kept a configuration for ever, and the only way to be rid of it was to delete files by hand.
     * With none left there is simply nothing active, which is the state every layout starts in.
     */
    @Test
    public void testTheLastConfigurationMayBeDeleted() throws IOException
    {
        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");
        store.save();

        store.deleteConfiguration("Only");

        assertTrue(store.getConfigurationNames().isEmpty(), "the last one should have gone");
        assertNull(store.getActiveConfiguration(), "and nothing is active any more");

        store.save();

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);
        reopened.load();

        assertTrue(reopened.getConfigurationNames().isEmpty(),
            "and it stays gone, rather than coming back on the next load");
    }

    /**
     * Deleting one of several still leaves a sensible active configuration.
     *
     * The half of the old behaviour that was right: whichever one was running has gone, so something
     * else has to be, or the next save has nowhere to put anything.
     */
    @Test
    public void testDeletingTheActiveConfigurationPromotesAnother() throws IOException
    {
        store.createConfiguration("Morning", null);
        store.createConfiguration("Evening", null);
        store.setActiveConfiguration("Morning");

        store.deleteConfiguration("Morning");

        assertEquals(store.getConfigurationNames().size(), 1);
        assertEquals(store.getActiveConfiguration(), "Evening",
            "with one left, that is the one running");
    }

    /**
     * Deleting everything removes the files and the decisions, and leaves the diagram alone.
     *
     * The way back out of having set autonomy up at all.  What has to go is everything in the setup -
     * configurations, names, stations, captions, directions - and what has to stay is the track diagram,
     * which belongs to the layout rather than to autonomy.
     */
    @Test
    public void testDeletingEverythingRemovesTheFilesAndTheDecisions() throws IOException
    {
        TileKey station = new TileKey("main", 4, 4);

        store.setPointName(station, "Bahnhof");
        store.setStation(station, true);
        store.setCaption(new TileKey("main", 4, 5), station);
        store.createConfiguration("Morning", null);
        store.createConfiguration("Evening", null);
        store.save();

        File setup = new File(layout, "config/autonomy/setup.json");

        assertTrue(setup.isFile(), "the fixture did not write " + setup);

        store.deleteEverything();

        assertFalse(setup.isFile(), "the setup file should be gone");
        assertTrue(store.getConfigurationNames().isEmpty());
        assertNull(store.getPointName(station), "and every decision with it");
        assertFalse(store.isStation(station));
        assertNull(store.getCaptionTarget(new TileKey("main", 4, 5)));

        // and a store opened on the same layout afterwards finds nothing, rather than a half-deleted
        // setup that loads
        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        assertFalse(reopened.exists(), "there should be no setup left to find");

        reopened.load();

        assertTrue(reopened.getConfigurationNames().isEmpty());
        assertNull(reopened.getPointName(station));

        // the layout folder itself is untouched: autonomy has no business deleting somebody's diagram
        assertTrue(layout.isDirectory(), "the layout folder is not autonomy to remove");
    }

    /**
     * The one page these export tests use, under a fixed id so a second store agrees about it.
     */
    private java.util.Map<String, String> onePage()
    {
        java.util.Map<String, String> ids = new java.util.LinkedHashMap<>();

        ids.put("1 - Main", "1");

        return ids;
    }

    /**
     * An exported configuration carries the station names and station flags it refers to.
     *
     * Exporting used to write the configuration alone.  A configuration is placements and homes against
     * POINTS, while what makes a square a point, what it is called, how long it is and which way it
     * runs all live in the shared half - so the file named things the receiving setup had never heard
     * of.  Imported into a fresh setup it produced a configuration referring entirely to nothing, which
     * from the outside is indistinguishable from having lost the names.
     *
     * Asserted through a real import into an empty store rather than on the keys in the file: a bundle
     * containing the right fields that the importer then ignored would pass the second and fail the
     * user.
     */
    @Test
    public void testAnExportCarriesTheNamesTheConfigurationRefersTo() throws Exception
    {
        store.setPageIds(onePage());

        TileKey platform = new TileKey("1 - Main", 4, 7);

        store.setStation(platform, true);
        store.setPointName(platform, "Hauptbahnhof");
        store.setTileLength(platform, 240);

        store.createConfiguration("Adam 1", null);

        org.json.JSONObject bundle = store.exportBundle("Adam 1");

        assertNotNull(bundle, "there was no such configuration to export");

        File second = Files.createTempDirectory("tc-autonomy-store-2").toFile();

        try
        {
            // A different setup entirely: the same track, nothing filled in yet
            AutonomyCompanionStore fresh = new AutonomyCompanionStore(second);
            fresh.setPageIds(onePage());

            int filled = fresh.importBundle("Adam 1", new org.json.JSONObject(bundle.toString()));

            assertTrue(filled > 0, "the import filled nothing in, so the file carried nothing to fill");

            assertEquals(fresh.getPointName(platform), "Hauptbahnhof",
                "the station name did not travel with the configuration that refers to it");

            assertTrue(fresh.isStation(platform),
                "the square is not a station here, so the configuration refers to a point that is not one");

            assertEquals(fresh.getTileLength(platform), 240, "the length did not travel");

            assertTrue(fresh.getConfigurationNames().contains("Adam 1"),
                "the configuration itself did not arrive");
        }
        finally
        {
            delete(second);
        }
    }

    /**
     * Importing fills gaps and never overwrites a name this setup already has.
     *
     * The shared half is layout-wide, so adopting somebody else's wholesale would rename stations the
     * importing user had named themselves - a silent edit to work they never offered up.  Filling gaps
     * gives the restoring case everything back and the sharing case the union.
     */
    @Test
    public void testImportingFillsGapsAndOverwritesNothing() throws Exception
    {
        TileKey shared = new TileKey("1 - Main", 4, 7);
        TileKey onlyTheirs = new TileKey("1 - Main", 5, 7);

        store.setPageIds(onePage());

        store.setStation(shared, true);
        store.setPointName(shared, "Their name for it");

        store.setStation(onlyTheirs, true);
        store.setPointName(onlyTheirs, "Only they have this");

        store.createConfiguration("Adam 1", null);

        org.json.JSONObject bundle = store.exportBundle("Adam 1");

        File second = Files.createTempDirectory("tc-autonomy-store-2").toFile();

        try
        {
            AutonomyCompanionStore local = new AutonomyCompanionStore(second);
            local.setPageIds(onePage());

            local.setStation(shared, true);
            local.setPointName(shared, "My name for it");

            local.importBundle("Adam 1", new org.json.JSONObject(bundle.toString()));

            assertEquals(local.getPointName(shared), "My name for it",
                "importing renamed a station this setup had already named");

            assertEquals(local.getPointName(onlyTheirs), "Only they have this",
                "importing did not fill in the name this setup was missing");
        }
        finally
        {
            delete(second);
        }
    }

    /**
     * A file written before exporting carried the shared half still imports.
     *
     * That is every file anybody has exported until now, and throwing on them would turn a gap into a
     * regression.
     */
    @Test
    public void testABareConfigurationFileStillImports() throws Exception
    {
        store.setPageIds(onePage());
        store.createConfiguration("Old Export", null);

        // The old format: the configuration object on its own, with no shared half beside it
        org.json.JSONObject bare =
            new org.json.JSONObject(store.getConfiguration("Old Export").toString());

        File second = Files.createTempDirectory("tc-autonomy-store-2").toFile();

        try
        {
            AutonomyCompanionStore fresh = new AutonomyCompanionStore(second);
            fresh.setPageIds(onePage());

            int filled = fresh.importBundle("Old Export", bare);

            assertEquals(filled, 0, "a bare configuration has no shared half, so nothing can be filled");

            assertTrue(fresh.getConfigurationNames().contains("Old Export"),
                "the configuration from an older export did not arrive");
        }
        finally
        {
            delete(second);
        }
    }

    /**
     * A caption survives being written to disk and read back under the page's id.
     *
     * Captions are the only thing in the setup whose KEY and VALUE are both squares, so they are the
     * only thing that goes through translateTileMap and untranslateTileMap - a different pair from the
     * ones every other field uses, and therefore a pair no other test exercises.  Everything else here
     * would keep passing if these two disagreed, and a caption that does not resolve after a reload is
     * indistinguishable, on screen, from one that was never saved.
     */
    @Test
    public void testACaptionResolvesAfterAReloadUnderPageIds() throws IOException
    {
        java.util.Map<String, String> ids = new java.util.LinkedHashMap<>();
        ids.put("1 - Main", "1");

        store.setPageIds(ids);

        TileKey station = new TileKey("1 - Main", 4, 7);
        TileKey where = new TileKey("1 - Main", 5, 7);

        store.setStation(station, true);
        store.setPointName(station, "Hauptbahnhof");
        store.setCaption(where, station);
        store.createConfiguration("Default", null);

        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(ids);
        reloaded.load();

        assertEquals(reloaded.getCaptionTarget(where), station,
            "the caption does not resolve by page name after a reload, so the diagram has nothing to "
                + "draw even though the file plainly holds it");

        assertEquals(reloaded.captionsFor(station), java.util.Collections.singleton(where),
            "the station cannot find its own caption after a reload");
    }

    /**
     * And a caption placed on the station's own square, which is what an import leaves.
     *
     * The same square on both sides of the entry is the shape most likely to be mangled by a
     * translation that treats keys and values differently.
     */
    @Test
    public void testACaptionOnItsOwnStationResolvesAfterAReload() throws IOException
    {
        java.util.Map<String, String> ids = new java.util.LinkedHashMap<>();
        ids.put("1 - Main", "1");

        store.setPageIds(ids);

        TileKey station = new TileKey("1 - Main", 4, 7);

        store.setStation(station, true);
        store.setPointName(station, "Hauptbahnhof");
        store.setCaption(station, station);
        store.createConfiguration("Default", null);

        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(ids);
        reloaded.load();

        assertEquals(reloaded.getCaptionTarget(station), station,
            "a station captioned on its own square does not resolve after a reload");
    }

    /**
     * A page's EXCLUSION survives a rename too, because it is stored by id like everything else.
     *
     * UR-7, from the uninformed review. `excludedPages` was written raw - `new JSONArray(excludedPages)`
     * - and read raw, alone among the ten shared collections, every other one of which goes through
     * toStored/fromStored. The rule it broke is the class's own, at the top of setPageIds: "A name is
     * something a user renames on a whim, and every key here begins with one, so a rename would
     * otherwise orphan a whole page of names, lengths, directions and pairings at once."
     *
     * So after a rename everything tile-keyed came back onto the new name and the page's exclusion did
     * not: the page silently rejoined autonomy, and the stale name sat in the set for ever because
     * nothing prunes it. That matters most on the layout the setting exists for - a page that redraws
     * another page's sensors, which is `excludeRepeatedSensorPages`' case - because rejoining puts two
     * Points on one sensor, the state that code says nothing downstream can resolve.
     *
     * `renamePage` has no production caller, so every real rename is an EXTERNAL one - in the Central
     * Station, or by editing the gleisbild - which is exactly the case ids exist to survive and the one
     * this test performs.
     *
     * The page name has no colon, so toStored and fromStored return it unchanged: they split a tile key
     * on its page, and there is nothing here to split. Hence the two page-level translators.
     */
    @Test
    public void testAnExcludedPageIsStillExcludedAfterItIsRenamed() throws IOException
    {
        java.util.Map<String, String> before = new java.util.LinkedHashMap<>();
        before.put("Scenery", "3");
        before.put("Old Name", "2");

        store.setPageIds(before);

        store.setPageExcluded("Old Name", true);
        store.createConfiguration("Default", null);

        store.save();

        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("Scenery", "3");
        after.put("New Name", "2");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(after);
        reloaded.load();

        assertTrue(reloaded.getExcludedPages().contains("New Name"),
            "the renamed page is no longer excluded, so it has silently rejoined autonomy - and on the "
            + "layout this setting exists for that puts two Points on one sensor (UR-7)");

        assertFalse(reloaded.getExcludedPages().contains("Old Name"),
            "the old name is still in the set. Nothing prunes it, so it stays there for ever, and a "
            + "page later given that name would be excluded for a reason nobody can find");
    }

    /**
     * And a page the index has never heard of keeps its name, so nothing is lost by translating.
     */
    @Test
    public void testAnExcludedPageWithNoIdKeepsItsName() throws IOException
    {
        java.util.Map<String, String> known = new java.util.LinkedHashMap<>();
        known.put("Known", "2");

        store.setPageIds(known);

        store.setPageExcluded("Not in the index", true);
        store.createConfiguration("Default", null);

        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(known);
        reloaded.load();

        assertTrue(reloaded.getExcludedPages().contains("Not in the index"),
            "a page with no id in the index lost its exclusion. Files written before this change hold "
            + "NAMES, and a page added since the index was read has no id yet - both have to survive");
    }

    /**
     * Renaming a locomotive repairs EVERY configuration, not only the one that is running.
     *
     * UR-9, from the uninformed review. Three places in a configuration hold a locomotive by NAME - the
     * placement, the home assignment and the exclusion list - and neither renameLoc nor deleteLoc
     * touched any of them. `captureFromLayout` launders the ACTIVE configuration back from the running
     * layout, so a rename is repaired there if a capture happens; a configuration that was not active
     * at the time is never touched at all.
     *
     * The consequence is not a lost placement. `parseAuto` refuses a configuration naming a locomotive
     * that is not in the database, and answers a refusal by invalidating the WHOLE layout - so
     * switching to that configuration later stops the railway working, with an error naming a
     * locomotive and nothing connecting it to a rename made days earlier.
     *
     * MarklinControlStation's own comment says which state needs this: "State held by NAME does still
     * need repairing, and there are two such places - the routes below, and autonomy home assignments."
     * There were three.
     */
    @Test
    public void testRenamingALocomotiveRepairsEveryConfiguration() throws IOException
    {
        store.createConfiguration("Running", null);
        store.createConfiguration("Put away", null);
        store.setActiveConfiguration("Running");

        place(store.getConfiguration("Running"), "1:4,4", "Old Name");
        place(store.getConfiguration("Put away"), "1:9,2", "Old Name");

        store.locomotiveRenamed("Old Name", "New Name");

        for (String which : new String[]{"Running", "Put away"})
        {
            org.json.JSONObject point = store.getConfiguration(which).getJSONObject("points")
                .getJSONObject("Running".equals(which) ? "1:4,4" : "1:9,2");

            assertEquals(point.getJSONObject("loc").getString("name"), "New Name",
                which + ": the placement still names the old locomotive. parseAuto refuses a name it "
                + "cannot find and invalidates the WHOLE configuration, so this stops the railway "
                + "working the next time this configuration is chosen (UR-9)");

            assertEquals(point.getString("home"), "New Name",
                which + ": the home assignment still names the old locomotive");

            assertEquals(point.getJSONArray("excludedLocs").getString(0), "New Name",
                which + ": the exclusion still names the old locomotive, so the station it was kept "
                + "out of now accepts it under its new name");
        }
    }

    /**
     * And deleting one takes it out of every configuration rather than leaving a name nothing resolves.
     */
    @Test
    public void testDeletingALocomotiveClearsItFromEveryConfiguration() throws IOException
    {
        store.createConfiguration("Running", null);
        store.createConfiguration("Put away", null);
        store.setActiveConfiguration("Running");

        place(store.getConfiguration("Running"), "1:4,4", "Gone");
        place(store.getConfiguration("Put away"), "1:9,2", "Gone");

        store.locomotiveDeleted("Gone");

        for (String which : new String[]{"Running", "Put away"})
        {
            org.json.JSONObject point = store.getConfiguration(which).getJSONObject("points")
                .getJSONObject("Running".equals(which) ? "1:4,4" : "1:9,2");

            assertFalse(point.has("loc"),
                which + ": a deleted locomotive is still placed. The name resolves to nothing, and "
                + "parseAuto answers that by invalidating the whole configuration (UR-9)");

            assertFalse(point.has("home"), which + ": the home assignment still names it");

            assertEquals(point.getJSONArray("excludedLocs").length(), 0,
                which + ": the exclusion still names it");
        }
    }

    /**
     * And a locomotive that shares nothing but a prefix is left alone.
     */
    @Test
    public void testRepairingOneLocomotiveDoesNotTouchAnother() throws IOException
    {
        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");

        place(store.getConfiguration("Only"), "1:4,4", "BR 01");
        place(store.getConfiguration("Only"), "1:5,4", "BR 011");

        store.locomotiveRenamed("BR 01", "Renamed");

        assertEquals(store.getConfiguration("Only").getJSONObject("points")
            .getJSONObject("1:5,4").getJSONObject("loc").getString("name"), "BR 011",
            "a locomotive whose name merely begins with the renamed one was renamed too");
    }

    /**
     * A placement, a home and an exclusion on one square, as the builder writes them.
     */
    private void place(org.json.JSONObject configuration, String key, String loc)
    {
        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        configuration.getJSONObject("points").put(key, new org.json.JSONObject()
            .put("loc", new org.json.JSONObject().put("name", loc))
            .put("home", loc)
            .put("excludedLocs", new org.json.JSONArray().put(loc)));
    }

    /**
     * Importing a bundle can tell a page RENUMBER from a rename.
     *
     * UR-10, from the uninformed review. `exportBundle` writes the exporter's `pages` map - which id
     * was called what when they wrote it - beside keys built from the exporter's page ids. The merge
     * then starts from `sharedFields()`, which is MY pages map, and keeps what I already have for every
     * inner key. So for any id both files know, the exporter's name was dropped, `readShared` read my
     * own names into `pageNamesWhenWritten`, and the conflict loop compared them against
     * `pageIdToName` - my own names again. The two always matched, so `pageIdConflicts` was guaranteed
     * empty after any import.
     *
     * The detection was disabled by construction, not by an oversight in the check.
     *
     * What it exists to catch is described where the ids are set up: a renumber "would silently reattach
     * a page of settings to the WRONG page, which is worse than losing them, because nothing looks
     * wrong." An import from a layout numbered differently attaches their station names, lengths,
     * one-way directions, portal pairings and captions to my pages, quietly.
     *
     * A rename is still not a conflict - the deciding question is whether the old name still exists
     * somewhere in MY index, which is what tells "the same page, called something else" from "a
     * different page now holds this id".
     */
    @Test
    public void testImportingABundleCanStillSpotARenumberedPage() throws IOException
    {
        java.util.Map<String, String> theirs = new java.util.LinkedHashMap<>();
        theirs.put("Yard", "2");

        AutonomyCompanionStore exporter =
            new AutonomyCompanionStore(java.nio.file.Files.createTempDirectory("tc-export").toFile());

        exporter.setPageIds(theirs);
        exporter.createConfiguration("Theirs", null);
        exporter.setPointName(new TileKey("Yard", 3, 3), "Their siding");

        org.json.JSONObject bundle = exporter.exportBundle("Theirs");

        assertNotNull(bundle, "nothing was exported, so nothing below tests anything");

        // Here, id 2 is a different page - and the page THEY called Yard is my id 3.  That is a
        // renumber: adopting their keys would put their siding on my "Main".
        java.util.Map<String, String> mine = new java.util.LinkedHashMap<>();
        mine.put("Main", "2");
        mine.put("Yard", "3");

        store.setPageIds(mine);
        store.setPointName(new TileKey("Main", 1, 1), "Mine");

        store.importBundle("Imported", bundle);

        assertFalse(store.getPageIdConflicts().isEmpty(),
            "importing a bundle from a layout whose pages are numbered differently reported no "
            + "conflict. The check compares the names in the file against my own - and the merge had "
            + "already replaced theirs with mine, so it was comparing my names with my names and could "
            + "never disagree (UR-10)");
    }

    /**
     * An import that fails leaves nothing behind, the configuration included.
     *
     * UR-10, second half. The shared merge is rolled back when `readShared` throws - it uses the
     * type-strict accessors on an object assembled out of somebody else's file, and the comment there
     * says why that matters: clearing first "emptied the shared half and then failed, and the panel's
     * 'import unreadable' told the user nothing had happened while the store stood blank".
     *
     * The configuration was installed BEFORE any of that and was not rolled back with it. So an
     * unreadable bundle left a configuration whose placements, homes and exclusions refer to points the
     * rollback had just taken away - and it is offered in the configuration list like any other.
     */
    @Test
    public void testAFailedImportLeavesNoConfigurationBehind() throws IOException
    {
        store.setPointName(new TileKey("Main", 1, 1), "Mine");
        store.createConfiguration("Kept", null);

        org.json.JSONObject bundle = new org.json.JSONObject();

        bundle.put(AutonomyCompanionStore.EXPORT_CONFIGURATION,
            new org.json.JSONObject().put("name", "Theirs"));

        // A point name that is a NUMBER.  readShared's accessors are type-strict and throw on it -
        // which is the case the rollback exists for.
        bundle.put(AutonomyCompanionStore.EXPORT_SHARED, new org.json.JSONObject()
            .put("pointNames", new org.json.JSONObject().put("1:7,7", 5)));

        try
        {
            store.importBundle("Broken", bundle);
        }
        catch (RuntimeException expected)
        {
            // the panel reports this as "import unreadable"
        }

        assertEquals(store.getPointName(new TileKey("Main", 1, 1)), "Mine",
            "the shared half was not put back, which is what the rollback is for");

        assertFalse(store.getConfigurationNames().contains("Broken"),
            "the failed import left its configuration behind. Its placements, homes and exclusions "
            + "refer to points the rollback has just taken away, and it is offered in the list like "
            + "any other (UR-10)");
    }

    /**
     * A station that was never named goes when its tile does.
     *
     * UR-12, from the uninformed review. `stations` is pruned only as a side effect of pruning
     * `pointNames` - the loop walks the NAMES that no longer have a square - so a square carrying a
     * station designation and no name is never visited. There is no `dropMissingMembers(stations, keys)`
     * to match the one written for `disabledPortals` twenty lines above.
     *
     * An unnamed station is an ordinary state rather than a corner case: `setStation` asks for no name,
     * and `placeCaption` has a dedicated "not named yet" answer for exactly this.
     *
     * What it costs: mark a sensor as a station, do not name it yet, delete the tile later. The square
     * stays in `stations` for good, so redrawing a sensor at those coordinates - routine when a page is
     * re-laid-out - makes it silently a station again. And `checkNames` raises UNNAMED_STATION as an
     * ERROR, so the user gets a blocking finding they did not create, about a square they cannot see.
     *
     * A NAMED station is deliberately not dropped this way: the loop above keeps one whose name a
     * configuration still refers to, so the user can find it. Nothing can refer to an unnamed one, so
     * that rule has nothing to say here.
     */
    @Test
    public void testAnUnnamedStationGoesWhenItsTileDoes()
    {
        TileKey unnamed = new TileKey("1 - Main", 3, 3);
        TileKey named = new TileKey("1 - Main", 4, 3);

        // A station with no name whose tile IS still there (CR-C3).
        //
        // Without it this test only pinned one direction. A reviewer mutated the rule to delete every
        // unnamed station rather than only the ones whose tile has gone - which silently strips a
        // designation off live track - and 184 tests across five classes stayed green, because no
        // fixture anywhere had a live unnamed station in it. An unnamed station is an ordinary state:
        // setStation asks for no name.
        TileKey unnamedButPresent = new TileKey("1 - Main", 5, 3);

        store.setStation(unnamed, true);

        store.setStation(unnamedButPresent, true);

        store.setStation(named, true);
        store.setPointName(named, "Still here");

        AutonomyCompanionStore.Reconciliation report = store.reconcile(
            new java.util.LinkedHashSet<>(java.util.Arrays.asList(named, unnamedButPresent)));

        assertFalse(store.isStation(unnamed),
            "an unnamed station outlived its tile. It sits in setup.json for good, so a sensor drawn "
            + "at those coordinates later is silently a station - and checkNames raises a blocking "
            + "UNNAMED_STATION finding about a square nobody can see (UR-12)");

        assertTrue(store.isStation(named), "the station whose tile is still there was dropped");

        assertTrue(store.isStation(unnamedButPresent),
            "a station whose tile is STILL THERE was dropped because it has no name. The rule is "
            + "about squares the track no longer has, and an unnamed station is an ordinary thing - "
            + "setStation asks for no name. Deleting it strips a designation off live track, and "
            + "silently: nothing tells the operator the square stopped being a station (CR-C3)");

        assertFalse(report.getDroppedTileProperties().isEmpty(),
            "the square was dropped without saying so. A diagram edit that quietly costs a station "
            + "should be visible rather than discovered later - which is the rule the links next to "
            + "it already follow");
    }

    /**
     * A protecting signal goes when the SIGNAL's tile does, not only when the station's does.
     *
     * UR-13, from the uninformed review. `dropMissing(stationSignals, keys, false)` tests the KEY - the
     * station's square. `stationSignals` is the only square-referencing collection whose VALUE
     * reconcile never checked: portals are checked on both ends, and captions inside reconcileCaptions.
     *
     * `forgetSquares` covers a signal square that is BUILT OVER, so what is left is the plain deletion,
     * and the pairing then survives every save. `signalsThatAreGone()` reports it, so it is not
     * invisible - but nothing drops it, and if any accessory-bearing tile is later drawn at those
     * coordinates, `protectingSignalNames()` resolves it and autonomy starts throwing an accessory
     * nobody paired. That is the same defect the neighbouring drop was written for - "INHERITED by the
     * next link drawn on that square" - applied to the one collection that commands real hardware.
     *
     * The plan this came from says the intended rule outright: dropped in reconcile when EITHER tile
     * goes.
     */
    @Test
    public void testAPairingGoesWhenTheSignalsTileDoes()
    {
        TileKey station = new TileKey("1 - Main", 6, 6);
        TileKey stays = new TileKey("1 - Main", 7, 6);
        TileKey deleted = new TileKey("1 - Main", 8, 6);

        store.setStation(station, true);
        store.setPointName(station, "Guarded");
        store.setProtectingSignals(station, java.util.Arrays.asList(stays, deleted));

        store.reconcile(new java.util.LinkedHashSet<>(java.util.Arrays.asList(station, stays)));

        assertEquals(store.getProtectingSignals(station), java.util.Arrays.asList(stays),
            "the pairing with the deleted signal survived. Draw anything with an address at those "
            + "coordinates later and autonomy throws an accessory nobody paired (UR-13)");
    }

    /**
     * And a station whose signals have all gone stops being paired at all.
     */
    @Test
    public void testAStationWhoseOnlySignalWentIsNoLongerPaired()
    {
        TileKey station = new TileKey("1 - Main", 6, 8);
        TileKey deleted = new TileKey("1 - Main", 7, 8);

        store.setStation(station, true);
        store.setPointName(station, "Was guarded");
        store.setProtectingSignals(station, java.util.Arrays.asList(deleted));

        store.reconcile(new java.util.LinkedHashSet<>(java.util.Arrays.asList(station)));

        assertTrue(store.getProtectingSignals(station).isEmpty(),
            "the station is still recorded as protected by a signal that no longer exists");
    }

    /**
     * A load that fails on the CONTENT leaves the setup exactly as it was, like one that fails on the file.
     *
     * UR-14, from the uninformed review. load()'s own comment promises this outright: "Read and parse
     * BEFORE anything is thrown away ... A load that fails now leaves the setup empty" - and it is true
     * of a parse failure, which happens before `clear()`. It was not true of a TYPE failure.
     * `readShared` runs after `clear()` and uses the strict accessors throughout - `getString`, `getInt`
     * - each of which throws part way through, with the store already empty.
     *
     * What that costs is what the comment says it costs: "The caller then had a live, blank store:
     * every station, name and direction gone from the screen, and one press of Save away from being
     * gone from the disk as well." `discardEdits` leaves `dirty` set over an empty store, and the next
     * save on the way out writes that over setup.json.
     *
     * `importBundle` really is guarded, and its comment cites load() as the model it was following.
     *
     * The trigger is a setup.json that TrainControl did not write - hand-edited, or from another tool.
     * Every field this build writes round-trips, so this cannot happen by itself today; the guarantee
     * is what is being tested, not the odds.
     */
    @Test
    public void testALoadThatFailsOnATypeLeavesTheSetupAlone() throws IOException
    {
        store.setPointName(new TileKey("1 - Main", 2, 2), "Still here");
        store.setStation(new TileKey("1 - Main", 2, 2), true);
        store.createConfiguration("Mine", null);
        store.save();

        // A point name that is a NUMBER, which the strict accessors refuse - written straight into the
        // file, as a hand edit would leave it.
        java.io.File file = new java.io.File(new java.io.File(layout, "config/autonomy"), "setup.json");

        org.json.JSONObject root = new org.json.JSONObject(new String(
            java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));

        root.getJSONObject("pointNames").put("1:9,9", 7);

        java.nio.file.Files.write(file.toPath(),
            root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));

        AutonomyCompanionStore reopened = new AutonomyCompanionStore(layout);

        reopened.setPointName(new TileKey("1 - Main", 5, 5), "What was there before");

        try
        {
            reopened.load();
        }
        catch (RuntimeException | IOException expected)
        {
            // the caller reports this as "the setup could not be read"
        }

        assertEquals(reopened.getPointName(new TileKey("1 - Main", 5, 5)), "What was there before",
            "a load that failed on the CONTENT emptied the store, which is what the same method's "
            + "comment promises it no longer does. The caller is left with a live blank store, one "
            + "press of Save away from writing that over setup.json (UR-14)");
    }

    /**
     * A failed import that was REPLACING a configuration puts the old one back.
     *
     * UR-10, second half, and the branch the first test of this pair did not reach. Importing over an
     * existing name replaces it - the panel says so and asks first - but that consents to being
     * replaced by a good file, not to losing the configuration to an unreadable one.
     *
     * The rollback used to take the configuration back only when the import had ADDED it, reasoning
     * that removing an existing name would take the user's own work with it. That was a false choice:
     * the object being replaced can be kept and put back, which is what load() does with snapshotSetup.
     */
    @Test
    public void testAFailedImportPutsBackTheConfigurationItWasReplacing() throws IOException
    {
        store.setPointName(new TileKey("Main", 1, 1), "Mine");
        store.createConfiguration("Ours", null);

        store.getConfiguration("Ours").put("points", new org.json.JSONObject()
            .put("1:4,4", new org.json.JSONObject().put("home", "BR 218")));

        org.json.JSONObject bundle = new org.json.JSONObject();

        bundle.put(AutonomyCompanionStore.EXPORT_CONFIGURATION,
            new org.json.JSONObject().put("name", "Theirs"));

        // A point name that is a NUMBER, which readShared's strict accessors refuse
        bundle.put(AutonomyCompanionStore.EXPORT_SHARED, new org.json.JSONObject()
            .put("pointNames", new org.json.JSONObject().put("1:7,7", 5)));

        try
        {
            store.importBundle("Ours", bundle);
        }
        catch (RuntimeException expected)
        {
            // the panel reports this as "import unreadable"
        }

        assertTrue(store.getConfigurationNames().contains("Ours"),
            "the configuration the import was replacing is gone. Importing over a name consents to "
            + "being replaced by a GOOD file, not to losing the configuration to a bad one (UR-10)");

        assertEquals(store.getConfiguration("Ours").getJSONObject("points")
            .getJSONObject("1:4,4").optString("home", null), "BR 218",
            "the name survived but the configuration behind it is the imported one, so the user's own "
            + "placements, homes and exclusions are gone");

        assertEquals(store.getPointName(new TileKey("Main", 1, 1)), "Mine",
            "the shared half was not put back, which is what the rollback is for");
    }

    /**
     * A locomotive renamed while an editor is open is repaired in the snapshot Cancel would put back.
     *
     * The diagram editor holds the whole setup as it was when it opened, so that cancelling undoes
     * every edit made in that window. A rename made meanwhile repairs the live store - and left the
     * snapshot naming the old locomotive, so pressing Cancel wrote it back.
     *
     * What that costs is not a lost name. `parseAuto` refuses a configuration naming a locomotive that
     * is not in the database and answers a refusal by invalidating the WHOLE layout, so the rename
     * quietly armed that, to go off whenever somebody happened to cancel an editor session.
     *
     * Repaired rather than refused: blocking renames while an editor is open takes away something
     * reasonable to do, and the two windows are about different things.
     */
    @Test
    public void testARenameReachesTheSnapshotACancelPutsBack() throws IOException
    {
        store.createConfiguration("Running", null);
        store.setActiveConfiguration("Running");

        place(store.getConfiguration("Running"), "1:4,4", "Old Name");

        // What the editor takes when it opens
        org.json.JSONObject asOpened = store.snapshotSetup();

        store.locomotiveRenamed("Old Name", "New Name");

        AutonomyCompanionStore.repairLocomotiveInSetup(asOpened, "Old Name", "New Name");

        // What Cancel does
        store.restoreSetup(asOpened);

        org.json.JSONObject point = store.getConfiguration("Running")
            .getJSONObject("points").getJSONObject("1:4,4");

        assertEquals(point.getJSONObject("loc").getString("name"), "New Name",
            "cancelling the editor put the old locomotive name back. It is not in the database any "
            + "more, and parseAuto answers a name it cannot find by invalidating the whole layout - so "
            + "the rename armed that, to go off whenever somebody cancelled");

        assertEquals(point.getString("home"), "New Name",
            "the home assignment in the restored snapshot still names the old locomotive");

        assertEquals(point.getJSONArray("excludedLocs").getString(0), "New Name",
            "the exclusion in the restored snapshot still names the old locomotive");
    }

    /**
     * And a deletion clears it from the snapshot rather than leaving a name nothing resolves.
     */
    @Test
    public void testADeletionReachesTheSnapshotToo() throws IOException
    {
        store.createConfiguration("Running", null);
        store.setActiveConfiguration("Running");

        place(store.getConfiguration("Running"), "1:4,4", "Gone");

        org.json.JSONObject asOpened = store.snapshotSetup();

        store.locomotiveDeleted("Gone");

        AutonomyCompanionStore.repairLocomotiveInSetup(asOpened, "Gone", null);

        store.restoreSetup(asOpened);

        org.json.JSONObject point = store.getConfiguration("Running")
            .getJSONObject("points").getJSONObject("1:4,4");

        assertFalse(point.has("loc"),
            "cancelling put back a placement for a locomotive that has been deleted");

        assertFalse(point.has("home"), "cancelling put back a home for a deleted locomotive");

        assertEquals(point.getJSONArray("excludedLocs").length(), 0,
            "cancelling put back an exclusion naming a deleted locomotive");
    }

    /**
     * A rename reaches a PAGE snapshot too - what the editor's undo stack holds.
     *
     * The third holder of the same names, and the one that reaches disk. Cancel restores the setup as
     * it was when the window opened; Ctrl+Z restores one page snapshot and SAVES. So a rename made
     * while the editor is open was repaired in the live store and in the Cancel snapshot, and an undo
     * afterwards wrote the old name back over both - which parseAuto answers by invalidating the whole
     * layout, days later, with nothing connecting it to the rename.
     *
     * Found by review, after the Cancel door had been fixed and reported as done. Two ways of saying
     * the same thing, one of them covered - which is this codebase's recurring shape.
     */
    @Test
    public void testARenameReachesAPageSnapshotTheUndoStackHolds() throws IOException
    {
        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");

        place(store.getConfiguration("Only"), "1:4,4", "Old Name");

        // What the editor pushes onto its undo stack for every edit
        java.util.Map<String, Object> pushed = store.snapshotPage("1");

        store.locomotiveRenamed("Old Name", "New Name");

        AutonomyCompanionStore.repairLocomotiveInPageSnapshot(pushed, "Old Name", "New Name");

        // What Ctrl+Z does
        store.restorePage("1", pushed);

        org.json.JSONObject point = store.getConfiguration("Only")
            .getJSONObject("points").getJSONObject("1:4,4");

        assertEquals(point.getJSONObject("loc").getString("name"), "New Name",
            "undoing an edit put the old locomotive name back, and restoring a page SAVES - so this "
            + "is on disk, and a configuration naming a locomotive that is not in the database "
            + "invalidates the whole layout");

        assertEquals(point.getString("home"), "New Name",
            "the home assignment in the restored page still names the old locomotive");

        assertEquals(point.getJSONArray("excludedLocs").getString(0), "New Name",
            "the exclusion in the restored page still names the old locomotive");
    }

    /**
     * Undo re-opens a link whose two halves are on different pages (SVN-B8).
     *
     * A portal is a pair of squares on two pages - that is what it is for - and `setPortalDisabled`
     * writes **both** ends. `453a3ef4` taught `PairMapKept` and `ListMapKept` to capture an entry with
     * either end on the page being snapshotted, and left the set kind alone; `disabledLinks` is a set,
     * and it is the one set whose members are paired across pages.
     *
     * So: link open, snapshot page 1 (captures nothing about it), shut the link (both ends go in),
     * undo page 1. The members on page 1 are dropped and nothing is put back, leaving the far half
     * alone in the set - and `isPortalDisabled` answers through the partner, so **the undo did not
     * re-open the link**. What reaches disk is `disabledLinks` with one end in it, the shape
     * `TileGraph.portalClosed` calls out as having no migration.
     *
     * Both directions are asserted, because a fix that simply cleared the set would pass the first.
     *
     * MUTATION: constructing `disabledLinks` as an unpaired `SquareSetKept` fails this.
     */
    @Test
    public void testUndoReopensALinkWhoseHalvesAreOnDifferentPages() throws IOException
    {
        TileKey here = new TileKey("1", 10, 9);
        TileKey there = new TileKey("5", 15, 5);

        store.pairPortals(here, there);

        assertFalse(store.isPortalDisabled(here), "precondition: the link starts open");

        // What the editor pushes before the gesture.
        java.util.Map<String, Object> pushed = store.snapshotPage("1");

        store.setPortalDisabled(here, true);

        assertTrue(store.isPortalDisabled(here) && store.isPortalDisabled(there),
            "precondition: shutting a link shuts both ends of it");

        // Ctrl+Z.
        store.restorePage("1", pushed);

        assertFalse(store.isPortalDisabled(here),
            "undo did not re-open the link.  The snapshot of page 1 was taken before the link was "
            + "shut and captured nothing, because the set kind only ever looked at members ON the "
            + "page - so the undo dropped the near half and left the far one, and "
            + "isPortalDisabled answers through the partner (SVN-B8)");

        assertFalse(store.isPortalDisabled(there),
            "the far half is still in disabledLinks on its own, so disabledLinks reaches disk with "
            + "one end of a pair in it - the shape TileGraph.portalClosed says has no migration");
    }

    /**
     * A rename reaches a setup that nothing has open.
     *
     * OB-062. A locomotive rename has to reach the database, the setup in memory and the setup on
     * disk. With no session built the window did the last of those not at all, on the reasoning that
     * the file "is read the next time it IS opened - by which time this rename is already in the
     * locomotive database".
     *
     * Nothing repairs locomotive names at load, so that is not what happens. The old name survives in
     * the placement, the home and the exclusions until somebody chooses that configuration - and
     * parseAuto answers a locomotive it cannot resolve by invalidating the whole layout, days later,
     * with nothing connecting it to the rename.
     */
    @Test
    public void testARenameReachesASetupNothingHasOpen() throws IOException
    {
        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");

        place(store.getConfiguration("Only"), "1:4,4", "Old Name");

        store.save();

        assertTrue(AutonomyCompanionStore.repairLocomotiveOnDisk(layout, "Old Name", "New Name"),
            "the setup on disk was not found, so nothing below tests anything");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        org.json.JSONObject point = reloaded.getConfiguration("Only")
            .getJSONObject("points").getJSONObject("1:4,4");

        assertEquals(point.getJSONObject("loc").getString("name"), "New Name",
            "the placement still names the old locomotive. Choosing this configuration invalidates "
            + "the whole layout, and nothing about the message says a rename caused it");

        assertEquals(point.getString("home"), "New Name",
            "the home assignment still names the old locomotive");

        assertEquals(point.getJSONArray("excludedLocs").getString(0), "New Name",
            "the exclusion still names the old locomotive");
    }

    /**
     * And a delete reaches it the same way.
     */
    @Test
    public void testADeleteReachesASetupNothingHasOpen() throws IOException
    {
        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");

        place(store.getConfiguration("Only"), "1:4,4", "Going");

        store.save();

        assertTrue(AutonomyCompanionStore.repairLocomotiveOnDisk(layout, "Going", null),
            "the setup on disk was not found, so nothing below tests anything");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        org.json.JSONObject point = reloaded.getConfiguration("Only")
            .getJSONObject("points").getJSONObject("1:4,4");

        assertFalse(point.has("loc") && point.getJSONObject("loc").has("name")
            && "Going".equals(point.getJSONObject("loc").getString("name")),
            "a deleted locomotive is still placed here");
    }

    /**
     * A layout that has never had a setup does not acquire one.
     *
     * This is the whole reason the window declines to build a SESSION on a rename: doing so opens
     * every page, runs the caption migration, can raise a dialog and then writes a setup.json - so
     * renaming a locomotive would create autonomy out of nothing, on a layout where nobody has ever
     * asked for it. The file repair must not smuggle that back in by a shorter route.
     */
    @Test
    public void testRepairingALayoutWithNoSetupCreatesNothing() throws IOException
    {
        File bare = Files.createTempDirectory("tc-no-setup").toFile();

        try
        {
            assertFalse(AutonomyCompanionStore.repairLocomotiveOnDisk(bare, "Old Name", "New Name"),
                "a layout with no setup reported that it had repaired one");

            assertFalse(new File(bare, "config").exists(),
                "renaming a locomotive created an autonomy setup on a layout that never had one");
        }
        finally
        {
            delete(bare);
        }
    }

    /**
     * Repairing on disk changes the locomotive and nothing else - the page record above all.
     *
     * Nobody calls setPageIds on a bare store: there is no session to tell it what the pages are
     * called. So pageIdToName is empty, and sharedFields() writes the file's "pages" record from
     * exactly that map - which means saving would replace it with {}.
     *
     * That record is the only evidence a page renumber ever happened. readShared compares it against
     * the current index to tell a rename from a renumber, and pageOf resolves every stored id through
     * it. Blanking it is the same data loss this class was repaired for in the commit before this one,
     * arriving by a new door: a locomotive rename would quietly disarm the detection for the whole
     * setup, and the next renumber would go through unnoticed.
     *
     * Caught by probing the method rather than by reading it, which is the only reason it is not in the
     * repository.
     */
    @Test
    public void testRepairingOnDiskChangesOnlyTheLocomotive() throws IOException
    {
        store.setPageIds(twoPages());

        TileKey square = new TileKey("Main", 4, 4);

        store.setPointName(square, "Platform");
        store.setStation(square, true);
        store.setTileLength(square, 11);
        store.setPageExcluded("Yard", true);

        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");
        place(store.getConfiguration("Only"), "1:4,4", "Old Name");

        store.save();

        String before = new String(Files.readAllBytes(new File(layout, "config/autonomy/setup.json").toPath()), StandardCharsets.UTF_8);

        assertTrue(before.contains("\"Main\""),
            "the fixture never recorded its page names, so nothing below tests anything: " + before);

        assertTrue(AutonomyCompanionStore.repairLocomotiveOnDisk(layout, "Old Name", "New Name"));

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(twoPages());
        reloaded.load();

        assertEquals(reloaded.getPointName(square), "Platform",
            "repairing a locomotive lost the point names");
        assertTrue(reloaded.isStation(square), "repairing a locomotive lost the stations");
        assertEquals(reloaded.getTileLength(square), 11, "repairing a locomotive lost the lengths");
        assertTrue(reloaded.getExcludedPages().contains("Yard"),
            "repairing a locomotive lost the excluded pages");

        String after = new String(Files.readAllBytes(new File(layout, "config/autonomy/setup.json").toPath()), StandardCharsets.UTF_8);

        assertTrue(after.contains("\"Main\"") && after.contains("\"Yard\""),
            "the page record was blanked by a LOCOMOTIVE rename. It is the only evidence a renumber "
            + "ever happened - readShared tells a rename from a renumber by it, and pageOf resolves "
            + "every stored id through it - so the detection is now disarmed for this whole setup: "
            + after);
    }

    /**
     * Two pages, by the numbering a file would have been written under.
     */
    private java.util.Map<String, String> twoPages()
    {
        java.util.Map<String, String> pages = new java.util.LinkedHashMap<>();

        pages.put("Main", "1");
        pages.put("Yard", "2");

        return pages;
    }

    /**
     * A page rename survives a save and a load, and takes nothing else with it.
     *
     * Adam's shape for this, and the right one: "as long as names change in the objects, and
     * reads/writes of the saved state restore the exact data, then we should be good... a mutation, a
     * check, a save, a load, and verification that the mutation is still there (while rest staying the
     * same)."
     *
     * Both halves matter and the second is the one this week kept failing. Every loss so far has been a
     * rename that worked perfectly in memory and then took a page of settings with it on the way to
     * disk - pruned by a reconcile against stale names, or reattached by a renumber. Checking only the
     * memory would have passed throughout.
     *
     * So the fixture carries one of everything the store holds, on TWO pages, and the untouched page is
     * asserted as hard as the renamed one.
     */
    @Test
    public void testARenamedPageSurvivesASaveAndLoad() throws IOException
    {
        store.setPageIds(twoPages());

        TileKey moving = new TileKey("Main", 4, 4);
        TileKey signal = new TileKey("Main", 5, 4);
        TileKey plaque = new TileKey("Main", 4, 5);
        TileKey doorway = new TileKey("Main", 6, 6);

        TileKey elsewhere = new TileKey("Yard", 1, 1);
        TileKey farDoor = new TileKey("Yard", 2, 2);

        store.setPointName(moving, "Platform One");
        store.setStation(moving, true);
        store.setTileLength(moving, 14);
        store.setTileDirection(moving, new RouteId(0, 0), Direction.TOWARD_B);
        store.setProtectingSignals(moving, java.util.Arrays.asList(signal));
        store.setCaption(plaque, moving);
        store.setBlockingPoints(moving, java.util.Arrays.asList(elsewhere));
        store.pairPortals(doorway, farDoor);

        store.setPointName(elsewhere, "Yard Throat");
        store.setStation(elsewhere, true);
        store.setTileLength(elsewhere, 3);

        store.setPageExcluded("Yard", true);

        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");
        place(store.getConfiguration("Only"), "Main:4,4", "BR 232");

        store.save();

        // --- the mutation ---------------------------------------------------------------------
        store.renamePage("Main", "Mainline");

        TileKey renamed = new TileKey("Mainline", 4, 4);
        TileKey renamedPlaque = new TileKey("Mainline", 4, 5);
        TileKey renamedDoor = new TileKey("Mainline", 6, 6);

        // --- the check, in memory -------------------------------------------------------------
        assertEverythingIsWhereItShouldBe(store, renamed, renamedPlaque, renamedDoor, elsewhere,
            "in memory, immediately after the rename");

        // --- the save, and the load ------------------------------------------------------------
        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("Mainline", "1");
        after.put("Yard", "2");

        store.setPageIds(after);
        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(after);
        reloaded.load();

        // --- and the check again ----------------------------------------------------------------
        assertEverythingIsWhereItShouldBe(reloaded, renamed, renamedPlaque, renamedDoor, elsewhere,
            "after a save and a load - so the rename was right in memory and wrong on disk, which is "
            + "every page-rename defect this week");

        assertNull(reloaded.getPointName(new TileKey("Main", 4, 4)),
            "the old page name still carries the setup, so the rename left a copy behind");
    }

    /**
     * The same, through the door used when nothing has the setup open.
     *
     * `renamePageOnDisk` had no test at all. It reads the file twice on purpose - the first read has no
     * page numbering to work with, so every key comes back in ID form, and renamePage works on NAMES
     * and would match nothing against those. Delete the second read and this is the test that notices.
     */
    @Test
    public void testARenamedPageSurvivesTheOnDiskDoor() throws IOException
    {
        store.setPageIds(twoPages());

        TileKey moving = new TileKey("Main", 4, 4);
        TileKey signal = new TileKey("Main", 5, 4);
        TileKey plaque = new TileKey("Main", 4, 5);
        TileKey doorway = new TileKey("Main", 6, 6);
        TileKey elsewhere = new TileKey("Yard", 1, 1);
        TileKey farDoor = new TileKey("Yard", 2, 2);

        store.setPointName(moving, "Platform One");
        store.setStation(moving, true);
        store.setTileLength(moving, 14);
        store.setTileDirection(moving, new RouteId(0, 0), Direction.TOWARD_B);
        store.setProtectingSignals(moving, java.util.Arrays.asList(signal));
        store.setCaption(plaque, moving);
        store.setBlockingPoints(moving, java.util.Arrays.asList(elsewhere));
        store.pairPortals(doorway, farDoor);

        store.setPointName(elsewhere, "Yard Throat");
        store.setStation(elsewhere, true);
        store.setTileLength(elsewhere, 3);

        store.setPageExcluded("Yard", true);
        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");
        place(store.getConfiguration("Only"), "Main:4,4", "BR 232");
        store.save();

        // the mutation, by the door the window uses when no session is open
        assertTrue(AutonomyCompanionStore.renamePageOnDisk(layout, "Main", "Mainline"),
            "the setup on disk was not found, so nothing below tests anything");

        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("Mainline", "1");
        after.put("Yard", "2");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(after);
        reloaded.load();

        assertEverythingIsWhereItShouldBe(reloaded,
            new TileKey("Mainline", 4, 4), new TileKey("Mainline", 4, 5),
            new TileKey("Mainline", 6, 6), elsewhere,
            "after renaming through the on-disk door");
    }

    /**
     * Everything the fixture above put in, asserted where it should now be - and the other page
     * asserted just as hard, because "the rest staying the same" is half the rule.
     */
    private void assertEverythingIsWhereItShouldBe(AutonomyCompanionStore check, TileKey renamed,
        TileKey plaque, TileKey doorway, TileKey elsewhere, String when)
    {
        assertEquals(check.getPointName(renamed), "Platform One", "the name, " + when);
        assertTrue(check.isStation(renamed), "the station flag, " + when);
        assertEquals(check.getTileLength(renamed), 14, "the length, " + when);
        assertEquals(check.getTileDirection(renamed, new RouteId(0, 0)), Direction.TOWARD_B,
            "the direction, " + when);

        assertEquals(check.getCaptionTarget(plaque), renamed,
            "the caption points at the station it is about, " + when);

        assertEquals(check.getPortalPartner(doorway), new TileKey("Yard", 2, 2),
            "the portal's far end, " + when);

        assertFalse(check.getProtectingSignals(renamed).isEmpty(),
            "the protecting signal, " + when);

        assertFalse(check.getBlockingPoints(renamed).isEmpty(),
            "the blocking point, " + when);

        // and the page nobody touched
        assertEquals(check.getPointName(elsewhere), "Yard Throat", "the OTHER page's name, " + when);
        assertTrue(check.isStation(elsewhere), "the OTHER page's station, " + when);
        assertEquals(check.getTileLength(elsewhere), 3, "the OTHER page's length, " + when);
        assertTrue(check.getExcludedPages().contains("Yard"),
            "the OTHER page is still excluded, " + when);

        assertTrue(check.getConfiguration("Only").getJSONObject("points").has("Mainline:4,4"),
            "the configuration's placement followed the rename, " + when);
    }

    /**
     * Deleting a page through the on-disk door forgets BOTH halves of the setup.
     *
     * This is the test the second read in repairOnDisk exists for, and the one that notices when it is
     * taken out - the rename above does not, which is worth writing down.
     *
     * The shared half is keyed by page ID on disk, so a rename needs nothing from that read: the id
     * does not move and the name follows the index. Configuration points are keyed by page NAME, and
     * renamePage rewrites those whether the keys are in name form or not. So a rename works either way.
     *
     * A delete does not. deletePage gathers a page's squares by asking isOnPage of every key, and
     * against ID-form keys - which is what one read leaves behind - that question is false for every
     * one of them. Without the second read the configurations are cleaned and the shared half is not:
     * the names, stations, lengths, directions, captions and pairings of a deleted page all stay in
     * setup.json, keyed to an id that has been retired.
     */
    @Test
    public void testADeletedPageIsForgottenThroughTheOnDiskDoor() throws IOException
    {
        store.setPageIds(twoPages());

        TileKey going = new TileKey("Yard", 1, 1);
        TileKey staying = new TileKey("Main", 4, 4);

        store.setPointName(going, "Yard Throat");
        store.setStation(going, true);
        store.setTileLength(going, 8);
        store.setTileDirection(going, new RouteId(0, 0), Direction.TOWARD_A);

        store.setPointName(staying, "Platform One");
        store.setStation(staying, true);

        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");
        place(store.getConfiguration("Only"), "Yard:1,1", "BR 232");
        place(store.getConfiguration("Only"), "Main:4,4", "MY 1106");

        store.save();

        assertTrue(AutonomyCompanionStore.deletePageOnDisk(layout, "Yard"),
            "the setup on disk was not found, so nothing below tests anything");

        // reloaded under the numbering that survives the delete
        java.util.Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("Main", "1");

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.setPageIds(after);
        reloaded.load();

        assertFalse(reloaded.getConfiguration("Only").getJSONObject("points").has("Yard:1,1"),
            "the deleted page is still placed in the configuration");

        // the SHARED half - the part one read leaves behind
        String written = new String(java.nio.file.Files.readAllBytes(
            new File(layout, "config/autonomy/setup.json").toPath()), StandardCharsets.UTF_8);

        assertFalse(written.contains("Yard Throat"),
            "the deleted page's station name is still in setup.json, keyed to an id that has been "
            + "retired. The configurations were cleaned and the shared half was not, which is what "
            + "happens when the keys are still in ID form and deletePage asks isOnPage by NAME: "
            + written);

        assertTrue(written.contains("Platform One"),
            "deleting one page took the other page's setup with it: " + written);
    }

    /**
     * A locomotive rename reaches the captured timetable, and survives a save and a load.
     *
     * OB-069. The repair's own note enumerated three holders of a locomotive's name - the placement,
     * the home and the exclusion list - and all three are inside "points". The captured timetable is
     * not: it rides in "globals", and every entry names its locomotive.
     *
     * Left behind, the entry named a locomotive that no longer exists, and the loader answered that by
     * discarding the ENTIRE timetable - then the next capture wrote the emptiness back permanently.
     */
    @Test
    public void testARenameReachesTheCapturedTimetable() throws IOException
    {
        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");

        timetable(store.getConfiguration("Only"), "Old Name", "Other Loco");

        store.save();

        // --- the mutation ---------------------------------------------------------------------
        store.locomotiveRenamed("Old Name", "New Name");

        assertEquals(legOf(store, 0), "New Name", "in memory, the renamed leg");
        assertEquals(legOf(store, 1), "Other Loco", "in memory, the leg that was not renamed");

        // --- save, load, and check again --------------------------------------------------------
        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        assertEquals(legOf(reloaded, 0), "New Name",
            "the timetable leg still names the old locomotive after a save and a load. The loader "
            + "cannot resolve it, so that leg is dropped on every load - and the next capture writes "
            + "the loss back permanently");

        assertEquals(legOf(reloaded, 1), "Other Loco",
            "renaming one locomotive changed another's timetable leg");
    }

    /**
     * And deleting one takes its legs with it, rather than leaving the loader to drop them for ever.
     */
    @Test
    public void testADeleteRemovesThatLocomotivesTimetableLegs() throws IOException
    {
        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");

        timetable(store.getConfiguration("Only"), "Going", "Staying");

        store.save();

        store.locomotiveDeleted("Going");

        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);
        reloaded.load();

        org.json.JSONArray legs = reloaded.getConfiguration("Only")
            .getJSONObject("globals").getJSONArray("timetable");

        assertEquals(legs.length(), 1, "the deleted locomotive still has a timetable leg: " + legs);
        assertEquals(legs.getJSONObject(0).getString("loc"), "Staying",
            "deleting one locomotive took another's leg with it");
    }

    /**
     * Two timetable legs, one per locomotive, in the place a captured timetable actually lives.
     */
    private void timetable(org.json.JSONObject configuration, String first, String second)
    {
        org.json.JSONArray legs = new org.json.JSONArray();

        for (String loc : new String[] {first, second})
        {
            legs.put(new org.json.JSONObject()
                .put("loc", loc)
                .put("path", new org.json.JSONArray())
                .put("executionTime", 0)
                .put("secondsToNext", 0));
        }

        if (!configuration.has("globals")) configuration.put("globals", new org.json.JSONObject());

        configuration.getJSONObject("globals").put("timetable", legs);
    }

    private String legOf(AutonomyCompanionStore from, int at)
    {
        return from.getConfiguration("Only").getJSONObject("globals")
            .getJSONArray("timetable").getJSONObject(at).getString("loc");
    }

    /**
     * A page whose NAME contains a colon keeps its own setup.
     *
     * OB-071. A key is "page:x,y", and `parseTileKey`'s own comment calls "Yard: Upper" an ordinary
     * thing to call a page - which is why it, `isOnPage` and `rekeyOne` all split on the LAST colon.
     * `toStored` and `fromStored` were splitting on the first, so every square on "Yard: Upper" was
     * stored under the id belonging to the page called "Yard".
     *
     * The consequence is the one this month keeps producing: rename "Yard" - a page you are not
     * touching - and "Yard: Upper" loses its entire setup, with nothing connecting the two.
     */
    @Test
    public void testAPageNameContainingAColonKeepsItsOwnSetup() throws IOException
    {
        java.util.Map<String, String> pages = new java.util.LinkedHashMap<>();

        pages.put("Yard", "1");
        pages.put("Yard: Upper", "2");

        store.setPageIds(pages);

        TileKey plain = new TileKey("Yard", 3, 3);
        TileKey colon = new TileKey("Yard: Upper", 3, 3);

        store.setPointName(plain, "Down Sidings");
        store.setStation(plain, true);

        store.setPointName(colon, "Upper Sidings");
        store.setStation(colon, true);
        store.setTileLength(colon, 21);

        store.createConfiguration("Only", null);
        store.setActiveConfiguration("Only");
        store.save();

        AutonomyCompanionStore reloaded = new AutonomyCompanionStore(layout);

        reloaded.setPageIds(pages);
        reloaded.load();

        assertEquals(reloaded.getPointName(colon), "Upper Sidings",
            "the colon in the page name was read as the end of the page, so this square was stored "
            + "under the OTHER page's id - and renaming that page, which nobody was touching, would "
            + "take this one's whole setup with it");

        assertEquals(reloaded.getTileLength(colon), 21, "the length went to the wrong page");
        assertTrue(reloaded.isStation(colon), "the station went to the wrong page");

        assertEquals(reloaded.getPointName(plain), "Down Sidings",
            "the page whose name has no colon lost its own setup");

        // and the two are genuinely distinct on disk
        String written = new String(java.nio.file.Files.readAllBytes(
            new File(layout, "config/autonomy/setup.json").toPath()), StandardCharsets.UTF_8);

        assertTrue(written.contains("\"2:3,3\""),
            "the square on \"Yard: Upper\" was not stored under that page's own id (2): " + written);
    }
}
