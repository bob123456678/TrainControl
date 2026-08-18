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

    /** Stations with zero incoming edges - hand-staged launch pads; see snapshot. */
    private final Set<String> launchPads;

    private HomeStaging(Layout layout, Map<Point, Locomotive> start, Map<Locomotive, Point> homes,
        List<Point> stations, Set<String> sensorsSet, Map<String, List<Point>> pointsBySensor,
        Set<String> launchPads)
    {
        this.launchPads = launchPads;
        this.layout = layout;
        this.start = start;
        this.homes = homes;
        this.stations = stations;
        this.sensorsSet = sensorsSet;
        this.pointsBySensor = pointsBySensor;

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

        for (Point p : layout.getPoints())
        {
            if (p.isDestination() && p.isActive()) stations.add(p);

            if (p.getCurrentLocomotive() != null) occupancy.put(p, p.getCurrentLocomotive());

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

            boolean assigned = entry.getKey().getName().equals(entry.getValue().getHomeLoc());
            boolean standingOnIt = entry.getKey().equals(occupancy.get(entry.getValue()));

            if (!assigned && !standingOnIt && launchPads.contains(entry.getValue().getName()))
            {
                it.remove();
            }
        }

        return new HomeStaging(layout, occupancy, homes, stations,
            sensorsSet, pointsBySensor, launchPads);
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

        // A locomotive with no route to its home at all cannot be helped by moving anything else, and
        // it is the only impossibility this class can prove.  Locomotives already standing on their
        // home are skipped: asking for a route from a point back to itself is a different question,
        // and on a layout without a loop the answer is "none" - which would report a graph that is
        // entirely in order as impossible.
        List<Locomotive> unreachable = new ArrayList<>();

        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || home.equals(locationOf(this.start, l))) continue;

            // A home the locomotive could never be parked at - inactive, excluding it, too short for
            // it, or a terminus it cannot reverse out of - is impossible by construction: no move can
            // ever end there, so the goal is unreachable however the search is run.  Without this the
            // search burns its whole budget and then reports "no arrangement found - it may still be
            // possible", which is wrong twice over and sends the operator shunting for nothing.
            // A locomotive that cannot leave where it stands is as unreachable as one with no route:
            // firstClearRoute refuses an inactive origin, so the search can only exhaust and answer
            // "maybe".  One flag test turns that into a proof, the same upgrade the pairwise goal scan
            // below gives conflicting homes.
            if (!locationOf(this.start, l).isActive()
                || !canRest(l, home)
                || !connected(locationOf(this.start, l), home)) unreachable.add(l);
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

                if (sharesSection(a.getValue(), b.getValue()))
                {
                    if (!unreachable.contains(a.getKey())) unreachable.add(a.getKey());
                    if (!unreachable.contains(b.getKey())) unreachable.add(b.getKey());
                }
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

                if (home == null || home.equals(locationOf(state, l))) continue;

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

                if (this.launchPads.contains(at.getName()) && (ownHome == null || ownHome.equals(at)))
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
        // sensor blocking its own departure - but not from this one.  isPathClear applies its
        // inactive-point rule to every edge start including the first, and staging executes with
        // autonomy running, so a locomotive standing on a deactivated point would be planned home and
        // then refused at its first edge.
        if (!from.isActive()) return null;
        if (!canRest(loc, to) || state.containsKey(to)) return null;

        Deque<Candidate> queue = new ArrayDeque<>();
        Map<Point, List<Map<String, Accessory.accessorySetting>>> seen = new HashMap<>();
        int expansions = 0;

        queue.add(new Candidate(from, new LinkedList<Edge>(),
            new HashMap<String, Accessory.accessorySetting>()));

        while (!queue.isEmpty() && expansions++ < ROUTE_SEARCH_LIMIT)
        {
            Candidate current = queue.poll();

            for (Edge e : this.layout.getNeighbors(current.at))
            {
                Point next = e.getEnd();

                if (!canEnter(next, loc, blocked, state)) continue;

                // An edge can be refused because of track the train never drives on
                if (!lockEdgesFree(e, loc, state)) continue;

                Map<String, Accessory.accessorySetting> commands = withCommandsOf(e, current.commands);

                // Two edges asking one accessory for opposite settings
                if (commands == null) continue;

                if (alreadyReached(seen, next, commands)) continue;

                if (!seen.containsKey(next)) seen.put(next, new ArrayList<>());
                seen.get(next).add(commands);

                List<Edge> route = new LinkedList<>(current.route);
                route.add(e);

                if (next.equals(to)) return route;

                // A terminus may be arrived at but not driven through, so it is never expanded
                if (!next.isTerminus()) queue.add(new Candidate(next, route, commands));
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

        private Candidate(Point at, List<Edge> route, Map<String, Accessory.accessorySetting> commands)
        {
            this.at = at;
            this.route = route;
            this.commands = commands;
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
    private static boolean alreadyReached(Map<Point, List<Map<String, Accessory.accessorySetting>>> seen,
        Point p, Map<String, Accessory.accessorySetting> commands)
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
     * Whether every edge this one locks is free.
     *
     * Taking an edge also claims the edges listed against it, and an edge counts as occupied when the
     * point it leads to holds another locomotive.  So a route can be refused because of track it never
     * touches - which is not a detail on a layout like the author's, where 54 of 92 edges carry lock
     * edges between them.
     *
     * Only the endpoint is consulted: the runtime's own "locked" flag is set while a path is being
     * driven, and the planner reasons about a layout at rest where nothing holds a lock.
     */
    private static boolean lockEdgesFree(Edge e, Locomotive loc, Map<Point, Locomotive> state)
    {
        for (Edge locked : e.getLockEdges())
        {
            Locomotive occupant = state.get(locked.getEnd());

            if (occupant != null && !occupant.equals(loc)) return false;
        }

        return true;
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

                if (there != null && !there.equals(loc)) return false;
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
        return canRest(loc, at);
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
            if (l != null && p.getHomeLoc().equals(l.getName())) return p.getHomeLoc();
        }

        return null;
    }

    /**
     * Whether a locomotive may come to rest on a station - length, exclusions, and the reversibility a
     * terminus demands.
     */
    private static boolean canRest(Locomotive loc, Point at)
    {
        return at.isDestination()
            && at.isActive()
            && !at.getExcludedLocs().contains(loc)
            && at.validateTrainLength(loc)
            && (!at.isTerminus() || loc.isReversible());
    }

    /**
     * Whether any route exists between two points at all, ignoring who is standing where.
     *
     * Deliberately blind to occupancy: it answers "could this locomotive ever get home", which is the
     * only impossibility this class can prove.  A route blocked merely by another train is not
     * impossible - moving that train is exactly what the planner is for.
     */
    private boolean connected(Point from, Point to)
    {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true;

        Set<Point> seen = new HashSet<>();
        Deque<Point> queue = new ArrayDeque<>();

        seen.add(from);
        queue.add(from);

        while (!queue.isEmpty())
        {
            Point at = queue.poll();

            for (Edge e : this.layout.getNeighbors(at))
            {
                Point next = e.getEnd();

                if (seen.contains(next)) continue;
                if (next.equals(to)) return true;

                seen.add(next);

                if (!next.isTerminus()) queue.add(next);
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
     */
    private boolean sharesSection(Point a, Point b)
    {
        return a != null && b != null && !a.equals(b)
            && a.isActive() && b.isActive()
            && a.getS88() != null && a.getS88().equals(b.getS88());
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

            if (home != null && !home.equals(e.getKey())) count++;
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
