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
     * How heavily the second half of the search weighs the moves still needed against the moves made (OB-230).  At 1
     * the search finds the shortest plan and, on a crowded railway, runs out of time finding it; weighted, it may
     * return a plan longer than the shortest, by at most this factor.  5 solves the six-train arrangement of
     * `core.testReturnHomeFindsAPlanOnAFullRailway` where 2 did not in fifteen seconds.
     */
    private static final int ANY_PLAN_WEIGHT = 5;

    /**
     * Wall clock, because a state count cannot bound the time this takes.
     *
     * Expanding one state runs firstClearRoute once per locomotive per station: milliseconds on a 62-point layout.  An
     * arrangement with no solution must say NO_PLAN_FOUND ("may still be possible") within this budget, not freeze.
     *
     * **One budget for the whole search, shared out** (OB-230).  A third looks for the SHORTEST plan from where the
     * greedy pass stopped, a third for the shortest from the railway as it stands (the retry OB-228 needs), and the
     * last for ANY plan from the railway as it stands, with the estimate weighted (`ANY_PLAN_WEIGHT`).  Halves,
     * shortest then any, when the greedy pass moved nothing.
     */
    private static final long SEARCH_BUDGET_MS = 15000;

    /** When the search in progress started - set once per `search`, so every `astar` it runs is timed from it. */
    private long searchStarted;

    /**
     * Where the search reads the time: the wall clock on the railway.  A field so a test can step it, since a claim on
     * the real clock would spend fifteen seconds a run (`core.testReturnHomeKeepsClearOfTheTailsItLeaves` sets it).
     */
    private java.util.function.LongSupplier clock = System::currentTimeMillis;

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
     * The track the trains were lying across when the snapshot was taken, and who is lying across it (OB-184).
     *
     * A train longer than the track it stands on protrudes onto the track BEHIND it, which belongs to no sensor.
     * `Layout.isPathClear` consults it, so the planner must too.
     *
     * Only for a train that has not moved yet: where a tail lies after a move depends on the side the train arrives by,
     * which nothing here models.  So a train still standing where it started blocks what it really blocks, and one the
     * plan has moved blocks nothing extra.  That under-claims, the safe direction: the runtime refuses what it must,
     * and a planner that over-refused would report a railway as impossible that is not.
     */
    private final Map<Locomotive, Map<Edge, Locomotive>> coveredAtStart;

    /** The same, by place rather than by edge - what the shared-metal refusal is narrowed with. */
    private final Map<Locomotive, Map<String, Locomotive>> placesCoveredAtStart;

    /**
     * The route each train the search has moved last took, for the arrangement being expanded (OB-228, MT-335).  A
     * train the plan has moved lies along its route, where `coveredAtStart` is the railway before the plan.  Set before
     * every expansion: the greedy pass keeps it as it goes, A* rebuilds it from the chain of moves that reached the
     * arrangement.
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

        // Taken once, with the rest of the starting state: asking the live layout during the search would answer for a
        // railway that has moved on.  At both grains: the edges a standing train's tail touches, and the pieces of
        // metal it lies on (what the runtime narrows its shared-metal refusal with).  One train at a time: the
        // railway's single map holds one train per place, so two tails fouling one switch from its two legs would be
        // recorded as one.
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

                // READ, never inferred: a stationary locomotive does not necessarily hold its sensor (in simulation the
                // feedback is pulsed), and a bypass and its platform routinely report the same sensor.
                if (layout.isFeedbackOccupied(p.getS88())) sensorsSet.add(p.getS88());
            }
        }

        // Launch pads: stations with zero incoming edges.  Some layouts stage trains on one-way tracks and re-stage by
        // hand.  The topology declares the intent: a positional home on one is not a home once left, and the search
        // never moves a locomotive off one (both below).
        Set<String> launchPads = new HashSet<>();

        for (Point p : layout.getPoints())
        {
            if (layout.getIncomingEdges(p).isEmpty()) launchPads.add(p.getName());
        }

        // A POSITIONAL home on a launch pad stops being a home once the locomotive has LEFT it: nothing can re-enter
        // the pad, and a plan is all-or-nothing, so keeping the entry would make Return Home refuse to move ANYTHING.
        // Still standing there, the claim is satisfied (dropping it would turn ALREADY_HOME into NO_HOMES).  An
        // ASSIGNED home keeps the strict contract: an unreachable one answers IMPOSSIBLE with the locomotive named.
        Map<Locomotive, Point> homes = new LinkedHashMap<>(layout.getHomeStations());

        for (Iterator<Map.Entry<Locomotive, Point>> it = homes.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry<Locomotive, Point> entry = it.next();

            // Identity, not a name: a name comparison would answer false for ever and treat every home as positional.
            boolean assigned = entry.getKey().equals(entry.getValue().getHomeLoc());
            // The SQUARE, not the Point (MT-165): `occupancy.get(home)` names one copy of a platform, so a train on the
            // other copy of its own home would read as not standing on it.
            boolean standingOnIt = atHome(entry.getValue(), locationOf(occupancy, entry.getKey()));

            if (!assigned && !standingOnIt && launchPads.contains(entry.getValue().getName()))
            {
                it.remove();
            }
        }

        // A PAUSED TRAIN STAYS WHERE IT STANDS (FR-117): for this plan its home is where it stands, so nothing is
        // planned for it, the check after a run counts it home, and the search never moves it out of another's way
        // (`paused`).  One away from a home of its own is named in the log (`getPausedAwayFromHome`).
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
     *
     * @return the trains, in the order they stand
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
         * Every train that can be sent home is on its home, but some that are not are paused (OB-355): nothing is
         * planned for a paused train, so this is not ALREADY_HOME - see getBlocked for those trains.
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
         * A locomotive is held on more than one point, so where it is standing cannot be said at all - see getBlocked.
         *
         * A locked path reserves every point along it for one locomotive, and a path that failed part-way through
         * unlocking leaves those reservations behind; nothing in the model distinguishes a reservation from a train.
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

        Plan(Outcome outcome, List<Move> moves, List<Locomotive> blocked,
            Map<Locomotive, List<String>> reasons)
        {
            this.outcome = outcome;
            this.moves = moves;
            this.blocked = blocked;
            this.reasons = reasons;
        }

        /**
         * Why each locomotive the plan could not bring home was refused, one sentence per fact found (FR-078).
         *
         * For IMPOSSIBLE it is a proof, one entry per locomotive on `getBlocked`.  For NO_PLAN_FOUND it is only what
         * can be SEEN from where everything stands (a home somebody else is standing on), because that outcome proves
         * nothing and its sentences must not pretend to.
         *
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
     * @return
     */
    public Plan plan()
    {
        Outcome trivial = triage();

        // The paused trains away from their homes are the plan's "blocked" for this outcome, so the sentence can name them
        if (trivial == Outcome.PAUSED_AWAY) return new Plan(trivial, empty(), new ArrayList<>(this.pausedAway));

        if (trivial != null) return new Plan(trivial, empty(), noLocs());

        // A locomotive in two places at once, which nothing below this line can reason about.  A locked path reserves
        // every point along it for one locomotive, and one that failed part-way through unlocking leaves those
        // reservations standing; nothing in the model tells a reservation from a train (reserve() and setLocomotive()
        // write the same field).  Guessing an origin could drive a real train from a place it is not standing, while
        // NO_PLAN_FOUND claims nothing false, so it is reported with the locomotive's name and the ordinary remedy:
        // place the train on the square it is actually on, which sweeps the rest.
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

        // A locomotive with no route to its home at all cannot be helped by moving anything else, and is the only
        // impossibility this class can prove.  Locomotives already home are skipped: a route from a point back to
        // itself is a different question, and on a layout without a loop its answer "none" would report a graph in
        // order as impossible.
        List<Locomotive> unreachable = new ArrayList<>();

        // One sentence per fact, per locomotive, for the log (FR-078).
        Map<Locomotive, List<String>> reasons = new java.util.LinkedHashMap<>();

        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || atHome(home, locationOf(this.start, l))) continue;

            // A home the locomotive could never be parked at (inactive, excluding it, too short, or a terminus it
            // cannot reverse out of), or an origin it cannot leave (an inactive one), is impossible by construction.
            // The STATELESS canRest and no FR-001 test: this pre-scan reads the STARTING occupancy, so a state-aware
            // canRest (OB-073) would let a locomotive merely standing on a watched square prove the goal unreachable.
            // FR-001 is asked at the arrival test in `firstClearRoute`.
            //
            // THE SQUARE: `canRest` and `connected` are asked of the same copy of the home, and a terminus turns a
            // train round as it ARRIVES, so only the copies that turn are closed to a locomotive that cannot reverse.
            //
            // The inactive start is refused here deliberately.  A hand-driven send allows one (Adam: "inactive really
            // means nothing can pass", with the square a train is ALREADY standing on exempted), but Return Home plans
            // for every staged locomotive at once, and a square switched off is one its author said to leave alone.
            if (!locationOf(this.start, l).isActive()
                || !locationOf(this.start, l).isDestination()
                || !canGetHome(l, locationOf(this.start, l), home))
            {
                unreachable.add(l);

                // And why (FR-078), asked of the same three conditions so the sentence describes the rule that refused.
                reasons.put(l, whyNotHome(l, locationOf(this.start, l), home));
            }
        }

        // Nor one that cannot leave where it stands (OB-325): a reversible train of 4 on BottomMainB's turning copy,
        // from which every reachable station is shorter, has its home connected by track and passes the scan above, yet
        // has no first move, and the search would run the whole fleet for about fifteen seconds and name nobody.  Asked
        // with every other train taken off (`hasAFirstMove`), which makes it a proof: another train only takes moves
        // away, so a train with none on an empty railway has none on any.
        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || unreachable.contains(l)) continue;

            Point from = locationOf(this.start, l);

            if (atHome(home, from) || this.hasAFirstMove(l, from, true)) continue;

            // Not where a sensor is all that holds it: a sensor reading occupied with no train on the graph to account
            // for it (a vehicle TrainControl does not know, a dirty contact) blocks every arrangement alike, so it
            // would pass for a proof, but it is live state and goes when somebody clears it.  Such a train is left to
            // the search, which answers "it may still be possible, try moving something by hand".
            if (this.hasAFirstMove(l, from, false)) continue;

            unreachable.add(l);

            reason(reasons, l, I18n.f("autolayout.whyHomeCannotLeave", Layout.placeNameOf(from)));
        }

        // Goals that conflict with each other, which no arrangement can satisfy either.  Two homes on one detection
        // section is the easiest wrong click on a layout that shares addresses, and nothing warns at assignment because
        // canBeHome is a one-station question.  Provable pairwise, so the search need not spend its budget to say
        // "maybe".
        for (Map.Entry<Locomotive, Point> a : this.homes.entrySet())
        {
            if (!this.start.containsValue(a.getKey())) continue;

            for (Map.Entry<Locomotive, Point> b : this.homes.entrySet())
            {
                if (a.getKey().equals(b.getKey()) || !this.start.containsValue(b.getKey())) continue;

                if (!sharesSection(a.getValue(), b.getValue())) continue;

                // Both already parked: nothing arrives, so nothing is checked.  What is proved here is that the SECOND
                // arrival onto a shared detection section is refused, and both trains stand on their own homes.
                // Leaving the exemption out costs no plan, not a worse one: IMPOSSIBLE refuses the WHOLE staging run,
                // so a third locomotive that only needed driving to the next platform never moves.  Only one of the two
                // has to be away for the proof to hold again (testTwoActivePointsSharingASensorAreNeverBothOccupied).
                // Not reachable on a layout from the 3.0.0 diagram, where a home on a shared-sensor square is refused
                // by whyNotAHome.
                if (atHome(a.getValue(), locationOf(this.start, a.getKey()))
                    && atHome(b.getValue(), locationOf(this.start, b.getKey()))) continue;

                if (!unreachable.contains(a.getKey())) unreachable.add(a.getKey());
                if (!unreachable.contains(b.getKey())) unreachable.add(b.getKey());

                reason(reasons, a.getKey(), I18n.f("autolayout.whyHomeSharesASection",
                    Layout.placeNameOf(a.getValue()), b.getKey().getName(), Layout.placeNameOf(b.getValue())));
            }
        }

        // And no cycle scan over the occupancy restrictions (OB-085).  Two homes that each hold back the square the
        // other's occupant must end on are a real deadlock now that the planner enforces FR-001, but IMPOSSIBLE names
        // locomotives and asserts no arrangement exists, and the scan that produced that verdict was wrong three times,
        // each time about a railway that works.  A search that exhausts and answers NO_PLAN_FOUND is the weaker and
        // true claim (`behaviour/tiers-and-choice.md` section 1;
        // `testHomeStaging.testTwoHomesThatHoldEachOtherBackAreADeadlock` pins it).  The pairwise scan above stays: it
        // is about two homes on ONE DETECTION SECTION, a fact about the track.

        if (!unreachable.isEmpty())
        {
            return new Plan(Outcome.IMPOSSIBLE, empty(), unreachable, reasons);
        }

        List<Move> moves = search();

        if (moves == null)
        {
            return new Plan(Outcome.NO_PLAN_FOUND, empty(), noLocs(), whatCanBeSeen());
        }

        return new Plan(Outcome.READY, moves, noLocs());
    }

    /**
     * Compares the planner's idea of where each locomotive may go against the runtime's, for the state as it stands
     * now.
     *
     * The planner answers for hypothetical futures, so it cannot call isPathClear (which reads live feedback) and
     * re-implements the rules; this checks that re-implementation against the original for the ONE state where both can
     * be asked, the present: the two answers must match.
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

            // A locomotive standing on a deactivated point is the third correct divergence, the mirror of the
            // inactive-destination one below: getPossiblePaths is asked at rest, where the runtime's inactive rule is
            // not in force, so it offers departures the planner refuses because staging executes with autonomy running.
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
                // Inactive points are the one divergence that is correct rather than a defect: the runtime skips its
                // inactive-point rule unless autonomy is already running, so at rest it offers parking tracks that it
                // would refuse once a timetable is under way.  The planner reasons about the running case.
                if (!p.isActive()) continue;

                // And the same for exclusions: the oracle here is getPossiblePaths, which filters destinations on
                // occupancy and station-ness but not on exclusion (pickPath does that separately), so it offers
                // stations the planner's canRest correctly refuses.
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
                // A destination only a lap reaches is the fourth correct divergence, and belongs on THIS side:
                // `Layout.bfs` refuses a route that doubles back through another copy of the square it starts or ends
                // at, so the menu (this oracle) offers none.  The planner does (Adam ruled the copy rule onto the "menu
                // and autonomy only"), and the RUNTIME agrees: `isPathClear` exempts an intermediate copy of the
                // train's own square.
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
     * Separated so a caller can find out cheaply, before asking the user a question that would be rude when the answer
     * is "nothing needed".  It reads the occupancy and nothing more; plan() delegates to it, so the two can never
     * disagree about whether there is work.
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
            // A paused train counts as home for the plan (FR-117), which is true of nothing else: said apart, so "every
            // locomotive is already home" is not shown over trains standing away from theirs (OB-355)
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
        List<Move> plan = new ArrayList<>();

        // Nothing has moved yet (OB-228).
        this.movedAlong = new java.util.HashMap<>();

        // ONE BUDGET, for every search this runs, shared out below.
        this.searchStarted = this.clock.getAsLong();

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

        if (misplaced(state) == 0) return plan;

        // A* starts from the greedy pass's arrangement, so it inherits the routes that pass gave (OB-228).
        this.greedyMovedAlong = new java.util.HashMap<>(this.movedAlong);

        // A SHARE OF THE BUDGET EACH (OB-230): the shortest plan from where the greedy pass stopped, the shortest
        // from the railway as it stands, then any plan.  Thirds when the greedy pass moved something; halves when it
        // moved nothing, because then the first two would search from the same arrangement.
        boolean retry = !plan.isEmpty();

        long share = SEARCH_BUDGET_MS / (retry ? 3 : 2);

        List<Move> rest = astar(state, this.searchStarted + share, 1);

        if (rest != null)
        {
            plan.addAll(rest);

            return plan;
        }

        // Not only from where the greedy pass left things (OB-228): it makes every move it can in the order it meets
        // the trains, and a move it makes can block the rest (a train parked on a platform with no way out, its tail
        // across the run another train needs), which no search from there can undo.  So when nothing is found, search
        // again from the railway as it stands.
        this.greedyMovedAlong = new java.util.HashMap<>();

        if (retry)
        {
            List<Move> fromTheStart = astar(new LinkedHashMap<>(this.start), this.searchStarted + 2 * share, 1);

            if (fromTheStart != null) return fromTheStart;
        }

        // And then for ANY plan, not the shortest (OB-230; Adam: "the whole point of the A* is to figure out how to
        // rearrange other trains to park things when they belong").  On a crowded railway a search for the shortest
        // plan runs out of time and finds nothing, while the weighted search finds a longer plan that brings everybody
        // home.  Not in place of the retry above: on the railway that retry exists for, the weighted search heads down
        // sixty sidings and finds nothing in its time (`core.testReturnHomeKeepsClearOfTheTailsItLeaves`).
        return astar(new LinkedHashMap<>(this.start), this.searchStarted + SEARCH_BUDGET_MS, ANY_PLAN_WEIGHT);
    }

    /**
     * A* over configurations, with `movesStillNeeded` as the heuristic, weighted by `weight`.
     *
     * Admissible: every move relocates exactly one locomotive, and the estimate counts only moves that cannot be
     * avoided (OB-230).
     */
    private List<Move> astar(Map<Point, Locomotive> from, long deadline, int weight)
    {
        Map<String, Map<Point, Locomotive>> states = new HashMap<>();
        Map<String, Integer> cost = new HashMap<>();
        Map<String, Integer> score = new HashMap<>();
        Map<String, Move> arrivedBy = new HashMap<>();
        Map<String, String> cameFrom = new HashMap<>();

        // The routes each arrangement's moved trains came by, kept with it: two orders reaching the same squares with
        // different tails are different arrangements (a train parked the short way lies across the run another needs;
        // parked the long way, it does not).
        Map<String, Map<Locomotive, List<Edge>>> routesOf = new HashMap<>();
        Map<Locomotive, List<Edge>> startRoutes = new java.util.HashMap<>(this.greedyMovedAlong);

        String startKey = tailKey(from, startRoutes);

        routesOf.put(startKey, startRoutes);
        states.put(startKey, from);
        cost.put(startKey, 0);
        score.put(startKey, weight * movesStillNeeded(from));

        // Ordered on a precomputed f-score, carried by each entry rather than read from the score map: the relaxation
        // below re-scores a state when it finds a cheaper route, and rewriting the map would change a queued entry's
        // priority in place and break the heap invariant.
        //
        // Ties are broken towards the goal (OB-230): of two entries with the same score, the one with fewer moves still
        // needed is further along.  The score still orders the queue, so a plan found is still one of the fewest moves.
        PriorityQueue<Scored> open = new PriorityQueue<>((a, b) -> a.score != b.score
            ? Integer.compare(a.score, b.score) : Integer.compare(a.stillNeeded, b.stillNeeded));

        open.add(new Scored(startKey, score.get(startKey), movesStillNeeded(from)));

        Set<String> closed = new HashSet<>();
        int examined = 0;



        while (!open.isEmpty() && examined < SEARCH_LIMIT && this.clock.getAsLong() < deadline)
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

            // THE ROUTES THAT BROUGHT THE TRAINS TO THIS ARRANGEMENT (OB-228), as it was reached.
            this.movedAlong = routesOf.get(currentKey);

            // One set per state, not one per candidate move: nothing moves while we expand this state
            Set<String> blocked = blockedSensors(current);

            for (Locomotive l : new ArrayList<>(current.values()))
            {
                Point at = locationOf(current, l);

                // NOR A PAUSED TRAIN (FR-117): it stays where it stands, in somebody's way or not
                if (this.paused.contains(l)) continue;

                // A locomotive standing on a launch pad stays there unless its assigned home lies elsewhere.  A pad has
                // no incoming edges, so a move off one can never be undone and the hand-staging it represents would be
                // destroyed as a side effect of someone else's plan.  Keyed on the pad and the home, not on
                // homelessness: a satisfied positional pad-home stays in the homes map (that is what makes a staged
                // layout report ALREADY_HOME).  One already dispatched from its pad is an ordinary free agent.
                Point ownHome = this.homes.get(l);

                // atHome: a train resting on the far copy of its own home square is resting at home, and pinning it to
                // the exact copy can ask the A* for a move that no arrival side allows.
                if (this.launchPads.contains(at.getName()) && (ownHome == null || atHome(ownHome, at)))
                {
                    continue;
                }

                // A train that cannot reverse is turned on the way only to go home (Adam: "This should only be allowed
                // if the train is going to reverse into its berth on the next turn.").  The run turns a train at every
                // terminus and reversing point a leg ends at (`ALWAYS_REVERSE`), so resting such a train on one and
                // then sending it anywhere but home would drive it on running the other way (OB-205).  A train the PLAN
                // has put on one moves next only to its home.
                //
                // Carried by the train, not read off the square: a plan could turn a train into a berth, which the
                // ruling permits, and then take it out to an ordinary station, which it does not.  So what is asked is
                // whether the route that brought the train here turned it.  And only of a train THIS PLAN has moved
                // (`movedAlong` has an entry): `turnsATrainArrivingAt(at)` asks about the SQUARE and cannot tell who
                // turned the train on it, so a train the railway already had on a terminus would be refused every
                // square there is.
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

                    // Nor turned in the middle of the move and sent on.  The rules above are about where a move ENDS; a
                    // route may also turn the train at a reversing point on the way (how a non-reversing train backs
                    // into a berth, MT-245).  That is allowed only where this move ends somewhere the turn was FOR: the
                    // train's home, a berth, or a square it comes to rest facing out of; elsewhere it would be driven
                    // on backwards.
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

        // Not round a loop back onto the square it stands on (Adam: "These are the same point so we should never do a
        // round trip just to change direction").  Another copy of the train's own square is the same track, which the
        // menu and autonomy never offer (`Point.getBlockLocomotive` reads it as occupied by the train).  A train on any
        // copy of its home is already home (`atHome`), so this never keeps a train from home.
        if (from.getBlock() != null && from.getBlock().equals(to.getBlock())) return null;

        // The origin is exempt from every other test here (so the moving train's own sensor does not block its own
        // departure) but not from these two.  isPathClear applies its inactive-point rule to the first edge too, and
        // staging runs with autonomy on, so a train on a deactivated point would be planned home and refused at its
        // first edge.
        if (!from.isActive()) return null;

        // And the rule in the next `if`, "Starting point is not a station": staging is fully autonomous operation
        // (executeTimetable sets `running`).  Missing it costs more than a refused plan: the run STARTS, the first leg
        // is refused, and the retry loop abandons the run, for a train parked where no automatic path may begin.
        if (!from.isDestination()) return null;
        // FR-001 at the arrival is asked by the search below, of every square it arrives at, `to` included, on the
        // PLANNED state.  The runtime enforces it in every tier, and a leg the railway then refuses is OB-073.
        // `auditAgainstRuntime` proves the two agree.
        if (!canRest(loc, to) || state.containsKey(to)) return null;

        // Where the plan has put everybody, for the FR-001 question at every square on the way (OB-295).
        final Point.Occupancy planned = plannedOccupancy(state);

        // Its own body as it stands here, for the own-tail question below (OB-294): the road this plan moved it along,
        // or what the railway records where it has not.  Walked after the cheap refusals and once per train and road,
        // since it is asked once per destination inside a search with a time budget.
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
        // `turned` is only "a different way of getting here": nothing reads it as a rule (Return Home is manual, so a
        // terminus is not refused to a train that cannot reverse).  It survives in the visited key below, where it
        // keeps two routes to one square apart; collapsing the key would change which route this search returns first.
        // Not seeded at a TERMINUS: `executePath` flips direction on arrival at a terminus and at a reversing point
        // alike, but at a terminus that flip turns a backed-in train round to face out, so it is spent and a train
        // leaving one leaves FORWARDS; seeding true there would count the same flip twice.
        queue.add(new Candidate(from, new LinkedList<Edge>(),
            new HashMap<String, Accessory.accessorySetting>(), from.isReversing()));

        while (!queue.isEmpty() && expansions++ < ROUTE_SEARCH_LIMIT)
        {
            Candidate current = queue.poll();

            // By name, not as the railway shuffles them: `Layout.getNeighbors` shuffles deliberately (autonomy spreads
            // its choices that way) and this search returns the FIRST clear route, so a plan would ride on the shuffle,
            // READY on one press and NO_PLAN_FOUND on the next.  Ordered here, not in `getNeighbors`, which every tier
            // shares.
            List<Edge> outward = new java.util.ArrayList<>(this.layout.getNeighbors(current.at));

            java.util.Collections.sort(outward, (one, two) -> one.getName().compareTo(two.getName()));

            for (Edge e : outward)
            {
                Point next = e.getEnd();

                // The copy rule is NOT applied here, by Adam's ruling: `Layout.bfs` refuses a route passing another
                // copy of the square it starts or ends at, so the menu and autonomy never offer a lap, but applying it
                // here lost Return Home plans (`core.testTrainsComeHomeToTheirPlatforms`).  He chose "menu and autonomy
                // only": the planner keeps the looser search.  The runtime agrees (`Layout.isPathClear` exempts an
                // intermediate copy of the train's own square) and `auditAgainstRuntime` makes the matching exemption.
                //
                // Whether the train has been turned round by the time it stands here: part of the STATE
                // (Candidate.turned).
                boolean turned = current.turned || next.isReversing();

                if (!canEnter(next, loc, blocked, state)) continue;

                // And not through a square held back by an occupied one (OB-295): `isPathClear` asks FR-001 of every
                // square a route arrives at, so a plan through one is OB-073's plan.  Asked of the planned state; a
                // route reaches `to` only here, so this is the destination's own test too.
                if (Point.heldBackBy(next, loc, planned) != null) continue;

                // And the track a standing train is lying across (OB-184).  `canEnter` asks about the POINT this edge
                // leads to; this asks about the EDGE, since a long train protrudes onto track no point reports.
                if (!passesTheTailsOfTrainsThatHaveNotMoved(e, loc, state)) continue;
                if (!passesTheTailsOfTrainsItHasMoved(e, loc, state)) continue;

                // Lock edges are deliberately NOT consulted here.  The runtime does not count an edge as occupied when
                // the point it leads to holds another locomotive, so asking here would make the planner the stricter
                // half and report NO_PLAN_FOUND after its whole budget.  Nothing replaces it, because a staging plan
                // runs ONE TRAIN AT A TIME (see Layout's staging flag): no other route is running to hold the track.
                // auditAgainstRuntime proves the two halves agree.

                Map<String, Accessory.accessorySetting> commands = withCommandsOf(e, current.commands);

                // Two edges asking one accessory for opposite settings
                if (commands == null) continue;

                // Keyed by the situation, not the square: the same point reached with and without a reversal behind it
                // are two states, and collapsing them lets the short way close off the only route that could back the
                // train in.
                List<Edge> route = new LinkedList<>(current.route);
                route.add(e);

                // And not round a loop into its own tail (OB-294), as `Layout.isPathClear` asks it.  Prefix-closed,
                // like the room rule below: no extension of a route that meets the tail can clear it, so pruning loses
                // nothing.
                if (Layout.whyItWouldMeetItsOwnTail(route, loc, ownBody) != null) continue;

                // Room is asked before the arrival is recorded: the refusal below is a `continue` because a longer
                // approach is more room, but `alreadyReached` is a DOMINANCE test, so a short arrival recorded first
                // would drop the longer one before its room was measured.
                //
                // Wherever the runtime asks, and nowhere else (`Layout.whyTooLongForThisRoute`): fewer squares would
                // have the first move refused, more would drop plans that would have run.  So this asks the destination
                // and a square the train turns at, the berth judged on the whole route behind it (MT-333).  The
                // property is prefix-closed, so no extension of a route the train does not fit on can fit.
                if (next.equals(to))
                {
                    // At the destination, the berth rule as well as the room.  `Layout.whyABerthCannotHoldIt` is the
                    // static method `isPathClear` asks, so the two cannot disagree (OB-073).
                    //
                    // And the platform relaxation: the room is asked as `measuredRoomAtTheEndOf`, not the whole of
                    // `whyTooLongForThisRoute`, whose station capacity and per-square room tests made this planner
                    // STRICTER than the runtime (`core.testTrainsComeHomeToTheirPlatforms`).  A station autonomy may
                    // choose takes a train its measured route in holds (FR-087), so the relaxation lives in
                    // `theApproachItselfHoldsIt` and both sides ask it.
                    Integer room = Layout.measuredRoomAtTheEndOf(route, loc);

                    if (room != null && loc.getTrainLength() > room
                        && !Layout.theApproachItselfHoldsIt(route, loc))
                    {
                        continue;
                    }

                    // The station's stated capacity is not asked here: `canRest` ends with
                    // `at.validateTrainLength(loc)`, so a home limited below the train's length is refused before any
                    // route is searched, and a second spelling of a rule is the kind that stops agreeing later.
                    // `core.testHomeStaging.testThePlannerRefusesAHomeTheOperatorSaidIsTooShort` pins it.

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
                    // No turn is required here (Adam: "Return home is manual, take the rule out and say why."), as in
                    // `isPathClear` for manual operation.  `pickPath` keeps the rule: autonomy chooses destinations
                    // nobody asked for, and backing a train into a dead end unasked differs from doing it because
                    // somebody pressed the button.
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
     * Whether this point has already been reached under commands that leave at least as much freedom.
     *
     * Arriving somewhere is no longer enough to dismiss a later arrival, because the settings committed
     * on the way decide what may still be done.  A previous arrival only makes this one redundant if it
     * committed to nothing that this one has not also committed to - then anything reachable from here
     * was reachable from there.
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

    /**
     * Whether this edge is free of the tails the plan's own moves leave behind (OB-228, MT-335).  A train the plan
     * moved stands at the end of its route, having come in along it; `Layout.edgesATailWouldCover` walks that tail with
     * the runtime's own code.  The same shared-metal and places rules as the unmoved trains, and a train never blocks
     * itself.  Only a train still standing where that route left it counts; one moved again has a later route.
     */
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

            if (covered.containsKey(edge)) return false;

            Map<String, Locomotive> places = this.placesByAMove.computeIfAbsent(road,
                r -> this.layout.placesATailWouldCover(end, train, r));

            // AND EVERY OTHER COPY OF THE SAME METAL, as `Layout.isPathClear` asks it.
            if (Layout.anotherTailOn(edge, mover, places) != null) return false;

            for (Edge sharing : edge.getLockEdges())
            {
                if (!covered.containsKey(sharing)) continue;
                if (!sharing.getLockEdges().contains(edge)) continue;
                if (!edge.getPlaceIds().isEmpty() && !sharing.getPlaceIds().isEmpty()
                    && !Layout.tailLiesOn(edge, train, places))
                {
                    continue;
                }

                return false;
            }
        }

        return true;
    }

    /**
     * Whether this edge is free of the tails of trains that are still where they started (OB-184).
     *
     * A train never blocks ITSELF, as in `Layout.isPathClear`; the two rules must agree or the planner offers what the
     * runtime refuses.  The "has not moved" test keeps this sound: once the plan has moved a train, `coveredAtStart` is
     * about track it has left, and blocking on it would refuse routes that are clear.  Every train lying across the
     * edge is asked, not the first found: otherwise an edge two start tails lie across would pass once the first has
     * gone, a plan that stops half way (OB-073).
     */
    private boolean passesTheTailsOfTrainsThatHaveNotMoved(Edge edge, Locomotive mover,
        Map<Point, Locomotive> state)
    {
        Set<Locomotive> lyingAcross = new LinkedHashSet<>();

        // EACH TRAIN'S OWN RECORD, one at a time - so a place two tails claim answers with both.
        for (Map.Entry<Locomotive, Map<Edge, Locomotive>> each : this.coveredAtStart.entrySet())
        {
            Locomotive train = each.getKey();

            // THE MOVER'S OWN TAIL IS NOT THE ANSWER, only not an obstacle (OB-285): the other trains on the same metal
            // are still asked about, as `Layout.isPathClear` asks them since the same change.
            if (train.equals(mover)) continue;

            if (liesAcrossAtStart(edge, train, each.getValue(), this.placesCoveredAtStart.get(train))) lyingAcross.add(train);
        }

        for (Locomotive train : lyingAcross)
        {
            if (stillWhereItStarted(train, state)) return false;
        }

        return true;
    }

    /** Whether this train's tail, as it stood at the start of the plan, lies over metal this edge runs on (OB-184). */
    private static boolean liesAcrossAtStart(Edge edge, Locomotive train, Map<Edge, Locomotive> covered,
        Map<String, Locomotive> places)
    {
        if (places == null) places = java.util.Collections.emptyMap();

        if (covered.containsKey(edge)) return true;

        // And the track it shares metal with, where this actually bites (OB-184): the walk that works tails out stops
        // at a fork, and a train protruding across a SWITCH fouls every road through it.  `Layout.isPathClear` reads
        // that through `getLockEdges`, restricted to SYMMETRIC partners (the builder's one-directional travel
        // restrictions live in the same collection and must not be swept); the planner asks the same way or it offers
        // plans the runtime refuses.
        for (Edge sharing : edge.getLockEdges())
        {
            if (!covered.containsKey(sharing)) continue;

            if (!sharing.getLockEdges().contains(edge)) continue;

            // Only over the part of the edge the train is lying on (OB-207), as the runtime narrows it, else the
            // planner would declare impossible what the railway would carry out.  `Layout.tailLiesOn`, with the same
            // fallback: both sides must be described in places or nothing is narrowed (a pre-3.0.0 configuration keeps
            // the whole-edge answer).
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
     * Whether a train is still on the station it started at, so its tail is where it was too (OB-184).  A train
     * covering track without a starting station has nothing to compare and counts as moved.
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

        // The rule the runtime enforces: Edge.isOccupied is true when the BLOCK the edge leads into holds someone else.
        // This planner models occupancy per Point and the shared-sensor rule below catches the same pairs, because
        // every copy of a square carries its s88; the two agree by coincidence, not construction.
        Locomotive occupant = state.get(p);

        if (occupant != null && !occupant.equals(loc)) return false;

        if (p.getS88() != null && blocked.contains(p.getS88())) return false;

        // Two ACTIVE points reporting one sensor are one detection section, so they cannot both hold a train.  Blocking
        // the address itself would apply only to a sensor READING occupied at the snapshot; on quiet feedback the
        // planner would park two trains on one section.
        if (p.getS88() != null && this.pointsBySensor.containsKey(p.getS88()))
        {
            for (Point sibling : this.pointsBySensor.get(p.getS88()))
            {
                // Not gated on the sibling being active: a train parked on a deactivated siding holds the sensor as
                // hard as one on a live platform, and snapshot records occupants of inactive points.
                if (sibling.equals(p)) continue;

                Locomotive there = state.get(sibling);

                // The MOVER is not exempt either: isPathClear never looks at the point a path STARTS from but checks
                // the end of every edge against the live feedback, exempting nobody.  While the train still stands on
                // this section the feedback really is set (on latching detection; pulsed feedback clears behind the
                // train), so a leg into another point reporting that sensor would be planned and then refused at
                // execution.
                if (there != null) return false;
            }
        }

        // The two exclusion lists mean different things.  On a NON-station, exclusion means the locomotive may not
        // pass: the collision constraint, enforced here.  On a station it means it may not STOP there, which canRest
        // enforces; driving through is allowed, since a station on a through route is where an operator puts "not this
        // one, not here" (the stricter reading removed 45% of the reachable station pairs on the author's layout).
        // Asked of the square, as isPathClear asks it.
        return !p.excludes(loc) || this.layout.isAStationSquare(p);
    }

    /**
     * Sensors reading occupied that no locomotive on the graph accounts for.
     *
     * Something is sitting on that track which the graph does not know about, and no move of ours will clear it.
     *
     * A sensor a KNOWN train is standing on is not blocked here: that train can move, and the mutual exclusion in
     * canEnter closes the section behind it, which makes the rule structural, not a function of whatever the feedback
     * read a moment ago.
     *
     * A sensor a standing train's TAIL is lying on goes with that train (Adam: "Yes, tails hold sensors."): the section
     * behind a long train reads occupied with nothing standing on any square reporting it, and `Layout.isPathClear`
     * refuses an edge whose end reports a set sensor (OB-073).  Once the plan has moved the train the section is free,
     * which is why this reads the STATE.
     */
    private Set<String> blockedSensors(Map<Point, Locomotive> state)
    {
        Set<String> out = new HashSet<>();

        // PER CAUSE AND PER END, not per sensor (OB-073: the planner must never be looser than the railway).
        // `edgesCoveredByStandingTrains` claims an edge whole or not at all (`placesCoveredByStandingTrains` narrows
        // it), and one boolean per sensor would let a departed tail free a sensor a second, unmoved tail still lies on.
        // So a sensor is freed only when every tail accounting for it has gone, and a tail accounts for the end of an
        // edge only where the places say it reached that end.
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

                // Only the end the tail actually reached: `Layout.tailLiesOn` answers for the whole edge, which would
                // free the far point of a long edge too.  The place adjacent to each end is the first for the start and
                // the last for the end, in path order.  With no places described, the edge is claimed whole.
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
     * Whether this station could ever be a home for this locomotive: the planner's own question, delegated so the
     * answer here matches the one Return Home will give later.
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
     * Why this square cannot be a home, or null when it can.  Split out of {@link #canBeHome} so the refusal can say
     * why: a grayed item with no explanation is what OB-050 was about.
     *
     * @param loc the locomotive being assigned
     * @param at the square
     * @return a message key, or null when the assignment is fine
     */
    public static String whyNotAHome(Locomotive loc, Point at)
    {
        // A split square is an ordinary home: the home is the SQUARE (`Point.isSamePlaceAs`, MT-165), so several graph
        // Points are not refused.  Usability is asked of the whole square too: a platform whose copies are a turning
        // berth and a through road suits a train that cannot reverse, and which copy carries the home is the builder's
        // choice, not the operator's.
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
     * Whether this train could make any move from where it stands with every other train taken off (OB-325): the
     * search's own move, `firstClearRoute` to each station it would try, asked of an arrangement holding this train
     * alone.  Every rule only takes moves away when another train is present, so no move here means no move in any
     * reachable arrangement, and `plan` may name the train as a proof.
     *
     * Looser than the search on purpose: the rules applied after a route is found are not asked, so this may say "it
     * can move" where the search would not, never the other way.  `plan` asks twice, reading the sensors and then with
     * none read, so a train held only by a sensor nothing on the graph accounts for is not named.
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
     * Why one locomotive cannot get home, one sentence per rule it breaks (FR-078): the square it stands on, the home
     * itself, or the way between, each named as the square it is.  A home rule is reported only when no copy a train
     * can STOP at would take the train.
     */
    private List<String> whyNotHome(Locomotive loc, Point from, Point home)
    {
        List<String> out = new ArrayList<>();

        if (!from.isActive())
        {
            out.add(I18n.f("autolayout.whyHomeStartOutOfService", Layout.placeNameOf(from)));
        }

        // A placement turns the train round, so "place it on a station first" would send the operator back to the same
        // square: say what to do instead.
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

        // Only the copies a train can stop at: `validateTrainLength` answers yes for a non-destination copy, so a
        // barred copy would answer the length question and the length sentence would never be said.
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

        // THE ROUTE, asked only when the home itself would take the train: "no way there" about a
        // square the train could not stand on anyway is a second sentence about the first problem.
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
     * What can be seen to stand in the way when the search found no arrangement (FR-078).  NO_PLAN_FOUND proves
     * nothing, so this says only what is true of the railway as it stands: which homes another train is standing on.
     *
     * @return sentences keyed by locomotive; one with nothing visible in its way gets the plain statement that no
     *     arrangement was found
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

        if (home.getBlock() == null || home.getLayout() == null)
        {
            return canRest(loc, home) && connected(from, home);
        }

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
     * `Point.Occupancy` that reads the PLAN.  It consults three things:
     *
     *  - the square itself;
     *  - its other copies, by BLOCK, as the runtime's `getBlockLocomotive` does (OB-073: a plan the railway refuses);
     *  - the other points reporting the same SENSOR, which the runtime does NOT consult.  The planner is right that two
     *    active points on one feedback cannot both hold a train, but a sensor is not a square (AutonomyBuilder), so on
     *    such a layout it refuses arrivals the runtime would allow.  That fails safe and is left in force on purpose:
     *    dropping it changes which stations staging offers, which is Adam's decision.  Nothing pins this third half: a
     *    test needs a fixture where only the FR-001 relation joins the watched square to the destination.
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
        // a train is parked (EN57-947 could not be homed at TunnelLeftPark).  Whether it can GET there is a route
        // question (a path past a reversing point turns it, so it backs in), asked by Layout.isPathClear; with no path
        // to ask about, a guard here could only guess.

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

                // A terminus is traveled through here by stopping at it: `firstClearRoute` builds ONE path and will not
                // expand one, but `plan()` ends a move at any station and starts the next from there, so stopping and
                // setting off again is an ordinary two-move plan.  Only a terminus a move could END at (a destination
                // in service) counts; going through any other would invent a move the planner cannot make.
                //
                // Still tighter than the search: an out-of-service or non-destination terminus is not expanded here,
                // while the search will end a leg at one, so a graph whose only route home passes through such a square
                // is proved IMPOSSIBLE although a two-move plan exists.  Not reachable on a builder-derived graph; the
                // safe direction to widen is this one.
                if (!next.isTerminus() || (next.isDestination() && next.isActive()))
                {
                    queue.add(next);
                }
            }
        }

        return false;
    }

    // State helpers

    /**
     * Whether two points are one detection section: both active, and reporting the same sensor.
     *
     * It asks the SENSOR, not the block, and keeping it is Adam's decision (MT-187): `canEnter` enforces the sensor
     * rule structurally, so the planner never produces an arrangement with both occupied and "no arrangement exists"
     * is true of every one it can reach.  Asking the block would turn an instant IMPOSSIBLE naming both locomotives
     * into a search that burns its whole budget and answers "maybe" on a layout with shared sensors.
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
        // with the same arrival side: that includes the home copy's turning twin, the train turned round again.
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
            if (this.layout.bfs(from, to, null) != null) return false;

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
     * true number, so the plan found stays one of the fewest.  Three counts, none counted twice:
     *
     *  - every homed train away from home needs one (`misplaced`);
     *  - every homeless train standing on the home of a train still away must move once;
     *  - every closed ring of trains each standing on the next one's home needs one more, since the first to move
     *    cannot go home.  Rings never share a member.
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
