package org.traincontrol.automation;

import org.traincontrol.base.Locomotive;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.json.JSONArray;
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

    // The locomotive this station has been assigned to, as a REFERENCE.
    //
    // This said "by NAME rather than by reference" and went on to argue for it, above a field that has
    // been a Locomotive since the object migration. Left standing it is worse than no comment: the
    // argument is sound and it is an argument for the opposite of what the field now is, so a reader
    // trusting it would look for name-comparison bugs that cannot exist and miss the identity ones
    // that can.
    //
    // What actually holds: an assignment outlives placement, so a locomotive that is not currently on
    // the graph keeps its station and is ignored while absent - that part was always about the
    // ASSIGNMENT rather than about how it is stored. The name/object boundary is toJSON and parseAuto
    // and nowhere else, and a name in the file that resolves to no locomotive is reported and cleared
    // on load. Neither case invalidates the layout.
    //
    // Null means no assignment, which is the state every existing layout is in - and in that state the
    // home of a locomotive is still simply where it was standing when the graph loaded.
    /**
     * The locomotive this station is the home of, or null.
     *
     * The LOCOMOTIVE, not its name.  It was a name, and five separate mechanisms existed only because
     * of that: a rename had to be followed into it, a deletion had to clear it, a load had to drop
     * names that matched nothing, and the resolved answer was kept a second time in Layout.homeStations
     * and rebuilt to stay in step.  Three of the defects fixed this week were that duplication.
     *
     * Holding the object makes most of it stop existing rather than get fixed: a rename changes the
     * object, so every reference to it is already right, and a locomotive that is not in the database
     * cannot be pointed at.  What is left is the two rules that are really about the railway - one
     * locomotive has one station, and a station whose locomotive has been deleted has none - and both
     * of those are now identity checks beside their neighbours rather than string comparisons.
     *
     * The FILE still holds a name, because a file has to; that boundary is in Layout.parseAuto and
     * Point.toJSON, and it is now the only place a home is a string.
     */
    private Locomotive homeLoc;

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
        // Null means "no priority", which is the meaning 0 already has - and the priority dialog
        // sends null every time somebody clears the box, which is the obvious way to say exactly
        // that.  Stored as null it read back through getPriority(), which returns int, and through
        // toJSON's `!= 0`: both unbox.  So clearing one point's priority threw NullPointerException
        // out of the path-choosing comparator - outside the try that guards executePath, so the
        // locomotive's dispatch thread died and that train silently stopped being sent anywhere for
        // the rest of the session - and threw again out of every attempt to SAVE the layout.
        //
        // The same fix setMaxTrainLength already carries, for the same reason.
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
     * The accessories thrown to red while this platform is claimed.
     *
     * A station's protection, not a side's: every copy of a square carries the same ones, because the
     * copies are one platform.
     *
     * A LIST rather than one accessory, because a platform reachable from two directions needs a
     * signal on each approach, and a real station often has several.  They are commanded together and
     * show the same aspect - they say the same thing about the same platform - so nothing here has to
     * decide which of them applies to an approach.  That would need the arrival side, which the Point
     * knows and the pairing does not.
     *
     * Never null, so no caller has to check twice.
     */
    private final List<String> protectingSignals = new ArrayList<>();

    /**
     * Points whose occupancy makes this station unavailable to autonomy.
     *
     * FR-001, and the half of it that lock edges cannot express.  The build ALSO emits a lock edge
     * ending at each watched square, which holds the station back while a route is running over that
     * approach - but Edge.isLockHeld asks about a reservation and deliberately not about a train
     * standing at the sensor beyond it, because counting a parked train made a locomotive beside a
     * junction a permanent roadblock.  Adam asked for both.
     *
     * The POINTS themselves, not their names.  A file holds names because a file has to, and
     * Layout.parseAuto resolves them once every point exists - so this and toJSON are the boundary, and
     * everywhere in between a restriction either points at a real place or is not there at all.
     *
     * That is what makes the rule below a lookup rather than a search: asking whether a named point is
     * occupied meant resolving the name on every path check, and answering "not occupied" for a name
     * that no longer matched anything - which is indistinguishable from the restriction being satisfied.
     *
     * Never null, so no caller has to check twice.
     */
    private final List<Point> blockedBy = new ArrayList<>();

    /**
     * @return the points that make this station unavailable while they are occupied
     */
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
                // De-duplicated, and a point never blocks itself: standing at a station already decides
                // whether it is free, so watching itself makes a station nothing can be sent to rather
                // than one that is restricted.
                if (one != null && one != this && !this.blockedBy.contains(one))
                {
                    this.blockedBy.add(one);
                }
            }
        }

        return this;
    }

    /**
     * Who is standing on a watched square, for the FR-001 rule below.
     *
     * The rule is one question - "is any square this station is held back by occupied by somebody
     * else?" - but there are two legitimate places to look for the answer, and they cannot be merged
     * because they are asked about different worlds:
     *
     *  - the LIVE railway, which `onTheLiveBlock()` answers by asking `getBlockLocomotive`; this is
     *    what `isPathClear` and the FR-017 why-window use, and it is the authority, because it is the
     *    copy that actually refuses the arrival;
     *  - a PLANNED state, which the staging planner answers out of its own shadow occupancy map,
     *    because "could this move happen after three other moves" has no answer on live feedback.
     *
     * Splitting the occupancy source out is what lets the rule itself - which squares are consulted,
     * and which locomotive is exempt - live in exactly one place.  Before this, three copies of that
     * iteration existed and answered differently (DR-B2).
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
     * The live railway's answer: the whole BLOCK, not just the named Point.
     *
     * A square emitted as several copies is one piece of track, so a train on the eastbound copy of the
     * watched point is standing on it, and asking only the copy that carries the name would answer
     * clear with a train there.
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
     * The square holding this destination back, or null when none is (FR-001).
     *
     * ONE expression of the rule.  It used to be written three times - the runtime check in
     * `isPathClear`, the staging planner's `canRest`, and the replay oracle in the staging tests - and
     * the three answered differently on real layouts, which is the defect DR-B2 named.  Everything that
     * asks the question now asks it here; what a caller supplies is only where to look for occupancy.
     *
     * The train LEAVING the watched square is exempt.  Adam, asked directly: "The condition should not
     * apply to trains leaving - only departing."  Without the exemption the one movement that clears
     * the condition is the movement it forbids: a locomotive standing in the yard could never be sent
     * to the platform the yard holds back, and while it sat there the platform was shut to everybody
     * else too.
     *
     * Note what this does NOT decide: whether the rule is in force at all.  The runtime fences it
     * behind `isAutoRunning` because it shapes what AUTONOMY chooses, and the planner applies it always
     * because staging executes with autonomy running.  That fence is a property of the caller, not of
     * the rule, so it stays at the call sites - and the staging audit carries an exemption for exactly
     * that difference (DR-B1).
     *
     * @param destination the station being arrived at; a null destination is held back by nothing
     * @param arriving the locomotive arriving, exempt where it is itself the occupant
     * @param occupancy where to look for who is standing where
     * @return the watched square somebody else is standing on, or null when the destination is free
     */
    public static Point heldBackBy(Point destination, Locomotive arriving, Occupancy occupancy)
    {
        if (destination == null) return null;

        for (Point watched : destination.getBlockedBy())
        {
            // Never null in practice - setBlockedBy drops nulls and parseAuto resolves names before it
            // is called - but the two production copies of this loop guarded it differently, one of
            // them defending against something its own constructor made impossible.  Guarded once here
            // so no caller has to decide again.
            if (watched == null) continue;

            if (occupancy.heldBySomebodyOtherThan(watched, arriving)) return watched;
        }

        return null;
    }

    /**
     * The rule asked of the live railway, which is what everything outside the staging planner wants.
     *
     * @param destination the station being arrived at
     * @param arriving the locomotive arriving, exempt where it is itself the occupant
     * @return the watched square somebody else is standing on, or null
     */
    public static Point heldBackBy(Point destination, Locomotive arriving)
    {
        return heldBackBy(destination, arriving, onTheLiveBlock());
    }

    /**
     * @return the first signal protecting this platform, or null
     */
    public String getProtectingSignal()
    {
        return this.protectingSignals.isEmpty() ? null : this.protectingSignals.get(0);
    }

    /**
     * @return every signal protecting this platform, in the order they were paired
     */
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
     * The locomotive assigned to this station, or null.
     * @return
     */
    public Locomotive getHomeLoc()
    {
        return this.homeLoc;
    }

    /**
     * Assigns this station to a locomotive, or clears it with null.
     *
     * Held as the locomotive itself; its NAME is what gets written out and matched back on load, which
     * is why the naming rules below decide what survives a round trip.  This pair used to say the
     * assignment was made by name, which the signature has never done (RC-C8).
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
    public void setHomeLoc(Locomotive homeLoc)
    {
        this.homeLoc = homeLoc;
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
        // One is written as a bare string, several as an array.  The single case is what every
        // version before this one wrote and read, and a station guarded by one signal - which is most
        // of them - stays readable to an older TrainControl rather than becoming an array it would
        // make nonsense of.
        if (this.protectingSignals.size() == 1)
        {
            jsonObj.put("protectingSignal", this.protectingSignals.get(0));
        }
        else if (!this.protectingSignals.isEmpty())
        {
            jsonObj.put("protectingSignal", new JSONArray(this.protectingSignals));
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
            // By NAME, because a file has to.  This and Layout.parseAuto are now the only two places a
            // home is a string; everywhere in between it is the locomotive.
            jsonObj.put("home", this.homeLoc.getName());
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
            // WRITTEN ONLY WHEN IT IS A SPEED (MT-233).
            //
            // Adam: "ensure a locomotive cannot have a speed of 0 set anywhere in the autonomy config
            // input files."  Zero is how this field says "not set", and parseAuto reads it that way
            // twice over - it applies a stored speed only between 1 and 100, and then fills anything
            // still at zero from defaultLocSpeed.  So a zero here was never dangerous; it was a value
            // in the file that means "no value", which is the shape that invites the next reader to
            // trust it.
            //
            // Absence says the same thing and cannot be misread.  It is also what arrivalFunc and
            // departureFunc below already do, for the same reason - the format had the idiom already.
            if (this.currentLoc.getPreferredSpeed() > 0)
            {
                locObj.put("speed", this.currentLoc.getPreferredSpeed());
            }
            
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

            // SORTED, so the file is the same file when nothing has changed.
            //
            // excludedLocs is a set of Locomotive objects, and Locomotive does not override hashCode -
            // so the iteration order is identity hashes, which differ on every run of the JVM. Merely
            // opening a layout and saving it therefore rewrote this array in a new order, and the
            // whole configuration file came out different with nothing changed.
            //
            // That is not a data defect - the set is the same set - but it costs three real things: a
            // sync on every launch for a layout that lives in OneDrive, a diff that says something
            // happened when nothing did, and, in this repository, a test that opens the window quietly
            // rewriting Adam's own railway.
            java.util.Collections.sort(locNames);

            jsonObj.put("excludedLocs", locNames);
        }

        if (!this.blockedBy.isEmpty())
        {
            // By NAME, because a file has to.  The other side of this boundary is Layout.parseAuto,
            // which resolves them once every point exists.
            List<String> named = new ArrayList<>();

            for (Point blocker : this.blockedBy) named.add(blocker.getName());

            jsonObj.put("blockedBy", new JSONArray(named));
        }
                
        if (this.coordinatesSet())
        {
            jsonObj.put("x", this.getX());
            jsonObj.put("y", this.getY());
        }
        
        return jsonObj;
    }
}
