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

    /** A page no setting in this matrix is written on or points at, so deleting it is a no-op */
    private static final String UNRELATED_PAGE = "spare";

    private static final TileKey ELSEWHERE = new TileKey(PAGE, 9, 9);
    private static final TileKey ON_ANOTHER_PAGE = new TileKey(OTHER_PAGE, 2, 2);

    /**
     * ASYMMETRIC, and that is the whole of why this constant has a comment.
     *
     * It was `RouteId(0, 0)`, which reads the same either way round - so every column of this matrix
     * that carries a direction across a move, a rename, a delete or a restore could not see the route
     * half of the key at all. A review proved it: making `DirectionKey.withSquare` throw the route
     * away and substitute route 0,0 - which would silently collapse every direction on a switch onto
     * one route, last write wins, on any page rename - passed 209 tests across seven classes.
     *
     * Every fixture in the repository used 0,0. The same discovery had already been made once for the
     * READ path, when swapping the two numbers in `readDirectionMap` passed everything; it was not
     * carried to the write path.
     */
    private static final RouteId ROUTE = new RouteId(1, 3);

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
        "pageIdConflicts",       // by page
        // By FIELD name, and deliberately outside every rule in this matrix: it holds the file's own
        // JSON for pages that are not loaded, and is written back exactly as it came in.  Moving,
        // building over, renaming or restoring it would be acting on squares of a page nobody can
        // see - which is the loss it exists to prevent (OB-067).
        "heldForAbsentPages"
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
     * Every setting is forgotten when the page it is on is deleted.
     *
     * The column this matrix was missing, and the one with the worst consequence.  `deletePage`
     * gathers the page's squares by naming all twelve collections one at a time - deliberately, so
     * that testStoreCollectionsAreHandledEverywhere's textual guard governs it - and hands them to
     * `forgetSquares`.  That guard only requires the collection's NAME to appear in the method.  A
     * gathering loop that mentions `disabledPortals` and gathers nothing from it reads as covered and
     * is not: the setting survives the page, keyed to track that no longer exists, and page-id reuse
     * (testPageIdsAreDurable) then hands it to whatever page arrives next.
     *
     * Mutation this must fail: in `AutonomyCompanionStore.deletePage`, neuter one gathering loop while
     * still naming its collection - `for (TileKey key : new LinkedHashSet<>(disabledPortals)) if
     * (isOnPage(key, page)) squares.add(key);` becomes `... squares.size();`.  Before this test that
     * mutant passed 85 tests across all four store guard classes.  It now fails one row here.
     *
     * The id-reuse half of the same loss is pinned separately and does not need repeating twelve
     * times - see testPageIdsAreDurable.testAPageReusingARetiredIdInheritsNothing.  In this store the
     * two are the same assertion anyway: keys carry the page NAME, so a page created again under the
     * deleted page's name reads exactly the squares asserted absent below.
     */
    @Test
    public void testEverySettingIsForgottenWhenItsPageIsDeleted()
    {
        for (Setting setting : SETTINGS)
        {
            AutonomyCompanionStore store = store();
            TileKey doomed = on(OTHER_PAGE, 4, 4);

            setting.write.accept(store, doomed);

            // Assert the variable, not the control: without this a setting whose writer silently did
            // nothing on a second page would read as absent afterwards and pass having tested nothing
            assertNotEquals(setting.read.apply(store, doomed), absent(setting),
                "the fixture wrote nothing onto the page it is about to delete: " + setting);

            store.deletePage(OTHER_PAGE);

            assertEquals(setting.read.apply(store, doomed), absent(setting),
                setting + " survived the deletion of the page it was on.  It now describes track that "
                + "does not exist, and the next page to take that name inherits it");
        }
    }

    /**
     * And deleting a page leaves every other page exactly as it was.
     *
     * The mirror, for the same reason the built-over test needs the moved test: "forget everything"
     * passes the column above and empties the operator's whole setup because one page went.  The page
     * deleted here holds nothing and is named by nothing, so the correct answer is that this store is
     * untouched - which is the weakest form of the property and the only one that is free of the
     * fixture's own cross-page values (the portal row pairs its square with a square on OTHER_PAGE, so
     * deleting OTHER_PAGE is *meant* to break that pair).
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
     *
     * **With page ids set, which they had not been until DR-B8.** The store keys by page NAME in
     * memory and by page ID on disk, and `setPageIds` is what tells it the mapping. Without that call
     * `toStored` and `fromStored` pass keys through unchanged, `withoutAbsentPages` returns early on
     * an empty index, and the held path never runs - so this test, whose whole charter is "every
     * setting against every structural thing", round-tripped all twelve rows with the translation
     * layer switched off.
     *
     * What that cost: a thirteenth collection whose author forgot the translate call passed all sixty
     * cells of this matrix. The store has already had that defect once - `excludedPages` was written
     * raw, and the comment where it was fixed says it "broke the rule setPageIds states".
     *
     * MUTATION: making `fromStored` return its argument unchanged fails this test. Before ids were
     * set it did not, because with no ids to translate through, returning the argument unchanged is
     * what the method already did.
     *
     * Recorded because it was measured rather than assumed: making `toStored` return its argument -
     * the written-raw defect itself - does NOT fail this, and does not fail the renumber test either.
     * A file written with page NAMES in it round-trips perfectly well as long as the names do not
     * change. What it cannot survive is a RENAME, which is the whole reason the file is keyed by id,
     * and that is what the third test below is for.
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
        // A LIST of squares as well as one, because a setting may name several - a station can be held
        // back by more than one point (FR-001). Without this the list came back correctly repointed at
        // the renamed page and was compared against the old one, which reads as the store having failed
        // when it was this helper that could not follow.
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
     * DR-B8's real subject. The test above now exercises the translation layer, but it translates
     * through the same numbering both ways, so a store that simply wrote names would still pass it.
     * This one changes the numbers between the save and the load, which is the thing page ids exist
     * for: the layout index is rewritten - a page added, deleted or combined - and the number a page
     * answers to is not the one it had when the setup was written.
     *
     * That is the MT-135 loss in miniature, and it cost Adam 19 point names, 14 stations, 22
     * directions and 15 captions to a single rename on 23 August. It was found one setting at a time,
     * which is the history this matrix's own javadoc warns about, and `testPageIdsAreDurable` still
     * covers this ground for `pointNames` only.
     *
     * MUTATION: making `fromStored` return its argument unchanged fails this test, and does NOT fail
     * the save-and-load test above - which is exactly the gap DR-B8 named.
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

                // The layout index is rewritten and every page comes back under a different number.
                // The NAMES are unchanged, which is the point: the setup is keyed by name in memory
                // and by number on disk, so nothing the operator can see has moved.
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
     * The case page ids exist for, and the one the other two cannot see. A setup written with page
     * names in it round-trips fine under renumbering, because names do not change when numbers do -
     * so DR-B8's "written raw" defect class needs a rename to show itself, and a rename is exactly
     * what cost Adam his setup on 23 August (MT-135).
     *
     * The store is keyed by name in memory and by id on disk. A rename changes the name and keeps the
     * id, so the entries have to be found through the id and come back under whatever the page is
     * called now.
     *
     * The rename is driven the way the application drives it - `renamePage` in memory, then a save,
     * then a load under the new name - because that is the sequence a menu item performs and this
     * matrix exists to check the real path rather than a convenient one.
     *
     * **What was measured on the way, and is worth knowing.** Skipping the in-memory `renamePage` and
     * simply reloading under the new index passes for ELEVEN of the twelve settings and fails for the
     * twelfth, "what a configuration says about the square". The eleven live in setup.json, which is
     * keyed by page ID, so they are found whatever the page is called now. The configurations are
     * keyed by page NAME on disk and are never translated - the opposite keying, in the same folder -
     * so they survive a rename only because the application rekeys them in memory first. That is not
     * a defect today; it is a dependency nothing states, and it means a rename performed by any path
     * that does not call `renamePage` orphans the configuration and nothing else.
     *
     * MUTATION: making `toStored` return its argument unchanged - writing page NAMES into setup.json,
     * which is the defect `excludedPages` really had - fails this test, and fails neither of the two
     * above.
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

                // Same id, different name: what the index reports after a rename, and the one case a
                // name-keyed file cannot survive on its own.
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
     * The column that catches a raw write, and it took three attempts to find one that could. The
     * other three all rename the page IN MEMORY first, which is what the menu item does - and a setup
     * written with page names in it survives that perfectly well, because by the time it is saved the
     * names in memory are already the new ones. Making `toStored` return its argument passes all
     * three.
     *
     * What it cannot survive is a rename that the running application never saw: the page renamed
     * with TrainControl closed, or on the other machine this layout syncs to, or through the Central
     * Station. Then setup.json holds keys written under the OLD name and the index offers only the
     * new one, and the only thing that can reunite them is the id - which is why the file is keyed by
     * id and why `toStored` exists.
     *
     * **The configuration row is excluded, deliberately and by name.** `configurations` is keyed by
     * page NAME on disk and is never translated - the opposite keying from setup.json, in the same
     * folder - so it cannot survive this and is not expected to. That is not a defect today: every
     * path that renames a page rekeys the configurations in memory first. It is a dependency that
     * nothing stated until this test, and it means a rename by any path that does not call
     * `renamePage` orphans the configuration and nothing else. Worth Adam knowing; not worth changing
     * a file format over.
     *
     * MUTATION: making `toStored` return its argument unchanged fails this test on the eleven
     * id-keyed settings, and fails none of the other three columns.
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

                // No renamePage: nothing was running when this happened.  All that changed is the
                // index, which now gives id 3 a different name.
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
     * The numbering this matrix saves and loads through.
     *
     * Three pages, numbered from something other than one, so that a translation which happens to be
     * the identity is not mistaken for one that works.
     *
     * @return page name to page id, in the string form the index hands out
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
