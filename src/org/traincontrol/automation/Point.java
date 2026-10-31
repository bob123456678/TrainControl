package org.traincontrol.automation;

import org.traincontrol.base.Locomotive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * Represent stations/stops as graph points
 * @author Adam
 */
public class Point
{
    /**
     * The side a train standing here came IN by, which is where its tail lies.
     *
     * Facing cannot answer this: a train faces the way it will leave, and once turned round (arrived from the west,
     * turned to face east) its tail points the same way it faces, so inferring the tail from the heading finds the
     * wrong track.  A compass side rather than an edge, because an edge is directional and a tail is not.  Null when
     * nothing has said.
     */
    private String arrivedFrom;

    /** The route the standing train arrived along, or null (MT-335).  See `getArrivedAlong`. */
    private java.util.List<Edge> arrivedAlong;

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
     * Whether full autonomy may CHOOSE this station as a destination; true by default.  Separate from active: this only
     * withholds the station from automatic selection, so a route picked by hand and Return Home still reach it.
     */
    private boolean autoDestination = true;
    private Set<Locomotive> excludedLocs;

    /**
     * Whether `excludedLocs` names the ONLY locomotives allowed here rather than the ones kept out (FR-121).
     * `excludes` reads the list in its mode, so a locomotive added to the database later is excluded too; turning the
     * mode back keeps the choice.
     */
    private boolean allowOnlyListed = false;

    /** The key the mode is written under, beside `excludedLocs` - only when the list names the locomotives allowed. */
    public static final String EXCLUDED_LOCS_MODE = "excludedLocsMode";

    /** The value of `EXCLUDED_LOCS_MODE` that makes the list the locomotives allowed (Adam's word for it). */
    public static final String ALLOW_ONLY_LISTED = "whitelist";

    private double speedMultiplier = 1.0;

    /**
     * The locomotive this station is the home of, or null.  Held as the object; the file holds its name (the boundary
     * is `Layout.parseAuto` and `toJSON`).  An assignment outlives placement: a locomotive not on the graph keeps its
     * station and is ignored while absent, and a name that resolves to no locomotive is reported and cleared on load.
     */
    private Locomotive homeLoc;

    /**
     * Whether the home was set facing the way this copy faces, so Return Home brings its locomotive back to a copy
     * facing that way (OB-282).  False means the home is the square, whichever copy.
     */
    private boolean homeFacingFixed;

    /**
     * On a split square, the way a train standing on this copy points; null on a square that is one Point (OB-282).
     * The facing and not the arrival side: a turning copy shares its plain twin's arrival and faces the other way.
     */
    private String copyFacing;

    // Unique ID for any new node
    /** The id allocator; atomic because `++` on a static is not, and `uniqueId` keys every stored setting. */
    private static final java.util.concurrent.atomic.AtomicInteger ID_ALLOCATOR =
        new java.util.concurrent.atomic.AtomicInteger(0);
  
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

        // Fail where the mistake is made: toJSON does Integer.valueOf(s88), so a non-numeric value must be refused
        // here, in a public constructor, rather than at save time far from the cause.
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
        this.uniqueId = ID_ALLOCATOR.incrementAndGet();
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
    
