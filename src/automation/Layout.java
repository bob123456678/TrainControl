package automation;

import base.Locomotive;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
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
    
    // ms to wait between configuration commands
    public static final int CONFIGURE_SLEEP = 200;
        
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
    
    private final ViewListener control;
    private final Map<String, Edge> edges;
    private final Map<String, Point> points;
    private final Map<String, List<Edge>> adjacency;
    
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
        }
        
        return true;
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
     * @return
     * @throws Exception 
     */
    public List<Edge> bfs(Point start, Point end) throws Exception
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
                    //this.control.log("Path: " + path.toString());
                    return path;
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
            
            while(true)
            {
                try {
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
                            List<Edge> path = this.bfs(start, end);
                            
                            if (path != null && this.isPathClear(path, loc))
                            {
                                return path;
                            }
                        }
                        catch (Exception e)
                        {

                        }      
                    }
                }
            }
        }
        
        this.control.log(loc.getName() + " has no free paths at the moment");
        
        return null;
    }
  
    /**
     * Locks a path and runs the locomotive from the start to the end
     * @param path
     * @param loc
     * @param speed 
     */
    public void executePath(List<Edge> path, Locomotive loc, int speed)
    {        
        if (path.isEmpty())
        {
            this.control.log("Path is empty");
            return;
        }
        
        if (loc == null)
        {
            this.control.log("Locomotive is null");
            return;
        }
        
        Point start = path.get(0).getStart();
        Point end = path.get(path.size() - 1).getEnd();
          
        if (!loc.equals(start.getCurrentLocomotive()))
        {
            this.control.log("Locomotive does not currently occupy the start of the path");
            return;
        }
        
        boolean result = configureAndLockPath(path, loc);
        
        if (!result)
        {
            this.control.log("Error: path is occupied");
            return;
        }
        else
        {
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

        this.unlockPath(path);

        this.control.log("Locomotive " + loc.getName() + " finished its path: " + path.toString());              
    }
}
