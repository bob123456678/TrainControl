import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Home stations and the "return to home" planner.
 *
 * Two things are under test and they fail differently:
 *
 *  - **Where home comes from.**  A locomotive's home is the station it occupied when it first appeared
 *    on the graph, claimed once and never re-claimed.  The map is injective by construction - a station
 *    can be claimed by only one locomotive - because two locomotives wanting one station would make
 *    returning home unsatisfiable no matter how good the planner is.  A locomotive placed on a station
 *    already claimed gets no home and becomes a free agent.
 *  - **The plan.**  Getting everyone home is a rearrangement problem: a station holds one locomotive,
 *    so a train cannot go home while another sits there.  The planner works on a copy of the occupancy,
 *    never on live state, because "could this move happen after three other moves" has no answer on
 *    live state.
 *
 * Plans are checked by *applying* them - each move is replayed against the model and the target
 * asserted free beforehand - rather than by inspecting the move list.  A plan that reads plausibly and
 * routes a train into an occupied station is exactly the failure worth catching, and only replay
 * catches it.
 */
public class testHomeStaging
{
    private static MarklinControlStation model;

    /** Feedback range picked clear of the other suites (88xx is shared, so these sit at the top). */
    private static final int S88_BASE = 8890;

    private static final String LOC_A = "HS alpha";
    private static final String LOC_B = "HS bravo";
    private static final String LOC_C = "HS charlie";

    private static final String[] TEST_LOCS = { LOC_A, LOC_B, LOC_C };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        model = init(null, true, false, false, false);
        model.stop();