    /** Whether full autonomy may choose this station as a destination. */
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
     * `isPathClear` refuses a path THROUGH an inactive point whatever it is (the editor's Out of service).
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
        // Null means "no priority" (0): the priority dialog sends null when the box is cleared, and the path comparator
        // and toJSON's `!= 0` need a number.
        this.priority = (value == null) ? 0 : value;

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
     * Whether this point keeps a locomotive out: one its list names or, in allow-only mode (FR-121), any it does not.
     * Every exclusion rule asks this rather than the list.
     *
     * @param loc the locomotive, or null
     * @return true where it is excluded; false for null
     */
    public boolean excludes(Locomotive loc)
    {
        if (loc == null) return false;

        boolean listed = excludedLocs.contains(loc);

        return allowOnlyListed ? !listed : listed;
    }

    /** Whether the list names the only locomotives allowed here (FR-121), rather than the ones kept out. */
    public boolean isAllowOnlyListed()
    {
        return allowOnlyListed;
    }

    /** Reads the list as the only locomotives allowed here, or (false, the default) as the ones kept out (FR-121). */
    public void setAllowOnlyListed(boolean allowOnlyListed)
    {
        this.allowOnlyListed = allowOnlyListed;
    }

    /**
     * Removes a locomotive from the exclusion set, if it is in it.  A plain removal is safe because MarklinLocomotive
     * hashes by identity, so its hash cannot move while it sits in this set.
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
    
    /**
     * @return the side a standing train arrived by, or null when nothing has said
     */
    public String getArrivedFrom()
    {
        return this.arrivedFrom;
    }

    /**
     * Records which side a train came in by, so its tail can be found.
     *
     * @param side the compass side, or null to forget
     */
    public void setArrivedFrom(String side)
    {
        this.arrivedFrom = side;
    }

    /**
     * The route the standing train arrived along, which its tail follows back past a junction (MT-335).
     *
     * `arrivedFrom` answers the first hop; past a junction the graph cannot say which road the train lies on, but the
     * route of the train that drove there can.  Cleared on the same change of occupant as `arrivedFrom`; saved with
     * the point, and a hand-placed train can be given one (FR-085, behaviour/tail.md 5c).
     *
     * @return the edges in the order they were driven, or null when nothing was driven here
     */
    public java.util.List<Edge> getArrivedAlong()
    {
        return this.arrivedAlong;
    }

    /**
     * Records the route a train arrived along, so its tail can be followed past a junction.
     *
     * @param path the edges in the order they were driven, or null to forget
     */
    public void setArrivedAlong(java.util.List<Edge> path)
    {
        this.arrivedAlong = path == null ? null
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(path));
    }

    public Point setLocomotive(Locomotive l)
    {
        final Locomotive previousOccupant = this.getCurrentLocomotive();
        // One locomotive, one place, enforced here because this is the only door: it is swept off every other Point
        // first.  The sweep runs BEFORE this Point's monitor is taken, since holding one Point's lock while taking
        // another's can deadlock two placements.  It assumes one thread places a given locomotive at a time; sweep and
        // assignment are not atomic, and the fix if that changes is a placement lock, not a wider monitor.
        if (l != null && this.layout != null) this.layout.clearLocomotiveExcept(l, this);

        assign(l);

        // The signal follows the platform through this one door: red while it is claimed (a standing train or a locked
        // path that reserved it, not one only passing through - OB-315, `Layout.onlyPassing`), green when not, and
        // never red while an active path holds it green (OB-340).
        if (this.layout != null) this.layout.refreshProtectingSignal(this);

        // AND THE TAIL BELONGS TO THE TRAIN THAT LEFT.
        //
        // `arrivedFrom` describes which way the occupant came in.  A different occupant did not come
        // in that way, and an empty platform has no tail at all - so leaving the old value standing
        // makes the walk block the rail behind whoever was here BEFORE, which is now clear, while
        // leaving the rail the new train is actually lying across open.
        //
        // Cleared on every change of occupant rather than only on removal: a platform handed straight
        // from one train to another is where the stale value is least visible and most wrong.
        if (l != previousOccupant) this.arrivedFrom = null;

        // The route goes with the side (MT-335); a train placed by hand has none.
        if (l != previousOccupant) this.arrivedAlong = null;
        return this;
    }

    /**
     * Puts a locomotive on this Point without taking it off anywhere else: for RESERVING, where a locked path holds
     * every point along it at once and the sweep of `setLocomotive` would tear the reservation down.  Package-private:
     * only the layout locks and unlocks paths.
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
        Locomotive previousOccupant = this.currentLoc;

        this.currentLoc = l;

        // Both clear on a change of occupant, as in setLocomotive; a locomotive reserving the square it stands on keeps
        // its tail.
        if (l != previousOccupant)
        {
            this.arrivedFrom = null;
            this.arrivedAlong = null;
        }

        return this;
    }

    /**
     * Which physical piece of track this Point is part of, or null when it stands alone.  A square emitted as several
     * Points (one per arrival side) shares one block, so a train on one copy occupies its twins.  Not the s88:
     * different places legitimately share a sensor.  Set by the builder.
     */
    private String block;

    /**
     * The square of the diagram this Point is a copy of, as the build names it, or null on a Point written by hand.
     * A rebuild carries each train by it: a rename can hand a Point's name to another square.
     */
    private String square;

    /**
     * The accessories thrown to red while this platform is claimed.  A station's protection, not a side's: every copy
     * of a square carries the same ones.  A list because a platform reachable from two directions needs a signal on
     * each approach; they are commanded together.  Never null.
     */
    private final List<String> protectingSignals = new ArrayList<>();

