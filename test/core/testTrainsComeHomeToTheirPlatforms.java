package core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.automation.Edge;
import org.traincontrol.automation.HomeStaging;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import static org.traincontrol.marklin.MarklinControlStation.init;

/**
 * Five trains go out from ordinary platforms, come back, and face the way they set off.
 *
 * Adam: "we should have trains on regular stations that need to go home, not parked trains that need to go park
 * somewhere else."  These stand on plain through platforms (no parking flag, no compulsory reversal), each is homed
 * where it starts, the railway runs itself for a while, and then everybody is sent home.
 *
 * Facing is the Point, not the square: a square entered from two directions is one Point per arrival side, and which
 * copy a train stands on IS its facing, so "the same direction" is asserted as the same Point.
 *
 * It is registered in build.xml and was written red until Return Home staged this arrangement: a test for something
 * that does not work belongs in the battery being red, not in an exclusion table being quiet.
 *
 * It runs against a frozen copy (`test/operator_layout`, Adam: "let's get the current diagram frozen in the test"), not
 * the live `cs2_sample_layout`, which is his real railway and would make the test say something different on every run.
 * To refresh it, copy the folder over and say so in the commit.  `LayoutSandbox.open(folder)` copies whatever it is
 * given.
 *
 * @author Adam
 */
