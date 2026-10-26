package regression;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import static org.testng.Assert.*;
import org.json.JSONObject;
import org.testng.annotations.Test;
import org.traincontrol.automationui.AutonomyCompanionStore;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts;

/**
 * Every setting the store holds, against every structural thing that can happen to a diagram.
 *
 * The store keeps eleven separate collections of settings, all keyed by square, and a diagram edit has to do the right
 * thing to every one of them.  Tests written one setting at a time left misses of one shape: a collection that some
 * operation forgot (`tileDirections` left behind by every move because their keys carry a suffix; `captions` dropped by
 * a move that landed on them).  So this is a matrix, settings down one side and operations along the other, every cell
 * asserted.
 *
 * The last test is the important one for the future: it reflects over the store's own fields and refuses to pass
 * unless every collection is in the matrix or on the list of things not keyed by square, so adding a setting without
 * deciding which fails the build.
 */
public class testAutonomyStoreSettingsMatrix
{
    private static final String PAGE = "main";
    private static final String OTHER_PAGE = "other";

    /** A page no setting in this matrix is written on or points at, so deleting it is a no-op */
    private static final String UNRELATED_PAGE = "spare";

    private static final TileKey ELSEWHERE = new TileKey(PAGE, 9, 9);
    private static final TileKey ON_ANOTHER_PAGE = new TileKey(OTHER_PAGE, 2, 2);

    /**
     * ASYMMETRIC on purpose: `RouteId(0, 0)` reads the same either way round, so a column carrying a direction across a
     * move, rename, delete or restore could not see the route half of the key (a `withSquare` that threw the route away
     * passed 209 tests).
     */
    private static final RouteId ROUTE = new RouteId(1, 3);

    private static final String CONFIGURATION = "Evening";

    /** One setting: how to put it on a square, and how to read it back off one. */
    private static final class Setting
    {
        final String name;

        /** The store's own field, so that the guard below can tell what is covered */
        final String field;

        final BiConsumer<AutonomyCompanionStore, TileKey> write;

        final BiFunction<AutonomyCompanionStore, TileKey, Object> read;

