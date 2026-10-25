package core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinLocomotive;

/**
 * What a run leaves behind (FR-136 Phase 6; Adam: "Don't limit this to UI interactions, it can also be state outcomes"):
 * where the train stands and the side it came in by, the track it holds, the switch, the statistics, and the setup saved and
 * read back.
 *
 * On the `single-switch` scenario, simulated.  One train sets off from WestEnd under autonomy and makes three journeys or
 * more; Graceful Stop then lets it finish, and `support.Settle` waits until the railway says it is idle.  A second run
 * sends it home to BranchPlatform with Return Home, over the switch.  Each run is made once, in `setUpClass`, and what it
 * left is read by the claims.
 *
 * @author Adam
 */
public class testARunLeavesItsStateBehind
{
    private static support.Scenario scenario;

    private static MarklinLocomotive train;

    private static final String TRAIN = "run state probe";

    /** Where the train sets off, a terminus at the west end of the line. */
    private static final int WEST_X = 1;
    private static final int WEST_Y = 3;

    /** Its home for the Return Home run: the branch platform, through the switch's turned road. */
    private static final int BRANCH_X = 6;
    private static final int BRANCH_Y = 2;

    private static final String SWITCH = "Switch 1";

    private static final int JOURNEYS = 3;

    /** How long a run is given here at most: a ceiling, not a pause. */
    private static final long PATIENCE = 60000;

    /** The autonomy run's railway, and every path it started, in order. */
    private static Layout autonomy;
    private static final List<List<Edge>> started = Collections.synchronizedList(new ArrayList<List<Edge>>());

    /** Whether the railway held every edge of a path while that path ran: asked as each journey set off. */
    private static final List<Boolean> heldWhileRunning = Collections.synchronizedList(new ArrayList<Boolean>());

    private static boolean cameToRest;
    private static int journeysStarted;
    private static int journeysCounted;
    private static long ranToday;
    private static boolean switchTurnedAfterRun;

    /** What the setup read back from disk once the run had been captured and saved. */
    private static String savedAt;
    private static Side savedFacing;
    private static Map<TileKey, String> savedPlacements;
    private static String rebuiltAt;

    /** The Return Home run's railway, and the switch before it. */
    private static Layout homeRun;
    private static boolean switchTurnedBefore;
    private static boolean homeRunFinished;
    private static HomeStaging.Outcome homeTriage;
    private static String homePlan;
    private static boolean homeRunCameToRest;
    private static Map<TileKey, String> savedAfterHome;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // The sandbox is opened inside Scenario.open, BEFORE the model is built (OB-111).
        scenario = support.Scenario.open("single-switch");

        // Simulation is a debug mode, and the railway refuses it otherwise
        scenario.getModel().debug(true);
        scenario.getModel().stop();

        train = scenario.getModel().newMM2Locomotive(TRAIN, 64);

        assertNotNull(train, "precondition: the class could not create its own train");

        runAutonomy();

