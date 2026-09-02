package core;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;
import org.testng.SkipException;
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
 * Five trains on the operator's own stations go out, come back, and face the way they set off.
 *
 * Adam asked for exactly this arrangement: "place a non reversing train on TunnelLeftPark,
 * TunnelRightPark, and TunnelCenterPark.  Place a normal train at BottomMainA and a reversing train
 * facing west on BottomMainC.  Then run for a while, send home and ensure they are where they started,
 * facing the same direction as when they started."
 *
 * **Why the arrangement is the point.** The three Tunnel berths are parking terminuses - a train can
 * only leave one by reversing - and the trains standing in them cannot reverse, so each of them can
 * only get home by BACKING IN past a reversing point. That is the rule of 2026-08-31, and this is it
 * on the real railway rather than on a three-point fixture. BottomMainA and BottomMainC are main-line
 * platforms drawn as more than one graph Point, so they are the case where "the home is the square"
 * and "facing the same direction" are two different claims.
 *
 * **Facing is the Point, not the square.** A square that trains can enter from two directions is
 * emitted as one Point per arrival side, and which copy a train stands on IS which way it is facing -
 * the model has no separate direction. So "facing the same direction as when they started" is asserted
 * as the same Point, and on a split platform that is strictly stronger than being home.
 *
 * **THIS TEST FAILS TODAY, and that is what it is for.** Return Home answers NO_PLAN_FOUND for this
 * arrangement on his railway. Three causes were eliminated by experiment before it was written up:
 *
 *   - it is NOT the backing-in rule added on 2026-08-31 - disabling `mustBackIn` gives the same
 *     answer;
 *   - it is NOT the search budget - raising SEARCH_BUDGET_MS from 15s to 90s gives the same answer;
 *   - it is NOT the fifth train standing on a copy trains cannot be SENT to - putting all five on
 *     destination copies gives the same answer.
 *
 * So it is structural, and it wants somebody who knows what the plan ought to be. Until then this
 * class is deliberately NOT registered in build.xml: a permanently red battery would cost more than
 * this test earns, and the finding is written up in the tracker instead of being shouted every run.
 *
 * **IT RUNS AGAINST A FROZEN COPY, not against his railway (Adam, 2026-09-01: "let's get the current
 * diagram frozen in the test").**
 *
 * `test/operator_layout` is his diagram as it stood when this was written, checked in.  It used to read
 * `cs2_sample_layout` directly - the live folder - which made the test say something different every
 * time he moved a train or changed a flag, and made a failure impossible to attribute: the code, the
 * fixture and the railway all moved between runs.  A test whose fixture changes underneath it is not
 * measuring the code.
 *
 * It is also the last reason this suite had to open that folder at all, which is worth more than the
 * determinism: `cs2_sample_layout` is his real railway and is not recoverable.
 *
 * To refresh it when the railway changes in a way this test should follow, copy the folder over and say
 * so in the commit - the point is that it moves when somebody decides it should, not on its own.
 * `LayoutSandbox.open(folder)` still copies whatever it is given somewhere temporary and points the
 * layout preference at the copy, so even the frozen fixture is only ever read.
 *
 * @author Adam
 */
public class testTheParkingBerthsGetTheirTrainsBack
{
    private static MarklinControlStation model;

    private static support.LayoutSandbox sandbox;

    private static Layout layout;

    /** Addresses of its own, high enough not to collide with anything on the real database. */
    private static final int FIRST_ADDRESS = 2101;

    /** How long autonomy is allowed to rearrange the railway before everyone is sent home. */
    private static final int RUN_SECONDS = 20;

    private static final long SETTLE_TIMEOUT_MS = 180000;

    /** Where each train was put, which is also which way it was facing. */
    private static final Map<String, String> STARTED_AT = new LinkedHashMap<>();

    private static final String[] BERTHS = {"TunnelLeftPark", "TunnelRightPark", "TunnelCenterPark"};

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        File frozen = new File("test/operator_layout");

        if (!frozen.isDirectory())
        {
            throw new SkipException("test/operator_layout is not here - this suite runs his stations");
        }

        // The sandbox copy, before the model, because init reads the layout preference.
        sandbox = support.LayoutSandbox.open(frozen);

        // Debug mode, which simulation requires.
        model = init(null, true, false, false, true);
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
            throw new SkipException("his setup did not parse here: " + Layout.getLastError());
        }

        layout.setSimulate(true);
        layout.setMinDelay(0);
        layout.setMaxDelay(0);
    }

    @AfterClass
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

    /**
     * The arrangement Adam asked for, and everyone back on the square they left, facing as they left.
     */
    @Test
    public void testEveryoneComesBackFacingTheWayTheySetOff() throws Exception
    {
        place();

        assertEquals(STARTED_AT.size(), 5, "not every train was placed, so this proves nothing");

        // A floor on the fixture: the berths must really be terminuses, or the backing-in rule this
        // arrangement was chosen for is not being exercised at all.
        int berths = 0;

        for (String berth : BERTHS)
        {
            Point p = layout.getPoint(berth);

            if (p != null && p.isTerminus()) berths++;
        }

        assertEquals(berths, BERTHS.length,
            "the parking berths are not terminuses on this setup, so nothing here has to back in");

        // THE PARKED ONES ARE STARTED BY HAND, once.
        //
        // Adam: "the parked trains need to be manually started the first time."  A berth is not an
        // automatic destination and a train sitting in one is not something full autonomy will choose
        // to move, so without this the three of them simply stand there for the whole run and the
        // arrangement this test is about never happens.  A hand dispatch is exactly what the diagram's
        // right-click menu does: one path, executed.
        int started = 0;

        for (int i = 0; i < BERTHS.length; i++)
        {
            Locomotive parked = model.getLocByName(name(i));

            List<List<Edge>> options = layout.getPossiblePaths(parked, true);

            // SOMEWHERE AUTONOMY WILL MOVE IT ON FROM (RTG-B1, 2026-09-01).
            //
            // `getPossiblePaths` is the MANUAL door, and it deliberately offers destinations autonomy
            // will never choose - inactive squares among them.  Taking `options.get(0)` took whichever
            // of those came first, and on Adam's configuration that is ParkingTrack6, which is
            // `active: false`.  So the hand-start that is supposed to set these trains going instead
            // parked one somewhere autonomy would not touch again, and it was still sitting there when
            // Return Home was asked for - stranded before the run began, by the fixture rather than by
            // the railway.
            //
            // This is the same tier distinction the whole feature turns on, and the test had fallen the
            // wrong side of it: a hand-start that models "start it off and let autonomy take over" has
            // to leave the train where autonomy can take over.
            List<Edge> chosen = null;

            for (List<Edge> option : options)
            {
                if (layout.isChoosableByAutonomy(option.get(option.size() - 1).getEnd()))
                {
                    chosen = option;
                    break;
                }
            }

            if (chosen == null)
            {
                System.out.println("NOT STARTED " + name(i) + " - nowhere autonomy would take it on "
                    + "from, out of " + options.size() + " manual option(s) from " + BERTHS[i]);
                continue;
            }

            if (layout.executePath(chosen, parked, parked.getPreferredSpeed(), null)) started++;
        }

        System.out.println("HAND-STARTED " + started + " of the " + BERTHS.length + " parked trains");

        assertTrue(started > 0,
            "not one of the parked trains could be started by hand, so the railway never moved and "
            + "everything below would pass by standing still");

        // Then autonomy takes over for a while, and the trains finish the paths they are on.
        layout.runLocomotives();

        Thread.sleep(RUN_SECONDS * 1000L);

        layout.stopLocomotives();

        awaitStopped();

        String arrangement = describe();

        // Then send everybody home.
        HomeStaging.Outcome trivial = layout.triageReturnToHome();

        if (trivial != HomeStaging.Outcome.ALREADY_HOME)
        {
            assertNull(trivial,
                "the homes went away while the railway ran - got " + trivial + " from " + arrangement);

            HomeStaging.Plan plan = layout.planReturnToHome();

            if (!plan.isPossible()) System.out.println(reachability());

            assertTrue(plan.isPossible(),
                "no way home from " + arrangement + " (outcome " + plan.getOutcome()
                + ", blocked " + plan.getBlocked() + "). The three berths are terminuses and their "
                + "trains cannot reverse, so each of them has to back in past a reversing point");

            layout.loadReturnToHomeTimetable();

            assertTrue(layout.executeTimetable(),
                "the plan was accepted but a move gave up on the way, from " + arrangement);

            awaitStopped();
        }

        // AND THE WHOLE POINT: the same Point, not merely the same square.
        List<String> wrong = new ArrayList<>();

        for (Map.Entry<String, String> e : STARTED_AT.entrySet())
        {
            Locomotive loc = model.getLocByName(e.getKey());

            Point now = layout.getLocomotiveLocation(loc);

            String where = now == null ? "nowhere" : now.getName();

            if (!e.getValue().equals(where))
            {
                wrong.add(e.getKey() + " set off from " + e.getValue() + " and is at " + where);
            }
        }

        assertEquals(wrong.toString(), "[]",
            "trains did not come back to where they started, facing the way they started. A square a "
            + "train can enter from two directions is emitted as one Point per arrival side, so the "
            + "Point is the facing - coming back on the other copy is coming back the wrong way round. "
            + "Ran from " + arrangement);
    }

    /**
     * Five locomotives of its own, placed where Adam asked for them.
     */
    /**
     * Which train cannot be pathed home, and which half of the question it fails.
     *
     * NO_PLAN_FOUND with an empty blocked list is the planner declining to claim anything - honest, and
     * it tells nobody what is missing.  These are the two questions that decide it: is there ANY route
     * from where the train stands to its home, and is there one that turns it round on the way, which a
     * train that cannot reverse needs before it may back into a terminus.
     *
     * A train that passes the first and fails the second is not a broken graph.  It is a berth with no
     * reversing point on its approach.
     */
    private static String reachability()
    {
        StringBuilder out = new StringBuilder("\nREACHABILITY, per train:\n");

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

            // Asked over EVERY copy of the home square: which copy carries the home is the builder's
            // choice, and a train may be able to reach one and not another.
            for (Point candidate : layout.getPoints())
            {
                if (target == null || !candidate.isSamePlaceAs(target)) continue;

                if (routeExists(at, candidate, false)) anyRoute = true;
                if (routeExists(at, candidate, true)) turningRoute = true;
            }

            // AND THE QUESTION THE PLANNER ACTUALLY ASKS: is a path home clear RIGHT NOW, with
            // everybody standing where they are?  getPossiblePaths is occupancy-aware and command-
            // aware, so this is the difference between "the railway connects these two squares" and
            // "this train can set off for home on its next move".
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

            // IS THERE A TWO-MOVE PLAN?  Adam: "you may need to take the bottommainb/c trains around
            // to bottommaina before reversing via BottomMainPost."  A train with no path home from
            // where it stands is not stuck if it can reach somewhere that HAS one - which is a plan of
            // two moves, and exactly what the search is supposed to find.  If this reports staging
            // squares and the planner still answers NO_PLAN_FOUND, the routes are there and the search
            // is not finding them.
            if (clear == 0)
            {
                List<String> staging = new ArrayList<>();

                for (List<Edge> first : layout.getPossiblePaths(loc, false))
                {
                    if (first.isEmpty() || !layout.isPathClear(first, loc, false)) continue;

                    Point midway = first.get(first.size() - 1).getEnd();

                    // Would a second leg from there reach home?  Asked on the graph, since the train
                    // is not standing there yet and getPossiblePaths only answers about where it is.
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

            // AND WHETHER THE PLANNER MAY END A MOVE THERE AT ALL.
            //
            // A route existing is not the same question.  The search only ever moves a locomotive to a
            // square in its station list, which is every Point that is a DESTINATION and ACTIVE - so a
            // home sitting on a copy that is neither can be perfectly reachable and still be somewhere
            // no move can finish.  That is invisible to the two questions above, and it is exactly the
            // shape that answers NO_PLAN_FOUND with nobody blocked.
            for (Point candidate : layout.getPoints())
            {
                if (target == null || !candidate.isSamePlaceAs(target)) continue;

                out.append(String.format("        copy %-34s dest=%-5s active=%-5s terminus=%-5s reversing=%s%n",
                    candidate.getName(), candidate.isDestination(), candidate.isActive(),
                    candidate.isTerminus(), candidate.isReversing()));
            }
        }

        out.append("  (a train that cannot reverse needs turningRoute=true to back into a terminus)\n");

        return out.toString();
    }

    /**
     * Whether a route exists between two Points, optionally insisting it turns the train round.
     *
     * The same shape as HomeStaging.connected, written here because that one is private and this is a
     * diagnostic: blind to occupancy, and keyed on (point, turned) because a square reached with a
     * reversal behind it and the same square reached without one are two different states.
     *
     * A train standing on a reversing point or a terminus was turned by its own arrival, so the search
     * starts turned there; and a terminus is never expanded THROUGH, because a train may arrive at one
     * but not drive on past it.
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
        // HIS OWN TRAINS COME OFF FIRST.
        //
        // The copy is his railway, so it arrives with his locomotives standing on it and their homes
        // recorded - EN57-947, EN57-203 and the rest. Return Home has to get EVERYBODY home, so
        // leaving them there means planning for eight trains when this test is about five, and that
        // is what NO_PLAN_FOUND was: a fixture that asked a far harder question than the one written
        // at the top of this class.
        //
        // Purged rather than merely lifted, so they leave the run list too.
        int lifted = 0;

        for (Point p : new ArrayList<>(layout.getPoints()))
        {
            if (p.getCurrentLocomotive() == null) continue;

            layout.moveLocomotive(null, p.getName(), true);

            lifted++;
        }

        System.out.println("LIFTED " + lifted + " of his own trains off the copy");

        String[] where = new String[]
        {
            BERTHS[0], BERTHS[1], BERTHS[2], "BottomMainA", "BottomMainC"
        };

        // The facing each one is to be placed on, where the square has copies. BottomMainA is
        // eastbound, which is Adam's own answer; BottomMainC is the westbound one he asked for.
        // BottomMainC is null here on purpose: no westbound copy of it is a station, so the placement
        // door cannot be asked for one. It goes on a destination copy first, to join the run list and
        // get its speed, and is then put on the westbound copy directly below.
        String[] facing = new String[] {null, null, null, "east", null};

        for (int i = 0; i < where.length; i++)
        {
            model.newMM2Locomotive(name(i), FIRST_ADDRESS + i);

            Locomotive loc = model.getLocByName(name(i));

            assertNotNull(loc, "could not create " + name(i));

            // The three in the berths cannot reverse; the one on BottomMainA is an ordinary train; the
            // one on BottomMainC is a reversing train, which is what Adam asked for.
            loc.setReversible(i >= 3);

            loc.setPreferredSpeed(35);

            Point target = pointFor(where[i], facing[i]);

            assertNotNull(target, "no point named " + where[i] + " on his setup");

            assertTrue(layout.moveLocomotive(name(i), target.getName(), false),
                "could not place " + name(i) + " on " + target.getName());

            // THE ONE FACING WEST, which cannot be reached through the placement door.
            //
            // Adam asked for "a reversing train facing west on BottomMainC". Both westbound copies of
            // that platform are NOT destinations, because his own setup bars arrivals from the east
            // there - so moveLocomotive refuses them, and a train can only be SENT to it facing east.
            //
            // Standing there facing west is a real state of his railway even so, and it is the state
            // he asked about, so the train is put on that copy directly. Whether Return Home can ever
            // restore it is exactly the question this test exists to answer.
            if (i == 4)
            {
                Point west = facingCopy(where[i], "west");

                assertNotNull(west, "BottomMainC has no westbound copy at all on his setup");

                west.setLocomotive(loc);

                target = west;
            }

            System.out.println("PLACING " + name(i) + " reversible=" + loc.isReversible()
                + " on " + target.getName() + " (destination=" + target.isDestination() + ")");

            STARTED_AT.put(name(i), target.getName());
        }
    }

    /**
     * Any copy of a square facing a given way, whether or not trains may be SENT there.
     */
    private static Point facingCopy(String station, String facing)
    {
        for (Point p : layout.getPoints())
        {
            if (!p.getName().startsWith(station + " (")) continue;

            if (p.getName().toLowerCase().contains(facing)) return p;
        }

        return null;
    }

    /**
     * The Point for a station, choosing the copy that faces a given way where the square has copies.
     *
     * A square trains can enter from two directions is emitted as one Point per arrival side, and the
     * builder puts the direction in the name - "BottomMainC (westbound)". Where a square has only one
     * copy the direction is not modelled and the name is bare.
     */
    private static Point pointFor(String station, String facing)
    {
        // Only the copies a train may actually be SENT to.
        //
        // A square trains can enter from two directions is emitted as one Point per arrival side, and
        // not every copy is a destination - "BottomMainA (westbound) is not a station" is what the
        // model says about the other kind. Placing is refused there, so the choice has to be made
        // among the copies that are stations.
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

        if (facing == null) return stations.get(0);

        for (Point p : stations)
        {
            if (p.getName().toLowerCase().contains(facing)) return p;
        }

        // FAILED, not skipped.  A skip here would hide the one thing this arrangement was chosen to
        // exercise, and read as green while asking nothing.
        fail(station + " has no copy facing " + facing + " that a train can be placed on. Copies "
            + "found: " + all);

        return null;
    }

    /**
     * Waits for every driving thread to finish, so the arrangement being asserted is a settled one.
     */
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

    /**
     * Where everybody is, for a failure message somebody can act on.
     */
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
