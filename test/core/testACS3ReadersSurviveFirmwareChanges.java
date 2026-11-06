package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.file.CS2File;
import support.StandInStation;
import support.StationRig;

/**
 * A new CS3 firmware that renames, removes or retypes a key, or answers with something else altogether, costs the
 * records it touches and nothing more: every reader skips what it cannot read with one log warning, and a sync still
 * succeeds and keeps every page (FR-169).  The fixtures are the repository's (2.5, and 2.6.1 from the CS3 plus), mutated
 * one key at a time and all keys at once, in six ways: removed, renamed, a number for a string and the reverse, null,
 * an empty object, an empty array.  The goldens of good input are testCS3InputsParseAsBefore and testACS3SyncIsAsBefore.
 *
 * The CS3 reports a solenoid's position as 128 plus the position while the current flows, then the plain position about a
 * second later; both are read.
 *
 * MUTATION: read a locomotive's `dectyp` with getString again (CS2File.parseLocomotivesCS3), and the mutated `dectyp`
 * lists fail; or take a position of 128 and 129 for none, and the energized articles are skipped.
 *
 * @author Adam
 */
public class testACS3ReadersSurviveFirmwareChanges
{
    private static final String[] LOCS = {"test/CS3_loks.json", "test/CS3_loks_v260.json"};

    private static final String[] AUTOMATICS = {"test/CS3_automatics_v260.json", "test/CS3_automatics.json"};

    private static final String[] MAGS = {"test/CS3_mags.json", "test/cs3_v261/app/api/mags.json"};

    private static final int MODES = 6;

    private static String read(String path) throws Exception
    {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
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

    private static StationRig rig() throws Exception
    {
        return new StationRig(Files.createTempDirectory("tc-cs3-mutated").toFile(), StandInStation.cs3("260", null));
    }

    @Test
    public void testNoLocomotiveListFailsTheReader() throws Exception
    {
        try (StationRig rig = rig())
        {
            CS2File parser = new CS2File("127.0.0.1:1", rig.model);

            for (String file : LOCS)
            {
                int plain = parser.parseLocomotivesCS3(new JSONArray(read(file))).size();
                assertTrue(plain > 50, "the fixture reads: " + plain);

                forEachVariant(file, list ->
                    {
                        assertTrue(fresh(rig).parseLocomotivesCS3(list).size() <= plain, "a mutation cannot add locomotives");
                        rig.takeLog();
                    });
            }
        }
    }

    @Test
    public void testNoRouteListFailsTheReader() throws Exception
    {
        try (StationRig rig = rig())
        {
            CS2File parser = new CS2File("127.0.0.1:1", rig.model);

            for (int f = 0; f < 2; f++)
            {
                String automatics = AUTOMATICS[f];
                JSONArray mags = new JSONArray(read(MAGS[f]));
                JSONArray locs = new JSONArray(read(LOCS[1 - f]));
                JSONArray routes = listOf(read(automatics));
                int plain = parser.parseRoutesCS3(routes, mags, locs).size();

                forEachVariant(automatics, list ->
                    {
                        assertTrue(fresh(rig).parseRoutesCS3(list, mags, locs).size() <= plain, "a mutation cannot add routes");
                        rig.takeLog();
                    });
                forEachVariant(MAGS[f], list ->
                    {
                        assertTrue(fresh(rig).parseRoutesCS3(routes, list, locs).size() <= plain, "bad accessories add no routes");
                        rig.takeLog();
                    });
                forEachVariant(LOCS[1 - f], list ->
                    {
                        assertTrue(fresh(rig).parseRoutesCS3(routes, mags, list).size() <= plain, "bad locomotives add no routes");
                        rig.takeLog();
                    });
            }
        }
    }

    /** One locomotive that will not read costs that locomotive and one warning, not the others. */
    @Test
    public void testOneBadLocomotiveIsSkippedWithOneWarning() throws Exception
    {
        try (StationRig rig = rig())
        {
            CS2File parser = new CS2File("127.0.0.1:1", rig.model);
            JSONArray list = new JSONArray(read(LOCS[1]));
            int plain = parser.parseLocomotivesCS3(list).size();
            rig.takeLog();

            list.getJSONObject(5).remove("uid");
            assertEquals(parser.parseLocomotivesCS3(list).size(), plain - 1, "the others are read");

            long warnings = rig.takeLog().stream().filter(l -> l.contains("could not be parsed")).count();
            assertEquals(warnings, 1, "one warning");
        }
    }

    /** A sync against a station whose answers are all mutated succeeds and keeps every page. */
    @Test
    public void testASyncSurvivesAStationThatAnswersWithOtherThingsAndKeepsEveryPage() throws Exception
    {
        String[] served = {"/app/api/locos", "/app/api/automatics", "/app/api/mags", "/app/api/mags/state", "/app/api/gbs",
            "/app/api/info"};
        Map<String, byte[]> replaced = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.function.BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", "{\"softwareVersion\":\"2.6.1\"}");

        try (StationRig rig = new StationRig(Files.createTempDirectory("tc-cs3-answers").toFile(),
            (path, n) -> replaced.containsKey(path) ? replaced.get(path) : base.apply(path, n)))
        {
            rig.model.syncWithCS2();
            List<String> pages = new ArrayList<>(rig.model.getLayoutList());
            assertTrue(pages.size() >= 3, "the pages of the fixture: " + pages);

            List<String> others = Arrays.asList("", "{}", "[]", "null", "7", "\"text\"", "<html>", "[[],{},7]",
                "{\"automatics\":7}", "{\"plans\":{}}");

            // Every answer mutated at once, in each way
            for (int mode = 0; mode < MODES; mode++)
            {
                replaced.clear();

                for (String p : served)
                {
                    byte[] b = base.apply(p, 1);

                    if (b != null && !p.equals("/app/api/info"))
                    {
                        replaced.put(p, String.valueOf(mutated(new String(b, StandardCharsets.UTF_8), null, mode))
                            .getBytes(StandardCharsets.UTF_8));
                    }
                }

                syncKeepingThePages(rig, pages, "all answers, mode " + mode);
            }

            // And each answer in turn something else altogether
            for (String path : served)
            {
                for (String other : others)
                {
                    replaced.clear();
                    replaced.put(path, other.getBytes(StandardCharsets.UTF_8));
                    syncKeepingThePages(rig, pages, path + " answering " + other);
                }
            }
        }
    }

    private static void syncKeepingThePages(StationRig rig, List<String> pages, String what) throws Exception
    {
        rig.takeLog();
        rig.model.clearLayouts();
        assertTrue(rig.model.syncWithCS2() >= 0, "the sync succeeds (" + what + "): " + rig.model.getLastSyncError());
        assertEquals(new ArrayList<>(rig.model.getLayoutList()), pages, "every page is kept (" + what + ")");
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
