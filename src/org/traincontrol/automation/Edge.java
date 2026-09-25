package org.traincontrol.automation;

import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.traincontrol.model.ViewListener;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.util.I18n;

/**
 * Graph edge - includes start and end points and how many claims are standing on it
 * @author Adam
 */
public class Edge
{
    /**
     * HOW MANY CLAIMS ARE STANDING ON THIS EDGE, not whether any is (RC-A9).
     *
     * This was a boolean, and a boolean cannot hold two claims.  Two different edges may name this one
     * as a lock edge - which is what a crossing looks like when the editor writes it - and with a
     * boolean the second claim wrote the same true as the first while the FIRST release wrote false
     * for both, freeing track a second train was still crossing.
     *
     * Every raise is matched by exactly one release.  setOccupied raises this edge and each of its
     * lock edges; setUnoccupied lowers both; the two "LockedEdge" methods are the same operations
     * without the cascade, and exist so that a lock relation cannot recurse.
     *
     * FLOORED AT ZERO rather than allowed to go negative - see release().
     */
    private int occupancy;
    /**
     * How many paths are RUNNING OVER this rail, as against holding it clear (OB-269).
     *
     * `occupancy` counts both: a path increments every edge it runs over AND every edge in each of
     * those edges' `lockEdges`, so an edge can carry a claim because of rail it merely shares.  That
     * is right for `isOccupied`, and right for the accessory protection in `getActiveAccs` - a held
     * throat's turnouts must not be thrown either way.
     *
     * It is too wide for one question, and only one: whether a CANDIDATE path may use this rail.
     * Both sides of that transaction walk `lockEdges` forwards, so a candidate sharing rail with an
     * edge that shares rail with a running path was refused while sharing nothing with the path
     * itself.  Adam's case, 2026-09-22: `BottomSecondary -> TunnelPre -> Tunnel` refused
     * `BottomInner -> BottomInnerOtherside`, naming `12,7 -> Tunnel` - eight tiles shared with the
     * run, three with the hop, and none at all between the run and the hop.
     *
     * So this counts the narrow thing, `isRunOver` asks it, and `occupancy` is left alone.
     */
    private int runOver;
    private final Point start;
    private final Point end;
    private final Map<String, Accessory.accessorySetting> configCommands;
    private int length = 0;

    /**
     * The track between the last switch on this edge and the Point it ends at (Adam, 2026-09-02).
     *
     * `Integer.MIN_VALUE` - the default - when this edge crosses no switch, which is what a
     * hand-written configuration and every pre-3.0.0 file will leave it as.  Such an edge does not
     * bound where a train may come to rest, and `Layout.measuredRoomAtTheEndOf` walks back past it.
     *
     * `-1` when it does cross one and NOTHING in the stretch after it is measured: the room is bounded
     * but unknown, and unknown is the case this guard declines to act on.
     *
     * **Not "some tile is unmeasured", which is what this said until 2026-09-08** - that was the rule
     * Adam replaced two days earlier: *"you need to measure total distance between points, not validate
     * that every edge has a length > 0.  It is only indeterminate if the entire logical segment has
     * length 0."*  An unmeasured tile now contributes nothing and the rest still counts, so a partly
     * measured stretch answers with what is known rather than throwing it away.
     *
     * Not authored anywhere.  It is derived from the diagram by `GraphReducer`, which is the only part
     * of the system that knows which tiles an edge is made of.
     */
    private int roomAtTheEnd = Integer.MIN_VALUE;

    // A list of edges that should be locked whenever this edge is locked
    // This is useful if the layout contains crossings that cannot otherwise be modeled as a graph edge
    private final List<Edge> lockEdges;
        
    /**
     * @param start
     * @param end
     */
    public Edge(Point start, Point end)
    {
        this.start = start;
        this.end = end;
        this.occupancy = 0;
        this.runOver = 0;
        this.lockEdges = new LinkedList<>();
        this.configCommands = new HashMap<>();
    }
    
    /**
     * Returns all the config commands set for this edge
     * @return 
     */
    public Map<String, Accessory.accessorySetting> getConfigCommands()
    {
        return this.configCommands;
    }
    