        for (int i = 0; i < TEST_LOCS.length; i++)
        {
            model.newMM2Locomotive(TEST_LOCS[i], 81 + i);
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        for (String name : TEST_LOCS)
        {
            model.deleteLoc(name);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------------------------

    private static String json(String s)
    {
        return s.replace('\'', '"');
    }

    /**
     * Four stations in a ring, each edge present in both directions, so every station can reach every
     * other and the graph itself never makes a plan impossible.  Which locomotives start where is the
     * only variable.
     */
    private static String ring(String locAtA, String locAtB, String locAtC)
    {
        return json("{'points': ["
            + station("HS A", 0, locAtA) + ","
            + station("HS B", 1, locAtB) + ","
            + station("HS C", 2, locAtC) + ","
            + station("HS D", 3, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("HS C", "HS D") + "," + edge("HS D", "HS C") + ","
            + edge("HS D", "HS A") + "," + edge("HS A", "HS D")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    private static String station(String name, int s88Offset, String loc)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (S88_BASE + s88Offset)
            + (loc == null ? "" : ", 'loc': {'name': '" + loc + "'}") + "}";
    }

    private static String edge(String from, String to)
    {
        return "{'start': '" + from + "', 'end': '" + to + "'}";
    }

    /** Loads a graph and returns it, asserting it parsed - an invalid graph fails every test below
     *  for reasons that have nothing to do with staging. */
    private static Layout load(String config)
    {
        model.parseAuto(config);

        Layout layout = model.getAutoLayout();

        assertTrue(layout.isValid(), "precondition: the test graph must parse - " + Layout.getLastError());

        return layout;
    }

    private static MarklinLocomotive loc(String name)
    {
        return model.getLocByName(name);
    }

    /**
     * Replays a plan against the model, checking the invariant that makes it a plan at all: every move
     * must find its destination free at the moment it runs.
     */
    private static void applyPlan(Layout layout, HomeStaging.Plan plan)
    {
        for (HomeStaging.Move move : plan.getMoves())
        {
            assertNull(layout.getPoint(move.getEnd().getName()).getCurrentLocomotive(),
                "move \"" + move + "\" sends a locomotive into an occupied station");

            assertTrue(
                layout.moveLocomotive(move.getLocomotive().getName(), move.getEnd().getName(), false),
                "move \"" + move + "\" was rejected by the model");
        }
    }

    private static void assertEveryoneHome(Layout layout)
    {
        for (java.util.Map.Entry<org.traincontrol.base.Locomotive, org.traincontrol.automation.Point> e
            : layout.getHomeStations().entrySet())
        {
            assertEquals(layout.getPoint(e.getValue().getName()).getCurrentLocomotive(), e.getKey(),
                e.getKey().getName() + " did not end on its home station " + e.getValue().getName());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Where home comes from
    // ---------------------------------------------------------------------------------------------

    /**
     * Loading the graph claims a home for every locomotive it places.
     */
    @Test
    public void testLoadingTheGraphClaimsHomes()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"));
        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS B"));
        assertNull(layout.getHomeStation(loc(LOC_C)), "a locomotive the graph never placed has no home");
    }

    /**
     * A locomotive placed by hand after the load claims where it is put - but only if that station is
     * not already somebody's home.
     */
    @Test
    public void testPlacingByHandClaimsAnUnclaimedStationOnly()
    {
        Layout layout = load(ring(LOC_A, null, null));

        assertTrue(layout.moveLocomotive(LOC_B, "HS C", false));
        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS C"),
            "an unclaimed station is claimed by the first locomotive placed on it");

        // HS A belongs to LOC_A, which has moved away - the claim outlives the occupancy
        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));
        assertTrue(layout.moveLocomotive(LOC_C, "HS A", false));

        assertNull(layout.getHomeStation(loc(LOC_C)),
            "HS A is already claimed, so this locomotive is a free agent rather than a second claimant");
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "and the original claim is untouched");
    }

    /**
     * Moving a locomotive that already has a home does not re-home it - otherwise "return to home"
     * would mean "return to wherever it was last put", which is not a destination anyone chose.
     */
    @Test
    public void testMovingAHomedLocomotiveDoesNotRehomeIt()
    {
        Layout layout = load(ring(LOC_A, null, null));

        assertTrue(layout.moveLocomotive(LOC_A, "HS C", false));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"));
    }

    /**
     * Deleting a locomotive releases its claim, so the station can be claimed again.
     *
     * Without this the station stays spoken for by something that no longer exists, and nothing placed
     * there afterwards could ever have a home - the same leak that outlived deleted locomotives in the
     * exclusion sets until it was found as IND-M4.
     */
    @Test
    public void testDeletingALocomotiveReleasesItsHome() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"));

        MarklinLocomotive deleted = loc(LOC_A);
        layout.locDeleted(deleted);

        assertNull(layout.getHomeStation(deleted));

        assertTrue(layout.moveLocomotive(LOC_B, "HS A", false));
        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS A"),
            "the released station is claimable again");
    }

    // ---------------------------------------------------------------------------------------------
    // Planning
    // ---------------------------------------------------------------------------------------------

    /**
     * Three stations in a line, one way only - there is no route from any station back to itself.
     */
    private static String line(String locAtA)
    {
        return json("{'points': ["
            + station("HS A", 0, locAtA) + ","
            + station("HS B", 1, null) + ","
            + station("HS C", 2, null)
            + "],'edges': [" + edge("HS A", "HS B") + "," + edge("HS B", "HS C")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A layout already in order reports so, even when no locomotive could route back to where it
     * stands.
     *
     * The reachability check asks whether each locomotive can get from where it is to its home.  Asked
     * of a locomotive already at home that is a route from a point to itself, which exists only if the
     * track loops - so on a line the check called a perfectly ordered layout impossible.  Everything
     * already being home is now settled before reachability is considered at all.
     */
    @Test
    public void testAlreadyHomeIsReportedOnALayoutWithNoWayBack()
    {
        Layout layout = load(line(LOC_A));

        assertEquals(layout.planReturnToHome().getOutcome(), HomeStaging.Outcome.ALREADY_HOME);
    }

    /**
     * Triage answers the cheap half without planning, and never disagrees with the plan.
     *
     * The UI asks it before confirming that the timetable will be replaced - there is no point putting
     * that question to someone when nothing is going to run.
     */
    @Test
    public void testTriageAgreesWithThePlanAndNeedsNoSearch()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertEquals(layout.triageReturnToHome(), HomeStaging.Outcome.ALREADY_HOME);
        assertEquals(layout.planReturnToHome().getOutcome(), HomeStaging.Outcome.ALREADY_HOME);

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        assertNull(layout.triageReturnToHome(),
            "with work to do, only a plan can say whether it is possible");
        assertTrue(layout.planReturnToHome().isPossible());
    }

    /**
     * Gives the layout a timetable of its own, so there is something to lose.
     */
    private static List<TimetablePath> giveTimetable(Layout layout, String locName)
    {
        List<List<Edge>> paths = layout.getPossiblePaths(loc(locName), true);

        assertFalse(paths.isEmpty(), "precondition: the fixture must offer this locomotive somewhere to go");

        List<TimetablePath> timetable = new ArrayList<>();

        timetable.add(new TimetablePath(loc(locName), paths.get(0), 111L));
        timetable.add(new TimetablePath(loc(locName), paths.get(0), 222L));

        layout.setTimetable(new ArrayList<>(timetable));

        return timetable;
    }

    /**
     * A staging run borrows the timetable and gives back exactly what it took.
     *
     * The operator's timetable is theirs - captured by hand, or built entry by entry - and it is only
     * written to disk when the autonomy file is saved.  Returning home occupies the timetable because
     * that is the machinery that drives trains, so it has to put back what it displaced: the same
     * entries, in the same order, with their execution times intact.  Sequential mode has to go back
     * too, or the restored timetable would run under staging semantics it was never written for.
     */
    @Test
    public void testTheTimetableIsBorrowedAndGivenBackUnchanged()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        List<TimetablePath> original = giveTimetable(layout, LOC_A);

        assertFalse(layout.isTimetableSequential(), "precondition: an ordinary timetable is not sequential");

        // What the UI does before it hands the timetable over - a copy, because getTimetable is live
        List<TimetablePath> borrowed = new ArrayList<>(layout.getTimetable());

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        assertTrue(layout.loadReturnToHomeTimetable().isPossible(),
            "precondition: the fixture must produce a plan, or nothing displaces the timetable and the "
            + "assertions below would be measuring the wrong thing");

        assertNotEquals(layout.getTimetable(), original, "the plan should have displaced the timetable");
        assertTrue(layout.isTimetableSequential(), "a staging plan must run one train at a time");

        // What the UI does in its finally, however the run ended
        layout.setTimetable(borrowed);

        assertEquals(layout.getTimetable().size(), original.size(), "a different number of entries came back");

        for (int i = 0; i < original.size(); i++)
        {
            assertSame(layout.getTimetable().get(i), original.get(i),
                "entry " + i + " is not the one that was taken away");

            assertEquals(layout.getTimetable().get(i).getExecutionTime(), original.get(i).getExecutionTime(),
                "entry " + i + " came back with a different execution time");
        }

        assertFalse(layout.isTimetableSequential(),
            "restoring the timetable must also restore ordinary execution, or the operator's timetable "
            + "would run under staging rules");
    }

    /**
     * Loading a plan leaves timetable capture as it found it.
     *
     * Capture has to be off while the plan is written, or the staging moves would be recorded as though
     * the operator had driven them.  Turning it off is not the same as turning it off permanently, and
     * an operator who had capture on has no reason to expect it silently switched off.
     */
    @Test
    public void testTimetableCaptureSurvivesLoadingAPlan()
    {
        for (boolean capturing : new boolean[] { true, false })
        {
            Layout layout = load(ring(LOC_A, LOC_B, null));

            layout.setTimetableCapture(capturing);
            assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

            layout.loadReturnToHomeTimetable();

            assertEquals(layout.isTimetableCapture(), capturing,
                "capture was " + capturing + " before the plan was loaded and must be " + capturing
                + " after it");
        }
    }

    /**
     * getTimetable hands back the live list, which is why the caller has to copy it.
     *
     * Holding the returned list and expecting to restore from it later does not work - loading a plan
     * empties that very list.  This is pinned because the mistake is invisible: the code reads as though
     * it saved the timetable, and the failure only shows up as the operator's timetable quietly gone.
     */
    @Test
    public void testGetTimetableIsLiveSoCallersMustCopyToRestoreIt()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        giveTimetable(layout, LOC_A);

        TimetablePath first = layout.getTimetable().get(0);

        List<TimetablePath> notACopy = layout.getTimetable();
        List<TimetablePath> aCopy = new ArrayList<>(layout.getTimetable());

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));
        assertTrue(layout.loadReturnToHomeTimetable().isPossible(), "precondition: a plan must be loaded");

        // Checked by content rather than by size: a plan that happened to have as many moves as the
        // timetable had entries would make a size comparison pass while proving nothing
        assertFalse(notACopy.contains(first),
            "if the live list still held the original entries this test would be pointless - and the "
            + "copy in the UI could be dropped");

        assertTrue(aCopy.contains(first), "the copy must still hold what was taken away");

        layout.setTimetable(aCopy);

        assertEquals(layout.getTimetable().size(), 2, "only the copy could restore the original");
    }

    /**
     * Nothing placed means nothing to offer, and it must not read as an error.
     */
    @Test
    public void testEmptyGraphReportsNoLocomotives()
    {
        Layout layout = load(ring(null, null, null));

        assertEquals(layout.planReturnToHome().getOutcome(), HomeStaging.Outcome.NO_LOCOMOTIVES);
    }

    /**
     * Everything already where it belongs is its own answer, distinct from "a plan is ready" - there is
     * nothing to run, and offering a run would be misleading.
     */
    @Test
    public void testUntouchedGraphReportsAlreadyHome()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.ALREADY_HOME);
        assertFalse(plan.isPossible(), "there is nothing to execute");
    }

    /**
     * The straightforward case: one locomotive has wandered, its home is free, send it back.
     */
    @Test
    public void testASingleDisplacedLocomotiveIsPlannedHome()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "outcome was " + plan.getOutcome());
        assertEquals(plan.getMoves().size(), 1, "one locomotive is away, so one move: " + plan.getMoves());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);
    }

    /**
     * The case the greedy pass cannot solve on its own: two locomotives on each other's home stations.
     *
     * Neither can go home first - the station it wants is occupied by the one that wants the station it
     * is standing on.  It needs a locomotive moved somewhere it does not belong, which only the search
     * will do, and it is solvable here only because the ring has a spare station.
     */
    @Test
    public void testTwoLocomotivesOnEachOthersHomesAreUnwound()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // Park one out of the way, swap them, then take the parked one back out of the picture
        assertTrue(layout.moveLocomotive(LOC_A, "HS C", false));
        assertTrue(layout.moveLocomotive(LOC_B, "HS A", false));
        assertTrue(layout.moveLocomotive(LOC_A, "HS B", false));

        assertEquals(layout.getPoint("HS A").getCurrentLocomotive(), loc(LOC_B));
        assertEquals(layout.getPoint("HS B").getCurrentLocomotive(), loc(LOC_A));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "outcome was " + plan.getOutcome());
        assertTrue(plan.getMoves().size() >= 3,
            "a swap needs a locomotive moved out of the way and back: " + plan.getMoves());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);
    }

    /**
     * A corridor with one siding: HOME and FAR are joined only through MID, and MID has a siding.
     */
    private static String corridor(String atHome, String atMid, String atFar)
    {
        return json("{'points': ["
            + station("HS HOME", 4, atHome) + ","
            + station("HS MID", 5, atMid) + ","
            + station("HS FAR", 6, atFar) + ","
            + station("HS SIDING", 7, null)
            + "],'edges': ["
            + edge("HS HOME", "HS MID") + "," + edge("HS MID", "HS HOME") + ","
            + edge("HS MID", "HS FAR") + "," + edge("HS FAR", "HS MID") + ","
            + edge("HS MID", "HS SIDING") + "," + edge("HS SIDING", "HS MID")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A locomotive that is already home may still have to move, so that another can get past it.
     *
     * The only way from FAR to HOME is through MID, and MID's own occupant is sitting exactly where it
     * belongs.  Nothing is wrong with the arrangement except that it is in the way, so the plan has to
     * park that locomotive on the siding, let the other through, and put it back.
     *
     * This is the shape a real layout produced: a locomotive could not reach its home because a
     * correctly-placed train stood on the only corridor to it.  The planner reported that as
     * impossible, because it asked whether any of a handful of routes computed WITHOUT regard to
     * occupancy happened to be clear, rather than whether a clear route existed.  On a layout with
     * loops those routes share the busy stretch, so they were all blocked at once.
     */
    @Test
    public void testALocomotiveAtHomeStepsAsideAndReturns()
    {
        Layout layout = load(corridor(LOC_A, LOC_B, null));

        // LOC_A goes to the far end, so its way back runs through LOC_B's home
        assertTrue(layout.moveLocomotive(LOC_A, "HS FAR", false));

        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS MID"));
        assertEquals(layout.getPoint("HS MID").getCurrentLocomotive(), loc(LOC_B),
            "precondition: the blocker is standing on its own home");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(),
            "a train in the way is not an impossibility - it can be moved.  Outcome was "
            + plan.getOutcome());

        assertTrue(plan.getMoves().size() >= 3,
            "the blocker must step aside and come back: " + plan.getMoves());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);
    }

    /**
     * A free agent may be moved but never has to land anywhere in particular, so it does not make a
     * plan impossible by sitting on somebody's home.
     */
    @Test
    public void testAFreeAgentIsMovedOutOfTheWay()
    {
        Layout layout = load(ring(LOC_A, null, null));

        // LOC_C has no home, and parks on the station LOC_A wants
        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));
        assertTrue(layout.moveLocomotive(LOC_C, "HS A", false));

        assertNull(layout.getHomeStation(loc(LOC_C)), "precondition: it really is a free agent");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "outcome was " + plan.getOutcome());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);
    }

    // ---------------------------------------------------------------------------------------------
    // Loading the timetable
    // ---------------------------------------------------------------------------------------------

    /**
     * The plan is handed to the existing timetable machinery rather than to a second execution engine.
     * Every entry must be unrun and undelayed: executeTimetable resumes at the first entry whose
     * execution time is zero, and dispatches each as soon as the one before it has started.
     */
    @Test
    public void testLoadingTheTimetableProducesRunnableEntries()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

        assertTrue(plan.isPossible());
        assertEquals(layout.getTimetable().size(), plan.getMoves().size(),
            "every move should be loaded as a timetable entry");

        for (int i = 0; i < layout.getTimetable().size(); i++)
        {
            assertEquals(layout.getTimetable().get(i).getLoc(), plan.getMoves().get(i).getLocomotive(),
                "entry " + i + " must keep the plan's order - the order is what makes it safe");
            assertEquals(layout.getTimetable().get(i).getExecutionTime(), 0,
                "a freshly loaded entry has not run");
            assertEquals(layout.getTimetable().get(i).getSecondsToNext(), 0,
                "no artificial delay: the retry loop holds moves back when the path is busy");
        }

        assertFalse(layout.isTimetableCapture(),
            "capture must not be left on, or executing this would record it all over again");
    }

    /**
     * A staging plan must be flagged to run one train at a time, and any other timetable must not be.
     *
     * This is the fix for a real failure: executeTimetable normally dispatches an entry as soon as the
     * one before it has STARTED, and executePath locks a whole path up front.  Two staging moves
     * overlapping therefore contended for an edge the planner - which models nothing as moving - never
     * considered, and the second retried forever on a fixed path it could not abandon, while a free
     * alternative existed that only live path selection would have found.
     */
    @Test
    public void testStagingPlansAreFlaggedSequentialAndOtherTimetablesAreNot()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertFalse(layout.isTimetableSequential(), "a freshly parsed layout has no staging plan");

        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));
        assertTrue(layout.loadReturnToHomeTimetable().isPossible());

        assertTrue(layout.isTimetableSequential(),
            "a staging plan is only valid one train at a time");

        // Any other timetable load must put it back - the flag belongs to the plan, not the layout
        layout.setTimetable(new java.util.LinkedList<>());

        assertFalse(layout.isTimetableSequential(),
            "loading any other timetable restores the normal overlapping behaviour");
    }

    /**
     * Nothing is loaded when there is nothing to do, so a stale plan cannot be left sitting in the
     * timetable waiting to be executed by accident.
     */
    @Test
    public void testNothingIsLoadedWhenAlreadyHome()
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));
        layout.setTimetable(new java.util.LinkedList<>());

        HomeStaging.Plan plan = layout.loadReturnToHomeTimetable();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.ALREADY_HOME);
        assertTrue(layout.getTimetable().isEmpty(), "an untouched layout loads no moves");
    }
}
