package org.traincontrol.automation;

import java.util.ArrayList;
import org.traincontrol.base.Accessory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import org.traincontrol.base.Locomotive;

/**
 * Plans the moves that put every locomotive back on its home station.
 *
 * Home is where a locomotive was when it first appeared on the graph - see Layout.claimHome.  A
 * locomotive with no home is a *free agent*: it may be moved out of the way but never has to end
 * anywhere in particular, and it doubles as the spare capacity that lets a blocked arrangement unwind.
 *
 * <b>This is a rearrangement problem, not a routing one.</b>  A station holds one locomotive, so a
 * train cannot go home while another sits there, and that one may need to move through the station the
 * first is leaving.  It is pebble motion on a graph: deciding feasibility is polynomial, finding the
 * shortest solution is NP-hard, and the shortest solution is worth nothing here - staging happens once,
 * before a session, and nobody counts the moves.
 *
 * <b>Nothing here touches the live layout.</b>  snapshot() copies the occupancy and the planner works
 * on that copy, because the question "could this locomotive move there *after* three other moves" has
 * no answer on live state.  In particular it does NOT call Layout.isPathClear, which consults real s88
 * feedback, isAutoRunning() and maxActiveTrains - none of which can be evaluated for a hypothetical
 * future.  The shadow rule is simpler and exact for a layout at rest: a locomotive at rest occupies
 * exactly its own station's sensor, so a point is passable iff its s88 is not one an occupied station
 * reports.  Occupancy is keyed by <b>sensor address, not by point</b>: nothing stops two points sharing
 * an s88, and it is the sensor that isPathClear interrogates.
 *
 * Plans are sequential - one train at a time - which is what keeps edge locks, activeLocomotives and
 * maxActiveTrains out of the model entirely: in every state the planner considers, nothing is moving.
 * The runtime may still overlap the moves it is given, provided it respects their order.
 *
 * @author Adam
 */
public final class HomeStaging
{
    /** Configurations examined before the search gives up.  Reached only on large, tightly packed
     *  layouts; a plain greedy pass solves the ordinary case without searching at all. */
    private static final int SEARCH_LIMIT = 50000;

    /**
     * Wall clock, because a state count cannot bound the time this takes.
     *
     * Expanding one state runs firstClearRoute once per locomotive per station, and each of those is a
     * breadth-first search over the graph.  On a 62-point layout that is milliseconds per state, so the
     * old ceiling of 200000 states was minutes of work - and an arrangement with no solution reached it
     * every time, presenting as a frozen application rather than as NO_PLAN_FOUND.
     *
     * NO_PLAN_FOUND already says "may still be possible", which is exactly the right claim to make when
     * the answer is cut short.  What was wrong was how long it took to say it.
     */
    private static final long SEARCH_BUDGET_MS = 15000;

    /** Expansions allowed per route search.  A point may now be revisited under different accessory
     *  settings, so the search is no longer bounded by the number of points. */
    private static final int ROUTE_SEARCH_LIMIT = 20000;

    private final Layout layout;

    /** Station -> occupant, at the moment of the snapshot.  The whole of the planner's world. */
    private final Map<Point, Locomotive> start;

    private final Map<Locomotive, Point> homes;

    /** Every station a locomotive could rest on, snapshot once. */
    private final List<Point> stations;

    /** Sensors that were genuinely reading occupied when the snapshot was taken. */
    private final Set<String> sensorsSet;

    /** Which points report each sensor, so a sensor can be released when its point is vacated. */
    private final Map<String, List<Point>> pointsBySensor;

    /**
     * Which points are copies of one square, which is what the RUNTIME means by "the same piece of
     * track" - `Point.getBlockLocomotive` asks the block and nothing else.
     *
     * The planner had no block index at all and used the shared sensor as a stand-in for it.  That is
     * a superset on a builder-emitted layout, where every copy of a square carries that square's s88 -
     * but it is not a superset where the square has NO sensor, and there the planner was the LOOSER
     * half: a train standing on the unnamed copy of a watched square was invisible to it, so it planned
     * an arrival the runtime then refused.  That is OB-073's symptom arriving by a second door.
     */
    private final Map<String, List<Point>> pointsByBlock;

    /** Stations with zero incoming edges - hand-staged launch pads; see snapshot. */
    private final Set<String> launchPads;

    private HomeStaging(Layout layout, Map<Point, Locomotive> start, Map<Locomotive, Point> homes,
        List<Point> stations, Set<String> sensorsSet, Map<String, List<Point>> pointsBySensor,
        Map<String, List<Point>> pointsByBlock, Set<String> launchPads)
    {
        this.launchPads = launchPads;
        this.layout = layout;
        this.start = start;
        this.homes = homes;
        this.stations = stations;
        this.sensorsSet = sensorsSet;
        this.pointsBySensor = pointsBySensor;
        this.pointsByBlock = pointsByBlock;

    }

    /**
     * Copies everything the planner needs out of a layout.  Nothing afterwards reads live state.
     * @param layout
     * @return
     */
    public static HomeStaging snapshot(Layout layout)
    {
        Map<Point, Locomotive> occupancy = new LinkedHashMap<>();
        List<Point> stations = new ArrayList<>();
        Set<String> sensorsSet = new HashSet<>();
        Map<String, List<Point>> pointsBySensor = new HashMap<>();
        Map<String, List<Point>> pointsByBlock = new HashMap<>();

        for (Point p : layout.getPoints())
        {
            if (p.isDestination() && p.isActive()) stations.add(p);

            if (p.getCurrentLocomotive() != null) occupancy.put(p, p.getCurrentLocomotive());

            // The block index, taken here for the same reason as everything else in this method: the
            // planner must not read live state later.  Only squares emitted as more than one Point
            // carry a block at all, so most layouts put nothing in this map.
            if (p.getBlock() != null)
            {
                if (!pointsByBlock.containsKey(p.getBlock()))
                {
                    pointsByBlock.put(p.getBlock(), new ArrayList<>());
                }

                pointsByBlock.get(p.getBlock()).add(p);
            }

            if (p.getS88() != null)
            {
                if (!pointsBySensor.containsKey(p.getS88()))
                {
                    pointsBySensor.put(p.getS88(), new ArrayList<>());
                }

                pointsBySensor.get(p.getS88()).add(p);

                // READ, never inferred.  A stationary locomotive does not necessarily hold its sensor -
                // in simulation the feedback is pulsed and clears again behind the train - so deducing
                // "sensor busy" from "point occupied" describes a layout that does not exist.  It also
                // gets shared addresses badly wrong: a bypass exists precisely so a train can pass an
                // occupied platform, and the two routinely report the same sensor.
                if (layout.isFeedbackOccupied(p.getS88())) sensorsSet.add(p.getS88());
            }
        }

        // Launch pads: stations with zero incoming edges.  Some layouts stage trains on one-way
        // tracks - departure edges only - and re-stage by hand; the author's own graph carries
        // nineteen of them.  The topology is the declaration of intent, and two rules follow from
        // it below: a positional home on one is not a home, and the search never moves a locomotive
        // off one.
        Set<String> launchPads = new HashSet<>();

        for (Point p : layout.getPoints())
        {
            if (layout.getIncomingEdges(p).isEmpty()) launchPads.add(p.getName());
        }

        // A POSITIONAL home on a launch pad stops being a home once the locomotive has LEFT it.
        // Away, the claim is unsatisfiable - nothing can re-enter the pad - and because a plan is
        // all-or-nothing, keeping the entry made Return Home refuse to move ANYTHING for the rest
        // of the session.  Still standing there, the claim is simply satisfied, and dropping it
        // anyway turned a perfectly staged layout's ALREADY_HOME into NO_HOMES - which the
        // no-way-back test caught on the very fixture its javadoc tells a war story about.  An
        // ASSIGNED home keeps the strict contract either way: the operator chose it, so an
        // unreachable one still answers IMPOSSIBLE with the locomotive named.
        Map<Locomotive, Point> homes = new LinkedHashMap<>(layout.getHomeStations());

        for (Iterator<Map.Entry<Locomotive, Point>> it = homes.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry<Locomotive, Point> entry = it.next();

            // Identity, not a name.  This read getName().equals(getHomeLoc()) and went on compiling
            // when the home became a Locomotive - String.equals takes an Object, so it simply answered
            // false for ever, and every assigned home was quietly treated as positional.  Nothing
            // failed; the strict contract just stopped applying.
            boolean assigned = entry.getKey().equals(entry.getValue().getHomeLoc());
            // The SQUARE, not the Point (MT-165, second round).  Asking `occupancy.get(home)` names
            // one copy of a platform, so a train standing on the other copy of its own home read as
            // not standing on it - and this entry is then dropped when the home is a launch pad.
            boolean standingOnIt = atHome(entry.getValue(), locationOf(occupancy, entry.getKey()));

            if (!assigned && !standingOnIt && launchPads.contains(entry.getValue().getName()))
            {
                it.remove();
            }
        }

        return new HomeStaging(layout, occupancy, homes, stations,
            sensorsSet, pointsBySensor, pointsByBlock, launchPads);
    }

    // ---------------------------------------------------------------------------------------------
    // The answer
    // ---------------------------------------------------------------------------------------------

    /**
     * Why a staging run is or is not available.
     *
     * IMPOSSIBLE and NO_PLAN_FOUND are deliberately different answers.  The first is a proof - some
     * locomotive has no route to its home under any arrangement of the others.  The second means the
     * search ran out of room, which is not the same claim, and reporting it as impossible would be a
     * statement this class cannot support.
     */
    public enum Outcome
    {
        /** Every locomotive with a home is already on it */
        ALREADY_HOME,
        /** A plan exists - see getMoves */
        READY,
        /** No locomotive is placed on the graph */
        NO_LOCOMOTIVES,
        /** Locomotives are placed, but none has a home to return to */
        NO_HOMES,
        /** At least one locomotive cannot reach its home at all - see getBlocked */
        IMPOSSIBLE,
        /** No plan was found within the search limit.  May still be possible. */
        NO_PLAN_FOUND,

