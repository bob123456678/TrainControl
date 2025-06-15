package org.traincontrol.base;

import java.util.LinkedList;
import java.util.List;
import org.traincontrol.model.ViewListener;

/**
 * Abstract route class
 * 
 * @author Adam
 */
abstract public class Route
{    
    // Name of this route
    private final String name;
    
    // Route commands
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
    public Route(String name, List<RouteCommand> route)
    {
        this.name = name;
        this.setRoute(route);
    }
    
    /**
     * Adds to the route
     * @param address
     * @param protocol
     * @param setting
     */
    public void addAccessory(int address, Accessory.accessoryDecoderType protocol, boolean setting)
    {
        this.route.add(RouteCommand.RouteCommandAccessory(address, protocol, setting));
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
     * @param rcl 
     */
    public final void setRoute(List<RouteCommand> rcl)
    {
        this.route = rcl;
    }
    
    /**
     * Returns the route
     * @return 
     */
    public List<RouteCommand> getRoute()
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
     * This will update route commands that reference other routes to ensure the name changes are propagated
     * @param oldName
     * @param newName 
     */
    public void otherRouteRenamed(String oldName, String newName)
    {
        if (oldName != null && newName != null && !oldName.equals(newName))
        {
            for (RouteCommand rc : this.route)
            {
                // Route command references old route name
                if (rc.isRoute() && oldName.equals(rc.getName()))
                {
                    rc.setName(newName);
                }
            }
        }
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
            out += r.toLine(null);
        }
        
        return out.trim();
    }
    
    /**
     * Checks if a RouteCommand condition is satisfied
     * @param rc
     * @param control
     * @return 
     */
    public static boolean evaluate(RouteCommand rc, ViewListener control)
    {
        if (rc.isAccessory())
        {
            // TODO rc should maintain the accessory type
            return control.getAccessoryState(rc.getAddress(), rc.getProtocol()) == rc.getSetting();
        }
        else if (rc.isFeedback())
        {
            return control.getFeedbackState(Integer.toString(rc.getAddress())) == rc.getSetting();
        }
        else if (rc.isAutoLocomotive())
        {
            if (control.getAutoLayout() != null && control.getLocByName(rc.getName()) != null)
            {
                // Avoid race condition and ensure the autonomy resolution finishes first
                control.getAutoLayout().waitForS88Reached(control.getLocByName(rc.getName()), Integer.toString(rc.getAddress()));
                
                return 
                    // Last milestone if loc is active
                    Integer.toString(rc.getAddress()).equals(
                    control.getAutoLayout().getLatestMilestoneS88(
                        control.getLocByName(rc.getName())
                    )) 
                    || 
                    // Also check Loc location if route was completed
                    (
                        // This would require the locomotive to be inactive
                        // !control.getAutoLayout().getActiveLocomotives().containsKey(control.getLocByName(rc.getName()))
                        // &&
                        Integer.toString(rc.getAddress()).equals(
                            control.getAutoLayout().getLocomotiveLocation(
                                control.getLocByName(rc.getName())
                            ).getS88()
                        )
                    );
            }
            
            return false;
        }
        
        return false;
    }
}
