package org.traincontrol.automation;

import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
import org.traincontrol.util.Util;

/**
 * Graph edge - includes start and end points and how many claims are standing on it
 * @author Adam
 */
public class Edge
{
    /**
     * How many claims are standing on this edge, not whether any is: a boolean cannot hold two claims, and the first
     * release would free track a second train is still crossing.  setOccupied raises this edge and each of its lock
     * edges and setUnoccupied lowers them; the "LockedEdge" methods do the same without the cascade, so a lock relation
     * cannot recurse.  Floored at zero (see release()).
     */
    private int occupancy;
    /**
     * How many paths are running over this rail, as against holding it clear (OB-269).  `occupancy` counts both,
     * because a path also claims every edge in each of its edges' `lockEdges`; that is right for `isOccupied` and
     * `getActiveAccs`, but too wide for whether a candidate path may use this rail, which `isRunOver` answers.
     */
    private int runOver;
    private final Point start;
    private final Point end;
    private final Map<String, Accessory.accessorySetting> configCommands;
    private int length = 0;

    /**
     * The track between the last switch on this edge and the Point it ends at.  `Integer.MIN_VALUE` (the default) when
     * the edge crosses no switch, as every hand-written or pre-3.0.0 configuration leaves it: such an edge does not
     * bound where a train may come to rest.  `-1` when it crosses one and nothing in the stretch after it is measured:
     * bounded but unknown, which this guard declines to act on.
     *
     * Adam: "you need to measure total distance between points, not validate that every edge has a length > 0.  It is
     * only indeterminate if the entire logical segment has length 0."  An unmeasured tile contributes nothing and the
     * rest still counts.  Derived from the diagram by `GraphReducer`, never authored.
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
     * The side of the END point this edge comes in by, as the build names it.  A Point may be the far end of a run that
     * turns corners, so the compass direction of the neighboring Point (`Layout.sideTowards`) is not the side the metal
     * leaves by; the arrival write, the tail walk and the operator doors all read this one vocabulary (OB-182).
     *
     * Null for a configuration written before this existed or built by hand; every reader then falls back to the
     * geometry.
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
     * The places this edge runs over, in order from its start, and what each measures (OB-207).  Without these an edge
     * is covered whole or not at all: a one-unit train parked at the end of a twelve-tile run refused every path
     * sharing any tile of it.  Written by the builder from `GraphReducer.placesAlong`; the lengths sum to
     * `getLength()`, the arriving square counting and the departing one not.  Empty for a hand-written or pre-3.0.0
     * configuration, where every reader falls back to the whole-edge answer.
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
     * The places answered 0 on purpose (Adam: *"stop listing answered zeros as missing"*).
     *
     * They measure 0 - track of no length, which a leg answered throughout is measured as (`isMeasured`) - and a refusal
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
     * What Mass Assign Lengths would ask a length for on this edge, place by place: the key of the piece, switch or
     * shared square each place lies in, where that still wants a length (OB-297).  The own-tail note counts these, so
     * it asks for exactly what the editor offers.
     *
     * Written by the build; a configuration built before has none, and says so (`knowsPiecesToMeasure`), and its edges
     * are read whole.
     */
    private java.util.Map<String, String> piecesToMeasure = Collections.emptyMap();

    /** Whether the build said what Mass Assign asks for on this edge - true even where it asks for nothing */
    private boolean knowsPiecesToMeasure = false;

    /**
     * @param marks each place still wanting a length, to the key of what Mass Assign asks for there; null for not known
     */
    public void setPiecesToMeasure(java.util.Map<String, String> marks)
    {
        this.piecesToMeasure = marks == null ? Collections.<String, String>emptyMap()
            : Collections.unmodifiableMap(new java.util.LinkedHashMap<>(marks));

        this.knowsPiecesToMeasure = marks != null;
    }

    /**
     * @return whether the build said what Mass Assign asks for on this edge
     */
    public boolean knowsPiecesToMeasure()
    {
        return this.knowsPiecesToMeasure;
    }

    /**
     * @param id a place identifier
     * @return the key of what Mass Assign asks a length for at that place, or null where it asks for nothing
     */
    public String pieceToMeasure(String id)
    {
        return id == null ? null : this.piecesToMeasure.get(id);
    }

