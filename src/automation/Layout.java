package automation;

import base.Accessory;
import base.Accessory.accessorySetting;
import static base.Accessory.accessorySetting.GREEN;
import static base.Accessory.accessorySetting.RED;
import static base.Accessory.accessorySetting.STRAIGHT;
import static base.Accessory.accessorySetting.TURN;
import base.Locomotive;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import marklin.MarklinAccessory;
import marklin.MarklinControlStation;
import marklin.MarklinLocomotive;
import model.ViewListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represent layout as a directed graph to support fully automated train operation
 * @author Adam
 */
public class Layout
{
    // Callback names
    public static final String CB_ROUTE_END = "routeEnd";
    public static final String CB_ROUTE_START = "routeStart";
    public static final String CB_PRE_ARRIVAL = "preArrival";
    
    // If set to true, paths will automatically execute.  Only useful for debugging / testing during development.
    private boolean simulate = false;
    
    // ms to wait between configuration commands
    public static final int CONFIGURE_SLEEP = 150;
    
    // Maximum number of seconds another locomotive should yield for to the inactive locomotive
    public static final int YIELD_SLEEP = 30;

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
    
    // Execution history
    private final List<TimetablePath> timetable;
    
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

    // Track the layout version so we know whether an orphan instance of this class is stale
    private static int layoutVersion = 0;
    
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
        public final Map<MarklinAccessory, Accessory.accessorySetting> configHistory;
        public final List<String> invalidConfigs;

        public EdgeConfigurationState()
        {         
            this.configIsValid = true;
            this.configHistory = new HashMap<>();
            this.invalidConfigs = new LinkedList<>();
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
        this.locomotivesToRun = new HashSet<>();
        this.callbacks = new HashMap<>();
        this.activeLocomotives = new HashMap<>();
        this.locomotiveMilestones = new HashMap<>();
        this.timetable = new LinkedList<>();
        
        Layout.layoutVersion += 1;
    }
    
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
        if (!path.isEmpty() && loc != null && this.timetableCapture)
        {
            timetable.add(new TimetablePath(loc, path, timestamp)); 
            
            // Calculate the delay time
            if (timetable.size() > 1)
            {
                TimetablePath second = timetable.get(timetable.size() - 1);
                TimetablePath first = timetable.get(timetable.size() - 2);
                
                first.setSecondsToNext(second.getExecutionTime() - first.getExecutionTime());
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
        return this.locomotiveMilestones.get(loc);
    }
    
    /**
     * Marks the layout state as invalid
     * Used to show error message in UI
     */
    public void invalidate()
    {
        this.isValid = false;
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
            throw new Exception("Simulation mode can only be changed when no trains are running");
        }
        
        if ((!control.isDebug() || control.getNetworkCommState()) && simulate)
        {
            throw new Exception("Simulation can only be enabled in debug mode and when not connected to a Central Station.\n\nDebug mode is enabled by passing a second argument via the command line.");
        }

        this.simulate = simulate;
        
        if (simulate)
        {
            control.log("Auto layout WARNING: Auto layout development / simulation mode enabled.  Trains will not run.");
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
                control.log("Skipping autonomous operation of locomotive " + loc.getName() + " (inactive)");  
                return;
            }
            else
            {
                control.log("Starting autonomous operation of locomotive " + loc.getName());
            }
            
            try 
            {
                runLocomotive(loc, loc.getPreferredSpeed());
            } 
            catch (Exception ex)
            {
               control.log("Auto layout error: Failed to run locomotive " + loc.getName());
               this.invalidate();
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
            throw new Exception("preArrivalSpeedReduction must be > 0 and <= 1");
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
            throw new Exception("Feedback " + feedback + " does not exist");
        }
        
        if ("".equals(name) || name == null)
        {
            throw new Exception("Point must have a name");
        }
        
        if (this.points.containsKey(name))
        {
            throw new Exception("Point " + name + " already exists");
        }
        
        Point p = new Point(name, isDest, feedback);
        
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
            throw new Exception("Start or end point does not exist");
        }
        
        Edge newEdge = new Edge(this.points.get(startPoint), this.points.get(endPoint));
        
