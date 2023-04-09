package marklin;

import base.Route;
import gui.LayoutLabel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    // TODO - make this configurable in the future
    private static final int EXTRA_SLEEP_MS = 150;
    
    // State for routes with S88 trigger
    private boolean enabled;
    private final s88Triggers triggerType;
    private int s88;
    
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
     */
    public MarklinRoute(MarklinControlStation network, String name, int id, Map<Integer, Boolean> route, int s88, s88Triggers triggerType, boolean enabled)
    { 
        super(name, route);
        
        this.id = id;
        this.network = network;    
        
        this.tiles = new HashSet<>();
        this.s88 = s88;
        this.triggerType = triggerType;
        this.enabled = enabled;
        
        // Execute the automatic route
        if (this.enabled && this.hasS88())
        {
            new Thread(() -> 
            {                
                if (this.network.getLocList().isEmpty())
                {
                    this.network.log("Route " + this.getName() + " cannot run because there are no locomotives in the DB.");
                    return;
                }
                
                MarklinLocomotive loc = this.network.getLocByName(this.network.getLocList().get(0));

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
                    
                    this.network.log("Route " + this.getName() + " S88 condition triggered");

                    this.execRoute(); 
                    
                    try {
                        Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + EXTRA_SLEEP_MS);
                    } catch (InterruptedException ex) {
                    }
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

                for (Integer id : this.route.keySet())
                {
                    Boolean state = this.route.get(id);

                    this.network.setAccessoryState(id, state);

                    try
                    {
                        Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + EXTRA_SLEEP_MS);
                    } catch (InterruptedException ex)
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
     * Gets the s88 sensor
     * @return 
     */
    public int getS88()
    {
        return this.s88;
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
