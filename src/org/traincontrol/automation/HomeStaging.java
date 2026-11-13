package org.traincontrol.automation;

import org.traincontrol.util.I18n;
import java.util.ArrayList;
import org.traincontrol.base.Accessory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * Home is where a locomotive was when it first appeared on the graph (Layout.claimHome).  A locomotive with no home is
 * a *free agent*: it may be moved out of the way but never has to end anywhere in particular, and it is the spare
 * capacity that lets a blocked arrangement unwind.
 *
 * <b>This is a rearrangement problem, not a routing one.</b>  A station holds one locomotive, so a train cannot go home
 * while another sits there, and that one may need to move through the station the first is leaving.  It is pebble
 * motion on a graph: feasibility is polynomial, the shortest solution is NP-hard, and nobody counts the moves here.
 *
 * <b>Nothing here touches the live layout.</b>  snapshot() copies the occupancy and the planner works on that copy; it
 * does NOT call Layout.isPathClear (live s88 feedback, isAutoRunning() and maxActiveTrains cannot be evaluated for a
 * hypothetical future).  At rest a locomotive occupies exactly its own station's sensor, so a point is passable iff its
 * s88 is not one an occupied station reports.  Occupancy is keyed by <b>sensor address, not by point</b>: two points
 * may share an s88.
 *
 * Plans are sequential, one train at a time, so edge locks, activeLocomotives and maxActiveTrains stay out of the
 * model.  The runtime may overlap the moves it is given, provided it respects their order.
 *
 * @author Adam
 */
public final class HomeStaging
{
    /** Configurations examined before the search gives up.  Reached only on large, tightly packed
     *  layouts; a plain greedy pass solves the ordinary case without searching at all. */
    private static final int SEARCH_LIMIT = 50000;

    /**
     * How heavily the second half of the search weighs moves still needed against moves made (OB-230): at 1 it finds
     * the shortest plan but runs out of budget on a crowded railway; weighted, the plan may be longer by at most this
     * factor.  5 solves `core.testReturnHomeFindsAPlanOnAFullRailway`.
     */
    private static final int ANY_PLAN_WEIGHT = 5;

    /**
     * Route work one plan may spend (`spent`), shared between the passes (OB-230).  Work, not time, so a plan is the
     * same on any machine (OB-413); a unit is 0.08 to 0.09 us on the owner's machine, so this is about 15 s there.  No
     * solution says NO_PLAN_FOUND within it.
     */
    private static final long SEARCH_BUDGET = 175000000L;

    /**
     * What an edge a route tries costs, in places of the own-tail walk (OB-413): measured, the edge and its checks take
     * about eight.
     */
    private static final int EDGE_COST = 8;

    /** The budget this planner uses; a field so a test can shrink it. */
    private long budget = SEARCH_BUDGET;

    /** Route work spent by the search in progress, counted in `firstClearRoute`: routes, edges tried, places walked. */
    private long spent;

    /** Whether a pass stopped at its share of the budget or at SEARCH_LIMIT with arrangements still open. */
    private boolean ranOut;

    /** Expansions allowed per route search.  A point can be revisited under different accessory settings. */
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
     * The track trains were lying across at the snapshot, and who (OB-184), as `Layout.isPathClear` consults it; only
     * for a train not yet moved.
     */
    private final Map<Locomotive, Map<Edge, Locomotive>> coveredAtStart;

    /** The same, by place rather than by edge - what the shared-metal refusal is narrowed with. */
    private final Map<Locomotive, Map<String, Locomotive>> placesCoveredAtStart;

    /**
     * The route each train the search has moved last took, for the arrangement being expanded (OB-228, MT-335);
     * `coveredAtStart` is the railway before the plan.  Set before every expansion: greedy keeps it as it goes, A*
     * rebuilds it from the chain of moves.
     */
    private Map<Locomotive, List<Edge>> movedAlong = new java.util.HashMap<>();

    /** A train's body at a square, by the square, the train and the road the plan moved it along (OB-294). */
    private final Map<List<Object>, Map<String, Integer>> bodies = new HashMap<>();

    /** What the greedy pass left in `movedAlong`, for A* to start from. */
    private Map<Locomotive, List<Edge>> greedyMovedAlong = new java.util.HashMap<>();

    /** The covering each moved train would leave, by train and route, so a route is walked once per search. */
    private final Map<List<Edge>, Map<Edge, Locomotive>> coveredByAMove = new java.util.IdentityHashMap<>();

    /** The same, in places. */
    private final Map<List<Edge>, Map<String, Locomotive>> placesByAMove = new java.util.IdentityHashMap<>();


    /** Stations with zero incoming edges - hand-staged launch pads; see snapshot. */
    private final Set<String> launchPads;

    private HomeStaging(Layout layout, Map<Point, Locomotive> start, Map<Locomotive, Point> homes,
        List<Point> stations, Set<String> sensorsSet, Map<String, List<Point>> pointsBySensor,
        Set<String> launchPads)
    {
        this.launchPads = launchPads;
        this.layout = layout;
        this.start = start;

        // Taken once with the starting state, as the live layout would answer for a railway that has moved on; at both
        // grains (edges a tail touches, metal it lies on), one train at a time since the railway's map holds one train
        // per place.
        this.coveredAtStart = new LinkedHashMap<>();
        this.placesCoveredAtStart = new LinkedHashMap<>();

        if (layout != null)
        {
            for (Map.Entry<Locomotive, Object[]> tail : layout.tailsOfEachStandingTrain().entrySet())
            {
                @SuppressWarnings("unchecked")
                Map<Edge, Locomotive> edges = (Map<Edge, Locomotive>) tail.getValue()[0];
                @SuppressWarnings("unchecked")
                Map<String, Locomotive> places = (Map<String, Locomotive>) tail.getValue()[1];

                this.coveredAtStart.put(tail.getKey(), edges);
                this.placesCoveredAtStart.put(tail.getKey(), places);
            }
        }

        this.homes = homes;
        this.stations = stations;
        this.sensorsSet = sensorsSet;
        this.pointsBySensor = pointsBySensor;

    }

    /**
     * Copies everything the planner needs out of a layout.  Nothing afterwards reads live state.
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
                if (!pointsBySensor.containsKey(p.getS88())) pointsBySensor.put(p.getS88(), new ArrayList<>());

                pointsBySensor.get(p.getS88()).add(p);

                // READ, never inferred: a stationary locomotive need not hold its sensor (simulation pulses feedback).
                if (layout.isFeedbackOccupied(p.getS88())) sensorsSet.add(p.getS88());
            }
        }

        // Launch pads: stations with zero incoming edges, where layouts stage trains by hand; a positional home on one
        // is not a home once left, and the search never moves a locomotive off one.
        Set<String> launchPads = new HashSet<>();

        for (Point p : layout.getPoints())
        {
            if (layout.getIncomingEdges(p).isEmpty()) launchPads.add(p.getName());
        }

        // A POSITIONAL home on a launch pad stops being a home once the locomotive has LEFT (nothing can re-enter, and
        // a plan is all-or-nothing, so Return Home would refuse to move ANYTHING); still standing there, the claim is
        // satisfied (ALREADY_HOME, not NO_HOMES).  An ASSIGNED home keeps the strict contract: IMPOSSIBLE naming it.
        Map<Locomotive, Point> homes = new LinkedHashMap<>(layout.getHomeStations());

        for (Iterator<Map.Entry<Locomotive, Point>> it = homes.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry<Locomotive, Point> entry = it.next();

            // Identity, not a name: a name comparison would answer false for ever and treat every home as positional.
            boolean assigned = entry.getKey().equals(entry.getValue().getHomeLoc());
            // The SQUARE, not the Point (MT-165): a train on the other copy of its own home would read as not on it.
            boolean standingOnIt = atHome(entry.getValue(), locationOf(occupancy, entry.getKey()));

            if (!assigned && !standingOnIt && launchPads.contains(entry.getValue().getName())) it.remove();
        }

        // A PAUSED TRAIN STAYS WHERE IT STANDS (FR-117): its home is where it stands, so nothing is planned for it, the
        // post-run check counts it home, and the search never moves it for another (`paused`); one away from a home of
        // its own is logged (`getPausedAwayFromHome`).
        Set<Locomotive> paused = new HashSet<>();
        List<Locomotive> pausedAway = new ArrayList<>();

        for (Locomotive l : new java.util.LinkedHashSet<>(occupancy.values()))
        {
            if (!l.isAutonomyPaused()) continue;

            paused.add(l);

            Point at = locationOf(occupancy, l);
            Point home = homes.get(l);

            if (home == null) continue;

            if (!atHome(home, at)) pausedAway.add(l);

            homes.put(l, at);
        }

        HomeStaging staging = new HomeStaging(layout, occupancy, homes, stations,
            sensorsSet, pointsBySensor, launchPads);

        staging.paused = paused;
        staging.pausedAway = pausedAway;

        return staging;
    }

    /** The trains paused when the snapshot was taken, which no plan moves (FR-117). */
    private Set<Locomotive> paused = Collections.emptySet();

