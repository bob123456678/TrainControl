package automation;
import base.Locomotive;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import org.json.JSONObject;

/**
 * Represent stations/stops as graph points
 * @author Adam
 */
public class Point
{
    private Locomotive currentLoc;
    private boolean isDestination;
    private String name;
    private String s88;
    private boolean isTerminus;
    private Integer x;
    private Integer y;
    private Integer maxTrainLength = 0;
    private Integer uniqueId;
    
    // Unique ID for any new node
    private static Integer id = 0;
  
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
        
        // Save the immutable unique ID
        this.uniqueId = ++id;
    }
    
    /**
     * Returns this node's unique ID
     * @return 
     */
    public String getUniqueId()
    {
        return Integer.toString(uniqueId);
    }
    
    /**
     * Sets an S88 value for this point
     * @param value 
     */
    public void setS88(Integer value)
    {
        if (value != null)
        {
            this.s88 = Integer.toString(Math.abs(value));
        }
        else
        {
            this.s88 = null;
        }
    }
    
    /**
     * Changes the state of this station
     * @param state
     * @throws Exception 
     */
    public void setDestination(boolean state) throws Exception
    {
        if (state && !hasS88())
        {
            throw new Exception("Stations must have an s88 sensor.  Set the s88 address first.");
        }
        
        this.isDestination = state;
        
        // Reset terminus status
        if (!this.isDestination) this.isTerminus = false;
    }
    
    /**
     * A terminus station will require the departing train to change direction
     * @param state
     * @throws Exception 
     */
    public void setTerminus(boolean state) throws Exception
    {
        if (!isDestination)
        {
            throw new Exception("Only destination points can be a terminus");
        }
        else
        {
            this.isTerminus = state;
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
    
    /**
     * Renames the point
     * @param newName 
     */
    public void rename(String newName)
    {
        this.name = newName;
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
    
    public Integer getMaxTrainLength()
    {
        return maxTrainLength;
    }

    /**
     * Sets the maximum train length allowed at this point
     * @param maxTrainLength 
     */
    public void setMaxTrainLength(Integer maxTrainLength)
    {
        assert maxTrainLength >= 0;
        
        this.maxTrainLength = maxTrainLength;
    }
    
    /**
     * Checks if this station's train length is long enough for the passed locomotive to stop there
     * @param loc 
     * @return (true if OK to proceed)
     */
    public boolean validateTrainLength(Locomotive loc)
    {
        if (!this.isDestination) return true;
        if (this.getMaxTrainLength() == 0) return true;
        
        return loc.getTrainLength() <= this.getMaxTrainLength();   
    }
    
    /**
     * Converts this point to a JSON representation
     * @return 
     * @throws java.lang.IllegalAccessException 
     * @throws java.lang.NoSuchFieldException 
     */
    public JSONObject toJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {		
        JSONObject jsonObj = new JSONObject();
        Field map = jsonObj.getClass().getDeclaredField("map");
        map.setAccessible(true);
        map.set(jsonObj, new LinkedHashMap<>());
        map.setAccessible(false);
        
        jsonObj.put("name", this.getName());
        jsonObj.put("station", this.isDestination);
        
        if (this.hasS88())
        {
            jsonObj.put("s88", new Integer(this.s88));
        }
        
        if (this.isDestination && this.maxTrainLength > 0)
        {
            jsonObj.put("maxTrainLength", this.maxTrainLength);
        }
        
        if (this.currentLoc != null && this.isDestination)
        {
            jsonObj.put("loc", this.currentLoc.getName());
            jsonObj.put("locReversible", this.currentLoc.isReversible());
            jsonObj.put("locSpeed", this.currentLoc.getPreferredSpeed());
            
            if (this.currentLoc.getTrainLength() > 0)
            {
                jsonObj.put("locTrainLength", this.currentLoc.getTrainLength());
            }
        }
        
        if (this.isTerminus)
        {
            jsonObj.put("terminus", this.isTerminus);
        }
        
        if (this.currentLoc != null && this.currentLoc.getArrivalFunc() != null)
        {
            jsonObj.put("locArrivalFunc", this.currentLoc.getArrivalFunc());
        }
        
        if (this.currentLoc != null && this.currentLoc.getDepartureFunc() != null)
        {
            jsonObj.put("locDepartureFunc", this.currentLoc.getDepartureFunc());
        }
        
        if (this.coordinatesSet())
        {
            jsonObj.put("x", this.getX());
            jsonObj.put("y", this.getY());
        }
        
        return jsonObj;
    }
}
