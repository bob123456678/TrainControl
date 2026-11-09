package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import support.StandInStation;
import support.StationRig;

/**
 * The CS3 records the reader of 3.0.8 took are still taken, as it took them, and a record it skipped is still skipped
 * without the start-up warning that part of the data could not be read (FR-169).  Each claim is the CS3 sync golden's
 * 2.6.1 station (StandInStation.cs3) with one record changed, synced into an empty database.
 *
 * @author Adam
 */
public class testACS3SyncTakesWhatItTookBefore
{
    private static final String INFO = "{\"softwareVersion\":\"2.6.1 (Build 3)\"}";

    private static final String LOCOS = "/app/api/locos";

    private static final String ROUTES = "/app/api/automatics";

    /** The probe route of test/CS3_automatics_lok.json: a switch, a speed and a direction for one locomotive, a speed for another. */
    private static final String PROBE = "PROBE switch then go";

    private static JSONArray locomotives() throws Exception
    {
        return new JSONArray(new String(Files.readAllBytes(new File("test/CS3_loks_v260.json").toPath()), StandardCharsets.UTF_8));
    }

    private static JSONArray routes()
    {
        return new JSONArray(new String(StandInStation.cs3("260", INFO).apply(ROUTES, 1), StandardCharsets.UTF_8));
    }

    private static JSONObject named(JSONArray list, String name)
    {
        for (int i = 0; i < list.length(); i++)
        {
            if (name.equals(list.getJSONObject(i).optString("name"))) return list.getJSONObject(i);
        }

        throw new AssertionError("no record " + name);
    }

    /** The golden 2.6.1 station, answering `body` at `path`. */
    private static StationRig rig(String path, Object body) throws Exception
    {
        BiFunction<String, Integer, byte[]> base = StandInStation.cs3("260", INFO);
        byte[] answer = String.valueOf(body).getBytes(StandardCharsets.UTF_8);

        return new StationRig(Files.createTempDirectory("tc-cs3-took").toFile(), (p, n) -> path.equals(p) ? answer : base.apply(p, n));
    }

    /**
     * A locomotive without `internname` is imported, and nothing is said of it at start-up.
     *
     * MUTATION: read `internname` with getString in CS2File.parseLocomotivesCS3's record loop, and the locomotive is skipped.
     */
    @Test
    public void testALocomotiveWithoutAnInternalNameIsImported() throws Exception
    {
        JSONArray locs = locomotives();
        named(locs, "065 001-0 DB").remove("internname");

        try (StationRig rig = rig(LOCOS, locs))
        {
            assertTrue(rig.model.syncWithCS2() >= 0);
            assertNotNull(rig.model.getLocByName("065 001-0 DB"), "the locomotive is imported");
            assertFalse(rig.model.wasLastSyncPartial(), "and nothing is said of it at start-up");
        }
    }

    /**
     * A 2.6 multi-unit whose `traktion` entry has no `lokname` is imported with its other members, and nothing is said of
     * it at start-up.
     *
     * MUTATION: read `lokname` with getString in CS2File.parseLocomotivesCS3, and the unit is skipped.
     */
    @Test
    public void testAMultiUnitEntryWithoutAMemberNameKeepsTheUnit() throws Exception
    {
        Map<String, Double> members;

        try (StationRig rig = rig(LOCOS, locomotives()))
        {
            assertTrue(rig.model.syncWithCS2() >= 0);
            members = new HashMap<>(rig.model.getLocByName("ES44 x2").getModelMultiUnitLocomotiveNames());
        }

        JSONArray locs = locomotives();
        JSONObject entry = named(locs, "ES44 x2").getJSONObject("traktion").getJSONObject("01");
        String gone = "ES44AC 7912 UP";
        assertTrue(members.size() == 3 && members.containsKey(gone) && "ES44AC#207912#20UP".equals(entry.getString("lokname")),
            "the unit of the fixture: " + members);
        entry.remove("lokname");
        members.remove(gone);

        try (StationRig rig = rig(LOCOS, locs))
        {
            assertTrue(rig.model.syncWithCS2() >= 0);
            MarklinLocomotive unit = rig.model.getLocByName("ES44 x2");
            assertNotNull(unit, "the unit is imported");
            assertEquals(unit.getModelMultiUnitLocomotiveNames(), members, "with the members it names");
            assertFalse(rig.model.wasLastSyncPartial(), "and nothing is said of it at start-up");
        }
    }

