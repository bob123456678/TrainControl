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
     * How heavily the second half of the search weighs the moves still needed against the moves made (OB-230).
     *
     * At 1 the search finds the shortest plan and, on a crowded railway, runs out of time finding it.  Weighted, it
     * heads for arrangements with less left to do and may return a plan longer than the shortest, by at most this
     * factor, since the estimate never overstates.  5 solves the six-train arrangement of
     * `core.testReturnHomeFindsAPlanOnAFullRailway` where 2 did not in fifteen seconds.
     */
    private static final int ANY_PLAN_WEIGHT = 5;

    /**
     * Wall clock, because a state count cannot bound the time this takes.
     *
     * Expanding one state runs firstClearRoute once per locomotive per station, each a breadth-first search over the
     * graph: milliseconds per state on a 62-point layout.  An arrangement with no solution must say NO_PLAN_FOUND ("may
     * still be possible") within this budget, not freeze the application.
     *
     * **One budget for the whole search, shared out** (OB-230).  A third looks for the SHORTEST plan
     * from where the greedy pass stopped, a third for the shortest from the railway as it stands (the retry OB-228
     * needs, for a train the greedy pass parked with no way out), and the last third for ANY plan from the railway as
     * it stands, with the estimate weighted (`ANY_PLAN_WEIGHT`).  Halves, shortest then any, when the greedy pass moved
     * nothing.  The last share finds a plan on a crowded railway where the shortest cannot be found in time
     * (`core.testReturnHomeFindsAPlanOnAFullRailway`).
     */
    private static final long SEARCH_BUDGET_MS = 15000;

    /** When the search in progress started - set once per `search`, so every `astar` it runs is timed from it. */
    private long searchStarted;

    /**
     * Where the search reads the time.
     *
     * The wall clock, always, on the railway.  A field rather than a call so a test can step it: how the budget is
     * shared out between the searches is a rule about time, and a claim on the real clock would spend fifteen
     * seconds a run.  `core.testReturnHomeKeepsClearOfTheTailsItLeaves` sets it.
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
     * The track the trains were lying across when the snapshot was taken, and who is lying across it.
     *
     * Adam, OB-184: **"The home planner does not consider blocks due to the length of a train, i.e. a
     * train that blocks edges behind it."**
     *
     * A train longer than the track it stands on protrudes onto the track BEHIND it, which belongs to no sensor at all.
     * `Layout.isPathClear` consults it, so the planner must too, or it routes a train through a switch another train is
     * lying across and the runtime refuses the move.
     *
     * **Only for a train that has not moved yet, and that is a real limit rather than laziness.**  Where a train's tail
     * lies after a move depends on the side it arrives by, which nothing here models.  So a train still standing where
     * it started blocks what it really blocks, and one the plan has already moved blocks nothing extra.  That
     * under-claims, the safe direction: the runtime refuses what it must, and a planner that over-refused would report
     * a railway as impossible that is not.
     */
    private final Map<Locomotive, Map<Edge, Locomotive>> coveredAtStart;

    /** The same, by place rather than by edge - what the shared-metal refusal is narrowed with. */
    private final Map<Locomotive, Map<String, Locomotive>> placesCoveredAtStart;

    /**
     * The route each train the search has moved last took, for the arrangement being expanded (OB-228, MT-335).
     *
     * `coveredAtStart` is the railway before the plan; a train the plan has moved lies along the route it was given,
     * and this says which route that was.  Set before every expansion: the greedy pass keeps it as it goes, A* rebuilds
     * it from the chain of moves that reached the arrangement.
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
        // railway that has moved on, differently on every expansion.
        //
        // At both grains: the edges a standing train's tail touches, and the pieces of metal it lies on, which
        // the runtime narrows its shared-metal refusal with.
        //
        // One train at a time: the railway's single map holds one train per place, so two tails fouling one
        // switch from its two legs were recorded as one. tails fouling one switch from its two legs were recorded as
        // one - and the train recorded, moving, hid the other.
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
                // feedback is pulsed and clears again behind the train), and a bypass and its platform routinely report
                // the same sensor.
                if (layout.isFeedbackOccupied(p.getS88())) sensorsSet.add(p.getS88());
            }
        }

        // Launch pads: stations with zero incoming edges.  Some layouts stage trains on one-way tracks (departure edges
        // only) and re-stage by hand.  The topology is the declaration of intent: a positional home on one is not a
        // home once left, and the search never moves a locomotive off one (both below).
        Set<String> launchPads = new HashSet<>();

        for (Point p : layout.getPoints())
        {
            if (layout.getIncomingEdges(p).isEmpty()) launchPads.add(p.getName());
        }

        // A POSITIONAL home on a launch pad stops being a home once the locomotive has LEFT it: nothing can re-enter
        // the pad, and a plan is all-or-nothing, so keeping the entry would make Return Home refuse to move ANYTHING.
        // Still standing there, the claim is satisfied, and dropping it would turn ALREADY_HOME into NO_HOMES.  An
        // ASSIGNED home keeps the strict contract either way: an unreachable one answers IMPOSSIBLE with the locomotive
        // named.
        Map<Locomotive, Point> homes = new LinkedHashMap<>(layout.getHomeStations());

        for (Iterator<Map.Entry<Locomotive, Point>> it = homes.entrySet().iterator(); it.hasNext();)
        {
            Map.Entry<Locomotive, Point> entry = it.next();

            // Identity, not a name: the home is a Locomotive, and a name comparison (String.equals takes an Object)
            // would answer false for ever and treat every assigned home as positional.
            boolean assigned = entry.getKey().equals(entry.getValue().getHomeLoc());
            // The SQUARE, not the Point (MT-165): `occupancy.get(home)` names one copy of a platform, so a train on the
            // other copy of its own home would read as not standing on it.
            boolean standingOnIt = atHome(entry.getValue(), locationOf(occupancy, entry.getKey()));

            if (!assigned && !standingOnIt && launchPads.contains(entry.getValue().getName()))
            {
                it.remove();
            }
        }

        // A PAUSED TRAIN STAYS WHERE IT STANDS (FR-117; kept out of "Autonomy and Return Home").  For this plan its
        // home is where it stands: it is home already, nothing is planned for it, the check after a run counts it home,
        // and the search never moves it out of another's way (`paused`).  One away from a home of its own is named in
        // the log (`getPausedAwayFromHome`).
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
         * Why each locomotive could not be brought home, in words, or empty when nothing was found
         * wrong with any one of them (FR-078).  See `getReasons`.
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
         * Why each locomotive the plan could not bring home was refused, one sentence per fact found.
         *
         * Adam, FR-078: *"if a return home plan fails, state the reason why the layout doesn't allow a locomotive to go
         * to its home in the log (length, blocked, etc.)"*.  The outcome names the trains; this names the rule each one
         * fell foul of: a length, a switch, a train in the way.
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

        // A locomotive in two places at once, which nothing below this line can reason about.
        //
        // A locked path reserves every point along it for one locomotive, and a path that failed part-way through
        // unlocking leaves those reservations standing.  Nothing in the model tells a reservation from a train
        // (reserve() and setLocomotive() write the same field), so `misplaced` would count one train twice and the
        // answer would be NO_PLAN_FOUND with no moves.  Counting it properly would be the wrong fix: the plan would
        // depart from whichever point locationOf yields first, driving a real train from a place it is not standing.
        // NO_PLAN_FOUND claims less than it could and nothing false; a guessed origin may claim something false.
        //
        // So it is reported with the locomotive's name, and the remedy is the ordinary one: place the train on the
        // square it is actually on, which sweeps the rest.
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

        // A locomotive with no route to its home at all cannot be helped by moving anything else, and it is the only
        // impossibility this class can prove.  Locomotives already on their home are skipped: a route from a point back
        // to itself is a different question, and on a layout without a loop the answer is "none", which would report a
        // graph in order as impossible.
        List<Locomotive> unreachable = new ArrayList<>();

        // One sentence per fact, per locomotive, for the log (FR-078).
        Map<Locomotive, List<String>> reasons = new java.util.LinkedHashMap<>();

        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || atHome(home, locationOf(this.start, l))) continue;

            // A home the locomotive could never be parked at (inactive, excluding it, too short, or a terminus it
            // cannot reverse out of), or an origin it cannot leave (an inactive one), is impossible by construction.
            //
            // The STATELESS canRest and no FR-001 test: this pre-scan reads the STARTING occupancy, so
            // a state-aware canRest (OB-073) would let a locomotive merely standing on a watched square prove the goal
            // unreachable, including one whose departure is the plan's own first move.  FR-001 is asked at the arrival
            // test in `firstClearRoute`.
            //
            // THE SQUARE: `canRest` and `connected` are asked of the same copy of the home, and a terminus turns a
            // train round as it ARRIVES, so only the copies that turn are closed to a locomotive that cannot reverse.
            //
            // THE INACTIVE START IS REFUSED HERE, DELIBERATELY.  A hand-driven send allows one (Adam:
            // "inactive really means nothing can pass", with the square a train is ALREADY standing on exempted), but
            // Return Home is a plan made for every staged locomotive at once, and a square switched off is a square its
            // author has said to leave alone.
            if (!locationOf(this.start, l).isActive()
                || !locationOf(this.start, l).isDestination()
                || !canGetHome(l, locationOf(this.start, l), home))
            {
                unreachable.add(l);

                // AND WHY (FR-078): asked of the same three conditions as the line above, so the sentence cannot
                // describe a rule other than the one that refused.
                reasons.put(l, whyNotHome(l, locationOf(this.start, l), home));
            }
        }

        // NOR ONE THAT CANNOT LEAVE WHERE IT STANDS (OB-325).  A reversible train of 4 on BottomMainB's
        // turning copy, from which every station reachable is shorter, has its home connected by track, so the scan
        // above passes it, yet it has no first move: the search would run the whole fleet for about fifteen seconds and
        // name nobody.  A train with no first move and not at home can never get home, so that is a proof, said with
        // the train's name.
        //
        // Asked with every other train taken off (`hasAFirstMove`), which makes it a proof rather than a guess: another
        // train only ever takes moves away, so a train with no move on an empty railway has none on any.
        for (Locomotive l : this.start.values())
        {
            Point home = this.homes.get(l);

            if (home == null || unreachable.contains(l)) continue;

            Point from = locationOf(this.start, l);

            if (atHome(home, from) || this.hasAFirstMove(l, from, true)) continue;

            // NOT WHERE A SENSOR IS ALL THAT HOLDS IT.  A sensor that reads occupied with no train on the
            // graph to account for it (a vehicle TrainControl does not know, a dirty contact, a tail past where the
            // walk stops) blocks every arrangement alike, so it would pass for a proof; but it is live state, and goes
            // when somebody clears it.  A train with a move once no sensor is read is left to the search, which
            // answers: nothing found, it may still be possible, try moving something by hand.
            if (this.hasAFirstMove(l, from, false)) continue;

            unreachable.add(l);

            reason(reasons, l, I18n.f("autolayout.whyHomeCannotLeave", Layout.placeNameOf(from)));
        }

        // Goals that conflict with each other, which no arrangement can satisfy either.  Two homes on one detection
        // section is the easiest wrong click on a layout that shares addresses (a platform and its bypass), and nothing
        // warns when the assignment is made, because canBeHome is a one-station question.  Provable in a pairwise scan,
        // so the search need not spend its whole budget to say "maybe".
        for (Map.Entry<Locomotive, Point> a : this.homes.entrySet())
        {
            if (!this.start.containsValue(a.getKey())) continue;

            for (Map.Entry<Locomotive, Point> b : this.homes.entrySet())
            {
                if (a.getKey().equals(b.getKey()) || !this.start.containsValue(b.getKey())) continue;

                if (!sharesSection(a.getValue(), b.getValue())) continue;

                // Both already parked: nothing arrives, so nothing is checked.  What is proved here is that the
                // SECOND arrival onto a shared detection section is refused, and neither of these trains arrives: both
                // stand on their own homes, so the pair is finished before the run begins.  Leaving the exemption out
                // costs no plan, not a worse one: IMPOSSIBLE refuses the WHOLE staging run, so a third locomotive that
                // only needed driving to the next platform never moves, and the two names handed to the operator are
                // trains already where they belong.  Only one of the two has to be away for the proof to hold again
                // (testTwoActivePointsSharingASensorAreNeverBothOccupied).
                //
                // Not reachable on a layout from the 3.0.0 diagram: no two DIFFERENT station squares share a sensor
                // (every shared-sensor group is one square emitted per arrival side), and a home on such a square is
                // refused outright by whyNotAHome.  The shape is ordinary elsewhere: AutonomyBuilder is explicit that a
                // station, its approach guard and a reversing point can be three Points on one feedback.
                if (atHome(a.getValue(), locationOf(this.start, a.getKey()))
                    && atHome(b.getValue(), locationOf(this.start, b.getKey()))) continue;

                if (!unreachable.contains(a.getKey())) unreachable.add(a.getKey());
                if (!unreachable.contains(b.getKey())) unreachable.add(b.getKey());

                reason(reasons, a.getKey(), I18n.f("autolayout.whyHomeSharesASection",
                    Layout.placeNameOf(a.getValue()), b.getKey().getName(), Layout.placeNameOf(b.getValue())));
            }
        }

        // AND NO CYCLE SCAN OVER THE OCCUPANCY RESTRICTIONS (OB-085).  Two homes that each hold back the square
        // the other's occupant must end on are a real deadlock now that the planner enforces FR-001, and the scan stays
        // out anyway: IMPOSSIBLE names locomotives and asserts that no arrangement exists, and the scan that produced
        // that verdict was wrong three times, each time about a railway that works.  A search that exhausts and answers
        // NO_PLAN_FOUND is the weaker claim and the true one.  `docs/reference/behaviour.md` section 1 records that as
        // Adam's own choice, and `testHomeStaging.testTwoHomesThatHoldEachOtherBackAreADeadlock` pins it.
        //
        // The pairwise scan above stays: it is about two homes on ONE DETECTION SECTION, a fact about the track that no
        // ruling about tiers touches.

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
     * right now.
     *
     * The planner answers that question for hypothetical futures, so it cannot call isPathClear (which reads live
     * feedback) and re-implements the rules.  Re-implementing a specification is only safe if you check it against the
     * original, and this is that check: for the ONE state where both can be asked, the present, the two answers must
     * match.
     *
     * Logged rather than enforced.  A divergence is a defect in this class, not a reason to refuse the operator a
     * staging run, and the runtime re-validates every move before driving it anyway.
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
                // A DESTINATION ONLY A LAP REACHES IS THE FOURTH CORRECT DIVERGENCE, and it belongs on THIS side.
                // `Layout.bfs` refuses a route that doubles back through another copy of the square it starts or ends
                // at, so the menu (this oracle) offers no such destination.  The planner does, because Adam ruled the
                // copy rule onto the **menu and autonomy only** (applying it here cost Return Home plans it used to
                // find), and the RUNTIME agrees with the planner: `isPathClear` exempts an intermediate copy of the
                // train's own square as occupied by that train.
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
     * Separated so a caller can find out cheaply, before asking the user a question it would be rude to ask when the
     * answer is "nothing needed" (whether to replace the timetable matters only if a run is going to happen).  It reads
     * the occupancy and nothing more: no route is enumerated and nothing is searched.  plan() delegates to it, so the
     * two can never disagree about whether there is work.
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

        // NOT ONLY FROM WHERE THE GREEDY PASS LEFT THINGS (OB-228).  That pass makes every move it can, in the order it
        // meets the trains, and with the tails of moved trains modeled a move it makes can block the rest: it parks a
        // train on a platform with no way out and leaves the tail across the run another train needs, and no search
        // from there can take it back.  So when nothing is found from the greedy arrangement, search again from the
        // railway as it stands.
        this.greedyMovedAlong = new java.util.HashMap<>();

        if (retry)
        {
            List<Move> fromTheStart = astar(new LinkedHashMap<>(this.start), this.searchStarted + 2 * share, 1);

            if (fromTheStart != null) return fromTheStart;
        }

        // AND THEN FOR ANY PLAN, NOT THE SHORTEST (OB-230; Adam: *"the whole point of the A* is to figure out how to
        // rearrange other trains to park things when they belong"*).  On a crowded railway a search for the shortest
        // plan examines thousands of arrangements, runs out of time and finds nothing, while the weighted search finds
        // a longer plan that brings everybody home, which is what the button is for.  A shortest plan is still
        // preferred wherever it can be had, which is the shares before this.  Not in place of the retry above: on the
        // railway that retry exists for, the weighted search heads down sixty sidings and finds nothing in its time
        // (`core.testReturnHomeKeepsClearOfTheTailsItLeaves`).
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

        // THE ROUTES EACH ARRANGEMENT'S MOVED TRAINS CAME BY, kept with it.  The key is the squares plus those
        // routes: two orders reaching the same squares with different tails are different arrangements (MT-335's shape:
        // a train parked the short way lies across the run another needs; parked the long way, it does not).
        Map<String, Map<Locomotive, List<Edge>>> routesOf = new HashMap<>();
        Map<Locomotive, List<Edge>> startRoutes = new java.util.HashMap<>(this.greedyMovedAlong);

        String startKey = tailKey(from, startRoutes);

        routesOf.put(startKey, startRoutes);
        states.put(startKey, from);
        cost.put(startKey, 0);
        score.put(startKey, weight * movesStillNeeded(from));

        // Ordered on a precomputed f-score: computing it inside the comparator would rescan every locomotive on every
        // comparison.  Each entry carries the score it was queued with rather than reading the score map, because the
        // relaxation below re-scores a state when it finds a cheaper route to it, and that state may already be queued:
        // a PriorityQueue compares on demand, so rewriting the map would change an entry's priority in place and break
        // the heap invariant, and the search would spend its budget out of order (NO_PLAN_FOUND is a statement about
        // that budget).
        //
        // Ties are broken towards the goal (OB-230): of two entries with the same score, the one with fewer moves still
        // needed is further along, so the search goes deeper instead of spreading over every equally promising order.
        // The score still orders the queue, so a plan found is still one of the fewest moves.
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
                // no incoming edges, so a move off one can never be planner-undone: the hand-staging the pad represents
                // would be destroyed silently, as a side effect of someone else's plan.  Keyed on the pad and the home,
                // not on homelessness: a satisfied positional pad-home stays in the homes map (that is what makes a
                // staged layout report ALREADY_HOME).  One already dispatched from its pad is an ordinary free agent
                // wherever it now stands.
                Point ownHome = this.homes.get(l);

                // atHome: a train resting on the far copy of its own home square is resting at home, and pinning it to
                // the exact copy can ask the A* for a move that no arrival side allows.
                if (this.launchPads.contains(at.getName()) && (ownHome == null || atHome(ownHome, at)))
                {
                    continue;
                }

                // A TRAIN THAT CANNOT REVERSE IS TURNED ON THE WAY ONLY TO GO HOME (Adam: *"This should only be
                // allowed if the train is going to reverse into its berth on the next turn."*).  The run turns a train
                // at every terminus and reversing point a leg ends at (`ALWAYS_REVERSE`), so resting such a train on
                // one and then sending it anywhere but home would drive it on running the other way (OB-205).  A move
                // onto such a square is made only by a train with a home to go to, and a train the PLAN has put on one
                // moves next only to its home.
                //
                // CARRIED BY THE TRAIN, NOT READ OFF THE SQUARE: a plan could turn a train on its way into a
                // berth, which the ruling permits, and then take it out to an ordinary station, which it does not; the
                // pair breaks the rule though neither move does.  So what is asked is the route that brought the train
                // here: if it turned the train and the train is not home, its next move owes itself to its home.  And
                // ONLY OF A TRAIN THIS PLAN HAS MOVED: `turnsATrainArrivingAt(at)` asks about the SQUARE,
                // which cannot tell who turned the train standing on it, and a train the railway already had on a
                // terminus (or a free agent with no home) would be refused every square there is.  `movedAlong` knows:
                // a train this plan has moved has an entry there.
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

                    // NOR TURNED IN THE MIDDLE OF THE MOVE AND SENT ON.  The two rules above are about where a
                    // move ENDS.  A route may also turn the train at a reversing point on the way (how a train that
                    // cannot reverse backs into a berth, MT-245) and then carry on, so it arrives running the other way
                    // round.  A turn on the way is allowed only where this move ends somewhere the turn was FOR: the
                    // train's home, a berth, or a square it comes to rest facing out of.  Everywhere else it would be
                    // driven on backwards.
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

        // NOT ROUND A LOOP BACK ONTO THE SQUARE IT STANDS ON (Adam: *"These are the same point so we should never do a
        // round trip just to change direction"*).  Another copy of the train's own square is the same track, and the
        // right-click menu and autonomy never offer one (`Point.getBlockLocomotive` reads it as occupied by the train),
        // so a plan that used one would be the planner disagreeing with the railway.  A train on any copy of its home
        // is already home (`atHome`), so this never keeps a train from home.
        if (from.getBlock() != null && from.getBlock().equals(to.getBlock())) return null;

        // The origin is exempt from every other test here (that stops the moving train's own sensor blocking its own
        // departure) but not from these two.  isPathClear applies its inactive-point rule to every edge start including
        // the first, and staging executes with autonomy running, so a locomotive standing on a deactivated point would
        // be planned home and then refused at its first edge.
        if (!from.isActive()) return null;

        // And the rule in the very next `if`: "Starting point is not a station - do not pick it in fully
        // autonomous mode".  Staging is fully autonomous operation (executeTimetable sets `running`, which is why the
        // reversing-point exclusion was moved out of isPathClear and into selection).  Missing it costs more than a
        // refused plan: the run STARTS, the first leg is refused, and the retry loop asks every two seconds until it
        // abandons the run, when the track was clear and the train was parked somewhere no automatic path may begin, as
        // a hand-placed train is.
        if (!from.isDestination()) return null;
        // FR-001 at the arrival is asked by the search below, of every square it arrives at, `to` included, on the
        // PLANNED state.  The runtime enforces it in every tier (Adam: *"if it's cleaner to go with consistency across
        // the board, then let's enforce the occupancy ruling in all modes and then rely on isPathClear."*), and a plan
        // that offers a leg the railway then refuses is OB-073: the run retries until it gives up and stops with the
        // fleet half-staged.  `auditAgainstRuntime` proves the two agree.
        if (!canRest(loc, to) || state.containsKey(to)) return null;

        // Where the plan has put everybody, for the FR-001 question at every square on the way (OB-295).
        final Point.Occupancy planned = plannedOccupancy(state);

        // ITS OWN BODY AS IT STANDS HERE, for the own-tail question below (OB-294): the road this plan moved it along
        // where it has moved it, and what the railway records where it has not, the two bodies the runtime would walk.
        //
        // Walked after the cheap refusals, and once per train and road: it depends on neither the destination
        // nor the rest of the arrangement, and is asked once per destination inside a search with a time budget.
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
        // `turned` means only "a different way of getting here": nothing reads the flag as a rule (Return Home is
        // manual operation, so a terminus is not refused to a train that cannot reverse).  It survives in the visited
        // key below, where it keeps two routes to one square apart; collapsing the key would prune one of them and
        // change which route this search returns first.  (`connected` lost the same state, because it only answers yes
        // or no and pruning cannot change that.)
        //
        // NOT A TERMINUS.  `executePath` flips direction on arrival at a terminus and at a reversing
        // point alike, but this flag means "this train will BACK INTO the terminus it is ending at".  At a reversing
        // point the arrival flip leaves the train trailing, so it goes on backing: seeding true is right.  At a
        // TERMINUS that flip is the one that turns a backed-in train round to face out again, so it is spent and a
        // train leaving a terminus leaves FORWARDS; seeding true there counts the same flip twice and describes a
        // nose-first arrival as backing in.
        queue.add(new Candidate(from, new LinkedList<Edge>(),
            new HashMap<String, Accessory.accessorySetting>(), from.isReversing()));

        while (!queue.isEmpty() && expansions++ < ROUTE_SEARCH_LIMIT)
        {
            Candidate current = queue.poll();

            // BY NAME, NOT AS THE RAILWAY SHUFFLES THEM.  `Layout.getNeighbors` shuffles deliberately (that is
            // how autonomy spreads its choices over a railway), and this search returns the FIRST clear route it finds,
            // so a plan would ride on the shuffle: the same railway answering READY on one press and NO_PLAN_FOUND on
            // the next.  A capability claim that answers differently each time spends the battery's authority.  Ordered
            // here rather than at `getNeighbors`, which every tier shares: a planner wants a function of the track, a
            // run wants variety.
            List<Edge> outward = new java.util.ArrayList<>(this.layout.getNeighbors(current.at));

            java.util.Collections.sort(outward, (one, two) -> one.getName().compareTo(two.getName()));

            for (Edge e : outward)
            {
                Point next = e.getEnd();

                // THE COPY RULE IS NOT APPLIED HERE, and that is Adam's ruling rather than an omission.  `Layout.bfs`
                // refuses a route that passes another copy of the square it starts or ends at (*"a copy makes a
                // cycle"*), so the right-click menu and autonomy never offer a lap.  Applying it here cost Return Home
                // plans it used to find (`core.testTrainsComeHomeToTheirPlatforms`), so he chose **menu and autonomy
                // only**: the planner keeps the looser search, and a lap is available to it when that is the only way a
                // train can get home.  The runtime agrees: `Layout.isPathClear` treats an intermediate copy of the
                // train's own square as occupied by that train and exempts it.  `auditAgainstRuntime` makes the
                // matching exemption.  Whether the train has been turned round by the time it stands here.  Part of the
                // STATE, not of the square - see Candidate.turned.
                boolean turned = current.turned || next.isReversing();

                if (!canEnter(next, loc, blocked, state)) continue;

                // AND NOT THROUGH A SQUARE HELD BACK BY AN OCCUPIED ONE (OB-295).  `isPathClear` asks FR-001 of every
                // square a route arrives at (*"trains shouldn't be sent to THIS square while trains are STANDING ON ...
                // the other specified station(s)"*), so a plan through one is OB-073's plan, refused on the move.
                // Asked of the planned state; the destination's own FR-001 test, since a route reaches `to` only here.
                if (Point.heldBackBy(next, loc, planned) != null) continue;

                // AND THE TRACK A STANDING TRAIN IS LYING ACROSS (OB-184).  `canEnter` asks about the POINT this edge
                // leads to; this asks about the EDGE, because a train longer than its berth protrudes onto track that
                // belongs to no sensor and no point reports.  `Layout.isPathClear` enforces it, and a plan that ignores
                // it is refused on the first move.
                if (!passesTheTailsOfTrainsThatHaveNotMoved(e, loc, state)) continue;
                if (!passesTheTailsOfTrainsItHasMoved(e, loc, state)) continue;

                // Lock edges are deliberately NOT consulted here.  The runtime does not count an edge as occupied when
                // the point it leads to holds another locomotive (a lock edge holds a shared throat clear, and a train
                // at the point one leads to is not on that throat), so asking here would make the planner the stricter
                // of the two: it would refuse arrangements the runtime would drive, and report NO_PLAN_FOUND after its
                // whole search budget.  Nothing replaces it, because a staging plan runs ONE TRAIN AT A TIME (see
                // Layout's staging flag): the runtime rule is "is another route holding this track", and during a
                // staging move no other route is running.  A check that cannot fire invites a future reader to make it
                // fire.  auditAgainstRuntime proves the two halves agree.

                Map<String, Accessory.accessorySetting> commands = withCommandsOf(e, current.commands);

                // Two edges asking one accessory for opposite settings
                if (commands == null) continue;

                // KEYED BY THE SITUATION, not by the square: the same point reached with a reversal behind it and
                // without one are two states, and collapsing them means the first arrival (usually the short way, which
                // does not turn the train) closes off the only route that could have backed it in.
                List<Edge> route = new LinkedList<>(current.route);
                route.add(e);

                // AND NOT ROUND A LOOP INTO ITS OWN TAIL (OB-294), as `Layout.isPathClear` asks it: the one rule, not a
                // copy.  Prefix-closed, like the room rule below: every return is asked and the tightest named,
                // and an extension keeps every return the route already has, so no extension of a route that
                // meets the tail can clear it, and pruning here loses nothing.
                if (Layout.whyItWouldMeetItsOwnTail(route, loc, ownBody) != null) continue;

                // ROOM IS ASKED BEFORE THE ARRIVAL IS RECORDED.  The refusal below is a
                // `continue` because another route to the same berth may be longer and a longer approach is more room;
                // but `alreadyReached` is a DOMINANCE test, so a short arrival recorded first would dominate the longer
                // one and drop it before its room was measured.  An arrival refused for want of room is not a state
                // that has been reached.  A longer route can still be pruned at a shared square EARLIER in the path
                // (path length is in neither the key nor the dominance relation), but a genuinely different approach
                // sets some switch the other way and survives.
                //
                // WHEREVER THE RUNTIME ASKS, AND NOWHERE ELSE (see `Layout.whyTooLongForThisRoute`): fewer squares than
                // the runtime asks would have the first move refused, more would drop plans that would have run, so
                // this asks the destination and a square the train turns at, and the berth is judged on the whole route
                // behind it (MT-333).  A square the train only passes is not asked at all.  Pruning is sound as well as
                // cheap: the property is prefix-closed, so no extension of a route the train does not fit on can fit.
                if (next.equals(to))
                {
                    // AT THE DESTINATION, THE BERTH RULE AS WELL AS THE ROOM.  `Layout.whyABerthCannotHoldIt`
                    // is the static method `isPathClear` asks, so the two cannot come to disagree (the pattern
                    // `Point.heldBackBy` sets for FR-001); without it a plan sends a long train into a parking berth
                    // the runtime then refuses at the first move (OB-073).
                    //
                    // **AND THE PLATFORM RELAXATION.**  The room is asked as `measuredRoomAtTheEndOf` rather than as
                    // the whole of `whyTooLongForThisRoute`, which also carries the station's stated capacity and a
                    // room test at every square the route runs through: substituting it made this planner STRICTER than
                    // the runtime (`core.testTrainsComeHomeToTheirPlatforms` came back NO_PLAN_FOUND for all five
                    // trains).  A station autonomy may choose takes a train its measured route in holds (FR-087), which
                    // the runtime allows; a planner stricter than the railway silently drops plans that would have run.
                    // So the relaxation lives in `theApproachItselfHoldsIt` and both sides ask it: one question, two
                    // callers.
                    Integer room = Layout.measuredRoomAtTheEndOf(route, loc);

                    if (room != null && loc.getTrainLength() > room
                        && !Layout.theApproachItselfHoldsIt(route, loc))
                    {
                        continue;
                    }

                    // THE STATION'S STATED CAPACITY IS NOT ASKED HERE, AND DOES NOT NEED TO BE: `canRest` ends with
                    // `at.validateTrainLength(loc)`, so a home the operator has limited below the train's length is
                    // refused before any route to it is searched.  A second spelling of a rule is this project's most
                    // repeated defect, and one that agrees today is the kind that stops agreeing later.
                    // `core.testHomeStaging.testThePlannerRefusesAHomeTheOperatorSaidIsTooShort` pins the behavior
                    // wherever it is enforced from.

                    if (Layout.whyABerthCannotHoldIt(route, loc) != null) continue;
                }
                else if (next.isReversing())
                {
                    // ONLY A SQUARE THE TRAIN TURNS AT, as the runtime judges it (MT-333): a square it only passes is
                    // not asked about at all, since it does not stand there, and a turning square is asked on the same
                    // walk the runtime uses for it.
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
                    // NO TURN IS REQUIRED HERE (Adam: *"Return home is manual, take the rule out and say why."*).  The
                    // reason is the same as his rule for `isPathClear`: *"in manual operation, non reversing trains
                    // must be able to back into a terminus if the graph makes that possible"*.  Pressing Return Home IS
                    // the operator asking for it, which is what makes it manual.  `pickPath` keeps the rule, and
                    // should: autonomy chooses destinations nobody asked for, and backing a train into a dead end
                    // unasked is a different thing from doing it because somebody pressed the button.
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
     * A path sets every switch and signal it needs before the train departs, so one accessory cannot be asked for two
     * different settings on the same route: isPathClear builds exactly this map and refuses the path when an entry
     * would be overwritten.  Where a platform and its approach both drive the same signal, the shortest route is
     * routinely one of these.
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
     * Whether this edge is free of the tails the plan's own moves leave behind (OB-228, MT-335).
     *
     * A train the plan moved stands at the end of the route it was given, has come in by that route's last rail and
     * along that route, and `Layout.edgesATailWouldCover` walks that tail with the runtime's own code.
     * (`passesTheTailsOfTrainsThatHaveNotMoved` cannot see it: where a train's tail lies after a move depends on the
     * side it arrives by.)
     *
     * The same shared-metal and places rules as the unmoved trains, and the same exception: a train never blocks
     * itself.  Only a train still standing where that route left it counts; one moved again since has a later route
     * here.
     *
     * @param edge the edge being entered
     * @param mover the locomotive being routed
     * @param state the arrangement being considered
     * @return true when no train the plan has moved would be lying across it
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
     * A train never blocks ITSELF: its own tail is behind it by definition, and pulling forward off it is the ordinary
     * way a train leaves a berth.  `Layout.isPathClear` says the same, and the two rules have to agree or the planner
     * offers what the runtime refuses.
     *
     * **The "has not moved" test is what keeps this sound.**  `coveredAtStart` describes the railway as it was.  Once
     * the plan has moved a train, that record is about track it has left, and blocking on it would refuse routes that
     * are genuinely clear (Adam's standing preference is no check over an over-strict one).  Where the train has not
     * moved, the record is exactly true.
     *
     * **EVERY TRAIN LYING ACROSS IT, not the first found**.  An edge two start tails lie across must not pass
     * as soon as the first-found train has gone while the second still lies there: the runtime walks the tails as they
     * are now and would refuse the leg, a plan that stops half way (OB-073).  The runtime may stop at the first,
     * because any one refuses; this has to ask them all.
     *
     * @param edge the edge being entered
     * @param mover the locomotive being routed
     * @param state the arrangement being considered
     * @return true when nothing standing is lying across it
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

    /**
     * Whether this train's tail, as it stood at the start of the plan, lies over metal this edge runs on (OB-184).
     *
     * @param edge the edge being entered
     * @param train one train standing at the start
     * @param covered the edges its tail covers, walked alone
     * @param places the places its tail claims, walked alone
     * @return true when it lies there
     */
    private static boolean liesAcrossAtStart(Edge edge, Locomotive train, Map<Edge, Locomotive> covered,
        Map<String, Locomotive> places)
    {
        if (places == null) places = java.util.Collections.emptyMap();

        if (covered.containsKey(edge)) return true;

        // AND THE TRACK IT SHARES METAL WITH, which is where this actually bites (OB-184).  A tail lying directly on
        // the edge being entered is the easy half and the rarer one: the walk that works tails out STOPS at a fork, so
        // a tail only extends along a linear stretch.  The other half is a train protruding across a SWITCH: the
        // switch's tiles belong to every road through it, so the tail fouls roads that are not the edge it lies on.
        // `Layout.isPathClear` reads that through `getLockEdges`, restricted to SYMMETRIC partners (sharing a tile is
        // mutual, while the builder's one-directional travel restrictions live in the same collection and must not be
        // swept).  The same rule asked the same way, because a planner that asks a different question offers
        // plans the runtime refuses.
        for (Edge sharing : edge.getLockEdges())
        {
            if (!covered.containsKey(sharing)) continue;

            if (!sharing.getLockEdges().contains(edge)) continue;

            // AND ONLY OVER THE PART OF IT THE TRAIN IS ACTUALLY LYING ON (OB-207).  The runtime narrows this
            // to places, and a planner that did not follow would declare impossible what the railway would carry out
            // (`Tunnel -> BottomMainA` past a one-unit tail twelve tiles away): a proof may be looser than the search
            // it guards, never tighter.  `Layout.tailLiesOn` rather than a copy of it (the pattern `Point.heldBackBy`
            // sets for FR-001), with the same fallback: both sides must be described in places or nothing is narrowed,
            // so a configuration written before 3.0.0 keeps the whole-edge answer.
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
     * Whether a train is still on the station it started at, so its tail is where it was too (OB-184).
     *
     * A train covering track without a starting station of its own has nothing to compare, and counts as moved: nothing
     * is claimed for it.
     *
     * @param train the train
     * @param state the arrangement being considered
     * @return true while it has not moved
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

        // The rule the runtime enforces: Edge.isOccupied is true when the BLOCK the edge leads into holds someone else,
        // the point itself or another copy of the same square (a square is emitted as one Point per arrival side, and
        // they are one piece of track).  This planner models occupancy per Point, and the shared-sensor rule below
        // catches the same pairs because every copy of a square carries that square's s88.  The two agree by
        // coincidence rather than by construction, which is worth knowing before either rule is changed.
        Locomotive occupant = state.get(p);

        if (occupant != null && !occupant.equals(loc)) return false;

        if (p.getS88() != null && blocked.contains(p.getS88())) return false;

        // Two ACTIVE points reporting one sensor are a single detection section, so they cannot both hold a train.
        // This is the whole of the shared-address rule: blocking the address itself would apply only to a sensor
        // READING occupied at the snapshot, and on quiet feedback the planner would park two trains on one section for
        // the runtime to discover.
        if (p.getS88() != null && this.pointsBySensor.containsKey(p.getS88()))
        {
            for (Point sibling : this.pointsBySensor.get(p.getS88()))
            {
                // Not gated on the sibling being active: a detection section is electrical, and a train parked on a
                // deactivated siding holds the sensor as hard as one on a live platform.  snapshot records occupants of
                // inactive points, so skipping it would let a second train be routed into the active twin, for the
                // runtime to refuse on live feedback partway through the run.
                if (sibling.equals(p)) continue;

                Locomotive there = state.get(sibling);

                // The MOVER is not exempt either.  It reads as though it must be (a train cannot be blocked by
                // its own sensor), but the point it is standing on is not what is being asked about: isPathClear never
                // looks at the point a path STARTS from, it looks at the end of every edge against the live feedback,
                // and it exempts nobody.  While the train still stands on this section the feedback really is set, so a
                // leg into another point reporting that sensor would be planned and then refused at execution, the run
                // retrying until it was abandoned.  Hardware-conditional: on pulsed feedback the sensor clears behind
                // the train; on latching occupancy detection the check fires every time.  Departing is unaffected:
                // `from` is never passed to this method.
                if (there != null) return false;
            }
        }

        // The two exclusion lists mean different things, deliberately.  On a NON-station, exclusion means the
        // locomotive may not pass at all: the collision constraint, enforced here.  On a station it means the
        // locomotive may not STOP there, which canRest enforces; driving through is allowed, because a station on a
        // through route is exactly where an operator puts "not this one, not here".  The stricter reading on stations
        // too removed 45% of the reachable station pairs for two locomotives on the author's layout.
        //
        // Asked of the square, as isPathClear asks it: a station's copy for a barred arrival side is no
        // destination, and is still a station's.
        return !p.excludes(loc) || this.layout.isAStationSquare(p);
    }

    /**
     * Sensors reading occupied that no locomotive on the graph accounts for.
     *
     * Something is sitting on that track which the graph does not know about, and no move of ours will clear it, so it
     * stays blocked for the whole plan.
     *
     * A sensor a KNOWN train is standing on is not blocked here.  That train can move, and where it leaves the section
     * closed behind it is the mutual exclusion in canEnter: two active points sharing an address cannot both be
     * occupied.  Expressing it there is what makes the rule structural instead of a function of whatever the feedback
     * happened to read a moment ago.
     *
     * **AND A SENSOR A STANDING TRAIN'S TAIL IS LYING ON GOES WITH THAT TRAIN** (Adam: **"Yes, tails hold
     * sensors."**).  So the section behind a long train reads occupied with nothing standing on any square that reports
     * it: unexplained here, and shut for every move of the plan while that train has not moved, because
     * `Layout.isPathClear` reads the live feedback and refuses an edge whose end reports a set sensor (OB-073).  Once
     * the plan has moved the train whose tail it is, the tail is somewhere else and the section is free, which is why
     * this reads the STATE.
     */
    private Set<String> blockedSensors(Map<Point, Locomotive> state)
    {
        Set<String> out = new HashSet<>();

        // PER CAUSE AND PER END, not per sensor.  Freeing a sensor as soon as ONE tail that had lain on it
        // moved, or freeing both ends of every covered edge, would over-claim in the direction this class must never
        // take (the planner looser than the railway, OB-073):
        //
        //  - `edgesCoveredByStandingTrains` claims an edge WHOLE or not at all (which is why `placesCoveredByStandingTrains`
        //    exists to narrow it), so a point at the far end of a long edge would be freed although the tail never reached it;
        //  - one boolean per sensor would let a departed tail free a sensor a SECOND, unmoved tail still lies on.
        //
        // So a sensor is freed only when every tail accounting for it has gone, and a tail accounts for the END OF AN
        // EDGE only where the places say it reached that end.
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

                // ONLY THE END THE TAIL ACTUALLY REACHED.  `Layout.tailLiesOn` answers about the whole edge (any place
                // of it), which on a long edge would free the far point as well.  The place ADJACENT to each end is the
                // first for the start and the last for the end, in path order, and a tail that has not claimed it never
                // lay on that square's section.  With no places described, the edge is claimed whole, as before 3.0.0.
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
     * Split out of {@link #canBeHome} so the refusal can say which of the two reasons it is: a grayed item with no
     * explanation is the thing OB-050 was about.  Both doors (the right-click menu and the assignment) ask this, and
     * each acts on the answer it is about.
     *
     * @param loc the locomotive being assigned
     * @param at the square
     * @return a message key, or null when the assignment is fine
     */
    public static String whyNotAHome(Locomotive loc, Point at)
    {
        // A SPLIT SQUARE IS AN ORDINARY HOME (Adam: *"the home should just be the logical point, and the direction is
        // wherever the locomotive was facing when it started moving."*).  One answer, because the home is the SQUARE,
        // which is what Point.isSamePlaceAs has said since MT-165; a square drawn as several graph Points is not
        // refused (nor by Layout.setHomeLocomotive or the loader).
        //
        // AND USABILITY IS ASKED OF THE SQUARE TOO, or "the home is the square" means nothing here.  A platform whose
        // copies are a turning berth and a through road is a good home for a locomotive that cannot reverse: it stands
        // on the through road.  Asking only the copy that happens to carry the home would refuse that, and which copy
        // carries it is a choice AutonomyBuilder made, not one the operator made.
        return canRestOnSquare(loc, at) ? null : "autolayout.errorHomeCannotRestHere";
    }

    /**
     * The home assignment that excluding these locomotives from this station would contradict.
     *
     * canBeHome asked from the other side.  Assigning a home to a station that excludes the locomotive is warned about;
     * excluding a locomotive from the station that is its home reaches the same dead state (every future Return Home
     * reports IMPOSSIBLE), so the guard stands on both doors.
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
     * Whether this train could make any move at all from where it stands, with every other train taken off (OB-325).
     *
     * The search's own move - `firstClearRoute`, to each station it would try - asked of an arrangement holding this
     * train alone.  Every rule that reads the arrangement only takes moves away when another train is in it: a square
     * someone stands on, a sensor a standing train or its tail explains, a station held back by an occupied one, a tail
     * lying across the way.  A sensor occupied with nothing known on it blocks every arrangement alike.  So no move here
     * means no move in any arrangement the search could reach, and `plan` may name the train as a proof.
     *
     * Looser than the search on purpose where it differs: the rules the search applies after a route is found (where a
     * turn on the way may end, a train turned by the plan going only home) are not asked, so this may say "it can move"
     * where the search would not - that train is then searched for as before - and never the other way round.
     *
     * Asked twice by `plan`: reading the sensors, as the search reads them, and then with none read - a train held only
     * by a sensor nothing on the graph accounts for is not named.
     *
     * @param l the train
     * @param from where it stands
     * @param readTheSensors false to ask as if no sensor read occupied
     * @return true when some station can be reached from there by a route the search could use
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
     * Why one locomotive cannot get home, in words, one sentence per rule it breaks (FR-078).
     *
     * The same questions `plan` asks before it calls a train unreachable, broken into their parts, so the log can say
     * which part: the square it stands on, the home itself, or the way between, each named as the square it is, never
     * as the builder's direction copy.  A home is a SQUARE, so a home rule is reported only when no copy of it
     * a train can STOP at would take the train: a copy that is not a destination has no say in it.
     *
     * @param loc the locomotive
     * @param from where it stands
     * @param home its home
     * @return at least one sentence
     */
    private List<String> whyNotHome(Locomotive loc, Point from, Point home)
    {
        List<String> out = new ArrayList<>();

        if (!from.isActive())
        {
            out.add(I18n.f("autolayout.whyHomeStartOutOfService", Layout.placeNameOf(from)));
        }

        // FACING THE WAY TRAINS MAY NOT ARRIVE, AT A STATION: said so, with what to do.  A placement turns
        // the train round, so "place it on a station first" would send the operator back to the same square.
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

        // OVER THE COPIES A TRAIN CAN STOP AT, and only those.  A platform whose arrival from one side is
        // barred is built as a destination copy and a copy that is not one, and `validateTrainLength` answers yes for a
        // copy that is not a destination, so the barred copy would answer the length question and the length sentence
        // would never be said.
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
     * What can be seen to stand in the way when the search found no arrangement (FR-078).
     *
     * NO_PLAN_FOUND proves nothing - the search only ran out of room - so this says only what is TRUE
     * of the railway as it stands: which homes another train is standing on.  That is the commonest
     * reason and the one the operator can act on; anything subtler is the search's, and is not
     * guessed at.
     *
     * @return sentences keyed by locomotive; a locomotive with nothing visible in its way gets the
     *     plain statement that no arrangement was found, so every train away from home is mentioned
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

    /**
     * The copies of a home square a train may come home on: every copy, or - for a home held to a facing - those facing
     * that way, which is what `atHome` counts as home (OB-282).
     *
     * @param home the home
     * @return the copies, never empty
     */
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
     * Whether this locomotive could get home at all, over any copy of the home square.
     *
     * Deliberately one method rather than two: resting and reaching asked separately over the copies would accept a
     * home where one copy can be rested at and a DIFFERENT one reached, which is no home at all.  They are asked of the
     * same copy.
     *
     * Blind to occupancy, like `connected`, and for the same reason: a route blocked merely by another train is not
     * impossible, and moving that train is what the planner is for.
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

        // THE COPIES IT MAY COME HOME ON: every copy of the square, or those facing the way a home held to a facing
        // faces.  Asked of every copy, a home the train could only reach turned round would pass here, and the
        // search would spend its budget to answer "maybe" about something provable.
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
     * Whether a locomotive could stand anywhere on this square.
     *
     * `canRest` answers about one graph Point, the right question when routing a train to a particular arrival side and
     * the wrong one about a HOME: a home is a square, so what matters is whether the train can stand on any copy of it.
     * An ordinary square (no block) falls straight through to the plain answer.
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
     * The staging planner's answer to "who is standing on the same piece of track as this square": the second of
     * `Point.Occupancy`'s two named variants, the one that reads the PLAN.
     *
     * It consults three things, and the third is a deliberate divergence from the runtime:
     *
     *  - the square itself;
     *  - the other copies of it, by BLOCK, exactly what the runtime's `getBlockLocomotive` asks (a train on a copy the
     *    restriction does not name would otherwise be invisible here: a plan the railway refuses, OB-073's own symptom);
     *  - the other points reporting the same SENSOR, which the runtime does NOT consult.  Two active points on one
     *    feedback are one detection section, so the planner is right that they cannot both hold a train, but
     *    `AutonomyBuilder` says outright that a sensor is not a square: *"a station, its approach guard and a reversing
     *    point can be three Points on one feedback."*  On such a layout this refuses arrivals the runtime would allow.
     *    It fails SAFE (a refused plan, never a wrong movement) but is the "planner is the stricter half" shape, whose
     *    symptom is NO_PLAN_FOUND.  Left in force deliberately: dropping it changes which stations staging offers on a
     *    real railway, and that is Adam's decision rather than a refactor's.  Nothing pins this third half: on a
     *    four-point ring `canEnter`'s own sensor rule refuses the arrival first, so a test needs a fixture where only
     *    the FR-001 relation joins the watched square to the destination.
     *
     * @param state who is standing where, in the plan
     * @return the occupancy source for `Point.heldBackBy`
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
     * Whether a point holds a locomotive that is not the one being planned.
     *
     * @param p the square
     * @param loc the locomotive being planned, which is exempt
     * @param state who is standing where, in the plan
     * @return true when somebody else is there
     */
    private static boolean heldBySomebodyElse(Point p, Locomotive loc, Map<Point, Locomotive> state)
    {
        Locomotive there = state.get(p);

        return there != null && !there.equals(loc);
    }

    /**
     * The other points the planner treats as one piece of track with this one.
     *
     * Read off the layout rather than out of a field: a cache is not worth bringing back for a walk this short.
     * `canRestOnSquare` reads block copies the same way, a few methods down.
     *
     * @param track the square being asked about
     * @return its block copies and its sensor siblings, never including the square itself
     */
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
                // A copy that is both a block sibling and a sensor sibling (every copy on a builder-emitted layout) is
                // asked once; the two terms are meant to be visibly different sets.
                if (!sibling.equals(track) && !out.contains(sibling)) out.add(sibling);
            }
        }

        return out;
    }

    /**
     * Whether a locomotive may come to rest on a station: its length, and the station's exclusions.
     *
     * Not reversibility: whether a train can GET to a terminus is a question about a route, asked where there is one.
     */
    private static boolean canRest(Locomotive loc, Point at)
    {
        // NOT THE TERMINUS RULE.  Refusing a parking berth as a home to any train that cannot reverse refused most of
        // the places a train is actually parked (most berths are terminuses; EN57-947 could not be homed at
        // TunnelLeftPark for this reason alone).  Whether the train can GET there is a question about a ROUTE: a path
        // that passes a reversing point turns it, so it backs in and leaves forwards.  Layout.isPathClear asks that,
        // where there is a path to ask about.  Asking again here, with none, could only guess, and a guard that nothing
        // can satisfy is worse than no guard.

        return at.isDestination()
            && at.isActive()
            && !at.excludes(loc)
            && at.validateTrainLength(loc);
    }

    // There is no two-argument `connected`: a convenience overload that silently supplies one of the
    // two things this question turns on is worth removing, and which value that argument should
    // take is the caller's to make.


    /**
     * Whether a route exists from one square to another, ignoring who is standing where.
     *
     * There is no reversal parameter: Return Home is manual operation, so nothing asks whether the way there turned the
     * train round (Adam: *"Return home is manual, take the rule out and say why"*).  A parameter every caller answers
     * the same way is how a reader comes to believe there is a choice being made (the
     * same shape is kept in the diagram code).  `firstClearRoute` still tracks `turned` and keys on it deliberately:
     * collapsing ITS key changes which route the search returns first, and this one only has to answer yes or no.
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

        // NO REVERSAL STATE: nothing asks whether the train has been turned by the time it arrives.  The findings that
        // shaped this search are about its relationship to the executor: the two searches disagreeing
        // about where a train starts, and this one forgetting that `executePath` turns a train on arrival
        // at a terminus as well as at a reversing point.  Both were the PROOF being stricter than the runtime, which is
        // the error that matters here: `plan()` consults this to call a locomotive UNREACHABLE, the only thing this
        // class claims to have proved.  A proof may be looser than the search it guards, never tighter.
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

                // A TERMINUS IS TRAVELED THROUGH HERE, BY STOPPING AT IT.  `firstClearRoute` will not expand a
                // terminus, rightly: it builds ONE path, and a train arrives at a terminus without driving on past it.
                // This search answers a different question: `plan()` consults it to declare a locomotive UNREACHABLE,
                // and `plan()` ends a move at any station and starts the next from there, so stopping at a terminus and
                // setting off again is an ordinary two-move plan, not an impossibility.
                //
                // Only a terminus a move could actually END at, which is a destination that is in service.  One that is
                // neither is not a stop, and traveling through it would invent a move the planner cannot make, the
                // costlier error: a proof may be looser than the search it guards, never tighter.

                // WHERE THIS IS STILL TIGHTER THAN THE SEARCH, for whoever widens it next.  An out-of-service
                // or non-destination terminus is not expanded at all, and the search WILL end a leg at one and start
                // the next from it, so a graph whose only route home passes through such a square is proved IMPOSSIBLE
                // although a two-move plan exists.  Not reachable on a builder-derived graph, because the main line
                // runs over the other copies of a split square; structurally real, and the direction that is safe to
                // widen is this one, never the other.
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
     * The same rule canEnter enforces between a train and the section it wants to enter, asked here between two goals
     * instead.
     *
     * **It asks the SENSOR, not the BLOCK, and the decision to keep it is Adam's.**  The case for the block: the
     * railway's own mutual exclusion is `getBlockLocomotive`, and AutonomyBuilder says outright that a station, its
     * approach guard and a reversing point can be three Points on one feedback, so "one sensor" is not "one piece of
     * track", and homing two locomotives on two such squares answers IMPOSSIBLE naming both, a false proof.  Measured
     * on the derived graph, every sensor carrying two active stations carries one SQUARE emitted once per arrival side
     * (and a home on a square that is several Points is refused by whyNotAHome anyway), so his layout is not an
     * instance of it.
     *
     * The case against changing it: `canEnter` enforces the sensor rule deliberately and structurally, so the planner
     * will not produce that arrangement, and "no arrangement exists" is true of every arrangement it can reach.
     * Removing it turns an instant IMPOSSIBLE naming both locomotives into a search that burns its whole budget and
     * answers "maybe", on every Return Home of a layout with eleven shared sensors
     * (`testTwoActivePointsSharingASensorAreNeverBothOccupied`).
     *
     * It is about two homes on one DETECTION SECTION rather than about an occupancy restriction.  See MT-187.
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
     * A square emitted as several Points is ONE piece of track (that is what a block IS), so a train on any copy of its
     * home platform is home.  The comparison is `Point.isSamePlaceAs`, shared with `Layout.claimHome` so the two doors
     * agree on when a platform is taken.  Without it a train returning on the far copy of its own platform would be
     * judged not home, and the planner would try to move it to the exact copy, which on a split square means arriving
     * from one particular direction and may be impossible.
     *
     * @param home the home, or null
     * @param where the locomotive is now, or null
     * @return true when those are the same place
     */
    private static boolean atHome(Point home, Point where)
    {
        if (home == null || !home.isSamePlaceAs(where)) return false;

        // AND FACING THE WAY IT WAS HOMED, where the home says which way (OB-282; Adam: *"yes, it should accomplish the
        // facing.  but it's also reasonable to expect that the input facings are ones realistic on the layout.  we
        // shouldn't allow an impossible facing to be saved."*).
        //
        // A copy is a facing (behaviour.md 3), so the other arrival of the home square is the train turned round, which
        // counts as home only for a home set with no facing (the square, MT-165).  Where the home was set facing a way,
        // only a copy FACING that way is home.  Not the copy with the same arrival side: that includes the home copy's
        // turning twin, which points back at the side it came in by, the train turned round again:
        // reported already home at BottomMainB, which trains may turn at.  A copy of the other arrival that has turned
        // faces the home's way, and is home.
        return !home.isHomeFacingFixed() || java.util.Objects.equals(home.getCopyFacing(), where.getCopyFacing());
    }

    /**
     * Whether the run turns a train round when a leg ends here.
     *
     * Legs run under `ALWAYS_REVERSE`, for which `Layout.turnsOnArrival` is a terminus or a reversing point.
     */
    private static boolean turnsATrainArrivingAt(Point where)
    {
        return where != null && (where.isTerminus() || where.isReversing());
    }

    /**
     * Whether the only way from one square to another doubles back through a copy of either.
     *
     * The fourth correct divergence in `auditAgainstRuntime`: `Layout.bfs` will not walk such a route, so the menu (the
     * audit's oracle) offers no such destination, while the planner may still use one because Adam chose *"menu and
     * autonomy only"*.  Where that is the ONLY way there, the two are meant to disagree.
     *
     * Asked as the two searches, the same shape `Layout.firstClearOrWhyNot` uses: nothing from the search that refuses
     * laps, and something from the one that does not, means every route there is a lap.  Occupancy is not consulted -
     * this is a question about the track, and the audit has already decided what to compare.
     *
     * @param from where the train stands
     * @param to the destination the oracle did not offer
     * @return true when the track connects them only by a lap
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
     * Whether a route turned the train on its way, short of where it ends.
     *
     * The end is the caller's own question - `turnsATrainArrivingAt` - because a route that ends at a turning square
     * leaves the train facing out of it, which is the backing-in case MT-245 protects.  A reversing point PASSED on
     * the way leaves it running the other way for the rest of the move.
     *
     * @param route the route the plan gave this train, or null where it has not moved
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
     * The fewest moves that can still be needed from this arrangement - the A*'s estimate (OB-230).
     *
     * **Never more than the true number**, which is what keeps a plan found by the search one of the fewest moves.
     * Three counts, each a move that cannot be avoided, and no move counted twice:
     *
     *  - every homed train away from home needs at least one - `misplaced`, which was the whole estimate;
     *  - every HOMELESS train standing on the home of a train still away must move at least once, and `misplaced`
     *    never counts a homeless train;
     *  - every closed RING of trains each standing on the next one's home needs one more: the first to move cannot
     *    go home, because another member of the ring is standing there.  A train stands on one square and a home
     *    holds one train, so rings never share a member.
     *
     * `misplaced` alone could not tell an arrangement where every train can drive home from one where they are wedged
     * into each other, so the search spent its budget in breadth.
     *
     * @param state where the trains stand
     * @return a lower bound on the moves still needed
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
     * Two orders of moves that put the trains on the same squares are the same arrangement only if they leave the same
     * tails - otherwise one of them may let a later move through that the other refuses, and a search that keeps the
     * first it found can answer NO_PLAN_FOUND where a plan exists.  Keyed by the edges each tail covers, walked by
     * `Layout.edgesATailWouldCover` as `passesTheTailsOfTrainsItHasMoved` asks it, rather than by the route itself:
     * two routes whose tails lie on the same track are one arrangement, so the state count grows only where the tails
     * really differ.
     *
     * @param state where the trains stand
     * @param routes the route each moved train last took
     * @return the key
     */
    private String tailKey(Map<Point, Locomotive> state, Map<Locomotive, List<Edge>> routes)
    {
        if (routes.isEmpty() || this.layout == null) return key(state);

        List<String> tails = new ArrayList<>();

        // AND WHAT THE PLAN DID TO EACH TRAIN IT MOVED.  `turnedByThePlan` reads two facts out of
        // `movedAlong`: whether the train is in it at all, and whether the route it holds passed a reversing point.
        // `key(state)` carries neither, so two states with the same arrangement would share a key, and whichever the
        // search reached first would decide whether that train was restricted to its home on the next expansion.  Both
        // are carried here, and every moved train is named whether it turned or not, which keys the first fact (the
        // tails list below drops a train whose tail covers nothing).  Written `<name>/turned` or `<name>/straight`, the
        // spelling `firstClearRoute` uses for its own visited set.
        //
        // A finer key can spend more of SEARCH_LIMIT (about 8% more states over
        // `core.testReturnHomeOnRealLayout`, the same plans); it cannot lose a plan, because every state it separates
        // was two states the search was treating as one.
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
