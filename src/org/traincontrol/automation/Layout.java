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

    // Base time budget for path validation: the actual deadline is PATH_VALIDATION_MS * (accessories on
    // the path + 1) - see validatePathActuation - so paths with more accessories get proportionally more
    // time.  Not final so tests can shorten it; the wait exits early on success and only blocks the full
    // duration when an accessory never confirms.
    public static int PATH_VALIDATION_MS = 1000;

    // When true, configureAndLockPath verifies (via the CS echo) that every accessory on the path actually
    // reached its commanded state before releasing the locomotive; on a persistent mismatch it stops that
    // locomotive and releases its locks rather than letting it depart onto an unset path.  Tied to the
    // "Path Integrity Validation" preference in the UI, but respected headless via this flag.  Default on.
    public static boolean PATH_INTEGRITY_VALIDATION = true;

    // Every path validation failure is always logged to the console.  A UI popup is only raised once this
    // many failures have accumulated, and - unlike the console log - at most ONCE per Layout instance:
    // after that first popup, the console is considered sufficient and we do not keep interrupting the
    // user with repeated popups for the rest of this session.  Tunable.
    public static int PATH_VALIDATION_ALERT_THRESHOLD = 3;

    // Running count of path validation failures on this Layout.  Never reset - purely informational /
    // used by tests.  Per-instance (not static) so a re-created Layout - e.g. after the user loads a
    // different autonomy configuration or edits the layout - starts clean and never confounds old
    // failures with new state.  Guarded by this Layout since its locomotives validate paths concurrently.
    private int pathValidationFailureCount = 0;

    // Whether the one-time UI alert has already been shown for this Layout instance.  Guarded by this
    // Layout, same as pathValidationFailureCount.
    private boolean pathValidationAlertShown = false;

    // Maximum number of seconds another locomotive should yield for to the inactive locomotive
    public static final int YIELD_SECONDS = 30;

    /**
     * How long a boxed-in locomotive waits before looking for a path again, when no delay is set.
     *
     * pickPath shuffles every point, sorts, and BFSes the whole graph per candidate - so re-running it
     * as fast as the CPU allows pegs a core for every train that has nowhere to go.  A quarter second
     * still re-checks for freed track several times over and costs nothing.
     */
    private static final long NO_PATH_IDLE_MS = 250;

    /**
     * How autonomy chooses between the routes a train could take.
     *
     * An application preference rather than a property of a railway: it is how the user wants their
     * trains to behave, and it has to survive a configuration being reloaded.  Static and volatile for
     * the same reason - one running program, one answer, read on whichever thread is dispatching.
     */
    public enum PathPreference
    {
        /**
         * The route that runs past the fewest OTHER stations.
         *
         * Not the fewest sensors: a route counted in sensors prefers whatever happens to be lightly
         * instrumented, which is an accident of where the s88s were installed rather than anything
         * about the railway.  Stations are the places trains stop, so a route past few of them is a
         * route that interferes with few other trains - which is what somebody means by "direct".
         */
        FEWEST_STATIONS,

        /**
         * The route over the shortest track, added up from the lengths set on the tiles.
         *
         * Only as good as the lengths that have been set.  With none set every route measures zero and
         * this falls back to whichever was found first, so it is offered rather than assumed.
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
         * The route past the MOST other stations, and its two companions below.
         *
         * The mirror of each rule above, and not a joke: a layout that should look busy wants its
         * trains taking the long way round and calling past things, not going straight there.  Short
         * is right for a timetable and wrong for a railway somebody is watching.
         *
         * Free to implement, since every rule ranks by a number - these are the same numbers negated.
         */
        MOST_STATIONS,

        LONGEST_LENGTH,

        MOST_POINTS,

        /**
         * The route to whichever station has gone longest without a train.
         *
         * The rule an operator reaches for first, and the one that was missing: on a layout with a
         * favourite loop, every other rule here can leave the far corner of the railway untouched all
         * evening, because none of them knows or cares where trains have already been.  This one does.
         *
         * Ranked by when the destination last had an arrival, so a station never visited wins outright
         * and the busiest loses.  Station PRIORITY still applies first, so this arranges the stations
         * you are equally happy with rather than overriding the ones you are not.
         */
        LEAST_RECENTLY_VISITED,

        /**
         * Whatever is found first, which with the destinations shuffled is effectively at random.
         *
         * The DEFAULT, because it is what every version before this did.  A preference that changed
         * how existing railways behave the moment its owner upgraded would not be a preference, it
         * would be a regression with a switch next to it - and the people most affected drive from
         * scripts and would never see the menu.
         *
         * It is also the cheapest, so defaulting to it means the upgrade costs nothing either: the
         * ranked options have to enumerate the alternatives before they can compare them, and this one
         * stops at the first route that works.
         */
        RANDOM
    }

    private static volatile PathPreference pathPreference = PathPreference.RANDOM;

    public static void setPathPreference(PathPreference preference)
    {
        if (preference != null) Layout.pathPreference = preference;
    }

    public static PathPreference getPathPreference()
    {
        return Layout.pathPreference;
    }

    /**
     * What a route costs under the current preference.  Lower is better.
     *
     * The destination itself is never counted.  Every candidate ends at a station, so counting it would
     * add one to all of them and change nothing except to make a route to a station look worse than a
     * route to the same place.
     *
     * @param path the route being weighed
     * @return its cost, or 0 where the preference does not rank
     */
    private int costOf(List<Edge> path)
    {
        switch (Layout.pathPreference)
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

            case MOST_STATIONS:
                return -stationsOn(path);

            default:
                return 0;
        }
    }

    /**
     * How long a route is, from the lengths set on its tiles.
     */
    private int lengthOf(List<Edge> path)
    {
        int total = 0;

        for (Edge e : path) total += Math.max(0, e.getLength());

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
     * By block where a Point has one, exactly as sensorsOn counts, and by the Point's own identity
     * otherwise - a hand-built graph has no blocks and its Points already are one square each.
     *
     * Keying by uniqueId made the whole preference useless on the graphs it was written for. A train
     * arriving at Tunnel by its north copy recorded nothing about the south copy, so a later route to
     * the south copy found no visit and won outright as "never visited" - which on a derived graph is
     * every station, every time. The rule degraded to random, and worse than random: once every
     * station had one visited side, the just-visited ones kept beating the far corner nobody had been
     * near, which is the exact opposite of what it promises.
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
     * Records an arrival at a point, for a test that needs a known visit history.
     *
     * The real recording happens at the end of executePath, which means driving a train - minutes of
     * simulation to establish a fact that takes a line to state.  Exposed rather than reached for with
     * reflection, and named so nobody mistakes it for part of the running machinery.
     *
     * @param pointName the point that has just had a train
     */
    public void noteArrivalForTest(String pointName)
    {
        Point at = this.getPoint(pointName);

        // By unique id, which is what the real recording uses.  Keying by NAME here made the lookup
        // miss every time, so every station looked never-visited and the preference silently did
        // nothing - which the test caught, having been written to fail if it did nothing.
        if (at != null) this.lastArrival.put(recencyKeyOf(at), System.currentTimeMillis());
    }

    /**
     * How many stations a route runs PAST.
     *
     * Every edge end except the last, which is the destination.  Counting that would add one to every
     * candidate and change no ordering, while making a route to a station look worse than the same
     * journey described without its last step.
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
     * isRunning() used to be "the flag, or somebody is on a path", and there is a window where neither
     * is true and a train is nevertheless about to move: the thread has passed its `while (running)`
     * check and is inside pickPath - a shuffle, a sort and a breadth-first search of the whole graph -
     * and does not appear in activeLocomotives until executePath registers it.
     *
     * So a graceful stop could report itself finished while a locomotive was one instruction from
     * departing, and everything that asks "is it safe to edit now" believed it: moveLocomotive refuses
     * while running and would accept, and the interface re-enables what it disables during a run.  The
     * timetable test caught it - it waited for the layout to stop, got its answer, and had its very
     * next edit refused by the train it had waited for.
     *
     * Counted from the moment the thread starts to the moment it exits, so the window closes exactly.
     */
    private final java.util.concurrent.atomic.AtomicInteger locomotiveThreads =
        new java.util.concurrent.atomic.AtomicInteger();
    
    // Is the layout state valid?
    private boolean isValid = true;
       
    private final ViewListener control;
    private final Map<String, Edge> edges;
    private final Map<String, Point> points;
    private final Map<String, List<Edge>> adjacency;
    
    // Custom callbacks before/after path execution
    protected Map<String, TriFunction<List<Edge>, Locomotive, Boolean, Void>> callbacks;
        
    // List of all / active locomotives
    private final Set<Locomotive> locomotivesToRun;
    private final Map<Locomotive, List<Edge>> activeLocomotives;

    /**
     * Locomotives that have locked a path but are not yet running it.
     *
     * The cap on how many trains may be out at once was counted from activeLocomotives, and a
     * locomotive does not appear there until executePath registers it - which is AFTER the path has
     * been locked, the accessories thrown, and validatePathActuation has waited for them to confirm.
     * That wait deliberately holds no lock and takes up to a second per accessory, so the gap between
     * "this train has claimed its route" and "this train is counted" is seconds wide.
     *
     * Two trains taking disjoint routes could both cross it: each checked the cap, each saw the other
     * uncounted, and both went. The track was never at risk - the edges are exclusively locked either
     * way - but a cap is usually set for something the model cannot see, like what a booster will
     * carry, and quietly running more trains than asked is not a small thing to get wrong.
     *
     * Claimed inside the same monitor that locks the path, so the check and the claim cannot be
     * separated, and given up when the locomotive is registered or the path is released.
     */
    private final Set<Locomotive> takingPath;
    private final Map<Locomotive, List<Point>> locomotiveMilestones;
    private final Map<Locomotive, String> locomotivePendingS88;

    /**
     * The monitor for the pending-s88 bookkeeping, which is NOT the layout monitor.
     *
     * It used to be, and that put a RUNNING train behind a train that was still being dispatched.
     * configureAndLockPath holds the layout monitor across its whole lock loop - deliberately, because
     * claiming a path has to be atomic - and that loop sleeps CONFIGURE_SLEEP per edge and again per
     * accessory inside configureEdge, so it is held for seconds on a long path.  Meanwhile every
     * locomotive already under way calls updatePendingS88 immediately before waiting for its next
     * sensor, and that needed the same monitor.
     *
     * So a second train could be blocked here while its own train crossed the sensor it was about to
     * start waiting for AND cleared it again.  waitForOccupiedFeedback tests a LEVEL, so by the time
     * it ran the sensor read clear: it then waited for the next occupancy of a sensor the train had
     * already passed, and drove on without slowing or stopping.
     *
     * Nothing else waits on or notifies the layout monitor - waitForS88Reached and updatePendingS88
     * are the only pair - so moving them here costs nothing and unpicks the two jobs.
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
    private boolean atomicRoutes = true; // if false, routes will be unlocked as milestones are passed
    private boolean timetableCapture = false;

    // Staging plans are only valid executed one train at a time.  Set by loadReturnToHomeTimetable and
    // cleared by any other timetable load - see executeTimetable.
    private boolean timetableSequential = false;

    // Set for as long as executeTimetable is driving.  Capture records what the OPERATOR drives; a
    // timetable run recording itself appends to the very list being walked, and the dispatch loop
    // re-reads its own size.  Staging was only one of the two entrances into that.
    private volatile boolean timetableExecuting = false;

    // A staging entry that cannot run will not become runnable by waiting: nothing else is moving.  A
    // few retries ride out a sensor settling; beyond that the assumption is wrong and it must say so.
    /**
     * How long a timetable entry may go on being unexecutable before the run gives up, in ms.
     *
     * A TIME rather than a count of attempts, because the pause between attempts is the user's own
     * delay setting, which ranges from nothing to tens of seconds - so a count of attempts would mean
     * a wildly different wall-clock bound on one layout than on another, and the thing being bounded
     * here is how long a train stands still.
     *
     * (An earlier version of this said the pause "may be zero".  It may not: pacedWait falls back to
     * COMPLETION_POLL when both delay settings are zero, precisely so that no wait loop spins.  The
     * conclusion was right and the reason was wrong.)
     *
     * Minutes, deliberately. A train in a parallel timetable may legitimately wait a long while for
     * another to clear its way, and ending a run that would have worked is worse than a late finish.
     * Not final so a test can shorten it; there is no user-facing setting for it.
     */
    public static volatile long TIMETABLE_STUCK_MS = 180000;

    private static final int STAGING_MAX_ATTEMPTS = 3;
    private static final int STAGING_RETRY_PAUSE = 2000;

    // How often executeTimetable checks whether the run it dispatched has actually finished
    private static final int COMPLETION_POLL = 250;
    private int maxLatency = 0;
    private int maxActiveTrains = 0;
    
    // Route-related settings
    private boolean activateRoutes = false;
    private List<Integer> activateRouteIDs;

    // Track the layout version so we know whether an orphan instance of this class is stale
    // Volatile because the thread that writes it is never the thread that reads it: it is written
    // once, by whichever thread loads a layout, and read by every driving thread at six points in its
    // loop and by both timetable waits.  Without it a locomotive can keep reading the value it cached
    // before the reload and drive a whole path against a graph that has been retired.  Every other
    // piece of cross-thread state in this class - running, stagingInProgress, timetableExecuting - is
    // already volatile; this one was missed.
    private static volatile int layoutVersion = 0;

    // Whether a staging flow owns this Layout - set at the commit point and cleared when the flow
    // unwinds, mirroring the UI flag of the same lifetime.
    //
    // It lives here rather than only on the UI because six guards ask the *model* whether autonomy is
    // busy, and one of them - the sync adopting Central Station addresses - is not reachable from any
    // UI predicate at all.  Nothing is dispatched while a plan is being derived, so isRunning() alone
    // reads that whole window as idle, and a locomotive could be deleted, renamed or re-addressed out
    // from under a plan that is about to drive it.
    private volatile boolean stagingInProgress = false;

    // This instance's version, fixed at construction.  Compared against layoutVersion to answer "am I
    // still the current Layout?" - see isCurrentLayout
    private final int version;
    
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
        // These four are read by the UI (getActiveAccs, getActiveLocomotives, getReachedMilestones)
        // without holding the writers' synchronized(activeLocomotives) lock, so they must be
        // individually thread-safe.  The existing synchronized blocks still provide the writers'
        // compound atomicity; the concurrent collections add safe lock-free reads on top.
        // NOTE: ConcurrentHashMap rejects null keys/values, so accessors that take a Locomotive
        // must null-guard before touching these (see getDestination/getStart/getReachedMilestones/etc.).
        this.locomotivesToRun = ConcurrentHashMap.<Locomotive>newKeySet();
        // ConcurrentHashMap: setCallback (e.g. opening the graph view) puts on the EDT while
        // executePath iterates callbacks.values() on loco threads - a plain HashMap would CME.
        this.callbacks = new ConcurrentHashMap<>();
        this.activeLocomotives = new ConcurrentHashMap<>();
        this.takingPath = Collections.newSetFromMap(new ConcurrentHashMap<Locomotive, Boolean>());
        this.locomotiveMilestones = new ConcurrentHashMap<>();
        this.timetable = new LinkedList<>();
        this.homeStations = new LinkedHashMap<>();
        this.locomotivePendingS88 = new ConcurrentHashMap<>();
        this.activateRouteIDs = new LinkedList<>();
        
        Layout.layoutVersion += 1;
        this.version = Layout.layoutVersion;
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
        // A staging run drives the timetable it is executing, so capturing it would append each move
        // to the list being walked - and the dispatch loop re-reads its own size, so it then executes
        // the copies, fails them (the locomotive is at the path's END now), and reports a run that
        // actually succeeded as abandoned, with a stop.  Capture is for what the operator drives.
        if (!path.isEmpty() && loc != null && this.timetableCapture && !this.timetableExecuting)
        {
            timetable.add(new TimetablePath(loc, path, timestamp)); 
            
            // Calculate the delay time
            if (timetable.size() > 1)
            {
                TimetablePath second = timetable.get(timetable.size() - 1);
                TimetablePath first = timetable.get(timetable.size() - 2);

                // Stored on the LATER of the pair.  Everywhere else this field is read as the delay
                // BEFORE that entry runs: executeTimetable waits on the current entry's own value
                // before dispatching it, and the edit dialog says so in as many words - "enter delay
                // (seconds) before this route executes".  Writing the gap onto the earlier entry made
                // capture the only place using the opposite convention, so a captured timetable
                // replayed with every gap shifted one entry back - the first captured gap was never
                // applied and the last entry always started immediately.
                //
                // Affects newly captured timetables only.  Saved ones keep whatever they stored, and a
                // delay set by hand in the UI was already correct.
                second.setSecondsToNext(second.getExecutionTime() - first.getExecutionTime());
            }
            
            return true;
        }
        
        return false;
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
     * Returns all accessories along active routes
     * @return 
     */
    synchronized public Set<Accessory> getActiveAccs()
    {
        Set<Accessory> activeAccessories = new HashSet<>();
        
        for (List<Edge> activeEdges : this.activeLocomotives.values())
        {
            for (Edge e : activeEdges)
            {
                for (String acc : e.getConfigCommands().keySet())
                {
                    Accessory a = this.control.getAccessoryByName(acc);
                    
                    if (a != null) activeAccessories.add(a);
                }
            }
        }
        
        return activeAccessories;
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
        this.locomotiveMilestones.remove(l);

        // The claim on a path slot.  Left behind it lowers the cap on how many trains may run for the
        // rest of the session - a leak that makes the railway quieter and quieter with nothing to say
        // why, which is what its own comment in configureAndLockPath warns about.
        this.takingPath.remove(l);

        // The sensor this locomotive was said to be heading for.  A route condition asking "has it
        // reached that sensor yet" waits on this entry, and one left behind is an entry nothing will
        // ever clear - the thread evaluating that route parks until the locomotive is dispatched again,
        // which for a deleted one is never.  updatePendingS88 notifies, so anybody already waiting is
        // let go rather than left.
        updatePendingS88(l, null);

        // And every timetable entry that would run it.  TimetablePath holds the locomotive itself, so
        // executing the timetable afterwards drives something that is not in the database - and the
        // entry is written back out on every save, naming a locomotive the next load cannot resolve.
        for (java.util.Iterator<TimetablePath> entries = this.timetable.iterator(); entries.hasNext();)
        {
            if (l.equals(entries.next().getLoc())) entries.remove();
        }

        // Points hold their own references, and nothing else was clearing them: a deleted locomotive
        // stayed excluded forever, and its name kept being written into the exported JSON as an
        // exclusion for a locomotive that no longer exists.
        for (Point p : this.getPoints())
        {
            p.removeExcludedLoc(l);

            // The home assignment, which is now the same shape as the exclusion above it: the Point
            // holds the locomotive, so this is identity like its neighbour rather than a name
            // comparison that had to be remembered separately.
            if (l.equals(p.getHomeLoc())) p.setHomeLoc(null);

            // And the locomotive STANDING here, which is the sixth thing this sweep did not clear
            // (UR-3).
            //
            // Left behind, the square is occupied by a train that does not exist: nothing can ever be
            // routed through it again, and there is no way to take the train off because the train is
            // gone. Worse, Point.toJSON writes the name back out, so the next load reports a locomotive
            // that is not in the database - and that invalidates the WHOLE configuration, which then
            // answers null for every point in it.
            //
            // Exactly the shape of the two lines above, both of which had to be added later for the
            // same reason. A sweep over "everything that names this locomotive" is a list, and a list
            // is a thing one can be missing from.
            if (l.equals(p.getCurrentLocomotive())) p.setLocomotive(null);
        }

        // Releases the home claim too.  Without this the station stays claimed by a locomotive that no
        // longer exists, and nothing placed there afterwards could ever have a home of its own - the
        // same shape as the exclusions above, which outlived their locomotive until IND-M4.
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

        // Already has a home: keep it.  Moving a locomotive by hand does not re-home it.
        if (this.homeStations.containsKey(l)) return;

        // Station already spoken for: this locomotive is a free agent
        if (this.homeStations.containsValue(p)) return;

        this.homeStations.put(l, p);
    }

    /**
     * Recomputes every home from the assignments and the current placements.
     *
     * One method for both entrances - loading a file and editing an assignment - so the two cannot
     * drift into deriving homes differently.
     *
     * Assignments win, and are applied first: a station assigned to a locomotive is that locomotive's
     * home whether or not it is standing there, and whether or not it is on the graph at all.  Whatever
     * is left then falls back to the original rule, the station a locomotive is standing on.  With no
     * assignments anywhere - every layout that existed before this - the fallback is the only thing that
     * runs, and the result is exactly what claiming at load produced.
     *
     * A dangling assignment is no longer possible here: a Point holds the LOCOMOTIVE, so there is
     * nothing to resolve and nothing that can fail to.  Reading a name out of a file is the one place
     * that can still meet one, and parseAuto reports it there.
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
                // Dropped, for the reason a dangling name is dropped: it can never be honoured.  One
                // locomotive has one station, and keeping the loser would re-warn on every load and be
                // written back out on every save.
                //
                // This used to add "so only a hand-edited file reaches here".  True when it was
                // written; the autonomy setup gained its own home editor a fortnight later and did not
                // sweep, so a MENU reached it - and which of the two assignments survived was decided
                // by iteration order, with this log line as the only notice.  AutonomySession.setHome
                // sweeps now, so a hand-edited file is once again the way in - but the sentence is left
                // out rather than restored, because it is what stopped anybody looking here (TD-8).
                this.control.logf("autolayout.warnHomeLocomotiveAssignedTwice", l.getName(), p.getName());
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

        // One station per locomotive: assigning it somewhere new gives up wherever it was assigned
        // before, or two stations would be waiting for the same train and neither could be satisfied.
        if (loc != null)
        {
            for (Point other : this.points.values())
            {
                if (other != p && loc.equals(other.getHomeLoc())) other.setHomeLoc(null);
            }
        }

        p.setHomeLoc(loc);

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
     * A copy, not a view.  This map is rebuilt wholesale - cleared and repopulated - and is also
     * written by hand placement, station deletion and locomotive deletion, none of which happen on the
     * thread that reads it.  The planner reads it to build a plan, and an unmodifiable *view* left that
     * read walking the live map while another thread cleared it: a ConcurrentModificationException in a
     * worker with nothing to catch it, or the quieter outcome of a plan derived from half the homes.
     *
     * Copying under the monitor makes every reader safe without asking each of the writers to know
     * about the readers, which is the version of this that has to be got right once rather than at
     * every call site that will ever mutate a home.
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
     * Whether this Layout is still the one in use, or has been superseded by a newer one.
     *
     * executePath used to answer this by capturing layoutVersion when a run began and comparing the
     * capture at each milestone.  That capture happened *after* configureAndLockPath, which waits for
     * the Central Station to confirm every accessory on the path - seconds, on a path with several.  A
     * reload landing in that window was captured as the new version, so the comparison matched at every
     * milestone and the locomotive drove the entire path against a graph that had already been retired.
     *
     * Asking whether this instance is the newest has no window to land in: the answer does not depend on
     * when it is asked.
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

        if (this.invalidReason == null) this.invalidReason = "(no reason was recorded)";
    }

    /**
     * Why this layout was invalidated, kept apart from lastError.
     *
     * lastError is written by every path that fails for any reason - a busy edge, an excluded
     * locomotive, a destination already occupied - so by the time somebody reads "the configuration is
     * invalid" it holds whatever went wrong most recently, which is usually the failed path they were
     * looking at rather than the invalidation from minutes earlier.  Printing it beside the refusal
     * was worse than printing nothing: it looked like an explanation and named the wrong thing.
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
        this.invalidReason = message;
        Layout.lastError = message;
        this.control.log(message);
    }

    /**
     * @return why this layout was invalidated, or null while it is still valid
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
     * The simulation announces each path point by setting its s88, then spawns a detached thread to
     * clear it behind the train after a delay.  That clear used to fire unconditionally.  When two
     * consecutive path points share one sensor - BottomMainPost and TunnelLongParkReverse both
     * report 2013 on the author's layout - the stale clear could land after the next point's
     * announcement: inside the 201ms occupancy-hold window, or between the announcement and the
     * wait.  Either way the waiter ended up blocked on a sensor no producer would ever set again -
     * a permanent, silent stall of the run (observed live: cleared one millisecond after the
     * milestone).  Real hardware is immune, because a physical sensor spanning both points simply
     * stays held; only the per-point pulse model manufactures the false gap.
     *
     * The guard: each announcement bumps the sensor's epoch under the epoch's own lock; each clear
     * re-checks under the same lock and stands down if any later announcement re-armed the sensor.
     * The last occupant still clears, so the pulse-and-clear behaviour every other test relies on
     * is unchanged for unshared sensors.
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
        // The clear is spawned detached with a delay of up to maxDelay SECONDS, so a run can end - and
        // this Layout be replaced by a reload - while clears are still pending.  The epoch map is per
        // instance, so an orphan's clear would consult a map the NEW run's announcements never bump,
        // pass its own stand-down check, and clear a sensor the new run is waiting on: the same wedge,
        // one Layout boundary later.  Only the clear side needs the fence - a run cannot span a
        // reload, so an announcement can never come from an orphan.
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
     * Returns auto or manual running status
     * @return 
     */
    public boolean isRunning()
    {
        return this.running || !this.getActiveLocomotives().isEmpty()
            || this.locomotiveThreads.get() > 0;
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
     * Stops locomotives gracefully (i.e., at their next station for those that are running)
     */
    public void stopLocomotives()
    {
        this.running = false;
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

        // The signals are about a railway that has since been arranged by hand
        refreshAllProtectingSignals();
        
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
                // Refused here, not left to runLocomotive's throw.  That throw is caught below and
                // answered by invalidating the ENTIRE layout and stopping every locomotive, so one
                // locomotive placed on the graph without a speed ever being chosen turned Start into
                // "configuration invalid, must reload".  Skipping it - exactly as an inactive
                // locomotive is skipped above - leaves every other locomotive running.
                //
                // The guard in executePathInternal does not help here: Start never reaches it.
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
            } 
            catch (Exception ex)
            {
                this.invalidate(
                    I18n.f("autolayout.errorFailedToRunLocomotive", loc.getName())
                );
                this.stopLocomotives();
            }
        });
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
     * @param name a unique identifier for the point
     * @param isDest are trains allowed to stop at this point?  Requires s88 feedback to work properly.
     * @param feedback address of the corresponding feedback module, or null if none
     * @return
     * @throws Exception
     */
    public Point createPoint(String name, boolean isDest, String feedback) throws Exception
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
    public Edge createEdge(String startPoint, String endPoint) throws Exception
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
     * lastError is set either way - the caller that reports per-destination reasons reads it.
     * What the flag turns off is the log line, for the callers that are ENUMERATING paths rather
     * than validating a chosen one.  For those, a refusal is the ordinary answer and not an error:
     * getPossiblePaths asks about every candidate route from a point, so on a layout with trains
     * parked across it nearly every question is answered no.  One test run produced 143,353 of
     * these lines - 38 distinct messages, 36MB, up to 77 in a single millisecond - which is slow
     * enough to matter and far too noisy to read.
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
     * How many trains are out, counting the ones that have claimed a route but not yet set off.
     *
     * A union rather than a sum, so that a locomotive which is both claiming and registered - the
     * moment between the two, however it is ordered - is one train and not two.
     *
     * @return the number of distinct locomotives underway
     */
    private int trainsUnderway()
    {
        Set<Locomotive> both = Collections.newSetFromMap(
            new java.util.IdentityHashMap<Locomotive, Boolean>());

        both.addAll(this.activeLocomotives.keySet());
        both.addAll(this.takingPath);

        return both.size();
    }

    /**
     * @param logFailures false when enumerating candidate paths, where a refusal is the ordinary
     *                    answer rather than something worth a log line.  lastError is still set.
     */
    public boolean isPathClear(List<Edge> path, Locomotive loc, boolean logFailures)
    {
        if (this.maxActiveTrains > 0 && this.isAutoRunning() && trainsUnderway() >= this.maxActiveTrains)
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
                    I18n.f("autolayout.errorEdgeOccupied", e.getName())
                );
                return false;
            }

            // The same edge going in the opposite direction
            if (this.getEdge(e.getOppositeName()) != null && this.getEdge(e.getOppositeName()).isOccupied(loc))
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorEdgeOccupied", e.getOppositeName())
                );
                return false;
            }

            // Excluded intermediate points cannot be traversed.  Non-stations only, and deliberately:
            // a station's exclusion list says the locomotive may not STOP there, which is checked
            // against the path's destination, not against the points it drives past.  Blocking passage
            // through excluded stations as well was tried and reverted - on the author's own layout it
            // removed 45% of the reachable station pairs for two locomotives, because it uses station
            // exclusions on through routes.  See HomeStaging.canEnter for the other half of the rule.
            if (!e.getStart().isDestination() && e.getStart().getExcludedLocs().contains(loc))
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorIntermediatePointExcluded", e.getStart().getName())
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

            // Ensure no lock edge is already held by another route.
            //
            // Held, not occupied.  A lock edge is track kept clear so two routes cannot take one throat
            // at once; a train STANDING at the point one leads to is not using the throat, and cannot
            // be - a sensor is an endpoint of the edges that meet there and part of the path of none of
            // them.  See Edge.isLockHeld, which also explains why nothing is given up by asking only
            // this.
            for (Edge e2 : e.getLockEdges())
            {
                if (e2.isLockHeld(loc))
                {
                    logPathError(loc, path, logFailures,
                        I18n.f("autolayout.errorLockEdgeOccupied", e2.getName())
                    );
                    return false;
                }
            }
        }
        
        // An INTERMEDIATE point that has been switched off may never be driven through, whatever asked
        // for the route.  This one is deliberately NOT fenced behind isAutoRunning, unlike the endpoint
        // rules above and below it: a manually chosen route may still START from a deactivated point,
        // which is how a train held in place is driven out by hand, and may still FINISH on one, which
        // is how a route to a parked-up berth is picked.  Passage is the absolute case, because a train
        // crossing a point the operator switched off is the one place where nobody chose that point at
        // all - the route was only trying to get past it.
        //
        // Intermediates are exactly the END of every edge but the last: any edge start other than the
        // first is the previous edge end, so this visits each intermediate once and neither endpoint.
        for (int i = 0; i < path.size() - 1; i++)
        {
            if (!path.get(i).getEnd().isActive())
            {
                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorInactiveIntermediatePoint", path.get(i).getEnd().getName())
                );
                return false;
            }
        }

        // Check train length
        if (!path.get(path.size() - 1).getEnd().validateTrainLength(loc))
        {
            logPathError(
                loc,
                path,
                logFailures,
                I18n.f("autolayout.errorTrainLengthTooLong", path.get(path.size() - 1).getEnd().getName())
            );
            return false;
        }

        if (!path.get(path.size() - 1).getEnd().isActive() && this.isAutoRunning())
        {
            logPathError(
                loc,
                path,
                logFailures,
                I18n.f("autolayout.errorInactiveStationInAutoRun", path.get(path.size() - 1).getEnd().getName())
            );
            return false;
        }

        // Only reversible locomotives can go to a terminus.
        //
        // Deliberately still the only reversal rule at THIS tier.  A first attempt put the reversing
        // points here too, which is the wrong height: isPathClear is what every tier passes through,
        // so it took the manual route menu and the staging planner with it - and a test guarding
        // exactly that had been written years before, with a javadoc warning against this change.
        // Full autonomy's own rule lives where full autonomy chooses, in reversesAlongTheWay.
        if (path.get(path.size() - 1).getEnd().isTerminus() && !loc.isReversible())
        {
            logPathError(
                loc,
                path,
                logFailures,
                I18n.f("autolayout.errorTerminusNotAllowedForNonReversibleLoc", loc.getName())
            );
            return false;
        }

        // A station held back while another point has a train STANDING on it (FR-001).
        //
        // The other half of this setting is built into the configuration as lock edges, which ask
        // whether that approach is held by a ROUTE - Edge.isLockHeld says so, and says why it stops
        // there: counting a parked train made a locomotive beside a junction a permanent roadblock, and
        // two could deadlock.  That reasoning is about track a route needs to CROSS.  This is a
        // different question, asked only of a path DESTINATION and only about squares somebody named,
        // so neither hazard applies - nothing here can hold up a route that was not going to that
        // station anyway.
        //
        // Behind isAutoRunning, like the endpoint rules above it: this shapes what AUTONOMY chooses,
        // and a person dispatching by hand is looking at the railway and has said what they want.
        //
        // The whole BLOCK, not just the named Point.  A square emitted as several copies is one piece
        // of track, so a train on the eastbound copy of the watched point is standing on it, and asking
        // only the copy that carries the name would answer clear with a train there.
        //
        // The points themselves, so there is nothing to resolve here and nothing that can fail to.
        // A restriction naming a point that does not exist is dropped when the file is read, which is
        // the one place a name is still involved.
        if (this.isAutoRunning())
        {
            Point destination = path.get(path.size() - 1).getEnd();

            for (Point watched : destination.getBlockedBy())
            {
                if (watched.getBlockLocomotive() == null) continue;

                logPathError(loc, path, logFailures,
                    I18n.f("autolayout.errorDestinationBlockedByPoint",
                        destination.getName(), watched.getName()));

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
     * Function to configure an accessory.  This is called from the edge configuration lambda (instead of calling control directly) as defined in layout.createEdge 
     * so that the graph can keep track of conflicting configuration commands, and invalidate those paths accordingly
     * @param e - the edge
     * @param preConfigure - when set, simulate sequence of commands and record validity status
     * @return false if the edge could not be configured, or - when previewing - cannot be
     */
    private boolean configureEdge(Edge e, EdgeConfigurationState preConfigure)
    {
        boolean result = true;

        // Released before thrown, and otherwise by name.
        //
        // This used to iterate the map's own key order, which is no order at all.  It matters because a
        // three-way turnout is two drives on consecutive addresses: commanding the diverging one before
        // the other has been released puts both blade sets over at once, a combination that routes
        // nowhere and that some mechanisms bind in.  Releasing first means the only transient the
        // turnout passes through is straight.
        //
        // Applied to every edge rather than to detected pairs: for independent accessories the order is
        // immaterial, so there is nothing to weigh against making it deterministic - and a pair is only
        // recognisable from an address convention this method cannot see.
        //
        // The guarantee stops at the edge boundary.  configureAndLockPath configures a path's edges in
        // path order, so a pair split across two edges executes in that order whatever this sort says.
        // Both commands of one turnout belong on one edge.
        List<String> names = new ArrayList<>(e.getConfigCommands().keySet());

        names.sort((a, b) ->
        {
            boolean aThrows = Accessory.isThrow(e.getConfigCommands().get(a));
            boolean bThrows = Accessory.isThrow(e.getConfigCommands().get(b));

            return aThrows == bThrows ? a.compareTo(b) : (aThrows ? 1 : -1);
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

                if (!acc.setState(state))
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
        
        // Releases any home claim on it - the twin of locDeleted's release, on the value side of the
        // map.  Without this the claim outlives the station: its locomotive still counts as misplaced,
        // so Return Home stays lit, and every press reports "cannot reach their home station" naming a
        // station that no longer exists - stable, unactionable, and self-inflicted.
        //
        // By name, because that is how every consumer compares points, and a claim held against an
        // object equal by name is the same claim.
        this.homeStations.values().removeIf(home -> home != null && home.getName().equals(name));

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

        // Both preconditions used to live only in the one dialog that calls this.  A second caller -
        // a keyboard shortcut, a bulk rename - would have inherited graph corruption: points.put
        // overwrites a point that already carries the new name and the edge-key rebuild below then
        // drops its edges, and a rename mid-run mutates Point.hashCode under live visited sets.
        if (!newName.equals(name) && this.getPoint(newName) != null)
        {
            throw new Exception(I18n.f("autolayout.errorPointAlreadyExists", newName));
        }

        // isRunning, not the bare isAutoRunning flag: it also counts in-flight locomotives, so the
        // guard holds through the graceful-stop wind-down while paths still walk the graph.  And the
        // staging planner runs bfs over these structures with nothing dispatched at all, which is
        // exactly the window the bare flag waves through.
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
        // Lock the path and send the accessory commands under the Layout monitor.  Holding it here is
        // fine - path locking must be atomic - but the validation wait below must NOT hold it, so other
        // locomotives' path checks are not blocked for the (possibly multi-second, scales with path size -
        // see validatePathActuation) validation wait.
        boolean configureFailed = false;
        int edgesLocked = 0;

        // A THROW out of the lock loop, kept to be rethrown once the track has been given back.
        //
        // The two returned-false failures below release what they took; a thrown one did not.  It went
        // straight out to executePath's handler, which deliberately does not unlock - "the locomotive
        // may be physically standing on those edges, and releasing them would let another train be
        // routed into occupied track".  That is true of a failure MID-RUN and false here: this method
        // returns before loc.setSpeed is issued, so the train has not moved.  The rule was lifted from
        // the case whose precondition made it safe.
        //
        // Two things were left behind and neither ever clears.  Every edge taken stays occupied for the
        // session, with its lock edges, so that track is refused to every train.  And reserve has put
        // the locomotive on those Points and deliberately does not sweep, so it stands in several
        // places at once - pickPath then takes the first in iteration order, which can be a mid-path
        // Point it is not standing on, and throws real ironwork for a departure from a station the
        // train is not at.
        RuntimeException lockFailure = null;

        synchronized (this)
        {
            // Return if this path isn't clear
            if (!this.isPathClear(path, loc))
            {
                this.control.logf("autolayout.errorPathOccupied");
                return false;
            }

            // Claimed HERE, in the same monitor that just did the counting.  Anywhere later and the
            // check and the claim can be pulled apart by another thread doing its own check in between
            // - which is exactly what let two trains past a cap of one.
            this.takingPath.add(loc);

            try
            {
            for (Edge e : path)
            {
                // Counted as TAKEN before it is taken, not after.
                //
                // The release below covers path.subList(0, edgesLocked), so an edge counted afterwards
                // is one a throw out of setOccupied or reserve leaves occupied and outside the release -
                // the single edge the recovery provably could not reach.  Counting first can only ever
                // release an edge that was never taken, and setUnoccupied on an edge that is already
                // clear does nothing.
                edgesLocked++;

                e.setOccupied();

                // RESERVED, not placed.  A locked path holds every one of its points for this
                // locomotive at once, which is the whole mechanism that keeps a junction behind the
                // train reserved against a second train reaching it another way.  setLocomotive would
                // sweep the train off the point it was just reserved on the moment the next point is
                // reserved, collapsing the reservation to the destination and freeing every junction -
                // and, on a path-integrity failure, stranding the train on no point at all.
                e.getEnd().reserve(loc);

                // isPathClear already previewed the configuration, so this should not fail - but if an
                // accessory went missing in between, the locomotive must not be released onto a path we
                // were unable to set up.  Stop here and let the caller below release the locks.
                if (!this.configureEdge(e, null))
                {
                    configureFailed = true;
                    break;
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
            // Exactly what a configuration failure does, and only the edges actually taken: releasing
            // the rest would call setUnoccupied on edges never held, which also clears their lock edges
            // - and those may belong to another locomotive by now.
            //
            // Unless nothing was taken at all, in which case there is nothing to hand back and asking
            // would make the recovery a different failure: handleMisconfiguredPath ends by reserving
            // path.get(0).getStart(), which on an empty list throws - replacing the exception that
            // explains what went wrong, and skipping the claim release below so the locomotive stays
            // claimed for the session.  The configureFailed path cannot reach zero; a throw can.
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
                    // Autonomy is being stopped - abort validation without flagging a misconfiguration
                    // (the loco is not going anywhere).  Preserve the interrupt for downstream code.
                    Thread.currentThread().interrupt();
                    return true;
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

        // Guarded, because working out WHAT to say must not be able to stop the release below.
        //
        // This is also reached from the lock loop's failure handler, and the failure being handled
        // there may be the same call that fails here - reading a path's configuration is how an edge is
        // set up in the first place.  A release that cannot happen leaves the track occupied for the
        // rest of the session, which is far worse than an operator message that names nothing.
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

            // Provably at its start, whatever the path's shape.  Clearing the ends above leaves the
            // train on its start point, which it never left - except on a path that loops back through
            // that point, where the start is also an end and would be cleared.  Re-reserved rather than
            // placed, so a train that legitimately sat on several points is not swept down to this one.
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
        // Releasing the path releases the claim on a slot with it.  The claim is taken when a path is
        // locked, so this is its other natural end - and without it, anything that locks and unlocks
        // without ever running the train would lower the cap for the rest of the session.
        this.takingPath.remove(loc);

        List<Edge> output = new LinkedList<>();
        
        for (int i = 0; i < path.size(); i++)
        {
            Edge e = path.get(i);
            
            if (this.atomicRoutes)
            {            
                if (i == 0)
                {
                    e.getStart().setLocomotive(null);
                }

                e.setUnoccupied();

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
                    e.setUnoccupied();
                }
                else
                {
                    output.add(e);

                    // Another locomotive has taken the end point, so we must not touch the edge's own
                    // flag - it may be that locomotive's lock now.  Its LOCK edges still have to be
                    // released: executePath's early unlock uses setLockedEdgeUnoccupied, which
                    // deliberately leaves lock edges held until the path completes, so skipping them
                    // here left them held for the rest of the session, permanently blocking every path
                    // that crosses them.
                    //
                    // Safe for a crossing declared symmetrically - each of the two edges naming the
                    // other as a lock edge, written symmetrically - because the crossing
                    // edge is then itself part of any conflicting path, so isPathClear rejects that path
                    // on the edge's own occupancy flag while we hold it.
                    //
                    // Two claims that used to stand here are false and the reasoning must not be
                    // extended on either.  Symmetry is NOT "how the editor writes them" - only
                    // GraphReducer writes symmetric locks, GraphEdgeEdit.applyLockEdges writes one
                    // direction, and the sample layout that ships carries 104 asymmetric relations out
                    // of 118.  And isPathClear DOES inspect lock edges, through Edge.isLockHeld.
                    //
                    // Occupancy is still a flag rather than a count, so the caution about a hand-edited
                    // autonomy.json in which two edges name a third they never traverse stands on that
                    // ground alone.
                    for (Edge lockEdge : e.getLockEdges())
                    {
                        lockEdge.setLockedEdgeUnoccupied();
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
     * Finds the shortest path between two points using BFS
     * @param start
     * @param end
     * @param excludePaths
     * @return
     * @throws Exception 
     */
    public List<Edge> bfs(Point start, Point end, List<List<Edge>> excludePaths) throws Exception
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
        
        // A set, not a list: this is probed with contains() once per neighbour of every point dequeued,
        // which was a linear scan against a LinkedList.  Point overrides both equals and hashCode, so
        // membership is decided identically - only the cost changes.
        //
        // Deliberately still marked on DEQUEUE below rather than on enqueue.  Marking on enqueue is the
        // usual BFS refinement and would stop a point being queued more than once, but it would change
        // what this method finds: all three callers pass excludePaths, and the search depends on
        // reaching a point by several different routes in order to find one that is not excluded.
        // Marking on enqueue would explore only the first route to each point and could then fail to
        // return an allowed alternative that exists.
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
                else if (!visited.contains(next.getEnd()))
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
     * Whether a path would turn the train round on its way somewhere else.
     *
     * executePathInternal stops and reverses at EVERY reversing point a train reaches, not only at the
     * end of a path - so a route through one is a shunting move in the middle of a journey: the train
     * halts somewhere it was not going, changes direction, and carries on.
     *
     * This used to bar only reversing STATIONS, on the reasoning that a berth has to be off the
     * through-network as well as barred as a destination, and to leave the reversing loops and
     * headshunts alone because being driven through is their purpose. That exemption is what let a
     * train with a locomotive at one end only be turned round mid-journey and sent on running
     * backwards in service.
     *
     * The rule Adam settled on is simpler than picking that apart, and it is a rule about the railway
     * rather than about which flag a point carries: IN FULL AUTONOMY A TRAIN IS ONLY EVER REVERSED AT
     * A TERMINUS. Everything else that would turn it round is off the through-network, berth and
     * headshunt alike.
     *
     * The tier matters as much as the rule. This is asked by pickPath, by the probe that mirrors it,
     * and by the "why is it not moving" explanation - the three places full autonomy chooses. It is
     * NOT asked by isPathClear, so a hand-driven move and the staging planner bringing a train home
     * can still use a headshunt, which is what they are for and where somebody is watching.
     *
     * The origin is exempt - a train standing on one is free to leave it - so only the END of each
     * edge is tested.
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
     * getPossiblePaths answers the MANUAL question - what the right-click route menu offers - and by
     * design that includes reversing stations and destinations that exclude the locomotive, neither of
     * which pickPath will ever choose.  Yielding on the manual answer makes a train parked on purpose
     * read as one merely waiting its turn: its idle time only grows, so it wins every "longest waiting"
     * comparison from then on, and each running locomotive stops for YIELD_SECONDS to let it go first,
     * over and over, for a dispatch that will never happen.
     *
     * pickPath itself cannot serve as the probe - it sleeps for minDelay when it finds nothing - so the
     * enumeration is filtered instead.  Only the clauses that diverge are re-tested here; active,
     * unoccupied, is-a-destination and reachable-by-a-clear-path have already been applied by
     * getPossiblePaths and isPathClear.
     *
     * Every clause pickPath applies to its candidates has to be mirrored here, and that is the whole
     * maintenance burden of this method: the two fell out of step once already, when berths were barred
     * as intermediates in pickPath alone, and a locomotive whose only route out crossed a berth went
     * back to collecting false yields.  Anything added to pickPath's selection belongs here too.
     */
    private boolean hasAutonomousDestination(Locomotive loc)
    {
        // uniqueDest false on purpose.  The flag only gates what the enumeration COLLECTS - the loop
        // inside runs to exhaustion either way - so asking for every clear path costs nothing extra,
        // and asking for one per start/end pair would make this probe under-report: the single path
        // kept for a pair may be the one that crosses a berth while a berth-free alternative to the
        // same destination exists, and pickPath would have found that alternative.
        for (List<Edge> path : this.getPossiblePaths(loc, false))
        {
            Point end = path.get(path.size() - 1).getEnd();

            if (!end.isReversing() && end.isAutoDestination()
                    && !end.getExcludedLocs().contains(loc)
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
                List<Edge> path = this.pickPath(loc);

                if (path != null)
                {
                    // Guarded for the same reason the callbacks are, and it is the same consequence.
                    // executePath's own handler deliberately does NOT unlock a failed path, so an
                    // exception escaping here killed this locomotive's thread with its track still
                    // held - autonomy quietly lost a train and everything that needed that track.
                    // Logged and carried on instead: the next pass picks a fresh path.
                    try
                    {
                        this.executePath(path, loc, speed, null);
                    }
                    catch (Throwable e)
                    {
                        this.control.log(e instanceof Exception ? (Exception) e : new Exception(e));
                    }
                }
                else if (this.getMinDelay() <= 0)
                {
                    // Boxed in, and no delay configured to throttle the retry.  Without this floor the
                    // loop re-runs pickPath - a shuffle, a sort and a BFS of the whole graph - as fast
                    // as the CPU allows, pegging a core for every train that has nowhere to go.
                    loc.delay(NO_PATH_IDLE_MS);
                }

                // Skipped once the run has been asked to stop.
                //
                // This paces one path against the next, and after a graceful stop there is no next -
                // so paying it delays only the moment the thread exits.  That matters now that
                // isRunning() waits for these threads: without this, the interface stayed disabled for
                // the length of the user's own delay setting after the last train had already
                // arrived, which reads as the stop having hung.
                if (running) loc.delay(this.getMinDelay() * 1000);

                // If another locomotive is falling behind, attempt to yield to it
                if (this.isAutoRunning() && this.maxLocInactiveSeconds > 0)
                {
                    Locomotive yieldLoc = this.checkForSlowerLoc(this.maxLocInactiveSeconds, loc);

                    // running re-checked HERE, not only at the top of the block.  This waits up to
                    // YIELD_SECONDS - thirty of them - for a train to move, and after a stop no train
                    // is going to.  The outer guard stops a yield being ENTERED after a stop; it does
                    // nothing about one already under way, which held the whole run "still running"
                    // for half a minute after the last train had arrived.
                    if (yieldLoc != null && running)
                    {
                        yieldLoc.blockUntilMotion(YIELD_SECONDS);
                    }
                }                   
            }
            }
            finally
            {
                // In a finally, so a thread killed by anything at all still stops being counted -
                // otherwise one failure would leave the layout reporting itself as running for ever,
                // and nothing could be edited again without a restart.
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

        // Now sort by priority
        Collections.sort(ends, (Point p1, Point p2) ->
        {
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
                // The best route found so far, within one band of station priority.
                //
                // Banded, because priority must still win: a station the user marked important is not
                // beaten by a shorter route to an ordinary one.  Ranking happens BETWEEN equals, and
                // with every priority left at its default that is simply every station.
                List<Edge> best = null;
                int bestCost = Integer.MAX_VALUE;
                Integer band = null;

                for (Point end : ends)
                {
                    // A band is settled before the next one is looked at
                    if (best != null && band != null && !band.equals(end.getPriority()))
                    {
                        return best;
                    }

                    band = end.getPriority();

                    // Reversing stations are parking, not traffic: the automation guide has always said they
                    // are chosen only in semi-autonomous operation, where the user picks the route.
                    // The exclusion belongs here and not in isPathClear, because executeTimetable sets
                    // running - so an isAutoRunning() fence would also refuse the "return home" staging
                    // run, which is precisely what is meant to fill these tracks at the end of a
                    // session.  Filtering at selection, never refusing at execution, is the same tier
                    // split the excluded-locomotive rule uses.
                    // getBlockLocomotive, not isOccupied: a destination whose sibling copy holds a
                    // train is not free - it is the same piece of track, and sending a second train
                    // there is a collision.
                    if (!end.equals(start) && end.getBlockLocomotive() == null && end.isDestination() && end.isActive()
                            && !end.isReversing() && end.isAutoDestination()
                            && !end.getExcludedLocs().contains(loc))
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
                                    // Whatever works, at once.  This is the behaviour every version
                                    // before the preference existed had, and it is the cheap one: the
                                    // ranked options have to enumerate the alternatives to compare
                                    // them, and this one does not have to look at any of them.
                                    if (Layout.pathPreference == PathPreference.RANDOM) return path;

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
                            // Not silent.  Execution falls through to the "no free paths" message, which reports
                            // a normal, expected condition - so a failure here was indistinguishable from simply
                            // having nowhere to go, and the retry loop went on calling this forever with nothing
                            // in the log to explain it.
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
        // Throttles the retry while a run is going.  After a stop there is no retry, so waiting here
        // delays nothing except this thread noticing it should exit - and with the delay settings up
        // at tens of seconds, that is how long the interface stayed disabled after everything had
        // already stopped.  A train with nowhere to go is precisely the one sitting in this sleep
        // when the user presses Stop.
        if (running) loc.delay(minDelay, maxDelay);

        return null;
    }
    
    /**
     * Why this locomotive cannot leave at all, or null when it can.
     *
     * The four reasons that have nothing to do with any particular destination: they are about the
     * train and the square it is standing on, and while one of them holds no destination is worth
     * asking about.
     *
     * @param loc the locomotive
     * @return the reason, ready to show, or null when the train is free to be given a route
     */
    public String explainCannotStart(Locomotive loc)
    {
        if (loc == null) return null;

        if (loc.isAutonomyPaused()) return I18n.t("autolayout.why.paused");

        Point at = this.getLocomotiveLocation(loc);

        if (at == null) return I18n.t("autolayout.why.notOnGraph");

        if (!at.isDestination()) return I18n.f("autolayout.why.startNotStation", at.getName());

        if (!at.isActive()) return I18n.f("autolayout.why.startInactive", at.getName());

        return null;
    }

    /**
     * Every station this locomotive might be sent to, and why each one is or is not available.
     *
     * The answer to "I pressed start and nothing happened", which is the commonest thing a new user
     * asks and which nothing in the interface could tell them. All of this was already computed on
     * every attempt and then thrown away: pickPath rejects a destination with ONE conjunction of
     * seven terms and records nothing, and isPathClear names its reasons properly but is called with
     * logging off, so its message went into lastError and was overwritten by the next candidate.
     *
     * So this is the same set of questions asked in the same order, keeping the answer instead of
     * discarding it. It deliberately does NOT re-implement the rules - the second stage calls
     * isPathClear itself, so a route this says is clear is a route the running layout agrees is
     * clear. A "why not" that disagrees with the thing it explains is worse than no explanation.
     *
     * Non-stations are left out entirely rather than listed as "not a station", because on a real
     * layout they are most of the graph and a list of two hundred of them answers nothing.
     *
     * @param loc the locomotive
     * @return station name to reason, in priority order, with a null value where the train could go
     */
    synchronized public java.util.Map<String, String> explainDestinations(Locomotive loc)
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
            // Not a station: not a candidate, and not worth a line.
            //
            // And not where this train is ALREADY standing - which on a derived graph is not just the
            // Point it is on but every copy of that square.  Comparing Points alone listed the
            // train's own platform as "occupied by <itself>", which is true and useless.
            if (!end.isDestination() || end.equals(start)) continue;

            if (start.getBlock() != null && start.getBlock().equals(end.getBlock())) continue;

            String reason = null;

            if (end.getBlockLocomotive() != null)
            {
                // The BLOCK, not the point: a train on another arrival-side copy of this square is
                // standing on this platform, and naming the copy would be telling the user about the
                // model rather than about their railway
                reason = I18n.f("autolayout.why.occupied", end.getBlockLocomotive().getName());
            }
            else if (!end.isActive())
            {
                reason = I18n.t("autolayout.why.inactive");
            }
            else if (end.isReversing())
            {
                reason = I18n.t("autolayout.why.reversing");
            }
            else if (!end.isAutoDestination())
            {
                reason = I18n.t("autolayout.why.notAutoDestination");
            }
            else if (end.getExcludedLocs().contains(loc))
            {
                reason = I18n.f("autolayout.why.excluded", loc.getName());
            }
            else
            {
                // Past the filter, so the question becomes whether any route is clear.  Every
                // alternative is tried, exactly as pickPath does, because the first one being blocked
                // says nothing about the second.
                reason = firstClearOrWhyNot(loc, start, end);
            }

            out.put(end.getName(), reason);
        }

        return out;
    }

    /**
     * Null when some route from start to end is clear, and otherwise why the last one was not.
     *
     * The reason comes from isPathClear itself, through lastError, which it sets whether or not it is
     * logging.  Enumerating the alternatives matters: a layout with a train parked across one way
     * round is the ordinary case, and reporting that first blocked route as though it were the only
     * one would tell the user their railway is broken when it is merely busy.
     */
    private String firstClearOrWhyNot(Locomotive loc, Point start, Point end)
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

                if (this.reversesAlongTheWay(path))
                {
                    why = I18n.t("autolayout.why.throughReversing");
                    continue;
                }

                // Read INSIDE this method's synchronized block, immediately after the call that set
                // it - but lastError is static and every running locomotive's thread writes it from
                // pickPath, which is not synchronized on this Layout. So with trains moving, the
                // message read here can be one generated for a different train's candidate route.
                //
                // The DECISION is never wrong: that comes from isPathClear's return value. Only the
                // wording can be misattributed, and it is labelled as such rather than silently
                // trusted, because a reason naming an edge nowhere near this route would send the user
                // to look at the wrong piece of railway.
                if (this.isPathClear(path, loc, false)) return null;

                why = this.isAutoRunning()
                    ? I18n.t("autolayout.why.blockedWhileRunning") : Layout.getLastError();

            } while (path != null);
        }
        catch (Exception e)
        {
            return String.valueOf(e.getMessage());
        }

        // No route at all is a different answer from every route being blocked, and the difference is
        // the difference between "build some track" and "wait a minute"
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
                                // Not silent.  Execution falls through to the "no free paths" message, which reports
                                // a normal, expected condition - so a failure here was indistinguishable from simply
                                // having nowhere to go, and the retry loop went on calling this forever with nothing
                                // in the log to explain it.
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
     * Returns the index of the first unfinished path in the timetable, or -1 if every entry has
     * finished.
     *
     * -1 rather than 0, which used to mean both "entry 0 is unfinished" and "nothing is unfinished".
     * Entries are dispatched on their own threads, so they can finish out of order: a graceful stop
     * can leave entry 0 still retrying while entry 1 has completed. The caller below then read 0 as
     * "nothing unfinished" and wiped every completion timestamp, including entry 1's.
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
     * A copy taken under the monitor.  getTimetable hands back the field itself and has to keep doing
     * so - deleteTimetableEntry removes from the list it returns - so the safe read is a second
     * accessor rather than a change to that one.  Locomotive threads append here whenever capture is on
     * during a run, and the readers are on the EDT holding nothing.
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
                if (l.equals(ttp.getLoc()))
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
     * Honours the operator's action delays, but never spins: both settings are allowed to be zero, and
     * zero means Thread.sleep(0).  That was survivable while these loops waited only for the train
     * ahead to SET OFF - a short window - but the sequential branch waits for it to ARRIVE, which is
     * minutes per entry.  With no floor, a staging run pins a core for most of its duration, and
     * silently: the wait message never varies, so the log's consecutive-duplicate suppression hides
     * every repeat after the first.
     *
     * The staging retry loop already reached this conclusion for itself - STAGING_RETRY_PAUSE exists
     * with "the delay settings may be zero" written against it.  This is the same thought, applied to
     * the other two places that wait.
     *
     * @param loc
     */
    private void pacedWait(Locomotive loc)
    {
        if (this.getMinDelay() == 0 && this.getMaxDelay() == 0)
        {
            try
            {
                Thread.sleep(COMPLETION_POLL);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
        else
        {
            loc.delay(this.getMinDelay(), this.getMaxDelay());
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
        // The flag is set here rather than inside, so that nothing thrown from the run can leave it
        // set - which would silently disable timetable capture for the rest of the session.
        this.timetableExecuting = true;

        try
        {
            return executeTimetableInternal();
        }
        finally
        {
            this.timetableExecuting = false;
        }
    }

    private boolean executeTimetableInternal()
    {
        // Returning after setting running would leave it set with nothing to clear it: no entry means
        // no thread, and isRunning() stayed true for the session - blocking autonomy and locomotive
        // edits until a reload.  The wait at the end of this method would also never return.
        if (this.timetable.isEmpty())
        {
            this.control.logf("autolayout.infoTimetableExecutionFinished");
            return true;
        }

        synchronized (this.activeLocomotives)
        {
            this.running = true;
        }

        // See runLocomotives: the signals are about a railway that has since been arranged by hand
        refreshAllProtectingSignals();
        
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
        
        // Calculate start index in case of prior graceful stop request.  Clamped because the index
        // is -1 when nothing is unfinished - which happens for an empty timetable, and momentarily
        // after the reset above sets every entry back to unfinished.
        int startIndex = Math.max(0, getUnfinishedTimetablePathIndex());
            
        this.control.logf(
            "autolayout.infoExecutionStartedFromIndex",
            startIndex + 1
        );        
        
        for (int i = startIndex; i < this.timetable.size(); i++)
        {
            TimetablePath ttp = this.timetable.get(i);
            
            final int index = i;

            // Continuously execute unless user requests graceful stop - or unless this Layout has been
            // retired.  Without the fence a reload during a run left this loop waiting on a retired
            // graph forever: the sequential branch below waits for the entry ahead to leave
            // activeLocomotives, the fence-abort inside executePath returns before removing it, and
            // nothing reachable from the UI can call stopLocomotives() on a Layout that getAutoLayout()
            // no longer resolves to.  The executor never returned, so its caller’s finally never ran.
            //
            // The completion wait below already reads isRunning() && isCurrentLayout(); this is the
            // same question asked at the point that actually spins.
            while (this.running && this.isCurrentLayout())
            {
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
                else
                {
                    this.control.logf(
                        "autolayout.infoStartingTimetableRoute",
                        ttp.toString()
                    );
                    startTime = System.currentTimeMillis();

                    new Thread(() ->
                    {
                        try
                        {
                            int attempts = 0;

                            // When this entry first refused, so a parallel run can give up on time
                            long refusingSince = 0;

                            while (this.running && !this.executePath(ttp.getPath(), ttp.getLoc(), ttp.getLoc().getPreferredSpeed(), ttp))
                            {
                                attempts++;

                                if (refusingSince == 0) refusingSince = System.currentTimeMillis();

                                // A PARALLEL timetable gives up on time, where a sequential one gives up
                                // on attempts.
                                //
                                // Both bounds used to be gated on timetableSequential, which is true
                                // only for a return-home plan - so a captured timetable retried a
                                // refusal that could never change until somebody noticed and stopped it
                                // by hand.  That was filed as T3 and pinned by a characterisation test
                                // saying so.
                                //
                                // Time rather than attempts here because the pause between attempts is
                                // pacedWait, which honours the user's delay settings - anything from a
                                // quarter second to tens of them.  Attempts would then bound a
                                // different amount of standing still on every layout.
                                boolean stuck = this.timetableSequential
                                    ? attempts >= STAGING_MAX_ATTEMPTS
                                    : System.currentTimeMillis() - refusingSince >= TIMETABLE_STUCK_MS;

                                if (stuck)
                                {
                                    // Retrying has stopped being worth it: with one train moving at a
                                    // time nothing will free the path, and in a parallel run nothing
                                    // has freed it for long enough that nothing is going to.  Stop and
                                    // say so rather than spin.
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

                                if (this.timetableSequential)
                                {
                                    // Paced independently of the delay settings, which may be zero -
                                    // that would busy-wait here rather than pause
                                    try
                                    {
                                        Thread.sleep(STAGING_RETRY_PAUSE);
                                    }
                                    catch (InterruptedException ie)
                                    {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                                else
                                {
                                    this.pacedWait(ttp.getLoc());
                                }
                            }

                            this.control.logf("autolayout.infoTimetablePathFinished");
                        }
                        // Throwable, for the reason runLocomotive gives where it catches the same call:
                        // executePath's own handler is catch (RuntimeException), so an Error walks
                        // straight past it AND past this - and past the block below, which is the only
                        // place the last entry clears `running`.  isRunning() then stays true for ever,
                        // the completion wait never returns, and Start and Graceful Stop never come
                        // back.  Stopping the trains and saying so is the right answer to an Error too.
                        catch (Throwable e)
                        {
                            this.control.logf(
                                "autolayout.errorTimetableExecutionFailed",
                                e.toString()
                            );

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

                    }).start();

                    break;
                }  

                this.pacedWait(ttp.getLoc());
            }                
        }

        // The loop above only DISPATCHES the last entry - its own thread is still driving that
        // train, and is what finally clears the running state.  Returning here handed control back
        // while a locomotive was still moving: callers re-enable their buttons on return, so Start
        // came back before the last train had arrived, and Graceful Stop stayed lit after it had.
        //
        // Also covers a graceful stop and a staging entry that gave up: both clear running, and
        // this still waits for whatever is mid-path to finish and leave activeLocomotives.
        // isCurrentLayout is the second condition because a fence-abandoned path leaves its
        // activeLocomotives entry in place ON PURPOSE - the entry belongs to a Layout being discarded,
        // and removing it was settled against.  isRunning therefore stays true forever on a reloaded
        // graph, and a wait that polled only that never returned: the timetable was never restored and
        // the buttons never came back.  Both behaviours are right; they had simply never been asked
        // about each other, the wait being newer than the fence.
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

        return !abandoned.get();
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
                        // Remembered rather than re-armed, for the reason spelled out on the same
                        // pattern in Locomotive: re-arming here makes the next wait() throw at once and
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
     * Locks a path and runs the locomotive from the start to the end
     * @param path
     * @param loc
     * @param speed 
     * @param ttp - null if not running a timetable route
     * @return  
     */
    public boolean executePath(List<Edge> path, Locomotive loc, int speed, TimetablePath ttp)
    {
        // A dispatch is a train under way, however it was asked for.
        //
        // runLocomotive counts autonomy's driving threads so that a locomotive between one path and the
        // next is never invisible to isRunning().  A train dispatched BY HAND was invisible for a
        // different reason: the diagram's right-click menu is a bare thread straight into this method,
        // so nothing counted it, and activeLocomotives is not written until configureAndLockPath has
        // already reserved every point on the route.  For the whole locking phase isRunning() was
        // false, and everything built on it stood down - including the protection that should have
        // thrown the destination's signal the moment its platform was reserved.  The signal stayed
        // green for the entire approach and only went red once the train was standing at the platform.
        //
        // Adam's rule, 2026-08-23: "The same thing should happen in manual operation vs auto - the same
        // switches and signals set, and guards applied."  So the same guards apply for the length of a
        // hand dispatch: the editors refuse to open, the simulation cannot be toggled, and locomotives
        // cannot be edited or deleted, exactly as while autonomy runs.
        //
        // Nested under autonomy, which has already counted its own thread, so this can only reach zero
        // when the LAST thing finishes - and reaching zero announces the end of the run, which is how
        // the interface learns to re-enable what it disabled.
        this.locomotiveThreads.incrementAndGet();

        try
        {
        try
        {
            return executePathInternal(path, loc, speed, ttp);
        }
        catch (RuntimeException e)
        {
            // An unexpected failure part way through a path used to leave the locomotive registered in
            // activeLocomotives for the rest of the session.  Nothing else clears that map, so
            // isRunning() - which is "running || !activeLocomotives.isEmpty()" - stayed true forever,
            // and with it every guard built on it: autonomy could not be started, simulation could not
            // be toggled, and locomotives could not be edited or deleted.  Only reloading the graph, or
            // restarting, recovered.
            //
            // Three things this deliberately does NOT do:
            //
            //  - It does not use finally.  The version fence returns normally when a path is abandoned
            //    after the layout is replaced, and that path leaves the entry in place on purpose - it
            //    belongs to a Layout that is being discarded.  A finally would fire there too.
            //  - It does not swallow the exception.  executeTimetable catches around executePath and
            //    responds by stopping the run; returning false instead would feed its retry loop, which
            //    would spin on a permanent fault rather than halting.
            //  - It does not unlock the path.  The locomotive may be physically standing on those edges,
            //    and releasing them would let another train be routed into occupied track.  Leaving them
            //    locked is degraded but safe, and a graph reload resets them.
            synchronized (this.activeLocomotives)
            {
                this.activeLocomotives.remove(loc);
                this.locomotiveMilestones.remove(loc);
            }

            // And any unfinished claim on a slot, for the same reason the registration is cleared:
            // one left behind lowers the cap for good.
            this.takingPath.remove(loc);

            // And the sensor this locomotive was said to be heading for.  A route condition asking
            // "has it reached that sensor yet" waits on this entry, and an entry left behind by a
            // failure is one nothing will ever clear: the thread evaluating that route parks until
            // the locomotive happens to be dispatched again, which after a timetable failure is
            // never.  notifyAll inside releases anyone already waiting.
            updatePendingS88(loc, null);

            // Stopping matters more than the bookkeeping: the locomotive is somewhere on the path with
            // nothing left tracking it.  Guarded so that a second failure here cannot replace the
            // exception that actually explains what went wrong.
            try
            {
                loc.setSpeed(0);
            }
            catch (RuntimeException stopFailure)
            {
                this.control.log(stopFailure);
            }

            this.control.log(e);

            throw e;
        }
        }
        finally
        {
            // In a finally for the reason runLocomotive's is: a thread killed by anything at all still
            // stops being counted.  One left behind reports the layout as running for the rest of the
            // session, and nothing can be edited again without a restart.
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
    private boolean executePathInternal(List<Edge> path, Locomotive loc, int speed, TimetablePath ttp)
    {    
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

        // Retired graph: refuse before anything is commanded.  The speed writes further down are each
        // fenced, so no train moved - but configureAndLockPath and the departure function ran first,
        // throwing every switch and signal on a path belonging to a layout that has been replaced.
        if (!this.isCurrentLayout())
        {
            return false;
        }

        // A speed of 0 is not a dispatch.  Two semi-autonomous paths pass the locomotive's preferred
        // speed, which is 0 for one placed on a node without the speed dialog ever being opened - and
        // the path was then locked, the departure functions fired, setSpeed(0) issued, and the thread
        // parked forever in waitForOccupiedFeedback on a sensor a stationary train can never reach.
        // activeLocomotives never emptied, so isRunning() stayed true and autonomy, the simulation
        // toggle and locomotive editing were all blocked until the graph was reloaded.
        //
        // runLocomotive already refused this, by throwing - but its caller turns the throw into "the
        // configuration is invalid, reload it", so one 0-speed locomotive disabled the whole layout.
        // Refusing here is both earlier and narrower.
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

        if (this.activeLocomotives.containsKey(loc))
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
        
        boolean result;
        
        result = configureAndLockPath(path, loc);

        if (!result)
        {
            // configureAndLockPath has already logged the reason (path occupied, or a validation failure
            // that stopped the loco and released its locks) - do not run the locomotive.
            //
            // The claim is dropped here as well as on the paths that set this, because a claim that
            // outlives its path lowers the cap for the rest of the session - a leak that makes the
            // railway quieter and quieter with nothing to say why.
            this.takingPath.remove(loc);
            return false;
        }
        else
        {
            synchronized (this.activeLocomotives)
            {                
                // CopyOnWriteArrayList: this list is only ever appended to (below) and read by the UI
                // (getReachedMilestones/getLatestMilestoneS88).  COW makes those reads iterate a snapshot
                // with no lock and no ConcurrentModificationException, so the getters need no locking.
                this.locomotiveMilestones.put(loc, new CopyOnWriteArrayList<>());
                this.locomotiveMilestones.get(loc).add(start);
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
            this.addTimetableEntry(loc, path);
        }
        
                    
        if (loc.hasCallback(CB_ROUTE_START))
        {
            loc.getCallback(CB_ROUTE_START).accept(loc);
        }
        
        loc.setSpeed(speed);
        this.control.logf(
            "autolayout.infoLocomotiveStarted",
            loc.getName()
        );

        // When !this.atomicRoutes: track edges to unlock based on length of train
        List<Integer> toUnlock = new LinkedList<>();
        Integer lengthTraversed = 0;

        for (int i = 0; i < path.size(); i++)
        {
            Point current = path.get(i).getEnd();
            
            if (i != path.size() - 1)
            {
                // Adjust speed based on multiplier
                if (isCurrentLayout())
                {
                    int calculatedSpeed = (int) Math.ceil((double) speed * current.getSpeedMultiplier());
                    calculatedSpeed = Math.min(calculatedSpeed, 100);
                    
                    if (loc.getSpeed() != calculatedSpeed)
                    {
                        this.control.logf(
                            "autolayout.infoAdjustingSpeedForLocomotive",
                            calculatedSpeed,
                            loc.getName()
                        );
                        loc.setSpeed(calculatedSpeed);
                    }
                }
                
                // Intermediate points - wait for feedback to be triggered and to clear
                if (current.hasS88() && isCurrentLayout())
                {
                    long simEpoch = 0;

                    if (this.simulate)
                    {
                        loc.delay(this.getMinDelay(), this.getMaxDelay());
                        simEpoch = simAnnounce(current.getS88());
                    }
                    
                    this.updatePendingS88(loc, current.getS88());

                    // With an advisory, because THIS is the wait that means "a train was sent and
                    // has not got there".  Nothing acts on it and the wait itself is unchanged - it
                    // only stops the operator having to notice for themselves that a train has gone
                    // quiet.  Asked for HERE rather than inside the wait, because "this train was
                    // dispatched" is a fact the dispatch loop has and the wait does not: a route's
                    // trigger monitor uses the same wait and is supposed to sit on its sensor for as
                    // long as the layout runs.
                    loc.waitForOccupiedFeedback(current.getS88(),
                        Locomotive.FEEDBACK_DURATION_THRESHOLD, Locomotive.FEEDBACK_ADVISORY_MS);
                    
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
                
                // Reverse the locomotive if this is a reversing station
                if (current.isReversing() && isCurrentLayout())
                {
                        this.control.logf(
                            "autolayout.infoIntermediateReversingForLocomotive",
                            loc.getName()
                        );
                        loc.setSpeed(0)
                        .switchDirection()
                        .waitForSpeedBelow(1)
                        .delay(this.getMinDelay(), this.getMaxDelay()) // Pause for a more realistic appearance
                        .setSpeed(speed)
                        .waitForSpeedAtOrAbove(speed);
                }
                
                // We can also clear the edges dynamically 
                // This can be useful, but extra care needs to be taken if any paths cross over
                // Therefore, we use setLockedEdgeUnoccupied and unlock 1 edge prior to the current one
                // path.get(i).setUnoccupied();
                if (!this.atomicRoutes && isCurrentLayout())
                {
                    if (i > 0)
                    {       
                        lengthTraversed += path.get(i - 1).getLength();
                        toUnlock.add(i - 1);
                        
                        if (lengthTraversed >= loc.getTrainLength() || lengthTraversed == 0)
                        {
                            for (int index : toUnlock)
                            {
                                synchronized (this.activeLocomotives)
                                {
                                    path.get(index).setLockedEdgeUnoccupied();
                                    path.get(index).getStart().setLocomotive(null);
                                    // path.get(index).getEnd().setLocomotive(null); // not necessary as this unlocks the second edge early
                                }
                                
                                if (control.isDebug())
                                {
                                    this.control.logf(
                                        "autolayout.infoUnlockingTraversedEdge",
                                        path.get(index).getName()
                                    );
                                }
                            }
                            
                            toUnlock.clear();
                            lengthTraversed = 0;
                        }
                        else
                        {
                            if (control.isDebug())
                            {
                                this.control.logf(
                                    "autolayout.infoNotUnlockingTraversedEdgeDueToTrainLength",
                                    loc.getTrainLength(),
                                    lengthTraversed,
                                    path.get(i - 1).getName()
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
                    loc.setSpeed((int) Math.ceil((double) speed * Math.min(preArrivalSpeedReduction, current.getSpeedMultiplier())));
                    this.control.logf(
                        "autolayout.infoPreArrivalSpeedForLocomotive",
                        loc.getSpeed(),
                        loc.getName()
                    );

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
                        Locomotive.FEEDBACK_DURATION_THRESHOLD, Locomotive.FEEDBACK_ADVISORY_MS);
                       
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
                // Stop before abandoning the path.  The layout that owned this run has been retired,
                // and stopLocomotives() only clears the dispatch flag - it never commands anything, so
                // returning here would leave a locomotive running between stations with nothing left
                // that will ever stop it, and a new graph that knows nothing about it.  It is standing
                // at a known milestone point right now, which is exactly where a graceful stop would
                // have put it.
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
                if (milestones != null)
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

        // Reverse at terminus station
        if (path.get(path.size() - 1).getEnd().isTerminus() || path.get(path.size() - 1).getEnd().isReversing())
        {
            this.control.logf(
                "autolayout.infoLocomotiveReachedTerminusOrFinalReversingStation",
                loc.getName()
            );
            loc.delay(this.getMinDelay(), this.getMaxDelay()).switchDirection().delay(1000); // pause to avoid network issues
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
     * Ensures that the passed locomotive does not conflict with any other multi-units by removing it from the graph
     * @param l 
     */
    public void sanitizeMultiUnits(Locomotive l)
    {
        if (l != null)
        {
            for (Point p : this.getPoints())
            {
                if (
                    // Is the locomotive present in any active multi-unit?
                    p.getCurrentLocomotive() != null && !p.getCurrentLocomotive().isSimultaneousMultiUnitCompatible(l)
                    // Or are we linked to the current locomotive?
                    || p.getCurrentLocomotive() != null && !l.isSimultaneousMultiUnitCompatible(p.getCurrentLocomotive())
                )
                {
                    this.control.log("Auto layout warning: removed locomotive " + p.getCurrentLocomotive().getName() + " from " + p.getName() + " because it confliced with " + l.getName());
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
            
            // Resolved once and null-checked, as the locomotive == null branch below already does.
            // An unknown point name used to be dereferenced straight away and thrown, rather than
            // reported - the two branches of this method disagreed about that.
            Point target = this.getPoint(targetPoint);

            if (target == null)
            {
                this.control.logf(
                    "autolayout.errorPointDoesNotExist",
                    targetPoint
                );
                return result;
            }

            // Can only place loc on a station
            if (!target.isDestination())
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
            
            // Removing from elsewhere is setLocomotive's own job now, and it does not stop at the
            // first copy.  This loop broke after one - written when a station was one Point, and a
            // square is several since - so a locomotive recorded on two copies of one platform lost
            // one of them and kept the other.
            
            // Ensure no multi-unit conflicts
            this.sanitizeMultiUnits(l);

            // Placing by hand DISPLACES whoever was there, and deliberately so: it is a person telling
            // the model where a train actually is, and the previous occupant is by definition no longer
            // there.  Refusing instead was tried and broke every displacing placement the multi-unit
            // tests pin.
            //
            // The whole SQUARE is displaced, not just the copy being written to.  setLocomotive clears
            // the train being placed off everywhere else - "one locomotive, one place" - but that says
            // nothing about the other direction, and a square is several Points now: putting a second
            // train on the other copy of an occupied platform left both standing on one piece of track.
            // Autonomy cannot reach that state, because the block check refuses a route onto a platform
            // whose sibling holds a train; hand placement was the way in.
            //
            // Only here, and deliberately not inside setLocomotive.  Path reservation goes through
            // reserve(), which does not sweep, and a sweep on the shared setter is what collapsed
            // reservations and stranded trains the last time this rule was widened.
            this.clearBlockExcept(target);

            // Set new location
            target.setLocomotive(l);

            // A locomotive placed by hand claims this station if it has no home and the station is
            // free of claims.  Placing an already-homed locomotive somewhere else does not re-home it.
            this.claimHome(l, target);

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
     * Returns all edges in the graph
     * @return 
     */
    public Collection<Edge> getEdges()
    {
        return this.edges.values();   
    }
    
    /**
     * Returns all points in the graph
     * @return 
     */
    public Collection<Point> getPoints()
    {
        return this.points.values();
    }

    /**
     * Fires one repaint callback, and does not let it strand the railway.
     *
     * Every callback runs synchronously on the DRIVING thread and every registered one repaints
     * something, so an exception out of the UI - a graph view that has drifted from the layout, a null
     * node - kills that thread.  Killed at a milestone, the train stops mid-block with its track still
     * locked, and every other train that needs that track is refused for the rest of the session.
     *
     * The start-of-path loop already guarded against this; the milestone and completion loops did not,
     * which is the drift this one door removes - there is nowhere left to fire a callback un-guarded.
     */
    /**
     * Tells whoever is watching that the last locomotive thread has gone.
     *
     * The counter alone was not enough, and this is the half that was missing. Nothing reads
     * isRunning() on a timer: the interface re-reads it when a path ENDS, and a train that is idle
     * when the user presses Stop produces no path end - so the last thread could exit with the buttons
     * still greyed and the Return Home tooltip still saying trains are running, until some unrelated
     * action happened to repaint. Worse, the exit-path capture of placements and homes is gated on
     * isRunning() and skips silently, so closing the application in that window lost the session's
     * changes without a word.
     *
     * An EMPTY edge list rather than null, because the registered callback walks it - and a null
     * locomotive, which it already guards for.
     */
    private void announceRunFinished()
    {
        for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
        {
            fireCallback(callback, new LinkedList<Edge>(), null, false);
        }
    }

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
            // Throwable, not Exception.  The usual advice against catching Errors assumes the
            // alternative is an orderly shutdown; here the alternative is a train stopped mid-block
            // with its track locked for the rest of the session, and every other train refused that
            // track.  A NoClassDefFoundError out of a repaint is not a reason to strand a railway.
            this.control.log(e instanceof Exception ? (Exception) e : new Exception(e));
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
    /**
     * Sets a station's protecting signal to match whether its platform is claimed.
     *
     * Red when a train is standing there or a locked path has reserved it, green when it is free.
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

        // Only while trains are actually being run.
        //
        // Every occupancy change comes through here, and placing a train by hand is an occupancy
        // change - so arranging the railway before a run threw real signals: cutting a locomotive off
        // a platform with Control+X drove its protecting signals on the spot, which is hardware moving
        // in response to a setup gesture.  Nobody asked for the railway to be commanded while they were
        // still deciding what it should look like.
        //
        // isRunning rather than isAutoRunning: a train under a driving thread is being run whether or
        // not the whole layout is, and its arrival still has to protect its platform.
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
     * Throws one protecting signal to whatever the platforms behind it now say.
     *
     * Per SIGNAL rather than per point, because that is the thing being commanded and because a signal
     * may protect more than one platform - and, since this change, a platform may have more than one
     * signal.  Neither of those is a special case here: the question asked is only ever "is anything
     * this signal protects claimed", of every Point in the layout.
     *
     * @param accessory the signal to bring up to date
     */
    private void refreshOneSignal(String accessory)
    {
        try
        {
            // Red if ANY platform this signal protects is claimed.
            //
            // Asked of the SIGNAL rather than of the square, which fixes two things at once.  Every
            // copy of a square carries the same accessory, so this covers the whole platform without
            // having to ask for the block - and it also covers the case the pairing UI allows and
            // nothing else handled: two different stations paired to one signal.  A signal can only
            // show one aspect, so it must stay red while either of them is occupied.
            boolean claimed = false;

            for (Point other : this.points.values())
            {
                if (!other.getProtectingSignals().contains(accessory)) continue;

                if (other.getCurrentLocomotive() != null)
                {
                    claimed = true;
                    break;
                }
            }

            // Only when it CHANGES.  This fires from every occupancy change, including each point a
            // locked path reserves - and configureAndLockPath holds the layout monitor across that
            // whole loop, so a command per reservation would put a burst of accessory traffic under
            // the lock the event thread also needs.  A signal already showing the right aspect needs
            // nothing sent to it.
            //
            // Remembered against the SIGNAL, not against a Point.  It used to be a field on Point
            // while "claimed" was a fact about the whole square, so a refresh on one copy that saw the
            // square claimed through ANOTHER copy wrote true into its own memo while standing empty.
            // Nothing ever wrote false back - the clearing transition was recorded on the other copy -
            // so the next real arrival there matched its stale memo and sent no command at all, and
            // the signal stayed GREEN with a train standing at the platform.
            Accessory acc = this.control.getAccessoryByName(accessory);

            if (acc == null) return;

            Boolean showing = this.signalAspects.get(accessory);

            // The memo AND the signal, because the memo is only the aspect protection last commanded
            // and it is not the only thing that commands this accessory.  TilePorts gives a SIGNAL tile
            // a GREEN configuration command, so a path configured across one drives it green through
            // getConfigCommands - the same Accessory, and configureAndLockPath does it in the same loop
            // that reserves the point, immediately after.  Protection then agreed with its own memo,
            // sent nothing, and went on agreeing: the signal stood GREEN over an occupied platform
            // until the train left, and no later occupancy change corrected it.
            //
            // Adam's ruling, 2026-08-23: a signal a path crosses and a signal protecting the
            // destination are two different signals, so a railway wired the way he intends never asks
            // which of them wins.  Nothing stops the two being paired to one accessory, though, and
            // when they are the failure is silent - so what protection may not do is skip because of
            // something it REMEMBERS.  Whatever moved the signal, the next occupancy change is decided
            // by looking.
            //
            // isRed() rather than a second memo: Accessory.switched is set optimistically by every
            // caller, protection and path configuration alike, so it is the one record that cannot go
            // stale behind somebody's back.
            if (showing != null && showing == claimed && acc.isRed() == claimed) return;

            this.signalAspects.put(accessory, claimed);

            // Through Accessory.setState, the same door the edge configuration uses, so a signal
            // thrown here is thrown exactly as one thrown by a route.
            acc.setState(claimed
                ? Accessory.accessorySetting.RED : Accessory.accessorySetting.GREEN);
        }
        catch (Exception e)
        {
            this.control.log(e);
        }
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
     * Brings every protecting signal into line with where the trains actually are.
     *
     * Called when a run begins, and forgetting the memo is not enough on its own.  While nothing is
     * running the refresh is silent - trains are placed and taken off by hand then, and driving real
     * signals from a setup gesture is what that silence exists to prevent - so two things are true at
     * the moment a run starts: what the signals were last told describes a railway that has since been
     * rearranged, and no occupancy CHANGE is coming for a train that was already put on its platform.
     *
     * Only clearing the memo leaves that second train unprotected for the whole run: nothing calls the
     * refresh for a square whose occupancy has not changed, so its signal simply stays as it was.  So
     * the memo is dropped AND every signal is asked again from current occupancy.
     *
     * The cost is one command per signal at the start of a run.  Set against a platform standing green
     * with a train in it, that is nothing.
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
     * Takes a locomotive off every Point except the one that is claiming it.
     *
     * The other half of the rule Point.setLocomotive enforces.  Here rather than there because only the
     * layout knows what the other Points are, and every copy is cleared rather than the first: a square
     * is several Points now, so a train that was "somewhere else" may have been in several somewhere
     * elses at once - which is the state this exists to make unrepresentable.
     *
     * Not synchronized: setLocomotive holds the Point's own monitor, and taking the layout's here would
     * order the two locks against every other path that takes them the other way round.  The map it
     * walks is only structurally changed by graph construction.
     *
     * @param l the locomotive being placed
     * @param claiming the Point placing it, which keeps it
     */
    /**
     * Whether a train standing here could be sent anywhere at all.
     *
     * Not "does this point have an outgoing edge" - that is the question that produced the fault this
     * exists to fix.  A copy of a split square can have somewhere to go and still have nowhere to be
     * SENT: every square it reaches is a plain point, a reversing point or parking, and autonomy only
     * ever dispatches a train to a destination.  A train placed there never moves, and nothing says
     * why.
     *
     * A plain walk of the edges rather than a path search per candidate: the question is reachability
     * over the track, and running the full path finder against every destination to answer "any?" is
     * work nobody needs.  Occupancy and locking are deliberately NOT considered - this asks what the
     * railway allows, not what is free this second.
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

                // Somewhere a train can actually be sent, and not the square it started on
                if (!end.equals(from) && end.isDestination() && end.isActive()
                    && end.isAutoDestination() && !end.isReversing())
                {
                    return true;
                }

                queue.add(end);
            }
        }

        return false;
    }

    /**
     * Takes every OTHER locomotive off the square this Point belongs to.
     *
     * The companion of clearLocomotiveExcept, and the other invariant: that one keeps a train from
     * being in two places, this keeps two trains from being in one.  A square emitted as several Points
     * is one piece of rail, so a train on any copy of it is on the platform.
     *
     * Called only from hand placement.  Autonomy never needs it - a route onto a platform whose sibling
     * copy holds a train is refused by the block check long before anything is placed - and calling it
     * from the shared setter would sweep away the reservations a locked path has made.
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

            // Said out loud, as sanitizeMultiUnits says its own evictions.
            //
            // A train taken off here may now be nowhere on the graph at all - it keeps its place in
            // the run list and its home claim, but staging skips it and pickPath will never dispatch
            // it, so it is simply out of the session.  That is the right outcome when somebody has
            // just said a different train is standing there, and the wrong thing to do in silence:
            // the copy that gets written to is not always the copy the user was looking at.
            this.control.logf("autolayout.infoLocomotiveDisplacedFromSquare",
                displaced.getName(), claiming.getName());
        }
    }

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

    public void setAtomicRoutes(boolean atomicRoutes)
    {
        this.atomicRoutes = atomicRoutes;
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

        // Debug only.  A mismatch here is the single most likely reason a staging run does something
        // inexplicable, so the check is worth keeping - but it asks the runtime for every path every
        // locomotive could take, and the runtime logs each one it rejects.  Left on, an ordinary press
        // of the button spends that work and buries the operator's log under thousands of lines about
        // paths nobody asked for.
        if (this.control.isDebug())
        {
            this.control.logf("autolayout.infoStagingAudit", staging.auditAgainstRuntime());
        }

        HomeStaging.Plan plan = staging.plan();

        // Always logged.  "Nothing happened" is the one outcome that tells the operator nothing, and
        // it is reachable several different ways - no homes recorded, everything already home, no
        // route, no arrangement found - which look identical from outside.
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
        return HomeStaging.snapshot(this).triage();
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
     * Capture is forced off for the load: with it on, every move would be appended to the timetable a
     * second time as though the operator had recorded it.
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

        // Capture is left exactly as the operator set it.  Turning it off around the load covered the
        // load only, and the moves are appended during the RUN - addTimetableEntry now excludes them
        // for as long as timetableSequential is set, which is the whole duration.
        List<TimetablePath> staged = new LinkedList<>();

        for (HomeStaging.Move move : plan.getMoves())
        {
            TimetablePath entry = new TimetablePath(move.getLocomotive(), move.getPath(), 0);

            // No delay: every entry should start as soon as the one before it has, and be held back
            // only by the path actually being occupied
            entry.setSecondsToNext(0);

            staged.add(entry);
        }

        this.setTimetable(staged);

        // Must be after setTimetable, which clears it.
        //
        // The plan is built on a model in which nothing is moving - see HomeStaging - so the moves are
        // only safe run one at a time.  executeTimetable normally dispatches an entry as soon as the
        // one before it has STARTED, and executePath locks a whole path up front, so two staging moves
        // overlapping can contend for an edge the planner never considered: the second retries forever
        // on a route it cannot abandon, while a free alternative exists that only live path selection
        // would find.  Observed in exactly that form before this flag existed.
        this.timetableSequential = true;

        this.control.logf("autolayout.infoReturnToHomeLoaded", staged.size());

        return plan;
    }
        
    /**
     * Whether the loaded timetable must run one train at a time.
     *
     * True only for a staging plan: it is built on a model in which nothing is moving, so overlapping
     * its moves can contend for an edge the planner never considered.
     *
     * @return
     */
    /**
     * Says whether the timetable's entries must run one at a time.
     *
     * Public because setTimetable clears it and only the staging planner sets it, which left no way
     * to state the flag directly - including for the file that has to remember it.
     *
     * @param sequential true to wait for each entry to arrive before starting the next
     */
    public void setTimetableSequential(boolean sequential)
    {
        this.timetableSequential = sequential;
    }

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
        // Copied, not adopted: deleteRoute mutates this list, and one caller passes
        // Collections.singletonList - immutable, so the delete died with
        // UnsupportedOperationException partway, leaving the route in the database.
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
            // Optionally skip turning on the functions
            Layout layout = lc.getModel().getAutoLayout();
            
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
            // Optionally disable the arrival functions
            Layout layout = lc.getModel().getAutoLayout();
            
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
        
        for (Point p : pointList)
        {
            pointJson.add(p.toJSON());
        }
        
        for (Edge e : edgeList)
        {
            edgeJson.add(e.toJSON());
        }
        
        for (TimetablePath p : this.timetable)
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
        jsonObj.put("turnOnFunctionsOnDeparture", this.isTurnOnFunctionsOnDeparture());
        jsonObj.put("atomicRoutes", this.isAtomicRoutes());
        jsonObj.put("maxActiveTrains", this.maxActiveTrains);
        jsonObj.put("maxLocInactiveSeconds", this.maxLocInactiveSeconds);
        jsonObj.put("timetable", timeTableJson);

        // Written only when set, so an ordinary layout's file does not grow a key that means nothing
        // to it.  Without this a saved return-home plan reloaded as an ordinary timetable: entries
        // dispatched as soon as the previous one STARTED rather than arrived, which is the contention
        // the flag exists to prevent, and which was observed in exactly that form before it existed.
        if (this.timetableSequential)
        {
            jsonObj.put("timetableSequential", true);
        }
        jsonObj.put("activateRoutes", this.isActivateRoutes());
        jsonObj.put("activateRouteIDs", new JSONArray(this.activateRouteIDs));

        if (this.simulate)
        {
            jsonObj.put("simulate", true);
        }

        return jsonObj.toString(4);
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
        
        // What each point is held back by, by NAME, until every point exists.
        //
        // The array is in file order rather than dependency order, so a restriction routinely names a
        // point further down it.  Resolved after the loop, next to the home assignments, which wait
        // for the same reason.
        java.util.Map<String, List<String>> blockersByPoint = new java.util.LinkedHashMap<>();

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
                // optBoolean, not getBoolean: every other optional field on a point defaults, and a
                // missing "station" threw out of this try, dropping the point silently.  The first sign
                // of it was an edge complaining that one of its endpoints did not exist, which names
                // the wrong line entirely in a file the operator edits by hand.
                layout.createPoint(point.getString("name"), point.optBoolean("station", false), s88);

                // Which piece of track this Point is part of, when the generator said so.  Absent on
                // everything hand-written, and on anything generated before this: those Points each
                // stand alone, which is what they have always done.
                if (point.has("block"))
                {
                    layout.getPoint(point.getString("name")).setBlock(point.optString("block", null));
                }

                // The signals thrown to red while this platform is claimed.  Absent everywhere they
                // have not been paired, and on everything hand-written.
                //
                // One is written as a bare string and several as an array, so both are read - a file
                // from any earlier version has the string, and nothing about it needs converting.
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

                // The points whose occupancy makes this station unavailable to autonomy (FR-001).
                //
                // Names, read verbatim: they may name a Point this loop has not created yet, and
                // nothing resolves them at load - the rule asks by name at the moment it is applied, so
                // a name matching nothing simply blocks nothing.  That is the tolerant direction and
                // the deliberate one: refusing the configuration would take a whole layout out of
                // service because one station lost the point it was paired with.
                if (point.has("blockedBy"))
                {
                    // Kept as names for now and resolved after the loop.
                    //
                    // A restriction can name a point this loop has not created yet - the array is in
                    // file order, not dependency order - so nothing can be resolved until every point
                    // exists.  Same reason the home assignments used to wait for the rebuild.
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

                // Read verbatim and not resolved here.  A point's assignment can name a locomotive
                // placed at a point this loop has not reached yet, so nothing can be concluded from it
                // until every point exists - see the rebuild after the loop.
                if (point.has("home"))
                {
                    // The boundary.  A file holds a name; a Point holds the locomotive - so this is
                    // where the two meet, and the only place a home can be dangling.
                    //
                    // Reported and dropped rather than refused, which is what rebuildHomeStations used
                    // to do further down: a name that matches nothing cannot resolve later, and
                    // invalidating a whole layout over one assignment is a much worse answer than
                    // losing the assignment.
                    String named = point.optString("home", null);

                    if (named != null && !named.trim().isEmpty())
                    {
                        Locomotive home = control.getLocByName(named);

                        if (home == null)
                        {
                            control.logf("autolayout.warnHomeLocomotiveNotInDatabase", named,
                                point.getString("name"));
                        }

                        layout.getPoint(point.getString("name")).setHomeLoc(home);
                    }
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

                            if (control.getLocByName(locName) != null) excludedLocs.add(control.getLocByName(locName));
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
                
                if (point.has("maxTrainLength"))
                {
                    if (point.get("maxTrainLength") instanceof Integer && point.getInt("maxTrainLength") >= 0)
                    { 
                        layout.getPoint(point.getString("name")).setMaxTrainLength(point.getInt("maxTrainLength"));

                        if (point.getInt("maxTrainLength") > 0)
                        {
                            control.logf(
                                "autolayout.infoSetMaxTrainLength",
                                point.getInt("maxTrainLength"),
                                point.getString("name")
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
                            else
                            {
                                l.setTrainLength(0);   
                            }

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
                            else
                            {
                                l.setReversible(false);   
                            }

                            // Only throw a warning if this is not a station
                            if (!point.optBoolean("station", false))
                            {
                                control.logf(
                                    "autolayout.warnLocomotivePlacedOnNonStation",
                                    loc
                                );
                            }

                            // De-conflict with other multi-units
                            layout.sanitizeMultiUnits(l);

                            Point placeOn = layout.getPoint(point.getString("name"));

                            // And with anything already standing on the same square.
                            //
                            // The second door into two trains on one piece of track, and the one that
                            // matters more than hand placement: a file written while the layout was in
                            // that state - by a version before the rule existed, hand-edited, or brought
                            // from another machine - reinstated it on every load, so the fault outlived
                            // the fix meant to end it.  fromJSON checks for a duplicate LOCOMOTIVE and
                            // never for a duplicate square.
                            //
                            // Repaired rather than refused.  A configuration that will not load is a
                            // railway nobody can use, and clearBlockExcept names the train it displaces,
                            // so the repair is not a silent one.
                            layout.clearBlockExcept(placeOn);

                            // Place the locomotive
                            placeOn.setLocomotive(l);
                            
                            // Reset if none present
                            l.setDepartureFunc(null);

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

                            // Reset if none present
                            l.setArrivalFunc(null);

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
                            layout.invalidate(
                                I18n.f("autolayout.errorLocomotiveNotInDatabase", loc.toString())
                            );
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
                                // This call is here to ADD the accessory, not merely to check it.  If
                                // it cannot be added the edge can never be actuated, so the failure is
                                // fatal rather than advisory: the outer catch invalidates the layout.
                                // Continuing would build a path that configureEdge refuses later, with
                                // the cause several steps behind wherever the operator notices.
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
                            layout.invalidate(
                                I18n.f("autolayout.errorLockEdgeNotInGraph", lockEdge.toString())
                            );
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
                timetableList.add(TimetablePath.fromJSON(tt.toString(), control, layout));
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
        }

        layout.setLocomotivesToRun(locsToRun);

        // The FR-001 restrictions, now that every point exists.
        //
        // A name matching no point is dropped and said out loud rather than kept: it can never be
        // resolved later, so leaving it would store something that only looks like state - and it would
        // be written back out on every save.  Dropping one restriction is also much the better answer
        // than refusing the configuration, which would take a whole layout out of service because one
        // station lost the point it was paired with.
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

        // Applied only now, because an assignment may name a locomotive placed at any point, and until
        // every point exists there is no way to tell an assignment that will be honoured from one whose
        // locomotive simply has not been read yet.
        //
        // A file with no assignments - which is every file written before this - reaches here with
        // nothing to apply, and the rebuild reproduces exactly what claiming during the loop produced.
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