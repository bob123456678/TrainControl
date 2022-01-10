/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package layout;

import base.Locomotive;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import model.ViewListener;

/**
 * Represent layout as a directed graph to support fully automated train operation
 * @author Adam
 */
public class Layout
{
    /**
     * Helper class for BFS
     */
    class PointPath
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
     * Retrieves a saved edge by its name (note: can also 
     * @param name
     * @return 
     */
    public Edge getEdge(String name)
    {
        return this.edges.get(name);
    }
    
    /**
     * Creates a new point
     * @param name
     * @param isDest
     * @param feedback
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
     * @param startPoint
     * @param endPoint
     * @param configureFunc 
     */
    public void createEdge(String startPoint, String endPoint, Function<ViewListener, Boolean> configureFunc) throws Exception
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
                if (!e.isOccupied() && !e.getEnd().equals(p) && !e.getEnd().isOccupied())
                {
                    neighbors.add(e);
                }
            }
        }
        
        return neighbors;
    }

    /**
     * Marks all the edges in a path as occupied
     * @param path a list of edges to traverse
     */
    synchronized public boolean configureAndLockPath(List<Edge> path)
    {
        // Return if this path isn't clear
        for (Edge e : path)
        {
            if (e.isOccupied())
            {
                return false;
            }
            
            if (control.getFeedbackState(e.getEnd().getS88()) != false)
            {
                control.log("Path: expecting feedback " + e.getEnd().getS88() + " to be clear");
                return false;
            }
        }
        
        for (Edge e : path)
        {
            e.configure(control);
            e.setOccupied();
        }
        
        return true;
    }
    
    /**
     * Marks all the edges in a path as unoccupied
     * @param path
     */
    synchronized public void unlockPath(List<Edge> path)
    {
        for (Edge e : path)
        {
            e.setUnoccuplied();
        }
    }
    
    public List<Edge> bfs(Point start, Point end) throws Exception
    {
        start = this.getPoint(start.getName());
        end = this.getPoint(end.getName());
        
        this.control.log("Trying path " + start.getName() + " -> " + end.getName());
        
        if (start == null || end == null)
        {
            throw new Exception("Invalid points specified");
        }
        
        if (!end.isDestination() || end.isOccupied())
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
                    this.control.log("Path: " + path.toString());
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
        
        this.control.log("Path: []");
        
        return null;   
    }
       
    /**
     * Picks a random (valid and unoccupied) path and executes it
     * @param speed 
     */
    public void pickAndExecutePath(int speed)
    {
        List<Point> starts = new LinkedList<>(this.points.values());
        Collections.shuffle(starts);
        List<Point> ends = new LinkedList<>(this.points.values());
        Collections.shuffle(ends);
        
        for (Point start : starts)
        {
            if (start.getCurrentLocomotive() != null)
            {
                for (Point end : ends)
                {
                    if (!end.equals(start) && !end.isOccupied() && end.isDestination())
                    {
                        try 
                        {
                            List<Edge> path = this.bfs(start, end);
                            
                            if (path != null)
                            {
                                this.executePath(path, start.getCurrentLocomotive(), speed);
                                return;
                            }
                        }
                        catch (Exception e)
                        {

                        }      
                    }
                }
            }
        }
        
        this.control.log("Layout: no free paths at the moment");
    }
  
    public synchronized void executePath(List<Edge> path, Locomotive loc, int speed)
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
            this.control.log("Locomotive does not currently occupy the start");
            return;
        }
        
        boolean result = configureAndLockPath(path);
        
        if (!result)
        {
            this.control.log("Error: path is occuplied");
            return;
        }
        else
        {
            this.control.log("Executing path " + path.toString() + " for " + loc.getName());
        }
        
        end.setLocomotive(loc);
        start.setLocomotive(null);

        // TODO - make the delay & loc functions configurable
        new Thread(() -> {
            loc.lightsOn().setSpeed(speed).waitForOccupiedFeedback(end.getS88()).setSpeed(0).lightsOff().delay((long) (Math.random() * 5000));

            this.unlockPath(path);

            this.control.log("Finished executing path " + path.toString() + " for locomotive " + loc.getName());  
        }).start();
    }
}