        /**
         * A locomotive is held on more than one point, so where it is standing cannot be said at all
         * - see getBlocked.
         *
         * A locked path reserves every point along it for one locomotive at once, and a path that
         * failed part-way through unlocking leaves those reservations behind.  Nothing in the model
         * distinguishes a reservation from a train, so the planner has several equally good answers to
         * "where is it", and departing from the wrong one drives a real train from a place it is not.
         */
        POSITION_AMBIGUOUS,

        /** Something is already moving - not a conclusion about the layout, just the wrong moment. */
        LOCOMOTIVES_RUNNING
    }

    /**
     * One locomotive, one path, to be executed in list order.
     */
    public static final class Move
    {
        private final Locomotive loc;
        private final List<Edge> path;

        Move(Locomotive loc, List<Edge> path)
        {
            this.loc = loc;
            this.path = path;
        }

        public Locomotive getLocomotive()
        {
            return this.loc;
        }

        public List<Edge> getPath()
        {
            return this.path;
        }

        public Point getEnd()
        {
            return this.path.get(this.path.size() - 1).getEnd();
        }

        @Override
        public String toString()
        {
            return this.loc.getName() + " -> " + getEnd().getName();
        }
    }

    /**
     * The result of planning: whether it can run, and what it would do.
     */
    public static final class Plan
    {
        private final Outcome outcome;
        private final List<Move> moves;
        private final List<Locomotive> blocked;

        Plan(Outcome outcome, List<Move> moves, List<Locomotive> blocked)
        {
            this.outcome = outcome;
            this.moves = moves;
            this.blocked = blocked;
        }

        public Outcome getOutcome()
        {
            return this.outcome;
        }

        /** True when a staging run can be offered - there is something to do and a way to do it. */
        public boolean isPossible()
        {
            return this.outcome == Outcome.READY;
        }

        /** The moves, in the order they must be applied */
        public List<Move> getMoves()
        {
            return Collections.unmodifiableList(this.moves);
        }

        /** Locomotives that cannot reach their home, when the outcome is IMPOSSIBLE */
        public List<Locomotive> getBlocked()
        {
            return Collections.unmodifiableList(this.blocked);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Planning
    // ---------------------------------------------------------------------------------------------

    /**
     * Works out how to get every homed locomotive back to its home.
     * @return
     */
    public Plan plan()
    {
        Outcome trivial = triage();

        if (trivial != null) return new Plan(trivial, empty(), noLocs());

        // A locomotive in two places at once, which nothing below this line can reason about (SG-A3).
        //
        // A locked path reserves every point along it for the one locomotive at once - that is how a
        // junction behind the train is held against a second train reaching it another way - and a path
        // that failed part-way through unlocking leaves those reservations standing.  Nothing in the
        // model tells a reservation from a train: reserve() and setLocomotive() write the same field,
        // and the only difference between them is whether the other copies are swept.
        //
        // What went wrong before this was in the counting.  `misplaced` counted map ENTRIES, so one
        // train counted twice, and `apply` moved it by removing the first entry it found, leaving the
        // other standing for ever - so `misplaced == 0` could not be reached and the answer was
        // NO_PLAN_FOUND with no moves, for a train with a clear run to an empty home.
        //
        // **Counting it properly would be the wrong fix.**  It produces a plan, and the plan departs
        // from whichever of the points locationOf yields first - so a real train is driven from a
        // place it is not standing.  The doctrine written all through this class is that NO_PLAN_FOUND
        // claims less than it could and claims nothing false; a guessed origin claims something that
        // may be false, at the highest price this project has.
        //
        // So it is reported instead, with the locomotive's name, and the operator's remedy is the
        // ordinary one: place the train on the square it is actually on, which sweeps the rest.
        List<Locomotive> ambiguous = new ArrayList<>();

        for (Locomotive l : this.start.values())
        {
            if (ambiguous.contains(l)) continue;

            int held = 0;

            for (Locomotive at : this.start.values())
            {
                if (at.equals(l)) held++;
            }

            if (held > 1) ambiguous.add(l);
        }

        if (!ambiguous.isEmpty()) return new Plan(Outcome.POSITION_AMBIGUOUS, empty(), ambiguous);

        // A locomotive with no route to its home at all cannot be helped by moving anything else, and
        // it is the only impossibility this class can prove.  Locomotives already standing on their
        // home are skipped: asking for a route from a point back to itself is a different question,
        // and on a layout without a loop the answer is "none" - which would report a graph that is
        // entirely in order as impossible.
        List<Locomotive> unreachable = new ArrayList<>();

        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || atHome(home, locationOf(this.start, l))) continue;

            // A home the locomotive could never be parked at - inactive, excluding it, too short for
            // it, or a terminus it cannot reverse out of - is impossible by construction: no move can
            // ever end there, so the goal is unreachable however the search is run.  Without this the
            // search burns its whole budget and then reports "no arrangement found - it may still be
            // possible", which is wrong twice over and sends the operator shunting for nothing.
            // A locomotive that cannot leave where it stands is as unreachable as one with no route:
            // firstClearRoute refuses an inactive origin, so the search can only exhaust and answer
            // "maybe".  One flag test turns that into a proof, the same upgrade the pairwise goal scan
            // below gives conflicting homes.
            //
            // The STATELESS canRest, and no FR-001 test at all (FBR-B1, then FBR-B2).
            //
            // The OB-073 fix put the state-aware canRest here, which reads the starting occupancy - so a
            // locomotive merely standing on a watched square proved the goal unreachable, including one
            // being staged elsewhere whose departure is the plan's own first move.  The repair narrowed
            // that to blockers staging "will never move", meaning one with no home or one already
            // standing on its home.  Both halves of that were wrong as well:
            //
            //   - `astar` moves a locomotive off its own home freely.  The only exemption is the launch
            //     pad, and the goal test is `misplaced == 0` - so a locomotive that steps aside and
            //     comes back is an ordinary three-move plan, and is exactly the plan these
            //     arrangements need.
            //   - A homeless locomotive is a free agent the expansion moves wherever it likes, and it
            //     barely exists anyway: `Layout.claimHome` gives a hand-placed one a positional home
            //     where it is put.
            //
            // There is nothing here to keep, and "no state-independent statement can be made about an
            // FR-001 blocker" - which is how this was first written - is too strong (FSR-C3). One can:
            // two homes each held back by a square the other's occupant must end on is impossible from
            // the structure alone, whoever is standing where. That is real and it is not caught here;
            // the search exhausts and answers NO_PLAN_FOUND, which claims less than it could but claims
            // nothing false. Filed rather than added, because the last two things put into this scan
            // were both wrong and both looked obviously right.  `connected`, four lines down, states the doctrine: "A route blocked
            // merely by another train is not impossible - moving that train is exactly what the planner
            // is for."  The state-aware canRest inside firstClearRoute is the one OB-073 ever needed,
            // and it stays: it is what makes the plans the search returns executable.
            // THE SQUARE, and both questions of the same copy (2026-08-31).
            //
            // This asked `canRest(l, home)` and `connected(from, home)` about the one copy that
            // happens to carry the home. Since the home became the square that is the wrong copy as
            // often as the right one - which copy carries it is AutonomyBuilder's choice - so a
            // locomotive that cannot reverse was called unreachable from a platform whose other copy
            // is a through road it could stand on perfectly well, after the editor had accepted the
            // home. A terminus turns a train round as it ARRIVES, so it is only the copies that turn
            // that this locomotive cannot use.
            if (!locationOf(this.start, l).isActive()
                || !locationOf(this.start, l).isDestination()
                || !canGetHome(l, locationOf(this.start, l), home)) unreachable.add(l);
        }

        // Goals that conflict with each other, which no arrangement can satisfy either.  Two homes on
        // one detection section is the easiest wrong click on a layout that shares addresses - a
        // platform and its bypass - and nothing warns when the assignment is made, because canBeHome is
        // a one-station question.  Without this the search simply exhausts: correct, since
        // NO_PLAN_FOUND claims less than it could, but it spends the whole budget to say "maybe" about
        // something provable in a pairwise scan.
        for (Map.Entry<Locomotive, Point> a : this.homes.entrySet())
        {
            if (!this.start.containsValue(a.getKey())) continue;

            for (Map.Entry<Locomotive, Point> b : this.homes.entrySet())
            {
                if (a.getKey().equals(b.getKey()) || !this.start.containsValue(b.getKey())) continue;

                if (!sharesSection(a.getValue(), b.getValue())) continue;

                // Both already parked: nothing arrives, so nothing is checked (SG-A1).
                //
                // The same exemption the cycle scan below carries, and for the same reason.  What is
                // proved here is that the SECOND arrival onto a shared detection section is refused -
                // and neither of these trains arrives, because both are standing on their own homes
                // already.  The arrangement is finished for this pair before the run begins.
                //
                // The cost of leaving it out is not a worse plan but no plan: IMPOSSIBLE refuses the
                // WHOLE staging run, so a third locomotive that only needed driving to the next
                // platform never moves, and the two names the operator is handed are the two trains
                // that are already where they belong.
                //
                // NOT reachable on Adam's own railway, which is worth saying because the review that
                // raised this said it was.  It had read the hand-written autonomy.json, where
                // BottomMainCTerm exists; the 3.0.0 diagram derives a graph in which it does not, and
                // no two DIFFERENT station squares on it share a sensor - every shared-sensor group is
                // one square emitted per arrival side.  A home on such a square is refused outright by
                // whyNotAHome, on his own ruling.
                //
                // The shape is ordinary elsewhere: AutonomyBuilder is explicit that a station, its
                // approach guard and a reversing point can be three Points on one feedback.
                //
                // Only one of the two has to be away for the proof to hold again, which is the case
                // testTwoActivePointsSharingASensorAreNeverBothOccupied holds.
                if (atHome(a.getValue(), locationOf(this.start, a.getKey()))
                    && atHome(b.getValue(), locationOf(this.start, b.getKey()))) continue;

                if (!unreachable.contains(a.getKey())) unreachable.add(a.getKey());
                if (!unreachable.contains(b.getKey())) unreachable.add(b.getKey());
            }
        }

