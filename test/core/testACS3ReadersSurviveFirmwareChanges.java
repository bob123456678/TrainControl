package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.file.CS2File;
import support.HeldState;
import support.StandInStation;
import support.StationRig;

/**
 * A new CS3 firmware that renames, removes or retypes a key, or answers with something else altogether, changes nothing
 * the application already holds that it could not read, and the sync goes on with what it could (FR-169).
 *
 * Each test syncs well, arms the routes, and keeps what is held (support.HeldState: routes with their commands, lock and
 * auto flag, locomotives with their functions, types, triggers and members, pages, accessories).  Then a mutated answer is
 * served, and the sync must succeed and leave what is held exactly as it was where the answer could not be read; then a
 * good sync must give the right result again.  A list that cannot be read, or that has no way to match its records, skips
 * that import whole; a record missing what it needs is skipped alone.
 *
 * MUTATION: have CS2File.readList return an empty list instead of throwing UnreadableAnswerException, and
 * testAnAnswerThatCannotBeReadChangesNothingHeld finds the routes replaced.  Read a locomotive's `funktionen` with
 * optJSONArray, and testALocomotiveThatCannotBeReadIsSkippedAndTheHeldOneKept finds its functions overwritten.  Read a
 * multi-unit's `lokname` with optString, and the same test finds the members gone.  Read `dectyp` with optString and a
 * default, and testAnEmptyDatabaseIsNotFilledWithGuesses finds every locomotive stored as MM2.
 *
 * @author Adam
 */
public class testACS3ReadersSurviveFirmwareChanges
{
    private static final String[] LOCS = {"test/CS3_loks.json", "test/CS3_loks_v260.json"};

    private static final String[] AUTOMATICS = {"test/CS3_automatics_v260.json", "test/CS3_automatics.json"};

    private static final String[] MAGS = {"test/CS3_mags.json", "test/cs3_v261/app/api/mags.json"};

    private static final String INFO = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    private static final String LOCOS = "/app/api/locos";

    private static final String ROUTES = "/app/api/automatics";

    private static final String ACCESSORIES = "/app/api/mags";

    private static final int MODES = 6;