        Setting(String name, String field, BiConsumer<AutonomyCompanionStore, TileKey> write,
            BiFunction<AutonomyCompanionStore, TileKey, Object> read)
        {
            this.name = name;
            this.field = field;
            this.write = write;
            this.read = read;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    private static final List<Setting> SETTINGS = Arrays.asList(

        new Setting("the station's name", "pointNames",
            (store, at) -> store.setPointName(at, "Platform 3"),
            (store, at) -> store.getPointName(at)),

        new Setting("being a station at all", "stations",
            (store, at) -> store.setStation(at, true),
            (store, at) -> store.isStation(at)),

        new Setting("the length of the track", "tileLengths",
            (store, at) -> store.setTileLength(at, 42),
            (store, at) -> store.getTileLength(at)),

        // Keyed by the square AND a route across it, so its keys carry a suffix a mover matching whole keys leaves
        // behind
        new Setting("which way trains may run", "tileDirections",
            (store, at) -> store.setTileDirection(at, ROUTE, Direction.TOWARD_A),
            (store, at) -> store.getTileDirection(at, ROUTE)),

        new Setting("which sides trains may arrive from", "barredArrivals",
            (store, at) -> store.setBarredArrivals(at, sides(TilePorts.Side.N)),
            (store, at) -> store.getBarredArrivals(at)),

        // Key and value are both squares and the pairing is mutual: the far end must be rewritten whenever the near
        // moves
        new Setting("what a link is paired with", "portals",
            (store, at) -> store.pairPortals(at, ON_ANOTHER_PAGE),
            (store, at) -> store.getPortalPartner(at)),

        new Setting("the signal guarding a station", "stationSignals",
            (store, at) -> store.setProtectingSignal(at, ELSEWHERE),
            (store, at) -> store.getProtectingSignal(at)),

        // And the signals guarding the way into it (FR-096) - square to squares, the same shape
        new Setting("a signal guarding the way into a station", "entrySignals",
            (store, at) -> store.setEntrySignals(at, java.util.Arrays.asList(ELSEWHERE)),
            (store, at) -> store.getEntrySignals(at).isEmpty() ? null : store.getEntrySignals(at)),

        // A reference rather than a fact: this square carries the NAME of another one
        new Setting("a label naming a station", "captions",
            (store, at) -> store.setCaption(at, ELSEWHERE),
            (store, at) -> store.getCaptionTarget(at)),

        // Also a reference: this station is held back while ANOTHER square is occupied (FR-001)
        new Setting("a point that holds a station back", "blockedPoints",
            (store, at) -> store.setBlockingPoints(at, java.util.Arrays.asList(ELSEWHERE)),
            (store, at) -> store.getBlockingPoints(at)),

        new Setting("what a link is called", "linkNames",
            (store, at) -> store.setLinkName(at, "to the yard"),
            (store, at) -> store.getLinkName(at)),

        new Setting("a link switched off", "disabledPortals",
            (store, at) -> store.setPortalDisabled(at, true),
            (store, at) -> store.isPortalDisabled(at)),

        // The configurations key by square too: the facings, the placements, the homes, the termini
        new Setting("what a configuration says about the square", "configurations",
            (store, at) -> configurationPoints(store).put(at.toString(),
                new JSONObject().put("facing", "N")),
            (store, at) ->
            {
                JSONObject points = configurationPoints(store);

                return points.has(at.toString())
                    ? points.getJSONObject(at.toString()).optString("facing", null) : null;
            })
    );

    /**
     * Everything in the store NOT keyed by square, listed rather than inferred so the guard can tell a forgotten
     * setting from one that does not belong here.
     */
    private static final Set<String> NOT_KEYED_BY_SQUARE = new LinkedHashSet<>(Arrays.asList(
        "excludedPages",         // by page
        "keptDespiteRepeats",    // by page - the pages the operator turned back on
        "unknownSharedFields",   // whatever a newer TrainControl wrote, kept so it can be written back
        "pageNameToId",          // by page
        "pageIdToName",          // by page id
        "pageNamesWhenWritten",  // by page id
        "pageIdConflicts",       // by page
        // By FIELD name, outside every rule here: it holds the file's own JSON for pages that are not loaded and is
        // written back as it came, so acting on it would act on squares of a page nobody can see (OB-067).
        "heldForAbsentPages"
    ));

    // The matrix

    /** Every setting follows its square when the tile moves. */
    @Test
    public void testEverySettingFollowsAMovedTile()
    {
        for (Setting setting : SETTINGS)
        {
            AutonomyCompanionStore store = store();
            TileKey was = at(4, 4);
            TileKey now = at(6, 7);

            setting.write.accept(store, was);

            Object expected = setting.read.apply(store, was);

            assertNotEquals(expected, absent(setting), "the fixture writes nothing: " + setting);

            store.moveTiles(one(was, now));

            assertEquals(setting.read.apply(store, now), expected,
                setting + " did not travel with the tile - it is still on the square the track left");

            assertEquals(setting.read.apply(store, was), absent(setting),
                setting + " was copied rather than moved, so two squares now claim it");
        }
    }

    /**
     * Every setting is dropped when its square is built over by other track.  "Carry everything, always" would pass
     * the test above and leave a station's name, signals and length on a square holding somebody else's track;
     * reconcile only drops settings from squares that are EMPTY.
     */
    @Test
    public void testEverySettingIsDroppedWhenItsSquareIsBuiltOver()
    {
        for (Setting setting : SETTINGS)
        {
            AutonomyCompanionStore store = store();
            TileKey doomed = at(4, 4);

            setting.write.accept(store, doomed);

            // A tile carrying nothing arrives on top of it: the mover only cleared a square it had something to write
            // on
            store.moveTiles(one(at(1, 1), doomed));

            assertEquals(setting.read.apply(store, doomed), absent(setting),
                setting + " survived its square being built over, and now describes track that has "
                + "been replaced");
        }
    }

    /**
     * Every setting is forgotten when the page it is on is deleted.
     *
     * `deletePage` gathers the page's squares by naming every collection, so testStoreCollectionsAreHandledEverywhere's
     * textual guard governs it; but that guard only requires the NAME to appear, and a loop that mentions
     * `disabledPortals` and gathers nothing reads as covered.  The setting would then survive the page and, by page-id
     * reuse (testPageIdsAreDurable), go to whatever page arrives next.
     *
     * MUTATION: in `AutonomyCompanionStore.deletePage`, neuter one gathering loop while still naming its collection
     * (`for (TileKey key : new LinkedHashSet<>(disabledPortals)) if (isOnPage(key, page)) squares.add(key);` becomes
     * `... squares.size();`) and one row here fails.
     */
    @Test
    public void testEverySettingIsForgottenWhenItsPageIsDeleted()
    {
        for (Setting setting : SETTINGS)
        {
            AutonomyCompanionStore store = store();
            TileKey doomed = on(OTHER_PAGE, 4, 4);

            setting.write.accept(store, doomed);

            // Assert the variable, not the control: a writer that did nothing on a second page would read as absent.
            assertNotEquals(setting.read.apply(store, doomed), absent(setting),
                "the fixture wrote nothing onto the page it is about to delete: " + setting);

            store.deletePage(OTHER_PAGE);

            assertEquals(setting.read.apply(store, doomed), absent(setting),
                setting + " survived the deletion of the page it was on.  It now describes track that "
                + "does not exist, and the next page to take that name inherits it");
        }
    }

    /**
     * And deleting a page leaves every other page exactly as it was: "forget everything" would pass the column above.
     * The page deleted here holds nothing and is named by nothing, so the store must be untouched (the portal row pairs
     * its square with one on OTHER_PAGE, so deleting OTHER_PAGE is *meant* to break that pair).
     */
    @Test
    public void testDeletingAPageLeavesEverySettingOnEveryOtherPageAlone()
    {
        for (Setting setting : SETTINGS)
        {
            AutonomyCompanionStore store = store();
            TileKey kept = at(4, 4);

            setting.write.accept(store, kept);

            Object expected = setting.read.apply(store, kept);

            assertNotEquals(expected, absent(setting), "the fixture writes nothing: " + setting);

            store.deletePage(UNRELATED_PAGE);

            assertEquals(setting.read.apply(store, kept), expected,
                setting + " was forgotten because an unrelated page was deleted");
        }
    }

    /**
     * Every setting comes back when a page is put back the way it was, which is what "exit without saving" is made of.
     */
    @Test
    public void testEverySettingComesBackWhenThePageIsRestored()
    {
        for (Setting setting : SETTINGS)
        {
            AutonomyCompanionStore store = store();
            TileKey was = at(4, 4);

            setting.write.accept(store, was);

            Object expected = setting.read.apply(store, was);

            Map<String, Object> before = store.snapshotPage(PAGE);

            store.moveTiles(one(was, at(6, 7)));

            assertEquals(setting.read.apply(store, was), absent(setting),
                "the move did not happen, so the undo proves nothing: " + setting);

            store.restorePage(PAGE, before);

            assertEquals(setting.read.apply(store, was), expected,
                setting + " did not come back when the page was restored - which is an edit the user "
                + "was told had been undone");
        }
    }

    /**
     * Every setting survives the page being renamed: a rename rewrites every key on that page and the values that name
     * squares on it, the half that gets missed.
     */
    @Test
    public void testEverySettingSurvivesAPageRename()
    {
        for (Setting setting : SETTINGS)
        {
            AutonomyCompanionStore store = store();
            TileKey was = at(4, 4);

            setting.write.accept(store, was);

            Object expected = setting.read.apply(store, was);

            store.renamePage(PAGE, "main renamed");

            Object now = setting.read.apply(store, new TileKey("main renamed", 4, 4));

            assertEquals(String.valueOf(now), String.valueOf(renamed(expected)),
                setting + " did not survive the page being renamed");
        }
    }

    /**
     * Every setting survives being written to disk and read back, with page ids set.
     *
     * The store keys by page NAME in memory and by page ID on disk, and `setPageIds` tells it the mapping; without it
     * `toStored` and `fromStored` pass keys through, `withoutAbsentPages` returns early and the held path never runs,
     * so the round trip ran with the translation layer switched off.
     *
     * MUTATION: making `fromStored` return its argument unchanged fails this test.  Making `toStored` return its
     * argument (the written-raw defect itself) does NOT fail this or the renumber test: a file written with page NAMES
     * round-trips while names do not change.  Only a RENAME exposes it, which is what the third test below is for.
     */
    @Test
    public void testEverySettingSurvivesASaveAndLoad() throws IOException
    {
        for (Setting setting : SETTINGS)
        {
            File folder = Files.createTempDirectory("tc-matrix").toFile();

            try
            {
                AutonomyCompanionStore store = new AutonomyCompanionStore(folder);

                store.setPageIds(pageIds());

                store.createConfiguration(CONFIGURATION, null);
                store.setActiveConfiguration(CONFIGURATION);

                TileKey was = at(4, 4);

                setting.write.accept(store, was);

                Object expected = setting.read.apply(store, was);

                store.save();

                AutonomyCompanionStore reloaded = new AutonomyCompanionStore(folder);

                reloaded.setPageIds(pageIds());
                reloaded.load();

                assertEquals(setting.read.apply(reloaded, was), expected,
                    setting + " was not written to the file, or was not read back from it");
            }
            finally
            {
                delete(folder);
            }
        }
    }

    // The guard that keeps the matrix complete

    /**
     * Every collection the store holds is either in this matrix or declared not to belong in it.
     *
     * The point of the whole file: a matrix stays complete only if adding a setting forces the decision.  Reflection
     * rather than a hand-kept list, which has the same failure mode as the thing it checks.
     */
    @Test
    public void testEveryCollectionInTheStoreIsAccountedFor()
    {
        Set<String> covered = new LinkedHashSet<>();

        for (Setting setting : SETTINGS) covered.add(setting.field);

        List<String> unaccounted = new ArrayList<>();

        for (Field field : AutonomyCompanionStore.class.getDeclaredFields())
        {
            if (Modifier.isStatic(field.getModifiers())) continue;

            if (!Map.class.isAssignableFrom(field.getType())
                && !Set.class.isAssignableFrom(field.getType())) continue;

            if (covered.contains(field.getName())) continue;

            if (NOT_KEYED_BY_SQUARE.contains(field.getName())) continue;

            unaccounted.add(field.getName());
        }

        assertTrue(unaccounted.isEmpty(),
            "the store has collections this matrix says nothing about: " + unaccounted
            + ".  Either add each to SETTINGS - so that moving, building over, restoring, renaming "
            + "and saving are all checked against it - or add it to NOT_KEYED_BY_SQUARE with a note "
            + "saying what it is keyed by.  Every one of the settings bugs in this project so far has "
            + "been a collection that one operation did not know about.");
    }

    /**
     * And every setting in the matrix names a field that exists, so a renamed field fails here instead of guarding
     * nothing.
     */
    @Test
    public void testEverySettingNamesARealField()
    {
        for (Setting setting : SETTINGS)
        {
            boolean found = false;

            for (Field field : AutonomyCompanionStore.class.getDeclaredFields())
            {
                if (field.getName().equals(setting.field)) found = true;
            }

            assertTrue(found, setting + " says it lives in a field called " + setting.field
                + ", and the store has no such field - so the guard is not guarding it");
        }
    }

    /**
     * What this setting reads as on a square that never had one, so "gone" is asserted without each setting's own idea
     * of empty.
     */
    private static Object absent(Setting setting)
    {
        return setting.read.apply(store(), at(4, 4));
    }

    /** A rename moves the page part of every key AND of every value that names a square on that page. */
    private static Object renamed(Object expected)
    {
        // A LIST of squares as well as one (a station can be held back by more than one point, FR-001); otherwise a
        // repointed list was compared against the old page's.
        if (expected instanceof java.util.List)
        {
            java.util.List<Object> out = new java.util.ArrayList<>();

            for (Object one : (java.util.List<?>) expected) out.add(renamed(one));

            return out;
        }

        return expected instanceof TileKey && PAGE.equals(((TileKey) expected).getPage())
            ? new TileKey("main renamed", ((TileKey) expected).getX(), ((TileKey) expected).getY())
            : expected;
    }

    private static AutonomyCompanionStore store()
    {
        AutonomyCompanionStore store = new AutonomyCompanionStore(null);

        try
        {
            store.createConfiguration(CONFIGURATION, null);
        }
        catch (IOException cannotHappen)
        {
            // A store with no folder writes nothing, so this is unreachable - but it is checked
            throw new IllegalStateException(cannotHappen);
        }

        store.setActiveConfiguration(CONFIGURATION);

        return store;
    }

    private static JSONObject configurationPoints(AutonomyCompanionStore store)
    {
        JSONObject configuration = store.getConfiguration(CONFIGURATION);

        if (!configuration.has("points")) configuration.put("points", new JSONObject());

        return configuration.getJSONObject("points");
    }

    private static Map<TileKey, TileKey> one(TileKey from, TileKey to)
    {
        Map<TileKey, TileKey> move = new LinkedHashMap<>();

        move.put(from, to);

        return move;
    }

    private static Set<TilePorts.Side> sides(TilePorts.Side... sides)
    {
        return new LinkedHashSet<>(Arrays.asList(sides));
    }

    /**
     * Every setting survives its page being RENUMBERED while the file sits on disk.
     *
     * The save-and-load test above translates through the same numbering both ways, so a store that wrote names would
     * still pass it.  This one changes the numbers between the save and the load, which is what page ids exist for (the
     * MT-135 loss in miniature).
     *
     * MUTATION: making `fromStored` return its argument unchanged fails this test and NOT the save-and-load test above.
     */
    @Test
    public void testEverySettingSurvivesItsPageBeingRenumbered() throws IOException
    {
        for (Setting setting : SETTINGS)
        {
            File folder = Files.createTempDirectory("tc-matrix-renumber").toFile();

            try
            {
                AutonomyCompanionStore store = new AutonomyCompanionStore(folder);

                store.setPageIds(pageIds());

                store.createConfiguration(CONFIGURATION, null);
                store.setActiveConfiguration(CONFIGURATION);

                TileKey was = at(4, 4);

                setting.write.accept(store, was);

                Object expected = setting.read.apply(store, was);

                store.save();

                // The layout index is rewritten and every page comes back under a different number; the names are
                // unchanged.
                Map<String, String> renumbered = new LinkedHashMap<>();

                renumbered.put(PAGE, "7");
                renumbered.put(OTHER_PAGE, "8");
                renumbered.put(UNRELATED_PAGE, "9");

                AutonomyCompanionStore reloaded = new AutonomyCompanionStore(folder);

                reloaded.setPageIds(renumbered);
                reloaded.load();

                assertEquals(setting.read.apply(reloaded, was), expected,
                    setting + " did not survive its page being renumbered.  It is keyed by page ID on "
                    + "disk, so a setup read through the wrong number lands on whatever track holds "
                    + "that number today - which is the MT-135 loss");
            }
            finally
            {
                delete(folder);
            }
        }
    }

    /**
     * Every setting survives its page being RENAMED while the file sits on disk.
     *
     * A setup written with page names round-trips under renumbering, so the "written raw" defect needs a rename to
     * show.  The rename is driven as the application drives it - `renamePage` in memory, then a save, then a load under
     * the new name - because this matrix checks the real path.
     *
     * Skipping the in-memory `renamePage` and reloading under the new index passes for eleven of the twelve settings
     * and fails "what a configuration says about the square": the eleven live in setup.json, keyed by page ID, while
     * the configurations are keyed by page NAME on disk and never translated, so a rename by any path that does not
     * call `renamePage` orphans the configuration and nothing else.
     *
     * MUTATION: this test does NOT catch `toStored` returning its argument unchanged (the class then reports one
     * failure, `testEveryIdKeyedSettingSurvivesARenameNothingSaw`): renaming IN MEMORY first puts the new name in the
     * keys, so nothing needs translating.  What it does catch is a setting that does not survive a rename at all.
     */
    @Test
    public void testEverySettingSurvivesItsPageBeingRenamedOnDisk() throws IOException
    {
        for (Setting setting : SETTINGS)
        {
            File folder = Files.createTempDirectory("tc-matrix-rename").toFile();

            try
            {
                AutonomyCompanionStore store = new AutonomyCompanionStore(folder);

                store.setPageIds(pageIds());

                store.createConfiguration(CONFIGURATION, null);
                store.setActiveConfiguration(CONFIGURATION);

                TileKey was = at(4, 4);

                setting.write.accept(store, was);

                Object expected = setting.read.apply(store, was);

                // The rename, then the save, then the load - the order the menu item does it in.
                store.renamePage(PAGE, "main renamed");

                store.save();

                // Same id, different name: what the index reports after a rename.
                Map<String, String> afterRename = new LinkedHashMap<>();

                afterRename.put("main renamed", "3");
                afterRename.put(OTHER_PAGE, "4");
                afterRename.put(UNRELATED_PAGE, "5");

                AutonomyCompanionStore reloaded = new AutonomyCompanionStore(folder);

                reloaded.setPageIds(afterRename);
                reloaded.load();

                Object now = setting.read.apply(reloaded, new TileKey("main renamed", 4, 4));

                assertEquals(String.valueOf(now), String.valueOf(renamed(expected)),
                    setting + " did not survive its page being renamed on disk.  Its entries are "
                    + "keyed by page id in the file precisely so that a rename cannot orphan them - "
                    + "which is the loss MT-135 was");
            }
            finally
            {
                delete(folder);
            }
        }
    }

    /**
     * Every id-keyed setting survives a rename that happened while nothing was running.
     *
     * The column that catches a raw write: the other three rename IN MEMORY first, so by the save the names are already
     * the new ones.  This is a rename the application never saw (TrainControl closed, another machine, the Central
     * Station): setup.json holds keys under the OLD name, the index offers only the new one, and only the id can
     * reunite them.
     *
     * The configuration row is excluded by name: it is keyed by page NAME on disk and never translated, which is no
     * defect today (every path that renames a page rekeys the configurations first) but a dependency nothing stated.
     *
     * MUTATION: making `toStored` return its argument unchanged fails this test on the eleven id-keyed settings, and
     * none of the other three columns.
     */
    @Test
    public void testEveryIdKeyedSettingSurvivesARenameNothingSaw() throws IOException
    {
        int checked = 0;

        for (Setting setting : SETTINGS)
        {
            // See the javadoc: this one is name-keyed on disk by design.
            if ("configurations".equals(setting.field)) continue;

            checked++;

            File folder = Files.createTempDirectory("tc-matrix-offline").toFile();

            try
            {
                AutonomyCompanionStore store = new AutonomyCompanionStore(folder);

                store.setPageIds(pageIds());

                store.createConfiguration(CONFIGURATION, null);
                store.setActiveConfiguration(CONFIGURATION);

                TileKey was = at(4, 4);

                setting.write.accept(store, was);

                Object expected = setting.read.apply(store, was);

                store.save();

                // No renamePage: nothing was running, so only the index changed, giving id 3 a different name.
                Map<String, String> afterRename = new LinkedHashMap<>();

                afterRename.put("main renamed", "3");
                afterRename.put(OTHER_PAGE, "4");
                afterRename.put(UNRELATED_PAGE, "5");

                AutonomyCompanionStore reloaded = new AutonomyCompanionStore(folder);

                reloaded.setPageIds(afterRename);
                reloaded.load();

                Object now = setting.read.apply(reloaded, new TileKey("main renamed", 4, 4));

                assertEquals(String.valueOf(now), String.valueOf(renamed(expected)),
                    setting + " did not survive a rename the application never saw.  setup.json is "
                    + "keyed by page ID for exactly this: the name in the file is gone, and the id is "
                    + "the only thing that can find these entries again");
            }
            finally
            {
                delete(folder);
            }
        }

        assertEquals(checked, SETTINGS.size() - 1,
            "this test skipped more than the one setting it means to skip, so it is checking less "
            + "than it claims");
    }

    /**
     * The numbering this matrix saves and loads through: three pages numbered from something other than one, so an
     * identity translation is not mistaken for one that works.
     */
    private static Map<String, String> pageIds()
    {
        Map<String, String> ids = new LinkedHashMap<>();

        ids.put(PAGE, "3");
        ids.put(OTHER_PAGE, "4");
        ids.put(UNRELATED_PAGE, "5");

        return ids;
    }

    private static TileKey at(int x, int y)
    {
        return new TileKey(PAGE, x, y);
    }

    private static TileKey on(String page, int x, int y)
    {
        return new TileKey(page, x, y);
    }

    private static void delete(File file)
    {
        File[] kids = file.listFiles();

        if (kids != null) for (File kid : kids) delete(kid);

        file.delete();
    }
}