        // And goals that hold EACH OTHER back, which is the second thing a pairwise scan can prove
        // (OB-085, raised as FSR-C3).
        //
        // The comment above used to say there is "no state-independent statement to make about an
        // FR-001 blocker". That is too strong, and here is the counterexample. Suppose HS C is held
        // back while HS D is occupied, and HS D is held back while HS C is occupied, and the two are
        // the homes of two different locomotives. In the finished arrangement each train stands on its
        // own home, so each is standing on the square that closes the other station - and a station is
        // only checked when a train ARRIVES, so the question is which of the two arrives last. Whoever
        // it is, the other train is already parked on the square holding this one back. No ordering
        // works, and no occupancy has been read to know it.
        //
        // What it costs without this is not wrongness but time and a weaker answer: the search
        // exhausts its budget and reports NO_PLAN_FOUND - "no arrangement found, it may still be
        // possible" - about something that is provably not.
        //
        // **The two exemptions are the whole of the care here,** because the last two things put into
        // this scan were both wrong and both looked obviously right:
        //
        //  - ONE direction is not impossible, and must not be reported as such. If only HS C watches
        //    HS D, the arrangement is simply ordered: park at HS C first, while HS D is still empty,
        //    then park at HS D, which nothing watches. That is an ordinary plan and the search finds
        //    it. testAOneWayHoldIsJustAnOrdering is the control that holds this.
        //  - Two trains ALREADY on their own homes need no arrival at all, so nothing is ever checked
        //    and the arrangement stands as it is. Reporting that pair as impossible would call a
        //    railway that is already correct unfixable. Only one of the two has to be away for the
        //    proof to hold: a train sitting on its home can step aside and come back, but coming back
        //    is an arrival, and it finds the other train parked on the square that holds it.
        //
        // Pairwise, like the scan above, and deliberately not generalised to longer cycles. A
        // three-station cycle is equally impossible and is still answered with NO_PLAN_FOUND, which
        // claims less than the truth and claims nothing false. Pairs are what a wrong click produces.
        // A SNAPSHOT of what the scans above found, not the list this loop is extending.
        //
        // The skip below asks whether a locomotive was already proved stuck for its own reasons. Read
        // against the live list, an entry this very loop had just added would answer that question too
        // - so one proved cycle silently suppressed another. A review demonstrated it: three
        // locomotives in two genuine mutual cycles, and the second cycle's pair went unnamed. The
        // operator repairs both named relations, re-runs, and it is still impossible.
        //
        // Which is the same harm the skip was added to remove, pointing the other way.
        List<Locomotive> alreadyStuck = new ArrayList<>(unreachable);

        for (Map.Entry<Locomotive, Point> a : this.homes.entrySet())
        {
            if (!this.start.containsValue(a.getKey())) continue;

            for (Map.Entry<Locomotive, Point> b : this.homes.entrySet())
            {
                if (a.getKey().equals(b.getKey()) || !this.start.containsValue(b.getKey())) continue;

                // Two homes on one piece of track are the scan above's business, and asking this one
                // about them would double-report the same fault under a different explanation.
                if (a.getValue() == null || b.getValue() == null) continue;
                if (a.getValue().equals(b.getValue()) || onOneTrack(a.getValue(), b.getValue())) continue;

                if (!watchesTrack(a.getValue(), b.getValue())) continue;
                if (!watchesTrack(b.getValue(), a.getValue())) continue;

                // Both already parked: nothing arrives, so nothing is checked.
                if (atHome(a.getValue(), locationOf(this.start, a.getKey()))
                    && atHome(b.getValue(), locationOf(this.start, b.getKey()))) continue;

                // And neither of them already proved unreachable for its own reasons.
                //
                // The cycle argument is "whichever of the two arrives last finds the other already
                // parked on the square that holds it". If one of them can never get home at all - no
                // route, a home it cannot rest at, two homes on one section - then it never parks
                // there, the square stays clear, and the other one is free to arrive.
                //
                // The plan is impossible either way, so this changes no outcome. What it changes is
                // the LIST, and the list is the part the operator reads: naming a locomotive that
                // could get home sends them looking for a fault that is not there. Found by rereading
                // this scan rather than by a test, which is worth saying - it is the third over-claim
                // in it, and the first two were also things that looked obviously right.
                if (alreadyStuck.contains(a.getKey()) || alreadyStuck.contains(b.getKey())) continue;

                if (!unreachable.contains(a.getKey())) unreachable.add(a.getKey());
                if (!unreachable.contains(b.getKey())) unreachable.add(b.getKey());
            }
        }

        if (!unreachable.isEmpty()) return new Plan(Outcome.IMPOSSIBLE, empty(), unreachable);

        List<Move> moves = search();

        if (moves == null) return new Plan(Outcome.NO_PLAN_FOUND, empty(), noLocs());

        return new Plan(Outcome.READY, moves, noLocs());
    }

    /**
     * Compares the planner's idea of where each locomotive may go against the runtime's, for the state
     * as it stands right now.
     *
     * The planner has to answer that question for hypothetical futures, which is why it cannot simply
     * call isPathClear - that reads live feedback.  So it re-implements the rules, and every time a
     * rule was mis-copied the result was a plan the runtime then refused, or no plan where one existed.
     * Re-implementing a specification is only safe if you check it against the original, and this is
     * that check: for the ONE state where both can be asked - the present - the two answers must match.
     *
     * Logged rather than enforced.  A divergence is a defect in this class, not a reason to refuse the
     * operator a staging run, and the runtime re-validates every move before driving it anyway.
     *
     * @return the number of disagreements
     */
    public int auditAgainstRuntime()
    {
        int disagreements = 0;
        Set<String> blocked = blockedSensors(this.start);

        for (Map.Entry<Point, Locomotive> e : this.start.entrySet())
        {
            Locomotive loc = e.getValue();

            // A locomotive standing on a deactivated point is the third correct divergence, and it is
            // the mirror of the inactive-destination one below: getPossiblePaths is asked at rest,
            // where the runtime's inactive rule is not in force, so it offers departures the planner
            // refuses because staging executes with autonomy running.  Comparing them here would
            // report the planner as wrong for applying the rule it is supposed to apply.
            if (!e.getKey().isActive()) continue;

            Set<Point> runtimeSays = new HashSet<>();

            for (List<Edge> path : this.layout.getPossiblePaths(loc, true))
            {
                runtimeSays.add(path.get(path.size() - 1).getEnd());
            }

            Set<Point> plannerSays = new HashSet<>();

            for (Point to : this.stations)
            {
                if (firstClearRoute(this.start, blocked, loc, e.getKey(), to) != null) plannerSays.add(to);
            }

            for (Point p : runtimeSays)
            {
                // Inactive points are the one divergence that is correct rather than a defect: the
                // runtime skips its inactive-point rule unless autonomy is already running, so at rest
                // it offers parking tracks that it would refuse once a timetable is under way.  The
                // planner reasons about the running case, so it is right to leave them out.
                if (!p.isActive()) continue;

                // And the same for exclusions, for a closely related reason: the oracle here is
                // getPossiblePaths, which filters destinations on occupancy and station-ness but not on
                // exclusion - pickPath does that separately.  So it offers stations the planner's
                // canRest correctly refuses, and every audit on a layout with a free excluded station
                // reported a disagreement between two runtime methods rather than a planner defect.
                if (p.getExcludedLocs().contains(loc)) continue;

                // And the fourth, in the same shape as the three above it: FR-001 (DR-B1).
                //
                // OB-073 added the rule to both sides on different terms.  The runtime's copy is fenced
                // behind isAutoRunning - it shapes what AUTONOMY chooses, and a person dispatching by
                // hand is looking at the railway - and this audit runs from planReturnToHome with the
                // layout at rest.  The planner's copy applies always, because staging executes with
                // autonomy running.  So on any layout using FR-001, a train standing on a watched
                // square made the audit accuse the planner of a defect for applying the rule it is
                // supposed to apply - a false accusation from the one instrument that exists to find
                // real divergence, in a channel only read when something else is already being chased.
                //
                // The RUNTIME'S question, not the planner's (automation review, C).
                //
                // This asked `plannedOccupancy(this.start)` - which is exactly what `canRest` asks,
                // on exactly these arguments, since firstClearRoute is called from here with
                // this.start. The two cancelled: the planner dropping p and the exemption skipping p
                // happened under identical conditions, so a planner mis-copy of FR-001 could never
                // produce a disagreement. The comment that stood here claimed the narrowing bought
                // visibility of exactly that, and it did not.
                //
                // Asking the live-block variant instead keeps the exemption honest: it skips the
                // destinations the RAILWAY would refuse, so the planner refusing one for its own
                // reasons - a sensor sibling, or a mis-copied rule - still shows up as a divergence,
                // which is the only thing this instrument exists for.
                //
                // No isAutoRunning fence on it, like the three above: when autonomy IS running,
                // getPossiblePaths applies the rule itself through isPathClear, so the destination is
                // not in runtimeSays and there is nothing here to skip.
                if (Point.heldBackBy(p, loc) != null) continue;

                if (!plannerSays.contains(p))
                {
                    disagreements++;
                    this.layout.logStagingAudit(loc.getName(), p.getName(), true);
                }
            }

            for (Point p : plannerSays)
            {
                if (!runtimeSays.contains(p))
                {
                    disagreements++;
                    this.layout.logStagingAudit(loc.getName(), p.getName(), false);
                }
            }
        }

        return disagreements;
    }

