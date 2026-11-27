package core;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.StandingRules;
import org.traincontrol.automationui.AutonomyBuilder;
import org.traincontrol.automationui.AutonomyChecks;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automationui.AutonomySession.WhichTrains;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Locomotive;
import static org.testng.Assert.*;

/**
 * Allowed trains per arrival side and per action (FR-260), on `single-switch`, with `Approach` set up as the user's
 * St31Apost: trains may turn there; from the west (the user's north) nobody stops and every train runs through; from
 * the east only Q stops, and only MX 26 runs through, so Q must turn.  `testPointAllowed` (test/unit) tests one cell alone.
 */
public class testTrainsBySide
{
    private static support.Scenario scenario;
    private static AutonomySession session;
    private static JSONObject pristine;
    private static TileKey approach, westEnd;
    private static Locomotive q, mx, plain;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        scenario = support.Scenario.openSimulated("single-switch");
        session = scenario.getSession();
        pristine = session.snapshotSetup();
        approach = scenario.tile(3, 3);
        westEnd = scenario.tile(1, 3);

        Locomotive[] made = new Locomotive[3];

        String[] names = {"Sides Q", "Sides MX 26", "Sides plain"};

        for (int i = 0; i < made.length; i++) made[i] = scenario.newTrain(names[i], 3121 + i);

