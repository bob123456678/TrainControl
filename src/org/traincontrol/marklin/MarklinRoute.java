package org.traincontrol.marklin;

import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutLabel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

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
    private int id;
    
    // Gui reference
    private final Set<LayoutLabel> tiles;
    
    // Extra delay between route commands
    private static final int DEFAULT_SLEEP_MS = 150;
    
    // State for routes with S88 trigger
    private boolean enabled;
    private s88Triggers triggerType;
    private int s88;
    private final Map<Integer, Boolean> conditionS88s;
    private final List<RouteCommand> conditionAccessories;
    
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
        this.conditionAccessories = new ArrayList<>();

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
     * @param conditionAccessories
     */
    public MarklinRoute(MarklinControlStation network, String name, int id, List<RouteCommand> route, int s88, s88Triggers triggerType, boolean enabled,
            Map<Integer, Boolean> conditionS88s, List<RouteCommand> conditionAccessories)
    { 
        super(name, route);
        
        this.id = id;
        this.network = network;    
        
        this.tiles = new HashSet<>();
        this.s88 = s88;
        this.triggerType = triggerType;
        this.enabled = enabled;
        
        if (conditionS88s != null)
        {
            this.conditionS88s = conditionS88s;
        }
        else
        {
            this.conditionS88s = new HashMap<>();
        }
        
        if (conditionAccessories != null)
        {
            this.conditionAccessories = conditionAccessories;
        }
        else
        {
            this.conditionAccessories = new ArrayList<>();
        }
        
        // Starts the execution of the automated route
        this.executeAutoRoute();
    }
    
    /**
     * Monitors the route conditions and executes the route when appropriate
     * @return 
     */
    public final boolean executeAutoRoute()
    {
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
                            if (this.network.getFeedbackState(Integer.toString(key)) != this.conditionS88s.get(key))
                            {
                                skip = true;
                                break;
                            }
                        }
                        
                        for (RouteCommand rc : this.conditionAccessories)
                        {
                            if (this.network.getAccessoryState(rc.getAddress()) != rc.getSetting())
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
            
            return true;
        }
        
        return false;
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

    public List<RouteCommand> getConditionAccessories()
    {
        return conditionAccessories;
    }
    
    /**
     * Adds an accessory condition to the route
     * @param address
     * @param setting
     */
    public void addConditionAccessory(int address, boolean setting)
    {
        this.conditionAccessories.add(RouteCommand.RouteCommandAccessory(address, setting));
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
                    if (rc != null)
                    {
                        if (rc.isAccessory())
                        {
                            int idd = rc.getAddress();
                            boolean state = rc.getSetting();

                            this.network.setAccessoryState(idd, state);
                        }
                        else if (rc.isStop())
                        {                        
                            // Only send stop command once
                            if (this.network.getPowerState())
                            {
                                this.network.log("Power turned off due to route condition");
                                this.network.stop();
                            }
                            else
                            {
                                this.network.log("Route condition fired but power was already off");
                            }
                        }
                        else if (rc.isFunctionsOff())
                        {
                            this.network.log("Route turning off all functions.");
                            
                            this.network.allFunctionsOff();
                        }
                        else if (rc.isAutonomyLightsOn())
                        {
                            if (this.network.getAutoLayout() != null)
                            {
                                this.network.log("Route turning on lights of autonomy locomotives.");

                                this.network.lightsOn(this.network.getAutoLayout().getLocomotivesToRun().stream().map(Locomotive::getName).collect(Collectors.toList()));
                            }
                        }
                        else if (rc.isLightsOn())
                        {
                            this.network.log("Route turning on lights of all locomotives.");

                            this.network.lightsOn(this.network.getLocList());  
                        }
                        else if (rc.isLocomotive())
                        {
                            MarklinLocomotive loc = this.network.getLocByName(rc.getName());
                            
                            if (loc != null)
                            {
                                if (rc.getSpeed() < 0)
                                {
                                    loc.instantStop();
                                }
                                else
                                {
                                    loc.setSpeed(rc.getSpeed());
                                }
                            }
                            else
                            {
                                this.network.log(("Route warning: locomotive " + rc.getName() + " does not exist"));
                            }
                        }
                        else if (rc.isFunction())
                        {
                            MarklinLocomotive loc = this.network.getLocByName(rc.getName());
                            
                            if (loc != null)
                            {
                                loc.setF(rc.getFunction(), rc.getSetting());
                            }
                            else
                            {
                                this.network.log(("Route warning: locomotive " + rc.getName() + " does not exist"));
                            }
                        }

                        try
                        {
                            if (rc.getDelay() > MarklinRoute.DEFAULT_SLEEP_MS)
                            {
                                this.network.log("Route delay " + rc.getDelay() + "ms");
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
     * Adds a s88 condition to the route
     * @param id
     * @param state 
     */
    public void addConditionS88(Integer id, boolean state)
    {
        this.conditionS88s.put(id, state);
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
     * Returns whether this route has s88 conditions
     * @return 
     */
    public final boolean hasConditionS88()
    {
        return !this.conditionS88s.isEmpty();
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

    public void setId(int id)
    {
        this.id = id;
    }
    
    public boolean hasConditionAccessories()
    {
        return !this.conditionAccessories.isEmpty();
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
                " | Condition S88: " + this.conditionS88s +
                " | Condition Accessories: " + this.getConditionAccessoryCSV() + 
                ")";
    }
    
    public JSONObject toJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {		
        JSONObject jsonObj = new JSONObject();
        Field map = jsonObj.getClass().getDeclaredField("map");
        map.setAccessible(true);
        map.set(jsonObj, new LinkedHashMap<>());
        map.setAccessible(false);
        
        jsonObj.put("name", this.getName());
        jsonObj.put("id", this.getId());
        
        if (this.hasS88())
        {
            jsonObj.put("s88", this.getS88());
        }
        
        if (this.hasConditionS88())
        {
            jsonObj.put("conditionS88", this.getConditionS88String());
        }
        
        jsonObj.put("auto", this.enabled);
        
        if (this.triggerType != null)
        {
            jsonObj.put("triggerType", this.triggerType.toString());
        }
        
        // Use simple representation for now
        JSONArray configObj = new JSONArray();

        for (RouteCommand rc : this.getRoute())
        {
            configObj.put(rc.toJSON());
        }
                    
        jsonObj.put("commands", configObj);
        
        if (this.hasConditionAccessories())
        {
            JSONArray conditionAcc = new JSONArray();

            for (RouteCommand rc : this.conditionAccessories)
            {
                conditionAcc.put(rc.toJSON());
            }

            jsonObj.put("conditionAcc", conditionAcc);
        }
  
        return jsonObj;
    }
    
     /**
     * Create a MarklinRoute object from JSON
     * @param jsonObject The JSON representation of MarklinRoute
     * @param network
     * @return MarklinRoute object
     */
    public static MarklinRoute fromJSON(JSONObject jsonObject, MarklinControlStation network)
    {
        String name = jsonObject.getString("name");
        int id = Math.abs(jsonObject.getInt("id"));
        int s88 = Math.abs(jsonObject.optInt("s88", 0));
        boolean enabled = jsonObject.getBoolean("auto");

        MarklinRoute.s88Triggers triggerType = null;
        if (jsonObject.has("triggerType"))
        {
            triggerType = MarklinRoute.s88Triggers.valueOf(jsonObject.getString("triggerType"));
        }

        Map<Integer, Boolean> conditionS88s = new HashMap<>();
        
        if (jsonObject.has("conditionS88"))
        {
            String conditionS88String = jsonObject.getString("conditionS88");
            String[] conditionS88Pairs = conditionS88String.split("\n");
            for (String pair : conditionS88Pairs)
            {
                String[] parts = pair.split(",");
                conditionS88s.put(Math.abs(Integer.parseInt(parts[0])), parts[1].equals("1"));
            }
        }

        List<RouteCommand> routeCommands = new ArrayList<>();
        if (jsonObject.has("commands"))
        {
            JSONArray commandsArray = jsonObject.getJSONArray("commands");
            for (int i = 0; i < commandsArray.length(); i++)
            {
                routeCommands.add(RouteCommand.fromJSON(commandsArray.getJSONObject(i)));
            }
        }
        
        List<RouteCommand> conditionAcc = new ArrayList<>();
        if (jsonObject.has("conditionAcc"))
        {
            JSONArray commandsArray = jsonObject.getJSONArray("conditionAcc");
            for (int i = 0; i < commandsArray.length(); i++)
            {
                conditionAcc.add(RouteCommand.fromJSON(commandsArray.getJSONObject(i)));
            }
        }

        return new MarklinRoute(network, name, id, routeCommands, s88, triggerType, enabled, conditionS88s, conditionAcc);
    }
    
    /**
     * Returns a CSV representation of the route
     * @return 
     */
    public String getConditionAccessoryCSV()
    {
        String out = "";
        
        for (RouteCommand r : this.conditionAccessories)
        {
            // TODO others not yet supported
            if (r.isAccessory())
            {
                // out += Integer.toString(r.getAddress()) + "," + (r.getSetting() ? "1" : "0") + "\n";
                out += r.toLine(network.getAccessoryByAddress(r.getAddress()));
            }
        }
        
        return out.trim();
    }
    
    @Override
    /**
     * Returns a CSV representation of the route
     * @return 
     */
    public String toCSV()
    {
        String out = "";
        
        for (RouteCommand r : this.route)
        {
            if (r.isAccessory())
            {
                // Pass through the accessory so we can pretty print its type
                out += r.toLine(network.getAccessoryByAddress(r.getAddress()));
            }
            else
            {
                out += r.toLine(null);
            }
        }
        
        return out.trim();
    }
    
    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        MarklinRoute other = (MarklinRoute) o;
        return id == other.id &&
                s88 == other.s88 &&
                enabled == other.enabled &&
                triggerType == other.triggerType &&
                this.conditionS88s.equals(other.getConditionS88s())
                && this.conditionAccessories.equals(other.getConditionAccessories())
                && this.getRoute().equals(other.getRoute());
    }
    
    /**
     * Checks routes for equality, but does not care about the sequence of route commands
     * @param o
     * @return 
     */
    public boolean equalsUnordered(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        MarklinRoute other = (MarklinRoute) o;
        return id == other.id &&
                s88 == other.s88 &&
                enabled == other.enabled &&
                triggerType == other.triggerType &&
                this.conditionS88s.equals(other.getConditionS88s())
                && this.conditionAccessories.equals(other.getConditionAccessories())
                && new HashSet(this.getRoute()).equals(new HashSet(other.getRoute()));
    }

    @Override
    public int hashCode()
    {
        int hash = 5;
        hash = 53 * hash + this.id;
        hash = 53 * hash + (this.enabled ? 1 : 0);
        hash = 53 * hash + Objects.hashCode(this.triggerType);
        hash = 53 * hash + this.s88;
        hash = 53 * hash + Objects.hashCode(this.conditionS88s);
        return hash;
    }
}