    /**
     * The part of the answer that needs no planning - is there anything to do at all?
     *
     * Separated so a caller can find out cheaply, before asking the user a question it would be rude
     * to ask when the answer is "nothing needed": deciding whether to replace the timetable matters
     * only if a run is actually going to happen.  Reading the occupancy is all this costs; no route is
     * enumerated and nothing is searched.
     *
     * plan() delegates to it, so the two can never disagree about whether there is work.
     *
     * @return the outcome, or null when planning is needed to say more
     */
    public Outcome triage()
    {
        if (this.start.isEmpty()) return Outcome.NO_LOCOMOTIVES;

        boolean anyHomed = false;

        for (Locomotive l : this.start.values())
        {
            if (this.homes.containsKey(l)) anyHomed = true;
        }

        if (!anyHomed) return Outcome.NO_HOMES;

        if (misplaced(this.start) == 0) return Outcome.ALREADY_HOME;

        return null;
    }

    /**
     * Greedy first, then A*.
     *
     * The greedy pass sends home any locomotive whose home is free and reachable right now, repeatedly.
     * On a layout with spare stations that finishes the job without searching, which is the common
     * case.  What it cannot do is move a train *out of the way*, so anything cyclic falls through to
     * the search.
     */
    private List<Move> search()
    {
        Map<Point, Locomotive> state = new LinkedHashMap<>(this.start);
        List<Move> plan = new ArrayList<>();

        boolean progress = true;

        while (progress && misplaced(state) > 0)
        {
            progress = false;

            for (Locomotive l : new ArrayList<>(state.values()))
            {
                Point home = this.homes.get(l);

                if (home == null || atHome(home, locationOf(state, l))) continue;

                List<Edge> path = firstClearRoute(state, blockedSensors(state), l,
                    locationOf(state, l), home);

                if (path != null)
                {
                    apply(state, l, home);
                    plan.add(new Move(l, path));
                    progress = true;
                }
            }
        }

        if (misplaced(state) == 0) return plan;

        List<Move> rest = astar(state);

        if (rest == null) return null;

        plan.addAll(rest);

        return plan;
    }

    /**
     * A* over configurations, with "locomotives not on their home" as the heuristic.
     *
     * Admissible: every move relocates exactly one locomotive, so at least one move per misplaced one
     * is needed.  Cheap, and enough to keep realistic layouts well inside the limit.
     */
    private List<Move> astar(Map<Point, Locomotive> from)
    {
        Map<String, Map<Point, Locomotive>> states = new HashMap<>();
        Map<String, Integer> cost = new HashMap<>();
        Map<String, Integer> score = new HashMap<>();
        Map<String, Move> arrivedBy = new HashMap<>();
        Map<String, String> cameFrom = new HashMap<>();

        String startKey = key(from);
        states.put(startKey, from);
        cost.put(startKey, 0);
        score.put(startKey, misplaced(from));

        // Ordered on a precomputed f-score.  Computing it inside the comparator instead would rescan
        // every locomotive on every comparison - O(log n) comparisons per queue operation, each doing
        // work proportional to the fleet.
        //
        // Each entry carries the score it was queued with rather than reading the score map.  The
        // relaxation below re-scores a state when it finds a cheaper route to it, and that state may
        // already be sitting in the queue: a PriorityQueue compares on demand, so rewriting the map
        // changed an existing entry’s priority in place and broke the heap invariant after the fact,
        // letting polls return states that were not the cheapest.  Plans stayed valid - the closed set
        // makes revisits harmless - but the search spent its budget out of order, and NO_PLAN_FOUND is
        // precisely a statement about that budget.
        PriorityQueue<Scored> open = new PriorityQueue<>((a, b) -> Integer.compare(a.score, b.score));

        open.add(new Scored(startKey, score.get(startKey)));

        Set<String> closed = new HashSet<>();
        int examined = 0;

        long deadline = System.currentTimeMillis() + SEARCH_BUDGET_MS;

        while (!open.isEmpty() && examined < SEARCH_LIMIT && System.currentTimeMillis() < deadline)
        {
            Scored polled = open.poll();
            String currentKey = polled.key;

            if (closed.contains(currentKey)) continue;

            // A cheaper route to this state was found after this entry was queued.  The better entry is
            // still in the queue and will come up in its own place, so this one is stale.
            if (polled.score != score.get(currentKey)) continue;

            closed.add(currentKey);
            examined++;

            Map<Point, Locomotive> current = states.get(currentKey);

            if (misplaced(current) == 0) return rebuild(currentKey, cameFrom, arrivedBy);

            // One set per state, not one per candidate move: nothing moves while we expand this state
            Set<String> blocked = blockedSensors(current);

            for (Locomotive l : new ArrayList<>(current.values()))
            {
                Point at = locationOf(current, l);

                // A locomotive standing on a launch pad stays there unless its assigned home lies
                // elsewhere.  Free agents exist to break deadlocks, and the expansion would happily
                // relocate one when cornered - but a pad has no incoming edges, so the move can
                // never be planner-undone: the hand-staging the pad represents would be destroyed
                // permanently, silently, as a side effect of someone else's plan.  Keyed on the pad
                // and the home, not on homelessness: a satisfied positional pad-home stays in the
                // homes map (that is what makes a staged layout report ALREADY_HOME), so the
                // homeless test alone would have re-opened this exact hole for it.  One already
                // dispatched from its pad is an ordinary free agent wherever it now stands.
                Point ownHome = this.homes.get(l);

                // atHome, for the reason the comparison above it changed: a train resting on the
                // far copy of its own home square is resting at home, and pinning it to the exact
                // copy can ask the A* for a move that no arrival side allows.
                if (this.launchPads.contains(at.getName()) && (ownHome == null || atHome(ownHome, at)))
                {
                    continue;
                }

                for (Point to : this.stations)
                {
                    if (to.equals(at) || current.containsKey(to)) continue;

                    List<Edge> path = firstClearRoute(current, blocked, l, at, to);

                    if (path == null) continue;

                    Map<Point, Locomotive> next = new LinkedHashMap<>(current);
                    apply(next, l, to);

                    String nextKey = key(next);
                    int nextCost = cost.get(currentKey) + 1;

                    if (!cost.containsKey(nextKey) || nextCost < cost.get(nextKey))
                    {
                        states.put(nextKey, next);
                        cost.put(nextKey, nextCost);
                        score.put(nextKey, nextCost + misplaced(next));
                        cameFrom.put(nextKey, currentKey);
                        arrivedBy.put(nextKey, new Move(l, path));
                        open.add(new Scored(nextKey, score.get(nextKey)));
                    }
                }
            }
        }

        return null;
    }

    private List<Move> rebuild(String end, Map<String, String> cameFrom, Map<String, Move> arrivedBy)
    {
        LinkedList<Move> out = new LinkedList<>();
        String at = end;

        while (cameFrom.containsKey(at))
        {
            out.addFirst(arrivedBy.get(at));
            at = cameFrom.get(at);
        }

        return out;
    }

    // ---------------------------------------------------------------------------------------------
    // The shadow rules
    // ---------------------------------------------------------------------------------------------

