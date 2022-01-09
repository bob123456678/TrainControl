/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package layout;

import java.util.function.Function;
import model.ViewListener;

/**
 * Graph edge - includes start and end point and occupied state
 * @author Adam
 */
public class Edge
{
    private final String name;
    private boolean occupied;
    private final Point start;
    private final Point end;
    private final Function<ViewListener, Boolean> configureFunc;
    
    /**
     * @param start
     * @param end
     * @param configureFunc lambda which will configure the edge 
     *                      (i.e., set switches and signals correctly to 
     *                       ensure the train can reach its destination)
     */
    public Edge(Point start, Point end, Function<ViewListener, Boolean> configureFunc)
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
            this.configureFunc.apply(control);
        }
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
    
    public boolean isOccupied()
    {
        return occupied;
    }
    
    public void setOccupied()
    {
        occupied = true;
    }
    
    public void setUnoccuplied()
    {
        occupied = false;
    }
}
            
