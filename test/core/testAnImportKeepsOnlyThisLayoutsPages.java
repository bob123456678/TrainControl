package core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * Importing an export brings in only the pages this layout has, and says which it left out.
 *
 * Adam, MT-380, 2026-09-13, on importing into a one-page layout: *"the 'the setup was left alone' popup
 * shows twice (once at first import, once again when import is completed).  Then, it shows up after
 * every time the autonomy editor is opened."*  His ruling: **"keep only the pages, and alert the user."**
 *
 * **What happened.**  `importBundle` took the exporter's whole page record, so an export of his
 * five-page railway gave `random_test_layout` five page names it will never have.  The store keeps the
 * names of absent pages deliberately - a OneDrive page still downloading must not be pruned - so every
 * save found five pages it could not load and refused to tidy.  The settings the export carried for
 * those pages were held for them too, and written back on every save.
 *
 * **What this pins.**  After the import, `pagesNotLoaded` - the question every save asks - names none of
 * the exporter's extra pages; their settings are not in the file a save writes; the settings for the page
 * this layout does have came across; and the pages left out are reported.  And the control that keeps the
 * protection honest: a page this layout ALREADY knew and cannot load right now is still reported missing,
 * because an import has no business deciding a OneDrive page is gone.
 *
 * Built on the store alone, in temporary folders - no window, no railway, no Preferences.
 *
 * @author Adam
 */
public class testAnImportKeepsOnlyThisLayoutsPages
{
    private File theirs;
    private File mine;

    @BeforeMethod
    public void setUp() throws IOException
    {
        theirs = Files.createTempDirectory("tc-import-theirs").toFile();
        mine = Files.createTempDirectory("tc-import-mine").toFile();
    }

    @AfterMethod
    public void tearDown()
    {
        delete(theirs);
        delete(mine);
    }

    /**
     * A page the exporter had and this layout does not is left out: not recorded, not held, reported.
     *
     * @throws IOException from the temporary folders
     */
    @Test
    public void testThePagesThisLayoutDoesNotHaveAreLeftOut() throws IOException
    {
        org.json.JSONObject bundle = anExportOfTwoPages();

        AutonomyCompanionStore store = new AutonomyCompanionStore(mine);

        store.setPageIds(pages("Page 1", "0"));

        store.setPointName(new TileKey("Page 1", 5, 5), "Already Mine");

        store.importBundle("Theirs", bundle);

        // THE QUESTION EVERY SAVE ASKS, and the one that produced the warning on every save.
        List<String> missing = store.pagesNotLoaded(Collections.singletonList("Page 1"));

        assertFalse(missing.contains("2 - Bottom"),
            "after importing an export of a two-page railway into a one-page layout, the store reports"
            + " the exporter's other page as one it cannot load: " + missing + ". Every save then"
            + " refuses to tidy and says \"the setup was left alone\" - Adam, MT-380: \"it shows up after"
            + " every time the autonomy editor is opened\". His ruling: \"keep only the pages\".");

        // AND THE PAGE THIS LAYOUT HAS CAME ACROSS, or the claim above would pass on an import that
        // brought in nothing at all.
        assertEquals(store.getPointName(new TileKey("Page 1", 1, 1)), "Came Across",
            "the setting for the page this layout does have did not come across, so nothing above is"
            + " about an import that did anything");

        assertEquals(store.getPointName(new TileKey("Page 1", 5, 5)), "Already Mine",
            "the import overwrote a name this layout already had");

        // AND IT SAID WHICH (the half of the ruling that is not the dropping).
        assertEquals(store.getPagesLeftOutOfLastImport(), Collections.singletonList("2 - Bottom"),
            "the import did not report the page it left out, so the operator is not told the file"
            + " described a page this layout does not have - Adam: \"and alert the user\"");

        // AND WHAT A SAVE WRITES carries neither the page nor the settings held for it.
        store.save();

        String written = everySetupFileUnder(mine);

        assertFalse(written.contains("2 - Bottom"),
            "the saved setup still names the exporter's other page, so the next time this layout is"
            + " opened it is a page the setup knows and cannot load, and the warning is back");

        assertFalse(written.contains("Somewhere Else"),
            "the saved setup still holds the setting the export carried for a page this layout does not"
            + " have, written back verbatim for a page that will never come");
    }

    /**
     * A page this layout already knew and cannot load right now is NOT dropped by an import.
     *
     * The control, and the protection the store exists to give: a OneDrive page that has not downloaded
     * yet is absent in exactly the way a foreign page is, and only the person who knows may say it is
     * gone. An import that pruned it would be a new way to lose a page's settings.
     *
     * @throws IOException from the temporary folders
     */
    @Test
    public void testAPageThisLayoutAlreadyKnewIsStillProtected() throws IOException
    {
        // This layout, as it was written with two pages of its own ...
        AutonomyCompanionStore before = new AutonomyCompanionStore(mine);

        Map<String, String> both = pages("Page 1", "0");

        both.put("Away", "7");

        before.setPageIds(both);
        before.setPointName(new TileKey("Page 1", 5, 5), "Already Mine");
        before.setPointName(new TileKey("Away", 3, 3), "Waiting To Download");
        before.save();

        // ... and opened again with "Away" not loaded, which is what an unhydrated page looks like.
        AutonomyCompanionStore store = new AutonomyCompanionStore(mine);

        store.setPageIds(pages("Page 1", "0"));
        store.load();

        assertTrue(store.pagesNotLoaded(Collections.singletonList("Page 1")).contains("Away"),
            "precondition: the layout's own unloaded page is not recorded as missing before the import,"
            + " so there is no protection here for the import to take away");

        store.importBundle("Theirs", anExportOfTwoPages());

        List<String> missing = store.pagesNotLoaded(Collections.singletonList("Page 1"));

        assertTrue(missing.contains("Away"),
            "an import dropped a page this layout already had and simply could not load - " + missing
            + ". That is a OneDrive page still downloading, and deciding it is gone is the operator's"
            + " call, never an import's.");

        assertFalse(missing.contains("2 - Bottom"),
            "and the exporter's foreign page was kept, so the filter did nothing here");

        assertFalse(store.getPagesLeftOutOfLastImport().contains("Away"),
            "the layout's own unloaded page was reported as left out of the import");
    }