    /**
     * A route from one station to another that is clear in this hypothetical state, or null.
     *
     * Searches over the points that can actually be entered given who is standing where, rather than
     * enumerating a few routes computed without regard to occupancy and then filtering them.  Those are
     * different questions, and the difference is not academic: on a layout with loops the shortest
     * handful of routes between two stations tend to share the same busy stretch of track, so all of
     * them can be blocked while a longer clear one exists.  Answering the wrong question made the
     * planner report that a locomotive could not get home when it plainly could.
     *
     * Breadth-first, so the route returned is the shortest clear one.
     */
    private List<Edge> firstClearRoute(Map<Point, Locomotive> state, Set<String> blocked, Locomotive loc,
        Point from, Point to)
    {
        if (from == null || to == null || from.equals(to)) return null;

        // The origin is exempt from every other test here - that is what stops the moving train's own
        // sensor blocking its own departure - but not from these two.  isPathClear applies its
        // inactive-point rule to every edge start including the first, and staging executes with
        // autonomy running, so a locomotive standing on a deactivated point would be planned home and
        // then refused at its first edge.
        if (!from.isActive()) return null;

        // And the rule in the very next `if` after that one (SG-A2).
        //
        // "Starting point is not a station - do not pick it in fully autonomous mode", and staging is
        // fully autonomous operation: executeTimetable sets `running`, which is exactly why the
        // reversing-point exclusion had to be moved out of isPathClear and into selection.
        //
        // Missing this cost more than a refused plan.  The run STARTED, the first leg was refused, and
        // the retry loop asked again every two seconds until it abandoned the run saying "the track it
        // needs never became free" - which is not what was wrong.  The track was clear; the train was
        // parked somewhere no automatic path may begin, which is where a hand-placed train sits.
        if (!from.isDestination()) return null;
        if (!canRest(loc, to, state) || state.containsKey(to)) return null;

        Deque<Candidate> queue = new ArrayDeque<>();
        Map<String, List<Map<String, Accessory.accessorySetting>>> seen = new HashMap<>();
        int expansions = 0;

        // A train already standing on a reversing point sets off turned; anywhere else it does not.
        //
        // **WHAT `turned` MEANS NOW IS ONLY "a different way of getting here"** (Adam, 2026-09-04).
        //
        // It used to be a rule: `mustBackIn` asked it at arrival and refused a terminus to a train
        // that could not reverse unless the route had turned it. That rule came out on his ruling
        // that Return Home is manual operation. Nothing reads the flag as a rule any more - it
        // survives only in the visited key below, where it keeps two routes to one square apart.
        //
        // **Left in deliberately rather than ripped out.** Collapsing the key would prune one of
        // those two and change which route this search returns first, and a route change is not
        // worth the tidiness on release eve. `connected` lost the same state, because it only ever
        // answered yes or no and pruning cannot change that answer.
        //
        // The seeding argument is kept below because it is still the thing a reader will wonder
        // about, and because if the rule ever comes back this is where it was right:
        //
        // NOT A TERMINUS, and this was briefly changed to include one (FV2-B2, reverted by SV2-A1).
        // The argument for adding it was that `executePath` flips direction on arrival at a terminus
        // and at a reversing point alike, in one statement - which is true, and was not what this
        // flag meant. It meant "this train will BACK INTO the terminus it is ending at".
        //
        // At a reversing point the arrival flip leaves the train trailing, so it goes on backing:
        // seeding true is right.  At a TERMINUS the flip is the one that turns a backed-in train round
        // to face out again - it is spent - so a train leaving a terminus leaves FORWARDS.  Seeding
        // true there counts the same flip twice and tells the planner a train arrives backing in when
        // it arrives nose first.
        //
        // What seeding it wrong produces is a plan that strands a locomotive: sent nose first into a
        // berth it cannot reverse out of.  That is still true and still worth avoiding, even though
        // the planner no longer refuses such a berth - the flag decides how the route is DESCRIBED,
        // and a wrong description is what a later rule would be built on.
        //
        // (`connected` used to seed from either, and no longer seeds at all - it lost its reversal
        // state with the rule.  The invariant that mattered there is unchanged and now trivial: a
        // proof may be looser than the search it guards, never tighter.)
        queue.add(new Candidate(from, new LinkedList<Edge>(),
            new HashMap<String, Accessory.accessorySetting>(), from.isReversing()));

        while (!queue.isEmpty() && expansions++ < ROUTE_SEARCH_LIMIT)
        {
            Candidate current = queue.poll();

            for (Edge e : this.layout.getNeighbors(current.at))
            {
                Point next = e.getEnd();

                // Whether the train has been turned round by the time it stands here.  Part of the
                // STATE, not of the square - see Candidate.turned.
                boolean turned = current.turned || next.isReversing();

                if (!canEnter(next, loc, blocked, state)) continue;

                // Lock edges are deliberately NOT consulted here.
                //
                // They used to be, by the rule the runtime used at the time: an edge counted as
                // occupied when the point it led to held another locomotive.  The runtime stopped
                // asking that - a lock edge holds a shared throat clear, and a train standing at the
                // point one leads to is not on that throat - and this half was left behind, so the
                // planner became the stricter of the two.
                //
                // That is the worst way round for it to be wrong.  The planner refused arrangements
                // the runtime would have driven, and reported NO_PLAN_FOUND after exhausting its whole
                // search budget - the vaguest message this can give, after the longest wait.
                //
                // Nothing replaces it, rather than the runtime's own test being copied, because a
                // staging plan runs ONE TRAIN AT A TIME (see Layout's staging flag).  The runtime rule
                // is "is another route holding this track", and during a staging move no other route
                // is running, so the answer is always no.  A check that cannot fire is worse than no
                // check: it invites a future reader to make it fire.
                //
                // auditAgainstRuntime is what proves the two halves agree; it compares this search
                // against getPossiblePaths, which is where isPathClear applies the runtime rule.

                Map<String, Accessory.accessorySetting> commands = withCommandsOf(e, current.commands);

                // Two edges asking one accessory for opposite settings
                if (commands == null) continue;

                // KEYED BY THE SITUATION, not by the square.
                //
                // The same point reached with a reversal behind it and without one are two states, and
                // collapsing them means the first arrival - usually the short way, which does not turn
                // the train - closes off the only route that could have backed it in.
                List<Edge> route = new LinkedList<>(current.route);
                route.add(e);

                // ROOM IS ASKED BEFORE THE ARRIVAL IS RECORDED (WK3-B2, D3F-B1, RT3-B1).
                //
                // The refusal below is a `continue` rather than a give-up, on the stated ground that
                // another route to the same berth may be longer and a longer approach is more room.
                // That was only true if the search still had the longer route to try, and it did not:
                // this arrival used to be written into `seen` first, and `alreadyReached` is a
                // DOMINANCE test - a longer way round to the same berth carries the short way's
                // commands plus more, so the short one dominated it and it was dropped before its
                // room was ever measured.  Three reviewers found it independently within hours of the
                // rule landing, and the test below reproduces it twenty times out of twenty.
                //
                // An arrival refused for want of room is not a state that has been reached.  Recording
                // it was what closed the door the refusal depends on.
                //
                // What this does NOT fix, and it is worth knowing: a longer route can still be pruned
                // at a shared square EARLIER in the path, because path length is in neither the key
                // nor the dominance relation.  In practice a genuinely different approach sets some
                // switch the other way, so its command map is not dominated and it survives - but a
                // parallel run of track with identical ironwork would still be lost here.
                if (next.equals(to))
                {
                    Integer room = Layout.measuredRoomToReverseInto(route, loc);

                    if (room != null && loc.getTrainLength() > room) continue;
                }

                String key = next.getUniqueId() + (turned ? "/turned" : "/straight");

                if (alreadyReached(seen, key, commands)) continue;

                if (!seen.containsKey(key)) seen.put(key, new ArrayList<>());
                seen.get(key).add(commands);

                if (next.equals(to))
                {
                    // Room was asked above, before this arrival was recorded - see the note there
                    // for why the order is the whole of it (TCX-A2, WK3-B2).
                    //
                    // NO TURN IS REQUIRED HERE ANY MORE (Adam, 2026-09-04).
                    //
                    // This read `if (!mustBackIn(loc, to) || turned) return route;` - a terminus was
                    // refused to a train that cannot reverse unless a reversing point lay on the way,
                    // so that it backed in and could leave forwards.
                    //
                    // His ruling: *"Return home is manual, take the rule out and say why."*  The why
                    // is that this is the same question he settled on 2026-09-01 for `isPathClear` -
                    // *"in manual operation, non reversing trains must be able to back into a terminus
                    // if the graph makes that possible"* - and `280ff08b` kept staging on the strict
                    // side of it deliberately, reasoning that a staged run drives itself and cannot
                    // ask the operator to shunt.  That was decided without a case in front of it.
                    //
                    // The case: `EN57-203` cannot reverse, stands on `BottomMainA`, and is homed at
                    // `TunnelLongPark`, which is authored `mustReverse` and built as a terminus.  His
                    // railway carries exactly ONE reversing point and it is not on the way, so Return
                    // Home refused - permanently, and for a berth he had deliberately chosen.  Pressing
                    // the button IS the operator asking for it, which is what makes it manual.
                    //
                    // `pickPath` keeps the rule, and should: autonomy chooses destinations nobody asked
                    // for, and backing a train into a dead end unasked is a different thing from doing
                    // it because somebody pressed Return Home.
                    return route;
                }

                // A terminus may be arrived at but not driven through, so it is never expanded
                if (!next.isTerminus()) queue.add(new Candidate(next, route, commands, turned));
            }
        }

        return null;
    }

    /** A queued state and the score it was queued with, so re-scoring cannot reorder what is already in. */
    private static final class Scored
    {
        private final String key;
        private final int score;

        private Scored(String key, int score)
        {
            this.key = key;
            this.score = score;
        }
    }

    /** A partial route and the accessory settings it has committed to along the way. */
    private static final class Candidate
    {
        private final Point at;
        private final List<Edge> route;
        private final Map<String, Accessory.accessorySetting> commands;

        /**
         * Whether a reversing point has been passed on the way here.
         *
         * Part of the STATE, not a property of the square: the same point reached with a reversal
         * behind it and without one are two different situations, and only one of them may end at a
         * terminus with a locomotive that cannot reverse.
         */
        private final boolean turned;

        private Candidate(Point at, List<Edge> route, Map<String, Accessory.accessorySetting> commands,
            boolean turned)
        {
            this.at = at;
            this.route = route;
            this.commands = commands;
            this.turned = turned;
        }
    }

    /**
     * The commands so far plus this edge's, or null if they contradict.
     *
     * A path sets every switch and signal it needs before the train departs, so one accessory cannot be
     * asked for two different settings on the same route - isPathClear builds exactly this map and
     * refuses the path when an entry would be overwritten with something else.  On a layout where a
     * platform and its approach both drive the same signal, the shortest route between two stations is
     * routinely one of these: the planner offered it and the runtime then refused to drive it.
     */
    private static Map<String, Accessory.accessorySetting> withCommandsOf(Edge e,
        Map<String, Accessory.accessorySetting> soFar)
    {
        if (e.getConfigCommands().isEmpty()) return soFar;

        Map<String, Accessory.accessorySetting> merged = new HashMap<>(soFar);

        for (Map.Entry<String, Accessory.accessorySetting> command : e.getConfigCommands().entrySet())
        {
            Accessory.accessorySetting existing = merged.get(command.getKey());

            if (existing != null && !existing.equals(command.getValue())) return null;

            merged.put(command.getKey(), command.getValue());
        }

        return merged;
    }

    /**
     * Whether this point has already been reached under commands that leave at least as much freedom.
     *
     * Arriving somewhere is no longer enough to dismiss a later arrival, because the settings committed
     * on the way decide what may still be done.  A previous arrival only makes this one redundant if it
     * committed to nothing that this one has not also committed to - then anything reachable from here
     * was reachable from there.
     */
    private static boolean alreadyReached(Map<String, List<Map<String, Accessory.accessorySetting>>> seen,
        String p, Map<String, Accessory.accessorySetting> commands)
    {
        if (!seen.containsKey(p)) return false;

        for (Map<String, Accessory.accessorySetting> earlier : seen.get(p))
        {
            boolean dominates = true;

            for (Map.Entry<String, Accessory.accessorySetting> command : earlier.entrySet())
            {
                if (!command.getValue().equals(commands.get(command.getKey())))
                {
                    dominates = false;
                    break;
                }
            }

            if (dominates) return true;
        }

        return false;
    }

