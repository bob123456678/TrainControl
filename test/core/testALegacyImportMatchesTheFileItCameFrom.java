package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * A 2.7.4c import brings the settings across, and nothing else (MT-296).
 *
 * Adam, 2026-09-12: *"too much manual effort, make a test case for this and validate that state matches
 * by having two fixtures in the test."*
 *
 * **The two fixtures are his own.** `test/layouts/live-snapshot` is the frozen copy of his railway, and
 * `config/autonomy_legacy/autonomy.json` inside it is the frozen copy of the 2.7.4c file the upgrade path
 * exists for. The test reads the file, imports it into a fresh configuration, and compares the result
 * against the file itself rather than against a list of expected values written down here - so a
 * settings key added to either format cannot quietly stop being carried.
 *
 * **The four claims are the entry's own Expected.** The settings come across; the three that must not
 * come across do not; a station's **capacity** arrives as a capacity; and the square's **track length**
 * is untouched. The last two are one confusion with two halves - `importLegacy` used to write
 * `points[].maxTrainLength` through `setTileLength`, so every upgrading station lost its limit and six
 * squares gained lengths nobody had measured, and lengths are what the shortest-track and longest-track
 * routing rules are computed from.
 *
 * MUTATION, all four run on 2026-09-12: guarding the settings copy out of `importLegacy` fails the
 * first ("What arrived: []" against the file's ten); removing the `activateRoutes` exclusion fails the
 * second; taking `maxTrainLength` out of `CARRIED_SETTINGS` fails the third; and putting the old
 * `setTileLength(tile, maxTrainLength)` line back fails the fourth, with five squares gaining a length.
 *
 * **On this fixture the file arrives as `skipped`, not `matched`**, because the companion store's square
 * names outlive a configuration - so a "fresh" configuration is fresh in its settings and not in its
 * names. The settings, the capacities and the lengths are all carried on the path taken either way,
 * which is why the precondition below counts both.
 *
 * @author Adam
 */
public class testALegacyImportMatchesTheFileItCameFrom
{
    private static support.LayoutSandbox sandbox;
    private static MarklinControlStation model;
    private static AutonomySession session;

    /** The 2.7.4c file, as read off disk */
    private static org.json.JSONObject legacy;

    /** What the import wrote above the points */
    private static org.json.JSONObject globals;

    /** Which squares carried a track length before the import, and what each said */
    private static Map<TileKey, Integer> lengthsBefore;

    /**
     * The keys an import must NOT carry, each excluded for its own reason in `importLegacy`.
     *
     * The two route-activation keys do not stay inside the configuration: `parseAuto` ends in
     * `applyAutonomyRouteActivations`, which disables every route in the live Central Station database
     * whose id is not listed - and this very file says `activateRoutes: true` with an empty list. The
     * timetable names points by name and all but a couple of its legs would be dropped one warning at a
     * time, then written back permanently by the next capture.
     */
    private static final Set<String> MUST_NOT_TRAVEL = new LinkedHashSet<>(
        Arrays.asList("activateRoutes", "activateRouteIDs", "timetable"));

    /** The setup itself, which belongs in the configuration and not in its settings */
    private static final Set<String> NOT_SETTINGS = new LinkedHashSet<>(
        Arrays.asList("points", "edges"));

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));

        java.io.File file = new java.io.File(new java.io.File(sandbox.getFolder(),
            "config" + java.io.File.separator + "autonomy_legacy"), "autonomy.json");

        if (!file.exists())
        {
            throw new SkipException("this fixture carries no legacy autonomy.json at " + file);
        }

        legacy = new org.json.JSONObject(new String(
            java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));

        model = MarklinControlStation.init(null, true, false, false, false);

        session = new AutonomySession(sandbox.getFolder());

        session.open(support.LayoutSandbox.wiredPages(model));

        // A FRESH CONFIGURATION, which is what the entry's own steps say - "into a fresh
        // configuration". The import gap-fills, so importing over the setup this snapshot already
        // carries would skip every square that is already named and test nothing.
        session.getStore().createConfiguration("Imported", null);
        session.getStore().setActiveConfiguration("Imported");

        // The reduction is derived per configuration, and the import matches legacy points to squares
        // through it - so without this it looks at the one the session opened with.
        session.rebuild();

        lengthsBefore = new LinkedHashMap<>();

        for (TileKey measured : session.tilesWithALength())
        {
            lengthsBefore.put(measured, session.getStore().getTileLength(measured));
        }

        AutonomySession.LegacyImport result = session.importLegacy(legacy);

        // MATCHED OR SKIPPED, because a square that already has a name is still a square the file
        // reached - and the companion store's names outlive a configuration, so on this fixture the
        // whole file arrives as `skipped`. What would mean the file and the pages have stopped being
        // about one railway is neither: nothing landing anywhere.
        if (result.matched + result.skipped == 0)
        {
            throw new SkipException("the import reached no square on this diagram, so there is nothing"
                + " here to compare - " + result.unmatched.size() + " points landed nowhere");
        }

        session.rebuild();

        globals = session.getStore().getConfiguration("Imported").optJSONObject("globals");
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (sandbox != null) sandbox.close();
    }

    /**
     * The claim: every setting in the file that is allowed to travel arrives with the same value.
     *
     * Swept from the FILE rather than compared against a list written here, so this cannot fall behind
     * a format that gains a key.
     *
     * @throws Exception from the store
     */
    @Test
    public void testEverySettingArrivesWithItsOwnValue() throws Exception
    {
        assertNotNull(globals,
            "the file's settings did not arrive at all, so an upgrading user keeps their station names"
            + " and loses every rule about how their railway runs");

        List<String> expected = new ArrayList<>();

        for (String key : legacy.keySet())
        {
            if (NOT_SETTINGS.contains(key) || MUST_NOT_TRAVEL.contains(key)) continue;

            expected.add(key);
        }

        assertTrue(expected.size() >= 5,
            "this legacy file carries only " + expected.size() + " settings above its points, so the"
            + " claim below is about almost nothing: " + expected);

        for (String key : expected)
        {
            assertTrue(globals.has(key),
                "the setting " + key + " did not come across. What arrived: " + globals.keySet()
                + "; what the file holds: " + expected);

            assertEquals(String.valueOf(globals.get(key)), String.valueOf(legacy.get(key)),
                "the setting " + key + " arrived as " + globals.get(key) + " where the file says "
                + legacy.get(key));
        }
    }

    /**
     * And the three that must not travel did not.
     *
     * The route pair is the important one: carrying it disables every route the file does not list, on
     * import and again on every diagram edit, and this file lists none.
     *
     * @throws Exception from the store
     */
    @Test
    public void testTheThreeThatWouldDoHarmStayBehind() throws Exception
    {
        assertNotNull(globals, "no settings arrived at all");

        for (String key : MUST_NOT_TRAVEL)
        {
            // Only meaningful for a key the file actually carries - otherwise the assertion is about
            // an absence nothing could have produced.
            if (!legacy.has(key)) continue;

            assertFalse(globals.has(key),
                "the import carried " + key + ", which this file holds and which must stay behind."
                + " activateRoutes/activateRouteIDs reach the live Central Station route database"
                + " through applyAutonomyRouteActivations - and this file says activateRoutes with an"
                + " EMPTY list, so carrying it switches off every route Adam has");
        }

        assertTrue(legacy.has("activateRoutes"),
            "this fixture no longer carries activateRoutes, so the claim above is about nothing - the"
            + " exclusion that matters most would pass with the code removed");
    }

    /**
     * A station's capacity arrives as a capacity.
     *
     * @throws Exception from the store
     */
    @Test
    public void testAStationsCapacityArrivesAsACapacity() throws Exception
    {
        Map<String, Integer> wanted = new LinkedHashMap<>();

        org.json.JSONArray points = legacy.optJSONArray("points");

        for (int i = 0; points != null && i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point == null || !point.has("maxTrainLength") || !point.has("name")) continue;

            if (point.optInt("maxTrainLength", 0) <= 0) continue;

            wanted.put(point.getString("name"), point.getInt("maxTrainLength"));
        }

        if (wanted.isEmpty())
        {
            throw new SkipException("this legacy file gives no station a maximum train length");
        }

        int checked = 0;

        for (Map.Entry<String, Integer> station : wanted.entrySet())
        {
            TileKey square = squareNamed(station.getKey());

            // A legacy point whose sensor is not on this diagram lands nowhere, which the import
            // counts and reports; it is not this test's subject.
            if (square == null) continue;

            checked++;

            Object carried = session.getPointProperty(square, "maxTrainLength");

            // As a NUMBER, whatever the store hands back it as: an Integer and a JSON number are the
            // same capacity, and a test that failed on the box would be about the store's typing.
            assertTrue(carried instanceof Number
                    && ((Number) carried).intValue() == station.getValue(),
                station.getKey() + " came in with a maximum train length of " + carried
                + " where the file says " + station.getValue()
                + ". It used to be written through setTileLength, so every upgrading station lost its"
                + " limit and squares gained lengths nobody had measured");
        }

        assertTrue(checked > 0,
            "not one of the " + wanted.size() + " stations the file gives a capacity to reached a"
            + " square, so this claim checked nothing: " + wanted.keySet());
    }

    /**
     * And the square's own track length is untouched, which is the other half of the same confusion.
     *
     * Lengths are what the shortest-track and longest-track routing rules are computed from, so a
     * capacity written into one is not a harmless mislabelling.
     *
     * @throws Exception from the store
     */
    @Test
    public void testNoSquareGainedATrackLength() throws Exception
    {
        Map<TileKey, Integer> after = new LinkedHashMap<>();

        for (TileKey measured : session.tilesWithALength())
        {
            after.put(measured, session.getStore().getTileLength(measured));
        }

        assertEquals(after, lengthsBefore,
            "the import changed which squares carry a track length. Before: " + lengthsBefore
            + "; after: " + after + ". A station's capacity is not a track's length, and writing one"
            + " as the other is what MT-296 is about");
    }

    /**
     * The square the import gave this legacy name to, or null if it landed nowhere.
     */
    private static TileKey squareNamed(String name)
    {
        if (session.getStationIndex() == null) return null;

        for (TileKey square : session.getStationIndex().squares())
        {
            if (name.equals(session.getStationIndex().nameOf(square))) return square;
        }

        return null;
    }
}