    /**
     * Adds a new config command
     * Excepts a call to validateConfigCommand beforehand
     * @param acc
     * @param state 
     * @return  
     */
    public Edge addConfigCommand(String acc, Accessory.accessorySetting state)
    {
        this.configCommands.put(acc.trim(), state);
        
        return this;
    }
    
    /**
     * Clears config commands
     * @param acc 
     * @return  
     */
    public Edge clearConfigCommand(String acc)
    {
        this.configCommands.remove(acc);
        
        return this;
    }
    
    /**
     * Clears all config commands
     */
    public void clearAllConfigCommands()
    {
        this.configCommands.clear();
    }
    
    /**
     * Validates that a command is valid.  Creates accessories in DB if needed.
     * @param accessory
     * @param action
     * @param control
     * @return 
     * @throws Exception 
     */
    public static Accessory.accessorySetting validateConfigCommand(String accessory, String action, ViewListener control) throws Exception
    {
        if (null == accessory || null == action)
        {
            throw new Exception(
                I18n.f("error.missingOrInvalidCommand")
            );
        }
        
        Accessory.accessorySetting output = Accessory.stringToAccessorySetting(action);
        
        if (null == output)
        {
            throw new Exception(
                I18n.f(
                    "acc.invalidAccessoryCommandMustBeOneOf",
                    action,
                    Arrays.toString(Accessory.accessorySetting.values()).toLowerCase()
                )
            );        
        }
        
        if (null == control.getAccessoryByName(accessory))
        {
            // rc expects Switch 1,state
            RouteCommand rc = RouteCommand.fromLine(accessory + "," + action, true);
            
            // Parsed data
            Integer address = rc.getAddress();
            Accessory.accessoryDecoderType protocol = rc.getProtocol();
            Accessory.accessoryType type = Accessory.stringToAccessoryType(rc.getAccessoryType());
            
            // No accessory is registered at this address - getAccessoryByName above already falls back
            // to the address and protocol encoded in the name, so a signal and a switch at the same
            // address resolve to each other.  There is therefore nothing here to conflict with: the two
            // are the same decoder, and the type only selects how it is displayed.  Note we must not
            // look the address up via getAccessoryByAddress, which would invent an accessory here.
            if (type == Accessory.accessoryType.SIGNAL)
            {
                control.newSignal(address, protocol, false);
            }
            else if (type == Accessory.accessoryType.SWITCH)
            {
                control.newSwitch(address, protocol, false);
            }
            else
            {
                throw new Exception(
                    I18n.f("autolayout.errorUnrecognizedAccessoryTypeMustBeSignalOrSwitch", accessory)
                );
            }
            
            control.logf("autolayout.warningCreatedAccessory", accessory);
        }
        
        if (null == control.getAccessoryByName(accessory))
        {
            throw new Exception(
                I18n.f("autolayout.errorAccessoryDoesNotExistOrCannotBeAdded", accessory)
            );
        }
                
        return output;
    }
        
    /**
     * Returns the name of the same edge going in the opposite direction
     * @return 
     */
    public String getOppositeName()
    {
        return getEdgeName(end, start);
    }
    
    /**
     * Returns the name of this edge
     * @return 
     */
    public String getName()
    {
        return getEdgeName(start, end);
    }
    
    /**
     * Returns the unique id of this edge
     * @return 
     */
    public String getUniqueId()
    {
        return getEdgeUniqueId(start, end);
    }
    
