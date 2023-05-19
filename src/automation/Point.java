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
    private boolean isTerminus;
    
    public Point(String name, boolean isDestination, String s88) throws Exception
    {
        this.name = name;
        this.isDestination = isDestination;
        this.s88 = s88;
        this.currentLoc = null;
        this.isTerminus = false;
        
        if (isDestination && !hasS88())
        {
            throw new Exception("Destination point must have S88");
        }
    }
    
    /**
     * A terminus station will require the departing train to change direction
     * @throws Exception 
     */
    public void setTerminus() throws Exception
    {
        if (!isDestination)
        {
            throw new Exception("Only destination points can be a terminus");
        }
        else
        {
            this.isTerminus = true;
        }
    }
    
    public boolean isTerminus()
    {
        return isTerminus;
    }
    
    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof Point))
        {
            return false;
        }
        
        return this.name.equals(((Point) other).getName());
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
    
    public final boolean hasS88()
    {
        return this.s88 != null;
    }
    
    @Override
    public String toString()
    {
        return this.getName();
    }
}
