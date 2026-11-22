package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.automation.Layout;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * Every autonomy file the repository holds, the configuration the live snapshot's setup builds, and a file using every
 * key `Layout.fromJSON` reads, valid and then broken one key at a time and two at a time, load exactly as they did
 * before FR-205 split the reader: whether each is refused, every reason in order (the first failure first), what the
 * load logged, and a digest of the Layout's `toJSON` (and of a second load of that).  The golden,
 * `test/baseline/fromjson_golden.txt`, was written by `main` below run on dev at 577b37883.
 *
 * MUTATION: read "maxLatency" before "preArrivalSpeedReduction" in readSettings, and the case with both wrong gives the
 * other reason; or make ConfigurationFile.count answer 0 for an absent key, and an absent trainLength clears the
 * train's; or write `speed > 0 && speed <= 100` in placeLocomotive as `speed < 100`, and the loc speed 100 and 0
 * cases differ.
 */
public class testTheLoaderReadsEveryFileAsBefore
{
    private static final String GOLDEN = "test/baseline/fromjson_golden.txt";

    // One copy of each file: the others in test/ are byte for byte one of these
    private static final String[] FILES =
    {
        "test/autonomy.json", "test/autonomy_sanity.json", "test/autonomy_sanity_forks.json", "test/sample_autonomy.json",
        "test/autonomy_formats/autonomy-2.7.4c.json", "test/autonomy_formats/legacy-graph-sample-layout.json",
        "test/baseline/configuration.json", "test/baseline/layout/config/autorun/autonomy.json",
        "test/test_layout/config/autorun/autonomy.json"
    };

    private static final String ONE = "FJ one", TWO = "FJ two";

    /** Every key fromJSON reads, valid; each variant breaks it. */
    private static final String EVERY_KEY = ("{'minDelay': 1, 'maxDelay': 2, 'defaultLocSpeed': 40,"
        + " 'preArrivalSpeedReduction': 0.4, 'maxLatency': 300, 'maxActiveTrains': 2, 'maxLocInactiveSeconds': 60,"
        + " 'turnOffFunctionsOnArrival': true, 'turnOnFunctionsOnDeparture': false,"
        + " 'pathPreference': 'SHORTEST_LENGTH',"
        + " 'simulate': false, 'atomicRoutes': false, 'pausedLocomotives': ['FJ two', 7], 'timetable': [{'bogus': 1}],"
        + " 'timetableSequential': true, 'activateRoutes': true, 'activateRouteIDs': [3, 1],"
        + " 'points': [{'name': 'FJ A', 'station': true, 's88': 9001, 'x': 1, 'y': 2, 'block': 'b1', 'square': 's1',"
        + " 'protectingSignal': 'Signal 251', 'entrySignal': ['Signal 252', 'Signal 253'], 'arrivedFrom': 'FJ B',"
        + " 'arrivedAlong': [['FJ B', 'FJ A']], 'blockedBy': ['FJ C', 'FJ none'], 'home': 'FJ one',"
        + " 'homeFacingFixed': true, 'copyFacing': 'east', 'runThrough': false, 'excludedLocs': ['FJ two', 'FJ none'],"
        + " 'excludedLocsMode': 'whitelist', 'maxTrainLength': 50, 'active': true, 'autoDestination': false,"
        + " 'reversing': false, 'speedMultiplier': 0.5, 'priority': 3, 'loc': {'name': 'FJ one', 'trainLength': 40,"
        + " 'reversible': true, 'speed': 50, 'departureFunc': 3, 'arrivalFunc': 4}},"
        + " {'name': 'FJ B', 'station': true, 's88': 9002, 'terminus': true, 'loc': {'name': 'FJ two'}},"
        + " {'name': 'FJ C'}],"
        + " 'edges': [{'start': 'FJ A', 'end': 'FJ B', 'commands': [{'acc': 'Switch 250', 'state': 'turn'}],"
        + " 'roomAtTheEnd': 5, 'entrySide': 'north', 'places': [{'at': 't1', 'length': 10, 'answered': true,"
        + " 'toMeasure': 't1 piece'}, {'at': 't2'}], 'lengthsAsked': true, 'length': 30,"
        + " 'lockedges': [{'start': 'FJ B', 'end': 'FJ A'}]},"
        + " {'start': 'FJ B', 'end': 'FJ A', 'commands': [{'acc': 'Switch 250', 'state': 'straight'}]},"
        + " {'start': 'FJ B', 'end': 'FJ C'}]}").replace('\'', '"');

