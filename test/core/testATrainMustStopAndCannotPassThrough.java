package core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.util.I18n;

/**
 * "Trains must stop and can't pass through" (FR-111), on its own, through the real doors with simulated trains running
 * to the end.  `single-switch`: `Approach` is the through station between `WestEnd` and the switch to `MainPlatform` and
 * `BranchPlatform`, so nothing reaches the far side but through it.  Every trip here is watched by `Watched`, which
 * records each square that train ever held, reservations included.
 *
 * @author Adam
 */
public class testATrainMustStopAndCannotPassThrough
{
    private static support.Scenario scenario;
    private static MarklinControlStation model;
    private static AutonomySession session;
    private static List<LayoutDiagram> pages;

    private static Locomotive train;

    private static final List<String> LOGGED = Collections.synchronizedList(new ArrayList<String>());
    private static java.util.logging.Handler tap;

    private static TileKey approach;
    private static final RouteId THE_ROAD = new RouteId(0, 0);

    private static JSONObject pristine;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        scenario = support.Scenario.openSimulated("single-switch");

        model = scenario.getModel();

        model.stop();

        pages = scenario.getPages();

        session = scenario.getSession();

        approach = new TileKey(pages.get(0).getName(), 3, 3);

        train = scenario.newTrain("Stop-only train", 2201);

        pristine = session.snapshotSetup();

        tap = support.LogTap.messagesInto(LOGGED);