        runHome();
    }

    /** Autonomy, three journeys or more, then Graceful Stop; then the setup captured, saved and read back. */
    private static void runAutonomy() throws Exception
    {
        AutonomySession session = scenario.getSession();

        session.placeLocomotive(west(), TRAIN);

        // AUTONOMY LEAVES THE BRANCH ALONE, so every journey over the switch sets it straight; it starts turned
        session.setAutoDestination(branch(), false);

        autonomy = scenario.build();

        // Turned at each terminus: a train that cannot turn is offered none of the three
        train.setReversible(true);

        support.Settle.simulate(autonomy);

        scenario.getModel().getAccessoryByName(SWITCH).turn();

        final Layout watched = autonomy;

        train.setCallback(Layout.CB_ROUTE_START, loc ->
        {
            List<Edge> path = new ArrayList<>(watched.getActiveLocomotives().get(loc));

            boolean held = !path.isEmpty();

            for (Edge e : path) held &= e.isLockHeld(loc);

            started.add(path);
            heldWhileRunning.add(held);
        });

        try
        {
            autonomy.runLocomotives();

            support.Settle.journeys(train, JOURNEYS, PATIENCE);

            cameToRest = support.Settle.gracefully(autonomy, PATIENCE);
        }
        finally
        {
            train.setCallback(Layout.CB_ROUTE_START, null);

            autonomy.stopLocomotives();
        }

        journeysStarted = started.size();
        journeysCounted = train.getNumPaths();
        ranToday = train.getRuntimeToday();
        switchTurnedAfterRun = scenario.getModel().getAccessoryByName(SWITCH).isTurned();

        // WHAT THE WINDOW DOES ON THE WAY OUT (`saveState`): the running layout folded into its configuration, and written
        session.captureFromLayout(autonomy.toJSON());
        session.saveWithoutReconciling();

        AutonomySession reread = new AutonomySession(scenario.getFolder());

        reread.open(scenario.getPages());

        savedPlacements = reread.placementsAutonomyWillWrite();
        savedFacing = reread.facingOf(TRAIN);

        Point stands = autonomy.getLocomotiveLocation(train);

        TileKey square = stands == null ? null : reread.getStationIndex().squareOf(stands.getName());

        savedAt = square == null ? null : reread.getLocomotiveNameAt(square);

        // AND BUILT AGAIN from what was read back, as the next start-up builds it
        scenario.getModel().parseAuto(reread.buildConfiguration());

        Point rebuilt = scenario.getModel().getAutoLayout().getLocomotiveLocation(train);

        rebuiltAt = rebuilt == null ? null : rebuilt.getName();
    }

    /** The train put back at WestEnd with the switch straight, and sent home to BranchPlatform. */
    private static void runHome() throws Exception
    {
        AutonomySession session = scenario.getSession();

        session.placeLocomotive(west(), TRAIN);
        session.setAutoDestination(branch(), true);
        session.setHome(branch(), TRAIN);

        homeRun = scenario.build();

        train.setReversible(true);

        support.Settle.simulate(homeRun);

        scenario.getModel().getAccessoryByName(SWITCH).straight();

        switchTurnedBefore = scenario.getModel().getAccessoryByName(SWITCH).isTurned();

        try
        {
            // ASKED HERE AND ASSERTED BY THE CLAIMS, so a run that cannot start fails them rather than skipping them
            homeTriage = homeRun.triageReturnToHome();

            HomeStaging.Plan plan = homeTriage == null ? homeRun.planReturnToHome() : null;

            homePlan = plan == null ? String.valueOf(homeTriage) : String.valueOf(plan.getOutcome());

            if (plan == null || !plan.isPossible()) return;

            homeRun.loadReturnToHomeTimetable();

            // ON A THREAD OF ITS OWN, waited for at most PATIENCE: the timetable waits on the railway without a bound
            final boolean[] finished = {false};

            Thread going = new Thread(() -> finished[0] = homeRun.executeTimetable(), "return home");

            going.setDaemon(true);
            going.start();
            going.join(PATIENCE);

            homeRunFinished = finished[0];

            homeRunCameToRest = support.Settle.idle(homeRun, PATIENCE);
        }
        finally
        {
            homeRun.stopLocomotives();
        }

        session.captureFromLayout(homeRun.toJSON());
        session.saveWithoutReconciling();

        AutonomySession reread = new AutonomySession(scenario.getFolder());

        reread.open(scenario.getPages());

        savedAfterHome = reread.placementsAutonomyWillWrite();
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (scenario == null) return;

        try
        {
            scenario.getModel().deleteLoc(TRAIN);
        }
        catch (Exception alreadyGone)
        {
        }

        scenario.close();
    }

    private static TileKey west()
    {
        return scenario.tile(WEST_X, WEST_Y);
    }

    private static TileKey branch()
    {
        return scenario.tile(BRANCH_X, BRANCH_Y);
    }

    private static List<Edge> lastJourney()
    {
        assertTrue(journeysStarted >= JOURNEYS, "precondition: the train made " + journeysStarted + " journeys, not "
            + JOURNEYS + " or more, so the run did not get far enough to leave anything worth reading");

        return started.get(journeysStarted - 1);
    }

    /**
     * Graceful Stop brings the run to rest: nothing running, no train under way, by the railway's own word.
     *
     * MUTATION: leave the train among the active ones at the end of its journey (`executePath`'s
     * `activeLocomotives.remove`), and the railway never says it is idle.
     */
    @Test
    public void testGracefulStopBringsTheRunToRest()
    {
        assertTrue(cameToRest, "the run did not come to rest after Graceful Stop: running " + autonomy.isAutoRunning()
            + ", trains under way " + autonomy.getActiveLocomotives().keySet());

        assertFalse(autonomy.isAutoRunning(), "autonomy still says it is running after Graceful Stop");

        assertTrue(autonomy.getActiveLocomotives().isEmpty(), "a train is still under way after the run came to rest: "
            + autonomy.getActiveLocomotives().keySet());
    }

    /**
     * The train stands where its last journey ended, on that one Point and on no other.
     *
     * MUTATION: skip putting the train on the Point it arrived at, and this fails.
     */
    @Test
    public void testTheTrainStandsWhereItsLastJourneyEnded()
    {
        List<Edge> last = lastJourney();

        Point end = last.get(last.size() - 1).getEnd();

        List<String> holding = new ArrayList<>();

        for (Point p : autonomy.getPoints())
        {
            if (train.equals(p.getCurrentLocomotive())) holding.add(p.getName());
        }

        assertEquals(holding, Collections.singletonList(end.getName()), "the train's last journey was "
            + Edge.pathToString(last) + ", and the Points holding it afterwards are " + holding);

        assertEquals(edgeNames(end.getArrivedAlong()), edgeNames(last), "the Point it stands on does not record the road"
            + " it arrived along, which the tail walk behind a standing train follows");
    }

    /**
     * And the Point it stands on remembers which side it came in by, where its tail lies: the side of the square the last
     * journey's last leg came from, worked out here from the diagram's squares.  The facing a train is given back after a
     * terminus, and the track a standing train blocks, are read from it.
     *
     * MUTATION: record the arrival side as the side the train did not come in by, and this fails.
     */
    @Test
    public void testTheTrainRemembersWhichWayItCameIn()
    {
        List<Edge> last = lastJourney();

        Point end = last.get(last.size() - 1).getEnd();
        Point from = last.get(last.size() - 1).getStart();

        assertEquals(end.getArrivedFrom(), sideTowards(end, from).name(), "the train ran into " + end.getName()
            + " from " + from.getName() + ", and the railway records it as having come in by another side");
    }

    /**
     * Every edge a journey ran over was held while it ran, and none is held once the run is at rest.
     *
     * MUTATION: skip `unlockPath` at the end of a journey, and the run leaves its track claimed.
     */
    @Test
    public void testNoTrackIsClaimedOnceTheRunIsAtRest()
    {
        assertFalse(heldWhileRunning.isEmpty(), "precondition: no journey was seen setting off");

        assertFalse(heldWhileRunning.contains(false), "a journey set off without its track held: " + heldWhileRunning);

        List<String> held = new ArrayList<>();

        for (Edge e : autonomy.getEdges())
        {
            if (e.isLockHeld(null) || e.isRunOver(null)) held.add(e.getName());
        }

        assertEquals(held, Collections.<String>emptyList(), "track is still claimed after the run came to rest");
    }

    /**
     * Each journey is counted on the train, once: the count is the number of journeys that set off.
     *
     * MUTATION: drop `incrementNumPaths` at the end of a journey, and this fails.
     */
    @Test
    public void testEveryJourneyIsCountedOnTheTrain()
    {
        lastJourney();

        assertEquals(journeysCounted, journeysStarted, "the train counts " + journeysCounted
            + " journeys, and " + journeysStarted + " set off and finished");
    }

    /**
     * The time it ran is kept under today's date, which is what the statistics window reads.
     *
     * MUTATION: skip adding the time when the train is stopped (`_setSpeed`), and this fails.
     */
    @Test
    public void testTheRunningTimeIsKeptForToday()
    {
        lastJourney();

        assertTrue(train.getHistoricalOperatingTime().containsKey(Locomotive.getDate()),
            "the train has no running record for today after " + journeysStarted + " journeys: "
            + train.getHistoricalOperatingTime());

        assertTrue(ranToday > 0, "the train ran " + journeysStarted + " journeys today and its running time for today is "
            + ranToday + " ms");
    }

    /**
     * Every station a journey ended at is in the railway's visit history, which a balanced run uses to send trains where
     * none has been lately, and which a rebuild carries over.
     *
     * MUTATION: skip recording the arrival in the visit history, and this fails.
     */
    @Test
    public void testEveryStationReachedIsInTheVisitHistory()
    {
        lastJourney();

        Set<String> ended = new LinkedHashSet<>();

        synchronized (started)
        {
            for (List<Edge> path : started) ended.add(path.get(path.size() - 1).getEnd().getName());
        }

        Set<String> missing = new LinkedHashSet<>(ended);

        missing.removeAll(autonomy.getVisitHistory().keySet());

        assertTrue(missing.isEmpty(), "journeys ended at " + ended + " and the visit history has no "
            + missing + ": " + autonomy.getVisitHistory());
    }

    /**
     * The switch stands as the last journey over it set it: straight for the main platform, autonomy having been told to
     * leave the branch alone, from turned before the run.
     *
     * MUTATION: skip the accessory commands when a path is set (`configureAndLockPath`), and this fails.
     */
    @Test
    public void testTheSwitchStandsAsTheLastJourneyOverItSetIt()
    {
        lastJourney();

        Accessory.accessorySetting lastSet = null;

        synchronized (started)
        {
            for (List<Edge> path : started)
            {
                for (Edge e : path)
                {
                    if (e.getConfigCommands().containsKey(SWITCH)) lastSet = e.getConfigCommands().get(SWITCH);
                }
            }
        }

        assertNotNull(lastSet, "precondition: no journey crossed the switch, so its position says nothing");

        assertEquals(lastSet, Accessory.accessorySetting.STRAIGHT, "precondition: a journey took the branch, which"
            + " autonomy was told to leave alone");

        assertEquals(switchTurnedAfterRun, lastSet == Accessory.accessorySetting.TURN, "the last journey over " + SWITCH
            + " set it " + lastSet + ", and after the run it stands " + (switchTurnedAfterRun ? "turned" : "straight"));
    }

    /**
     * Where the run left the train is saved with the setup, and read back: the square, the facing, and the same Point
     * when the railway is built again from the file.
     *
     * MUTATION: have the capture leave the placements out of the configuration, and this fails.
     */
    @Test
    public void testWhereTheRunLeftTheTrainIsSavedAndReadBack()
    {
        Point stands = autonomy.getLocomotiveLocation(train);

        assertNotNull(stands, "precondition: the train stands nowhere after the run");

        assertEquals(savedAt, TRAIN, "read back from disk, the square the run left the train on (" + stands.getName()
            + ") does not hold it: " + savedPlacements);

        assertEquals(savedPlacements.size(), 1, "read back from disk, the setup places the train more than once, or"
            + " somebody else as well: " + savedPlacements);

        assertEquals(savedFacing, scenario.getSession().facingOf(TRAIN, autonomy), "read back from disk, the train"
            + " faces otherwise than the railway had it");

        assertEquals(rebuiltAt, stands.getName(), "built again from the file, the railway stands the train on another"
            + " Point");
    }

    /**
     * Return Home sends the train home over the switch, and leaves the switch turned for the branch it took.
     *
     * MUTATION: skip the accessory commands when a path is set, and the switch is still straight.
     */
    @Test
    public void testReturnHomeThrowsTheSwitchOnItsWayHome()
    {
        assertFalse(switchTurnedBefore, "precondition: the switch was not straight before Return Home");

        assertTrue(homeRunFinished, "Return Home did not take the train home: triage " + homeTriage + ", plan " + homePlan
            + ", stopped because " + homeRun.whyTheTimetableStopped());

        assertTrue(scenario.getModel().getAccessoryByName(SWITCH).isTurned(), "the train went home to BranchPlatform"
            + " and " + SWITCH + " is not turned for the branch");
    }

    /**
     * And it leaves the train on its home, the railway at rest with no track held, and the setup saved with the train
     * there.
     *
     * MUTATION: skip putting the train on the Point it arrived at, and this fails.
     */
    @Test
    public void testReturnHomeLeavesTheTrainAtHomeAndSaysSoOnDisk()
    {
        assertTrue(homeRunCameToRest, "the railway did not come to rest after Return Home");

        Point stands = homeRun.getLocomotiveLocation(train);

        assertNotNull(stands, "the train stands nowhere after Return Home");

        assertEquals(stands.getName(), "BranchPlatform", "the train did not come home");

        for (Edge e : homeRun.getEdges())
        {
            assertFalse(e.isLockHeld(null), e.getName() + " is still claimed after Return Home");
        }

        assertEquals(savedAfterHome, Collections.singletonMap(branch(), TRAIN), "read back from disk after Return Home,"
            + " the setup does not have the train at its home and nowhere else");
    }

    /** The side of a Point's square that another Point's square lies on. */
    private static Side sideTowards(Point at, Point other)
    {
        int[] here = squareOf(at);
        int[] there = squareOf(other);

        int dx = there[0] - here[0];
        int dy = there[1] - here[1];

        if (Math.abs(dx) >= Math.abs(dy)) return dx > 0 ? Side.E : Side.W;

        return dy > 0 ? Side.S : Side.N;
    }

    private static int[] squareOf(Point p)
    {
        String square = p.getSquare();

        assertNotNull(square, "precondition: " + p.getName() + " has no square");

        String[] xy = square.substring(square.lastIndexOf(':') + 1).split(",");

        return new int[] {Integer.parseInt(xy[0].trim()), Integer.parseInt(xy[1].trim())};
    }

    private static List<String> edgeNames(List<Edge> path)
    {
        List<String> names = new ArrayList<>();

        if (path != null) for (Edge e : path) names.add(e.getName());

        return names;
    }
}
