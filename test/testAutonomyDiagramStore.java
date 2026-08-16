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
import org.traincontrol.base.AutonomyCompanionStore;
import org.traincontrol.base.TileGraph.Direction;
import org.traincontrol.base.TileGraph.RouteId;
import org.traincontrol.base.TileGraph.TileKey;

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
     * The last configuration cannot be deleted: a setup with no configuration is a state nothing in the
     * UI could act on.
     */
    @Test
    public void testTheLastConfigurationCannotBeDeleted() throws IOException
    {
        store.createConfiguration("Only", null);

        try
        {
            store.deleteConfiguration("Only");
            fail("deleting the last configuration should be refused");
        }
        catch (IOException expected)
        {
            assertTrue(expected.getMessage().contains("autosetup"));
        }

        store.createConfiguration("Second", "Only");
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
}
