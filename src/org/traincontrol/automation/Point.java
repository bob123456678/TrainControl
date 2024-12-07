package org.traincontrol.automation;

import org.traincontrol.base.Locomotive;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private boolean isReversing;
    private Integer x;
    private Integer y;
    private Integer maxTrainLength = 0;
    private Integer priority = 0;
    private Integer uniqueId;
    private boolean active;
    private Set<Locomotive> excludedLocs;
    
    // Unique ID for any new node
    private static Integer id = 0;
  
    public Point(String name, boolean isDestination, String s88) throws Exception
    {
        this.name = name.replace("\"", "");
        this.isDestination = isDestination;
        this.s88 = s88;
        this.currentLoc = null;
        this.isTerminus = false;
        this.isReversing = false;
        this.active = true;
        this.excludedLocs = new HashSet<>();

        if (isDestination && !hasS88())
        {
            throw new Exception("Destination point must have S88");
        }
        
        // Save the immutable unique ID
        this.uniqueId = ++id;
    }
    
    /**
     * Sets the point as active or inactive
     * @param status 
     */
    public void setActive(boolean status)
    {
        this.active = status;
    }
    
    /**
     * Returns if the point is active.
     * Active means the point will be selected by autonomous logic
     * Ignored for non-stations
     * @return 
     */
    public boolean isActive()
    {        
        return this.active;
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
     * @return  
     */
    public Point setS88(Integer value)
    {
        if (value != null)
        {
            this.s88 = Integer.toString(Math.abs(value));
        }
        else
        {
            this.s88 = null;
        }
        
        return this;
    }
    
    /**
     * Sets the point's priority
     * @param value 
     * @return  
     */
    public Point setPriority(Integer value)
    {
        this.priority = value;
        
        return this;
    }

    public Set<Locomotive> getExcludedLocs()
    {
        return excludedLocs;
    }

    public void setExcludedLocs(Set<Locomotive> excludedLocs)
    {
        if (excludedLocs != null)
        {
            this.excludedLocs = excludedLocs;
        }
    }
    
    /**
     * Returns the point's priority
     * @return 
     */
    public int getPriority()
    {
        return this.priority;
    }
    
    /**
     * Changes the state of this station
     * @param state
     * @return 
     * @throws Exception 
     */
    public Point setDestination(boolean state) throws Exception
    {
        if (state && !hasS88())
        {
            throw new Exception("Stations must have an s88 sensor.  Set the s88 address first.");
        }
        
        this.isDestination = state;
        
        // Reset terminus status
        if (!this.isDestination) this.isTerminus = false;
        
        return this;
    }
    
    /**
     * A terminus station will require the departing train to change direction
     * @param state
     * @return 
     * @throws Exception 
     */
    public Point setTerminus(boolean state) throws Exception
    {
        if (!isDestination && state)
        {
            throw new Exception("Only destination points (stations) can be a terminus");
        }
        else if (isReversing && state)
        {
            throw new Exception("Reversing points cannot be set as terminus");
        }
        else
        {
            this.isTerminus = state;
        }
        
        return this;
    }
    
    /**
     * A reversing station will require the departing train to change direction, as part of shunting operations
     * @param state
     * @return 
     * @throws Exception 
     */
    public Point setReversing(boolean state) throws Exception
    {
        if (isTerminus && state)
        {
            throw new Exception("Terminus stations cannot be set as reversing");
        }
        else
        {
            this.isReversing = state;
        }
        
        return this;
    }
    
    public boolean isReversing()
    {
        return isReversing;
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

    @Override
    public int hashCode()
    {
        int hash = 5;
        hash = 59 * hash + Objects.hashCode(this.name);
        return hash;
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
    
    synchronized public Point setLocomotive(Locomotive l)
    {
        this.currentLoc = l;
        
        return this;
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
     * @return  
     */
    public Point setMaxTrainLength(Integer maxTrainLength)
    {
        assert maxTrainLength >= 0;
        
        this.maxTrainLength = maxTrainLength;
        
        return this;
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
        
        if (!this.isActive())
        {
            jsonObj.put("active", this.active);
        }
        
        if (this.hasS88())
        {
            jsonObj.put("s88", Integer.valueOf(this.s88));
        }
        
        if (this.isDestination || this.maxTrainLength > 0)
        {
            jsonObj.put("maxTrainLength", this.maxTrainLength);
        }
        
        if (this.isTerminus)
        {
            jsonObj.put("terminus", this.isTerminus);
        }
                
        if (this.isReversing)
        {
            jsonObj.put("reversing", this.isReversing);
        }
        
        if (this.priority != 0)
        {
            jsonObj.put("priority", this.priority);
        }
        
        if (this.currentLoc != null)
        {
            JSONObject locObj = new JSONObject();
            
            map = locObj.getClass().getDeclaredField("map");
            map.setAccessible(true);
            map.set(locObj, new LinkedHashMap<>());
            map.setAccessible(false);
            
            locObj.put("name", this.currentLoc.getName());
            locObj.put("reversible", this.currentLoc.isReversible());
            locObj.put("speed", this.currentLoc.getPreferredSpeed());
            
            if (this.currentLoc.getArrivalFunc() != null)
            {
                locObj.put("arrivalFunc", this.currentLoc.getArrivalFunc());
            }
        
            if (this.currentLoc.getDepartureFunc() != null)
            {
                locObj.put("departureFunc", this.currentLoc.getDepartureFunc());
            }
            
            if (this.currentLoc.getTrainLength() > 0)
            {
                locObj.put("trainLength", this.currentLoc.getTrainLength());
            }
            
            jsonObj.put("loc", locObj);
        }
        
        if (!this.excludedLocs.isEmpty())
        {
            List<String> locNames = new ArrayList<>();
            for (Locomotive l : excludedLocs)
            {
                locNames.add(l.getName());
            }

            jsonObj.put("excludedLocs", locNames);
        }
                
        if (this.coordinatesSet())
        {
            jsonObj.put("x", this.getX());
            jsonObj.put("y", this.getY());
        }
        
        return jsonObj;
    }
}
