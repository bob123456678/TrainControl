package automation;

import base.Locomotive;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import model.ViewListener;

/**
 * Graph edge - includes start and end points and occupied state
 * @author Adam
 */
public class Edge
{
    private final String name;
    private boolean occupied;
    private final Point start;
    private final Point end;
    
    // A lambda function with accessory commands needed to connect this edge
    private final Consumer<ViewListener> configureFunc;
    
    // A list of edges that should be locked whenever this edge is locked
    // This is useful if the layout contains crossings that cannot otherwise be modeled as a graph edge
    private final List<Edge> lockEdges;
    
    /**
     * @param start
     * @param end
     * @param configureFunc lambda which will configure the edge 
     *                      (i.e., set switches and signals correctly to 
     *                       ensure the train can reach its destination)
     */
    public Edge(Point start, Point end, Consumer<ViewListener> configureFunc)
    {
        this.start = start;
        this.end = end;
        this.name = getEdgeName(start, end);
        this.configureFunc = configureFunc;
        this.occupied = false;
        this.lockEdges = new LinkedList<>();
    }
    
    /**
     * Executes the configuration function
     * @param control 
     */
    public void configure(ViewListener control)
    {
        if (configureFunc != null)
        {
            this.configureFunc.accept(control);
        }
    }
    
    /**
     * Returns the name of the same edge going in the opposite direction
     * @return 
     */
    public String getOppositeName()
    {
        return getEdgeName(end, start);
    }
    
    /**
     * Returns the name of this edge
     * @return 
     */
    public String getName()
    {
        return name;
    }
    
    @Override
    public String toString()
    {
        return "Edge: " + getName();
    }
    
    public boolean equals(Edge other)
    {
        return this.getName().equals(other.getName());
    }
    
    /**
     * Gets the starting point of this edge
     * @return 
     */
    public Point getStart()
    {
        return start;
    }
    
    /**
     * Gets the ending point of this edge
     * @return 
     */
    public Point getEnd()
    {
        return end;
    }
    
    /**
     * Add an edge to the list of edges which must always be locked whenever this edge is locked
     * @param e
     */
    public void addLockEdge(Edge e)
    {
        this.lockEdges.add(e);
    }
    
    /**
     * Gets the name of an edge based on two points
     * @param start
     * @param end 
     * @return  
     */
    public static String getEdgeName(Point start, Point end)
    {
        return start.getName() + "_" + end.getName(); 
    }
    
    /**
     * Tests if the given edge is occupied by a different locomotive than the one specified
     * @param loc
     * @return 
     */
    synchronized public boolean isOccupied(Locomotive loc)
    {
        if (this.end.isOccupied() && !this.end.getCurrentLocomotive().equals(loc))
        {
            return true;
        }
        
        return occupied;
    }
    
    /**
     * Same as setOccupied, but should only be called on edges in the lockEdges list (to prevent infinite recursion) 
     */
    synchronized protected void setLockedEdgeOccupied()
    {
        occupied = true;    
    }
    
    /**
     * Same as setUnoccupied, but should only be called on edges in the lockEdges list (to prevent infinite recursion) 
     */
    protected void setLockedEdgeUnoccupied()
    {
        occupied = false;
    }
    
    /**
     * Mark this edge, as well as any linked edges, as occupied
     */
    synchronized public void setOccupied()
    {
        occupied = true;
        
        for (Edge e : this.lockEdges)
        {
            e.setLockedEdgeOccupied();
        }
    }
    
    /**
     * Mark this edge, as well as any linked edges, as unoccupied
     */
    synchronized public void setUnoccupied()
    {
        occupied = false;
        
        for (Edge e : this.lockEdges)
        {
            e.setLockedEdgeUnoccupied();
        }
    }
}
            