    /**
     * The accessories thrown red when a train arrives here as the end of its journey: the entry guard (FR-096).  Never
     * turned green here; the next route that needs a signal sets it.  Carried by every copy of the square.  Never null.
     */
    private final List<String> entrySignals = new ArrayList<>();

    /** @return every entry-guard signal of this platform, in the order they were paired (FR-096) */
    public List<String> getEntrySignals()
    {
        return Collections.unmodifiableList(this.entrySignals);
    }

    /**
     * Replaces the entry guard (FR-096).
     *
     * @param accessories the signals, or null to clear
     * @return this
     */
    public Point setEntrySignals(List<String> accessories)
    {
        this.entrySignals.clear();

        if (accessories != null)
        {
            for (String one : accessories) if (one != null && !this.entrySignals.contains(one)) this.entrySignals.add(one);
        }

        return this;
    }

    /**
     * Points whose occupancy makes this station unavailable to autonomy (FR-001).  The build also emits a lock edge at
     * each watched square, but `Edge.isLockHeld` deliberately ignores a parked train beyond it, so this covers the
     * standing train.  The Points themselves, not names: `Layout.parseAuto` resolves names once every point exists.
     * Never null.
     */
    private final List<Point> blockedBy = new ArrayList<>();

    /** @return the points that make this station unavailable while they are occupied */
    public List<Point> getBlockedBy()
    {
        return Collections.unmodifiableList(this.blockedBy);
    }

    /**
     * Replaces the list of points that hold this station back.
     *
     * @param points the points, or null to clear
     * @return this
     */
    public Point setBlockedBy(List<Point> points)
    {
        this.blockedBy.clear();

        if (points != null)
        {
            for (Point one : points)
            {
                // De-duplicated, and a point never blocks itself (that would shut the station to everything).
                if (one != null && one != this && !this.blockedBy.contains(one))
                {
                    this.blockedBy.add(one);
                }
            }
        }

        return this;
    }

    /**
     * Where the FR-001 rule looks for who is standing on a watched square.  The two sources cannot be merged: the LIVE
     * railway (`onTheLiveBlock`, the authority `isPathClear` and the why-window use) and a PLANNED state, which the
     * staging planner answers from its own shadow occupancy.  Splitting the source out keeps the rule in one place.
     */
    public interface Occupancy
    {
        /**
         * @param track a square this station is held back by
         * @param exempt the locomotive being routed, which does not count as an occupant of it
         * @return whether anybody OTHER than exempt is standing on the same piece of track
         */
        boolean heldBySomebodyOtherThan(Point track, Locomotive exempt);
    }

    /**
     * The live railway's answer: the whole BLOCK, not just the named Point, since a train on another copy of the
     * watched square is standing on it.
     */
    public static Occupancy onTheLiveBlock()
    {
        return (track, exempt) ->
        {
            Locomotive standing = track.getBlockLocomotive();

            return standing != null && !standing.equals(exempt);
        };
    }

    /**
     * The square holding this one back, or null when none is (FR-001), asked of every square a route arrives at, not
     * only its destination (OB-295).  The one expression of the rule: callers supply only where to look for occupancy.
     *
     * The train LEAVING the watched square is exempt (Adam: "The condition should not apply to trains leaving - only
     * departing."), else a locomotive in the yard could never be sent to the platform the yard holds back.  Whether
     * the rule is in force at all is the caller's business; no caller fences it, in any mode.
     *
     * @param destination the square being arrived at - a route's destination or any square on the way (OB-295); a null
     *        square is held back by nothing
     * @param arriving the locomotive arriving, exempt where it is itself the occupant
     * @param occupancy where to look for who is standing where
     * @return the watched square somebody else is standing on, or null when nobody else stands on any it watches
     */
    public static Point heldBackBy(Point destination, Locomotive arriving, Occupancy occupancy)
    {
        if (destination == null) return null;

        for (Point watched : destination.getBlockedBy())
        {
            // Never null in practice (setBlockedBy drops nulls); guarded once here so no caller has to.
            if (watched == null) continue;

            if (occupancy.heldBySomebodyOtherThan(watched, arriving)) return watched;
        }

        return null;
    }

    /**
     * The rule asked of the live railway, which is what everything outside the staging planner wants.
     *
     * @param destination the square being arrived at - a route's destination or any square on the way (OB-295)
     * @param arriving the locomotive arriving, exempt where it is itself the occupant
     * @return the watched square somebody else is standing on, or null
     */
    public static Point heldBackBy(Point destination, Locomotive arriving)
    {
        return heldBackBy(destination, arriving, onTheLiveBlock());
    }