    /**
     * Whether a locomotive may enter a point at all - the traversal half of what isPathClear enforces,
     * evaluated against the shadow state instead of live feedback.
     *
     * The origin is never tested, which is what keeps the moving locomotive's own sensor from blocking
     * its own departure.
     */
    private boolean canEnter(Point p, Locomotive loc, Set<String> blocked,
        Map<Point, Locomotive> state)
    {
        if (!p.isActive()) return false;

        // The rule the runtime enforces: Edge.isOccupied is true when the BLOCK the edge leads into
        // holds someone else - the point itself, or another copy of the same square, since a square is
        // emitted as one Point per arrival side and they are one piece of track.
        //
        // This planner still models occupancy per Point, and the shared-sensor rule below happens to
        // catch the same pairs: every copy of a square carries that square's s88.  So the two agree
        // today, and this planner is if anything stricter than the runtime - it never plans a move the
        // runtime would refuse.  They agree by coincidence rather than by construction, which is worth
        // knowing before either rule is changed.
        Locomotive occupant = state.get(p);

        if (occupant != null && !occupant.equals(loc)) return false;

        if (p.getS88() != null && blocked.contains(p.getS88())) return false;

        // Two ACTIVE points reporting one sensor are a single detection section, so they cannot both
        // hold a train.  This is now the whole of the shared-address rule.  It used to be expressed by
        // blocking the address itself, which only happened for a sensor that was READING occupied when
        // the snapshot was taken - so on a layout whose feedback was quiet the rule did not apply at
        // all, and the planner would cheerfully park two trains on one section for the runtime to
        // discover.
        if (p.getS88() != null && this.pointsBySensor.containsKey(p.getS88()))
        {
            for (Point sibling : this.pointsBySensor.get(p.getS88()))
            {
                // Not gated on the sibling being active: a detection section is electrical, and a
                // train parked on a deactivated siding holds the sensor exactly as hard as one on a
                // live platform.  snapshot records occupants of inactive points, so the planner knows
                // it is there; skipping it let a second train be routed into the active twin, for the
                // runtime to refuse on live feedback partway through the run.
                if (sibling.equals(p)) continue;

                Locomotive there = state.get(sibling);

                // The MOVER is not exempt either (SG-A4).
                //
                // It reads as though it must be - a train cannot be blocked by its own sensor - but
                // the point it is standing on is not what is being asked about.  isPathClear never
                // looks at the point a path STARTS from; what it looks at is the end of every edge,
                // against the live feedback, and it exempts nobody.  While the train is still standing
                // on this section the feedback really is set, so a leg into another point reporting
                // that sensor was planned and then refused at execution - and the run retried it every
                // two seconds until it was abandoned.
                //
                // Hardware-conditional, which is why nothing caught it: on pulsed feedback the sensor
                // clears behind the train and the runtime's check never fires.  On latching occupancy
                // detection it fires every time.
                //
                // Departing is unaffected: `from` is never passed to this method.
                if (there != null) return false;
            }
        }

        // The two exclusion lists mean different things, and the difference is deliberate.
        //
        // On a NON-station, exclusion means the locomotive may not pass at all - that is the collision
        // constraint, and it is enforced here.  On a station it means the locomotive may not STOP
        // there, which canRest enforces; driving through is allowed, because a station on a through
        // route is exactly where an operator puts "not this one, not here".
        //
        // Enforcing the stricter reading on stations too was tried and reverted: on the author's own
        // layout it removed 45% of the reachable station pairs for two locomotives, because two of its
        // through stations carry exclusion lists.
        return p.isDestination() || !p.getExcludedLocs().contains(loc);
    }

    /**
     * Sensors reading occupied that no locomotive on the graph accounts for.
     *
     * Something is sitting on that track which the graph does not know about, and no move of ours will
     * clear it, so it stays blocked for the whole plan.
     *
     * A sensor a KNOWN train is standing on is not blocked here.  That train can move, and where it
     * leaves the section closed behind it is the mutual exclusion in canEnter - two active points
     * sharing an address cannot both be occupied.  Expressing it there rather than here is what makes
     * the rule structural instead of a function of whatever the feedback happened to read a moment ago.
     */
    private Set<String> blockedSensors(Map<Point, Locomotive> state)
    {
        Set<String> out = new HashSet<>();

        for (String sensor : this.sensorsSet)
        {
            boolean explained = false;

            for (Point p : this.pointsBySensor.get(sensor))
            {
                if (this.start.containsKey(p)) explained = true;
            }

            if (!explained) out.add(sensor);
        }

        return out;
    }

    /**
     * Whether this station could ever be this locomotive’s home.
     *
     * The same question the planner asks before it searches, offered to callers who are about to make
     * an assignment.  Delegated rather than restated: a second copy of the rest rules would drift, and
     * the whole point is that the answer here matches the one Return Home will give later.
     *
     * @param loc
     * @param at
     * @return
     */
    public static boolean canBeHome(Locomotive loc, Point at)
    {
        return whyNotAHome(loc, at) == null;
    }

    /**
     * Why this square cannot be a home, or null when it can.
     *
     * Split out of {@link #canBeHome} so the refusal can say which of the two reasons it is - a
     * greyed item with no explanation is the thing OB-050 was about.
     *
     * For a day it had no caller but canBeHome, so the stated purpose was unrealised and both doors
     * went on treating the two reasons as one: the right-click menu warned "no train can come to rest
     * here" about a square whose problem was that it is two places, then let the operator proceed into
     * a throw. Review found it. Both doors ask this now, and each acts on the answer it is about.
     *
     * @param loc the locomotive being assigned
     * @param at the square
     * @return a message key, or null when the assignment is fine
     */
    public static String whyNotAHome(Locomotive loc, Point at)
    {
        // A square that is more than one graph Point cannot be a home.
        //
        // Adam, 2026-08-25, asked whether the staging planner should stop treating a shared sensor as
        // one square: "this is an invalid state - any home with two graph points should be refused."
        //
        // He is dissolving the question rather than answering it, which is the second time that has
        // been the right move on this feature. The planner and the runtime disagreed about such a
        // square because the square is genuinely ambiguous: "is the train home?" has no single answer
        // when home is two places. Making the configuration impossible removes the disagreement
        // instead of picking a winner for it.
        //
        // A SPLIT SQUARE IS AN ORDINARY HOME (Adam, 2026-08-31).
        //
        // This refused one, and so did Layout.setHomeLocomotive and the loader, on the argument that a
        // square drawn as several graph Points makes "is the train home?" have more than one answer.
        // His ruling: "the home should just be the logical point, and the direction is wherever the
        // locomotive was facing when it started moving." One answer, because the home is the SQUARE -
        // which is what Point.isSamePlaceAs has said since MT-165.
        //
        // AND USABILITY IS ASKED OF THE SQUARE TOO, or "the home is the square" means nothing here. A
        // platform whose copies are a turning berth and a through road is a good home for a locomotive
        // that cannot reverse: it stands on the through road. Asking only the copy that happens to
        // carry the home would refuse that - and which copy carries it is a choice AutonomyBuilder
        // made, not one the operator made.
        return canRestOnSquare(loc, at) ? null : "autolayout.errorHomeCannotRestHere";
    }

    /**
     * The home assignment that excluding these locomotives from this station would contradict.
     *
     * canBeHome asked from the other side.  Assigning a home to a station that excludes the locomotive
     * is warned about; excluding a locomotive from the station that is its home reaches exactly the
     * same dead state - every future Return Home reports IMPOSSIBLE - and was silent, so the guard
     * stood on one door of two.  One keystroke over a hovered node was enough to walk through the
     * other.
     *
     * @param p the station whose exclusion list is being changed
     * @param toExclude the locomotives that would be excluded from it
     * @return the name of the home locomotive this would strand, or null if none
     */
    public static String homeBrokenByExcluding(Point p, Collection<Locomotive> toExclude)
    {
        if (p == null || p.getHomeLoc() == null || toExclude == null) return null;

        for (Locomotive l : toExclude)
        {
            // Identity now that a Point holds the locomotive rather than its name.  The answer is still
            // a NAME because this is what the warning shows the user.
            if (l != null && l.equals(p.getHomeLoc())) return p.getHomeLoc().getName();
        }

        return null;
    }

    /**
     * canRest, plus the one rest rule that depends on where everything ELSE is.
     *
     * FR-001 holds a station back while another named square is occupied, and `isPathClear` enforces
     * it on a path's DESTINATION - which is every move this planner makes. The planner could not see
     * it: `canRest` reads only the station itself, and `getBlockedBy` is about a different square.
     *
     * So the plan reported READY, execution refused the leg, the run retried until it gave up, and it
     * stopped everything with the fleet half-staged (OB-073). It fails safe - no train moves wrongly -
     * but partial execution is the thing staging exists to avoid, and the planner is where it should
     * have been refused.
     *
     * Asked of the PLANNED state rather than the live railway, because that is what the rest of this
     * class reasons about: by the time this move happens the trains are where the plan put them, not
     * where they are now.
     *
     * The locomotive being routed is exempt, as it is at runtime - "the condition should not apply to
     * trains leaving, only departing" - so a train standing on the watched square may still be sent to
     * the station that square holds back.
     *
     * WHICH squares are consulted and WHO is exempt are no longer decided here: that is the rule, and
     * the rule lives in Point.heldBackBy (DR-B2).  All this contributes is where to look for occupancy
     * - the planned state rather than the live railway - which is the one thing about FR-001 that is
     * genuinely this class's business.
     *
     * @param loc the locomotive being planned
     * @param at where it would come to rest
     * @param state who is standing where, in the plan
     * @return whether it may rest there
     */
    private boolean canRest(Locomotive loc, Point at, Map<Point, Locomotive> state)
    {
        if (!canRest(loc, at)) return false;

        return Point.heldBackBy(at, loc, plannedOccupancy(state)) == null;
    }