        if (this.edges.containsKey(newEdge.getName()))
        {
            throw new Exception("Edge already exists");
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
     * Returns whether the given point only has incoming edges from reversing points
     * @param p
     * @return 
     */
    public boolean hasOnlyReversingIncoming(Point p)
    {        
        boolean hasNeighbor = false;

        for (Edge e : this.getIncomingEdges(p))
        {
            hasNeighbor = true;

            if (!e.getStart().isReversing()) return false;
        }
        
        return hasNeighbor;
    }
    
    /**
     * Returns whether the given point only has incoming edges from inactive points
     * @param p
     * @return 
     */
    public boolean hasOnlyInactiveIncoming(Point p)
    {        
        boolean hasNeighbor = false;

        for (Edge e : this.getIncomingEdges(p))
        {
            hasNeighbor = true;

            if (e.getStart().isActive()) return false;
        }
        
        return hasNeighbor;
    }
    
    /**
     * Returns whether the given point's neighbors are all inactive points
     * @param p
     * @return 
     */
    public boolean hasOnlyInactiveNeighbors(Point p)
    {       
        boolean hasNeighbor = false;
        
        for (Edge e : this.getNeighbors(p))
        {
            hasNeighbor = true;
            if (e.getEnd().isActive()) return false;
        }
        
        return hasNeighbor;
    }
    
    /**
     * Returns whether the given point's neighbors are all reversing points
     * @param p
     * @return 
     */
    public boolean hasOnlyReversingNeighbors(Point p)
    {       
        boolean hasNeighbor = false;
        
        for (Edge e : this.getNeighbors(p))
        {
            hasNeighbor = true;
            if (!e.getEnd().isReversing()) return false;
        }
        
        return hasNeighbor;
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
     * Writes a log message that the specified path is impossible
     * @param loc
     * @param path 
     */
    private void logPathError(Locomotive loc, List<Edge> path, String message)
    {
        if (control.isDebug())
        {
            this.control.log("\t " + loc.getName() + " path invalid: " + message + " " + this.pathToString(path));
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
        for (Edge e : path)
        {
            if (e.isOccupied(loc))
            {
                logPathError(loc, path, "Edge is occupied: " + e.getName());
                
                return false;
            }
                        
            // The same edge going in the opposite direction
            if (this.getEdge(e.getOppositeName()) != null && this.getEdge(e.getOppositeName()).isOccupied(loc))
            {
                logPathError(loc, path, "Edge is occupied: " + e.getOppositeName());
                              
                return false;
            }
            
            // Terminus stations may only be at the end of a path
            if (e.getStart().isTerminus() && !e.getStart().equals(path.get(0).getStart()))
            {
                logPathError(loc, path, "Contains an intermediate terminus station");
                
                return false;
            }
            
            // Inactive points not allowed in auto running
            if (this.isAutoRunning() && (!e.getStart().isActive() || !e.getEnd().isActive()))
            {
                logPathError(loc, path, "Contains an inactive point, which cannot be chosen in autonomous operation");
                
                return false;
            }
            
            // Starting point is not a station - do not pick it in fully autonomous mode
            if (this.isAutoRunning() && !e.getStart().isDestination() && e.getStart().equals(path.get(0).getStart()))
            {
                logPathError(loc, path, "Starts with a non-station, which cannot be chosen in autonomous operation");
                
                return false;
            }
            
            if (control.getFeedbackState(e.getEnd().getS88()) != false)
            {
                logPathError(loc, path, "Expects feedback " + e.getEnd().getS88() + " to be clear");
                             
                return false;
            }
            
            // Ensure all lock edges are unoccupied
            for (Edge e2 : e.getLockEdges())
            {
                if (e2.isOccupied(loc))
                {
                    logPathError(loc, path, "Lock edge " + e2.getName() + " occupied");
                    
                    return false;
                }
            } 
        }
        
        // Check train length
        if (!path.get(path.size() - 1).getEnd().validateTrainLength(loc))
        {
            logPathError(loc, path, "trainLength is too long to stop at " + path.get(path.size() - 1).getEnd().getName());
            
            return false;
        }
        
        if (!path.get(path.size() - 1).getEnd().isActive() && this.isAutoRunning())
        {
            logPathError(loc, path, "Disallowed because inactive station " + path.get(path.size() - 1).getEnd().getName() + " cannot be chosen in autonomous operation.");
            
            return false;
        }
        
        // Only reversible locomotives can go to a terminus
        if (path.get(path.size() - 1).getEnd().isTerminus() && !loc.isReversible())
        {
            logPathError(loc, path, "Terminus disallowed because " + loc.getName() + " is not reversible");
            
            return false;
        }
                
        // Preview the configuration                  
        EdgeConfigurationState validity = new EdgeConfigurationState();
        for (Edge e : path)
        {
            this.configureEdge(e, validity);
        }

        // Invalid state means there were conflicting accessory commands, so this path would not work as intended
        if (!validity.configIsValid)
        {
            logPathError(loc, path, "Has conflicting commands (" + validity.invalidConfigs.toString() + ")");
            
            return false;
        }
              
        return true;
    }
        
    /**
     * Returns the length of the given path
     * @param path
     * @return 
     */
    public int getPathLegnth(List<Edge> path)
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
     */
    private void configureEdge(Edge e, EdgeConfigurationState preConfigure)
    {
        for (String name : e.getConfigCommands().keySet())
        { 
            Accessory.accessorySetting state = e.getConfigCommands().get(name);  
        
            // Sanity check
            MarklinAccessory acc = control.getAccessoryByName(name);

            if (acc == null)
            {
                control.log("Accessory does not exist: " + name + " " + state);

                if (preConfigure == null)
                {
                    this.invalidate();
                    control.log("Invalidating auto layout state");
                }
                
                return;
            }

            if (preConfigure != null)
            {   
                // An opposite configuration was already issued - invalidate!
                if (preConfigure.configHistory.containsKey(acc) && !preConfigure.configHistory.get(acc).equals(state))
                {
                    //this.control.log("Conflicting command " + acc.getName() + " " + state);
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
                control.log("Auto layout: Configuring " + acc.getName() + " " + state.toString().toLowerCase());

                if (state == TURN || state == RED)
                {   
                    acc.turn();
                }
                else if (state == STRAIGHT || state == GREEN)
                {
                    acc.straight();
                }
                else
                {
                    // This should never happen
                    control.log("Invalid configuration command: " + name + " " + state.toString());
                }

                // Sleep between commands
                try 
                {
                    Thread.sleep(CONFIGURE_SLEEP);
                } 
                catch (InterruptedException ex) { }        
            }
        }
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
            throw new Exception("Point " + name + " does not exist");
        }
        
        if (!this.getNeighbors(p).isEmpty())
        {
            throw new Exception("Point " + name + " is connected to other points.  Delete edges first.");
        }
        
        for (Edge e : this.getEdges())
        {
            if (e.getStart().equals(p) || e.getEnd().equals(p))
            {
                throw new Exception("Point " + name + " has incoming edges.  Delete edges first.");
            }
        }
        
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
            throw new Exception("Edge " + start + " -> " + end + " does not exist");
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
            throw new Exception("Point " + name + " does not exist");
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
                callback.apply(new LinkedList<>(this.getEdges()), null, false);
            }
        }
    }
        
    /**
     * Marks all the edges in a path as occupied, effectively locking it
     * @param path a list of edges to traverse
     * @param loc
     * @return 
     */
    synchronized public boolean configureAndLockPath(List<Edge> path, Locomotive loc)
    {
        // Return if this path isn't clear
        if (!this.isPathClear(path, loc))
        {
            return false;
        }
                    
        for (Edge e : path)
        {
            e.setOccupied();
            e.getEnd().setLocomotive(loc);
            this.configureEdge(e, null);
            loc.delay(CONFIGURE_SLEEP);
        }
        
        return true;
    }
    
    /**
     * Marks all the edges in a path as unoccupied, 
     * unlocking it so that other trains may pass
     * @param path
     * @param loc
     * @return list of edges that were not unlocked during dynamic routes
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
                    
                    if (this.control.isDebug())
                    {
                        this.control.log("Auto layout: skipping unlock for " + e.getName() + " due to new active locomotive set via non-atomic paths");
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
        
        // this.control.log("Trying path " + start.getName() + " -> " + end.getName());
        
        if (start == null || end == null)
        {
            throw new Exception("Invalid points specified");
        }
        
        if (!end.isDestination())
        {
            return null;
        }
        
        List<Point> visited = new LinkedList<>();
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
        
        //this.control.log("Path: []");
        
        return null;   
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
            if (minLoc == null || l.getLastPathTime() < minLoc.getLastPathTime())
            {
                minLoc = l;
            }
        }
        
        if (minLoc != null && !currentLoc.equals(minLoc) && (currentLoc.getLastPathTime() - minLoc.getLastPathTime()) > threshold * 1000
                && !this.getPossiblePaths(minLoc, true).isEmpty())
        {
            int waited = (int) ((currentLoc.getLastPathTime() - minLoc.getLastPathTime()) / 1000);
            
            this.control.log(currentLoc.getName() + " yielding for up to " + YIELD_SLEEP + " seconds as " + minLoc.getName() + " has not run for " + waited + " seconds");
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
            throw new Exception("Invalid speed specified");
        }
        
        new Thread( () ->
        {    
            while(running)
            {
                List<Edge> path = this.pickPath(loc);

                if (path != null)
                {
                    this.executePath(path, loc, speed, null);
                }

                loc.delay(this.getMinDelay() * 1000);

                // If another locomotive is falling behind, attempt to yield to it
                if (this.isAutoRunning() && this.maxLocInactiveSeconds > 0)
                {
                    Locomotive yieldLoc = this.checkForSlowerLoc(this.maxLocInactiveSeconds, loc);

                    if (yieldLoc != null)
                    {
                        yieldLoc.waitForSpeedAtOrAbove(1, YIELD_SLEEP);
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
                    if (!end.equals(start) && !end.isOccupied() && end.isDestination() && end.isActive())
                    {
                        try 
                        {
                            // If the first shortest path is invalid, check all alternatives                            
                            List<Edge> path;
                            List<List<Edge>> seenPaths = new LinkedList<>();

                            do 
                            {
                                path = this.bfs(start, end, seenPaths);

                                if (path != null && this.isPathClear(path, loc))
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

                        }      
                    }
                }

                break;
            }
        }

        this.control.log(loc.getName() + " has no free paths at the moment");
          
        loc.delay(minDelay, maxDelay);
        
        return null;
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
                        if (!end.equals(start) && !end.isOccupied() && end.isDestination())
                        {
                            try 
                            {
                                List<Edge> path;
                                List<List<Edge>> seenPaths = new LinkedList<>();

                                // If the first shortest path is invalid, check all alternatives                            
                                do 
                                {
                                    path = this.bfs(start, end, seenPaths);

                                    if (path != null && this.isPathClear(path, loc))
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
     * Returns the index of the first unfinished path in the timetable
     * @return 
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
        
        return 0;
    }
    
    /**
     * Fetches the starting station for a given locomotive
     * @param l
     * @return 
     */
    public Point getTimetableStartingPoint(Locomotive l)
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
        return getUnfinishedTimetablePathIndex() != 0;
    }
  
