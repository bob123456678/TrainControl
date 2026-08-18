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

        // and nothing is left under the old name
        assertNull(store.getPointName(before));
        assertFalse(store.getExcludedPages().contains("Old Name"));
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

        // and now the other way round: the sensor stays, the square the text was on is deleted
        store.reconcile(new LinkedHashSet<>(java.util.Arrays.asList(station)));

        assertNull(store.getCaptionTarget(caption));
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
}