    /**
     * Whether this edge is measured: it has a length, or every place on it was answered 0 on purpose - measured track
     * of no length.  Adam: *"0 lengths count as measures, so non-atomic should be allowed"*.
     *
     * The question every length rule asks of a leg with no length (the Atomic Routes gate, the release escape, the
     * route in, the room walk, the tail walk, the berth rule and the own-tail rule), so they must agree: an answered
     * leg is counted, adding nothing, and walked on over; a leg nobody answered still ends each of them
     * (`Layout.measuredThroughout`, OB-300).  Every place must be answered - a square nobody answered, a switch or
     * crossing on it included, leaves the leg unmeasured, as Mass Assign Lengths still asks for it.  Without places,
     * which a hand-written configuration has none of, only a length says so.
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
     *
     * "A switch" in the room rule is what bounds a berth's room (`GraphReducer.boundsTheRoom`): a switch, or a turnout
     * drawn permanent (FR-097), but not either square of a crossing made of two permanent Ys (OB-320).
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
     * Whether this edge is held by another route, which is the question a LOCK edge asks: the reservation flag and
     * nothing else.  A lock edge is track held clear so that two routes cannot take one throat at the same time; it is
     * not a claim on the point beyond it, and a train standing there is not using the throat.
     *
     * That is how the graph is built: reduction cuts an edge at every sensor, so a Point's tile is an endpoint of the
     * edges that meet there and an intermediate step of none, and a train stands on a sensor.
     *
     * Locks need not be symmetric (GraphEdgeEdit writes one direction only), and nothing here relies on it: the SAME
     * list is used in both directions of the transaction - locking a path writes this flag onto every edge in each of
     * its edges' lockEdges, and checking one reads it off every edge in each of its edges' lockEdges - so an asymmetric
     * relation still refuses both orderings.
     *
     * Counted as occupancy instead, a train parked next to a junction would block every route across it, and two such
     * trains could deadlock.
     *
     * @param loc the locomotive asking, kept for symmetry with isOccupied and for future ownership
     */
    synchronized public boolean isLockHeld(Locomotive loc)
    {
        return occupancy > 0;
    }
    /**
     * Whether a path is running over this rail, rather than holding it clear (OB-269): the narrow half of `isLockHeld`.
     * A lock edge carries a claim either because a path runs over it or because it shares rail with one that does, and
     * refusing a candidate for the second refuses it for rail nothing is on.  Adam: *"nothing should disturb the
     * ability to go from bottominner to bottominnerotherside unless trains are crossing down, or passing through either
     * of those stations"*.
     *
     * Asked in exactly two places, both in `isPathClear`: of a candidate's lock edges, and of the same rail in the
     * other direction.  Everything else keeps `isLockHeld`, because a reserved throat's accessories must stay protected
     * whether the reservation is direct or shared.
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
        // The whole block, not just this Point: a square emitted as several copies is one piece of track, so asking
        // only this copy let a second train be routed onto its twin.  On a Point with no block this is exactly
        // getCurrentLocomotive.
        Locomotive endLocomotive = wholeBlock
            ? this.end.getBlockLocomotive() : this.end.getCurrentLocomotive();

        if (endLocomotive != null && !endLocomotive.equals(loc))
        {
            return true;
        }

        return occupancy > 0;
    }

    /**
     * Gives up one claim, and never fewer than none.  The floor is a contract: configureAndLockPath counts an edge as
     * taken BEFORE it takes it, so that an edge a throw leaves occupied is still inside the range its recovery
     * releases.
     *
     * The floor holds for THIS edge only: `setUnoccupied` cascades `setLockedEdgeUnoccupied()` to every entry in
     * `lockEdges`, each decrementing its own count.  What makes an over-release unreachable is written at the call
     * site: `setOccupied` increments as its first statement, so only locked siblings a mid-loop throw never reached
     * could be released untaken.  Going negative would be worse: the next train to lock the edge would find it free.
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

        // This one is being run over; the propagated claims below are not (OB-269).
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

        // The same floor as `release`: a release can arrive for a claim that was never made, and going negative would
        // leave the next train finding occupied rail free.
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
        JSONObject jsonObj = Util.orderedJSON();

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

        // The arrival side, written only when there is one: Layout.fromJSON reads `entrySide`, so without it an export
        // is not a configuration the program can reload, and arrival-side reasoning (which side of a station a train
        // comes in on, so what counts as a reversal) is lost.  An absent key means "not traced", like roomAtTheEnd
        // above.
        if (this.entrySide != null) jsonObj.put("entrySide", this.entrySide);

        // And the places, for the same reason: reloading without them silently returns the whole-edge coverage
        // (OB-207).
        if (!this.placeIds.isEmpty())
        {
            List<JSONObject> placeList = new LinkedList<>();

            for (int i = 0; i < this.placeIds.size(); i++)
            {
                JSONObject place = new JSONObject();

                place.put("at", this.placeIds.get(i));
                place.put("length", this.placeLengths.get(i));

                if (this.answeredPlaces.contains(this.placeIds.get(i))) place.put("answered", true);

                // And what Mass Assign asks for there (OB-297), for the same reason: an export is a configuration.
                if (this.piecesToMeasure.containsKey(this.placeIds.get(i)))
                {
                    place.put("toMeasure", this.piecesToMeasure.get(this.placeIds.get(i)));
                }

                placeList.add(place);
            }

            jsonObj.put("places", new JSONArray(placeList));

            if (this.knowsPiecesToMeasure) jsonObj.put("lengthsAsked", true);
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
        JSONObject jsonObj = Util.orderedJSON();

        jsonObj.put("start", this.start.getName());
        jsonObj.put("end", this.end.getName());       
        
        return jsonObj;
    }
}
            