public class testTrainsComeHomeToTheirPlatforms
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static Layout layout;

    /** Addresses of its own, high enough not to collide with the real database. */
    private static final int FIRST_ADDRESS = 2101;

    /** How long autonomy is allowed to rearrange the railway before everyone is sent home. */
    private static final int RUN_SECONDS = 20;

    private static final long SETTLE_TIMEOUT_MS = 180000;

    /** Where each train was put, which is also which way it was facing. */
    private static final Map<String, String> STARTED_AT = new LinkedHashMap<>();

    /**
     * Ordinary through platforms, where trains actually stand: no `parking`, no `mustReverse`, nothing switched off, so
     * the journey home is ordinary and not a berth-to-berth shunt.
     */
    private static final String[] PLATFORMS =
    {
        "BottomMainA", "TopMainR1", "TopMainR2", "Tunnel", "LowerFront"
    };

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File frozen = new File("test/operator_layout");

        if (!frozen.isDirectory())
        {
            throw new AssertionError("test/operator_layout is not here - this suite runs his stations");
        }

        // The sandbox copy, before the model, because init reads the layout preference.
        sandbox = support.LayoutSandbox.open(frozen);

        // Debug mode, which simulation requires.
        model = support.TestStation.simulatedInDebug();
        model.stop();

        List<LayoutDiagram> pages = new ArrayList<>();

        for (String page : model.getLayoutList()) pages.add(model.getLayout(page));

        org.traincontrol.automationui.AutonomySession session =
            new org.traincontrol.automationui.AutonomySession(sandbox.getFolder());

        session.open(pages);

        model.parseAuto(session.buildConfiguration());

        layout = model.getAutoLayout();

        if (layout == null || !layout.isValid())
        {
            throw new AssertionError("his setup did not parse here: " + Layout.getLastError());
        }

        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        try
        {
            if (layout != null) layout.stopLocomotives();
        }
        catch (Exception ignored) { }

        if (model != null)
        {
            for (int i = 0; i < 5; i++)
            {
                try { model.deleteLoc(name(i)); } catch (Exception ignored) { }
            }
        }

        if (sandbox != null) sandbox.close();
    }

    private static String name(int i)
    {
        return "PB test loc " + i;
    }

    /** The arrangement Adam asked for, and everyone back on the square they left, facing as they left. */
    @Test
    public void testEveryoneComesBackFacingTheWayTheySetOff() throws Exception
    {
        place();

        assertEquals(STARTED_AT.size(), 5, "not every train was placed, so this proves nothing");

        // A FLOOR ON THE FIXTURE: these have to be ordinary platforms, or the test quietly goes back to the berth
        // question.
        int plain = 0;

        List<String> notPlain = new ArrayList<>();

        for (String platform : PLATFORMS)
        {
            Point p = layout.getPoint(platform);

            if (p == null) p = ordinaryCopy(platform);

            if (p != null && p.isDestination() && p.isActive() && !p.isTerminus()) plain++;
            else notPlain.add(platform + " -> " + (p == null ? "no copy a train may be sent to"
                : p.getName() + " terminus=" + p.isTerminus() + " active=" + p.isActive()));
        }

        assertEquals(plain, PLATFORMS.length, notPlain
            + " - one of these is not an ordinary platform on this setup - a terminus or something switched "
            + "off among them turns this back into the parking-berth question it was moved away from");

        // NO HAND-START: a train on a platform is somewhere autonomy picks up on its own, and starting it by hand would
        // be the test arranging the run rather than watching it.

        // Autonomy runs the railway for a while.
        layout.runLocomotives();

        Thread.sleep(RUN_SECONDS * 1000L);

        layout.stopLocomotives();

        awaitStopped();

        String arrangement = describe();

        // Then send everybody home.
        List<String> planned = new ArrayList<>();

        HomeStaging.Outcome trivial = layout.triageReturnToHome();

        if (trivial != HomeStaging.Outcome.ALREADY_HOME)
        {
            assertNull(trivial,
                "the homes went away while the railway ran - got " + trivial + " from " + arrangement);

            HomeStaging.Plan plan = layout.planReturnToHome();

            if (!plan.isPossible()) System.out.println(reachability());

            // NO BRANCH HERE: an earlier attempt asked `canReachAnyDestination` per train and asserted no plan when one
            // was trapped.  That predicate is the full-autonomy one (it requires `isAutoDestination`, which Return Home
            // ignores), a trapped train and an impossible plan are independent facts, and the early return skipped the
            // facing check.  A test that absorbs its own failures reads as green while proving nothing.
            assertTrue(plan.isPossible(),
                "no way home from " + arrangement + " (outcome " + plan.getOutcome()
                + ", blocked " + plan.getBlocked() + ").  Five trains that set off from ordinary "
                + "platforms have to be able to get back to them.  WHICH fault this is - a planner "
                + "that is short, or a railway that cannot do it from where the run left them - is "
                + "not decided here: the per-train reachability printed above is what says, and it "
                + "gives each train's home and whether any route reaches it");

            // KEPT, to tell a train that does not arrive from one the plan never undertook to move: a short plan and a
            // move that did not stick.
            for (HomeStaging.Move move : plan.getMoves())
            {
                planned.add(move.getLocomotive().getName() + " -> " + move.getEnd().getName());
            }

            layout.loadReturnToHomeTimetable();

            assertTrue(layout.executeTimetable(),
                "the plan was accepted but a move gave up on the way, from " + arrangement
                + "\nplan was: " + planned);

            awaitStopped();
        }

        // AND THE WHOLE POINT: the same Point, not merely the same square.
        List<String> wrong = new ArrayList<>();

        for (Map.Entry<String, String> e : STARTED_AT.entrySet())
        {
            Locomotive loc = model.getLocByName(e.getKey());

            Point now = layout.getLocomotiveLocation(loc);

            String where = now == null ? "nowhere" : now.getName();

            // THE SAME ARRIVAL is what "the way it started" means.  A square trains may TURN at emits two copies for
            // one arrival ("LowerFront (eastbound)" and "(eastbound, reverse)") that differ in what the train does
            // next, not in how it came in; demanding the exact copy failed a train standing on its own platform.
            if (!arrival(e.getValue()).equals(arrival(where)))
            {
                wrong.add(e.getKey() + " set off from " + e.getValue() + " and is at " + where);
            }
        }

        if (!wrong.isEmpty()) System.out.println("PLAN WAS: " + planned);

        assertEquals(wrong.toString(), "[]",
            "trains did not come back to where they started, facing the way they started. A square a "
            + "train can enter from two directions is emitted as one Point per arrival side, so the "
            + "Point is the facing - coming back on another arrival is coming back the wrong way "
            + "round.  The turning copy of an arrival counts as that arrival. "
            + "Ran from " + arrangement);
    }

    /**
     * A copy's name with the turning twin folded onto the plain one: "LowerFront (eastbound, reverse)" is the arrival
     * "LowerFront (eastbound)".
     */
    private static String arrival(String point)
    {
        String turning = ", reverse)";

        if (!point.endsWith(turning)) return point;

        return point.substring(0, point.length() - turning.length()) + ")";
    }

    /**
     * Which train cannot be pathed home, and which half of the question it fails: is there ANY route from where it
     * stands to its home, and is there one that turns it round, which a train that cannot reverse needs before backing
     * into a terminus.
     */
    private static String reachability()
    {
        StringBuilder out = new StringBuilder("\nREACHABILITY, per train:\n");

        // THE HOMES THE LAYOUT ACTUALLY HOLDS, not the ones the test asked for: a home can be dropped after it is set,
        // and a locomotive with no home is a free agent the planner may leave anywhere.
        out.append("  homes the layout holds - the map the planner reads:\n");

        for (Map.Entry<Locomotive, Point> e : layout.getHomeStations().entrySet())
        {
            out.append(String.format("    %-16s -> %-34s launchPad=%s%n",
                e.getKey().getName(), e.getValue().getName(),
                layout.getIncomingEdges(e.getValue()).isEmpty()));
        }


        for (String name : STARTED_AT.keySet())
        {
            Locomotive loc = model.getLocByName(name);

            Point at = null;

            for (Point p : layout.getPoints())
            {
                if (loc != null && loc.equals(p.getCurrentLocomotive()))
                {
                    at = p;
                    break;
                }
            }

            String home = STARTED_AT.get(name);

            out.append(String.format("  %-16s at %-30s home %-20s",
                name, at == null ? "(nowhere)" : at.getName(), home));

            if (at == null)
            {
                out.append("  - not on the railway\n");
                continue;
            }

            Point target = layout.getPoint(home);

            boolean anyRoute = false;
            boolean turningRoute = false;

            // Asked over EVERY copy of the home square: the builder chooses which copy carries the home, and a train
            // may reach one and not another.
            for (Point candidate : layout.getPoints())
            {
                if (target == null || !candidate.isSamePlaceAs(target)) continue;

                if (routeExists(at, candidate, false)) anyRoute = true;
                if (routeExists(at, candidate, true)) turningRoute = true;
            }

            // AND THE QUESTION THE PLANNER ACTUALLY ASKS: is a path home clear RIGHT NOW, with everybody standing where
            // they are?  getPossiblePaths is occupancy- and command-aware, unlike "the railway connects these two
            // squares".
            int offered = 0;
            int clear = 0;

            for (List<Edge> path : layout.getPossiblePaths(loc, false))
            {
                if (path.isEmpty()) continue;

                Point ends = path.get(path.size() - 1).getEnd();

                if (target == null || !ends.isSamePlaceAs(target)) continue;

                offered++;

                if (layout.isPathClear(path, loc, false)) clear++;
            }

            out.append(String.format("  anyRoute=%-5s turningRoute=%-5s reversible=%-5s "
                + "pathsHomeNow=%d clearNow=%d%n",
                anyRoute, turningRoute, loc != null && loc.isReversible(), offered, clear));

            // IS THERE A TWO-MOVE PLAN?  Adam: "you may need to take the bottommainb/c trains around to bottommaina
            // before reversing via BottomMainPost."  A train with no path home is not stuck if it can reach somewhere
            // that HAS one.
            if (clear == 0)
            {
                List<String> staging = new ArrayList<>();

                for (List<Edge> first : layout.getPossiblePaths(loc, false))
                {
                    if (first.isEmpty() || !layout.isPathClear(first, loc, false)) continue;

                    Point midway = first.get(first.size() - 1).getEnd();

                    // Would a second leg from there reach home?  Asked on the graph, as the train is not standing there
                    // yet.
                    for (Point candidate : layout.getPoints())
                    {
                        if (target == null || !candidate.isSamePlaceAs(target)) continue;

                        if (routeExists(midway, candidate, !loc.isReversible() && candidate.isTerminus()))
                        {
                            if (!staging.contains(midway.getName())) staging.add(midway.getName());

                            break;
                        }
                    }
                }

                out.append(String.format("        two-move staging squares: %d%s%n",
                    staging.size(),
                    staging.isEmpty() ? "" : "  e.g. " + staging.subList(0, Math.min(4, staging.size()))));
            }

            // AND WHETHER THE PLANNER MAY END A MOVE THERE AT ALL: the search only moves a locomotive to a square in
            // its station list (every DESTINATION and ACTIVE Point), so a home on a copy that is neither is reachable
            // yet nowhere a move can finish, which answers NO_PLAN_FOUND with nobody blocked.
            for (Point candidate : layout.getPoints())
            {
                if (target == null || !candidate.isSamePlaceAs(target)) continue;

                out.append(String.format("        copy %-34s dest=%-5s active=%-5s terminus=%-5s reversing=%s%n",
                    candidate.getName(), candidate.isDestination(), candidate.isActive(),
                    candidate.isTerminus(), candidate.isReversing()));
            }
        }

        out.append("  (a train that cannot reverse may back into a terminus; Return Home turns it on the way only to go home)\n");

        return out.toString();
    }

    /**
     * Whether a route exists between two Points, optionally insisting it turns the train round.  The same shape as
     * HomeStaging.connected (private), blind to occupancy and keyed on (point, turned); a train on a reversing point or
     * terminus was turned by its own arrival, and a terminus is never expanded THROUGH.
     */
    private static boolean routeExists(Point from, Point to, boolean mustTurn)
    {
        if (from == null || to == null) return false;

        java.util.Deque<Point> queue = new java.util.ArrayDeque<>();
        java.util.Deque<Boolean> turnedQ = new java.util.ArrayDeque<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        boolean start = from.isReversing() || from.isTerminus();

        queue.add(from);
        turnedQ.add(start);
        seen.add(from.getUniqueId() + "/" + start);

        while (!queue.isEmpty())
        {
            Point at = queue.poll();
            boolean turned = turnedQ.poll();

            for (Edge e : layout.getNeighbors(at))
            {
                Point next = e.getEnd();

                boolean now = turned || next.isReversing();

                if (next.equals(to))
                {
                    if (!mustTurn || now) return true;

                    continue;
                }

                if (next.isTerminus()) continue;

                String key = next.getUniqueId() + "/" + now;

                if (seen.add(key))
                {
                    queue.add(next);
                    turnedQ.add(now);
                }
            }
        }

        return false;
    }

    private static void place() throws Exception
    {
        // HIS OWN TRAINS COME OFF FIRST: the copy arrives with his locomotives and their homes, and Return Home has to
        // get EVERYBODY home, so leaving them would plan for eight trains when this test is about five.  Purged, not
        // lifted, so they leave the run list too.
        int lifted = 0;

        for (Point p : new ArrayList<>(layout.getPoints()))
        {
            if (p.getCurrentLocomotive() == null) continue;

            layout.moveLocomotive(null, p.getName(), true);

            lifted++;
        }

        System.out.println("LIFTED " + lifted + " of his own trains off the copy");

        // AND THEIR HOMES, which lifting the trains does not touch: a home is an assignment rather than a train.  A
        // square can be the home of only ONE locomotive and `claimHome` refuses a second claim SILENTLY, so a test
        // train placed on one of his homes got no home, and the planner was free to leave it parked in a siding.
        layout.clearHomeLocomotives();

        System.out.println("CLEARED his home assignments too - "
            + layout.getHomeStations().size() + " left");

        String[] where = new String[]
        {
            PLATFORMS[0], PLATFORMS[1], PLATFORMS[2], PLATFORMS[3], PLATFORMS[4]
        };

        // NO FACING IS ASKED FOR: naming one was a demand the railway cannot meet on these platforms, and the test
        // asserts every train returns to the SAME Point it left, whichever copy placement gave it.
        String[] facing = new String[] {null, null, null, null, null};

        for (int i = 0; i < where.length; i++)
        {
            model.newMM2Locomotive(name(i), FIRST_ADDRESS + i);

            Locomotive loc = model.getLocByName(name(i));

            assertNotNull(loc, "could not create " + name(i));

            // A MIXTURE, deliberately: the first three cannot reverse and the last two can, so the planner brings home
            // both kinds.
            loc.setReversible(i >= 3);

            loc.setPreferredSpeed(35);

            Point target = pointFor(where[i], facing[i]);

            assertNotNull(target, "no point named " + where[i] + " on his setup");

            assertTrue(layout.moveLocomotive(name(i), target.getName(), false),
                "could not place " + name(i) + " on " + target.getName());

            System.out.println("PLACING " + name(i) + " reversible=" + loc.isReversible()
                + " on " + target.getName() + " (destination=" + target.isDestination() + ")");

            STARTED_AT.put(name(i), target.getName());
        }
    }

    /**
     * The Point for a station, choosing the copy that faces a given way where the square has copies (the name carries
     * "(westbound)").
     */
    private static Point pointFor(String station, String facing)
    {
        // Only the copies a train may actually be SENT to: not every copy is a destination, and placing is refused on
        // the others.
        List<Point> stations = new ArrayList<>();
        List<String> all = new ArrayList<>();

        for (Point p : layout.getPoints())
        {
            if (!p.getName().equals(station) && !p.getName().startsWith(station + " (")) continue;

            all.add(p.getName() + (p.isDestination() ? "" : " [not a station]"));

            if (p.isDestination()) stations.add(p);
        }

        assertFalse(stations.isEmpty(),
            "no copy of " + station + " is a station on his setup, so nothing can be placed there. "
            + "Copies found: " + all);

        if (facing == null)
        {
            // THE SAME PREFERENCE THE FLOOR APPLIES: the first copy found put a locomotive on the terminus "LowerFront
            // (eastbound, reverse)".
            Point ordinary = ordinaryCopy(station);

            return ordinary != null ? ordinary : stations.get(0);
        }

        for (Point p : stations)
        {
            if (p.getName().toLowerCase().contains(facing)) return p;
        }

        // FAILED, not skipped: a skip would hide the one thing this arrangement exercises and read as green.
        fail(station + " has no copy facing " + facing + " that a train can be placed on. Copies "
            + "found: " + all);

        return null;
    }

    /**
     * The copy of a square a train may be sent to and does not have to reverse at, ORDINARY PREFERRED over the first
     * found.  The square is the exact name or the name and a bracket: `startsWith` alone matched TunnelCenterPark for
     * "Tunnel".  Returns the first copy trains may be sent to if every copy reverses, null if none accepts a train.
     */
    private static Point ordinaryCopy(String station)
    {
        Point reversing = null;

        for (Point copy : layout.getPoints())
        {
            boolean sameSquare = copy.getName().equals(station)
                || copy.getName().startsWith(station + " (");

            if (!sameSquare || !copy.isDestination() || !copy.isActive()) continue;

            if (!copy.isTerminus()) return copy;

            if (reversing == null) reversing = copy;
        }

        return reversing;
    }


    /** Waits for every driving thread to finish, so the arrangement being asserted is a settled one. */
    private static void awaitStopped() throws Exception
    {
        long until = System.currentTimeMillis() + SETTLE_TIMEOUT_MS;

        while (System.currentTimeMillis() < until)
        {
            if (!layout.isRunning()) return;

            Thread.sleep(250);
        }

        fail("the railway was still running " + (SETTLE_TIMEOUT_MS / 1000) + " seconds after being "
            + "asked to stop, so nothing below could be trusted");
    }

    /** Where everybody is, for a failure message somebody can act on. */
    private static String describe()
    {
        StringBuilder out = new StringBuilder();

        for (String who : STARTED_AT.keySet())
        {
            Locomotive loc = model.getLocByName(who);

            Point at = loc == null ? null : layout.getLocomotiveLocation(loc);

            out.append(out.length() == 0 ? "" : ", ")
               .append(who).append(" at ").append(at == null ? "nowhere" : at.getName());
        }

        return out.toString();
    }
}
