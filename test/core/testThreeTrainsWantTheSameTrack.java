package core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import support.Reflect;

/**
 * Three trains ready to go and one piece of track they all want (OB-455): the dispatcher lets one hold it at a time,
 * the other two wait rather than being refused for good, each gets through in turn, and every claim is given back.
 *
 * Two railways of this class's own, run in simulation with the real dispatcher: a single-track section that three
 * stations feed and three stations leave, and a crossing where three one-edge roads are each other's lock edges and
 * share no edge at all.  Polled every few milliseconds; `support.TrackWatch` makes the pair check.
 */
public class testThreeTrainsWantTheSameTrack
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    /** Clear of the other suites' feedback ranges and locomotive addresses. */
    private static final int S88_BASE = 9550;
    private static final int ADDRESS_BASE = 4550;

    private static final String[] TRAINS = {"OB-455 one", "OB-455 two", "OB-455 three"};

    private static final long RUN_LIMIT_MS = 90_000;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        sandbox = support.LayoutSandbox.open();

        model = support.TestStation.simulatedInDebug();
        model.setNetworkCommState(false);

        for (int i = 0; i < TRAINS.length; i++) model.newMM2Locomotive(TRAINS[i], ADDRESS_BASE + i);

        model.go();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception
    {
        try
        {
            if (model != null && model.hasAutoLayout()) model.getAutoLayout().stopLocomotives();

            if (model != null) for (String name : TRAINS) model.deleteLoc(name);
        }
        finally
        {
            if (sandbox != null) sandbox.close();
        }
    }

    /**
     * Three stations feed one edge and three stations leave it.  Whoever sets out first holds the edge to the far
     * station; the others stand where they are until it is given back.
     *
     * MUTATION: skip the occupied check in `Layout.isPathClear` (the `e.isOccupied(loc)` test) and a second train is
     * sent over the edge while the first holds it: the pair check and the one-claim floor fail.
     */
    @Test(timeOut = 150_000)
    public void testThreeTrainsShareASingleTrackOneAtATime() throws Exception
    {
        runThreeTrains(singleTrack(), "Join", "Mid");
    }

    /**
     * Three roads that share no edge, each listing the other two as lock edges (a crossing): a train on one holds the
     * other two clear.
     *
     * MUTATION: stop `Edge.setOccupied` claiming the lock edges AND skip the `e2.isRunOver` loop in `Layout.isPathClear`
     * (two guards, each covers the other; either alone stays green) and a second train takes a road the first has
     * locked: the pair check fails.
     */
    @Test(timeOut = 150_000)
    public void testThreeTrainsAtACrossingGoOneAtATime() throws Exception
    {
        runThreeTrains(crossing(), null, null);
    }

    /**
     * Loads the railway, starts the trains together and watches until all three have arrived.  Edge `shared` (from,
     * to) is the one every journey crosses; null when the claim is about lock edges alone.
     */
    private static void runThreeTrains(String json, String sharedFrom, String sharedTo) throws Exception
    {
        model.parseAuto(json);

        Layout layout = model.getAutoLayout();

        assertTrue(layout != null && layout.isValid(), "precondition: the railway does not build: " + Layout.getLastError());

        Edge shared = sharedFrom == null ? null : layout.getEdge(sharedFrom, sharedTo);

        if (sharedFrom != null) assertNotNull(shared, "precondition: no edge from " + sharedFrom + " to " + sharedTo);

        Map<String, AtomicInteger> arrivals = new HashMap<>();
        List<Locomotive> trains = new ArrayList<>();

        for (String name : TRAINS)
        {
            arrivals.put(name, new AtomicInteger());

            Locomotive loc = model.getLocByName(name);

            loc.setCallback(Layout.CB_ROUTE_END, (l) -> arrivals.get(l.getName()).incrementAndGet());
            trains.add(loc);
        }

        layout.runLocomotives();

        support.TrackWatch watch = new support.TrackWatch(layout);

        int pollsWithAWaiter = 0;
        int mostClaimsOnOneEdge = 0;
        Map<Locomotive, Boolean> sawHolding = new HashMap<>();

        long deadline = System.currentTimeMillis() + RUN_LIMIT_MS;

        try
        {
            while (System.currentTimeMillis() < deadline && !allArrived(arrivals))
            {
                watch.poll();

                for (Edge e : layout.getEdges()) mostClaimsOnOneEdge = Math.max(mostClaimsOnOneEdge, claimsOn(e));

                boolean someoneOut = false;

                synchronized (layout.getActiveLocomotives())
                {
                    for (Map.Entry<Locomotive, List<Edge>> out : layout.getActiveLocomotives().entrySet())
                    {
                        if (out.getValue() == null || out.getValue().isEmpty()) continue;

                        someoneOut = true;

                        sawHolding.put(out.getKey(), true);
                    }
                }

                if (someoneOut)
                {
                    for (Locomotive loc : trains)
                    {
                        Point at = layout.getLocomotiveLocation(loc);

                        if (at != null && at.getName().startsWith("Start") && !layout.isAlreadyUnderway(loc))
                        {
                            pollsWithAWaiter++;

                            break;
                        }
                    }
                }

                Thread.sleep(5);
            }
        }
        finally
        {
            layout.stopLocomotives();
        }

        long windDown = System.currentTimeMillis() + 5000;

        while (!layout.getActiveLocomotives().isEmpty() && System.currentTimeMillis() < windDown) Thread.sleep(50);

        for (String name : TRAINS)
        {
            assertEquals(arrivals.get(name).get(), 1, name + " should have arrived exactly once, but arrived "
                + arrivals.get(name).get() + " times: one that waits is not refused for good, and one that goes twice"
                + " was sent twice");

            assertTrue(sawHolding.containsKey(model.getLocByName(name)),
                name + " never held a path, so it did not take its turn");
        }

        assertTrue(pollsWithAWaiter > 0, "no poll saw a train hold its road while another still stood at its start,"
            + " so the three never wanted the same track at once and this proves nothing about contention");

        assertTrue(mostClaimsOnOneEdge == 1, "some edge was claimed " + mostClaimsOnOneEdge + " times at once: two"
            + " trains held (or locked) the same track");

        Map<String, String> finishedAt = new HashMap<>();

        for (Locomotive loc : trains)
        {
            Point at = layout.getLocomotiveLocation(loc);

            assertNotNull(at, loc.getName() + " is on no square after its run");

            assertTrue(at.getName().startsWith("End"), loc.getName() + " stopped at " + at.getName()
                + ", not at one of the three stations the run ends in");

            assertEquals(finishedAt.put(at.getName(), loc.getName()), null,
                at.getName() + " holds two trains: " + finishedAt);
        }

        for (Edge e : layout.getEdges())
        {
            assertEquals(claimsOn(e), 0, "the claim on " + e.getName() + " was not given back once the trains arrived");

            assertEquals(Reflect.get(Edge.class, e, "runOver"), 0, "the run-over count on " + e.getName()
                + " was not given back once the trains arrived");
        }
    }

    private static boolean allArrived(Map<String, AtomicInteger> arrivals)
    {
        for (AtomicInteger n : arrivals.values()) if (n.get() < 1) return false;

        return true;
    }

    private static int claimsOn(Edge e) throws Exception
    {
        return (Integer) Reflect.get(Edge.class, e, "occupancy");
    }

    /** Start 1-3 feed Join, one edge Join to Mid, and Mid leads on to End 1-3; each Start holds one train. */
    private static String singleTrack() throws Exception
    {
        JSONArray points = new JSONArray();
        JSONArray edges = new JSONArray();

        for (int i = 1; i <= 3; i++)
        {
            points.put(station("Start " + i, S88_BASE + i, TRAINS[i - 1]));
            points.put(station("End " + i, S88_BASE + 10 + i, null));

            edges.put(edge("Start " + i, "Join", null));
            edges.put(edge("Mid", "End " + i, null));
        }

        points.put(new JSONObject().put("name", "Join").put("station", false).put("x", 300).put("y", 200));
        points.put(new JSONObject().put("name", "Mid").put("station", false).put("x", 400).put("y", 200));

        edges.put(edge("Join", "Mid", null));

        return railway(points, edges);
    }

    /** Start i runs to End i on its own edge; the three edges list each other as lock edges. */
    private static String crossing() throws Exception
    {
        JSONArray points = new JSONArray();
        JSONArray edges = new JSONArray();

        for (int i = 1; i <= 3; i++)
        {
            points.put(station("Start " + i, S88_BASE + 20 + i, TRAINS[i - 1]));
            points.put(station("End " + i, S88_BASE + 30 + i, null));

            JSONArray locks = new JSONArray();

            for (int j = 1; j <= 3; j++)
            {
                if (j != i) locks.put(new JSONObject().put("start", "Start " + j).put("end", "End " + j));
            }

            edges.put(edge("Start " + i, "End " + i, locks));
        }

        return railway(points, edges);
    }

    private static JSONObject station(String name, int s88, String train)
    {
        JSONObject p = new JSONObject().put("name", name).put("station", true).put("s88", s88)
            .put("maxTrainLength", 0).put("x", name.startsWith("Start") ? 100 : 500)
            .put("y", 100 * Integer.parseInt(name.substring(name.length() - 1)));

        if (train != null) p.put("loc", new JSONObject().put("name", train).put("speed", 35));

        return p;
    }

    private static JSONObject edge(String from, String to, JSONArray locks)
    {
        JSONObject e = new JSONObject().put("start", from).put("end", to).put("length", 0);

        if (locks != null) e.put("lockedges", locks);

        return e;
    }

    private static String railway(JSONArray points, JSONArray edges)
    {
        return new JSONObject().put("points", points).put("edges", edges).put("minDelay", 0).put("maxDelay", 1)
            .put("defaultLocSpeed", 35).put("atomicRoutes", false).put("timetable", new JSONArray())
            .put("simulate", true).toString();
    }
}
