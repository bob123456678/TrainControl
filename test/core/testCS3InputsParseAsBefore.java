package core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.annotations.Test;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.marklin.file.CS3TrackPlan;
import support.StandInStation;

/**
 * Every CS3 JSON input the repository holds parses as it did before the CS3 readers were hardened: the locomotive lists
 * (2.5 and 2.6), the route lists read against each accessory list, the accessory positions, and the track plans.  The
 * golden is `test/baseline/cs3_parse_golden.txt`, written by this class on dev at e9bbd789; a mismatch writes what this
 * run saw to `build/cs3_parse_actual.txt`.
 *
 * MUTATION: scale a route's locomotive speed by 10 instead of 100 (parseRoutesCS3, the "speed" item), and the routes of
 * test/CS3_automatics_lok.json differ.
 *
 * @author Adam
 */
public class testCS3InputsParseAsBefore
{
    private static final String[][] ROUTES =
    {
        {"test/CS3_automatics.json", "test/CS3_mags.json", "test/CS3_loks.json"},
        {"test/CS3_automatics_v260.json", "test/CS3_mags.json", "test/CS3_loks_v260.json"},
        {"test/CS3_automatics_v260.json", "test/cs3_v261/app/api/mags.json", "test/CS3_loks_v260.json"},
        {"test/CS3_automatics_lok.json", "test/CS3_mags.json", "test/CS3_loks_v260.json"},
        {"test/CS3_automatics_lok.json", "test/CS3_mags.json", "test/CS3_loks.json"}
    };

    private static String read(String path) throws Exception
    {
        return CS2File.parseJSONObject(CS2File.fetchURL(new File(path).toURI().toString())).toString();
    }

    private static JSONArray array(String path) throws Exception
    {
        return CS2File.parseJSONArray(CS2File.fetchURL(new File(path).toURI().toString()));
    }

    /** An automatics list: the 2.5 file is an object holding the list, the 2.6 one is the list. */
    private static JSONArray automatics(String path) throws Exception
    {
        String text = new String(java.nio.file.Files.readAllBytes(new File(path).toPath()),
            java.nio.charset.StandardCharsets.UTF_8).trim();
        return text.startsWith("[") ? new JSONArray(text) : new JSONObject(text).getJSONArray("automatics");
    }

    /** The whole dump, one line per fact. */
    static String dump() throws Exception
    {
        List<String> out = new ArrayList<>();
        CS2File parser = new CS2File(null, null);

        for (String file : new String[] {"test/CS3_loks.json", "test/CS3_loks_v260.json"})
        {
            out.add("== locomotives " + file);

            for (MarklinLocomotive l : parser.parseLocomotivesCS3(array(file)))
            {
                out.add("  " + l.toString().replace("\n", " | ") + " | functions=" + l.getNumF() + " | image="
                    + l.getImageURL() + " | uid=" + l.getUID() + " | types=" + java.util.Arrays.toString(l.getFunctionTypes())
                    + " | triggers=" + java.util.Arrays.toString(l.getFunctionTriggerTypes()) + " | members="
                    + (l.getModelMultiUnitLocomotiveNames() == null ? "-"
                        : new java.util.TreeMap<>(l.getModelMultiUnitLocomotiveNames())));
            }
        }

        for (String[] r : ROUTES)
        {
            out.add("== routes " + String.join(" + ", r));

            for (MarklinRoute route : parser.parseRoutesCS3(automatics(r[0]), array(r[1]), array(r[2])))
            {
                out.add("  " + route.toString().replace("\n", " | ") + " s88=" + route.getS88() + " | " + route.getRoute()
                    + " | " + route.toJSON());
            }
        }

        // The positions, read through a stand-in because the reader asks the station
        for (String mags : new String[] {"test/CS3_mags.json", "test/cs3_v261/app/api/mags.json"})
        {
            out.add("== positions " + mags);

            try (StandInStation station = new StandInStation((path, n) -> path.equals("/app/api/mags/state")
                ? StandInStation.file("test/cs3_v261/app/api/mags/state.json")
                : path.equals("/app/api/mags") ? StandInStation.file(mags) : null))
            {
                Map<Integer, Boolean> sorted = new TreeMap<>(new CS2File(station.address(), null).parseAccessoryStatesCS3());

                sorted.forEach((uid, turned) -> out.add("  " + Integer.toHexString(uid) + " turned=" + turned));
            }
        }

        out.add("== track plans test/cs3_v261/app/api/gbs.json");
        JSONObject gbs = new JSONObject(read("test/cs3_v261/app/api/gbs.json"));
        CS3TrackPlan plan = new CS3TrackPlan(gbs);

        for (int i = 0; i < gbs.getJSONArray("plans").length(); i++)
        {
            JSONObject p = gbs.getJSONArray("plans").getJSONObject(i);
            CS3TrackPlan.Page page = plan.page(p.optString("name"));
            int held = 0;

            for (int j = 0; j < p.getJSONArray("articles").length(); j++)
            {
                // A name without a "#" reads the same decoded
                String name = p.getJSONArray("articles").getJSONObject(j).optString("internname");
                held += !name.contains("#") && page.holds(name) ? 1 : 0;
            }

            out.add("  " + p.optString("name") + " type=" + p.optString("type") + " grid=" + page.isGrid() + " articles="
                + p.getJSONArray("articles").length() + " plainNamesHeld=" + held);
        }

        return String.join("\n", out) + "\n";
    }

    @Test
    public void testEveryCS3InputParsesAsBefore() throws Exception
    {
        support.GoldenCheck.check(dump(), "test/baseline/cs3_parse_golden.txt",
            "test/baseline/cs3_parse_golden_changes.tsv", "cs3_parse_actual.txt");
    }
}