    /**
     * A route's speed or direction item without `lok` is left out of the route, and a direction that is neither 0 nor 1
     * is forward; the route is imported, and nothing is said of it at start-up.
     *
     * MUTATION: throw in CS2File.parseRoutesCS3 for a train command without `lok`, or for a direction that is neither 0
     * nor 1, and the route is skipped.
     */
    @Test
    public void testARouteKeepsTheItemsItCanRead() throws Exception
    {
        List<Consumer<JSONArray>[]> cases = new ArrayList<>();
        cases.add(pair(items -> items.getJSONObject(1).remove("lok"), items -> items.remove(1)));
        cases.add(pair(items -> items.getJSONObject(2).remove("lok"), items -> items.remove(2)));
        cases.add(pair(items -> items.getJSONObject(2).put("wert", 2), items -> items.getJSONObject(2).put("wert", 0)));
        cases.add(pair(items -> items.getJSONObject(2).put("wert", "x"), items -> items.getJSONObject(2).put("wert", "0")));

        for (int c = 0; c < cases.size(); c++)
        {
            JSONArray given = routes();
            JSONArray expected = routes();
            JSONArray items = named(given, PROBE).getJSONArray("items");
            assertTrue("speed".equals(items.getJSONObject(1).getString("typ")) && "dir".equals(items.getJSONObject(2).getString("typ")),
                "the probe route of the fixture: " + items);
            cases.get(c)[0].accept(items);
            cases.get(c)[1].accept(named(expected, PROBE).getJSONArray("items"));

            List<RouteCommand> commands;

            try (StationRig rig = rig(ROUTES, expected))
            {
                assertTrue(rig.model.syncWithCS2() >= 0);
                commands = new ArrayList<>(rig.model.getRoute(PROBE).getRoute());
            }

            try (StationRig rig = rig(ROUTES, given))
            {
                assertTrue(rig.model.syncWithCS2() >= 0);
                MarklinRoute route = rig.model.getRoute(PROBE);
                assertNotNull(route, "the route is imported, case " + c);
                assertEquals(new ArrayList<>(route.getRoute()), commands, "with the items it can read, case " + c);
                assertFalse(rig.model.wasLastSyncPartial(), "and nothing is said of it at start-up, case " + c);
            }
        }
    }

    /**
     * The control: a route naming a locomotive that is not in the list, while another record has no name and so may be
     * it, is held back, and the start-up still warns of it.
     *
     * MUTATION: throw a plain JSONException for a route held back in CS2File.parseRoutesCS3, and the start-up is silent.
     */
    @Test
    public void testARouteHeldBackIsStillWarnedOf() throws Exception
    {
        JSONArray locs = locomotives();
        named(locs, "065 001-0 DB").remove("name");

        for (int i = 0; i < locs.length(); i++)
        {
            if ("02#200314-1#20DDR".equals(locs.getJSONObject(i).optString("internname"))) locs.remove(i--);
        }

        try (StationRig rig = rig(LOCOS, locs))
        {
            assertTrue(rig.model.syncWithCS2() >= 0);
            assertNull(rig.model.getRoute(PROBE), "the route naming the locomotive that is gone is held back");
            assertTrue(rig.model.wasLastSyncPartial(), "and the start-up warns of it");
        }
    }

    @SuppressWarnings("unchecked")
    private static Consumer<JSONArray>[] pair(Consumer<JSONArray> given, Consumer<JSONArray> expected)
    {
        return new Consumer[] {given, expected};
    }

    /**
     * A locomotive with a function entry missing `typ2` is skipped with its line in the log, at every start-up, and
     * the start-up does not warn of it.
     *
     * MUTATION: count every skipped locomotive in CS2File.getUnreadableRecords, and the start-up warns.
     */
    @Test
    public void testARecordSkippedAsBeforeRaisesNoWarning() throws Exception
    {
        JSONArray locs = locomotives();
        named(locs, "1047 005-2").getJSONArray("funktionen").getJSONObject(0).remove("typ2");

        try (StationRig rig = rig(LOCOS, locs))
        {
            for (int start = 1; start <= 2; start++)
            {
                rig.takeLog();
                assertTrue(rig.model.syncWithCS2() >= 0);
                assertNull(rig.model.getLocByName("1047 005-2"), "the locomotive is skipped, start " + start);
                assertEquals(rig.takeLog().stream().filter(l -> l.contains("could not be parsed")).count(), 1,
                    "with its line in the log, start " + start);
                assertFalse(rig.model.wasLastSyncPartial(), "and no warning, start " + start);
            }
        }
    }
}