    /**
     * The staging planner's answer to "who is standing on the same piece of track as this square" -
     * the second of Point.Occupancy's two named variants, and the one that reads the PLAN.
     *
     * Asked of the planned state rather than the live railway, because that is what the rest of this
     * class reasons about: by the time a move happens the trains are where the plan put them, not
     * where they are now.  That is why this variant has to exist at all.
     *
     * It consults three things, and the third is the deliberate divergence from the runtime:
     *
     *  - the square itself;
     *  - the other copies of it, by BLOCK.  This is exactly what the runtime's getBlockLocomotive does,
     *    and the planner did not do it: a train on a copy the restriction does not name was invisible
     *    to the planner while the runtime could see it plainly.  On a square with a sensor the sibling
     *    rule below happened to cover the same pairs; on a square with none, nothing did, and the
     *    planner was the looser half - which is a plan the railway refuses, OB-073's own symptom.
     *  - the other points reporting the same SENSOR, which the runtime does NOT consult.  Two active
     *    points on one feedback are one detection section, so the planner is right that they cannot
     *    both hold a train - but AutonomyBuilder says outright that a sensor is not a square: "a
     *    station, its approach guard and a reversing point can be three Points on one feedback - so the
     *    sensor cannot say which Points are one square."  On such a layout this refuses arrivals the
     *    runtime would allow.  It fails SAFE - a refused plan, never a wrong movement - but it is the
     *    "planner is the stricter half" shape, whose symptom is NO_PLAN_FOUND.  Left in force
     *    deliberately, because dropping it changes which stations staging offers on a real railway, and
     *    that is Adam's decision rather than a refactor's.  It is pinned in both directions by
     *    testTheStagingPlannerIsTheStricterHalfOnASharedSensor, so it cannot move by accident.
     *
     * @param state who is standing where, in the plan
     * @return the occupancy source for Point.heldBackBy
     */
    private Point.Occupancy plannedOccupancy(final Map<Point, Locomotive> state)
    {
        return (track, exempt) ->
        {
            if (heldBySomebodyElse(track, exempt, state)) return true;

            for (Point sibling : sameTrackAs(track))
            {
                if (heldBySomebodyElse(sibling, exempt, state)) return true;
            }

            return false;
        };
    }

    /**
     * The other points the planner treats as one piece of track with this one.
     *
     * @param track the square being asked about
     * @return its block copies and its sensor siblings, never including the square itself
     */
    private List<Point> sameTrackAs(Point track)
    {
        List<Point> out = blockCopiesOf(track);

        if (track.getS88() != null)
        {
            for (Point sibling : this.pointsBySensor.getOrDefault(track.getS88(),
                java.util.Collections.<Point>emptyList()))
            {
                // A copy that is both a block sibling and a sensor sibling - which is every copy on a
                // builder-emitted layout - is asked once.  Twice would be harmless here and misleading
                // to anyone counting, since the two terms are meant to be visibly different sets.
                if (!sibling.equals(track) && !out.contains(sibling)) out.add(sibling);
            }
        }

        return out;
    }

    /**
     * Whether a station is held back by the piece of track another station stands on (OB-085).
     *
     * The structural half of FR-001: not "is somebody standing there", which is a question about
     * state, but "would somebody standing there close this station", which is a question about the
     * graph and has the same answer whatever the trains are doing.
     *
     * The track rather than the square, because a watched square and the station in question can be
     * different Points of one block or one detection section - the same widening
     * {@link #plannedOccupancy} applies when it reads occupancy.
     *
     * @param station the station whose restrictions are being read
     * @param track the square being asked about
     * @return true when a train standing on that track would hold this station back
     */
    private boolean watchesTrack(Point station, Point track)
    {
        if (station == null || track == null) return false;

        for (Point watched : station.getBlockedBy())
        {
            if (watched == null) continue;

            if (watched.equals(track) || blockCopiesOf(watched).contains(track)) return true;
        }

        return false;
    }

    /**
     * The other Points that are the same square as this one, by BLOCK and nothing else.
     *
     * Deliberately narrower than {@link #sameTrackAs}, and the difference is the whole of what makes
     * the impossibility proof legitimate.
     *
     * `sameTrackAs` also unions the points reporting the same SENSOR, which the runtime does not
     * consult. That widening is the planner being conservative on purpose, and `plannedOccupancy`
     * says exactly what it is worth: "It fails SAFE - a refused plan, never a wrong movement - but it
     * is the 'planner is the stricter half' shape, whose symptom is NO_PLAN_FOUND."
     *
     * A refused plan and a PROOF are not the same claim. IMPOSSIBLE names locomotives and asserts that
     * no arrangement exists, so it may only be built out of the relation the railway actually
     * enforces - which is the block, the same thing `getBlockLocomotive` asks.
     *
     * The first version of the OB-085 scan used `sameTrackAs`, and a review built the counterexample:
     * two ordinary platforms, one one-way hold, and an approach guard sharing a feedback address with
     * the other platform - which AutonomyBuilder says outright is normal, "a station, its approach
     * guard and a reversing point can be three Points on one feedback". The railway stages it in two
     * moves. Return Home said impossible and named both locomotives. That is the third time something
     * put into this scan has been wrong, and the first two were caught the same way.
     *
     * @param track the square being asked about
     * @return its block copies, never including the square itself
     */
    private List<Point> blockCopiesOf(Point track)
    {
        List<Point> out = new ArrayList<>();

        if (track.getBlock() == null) return out;

        for (Point copy : this.pointsByBlock.getOrDefault(track.getBlock(),
            java.util.Collections.<Point>emptyList()))
        {
            if (!copy.equals(track)) out.add(copy);
        }

        return out;
    }

    /**
     * Whether two squares are one piece of track as far as the planner is concerned.
     *
     * @param a one square
     * @param b the other
     * @return true when a train on one is a train on the other
     */
    private boolean onOneTrack(Point a, Point b)
    {
        // The WIDE relation here, unlike watchesTrack above, and the asymmetry is deliberate: this
        // decides what the cycle scan SKIPS, and skipping more can only make it claim less. Two homes
        // sharing a sensor are the pairwise goal scan's business anyway.
        return a != null && b != null && sameTrackAs(a).contains(b);
    }

    /**
     * Whether a point holds a locomotive that is not the one being planned.
     */
    private static boolean heldBySomebodyElse(Point p, Locomotive loc, Map<Point, Locomotive> state)
    {
        Locomotive there = state.get(p);

        return there != null && !there.equals(loc);
    }


    /**
     * Whether this locomotive could get home at all, over any copy of the home square (2026-08-31).
     *
     * Deliberately one method rather than two. Resting and reaching are separate questions, and asking
     * them separately over the copies would accept a home where one copy can be rested at and a
     * DIFFERENT one can be reached - which is no home at all. They are asked of the same copy.
     *
     * Blind to occupancy, like `connected`, and for the same reason: a route blocked merely by another
     * train is not impossible, and moving that train is what the planner is for.
     *
     * @param loc the locomotive
     * @param from where it is standing
     * @param home its home, or any copy of the home square
     * @return true when some copy of that square would take it and can be reached
     */
    private boolean canGetHome(Locomotive loc, Point from, Point home)
    {
        if (home == null) return false;

        if (home.getBlock() == null || home.getLayout() == null)
        {
            return canRest(loc, home) && connected(from, home);
        }

        boolean sawACopy = false;

        for (Point copy : home.getLayout().getPoints())
        {
            if (!home.getBlock().equals(copy.getBlock())) continue;

            sawACopy = true;

            if (canRest(loc, copy) && connected(from, copy)) return true;
        }

        // A block naming no copies cannot be answered by looking at them.
        return sawACopy ? false : (canRest(loc, home) && connected(from, home));
    }

    /**
     * Whether a locomotive could stand anywhere on this square (2026-08-31).
     *
     * `canRest` answers about one graph Point, which is the right question when the planner is routing
     * a train to a particular arrival side. It is the wrong question about a HOME: a home is a square,
     * so what matters is whether the train can stand on any copy of it.
     *
     * An ordinary square - anything with no block, which is most of them - falls straight through to
     * the plain answer.
     *
     * @param loc the locomotive
     * @param at any copy of the square
     * @return true when some copy of that square would take it
     */
    private static boolean canRestOnSquare(Locomotive loc, Point at)
    {
        if (at == null) return false;

        if (at.getBlock() == null || at.getLayout() == null) return canRest(loc, at);

        boolean sawACopy = false;

        for (Point copy : at.getLayout().getPoints())
        {
            if (!at.getBlock().equals(copy.getBlock())) continue;

            sawACopy = true;

            if (canRest(loc, copy)) return true;
        }

        // A block naming no copies cannot be answered by looking at them.
        return sawACopy ? false : canRest(loc, at);
    }

    /**
     * Whether a locomotive may come to rest on a station - its length, and the station's exclusions.
     *
     * NOT reversibility, whatever this line said until 2026-09-01: it went on listing "the
     * reversibility a terminus demands" after the clause enforcing it had been deleted from the body
     * directly below, in the same commit, on Adam's ruling.  A summary that survives the rule it
     * summarises is worse than none - it is what the next reader trusts instead of reading on - and
     * the very next comment in this method already explains why the rule went.
     */
    private static boolean canRest(Locomotive loc, Point at)
    {
        // NOT THE TERMINUS RULE (Adam, 2026-08-31).
        //
        // This ended `&& (!at.isTerminus() || loc.isReversible())`, which refused a parking berth as a
        // home to any train that cannot reverse - and most parking berths are terminuses, so on his
        // railway it refused most of the places a train is actually parked.  EN57-947 could not be
        // homed at TunnelLeftPark for this reason and no other.
        //
        // Whether the train can GET there is a question about a ROUTE: a path that passes a reversing
        // point turns it, so it backs in and leaves forwards.  Layout.isPathClear asks that, where
        // there is a path to ask about.  Asking again here, with none, could only guess - and a guard
        // that nothing can satisfy is worse than no guard.

        return at.isDestination()
            && at.isActive()
            && !at.getExcludedLocs().contains(loc)
            && at.validateTrainLength(loc);
    }

