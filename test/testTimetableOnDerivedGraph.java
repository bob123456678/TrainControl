import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.automation.TimetablePath;
import org.traincontrol.automationui.AutonomySession;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Capture a timetable on a graph DERIVED FROM THE TRACK DIAGRAM, then replay it and check it ran.
 *
 * Every other timetable test builds its layout by hand with createPoint, so until now nothing had ever
 * captured against the graph the new autonomy actually produces.  That graph differs in the one way
 * that matters here: a station is several Points, named apart by the side trains arrive from, and a
 * timetable records a route by the NAMES of its points.  So the question this answers is whether a
 * captured route still means the same thing after the trains have moved and the graph has been asked
 * to run it again.
 *
 * The shape of the run is Adam's:
 *
 *   1. take the current layout and derive its configuration
 *   2. place a random set of locomotives on ordinary stations
 *   3. run them under autonomy with capture on
 *   4. send them back to where they started
 *   5. replay what was captured, and check both that every entry finished AND that each one drove the
 *      route it was captured driving
 *
 * Step 5 is the whole test.  "Every entry finished" alone would pass on a replay that quietly took a
 * different way round, which is exactly the failure a renamed or vanished arrival-side copy would
 * cause, so the paths are compared edge by edge.
 *
 * Simulation is mandatory and the run is skipped rather than failed if it cannot be turned on - this
 * drives locomotives for minutes at a time, and doing that to real hardware because a precondition
 * quietly failed is not a test failure worth having.
 */
public class testTimetableOnDerivedGraph
{
    private static MarklinControlStation model;

    /** How many locomotives to place, when the layout has that many stations to spare. */
    private static final int TRAINS = 3;

    /** How long autonomy runs before the graceful stop.  Long enough to capture several moves. */
    private static final int RUN_SECONDS = 12;

    private static final long SETTLE_TIMEOUT_MS = 90000;

