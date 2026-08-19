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

    /**
     * Whether full autonomy may CHOOSE this station as somewhere to send a train.
     *
     * Defaults to true, so a configuration written before this existed behaves exactly as it did.
     *
     * Separate from active on purpose.  Inactive means the point is out of service - nothing may pass
     * through it and nothing may be sent to it - whereas this only withholds it from automatic
     * selection: a route the user picks by hand still reaches it, and so does Return Home, which is
     * what makes a parking berth a berth rather than a closed siding.
     *
     * It also replaces the way that used to be said.  A "reversing station" was the only way to keep
     * autonomy from choosing a station, and it dragged two unrelated behaviours along with it - the
     * train reversed on arrival, and no path could be routed through.  Those are now what a terminus
     * and a shut arm say, each on its own.
     */
    private boolean autoDestination = true;
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
     * Whether full autonomy may choose this station as a destination.
     * @return
     */
    public boolean isAutoDestination()
    {
        return autoDestination;
    }

    /**
     * @param status false to keep autonomy from choosing this station of its own accord.  Routes the
     *        user picks, and Return Home, are unaffected.
     */
    public void setAutoDestination(boolean status)
    {
        this.autoDestination = status;
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
    
    public Point setLocomotive(Locomotive l)
    {
        // One locomotive, one place - enforced HERE, because here is the only door.
        //
        // A train is a physical object and cannot be in two places, but nothing in the model said so:
        // every caller was trusted to take it off wherever it was first, and they did not all do it.
        // moveLocomotive stopped at the first copy it found; parseAuto placed from the file without
        // looking; and a square is several Points now, so "wherever it was" is a set rather than a
        // place.  The result was a locomotive standing on two copies of one platform, a removal that
        // cleared one of them, and a configuration that fromJSON then refused outright.
        //
        // Backwards compatible in the way that matters: a Point with no layout behind it - every Point
        // built by hand in a test, and every one built before this - behaves exactly as it did.
        // Swept BEFORE this Point's own monitor is taken, which is why the sweep is not inside the
        // synchronized part.  Holding one Point's lock while taking another's is how two placements on
        // two threads deadlock each other.
        //
        // The invariant this rests on is that a locomotive is PLACED by one thread at a time - its own
        // driver thread, or a UI gesture while autonomy is stopped.  It is not a claim that the sweep
        // and the assignment are atomic: two threads placing the SAME locomotive could both sweep, find
        // nothing, and both assign, leaving it in two places.  Nothing does that today, and the fix if
        // something ever needs to would be a placement lock, not a wider monitor here.
        if (l != null && this.layout != null) this.layout.clearLocomotiveExcept(l, this);

        assign(l);

        // The signal follows the platform, through the one door every occupancy change goes through.
        //
        // Red while the platform is claimed and green when it is not - and "claimed" covers a train
        // standing there AND a locked path that has reserved it, because reserving sets the locomotive
        // exactly as arriving does.  One derived rule rather than a hook on arrival and another on
        // departure, so a released or failed path cannot leave a signal stuck red: whatever clears the
        // reservation clears the signal with it.
        if (this.layout != null) this.layout.refreshProtectingSignal(this);

        return this;
    }

    /**
     * Puts a locomotive on this Point without taking it off anywhere else.
     *
     * For RESERVING a point, which is a different thing from placing a train there and must not sweep.
     * A locked path reserves every point along it for one locomotive at once - that is how a junction
     * two trains could reach is held against the second while the first runs through it - so the sweep
     * that keeps a PLACED train in one place would tear a running train's own reservation down to its
     * destination and free every junction behind it.
     *
     * Package-private: only the layout locks and unlocks paths, and only it should be able to say a
     * locomotive is in more than one place at once.
     *
     * @param l the locomotive reserving this point
     * @return this
     */
    Point reserve(Locomotive l)
    {
        assign(l);

        if (this.layout != null) this.layout.refreshProtectingSignal(this);

        return this;
    }

    synchronized private Point assign(Locomotive l)
    {
        this.currentLoc = l;

        return this;
    }

    /**
     * Which physical piece of track this Point is part of, or null when it is one of its own.
     *
     * A square of the diagram is emitted as several Points - one per side a train can arrive by - and
     * they are the SAME piece of track.  Two trains cannot be on it at once: that is a collision, not a
     * state to model.  But occupancy is recorded per Point, so a sibling copy reads free while a train
     * stands on its twin, and a second train could be routed onto it.
     *
     * Not the s88.  Genuinely different places legitimately share a sensor - a station, its approach
     * guard and a reversing point are three Points on one feedback in a real layout - so keying on it
     * would refuse paths that are perfectly safe.  The identity comes from the builder, which is the
     * only layer that knows two Points came from one tile.
     *
     * Null on everything hand-written or generated before this, which is exactly right: those Points
     * each stand alone, and behave as they always have.
     */
    private String block;

    /**
     * The accessory thrown to red while this platform is claimed, or null.
     *
     * A station's protection, not a side's: every copy of a square carries the same one, because the
     * copies are one platform.
     */
    private String protectingSignal;

    public String getProtectingSignal()
    {
        return this.protectingSignal;
    }

    /**
     * The aspect this Point's signal was last told to show, so an unchanged one is not commanded again.
     *
     * Null until the first command, which is what makes the first one always go out - a signal left red
     * by a previous session must be corrected even if the platform is free.
     */
    private Boolean signalClaimed;

    Boolean wasSignalClaimed()
    {
        return this.signalClaimed;
    }

    void rememberSignalClaimed(boolean claimed)
    {
        this.signalClaimed = claimed;
    }

    public Point setProtectingSignal(String accessory)
    {
        this.protectingSignal = accessory;

        return this;
    }

    /**
     * @return the block this Point shares with the other copies of its square, or null
     */
    public String getBlock()
    {
        return this.block;
    }

    /**
     * @param block the shared identity, from the builder
     * @return this
     */
    public Point setBlock(String block)
    {
        this.block = block;

        return this;
    }

    /**
     * The locomotive standing anywhere on this Point's piece of track.
     *
     * Its own if it has one, otherwise whatever is standing on another copy of the same square.  This
     * is what occupancy has to ask: a train on the eastbound copy of a platform is on the platform, and
     * the westbound copy is not free just because it is a different object.
     *
     * @return the locomotive, or null when the whole block is clear
     */
    public Locomotive getBlockLocomotive()
    {
        Locomotive mine = this.currentLoc;

        if (mine != null) return mine;

        if (this.block == null || this.layout == null) return null;

        return this.layout.locomotiveInBlock(this.block, this);
    }

    /**
     * The layout this Point belongs to, so that placing a locomotive can clear it from everywhere else.
     *
     * Set by the layout as it takes the Point, and never by anything else.  Null on a Point that was
     * built and not added, which is exactly when there is nowhere else for a locomotive to be.
     */
    private Layout layout;

    /**
     * @param layout the layout taking ownership of this Point
     */
    void setLayout(Layout layout)
    {
        this.layout = layout;
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

        // Which piece of track this Point is part of.
        //
        // Written because it is READ: parseAuto takes it back, and without this the export path lost
        // it silently - a graph exported and re-imported came back with every square split into
        // independent Points again, so two trains could once more be routed onto one platform.  The
        // operator would have had no way to tell: the file looks like a faithful copy of the graph.
        // The signal thrown to red while this platform is claimed.
        //
        // Written as well as read.  parseAuto has always taken this back in, and leaving it out of the
        // export made the configuration JSON quietly lossy: a setup exported and imported came back
        // with every station-signal pairing gone, and nothing said so.  Exactly what happened to the
        // block field before it, which is the line below.
        if (this.protectingSignal != null)
        {
            jsonObj.put("protectingSignal", this.protectingSignal);
        }

        if (this.block != null)
        {
            jsonObj.put("block", this.block);
        }

        // written only when it differs from the default, so a file gains no noise from a setting
        // nobody has touched
        if (!this.autoDestination)
        {
            jsonObj.put("autoDestination", this.autoDestination);
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
