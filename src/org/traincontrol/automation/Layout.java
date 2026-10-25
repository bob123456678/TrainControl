package org.traincontrol.automation;

import org.traincontrol.base.Accessory;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.base.Locomotive;
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
import org.traincontrol.util.Util;

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

    // The deadline is PATH_VALIDATION_MS * (accessories on the path + 1).  Not final so tests can shorten it.
    public static int PATH_VALIDATION_MS = 1000;

    // When true, a locomotive departs only once the CS echo shows every accessory on its path set.  Tied to the
    // "Path Integrity Validation" preference in the UI, but respected headless via this flag.  Default on.
    public static boolean PATH_INTEGRITY_VALIDATION = true;

    // Failures are always logged; a UI popup comes once this many have accumulated, at most once per Layout.
    public static int PATH_VALIDATION_ALERT_THRESHOLD = 3;

    // Never reset.  Guarded by this Layout, since its locomotives validate paths concurrently.
    private int pathValidationFailureCount = 0;

    // Whether the one-time UI alert has already been shown for this Layout instance.  Guarded by this
    // Layout, same as pathValidationFailureCount.
    private boolean pathValidationAlertShown = false;

    // Maximum number of seconds another locomotive should yield for to the inactive locomotive
    public static final int YIELD_SECONDS = 30;

    /** A boxed-in locomotive's wait between path searches when no delay is set: retrying flat out pegs a core. */
    private static final long NO_PATH_IDLE_MS = 250;

    /**
     * How autonomy chooses between the routes a train could take.  Each rule ranks routes by a cost, lower is better.
     */
    public enum PathPreference
    {
        /** The route past the fewest other stations: stations are where trains stop, so the fewest trains disturbed. */
        FEWEST_STATIONS,

        /** The route over the shortest track, from the tiles' lengths; an unmeasured section counts one. */
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

        /** The route past the MOST other stations; this and the two below mirror the three rules above. */
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
         * Whatever is found first, which with the destinations shuffled is effectively random.  The default, and the
         * cheapest: the ranked rules enumerate every alternative first.
         */
        RANDOM,

        /**
         * Whatever is found first, with station priority ignored, so every reachable destination is equally likely
         * (OB-156).  The priority comparator answers "equal" for this rule and Collections.sort is stable, so the
         * shuffle survives.
         */
        RANDOM_ANY_STATION,

        /**
         * Priority weighed against distance.  The only rule that sees past the top priority band (pickPath settles the
         * highest band first for every other rule), so a nearer, less important station can win.
         */
        BALANCED_PRIORITY
    }

    /** Per layout, not per program: toJSON writes it into the autonomy config, so it travels with the configuration. */
    private volatile PathPreference pathPreference = PathPreference.RANDOM;

    public void setPathPreference(PathPreference preference)
    {
        if (preference != null) this.pathPreference = preference;
    }

    public PathPreference getPathPreference()
    {
        return this.pathPreference;
    }

    /** A route's cost under the current preference, lower being better; 0 where the preference does not rank. */
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
     * Priority per unit of track (BALANCED_PRIORITY), larger being better.  Divides by at least 1, so a route measured
     * 0 ties here with one measured 1, where SHORTEST_LENGTH prefers the 0.
     */
    private int ratioOf(List<Edge> path)
    {
        if (path == null || path.isEmpty()) return 0;

        Point end = path.get(path.size() - 1).getEnd();

        int length = Math.max(1, lengthOf(path));

        // What a station is worth, always positive: (priority + 1) million at or above the default 0, so an
        // unprioritised railway ranks by 1/distance; below zero, 1/(1 - priority) of an ordinary station, since a
        // negative numerator over a longer route would grow.  A million, in long, because the division is integer.
        long worth = end.getPriority() >= 0
            ? (end.getPriority() + 1L) * 1000000L
            : 1000000L / (1L - end.getPriority());

        // Clamped, not wrapped: a huge priority must not come back as a negative cost.
        return (int) Math.min(Integer.MAX_VALUE, worth / length);
    }
    /**
     * Track length of a route from its tiles' lengths.  An edge with no length counts 1 (Adam: "where we count each s88
     * as length 1 by default"), which keeps the length rules from tying at zero; the flat floor assumes small units.
     * An edge answered 0 is a measurement (Edge.isMeasured) and counts 0.
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
     * By block where a Point has one, as sensorsOn counts.  By uniqueId, an arrival by the north copy would leave the
     * south copy "never visited".
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
     * The visit history, for restoreVisitHistory after a rebuild: parseAuto replaces the Layout on every setup gesture.
     * Keyed by point name, since unique ids do not survive a rebuild.  A copy.
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
     * Puts a previous layout's visit history back after a rebuild.  A name this layout does not have is dropped.
     *
     * @param history what the outgoing layout knew, by point name
     */
    public void restoreVisitHistory(java.util.Map<String, Long> history)
    {
        if (history == null || history.isEmpty()) return;

        for (java.util.Map.Entry<String, Long> been : history.entrySet())
        {
            Point here = this.getPoint(been.getKey());

            if (here != null) this.lastArrival.put(recencyKeyOf(here), been.getValue());
        }
    }

    /**
     * Records an arrival at a point, for a test that needs a known visit history without driving a train.
     *
     * @param pointName the point that has just had a train
     */
    public void noteArrivalForTest(String pointName)
    {
        Point at = this.getPoint(pointName);

        if (at != null) this.lastArrival.put(recencyKeyOf(at), System.currentTimeMillis());
    }

    /** How many stations a route runs past; not the destination, which would add one to every candidate. */
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
     * Counted from thread start to exit, so isRunning() has no window between a thread's `while (running)` check and
     * its entry into activeLocomotives in which a graceful stop could report itself finished.
     */
    private final java.util.concurrent.atomic.AtomicInteger locomotiveThreads =
        new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Timetable entries alive, from before each thread starts until it leaves, plus the timetable's own dispatch loop.
     * Uncounted, a stopped run would read idle while an entry paused, and the entry would wake into the next run.
     */
    private final java.util.concurrent.atomic.AtomicInteger timetableEntries =
        new java.util.concurrent.atomic.AtomicInteger();

    /** Timetable pauses wait here so any stop wakes them.  Held only to wait or notify, never with another lock. */
    private final Object entryPause = new Object();

    /**
     * Held by the Yes across `running` cleared and the stop counted, and by a timetable start across `running` set and
     * the count read, so neither pair falls between the other's.  No journey takes it and neither holder waits inside
     * it except on `entryPause`, so the event thread making the Yes never waits behind a journey.
     */
    private final Object stopLock = new Object();
    
    // Volatile: set on the thread that loads or invalidates, read by every driving thread.
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
     * Locomotives that have locked a path but are not yet in activeLocomotives (seconds, while accessories are
     * thrown), so the cap counts them; claimed in the monitor that locks the path, and carrying it so getActiveAccs
     * sees the accessories a half-configured dispatch has thrown.
     */
    private final Map<Locomotive, List<Edge>> takingPath;

    /** The reversal answer each run was dispatched with, read only while its path is in activeLocomotives (OB-314). */
    private final Map<Locomotive, java.util.Optional<ReversalPolicy>> runPolicies = new ConcurrentHashMap<>();
    private final Map<Locomotive, List<Point>> locomotiveMilestones;

    /**
     * Edges of a running path the train has finished with, tail and all.  Separate from the lock, which under atomic
     * routes holds for the whole run: this answers "is a train still on it" (Adam: "once a train passes, signals on the
     * route, but behind the train, should still be allowed to be changed by auto routes.").
     */
    private final Map<Locomotive, Set<Edge>> clearedEdges;

    /**
     * Edges each running train has released early as its tail cleared them.  Not clearedEdges, which atomic routes
     * also fill but never release before the end: unlockPath needs to know what the run still holds.
     */
    private final Map<Locomotive, Set<Edge>> releasedEarly;
    private final Map<Locomotive, String> locomotivePendingS88;

    /**
     * The pending-s88 monitor, not the layout's: configureAndLockPath holds that for seconds, and a train blocked in
     * updatePendingS88 could cross the sensor it was about to wait for.  waitForS88Reached/updatePendingS88 are the
     * wait/notify pair; waitForTheRunToRecord waits here briefly, never while reading the feedback store.
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
    // Volatile: read by every running train at every sensor, written on the event thread while trains run.
    private volatile boolean atomicRoutes = true; // if false, routes will be unlocked as milestones are passed
    private boolean timetableCapture = false;

    // Staging plans are only valid executed one train at a time.  Set by loadReturnToHomeTimetable and
    // cleared by any other timetable load - see executeTimetable.
    private boolean timetableSequential = false;

    // The timetable a staging plan has borrowed while it occupies it, so a fold during Return Home writes the
    // railway's own.  Set and cleared with timetableSequential.
    private volatile List<TimetablePath> timetableOnLoan = null;

    /** Why the last timetable run stopped at an entry it cannot run, or null. */
    private volatile String timetableStoppedBecause;

    // Set for as long as executeTimetable is driving.  Capture records what the OPERATOR drives; a
    // timetable run recording itself appends to the very list being walked, and the dispatch loop
    // re-reads its own size.  Staging was only one of the two entrances into that.
    private volatile boolean timetableExecuting = false;

    // A staging entry that cannot run will not become runnable by waiting: nothing else is moving.  A
    // few retries ride out a sensor settling; beyond that the assumption is wrong and it must say so.
    /**
     * How long a timetable entry may stay unexecutable before the run gives up, in ms.  A time, not a count, since the
     * pause between attempts is the user's delay setting; minutes, since a parallel entry may wait long for its way.
     * Not final so a test can shorten it.
     */
    public static volatile long TIMETABLE_STUCK_MS = 180000;

    private static final int STAGING_MAX_ATTEMPTS = 3;
    private static final int STAGING_RETRY_PAUSE = 2000;

    // How often executeTimetable checks whether the run it dispatched has actually finished
    private static final int COMPLETION_POLL = 250;
    private int maxLatency = 0;
    private int maxActiveTrains = 0;

    // Trains on a journey sent by hand: the cap on trains out neither counts nor refuses them (behaviour.md 1).
    private final Set<Locomotive> sentByHand = Collections.synchronizedSet(Collections.newSetFromMap(
        new java.util.IdentityHashMap<Locomotive, Boolean>()));

    /**
     * Where each locomotive stood when this railway last turned it at a destination, by square; dropped when it
     * arrives without turning.  Kept here because nothing in this class may call the window, which drains it when the
     * railway goes idle.  Concurrent: drivers write, the event thread drains.
     */
    private final java.util.Map<String, String> reversedOnArrival =
        new java.util.concurrent.ConcurrentHashMap<>();
    
    // Route-related settings
    private boolean activateRoutes = false;
    private List<Integer> activateRouteIDs;

    // Track the layout version so we know whether an orphan instance of this class is stale
    // Volatile: written by whichever thread loads a layout, read by every driving thread.
    private static volatile int layoutVersion = 0;

    // Whether a staging flow owns this Layout, from the commit point until it unwinds.  On the model because guards
    // with no UI access ask it, and isRunning() alone reads a plan being derived as idle.
    private volatile boolean stagingInProgress = false;

    // This instance's version, fixed at construction.  Compared against layoutVersion to answer "am I
    // still the current Layout?" - see isCurrentLayout
    private volatile int version;

    // What the version counter is ticked under: a construction, a retirement and a load made current.
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
    
    /** Why a train cannot start, and why a station is not offered or reached. */
    private final LayoutExplanations layoutExplanations = new LayoutExplanations(this);

    /** Where each standing train's body lies: the tail walks behind the covered track and places. */
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
        // These four are read by the UI without the writers' synchronized(activeLocomotives) lock, so each must be
        // thread-safe; ConcurrentHashMap rejects nulls, so accessors taking a Locomotive null-guard first.
        this.locomotivesToRun = ConcurrentHashMap.<Locomotive>newKeySet();
        // ConcurrentHashMap: setCallback (e.g. opening the graph view) puts on the EDT while
        // executePath iterates callbacks.values() on loco threads - a plain HashMap would CME.
        this.callbacks = new ConcurrentHashMap<>();
        this.activeLocomotives = new ConcurrentHashMap<>();
        this.takingPath = new ConcurrentHashMap<>();
        this.locomotiveMilestones = new ConcurrentHashMap<>();
        // Written on locomotive threads, read by the UI and the route guard without a lock.
        this.clearedEdges = new ConcurrentHashMap<>();
        this.releasedEarly = new ConcurrentHashMap<>();
        // Copy on write: getTimetable hands out the list, read on the event thread while a train's capture appends.
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
        // Not while a timetable runs: the dispatch loop re-reads the list's size and would execute its own captures.
        if (!path.isEmpty() && loc != null && this.timetableCapture && !this.timetableExecuting)
        {
            timetable.add(new TimetablePath(loc, path, timestamp)); 
            
            // Calculate the delay time
            if (timetable.size() > 1)
            {
                TimetablePath second = timetable.get(timetable.size() - 1);
                TimetablePath first = timetable.get(timetable.size() - 2);

                // Stored on the later entry: the field is the delay BEFORE that entry runs.
                second.setSecondsToNext(second.getExecutionTime() - first.getExecutionTime());
            }
            
            return true;
        }
        
        return false;
    }
    
    /** Marks whether the arrival turns the train on the newest entry recorded for this train and path. */
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
     * Returns all accessories along active routes, including the exit guards each path's first edge sets (OB-340).
     *
     * Not synchronized: the event thread reads it while configureAndLockPath holds this monitor across its sleeps, so
     * taking it would freeze the window, Stop included.  Safe without it, as activeLocomotives is concurrent and its
     * lists are replaced whole; the answer may straddle one path being replaced.
     *
     * @return 
     */
    public Set<Accessory> getActiveAccs()
    {
        return new HashSet<>(getActiveAccSettings().keySet());
    }

    /**
     * Every accessory along an active route, with the setting its route holds it at: true for TURN and RED, as
     * RouteCommand.getSetting reads it; null where two routes underway ask different settings.  A path's first edge
     * also holds its station's exit guards at GREEN (OB-340).  Not synchronized, as for getActiveAccs.
     *
     * @return each accessory held, and the setting it is held at
     */
    public Map<Accessory, Boolean> getActiveAccSettings()
    {
        Map<Accessory, Boolean> activeAccessories = new HashMap<>();

        // Every train underway, including one still being dispatched: activeLocomotives misses it until
        // configureAndLockPath returns, its accessories already thrown.  activeLocomotives wins where both hold one.
        // The claims are read first, as in pathHeldBy: the hand-over writes the journey before dropping the claim, so
        // a train between the two reads is still seen.
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
                // Only the part the train has not finished with: not an edge given up (atomicRoutes off), nor one its
                // tail has provably passed (clearedEdges), so a turnout under the middle of a train is still refused.
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

    /** The exit guards of the station a path's first edge leaves, minus any the edge commands itself (OB-340). */
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

        this.runPolicies.remove(l);
        this.locomotiveMilestones.remove(l);
        this.clearedEdges.remove(l);
        this.releasedEarly.remove(l);

        // A claim left behind would lower the cap on trains out for the rest of the session.
        this.takingPath.remove(l);

        this.sentByHand.remove(l);

        // A route condition waiting on this sensor would wait forever; updatePendingS88 notifies any waiter.
        updatePendingS88(l, null);

        // TimetablePath holds the locomotive itself: left in, a run or a save would name a locomotive that is gone.
        this.timetable.removeIf(entry -> l.equals(entry.getLoc()));

        // Keyed by name: left behind, a new locomotive of the same name would inherit the deleted one's facing.
        if (l.getName() != null) this.reversedOnArrival.remove(l.getName());

        // Points hold their own references: a deleted locomotive would otherwise stay excluded, and be saved so.
        for (Point p : this.getPoints())
        {
            p.removeExcludedLoc(l);

            if (l.equals(p.getHomeLoc())) p.setHomeLoc(null);

            // Left standing here, Point.toJSON would save a locomotive the next load cannot resolve, failing the load.
            if (l.equals(p.getCurrentLocomotive())) p.setLocomotive(null);
        }

        // Otherwise the station stays claimed by a locomotive that is gone, and nothing placed there gets a home.
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

        // A split square may hold a positional home (MT-165; Adam: "unless there is an explicit home, the home should
        // be where the train started at startup."): the copy under the wheels is known, unlike for an assignment, and
        // HomeStaging.atHome treats every copy as home.

        // Already has a home: keep it.  Moving a locomotive by hand does not re-home it.
        if (this.homeStations.containsKey(l)) return;

        // Station already spoken for: a free agent.  By square, not Point (MT-165), or two trains would be homed on
        // one piece of track, which Return Home cannot satisfy.
        for (Point taken : this.homeStations.values())
        {
            if (taken.isSamePlaceAs(p)) return;
        }

        this.homeStations.put(l, p);
    }

    /**
     * Recomputes every home from the assignments and the current placements: one method for loading and editing, so
     * the two cannot derive homes differently.  Assignments win, whether or not the locomotive stands there; the rest
     * fall back to the station a locomotive stands on.  Synchronized on this Layout.
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
                // Dropped: one locomotive has one station, and keeping the loser would re-warn and re-save on every
                // load.  Only a hand-edited file gets here.
                this.control.logf("autolayout.warnHomeLocomotiveAssignedTwice", l.getName(), p.getName());
                p.setHomeLoc(null);
                continue;
            }

            // And one home per square, as claimHome: parseAuto writes homes onto the Points, so a file can carry two
            // locomotives on two copies of one square.  isSamePlaceAs is the block, so two squares sharing a detection
            // section are not caught; that sensor-versus-block question is open under MT-187.  Which assignment
            // survives follows the iteration order of points.
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

        // Then positional homes; claimHome refuses a locomotive with a home and a station spoken for.
        for (Point p : this.points.values())
        {
            if (p.getCurrentLocomotive() != null) this.claimHome(p.getCurrentLocomotive(), p);
        }
    }


    /**
     * Assigns a station to a locomotive by name, or clears the assignment when the name is null or blank.
     * Synchronized on this Layout.
     *
     * @param pointName the station
     * @param locName the locomotive, or null to clear
     * @throws java.lang.Exception if the station or the locomotive does not exist
     */
    synchronized public void setHomeLocomotive(String pointName, String locName) throws Exception
    {
        Point p = this.getPoint(pointName);

        if (p == null)
        {
            throw new Exception(I18n.f("autolayout.errorPointDoesNotExist", pointName));
        }

        Locomotive loc = locName == null || locName.trim().isEmpty()
            ? null : this.control.getLocByName(locName);

        if (locName != null && !locName.trim().isEmpty() && loc == null)
        {
            throw new Exception(I18n.f("autolayout.warnHomeLocomotiveNotInDatabase", locName,
                p.getName()));
        }

        // Nothing is refused here: a square is one logical point (Adam: "the home should just be the logical point, and
        // the direction is wherever the locomotive was facing when it started moving."), and a refusal at this door
        // would be walked past by parseAuto and claimHome.  An impossible assignment is warned about, not forbidden.

        // One station per locomotive, or two stations would wait for one train.
        if (loc != null)
        {
            for (Point other : this.points.values())
            {
                if (other != p && loc.equals(other.getHomeLoc())) other.setHomeLoc(null);
            }
        }

        // And one home per square: two copies are two Points, each with its own homeLoc.  Displaced, not refused: the
        // operator is saying this train lives here now.
        if (loc != null)
        {
            for (Point other : this.points.values())
            {
                if (other != p && other.isSamePlaceAs(p)) other.setHomeLoc(null);
            }
        }

        p.setHomeLoc(loc);

        // The copy is the facing, and the operator chose this one (OB-282; Adam: "it should accomplish the facing").
        p.setHomeFacingFixed(loc != null);

        this.rebuildHomeStations();
    }

    /** Drops every assignment, so homes come purely from where trains stand.  Synchronized on this Layout. */
    synchronized public void clearHomeLocomotives()
    {
        for (Point p : this.points.values())
        {
            p.setHomeLoc(null);
        }

        this.rebuildHomeStations();
    }

    /**
     * Whether any station has been assigned a home locomotive.  Synchronized on this Layout.
     * @return true if any has
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
     * Every locomotive that has a home, and where.  A copy taken under this Layout's monitor, since the map is
     * rebuilt and written from several threads.
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
     * Where each train under way is last known to be: the last station on its path whose sensor it has tripped, or
     * where it set off.  What a reload that stops the trains has to carry, since a locked path holds every point on
     * it.  Points with no sensor or no station are never a position; no two trains are kept on one square (keptApart).
     *
     * Read without the railway's monitor, which the event thread must not take: the maps are concurrent.
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

        return this.keptApart(out);
    }

    /**
     * Keeps trains under way apart: none on a standing train's square, no two on one square (any copy).  A train that
     * cannot keep its point takes the first station ahead it holds that is free, or one whose holder can itself move
     * on, along the chain; with nothing left it stays.  Ties go by name, not map order.
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
            if (!this.movedOn(loc, read.get(loc), standing, keptThere, out, new HashSet<Locomotive>()))
            {
                out.put(loc, read.get(loc));
            }
        }

        return out;
    }

    /**
     * Moves a train on to a station ahead that it holds: a free one, or one whose kept train can itself move on, along
     * the chain.  Nothing changes unless the whole chain moves; `moving` stops the chain coming round to itself.
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

    /** The square a point is part of: the block its copies share, or the point itself where it has none. */
    private static Object placeOf(Point point)
    {
        return point.getBlock() != null ? "block:" + point.getBlock() : point;
    }

    /** The stations after this point on the train's held path that it still holds, in path order, destination last. */
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

    /** The last milestone after the start that is a station whose sensor was tripped, else the start; may be null. */
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
     * Retires every Layout, so none is current and a path's thread on any of them stops its train at its next check.
     * Loads retire the railway they replace by building a newer one; Unload, replacing it with nothing, calls this.
     */
    public static void retireEveryLayout()
    {
        synchronized (VERSIONS)
        {
            Layout.layoutVersion += 1;
        }

        // Every journey of a retired railway asks again whether to go on waiting: one left waiting on a sensor its
        // stopped train will not reach would stop whichever train next occupied it.
        Locomotive.wakeEveryWait();
    }

    /**
     * Makes this the current Layout, retiring every other.  The parse builds this first and the model takes it after,
     * so a lookup landing between would otherwise build a newer, empty railway and retire this one.
     */
    public void makeCurrent()
    {
        synchronized (VERSIONS)
        {
            Layout.layoutVersion += 1;
            this.version = Layout.layoutVersion;
        }

        // As in retireEveryLayout.
        Locomotive.wakeEveryWait();
    }

    /**
     * Whether this Layout is still the one in use, or has been superseded by a newer one.
     *
     * Unlike a version captured when a run began, the answer does not depend on when it is asked.
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

    /** Why this layout was invalidated; not lastError, which every later failure overwrites. */
    private String invalidReason;
    
    /**
     * Marks the layout state as invalid
     * Prints and logs the error
     * @param message
     */
    public void invalidate(String message)
    {
        this.isValid = false;

        // Every reason, first first (OB-266): later refusals often follow from the first, which is the cause.
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
     * later announcement on the same sensor: two consecutive path points can share one s88, and a stale clear landing
     * after the next announcement would stall the waiter for good.  Each clear stands down if a later announcement
     * re-armed its sensor.
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
        // Clears run detached for up to maxDelay seconds, so a reload may have replaced this Layout; an orphan's
        // epoch map is not the new run's, so its stand-down check would pass and clear a sensor the new run waits on.
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

    /** @return why the last timetable run stopped short, or null */
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
     * Whether full autonomy is running: the railway choosing its own destinations.  Not isAutoRunning, which a
     * timetable (Return Home included) also sets; behaviour.md 1 puts a timetable with manual.  Nothing asks it today.
     *
     * @return true while autonomy is dispatching and no timetable is
     */
    public boolean isFullAutonomyRunning()
    {
        return this.running && !this.timetableExecuting;
    }
    
    /** Stops ordered by stopEveryTrainWhereItIs; each journey reads it at dispatch and around every speed it writes. */
    private final java.util.concurrent.atomic.AtomicInteger stopsOrdered = new java.util.concurrent.atomic.AtomicInteger();

    /** Whether this railway has been retired: what a journey's sensor waits give up on. */
    private final java.util.function.BooleanSupplier retiredRailway = () -> !this.isCurrentLayout();

    /**
     * Stops every train where it is, now: the reload's and Unload's Yes.  Unlike the graceful stopLocomotives, it also
     * counts a stop, which every journey checks before and after each speed it writes (`drive`), so no journey chosen
     * before it moves on; then every train under way stops.
     *
     * Takes only `stopLock`, never the journeys' lock or this monitor: the event thread calls it, and an arrival holds
     * the journeys' lock while it waits for this monitor.
     */
    public void stopEveryTrainWhereItIs()
    {
        // `running` cleared, then the stop counted, under stopLock: a timetable starting sees both or neither.
        synchronized (this.stopLock)
        {
            stopLocomotives();

            this.stopsOrdered.incrementAndGet();
        }

        for (Locomotive active : this.activeLocomotives.keySet()) active.setSpeed(0);
    }

    /**
     * How many times every train has been stopped where it stood; a journey carries the count from its dispatch, and
     * one chosen before a stop sends nothing after it.
     *
     * @return the stops counted so far
     */
    public int stopsOrdered()
    {
        return this.stopsOrdered.get();
    }

    /**
     * The other train whose journey still waits on this sensor, or null: a claimed or running route waits on every
     * sensor it has not reached, and keeps one it has reached until the rail to it is given back.
     */
    private Locomotive whoseJourneyAwaits(String s88, Locomotive asking)
    {
        if (s88 == null) return null;

        // Claims first, journeys second: the hand-over writes the journey before dropping the claim, so a train
        // handing over between the two reads is still seen.
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

                // Held until the rail to it is given back, not only until the head reaches it: a gap between two axles
                // reads clear.
                boolean reachedIt = reached != null && reached.contains(e.getEnd());
                boolean released = givenBack != null && givenBack.contains(e);

                if (!reachedIt || !released) return journey.getKey();
            }
        }

        return null;
    }

    /**
     * Writes a journey's speed unless a stop was ordered since its dispatch or its railway retired; asked again once
     * the command has gone, so a stop ordered meanwhile puts the train back to a stand.  Returns whether it stands.
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
     * Gives back a route a stop has overtaken before the train moved, as handleMisconfiguredPath does: track and
     * squares released, the train reserved where it stands.  `why` is the log key naming the train.
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

        // A hand send answers a false with "check log".
        this.control.logf(why, loc.getName());
    }

    /**
     * Stops locomotives gracefully (i.e., at their next station for those that are running)
     */
    public void stopLocomotives()
    {
        this.running = false;

        // Wakes every timetable pause to leave; `running` is cleared first and a pause checks it under the monitor, so
        // no wake is lost.
        synchronized (this.entryPause)
        {
            this.entryPause.notifyAll();
        }
    }

    /** Set by the first train to find the track power off, so the wait is logged once; cleared when it reads on. */
    private final java.util.concurrent.atomic.AtomicBoolean waitingForThePower =
        new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Waits while the track power is off, before a run chooses or sends a train, so no switch or speed is sent to a
     * dead railway.  Reads on until a GO or STOP echo is heard; Instant Stop leaves it alone (OB-251).  It sleeps, so
     * it is never called holding this monitor or `activeLocomotives`; a stop or retirement ends it within a poll.
     *
     * @return whether it waited, so the caller asks again whether its run still goes
     */
    private boolean waitWhileThePowerIsOff()
    {
        boolean waited = false;
        boolean interrupted = false;

        while (this.running && this.isCurrentLayout() && !this.control.getPowerState())
        {
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
                // Put back on the way out: re-armed here, the next sleep would throw at once and spin.
                interrupted = true;
            }
        }

        if (this.control.getPowerState()) this.waitingForThePower.set(false);

        if (interrupted) Thread.currentThread().interrupt();

        return waited;
    }

    /** A timetable's pause between refusals, which any stop cuts short so the entry cannot wake into the next run. */
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

    /** pacedWait's pause: a random time between the operator's delays, or the poll where both are zero. */
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

        final java.util.concurrent.atomic.AtomicInteger started =
            new java.util.concurrent.atomic.AtomicInteger();

        // No signal sweep here (OB-166, Adam: "Signals should only be touched when a route activates"): a sweep would
        // turn green a signal a route had left red.  A train placed by hand at a protected platform keeps its aspect
        // until a route activates over it.
        
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
                // Skipped here, not left to runLocomotive's throw, which the catch below answers by invalidating the
                // whole layout.
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

        // Not running if nothing runs: `running` was set first and both skips return without a thread, so nothing
        // would ever clear it.  Only for a non-empty list: tests start an empty one to say "autonomy is on".
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
     * Synchronized on this Layout, as every door that changes the graph is.
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
     * lastError is set either way; the flag silences the log line for callers enumerating candidate paths, where a
     * refusal is the ordinary answer.
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
     * Whether this locomotive is already out: running, or claiming a route it has not set off on yet.  A dispatch must
     * refuse both, since a locomotive joins activeLocomotives only after configureAndLockPath returns, seconds later,
     * and a second dispatch in that window would drive one train from two threads.
     *
     * @param loc the locomotive
     * @return whether a dispatch of it should be refused as already under way
     */
    public boolean isAlreadyUnderway(Locomotive loc)
    {
        // Claims first: the hand-over writes the journey before dropping the claim.
        return this.takingPath.containsKey(loc) || this.activeLocomotives.containsKey(loc);
    }

    /**
     * How many distinct trains are out, counting those that have claimed a route but not set off.  Not trains sent by
     * hand, which the cap neither counts nor refuses.
     */
    private int trainsUnderway()
    {
        Set<Locomotive> both = Collections.newSetFromMap(
            new java.util.IdentityHashMap<Locomotive, Boolean>());

        // Claims first, as in isAlreadyUnderway.
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

    /** The same, saying whether a person would send it by hand, which the cap on trains out does not refuse. */
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

            // Excluded intermediate points cannot be traversed; a station's exclusion says only that the train may not
            // stop there (see HomeStaging.canEnter).  Asked of the square, since a station's barred-side copy is no
            // destination itself.
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

            // Nor a sensor another train's journey still waits on: the first occupation ends every wait on it, so two
            // places on one sensor (OB-238) would otherwise take two trains at once.
            Locomotive awaiting = whoseJourneyAwaits(e.getEnd().getS88(), loc);

            if (awaiting != null)
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorFeedbackAwaitedByAnotherTrain", e.getEnd().getS88(), awaiting.getName())
                );
                return false;
            }

            // No lock edge another route is running over.  Run over, not held (OB-269): the write marks a path's lock
            // edges and this reads a candidate's, so asking "held" would reach two hops and refuse a candidate that
            // shares no rail with the path.  isLockHeld stays for protecting a held throat's accessories.
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

            // And the same rail the other way (OB-269): its two directions are two Edges that deriveLocks deliberately
            // does not lock against each other.  Found by place, as deriveLocks does, since the two directions run
            // between different copies of the squares.
            for (Edge sameRailBack : this.edges.values())
            {
                if (sameRailBack == e) continue;

                if (sameRailBack.getStart() == null || sameRailBack.getEnd() == null) continue;

                if (!sameRailBack.getStart().isSamePlaceAs(e.getEnd())) continue;

                if (!sameRailBack.getEnd().isSamePlaceAs(e.getStart())) continue;

                if (!sameRailBack.isRunOver(loc)) continue;

                // A train never blocks itself (behaviour.md 5c): Return Home holds the rail its earlier legs took.
                if (railHeldByThisTrain(sameRailBack, loc)) continue;

                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorLockEdgeOccupied", placeNameOf(sameRailBack))
                );

                return false;
            }
        }
        
        // An inactive intermediate point may never be driven through, whatever asked for the route (Adam: "Inactive
        // really means nothing can pass").  Not fenced behind isAutoRunning: a manual route may still start there.
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

        // The station's own capacity is asked with the track room further down (MT-262), after the inactive and
        // covered-track rules, so a berth both switched off and too short reports as switched off.

        // At every door, not only under autonomy (Adam: "In manual mode, inactive endpoints and intermediates should
        // be refused as well.  Just not inactive start points.").  The start stays exempt: that is how a train is
        // driven out of a square closed around it.
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

        // No terminus rule here (Adam: "In manual operation, non reversing trains must be able to back into a terminus
        // if the graph makes that possible."): pickPath filters at selection, and HomeStaging keeps a stricter rule.

        // A train too long for the track it would reverse into, where that track is measured (Adam: "such paths need
        // to be disallowed if any track (not station) lengths are set and we reverse over a switch."); on a built
        // configuration the room counted is the stretch after the last switch (roomAtTheEnd).
        //
        // And nothing runs over track a standing train lies across (Adam: "If a train protrudes far behind where it
        // is standing, those edges it reaches need to be considered blocked..."), which point occupancy cannot see.
        Map<Edge, Locomotive> coveredTrack = new LinkedHashMap<>();

        Map<String, Locomotive> coveredPlaces = new LinkedHashMap<>();

        // Every train but this one: the maps keep one train per place, and this train's own claim could hide another's.
        walkStandingTrains(coveredTrack, coveredPlaces, other -> !other.equals(loc));

        for (Edge e : path)
        {
            // Only other trains' tails are in the maps (OB-285): a train turned where it stands leaves over its own
            // tail, and finding it there would hide another train's tail on the same stretch.
            Locomotive lyingAcross = coveredTrack.get(e);

            // And the track it shares with something covered: a train across a switch covers the edge it arrived on,
            // and lock edges (derived from shared tiles) name every edge sharing metal with it.
            if (lyingAcross == null)
            {
                for (Edge sharing : e.getLockEdges())
                {
                    Locomotive onShared = coveredTrack.get(sharing);

                    if (onShared == null) continue;

                    // Shared metal only: the builder also adds one-way lock edges for FR-001 restrictions, which share
                    // none.  Told apart by symmetry; two stations holding each other back would be refused here.
                    if (!sharing.getLockEdges().contains(e)) continue;

                    // And only over the part the tail lies on (OB-207), where both edges are described in places; the
                    // direct case above stays whole, since a path uses all of its own edges.
                    if (!e.getPlaceIds().isEmpty() && !sharing.getPlaceIds().isEmpty()
                        && !tailLiesOn(e, onShared, coveredPlaces))
                    {
                        continue;
                    }

                    lyingAcross = onShared;

                    break;
                }
            }

            // And every other copy of the same metal: the rail into a turning square is emitted twice over the same
            // places, and the copies are not lock partners (OB-280).  An edge with no places keeps the answers above.
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

        // A train too long for the track it would stand on, at every destination and in every tier (MT-262): a berth
        // that cannot hold a train is not a preference.  measuredRoomAtTheEndOf is the one walk, shared with the
        // staging planner, so a plan cannot offer a berth this then refuses.
        String tooLong = whyTooLongForThisRoute(path, loc);

        if (tooLong != null)
        {
            logPathError(loc, path, logFailures, tooLong);

            return false;
        }

        // And a parking berth may not block a road (Adam's ruling); whyABerthCannotHoldIt is pure, so the planner asks
        // it too.
        String foulsARoad = whyABerthCannotHoldIt(path, loc);

        if (foulsARoad != null)
        {
            logPathError(loc, path, logFailures, foulsARoad);

            return false;
        }

        // And not round a loop into its own tail (OB-294): the checks above pass a train's own tail.
        String ownTail = whyItWouldMeetItsOwnTail(path, loc);

        if (ownTail != null)
        {
            logPathError(loc, path, logFailures, ownTail);

            return false;
        }

        // A square held back while another has a train standing on it (FR-001), for every square the route arrives at
        // (OB-295; Adam: "It means trains shouldn't be sent to THIS square while trains are STANDING ON or hold a lock
        // on the other specified station(s).").  The route half is lock edges (Edge.isLockHeld).  Two restrictions set
        // against each other hold both trains until one is cleared (behaviour.md 1).  In every tier; HomeStaging
        // applies it too, against the occupancy its plan has reached (auditAgainstRuntime proves they agree).
        {
            // The train leaving the watched point is exempt (Adam: "The condition should not apply to trains leaving -
            // only departing."), or the one move that clears the condition would be forbidden.  The window that
            // explains a refusal asks the same method.
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
     * Orders accessory commands releases first, throws after, alphabetically within each: a three-way turnout is two
     * drives, and throwing the diverging one before the other is released routes nowhere.
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

        // Released before thrown (see releasesBeforeThrows), for every edge: a turnout pair cannot be recognised here.
        // Both commands of one turnout must sit on one edge, since edges are configured in path order.
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

                // Under the accessory's signal lock, as the exit guards are (see signalLocks).
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

        // Not while the railway uses the graph, as in renamePoint: this removes a point from under live visited sets.
        // The menu greys at popup time, not click time; staging counts too, since the planner walks these structures.
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
        
        // Releases any home claim on it, or Return Home would keep naming a station that no longer exists.
        this.homeStations.values().removeIf(home -> home != null && home.getName().equals(name));

        // And every other point watching this one (OB-080): a ghost blocker left in blockedBy would refuse its station
        // for the session the moment a stale reference occupied it.  The occupant goes too.
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

        // Not while the railway uses the graph, as in deletePoint.
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

        // The preconditions live here, not only in the dialog: points.put would overwrite a point of the new name, and
        // a rename mid-run mutates Point.hashCode under live visited sets.
        if (!newName.equals(name) && this.getPoint(newName) != null)
        {
            throw new Exception(I18n.f("autolayout.errorPointAlreadyExists", newName));
        }

        // isRunning, not isAutoRunning: it covers the graceful-stop wind-down; staging, since the planner walks these
        // structures.
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
     * Follows a locomotive rename in the record of which train the railway turned where, which is keyed by name.
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
     * What the turned-on-arrival record holds for this train, for tests.
     *
     * @param name the locomotive
     * @return the square it was turned at, or null
     */
    public String turnedOnArrivalAt(String name)
    {
        return name == null ? null : this.reversedOnArrival.get(name);
    }
    /**
     * The locomotives turned round at their destination since this was last asked, each against the Point it stood on
     * when it turned; drained, so the caller puts back what it could not write.  Removed by key and value, so a turn
     * recorded meanwhile by a driver thread survives.
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
     * The free plain copy of the same square that a train coming in by this path's last leg would have driven onto, or
     * null (a real terminus, or no plain copy for this approach).
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

            // Reachable from the same approach: the copy this train would have driven onto.
            if (this.getEdge(cameFrom.getName(), sibling.getName()) == null) continue;

            if (sibling.getCurrentLocomotive() != null) continue;

            return sibling;
        }

        return null;
    }

    /**
     * Moves a train that declined to turn off the turning copy of its square onto the plain copy its approach reaches,
     * since the two copies face opposite ways.  The tail comes with it: the carriages have not moved.  A compulsory
     * turn has no plain copy, so the train stays.
     */
    private void standOnTheCopyItDidNotTurnOn(Point arrived, Locomotive loc, List<Edge> path)
    {
        if (arrived == null || loc == null || path == null || path.isEmpty()) return;

        // A real terminus passes this test too; it has no plain sibling, so the search below finds nothing.
        if (!arrived.isTerminus() && !arrived.isReversing()) return;

        // Asked ahead of the arrival too, by copyItWillStandOn (OB-314).
        Point sibling = plainSiblingFor(arrived, path);

        if (sibling != null)
        {
            String tail = arrived.getArrivedFrom();

            // Read before the move, which clears the side and the route on the copy it leaves.
            java.util.List<Edge> along = arrived.getArrivedAlong();

            // Onto the plain copy first, and only then off the turning one: setLocomotive sweeps the train off every
            // other copy, so the square would read empty for a moment and another thread's claim could command the
            // exit guard green over the standing train.  After the unlock, so the sweep does not take this run's
            // reservations.
            sibling.reserve(loc);

            sibling.setArrivedFrom(tail);

            // The route as read before the move (MT-335).
            sibling.setArrivedAlong(along);

            clearLocomotiveExcept(loc, sibling);

            // The station, not the copy (see placeNameOf).
            this.control.logf("autolayout.log.stoodOnTheCopyItFaces", placeNameOf(arrived));

            return;
        }

        // Nothing to move it to: right for a real terminus, but on a may-turn square the operator's "keep the
        // direction" was not honoured (a may-turn dead end has only the turning copy), so it is said.
        if (!mustTurnAt(arrived))
        {
            this.control.logf("autolayout.log.noPlainCopyToStandOn", placeNameOf(arrived));
        }
    }

    /** What a person calls the track an edge runs over: "From -> To" by square, without the builder's headings. */
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
     * A Point's name as a place, without the builder's arrival suffix (Adam: "the whole copy thing needs to be masked
     * from the user").
     */
    static String placeNameOf(Point point)
    {
        if (point == null) return "";

        return withoutArrivalSuffix(point.getName());
    }

    /** The words `AutonomyBuilder.heading` names a square's copies by, which this package cannot call. */
    private static final String[] ARRIVAL_HEADINGS = {"northbound", "southbound", "eastbound", "westbound"};

    /**
     * An emitted Point name without the builder's arrival suffix: " (northbound)", or " (northbound, reverse)", at the
     * very end.  A name ending in anything else, "Yard (old)" say, is left alone.
     *
     * @param name an emitted Point name, or null
     * @return the square's name, as far as it can be recovered; null for null
     */
    public static String withoutArrivalSuffix(String name)
    {
        if (name == null || !name.endsWith(")")) return name;

        int open = name.lastIndexOf(" (");

        if (open <= 0) return name;

        String inside = name.substring(open + 2, name.length() - 1);

        int comma = inside.indexOf(',');

        String heading = comma < 0 ? inside : inside.substring(0, comma);

        for (String known : ARRIVAL_HEADINGS)
        {
            if (known.equals(heading)) return name.substring(0, open);
        }

        return name;
    }

    /**
     * Whether a train arriving on this turning copy from that Point has no copy of its square to stand facing on: no
     * copy that neither turns nor reverses trains is reachable from there.  The end of a line marked "may change
     * direction" is one; kept facing on there, a train would stand facing back out.
     *
     * @param arrived the copy the train arrives on
     * @param cameFrom the Point its last leg starts from
     * @return whether no copy faces on from that approach
     */
    public boolean noCopyFacesOnFrom(Point arrived, Point cameFrom)
    {
        if (arrived == null || cameFrom == null) return false;

        if (!arrived.isTerminus() && !arrived.isReversing()) return false;

        // A copy with no block counts: the build gives a single-copy square no block.

        for (Point sibling : this.points.values())
        {
            if (sibling == arrived || !arrived.isSamePlaceAs(sibling)) continue;

            if (sibling.isTerminus() || sibling.isReversing()) continue;

            if (this.getEdge(cameFrom.getName(), sibling.getName()) != null) return false;
        }

        return true;
    }

    /** noCopyFacesOnFrom asked of every Point an edge reaches this copy from; false for a copy nothing reaches. */
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
     * The copy of its square a train that turned on arriving here is stood on: one facing the way it came in,
     * preferring a destination, then one that does not turn trains, as AutonomySession.copyFacing chooses.  Null if
     * the square is not split or no copy faces that way.
     */
    private Point copyAfterTheTurn(Point arrived, String cameInBy)
    {
        if (arrived == null || cameInBy == null || arrived.getCopyFacing() == null) return null;

        // Already facing that way, on a copy trains may arrive at: it stays.
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
     * Moves a train that has just turned onto the copy of its square it now faces on from, as
     * standOnTheCopyItDidNotTurnOn does (onto it first, so the square never reads empty), and points the turn record
     * at it.
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

        // Nothing logged: the turn itself is, and the copy is bookkeeping the operator cannot see.
    }

    /**
     * The path a timetable entry runs from where its train stands: as recorded if it stands on the start copy; if it
     * stands on another copy of that square facing the same way, with the start copy free, the same path with its
     * first edge swapped for that copy's edge to the same next Point; otherwise null.  The train is not moved onto the
     * start copy, since its tail is walked from the rails of the copy it stands on.
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

    /** Whether every copy of this square turns trains, so turning there was compulsory, not the operator's choice. */
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
     * Puts back reversals that were drained but not written.  putIfAbsent, so a newer record of a train that has since
     * arrived elsewhere is not overwritten.
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

    /** configureAndLockPath, keeping the run's reversal answer with the claim (see copyItWillStandOn). */
    private boolean configureAndLockPath(List<Edge> path, Locomotive loc, ReversalPolicy reversals)
    {
        // Lock the path and send the accessory commands under the Layout monitor.  Holding it here is
        // fine - path locking must be atomic - but the validation wait below must NOT hold it, so other
        // locomotives' path checks are not blocked for the (possibly multi-second, scales with path size -
        // see validatePathActuation) validation wait.
        boolean configureFailed = false;
        int edgesLocked = 0;

        // A throw out of the lock loop, rethrown once the track is given back: executePath's handler does not unlock
        // (right mid-run), but here the train has not moved, and the edges taken would stay occupied for the session.
        RuntimeException lockFailure = null;

        synchronized (this)
        {
            // Already out, asked again in the monitor that takes the claim: executePath's outer check can be seconds
            // stale, and two dispatches of one train on disjoint routes would both lock.
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

            // The run's reversal answer, kept with its claim so the copy the train will stand on is known while its
            // route is set.  Past the refusals, so a losing duplicate writes nothing; before the route, so a reader
            // that
            // finds the route finds this with it.
            this.runPolicies.put(loc, java.util.Optional.ofNullable(reversals));

            // Claimed in the same monitor as the cap check, so another thread's check cannot fall between them.
            this.takingPath.put(loc, path);

            try
            {
            for (Edge e : path)
            {
                // Counted before it is taken, so a throw out of setOccupied or reserve still falls inside the release
                // of path.subList(0, edgesLocked).  setOccupied increments first, so only untaken lock siblings could
                // be over-released, and nothing throws inside that loop.
                edgesLocked++;

                e.setOccupied();

                // Reserved, not placed: a locked path holds all its points for this train, so a junction behind it
                // stays reserved; setLocomotive would sweep the train off each point as the next is reserved.
                e.getEnd().reserve(loc);

                // isPathClear already previewed the configuration, so this should not fail - but if an
                // accessory went missing in between, the locomotive must not be released onto a path we
                // were unable to set up.  Stop here and let the caller below release the locks.
                if (!this.configureEdge(e, null))
                {
                    configureFailed = true;
                    break;
                }

                // And the first edge sets the exit guards of the station it leaves green (OB-340), each under its
                // signal's lock (see signalLocks).
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
                // Released outside the monitor, like the configureFailed path below.
                lockFailure = e;
            }
        }

        if (lockFailure != null)
        {
            // As a configuration failure does, and only the edges actually taken: releasing others clears lock edges
            // that may now be another train's.  Not with none taken: handleMisconfiguredPath would throw on the empty
            // list.
            if (edgesLocked > 0)
            {
                this.handleMisconfiguredPath(path.subList(0, edgesLocked), loc);
            }

            this.takingPath.remove(loc);

            // Rethrown, not turned into false: nobody can act on a fault reported as "the path was occupied".
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
                    // Interrupted: ask rather than assume everything actuated (OB-080); allConfirmed reads state that
                    // has already arrived.  The interrupt is preserved.
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

        // Guarded, so working out what to say cannot stop the release below: from the lock loop's failure handler, the
        // failing call may be the same one.
        try
        {
            for (Edge e : path)
            {
                for (String name : e.getConfigCommands().keySet())
                {
                    Accessory acc = control.getAccessoryByName(name);
                    accessorySetting state = e.getConfigCommands().get(name);
                    boolean d = Accessory.isThrow(state);

                    // An accessory missing from the database is named too.
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

            // Provably at its start, even on a path looping back through it.  Re-reserved, not placed, so a train on
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
        // The claim on a slot goes with the path, or a lock-and-unlock without a run would lower the cap for good.
        this.takingPath.remove(loc);

        // The road follows what this run did, not the setting now: a run that gave edges back early may have had them
        // taken by another train since, and the setting can be switched on under it.  By releasedEarly, not
        // clearedEdges, which atomic mode fills without releasing anything.
        Set<Edge> givenBackEarly = this.releasedEarly.get(loc);

        boolean heldItAll = givenBackEarly == null || givenBackEarly.isEmpty();

        List<Edge> output = new LinkedList<>();

        for (int i = 0; i < path.size(); i++)
        {
            Edge e = path.get(i);

            if (this.atomicRoutes && heldItAll)
            {            
                // The edge given back first, so the start's guards are asked without this path holding them (OB-340).
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
                    Set<Edge> alreadyGivenUp = this.releasedEarly.get(loc);

                    if (alreadyGivenUp != null && alreadyGivenUp.contains(e))
                    {
                          // Already released, lock edges and all, when the tail passed it (OB-164); occupancy counts,
                          // so a second release would take away another train's claim.
                    }
                    else
                    {
                        e.setUnoccupied();
                    }
                }
                else
                {
                    output.add(e);

                    // Another locomotive has taken the end point, so the edge's own flag may be its lock now; only the
                    // lock edges are released, unless the tail already gave this edge up with them (OB-164).
                    // Occupancy counts, so every claim must be given back exactly once.
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
     * Whether the track connects these two squares at all, by any route, including through a terminus or round a lap
     * that no train may take.  For explaining why a train cannot go (firstClearOrWhyNot) and for
     * HomeStaging.auditAgainstRuntime; nothing that sends a train asks it.
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
     * The same search; throughTermini asks about the track rather than a route a train may take, so Why Not Moving?
     * can tell "a terminus is in the way" from "no track leads there".
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
        
        // Unless asking about the track alone, searched by square and switch settings, so a shorter route that sets a
        // switch both ways cannot hide a clear one (see bfsBySettings).
        //
        // Below, marked on dequeue, not enqueue: finding a route that is not excluded needs a point reached by several.
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
                // Not on through a terminus (OB-229), nor through another copy of the square it starts or ends at
                // (Adam: "We need to refuse both.  A copy makes a cycle."), except when asking about the track.
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
    
    /** One step of bfsBySettings; commands is null once two of the route's switch commands have disagreed. */
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

    /** bfs by square and the switch settings on the way; see the note in bfs. */
    private List<Edge> bfsBySettings(Point start, Point end, List<List<Edge>> excludePaths)
    {
        // Marked when taken off the queue, as in bfs, so excluded routes of equal length still lead on.
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

                // Never through a terminus that is not the end (OB-229), nor round onto another copy of either end
                if (next.getEnd().isTerminus() || next.getEnd().isSamePlaceAs(start)
                    || next.getEnd().isSamePlaceAs(end)) continue;

                // Passed over when this square was left already under settings that leave as much freedom.  A route
                // already in conflict keeps the square-alone rule: it is followed only so Why Not Moving? can name the
                // conflict, and must not go round loops.
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
     * The rule (Adam's): in full autonomy a train is only ever reversed at a terminus.
     *
     * Asked where full autonomy chooses (pickPath, its probe, and the "why is it not moving" explanation), not by
     * isPathClear, so a hand-driven move and the staging planner may still use a headshunt.
     *
     * Only the end of each edge is tested: a train may leave the reversing point it stands on.
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
     * getPossiblePaths answers the manual question, which includes destinations pickPath never chooses; yielding on it
     * would stop every running train for a dispatch that never happens.
     *
     * pickPath cannot be the probe (it sleeps when it finds nothing), so its candidates' clauses are mirrored here.
     *
     * Anything added to pickPath's selection belongs here too.
     */
    private boolean hasAutonomousDestination(Locomotive loc)
    {
        // uniqueDest false: one path per start/end pair could keep the one crossing a berth where pickPath would find
        // another.  Each terminus asked once, however many routes end there.
        java.util.Map<Point, Boolean> leaves = new java.util.HashMap<>();

        for (List<Edge> path : this.getPossiblePaths(loc, false))
        {
            Point end = path.get(path.size() - 1).getEnd();

            // The shared clauses through isSendableDestination, mirroring pickPath; getPossiblePaths filters on
            // isDestination only, so inactive, reversing and unchosen candidates are refused here.
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
                // Nothing chosen or sent while the track power is off; once it is back, ask again whether the run goes.
                if (this.waitWhileThePowerIsOff()) continue;

                // The stops counted before the choice: a Yes landing after the running check below still binds the
                // journey.
                final int stopsAtChoice = this.stopsOrdered.get();

                List<Edge> path = this.pickPath(loc);

                // And still running once the choice is made: choosing waits on the monitor, and a stop meanwhile sends
                // nothing.
                if (path != null && running)
                {
                    // Guarded: executePath's handler does not unlock a failed path, so an escaping exception would kill
                    // this thread with its track held.
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

                // Not after a stop: it would only delay the thread exiting, which isRunning() waits for.
                if (running) loc.delay(this.getMinDelay() * 1000);

                // If another locomotive is falling behind, attempt to yield to it
                if (this.isAutoRunning() && this.maxLocInactiveSeconds > 0)
                {
                    Locomotive yieldLoc = this.checkForSlowerLoc(this.maxLocInactiveSeconds, loc);

                    // running re-checked here: after a stop no train is going to move, and this waits up to
                    // YIELD_SECONDS.
                    if (yieldLoc != null && running)
                    {
                        yieldLoc.blockUntilMotion(YIELD_SECONDS);
                    }
                }                   
            }
            }
            finally
            {
                // In a finally, or a thread killed by anything leaves the layout running for ever.
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
     * Where a train's body is (MT-438): its last milestone, else the start of the path it holds, else the Point
     * holding it.  Ask this when you mean "where is the train"; getLocomotiveLocation answers "which Point holds it",
     * and during a run a locked path holds several, in arbitrary order.  For a standing train the two agree.
     *
     * @param loc the locomotive
     * @return where its body is, or null when nothing holds it
     */
    public Point whereTheTrainIs(Locomotive loc)
    {
        Point reached = this.lastMilestone(loc);

        if (reached != null) return reached;

        // Then the start of the road it holds: between the lock and the first sensor a train has reserved its whole
        // path and reported nothing.  Claims first (see pathHeldBy).
        List<Edge> given = this.pathHeldBy(loc);

        if (given != null && !given.isEmpty() && given.get(0).getStart() != null)
        {
            return given.get(0).getStart();
        }

        return this.getLocomotiveLocation(loc);
    }

    /** The last Point a run has reported reaching, or null (MT-438). */
    Point lastMilestone(Locomotive loc)
    {
        if (loc == null) return null;

        // Through the accessor, not the field: DiagramMonitor's test double overrides getReachedMilestones.
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

        // Then by priority, with a stable sort, so the shuffle survives within a band.  The band gate below reads this
        // order, so nothing may reorder `ends` after it.
        Collections.sort(ends, (Point p1, Point p2) ->
        {
            // Equal, always, for the rule that ignores priority (OB-156), so the shuffle survives untouched.
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
                // The best route so far within one band of station priority: priority still wins between bands.
                List<Edge> best = null;
                int bestCost = Integer.MAX_VALUE;
                Integer band = null;

                for (Point end : ends)
                {
                    // The band gate: a band is settled before the next is looked at, so priority is absolute; not for
                    // BALANCED_PRIORITY, which weighs priority against distance.
                    if (this.pathPreference != PathPreference.BALANCED_PRIORITY
                        && best != null && band != null && !band.equals(end.getPriority()))
                    {
                        return best;
                    }

                    band = end.getPriority();

                    // Filtering at selection, never refusing at execution: reversing stations are parking, chosen only
                    // by hand (an isAutoRunning() fence in isPathClear would also refuse Return Home, which fills
                    // them);
                    // a terminus only for a train that can leave it at its length.  getBlockLocomotive, not isOccupied:
                    // a sibling copy holding a train is the same track.  The shared clauses are isSendableDestination.
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
                                    // Random rules take whatever works, at once; ranked rules enumerate the
                                    // alternatives.
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

                                    // Recorded even when accepted, or bfs hands back the same route for ever.
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
                            // Not silent: the "no free paths" message that follows would hide a failure, and the retry
                            // loop would call this forever with nothing in the log.
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
        // Throttles the retry while running; after a stop it would only delay the thread's exit.
        if (running) loc.delay(minDelay, maxDelay);

        return null;
    }
    
    /**
     * The length of the square an edge arrives on, which its sensor reports the head entering, so it is not yet known
     * to be behind the head: the edge's last place, 0 where it records none, never more than the edge.
     */
    private static int arrivingSquare(Edge edge)
    {
        List<Integer> lengths = edge == null ? null : edge.getPlaceLengths();

        if (lengths == null || lengths.isEmpty()) return 0;

        Integer last = lengths.get(lengths.size() - 1);

        return last == null ? 0 : Math.max(0, Math.min(last, edge.getLength()));
    }

    /**
     * Whether an edge the head has passed may be handed back: reported clear, and unlocked.  One rule, because both
     * are safety-relevant, and neither may rest on a guess about the tail: "cannot be known" means "the train is still
     * there".  The one escape is a path with nothing measured, where holding would hold until the route ended.
     *
     * @param pathIsUnmeasured whether nothing on this path is measured (`pathIsUnmeasured`), computed over the whole
     *        path before the run
     * @param behind how far the head has travelled since the END of the edge in question
     * @param trainLength the train's length, or null
     * @return whether the tail is provably clear of it
     */
    public static boolean tailHasProvablyPassed(boolean pathIsUnmeasured, int behind,
        Integer trainLength)
    {
        // A property of the whole path, asked once: a running total would read unmeasured until the first measured
        // edge and hand back an unmeasured first edge with the whole train on it.
        if (pathIsUnmeasured) return true;

        int length = trainLength == null ? 0 : trainLength;

        return behind >= length;
    }


    /**
     * Whether nothing on this path is measured, by Edge.isMeasured: track answered 0 counts as measured (Adam: "0
     * lengths count as measures"), so such a path holds its edges, the safe direction.
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
     * The reasons that have nothing to do with a particular destination (paused, off the graph, facing a way trains
     * may not arrive, not a station, switched off, no choosable station reachable: OB-299).
     *
     * @param loc the locomotive
     * @return the reason, ready to show, or null when the train is free to be given a route
     */
    public String explainCannotStart(Locomotive loc)
    {
        return layoutExplanations.explainCannotStart(loc);
    }

    /**
     * The reasons and the grouping, taken together under this Layout's monitor, so a reason and the heading it is
     * printed under cannot come from two different states of the railway.
     *
     * @param loc the locomotive asking
     * @return both halves, of one moment
     */
    synchronized public Destinations explainDestinationsGrouped(Locomotive loc)
    {
        return new Destinations(explainDestinations(loc), destinationsBarredFromAutonomy(loc));
    }

    /**
     * The same, for either tier (MT-434).  By hand, the reasons a hand-driven send gets, and no barred group: what
     * autonomy will not do is not the question.  Synchronized on this Layout.
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
     * Why each station is unavailable, and which of them autonomy would never choose anyway: one holder, so the two
     * are always taken together.
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

        /**
         * Files one point's reason under the station it is a copy of: a station is choosable if any of its copies is,
         * and barred only while none is; each group keeps the first reason it was given.
         *
         * @param point a point name from getReasons
         * @param station the station it is a copy of, as shown
         * @param choosable the stations autonomy could choose, to their reason
         * @param neverChosen the stations it never would, to their reason
         */
        public void fileUnderStation(String point, String station, java.util.Map<String, String> choosable,
            java.util.Map<String, String> neverChosen)
        {
            if (barred.contains(point))
            {
                if (!choosable.containsKey(station) && !neverChosen.containsKey(station))
                {
                    neverChosen.put(station, reasons.get(point));
                }
            }
            else
            {
                neverChosen.remove(station);

                if (!choosable.containsKey(station)) choosable.put(station, reasons.get(point));
            }
        }
    }

    /**
     * Whether autonomy could ever choose this square as a destination: the standing bars only (inactive, reversing,
     * not an auto destination), through the same barredFromAutonomy the "no available paths" window groups by.  Asked
     * with no locomotive, as a question about the square; it does not ask isDestination(), since callers already hold
     * a station.  Synchronized on this Layout.
     *
     * @param end the candidate station
     * @return true when nothing standing bars it
     */
    synchronized public boolean isChoosableByAutonomy(Point end)
    {
        return end != null && barredFromAutonomy(end, null) == null;
    }

    /**
     * Whether an operator should be offered this station for this train at all.  Almost everything is: only a
     * switched-off square (Adam: "make the inactive stations disappear from the track diagram menu - they should only
     * be visible in the autonomy tab.") and one that excludes this locomotive are not.  A terminus is offered (see the
     * no-terminus note in isPathClear).  Synchronized on this Layout.
     *
     * @param end the candidate station
     * @param loc the locomotive being offered it
     * @return false only where the operator has said this train does not belong there
     */
    synchronized public boolean isOfferableToOperator(Point end, Locomotive loc)
    {
        return whyNotOfferedToOperator(end, loc) == null;
    }

    /** Why the right-click menu leaves this station out for this train, or null; Why Not Moving? asks the same. */
    private String whyNotOfferedToOperator(Point end, Locomotive loc)
    {
        return layoutExplanations.whyNotOfferedToOperator(end, loc);
    }

    /**
     * Whether a train can stand on this square without being turned round (MT-367): a plain copy answers true about
     * itself, every copy of a compulsory turn false, and a may-turn square true from either copy.  A question about
     * the graph, not a train (that is turnsOnArrival).  False for a Point with no block.
     *
     * @param square any copy of the square
     * @return true when some copy of it does not turn the train
     */
    public boolean hasAWayThrough(Point square)
    {
        if (square == null) return false;

        // The copy asked about counts: a plain copy is itself the way through.
        for (Point copy : this.points.values())
        {
            if (!square.isSamePlaceAs(copy)) continue;

            if (!copy.isTerminus() && !copy.isReversing()) return true;
        }

        return false;
    }

    /**
     * Whether a train at this terminus could be sent on again by autonomy, to a station it fits; else a reversible
     * train sent to turn there never moves again.  Standing clauses only, one hop, and true on doubt.
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
     * Why autonomy will never choose this point for this locomotive, or null: the standing bars only, not what is
     * happening this minute.  The one list, so the window's grouping and its reasons cannot disagree.
     */
    private String barredFromAutonomy(Point end, Locomotive loc)
    {
        if (!end.isActive()) return I18n.t("autolayout.why.inactive");

        if (end.isReversing()) return I18n.t("autolayout.why.reversing");

        if (!end.isAutoDestination()) return I18n.t("autolayout.why.notAutoDestination");

        // A terminus this locomotive could not leave: standing, and the one list pickPath filters by too.
        if (loc != null && end.isTerminus() && !loc.isReversible())
        {
            return I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", loc.getName());
        }

        // And one a reversible train could not leave at its length, pickPath's clause through the same method.
        if (loc != null && end.isTerminus() && !this.leavesForAStationItFits(end, loc))
        {
            return I18n.f("autolayout.why.terminusItCouldNotLeave", loc.getName());
        }

        if (loc != null && end.excludes(loc)) return I18n.f("autolayout.why.excluded", loc.getName());

        return null;
    }

    /**
     * Of the points explainDestinations reports on, the ones autonomy will never choose, by name (FR-017: the window
     * groups them, and the reason strings cannot be told apart reliably).  Synchronized on this Layout.
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
            // The same skips explainDestinations makes.
            if (!end.isDestination() || end.equals(start)) continue;

            if (start.getBlock() != null && start.getBlock().equals(end.getBlock())) continue;

            if (barredFromAutonomy(end, loc) != null) out.add(end.getName());
        }

        return out;
    }

    /**
     * Every station this locomotive might be sent to, and why each one is or is not available.
     *
     * The answer to "I pressed start and nothing happened": pickPath and isPathClear (logging off) discard their
     * reasons.
     *
     * The same questions in the same order, keeping the answers; the second stage calls isPathClear itself, so it never
     * disagrees with the running layout.
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
     * The same reasons, for a train a person sends rather than one autonomy picks (MT-434): autonomy's standing bars
     * are skipped, and each station is decided as getPossiblePaths offers it.  Synchronized on this Layout.
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
            // Not a station: not a candidate.  Nor any copy of the square this train already stands on.
            if (!end.isDestination() || end.equals(start)) continue;

            if (start.getBlock() != null && start.getBlock().equals(end.getBlock())) continue;

            // The standing bar first, so a station autonomy never chooses is not reported "occupied" (FR-017).  By
            // hand, the menu's own reasons instead, so the button and its explanation agree (OB-057).
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
                // A physical refusal outranks a preference (MT-262): an operator sending a too-long train to a parking
                // berth must be told the length.  Only the length, which does not clear itself; the grouping is
                // untouched.  Not by hand, where the station is not offered at all and the menu's reason stands.
                String cannotFit = byHand ? null : whyNoRouteFitsTo(loc, start, end);

                if (cannotFit != null) reason = cannotFit;
            }
            out.put(end.getName(), reason);
        }

        return out;
    }

    /**
     * The square holding this destination back right now, or null (FR-001): the runtime's live-occupancy name for
     * Point.heldBackBy, shared by isPathClear and the explanation.  No tier fence.
     */
    private Point blockingOccupantOf(Point destination, Locomotive loc)
    {
        return Point.heldBackBy(destination, loc);
    }

    /**
     * The first square this route arrives at that is held back by an occupied one, with the square holding it, or
     * null (FR-001, OB-295).  Every square, not only the destination; what isPathClear refuses on and the window
     * explains.
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
     * Whether this rail is held by the asking train's own path (OB-269): a train never blocks itself (behaviour.md
     * 5c), and occupancy counters record no owner.
     */
    private boolean railHeldByThisTrain(Edge rail, Locomotive loc)
    {
        if (rail == null || loc == null) return false;

        List<Edge> mine = this.pathHeldBy(loc);

        return mine != null && mine.contains(rail);
    }

    /**
     * Whether a train holds a path, under way on it or still claiming it.
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
     * The path a train is under way on or still claiming.  Claims first: the hand-over writes the journey before
     * dropping the claim, outside the monitor, so a train between the two reads is still found.  Readers of the two
     * maps as a union read them in this order.
     */
    List<Edge> pathHeldBy(Locomotive loc)
    {
        List<Edge> path = this.takingPath.get(loc);

        return path != null && !path.isEmpty() ? path : this.activeLocomotives.get(loc);
    }

    /**
     * Why no route from start to end can hold this train, or null when one can (MT-262): the one refusal that is a
     * fact about the railway, tried over every alternative route.  Free on a railway with no lengths.
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

                // Autonomy's rule, not a hand send's: isPathClear does not ask reversesAlongTheWay.
                if (!byHand && this.reversesAlongTheWay(path))
                {
                    why = I18n.t("autolayout.why.throughReversing");
                    continue;
                }

                // lastError is static and written by every train's pickPath, so the wording read below may belong to
                // another train's candidate; the decision comes from isPathClear's return value.
                if (this.isPathClear(path, loc, false, byHand)) return null;

                // FR-001 asked directly, not read from the static: while autonomy runs the wording below would call a
                // permanent hold "blocked while running".  The same rule isPathClear refuses on, in every tier
                // (OB-295).
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

        // No route at all is a different answer from every route blocked.  But the search above never passes a
        // terminus or a copy of either end, so ask the track once more: if a route exists that way, name what is in it.
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

                    // The terminus first: it has a remedy on the diagram.
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
                                // Not silent, as in pickPath.
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
     * -1, not 0: entries finish out of order on their own threads, and the caller wipes every completion timestamp
     * when nothing is unfinished.
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
     * A copy taken under the monitor, for readers that want one consistent list.
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
     * Honours the operator's action delays, but never spins when both are zero: the sequential branch waits minutes
     * per entry.
     *
     * Any stop cuts it short, or the loop could live on into the next run.
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
     * The same, with the stops counted where the run was chosen: a stop ordered since (a reload's Yes while Return
     * Home plans) starts nothing.
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
        // Before running is set: with no entry no thread would clear it.
        if (this.timetable.isEmpty())
        {
            this.control.logf("autolayout.infoTimetableExecutionFinished");
            return true;
        }

        this.timetableStoppedBecause = null;

        // Not after a stop ordered since the run was chosen: `running` set, then the count asked, under stopLock, so a
        // Yes cannot fall between them.
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
        
        // Resume after a graceful stop; clamped, since the index is -1 when nothing is unfinished.
        int startIndex = Math.max(0, getUnfinishedTimetablePathIndex());
            
        this.control.logf(
            "autolayout.infoExecutionStartedFromIndex",
            startIndex + 1
        );        
        
        // The loop is counted while it dispatches, as each entry is (see timetableEntries).
        this.timetableEntries.incrementAndGet();

      try
      {
        for (int i = startIndex; i < this.timetable.size(); i++)
        {
            TimetablePath ttp = this.timetable.get(i);
            
            final int index = i;

            // Until a graceful stop, or until this Layout is retired: after a reload nothing can stop a retired
            // Layout, and the sequential wait below would wait forever.
            while (this.running && this.isCurrentLayout())
            {
                // No entry started while the track power is off; the run waits, then asks again.
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
                    // A path through a point the railway does not have stops the run, and says why (Adam: "stop the
                    // timetable run upon encountering an invalid path, and let the user know"); skipped, a train's
                    // later entries would start where it was not.
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

                    // Counted before it starts, until it leaves (see timetableEntries).
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

                            // The power's epoch as this attempt began; see below.
                            int powerAtAttempt = this.control.getPowerEpoch();

                            while (this.running && !this.executePath(ttp.getPath(), ttp.getLoc(),
                                ttp.getLoc().getPreferredSpeed(), ttp, playedBack(ttp), stopsAtChoice))
                            {
                                // A bad speed is not a busy track: executePath refuses it with the same false, so skip
                                // this locomotive and say so, as runLocomotives does, rather than retrying to "stuck".
                                if (ttp.getLoc().getPreferredSpeed() < 1
                                    || ttp.getLoc().getPreferredSpeed() > 100)
                                {
                                    this.control.logf("autolayout.errorFailedToRunLocomotive",
                                        ttp.getLoc().getName());

                                    // Stamped, or the next entry would wait forever on "the previous route has not
                                    // started yet".
                                    ttp.setExecutionTime(System.currentTimeMillis());

                                    break;
                                }

                                // An attempt the power cut is no refusal, nor the start of a parallel run's clock: the
                                // route was given back unrun.  The epoch catches a stop between the power wait and the
                                // read.
                                boolean cut = this.control.getPowerEpoch() != powerAtAttempt || !this.control.getPowerState();

                                if (!cut)
                                {
                                    attempts++;

                                    if (refusingSince == 0) refusingSince = System.currentTimeMillis();
                                }

                                // A parallel timetable gives up on time, a sequential one on attempts: the pause
                                // between
                                // attempts follows the user's delay settings, so attempts would bound different waits.
                                boolean stuck = !cut && (this.timetableSequential
                                    ? attempts >= STAGING_MAX_ATTEMPTS
                                    : System.currentTimeMillis() - refusingSince >= TIMETABLE_STUCK_MS);

                                if (stuck)
                                {
                                    // Nothing will free the path: stop and say so rather than spin.
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

                                // A pause any stop cuts short.  A sequential run is paced on its own, as the delay
                                // settings may be zero.
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

                                // Nor tried again with the power off; that wait is not refusing, so the giving-up clock
                                // restarts.
                                if (this.waitWhileThePowerIsOff()) refusingSince = 0;

                                powerAtAttempt = this.control.getPowerEpoch();
                            }

                            this.control.logf("autolayout.infoTimetablePathFinished");
                        }
                        // Throwable: executePath's handler catches RuntimeException only, and an Error escaping here
                        // would skip the block below that clears `running`, leaving isRunning() true for ever.
                        catch (Throwable e)
                        {
                            this.control.logf(
                                "autolayout.errorTimetableExecutionFailed",
                                e.toString()
                            );

                            // Abandoned (OB-072), set before the trains are stopped, so the return never reports a
                            // normal finish.
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

        // The loop only dispatches the last entry; wait for every train to finish before returning, since callers
        // re-enable their buttons on return.  isCurrentLayout too: a fence-abandoned path leaves its activeLocomotives
        // entry on a retired Layout on purpose, so isRunning would stay true.
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

        // Now the railway is idle, so say so: every other announcement a timetable makes comes while it still runs,
        // and without this a turn recorded on a Return Home arrival would never be reconciled into the setup.  Not
        // gated on `abandoned`: a run that gave up part way still moved trains.
        announceRunFinished();

        return !abandoned.get();
    }
    
    /**
     * Waits, at most `atMostMs`, while a running train stands on the sensor its run is waiting for and has not yet
     * recorded (OB-250): a route fired by that sensor asks "where is the train" in the gap.  Not for a sensor the
     * train has not reached, nor one already recorded.
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

            // Read outside the monitor: the feedback store has a lock of its own.  Recorded already: still pending
            // through a turn.
            if (pending == null || pending.equals(this.getLatestMilestoneS88(l))
                || !this.control.getFeedbackState(pending)) break;

            long left = until - System.currentTimeMillis();

            if (left <= 0) break;

            synchronized (pendingS88Monitor)
            {
                if (!pending.equals(this.locomotivePendingS88.get(l))) continue;

                try
                {
                    // Woken when the run moves on, and asked again within 50 ms for a sensor that clears unrecorded.
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
                        // Remembered, not re-armed: re-arming makes the next wait() throw at once and spin.
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
     * Asked before a train is turned round at a point it may reverse at.  Intent cannot be inferred (Adam: "in manual
     * mode, the system has no way of knowing that the intention is to reverse into a berth on the next turn."), so
     * the caller decides: autonomy always says yes, a hand-driven send asks the operator.  A true terminus is never
     * asked.
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
         * It must be cheap, silent and independent of the answer: it runs at every point on every path, and
         * shouldReverseAt reads it to tell a compulsory turn from a choice.  shouldReverse may be called while the
         * train is moving, so it must not block (a dialog there would let the train run past).
         *
         * The default answers `at.isReversing()`, which is also true at every copy of a compulsory turn, so a real
         * door must override it: mayTurnTiles() is the only record of which squares are optional.
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
     * Whether a train that has just arrived here is turned round (MT-368).  A square the door asks about is the
     * operator's call (Adam: "may reverse should always prompt in manual mode"), terminus flag or not, since the
     * builder marks a may-turn square's turning copy a terminus; a terminus nobody is asked about turns the train
     * (MT-245); everything else is shouldReverseAt.
     *
     * @param arrived where the train has stopped
     * @param loc the train
     * @param reversals the policy, or null for autonomy
     * @return whether to turn it
     */
    public boolean turnsOnArrival(Point arrived, Locomotive loc, ReversalPolicy reversals)
    {
        if (arrived == null) return false;

        // Nowhere on the square to stand facing on from the way it came: no choice, whatever the door's answer, or the
        // train stands facing back out and its next journey drives into the end of the line.  Asked of the journey it
        // holds.
        if (loc != null && reversals != null && reversals != ALWAYS_REVERSE)
        {
            List<Edge> held = this.pathHeldBy(loc);

            if (held != null && !held.isEmpty() && held.get(held.size() - 1).getEnd() == arrived
                && noCopyFacesOnFrom(arrived, held.get(held.size() - 1).getStart()))
            {
                return true;
            }
        }

        // A train that cannot reverse is never turned where turning is optional (MT-368); a terminus still turns one
        // that backed into it (MT-245).  Not where a plan decided (ALWAYS_REVERSE, or null): re-standing the train
        // would refuse the plan's next leg.  Whether autonomy should route such a train to a turning copy at all is
        // open with Adam.
        if (loc != null && !loc.isReversible() && reversals != null && reversals != ALWAYS_REVERSE
            && hasAWayThrough(arrived))
        {
            return false;
        }

        // A square the door asks about is the operator's call, terminus flag or not (asksAbout comes from
        // mayTurnTiles()).
        if (reversals != null && reversals != ALWAYS_REVERSE && reversals.asksAbout(arrived))
        {
            return reversals.shouldReverse(loc, arrived);
        }

        return arrived.isTerminus() || shouldReverseAt(arrived, arrived, loc, reversals);
    }

    /**
     * Whether a train reaching this point is turned round here.  A point trains do not reverse at: no.  On a journey
     * whose destination is a terminus, a reversing point turns the train without asking: that is how it backs in
     * (Adam, MT-245: "trains should be allowed to back into terminuses if they are not reversible").  Anywhere else
     * the caller's policy decides; a null policy means yes.
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

        // Nobody to ask means autonomy, which turns exactly where the flag says.  ALWAYS_REVERSE counts as nobody: it
        // is what autonomy and the timetable (Return Home) pass (Adam: "no prompting in return home or auto.").
        if (reversals == null || reversals == ALWAYS_REVERSE) return current.isReversing();

        // A journey ending at a terminus the door does not ask about turns where the flag says: the turn is how the
        // train gets there (MT-245).  The door is asked because a may-turn square's turning copy is also marked a
        // terminus (OB-205), and only mayTurnTiles() knows which squares are optional.
        if (destination != null && destination.isTerminus() && !reversals.asksAbout(destination))
        {
            return current.isReversing();
        }

        // In manual mode the door decides which squares are asked about (Adam: "May reverse should always prompt in
        // manual mode.").  A reversing copy it does not ask about is a compulsory turn, which turns in every mode: not
        // turning would drive the train off its reserved path.
        if (current.isReversing() && !reversals.asksAbout(current)) return true;

        // mayReverseAt covers the plain copy of a split square, where the default asksAbout answers false.
        if (!mayReverseAt(current) && !reversals.asksAbout(current)) return false;

        return reversals.shouldReverse(loc, current);
    }

    /**
     * A road written by namesOfRoad, looked up on this layout.  All or nothing: a pair naming an edge this layout does
     * not have drops the whole road, and the tail walk falls back to the fork rule.
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
     * A road as the file writes it: [start, end] point-name pairs, in the order driven.
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
     * Which side of its end point an edge comes in by, the one definition for the arrival that writes it and the tail
     * walk that reads it.  The build's recorded side, else the compass direction of the neighbouring point (wrong
     * only on a curve).
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
     * The edges a running train has already given back behind it under non-atomic routes (none under atomic routes),
     * for the diagram (FR-106).  A copy, taken without the monitor: the diagram's worker must not wait on a dispatch.
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
     * Edges, not points (Adam: "because the points are technically unoccupied.  But the blocked edges should prevent
     * routing to the covered points.").
     *
     * Derived, never stored, so it corrects itself the moment a train moves.
     *
     * It stops early on purpose (Adam's rulings): at a fork entered from the split side ("just end locking at the
     * switch and call it a day."), and at an unmeasured segment ("Only segments with a positive length are
     * determinate.").
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
     * Each standing train's covered edges and claimed places, walked one train at a time: the combined maps keep one
     * train per place, so a question about every train on a place (Return Home's) reads these.  Synchronized.
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
     * The same walk, answered in places rather than whole edges (OB-207), so a short train at the far end of a long
     * edge does not foul all of it.  A place is claimed if the tail reaches it at all.  Empty for edges the build did
     * not describe in places; readers fall back to edgesCoveredByStandingTrains.  Synchronized on this Layout.
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
     * Whether a place an edge runs over is the square a Point stands on, compared by tile.  A Point with no block
     * cannot say, so nothing is.
     */
    static boolean isTheSquareOf(String placeId, Point point)
    {
        String block = point == null ? null : point.getBlock();

        if (placeId == null || block == null) return false;

        return tileOf(placeId).equals(tileOf(block));
    }

    /** The tile part of a place or block id; looked for after the last colon, as a page name may contain a slash. */
    private static String tileOf(String id)
    {
        int slash = id.indexOf('/', Math.max(0, id.lastIndexOf(':')));

        return slash < 0 ? id : id.substring(0, slash);
    }

    /** The one walk behind both answers above, so the two can never disagree about where a tail is. */
    private void walkStandingTrains(Map<Edge, Locomotive> covered, Map<String, Locomotive> places)
    {
        standingTrains.walkStandingTrains(covered, places);
    }

    /**
     * The same walk, over only the trains asked for: the maps keep one train per place, the last walked, so a
     * question about one train's route walks every other train (its own tail is no obstacle, behaviour.md 5c).
     */
    private void walkStandingTrains(Map<Edge, Locomotive> covered, Map<String, Locomotive> places,
        java.util.function.Predicate<Locomotive> which)
    {
        standingTrains.walkStandingTrains(covered, places, which);
    }

    /**
     * One standing train's tail walk, also recording how far behind the head each claimed place begins (OB-294), for
     * whyItWouldMeetItsOwnTail.
     */
    private void walkOneTail(Point standing, Locomotive loc, String arrivedFrom, List<Edge> arrivedAlong,
        Map<Edge, Locomotive> covered, Map<String, Locomotive> places, Map<String, Integer> reach)
    {
        standingTrains.walkOneTail(standing, loc, arrivedFrom, arrivedAlong, covered, places, reach);
    }

    /**
     * The edges a train would cover standing at the end of this road, having driven it (OB-228): what the Return Home
     * planner asks of a train its plan has moved.  Synchronized on this Layout.
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
     * Whether this square is a parking berth, by Adam's ruling on FR-060: "For now, we consider anything with
     * autodestination=false and only one way in/out as a parking square."  Ways are counted in squares, not copies.
     * Not the berth rule's gate, which whyABerthCannotHoldIt applies more widely.
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
     * The train, other than the one being routed, whose tail lies over metal this edge runs on, or null: by place, so
     * every copy and both directions of a rail answer alike.
     */
    static Locomotive anotherTailOn(Edge edge, Locomotive mover, Map<String, Locomotive> claimed)
    {
        return StandingTrains.anotherTailOn(edge, mover, claimed);
    }

    /** Whether a named locomotive's tail lies over any of the metal this edge runs on (OB-207). */
    static boolean tailLiesOn(Edge edge, Locomotive who, Map<String, Locomotive> claimed)
    {
        return StandingTrains.tailLiesOn(edge, who, claimed);
    }
    /**
     * Which compass side of `from` the point `to` lies on: the sign of the larger coordinate difference, a tie to the
     * horizontal.  Null when either point has no coordinates.
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

        // Y grows downwards on the diagram, so a larger y is south.
        return dy > 0 ? "S" : "N";
    }
    /**
     * Whether a point's square is a station (any copy a destination), which decides how a locomotive list is read: on
     * a station it names locomotives that may not stop, elsewhere ones that may not pass.  Not the copy's own flag: a
     * barred arrival side's copy is built as no destination.
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
     * Whether trains may turn round at this piece of track, on any of its copies: a may-turn square is expressed by a
     * split, with only the turning copy marked reversing.
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
     * What autonomy uses: the four-argument executePath, taken by autonomy's own loop (pickPath refuses paths that
     * reverse on the way) and by the timetable, which Return Home plans.  A new caller inherits it by writing four
     * arguments, so choose it deliberately.
     */
    public static final ReversalPolicy ALWAYS_REVERSE = (loc, at) -> true;

    /**
     * The policy a timetable entry is played back with: autonomy's, unless the entry recorded its arrival's answer,
     * which is replayed at its destination only, and only where the square can still turn trains (Adam: "The path
     * should organically fail in the direction no longer allowed.").
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
     * Locks a path and runs the locomotive from the start to the end, turning it wherever the flags say.
     *
     * @param path the route
     * @param loc the train
     * @param speed how fast
     * @param ttp the timetable entry, or null if not running a timetable route
     * @return whether the train got there
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
     * A journey sent by hand: executePath, with the train marked as a hand send for its length, which the cap on
     * trains out neither counts nor refuses (behaviour.md 1).  Every hand door comes here.
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
        // Marked only if not already, so a refused second hand send cannot take the first one's mark away.
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
     * The same, carrying the stops counted where the journey was chosen: a stop ordered since sends nothing.
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
        // A timetable entry runs from the copy its train stands on (pathFromWhereItStands).  Here, not in
        // executePathInternal, so the cleanup below unlocks the path that was locked.
        if (ttp != null)
        {
            List<Edge> from = this.pathFromWhereItStands(path, loc);

            if (from != null) path = from;
        }

        // Every dispatch is counted, by hand too: activeLocomotives is written only after the lock phase, and
        // everything built on isRunning() must hold through it (Adam: "The same thing should happen in manual
        // operation vs auto - the same switches and signals set, and guards applied.").  Reaching zero announces the
        // end of the run.
        this.locomotiveThreads.incrementAndGet();

        try
        {
            try
            {
                return executePathInternal(path, loc, speed, ttp, reversals, stopsAtChoice);
            }
            catch (RuntimeException e)
            {
                // A failure part way through must not leave the locomotive in activeLocomotives, which nothing else
                // clears.  Not a finally: the version fence returns normally and leaves its entry on purpose.
                // Rethrown:
                // a false would feed executeTimetable's retry loop.  Whether it ever got its path is read before the
                // removal, since membership means the lock phase succeeded.
                final boolean hadItsPath;

                synchronized (this.activeLocomotives)
                {
                    hadItsPath = this.activeLocomotives.containsKey(loc);

                    this.activeLocomotives.remove(loc);
                    this.locomotiveMilestones.remove(loc);
                }

                // And any unfinished claim on a slot, which would lower the cap for good.
                this.takingPath.remove(loc);

                // And the run stops itself gracefully (Adam: "Autonomy gracefully stopping itself so that it can be
                // resumed is the right approach."): nothing watches the stretch the failed train stands on.  Read
                // `running` before the stop clears it.
                final boolean wasRunning = this.running;

                stopLocomotives();

                // The sentence for whichever it was: a hand dispatch also fails here, with autonomy stopped.
                this.control.logf(wasRunning
                    ? "autolayout.errorRunStoppedByFailure"
                    : "autolayout.errorHandDispatchFailed", loc.getName());

                // Then the track goes back (Adam: "Force a graceful stop, alert the user, then unlock."), after the
                // train itself is stopped, since unlockPath may wait seconds for the monitor.  Released as a finished
                // path is, before releasedEarly is cleared, which unlockPath reads.  Guarded, so a second failure
                // cannot
                // replace the exception that explains the first.
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
                    // Only a path this locomotive actually got: a lock-phase failure is already recovered by
                    // configureAndLockPath, and unlocking the whole path over it would clear the start reservation and
                    // release counted lock edges never taken.
                    if (hadItsPath)
                    {
                        this.unlockPath(path, loc);
                    }

                    this.clearedEdges.remove(loc);
                    this.releasedEarly.remove(loc);
                }

                // A route condition waiting on this sensor would wait forever; updatePendingS88 notifies any waiter.
                updatePendingS88(loc, null);


                this.control.log(e);

                throw e;
            }
        }
        finally
        {
            // In a finally, as in runLocomotive: otherwise the layout reads as running for the rest of the session.
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
        // The stops counted where this journey was chosen, or now for a caller that chose nothing: before the claim,
        // which can wait seconds behind another train's switches.
        final int stopsAtDispatch = stopsAtChoice < 0 ? this.stopsOrdered.get() : stopsAtChoice;

        // The power's epoch before the route's first switch: odd means off now, and a change later means a cut in
        // between.
        final int powerAtDispatch = this.control.getPowerEpoch();

        // A stop ordered already sends nothing, and says so.
        if (this.stopsOrdered.get() != stopsAtDispatch)
        {
            this.control.logf("autolayout.log.notSentAfterTheStop", loc.getName());

            return false;
        }

        // Sanity check
        if (!this.isValid())
        {
            // With the reason this layout recorded when invalidated, not lastError, which names the latest failure.
            this.control.logf("autolayout.errorConfigurationInvalidMustReload");

            if (this.invalidReason != null)
            {
                this.control.log(this.invalidReason);
            }

            return false;
        }

        // Retired graph: refuse before configureAndLockPath throws switches on a replaced layout's path.
        if (!this.isCurrentLayout())
        {
            return false;
        }

        // A speed of 0 is not a dispatch: the thread would wait forever on a sensor a stationary train never reaches,
        // with isRunning() stuck true.
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

        // Already out, in either sense.
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

        // A lap is refused: its arrival would take the train off the station it had just reached.  No door offers one;
        // a timetable file can carry one.
        if (start == path.get(path.size() - 1).getEnd())
        {
            this.control.logf("autolayout.errorPathEndsWhereItStarts", start.getName());
            return false;
        }
        
        // No signal sweep here (OB-166): see runLocomotives.

        boolean result;
        
        result = configureAndLockPath(path, loc, reversals);

        if (!result)
        {
            // configureAndLockPath has already logged the reason.  Nothing to drop here: every refusal inside it took
            // nothing or dropped its own claim, and removing one here could remove another dispatch's.
            return false;
        }
        else
        {
            // A stop ordered while this claimed its route: given back unrun, the train standing where it was.
            if (this.stopsOrdered.get() != stopsAtDispatch)
            {
                giveBackUnrun(path, loc, "autolayout.log.notSentAfterTheStop");

                return false;
            }

            // Nor if the track power went off while its route was set, even briefly (compared by epoch): the speed
            // would take effect the moment the power came back.  Given back unrun; the loops wait for the power.
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

                // Counted for real now, so the claim is given up; trainsUnderway takes a union, so the order cannot
                // count two.
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
            // And what its arrival is told, for a send by hand, so playback turns or keeps the train as it was
            // recorded.
            if (this.addTimetableEntry(loc, path) && reversals != null && reversals != ALWAYS_REVERSE)
            {
                this.answerTheEntryRecorded(loc, path, turnsOnArrival(path.get(path.size() - 1).getEnd(), loc, reversals));
            }
        }
        
                    
        if (loc.hasCallback(CB_ROUTE_START))
        {
            loc.getCallback(CB_ROUTE_START).accept(loc);
        }
        
        // Fenced as every speed below is: a stop since dispatch, or a retired railway, keeps the train standing.
        if (drive(loc, speed, stopsAtDispatch))
        {
            this.control.logf(
                "autolayout.infoLocomotiveStarted",
                loc.getName()
            );
        }

        // When !this.atomicRoutes: track edges to unlock based on length of train
        // Edges waiting to be released, each { index into path, distance the head has gone since its end, where it
        // ends }: per edge, not a running total, which would release the whole batch at once, track under the train
        // included.
        List<int[]> waitingToClear = new LinkedList<>();

        // Asked of the whole path, once, before anything is handed back (see tailHasProvablyPassed).
        boolean pathIsUnmeasured = pathIsUnmeasured(path);

        // The length as dispatched: a change while the train runs must not shorten what it holds.
        final Integer lengthAtDispatch = loc.getTrainLength();

        // Distances from the path's start: to the far end of the edge this loop has reached, and to where a sensor
        // last saw the head.  An edge's distance behind the head is the second less where that edge ends.
        int pathRunTo = path.get(0).getLength();
        int headSeenAt = 0;

        for (int i = 0; i < path.size(); i++)
        {
            Point current = path.get(i).getEnd();

            // Recorded when its sensor answers, so not again at the end of this step.
            boolean recordedAlready = false;

            // Whether this point's sensor answered for the head: the only proof of where the head is.
            boolean seenHere = false;
            
            if (i != path.size() - 1)
            {
                // Adjust speed based on multiplier
                if (isCurrentLayout())
                {
                    int calculatedSpeed = (int) Math.ceil((double) speed * current.getSpeedMultiplier());
                    calculatedSpeed = Math.min(calculatedSpeed, 100);
                    
                    // Not after a stop since dispatch: a stopped train braking onto the next sensor gets no speed back.
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
                    // Already occupied when its wait begins (a shared sensor, or another train on it), the answer is
                    // no proof of where the head is.  Asked before simulation announces the train.
                    boolean occupiedAlready = loc.isFeedbackSet(current.getS88()) && loc.getFeedbackState(current.getS88());

                    long simEpoch = 0;

                    if (this.simulate)
                    {
                        loc.delay(this.getMinDelay(), this.getMaxDelay());
                        simEpoch = simAnnounce(current.getS88());
                    }
                    
                    this.updatePendingS88(loc, current.getS88());

                    // With an advisory: this is the wait that means "a train was sent and has not got there", which
                    // only the dispatch loop knows (a route's trigger uses the same wait).
                    loc.waitForOccupiedFeedback(current.getS88(),
                        Locomotive.FEEDBACK_DURATION_THRESHOLD, Locomotive.FEEDBACK_ADVISORY_MS, this.retiredRailway);

                    // Seen here, unless the wait was given up for a retired railway or the sensor was occupied
                    // already; then the head is credited where the next sensor answers.
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

                // Reached, and recorded as soon as its sensor answers, before any turn here, so a turning train is not
                // placed a square back.
                synchronized (this.activeLocomotives)
                {
                    List<Point> reached = this.locomotiveMilestones.get(loc);

                    if (reached != null) reached.add(current);
                }

                recordedAlready = true;
                
                // Reverse here if shouldReverseAt says so: a terminus turns the train, elsewhere the caller's policy
                // decides.  Asked before stopping, which is safe because the policy never blocks (the operator answers
                // at departure), so a train stops only to turn.  Not mayReverseAt, which is square-wide and would stop
                // autonomy dead at every plain copy it passes.
                if (isCurrentLayout()
                    && shouldReverseAt(current, path.get(path.size() - 1).getEnd(), loc, reversals))
                {
                    loc.setSpeed(0).waitForSpeedBelow(1);

                    // And redrawn now, or a train turning with nothing else moving is drawn running until the step
                    // ends.
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

                    // At this point's own speed, not the journey's, keeping the square's multiplier.
                    int resume = Math.min((int) Math.ceil(
                        (double) speed * current.getSpeedMultiplier()), 100);

                    // Not after a stop since dispatch, and then not waited for either.
                    if (drive(loc, resume, stopsAtDispatch))
                    {
                        // Given up on a stop or a retired railway, or a Yes landing between the write and this wait
                        // would hang it.
                        loc.waitForSpeedAtOrAbove(resume, () -> this.stopsOrdered.get() != stopsAtDispatch
                            || !this.isCurrentLayout());
                    }
                }
                
                // We can also clear the edges dynamically 
                // This can be useful, but extra care needs to be taken if any paths cross over
                // Therefore, we use setLockedEdgeUnoccupied and unlock 1 edge prior to the current one
                // path.get(i).setUnoccupied();
                //
                // Runs in both modes, so the tail is always known: cleared ("no train on top of this", both modes) and
                // unlocked ("another train may be routed here", non-atomic only) share one answer (see clearedEdges).
                if (isCurrentLayout())
                {
                    if (i > 0)
                    {       
                        // The head has finished edge i: once its end sensor answers, edge i-1 is edge i's length behind
                        // it.  But not the sensor's own square, which the head has only entered (arrivingSquare); and
                        // only as far as a sensor has seen the head, so a Point with no sensor, or one occupied
                        // already, credits nothing until the next sensor answers.  A release must never be early (Adam
                        // chose releasing track "earlier, never unsafe").
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

                            // The tail is the train's length behind the head, unless nothing on the path is measured.
                            // One proof for clearing and unlocking: with atomicRoutes on, being cleared is the only
                            // thing that drops an edge's protection from routes (getActiveAccs), so an early clear lets
                            // a route throw a turnout under the train.
                            if (!tailHasProvablyPassed(pathIsUnmeasured, waiting[1],
                                lengthAtDispatch))
                            {
                                // Still under the train; only reported where it could have been unlocked.
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

                            // Cleared, by the same proof unlocking uses: a signal or turnout behind the train may move.
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
                                // Everything this edge took, lock edges included (OB-164), or every throat the train
                                // had been through would refuse routes until the path finished.  setUnoccupied releases
                                // each lock edge once, matching setOccupied, so unlockPath must not do it again.
                                path.get(waiting[0]).setUnoccupied();
                                path.get(waiting[0]).getStart().setLocomotive(null);
                                // the far end is not cleared: that unlocks the next edge early

                                // What this run no longer holds, for unlockPath.
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
                    // Not after a stop ordered since dispatch
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
                // Stop before abandoning the path: the layout was retired, stopLocomotives() commands nothing, and
                // nothing else would ever stop this train.
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

        // Reverse at the destination: a terminus turns the train, and a may-reverse point asks first (Adam: "if the
        // user decides to send a train to a 'may reverse' point, explicitly ask the user if the train should change
        // direction.").
        final Point arrived = path.get(path.size() - 1).getEnd();

          // And the railway remembers which way it came in, the only moment anything knows it: the tail lies along
          // the arrival track whichever way the train then faces.  Recorded before any turn, by the rule the tail walk
          // reads back (OB-182).
                arrived.setArrivedFrom(entrySideOf(path.get(path.size() - 1), arrived));

          // AND THE ROUTE ITSELF, so the tail can be followed past a junction (Adam, MT-335: "keep claiming along the road it
          // actually arrived on").
                arrived.setArrivedAlong(path);

        // And its entry guard goes red (FR-096; Adam: "a signal that turns red after arrival at the final
        // designation."), at the journey's end in every tier.  Nothing here turns it green.
        throwEntryGuard(arrived);

        // AND THE SQUARE IT LEFT NO LONGER HAS A TAIL ON IT: otherwise the track behind an empty platform stays blocked by a
        // train that drove away, until a rebuild.
        if (!path.isEmpty() && path.get(0).getStart() != null
            && path.get(0).getStart() != arrived)
        {
            path.get(0).getStart().setArrivedFrom(null);
        }

        // Set when the arrival declined a turn at a square that may turn; acted on after the unlock.
        boolean restandAfterUnlocking = false;

        // Set when the arrival turned the train; acted on after the unlock, likewise.
        boolean restandAfterTheTurn = false;

        // Asked once, in this one expression: testTheRunAsksThatRule reads this file and asserts this statement.
        if (turnsOnArrival(arrived, loc, reversals))
        {
            this.control.logf(
                "autolayout.infoLocomotiveReachedTerminusOrFinalReversingStation",
                loc.getName()
            );
            loc.delay(this.getMinDelay(), this.getMaxDelay());

            // Not on a railway retired on the way (Unload or a reload can come in the pause above): the fold that
            // retired it recorded the train unturned.
            final boolean stillThisRailway = isCurrentLayout();

            if (stillThisRailway) loc.switchDirection().delay(1000); // pause to avoid network issues

            // Re-stood at once only for a turn the operator answered: a plan has already routed its next leg from
            // the turning copy.
            restandAfterTheTurn = stillThisRailway && reversals != null && reversals != ALWAYS_REVERSE;

            // And the graph is told, by square, so the window writes the facing absolutely (see reversedOnArrival).
            // One locomotive's arrivals are all on its own thread, so nothing races for a key.
            if (stillThisRailway && loc.getName() != null) this.reversedOnArrival.put(loc.getName(), arrived.getName());
        }
        else
        {
            if (loc.getName() != null)
            {
                // An arrival that did not turn clears it: a stale record would be applied to a square the train has
                // left.
                this.reversedOnArrival.remove(loc.getName());
            }

            // And it must not stay on the copy that expected it to turn (MT-368): that copy faces the other way, so the
            // next dispatch would read edges for a train pointing the other way.  Re-stood after the unlock, since
            // setLocomotive sweeps the run's remaining reservations.
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

            // Now the train may be moved onto the copy it faces: after the unlock, inside the monitor, as every
            // end-of-path rearrangement is.
            if (restandAfterUnlocking) standOnTheCopyItDidNotTurnOn(arrived, loc, path);

            // And a train that turned, onto the copy it now faces on from, here rather than at the idle drain, which a
            // timetable never reaches.
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
     * Takes off the graph whatever an edit to this locomotive (name, address, consist, from the window or the Central
     * Station sync) has made conflict with it: the train itself where it stands, and every standing multi-unit that
     * drives it, each swept as itself.  The head is never asked whether it is compatible with its own member (MT-149).
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
     * Takes off the graph every train that cannot stand on it with this one: a member of its consist, or one with an
     * equivalent address.
     */
    private void clearMultiUnitConflictsWith(Locomotive l)
    {
        if (l != null)
        {
            for (Point p : this.getPoints())
            {
                // Never the locomotive asked about (MT-149): compared with itself it reads as incompatible.
                if (l.equals(p.getCurrentLocomotive())) continue;

                if (
                    // Is the locomotive present in any active multi-unit?
                    p.getCurrentLocomotive() != null && !p.getCurrentLocomotive().isSimultaneousMultiUnitCompatible(l)
                    // Or are we linked to the current locomotive?
                    || p.getCurrentLocomotive() != null && !l.isSimultaneousMultiUnitCompatible(p.getCurrentLocomotive())
                )
                {
                    // Through the bundles, like the other warnings in this class.
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
     * Claims the squares every rail shares from the platform back to where they part, when they do part (MT-477);
     * true then, and the walk stops there.  Copies of one neighbour are one road, and an unmeasured rail says nothing.
     */
    private static boolean claimUpToWhereTheRailsPart(List<Edge> rails, Locomotive loc, Map<String, Locomotive> places,
        Set<String> spent, int remaining, Map<String, Integer> reach)
    {
        return StandingTrains.claimUpToWhereTheRailsPart(rails, loc, places, spent, remaining, reach);
    }

    /**
     * Whether a Point is a copy of a station square that trains may not arrive at: another copy is a station, and none
     * faces this one's way.
     *
     * @param point the Point
     * @return true for such a copy
     */
    public boolean isABarredCopyOfAStation(Point point)
    {
        if (point == null || point.isDestination()) return false;

        // Where a station copy faces this way (a turning copy at a may-turn square), the train can start from there.
        if (startableTwinOf(point) != null) return false;

        for (Point other : this.points.values())
        {
            if (other != point && other.isDestination() && other.isSamePlaceAs(point)) return true;
        }

        return false;
    }

    /**
     * Whether every copy of this Point's square turns a train round: a compulsory turn, or a terminus.
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
     * A copy of the same square that is a station and faces the same way as this one, or null.
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
     * Moves a locomotive to a station, as moveLocomotive(String, String, boolean) does, and with evenOntoABarredCopy
     * also onto a barred copy of a station square: a train turned on the throttle or by the Facing menu can stand
     * there, and a rebuild must be able to put it back.  Synchronized on this Layout.
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
     * The same, and, for putting trains back after a rebuild only, onto a copy that is no station at all: a train
     * stands where it stands, and autonomy then refuses to start it and says why.
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

            // And a speed, if nobody has ever given it one (MT-233), as parseAuto does on load, or Start answers
            // "Invalid speed specified".  Only when the default is usable: a hand-built test Layout may have none.
            if (l.getPreferredSpeed() < 1 && this.defaultLocSpeed >= 1)
            {
                l.setPreferredSpeed(this.defaultLocSpeed);

                this.control.logf("autolayout.warnLocomotiveDefaultSpeedApplied",
                    l.getName(), this.defaultLocSpeed);
            }
            
            Point target = this.getPoint(targetPoint);

            if (target == null)
            {
                this.control.logf(
                    "autolayout.errorPointDoesNotExist",
                    targetPoint
                );
                return result;
            }

            // Can only place loc on a station, or put one back on a barred copy of one
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
            
            // Ensure no multi-unit conflicts, before the train is put down.
            this.clearMultiUnitConflictsWith(l);

            // Placing by hand displaces whoever was there, across the whole square: it is a person saying where the
            // train is, and two trains on two copies of one platform would share one piece of track.  Only here, not
            // in the shared setLocomotive, where a sweep would collapse path reservations.
            this.clearBlockExcept(target);

            // Set new location
            target.setLocomotive(l);

            // A locomotive placed by hand claims this station if it has no home and the station is
            // free of claims.  Placing an already-homed locomotive somewhere else does not re-home it.
            this.claimHome(l, target);

            // A hand placement supersedes a turn the window has not written yet: it is a newer answer about the train.
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
     * The track non-atomic mode could release under a train: the atomic-routes gate's question.  Non-atomic mode is
     * unsafe only where a whole path is unmeasured (tailHasProvablyPassed's escape), so these are the unmeasured rails
     * from whose far end an active destination is reachable over unmeasured track alone.  That relies on every path
     * ending at a destination (bfs, HomeStaging.firstClearRoute).  An answered 0 is a measure (Adam: "0 lengths count
     * as measures, so non-atomic should be allowed").  Counted by rail, not by edge.
     *
     * @return the rails, named by the two places each joins, alphabetically (the message names the first three)
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

        // The points from which an active destination is reachable over unmeasured track, walked back from each
        // destination.
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
     * Every locomotive autonomy would run, with its train length, 0 where it has none (MT-533).
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
     * The locomotives autonomy would run that have no train length: the other half of what makes non-atomic mode
     * unsafe, since with a length of 0 an edge is handed back with the whole train on it.
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
     * Not synchronized and not a copy: a live view, read off-thread by the staging planner.  Taking this monitor on
     * the event thread would freeze the window behind a dispatch, and deadlock against the UI monitor
     * (TrainControlUI.updateVisiblePoints is synchronized on the UI and calls getPoints); a copy per call would make
     * this class's own sweeps quadratic.
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
     * Live and unsynchronized, as getEdges is.
     *
     * @return 
     */
    public Collection<Point> getPoints()
    {
        return this.points.values();
    }

    /**
     * Tells whoever is watching that the last locomotive thread has gone.
     * Nothing reads isRunning() on a timer, and a train idle when Stop is pressed ends no path, so without this the
     * buttons would stay greyed and the exit capture, gated on isRunning(), would skip silently.
     *
     * An empty edge list rather than null, because the registered callback walks it.
     */
    private void announceRunFinished()
    {
        for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
        {
            fireCallback(callback, new LinkedList<Edge>(), null, false);
        }
    }

    /**
     * Fires one repaint callback on the driving thread, so that a failure in the UI cannot kill the thread and strand
     * the train with its track locked.  Every callback goes through here.
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
            // Throwable: even an Error out of a repaint is no reason to strand a railway.
            this.control.log(e instanceof Exception ? (Exception) e : new Exception(e));
        }
    }

    /**
     * Throws a station's entry-guard signals red as a train arrives at the end of its journey (FR-096): a command on
     * an event, never undone; the next route sets it green.  Quiet about failures, since it runs on the driving thread.
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
     * The copy of its destination a train will stand on, on its way or while its route is set (OB-314), or null for a
     * train holding no path: a path to a may-turn square often ends on its turning copy, and a train that will not turn
     * is moved to the plain copy only on arrival.  Asks the arrival's own questions ahead of time; changes nothing.
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

        // Turned: the copy it will face on from, for a turn the operator answered; a plan's turned train stays put.
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
     * Whether a locomotive holds a point only because its locked path runs through it, neither its start nor its
     * destination (OB-315, OB-316).  Includes a path still being locked: the reservation refreshes the signal first.
     */
    private boolean onlyPassing(Locomotive loc, Point point)
    {
        // Through pathHeldBy, claims first, as every reader of one train's path.
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
     * Sets a station's protecting signal to match whether its platform is claimed: red when a train stands there or a
     * locked path reserves it, unless an active path holds it green (OB-340).  Derived, not remembered, so nothing has
     * to undo it.  Quiet about failures, since it runs on the driving thread.
     */
    void refreshProtectingSignal(Point point)
    {
        if (point == null || this.control == null) return;

        // Only while trains are being run, so arranging the railway by hand throws no real signals.  isRunning, not
        // isAutoRunning: a hand-driven train's arrival still protects its platform.
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
     * A lock per signal, so a protection refresh reads and commands as one step against a departure setting the
     * signal GREEN (OB-340): refreshOneSignal holds it from read to command; a departure holds it to command each
     * accessory of an edge it has taken and its exit guards.  Taken last: under it only the edges', the accessory
     * database's, the accessory's, Locomotive.accessoryMonitor and NetworkProxy's monitors; none of them is held while
     * this is waited for, so a wait here is for one refresh or command, never a sleep.
     */
    private final Map<Accessory, Object> signalLocks = new ConcurrentHashMap<>();

    /** The lock of one signal, or of any accessory a path commands: see signalLocks. */
    private Object signalLock(Accessory signal)
    {
        return this.signalLocks.computeIfAbsent(signal, any -> new Object());
    }

    /**
     * Throws one protecting signal to whatever the platforms behind it now say: per signal, since a signal may protect
     * several platforms and a platform have several signals.
     */
    private void refreshOneSignal(String accessory)
    {
        try
        {
            Accessory acc = this.control.getAccessoryByName(accessory);

            if (acc == null) return;

            // One step from the read to the command (see signalLocks).
            synchronized (this.signalLock(acc))
            {
                // Red if any platform this signal protects is claimed, which covers every copy of a square and two
                // stations paired to one signal.
                boolean claimed = false;

                for (Point other : this.points.values())
                {
                    if (!other.getProtectingSignals().contains(accessory)) continue;

                    Locomotive holding = other.getCurrentLocomotive();

                    // Not by a train only passing through (OB-315, OB-316); its own start and destination still count.
                    if (holding != null && !onlyPassing(holding, other))
                    {
                        claimed = true;
                        break;
                    }
                }

                // Only when it changes: this fires on every reservation while configureAndLockPath holds the monitor.
                // Remembered per signal, not per copy, so the memo cannot go stale through another copy.
                // Not red under a path that holds it green (OB-340): the train it lets go goes first.
                if (claimed && Boolean.FALSE.equals(this.getActiveAccSettings().get(acc))) return;

                Boolean showing = this.signalAspects.get(accessory);

                // The memo and the signal itself: a path across a SIGNAL tile can drive it green without protection
                // knowing, so the decision looks at isRed(), which every caller keeps current.
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
     * Whether this accessory is a signal protecting a square a locomotive is standing on: the half of the route guard
     * getActiveAccs cannot see, since protecting signals are driven by occupancy, not by paths.  Asked by routes and
     * the diagram's accessory tile alike.  Names are resolved, so "Signal 12" and "Switch 12" match.
     *
     * Not synchronized, as for getActiveAccs: the event thread asks it while configureAndLockPath holds the monitor.
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
     * Whether a train stands at a platform this signal protects, rather than a locked path only having reserved it,
     * so the refusal's sentence does not send the operator looking for a train that is elsewhere.  Not synchronized,
     * as for protectsAnOccupiedSquare.
     *
     * @param accessory the signal, or null
     * @return true when a train stands at a platform it protects; false when every platform it protects that is held
     *         is held only by a reservation
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

    /** Whether this signal is one of a point's protecting signals, by name or by the accessory a name resolves to. */
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
     * Whether commanding this accessory that way would take protection off an occupied platform.  Only the green
     * command can; the caller supplies the direction, since a tile toggles, the keyboard sets and a route holds it.
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
     * Whether a station autonomy may choose takes this train because the measured route in holds it (FR-087): a train
     * there is passing and may stand across the points (a parking berth may not; see whyABerthCannotHoldIt).  It only
     * allows, where the room rule refused, and never beyond measuredRouteIn.  Apart from whyTooLongForThisRoute so the
     * staging planner can ask it alone.
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

        // isAutoDestination is "Can Be Chosen In Full Autonomy".
        if (ending == null || !ending.isAutoDestination()) return false;

        return loc.getTrainLength() <= measuredRouteIn(path);
    }

    /**
     * How much measured track the route in holds, counted back from where it ends (FR-087): leg by leg while each is
     * measured, an answered 0 counting nothing (Adam: "we can't possibly have positive lengths everywhere because the
     * tracks just aren't that long."), and never back past a square the train turns at, as measuredRoomAtTheEndOf.
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
     * Why a parking berth cannot hold this train: it would lie across somebody else's road (Adam: "make a rule that
     * parking berths cant block any other edges, but not make that check for active stations.").  The places claimed
     * use the tail walk's arithmetic, written separately, so a change to one must check the other.  The berth's own
     * way in and out does not count; shared metal only.  Silent where the build gave no places.  Pure, so the staging
     * planner asks it too.
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
     * Why this train would run into its own tail on this route, or null when it would not (OB-294), judging the body
     * the tail walk sees.  Not synchronized, like isPathClear: the manual doors ask it on the event thread.
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
     * Where a train's body lies at a Point, with how far behind the head each place begins (OB-294): along the road
     * given (a Return Home plan's), else what the Point records.
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
     * Why this train would run into its own tail on this route, given where its body lies (OB-294; Adam: "the check
     * should pass if the train would be gone").  The head may come back to a place only once the tail has left it;
     * only measured track binds, the tightest return is named, and the question restarts after every turn.
     */
    static String whyItWouldMeetItsOwnTail(List<Edge> path, Locomotive loc, Map<String, Integer> reach)
    {
        return TrainFit.whyItWouldMeetItsOwnTail(path, loc, reach);
    }

    /**
     * Why this train does not fit anywhere this path would put it to rest, or null when it does (MT-262): the
     * destination and any square it turns at (MT-333).  The one predicate for "it does not fit", over both the
     * station's stated capacity and the measured track into it; the message carries the room and the train's length.
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
     * The measured room a train has to stand in at the end of this path (MT-262), prefixes included, so a turn
     * mid-route is asked by the same walk.  Pure, so the staging planner asks it too.  Not fenced on terminus.  Null
     * where the question does not arise (no train length, nothing measured): unmeasured is unknown, not zero.
     *
     * @param path the route, in order
     * @param loc the train
     * @return the total measured length behind the berth, or null when the question does not arise
     */
    public static Integer measuredRoomAtTheEndOf(List<Edge> path, Locomotive loc)
    {
        return TrainFit.measuredRoomAtTheEndOf(path, loc);
    }

    /** The aspect each protecting signal was last commanded to show, by accessory: one signal, one aspect. */
    private final java.util.Map<String, Boolean> signalAspects =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Brings every protecting signal into line with where the trains are, clearing the signalAspects memo.  Nothing in
     * the application calls this, and nothing should (OB-166; Adam: "Signals should only be touched when a route
     * activates."); it stays public for the tests that prove so.
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
     * Whoever is standing on another copy of this block, or null.  Walked, not indexed: an index would be one more
     * structure to keep in step with renames and rebuilds.
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
     * Whether this point is somewhere autonomy may send a train at all: a destination, in service, one autonomy may
     * choose, and a plain copy rather than a turning one.
     *
     * The clauses shared by every site that chooses; per-locomotive ones stay at the call.  Occupancy and locking are
     * not included: canReachAnyDestination asks what the railway allows.
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
     * Whether a train standing here could be sent anywhere at all: some destination is reachable by edges.  A copy of
     * a split square can have somewhere to go and nowhere to be sent.  Occupancy and locking are not considered.
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

                // Somewhere a train can be sent, by the shared clauses only: no locomotive here to ask the rest of.
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
     * Takes every other locomotive off the square this Point belongs to, so two trains are never in one place.  Only
     * from hand placement and the file load: in the shared setter it would sweep a locked path's reservations.
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

            // Said out loud: the displaced train may now be nowhere on the graph, and the copy written to is not always
            // the one the user was looking at.
            this.control.logf("autolayout.infoLocomotiveDisplacedFromSquare",
                displaced.getName(), claiming.getName());
        }
    }

    /**
     * Takes a locomotive off every Point except the one claiming it, every copy included.  Not synchronized:
     * setLocomotive holds the Point's monitor, and taking the layout's here would invert the lock order.
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
     * Turns the early release of passed track on or off.  A run the setting changes under is unlocked by what it did,
     * so no edge is given back twice; true-to-false mid-run can leave one edge held, which the interface prevents by
     * refusing the change while running.  Not locked: a click must not wait behind a route being thrown.
     *
     * @param atomicRoutes whether a route holds all its track until it finishes
     */
    public void setAtomicRoutes(boolean atomicRoutes)
    {
        this.atomicRoutes = atomicRoutes;
    }
    
    /**
     * Removes the timetable entry the operator chose, only if the row still holds the entry they were shown (OB-260).
     * Synchronized on this Layout, which capture appends under.
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
     * Empties the timetable, for Clear Timetable; synchronized, as the remove is (OB-260).  Not named clearTimetable,
     * the window's door: the monitor guard reads callers by name.
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

        // And nothing is on loan: this is the timetable, or the one a plan hands back.
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

        // Debug only: the audit logs every path it rejects, thousands of lines.
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

        // And each paused train it leaves away from its home (FR-117), which is not a failure.
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
     * Capture is not touched here: addTimetableEntry keeps the moves out of the timetable while timetableExecuting is
     * set.
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

        List<TimetablePath> staged = new LinkedList<>();

        for (HomeStaging.Move move : plan.getMoves())
        {
            TimetablePath entry = new TimetablePath(move.getLocomotive(), move.getPath(), 0);

            // No delay: every entry should start as soon as the one before it has, and be held back
            // only by the path actually being occupied
            entry.setSecondsToNext(0);

            staged.add(entry);
        }

        // The owner's own, for a fold to write while the plan occupies the timetable
        List<TimetablePath> owners = new ArrayList<>(this.timetable);

        this.setTimetable(staged);

        // After setTimetable, which clears it.  The plan assumes nothing else moves, so its moves run one at a time:
        // overlapping, two could contend for an edge the planner never considered.
        this.timetableSequential = true;

        // And so is this
        this.timetableOnLoan = owners;

        this.control.logf("autolayout.infoReturnToHomeLoaded", staged.size());

        return plan;
    }
        
    /**
     * Says whether the timetable's entries must run one at a time.  setTimetable clears it; the application no longer
     * calls this, and tests use it to set the flag by hand.
     *
     * @param sequential true to wait for each entry to arrive before starting the next
     */
    public void setTimetableSequential(boolean sequential)
    {
        this.timetableSequential = sequential;
    }

    /**
     * Whether the loaded timetable must run one train at a time: true only for a staging plan, built on a model in
     * which nothing else moves.
     *
     * @return true for one train at a time
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
            // Optionally skip turning on the functions.  Asked, not built: this runs on the train's thread, after
            // Unload too, and asking for the railway would build an empty one.
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
            // Optionally disable the arrival functions.  Asked, not built, as above
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
     * The same, with each train under way written on the one point it is kept at (getLastPointsReached), since a
     * locked path holds every point on it and a configuration refuses a train in several places.  Synchronized.
     *
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
        
        // The owner's timetable, not a staging plan borrowing it: a fold during Return Home must not save the plan.
        List<TimetablePath> owners = this.timetableOnLoan;

        for (TimetablePath p : owners != null ? owners : this.timetable)
        {
            timeTableJson.add(p.toJSON());
        }
        
        JSONObject jsonObj = Util.orderedJSON();

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

        // Written only when set and no plan has the timetable on loan, or a folded Return Home plan would reload as an
        // ordinary timetable, dispatching each entry as the previous one starts.
        if (this.timetableSequential && owners == null)
        {
            jsonObj.put("timetableSequential", true);
        }
        jsonObj.put("activateRoutes", this.isActivateRoutes());
        jsonObj.put("activateRouteIDs", new JSONArray(this.activateRouteIDs));

        // The trains paused (FR-117), by name and sorted so an unchanged file is the same file; only when there are
        // any.
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
     * Every train the database has paused, by name and sorted (FR-117): what toJSON writes under PAUSED_LOCOMOTIVES.
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

    /** A point's field written as a bare string or as an array of them, read as a list either way. */
    private static List<String> oneOrMany(JSONObject point, String key)
    {
        List<String> values = new ArrayList<>();

        JSONArray several = point.optJSONArray(key);

        if (several != null)
        {
            for (int at = 0; at < several.length(); at++) values.add(several.getString(at));
        }
        else if (point.optString(key, null) != null)
        {
            values.add(point.getString(key));
        }

        return values;
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

        // And the arrival sides, applied after the locomotives are placed: setLocomotive drops the side on every
        // change.
        java.util.Map<String, String> arrivalSideByPoint = new java.util.LinkedHashMap<>();

        // And the roads, held back likewise.
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

        // Which trains are paused (FR-117), every train told yes or no: the pause lives on the locomotive, which
        // outlives this railway.  Logged, since an import, a start-up and a rebuild all load through here.
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
           
        // Trains placed on a copy that is no station, warned about once every copy is read.
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

                // And which square of the diagram it is a copy of: what a rebuild carries a train by, as a rename can
                // move a name.
                if (point.has("square"))
                {
                    layout.getPoint(point.getString("name")).setSquare(point.optString("square", null));
                }

                // The signals thrown to red while this platform is claimed: a bare string or an array, both read.
                if (point.has("protectingSignal"))
                {
                    layout.getPoint(point.getString("name")).setProtectingSignals(oneOrMany(point, "protectingSignal"));
                }

                // The entry guard (FR-096), read in the same two shapes.
                if (point.has("entrySignal"))
                {
                    layout.getPoint(point.getString("name")).setEntrySignals(oneOrMany(point, "entrySignal"));
                }

                // Where the tail is, which facing cannot answer once a train has turned; applied after the trains are
                // placed.
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
                    // Points whose occupancy makes this station unavailable to autonomy (FR-001), resolved after the
                    // loop; a name that matches nothing is logged and dropped rather than refusing the whole layout.
                    blockersByPoint.put(point.getString("name"), oneOrMany(point, "blockedBy"));
                }

                if (point.has("home"))
                {
                    // The only place a home can dangle, since a file holds a name: reported and dropped, not refused.
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

                        // Taken as written: the home is the square, on the copy AutonomyBuilder.homeCopy chose.
                        homeAt.setHomeLoc(home);

                        // And whether it was set facing this copy's way (OB-282).
                        if (homeAt != null && home != null)
                        {
                            homeAt.setHomeFacingFixed(point.optBoolean("homeFacingFixed", false));
                        }
                    }
                }

                // Which way a train on this copy points, on a split square (OB-282).
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

                            // Named in the log when dropped, as the home and blockedBy are: a silently vanished
                            // exclusion is a restriction the operator believes is in force (OB-349).
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

                // The list's mode (FR-121): the locomotives allowed rather than the ones kept out
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
                            // Absent means not stated: a diagram placement writes only {"name": X}, so an absent
                            // trainLength keeps the live length (as speed does, MT-233); write 0 to clear it.

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
                            // Absent means not stated, as above; read as false, a reversible EMU would be refused a
                            // terminus.

                            // Only warn if this is not a station, after the loop, once the square's other copies are
                            // read.
                            if (!point.optBoolean("station", false))
                            {
                                placedOffAStation.add(new String[] {loc, point.getString("name")});
                            }

                            // De-conflict with other multi-units, before it is put down, as a placement does.
                            layout.clearMultiUnitConflictsWith(l);

                            Point placeOn = layout.getPoint(point.getString("name"));

                            // And with anything already on the same square, or a file written in that state would
                            // reinstate two trains on one track on every load.  Repaired, not refused; clearBlockExcept
                            // names the train it displaces.
                            layout.clearBlockExcept(placeOn);

                            // Place the locomotive
                            placeOn.setLocomotive(l);
                            
                            // Not reset: a placement carries only a name, so a departure function set in the locomotive
                            // window survives.

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

                            // Not reset, as for departureFunc above.

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
                            // Dropped and named, not refused (Adam: "drop the train and keep the rest."): one sold or
                            // renamed locomotive must not cost the whole railway.  The load door usually removes it
                            // first (OB-349).
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

        // Not for a barred copy of a station, where a turned train can stand; warned here, once every copy is read.
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

                // The side the track comes in by, as the builder split the square (OB-182); missing, readers use the
                // geometry.
                if (edge.has("entrySide") && edge.get("entrySide") instanceof String)
                {
                    e.setEntrySide(edge.getString("entrySide"));
                }

                // Where this edge runs, place by place (OB-207); missing, the covered-track sweep uses the whole edge.
                if (edge.has("places") && edge.get("places") instanceof JSONArray)
                {
                    List<String> ids = new LinkedList<>();

                    List<Integer> spans = new LinkedList<>();

                    List<String> answered = new LinkedList<>();

                    // What Mass Assign asks a length for, place by place (OB-297).
                    java.util.Map<String, String> asked = new java.util.LinkedHashMap<>();

                    JSONArray places = edge.getJSONArray("places");

                    // All of them or none: read positionally (the last is the square the edge arrives at), so a skipped
                    // entry would shift every position.  A bad entry falls back to the whole edge, the refusing side.
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
                            // Dropped, and said loudly (Adam: "drop the lock edge with a loud log line too."): a
                            // misspelt lock may let two trains onto one piece of metal.  The other locks still apply.
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
                // Each entry on its own: one naming a renamed or deleted locomotive or station is stale, and letting
                // the exception out would discard the whole timetable.
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

            // After setTimetable, which clears it.
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
                // Said out loud: dropped in silence, the train would simply never move.
                control.logf("autolayout.warnRunLocomotiveNotInDatabase", s);
            }
        }

        layout.setLocomotivesToRun(locsToRun);

        // The FR-001 restrictions, now that every point exists.  A name matching no point is dropped and logged, not
        // refused.
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

        // The arrival sides, now that every train stands where the file says: the file has the last word.  A name
        // matching no point is dropped silently; its tail went with it.
        for (java.util.Map.Entry<String, String> entry : arrivalSideByPoint.entrySet())
        {
            Point landed = layout.getPoint(entry.getKey());

            // Only onto a square whose placement was honoured, or the next train to reserve it inherits the tail.
            if (landed != null && landed.getCurrentLocomotive() != null)
            {
                landed.setArrivedFrom(entry.getValue());
            }
        }

        // And the roads, likewise; a road naming a missing edge is dropped whole (see roadNamed).
        for (java.util.Map.Entry<String, Object> entry : arrivalRoadByPoint.entrySet())
        {
            Point landed = layout.getPoint(entry.getKey());

            if (landed != null && landed.getCurrentLocomotive() != null)
            {
                landed.setArrivedAlong(layout.roadNamed(entry.getValue()));
            }
        }

        // Only now: until every point exists an honoured assignment cannot be told from one whose locomotive is unread.
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