    // THE TWO-ARGUMENT `connected` IS GONE (SVN-C14, FV2-C6).
    //
    // It defaulted `mustReverse` to false, and its last caller went with `09777d4c`.  A convenience
    // overload that silently supplies one of the two things this question turns on is worth removing
    // rather than keeping: the whole of `SV2-A1` was about which value that argument should take.


    /**
     * Whether a route exists from one square to another, ignoring who is standing where.
     *
     * **This used to be able to insist that the way there turned the train round** (Adam, 2026-08-31:
     * *"For homing: I would also like non-reversing trains to have to back in"*), seeded by
     * `mustBackIn`. That rule came out on 2026-09-04 - *"Return home is manual, take the rule out and
     * say why"* - and with its only seed gone the parameter had one possible value and the reversal
     * state keyed a visited set on a distinction that could no longer change an answer.
     *
     * Removed rather than left passing `false`, because a parameter every caller answers the same way
     * is how a reader comes to believe there is a choice being made here. `DAY-C1` is an open finding
     * about the same shape kept in the diagram code.
     *
     * `firstClearRoute` still tracks `turned` and still keys on it. That is deliberate: collapsing ITS
     * key changes which route the search returns first, and this one only had to answer yes or no.
     *
     * @param from where the train is
     * @param to where it is going
     * @return true when such a route exists, ignoring who is standing where
     */
    private boolean connected(Point from, Point to)
    {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true;

        Set<String> seen = new HashSet<>();
        Deque<Point> queue = new ArrayDeque<>();

        // NO REVERSAL STATE ANY MORE (Adam, 2026-09-04).
        //
        // This search used to carry a `turned` flag beside every square, seeded from
        // `from.isReversing() || from.isTerminus()` and keyed into the visited set, so that a square
        // reached with a reversal behind it and the same square reached without one were two states.
        // The whole of that existed to answer one question - had the train been turned by the time it
        // arrived - and nothing asks it now.
        //
        // The findings that shaped it are worth keeping in view, because they are about this search's
        // relationship to the executor rather than about the reversal rule: `D24-B1` was the two
        // searches disagreeing about where a train starts, and `FV2-B2` was this one forgetting that
        // `executePath` turns a train on arrival at a terminus as well as at a reversing point. Both
        // were cases of the PROOF being stricter than the runtime, which is the error that matters
        // here: `plan()` consults this to call a locomotive UNREACHABLE, and unreachable is the only
        // thing this class claims to have proved. A proof may be looser than the search it guards,
        // never tighter.
        seen.add(from.getUniqueId());
        queue.add(from);

        while (!queue.isEmpty())
        {
            Point at = queue.poll();

            for (Edge e : this.layout.getNeighbors(at))
            {
                Point next = e.getEnd();

                // Arriving is checked before the visited set, as it always was - the destination may
                // be a terminus, which is a place this search will not travel THROUGH.
                if (next.equals(to)) return true;

                if (seen.contains(next.getUniqueId())) continue;

                seen.add(next.getUniqueId());

                // A TERMINUS IS TRAVELLED THROUGH HERE, BY STOPPING AT IT (Adam, 2026-09-01).
                //
                // `firstClearRoute` will not expand a terminus, and is right not to: it builds ONE
                // path, and a train arrives at a terminus without driving on past it.  Copying that
                // rule into this search was wrong, because this search answers a different question.
                // It is what `plan()` consults to declare a locomotive UNREACHABLE, and `plan()` ends a
                // move at any station and starts the next from there - so stopping at a terminus and
                // setting off again is an ordinary two-move plan, not an impossibility.
                //
                // Found on his own railway the day he made the ramp a place trains may stop, so that
                // they could reach the parking berths: the answer went from "no arrangement found" to
                // IMPOSSIBLE, naming a locomotive, for the two-move route he had just built.
                //
                // Only a terminus a move could actually END at, which is a destination that is in
                // service.  One that is neither is not a stop, and travelling through it would be this
                // search inventing a move the planner cannot make - the opposite error, and the one
                // that costs more: a proof may be looser than the search it guards, never tighter.
                //

                // WHERE THIS IS STILL TIGHTER THAN THE SEARCH, for whoever widens it next (TV2-C6).
                // An out-of-service or non-destination terminus is not expanded at all, and the search
                // WILL end a leg at one and start the next from it - so a graph whose only route home
                // passes through such a square is proved IMPOSSIBLE although a two-move plan exists.
                // Not reachable on a builder-derived graph, because the main line runs over the other
                // copies of a split square; structurally real, and the direction that is safe to widen
                // is this one, never the other.  A proof may be looser than the search it guards.
                if (!next.isTerminus() || (next.isDestination() && next.isActive()))
                {
                    queue.add(next);
                }
            }
        }

        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // State helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Whether two points are one detection section - both active, and reporting the same sensor.
     *
     * The same rule canEnter enforces between a train and the section it wants to enter, asked here
     * between two goals instead.
     *
     * **A review argued this should ask the BLOCK, like the proof below it, and I have left it asking
     * the sensor. Recorded because the argument is good and the decision is Adam's.**
     *
     * The case for changing it: the railway's own mutual exclusion is `getBlockLocomotive`, and
     * AutonomyBuilder says outright that a station, its approach guard and a reversing point can be
     * three Points on one feedback - so "one sensor" is not "one piece of track". Homing two
     * locomotives on two such squares answers IMPOSSIBLE naming both, which is the same false-proof
     * shape as AU-A1, twelve lines away, and it was found by the pass that validated the AU-A1 fix.
     *
     * **The example this used to give was wrong, and it is worth keeping the correction here.** It
     * said BottomMainC and BottomMainCTerm share feedback 4 on Adam's own graph. They did on the
     * hand-written autonomy.json; the 3.0.0 diagram derives its own graph, in which BottomMainCTerm
     * does not exist - AutonomySession's caption migration names it as one of four labels left behind
     * by the configuration this feature replaced. Measured on the derived graph: every sensor carrying
     * two active stations carries one SQUARE, emitted once per arrival side, and a home on a square
     * that is several Points is refused by whyNotAHome anyway. So the case is real and general, and
     * his layout is not an instance of it.
     *
     * The case against changing it, which is why it has not been: `canEnter` enforces the sensor rule
     * deliberately and structurally, so the planner genuinely will not produce that arrangement. The
     * claim "no arrangement exists" is therefore true of every arrangement this planner can reach -
     * self-consistent, if narrower than the railway. Removing it does not make the plan appear; it
     * turns an instant IMPOSSIBLE naming both locomotives into a search that burns its whole budget
     * and answers "maybe". Adam's layout has eleven shared sensors, so that is a real cost on every
     * Return Home, and `testTwoActivePointsSharingASensorAreNeverBothOccupied` argues exactly that.
     *
     * **It is the same decision as the sensor-versus-block divergence in `plannedOccupancy`,** and
     * both should move together or not at all - which is precisely why neither moved at the end of an
     * autonomous round. See MT-187.
     *
     * @param a one home
     * @param b the other
     * @return true when the planner treats a train at one as a train at the other
     */
    private boolean sharesSection(Point a, Point b)
    {
        return a != null && b != null && !a.equals(b)
            && a.isActive() && b.isActive()
            && a.getS88() != null && a.getS88().equals(b.getS88());
    }

    /**
     * Whether a locomotive standing here is standing at its home (MT-165).
     *
     * A square emitted as several Points is ONE piece of track - that is what a block IS - so a train
     * on any copy of its home platform is home.  The comparison itself is `Point.isSamePlaceAs`, which
     * is where it belongs: this method had it privately, `Layout.claimHome` did not, and the door that
     * did not have it let two locomotives be homed on one platform.  Asking `home.equals(where)` instead was right for as
     * long as a home could never be on a split square, which was until claimHome started giving
     * positional homes there: Adam's railway has ten station squares with a block, and no train
     * standing on one of them had ever been given a home at all.
     *
     * Without this the fix would trade one fault for a worse one: a train returning on the far copy of
     * its own platform would be judged not home, and the planner would try to move it to the exact
     * copy - which on a split square means arriving from one particular direction, and may be
     * impossible.
     *
     * @param home the home, or null
     * @param where the locomotive is now, or null
     * @return true when those are the same place
     */
    private static boolean atHome(Point home, Point where)
    {
        return home != null && home.isSamePlaceAs(where);
    }

    private static Point locationOf(Map<Point, Locomotive> state, Locomotive l)
    {
        for (Map.Entry<Point, Locomotive> e : state.entrySet())
        {
            if (e.getValue().equals(l)) return e.getKey();
        }

        return null;
    }

    private static void apply(Map<Point, Locomotive> state, Locomotive l, Point to)
    {
        Point at = locationOf(state, l);

        if (at != null) state.remove(at);

        state.put(to, l);
    }

    /** Homed locomotives that are not on their home.  Free agents are never counted - they may end
     *  anywhere, which is what makes them useful for breaking a deadlock. */
    private int misplaced(Map<Point, Locomotive> state)
    {
        int count = 0;

        for (Map.Entry<Point, Locomotive> e : state.entrySet())
        {
            Point home = this.homes.get(e.getValue());

            if (home != null && !atHome(home, e.getKey())) count++;
        }

        return count;
    }

    /** Identifies a configuration for the search's visited set. */
    private static String key(Map<Point, Locomotive> state)
    {
        List<String> parts = new ArrayList<>();

        for (Map.Entry<Point, Locomotive> e : state.entrySet())
        {
            parts.add(e.getKey().getName() + "=" + e.getValue().getName());
        }

        Collections.sort(parts);

        return String.join("|", parts);
    }

    private static List<Move> empty()
    {
        return new ArrayList<>();
    }

    private static List<Locomotive> noLocs()
    {
        return new ArrayList<>();
    }
}