    /** @return the first signal protecting this platform, or null */
    public String getProtectingSignal()
    {
        return this.protectingSignals.isEmpty() ? null : this.protectingSignals.get(0);
    }

    /** @return every signal protecting this platform, in the order they were paired */
    public List<String> getProtectingSignals()
    {
        return Collections.unmodifiableList(this.protectingSignals);
    }

    /**
     * Replaces the protection with one accessory, or none.
     *
     * @param accessory the signal, or null to clear
     * @return this
     */
    public Point setProtectingSignal(String accessory)
    {
        this.protectingSignals.clear();

        if (accessory != null) this.protectingSignals.add(accessory);

        return this;
    }

    /**
     * Replaces the protection with a list.
     *
     * @param accessories the signals, or null to clear
     * @return this
     */
    public Point setProtectingSignals(List<String> accessories)
    {
        this.protectingSignals.clear();

        if (accessories != null)
        {
            for (String one : accessories)
            {
                if (one != null && !this.protectingSignals.contains(one)) this.protectingSignals.add(one);
            }
        }

        return this;
    }

    /**
     * Whether this and another Point are the same piece of track (MT-165): a train on any copy of a square is standing
     * on that platform, so a rule about the square must not compare Points.
     *
     * @param other the other Point, or null
     * @return true when a train on one is standing where a train on the other is standing
     */
    public boolean isSamePlaceAs(Point other)
    {
        if (other == null) return false;

        if (this.equals(other)) return true;

        return this.block != null && this.block.equals(other.getBlock());
    }

    /** @return the block this Point shares with the other copies of its square, or null */
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
     * The locomotive standing anywhere on this Point's piece of track: its own, else one on another copy of the square.
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
     * The layout this Point belongs to, set by the layout as it takes the Point; null on a Point built and not added.
     */
    private Layout layout;

    /**
     * The layout this Point belongs to, or null when it was built and never added.  Package-private: for rules inside
     * the automation model that ask about a Point's neighbors.
     */
    Layout getLayout()
    {
        return this.layout;
    }

    /** @param layout the layout taking ownership of this Point */
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

        // A train of unknown length is not too long.
        if (loc.getTrainLength() == null) return true;
        
