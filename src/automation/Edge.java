package automation;

import base.Locomotive;
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
    private final Consumer<ViewListener> configureFunc;
    
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
    }
    
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
    
    public Point getStart()
    {
        return start;
    }
    
    public Point getEnd()
    {
        return end;
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
    
    synchronized public boolean isOccupied(Locomotive loc)
    {
        if (this.end.isOccupied() && !this.end.getCurrentLocomotive().equals(loc))
        {
            return true;
        }
        
        return occupied;
    }
    
    synchronized public void setOccupied()
    {
        occupied = true;
    }
    
    synchronized public void setUnoccupied()
    {
        occupied = false;
    }
}
            
