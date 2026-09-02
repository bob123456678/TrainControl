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
 * Five trains go out from ordinary platforms, come back, and face the way they set off.
 *
 * Adam asked first for an arrangement on the parking berths, and then, having watched what that made
 * the planner do, for this one instead: **"we should have trains on regular stations that need to go
 * home, not parked trains that need to go park somewhere else."**
 *
 * The berth version asked a question nobody wanted answered.  On his railway a train in ParkingTrack6
 * homed at TunnelLeftPark has to climb from one page to the other, run past BottomMainA, reverse, and
 * only then park - three hops of shunting, and a hard planning problem that says nothing about whether
 * Return Home works.  Measured while deciding to move: from ParkingTrack6 the right-click menu offers
 * twenty-five clear paths to thirteen destinations and TunnelLeftPark is not one of them.  So the
 * failure there was pathing rather than anything switched off, and it was the wrong thing to be
 * testing.
 *
 * These five stand on plain through platforms - no parking flag, no compulsory reversal, nothing out of
 * service - which is where trains on this railway actually are.  Each is homed where it starts, the
 * railway runs itself for a while, and then everybody is sent home.
 *
 * **Facing is the Point, not the square.** A square that trains can enter from two directions is
 * emitted as one Point per arrival side, and which copy a train stands on IS which way it is facing -
 * the model has no separate direction. So "facing the same direction as when they started" is asserted
 * as the same Point, and on a split platform that is strictly stronger than being home.
 *
 * **It is registered in build.xml and it is expected to be RED until Return Home stages this.**  Adam,
 * on being told the outcome was NO_PLAN_FOUND: "ok, so that test should then be red."  Nothing moves
 * when Return Home is pressed for this arrangement; a test for something that does not work belongs in
 * the battery being red rather than in an exclusion table being quiet.
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
public class testTrainsComeHomeToTheirPlatforms
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

    /**
     * Ordinary through platforms, which is where trains actually stand (Adam, 2026-09-01).
     *
     * "We should have trains on regular stations that need to go home, not parked trains that need to
     * go park somewhere else."
     *
     * The arrangement used to put three of the five in parking berths.  That made every journey home a
     * berth-to-berth shunt - on his railway ParkingTrack6 to TunnelLeftPark is up from one page to
     * another, past BottomMainA, reverse, and only then park - which is a hard and unrepresentative
     * question to ask of a planner, and not the one this test is for.
     *
     * These five are plain platforms: no `parking`, no `mustReverse`, nothing switched off.  A train
     * standing on one is somewhere autonomy will move on its own, and its way home is an ordinary
     * journey rather than a shunt.
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

        // A FLOOR ON THE FIXTURE: these have to be ordinary platforms, or the test is quietly back
        // to asking the berth question.
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

        // NO HAND-START.  A berth is not an automatic destination and a train sitting in one will not
        // be moved by autonomy, so the old arrangement had to be set going by hand.  A train on a
        // platform is somewhere autonomy picks up on its own, and starting it by hand would be the test
        // arranging the run rather than watching it.

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

            assertTrue(plan.isPossible(),
                "no way home from " + arrangement + " (outcome " + plan.getOutcome()
                + ", blocked " + plan.getBlocked() + "). Five trains that set off from ordinary "
                + "platforms have to be able to get back to them; the per-train reachability printed "
                + "above says which one cannot and whether it is the graph or the plan that is short");

            // KEPT, so that a train which does not arrive can be told apart from one the plan never
            // undertook to move.  Those are different faults - a plan that is short, and a move that
            // did not stick - and without the plan in front of you the assertion below cannot say
            // which it was looking at.
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

            // THE SAME ARRIVAL, which is what "the way it started" means here.
            //
            // A square that can be entered from two directions is emitted as one Point per arrival
            // side, so the copy IS the facing, and coming back on the other one is coming back the
            // wrong way round.  A square trains may TURN at emits two copies for the same arrival
            // though - "LowerFront (eastbound)" and "LowerFront (eastbound, reverse)" - and those two
            // differ in what the train does next, not in which way it came in.  Demanding the exact
            // copy failed a train standing on its own platform, having arrived exactly as it set off.
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
     * A copy's name with the turning twin folded onto the plain one.
     *
     * "LowerFront (eastbound, reverse)" and "LowerFront (eastbound)" are one arrival and two things a
     * train may do next; only the arrival is the facing.  Fold them and a train that came home the way
     * it left reads as home, while "TopMainR1 (northbound)" and "TopMainR1 (southbound)" stay the two
     * different answers they are.
     *
     * @param point a Point's name, or anything without a bracket, which is returned unchanged
     * @return the name of the arrival it belongs to
     */
    private static String arrival(String point)
    {
        String turning = ", reverse)";

        if (!point.endsWith(turning)) return point;

        return point.substring(0, point.length() - turning.length()) + ")";
    }

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

        // THE HOMES THE LAYOUT ACTUALLY HOLDS, which is not the same as the ones the test asked for.
        // A home can be dropped after it is set - one home per platform, and the square is the unit -
        // and a locomotive with no home is a free agent the planner may move anywhere and leave there.
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

        // AND THEIR HOMES, which lifting the trains does not touch.
        //
        // This is the defect that made the whole class lie for a day.  His configuration records homes
        // for his own locomotives - EN57-203 at Tunnel, 75 407 DB at LowerFront, and two more - and
        // those entries survive the purge above, because a home is an assignment rather than a train.
        //
        // A square can be the home of only ONE locomotive: `claimHome` refuses a claim on a square
        // another home already holds, and refuses it SILENTLY, which is right for the application and
        // merciless here.  So placing a test train on Tunnel or on LowerFront claimed nothing, and two
        // of the five went into the run with no home at all.
        //
        // A locomotive with no home is a free agent - the planner may move it anywhere and is under no
        // obligation to bring it back - so the plan really did leave one parked in a siding, and
        // `misplaced` was right not to count it.  Every "the planner abandoned a train" reading of this
        // test came from here, and so did its flakiness: which of his homes a test train landed on
        // depended on where the twenty-second run had left everybody.
        layout.clearHomeLocomotives();

        System.out.println("CLEARED his home assignments too - "
            + layout.getHomeStations().size() + " left");

        String[] where = new String[]
        {
            PLATFORMS[0], PLATFORMS[1], PLATFORMS[2], PLATFORMS[3], PLATFORMS[4]
        };

        // NO FACING IS ASKED FOR, since the arrangement moved onto ordinary platforms.
        //
        // It used to name "east" for BottomMainA, because Adam had said which way that train stood.
        // Carried over to a different set of platforms it became a demand the railway cannot meet -
        // Tunnel has no eastbound copy that is a station at all, and the placement door said so.
        //
        // Nothing is lost by leaving it open.  What this test asserts is that every train comes back to
        // the SAME Point it left, and which copy that is gets recorded at placement time; the copy the
        // train happens to be given is the copy it has to return to either way.
        String[] facing = new String[] {null, null, null, null, null};

        for (int i = 0; i < where.length; i++)
        {
            model.newMM2Locomotive(name(i), FIRST_ADDRESS + i);

            Locomotive loc = model.getLocByName(name(i));

            assertNotNull(loc, "could not create " + name(i));

            // A MIXTURE, deliberately: the first three cannot reverse and the last two can, so the
            // planner has to bring home both kinds over the same railway.  Which platform gets which
            // does not matter and is not asserted - what matters is that neither kind is left out.
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

        if (facing == null)
        {
            // THE SAME PREFERENCE THE FLOOR APPLIES.  Asking for "an ordinary platform" and then
            // standing the train on whichever copy came first put a locomotive on
            // "LowerFront (eastbound, reverse)" - a terminus - in a test written to keep termini out.
            Point ordinary = ordinaryCopy(station);

            return ordinary != null ? ordinary : stations.get(0);
        }

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
     * The copy of a square a train may be sent to and does not have to reverse at.
     *
     * ORDINARY IS PREFERRED, not merely accepted.  One square can emit several destination copies -
     * LowerFront emits "(eastbound)" and "(eastbound, reverse)" both - and taking the first one found
     * hands back whichever the builder happened to emit first.  When that was the reversing copy, the
     * floor refused a platform that is perfectly ordinary and the placement stood a train on a
     * terminus, in a test whose whole point is that these are ordinary platforms.
     *
     * THE SQUARE, not anything whose name begins with it: a split platform is emitted as
     * "Name (facing)", so a copy is the exact name or the name and a bracket. `startsWith` alone
     * matched TunnelCenterPark for "Tunnel", which is a parking terminus.
     *
     * @param station the square's authored name
     * @return its ordinary copy; the first copy trains may be sent to if every copy reverses; null if
     *         no copy of that name accepts a train at all
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