    private static final class Variant
    {
        final String label;
        final Consumer<JSONObject> breaks;

        Variant(String label, Consumer<JSONObject> breaks)
        {
            this.label = label;
            this.breaks = breaks;
        }
    }

    private static JSONObject pt(JSONObject o, int i)
    {
        return o.getJSONArray("points").getJSONObject(i);
    }

    private static JSONObject loc(JSONObject o)
    {
        return pt(o, 0).getJSONObject("loc");
    }

    private static JSONObject ed(JSONObject o, int i)
    {
        return o.getJSONArray("edges").getJSONObject(i);
    }

    private static Variant set(String key, Object to)
    {
        return new Variant(key + "=" + to, o -> o.put(key, to));
    }

    private static Variant gone(String key)
    {
        return new Variant(key + " absent", o -> o.remove(key));
    }

    private static Variant point(String key, Object to)
    {
        return new Variant("point " + key + "=" + to, o -> pt(o, 0).put(key, to));
    }

    private static Variant onLoc(String key, Object to)
    {
        return new Variant("loc " + key + "=" + to, o -> loc(o).put(key, to));
    }

    private static Variant edge(String key, Object to)
    {
        return new Variant("edge " + key + "=" + to, o -> ed(o, 0).put(key, to));
    }

    private static JSONArray list(String json)
    {
        return new JSONArray(json.replace('\'', '"'));
    }

