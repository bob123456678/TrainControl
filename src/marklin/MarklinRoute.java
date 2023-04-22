package marklin;

import base.Route;
import base.RouteCommand;
import gui.LayoutLabel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple route representation
 * 
 * @author Adam
 */
public class MarklinRoute extends Route 
    implements java.io.Serializable
{
    public static enum s88Triggers {CLEAR_THEN_OCCUPIED, OCCUPIED_THEN_CLEAR};
    
    // Control station reference
    private final MarklinControlStation network;
    
    // Internal identifier used by CS2
    private final int id;
    
    // Gui reference
    private final Set<LayoutLabel> tiles;
    
    // Extra delay between route commands
    private static final int DEFAULT_SLEEP_MS = 150;
    
    // State for routes with S88 trigger
    private boolean enabled;
    private s88Triggers triggerType;
    private int s88;
    private Map<Integer, Boolean> conditionS88s;
    
    /**
     * Simple constructor
     * @param network
     * @param name 
     * @param id 
     */
    public MarklinRoute(MarklinControlStation network, String name, int id)
    { 
        super(name);
        
        this.id = id;
        this.network = network;  
        
        this.tiles = new HashSet<>();
        
        this.s88 = 0;
        
        this.conditionS88s = new HashMap<>();

        this.enabled = false;
        this.triggerType = s88Triggers.CLEAR_THEN_OCCUPIED;
    }
    
    /**
     * Complete constructor
     * @param network
     * @param name 
     * @param id 
     * @param route 
     * @param s88 
     * @param triggerType 
     * @param enabled 
     * @param conditionS88s
     */
    public MarklinRoute(MarklinControlStation network, String name, int id, List<RouteCommand> route, int s88, s88Triggers triggerType, boolean enabled,
            Map<Integer, Boolean> conditionS88s)
    { 
        super(name, route);
        
        this.id = id;
        this.network = network;    
        
        this.tiles = new HashSet<>();
        this.s88 = s88;
        this.triggerType = triggerType;
        this.enabled = enabled;
        this.conditionS88s = conditionS88s;
        
        // Execute the automatic route
        if (this.enabled && this.hasS88())
        {
            new Thread(() -> 
            {                
                // The utility functions are defined in Locomotive, so create a dummy locomotive
                MarklinLocomotive loc = new MarklinLocomotive(this.network, 1, MarklinLocomotive.decoderType.MM2, "Dummy Loc");

                this.network.log("Route " + this.getName() + " is running...");
                
                while (this.enabled)
                {
                    if (this.triggerType == s88Triggers.CLEAR_THEN_OCCUPIED)
                    {
                        loc.waitForClearThenOccupied(this.getS88String());
                    }
                    else
                    {
                        loc.waitForOccupiedThenClear(this.getS88String());
                    }
                    
                    // Exit if the state changed
                    if (!this.enabled) return;
                    
                    // Check the condition
                    if (this.hasConditionS88())
                    {
                        boolean skip = false;
                        
                        for (int key : this.conditionS88s.keySet())
                        {
                            if(this.network.getFeedbackState(Integer.toString(key)) != this.conditionS88s.get(key))
                            {
                                skip = true;
                                break;
                            }
                        }
                        
                        if (skip)
                        {                        
                            this.network.log("Route " + this.getName() + " S88 triggered but condition failed");
                            continue;
                        }
                    }
            
                    this.network.log("Route " + this.getName() + " S88 triggered");

                    this.execRoute(); 
                }
            }).start();
        }
    }
    
    /**
     * Returns the CS2 route ID
     * @return 
     */
    public int getId()
    {
        return this.id;
    }
    
    /**
     * Refreshes tile images on all tiles in the list
     * Deletes tiles that are no longer visible (e.g., from closed windows)
     */
    public void updateTiles()
    {        
        Iterator<LayoutLabel> i = this.tiles.iterator();
        while (i.hasNext())
        {
            LayoutLabel nxtTile = i.next();
            nxtTile.updateImage();

            if (!nxtTile.isParentVisible())
            {
                i.remove();
            }
        }
    }
    
    /**
     * Adds a UI tile to be updated whenever a CS2 event fires
     * @param l 
     */
    public void addTile(LayoutLabel l)//, boolean dynamic)
    {   
        this.tiles.add(l);
    }
    
    /**
     * Executes the route
     */
    public void execRoute()
    {
        // Must be a thread for the UI to update correctly
        new Thread(() -> 
        {
            if (this.setExecuting())
            {   
                this.network.log("Executing route " + this.getName());

                // This will highlight icons in the UI
                this.updateTiles();

                for (RouteCommand rc : this.route)
                {
                    int id = rc.getAddress();
                    boolean state = rc.getSetting();
                    
                    this.network.setAccessoryState(id, state);

                    try
                    {
                        if (rc.getDelay() > MarklinRoute.DEFAULT_SLEEP_MS)
                        {
                            this.network.log("Delay for accessory " + id + " is " + rc.getDelay());
                            Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + rc.getDelay());
                        }
                        else
                        {
                            Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + MarklinRoute.DEFAULT_SLEEP_MS);
                        }    
                    } 
                    catch (InterruptedException ex)
                    {

                    }
                }

                this.stopExecuting();

                this.updateTiles();
                
                this.network.log("Executed route " + this.getName());
            }
        }).start();
    }
    
    /**
     * Sets the corresponding s88 sensor
     * @param s88 
     */
    public void setS88(int s88)
    {
        this.s88 = s88;
    }
    
    /**
     * Gets the s88 sensor to trigger the route
     * @return 
     */
    public int getS88()
    {
        return this.s88;
    }
     
    /**
     * Sets the delay for an individual item
     * @param key
     * @param delayMs 
     */
    public final void setDelay(Integer key, Integer delayMs)
    {
        for (RouteCommand rc : this.route)
        {
            if (rc.getAddress() == key)
            {
                rc.setDelay(delayMs);
                return;
            }
        }
        
        this.network.log("Error: route key " + key + " not found");
    }
    
    /**
     * Removes from the route
     * @param rc
     */
    @Override
    public void removeItem(RouteCommand rc)
    {
        this.route.remove(rc);
    }
        
    /**
     * Get the s88 sensors and require state to execute the route
     * @return 
     */
    public Map<Integer, Boolean> getConditionS88s()
    {
        return this.conditionS88s;
    }
    
    /**
     * Gets the s88 sensor as a string
     * @return 
     */
    public String getConditionS88String()
    {
        String out = "";
        
        List<Integer> keys = new ArrayList(this.conditionS88s.keySet());
        Collections.sort(keys);
        
        for (int idx : keys)
        {
            out += Integer.toString(idx) + "," + (this.conditionS88s.get(idx) ? "1" : "0") + "\n";
        }
        
        return out.trim();
    }
    
    /**
     * Gets the s88 sensor as a string
     * @return 
     */
    public String getS88String()
    {
        return Integer.toString(this.s88);
    }
    
    /**
     * Returns whether this route has an s88 sensor
     * @return 
     */
    public final boolean hasS88()
    {
        return this.s88 > 0;
    }
    
    /**
     * Returns whether this route has an s88 sensor
     * @return 
     */
    public final boolean hasConditionS88()
    {
        return this.conditionS88s.size() > 0;
    }
    
    /**
     * Enables the route
     */
    public void enable()
    {
        this.enabled = true;
    }
    
    /**
     * Disables the route
     */
    public void disable()
    {
        this.enabled = false;
    }
    
    /**
     * Returns if the automatic route is enabled
     * @return 
     */
    public boolean isEnabled()
    {
        return this.enabled;
    }
    
    /**
     * Returns the trigger type
     * @return 
     */
    public s88Triggers getTriggerType()
    {
        return this.triggerType;
    }
    
    /**
     * Set the trigger type
     * @param type
     */
    public void setTriggerType(s88Triggers type)
    {
        this.triggerType = type;
    }
    
    @Override
    public String toString()
    {
        return super.toString() + " (ID: " + this.id + " | Auto: " + (this.enabled ? "Yes": "No") + ")";
    }
    
    public String toVerboseString()
    {
        return super.toString() + " (ID: " + this.id + 
                " | S88: " + this.s88 + 
                " | Trigger Type: " + (this.triggerType == s88Triggers.CLEAR_THEN_OCCUPIED ? "CLEAR_THEN_OCCUPIED" : "OCCUPIED_THEN_CLEAR") +
                " | Auto: " + (this.enabled ? "Yes": "No") +
                ")";
    }
}
