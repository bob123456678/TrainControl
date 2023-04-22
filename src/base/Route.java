package base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
    protected List<RouteCommand> route;
    
    // Execution state
    private boolean isExecuting = false;
    
    /**
     * Simple constructor
     * @param name 
     */
    public Route(String name)
    {
       this.name = name;
       this.route = new LinkedList<>();
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
     * @param address
     * @param setting
     */
    public void addAccessory(int address, boolean setting)
    {
        this.route.add(RouteCommand.RouteCommandAccessory(address, setting));
    }
    
    /**
     * Adds to the route
     * @param rc
     */
    public void addItem(RouteCommand rc)
    {
        this.route.add(rc);
    }
    
    /**
     * Removes from the route
     * @param rc
     */
    public void removeItem(RouteCommand rc)
    {
        this.route.remove(rc);
    }
    
    /**
     * Sets a full route
     * @param route 
     */
    public final void setRoute(LinkedHashMap<Integer, Boolean> route)
    {
        this.route = new LinkedList<>();
        
        for (Integer i : route.keySet())
        {
            this.route.add(RouteCommand.RouteCommandAccessory(i, route.get(i)));
        }
    }
    
    /**
     * Returns the route
     * @return 
     */
    public LinkedHashMap<Integer, Boolean> getRoute()
    {
        LinkedHashMap routeMap = new LinkedHashMap<>();
        
        for (RouteCommand r : this.route)
        {
            routeMap.put(r.getAddress(), r.getSetting());
        }
        
        return routeMap;
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
        
        for (RouteCommand r : this.route)
        {
            out += Integer.toString(r.getAddress()) + "," + (r.getSetting() ? "1" : "0") + "\n";
        }
        
        return out.trim();
    }
}
