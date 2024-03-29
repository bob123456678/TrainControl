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
import marklin.MarklinLocomotive;
import model.ViewListener;
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
    
    // Additional configuration
    private int minDelay;
    private int maxDelay;
    private int defaultLocSpeed;
    private boolean turnOffFunctionsOnArrival;
    private boolean turnOnFunctionsOnDeparture;
    private double preArrivalSpeedReduction = 0.5;
    private int maxLocInactiveSeconds = 0; // Locomotives that have not run for at least this many seconds will be prioritized
    private boolean atomicRoutes = true; // if false, routes will be unlocked as milestones are passed

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
        
        Layout.layoutVersion += 1;
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
            control.log("Starting autonomous operation of locomotive " + loc.getName());
            
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
                    this.executePath(path, loc, speed);
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
     * Locks a path and runs the locomotive from the start to the end
     * @param path
     * @param loc
     * @param speed 
     * @return  
     */
    public boolean executePath(List<Edge> path, Locomotive loc, int speed)
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
            }
             
            this.control.log("Executing path " + this.pathToString(path) + " for " + loc.getName());
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
                        new Thread( () -> 
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
    
    public void setMaxLocInactiveSeconds(int sec) throws Exception
    {
        if (sec < 0)
        {
            throw new Exception("maxLocInactiveSeconds may not be a negatibve value");
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
        
        if (this.simulate)
        {
            jsonObj.put("simulate", true);
        }

        return jsonObj.toString(4);
    }
}


