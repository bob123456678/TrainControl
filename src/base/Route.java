package base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract route class
 * 
 * @author Adam
 */
abstract public class Route
{
    // Name of this route
    private final String name;
    
    // Route map
    protected LinkedHashMap<Integer, Boolean> route;
    
    // Execution state
    private boolean isExecuting = false;
    
    /**
     * Simple constructor
     * @param name 
     */
    public Route(String name)
    {
       this.name = name;
       this.route = new LinkedHashMap<>();
    }
    
    /**
     * Full constructor
     * @param name
     * @param route 
     */
    public Route(String name, LinkedHashMap<Integer, Boolean> route)
    {
        this.name = name;
        this.setRoute(route);
    }
    
    /**
     * Adds to the route
     * @param i
     * @param b 
     */
    public void addItem(Integer i, Boolean b)
    {
        this.route.put(i, b);
    }
    
    /**
     * Removes from the route
     * @param i
     */
    public void removeItem(Integer i)
    {
        this.route.remove(i);
    }
    
    /**
     * Sets a full route
     * @param route 
     */
    public final void setRoute(LinkedHashMap<Integer, Boolean> route)
    {
        this.route = route;
    }
    
    /**
     * Returns the route
     * @return 
     */
    public LinkedHashMap<Integer, Boolean> getRoute()
    {
        return this.route;
    }
    
    @Override
    public String toString()
    {
        return "Route " + this.name + "\n" + this.route.toString();
    }
    
    /**
     * Name of this route
     * @return 
     */
    public String getName()
    {
        return this.name;
    }
    
    /**
     * Marks this route as actively executing
     * @return 
     */
    synchronized public boolean setExecuting()
    {
        if (this.isExecuting)
        {
            return false;
        }
        
        this.isExecuting = true;
        
        return true;
    }
    
    /**
     * Marks this route as no longer executing
     * @return 
     */
    synchronized public boolean stopExecuting()
    {
        if (!this.isExecuting)
        {
            return false;
        }
        
        this.isExecuting = false;
        
        return true;
    }
    
    /**
     * Returns executing state
     * @return 
     */
    synchronized public boolean isExecuting()
    {
        return this.isExecuting;
    }
    
    /**
     * Returns a CSV representation of the route
     * @return 
     */
    public String toCSV()
    {
        String out = "";
        
        for (int idx : this.route.keySet())
        {
            out += Integer.toString(idx) + "," + (this.route.get(idx) ? "1" : "0") + "\n";
        }
        
        return out.trim();
    }
    
    /**
     * Returns a CSV representation of the route
     * @return 
     */
    public String toSortedCSV()
    {
        String out = "";
        
        List<Integer> keys = new ArrayList(this.route.keySet());
        Collections.sort(keys);
        
        for (int idx : keys)
        {
            out += Integer.toString(idx) + "," + (this.route.get(idx) ? "1" : "0") + "\n";
        }
        
        return out.trim();
    }
}
