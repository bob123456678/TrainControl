package base;

import java.util.HashMap;
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
    protected Map<Integer, Boolean> route;
    
    /**
     * Simple constructor
     * @param name 
     */
    public Route(String name)
    {
       this.name = name;
       this.route = new HashMap<>();
    }
    
    /**
     * Full constructor
     * @param name
     * @param route 
     */
    public Route(String name, Map<Integer, Boolean> route)
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
     */
    public void removeItem(Integer i)
    {
        this.route.remove(i);
    }
    
    /**
     * Sets a full route
     * @param route 
     */
    public final void setRoute(Map<Integer, Boolean> route)
    {
        this.route = route;
    }
    
    /**
     * Returns the route
     * @return 
     */
    public Map<Integer, Boolean> getRoute()
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
}