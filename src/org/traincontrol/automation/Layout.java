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

    // Set to false to disable locomotives
    private volatile boolean running = false;
    
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
    private final Map<Locomotive, List<Point>> locomotiveMilestones;
    private final Map<Locomotive, String> locomotivePendingS88;
    
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
    private static int layoutVersion = 0;

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

        // Points hold their own references, and nothing else was clearing them: a deleted locomotive
        // stayed excluded forever, and its name kept being written into the exported JSON as an
        // exclusion for a locomotive that no longer exists.
        for (Point p : this.getPoints())
        {
            p.removeExcludedLoc(l);

            // The home assignment naming it, which is held by NAME and so cannot be dropped by identity
            // the way the exclusion above is.  Left behind, it is written back out on every save and
            // reported on every load as a locomotive that is not in the database - and until then the
            // menus go on offering a station assigned to something that no longer exists.
            if (l.getName().equals(p.getHomeLoc())) p.setHomeLoc(null);
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
     * An assignment naming a locomotive that is not in the database is reported and removed - the name
     * is dangling, and nothing can resolve it later.  The layout itself is never invalidated over it.
     */
    synchronized public void rebuildHomeStations()
    {
        this.homeStations.clear();

        for (Point p : this.points.values())
        {
            if (p.getHomeLoc() == null) continue;

            Locomotive l = this.control.getLocByName(p.getHomeLoc());

            if (l == null)
            {
                // Dropped, not kept.  A name matching no locomotive cannot become an assignment again
                // by itself, so leaving it on the point stores something that only looks like state -
                // it would be written back out on every save and re-reported on every load.
                this.control.logf("autolayout.warnHomeLocomotiveNotInDatabase", p.getHomeLoc(), p.getName());
                p.setHomeLoc(null);
                continue;
            }

            if (this.homeStations.containsKey(l))
            {
                // Dropped, for the reason a dangling name is dropped: it can never be honoured.  One
                // locomotive has one station - setHomeLocomotive enforces exactly that when an
                // assignment is made - so only a hand-edited file reaches here, and keeping the loser
                // would re-warn on every load and be written back out on every save.
                this.control.logf("autolayout.warnHomeLocomotiveAssignedTwice", p.getHomeLoc(), p.getName());
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
     * Repoints assignments at a locomotive that has just been renamed.
     *
     * Everything else the layout holds - exclusions, the run list, occupancy - holds locomotives by
     * reference and hashes them by identity, so a rename cannot dislodge any of it, and renameLoc says
     * so.  A home assignment is the one exception: it is stored as a name, so that it can outlive the
     * locomotive being absent from the graph.  Without this the name dangles the instant it is renamed,
     * and the next rebuild reports it as missing from the database and drops it - losing an assignment
     * over an edit that had nothing to do with it.
     *
     * The map itself needs no repair and is deliberately not rebuilt: it is keyed by the locomotive
     * object, which is the same object it always was.  Rebuilding would re-derive the home of every
     * unassigned locomotive from wherever it happens to be standing.
     *
     * @param oldName
     * @param newName
     */
    synchronized public void locomotiveRenamed(String oldName, String newName)
    {
        if (oldName == null || newName == null || oldName.equals(newName)) return;

        for (Point p : this.points.values())
        {
            if (oldName.equals(p.getHomeLoc())) p.setHomeLoc(newName);
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

        // One station per locomotive: assigning it somewhere new gives up wherever it was assigned
        // before, or two stations would be waiting for the same train and neither could be satisfied.
        if (locName != null)
        {
            for (Point other : this.points.values())
            {
                if (other != p && locName.equals(other.getHomeLoc())) other.setHomeLoc(null);
            }
        }

        p.setHomeLoc(locName);

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
        return this.running || !this.getActiveLocomotives().isEmpty();
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
     * @param logFailures false when enumerating candidate paths, where a refusal is the ordinary
     *                    answer rather than something worth a log line.  lastError is still set.
     */
    public boolean isPathClear(List<Edge> path, Locomotive loc, boolean logFailures)
    {
        if (this.maxActiveTrains > 0 && this.isAutoRunning() && this.activeLocomotives.size() >= this.maxActiveTrains)
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

        // Only reversible locomotives can go to a terminus
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
     * Returns a list of possible new neighbors (edges) that could be added from the specified point
     * @param pointName
     * @return 
     */
    public List<Point> getPossibleEdges(String pointName)
    {
        List<Point> pointList = new LinkedList<>();
        
        if (this.points.containsKey(pointName))
        {
            pointList.addAll(this.getPoints());
            pointList.removeAll(this.getNeighbors(this.getPoint(pointName)));
        }
        
        return pointList;    
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

        synchronized (this)
        {
            // Return if this path isn't clear
            if (!this.isPathClear(path, loc))
            {
                this.control.logf("autolayout.errorPathOccupied");
                return false;
            }

            for (Edge e : path)
            {
                e.setOccupied();

                // RESERVED, not placed.  A locked path holds every one of its points for this
                // locomotive at once, which is the whole mechanism that keeps a junction behind the
                // train reserved against a second train reaching it another way.  setLocomotive would
                // sweep the train off the point it was just reserved on the moment the next point is
                // reserved, collapsing the reservation to the destination and freeing every junction -
                // and, on a path-integrity failure, stranding the train on no point at all.
                e.getEnd().reserve(loc);
                edgesLocked++;

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

        if (configureFailed)
        {
            // Only the edges we actually took.  Releasing the rest would call setUnoccupied on edges we
            // never locked, and that also clears their lock edges - which, precisely because we never
            // held them, may belong to another locomotive by now.
            this.handleMisconfiguredPath(path.subList(0, edgesLocked), loc);
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
                    // other as a lock edge, which is how the editor writes them - because the crossing
                    // edge is then itself part of any conflicting path, so isPathClear rejects that path
                    // on the edge's own occupancy flag while we hold it.  Note that isPathClear does NOT
                    // inspect lock edges, and occupancy is a flag rather than a count, so this reasoning
                    // does not extend to a hand-edited autonomy.json in which two edges name a third as
                    // a lock edge without either of them traversing it.
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
     * Whether a path drives through a reversing STATION on its way somewhere else.
     *
     * A reversing station is a parking berth, and executePathInternal stops and reverses the train at
     * every reversing point it reaches - so routing through one mid-journey turns a berth into a
     * shunting move nobody asked for, with a train halting and changing direction inside the parking
     * area en route to somewhere unrelated.  Barring them as destinations was not enough; a berth has
     * to be off the through-network as well.
     *
     * Reversing NON-stations are deliberately still allowed.  Those are the reversing loops and
     * headshunts that exist precisely to be driven through, and the mid-path flip is their purpose.
     *
     * The origin is exempt - a train standing on a berth is free to leave it - so only the END of each
     * edge is tested.  The last of those is the destination, which pickPath's own filter has already
     * refused for the same reason, so testing every end costs nothing and keeps the rule one shape.
     */
    private boolean passesThroughReversingStation(List<Edge> path)
    {
        for (Edge e : path)
        {
            if (e.getEnd().isReversing() && e.getEnd().isDestination())
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
                    && !this.passesThroughReversingStation(path))
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

                loc.delay(this.getMinDelay() * 1000);

                // If another locomotive is falling behind, attempt to yield to it
                if (this.isAutoRunning() && this.maxLocInactiveSeconds > 0)
                {
                    Locomotive yieldLoc = this.checkForSlowerLoc(this.maxLocInactiveSeconds, loc);

                    if (yieldLoc != null)
                    {
                        yieldLoc.blockUntilMotion(YIELD_SECONDS);
                    }
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
                for (Point end : ends)
                {                        
                    // Reversing stations are parking, not traffic: Automation.md has always said they
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

                                if (path != null && !this.passesThroughReversingStation(path)
                                        && this.isPathClear(path, loc, false))
                                {
                                    return path;
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

                break;
            }
        }

        this.control.logf(
            "autolayout.infoLocomotiveNoFreePaths",
            loc.getName()
        );          
        loc.delay(minDelay, maxDelay);
        
        return null;
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
     * @return false if a move was abandoned because its path would not clear - only possible in
     *         sequential (staging) mode.  A graceful stop is not an abandonment and returns true.
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

                            while (this.running && !this.executePath(ttp.getPath(), ttp.getLoc(), ttp.getLoc().getPreferredSpeed(), ttp))
                            {
                                attempts++;

                                if (this.timetableSequential && attempts >= STAGING_MAX_ATTEMPTS)
                                {
                                    // Retrying cannot help here - with one train moving at a time,
                                    // nothing will free the path.  Stop and say so rather than spin.
                                    this.control.logf(
                                        "autolayout.errorReturnToHomeEntryStuck",
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
                        catch (Exception e)
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

                            control.log(e);
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

        while (this.locomotivePendingS88.get(l) != null && this.locomotivePendingS88.get(l).equals(targetS88))
        {
            synchronized (this)
            {
                while (this.locomotivePendingS88.get(l) != null && this.locomotivePendingS88.get(l).equals(targetS88))
                {
                    try
                    {
                        wait();
                    }
                    catch (InterruptedException ex)
                    {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
    
    /**
     * Updates the pending S88 state so we can track what s88 each locomotive is waiting for
     * @param loc
     * @param s88 
     */
    private synchronized void updatePendingS88(Locomotive loc, String s88)
    {
        if (s88 == null)
        {
            this.locomotivePendingS88.remove(loc);
        }
        else
        {
            this.locomotivePendingS88.put(loc, s88);
        }

        notifyAll();
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
                    loc.waitForOccupiedFeedback(current.getS88());    
                    
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
                    
                    loc.waitForOccupiedFeedback(current.getS88()); 
                       
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
            // there.  setLocomotive's sweep then takes that train off every other square, so this can
            // never leave two trains on one piece of track - which is the only thing the block rule
            // has to prevent.  Refusing instead was tried and broke every displacing placement the
            // multi-unit tests pin.
                        
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

        String accessory = point.getProtectingSignal();

        if (accessory == null) return;

        try
        {
            // The whole platform, not this copy: another copy holding a train means the platform is
            // occupied, and the signal guards the platform.
            boolean claimed = point.getBlockLocomotive() != null;

            // Only when it CHANGES.  This fires from every occupancy change, including each point a
            // locked path reserves - and configureAndLockPath holds the layout monitor across that
            // whole loop, so a command per reservation would put a burst of accessory traffic under
            // the lock the event thread also needs.  A signal already showing the right aspect needs
            // nothing sent to it.
            if (point.wasSignalClaimed() != null && point.wasSignalClaimed() == claimed) return;

            point.rememberSignalClaimed(claimed);

            Accessory acc = this.control.getAccessoryByName(accessory);

            if (acc == null) return;

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

                // The signal thrown to red while this platform is claimed.  Absent everywhere it has
                // not been paired, and on everything hand-written.
                if (point.has("protectingSignal"))
                {
                    layout.getPoint(point.getString("name"))
                        .setProtectingSignal(point.optString("protectingSignal", null));
                }

                // Read verbatim and not resolved here.  A point's assignment can name a locomotive
                // placed at a point this loop has not reached yet, so nothing can be concluded from it
                // until every point exists - see the rebuild after the loop.
                if (point.has("home"))
                {
                    layout.getPoint(point.getString("name")).setHomeLoc(point.optString("home", null));
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
                            
                            // Place the locomotive
                            layout.getPoint(point.getString("name")).setLocomotive(l);
                            
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