    /** Those of them standing away from a home of their own, which the plan leaves there (FR-117). */
    private List<Locomotive> pausedAway = Collections.emptyList();

    /**
     * The paused trains standing away from their homes, which the plan leaves where they stand (FR-117), for the log.
     */
    public List<Locomotive> getPausedAwayFromHome()
    {
        return Collections.unmodifiableList(pausedAway);
    }

    // The answer

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
        /**
         * Every train that can be sent home is home, but some are paused (OB-355): not ALREADY_HOME; see getBlocked.
         */
        PAUSED_AWAY,
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
         * A locomotive is held on more than one point, so where it stands cannot be said (see getBlocked): a failed
         * unlock leaves reservations the model cannot tell from a train.
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
            // The square, not the builder's copy of it - this is the "planned" line the log shows.
            return this.loc.getName() + " -> " + Layout.placeNameOf(getEnd());
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

        /**
         * Why each locomotive could not be brought home, or empty when nothing was found wrong (FR-078); see
         * `getReasons`.
         */
        private final Map<Locomotive, List<String>> reasons;

        Plan(Outcome outcome, List<Move> moves, List<Locomotive> blocked)
        {
            this(outcome, moves, blocked, Collections.<Locomotive, List<String>>emptyMap());
        }

        /** Whether the search used up its budget before trying every arrangement it could reach (OB-413). */
        private final boolean ranOut;

        Plan(Outcome outcome, List<Move> moves, List<Locomotive> blocked,
            Map<Locomotive, List<String>> reasons)
        {
            this(outcome, moves, blocked, reasons, false);
        }

        Plan(Outcome outcome, List<Move> moves, List<Locomotive> blocked,
            Map<Locomotive, List<String>> reasons, boolean ranOut)
        {
            this.outcome = outcome;
            this.moves = moves;
            this.blocked = blocked;
            this.reasons = reasons;
            this.ranOut = ranOut;
        }

        /**
         * Whether the search stopped at its limit with arrangements untried (OB-413), so a shorter plan may exist.
         * @return true when the budget, not the railway, ended the search
         */
        public boolean ranOutOfSearch()
        {
            return this.ranOut;
        }

        /**
         * Why each locomotive the plan could not bring home was refused (FR-078): a proof for IMPOSSIBLE, only what can
         * be SEEN for NO_PLAN_FOUND.
         * @return the sentences, keyed by locomotive, in the order the planner found them
         */
        public Map<Locomotive, List<String>> getReasons()
        {
            return Collections.unmodifiableMap(this.reasons);
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

        /** Locomotives that cannot reach their home, when the outcome is IMPOSSIBLE; the paused ones away, for PAUSED_AWAY */
        public List<Locomotive> getBlocked()
        {
            return Collections.unmodifiableList(this.blocked);
        }
    }

    // Planning

    /**
     * Works out how to get every homed locomotive back to its home.
     */
    public Plan plan()
    {
        Outcome trivial = triage();

        // The paused trains away from their homes are the plan's "blocked" for this outcome, so the sentence can name them
        if (trivial == Outcome.PAUSED_AWAY) return new Plan(trivial, empty(), new ArrayList<>(this.pausedAway));

        if (trivial != null) return new Plan(trivial, empty(), noLocs());

        // A locomotive in two places at once (a failed unlock leaves reservations that look like trains) cannot be
        // reasoned about, and guessing an origin could drive a train from where it is not: NO_PLAN_FOUND with its name
        // and the remedy, place the train on its square.
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

        // A locomotive with no route home is the only impossibility this class can prove; ones already home are skipped
        // (a route from a point to itself would report a loop-free graph as impossible).
        List<Locomotive> unreachable = new ArrayList<>();

        // One sentence per fact, per locomotive, for the log (FR-078).
        Map<Locomotive, List<String>> reasons = new java.util.LinkedHashMap<>();

        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || atHome(home, locationOf(this.start, l))) continue;

