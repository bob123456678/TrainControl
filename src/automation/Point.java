package automation;
import base.Locomotive;

/**
 * Represent stations/stops as graph points
 * @author Adam
 */
public class Point
{
    private Locomotive currentLoc;
    private final boolean isDestination;
    private final String name;
    private final String s88;
    
    public Point(String name, boolean isDestination, String s88) throws Exception
    {
        this.name = name;
        this.isDestination = isDestination;
        this.s88 = s88;
        this.currentLoc = null;
        
        if (isDestination && !hasS88())
        {
            throw new Exception("Destination point must have S88");
        }
    }
    
    public boolean equals(Point other)
    {
        return this.name.equals(other.getName());
    }
      
    public String getName()
    {
        return this.name;
    }
    
    synchronized public boolean isOccupied()
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
    
    synchronized public void setLocomotive(Locomotive l)
    {
        this.currentLoc = l;
    }
    
    public String getS88()
    {
        return this.s88;
    }
    
    public boolean hasS88()
    {
        return this.s88 != null;
    }
    
    @Override
    public String toString()
    {
        return this.getName();
    }
}
