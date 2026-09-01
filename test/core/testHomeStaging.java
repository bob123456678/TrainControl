package core;

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
     *
     * "Free" means two things, and it used to check only the first (FSR-C1). `moveLocomotive` PLACES a
     * locomotive - it does not consult `getBlockedBy` and it does not refuse - so replaying through it
     * cannot notice a move into a station FR-001 is holding back, which is exactly the fault OB-073 was
     * about. Under a mutation that reintroduced OB-073, the replay ran clean and the failure came from
     * a different assertion entirely, while four separate comments credited this method with catching
     * it.
     *
     * So the FR-001 condition is asserted here, against the state at the moment the move runs, the same
     * question `isPathClear` asks of a path's destination.
     *
     * **It asks it by calling the rule, not by restating it (DR-B2).** The hand-written version here
     * was the third and weakest of three copies: it asked `getCurrentLocomotive` rather than the block,
     * so a train on another copy of the watched square was invisible to the oracle grading the two
     * production copies; and it had no exemption for the DEPARTING train, so a legal plan whose move
     * *is* the train leaving the watched square would have failed the test. Both production copies
     * exempt it - Adam: "The condition should not apply to trains leaving, only departing" - so the
     * oracle was forbidding an arrival the railway allows.
     *
     * `Point.heldBackBy(end, loc)` is the live-block variant, which is exactly what `isPathClear` asks.
     * Being the same call is the point: an oracle that restates the rule can only ever grade the copies
     * against a fourth opinion.
     */
    private static void applyPlan(Layout layout, HomeStaging.Plan plan)
    {
        for (HomeStaging.Move move : plan.getMoves())
        {
            org.traincontrol.automation.Point end = layout.getPoint(move.getEnd().getName());

            assertNull(end.getCurrentLocomotive(),
                "move \"" + move + "\" sends a locomotive into an occupied station");

            org.traincontrol.automation.Point watched = org.traincontrol.automation.Point.heldBackBy(
                end, move.getLocomotive());

            assertNull(watched,
                "move \"" + move + "\" sends a locomotive into a station that is held back while "
                + (watched == null ? "" : watched.getName()) + " is occupied, and it is occupied by "
                + (watched == null ? "" : watched.getBlockLocomotive())
                + ". isPathClear refuses that arrival, so the run would retry until it gave up and "
                + "stop with the fleet half-staged - which is OB-073, exactly");

            assertTrue(
                layout.moveLocomotive(move.getLocomotive().getName(), move.getEnd().getName(), false),
                "move \"" + move + "\" was rejected by the model");
        }
    }

    private static void assertEveryoneHome(Layout layout)
    {
        assertFalse(layout.getHomeStations().isEmpty(),
            "precondition: there must be homes to check, or the loop below runs zero times and "
            + "\"everyone home\" is vacuously true");

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
        assertEquals(layout.getPoint("HS C").getHomeLoc(), loc(LOC_A));
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

        assertEquals(reloaded.getPoint("HS D").getHomeLoc(), loc(LOC_A));
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

        assertEquals(layout.getPoint("HS D").getHomeLoc(), loc(LOC_C), "precondition: the assignment was made");

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
            assertSame(layout.getPoint("HS D").getHomeLoc(), model.getLocByName(renamed),
                "the assignment is the LOCOMOTIVE, so a rename is nothing it has to be told about - "
                + "the object it points at is the object that was renamed");

            // Only a rebuild proves it: that is what resolves names, and what would have dropped a
            // stale one
            layout.rebuildHomeStations();

            assertSame(layout.getPoint("HS D").getHomeLoc(), model.getLocByName(renamed),
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

        assertEquals(layout.getPoint("HS Depot").getHomeLoc(), loc(LOC_A));
        assertEquals(layout.getHomeStation(loc(LOC_A)), layout.getPoint("HS Depot"));
    }

    /**
     * An assignment is the locomotive itself, so there is no name to get wrong.
     *
     * **This test used to be about a string.** It required an assignment to store the name exactly as
     * given, because locomotive names are never trimmed and surrounding space is part of the name - so
     * trimming on the way in produced a name matching no locomotive, and the next rebuild reported it
     * missing from the database and dropped the assignment for a locomotive sitting right there.
     *
     * A Point holds the LOCOMOTIVE now, so none of that is representable: there is nothing to trim,
     * nothing to mismatch, and no blank-versus-null distinction to get on the wrong side of. What is
     * left to assert is the property that replaced it - the object goes in and the same object comes
     * back, whatever it is called.
     */
    @Test
    public void testAnAssignmentIsTheLocomotiveItself()
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        Locomotive alpha = loc(LOC_A);

        d.setHomeLoc(alpha);

        assertSame(d.getHomeLoc(), alpha,
            "the assignment is not the locomotive that was given to it");

        // Renaming is the case this refactor exists for: the object is the same object, so the
        // assignment needs no repair and cannot be left naming something that is gone.
        String was = alpha.getName();

        try
        {
            alpha.rename("HS alpha renamed");

            assertSame(d.getHomeLoc(), alpha,
                "the assignment stopped pointing at its locomotive when the locomotive was renamed. "
                + "That is the whole reason this is an object rather than a name");
        }
        finally
        {
            alpha.rename(was);
        }

        d.setHomeLoc(null);

        assertNull(d.getHomeLoc(), "null is how a station says it has no locomotive of its own");
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

            // A terminus, which is NOT a reason to refuse a home (Adam, 2026-08-31).
            //
            // These two lines asserted the opposite until then: "a non-reversible locomotive cannot
            // end at a terminus". His ruling - "trains should be allowed to back into terminuses if
            // they are not reversible (that's why we have the reversing point at feedback 2013)" -
            // moves that question to the route, where isPathClear can see whether the way there turns
            // the train round. Most parking berths are terminuses.
            loc(LOC_A).setReversible(false);
            d.setTerminus(true);
            assertTrue(HomeStaging.canBeHome(loc(LOC_A), d),
                "a parking berth was refused as a home to a train that cannot reverse");
            loc(LOC_A).setReversible(true);
            assertTrue(HomeStaging.canBeHome(loc(LOC_A), d), "and a reversible one, as always");
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
     * A derived home is not an assignment, and the display must not treat them alike.
     *
     * getHomeStation answers with the positional fallback as well as the assignments, which is right
     * for the planner - Return Home moves a locomotive back to where it started whether or not anyone
     * said so.  It is wrong for the locomotive list, which paints "standing at home" teal: on a graph
     * nobody has assigned anything on, every placed locomotive is standing on its derived home, so the
     * whole list went teal while the graph - which reads the assignments - outlined nothing.
     *
     * Assigning one locomotive elsewhere then moves the count for locomotives nobody touched, because
     * an assignment makes rebuildHomeStations refuse the fallback claims that collide with it.  That is
     * the shape of the reported bug, and this pins the difference the two readings produce.
     */
    @Test
    public void testADerivedHomeIsNotAnAssignment() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, LOC_C));

        assertFalse(layout.hasHomeLocomotives(), "precondition: this graph assigns nothing");

        for (Point point : layout.getPoints())
        {
            assertNull(point.getHomeLoc(), point.getName() + " carries no assignment");

            if (point.getCurrentLocomotive() != null)
            {
                assertEquals(layout.getHomeStation(point.getCurrentLocomotive()), point,
                    point.getName() + " is nonetheless its occupant's derived home");
            }
        }

        // One locomotive given a station it is not standing on - HS D is empty, LOC_A is at HS A
        layout.setHomeLocomotive("HS D", LOC_A);

        int atAssignedHome = 0;
        int atDerivedHome = 0;

        for (Point point : layout.getPoints())
        {
            Locomotive here = point.getCurrentLocomotive();

            if (here == null) continue;

            if (here.equals(point.getHomeLoc())) atAssignedHome++;
            if (point.equals(layout.getHomeStation(here))) atDerivedHome++;
        }

        assertEquals(atAssignedHome, 0,
            "nothing is standing on a station that was assigned to it, which is what the graph outlines "
            + "and what the locomotive list must agree with");

        assertEquals(atDerivedHome, 2,
            "while two locomotives stand on homes that were only ever derived - the reading that used "
            + "to paint them teal against a graph showing nothing");
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

    // =============================================================================================
    // Point types in the path, rather than only at its end
    //
    // The suite pinned canBeHome - the rule for where a locomotive may COME TO REST - and nothing at
    // all about what it may drive through.  These cover the traversal half: terminus, reversing,
    // non-station, exclusions and shared sensors, in the middle of a route rather than at the end.
    // =============================================================================================

    /**
     * The four-station ring, with extra JSON spliced onto any of its stations.
     *
     * Named apart from ring(String, String, String) rather than overloading it: three nulls match both
     * signatures and neither is more specific, so ring(null, null, null) stopped compiling.
     */
    private static String ringWith(String[] locs, String[] extras)
    {
        return ringWith(locs, extras, new int[]{0, 1, 2, 3});
    }

    /**
     * The ring with explicit sensor offsets, so two stations can be given one address.
     *
     * A separate argument rather than another entry in extras: station() already emits an s88, and
     * splicing a second one produced a duplicate key and a graph that would not parse at all.
     */
    private static String ringWith(String[] locs, String[] extras, int[] s88Offsets)
    {
        StringBuilder points = new StringBuilder();

        for (int i = 0; i < 4; i++)
        {
            String raw = station("HS " + (char) ('A' + i), s88Offsets[i], locs[i]);

            if (extras[i] != null)
            {
                // Splice before the closing brace, and assert it landed - a fixture that silently
                // dropped the flag would make every test below pass while testing nothing
                assertTrue(raw.endsWith("}"), "station JSON shape changed: " + raw);
                raw = raw.substring(0, raw.length() - 1) + ", " + extras[i] + "}";
            }

            if (i > 0) points.append(",");

            points.append(raw);
        }

        String config = json("{'points': [" + points + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("HS C", "HS D") + "," + edge("HS D", "HS C") + ","
            + edge("HS D", "HS A") + "," + edge("HS A", "HS D")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");

        for (String extra : extras)
        {
            if (extra != null) assertTrue(config.contains(json(extra)), "fixture lost " + extra);
        }

        return config;
    }

    /**
     * Three mutually connected stations, the third a terminus.
     *
     * Used where the terminus has to be the ONLY place a train can step aside to.  Carving that out of
     * the ring with exclusions would have worked today and broken the moment exclusions start applying
     * to stations driven through - this says what it means instead.
     */
    private static String triangleWithTerminus(String locAtA, String locAtB)
    {
        String t = station("HS T", 2, null);

        assertTrue(t.endsWith("}"), "station JSON shape changed: " + t);

        return json("{'points': ["
            + station("HS A", 0, locAtA) + ","
            + station("HS B", 1, locAtB) + ","
            + t.substring(0, t.length() - 1) + ", 'terminus': true}"
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS T") + "," + edge("HS T", "HS B") + ","
            + edge("HS T", "HS A") + "," + edge("HS A", "HS T")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A blocker with a home of its own does not make staging impossible.
     *
     * FBR-B1, from the Fable review of this round, and it is my own OB-073 fix being wrong in the
     * half its own note said was doing the work.
     *
     * The impossibility scan proves one thing: "no move can ever end there". Every test in it is
     * state-INDEPENDENT for that reason - inactive origin, exclusion, length, terminus, disconnection -
     * and `connected`, one method below the scan, says the rule out loud: "Deliberately blind to
     * occupancy... A route blocked merely by another train is not impossible - moving that train is
     * exactly what the planner is for."
     *
     * The OB-073 fix put the state-AWARE `canRest(l, home, this.start)` into that scan. It reads the
     * starting occupancy, so a locomotive standing on a watched square proved the goal unreachable -
     * including when that locomotive is itself being staged somewhere else, and the plan's own first
     * move vacates the square. IMPOSSIBLE is shown to the operator as a proof, with the blocked
     * locomotives named, and it skips the search entirely.
     *
     * The fixture the round shipped could not catch it: its blocker has no home, so it genuinely
     * cannot be moved and IMPOSSIBLE is right for it. This one gives the blocker a home elsewhere,
     * which is the difference between "in the way" and "stuck".
     *
     * Same shape as the last two of these: a rule copied to a place whose contract did not hold the
     * precondition that made it safe.
     */
    @Test
    public void testABlockerWithAHomeOfItsOwnIsNotAProofOfImpossibility() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // A wants HS B, which is held back while HS D is occupied.
        layout.getPoint("HS B").setBlockedBy(
            java.util.Arrays.asList(layout.getPoint("HS D")));

        // FBR-D19: without this the fixture could stop taking and the test would pass vacuously, on a
        // station nothing is watching.
        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS B this tests nothing at all");

        assign(layout, LOC_A, "HS B");

        // And B has a home of its own, which is NOT the square it is standing on. Staging will move
        // it, and moving it clears the square A is waiting for.
        assign(layout, LOC_B, "HS C");

        assertTrue(layout.moveLocomotive(LOC_A, "HS A", false), "the fixture could not be arranged");
        assertTrue(layout.moveLocomotive(LOC_B, "HS D", false),
            "could not stand the blocker on the watched point");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "the planner called a movable blocker a proof of impossibility. B is standing on the "
            + "square that holds A's home back, and B is being staged to HS C - so the first move of "
            + "the plan clears it. IMPOSSIBLE is presented to the operator as a proof, with the "
            + "locomotives named, and it skips the search that would have found the two-move answer "
            + "(FBR-B1).  Got: " + plan.getOutcome());

        assertFalse(plan.getBlocked().contains(loc(LOC_A)),
            "and A must not be named as blocked when what is in its way is about to leave");
    }

    /**
     * Two homes that hold each other back are impossible, and the SCAN says so (OB-085).
     *
     * HS C is held back while HS D is occupied; HS D is held back while HS C is occupied; and the two
     * are the homes of the two locomotives on the layout. In any finished arrangement each train
     * stands on its own home, and therefore on the square that closes the other station - so whichever
     * of the two arrives last finds its station held back by a train that is already parked. No
     * ordering works, and no occupancy has to be read to know it.
     *
     * This is the counterexample to the sentence that stood in `plan()` for a day: "no
     * state-independent statement can be made about an FR-001 blocker". One can, and this is it.
     *
     * **The assertion that the SCAN rather than the search answers** is the outcome itself, and that
     * is the whole point of the ticket. IMPOSSIBLE is a proof - it names the locomotives - while
     * NO_PLAN_FOUND says "no arrangement found, it may still be possible". Without the scan the search
     * exhausts its budget and answers the second, so the two are distinguishable by more than timing.
     *
     * MUTATION-CHECKED: deleting the cycle scan from `plan()` fails this test and no other, with
     * exactly the message the ticket predicted - "Got: NO_PLAN_FOUND expected [IMPOSSIBLE]". So the
     * scan is what answers here, not the search reaching the same conclusion by a slower road.
     */
    @Test
    public void testTwoHomesThatHoldEachOtherBackAreImpossible() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assign(layout, LOC_A, "HS C");
        assign(layout, LOC_B, "HS D");

        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));
        layout.getPoint("HS D").setBlockedBy(Arrays.asList(layout.getPoint("HS C")));

        assertEquals(layout.getPoint("HS C").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS C there is no cycle to find");
        assertEquals(layout.getPoint("HS D").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS D there is no cycle to find");

        assertNotEquals(layout.getPoint("HS C"), locationOfLoc(layout, LOC_A),
            "precondition: a train already on its home never arrives, and nothing would be checked");
        assertNotEquals(layout.getPoint("HS D"), locationOfLoc(layout, LOC_B),
            "precondition: a train already on its home never arrives, and nothing would be checked");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "two homes each held back by the other is provable from the graph alone, so this should "
            + "be a proof and not a budget running out.  Got: " + plan.getOutcome());

        assertTrue(plan.getBlocked().contains(loc(LOC_A)) && plan.getBlocked().contains(loc(LOC_B)),
            "an IMPOSSIBLE verdict names the locomotives that cannot be helped, and both of these are "
            + "in the cycle.  Got: " + plan.getBlocked());
    }

    /**
     * A sensor shared with an approach guard does not make an ordinary layout impossible (OB-085).
     *
     * **The third thing put into this scan that was wrong, and the review that found it built exactly
     * the counterexample the ticket asked for.** The scan asked "would a train standing there close
     * this station" through the planner's `sameTrackAs`, which unions block copies AND the points
     * reporting the same feedback address. The sensor half is the planner being conservative on
     * purpose, and its own javadoc prices that conservatism honestly: it costs a refused plan, never a
     * wrong movement.
     *
     * A refused plan and a PROOF are not the same claim. IMPOSSIBLE names locomotives and asserts no
     * arrangement exists, so it may only be built out of the relation the railway enforces - the
     * block. Built out of the wider one, this fixture came back IMPOSSIBLE with both locomotives
     * named, for an arrangement the railway performs in two moves.
     *
     * The two tests already here could not see it: both build their restrictions out of direct Point
     * references, so neither reaches `sameTrackAs` at all. The control that exists precisely to stop
     * this over-claim was blind to the way it actually happened - which is the same lesson as the
     * first two attempts, arriving a third time.
     *
     * MUTATION: putting `sameTrackAs` back in place of `blockCopiesOf` inside `watchesTrack` fails
     * this test, with IMPOSSIBLE and both locomotives named.
     */
    @Test
    public void testASharedSensorDoesNotMakeAnOrdinaryLayoutImpossible() throws Exception
    {
        Layout layout = load(twoHomesAndAGuardSharingASensor());

        assign(layout, LOC_A, "HS C");
        assign(layout, LOC_B, "HS D");

        // One way: HS C waits for HS D to be clear.
        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));

        // The other way is about the GUARD, not about HS C.
        layout.getPoint("HS D").setBlockedBy(Arrays.asList(layout.getPoint("HS W2")));

        assertEquals(layout.getPoint("HS W2").getS88(), layout.getPoint("HS C").getS88(),
            "the fixture did not take: the guard has to share HS C's feedback for this to be the "
            + "case that went wrong");

        assertNull(layout.getPoint("HS W2").getBlock(),
            "the fixture did not take: the guard must NOT be a block copy of anything, or this is a "
            + "different case entirely");

        assertNotEquals(layout.getPoint("HS W2"), layout.getPoint("HS C"),
            "the fixture did not take: they have to be different squares");

        // What the railway says, asked in the order the arrangement is built - the same call
        // isPathClear makes.
        assertNull(Point.heldBackBy(layout.getPoint("HS C"), loc(LOC_A)),
            "precondition: with HS D empty the railway lets a train into HS C, so an arrangement "
            + "exists and IMPOSSIBLE would be a false claim");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "an ordinary layout - one one-way hold, and an approach guard sharing a feedback address "
            + "with the other platform - was declared impossible.  The railway stages it: park at "
            + "HS C while HS D is empty, then park at HS D.  Blocked: " + plan.getBlocked());
    }

    /**
     * The cycle scan does not name a train that was already stuck for its own reasons (OB-085).
     *
     * The third over-claim found in this scan, and this one by rereading it rather than by a test -
     * which is worth recording, because the first two also looked obviously right.
     *
     * The cycle argument is "whichever of the two arrives last finds the other already parked on the
     * square that holds it". It rests on both of them actually parking. If one can never get home at
     * all - no route, a home it cannot rest at - then it never parks there, the square stays clear,
     * and the other is free to arrive.
     *
     * The plan is impossible either way, so no outcome changes. What changes is the LIST, and the list
     * is the part the operator reads: naming a locomotive that could get home perfectly well sends
     * them looking for a fault that is not there.
     *
     * MUTATION: removing the `unreachable.contains(...)` guard from the cycle scan fails this test.
     */
    @Test
    public void testACycleDoesNotNameATrainThatWasAlreadyStuck() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assign(layout, LOC_A, "HS C");
        assign(layout, LOC_B, "HS D");

        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));
        layout.getPoint("HS D").setBlockedBy(Arrays.asList(layout.getPoint("HS C")));

        // LOC_B cannot rest at HS D whatever anybody else does, so it never parks there - which means
        // HS C is never actually held back.
        layout.getPoint("HS D").setActive(false);

        assertFalse(layout.getPoint("HS D").isActive(),
            "the fixture did not take: HS D has to be somewhere LOC_B cannot come to rest");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "LOC_B has a home it cannot rest at, so this arrangement really is impossible");

        assertTrue(plan.getBlocked().contains(loc(LOC_B)),
            "the locomotive that genuinely cannot get home is not named.  Got: " + plan.getBlocked());

        assertFalse(plan.getBlocked().contains(loc(LOC_A)),
            "a locomotive that could park at HS C perfectly well - because the train supposedly "
            + "holding it back can never arrive - was named as blocked.  That sends the operator "
            + "looking for a fault that is not there.  Got: " + plan.getBlocked());
    }

    /**
     * A hold in ONE direction is an ordering, not an impossibility (OB-085).
     *
     * The control, and the assertion that matters most in this pair. The scan above proves a cycle;
     * the identical fixture with one of the two restrictions removed is perfectly solvable - park at
     * HS C first, while HS D is still empty, then park at HS D, which nothing watches - and a scan
     * that reported it impossible would be refusing arrangements the railway can actually make.
     *
     * That is the failure mode this scan is prone to and the ticket warned about: the last two things
     * put into it were both wrong, both looked obviously right, and both shipped with a test that
     * could not tell the difference. A test for the cycle alone cannot tell a scan that proves cycles
     * from one that refuses any blockedBy list.
     *
     * MUTATION-CHECKED: dropping either of the two `watchesTrack` tests from the scan - so that one
     * direction is enough - fails this test, and also fails
     * testAHomeHeldBackByAnOccupiedPointStillGetsAnExecutablePlan, which was already here. Two tests
     * for one over-claim is the right number: that one says the plan still executes, this one says the
     * verdict is not a refusal, and a scan could break either without the other.
     */
    @Test
    public void testAOneWayHoldIsJustAnOrdering() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        assign(layout, LOC_A, "HS C");
        assign(layout, LOC_B, "HS D");

        // One direction only.
        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));

        assertTrue(layout.getPoint("HS D").getBlockedBy().isEmpty(),
            "the fixture did not take: this test is about there being NO cycle");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "a station held back by another station's square is an ordering constraint, not a "
            + "contradiction - park at HS C while HS D is empty, then park at HS D.  Reporting this "
            + "impossible refuses an arrangement the railway can make");
    }

    /**
     * Two trains already standing on their own homes are not in a cycle, whatever watches what.
     *
     * The second way this scan could over-claim. A station's restrictions are read when a train
     * ARRIVES; two trains that are already parked never arrive, so nothing is ever checked and the
     * arrangement stands exactly as it is. Reporting that pair as impossible would call a railway that
     * is already correct unfixable - and it would do it on a layout where nothing is wrong at all.
     *
     * A third locomotive is out of place, so the planner has work to do and cannot answer from
     * `triage()` before the scan is reached. Without it this test would pass by never getting there.
     *
     * MUTATION-CHECKED: deleting the both-already-parked exemption from the scan fails this test.
     */
    @Test
    public void testTwoTrainsAlreadyAtHomeAreNotACycle() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, LOC_C));

        // Each of the two is standing on the home it is given.
        assign(layout, LOC_A, "HS A");
        assign(layout, LOC_B, "HS B");

        // And a third that is not, so there is something for the planner to do.
        assign(layout, LOC_C, "HS D");

        layout.getPoint("HS A").setBlockedBy(Arrays.asList(layout.getPoint("HS B")));
        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS A")));

        assertEquals(layout.getPoint("HS A"), locationOfLoc(layout, LOC_A),
            "the fixture did not take: this test is about trains that are ALREADY home");
        assertEquals(layout.getPoint("HS B"), locationOfLoc(layout, LOC_B),
            "the fixture did not take: this test is about trains that are ALREADY home");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.getBlocked().contains(loc(LOC_A)) || plan.getBlocked().contains(loc(LOC_B)),
            "two trains already on their homes never arrive anywhere, so no restriction is ever "
            + "consulted and neither is unreachable.  Got: " + plan.getBlocked());
    }

    /**
     * Where a locomotive is standing, by name, for the preconditions above.
     *
     * @param layout the layout
     * @param locName the locomotive
     * @return the point it stands on, or null
     */
    private static Point locationOfLoc(Layout layout, String locName)
    {
        for (Point p : layout.getPoints())
        {
            if (p.getCurrentLocomotive() != null && p.getCurrentLocomotive().equals(loc(locName)))
            {
                return p;
            }
        }

        return null;
    }

    /**
     * Saving a layout that has not changed produces the same file it produced last time.
     *
     * Not a data defect - the set is the same set - but it cost three real things, and the third is
     * how it was found. `Point.excludedLocs` is a set of Locomotive objects; Locomotive does not
     * override hashCode, so iteration order is identity hashes and differs on every run of the JVM.
     * Merely opening a layout and saving it rewrote the array in a new order, and the whole
     * configuration file came out different with nothing changed.
     *
     * The cost: a sync on every launch for a layout that lives in OneDrive; a diff that says something
     * happened when nothing did; and a test that opens the window quietly rewriting Adam’s own
     * railway, which is what put this on the list at all.
     *
     * Written as "two sets built in opposite orders serialise identically", which is the property,
     * rather than "run it twice and hope the hashes differ" - which is a coin toss dressed as a test.
     *
     * MUTATION: removing the `Collections.sort(locNames)` from `Point.toJSON` fails this.
     *
     * **It did not, for a day.** The two sets below were `HashSet`s, and a HashSet discards insertion
     * order - with three elements in a sixteen-slot table both iterate identically, so this compared
     * two strings that were equal whatever `toJSON` did. Review ran the mutation this javadoc names
     * eight times and got eight passes, then changed one word and got a failure. `LinkedHashSet`
     * keeps the order the fixture is built in, which is the only thing that makes "built in opposite
     * orders" mean anything.
     *
     * The one word is the difference between this test and no test, on the property that stopped the
     * window quietly rewriting Adam's own railway on every launch.
     */
    @Test
    public void testAnUnchangedLayoutSerialisesTheSameWayTwice() throws Exception
    {
        Layout one = load(ring(LOC_A, LOC_B, LOC_C));
        Layout two = load(ring(LOC_A, LOC_B, LOC_C));

        java.util.List<String> names = Arrays.asList(LOC_A, LOC_B, LOC_C);

        // The same three locomotives, added in opposite orders.
        // LinkedHashSet, NOT HashSet - see the javadoc.  A HashSet throws away the very thing this
        // test is built on.
        java.util.Set<Locomotive> forward = new java.util.LinkedHashSet<>();
        java.util.Set<Locomotive> backward = new java.util.LinkedHashSet<>();

        for (String name : names) forward.add(loc(name));

        for (int at = names.size() - 1; at >= 0; at--) backward.add(loc(names.get(at)));

        one.getPoint("HS D").setExcludedLocs(forward);
        two.getPoint("HS D").setExcludedLocs(backward);

        assertEquals(one.getPoint("HS D").getExcludedLocs().size(), 3,
            "the fixture did not take: with fewer than two excluded locomotives there is no order to "
            + "get wrong");

        assertEquals(one.getPoint("HS D").toJSON().toString(),
            two.getPoint("HS D").toJSON().toString(),
            "the same set of excluded locomotives wrote itself out two different ways, so saving a "
            + "layout nothing has changed produces a different file every time");
    }

    /**
     * The home rule at the door it was made about, and not at the other one (LD-8, then MT-165).
     *
     * The assignment door was given the rule and its comment claimed to be "the one door everything
     * comes through". Review walked the other two and it is not:
     *
     *   - `parseAuto` calls `Point.setHomeLoc` directly, so a hand-edited or imported configuration
     *     installed the forbidden home unchallenged - which is the case the model door was added FOR,
     *     since a file cannot be greyed out.
     *   - the POSITIONAL default in `claimHome` makes a square the home of whatever is standing on
     *     it. LD-8 gave that door the same refusal, on the argument that "is the train home?" would
     *     have more than one answer either way.
     *
     * **The second half of that was wrong, and Adam reversed it on 2026-08-31** - "unless there is an
     * explicit home, the home should be where the train started at startup". The two doors are not the
     * same question. An ASSIGNMENT is a person naming a station and there is no way to know which copy
     * they meant; a POSITIONAL home is the graph noticing where a train IS, and the copy is the one
     * under the wheels.
     *
     * What it cost was the feature: ten of his thirty-six station squares carry a block, so no train
     * standing on a main-line platform had a home and Return Home was dark whatever he did.
     *
     * The ambiguity does not vanish, it moves to the far end - a train returning on the other copy -
     * and the third assertion here is what holds that: the copies of a square are one place.
     *
     * MUTATION: putting back `homeAt.setHomeLoc(home)` unconditionally in `parseAuto` fails the first
     * half; putting back the `p.getBlock() != null` return in `claimHome` fails the second; making
     * `atHome` compare Points rather than squares fails the third.
     */
    @Test
    public void testTheHomeRuleReachesTheDoorsHomesActuallyComeFrom() throws Exception
    {
        // The file door: a configuration naming a home on a square that is two graph Points.
        Layout fromFile = load(blockAssigningItsWatchedSquare(LOC_A));

        Point copy = fromFile.getPoint("HS W2");

        assertNotNull(copy.getBlock(),
            "the fixture did not take: this test needs a square emitted as more than one Point");

        // KEPT, as of Adam's ruling of 2026-08-31 (see the test below).
        //
        // This asserted the opposite - that the loader drops such a home - which was LD-8 carrying his
        // 2026-08-25 ruling to the one door a person cannot be warned at. Both halves of that ruling
        // are reversed now: the home is the square, so a square drawn as several Points is an ordinary
        // home and there is nothing to refuse.
        assertEquals(copy.getHomeLoc(), loc(LOC_A),
            "a home named by a configuration file was dropped as it loaded because its square is "
            + "drawn as more than one graph Point. The home is the SQUARE - a train on any copy of it "
            + "is home - so there is nothing ambiguous to refuse, and silently dropping it is a "
            + "Return Home that quietly does something else");

        // And the control: an ordinary square in the same file keeps the home it was given, so the
        // load is not simply dropping homes.
        Layout ordinary = load(ring(LOC_A, LOC_B, LOC_C));

        Point plain = ordinary.getPoint("HS D");

        assertNull(plain.getBlock(), "the control square must not be a multi-point square");

        ordinary.setHomeLocomotive("HS D", LOC_A);

        assertEquals(plain.getHomeLoc(), loc(LOC_A),
            "an ordinary square stopped accepting a home, so the rule refuses more than it was asked");

        ordinary.setHomeLocomotive("HS D", null);

        // The positional door: a train standing on such a square DOES derive a home from it.
        //
        // This asserted the opposite until 2026-08-31, and the reversal is Adam's: "unless there is an
        // explicit home, the home should be where the train started at startup".  Measured on his own
        // graph, ten of his thirty-six station squares carry a block - BottomMainA, BottomMainB,
        // BottomMainC, BottomInner, TopMainR1, TopMainR2 and Tunnel among them - so the rule as it
        // stood meant no train standing on a main-line platform ever had a home, and Return Home was
        // dark whatever he did with it.
        Layout derived = load(blockOfTwoWatching(null, null));

        Point standing = derived.getPoint("HS W2");

        assertNotNull(standing.getBlock(), "the fixture did not take");

        standing.setLocomotive(loc(LOC_A));

        derived.rebuildHomeStations();

        assertTrue(derived.getHomeStations().containsValue(standing),
            "a train standing on a split square was given no home, so Return Home has nothing to "
            + "offer for it - which on Adam's railway is most of the platforms trains stand on");

        // AND THE HALF THAT MAKES IT SAFE: the copies are one place.
        //
        // The ambiguity his ruling was about does not go away, it moves - a train coming back on the
        // far copy of its own platform.  Judged by Point identity it would not be home, and the
        // planner would try to move it to the exact copy, which on a split square means arriving from
        // one particular direction and may be impossible.
        Point otherCopy = derived.getPoint("HS W1");

        assertNotNull(otherCopy.getBlock(), "the fixture did not take: HS W1 must be the other copy");

        assertEquals(otherCopy.getBlock(), standing.getBlock(),
            "the fixture did not take: the two copies must share a block, or this asks nothing");

        standing.setLocomotive(null);
        otherCopy.setLocomotive(loc(LOC_A));

        assertEquals(HomeStaging.snapshot(derived).triage(), HomeStaging.Outcome.ALREADY_HOME,
            "a train standing on the other copy of its own home platform was judged not home.  The "
            + "copies of a square are one piece of track - that is what a block IS - so the planner "
            + "would have tried to move it onto a particular arrival side of the platform it is "
            + "already standing on");

        otherCopy.setLocomotive(null);
    }

    /**
     * A square that is more than one graph Point cannot be a home (Adam's ruling, 2026-08-25).
     *
     * Asked whether the staging planner should stop treating a shared sensor as one square for FR-001,
     * he dissolved the question instead of answering it: "this is an invalid state - any home with two
     * graph points should be refused."
     *
     * That is the right shape and it is the second time it has been on this feature. The planner and
     * the runtime disagreed about such a square because the square is genuinely ambiguous - "is the
     * train home?" has no single answer when home is two places - so making the configuration
     * impossible removes the disagreement rather than picking a winner for it.
     *
     * `getBlock()` is exactly this test. `AutonomyBuilder` sets it on one condition,
     * `if (nodes.size() > 1) json.put("block", ...)`, so a block is present precisely when a square
     * was emitted as more than one Point.
     *
     * Measured before it was written: on Adam's own layout this refuses ONE square of fifty-seven, and
     * he has no homes assigned at all, so nothing existing is invalidated.
     *
     * Refused at the MODEL door, not only in the menu - a hand-edited file, an imported configuration
     * and the scripting API all come through here and none of them can be greyed out.
     *
     * MUTATION: removing the `p.getBlock() != null` refusal from `Layout.setHomeLocomotive` fails this
     * test.
     */
    @Test
    public void testAHomeCanBeASquareThatIsSeveralPoints() throws Exception
    {
        Layout layout = load(blockOfTwoWatching(null, null));

        Point copy = layout.getPoint("HS W2");

        assertNotNull(copy.getBlock(),
            "the fixture did not take: this test needs a square emitted as more than one Point, "
            + "which is exactly what carrying a block means");

        // ACCEPTED, as of Adam's ruling of 2026-08-31.
        //
        // This test used to require the opposite - `fail(...)` if the assignment went through, and a
        // refusal naming the square. It is kept and inverted rather than deleted, because it is the
        // record of a ruling and the reversal is the interesting part of that record.
        //
        // His 2026-08-25 ruling was "any home with two graph points should be refused", on the
        // argument that "is the train home?" would have more than one answer. His 2026-08-31 ruling
        // settles what a home IS, which is what that argument was really about: "the home should just
        // be the logical point, and the direction is wherever the locomotive was facing when it
        // started moving." One square, one answer, however many arrival sides it is drawn as.
        layout.setHomeLocomotive("HS W2", LOC_A);

        assertEquals(copy.getHomeLoc(), loc(LOC_A),
            "a home on a square drawn as two arrival sides was refused. The home is the square, so "
            + "there is nothing ambiguous about it - and on Adam's own layout this refusal covered "
            + "ten of the thirty-six station squares, which are the main-line platforms");

        layout.setHomeLocomotive("HS W2", null);

        // And the control: an ordinary single-Point square is still assignable, so the rule is not
        // simply refusing homes.
        Point ordinary = layout.getPoint("HS C");

        assertNull(ordinary.getBlock(),
            "the fixture did not take: the control square must NOT be a multi-point square");

        layout.setHomeLocomotive("HS C", LOC_A);

        assertEquals(ordinary.getHomeLoc(), loc(LOC_A),
            "an ordinary square stopped being assignable, so the rule is refusing far more than it "
            + "was asked to");

        layout.setHomeLocomotive("HS C", null);
    }

    /** Assigns homes directly, which is clearer here than rewriting fixture JSON per case. */
    private static void assign(Layout layout, String locName, String stationName) throws Exception
    {
        layout.setHomeLocomotive(stationName, locName);
    }

    /**
     * Sets reversibility on both test locomotives and returns what they were.
     *
     * Locomotive state outlives load(), which re-parses the graph and not the database, so anything
     * changed here has to be put back exactly - restoring a hardcoded "true" would hand every later
     * test a locomotive this one had quietly made reversible.
     */
    private static boolean[] setReversible(boolean state, String... names)
    {
        boolean[] was = new boolean[names.length];

        for (int i = 0; i < names.length; i++)
        {
            was[i] = loc(names[i]).isReversible();
            loc(names[i]).setReversible(state);
        }

        return was;
    }

    private static void restoreReversible(boolean[] was, String... names)
    {
        for (int i = 0; i < names.length; i++) loc(names[i]).setReversible(was[i]);
    }

    // ---------------------------------------------------------------------------------------------
    // Terminus
    // ---------------------------------------------------------------------------------------------

    /**
     * A terminus is a legitimate place to put a train mid-plan.
     *
     * Two locomotives standing on each other's homes can only be unwound by parking one somewhere
     * else first.  Here the only free station is a terminus, so the plan exists if and only if a
     * terminus may be an intermediate stop.  A* offers every active destination as a target and gates
     * it through canRest, which permits a terminus to a reversible locomotive - this pins that, and
     * pins that the train can leave again afterwards.
     */
    @Test
    public void testATerminusCanBeTheEndOfAnIntermediateMove() throws Exception
    {
        Layout layout = load(triangleWithTerminus(LOC_A, LOC_B));

        assertTrue(layout.getPoint("HS T").isTerminus(), "precondition: HS T is the terminus");

        assign(layout, LOC_A, "HS B");
        assign(layout, LOC_B, "HS A");

        // Set rather than asserted: a locomotive is not reversible by default
        boolean[] was = setReversible(true, LOC_A, LOC_B);

        try
        {
            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertTrue(plan.isPossible(),
                "a swap needs one train parked elsewhere, and the terminus is the only elsewhere: " + plan);

            applyPlan(layout, plan);

            boolean usedTerminus = false;

            for (HomeStaging.Move m : plan.getMoves())
            {
                if (m.getEnd().getName().equals("HS T")) usedTerminus = true;
            }

            assertTrue(usedTerminus, "the terminus has to be the station used: " + plan.getMoves());
        }
        finally
        {
            restoreReversible(was, LOC_A, LOC_B);
        }
    }

    /**
     * ...but only for a locomotive that can reverse out of it again.
     *
     * The same fixture with a non-reversible locomotive: the terminus is unusable, so there is nowhere
     * to step aside and no plan exists.  isPathClear refuses a terminus to a non-reversible locomotive,
     * so a planner that offered it would be planning a move the runtime then rejects.
     */
    @Test
    public void testANonReversibleLocomotiveMayBeParkedAtATerminus() throws Exception
    {
        Layout layout = load(triangleWithTerminus(LOC_A, LOC_B));

        assign(layout, LOC_A, "HS B");
        assign(layout, LOC_B, "HS A");

        boolean[] was = setReversible(false, LOC_A, LOC_B);

        try
        {
            // SOMETHING HAS TO TURN THEM ROUND, or the ruling cannot be exercised here.
            //
            // Adam's second ruling of the day - "non-reversing trains have to back in" - means a
            // terminus is reachable for these two only if a reversing point lies on the way. Without
            // one this fixture asks whether the planner refuses an impossible thing, which is a
            // different question.
            layout.getPoint("HS B").setReversing(true);

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            // POSSIBLE, as of Adam's ruling of 2026-08-31.
            //
            // This required the opposite - that neither train could rest at the terminus, so the swap
            // could not be unwound. His ruling: "trains should be allowed to back into terminuses if
            // they are not reversible (that's why we have the reversing point at feedback 2013)."
            //
            // A terminus is no longer a reason a train cannot LIVE somewhere. Whether it can get there
            // is a question about a route, and Layout.isPathClear asks it - a path that passes a
            // reversing point turns the train, so it backs in and leaves forwards.
            assertTrue(plan.isPossible(),
                "a terminus was treated as somewhere a non-reversible train can never rest, which is "
                + "the rule Adam reversed: most parking berths are terminuses, and a train backs into "
                + "one past a reversing point. " + plan);
        }
        finally
        {
            restoreReversible(was, LOC_A, LOC_B);
        }
    }

    /**
     * A terminus may be arrived at but never driven through.
     *
     * The planner refuses to expand a terminus during its search; the runtime reaches the same answer
     * from the other end, by rejecting any path whose intermediate point is a terminus.  Both have to
     * agree, because this one decides whether the answer is IMPOSSIBLE - a claim the planner is only
     * entitled to make when no arrangement of the other trains could help.
     */
    @Test
    public void testAHomeReachableOnlyThroughATerminusIsImpossible() throws Exception
    {
        // A line, not a ring: HS A - HS B - HS C, with the middle station a terminus
        Layout layout = load(ringWith(new String[]{LOC_A, null, null, null},
                                  new String[]{null, "'terminus': true", null, null}));

        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        // The ring still offers HS A -> HS D -> HS C, so this must SUCCEED - the terminus only removes
        // one of the two ways round.  Asserting the reachable case first keeps the test below honest.
        assertTrue(plan.isPossible(), "the long way round the ring is still open: " + plan);

        // Now close the other way round, leaving the terminus as the only route
        Layout blocked = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null).substring(0, station("HS B", 1, null).length() - 1)
                + ", 'terminus': true}" + ","
            + station("HS C", 2, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertTrue(blocked.getPoint("HS B").isTerminus(), "precondition: the only route is through HS B");

        assign(blocked, LOC_A, "HS C");

        HomeStaging.Plan impossible = HomeStaging.snapshot(blocked).plan();

        assertEquals(impossible.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "a terminus cannot be driven through, so HS C is unreachable: " + impossible);

        assertTrue(impossible.getBlocked().contains(loc(LOC_A)),
            "and the locomotive that cannot get home is named");
    }

    /**
     * A locomotive standing on a terminus can still be sent home.
     *
     * The rule is that a terminus may not be an intermediate point; being the point a path STARTS from
     * is expressly allowed, by isPathClear and by the planner seeding its search there.
     */
    @Test
    public void testALocomotiveStartingOnATerminusIsPlannedHome() throws Exception
    {
        Layout layout = load(ringWith(new String[]{null, null, null, LOC_A},
                                  new String[]{null, null, null, "'terminus': true"}));

        assign(layout, LOC_A, "HS B");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertTrue(plan.isPossible(), "a train may leave a terminus: " + plan);

        applyPlan(layout, plan);

        assertEquals(plan.getMoves().size(), 1, "one move, straight home: " + plan.getMoves());
        assertEquals(plan.getMoves().get(0).getEnd().getName(), "HS B");
    }

    // ---------------------------------------------------------------------------------------------
    // Reversing
    // ---------------------------------------------------------------------------------------------

    /**
     * A reversing station is an ordinary station to the planner - drivable through, restable on.
     *
     * Neither the planner nor isPathClear has any rule about reversing points, so this pins the
     * absence: if one is ever added to the runtime, the planner has to learn it in the same change.
     */
    @Test
    public void testAReversingStationIsAnOrdinaryStationToThePlanner() throws Exception
    {
        Layout layout = load(ringWith(new String[]{LOC_A, null, null, null},
                                  new String[]{null, "'reversing': true", null, null}));

        assertTrue(layout.getPoint("HS B").isReversing(), "precondition: HS B reverses");

        // Home two stations away, so the shortest route runs THROUGH the reversing station
        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan through = HomeStaging.snapshot(layout).plan();

        assertTrue(through.isPossible(), "a reversing station may be driven through: " + through);

        // And it may be a home in its own right
        Layout resting = load(ringWith(new String[]{LOC_A, null, null, null},
                                   new String[]{null, "'reversing': true", null, null}));

        assign(resting, LOC_A, "HS B");

        assertTrue(HomeStaging.canBeHome(loc(LOC_A), resting.getPoint("HS B")),
            "a reversing station can be a home");

        assertTrue(HomeStaging.snapshot(resting).plan().isPossible());
    }

    // ---------------------------------------------------------------------------------------------
    // Exclusions on the way past - collision prevention, not just parking
    // ---------------------------------------------------------------------------------------------

    /**
     * A station's exclusion list says where a locomotive may not STOP - not where it may not go.
     *
     * The two lists mean different things on purpose.  On a non-station, exclusion blocks passage, which
     * is the collision constraint.  On a station it blocks parking only, because a station on a through
     * route is exactly where an operator writes "not this one, not here" without wanting to sever the
     * route.
     *
     * Made to block passage as well, briefly, and reverted: on the author's own layout that removed 45%
     * of the reachable station pairs for two locomotives, and Return Home then ran its search out
     * instead of finding the plan that was there before.  A line graph is used here rather than the
     * ring, so passing through HS B is the only way to reach HS C and the test cannot be satisfied by
     * the long way round.
     */
    @Test
    public void testAStationExclusionStopsParkingNotPassing() throws Exception
    {
        Layout line = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + station("HS C", 2, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        Point b = line.getPoint("HS B");

        b.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        assertFalse(HomeStaging.canBeHome(loc(LOC_A), b),
            "the exclusion still forbids parking there");

        assign(line, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(line).plan();

        assertTrue(plan.isPossible(),
            "but HS B may be driven through, and it is the only way to HS C: " + plan);

        applyPlan(line, plan);

        assertEquals(plan.getMoves().get(0).getEnd().getName(), "HS C");
    }

    /**
     * A non-station that excludes the locomotive cannot be passed at all.
     *
     * The other half of the same rule, and the one that carries the collision constraint.  Without it
     * the test above would be satisfied by exclusions meaning nothing anywhere.
     */
    @Test
    public void testANonStationExclusionBlocksPassage() throws Exception
    {
        Layout line = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + "{'name': 'Mid', 'station': false, 's88': " + (S88_BASE + 1) + "}" + ","
            + station("HS C", 2, null)
            + "],'edges': ["
            + edge("HS A", "Mid") + "," + edge("Mid", "HS A") + ","
            + edge("Mid", "HS C") + "," + edge("HS C", "Mid")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        Point mid = line.getPoint("Mid");

        assertFalse(mid.isDestination(), "precondition: Mid is not a station");

        mid.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        assign(line, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(line).plan();

        assertFalse(plan.isPossible(),
            "the only route runs through a non-station this locomotive may not enter: " + plan);
    }


    /**
     * A train parked on a deactivated point still holds its detection section.
     *
     * A section is electrical: taking a siding out of service does not lift the train standing on it
     * off the rails.  The mutual-exclusion rule skipped inactive siblings, so the planner would route
     * a second train into the active twin of that section - and the runtime, reading the real sensor,
     * refuses it partway through the run.
     */
    @Test
    public void testATrainOnAnInactivePointStillClosesItsSection() throws Exception
    {
        // HS A is out of service with LOC_B stored on it, and HS C reports HS A's sensor
        Layout layout = load(ringWith(new String[]{LOC_B, LOC_A, null, null},
                                      new String[]{"'active': false", null, null, null},
                                      new int[]{0, 1, 0, 3}));

        assertFalse(layout.getPoint("HS A").isActive(), "precondition: HS A is out of service");
        assertEquals(layout.getPoint("HS C").getS88(), layout.getPoint("HS A").getS88(),
            "precondition: HS C reports HS A's sensor");

        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.isPossible(),
            "HS C is the far end of a section LOC_B is standing on: " + plan);
    }

    /**
     * A locomotive standing on a deactivated point is not planned home.
     *
     * Every other test here exempts the origin - that is what stops a train's own sensor blocking its
     * own departure - but the runtime does not exempt it from the inactive-point rule, and staging
     * executes with autonomy running.  Planning the move anyway means it is refused at its first edge.
     */
    @Test
    public void testALocomotiveOnAnInactivePointIsNotPlannedHome() throws Exception
    {
        Layout layout = load(ringWith(new String[]{LOC_A, null, null, null},
                                      new String[]{"'active': false", null, null, null}));

        assertFalse(layout.getPoint("HS A").isActive(), "precondition: HS A is out of service");

        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.isPossible(),
            "the runtime would refuse this at the first edge, so it must not be planned: " + plan);

        // IMPOSSIBLE, not NO_PLAN_FOUND.  !isPossible() alone was true before the pre-check learned
        // this rule as well - the search simply found nothing - so it pinned the refusal without
        // pinning the upgrade.  A locomotive that cannot leave where it stands is provably stuck, and
        // saying so costs one flag test instead of the whole budget.
        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "a locomotive that cannot depart is proved stuck, not searched for: " + plan);

        assertTrue(plan.getBlocked().contains(loc(LOC_A)),
            "and it is named, so the operator knows which train to reactivate: " + plan.getBlocked());
    }

    /**
     * A locomotive standing on a point that is not a station is not planned home either (SG-A2).
     *
     * The rule the planner was missing sits in isPathClear one `if` after the inactive-point rule it
     * did learn: "Starting point is not a station - do not pick it in fully autonomous mode".  Staging
     * executes with autonomy running - executeTimetable sets the flag, which is why the reversing-point
     * exclusion had to be moved out of isPathClear and into selection - so the rule is in force for
     * every leg of a Return Home run.
     *
     * What it cost is worse than a refused plan.  The run STARTS, the first leg is refused, and the
     * retry loop tries it again every two seconds until it gives up and abandons the run with
     * "the track it needs never became free" - which is not what is wrong.  The track is clear; the
     * train is parked somewhere no automatic path may begin.
     *
     * A train gets there by being placed by hand, which is the ordinary way to put one on the layout.
     *
     * The control below is the point of the test: the same graph with the same train in the same
     * place, differing only in whether that place is a station, must plan perfectly well.  Without it
     * this test would pass for a fixture that was broken in some other way.
     *
     * MUTATION this catches: dropping either half - the pre-scan's isDestination test, or
     * firstClearRoute's - leaves the first assertion failing, since the search then finds the move.
     */
    @Test
    public void testALocomotiveOnANonStationIsNotPlannedHome() throws Exception
    {
        Layout layout = load(nonStationOrigin(false));

        assertFalse(layout.getPoint("HS P").isDestination(),
            "precondition: HS P is not a station, which is the whole case");

        assertTrue(layout.getPoint("HS P").isActive(),
            "precondition: HS P is in service - the inactive rule is the OTHER half, and if it fired "
            + "here this test would pass without the rule under test existing at all");

        assign(layout, LOC_A, "HS A");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.isPossible(),
            "the runtime refuses a path that starts anywhere but a station, so this would be planned "
            + "and then retried every two seconds until the run was abandoned: " + plan);

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "a locomotive that cannot depart is proved stuck, not searched for: " + plan);

        assertTrue(plan.getBlocked().contains(loc(LOC_A)),
            "and it is named, so the operator knows which train to move by hand: " + plan.getBlocked());

        // The control.  One flag different, and the plan is ordinary.
        Layout station = load(nonStationOrigin(true));

        assertTrue(station.getPoint("HS P").isDestination(),
            "the control did not take: HS P has to be a station in this one");

        assign(station, LOC_A, "HS A");

        HomeStaging.Plan ordinary = HomeStaging.snapshot(station).plan();

        assertTrue(ordinary.isPossible(),
            "the same train, the same square, the same empty home one edge away - and the only "
            + "difference is whether the square is a station.  If this fails the fixture is wrong "
            + "and the assertions above prove nothing: " + ordinary);
    }

    /**
     * The parity audit reports nothing when planner and runtime actually agree.
     *
     * The audit exists to catch the planner drifting from the rules it re-implements, which only works
     * if a clean layout is silent.  Its runtime oracle is getPossiblePaths, which filters destinations
     * on occupancy and station-ness but not on exclusion - pickPath does that separately - so a free
     * station that excludes the locomotive was reported as a disagreement on every run.  That is two
     * runtime methods disagreeing with each other, not a planner defect, and on a layout carrying
     * station exclusions it made the instrument cry wolf exactly where it is meant to be believed.
     */
    @Test
    public void testTheParityAuditIsSilentWhenTheTwoAgree() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        // Free, reachable, a station, and excluded - the combination the oracle used to offer
        layout.getPoint("HS C").setExcludedLocs(
            new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        assign(layout, LOC_A, "HS B");

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "an excluded destination is not a divergence - canRest refuses it and pickPath would too");
    }

    /**
     * The parity audit is silent about a locomotive that cannot depart, too.
     *
     * The companion to the exclusion case: the audit's oracle is asked at rest, where the runtime does
     * not apply its inactive-point rule, so it offers departures from a deactivated point that the
     * planner refuses because staging executes with autonomy running.  That is the third deliberate
     * divergence, and reporting it would make the instrument cry wolf in the configuration the rule
     * was just added for.
     */
    @Test
    public void testTheParityAuditIsSilentAboutALocomotiveThatCannotDepart() throws Exception
    {
        Layout layout = load(ringWith(new String[]{LOC_A, null, null, null},
                                      new String[]{"'active': false", null, null, null}));

        assertFalse(layout.getPoint("HS A").isActive(), "precondition: HS A is out of service");

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "the planner refuses every departure from an inactive point, and is right to - comparing "
            + "that against an at-rest oracle reports the rule as a defect");
    }

    // ---------------------------------------------------------------------------------------------
    // Shared sensors
    // ---------------------------------------------------------------------------------------------

    /**
     * Two active points that report one sensor are never both occupied.
     *
     * They are one piece of track as far as detection goes, so a plan that ends with a train on each
     * of them is describing something the layout cannot do - and the runtime would refuse the second
     * arrival, stranding the run halfway through.
     *
     * The rule used to be expressed by blocking the sensor address, and only for a sensor that was
     * READING occupied when the snapshot was taken - so on a layout whose feedback was quiet it did
     * not apply at all, and the planner would put both trains on one section for the runtime to
     * discover halfway through the run.  It is now structural, in canEnter.
     */
    @Test
    public void testTwoActivePointsSharingASensorAreNeverBothOccupied() throws Exception
    {
        // HS C reports HS A's sensor, and each train is assigned to one of the pair
        Layout layout = load(ringWith(new String[]{null, LOC_A, null, LOC_B},
                                  new String[]{null, null, null, null},
                                  new int[]{0, 1, 0, 3}));

        assertEquals(layout.getPoint("HS C").getS88(), layout.getPoint("HS A").getS88(),
            "precondition: HS A and HS C report one sensor");

        assertTrue(layout.getPoint("HS A").isActive() && layout.getPoint("HS C").isActive(),
            "precondition: both are active, which is what makes them mutually exclusive");

        assign(layout, LOC_A, "HS A");
        assign(layout, LOC_B, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertFalse(plan.isPossible(),
            "the two homes are one detection section - both trains cannot stand on them: " + plan);

        // IMPOSSIBLE, not NO_PLAN_FOUND.  The distinction is the whole point: NO_PLAN_FOUND means the
        // search ran out of room and says so, and reaching it here costs the entire budget - instant on
        // this four-point fixture, fifteen seconds on a real layout.  Conflicting goals are provable
        // without searching at all.
        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "conflicting homes are proved, not searched for: " + plan);

        assertTrue(plan.getBlocked().contains(loc(LOC_A)) && plan.getBlocked().contains(loc(LOC_B)),
            "and both locomotives are named, since either assignment could be the wrong one: "
            + plan.getBlocked());
    }

    /**
     * Two trains already standing on their own homes are not told the arrangement is impossible.
     *
     * The pairwise goal scan proves that two homes on one detection section cannot both be occupied,
     * and it is right - but it proves it about an ARRIVAL, and an arrival is the one thing that does
     * not happen when the train is already there.  Its sibling scan twelve lines below carries exactly
     * this exemption, in exactly these words: "Both already parked: nothing arrives, so nothing is
     * checked."  This one was written without it.
     *
     * The cost is not a worse plan, it is no plan at all.  IMPOSSIBLE refuses the WHOLE staging run,
     * so a third locomotive that only needed driving from one platform to the next never moves - and
     * the two names the operator is given are the two trains that are already where they belong.
     *
     * On Adam's railway BottomMainC and BottomMainCTerm share feedback 4, which is what a platform and
     * its terminus stub look like, so this is not a hypothetical shape.
     *
     * MUTATION this catches: removing the exemption restores the fault - IMPOSSIBLE, with the two
     * parked locomotives named and the one that could have moved left standing.
     */
    @Test
    public void testTwoTrainsAlreadyHomeOnOneSensorAreNotRefused() throws Exception
    {
        // HS A and HS B are one detection section, and each holds the train whose home it is.  HS C
        // holds a third, whose home is the empty HS D - so there IS something for the run to do.
        Layout layout = load(ringWith(new String[]{LOC_A, LOC_B, LOC_C, null},
                                  new String[]{null, null, null, null},
                                  new int[]{0, 0, 2, 3}));

        assertEquals(layout.getPoint("HS A").getS88(), layout.getPoint("HS B").getS88(),
            "precondition: HS A and HS B report one sensor, which is the whole case");

        assertTrue(layout.getPoint("HS A").isActive() && layout.getPoint("HS B").isActive(),
            "precondition: both are active, or the scan under test never looks at them");

        assign(layout, LOC_A, "HS A");
        assign(layout, LOC_B, "HS B");
        assign(layout, LOC_C, "HS D");

        assertEquals(layout.getPoint("HS A").getCurrentLocomotive(), loc(LOC_A),
            "precondition: the first train is standing on its own home");

        assertEquals(layout.getPoint("HS B").getCurrentLocomotive(), loc(LOC_B),
            "precondition: the second train is standing on its own home");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
            "two trains that are already home were proved unable to get there, which refused the "
            + "whole run - including the third train, which only had to cross to the next platform.  "
            + "Blocked: " + plan.getBlocked());

        assertFalse(plan.getBlocked().contains(loc(LOC_A)) || plan.getBlocked().contains(loc(LOC_B)),
            "a train that is already standing on its home was named as unable to reach it: "
            + plan.getBlocked());

        assertTrue(plan.isPossible(),
            "the third train has an empty home one edge away and nothing in its way: " + plan);
    }

    /**
     * Control: the shared address closes the other point only while something is standing on it.
     *
     * Without this the test above would be satisfied by a planner that refused every layout with a
     * shared address on it, which is most real layouts.
     */
    @Test
    public void testASharedSensorOnlyClosesTheOtherPointWhileItIsHeld() throws Exception
    {
        Layout layout = load(ringWith(new String[]{null, LOC_A, null, null},
                                  new String[]{null, null, null, null},
                                  new int[]{0, 1, 0, 3}));

        assertEquals(layout.getPoint("HS C").getS88(), layout.getPoint("HS A").getS88(),
            "precondition: HS A and HS C report one sensor");

        assign(layout, LOC_A, "HS A");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertTrue(plan.isPossible(),
            "nothing is standing on HS C, so its twin HS A is free to be occupied: " + plan);

        applyPlan(layout, plan);

        assertEquals(plan.getMoves().size(), 1, "one move: " + plan.getMoves());
    }

    /**
     * Excluding a locomotive from the station that is its home is recognised before it is applied.
     *
     * The guard on the other door already existed: assigning a home to a station that excludes the
     * locomotive warns.  This state is identical - every future Return Home reports IMPOSSIBLE and
     * refuses to move anything, including the locomotives that could have gone home - and reaching it
     * was silent, one keystroke over a hovered node.
     *
     * The predicate is what both UI paths ask; the dialog around it is not testable here.
     */
    @Test
    public void testExcludingALocomotiveFromItsOwnHomeIsRecognised() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        Point d = layout.getPoint("HS D");

        layout.setHomeLocomotive("HS D", LOC_A);

        assertEquals(HomeStaging.homeBrokenByExcluding(d, Arrays.asList((Locomotive) loc(LOC_A))),
            LOC_A, "HS D is the home of this locomotive, so excluding it there strands it");

        assertNull(HomeStaging.homeBrokenByExcluding(d, Arrays.asList((Locomotive) loc(LOC_B))),
            "another locomotive being excluded from HS D breaks nothing");

        assertNull(HomeStaging.homeBrokenByExcluding(layout.getPoint("HS C"),
            Arrays.asList((Locomotive) loc(LOC_A))),
            "and neither does excluding it from a station that is not its home");

        // The state the guard exists to prevent, confirmed to be as bad as claimed
        d.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE);
        assertTrue(plan.getMoves().isEmpty(),
            "and no locomotive moves at all, not even LOC_B which could have gone home");

        d.setExcludedLocs(new HashSet<Locomotive>());
    }

    /**
     * A positional home on an exit-only station is a launch pad, and does not block the fleet.
     *
     * Some layouts stage trains on one-way tracks - departure edges only, re-staged by hand; the
     * author's graph carries nineteen.  A locomotive loaded there acquires it as its positional
     * home, autonomy is free to dispatch it, and it can then never return - which, because a plan is
     * all-or-nothing, made Return Home answer IMPOSSIBLE for the whole fleet for the rest of the
     * session (found in the wild: MV 1134 on St99).  The graph states the intent topologically, so
     * the planner now treats such a locomotive as homeless.
     *
     * An ASSIGNED home on the same station keeps the strict contract - the operator chose it, so it
     * still answers IMPOSSIBLE with the locomotive named.
     */
    @Test
    public void testALaunchPadPositionalHomeDoesNotBlockTheFleet() throws Exception
    {
        // Two stations, a siding, and a launch pad whose only edge points INTO the layout.  The
        // siding matters: the dispatched pad locomotive is parked on the homed one's station, so
        // the plan exists only if the free agent has somewhere legitimate to step aside to - the
        // first version of this fixture had no siding, and its READY expectation was impossible
        // for any planner (the launch pad itself can never be re-entered).
        Layout layout = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + station("HS C", 5, null) + ","
            + station("Launch", 4, LOC_B)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("Launch", "HS A")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertTrue(layout.getIncomingEdges(layout.getPoint("Launch")).isEmpty(),
            "precondition: nothing can reach the launch pad");

        // LOC_B was loaded on the pad (positional home Launch), then ran - the wild scenario
        assertTrue(layout.moveLocomotive(LOC_B, "HS B", false),
            "precondition: the dispatch itself must succeed");

        assign(layout, LOC_A, "HS B");

        try
        {
            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
                "the launch-pad locomotive is homeless, not a blocker: " + plan);

            assertTrue(plan.getBlocked().isEmpty(), "nothing is reported stuck: " + plan.getBlocked());

            boolean movesHomedLoc = false;

            for (HomeStaging.Move m : plan.getMoves())
            {
                if (m.getLocomotive().equals(loc(LOC_A))) movesHomedLoc = true;
            }

            assertTrue(movesHomedLoc, "and the locomotive with a real home still gets there: "
                + plan.getMoves());

            // The strict half: ASSIGN the unreachable station and the answer is IMPOSSIBLE again,
            // because the operator explicitly asked for something the track cannot do
            assign(layout, LOC_B, "Launch");

            HomeStaging.Plan strict = HomeStaging.snapshot(layout).plan();

            assertEquals(strict.getOutcome(), HomeStaging.Outcome.IMPOSSIBLE,
                "an assigned home is still honoured with an error: " + strict);

            assertTrue(strict.getBlocked().contains(loc(LOC_B)),
                "and the locomotive concerned is named");
        }
        finally
        {
            layout.setHomeLocomotive("Launch", null);
        }
    }

    /**
     * UC-C21: a cornered search never buys a plan by moving a locomotive off its launch pad.
     *
     * Free agents exist to break deadlocks, and the A* expansion generates moves for every
     * locomotive in the state - so a locomotive still standing on its pad could be relocated when
     * the search was cornered, reachable whenever the pad shares a sensor with a point on someone
     * else's route.  A pad has no incoming edges, so that move can never be planner-undone: the
     * hand-staging the pad represents would be destroyed permanently as a side effect.  The honest
     * answer is NO_PLAN_FOUND - the only way out required undoing hand-staging, which is the
     * operator's to undo.
     */
    @Test
    public void testTheSearchNeverMovesALocomotiveOffItsLaunchPad() throws Exception
    {
        // X must travel HS A -> HS D -> HS B; the pad shares HS D's sensor, so the locomotive
        // parked on the pad closes HS D (two active points on one section are mutually exclusive).
        // Pre-fix, the search unwedges itself by shunting the pad locomotive to HS E.
        Layout layout = load(json("{'points': ["
            + station("HS A", 0, LOC_A) + ","
            + station("HS B", 1, null) + ","
            + station("HS D", 2, null) + ","
            + station("HS E", 3, null) + ","
            + station("Pad", 2, LOC_B)
            + "],'edges': ["
            + edge("HS A", "HS D") + "," + edge("HS D", "HS B") + ","
            + edge("Pad", "HS E")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertEquals(layout.getPoint("Pad").getS88(), layout.getPoint("HS D").getS88(),
            "precondition: the pad and HS D are one detection section");
        assertTrue(layout.getIncomingEdges(layout.getPoint("Pad")).isEmpty(),
            "precondition: the pad has no way back in");

        assign(layout, LOC_A, "HS B");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        for (HomeStaging.Move m : plan.getMoves())
        {
            assertFalse(m.getLocomotive().equals(loc(LOC_B)),
                "the plan bought its way out by destroying hand-staging: " + plan.getMoves());
        }

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.NO_PLAN_FOUND,
            "with the pad exempt, the honest answer is that no plan was found: " + plan);
    }

    /**
     * "Return home" may still stage a locomotive onto a reversing station.
     *
     * This is the half of the rule that gives it its purpose.  Parking tracks are flagged reversing so
     * full autonomy never sends a train to one at random; return home is what fills them deliberately.
     * The exclusion therefore lives in pickPath alone - HomeStaging reaches its destinations through
     * canRest and firstClearRoute, neither of which consults the flag.
     *
     * If the exclusion is ever moved somewhere shared - isPathClear, canRest, bfs - the two behaviours
     * collapse into one and this fails, which is the whole reason it is written down.
     */
    @Test
    public void testReturnHomeMayStillStageOntoAReversingStation() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        layout.getPoint("HS C").setReversing(true);

        assertTrue(layout.getPoint("HS C").isReversing(),
            "precondition: the destination under test must actually be a reversing station");

        assign(layout, LOC_A, "HS C");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "a reversing station is a legitimate home for staging: " + plan);

        assertTrue(plan.getMoves().stream()
                .anyMatch(m -> m.getLocomotive().equals(loc(LOC_A))
                    && "HS C".equals(m.getEnd().getName())),
            "the plan must actually send the locomotive there: " + plan.getMoves());
    }

    /**
     * A plan whose destination is held back by an occupied point is one the railway can carry out.
     *
     * OB-073: "the planner reported a plan it cannot carry out". FR-001 holds a station back while
     * another named square is occupied, and `isPathClear` enforces that on a path's destination -
     * which is every move staging makes. The planner did not read `getBlockedBy` at all, so it
     * reported READY, execution refused the leg, the run retried until it gave up, and it stopped
     * everything with the fleet half-staged.
     *
     * **This test was inverted on 2026-08-24, and the inversion is the point (FBR-B2).**
     *
     * It used to assert `!= READY`, on the reasoning that a station held back by an occupied square
     * cannot be reached. That is not true and was never true: staging can move whatever is standing
     * there. What OB-073 was actually about is that a plan must be EXECUTABLE, and the check that
     * delivers it is the state-aware `canRest` inside `firstClearRoute` - which is asked of the
     * evolving state, so the search vacates squares as it takes moves.
     *
     * `!= READY` could not tell a proof from a refusal, and it stayed green through two wrong fixes
     * because of it: a scan that called every occupant a proof of impossibility (FBR-B1), and then one
     * that called an occupant standing on its own home a proof (FBR-B2). `astar` moves locomotives off
     * their homes freely; the only exemption is the launch pad.
     *
     * So it asserts the property that would have caught OB-073 and does not forbid the right answer:
     * a plan comes back, every move finds its destination free at the moment it runs, and everyone
     * ends up home.
     *
     * "Free" had to be widened for that to be true (FSR-C1). `applyPlan` replays through
     * `moveLocomotive`, which PLACES a locomotive rather than refusing and never reads `getBlockedBy` -
     * so the replay could not see the very arrival OB-073 was about, and under the mutation it ran
     * clean while a different assertion did the work. It asks the FR-001 question directly now.
     *
     * The SOP has the paragraph for what happened here: "When a root fix lands, expect tests of the
     * old bug to fail at their preconditions - that is confirmation, not regression."
     */
    @Test
    public void testAHomeHeldBackByAnOccupiedPointStillGetsAnExecutablePlan() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // B is where A wants to go, and it is held back by D - where nobody is yet.
        layout.getPoint("HS B").setBlockedBy(
            java.util.Arrays.asList(layout.getPoint("HS D")));

        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS B this tests nothing at all");

        assign(layout, LOC_A, "HS B");

        assertTrue(layout.moveLocomotive(LOC_A, "HS A", false), "the fixture could not be arranged");

        // Somebody on the watched point. Execution refuses A's arrival at B while this is true, so any
        // plan that sends A straight there is one the railway will not carry out.
        assertTrue(layout.moveLocomotive(LOC_B, "HS D", false),
            "could not stand a second locomotive on the watched point");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "no plan was produced for an arrangement that has one. B can be moved off the watched "
            + "square and A can then take its home - three moves. Reporting anything else here means "
            + "the impossibility scan has started proving things about occupancy again, which it "
            + "cannot: staging moves whatever is standing in the way (FBR-B2).  Got: "
            + plan.getOutcome());

        // The replay FIRST, because it is the assertion this test exists for (FSR-C1).
        //
        // The move-count check below used to come before it, and under a mutation that reintroduced
        // OB-073 that is the one that fired - on "a one-move plan cannot be right" rather than on the
        // move being one the railway refuses. Both are true of that plan; only the second says what is
        // wrong with it, and a test whose message names the wrong fault sends the next reader to the
        // wrong place.
        applyPlan(layout, plan);

        assertTrue(plan.getMoves().size() >= 2,
            "a one-move plan cannot be right: something has to leave the watched square before A "
            + "arrives, so the answer is at least two moves.  Got: " + plan.getMoves());

        assertEveryoneHome(layout);
    }

    // ---------------------------------------------------------------------------------------------
    // FR-001 written once (DR-B2), and the audit that grades it (DR-B1)
    // ---------------------------------------------------------------------------------------------

    /**
     * A square with everything about it optional.
     *
     * The FR-001 tests below need points that are not stations, that carry no feedback at all, and that
     * share a block - none of which station() can express, and all three of which are ordinary on a
     * builder-emitted layout.
     *
     * @param name the point name
     * @param s88Offset the sensor offset from S88_BASE, or null for a point with no feedback
     * @param block the shared-square identity, or null for a square emitted as a single Point
     * @param station whether a train may be sent there
     * @param loc the locomotive standing on it, or null
     * @return the point's JSON, in single quotes for json()
     */
    private static String square(String name, Integer s88Offset, String block, boolean station,
        String loc)
    {
        return "{'name': '" + name + "', 'station': " + station
            + (s88Offset == null ? "" : ", 's88': " + (S88_BASE + s88Offset))
            + (block == null ? "" : ", 'block': '" + block + "'")
            + (loc == null ? "" : ", 'loc': {'name': '" + loc + "'}") + "}";
    }

    /**
     * HS A - HS B - HS C in a line, plus a watched square emitted as TWO copies in one block, off on a
     * siding of its own so nothing can leave it.
     *
     * HS B is the destination the test holds back; the watched square carries no sensor, which is the
     * configuration in which the planner's sensor stand-in for a block covers nothing at all.
     *
     * @param locOnW2 a locomotive on the second copy of the watched square, or null
     * @param locOnC the same locomotive on HS C instead - the control - or null
     * @return the graph JSON
     */
    /**
     * HS A - HS P - HS B in a line, with a locomotive parked on the middle square.
     *
     * @param pIsAStation whether the middle square is a station, which is the only variable
     * @return the graph JSON
     */
    private static String nonStationOrigin(boolean pIsAStation)
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, null) + ","
            + square("HS P", 1, null, pIsAStation, LOC_A) + ","
            + square("HS B", 2, null, true, null)
            + "],'edges': ["
            + edge("HS A", "HS P") + "," + edge("HS P", "HS A") + ","
            + edge("HS P", "HS B") + "," + edge("HS B", "HS P")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * The block fixture, with a HOME assigned to the second copy of the watched square.
     *
     * Built by rewriting what `square` produced rather than by hand, and asserting the rewrite landed,
     * so a change to the square fixture cannot silently yield a config with no assignment in it -
     * which would make the test using it pass while testing nothing. Same device as `ringAssigning`.
     *
     * @param homeAtW2 the locomotive named as the home
     * @return the graph JSON
     */
    private static String blockAssigningItsWatchedSquare(String homeAtW2)
    {
        // Through json() on both sides, like ringAssigning: the fixture emits single quotes and
        // json() converts them, so a raw fragment never matches what blockOfTwoWatching returns.
        String raw = square("HS W2", null, "HSW", false, null);
        String plain = json(raw);
        String assigned = json(raw.substring(0, raw.length() - 1)
            + ", 'home': '" + homeAtW2 + "'}");

        String config = blockOfTwoWatching(null, null);

        assertTrue(config.contains(plain),
            "precondition: the block fixture must still emit HS W2 plainly");

        return config.replace(plain, assigned);
    }

    /**
     * Two locomotives cannot both be homed on one platform (MT-165, second round).
     *
     * The MT-165 fix let a positional home sit on a square that is drawn as several graph Points,
     * because ten of Adam's thirty-six station squares are, and no train standing on one had ever been
     * given a home.  What it did not do is teach the injectivity test the same thing: `claimHome` ends
     * `if (this.homeStations.containsValue(p)) return;`, which compares POINTS, so the far copy of a
     * platform looks like a station nobody has spoken for.
     *
     * The sequence is ordinary.  A train stands on a platform and takes it as its home.  Autonomy
     * sends it away.  The operator places another train on the same platform - arriving from the other
     * direction, which is the other copy - and it takes that as ITS home.  Two locomotives are now
     * homed on one piece of track.
     *
     * **No arrangement satisfies that**, and Return Home says so in the worst available way: two
     * active Points on one sensor make `sharesSection` true, so the planner answers IMPOSSIBLE naming
     * both locomotives, for the rest of the session, and on screen they look like two different
     * stations.
     *
     * The state was unreachable before MT-165 - `claimHome` refused a square with a block outright -
     * so this is a defect the fix introduced rather than one it exposed.
     *
     * MUTATION: putting `containsValue(p)` back in place of the block-aware test fails the last
     * assertion.
     */
    @Test
    public void testASecondLocomotiveDoesNotHomeOnTheOtherCopyOfAPlatform() throws Exception
    {
        Layout layout = load(twoStationCopiesAndASiding());

        Point p1 = layout.getPoint("HS P1");
        Point p2 = layout.getPoint("HS P2");

        assertNotNull(p1.getBlock(), "the fixture did not take: HS P1 must carry a block");

        assertEquals(p1.getBlock(), p2.getBlock(),
            "the fixture did not take: the two copies must share a block, or this asks nothing");

        // The MT-165 rule working: a train placed on a split platform takes it as its home.
        assertTrue(layout.moveLocomotive(LOC_A, "HS P1", false),
            "could not place the first locomotive");

        assertEquals(layout.getHomeStations().get(loc(LOC_A)), p1,
            "precondition: the first locomotive took no home from the square it was placed on, so "
            + "this cannot ask anything about the second");

        // It leaves.  By hand here; by autonomy in life.  The platform is empty and still its home.
        assertTrue(layout.moveLocomotive(LOC_A, "HS Q", false),
            "could not move the first locomotive away");

        assertEquals(layout.getHomeStations().get(loc(LOC_A)), p1,
            "precondition: moving a locomotive re-homed it, so the rest of this asks nothing");

        // And another train is put on the OTHER COPY of that platform.
        assertTrue(layout.moveLocomotive(LOC_B, "HS P2", false),
            "could not place the second locomotive");

        assertNull(layout.getHomeStations().get(loc(LOC_B)),
            "a second locomotive was given the far copy of a platform that is already the first "
            + "one's home.  The copies of a square are one piece of track, so no arrangement puts "
            + "both trains at home - and Return Home answers IMPOSSIBLE naming both of them from "
            + "then on, while the diagram shows what look like two different stations");
    }

    /**
     * A home is a SQUARE, and which way the train faces is not part of it (Adam, 2026-08-31).
     *
     * His words: "so the home should just be the logical point, and the direction is wherever the
     * locomotive was facing when it started moving."
     *
     * That settles a question this class has been answering three different ways. A square on the
     * diagram is emitted as one graph Point per arrival side, and LD-8 refused a home on any such
     * square at three doors - the editor menu, `Layout.setHomeLocomotive`, and the loader - because
     * "is the train home?" seemed to have more than one answer. It does not: the answer is about the
     * square, which is what `Point.isSamePlaceAs` has said since MT-165.
     *
     * **What the refusal was costing.** Ten of Adam's thirty-six station squares carry a block, and
     * they are the main-line platforms. `AutonomyBuilder.homeCopy` exists solely to choose which copy
     * of a split square should carry the home - and the loader then threw that choice away, every
     * time, because the chosen copy carries a block like all its siblings. The editor accepted the
     * assignment (`mayRestHere` filters `whyNotAHome` down to the resting reason only), so a home
     * could be set, look right, and be gone at the next start with only a log line.
     *
     * The last assertion is the one that makes "the square" mean something: usability is asked of the
     * square too. A platform with a turning copy and a plain copy is a perfectly good home for a
     * locomotive that cannot reverse, because it can stand on the plain one.
     *
     * MUTATION: putting back any of the three `getBlock() != null` refusals fails one of the first
     * three assertions; asking `canRest` about the Point rather than the square fails the last.
     */
    @Test
    public void testAHomeIsTheSquareAndNotOneOfItsDirections() throws Exception
    {
        Layout layout = load(twoStationCopiesAndASiding());

        Point p1 = layout.getPoint("HS P1");
        Point p2 = layout.getPoint("HS P2");

        assertNotNull(p1.getBlock(), "the fixture did not take: HS P1 must carry a block");
        assertEquals(p1.getBlock(), p2.getBlock(), "the fixture did not take: one square, two copies");

        // 1. The door a person uses.
        assertNull(HomeStaging.whyNotAHome(loc(LOC_A), p2),
            "the editor refuses a home on a platform drawn as two arrival sides, which is ten of "
            + "Adam's thirty-six station squares");

        // 2. The door the model uses.
        layout.setHomeLocomotive("HS P2", LOC_A);

        assertEquals(p2.getHomeLoc(), loc(LOC_A),
            "the model refused to record a home on a split square");

        final boolean wasReversible = loc(LOC_A).isReversible();

        try
        {
            // 3. And usability is asked of the SQUARE.
            //
            // A station's exclusion list is a property of one graph Point, so one copy of a platform
            // can bar a locomotive that the other welcomes. The home is the square, so what matters is
            // whether it can stand on any copy - asking only the copy that happens to carry the home
            // would refuse it, and which copy carries it is AutonomyBuilder's choice, not the
            // operator's.
            //
            // Exclusions rather than a turning berth, which is what this used before: a terminus
            // stopped disqualifying anybody on 2026-08-31, when Adam ruled that a train may back into
            // one past a reversing point.
            p2.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

            assertTrue(HomeStaging.canBeHome(loc(LOC_A), p2),
                "a home was refused because the copy carrying it bars this locomotive - while the "
                + "other copy of the same platform welcomes it perfectly well");

            // And the control: when EVERY copy bars it, the refusal is real.
            p1.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

            assertFalse(HomeStaging.canBeHome(loc(LOC_A), p2),
                "a platform every copy of which bars this locomotive was offered to it anyway, so "
                + "the square rule has stopped refusing anything");
        }
        finally
        {
            p1.setExcludedLocs(new HashSet<Locomotive>());
            p2.setExcludedLocs(new HashSet<Locomotive>());
            loc(LOC_A).setReversible(wasReversible);
            layout.setHomeLocomotive("HS P2", null);
        }
    }

    /**
     * The planner agrees with the door that accepted the home (2026-08-31).
     *
     * The home is the square, and `whyNotAHome` asks the square whether a train can stand there. The
     * planner's own reachability scan did not - it asked `canRest` and `connected` about the one copy
     * that happens to carry the home, which is AutonomyBuilder's choice and not the operator's. So the
     * editor accepted a home that Return Home then called IMPOSSIBLE.
     *
     * **Both halves have to be asked of the same copy.** Resting and reaching are separate questions,
     * and a copy you can rest at but cannot reach, plus a different copy you can reach but cannot rest
     * at, would pass two separate scans between them while being no home at all.
     *
     * The disqualifier here is an exclusion list, which is a property of one graph Point. It used to
     * be a turning berth; a terminus stopped disqualifying anybody on 2026-08-31, when Adam ruled that
     * a train that cannot reverse may back into one past a reversing point.
     *
     * MUTATION: asking `canRest(l, home)` about the stored copy fails the first assertion; asking
     * resting and connectedness over copies independently rather than per copy fails the second.
     */
    @Test
    public void testThePlannerAgreesWithTheDoorThatAcceptedTheHome() throws Exception
    {
        Layout layout = load(twoStationCopiesAndASiding());

        Point barred = layout.getPoint("HS P2");
        Point open = layout.getPoint("HS P1");

        assertEquals(barred.getBlock(), open.getBlock(),
            "the fixture did not take: these must be two copies of one platform");

        try
        {
            // The home is on the copy that bars this locomotive; the other copy welcomes it.
            barred.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

            layout.setHomeLocomotive("HS P2", LOC_A);

            assertTrue(layout.moveLocomotive(LOC_A, "HS Q", false),
                "could not place the locomotive away from its home");

            assertNull(HomeStaging.whyNotAHome(loc(LOC_A), barred),
                "precondition: the editor must accept this home, or the planner agreeing with it "
                + "proves nothing");

            // plan(), not triage(): triage is the cheap pre-check - already home, no homes, nothing
            // placed - and the reachability scan that decides IMPOSSIBLE lives in plan().
            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
                "Return Home called a home impossible that the application had just accepted. The "
                + "home is the platform, and the locomotive is welcome on its other copy");

            // AND THE CONTROL: when every copy bars it, it really is impossible.
            open.setExcludedLocs(new HashSet<Locomotive>(Arrays.asList((Locomotive) loc(LOC_A))));

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(),
                HomeStaging.Outcome.IMPOSSIBLE,
                "a platform every copy of which bars this locomotive was accepted for it, so the "
                + "rule has stopped refusing anything");
        }
        finally
        {
            open.setExcludedLocs(new HashSet<Locomotive>());
            barred.setExcludedLocs(new HashSet<Locomotive>());
            layout.setHomeLocomotive("HS P2", null);
            layout.moveLocomotive(null, "HS Q", false);
        }
    }

    /**
     * One platform holds one home, whichever of its copies is named (2026-08-31).
     *
     * `setHomeLocomotive` has always enforced one station per LOCOMOTIVE - assigning it somewhere new
     * gives up wherever it was before. The other direction was enforced by the field itself: a Point
     * holds one `homeLoc`, so naming a second locomotive at the same Point displaced the first.
     *
     * Allowing homes onto split squares opened a gap under that. Two copies of one platform are two
     * Points with two fields, so assigning one locomotive to each left both homed on one piece of
     * track - the state DAY-A1 fixed at the positional door, arrived at through the assignment door
     * instead. Nothing can satisfy it, and Return Home says IMPOSSIBLE naming both for the rest of the
     * session.
     *
     * Displacing rather than refusing, which is what naming the same Point twice already does: the
     * operator is saying this train lives here now.
     *
     * MUTATION: sweeping only the exact Point rather than the square leaves both homed.
     */
    @Test
    public void testOnePlatformHoldsOneHomeAcrossItsCopies() throws Exception
    {
        Layout layout = load(twoStationCopiesAndASiding());

        Point p1 = layout.getPoint("HS P1");
        Point p2 = layout.getPoint("HS P2");

        assertEquals(p1.getBlock(), p2.getBlock(), "the fixture did not take: one square, two copies");

        try
        {
            layout.setHomeLocomotive("HS P1", LOC_A);

            assertEquals(p1.getHomeLoc(), loc(LOC_A), "precondition: the first assignment did not take");

            // A second locomotive named at the OTHER copy of the same platform.
            layout.setHomeLocomotive("HS P2", LOC_B);

            assertEquals(p2.getHomeLoc(), loc(LOC_B), "the second assignment did not take");

            assertNull(p1.getHomeLoc(),
                "two locomotives are homed on two copies of one platform - which is one piece of "
                + "track, so no arrangement puts both of them home, and Return Home answers "
                + "IMPOSSIBLE naming both from now on");

            int here = 0;

            for (Point at : layout.getHomeStations().values())
            {
                if (at.isSamePlaceAs(p1)) here++;
            }

            assertEquals(here, 1, "the platform is recorded as the home of more than one locomotive");
        }
        finally
        {
            layout.setHomeLocomotive("HS P1", null);
            layout.setHomeLocomotive("HS P2", null);
        }
    }

    /**
     * A terminus can be a home for a train that cannot reverse (Adam, 2026-08-31).
     *
     * "trains should be allowed to back into terminuses if they are not reversible (that's why we have
     * the reversing point at feedback 2013)."
     *
     * `canRest` refused a terminus to a non-reversible locomotive, which is why EN57-947 could not be
     * homed at TunnelLeftPark - measured on his own layout, where that square is a terminus and both
     * of the locomotives he named are non-reversible. Almost every parking berth on a real railway is
     * a terminus, so the rule was refusing homes at most of the places a train is parked.
     *
     * Whether it can GET there with a reversal is a question about a route, and routes are where it is
     * now asked - `Layout.isPathClear` allows a terminus when the path passes a reversing point, and
     * refuses it when it does not. Asking it again here, without a route to look at, could only guess.
     * This is the "a guard needs a way past" rule: an over-strict check at the door where nothing can
     * satisfy it is worse than no check.
     *
     * MUTATION: putting the terminus clause back in canRest fails this.
     */
    @Test
    public void testATerminusCanBeAHomeForATrainThatCannotReverse() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        Point d = layout.getPoint("HS D");

        final boolean wasReversible = loc(LOC_A).isReversible();

        try
        {
            loc(LOC_A).setReversible(false);

            d.setTerminus(true);

            assertNull(HomeStaging.whyNotAHome(loc(LOC_A), d),
                "a parking berth was refused as a home to a train that cannot reverse. It backs in "
                + "past a reversing point and leaves forwards - and on Adam's railway that refusal "
                + "covered most of the places a train is actually parked");
        }
        finally
        {
            d.setTerminus(false);
            loc(LOC_A).setReversible(wasReversible);
        }
    }

    /**
     * A train that cannot reverse must BACK INTO its home, or it has no way there (Adam, 2026-08-31).
     *
     * "For homing: I would also like non-reversing trains to have to back in.  I know this is complex,
     * but we need to manage it."
     *
     * The runtime already insists: `Layout.isPathClear` refuses a terminus to a locomotive that cannot
     * reverse unless the path passes a reversing point, so the train arrives already turned and leaves
     * forwards. The planner did not know that rule. Its reachability scan asks `connected`, a plain
     * breadth-first search, so a home reachable only by a route with no reversing point read as
     * reachable - and the plan it then produced was one the runtime would refuse.
     *
     * The two ends of this test are the same railway with one flag moved, which is what makes it about
     * the rule rather than about the fixture.
     *
     * MUTATION: asking `connected` without the reversal requirement passes the first half and fails
     * the second.
     */
    @Test
    public void testATrainThatCannotReverseHasToBackIntoItsHome() throws Exception
    {
        Layout layout = load(straightToATerminus());

        Point berth = layout.getPoint("HS T");
        Point middle = layout.getPoint("HS M");

        assertTrue(berth.isTerminus(), "the fixture did not take: the home must be a terminus");

        final boolean wasReversible = loc(LOC_A).isReversible();

        try
        {
            loc(LOC_A).setReversible(false);

            layout.setHomeLocomotive("HS T", LOC_A);

            assertTrue(layout.moveLocomotive(LOC_A, "HS S", false),
                "could not place the locomotive away from its home");

            // NOTHING TURNS IT ROUND on the way, so it would have to back out of the berth to leave -
            // which is what "cannot reverse" forbids.
            assertFalse(middle.isReversing(), "the fixture did not take: nothing may reverse it yet");

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(),
                HomeStaging.Outcome.IMPOSSIBLE,
                "the planner offered to send a train that cannot reverse into a terminus by a route "
                + "that never turns it round. The runtime refuses exactly that, so the plan could "
                + "only have failed on its first move");

            // The same railway, with a reversing point on the way: now it can back in.
            middle.setReversing(true);

            assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
                "with a reversing point on the way the train arrives already turned - it backs in and "
                + "leaves forwards - and the planner still called it impossible");
        }
        finally
        {
            middle.setReversing(false);
            loc(LOC_A).setReversible(wasReversible);
            layout.setHomeLocomotive("HS T", null);
            layout.moveLocomotive(null, "HS S", false);
        }
    }

    /**
     * A start, a square in the middle that can be made to reverse trains, and a terminus beyond it.
     */
    private static String straightToATerminus()
    {
        return json("{'points': ["
            + square("HS S", 7, null, true, null) + ","
            + square("HS M", 8, null, true, null) + ","
            + terminus("HS T", 9)
            + "],'edges': ["
            + edge("HS S", "HS M") + "," + edge("HS M", "HS S") + ","
            + edge("HS M", "HS T") + "," + edge("HS T", "HS M")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * A station square a train can only leave by reversing.
     */
    private static String terminus(String name, int s88Offset)
    {
        return "{'name': '" + name + "', 'station': true, 's88': " + (S88_BASE + s88Offset)
            + ", 'terminus': true}";
    }

    /**
     * One platform drawn as two Points, and a siding to send a train to.
     *
     * Both copies are stations and both carry the same sensor, which is what a split platform IS on
     * the derived graph - `Point` refuses a destination with no feedback, so the copies cannot be
     * given the null s88 that `blockOfTwoWatching`'s pair uses.
     */
    private static String twoStationCopiesAndASiding()
    {
        return json("{'points': ["
            + square("HS P1", 5, "HSP", true, null) + ","
            + square("HS P2", 5, "HSP", true, null) + ","
            + square("HS Q", 6, null, true, null)
            + "],'edges': ["
            + edge("HS P1", "HS Q") + "," + edge("HS Q", "HS P1") + ","
            + edge("HS P2", "HS Q") + "," + edge("HS Q", "HS P2")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    private static String blockOfTwoWatching(String locOnW2, String locOnC)
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 1, null, true, null) + ","
            + square("HS C", 2, null, true, locOnC) + ","
            + square("HS W1", null, "HSW", false, null) + ","
            + square("HS W2", null, "HSW", false, locOnW2)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("HS W1", "HS W2") + "," + edge("HS W2", "HS W1")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * The same line, but the watched square HS W shares its FEEDBACK with HS X - a different place.
     *
     * AutonomyBuilder is explicit that this happens: "a station, its approach guard and a reversing
     * point can be three Points on one feedback - so the sensor cannot say which Points are one
     * square."  HS W and HS X carry no block, because they are not copies of one another.
     *
     * @param locOnX a locomotive on the square that merely shares the sensor, or null for the control
     * @return the graph JSON
     */
    private static String sensorSharedWithAnotherSquare(String locOnX)
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 1, null, true, null) + ","
            + square("HS W", 2, null, false, null) + ","
            + square("HS X", 2, null, false, locOnX)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS W", "HS X") + "," + edge("HS X", "HS W")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * Two homes, a one-way hold, and an approach guard that shares a feedback with the other home.
     *
     * The counterexample against the OB-085 impossibility proof, built by a review and kept because
     * it is the case that scan is prone to getting wrong.
     *
     * HS C is held back while HS D is occupied - an ordinary one-way restriction. HS D is held back
     * while its approach guard HS W2 is occupied, and HS W2 shares a feedback address with HS C, which
     * AutonomyBuilder says outright is normal: "a station, its approach guard and a reversing point
     * can be three Points on one feedback - so the sensor cannot say which Points are one square."
     *
     * There is no cycle. HS W2 is not HS C; a train on HS C is not a train on HS W2. The railway
     * stages it in two moves, in the obvious order.
     *
     * No block anywhere, deliberately: these squares are not copies of one another, and the difference
     * between "shares a sensor" and "is the same block" is the entire point of the fixture.
     *
     * @return the graph JSON
     */
    private static String twoHomesAndAGuardSharingASensor()
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 1, null, true, LOC_B) + ","
            + square("HS C", 2, null, true, null) + ","
            + square("HS D", 3, null, true, null) + ","
            + square("HS W2", 2, null, false, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B") + ","
            + edge("HS C", "HS D") + "," + edge("HS D", "HS C") + ","
            + edge("HS D", "HS A") + "," + edge("HS A", "HS D") + ","
            + edge("HS W2", "HS D") + "," + edge("HS D", "HS W2")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * HS A - HS B, with HS B watched by an EMPTY square and sharing its feedback with an occupied one.
     *
     * The narrowness fixture for the audit's FR-001 exemption: HS B carries a blockedBy list, so an
     * exemption written "skip every destination that has one" would skip it - but nothing is standing
     * on the square it names, so the exemption as written does not, and the genuine divergence
     * underneath stays visible.
     *
     * @return the graph JSON
     */
    private static String watchedSquareEmptyButTheSensorIsNot()
    {
        return json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 1, null, true, null) + ","
            + square("HS X", 1, null, false, LOC_B) + ","
            + square("HS W", 2, null, false, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS X", "HS W") + "," + edge("HS W", "HS X")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}");
    }

    /**
     * The audit's FR-001 exemption is NARROW - it does not blind the instrument (DR-B1).
     *
     * The review named this risk when it asked for the exemption: "an exemption written too wide
     * (skipping every FR-001 destination rather than the currently-held ones) would blind the audit to
     * a real mis-copy of the rule." An exemption is a hole in the one tool that exists to find real
     * divergence, and the sibling tests above - all of which assert the audit is SILENT - cannot tell a
     * narrow hole from a total one. This is the only test in the class that asserts it still speaks.
     *
     * The divergence used as the vehicle is a real one the audit reports today and is not exempted: HS
     * B shares its feedback with HS X, where LOC_B is standing, so `canEnter`'s shared-sensor rule
     * refuses the arrival while the at-rest runtime offers it. HS B also carries a blockedBy list
     * naming a square nobody is on, which is what makes the two shapes of exemption differ.
     *
     * If that traversal divergence is ever exempted in its own right, this test needs a new vehicle
     * rather than deletion - the property it pins is about the FR-001 skip, not about shared sensors.
     *
     * MUTATION-CHECKED. Widening the exemption to `if (!p.getBlockedBy().isEmpty()) continue;` - the
     * exact over-reach the review warned about - takes the count to 0 and fails this test and no
     * other: 1 failure in the 65 of this class.
     */
    @Test
    public void testTheParityAuditStillReportsADivergenceOnAWatchedStation() throws Exception
    {
        Layout layout = load(watchedSquareEmptyButTheSensorIsNot());

        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS W")));

        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: without a blockedBy list the two exemption shapes behave "
            + "identically and this test proves nothing");

        assertNull(layout.getPoint("HS W").getCurrentLocomotive(),
            "precondition: the watched square must be EMPTY - a held-back station is exempt, and "
            + "correctly so, which is the case the tests above cover");

        assertEquals(layout.getPoint("HS X").getCurrentLocomotive(), loc(LOC_B),
            "precondition: somebody has to be on the square that shares HS B's feedback, or there is "
            + "no divergence left for the audit to find");

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 1,
            "the audit must still speak: the at-rest runtime offers HS B and the planner refuses it "
            + "over the shared sensor, which is a genuine divergence and not an FR-001 one.  Zero "
            + "here means the FR-001 exemption is skipping every destination that carries a "
            + "blockedBy list, which is the hole that would hide a real mis-copy of the rule");
    }

    /**
     * The parity audit is silent about a station FR-001 is holding back (DR-B1).
     *
     * The fourth correct divergence, and the one OB-073 created without adding the exemption. The
     * runtime's FR-001 clause is fenced behind isAutoRunning - it shapes what AUTONOMY chooses, and a
     * person dispatching by hand is looking at the railway - while the planner's copy applies always,
     * because staging executes with autonomy running. This audit runs from planReturnToHome with the
     * layout at rest, so getPossiblePaths offers a held-back station and the planner refuses it, and
     * the instrument that exists to find real mis-copies reported the rule working as a defect. On
     * every layout using FR-001, in a debug channel that is only read when something else is already
     * being chased.
     *
     * MUTATION-CHECKED. Deleting the FR-001 exemption from auditAgainstRuntime - the line reading
     * `if (Point.heldBackBy(p, loc) != null) continue;` - fails this test and no other.
     *
     * That line asked `plannedOccupancy(this.start)` when it was written, which a later review showed
     * was the planner’s own question on the planner’s own arguments: the exemption and the thing
     * being audited cancelled exactly, so no planner mis-copy of FR-001 could ever produce a
     * divergence. It asks the live-block variant now - what the RAILWAY would refuse - so the
     * instrument can still see the planner disagreeing with it.
     */
    @Test
    public void testTheParityAuditIsSilentAboutAStationHeldBackByAnOccupiedSquare() throws Exception
    {
        Layout layout = load(ring(LOC_A, LOC_B, null));

        // HS C is held back while HS B is occupied - and HS B is where LOC_B is standing
        layout.getPoint("HS C").setBlockedBy(Arrays.asList(layout.getPoint("HS B")));

        assertEquals(layout.getPoint("HS C").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS C this tests nothing at all");

        assertEquals(layout.getPoint("HS B").getCurrentLocomotive(), loc(LOC_B),
            "precondition: somebody has to be standing on the watched square");

        // The divergence itself, stated rather than implied: the runtime offers HS C to LOC_A at rest
        // BECAUSE its copy of the rule is off, and that is the whole reason the exemption exists.
        assertFalse(layout.isAutoRunning(),
            "precondition: the audit's oracle is only at-rest getPossiblePaths while autonomy is not "
            + "running, which is when the runtime does not apply FR-001 at all");

        assertEquals(HomeStaging.snapshot(layout).auditAgainstRuntime(), 0,
            "a station held back by an occupied square is not a planner defect - the planner is "
            + "applying the rule it is supposed to apply, and the at-rest oracle simply is not");
    }

    /**
     * The staging planner sees a train on another COPY of the watched square (DR-B2).
     *
     * FR-001 asks whether a watched square is occupied, and "the square" means the whole block: a
     * square emitted as several Points is one piece of track, which is what `getBlockLocomotive` is
     * for. The runtime asked the block. The planner had no block index at all and asked the shared
     * SENSOR instead, which covers the same pairs on a builder-emitted layout - every copy of a square
     * carries that square's s88 - and covers nothing whatever on a square that has no feedback.
     *
     * There the planner was the LOOSER half, which is the dangerous direction: it planned an arrival
     * isPathClear then refuses, the run retries until it gives up, and it stops with the fleet
     * half-staged. That is OB-073's own symptom arriving through a second door.
     *
     * The control is the same graph with the same locomotive standing somewhere else, so a green run
     * proves the refusal comes from where LOC_B is and not from the fixture being unbuildable.
     *
     * MUTATION-CHECKED. Deleting the block term from `HomeStaging.sameTrackAs` - the whole
     * `if (track.getBlock() != null)` clause, which is what the planner did before this fix - fails
     * this test and no other: 1 failure in the 65 of this class.
     */
    @Test
    public void testThePlannerSeesATrainOnAnotherCopyOfTheWatchedSquare() throws Exception
    {
        Layout layout = load(blockOfTwoWatching(LOC_B, null));

        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS W1")));

        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS B this tests nothing at all");

        assertNull(layout.getPoint("HS W1").getS88(),
            "precondition: the watched square must have NO sensor, or the planner's sensor rule "
            + "covers the block by accident and this test proves nothing");

        assertEquals(layout.getPoint("HS W1").getBlock(), layout.getPoint("HS W2").getBlock(),
            "precondition: the two copies must be one block");

        assertEquals(layout.getPoint("HS W2").getCurrentLocomotive(), loc(LOC_B),
            "precondition: the train has to be on the copy the restriction does NOT name");

        // The runtime's own answer, which is the standard the planner is being held to: HS B is held
        // back, even though the Point that carries the name is empty.
        assertEquals(Point.heldBackBy(layout.getPoint("HS B"), loc(LOC_A)), layout.getPoint("HS W1"),
            "precondition: the RUNTIME refuses this arrival - getBlockLocomotive finds LOC_B on the "
            + "other copy - so any plan that makes it is one the railway will not carry out");

        assign(layout, LOC_A, "HS B");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "the planner offered a move the runtime refuses: nothing can leave block HSW - it is a "
            + "siding of its own - so HS B is held back for the whole run.  Got: " + plan);

        // The control: one locomotive moved, one graph, one answer changed.
        Layout free = load(blockOfTwoWatching(null, LOC_B));

        free.getPoint("HS B").setBlockedBy(Arrays.asList(free.getPoint("HS W1")));

        assign(free, LOC_A, "HS B");

        HomeStaging.Plan control = free.planReturnToHome();

        assertEquals(control.getOutcome(), HomeStaging.Outcome.READY,
            "the control must plan: with block HSW empty nothing holds HS B back at all, and a "
            + "refusal here would mean the fixture, not the rule, is what the case above proved.  "
            + "Got: " + control);

        applyPlan(free, control);

        assertEveryoneHome(free);
    }

    /**
     * A station held back by the square the ARRIVING train is leaving can still be reached (DR-B2).
     *
     * Adam, asked directly: "The condition should not apply to trains leaving - only departing." Both
     * production copies exempt the locomotive being routed, and they must: without it the one movement
     * that clears the condition is the movement it forbids.
     *
     * The replay oracle in this class did not. It asserted the watched square's `getCurrentLocomotive`
     * was null with no exemption at all, so a legal plan whose move IS the departing train would have
     * failed the test that grades the other two copies - a false red in the only automated check
     * staging plans have. It calls `Point.heldBackBy` now, which is the same call `isPathClear` makes.
     *
     * MUTATION-CHECKED - and by the strongest kind, since the pre-fix code is the mutation. Restoring
     * applyPlan's hand-written loop (assertNull on `watched.getCurrentLocomotive()` for every entry in
     * `end.getBlockedBy()`) fails this test and no other: 1 failure in the 65 of this class.
     */
    @Test
    public void testAStationHeldBackByTheTrainThatIsLeavingItIsStillReachable() throws Exception
    {
        Layout layout = load(ringWith(new String[]{null, null, null, LOC_A},
                                      new String[]{null, null, null, null}));

        layout.getPoint("HS A").setBlockedBy(Arrays.asList(layout.getPoint("HS D")));

        assertEquals(layout.getPoint("HS A").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS A this tests nothing at all");

        assertEquals(layout.getPoint("HS D").getCurrentLocomotive(), loc(LOC_A),
            "precondition: the arriving train must be the one standing on the watched square - that "
            + "is the whole case");

        assign(layout, LOC_A, "HS A");

        HomeStaging.Plan plan = layout.planReturnToHome();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "the railway allows this arrival - the train leaving the watched square is exempt - so "
            + "the planner must offer it.  Got: " + plan);

        applyPlan(layout, plan);

        assertEveryoneHome(layout);
    }

    /**
     * The staging planner is deliberately the STRICTER half on a shared sensor, and this pins it.
     *
     * The one divergence between the two production copies of FR-001 that is NOT being unified, and it
     * is a decision about Adam's railway rather than a refactor. The runtime asks the block, which is
     * the only thing that means "one square". The planner also asks the shared SENSOR, on canEnter's
     * reasoning that two points on one feedback are one detection section - but AutonomyBuilder says
     * outright that a sensor is not a square: "a station, its approach guard and a reversing point can
     * be three Points on one feedback." On such a layout the planner refuses arrivals the runtime
     * allows. That fails safe - a plan withheld, never a wrong movement - but its symptom is
     * NO_PLAN_FOUND, which is the failure this class has been burned by before.
     *
     * Both directions are asserted, so the divergence cannot be changed by accident either way: drop
     * the sensor term from the planner and the outcome assertion fails; add it to `Point.heldBackBy`
     * and the runtime assertion fails.
     *
     * MUTATION-CHECKED. Deleting the sensor term from `HomeStaging.sameTrackAs` - the
     * `if (track.getS88() != null)` clause - fails this test and no other: 1 failure in the 65 of this
     * class.
     */
    @Test
    public void testTheStagingPlannerIsTheStricterHalfOnASharedSensor() throws Exception
    {
        Layout layout = load(sensorSharedWithAnotherSquare(LOC_B));

        layout.getPoint("HS B").setBlockedBy(Arrays.asList(layout.getPoint("HS W")));

        assertEquals(layout.getPoint("HS B").getBlockedBy().size(), 1,
            "the fixture did not take: with nothing watching HS B this tests nothing at all");

        assertEquals(layout.getPoint("HS W").getS88(), layout.getPoint("HS X").getS88(),
            "precondition: the two squares must report one feedback, or there is no divergence here");

        assertNull(layout.getPoint("HS W").getBlock(),
            "precondition: they must NOT be one block - two places sharing a sensor is exactly the "
            + "case AutonomyBuilder says the sensor cannot decide");

        // The runtime's answer.  HS W is empty, and the runtime looks no further than the block, so
        // isPathClear would let LOC_A into HS B.
        assertNull(Point.heldBackBy(layout.getPoint("HS B"), loc(LOC_A)),
            "the runtime asks the block and nothing else, so it holds nothing back here - if this "
            + "fails, the sensor term has been copied into the runtime rule, which changes which "
            + "stations autonomy offers on the operator's railway");

        assign(layout, LOC_A, "HS B");

        HomeStaging.Plan plan = layout.planReturnToHome();

        // And the planner's, which is different on purpose.
        assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
            "the planner treats a sensor sibling as the same piece of track, so it refuses an arrival "
            + "the runtime would allow.  If this fails, that term has been dropped - which is a "
            + "legitimate change, but it widens what staging offers and is Adam's to make.  Got: "
            + plan);

        // The control: the same graph with nobody on the sensor sibling plans without trouble, so the
        // refusal above is about where LOC_B stands and not about the shape of the fixture.
        Layout free = load(sensorSharedWithAnotherSquare(null));

        free.getPoint("HS B").setBlockedBy(Arrays.asList(free.getPoint("HS W")));

        assign(free, LOC_A, "HS B");

        HomeStaging.Plan control = free.planReturnToHome();

        assertEquals(control.getOutcome(), HomeStaging.Outcome.READY,
            "the control must plan: with the shared sensor clear nothing holds HS B back.  Got: "
            + control);

        applyPlan(free, control);

        assertEveryoneHome(free);
    }


    /**
     * A locomotive held on two points is reported, not planned for (SG-A3).
     *
     * A locked path reserves every point along it for the one locomotive at once - that is how a
     * junction behind the train is held against a second train reaching it another way - and a path
     * that failed part-way through unlocking leaves those reservations standing.  Nothing in the model
     * tells a reservation from a train: reserve() and setLocomotive() write the same field, and the
     * only difference between them is whether the other copies are swept.
     *
     * So the planner had a locomotive at two places at once, and two things went wrong with it.
     * `misplaced` counted map ENTRIES, so one train counted twice, and `apply` moved it by removing the
     * first entry it found - which left the other behind for ever.  `misplaced == 0` was therefore
     * unreachable, and the answer was NO_PLAN_FOUND with no moves, for a train with a clear run to an
     * empty home.
     *
     * **Counting it properly is the wrong fix.**  It makes the planner produce a plan, and the plan
     * departs from whichever of the two points the map happens to yield first - so a real train is
     * driven from a place it is not standing.  This class's own doctrine is that NO_PLAN_FOUND claims
     * less than it could and claims nothing false; a guessed origin claims something that may be false,
     * and the cost of being wrong is the worst outcome this project has.
     *
     * What the operator is told instead names the locomotive and the remedy - place it on the square it
     * is actually on, which sweeps the rest - rather than sending them to look at track that is fine.
     *
     * MUTATION this catches: removing the check returns NO_PLAN_FOUND, which the first assertion
     * rejects; naming nothing fails the second.
     */
    @Test
    public void testALocomotiveHeldOnTwoPointsIsReportedRatherThanGuessedAt() throws Exception
    {
        Layout layout = load(ring(LOC_A, null, null));

        assign(layout, LOC_A, "HS C");

        // The control first, so a failure below cannot be the fixture being unable to plan at all.
        assertEquals(HomeStaging.snapshot(layout).plan().getOutcome(), HomeStaging.Outcome.READY,
            "precondition: with one reservation this is an ordinary two-station move");

        reserveAsAFailedPathWould(layout, "HS B", LOC_A);

        assertEquals(layout.getPoint("HS A").getCurrentLocomotive(), loc(LOC_A),
            "the fixture did not take: the train has to still be held at HS A as well");

        assertEquals(layout.getPoint("HS B").getCurrentLocomotive(), loc(LOC_A),
            "the fixture did not take: HS B has to be reserved for the same locomotive");

        HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

        assertEquals(plan.getOutcome(), HomeStaging.Outcome.POSITION_AMBIGUOUS,
            "a locomotive standing in two places was planned for anyway, or dismissed as no plan "
            + "found - neither of which tells the operator what is actually wrong: " + plan);

        assertTrue(plan.getBlocked().contains(loc(LOC_A)),
            "and it is named, or the operator has nothing to act on: " + plan.getBlocked());

        assertTrue(plan.getMoves().isEmpty(),
            "nothing may be planned for a train whose position is not known: " + plan.getMoves());
    }

    /**
     * Reserves a point for a locomotive without taking it off anywhere else, as a locked path does.
     *
     * reserve() is package-private on purpose - only a locked path may do this - so the test reaches it
     * the way the failure does rather than by widening the door.
     *
     * @param layout the graph
     * @param pointName the point to reserve
     * @param locName the locomotive to reserve it for
     */
    private static void reserveAsAFailedPathWould(Layout layout, String pointName, String locName)
        throws Exception
    {
        Method reserve = org.traincontrol.automation.Point.class.getDeclaredMethod(
            "reserve", org.traincontrol.base.Locomotive.class);

        reserve.setAccessible(true);

        reserve.invoke(layout.getPoint(pointName), loc(locName));
    }

    /**
     * A train may not be planned into the detection section it is standing on (SG-A4).
     *
     * canEnter refuses a point whose sensor is held by another locomotive and exempts the MOVER from
     * its own - "there != null && !there.equals(loc)".  isPathClear grants no such exemption: it reads
     * the live feedback for the end of every edge and refuses the path if it is set, whoever is
     * standing on it.  So the planner produced a leg the runtime will not drive.
     *
     * The exemption looks obviously right, which is why it survived - a train must not be blocked by
     * its own sensor.  But it is not being blocked FROM ITS OWN POINT here; isPathClear never checks
     * the point a path starts from.  It is being blocked from a DIFFERENT point that happens to report
     * the same sensor, and while the train is still standing on that section the sensor really is set.
     *
     * Hardware-conditional, which is why nothing caught it: on pulsed feedback the sensor clears behind
     * the train and the runtime's check never fires.  On latching occupancy detection it fires every
     * time.  So the feedback is set here explicitly rather than left to the fixture.
     *
     * MUTATION this catches: putting the exemption back makes the planner ready a move the runtime has
     * just been asked about and refused, which is the first and last assertions together.
     */
    @Test
    public void testATrainIsNotPlannedIntoItsOwnDetectionSection() throws Exception
    {
        Layout layout = load(json("{'points': ["
            + square("HS A", 0, null, true, LOC_A) + ","
            + square("HS B", 0, null, true, null) + ","
            + square("HS C", 2, null, true, null)
            + "],'edges': ["
            + edge("HS A", "HS B") + "," + edge("HS B", "HS A") + ","
            + edge("HS B", "HS C") + "," + edge("HS C", "HS B")
            + "],'minDelay': 0,'maxDelay': 0,'defaultLocSpeed': 30}"));

        assertEquals(layout.getPoint("HS B").getS88(), layout.getPoint("HS A").getS88(),
            "precondition: HS A and HS B are one detection section, which is the whole case");

        assign(layout, LOC_A, "HS C");

        String sensor = layout.getPoint("HS A").getS88();

        model.newFeedback(Integer.parseInt(sensor), null);

        assertTrue(model.setFeedbackState(sensor, true),
            "precondition: the feedback has to exist, or nothing below is testing anything");

        try
        {
            assertTrue(layout.isFeedbackOccupied(sensor),
                "precondition: the section the train is standing on reads occupied, which is what "
                + "latching occupancy detection does and pulsed feedback does not");

            // What the RUNTIME says, which is the half that does not move.
            assertTrue(layout.getPossiblePaths(loc(LOC_A), true).isEmpty(),
                "precondition: the runtime offers this train nowhere to go, because the only way out "
                + "of HS A ends on a point reporting the sensor HS A is holding");

            HomeStaging.Plan plan = HomeStaging.snapshot(layout).plan();

            assertNotEquals(plan.getOutcome(), HomeStaging.Outcome.READY,
                "the planner readied a move the runtime has just refused, so the run would start, "
                + "retry every two seconds and be abandoned: " + plan);
        }
        finally
        {
            // The model is shared by every test in this class, and a sensor left set is a fault that
            // would surface somewhere else entirely.
            model.setFeedbackState(sensor, false);
        }
    }

}
