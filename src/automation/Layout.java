package automation;

import base.Accessory;
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
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import marklin.MarklinAccessory;
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
    
    // ms to wait if no paths exist 
    public static final int ROUTE_FIND_SLEEP = 5000;
    
    // ms to wait between configuration commands
    public static final int CONFIGURE_SLEEP = 200;
    
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
    
    // Used to check if accessories within a route conflict with each other
    private final Map<MarklinAccessory, Accessory.accessorySetting> configHistory;
    private boolean configIsValid;
    private List<String> invalidConfigs;
    private boolean preConfigure;
    
    // List of all / active locomotives
    private final Set<Locomotive> locomotivesToRun;
    private final Map<Locomotive, List<Edge>> activeLocomotives;
    private final Map<Locomotive, List<Point>> locomotiveMilestones;
    
    // Additional configuration
    private int minDelay;
    private int maxDelay;
    private int defaultLocSpeed;
    private boolean turnOffFunctionsOnArrival;
    private double preArrivalSpeedReduction = 0.5;

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
        this.configHistory = new HashMap<>();
        this.activeLocomotives = new HashMap<>();
        this.locomotiveMilestones = new HashMap<>();
        
        Layout.layoutVersion += 1;
    }
    
    /**
     * Resets the current config history
     */
    public void resetConfigHistory()
    {
        this.configHistory.clear();
        this.configIsValid = true;
        this.invalidConfigs = new LinkedList<>();
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
        this.running = true;
        
        // Start locomotives
        this.locomotivesToRun.forEach(loc ->
        {
            control.log("Starting autonomous operation of locomotive " + loc);
            
            try 
            {
                runLocomotive(loc, loc.getPreferredSpeed());
            } 
            catch (Exception ex)
            {
               control.log("Auto layout error: Failed to run locomotive " + loc);
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
        return this.edges.get(Edge.getEdgeName(this.getPoint(startPointName), this.getPoint(endPointName)));
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
     * @param configureFunc optional, a lambda that configures all accessories needed to connect the two points
     * @return 
     * @throws java.lang.Exception 
     */
    public Edge createEdge(String startPoint, String endPoint, BiConsumer<ViewListener, Edge> configureFunc) throws Exception
    {
        if (!this.points.containsKey(startPoint) || !this.points.containsKey(endPoint))
        {
            throw new Exception("Start or end point does not exist");
        }
        
        Edge newEdge = new Edge(this.points.get(startPoint), this.points.get(endPoint), configureFunc);
        
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
                if (control.isDebug())
                {
                    control.log("Edge is occupied: " + e.getName());
                }
                
                return false;
            }
                        
            // The same edge going in the opposite direction
            if (this.getEdge(e.getOppositeName()) != null && this.getEdge(e.getOppositeName()).isOccupied(loc))
            {
                if (control.isDebug())
                {
                    control.log("Edge is occupied: " + e.getOppositeName());
                }
                
                return false;
            }
            
            // Terminus stations may only be at the end of a path
            if (e.getStart().isTerminus() && !e.getStart().equals(path.get(0).getStart()))
            {
                if (control.isDebug())
                {
                    control.log("Path " + this.pathToString(path) + " contains an intermediate terminus station");
                }
                return false;
            }
            
            if (control.getFeedbackState(e.getEnd().getS88()) != false)
            {
                control.log("Path " + this.pathToString(path) + " expects feedback " + e.getEnd().getS88() + " to be clear");
                return false;
            }
            
            // Ensure all lock edges are unoccupied
            for (Edge e2 : e.getLockEdges())
            {
                if (e2.isOccupied(loc))
                {
                    control.log(loc.getName() + " can't proceed. Lock edge " + e2.getName() + " occupied for " + this.pathToString(path));
                    return false;
                }
            } 
        }
        
        // Check train length
        if (!path.get(path.size() - 1).getEnd().validateTrainLength(loc))
        {
            control.log("Locomotive " + loc.getName() +  " trainLength is too long to stop at " + path.get(path.size() - 1).getEnd().getName());
            
            return false;
        }
        
        // Only reversible locomotives can go to a terminus
        if (path.get(path.size() - 1).getEnd().isTerminus() && !loc.isReversible())
        {
            if (control.isDebug())
            {
                control.log("Path " + this.pathToString(path) + " disallowed because " + loc.getName() + " is not reversible");
            }
            
            return false;
        }
        
        // Preview the configuration
        this.resetConfigHistory();
        
        for (Edge e : path)
        {
            this.preConfigure = true;
            e.configure(control);
            this.preConfigure = false;
        }    
        
        // Invalid state means there were conflicting accessory commands, so this path would not work as intended
        if (!this.configIsValid)
        {
            this.control.log("Path " + this.pathToString(path) + " has conflicting commands - skipping (" + this.invalidConfigs.toString() + ")");
            return false;
        }
        
        return true;
    }
    
    /**
     * Function to configure an accessory.  This is called from the edge configuration lambda (instead of calling control directly) as defined in layout.createEdge 
     * so that the graph can keep track of conflicting configuration commands, and invalidate those paths accordingly
     * @param name - the name of the accessory (Switch 1, Signal 2, etc.) as used in control.getAccessoryByName
     * @param state - one of turn, straight, red, green
     */
    public void configure(String name, Accessory.accessorySetting state)
    {
        // Sanity check
        MarklinAccessory acc = control.getAccessoryByName(name);
        
        if (acc == null)
        {
            control.log("Accessory does not exist: " + name + " " + state);
            this.configIsValid = false;
            return;
        }
        
        // An opposite configuration was already issued - invalidate!
        if (this.configHistory.containsKey(acc) && !this.configHistory.get(acc).equals(state))
        {
            //this.control.log("Conflicting command " + acc.getName() + " " + state);
            this.invalidConfigs.add(acc.getName() + " " + state);
            this.configIsValid = false;
        }
        else
        {
            this.configHistory.put(acc, state);

            if (!this.preConfigure)
            {
                control.log("Auto layout: Configuring " + acc.getName() + ":" + state.toString().toLowerCase());
                
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
                    control.log("Invalid configuration command: " + name + " " + state);
                }
                
                // Sleep between commands
                try 
                {
                    Thread.sleep(CONFIGURE_SLEEP);
                } 
                catch (Exception e) { }     
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
        
        // Remove from db
        this.points.remove(name);
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
            e.configure(control);
            loc.delay(CONFIGURE_SLEEP);
        }
        
        return true;
    }
    
    /**
     * Marks all the edges in a path as unoccupied, 
     * unlocking it so that other trains may pass
     * @param path
     */
    synchronized public void unlockPath(List<Edge> path)
    {
        for (int i = 0; i < path.size(); i++)
        {
            Edge e = path.get(i);
            
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
        
        new Thread( () -> {
            
            while(running)
            {
                try 
                {
                    List<Edge> path = this.pickPath(loc);
                    
                    if (path != null)
                    {
                        this.executePath(path, loc, speed);
                    }
                    
                    Thread.sleep(1000);
                }
                catch (InterruptedException ex)
                {
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
        synchronized (this.activeLocomotives)
        {
            List<Point> ends = new LinkedList<>(this.points.values());
            Collections.shuffle(ends);

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
        }
        
        try
        {
            Thread.sleep(Layout.ROUTE_FIND_SLEEP);
        } 
        catch (InterruptedException ex)
        {

        }
        
        return null;
    }
    
    /**
     * Returns all possible paths for a given locomotive
     * @param loc 
     * @param uniqueDest - do we want to return multiple possible paths for the same start and end?
     * @return  
     */
    public List<List<Edge>> getPossiblePaths(Locomotive loc, boolean uniqueDest)
    {
        List<List<Edge>> output = new LinkedList<>();

        synchronized (this.activeLocomotives)
        {
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
                    }
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
        
        boolean result = configureAndLockPath(path, loc);
        
        if (!result)
        {
            this.control.log("Error: path is occupied");
            return false;
        }
        else
        {
            synchronized (this.activeLocomotives)
            {
                this.activeLocomotives.put(loc, path);
                this.locomotiveMilestones.put(loc, new LinkedList<>());
                this.locomotiveMilestones.get(loc).add(start);
            }   
            
            // Fire callbacks
            for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
            {
                if (callback != null)
                {
                    callback.apply(path, loc, true);
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

        for (int i = 0; i < path.size(); i++)
        {
            Point current = path.get(i).getEnd();

            if (i != path.size() - 1)
            {
                // Intermediate points - wait for feedback to be triggered and to clear
                if (current.hasS88())
                {
                    loc.waitForOccupiedFeedback(current.getS88());
                }
                
                // We can also clear this edges dynamically 
                // This can be useful, but extra care needs to be taken if any paths cross over
                // path.get(i).setUnoccupied();
                // path.get(i).getStart().setLocomotive(null);
                // path.get(i).getEnd().setLocomotive(null);
            }
            else
            {           
                // Since we cannot interrupt the Locomotive thread, abort the route here if we need to
                if (currentLayoutVersion == Layout.layoutVersion)
                {        
                    // Destination is next - reduce speed and wait for occupied feedback
                    loc.setSpeed((int) Math.floor( (double) loc.getSpeed() * preArrivalSpeedReduction));

                    if (loc.hasCallback(CB_PRE_ARRIVAL))
                    {
                        loc.getCallback(CB_PRE_ARRIVAL).accept(loc);
                    }

                    loc.waitForOccupiedFeedback(current.getS88()).setSpeed(0);
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
            
            this.locomotiveMilestones.get(loc).add(current);                  
            
            // Fire callbacks
            for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
            {
                if (callback != null)
                {
                    callback.apply(path, loc, true);
                }
            }
        }
        
        if (loc.hasCallback(CB_ROUTE_END))
        {
            loc.getCallback(CB_ROUTE_END).accept(loc);
        }
        
        // Reverse at terminus station
        if (path.get(path.size() - 1).getEnd().isTerminus())
        {
            loc.switchDirection();
            this.control.log("Locomotive " + loc.getName() + " reached terminus. Reversing");   
        }

        this.unlockPath(path);
        
        synchronized (this.activeLocomotives)
        {
            this.activeLocomotives.remove(loc);
            this.locomotiveMilestones.remove(loc);
        }
        
        // Fire callbacks
        for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
        {
            if (callback != null)
            {
                callback.apply(path, loc, false);
            }
        }

        this.control.log("Locomotive " + loc.getName() + " finished its path: " + this.pathToString(path));   
        
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
        
        if (this.running || !this.getActiveLocomotives().isEmpty())
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
    public interface TriFunction<T, U, V, R> {
        public R apply(T t, U u, V v);
    }
    
    public int getMinDelay()
    {
        return minDelay;
    }

    public void setMinDelay(int minDelay)
    {
        this.minDelay = minDelay;
    }

    public int getMaxDelay()
    {
        return maxDelay;
    }

    public void setMaxDelay(int maxDelay)
    {
        this.maxDelay = maxDelay;
    }

    public int getDefaultLocSpeed()
    {
        return defaultLocSpeed;
    }

    public void setDefaultLocSpeed(int defaultLocSpeed)
    {
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
                pieces.add (path.get(i).getStart().getName());
            }
            else
            {
                pieces.add (path.get(i).getEnd().getName());
            }
        }
        
        return "[" + String.join(" -> ", pieces) + "]";
    }
    
    /**
     * Returns the layout configuration as a JSON string
     * @return 
     * @throws java.lang.IllegalAccessException 
     * @throws java.lang.NoSuchFieldException 
     */
    public String toJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
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

        return jsonObj.toString(4);
    }
}


