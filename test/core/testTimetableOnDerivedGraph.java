package core;

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
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.file.CS2File;

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
 *   5. replay what was captured, and check both that every entry finished AND that every locomotive
 *      ended up standing where the timetable said it would
 *
 * Step 5 is the whole test.  "Every entry finished" alone would pass on a replay that quietly took a
 * different way round, which is exactly the failure a renamed or vanished arrival-side copy would
 * cause.  What catches it is the LAYOUT's own account of where the trains are: on a derived graph a
 * Point is an arrival side, so "Tunnel (northbound)" and "Tunnel (southbound)" are different answers
 * to "where did it finish".
 *
 * What this does NOT check, and it is worth being straight about: only each locomotive's LAST entry
 * is verified by position, because that is the only one whose destination the train is still standing
 * on when the run ends.  A replay that drove an earlier entry somewhere else and then recovered would
 * pass.  Checking every leg needs the run to record what it traversed as it goes, which is a change to
 * the timetable machinery rather than to this test.
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

    /**
     * How long autonomy runs, once it has actually dispatched something.  Long enough to capture
     * several moves.
     */
    private static final int RUN_SECONDS = 12;

    /**
     * How long to wait for autonomy to dispatch ANYTHING before giving up (OB-114, OB-132).
     *
     * This used to be a flat `Thread.sleep(RUN_SECONDS * 1000L)` with no wait for a first move - fine
     * on the small hand-built layouts most timetable tests use, where dispatch is close to instant, but
     * this test derives a 56-point graph from test_layout and does a real path search before its first
     * command. On a machine running a full battery - several JVMs at once, autonomy competing for CPU -
     * that search can take much longer than twelve seconds, and a skip after a short fixed sleep cannot
     * tell "nothing to see here" from "the clock ran out before the railway did anything". Matches the
     * ceiling testTimetableCaptureThroughARealRun uses for the same reason.
     */
    private static final long STARTUP_CEILING_MS = 240000;

    private static final long SETTLE_TIMEOUT_MS = 90000;

    /**
     * Seeded, and the seed is in every failure message.
     *
     * This test picks stations and locomotives at random and then drives trains for minutes.  An
     * unseeded Random makes a failure something nobody can repeat, which is the worst property a test
     * of this size can have.  Pass -Dtimetable.seed to re-run a particular one.
     */
    private static final long SEED = Long.getLong("timetable.seed", 20260819L);

    private static final Random RANDOM = new Random(SEED);

    /**
     * The configuration this runs against, by name.
     *
     * TST-B11: this named "Autonomy 1", which exists nowhere in test_layout/config/autonomy/ (the
     * fixture holds only configuration-Main.json, and setup.json's own activeConfiguration is "Main") -
     * so the guard in derivedLayout() that is supposed to select it deterministically never matched,
     * and every run silently fell through to "whichever happened to be active", which the comment
     * there says must not happen.  It only ever worked by coincidence, because Main is also the only
     * configuration the fixture has.
     */
    private static final String CONFIGURATION = "Main";

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

    // 600s: room for STARTUP_CEILING_MS (240s) plus a run, two settle waits (up to 90s each) and a
    // replay, without the harness timing this out before the diagnostics in runCaptureAndReplay get a
    // chance to say why.
    @Test(timeOut = 600000)
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

        // Only the ones standing on a STATION.
        //
        // moveLocomotive refuses a point that is not a destination, and rightly - placing a train by
        // hand is a person saying where it is, and "halfway along the approach" is not somewhere a
        // train is put. But a configuration's saved placements are not all stations, so requiring
        // every locomotive to go back where it was made this test fail in its own setup, on a
        // precondition it could never satisfy.
        //
        // The ones that cannot go back are taken OFF the graph instead. Left where the run finished
        // they would sit on track a captured route needs, and the replay would fail for a reason that
        // has nothing to do with what is being tested.
        java.util.List<String> cannotRestore = new java.util.LinkedList<>();

        for (Locomotive loc : layout.getLocomotivesToRun())
        {
            Point at = layout.getLocomotiveLocation(loc);

            if (at == null) continue;

            if (at.isDestination())
            {
                startedAt.put(loc.getName(), at.getName());
            }
            else
            {
                cannotRestore.add(at.getName());
            }
        }

        assertTrue(startedAt.size() >= locomotives.size(),
            "every locomotive this test placed stands on a station, so all of them should have been "
            + "recorded as restorable - found " + startedAt.size() + " for " + locomotives.size()
            + " placed" + andTheSeed());

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

        // Waits for a train to MOVE, not for a fixed number of seconds (OB-114, the same fix
        // testTimetableCaptureThroughARealRun applies for the same reason). A derived 56-point graph
        // does a real path search before its first dispatch, and this test used to give up after a
        // flat twelve-second sleep - which cannot tell "the fixture has nothing to route" from "the
        // clock ran out before a loaded machine got to it" (OB-132).
        boolean moved = false;

        long startupDeadline = System.currentTimeMillis() + STARTUP_CEILING_MS;

        while (!moved && System.currentTimeMillis() < startupDeadline)
        {
            if (!layout.getActiveLocomotives().isEmpty()) moved = true;
            else Thread.sleep(200);
        }

        // And then let it actually run for a while, which is the part capture records.
        if (moved) Thread.sleep(RUN_SECONDS * 1000L);

        layout.stopLocomotives();

        awaitStopped(layout);

        layout.setTimetableCapture(false);

        List<TimetablePath> captured = layout.getTimetableSnapshot();

        if (captured.isEmpty())
        {
            throw new SkipException(
                "no locomotive moved in " + (STARTUP_CEILING_MS / 1000) + "s, so there is no timetable "
                + "to replay - " + describe(layout) + ".  " + stations.size() + " ordinary station(s) "
                + "found (" + stationNames(stations) + "); " + startedAt.size() + " locomotive(s) "
                + "stand on a destination out of " + layout.getLocomotivesToRun().size()
                + " autonomy knows about.  If this keeps happening on an unloaded machine too, check "
                + "whether test_layout's active autonomy configuration still leaves enough live "
                + "destinations for that many trains to move between - several of its points are "
                + "marked inactive.");
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
        // Off the graph first, so they cannot hold track the replay needs
        for (String square : cannotRestore)
        {
            layout.moveLocomotive(null, square, true);
        }

        for (int pass = 1; pass <= 2; pass++)
        {
            for (java.util.Map.Entry<String, String> where : startedAt.entrySet())
            {
                Point at = layout.getLocomotiveLocation(model.getLocByName(where.getKey()));

                if (at != null && at.getName().equals(where.getValue())) continue;

                assertTrue(layout.moveLocomotive(where.getKey(), where.getValue(), false),
                    "could not put " + where.getKey() + " back at " + where.getValue()
                    + " before the replay" + andTheSeed());
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

        // ---- replay ---------------------------------------------------------------------------
        assertTrue(layout.executeTimetable(),
            "the captured timetable did not run to the end.  Entry "
                + layout.getUnfinishedTimetablePathIndex() + " of " + captured.size()
                + " gave up, from " + describe(layout));

        awaitStopped(layout);

        // ---- and it drove what it was captured driving ---------------------------------------
        //
        // Compared against where the trains ACTUALLY are, rather than against the timetable itself.
        //
        // The obvious check here was to snapshot the timetable again and compare each entry's route to
        // the string built from it before the replay.  That cannot fail: getTimetableSnapshot copies
        // the LIST, not the entries, TimetablePath has no setter for its path, and executePath never
        // writes one back - so both sides of the comparison were the same object, and a replay that
        // drove a completely different arrival side would have passed.
        //
        // The layout's own state is an independent witness.  Every locomotive named in the timetable
        // must be standing on the Point its last entry ends at, and on a derived graph a Point IS an
        // arrival side - "Tunnel (northbound)" and "Tunnel (southbound)" are different names.  So this
        // is exactly the substitution the old assertion claimed to catch, actually caught.
        List<TimetablePath> afterwards = layout.getTimetableSnapshot();

        assertEquals(afterwards.size(), captured.size(),
            "the replay changed the timetable it was given" + andTheSeed());

        java.util.Map<String, String> shouldEndAt = new java.util.LinkedHashMap<>();

        for (int i = 0; i < afterwards.size(); i++)
        {
            TimetablePath entry = afterwards.get(i);

            assertTrue(entry.isExecuted(),
                "entry " + i + " is not marked executed even though the run reported success"
                + andTheSeed());

            List<Edge> path = entry.getPath();

            if (path != null && !path.isEmpty())
            {
                // Later entries overwrite earlier ones, so this ends up holding each locomotive's LAST
                // destination, which is where it should be standing now
                shouldEndAt.put(entry.getLoc().getName(), path.get(path.size() - 1).getEnd().getName());
            }
        }

        assertFalse(shouldEndAt.isEmpty(),
            "no entry carried a path, so nothing above was actually checked - which is how the "
            + "assertion this replaced managed to pass while comparing an object with itself"
            + andTheSeed());

        for (java.util.Map.Entry<String, String> expected : shouldEndAt.entrySet())
        {
            Point at = layout.getLocomotiveLocation(model.getLocByName(expected.getKey()));

            assertNotNull(at, expected.getKey() + " is on no square at all after the replay"
                + andTheSeed());

            assertEquals(at.getName(), expected.getValue(),
                expected.getKey() + " finished at " + at.getName() + " rather than at "
                + expected.getValue() + ", which is where the captured timetable said it would.  A "
                + "station is several Points on this graph, so reaching the same station by a "
                + "different arrival side is a different route" + andTheSeed());
        }

        // The recorded route strings are still worth keeping: they name what was captured, so a
        // failure above can be read against them
        assertEquals(capturedRoutes.size(), captured.size());
    }

    /**
     * The seed, for a failure message, so a failing run can be repeated.
     */
    private static String andTheSeed()
    {
        return "  (seed " + SEED + " - re-run with -Dtimetable.seed=" + SEED + ")";
    }

    /**
     * The configuration the current track diagram produces, parsed and made safe to run.
     *
     * The pages come from test_layout ITSELF, parsed directly with CS2File - the same way
     * testTracedPathIsContinuous does it - rather than from model.getLayoutList(), which reads
     * whatever TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF names on this machine.  The autonomy store
     * opened two lines below is test_layout's; pairing it with pages from a DIFFERENT layout silently
     * derives a graph that is neither one thing nor the other.
     */
    private static Layout derivedLayout() throws Exception
    {
        File folder = new File("test_layout");

        if (!folder.isDirectory())
        {
            throw new SkipException("no test_layout to derive a graph from");
        }

        String path = "file:///" + folder.getAbsolutePath().replace(File.separatorChar, '/') + "/";

        CS2File parser = new CS2File(path, model);
        parser.setLayoutDataLoc(path);

        List<LayoutDiagram> pages = parser.parseLayout(new java.util.LinkedList<MarklinAccessory>());

        AutonomySession session = new AutonomySession(folder);

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

    /**
     * The names of a list of stations, for a diagnostic message - so a skip names what it found
     * rather than only how many.
     */
    private static String stationNames(List<Point> stations)
    {
        StringBuilder out = new StringBuilder();

        for (Point p : stations)
        {
            if (out.length() > 0) out.append(", ");

            out.append(p.getName());
        }

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