    private static final Random RANDOM = new Random();

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Debug mode last, because simulation requires it
        model = init(null, true, false, false, true);
        model.stop();
    }

    @AfterClass
    public static void tearDownClass() throws Exception
    {
        if (model != null && model.getAutoLayout() != null)
        {
            model.getAutoLayout().stopLocomotives();
        }
    }

    @Test
    public void testACapturedTimetableReplaysOverTheSameTrack() throws Exception
    {
        Layout layout = derivedLayout();

        List<Point> stations = ordinaryStations(layout);

        if (stations.size() < 2)
        {
            throw new SkipException("the current layout has fewer than two ordinary stations to run between");
        }

        List<String> locomotives = placeRandomly(layout, stations);

        if (locomotives.size() < 2)
        {
            throw new SkipException("fewer than two locomotives could be placed - nothing to capture");
        }

        // Where each train began, so it can be put back before the replay
        java.util.Map<String, String> startedAt = new java.util.LinkedHashMap<>();

        for (String name : locomotives)
        {
            Point at = layout.getLocomotiveLocation(model.getLocByName(name));

            assertNotNull(at, name + " was placed but is standing nowhere");

            startedAt.put(name, at.getName());
        }

        // ---- capture -------------------------------------------------------------------------
        layout.setTimetableCapture(true);

        layout.runLocomotives();
        Thread.sleep(RUN_SECONDS * 1000L);
        layout.stopLocomotives();

        awaitStopped(layout);

        layout.setTimetableCapture(false);

        List<TimetablePath> captured = layout.getTimetableSnapshot();

        if (captured.isEmpty())
        {
            throw new SkipException(
                "nothing moved in " + RUN_SECONDS + "s, so there is no timetable to replay - "
                + describe(layout));
        }

        // What was captured, remembered as names, because the replay rebuilds nothing but must drive
        // the same track
        List<String> capturedRoutes = new ArrayList<>();

        for (TimetablePath entry : captured)
        {
            capturedRoutes.add(entry.getLoc().getName() + ": " + via(entry.getPath()));
        }

        // ---- put everyone back --------------------------------------------------------------
        for (java.util.Map.Entry<String, String> where : startedAt.entrySet())
        {
            assertTrue(layout.moveLocomotive(where.getKey(), where.getValue(), false),
                "could not put " + where.getKey() + " back at " + where.getValue()
                + " before the replay");
        }

        layout.resetTimetable();

        // What the replay is about to be asked to do, against where everyone actually is.  Printed
        // rather than asserted, because the first run of this test needs to show which of the two is
        // wrong before anything is claimed about either.
        System.out.println("TT restored:");

        for (Locomotive loc : layout.getLocomotivesToRun())
        {
            Point at = layout.getLocomotiveLocation(loc);

            System.out.println("   " + loc.getName() + " at "
                + (at == null ? "NOWHERE" : at.getName())
                + "   first entry starts at "
                + (layout.getTimetableStartingPoint(loc) == null ? "(none)"
                    : layout.getTimetableStartingPoint(loc).getName()));
        }

        System.out.println("TT captured " + captured.size() + " entries:");

        for (TimetablePath entry : captured)
        {
            System.out.println("   " + entry.getLoc().getName() + ": " + via(entry.getPath()));
        }

        // ---- replay ---------------------------------------------------------------------------
        assertTrue(layout.executeTimetable(),
            "the captured timetable did not run to the end.  Entry "
                + layout.getUnfinishedTimetablePathIndex() + " of " + captured.size()
                + " gave up, from " + describe(layout));

        awaitStopped(layout);

        // ---- and it drove what it was captured driving ---------------------------------------
        List<TimetablePath> afterwards = layout.getTimetableSnapshot();

        assertEquals(afterwards.size(), captured.size(),
            "the replay changed the timetable it was given");

        for (int i = 0; i < afterwards.size(); i++)
        {
            TimetablePath entry = afterwards.get(i);

            assertEquals(entry.getLoc().getName() + ": " + via(entry.getPath()), capturedRoutes.get(i),
                "entry " + i + " ran a different route from the one captured.  A timetable names its "
                + "points, and on this graph a station is several of them - so a route that still "
                + "reaches the same station by a different arrival side is not the same route");

            assertTrue(entry.isExecuted(),
                "entry " + i + " is not marked executed even though the run reported success");
        }
    }

    /**
     * The configuration the current track diagram produces, parsed and made safe to run.
     */
    private static Layout derivedLayout() throws Exception
    {
        File folder = new File("cs2_sample_layout");

        if (!folder.isDirectory())
        {
            throw new SkipException("no cs2_sample_layout to derive a graph from");
        }

        AutonomySession session = new AutonomySession(folder);

        List<org.traincontrol.base.LayoutDiagram> pages = new ArrayList<>();

        for (String name : model.getLayoutList()) pages.add(model.getLayout(name));

        session.open(pages);

        if (session.getStore().getActiveConfiguration() == null)
        {
            throw new SkipException("the layout has no active autonomy configuration to derive");
        }

        model.parseAuto(session.buildConfiguration());

        Layout layout = model.getAutoLayout();

        assertNotNull(layout, "the diagram produced no graph");
        assertTrue(layout.isValid(), "the derived configuration is invalid: " + Layout.getLastError());

        try
        {
            layout.setSimulate(true);
        }
        catch (Exception e)
        {
            throw new SkipException("refusing to run - simulation could not be enabled: " + e.getMessage());
        }

        assertTrue(layout.isSimulate(), "simulation must be on before anything is asked to move");

        // Adam's pacing: fast enough to finish, slow enough to be a run rather than a burst
        layout.setMinDelay(0);
        layout.setMaxDelay(1);

        return layout;
    }

    /**
     * Stations trains can simply stand at and be sent from.
     *
     * Termini and reversing points are excluded deliberately: both put conditions on which locomotives
     * may rest there, and a random placement that happened to pick one would fail for a reason that has
     * nothing to do with timetables.
     */
    private static List<Point> ordinaryStations(Layout layout)
    {
        List<Point> out = new ArrayList<>();

        Set<String> blocksTaken = new LinkedHashSet<>();

        for (Point p : layout.getPoints())
        {
            if (!p.isDestination() || !p.isActive() || !p.isAutoDestination()) continue;

            if (p.isTerminus() || p.isReversing()) continue;

            // One per SQUARE.  The copies of a split station are one piece of track, so offering both
            // to a random placement would sometimes put two trains on one platform.
            String block = p.getBlock() == null ? p.getName() : p.getBlock();

            if (!blocksTaken.add(block)) continue;

            out.add(p);
        }

        return out;
    }

    /**
     * Puts up to TRAINS locomotives on randomly chosen stations, and returns the ones that took.
     */
    private static List<String> placeRandomly(Layout layout, List<Point> stations)
    {
        List<Point> shuffled = new ArrayList<>(stations);
        Collections.shuffle(shuffled, RANDOM);

        List<String> available = new ArrayList<>(model.getLocList());
        Collections.shuffle(available, RANDOM);

        List<String> placed = new ArrayList<>();

        for (String name : available)
        {
            if (placed.size() >= TRAINS || placed.size() >= shuffled.size()) break;

            Point where = shuffled.get(placed.size());

            if (layout.moveLocomotive(name, where.getName(), false)) placed.add(name);
        }

        return placed;
    }

    /**
     * Waits for every locomotive to stop, so an assertion is made about a settled railway.
     */
    private static void awaitStopped(Layout layout) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS;

        while (layout.isRunning() && System.currentTimeMillis() < deadline)
        {
            Thread.sleep(100);
        }

        assertFalse(layout.isRunning(),
            "the layout was still running " + SETTLE_TIMEOUT_MS + "ms after being asked to stop");
    }

    private static String via(List<Edge> path)
    {
        StringBuilder out = new StringBuilder(path.get(0).getStart().getName());

        for (Edge e : path) out.append(" > ").append(e.getEnd().getName());

        return out.toString();
    }

    private static String describe(Layout layout)
    {
        StringBuilder out = new StringBuilder();

        for (Locomotive loc : layout.getLocomotivesToRun())
        {
            Point at = layout.getLocomotiveLocation(loc);

            out.append(loc.getName()).append('@')
               .append(at == null ? "nowhere" : at.getName()).append("  ");
        }

        return out.toString().trim();
    }
}
