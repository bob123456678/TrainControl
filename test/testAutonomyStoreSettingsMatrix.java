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
 * The store keeps eleven separate collections of settings, all keyed by square, and a diagram edit has
 * to do the right thing to every one of them.  The tests for that were written one setting at a time,
 * as each was added - and so was the code, which is why the misses have always been of the same shape:
 * a collection that some operation forgot about.  tileDirections were left behind by every move for a
 * release, because their keys carry a suffix and the mover matched whole keys.  disabledPortals had to
 * be added to the mover separately.  captions were dropped by a move that landed on them, which is how
 * a station nudged one square down lost its name.  In each case the other ten were handled correctly,
 * a test existed for the operation, and it used one of the ten.
 *
 * So this is a matrix rather than a list of cases: settings down one side, operations along the other,
 * and every cell asserted.  A cell nobody thought about is a cell that fails.
 *
 * The last test is the important one for the future.  It reflects over the store's own fields and
 * refuses to pass unless every collection in there is either in the matrix or on the list of things
 * that are not keyed by square.  Adding a twelfth setting without deciding which it is fails the build,
 * which is the only way a matrix stays complete once the person who wrote it has moved on.
 */
public class testAutonomyStoreSettingsMatrix
{
    private static final String PAGE = "main";
    private static final String OTHER_PAGE = "other";

    private static final TileKey ELSEWHERE = new TileKey(PAGE, 9, 9);
    private static final TileKey ON_ANOTHER_PAGE = new TileKey(OTHER_PAGE, 2, 2);

    private static final RouteId ROUTE = new RouteId(0, 0);

    private static final String CONFIGURATION = "Evening";

    /**
     * One setting: how to put it on a square, and how to read it back off one.
     */
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

        // Keyed by the square AND a route across it, so its keys carry a suffix - which is how they
        // came to be left behind by a mover that matched whole keys
        new Setting("which way trains may run", "tileDirections",
            (store, at) -> store.setTileDirection(at, ROUTE, Direction.TOWARD_A),
            (store, at) -> store.getTileDirection(at, ROUTE)),

        new Setting("which sides trains may arrive from", "barredArrivals",
            (store, at) -> store.setBarredArrivals(at, sides(TilePorts.Side.N)),
            (store, at) -> store.getBarredArrivals(at)),

        // Key and value are both squares, and the pairing is mutual: the far end has to be rewritten
        // whenever the near one moves, or the pair is broken from the page nobody is looking at
        new Setting("what a link is paired with", "portals",
            (store, at) -> store.pairPortals(at, ON_ANOTHER_PAGE),
            (store, at) -> store.getPortalPartner(at)),

        new Setting("the signal guarding a station", "stationSignals",
            (store, at) -> store.setProtectingSignal(at, ELSEWHERE),
            (store, at) -> store.getProtectingSignal(at)),

        // A reference rather than a fact: this square carries the NAME of another one
        new Setting("a label naming a station", "captions",
            (store, at) -> store.setCaption(at, ELSEWHERE),
            (store, at) -> store.getCaptionTarget(at)),

        new Setting("what a link is called", "linkNames",
            (store, at) -> store.setLinkName(at, "to the yard"),
            (store, at) -> store.getLinkName(at)),

        new Setting("a link switched off", "disabledPortals",
            (store, at) -> store.setPortalDisabled(at, true),
            (store, at) -> store.isPortalDisabled(at)),

        // The configurations key by square too - the facings, the placements, the homes, the termini -
        // which renamePage found out about the hard way
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
     * Everything in the store that is NOT keyed by square, and so has no cell in this matrix.
     *
     * Listed rather than inferred, so that the guard below can tell a setting somebody forgot to cover
     * from one that genuinely does not belong here.
     */
    private static final Set<String> NOT_KEYED_BY_SQUARE = new LinkedHashSet<>(Arrays.asList(
        "excludedPages",         // by page
        "unknownSharedFields",   // whatever a newer TrainControl wrote, kept so it can be written back
        "pageNameToId",          // by page
        "pageIdToName",          // by page id
        "pageNamesWhenWritten",  // by page id
        "pageIdConflicts"        // by page
    ));

    // =============================================================================================
    // The matrix
    // =============================================================================================

    /**
     * Every setting follows its square when the tile moves.
     */
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
     * Every setting is dropped when its square is built over by other track.
     *
     * The opposite direction, and it needs stating: "carry everything, always" passes the test above
     * and leaves a station's name, its signals and its length attached to a square that now holds
     * somebody else's track.  Nothing else finds those - reconcile only drops settings from squares
     * that are EMPTY, and one of these is occupied.
     */
    @Test
    public void testEverySettingIsDroppedWhenItsSquareIsBuiltOver()
    {
        for (Setting setting : SETTINGS)
        {
            AutonomyCompanionStore store = store();
            TileKey doomed = at(4, 4);

            setting.write.accept(store, doomed);

            // A tile carrying nothing at all arrives on top of it, which is the case that used to
            // leave the settings behind: the mover only cleared a square it had something to write on
            store.moveTiles(one(at(1, 1), doomed));

            assertEquals(setting.read.apply(store, doomed), absent(setting),
                setting + " survived its square being built over, and now describes track that has "
                + "been replaced");
        }
    }

    /**
     * Every setting comes back when a page is put back the way it was.
     *
     * What "exit without saving" is made of.  A setting the snapshot does not take is a setting the
     * restore cannot bring back, and the user is told their edits were undone.
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
     * Every setting survives the page being renamed.
     *
     * Settings are keyed by page and square, so a rename rewrites every key on that page - and the
     * values that name squares on it, which is the half that gets missed.
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
     * Every setting survives being written to disk and read back.
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

                store.createConfiguration(CONFIGURATION, null);
                store.setActiveConfiguration(CONFIGURATION);

                TileKey was = at(4, 4);

                setting.write.accept(store, was);

                Object expected = setting.read.apply(store, was);

                store.save();

                AutonomyCompanionStore reloaded = new AutonomyCompanionStore(folder);
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

    // =============================================================================================
    // The guard that keeps the matrix complete
    // =============================================================================================

    /**
     * Every collection the store holds is either in this matrix or declared not to belong in it.
     *
     * The point of the whole file.  A matrix is only worth writing if it cannot quietly stop being
     * complete, and the way it stops being complete is that somebody adds a twelfth setting and tests
     * it the way the first eleven were tested - on its own, against the operation they had in mind.
     *
     * Reflection rather than a hand-kept list, because a hand-kept list has exactly the same failure
     * mode as the thing it is checking.
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
     * And every setting in the matrix names a field that actually exists.
     *
     * So that a renamed field turns into a failure here rather than into a guard that silently checks
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

    // =============================================================================================

    /**
     * What this setting reads as on a square that has never had one, so that "gone" can be asserted
     * without every setting needing its own idea of empty - null, zero, false, or an empty set.
     */
    private static Object absent(Setting setting)
    {
        return setting.read.apply(store(), at(4, 4));
    }

    /**
     * A rename moves the page part of every key AND of every value that names a square on that page.
     */
    private static Object renamed(Object expected)
    {
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

    private static TileKey at(int x, int y)
    {
        return new TileKey(PAGE, x, y);
    }

    private static void delete(File file)
    {
        File[] kids = file.listFiles();

        if (kids != null) for (File kid : kids) delete(kid);

        file.delete();
    }
}
