package automation;

import base.Accessory;
import base.Locomotive;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import model.ViewListener;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Graph edge - includes start and end points and occupied state
 * @author Adam
 */
public class Edge
{
    // private final String name;
    private boolean occupied;
    private final Point start;
    private final Point end;
    private final Map<String, String> configCommands;
    
    // A lambda function with accessory commands needed to connect this edge
    private final BiConsumer<ViewListener, Edge> configureFunc;
    
    // A list of edges that should be locked whenever this edge is locked
    // This is useful if the layout contains crossings that cannot otherwise be modeled as a graph edge
    private final List<Edge> lockEdges;
    
    /**
     * @param start
     * @param end
     * @param configureFunc lambda which will configure the edge 
     *                      (i.e., set switches and signals correctly to 
     *                       ensure the train can reach its destination)
     */
    public Edge(Point start, Point end, BiConsumer<ViewListener, Edge> configureFunc)
    {
        this.start = start;
        this.end = end;
        // this.name = getEdgeName(start, end);
        this.configureFunc = configureFunc;
        this.occupied = false;
        this.lockEdges = new LinkedList<>();
        this.configCommands = new HashMap<>();
    }
    
    /**
     * Returns all the config commands set for this edge
     * @return 
     */
    public Map<String, String> getConfigCommands()
    {
        return this.configCommands;
    }
    
    /**
     * Adds a new config command
     * @param acc
     * @param state 
     */
    public void addConfigCommand(String acc, String state)
    {
        this.configCommands.put(acc, state);
    }
    
    /**
     * Clears config commands
     * @param acc 
     */
    public void clearConfigCommand(String acc)
    {
        this.configCommands.remove(acc);
    }
    
    /**
     * Executes config commands as defined in configCommands
     * @param control1 
     */
    public void executeConfigCommands(ViewListener control1)
    {
        for (String acc : this.getConfigCommands().keySet())
        {
            String action = this.getConfigCommands().get(acc);

            if ("turn".equals(action))
            {
                control1.getAutoLayout().configure(acc, Accessory.accessorySetting.TURN);
            } 
            else if ("red".equals(action))
            {
                // == turn
                control1.getAutoLayout().configure(acc, Accessory.accessorySetting.RED);
            }
            else if ("straight".equals(action))
            {
                control1.getAutoLayout().configure(acc, Accessory.accessorySetting.STRAIGHT);
            } 
            else if ("green".equals(action))
            {
                // == straight
                control1.getAutoLayout().configure(acc, Accessory.accessorySetting.GREEN);
            } 
        }
    }
    
    /**
     * Executes the configuration function
     * @param control 
     */
    public void configure(ViewListener control)
    {
        if (configureFunc != null)
        {
            this.configureFunc.accept(control, this);
        }
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
    
    @Override
    public String toString()
    {
        return "Edge: " + getName();
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
     */
    public void addLockEdge(Edge e)
    {
        this.lockEdges.add(e);
    }
    
    /**
     * Gets the name of an edge based on two points
     * @param start
     * @param end 
     * @return  
     */
    public static String getEdgeName(Point start, Point end)
    {
        return start.getName() + "_" + end.getName(); 
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
    protected void setLockedEdgeUnoccupied()
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
        
        for (String accName : this.configCommands.keySet())
        {
            JSONObject command = new JSONObject();
            command.put("acc", accName);
            command.put("state", this.configCommands.get(accName));
            commandList.add(command);
        }
        
        jsonObj.put("start", this.start.getName());
        jsonObj.put("end", this.end.getName());
        
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
}
            
