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
import org.traincontrol.util.I18n;

/**
 * Represent stations/stops as graph points
 * @author Adam
 */
public class Point
{
    // volatile: setLocomotive is synchronized but getCurrentLocomotive is not, and both are called
    // across autonomy/UI threads.  volatile gives the unsynchronized reader visibility of the latest write.
    private volatile Locomotive currentLoc;
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
    private double speedMultiplier = 1.0;

    // The locomotive this station has been assigned to, by NAME rather than by reference.
    //
    // A name rather than a reference because an assignment outlives placement: a locomotive that is not
    // currently on the graph keeps its station, and is simply ignored while it is absent.  A name that
    // matches no locomotive at all is a different case - it is reported and cleared on load, since
    // nothing can ever resolve it - but neither invalidates the layout.
    //
    // Null means no assignment, which is the state every existing layout is in - and in that state the
    // home of a locomotive is still simply where it was standing when the graph loaded.
    private String homeLoc;

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
            throw new Exception(
                I18n.f("autolayout.errorDestinationPointMustHaveS88")
            );
        }

        // Fail where the mistake is made.  toJSON does Integer.valueOf(s88), so a non-numeric
        // value accepted here used to explode at save time as an unchecked NumberFormatException,
        // far from the call that caused it.  createPoint validates that the feedback exists, but
        // this constructor is public and validated nothing.
        if (s88 != null)
        {
            try
            {
                Integer.parseInt(s88.trim());
            }
            catch (NumberFormatException e)
            {
                throw new Exception(
                    I18n.f("autolayout.errorStationMustHaveValidS88Address")
                );
            }
        }
        
        // Save the immutable unique ID
        this.uniqueId = ++id;
    }
    
    /**
     * Get the speed multiplier for this point
     * @return 
     */
    public double getSpeedMultiplier()
    {
        return speedMultiplier;
    }
    
    /**
     * Get the speed multiplier for this point
     * @return 
     */
    public int getSpeedMultiplierPercent()
    {
        return (int) (100.0 * speedMultiplier);
    }
        
    /**
     * Change how much locomotives are slowed when this point is traversed
     * @param speedMultiplier 
     * @throws java.lang.Exception 
     */
    public void setSpeedMultiplier(double speedMultiplier) throws Exception
    {
        if (speedMultiplier > 0 && speedMultiplier <= 2)
        {
            this.speedMultiplier = speedMultiplier;
        }
        else
        {
            throw new Exception(
                I18n.f("autolayout.errorSpeedMultiplierRange")
            );
        }
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
        if (value == null && this.isDestination()) // || this.isReversing()))
        {
            throw new NumberFormatException(
                I18n.f("autolayout.errorStationMustHaveValidS88Address")
            );
        }
        
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
     * Removes a locomotive from the exclusion set, if it is in it.
     *
     * A plain removal is safe: MarklinLocomotive hashes by identity, so a locomotive's hash cannot
     * move while it sits in this set.  That was not always true - see the hash-identity note on
     * MarklinLocomotive.hashCode.
     *
     * @param l the locomotive to stop excluding
     * @return true if it was excluded and no longer is
     */
    synchronized public boolean removeExcludedLoc(Locomotive l)
    {
        if (l == null) return false;

        return this.excludedLocs.remove(l);
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
            throw new Exception(
                I18n.f("autolayout.errorStationMustHaveS88SensorSetAddressFirst")
            );
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
            throw new Exception(
                I18n.f("autolayout.errorOnlyDestinationPointsCanBeTerminus")
            );
        }
        else if (isReversing && state)
        {
            throw new Exception(
                I18n.f("autolayout.errorReversingPointsCannotBeTerminus")
            );
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
            throw new Exception(
                I18n.f("autolayout.errorTerminusStationsCannotBeSetAsReversing")
            );
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
        // 0 when no coordinates have been assigned, rather than unboxing a null Integer into a
        // NullPointerException carrying no message.  0 is already this codebase's "not positioned"
        // value - TrainControlUI treats (0,0) exactly like !coordinatesSet() when deciding whether to
        // auto-lay-out the graph - so a caller that forgets the check lands on that same path instead
        // of crashing.  Anything needing to tell "at the origin" from "unset" must ask coordinatesSet().
        return this.x == null ? 0 : this.x;
    }

    public void setX(int x)
    {
        this.x = x;
    }

    public int getY()
    {
        // See getX - 0 means "not positioned", not "at the origin"
        return this.y == null ? 0 : this.y;
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
        // Null and negative both mean "no limit", the meaning 0 already has.  The assert this
        // replaces was inert at runtime, so a null was stored and validateTrainLength later
        // threw NullPointerException unboxing it.
        this.maxTrainLength = (maxTrainLength == null || maxTrainLength < 0) ? 0 : maxTrainLength;

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
     * The locomotive assigned to this station, by name, or null.
     * @return
     */
    public String getHomeLoc()
    {
        return this.homeLoc;
    }

    /**
     * Assigns this station to a locomotive by name, or clears it with null.
     *
     * Not validated here: a name that matches nothing is a legitimate stored state, reported on load
     * rather than refused.
     *
     * Blank means unassigned, but a name that is not blank is stored exactly as given.  Locomotive
     * names are only checked for being blank when they are created, never trimmed, so surrounding space
     * is part of the name - and trimming it here would store something that matches no locomotive at
     * all, which the next rebuild reports as missing from the database and drops.
     *
     * @param homeLoc
     */
    public void setHomeLoc(String homeLoc)
    {
        this.homeLoc = homeLoc == null || homeLoc.trim().isEmpty() ? null : homeLoc;
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
        
        if (this.speedMultiplier != 1.0)
        {
            jsonObj.put("speedMultiplier", this.speedMultiplier);
        }
        
        if (this.homeLoc != null)
        {
            jsonObj.put("home", this.homeLoc);
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