    /** In the order fromJSON reads them, so neighbors are the pairs whose order is pinned. */
    private static List<Variant> variants()
    {
        Object nul = JSONObject.NULL;
        List<Variant> v = new ArrayList<>();

        Collections.addAll(v, gone("points"), set("points", "x"), set("points", nul), gone("edges"), set("minDelay", "a"),
            gone("maxDelay"), set("defaultLocSpeed", "a"), set("turnOffFunctionsOnArrival", "x"),
            set("pathPreference", "NOPE"), set("pathPreference", 5), set("turnOnFunctionsOnDeparture", "x"),
            gone("turnOnFunctionsOnDeparture"), set("preArrivalSpeedReduction", "x"), set("preArrivalSpeedReduction", 5),
            set("maxLatency", "x"), set("maxLatency", -5), set("maxActiveTrains", "x"), set("maxActiveTrains", -1),
            set("maxActiveTrains", 0), set("maxLocInactiveSeconds", "x"), set("maxLocInactiveSeconds", 0),
            set("simulate", "x"), set("simulate", true), set("pausedLocomotives", "x"), set("atomicRoutes", "x"),
            set("atomicRoutes", true));

        Collections.addAll(v, point("s88", "9001"), point("s88", nul), point("s88", 1.5), point("x", "1"),
            point("y", "2"), new Variant("point y absent", o -> pt(o, 0).remove("y")),
            new Variant("point name absent", o -> pt(o, 0).remove("name")), point("name", 5), point("station", "x"),
            point("block", 5), point("square", 5), point("protectingSignal", 5), point("protectingSignal", list("[5]")),
            point("entrySignal", 5), point("arrivedFrom", 5), point("arrivedAlong", "x"), point("blockedBy", 5),
            point("blockedBy", list("[5]")), point("home", "FJ none"), point("home", 5), point("home", " "),
            point("homeFacingFixed", "x"), point("copyFacing", 5), point("runThrough", "x"), point("runThrough", true),
            point("excludedLocs", "x"), point("excludedLocs", list("[5]")), point("excludedLocsMode", "blacklist"),
            point("maxTrainLength", -1), point("maxTrainLength", "x"), point("maxTrainLength", nul),
            new Variant("point maxTrainLength absent", o -> pt(o, 0).remove("maxTrainLength")), point("terminus", "x"),
            new Variant("point terminus and reversing", o -> pt(o, 0).put("terminus", true).put("reversing", true)),
            point("active", "x"), point("autoDestination", "x"), point("reversing", "x"), point("speedMultiplier", "x"),
            point("speedMultiplier", -1), point("priority", "x"), point("priority", 1.5));

        Collections.addAll(v, point("loc", "x"), point("loc", nul), new Variant("loc name absent",
            o -> loc(o).remove("name")), onLoc("name", 5), onLoc("name", "FJ none"), onLoc("trainLength", -1),
            onLoc("trainLength", "x"), new Variant("loc trainLength absent", o -> loc(o).remove("trainLength")),
            onLoc("reversible", "x"), onLoc("speed", "x"), onLoc("speed", 500), onLoc("speed", 101), onLoc("speed", 100),
            onLoc("speed", 1), onLoc("speed", 0), onLoc("speed", -1),
            new Variant("loc speed absent", o -> loc(o).remove("speed")), onLoc("departureFunc", "x"),
            onLoc("arrivalFunc", "x"), new Variant("loc twice", o -> pt(o, 1).getJSONObject("loc").put("name", ONE)),
            new Variant("loc off a station", o -> pt(o, 2).put("loc", pt(o, 1).remove("loc"))));

        Collections.addAll(v, new Variant("edge start absent", o -> ed(o, 0).remove("start")), edge("end", "FJ none"),
            edge("commands", "x"), edge("commands", nul), edge("commands", list("[{'state': 'turn'}]")),
            edge("commands", list("[{'acc': null, 'state': 'turn'}]")), edge("commands", list("[{'acc': 'Switch 250'}]")),
            edge("commands", list("[{'acc': 'Switch 250', 'state': 'sideways'}]")),
            edge("commands", list("[{'acc': 'Switch 99999', 'state': 'turn'}]")), edge("commands", list("[5]")),
            edge("roomAtTheEnd", "x"), edge("entrySide", 5), edge("places", "x"), edge("places", list("[{'at': 't1'}, {}]")),
            edge("places", list("[{'at': 5}]")), edge("places", list("[{'at': 't1', 'length': 'x'}]")), edge("length", -1),
            edge("length", "x"), new Variant("edge length absent", o -> ed(o, 0).remove("length")), edge("lockedges", "x"),
            edge("lockedges", list("[{'start': 'FJ A', 'end': 'FJ none'}]")), edge("lockedges", list("[{'end': 'FJ A'}]")),
            edge("lockedges", list("[5]")));

        Collections.addAll(v, set("timetable", "x"), gone("timetable"), set("timetableSequential", "x"),
            set("activateRoutes", "x"), gone("activateRoutes"), set("activateRouteIDs", "x"),
            set("activateRouteIDs", list("[2, 'a']")));

        return v;
    }

    private static String digest(String s) throws Exception
    {
        StringBuilder hex = new StringBuilder();

        for (byte b : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))
        {
            hex.append(String.format("%02x", b));
        }

