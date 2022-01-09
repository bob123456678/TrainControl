/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package layout;
import base.Feedback;
import base.Locomotive;

/**
 * TODO - Old code to represent layout as a graph, convert to Java
 * @author Adam
 */
public class Point
{
    private Locomotive currentLoc;
    private final boolean isDestination;
    private final String name;
    private final String s88;
    
    public Point(String name, boolean isDestination, String s88)
    {
        this.name = name;
        this.isDestination = isDestination;
        this.s88 = s88;
        this.currentLoc = null;
    }
    
    public boolean equals(Point other)
    {
        return this.name.equals(other.getName());
    }
      
    public String getName()
    {
        return this.name;
    }
    
    public boolean isOccupied()
    {
        return this.currentLoc != null;
    }
    
    public boolean isDestination()
    {
        return this.isDestination;
    }
    
    public Locomotive getCurrentLocomotive()
    {
        return this.currentLoc;
    }
    
    public void setLocomotive(Locomotive l)
    {
        this.currentLoc = l;
    }
    
    public String getS88()
    {
        return this.s88;
    }
    
    @Override
    public String toString()
    {
        return this.getName();
    }
}
