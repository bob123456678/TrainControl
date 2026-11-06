package core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;

/**
 * An old setup needs no conversion (FR-111): on the frozen real railway, written before the new settings, every station
 * reads both boxes on, every out-of-service square both off, and a save writes no new key.  The rule takes away the
 * routes through the station and nothing else.  The side by side proof over every fixture is
 * `docs/tools/old-setups.sh`; this holds the part that needs no older build.
 *
 * @author Adam
 */
public class testAnOldSetupReadsAsItDid
{
    private static support.Scenario scenario;
    private static MarklinControlStation model;
    private static AutonomySession session;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        scenario = support.Scenario.open("live-snapshot");

        model = scenario.getModel();

        model.stop();

        session = scenario.getSession();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        if (scenario != null) scenario.close();
    }

    private static JSONObject pointsOfTheConfiguration()
    {
        return session.getStore().getConfiguration(session.getStore().getActiveConfiguration()).getJSONObject("points");
    }

    private static TileKey keyOf(String printed)
    {
        return org.traincontrol.automationui.AutonomyCompanionStore.parseTileKey(printed);
    }

    /**
     * Stored only when off: no station of an old setup reads stop-only, on the setup or on the railway it builds.
     *
     * MUTATION: read an absent key as off in `AutonomySession.isStopOnly` or `Layout.parseAuto` and this fails.
     */
    @Test
    public void testAnOldStationReadsBothOn() throws Exception
    {
        JSONObject points = pointsOfTheConfiguration();

        int stations = 0;

        for (String key : points.keySet())
        {
            TileKey square = keyOf(key);

            if (square == null || !session.getStore().isStation(square)) continue;

            stations++;

            assertFalse(session.isStopOnly(square), key + " reads stop-only in a setup written before there was such a thing");
        }

        assertTrue(stations > 0, "the fixture has no station with settings, so this says nothing");

        model.parseAuto(session.buildConfiguration());

        Layout railway = model.getAutoLayout();

        assertTrue(railway.isValid(), "the old setup did not build: " + Layout.getLastError());

        for (Point p : railway.getPoints())
        {
            assertFalse(p.isStopOnly(), p.getName() + " built stop-only from an old setup");
        }
    }

    /**
     * An old out-of-service square is both boxes off, as it stands: it is not stop-only, and nothing can stop or pass.
     *
     * MUTATION: drop the inactive intermediate rule from `Layout.isPathClear` and this fails.
     */
    @Test
    public void testAnOldOutOfServiceSquareReadsBothOff() throws Exception
    {
        JSONObject points = pointsOfTheConfiguration();

        List<TileKey> shut = new ArrayList<>();

        for (String key : points.keySet())
        {
            if (Boolean.FALSE.equals(points.getJSONObject(key).opt("active")) && keyOf(key) != null) shut.add(keyOf(key));
        }

        assertFalse(shut.isEmpty(), "the frozen railway has no square out of service, so this says nothing");

        for (TileKey square : shut)
        {
            assertFalse(session.isStopOnly(square), square + " is out of service and reads stop-only");

            assertNull(session.getPointProperty(square, Point.RUN_THROUGH), "an old square gained a key on load");
        }

        // And it means what it did: `active: false` on a through station, as an old file has it, stops a route through it
        model.parseAuto(session.buildConfiguration());

        Layout open = model.getAutoLayout();

        Point through = busiestThroughStation(open);

        List<String> route = new ArrayList<>();

        for (Edge e : routeThrough(open, through)) route.add(e.getName());

        TileKey station = keyOf(through.getSquare());

        session.setPointProperty(station, "active", Boolean.FALSE);

        try
        {
            model.parseAuto(session.buildConfiguration());

            Layout shutOne = model.getAutoLayout();

            // Standing trains would refuse it first
            for (Point p : shutOne.getPoints()) p.setLocomotive(null);

            List<Edge> edges = new ArrayList<>();

            for (String name : route) edges.add(shutOne.getEdge(name));

            assertFalse(shutOne.isPathClear(edges, model.getLocomotives().get(0), false),
                "a route ran through a square out of service: " + edges);

            assertTrue(Layout.getLastError().startsWith(
                org.traincontrol.util.I18n.f("autolayout.errorInactiveIntermediatePoint", "").trim()),
                "refused, but not for being out of service: " + Layout.getLastError());

            assertFalse(session.isStopOnly(station), "a square out of service reads stop-only");
        }
        finally
        {
            session.setPointProperty(station, "active", null);
        }
    }

    /**
     * A save of an old setup writes none of the new keys.  Byte for byte against an older build is the script's.
     *
     * MUTATION: write `RUN_THROUGH` always in `Point.toJSON` and this fails.
     */
    @Test
    public void testASaveWritesNoNewKey() throws Exception
    {
        session.save();

        model.parseAuto(session.buildConfiguration());

        session.captureFromLayout(model.getAutoLayout().toJSON());

        session.save();

        String railway = model.getAutoLayout().toJSON();

        assertFalse(railway.contains(Point.RUN_THROUGH), "the railway's own file gained the key");

        File[] files = new File(scenario.getFolder(), "config/autonomy").listFiles();

        assertNotNull(files, "the setup was not written");

        for (File file : files)
        {
            if (!file.isFile()) continue;

            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            assertFalse(text.contains(Point.RUN_THROUGH), file.getName() + " gained the stop-only key");

            assertFalse(text.contains(AutonomyBuilder.REMEMBERED_LOCS), file.getName() + " gained the list key");
        }
    }

    /**
     * The rule takes the routes through the station and nothing else, on the real railway: no route passes it now, and
     * wherever a train could get to without passing it, it still can.  By pair, not by route: the search passes over a
     * route once another has left a square under the same settings, so which alternatives it gives shifts.
     *
     * MUTATION: refuse a route leaving a stop-only station too in `Layout.bfsBySettings` and this fails.
     */
    @Test
    public void testTheRuleTakesAwayOnlyTheRoutesThroughIt() throws Exception
    {
        model.parseAuto(session.buildConfiguration());

        Layout railway = model.getAutoLayout();

        Point through = busiestThroughStation(railway);

        Set<String> before = routes(railway);

        for (Point p : railway.getPoints())
        {
            if (p.isSamePlaceAs(through)) p.setStopOnly(true);
        }

        Set<String> after = routes(railway);

        int taken = 0;

        Set<String> pairsReachedAfter = new HashSet<>();

        for (String route : after)
        {
            assertFalse(route.contains(" " + through.getSquare() + " "), "a route still runs through the station: " + route);

            pairsReachedAfter.add(route.substring(0, route.indexOf(" via ")));
        }

        for (String route : before)
        {
            if (route.contains(" " + through.getSquare() + " "))
            {
                taken++;

                continue;
            }

            assertTrue(pairsReachedAfter.contains(route.substring(0, route.indexOf(" via "))),
                "somewhere reached without passing the station can no longer be reached: " + route);
        }

        assertTrue(taken > 0, "no route passed " + through + ", so this says nothing");
    }

    private static List<Point> stationsOf(Layout railway)
    {
        List<Point> stations = new ArrayList<>();

        for (Point p : railway.getPoints()) if (p.isDestination()) stations.add(p);

        return stations;
    }

    /** Every route the search gives between stations, up to eight a pair, as the squares it passes between its ends. */
    private static Set<String> routes(Layout railway) throws Exception
    {
        Set<String> out = new HashSet<>();

        for (Point from : stationsOf(railway))
        {
            for (Point to : stationsOf(railway))
            {
                if (from == to) continue;

                List<List<Edge>> seen = new ArrayList<>();

                for (int i = 0; i < 8; i++)
                {
                    List<Edge> path = railway.bfs(from, to, seen);

                    if (path == null) break;

                    seen.add(path);

                    StringBuilder route = new StringBuilder(from.getName() + " > " + to.getName() + " via ");

                    for (int at = 0; at + 1 < path.size(); at++)
                    {
                        route.append(' ').append(path.get(at).getEnd().getSquare()).append(' ');
                    }

                    out.add(route.toString());
                }
            }
        }

        return out;
    }

    /** A route through this station that the search gives. */
    private static List<Edge> routeThrough(Layout railway, Point through) throws Exception
    {
        for (Point from : stationsOf(railway))
        {
            for (Point to : stationsOf(railway))
            {
                List<Edge> path = from == to ? null : railway.bfs(from, to, null);

                for (int at = 0; path != null && at + 1 < path.size(); at++)
                {
                    if (through.isSamePlaceAs(path.get(at).getEnd())) return path;
                }
            }
        }

        throw new AssertionError("no route found through " + through);
    }

    /** The station most shortest routes pass through, so taking it away is a real change. */
    private static Point busiestThroughStation(Layout railway) throws Exception
    {
        java.util.Map<Point, Integer> passes = new java.util.HashMap<>();

        for (Point from : stationsOf(railway))
        {
            for (Point to : stationsOf(railway))
            {
                List<Edge> path = from == to ? null : railway.bfs(from, to, null);

                if (path == null) continue;

                for (int at = 0; at + 1 < path.size(); at++)
                {
                    Point midway = path.get(at).getEnd();

                    if (midway.isDestination()) passes.merge(midway, 1, Integer::sum);
                }
            }
        }

        assertFalse(passes.isEmpty(), "no route on the real railway passes a station, so this says nothing");

        return java.util.Collections.max(passes.entrySet(), java.util.Map.Entry.comparingByValue()).getKey();
    }
}