    private static String read(String path) throws Exception
    {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(Object json)
    {
        return String.valueOf(json).getBytes(StandardCharsets.UTF_8);
    }

    /** The JSON of a file with the key (every key when null) mutated in one of the six ways, wherever it occurs. */
    private static Object mutated(String json, String key, int mode)
    {
        Object root = new JSONTokener(json).nextValue();
        walk(root, key, mode);
        return root;
    }

    private static void walk(Object node, String key, int mode)
    {
        if (node instanceof JSONArray)
        {
            for (int i = 0; i < ((JSONArray) node).length(); i++) walk(((JSONArray) node).get(i), key, mode);
        }
        else if (node instanceof JSONObject)
        {
            JSONObject o = (JSONObject) node;

            for (String k : new ArrayList<>(o.keySet()))
            {
                Object child = o.get(k);

                if (key != null && !k.equals(key))
                {
                    walk(child, key, mode);
                    continue;
                }

                o.remove(k);

                switch (mode)
                {
                    case 1: o.put(k + "_renamed", child); break;
                    case 2: o.put(k, child instanceof String ? (Object) 7 : "x"); break;
                    case 3: o.put(k, JSONObject.NULL); break;
                    case 4: o.put(k, new JSONObject()); break;
                    case 5: o.put(k, new JSONArray()); break;
                    default: break;
                }
            }
        }
    }

    /** The list a file holds: the array itself, or the one under "automatics" in the 2.5 routes. */
    private static JSONArray listOf(Object parsed)
    {
        if (parsed instanceof JSONArray) return (JSONArray) parsed;

        // A 2.5 file whose "automatics" was mutated holds no list
        JSONArray inside = ((JSONObject) parsed).optJSONArray("automatics");
        return inside == null ? new JSONArray() : inside;
    }

    private static JSONArray listOf(String json)
    {
        return listOf(new JSONTokener(json).nextValue());
    }

    private static Set<String> keysOf(Object node, Set<String> into)
    {
        if (node instanceof JSONArray)
        {
            for (int i = 0; i < ((JSONArray) node).length(); i++) keysOf(((JSONArray) node).get(i), into);
        }
        else if (node instanceof JSONObject)
        {
            for (String k : ((JSONObject) node).keySet())
            {
                into.add(k);
                keysOf(((JSONObject) node).get(k), into);
            }
        }

        return into;
    }

    /** Hands over, one at a time, every (key, mode) of the file and all keys at once in each mode, as the mutated array. */
    private static void forEachVariant(String file, java.util.function.Consumer<JSONArray> each) throws Exception
    {
        String json = read(file);
        List<String> keys = new ArrayList<>(keysOf(listOf(json), new java.util.TreeSet<String>()));
        keys.add(null);

        for (String key : keys)
        {
            for (int mode = 0; mode < MODES; mode++)
            {
                each.accept(listOf(mutated(json, key, mode)));
            }
        }
    }

    /** A parser with nothing remembered: it keeps the records it has found, which a mutated list must not be given back. */
    private static CS2File fresh(StationRig rig)
    {
        return new CS2File("127.0.0.1:1", rig.model);
    }

    /** The answers of a 2.6.1 CS3 with the accessory list that the routes' articles are in, mutated where `replaced` says. */
    private static BiFunction<String, Integer, byte[]> station(Map<String, byte[]> replaced)
    {
        BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", INFO);

        return (path, n) -> replaced.containsKey(path) ? replaced.get(path)
            : ACCESSORIES.equals(path) ? StandInStation.file(MAGS[0]) : base.apply(path, n);
    }

    private static StationRig rig(Map<String, byte[]> replaced) throws Exception
    {
        return new StationRig(Files.createTempDirectory("tc-cs3-held").toFile(), station(replaced));
    }

    /** Syncs well, arms the routes that can be, and returns what is held. */
    private static String held(StationRig rig, String what) throws Exception
    {
        rig.model.syncWithCS2();
        assertTrue(rig.model.syncWithCS2() >= 0, "the good sync succeeds (" + what + "): " + rig.model.getLastSyncError());

        for (MarklinRoute r : rig.model.getRoutes())
        {
            if (r.hasS88()) r.enable();
        }

        return state(rig);
    }

    private static String state(StationRig rig) throws Exception
    {
        return HeldState.of(rig.model).replace(rig.station.address(), "STATION");
    }

    private static void disarm(StationRig rig)
    {
        for (MarklinRoute r : rig.model.getRoutes()) r.disable();
    }

    /** Serves `answers` for one sync and returns the sync's result; the good answers are back after. */
    private static int syncWith(StationRig rig, Map<String, byte[]> replaced, Map<String, byte[]> answers) throws Exception
    {
        replaced.putAll(answers);
        int result = rig.model.syncWithCS2();
        replaced.clear();
        rig.takeLog();
        return result;
    }

    /** The sync succeeds, what is held is as it was, and a good sync gives the same again. */
    private static void assertHeldUnchanged(StationRig rig, Map<String, byte[]> replaced, String held, String what,
        Map<String, byte[]> answers) throws Exception
    {
        int result = syncWith(rig, replaced, answers);

        assertTrue(result >= 0, "the sync goes on (" + what + "): " + rig.model.getLastSyncError());
        assertEquals(state(rig), held, "what is held is unchanged (" + what + ")");
        assertTrue(rig.model.syncWithCS2() >= 0, "a good sync after it (" + what + ")");
        assertEquals(state(rig), held, "a good sync after it gives the same (" + what + ")");
    }

    private static Map<String, byte[]> only(String path, Object body)
    {
        Map<String, byte[]> one = new ConcurrentHashMap<>();
        one.put(path, body instanceof byte[] ? (byte[]) body : bytes(body));
        return one;
    }

    /** A list that is empty, cut off, another shape or has no way to match its records leaves the routes as they are. */
    @Test
    public void testAnAnswerThatCannotBeReadChangesNothingHeld() throws Exception
    {
        Map<String, byte[]> replaced = new ConcurrentHashMap<>();

        try (StationRig rig = rig(replaced))
        {
            String held = held(rig, "start");

            try
            {
                int commands = rig.model.getRoutes().stream().mapToInt(r -> r.getRoute().size()).sum();
                assertTrue(commands > 100, "the routes hold many commands: " + commands);
                assertTrue(rig.model.getRoutes().stream().anyMatch(r -> r.isEnabled()), "some route is armed");

                String[] paths = {ROUTES, ACCESSORIES, LOCOS};
                String[] files = {AUTOMATICS[0], MAGS[0], LOCS[1]};

                for (int p = 0; p < paths.length; p++)
                {
                    String good = read(files[p]);
                    Object[] answers = {"", "<html>not json</html>", good.substring(0, good.length() / 2), "{}", "7", "null",
                        "\"text\"", "{\"automatics\":7}"};

                    for (Object answer : answers)
                    {
                        assertHeldUnchanged(rig, replaced, held, paths[p] + " answering " + answer.toString().substring(0,
                            Math.min(20, answer.toString().length())), only(paths[p], answer));
                    }
                }

                // Empty lists, while the routes use accessories and locomotives
                assertHeldUnchanged(rig, replaced, held, "no accessories", only(ACCESSORIES, "[]"));
                assertHeldUnchanged(rig, replaced, held, "no locomotives", only(LOCOS, "[]"));

                // Lists whose records cannot be matched: the key that names them renamed, or not what it was
                for (int mode : new int[] {0, 1, 2})
                {
                    assertHeldUnchanged(rig, replaced, held, "accessory id, mode " + mode,
                        only(ACCESSORIES, mutated(read(MAGS[0]), "id", mode)));
                    assertHeldUnchanged(rig, replaced, held, "locomotive internname, mode " + mode,
                        only(LOCOS, mutated(read(LOCS[1]), "internname", mode)));
                }
            }
            finally
            {
                disarm(rig);
            }
        }
    }

    /** A record missing what it needs is skipped, with one warning, and the one held is not touched. */
    @Test
    public void testALocomotiveThatCannotBeReadIsSkippedAndTheHeldOneKept() throws Exception
    {
        Map<String, byte[]> replaced = new ConcurrentHashMap<>();

        try (StationRig rig = rig(replaced))
        {
            String held = held(rig, "start");

            try
            {
                String[] keys = {"uid", "name", "dectyp", "funktionen", "typ", "typ2", "isMoment", "dauer", "lokname"};

                for (String key : keys)
                {
                    for (int mode : new int[] {0, 1, 2})
                    {
                        assertHeldUnchanged(rig, replaced, held, key + ", mode " + mode,
                            only(LOCOS, mutated(read(LOCS[1]), key, mode)));
                    }
                }

                // A multi-unit's members are the whole of its entry: one entry that will not read skips the unit
                assertHeldUnchanged(rig, replaced, held, "traktion retyped", only(LOCOS, mutated(read(LOCS[1]), "traktion", 2)));

                // An icon is not needed: a locomotive without one keeps the one it holds
                assertHeldUnchanged(rig, replaced, held, "icon removed", only(LOCOS, mutated(read(LOCS[1]), "icon", 0)));

                // One record only, and one warning
                JSONArray one = new JSONArray(read(LOCS[1]));
                one.getJSONObject(5).remove("funktionen");
                rig.takeLog();
                replaced.put(LOCOS, bytes(one));
                assertTrue(rig.model.syncWithCS2() >= 0);
                replaced.clear();

                assertEquals(state(rig), held, "the others are read as they were, and the held one is kept");
                assertEquals(rig.takeLog().stream().filter(l -> l.contains("could not be parsed")).count(), 1, "one warning");
            }
            finally
            {
                disarm(rig);
            }
        }
    }

    /** A first sync with locomotives missing their decoder type guesses nothing: the database stays empty, and a good sync fills it. */
    @Test
    public void testAnEmptyDatabaseIsNotFilledWithGuesses() throws Exception
    {
        Map<String, byte[]> replaced = new ConcurrentHashMap<>();
        String good;

        try (StationRig rig = rig(new ConcurrentHashMap<>()))
        {
            rig.model.syncWithCS2();
            good = state(rig);
        }

        replaced.put(LOCOS, bytes(mutated(read(LOCS[1]), "dectyp", 0)));

        try (StationRig rig = rig(replaced))
        {
            assertTrue(rig.model.syncWithCS2() >= 0, "the sync goes on: " + rig.model.getLastSyncError());
            assertEquals(rig.model.getLocomotives().size(), 0, "no locomotive is stored on a guess");

            replaced.clear();
            rig.model.syncWithCS2();
            assertEquals(state(rig), good, "a good sync gives the right result");
        }
    }

    /** No reader throws on a locomotive or route list with any key mutated, and none gives more than the good list does. */
    @Test
    public void testNoListFailsTheReader() throws Exception
    {
        try (StationRig rig = rig(new ConcurrentHashMap<>()))
        {
            int locs = fresh(rig).parseLocomotivesCS3(new JSONArray(read(LOCS[1]))).size();
            JSONArray mags = new JSONArray(read(MAGS[0]));
            JSONArray locations = new JSONArray(read(LOCS[1]));
            JSONArray routes = listOf(read(AUTOMATICS[0]));
            int plain = fresh(rig).parseRoutesCS3(routes, mags, locations).size();
            assertTrue(locs > 50 && plain > 20, "the fixtures read: " + locs + ", " + plain);

            sweep(rig, LOCS[1], list -> fresh(rig).parseLocomotivesCS3(list).size(), locs);
            sweep(rig, AUTOMATICS[0], list -> fresh(rig).parseRoutesCS3(list, mags, locations).size(), plain);
            sweep(rig, MAGS[0], list -> fresh(rig).parseRoutesCS3(routes, list, locations).size(), plain);
            sweep(rig, LOCS[1], list -> fresh(rig).parseRoutesCS3(routes, mags, list).size(), plain);
        }
    }

    /** Every variant of the file is read without throwing, and gives no more than the good one did. */
    private static void sweep(StationRig rig, String file, java.util.function.ToIntFunction<JSONArray> parse, int most)
        throws Exception
    {
        forEachVariant(file, list ->
        {
            assertTrue(parse.applyAsInt(list) <= most, "a mutation cannot add records");
            rig.takeLog();
        });
    }

    /** A sync against a station whose answers are all mutated, or each something else, succeeds and keeps every page. */
    @Test
    public void testASyncSurvivesAStationThatAnswersWithOtherThingsAndKeepsEveryPage() throws Exception
    {
        String[] served = {LOCOS, ROUTES, ACCESSORIES, "/app/api/mags/state", "/app/api/gbs"};
        Map<String, byte[]> replaced = new ConcurrentHashMap<>();
        BiFunction<String, Integer, byte[]> base = station(new ConcurrentHashMap<>());

        try (StationRig rig = rig(replaced))
        {
            String held = held(rig, "start");

            try
            {
                List<String> pages = new ArrayList<>(rig.model.getLayoutList());
                assertTrue(pages.size() >= 3, "the pages of the fixture: " + pages);

                // Every answer mutated at once, in each way
                for (int mode = 0; mode < MODES; mode++)
                {
                    for (String p : served)
                    {
                        replaced.put(p, bytes(mutated(new String(base.apply(p, 1), StandardCharsets.UTF_8), null, mode)));
                    }

                    syncKeepingThePages(rig, replaced, pages, "all answers, mode " + mode);
                }

                // And each answer in turn something else altogether
                for (String path : served)
                {
                    for (String other : new String[] {"", "{}", "[]", "null", "7", "\"text\"", "<html>", "[[],{},7]"})
                    {
                        replaced.put(path, other.getBytes(StandardCharsets.UTF_8));
                        syncKeepingThePages(rig, replaced, pages, path + " answering " + other);
                    }
                }

                // As a start-up does: the pages are built from what the station gives
                rig.model.clearLayouts();
                assertTrue(rig.model.syncWithCS2() >= 0, "a good sync after them all");
                assertEquals(state(rig), held, "and it gives the right result");
            }
            finally
            {
                disarm(rig);
            }
        }
    }

    private static void syncKeepingThePages(StationRig rig, Map<String, byte[]> replaced, List<String> pages, String what)
        throws Exception
    {
        rig.takeLog();
        rig.model.clearLayouts();
        assertTrue(rig.model.syncWithCS2() >= 0, "the sync succeeds (" + what + "): " + rig.model.getLastSyncError());
        assertEquals(new ArrayList<>(rig.model.getLayoutList()), pages, "every page is kept (" + what + ")");
        replaced.clear();
    }

    /** 128 and 129 are the positions 0 and 1 with the current flowing; any other state is skipped. */
    @Test
    public void testAnEnergizedSolenoidsPositionIsRead() throws Exception
    {
        List<JSONObject> two = new ArrayList<>();
        JSONArray mags = new JSONArray(read(MAGS[1]));

        for (int i = 0; i < mags.length() && two.size() < 4; i++)
        {
            JSONObject a = mags.getJSONObject(i);

            if (a.optInt("states") == 2 && a.optInt("address") > 0 && "mm".equals(a.optString("prot"))) two.add(a);
        }

        assertEquals(two.size(), 4, "four two-position articles in the fixture");

        String[] states = {"128", "129", "0", "130"};
        JSONArray reported = new JSONArray();

        for (int i = 0; i < 4; i++)
        {
            reported.put(new JSONArray().put(String.valueOf(two.get(i).get("id"))).put(new JSONObject().put("state", states[i])));
        }

        try (StandInStation s = new StandInStation((path, n) -> path.equals("/app/api/mags/state")
            ? reported.toString().getBytes(StandardCharsets.UTF_8) : StandInStation.file(MAGS[1])))
        {
            Map<Integer, Boolean> positions = new CS2File(s.address(), null).parseAccessoryStatesCS3();
            int[] uids = new int[4];

            for (int i = 0; i < 4; i++)
            {
                uids[i] = org.traincontrol.marklin.MarklinAccessory.UIDfromAddress(two.get(i).getInt("address") - 1,
                    org.traincontrol.base.Accessory.accessoryDecoderType.MM2);
            }

            assertEquals(positions.get(uids[0]), Boolean.TRUE, "128 is position 0, turned");
            assertEquals(positions.get(uids[1]), Boolean.FALSE, "129 is position 1");
            assertEquals(positions.get(uids[2]), Boolean.TRUE, "the plain 0");
            assertFalse(positions.containsKey(uids[3]), "130 is not a position");
        }
    }
}