    /**
     * Executes the paths in the timetable
     */
    public void executeTimetable()
    {
        synchronized (this.activeLocomotives)
        {
            this.running = true;
        }
        
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
        
        // Calculate start index in case of prior graceful stop request
        int startIndex = getUnfinishedTimetablePathIndex();
            
        this.control.log("Starting timetable execution from index " + (startIndex + 1));
        
        for (int i = startIndex; i < this.timetable.size(); i++)
        {
            TimetablePath ttp = this.timetable.get(i);
            
            final int index = i;

            // Continuously execute unless user requests graceful stop
            while (this.running)
            {
                if (i > startIndex && (System.currentTimeMillis() - startTime) < ttp.getSecondsToNext())
                {
                    this.control.log("Waiting " + (ttp.getSecondsToNext() - (System.currentTimeMillis() - startTime)) / 1000  + "s to time of next timetable entry...");
                }
                else if (i > startIndex && this.timetable.get(i - 1).getExecutionTime() == 0)
                {
                    this.control.log("Waiting for previous route to start.");
                }
                else
                {
                    this.control.log("Starting timetable route " + ttp.toString());
                    startTime = System.currentTimeMillis();

                    new Thread(() ->
                    {   
                        try
                        {
                            while (this.running && !this.executePath(ttp.getPath(), ttp.getLoc(), ttp.getLoc().getPreferredSpeed(), ttp))
                            {
                                this.control.log("Timetable entry " + ttp.toString() + " not yet executable. Check log. Retrying...");
                                
                                ttp.getLoc().delay(this.getMinDelay(), this.getMaxDelay());
                            }
                            
                            this.control.log("Timetable path finished.");
                        }
                        catch (Exception e)
                        {
                            this.control.log("Timetable error: " + e.toString());
                            
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
                            
                            this.control.log("Timetable execution finished.");
                        }
                        
                    }).start();
                    
                    break;
                }  

                ttp.getLoc().delay(this.getMinDelay(), this.getMaxDelay());
            }                
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
        // Sanity check
        if (!this.isValid())
        {
            this.control.log("Auto layout: Configuration is invalid and must be reloaded.");
            return false;
        }
        
        if (path.isEmpty())
        {
            this.control.log("Path is empty");
            return false;
        }
                
        if (loc == null)
        {
            this.control.log("Locomotive is null");
            return false;
        }
        
        if (this.activeLocomotives.containsKey(loc))
        {
            this.control.log("Locomotive is currently busy");
            return false;
        }
        
        Point start = path.get(0).getStart();
        // Point end = path.get(path.size() - 1).getEnd();
          
        if (!loc.equals(start.getCurrentLocomotive()))
        {
            this.control.log("Locomotive does not currently occupy the start of the path");
            return false;
        }
        
        boolean result;
        
        result = configureAndLockPath(path, loc);
                
        if (!result)
        {
            this.control.log("Error: path is occupied");
            return false;
        }
        else
        {
            synchronized (this.activeLocomotives)
            {                
                this.locomotiveMilestones.put(loc, new LinkedList<>());
                this.locomotiveMilestones.get(loc).add(start);
                this.activeLocomotives.put(loc, path);
            
                // Fire callbacks
                for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
                {
                    if (callback != null)
                    {
                        callback.apply(path, loc, true);
                    }
                }
                            
                if (ttp != null)
                {
                    ttp.setExecutionTime(System.currentTimeMillis());
                }
            }
             
            this.control.log("Executing path " + this.pathToString(path) + " for " + loc.getName());
            this.addTimetableEntry(loc, path);
        }
        
        // Check to see if the layout class has been re-created since this run
        int currentLayoutVersion = Layout.layoutVersion;
                    
        if (loc.hasCallback(CB_ROUTE_START))
        {
            loc.getCallback(CB_ROUTE_START).accept(loc);
        }
        
        loc.setSpeed(speed);
        this.control.log("Auto layout: started " + loc.getName());
        
        // When !this.atomicRoutes: track edges to unlock based on length of train
        List<Integer> toUnlock = new LinkedList<>();
        Integer lengthTraversed = 0;

        for (int i = 0; i < path.size(); i++)
        {
            Point current = path.get(i).getEnd();

            if (i != path.size() - 1)
            {
                // Intermediate points - wait for feedback to be triggered and to clear
                if (current.hasS88())
                {
                    if (this.simulate)
                    {
                        loc.delay(this.getMinDelay(), this.getMaxDelay());
                        this.control.setFeedbackState(current.getS88(), true);
                    }
                    
                    loc.waitForOccupiedFeedback(current.getS88());    
                    
                    if (this.simulate)
                    {            
                        // TODO - possible race condition here
                        new Thread(() -> 
                        {
                            loc.delay(this.getMinDelay(), this.getMaxDelay());
                            this.control.setFeedbackState(current.getS88(), false);
                        }).start();
                    }
                }    
                
                // Reverse the locomotive if this is a reversing station
                if (current.isReversing() && currentLayoutVersion == Layout.layoutVersion)
                {
                    this.control.log("Auto layout: intermediate reversing for " + loc.getName());
                    loc.setSpeed(0)
                        .switchDirection()
                        .waitForSpeedBelow(1, YIELD_SLEEP)
                        .delay(this.getMinDelay(), this.getMaxDelay()) // Pause for a more realistic appearance
                        .setSpeed(speed)
                        .waitForSpeedAtOrAbove(speed, YIELD_SLEEP);
                }
                
                // We can also clear this edges dynamically 
                // This can be useful, but extra care needs to be taken if any paths cross over
                // Therefore, we use setLockedEdgeUnoccupied and unlock 1 edge prior to the current one
                // path.get(i).setUnoccupied();
                if (!this.atomicRoutes && currentLayoutVersion == Layout.layoutVersion)
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
                                    control.log("Unlocking traversed edge: " + path.get(index).getName());
                                }
                            }
                            
                            toUnlock.clear();
                            lengthTraversed = 0;
                        }
                        else
                        {
                            if (control.isDebug())
                            {
                                control.log("Not yet unlocking traversed edge due to train length " + loc.getTrainLength() + " > " + lengthTraversed + ": " + path.get(i - 1).getName());
                            }
                        }
                    }
                }
            }
            else
            {           
                // Since we cannot interrupt the Locomotive thread, abort the route here if we need to
                if (currentLayoutVersion == Layout.layoutVersion)
                {        
                    // Destination is next - reduce speed and wait for occupied feedback
                    loc.setSpeed((int) Math.floor( (double) loc.getSpeed() * preArrivalSpeedReduction));
                    this.control.log("Auto layout: pre-arrival for " + loc.getName());
                                    
                    if (loc.hasCallback(CB_PRE_ARRIVAL))
                    {
                        loc.getCallback(CB_PRE_ARRIVAL).accept(loc);
                    }
                    
                    if (this.simulate)
                    {
                        loc.delay(this.getMinDelay(), this.getMaxDelay());
                        this.control.setFeedbackState(current.getS88(), true);
                    }
                    
                    loc.waitForOccupiedFeedback(current.getS88());    
                    
                    if (this.simulate)
                    {            
                        new Thread( () -> 
                        {
                            loc.delay(this.getMinDelay(), this.getMaxDelay());
                            this.control.setFeedbackState(current.getS88(), false);
                        }).start();
                    }

                    loc.setSpeed(0);
                    this.control.log("Auto layout: stopping " + loc.getName());
                }
            }  
            
            // Since we cannot interrupt the Locomotive thread, abort the route here if we need to
            if (currentLayoutVersion != Layout.layoutVersion)
            {
                if (control.isDebug())
                {
                    this.control.log("Locomotive " + loc.getName() + " path execution halted from prior layout version.");
                }
                
                return true;
            }
            
            this.control.log("Locomotive " + loc.getName() + " reached milestone " + current.toString());   
            
            synchronized (this.activeLocomotives)
            {
                this.locomotiveMilestones.get(loc).add(current); 
               
                // Fire callbacks
                for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
                {
                    if (callback != null)
                    {
                        callback.apply(path, loc, true);

                        // Repaint other routes in non-atomic route mode
                        if (!this.atomicRoutes)
                        {
                            for (Locomotive otherLoc : this.getActiveLocomotives().keySet())
                            {
                                // Our loc is still active, so skip repainting it
                                if (!otherLoc.equals(loc))
                                {
                                    callback.apply(this.activeLocomotives.get(otherLoc), otherLoc, true); 
                                }
                            }
                        }     
                    }
                }   
            }
        }
        
        // Reverse at terminus station
        if (path.get(path.size() - 1).getEnd().isTerminus() || path.get(path.size() - 1).getEnd().isReversing())
        {
            this.control.log("Auto layout: Locomotive " + loc.getName() + " reached terminus or final reversing station. Reversing");   
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
                    callback.apply(path, loc, false);

                    // Repaint other routes in non-atomic route mode
                    if (!this.atomicRoutes)
                    {
                        for (Locomotive otherLoc : this.getActiveLocomotives().keySet())
                        {
                            callback.apply(this.activeLocomotives.get(otherLoc), otherLoc, true); 
                        }
                    }                    
                }
            }
        }
        
        this.control.log("Locomotive " + loc.getName() + " finished its path: " + this.pathToString(path));   
        
        // Track number of completed paths
        loc.incrementNumPaths();
        
        return true;
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
            this.control.log("Cannot edit auto layout while running.");
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
            
            // Can only place loc on a station
            if (!this.getPoint(targetPoint).isDestination())
            {
                this.control.log(targetPoint + " is not a station.");
                return result;
            }
            
            // Can only place reversible trains on a terminus
            /* if (!this.getPoint(targetPoint).isTerminus() && !this.reversibleLocs.contains(locomotive))
            {
                this.control.log(locomotive + " is not reversible, but " + targetPoint + " is a terminus station.");
                return result;
            }*/
            
            // Remove from elsewhere
            for (Point p : this.getPoints())
            {
                if (l.equals(p.getCurrentLocomotive()))
                {
                    p.setLocomotive(null);
                    break;
                }
            }
            
            // Set new location
            this.getPoint(targetPoint).setLocomotive(l);
            
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
                    callback.apply(new LinkedList<>(this.getEdges()), locomotive == null ? null : this.control.getLocByName(locomotive), false);
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
            throw new Exception("minDelay must be positive and less than or equal to maxDelay");      
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
            throw new Exception("maxDelay must be positive and greater than or equal to to minDelay");      
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
            throw new Exception("defaultLocSpeed must be between 1 and 100");
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
            throw new Exception("maxLocInactiveSeconds may not be a negative value");
        }
        
        this.maxLocInactiveSeconds = sec;
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
            Layout layout = ((MarklinLocomotive) lc).getModel().getAutoLayout();
            
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
            Layout layout = ((MarklinLocomotive) lc).getModel().getAutoLayout();
            
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
        jsonObj.put("turnOffFunctionsOnArrival", this.isTurnOffFunctionsOnArrival());
        jsonObj.put("turnOnFunctionsOnDeparture", this.isTurnOnFunctionsOnDeparture());
        jsonObj.put("atomicRoutes", this.isAtomicRoutes());
        jsonObj.put("maxLocInactiveSeconds", this.maxLocInactiveSeconds);
        jsonObj.put("timetable", timeTableJson);
        
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
    public static Layout fromJSON(String config, MarklinControlStation control)
    {           
        Layout layout = new Layout(control);
        JSONObject o;
        
        try
        {
            o = new JSONObject(config);
        }
        catch (JSONException e)
        {
            control.log("Auto layout error: JSON parsing error");
            layout.invalidate();
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
            control.log("Auto layout error: missing or invalid keys (points, edges, minDelay, maxDelay, defaultLocSpeed)");
            layout.invalidate();
            return layout;
        }
        
        if (points == null || edges == null)
        {
            control.log("Auto layout error: missing keys (points, edges)");
            layout.invalidate();
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
            control.log("Auto layout error: " + e.getMessage());
            layout.invalidate();
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
                control.log("Auto layout error: invalid value for preArrivalSpeedReduction (must be 0-1)");
                layout.invalidate();
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
                     control.log("Auto layout: trains will yield to other trains inactive longer than " + o.getInt("maxLocInactiveSeconds") + " seconds");
                }
            }
            catch (Exception e)
            {
                control.log("Auto layout error: invalid value for maxLocInactiveSeconds (must not be negative)");
                layout.invalidate();
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
            control.log("Auto layout simulation warning: " + e.getMessage());
        }
        
        if (o.has("atomicRoutes"))
        {
            try
            {
                layout.setAtomicRoutes(o.getBoolean("atomicRoutes"));
                
                if (!o.getBoolean("atomicRoutes"))
                {
                    control.log("Auto layout notice: disabled atomic routes.  Edges will be unlocked as trains pass them instead of at the end of the route.");
                }

            }
            catch (JSONException e)
            {
                control.log("Auto layout error: invalid value for atomicRoutes (must be true or false)");
                layout.invalidate();
                return layout;
            }    
        }
           
        // Add points
        points.forEach(pnt -> { 
            JSONObject point = (JSONObject) pnt; 

            String s88 = null;
            if (point.has("s88"))
            {
                if (point.get("s88") instanceof Integer)
                {
                    s88 = Integer.toString(point.getInt("s88"));
                    
                    if (!control.isFeedbackSet(s88))
                    {
                        control.log("Auto layout warning: feedback " + s88 + " does not exist in CS2 layout");
                        control.newFeedback(point.getInt("s88"), null);
                    }
                }
                else if (!point.isNull("s88"))
                {
                    control.log("Auto layout error: s88 not a valid integer " + point.toString());
                    layout.invalidate();
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
                    control.log("Auto layout error: x not a valid integer " + point.toString());
                    layout.invalidate();
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
                    control.log("Auto layout error: y not a valid integer " + point.toString());
                    layout.invalidate();
                }
            }
            
            try 
            {
                layout.createPoint(point.getString("name"), point.getBoolean("station"), s88);
                     
                if (point.has("maxTrainLength"))
                {
                    if (point.get("maxTrainLength") instanceof Integer && point.getInt("maxTrainLength") >= 0)
                    { 
                        layout.getPoint(point.getString("name")).setMaxTrainLength(point.getInt("maxTrainLength"));

                        if (point.getInt("maxTrainLength") > 0)
                        {
                            control.log("Set max train length of " + point.getInt("maxTrainLength") + " for " + point.getString("name"));
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: " + point.getString("name") + " has invalid maxTrainLength value");
                        layout.invalidate();  
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
                            control.log("Auto layout error: " + point.toString() + " " + e.getMessage());
                            layout.invalidate();  
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: invalid value for terminus " + point.toString());
                        layout.invalidate();
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
                            control.log("Auto layout error: " + point.toString() + " " + e.getMessage());
                            layout.invalidate();  
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: invalid value for active " + point.toString());
                        layout.invalidate();
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
                            control.log("Auto layout error: " + point.toString() + " " + e.getMessage());
                            layout.invalidate();  
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: invalid value for reversing " + point.toString());
                        layout.invalidate();
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
                            control.log("Auto layout error: " + point.toString() + " " + e.getMessage());
                            layout.invalidate();  
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: invalid value for priority " + point.toString());
                        layout.invalidate();
                    }
                } 
            } 
            catch (Exception ex)
            {
                control.log(ex);
                
                control.log("Auto layout error: Point error for " + point.toString() + " (" + ex.getMessage() + ")");
                layout.invalidate();
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

                                    control.log("Set train length of " + locInfo.getInt("trainLength") + " for " + loc);
                                }
                                else
                                {
                                    control.log("Auto layout error: " + loc + " has invalid trainLength value");
                                    layout.invalidate();  
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
                                        control.log("Flagged as reversible: " + loc);
                                    }
                                }
                                else
                                {
                                    control.log("Auto layout error: " + loc + " has invalid reversible value");
                                    layout.invalidate();  
                                }
                            }
                            else
                            {
                                l.setReversible(false);   
                            }

                            // Only throw a warning if this is not a station
                            if (point.getBoolean("station") != true)
                            {
                                control.log("Auto layout warning: " + loc + " placed on a non-station at will not be run automatically");
                            }

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
                                    control.log("Auto layout error: Error in speed value for " + locInfo.getString("name"));
                                    layout.invalidate();
                                }
                            }

                            // Set start and end callbacks
                            if (locInfo.has("departureFunc") && locInfo.get("departureFunc") != null)
                            {
                                try
                                {
                                    l.setDepartureFunc(locInfo.getInt("departureFunc"));
                                }
                                catch (JSONException ex)
                                {
                                    control.log("Auto layout error: Error in departureFunc value for " + locInfo.getString("name"));
                                    layout.invalidate();
                                }
                            }

                            // Fires functions on departure and arrival
                            layout.applyDefaultLocCallbacks(l);

                            // Reset if none present
                            l.setArrivalFunc(null);

                            if (locInfo.has("arrivalFunc") && locInfo.get("arrivalFunc") != null)
                            {
                                try
                                {
                                    l.setArrivalFunc(locInfo.getInt("arrivalFunc"));
                                }
                                catch (JSONException ex)
                                {
                                    control.log("Auto layout error: Error in arrivalFunc value for " + locInfo.getString("name"));
                                    layout.invalidate();
                                }
                            }

                            if (l.getPreferredSpeed() == 0)
                            {
                                l.setPreferredSpeed(defaultLocSpeed);
                                control.log("Auto layout warning: Locomotive " + loc + " had no preferred speed.  Setting to default of " + defaultLocSpeed);
                            }

                            if (locomotives.contains(loc))
                            {
                                control.log("Auto layout error: duplicate locomotive " + loc + " at " + point.getString("name"));
                                layout.invalidate();
                            }
                            else
                            {
                                locomotives.add(loc);
                            }
                        }
                        else
                        {
                            control.log("Auto layout error: Locomotive " + loc + " does not exist");
                            layout.invalidate();
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: Locomotive configuration array at " + point.getString("name") + " missing name");
                        layout.invalidate();
                    }
                }
                else
                {
                    control.log("Auto layout warning: ignoring invalid value for loc \"" + point.get("loc").toString() + "\" (must be a JSON object)");
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
                    commands.forEach((cmd) -> {
                        JSONObject command = (JSONObject) cmd;
                        
                        // Validate accessory
                        if (command.has("acc") && !command.isNull("acc"))
                        {
                            String accessory = command.getString("acc");
                            if (null == control.getAccessoryByName(accessory))
                            {
                                // TODO - use Edge.validateConfigCommand
                                control.log("Auto layout warning: accessory \"" + accessory + "\" does not exist in CS2 layout");
                                
                                if (accessory.contains("Signal "))
                                {
                                    Integer address = Integer.valueOf(accessory.replace("Signal ", ""));
                                    
                                    if (control.getAccessoryByName("Switch " + address) != null)
                                    {
                                        control.log("Auto layout warning: " + accessory + " conflicts with switch with the same address.");
                                        //layout.invalidate();
                                    }
                                    
                                    control.newSignal(address.toString(), address, false);
                                    control.log("Auto layout warning: created " + accessory);
                                }
                                else if (accessory.contains("Switch "))
                                {   
                                    Integer address = Integer.valueOf(accessory.replace("Switch ", ""));

                                    if (control.getAccessoryByName("Signal " + address) != null)
                                    {
                                        control.log("Auto layout warning: " + accessory + " conflicts with signal with the same address.");
                                        //layout.invalidate();
                                    }
                                    
                                    control.newSwitch(address.toString(), address, false);
                                    control.log("Auto layout warning: created " + accessory);                       
                                }
                                else
                                {
                                    control.log("Auto layout error: unrecognized accessory type");
                                    layout.invalidate();
                                }
                            }
                        }
                        else
                        {
                            control.log("Auto layout error: Edge command missing accessory definition in " + start + "-" + end + " action: " + command.toString());
                            layout.invalidate(); 
                        }
                        
                        // Validate state
                        if (command.has("state") && !command.isNull("state"))
                        {            
                            String action = command.getString("state");

                            if (null == Accessory.stringToAccessorySetting(action))
                            {
                                control.log("Auto layout error: Invalid action in edge " + start + "->" + end + " (" + command.toString() + ")");
                                layout.invalidate();
                            }
                        }
                        else
                        {
                            control.log("Auto layout error: Edge command missing state " + start + "->" + end + " action: " + command.toString());
                            layout.invalidate();
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
                                control.log("Set edge length of " + edge.getInt("length") + " for " + e.getName());
                            }
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: " + e.getName() + " has invalid length value");
                        layout.invalidate();  
                    }
                }
                else
                {
                    e.setLength(0);
                }
            } 
            catch (Exception ex)
            {
                control.log("Auto layout error: Invalid edge " + edge.toString() + " (" + ex.getMessage() + ")");
                layout.invalidate();
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
                            control.log("Auto layout error: Lock edge" + lockEdge.toString() + " does not exist");  
                            layout.invalidate();
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
                control.log("Auto layout error: Lock edge error - " + edge.toString());
                layout.invalidate();
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
            control.log("Auto layout timetable warning: " + e.getMessage());
        }
        
        // Set list of reversible locomotives
        /*reversible.forEach(locc -> { 
            String loc = (String) locc;
                            
            if (control.getLocByName(loc) != null)
            {
                layout.addReversibleLoc(control.getLocByName(loc));
                control.log("Flagged as reversible: " + loc);
            }
            else
            {
                control.log("Auto layout error: Reversible locomotive " + loc + " does not exist");
                layout.invalidate();
            }
        });*/

        /*if (locomotives.isEmpty())
        {
            control.log("Auto layout error: No locomotives placed.");
            layout.invalidate();
        }*/
        
        List<Locomotive> locsToRun = new LinkedList<>();
        
        for (String s : locomotives)
        {
            locsToRun.add(control.getLocByName(s));
        }
        
        layout.setLocomotivesToRun(locsToRun);
                    
        return layout;
    }
}


