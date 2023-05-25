package automation;

import base.Accessory;
import static base.Accessory.accessorySetting.GREEN;
import static base.Accessory.accessorySetting.RED;
import static base.Accessory.accessorySetting.STRAIGHT;
import static base.Accessory.accessorySetting.TURN;
import base.Locomotive;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import marklin.MarklinAccessory;
import model.ViewListener;

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
    private final List<String> locomotivesToRun;
    private final Map<String, List<Edge>> activeLocomotives;
    private final Map<String, List<Point>> locomotiveMilestones;
    
    // Reversible locomotives (can travel to a terminus station)
    private final Set<String> reversibleLocs;
    
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
        this.locomotivesToRun = new LinkedList<>();
        this.callbacks = new HashMap<>();
        this.configHistory = new HashMap<>();
        this.activeLocomotives = new HashMap<>();
        this.locomotiveMilestones = new HashMap<>();
        this.reversibleLocs = new HashSet<>();
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
    public void setLocomotivesToRun(List<String> locs)
    {
        this.locomotivesToRun.clear();
        this.locomotivesToRun.addAll(locs);
    }
    
    /**
     * Sets the list of locomotives that can travel to a terminus station
     * @param locs 
     */
    public void setReversibleLocs(List<String> locs)
    {
        this.reversibleLocs.clear();
        this.reversibleLocs.addAll(locs);
    }
    
    /**
     * Add a locomotive that can travel to a terminus station
     * @param loc
     */
    public void addReversibleLoc(Locomotive loc)
    {
        this.reversibleLocs.add(loc.getName());
    }
    
    /**
     * Gets the locomotives that will be run
     * @return  
     */
    public List<String> getLocomotivesToRun()
    {
        return this.locomotivesToRun;
    }
    
    /**
     * Gets locomotives currently running
     * @return  
     */
    public Map<String, List<Edge>> getActiveLocomotives()
    {
        return this.activeLocomotives;
    }
    
    /**
     * Gets milestones already reached by a locomotive
     * @param loc
     * @return  
     */
    public List<Point> getReachedMilestones(String loc)
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
     * Returns running status
     * @return 
     */
    public boolean isRunning()
    {
        return this.running;
    }
    
    /**
     * Stops locomotives
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
                runLocomotive(control.getLocByName(loc), control.getLocByName(loc).getPreferredSpeed());
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
     * @param configureFunc optional, a lambda that configures all accessories needed to connecte the two points
     * @throws java.lang.Exception 
     */
    public void createEdge(String startPoint, String endPoint, Consumer<ViewListener> configureFunc) throws Exception
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
                // control.log("Edge is occupied: " + e.getName());
                return false;
            }
            
            // Ensure all lock edges are unoccupied
            for (Edge e2 : e.getLockEdges())
            {
                if (e2.isOccupied(loc))
                {
                    control.log(loc.getName() + " can't proceed. Lock edge " + e2.getName() + " occupied for " + path.toString());
                    return false;
                }
            }
            
            // The same edge going in the opposite direction
            if (this.getEdge(e.getOppositeName()) != null && this.getEdge(e.getOppositeName()).isOccupied(loc))
            {
                // control.log("Edge is occupied: " + e.getOppositeName());
                return false;
            }
            
            if (control.getFeedbackState(e.getEnd().getS88()) != false)
            {
                control.log("Path " + path.toString() + " expects feedback " + e.getEnd().getS88() + " to be clear");
                return false;
            }
            
            // Terminus stations may only be at the end of a path
            if (e.getStart().isTerminus() && !e.getStart().equals(path.get(0).getStart()))
            {
                // control.log("Path " + path.toString() + " contains an intermediate terminus station");
                return false;
            }
        }
        
        // Only reversible locomotives can go to a terminus
        if (path.get(path.size() - 1).getEnd().isTerminus() && !this.reversibleLocs.contains(loc.getName()))
        {
            // control.log("Path " + path.toString() + " disallowed because " + loc.getName() + " is not reversible");
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
            this.control.log("Path " + path + " has conflicting commands - skipping (" + this.invalidConfigs.toString() + ")");
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
                        //this.control.log("Path: " + path.toString());
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
            }
        }
        
        this.control.log(loc.getName() + " has no free paths at the moment");
        
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
        
        // If the locomotive is currently running, it has no possible paths
        if (!this.activeLocomotives.containsKey(loc.toString()))
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
        Point end = path.get(path.size() - 1).getEnd();
          
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
                this.activeLocomotives.put(loc.getName(), path);
                this.locomotiveMilestones.put(loc.getName(), new LinkedList<>());
                this.locomotiveMilestones.get(loc.getName()).add(start);
            }   
            
            // Fire callbacks
            for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
            {
                if (callback != null)
                {
                    callback.apply(path, loc, true);
                }
            }
            
            this.control.log("Executing path " + path.toString() + " for " + loc.getName());
        }
            
        // TODO - make the delay & loc functions configurable
        
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
                    this.control.log("Locomotive " + loc.getName() + " reached milestone " + current.toString());
                    
                    this.locomotiveMilestones.get(loc.getName()).add(current);

                    // Fire callbacks for milestones
                    for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
                    {
                        if (callback != null)
                        {
                            callback.apply(path, loc, true);
                        }
                    }                    
                }
                
                // We can also clear this edges dynamically 
                // This can be useful, but extra care needs to be taken if any paths cross over
                // path.get(i).setUnoccupied();
                // path.get(i).getStart().setLocomotive(null);
                // path.get(i).getEnd().setLocomotive(null);
            }
            else
            {
                // Destination is next - reduce speed and wait for occupied feedback
                loc.setSpeed(loc.getSpeed() / 2);

                if (loc.hasCallback(CB_PRE_ARRIVAL))
                {
                    loc.getCallback(CB_PRE_ARRIVAL).accept(loc);
                }
                
                loc.waitForOccupiedFeedback(current.getS88()).setSpeed(0);
            }    
        }
        
        if (loc.hasCallback(CB_ROUTE_END))
        {
            loc.getCallback(CB_ROUTE_END).accept(loc);
        }
        
        // Reverse at terminus station
        // TODO - need a way to specify which locomotives are allows to travel to a terminus
        if (path.get(path.size() - 1).getEnd().isTerminus())
        {
            loc.switchDirection();
            this.control.log("Locomotive " + loc.getName() + " reached terminus. Reversing");   
        }

        this.unlockPath(path);
        
        synchronized (this.activeLocomotives)
        {
            this.activeLocomotives.remove(loc.getName());
            this.locomotiveMilestones.remove(loc.getName());
        }
        
        // Fire callbacks
        for (TriFunction<List<Edge>, Locomotive, Boolean, Void> callback : this.callbacks.values())
        {
            if (callback != null)
            {
                callback.apply(path, loc, false);
            }
        }

        this.control.log("Locomotive " + loc.getName() + " finished its path: " + path.toString());   
        
        return true;
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
}