        java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).addHandler(tap);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (tap != null) java.util.logging.Logger.getLogger(MarklinControlStation.class.getName()).removeHandler(tap);

        if (scenario != null) scenario.close();
    }

    @BeforeMethod
    public void aFreshSetup() throws Exception
    {
        session.restoreSetup(new JSONObject(pristine.toString()));

        LOGGED.clear();
    }

    /** What the application does after an edit; simulated and unpaced. */
    private static Layout build() throws Exception
    {
        Layout railway = scenario.buildSimulated();

        assertTrue(railway.isValid(), "the setup did not build: " + Layout.getLastError());

        return railway;
    }

    private static Layout stopOnly() throws Exception
    {
        session.setStopsAndRunsThrough(approach, true, false);

        assertTrue(session.isStopOnly(approach), "the setter did not make the station stop-only");

        return build();
    }

    private static List<Edge> theWayThrough(Layout railway)
    {
        return Arrays.asList(railway.getEdge(Edge.getEdgeName(railway.getPoint("WestEnd"), eastbound(railway))),
            railway.getEdge(Edge.getEdgeName(eastbound(railway), railway.getPoint("MainPlatform"))));
    }

    private static Layout trainAtWestEnd(Layout railway)
    {
        railway.getPoint("WestEnd").setLocomotive(train);

        return railway;
    }

    private static Point eastbound(Layout railway)
    {
        return copyArrivingFromTheWest(railway);
    }

    /** The copy of Approach a train from WestEnd arrives on, by the rail, not by a name a change could move. */
    private static Point copyArrivingFromTheWest(Layout railway)
    {
        for (Edge e : railway.getNeighbors(railway.getPoint("WestEnd")))
        {
            return e.getEnd();
        }

        throw new AssertionError("nothing leaves WestEnd");
    }

    private static String sentence(String station)
    {
        return I18n.f("autolayout.errorThroughStopOnlyStation", station);
    }

    /** Records every square a train holds while a trip runs, reservations included. */
    private static final class Watched implements AutoCloseable
    {
        private final Set<String> held = Collections.synchronizedSet(new HashSet<String>());
        private final Thread watcher;
        private volatile boolean watching = true;

        Watched(Layout railway, Locomotive loc)
        {
            watcher = new Thread(() ->
            {
                while (watching)
                {
                    for (Point p : railway.getPoints())
                    {
                        if (p.getCurrentLocomotive() == loc) held.add(p.getName());
                    }

                    try
                    {
                        Thread.sleep(1);
                    }
                    catch (InterruptedException e)
                    {
                        return;
                    }
                }
            }, "watching squares");

            watcher.setDaemon(true);
            watcher.start();
        }

        Set<String> squares()
        {
            return held;
        }

        @Override
        public void close() throws InterruptedException
        {
            watching = false;
            watcher.join();
        }
    }

    private static void waitFor(BooleanSupplier what, long ms, String failure) throws InterruptedException
    {
        long until = System.currentTimeMillis() + ms;

        while (!what.getAsBoolean())
        {
            assertTrue(System.currentTimeMillis() < until, failure);

            Thread.sleep(20);
        }
    }

    private static boolean sentArrives(Layout railway, String end) throws Exception
    {
        return railway.executePathByHand(sendTo(railway, train, end), train, 50, Layout.ALWAYS_REVERSE, -1);
    }

    private static List<Edge> sendTo(Layout railway, Locomotive loc, String end) throws Exception
    {
        Point from = railway.getLocomotiveLocation(loc);

        List<Edge> path = railway.bfs(from, railway.getPoint(end), null);

        assertNotNull(path, "no route from " + from + " to " + end);

        return path;
    }

    /**
     * The fixture, with the rule off: WestEnd reaches both platforms straight through Approach.  Without this every
     * refusal below could be the track's.
     *
     * MUTATION: read an absent key as off in `Layout.parseAuto` and this fails.
     */
    @Test
    public void testAnOrdinaryStationLetsTrainsThrough() throws Exception
    {
        Layout railway = build();

        assertFalse(session.isStopOnly(approach), "the fixture's station reads stop-only with nothing set");

        railway.getPoint("WestEnd").setLocomotive(train);

        assertEquals(support.Scenario.endsOffered(railway, train),
            Arrays.asList(copyArrivingFromTheWest(railway).getName(), "BranchPlatform", "MainPlatform").stream()
                .sorted().collect(java.util.stream.Collectors.toList()),
            "an ordinary through station does not let trains through on this fixture");

        assertEquals(sendTo(railway, train, "MainPlatform").size(), 2, "the route is not WestEnd, Approach, Main");
    }

    /**
     * The path search gives no route through it and still ends one there, and leaves one from it.
     *
     * MUTATION: take the `isStopOnly` line out of `Layout.bfsBySettings` and this fails.
     */
    @Test
    public void testThePathSearchFindsNoRouteThroughIt() throws Exception
    {
        Layout railway = stopOnly();

        Point west = railway.getPoint("WestEnd");
        Point main = railway.getPoint("MainPlatform");
        Point middle = copyArrivingFromTheWest(railway);

        assertNull(railway.bfs(west, main, null), "the search ran a route through a stop-only station");

        assertNotNull(railway.bfs(west, middle, null), "the search will not end a route at a stop-only station");

        assertNotNull(railway.bfs(middle, main, null), "the search will not start a route from a stop-only station");
    }

    /**
     * Every copy says so, a barred side's included, and a route written by hand is refused with the reason: `isPathClear`
     * asks of its own, as a route need not come from the search.
     *
     * MUTATION: take the rule out of `Layout.isPathClear` and this fails.
     */
    @Test
    public void testARouteThroughItIsNotClear() throws Exception
    {
        Layout railway = trainAtWestEnd(stopOnly());

        for (Point p : railway.getPoints())
        {
            assertEquals(p.isStopOnly(), p.getName().startsWith("Approach"),
                p.getName() + " is on the wrong side of the rule: every copy of the square, and nothing else");
        }

        assertFalse(railway.isPathClear(theWayThrough(railway), train, false),
            "a route through a stop-only station was reported clear");

        assertEquals(Layout.getLastError(), sentence("Approach"), "refused, but not for the reason");

        assertEquals(railway.stopOnlyStationRunThrough(theWayThrough(railway)).isStopOnly(), true,
            "the rule the matrix asks does not name the station");

        assertNull(railway.stopOnlyStationRunThrough(theWayThrough(railway).subList(0, 1)),
            "a route ending at the station was called running through it");

        assertNull(railway.stopOnlyStationRunThrough(theWayThrough(railway).subList(1, 2)),
            "a route leaving the station was called running through it");
    }

    /**
     * A trip sent by hand along the way through is refused, says why in the log, and nothing moves or is held.
     *
     * MUTATION: take the rule out of `Layout.isPathClear`, or say `errorPathOccupied` for it in
     * `Layout.configureAndLockPath`, and this fails.
     */
    @Test
    public void testAHandSentTripIsRefusedWithItsMessage() throws Exception
    {
        Layout railway = trainAtWestEnd(stopOnly());

        try (Watched watched = new Watched(railway, train))
        {
            assertFalse(railway.executePathByHand(theWayThrough(railway), train, 50, Layout.ALWAYS_REVERSE, -1),
                "a hand-sent trip ran through a stop-only station");

            assertEquals(railway.getLocomotiveLocation(train).getName(), "WestEnd", "the refused train moved");

            assertTrue(Collections.singleton("WestEnd").containsAll(watched.squares()),
                "the refused trip held squares it was refused: " + watched.squares());
        }

        assertTrue(LOGGED.contains(sentence("Approach")), "the log does not say why the trip was refused: " + LOGGED);

        assertFalse(LOGGED.contains(I18n.t("autolayout.errorPathOccupied")),
            "the log calls a standing rule busy track, which sends the operator to look for a train");

        // The doors a person uses offer only the station itself
        assertEquals(support.Scenario.endsOffered(railway, train), Arrays.asList(copyArrivingFromTheWest(railway).getName()),
            "the right-click menu's list offers somewhere beyond a stop-only station");
    }

    /**
     * An automatic run stops at the station, then a later trip goes on, and no trip it records ever passes the station.
     *
     * MUTATION: take the rule out of both `Layout.bfsBySettings` and `Layout.isPathClear` and this fails.
     */
    @Test
    public void testAnAutomaticRunStopsThereAndNeverPasses() throws Exception
    {
        Layout railway = trainAtWestEnd(stopOnly());

        railway.setTimetableCapture(true);

        List<TripSeen> trips;

        try (support.FastSimulation fast = support.FastSimulation.unpace(railway))
        {
            railway.setLocomotivesToRun(Collections.singletonList(train));

            railway.runLocomotives();

            waitFor(() -> railway.getTimetable().size() >= 6, 120000,
                "the run did not make six trips: " + railway.getTimetable());

            railway.stopLocomotives();

            waitFor(() -> !railway.isRunning(), 60000, "the run did not stop");

            trips = TripSeen.all(railway);
        }
        finally
        {
            railway.setTimetableCapture(false);
        }

        boolean wentBeyond = false;

        for (TripSeen trip : trips)
        {
            assertTrue(trip.throughTheStation == null, "a trip ran through the station: " + trip);

            if (trip.end.equals("MainPlatform") || trip.end.equals("BranchPlatform")) wentBeyond = true;
        }

        assertTrue(trips.get(0).end.startsWith("Approach"),
            "the first trip from WestEnd did not end at the station: " + trips);

        assertTrue(wentBeyond, "no later trip went on beyond the station, so the run was stuck there: " + trips);
    }

    /** One entry the run recorded, by the names of its squares. */
    private static final class TripSeen
    {
        final String end;
        final String throughTheStation;
        final String path;

        TripSeen(Layout railway, org.traincontrol.automation.TimetablePath entry)
        {
            List<Edge> edges = entry.getPath();

            this.end = edges.get(edges.size() - 1).getEnd().getName();
            this.path = edges.toString();

            String through = null;

            for (int i = 0; i + 1 < edges.size(); i++)
            {
                if (edges.get(i).getEnd().getName().startsWith("Approach")) through = edges.get(i).getEnd().getName();
            }

            this.throughTheStation = through;
        }

        static List<TripSeen> all(Layout railway)
        {
            List<TripSeen> out = new ArrayList<>();

            for (org.traincontrol.automation.TimetablePath entry : railway.getTimetable())
            {
                out.add(new TripSeen(railway, entry));
            }

            return out;
        }

        @Override
        public String toString()
        {
            return path;
        }
    }

    /**
     * The trip to the station holds nothing beyond it, ends there, and a later trip leaves onward in the same direction
     * with no turn round.  That is how it differs from Must, which is the control: there the same arrival turns the
     * train.
     *
     * MUTATION: make a stop-only Point a terminus in `Layout.parseAuto` and this fails.
     */
    @Test
    public void testALaterTripLeavesOnwardWithNoTurnRound() throws Exception
    {
        Layout railway = trainAtWestEnd(stopOnly());

        Locomotive.locDirection setOff = train.getDirection();

        String middle = copyArrivingFromTheWest(railway).getName();

        try (Watched watched = new Watched(railway, train))
        {
            assertTrue(sentArrives(railway, middle),
                "the trip to the station did not arrive");

            assertEquals(new java.util.TreeSet<>(watched.squares()),
                new java.util.TreeSet<>(Arrays.asList("WestEnd", middle)),
                "the first trip held a square beyond the station");
        }

        assertEquals(railway.getLocomotiveLocation(train).getName(), middle, "the train is not at the station");

        assertEquals(train.getDirection(), setOff, "arriving at a stop-only station turned the train round");

        assertEquals(support.Scenario.endsOffered(railway, train), Arrays.asList("BranchPlatform", "MainPlatform"),
            "a train at the station is not offered the way on");

        assertTrue(sentArrives(railway, "MainPlatform"),
            "the later trip did not arrive");

        assertEquals(railway.getLocomotiveLocation(train).getName(), "MainPlatform", "the train did not go on");

        // The control: Must turns a train at the same place, and offers no way on.
        session.setStopsAndRunsThrough(approach, true, true);
        session.setPointProperty(approach, org.traincontrol.automationui.AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);

        Layout must = trainAtWestEnd(build());

        Locomotive.locDirection before = train.getDirection();

        Point turning = must.getNeighbors(must.getPoint("WestEnd")).get(0).getEnd();

        assertTrue(turning.isTerminus(), "the control's station is not Must: " + turning);

        assertTrue(must.executePathByHand(must.bfs(must.getPoint("WestEnd"), turning, null), train, 50,
            Layout.ALWAYS_REVERSE, -1), "the control's trip did not arrive");

        assertNotEquals(train.getDirection(), before, "the control: Must did not turn the train");
    }

    /**
     * May depart off beyond the station leaves a train that came in there with no way on, and says so: the rule is not
     * a way round the arrows.
     *
     * MUTATION: make `AutonomySession.setDirection` do nothing and this fails.
     */
    @Test
    public void testMayDepartOffGivesNoWayOn() throws Exception
    {
        session.setStopsAndRunsThrough(approach, true, false);

        Direction onlyWest = directionThatClosesTheWayOn();

        session.setDirection(approach, THE_ROAD, onlyWest);

        Layout railway = trainAtWestEnd(build());

        Point middle = copyArrivingFromTheWest(railway);

        assertEquals(support.Scenario.endsOffered(railway, train), Arrays.asList(middle.getName()),
            "the station is not reached, or something past it is offered");

        assertTrue(sentArrives(railway, middle.getName()),
            "the trip to the station did not arrive");

        assertEquals(railway.getLocomotiveLocation(train).getName(), middle.getName(), "the train is not at the station");

        assertEquals(support.Scenario.endsOffered(railway, train), Collections.emptyList(),
            "May depart is off that way, and the train was offered somewhere");

        assertNotNull(railway.explainDestinations(train, true).get("MainPlatform"),
            "Why Not Moving? gives no reason for the way it may not leave");
    }

    /** The one-way setting that lets a train leave Approach westward only, found on the built railway. */
    private static Direction directionThatClosesTheWayOn() throws Exception
    {
        for (Direction candidate : Arrays.asList(Direction.TOWARD_A, Direction.TOWARD_B))
        {
            session.setDirection(approach, THE_ROAD, candidate);

            model.parseAuto(session.buildConfiguration());

            Layout railway = model.getAutoLayout();

            boolean goesEast = false;

            for (Point copy : railway.getPoints())
            {
                if (!copy.getName().startsWith("Approach")) continue;

                for (Edge e : railway.getNeighbors(copy))
                {
                    if (e.getEnd().getName().equals("MainPlatform")) goesEast = true;
                }
            }

            if (!goesEast) return candidate;
        }

        throw new AssertionError("neither direction closes the way on");
    }

    /**
     * Return Home does run through it, to the end, and the exemption goes when the trip does: the same train sent by hand
     * through the same station afterwards is refused.
     *
     * MUTATION: take `entry.setReturnHome(true)` out of `Layout.loadReturnToHomeTimetable` and this fails, as the run
     * refuses its own plan.  MUTATION: add the rule to `HomeStaging.firstClearRoute` and this fails, as no plan exists.
     */
    @Test
    public void testReturnHomeRunsThroughIt() throws Exception
    {
        session.setStopsAndRunsThrough(approach, true, false);

        session.setPointProperty(new TileKey(pages.get(0).getName(), 7, 3), "home", train.getName());

        Layout railway = trainAtWestEnd(build());

        HomeStaging.Plan plan = railway.loadReturnToHomeTimetable();

        assertTrue(plan.isPossible(), "no plan home through a stop-only station: " + plan.getOutcome());

        assertEquals(plan.getMoves().size(), 1, "the plan is not one trip: " + plan.getMoves());

        List<Edge> planned = plan.getMoves().get(0).getPath();

        assertNotNull(railway.stopOnlyStationRunThrough(planned), "the plan does not run through the station");

        try (Watched watched = new Watched(railway, train))
        {
            assertTrue(railway.executeTimetable(), "the plan was refused on the way: " + railway.whyTheTimetableStopped());

            waitFor(() -> !railway.isRunning(), 60000, "the plan did not finish");

            assertTrue(watched.squares().contains(copyArrivingFromTheWest(railway).getName()),
                "the train did not pass the station: " + watched.squares());
        }

        assertEquals(railway.getLocomotiveLocation(train).getName(), "MainPlatform", "the train did not get home");

        // And only Return Home.  The train goes back to the far side by hand, stopping, and is then sent through.
        railway.getPoint("MainPlatform").setLocomotive(null);
        railway.getPoint("WestEnd").setLocomotive(train);

        assertFalse(railway.executePathByHand(theWayThrough(railway), train, 50, Layout.ALWAYS_REVERSE, -1),
            "the exemption outlived Return Home: a hand-sent trip then ran through the station");
    }

    /**
     * The parity audit compares the planner with the runtime on the trips the planner plans.  Asked about Return Home's
     * trips the two agree, and they agree on a destination past the station, so the agreement is about something; a
     * rule added to either side alone shows as a disagreement.
     *
     * MUTATION: ask the public `getPossiblePaths` in `HomeStaging.auditAgainstRuntime`, or add the rule to
     * `HomeStaging.firstClearRoute`, and this fails.
     */
    @Test
    public void testTheParityAuditAgreesAboutTheSameTrips() throws Exception
    {
        Layout railway = trainAtWestEnd(stopOnly());

        HomeStaging staging = HomeStaging.snapshot(railway);

        LOGGED.clear();

        assertEquals(staging.auditAgainstRuntime(), 0, "the audit disagrees: " + LOGGED);

        // The other door still refuses, so the audit's agreement is not the rule having gone away
        assertFalse(support.Scenario.endsOffered(railway, train).contains("MainPlatform"), "the rule has gone from the menu's door");

        // And the planner itself reaches the far side, so the zero is about a station past the rule
        session.setPointProperty(new TileKey(pages.get(0).getName(), 7, 3), "home", train.getName());

        Layout homed = trainAtWestEnd(build());

        HomeStaging.Plan plan = homed.planReturnToHome();

        assertTrue(plan.isPossible(), "the planner does not itself reach the home past the station, so the audit's zero"
            + " above says nothing about it");
    }

    /**
     * Why Not Moving? says why, in both tiers, naming the station; and a train with nothing else to choose is told it
     * can reach no station rather than left looking able to start.
     *
     * MUTATION: take the stop-only sentence out of `Layout.firstClearOrWhyNot`, or the `isStopOnly` test out of
     * `Layout.canReachAnyDestination`, and this fails.
     */
    @Test
    public void testWhyNotMovingSaysWhy() throws Exception
    {
        Layout railway = trainAtWestEnd(stopOnly());

        String middle = copyArrivingFromTheWest(railway).getName();

        for (boolean byHand : new boolean[] {false, true})
        {
            Map<String, String> reasons = railway.explainDestinations(train, byHand);

            assertEquals(reasons.get("MainPlatform"), sentence("Approach"), "byHand=" + byHand + ": " + reasons);

            assertEquals(reasons.get("BranchPlatform"), sentence("Approach"), "byHand=" + byHand + ": " + reasons);

            assertNull(reasons.get(middle), "the station itself was given a reason, byHand=" + byHand + ": " + reasons);
        }

        assertNull(railway.explainCannotStart(train), "a train with a station to go to was told it cannot start");

        // The station not chosen by autonomy: nothing else to choose from here, and the walk stops at it
        session.setAutoDestination(approach, false);

        Layout berth = trainAtWestEnd(build());

        assertNotNull(berth.explainCannotStart(train),
            "a train whose only stations lie past a berth it may not pass was told nothing stops it starting");

        // The control: the same berth, no rule, and the train can start.
        session.setStopsAndRunsThrough(approach, true, true);

        Layout open = trainAtWestEnd(build());

        assertNull(open.explainCannotStart(train), "the control: the berth alone stops a train starting");
    }

    /**
     * The timetable: entries ending at the station and leaving it run to the end.  An entry recorded through the station
     * before it became stop-only stops the run at once and says why, with the train where it was and the entry kept.
     *
     * MUTATION: take the rule out of `Layout.whyEntryCannotRun` and this fails: the run then spins until its stuck timer
     * and says the track is busy.
     */
    @Test
    public void testTheTimetableKeepsTheRule() throws Exception
    {
        Layout plain = build();

        String middle = copyArrivingFromTheWest(plain).getName();

        // Two legs, as a recording that stopped would keep them
        session.setStopsAndRunsThrough(approach, true, false);

        session.setGlobal("timetable", timetableOf(
            new String[][] {{"WestEnd", middle}},
            new String[][] {{middle, "MainPlatform"}}));

        Layout railway = trainAtWestEnd(build());

        assertEquals(railway.getTimetable().size(), 2, "the timetable did not load");

        try (Watched watched = new Watched(railway, train))
        {
            assertTrue(railway.executeTimetable(), "the good timetable stopped: " + railway.whyTheTimetableStopped());

            waitFor(() -> !railway.isRunning(), 60000, "the good timetable did not finish");
        }

        assertEquals(railway.getLocomotiveLocation(train).getName(), "MainPlatform", "the good timetable did not get there");

        // One recorded straight through
        session.setGlobal("timetable", timetableOf(new String[][] {{"WestEnd", middle}, {middle, "MainPlatform"}}));

        Layout stale = trainAtWestEnd(build());

        assertEquals(stale.getTimetable().size(), 1, "the old entry did not load");

        long started = System.currentTimeMillis();

        try (Watched watched = new Watched(stale, train))
        {
            assertFalse(stale.executeTimetable(), "the run with an entry through the station reported success");

            waitFor(() -> !stale.isRunning(), 60000, "the run did not stop");

            assertTrue(Collections.singleton("WestEnd").containsAll(watched.squares()),
                "the refused entry held squares: " + watched.squares());
        }

        assertEquals(stale.whyTheTimetableStopped(), sentence("Approach"), "the run does not say why it stopped");

        assertTrue(System.currentTimeMillis() - started < 20000, "the run waited for a rule that will not change");

        assertEquals(stale.getLocomotiveLocation(train).getName(), "WestEnd", "the train moved");

        assertEquals(stale.getTimetable().size(), 1, "the entry was dropped");
    }

    private static JSONArray timetableOf(String[][]... entries)
    {
        JSONArray table = new JSONArray();

        for (String[][] legs : entries)
        {
            JSONArray path = new JSONArray();

            for (String[] leg : legs) path.put(new JSONObject().put("start", leg[0]).put("end", leg[1]));

            table.put(new JSONObject().put("loc", train.getName()).put("executionTime", 0L).put("secondsToNext", 0L)
                .put("path", path));
        }

        return table;
    }

    /**
     * Saved and reopened it stays stop-only, through every way a setup travels: the file, the railway's own file and a
     * capture from it, an undo, a square moved, an export and import.  Stored only when off, so putting it back leaves
     * no key.
     *
     * MUTATION: drop the key from `Point.toJSON`, or the read from `Layout.parseAuto`, and this fails.
     */
    @Test
    public void testItStaysStopOnlySavedAndReopened() throws Exception
    {
        session.setStopsAndRunsThrough(approach, true, false);

        session.save();

        AutonomySession reopened = new AutonomySession(scenario.getFolder());

        reopened.open(pages);

        assertTrue(reopened.isStopOnly(approach), "the setting did not survive a save and a reopen");

        model.parseAuto(reopened.buildConfiguration());

        Layout railway = model.getAutoLayout();

        assertTrue(copyArrivingFromTheWest(railway).isStopOnly(), "the reopened setup did not build stop-only");

        // The railway's own file reads back the same
        String file = railway.toJSON();

        model.parseAuto(file);

        assertTrue(copyArrivingFromTheWest(model.getAutoLayout()).isStopOnly(), "the railway's own file lost it");

        // The exit's capture from the railway leaves the choice alone
        reopened.captureFromLayout(file);

        assertTrue(reopened.isStopOnly(approach), "a capture from the running railway lost it");

        // An undo of the page puts back what was there
        Map<String, Object> snapshot = reopened.getStore().snapshotPage(pages.get(0).getName());

        reopened.setStopsAndRunsThrough(approach, true, true);

        assertFalse(reopened.isStopOnly(approach), "precondition: the station is still stop-only");

        assertFalse(reopened.getStore().getConfiguration(reopened.getStore().getActiveConfiguration())
            .getJSONObject("points").optJSONObject(approach.toString()).has(Point.RUN_THROUGH),
            "putting it back left a key: it is stored only when off");

        reopened.getStore().restorePage(pages.get(0).getName(), snapshot);

        assertTrue(reopened.isStopOnly(approach), "an undo did not put it back");

        // A square moved takes it along
        TileKey moved = new TileKey(pages.get(0).getName(), 3, 5);

        assertTrue(reopened.moveTile(approach, moved), "the square did not move");

        assertEquals(reopened.getPointProperty(moved, Point.RUN_THROUGH), Boolean.FALSE, "the setting stayed behind");

        assertTrue(reopened.moveTile(moved, approach), "the square did not move back");

        // An export carries it
        JSONObject bundle = reopened.getStore().exportBundle(reopened.getStore().getActiveConfiguration());

        assertEquals(reopened.importBundle("Imported", bundle) >= 0, true, "the import failed");

        assertEquals(reopened.getStore().getConfiguration("Imported").getJSONObject("points")
            .getJSONObject(approach.toString()).opt(Point.RUN_THROUGH), Boolean.FALSE, "the import lost it");
    }

    /**
     * It means a station: a square that is no station lets trains through whatever it remembers, and out of service
     * reads as both boxes off, as an old out-of-service square does.
     *
     * MUTATION: drop the `isStation` test beside `RUN_THROUGH` in `AutonomyBuilder` and this fails.
     */
    @Test
    public void testItMeansAStation() throws Exception
    {
        session.setPointProperty(approach, Point.RUN_THROUGH, Boolean.FALSE);

        session.setStation(approach, false);

        assertFalse(session.isStopOnly(approach), "a square that is no station reads stop-only");

        Layout railway = trainAtWestEnd(build());

        assertTrue(support.Scenario.endsOffered(railway, train).contains("MainPlatform"),
            "a square that is no station stopped trains passing");

        // Both off: Nothing Can Pass, what the square is left alone
        session.setStation(approach, true);
        session.setStopsAndRunsThrough(approach, false, false);

        assertFalse(session.isStopOnly(approach), "a square out of service reads stop-only");

        assertEquals(session.getPointProperty(approach, "active"), Boolean.FALSE, "both off is not Nothing Can Pass");

        assertTrue(session.getStore().isStation(approach), "both off changed what the square is");

        Layout shut = trainAtWestEnd(build());

        assertEquals(support.Scenario.endsOffered(shut, train), Collections.emptyList(), "a train was offered somewhere past a shut square");

        // Only Running through: a pass-through
        session.setStopsAndRunsThrough(approach, false, true);

        assertFalse(session.getStore().isStation(approach), "only Running through left a station");

        assertNull(session.getPointProperty(approach, "active"), "only Running through left the square shut");

        // Both on: a station again, as an old one is, with no key at all
        session.setStopsAndRunsThrough(approach, true, true);

        assertTrue(session.getStore().isStation(approach), "both on is not a station");

        assertNull(session.getPointProperty(approach, Point.RUN_THROUGH), "both on left a key");
    }

    /**
     * A train standing there when the setting changes stays where it stands, on the same copy, facing the same way, and
     * leaves onward; a train on the far side is refused the way past it.
     *
     * MUTATION: have `AutonomyBuilder` leave the locomotive off a stop-only square and this fails.
     */
    @Test
    public void testATrainStandingThereWhenItChanges() throws Exception
    {
        Layout before = build();

        String middle = copyArrivingFromTheWest(before).getName();

        before.getPoint(middle).setLocomotive(train);

        session.captureFromLayout(before.toJSON());

        session.setStopsAndRunsThrough(approach, true, false);

        Layout railway = build();

        assertEquals(railway.getLocomotiveLocation(model.getLocByName(train.getName())).getName(), middle,
            "the rebuild moved the train that was standing there");

        assertEquals(support.Scenario.endsOffered(railway, train), Arrays.asList("BranchPlatform", "MainPlatform"),
            "the train standing at the station may not leave onward");

        assertTrue(sentArrives(railway, "MainPlatform"),
            "the train standing there did not get away");

        assertEquals(railway.getLocomotiveLocation(train).getName(), "MainPlatform", "the train did not go on");
    }

    /**
     * A side trains may not arrive by keeps its copy, which is no destination, and it may not be run through either: from
     * there the train can neither stop nor pass, and says so.  The control: barred with the rule off, trains pass.
     *
     * MUTATION: emit `RUN_THROUGH` only on a copy where `stops` is true in `AutonomyBuilder` and this fails.
     */
    @Test
    public void testABarredSideCannotBeRunThroughEither() throws Exception
    {
        session.setBarredArrivals(approach, EnumSet.of(Side.W));

        Layout barred = trainAtWestEnd(build());

        assertFalse(copyArrivingFromTheWest(barred).isDestination(), "the fixture's barred copy still stops trains");

        assertTrue(support.Scenario.endsOffered(barred, train).contains("MainPlatform"), "the control: a barred side stops trains passing");

        session.setPointProperty(approach, Point.RUN_THROUGH, Boolean.FALSE);

        Layout railway = trainAtWestEnd(build());

        assertTrue(copyArrivingFromTheWest(railway).isStopOnly(), "a barred side's copy does not carry the rule");

        assertEquals(support.Scenario.endsOffered(railway, train), Collections.emptyList(), "a train passed by the barred side");

        assertEquals(railway.explainDestinations(train, true).get("MainPlatform"), sentence("Approach"),
            "Why Not Moving? does not say why");
    }

    /**
     * On a square trains may also turn at, every copy carries the rule: nothing passes, and turning stays a choice.
     *
     * MUTATION: emit `RUN_THROUGH` on one copy only in `AutonomyBuilder` and this fails.
     */
    @Test
    public void testAMayTurnSquareIsStopOnlyOnEveryCopy() throws Exception
    {
        session.setPointFlag(approach, org.traincontrol.automationui.AutonomyBuilder.CAN_REVERSE, true);

        session.setStopsAndRunsThrough(approach, true, false);

        Layout railway = build();

        int copies = 0;

        for (Point p : railway.getPoints())
        {
            if (!p.getName().startsWith("Approach")) continue;

            copies++;

            assertTrue(p.isStopOnly(), p.getName() + " does not carry the rule");
        }

        assertTrue(copies > 2, "the may-turn square did not split, so this says nothing: " + copies);

        railway.getPoint("WestEnd").setLocomotive(train);

        assertFalse(support.Scenario.endsOffered(railway, train).contains("MainPlatform"), "a train passed a may-turn stop-only station");
    }

    /**
     * The fixture older builds are tried on is what this build really writes for these choices, so it cannot go stale
     * ("saved-by-3-1-0", `core.testASetupFromANewerBuildOpens`).
     *
     * MUTATION: rename `Point.RUN_THROUGH` or `AutonomyBuilder.REMEMBERED_LOCS` and this fails.
     */
    @Test
    public void testTheFixtureForOlderBuildsIsWhatThisBuildSaves() throws Exception
    {
        session.setStopsAndRunsThrough(approach, true, false);

        TileKey main = new TileKey(pages.get(0).getName(), 7, 3);

        session.setTrainList(main, Collections.singletonList("Saved list train"));

        session.save();

        JSONObject written = new JSONObject(new String(java.nio.file.Files.readAllBytes(
            new java.io.File(scenario.getFolder(), "config/autonomy/configuration-Main.json").toPath()), "UTF-8"));

        JSONObject fixture = new JSONObject(new String(java.nio.file.Files.readAllBytes(
            new java.io.File(support.Scenario.folderFor("saved-by-3-1-0"),
                "config/autonomy/configuration-Main.json").toPath()), "UTF-8"));

        assertEquals(support.SetupDump.canonical(written.getJSONObject("points")),
            support.SetupDump.canonical(fixture.getJSONObject("points")),
            "this build no longer writes what the older build was tried on");
    }

    private static Map<TileKey, Set<TileKey>> reach()
    {
        return org.traincontrol.automationui.BuiltReach.of(new JSONObject(session.buildConfiguration()),
            session.builder(null).tilesByName()).bySquare();
    }

    /**
     * The editor's reach reads the build: a station is reached and nothing past it, so a check does not say a train can
     * be sent somewhere it can't.
     *
     * MUTATION: expand past a stop-only copy in `BuiltReach.stationsReachedFrom` and this fails.
     */
    @Test
    public void testTheEditorsReachStopsThere() throws Exception
    {
        TileKey west = new TileKey(pages.get(0).getName(), 1, 3);
        TileKey main = new TileKey(pages.get(0).getName(), 7, 3);

        assertTrue(reach().get(west).contains(main),
            "the control: the fixture's WestEnd does not reach MainPlatform with the rule off");

        session.setStopsAndRunsThrough(approach, true, false);

        Set<TileKey> reached = reach().get(west);

        assertTrue(reached.contains(approach), "the station itself is no longer reached");

        assertFalse(reached.contains(main), "the editor says a train can be sent past a stop-only station");
    }
}
