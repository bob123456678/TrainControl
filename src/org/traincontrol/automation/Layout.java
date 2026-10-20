package org.traincontrol.automation;

import org.traincontrol.base.Accessory;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.base.Locomotive;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.traincontrol.model.ViewListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.traincontrol.util.I18n;

/**
 * Represent layout as a directed graph to support fully automated train operation
 * @author Adam
 */
public class Layout
{
    // Callback names
    public static final String CB_ROUTE_END = "routeEnd";
    public static final String CB_ROUTE_PROG = "routeProg";
    public static final String CB_ROUTE_START = "routeStart";
    public static final String CB_PRE_ARRIVAL = "preArrival";
    
    // If set to true, paths will automatically execute.  Only useful for debugging / testing during development.
    private boolean simulate = false;

    // ms to wait between configuration commands
    public static final int CONFIGURE_SLEEP = 150;

    // Base time budget for path validation: the deadline is PATH_VALIDATION_MS * (accessories on the path + 1), see
    // validatePathActuation.  Not final so tests can shorten it.
    public static int PATH_VALIDATION_MS = 1000;

    // When true, configureAndLockPath verifies (via the CS echo) that every accessory on the path reached its commanded state before releasing the
    // locomotive; on a persistent mismatch it stops that locomotive and releases its locks rather than letting it depart onto an unset path.  Tied to the
    // "Path Integrity Validation" preference in the UI, but respected headless via this flag.  Default on.
    public static boolean PATH_INTEGRITY_VALIDATION = true;

    // Every path validation failure is always logged to the console.  A UI popup is only raised once this many failures have accumulated, and at most
    // ONCE per Layout instance: after that the console is considered sufficient.  Tunable.
    public static int PATH_VALIDATION_ALERT_THRESHOLD = 3;

    // Running count of path validation failures on this Layout.  Never reset; informational / used by tests.  Per-instance (not static) so a re-created
    // Layout starts clean.  Guarded by this Layout since its locomotives validate paths concurrently.
    private int pathValidationFailureCount = 0;

    // Whether the one-time UI alert has already been shown for this Layout instance.  Guarded by this
    // Layout, same as pathValidationFailureCount.
    private boolean pathValidationAlertShown = false;

    // Maximum number of seconds another locomotive should yield for to the inactive locomotive
    public static final int YIELD_SECONDS = 30;

    /**
     * How long a boxed-in locomotive waits before looking for a path again, when no delay is set.  pickPath shuffles,
     * sorts and BFSes the whole graph per candidate, so retrying flat out pegs a core per stuck train.
     */
    private static final long NO_PATH_IDLE_MS = 250;

    /**
     * How autonomy chooses between the routes a train could take.
     *
     * Every rule ranks routes by a number, lower being better (`costOf`); the choice is held per layout in `pathPreference`.
     */
    public enum PathPreference
    {
        /**
         * The route that runs past the fewest OTHER stations.  Not the fewest sensors: that would prefer whatever is
         * lightly instrumented, an accident of where the s88s went in.  Stations are where trains stop, so few of
         * them means interfering with few other trains.
         */
        FEWEST_STATIONS,

        /**
         * The route over the shortest track, added up from the lengths set on the tiles.  A section with none counts
         * ONE (see `lengthOf`), so with nothing measured this is the route over the fewest sections rather than a
         * tie (DCN-B2).
         */
        SHORTEST_LENGTH,

        /**
         * The route crossing the fewest SENSORS.
         *
         * Counted by distinct sensor, not by hops of the running graph.  On a derived graph a square is
         * several Points - one per arrival side - so a hop count is a count of the model's internal
         * structure and not of anything on the railway: two routes crossing exactly the same physical
         * s88s could come out with different numbers, and the one that "won" would have won for a
         * reason nobody could see on the diagram.
         */
        FEWEST_POINTS,

        /**
         * The route past the MOST other stations, and its two companions below: the mirror of each rule above, for a
         * layout that should look busy rather than direct (the same numbers negated).
         */
        MOST_STATIONS,

        LONGEST_LENGTH,

        MOST_POINTS,

        /**
         * The route to whichever station has gone longest without a train.
         * On a layout with a favourite loop every other rule can leave the far corner untouched all evening.
         *
         * Ranked by when the destination last had an arrival, so a station never visited wins outright.  Station
         * PRIORITY still applies first.
         */
        LEAST_RECENTLY_VISITED,

        /**
         * Whatever is found first, which with the destinations shuffled is effectively at random.
         * The DEFAULT, because it is what every version before this did, and the cheapest: the ranked options have to
         * enumerate the alternatives before comparing them.
         */
        RANDOM,

        /**
         * Whatever is found first, with station priority ignored entirely (OB-156).
         * The opposite of RANDOM, which shuffles and then SORTS by priority: every reachable destination is equally
         * likely, so a station pushed down to -5 is visited as often as one raised to 9.  The priority comparator
         * answers "equal" for this rule and Collections.sort is stable, so the shuffle survives.  The band gate needs
         * no exception: it cannot fire until a ranked rule has stored a route.
         */
        RANDOM_ANY_STATION,

        /**
         * Priority weighed against how far away it is, rather than priority winning outright.
         *
         * THE ONLY RULE THAT SEES PAST THE TOP PRIORITY BAND: every other one ranks within a band, because pickPath
         * settles the highest band first.  A ratio lets a nearer, less important station win, so the band gate is
         * opened for this one.  With every priority at its default it ranks purely by 1/distance, which is
         * SHORTEST_LENGTH with extra arithmetic.
         */
        BALANCED_PRIORITY
    }

    /**
     * Which routing logic this layout uses.  PER LAYOUT, NOT PER PROGRAM: it is written into the autonomy config, not
     * the UI preferences, so it travels with the configuration.  It reaches the file the way every run-wide setting
     * does: toJSON writes it, captureFromLayout copies every top-level key into the configuration's globals, and the
     * builder puts them back for fromJSON.
     */
    private volatile PathPreference pathPreference = PathPreference.RANDOM;

    public void setPathPreference(PathPreference preference)
    {
        if (preference != null) this.pathPreference = preference;
    }

    public PathPreference getPathPreference()
    {
        return this.pathPreference;
    }

    /**
     * What a route costs under the current preference.  Lower is better.
     *
     * The destination itself is never counted: every candidate ends at a station, so it would add one to all of them.
     *
     * @param path the route being weighed
     * @return its cost, or 0 where the preference does not rank
     */
    private int costOf(List<Edge> path)
    {
        switch (this.pathPreference)
        {
            case FEWEST_POINTS:
                return sensorsOn(path);

            case MOST_POINTS:
                return -sensorsOn(path);

            case LEAST_RECENTLY_VISITED:
                return recencyOf(path);

            case SHORTEST_LENGTH:
                return lengthOf(path);

            case LONGEST_LENGTH:
                return -lengthOf(path);

            case FEWEST_STATIONS:
                return stationsOn(path);

            case BALANCED_PRIORITY:
                return -ratioOf(path);

            case MOST_STATIONS:
                return -stationsOn(path);

            default:
                return 0;
        }
    }


    /**
     * How much priority a route buys per unit of track (BALANCED_PRIORITY).
     *
     * SCALED BEFORE THE DIVISION, because costOf deals in ints: otherwise priority 3 over 7 units and priority 2
     * over 7 both come out as zero and every route ties.
     *
     * Distance is measured as SHORTEST_LENGTH measures it, floor included - except that it divides by at least 1, since
     * a route answered 0 throughout measures 0.  So between a route of 0 and a route of 1 the two rules disagree: the
     * shortest track prefers the 0, and this ties them (RLA-C7).  Only stations joined by sensors side by side, with
     * everything between answered 0, can meet it.
     *
     * @param path the route being weighed
     * @return priority per unit of track, larger being better
     */
    private int ratioOf(List<Edge> path)
    {
        if (path == null || path.isEmpty()) return 0;

        Point end = path.get(path.size() - 1).getEnd();

        int length = Math.max(1, lengthOf(path));

        // WHAT A STATION IS WORTH, never zero or below (RC-B2).  At and above zero it is (priority + 1) times a million:
        // the default priority is ZERO, so a bare priority/distance would score every ordinary station 0 and tie them
        // all.  An unprioritised railway therefore ranks purely by 1/distance, and each step of priority multiplies
        // what a station is worth against the track it costs to reach.
        //
        // Below zero it shrinks instead of going negative (negative priorities are meant: Point.setPriority takes
        // them): a negative numerator over a larger length gets LARGER, so "go here less" would read as "go the long
        // way round", and a clamp at 1 would tie every de-prioritised station with an ordinary one.  A station is
        // worth 1/(1 - priority) of an ordinary one: half at -1, a third at -2, always positive, ordered among
        // themselves.
        //
        // A MILLION, in long (RC-B8): this is integer division, so the ratio ties at zero for every route longer than
        // `worth`; a million pushes that past anything real (a route longer than 1000000/(1 - priority) tiles).
        long worth = end.getPriority() >= 0
            ? (end.getPriority() + 1L) * 1000000L
            : 1000000L / (1L - end.getPriority());

        // Clamped rather than wrapped: a priority somebody typed with a stuck key must not come back
        // as a negative cost.
        return (int) Math.min(Integer.MAX_VALUE, worth / length);
    }
    /**
     * How long a route is, from the lengths set on its tiles.
     *
     * AN EDGE WITH NO LENGTH COUNTS ONE (Adam: *"where we count each s88 as length 1 by default"*): an edge runs from
     * one sensor to the next, so crossing one is one s88's worth of track until somebody says otherwise.  Without the
     * floor the ranking would tie at zero and SHORTEST_LENGTH and LONGEST_LENGTH would choose the same route.
     *
     * THE FLOOR ONLY WORKS BECAUSE THE UNITS ARE SMALL (LE2-B9): lengths run 1 to 6 in the hand-built graph and 2 to
     * 4 in the derived one, so the floor is the size of the smallest real edge.  For a railway measured in
     * centimetres the floor would have to become proportional to the measured lengths rather than a flat 1.
     *
     * AN EDGE ANSWERED 0 COUNTS 0.  The floor is for track nobody has measured; an answered 0 is a measurement
     * (`Edge.isMeasured`), as every length rule reads it, and counted as 1 it would make a route through sensors
     * side by side look longer than it is.
     */
    private int lengthOf(List<Edge> path)
    {
        int total = 0;

        for (Edge e : path) total += e.getLength() > 0 ? e.getLength() : e.isMeasured() ? 0 : 1;

        return total;
    }

    /**
     * How many distinct SENSORS a route crosses.
     *
     * By block where a Point has one, since that is the builder's name for "the several copies of one
     * square", and by the Point's own identity otherwise - a hand-built graph has no blocks and its
     * Points already are one square each.
     *
     * The destination is not counted, for the same reason stationsOn does not count it: it would add
     * one to every candidate and change no ordering.
     */
    private int sensorsOn(List<Edge> path)
    {
        java.util.Set<String> crossed = new java.util.HashSet<>();

        for (int i = 0; i < path.size() - 1; i++)
        {
            Point at = path.get(i).getEnd();

            if (at == null) continue;

            crossed.add(at.getBlock() != null ? at.getBlock() : at.getUniqueId());
        }

        return crossed.size();
    }

    /**
     * How recently a route's destination last had a train, as a cost where lower is better.
     *
     * Seconds since the last arrival, negated - so a station that has waited longer costs less. A
     * station with no recorded arrival at all is treated as having waited forever, which is what sends
     * the first few runs of an evening out to the parts of the railway nobody has used yet.
     */
    private int recencyOf(List<Edge> path)
    {
        if (path == null || path.isEmpty()) return 0;

        Point destination = path.get(path.size() - 1).getEnd();

        if (destination == null) return 0;

        Long last = this.lastArrival.get(recencyKeyOf(destination));

        if (last == null) return Integer.MIN_VALUE;

        long waited = (System.currentTimeMillis() - last) / 1000L;

        // Clamped, because a long-running session can put a station's wait beyond what an int holds
        // once negated, and an overflow here would silently reverse the ordering
        if (waited > Integer.MAX_VALUE) waited = Integer.MAX_VALUE;

        return (int) -waited;
    }

    /**
     * What "this station" means when recording a visit: the SQUARE, not the arrival-side copy.
     *
     * By block where a Point has one, exactly as sensorsOn counts, and by the Point's own identity otherwise (a
     * hand-built graph has no blocks and its Points already are one square each).  Keying by uniqueId would make the
     * preference useless on derived graphs: a train arriving by the north copy would record nothing about the south
     * copy, which would then win as "never visited".
     */
    private String recencyKeyOf(Point point)
    {
        if (point == null) return "";

        return point.getBlock() != null ? point.getBlock() : point.getUniqueId();
    }

    /**
     * When each Point last had a train arrive, for LEAST_RECENTLY_VISITED.
     *
     * Kept in memory rather than saved: it describes this session's running, and a layout switched on
     * tomorrow has not been anywhere yet.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> lastArrival =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The visit history, so a rebuild can put it back (AUR-C3).
     *
     * **A session is longer than a `Layout`.**  `parseAuto` replaces the whole object on every setup gesture, so
     * without this `LEAST_RECENTLY_VISITED` would go back to knowing nothing each time.
     *
     * **Keyed by POINT NAME, not by the key the rule uses** (FXV-C5): `recencyKeyOf` is the block or `getUniqueId`,
     * and unique ids come from a global allocator, so entries carried over by id would never match.  A name survives
     * a rebuild of the same railway.  A copy, because the caller holds it across the moment the old layout is
     * invalidated.
     *
     * @return where trains have been, by point name
     */
    public java.util.Map<String, Long> getVisitHistory()
    {
        java.util.Map<String, Long> out = new java.util.HashMap<>();

        for (Point point : this.getPoints())
        {
            Long last = this.lastArrival.get(recencyKeyOf(point));

            if (last != null) out.put(point.getName(), last);
        }

        return out;
    }

    /**
     * Puts a previous layout's visit history back after a rebuild (AUR-C3).
     *
     * Keyed by POINT NAME on the way in, and translated back into this layout's own keys here (FNL-C4): unique ids do not survive a rebuild (they come
     * from a global allocator), so a name this configuration does not have is dropped, which is the same answer as never having been there.
     *
     * @param history what the outgoing layout knew
     */
    public void restoreVisitHistory(java.util.Map<String, Long> history)
    {
        if (history == null || history.isEmpty()) return;

        // Translated back into this railway's own keys; a name this configuration does not have is dropped, which also
        // answers the other door `parseAuto` serves (FXV-C5): a pasted-in railway has never visited a station not on it.
        for (java.util.Map.Entry<String, Long> been : history.entrySet())
        {
            Point here = this.getPoint(been.getKey());

            if (here != null) this.lastArrival.put(recencyKeyOf(here), been.getValue());
        }
    }

    /**
     * Records an arrival at a point, for a test that needs a known visit history (the real recording happens at the
     * end of executePath, which means driving a train).
     *
     * @param pointName the point that has just had a train
     */
    public void noteArrivalForTest(String pointName)
    {
        Point at = this.getPoint(pointName);

        // By `recencyKeyOf`, which the real recording uses: keying by NAME here would never match.
        if (at != null) this.lastArrival.put(recencyKeyOf(at), System.currentTimeMillis());
    }

    /**
     * How many stations a route runs PAST.
     *
     * Every edge end except the last, which is the destination (counting it would add one to every candidate).
     */
    private int stationsOn(List<Edge> path)
    {
        int stations = 0;

        for (int i = 0; i < path.size() - 1; i++)
        {
            if (path.get(i).getEnd().isDestination()) stations++;
        }

        return stations;
    }

    // Set to false to disable locomotives
    private volatile boolean running = false;

    /**
     * How many locomotive threads are alive, including any between one path and the next.
     *
     * Counted from the moment the thread starts to the moment it exits, so isRunning() has no window in which a thread
     * has passed its `while (running)` check and is inside pickPath (a shuffle, a sort and a breadth-first search of
     * the whole graph) but not yet in activeLocomotives: a graceful stop could otherwise report itself finished a
     * moment before a locomotive departs.
     */
    private final java.util.concurrent.atomic.AtomicInteger locomotiveThreads =
        new java.util.concurrent.atomic.AtomicInteger();

    /**
     * The timetable entries alive, each counted from before its thread starts until it leaves (RSA3-C5), including
     * while it pauses between refusals, plus the timetable's own loop while it dispatches them (RSA4-C1).  Uncounted,
     * a stopped run would read idle while an entry paused, and the entry would wake into the next run and stop it.
     */
    private final java.util.concurrent.atomic.AtomicInteger timetableEntries =
        new java.util.concurrent.atomic.AtomicInteger();

    /**
     * What a timetable's pauses wait on, so that any stop wakes them to leave (RSA3-C5) - see `pauseUnlessStopped`.
     * Held only to wait or to notify, never while taking another lock.
     */
    private final Object entryPause = new Object();

    /**
     * Held by the Yes across its two statements (`running` cleared, the stop counted) and by a timetable starting
     * across its two (`running` set, the count read), so that neither pair falls between the other's (RSA4-C2).  No
     * journey takes it, and neither holder waits inside it on anything but `entryPause`, so the event thread, which
     * makes the Yes, is never kept behind a journey (RSA3-C4).
     */
    private final Object stopLock = new Object();
    
    // Is the layout state valid?  Volatile: set on the thread that loads or invalidates, read by every driving thread
    // (GST-C1)
    private volatile boolean isValid = true;
       
    private final ViewListener control;
    final Map<String, Edge> edges;
    final Map<String, Point> points;
    private final Map<String, List<Edge>> adjacency;
    
    // Custom callbacks before/after path execution
    protected Map<String, TriFunction<List<Edge>, Locomotive, Boolean, Void>> callbacks;
        
    // List of all / active locomotives
    private final Set<Locomotive> locomotivesToRun;
    private final Map<Locomotive, List<Edge>> activeLocomotives;

    /**
     * Locomotives that have locked a path but are not yet running it.
     *
     * The cap on trains out at once cannot count from activeLocomotives alone: a locomotive appears there only when
     * executePath registers it, after the path is locked, the accessories thrown and validatePathActuation has waited
     * for them (seconds), so two trains on disjoint routes could both pass the cap check in that gap.  The track is
     * never at risk (the edges are exclusively locked either way), but a cap is usually set for something the model
     * cannot see, like what a booster will carry.
     *
     * Claimed inside the same monitor that locks the path, so the check and the claim cannot be separated, and given
     * up when the locomotive is registered or the path is released.
     *
     * It carries the PATH ITSELF, not merely the fact of a claim (RC-A10): otherwise getActiveAccs could not say which
     * accessories the dispatch had already thrown, and an s88-triggered route could move a turnout on a
     * half-configured path.
     */
    private final Map<Locomotive, List<Edge>> takingPath;

    /**
     * The reversal answer each train's run was dispatched with, written beside its path in `activeLocomotives` and read
     * only while that is there (OB-314): what lets the arrival's question be asked before the arrival.  Empty for
     * autonomy's own runs, which pass none.
     */
    private final Map<Locomotive, java.util.Optional<ReversalPolicy>> runPolicies = new ConcurrentHashMap<>();
    private final Map<Locomotive, List<Point>> locomotiveMilestones;

    /**
     * Edges of a running path that the train has completely finished with - tail and all.
     *
     * Separate from the LOCK, deliberately: the lock asks "may another train be routed here", and with atomicRoutes on the answer is deliberately no for
     * the whole run; this asks "is there still a train on top of this", and once the tail has gone past, the answer is no whatever the lock says (Adam:
     * "once a train passes, signals on the route, but behind the train, should still be allowed to be changed by auto routes.").
     */
    private final Map<Locomotive, Set<Edge>> clearedEdges;

    /**
     * The edges each running train has given BACK early - released, whole, as its tail cleared them (GUI-A1).
     *
     * Not `clearedEdges`, which it looks like: that set is kept in atomic mode too, so routes know which accessories
     * behind a train may move, and in atomic mode nothing in it is released until the run ends.  `unlockPath` has to know
     * what the run still holds, and only this says it - an edge the tail cleared under atomic routes is still held; one
     * released under non-atomic routes is not, whatever the setting is by the end.
     */
    private final Map<Locomotive, Set<Edge>> releasedEarly;
    private final Map<Locomotive, String> locomotivePendingS88;

    /**
     * The monitor for the pending-s88 bookkeeping, which is NOT the layout monitor.
     *
     * configureAndLockPath holds the layout monitor across its whole lock loop and sleeps CONFIGURE_SLEEP per edge and
     * per accessory, seconds on a long path, while every locomotive already under way calls updatePendingS88 just
     * before waiting for its next sensor.  Sharing the monitor would block that train long enough to cross and clear
     * the sensor it was about to wait for, and waitForOccupiedFeedback tests a LEVEL, so it would drive on without
     * slowing or stopping.  Nothing else waits on or notifies the layout monitor (waitForS88Reached and
     * updatePendingS88 are the only pair); `waitForTheRunToRecord` waits here too (RSA56-C1), briefly and never while
     * it reads the feedback store.
     */
    private final Object pendingS88Monitor = new Object();
    
    // Execution history
    private final List<TimetablePath> timetable;

    // Where each locomotive belongs: the station it occupied when it first appeared on this graph.
    // Injective by construction - see claimHome.
    private final Map<Locomotive, Point> homeStations;
    
    // Additional configuration
    private int minDelay;
    private int maxDelay;
    private int defaultLocSpeed;
    private boolean turnOffFunctionsOnArrival;
    private boolean turnOnFunctionsOnDeparture;
    private double preArrivalSpeedReduction = 0.5;
    private int maxLocInactiveSeconds = 0; // Locomotives that have not run for at least this many seconds will be prioritized
    // VOLATILE (RSA-C5): read by every running train at every sensor, and written by the atomic-routes gate on the
    // event thread while trains run (GUI-A1) - a train already driving need never see a write to a plain field.
    private volatile boolean atomicRoutes = true; // if false, routes will be unlocked as milestones are passed
    private boolean timetableCapture = false;

    // Staging plans are only valid executed one train at a time.  Set by loadReturnToHomeTimetable and
    // cleared by any other timetable load - see executeTimetable.
    private boolean timetableSequential = false;

    // The timetable a staging plan has borrowed, for as long as the plan occupies it (RLV9-B1): what a fold writes as
    // the railway's timetable, so a configuration folded while Return Home runs keeps its own.  Set and cleared as the
    // flag above is.
    private volatile List<TimetablePath> timetableOnLoan = null;

    /** Why the last timetable run stopped short, where it stopped at an entry it cannot run - or null (RSA9-B1). */
    private volatile String timetableStoppedBecause;

    // Set for as long as executeTimetable is driving.  Capture records what the OPERATOR drives; a
    // timetable run recording itself appends to the very list being walked, and the dispatch loop
    // re-reads its own size.  Staging was only one of the two entrances into that.
    private volatile boolean timetableExecuting = false;

    // A staging entry that cannot run will not become runnable by waiting: nothing else is moving.  A
    // few retries ride out a sensor settling; beyond that the assumption is wrong and it must say so.
    /**
     * How long a timetable entry may go on being unexecutable before the run gives up, in ms.
     *
     * A TIME rather than a count of attempts, because the pause between attempts is the user's own delay setting
     * (nothing to tens of seconds; pacedWait falls back to COMPLETION_POLL when both are zero), so a count would be a
     * wildly different wall-clock bound from layout to layout, and what is bounded is how long a train stands still.
     * Minutes, deliberately: a train in a parallel timetable may legitimately wait a long while for another to clear
     * its way, and ending a run that would have worked is worse than a late finish.  Not final so a test can shorten
     * it; there is no user-facing setting for it.
     */
    public static volatile long TIMETABLE_STUCK_MS = 180000;

    private static final int STAGING_MAX_ATTEMPTS = 3;
    private static final int STAGING_RETRY_PAUSE = 2000;

    // How often executeTimetable checks whether the run it dispatched has actually finished
    private static final int COMPLETION_POLL = 250;
    private int maxLatency = 0;
    private int maxActiveTrains = 0;

    // The trains on a journey sent by hand, for its length (BCR-C1): the cap on trains out neither counts nor refuses them,
    // however many a run already has out (behaviour.md 1).  By identity, as every locomotive map here is.
    private final Set<Locomotive> sentByHand = Collections.synchronizedSet(Collections.newSetFromMap(
        new java.util.IdentityHashMap<Locomotive, Boolean>()));

    /**
     * Where each locomotive was standing when this railway last turned it round at a destination.
     *
     * **The SQUARE, not a flip owed (REV9-A1).**  Naming the Point makes the answer ABSOLUTE: a train that has just
     * turned faces the way it came in (behaviour.md 4), the arrival wrote that side onto this very Point before
     * turning it, and applying it twice writes the same thing twice.  A parity of names would assume the window
     * FLIPS the facing the setup recorded, which is unsound when it is read: behaviour.md 6a says nothing writes where
     * a run's trains ended up back to the setup, so at the arrival square the recorded facing is absent or the
     * previous occupant's (OB-190).
     *
     * **Dropped when the train arrives somewhere without turning**: a record only means something while the train
     * still stands where it turned.
     *
     * Recorded at the destination (Adam: **"it should be recorded at the destination.  Otherwise, it's just the same
     * as always."**).  A reversal at the arrival is the one direction change nothing was following: its echo lands
     * inside the run's own pause while `isRunning()` is true, so the window takes the baseline-only branch, and the
     * idle reconcile at the end LEVELS the baseline rather than following it.  So it is recorded here, where it is
     * certain, and the window drains it when the railway next goes idle: recorded rather than acted on, because
     * writing a facing writes the SETUP and nothing in this class may call the window (a rule this file's own tests
     * pin).  Concurrent because driver threads write it and the event thread drains it.
     */
    private final java.util.Map<String, String> reversedOnArrival =
        new java.util.concurrent.ConcurrentHashMap<>();
    
    // Route-related settings
    private boolean activateRoutes = false;
    private List<Integer> activateRouteIDs;

    // Track the layout version so we know whether an orphan instance of this class is stale
    // Volatile because the writer (whichever thread loads a layout) is never a reader (every driving thread, at six
    // points in its loop, and both timetable waits): otherwise a locomotive could drive a whole path against a retired
    // graph.
    private static volatile int layoutVersion = 0;

    // Whether a staging flow owns this Layout - set at the commit point and cleared when the flow unwinds, mirroring the UI flag of the same lifetime.  It
    // lives here rather than only on the UI because six guards ask the *model* whether autonomy is busy, and one of them (the sync adopting Central
    // Station addresses) is not reachable from any UI predicate; nothing is dispatched while a plan is being derived, so isRunning() alone reads that
    // window as idle.
    private volatile boolean stagingInProgress = false;

    // This instance's version, fixed at construction.  Compared against layoutVersion to answer "am I
    // still the current Layout?" - see isCurrentLayout
    private volatile int version;

    // What the version counter is ticked under: a construction, a retirement (RLV8-A1), and a load made current (RLV8-C3).
    private static final Object VERSIONS = new Object();
    
    // The last error message - useful for debugging the JSON parse result
    private static String lastError = "";

    /**
     * Helper class for BFS
     */
    private class PointPath
    {
        public Point start;
        public List<Edge> path;

        public PointPath(Point start, List<Edge> path)
        {
            this.start = start;
            this.path = path;
        }
    }
    
    /**
     * Used to preview conflicting edge configuration
     */
    private class EdgeConfigurationState
    {
        public boolean configIsValid;
        public final Map<Accessory, Accessory.accessorySetting> configHistory;
        public final List<String> invalidConfigs;

        // Set when the preview failed for a reason more specific than conflicting commands, so that
        // isPathClear can report what actually went wrong instead of a generic conflict message
        public String errorMessage;

        public EdgeConfigurationState()
        {
            this.configIsValid = true;
            this.configHistory = new HashMap<>();
            this.invalidConfigs = new LinkedList<>();
            this.errorMessage = null;
        }
    }
    
    /** Why a train cannot start, and why a station is not offered or reached (FR-136). */
    private final LayoutExplanations layoutExplanations = new LayoutExplanations(this);

    /** Where each standing train's body lies: the tail walks behind the covered track and places (FR-136). */
    private final StandingTrains standingTrains = new StandingTrains(this);

    /**
     * Initialize the layout model 
     * @param control Reference to the CS2 controller
     */
    public Layout(ViewListener control)
    {
        this.control = control;
        this.edges = new HashMap<>();
        this.points = new HashMap<>();
        this.adjacency = new HashMap<>();    
        // These four are read by the UI (getActiveAccs, getActiveLocomotives, getReachedMilestones) without holding the writers'
        // synchronized(activeLocomotives) lock, so they must be individually thread-safe; the synchronized blocks still give the writers' compound
        // atomicity.  ConcurrentHashMap rejects null keys/values, so accessors that take a Locomotive must null-guard before touching these (see
        // getDestination/getStart/getReachedMilestones).
        this.locomotivesToRun = ConcurrentHashMap.<Locomotive>newKeySet();
        // ConcurrentHashMap: setCallback (e.g. opening the graph view) puts on the EDT while
        // executePath iterates callbacks.values() on loco threads - a plain HashMap would CME.
        this.callbacks = new ConcurrentHashMap<>();
        this.activeLocomotives = new ConcurrentHashMap<>();
        this.takingPath = new ConcurrentHashMap<>();
        this.locomotiveMilestones = new ConcurrentHashMap<>();
        // Same reason as the rest of these: written on locomotive threads, read by the UI and by the
        // route guard without a lock.
        this.clearedEdges = new ConcurrentHashMap<>();
        this.releasedEarly = new ConcurrentHashMap<>();
        // COPY ON WRITE (GST-C3): `getTimetable` hands out the list itself; the event thread reads it while a running
        // train's capture appends from that train's thread, and a reader sees the list as it stood when it began.
        this.timetable = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.homeStations = new LinkedHashMap<>();
        this.locomotivePendingS88 = new ConcurrentHashMap<>();
        this.activateRouteIDs = new LinkedList<>();
        
        synchronized (VERSIONS)
        {
            Layout.layoutVersion += 1;
            this.version = Layout.layoutVersion;
        }

        // Every journey of a railway this retires asks again whether to go on waiting, as `retireEveryLayout` says.
        Locomotive.wakeEveryWait();

        Layout.lastError = "";
    }
    
    /**
     * Adds a new locomotive to the timetable
     * @param loc
     * @param path
     * @return 
     */
    private boolean addTimetableEntry(Locomotive loc, List<Edge> path)
    {
        return addTimetableEntry(loc, path, System.currentTimeMillis());
    }
    
    /**
     * Adds a path to the history list
     * @param loc
     * @param path 
     * @param timestamp
     */
    synchronized private boolean addTimetableEntry(Locomotive loc, List<Edge> path, long timestamp)
    {
        // A staging run drives the timetable it is executing, so capturing it would append each move to the list being
        // walked, and the dispatch loop, which re-reads its own size, would execute the copies and fail them.  Capture
        // is for what the operator drives.
        if (!path.isEmpty() && loc != null && this.timetableCapture && !this.timetableExecuting)
        {
            timetable.add(new TimetablePath(loc, path, timestamp)); 
            
            // Calculate the delay time
            if (timetable.size() > 1)
            {
                TimetablePath second = timetable.get(timetable.size() - 1);
                TimetablePath first = timetable.get(timetable.size() - 2);

                // Stored on the LATER of the pair: everywhere else this field is the delay BEFORE that entry runs
                // (executeTimetable waits on the current entry's own value before dispatching it, and the edit dialog
                // says "enter delay (seconds) before this route executes").
                second.setSecondsToNext(second.getExecutionTime() - first.getExecutionTime());
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Writes onto the entry just recorded for this train and path whether its arrival turns it (RSA49-B1) - the newest
     * such entry, which another train's capture may have followed already.
     *
     * @param loc the train
     * @param path the path recorded
     * @param turn whether the arrival turns it
     */
    synchronized private void answerTheEntryRecorded(Locomotive loc, List<Edge> path, boolean turn)
    {
        for (int i = this.timetable.size() - 1; i >= 0; i--)
        {
            TimetablePath entry = this.timetable.get(i);

            if (entry.getLoc() == loc && entry.getPath() == path)
            {
                entry.setTurnAtTheEnd(turn);

                return;
            }
        }
    }

    /**
     * Returns the timetable/path history
     * @return 
     */
    public List<TimetablePath> getTimetable()
    {
        return this.timetable;
    }
            
    /**
     * Sets the list of locomotives that will be run
     * @param locs 
     */
    public void setLocomotivesToRun(List<Locomotive> locs)
    {
        this.locomotivesToRun.clear();
        this.locomotivesToRun.addAll(locs);
    }
      
    /**
     * Gets the locomotives that will be run
     * @return  
     */
    public Set<Locomotive> getLocomotivesToRun()
    {
        return this.locomotivesToRun;
    }
    
    /**
     * Sets the maximum allowed network latency. (minimum of 100ms)
     * @param latency 
     */
    public void setMaxLatency(int latency)
    {
        latency = Math.abs(latency);
        
        if (latency < 100)
        {
            latency = 0; // disable setting
        }
        
        this.maxLatency = latency;
    }
    
    public int getMaxLatency()
    {
        return this.maxLatency;
    }
    
    /**
     * Returns all accessories along active routes, the exit guards each path's first edge sets among them (OB-340)
     *
     * NOT synchronized, deliberately (IAR-B2): this is read from the EVENT THREAD, by the layout label's click handler,
     * while autonomy is going, which is when `configureAndLockPath` holds this object's monitor across its per-command
     * sleeps, so taking it would freeze the whole window, Stop included (OB-079 fixed three sites of this shape).
     * Safe without the monitor: `activeLocomotives` is a ConcurrentHashMap, so iterating its values is weakly
     * consistent, and the lists inside it are only ever put and removed whole.  What is given up is compound atomicity
     * (the answer may straddle one locomotive's path being replaced), the right trade: a warning computed a moment ago
     * is worth having; a frozen Stop button is not.
     *
     * @return 
     */
    public Set<Accessory> getActiveAccs()
    {
        return new HashSet<>(getActiveAccSettings().keySet());
    }

    /**
     * Every accessory along an active route, with the setting its route holds it at: true for TURN and RED, the sense
     * `RouteCommand.getSetting` and `Accessory.isSwitched` use, so that a route's command can be told from the one the
     * path is holding.  Null for an accessory two routes underway ask different settings of.  A path's first edge
     * holds the exit guards of the station it leaves at GREEN, with its own accessories (OB-340).
     *
     * Not synchronized, for the reason `getActiveAccs` gives.
     *
     * @return each accessory held, and the setting it is held at
     */
    public Map<Accessory, Boolean> getActiveAccSettings()
    {
        Map<Accessory, Boolean> activeAccessories = new HashMap<>();

        // EVERY TRAIN UNDERWAY, INCLUDING ONE STILL BEING DISPATCHED (RC-A10).  activeLocomotives alone misses a
        // locomotive until configureAndLockPath returns (several seconds on a six-edge path, its turnouts and signals
        // already thrown), so an s88-triggered route could set an accessory the dispatch had just set.  takingPath
        // carries the path from the claim; trainsUnderway counts the same union for the max-trains cap.
        // activeLocomotives wins where both hold an entry: it is the fuller answer, and the one the milestone and
        // clearing bookkeeping is keyed to.
        //
        // THE CLAIMS READ FIRST (RSA4-C3), for the reason `pathHeldBy` gives: the hand-over writes the journey and then
        // takes the claim away, so a train missing from the claims is among the journeys read after them; read the
        // other way round, a train handing over between the two reads would be in neither.
        Map<Locomotive, List<Edge>> underway = new LinkedHashMap<>(this.takingPath);

        for (Map.Entry<Locomotive, List<Edge>> journey : this.activeLocomotives.entrySet())
        {
            if (journey.getValue() != null) underway.put(journey.getKey(), journey.getValue());
        }

        for (Map.Entry<Locomotive, List<Edge>> active : underway.entrySet())
        {
            Set<Edge> cleared = this.clearedEdges.get(active.getKey());

            for (Edge e : active.getValue())
            {
                // Only the part of the path the train has not finished with (see `clearedEdges`).  Two tests, because
                // neither implies the other:
                //   isLockHeld - the edge was given up entirely.  Only happens with atomicRoutes off.
                //   cleared    - the train's tail has gone past it, in both modes: with atomicRoutes on the lock is
                //                held for the whole run by design, so the lock alone never says the train has moved
                //                on.  The tail bookkeeping in executePath says so, the same computation that decides
                //                when an edge is safe to unlock.
                // Not the train's position alone: an edge is cleared only once a train's LENGTH has gone past its END,
                // so a turnout under the middle of a train is still refused.  Where no lengths are configured an edge
                // is clear the moment the front passes, the same trade as for unlocking.  clearedEdges is populated
                // only by tailHasProvablyPassed (WK-B1), the one predicate, no guess, that unlocking uses.
                if (!e.isLockHeld(active.getKey())) continue;

                if (cleared != null && cleared.contains(e)) continue;

                for (Map.Entry<String, Accessory.accessorySetting> acc : e.getConfigCommands().entrySet())
                {
                    Accessory a = this.control.getAccessoryByName(acc.getKey());

                    if (a == null) continue;

                    Boolean setting = Accessory.isThrow(acc.getValue());

                    // Two routes asking different settings of one accessory: neither is the one it is held at
                    if (activeAccessories.containsKey(a) && !setting.equals(activeAccessories.get(a)))
                    {
                        activeAccessories.put(a, null);
                    }
                    else if (!activeAccessories.containsKey(a))
                    {
                        activeAccessories.put(a, setting);
                    }
                }

                // AND THE EXIT GUARDS ITS FIRST EDGE SETS GREEN, held with that edge's own accessories (OB-340)
                if (e == active.getValue().get(0))
                {
                    for (Accessory guard : this.exitGuardsLeftBy(e))
                    {
                        activeAccessories.put(guard, activeAccessories.containsKey(guard)
                            && !Boolean.FALSE.equals(activeAccessories.get(guard)) ? null : Boolean.FALSE);
                    }
                }
            }
        }
        
        return activeAccessories;
    }

    /**
     * The exit guards of the station a path's first edge leaves, which that edge sets GREEN with its own accessories and
     * holds as it holds them (OB-340): each resolved to its accessory, and left out where the edge commands it itself.
     *
     * @param first the first edge of a path
     * @return the guards
     */
    private Set<Accessory> exitGuardsLeftBy(Edge first)
    {
        Set<Accessory> guards = new LinkedHashSet<>();

        for (String name : first.getStart().getProtectingSignals())
        {
            Accessory guard = this.control.getAccessoryByName(name);

            if (guard != null) guards.add(guard);
        }

        for (String name : first.getConfigCommands().keySet())
        {
            guards.remove(this.control.getAccessoryByName(name));
        }

        return guards;
    }
    
    /**
     * To be called externally when a locomotive is deleted from the database
     * @param l
     */
    synchronized public void locDeleted(Locomotive l)
    {
        if (l == null) return;

        this.locomotivesToRun.remove(l);
        this.activeLocomotives.remove(l);

        // And the reversal answer its run was dispatched with (OB-314), read only beside its path - gone with it
        this.runPolicies.remove(l);
        this.locomotiveMilestones.remove(l);
        this.clearedEdges.remove(l);
        this.releasedEarly.remove(l);

        // The claim on a path slot: left behind it lowers the cap on how many trains may run for the rest of the session, a leak that makes
        // the railway quieter and quieter with nothing to say why.
        this.takingPath.remove(l);

        // And its mark as sent by hand (BCR-C1), which its journey also takes away when it ends.
        this.sentByHand.remove(l);

        // The sensor this locomotive was said to be heading for: a route condition asking "has it reached that sensor yet" waits on this
        // entry, and one left behind is one nothing will ever clear.  updatePendingS88 notifies, so anybody already waiting is let go.
        updatePendingS88(l, null);

        // And every timetable entry that would run it: TimetablePath holds the locomotive itself, so executing the timetable afterwards drives
        // something that is not in the database, and the entry is written back out on every save, naming a locomotive the next load cannot resolve.
        this.timetable.removeIf(entry -> l.equals(entry.getLoc()));

        // The record that this train is standing where the railway turned it, which is keyed by NAME (RTX-C3).
        // Left behind, a locomotive later given the same name inherits it and is believed to be facing the way the
        // deleted one was left - and the entry is in no file, so nothing else ever clears it.
        if (l.getName() != null) this.reversedOnArrival.remove(l.getName());

        // Points hold their own references, and nothing else was clearing them: a deleted locomotive stayed excluded forever, and its name kept
        // being written into the exported JSON as an exclusion for a locomotive that no longer exists.
        for (Point p : this.getPoints())
        {
            p.removeExcludedLoc(l);

            // The home assignment: the Point holds the locomotive, so this is identity like the exclusion above it.
            if (l.equals(p.getHomeLoc())) p.setHomeLoc(null);

            // And the locomotive STANDING here (UR-3): left behind, the square stays occupied by a train that does not
            // exist, and Point.toJSON writes its name back out, so the next load reports a locomotive not in the
            // database and invalidates the WHOLE configuration.
            if (l.equals(p.getCurrentLocomotive())) p.setLocomotive(null);
        }

        // Releases the home claim too: otherwise the station stays claimed by a locomotive that no longer exists, and nothing placed there
        // afterwards could ever have a home of its own (the exclusions above outlived their locomotive the same way until IND-M4).
        this.homeStations.remove(l);
    }

    /**
     * Records where a locomotive belongs, the first time it is placed on this graph.
     *
     * First claim wins, and a station can be claimed only once, so the map stays injective - two
     * locomotives can never want the same station, which would make returning home unsatisfiable by
     * construction.
     *
     * A locomotive placed on an already-claimed station therefore gets no home. That is deliberate:
     * it becomes a free agent, which the staging planner may move anywhere free but never has to place
     * exactly. Free agents are also the spare capacity that lets a planner break a cyclic dependency.
     *
     * Called when the graph is loaded and when a locomotive is placed by hand - never on arrival at
     * the end of a path, which is a locomotive moving, not appearing.
     *
     * @param l
     * @param p
     */
    private void claimHome(Locomotive l, Point p)
    {
        if (l == null || p == null) return;

        // A SPLIT SQUARE MAY HOLD A POSITIONAL HOME, and only a positional one (MT-165).  Refusing one (LD-8) would cost
        // the whole feature on a railway whose main-line platforms carry a block: a train sitting on one at startup got
        // no home and Return Home stayed dark.  Adam: "unless there is an explicit home, the home should be where the
        // train started at startup."
        //
        // The two doors are not the same question.  An ASSIGNMENT is a person naming a station, and there is no way to
        // know which copy they meant (whyNotAHome still refuses it); a POSITIONAL home is the graph noticing where a
        // train IS, and the copy is under the wheels.  The ambiguity left is a train returning on the far copy of its
        // own platform, which HomeStaging.atHome answers as every rule about a split square does: the copies are one
        // piece of track, so a train on any of them is home.

        // Already has a home: keep it.  Moving a locomotive by hand does not re-home it.
        if (this.homeStations.containsKey(l)) return;

        // Station already spoken for: this locomotive is a free agent.  The SQUARE, not the Point (MT-165): once a split
        // square could hold a positional home, a train parked on the far copy of a platform another is homed on would
        // be homed on one piece of track too, which nothing can satisfy (`sharesSection` sees two active Points on one
        // sensor and Return Home answers IMPOSSIBLE for the rest of the session).
        for (Point taken : this.homeStations.values())
        {
            if (taken.isSamePlaceAs(p)) return;
        }

        this.homeStations.put(l, p);
    }

    /**
     * Recomputes every home from the assignments and the current placements.
     *
     * One method for both entrances - loading a file and editing an assignment - so the two cannot drift into deriving homes differently.
     * Assignments win, and are applied first: a station assigned to a locomotive is that locomotive's home whether or not it is standing
     * there, and whether or not it is on the graph at all.  Whatever is left falls back to the original rule, the station a locomotive is
     * standing on; with no assignments anywhere, that is the only thing that runs.  A Point holds the LOCOMOTIVE, so there is no dangling
     * assignment here; reading a name out of a file is the one place that can meet one, and parseAuto reports it there.
     */
    synchronized public void rebuildHomeStations()
    {
        this.homeStations.clear();

        for (Point p : this.points.values())
        {
            Locomotive l = p.getHomeLoc();

            if (l == null) continue;

            if (this.homeStations.containsKey(l))
            {
                // Dropped, for the reason a dangling name is dropped: it can never be honoured.  One locomotive has one
                // station, and keeping the loser would re-warn on every load and be written back out on every save.  A
                // hand-edited file is the way in, now that AutonomySession.setHome sweeps (TD-8).
                this.control.logf("autolayout.warnHomeLocomotiveAssignedTwice", l.getName(), p.getName());
                p.setHomeLoc(null);
                continue;
            }

            // AND THE SQUARE RULE (SVN-B13): the check above asks whether this LOCOMOTIVE already has a home;
            // `claimHome` asks whether this SQUARE is already somebody's home (two locomotives homed on two copies of
            // one platform is a state nothing can satisfy).  `parseAuto` writes a home onto the Point itself, so a file
            // carrying two different locomotives on two copies of one square would reach `homeStations` with both
            // intact (DAY-A1, the one door a person cannot be warned at).
            //
            // **WHAT THIS CATCHES, AND WHAT IT DOES NOT (V31-C4).**  `isSamePlaceAs` is the BLOCK, which
            // `AutonomyBuilder` emits only where one square became several Points, so this matches `claimHome`
            // exactly: two homes on two copies of ONE square.  It does NOT catch two homes on two different squares
            // that share a detection section (`HomeStaging.sharesSection` keys on the S88), which can still make Return
            // Home answer IMPOSSIBLE naming both.  That sensor-versus-block question is open with Adam under MT-187;
            // widening this rule to the sensor would settle it by the back door, and wrongly.
            //
            // Dropped with the same warning as the case above.  WHICH copy is named is the FIRST found; which of the
            // two ASSIGNMENTS survives is decided by iteration order over `points.values()`.
            Point sameSquare = null;

            for (Point taken : this.homeStations.values())
            {
                if (taken.isSamePlaceAs(p))
                {
                    sameSquare = taken;
                    break;
                }
            }

            if (sameSquare != null)
            {
                this.control.logf("autolayout.warnHomeSquareAssignedTwice",
                    p.getName(), sameSquare.getName(), l.getName());

                p.setHomeLoc(null);
                continue;
            }

            this.homeStations.put(l, p);
        }

        // The original rule, for everything the assignments did not speak for.  claimHome refuses a
        // locomotive that already has one and a station already spoken for, so assignments stand.
        for (Point p : this.points.values())
        {
            if (p.getCurrentLocomotive() != null) this.claimHome(p.getCurrentLocomotive(), p);
        }
    }


    /**
     * Assigns a station to a locomotive by name, or clears the assignment when name is null.
     *
     * @param pointName
     * @param locName
     * @throws java.lang.Exception
     */
    synchronized public void setHomeLocomotive(String pointName, String locName) throws Exception
    {
        Point p = this.getPoint(pointName);

        if (p == null)
        {
            throw new Exception(I18n.f("autolayout.errorPointDoesNotExist", pointName));
        }

        // Resolved once, here, which is the boundary: the caller says a name because a menu and a file
        // both speak in names, and everything past this line is the locomotive.
        Locomotive loc = locName == null || locName.trim().isEmpty()
            ? null : this.control.getLocByName(locName);

        if (locName != null && !locName.trim().isEmpty() && loc == null)
        {
            throw new Exception(I18n.f("autolayout.warnHomeLocomotiveNotInDatabase", locName,
                p.getName()));
        }

        // NOTHING IS REFUSED HERE (D24-C4).  A refusal at the MODEL door could be walked past by two other doors
        // (`parseAuto` calling `Point.setHomeLoc`, and the positional default in `claimHome`).  A square is one logical
        // point however many arrival sides it has (Adam: "the home should just be the logical point, and the direction
        // is wherever the locomotive was facing when it started moving."), so there is nothing ambiguous to refuse.
        // "No train can come to rest here" is a warning the menu gives and something Return Home reports, not a state
        // the model forbids (testAnImpossibleAssignmentIsExactlyWhatReturnHomeWouldRefuse depends on making one).

        // One station per locomotive: assigning it somewhere new gives up wherever it was assigned
        // before, or two stations would be waiting for the same train and neither could be satisfied.
        if (loc != null)
        {
            for (Point other : this.points.values())
            {
                if (other != p && loc.equals(other.getHomeLoc())) other.setHomeLoc(null);
            }
        }

        // AND ONE HOME PER PLATFORM, ACROSS ITS COPIES: a Point holds one homeLoc, so naming a second locomotive at the
        // same Point displaces the first, but two copies of one square are two fields, so one locomotive at each would
        // leave both homed on one piece of track, which nothing can satisfy (DAY-A1 at the positional door).  Displaced
        // rather than refused: the operator is saying this train lives here now.
        if (loc != null)
        {
            for (Point other : this.points.values())
            {
                if (other != p && other.isSamePlaceAs(p)) other.setHomeLoc(null);
            }
        }

        p.setHomeLoc(loc);

        // THE COPY IS THE FACING, and the operator chose this one (OB-282; Adam: *"it should accomplish the facing"*).  A home named here is a
        // home on this copy; Return Home brings its locomotive back to a copy of the square facing the way this one faces, not its turning twin
        // (TDY-B1).
        p.setHomeFacingFixed(loc != null);

        this.rebuildHomeStations();
    }

    /**
     * Drops every assignment, returning the graph to deriving homes purely from where trains stand.
     */
    synchronized public void clearHomeLocomotives()
    {
        for (Point p : this.points.values())
        {
            p.setHomeLoc(null);
        }

        this.rebuildHomeStations();
    }

    /**
     * Whether any station has been assigned, i.e. whether homes are still purely positional.
     * @return
     */
    synchronized public boolean hasHomeLocomotives()
    {
        for (Point p : this.points.values())
        {
            if (p.getHomeLoc() != null) return true;
        }

        return false;
    }

    /**
     * The station this locomotive belongs at, or null if it has none
     * @param l
     * @return
     */
    synchronized public Point getHomeStation(Locomotive l)
    {
        if (l == null) return null;

        return this.homeStations.get(l);
    }

    /**
     * Every locomotive that has a home, and where, as it stands right now.
     *
     * A copy, not a view: this map is rebuilt wholesale and also written by hand placement, station deletion and
     * locomotive deletion, none on the thread that reads it, and a view would let the planner walk the live map while
     * another thread cleared it.  Copying under the monitor makes every reader safe without asking each writer to know
     * about the readers.
     *
     * @return
     */
    synchronized public Map<Locomotive, Point> getHomeStations()
    {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.homeStations));
    }
    
    /**
     * Gets the destination of this active locomotive
     * @param locomotive
     * @return 
     */
    public Point getDestination(Locomotive locomotive)
    {
        if (locomotive == null) return null;

        List<Edge> path = getActiveLocomotives().get(locomotive);

        if (path == null || path.isEmpty())
        {
            return null;
        }

        return path.get(path.size() - 1).getEnd();
    }
    
    /**
     * Gets the starting point of this active locomotive
     * @param locomotive
     * @return 
     */
    public Point getStart(Locomotive locomotive)
    {
        if (locomotive == null) return null;

        List<Edge> path = getActiveLocomotives().get(locomotive);

        if (path == null || path.isEmpty())
        {
            return null;
        }

        return path.get(0).getStart();
    }
    
    /**
     * Gets the destination of this active locomotive
     * @param locomotive
     * @return 
     */
    public Set<Point> getPointsInActivePath(Locomotive locomotive)
    {
        Set<Point> output = new HashSet<>();

        if (locomotive == null) return output;

        List<Edge> path = getActiveLocomotives().get(locomotive);

        if (path != null && !path.isEmpty())
        {
            for (Edge e : path)
            {
                output.add(e.getStart());
                output.add(e.getEnd());
            }
        }
        
        return output;
    }
           
    /**
     * Gets locomotives currently running
     * @return  
     */
    public Map<Locomotive, List<Edge>> getActiveLocomotives()
    {
        return this.activeLocomotives;
    }
    
    /**
     * Gets milestones already reached by a locomotive
     * @param loc
     * @return  
     */
    public List<Point> getReachedMilestones(Locomotive loc)
    {
        if (loc == null) return null;

        return this.locomotiveMilestones.get(loc);
    }
    
    /**
     * Where each train under way is last known to be (RLV7-B1, RLV7-C1): the last station on its path whose sensor it
     * has tripped, or where it set off.  A locked path holds every point on it for its train, so read point by point a
     * train under way stands in several places at once, which a configuration refuses as one locomotive in two places.
     * A reload that stops the trains releases nothing, so this is what a carry across it has to read.
     *
     * - A point with no sensor is never a position: `executePath` passes one without waiting.
     * - Nor is a point that is no station: nothing can put a train back on one (`moveLocomotive`).
     * - Where another train now stands on that point (atomic routes off: a release clears the track behind a train,
     *   and another can stop there while it is still under way), the first station ahead on its path that it still
     *   holds, its destination at worst (RLV9-C4); a train only passing through the point gives way to the one kept
     *   there (RLV8-C4).  No two trains are kept on one square, in whatever order the map yields them: `keptApart`
     *   says how (RLV10-C2, RLV11-C1, RLV11-C2, RLV12-C1, RLV12-C2).
     *
     * No rule can know where a train stopped between two sensors is; this one keeps the last place it was known to be
     * held, as 2.8.2 keeps a train after a failed trip.
     *
     * Read without the railway's monitor, which the event thread must not take: the maps are concurrent, and a train setting off or
     * arriving as this runs is read on one side of it or the other.
     *
     * @return each train under way, or still locking its path, against that point; a copy
     */
    public Map<Locomotive, Point> getLastPointsReached()
    {
        Map<Locomotive, Point> out = new HashMap<>();

        // Still locking its path: points reserved, none reached
        for (Entry<Locomotive, List<Edge>> taking : this.takingPath.entrySet())
        {
            if (taking.getValue() != null && !taking.getValue().isEmpty())
            {
                out.put(taking.getKey(), taking.getValue().get(0).getStart());
            }
        }

        for (Entry<Locomotive, List<Edge>> running : this.activeLocomotives.entrySet())
        {
            Point known = this.lastKnownPoint(this.locomotiveMilestones.get(running.getKey()), running.getValue());

            if (known != null) out.put(running.getKey(), known);
        }

        // No two on one square, nor on one a standing train stands on (see `keptApart`)
        return this.keptApart(out);
    }

    /**
     * Where each train under way is kept, no two on one square and none on a square a standing train stands on - see
     * `getLastPointsReached`.  A square is one place whichever copy of it a train is on (`Point.isSamePlaceAs`): kept
     * by point, two trains could share a square and the carry would take one of them off the railway (RLV12-C2).
     *
     * - A standing train's square is its own: a train under way kept there takes a station ahead (RLV9-C4).
     * - Otherwise a train under way keeps the point it was last seen at - the one that point records first, then the
     *   others by name, so the answer does not follow a map's order (RLV10-C2).  A train only passing through a point is
     *   kept elsewhere and does not count (RLV8-C4).
     * - A train that cannot keep its point takes the first station ahead on its path that it holds and nobody is kept at;
     *   failing that, one another train is kept at, where that one can itself be moved on - to a free station ahead, or to
     *   one a third is kept at that can be moved on, and so along the chain (RLV11-C1, RLV12-C1).  Each step moves a
     *   train to a station ahead that it holds, so the walk ends.  So a train bound for the station another was last seen
     *   at takes it, and the other goes on; and of two trains last seen at one station nobody stands on, one keeps it and
     *   the other goes on (RLV11-C2).
     * - With nothing left, a train stays at its point, as it always did.
     *
     * @param read each train under way against the point it was last seen at
     * @return each against the point it is kept at
     */
    private Map<Locomotive, Point> keptApart(Map<Locomotive, Point> read)
    {
        Set<Object> standing = new HashSet<>();

        for (Point p : this.getPoints())
        {
            Locomotive there = p.getCurrentLocomotive();

            if (there != null && !read.containsKey(there)) standing.add(placeOf(p));
        }

        List<Locomotive> order = new ArrayList<>(read.keySet());

        order.sort((x, y) ->
        {
            boolean xOwn = read.get(x).getCurrentLocomotive() == x;
            boolean yOwn = read.get(y).getCurrentLocomotive() == y;

            if (xOwn != yOwn) return xOwn ? -1 : 1;

            return String.valueOf(x.getName()).compareTo(String.valueOf(y.getName()));
        });

        Map<Locomotive, Point> out = new HashMap<>();
        Map<Object, Locomotive> keptThere = new HashMap<>();
        List<Locomotive> goingOn = new ArrayList<>();

        for (Locomotive loc : order)
        {
            Object place = placeOf(read.get(loc));

            if (standing.contains(place) || keptThere.containsKey(place))
            {
                goingOn.add(loc);
            }
            else
            {
                out.put(loc, read.get(loc));
                keptThere.put(place, loc);
            }
        }

        for (Locomotive loc : goingOn)
        {
            // With nothing left, it stays at its point
            if (!this.movedOn(loc, read.get(loc), standing, keptThere, out, new HashSet<Locomotive>()))
            {
                out.put(loc, read.get(loc));
            }
        }

        return out;
    }

    /**
     * Moves a train on to a station ahead on its path that it holds: a free one, or one another train is kept at that can
     * itself be moved on, along the chain (RLV12-C1).  Nothing is changed unless the whole chain moves.
     *
     * @param loc the train
     * @param from the point it is kept at, or was last seen at
     * @param standing the squares standing trains stand on
     * @param keptThere each square against the train kept on it
     * @param out each train against the point it is kept at
     * @param moving the trains this chain is already moving, so that it cannot come round to one of them
     * @return whether it was moved on
     */
    private boolean movedOn(Locomotive loc, Point from, Set<Object> standing, Map<Object, Locomotive> keptThere,
        Map<Locomotive, Point> out, Set<Locomotive> moving)
    {
        if (!moving.add(loc)) return false;

        List<Point> ahead = this.stationsAheadItHolds(loc, from);

        Point chosen = null;

        for (Point station : ahead)
        {
            Object place = placeOf(station);

            if (chosen == null && !standing.contains(place) && !keptThere.containsKey(place)) chosen = station;
        }

        // A STATION AHEAD ANOTHER IS KEPT AT, where that one can be moved on in its turn
        for (Point station : chosen != null ? Collections.<Point>emptyList() : ahead)
        {
            Object place = placeOf(station);
            Locomotive other = keptThere.get(place);

            if (chosen == null && other != null && !standing.contains(place)
                && this.movedOn(other, out.get(other), standing, keptThere, out, moving))
            {
                chosen = station;
            }
        }

        if (chosen == null) return false;

        Point was = out.get(loc);

        if (was != null && keptThere.get(placeOf(was)) == loc) keptThere.remove(placeOf(was));

        out.put(loc, chosen);
        keptThere.put(placeOf(chosen), loc);

        return true;
    }

    /**
     * The square a point is part of, for `keptApart`: the block its copies share, or the point itself where it has none.
     *
     * @param point the point
     * @return its square
     */
    private static Object placeOf(Point point)
    {
        return point.getBlock() != null ? "block:" + point.getBlock() : point;
    }

    /**
     * The stations after this point on the path a train is under way on, or still locking, that it still holds, in path
     * order - its destination last (RLV9-C4).
     *
     * @param loc the train
     * @param from the point it was kept at
     * @return the stations, empty where it holds none ahead
     */
    private List<Point> stationsAheadItHolds(Locomotive loc, Point from)
    {
        List<Point> ahead = new ArrayList<>();
        List<Edge> path = this.pathHeldBy(loc);

        if (path == null) return ahead;

        boolean past = false;

        for (Edge e : path)
        {
            if (e.getStart() == from) past = true;

            if (!past) continue;

            Point end = e.getEnd();

            if (end.getCurrentLocomotive() == loc && (end.isDestination() || this.isABarredCopyOfAStation(end)))
            {
                ahead.add(end);
            }
        }

        return ahead;
    }

    /**
     * The last of these milestones that is a station whose sensor was tripped - its start not counted - or the start.
     *
     * @param milestones the points reached, in order, its start first; may be null
     * @param path the path; may be null
     * @return the point, or null where there is neither
     */
    private Point lastKnownPoint(List<Point> milestones, List<Edge> path)
    {
        if (milestones != null && !milestones.isEmpty())
        {
            for (int i = milestones.size() - 1; i > 0; i--)
            {
                Point reached = milestones.get(i);

                if (reached.hasS88() && (reached.isDestination() || this.isABarredCopyOfAStation(reached)))
                {
                    return reached;
                }
            }

            return milestones.get(0);
        }

        return path == null || path.isEmpty() ? null : path.get(0).getStart();
    }

    /**
     * Gets the S88 of the latest milestone reached by a locomotive
     * @param loc the locomotive to check.
     * @return the S88 sensor of the latest milestone, or null if none found.
     */
    public String getLatestMilestoneS88(Locomotive loc)
    {
       if (loc == null) return null;

       List<Point> milestones = this.locomotiveMilestones.get(loc);

       if (milestones == null || milestones.isEmpty()) return null;

       // Iterate backwards through the list
       for (int i = milestones.size() - 1; i >= 0; i--)
       {
            Point point = milestones.get(i);

            if (point.hasS88())
            {
                return point.getS88();
            }
       }

       return null;
    }
    
    /**
     * Retires every Layout there is, so that none is the current one and a path's thread on any of them stops its train
     * at its next check (RLV8-A1).
     *
     * Every load retires the railway it replaces, by building a newer one.  Unload replaces it with nothing, so the
     * door that drops the railway retires it itself.
     */
    public static void retireEveryLayout()
    {
        synchronized (VERSIONS)
        {
            Layout.layoutVersion += 1;
        }

        // EVERY JOURNEY OF A RAILWAY THIS RETIRES ASKS AGAIN whether to go on waiting (RSA-B1): the reload's and Unload's Yes stop the trains
        // between two sensors, and a journey left waiting on a sensor its train will not reach would stop whichever train next occupied it.
        Locomotive.wakeEveryWait();
    }

    /**
     * Makes this the current Layout, retiring every other (RLV8-C3): a railway loaded is the current one whatever was
     * built while it was parsed.  The parse builds this first and the model takes it after, so a lookup landing between
     * would build a newer, empty railway and retire the one loaded from the start.
     */
    public void makeCurrent()
    {
        synchronized (VERSIONS)
        {
            Layout.layoutVersion += 1;
            this.version = Layout.layoutVersion;
        }

        // Every journey of a railway this retires asks again whether to go on waiting, as `retireEveryLayout` says.
        Locomotive.wakeEveryWait();
    }

    /**
     * Whether this Layout is still the one in use, or has been superseded by a newer one.
     *
     * Asking whether this instance is the newest has no window to land in: the answer does not depend on when it is
     * asked, unlike a version captured when a run began (after configureAndLockPath, which waits seconds for the
     * Central Station).
     *
     * @return 
     */
    public boolean isCurrentLayout()
    {
        return this.version == Layout.layoutVersion;
    }

    /**
     * Whether a staging flow currently owns this Layout, planning or executing.
     * @return
     */
    public boolean isStagingInProgress()
    {
        return this.stagingInProgress;
    }

    /**
     * Marks this Layout as owned by a staging flow, or releases it.
     * @param stagingInProgress
     */
    public void setStagingInProgress(boolean stagingInProgress)
    {
        this.stagingInProgress = stagingInProgress;
    }

    /**
     * Marks the layout state as invalid
     * Used to show error message in UI
     */
    public void invalidate()
    {
        this.isValid = false;

        if (this.invalidReason == null) this.invalidReason = NO_REASON_RECORDED;
    }

    /** What `invalidReason` says where the layout was invalidated without one, until a reason is given. */
    private static final String NO_REASON_RECORDED = "(no reason was recorded)";

    /**
     * Why this layout was invalidated, kept apart from lastError: lastError is written by every path that fails for any
     * reason, so by the time somebody reads "the configuration is invalid" it holds whatever went wrong most recently,
     * not the invalidation.
     */
    private String invalidReason;
    
    /**
     * Marks the layout state as invalid
     * Prints and logs the error
     * @param message
     */
    public void invalidate(String message)
    {
        this.isValid = false;

        // EVERY REASON, FIRST FIRST (OB-266): a layout refused once for a cause is often refused again for what follows
        // from it, and keeping only the latest would name a consequence.
        if (this.invalidReason == null || NO_REASON_RECORDED.equals(this.invalidReason))
        {
            this.invalidReason = message;
        }
        else if (!java.util.Arrays.asList(this.invalidReason.split(NL_REASONS)).contains(message))
        {
            this.invalidReason = this.invalidReason + NL_REASONS + message;
        }

        Layout.lastError = message;
        this.control.log(message);
    }

    /** Between two reasons in `invalidReason`. */
    private static final String NL_REASONS = "\n";

    /**
     * @return every reason this layout was invalidated for, in the order they were found, one per line - or null while
     *     it is still valid
     */
    public String getInvalidReason()
    {
        return this.invalidReason;
    }
    
    /**
     * Returns validity status
     * @return 
     */
    public boolean isValid()
    {
        return this.isValid;
    }
    
    /**
     * Enables/disables simulation mode 
     * @param simulate
     * @throws Exception 
     */
    public void setSimulate(boolean simulate) throws Exception
    {
        if (this.isRunning())
        {
            throw new Exception(
                I18n.f("autolayout.errorSimulationModeNoTrains")
            );
        }

        if ((!control.isDebug() || control.getNetworkCommState()) && simulate)
        {
            throw new Exception(
                I18n.f("autolayout.errorSimulationModeDebugOnly")
            );
        }

        this.simulate = simulate;

        if (simulate)
        {
            control.logf("autolayout.warningSimulationModeEnabled");
        }
    }

    public double getPreArrivalSpeedReduction()
    {
        return preArrivalSpeedReduction;
    }

    public int getMaxLocInactiveSeconds()
    {
        return maxLocInactiveSeconds;
    }

    /**
     * Per-sensor announcement epochs for simulation mode, so a stale clear-behind cannot destroy a
     * later announcement on the SAME sensor.
     *
     * The simulation announces each path point by setting its s88, then spawns a detached thread to clear it behind the
     * train after a delay.  When two consecutive path points share one sensor (BottomMainPost and
     * TunnelLongParkReverse both report 2013) a stale clear could land after the next point's announcement and leave
     * the waiter blocked on a sensor nothing would set again: a permanent, silent stall.  Real hardware is immune,
     * because a physical sensor spanning both points stays held.
     *
     * The guard: each announcement bumps the sensor's epoch under the epoch's own lock; each clear re-checks under the
     * same lock and stands down if a later announcement re-armed the sensor.  The last occupant still clears, so
     * unshared sensors pulse and clear as before.
     */
    private final Map<String, AtomicLong> simFeedbackEpochs = new ConcurrentHashMap<>();

    /** Announces a point's sensor in simulation and returns the epoch the clear must present. */
    private long simAnnounce(String s88)
    {
        AtomicLong epoch = this.simFeedbackEpochs.computeIfAbsent(s88, k -> new AtomicLong());

        synchronized (epoch)
        {
            long stamp = epoch.incrementAndGet();
            this.control.setFeedbackState(s88, true);
            return stamp;
        }
    }

    /** Clears a sensor behind the train, unless a later announcement has re-armed it. */
    private void simClearBehind(String s88, long stamp)
    {
        // The clear is spawned detached with a delay of up to maxDelay SECONDS, so a run can end, and this Layout be replaced by a reload,
        // while clears are still pending.  The epoch map is per instance, so an orphan's clear would consult a map the NEW run's
        // announcements never bump, pass its own stand-down check, and clear a sensor the new run is waiting on.  Only the clear side needs
        // the fence: a run cannot span a reload, so an announcement can never come from an orphan.
        if (!this.isCurrentLayout())
        {
            return;
        }

        AtomicLong epoch = this.simFeedbackEpochs.get(s88);

        synchronized (epoch)
        {
            if (epoch.get() == stamp)
            {
                this.control.setFeedbackState(s88, false);
            }
        }
    }

    /**
     * Returns whether simulation mode is enabled
     * @return 
     */
    public boolean isSimulate()
    {
        return this.simulate;
    }

    /**
     * Why the last timetable run stopped where it did, or null.
     *
     * @return the reason
     */
    public String whyTheTimetableStopped()
    {
        return this.timetableStoppedBecause;
    }
    
    /**
     * Returns auto or manual running status
     * @return 
     */
    public boolean isRunning()
    {
        return this.running || !this.getActiveLocomotives().isEmpty()
            || this.locomotiveThreads.get() > 0 || this.timetableEntries.get() > 0;
    }
    
    /**
     * Returns auto running status
     * @return 
     */
    public boolean isAutoRunning()
    {
        return this.running;
    }

    /**
     * Whether FULL autonomy is running: the railway choosing its own destinations, with nobody having
     * said where a train should go.
     *
     * **Not the same question as `isAutoRunning`, and the difference is a tier.**  `executeTimetableInternal` sets
     * `running` as well, so every rule fenced behind `isAutoRunning` also applies to a timetable, including the Return
     * Home staging run, which behaviour.md 1 puts with MANUAL on where a train may be sent.  A timetable is a route
     * somebody chose; this is for a rule that only makes sense against a railway picking for itself.
     *
     * **Nothing asks it today.**  Its one caller was the FR-001 occupancy restriction in `isPathClear`, and Adam took
     * the fence off that: *"enforce the occupancy ruling in all modes and then rely on isPathClear."*  Kept because
     * the distinction is real and hard to get right, so the next rule that needs "full autonomy" should find it
     * spelled correctly (`docs/reference/behaviour.md` section 1 says the same).
     *
     * @return true while autonomy is dispatching and no timetable is
     */
    public boolean isFullAutonomyRunning()
    {
        return this.running && !this.timetableExecuting;
    }
    
    /**
     * How many times the operator has stopped every train where it stood (RSA-C1): counted by
     * `stopEveryTrainWhereItIs`, read by each journey when it is dispatched and again around every speed it writes.
     */
    private final java.util.concurrent.atomic.AtomicInteger stopsOrdered = new java.util.concurrent.atomic.AtomicInteger();

    /** Whether this railway has been retired: what a journey's sensor waits give up on (RSA-B1). */
    private final java.util.function.BooleanSupplier retiredRailway = () -> !this.isCurrentLayout();

    /**
     * Stops every train where it is, now: the reload's and Unload's Yes (RSA-C1).
     *
     * `stopLocomotives` is the graceful stop, which lets every journey finish.  This one also counts a stop, which
     * every journey carries from where it was chosen and asks before and after each speed it writes (`drive`): a
     * journey chosen before it sends nothing, a train still claiming its route is given it back unrun, one reaching
     * its next sensor is not given its speed back, and one set off at the same instant is put back to a stand.  Then
     * every train under way stops.
     *
     * **`running` cleared first and the stop counted second** (RSA2-C1, RSA2-C2): autonomy asks `running` once its
     * choice is made, so a thread that reads the count after it has been counted reads `running` cleared; and a
     * timetable sets `running` and then asks the count.  The two statements are held together against a timetable's
     * two by `stopLock`, which no journey takes (RSA4-C2), not the journeys' lock nor `synchronized` on the railway
     * (RSA3-C4): the event thread calls it, and nothing there may wait on a lock a journey holds while it waits for
     * this railway's monitor.
     */
    public void stopEveryTrainWhereItIs()
    {
        // NOT THE JOURNEYS' LOCK (RSA3-C4): an arrival holds it while it waits for the railway's monitor, which a
        // dispatch holds while it throws a route's switches, so taking it on the event thread would freeze the window
        // and let every train run on.  `stopLock` INSTEAD (RSA4-C2), which only this and a timetable starting take: a
        // timetable starting then sees both statements or neither (`executeTimetableInternal`).
        synchronized (this.stopLock)
        {
            stopLocomotives();

            this.stopsOrdered.incrementAndGet();
        }

        for (Locomotive active : this.activeLocomotives.keySet()) active.setSpeed(0);
    }

    /**
     * How many times the operator has stopped every train where it stood - read where a journey is chosen, and carried
     * to it (RSA2-C2): a journey chosen before a stop sends nothing after it.
     *
     * @return the stops counted so far
     */
    public int stopsOrdered()
    {
        return this.stopsOrdered.get();
    }

    /**
     * The train whose journey is still waiting on this sensor, other than the one asking, or null (RSA2-B1).  A journey
     * under way waits on every sensor of its route it has not reached, and one still claiming its route on all of them;
     * the first time the sensor is occupied ends every such wait, whichever train occupies it.  A sensor its head has
     * reached stays its own until the rail to it is given back (RSA3-C3).
     *
     * @param s88 the sensor
     * @param asking the train whose route is being asked about
     * @return the train another route over this sensor would wake, or null
     */
    private Locomotive whoseJourneyAwaits(String s88, Locomotive asking)
    {
        if (s88 == null) return null;

        // THE CLAIMS FIRST, THE JOURNEYS SECOND (RSA3-C1): the hand-over writes the journey and then takes the claim
        // away, so a train missing from the claims is among the journeys read after them.  The other order saw a train
        // handing over between the two reads in neither, and admitted a second train to the other place on its sensor.
        for (Map.Entry<Locomotive, List<Edge>> claim : this.takingPath.entrySet())
        {
            if (claim.getKey() == asking) continue;

            for (Edge e : claim.getValue())
            {
                if (s88.equals(e.getEnd().getS88())) return claim.getKey();
            }
        }

        for (Map.Entry<Locomotive, List<Edge>> journey : this.activeLocomotives.entrySet())
        {
            if (journey.getKey() == asking) continue;

            List<Point> reached = this.locomotiveMilestones.get(journey.getKey());

            Set<Edge> givenBack = this.releasedEarly.get(journey.getKey());

            for (Edge e : journey.getValue())
            {
                if (!s88.equals(e.getEnd().getS88())) continue;

                // HELD UNTIL THE RAIL TO IT IS GIVEN BACK, not only until the head reaches it (RSA3-C3): between the
                // two, only the sensor's own reading kept a second train off the other place on it, and a gap between
                // two axles reads clear.  Given back early in non-atomic mode, where the tail has provably passed; in
                // atomic mode not before the journey ends.
                boolean reachedIt = reached != null && reached.contains(e.getEnd());
                boolean released = givenBack != null && givenBack.contains(e);

                if (!reachedIt || !released) return journey.getKey();
            }
        }

        return null;
    }

    /**
     * Writes a journey's speed unless a stop has been ordered since the journey was dispatched, or its railway has been
     * retired (RSA-C1, GS-C1).  Asked again once the command has gone: a stop ordered while it was on its way wins, and
     * the train is put back to a stand.
     *
     * @param loc the train
     * @param speed the speed to write
     * @param stopsAtDispatch `stopsOrdered` as the journey read it when it was dispatched
     * @return whether the speed was written and stands
     */
    private boolean drive(Locomotive loc, int speed, int stopsAtDispatch)
    {
        if (this.stopsOrdered.get() != stopsAtDispatch || !isCurrentLayout()) return false;

        loc.setSpeed(speed);

        if (this.stopsOrdered.get() == stopsAtDispatch) return true;

        loc.setSpeed(0);

        return false;
    }

    /**
     * Gives back a route claimed for a journey that a stop has overtaken, before the train has moved (RSA-C1): the
     * track and the squares it reserved are released, and the train stands reserved where it was - as a route that
     * could not be configured is given back (`handleMisconfiguredPath`).
     *
     * @param path the route claimed
     * @param loc the train
     * @param why the log line saying why, naming the train - the stop, or the power (RSA62-C4)
     */
    private void giveBackUnrun(List<Edge> path, Locomotive loc, String why)
    {
        synchronized (this)
        {
            for (Edge e : path)
            {
                e.setUnoccupied();

                if (loc.equals(e.getEnd().getCurrentLocomotive())) e.getEnd().setLocomotive(null);
            }

            path.get(0).getStart().reserve(loc);
        }

        this.takingPath.remove(loc);

        // AND SAYS SO (RSA2-C8): a hand send answers a false with "check log"
        this.control.logf(why, loc.getName());
    }

    /**
     * Stops locomotives gracefully (i.e., at their next station for those that are running)
     */
    public void stopLocomotives()
    {
        this.running = false;

        // AND EVERY TIMETABLE ENTRY PAUSING BETWEEN REFUSALS WOKEN, to leave (RSA3-C5).  `running` is cleared before the
        // monitor is taken, and a pause asks it under the monitor before it waits, so no wake is lost.
        synchronized (this.entryPause)
        {
            this.entryPause.notifyAll();
        }
    }

    /**
     * Whether this railway's sends are waiting for the track power (GST-B1): set by whichever train finds it off first, so
     * the wait is said once however many trains wait, and cleared once the power reads on again.
     */
    private final java.util.concurrent.atomic.AtomicBoolean waitingForThePower =
        new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Waits while the track power is off, before a run chooses or sends a train (GST-B1).
     *
     * With the power cut (from the window, by the Central Station, or by a route), autonomy would go on choosing
     * journeys and setting their routes: switches commanded on a railway with no power to move them, and trains given
     * speeds that took effect all at once when the power came back.  Autonomy's loop, a timetable's entries and Return
     * Home (whose plan runs as a timetable) wait here instead: the run goes on, and sends again once the power reads
     * on.  A journey already under way is not touched.
     *
     * The flag is the last GO or STOP echo heard (`MarklinControlStation.getPowerState`), and reads on until one is
     * heard (at start-up, and always in a simulation without Echo Sent Commands), so a state nobody has reported never
     * holds a run.  A lost GO would hold it, as it already refuses Start: the window shows the power off, the log says
     * why the trains wait, and turning the power on is heard.  Instant Stop is a halt, not a power cut, and leaves the
     * flag alone (OB-251: *"Instant stop is unrelated to autonomy"*).  A hand send is not waited for here: the send
     * gate refuses it while the power is off (`TrainControlUI.whyNoTrainMayBeSent`).
     *
     * It sleeps, so it is never called holding the railway's monitor or `activeLocomotives`.  A stop (graceful, or the
     * reload's Yes) and a retired railway end it within one poll.
     *
     * @return whether it waited at all, so the caller asks again whether its run still goes before it sends anything
     */
    private boolean waitWhileThePowerIsOff()
    {
        boolean waited = false;
        boolean interrupted = false;

        while (this.running && this.isCurrentLayout() && !this.control.getPowerState())
        {
            // SAID ONCE, by whichever train finds the power off first
            if (this.waitingForThePower.compareAndSet(false, true))
            {
                this.control.logf("autolayout.infoWaitingForTrackPower");
            }

            waited = true;

            try
            {
                Thread.sleep(COMPLETION_POLL);
            }
            catch (InterruptedException e)
            {
                // Remembered and put back on the way out: re-armed here, the next sleep would throw at once, and this
                // would spin for as long as the power stayed off
                interrupted = true;
            }
        }

        if (this.control.getPowerState()) this.waitingForThePower.set(false);

        if (interrupted) Thread.currentThread().interrupt();

        return waited;
    }

    /**
     * A timetable's pause between refusals, which any stop cuts short (RSA3-C5): a pause through the operator's delays (seconds) with
     * nothing to end it would let an entry the Yes had found refusing wake only after its run was over.
     *
     * @param ms how long to pause while the run goes on
     * @throws InterruptedException if the thread is interrupted
     */
    private void pauseUnlessStopped(long ms) throws InterruptedException
    {
        final long until = System.currentTimeMillis() + ms;

        synchronized (this.entryPause)
        {
            while (this.running)
            {
                long left = until - System.currentTimeMillis();

                if (left <= 0) return;

                this.entryPause.wait(left);
            }
        }
    }

    /**
     * How long `pacedWait` pauses: the operator's delays, between the least and the most, or the poll where both are
     * zero.
     *
     * @return milliseconds
     */
    private long pacedWaitMillis()
    {
        if (this.getMinDelay() == 0 && this.getMaxDelay() == 0) return COMPLETION_POLL;

        int least = Math.abs(this.getMinDelay());
        int most = Math.abs(this.getMaxDelay());

        return (least + Math.round(Math.random() * (most - least))) * 1000L;
    }
    
    /**
     * Starts locomotives as configured
     */
    public void runLocomotives()
    {
        synchronized (this.activeLocomotives)
        {
            this.running = true;
        }

        // What actually got started, for the check at the bottom (RC-B5).
        final java.util.concurrent.atomic.AtomicInteger started =
            new java.util.concurrent.atomic.AtomicInteger();

        // NO SIGNAL SWEEP HERE (OB-166, Adam: "Signals should only be touched when a route activates"): a sweep of
        // every protecting signal from current occupancy at the start of a run also RELEASES (an empty protected
        // platform is commanded green), so a signal a route had left red would go green because some unrelated train
        // started moving.  Manual and automatic behave identically (executePath: "The same thing should happen in
        // manual operation vs auto - the same switches and signals set, and guards applied.").  What this gives up is
        // AU-B7: a train placed by hand at a protected platform keeps whatever aspect its signal already showed until a
        // route activates over it.
        
        // Start locomotives
        this.locomotivesToRun.forEach(loc ->
        {
            Point locLocation = this.getLocomotiveLocation(loc);
            
            // Optimization - avoid starting inactive locomotives
            if (locLocation != null && !locLocation.isActive())
            {
                control.logf("autolayout.warningSkipAutonomousInactiveLoc", loc.getName());
                return;
            }
            else
            {
                // Refused here, not left to runLocomotive's throw: that throw is caught below and answered by
                // invalidating the ENTIRE layout and stopping every locomotive, so one locomotive placed without a
                // speed would turn Start into "configuration invalid, must reload".  Skipping it, as an inactive
                // locomotive is skipped above, leaves every other locomotive running.  The guard in executePathInternal
                // does not help here: Start never reaches it.
                if (loc.getPreferredSpeed() < 1 || loc.getPreferredSpeed() > 100)
                {
                    control.logf("autolayout.errorFailedToRunLocomotive", loc.getName());
                    return;
                }

                control.logf("autolayout.infoAutonomousLocomotiveStarted", loc.getName());            
            }
            
            try 
            {
                runLocomotive(loc, loc.getPreferredSpeed());

                started.incrementAndGet();
            } 
            catch (Exception ex)
            {
                this.invalidate(
                    I18n.f("autolayout.errorFailedToRunLocomotive", loc.getName())
                );
                this.stopLocomotives();
            }
        });

        // NOT RUNNING IF NOTHING RUNS (RC-B5): `running` is set before a single locomotive is looked at, and both
        // skips above return without starting a thread.  Skip every locomotive and the layout would be left running
        // with nothing running, which nothing clears (announceRunFinished is only reached from a thread decrement that
        // never happens): moveLocomotive, renamePoint and setSimulate would all refuse, isPathClear would apply the
        // autonomy-only rules to hand dispatches, and the only way out is Stop.  executeTimetableInternal guards
        // exactly this; here the skips are decided one locomotive at a time, so it asks afterwards.  The speed skip
        // (MT-233) can only hit a locomotive placed on the graph AFTER the load, since parseAuto fills an unset speed
        // from defaultLocSpeed; the inactive-start-point skip is the ordinary way into this.
        //
        // AND ONLY WHEN THERE WAS SOMETHING TO START (RC-B7): an empty run list is not the case this guards (the Start
        // handler refuses one, and `setLocomotivesToRun(empty)` followed by this method is how four tests say
        // "autonomy is on" without moving anything), so clearing the flag there would change what this method MEANS
        // rather than fix the defect: every locomotive in a non-empty list being skipped.
        if (!this.locomotivesToRun.isEmpty() && started.get() == 0)
        {
            synchronized (this.activeLocomotives)
            {
                this.running = false;
            }

            control.logf("autolayout.errorNoLocomotivesCouldBeStarted");
        }
    }     
    
    /**
     * Retrieves a saved point by its name
     * @param name
     * @return 
     */
    public Point getPoint(String name)
    {
        return this.points.get(name);
    }
    
    /**
     * Retrieves a saved point by its unique id
     * @param id
     * @return 
     */
    public Point getPointById(String id)
    {
        for (Point p : this.getPoints())
        {
            if (p.getUniqueId().equals(id))
            {
                return p;
            }
        }
        
        return null;
    }
    
    /**
     * Retrieves a saved edge by its unique id
     * @param id
     * @return 
     */
    public Edge getEdgeById(String id)
    {
        for (Edge e : this.getEdges())
        {
            if (e.getUniqueId().equals(id))
            {
                return e;
            }
        }
        
        return null;
    }
    
    /**
     * Change how much locomotives are slowed one edge prior to arrival
     * @param preArrivalSpeedReduction 
     * @throws java.lang.Exception 
     */
    public void setPreArrivalSpeedReduction(double preArrivalSpeedReduction) throws Exception
    {
        if (preArrivalSpeedReduction > 0 && preArrivalSpeedReduction <= 1)
        {
            this.preArrivalSpeedReduction = preArrivalSpeedReduction;
        }
        else
        {
            throw new Exception(
                I18n.f("autolayout.errorPreArrivalSpeedReductionRange")
            );
        }
    }
    
    /**
     * Retrieves a saved edge by its name 
     * @param name
     * @return 
     */
    public Edge getEdge(String name)
    {
        return this.edges.get(name);
    }
    
    /**
     * Retrieve a saved edge by its start and end points
     * @param startPointName
     * @param endPointName
     * @return 
     */
    public Edge getEdge(String startPointName, String endPointName)
    {
        Point start = this.getPoint(startPointName);
        Point end = this.getPoint(endPointName);
        
        if (start == null || end == null) return null;
        
        return this.edges.get(Edge.getEdgeName(start, end));
    }
        
    /**
     * Creates a new point (i.e., a station or other landmark on your layout)
     *
     * Synchronized as the doors that take from the graph are - `deletePoint`, `deleteEdge`, `renamePoint`, `copyEdge`
     * (GST-C5).  Today it is called while a layout is built, before anything else holds it; the monitor keeps that true
     * of a caller added later, and `createEdge` likewise.
     *
     * @param name a unique identifier for the point
     * @param isDest are trains allowed to stop at this point?  Requires s88 feedback to work properly.
     * @param feedback address of the corresponding feedback module, or null if none
     * @return
     * @throws Exception
     */
    synchronized public Point createPoint(String name, boolean isDest, String feedback) throws Exception
    {        
        if (feedback != null && !this.control.isFeedbackSet(feedback))
        {
            throw new Exception(
                I18n.f("autolayout.errorFeedbackDoesNotExist", feedback)
            );
        }

        if (name == null || "".equals(name))
        {
            throw new Exception(
                I18n.f("autolayout.errorPointMustHaveName")
            );
        }

        if (this.points.containsKey(name))
        {
            throw new Exception(
                I18n.f("autolayout.errorPointAlreadyExists", name)
            );
        }
        
        Point p = new Point(name, isDest, feedback);
        
        p.setLayout(this);

        this.points.put(p.getName(), p);
        
        return p;
    }
    
    /**
     * Adds a (directed) Edge to the graph and updates adjacency list
     * Requires points to be added first
     * @param startPoint name of the starting point
     * @param endPoint name of the ending point
     * @return 
     * @throws java.lang.Exception 
     */
    synchronized public Edge createEdge(String startPoint, String endPoint) throws Exception
    {
        if (!this.points.containsKey(startPoint) || !this.points.containsKey(endPoint))
        {
            throw new Exception(
                I18n.f("autolayout.errorStartOrEndPointDoesNotExist")
            );
        }
        
        Edge newEdge = new Edge(this.points.get(startPoint), this.points.get(endPoint));
        
        if (this.edges.containsKey(newEdge.getName()))
        {
            throw new Exception(
                I18n.f("autolayout.errorEdgeAlreadyExists", newEdge.getName())
            );
        }
      
        this.edges.put(newEdge.getName(), newEdge);
           
        if (!this.adjacency.containsKey(newEdge.getStart().getName()))
        {
            List<Edge> newList = new LinkedList<>();
            newList.add(newEdge);
            this.adjacency.put(newEdge.getStart().getName(), newList);
        }
        else
        {
            this.adjacency.get(newEdge.getStart().getName()).add(newEdge);
        }
        
        return newEdge;
    }
    
    /**
     * Returns false if any of the given point's incoming edges are from non-reversing points
     * @param p
     * @return 
     */
    public boolean hasOnlyReversingIncoming(Point p)
    {        
        for (Edge e : this.getIncomingEdges(p))
        {
            if (!e.getStart().isReversing()) return false;
        }
        
        return true;
    }
    
    /**
     * Returns false if any of the given point's incoming edges are from active points
     * @param p
     * @return 
     */
    public boolean hasOnlyInactiveIncoming(Point p)
    {        
        for (Edge e : this.getIncomingEdges(p))
        {
            if (e.getStart().isActive()) return false;
        }
        
        return true;
    }
    
    /**
     * Returns false if any of the given point's neighbors are active points
     * @param p
     * @return 
     */
    public boolean hasOnlyInactiveNeighbors(Point p)
    {               
        for (Edge e : this.getNeighbors(p))
        {
            if (e.getEnd().isActive()) return false;
        }
        
        return true;
    }
    
    /**
     * Returns false if any of the given point's neighbors are reversing points
     * @param p
     * @return 
     */
    public boolean hasOnlyReversingNeighbors(Point p)
    {               
        for (Edge e : this.getNeighbors(p))
        {
            if (!e.getEnd().isReversing()) return false;
        }
        
        return true;
    }
    
    /**
     * Gets all incoming edges connected to the given point
     * @param p
     * @return 
     */
    public List<Edge> getIncomingEdges(Point p)
    {
        List<Edge> edgeList = new LinkedList<>();
        
        for (Edge e : this.getEdges())
        {
            if (e.getEnd().equals(p))
            {
                edgeList.add(e);
            }
        }
        
        return edgeList;
    }

    /**
     * Gets all edges neighboring a point
     * @param p
     * @return 
     */
    public List<Edge> getNeighbors(Point p)
    {
        List<Edge> neighbors = new LinkedList<>();
        
        if (this.adjacency.containsKey(p.getName()))
        {
            for (Edge e : this.adjacency.get(p.getName()))
            {
                neighbors.add(e);
            }
        }

        // Randomize order to allow for variation in paths
        Collections.shuffle(neighbors);

        return neighbors;
    }
    
    /**
     * Gets all edges neighboring and incoming to a point
     * @param p
     * @return 
     */
    public List<Edge> getNeighborsAndIncoming(Point p)
    {
        List<Edge> neighbors = new LinkedList<>();

        // Separate outgoing and incoming edges
        List<Edge> outgoing = new ArrayList<>();
        List<Edge> incoming = new ArrayList<>();

        for (Edge e : this.getEdges())
        {
            if (e.getStart().equals(p))
            {
                outgoing.add(e);
            }
            
            if (e.getEnd().equals(p))
            {
                incoming.add(e);
            }
        }

        // Sort outgoing edges by e.getEnd().getName()
        outgoing.sort(Comparator.comparing(e -> e.getEnd().getName()));

        // Sort incoming edges by e.getStart().getName()
        incoming.sort(Comparator.comparing(e -> e.getStart().getName()));

        // Combine sorted outgoing and incoming edges
        neighbors.addAll(outgoing);
        neighbors.addAll(incoming);

        return neighbors;
    }

    /**
     * Writes a log message that the specified path is impossible
     * @param loc
     * @param path 
     */
    private void logPathError(Locomotive loc, List<Edge> path, String message)
    {
        logPathError(loc, path, true, message);
    }

    /**
     * Records why a path was refused, and optionally says so in the log.
     *
     * lastError is set either way (the caller that reports per-destination reasons reads it).  What the flag turns off
     * is the log line, for the callers that are ENUMERATING paths rather than validating a chosen one: getPossiblePaths
     * asks about every candidate route from a point, so on a layout with trains parked across it nearly every question
     * is answered no, and logging each would flood the log.
     */
    private void logPathError(Locomotive loc, List<Edge> path, boolean log, String message)
    {
        lastError = message;

        if (!log) return;
        
        if (control.isDebug())
        {
            this.control.logf(
                "autolayout.errorLocomotivePathInvalid",
                loc.getName(),
                message,
                this.pathToString(path)
            );
        }
    }

    /**
     * Checks if the provided path is unoccupied
     * @param path
     * @param loc
     * @return 
     */
    public boolean isPathClear(List<Edge> path, Locomotive loc)
    {
        return isPathClear(path, loc, true);
    }

    /**
     * Whether this locomotive is already out - running, or claiming a route it has not set off on yet.
     *
     * **The two halves are not the same question, and a dispatch has to refuse both.**  A locomotive joins
     * `activeLocomotives` only after `configureAndLockPath` RETURNS, and that call is seconds long (it throws every
     * turnout and signal on the path with a wait between each, then validates the actuation), so in that window a
     * second dispatch of the same train would pass every check and start too: two threads driving one physical train,
     * each one's completion unlocking points the other still relies on (double-click a route in the Auto tab, then
     * dispatch from the diagram's right-click menu, whose items do not re-check when they are clicked).
     *
     * **One method rather than two guards** (REG9V-B2): one rule with two spellings drifts apart.  It is also the seam
     * `core.testATrainIsDispatchedOnce` needs (the alternative is a real dispatch, which waits on sensors).  Identity,
     * not equality, because both maps are keyed by the locomotive object the layout holds.
     *
     * @param loc the locomotive
     * @return whether a dispatch of it should be refused as already under way
     */
    public boolean isAlreadyUnderway(Locomotive loc)
    {
        // THE CLAIMS FIRST (RSA3-C1): the hand-over from claim to journey writes the journey and then takes the claim
        // away, so a train missing from the claims is among the journeys read after them.
        return this.takingPath.containsKey(loc) || this.activeLocomotives.containsKey(loc);
    }

    /**
     * How many trains are out, counting the ones that have claimed a route but not yet set off.
     *
     * A union rather than a sum, so that a locomotive which is both claiming and registered - the
     * moment between the two, however it is ordered - is one train and not two.
     *
     * Not a train sent by hand (BCR-C1): the cap is about what a run sends, and a hand send is neither counted nor refused.
     *
     * @return the number of distinct locomotives underway
     */
    private int trainsUnderway()
    {
        Set<Locomotive> both = Collections.newSetFromMap(
            new java.util.IdentityHashMap<Locomotive, Boolean>());

        // The claims first, for the reason `isAlreadyUnderway` gives (RSA3-C1)
        both.addAll(this.takingPath.keySet());
        both.addAll(this.activeLocomotives.keySet());

        synchronized (this.sentByHand)
        {
            both.removeAll(this.sentByHand);
        }

        return both.size();
    }

    /**
     * @param logFailures false when enumerating candidate paths, where a refusal is the ordinary
     *                    answer rather than something worth a log line.  lastError is still set.
     */
    public boolean isPathClear(List<Edge> path, Locomotive loc, boolean logFailures)
    {
        return isPathClear(path, loc, logFailures, false);
    }

    /**
     * The same, saying whether the question is about a send by hand - which the cap on trains out does not refuse (BCR-C1;
     * behaviour.md 1).  A train already on a journey sent by hand is one too, whoever asks.
     *
     * @param path the route
     * @param loc the train
     * @param logFailures false when enumerating candidate paths; see above
     * @param byHand true for a route a person would send by hand
     * @return whether the route is clear for the train
     */
    private boolean isPathClear(List<Edge> path, Locomotive loc, boolean logFailures, boolean byHand)
    {
        if (!byHand && !this.sentByHand.contains(loc)
            && this.maxActiveTrains > 0 && this.isAutoRunning() && trainsUnderway() >= this.maxActiveTrains)
        {
            logPathError(
                loc,
                path,
                logFailures,
                I18n.f("autolayout.errorMaxActiveTrainsExceeded", this.maxActiveTrains)
            );
            return false;
        }
        
        for (Edge e : path)
        {
            if (e.isOccupied(loc))
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorEdgeOccupied", placeNameOf(e))
                );
                return false;
            }

            // The same edge going in the opposite direction
            if (this.getEdge(e.getOppositeName()) != null && this.getEdge(e.getOppositeName()).isOccupied(loc))
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorEdgeOccupied", placeNameOf(this.getEdge(e.getOppositeName())))
                );
                return false;
            }

            // Excluded intermediate points cannot be traversed.  Non-stations only, deliberately: a station's exclusion
            // list says the locomotive may not STOP there, which is checked against the path's destination, not the
            // points it drives past, and blocking passage through excluded stations too removed 45% of the reachable
            // station pairs for two locomotives on the author's layout.  See HomeStaging.canEnter for the other half of
            // the rule.  Asked of the SQUARE (RSA65-B1): a station's copy for a barred arrival side is no destination,
            // and read as "not a station" its list would wall off the trains it keeps out.
            if (e.getStart().excludes(loc) && !isAStationSquare(e.getStart()))
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorIntermediatePointExcluded", placeNameOf(e.getStart()))
                );
                return false;
            }

            // Terminus stations may only be at the end of a path
            if (e.getStart().isTerminus() && !e.getStart().equals(path.get(0).getStart()))
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorIntermediateTerminusStation")
                );
                return false;
            }

            // Inactive points not allowed in auto running
            if (this.isAutoRunning() && (!e.getStart().isActive() || !e.getEnd().isActive()))
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorInactivePointInAutoRun")
                );
                return false;
            }

            // Starting point is not a station - do not pick it in fully autonomous mode
            if (this.isAutoRunning() && !e.getStart().isDestination() && e.getStart().equals(path.get(0).getStart()))
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorStartWithNonStation")
                );
                return false;
            }

            if (control.getFeedbackState(e.getEnd().getS88()) != false)
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorFeedbackNotClear", e.getEnd().getS88())
                );
                return false;
            }

            // NOR A SENSOR ANOTHER TRAIN'S JOURNEY IS STILL WAITING ON (RSA2-B1).  A journey waits on every sensor of
            // its route it has not reached, and the first time the sensor is occupied ends the wait, whichever train
            // occupies it.  Two places on one sensor (a feedback double curve's two arcs, OB-238) would otherwise take
            // two trains at once, and the first arrival would end both journeys while the second train was still on its
            // route.
            Locomotive awaiting = whoseJourneyAwaits(e.getEnd().getS88(), loc);

            if (awaiting != null)
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorFeedbackAwaitedByAnotherTrain", e.getEnd().getS88(), awaiting.getName())
                );
                return false;
            }

            // Ensure no lock edge is already held by another route.  Held, not occupied: a lock edge is track kept
            // clear so two routes cannot take one throat at once; a train STANDING at the point one leads to is not
            // using the throat (a sensor is an endpoint of the edges that meet there and part of the path of none of
            // them).  See Edge.isLockHeld.
            //
            // RAIL BEING RUN OVER, not rail that is merely held (OB-269; Adam: *"nothing should disturb the ability to
            // go from bottominner to bottominnerotherside unless trains are crossing down, or passing through either of
            // those stations."*).  **The write and the read each reach one hop, and together they reach two.**
            // `setOccupied` marks every edge in each of a path's edges' `lockEdges`; this loop reads the flag off every
            // edge in each CANDIDATE's `lockEdges`, so a candidate would be refused when some THIRD edge shared rail
            // with it and, separately, with the running path, though the candidate and the path share no rail at all.
            // `isRunOver` is the narrow question; `isLockHeld` is unchanged for everything else, because a held
            // throat's accessories must stay protected whether the hold is direct or shared (`getActiveAccs`).  BOTH
            // ORDERINGS OF AN ASYMMETRIC RELATION STILL REFUSE: a lock edge ON the path marks this candidate's
            // `occupancy` through the write and the occupancy test above refuses it; a candidate NAMING an edge on the
            // path is refused here.  Only the proxy-locked middle case is given up.
            for (Edge e2 : e.getLockEdges())
            {
                if (e2.isRunOver(loc))
                {
                    logPathError(loc, path, logFailures,
                        I18n.f("autolayout.errorLockEdgeOccupied", placeNameOf(e2))
                    );
                    return false;
                }
            }

            // AND THE SAME RAIL THE OTHER WAY (OB-269, and it is not optional).  A piece of rail is two `Edge` objects,
            // and `GraphReducer.deriveLocks` deliberately does NOT lock them against each other ("the two directions of
            // one run are not rivals for the track; they are the same track"), so the reverse edge is in nobody's
            // `lockEdges`; narrowing the reach without this would trade an over-refusal for a collision.  The covered
            // set knows this already (VAL8-A1 / REG7-B3, and by place AUT-B1, `anotherTailOn`).
            //
            // FOUND BY PLACE, NOT BY NAME: the two directions of one rail run between different COPIES of the two
            // squares, because facing IS one-way edges here, so `getEdge(end.getName(), start.getName())` finds
            // nothing.  This is the SAME QUESTION `deriveLocks` asks when it declines to lock such a pair, asked the
            // same way on purpose: two comparisons that must agree about what one rail is would drift apart.
            for (Edge sameRailBack : this.edges.values())
            {
                if (sameRailBack == e) continue;

                if (sameRailBack.getStart() == null || sameRailBack.getEnd() == null) continue;

                if (!sameRailBack.getStart().isSamePlaceAs(e.getEnd())) continue;

                if (!sameRailBack.getEnd().isSamePlaceAs(e.getStart())) continue;

                if (!sameRailBack.isRunOver(loc)) continue;

                // A TRAIN NEVER BLOCKS ITSELF (behaviour.md 5c, and `Edge.isOccupied` says the same of its own
                // question): `isRunOver` counts claims and knows nothing about whose they are, so without this a train
                // asking about the reverse of rail its OWN earlier leg holds would be refused (Return Home plans a
                // sequence and holds what it has already taken).
                if (railHeldByThisTrain(sameRailBack, loc)) continue;

                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorLockEdgeOccupied", placeNameOf(sameRailBack))
                );

                return false;
            }
        }
        
        // An INTERMEDIATE point that has been switched off may never be driven through, whatever asked for the route.
        // Deliberately NOT fenced behind isAutoRunning, unlike the every-edge rule above it: a manually chosen route may
        // still START from a deactivated point, which is how a train held in place is driven out by hand.  It may not
        // FINISH on one either (REG-B1; Adam: "Inactive really means nothing can pass").
        //
        // Intermediates are exactly the END of every edge but the last: any edge start other than the
        // first is the previous edge end, so this visits each intermediate once and neither endpoint.
        for (int i = 0; i < path.size() - 1; i++)
        {
            if (!path.get(i).getEnd().isActive())
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorInactiveIntermediatePoint", placeNameOf(path.get(i).getEnd()))
                );
                return false;
            }
        }

        // THE STATION'S OWN CAPACITY IS ASKED ALONGSIDE THE TRACK ROOM, further down (MT-262): one predicate, so the
        // doors and the why-not-moving window say the same thing about "this train does not fit where you are sending
        // it".  Asked after the inactive-endpoint and covered-track rules, so a berth that is both switched off and
        // too short reports as switched off.

        // AT EVERY DOOR, not only while autonomy is running (Adam: **"In manual mode, inactive endpoints and
        // intermediates should be refused as well.  Just not inactive start points.  Inactive really means nothing can
        // pass."**).  THE START STAYS EXEMPT: a train standing on a square that has been switched off is driven out by
        // hand, which is what closing a square around a train is for.  The stricter test a few lines above (every edge
        // refused when either endpoint is inactive) keeps its `isAutoRunning` fence, because it refuses the start too.
        if (!path.get(path.size() - 1).getEnd().isActive())
        {
            logPathError(
                loc,
                path,
                logFailures,
                I18n.f("autolayout.errorInactiveStation", placeNameOf(path.get(path.size() - 1).getEnd()))
            );
            return false;
        }

        // NO TERMINUS RULE HERE (Adam: "In manual operation, non reversing trains must be able to back into a terminus
        // if the graph makes that possible.  Otherwise we'd need a third kind of station.").  A refusal here reaches
        // every door, the right-click menu as well as autonomy; the doctrine is "Filtering at selection, never refusing
        // at execution", so pickPath will not CHOOSE a terminus for a locomotive that cannot get out of one and
        // nothing refuses the operator who asks for it.  Staging keeps the rule: HomeStaging carries its own, and it
        // is stricter (a non-reversible train may only be sent home to a terminus by a route that turns it round).

        // A TRAIN TOO LONG FOR THE TRACK IT WOULD REVERSE INTO: `validateTrainLength` compares the train against a
        // MAXIMUM SOMEBODY TYPED on the station, not the track that is there, and is skipped when that maximum is zero
        // (most squares), so a train reversing into a berth shorter than itself would stand across the switch.
        // It applies where the train will reverse (a terminus is left only by backing out), and only where the track
        // has been measured, because unmeasured track is unknown, not short (Adam: "such paths need to be disallowed if
        // any track (not station) lengths are set and we reverse over a switch.").  What it measures (REL-C6): on a
        // builder-emitted configuration the sum stops at the LAST SWITCH and counts only the stretch after it
        // (`roomAtTheEnd`); the whole-path sum survives only for a route with no switch.
        //
        // AND NOTHING RUNS OVER TRACK A STANDING TRAIN IS LYING ACROSS (Adam: **"If a train protrudes far behind where
        // it is standing, those edges it reaches need to be considered blocked..."**).  The occupancy checks above ask
        // whether a POINT holds a train, and for a long train they all say no, so this asks about edges (*"edges,
        // because the points are technically unoccupied"*).  A train never blocks ITSELF: a route that starts by
        // pulling forward off its own tail is the ordinary way a train leaves a berth.
        Map<Edge, Locomotive> coveredTrack = new LinkedHashMap<>();

        Map<String, Locomotive> coveredPlaces = new LinkedHashMap<>();

        // EVERY TRAIN BUT THIS ONE (TDD-A1).  The maps hold one train per place, the last walked; walked with the others,
        // this train's own claim on a switch square another train's tail also fouls could stand where that train's was -
        // and nothing below would find it.  Its own tail is no obstacle to it, so leaving it out loses nothing.
        walkStandingTrains(coveredTrack, coveredPlaces, other -> !other.equals(loc));

        for (Edge e : path)
        {
            // ITS OWN TAIL IS NOT THE ANSWER, only not an obstacle (OB-285): it is not in the maps at all, the walk
            // above leaves the train being routed out (TDD-A1), so each of the three questions below finds only other
            // trains.  Were it in them, it would end the question: a train turned where it stands leaves over its own
            // tail, so another train's tail on the same stretch would never be looked for.
            Locomotive lyingAcross = coveredTrack.get(e);

            // AND THE TRACK IT SHARES WITH SOMETHING COVERED.  Coverage is recorded per EDGE and drawn per SQUARE, and
            // at a switch they differ: a train lying across switch 60 covers the edge it arrived along, while a route
            // taking the other pair of arms is a different Edge never in the covered set (the same asymmetry VAL8-A1
            // found between the two directions of one rail).  Lock edges are exactly the relation needed:
            // GraphReducer derives them from SHARED TILES ("which edges cannot run at the same time as which"), so if a
            // train is lying across an edge, every edge sharing metal with it is fouled too.
            if (lyingAcross == null)
            {
                for (Edge sharing : e.getLockEdges())
                {
                    Locomotive onShared = coveredTrack.get(sharing);

                    if (onShared == null) continue;

                    // SHARED METAL ONLY, and lockEdges holds two different relations (RGD-B2).  The reduction derives
                    // locks from SHARED TILES, the relation this rule wants; the builder then adds a second population
                    // for the FR-001 restrictions (an edge arriving at a station somebody has held back gains every edge
                    // that ends at the watched square), and those edges need share no metal at all.  Told apart by
                    // SYMMETRY: sharing a tile is mutual and the reducer adds both directions, so a shared-metal partner
                    // lists this edge back, while a restriction is one-directional.  Known limit: two stations each
                    // holding the other back are symmetric by coincidence, and this would refuse there.
                    if (!sharing.getLockEdges().contains(e)) continue;

                    // AND ONLY OVER THE PART OF IT THE TRAIN IS ACTUALLY LYING ON (OB-207).  The relation above is right
                    // (a train lying across a switch fouls every road through it); its extent was not, since an edge was
                    // covered whole or not at all: a train of length ONE lying inside the first tile of a twelve-tile
                    // edge would refuse a path that shares only its last tile.  With the build emitting each edge's
                    // places the question is: does the tail lie on metal THIS edge runs over?  Both sides must be
                    // described in places or nothing is narrowed (a configuration without them keeps the whole-edge
                    // answer).  The DIRECT case a few lines up is deliberately left whole: a path uses all of its own
                    // edges, so a tail anywhere on one is in the way, while a SHARED edge may be touched at one end and
                    // no further.
                    if (!e.getPlaceIds().isEmpty() && !sharing.getPlaceIds().isEmpty()
                        && !tailLiesOn(e, onShared, coveredPlaces))
                    {
                        continue;
                    }

                    lyingAcross = onShared;

                    break;
                }
            }

            // AND EVERY OTHER COPY OF THE SAME METAL (AUT-B1).  The two questions above ask about the Edge the tail was
            // walked along and its lock partners, and one piece of rail is often several Edges: the build emits the
            // rail into a square a train may turn round at twice (into the plain copy and into the turning copy) over
            // exactly the same places, and those two are not each other's lock partners (OB-280: the diagram drew the
            // turning copy grey while a train could be sent onto it).  The places are the metal: an edge whose places
            // another train's tail has claimed is refused, whichever copy it is.  An edge described in no places keeps
            // the two answers above.
            if (lyingAcross == null) lyingAcross = anotherTailOn(e, loc, coveredPlaces);

            if (lyingAcross == null) continue;

            logPathError(
                loc,
                path,
                logFailures,
                I18n.f("autolayout.errorTrackCoveredByStandingTrain", lyingAcross.getName(),
                    placeNameOf(e))
            );

            return false;
        }

        // A TRAIN TOO LONG FOR THE TRACK IT WOULD STAND ON, at EVERY destination (MT-262): a manual send of a train
        // longer than the track between its position and the destination is refused, not merely noted.  The room rule
        // is not fenced on terminus: a train comes to rest with its head at the destination sensor whatever kind of
        // station it is, so its tail lies back along the run either way, as `edgesCoveredByStandingTrains` models it.
        //
        // AUTONOMY IS INCLUDED, deliberately: this method is the tier every door passes through, and a berth that
        // cannot physically hold a train is not a preference a person may overrule.  The tiering in
        // `docs/reference/behaviour.md` section 1 makes autonomy the STRICTER tier (manual may go where autonomy is
        // told not to, never the other way about), so a length rule that refused only the operator would invert it.
        //
        // The rule, its two known unsoundnesses and Adam's "OK" accepting them as they stand are all in
        // `measuredRoomAtTheEndOf`, which is also what the staging planner asks: one walk, one answer, so a plan cannot
        // offer a berth this then refuses on the first move.
        String tooLong = whyTooLongForThisRoute(path, loc);

        if (tooLong != null)
        {
            logPathError(loc, path, logFailures, tooLong);

            return false;
        }

        // AND A PARKING BERTH MAY NOT BLOCK A ROAD (Adam's ruling): the other half of the rule above, and the reason it could be
        // relaxed at all (a train that stands across the points at a platform is passing, one at a berth is staying).  See
        // `whyABerthCannotHoldIt`, pure for the same reason its neighbour is: the staging planner asks both rather than carrying copies.
        String foulsARoad = whyABerthCannotHoldIt(path, loc);

        if (foulsARoad != null)
        {
            logPathError(loc, path, logFailures, foulsARoad);

            return false;
        }

        // AND NOT ROUND A LOOP INTO ITS OWN TAIL (OB-294).  The standing-train checks above pass a train's own tail (a
        // train never blocks itself while it leads away from it); this is the case where the route comes back.
        String ownTail = whyItWouldMeetItsOwnTail(path, loc);

        if (ownTail != null)
        {
            logPathError(loc, path, logFailures, ownTail);

            return false;
        }

        // A square held back while another has a train STANDING on it (FR-001) - every square the route arrives at, not only its destination
        // (OB-295; Adam: *"It means trains shouldn't be sent to THIS square while trains are STANDING ON or hold a lock on the other specified
        // station(s)."*).  The other half of this setting is built into the configuration as lock edges, which ask whether that approach is held by
        // a ROUTE (`Edge.isLockHeld`: counting a parked train made a locomotive beside a junction a permanent roadblock).  They sit on every edge
        // arriving at the square, so a route through it was already refused while the watched square's approach was held, and this is asked of
        // every square so a route through a square trains only pass is not let go while a train STANDS on the watched one.  The deadlock
        // `Edge.isLockHeld` stops short of is taken on here, by his answer: two restrictions set against each other hold both trains until one is
        // moved without TrainControl's routing or a restriction is cleared (ADA-C3, ADA2-C4; behaviour.md section 1).
        //
        // IN EVERY TIER (Adam: *"If it's cleaner to go with consistency across the board, then let's enforce the occupancy ruling in all modes and
        // then rely on isPathClear."*).  **The planner moved with it:** `HomeStaging` applies this rule too, reading the occupancy the PLAN has
        // reached, so a plan cannot fail half way through with the fleet scattered (OB-073); `auditAgainstRuntime` proves the two agree.  The whole
        // BLOCK, not just the named Point: a square emitted as several copies is one piece of track.
        {
            // The train LEAVING the watched point is exempt; the restriction is about what may ARRIVE at the held-back
            // station (Adam: "The condition should not apply to trains leaving - only departing.").  Without the
            // exemption the one movement that clears the condition is the movement it forbids: a locomotive standing in
            // the yard could never be sent to the platform the yard holds back.  The same choice Edge.isOccupied makes,
            // for the reason isLockHeld records.  It cannot let two trains onto one square: whether the DESTINATION is
            // free is a different question, asked by isOccupied in this same method.  In blockingOccupantOf, so the
            // window that EXPLAINS this can ask the same question (DR-B3).
            Point[] held = heldBackAlong(path, loc);

            if (held != null)
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorDestinationBlockedByPoint",
                        placeNameOf(held[0]), placeNameOf(held[1])));

                return false;
            }
        }

        // Preview the configuration
        EdgeConfigurationState validity = new EdgeConfigurationState();
        for (Edge e : path)
        {
            this.configureEdge(e, validity);
        }

        // Invalid state means the commands conflicted, or referenced an accessory we do not have -
        // either way this path would not work as intended, so it must not be offered
        if (!validity.configIsValid)
        {
            logPathError(
                loc,
                path,
                logFailures,
                validity.errorMessage != null
                    ? validity.errorMessage
                    : I18n.f("autolayout.errorConflictingAccessoryCommands", validity.invalidConfigs.toString())
            );
            return false;
        }
              
        return true;
    }
        
    /**
     * Returns the length of the given path
     * @param path
     * @return 
     */
    public int getPathLength(List<Edge> path)
    {
        int pathLength = 0;
        
        for (Edge e : path)
        {
            pathLength += e.getLength();
        }
        
        return pathLength;
    }
      
    /**
     * Releases first, throws after, and alphabetically within each (TCX-B4).
     *
     * **The reason is mechanical.** A three-way turnout is two drives on consecutive addresses: commanding the diverging one
     * before the other has been released puts the ironwork into a combination that routes nowhere, and on real hardware that is a
     * turnout a train then runs through.  Named here so the rule can be run, with a source check on the call site so that testing
     * the rule cannot quietly leave the call uncovered.
     *
     * @param aThrows whether the first command throws its accessory
     * @param bThrows whether the second does
     * @param a the first accessory's name
     * @param b the second's
     * @return the comparison, releases sorting before throws
     */
    public static int releasesBeforeThrows(boolean aThrows, boolean bThrows, String a, String b)
    {
        return aThrows == bThrows ? a.compareTo(b) : (aThrows ? 1 : -1);
    }
    /**
     * Function to configure an accessory.  This is called from the edge configuration lambda (instead of calling control directly) as defined in layout.createEdge 
     * so that the graph can keep track of conflicting configuration commands, and invalidate those paths accordingly
     * @param e - the edge
     * @param preConfigure - when set, simulate sequence of commands and record validity status
     * @return false if the edge could not be configured, or - when previewing - cannot be
     */
    private boolean configureEdge(Edge e, EdgeConfigurationState preConfigure)
    {
        boolean result = true;

        // Released before thrown, and otherwise by name (the map's own key order is no order at all).  A three-way turnout is two drives
        // on consecutive addresses: commanding the diverging one before the other has been released puts both blade sets over at once,
        // a combination that routes nowhere and that some mechanisms bind in, so releasing first means the only transient is straight.
        // Applied to every edge rather than to detected pairs: for independent accessories the order is immaterial, and a pair is only
        // recognisable from an address convention this method cannot see.  The guarantee stops at the edge boundary:
        // configureAndLockPath configures a path's edges in path order, so both commands of one turnout belong on one edge.
        List<String> names = new ArrayList<>(e.getConfigCommands().keySet());

        names.sort((a, b) ->
        {
            boolean aThrows = Accessory.isThrow(e.getConfigCommands().get(a));
            boolean bThrows = Accessory.isThrow(e.getConfigCommands().get(b));

            return releasesBeforeThrows(aThrows, bThrows, a, b);
        });

        for (String name : names)
        {
            Accessory.accessorySetting state = e.getConfigCommands().get(name);

            // Sanity check
            Accessory acc = control.getAccessoryByName(name);

            if (acc == null)
            {
                String errorMessage = I18n.f("autolayout.errorAccessoryDoesNotExist", name, state);
                control.log(errorMessage);

                if (preConfigure != null)
                {
                    // A path whose accessory we do not have cannot be set up, so it must not be
                    // offered.  Carry on checking so that every missing accessory is reported at once.
                    preConfigure.invalidConfigs.add(name + " " + state);
                    preConfigure.configIsValid = false;
                    preConfigure.errorMessage = errorMessage;
                    result = false;
                    continue;
                }

                this.invalidate();
                Layout.lastError = errorMessage;
                control.logf("autolayout.errorInvalidatingAutoLayoutState");

                return false;
            }

            if (preConfigure != null)
            {   
                // An opposite configuration was already issued - invalidate!
                if (preConfigure.configHistory.containsKey(acc) && !preConfigure.configHistory.get(acc).equals(state))
                {
                    preConfigure.invalidConfigs.add(acc.getName() + " " + state);
                    preConfigure.configIsValid = false;
                }
                else
                {
                    preConfigure.configHistory.put(acc, state);
                }
            }
            else
            {
                control.logf(
                    "autolayout.infoConfiguringAccessory",
                    acc.getName(),
                    state.toString().toLowerCase()
                );

                boolean commanded;

                // UNDER THE ACCESSORY'S SIGNAL LOCK, as the exit guards are: the edge is taken, so its hold is readable,
                // and a refresh that read the holds before that has commanded already (RSA67-C1, `signalLocks`)
                synchronized (this.signalLock(acc))
                {
                    commanded = acc.setState(state);
                }

                if (!commanded)
                {
                    // This should never happen - but if it does the accessory was not commanded, so
                    // the edge is not configured and the caller must not release the locomotive
                    control.logf(
                        "autolayout.errorInvalidConfigurationCommand",
                        name,
                        state.toString()
                    );

                    result = false;
                }

                // Sleep between commands
                try
                {
                    Thread.sleep(CONFIGURE_SLEEP);
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return result;
    }
    
    /**
     * Deletes a point from the graph.  Requires that no edges connect it.
     * @param name
     * @throws Exception 
     */
    synchronized public void deletePoint(String name) throws Exception
    {
        Point p = this.getPoint(name);
        
        if (p == null)
        {
            throw new Exception(
                I18n.f("autolayout.errorPointDoesNotExist", name)
            );
        }

        // NOT WHILE THE RAILWAY IS USING THE GRAPH (C3): the same guard renamePoint carries, and for a stronger reason
        // (a rename mutates a point under live visited sets, this removes it from under them).  The greying on the menu
        // item is not this guard: items are greyed when the popup OPENS and the action fires when it is CLICKED, so
        // starting autonomy from another window in between leaves a live item over a running railway.  isRunning
        // rather than the bare flag, and staging as well: the planner walks these structures with nothing dispatched.
        if (this.isRunning() || this.isStagingInProgress())
        {
            throw new Exception(I18n.f("autolayout.errorCannotEditWhileRunning"));
        }

        if (!this.getNeighbors(p).isEmpty())
        {
            throw new Exception(
                I18n.f("autolayout.errorPointConnectedDeleteEdgesFirst", name)
            );
        }

        for (Edge e : this.getEdges())
        {
            if (e.getStart().equals(p) || e.getEnd().equals(p))
            {
                throw new Exception(
                    I18n.f("autolayout.errorPointHasIncomingEdgesDeleteFirst", name)
                );
            }
        }
        
        // Releases any home claim on it - the twin of locDeleted's release, on the value side of the map.  Otherwise the claim outlives
        // the station: its locomotive still counts as misplaced, so Return Home stays lit and every press reports "cannot reach their
        // home station" naming a station that no longer exists.  By name, because that is how every consumer compares points.
        this.homeStations.values().removeIf(home -> home != null && home.getName().equals(name));

        // And every OTHER point that was watching this one (OB-080): blockedBy holds Points, so a deleted station would
        // stay in the lists of the stations it held back as a ghost blocker.  It answers null so the rule passes, but
        // the moment anything DOES occupy the deleted object (a stale reference from a path mid-flight) the station it
        // guards is refused for the rest of the session, naming a point the user cannot see to clear; the list is
        // written by name, so the symptom would vanish across a save and load.  The occupant goes too: a locomotive's
        // position must not point at an object no longer in the graph.
        for (Point other : this.points.values())
        {
            if (other == p) continue;

            if (other.getBlockedBy().isEmpty()) continue;

            // Through setBlockedBy, because getBlockedBy hands back an unmodifiable view.
            List<Point> keeping = new LinkedList<>();

            for (Point watched : other.getBlockedBy())
            {
                if (watched != null && watched.getName().equals(name)) continue;

                keeping.add(watched);
            }

            other.setBlockedBy(keeping);
        }

        if (p.getCurrentLocomotive() != null) p.setLocomotive(null);

        // Remove from db
        this.points.remove(name);
    }
    
    /**
     * Creates a new edge with a different start ending point
     * @param original
     * @param newPoint
     * @param changeEnd true to change end, otherwise will change start
     * @return
     * @throws Exception 
     */
    synchronized public Edge copyEdge(Edge original, String newPoint, boolean changeEnd) throws Exception
    {
        Edge newEdge;
        
        if (changeEnd)
        {
            newEdge = this.createEdge(original.getStart().getName(), newPoint);
        }
        else
        {
            newEdge = this.createEdge(newPoint, original.getEnd().getName());
        }
        
        // Copy lock edges
        for (Edge e : original.getLockEdges())
        {
            newEdge.addLockEdge(e);
        }
        
        // Copy config commands
        for (Entry <String, accessorySetting> m : original.getConfigCommands().entrySet())
        {
            newEdge.addConfigCommand(m.getKey(), m.getValue());   
        }
        
        // Copy length
        newEdge.setLength(original.getLength());
        
        return newEdge;
    }
    
    /**
     * Deletes an edge from the graph
     * @param start
     * @param end
     * @throws Exception 
     */
    synchronized public void deleteEdge(String start, String end) throws Exception
    {
        Edge e = this.getEdge(start, end);
        
        if (e == null)
        {
            throw new Exception(I18n.f("autolayout.errorEdgeDoesNotExist", start, end));                    
        }

        // NOT WHILE THE RAILWAY IS USING THE GRAPH (C3), as in deletePoint and renamePoint: it removes the edge from under live visited
        // sets.  The menu item's greying is not this guard (the popup is greyed when it OPENS and fires when it is CLICKED); isRunning
        // and staging as well, because the planner walks these structures with nothing dispatched.
        if (this.isRunning() || this.isStagingInProgress())
        {
            throw new Exception(I18n.f("autolayout.errorCannotEditWhileRunning"));
        }
        
        // Remove from adjacency list
        this.adjacency.get(e.getStart().getName()).remove(e);
        
        // Remove from db
        this.edges.remove(e.getName());
        
        // Remove from lock edge lists
        for (Edge e2 : this.getEdges())
        {
            e2.removeLockEdge(e);
        }
    }
   
    
    /**
     * Renames a point
     * @param name
     * @param newName
     * @throws Exception 
     */
    synchronized public void renamePoint(String name, String newName) throws Exception
    {
        Point p = this.getPoint(name);
        
        if (p == null)
        {
            throw new Exception(I18n.f("autolayout.errorPointDoesNotExist2", name));
        }

        // The preconditions live here, not only in the dialog that calls this, so a second caller (a keyboard shortcut,
        // a bulk rename) cannot corrupt the graph: points.put would overwrite a point that already carries the new
        // name and the edge-key rebuild would drop its edges, and a rename mid-run mutates Point.hashCode under live
        // visited sets.
        if (!newName.equals(name) && this.getPoint(newName) != null)
        {
            throw new Exception(I18n.f("autolayout.errorPointAlreadyExists", newName));
        }

        // isRunning, not the bare isAutoRunning flag: it also counts in-flight locomotives, so the guard holds through the
        // graceful-stop wind-down, and the staging planner runs bfs over these structures with nothing dispatched at all.
        if (this.isRunning() || this.isStagingInProgress())
        {
            throw new Exception(I18n.f("autolayout.errorCannotEditWhileRunning"));
        }
        
        // Update the point name
        p.rename(newName);
        
        // Update points map key
        this.points.put(newName, p);
        this.points.remove(name);
        
        // Update adjacency list keys
        if (this.adjacency.containsKey(name))
        {
            this.adjacency.put(newName, this.adjacency.get(name));
            this.adjacency.remove(name);
        }
        
        // Add keys corresponding to new edge name
        List<Edge> edgeList = new ArrayList(this.getEdges());
        
        for (Edge e : edgeList)
        {
            if (!this.edges.containsKey(e.getName()))
            {
                this.edges.put(e.getName(), e);
            }
        }
        
        // Delete old invalid keys
        Iterator<Map.Entry<String, Edge>> it = this.edges.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<String,Edge> entry = it.next();
            
            if (!entry.getKey().equals(entry.getValue().getName()))
            {
                it.remove();
            }
        }
        
        this.refreshUI();
    }
    
    /**
     * Follows a rename in the record of which train the railway turned where (RTX-C3, SVA-C4).
     *
     * `reversedOnArrival` is keyed by NAME (it feeds a facing written into the setup, and a file holds a name).
     * `renameLoc` repairs everything else a rename touches; without this, a train turned on arrival and renamed before
     * the window next drained the record would lose its turn.  Deleting a locomotive clears its entry, the same rule
     * one door along.
     *
     * @param from the name it had
     * @param to the name it has
     */
    public void locRenamed(String from, String to)
    {
        if (from == null || to == null) return;

        String turnedAt = this.reversedOnArrival.remove(from);

        if (turnedAt != null) this.reversedOnArrival.put(to, turnedAt);
    }

    /**
     * What the record holds for this train, for the test that pins the two repairs above (RTX-C3).
     *
     * @param name the locomotive
     * @return the square it was turned at, or null
     */
    public String turnedOnArrivalAt(String name)
    {
        return name == null ? null : this.reversedOnArrival.get(name);
    }
    /**
     * The locomotives turned round at their destination since this was last asked, and forgets them.
     *
     * Each name against the Point it was standing on when the turn happened, which makes the write absolute rather than a flip of
     * whatever the setup last recorded (REV9-A1).  Drained rather than read, so the drain has an end: the caller puts back whatever
     * it could not write.  **Removed by KEY AND VALUE**, so a turn made by another driver thread between the copy and the removal
     * is not swallowed by a drain that never saw it (REV9-D2: the newer entry names a different square and must survive).
     *
     * @return the names against the Points they turned at, possibly empty, never null
     */
    public java.util.Map<String, String> takeReversalsOnArrival()
    {
        java.util.Map<String, String> taken = new java.util.LinkedHashMap<>(this.reversedOnArrival);

        for (java.util.Map.Entry<String, String> turn : taken.entrySet())
        {
            this.reversedOnArrival.remove(turn.getKey(), turn.getValue());
        }

        return taken;
    }

    /**
     * The plain copy of a turning copy that a train coming in by this path would have driven onto: the same square, not a
     * turning copy, reached from where the path's last leg starts, and free - or null where there is none (a real
     * terminus, or a square with no plain copy for this approach).  The search `standOnTheCopyItDidNotTurnOn` acts on at
     * the arrival and `copyItWillStandOn` asks ahead of it (OB-314); see the first for why the approach decides.
     *
     * @param arrived the Point the path ends on
     * @param path the path
     * @return the plain copy, or null
     */
    private Point plainSiblingFor(Point arrived, List<Edge> path)
    {
        if (arrived == null || path == null || path.isEmpty()) return null;

        if (!arrived.isTerminus() && !arrived.isReversing()) return null;

        Point cameFrom = path.get(path.size() - 1).getStart();

        if (cameFrom == null) return null;

        for (Point sibling : this.points.values())
        {
            if (sibling == arrived || !arrived.isSamePlaceAs(sibling)) continue;

            if (sibling.isTerminus() || sibling.isReversing()) continue;

            // Reachable from the same approach, which is what makes it the copy THIS train would
            // have driven onto rather than the one facing the other way.
            if (this.getEdge(cameFrom.getName(), sibling.getName()) == null) continue;

            if (sibling.getCurrentLocomotive() != null) continue;

            return sibling;
        }

        return null;
    }

    /**
     * Moves a train off the turning copy of a square it declined to turn at (PRW-A1).
     *
     * A may-turn square is emitted as a plain copy and a turning copy per arrival side, tied by a block and reached by
     * the same edge, and they face opposite ways.  A train that arrived on the turning copy and did not turn is
     * standing where the graph expects a train pointing the other way, so this stands it on the plain sibling that the
     * same approach reaches.  A split square has a plain copy per arrival SIDE and only one is the one this train
     * could have driven onto, so the copy is chosen by the approach (the one with an incoming edge from the Point the
     * last leg started at), not by the flags alone.
     *
     * It does nothing for most arrivals (the Point is not a turning copy, it has no siblings, or none is reachable from
     * where the train came).  A square where turning is COMPULSORY has no plain copy at all, so the train stays where
     * it is, which is right because there is nowhere else for it to be.
     *
     * The tail comes with it, for the reason `AutonomySession.moveOntoFacingCopy` gives: `setLocomotive` clears
     * `arrivedFrom` on every change of occupant, and this is the same train re-stood on a sibling copy of one square,
     * whose carriages have not moved.
     *
     * @param arrived the Point the path ended on
     * @param loc the train that has just arrived and did not turn
     * @param path the path it drove, whose last leg says which approach it came by
     */
    private void standOnTheCopyItDidNotTurnOn(Point arrived, Locomotive loc, List<Edge> path)
    {
        if (arrived == null || loc == null || path == null || path.isEmpty()) return;

        // Only a turning copy has anything to correct.  The predicate is the one the builder writes (`stops ? "terminus" :
        // "reversing"`) and cannot tell a turning copy from a real terminus on its own; it does not have to, since a real terminus
        // has no plain sibling and the search below finds nothing.
        if (!arrived.isTerminus() && !arrived.isReversing()) return;

        // The copy this train would have driven onto - asked ahead of the arrival too, by `copyItWillStandOn` (OB-314)
        Point sibling = plainSiblingFor(arrived, path);

        if (sibling != null)
        {
            String tail = arrived.getArrivedFrom();

            // AND THE ROUTE, read here for the same reason as the side (TDR-B1): moving the train onto
            // the sibling clears both on the copy it leaves, so what is read after the move is nothing.
            java.util.List<Edge> along = arrived.getArrivedAlong();

            // ONTO THE PLAIN COPY FIRST, WITH THE SIDE AND THE ROAD IT CAME IN BY, AND ONLY THEN OFF THE TURNING ONE (RSA-C3):
            // `setLocomotive` sweeps the train off every other copy before it places it, so the square read empty for a moment, a claim
            // on another thread saw no train and no tail there, and the platform's exit guard (which follows the square) was commanded
            // GREEN and then RED again over the standing train.  Reserved here, both copies hold it until the turning one lets go.
            // After the unlock still: until then the Points `clearLocomotiveExcept` sweeps are this run's reservations (PRV-B2).
            sibling.reserve(loc);

            sibling.setArrivedFrom(tail);

            // AND THE ROUTE, which describes the same arrival from this copy as from the one it
            // declined (MT-335) - the value read before the move, not after it.
            sibling.setArrivedAlong(along);

            clearLocomotiveExcept(loc, sibling);

            // THE STATION, NOT THE COPY: both Points are one square, and naming them would tell the operator about
            // machinery they cannot see (see `placeNameOf`).
            this.control.logf("autolayout.log.stoodOnTheCopyItFaces", placeNameOf(arrived));

            return;
        }

        // NOTHING TO MOVE IT TO, AND THAT IS WORTH SAYING WHERE THE TURN WAS OPTIONAL (PRV-C7).  A real terminus has no
        // plain sibling by construction, and returning quietly is right there.  A MAY-TURN square is different: the
        // operator was asked, said keep the direction, and the train is left standing on the copy that faces the other
        // way.  `AutonomyBuilder.nodesFor` emits the plain copy only where the square has somewhere onward to go, so a
        // may-turn dead end has only the turning copy for that side, the one case this method cannot repair.  If this
        // ever prints, the railway has a shape the author's does not (all twelve parking berths are compulsory turns,
        // and all four may-turn squares have plain copies), and the answer the operator gave was not honoured.
        if (!mustTurnAt(arrived))
        {
            this.control.logf("autolayout.log.noPlainCopyToStandOn", placeNameOf(arrived));
        }
    }

    /**
     * What a person calls the track an edge runs over: the two squares it joins, without the heading a builder-emitted
     * copy carries in its name.  An edge is named after its two copies - "Tunnel (southbound) -> BottomMainAPre
     * (eastbound)" - and the headings are how the builder tells two directions of one square apart, not anything on
     * the diagram.
     *
     * @param edge the edge
     * @return "From -> To", by square
     */
    static String placeNameOf(Edge edge)
    {
        if (edge == null) return "";

        // An edge between two copies of ONE square - a turn in place - is that square, not "X -> X".
        if (edge.getStart() != null && edge.getStart().isSamePlaceAs(edge.getEnd()))
        {
            return placeNameOf(edge.getStart());
        }

        return placeNameOf(edge.getStart()) + " -> " + placeNameOf(edge.getEnd());
    }

    /**
     * What a Point is called as a PLACE, for a sentence the operator reads.
     *
     * A square that became several Points carries its arrival in its name - "BottomMainB (eastbound, reverse)" - and that suffix is
     * the builder's machinery, not something on the diagram (Adam: *"the whole copy thing needs to be masked from the user"*).  The
     * same stripping `StationIndex.withoutArrivalSuffix` does, deliberately a mirror of it rather than a call: this package sits
     * below `automationui`.  The four words are `AutonomyBuilder.heading`'s, and a name that ends in anything else is left alone, so
     * a station somebody really did call "Yard (old)" keeps its name.
     *
     * @param point the Point being named
     * @return its name without an arrival suffix
     */
    static String placeNameOf(Point point)
    {
        if (point == null) return "";

        String name = point.getName();

        if (name == null || !name.endsWith(")")) return name;

        int open = name.lastIndexOf(" (");

        if (open <= 0) return name;

        String inside = name.substring(open + 2, name.length() - 1);

        int comma = inside.indexOf(',');

        String heading = comma < 0 ? inside : inside.substring(0, comma);

        for (String known : new String[]{"northbound", "southbound", "eastbound", "westbound"})
        {
            if (known.equals(heading)) return name.substring(0, open);
        }

        return name;
    }

    /**
     * Whether a train arriving on this copy from that Point has nowhere on the square to stand facing on (RSA49-A1): the
     * copy is a turning copy, and no copy of its square that neither turns trains nor reverses them can be driven onto
     * from where the train came - the search `plainSiblingFor` makes for the re-stand, asked of the approach rather than
     * of a run, and whoever stands there.
     *
     * The end of a line marked "Trains May Change Direction Here" is one: the build emits only the turning copy for an
     * arrival with no way on.  So is a may-turn square one of whose ways in has no way on, whose plain copy belongs to the
     * other approach.  Kept facing on there, a train stands on the copy that faces back out with its decoder driving it
     * forward.
     *
     * @param arrived the copy the train arrives on
     * @param cameFrom the Point its last leg starts from
     * @return whether no copy faces on from that approach
     */
    public boolean noCopyFacesOnFrom(Point arrived, Point cameFrom)
    {
        if (arrived == null || cameFrom == null) return false;

        if (!arrived.isTerminus() && !arrived.isReversing()) return false;

        // A COPY WITH NO BLOCK COUNTS (RSA50-A1): the build gives a square of ONE copy no block - the end of a line marked
        // "may" is one, its only copy the turning one - and leaving it out left exactly the case this asks about.  A
        // hand-written railway's lone turning Point is no different: kept facing on, its train stands facing back out.

        for (Point sibling : this.points.values())
        {
            if (sibling == arrived || !arrived.isSamePlaceAs(sibling)) continue;

            if (sibling.isTerminus() || sibling.isReversing()) continue;

            if (this.getEdge(cameFrom.getName(), sibling.getName()) != null) return false;
        }

        return true;
    }

    /**
     * Whether no way into this copy has a copy of its square facing on (RSA49-A1): `noCopyFacesOnFrom` asked of every
     * Point an edge reaches it from.  False for a copy nothing reaches.
     *
     * @param arrived the copy
     * @return whether every way in turns the train
     */
    boolean noCopyFacesOnFromAnyWayIn(Point arrived)
    {
        boolean reached = false;

        for (Edge edge : this.edges.values())
        {
            if (edge.getEnd() != arrived) continue;

            reached = true;

            if (!noCopyFacesOnFrom(arrived, edge.getStart())) return false;
        }

        return reached;
    }

    /**
     * The copy of its square a train turned on arriving here is stood on (RSA50-A3, RSA50-B1): one whose train points the
     * way it came in - one trains may arrive at before one they may not, and of those one that does not turn trains
     * before one that does.  That is the window's choice after a turn (`AutonomySession.copyFacing`, which its idle drain
     * makes); read here from the copies' own facings (`Point.getCopyFacing`, written by the build for a split square), so
     * that the arrival makes it and the drain then finds the train where it would have put it.
     *
     * @param arrived the copy the train arrived on
     * @param cameInBy the side it came in by, which a turned train faces
     * @return the copy, or null where the square is not split or no copy faces that way
     */
    private Point copyAfterTheTurn(Point arrived, String cameInBy)
    {
        if (arrived == null || cameInBy == null || arrived.getCopyFacing() == null) return null;

        // ALREADY FACING THAT WAY, AND A COPY TRAINS MAY ARRIVE AT: where it is (RSA52-B1) - the turning copy of the
        // way it came in, as the window's drain now leaves it
        if (cameInBy.equals(arrived.getCopyFacing()) && arrived.isDestination()) return arrived;

        Point best = null;

        int rank = Integer.MAX_VALUE;

        for (Point copy : this.points.values())
        {
            if (!arrived.isSamePlaceAs(copy) || !cameInBy.equals(copy.getCopyFacing())) continue;

            int r = (copy.isDestination() ? 0 : 2) + (copy.isTerminus() || copy.isReversing() ? 1 : 0);

            if (r < rank)
            {
                rank = r;
                best = copy;
            }
        }

        return best;
    }

    /**
     * Moves a train that has just turned onto the copy of its square it now faces on from (RSA50-A3, RSA50-B1), with the
     * side and the road it came in by, as `standOnTheCopyItDidNotTurnOn` does for a train that kept its direction - onto
     * that copy first, and only then off the one it arrived on, so the square never reads empty.
     *
     * And the record the window writes the turn from (`reversedOnArrival`) names the copy it stands on now, whose side
     * it came in by is the same: the window's drain writes the facing from it and finds the train already on the copy it
     * would have chosen.
     *
     * @param arrived the Point the path ended on
     * @param loc the train that has just arrived and turned
     */
    private void standOnTheCopyItFacesAfterTheTurn(Point arrived, Locomotive loc)
    {
        if (arrived == null || loc == null || arrived.getCurrentLocomotive() != loc) return;

        Point onto = copyAfterTheTurn(arrived, arrived.getArrivedFrom());

        if (onto == null || onto == arrived) return;

        if (onto.getCurrentLocomotive() != null && onto.getCurrentLocomotive() != loc) return;

        String tail = arrived.getArrivedFrom();

        java.util.List<Edge> along = arrived.getArrivedAlong();

        onto.reserve(loc);

        onto.setArrivedFrom(tail);

        onto.setArrivedAlong(along);

        clearLocomotiveExcept(loc, onto);

        if (loc.getName() != null && arrived.getName().equals(this.reversedOnArrival.get(loc.getName())))
        {
            this.reversedOnArrival.put(loc.getName(), onto.getName());
        }

        // Nothing logged: the turn itself is (autolayout.infoLocomotiveReachedTerminusOrFinalReversingStation), and which
        // copy of one square the graph keeps the train on is bookkeeping the operator cannot see
    }

    /**
     * The path a timetable entry runs from where its train stands (RSA53-B1, RSA54-A1): the path as recorded where the train stands
     * on its start copy; where it stands on another copy of that square facing the start copy's way, with the start copy free, the
     * same path with its first edge swapped for that copy's own edge to the same next Point; and null where it does not stand at its
     * start.  The setup keeps a standing train as its square and facing only, so a placement stands it on the plain copy while a turn
     * leaves it on the turning copy, and the timetable names that one (RSA52-B1).  The two copies leave by the same Points.
     *
     * The train is NOT moved onto the start copy (RSA54-A1): a turning copy has rails on the side it is arrived at from only, so a
     * train that came in from the other side kept a tail side the copy has no rail on, and the railway claimed none of the track its
     * body lies over.  Run from the copy it stands on, its tail is walked from the rails by its side.
     *
     * @param path the entry's path as recorded
     * @param loc the entry's train
     * @return the path to run, or null where the train is not at its start
     */
    public List<Edge> pathFromWhereItStands(List<Edge> path, Locomotive loc)
    {
        if (path == null || path.isEmpty() || loc == null) return null;

        Point start = path.get(0).getStart();

        if (loc.equals(start.getCurrentLocomotive())) return path;

        if (start.getCopyFacing() == null || start.getCurrentLocomotive() != null) return null;

        Point next = path.get(0).getEnd();

        for (Point copy : this.points.values())
        {
            if (copy == start || !loc.equals(copy.getCurrentLocomotive()) || !copy.isSamePlaceAs(start)
                || !start.getCopyFacing().equals(copy.getCopyFacing()))
            {
                continue;
            }

            Edge first = this.edges.get(Edge.getEdgeName(copy, next));

            if (first == null) return null;

            List<Edge> from = new java.util.ArrayList<>(path);

            from.set(0, first);

            return from;
        }

        return null;
    }

    /**
     * Whether turning is compulsory at this square rather than a choice the operator was given.
     *
     * A square whose copies are ALL turning copies is one the graph turns every train at; where a plain copy exists somewhere, turning
     * there was optional.  The complement of `hasAWayThrough`, spelled out here so PRV-C7's log line says something true.
     *
     * @param square the copy the train arrived on
     * @return whether every copy of that square turns trains
     */
    private boolean mustTurnAt(Point square)
    {
        for (Point copy : this.points.values())
        {
            if (!square.isSamePlaceAs(copy)) continue;

            if (!copy.isTerminus() && !copy.isReversing()) return false;
        }

        return true;
    }

    /**
     * Puts back reversals that were drained but not written.
     *
     * Draining is what stops a record becoming a metronome, but it happens BEFORE the caller has written anything, and the caller can
     * fail (no setup to write to, a store that throws part way through a list), which would lose a turn this record exists to make
     * durable (RGD-C7).
     *
     * `putIfAbsent` is the whole implementation: it restores exactly the state the drain took away, and a train that has arrived
     * somewhere else in the meantime has a NEWER record which this must not overwrite with the square it has since left.  Not a cure
     * for a configuration RELOAD between the turn and the write, which swaps this object entirely.
     *
     * @param unwritten the turns the caller did not manage to write, may be null or empty
     */
    public void restoreReversalsOnArrival(java.util.Map<String, String> unwritten)
    {
        if (unwritten == null) return;

        for (java.util.Map.Entry<String, String> turn : unwritten.entrySet())
        {
            if (turn.getKey() != null && turn.getValue() != null)
            {
                this.reversedOnArrival.putIfAbsent(turn.getKey(), turn.getValue());
            }
        }
    }

    /**
     * Fires callbacks to repaint the graph UI
     */
    public void refreshUI()
    {
        for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
        {
            if (callback != null)
            {
                fireCallback(callback, new LinkedList<>(this.getEdges()), null, false);
            }
        }
    }
        
    /**
     * Marks all the edges in a path as occupied, effectively locking it
     * @param path a list of edges to traverse
     * @param loc
     * @return 
     */
    public boolean configureAndLockPath(List<Edge> path, Locomotive loc)
    {
        return configureAndLockPath(path, loc, null);
    }

    /**
     * `configureAndLockPath`, keeping the run's reversal answer with the claim (RSA30-C3): see `copyItWillStandOn`.
     *
     * @param path the path
     * @param loc the locomotive
     * @param reversals the run's reversal policy, or null for autonomy's
     * @return whether the path was locked
     */
    private boolean configureAndLockPath(List<Edge> path, Locomotive loc, ReversalPolicy reversals)
    {
        // Lock the path and send the accessory commands under the Layout monitor.  Holding it here is
        // fine - path locking must be atomic - but the validation wait below must NOT hold it, so other
        // locomotives' path checks are not blocked for the (possibly multi-second, scales with path size -
        // see validatePathActuation) validation wait.
        boolean configureFailed = false;
        int edgesLocked = 0;

        // A THROW out of the lock loop, kept to be rethrown once the track has been given back.  A thrown failure would
        // go straight out to executePath's handler, which deliberately does not unlock ("the locomotive may be
        // physically standing on those edges"): true of a failure MID-RUN, false here, since this method returns before
        // loc.setSpeed is issued and the train has not moved.  Left behind, every edge taken would stay occupied for
        // the session with its lock edges, and reserve has put the locomotive on those Points without sweeping, so it
        // would stand in several places at once.
        RuntimeException lockFailure = null;

        synchronized (this)
        {
            // ALREADY OUT, ASKED AGAIN WHERE THE CLAIM IS TAKEN (AUT-C1): `executePath` asks this before it gets here, outside the
            // monitor, and the claim that answer reads is taken below, inside it, which can be seconds later while another train throws
            // its accessories; two dispatches of one train in that window both passed the outer check, and where their routes shared no
            // track both locked and two threads drove one train.  Asked in the same monitor as the claim, the second is refused, before
            // anything is taken - and `executePathInternal` removes nothing when this refuses (AUT2-C1).
            if (this.isAlreadyUnderway(loc))
            {
                this.control.logf("autolayout.errorLocomotiveBusy", loc.getName());
                return false;
            }

            // Return if this path isn't clear
            if (!this.isPathClear(path, loc))
            {
                this.control.logf("autolayout.errorPathOccupied");
                return false;
            }

            // THE RUN'S REVERSAL ANSWER, kept with its claim (RSA30-C3) so the copy the train will stand on is known while its route is
            // set (kept only from the hand-over, the label read the turning copy this claim reserves and showed the train the wrong way
            // round until it set off).  Here, past the refusals above, so a duplicate dispatch that loses writes nothing over the
            // winner's, and BEFORE the route, so a reader that finds the route finds this answer with it (RSA31-C1).
            this.runPolicies.put(loc, java.util.Optional.ofNullable(reversals));

            // Claimed HERE, in the same monitor that just did the counting: anywhere later, the check and the claim can be pulled apart
            // by another thread doing its own check in between, which is what let two trains past a cap of one.
            this.takingPath.put(loc, path);

            try
            {
            for (Edge e : path)
            {
                // Counted as TAKEN before it is taken, not after: the release below covers
                // path.subList(0, edgesLocked), so an edge counted afterwards is one a throw out of setOccupied or
                // reserve leaves occupied and outside the release.
                //
                // Counting first can only ever release an edge that was never taken, and releasing one is NOT free
                // (DAY-C4, REL-C16): `Edge.setUnoccupied` floors this edge's count at zero and cascades
                // `setLockedEdgeUnoccupied()` to every entry in `lockEdges`, each decrementing its own count.  The
                // over-release that licenses is unreachable: `setOccupied` increments `occupancy` as its first
                // statement, so an edge counted and then thrown past has already been incremented; only its locked
                // siblings could be released untaken, and reaching them needs a throw INSIDE the lockEdges loop (a
                // ConcurrentModificationException mid-iteration, which nothing in the tree produces).
                edgesLocked++;

                e.setOccupied();

                // RESERVED, not placed: a locked path holds every one of its points for this locomotive at once, which keeps a junction
                // behind the train reserved against a second train reaching it another way.  setLocomotive would sweep the train off the
                // point it was just reserved on the moment the next point is reserved, collapsing the reservation to the destination,
                // freeing every junction and, on a path-integrity failure, stranding the train on no point at all.
                e.getEnd().reserve(loc);

                // isPathClear already previewed the configuration, so this should not fail - but if an
                // accessory went missing in between, the locomotive must not be released onto a path we
                // were unable to set up.  Stop here and let the caller below release the locks.
                if (!this.configureEdge(e, null))
                {
                    configureFailed = true;
                    break;
                }

                // AND THE FIRST EDGE SETS THE EXIT GUARDS OF THE STATION IT LEAVES GREEN, with its own accessories
                // (OB-340) - each under its signal's lock, now that the claim and this edge make the hold readable, so a
                // refresh that read the holds before them has commanded already (RSA67-C1, `signalLocks`)
                if (e == path.get(0))
                {
                    for (Accessory guard : this.exitGuardsLeftBy(e))
                    {
                        synchronized (this.signalLock(guard))
                        {
                            guard.setState(Accessory.accessorySetting.GREEN);
                        }
                    }
                }

                loc.delay(CONFIGURE_SLEEP);
            }
            }
            catch (RuntimeException e)
            {
                // Released OUTSIDE the monitor, like the configureFailed path below and for the same
                // reason - so what is recorded here is only which failure to rethrow.
                lockFailure = e;
            }
        }

        if (lockFailure != null)
        {
            // Exactly what a configuration failure does, and only the edges actually taken: releasing the rest would
            // call setUnoccupied on edges never held, which also clears their lock edges, and those may belong to
            // another locomotive by now.  Unless nothing was taken at all: handleMisconfiguredPath ends by reserving
            // path.get(0).getStart(), which on an empty list throws, replacing the exception that explains what went
            // wrong and skipping the claim release below.  The configureFailed path cannot reach zero; a throw can.
            if (edgesLocked > 0)
            {
                this.handleMisconfiguredPath(path.subList(0, edgesLocked), loc);
            }

            this.takingPath.remove(loc);

            // Rethrown rather than turned into false.  Nothing here knows what went wrong, and a fault
            // reported as "the path was occupied" is one nobody can act on.
            throw lockFailure;
        }

        if (configureFailed)
        {
            // Only the edges we actually took.  Releasing the rest would call setUnoccupied on edges we
            // never locked, and that also clears their lock edges - which, precisely because we never
            // held them, may belong to another locomotive by now.
            this.handleMisconfiguredPath(path.subList(0, edgesLocked), loc);
            this.takingPath.remove(loc);
            return false;
        }

        // In pure simulation mode the accessories are not really actuated, so there is nothing to
        // validate - skip the guard entirely (see setSimulate: sim requires debug + no connection).
        // Also skip when the user has disabled path integrity validation.
        if (this.simulate || !PATH_INTEGRITY_VALIDATION)
        {
            return true;
        }

        // Verify the accessories actually reached their commanded state (this wait does not hold the
        // Layout monitor).  If not, stop the locomotive and release its locks (returning false so
        // executePath does not run it); power is left on and autonomy re-attempts the path organically on
        // its next cycle, so no explicit retry is needed here.
        if (!this.validatePathActuation(path))
        {
            this.handleMisconfiguredPath(path, loc);
            this.takingPath.remove(loc);
            return false;
        }

        return true;
    }

    /**
     * Waits (up to PATH_VALIDATION_MS * (accessories on the path + 1), so longer paths get proportionally
     * more time) for every accessory on the path to reach its CS-confirmed commanded state.  Waits on
     * Accessory.actuationConfirmedMonitor (which MarklinAccessory notifies on each confirmed actuation)
     * rather than busy-polling, and returns as soon as all are confirmed - the full timeout only elapses
     * when an accessory never confirms.  Must be called WITHOUT holding the Layout monitor so concurrent
     * path checks are not blocked.
     * @param path
     * @return true if all accessories on the path are confirmed at their commanded state
     */
    private boolean validatePathActuation(List<Edge> path)
    {
        List<Accessory> accessories = new ArrayList<>();
        List<Boolean> desired = new ArrayList<>();

        for (Edge e : path)
        {
            for (String name : e.getConfigCommands().keySet())
            {
                Accessory acc = control.getAccessoryByName(name);

                if (acc != null)
                {
                    accessorySetting state = e.getConfigCommands().get(name);
                    accessories.add(acc);
                    desired.add(Accessory.isThrow(state));
                }
            }
        }

        if (accessories.isEmpty())
        {
            return true;
        }

        // Wait on the dedicated actuation monitor until all are confirmed or the timeout elapses.
        // MarklinAccessory notifies it each time a CS echo advances stateAtLastActuation, so we wake and
        // re-check only when a confirmed state actually changed - and exit immediately once all are confirmed.
        long deadline = System.currentTimeMillis() + PATH_VALIDATION_MS + PATH_VALIDATION_MS * accessories.size();

        synchronized (Accessory.actuationConfirmedMonitor)
        {
            while (!allConfirmed(accessories, desired))
            {
                long remaining = deadline - System.currentTimeMillis();

                if (remaining <= 0)
                {
                    break;
                }

                try
                {
                    Accessory.actuationConfirmedMonitor.wait(remaining);
                }
                catch (InterruptedException ex)
                {
                    // Interrupted: stop waiting, and ASK rather than assume (OB-080).  Returning true ("everything
                    // actuated") without looking would let a train onto turnouts nothing ever confirmed, the wrong
                    // direction for a guard to fail in.  allConfirmed is a pure read of state that has already arrived,
                    // cannot block, and gives the honest answer.  The interrupt is preserved for whatever is
                    // unwinding.  Unreachable today (nothing interrupts a dispatch thread from outside).
                    Thread.currentThread().interrupt();

                    return allConfirmed(accessories, desired);
                }
            }
        }

        return allConfirmed(accessories, desired);
    }

    /**
     * Whether every accessory in the list is confirmed at its corresponding desired state.
     */
    private boolean allConfirmed(List<Accessory> accessories, List<Boolean> desired)
    {
        for (int i = 0; i < accessories.size(); i++)
        {
            if (!accessories.get(i).isConfirmedAt(desired.get(i)))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Handles a path whose accessories could not be confirmed: stops the locomotive and releases the locks
     * it just acquired (leaving it at its start point), and logs the problem.  Power is deliberately left
     * on so other locomotives keep running unaffected; this locomotive simply does not depart, and the
     * released edges become available for whoever claims (and reconfigures) them next - including this loco
     * when autonomy re-attempts the path.  A UI alert (naming the locomotive and the unconfirmed
     * accessories) is only shown once failures reach PATH_VALIDATION_ALERT_THRESHOLD, so a one-off does not
     * alarm the user.
     * @param path
     * @param loc
     */
    private void handleMisconfiguredPath(List<Edge> path, Locomotive loc)
    {
        // LinkedHashSet: if a path repeats the same accessory across edges (e.g. a shared throat switch
        // referenced by two consecutive edges), isPathClear already guarantees it's commanded to the same
        // state at every occurrence, so the resulting string is identical each time and collapses here -
        // the operator-facing message names each misconfigured accessory once, in first-seen order.
        Set<String> misconfigured = new LinkedHashSet<>();

        // Guarded, because working out WHAT to say must not be able to stop the release below: this is also reached from the lock loop's
        // failure handler, where the failing call may be the same one that fails here (reading a path's configuration is how an edge is
        // set up).  A release that cannot happen leaves the track occupied for the rest of the session, far worse than an operator
        // message that names nothing.
        try
        {
            for (Edge e : path)
            {
                for (String name : e.getConfigCommands().keySet())
                {
                    Accessory acc = control.getAccessoryByName(name);
                    accessorySetting state = e.getConfigCommands().get(name);
                    boolean d = Accessory.isThrow(state);

                    // An accessory that is not in the database at all is named too - otherwise the
                    // operator is told the path is misconfigured without being told which part
                    if (acc == null || !acc.isConfirmedAt(d))
                    {
                        misconfigured.add(name + " (" + state.toString().toLowerCase() + ")");
                    }
                }
            }
        }
        catch (RuntimeException e)
        {
            this.control.log(e);
        }

        // Stop this locomotive so it cannot move onto the unset path.
        loc.setSpeed(0);

        // Release only the locks configureAndLockPath just took: mark the edges unoccupied and clear the
        // per-edge end-point assignments, but leave the loco at its start point (it never departed).
        synchronized (this)
        {
            for (Edge e : path)
            {
                e.setUnoccupied();

                if (loc.equals(e.getEnd().getCurrentLocomotive()))
                {
                    e.getEnd().setLocomotive(null);
                }
            }

            // Provably at its start, whatever the path's shape: clearing the ends above would also clear the start of a
            // path that loops back through it.  Re-reserved rather than placed, so a train that legitimately sat on
            // several points is not swept down to this one.
            path.get(0).getStart().reserve(loc);
        }

        String accList = String.join(", ", misconfigured);

        // Always log every failure to the console.
        control.logf("autolayout.errorPathMisconfigured", loc.getName(), accList);

        // Only raise a UI popup once failures cross the threshold, and at most once per Layout instance -
        // after that, the console log above is considered sufficient, so we do not keep interrupting the
        // user with popups for the rest of this session.
        boolean alert;

        synchronized (this)
        {
            pathValidationFailureCount++;
            alert = !pathValidationAlertShown && pathValidationFailureCount >= PATH_VALIDATION_ALERT_THRESHOLD;

            if (alert)
            {
                pathValidationAlertShown = true;
            }
        }

        if (alert)
        {
            control.showAutonomyAlert(
                I18n.f("autolayout.errorPathMisconfiguredDialog", loc.getName(), accList)
            );
        }
    }

    /**
     * Current number of path validation failures accumulated on this Layout (never reset; exposed for
     * tests).
     * @return the failure count
     */
    public int getPathValidationFailureCount()
    {
        synchronized (this)
        {
            return pathValidationFailureCount;
        }
    }

    /**
     * Whether the one-time UI alert has already been shown for this Layout instance (exposed for tests).
     * @return true if the alert has fired
     */
    public boolean hasShownPathValidationAlert()
    {
        synchronized (this)
        {
            return pathValidationAlertShown;
        }
    }

    /**
     * Marks all the edges in a path as unoccupied,
     * unlocking it so that other trains may pass
     * @param path
     * @param loc
     * @return edges whose own occupancy flag was left alone because another locomotive has since
     *         claimed their end point.  Informational only - their lock edges are still released, and
     *         the flag itself either belongs to that other locomotive now or was already cleared by
     *         executePath's early unlock, so there is nothing for the caller to act on.
     */
    synchronized public List<Edge> unlockPath(List<Edge> path, Locomotive loc)
    {
        // Releasing the path releases the claim on a slot with it: anything that locks and unlocks without running the
        // train would otherwise lower the cap for the rest of the session.
        this.takingPath.remove(loc);

        // WHAT THIS RUN STILL HOLDS, NOT WHAT THE SETTING SAYS NOW (GUI-A1).  The atomic road below gives every edge
        // back and empties every Point of the path, which is right for a run that held all of it to the end.  A run
        // that gave edges back early as its tail cleared them (Atomic Routes off while it ran) may have had them taken
        // by another train since, and the setting can be switched on under it (the Atomic Routes gate does, when a
        // train turns out to have no length); read at the end, the setting would give those edges back a second time
        // and empty the Points the other train holds.  So the road is chosen by what the run did: one that gave
        // anything back early takes the careful road, which skips what it gave back and leaves alone what another
        // train holds.
        //
        // WHAT WAS RELEASED, NOT WHAT THE TAIL CLEARED: `clearedEdges` is kept in atomic mode too and releases nothing
        // there, so an atomic run whose tail had passed anything would take the careful road and keep those edges and
        // their locks for good.  `releasedEarly` records only real releases.
        Set<Edge> givenBackEarly = this.releasedEarly.get(loc);

        boolean heldItAll = givenBackEarly == null || givenBackEarly.isEmpty();

        List<Edge> output = new LinkedList<>();

        for (int i = 0; i < path.size(); i++)
        {
            Edge e = path.get(i);

            if (this.atomicRoutes && heldItAll)
            {            
                // THE EDGE GIVEN BACK FIRST, so the start's guards are asked with this path no longer holding them (OB-340)
                e.setUnoccupied();

                if (i == 0)
                {
                    e.getStart().setLocomotive(null);
                }

                if (i < path.size() - 1)
                {
                    e.getEnd().setLocomotive(null);
                }
            }
            // With atomicRoutes disabled, we skip unlocking if a different locomotive now occupies the edge
            else
            {
                if (
                    // Edges may be out of sequence, so it's OK if another locomotive now occupies the start
                    //(loc.equals(e.getStart().getCurrentLocomotive()) || null == e.getStart().getCurrentLocomotive())
                       // &&
                    (loc.equals(e.getEnd().getCurrentLocomotive()) || null == e.getEnd().getCurrentLocomotive())
                )
                {
                    // What this run really gave back (GUI-A1) - not the cleared set, which atomic mode fills too.
                    Set<Edge> alreadyGivenUp = this.releasedEarly.get(loc);

                    if (alreadyGivenUp != null && alreadyGivenUp.contains(e))
                    {
                          // ALREADY RELEASED, WHOLE, WHEN THE TAIL PASSED IT (RC-A9, OB-164).  With atomicRoutes off
                          // this path gives each edge up twice, early (the moment tailHasProvablyPassed says the train
                          // is clear of it) and again here; occupancy counts, so the second release would take away a
                          // claim somebody else made in between.  NOTHING IS LEFT TO DO: the early release gives up the
                          // lock edges too (OB-164), so doing it again would take away a claim this path no longer
                          // holds.
                    }
                    else
                    {
                        e.setUnoccupied();
                    }
                }
                else
                {
                    output.add(e);

                    // Another locomotive has taken the end point, so we must not touch the edge's own flag - it may be
                    // that locomotive's lock now.  Its LOCK edges still have to be released, UNLESS the tail already
                    // gave this edge up, in which case they went with it (OB-164).
                    //
                    // Safe for a crossing declared symmetrically (each of the two edges naming the other as a lock
                    // edge), because the crossing edge is then itself part of any conflicting path, so isPathClear
                    // rejects that path on the edge's own occupancy flag while we hold it.  Symmetry is NOT "how the
                    // editor writes them": only GraphReducer writes symmetric locks, GraphEdgeEdit.applyLockEdges
                    // writes one direction, and the sample layout carries 104 asymmetric relations out of 118; and
                    // isPathClear DOES inspect lock edges, through Edge.isLockHeld.  Occupancy COUNTS (RC-A9): the
                    // caution about a hand-edited autonomy.json in which two edges name a third they never traverse
                    // stands because a count only balances if every claim is given back exactly once, which is what
                    // the given-up test above is for.
                    Set<Edge> givenUp = this.releasedEarly.get(loc);

                    if (givenUp == null || !givenUp.contains(e))
                    {
                        for (Edge lockEdge : e.getLockEdges())
                        {
                            lockEdge.setLockedEdgeUnoccupied();
                        }
                    }

                    if (this.control.isDebug())
                    {
                        this.control.logf(
                            "autolayout.infoSkippingUnlockDueToNonAtomicPaths",
                            e.getName()
                        );
                    }
                }
                
                if ((i == 0 || i != path.size() - 1) && loc.equals(e.getStart().getCurrentLocomotive()))
                {
                    e.getStart().setLocomotive(null);
                }

                if (i < path.size() - 1 && loc.equals(e.getEnd().getCurrentLocomotive()))
                {
                    e.getEnd().setLocomotive(null);
                }
            }
        }
        
        return output;
    }
        
    /**
     * Finds the shortest path between two points using BFS, never through a terminus that is not its end (OB-229).
     *
     * @param start where from
     * @param end where to
     * @param excludePaths routes already found, to be passed over
     * @return the route, or null
     * @throws Exception when either point is not on this layout
     */
    public List<Edge> bfs(Point start, Point end, List<List<Edge>> excludePaths) throws Exception
    {
        return bfs(start, end, excludePaths, false);
    }

    /**
     * Whether the track connects these two squares AT ALL, by any route a train may not take.
     *
     * The question `firstClearOrWhyNot` puts to the track rather than to a train: the search above refuses a route
     * through a terminus that is not its end (OB-229) and one that doubles back through another copy of its own start
     * or end (AMR-B1), so "nothing found" there covers three different answers - no rails, a terminus in the way, and
     * only a lap.  This one walks all of them, and the caller decides which sentence to give.  Also asked by
     * `HomeStaging.auditAgainstRuntime`, to tell a destination only a lap reaches (where the planner and the menu are
     * MEANT to disagree: the copy rule is on the menu and autonomy alone) from a genuine planner defect.  Nothing that
     * sends a train asks this: `isPathClear` refuses a route through a terminus, and selection refuses the lap.
     *
     * @param start where from
     * @param end where to
     * @return true when some route exists, whatever a tier would make of it
     * @throws Exception when either point is not on this layout
     */
    public boolean anyTrackRouteBetween(Point start, Point end) throws Exception
    {
        return bfs(start, end, null, true) != null;
    }

    /**
     * The same search, with a choice about termini.
     *
     * Through them only for a question about the TRACK rather than about a route a train may take: Why Not Moving?
     * asks it to tell "a terminus is in the way" from "no track leads there" (PTR-B1).  Nothing that sends a train
     * asks it - `isPathClear` refuses every route through a terminus.
     *
     * @param start where from
     * @param end where to
     * @param excludePaths routes already found, to be passed over
     * @param throughTermini true to extend a route through a terminus that is not its end
     * @return the route, or null
     * @throws Exception when either point is not on this layout
     */
    private List<Edge> bfs(Point start, Point end, List<List<Edge>> excludePaths, boolean throughTermini) throws Exception
    {
        start = this.getPoint(start.getName());
        end = this.getPoint(end.getName());
                
        if (start == null || end == null)
        {
            throw new Exception(
                I18n.f("autolayout.errorInvalidPointsSpecified")
            );
        }
        
        if (!end.isDestination())
        {
            return null;
        }
        
        // A set, not a list: probed with contains() once per neighbour of every point dequeued (Point overrides both
        // equals and hashCode).  Marked on DEQUEUE rather than on enqueue, deliberately: every caller passes
        // excludePaths, and the search depends on reaching a point by several different routes to find one that is not
        // excluded.
        //
        // THE SQUARE AND THE SWITCHES SET ON THE WAY THERE, not the square alone (RSA59-B1).  Marking a square visited
        // the first time any route reached it would let a shorter route that sets a switch one way and then the other
        // (refused later for its conflicting commands) hide a clear route reaching the same square with the switch
        // untouched, so which routes callers got would depend on the order `getNeighbors` shuffles into.  A square is
        // passed over only when it has been reached already under switch settings that leave at least as much freedom,
        // Return Home's planner's own rule (`HomeStaging.alreadyReached`), shared.  A route already in conflict is
        // still followed, and still returned when it reaches the end (Why Not Moving? says so where it is all there
        // is), but under the square-alone rule, so it can neither hide a clear route nor go round a loop.  NOT FOR THE
        // TRACK-ONLY QUESTION (`throughTermini`): whether any track connects two points does not depend on the switches.
        if (!throughTermini) return bfsBySettings(start, end, excludePaths);

        Set<Point> visited = new HashSet<>();
        Queue<PointPath> queue = new LinkedList<>();
        
        queue.add(new PointPath(start, new LinkedList<>()));
        
        while (!queue.isEmpty())
        {
            PointPath current = queue.remove();
            Point point = current.start;
            List<Edge> path = current.path;
            
            visited.add(point);
            
            for (Edge next : this.getNeighbors(point))
            {
                if (next.getEnd().equals(end))
                {
                    path.add(next);
                                        
                    // Path is not within the list of disallowed paths - return it
                    if (excludePaths == null || !excludePaths.contains(path))
                    {                                  
                        //this.control.log("Path: " + this.pathToString(path));
                        return path;
                    }
                    // Path is disallowed - continue and get another one
                    else
                    {
                        path.remove(path.size() - 1);
                    }
                }
                // NOT ON THROUGH A TERMINUS (OB-229).  A route may END at one; it may not pass through, and
                // `isPathClear` refuses every route that does ("Contains an intermediate terminus station").  A square
                // is marked visited the first time it is taken off the queue, so extending a terminus anyway would spend
                // the square on a route that could never be used and never try the longer way through it.  The
                // planner's own search stops at a terminus the same way.
                // NOR THROUGH ANOTHER COPY OF THE SQUARE IT STARTS OR ENDS AT (AMR-B1).  A square drawn as several Points is one piece of
                // track, so a route that comes back through another copy of the train's own square has gone round a loop to where it
                // already was, and one that passes another copy of its destination drives through that platform to reach it.  Adam: **"We
                // need to refuse both.  A copy makes a cycle."**  NOT in the form that asks about the TRACK (`throughTermini`): that one
                // tells "a terminus is in the way" from "no track leads there" (PTR-B1), and a route it may not walk would come back as
                // missing track.  `firstClearOrWhyNot` asks it, and says which of the two it found.
                else if (!visited.contains(next.getEnd()) && (throughTermini || !next.getEnd().isTerminus())
                    && (throughTermini || !next.getEnd().isSamePlaceAs(start))
                    && (throughTermini || !next.getEnd().isSamePlaceAs(end)))
                {
                    List<Edge> newPath = new LinkedList<>(path);
                    newPath.add(next);
                    queue.add(new PointPath(next.getEnd(), newPath));
                }
            }
        }
                
        return null;   
    }
    
    /**
     * One step of `bfsBySettings`: where it has got to, the route there, and the switch commands that route sets - null
     * once two of them have disagreed.
     */
    private static final class SettingsStep
    {
        final Point at;
        final List<Edge> path;
        final Map<String, Accessory.accessorySetting> commands;

        SettingsStep(Point at, List<Edge> path, Map<String, Accessory.accessorySetting> commands)
        {
            this.at = at;
            this.path = path;
            this.commands = commands;
        }
    }

    /**
     * The shortest route from start to end that is not among those excluded, never through a terminus or another copy
     * of either end, searched by square AND the switch settings on the way (RSA59-B1) - see the note in `bfs`.
     *
     * @param start where from
     * @param end where to, a destination
     * @param excludePaths routes already found, to be passed over
     * @return the route, or null
     */
    private List<Edge> bfsBySettings(Point start, Point end, List<List<Edge>> excludePaths)
    {
        // MARKED WHEN TAKEN OFF THE QUEUE, as `bfs` marks a square, and for its reason: routes of the same length to
        // a square are all followed on from it, so a caller that has excluded one of them still finds the next
        Map<String, List<Map<String, Accessory.accessorySetting>>> seen = new HashMap<>();
        Set<Point> left = new HashSet<>();
        Queue<SettingsStep> queue = new LinkedList<>();

        queue.add(new SettingsStep(start, new LinkedList<>(), new HashMap<>()));

        while (!queue.isEmpty())
        {
            SettingsStep current = queue.remove();

            left.add(current.at);

            if (current.commands != null && !HomeStaging.alreadyReached(seen, current.at.getUniqueId(), current.commands))
            {
                seen.computeIfAbsent(current.at.getUniqueId(), k -> new ArrayList<>()).add(current.commands);
            }

            for (Edge next : this.getNeighbors(current.at))
            {
                Map<String, Accessory.accessorySetting> commands = current.commands == null ? null
                    : HomeStaging.withCommandsOf(next, current.commands);

                if (next.getEnd().equals(end))
                {
                    List<Edge> path = new LinkedList<>(current.path);
                    path.add(next);

                    if (excludePaths == null || !excludePaths.contains(path)) return path;

                    continue;
                }

                // Never through a terminus that is not the end (OB-229), nor round onto another copy of either end (AMR-B1)
                if (next.getEnd().isTerminus() || next.getEnd().isSamePlaceAs(start)
                    || next.getEnd().isSamePlaceAs(end)) continue;

                // Passed over when this square has been left already under settings that leave as much freedom.
                //
                // A ROUTE ALREADY IN CONFLICT KEEPS THE SQUARE-ALONE RULE `bfs` HAS ALWAYS HAD: a square any route has left is closed to
                // it.  It is followed only so that Why Not Moving? can name the conflict where that is all there is, so it must find
                // nothing the old search could not (given a square set of its own it went round loops the clear routes were kept out of).
                if (commands == null ? left.contains(next.getEnd())
                    : HomeStaging.alreadyReached(seen, next.getEnd().getUniqueId(), commands)) continue;

                List<Edge> path = new LinkedList<>(current.path);
                path.add(next);

                queue.add(new SettingsStep(next.getEnd(), path, commands));
            }
        }

        return null;
    }

    /**
     * Whether a path would turn the train round on its way somewhere else.
     *
     * executePathInternal stops and reverses at EVERY reversing point a train reaches, not only at the end of a path, so a route
     * through one is a shunting move in the middle of a journey: the train halts somewhere it was not going, changes direction,
     * and carries on.
     *
     * The rule (Adam's): IN FULL AUTONOMY A TRAIN IS ONLY EVER REVERSED AT A TERMINUS.  Everything else that would turn it round is
     * off the through-network, berth and headshunt alike (exempting reversing loops and headshunts let a train with a locomotive at
     * one end only be turned round mid-journey and sent on running backwards in service).
     *
     * The tier matters as much as the rule.  This is asked by pickPath, by the probe that mirrors it, and by the "why is it not
     * moving" explanation - the three places full autonomy chooses.  It is NOT asked by isPathClear, so a hand-driven move and the
     * staging planner bringing a train home can still use a headshunt, which is what they are for and where somebody is watching.
     *
     * The origin is exempt - a train standing on one is free to leave it - so only the END of each edge is tested.
     */
    private boolean reversesAlongTheWay(List<Edge> path)
    {
        for (Edge e : path)
        {
            if (e.getEnd().isReversing())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether full autonomy would actually have somewhere to send this locomotive.
     *
     * getPossiblePaths answers the MANUAL question - what the right-click route menu offers - and by design that includes reversing
     * stations and destinations that exclude the locomotive, neither of which pickPath will ever choose.  Yielding on the manual
     * answer makes a train parked on purpose read as one merely waiting its turn: its idle time only grows, so it wins every
     * "longest waiting" comparison and each running locomotive stops for YIELD_SECONDS to let it go first, for a dispatch that will
     * never happen.
     *
     * pickPath itself cannot serve as the probe (it sleeps for minDelay when it finds nothing), so the enumeration is filtered
     * instead.  Only the clauses that diverge are re-tested here; active, unoccupied, is-a-destination and
     * reachable-by-a-clear-path have already been applied by getPossiblePaths and isPathClear.
     *
     * Every clause pickPath applies to its candidates has to be mirrored here, and that is the whole maintenance
     * burden of this method: anything added to pickPath's selection belongs here too.
     */
    private boolean hasAutonomousDestination(Locomotive loc)
    {
        // uniqueDest false on purpose: the flag only gates what the enumeration COLLECTS, so asking for every clear path costs
        // nothing extra, and one per start/end pair would make this probe under-report (the single path kept for a pair may be the
        // one that crosses a berth while a berth-free alternative to the same destination exists, which pickPath would have found).
        // Each terminus asked once, however many routes end there (E2E-B1)
        java.util.Map<Point, Boolean> leaves = new java.util.HashMap<>();

        for (List<Edge> path : this.getPossiblePaths(loc, false))
        {
            Point end = path.get(path.size() - 1).getEnd();

            // The shared clauses through the one method (DR-B3), the terminus clause with them: this loop mirrors
            // pickPath clause for clause (see the javadoc).  A small TIGHTENING (MON-C4): `getPossiblePaths` filters on
            // `end.isDestination()` and deliberately no more, so a candidate can arrive here inactive, reversing, or
            // marked as somewhere autonomy does not choose, and this is where those are refused.
            if (isSendableDestination(end)
                    && (!end.isTerminus()
                        || (loc.isReversible() && leaves.computeIfAbsent(end, t -> this.leavesForAStationItFits(t, loc))))
                    && !end.excludes(loc)
                    && !this.reversesAlongTheWay(path))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if any locomotive has been inactive longer than the threshold time, and returns the locomotive with the oldest timestamp
     * @param threshold seconds
     * @param currentLoc the current locomotive
     * @return 
     */
    public Locomotive checkForSlowerLoc(int threshold, Locomotive currentLoc)
    {
        // Calculate locomotive that has been inactive the longest
        Locomotive minLoc = null;
        
        for (Locomotive l : this.locomotivesToRun)
        {
            if (l.isAutonomyPaused()) continue;
            
            if (minLoc == null || l.getLastPathTime() < minLoc.getLastPathTime())
            {
                minLoc = l;
            }
        }
        
        if (minLoc != null && !currentLoc.equals(minLoc) && (currentLoc.getLastPathTime() - minLoc.getLastPathTime()) > threshold * 1000
                && this.hasAutonomousDestination(minLoc))
        {
            int waited = (int) ((currentLoc.getLastPathTime() - minLoc.getLastPathTime()) / 1000);
            
            this.control.logf(
                "autolayout.infoLocomotiveYieldingForInactive",
                currentLoc.getName(),
                YIELD_SECONDS,
                minLoc.getName(),
                waited
            );
            return minLoc;
        }
        
        return null;
    }
    
    /**
     * Continuously looks for a valid path for the given locomotive, and executes the path when found
     * @param loc
     * @param speed how fast the locomotive should travel, 1-100
     * @throws java.lang.Exception
     */
    public void runLocomotive(Locomotive loc, int speed) throws Exception
    {
        if (speed < 1 || speed > 100)
        {
            throw new Exception(
                I18n.f("autolayout.errorInvalidSpeedSpecified")
            );
        }
        
        new Thread( () ->
        {    
            // Counted before the first check and released in the finally below, so a locomotive
            // choosing its next path is never invisible to isRunning()
            this.locomotiveThreads.incrementAndGet();

            try
            {
            while(running)
            {                
                // NOTHING CHOSEN, NOTHING SENT, WHILE THE TRACK POWER IS OFF (GST-B1): the loop waits, and once the power is back asks again
                // whether the run still goes before it chooses.
                if (this.waitWhileThePowerIsOff()) continue;

                // THE STOPS COUNTED BEFORE THE CHOICE, carried to the journey (RSA2-C2): `running` is asked again once
                // the choice is made, and a Yes landing after that question is one the journey still obeys
                final int stopsAtChoice = this.stopsOrdered.get();

                List<Edge> path = this.pickPath(loc);

                // AND STILL RUNNING once the choice is made (RSA-C1): a choice takes the railway's monitor, which a
                // dispatch holds for as long as it throws its switches, and a stop - graceful, or the operator's Yes -
                // that came meanwhile sends nothing.
                if (path != null && running)
                {
                    // Guarded for the same reason the callbacks are: executePath's own handler deliberately does NOT unlock a failed path, so
                    // an exception escaping here would kill this locomotive's thread with its track still held.  Logged and carried on
                    // instead.
                    try
                    {
                        this.executePath(path, loc, speed, null, ALWAYS_REVERSE, stopsAtChoice);
                    }
                    catch (Throwable e)
                    {
                        this.control.log(e instanceof Exception ? (Exception) e : new Exception(e));
                    }
                }
                else if (this.getMinDelay() <= 0)
                {
                    // Boxed in, and no delay configured to throttle the retry (see NO_PATH_IDLE_MS)
                    loc.delay(NO_PATH_IDLE_MS);
                }

                // Skipped once the run has been asked to stop: this paces one path against the next, and after a
                // graceful stop paying it would only delay the thread exiting, which isRunning() waits for.
                if (running) loc.delay(this.getMinDelay() * 1000);

                // If another locomotive is falling behind, attempt to yield to it
                if (this.isAutoRunning() && this.maxLocInactiveSeconds > 0)
                {
                    Locomotive yieldLoc = this.checkForSlowerLoc(this.maxLocInactiveSeconds, loc);

                    // running re-checked HERE, not only at the top of the block: this waits up to YIELD_SECONDS (thirty) for a train to move,
                    // and after a stop none is going to.  The outer guard stops a yield being ENTERED after a stop, not one already under way.
                    if (yieldLoc != null && running)
                    {
                        yieldLoc.blockUntilMotion(YIELD_SECONDS);
                    }
                }                   
            }
            }
            finally
            {
                // In a finally, so a thread killed by anything at all still stops being counted, or the layout reports itself as running
                // for ever.
                if (this.locomotiveThreads.decrementAndGet() == 0)
                {
                    announceRunFinished();
                }
            }
        }).start();
    }
    
    /**
     * Returns the current location of the given locomotive
     * @param loc
     * @return 
     */
    public Point getLocomotiveLocation(Locomotive loc)
    {
        for (Point start : this.points.values())
        {
            if (loc.equals(start.getCurrentLocomotive()))
            {
                return start;
            }
        }

        return null;
    }

    /**
     * WHERE A TRAIN'S BODY IS - the one statement of it (MT-438, VD12-B1).
     *
     * `getLocomotiveLocation` above answers "which Point holds this locomotive", and while a path is locked that is
     * SEVERAL Points (`Point.reserve` deliberately does not sweep: the reservation holds a junction behind the train
     * against a second train reaching it another way).  It returns the first in `HashMap` iteration order, which can
     * be the destination the train has not reached, so every reader that means "where is the train" would get an
     * arbitrary answer for the whole of a run.  The MILESTONES are the Points the run has reported reaching, in
     * order, so the last is where the train actually is.  One method, so the next door asks instead of working it out
     * again.
     *
     * **NO CENSUS OF CALLERS HERE (VD13-C2, VD14-C4, VD15-B2/B3)**: enumerating them in a comment failed three times.
     * The rule is ASK THIS METHOD WHEN YOU MEAN "WHERE IS THE TRAIN", and `getLocomotiveLocation` only when you mean
     * "which Point holds it".  For a train that is standing the two agree, which is why most callers are right as
     * they are.  The s88 arm of the static `Route.evaluate` ("train X at sensor T") asks this too (OB-250; "X is at T
     * now" is the last sensor X reached, or where it stands).  NOT asked by `AutoLocomotiveStatus`'s @-station line,
     * which shows NOTHING rather than a reservation where a run has reported no milestone yet: that is a decision
     * about a line of text, and this rule's fallback would change it.
     *
     * @param loc the locomotive
     * @return where its body is, or null when nothing holds it
     */
    public Point whereTheTrainIs(Locomotive loc)
    {
        Point reached = this.lastMilestone(loc);

        if (reached != null) return reached;

        // THEN THE START OF THE ROAD IT HAS BEEN GIVEN, which a milestone cannot cover: a path is locked before the
        // train moves, and the previous run's milestones are cleared when that run ends, so between the lock and the
        // first sensor a train has reserved half the railway and reported nothing, and asking which Point holds it
        // would give the arbitrary answer this method exists to avoid.  `takingPath` as well as `activeLocomotives`,
        // for the window `configureAndLockPath` opens before its caller records the run (RC-A10, VD12-B3): the claims
        // first (RSA4-C3), see `pathHeldBy`.
        List<Edge> given = this.pathHeldBy(loc);

        if (given != null && !given.isEmpty() && given.get(0).getStart() != null)
        {
            return given.get(0).getStart();
        }

        return this.getLocomotiveLocation(loc);
    }

    /**
     * The last Point a run has reported reaching, or null (MT-438).
     *
     * @param loc the locomotive
     * @return its latest milestone
     */
    Point lastMilestone(Locomotive loc)
    {
        if (loc == null) return null;

        // THROUGH THE ACCESSOR, not the field (VD12-B1): `DiagramMonitor`'s test double overrides `getReachedMilestones` and
        // `getLocomotiveLocation` and nothing else, so a rule that reads the map directly cannot be tested through that door
        // (`core.testAutonomyDiagramMonitor`).
        List<Point> milestones = this.getReachedMilestones(loc);

        if (milestones == null || milestones.isEmpty()) return null;

        return milestones.get(milestones.size() - 1);
    }
       
    /**
     * Picks a random (valid and unoccupied) path for a given locomotive
     * and returns the path
     * @param loc 
     * @return  
     */
    public List<Edge> pickPath(Locomotive loc)
    {
        if (loc.isAutonomyPaused()) return null;
        
        List<Point> ends = new LinkedList<>(this.points.values());
        Collections.shuffle(ends);

        // Now sort by priority - and it is a STABLE sort, which is the whole mechanism: the shuffle above makes every rule pick at
        // random between equals, and a stable sort keeps that order within a band.  The band gate downstream READS THIS ORDER to
        // decide when a band has ended, so nothing may reorder `ends` after this without teaching the gate about it.
        Collections.sort(ends, (Point p1, Point p2) ->
        {
            // EQUAL, ALWAYS, FOR THE RULE THAT IGNORES PRIORITY (OB-156): a stable sort whose comparator never distinguishes anything
            // leaves the shuffle exactly as it was, which is what "completely at random" means.  Expressed here rather than by
            // skipping the sort, so that there is no second code path in which `ends` is built a different way.  RANDOM_ANY_STATION
            // is not also excused from the band gate below: both random rules return the first route that works before `best` is
            // ever assigned, so the gate never sees it.
            if (this.pathPreference == PathPreference.RANDOM_ANY_STATION)
            {
                return 0;
            }

            // Random order if equivalent
            if (p1.getPriority() == p2.getPriority())
            {
                return 0;
            }

            // Points with higher priority will come first
            return p2.getPriority() < p1.getPriority() ? -1 : 1;
        });

        for (Point start : this.points.values())
        {
            if (loc.equals(start.getCurrentLocomotive()) 
                    && start.isActive() && start.isDestination() // not needed from a validation perspective, but will speed things up
            )
            {
                // The best route found so far, within one band of station priority: banded, because priority must still win (a station
                // the user marked important is not beaten by a shorter route to an ordinary one), so ranking happens BETWEEN equals.
                List<Edge> best = null;
                int bestCost = Integer.MAX_VALUE;
                Integer band = null;

                for (Point end : ends)
                {
                    // THE BAND GATE, WHICH ONE RULE DELIBERATELY DOES NOT PASS THROUGH: a band is settled before the
                    // next one is looked at, which makes priority absolute for every other rule, so "the highest
                    // priority available station" needs no preference of its own.  BALANCED_PRIORITY weighs priority
                    // AGAINST distance, so a nearer, less important station is allowed to win; returning here would
                    // make that impossible.
                    if (this.pathPreference != PathPreference.BALANCED_PRIORITY
                        && best != null && band != null && !band.equals(end.getPriority()))
                    {
                        return best;
                    }

                    band = end.getPriority();

                    // Reversing stations are parking, not traffic: they are chosen only in semi-autonomous operation,
                    // where the user picks the route.  The exclusion belongs here and not in isPathClear, because
                    // executeTimetable sets running, so an isAutoRunning() fence would also refuse the "return home"
                    // staging run, which is meant to fill these tracks.  Filtering at selection, never refusing at
                    // execution, is the same tier split the excluded-locomotive rule uses.
                    // getBlockLocomotive, not isOccupied: a destination whose sibling copy holds a train is not free -
                    // it is the same piece of track, and sending a second train there is a collision.
                    // AND NOT A TERMINUS IT COULD NOT LEAVE: a locomotive that cannot reverse is stranded by a
                    // terminus, so full autonomy does not choose one for it, while the operator asking for that berth by
                    // hand is not refused.  NOR ONE IT COULD NOT LEAVE AT ITS LENGTH (E2E-B1): a reversible train is
                    // sent to a terminus only where some station it fits is reachable from there again.  The four
                    // shared clauses are `isSendableDestination`; what stays here is what only this site can ask - the
                    // square it started on, whether anything is standing there NOW, and the two per-locomotive
                    // questions (DR-B3).
                    if (!end.equals(start) && end.getBlockLocomotive() == null
                            && isSendableDestination(end)
                            && (!end.isTerminus() || (loc.isReversible() && this.leavesForAStationItFits(end, loc)))
                            && !end.excludes(loc))
                    {
                        try 
                        {
                            // If the first shortest path is invalid, check all alternatives                            
                            List<Edge> path;
                            List<List<Edge>> seenPaths = new LinkedList<>();

                            do
                            {
                                path = this.bfs(start, end, seenPaths);

                                if (path != null && !this.reversesAlongTheWay(path)
                                        && this.isPathClear(path, loc, false))
                                {
                                    // Whatever works, at once: the behaviour every version before the preference existed had, and the
                                    // cheap one (the ranked options have to enumerate the alternatives to compare them).
                                    if (this.pathPreference == PathPreference.RANDOM
                                        || this.pathPreference == PathPreference.RANDOM_ANY_STATION)
                                    {
                                        return path;
                                    }

                                    int cost = this.costOf(path);

                                    if (cost < bestCost)
                                    {
                                        bestCost = cost;
                                        best = path;
                                    }

                                    // Accepted, and still recorded, or bfs hands back the same route
                                    // for ever and the enumeration never ends
                                    seenPaths.add(path);
                                }
                                else if (path != null)
                                {
                                    // Get another path other than the one we just saw
                                    seenPaths.add(path);
                                }

                            } while (path != null);
                        }
                        catch (Exception e)
                        {
                            // Not silent: execution falls through to the "no free paths" message, which reports a normal, expected
                            // condition, so a failure here would be indistinguishable from having nowhere to go and the retry loop would
                            // call this forever with nothing in the log.
                            this.control.logf("autolayout.errorPathSelectionFailed", loc.getName());
                            this.control.log(e);
                        }
                    }
                }

                // The last band, which has no successor to close it
                if (best != null) return best;

                break;
            }
        }

        this.control.logf(
            "autolayout.infoLocomotiveNoFreePaths",
            loc.getName()
        );          
        // Throttles the retry while a run is going.  After a stop there is no retry, so waiting here would only delay
        // this thread noticing it should exit.
        if (running) loc.delay(minDelay, maxDelay);

        return null;
    }
    
    /**
     * The length of the square an edge arrives on - its end Point's sensor square - which that sensor reports the head
     * ENTERING, so which is not yet known to be behind the head when it answers (GS-B3).  The last of the edge's places,
     * which is `GraphReducer.placesAlong`'s shape: the steps, then the square arrived at.  0 where the edge records no
     * places (a hand-written configuration), and never more than the edge itself.
     *
     * @param edge the edge
     * @return the arriving square's length
     */
    private static int arrivingSquare(Edge edge)
    {
        List<Integer> lengths = edge == null ? null : edge.getPlaceLengths();

        if (lengths == null || lengths.isEmpty()) return 0;

        Integer last = lengths.get(lengths.size() - 1);

        return last == null ? 0 : Math.max(0, Math.min(last, edge.getLength()));
    }

    /**
     * Whether an edge the head has passed may be handed back - reported clear, and unlocked.
     *
     * ONE rule, because both things this decides are safety-relevant (WK-B1).  Reporting an edge clear is what stops
     * `MarklinRoute.heldReason` refusing a route that would set an accessory on it, and with atomicRoutes on the lock is held for
     * the whole run by design, so being reported clear is the only thing that drops that protection; unlocking hands the rails to
     * another train.  Neither may rest on a guess about where the tail is: "cannot be known" has to mean "assume the train is
     * still there".
     *
     * The one escape that is NOT a guess about this train is the first clause below: where nothing on a path has a
     * length, distance can never accumulate, so holding would hold until the route ended, the ordinary case on a
     * layout nobody has measured.  The cost: on a path with lengths on SOME edges, an edge whose measured distance
     * behind never reaches the train's length is held until the route ends.  The cure is to measure those edges.
     *
     * @param pathIsUnmeasured whether nothing on this path is measured (`pathIsUnmeasured`: no edge with a length, or with
     *        every square answered 0 - TDU-C6) - computed over the whole path before the run, not sampled from a running
     *        total
     * @param behind how far the head has travelled since the END of the edge in question
     * @param trainLength the train's length, or null
     * @return whether the tail is provably clear of it
     */
    public static boolean tailHasProvablyPassed(boolean pathIsUnmeasured, int behind,
        Integer trainLength)
    {
        // Nothing measured ANYWHERE on this path - a property of the whole path, asked once (VAL-A1): a RUNNING total is true at the
        // start of every run and stays true until the first measured edge is crossed, so edges [0, 100, 100] with a 250 train would
        // hand edge 0 back on the very first step with the whole train on it (an unmeasured first edge is the ordinary case on a
        // railway with lengths on its platforms and nowhere else).  Not a guess about THIS train: where nothing has a length,
        // distance can never accumulate, so holding would hold until the route ended.
        if (pathIsUnmeasured) return true;

        int length = trainLength == null ? 0 : trainLength;

        // Measured, and the measurement covers the whole train.
        return behind >= length;
    }


    /**
     * Whether NOTHING on this path is measured - the premise of `tailHasProvablyPassed`'s escape (VAL-A1).
     *
     * Asked of each edge by `Edge.isMeasured`, the question the Atomic Routes gate asks of the railway
     * (`unmeasuredTrackThatCouldBeReleased`): track answered 0 on purpose is measured (Adam, TDU-C6: *"0 lengths count as
     * measures"*), so a path over it is not given the escape: its edges are held until the measured track the head runs after
     * them covers the train, and to the end of the route where none does - the safe direction.
     *
     * @param path the route
     * @return true when no edge on it is measured
     */
    static boolean pathIsUnmeasured(List<Edge> path)
    {
        for (Edge edge : path)
        {
            if (edge != null && edge.isMeasured()) return false;
        }

        return true;
    }

    /**
     * Why this locomotive cannot leave, for the tier asking.
     *
     * @param loc the locomotive
     * @param byHand whether the question is about a route picked by hand
     * @return the reason, ready to show, or null when the train is free to be given a route
     */
    public String explainCannotStart(Locomotive loc, boolean byHand)
    {
        return layoutExplanations.explainCannotStart(loc, byHand);
    }

    /**
     * Why this locomotive cannot leave at all, or null when it can.
     *
     * The six reasons that have nothing to do with any particular destination - paused, off the graph,
     * facing a way trains may not arrive, not a station, switched off, and standing where no station autonomy may choose
     * can be reached (OB-299): they are about the train and the square it is standing on, and while one of them holds no
     * destination is worth asking about.
     *
     * @param loc the locomotive
     * @return the reason, ready to show, or null when the train is free to be given a route
     */
    public String explainCannotStart(Locomotive loc)
    {
        return layoutExplanations.explainCannotStart(loc);
    }

    /**
     * The reasons and the grouping, answered together under one lock (FBR-C6).
     *
     * Asked as two calls, each synchronized on this Layout, the window's two halves could be computed against two different states
     * (a train arrives, or a station is switched off, between them) and a line would sit under the wrong heading, contradicting the
     * reason FR-017 was built around a single `barredFromAutonomy`: that the reason printed beside a station and the group it is
     * printed under cannot disagree.
     *
     * @param loc the locomotive asking
     * @return both halves, of one moment
     */
    synchronized public Destinations explainDestinationsGrouped(Locomotive loc)
    {
        return new Destinations(explainDestinations(loc), destinationsBarredFromAutonomy(loc));
    }

    /**
     * The same, for either tier (MT-434).  By hand, the reasons `explainDestinations(Locomotive, boolean)` gives a
     * hand-driven send, and nothing in the group autonomy will never choose: what autonomy will not do is not the
     * question a person sending a train has asked.
     *
     * @param loc the train
     * @param byHand true for a hand-driven send
     * @return the reasons and the barred stations, taken under one lock
     */
    synchronized public Destinations explainDestinationsGrouped(Locomotive loc, boolean byHand)
    {
        if (!byHand) return explainDestinationsGrouped(loc);

        return new Destinations(explainDestinations(loc, true), new java.util.LinkedHashSet<String>());
    }

    /**
     * Why each station is unavailable, and which of them autonomy would never choose anyway.
     *
     * A holder rather than two return values.  It exists so the two can be taken together; handing
     * them back separately would let a caller acquire them separately again, which is the defect.
     */
    public static class Destinations
    {
        private final java.util.Map<String, String> reasons;
        private final java.util.Set<String> barred;

        Destinations(java.util.Map<String, String> reasons, java.util.Set<String> barred)
        {
            this.reasons = reasons;
            this.barred = barred;
        }

        /**
         * @return point name to the reason it is unavailable, null where it is available
         */
        public java.util.Map<String, String> getReasons()
        {
            return reasons;
        }

        /**
         * @return the point names autonomy will never choose, whatever is happening on the railway
         */
        public java.util.Set<String> getBarred()
        {
            return barred;
        }
    }

    /**
     * Whether autonomy could ever choose this point as a destination.
     *
     * The standing bars only - inactive, reversing, not an auto destination - asked through the same
     * `barredFromAutonomy` the "no available paths" window groups by, so there is ONE list of reasons a station is
     * never picked (the diagram's captions are a third caller).
     *
     * Asked with no locomotive, deliberately: per-locomotive exclusions are a fact about one train, and this answers a
     * question about the SQUARE - a station excluded for one locomotive is still somewhere trains are sent, and its
     * name still belongs on the diagram.
     *
     * **It does not ask `isDestination()`, deliberately (MON-C5).**  `isSendableDestination` has that clause; this
     * one is "will autonomy ever pick this SQUARE", asked by callers that already know they hold one (the diagram's
     * captions walk the copies of a named station; the right-click menu asks it about the end of a path, which
     * `getPossiblePaths` has already required to be a destination).  At the caption door adding the clause would
     * change the wrong thing: an inactive terminus passes that flag and is exactly what Adam wanted gone.  A caller
     * that hands this a square autonomy has no business stopping at is asking the wrong method.
     *
     * @param end the candidate station
     * @return true when nothing standing bars it
     */
    synchronized public boolean isChoosableByAutonomy(Point end)
    {
        return end != null && barredFromAutonomy(end, null) == null;
    }

    /**
     * Whether an operator should be offered this station for this train AT ALL.
     *
     * Not the same question as `isChoosableByAutonomy`, and deliberately a much smaller one: that asks whether
     * autonomy would ever *pick* a square; this asks whether a human may *send* a train there, and almost everything
     * answers yes (a parking track, a reversing point, a square marked as not an automatic destination are all
     * somewhere an operator may legitimately go by hand).
     *
     * Two things are not offered, both the operator's own settings, saying this train does not belong there:
     *
     * - A SWITCHED-OFF square (Adam: *"make the inactive stations disappear from the track diagram menu - they should
     *   only be visible in the autonomy tab."*)
     * - A square that EXCLUDES this locomotive (Adam: *"if it excludes the loc, don't even include it in the list."*)
     *
     * **A TERMINUS IS NOT ONE OF THEM**, which is the whole reason this method exists rather than a locomotive
     * argument on `isChoosableByAutonomy`: passing a locomotive brought the terminus clause with it, and a square
     * the fixture emits twice (once as a through arrival, once as a reverse arrival that is a terminus) dropped out
     * of the base list for a non-reversible train, though the square excludes nothing.  The base list keeps the same
     * options unless they are unselectable, and a terminus is selectable (the NO TERMINUS RULE HERE note in
     * `isPathClear`).
     *
     * @param end the candidate station
     * @param loc the locomotive being offered it
     * @return false only where the operator has said this train does not belong there
     */
    synchronized public boolean isOfferableToOperator(Point end, Locomotive loc)
    {
        return whyNotOfferedToOperator(end, loc) == null;
    }

    /**
     * Why the right-click menu leaves this station out for this train, or null when it offers it (AMR-B2).
     *
     * The rule of `isOfferableToOperator`, answering with the reason, so that Why Not Moving? on Manual asks the
     * menu's own question rather than a copy of it.
     *
     * @param end the candidate station
     * @param loc the locomotive being offered it
     * @return the reason, already translated, or null
     */
    private String whyNotOfferedToOperator(Point end, Locomotive loc)
    {
        return layoutExplanations.whyNotOfferedToOperator(end, loc);
    }

    /**
     * Whether a train can stand on this square without being turned round (MT-367).
     *
     * **PUBLIC because the repair of `whyNotOfferedToOperator` could not otherwise be tested** (SVV-C4, Adam: *"make it public and
     * add the tests"*): both callers inside this file hand it a copy already established to be a turning copy, which is never the
     * answer, so including the asked-about copy or skipping it gave the identical answer at every call the program makes, and
     * SEV-C1's repair was indistinguishable from the defect it repaired.
     *
     * The contract, which `core.testWhatHasAWayThrough` asks in all three shapes:
     *
     *   - a PLAIN copy answers true about itself - it is the way through;
     *   - every copy of a compulsory turn answers false - there is no way to stand there facing on;
     *   - a may-turn square answers true from either copy, because its plain sibling is one.
     *
     * It is a question about the GRAPH, not about a train: whether this particular locomotive would be turned there is
     * `turnsOnArrival`.  A square is emitted as one Point per way of arriving, and `Point.getBlock` says two of them
     * are one piece of track; a copy that neither turns trains nor is a reversing point is a way to be there and
     * leave the way you came.  False for a Point with no block (a hand-written configuration).
     *
     * @param square any copy of the square
     * @return true when some copy of it does not turn the train
     */
    public boolean hasAWayThrough(Point square)
    {
        if (square == null) return false;

        // THE COPY BEING ASKED ABOUT COUNTS AS ONE OF THEM (SEV-C1): a plain copy is itself the way through.  Harmless
        // at the two callers (both ask about a copy already established to be a turning copy), a trap for a third.
        for (Point copy : this.points.values())
        {
            if (!square.isSamePlaceAs(copy)) continue;

            if (!copy.isTerminus() && !copy.isReversing()) return true;
        }

        return false;
    }

    /**
     * Whether a train standing at this terminus could be sent on again by autonomy: to some station autonomy would send it to and
     * that it fits, on the railway as it is set up, whoever else is on it (E2E-B1).
     *
     * A may-turn square's turning copy is a terminus whose one way out is back the way the train came.  A reversible
     * train is sent to one to turn there, and if nothing reachable from it fits the train's length the train never
     * moves again (BottomMainB admits a train of 4 and every station its turning copy leads to is shorter, so Return
     * Home found no plan for the whole fleet).  A train that cannot reverse is kept out of a terminus the same way;
     * this is the same rule for one that can, at its length.
     *
     * THE STANDING CLAUSES ONLY, so a terminus is never refused for something that clears by itself: occupancy, the
     * sensors, the cap and the routes held are not asked.  What is asked is what autonomy would refuse every time from
     * there - a station it does not choose or that does not take this train, a route that turns on the way, one with a
     * square switched off, one the train does not fit, and one whose switch commands conflict.  One hop: a terminus that
     * leads only to another terminus is not followed further.
     *
     * Allows on doubt: a search that fails says nothing about the railway, and the terminus is then not refused.
     *
     * @param from the terminus
     * @param loc the train
     * @return true when some station autonomy would send this train to is reachable from there
     */
    private boolean leavesForAStationItFits(Point from, Locomotive loc)
    {
        for (Point d : this.points.values())
        {
            if (d.equals(from) || !isSendableDestination(d) || d.excludes(loc)) continue;

            if (d.isTerminus() && !loc.isReversible()) continue;

            // Another copy of the square it is standing on is not somewhere else
            if (from.getBlock() != null && from.getBlock().equals(d.getBlock())) continue;

            try
            {
                List<List<Edge>> seenPaths = new LinkedList<>();
                List<Edge> path;

                while ((path = this.bfs(from, d, seenPaths)) != null)
                {
                    seenPaths.add(path);

                    if (this.reversesAlongTheWay(path) || whyTooLongForThisRoute(path, loc) != null) continue;

                    boolean switchedOff = false;

                    for (Edge e : path)
                    {
                        if (!e.getStart().isActive() || !e.getEnd().isActive()) switchedOff = true;
                    }

                    if (switchedOff) continue;

                    // The same preview isPathClear makes: no command is sent
                    EdgeConfigurationState validity = new EdgeConfigurationState();

                    for (Edge e : path) this.configureEdge(e, validity);

                    if (validity.configIsValid) return true;
                }
            }
            catch (Exception e)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Why autonomy will never choose this point for this locomotive, or null when it is a candidate.
     *
     * The STANDING bars only: the ones that are a property of how the railway is set up rather than of what is happening on it
     * this minute (a station occupied by another train is a candidate that is busy).
     *
     * Pulled out of `explainDestinations` so that `destinationsBarredFromAutonomy` asks the same question the explanation
     * answers: two copies of this list would be two answers to "can autonomy pick this station", and the popup that groups by it
     * would disagree with the reason printed beside each line.
     *
     * @param end the candidate
     * @param loc the locomotive asking - exclusions are per locomotive
     * @return the reason, already translated, or null if nothing standing bars it
     */
    private String barredFromAutonomy(Point end, Locomotive loc)
    {
        if (!end.isActive()) return I18n.t("autolayout.why.inactive");

        if (end.isReversing()) return I18n.t("autolayout.why.reversing");

        if (!end.isAutoDestination()) return I18n.t("autolayout.why.notAutoDestination");

        // A TERMINUS THIS LOCOMOTIVE COULD NOT LEAVE: standing, not transient (a fact about how the railway is built and
        // which locomotive is asking), and in pickPath's filter too, since this method is the one list of standing
        // reasons autonomy never picks a station.
        if (loc != null && end.isTerminus() && !loc.isReversible())
        {
            return I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", loc.getName());
        }

        // AND ONE A TRAIN THAT CAN REVERSE COULD NOT LEAVE AT ITS LENGTH (E2E-B1): pickPath's clause, through the same
        // method, so the window and the choice give one answer.
        if (loc != null && end.isTerminus() && !this.leavesForAStationItFits(end, loc))
        {
            return I18n.f("autolayout.why.terminusItCouldNotLeave", loc.getName());
        }

        if (loc != null && end.excludes(loc)) return I18n.f("autolayout.why.excluded", loc.getName());

        return null;
    }

    /**
     * Of the points `explainDestinations` reports on, the ones autonomy will never choose.
     *
     * FR-017: the window groups the ones that can be chosen autonomously above the ones that cannot, and the grouping
     * is not recoverable from the reason strings (two of them are translated text that would have to be matched by
     * value, and one of the standing bars can produce the same sentence as a transient one).
     *
     * By NAME, matching the keys of the map it partitions, so the caller does no lookup of its own.
     *
     * @param loc the locomotive asking
     * @return the barred point names, empty when none are
     */
    synchronized public java.util.Set<String> destinationsBarredFromAutonomy(Locomotive loc)
    {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();

        if (loc == null) return out;

        Point start = this.getLocomotiveLocation(loc);

        if (start == null) return out;

        for (Point end : this.points.values())
        {
            // The same three skips explainDestinations makes, so the two agree on what is even being
            // reported on before they disagree about why.
            if (!end.isDestination() || end.equals(start)) continue;

            if (start.getBlock() != null && start.getBlock().equals(end.getBlock())) continue;

            if (barredFromAutonomy(end, loc) != null) out.add(end.getName());
        }

        return out;
    }

    /**
     * Every station this locomotive might be sent to, and why each one is or is not available.
     *
     * The answer to "I pressed start and nothing happened".  All of this was already computed on every attempt and then thrown
     * away: pickPath rejects a destination with ONE conjunction of seven terms and records nothing, and isPathClear names its
     * reasons properly but is called with logging off, so its message went into lastError and was overwritten by the next
     * candidate.
     *
     * So this is the same set of questions asked in the same order, keeping the answer instead of discarding it.  It deliberately
     * does NOT re-implement the rules - the second stage calls isPathClear itself, so a route this says is clear is a route the
     * running layout agrees is clear.
     *
     * Non-stations are left out entirely rather than listed as "not a station", because on a real
     * layout they are most of the graph and a list of two hundred of them answers nothing.
     *
     * @param loc the locomotive
     * @return station name to reason, in priority order, with a null value where the train could go
     */
    synchronized public java.util.Map<String, String> explainDestinations(Locomotive loc)
    {
        return explainDestinations(loc, false);
    }

    /**
     * The same reasons, for a train a person sends rather than one autonomy picks (MT-434).
     *
     * Autonomy's standing bars - a station set not to be chosen, one switched off, a train excluded, a reversing
     * square, a terminus for a train that cannot reverse - say what autonomy will pick, and are no answer to an
     * operator asking why a hand-driven send will not go.  `getPossiblePaths`, which is what a person is offered, asks
     * none of them itself, so a hand-driven answer skips them and decides every station the way that list does:
     * somebody standing there, or the first clear route, or why there is none.
     *
     * @param loc the train
     * @param byHand true for the reasons a hand-driven send meets; false for autonomy's
     * @return each station's reason, null where it may go
     */
    synchronized public java.util.Map<String, String> explainDestinations(Locomotive loc, boolean byHand)
    {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();

        if (loc == null) return out;

        Point start = this.getLocomotiveLocation(loc);

        if (start == null) return out;

        // The same order pickPath walks, so the first available entry here is the one it would have
        // taken - which makes this readable as "what it decided" and not merely "what is true"
        List<Point> ends = new LinkedList<>(this.points.values());

        Collections.sort(ends, (Point p1, Point p2) ->
            p1.getPriority() == p2.getPriority() ? 0 : (p2.getPriority() < p1.getPriority() ? -1 : 1));

        for (Point end : ends)
        {
            // Not a station: not a candidate, and not worth a line.  And not where this train is ALREADY standing, which on a derived
            // graph is every copy of that square, not just the Point it is on (comparing Points alone listed the train's own platform
            // as "occupied by <itself>").
            if (!end.isDestination() || end.equals(start)) continue;

            if (start.getBlock() != null && start.getBlock().equals(end.getBlock())) continue;

            // The standing bar FIRST, and the transient one after it (FR-017): a station autonomy will never choose must
            // not report "occupied by X" whenever a train happens to sit on it, and the reason must agree with the
            // grouping the popup sorts by.  Not on a hand-driven send (MT-434): autonomy's bars are not the operator's.
            //
            // BUT THE MENU'S OWN (AMR-B2).  The right-click menu leaves out a station that excludes the train and a
            // terminus a train that cannot reverse would be stranded at, and "may go" said of either would be the
            // button and its explanation disagreeing (OB-057).
            String reason = byHand ? whyNotOfferedToOperator(end, loc) : barredFromAutonomy(end, loc);

            if (reason == null && end.getBlockLocomotive() != null)
            {
                // The BLOCK, not the point: a train on another arrival-side copy of this square is
                // standing on this platform, and naming the copy would be telling the user about the
                // model rather than about their railway
                reason = I18n.f("autolayout.why.occupied", end.getBlockLocomotive().getName());
            }
            else if (reason == null)
            {
                // Past the filter, so the question becomes whether any route is clear.  Every
                // alternative is tried, exactly as pickPath does, because the first one being blocked
                // says nothing about the second.
                reason = firstClearOrWhyNot(loc, start, end, byHand);
            }
            else
            {
                // A PHYSICAL REFUSAL OUTRANKS A PREFERENCE (MT-262).  The standing bar above ("Set not to be chosen
                // automatically.") is a statement about AUTONOMY, and an operator asking why a hand-driven send of a
                // four-unit train into two units of track will not work must be told the length, not that.  A parking
                // berth is spelled `autoDestination: false`, so the squares a person actually sends trains to by hand
                // are exactly the ones this branch covers.  Only the LENGTH refusal is asked here, not the whole of
                // `isPathClear`: the rest is about what is happening on the railway this minute and clears itself,
                // while being too long for the berth does not, and it is as true of a hand-driven send as of an
                // automatic one.  The grouping is untouched: this station is still reported among the ones autonomy
                // will never choose.
                //
                // NOT ON A HAND-DRIVEN SEND, WHERE THE REASON IS THE MENU'S OWN (AMV-C5).  A station set not to be
                // chosen is a preference, and being too long for the berth is a fact, so the fact wins on autonomy's
                // tier.  The bars this branch carries on Manual are the ones that keep a station OUT of the right-click
                // menu (it excludes this train, or it is a terminus the train could not leave), and a station the
                // operator is not offered at all is not one whose length is the answer: the operator sees the menu's
                // reason.
                String cannotFit = byHand ? null : whyNoRouteFitsTo(loc, start, end);

                if (cannotFit != null) reason = cannotFit;
            }
            out.put(end.getName(), reason);
        }

        return out;
    }

    /**
     * The square holding this destination back right now, or null when none is (FR-001).
     *
     * ONE expression of the rule, asked by the runtime check in `isPathClear` and by the explanation in
     * `firstClearOrWhyNot` (DR-B3).  The train LEAVING the watched square is exempt, as it is at runtime, and the whole
     * BLOCK is asked rather than the named copy, because a square emitted as several copies is one piece of track.
     * The rule lives in `Point.heldBackBy` (DR-B2); this is the runtime's NAME for it, the live-block variant, because
     * WHICH occupancy to read is the runtime's business: the live railway here, the occupancy the plan has reached in
     * `HomeStaging.plannedOccupancy`.
     *
     * **There is no fence on it**: it fires for every tier, and the window names the square in all of them.
     *
     * @param destination the square being arrived at - a route's destination or any square on the way (OB-295)
     * @param loc the locomotive arriving, exempt where it is itself the occupant
     * @return the watched square with somebody else standing on it, or null
     */
    private Point blockingOccupantOf(Point destination, Locomotive loc)
    {
        return Point.heldBackBy(destination, loc);
    }

    /**
     * The first square this route arrives at that is held back by an occupied one, with the square holding it back - or
     * null when none is (FR-001, OB-295).
     *
     * Every square, not only the destination (a square trains only pass is never a destination; see the FR-001 note
     * in `isPathClear`).  The one question `isPathClear` refuses on and the why-window explains with.
     *
     * @param path the route
     * @param loc the train, exempt where it is itself the occupant
     * @return {the square held back, the square holding it}, or null
     */
    private Point[] heldBackAlong(List<Edge> path, Locomotive loc)
    {
        for (Edge edge : path)
        {
            Point watched = edge == null ? null : blockingOccupantOf(edge.getEnd(), loc);

            if (watched != null) return new Point[] {edge.getEnd(), watched};
        }

        return null;
    }

    /**
     * Whether this rail is held by the asking train's own path rather than somebody else's (OB-269).
     *
     * **A train never blocks itself** - `behaviour.md` 5c, and `Edge.isOccupied` honours the same rule for its own question.  The
     * occupancy counters do not record an owner, so the answer comes from the path maps instead.
     *
     * **Both maps, for VD12-B3's reason:** `configureAndLockPath` claims `takingPath` and reserves the road before it returns, and
     * `activeLocomotives` is written afterwards by the caller, so in that window a train holds rail and appears in only one of
     * them.  `walkStandingTrains` reads them as a union for exactly this.
     *
     * @param rail the piece of rail being asked about
     * @param loc the train asking, or null when nobody is
     * @return true when the claim on that rail is this train's own
     */
    private boolean railHeldByThisTrain(Edge rail, Locomotive loc)
    {
        if (rail == null || loc == null) return false;

        // Either map, the claims first (RSA4-C3): one train holds one path, in the one map or the other, and in both only
        // while it hands over - see `pathHeldBy`
        List<Edge> mine = this.pathHeldBy(loc);

        return mine != null && mine.contains(rail);
    }

    /**
     * Whether a train holds a path - under way on it, or still claiming it (RSA17-B2): every Point of a route being set up
     * answers it as its occupant until it is under way.
     *
     * @param loc the train
     * @return true while it holds one
     */
    public boolean holdsAPath(Locomotive loc)
    {
        List<Edge> path = loc == null ? null : this.pathHeldBy(loc);

        return path != null && !path.isEmpty();
    }

    /**
     * The path a train is under way on or still claiming (RC-A10, VD12-B3), the claims read first (RSA4-C3).
     *
     * The hand-over from claim to journey writes the journey and then takes the claim away, outside the railway's
     * monitor, so a train missing from the claims when they are read is among the journeys read after them.  Read the
     * other way round, a train handing over between the two reads was in neither: the tail rule anchored its tail at
     * whichever Point it had reserved came first, and a second train was routed over the track behind its start.  The
     * readers of the two maps as a union read them in the same order (RSA3-C1).
     *
     * @param loc the train
     * @return its path, or null where it holds none
     */
    List<Edge> pathHeldBy(Locomotive loc)
    {
        List<Edge> path = this.takingPath.get(loc);

        return path != null && !path.isEmpty() ? path : this.activeLocomotives.get(loc);
    }

    /**
     * Why no route from start to end can hold this train, or null when one of them can (MT-262, AMR-C2).
     *
     * **The refusal that does not clear itself.**  `explainDestinations` reports the standing bars first and stops, so a berth
     * autonomy will not choose (a parking track, where a person sends a train by hand) could never report a hand-driven refusal.
     * This is the one such refusal that is a fact about the railway rather than about this minute.
     *
     * Every alternative route is tried, as `firstClearOrWhyNot` does: one route being too short says nothing about another, and a
     * longer approach is more room.  ONE route that fits is enough to answer no.
     *
     * **It costs nothing on a railway with no lengths recorded**: a train with no length is answered before any search happens.
     * It is asked while the operator hovers the "no available paths" label, on the status window's own worker, and is the same
     * order of work `firstClearOrWhyNot` does for every station that is NOT barred (RTX-C5).
     *
     * @param loc the train
     * @param start where it is standing
     * @param end the destination being reported on
     * @return the refusal, already translated, or null when some route fits
     */
    private String whyNoRouteFitsTo(Locomotive loc, Point start, Point end)
    {
        return layoutExplanations.whyNoRouteFitsTo(loc, start, end);
    }
    /**
     * Null when some route from start to end is clear, and otherwise why the last one was not.
     *
     * The reason comes from isPathClear itself, through lastError, which it sets whether or not it is
     * logging.  Enumerating the alternatives matters: a layout with a train parked across one way
     * round is the ordinary case, and reporting that first blocked route as though it were the only
     * one would tell the user their railway is broken when it is merely busy.
     */
    private String firstClearOrWhyNot(Locomotive loc, Point start, Point end, boolean byHand)
    {
        List<Edge> path;
        List<List<Edge>> seenPaths = new LinkedList<>();

        String why = null;

        try
        {
            do
            {
                path = this.bfs(start, end, seenPaths);

                if (path == null) break;

                seenPaths.add(path);

                // AUTONOMY'S RULE, NOT A HAND-DRIVEN SEND'S (MFR-B1).  `reversesAlongTheWay` is not asked by `isPathClear`, so the
                // right-click menu offers a station beyond a headshunt; by hand this answer has to as well.
                if (!byHand && this.reversesAlongTheWay(path))
                {
                    why = I18n.t("autolayout.why.throughReversing");
                    continue;
                }

                // Read INSIDE this method's synchronized block, immediately after the call that set it - but lastError is static and
                // every running locomotive's thread writes it from pickPath, which is not synchronized on this Layout, so with trains
                // moving the message read here can be one generated for a different train's candidate route.  The DECISION is never
                // wrong (it comes from isPathClear's return value); only the wording can be misattributed, and it is labelled as such
                // rather than silently trusted.
                if (this.isPathClear(path, loc, false, byHand)) return null;

                // FR-001 is asked directly rather than read out of the static (DR-B3): the substitution below fires
                // whenever autonomy is running, so a station held back permanently would be reported as "blocked by a
                // train or a route in progress", telling the operator to wait for something waiting will not clear.
                // Asking blockingOccupantOf is not a second copy of the rule: it IS the rule the runtime check above
                // calls.  NO FENCE, because isPathClear has none (the restriction is enforced in every tier); a fence
                // here would name a watched square as the reason for a refusal that tier never made.  Every square the
                // route arrives at, as `isPathClear` asks it (OB-295).
                Point[] held = heldBackAlong(path, loc);

                if (held != null)
                {
                    why = I18n.f("autolayout.errorDestinationBlockedByPoint",
                        placeNameOf(held[0]), placeNameOf(held[1]));

                    continue;
                }

                why = this.isAutoRunning()
                    ? I18n.t("autolayout.why.blockedWhileRunning") : Layout.getLastError();

            } while (path != null);
        }
        catch (Exception e)
        {
            return String.valueOf(e.getMessage());
        }

        // No route at all is a different answer from every route being blocked: the difference between "build some track" and
        // "wait a minute".  NOT "NO TRACK" WHERE THE TRACK CONNECTS THROUGH A TERMINUS (PTR-B1): the search above never goes
        // through a terminus (OB-229), so a station whose every route passes one finds no route at all, and "No track route
        // leads there" would send the operator looking for rails that are there.  Asked of the track once more, through termini:
        // if a route exists that way, the reason is the terminus in the way, in the route check's own sentence.  AND NOT "A
        // TERMINUS" WHERE THE ONLY WAY IS A LAP (AMR-B1): that question is put to the TRACK, so it may walk both the routes a train
        // may not take (through a terminus, and round through another copy of the square this journey starts or ends at), and
        // which of the two it found decides the sentence, read off the route it returned.
        if (why == null)
        {
            try
            {
                List<Edge> anyTrack = this.bfs(start, end, null, true);

                if (anyTrack != null)
                {
                    boolean passesATerminus = false;
                    boolean passesACopy = false;

                    for (int at = 0; at + 1 < anyTrack.size(); at++)
                    {
                        Point midway = anyTrack.get(at).getEnd();

                        if (midway.isTerminus()) passesATerminus = true;

                        if (midway.isSamePlaceAs(start) || midway.isSamePlaceAs(end)) passesACopy = true;
                    }

                    // The terminus first, where a route meets both: it is the older rule and the one with a remedy on
                    // the diagram.
                    if (passesATerminus) return I18n.t("autolayout.errorIntermediateTerminusStation");

                    if (passesACopy) return I18n.t("autolayout.why.onlyALapLeadsThere");
                }
            }
            catch (Exception unsearchable)
            {
                return String.valueOf(unsearchable.getMessage());
            }
        }

        return why == null ? I18n.t("autolayout.why.noRoute") : why;
    }

    /**
     * Debugs a connection between two points.  Output value will be null for valid paths
     * @param loc
     * @param start
     * @param end
     * @return
     * @throws Exception 
     */
    public Map<List<Edge>, String> debugPath(Locomotive loc, Point start, Point end) throws Exception
    {
        Map<List<Edge>, String> output = new HashMap<>();
        
        List<Edge> path;
        List<List<Edge>> seenPaths = new LinkedList<>();

        // Get all possible paths
        do 
        {
            path = this.bfs(start, end, seenPaths);

            if (path != null)
            {
                seenPaths.add(path);
            }

        } while (path != null);
        
        for (List<Edge> p : seenPaths)
        {
            try
            {
                boolean result = this.isPathClear(p, loc, false);

                if (!result)
                {
                    output.put(p, lastError != null ? lastError : "");
                }
                else
                {
                    output.put(p, null);
                }
            }
            catch (Exception e)
            {
                output.put(p, e.getMessage());
            }
        }
        
        return output;
    }
    
    /**
     * Returns all possible paths for a given locomotive
     * @param loc 
     * @param uniqueDest - do we want to return multiple possible paths for the same start and end?
     * @return  
     */
    synchronized public List<List<Edge>> getPossiblePaths(Locomotive loc, boolean uniqueDest)
    {
        List<List<Edge>> output = new LinkedList<>();

        if (loc == null) return output;

        // If the locomotive is currently running, it has no possible paths
        if (!this.activeLocomotives.containsKey(loc))
        {     
            List<Point> ends = new LinkedList<>(this.points.values());
            //Collections.shuffle(ends);

            for (Point start : this.points.values())
            {
                if (loc.equals(start.getCurrentLocomotive()))
                {
                    for (Point end : ends)
                    {
                        // The same block rule as above - a copy of an occupied square is occupied.
                        if (!end.equals(start) && end.getBlockLocomotive() == null && end.isDestination())
                        {
                            try 
                            {
                                List<Edge> path;
                                List<List<Edge>> seenPaths = new LinkedList<>();

                                // If the first shortest path is invalid, check all alternatives                            
                                do 
                                {
                                    path = this.bfs(start, end, seenPaths);

                                    if (path != null && this.isPathClear(path, loc, false))
                                    {
                                        boolean unique = true;

                                        // Only return unique starts and ends
                                        if (uniqueDest)
                                        {
                                            for (List<Edge> e : output)
                                            {
                                                if (e.get(0).getStart().equals(start) && e.get(e.size() - 1).getEnd().equals(end))
                                                {
                                                    unique = false;
                                                    break;
                                                }
                                            }
                                        }

                                        if (unique)
                                        {
                                            output.add(path);
                                        }
                                    }

                                    if (path != null)
                                    {
                                        // Get another path other than the one we just saw
                                        seenPaths.add(path);
                                    }

                                } while (path != null);
                            }
                            catch (Exception e)
                            {
                                // Not silent, as in pickPath: a failure here would be indistinguishable from having
                                // nowhere to go.
                                this.control.logf("autolayout.errorPathSelectionFailed", loc.getName());
                                this.control.log(e);
                            }
                        }
                    }

                    break;
                }
            }
        }
        
        return output;
    }
    
    /**
     * Marks all paths in the timetable as untraversed
     */
    public void resetTimetable()
    {
        for (int i = 0; i < this.timetable.size(); i++)
        {
            this.timetable.get(i).setExecutionTime(0);
        }
    }
    
    /**
     * Returns the index of the first unfinished path in the timetable, or -1 if every entry has finished.
     *
     * -1 rather than 0, which would mean both "entry 0 is unfinished" and "nothing is unfinished": entries are dispatched on
     * their own threads and can finish out of order (a graceful stop can leave entry 0 still retrying while entry 1 has
     * completed), and the caller wipes every completion timestamp when nothing is unfinished.
     * @return the index of the first unfinished entry, or -1 if there is none
     */
    public int getUnfinishedTimetablePathIndex()
    {
        for (int i = 0; i < this.timetable.size(); i++)
        {
            if (this.timetable.get(i).getExecutionTime() == 0)
            {
                return i;
            }
        }

        return -1;
    }
    
    /**
     * The timetable as it stands right now, for readers that only look at it.
     *
     * A copy taken under the monitor.  getTimetable hands back the field itself, which is a copy-on-write list since
     * GST-C3, so a reader iterating it no longer throws when a train's capture appends; this copy is kept for the
     * readers that want the timetable as one consistent list, taken with the monitor's other writers held off.
     *
     * @return
     */
    synchronized public List<TimetablePath> getTimetableSnapshot()
    {
        return Collections.unmodifiableList(new ArrayList<>(this.timetable));
    }

    /**
     * Fetches the starting station for a given locomotive
     * @param l
     * @return 
     */
    synchronized public Point getTimetableStartingPoint(Locomotive l)
    {
        if (l != null)
        {
            for (TimetablePath ttp : this.timetable)
            {
                // Its first entry the railway can run - one through a page left out of autonomy starts nowhere on it
                if (l.equals(ttp.getLoc()) && ttp.isRunnable())
                {
                    return ttp.getStart();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks whether the timetable has any unfinished paths
     * @return 
     */
    private boolean timetableHasUnfinishedPaths()
    {
        // >= 0, matching the -1 sentinel: any non-negative index names a real unfinished entry
        return getUnfinishedTimetablePathIndex() >= 0;
    }
  
    /**
     * Pauses between polls of a condition the dispatch loop is waiting on.
     *
     * Honours the operator's action delays, but never spins: both settings are allowed to be zero, and zero means
     * Thread.sleep(0).  The sequential branch waits for the train ahead to ARRIVE, which is minutes per entry, so with
     * no floor a staging run would pin a core for most of its duration, silently (the wait message never varies, so
     * the log's consecutive-duplicate suppression hides every repeat).
     *
     * AND ANY STOP CUTS IT SHORT (RSA4-C1), as it does an entry's own pause (RSA3-C5): a sleep nothing ended would let the loop
     * live on inside the timetable for the rest of the operator's delay, long enough for Start to come back and begin a run the
     * loop then sent its next entry into.
     */
    private void pacedWait()
    {
        try
        {
            this.pauseUnlessStopped(this.pacedWaitMillis());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Executes the paths in the timetable.
     *
     * Blocks until every train has arrived, not merely until the last one has set off.
     *
     * @return false if a move was abandoned because its path would not clear.  Reachable in BOTH
     *         modes: sequential gives up after a few attempts, parallel after TIMETABLE_STUCK_MS of
     *         being refused.  A graceful stop is not an abandonment and returns true.
     */
    public boolean executeTimetable()
    {
        return executeTimetable(this.stopsOrdered.get());
    }

    /**
     * The same, with the stops counted where the run was chosen (RSA2-C1): Return Home counts them at its press and
     * plans before it runs, so the reload's Yes while it plans - nothing yet moving - is answered by starting nothing,
     * whether the load that asked it then succeeds or is refused.
     *
     * @param stopsAtChoice `stopsOrdered()` as read where the run was chosen
     * @return as `executeTimetable()`; true, having started nothing, after a stop ordered since
     */
    public boolean executeTimetable(int stopsAtChoice)
    {
        // The flag is set here rather than inside, so that nothing thrown from the run can leave it
        // set - which would silently disable timetable capture for the rest of the session.
        this.timetableExecuting = true;

        try
        {
            return executeTimetableInternal(stopsAtChoice);
        }
        finally
        {
            this.timetableExecuting = false;
        }
    }

    private boolean executeTimetableInternal(int stopsAtChoice)
    {
        // Returning after setting running would leave it set with nothing to clear it (no entry means no thread), so isRunning()
        // would stay true for the session; the wait at the end of this method would also never return.
        if (this.timetable.isEmpty())
        {
            this.control.logf("autolayout.infoTimetableExecutionFinished");
            return true;
        }

        this.timetableStoppedBecause = null;

        // NOT AFTER A STOP ORDERED SINCE THE RUN WAS CHOSEN (RSA2-C1): `running` set, THEN the count asked, and cleared again where
        // it has moved, the two held as one against the Yes's two (which clear `running` and then count) by `stopLock` (RSA4-C2).
        // Unheld, a Yes could clear before the set and count after the read, and the run started after it.
        synchronized (this.stopLock)
        {
            this.running = true;

            if (this.stopsOrdered.get() != stopsAtChoice)
            {
                this.stopLocomotives();

                return true;
            }
        }

        // No signal sweep: see runLocomotives.
        
        // Written from an entry's own thread, read here once they have all finished
        final AtomicBoolean abandoned = new AtomicBoolean(false);

        // Capture start time
        long startTime = System.currentTimeMillis();

        // Reset all timestamps in the timetable
        if (!this.timetableHasUnfinishedPaths())
        {
            // this.control.log("Starting fresh timetable execution.");
            
            for (TimetablePath ttp : this.timetable)
            {
                ttp.setExecutionTime(0);
            }
        }
        
        // Calculate start index in case of prior graceful stop request.  Clamped because the index is -1 when nothing is unfinished
        // (an empty timetable, and momentarily after the reset above).
        int startIndex = Math.max(0, getUnfinishedTimetablePathIndex());
            
        this.control.logf(
            "autolayout.infoExecutionStartedFromIndex",
            startIndex + 1
        );        
        
        // THE LOOP COUNTED WHILE IT DISPATCHES (RSA4-C1), as each entry's thread is (RSA3-C5), see `timetableEntries`:
        // it pauses between entries with no train moving, and uncounted, a stop made in that pause would read idle and
        // the loop would wake into the next run.
        this.timetableEntries.incrementAndGet();

      try
      {
        for (int i = startIndex; i < this.timetable.size(); i++)
        {
            TimetablePath ttp = this.timetable.get(i);
            
            final int index = i;

            // Continuously execute unless user requests graceful stop - or unless this Layout has been retired.
            // Without the fence a reload during a run would leave this loop waiting on a retired graph forever: the
            // sequential branch below waits for the entry ahead to leave activeLocomotives, the fence-abort inside
            // executePath returns before removing it, and nothing reachable from the UI can call stopLocomotives() on a
            // Layout that getAutoLayout() no longer resolves to.  The completion wait below asks the same question
            // where it spins.
            while (this.running && this.isCurrentLayout())
            {
                // NO ENTRY STARTED WHILE THE TRACK POWER IS OFF (GST-B1) - Execute Timetable and Return Home, whose plan runs
                // as a timetable, alike: the run waits, and asks again once the power is back.
                if (this.waitWhileThePowerIsOff()) continue;

                if (i > startIndex && (System.currentTimeMillis() - startTime) < ttp.getSecondsToNext())
                {
                    this.control.logf(
                        "autolayout.infoWaitingForNextTimetableEntry",
                        (ttp.getSecondsToNext() - (System.currentTimeMillis() - startTime)) / 1000
                    );
                }
                else if (i > startIndex && this.timetable.get(i - 1).getExecutionTime() == 0)
                {
                    this.control.logf(
                        "autolayout.infoWaitingForPreviousRouteToStart"
                    );
                }
                else if (i > startIndex && this.timetableSequential
                    && this.activeLocomotives.containsKey(this.timetable.get(i - 1).getLoc()))
                {
                    // Sequential mode waits for the train ahead to ARRIVE, not merely to set off
                    this.control.logf(
                        "autolayout.infoWaitingForPreviousRouteToFinish"
                    );
                }
                else if (!ttp.isRunnable())
                {
                    // A PATH THROUGH A POINT THE RAILWAY DOES NOT HAVE - one on a page left out of autonomy - STOPS THE RUN, and says
                    // why (Adam: "stop the timetable run upon encountering an invalid path, and let the user know").  Left unrun, so
                    // the window names it; trains already under way finish their journeys.  Refused and passed over, a train's later
                    // entries would start where it was not (RSA9-B1).
                    this.control.logf("autolayout.errorTimetableEntryNotRun", ttp.toString(), ttp.whyNotRunnable());

                    this.timetableStoppedBecause = ttp.whyNotRunnable();

                    abandoned.set(true);

                    synchronized (this.activeLocomotives)
                    {
                        this.stopLocomotives();
                    }

                    break;
                }
                else
                {
                    this.control.logf(
                        "autolayout.infoStartingTimetableRoute",
                        ttp.toString()
                    );
                    startTime = System.currentTimeMillis();

                    // COUNTED BEFORE IT STARTS, until it leaves (RSA3-C5) - see `timetableEntries`
                    this.timetableEntries.incrementAndGet();

                    new Thread(() ->
                    {
                      try
                      {
                        try
                        {
                            int attempts = 0;

                            // When this entry first refused, so a parallel run can give up on time
                            long refusingSince = 0;

                            // The power's epoch as this attempt began - see below (RSA62-C3)
                            int powerAtAttempt = this.control.getPowerEpoch();

                            while (this.running && !this.executePath(ttp.getPath(), ttp.getLoc(),
                                ttp.getLoc().getPreferredSpeed(), ttp, playedBack(ttp), stopsAtChoice))
                            {
                                // A SPEED IS NOT A BUSY TRACK (SG-A5): executePath refuses a locomotive whose
                                // preferred speed is outside 1 to 100 and returns false, indistinguishable here from a
                                // path it could not lock, so this loop would wait, ask again, and after three attempts
                                // declare the entry stuck and end the run.  runLocomotives settles it the same way
                                // (RC-B5): skip that locomotive, say so in the log (the same message), and let
                                // everything else run.  Breaking out rather than setting `abandoned`: the run did go on
                                // to the end, and the line naming the locomotive is what the operator needs.
                                if (ttp.getLoc().getPreferredSpeed() < 1
                                    || ttp.getLoc().getPreferredSpeed() > 100)
                                {
                                    this.control.logf("autolayout.errorFailedToRunLocomotive",
                                        ttp.getLoc().getName());

                                    // STAMPED, or the run stops here anyway: the next entry waits on "the previous
                                    // route has not started yet", which is executionTime == 0, so an unstamped entry
                                    // would turn the abandonment into a wait that nothing ever ends.  The entry HAS had
                                    // its turn; what it has not had is a train.
                                    ttp.setExecutionTime(System.currentTimeMillis());

                                    break;
                                }

                                // AN ATTEMPT THE POWER CUT IS NO REFUSAL (RSA62-C3).  A route being set when the power goes off is given
                                // back unrun (RSA61-C1), and that false is not the track refusing: counted, three cuts in one leg's
                                // setting ended Return Home with "the path stayed blocked".  Neither an attempt, nor the start of a
                                // parallel run's clock.
                                // the epoch read once each time (RSA63-C1): a STOP after the power wait and before the
                                // attempt's read began it with the power off - a GO since moved the epoch, and no GO reads off
                                boolean cut = this.control.getPowerEpoch() != powerAtAttempt || !this.control.getPowerState();

                                if (!cut)
                                {
                                    attempts++;

                                    if (refusingSince == 0) refusingSince = System.currentTimeMillis();
                                }

                                // A PARALLEL timetable gives up on time, where a sequential one gives up on attempts:
                                // otherwise a captured timetable would retry a refusal that could never change until
                                // somebody stopped it by hand (T3).  Time rather than attempts here because the pause
                                // between attempts is pacedWait, which honours the user's delay settings (a quarter
                                // second to tens of them), so attempts would bound a different amount of standing
                                // still on every layout.
                                boolean stuck = !cut && (this.timetableSequential
                                    ? attempts >= STAGING_MAX_ATTEMPTS
                                    : System.currentTimeMillis() - refusingSince >= TIMETABLE_STUCK_MS);

                                if (stuck)
                                {
                                    // Retrying has stopped being worth it: with one train moving at a time nothing will free the path, and
                                    // in a parallel run nothing has freed it for long enough that nothing is going to.  Stop and say so
                                    // rather than spin.
                                    this.control.logf(
                                        this.timetableSequential
                                            ? "autolayout.errorReturnToHomeEntryStuck"
                                            : "autolayout.errorTimetableEntryStuck",
                                        ttp.toString()
                                    );

                                    // Recorded so the caller can say so, rather than the operator
                                    // having to notice a log line and work out that the run ended early
                                    abandoned.set(true);

                                    synchronized (this.activeLocomotives)
                                    {
                                        this.stopLocomotives();
                                    }

                                    break;
                                }

                                this.control.logf(
                                    "autolayout.infoTimetableEntryNotYetExecutable",
                                    ttp.toString()
                                );

                                // A PAUSE ANY STOP CUTS SHORT (RSA3-C5), so an entry the Yes finds refusing leaves
                                // with its run.  A sequential run is paced independently of the delay settings, which
                                // may be zero - that would busy-wait here rather than pause.
                                try
                                {
                                    this.pauseUnlessStopped(this.timetableSequential ? STAGING_RETRY_PAUSE
                                        : this.pacedWaitMillis());
                                }
                                catch (InterruptedException ie)
                                {
                                    Thread.currentThread().interrupt();
                                    break;
                                }

                                // NOR TRIED AGAIN ON A RAILWAY WITH ITS POWER OFF (GST-B1): the next attempt waits for the
                                // power, and the wait is not refusing - a parallel run's clock for giving up starts again.
                                if (this.waitWhileThePowerIsOff()) refusingSince = 0;

                                powerAtAttempt = this.control.getPowerEpoch();
                            }

                            this.control.logf("autolayout.infoTimetablePathFinished");
                        }
                        // Throwable, for the reason runLocomotive gives where it catches the same call: executePath's
                        // own handler is catch (RuntimeException), so an Error walks straight past it AND past the
                        // block below, which is the only place the last entry clears `running`; isRunning() would stay
                        // true for ever and Start and Graceful Stop would never come back.  Stopping the trains and
                        // saying so is the right answer to an Error too.
                        catch (Throwable e)
                        {
                            this.control.logf(
                                "autolayout.errorTimetableExecutionFailed",
                                e.toString()
                            );

                            // ABANDONED (OB-072): a leg that threw is the clearest abandonment there is, and the
                            // method ends in `return !abandoned.get()`, so without this the operator would be told the
                            // run had finished normally.  Set before the trains are stopped, so nothing between here
                            // and the return can be reached with the flag still false.
                            abandoned.set(true);

                            // Stop execution
                            synchronized (this.activeLocomotives)
                            {
                                this.stopLocomotives();
                            }

                            control.log(e instanceof Exception ? (Exception) e : new Exception(e));
                        }

                        // When we are done, exit in this thread to avoid disrupting the final path
                        if (index == this.timetable.size() - 1)
                        {
                            // Reset running status
                            synchronized (this.activeLocomotives)
                            {
                                this.stopLocomotives();
                            }

                            this.control.logf("autolayout.infoTimetableExecutionFinished");
                        }
                      }
                      finally
                      {
                        this.timetableEntries.decrementAndGet();
                      }
                    }).start();

                    break;
                }  

                this.pacedWait();
            }                
        }
      }
      finally
      {
        this.timetableEntries.decrementAndGet();
      }

        // The loop above only DISPATCHES the last entry - its own thread is still driving that train, and is what finally clears the
        // running state.  Returning here would hand control back while a locomotive was still moving (callers re-enable their
        // buttons on return).  Also covers a graceful stop and a staging entry that gave up: both clear running, and this still
        // waits for whatever is mid-path to finish and leave activeLocomotives.  isCurrentLayout is the second condition because a
        // fence-abandoned path leaves its activeLocomotives entry in place ON PURPOSE (it belongs to a Layout being discarded), so
        // isRunning stays true forever on a reloaded graph and a wait that polled only that never returned.
        while (this.isRunning() && this.isCurrentLayout())
        {
            try
            {
                Thread.sleep(COMPLETION_POLL);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // AND NOW THE RAILWAY IS IDLE, SO SAY SO (W7-A2).  Every other announcement a timetable makes is made while it
        // is still running (the arrival callback fires before `executePath`'s finally has decremented the thread
        // count; the last leg's own thread clears `running` only AFTER its `executePath` has announced), so nothing is
        // announced with `isRunning()` false, which is what the loop above waits for.
        //
        // `TrainControlUI.reconcileFacingWhenIdle` is the only thing that writes a reversal the railway made into the
        // setup, it is reached only from a diagram refresh, and it refuses while anything is moving.  A train that
        // backs into its home berth on a Return Home run (a timetable) turns on the shared arrival path and records it
        // in `reversedOnArrival`; with no idle announcement the turn would stay pending, the diagram would draw the
        // train facing the way it set off, and the exit capture would write the un-reconciled facing to disk.
        //
        // ONE PLACE RATHER THAN A FOURTH COPY (W7-C2): the two hand-driven doors (OB-189) reach `announceRunFinished()`
        // when their journey returns and the count falls to zero, so announcing here covers the timetable button and
        // Return Home too.  Not gated on `abandoned`: a run that gave up part way still moved trains.
        announceRunFinished();

        return !abandoned.get();
    }
    
    /**
     * Waits while a running train stands on the sensor its run is waiting for and has not yet recorded (RSA56-C1,
     * OB-250) - at most `atMostMs`.
     *
     * The run records a sensor `Locomotive.FEEDBACK_DURATION_THRESHOLD` after it reads occupied, and a route fired by
     * that same sensor asks "where is the train" in the gap, when the last sensor reached is still the one before.
     * Nothing is waited for on a sensor the train has not reached: `waitForS88Reached` would hold the route's monitor,
     * untimed, until the train got there.
     *
     * A sensor the run has already recorded is not waited for (RSA57-C1): the run keeps a sensor pending until its step ends,
     * and at a reversing point partway along that is after the turn's pause, seconds in which the train stands on a sensor it
     * has long since reached.
     *
     * @param l the train
     * @param atMostMs the longest wait
     */
    public void waitForTheRunToRecord(Locomotive l, long atMostMs)
    {
        if (l == null) return;

        long until = System.currentTimeMillis() + atMostMs;

        boolean interrupted = false;

        while (true)
        {
            String pending = this.locomotivePendingS88.get(l);

            // READ OUTSIDE THE MONITOR: the feedback store has a lock of its own.  ALREADY RECORDED: still pending
            // through a turn, but the answer is already there (RSA57-C1)
            if (pending == null || pending.equals(this.getLatestMilestoneS88(l))
                || !this.control.getFeedbackState(pending)) break;

            long left = until - System.currentTimeMillis();

            if (left <= 0) break;

            synchronized (pendingS88Monitor)
            {
                if (!pending.equals(this.locomotivePendingS88.get(l))) continue;

                try
                {
                    // WOKEN when the run moves on (`updatePendingS88`), and asked again within 50 ms for a sensor that
                    // clears without the run recording it
                    pendingS88Monitor.wait(Math.min(left, 50));
                }
                catch (InterruptedException e)
                {
                    interrupted = true;

                    break;
                }
            }
        }

        if (interrupted) Thread.currentThread().interrupt();
    }

    /**
     * If targetS88 is the nextS88 for the given locomotive, this method will wait until it isn't
     * @param l
     * @param targetS88 
     */
    public void waitForS88Reached(Locomotive l, String targetS88)
    {
        if (l == null) return;

        boolean interrupted = false;

        while (this.locomotivePendingS88.get(l) != null && this.locomotivePendingS88.get(l).equals(targetS88))
        {
            synchronized (pendingS88Monitor)
            {
                while (this.locomotivePendingS88.get(l) != null && this.locomotivePendingS88.get(l).equals(targetS88))
                {
                    try
                    {
                        pendingS88Monitor.wait();
                    }
                    catch (InterruptedException ex)
                    {
                        // Remembered rather than re-armed, as in Locomotive: re-arming makes the next wait() throw at once and
                        // turns a blocked thread into a spinning one.
                        interrupted = true;
                    }
                }
            }
        }

        // Put back so that whoever interrupted this thread still gets their answer
        if (interrupted) Thread.currentThread().interrupt();
    }
    
    /**
     * Updates the pending S88 state so we can track what s88 each locomotive is waiting for
     * @param loc
     * @param s88 
     */
    private void updatePendingS88(Locomotive loc, String s88)
    {
        // On pendingS88Monitor rather than on the layout - see the field
        synchronized (pendingS88Monitor)
        {
            if (s88 == null)
            {
                this.locomotivePendingS88.remove(loc);
            }
            else
            {
                this.locomotivePendingS88.put(loc, s88);
            }

            pendingS88Monitor.notifyAll();
        }
    }
    
    /**
     * Asked before a train is turned round at a point it MAY reverse at.
     *
     * **Intent cannot be inferred, so it is asked for.** A reversing point turns whatever passes it, and the reason
     * for the turn lives in the leg after this one: a train may be going there to back into a berth next time, or may
     * simply be passing through.  Adam: *"in manual mode, the system has no way of knowing that the intention is to
     * reverse into a berth on the next turn.  So we can't possibly both allow that and disallow it based on train."*
     *
     * So the caller decides.  Autonomy answers yes without asking anybody - it chose the path, and `pickPath` already
     * refuses any path that reverses along the way, so the only reversal it can reach is one it meant.  A hand-driven
     * send puts the question to the operator.
     *
     * A TRUE TERMINUS IS NOT ASKED ABOUT, at either door.  There the turn is not a choice: the train has run out of
     * track and the next move is back the way it came.  Adam: *"no unprompted reversals in manual mode unless going to
     * a true terminus (must reverse)".*
     */
    public interface ReversalPolicy
    {
        /**
         * @param loc the train
         * @param at the point it has just reached
         * @return whether to turn it round here
         */
        boolean shouldReverse(Locomotive loc, Point at);

        /**
         * Whether this point is one the operator has a say over at all.
         *
         * **It must be cheap, silent, and independent of the ANSWER.**  It runs at every point on every path, and
         * `shouldReverseAt` reads it to tell a compulsory turn from a choice: a reversing copy nobody is asked about
         * is one the railway turns every train at.  So it describes the RAILWAY - which squares anybody could have an
         * opinion about - and never what the opinion was
         * (`testTheJourneyPolicyAnswersAsksAboutIndependentlyOfTheAnswer` is the guard).
         *
         * **`shouldReverse` MAY BE CALLED WHILE THE TRAIN IS MOVING (CONF2-B1).**  The stop is the rule's own answer
         * (a train is stopped exactly when it is about to be turned), so the rule is evaluated first, at line speed.
         * Nothing reachable blocks in it, because both doors answer at departure and hand over a constant; a
         * `shouldReverse` that puts a dialog up would reintroduce `DIR-A1`, the train running past the point for as
         * long as the dialog stands open.
         *
         * **It exists because the runtime cannot answer it.**  `canReverse` - the operator's "trains may turn round
         * here" - never reaches `parseAuto`: `AutonomyBuilder` skips it ("it is the instruction to split, not
         * something parseAuto knows") and expresses it by splitting the square into a plain copy and a turning one.
         * Where the square CANNOT be split no flag exists anywhere, and no rule in this class can find one.
         *
         * **The default answers `at.isReversing()`, and a real door must NOT inherit it (CONF-C1).**  `reversing` is
         * true for every copy of a COMPULSORY turn as well as for a may-reverse one (the expression removed from
         * `ManualReversalPrompt` as `REG6-A1`), so an inheriting policy offers a question about a turn the railway
         * has no choice about, and "keep direction" drives the train forward off a copy whose only edges leave by the
         * side it came in at.  Anything built from the setup must override this: `mayTurnTiles()` is the reversible
         * squares minus the compulsory ones, the only place that distinction is recorded.
         *
         * @param at the point
         * @return whether the operator has a say about turning here
         */
        default boolean asksAbout(Point at)
        {
            return at != null && at.isReversing();
        }
    }

    /**
     * Whether a train that has just arrived here is turned round (MT-368).
     *
     * **`isTerminus` must not outrank a square the operator was promised a say over.**  `AutonomyBuilder` emits the
     * turning copy of a MAY-turn square with `terminus: true`, so reading `arrived.isTerminus() || shouldReverseAt(...)`
     * would collect the operator's answer and discard it (Adam: *"it is just always getting reversed."*).  The same
     * flag confusion `OB-205` claims 2 and 3 named (`ManualReversalPrompt.forJourney` and `shouldReverseAt`).
     *
     * **Three cases.**
     *
     *   - A square the door ASKS about is a may-turn square, and the answer is the operator's (Adam: *"may reverse should always
     *     prompt in manual mode"*).
     *   - A terminus nobody is asked about is compulsory: the turn is how the train gets in and how it leaves again, and
     *     `MT-245` turns on it.  Still unconditional.
     *   - Everything else is `shouldReverseAt`, unchanged.
     *
     * **Why `isTerminus` cannot simply be dropped.**  At a real terminus the arrival copy carries `terminus: true` and
     * `reversing: false`, so `shouldReverseAt` answers `current.isReversing()` - false - and a train would arrive at a terminus
     * and never turn.  The flag still has to be read; what it must not do is outrank a square the operator was promised a say over.
     *
     * **It is a METHOD so that what it does can be tested** (`core.testTheArrivalHonoursTheAnswer` asks this what it answers),
     * where a guard asserting the old expression verbatim could not fail on behaviour.
     *
     * @param arrived where the train has stopped
     * @param loc the train
     * @param reversals the policy, or null for autonomy
     * @return whether to turn it
     */
    public boolean turnsOnArrival(Point arrived, Locomotive loc, ReversalPolicy reversals)
    {
        if (arrived == null) return false;

        // NOWHERE TO STAND FACING ON, SO NO CHOICE (RSA49-A1).  A journey that ends on a turning copy no copy of whose square
        // faces on from the way it came (the end of a line marked "may", or a may-turn square one of whose ways in has no way on)
        // turns the train whatever the door's answer and whether it can reverse: kept, it stood on the copy facing back out with
        // its decoder driving it forward, and its next journey drove it into the end of the line over track nothing held
        // (REG6-A1's hazard, on a square marked "may").  A dead end already turns a train that cannot reverse (SVV-C2, MT-245).
        // Asked of the journey the train holds, which is the one ending here; a caller with none gets the rules below.
        if (loc != null && reversals != null && reversals != ALWAYS_REVERSE)
        {
            List<Edge> held = this.pathHeldBy(loc);

            if (held != null && !held.isEmpty() && held.get(held.size() - 1).getEnd() == arrived
                && noCopyFacesOnFrom(arrived, held.get(held.size() - 1).getStart()))
            {
                return true;
            }
        }

        // A TRAIN THAT CANNOT REVERSE IS NEVER TURNED WHERE TURNING IS OPTIONAL (MT-368).  This is the half that
        // decides: `ManualReversalPrompt.KEEP_DIRECTION.asksAbout` answers FALSE ("no opinion"), so the branch below is
        // skipped and `arrived.isTerminus()` decides, which is TRUE at the turning copy of a may-turn square.
        // `hasAWayThrough` tells an OPTIONAL turn from a compulsory one (a may-turn square keeps a plain copy), so a
        // terminus still turns a train that backed into it (MT-245, `testATrainThatCannotReverseMayBackIntoATerminus`).
        //
        // NOT WHERE A PLAN HAS ALREADY DECIDED (SEV-B2): `executePath(path, loc, speed, ttp)` hands in
        // `ALWAYS_REVERSE`, the form autonomy and the staging planner use, and a plan that routed a non-reversible
        // train to a turning copy has searched the next leg from that copy's edges; re-standing the train on the plain
        // copy would refuse that leg (`errorLocomotiveNotAtPathStart`).  `shouldReverseAt` fences the identical
        // question the identical way, and NULL COUNTS AS A PLAN AS WELL (SVV-C1; unreachable today).  Open, and raised
        // with Adam: whether autonomy should route a non-reversible train to a turning copy at all (MT-367 stops it
        // being OFFERED a compulsory turn).
        if (loc != null && !loc.isReversible() && reversals != null && reversals != ALWAYS_REVERSE
            && hasAWayThrough(arrived))
        {
            return false;
        }

        // A SQUARE THE DOOR ASKS ABOUT IS THE OPERATOR'S CALL, terminus flag or not: `asksAbout` comes from `mayTurnTiles()`, the
        // reversible squares MINUS the compulsory ones, the one place that distinction is recorded.
        if (reversals != null && reversals != ALWAYS_REVERSE && reversals.asksAbout(arrived))
        {
            return reversals.shouldReverse(loc, arrived);
        }

        return arrived.isTerminus() || shouldReverseAt(arrived, arrived, loc, reversals);
    }

    /**
     * Whether a train reaching this point is turned round here.
     *
     * **Three answers, and only one of them is a question.**  A point trains do not reverse at: no.  A journey whose DESTINATION
     * is a terminus: yes, and nobody is asked.  Anywhere else the caller's policy decides, which is how a hand-driven send comes
     * to ask the operator while autonomy does not.
     *
     * **The terminus is the destination, not this point**: the model refuses a terminus set as reversing (*"Terminus
     * stations cannot be set as reversing."*), so a rule asking whether THIS point is both could never fire.  A train
     * backs into a terminus by turning at the reversing point BEFORE it (`testATrainThatCannotReverseMayBackIntoATerminus`;
     * Adam, MT-245: *"trains should be allowed to back into terminuses if they are not reversible (that's why we have
     * the reversing point at feedback 2013)."*), so on a journey that ENDS at a terminus the turn on the way is not a
     * choice; it is how the train gets there.
     *
     * A null policy means yes, so every caller that has no opinion behaves as this method did before there was one.  Named
     * rather than left inline so it can be run; the call site is pinned separately.
     *
     * @param current the point the train has reached
     * @param destination where this journey ends
     * @param loc the train
     * @param reversals the caller's policy, or null for "always"
     * @return whether to turn it round
     */

    public boolean shouldReverseAt(Point current, Point destination, Locomotive loc,
        ReversalPolicy reversals)
    {
        if (current == null) return false;

        // NOBODY TO ASK MEANS AUTONOMY, and autonomy turns exactly where the flag says.  Widening this branch would turn trains at
        // plain copies of a may-reverse square, the promotion of "may" to "must" that `AutonomyBuilder` refuses to emit (it "made
        // a through station one no path could be routed through").
        //
        // ALWAYS_REVERSE COUNTS AS NOBODY: the four-argument overload hands it to autonomy's own loop AND to the timetable, which
        // is what Return Home runs; being non-null it would fall through to the manual branch, answer yes to everything, and turn
        // staged trains at every plain copy they passed (Adam: "no prompting in return home or auto.").
        if (reversals == null || reversals == ALWAYS_REVERSE) return current.isReversing();

        // ARRIVING at a terminus is not a question either way - the turn is how the train gets there, and MT-245 turns
        // on it: "a train that cannot reverse may back into a terminus".  The reversal ON THE WAY to a terminus is part
        // of getting there, not a preference about it, so `forJourney` does not ask about a journey that ends at a
        // terminus (SPEC-B1, REG6-B1): a dialog whose answer is discarded reports a decision that was never taken, and
        // honouring more would strand trains.
        //
        // THE TURNING COPY OF A MAY-TURN SQUARE IS ALSO A TERMINUS (OB-205 claims 2 and 3).  `AutonomyBuilder` emits
        // that copy with `terminus: true` (`json.put(stops ? "terminus" : "reversing", true)`), so `isTerminus()` is
        // true at a square the operator was promised a question about (Adam: *"when I set it as reversible, there is
        // still no 'should it change direction' prompt."*).
        //
        // **The door is asked, because the door is the only thing that knows**: `asksAbout` comes from
        // `mayTurnTiles()`, the reversible squares MINUS the compulsory ones.  A destination the door asks about is a
        // may-turn square, and a turn there is the operator's call; one it does not ask about is a real terminus,
        // where the turn is how the train gets in (MT-245).
        if (destination != null && destination.isTerminus() && !reversals.asksAbout(destination))
        {
            return current.isReversing();
        }

        // AND IN MANUAL MODE, THE DOOR DECIDES WHICH SQUARES ARE ASKED ABOUT (Adam: **"May reverse should always
        // prompt in manual mode."**).
        //
        // A COMPULSORY TURN TURNS, in every mode (CONF-A1).  A copy of a COMPULSORY turn leaves only by the side the
        // train arrived from, so not turning is not an available outcome (it drives the train forward off its reserved
        // path) and nothing is for anybody to have an opinion about: it must not reach a policy (REG6-A1 stopped the
        // operator being asked about it).
        //
        // `current.isReversing()` alone is NOT that test: a may-reverse square that the build DID split also has a
        // turning copy, and reaching it is a real question (the reason for such a turn lives in the leg after this
        // one, so nothing in the path can decide it).  Nor can the two be told apart from the graph (are all the copies
        // sharing this block reversing? is right about a split square and wrong about a square with one copy, which is
        // most of them).  So the DOOR is asked, as above: a reversing copy the door does not ask about is a
        // compulsory turn.
        if (current.isReversing() && !reversals.asksAbout(current)) return true;

        // `mayReverseAt` stays beside `asksAbout`: it is the runtime's own coverage of the plain copy of a split square, which a
        // policy that does not override `asksAbout` relies on (the default answers `isReversing()`, false at exactly that copy).
        // Only the DOOR's policy can answer from the setup.
        if (!mayReverseAt(current) && !reversals.asksAbout(current)) return false;

        return reversals.shouldReverse(loc, current);
    }

    /**
     * A road written by `namesOfRoad`, looked up on this layout (WK7-B1).
     *
     * **All or nothing.**  A pair naming an edge this layout does not have - a square re-split, a point renamed -
     * drops the whole road, because the part that is left would lead the tail walk down a road the train may never
     * have been on.  With no road the walk keeps the fork rule, which claims less.
     *
     * @param named a JSON array of [start, end] point-name pairs, or its string form, or null
     * @return the edges in the order they were written, or null
     */
    public List<Edge> roadNamed(Object named)
    {
        if (named == null) return null;

        JSONArray pairs;

        try
        {
            pairs = named instanceof JSONArray ? (JSONArray) named : new JSONArray(named.toString());
        }
        catch (JSONException notARoad)
        {
            return null;
        }

        List<Edge> road = new java.util.ArrayList<>();

        for (int i = 0; i < pairs.length(); i++)
        {
            JSONArray pair = pairs.optJSONArray(i);

            if (pair == null || pair.length() != 2) return null;

            Edge edge;

            try
            {
                edge = getEdge(pair.optString(0, null), pair.optString(1, null));
            }
            catch (RuntimeException noSuchEdge)
            {
                return null;
            }

            if (edge == null) return null;

            road.add(edge);
        }

        return road.isEmpty() ? null : road;
    }

    /**
     * A road as the file writes it: [start, end] point-name pairs, in the order driven (WK7-B1).
     *
     * @param road the edges, or null
     * @return the JSON array's string form, or null when there is nothing to write
     */
    public static String namesOfRoad(List<Edge> road)
    {
        if (road == null || road.isEmpty()) return null;

        JSONArray pairs = new JSONArray();

        for (Edge edge : road)
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) return null;

            pairs.put(new JSONArray().put(edge.getStart().getName()).put(edge.getEnd().getName()));
        }

        return pairs.toString();
    }

    /**
     * Which side of its END point an edge comes in by - the ONE definition, used by both readers.
     *
     * **The build says, and the geometry is the fallback.**  `AutonomyBuilder` splits a square on the side the metal
     * actually enters by and writes that into the configuration, so this reads back a decision already made.  Where it
     * is missing (an older setup, and every `Layout` a test builds by hand) the compass direction of the neighbouring
     * POINT is used, which is wrong only on a curve.
     *
     * **Why it has to be one method.**  The side is WRITTEN on arrival and READ by the walk that blocks the track behind a
     * standing train; when those disagreed the walk matched nothing on a curve and blocked nothing at all, a protection that
     * silently does nothing.
     *
     * @param edge the edge
     * @param at the point being arrived at, which must be one of its ends
     * @return the side name, or null when neither source can say
     */
    public String entrySideOf(Edge edge, Point at)
    {
        if (edge == null || at == null) return null;

        // The stored side describes the END of the edge, so it only answers for a train arriving.
        if (edge.getEnd() == at && edge.getEntrySide() != null) return edge.getEntrySide();

        Point other = edge.getStart() == at ? edge.getEnd() : edge.getStart();

        return other == null ? null : sideTowards(at, other);
    }

    /**
     * The edges a running train has already given back behind it - released, whole, as its tail cleared them, under
     * non-atomic routes (`releasedEarly`); none under atomic routes, which give nothing back until the run ends.  For the
     * diagram, which draws driven track only while the run holds it (FR-106).
     *
     * A copy, and it asks no monitor: the set is a concurrent one, and the diagram asks from a worker that must not wait
     * on a dispatch holding this railway.
     *
     * @param loc the train
     * @return the edges it has given back, never null
     */
    public Set<Edge> releasedBehind(Locomotive loc)
    {
        Set<Edge> given = loc == null ? null : this.releasedEarly.get(loc);

        return given == null ? java.util.Collections.<Edge>emptySet() : new java.util.HashSet<>(given);
    }

    /**
     * The track behind every standing train, which nothing else may run over.
     *
     * Adam: **"if a train protrudes far behind where it is standing, those edges it reaches need to be considered
     * blocked."**
     *
     * **EDGES, not points** (his ruling): *"because the points are technically unoccupied.  But the blocked edges should prevent
     * routing to the covered points."*  A train standing on one sensor does not make the next sensor occupied; it makes the track
     * between them impassable, and a station reachable only across that track becomes unreachable as a CONSEQUENCE rather than by
     * a second rule that could disagree with this one.
     *
     * **Derived, never stored**: recomputed from where the trains are, so it corrects itself the moment one moves.  The
     * reservation counter on `Edge` is deliberately NOT used: that counts route holds, which a route release would zero, and a
     * train standing still holds nothing.
     *
     * **It stops early on purpose, twice (Adam's rulings).**  At a FORK - *"if entering a switch from the no fork direction, keep
     * following it.  If entering a switch from the fork direction where it splits, just end locking at the switch and call it a
     * day."* - walked backwards, one way in means the tail certainly lies there; several means the graph cannot say which.  At an
     * UNMEASURED segment - *"if no length specified, just stop there.  Only segments with a positive length are determinate."*
     * So this UNDER-claims, knowingly: the alternative is blocking track on a guess, and a guess that refuses is still a refusal.
     *
     * @return every covered edge, with the locomotive whose tail covers it
     */
    synchronized public Map<Edge, Locomotive> edgesCoveredByStandingTrains()
    {
        Map<Edge, Locomotive> covered = new LinkedHashMap<>();

        walkStandingTrains(covered, new LinkedHashMap<String, Locomotive>());

        return covered;
    }

    /**
     * Each standing train's covered edges and claimed places, walked one train at a time (TDD-A1).
     *
     * The maps `edgesCoveredByStandingTrains` and `placesCoveredByStandingTrains` answer "whose tail is here" with ONE
     * train, the last walked; two tails that foul one switch from its two legs both claim its square, and only one is
     * recorded.  A question about every train on a place - Return Home's, which asks whether each has moved - reads
     * these instead.
     *
     * @return for each standing train, its covered edges (index 0) and its claimed places (index 1)
     */
    synchronized public Map<Locomotive, Object[]> tailsOfEachStandingTrain()
    {
        Map<Locomotive, Object[]> out = new LinkedHashMap<>();

        for (Point standing : this.points.values())
        {
            final Locomotive train = standing.getCurrentLocomotive();

            if (train == null || out.containsKey(train)) continue;

            Map<Edge, Locomotive> covered = new LinkedHashMap<>();
            Map<String, Locomotive> places = new LinkedHashMap<>();

            walkStandingTrains(covered, places, other -> other.equals(train));

            if (!covered.isEmpty() || !places.isEmpty()) out.put(train, new Object[] {covered, places});
        }

        return out;
    }

    /**
     * The same walk, answered in PLACES rather than whole edges (OB-207).
     *
     * An edge is covered whole or not at all, and `isPathClear` then refuses every edge sharing metal with a covered
     * one, so a one-unit train parked at the far end of a twelve-tile run would foul the whole of it.
     *
     * This says how far the tail actually reaches: the places are ordered along the edge and carry their own lengths, so the walk
     * spends the train's length across them from whichever end it entered by and stops.  A place is claimed when the tail reaches
     * it AT ALL, including an unmeasured one the train lies over for nothing, which keeps this on the refusing side of a rounding.
     *
     * Empty of any edge the build did not describe in places (a hand-written configuration or an older one).  Readers
     * pair it with `edgesCoveredByStandingTrains` and fall back to that.
     *
     * @return every claimed place, with the locomotive whose tail lies over it
     */
    synchronized public Map<String, Locomotive> placesCoveredByStandingTrains()
    {
        Map<String, Locomotive> places = new LinkedHashMap<>();

        walkStandingTrains(new LinkedHashMap<Edge, Locomotive>(), places);

        return places;
    }

    /**
     * Whether a place an edge runs over is the square a Point stands on (AUT-C3).
     *
     * Asked of the tile both name: a split square's block is its tile ("page:x,y"), or its tile and a road on a
     * double curve ("page:x,y/W"), and a built place is its tile, or its tile and a route id there.  A Point with no
     * block - a square emitted once, or a hand-written graph - cannot say which place is its square, so nothing is.
     *
     * @param placeId a place from an edge's places
     * @param point the Point
     * @return whether that place is the Point's own square
     */
    static boolean isTheSquareOf(String placeId, Point point)
    {
        String block = point == null ? null : point.getBlock();

        if (placeId == null || block == null) return false;

        return tileOf(placeId).equals(tileOf(block));
    }

    /**
     * The tile part of a place or block id: everything before a road or route written after the coordinates.
     *
     * Looked for after the last colon, because a page name may itself contain a slash.
     */
    private static String tileOf(String id)
    {
        int slash = id.indexOf('/', Math.max(0, id.lastIndexOf(':')));

        return slash < 0 ? id : id.substring(0, slash);
    }

    /**
     * The one walk behind both answers above, so the two can never disagree about where a tail is.
     *
     * Callers that want both - `isPathClear` does - come here directly rather than asking twice: the
     * walk visits every point and rescans the edge table for each covered segment, and doing that twice
     * per clearance check is real work for no new information.
     *
     * @param covered filled with every covered edge and the locomotive covering it
     * @param places filled with every claimed place and the locomotive lying over it
     */
    private void walkStandingTrains(Map<Edge, Locomotive> covered, Map<String, Locomotive> places)
    {
        standingTrains.walkStandingTrains(covered, places);
    }

    /**
     * The same walk, over only the trains asked for (TDD-A1).
     *
     * The maps hold ONE train per edge and per place, and the train walked last wins - in the order of `points`, which
     * nobody chose.  So a question about one train's route walks every OTHER train: its own tail is never an obstacle
     * to it (behaviour.md 5c), and walked with the others its claim could stand where another train's was.  And a
     * question that needs every train on a place walks them one at a time.
     *
     * @param covered filled with every covered edge and its train
     * @param places filled with every claimed place and its train
     * @param which the trains to walk, or null for every one
     */
    private void walkStandingTrains(Map<Edge, Locomotive> covered, Map<String, Locomotive> places,
        java.util.function.Predicate<Locomotive> which)
    {
        standingTrains.walkStandingTrains(covered, places, which);
    }

    /**
     * One standing train's tail walk (`StandingTrains.walkOneTail`), also saying how far behind the head each claimed
     * place begins (OB-294).
     *
     * Recorded as each place is claimed, which is before its length is spent - so the figure is the length of the body
     * between the head and the near end of that place, by the walk's own arithmetic rather than a second one.  Asked by
     * `whyItWouldMeetItsOwnTail`, which needs to know when the tail will have left each place.
     *
     * @param standing the Point the train stands on
     * @param loc the train
     * @param arrivedFrom the side it came in by, or null
     * @param arrivedAlong the road it came along, or null
     * @param covered filled with every covered edge and this train
     * @param places filled with every claimed place and this train
     * @param reach filled with each claimed place and how far behind the head it begins, or null when not wanted
     */
    private void walkOneTail(Point standing, Locomotive loc, String arrivedFrom, List<Edge> arrivedAlong,
        Map<Edge, Locomotive> covered, Map<String, Locomotive> places, Map<String, Integer> reach)
    {
        standingTrains.walkOneTail(standing, loc, arrivedFrom, arrivedAlong, covered, places, reach);
    }

    /**
     * The edges a train would cover standing at the end of this road, having driven it (OB-228).
     *
     * Asked by the Return Home planner of a train its plan has moved.  The side is the one the road's last rail comes in
     * by, and the road is the one the plan gave it - exactly what an arrival records - so this is the covering the runtime
     * will see once the move has been driven.
     *
     * @param standing where the train would stand
     * @param loc the train
     * @param road the route it would have driven, ending at `standing`
     * @return every edge its tail would cover
     */
    synchronized public Map<Edge, Locomotive> edgesATailWouldCover(Point standing, Locomotive loc, List<Edge> road)
    {
        Map<Edge, Locomotive> covered = new LinkedHashMap<>();

        tailAlong(standing, loc, road, covered, new LinkedHashMap<String, Locomotive>());

        return covered;
    }

    /**
     * The same, in places - see `placesCoveredByStandingTrains`.
     *
     * @param standing where the train would stand
     * @param loc the train
     * @param road the route it would have driven
     * @return every place its tail would claim
     */
    synchronized public Map<String, Locomotive> placesATailWouldCover(Point standing, Locomotive loc, List<Edge> road)
    {
        Map<String, Locomotive> places = new LinkedHashMap<>();

        tailAlong(standing, loc, road, new LinkedHashMap<Edge, Locomotive>(), places);

        return places;
    }

    private void tailAlong(Point standing, Locomotive loc, List<Edge> road, Map<Edge, Locomotive> covered,
        Map<String, Locomotive> places)
    {
        standingTrains.tailAlong(standing, loc, road, covered, places);
    }

    /**
     * Whether this square is a PARKING BERTH: somewhere a train is meant to sit rather than pass.
     *
     * Adam's ruling on FR-060: **"For now, we consider anything with autodestination=false and only one way in/out as a parking
     * square.  We can revisit dedicated marking if this doesn't work out with clean logic, or if it gets too confusing to the
     * user to manage."**
     *
     * **Two facts, and neither is enough alone.**  `autoDestination` off says the OPERATOR does not want autonomy choosing this
     * station (true of a parking berth and also of a platform they keep for themselves); one way in and out says the TRACK is a
     * dead end (true of a berth and also of a headshunt nobody parks on).
     *
     * **Counted in SQUARES, not copies**: a square split into a northbound and a southbound Point is one place with one way
     * out.  `Point.isSamePlaceAs` knows two Points are one piece of track, the relation `whyABerthCannotHoldIt` uses too.
     *
     * **What this does NOT decide.**  It is not the berth rule's gate: `whyABerthCannotHoldIt` applies wherever autonomy will
     * not choose the square, which is wider on purpose (Adam's own example for that rule is TunnelLongPark, which has three
     * ways in and is not a parking square by this test).
     *
     * @param square any copy of the square
     * @return whether this is somewhere trains are put away
     */
    public boolean isParkingSquare(Point square)
    {
        if (square == null || square.isAutoDestination()) return false;

        List<Point> ways = new LinkedList<>();

        for (Point copy : this.points.values())
        {
            if (!copy.isSamePlaceAs(square)) continue;

            for (Edge touching : this.getNeighborsAndIncoming(copy))
            {
                Point other = touching.getStart() == copy
                    ? touching.getEnd() : touching.getStart();

                if (other == null || other.isSamePlaceAs(square)) continue;

                boolean known = false;

                for (Point already : ways)
                {
                    if (already.isSamePlaceAs(other)) known = true;
                }

                if (!known) ways.add(other);
            }
        }

        return ways.size() == 1;
    }

    /**
     * The train, other than the one being routed, whose tail lies over metal this edge runs on - or null (AUT-B1).
     *
     * By place, so every copy of a rail answers alike: the rail into a square's plain copy and into its turning copy
     * run over the same places, and so do the two directions of one rail.
     *
     * @param edge the edge being asked about
     * @param mover the locomotive being routed, whose own tail never blocks it
     * @param claimed the places standing trains' tails reach, from `walkStandingTrains`
     * @return the train lying there, or null
     */
    static Locomotive anotherTailOn(Edge edge, Locomotive mover, Map<String, Locomotive> claimed)
    {
        return StandingTrains.anotherTailOn(edge, mover, claimed);
    }

    /**
     * Whether a named locomotive's tail lies over any of the metal this edge runs on (OB-207).
     *
     * `isPathClear` asks it beside `anotherTailOn`, which refuses the same tails, so its refusals are pinned where
     * nothing else asks: Return Home's start-time check, in `testReturnHomeKeepsClearOfTheTailsItLeaves`.
     *
     * @param edge the edge being asked about
     * @param who the locomotive standing somewhere on the layout
     * @param claimed the places its tail reaches, from `walkStandingTrains`
     * @return true when the two meet
     */
    static boolean tailLiesOn(Edge edge, Locomotive who, Map<String, Locomotive> claimed)
    {
        return StandingTrains.tailLiesOn(edge, who, claimed);
    }
    /**
     * Which compass side of `from` the point `to` lies on: the sign of the larger coordinate difference, a tie to the
     * horizontal, as the editor names sides and as `arrivedFrom` is recorded.  Null when either point has no coordinates (a
     * station built from something the diagram never placed).
     *
     * @param from the point being stood on
     * @param to a neighbour
     * @return "N", "S", "E", "W", or null
     */
    public String sideTowards(Point from, Point to)
    {
        if (from == null || to == null) return null;

        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();

        if (dx == 0 && dy == 0) return null;

        if (Math.abs(dx) >= Math.abs(dy)) return dx > 0 ? "E" : "W";

        // Y GROWS DOWNWARDS on the diagram, as in every grid drawn from the top left, so a larger y is SOUTH; the wrong way round
        // would put every tail on the wrong side of its train.
        return dy > 0 ? "S" : "N";
    }
    /**
     * Whether a point's square is a station - any copy of it a destination - which is what a locomotive list is read by: on a
     * station it names the locomotives that may not stop there, and they may still pass; on any other square, the ones that may
     * not pass at all (`514071ee`).  Not the copy's own flag: a station with an arrival side barred is built with that side's
     * copy as no destination (`AutonomyBuilder`: "A station, unless a train arriving THIS way is not allowed to stop"), and read
     * as "not a station" its list would stop the trains it keeps out from driving through it that way (RSA65-B1).
     *
     * @param point any copy
     * @return whether its square is a station
     */
    public boolean isAStationSquare(Point point)
    {
        if (point == null) return false;

        if (point.isDestination()) return true;

        final String place = point.getBlock();

        // No block is a square with one copy, whose own flag is the whole answer
        if (place == null) return false;

        for (Point other : this.points.values())
        {
            if (other != point && place.equals(other.getBlock()) && other.isDestination()) return true;
        }

        return false;
    }

    /**
     * Whether trains may turn round at this piece of track, on any of its copies.
     *
     * A square that trains MAY turn at is not marked: the build expresses it by splitting the square
     * into a plain copy and a turning one, and only the turning copy carries `reversing`.  So the
     * question "may a train turn here" is about the PLACE rather than about the copy, and the copies
     * of one place are the Points that share its block.
     *
     * @param point any copy
     * @return whether trains may turn round at that place
     */
    public boolean mayReverseAt(Point point)
    {
        if (point == null) return false;

        if (point.isReversing()) return true;

        final String place = point.getBlock();

        // No block is a square with one copy, and one copy cannot be the plain half of a split.
        if (place == null) return false;

        for (Point other : this.points.values())
        {
            if (other != point && place.equals(other.getBlock()) && other.isReversing()) return true;
        }

        return false;
    }

    /**
     * What autonomy uses, and what every caller used before there was a choice.
     *
     * **Which callers get it, and why that was decided rather than defaulted (DIR-C9).**  Four places call `executePath`.  The
     * two hand-driven doors pass a prompt.  The other two take the four-argument overload and land here:
     *
     * - autonomy's own dispatch loop, which chose the path itself and whose `pickPath` already refuses any path that reverses
     *   along the way, so the only reversal it can reach is one it meant;
     * - the timetable, which `Return Home` loads.  `executeTimetableInternal` sets `running`, so a staging run IS an autonomous
     *   run, and `HomeStaging` plans the reversals it needs; asking the operator about a turn the planner chose on their behalf
     *   would be asking about a decision they already made.
     *
     * A new caller inherits this by writing four arguments, which is easy to do without meaning it - hence this list.
     */
    public static final ReversalPolicy ALWAYS_REVERSE = (loc, at) -> true;

    /**
     * The policy a timetable entry is played back with (RSA49-B1): autonomy's, for an entry recorded from autonomy or
     * before the answer was kept; for one recorded by hand, the answer its arrival was given, at its destination only - the
     * shape `ManualReversalPrompt.forJourney` hands in - so the train ends turned or kept, and stood, as it was recorded.
     *
     * **Only where a turn is still the railway's to make** (RSA52-C1; Adam: *"The path should organically fail in the
     * direction no longer allowed."*): a square with a turning copy.  Where the station has since lost
     * "Trains May Change Direction Here", the recorded turn is not made, and an entry that needs the train the other way
     * fails as any journey would.
     *
     * @param ttp the entry
     * @return the policy
     */
    ReversalPolicy playedBack(TimetablePath ttp)
    {
        final Boolean recorded = ttp == null ? null : ttp.getTurnAtTheEnd();

        if (recorded == null || ttp.getPath().isEmpty()) return ALWAYS_REVERSE;

        final Point end = ttp.getPath().get(ttp.getPath().size() - 1).getEnd();

        boolean turnsTrains = false;

        for (Point copy : this.points.values())
        {
            if ((copy == end || end.isSamePlaceAs(copy)) && (copy.isTerminus() || copy.isReversing())) turnsTrains = true;
        }

        final boolean turn = recorded && turnsTrains;

        return new ReversalPolicy()
        {
            @Override
            public boolean shouldReverse(Locomotive loc, Point at)
            {
                return turn && at == end;
            }

            @Override
            public boolean asksAbout(Point at)
            {
                return at != null && at == end;
            }
        };
    }

    /**
     * Locks a path and runs the locomotive from the start to the end
     * @param path
     * @param loc
     * @param speed 
     * @param ttp - null if not running a timetable route
     * @return  
     */
    public boolean executePath(List<Edge> path, Locomotive loc, int speed, TimetablePath ttp)
    {
        return executePath(path, loc, speed, ttp, ALWAYS_REVERSE);
    }

    /**
     * The same, with a say in whether an intermediate reversal happens.
     *
     * @param path the route
     * @param loc the train
     * @param speed how fast
     * @param ttp the timetable entry, or null
     * @param reversals asked at each may-reverse point on the way; see ReversalPolicy
     * @return whether the train got there
     */
    public boolean executePath(List<Edge> path, Locomotive loc, int speed, TimetablePath ttp,
        ReversalPolicy reversals)
    {
        return executePath(path, loc, speed, ttp, reversals, -1);
    }

    /**
     * A journey sent by hand: executePath, with the train marked as a hand send for its length (BCR-C1), which the cap on
     * trains out neither counts nor refuses however many a run already has out (behaviour.md 1).  Every hand door comes
     * here - the track diagram's and the Auto tab's, through `TrainControlUI.sendATrainByHand`.
     *
     * @param path the route
     * @param loc the train
     * @param speed how fast
     * @param reversals asked at each may-reverse point on the way; see ReversalPolicy
     * @param stopsAtChoice `stopsOrdered()` as read at the click, or -1 to read it now
     * @return whether the train arrived
     */
    public boolean executePathByHand(List<Edge> path, Locomotive loc, int speed, ReversalPolicy reversals,
        int stopsAtChoice)
    {
        // Marked here only if not already: a second hand send of a train on one is refused inside, and must not take the
        // first one's mark away with it
        boolean marked = loc != null && this.sentByHand.add(loc);

        try
        {
            return executePath(path, loc, speed, null, reversals, stopsAtChoice);
        }
        finally
        {
            if (marked) this.sentByHand.remove(loc);
        }
    }

    /**
     * The same, carrying the stops counted where the journey was chosen (RSA2-C2): before autonomy's choice, at a hand
     * send's click, at a timetable's start or Return Home's press.  A stop ordered since sends nothing, where a count
     * read only when the journey begins let a thread held between its choice and its dispatch send its train after the
     * operator's Yes.
     *
     * @param path the route
     * @param loc the train
     * @param speed how fast
     * @param ttp the timetable entry, or null
     * @param reversals asked at each may-reverse point on the way; see ReversalPolicy
     * @param stopsAtChoice `stopsOrdered()` as read where the journey was chosen, or -1 to read it now
     * @return whether the train arrived
     */
    public boolean executePath(List<Edge> path, Locomotive loc, int speed, TimetablePath ttp,
        ReversalPolicy reversals, int stopsAtChoice)
    {
        // A TIMETABLE ENTRY RUNS FROM THE COPY ITS TRAIN STANDS ON (RSA53-B1, RSA54-A1): on another copy of the start
        // square facing the same way, its first edge swapped for that copy's own - the train is not moved, so its tail
        // keeps the rails by its side.  An entry only - a hand send, autonomy and a route choose their path from the copy
        // the train stands on.  Here and not in executePathInternal, so the cleanup below unlocks the path that was locked.
        if (ttp != null)
        {
            List<Edge> from = this.pathFromWhereItStands(path, loc);

            if (from != null) path = from;
        }

        // A dispatch is a train under way, however it was asked for.  runLocomotive counts autonomy's driving threads so
        // that a locomotive between one path and the next is never invisible to isRunning(); a train dispatched BY HAND
        // (a bare thread straight into this method) must be counted too, because activeLocomotives is not written until
        // configureAndLockPath has reserved every point, and for that locking phase everything built on isRunning()
        // would stand down, including the protection that should throw the destination's signal.
        //
        // Adam's rule: "The same thing should happen in manual operation vs auto - the same switches and signals set, and guards
        // applied."  So the same guards apply for the length of a hand dispatch: the editors refuse to open, the simulation
        // cannot be toggled, and locomotives cannot be edited or deleted, exactly as while autonomy runs.
        //
        // Nested under autonomy, which has already counted its own thread, so this can only reach zero when the LAST thing
        // finishes - and reaching zero announces the end of the run, which is how the interface learns to re-enable what it
        // disabled.  COUNTED, not read (SVN-C1): `locomotiveThreads` is what tells the rest of this class whether anything is
        // running.
        this.locomotiveThreads.incrementAndGet();

        try
        {
            try
            {
                return executePathInternal(path, loc, speed, ttp, reversals, stopsAtChoice);
            }
            catch (RuntimeException e)
            {
                // An unexpected failure part way through a path must not leave the locomotive registered in activeLocomotives:
                // nothing else clears that map, so isRunning() stays true forever and every guard built on it with it (autonomy
                // cannot start, simulation cannot be toggled, locomotives cannot be edited) until the graph is reloaded.
                // This deliberately does NOT use finally (the version fence returns normally when a path is abandoned after the
                // layout is replaced, and that entry stays on purpose), and does NOT swallow the exception (executeTimetable
                // catches around executePath and stops the run; returning false would feed its retry loop on a permanent fault).
                // releasedEarly (and clearedEdges) is cleared after the release below, not here (VD10-A1): `unlockPath` reads it to
                // know which edges the tail already gave up (GUI-A1, DCN4-C1), and emptying it first would release each of them a
                // SECOND time with its lock edges, taking away a claim somebody else made in between.  `atomicRoutes` is false on
                // the operator's own configuration, so this is the live case.
                //
                // WHETHER THIS LOCOMOTIVE EVER GOT ITS PATH, read BEFORE the removal below (ACC-A1): it joins `activeLocomotives`
                // only after `configureAndLockPath` has returned, so membership is exactly "did the lock phase succeed", and asked
                // after the removal it is always false.  A fact a recovery destroys must be captured before the recovery starts.
                final boolean hadItsPath;

                synchronized (this.activeLocomotives)
                {
                    hadItsPath = this.activeLocomotives.containsKey(loc);

                    this.activeLocomotives.remove(loc);
                    this.locomotiveMilestones.remove(loc);
                }

                // And any unfinished claim on a slot, for the same reason the registration is cleared:
                // one left behind lowers the cap for good.
                this.takingPath.remove(loc);

                // AND THE RUN STOPS ITSELF, GRACEFULLY (RC-A11; Adam: "we need a way to alert the operator so they can park the
                // trains and restart them.  Autonomy gracefully stopping itself so that it can be resumed is the right approach."):
                // the failed locomotive may be standing on its path's edges and every other train is still running, so nothing
                // watches that stretch of track.  This is the graceful stop the button performs: `running` goes false, so no new
                // path is dispatched, and each train already underway finishes its current path and stops at a station; Start puts
                // the railway back.  The end-of-run callback still fires from the finally below.  Read BEFORE the stop, which
                // clears it.
                final boolean wasRunning = this.running;

                stopLocomotives();

                // THE RIGHT SENTENCE FOR WHICHEVER IT WAS (DAY-C3): `executePath` has two other callers, both manual (the commands
                // panel and the diagram's right-click menu), which work with autonomy stopped, and "Autonomy has stopped itself so
                // the railway can be parked and started again" would send the operator to press Start over a failed hand dispatch.
                // `wasRunning` is read before `stopLocomotives()` clears it, as `hadItsPath` is read before the removal.
                this.control.logf(wasRunning
                    ? "autolayout.errorRunStoppedByFailure"
                    : "autolayout.errorHandDispatchFailed", loc.getName());

                // AND THEN THE TRACK GOES BACK (Adam: "Force a graceful stop, alert the user, then unlock."), in that
                // order, which is what makes the third safe.  Keeping the path locked would not protect it: the handler
                // above has already given up `activeLocomotives`, `locomotiveMilestones` and `clearedEdges`, which
                // `getActiveAccs` reads to know which accessories a route must not throw, so it would be held and
                // unprotected.  `stopLocomotives()` closes the gap that mattered: `running` is false, so autonomy
                // dispatches no new path over this track (a person still can, from the right-click menu, which is why
                // the message says to look at where that locomotive stands).
                //
                // Released the same way a finished path is released, and in the same ORDER (VD10-A1): `unlockPath`
                // consults `releasedEarly` (GUI-A1), so it runs while that map still has the entries, and the clear
                // follows it.  THE TRAIN ITSELF IS STOPPED FIRST (VD10-B1): the line below is the only thing that stops
                // THIS train (`stopLocomotives()` only sets `running = false`), and the release is not instantaneous
                // (`unlockPath` is synchronized on the layout monitor, which `configureAndLockPath` holds "for seconds
                // on a long path"), so a hand dispatch at the same moment could keep this train running on track the
                // model had just declared free.  Guarded so that a second failure here cannot replace the exception
                // that explains what went wrong.
                try
                {
                    loc.setSpeed(0);
                }
                catch (RuntimeException stopFailure)
                {
                    this.control.log(stopFailure);
                }
                synchronized (this.activeLocomotives)
                {
                    // ONLY A PATH THIS LOCOMOTIVE ACTUALLY GOT (ACC-A1).  This handler also catches a failure from the
                    // LOCK PHASE, which `configureAndLockPath` already recovers completely: it releases the prefix it
                    // took, and `handleMisconfiguredPath` re-reserves the locomotive on the point it never left, then
                    // rethrows.  Unlocking the WHOLE path over that would clear the start reservation at `unlockPath`'s
                    // `i == 0` clause (the train stands on a square the model believes empty) and release edges never
                    // taken, each cascading to lock edges whose occupancy is a COUNT shared with other running
                    // dispatches.  `activeLocomotives` is the discriminator: a locomotive joins it only after
                    // `configureAndLockPath` has returned, so its absence means the lock phase is what threw and has
                    // cleaned up after itself.
                    if (hadItsPath)
                    {
                        this.unlockPath(path, loc);
                    }

                    this.clearedEdges.remove(loc);
                    this.releasedEarly.remove(loc);
                }

                // And the sensor this locomotive was said to be heading for: a route condition asking "has it reached that sensor
                // yet" waits on this entry, and an entry left behind by a failure is one nothing will ever clear.  notifyAll
                // inside releases anyone already waiting.
                updatePendingS88(loc, null);


                this.control.log(e);

                throw e;
            }
        }
        finally
        {
            // In a finally for the reason runLocomotive's is: a thread killed by anything at all still stops being counted, or the
            // layout reads as running for the rest of the session and nothing can be edited again without a restart.
            if (this.locomotiveThreads.decrementAndGet() == 0)
            {
                announceRunFinished();
            }
        }
    }

    /**
     * The body of executePath.  Call executePath, which adds the cleanup this cannot do for itself.
     * @param path
     * @param loc
     * @param speed
     * @param ttp
     * @return 
     */
    private boolean executePathInternal(List<Edge> path, Locomotive loc, int speed, TimetablePath ttp,
        ReversalPolicy reversals, int stopsAtChoice)
    {    
        // THE STOPS COUNTED WHERE THIS JOURNEY WAS CHOSEN (RSA-C1, RSA2-C2) - before autonomy's choice, at a hand
        // send's click, at a timetable's start - or, for a caller that chose nothing, now: before the claim, which can
        // wait seconds behind another train's switches.  A stop ordered since is one this journey obeys.
        final int stopsAtDispatch = stopsAtChoice < 0 ? this.stopsOrdered.get() : stopsAtChoice;

        // AND THE POWER'S EPOCH, here, before the route's first switch (RSA62-C2): one read, which says whether the power is off
        // now (odd) and, asked again once the route is set, whether it went off in between (RSA63-C1).
        final int powerAtDispatch = this.control.getPowerEpoch();

        // AND ONE ORDERED ALREADY SENDS NOTHING, and says so (RSA2-C2, RSA2-C8)
        if (this.stopsOrdered.get() != stopsAtDispatch)
        {
            this.control.logf("autolayout.log.notSentAfterTheStop", loc.getName());

            return false;
        }

        // Sanity check
        if (!this.isValid())
        {
            // With the reason THIS layout recorded when it was invalidated - not lastError, which by
            // now holds whatever path failed most recently and named the wrong thing convincingly.
            this.control.logf("autolayout.errorConfigurationInvalidMustReload");

            if (this.invalidReason != null)
            {
                this.control.log(this.invalidReason);
            }

            return false;
        }

        // Retired graph: refuse before anything is commanded.  The speed writes below are each fenced, but configureAndLockPath
        // and the departure function would run first, throwing every switch and signal on a path belonging to a replaced layout.
        if (!this.isCurrentLayout())
        {
            return false;
        }

        // A speed of 0 is not a dispatch.  Two semi-autonomous paths pass the locomotive's preferred speed, which is 0 for one
        // placed on a node without the speed dialog ever being opened; the path would be locked, the departure functions fired,
        // and the thread parked forever in waitForOccupiedFeedback on a sensor a stationary train can never reach, so isRunning()
        // stayed true and autonomy, the simulation toggle and locomotive editing were blocked until the graph was reloaded.
        // runLocomotive refuses by throwing, which its caller turns into "the configuration is invalid, reload it"; refusing
        // here is both earlier and narrower.
        if (speed < 1 || speed > 100)
        {
            this.control.logf("autolayout.errorInvalidSpeedSpecified");
            return false;
        }

        if (path.isEmpty())
        {
            this.control.logf("autolayout.errorPathEmpty");
            return false;
        }

        if (loc == null)
        {
            this.control.logf("autolayout.errorLocomotiveIsNull");
            return false;
        }

        // ALREADY OUT, in either of the two senses (REG9-A2, REG9V-B2): asked once, through `isAlreadyUnderway`, which a test
        // can also ask.
        if (this.isAlreadyUnderway(loc))
        {
            this.control.logf("autolayout.errorLocomotiveBusy", loc.getName());
            return false;
        }

        Point start = path.get(0).getStart();

        if (!loc.equals(start.getCurrentLocomotive()))
        {
            this.control.logf("autolayout.errorLocomotiveNotAtPathStart", loc.getName());
            return false;
        }

        // A ROUTE THAT ENDS WHERE IT STARTS - a lap - is refused (RSA30-C2).  Run, its arrival took the train off the
        // station it had just reached, leaving it on no Point with that station's exit guard green under it; and in
        // non-atomic mode the release behind it freed the station while the train was still on its way there.  No door
        // offers one; a timetable read from a file can carry one, and is refused at the read.
        if (start == path.get(path.size() - 1).getEnd())
        {
            this.control.logf("autolayout.errorPathEndsWhereItStarts", start.getName());
            return false;
        }
        
        // NO SIGNAL SWEEP HERE (OB-166; D24-C3, AU-B7): a sweep releases as well as protects, so a hand dispatch would
        // drive every empty protected platform green, including signals a route had set.  See runLocomotives for the
        // whole reason.  Adam's rule stands: "The same thing should happen in manual operation vs auto - the same
        // switches and signals set."  `refreshProtectingSignal` states the same for itself: no commanding the railway
        // while the operator is still deciding what it should look like.

        boolean result;
        
        result = configureAndLockPath(path, loc, reversals);

        if (!result)
        {
            // configureAndLockPath has already logged the reason (path occupied, or a validation failure that stopped the loco and
            // released its locks) - do not run the locomotive.
            //
            // NOTHING TO DROP HERE (AUT-C1, AUT2-C1).  Every refusal inside `configureAndLockPath` either took nothing - the train
            // already under way, the path not clear - or drops its own claim before it returns.  Removing the claim here could
            // only ever remove ANOTHER dispatch's (two dispatches of the same route carry equal paths, so `remove(loc, path)` does
            // not help), leaving the train in neither map while it was still locking: a third dispatch let through, the cap
            // undercounted.
            return false;
        }
        else
        {
            // A STOP ORDERED WHILE THIS CLAIMED ITS ROUTE (RSA-C1, GS-C1): the operator's Yes stopped the trains under
            // way, and this one was not yet among them - so the route is given back unrun, and the train stands where
            // it was.
            if (this.stopsOrdered.get() != stopsAtDispatch)
            {
                giveBackUnrun(path, loc, "autolayout.log.notSentAfterTheStop");

                return false;
            }

            // NOR IF THE TRACK POWER WENT OFF WHILE ITS ROUTE WAS SET (RSA61-C1, GST-B1): setting a long route takes a second and
            // more, so a cut in that window would leave the train to be given its speed, to take effect the moment the power came
            // back.  Given back unrun, as a stop ordered in the same window is; autonomy's loop and the timetable's wait for the
            // power and go on.  A CUT THAT CAME AND WENT TOO (RSA62-C2): asked only whether the power was on now, a cut begun and
            // ended while the route was set went unseen, so the power's epoch is compared with the one at dispatch.
            if (!this.control.getPowerState() || this.control.getPowerEpoch() != powerAtDispatch)
            {
                giveBackUnrun(path, loc, "autolayout.log.notSentPowerOff");

                return false;
            }

            synchronized (this.activeLocomotives)
            {                
                // CopyOnWriteArrayList: this list is only ever appended to (below) and read by the UI
                // (getReachedMilestones/getLatestMilestoneS88), so those reads iterate a snapshot with no lock.
                this.locomotiveMilestones.put(loc, new CopyOnWriteArrayList<>());
                this.locomotiveMilestones.get(loc).add(start);
                this.clearedEdges.put(loc, ConcurrentHashMap.<Edge>newKeySet());
                this.releasedEarly.put(loc, ConcurrentHashMap.<Edge>newKeySet());
                this.activeLocomotives.put(loc, path);

                // Counted for real now, so the claim is given up.  Kept as a union rather than a sum
                // above, so the order of these two lines cannot make one train read as two.
                this.takingPath.remove(loc);
            
                // Fire callbacks
                for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
                {
                    fireCallback(callback, path, loc, true);
                }
                            
                if (ttp != null)
                {
                    ttp.setExecutionTime(System.currentTimeMillis());
                }
            }
             
            this.control.logf(
                "autolayout.infoExecutingPathForLocomotive",
                this.pathToString(path),
                loc.getName()
            );
            // AND WHAT ITS ARRIVAL IS TOLD, for a send by hand (RSA49-B1): played back as autonomy, a train kept at a
            // turning copy was turned, and the next entry, recorded from the copy it was stood on, could not start
            if (this.addTimetableEntry(loc, path) && reversals != null && reversals != ALWAYS_REVERSE)
            {
                this.answerTheEntryRecorded(loc, path, turnsOnArrival(path.get(path.size() - 1).getEnd(), loc, reversals));
            }
        }
        
                    
        if (loc.hasCallback(CB_ROUTE_START))
        {
            loc.getCallback(CB_ROUTE_START).accept(loc);
        }
        
        // FENCED AS EVERY SPEED BELOW IS (GS-C1, RSA-C1): a stop ordered since this journey was dispatched keeps the
        // train standing, and so does a railway retired while its route was claimed.
        if (drive(loc, speed, stopsAtDispatch))
        {
            this.control.logf(
                "autolayout.infoLocomotiveStarted",
                loc.getName()
            );
        }

        // When !this.atomicRoutes: track edges to unlock based on length of train
        // Edges waiting to be released, each with how far the HEAD has travelled since the end of it.  Two numbers per
        // edge, not one running total: a total summed over the edges WAITING would release everything in the batch the
        // moment it added up to the train's length, including track the train was standing on.  The distance from an
        // edge's END to the head is the only thing that answers "has the tail gone by", because the tail is exactly the
        // train's length behind the head.
        //
        //   { index into path, how far the head has gone since that edge ended }
        List<int[]> waitingToClear = new LinkedList<>();

        // Asked of the WHOLE path, once, before anything is handed back (VAL-A1): the escape below means "nothing on this path has
        // a length, so distance can never accumulate", a fact about the path, not about a running total.
        boolean pathIsUnmeasured = pathIsUnmeasured(path);

        // THE TRAIN'S LENGTH AS IT WAS DISPATCHED (RSA-A1): what this journey hands back behind the train is measured
        // by it, and a length changed while the train runs - which no door allows - must not shorten what it holds.
        final Integer lengthAtDispatch = loc.getTrainLength();

        // WHERE THE ROUTE HAS RUN TO, AND WHERE THE HEAD HAS BEEN SEEN (GS-B3): distances from the start of the path - to
        // the far end of the edge this loop has reached, and to the far end of the last edge whose end Point's sensor
        // answered.  An edge's distance behind the head is the second less where that edge ends.  At the first Point
        // nothing is behind the head yet, so `headSeenAt` starts at nothing.
        int pathRunTo = path.get(0).getLength();
        int headSeenAt = 0;

        for (int i = 0; i < path.size(); i++)
        {
            Point current = path.get(i).getEnd();

            // Recorded when its sensor answers, and so not again at the end of this step (RSA30-C4)
            boolean recordedAlready = false;

            // WHETHER THIS POINT'S SENSOR ANSWERED FOR THE HEAD (GS-B3), the only proof of where the head is
            boolean seenHere = false;
            
            if (i != path.size() - 1)
            {
                // Adjust speed based on multiplier
                if (isCurrentLayout())
                {
                    int calculatedSpeed = (int) Math.ceil((double) speed * current.getSpeedMultiplier());
                    calculatedSpeed = Math.min(calculatedSpeed, 100);
                    
                    // Not after a stop ordered since dispatch (RSA-C1): a train stopped between two sensors that
                    // brakes onto the one ahead is not given the next leg's speed back.
                    if (loc.getSpeed() != calculatedSpeed && drive(loc, calculatedSpeed, stopsAtDispatch))
                    {
                        this.control.logf(
                            "autolayout.infoAdjustingSpeedForLocomotive",
                            calculatedSpeed,
                            loc.getName()
                        );
                    }
                }
                
                // Intermediate points - wait for feedback to be triggered and to clear
                if (current.hasS88() && isCurrentLayout())
                {
                    // ALREADY OCCUPIED WHEN ITS WAIT BEGINS, the answer is no proof of where the head is (RSA61-A1): a
                    // sensor two Points in a row share, or one another train stands on, reads occupied before this train's
                    // head is anywhere near it, and the wait returns at once.  Asked before simulation announces the train.
                    boolean occupiedAlready = loc.isFeedbackSet(current.getS88()) && loc.getFeedbackState(current.getS88());

                    long simEpoch = 0;

                    if (this.simulate)
                    {
                        loc.delay(this.getMinDelay(), this.getMaxDelay());
                        simEpoch = simAnnounce(current.getS88());
                    }
                    
                    this.updatePendingS88(loc, current.getS88());

                    // With an advisory, because THIS is the wait that means "a train was sent and has not got there".  Nothing acts
                    // on it; it only stops the operator having to notice that a train has gone quiet.  Asked for HERE rather than
                    // inside the wait, because "this train was dispatched" is a fact the dispatch loop has and the wait does not
                    // (a route's trigger monitor uses the same wait and sits on its sensor as long as the layout runs).
                    loc.waitForOccupiedFeedback(current.getS88(),
                        Locomotive.FEEDBACK_DURATION_THRESHOLD, Locomotive.FEEDBACK_ADVISORY_MS, this.retiredRailway);

                    // SEEN HERE (GS-B3) - unless the wait was given up for a retired railway, which goes no further, or the
                    // sensor already read occupied when it began (RSA61-A1): then the head is credited where the next sensor
                    // answers, as at a Point with no sensor
                    seenHere = this.isCurrentLayout() && !occupiedAlready;
                    
                    if (this.simulate)
                    {            
                        final long announcedEpoch = simEpoch;

                        new Thread(() -> 
                        {
                            loc.delay(this.getMinDelay(), this.getMaxDelay());
                            simClearBehind(current.getS88(), announcedEpoch);
                        }).start();
                    }
                }    

                // REACHED, AND RECORDED AS SOON AS ITS SENSOR ANSWERS (RSA30-C4), before a turn here: recorded only at the end of the
                // step, a train stopped to turn was placed a square back for the length of the turn.
                synchronized (this.activeLocomotives)
                {
                    List<Point> reached = this.locomotiveMilestones.get(loc);

                    if (reached != null) reached.add(current);
                }

                recordedAlready = true;
                
                // Reverse the locomotive if this is a reversing station - IF THE CALLER SAYS SO.  A terminus is not a question: the train has run out of
                // track, so it turns whatever the policy thinks.  Everywhere else the policy decides, which is how a hand-driven send comes to ask the
                // operator and autonomy does not.
                //
                // STOPPED FIRST, THEN ASKED (DIR-A1), and the stop IS the gate's answer (CONF-A2): a train is brought to
                // a stand exactly when it is about to be turned, and never otherwise.  The question is put to a person
                // through a modal dialog with no time limit, so the stop must not sit inside the branch that answer
                // decides (otherwise the train would run past a headshunt for as long as the dialog stood open).
                // Nothing in the call blocks (the question is put at departure), so evaluating before stopping is safe,
                // and a journey nobody is turning never enters it.
                //
                // THE STOP IS NOT `mayReverseAt` (SPEC-A2, REG6-B3), which is SQUARE-WIDE: `AutonomyBuilder.build` gives every copy of a multi-copy
                // square the same block, so it is true at the PLAIN copy as well as the turning one, and autonomy (through `ALWAYS_REVERSE`) would stop
                // dead at plain copies it passes at line speed.  A turning copy stops because turning is what it is for (`current.isReversing()`); the
                // operator half is asked of the DOOR, the only thing that knows a square is may-reverse, since `canReverse` never reaches `parseAuto`.
                if (isCurrentLayout()
                    && shouldReverseAt(current, path.get(path.size() - 1).getEnd(), loc, reversals))
                {
                    loc.setSpeed(0).waitForSpeedBelow(1);

                    // AND SAID SO (RSA31-C2): the track diagram is redrawn when these fire, and the next fire is the step's end,
                    // after the turn, so without this a train turning with nothing else moving was drawn running at the sensor before.
                    synchronized (this.activeLocomotives)
                    {
                        for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
                        {
                            fireCallback(callback, path, loc, true);
                        }
                    }

                    this.control.logf(
                        "autolayout.infoIntermediateReversingForLocomotive",
                        loc.getName()
                    );

                    loc.switchDirection()
                    .delay(this.getMinDelay(), this.getMaxDelay()); // a more realistic appearance

                    // AT THIS POINT'S OWN SPEED, not the journey's (REG6-B3): restoring the raw `speed` would discard the multiplier
                    // applied for this square, so a train that stopped to turn resumed at full line speed on a stretch its owner
                    // had set to run slow.
                    int resume = Math.min((int) Math.ceil(
                        (double) speed * current.getSpeedMultiplier()), 100);

                    // Not after a stop ordered since dispatch (RSA-C1) - and then not waited for either
                    if (drive(loc, resume, stopsAtDispatch))
                    {
                        // GIVEN UP on a stop ordered since, or a retired railway (RSA2-C2): a Yes landing between the
                        // write and this wait leaves the speed where only the next driver would raise it
                        loc.waitForSpeedAtOrAbove(resume, () -> this.stopsOrdered.get() != stopsAtDispatch
                            || !this.isCurrentLayout());
                    }
                }
                
                // We can also clear the edges dynamically 
                // This can be useful, but extra care needs to be taken if any paths cross over
                // Therefore, we use setLockedEdgeUnoccupied and unlock 1 edge prior to the current one
                // path.get(i).setUnoccupied();
                //
                // This runs in both atomic and non-atomic modes, so something always knows where the train's tail is; what
                // atomicRoutes decides is whether the edge is UNLOCKED, a different question.
                //
                //   cleared  - "the train is no longer on top of this".  Both modes.
                //   unlocked - "another train may be routed here".  Non-atomic only.
                //
                // Two questions, one answer to when the tail has gone by, computed once.  Adam: "once a train passes, signals on
                // the route, but behind the train, should still be allowed to be changed by auto routes."
                if (isCurrentLayout())
                {
                    if (i > 0)
                    {       
                        // THE HEAD HAS FINISHED EDGE i, NOT EDGE i-1 (GS-B3).  `current` is `path.get(i).getEnd()`: once its sensor has answered, the
                        // head is at the far end of edge i, so edge i-1, which ended where edge i begins, is edge i's length behind it, and every edge
                        // before that is further back by the same (`core.testTrainTailClearsEdges`: edges of 100, 100 and 0 and a train of 250, "edge 0
                        // has 100 behind it" when the head finishes the third).
                        //
                        // BUT NOT THE SENSOR'S OWN SQUARE.  A sensor answers when the head ENTERS its square, so all that is known to be behind the head is
                        // the edge up to that square; the square is credited when the next sensor answers (`arrivingSquare`).  Crediting it at once could
                        // hand an edge back with the tail still on it, and a release must never be early (Adam chose releasing track "earlier, never unsafe").
                        //
                        // ONLY AS FAR AS A SENSOR HAS SEEN THE HEAD.  A Point with no sensor is passed without waiting and nothing says the head is there:
                        // `headSeenAt` moves only where a sensor answered, and the track to a Point nobody saw is counted when the next sensor answers.  So
                        // too a sensor that already read occupied when its wait began, whose answer came before the head did (RSA61-A1).  The edge still joins
                        // the queue here, so the unmeasured escape and a train with no length hand it back as always.  Distance behind the head: where it was
                        // seen less where the edge ends, both from the path's start.
                        int edgeLeftEndsAt = pathRunTo;

                        pathRunTo += path.get(i).getLength();

                        if (seenHere) headSeenAt = pathRunTo - arrivingSquare(path.get(i));

                        waitingToClear.add(new int[] { i - 1, 0, edgeLeftEndsAt });

                        for (int[] waiting : waitingToClear)
                        {
                            waiting[1] = Math.max(0, headSeenAt - waiting[2]);
                        }

                        for (java.util.Iterator<int[]> pending = waitingToClear.iterator();
                            pending.hasNext();)
                        {
                            int[] waiting = pending.next();

                            // The tail is the train's length behind the head.  Unless this path has no lengths on it at all (the
                            // ordinary case where nobody has measured anything): then no distance can ever accumulate and holding
                            // would hold forever, so the railway falls back to "clear the moment the front passes".
                            //
                            // ONE STANDARD OF PROOF, because both answers are safety-relevant (WK-B1): `clearedEdges` is read by
                            // `getActiveAccs`, which `MarklinRoute.heldReason` consults to refuse a route that would set an accessory
                            // on an active path, and with atomicRoutes ON the lock is held for the whole run by design, so being in
                            // this set is the ONLY thing that drops an edge's protection.  An early clear lets a route throw a turnout
                            // on track the train is still standing on, so this asks the same rule as the unlock below.
                            //
                            // Early releases, which `unlockPath` reads back out of `releasedEarly` (GUI-A1), are what a
                            // configuration with `"atomicRoutes": false` does (VD10-C15, VD11-C1).
                            if (!tailHasProvablyPassed(pathIsUnmeasured, waiting[1],
                                lengthAtDispatch))
                            {
                                // Still under the train.  In atomic mode nothing was going to be
                                // unlocked anyway, so this only reports in the mode it can happen in.
                                if (control.isDebug() && !this.atomicRoutes)
                                {
                                    this.control.logf(
                                        "autolayout.infoNotUnlockingTraversedEdgeDueToTrainLength",
                                        lengthAtDispatch,
                                        waiting[1],
                                        path.get(waiting[0]).getName()
                                    );
                                }

                                continue;
                            }

                            // CLEARED: tailHasProvablyPassed said so above, the same proof unlocking
                            // uses below. There is no looser rule left (WK-B1) - a signal, or a
                            // turnout, behind the train may move.
                            Set<Edge> cleared = this.clearedEdges.get(loc);

                            if (cleared != null) cleared.add(path.get(waiting[0]));

                            if (this.atomicRoutes)
                            {
                                pending.remove();
                                continue;
                            }

                            pending.remove();

                            synchronized (this.activeLocomotives)
                            {
                                // EVERYTHING THIS EDGE TOOK, including its lock edges (OB-164): releasing only the
                                // edge would keep every throat it had locked until the whole path finished, so on a
                                // railway where routes cross, every throat the train had been through would refuse
                                // every route over it for the rest of the run.  The proof is tailHasProvablyPassed: a
                                // train clear of the edge is clear of the throat that edge needed.  setUnoccupied
                                // passes exactly one release to each lock edge, matching the one setOccupied took,
                                // which is why unlockPath must not do it again below.
                                path.get(waiting[0]).setUnoccupied();
                                path.get(waiting[0]).getStart().setLocomotive(null);
                                // the far end is not cleared: that unlocks the next edge early

                                // WHAT THIS RUN NO LONGER HOLDS, for `unlockPath` (GUI-A1).  A record of the release just
                                // made, behind the same proof - not a second place an edge is reported clear, which
                                // `clearedEdges` above is the only one of (WK-B1).
                                Set<Edge> released = this.releasedEarly.get(loc);

                                Edge givenBack = path.get(waiting[0]);

                                if (released != null) released.add(givenBack);
                            }

                            if (control.isDebug())
                            {
                                this.control.logf(
                                    "autolayout.infoUnlockingTraversedEdge",
                                    path.get(waiting[0]).getName()
                                );
                            }
                        }
                    }
                }
                
                // Route is in progress but not yet complete
                if (loc.hasCallback(CB_ROUTE_PROG))
                {
                    loc.getCallback(CB_ROUTE_PROG).accept(loc);
                }
            }
            else
            {           
                // Since we cannot interrupt the Locomotive thread, abort the route here if we need to
                if (isCurrentLayout())
                {        
                    // Destination is next - reduce speed and wait for occupied feedback
                    // Not after a stop ordered since dispatch (RSA-C1)
                    if (drive(loc, (int) Math.ceil((double) speed * Math.min(preArrivalSpeedReduction,
                        current.getSpeedMultiplier())), stopsAtDispatch))
                    {
                        this.control.logf(
                            "autolayout.infoPreArrivalSpeedForLocomotive",
                            loc.getSpeed(),
                            loc.getName()
                        );
                    }

                    if (loc.hasCallback(CB_PRE_ARRIVAL))
                    {
                        loc.getCallback(CB_PRE_ARRIVAL).accept(loc);
                    }
                    
                    long simEpoch = 0;

                    if (this.simulate)
                    {
                        loc.delay(this.getMinDelay(), this.getMaxDelay());
                        simEpoch = simAnnounce(current.getS88());
                    }
                    
                    this.updatePendingS88(loc, current.getS88());
                    

                    // With an advisory - see the intermediate points above
                    loc.waitForOccupiedFeedback(current.getS88(),
                        Locomotive.FEEDBACK_DURATION_THRESHOLD, Locomotive.FEEDBACK_ADVISORY_MS, this.retiredRailway);
                       
                    if (this.simulate)
                    {            
                        final long announcedEpoch = simEpoch;

                        new Thread( () -> 
                        {
                            loc.delay(this.getMinDelay(), this.getMaxDelay());
                            simClearBehind(current.getS88(), announcedEpoch);
                        }).start();
                    }

                    loc.setSpeed(0);
                    this.control.logf(
                        "autolayout.infoLocomotiveStopping",
                        loc.getName()
                    );
                }
            }  
            
            // Since we cannot interrupt the Locomotive thread, abort the route here if we need to
            if (!isCurrentLayout())
            {
                // Stop before abandoning the path: the layout that owned this run has been retired, and stopLocomotives() only
                // clears the dispatch flag and never commands anything, so returning here would leave a locomotive running between
                // stations with nothing left that will ever stop it.  Two ways in (RSA2-C8): a wait given up at the retirement
                // (RSA-B1), the usual one, where the train is stopped between two sensors by the reload's or Unload's Yes, which
                // the stop below repeats; and a sensor reached after the retirement, the train standing at a known milestone.
                loc.setSpeed(0);

                if (control.isDebug())
                {
                    this.control.logf(
                        "autolayout.debugLocomotivePathExecutionHaltedFromPriorLayoutVersion",
                        loc.getName()
                    );
                }

                return true;
            }

            this.control.logf(
                "autolayout.infoLocomotiveReachedMilestone",
                loc.getName(),
                current.toString()
            );

            synchronized (this.activeLocomotives)
            {
                List<Point> milestones = this.locomotiveMilestones.get(loc);

                // Null if the locomotive was deleted from the database while this path was running
                if (milestones != null && !recordedAlready)
                {
                    milestones.add(current);
                }

                // Fire callbacks
                for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
                {
                    if (callback != null)
                    {
                        fireCallback(callback, path, loc, true);

                        // Repaint other routes in non-atomic route mode
                        if (!this.atomicRoutes)
                        {
                            for (Locomotive otherLoc : this.getActiveLocomotives().keySet())
                            {
                                // Our loc is still active, so skip repainting it
                                if (!otherLoc.equals(loc))
                                {
                                    fireCallback(callback, this.activeLocomotives.get(otherLoc), otherLoc, true); 
                                }
                            }
                        }     
                    }
                }   
            }
            
            this.updatePendingS88(loc, null);
        }
        
        // Recorded HERE, at the point the train has actually stopped at its destination, rather than
        // when the route was chosen - LEAST_RECENTLY_VISITED ranks by where trains have BEEN.
        this.lastArrival.put(recencyKeyOf(path.get(path.size() - 1).getEnd()),
            System.currentTimeMillis());

        // Reverse at terminus station - AND ASK FIRST WHEN IT IS ONLY A MAY-REVERSE (DIR-A2): the last point is turned here, so
        // the policy must be consulted here too, or a hand-driven send whose DESTINATION is a reversing point turns the train
        // without a word (Adam: *"if the user decides to send a train to a 'may reverse' point, explicitly ask the user if the
        // train should change direction."*).  A TERMINUS IS STILL NOT A QUESTION; the message key
        // `...TerminusOrFinalReversingStation` names both cases.
        final Point arrived = path.get(path.size() - 1).getEnd();

          // AND THE RAILWAY REMEMBERS WHICH WAY IT CAME IN, the only moment anything knows it for certain: a train's
          // tail lies along the track it arrived by, and once it has come to rest nothing about the train says which
          // side that was (facing points the way it will LEAVE, and after a reversal the two agree while the tail is on
          // the opposite side).  Recorded BEFORE any reversal below, since turning the train round at the platform does
          // not move its tail.  THE SAME RULE THE WALK READS BACK (OB-182): the last leg is the one it came in on, so
          // the side it entered by is that edge's, which the build knows exactly and the geometry only approximates.
                arrived.setArrivedFrom(entrySideOf(path.get(path.size() - 1), arrived));

          // AND THE ROUTE ITSELF, so the tail can be followed past a junction (Adam, MT-335: "keep claiming along the road it
          // actually arrived on").
                arrived.setArrivedAlong(path);

        // AND ITS ENTRY GUARD GOES RED (FR-096; Adam: *"a signal that turns red after arrival at the final designation."*), here
        // where the journey's END is recorded: the path's last Point, reached by every tier's run (autonomy, a hand dispatch,
        // Return Home).  A square the train only passed is never `arrived`.  Nothing turns it green (*"The next route sets it
        // green, so that is out of scope."*).
        throwEntryGuard(arrived);

        // AND THE SQUARE IT LEFT NO LONGER HAS A TAIL ON IT: otherwise the track behind an empty platform stays blocked by a
        // train that drove away, until a rebuild.
        if (!path.isEmpty() && path.get(0).getStart() != null
            && path.get(0).getStart() != arrived)
        {
            path.get(0).getStart().setArrivedFrom(null);
        }

        // Set when the arrival declined a turn at a square that may turn, and acted on after the
        // path is unlocked (PRW-A1, PRV-B2).
        boolean restandAfterUnlocking = false;

        // Set when the arrival turned the train, and acted on after the path is unlocked as the declined turn's re-stand is
        // (RSA50-A3, RSA50-B1)
        boolean restandAfterTheTurn = false;

        // The same rule as the intermediate points, asked of the arrival (DIR-A2).  ASKED ONCE, in one expression, and the
        // `else` below records the same thing this branch did.  Not hoisted into a local: `testNonReversibleTrains.testTheRunAsksThatRule`
        // reads this file as a string and asserts this exact statement, a guard against the arrival deciding a reversal
        // WITHOUT the policy.
        if (turnsOnArrival(arrived, loc, reversals))
        {
            this.control.logf(
                "autolayout.infoLocomotiveReachedTerminusOrFinalReversingStation",
                loc.getName()
            );
            loc.delay(this.getMinDelay(), this.getMaxDelay());

            // NOT ON A RAILWAY RETIRED ON THE WAY (RLV11-C5, RLV12-C5).  The milestones' check stops a train whose railway
            // was retired - Unload, a reload - and this arrival comes after the last of them, and after the pause just
            // above, in which Unload can come.  The fold that retired the railway wrote the train as it stood; turned now,
            // the locomotive faces the other way from that record, and the turn is owed to a railway nothing reads.
            final boolean stillThisRailway = isCurrentLayout();

            if (stillThisRailway) loc.switchDirection().delay(1000); // pause to avoid network issues

            // ONLY A TURN THE OPERATOR ANSWERED (RSA51-B1): a plan - autonomy, Return Home, staging, a timetable recorded
            // from autonomy - turns a train on the turning copy and plans its next leg from that copy, so it stays there
            // until the railway is idle, as it always did; standing it on the drain's copy at once refused that leg
            restandAfterTheTurn = stillThisRailway && reversals != null && reversals != ALWAYS_REVERSE;

            // AND THE GRAPH IS TOLD, at the destination, which is the only place that knows.  See `reversedOnArrival` for why
            // neither of the two paths that follow a direction change can pick this one up.
            //
            // AND WHERE, NOT JUST WHO (REV9-A1): the Point records the side the train came in by (written above,
            // before the turn), so the window can write the answer ABSOLUTELY (see `reversedOnArrival`).  One
            // locomotive's arrivals all happen on its own driver thread, so nothing races for a given key.
            if (stillThisRailway && loc.getName() != null) this.reversedOnArrival.put(loc.getName(), arrived.getName());
        }
        else
        {
            if (loc.getName() != null)
            {
                // AND AN ARRIVAL THAT DID NOT TURN CLEARS IT: the record means "this train is standing where it turned and the
                // graph has not been told"; once it has run on, the graph is already right about it, and a stale record would be
                // applied to the square the train has since left or is standing on.
                this.reversedOnArrival.remove(loc.getName());
            }

            // AND IT MUST NOT BE LEFT STANDING ON THE COPY THAT EXPECTED IT TO TURN (PRW-A1).  A square trains MAY turn
            // at is two Points per arrival side: the plain copy, facing the way a train that drove in is pointing, and
            // the turning copy, facing the other way.  Both are reached by the same edge, so a path to that square can
            // end on either (the menu routinely offers the turning one).  A train that arrives there and declines the
            // turn faces one way while the Point it stands on says the other, and the next dispatch reads that Point's
            // outgoing edges, which are for a train pointing the other way: it would drive off its route (MT-368).
            //
            // The mirror case, a train that DOES turn, is re-stood by `AutonomySession.faceTheWayItCameIn`, which writes
            // the side it came in by and calls `moveOntoFacingCopy`; this is that repair for the other answer, done here
            // rather than in the drain because the harm is the NEXT dispatch.  The setup's own record still belongs to
            // the drain and to `captureFromLayout`.
            //
            // NOT HERE, THOUGH - after the path is unlocked (PRV-B2): `Point.setLocomotive` sweeps the train off every
            // other Point, and until `unlockPath` has run those are this run's remaining reservations; moving the train
            // first gives them up early.  The flag is set and the move is made below.
            restandAfterUnlocking = true;
        }
        
        if (loc.hasCallback(CB_ROUTE_END))
        {
            loc.getCallback(CB_ROUTE_END).accept(loc);
        }

        synchronized (this.activeLocomotives)
        {
            this.unlockPath(path, loc);

            this.activeLocomotives.remove(loc);
            this.locomotiveMilestones.remove(loc);
            this.clearedEdges.remove(loc);
            this.releasedEarly.remove(loc);

            // AND NOW THE TRAIN MAY BE MOVED ONTO THE COPY IT FACES (PRW-A1, PRV-B2): after the unlock, because
            // `Point.setLocomotive` sweeps the locomotive off every other Point and before this line those are still this run's
            // reservations; inside the monitor, because everything else that rearranges the railway at the end of a path is.
            if (restandAfterUnlocking) standOnTheCopyItDidNotTurnOn(arrived, loc, path);

            // AND A TRAIN THAT TURNED, ONTO THE COPY IT NOW FACES ON FROM (RSA50-A3, RSA50-B1), at the arrival rather than at
            // the window's idle drain, which a timetable played back never reaches.
            else if (restandAfterTheTurn) standOnTheCopyItFacesAfterTheTurn(arrived, loc);
                                  
            // Fire callbacks
            for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
            {
                if (callback != null)
                {
                    fireCallback(callback, path, loc, false);

                    // Repaint other routes in non-atomic route mode
                    if (!this.atomicRoutes)
                    {
                        for (Locomotive otherLoc : this.getActiveLocomotives().keySet())
                        {
                            fireCallback(callback, this.activeLocomotives.get(otherLoc), otherLoc, true); 
                        }
                    }                    
                }
            }
        }
        
        this.control.logf(
            "autolayout.infoLocomotiveFinishedPath",
            loc.getName(),
            this.pathToString(path)
        );

        // Track number of completed paths
        loc.incrementNumPaths();
        
        return true;
    }
        
    /**
     * Takes off the graph whatever an edit to this locomotive - its name, its address, its consist, changed from the
     * window or by the Central Station sync - has made conflict with it, through the train itself where it stands and
     * every standing multi-unit that drives it (BPV-A1, RLA-B1).
     *
     * An edit can only make a conflict ON THE GRAPH through a train that stands there and drives the edited one: the
     * An edit can only make a conflict ON THE GRAPH through a train that stands there and drives the edited one: the
     * edited train itself where it stands, and every standing multi-unit it is a member of.  The head is swept as
     * itself, which asks exactly what putting the head down again would: a member given the address of another
     * standing train would otherwise take nothing off, while every command to its head reached that train's decoder
     * and autonomy ran it as a train of its own (RLA-B1).  The head is never asked whether it is compatible with its
     * own member (`isLinkedTo`), which would take the head off its station on a rename of the member (MT-149); that
     * member, standing nowhere, is no conflict.
     *
     * Placing a train asks the sweep itself, before the train is put down, through `clearMultiUnitConflictsWith`.
     *
     * @param l the locomotive edited
     */
    public void sanitizeMultiUnits(Locomotive l)
    {
        if (l == null) return;

        if (this.getLocomotiveLocation(l) != null) clearMultiUnitConflictsWith(l);

        // Every standing multi-unit that drives it - a consist linked here, or one the Central Station holds.
        List<Locomotive> heads = new ArrayList<>();

        for (Point p : this.getPoints())
        {
            Locomotive head = p.getCurrentLocomotive();

            if (head != null && !head.equals(l) && !heads.contains(head)
                && (head.isLinkedTo(l) || head.getModelMultiUnitLocomotives().contains(l)))
            {
                heads.add(head);
            }
        }

        for (Locomotive head : heads)
        {
            if (this.getLocomotiveLocation(head) != null) clearMultiUnitConflictsWith(head);
        }
    }

    /**
     * Takes off the graph every train that cannot stand on it at the same time as this one - a member of its consist,
     * or one with an equivalent address - for a locomotive that is on the graph or about to be put there.
     *
     * @param l the locomotive
     */
    private void clearMultiUnitConflictsWith(Locomotive l)
    {
        if (l != null)
        {
            for (Point p : this.getPoints())
            {
                // NEVER THE LOCOMOTIVE THIS WAS ASKED ABOUT (MT-149): isSimultaneousMultiUnitCompatible ends in
                // `return !this.hasEquivalentAddress(l)`, so a locomotive compared with ITSELF is declared incompatible, and
                // asked about a train standing somewhere this sweep would take it off its square (the rename doors call it
                // straight after renameLoc; moveLocomotive calls it BEFORE putting the locomotive down, so it finds nothing
                // of its own).  A locomotive cannot conflict with itself.
                if (l.equals(p.getCurrentLocomotive())) continue;

                if (
                    // Is the locomotive present in any active multi-unit?
                    p.getCurrentLocomotive() != null && !p.getCurrentLocomotive().isSimultaneousMultiUnitCompatible(l)
                    // Or are we linked to the current locomotive?
                    || p.getCurrentLocomotive() != null && !l.isSimultaneousMultiUnitCompatible(p.getCurrentLocomotive())
                )
                {
                    // Through the bundles, like the other warnings in this class (PFX-C1).
                    this.control.logf("autolayout.warnRemovedConflictingLocomotive",
                        p.getCurrentLocomotive().getName(), p.getName(), l.getName());
                    p.setLocomotive(null);
                }          
            }
        }
    }
    
    /**
     * Requests to move a locomotive to a new station.  Called from the UI.
     * @param locomotive
     * @param targetPoint
     * @param purge if locomotive is null, do we also permanently remove it from the list to run?
     * @return 
     */
    synchronized public boolean moveLocomotive(String locomotive, String targetPoint, boolean purge)
    {
        return moveLocomotive(locomotive, targetPoint, purge, false);
    }

    /**
     * Claims the squares every rail shares from the platform back to where they part, when they do part (MT-477).
     *
     * Read from the platform end, as the walk reads an arriving rail: a place is claimed before its length is spent, so
     * a tail that just reaches the switch claims it.  Rails over the same squares - two copies of one neighbour - are
     * one road, not a fork, and an unmeasured rail says nothing, as the walk's measurement rule has it - one answered 0
     * throughout is measured (Adam): both leave the walk to its ordinary course.
     *
     * @param rails the rails arriving at the platform by the recorded side
     * @param loc the train
     * @param places where the claims are written
     * @param spent the squares already charged
     * @param remaining the train's length still to spend
     * @param reach filled with how far behind the head each claimed place begins, or null (OB-294)
     * @return true where the rails part and the shared squares were claimed - the walk stops there
     */
    private static boolean claimUpToWhereTheRailsPart(List<Edge> rails, Locomotive loc, Map<String, Locomotive> places,
        Set<String> spent, int remaining, Map<String, Integer> reach)
    {
        return StandingTrains.claimUpToWhereTheRailsPart(rails, loc, places, spent, remaining, reach);
    }

    /**
     * Whether a Point is a copy of a station square that trains may not arrive at - another copy of the same square is a
     * station (TDY3-A1), and none of those faces this one's way (TDY4-B1).
     *
     * @param point the Point
     * @return true for such a copy
     */
    public boolean isABarredCopyOfAStation(Point point)
    {
        if (point == null || point.isDestination()) return false;

        // AND NO STATION COPY FACES THIS ONE'S WAY (TDY4-B1, AUT4-B1): where one does - a turning copy at a square trains
        // may turn at - the train facing this way can stand where autonomy starts it, and `startableTwinOf` names it.
        if (startableTwinOf(point) != null) return false;

        for (Point other : this.points.values())
        {
            if (other != point && other.isDestination() && other.isSamePlaceAs(point)) return true;
        }

        return false;
    }

    /**
     * Whether every copy of this Point's square turns a train round - a compulsory turn, or a terminus (GUI4-C3).
     *
     * A square trains MAY turn at has plain copies beside its turning ones; one where they MUST has turning copies only.
     *
     * @param point a copy of the square
     * @return true where no copy lets a train through without turning it
     */
    public boolean turnsEveryTrainAt(Point point)
    {
        if (point == null) return false;

        for (Point other : this.points.values())
        {
            if (other.isSamePlaceAs(point) && !other.isTerminus() && !other.isReversing()) return false;
        }

        return true;
    }

    /**
     * A copy of the same square that is a station and faces the same way as this one, or null (TDY4-B1, AUT4-B1).
     *
     * @param point a copy trains may not arrive at
     * @return a copy a train facing the same way may stand on and be started from, or null
     */
    public Point startableTwinOf(Point point)
    {
        if (point == null || point.isDestination() || point.getCopyFacing() == null) return null;

        for (Point other : this.points.values())
        {
            if (other != point && other.isDestination() && other.isSamePlaceAs(point)
                && point.getCopyFacing().equals(other.getCopyFacing()))
            {
                return other;
            }
        }

        return null;
    }

    /**
     * Moves a locomotive to a station, as `moveLocomotive(String, String, boolean)` does - and, when
     * `evenOntoABarredCopy`, also onto a copy of a station square trains may not arrive at, where no station copy of
     * the square faces the same way (TDY3-A1, TDY4-B1, REG4-C3).
     *
     * A placement refuses a copy that is no station - nobody should put a train where autonomy cannot start it.  But a
     * train can be THERE: reversed on the throttle, or turned by the Facing menu, at a square whose other facing only such
     * a copy holds - the copy is its direction.  Putting the railway's trains back after a rebuild has to be able to
     * stand it there again, or the model keeps it where the setup last had it while it stands somewhere else.
     *
     * @param locomotive the locomotive
     * @param targetPoint the Point
     * @param purge as the three-argument form
     * @param evenOntoABarredCopy whether a copy of a station square trains may not arrive at is accepted
     * @return whether it was placed
     */
    synchronized public boolean moveLocomotive(String locomotive, String targetPoint, boolean purge,
        boolean evenOntoABarredCopy)
    {
        return moveLocomotive(locomotive, targetPoint, purge, evenOntoABarredCopy, false);
    }

    /**
     * The same, and - for putting the railway's trains back after a rebuild alone - onto a copy that is no station at
     * all (RSA17-A1).  A train stands where it stands: refused, the model would keep it where the setup last had it,
     * the square it stands on would read free, and another train could be sent through it.  Stood there, autonomy
     * refuses to start it, and says why.
     *
     * @param locomotive the locomotive
     * @param targetPoint the Point
     * @param purge as the three-argument form
     * @param evenOntoABarredCopy whether a copy of a station square trains may not arrive at is accepted
     * @param evenOntoANonStation whether any Point is accepted - the train is known to stand there
     * @return whether it was placed
     */
    synchronized public boolean moveLocomotive(String locomotive, String targetPoint, boolean purge,
        boolean evenOntoABarredCopy, boolean evenOntoANonStation)
    {
        boolean result = false;
        
        if (this.isRunning())
        {                
            this.control.logf(
                "autolayout.errorCannotEditWhileRunning"
            );
            return result;
        }
        
        if (locomotive != null && this.control.getLocByName(locomotive) != null)
        {
            Locomotive l = this.control.getLocByName(locomotive);
            
            // Add the locomotive to our list if needed
            if (!this.locomotivesToRun.contains(l))
            {
                this.locomotivesToRun.add(l);
            }

            // AND A SPEED, IF NOBODY HAS EVER GIVEN IT ONE (MT-233): parseAuto applies defaultLocSpeed as it loads, so a
            // locomotive placed by hand (the diagram paste, the right-click menu and the autonomy editor all arrive here)
            // needs the same rule or Start answers "Invalid speed specified".  Only when the default is usable:
            // setDefaultLocSpeed refuses anything outside 1 to 100, so a layout read from a file always has one, while a
            // Layout built by hand in a test may be at zero.
            if (l.getPreferredSpeed() < 1 && this.defaultLocSpeed >= 1)
            {
                l.setPreferredSpeed(this.defaultLocSpeed);

                this.control.logf("autolayout.warnLocomotiveDefaultSpeedApplied",
                    l.getName(), this.defaultLocSpeed);
            }
            
            // Resolved once and null-checked, as the locomotive == null branch below does: an unknown point name is reported.
            Point target = this.getPoint(targetPoint);

            if (target == null)
            {
                this.control.logf(
                    "autolayout.errorPointDoesNotExist",
                    targetPoint
                );
                return result;
            }

            // Can only place loc on a station - or put one back on a barred copy of one (TDY3-A1)
            if (!target.isDestination() && !(evenOntoABarredCopy && isABarredCopyOfAStation(target)) && !evenOntoANonStation)
            {
                this.control.logf(
                    "autolayout.errorPointIsNotStation",
                    targetPoint
                );
                return result;
            }
            
            // Can only place reversible trains on a terminus
            /* if (!this.getPoint(targetPoint).isTerminus() && !this.reversibleLocs.contains(locomotive))
            {
                this.control.log(locomotive + " is not reversible, but " + targetPoint + " is a terminus station.");
                return result;
            }*/
            
            // Removing from elsewhere is setLocomotive's own job, and it clears every copy, not the first: a square is several Points.
            
            // Ensure no multi-unit conflicts - asked before the train is put down, so the sweep itself, not the edit door
            // that asks only of a train already standing (BPV-A1).
            this.clearMultiUnitConflictsWith(l);

            // Placing by hand DISPLACES whoever was there, and deliberately: it is a person telling the model where a
            // train actually is, so the previous occupant is by definition no longer there.
            //
            // The whole SQUARE is displaced, not just the copy being written to: setLocomotive clears the train being
            // placed off everywhere else ("one locomotive, one place"), but a square is several Points, and a second
            // train on the other copy of an occupied platform would leave both standing on one piece of track.
            // Autonomy cannot reach that state (the block check refuses a route onto a platform whose sibling holds a
            // train); hand placement is the way in.  Only here, not inside setLocomotive: path reservation goes
            // through reserve(), which does not sweep, and a sweep on the shared setter collapses reservations and
            // strands trains.
            this.clearBlockExcept(target);

            // Set new location
            target.setLocomotive(l);

            // A locomotive placed by hand claims this station if it has no home and the station is
            // free of claims.  Placing an already-homed locomotive somewhere else does not re-home it.
            this.claimHome(l, target);

            // AND A HAND PLACEMENT SUPERSEDES A TURN THE WINDOW HAS NOT WRITTEN YET (REV9-A1): `reversedOnArrival` says "this
            // train is standing where the railway turned it and the graph has not been told", and somebody putting the train
            // down is a newer answer about both facts (OB-183's exception, one door further on).  A no-op on a REBUILD,
            // since `parseAuto` replaces this whole object.
            this.reversedOnArrival.remove(locomotive);

            result = true;
        }
        
        if (locomotive == null && this.getPoint(targetPoint) != null)
        {
            if (purge && this.getPoint(targetPoint).getCurrentLocomotive() != null)
            {
                this.locomotivesToRun.remove(this.getPoint(targetPoint).getCurrentLocomotive());
            }
            
            // Set new location
            this.getPoint(targetPoint).setLocomotive(null);
             
            result = true;
        }
        
        if (result)
        {
            // Fire callbacks to repaint UI
            for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
            {
                if (callback != null)
                {
                    fireCallback(callback, new LinkedList<>(this.getEdges()), locomotive == null ? null : this.control.getLocByName(locomotive), false);
                }
            }
        }
        
        return result; 
    }
    
    /**
     * THE TRACK NON-ATOMIC MODE COULD RELEASE UNDER A TRAIN - the atomic-routes gate's question (Adam).
     *
     * **Why this is the question and the editor's square count is not.**  Non-atomic mode is unsafe only through one
     * escape: `tailHasProvablyPassed` returns true the moment `pathIsUnmeasured`, that is, NO EDGE ANYWHERE ON THE PATH
     * is measured (a length, or every square answered 0; TDU-C6).  An edge's length is the sum of its squares, so a
     * railway with one unmeasured switch square still has a length on every edge: the escape can never fire on it, and
     * the missing square only makes `behind` under-count, so edges are held LONGER, the conservative direction.
     * Refusing something legal is the one thing Adam has asked not to happen.
     *
     * **By rail, not by edge**: a rail is two `Edge` objects, one per direction, so it is counted by the pair of
     * places it joins.
     *
     * **Only where a whole path could be unmeasured** (VD14-C6): a path always ENDS at a destination, so an
     * unmeasured rail with measured track between it and every destination can never be part of one.  What this
     * returns is the rails from whose far end an ACTIVE destination can be reached over unmeasured track alone (a
     * path may not run through or finish on a point that is switched off).  The premise is enforced where a path is
     * BUILT, not in `isPathClear` (VD15-C4, VD16-C1): `Layout.bfs` returns null unless `end.isDestination()`, and
     * `HomeStaging.firstClearRoute`, which does not use `bfs`, gates on `canRest` (`isDestination() && isActive()`).
     * If a path is ever allowed to finish anywhere else, this has to go back to counting every unmeasured edge.
     *
     * **An answered 0 is a measure** (Adam, TDU-C6: *"0 lengths count as measures, so non-atomic should be
     * allowed"*): track every square of which was answered 0 on purpose is not counted here; both ask
     * `Edge.isMeasured`.  A 0 nobody answered is still no length.
     *
     * SORTED, because `this.edges` is a `HashMap` (VD15-C2): the message names only the first three, which must not
     * change between runs of the same configuration.
     *
     * @return the rails, named by the two places each joins, in alphabetical order
     */
    public java.util.List<String> unmeasuredTrackThatCouldBeReleased()
    {
        java.util.List<Edge> unmeasured = new java.util.ArrayList<>();

        for (Edge edge : this.edges.values())
        {
            if (edge == null || edge.getStart() == null || edge.getEnd() == null) continue;

            if (edge.isMeasured()) continue;

            unmeasured.add(edge);
        }

        // FROM WHICH POINTS AN ACTIVE DESTINATION IS REACHABLE OVER UNMEASURED TRACK.  Walked
        // backwards from every destination a path could end at, over unmeasured edges only.
        Set<String> reaches = new LinkedHashSet<>();

        java.util.List<Point> frontier = new java.util.ArrayList<>();

        for (Point p : this.points.values())
        {
            if (p != null && p.isDestination() && p.isActive() && reaches.add(p.getName()))
            {
                frontier.add(p);
            }
        }

        while (!frontier.isEmpty())
        {
            Point here = frontier.remove(frontier.size() - 1);

            for (Edge edge : unmeasured)
            {
                if (edge.getEnd() != here) continue;

                Point back = edge.getStart();

                // Not through a point a path may not run through.
                if (back == null || !back.isActive()) continue;

                if (reaches.add(back.getName())) frontier.add(back);
            }
        }

        Set<String> rails = new LinkedHashSet<>();

        for (Edge edge : unmeasured)
        {
            // The rail can be released only if the rest of the way to a destination is unmeasured too.
            if (!reaches.contains(edge.getEnd().getName())) continue;

            String from = placeNameOf(edge.getStart());
            String to = placeNameOf(edge.getEnd());

            rails.add(from.compareTo(to) <= 0 ? from + " - " + to : to + " - " + from);
        }

        java.util.List<String> out = new java.util.ArrayList<>(rails);

        java.util.Collections.sort(out);

        return out;
    }

    /**
     * Every locomotive autonomy would run, with its train length - 0 where it has none (MT-533).
     *
     * Sorted, as `trainsWithNoLength` is.
     *
     * @return name to length, alphabetically
     */
    public java.util.Map<String, Integer> trainLengths()
    {
        java.util.Map<String, Integer> out = new java.util.TreeMap<>();

        for (Locomotive loc : this.getLocomotivesToRun())
        {
            if (loc == null) continue;

            out.put(loc.getName(), loc.getTrainLength() == null ? 0 : loc.getTrainLength());
        }

        return new java.util.LinkedHashMap<>(out);
    }

    /**
     * The locomotives autonomy would run that have no train length (VD14-B1).
     *
     * **The second half of what makes non-atomic mode unsafe.**  `tailHasProvablyPassed`'s other clause is
     * `behind >= trainLength`, and a locomotive's length is **0** until somebody sets it, so the first time an edge is asked
     * about, with `behind` still 0, `0 >= 0` is true and the edge is handed back with the whole train on it.  Measuring the
     * track does not prevent that; the tooltip says *"edge AND train lengths need to be set"*.
     *
     * Sorted, for the reason the rails are (VD15-C2): the run list is a concurrent SET and has no order.
     *
     * @return their names, alphabetically
     */
    public java.util.List<String> trainsWithNoLength()
    {
        java.util.List<String> out = new java.util.ArrayList<>();

        for (Locomotive loc : this.getLocomotivesToRun())
        {
            if (loc == null) continue;

            if (loc.getTrainLength() == null || loc.getTrainLength() <= 0) out.add(loc.getName());
        }

        java.util.Collections.sort(out);

        return out;
    }

    /**
     * Returns all edges in the graph
     *
     * NOT synchronized, and NOT a copy: these are live views read off-thread by the staging planner while the event
     * thread can be writing them (DR-B7).  Three reasons, in order of weight.
     *
     * FIRST, taking the monitor puts the event thread on the Layout monitor.  `getPoints` is called from five places
     * in the UI, and a dispatch holds that monitor across its per-command sleeps: the freeze IAR-B2 removed from
     * `getActiveAccs`.
     *
     * SECOND, it is a DEADLOCK rather than a freeze: `TrainControlUI.updateVisiblePoints` and `repaintAutoLocList` are
     * synchronized on the UI and call `getPoints()`, so they would take the Layout monitor while holding the UI's,
     * while `configureAndLockPath` holds the Layout monitor and reaches `MarklinAccessory.setSwitched` ->
     * `TrainControlUI.repaintSwitch`, which was synchronized on the UI until RSA67-C1.  `DiagramMonitorDriver` calls
     * `updateVisiblePoints` on the event thread every monitor tick during a run.
     *
     * The same freeze can arrive by another door (OB-192): `updateVisiblePoints` reached this monitor through
     * `refreshCoveredTrack` -> `AutonomySession.routesCoveredByStandingTrains` -> `edgesCoveredByStandingTrains`,
     * which is `synchronized` here, so the covered marks are computed on a worker.
     *
     * THIRD, these are called from inside this class's own loops (`deleteEdge` walks every edge to strip lock
     * references), so a copy per call turns a linear sweep into a quadratic one.
     *
     * The answer this class documents for its four UI-read collections is to make the maps concurrent, so a reader
     * gets a weakly-consistent iteration and no lock at all; that needs the null guards the note at those fields
     * warns about (ConcurrentHashMap rejects a null key, and `getPoint`/`getEdge` pass one straight through), so it is
     * filed rather than done.
     *
     * @return 
     */
    public Collection<Edge> getEdges()
    {
        return this.edges.values();   
    }
    
    /**
     * Returns all points in the graph
     *
     * Live and unsynchronized - see getEdges directly above for why it is not a copy.
     *
     * @return 
     */
    public Collection<Point> getPoints()
    {
        return this.points.values();
    }

    /**
     * Tells whoever is watching that the last locomotive thread has gone.
     * Nothing reads isRunning() on a timer: the interface re-reads it when a path ENDS, and a train that is idle when the
     * user presses Stop produces no path end, so the last thread could exit with the buttons still greyed and the Return
     * Home tooltip still saying trains are running.  The exit-path capture of placements and homes is gated on isRunning()
     * and skips silently, so closing the application in that window would lose the session's changes.
     *
     * An EMPTY edge list rather than null, because the registered callback walks it - and a null locomotive, which it
     * already guards for.
     */
    private void announceRunFinished()
    {
        for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
        {
            fireCallback(callback, new LinkedList<Edge>(), null, false);
        }
    }

    /**
     * Fires one repaint callback, and does not let it strand the railway.
     *
     * Every callback runs synchronously on the DRIVING thread and every registered one repaints something, so an exception
     * out of the UI (a graph view that has drifted from the layout, a null node) kills that thread: the train stops
     * mid-block with its track still locked, and every other train that needs that track is refused for the rest of the
     * session.  Every loop fires through this one door, so there is nowhere left to fire a callback un-guarded.
     *
     * @param callback the callback, or null for none
     * @param edges the path the callback is told about
     * @param loc the train
     * @param flag passed to the callback unchanged
     */
    private void fireCallback(TriFunction<List<Edge>, Locomotive, Boolean, Void> callback,
        List<Edge> edges, Locomotive loc, boolean flag)
    {
        if (callback == null) return;

        try
        {
            callback.apply(edges, loc, flag);
        }
        catch (Throwable e)
        {
            // Throwable, not Exception: the alternative to catching an Error is a train stopped mid-block with its track
            // locked for the rest of the session, and a NoClassDefFoundError out of a repaint is not a reason to strand a
            // railway.
            this.control.log(e instanceof Exception ? (Exception) e : new Exception(e));
        }
    }

    /**
     * Throws a station's entry-guard signals red, now that a train has arrived there at the end of its journey (FR-096).
     *
     * A COMMAND ON AN EVENT, where the exit guard beside it is an aspect derived from state: nothing is remembered and
     * nothing is undone, because the green half belongs to the next route that needs the signal.  Through
     * `Accessory.setState`, the door the exit guard and a route's own switching use.
     *
     * Quiet about failures, as the exit guard is: a signal that has gone from the layout, or a control station that is
     * not listening, must not stop a train arriving - and this runs on the driving thread.
     *
     * @param arrived the Point the journey ended on
     */
    void throwEntryGuard(Point arrived)
    {
        if (arrived == null || this.control == null) return;

        for (String accessory : arrived.getEntrySignals())
        {
            try
            {
                Accessory acc = this.control.getAccessoryByName(accessory);

                if (acc != null) acc.setState(Accessory.accessorySetting.RED);
            }
            catch (Exception e)
            {
                this.control.log(e);
            }
        }
    }

    /**
     * The copy of its destination a train will stand on (OB-314) - on its way, or while its route is set (RSA30-C3) - or
     * null for a train holding no path.
     *
     * A path to a square trains may turn at routinely ends on its TURNING copy, and a train that is not to turn there
     * is stood on the plain copy only once it arrives (`standOnTheCopyItDidNotTurnOn`) - so until then everything
     * reading the copy it holds would read it the wrong way round (the label at a platform showing the arrival facing
     * the wrong way).  This asks the arrival's own question - `turnsOnArrival`, with the answer the run was dispatched
     * with - and its own search for the plain copy, ahead of time.  Changes nothing.
     *
     * @param loc the train
     * @return the Point
     */
    public Point copyItWillStandOn(Locomotive loc)
    {
        List<Edge> path = loc == null ? null : this.pathHeldBy(loc);

        if (path == null || path.isEmpty()) return null;

        Point end = path.get(path.size() - 1).getEnd();

        java.util.Optional<ReversalPolicy> answer = this.runPolicies.get(loc);

        ReversalPolicy policy = answer == null ? null : answer.orElse(null);

        // TURNED, ONTO THE COPY IT WILL FACE ON FROM (RSA50-A3): where the arrival will stand it once it has turned - for a
        // turn the operator answered; a plan's turned train stays on the copy it arrives on (RSA51-B1)
        if (turnsOnArrival(end, loc, policy))
        {
            if (policy == null || policy == ALWAYS_REVERSE) return end;

            Point onto = copyAfterTheTurn(end, entrySideOf(path.get(path.size() - 1), end));

            return onto != null ? onto : end;
        }

        Point plain = plainSiblingFor(end, path);

        return plain != null ? plain : end;
    }

    /**
     * Whether a locomotive holds a point only because its locked path runs through it - neither where it set off from
     * nor where it is going (OB-315, OB-316).  Asked of the path being locked as well as the one being run, because the
     * reservation refreshes the signal before the run is counted.
     *
     * @param loc the locomotive holding the point
     * @param point the point
     * @return true for a point strictly inside its path
     */
    private boolean onlyPassing(Locomotive loc, Point point)
    {
        // THE CLAIM FIRST, through `pathHeldBy` as every reader of one train's path (RSA30-C1): the hand-over writes the
        // journey and then takes the claim away, so read the other way round a refresh straddling it finds the train in neither.
        List<Edge> path = this.pathHeldBy(loc);

        if (path == null || path.isEmpty()) return false;

        if (point == path.get(0).getStart() || point == path.get(path.size() - 1).getEnd()) return false;

        for (Edge e : path)
        {
            if (e.getEnd() == point) return true;
        }

        return false;
    }

    /**
     * Sets a station's protecting signal to match whether its platform is claimed.
     *
     * Red when a train is standing there or a locked path has reserved it, green when it is free - and
     * never turned red while an active path holds it green, as a path's first edge holds the guards of
     * the station it leaves (OB-340).
     * Derived rather than remembered, so nothing has to undo it: a path released after a failure
     * clears the reservation, and the signal follows on the same call.
     *
     * Quiet about failures.  A signal that has gone from the layout, or a control station that is not
     * listening, must not stop a train being placed - and this runs on the driving thread.
     *
     * @param point the Point whose occupancy just changed
     */
    void refreshProtectingSignal(Point point)
    {
        if (point == null || this.control == null) return;

        // Only while trains are actually being run: every occupancy change comes through here, and placing a train by hand is
        // one, so arranging the railway before a run would throw real signals (hardware moving in response to a setup
        // gesture).  isRunning rather than isAutoRunning: a train under a driving thread is being run whether or not the whole
        // layout is, and its arrival still has to protect its platform.
        if (!this.isRunning()) return;

        List<String> accessories = point.getProtectingSignals();

        if (accessories.isEmpty()) return;

        try
        {
            for (String accessory : accessories)
            {
                refreshOneSignal(accessory);
            }
        }
        catch (Exception e)
        {
            this.control.log(e);
        }
    }

    /**
     * A lock per signal, so that a protection refresh decides and commands as one step against a departure that sets the
     * signal GREEN (RSA67-C1, OB-340).
     *
     * A refresh need not hold the railway's monitor, so one that had read the holds could command RED after a departure
     * claimed its route and set a shared guard GREEN, leaving RED in front of the leaving train until the next change of
     * occupancy.  `refreshOneSignal` holds this from its read to its command; a departure holds it to command each
     * accessory of an edge it has taken (`configureEdge`) and the exit guards its first edge sets GREEN
     * (`configureAndLockPath`), once its claim and that edge make the hold readable.
     *
     * Taken last: under it only what a refresh took anyway (the edges' monitors, the accessory database's, and the
     * signal's command: the accessory's monitor, `Locomotive.accessoryMonitor`, and `NetworkProxy`'s as it sends; the
     * window's repaint is only posted, never waited for).  None of them is held while this is waited for, so a thread waits
     * here for one refresh or one command of the same signal, never for a sleep.
     */
    private final Map<Accessory, Object> signalLocks = new ConcurrentHashMap<>();

    /**
     * The lock of one signal, or of any accessory a path commands (RSA67-C1): see `signalLocks`.
     *
     * @param signal the accessory
     * @return its lock, the same object for every caller
     */
    private Object signalLock(Accessory signal)
    {
        return this.signalLocks.computeIfAbsent(signal, any -> new Object());
    }

    /**
     * Throws one protecting signal to whatever the platforms behind it now say: per SIGNAL rather than per point, because
     * that is the thing being commanded, a signal may protect more than one platform, and a platform may have more than one
     * signal.  The question asked is only ever "is anything this signal protects claimed", of every Point in the layout.
     *
     * @param accessory the signal to bring up to date
     */
    private void refreshOneSignal(String accessory)
    {
        try
        {
            Accessory acc = this.control.getAccessoryByName(accessory);

            if (acc == null) return;

            // ONE STEP, FROM THE READ TO THE COMMAND, against a departure that sets this signal GREEN once its hold is
            // readable: so a refresh either commands first, and the GREEN lands last, or reads the hold (RSA67-C1,
            // `signalLocks`)
            synchronized (this.signalLock(acc))
            {
                // Red if ANY platform this signal protects is claimed: asked of the SIGNAL rather than of the square, which covers
                // every copy of a square (they carry the same accessory) and two different stations paired to one signal, which
                // can only show one aspect.
                boolean claimed = false;

                for (Point other : this.points.values())
                {
                    if (!other.getProtectingSignals().contains(accessory)) continue;

                    Locomotive holding = other.getCurrentLocomotive();

                    // NOT BY A TRAIN ONLY PASSING THROUGH (OB-315, OB-316): a locked path reserves every station along
                    // it, so each one a train only passed would turn its exit guard red.  Its own start and its
                    // destination still count.
                    if (holding != null && !onlyPassing(holding, other))
                    {
                        claimed = true;
                        break;
                    }
                }

                // Only when it CHANGES.  This fires from every occupancy change, including each point a locked path reserves,
                // and configureAndLockPath holds the layout monitor across that whole loop, so a command per reservation would
                // put a burst of accessory traffic under the lock the event thread also needs.
                //
                // Remembered against the SIGNAL, not a Point: "claimed" is a fact about the whole square, so a memo per copy went
                // stale when a refresh on one copy saw the square claimed through another, and the next real arrival matched its
                // stale memo and sent no command.
                // NOT RED UNDER A PATH THAT HOLDS IT GREEN (OB-340): the train it lets go goes first
                if (claimed && Boolean.FALSE.equals(this.getActiveAccSettings().get(acc))) return;

                Boolean showing = this.signalAspects.get(accessory);

                // The memo AND the signal, because the memo is only the aspect protection last commanded and is not
                // the only thing that commands this accessory: TilePorts gives a SIGNAL tile a GREEN configuration
                // command, so a path configured across one drives it green through getConfigCommands, and protection
                // would agree with its own memo, send nothing and leave the signal GREEN over an occupied platform.  A
                // signal a path crosses and a signal protecting the destination are two different signals, but nothing
                // stops the two being paired to one accessory, so protection may not skip because of something it
                // REMEMBERS: whatever moved the signal, the next occupancy change is decided by looking.  isRed()
                // rather than a second memo: Accessory.switched is set optimistically by every caller, so it is the one
                // record that cannot go stale.
                if (showing != null && showing == claimed && acc.isRed() == claimed) return;

                this.signalAspects.put(accessory, claimed);

                // Through Accessory.setState, the same door the edge configuration uses, so a signal
                // thrown here is thrown exactly as one thrown by a route.
                acc.setState(claimed
                    ? Accessory.accessorySetting.RED : Accessory.accessorySetting.GREEN);
            }
        }
        catch (Exception e)
        {
            this.control.log(e);
        }
    }

    /**
     * Whether this accessory is a signal protecting a square a locomotive is standing on (SVN-B16).
     *
     * **The half of the route guard that `getActiveAccs` cannot see.**  That walks the config commands of active edges, and a
     * protecting signal is usually not one of them: it is driven separately, by occupancy.  A platform with a train at it can
     * have its signal turned green by something on no path, inviting a hand-driven train into an occupied platform.  Here
     * rather than in the route so the diagram's own accessory tile asks it too: one rule, asked by both.
     *
     * BY RESOLVING the names rather than comparing the strings: a configuration says "Signal 12" or "Switch 12" and the
     * accessory database is keyed by one of those; `getAccessoryByName` falls back across that difference.
     *
     * **NOT synchronized, deliberately** (`WK3-A1`, `DY3-A1`), for `getActiveAccs`'s reason.  It is read from the EVENT THREAD,
     * by the layout label's click handler, in the branch that runs while autonomy is going, which is when
     * `configureAndLockPath` holds this object's monitor across its per-command sleeps; taking the monitor here would freeze
     * the window, Stop included (`IAR-B2`, `getEdges`).  Nothing here needs it: it reads `points`, a live unsynchronised view,
     * and each Point's protecting-signal list; a list that grows while this walks it answers about the railway a moment ago,
     * as every other reader gets.
     *
     * @param accessory the accessory about to be commanded, or null
     * @return true when something is standing at a platform this signal protects
     */
    public boolean protectsAnOccupiedSquare(Accessory accessory)
    {
        if (accessory == null || this.control == null) return false;

        for (Point point : this.getPoints())
        {
            if (point.getCurrentLocomotive() != null && this.isProtectedBy(point, accessory)) return true;
        }

        return false;
    }

    /**
     * Whether a train STANDS at a platform this signal protects, rather than a locked path only having reserved it for a
     * train still on its way there (GSR-C2).
     *
     * `protectsAnOccupiedSquare` asks whether any platform the signal protects holds a locomotive, and a locked path holds
     * every point along it for its train (`Point.reserve`), its destination included, from the moment it is locked.  The
     * refusal is right either way - the platform is spoken for.  What it SAYS is not: "a train is standing" about a
     * platform nobody has reached sends the operator looking for a train that is somewhere else.  So the sentence is
     * chosen by this, and a platform is a standing train's only where `whereTheTrainIs` says that train is.
     *
     * Not synchronized, for `protectsAnOccupiedSquare`'s reason: it is asked where that is, the event thread among them.
     *
     * @param accessory the signal, or null
     * @return true when a train stands at a platform it protects; false when none does, every platform it protects that
     *         is held being held only by a reservation
     */
    public boolean protectsAStandingTrain(Accessory accessory)
    {
        if (accessory == null || this.control == null) return false;

        for (Point point : this.getPoints())
        {
            Locomotive holding = point.getCurrentLocomotive();

            if (holding == null || !this.isProtectedBy(point, accessory)) continue;

            if (point.isSamePlaceAs(this.whereTheTrainIs(holding))) return true;
        }

        return false;
    }

    /**
     * Whether this signal is one of a point's protecting signals, by its name or by the accessory a name resolves to - the
     * one reading `protectsAnOccupiedSquare` and `protectsAStandingTrain` both ask (GSR-C2).
     *
     * By resolving the names rather than comparing the strings alone, for the reason `protectsAnOccupiedSquare` gives: a
     * configuration says "Signal 12" or "Switch 12" and the accessory database is keyed by one of those.
     *
     * @param point the platform
     * @param accessory the signal
     * @return true when it protects the point
     */
    private boolean isProtectedBy(Point point, Accessory accessory)
    {
        for (String name : point.getProtectingSignals())
        {
            if (name == null) continue;

            if (name.equals(accessory.getName())) return true;

            Accessory named = this.control.getAccessoryByName(name);

            if (named != null && named.equals(accessory)) return true;
        }

        return false;
    }

    /**
     * Whether commanding this accessory that way would take protection off an occupied platform.
     *
     * **The aspect is half the rule** (`WK3-B1`): a protecting signal's only harmful command is the one that turns
     * protection OFF.  Setting it to danger does what the protection mechanism itself would do, and refusing that is "pure
     * over-strictness" (see `MarklinRoute.heldReason`).
     *
     * **The caller supplies the direction rather than this reading it**, because the three doors know it in three different
     * ways: a diagram tile TOGGLES, so its green direction is `!isStraight()`; the switch keyboard SETS an absolute state, so
     * reading the accessory's current aspect would answer about where it is, not where it is being sent; a route knows it
     * from the command it holds.  Only the question that follows is shared, so that three surfaces cannot ask three
     * versions of it.
     *
     * @param accessory what is being commanded, ignored when null
     * @param commandingGreen whether the command about to be sent is the one that clears protection
     * @return true when a person should be asked first
     */
    public boolean clearsProtection(Accessory accessory, boolean commandingGreen)
    {
        return accessory != null && commandingGreen && protectsAnOccupiedSquare(accessory);
    }

    /**
     * Whether a station autonomy may choose takes this train because the measured route in holds it (FR-087).
     *
     * The train is accepted if the measured track of the route it drives in on, counted back from the station without
     * a gap, holds it: its tail lies on that route, a driven train's road is kept (WK7-B1), and the track under it is
     * claimed.  A leg nobody measured ends the count, one answered 0 throughout is counted and adds nothing, so a tail
     * that would reach unmeasured track is still refused.
     *
     * **The reason it is a method rather than a line**: at a station autonomy may choose, a train is PASSING: it
     * stands across the points until it is given its next route, and the cost is other trains waiting rather than a
     * collision, since a standing tail claims the places it lies on (OB-207).  At a parking berth the train is
     * STAYING and the road would be shut all evening, which `whyABerthCannotHoldIt` refuses.
     *
     * **It only ever ALLOWS**, and only where the room rule has already refused.  The bound is the measured route in
     * (`measuredRouteIn`), so a train longer than that is still refused: its tail would lie back over track nothing has
     * measured.
     *
     * **Unbundled from `whyTooLongForThisRoute`** (PRW-B2): the staging planner needs this answer without the station's
     * stated capacity and the room test at every square on the way, which made it stricter than the runtime it plans for.
     *
     * @param path the route, in order, ending at the square the train comes to rest on
     * @param loc the train
     * @return whether the approach itself is long enough to hold it at a station autonomy may choose
     */
    public static boolean theApproachItselfHoldsIt(List<Edge> path, Locomotive loc)
    {
        if (path == null || path.isEmpty() || loc == null) return false;

        if (loc.getTrainLength() == null || loc.getTrainLength() <= 0) return false;

        Point ending = path.get(path.size() - 1).getEnd();

        // `isAutoDestination` is the flag his words name - "Can Be Chosen In Full Autonomy".
        if (ending == null || !ending.isAutoDestination()) return false;

        return loc.getTrainLength() <= measuredRouteIn(path);
    }

    /**
     * How much measured track the route in holds, counted back from where it ends (FR-087).
     *
     * Leg by leg while each is measured: a leg nobody measured ends the count; one answered 0 on purpose throughout is
     * measured track of no length and is counted, adding nothing (Adam: *"we can't possibly have positive lengths
     * everywhere because the tracks just aren't that long."*), as the walk that claims a standing train's tail walks on
     * over it, so what this admits is claimed (`Edge.isMeasured`; ADA-A1).  The count never goes back past a square
     * the train turns at: the berth is *"measured back to whichever of the last switch and the reversal is met
     * first"* (Adam), and a train that changed direction does not lie back over the track it drove before the turn.
     * The same test `measuredRoomAtTheEndOf` makes, so the room rule and this allowance stop at the same square.
     *
     * One number for two readers (MFR-C6): `theApproachItselfHoldsIt` admits a train no longer than it, and the refusal in
     * `whyTooLongForThisRoute` quotes it.
     *
     * @param path the route, in order, ending where the train comes to rest
     * @return the measured units, 0 for none
     */
    public static int measuredRouteIn(List<Edge> path)
    {
        if (path == null) return 0;

        int held = 0;

        for (int i = path.size() - 1; i >= 0; i--)
        {
            if (i < path.size() - 1 && path.get(i).getEnd() != null && path.get(i).getEnd().isReversing()) break;

            int leg = path.get(i).getLength();

            if (leg <= 0 && !path.get(i).isMeasured()) break;

            held += Math.max(0, leg);
        }

        return held;
    }

    /**
     * Why a PARKING BERTH cannot hold this train: it would lie across somebody else's road.
     *
     * Adam's ruling, the other half of the relaxation in `whyTooLongForThisRoute`: *"make a rule that parking berths
     * cant block any other edges, but not make that check for active stations."*  A train at a platform is passing and
     * may stand across the points; a train at a berth is staying, and a berth is not worth a road (SVX-C1: on the
     * author's railway a three-unit train at TunnelLongPark would lie over the only two roads to the lower level and
     * make 90 ordered pairs of stations unreachable).
     *
     * **Which places the train would claim is the same arithmetic the tail walk uses**: spend the train's length
     * across the approach's places from the end it comes in by, claiming each before spending it, the berth's own
     * square first (OB-278: *"the 2 length tile with the s88 consumes 2 units of the train"*).  The two walks are
     * written to agree and are not one piece of code (SEV-B1, SVX-B1), so a change to one must check the other.
     *
     * **Its own way in and out does not count.**  Every road that starts or ends at the berth's own square is blocked
     * by the train being there at all, whichever copy of a split square it is recorded on (`Point.isSamePlaceAs`).
     * **Shared metal only**, told from an FR-001 restriction by symmetry, as the covered-track sweep in `isPathClear`
     * tells them apart.
     *
     * Silent on an edge the build did not describe in places (a hand-written configuration or an older one), so an
     * old file keeps the answer from the room rule alone.  Pure, like `whyTooLongForThisRoute`, so the staging planner
     * can ask the same question.
     *
     * @param path the route, in order
     * @param loc the train
     * @return why the berth cannot hold it, or null when it can or when the question does not arise
     */
    public static String whyABerthCannotHoldIt(List<Edge> path, Locomotive loc)
    {
        return TrainFit.whyABerthCannotHoldIt(path, loc);
    }

    /**
     * Why this train would run into its own tail on this route, or null when it would not (OB-294).
     *
     * The train's body as it stands - walked by `walkOneTail`, the walk that claims the track behind every standing
     * train, so the body this judges is the one the rest of the railway sees - and then `whyItWouldMeetItsOwnTail` below.
     *
     * Not `synchronized`, like `isPathClear`, which asks it: the manual doors ask it on the event thread, and the event
     * thread taking this monitor is how OB-192 froze the window (see `getEdges`).
     *
     * @param path the route, in order, starting where the train stands
     * @param loc the train
     * @return the refusal, already translated, or null
     */
    public String whyItWouldMeetItsOwnTail(List<Edge> path, Locomotive loc)
    {
        if (path == null || path.isEmpty() || loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0)
        {
            return null;
        }

        Point start = path.get(0).getStart();

        Map<String, Integer> reach = new LinkedHashMap<>();

        // Where it stands: the route's first square, on whichever copy of it holds the train.
        for (Point standing : this.points.values())
        {
            if (start == null || !loc.equals(standing.getCurrentLocomotive())) continue;

            if (standing != start && !standing.isSamePlaceAs(start)) continue;

            reach = bodyOfATrainAt(standing, loc, null);

            break;
        }

        return whyItWouldMeetItsOwnTail(path, loc, reach);
    }

    /**
     * Where a train's body lies as it stands at a Point, with how far behind the head each place begins (OB-294).
     *
     * The road it came along where one is given - Return Home's plan knows the road of a train it has moved, as
     * `edgesATailWouldCover` takes it - and otherwise what the Point records, as every standing train is walked.
     *
     * @param standing where the train stands
     * @param loc the train
     * @param road the route it came along in a plan, or null for what the Point records
     * @return each place its body lies on, and how far behind the head that place begins
     */
    Map<String, Integer> bodyOfATrainAt(Point standing, Locomotive loc, List<Edge> road)
    {
        Map<String, Integer> reach = new LinkedHashMap<>();

        if (standing == null || loc == null || loc.getTrainLength() == null || loc.getTrainLength() <= 0) return reach;

        if (road != null && !road.isEmpty())
        {
            walkOneTail(standing, loc, entrySideOf(road.get(road.size() - 1), standing), road,
                new LinkedHashMap<Edge, Locomotive>(), new LinkedHashMap<String, Locomotive>(), reach);
        }
        else
        {
            walkOneTail(standing, loc, standing.getArrivedFrom(), standing.getArrivedAlong(),
                new LinkedHashMap<Edge, Locomotive>(), new LinkedHashMap<String, Locomotive>(), reach);
        }

        return reach;
    }

    /**
     * Why this train would run into its own tail on this route, given where its body lies before it moves (OB-294).
     *
     * Adam: *"make sure the model factors in whether the train will clear the area before it crosses over"*, and
     * *"the check should pass if the train would be gone (i.e. if that one was only length 4, for example)."*
     *
     * **A train never blocks itself** elsewhere (behaviour.md 5c), rightly while the route leads away: its tail is
     * behind it, and pulling forward off it is how a train leaves a berth.  A route that comes back round a loop to
     * track the body still lies on is the case that does not hold.
     *
     * **The rule: the head may come back to a place only once the tail has left it.**  Distances are measured along
     * the journey from where the head stands, and the tail is always the train's length behind the head.  A place the
     * body lies on at the start is left when the tail passes the end of it nearer the head, which is `reach` behind
     * the start; a place the route has run over is left when the tail passes the end the head went out by.  The track
     * run between leaving a place and coming back to it is the longest train that clears it in time, so a longer train
     * is refused and the refusal names the figure.  One exactly that long is clear as the head arrives, which is how
     * every length rule here reads a fit.
     *
     * **Only measured track binds**, as for every length rule here (*"only apply if lengths are specified"*): a
     * square with no length counts nothing, and a return with nothing measured on the way round is not judged.  THE
     * WAY ROUND is the route the head drives between leaving the place and coming back to it (TDA-B1), MEASURED as
     * every length rule reads it: a length on it, or every place on it answered 0 on purpose (OB-300, RLA-C6), asked
     * place by place (`measuredThroughout`) since a way round begins and ends inside a leg.  Unmeasured squares within
     * a measured way round make it shorter than it is, which refuses rather than permits, so the refusal says how many
     * things Mass Assign Lengths would ask a length for on the way round (OB-297, ADA-C1; the note's list and the
     * editor's are one list, TDA2-C1).
     *
     * **The tightest return is the one named** (TDA-C1): a route that comes up behind the body meets it more than
     * once, and a later return can allow less than an earlier one, so every return is asked and the smallest figure is
     * the answer.
     *
     * **After a turn the body is ahead of the train**, and moves with it: a train that turns, at a square it may turn
     * at on the way or where it stands before it sets off, drives back over the track its body lies on, which is no
     * collision.  So the question starts again after every turn with nothing behind; the room rule in
     * `whyTooLongForThisRoute` keeps a turning train on the track it came in by.  By place, the relation the tail
     * claims use: the two roads through a crossing share their square and meet here, and an overpass's two levels do
     * not.
     *
     * @param path the route, in order, starting where the train stands
     * @param loc the train
     * @param reach where its body lies before it moves: each place its tail claims, and how far behind the head that
     *        place begins - empty when nothing is known
     * @return the refusal, already translated, or null
     */
    static String whyItWouldMeetItsOwnTail(List<Edge> path, Locomotive loc, Map<String, Integer> reach)
    {
        return TrainFit.whyItWouldMeetItsOwnTail(path, loc, reach);
    }

    /**
     * Why this train does not fit anywhere this path would put it, or null when it does (MT-262).
     *
     * **Where the train comes to rest: the destination, and any square it turns round at** (Adam, MT-333) - not a square
     * it only drives past.  A train already standing still blocks other roads by its tail (`walkStandingTrains`), which is
     * a different mechanism and not asked here.
     *
     * **ONE predicate for "it does not fit", so that no door writes a second copy.**  There are two halves, the
     * capacity somebody typed on the station and the track actually leading into it, and a train can clear one and not
     * the other.  `isPathClear` asks this; the manual send doors and the why-not-moving window ask it directly because
     * they want the SENTENCE, not the yes-or-no.
     *
     * **The message carries both numbers**, the room found and the length of the train.
     *
     * @param path the route, in order
     * @param loc the train
     * @return the refusal, already translated, or null when the train fits
     */

    public static String whyTooLongForThisRoute(List<Edge> path, Locomotive loc)
    {
        return TrainFit.whyTooLongForThisRoute(path, loc);
    }

    /**
     * The measured room a train has to stand in at the end of this path (TCX-A2, MT-262).
     *
     * **Asked of PREFIXES as well as of whole routes** (Adam): "the end of this path" is meant literally, so the
     * first three edges of a nine-edge route answer about the square those three arrive at.  That is how
     * `whyTooLongForThisRoute` asks at a square the train turns round at as well as at the destination, with no second
     * walk to disagree with this one.
     *
     * **Pure**, which is what lets the staging planner ask it: every other rule in `isPathClear` reads live feedback, and
     * `HomeStaging` has to re-implement them for hypothetical futures, but this one reads only the path and the locomotive.
     *
     * **Not fenced on terminus** (MT-262): a train comes to rest with its head at the destination sensor whatever kind of
     * station it is, and its tail lies back over the run either way.
     *
     * Null rather than a number where the question does not arise: the train has no recorded length, or nothing on the
     * run in is measured.  **Unmeasured is unknown, not zero**: answering "I do not know" with "it is nothing" refuses
     * trains that fit.  The two known unsoundnesses in what it counts are recorded at the call site in `isPathClear`; they
     * are about WHICH segments, not about who may ask.
     *
     * @param path the route, in order
     * @param loc the train
     * @return the total measured length behind the berth, or null when the question does not arise
     */
    public static Integer measuredRoomAtTheEndOf(List<Edge> path, Locomotive loc)
    {
        return TrainFit.measuredRoomAtTheEndOf(path, loc);
    }

    /**
     * The aspect each protecting signal was last commanded to show.
     *
     * Keyed by the accessory, because that is the thing being commanded.  One signal, one aspect,
     * however many platforms are paired to it and however many Points a platform is emitted as.
     */
    private final java.util.Map<String, Boolean> signalAspects =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Brings every protecting signal into line with where the trains actually are: clears the `signalAspects` memo and asks
     * every signal again from current occupancy.
     *
     * **NOTHING IN THE APPLICATION CALLS THIS, AND NOTHING SHOULD (Adam, OB-166):** "Signals should only be touched when a
     * route activates."  The tests that drive it directly (`testAutoLayout`, `testBothProtectingSignalsAreThrown`) exist to
     * prove that no production door calls it; putting one back fails them.  It stays public for them, and because a railway
     * whose signals have drifted may one day want a button.  Signals are otherwise refreshed one at a time, as occupancy
     * changes.
     *
     * A sweep costs one command per signal.  It is also the only thing that clears `signalAspects`, so the memo otherwise
     * lives for the session; harmless, because the skip test also asks `acc.isRed() == claimed`, a fact about the accessory
     * rather than the memo.
     */
    public void refreshAllProtectingSignals()
    {
        this.signalAspects.clear();

        if (this.control == null) return;

        java.util.Set<String> seen = new LinkedHashSet<>();

        for (Point point : this.points.values())
        {
            seen.addAll(point.getProtectingSignals());
        }

        for (String accessory : seen)
        {
            refreshOneSignal(accessory);
        }
    }

    /**
     * Whoever is standing on a block, ignoring one Point of it.
     *
     * Walked rather than indexed: the points map is small, this is asked while choosing a path rather
     * than while driving, and an index would be a second structure to keep in step with renames and
     * rebuilds - which is the class of bug this area keeps producing.
     *
     * @param block the shared identity
     * @param except the Point asking, whose own occupancy the caller has already read
     * @return the locomotive on another copy of that square, or null
     */
    Locomotive locomotiveInBlock(String block, Point except)
    {
        if (block == null) return null;

        for (Point other : this.points.values())
        {
            if (other == except) continue;

            if (!block.equals(other.getBlock())) continue;

            Locomotive there = other.getCurrentLocomotive();

            if (there != null) return there;
        }

        return null;
    }

    /**
     * Whether this point is somewhere autonomy may send a train at all: a destination, in service, one autonomy is allowed
     * to choose, and a plain copy rather than a turning one (DR-B3).
     *
     * This is the part that is genuinely one question, written once because the clauses had drifted when they were written
     * out at three sites.  Everything per-locomotive stays at the call.  Occupancy and locking are deliberately NOT
     * included: `canReachAnyDestination` asks what the railway allows, not what is free this second.
     *
     * @param end the point being considered
     * @return true when autonomy may end a journey there
     */
    public boolean isSendableDestination(Point end)
    {
        return end != null && end.isDestination() && end.isActive()
            && end.isAutoDestination() && !end.isReversing();
    }
    /**
     * Whether a train standing here could be sent anywhere at all.
     *
     * Not "does this point have an outgoing edge": a copy of a split square can have somewhere to go and still have nowhere
     * to be SENT, because autonomy only ever dispatches a train to a destination, and a train placed there never moves with
     * nothing to say why.  A plain walk of the edges, not a path search per candidate; occupancy and locking are
     * deliberately NOT considered.
     *
     * @param from where the train would stand
     * @return true when some destination is reachable from there
     */
    public boolean canReachAnyDestination(Point from)
    {
        if (from == null) return false;

        Set<Point> seen = new HashSet<>();
        java.util.ArrayDeque<Point> queue = new java.util.ArrayDeque<>();

        seen.add(from);
        queue.add(from);

        while (!queue.isEmpty())
        {
            Point at = queue.poll();

            List<Edge> away = this.getNeighbors(at);

            if (away == null) continue;

            for (Edge e : away)
            {
                Point end = e.getEnd();

                if (end == null || !seen.add(end)) continue;

                // Somewhere a train can actually be sent, and not the square it started on.  The shared clauses only: this
                // method takes no locomotive, so the terminus and exclusion questions cannot be asked here (DR-B3).
                if (!end.equals(from) && isSendableDestination(end))
                {
                    return true;
                }

                queue.add(end);
            }
        }

        return false;
    }

    /**
     * Takes every OTHER locomotive off the square this Point belongs to: the companion of clearLocomotiveExcept, which keeps
     * a train from being in two places while this keeps two trains from being in one.  A square emitted as several Points
     * is one piece of rail.
     *
     * Called from hand placement and the file load only: autonomy never needs it (a route onto a platform whose sibling
     * copy holds a train is refused by the block check), and calling it from the shared setter would sweep away the
     * reservations a locked path has made.
     *
     * @param claiming the Point being placed on, which keeps whatever is put there
     */
    void clearBlockExcept(Point claiming)
    {
        if (claiming == null) return;

        String block = claiming.getBlock();

        if (block == null) return;

        for (Point other : this.points.values())
        {
            if (other == claiming) continue;

            if (!block.equals(other.getBlock())) continue;

            Locomotive displaced = other.getCurrentLocomotive();

            if (displaced == null) continue;

            other.setLocomotive(null);

            // Said out loud, as sanitizeMultiUnits says its own evictions: a train taken off here may now be nowhere on the
            // graph (it keeps its run-list place and home claim, but staging skips it and pickPath never dispatches it), which
            // must not happen in silence, since the copy written to is not always the copy the user was looking at.
            this.control.logf("autolayout.infoLocomotiveDisplacedFromSquare",
                displaced.getName(), claiming.getName());
        }
    }

    /**
     * Takes a locomotive off every Point except the one that is claiming it: the other half of the rule
     * `Point.setLocomotive` enforces.  Here because only the layout knows the other Points, and every copy is cleared, since
     * a square is several Points and the train may have been on several at once.
     *
     * Not synchronized: setLocomotive holds the Point's own monitor, and taking the layout's here would order the two locks
     * against every other path that takes them the other way round.  The map it walks only changes structurally in graph
     * construction.
     *
     * @param l the locomotive being placed
     * @param claiming the Point placing it, which keeps it
     */
    void clearLocomotiveExcept(Locomotive l, Point claiming)
    {
        if (l == null) return;

        for (Point other : this.points.values())
        {
            if (other == claiming) continue;

            if (l.equals(other.getCurrentLocomotive())) other.setLocomotive(null);
        }
    }
    
    /**
     * Checks if the specified callback has been defined
     * @param callbackName 
     * @return  
     */
    public boolean hasCallback(String callbackName)
    {
        return this.callbacks.containsKey(callbackName);
    }
    
    /**
     * Returns the requested callback function
     * @param callbackName
     * @return 
     */
    public TriFunction<List<Edge>, Locomotive, Boolean, Void> getCallback(String callbackName)
    {
        return this.callbacks.get(callbackName);
    }
    
    /**
     * Sets a new callback function for a given name
     * @param callbackName
     * @param callback 
     */
    public void setCallback(String callbackName, TriFunction<List<Edge>, Locomotive, Boolean, Void> callback)
    {
        this.callbacks.put(callbackName, callback);
    }
    
    /**
     * Lambda with 3 arguments
     * @param <T>
     * @param <U>
     * @param <V>
     * @param <R> 
     */
    @FunctionalInterface
    public interface TriFunction<T, U, V, R>
    {
        public R apply(T t, U u, V v);
    }
    
    public int getMinDelay()
    {
        return minDelay;
    }

    public void setMinDelay(int minDelay) throws Exception
    {
        if (minDelay > this.maxDelay || minDelay < 0)
        {
            throw new IllegalArgumentException(
                I18n.f("autolayout.errorMinDelayRange")
            );
        }
        
        this.minDelay = minDelay;
    }

    public int getMaxDelay()
    {
        return maxDelay;
    }

    public void setMaxDelay(int maxDelay) throws Exception
    {
        if (maxDelay < this.minDelay || maxDelay < 0)
        {
            throw new IllegalArgumentException(
                I18n.f("autolayout.errorMaxDelayRange")
            );
        }
        
        this.maxDelay = maxDelay;
    }

    public int getDefaultLocSpeed()
    {
        return defaultLocSpeed;
    }

    public void setDefaultLocSpeed(int defaultLocSpeed) throws Exception
    {
        if (defaultLocSpeed <= 0 || defaultLocSpeed > 100)
        {
            throw new IllegalArgumentException(
                I18n.f("autolayout.errorDefaultLocSpeedRange")
            );
        }
        
        this.defaultLocSpeed = defaultLocSpeed;
    }

    public boolean isTurnOffFunctionsOnArrival()
    {
        return turnOffFunctionsOnArrival;
    }

    public void setTurnOffFunctionsOnArrival(boolean turnOffFunctionsOnArrival)
    {
        this.turnOffFunctionsOnArrival = turnOffFunctionsOnArrival;
    }

    public boolean isTurnOnFunctionsOnDeparture()
    {
        return turnOnFunctionsOnDeparture;
    }

    public void setTurnOnFunctionsOnDeparture(boolean turnOnFunctionsOnDeparture)
    {
        this.turnOnFunctionsOnDeparture = turnOnFunctionsOnDeparture;
    }
     
    public boolean isAtomicRoutes()
    {
        return atomicRoutes;
    }

    /**
     * Turns the early release of passed track on or off.
     *
     * **A run that the setting changes under is unlocked by what it did** (GUI-A1): `unlockPath` gives track back by
     * whether the run gave any back early (the edges `releasedEarly` records) and, where it gave none, by the setting at its
     * end (DCN4-C1), so neither direction gives an edge back twice.  True-to-false can still leave one edge held (GUI2-C1):
     * one the tail cleared while the run was atomic is dropped from the wait list unreleased, and if another train then
     * takes its end Point the unlock's careful road leaves it - the cost `SVN-C17` describes, unreachable while the
     * checkbox refuses a change mid-run.
     *
     * The interface refuses the checkbox while `isAutoLayoutRunning()` (SVN-C17) as a statement about what an operator
     * should be able to do mid-run, not about safety.  Not locked here: taking the layout monitor would put a click
     * behind a whole route being thrown, the freeze `getActiveAccs` documents.
     *
     * @param atomicRoutes whether a route holds all its track until it finishes
     */
    public void setAtomicRoutes(boolean atomicRoutes)
    {
        this.atomicRoutes = atomicRoutes;
    }
    
    /**
     * Removes the timetable entry the operator chose - the one they were shown, and only that one (OB-260).
     *
     * Under this railway's lock, which capture appends under from locomotive threads: the list is a plain one, and a
     * remove made beside an append could corrupt it.  And asked of the entry as well as the row, because the confirmation
     * before it can stay open as long as the operator likes.
     *
     * @param index the row chosen
     * @param shown the entry that row held when the operator was asked
     * @return whether it was removed
     */
    synchronized public boolean removeTimetableEntry(int index, TimetablePath shown)
    {
        if (shown == null || index < 0 || index >= this.timetable.size() || this.timetable.get(index) != shown) return false;

        this.timetable.remove(index);

        return true;
    }

    /**
     * Empties the timetable, for Clear Timetable (RSA48-C1).
     *
     * Under this railway's lock, as the remove is (OB-260): capture appends under it from locomotive threads, and the list
     * is a plain one.  Not called clearTimetable, which is the window's door: the monitor guard reads callers by name.
     */
    synchronized public void emptyTimetable()
    {
        this.setTimetable(new java.util.LinkedList<>());
    }

    /**
     * Replaces the timetable with the one passed
     * Used when loading from JSON
     * @param lst 
     */
    public void setTimetable(List<TimetablePath> lst)
    {
        this.timetable.clear();
        this.timetable.addAll(lst);

        // Overlapping execution is the normal behaviour; only a staging plan opts out, and it does so
        // after calling this
        this.timetableSequential = false;

        // And nothing is on loan: this is the timetable, or the one a plan hands back (RLV9-B1)
        this.timetableOnLoan = null;
    }

    /**
     * Reports one disagreement between the staging planner and the runtime's own path check.
     * @param loc
     * @param destination
     * @param runtimeAllowsIt - true when the runtime would allow the move and the planner would not
     */
    void logStagingAudit(String loc, String destination, boolean runtimeAllowsIt)
    {
        this.control.logf(
            runtimeAllowsIt ? "autolayout.warnStagingPlannerTooStrict" : "autolayout.warnStagingPlannerTooLoose",
            loc, destination
        );
    }

    /**
     * Works out whether every locomotive can be sent back to its home station, and how.
     *
     * Read-only: nothing is moved, nothing is loaded.  Ask this to decide whether to offer the action,
     * and what to say when it cannot be offered - the outcome distinguishes "nothing to do" from
     * "impossible" from "no plan found", which are three different things to tell a user.
     *
     * Meaningful only with the layout at rest; the plan is built against current positions and a train
     * in motion has none.
     *
     * @return
     */
    public HomeStaging.Plan planReturnToHome()
    {
        HomeStaging staging = HomeStaging.snapshot(this);

        // Debug only.  A mismatch here is the single most likely reason a staging run does something inexplicable, but the
        // audit asks the runtime for every path every locomotive could take and the runtime logs each one it rejects, which
        // would bury the operator's log under thousands of lines.
        if (this.control.isDebug())
        {
            this.control.logf("autolayout.infoStagingAudit", staging.auditAgainstRuntime());
        }

        HomeStaging.Plan plan = staging.plan();

        // Always logged: "nothing happened" is reachable several ways (no homes recorded, everything already home, no route,
        // no arrangement found), which look identical from outside.
        this.control.logf(
            "autolayout.infoReturnToHomePlan",
            plan.getOutcome().toString(),
            plan.getMoves().size(),
            this.homeStations.size()
        );

        for (HomeStaging.Move move : plan.getMoves())
        {
            this.control.logf("autolayout.infoReturnToHomeMove", move.toString());
        }

        // AND WHY NOT, for each train it could not bring home (Adam, FR-078: "state the reason why
        // the layout doesn't allow a locomotive to go to its home in the log").
        for (Map.Entry<Locomotive, List<String>> entry : plan.getReasons().entrySet())
        {
            for (String why : entry.getValue())
            {
                this.control.logf("autolayout.infoReturnToHomeWhy", entry.getKey().getName(), why);
            }
        }

        // AND EACH PAUSED TRAIN IT LEAVES AWAY FROM ITS HOME (FR-117), which is not a reason the plan failed
        for (Locomotive paused : staging.getPausedAwayFromHome())
        {
            this.control.logf("autolayout.infoReturnToHomePaused", paused.getName());
        }

        return plan;
    }

    /**
     * Whether a feedback sensor is reporting occupied right now.
     *
     * Exposed for the staging planner, which must read the real sensor rather than deduce it from who
     * is standing where: in simulation the feedback is pulsed and clears again behind a train, so a
     * stationary locomotive does not hold its sensor at all.
     *
     * @param s88
     * @return
     */
    public boolean isFeedbackOccupied(String s88)
    {
        return s88 != null && this.control.getFeedbackState(s88);
    }

    /**
     * Whether there is anything to send home at all, answered without planning.
     *
     * Cheap - it reads the occupancy and nothing else.  Ask this before anything a user has to respond
     * to: there is no point confirming that the timetable will be replaced when the run is not going
     * to happen.
     *
     * @return the outcome, or null when only a plan can say more
     */
    public HomeStaging.Outcome triageReturnToHome()
    {
        return triageReturnToHome(null);
    }

    /**
     * The same, and the paused trains standing away from their homes, taken from the one snapshot so the outcome and the
     * trains it names cannot differ (OB-355).
     *
     * @param pausedAway receives those trains, or null to ask for none
     * @return the outcome, or null when only a plan can say more
     */
    public HomeStaging.Outcome triageReturnToHome(List<Locomotive> pausedAway)
    {
        HomeStaging staging = HomeStaging.snapshot(this);

        if (pausedAway != null) pausedAway.addAll(staging.getPausedAwayFromHome());

        return staging.triage();
    }

    /**
     * Plans the return home and, if one exists, loads it into the timetable ready to execute.
     *
     * <b>This replaces the current timetable.</b>  Save it first if it matters - a captured or
     * hand-built timetable is persisted in the autonomy file, so overwriting it and then saving loses
     * it for good.
     *
     * The moves are emitted in the order the planner produced, each with no delay, so the existing
     * timetable machinery does the rest: executeTimetable dispatches entry i+1 once entry i has
     * started, and its retry loop holds a move back while the train ahead is still on the path it
     * needs.  That is what turns a sequential plan into as much parallelism as the layout allows,
     * without a scheduler - and it is why the order matters and must not be rearranged.
     *
     * Capture is NOT touched here: nothing in this method reads or writes `timetableCapture`.  The moves are kept out of the
     * timetable during the RUN by `addTimetableEntry`, which refuses while `timetableExecuting` is set (S14-C3).
     *
     * @return the plan, whether or not it could be loaded - check isPossible()
     */
    public HomeStaging.Plan loadReturnToHomeTimetable()
    {
        HomeStaging.Plan plan = planReturnToHome();

        if (!plan.isPossible()) return plan;

        if (this.isRunning())
        {
            this.control.logf("autolayout.errorCannotEditWhileRunning");
            return new HomeStaging.Plan(HomeStaging.Outcome.LOCOMOTIVES_RUNNING,
                new LinkedList<>(), new LinkedList<>());
        }

        // Capture is left exactly as the operator set it: the moves are appended during the RUN, and addTimetableEntry
        // excludes them for as long as timetableExecuting is set, which is the whole duration (S14-C3).
        List<TimetablePath> staged = new LinkedList<>();

        for (HomeStaging.Move move : plan.getMoves())
        {
            TimetablePath entry = new TimetablePath(move.getLocomotive(), move.getPath(), 0);

            // No delay: every entry should start as soon as the one before it has, and be held back
            // only by the path actually being occupied
            entry.setSecondsToNext(0);

            staged.add(entry);
        }

        // His own, for a fold to write while the plan occupies the timetable (RLV9-B1)
        List<TimetablePath> owners = new ArrayList<>(this.timetable);

        this.setTimetable(staged);

        // Must be after setTimetable, which clears it.  The plan is built on a model in which nothing is moving (see
        // HomeStaging), so the moves are only safe run one at a time: executeTimetable normally dispatches an entry once the
        // one before it has STARTED, and two overlapping staging moves can contend for an edge the planner never considered,
        // the second retrying forever on a route it cannot abandon.
        this.timetableSequential = true;

        // And so is this
        this.timetableOnLoan = owners;

        this.control.logf("autolayout.infoReturnToHomeLoaded", staged.size());

        return plan;
    }
        
    /**
     * Says whether the timetable's entries must run one at a time.
     *
     * Public because setTimetable clears it and only the staging planner sets it.  Nothing in the application calls it now
     * (`fromJSON` sets the field itself, and a fold no longer writes a plan, RLV9-B1); tests use it to state the flag by
     * hand for a file written before that (RLV10-C4).
     *
     * @param sequential true to wait for each entry to arrive before starting the next
     */
    public void setTimetableSequential(boolean sequential)
    {
        this.timetableSequential = sequential;
    }

    /**
     * Whether the loaded timetable must run one train at a time.
     *
     * True only for a staging plan: it is built on a model in which nothing is moving, so overlapping
     * its moves can contend for an edge the planner never considered.
     *
     * @return
     */
    public boolean isTimetableSequential()
    {
        return this.timetableSequential;
    }

    public boolean isTimetableCapture()
    {
        return timetableCapture;
    }

    public void setTimetableCapture(boolean timetableCapture)
    {
        this.timetableCapture = timetableCapture;
    }
    
    public void setMaxLocInactiveSeconds(int sec) throws Exception
    {
        if (sec < 0)
        {
            throw new IllegalArgumentException(
                I18n.f("autolayout.errorMaxLocInactiveSecondsNegative")
            );
        }
        
        this.maxLocInactiveSeconds = sec;
    }
    
    /**
     * If false, we skip route-related settings
     * @return 
     */
    public boolean isActivateRoutes()
    {
        return activateRoutes;
    }

    /**
     * Route IDs to activate with this layout (those listed are deactivated)
     * @return 
     */
    public List<Integer> getActivateRouteIDs()
    {
        return activateRouteIDs;
    }

    public void setActivateRoutes(boolean activateRoutes)
    {
        this.activateRoutes = activateRoutes;
    }

    public void setActivateRouteIDs(List<Integer> activateRouteIDs)
    {
        // Copied, not adopted: deleteRoute mutates this list, and one caller passes an immutable
        // Collections.singletonList.
        this.activateRouteIDs =
            activateRouteIDs == null ? new LinkedList<>() : new LinkedList<>(activateRouteIDs);
    }
    
    /**
     * Returns a concise string representing a path
     * @param path
     * @return 
     */
    public String pathToString(List<Edge> path)
    {
        List<String> pieces = new ArrayList<>();
        
        for (int i = 0; i < path.size(); i++)
        {
            if (i == 0)
            {
                pieces.add(path.get(i).getStart().getName());
                
                // Single edge only - include end
                if (path.size() == 1)
                {
                    pieces.add(path.get(i).getEnd().getName());
                }
            }
            else
            {
                pieces.add(path.get(i).getEnd().getName());
            }
        }
        
        return "[" + String.join(" -> ", pieces) + "]";
    }
    
    /**
     * Applies a default set of callbacks for the given locomotive.  
     * Will turn on preset functions on departure and disable them on arrival
     * @param l 
     */
    public void applyDefaultLocCallbacks(Locomotive l)
    {        
        l.setCallback(Layout.CB_ROUTE_START, (lc) -> 
        {
            // Optionally skip turning on the functions.  ASKED, NOT BUILT (RLV11-C5): this runs on the train's own thread,
            // after Unload as well, and asking for the railway built an empty one where there was none
            Layout layout = lc.getModel().getAutoLayoutIfLoaded();
            
            if (layout != null && layout.isTurnOnFunctionsOnDeparture())
            {
                lc.applyPreferredFunctions().delay(minDelay, maxDelay);
            }
            
            if (lc.hasDepartureFunc())
            {
                lc.toggleF(lc.getDepartureFunc()).delay(minDelay, maxDelay);
            }
        });
        
        // Always set callback in case of future edits
        l.setCallback(Layout.CB_PRE_ARRIVAL, (lc) -> 
        {
            if (lc.hasArrivalFunc())
            {
                lc.toggleF(lc.getArrivalFunc());
            }
        }); 

        l.setCallback(Layout.CB_ROUTE_END, (lc) ->
        {
            // Optionally disable the arrival functions.  Asked, not built, as above (RLV11-C5)
            Layout layout = lc.getModel().getAutoLayoutIfLoaded();
            
            if (layout != null && layout.isTurnOffFunctionsOnArrival())
            {
                lc.delay(minDelay, maxDelay).functionsOff().delay(minDelay, maxDelay);
            }
            else
            {
                lc.delay(minDelay, maxDelay);
            }
        });
    }
    
    /**
     * Returns the layout configuration as a JSON string
     * @return 
     * @throws java.lang.IllegalAccessException 
     * @throws java.lang.NoSuchFieldException 
     */
    synchronized public String toJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {
        return toJSON(Collections.<Locomotive, Point>emptyMap());
    }

    /**
     * The same, with each train under way written on the one point it is kept at (RLV8-B1): `getLastPointsReached`'s.
     *
     * A locked path holds every point on it for its train, so written point by point a train under way stands in several
     * places, which a configuration refuses.  A fold of a railway holding a path writes it on the kept point and no other
     * point it holds; a kept point a released path no longer records it on is written with it while the railway still shows
     * the train and the database has it, and a point another train only passes through gives way to the train kept there
     * (RLV8-C4).
     * @param keptAt each train under way against the point it is kept at; empty for a railway at rest
     * @return the railway
     * @throws IllegalArgumentException from the JSON
     * @throws IllegalAccessException from the JSON
     * @throws NoSuchFieldException from the JSON
     */
    synchronized public String toJSON(Map<Locomotive, Point> keptAt) throws IllegalArgumentException,
        IllegalAccessException, NoSuchFieldException
    {        
        List<JSONObject> pointJson = new LinkedList<>();
        List<JSONObject> edgeJson = new LinkedList<>();
        List<JSONObject> timeTableJson = new LinkedList<>();

        // Sort station names alphabetically
        List<Point> pointList = new ArrayList<>(this.getPoints());
        List<Edge> edgeList = new ArrayList<>(this.getEdges());

        Collections.sort(pointList, 
                (Point p1, Point p2) -> p1.getName().compareTo(p2.getName())
        );
        Collections.sort(edgeList, 
                (Edge p1, Edge p2) -> p1.getName().compareTo(p2.getName())
        );
        
        // Each kept point, with the train kept on it - a train the railway still shows somewhere, and the database has
        Set<Locomotive> onTheRailway = new HashSet<>();

        for (Point p : pointList)
        {
            if (p.getCurrentLocomotive() != null) onTheRailway.add(p.getCurrentLocomotive());
        }

        Map<Point, Locomotive> keptHere = new HashMap<>();

        for (Entry<Locomotive, Point> kept : keptAt.entrySet())
        {
            Locomotive train = kept.getKey();

            if (kept.getValue() == null || !onTheRailway.contains(train) || train.getName() == null
                || this.control.getLocByName(train.getName()) == null)
            {
                continue;
            }

            keptHere.put(kept.getValue(), train);
        }

        for (Point p : pointList)
        {
            Locomotive here = p.getCurrentLocomotive();

            // A train kept elsewhere is only on its way through here
            Locomotive standing = here != null && keptAt.containsKey(here) && keptAt.get(here) != p ? null : here;

            if (standing == null && keptHere.containsKey(p)) standing = keptHere.get(p);

            pointJson.add(p.toJSON(standing));
        }
        
        for (Edge e : edgeList)
        {
            edgeJson.add(e.toJSON());
        }
        
        // THE OWNER'S TIMETABLE, not a staging plan that borrows it (RLV9-B1): Return Home puts its plan in the
        // timetable while it runs and hands the original back when it ends, and a fold in between (a reload confirmed
        // during the run, or Unload) must not save the plan as the timetable.
        List<TimetablePath> owners = this.timetableOnLoan;

        for (TimetablePath p : owners != null ? owners : this.timetable)
        {
            timeTableJson.add(p.toJSON());
        }
        
        // Change the map to a linkedhashmap so that ordering gets preserved
        // https://stackoverflow.com/questions/4515676/keep-the-order-of-the-json-keys-during-json-conversion-to-csv
        JSONObject jsonObj = new JSONObject();
        Field map = jsonObj.getClass().getDeclaredField("map");
        map.setAccessible(true);
        map.set(jsonObj, new LinkedHashMap<>());
        map.setAccessible(false);

        jsonObj.put("points", pointJson);
        jsonObj.put("edges", edgeJson);
        jsonObj.put("minDelay", this.getMinDelay());
        jsonObj.put("maxDelay", this.getMaxDelay());
        jsonObj.put("defaultLocSpeed", this.getDefaultLocSpeed());
        jsonObj.put("preArrivalSpeedReduction", this.preArrivalSpeedReduction);
        jsonObj.put("maxLatency", this.getMaxLatency());
        jsonObj.put("turnOffFunctionsOnArrival", this.isTurnOffFunctionsOnArrival());
        jsonObj.put("pathPreference", this.pathPreference.name());
        jsonObj.put("turnOnFunctionsOnDeparture", this.isTurnOnFunctionsOnDeparture());
        jsonObj.put("atomicRoutes", this.isAtomicRoutes());
        jsonObj.put("maxActiveTrains", this.maxActiveTrains);
        jsonObj.put("maxLocInactiveSeconds", this.maxLocInactiveSeconds);
        jsonObj.put("timetable", timeTableJson);

        // Written only when set, and not while a plan has the timetable on loan, since the owner's timetable is what is
        // written then (RLV9-B1).  Without it a Return Home plan folded into a file would reload as an ordinary
        // timetable, whose entries dispatch as the previous one STARTS rather than arrives (RLV10-C4).
        if (this.timetableSequential && owners == null)
        {
            jsonObj.put("timetableSequential", true);
        }
        jsonObj.put("activateRoutes", this.isActivateRoutes());
        jsonObj.put("activateRouteIDs", new JSONArray(this.activateRouteIDs));

        // THE TRAINS PAUSED (FR-117): every train the database has paused, by name and sorted, so the file is the same
        // file when nothing has changed.  Written only while it names somebody.
        List<String> paused = pausedNamesOf(this.control);

        if (!paused.isEmpty()) jsonObj.put(PAUSED_LOCOMOTIVES, new JSONArray(paused));

        if (this.simulate)
        {
            jsonObj.put("simulate", true);
        }

        return jsonObj.toString(4);
    }
    
    /**
     * The key a configuration lists its paused trains under (FR-117), by name: written by `toJSON`, read by `fromJSON`,
     * and kept by the setup with the rest of a configuration's settings.
     */
    public static final String PAUSED_LOCOMOTIVES = "pausedLocomotives";

    /**
     * Every train the database has paused, by name and sorted (FR-117): the list `toJSON` writes under
     * `PAUSED_LOCOMOTIVES`.  Nothing but `toJSON` calls it.
     *
     * @param control the model, or null for none
     * @return the names, possibly none
     */
    public static List<String> pausedNamesOf(ViewListener control)
    {
        List<String> paused = new ArrayList<>();

        for (Locomotive l : control == null ? Collections.<Locomotive>emptyList() : control.getLocomotives())
        {
            if (l != null && l.getName() != null && l.isAutonomyPaused()) paused.add(l.getName());
        }

        Collections.sort(paused);

        return paused;
    }

    /**
     * Parses TrainControl's autonomous operation configuration file
     * @param config 
     * @param control 
     * @return  
     */
    public static Layout fromJSON(String config, ViewListener control)
    {           
        Layout layout = new Layout(control);
        JSONObject o;
        
        try
        {
            o = new JSONObject(config);
        }
        catch (JSONException e)
        {
            layout.invalidate(
                I18n.f("autolayout.errorJsonParsing")
            );
            return layout;
        }
               
        List<String> locomotives = new LinkedList<>();

        JSONArray points;
        JSONArray edges;
        Integer minDelay;
        Integer maxDelay;
        Integer defaultLocSpeed;
        
        // What each point is held back by, by NAME, until every point exists: the array is in file order, not dependency
        // order, so a restriction routinely names a point further down it.  Resolved after the loop.
        java.util.Map<String, List<String>> blockersByPoint = new java.util.LinkedHashMap<>();

        // AND THE ARRIVAL SIDES, applied only after the locomotives are placed (REG8-A1): `Point.setLocomotive` drops the
        // arrival side whenever the occupant changes, so one applied while the points are created is lost.
        java.util.Map<String, String> arrivalSideByPoint = new java.util.LinkedHashMap<>();

        // AND THE ROADS, held back for the same reason and applied in the same place (WK7-B1).
        java.util.Map<String, Object> arrivalRoadByPoint = new java.util.LinkedHashMap<>();

        // Validate basic required data
        try
        {
            points = o.getJSONArray("points");
            edges = o.getJSONArray("edges");
            minDelay  = o.getInt("minDelay");
            maxDelay  = o.getInt("maxDelay");
            defaultLocSpeed  = o.getInt("defaultLocSpeed");
        }
        catch (JSONException e)
        {
            layout.invalidate(
                I18n.f("autolayout.errorMissingOrInvalidKeys")
            );
            return layout;
        }

        if (points == null || edges == null)
        {
            layout.invalidate(
                I18n.f("autolayout.errorMissingKeysPointsEdges")
            );
            return layout;
        }
                
        // Save values in layout class
        try
        {
            layout.setDefaultLocSpeed(defaultLocSpeed);
            layout.setMaxDelay(maxDelay);
            layout.setMinDelay(minDelay);
            layout.setTurnOffFunctionsOnArrival(o.has("turnOffFunctionsOnArrival") && o.getBoolean("turnOffFunctionsOnArrival"));

            // The routing logic, if this configuration carries one; a file without it keeps the model's default.
            if (o.has("pathPreference"))
            {
                try
                {
                    layout.setPathPreference(PathPreference.valueOf(o.getString("pathPreference")));
                }
                catch (IllegalArgumentException | JSONException e)
                {
                    // Written by a version with a choice this one lacks: the default is a working setting, so neither
                    // refuse the railway nor add a message to eight bundles.
                }
            }
            
            if (!o.has("turnOnFunctionsOnDeparture"))
            {
                layout.setTurnOnFunctionsOnDeparture(true);
            }
            else
            {
                layout.setTurnOnFunctionsOnDeparture(o.getBoolean("turnOnFunctionsOnDeparture"));
            }            
        }
        catch (Exception e)
        {
            layout.invalidate(
                I18n.f("autolayout.errorGenericWithMessage", e.getMessage())
            );
            return layout;   
        }
                
        // Optional values
        if (o.has("preArrivalSpeedReduction"))
        {
            try
            {
                layout.setPreArrivalSpeedReduction(o.getDouble("preArrivalSpeedReduction"));
            }
            catch (Exception e)
            {
                layout.invalidate(
                    I18n.f("autolayout.errorPreArrivalSpeedReductionInvalid")
                );
                return layout;
            }    
        }
        
        if (o.has("maxLatency"))
        {
            try
            {
                layout.setMaxLatency(o.getInt("maxLatency"));
            }
            catch (Exception e)
            {
                layout.invalidate(
                    I18n.f("autolayout.errorMaxLatencyInvalid")
                );
                return layout;
            }    
        }
              
        if (o.has("maxActiveTrains"))
        {
            try
            {
                layout.setMaxActiveTrains(o.getInt("maxActiveTrains"));
                
                if (o.getInt("maxActiveTrains") > 0)
                {
                    control.logf(
                        "autolayout.infoSetMaxActiveTrains",
                        o.getInt("maxActiveTrains")
                    );
                }
            }
            catch (Exception e)
            {
                layout.invalidate(
                    I18n.f("autolayout.errorMaxActiveTrainsInvalid")
                );
                return layout;
            }    
        }
        
        if (o.has("maxLocInactiveSeconds"))
        {
            try
            {
                layout.setMaxLocInactiveSeconds(o.getInt("maxLocInactiveSeconds"));

                if (o.getInt("maxLocInactiveSeconds") > 0)
                {
                    control.logf(
                        "autolayout.infoYieldToInactiveTrains",
                        o.getInt("maxLocInactiveSeconds")
                    );
                }
            }
            catch (Exception e)
            {
                layout.invalidate(
                    I18n.f("autolayout.errorMaxLocInactiveSecondsInvalid")
                );
                return layout;
            } 
        }
        
        // Debug/dev only setting
        try
        {
            layout.setSimulate(false);
            
            if (o.has("simulate"))
            {
                layout.setSimulate(o.getBoolean("simulate"));
            }
        }
        catch (Exception e)
        {
            control.logf(
                "autolayout.warnSimulation",
                e.getMessage()
            );
        }

        // WHICH TRAINS ARE PAUSED (FR-117), and every train the database has is told yes or no: the pause lives on the
        // locomotive, which outlives this railway, so a train paused under the last configuration and not under this
        // one runs again.  A list that will not read pauses nobody.  The log names the paused trains, since an import,
        // a start-up and a rebuild all load through here.
        java.util.Set<String> pausedNames = new java.util.HashSet<>();

        JSONArray pausedList = o.optJSONArray(PAUSED_LOCOMOTIVES);

        for (int i = 0; pausedList != null && i < pausedList.length(); i++)
        {
            Object name = pausedList.opt(i);

            if (name instanceof String) pausedNames.add((String) name);
        }

        List<String> nowPaused = new ArrayList<>();

        for (Locomotive l : control.getLocomotives())
        {
            if (l == null || l.getName() == null) continue;

            boolean paused = pausedNames.contains(l.getName());

            l.setAutonomyPaused(paused);

            if (paused) nowPaused.add(l.getName());
        }

        if (!nowPaused.isEmpty())
        {
            Collections.sort(nowPaused);

            control.logf("autolayout.infoPausedLocomotives", String.join(", ", nowPaused));
        }
        
        if (o.has("atomicRoutes"))
        {
            try
            {
                layout.setAtomicRoutes(o.getBoolean("atomicRoutes"));

                if (!o.getBoolean("atomicRoutes"))
                {
                    control.logf(
                        "autolayout.infoAtomicRoutesDisabled"
                    );
                }
            }
            catch (JSONException e)
            {
                layout.invalidate(
                    I18n.f("autolayout.errorAtomicRoutesInvalid")
                );
                return layout;
            }    
        }
           
        // The trains placed on a copy that is no station, warned about once every copy is read (GUI4-C5).
        List<String[]> placedOffAStation = new ArrayList<>();

        // ONE LINE PER STATION (OB-362): the copies a square builds into, one per direction, all carry its maximum; say it
        // once, by the place.
        final Set<String> maximumsSaid = new HashSet<>();

        // Add points
        points.forEach(pnt ->
        { 
            JSONObject point = (JSONObject) pnt; 

            String s88 = null;
            if (point.has("s88"))
            {
                if (point.get("s88") instanceof Integer)
                {
                    s88 = Integer.toString(point.getInt("s88"));

                    if (!control.isFeedbackSet(s88))
                    {
                        control.logf(
                            "autolayout.warnFeedbackDoesNotExistInCs2Layout",
                            s88
                        );
                        control.newFeedback(point.getInt("s88"), null);
                    }
                }
                else if (!point.isNull("s88"))
                {
                    layout.invalidate(
                        I18n.f("autolayout.errorS88NotValidInteger", point.toString())
                    );
                }
            }
            
            // Read optional coordinates
            Integer x = null, y = null;
            if (point.has("x"))
            {
                if (point.get("x") instanceof Integer)
                {
                    x = point.getInt("x");
                }
                else
                {
                    layout.invalidate(
                        I18n.f("autolayout.errorNotValidInteger", "x", point.toString())
                    );
                }
            }

            if (point.has("y"))
            {
                if (point.get("y") instanceof Integer)
                {
                    y = point.getInt("y");
                }
                else
                {
                    layout.invalidate(
                        I18n.f("autolayout.errorNotValidInteger", "y", point.toString())
                    );
                }
            }
            
            try 
            {
                // optBoolean, not getBoolean: a missing "station" must default like every other optional field, not throw
                // out of this try and drop the point silently.
                layout.createPoint(point.getString("name"), point.optBoolean("station", false), s88);

                // Which piece of track this Point is part of, when the generator said so; absent, the Point stands alone.
                if (point.has("block"))
                {
                    layout.getPoint(point.getString("name")).setBlock(point.optString("block", null));
                }

                // AND WHICH SQUARE OF THE DIAGRAM IT IS A COPY OF (RSA5-A1): what a rebuild carries a train by, since a
                // rename can hand a Point's name to another square
                if (point.has("square"))
                {
                    layout.getPoint(point.getString("name")).setSquare(point.optString("square", null));
                }

                // The signals thrown to red while this platform is claimed: a bare string or an array, both read.
                if (point.has("protectingSignal"))
                {
                    List<String> signals = new ArrayList<>();

                    JSONArray several = point.optJSONArray("protectingSignal");

                    if (several != null)
                    {
                        for (int at = 0; at < several.length(); at++)
                        {
                            signals.add(several.getString(at));
                        }
                    }
                    else if (point.optString("protectingSignal", null) != null)
                    {
                        signals.add(point.getString("protectingSignal"));
                    }

                    layout.getPoint(point.getString("name")).setProtectingSignals(signals);
                }

                // The entry guard (FR-096), read in the same two shapes.
                if (point.has("entrySignal"))
                {
                    List<String> signals = new ArrayList<>();

                    JSONArray several = point.optJSONArray("entrySignal");

                    if (several != null)
                    {
                        for (int at = 0; at < several.length(); at++) signals.add(several.getString(at));
                    }
                    else if (point.optString("entrySignal", null) != null)
                    {
                        signals.add(point.getString("entrySignal"));
                    }

                    layout.getPoint(point.getString("name")).setEntrySignals(signals);
                }

                // WHERE THE TAIL IS, which facing cannot answer once a train has been turned.  Kept and applied after the
                // locomotives are placed, not here (REG8-A1).
                if (point.has("arrivedFrom"))
                {
                    arrivalSideByPoint.put(point.getString("name"),
                        point.optString("arrivedFrom", null));
                }

                if (point.has("arrivedAlong"))
                {
                    arrivalRoadByPoint.put(point.getString("name"), point.get("arrivedAlong"));
                }

                if (point.has("blockedBy"))
                {
                    // The points whose occupancy makes this station unavailable to autonomy (FR-001).  Kept as names and
                    // resolved after the loop, because the array is in file order, not dependency order; a name that
                    // matches nothing then is logged and dropped, since refusing the configuration would take a whole layout
                    // out of service for one lost pairing.
                    List<String> blockers = new ArrayList<>();

                    JSONArray named = point.optJSONArray("blockedBy");

                    if (named != null)
                    {
                        for (int at = 0; at < named.length(); at++)
                        {
                            blockers.add(named.getString(at));
                        }
                    }
                    else if (point.optString("blockedBy", null) != null)
                    {
                        blockers.add(point.getString("blockedBy"));
                    }

                    blockersByPoint.put(point.getString("name"), blockers);
                }

                if (point.has("home"))
                {
                    // The boundary, and the only place a home can be dangling: a file holds a name and a Point holds the
                    // locomotive.  A name that matches nothing is reported and dropped, not refused, because invalidating a
                    // whole layout over one assignment is worse than losing it.
                    String named = point.optString("home", null);

                    if (named != null && !named.trim().isEmpty())
                    {
                        Locomotive home = control.getLocByName(named);

                        if (home == null)
                        {
                            control.logf("autolayout.warnHomeLocomotiveNotInDatabase", named,
                                point.getString("name"));
                        }

                        Point homeAt = layout.getPoint(point.getString("name"));

                        // TAKEN AS WRITTEN: the home is the square, even when the square is drawn as more than one
                        // graph Point; dropping it here would throw away `AutonomyBuilder.homeCopy`'s choice of copy
                        // (every copy carries a block).
                        homeAt.setHomeLoc(home);

                        // AND WHETHER IT WAS SET FACING THIS COPY'S WAY (OB-282).
                        if (homeAt != null && home != null)
                        {
                            homeAt.setHomeFacingFixed(point.optBoolean("homeFacingFixed", false));
                        }
                    }
                }

                // WHICH WAY A TRAIN ON THIS COPY POINTS, on a split square (OB-282, TDY-B1).
                if (point.has("copyFacing") && layout.getPoint(point.getString("name")) != null)
                {
                    layout.getPoint(point.getString("name")).setCopyFacing(point.optString("copyFacing", null));
                }
                
                if (point.has("excludedLocs") && point.get("excludedLocs") instanceof JSONArray)
                {
                    JSONArray locs = point.getJSONArray("excludedLocs");
                
                    Set<Locomotive> excludedLocs = new HashSet<>();
                    
                    for(Object loc : locs)
                    {
                        try
                        {
                            String locName = (String) loc;

                            // Named in the log when it is dropped, like the home and blockedBy doors (DR-B9): an exclusion that
                            // vanishes silently is a restriction the operator believes is in force.  Dropping is right, since
                            // one bad entry must not take the layout out of service.  The load door removes the name from the
                            // setup as well and logs it there (OB-349).
                            Locomotive excluded = control.getLocByName(locName);

                            if (excluded != null) excludedLocs.add(excluded);
                            else control.logf("autolayout.warnExcludedLocomotiveNotInDatabase", locName);
                        }
                        catch (Exception e)
                        {
                            layout.invalidate(
                                I18n.f("autolayout.errorInvalidExcludedLocomotive", point.toString(), e.getMessage())
                            );
                        }
                    }
                    
                    layout.getPoint(point.getString("name")).setExcludedLocs(excludedLocs);
                }

                // THE LIST'S MODE (FR-121): the locomotives allowed rather than the ones kept out
                if (point.has(Point.EXCLUDED_LOCS_MODE) && layout.getPoint(point.getString("name")) != null)
                {
                    layout.getPoint(point.getString("name")).setAllowOnlyListed(
                        Point.ALLOW_ONLY_LISTED.equals(point.optString(Point.EXCLUDED_LOCS_MODE, null)));
                }
                
                if (point.has("maxTrainLength"))
                {
                    if (point.get("maxTrainLength") instanceof Integer && point.getInt("maxTrainLength") >= 0)
                    { 
                        layout.getPoint(point.getString("name")).setMaxTrainLength(point.getInt("maxTrainLength"));

                        String place = placeNameOf(layout.getPoint(point.getString("name")));

                        if (point.getInt("maxTrainLength") > 0
                            && maximumsSaid.add(place + "\u0000" + point.getInt("maxTrainLength")))
                        {
                            control.logf(
                                "autolayout.infoSetMaxTrainLength",
                                point.getInt("maxTrainLength"),
                                place
                            );
                        }
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorMaxTrainLengthInvalid", point.getString("name"))
                        );
                    }
                }
                else
                {
                    layout.getPoint(point.getString("name")).setMaxTrainLength(0);
                }   
     
                // Set optional coordinates
                if (x != null && y != null)
                {
                    layout.getPoint(point.getString("name")).setX(x);
                    layout.getPoint(point.getString("name")).setY(y);
                }
                
                if (point.has("terminus"))
                {
                    if (point.get("terminus") instanceof Boolean)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setTerminus(point.getBoolean("terminus"));
                        } 
                        catch (Exception e)
                        {
                            layout.invalidate(
                                I18n.f("autolayout.errorSettingValueFailed", point.toString(), e.getMessage())
                            );
                        }
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorTerminusInvalidValue", point.toString())
                        );
                    }
                }  
                
                if (point.has("active"))
                {
                    if (point.get("active") instanceof Boolean)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setActive(point.getBoolean("active"));
                        } 
                        catch (Exception e)
                        {
                            layout.invalidate(
                                I18n.f("autolayout.errorSettingValueFailed", point.toString(), e.getMessage())
                            );
                        }
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorActiveInvalidValue", point.toString())
                        );
                    }
                }

                if (point.has("autoDestination"))
                {
                    if (point.get("autoDestination") instanceof Boolean)
                    {
                        layout.getPoint(point.getString("name"))
                            .setAutoDestination(point.getBoolean("autoDestination"));
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorAutoDestinationInvalidValue", point.toString())
                        );
                    }
                }

                if (point.has("reversing"))
                {
                    if (point.get("reversing") instanceof Boolean)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setReversing(point.getBoolean("reversing"));
                        } 
                        catch (Exception e)
                        {
                            layout.invalidate(
                                I18n.f("autolayout.errorSettingValueFailed", point.toString(), e.getMessage())
                            );
                        }
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorReversingInvalidValue", point.toString())
                        );
                    }
                }

                if (point.has("speedMultiplier"))
                {
                    if (point.get("speedMultiplier") instanceof Number)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setSpeedMultiplier(point.getDouble("speedMultiplier"));
                        } 
                        catch (Exception e)
                        {
                            layout.invalidate(
                                I18n.f("autolayout.errorSettingValueFailed", point.toString(), e.getMessage())
                            );
                        }
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorSpeedMultiplierInvalidValue", point.toString())
                        );
                    }
                } 
                
                if (point.has("priority"))
                {
                    if (point.get("priority") instanceof Integer)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setPriority(point.getInt("priority"));
                        } 
                        catch (Exception e)
                        {
                            layout.invalidate(
                                I18n.f("autolayout.errorSettingValueFailed", point.toString(), e.getMessage())
                            );
                        }
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorPriorityInvalidValue", point.toString())
                        );
                    }
                } 
            } 
            catch (Exception ex)
            {
                control.log(ex);
                
                layout.invalidate(
                    I18n.f("autolayout.errorPointErrorWithMessage", point.toString(), ex.getMessage())
                );
                return;
            }

            // Set the locomotive
            if (point.has("loc") && !point.isNull("loc"))
            {
                if (point.get("loc") instanceof JSONObject)
                {
                    JSONObject locInfo = point.getJSONObject("loc");
                    
                    if (locInfo.has("name") && locInfo.get("name") instanceof String)
                    {
                        String loc = locInfo.getString("name");

                        if (control.getLocByName(loc) != null)
                        {
                            Locomotive l = control.getLocByName(loc);

                            if (locInfo.has("trainLength"))
                            {
                                if (locInfo.get("trainLength") instanceof Integer && locInfo.getInt("trainLength") >= 0)
                                {
                                    l.setTrainLength(locInfo.getInt("trainLength"));   

                                    control.logf(
                                        "autolayout.infoSetTrainLength",
                                        locInfo.getInt("trainLength"),
                                        loc
                                    );
                                }
                                else
                                {
                                    layout.invalidate(
                                        I18n.f("autolayout.errorTrainLengthInvalid", loc.toString())
                                    );
                                }
                            }
                            // ABSENT MEANS NOT STATED (REL-A2): the diagram's capture writes a placement as {"name": X} and
                            // nothing else, so an absent trainLength must not clear the length held in the live Locomotive.
                            // `speed` below works the same way (MT-233).  Clearing a length is still possible: write the key as 0.

                            if (locInfo.has("reversible"))
                            {
                                if (locInfo.get("reversible") instanceof Boolean)
                                {
                                    l.setReversible(locInfo.getBoolean("reversible"));   

                                    if (locInfo.getBoolean("reversible"))
                                    {
                                        control.logf(
                                            "autolayout.infoLocomotiveFlaggedReversible",
                                            loc
                                        );
                                    }
                                }
                                else
                                {
                                    layout.invalidate(
                                        I18n.f("autolayout.errorLocomotiveReversibleInvalid", loc)
                                    );
                                }
                            }
                            // ABSENT MEANS NOT STATED (REL-A2), as above.  A reversible EMU read back as non-reversible is
                            // refused a terminus by `pickPath` and planned the long way round, with nothing to say why.

                            // Only throw a warning if this is not a station - after the loop, where the
                            // square's other copies have been read (GUI4-C5).
                            if (!point.optBoolean("station", false))
                            {
                                placedOffAStation.add(new String[] {loc, point.getString("name")});
                            }

                            // De-conflict with other multi-units - before it is put down, as a placement asks (BPV-A1).
                            layout.clearMultiUnitConflictsWith(l);

                            Point placeOn = layout.getPoint(point.getString("name"));

                            // And with anything already standing on the same square (the second door into two trains
                            // on one piece of track): a file written while the layout was in that state would otherwise
                            // reinstate it on every load.  Repaired rather than refused, since a configuration that will
                            // not load is a railway nobody can use; `clearBlockExcept` names the train it displaces.
                            layout.clearBlockExcept(placeOn);

                            // Place the locomotive
                            placeOn.setLocomotive(l);
                            
                            // NOT RESET (REL-A2): a placement from the diagram carries only a name, so a departure function
                            // set in the locomotive window must survive a start-up.

                            // Set start and end callbacks
                            if (locInfo.has("speed") && locInfo.get("speed") != null)
                            {
                                try
                                {
                                    if (locInfo.getInt("speed") > 0 && locInfo.getInt("speed") <= 100)
                                    {
                                        l.setPreferredSpeed(locInfo.getInt("speed"));
                                    }
                                }
                                catch (JSONException ex)
                                {
                                    layout.invalidate(
                                        I18n.f("autolayout.errorSpeedInvalidValue", locInfo.getString("name"))
                                    );
                                }
                            }

                            // Set departure callback
                            if (locInfo.has("departureFunc") && locInfo.get("departureFunc") != null)
                            {
                                try
                                {
                                    l.setDepartureFunc(locInfo.getInt("departureFunc"));
                                }
                                catch (JSONException ex)
                                {
                                    layout.invalidate(
                                        I18n.f("autolayout.errorDepartureFuncInvalidValue", locInfo.getString("name"))
                                    );
                                }
                            }

                            // Fires functions on departure and arrival
                            layout.applyDefaultLocCallbacks(l);

                            // NOT RESET (REL-A2), for the reason at `departureFunc` above.

                            // Set arrival callback
                            if (locInfo.has("arrivalFunc") && locInfo.get("arrivalFunc") != null)
                            {
                                try
                                {
                                    l.setArrivalFunc(locInfo.getInt("arrivalFunc"));
                                }
                                catch (JSONException ex)
                                {
                                    layout.invalidate(
                                        I18n.f("autolayout.errorArrivalFuncInvalidValue", locInfo.getString("name"))
                                    );
                                }
                            }

                            // Handle default speed
                            if (l.getPreferredSpeed() == 0)
                            {
                                l.setPreferredSpeed(defaultLocSpeed);
                                control.logf(
                                    "autolayout.warnLocomotiveDefaultSpeedApplied",
                                    loc,
                                    defaultLocSpeed
                                );
                            }

                            // Duplicate locomotive check
                            if (locomotives.contains(loc))
                            {
                                layout.invalidate(
                                    I18n.f("autolayout.errorDuplicateLocomotive", loc.toString(), point.getString("name"))
                                );
                            }
                            else
                            {
                                locomotives.add(loc);
                            }
                        }
                        else
                        {
                            // DROPPED AND NAMED, NOT REFUSED (AMR-C3; Adam: **"drop the train and keep the rest."**): one sold
                            // or renamed locomotive must not cost the whole railway.  The placement is simply not made;
                            // everything else about the point loads as written.  The load door removes the name from the setup
                            // before it builds (`AutonomyViewerPanel.loadPrepared`, OB-349), so this arm is reached by a
                            // configuration handed to parseAuto some other way.
                            control.logf("autolayout.warnPlacedLocomotiveNotInDatabase", loc,
                                point.getString("name"));
                        }
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorLocomotiveConfigMissingName", point.getString("name"))
                        );
                    }
                }
                else
                {
                    control.logf(
                        "autolayout.warnInvalidLocValue",
                        point.get("loc").toString()
                    );
                }
            }
        });

        // NOT FOR A COPY OF A STATION (GUI4-C5): a train can stand on a copy of a station square that trains may not
        // arrive at (reversed on the throttle, arrivals from one side barred), and the build records it there.  The warning
        // is raised here, after the loop, because the square's other copies are not all read inside it.
        for (String[] placed : placedOffAStation)
        {
            Point on = layout.getPoint(placed[1]);

            if (on != null && (layout.isABarredCopyOfAStation(on) || layout.startableTwinOf(on) != null)) continue;

            control.logf("autolayout.warnLocomotivePlacedOnNonStation", placed[0]);
        }

        // Add edges
        edges.forEach(edg -> 
        { 
            JSONObject edge = (JSONObject) edg; 
            try 
            {
                String start = edge.getString("start");
                String end = edge.getString("end");

                if (edge.has("commands") && !edge.isNull("commands"))
                {
                    JSONArray commands = edge.getJSONArray("commands");

                    // Validate commands
                    commands.forEach((cmd) ->
                    {
                        JSONObject command = (JSONObject) cmd;
                        
                        // Validate accessory
                        if (command.has("acc") && !command.isNull("acc"))
                        {
                            String accessory = command.getString("acc");
                            if (null == control.getAccessoryByName(accessory))
                            {
                                // This call is here to ADD the accessory, not merely to check it.  If it cannot be added the
                                // edge can never be actuated, so the failure is fatal: the outer catch invalidates the layout.
                                try
                                {
                                    Edge.validateConfigCommand(accessory, Accessory.accessorySetting.GREEN.toString(), control);
                                }
                                catch (Exception e)
                                {
                                    // Unchecked because this runs inside a forEach consumer.  The outer
                                    // catch turns it back into a readable invalidation message.
                                    throw new RuntimeException(
                                        I18n.f("autolayout.errorEdgeAccessoryCouldNotBeAdded", accessory, e.getMessage())
                                    );
                                }
                            }
                        }
                        else
                        {
                            layout.invalidate(
                                I18n.f("autolayout.errorEdgeMissingAccessoryDefinition", start, end, command.toString())
                            );
                        }

                        // Validate state
                        if (command.has("state") && !command.isNull("state"))
                        {            
                            String action = command.getString("state");

                            if (null == Accessory.stringToAccessorySetting(action))
                            {
                                layout.invalidate(
                                    I18n.f("autolayout.errorEdgeInvalidAction", start, end, command.toString())
                                );
                            }
                        }
                        else
                        {
                            layout.invalidate(
                                I18n.f("autolayout.errorEdgeMissingState", start, end, command.toString())
                            );
                        }
                    });
                }
                
                Edge e = layout.createEdge(start, end); 
                
                // Store the raw config commands so that we can reference them later
                if (edge.has("commands") && !edge.isNull("commands"))
                {
                    JSONArray commands = edge.getJSONArray("commands");
                    commands.forEach((cmd) -> 
                    {
                        JSONObject command = (JSONObject) cmd;
                        String action = command.getString("state");
                        String acc = command.getString("acc");

                        e.addConfigCommand(acc, Accessory.stringToAccessorySetting(action));
                    });                    
                }            
                
                // The room after the last switch, where the builder recorded one.  Read on its own, not inside a length
                // arm: a file with one and not the other is an ordinary older file.
                if (edge.has("roomAtTheEnd") && edge.get("roomAtTheEnd") instanceof Integer)
                {
                    e.setRoomAtTheEnd(edge.getInt("roomAtTheEnd"));
                }

                // THE SIDE THE TRACK COMES IN BY, as the builder split the square on (OB-182).  Optional: when missing the
                // readers fall back to the geometry.
                if (edge.has("entrySide") && edge.get("entrySide") instanceof String)
                {
                    e.setEntrySide(edge.getString("entrySide"));
                }

                // WHERE THIS EDGE RUNS, place by place (OB-207).  Optional; when missing the covered-track sweep uses the
                // whole edge.
                if (edge.has("places") && edge.get("places") instanceof JSONArray)
                {
                    List<String> ids = new LinkedList<>();

                    List<Integer> spans = new LinkedList<>();

                    List<String> answered = new LinkedList<>();

                    // What Mass Assign asks a length for, place by place (OB-297, ADA-C1).
                    java.util.Map<String, String> asked = new java.util.LinkedHashMap<>();

                    JSONArray places = edge.getJSONArray("places");

                    // ALL OF THEM OR NONE OF THEM (SVX-C9).  These are read POSITIONALLY: the last entry is the square the
                    // edge arrives at, where a standing train's body is spent from (`walkOneTail` and `whyABerthCannotHoldIt`
                    // start at the last element and never check it against `getEnd()`).  Skipping a malformed entry would
                    // shift every position, so a bad entry falls back to the whole edge, which every reader handles and which
                    // is the refusing side.  A build never writes one: `GraphReducer.placesAlong` always appends the end place.
                    boolean whole = true;

                    for (int at = 0; at < places.length(); at++)
                    {
                        JSONObject place = places.optJSONObject(at);

                        if (place == null || !place.has("at"))
                        {
                            whole = false;

                            break;
                        }

                        ids.add(place.getString("at"));

                        spans.add(Math.max(0, place.optInt("length", 0)));

                        if (place.optBoolean("answered", false)) answered.add(place.getString("at"));

                        if (place.has("toMeasure")) asked.put(place.getString("at"), place.getString("toMeasure"));
                    }

                    if (whole)
                    {
                        e.setPlaces(ids, spans);

                        e.setAnsweredPlaces(answered);

                        if (edge.optBoolean("lengthsAsked", false)) e.setPiecesToMeasure(asked);
                    }
                    else
                    {
                        control.logf("autolayout.warnEdgePlacesIncomplete", e.getName());
                    }
                }

                if (edge.has("length"))
                {
                    if (edge.get("length") instanceof Integer && edge.getInt("length") >= 0)
                    {
                        e.setLength(edge.getInt("length"));   

                        if (edge.getInt("length") > 0)
                        {
                            if (control.isDebug())
                            {
                                control.logf(
                                    "autolayout.infoSetEdgeLength",
                                    edge.getInt("length"),
                                    e.getName()
                                );
                            }
                        }
                    }
                    else
                    {
                        layout.invalidate(
                            I18n.f("autolayout.errorEdgeLengthInvalid", e.getName())
                        );
                    }
                }
                else
                {
                    e.setLength(0);
                }
            } 
            catch (Exception ex)
            {
                layout.invalidate(
                    I18n.f("autolayout.errorInvalidEdgeWithMessage", edge.toString(), ex.getMessage())
                );
            }
        });

        // Add lock edges
        edges.forEach(edg -> 
        { 
            JSONObject edge = (JSONObject) edg; 
            try 
            { 
                String start = edge.getString("start");
                String end = edge.getString("end");  
                
                if (layout.getEdge(start, end) != null && edge.has("lockedges"))
                {
                    edge.getJSONArray("lockedges").forEach(lckedg -> {
                        JSONObject lockEdge = (JSONObject) lckedg;

                        if (layout.getEdge(lockEdge.getString("start"), lockEdge.getString("end")) == null)
                        {
                            // DROPPED, AND SAID LOUDLY (AMR-C3; Adam: **"drop the lock edge with a loud log line too."**).
                            // A lock is a constraint on shared metal, so the message is written for the dangerous reading: if
                            // the named track is misspelt for track that IS there, two trains may now be let onto one piece of
                            // metal.  It names the track that owns the lock and the name that matched nothing, and says to
                            // check the file; the other locks in the list are still applied.
                            control.logf("autolayout.warnLockEdgeNotInGraph", start + " -> " + end,
                                lockEdge.getString("start") + " -> " + lockEdge.getString("end"));
                        }
                        else
                        {
                            layout.getEdge(start, end).addLockEdge(
                                layout.getEdge(lockEdge.getString("start"), lockEdge.getString("end"))
                            );
                        }
                    });
                }
            } 
            catch (JSONException ex)
            {
                layout.invalidate(
                    I18n.f("autolayout.errorLockEdgeGeneric", edge.toString())
                );
            }
        });
        
        // Load the timetable
        try
        {
            JSONArray timetable = o.getJSONArray("timetable");
            List<TimetablePath> timetableList = new LinkedList<>();
            
            for (Object tt : timetable)
            {
                // Each entry on its own, so one that cannot be read costs ITSELF: an entry naming a renamed or deleted
                // locomotive or station is a stale record, not a corrupt file, and letting the exception out would discard
                // the whole timetable (the next capture would write the empty one back).
                try
                {
                    timetableList.add(TimetablePath.fromJSON(tt.toString(), control, layout));
                }
                catch (Exception entry)
                {
                    control.logf("autolayout.warnTimetableEntry", entry.getMessage());
                }
            }
            
            layout.setTimetable(timetableList);

            // After setTimetable, which clears it: overlapping execution is the normal behaviour and
            // the flag is the exception, so the load order has to put the exception last.
            layout.timetableSequential = o.optBoolean("timetableSequential", false);
        }
        catch (Exception e)
        {
            control.logf(
                "autolayout.warnTimetable",
                e.getMessage()
            );
        }
        
        // Read boolean safely
        if (o.has("activateRoutes"))
        {
            layout.setActivateRoutes(o.optBoolean("activateRoutes", false));
        }
        
        if (o.has("activateRouteIDs"))
        {
            JSONArray arr = o.optJSONArray("activateRouteIDs");
            List<Integer> ids = new ArrayList<>();

            try
            {
                if (arr != null)
                {
                    for (int i = 0; i < arr.length(); i++)
                    {
                        ids.add(arr.getInt(i));
                    }   
                }
            }
            catch (Exception e)
            {
                control.logf(
                    "autolayout.warnRouteConfig",
                    "activateRouteIDs",
                    e.getMessage()
                );
            }
            
            layout.setActivateRouteIDs(ids);
        }
  
        /*if (locomotives.isEmpty())
        {
            control.log("Auto layout error: No locomotives placed.");
            layout.invalidate();
        }*/
        
        List<Locomotive> locsToRun = new LinkedList<>();

        for (String s : locomotives)
        {
            // Skip names that no longer resolve to a locomotive (deleted/renamed since the config was saved).
            // A null here would be a latent bug regardless: it also NPEs when checkForSlowerLoc iterates the run list.
            Locomotive loc = control.getLocByName(s);

            if (loc != null)
            {
                locsToRun.add(loc);
            }
            else
            {
                // Said out loud (DR-B9). Dropping it is right; doing so in silence meant a train that
                // simply never moved, with nothing anywhere to connect that to a rename.
                control.logf("autolayout.warnRunLocomotiveNotInDatabase", s);
            }
        }

        layout.setLocomotivesToRun(locsToRun);

        // The FR-001 restrictions, now that every point exists.  A name matching no point is dropped and said out loud: it
        // could never be resolved later and would be written back out on every save, and refusing the configuration would
        // take a whole layout out of service for one lost pairing.
        for (java.util.Map.Entry<String, List<String>> entry : blockersByPoint.entrySet())
        {
            Point held = layout.getPoint(entry.getKey());

            if (held == null) continue;

            List<Point> watching = new ArrayList<>();

            for (String name : entry.getValue())
            {
                Point watched = layout.getPoint(name);

                if (watched == null)
                {
                    control.logf("autolayout.warnBlockingPointNotFound", name, entry.getKey());
                    continue;
                }

                watching.add(watched);
            }

            held.setBlockedBy(watching);
        }

        // THE ARRIVAL SIDES, now that every train is standing where the file says (REG8-A1).  Last word to the file: it
        // describes a state that was consistent when written, and the clear in `setLocomotive` is for a change of occupant
        // on a LIVE railway, not a build.  A name matching no point is dropped in silence, unlike the restrictions above:
        // a point that has gone takes its own tail with it.
        for (java.util.Map.Entry<String, String> entry : arrivalSideByPoint.entrySet())
        {
            Point landed = layout.getPoint(entry.getKey());

            // AND ONLY ONTO A SQUARE WHOSE PLACEMENT WAS HONOURED (AUR-C1): a tail with no train on it is handed to whoever
            // reserves the square next.
            if (landed != null && landed.getCurrentLocomotive() != null)
            {
                landed.setArrivedFrom(entry.getValue());
            }
        }

        // AND THE ROADS, after the sides and for the same reason (WK7-B1): the placement pass above clears both
        // on a change of occupant.  A road naming an edge that no longer exists is dropped whole - half a road
        // would send the tail somewhere the train never was - and the walk then keeps the fork rule.
        for (java.util.Map.Entry<String, Object> entry : arrivalRoadByPoint.entrySet())
        {
            Point landed = layout.getPoint(entry.getKey());

            if (landed != null && landed.getCurrentLocomotive() != null)
            {
                landed.setArrivedAlong(layout.roadNamed(entry.getValue()));
            }
        }

        // Applied only now: until every point exists there is no telling an assignment that will be honoured from one whose
        // locomotive has not been read yet.  A file with no assignments reaches here with nothing to apply.
        layout.rebuildHomeStations();

        return layout;
    }

    public int getMaxActiveTrains()
    {
        return maxActiveTrains;
    }

    public void setMaxActiveTrains(int maxActiveTrains)
    {
        if (maxActiveTrains >= 0)
        {
            this.maxActiveTrains = maxActiveTrains;
        }
    }
    
    public static String getLastError()
    {
        return Layout.lastError;
    }
}