        return hex.substring(0, 16);
    }

    /** One load: refused or not, the reasons, the trains to run, the log, and toJSON in full or as a digest. */
    private static String load(MarklinControlStation model, List<String> said, List<String> out, String config,
        boolean whole, boolean again) throws Exception
    {
        said.clear();

        Layout layout;

        try
        {
            layout = Layout.fromJSON(config, model);
        }
        catch (RuntimeException e)
        {
            out.add("THREW " + e);
            out.add("said: " + String.join(" | ", said).replace("\n", " / "));
            return null;
        }

        List<String> run = new ArrayList<>();

        for (Locomotive l : layout.getLocomotivesToRun()) run.add(l.getName());

        Collections.sort(run);

        out.add("valid=" + layout.isValid() + " reason="
            + String.valueOf(layout.getInvalidReason()).replace("\n", " | "));
        out.add("run=" + run);

        for (String line : said) out.add("said: " + line.replace("\n", " / "));

        String json;

        try
        {
            json = layout.toJSON();
        }
        catch (RuntimeException e)
        {
            out.add("toJSON threw " + e.getClass().getName());
            return null;
        }

        if (whole) Collections.addAll(out, json.replace("\r", "").split("\n"));
        else out.add("json=" + digest(json));

        if (again)
        {
            out.add("-- again");
            load(model, said, out, json, false, false);
        }

        return json;
    }

    /** The whole dump, against a station on the live snapshot. */
    public static String dump() throws Exception
    {
        support.LayoutSandbox sandbox = null;
        MarklinControlStation model = null;
        List<String> said = Collections.synchronizedList(new ArrayList<String>());
        java.util.logging.Handler tap = support.LogTap.everyMessageInto(said);
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MarklinControlStation.class.getName());

        try
        {
            sandbox = support.LayoutSandbox.open(support.Scenario.folderFor("live-snapshot"));
            model = support.TestStation.simulated();
            model.newMM2Locomotive(ONE, 72);
            model.newMM2Locomotive(TWO, 73);

            AutonomySession session = new AutonomySession(sandbox.getFolder());
            session.open(support.LayoutSandbox.wiredPages(model));
            String built = session.buildConfiguration();

            logger.addHandler(tap);
            List<String> out = new ArrayList<>();

            for (String file : FILES)
            {
                out.add("== file " + file);
                load(model, said, out, new String(Files.readAllBytes(new File(file).toPath()), StandardCharsets.UTF_8),
                    false, true);
            }

            out.add("== built from live-snapshot");
            load(model, said, out, built, false, true);

            out.add("== every key");
            load(model, said, out, EVERY_KEY, true, true);

            List<Variant> variants = variants();

            for (int i = 0; i < variants.size(); i++)
            {
                JSONObject o = new JSONObject(EVERY_KEY);
                variants.get(i).breaks.accept(o);
                out.add("== " + variants.get(i).label);
                load(model, said, out, o.toString(), false, false);

                if (i + 1 == variants.size()) break;

                // Two wrong at once: the one read first must still be named first
                o = new JSONObject(EVERY_KEY);
                out.add("== " + variants.get(i).label + " + " + variants.get(i + 1).label);

                try
                {
                    variants.get(i).breaks.accept(o);
                    variants.get(i + 1).breaks.accept(o);
                }
                catch (RuntimeException notBoth)
                {
                    out.add("not both");
                    continue;
                }

                load(model, said, out, o.toString(), false, false);
            }

            return String.join("\n", out) + "\n";
        }
        finally
        {
            logger.removeHandler(tap);

            if (model != null) model.deleteLoc(ONE);
            if (model != null) model.deleteLoc(TWO);
            if (sandbox != null) sandbox.close();
        }
    }

    @Test
    public void testEveryFileLoadsAsBefore() throws Exception
    {
        List<String> want = Files.readAllLines(new File(GOLDEN).toPath(), StandardCharsets.UTF_8);
        List<String> got = java.util.Arrays.asList(dump().split("\n"));
        int same = 0;

        while (same < Math.min(want.size(), got.size()) && want.get(same).equals(got.get(same))) same++;

        assertTrue(same == want.size() && same == got.size(), "line " + (same + 1) + " of " + GOLDEN + " differs: now "
            + (same < got.size() ? got.get(same) : "(the end)")
            + "\n  was " + (same < want.size() ? want.get(same) : "(the end)"));
        assertTrue(want.size() > 1000, "the golden covers the inputs: " + want.size() + " lines");
    }

    /** Writes the golden to the file named: run on the code before the change. */
    public static void main(String[] args) throws Exception
    {
        Files.write(new File(args[0]).toPath(), dump().getBytes(StandardCharsets.UTF_8));
        System.exit(0);
    }
}