            // A home the locomotive could never rest at, or an origin it cannot leave, is impossible by construction.
            // The STATELESS canRest: this pre-scan reads the STARTING occupancy (OB-073).
            //
            // THE SQUARE: `canRest` and `connected` are asked of the same copy of the home.
            //
            // The inactive start is refused here deliberately: a square switched off is one its author said to leave
            // alone.
            if (!locationOf(this.start, l).isActive()
                || !locationOf(this.start, l).isDestination()
                || !canGetHome(l, locationOf(this.start, l), home))
            {
                unreachable.add(l);

                // And why (FR-078), asked of the same three conditions so the sentence describes the rule that refused.
                reasons.put(l, whyNotHome(l, locationOf(this.start, l), home));
            }
        }

        // Nor one that cannot leave where it stands (OB-325): its home is connected so it passes the scan above, yet it
        // has no first move, and the search would run the whole fleet for 15 s and name nobody.  Asked with every other
        // train taken off (`hasAFirstMove`), which makes it a proof.
        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || unreachable.contains(l)) continue;

            Point from = locationOf(this.start, l);

            if (atHome(home, from) || this.hasAFirstMove(l, from, true)) continue;

            // Not where a sensor is all that holds it: a sensor reading occupied with no known train (unknown vehicle,
            // dirty contact) would pass for a proof but is live state; such a train is left to the search.
            if (this.hasAFirstMove(l, from, false)) continue;

            unreachable.add(l);

            reason(reasons, l, I18n.f("autolayout.whyHomeCannotLeave", Layout.placeNameOf(from)));
        }

        // Goals that conflict, which no arrangement can satisfy: two homes on one detection section (the easiest wrong
        // click on a layout sharing addresses).  Provable pairwise, so the search spends no budget to say "maybe".
        for (Map.Entry<Locomotive, Point> a : this.homes.entrySet())
        {
            if (!this.start.containsValue(a.getKey())) continue;

            for (Map.Entry<Locomotive, Point> b : this.homes.entrySet())
            {
                if (a.getKey().equals(b.getKey()) || !this.start.containsValue(b.getKey())) continue;

                if (!sharesSection(a.getValue(), b.getValue())) continue;

                // Both already parked: nothing arrives, so nothing is checked; without the exemption the WHOLE run is
                // refused (IMPOSSIBLE) though a third locomotive only needed driving on.  One of the two away restores
                // the proof (testTwoActivePointsSharingASensorAreNeverBothOccupied).
                if (atHome(a.getValue(), locationOf(this.start, a.getKey()))
                    && atHome(b.getValue(), locationOf(this.start, b.getKey()))) continue;

                if (!unreachable.contains(a.getKey())) unreachable.add(a.getKey());
                if (!unreachable.contains(b.getKey())) unreachable.add(b.getKey());

                reason(reasons, a.getKey(), I18n.f("autolayout.whyHomeSharesASection",
                    Layout.placeNameOf(a.getValue()), b.getKey().getName(), Layout.placeNameOf(b.getValue())));
            }
        }

        // No cycle scan over the occupancy restrictions (OB-085): IMPOSSIBLE asserts no arrangement exists, so an
        // exhausted search answers NO_PLAN_FOUND (`testHomeStaging.testTwoHomesThatHoldEachOtherBackAreADeadlock`).

        if (!unreachable.isEmpty()) return new Plan(Outcome.IMPOSSIBLE, empty(), unreachable, reasons);

        List<Move> moves = search();

        if (moves == null) return new Plan(Outcome.NO_PLAN_FOUND, empty(), noLocs(), whatCanBeSeen(), this.ranOut);

        return new Plan(Outcome.READY, moves, noLocs(), Collections.<Locomotive, List<String>>emptyMap(), this.ranOut);
    }

    /**
     * Compares the planner's idea of where each locomotive may go against the runtime's, for the state as it stands
     * now.
     *
     * The planner answers for hypothetical futures, so it re-implements the rules instead of calling isPathClear (live
     * feedback); this checks that re-implementation against the original for the ONE state where both can be asked,
     * the present.
     *
     * Logged rather than enforced: a divergence is a defect in this class, not a reason to refuse a staging run, and
     * the runtime re-validates every move anyway.
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

            // A locomotive on a deactivated point is the third correct divergence: getPossiblePaths is asked at rest,
            // where the runtime's inactive rule is not in force, but staging runs with autonomy on.
            if (!e.getKey().isActive()) continue;

            Set<Point> runtimeSays = new HashSet<>();

            // Asked as a leg of Return Home, which alone may run through a stop-only station (FR-111): this planner
            // obeys no such rule, and both sides must be answering about the same trips or the audit says nothing.
            for (List<Edge> path : this.layout.getPossiblePathsGoingHome(loc, true))
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
                // Inactive points are a correct divergence: at rest the runtime skips its inactive-point rule and
                // offers parking tracks it would refuse under a timetable; the planner reasons about the running case.
                if (!p.isActive()) continue;

                // And exclusions: getPossiblePaths filters on occupancy and station-ness but not exclusion (pickPath
                // does), so it offers stations canRest correctly refuses.
                if (p.excludes(loc)) continue;

                // AND NO FR-001 EXEMPTION: both sides apply it in every tier, at rest too, so they agree; an exemption
                // would be a hole in the one instrument that exists to find real divergence.

                if (!plannerSays.contains(p))
                {
                    disagreements++;
                    this.layout.logStagingAudit(loc.getName(), Layout.placeNameOf(p), true);
                }
            }

            for (Point p : plannerSays)
            {
                // A destination only a lap reaches belongs on THIS side: `Layout.bfs` refuses a route doubling back
                // through another copy of its start or end square, so the menu offers none; the planner does, and the
                // RUNTIME agrees (`isPathClear` exempts an intermediate copy of the train's own square).
                if (onlyALapReachesIt(e.getKey(), p)) continue;

                if (!runtimeSays.contains(p))
                {
                    disagreements++;
                    this.layout.logStagingAudit(loc.getName(), Layout.placeNameOf(p), false);
                }
            }
        }

        return disagreements;
    }

    /**
     * The part of the answer that needs no planning: is there anything to do at all?
     *
     * Separated so a caller can ask cheaply before a question that would be rude when nothing is needed; plan()
     * delegates to it, so the two cannot disagree.
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

        if (misplaced(this.start) == 0)
        {
            // A paused train counts as home for the plan (FR-117), true of nothing else: said apart so "already home"
            // is not shown over trains standing away (OB-355)
            return this.pausedAway.isEmpty() ? Outcome.ALREADY_HOME : Outcome.PAUSED_AWAY;
        }

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

        // ONE BUDGET, for every search this runs, shared out below.
        this.spent = 0;
        this.ranOut = false;

        List<Move> plan = greedy(state);

        if (misplaced(state) == 0) return plan;

        Map<Locomotive, List<Edge>> greedyRoutes = new java.util.HashMap<>(this.movedAlong);

        // A SHARE OF THE BUDGET EACH (OB-230): shortest plan from the railway as it stands, from where greedy stopped,
        // then any plan; thirds if greedy moved something, else halves (the first two would search the same state).
        boolean retry = !plan.isEmpty();

        long share = this.budget / (retry ? 3 : 2);

        // From the railway as it stands first (OB-413): when it finishes it is the shortest plan, at a fraction of the
        // cost of searching from greedy's arrangement.
        this.greedyMovedAlong = new java.util.HashMap<>();

        List<Move> fromTheStart = astar(new LinkedHashMap<>(this.start), share, 1);

        if (fromTheStart != null) return fromTheStart;

        // Then from where the greedy pass left things, inheriting the routes that pass gave (OB-228).
        if (retry)
        {
            this.greedyMovedAlong = greedyRoutes;

            List<Move> rest = astar(state, 2 * share, 1);

            this.greedyMovedAlong = new java.util.HashMap<>();

            if (rest != null)
            {
                plan.addAll(rest);

                return plan;
            }
        }

        // And then for ANY plan, not the shortest (OB-230): on a crowded railway the shortest-plan search runs out of
        // room while the weighted one finds a longer plan.
        return astar(new LinkedHashMap<>(this.start), this.budget, ANY_PLAN_WEIGHT);
    }

    /**
     * Sends home, again and again, every train whose home is free and reachable; leaves `state` and `movedAlong` where
     * it stopped.
     */
    private List<Move> greedy(Map<Point, Locomotive> state)
    {
        List<Move> plan = new ArrayList<>();

        // Nothing has moved yet (OB-228).
        this.movedAlong = new java.util.HashMap<>();

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

                    // AND ITS TAIL NOW LIES ALONG THIS ROUTE, for every move after it (OB-228).
                    this.movedAlong.put(l, path);

                    progress = true;
                }
            }
        }

        return plan;
    }

    /**
     * A* over configurations, with `movesStillNeeded` as the heuristic, weighted by `weight`, until `spent` reaches
     * `until`.
     *
     * Admissible: the estimate counts only moves that cannot be avoided (OB-230).
     */
    private List<Move> astar(Map<Point, Locomotive> from, long until, int weight)
    {
        Map<String, Map<Point, Locomotive>> states = new HashMap<>();
        Map<String, Integer> cost = new HashMap<>();
        Map<String, Integer> score = new HashMap<>();
        Map<String, Move> arrivedBy = new HashMap<>();
        Map<String, String> cameFrom = new HashMap<>();

        // The routes each arrangement's moved trains came by, kept with it: the same squares with different tails are
        // different arrangements (parked the short way a train lies across the run another needs).
        Map<String, Map<Locomotive, List<Edge>>> routesOf = new HashMap<>();
        Map<Locomotive, List<Edge>> startRoutes = new java.util.HashMap<>(this.greedyMovedAlong);

        String startKey = tailKey(from, startRoutes);

        routesOf.put(startKey, startRoutes);
        states.put(startKey, from);
        cost.put(startKey, 0);
        score.put(startKey, weight * movesStillNeeded(from));

        // Ordered on an f-score carried by each entry: re-scoring a state in the map would change a queued entry's
        // priority and break the heap.
        //
        // Ties break towards the goal (OB-230).
        PriorityQueue<Scored> open = new PriorityQueue<>((a, b) -> a.score != b.score
            ? Integer.compare(a.score, b.score) : Integer.compare(a.stillNeeded, b.stillNeeded));

        open.add(new Scored(startKey, score.get(startKey), movesStillNeeded(from)));

        Set<String> closed = new HashSet<>();
        int examined = 0;

        while (!open.isEmpty())
        {
            if (examined >= SEARCH_LIMIT || this.spent >= until)
            {
                this.ranOut = true;

                break;
            }

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

            // THE ROUTES THAT BROUGHT THE TRAINS TO THIS ARRANGEMENT (OB-228), as it was reached.
            this.movedAlong = routesOf.get(currentKey);

            // One set per state, not one per candidate move: nothing moves while we expand this state
            Set<String> blocked = blockedSensors(current);

            for (Locomotive l : new ArrayList<>(current.values()))
            {
                Point at = locationOf(current, l);

                // NOR A PAUSED TRAIN (FR-117): it stays where it stands, in somebody's way or not
                if (this.paused.contains(l)) continue;

                // A locomotive on a launch pad stays unless its assigned home lies elsewhere: a move off a pad (no
                // incoming edges) can never be undone and would destroy the hand-staging.  Keyed on pad and home, not
                // homelessness (a satisfied positional pad-home stays in homes); one already dispatched is a free
                // agent.
                Point ownHome = this.homes.get(l);

                // atHome: a train on the far copy of its own home square is at home; pinning the exact copy can ask A*
                // for a move no arrival side allows.
                if (this.launchPads.contains(at.getName()) && (ownHome == null || atHome(ownHome, at))) continue;

                // A train that cannot reverse is turned on the way only to go home (Adam: "This should only be allowed
                // if the train is going to reverse into its berth on the next turn.", OB-205); carried by the train,
                // and only of one THIS PLAN has moved (`movedAlong` has an entry).
                boolean turnedByThePlan = !l.isReversible() && !atHome(ownHome, at)
                    && this.movedAlong.containsKey(l)
                    && (turnsATrainArrivingAt(at)
                        || turnedOnTheWay(this.movedAlong.get(l)));

                for (Point to : this.stations)
                {
                    if (to.equals(at) || current.containsKey(to)) continue;

                    if (turnedByThePlan && !atHome(ownHome, to)) continue;

                    if (!l.isReversible() && ownHome == null && turnsATrainArrivingAt(to)) continue;

                    List<Edge> path = firstClearRoute(current, blocked, l, at, to);

                    if (path == null) continue;

                    // Nor turned mid-move and sent on: a route may turn the train at a reversing point (a non-reversing
                    // train backing into a berth, MT-245), allowed only where the move ends somewhere the turn was FOR
                    // (home, a berth, or a square it rests facing out of).
                    boolean turnedOnTheWay = false;

                    for (int e = 0; e + 1 < path.size(); e++)
                    {
                        if (path.get(e).getEnd().isReversing()) turnedOnTheWay = true;
                    }

                    if (!l.isReversible() && turnedOnTheWay && !atHome(ownHome, to)
                        && !turnsATrainArrivingAt(to) && to.isAutoDestination())
                    {
                        continue;
                    }

                    Map<Point, Locomotive> next = new LinkedHashMap<>(current);
                    apply(next, l, to);

                    Map<Locomotive, List<Edge>> nextRoutes = new java.util.HashMap<>(this.movedAlong);

                    nextRoutes.put(l, path);

                    String nextKey = tailKey(next, nextRoutes);
                    int nextCost = cost.get(currentKey) + 1;

                    if (!cost.containsKey(nextKey) || nextCost < cost.get(nextKey))
                    {
                        states.put(nextKey, next);
                        cost.put(nextKey, nextCost);
                        int stillNeeded = movesStillNeeded(next);

                        score.put(nextKey, nextCost + weight * stillNeeded);
                        cameFrom.put(nextKey, currentKey);
                        arrivedBy.put(nextKey, new Move(l, path));
                        routesOf.put(nextKey, nextRoutes);
                        open.add(new Scored(nextKey, score.get(nextKey), stillNeeded));
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

    // The shadow rules

    /**
     * A route from one station to another that is clear in this hypothetical state, or null.  Breadth-first, so it is
     * the shortest clear one.
     *
     * Searches the points that can be entered given who stands where, not a filtered few routes: on a loop layout the
     * shortest few can all be blocked while a longer clear one exists.
     */
    private List<Edge> firstClearRoute(Map<Point, Locomotive> state, Set<String> blocked, Locomotive loc,
        Point from, Point to)
    {
        this.spent++;

        if (from == null || to == null || from.equals(to)) return null;

        // Not round a loop back onto the square it stands on (Adam: "These are the same point so we should never do a
        // round trip just to change direction").  Another copy of its own square is the same track, which menu and
        // autonomy never offer (`Point.getBlockLocomotive`); a train on any copy of its home is already home
        // (`atHome`).
        if (from.getBlock() != null && from.getBlock().equals(to.getBlock())) return null;

        // The origin is exempt from every other test (so the mover's sensor does not block its departure) but not these
        // two: isPathClear applies its inactive-point rule to the first edge, and staging runs with autonomy on.
        if (!from.isActive()) return null;

        // And the next `if`, "Starting point is not a station": staging is autonomous (executeTimetable sets
        // `running`); missing it STARTS the run, the first leg is refused, and the retry loop abandons it.
        if (!from.isDestination()) return null;
        // FR-001 at the arrival is asked below of every square it arrives at, `to` included, on the PLANNED state; the
        // runtime enforces it in every tier (OB-073), and `auditAgainstRuntime` proves the two agree.
        if (!canRest(loc, to) || state.containsKey(to)) return null;

        // Where the plan has put everybody, for the FR-001 question at every square on the way (OB-295).
        final Point.Occupancy planned = plannedOccupancy(state);

        // Its own body as it stands here (OB-294): the road this plan moved it along, or what the railway records.
        // Walked after the cheap refusals and once per train and road, as it is asked per destination in a budgeted
        // search.
        List<Edge> cameAlong = this.movedAlong.get(loc);

        if (cameAlong != null && (cameAlong.isEmpty() || !from.isSamePlaceAs(cameAlong.get(cameAlong.size() - 1).getEnd())))
        {
            cameAlong = null;
        }

        final Map<String, Integer> ownBody = this.layout == null ? null
            : this.bodies.computeIfAbsent(java.util.Arrays.asList(from, loc, cameAlong),
                key -> this.layout.bodyOfATrainAt(from, loc, (List<Edge>) key.get(2)));

        Deque<Candidate> queue = new ArrayDeque<>();
        Map<String, List<Map<String, Accessory.accessorySetting>>> seen = new HashMap<>();
        int expansions = 0;

        // A train already standing on a reversing point sets off turned; anywhere else it does not.
        //
        // `turned` is only "a different way of getting here" (nothing reads it as a rule; Return Home is manual): it
        // keeps two routes to one square apart in the visited key, and collapsing it would change which route is
        // returned first.  Not seeded at a TERMINUS, where `executePath`'s flip turns a backed-in train to face out, so
        // seeding true would count it twice.
        queue.add(new Candidate(from, new LinkedList<Edge>(),
            new HashMap<String, Accessory.accessorySetting>(), from.isReversing(), 0));

        // The own-tail walk below returns at once for a train with no length, and costs nothing then.
        boolean walksItsBody = loc.getTrainLength() != null && loc.getTrainLength() > 0;

        while (!queue.isEmpty() && expansions++ < ROUTE_SEARCH_LIMIT)
        {
            Candidate current = queue.poll();

            // By name, not as the railway shuffles them: `Layout.getNeighbors` shuffles deliberately and this returns
            // the FIRST clear route, so a plan would be READY on one press and NO_PLAN_FOUND on the next.  Ordered
            // here, as getNeighbors is shared by every tier.
            List<Edge> outward = new java.util.ArrayList<>(this.layout.getNeighbors(current.at));

            java.util.Collections.sort(outward, (one, two) -> one.getName().compareTo(two.getName()));

            for (Edge e : outward)
            {
                Point next = e.getEnd();

                this.spent += EDGE_COST;

                // The copy rule is NOT applied here: `Layout.bfs` refuses routes passing another copy of the start or
                // end square (menu and autonomy only), but applying it here lost Return Home plans
                // (`core.testTrainsComeHomeToTheirPlatforms`); the runtime agrees (`isPathClear` exempts an
                // intermediate copy) and `auditAgainstRuntime` matches.
                //
                // Whether the train has been turned around by the time it stands here: part of the STATE
                // (Candidate.turned).
                boolean turned = current.turned || next.isReversing();

                if (!canEnter(next, loc, blocked, state)) continue;

                // And not through a square held back by an occupied one (OB-295): `isPathClear` asks FR-001 of every
                // square a route arrives at; asked of the planned state, `to` included.
                if (Point.heldBackBy(next, loc, planned) != null) continue;

                // And the track a standing train is lying across (OB-184).  `canEnter` asks about the POINT this edge
                // leads to; this asks about the EDGE, since a long train protrudes onto track no point reports.
                if (!passesTheTailsOfTrainsThatHaveNotMoved(e, loc, state)) continue;
                if (!passesTheTailsOfTrainsItHasMoved(e, loc, state)) continue;

                // Lock edges are deliberately NOT consulted: the runtime does not count an edge occupied when its point
                // holds another locomotive, so asking would make the planner stricter; a staging plan runs ONE TRAIN AT
                // A TIME (Layout's staging flag), and `auditAgainstRuntime` proves agreement.

                Map<String, Accessory.accessorySetting> commands = withCommandsOf(e, current.commands);

                // Two edges asking one accessory for opposite settings
                if (commands == null) continue;

                // Keyed by the situation, not the square: the same point reached with and without a reversal behind it
                // are two states, or the short way closes off the only route that could back the train in.
                List<Edge> route = new LinkedList<>(current.route);
                route.add(e);

                int places = current.places + e.getPlaceIds().size();

                // The walk of its own body along it below visits every place of the route: most of a search's time.
                if (walksItsBody) this.spent += places;

                // And not round a loop into its own tail (OB-294), as `Layout.isPathClear` asks; prefix-closed like the
                // room rule below, so pruning loses nothing.
                if (Layout.whyItWouldMeetItsOwnTail(route, loc, ownBody) != null) continue;

                // Room is asked before the arrival is recorded: `alreadyReached` is a DOMINANCE test, so a short
                // arrival recorded first would drop a longer one unmeasured.  Asked where the runtime asks
                // (`Layout.whyTooLongForThisRoute`, MT-333) and nowhere else: fewer squares refuse the first move, more
                // drop plans that would run.
                if (next.equals(to))
                {
                    // At the destination, the berth rule as well as the room (`Layout.whyABerthCannotHoldIt`, as
                    // `isPathClear`, OB-073); the room as `measuredRoomAtTheEndOf`, not all of
                    // `whyTooLongForThisRoute`, which would be stricter than the runtime (FR-087).
                    Integer room = Layout.measuredRoomAtTheEndOf(route, loc);

                    if (room != null && loc.getTrainLength() > room
                        && !Layout.theApproachItselfHoldsIt(route, loc))
                    {
                        continue;
                    }

                    // The station's stated capacity is not asked here: `canRest` ends with
                    // `at.validateTrainLength(loc)`, so a second spelling of the rule would drift
                    // (`core.testHomeStaging.testThePlannerRefusesAHomeTheOperatorSaidIsTooShort`).

                    if (Layout.whyABerthCannotHoldIt(route, loc) != null) continue;
                }
                else if (next.isReversing())
                {
                    // Only a square the train turns at, as the runtime judges it (MT-333); a square it only passes is
                    // not asked about.
                    Integer room = Layout.measuredRoomAtTheEndOf(route, loc);

                    if (room != null && loc.getTrainLength() > room) continue;
                }

                String key = next.getUniqueId() + (turned ? "/turned" : "/straight");

                if (alreadyReached(seen, key, commands)) continue;

                if (!seen.containsKey(key)) seen.put(key, new ArrayList<>());
                seen.get(key).add(commands);

                if (next.equals(to))
                {
                    // Room was asked above, before this arrival was recorded.
                    //
                    // No turn is required (Adam: "Return home is manual, take the rule out and say why."), as in
                    // `isPathClear` for manual operation; `pickPath` keeps the rule, as autonomy picks destinations
                    // nobody asked for.
                    return route;
                }

                // A terminus may be arrived at but not driven through, so it is never expanded
                if (!next.isTerminus()) queue.add(new Candidate(next, route, commands, turned, places));
            }
        }

        return null;
    }

    /** A queued state and the score it was queued with, so re-scoring cannot reorder what is already in. */
    private static final class Scored
    {
        private final String key;
        private final int score;

        /** The estimate alone, which breaks a tie between two equal scores (OB-230) */
        private final int stillNeeded;

        private Scored(String key, int score, int stillNeeded)
        {
            this.key = key;
            this.score = score;
            this.stillNeeded = stillNeeded;
        }
    }

    /** A partial route and the accessory settings it has committed to along the way. */
    private static final class Candidate
    {
        private final Point at;
        private final List<Edge> route;
        private final Map<String, Accessory.accessorySetting> commands;

        /** Whether a reversing point has been passed on the way here: part of the STATE, not of the square. */
        private final boolean turned;

        /** The places along `route`, which is what walking it costs (OB-413). */
        private final int places;

        private Candidate(Point at, List<Edge> route, Map<String, Accessory.accessorySetting> commands,
            boolean turned, int places)
        {
            this.at = at;
            this.route = route;
            this.commands = commands;
            this.turned = turned;
            this.places = places;
        }
    }

    /**
     * The commands so far plus this edge's, or null if they contradict.
     *
     * One accessory cannot be asked for two different settings on the same route: isPathClear builds exactly this map
     * and refuses the path when an entry would be overwritten.
     */
    static Map<String, Accessory.accessorySetting> withCommandsOf(Edge e,
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
     * Whether this point was already reached under commands that leave at least as much freedom: an earlier arrival
     * makes this one redundant only if it committed to nothing this one has not also committed to.
     */
    static boolean alreadyReached(Map<String, List<Map<String, Accessory.accessorySetting>>> seen,
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

    /** Whether this edge is free of the tails the plan's own moves leave (OB-228); a train never blocks itself. */
    private boolean passesTheTailsOfTrainsItHasMoved(Edge edge, Locomotive mover, Map<Point, Locomotive> state)
    {
        if (this.layout == null || this.movedAlong.isEmpty()) return true;

        for (Map.Entry<Locomotive, List<Edge>> moved : this.movedAlong.entrySet())
        {
            Locomotive train = moved.getKey();
            List<Edge> road = moved.getValue();

            if (train.equals(mover) || road == null || road.isEmpty()) continue;

            Point end = road.get(road.size() - 1).getEnd();

            // Still standing where this route left it.
            if (end == null || !train.equals(state.get(end))) continue;

            Map<Edge, Locomotive> covered = this.coveredByAMove.computeIfAbsent(road,
                r -> this.layout.edgesATailWouldCover(end, train, r));

            Map<String, Locomotive> places = this.placesByAMove.computeIfAbsent(road,
                r -> this.layout.placesATailWouldCover(end, train, r));

            // The same question as for a train that has not moved, asked of the tail this move left
            if (liesAcross(edge, train, covered, places)) return false;
        }

        return true;
    }

    /**
     * Whether this edge is free of the tails of trains still where they started (OB-184); every train lying across it
     * is asked, a train never blocks ITSELF.
     */
    private boolean passesTheTailsOfTrainsThatHaveNotMoved(Edge edge, Locomotive mover,
        Map<Point, Locomotive> state)
    {
        Set<Locomotive> lyingAcross = new LinkedHashSet<>();

        // EACH TRAIN'S OWN RECORD, one at a time - so a place two tails claim answers with both.
        for (Map.Entry<Locomotive, Map<Edge, Locomotive>> each : this.coveredAtStart.entrySet())
        {
            Locomotive train = each.getKey();

            // THE MOVER'S OWN TAIL IS NOT THE ANSWER, only not an obstacle (OB-285); the others on the same metal are
            // asked, as `Layout.isPathClear` does.
            if (train.equals(mover)) continue;

            if (liesAcross(edge, train, each.getValue(), this.placesCoveredAtStart.get(train))) lyingAcross.add(train);
        }

        for (Locomotive train : lyingAcross)
        {
            if (stillWhereItStarted(train, state)) return false;
        }

        return true;
    }

    /**
     * Whether this train's tail lies over metal this edge runs on (OB-184), for a tail at the start or one a planned
     * move left (OB-228).
     */
    private static boolean liesAcross(Edge edge, Locomotive train, Map<Edge, Locomotive> covered,
        Map<String, Locomotive> places)
    {
        if (places == null) places = java.util.Collections.emptyMap();

        if (covered.containsKey(edge)) return true;

        // And the track it shares metal with (OB-184): a train across a SWITCH fouls every road through it.  Read as
        // `Layout.isPathClear` does, through `getLockEdges` restricted to SYMMETRIC partners (one-directional
        // restrictions share the collection).
        for (Edge sharing : edge.getLockEdges())
        {
            if (!covered.containsKey(sharing)) continue;

            if (!sharing.getLockEdges().contains(edge)) continue;

            // Only over the part of the edge the train lies on (OB-207), as `Layout.tailLiesOn` with the same fallback:
            // both sides must be described in places or nothing narrows (a pre-3.0.0 configuration keeps the whole
            // edge).
            if (!edge.getPlaceIds().isEmpty() && !sharing.getPlaceIds().isEmpty()
                && !Layout.tailLiesOn(edge, train, places))
            {
                continue;
            }

            return true;
        }

        // AND EVERY OTHER COPY OF THE SAME METAL, as `Layout.isPathClear` asks it: the rail into a turning copy
        // runs over the places of the rail into its plain twin, and is no lock partner of it.
        return Layout.tailLiesOn(edge, train, places);
    }

    /**
     * Whether a train is still on its starting station, so its tail is where it was (OB-184); one covering track with
     * no starting station counts as moved.
     */
    private boolean stillWhereItStarted(Locomotive train, Map<Point, Locomotive> state)
    {
        for (Map.Entry<Point, Locomotive> was : this.start.entrySet())
        {
            if (!train.equals(was.getValue())) continue;

            return train.equals(state.get(was.getKey()));
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

        // The runtime's rule: Edge.isOccupied is true when the BLOCK the edge leads into holds someone else.  This
        // planner models occupancy per Point and the shared-sensor rule below catches the same pairs (every copy
        // carries its s88), by coincidence, not construction.
        Locomotive occupant = state.get(p);

        if (occupant != null && !occupant.equals(loc)) return false;

        if (p.getS88() != null && blocked.contains(p.getS88())) return false;

        // Two ACTIVE points reporting one sensor are one detection section and cannot both hold a train; blocking the
        // address would apply only to a sensor READING occupied at the snapshot.
        if (p.getS88() != null && this.pointsBySensor.containsKey(p.getS88()))
        {
            for (Point sibling : this.pointsBySensor.get(p.getS88()))
            {
                // Not gated on the sibling being active: a train on a deactivated siding holds the sensor as hard.
                if (sibling.equals(p)) continue;

                Locomotive there = state.get(sibling);

                // The MOVER is not exempt: isPathClear checks the end of every edge against live feedback, and while
                // the train still stands on this section the feedback is set (latching detection).
                if (there != null) return false;
            }
        }

        // The two exclusion lists differ.  On a NON-station, exclusion means the locomotive may not pass (the collision
        // constraint, enforced here).  On a station it means may not STOP, which canRest enforces; driving through is
        // allowed, since an operator puts "not this one, not here" on a through station (the stricter reading removed
        // 45% of the reachable station pairs on the author's layout).  Asked of the square, as isPathClear asks it.
        return !p.excludes(loc) || this.layout.isAStationSquare(p);
    }

    /**
     * Sensors reading occupied that no locomotive on the graph accounts for.
     *
     * Something is sitting on that track which the graph does not know about, and no move of ours will clear it.
     *
     * A sensor a KNOWN train stands on is not blocked here, and neither is one its TAIL lies on (Adam: "Yes, tails hold
     * sensors.", OB-073): once the plan has moved the train the section is free, which is why this reads the STATE.
     */
    private Set<String> blockedSensors(Map<Point, Locomotive> state)
    {
        Set<String> out = new HashSet<>();

        // PER CAUSE AND PER END, not per sensor (OB-073: the planner must never be looser than the railway): a sensor
        // is freed only when every tail accounting for it has gone, and a tail accounts for an edge's end only where
        // the places say it reached it.
        Map<String, Boolean> everyTailHasGone = new LinkedHashMap<>();

        for (Map.Entry<Locomotive, Map<Edge, Locomotive>> each : this.coveredAtStart.entrySet())
        for (Map.Entry<Edge, Locomotive> covered : each.getValue().entrySet())
        {
            Locomotive tail = each.getKey();

            // THIS TRAIN'S OWN PLACES, so a second tail on the same square is not read as this one's.
            Map<String, Locomotive> tailPlaces = this.placesCoveredAtStart.get(tail);

            boolean moved = false;

            for (Map.Entry<Point, Locomotive> was : this.start.entrySet())
            {
                if (!tail.equals(was.getValue())) continue;

                moved = !tail.equals(state.get(was.getKey()));

                break;
            }

            List<String> places = covered.getKey().getPlaceIds();

            for (int which = 0; which < 2; which++)
            {
                Point end = which == 0 ? covered.getKey().getStart() : covered.getKey().getEnd();

                if (end == null || end.getS88() == null) continue;

                // Only the end the tail reached: `Layout.tailLiesOn` answers for the whole edge, which would free the
                // far point of a long edge too.  The place adjacent to each end is the first for the start and the last
                // for the end; with no places, the edge is claimed whole.
                if (!places.isEmpty())
                {
                    String nearest = places.get(which == 0 ? 0 : places.size() - 1);

                    if (tailPlaces == null || !tail.equals(tailPlaces.get(nearest))) continue;
                }

                Boolean soFar = everyTailHasGone.get(end.getS88());

                everyTailHasGone.put(end.getS88(), (soFar == null || soFar) && moved);
            }
        }

        for (String sensor : this.sensorsSet)
        {
            boolean explained = Boolean.TRUE.equals(everyTailHasGone.get(sensor));

            for (Point p : this.pointsBySensor.get(sensor))
            {
                if (this.start.containsKey(p)) explained = true;
            }

            if (!explained) out.add(sensor);
        }

        return out;
    }

    /**
     * Whether this station could ever be a home for this locomotive: the planner's own question, so the answer matches
     * Return Home's.
     */
    public static boolean canBeHome(Locomotive loc, Point at)
    {
        return whyNotAHome(loc, at) == null;
    }

    /**
     * Why this square cannot be a home, or null when it can; split from {@link #canBeHome} so the refusal says why
     * (OB-050).
     *
     * @return a message key, or null when the assignment is fine
     */
    public static String whyNotAHome(Locomotive loc, Point at)
    {
        // A split square is an ordinary home: the home is the SQUARE (`Point.isSamePlaceAs`, MT-165), asked of the
        // whole square (a turning berth plus a through road suits a train that cannot reverse; which copy carries the
        // home is the builder's choice).
        return canRestOnSquare(loc, at) ? null : "autolayout.errorHomeCannotRestHere";
    }

    /**
     * The home assignment that excluding these locomotives from this station would contradict.
     *
     * canBeHome asked from the other side: excluding a locomotive from the station that is its home reaches the same
     * dead state (every Return Home reports IMPOSSIBLE), so the guard stands on both doors.
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
            // Identity, but the answer is a NAME because the warning shows it to the user.
            if (l != null && l.equals(p.getHomeLoc())) return p.getHomeLoc().getName();
        }

        return null;
    }

    /**
     * Whether this train could move from where it stands with every other train taken off (OB-325): none here means
     * none in any arrangement, so `plan` may name it as a proof.  Looser than the search on purpose (may say "can
     * move", never the reverse); `plan` asks twice, reading the sensors and then not.
     */
    private boolean hasAFirstMove(Locomotive l, Point from, boolean readTheSensors)
    {
        // Nothing to ask the track with: not a proof either way
        if (this.layout == null || from == null) return true;

        Map<Point, Locomotive> alone = new HashMap<>();

        for (Map.Entry<Point, Locomotive> at : this.start.entrySet())
        {
            if (l.equals(at.getValue())) alone.put(at.getKey(), l);
        }

        Set<String> blocked = readTheSensors ? blockedSensors(alone) : new HashSet<String>();

        // AS THE SEARCH BEGINS: nothing moved yet, so no train carries a route that brought it here
        Map<Locomotive, List<Edge>> was = this.movedAlong;

        this.movedAlong = new java.util.HashMap<>();

        try
        {
            for (Point to : this.stations)
            {
                if (to.equals(from) || alone.containsKey(to)) continue;

                if (firstClearRoute(alone, blocked, l, from, to) != null) return true;
            }

            return false;
        }
        finally
        {
            this.movedAlong = was;
        }
    }

    /**
     * Why one locomotive cannot get home, a sentence per rule broken (FR-078), each naming squares; a home rule is
     * reported only when no copy a train can STOP at would take it.
     */
    private List<String> whyNotHome(Locomotive loc, Point from, Point home)
    {
        List<String> out = new ArrayList<>();

        if (!from.isActive()) out.add(I18n.f("autolayout.whyHomeStartOutOfService", Layout.placeNameOf(from)));

        // A placement turns the train round, so "place it on a station first" would loop the operator: say what to do
        // instead.
        if (this.layout.isABarredCopyOfAStation(from))
        {
            // Without "open that side" where every train turns: that is the terminus error.
            out.add(this.layout.turnsEveryTrainAt(from)
                ? I18n.f("autolayout.whyHomeStartFacingBarredMustTurn", Layout.placeNameOf(from))
                : I18n.f("autolayout.whyHomeStartFacingBarred", Layout.placeNameOf(from),
                    I18n.t("autosetup.ui.menuArrivalsGroup")));
        }
        else if (!from.isDestination())
        {
            out.add(I18n.f("autolayout.whyHomeStartNotAStation", Layout.placeNameOf(from)));
        }

        // The copies it may come home on, as `canGetHome` asks them - so the sentence describes the rule that refused.
        List<Point> copies = homeCopiesOf(home);

        boolean anyActive = false, anyAdmits = false, anyLongEnough = false, anyStation = false;

        // Only copies a train can stop at: `validateTrainLength` says yes for a non-destination copy, so the length
        // sentence would never be said.
        for (Point copy : copies)
        {
            if (!copy.isDestination()) continue;

            anyStation = true;
            anyActive |= copy.isActive();
            anyAdmits |= !copy.excludes(loc);
            anyLongEnough |= copy.validateTrainLength(loc);
        }

        if (!anyStation)
        {
            // Nothing else about the home is worth a sentence: the other three rules are about a place a
            // train could stop, and there is none.
            out.add(I18n.f("autolayout.whyHomeNotAStation", Layout.placeNameOf(home)));
        }
        else
        {
            if (!anyActive) out.add(I18n.f("autolayout.whyHomeOutOfService", Layout.placeNameOf(home)));

            if (!anyAdmits) out.add(I18n.f("autolayout.whyHomeExcludesIt", Layout.placeNameOf(home)));

            if (!anyLongEnough)
            {
                out.add(I18n.f("autolayout.whyHomeTooShort", Layout.placeNameOf(home),
                    String.valueOf(loc.getTrainLength()), String.valueOf(home.getMaxTrainLength())));
            }
        }

        // THE ROUTE, asked only when the home would take the train: "no way there" about a square it cannot stand on is
        // a second sentence about the first problem.
        if (out.isEmpty())
        {
            boolean reachable = false;

            for (Point copy : copies)
            {
                if (canRest(loc, copy) && connected(from, copy)) reachable = true;
            }

            if (!reachable)
            {
                out.add(I18n.f("autolayout.whyHomeNoRoute", Layout.placeNameOf(from), Layout.placeNameOf(home)));
            }
        }

        // Never an empty answer for a train the plan refused: that would read as "nothing is wrong".
        if (out.isEmpty()) out.add(I18n.f("autolayout.whyHomeNoRoute", Layout.placeNameOf(from), Layout.placeNameOf(home)));

        return out;
    }

    /**
     * What can be seen in the way when the search found no arrangement (FR-078): NO_PLAN_FOUND proves nothing, so only
     * which homes another train stands on.
     */
    private Map<Locomotive, List<String>> whatCanBeSeen()
    {
        Map<Locomotive, List<String>> out = new java.util.LinkedHashMap<>();

        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || out.containsKey(l) || atHome(home, locationOf(this.start, l))) continue;

            for (Point copy : copiesOf(home))
            {
                Locomotive there = this.start.get(copy);

                if (there != null && !there.equals(l))
                {
                    reason(out, l, I18n.f("autolayout.whyHomeOccupied", Layout.placeNameOf(home),
                        there.getName()));

                    break;
                }
            }

            if (!out.containsKey(l))
            {
                reason(out, l, I18n.f("autolayout.whyHomeNoArrangement", Layout.placeNameOf(home)));
            }
        }

        return out;
    }

    /** The copies of a square: itself when it has no block, else every point sharing its block. */
    private static List<Point> copiesOf(Point square)
    {
        List<Point> out = new ArrayList<>();

        if (square.getBlock() != null && square.getLayout() != null)
        {
            for (Point copy : square.getLayout().getPoints())
            {
                if (square.getBlock().equals(copy.getBlock())) out.add(copy);
            }
        }

        if (out.isEmpty()) out.add(square);

        return out;
    }

    /** The copies of a home square a train may come home on: all, or those facing the home's way (OB-282). */
    private static List<Point> homeCopiesOf(Point home)
    {
        List<Point> all = copiesOf(home);

        if (!home.isHomeFacingFixed()) return all;

        List<Point> facing = new ArrayList<>();

        for (Point copy : all)
        {
            if (java.util.Objects.equals(copy.getCopyFacing(), home.getCopyFacing())) facing.add(copy);
        }

        return facing.isEmpty() ? all : facing;
    }

    private static void reason(Map<Locomotive, List<String>> into, Locomotive loc, String sentence)
    {
        List<String> list = into.get(loc);

        if (list == null)
        {
            list = new ArrayList<>();
            into.put(loc, list);
        }

        if (!list.contains(sentence)) list.add(sentence);
    }

    /**
     * Whether this locomotive could get home at all, over any copy of the home square.  One method, not two: resting
     * and reaching asked separately would accept a home where one copy can be rested at and a DIFFERENT one reached.
     * Blind to occupancy, like `connected`: a route blocked merely by another train is not impossible.
     */
    private boolean canGetHome(Locomotive loc, Point from, Point home)
    {
        if (home == null) return false;

        if (home.getBlock() == null || home.getLayout() == null) return canRest(loc, home) && connected(from, home);

        boolean sawACopy = false;

        // Every copy, or those facing the way a held home faces: asked of every copy, a home reachable only turned
        // round would pass here and the search would spend its budget on "maybe" about something provable.
        for (Point copy : homeCopiesOf(home))
        {
            if (!home.getBlock().equals(copy.getBlock())) continue;

            sawACopy = true;

            if (canRest(loc, copy) && connected(from, copy)) return true;
        }

        // A block naming no copies cannot be answered by looking at them.
        return sawACopy ? false : (canRest(loc, home) && connected(from, home));
    }

    /**
     * Whether a locomotive could stand anywhere on this square: `canRest` answers for one graph Point, the wrong
     * question about a HOME, which is a square.
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
     * The planner's answer to "who is standing on the same piece of track as this square": the variant of
     * `Point.Occupancy` that reads the PLAN.  It consults the square, its other copies by BLOCK as the runtime does
     * (OB-073), and the other points reporting the same SENSOR, which the runtime does NOT: that fails safe and stays
     * on purpose, since dropping it changes which stations staging offers.
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

    /** Whether a point holds a locomotive that is not the one being planned. */
    private static boolean heldBySomebodyElse(Point p, Locomotive loc, Map<Point, Locomotive> state)
    {
        Locomotive there = state.get(p);

        return there != null && !there.equals(loc);
    }

    /** The other points the planner treats as one piece of track with this one: block copies and sensor siblings. */
    private List<Point> sameTrackAs(Point track)
    {
        List<Point> out = new ArrayList<>();

        if (track.getBlock() != null && track.getLayout() != null)
        {
            for (Point copy : track.getLayout().getPoints())
            {
                if (!copy.equals(track) && track.getBlock().equals(copy.getBlock())) out.add(copy);
            }
        }

        if (track.getS88() != null)
        {
            for (Point sibling : this.pointsBySensor.getOrDefault(track.getS88(),
                java.util.Collections.<Point>emptyList()))
            {
                // A copy that is both a block sibling and a sensor sibling is asked once.
                if (!sibling.equals(track) && !out.contains(sibling)) out.add(sibling);
            }
        }

        return out;
    }

    /**
     * Whether a locomotive may come to rest on a station: its length, and the station's exclusions.  Not reversibility;
     * whether a train can GET to a terminus is a question about a route.
     */
    private static boolean canRest(Locomotive loc, Point at)
    {
        // Not the terminus rule: refusing a berth as a home to any train that cannot reverse refused most of the places
        // a train is parked.  Whether it can GET there is a route question (a path past a reversing point turns it, so
        // it backs in), asked by Layout.isPathClear; with no path to ask about, a guard here could only guess.

        return at.isDestination()
            && at.isActive()
            && !at.excludes(loc)
            && at.validateTrainLength(loc);
    }

    // There is no two-argument `connected`: which value the second argument takes is the caller's to make.


    /**
     * Whether a route exists from one square to another, ignoring who is standing where.
     *
     * There is no reversal parameter: Return Home is manual operation, so nothing asks whether the way there turned the
     * train round (Adam: "Return home is manual, take the rule out and say why").  `firstClearRoute` still tracks
     * `turned` and keys on it: collapsing ITS key changes which route the search returns first.
     */
    private boolean connected(Point from, Point to)
    {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true;

        Set<String> seen = new HashSet<>();
        Deque<Point> queue = new ArrayDeque<>();

        // No reversal state.  `plan()` consults this to call a locomotive UNREACHABLE, the only thing this class claims
        // to have proved, so a proof may be looser than the search it guards, never tighter.
        seen.add(from.getUniqueId());
        queue.add(from);

        while (!queue.isEmpty())
        {
            Point at = queue.poll();

            for (Edge e : this.layout.getNeighbors(at))
            {
                Point next = e.getEnd();

                // Arriving is checked before the visited set: the destination may be a terminus, which this search will
                // not travel THROUGH.
                if (next.equals(to)) return true;

                if (seen.contains(next.getUniqueId())) continue;

                seen.add(next.getUniqueId());

                // A terminus is traveled through here by stopping at it: `firstClearRoute` will not expand one, but
                // `plan()` ends a move at any station and starts the next from there, so stopping and setting off again
                // is an ordinary two-move plan.  Only a terminus a move could END at (a destination in service) counts.
                //
                // Still tighter than the search: an out-of-service or non-destination terminus is not expanded here, so
                // a graph whose only route home passes through such a square is proved IMPOSSIBLE although a two-move
                // plan exists.  Not reachable on a builder-derived graph; the safe direction to widen is this one.
                if (!next.isTerminus() || (next.isDestination() && next.isActive())) queue.add(next);
            }
        }

        return false;
    }

    // State helpers

    /**
     * Whether two points are one detection section: both active, and reporting the same sensor.  It asks the SENSOR,
     * not the block, and keeping it is Adam's decision (MT-187).
     *
     * @return true when the planner treats a train at one as a train at the other
     */
    private boolean sharesSection(Point a, Point b)
    {
        return a != null && b != null && !a.equals(b)
            && a.isActive() && b.isActive()
            && a.getS88() != null && a.getS88().equals(b.getS88());
    }

    /**
     * Whether a locomotive standing here is standing at its home (MT-165): any copy of a split square is the same
     * platform, by `Point.isSamePlaceAs`, which `Layout.claimHome` shares so the two agree on when a platform is taken.
     */
    private static boolean atHome(Point home, Point where)
    {
        if (home == null || !home.isSamePlaceAs(where)) return false;

        // A home set facing a way (OB-282) is met only by a copy facing that way; a copy is a facing.  Not the copy
        // with the same arrival side: that includes the home copy's turning twin, the train turned around again.
        return !home.isHomeFacingFixed() || java.util.Objects.equals(home.getCopyFacing(), where.getCopyFacing());
    }

    /** Whether the run turns a train round when a leg ends here (`ALWAYS_REVERSE`: a terminus or reversing point). */
    private static boolean turnsATrainArrivingAt(Point where)
    {
        return where != null && (where.isTerminus() || where.isReversing());
    }

    /**
     * Whether the only way from one square to another doubles back through a copy of either.
     *
     * `Layout.bfs` will not walk such a route, so the menu offers no such destination while the planner may use one
     * (Adam chose "menu and autonomy only").  Asked as the two searches `Layout.firstClearOrWhyNot` uses; occupancy
     * is not consulted.
     */
    private boolean onlyALapReachesIt(Point from, Point to)
    {
        if (this.layout == null) return false;

        try
        {
            if (this.layout.bfsGoingHome(from, to, null) != null) return false;

            return this.layout.anyTrackRouteBetween(from, to);
        }
        catch (Exception unsearchable)
        {
            return false;
        }
    }

    /**
     * Whether a route turned the train on its way, short of where it ends.  The end is the caller's question
     * (`turnsATrainArrivingAt`, the backing-in case of MT-245); a reversing point passed on the way leaves the train
     * running the other way.
     */
    private static boolean turnedOnTheWay(List<Edge> route)
    {
        if (route == null) return false;

        for (int at = 0; at + 1 < route.size(); at++)
        {
            if (route.get(at).getEnd().isReversing()) return true;
        }

        return false;
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

    /**
     * A lower bound on the moves still needed from this arrangement, the A*'s estimate (OB-230).  Never more than the
     * true number, so the plan found stays one of the fewest.
     */
    private int movesStillNeeded(Map<Point, Locomotive> state)
    {
        int needed = misplaced(state);

        // Who stands on the home of each homed train that is still away from it.
        Map<Locomotive, Locomotive> onMyHome = new HashMap<>();
        Set<Locomotive> homelessInTheWay = new HashSet<>();

        for (Map.Entry<Locomotive, Point> homed : this.homes.entrySet())
        {
            Point at = locationOf(state, homed.getKey());

            if (at == null || atHome(homed.getValue(), at)) continue;

            for (Map.Entry<Point, Locomotive> standing : state.entrySet())
            {
                if (standing.getValue().equals(homed.getKey()) || !atHome(homed.getValue(), standing.getKey())) continue;

                if (this.homes.get(standing.getValue()) == null) homelessInTheWay.add(standing.getValue());
                else onMyHome.put(homed.getKey(), standing.getValue());
            }
        }

        needed += homelessInTheWay.size();

        // The rings: follow each train to whoever stands on its home, until the chain ends or closes.
        Set<Locomotive> seen = new HashSet<>();

        for (Locomotive start : onMyHome.keySet())
        {
            if (seen.contains(start)) continue;

            List<Locomotive> chain = new ArrayList<>();
            Locomotive at = start;

            while (at != null && !seen.contains(at) && !chain.contains(at))
            {
                chain.add(at);
                at = onMyHome.get(at);
            }

            if (at != null && chain.contains(at)) needed++;

            seen.addAll(chain);
        }

        return needed;
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

    /**
     * An arrangement's key: where the trains stand, and the track the trains the plan has moved lie across.
     *
     * Two orders of moves that reach the same squares are one arrangement only if they leave the same tails, else a
     * search keeping the first can answer NO_PLAN_FOUND where a plan exists.  Keyed by the edges each tail covers
     * (`Layout.edgesATailWouldCover`), not the route, so the state count grows only where tails really differ.
     */
    private String tailKey(Map<Point, Locomotive> state, Map<Locomotive, List<Edge>> routes)
    {
        if (routes.isEmpty() || this.layout == null) return key(state);

        List<String> tails = new ArrayList<>();

        // Also what the plan did to each moved train: `turnedByThePlan` reads from `movedAlong` whether the train is in
        // it and whether its route passed a reversing point, and `key(state)` carries neither.  Every moved train is
        // named as `<name>/turned` or `<name>/straight` (the spelling `firstClearRoute` uses), which also keys the
        // first fact, since the tails list below drops a train whose tail covers nothing.  A finer key can cost more of
        // SEARCH_LIMIT but cannot lose a plan.
        List<String> turned = new ArrayList<>();

        for (Map.Entry<Locomotive, List<Edge>> moved : routes.entrySet())
        {
            Locomotive train = moved.getKey();
            List<Edge> road = moved.getValue();

            if (road == null || road.isEmpty()) continue;

            turned.add(train.getName() + (turnedOnTheWay(road) ? "/turned" : "/straight"));

            Point end = road.get(road.size() - 1).getEnd();

            // Only a train still standing where that route left it lies along it.
            if (end == null || !train.equals(state.get(end))) continue;

            Map<Edge, Locomotive> covered = this.coveredByAMove.computeIfAbsent(road,
                r -> this.layout.edgesATailWouldCover(end, train, r));

            if (covered.isEmpty()) continue;

            List<String> legs = new ArrayList<>();

            for (Edge leg : covered.keySet()) legs.add(leg.getStart().getName() + ">" + leg.getEnd().getName());

            Collections.sort(legs);

            tails.add(train.getName() + ":" + String.join(",", legs));
        }

        StringBuilder out = new StringBuilder(key(state));

        if (!tails.isEmpty())
        {
            Collections.sort(tails);

            out.append('#').append(String.join("|", tails));
        }

        if (!turned.isEmpty())
        {
            Collections.sort(turned);

            out.append('/').append(String.join(",", turned));
        }

        return out.toString();
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
