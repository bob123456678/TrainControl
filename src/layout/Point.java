/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package layout;
import base.Feedback;
import base.Locomotive;

/**
 *
 * @author Adam
 */
public class Point
{
    private Locomotive currentLoc;
    private boolean isDestination;
    private String name;
    private Feedback s88;
    
    public Point(String name, boolean isDestination, Feedback s88, Locomotive currentLoc)
    {
        this.name = name;
        this.isDestination = isDestination;
        this.s88 = s88;
        this.currentLoc = currentLoc;
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
    
    public Feedback getS88()
    {
        return this.s88;
    }
}
