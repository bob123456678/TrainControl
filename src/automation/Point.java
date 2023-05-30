package automation;
import base.Locomotive;
import java.util.LinkedList;
import java.util.List;

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
    private Integer x;
    private Integer y;
    
    public Point(String name, boolean isDestination, String s88) throws Exception
    {
        this.name = name.replace("\"", "");
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

    public int getX()
    {
        return x;
    }

    public void setX(int x)
    {
        this.x = x;
    }

    public int getY()
    {
        return y;
    }

    public void setY(int y)
    {
        this.y = y;
    } 
    
    public boolean coordinatesSet()
    {
        return this.x != null && this.y != null;
    }
    
    /**
     * Converts this point to a JSON representation
     * @return 
     */
    public String toJSON()
    {		
        String json = "\"name\" : \"%s\", \"station\" : %s, \"s88\" : %s";
        
        json = String.format(json, this.getName(), this.isDestination, this.s88);
        
        if (this.currentLoc != null && this.isDestination)
        {
            json += ", \"loc\" : \"" + this.currentLoc.getName().replace("\"", "\\\"") + "\"";
            
            json += ", \"locReversible\" : " + this.currentLoc.isReversible();
        }
        
        if (this.isTerminus)
        {
            json += ", \"terminus\" : " + this.isTerminus;
        }
        
        if (this.currentLoc != null && this.currentLoc.getArrivalFunc() != null)
        {
            json += ", \"locArrivalFunc\" : " + this.currentLoc.getArrivalFunc();
        }
        
        if (this.currentLoc != null && this.currentLoc.getDepartureFunc() != null)
        {
            json += ", \"locDepartureFunc\" : " + this.currentLoc.getDepartureFunc();
        }
        
        if (this.coordinatesSet())
        {
            json += ", \"x\" : " + this.getX() + " , \"y\" : " + this.getY();
        }
        
        return "{" + json + "}";
    }
}
