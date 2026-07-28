package org.traincontrol.automation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    private static final int SEARCH_LIMIT = 200000;

    /** Alternative routes considered per station pair.  The first clear one is taken, so this only has
     *  to be deep enough to get past the paths another train happens to be sitting on. */
    private static final int PATHS_PER_PAIR = 8;

    private final Layout layout;

    /** Station -> occupant, at the moment of the snapshot.  The whole of the planner's world. */
    private final Map<Point, Locomotive> start;

    private final Map<Locomotive, Point> homes;

    /** Every station a locomotive could rest on, snapshot once. */
    private final List<Point> stations;

    /** Candidate routes per ordered station pair, enumerated once - the graph does not change while
     *  planning, so this is the expensive part done exactly once.
     *
     *  Keyed on the pair of points rather than on their names joined by a separator: point names come
     *  from the config and routinely contain spaces, so any string join has pairs that collide. */
    private final Map<List<Point>, List<List<Edge>>> routes = new HashMap<>();

    private HomeStaging(Layout layout, Map<Point, Locomotive> start, Map<Locomotive, Point> homes,
        List<Point> stations)
    {
        this.layout = layout;
        this.start = start;
        this.homes = homes;
        this.stations = stations;
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

        for (Point p : layout.getPoints())
        {
            if (p.isDestination() && p.isActive()) stations.add(p);

            if (p.getCurrentLocomotive() != null) occupancy.put(p, p.getCurrentLocomotive());
        }

        return new HomeStaging(layout, occupancy, new LinkedHashMap<>(layout.getHomeStations()), stations);
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
        NO_PLAN_FOUND
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

            if (routesBetween(locationOf(this.start, l), home).isEmpty()) unreachable.add(l);
        }

        if (!unreachable.isEmpty()) return new Plan(Outcome.IMPOSSIBLE, empty(), unreachable);

        List<Move> moves = search();

        if (moves == null) return new Plan(Outcome.NO_PLAN_FOUND, empty(), noLocs());

        return new Plan(Outcome.READY, moves, noLocs());
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
        PriorityQueue<String> open = new PriorityQueue<>((a, b) -> score.get(a) - score.get(b));

        open.add(startKey);

        Set<String> closed = new HashSet<>();
        int examined = 0;

        while (!open.isEmpty() && examined < SEARCH_LIMIT)
        {
            String currentKey = open.poll();

            if (closed.contains(currentKey)) continue;

            closed.add(currentKey);
            examined++;

            Map<Point, Locomotive> current = states.get(currentKey);

            if (misplaced(current) == 0) return rebuild(currentKey, cameFrom, arrivedBy);

            // One set per state, not one per candidate move: nothing moves while we expand this state
            Set<String> blocked = blockedSensors(current);

            for (Locomotive l : new ArrayList<>(current.values()))
            {
                Point at = locationOf(current, l);

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
                        open.add(nextKey);
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
     * The first route from one station to another that is clear in this hypothetical state, or null.
     */
    private List<Edge> firstClearRoute(Map<Point, Locomotive> state, Set<String> blocked, Locomotive loc,
        Point from, Point to)
    {
        if (from == null || to == null || !canRest(loc, to) || state.containsKey(to)) return null;

        for (List<Edge> route : routesBetween(from, to))
        {
            if (isClear(route, loc, blocked)) return route;
        }

        return null;
    }

    /**
     * Sensors an occupied station reports.  Keyed by address, because two points may share one and it
     * is the sensor that decides whether a path is blocked.
     */
    private static Set<String> blockedSensors(Map<Point, Locomotive> state)
    {
        Set<String> out = new HashSet<>();

        for (Point p : state.keySet())
        {
            if (p.getS88() != null) out.add(p.getS88());
        }

        return out;
    }

    /**
     * The static half of isPathClear, evaluated against the shadow state instead of live feedback.
     * Every point after the origin must be unoccupied - which is why the origin's own sensor, held by
     * the locomotive that is about to leave it, never has to be excluded by hand.
     */
    private static boolean isClear(List<Edge> route, Locomotive loc, Set<String> blocked)
    {
        for (Edge e : route)
        {
            Point end = e.getEnd();

            if (end.getS88() != null && blocked.contains(end.getS88())) return false;
            if (!end.isActive()) return false;

            Point begin = e.getStart();

            // Excluded intermediate points cannot be traversed
            if (!begin.isDestination() && begin.getExcludedLocs().contains(loc)) return false;

            // A terminus may only be the end of a path, never passed through
            if (begin.isTerminus() && !begin.equals(route.get(0).getStart())) return false;
        }

        return true;
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
     * Candidate routes between two stations, enumerated once and cached.
     */
    private List<List<Edge>> routesBetween(Point from, Point to)
    {
        if (from == null || to == null) return Collections.emptyList();

        List<Point> cacheKey = Arrays.asList(from, to);

        if (this.routes.containsKey(cacheKey)) return this.routes.get(cacheKey);

        List<List<Edge>> found = new ArrayList<>();
        List<List<Edge>> seen = new LinkedList<>();

        try
        {
            for (int i = 0; i < PATHS_PER_PAIR; i++)
            {
                List<Edge> path = this.layout.bfs(from, to, seen);

                if (path == null) break;

                found.add(path);
                seen.add(path);
            }
        }
        catch (Exception e)
        {
            // An unroutable pair is a normal answer here, not a failure: it just means this locomotive
            // cannot get there, which is what the caller is asking.
        }

        this.routes.put(cacheKey, found);

        return found;
    }

    // ---------------------------------------------------------------------------------------------
    // State helpers
    // ---------------------------------------------------------------------------------------------

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
