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

    /** The configuration this runs against, by name. */
    private static final String CONFIGURATION = "Autonomy 1";

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

    @Test(timeOut = 300000)
    public void testACapturedTimetableReplaysOverTheSameTrack() throws Exception
    {
        Layout layout = derivedLayout();

        // A stuck replay must FAIL rather than hang.  The shipped bound is three minutes, because a
        // train may legitimately wait a long while for another to clear - here that would just mean a
        // test that takes three minutes to say something is wrong.
        long stuckWas = Layout.TIMETABLE_STUCK_MS;
        Layout.TIMETABLE_STUCK_MS = 20000;

        try
        {
            runCaptureAndReplay(layout);
        }
        finally
        {
            Layout.TIMETABLE_STUCK_MS = stuckWas;
            layout.stopLocomotives();
        }
    }

    private void runCaptureAndReplay(Layout layout) throws Exception
    {

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

        // Where EVERY train began, not just the ones this test placed.
        //
        // The configuration carries its own placements, so the graph holds more locomotives than were
        // put there here - and all of them move once autonomy starts.  Restoring only the placed ones
        // left the rest wherever the run finished, and a captured route then found its track occupied
        // by a train the replay had never accounted for.
        //
        // One of them was worse than misplaced: restoring a train onto a square whose sibling copy held
        // another swept that other one off the graph entirely, which is the block rule working exactly
        // as it should and a thing this test has to plan for rather than trip over.
        java.util.Map<String, String> startedAt = new java.util.LinkedHashMap<>();

        for (Locomotive loc : layout.getLocomotivesToRun())
        {
            Point at = layout.getLocomotiveLocation(loc);

            if (at != null) startedAt.put(loc.getName(), at.getName());
        }

        assertTrue(startedAt.size() >= locomotives.size(),
            "every locomotive standing somewhere should have been recorded");

        // ---- capture -------------------------------------------------------------------------
        //
        // Emptied first.  A configuration carries its own saved timetable - it rides along in the
        // globals - so parseAuto loads one, and capture APPENDS.  Without this the snapshot is four
        // entries of somebody else's run followed by this one's, and a replay then tries to drive
        // routes whose preconditions were never going to hold: trains stand nowhere near the start of
        // an entry recorded on a different day.
        layout.setTimetable(new ArrayList<TimetablePath>());

        assertTrue(layout.getTimetableSnapshot().isEmpty(),
            "the timetable must be empty before capture, or what is captured cannot be told from what "
            + "was already there");

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
        //
        // Twice through.  A single pass can sweep a train that has already been restored, because
        // placing onto one copy of a square clears the others - so the first pass settles the
        // arrangement and the second repairs anything the first displaced.
        for (int pass = 1; pass <= 2; pass++)
        {
            for (java.util.Map.Entry<String, String> where : startedAt.entrySet())
            {
                Point at = layout.getLocomotiveLocation(model.getLocByName(where.getKey()));

                if (at != null && at.getName().equals(where.getValue())) continue;

                assertTrue(layout.moveLocomotive(where.getKey(), where.getValue(), false),
                    "could not put " + where.getKey() + " back at " + where.getValue()
                    + " before the replay");
            }
        }

        for (java.util.Map.Entry<String, String> where : startedAt.entrySet())
        {
            Point at = layout.getLocomotiveLocation(model.getLocByName(where.getKey()));

            assertNotNull(at, where.getKey() + " is on no square at all after the restore");

            assertEquals(at.getName(), where.getValue(),
                where.getKey() + " did not go back where it started, so the replay would be asked to "
                + "run a timetable captured from a different arrangement");
        }

        layout.resetTimetable();

        // What the replay is about to be asked to do, against where everyone actually is.  Printed
        // rather than asserted, because the first run of this test needs to show which of the two is
        // wrong before anything is claimed about either.
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

        // The configuration Adam actually runs.  Deriving whichever happened to be active last would
        // make this test describe a different railway from one machine to the next.
        if (session.getStore().getConfigurationNames().contains(CONFIGURATION))
        {
            session.getStore().setActiveConfiguration(CONFIGURATION);
        }

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