        q = made[0];
        mx = made[1];
        plain = made[2];
        plain.setReversible(false);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario != null) scenario.close();
    }

    /** St31Apost, from the pristine setup. */
    private static void st31()
    {
        session.restoreSetup(new JSONObject(pristine.toString()));

        session.setStopsAndRunsThrough(approach, true, true);
        session.setPointFlag(approach, AutonomyBuilder.CAN_REVERSE, true);
        session.setAutoDestination(approach, true);

        session.setWhichTrains(approach, Side.W, false, WhichTrains.WHITELIST, Collections.<String>emptyList());
        session.setWhichTrains(approach, Side.E, false, WhichTrains.WHITELIST, Collections.singletonList(q.getName()));
        session.setWhichTrains(approach, Side.E, true, WhichTrains.WHITELIST, Collections.singletonList(mx.getName()));
    }

    private static Layout build() throws Exception
    {
        Layout railway = scenario.build();

        assertTrue(railway.isValid(), "the setup does not build: " + Layout.getLastError());

        return railway;
    }

    private static List<Point> copies(Layout railway)
    {
        List<Point> out = new ArrayList<>();

        for (String name : session.getStationIndex().pointNamesAt(approach)) out.add(railway.getPoint(name));

        return out;
    }

    /** Approach's copy a train stands on facing this way: the plain one, or the one it turned on. */
    private static Point copy(Layout railway, String facing, boolean turned)
    {
        for (Point each : copies(railway))
        {
            if (facing.equals(each.getCopyFacing()) && turned == (each.isTerminus() || each.isReversing())) return each;
        }

        throw new AssertionError("Approach has no " + (turned ? "turning" : "plain") + " copy facing " + facing);
    }

    private static List<List<Edge>> pathsFrom(Layout railway, Locomotive loc, Point from)
    {
        for (Point each : railway.getPoints())
        {
            if (each.getCurrentLocomotive() != null) each.setLocomotive(null);
        }

        from.setLocomotive(loc);

        return railway.getPossiblePaths(loc, false);
    }

    /** Whether a trip from one station to another runs over Approach for this train. */
    private static boolean runsOver(Layout railway, Locomotive loc, String from, String to)
    {
        for (List<Edge> path : pathsFrom(railway, loc, railway.getPoint(from)))
        {
            if (path.get(path.size() - 1).getEnd() != railway.getPoint(to)) continue;

            for (Edge e : path.subList(0, path.size() - 1))
            {
                if (copies(railway).contains(e.getEnd())) return true;
            }
        }

        return false;
    }

    /** The copies of Approach a train may end a trip on, coming from a station. */
    private static List<String> endsFrom(Layout railway, Locomotive loc, String from)
    {
        List<String> out = new ArrayList<>();

        for (List<Edge> path : pathsFrom(railway, loc, railway.getPoint(from)))
        {
            Point end = path.get(path.size() - 1).getEnd();

            if (copies(railway).contains(end) && railway.isOfferableToOperator(end, loc)) out.add(end.getName());
        }

        return out;
    }

    /**
     * The user's rule, end to end: west to east every train runs over Approach and none stops there; east to west only
     * MX 26 runs over; Q coming from the east may stop only on the copy it turns on, and standing on the plain copy it
     * may not carry on west (stopping, then going on, would be running through in two steps).
     *
     * MUTATION: put back `e.getStart().excludes(loc) && !isAStationSquare(e.getStart())` in `Layout.isPathClear` and Q
     * runs east to west; drop the through cell from `Point.excludes` and Q is offered the plain copy; have
     * `Layout.setsOffFrom` answer `standing` and Q standing on the plain copy has no route; drop the through cell's test
     * from `Layout.turningCopyBarring` and MX 26 standing there is turned rather than let carry on west.
     */
    @Test
    public void testTheSt31RuleRunsEndToEnd() throws Exception
    {
        st31();

        Layout railway = build();

        for (Locomotive loc : Arrays.asList(q, mx, plain))
        {
            assertTrue(runsOver(railway, loc, "WestEnd", "MainPlatform"),
                loc.getName() + " cannot run west to east over Approach, which lets every train through that way");

            assertEquals(endsFrom(railway, loc, "WestEnd"), Collections.emptyList(),
                loc.getName() + " may stop at Approach coming from the west, where nobody may stop");
        }

        assertTrue(runsOver(railway, mx, "MainPlatform", "WestEnd"), "MX 26 cannot run east to west over Approach");
        assertFalse(runsOver(railway, q, "MainPlatform", "WestEnd"),
            "Q runs east to west over Approach, whose run-through list from the east names only MX 26");
        assertFalse(runsOver(railway, plain, "MainPlatform", "WestEnd"),
            "a train not on the run-through list from the east runs over Approach that way");

        assertEquals(endsFrom(railway, q, "MainPlatform"),
            Collections.singletonList(copy(railway, "E", true).getName()),
            "Q coming from the east may end its trip somewhere other than the copy it turns on");
        assertEquals(endsFrom(railway, mx, "MainPlatform"), Collections.emptyList(),
            "MX 26 may stop at Approach coming from the east, where only Q may stop");

        // Q standing on the plain copy may not carry on west, so it sets off turned, from the turning copy (D222)
        List<List<Edge>> turning = pathsFrom(railway, q, copy(railway, "W", false));

        assertFalse(turning.isEmpty(), "Q standing on the plain copy, which may turn there, has no route");

        for (List<Edge> path : turning)
        {
            assertEquals(path.get(0).getStart(), copy(railway, "E", true), "Q set off from elsewhere: " + path);
            assertNotEquals(path.get(path.size() - 1).getEnd(), railway.getPoint("WestEnd"), "Q carried on west");
        }

        assertEquals(pathsFrom(railway, plain, copy(railway, "W", false)), Collections.emptyList(),
            "a train that cannot reverse, standing on the plain copy, may carry on west or turn");
        assertTrue(pathsFrom(railway, mx, copy(railway, "W", false)).stream()
            .anyMatch(path -> path.get(path.size() - 1).getEnd() == railway.getPoint("WestEnd")),
            "MX 26 standing there may not carry on west, which it may run through");
        assertFalse(pathsFrom(railway, q, copy(railway, "E", true)).isEmpty(), "Q, turned there, may not leave east");

        Layout reloaded = Layout.fromJSON(railway.toJSON(), scenario.getModel());

        assertFalse(runsOver(reloaded, q, "MainPlatform", "WestEnd"), "an export lost the cells per side");
        assertTrue(runsOver(reloaded, mx, "MainPlatform", "WestEnd"), "an export lost the cells per side");
    }

    /**
     * On a square that is no station, the run-through cells give per-direction passing: every train west to east, only
     * MX 26 east to west.  Its stop cells mean nothing; cells that agree again are the one list again.
     *
     * MUTATION: have `Layout.barsPassing` read the one list on every point and this fails.
     */
    @Test
    public void testANonStationLetsTrainsThroughOneWayOnly() throws Exception
    {
        session.restoreSetup(new JSONObject(pristine.toString()));
        session.setStopsAndRunsThrough(approach, false, true);
        session.setWhichTrains(approach, Side.E, true, WhichTrains.WHITELIST, Collections.singletonList(mx.getName()));
        session.setWhichTrains(approach, Side.E, false, WhichTrains.WHITELIST, Collections.<String>emptyList());

        Layout railway = build();

        for (Locomotive loc : Arrays.asList(q, mx, plain))
        {
            assertTrue(runsOver(railway, loc, "WestEnd", "MainPlatform"), loc.getName() + " cannot pass west to east");
            assertEquals(runsOver(railway, loc, "MainPlatform", "WestEnd"), loc == mx,
                loc.getName() + " east to west, which only MX 26 may pass");
        }

        session.setWhichTrains(approach, Side.E, true, WhichTrains.ALL, null);

        assertFalse(session.hasTrainsBySide(approach), "run-through cells that agree were kept per side");
    }

    /**
     * An old setup's one list reads, routes and saves as before: a station's list bars stopping, every train still runs
     * through, and a cell set to what it already reads leaves the file byte for byte as it was.
     *
     * MUTATION: in `AutonomySession.setWhichTrains` by side, always write the grid and the file changes; read a
     * station's through cells as its list and the first read fails.
     */
    @Test
    public void testAnOldSetupReadsRoutesAndSavesAsBefore() throws Exception
    {
        session.restoreSetup(new JSONObject(pristine.toString()));
        session.setWhichTrains(approach, WhichTrains.WHITELIST, Collections.singletonList(q.getName()));
        session.save();

        byte[] before = savedSetup();

        assertEquals(session.getWhichTrains(approach, Side.E, true), WhichTrains.ALL,
            "a station's one list reads as keeping trains from running through");
        assertEquals(session.getTrainList(approach, Side.W, false), Collections.singletonList(q.getName()),
            "a station's one list does not read as who may stop");

        session.setWhichTrains(approach, Side.E, true, WhichTrains.WHITELIST, Collections.singletonList(mx.getName()));

        assertTrue(session.hasTrainsBySide(approach), "a cell unlike the others was not kept per side");

        session.setWhichTrains(approach, Side.E, true, WhichTrains.ALL, null);
        session.setWhichTrains(approach, Side.E, false, WhichTrains.WHITELIST, Collections.singletonList(q.getName()));

        assertFalse(session.hasTrainsBySide(approach), "cells that read as the one list were kept per side");

        session.save();

        assertEquals(new String(savedSetup(), "UTF-8"), new String(before, "UTF-8"),
            "a setup whose cells agree saved differently");

        String built = session.buildConfiguration();

        assertFalse(built.contains(Point.TRAINS_BY_SIDE) || built.contains(Point.ARRIVAL_SIDE),
            "a setup whose cells agree built with cells per side");

        Layout railway = build();

        assertTrue(runsOver(railway, mx, "MainPlatform", "WestEnd"),
            "MX 26 no longer runs through a station whose one list names only Q");
        assertEquals(endsFrom(railway, mx, "MainPlatform"), Collections.emptyList(), "MX 26 may stop there");
    }

    private static byte[] savedSetup() throws Exception
    {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        File[] files = new File(scenario.getFolder(), "config/autonomy").listFiles();

        Arrays.sort(files);

        for (File each : files)
        {
            if (each.isFile()) out.write(Files.readAllBytes(each.toPath()));
        }

        return out.toByteArray();
    }

    /**
     * Return Home keeps to the cells: Q, homed north of Approach, has no way home over it from the east, nor from its
     * plain copy, while MX 26 has.
     *
     * MUTATION: put back `!p.excludes(loc) || this.layout.isAStationSquare(p)` in `HomeStaging.canEnter` and Q is
     * planned over Approach; drop the origin's check in `HomeStaging` and Q is planned on from the plain copy.
     */
    @Test
    public void testReturnHomeKeepsToTheCells() throws Exception
    {
        for (Locomotive loc : Arrays.asList(q, mx))
        {
            st31();
            session.setPointProperty(westEnd, "home", loc.getName());

            Layout railway = build();

            pathsFrom(railway, loc, railway.getPoint("MainPlatform"));

            HomeStaging.Plan plan = railway.planReturnToHome();

            assertEquals(plan.getOutcome() == HomeStaging.Outcome.READY, loc == mx,
                loc.getName() + " going home over Approach from the east: " + plan.getOutcome() + plan.getReasons());

            pathsFrom(railway, loc, copy(railway, "W", false));

            plan = railway.planReturnToHome();

            assertEquals(plan.getOutcome() == HomeStaging.Outcome.READY, loc == mx,
                loc.getName() + " going home on from the plain copy: " + plan.getOutcome() + plan.getReasons());
        }

        // Homed east, a train on the plain copy goes home by turning if it may turn, as getPossiblePaths sets off
        for (Locomotive loc : Arrays.asList(q, plain))
        {
            st31();
            session.setPointProperty(scenario.tile(7, 3), "home", loc.getName());

            Layout railway = build();

            pathsFrom(railway, loc, copy(railway, "W", false));

            HomeStaging.Plan plan = railway.planReturnToHome();

            assertEquals(plan.getOutcome() == HomeStaging.Outcome.READY, loc == q,
                loc.getName() + " going home east from the plain copy: " + plan.getOutcome() + plan.getReasons());
        }
    }

    /**
     * A train standing on the plain copy that may not run through sets off by turning where it may (D222): Q's trip
     * leaves back east, turned; a train that cannot reverse stays, and why it does not move names the square.
     *
     * MUTATION: take `turnsToSetOff` out of `Layout.executePathInternal` and Q's trip is refused; take
     * `whyItCannotGoOnNorTurn` out of `LayoutExplanations` and the train that cannot reverse is given no reason; start
     * `pickPath` or `explainDestinations` where the train stands and they find Q no trip; drop the side from
     * `Layout.turningCopyBarring` and Q, barred both ways, sets off from the other side's turning copy one way.
     */
    @Test(timeOut = 120000)
    public void testATrainThatMayNotGoOnSetsOffByTurning() throws Exception
    {
        st31();

        Layout railway = scenario.buildSimulated();

        pathsFrom(railway, plain, copy(railway, "W", false));

        String why = org.traincontrol.util.I18n.f("autolayout.why.startMayNotRunThroughNorTurn", "Approach");

        assertEquals(railway.explainCannotStart(plain), why, "autonomy's reason the train that cannot reverse waits");
        assertEquals(railway.explainCannotStart(plain, true), why, "the reason by hand");

        List<Edge> trip = null;

        for (List<Edge> path : pathsFrom(railway, q, copy(railway, "W", false)))
        {
            if (path.get(path.size() - 1).getEnd() == railway.getPoint("MainPlatform")) trip = path;
        }

        assertNotNull(trip, "Q on the plain copy has no trip to MainPlatform");
        assertNull(railway.explainCannotStart(q), "Q, which may turn there, was given a reason not to start");
        assertNull(railway.explainDestinations(q, true).get("MainPlatform"), "why Q may not be sent to MainPlatform");
        assertNotNull(railway.pickPath(q), "autonomy finds Q no trip from the plain copy");

        boolean forward = q.goingForward();

        assertTrue(railway.executePath(trip, q, 30, null), "Q's trip from the plain copy was refused");
        assertEquals(q.goingForward(), forward, "Q was not turned both where it set off and at MainPlatform's buffer");
        assertEquals(railway.getPoint("MainPlatform").getCurrentLocomotive(), q, "Q did not arrive");

        // Barred both ways, it sets off from the turning copy for the side it came in by, each way
        session.setWhichTrains(approach, Side.W, true, WhichTrains.WHITELIST, Collections.singletonList(mx.getName()));

        Layout both = build();

        for (String facing : new String[] {"W", "E"})
        {
            List<List<Edge>> paths = pathsFrom(both, q, copy(both, facing, false));

            assertFalse(paths.isEmpty(), "Q facing " + facing + " on Approach, barred both ways, has no route");

            for (List<Edge> path : paths)
            {
                assertEquals(path.get(0).getStart(), copy(both, "W".equals(facing) ? "E" : "W", true),
                    "Q facing " + facing + " set off from elsewhere: " + path);
            }
        }

        // Nor is it told it reaches no station when only the way it may not go has none
        session.setAutoDestination(westEnd, false);

        Layout noWest = build();

        pathsFrom(noWest, q, copy(noWest, "W", false));

        assertNull(noWest.explainCannotStart(q), "Q, which sets off turned, was told it reaches no station");
    }

    /**
     * A set-off by turning refused by the lock (its end taken) or once its route is set (the power off) sends no turn:
     * Q stands back on the plain copy, unturned, its tail with it.  A trip from another station is refused as not where Q stands, Q neither turned
     * nor moved.
     *
     * MUTATION: send `switchDirection` before `configureAndLockPath` in `Layout.executePathInternal`, or drop the
     * lock's or the power's give-back `restand`, and Q is left turned or on the turning copy; drop `setsOffFrom(standing, loc) !=
     * start` from `Layout.turnsToSetOff` and the trip from MainPlatform runs.
     */
    @Test(timeOut = 120000)
    public void testARefusedSetOffSendsNoTurn() throws Exception
    {
        st31();

        Layout railway = scenario.buildSimulated();

        Point plainCopy = copy(railway, "W", false);

        List<Edge> elsewhere = pathsFrom(railway, q, railway.getPoint("MainPlatform")).get(0), trip = null;

        for (List<Edge> path : pathsFrom(railway, q, plainCopy))
        {
            if (path.get(path.size() - 1).getEnd() == railway.getPoint("MainPlatform")) trip = path;
        }

        assertNotNull(trip, "Q on the plain copy has no trip to MainPlatform");

        plainCopy.setArrivedFrom(plainCopy.getArrivalSide());

        java.lang.reflect.Method power = support.Reflect.method(org.traincontrol.marklin.MarklinControlStation.class,
            "setPowerState", boolean.class);

        List<String> said = Collections.synchronizedList(new ArrayList<String>());

        java.util.logging.Handler tap = support.LogTap.messagesInto(said);

        java.util.logging.Logger log =
            java.util.logging.Logger.getLogger(org.traincontrol.marklin.MarklinControlStation.class.getName());

        log.addHandler(tap);

        boolean forward = q.goingForward();

        try
        {
            assertFalse(railway.executePath(elsewhere, q, 30, null), "a trip from MainPlatform ran, Q on Approach");
            assertTrue(said.contains(org.traincontrol.util.I18n.f("autolayout.errorLocomotiveNotAtPathStart",
                q.getName())), "the trip from MainPlatform was not refused as not where Q stands: " + said);
            assertEquals(q.goingForward(), forward, "a trip from MainPlatform turned Q");
            assertEquals(railway.getLocomotiveLocation(q), plainCopy, "a trip from MainPlatform moved Q");

            // Refused by the lock (MainPlatform taken), then after it (the power off)
            for (String why : new String[] {"autolayout.errorPathOccupied", "autolayout.log.notSentPowerOff"})
            {
                railway.getPoint("MainPlatform").setLocomotive(why.contains("Occupied") ? mx : null);

                power.invoke(scenario.getModel(), why.contains("Occupied"));

                assertFalse(railway.executePath(trip, q, 30, null), "Q set off: " + why);
                assertTrue(said.contains(org.traincontrol.util.I18n.f(why, q.getName())), "not refused, " + why + said);
                assertEquals(q.goingForward(), forward, "a set-off refused turned Q: " + why);

                for (Point each : copies(railway))
                {
                    assertEquals(each.getCurrentLocomotive(), each == plainCopy ? q : null, "Q after " + why + each);
                }

                assertEquals(plainCopy.getArrivedFrom(), plainCopy.getArrivalSide(), "Q's tail was left, " + why);
            }
        }
        finally
        {
            power.invoke(scenario.getModel(), true);

            log.removeHandler(tap);
        }
    }

    /**
     * A square that is no station but may turn trains turns one its run-through cell from that side bars: a train
     * standing there sets off turned, even one that cannot reverse, and What's allowed here lets any train leave turned.
     *
     * MUTATION: drop `!isReversing` from `Point.barsPassingBySide` and the train cannot set off; have
     * `Point.allowedToPass` answer the through cell on a turning copy and the rules say Some trains.
     */
    @Test
    public void testANonStationThatMayTurnTurnsATrainItBarsFromRunningThrough() throws Exception
    {
        session.restoreSetup(new JSONObject(pristine.toString()));
        session.setStopsAndRunsThrough(approach, false, true);
        session.setPointFlag(approach, AutonomyBuilder.CAN_REVERSE, true);
        session.setWhichTrains(approach, Side.E, true, WhichTrains.WHITELIST, Collections.singletonList(mx.getName()));
        session.setWhichTrains(approach, Side.W, true, WhichTrains.WHITELIST, Arrays.asList(mx.getName(), plain.getName()));

        Layout railway = build();

        assertFalse(runsOver(railway, q, "MainPlatform", "WestEnd"), "Q runs through Approach from the east");

        // Standing there, even a train that cannot reverse sets off from the copy that turns every train
        List<List<Edge>> turning = pathsFrom(railway, plain, railway.getPoint("Approach (westbound)"));

        assertFalse(turning.isEmpty(), "a train standing on Approach from the east cannot turn there");

        for (List<Edge> path : turning)
        {
            assertEquals(path.get(0).getStart().getName(), "Approach (westbound, reverse)", "it set off: " + path);
        }

        // Sent out east by hand: a train that turned there leaves whatever the run-through cell from the east says
        StandingRules.Cell out = railway.standingRulesAt(session.getStationIndex().pointNamesAt(approach))
            .cells(StandingRules.Row.SENT_FROM, true).get("E");

        assertEquals(out.getAnswer(), StandingRules.Answer.YES, "sent out east: " + out.getWhy());
    }

    /**
     * What's allowed here reads each side: from the east, Some trains run through (MX 26) and Some stop (Q); from the
     * west, every train runs through and none stops.
     *
     * MUTATION: in `LayoutStandingRules.limited`, read the one list (`at.getExcludedLocs()`) again and this fails.
     */
    @Test
    public void testWhatIsAllowedHereReadsEachSide() throws Exception
    {
        st31();

        Layout railway = build();

        StandingRules rules = railway.standingRulesAt(session.getStationIndex().pointNamesAt(approach));

        for (boolean byHand : new boolean[] {true, false})
        {
            Map<String, StandingRules.Cell> through = rules.cells(StandingRules.Row.SENT_THROUGH, byHand);
            Map<String, StandingRules.Cell> here = rules.cells(StandingRules.Row.SENT_HERE, byHand);

            assertEquals(through.get("W").getAnswer(), StandingRules.Answer.YES, "from the west: " + through.get("W"));
            assertEquals(through.get("E").getAnswer(), StandingRules.Answer.SOME, "from the east: " + through.get("E"));
            assertTrue(through.get("E").getWhy().contains(mx.getName()), "the reason: " + through.get("E").getWhy());

            assertEquals(here.get("W").getAnswer(), StandingRules.Answer.NO, "from the west: " + here.get("W"));
            assertEquals(here.get("E").getAnswer(), StandingRules.Answer.SOME, "from the east: " + here.get("E"));
            assertTrue(here.get("E").getWhy().contains(q.getName()), "the reason: " + here.get("E").getWhy());
        }
    }

    /**
     * At a station that never turns trains, a train the stop cell names but the run-through cell bars may not be sent
     * there from that side: it could not leave.
     *
     * MUTATION: have `Point.allowedToStop` answer the stop cell alone and the rules say Q may be sent there.
     */
    @Test
    public void testWhatIsAllowedHereAtAStationThatNeverTurns() throws Exception
    {
        session.restoreSetup(new JSONObject(pristine.toString()));
        session.setStopsAndRunsThrough(approach, true, true);
        session.setAutoDestination(approach, true);
        session.setWhichTrains(approach, Side.E, false, WhichTrains.WHITELIST, Collections.singletonList(q.getName()));
        session.setWhichTrains(approach, Side.E, true, WhichTrains.WHITELIST, Collections.singletonList(mx.getName()));

        Layout railway = build();

        assertEquals(endsFrom(railway, q, "MainPlatform"), Collections.emptyList(), "Q may stop at Approach");

        StandingRules rules = railway.standingRulesAt(session.getStationIndex().pointNamesAt(approach));

        for (boolean byHand : new boolean[] {true, false})
        {
            StandingRules.Cell here = rules.cells(StandingRules.Row.SENT_HERE, byHand).get("E");

            assertEquals(here.getAnswer(), StandingRules.Answer.NO, "Q sent there from the east: " + here);
        }
    }

    /**
     * The setup's checks name a train that may stop from a side, may not run through from it, and cannot reverse: it
     * could neither carry on nor turn.  Q, which can reverse, is not named.
     *
     * MUTATION: take `checkTrainsThatCannotTurn` out of `AutonomySession.check`, its Blacklist arm, or its arm for a
     * square that never turns, and this fails.
     */
    @Test
    public void testTheChecksNameATrainThatCouldNeitherGoOnNorTurn() throws Exception
    {
        st31();
        session.setLocomotiveSource(scenario.getModel()::getLocByName);
        session.setWhichTrains(approach, Side.E, false, WhichTrains.WHITELIST,
            Arrays.asList(q.getName(), plain.getName()));

        assertEquals(said(), Collections.singletonList(plain.getName()), "the checks said: " + said());

        session.setWhichTrains(approach, Side.E, true, WhichTrains.BLACKLIST, Collections.singletonList(plain.getName()));

        assertEquals(said(), Collections.singletonList(plain.getName()), "a blacklist from the east: " + said());

        // Where trains never turn, every train the stop cell names and the run-through cell bars is named
        session.setWhichTrains(approach, Side.E, true, WhichTrains.WHITELIST, Collections.singletonList(mx.getName()));
        session.setPointFlag(approach, AutonomyBuilder.CAN_REVERSE, false);

        assertEquals(said(), Collections.singletonList(q.getName() + ", " + plain.getName()),
            "where trains never turn: " + said());

        session.setPointFlag(approach, AutonomyBuilder.CAN_REVERSE, true);

        // Not where nothing arrives from the east, nor where every train must turn, nor at a square that is no station
        int[] none = new int[3];

        session.setBarredArrivals(approach, Collections.singleton(Side.E));
        none[0] = cannotTurn();
        session.setBarredArrivals(approach, Collections.<Side>emptySet());
        session.setPointProperty(approach, AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);
        none[1] = cannotTurn();
        session.setPointProperty(approach, AutonomyBuilder.MUST_REVERSE, null);
        session.setStopsAndRunsThrough(approach, false, true);
        none[2] = cannotTurn();

        assertEquals(none, new int[3], "findings where trains may not arrive by the east side, where every train must"
            + " turn, and where the square is no station: " + Arrays.toString(none));
    }

    private static List<String> said()
    {
        List<String> out = new ArrayList<>();

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (AutonomyChecks.TRAINS_CANNOT_TURN.equals(finding.getMessageKey()) && approach.equals(finding.getTile()))
            {
                out.add(finding.getSubject());
            }
        }

        return out;
    }

    private static int cannotTurn()
    {
        int out = 0;

        for (AutonomyChecks.Finding finding : session.check())
        {
            if (AutonomyChecks.TRAINS_CANNOT_TURN.equals(finding.getMessageKey())) out++;
        }

        return out;
    }

    /**
     * A renamed or deleted locomotive is followed into each cell, as on the one list, and one the database lacks is let
     * go of there too.
     *
     * MUTATION: in the store's rename, walk only the square (not `Point.listsIn`), take `removeListedLoc` out of
     * `Layout.locDeleted`, or its stop cell or `trainsBySide` sweep out of it, and this fails.
     */
    @Test
    public void testTheCellsFollowARenameAndADeletion() throws Exception
    {
        st31();

        Layout railway = build();

        railway.locDeleted(mx);
        railway.locDeleted(q);

        for (Point each : copies(railway))
        {
            assertFalse(each.allowedToStop().getListed().contains(mx) || each.allowedToPass() != null
                && each.allowedToPass().getListed().contains(mx), "the running layout kept a deleted train on a cell");
            assertFalse(each.allowedToStop().getListed().contains(q), "the running layout kept Q on a stop cell");
            assertFalse(each.toJSON().toString().contains("Sides "), "an export names a deleted train: " + each.toJSON());
        }

        session.getStore().locomotiveRenamed(mx.getName(), "Sides MX renamed");

        assertEquals(session.getTrainList(approach, Side.E, true), Collections.singletonList("Sides MX renamed"),
            "a rename missed a cell");

        session.getStore().locomotiveDeleted("Sides MX renamed");

        assertEquals(session.getTrainList(approach, Side.E, true), Collections.emptyList(), "a deletion missed a cell");

        assertFalse(session.forgetLocomotivesNotIn("Main", Collections.singleton(mx.getName())).isEmpty(),
            "Q, named on a cell and not in the database given, was not let go of");
        assertEquals(session.getTrainList(approach, Side.E, false), Collections.emptyList(), "Q is still on the cell");
    }
}