        return loc.getTrainLength() <= this.getMaxTrainLength();   
    }
    
    /** The locomotive assigned to this station, or null. */
    public Locomotive getHomeLoc()
    {
        return this.homeLoc;
    }

    /**
     * Assigns this station to a locomotive, or clears it with null.  Not validated here: the file holds a name, and one
     * that matches nothing is reported on load.
     *
     * @param homeLoc the locomotive, or null
     */
    public void setHomeLoc(Locomotive homeLoc)
    {
        this.homeLoc = homeLoc;

        // A facing belongs to a home; taking the home away takes it too.
        if (homeLoc == null) this.homeFacingFixed = false;
    }

    /** @return whether this Point's home was set facing the way this copy faces (OB-282) */
    public boolean isHomeFacingFixed()
    {
        return this.homeLoc != null && this.homeFacingFixed;
    }

    /** @param fixed whether this Point's home was set facing the way this copy faces (OB-282) */
    public void setHomeFacingFixed(boolean fixed)
    {
        this.homeFacingFixed = fixed;
    }

    /** @return the square of the diagram this Point is a copy of, or null where no build said */
    public String getSquare()
    {
        return this.square;
    }

    /** @param square the square of the diagram this Point is a copy of, or null */
    public void setSquare(String square)
    {
        this.square = square;
    }

    /** @return the way a train standing on this copy points, or null on a square that is one Point (OB-282) */
    public String getCopyFacing()
    {
        return this.copyFacing;
    }

    /** @param side the way a train standing on this copy points, or null */
    public void setCopyFacing(String side)
    {
        this.copyFacing = side;
    }

    /**
     * Converts this point to a JSON representation
     * @return 
     * @throws java.lang.IllegalAccessException 
     * @throws java.lang.NoSuchFieldException 
     */
    public JSONObject toJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {
        return toJSON(this.currentLoc);
    }

    /**
     * The same, writing the given locomotive as the one standing here - or none, for null - rather than whichever this
     * point records.  A train under way is recorded on every point of its locked path, and a fold writes it on
     * the one point it is kept at.  The side it arrived by and its road are written only for the train this point
     * records.
     *
     * @param standing the locomotive to write as standing here, or null
     * @return the point
     * @throws IllegalArgumentException from the JSON
     * @throws IllegalAccessException from the JSON
     * @throws NoSuchFieldException from the JSON
     */
    public JSONObject toJSON(Locomotive standing) throws IllegalArgumentException, IllegalAccessException,
        NoSuchFieldException
    {		
        JSONObject jsonObj = Util.orderedJSON();
        
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

        // Written as well as read (parseAuto takes it back).  One signal is a bare string and several an array: the
        // single case is what older versions read.
        if (this.protectingSignals.size() == 1)
        {
            jsonObj.put("protectingSignal", this.protectingSignals.get(0));
        }
        else if (!this.protectingSignals.isEmpty())
        {
            jsonObj.put("protectingSignal", new JSONArray(this.protectingSignals));
        }

        // The entry guard (FR-096), in the same two shapes.
        if (this.entrySignals.size() == 1)
        {
            jsonObj.put("entrySignal", this.entrySignals.get(0));
        }
        else if (!this.entrySignals.isEmpty())
        {
            jsonObj.put("entrySignal", new JSONArray(this.entrySignals));
        }

        // Written because parseAuto reads it: without it a re-imported graph splits every square into independent
        // Points.
        if (this.block != null)
        {
            jsonObj.put("block", this.block);
        }

        // Read back by parseAuto too
        if (this.square != null) jsonObj.put("square", this.square);

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
            // By NAME, because a file has to (the other side is Layout.parseAuto).
            jsonObj.put("home", this.homeLoc.getName());

            if (this.homeFacingFixed) jsonObj.put("homeFacingFixed", true);
        }

        if (this.copyFacing != null) jsonObj.put("copyFacing", this.copyFacing);
        
        if (standing != null)
        {
            JSONObject locObj = Util.orderedJSON();
            
            locObj.put("name", standing.getName());
            locObj.put("reversible", standing.isReversible());
            // Written only when it is a speed (MT-233): zero means "not set" to parseAuto, and absence says the same
            // thing without being misread, as arrivalFunc and departureFunc below do.
            if (standing.getPreferredSpeed() > 0)
            {
                locObj.put("speed", standing.getPreferredSpeed());
            }
            
            if (standing.getArrivalFunc() != null)
            {
                locObj.put("arrivalFunc", standing.getArrivalFunc());
            }
        
            if (standing.getDepartureFunc() != null)
            {
                locObj.put("departureFunc", standing.getDepartureFunc());
            }
            
            // A length nobody has set is null, and unboxing it failed the whole Layout.toJSON.
            Integer trainLength = standing.getTrainLength();

            if (trainLength != null && trainLength > 0)
            {
                locObj.put("trainLength", trainLength);
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

            // SORTED so the file is the same file when nothing changed: Locomotive has no hashCode override, so set
            // iteration order differs on every JVM run and merely opening and saving a layout rewrote the array.
            java.util.Collections.sort(locNames);

            jsonObj.put("excludedLocs", locNames);
        }

        // The list's mode, written only where it names the locomotives allowed (FR-121) - an empty list then
        // keeps every locomotive out, so the mode is written whether the list is or not
        if (this.allowOnlyListed)
        {
            jsonObj.put(EXCLUDED_LOCS_MODE, ALLOW_ONLY_LISTED);
        }

        if (!this.blockedBy.isEmpty())
        {
            // By NAME, because a file has to; Layout.parseAuto resolves them once every point exists.
            List<String> named = new ArrayList<>();

            for (Point blocker : this.blockedBy) named.add(blocker.getName());

            jsonObj.put("blockedBy", new JSONArray(named));
        }

        // Only beside an occupant: a side with nobody on the square records a train the database no longer has.
        if (this.arrivedFrom != null && standing != null && standing == this.currentLoc)
        {
            jsonObj.put("arrivedFrom", this.arrivedFrom);
        }

        // And the road it came in on, which the side alone cannot say past a junction: [start, end] name pairs, the
        // form `Layout.roadNamed` reads.
        String road = Layout.namesOfRoad(this.arrivedAlong);

        if (road != null && standing != null && standing == this.currentLoc)
        {
            jsonObj.put("arrivedAlong", new JSONArray(road));
        }
                
        if (this.coordinatesSet())
        {
            jsonObj.put("x", this.getX());
            jsonObj.put("y", this.getY());
        }
        
        return jsonObj;
    }
}