    /**
     * A foreign page that happens to carry the same id as one of this layout's pages is left out, and
     * none of its settings land on the local page (TDR-B4).
     *
     * Page ids are numbered from the same start on every railway, so an export of a five-page layout into
     * a one-page one almost always has an id in common with it.  The page record merges by id and a key
     * is resolved by id when its recorded name is unknown here - so the exporter's "Yard", id 1, was read
     * straight onto this layout's "Main", id 1, while the import dialog said Yard's settings "were left
     * out, because there is nothing here for them to belong to".
     *
     * @throws IOException from the temporary folders
     */
    @Test
    public void testAForeignPageWithAnIdInCommonIsLeftOutAndNotMergedOntoThisOne() throws IOException
    {
        AutonomyCompanionStore source = new AutonomyCompanionStore(theirs);

        Map<String, String> theirPages = pages("Yard", "1");

        theirPages.put("Main", "2");

        source.setPageIds(theirPages);
        source.setPointName(new TileKey("Yard", 5, 5), "Yard Platform");
        source.setPointName(new TileKey("Main", 1, 1), "Came Across");
        source.createConfiguration("Theirs", null);

        org.json.JSONObject bundle = source.exportBundle("Theirs");

        assertNotNull(bundle, "the export produced nothing");

        AutonomyCompanionStore store = new AutonomyCompanionStore(mine);

        store.setPageIds(pages("Main", "1"));

        store.importBundle("Theirs", bundle);

        assertEquals(store.getPointName(new TileKey("Main", 1, 1)), "Came Across",
            "precondition: the setting for the page both layouts have did not come across, by name, so"
            + " nothing below is about an import that did anything");

        assertEquals(store.getPointName(new TileKey("Main", 5, 5)), null,
            "the exporter's Yard shares id 1 with this layout's Main, and Yard's station name was read onto"
            + " Main's square 5,5 - a page of somebody else's names on the wrong track (TDR-B4)");

        assertEquals(store.getPagesLeftOutOfLastImport(), Collections.singletonList("Yard"),
            "the page this layout does not have was not reported as left out");
    }

    /**
     * The control: the exporter's copy of a page this layout has lands on it whatever id either side
     * gave it.
     *
     * @throws IOException from the temporary folders
     */
    @Test
    public void testAPageThisLayoutHasLandsOnItUnderAnyId() throws IOException
    {
        AutonomyCompanionStore source = new AutonomyCompanionStore(theirs);

        source.setPageIds(pages("Main", "7"));
        source.setPointName(new TileKey("Main", 4, 4), "Renumbered Elsewhere");
        source.createConfiguration("Theirs", null);

        AutonomyCompanionStore store = new AutonomyCompanionStore(mine);

        store.setPageIds(pages("Main", "1"));

        store.importBundle("Theirs", source.exportBundle("Theirs"));

        assertEquals(store.getPointName(new TileKey("Main", 4, 4)), "Renumbered Elsewhere",
            "the exporter's Main, id 7, did not land on this layout's Main, id 1 - a page is matched by its"
            + " name, and an id in the file is only how that file spelled it");

        assertTrue(store.getPagesLeftOutOfLastImport().isEmpty(),
            "a page this layout has was reported as left out: " + store.getPagesLeftOutOfLastImport());
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    /**
     * An export from a railway with two pages, carrying a setting on each.
     *
     * @return the parsed bundle
     * @throws IOException from the temporary folder
     */
    private org.json.JSONObject anExportOfTwoPages() throws IOException
    {
        AutonomyCompanionStore source = new AutonomyCompanionStore(theirs);

        Map<String, String> two = pages("Page 1", "0");

        two.put("2 - Bottom", "1");

        source.setPageIds(two);
        source.setPointName(new TileKey("Page 1", 1, 1), "Came Across");
        source.setPointName(new TileKey("2 - Bottom", 2, 2), "Somewhere Else");

        // An export is of a named configuration, and carries the shared settings alongside it.
        source.createConfiguration("Theirs", null);

        org.json.JSONObject bundle = source.exportBundle("Theirs");

        assertNotNull(bundle, "the export produced nothing");

        return bundle;
    }

    private static Map<String, String> pages(String name, String id)
    {
        Map<String, String> out = new LinkedHashMap<>();

        out.put(name, id);

        return out;
    }

    /** Every setup file a save wrote under a folder, as one string, so a leftover shows wherever it is. */
    private static String everySetupFileUnder(File folder) throws IOException
    {
        StringBuilder all = new StringBuilder();

        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(folder.toPath()))
        {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) walk::iterator)
            {
                if (path.toString().endsWith(".json"))
                {
                    all.append(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).append('\n');
                }
            }
        }

        assertTrue(all.length() > 0, "precondition: the save wrote no setup file under " + folder);

        return all.toString();
    }

    private static void delete(File file)
    {
        if (file == null || !file.exists()) return;

        File[] children = file.listFiles();

        if (children != null)
        {
            for (File child : children) delete(child);
        }

        file.delete();
    }
}
