import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Edge;
import org.traincontrol.base.Locomotive;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;
import org.traincontrol.marklin.MarklinLocomotive;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * The ring, with HS D assigned to a named locomotive - which need not exist.
     *
     * Built by rewriting what station() produced rather than by hand, and asserting the rewrite landed,
     * so a change to the station fixture cannot silently yield a config with no assignment in it - which
     * would make the tests below pass while testing nothing.
     */
    private static String ringAssigning(String homeAtD)
    {
        String raw = station("HS D", 3, null);
        String plain = json(raw);
        String assigned = json(raw.substring(0, raw.length() - 1) + ", 'home': '" + homeAtD + "'}");

        String config = ring(LOC_A, null, null);

        assertTrue(config.contains(plain), "precondition: the ring fixture must still emit HS D plainly");

        return config.replace(plain, assigned);
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
     * With capture on, a staging run records nothing.
     *
     * Capture appends every path a locomotive starts to the timetable - and a staging run IS the
     * timetable, walked by a loop that re-reads its own size.  So capture turned the run into a
     * quine: each move appended a copy of itself, the loop walked into the copies, each copy failed
     * validation because the locomotive now stood at its path's end, and a run whose trains had all
     * arrived ended in an emergency stop and a failure dialog.  Excluding the append for the duration
     * is the fix; excluding it only around the load - which is what shipped first - was not.
     */
    @Test
    public void testAStagingRunIsNotCapturedIntoItsOwnTimetable() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        layout.setTimetableCapture(true);
        assertTrue(layout.moveLocomotive(LOC_A, "HS D", false));

        assertTrue(layout.loadReturnToHomeTimetable().isPossible(), "precondition: a plan must load");

        int staged = layout.getTimetable().size();

        assertTrue(staged > 0, "precondition: the plan must have put something in the timetable");
        assertTrue(layout.isTimetableSequential(), "precondition: a staged plan runs sequentially");

        // What executePath does for each move it starts.  Reached by reflection rather than through a
        // hook added to production code: the guard under test lives in addTimetableEntry, and a method
        // that exists only so a test can reach it is the kind of thing this review round removed.
        //
        // The flag is set the same way, because the guard is on the RUN and not on staging: a normal
        // timetable run with capture on appended itself too, and its retries never give up, so it
        // never finished at all.  Excluding only staging fixed one of two identical entrances.
        Method append = Layout.class.getDeclaredMethod(
            "addTimetableEntry", Locomotive.class, List.class, long.class);

        Field executing = Layout.class.getDeclaredField("timetableExecuting");

        append.setAccessible(true);
        executing.setAccessible(true);
        executing.setBoolean(layout, true);

        for (HomeStaging.Move move : layout.planReturnToHome().getMoves())
        {
            append.invoke(layout, move.getLocomotive(), move.getPath(), 0L);
        }

        executing.setBoolean(layout, false);

        assertEquals(layout.getTimetable().size(), staged,
            "a timetable run appended itself to the list it was executing - the dispatch loop would "
            + "then walk into the copies and abandon a run that had actually succeeded");
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
        // Exactly three, not merely at least three.  A swap cannot be done in two, and A* with an
        // admissible heuristic returns the shortest plan - so this is also the regression test for the
        // priority queue: it was ordered on a score map the relaxation rewrote, which changed an
        // enqueued entry's priority in place and let polls return states that were not the cheapest.
        // A search that explores out of order can still reach the goal, but by a longer route.
        assertEquals(plan.getMoves().size(), 3,
            "a swap needs one locomotive moved out of the way and back, and no more: " + plan.getMoves());

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
     * Deleting a station releases the home claim on it.
     *
     * The claim map is keyed by locomotive and valued by point, and releasing was wired only into the
     * key side: deleting a locomotive frees its claim, deleting its home did not.  A claim that
     * outlives its station is worse than a leak - the locomotive still counts as misplaced, so Return
     * Home stays lit, and pressing it reports that the locomotive cannot reach a station that is no
     * longer on the graph.  Stable, unactionable, and only escapable by re-creating the station or
     * reloading the file.
     */
    @Test
    public void testDeletingAStationReleasesTheHomeClaimOnIt() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "precondition: the locomotive is homed at the station about to be deleted");

        // A station can only be deleted once nothing connects to it, and the ring is bidirectional -
        // all four edges touching HS A have to go first
        assertTrue(layout.moveLocomotive(LOC_A, "HS C", false));

        layout.deleteEdge("HS A", "HS B");
        layout.deleteEdge("HS B", "HS A");
        layout.deleteEdge("HS D", "HS A");
        layout.deleteEdge("HS A", "HS D");
        layout.deletePoint("HS A");

        assertNull(layout.getHomeStation(loc(LOC_A)),
            "the claim outlived the station it was held against");

        // LOC_B is still homed and still at home, so the only homed locomotive is where it belongs.
        // Before the fix this said IMPOSSIBLE, naming a station that is no longer on the graph.
        assertEquals(layout.triageReturnToHome(), HomeStaging.Outcome.ALREADY_HOME,
            "a locomotive whose home was deleted is homeless, not permanently unable to reach home");
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

        // Nothing is asserted about capture here any more.  Loading used to switch it off, and this
        // line pinned that - but it only ever passed because the default is off, so it would have gone
        // on passing had the behaviour broken.  Capture is now left exactly as the operator set it, and
        // the guarantee that a run does not record itself lives where it is actually enforced:
        // testAStagingRunIsNotCapturedIntoItsOwnTimetable.
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

    // ---------------------------------------------------------------------------------------------
    // Assigning a home, rather than deriving one
    // ---------------------------------------------------------------------------------------------

    /**
     * An assignment says where a locomotive belongs; standing somewhere only ever said where it was.
     *
     * Assignments are applied before anything is derived, so the station a locomotive is sitting on no
     * longer speaks for it.  Everything the assignments did not mention still falls back to the old
     * rule, which is what keeps a layout that assigns nothing behaving exactly as it always did.
     */
    @Test
    public void testAnAssignmentBeatsWhereTheLocomotiveHappensToStand() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "precondition: with nothing assigned, home is the station the locomotive was placed on");

        layout.setHomeLocomotive("HS D", LOC_A);

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS D"),
            "the assignment decides, not the occupancy");

        assertEquals(layout.getHomeStation(loc(LOC_B)), layout.getPoint("HS B"),
            "a locomotive nobody assigned keeps the home it derived");

        // HS A is still occupied by LOC_A, but LOC_A is spoken for, so nothing claims HS A
        assertNull(layout.getHomeStation(loc(LOC_C)),
            "a locomotive that is not on the graph derives nothing");
    }

    /**
     * One station per locomotive: assigning it somewhere new gives up wherever it was.
     *
     * Two stations waiting for the same train can never both be satisfied, so the planner would be
     * handed a goal it cannot reach - and would report the layout impossible rather than the assignment
     * contradictory.
     */
    @Test
    public void testAssigningALocomotiveSomewhereNewReleasesItsOldStation() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.setHomeLocomotive("HS D", LOC_A);
        layout.setHomeLocomotive("HS C", LOC_A);

        assertNull(layout.getPoint("HS D").getHomeLoc(),
            "the station it was assigned to before has to let go of it");
        assertEquals(layout.getPoint("HS C").getHomeLoc(), LOC_A);
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS C"));
    }

    /**
     * Clearing one station falls back to the rule that was there before it was assigned.
     */
    @Test
    public void testClearingOneStationFallsBackToWhereItsLocomotiveStands() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.setHomeLocomotive("HS D", LOC_A);

        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS D"),
            "precondition: the assignment took effect");

        layout.setHomeLocomotive("HS D", null);

        assertNull(layout.getPoint("HS D").getHomeLoc());
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "with the assignment gone, home is once again the station it stands on");
    }

    /**
     * Clearing every assignment restores the derived homes exactly, not approximately.
     *
     * This is the promise the feature is built on: a user who tries assignments and changes their mind
     * gets back the behaviour they had before, with no residue.  Compared as a whole map rather than
     * station by station, because a leftover entry is precisely the failure worth catching.
     */
    @Test
    public void testClearingEveryAssignmentRestoresThePositionalHomesExactly() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, LOC_C));

        Map<Locomotive, Point> before = new LinkedHashMap<>(layout.getHomeStations());

        assertFalse(before.isEmpty(), "precondition: the fixture must derive some homes to restore");

        layout.setHomeLocomotive("HS D", LOC_A);
        layout.setHomeLocomotive("HS A", LOC_B);

        assertNotEquals(layout.getHomeStations(), before,
            "precondition: the assignments must have actually changed something");

        layout.clearHomeLocomotives();

        assertEquals(layout.getHomeStations(), before,
            "clearing has to leave exactly the homes a layout without this feature would have had");
    }

    /**
     * Only assignments count as assignments - derived homes are not something to offer to clear.
     *
     * The menu item that clears them all is shown on this answer alone, so getting it wrong would put a
     * destructive-sounding option in front of every user who has never assigned anything.
     */
    @Test
    public void testOnlyAssignmentsCountAsHomeLocomotives() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertFalse(layout.getHomeStations().isEmpty(), "precondition: homes were derived");
        assertFalse(layout.hasHomeLocomotives(), "derived homes are not assignments");

        layout.setHomeLocomotive("HS D", LOC_A);
        assertTrue(layout.hasHomeLocomotives());

        layout.clearHomeLocomotives();
        assertFalse(layout.hasHomeLocomotives());
    }

    /**
     * An assignment naming a locomotive that is not on the graph is kept, and ignored.
     *
     * Kept, because the locomotive may be placed later and the operator said what they meant.  Ignored,
     * because every question the planner asks is asked of locomotives that are actually placed - so an
     * absent one contributes no goal, and cannot make an otherwise fine layout unplannable.
     */
    @Test
    public void testAnAssignmentForALocomotiveNotOnTheGraphIsKeptButIgnored() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // LOC_C is in the database, but the fixture never placed it
        layout.setHomeLocomotive("HS D", LOC_C);

        assertEquals(layout.getHomeStation(loc(LOC_C)), layout.getPoint("HS D"), "the assignment is kept");

        assertTrue(layout.moveLocomotive(LOC_A, "HS C", false));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "a home for an absent locomotive must not stop the locomotives that are here");

        for (HomeStaging.Move move : plan.getMoves())
        {
            assertNotEquals(move.getLocomotive(), loc(LOC_C),
                "nothing can be routed for a locomotive that is not on the graph");
        }

        applyPlan(layout, plan);

        assertEquals(layout.getPoint("HS A").getCurrentLocomotive(), loc(LOC_A),
            "the locomotive that is here still went home");
    }

    /**
     * An assignment naming something that is not a locomotive at all is dropped when the file loads.
     *
     * A name matching nothing in the database cannot resolve later by itself, so keeping it would store
     * something that only looks like state: written back out on every save and reported again on every
     * load.  The graph itself is never invalidated over it - one bad name must not cost the layout.
     */
    @Test
    public void testAnAssignmentNamingAnUnknownLocomotiveIsDroppedOnLoad()
    {
        Layout layout = load(ringAssigning("HS phantom"));

        assertNull(layout.getPoint("HS D").getHomeLoc(),
            "a dangling name is removed rather than carried around");
        assertFalse(layout.hasHomeLocomotives());
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS A"),
            "and everything else still derives its home as usual");
    }

    /**
     * Assignments survive a save and a reload, which is the only reason to store them on the point.
     */
    @Test
    public void testAssignmentsSurviveBeingSavedAndReloaded() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.setHomeLocomotive("HS D", LOC_A);

        Layout reloaded = load(layout.toJSON());

        assertEquals(reloaded.getPoint("HS D").getHomeLoc(), LOC_A);
        assertEquals(reloaded.getHomeStation(loc(LOC_A)), reloaded.getPoint("HS D"));
        assertTrue(reloaded.hasHomeLocomotives());
    }

    /**
     * The whole point: returning home goes to the assigned station, not the one it was standing on.
     */
    @Test
    public void testReturningHomeGoesToTheAssignedStation() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        layout.setHomeLocomotive("HS D", LOC_A);
        layout.setHomeLocomotive("HS C", LOC_B);

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertTrue(plan.isPossible(), "outcome was " + plan.getOutcome());

        applyPlan(layout, plan);
        assertEveryoneHome(layout);

        assertEquals(layout.getPoint("HS D").getCurrentLocomotive(), loc(LOC_A));
        assertEquals(layout.getPoint("HS C").getCurrentLocomotive(), loc(LOC_B));
    }

    /**
     * Deleting a locomotive takes the assignment naming it with it.
     *
     * The assignment is a name, so nothing about deleting the locomotive object touches it.  Left
     * behind it is written back out on every save and reported on every load as a locomotive that is
     * not in the database - and until then the menus offer a station assigned to something gone.
     */
    @Test
    public void testDeletingALocomotiveClearsTheAssignmentNamingIt() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        layout.setHomeLocomotive("HS D", LOC_C);

        assertEquals(layout.getPoint("HS D").getHomeLoc(), LOC_C, "precondition: the assignment was made");

        layout.locDeleted(loc(LOC_C));

        assertNull(layout.getPoint("HS D").getHomeLoc(),
            "a name nothing can resolve must not be left sitting on the point");
        assertNull(layout.getHomeStation(loc(LOC_C)));
        assertFalse(layout.hasHomeLocomotives(),
            "and nothing is left for the clear-all menu item to offer");
    }

    /**
     * Renaming a locomotive keeps its assignment, rather than quietly losing it.
     *
     * Every other reference the layout holds is an object reference hashed by identity, which a rename
     * cannot dislodge - which is exactly why this one is easy to forget.  An assignment is a name, so
     * without repair it dangles the instant the locomotive is renamed, and the next rebuild reports it
     * missing from the database and drops it.  Driven through renameLoc rather than the layout method
     * directly, because the wiring is the part that would be missing.
     */
    @Test
    public void testRenamingALocomotiveKeepsItsAssignment() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        layout.setHomeLocomotive("HS D", LOC_A);

        String renamed = LOC_A + " renamed";

        assertTrue(model.renameLoc(LOC_A, renamed), "precondition: the rename must succeed");

        try
        {
            assertEquals(layout.getPoint("HS D").getHomeLoc(), renamed,
                "the assignment is held by name, so a rename has to be followed through");

            // Only a rebuild proves it: that is what resolves names, and what would have dropped a
            // stale one
            layout.rebuildHomeStations();

            assertEquals(layout.getPoint("HS D").getHomeLoc(), renamed,
                "and it survives being re-derived rather than being reported missing and dropped");
            assertEquals(layout.getHomeStation(model.getLocByName(renamed)), layout.getPoint("HS D"));
        }
        finally
        {
            // The database here is the real one, so the name goes back whatever happened above.  Not
            // asserted: an assertion in a finally replaces whatever failure it is cleaning up after,
            // and tearDownClass deletes by the original name, so a failure to restore is not silent.
            model.renameLoc(renamed, LOC_A);
        }
    }

    /**
     * Renaming a station keeps its assignment, which holds only because the point object survives.
     */
    @Test
    public void testRenamingAStationKeepsItsAssignment() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.setHomeLocomotive("HS D", LOC_A);
        layout.renamePoint("HS D", "HS Depot");

        assertEquals(layout.getPoint("HS Depot").getHomeLoc(), LOC_A);
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS Depot"));
    }

    /**
     * An assignment stores the locomotive name exactly as it was given.
     *
     * Locomotive names are only checked for being blank when they are created, never trimmed, so
     * surrounding space is part of the name.  Trimming it when it is stored would produce a name that
     * matches no locomotive - and the next rebuild would report it missing from the database and
     * silently drop the assignment, for a locomotive that is sitting right there.
     */
    @Test
    public void testAnAssignmentDoesNotAlterTheNameItIsGiven()
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        d.setHomeLoc("  padded name  ");

        assertEquals(d.getHomeLoc(), "  padded name  ",
            "a name is stored as given - trimming it would stop it matching its own locomotive");

        d.setHomeLoc("   ");
        assertNull(d.getHomeLoc(), "but blank is how a station says it has no locomotive of its own");

        d.setHomeLoc(null);
        assertNull(d.getHomeLoc());
    }

    /**
     * A station knows which locomotives it could never hold, before the assignment is made.
     *
     * Every one of these makes Return Home report IMPOSSIBLE forever after, and the advice that dialog
     * gives is to check the track - which is the wrong remedy, because nothing about the track is at
     * fault.  The chooser asks this question up front so the operator hears it then rather than later.
     * It warns rather than refuses: the same state is reachable by editing the station afterwards, and
     * assigning homes before configuring the stations is not a mistake.
     *
     * Asked through the planner’s own rule rather than a second copy of it: the answer given when the
     * assignment is made has to be the answer Return Home gives later, or the warning is theatre.
     */
    @Test
    public void testAStationKnowsWhichLocomotivesItCouldNeverHold() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        // Point state dies with the layout, which every load() replaces - but locomotive state does not,
        // because load() re-parses the graph and not the database.  Whatever this test changes about the
        // locomotive has to go back even if an assertion below fails, or the next test in the class runs
        // against a locomotive this one quietly left non-reversible.
        final boolean wasReversible = loc(LOC_A).isReversible();
        final Integer wasLength = loc(LOC_A).getTrainLength();

        assertTrue(HomeStaging.canBeHome(loc(LOC_A), d), "precondition: an ordinary station accepts it");

        try
        {
            // Excluded by the station
            d.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));
            assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "a station that excludes it cannot be its home");
            d.setExcludedLocs(new HashSet<Locomotive>());

            // Longer than the station allows
            loc(LOC_A).setTrainLength(10);
            d.setMaxTrainLength(5);
            assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "a train too long to stop there cannot rest there");
            d.setMaxTrainLength(0);
            assertTrue(HomeStaging.canBeHome(loc(LOC_A), d), "and no limit means no objection");

            // A terminus it cannot reverse out of
            loc(LOC_A).setReversible(false);
            d.setTerminus(true);
            assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "a non-reversible locomotive cannot end at a terminus");
            loc(LOC_A).setReversible(true);
            assertTrue(HomeStaging.canBeHome(loc(LOC_A), d), "but a reversible one can");
            d.setTerminus(false);

            // Switched off
            d.setActive(false);
            assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "an inactive station is not a destination for anything");
            d.setActive(true);
        }
        finally
        {
            loc(LOC_A).setReversible(wasReversible);
            loc(LOC_A).setTrainLength(wasLength);
        }
    }

    /**
     * And the warning agrees with what Return Home would then do: report a permanent IMPOSSIBLE.
     *
     * This is the reason the chooser delegates rather than restating the rules - the two answers are
     * the same answer, and this test fails if they ever stop being.
     */
    @Test
    public void testAnImpossibleAssignmentIsExactlyWhatReturnHomeWouldRefuse() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        d.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        assertFalse(HomeStaging.canBeHome(loc(LOC_A), d), "precondition: the chooser would warn about this");

        // Made anyway, which the chooser permits and a hand-edited file does not even ask about
        layout.setHomeLocomotive("HS D", LOC_A);

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "what the chooser warns about is exactly what the planner calls impossible");
        assertTrue(plan.getBlocked().contains(loc(LOC_A)), "and it names the locomotive concerned");

        d.setExcludedLocs(new HashSet<Locomotive>());
    }

    /**
     * A retired Layout refuses a path outright rather than part-running it.
     *
     * Reloading the autonomy file builds a new Layout, and the version counter is static, so the old
     * one is retired while whatever holds it carries on.  Every speed write was already fenced, so no
     * train moved - but the path was locked and every switch and signal on it commanded first, and the
     * fence-abort then returned before the completion block that removes the locomotive from
     * activeLocomotives.  That strand is what left isRunning() true for the rest of the session.
     */
    @Test
    public void testARetiredLayoutRefusesToRunAPath() throws Exception
    {
        Layout retired = load(ring(LOC_A, LOC_B, null));

        List<List<Edge>> paths = retired.getPossiblePaths(loc(LOC_A), true);

        assertFalse(paths.isEmpty(), "precondition: the fixture must offer this locomotive somewhere to go");

        List<Edge> path = paths.get(0);

        // Parsing again builds a new Layout, which is what a reload does - and the version counter is
        // static, so this retires the one above
        load(ring(LOC_A, LOC_B, null));

        assertFalse(retired.isCurrentLayout(), "precondition: the first layout must now be retired");

        assertFalse(retired.executePath(path, loc(LOC_A), 30, null),
            "a retired layout must refuse the path rather than locking it and aborting part way");

        assertTrue(retired.getActiveLocomotives().isEmpty(),
            "and must strand nothing in activeLocomotives - a strand there is what made isRunning() "
            + "true for the rest of the session");
    }

    /**
     * A retired Layout stops executing its timetable instead of waiting for a train that will never run.
     *
     * The dispatch loop waits for the entry ahead to leave activeLocomotives before starting the next
     * one.  On a retired Layout that entry is the strand above, nothing reachable from the UI can call
     * stopLocomotives() on a graph getAutoLayout() no longer resolves to, and the executor never
     * returned - so the staging worker holding it never reached its finally, and every surface asking
     * isAutonomyBusy answered "trains are moving" until TrainControl was restarted.
     *
     * The timeout is the assertion: without the fence this call does not come back.
     */
    @Test(timeOut = 30000)
    public void testARetiredLayoutStopsExecutingItsTimetable() throws Exception
    {
        Layout retired = load(ring(LOC_A, LOC_B, null));

        // The swap arrangement, because a single move never reaches the wait that spins
        assertTrue(retired.moveLocomotive(LOC_A, "HS C", false));
        assertTrue(retired.moveLocomotive(LOC_B, "HS A", false));
        assertTrue(retired.moveLocomotive(LOC_A, "HS B", false));

        HomeStaging.Plan plan = retired.loadReturnToHomeTimetable();

        assertTrue(plan.isPossible(), "precondition: there must be a plan to execute");
        assertTrue(plan.getMoves().size() > 1, "precondition: one move never reaches the sequential wait");
        assertTrue(retired.isTimetableSequential(), "precondition: a staged plan runs one train at a time");

        load(ring(LOC_A, LOC_B, null));

        assertFalse(retired.isCurrentLayout(), "precondition: the first layout must now be retired");

        retired.executeTimetable();

        assertTrue(retired.getActiveLocomotives().isEmpty(),
            "a retired layout must dispatch nothing at all");
    }


    /**
     * The model can see a staging flow, not merely a dispatched run.
     *
     * Six guards ask the *model* whether autonomy is busy - locomotive delete, rename, address change,
     * the rename-proposal pass, the diagram save, and the sync's address adoption - and the model
     * answered on isRunning() alone.  Nothing is dispatched while a plan is being derived, so all six
     * read the planning window as idle: a locomotive could be deleted out from under a plan about to
     * drive it, or re-addressed so the plan's commands reach a decoder the train no longer answers.
     *
     * The flag has to live on the Layout rather than the UI, because the sync deferral is model-side
     * and no UI predicate can ever cover it.
     */
    @Test
    public void testTheModelSeesAStagingFlowAsAutonomyRunning() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assertFalse(model.isAutonomyRunning(), "precondition: nothing is running or being planned");

        layout.setStagingInProgress(true);

        assertTrue(layout.isStagingInProgress());
        assertTrue(model.isAutonomyRunning(),
            "the model has to see a staging flow, or every model-side guard treats the planning "
            + "window as idle");

        layout.setStagingInProgress(false);

        assertFalse(model.isAutonomyRunning(), "and stop seeing it once the flow is done");
    }

    /**
     * The timetable can be read as a snapshot, while the getter stays live for the callers that edit it.
     *
     * Locomotive threads append to the timetable under the Layout monitor whenever capture is on during
     * a run, and the EDT iterates the same list to repaint - the table, and the legend that marks a
     * locomotive at its timetable start.  Neither reader holds the monitor, and both use iterators.
     *
     * The getter cannot simply return a copy: deleteTimetableEntry removes from the list it hands back,
     * so a snapshot there would make that deletion apply to nothing.  Hence two accessors, and this
     * pins the difference between them.
     */
    @Test
    public void testTheTimetableSnapshotIsACopyWhileTheGetterStaysLive() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        giveTimetable(layout, LOC_A);

        List<TimetablePath> snapshot = layout.getTimetableSnapshot();
        int before = snapshot.size();

        assertTrue(before > 0, "precondition: the fixture must have given us a timetable");

        // What deleteTimetableEntry does - it mutates the list the getter returns
        layout.getTimetable().remove(0);

        assertEquals(snapshot.size(), before,
            "a snapshot handed to a repaint must not change underneath the repaint");
        assertEquals(layout.getTimetable().size(), before - 1,
            "while the getter stays live, which is what the editing surfaces depend on");
    }

    /**
     * getHomeStations hands back a snapshot, not a window onto the live map.
     *
     * The planner reads this on a worker thread, while hand placement, station deletion, locomotive
     * deletion and a wholesale rebuild all write to it from elsewhere.  As a view it left that read
     * walking a map another thread was clearing: a ConcurrentModificationException in a worker with
     * nothing to catch it, or the quieter outcome of a plan built from half the homes.
     *
     * Pinned because turning it back into a view is a one-word change that nothing else notices until a
     * run dies in the middle of planning.
     */
    @Test
    public void testGetHomeStationsIsASnapshotRatherThanALiveView() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        Map<Locomotive, Point> taken = layout.getHomeStations();
        int before = taken.size();

        assertEquals(taken.get(loc(LOC_A)), layout.getPoint("HS A"), "precondition: derived from placement");

        // Two different writers, either of which a view would expose to a reader mid-iteration
        layout.setHomeLocomotive("HS D", LOC_A);                  // rebuild: clear and repopulate
        assertTrue(layout.moveLocomotive(LOC_C, "HS C", false));   // claimHome: a put

        assertEquals(taken.size(), before, "a map handed out earlier must not grow underneath its reader");
        assertEquals(taken.get(loc(LOC_A)), layout.getPoint("HS A"), "nor change what it already said");

        assertEquals(layout.getHomeStations().get(loc(LOC_A)), layout.getPoint("HS D"),
            "while a fresh read does see the change");
        assertEquals(layout.getHomeStations().size(), before + 1);
    }
}
