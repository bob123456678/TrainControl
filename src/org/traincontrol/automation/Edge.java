package org.traincontrol.automation;

import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import java.lang.reflect.Field;
import java.util.Arrays;
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

/**
 * Graph edge - includes start and end points and occupied state
 * @author Adam
 */
public class Edge
{
    private boolean occupied;
    private final Point start;
    private final Point end;
    private final Map<String, Accessory.accessorySetting> configCommands;
    private int length = 0;

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
        this.occupied = false;
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
     * Validates that a command is valid.Creates accessories in DB if needed.
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
            throw new Exception("Missing/invalid command.");
        }
        
        Accessory.accessorySetting output = Accessory.stringToAccessorySetting(action);
        
        if (null == output)
        {
            throw new Exception("Command " + action + " must be one of: " + Arrays.toString(Accessory.accessorySetting.values()).toLowerCase());
        }
        
        if (null == control.getAccessoryByName(accessory))
        {
            // rc expects Switch 1,state
            RouteCommand rc = RouteCommand.fromLine(accessory + "," + action, true);
            
            // Parsed data
            Integer address = rc.getAddress();
            Accessory.accessoryDecoderType protocol = rc.getProtocol();
            Accessory.accessoryType type = Accessory.stringToAccessoryType(rc.getAccessoryType());
            
            Accessory existing = control.getAccessoryByAddress(address, protocol);
            
            if (existing != null && existing.getType() != type)
            {
                throw new Exception("Command " + accessory + " conflicts with a " + existing.getType().toString() + " with the same address and should be renamed.");
            }
            
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
                throw new Exception("Auto layout error: unrecognized accessory type \"" + accessory + "\". Must be Signal or Switch, e.g. \"Signal 1\" or \"Switch 2 DCC\".");
            }
            
            control.log("Auto layout warning: created " + accessory); 
        }
        
        if (null == control.getAccessoryByName(accessory))
        {
            throw new Exception("Auto layout error: Accessory " + accessory + " does not exist in the layout or cannot be added");
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
     * Returns the edge length
     * @return 
     */
    public int getLength()
    {
        return length;
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
        if (this.end.isOccupied() && !this.end.getCurrentLocomotive().equals(loc))
        {
            return true;
        }
        
        return occupied;
    }
    
    /**
     * Same as setOccupied, but should only be called on edges in the lockEdges list (to prevent infinite recursion) 
     */
    synchronized protected void setLockedEdgeOccupied()
    {
        occupied = true;    
    }
    
    /**
     * Same as setUnoccupied, but should only be called on edges in the lockEdges list (to prevent infinite recursion) 
     */
    synchronized public void setLockedEdgeUnoccupied()
    {
        occupied = false;
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
        occupied = true;
        
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
        occupied = false;
        
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
            return "Empty path";
        }
        else if (e.get(0) == null || e.get(e.size() - 1) == null)
        {
            return "Error in path";
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
            