    @Override
    public String toString()
    {
        return getName();
    }
    
    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof Edge))
        {
            return false;
        }
        
        return this.getName().equals(((Edge) other).getName());
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.getName());
        return hash;
    }
    
    /**
     * Gets the starting point of this edge
     * @return 
     */
    public Point getStart()
    {
        return start;
    }
    
    /**
     * Gets the ending point of this edge
     * @return 
     */
    public Point getEnd()
    {
        return end;
    }
    
    /**
     * Add an edge to the list of edges which must always be locked whenever this edge is locked
     * @param e
     * @return 
     */
    public Edge addLockEdge(Edge e)
    {
        this.lockEdges.add(e);
        
        return this;
    }
    
    /**
     * Removes an edge from the lock edge list
     * @param e
     * @return 
     */
    public Edge removeLockEdge(Edge e)
    {
        this.lockEdges.remove(e);
        
        return this;
    }
    
    /**
     * Removes all edges from the lock edge list
     * @return 
     */
    public Edge clearLockEdges()
    {
        this.lockEdges.clear();
        
        return this;
    }
    
    /**
     * The side of the END point this edge comes in by, as the BUILD names it.
     *
     * **Two vocabularies used to describe one thing, and only one of them blocked track.** A Point is
     * the far end of a reduced run that may cross several tiles and turn corners, so the compass
     * direction of the NEIGHBOURING POINT - which is what `Layout.sideTowards` answers - is not the
     * side the metal actually leaves by. A rail leaving north and curving east reaches a neighbour
     * lying east: the geometry says "E" and the track says "N". On a straight they agree, which is why
     * it survived everywhere anybody looked.
     *
     * Adam met both halves of it: *"placing a train on bottommainc asks about arrival from the west or
     * the north, whereas it should be east or west"* (OB-182), and before that the same square offering
     * the wrong pair on a paste. The doors were corrected once to read the build and reverted the same
     * day - because `arrivedFrom` is CONSUMED by `edgesCoveredByStandingTrains`, which matched it
     * against the geometry, so correct labels meant nothing matched on a curve and the track behind a
     * standing train stopped being blocked at all.
     *
     * So the answer is not to pick a side but to give the model the one the builder already has. The
     * builder knows this when it emits the edge; it travels in the configuration; and the arrival
     * write, the tail walk and the operator doors all read it. One vocabulary rather than two that
     * have to agree.
     *
     * Null for a configuration written before this existed, and for one built by hand in a test. Every
     * reader falls back to the geometry when it is null, which is exactly the old behaviour - so an
     * old file keeps working and is wrong in the way it has always been wrong, rather than newly
     * blocking nothing.
     */
    private String entrySide = null;

    /**
     * @return the side of the end point this edge enters by, or null when the build did not say
     */
    public String getEntrySide()
    {
        return this.entrySide;
    }

    /**
     * @param entrySide the side of the end point this edge enters by
     */
    public void setEntrySide(String entrySide)
    {
        this.entrySide = entrySide;
    }

    /**
     * The places this edge runs over, in order from its start, and what each of them measures (OB-207).
     *
     * **Without these an edge is covered whole or not at all.** A standing train's tail is measured in
     * units and an edge knew only its total, so `Layout` could say "this edge is fouled" and never
     * "this edge is fouled as far as here" - and a one-unit train parked at the end of a twelve-tile
     * run refused every path that shared any tile of it.
     *
     * Written by the builder from `GraphReducer.placesAlong`, which is where the lock relation comes
     * from as well, so what an edge says about its own metal and what it says about its rivals cannot
     * disagree. The lengths sum to `getLength()`: the arriving square counts and the departing one does
     * not, the same convention the reducer measures by.
     *
     * Empty for a hand-written configuration, for one written before 3.0.0, and for a test graph built
     * by hand. Every reader falls back to the whole-edge answer when they are empty, which is exactly
     * what it did before - an old file is no worse off than it was.
     */
    private List<String> placeIds = Collections.emptyList();

    private List<Integer> placeLengths = Collections.emptyList();

    /**
     * Records where this edge runs, as the build worked it out.
     *
     * Ignored unless the two lists agree in size, because a reader walks them in step.
     *
     * @param ids the place identifiers, in path order
     * @param lengths what each of them measures, in the same order
     */
    public void setPlaces(List<String> ids, List<Integer> lengths)
    {
        if (ids == null || lengths == null || ids.size() != lengths.size()) return;

        this.placeIds = Collections.unmodifiableList(new ArrayList<>(ids));

        this.placeLengths = Collections.unmodifiableList(new ArrayList<>(lengths));
    }

    /**
     * @return the places this edge runs over, in path order; empty when the build did not say
     */
    public List<String> getPlaceIds()
    {
        return this.placeIds;
    }

    /**
     * @return what each of those places measures, in the same order; empty when the build did not say
     */
    public List<Integer> getPlaceLengths()
    {
        return this.placeLengths;
    }

    /**
     * The places answered 0 on purpose (Adam, 2026-09-23: *"stop listing answered zeros as missing"*).
     *
     * They measure 0 and every rule reads them as unmeasured - the walk still claims them for nothing - but a refusal
     * that counts the squares with no length leaves them out: the operator has already answered them.
     */
    private java.util.Set<String> answeredPlaces = Collections.emptySet();

    /**
     * @param ids the places answered 0 on purpose
     */
    public void setAnsweredPlaces(java.util.Collection<String> ids)
    {
        this.answeredPlaces = ids == null ? Collections.<String>emptySet()
            : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(ids));
    }

    /**
     * @param id a place identifier
     * @return whether that place's 0 was answered on purpose
     */
    public boolean isPlaceAnswered(String id)
    {
        return id != null && this.answeredPlaces.contains(id);
    }

    /**
     * The places that cut this edge into pieces - a switch, or a square two roads cross - each a piece of its own, as
     * Mass Assign Lengths asks for them (OB-297; Adam, 2026-09-24: *"Locations of switches are known."*).
     *
     * Written by the build.  Empty for a configuration built before, whose edges are then one piece each - which is what
     * the own-tail note counted by until then.
     */
    private java.util.Set<String> cutPlaces = Collections.emptySet();

    /**
     * @param ids the places that cut this edge into pieces
     */
    public void setCutPlaces(java.util.Collection<String> ids)
    {
        this.cutPlaces = ids == null ? Collections.<String>emptySet()
            : Collections.unmodifiableSet(new java.util.LinkedHashSet<>(ids));
    }

    /**
     * @param id a place identifier
     * @return whether that place cuts this edge into pieces - a switch, or a square two roads cross
     */
    public boolean isPlaceACut(String id)
    {
        return id != null && this.cutPlaces.contains(id);
    }

    /**
     * Whether this edge is measured: it has a length, or every place on it was answered 0 on purpose (Adam, 2026-09-24,
     * TDU-C6: *"0 lengths count as measures, so non-atomic should be allowed"*).
     *
     * The one question every rule that asks "is this track measured" puts to an edge - the Atomic Routes gate
     * (`Layout.unmeasuredTrackThatCouldBeReleased`), the release escape it stands for (`Layout.pathIsUnmeasured`) and the
     * route in (`Layout.measuredRouteIn`) - so the gate cannot let through a railway the escape then treats as
     * unmeasured.  Without places, which a hand-written configuration has none of, only a length says so.
     *
     * @return true when the edge is measured
     */
    public boolean isMeasured()
    {
        if (this.getLength() > 0) return true;

        if (this.placeIds.isEmpty()) return false;

        for (String id : this.placeIds)
        {
            if (!this.answeredPlaces.contains(id)) return false;
        }

        return true;
    }

    /**
     * Returns the edge length
     * @return 
     */
    public int getLength()
    {
        return length;
    }

    /**
     * @return the room after the last switch on this edge; `Integer.MIN_VALUE` when it crosses none,
     *  `-1` when it crosses one and the stretch is not fully measured
     */
    public int getRoomAtTheEnd()
    {
        return this.roomAtTheEnd;
    }

    /**
     * @return whether this edge crosses a switch, and so bounds where a train may come to rest
     */
    public boolean crossesASwitch()
    {
        return this.roomAtTheEnd != Integer.MIN_VALUE;
    }

    /**
     * Records the room measured by the reducer.
     *
     * @param roomAtTheEnd the measured stretch, `-1` for bounded-but-unmeasured
     */
    public void setRoomAtTheEnd(int roomAtTheEnd)
    {
        this.roomAtTheEnd = roomAtTheEnd;
    }

    /**
     * Sets the length of the edge.  Used in path validity calculation
     * @param length 
     */
    public void setLength(int length)
    {
        assert length >= 0;
        
        this.length = length;
    }
    
    /**
     * Gets the name of an edge based on two points
     * @param start
     * @param end 
     * @return  
     */
    public static String getEdgeName(Point start, Point end)
    {
        return start.getName() + " -> " + end.getName(); 
    }
    
    /**
     * Gets the ID of an edge based on two points
     * @param start
     * @param end
     * @return 
     */
    public static String getEdgeUniqueId(Point start, Point end)
    {
        return start.getUniqueId() + "_" + end.getUniqueId(); 
    }
    
    /**
     * Tests if the given edge is occupied by a different locomotive than the one specified
     * @param loc
     * @return 
     */
    synchronized public boolean isOccupied(Locomotive loc)
    {
        return isOccupied(loc, true);
    }

    /**
     * Whether this edge is held by another route, which is the question a LOCK edge asks.
     *
     * The reservation flag and nothing else.  A lock edge is track held clear so that two routes cannot
     * take one throat at the same time; it is not a claim on the point beyond it, and a train standing
     * at that point is not using the throat.
     *
     * That is not a judgement call, it is how the graph is built.  Reduction cuts an edge at every
     * sensor, so a Point's tile is an ENDPOINT of the edges that meet there and an intermediate step of
     * none of them - on the author's layout, 54 sensor tiles and 259 intermediate tiles with not one
     * tile in both.  A train stands on a sensor.  The track a lock edge protects is the rest of the
     * run.  They cannot be the same piece of rail.
     *
     * Nothing is given up by asking the narrow question - but NOT because locks are symmetric, which
     * is what this said and is not true. GraphReducer writes symmetric locks; GraphEdgeEdit only
     * writes one direction, and the sample layout that ships carries 104 asymmetric relations out of
     * 118 (Layout says so, and counts them). Reasoning from symmetry here would be reasoning from
     * something the data contradicts.
     *
     * What holds instead is that the SAME list is used in both directions of the transaction: locking
     * a path writes this flag onto every edge in each of its edges' lockEdges, and checking one reads
     * the flag off every edge in each of its edges' lockEdges. So an asymmetric relation - X naming Y
     * where Y does not name X - still refuses both orderings: one is caught by the write, the other by
     * the read. A train standing beyond a junction is stopped whichever direction the relation happens
     * to have been written in.
     *
     * That is not the same claim as "a route locks the edges it crosses AND checks the edges that lock
     * it", which is what this said until review pointed out there is no reverse scan anywhere - both
     * operations walk lockEdges forwards. The conclusion was right and the mechanism described was
     * not, in a comment this repository treats as load-bearing. The parked train is stopped by the lock, which is what
     * a lock is for - not by being counted as an obstruction it is not standing on.
     *
     * Left as an occupancy question, a train parked next to a junction was a permanent roadblock for
     * every route across that junction, and two such trains could deadlock with no way out for either.
     *
     * @param loc the locomotive asking, kept for symmetry with isOccupied and for future ownership
     */
    synchronized public boolean isLockHeld(Locomotive loc)
    {
        return occupancy > 0;
    }
    /**
     * Whether a path is RUNNING OVER this rail, rather than holding it clear (OB-269).
     *
     * The narrow half of `isLockHeld`, and the difference is the whole of Adam's finding: a lock edge
     * carries a claim either because a path runs over it, or because it shares rail with one that
     * does.  Refusing a candidate for the second refuses it for rail nothing is on - against his rule
     * that *"nothing should disturb the ability to go from bottominner to bottominnerotherside unless
     * trains are crossing down, or passing through either of those stations"*.
     *
     * **Asked in exactly two places, both in `isPathClear`:** of a candidate's lock edges, and of the
     * same rail in the other direction.  Everything else keeps `isLockHeld`, because a reserved
     * throat's accessories must stay protected whether the reservation is direct or shared.
     *
     * @param loc the locomotive asking, kept for symmetry with its siblings
     * @return true when some path is running over this rail
     */
    synchronized public boolean isRunOver(Locomotive loc)
    {
        return runOver > 0;
    }

    /**
     * @param wholeBlock whether a train on ANOTHER copy of the end square counts as occupying it.
     *
     * True when asking "may this train run onto that track" - the copies of a square are one piece of
     * rail and a train on either is in the way.  False asks only about the copy itself, which is what
     * a caller wants when it has already decided which copy it means.
     *
     * Neither is the question a lock edge asks - see isLockHeld.
     */
    synchronized public boolean isOccupied(Locomotive loc, boolean wholeBlock)
    {
        // Read once.  Point.isOccupied is synchronized but getCurrentLocomotive is not, so testing the
        // first and dereferencing the second let another thread clear the point in between - which threw
        // a NullPointerException inside isPathClear, on a locomotive thread
        //
        // The whole BLOCK, not just this Point: a square emitted as several copies is one piece of
        // track, and a train on the eastbound copy of a platform is on the platform.  Asking only this
        // copy let a second train be routed onto its twin, which is a collision.  On a Point with no
        // block - anything hand-written - this is exactly getCurrentLocomotive.
        Locomotive endLocomotive = wholeBlock
            ? this.end.getBlockLocomotive() : this.end.getCurrentLocomotive();

        if (endLocomotive != null && !endLocomotive.equals(loc))
        {
            return true;
        }

        return occupancy > 0;
    }

    /**
     * Gives up one claim, and never fewer than none (RC-A9).
     *
     * The floor is not defensive tidiness, it is a contract something already depends on.
     * configureAndLockPath counts an edge as taken BEFORE it takes it, so that an edge a throw leaves
     * occupied is still inside the range its recovery releases.
     *
     * **Its comment used to add "setUnoccupied on an edge that is already clear does nothing", and
     * this javadoc used to endorse that: "it still does nothing"** (`REL-C16`, `DAY-C4`). It is not
     * true here. The floor holds for THIS edge, and then `setUnoccupied` cascades
     * `setLockedEdgeUnoccupied()` to every entry in `lockEdges`, each decrementing its own count with
     * no knowledge of whether this edge was ever taken. A reader who followed the `Layout` comment to
     * its source was told the claim had been checked, in the one file that could have refuted it.
     *
     * What makes the over-release unreachable is narrower, and is written at the call site rather than
     * here: `setOccupied` increments as its first statement, so an edge counted and then thrown past
     * has already been incremented and its release is correct. Only locked siblings a mid-loop throw
     * never reached could be released untaken.
     *
     * Going negative would be worse than either: an edge released once too often would need two claims
     * before it read as occupied again, so the NEXT train to lock it would find it free.
     */
    private void release()
    {
        if (this.occupancy > 0) this.occupancy--;
    }
    
    /**
     * Same as setOccupied, but should only be called on edges in the lockEdges list (to prevent infinite recursion) 
     */
    synchronized protected void setLockedEdgeOccupied()
    {
        occupancy++;
    }
    
    /**
     * Same as setUnoccupied, but should only be called on edges in the lockEdges list (to prevent infinite recursion) 
     */
    synchronized public void setLockedEdgeUnoccupied()
    {
        release();
    }
    
    /**
     * Returns lock edges
     * @return 
     */
    public List<Edge> getLockEdges()
    {
        return this.lockEdges;
    }
    
    /**
     * Mark this edge, as well as any linked edges, as occupied
     */
    synchronized public void setOccupied()
    {
        occupancy++;

        // AND THIS ONE IS BEING RUN OVER, which the propagated claims below are not (OB-269).
        runOver++;
        
        for (Edge e : this.lockEdges)
        {
            e.setLockedEdgeOccupied();
        }
    }
    
    /**
     * Mark this edge, as well as any linked edges, as unoccupied
     */
    synchronized public void setUnoccupied()
    {
        release();

        // THE SAME FLOOR, FOR THE SAME REASON (see `release`): `configureAndLockPath` counts
        // an edge as taken BEFORE it takes it, so a release can arrive for a claim that was
        // never made - and going negative would leave the NEXT train finding occupied rail
        // free, which is the worse direction of the two.
        if (this.runOver > 0) this.runOver--;
        
        for (Edge e : this.lockEdges)
        {
            e.setLockedEdgeUnoccupied();
        }
    }
    
    /**
     * Returns a pretty textual representation of the start and end of a path
     * @param e
     * @return 
     */
    public static String pathToString(List<Edge> e)
    {
        if (e.isEmpty())
        {
            return I18n.f("autolayout.errorEmptyPath");
        }
        else if (e.get(0) == null || e.get(e.size() - 1) == null)
        {
            return I18n.f("autolayout.errorInPath");
        }
        else
        {
            return e.get(0).getStart().getName() + " -> " + e.get(e.size() - 1).getEnd().getName();
        }
    }
    
    /**
     * Converts this edge to a JSON representation
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

        List<JSONObject> lockEdgeList = new LinkedList<>();
        List<JSONObject> commandList = new LinkedList<>();
 
        for (Edge e : this.lockEdges)
        {
            JSONObject lockEdge = new JSONObject();
            lockEdge.put("start",  e.getStart().getName());
            lockEdge.put("end", e.getEnd().getName());
            lockEdgeList.add(lockEdge);
        }
        
        for (Entry<String, Accessory.accessorySetting> acc : this.configCommands.entrySet())
        {
            JSONObject command = new JSONObject();
            command.put("acc", acc.getKey());
            command.put("state", acc.getValue().toString().toLowerCase());
            commandList.add(command);
        }
        
        jsonObj.put("start", this.start.getName());
        jsonObj.put("end", this.end.getName());
        jsonObj.put("length", this.getLength());

        // Only when there is one - see the field.  An absent key means "crosses no switch", which is
        // what every hand-written and pre-3.0.0 configuration should read as.
        if (this.crossesASwitch()) jsonObj.put("roomAtTheEnd", this.getRoomAtTheEnd());

        // THE ARRIVAL SIDE, WHICH THIS METHOD DID NOT WRITE (S14-B3).
        //
        // Layout.fromJSON reads `entrySide`, AutonomyBuilder writes it when it traces a diagram, and the
        // field's own javadoc says it travels in the configuration - so the program's export was not a
        // configuration the program could reload.  Measured on the baseline configuration: 101 edges have
        // an arrival side and Export JSON then Load JSON brought back none of them, which costs
        // arrival-side reasoning - which side of a station a train comes in on, and so what counts as a
        // reversal - on any layout that has been through the export.
        //
        // Written only when there is one, like roomAtTheEnd above: an absent key means "not traced", which
        // is what a hand-written configuration and anything built before the diagram work should read as.
        if (this.entrySide != null) jsonObj.put("entrySide", this.entrySide);

        // AND THE PLACES, for the same reason S14-B3 gives just above: an export that drops them is not
        // a configuration this program can reload, and reloading without them silently returns the
        // whole-edge coverage that OB-207 is about.
        if (!this.placeIds.isEmpty())
        {
            List<JSONObject> placeList = new LinkedList<>();

            for (int i = 0; i < this.placeIds.size(); i++)
            {
                JSONObject place = new JSONObject();

                place.put("at", this.placeIds.get(i));
                place.put("length", this.placeLengths.get(i));

                if (this.answeredPlaces.contains(this.placeIds.get(i))) place.put("answered", true);

                // And where it is cut into pieces (OB-297), for the same reason: an export is a configuration.
                if (this.cutPlaces.contains(this.placeIds.get(i))) place.put("cut", true);

                placeList.add(place);
            }

            jsonObj.put("places", new JSONArray(placeList));
        }

        if (!commandList.isEmpty())
        {
            jsonObj.put("commands", new JSONArray(commandList));
        }
        
        if (!lockEdgeList.isEmpty())
        {
            jsonObj.put("lockedges", new JSONArray(lockEdgeList));
        }
        
        return jsonObj;
    }
    
    /**
     * Converts this edge to a simple JSON representation
     * @return 
     * @throws java.lang.IllegalAccessException 
     * @throws java.lang.NoSuchFieldException 
     */
    public JSONObject toSimpleJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {
        JSONObject jsonObj = new JSONObject();
        Field map = jsonObj.getClass().getDeclaredField("map");
        map.setAccessible(true);
        map.set(jsonObj, new LinkedHashMap<>());
        map.setAccessible(false);

        jsonObj.put("start", this.start.getName());
        jsonObj.put("end", this.end.getName());       
        
        return jsonObj;
    }
}
            
