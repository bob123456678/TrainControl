package org.traincontrol.marklin;

import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutLabel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.NodeExpression;

/**
 * Simple route representation
 * 
 * @author Adam
 */
public class MarklinRoute extends Route 
    implements java.io.Serializable
{    
    // Control station reference
    private final MarklinControlStation network;
    
    // Internal identifier used by CS2
    private int id;
    
    // Gui reference
    // ConcurrentHashMap-backed set: addTile is called from the EDT as track diagram windows open,
    // while updateTiles iterates and prunes from a Central Station message thread.  A plain HashSet
    // threw ConcurrentModificationException there, silently killing the thread mid-refresh.
    private final Set<LayoutLabel> tiles;
    
    // Extra delay between route commands
    private static final int DEFAULT_SLEEP_MS = 150;
    
    // State for routes with S88 trigger
    // volatile: the monitor thread started below loops on this field, while enable() and disable() are
    // called from the EDT.  Without it that thread has no guarantee of ever observing a disable, and
    // deleteRoute depends on exactly that to retire the monitor of a route being edited or removed -
    // an unretired monitor keeps firing the old command list on a route the UI can no longer reach.
    private volatile boolean enabled;
    private s88Triggers triggerType;
    private int s88;
    private NodeExpression conditions;
    
    // Controls if the route can be edited
    private boolean locked = false;

    // The thread watching this route's s88 sensor, when one is running.  Transient because a Thread is
    // not serializable, and it is pure runtime state.
    private transient Thread monitorThread;
    
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
        
        this.tiles = java.util.concurrent.ConcurrentHashMap.newKeySet();
        
        this.s88 = 0;
        
        this.conditions = null;
        
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
     * @param conditions
     */
    public MarklinRoute(MarklinControlStation network, String name, int id, List<RouteCommand> route, int s88, s88Triggers triggerType, boolean enabled,
            NodeExpression conditions)
    { 
        super(name, route);
        
        this.id = id;
        this.network = network;    
        
        this.tiles = java.util.concurrent.ConcurrentHashMap.newKeySet();
        this.s88 = s88;

        // Never left null: the monitor tests "== CLEAR_THEN_OCCUPIED" and a null would silently mean
        // occupied-then-clear instead of the documented default
        this.triggerType = triggerType != null ? triggerType : s88Triggers.CLEAR_THEN_OCCUPIED;
        this.enabled = enabled;
        
        this.conditions = conditions;
                
        // Starts the execution of the automated route
        this.executeAutoRoute();
    }

    @Override
    public boolean isLocked()
    {
        return locked;
    }

    public void setLocked(boolean locked)
    {
        this.locked = locked;
    }
    
    /**
     * Monitors the route conditions and executes the route when appropriate
     * @return 
     */
    public final synchronized boolean executeAutoRoute()
    {
        // Execute the automatic route
        if (this.enabled && this.hasS88())
        {
            // disable() only clears the enabled flag - the monitor thread stays parked in its feedback
            // wait until the sensor next fires, and only then notices and exits.  Without this guard a
            // disable/enable cycle in the meantime starts a second monitor, and the route then fires
            // once per monitor on every trigger.  applyAutonomyRouteActivations does exactly that when
            // one autonomy configuration omits the route and the next one includes it.
            if (this.monitorThread != null && this.monitorThread.isAlive())
            {
                return false;
            }

            this.monitorThread = new Thread(() ->
            {
                // The utility functions are defined in Locomotive, so create a dummy locomotive
                MarklinLocomotive loc = new MarklinLocomotive(this.network, 1, MarklinLocomotive.decoderType.MM2, "Dummy Loc");

                this.network.logf(
                    "route.running",
                    this.getName()
                );          
                
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

                    // Anything that goes wrong here must not end the loop.  This runs on a bare thread
                    // with no handler, so an escaping exception would silently stop this route from
                    // watching its sensor for the rest of the session, while it still reports itself
                    // as enabled.  Log it and wait for the next trigger instead.
                    try
                    {
                        // Check the condition
                        if (this.hasConditions() && !this.conditions.evaluate(network))
                        {
                            this.network.logf(
                                "route.s88ConditionFailed",
                                this.getName()
                            );
                            continue;
                        }

                        this.network.logf(
                            "route.s88Triggered",
                            this.getName()
                        );

                        this.execRoute(true);
                    }
                    catch (Exception e)
                    {
                        // Not "condition failed": this catch also covers execRoute, so the failure may
                        // have nothing to do with the conditions.  It exists so that no exception can
                        // silently end the monitor thread - naming the wrong cause defeats the point of
                        // logging it.
                        this.network.logf(
                            "route.s88MonitorFailed",
                            this.getName()
                        );

                        this.network.log(e);
                    }
                }
            });

            this.monitorThread.start();

            return true;
        }

        return false;
    }
        
    /**
     * Returns the CS2 route ID
     * @return 
     */
    @Override
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
            nxtTile.updateImage(false);

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
    @Override
    public void addTile(LayoutLabel l)//, boolean dynamic)
    {   
        this.tiles.add(l);
    }
    
    /**
     * Wrapper for a standard execution call
     * @param auto 
     */
    @Override
    public void execRoute(boolean auto)
    {
        execRoute(auto, 1);
    }
    
    /**
     * Executes the route
     * @param auto - was the route triggered automatically?
     * @param recursionLimit - the maximum number of other routes that can be triggered from this route
     */
    private void execRoute(boolean auto, int recursionLimit)
    {
        if (recursionLimit < 0)
        {
            this.network.logf(
                "route.recursionLimitReached",
                this.getName()
            );
            return;
        }
        
        // Must be a thread for the UI to update correctly
        new Thread(() -> 
        {
            if (this.setExecuting())
            {   
                this.network.logf(
                    "route.executing",
                    this.getName()
                );
                
                // This will highlight icons in the UI
                this.updateTiles();

                // try/finally so that stopExecuting always runs.  setExecuting is the re-entrancy
                // guard: if a command threw, the flag stayed set and every later attempt to run this
                // route silently returned false for the rest of the session.
                try
                {
                    for (RouteCommand rc : this.route)
                    {
                        if (rc != null)
                        {
                            if (rc.isAccessory())
                            {
                                int idd = rc.getAddress();
                                boolean state = rc.getSetting();

                                this.network.setAccessoryState(idd, rc.getProtocol(), state);
                            }
                            else if (rc.isStop())
                            {                        
                                // Only send stop command once
                                if (this.network.getPowerState())
                                {
                                    this.network.logf(
                                        "route.powerTurnedOffCondition",
                                        this.getName()
                                    );
                                    this.network.stop();
                                
                                    if (auto && this.network.getGUI() != null)
                                    {
                                        this.network.getGUI().emergencyStopTriggered(this);
                                    }
                                }
                                else
                                {
                                    this.network.logf(
                                        "route.conditionFiredPowerAlreadyOff",
                                        this.getName()
                                    );
                                }
                            }
                            else if (rc.isFunctionsOff())
                            {
                                this.network.logf(
                                    "route.turningOffFunctions",
                                    this.getName()
                                );
                            
                                this.network.allFunctionsOff();
                            }
                            else if (rc.isAutonomyLightsOn())
                            {
                                if (this.network.hasAutoLayout())
                                {
                                    this.network.logf(
                                        "route.turningOnAutonomyLights",
                                        this.getName()
                                    );

                                    this.network.lightsOn(this.network.getAutoLayout().getLocomotivesToRun().stream().map(Locomotive::getName).collect(Collectors.toList()));
                                }
                            }
                            else if (rc.isLightsOn())
                            {
                                this.network.logf(
                                    "route.turningOnAllLights",
                                    this.getName()
                                );

                                this.network.lightsOn(this.network.getLocList());  
                            }
                            else if (rc.isLocomotiveSpeed())
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
                                    this.network.logf(
                                        "route.warningLocomotiveNotExist",
                                        rc.getName()
                                    );
                                }
                            }
                            else if (rc.isLocomotiveDirection())
                            {
                                MarklinLocomotive loc = this.network.getLocByName(rc.getName());
                            
                                if (loc != null)
                                {
                                    loc.setDirection(rc.getDirection());
                                }
                                else
                                {
                                    this.network.logf(
                                        "route.warningLocomotiveNotExist",
                                        rc.getName()
                                    );
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
                                    this.network.logf(
                                        "route.warningLocomotiveNotExistCalledFrom",
                                        rc.getName(),
                                        this.getName()
                                    );
                                }
                            }
                            else if (rc.isRoute())
                            {
                                MarklinRoute r = this.network.getRoute(rc.getName());
                            
                                if (r == this)
                                {
                                    this.network.logf(
                                        "route.warningCommandSelfReference",
                                        rc.getName()
                                    );
                                }
                                else
                                {                      
                                    if (r != null)
                                    {
                                        if (!this.equals(r))
                                        {
                                             // We allow the route to recurse at most once
                                            r.execRoute(false, recursionLimit - 1);
                                        }
                                        else
                                        {
                                            this.network.logf(
                                                "route.warningCannotInvokeSelf",
                                                rc.getName()
                                            );
                                        }
                                    }
                                    else
                                    {
                                        this.network.logf(
                                            "route.warningRouteNotExistCalledFrom",
                                            rc.getName(),
                                            this.getName()
                                        );
                                    }
                                }
                            }

                            try
                            {
                                if (rc.getDelay() > MarklinRoute.DEFAULT_SLEEP_MS)
                                {
                                    this.network.logf(
                                        "route.delay",
                                        rc.getDelay()
                                    );                                
                                    Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + rc.getDelay());
                                }
                                else
                                {
                                    Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + MarklinRoute.DEFAULT_SLEEP_MS);
                                }    
                            } 
                            catch (InterruptedException ex)
                            {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }

                    this.network.logf(
                        "route.executed",
                        this.getName()
                    );
                }
                finally
                {
                    this.stopExecuting();

                    this.updateTiles();
                }
            }
        }).start();
    }
    
    /**
     * Adds a s88 condition to the route - legacy - all conditions will be ANDed
     * @param id
     * @param state 
     */
    public final void addConditionS88(Integer id, boolean state)
    {
        List<RouteCommand> routeConditions = NodeExpression.toList(conditions);
        
        routeConditions.add(RouteCommand.RouteCommandFeedback(id, state));
        
        conditions = NodeExpression.fromList(routeConditions);
    }
    
    /**
     * Adds an accessory condition to the route - legacy - all conditions will be ANDed
     * @param address
     * @param protocol
     * @param setting
     */
    public final void addConditionAccessory(int address, Accessory.accessoryDecoderType protocol, boolean setting)
    {
        List<RouteCommand> routeConditions = NodeExpression.toList(conditions);
        
        routeConditions.add(RouteCommand.RouteCommandAccessory(address, protocol, setting));
        
        conditions = NodeExpression.fromList(routeConditions);
    }
    
    /**
     * Sets the corresponding s88 sensor
     * @param s88 
     */
    @Override
    public void setS88(int s88)
    {
        this.s88 = s88;
    }
        
    /**
     * Gets the s88 sensor to trigger the route
     * @return 
     */
    @Override
    public int getS88()
    {
        return this.s88;
    }
     
    /**
     * Sets the delay for an individual item, identified by its address
     * @param key
     * @param delayMs
     */
    public final void setDelay(Integer key, Integer delayMs)
    {
        for (RouteCommand rc : this.route)
        {
            // Locomotive, function and route commands carry no address.  Skipping them matters:
            // calling getAddress() on one throws, and in the CS3 importer that exception is caught
            // per route, silently dropping any route that sets a speed before a delayed accessory.
            if (rc.hasAddress() && rc.getAddress() == key)
            {
                rc.setDelay(delayMs);
                return;
            }
        }

        this.network.logf("route.keyNotFound", key);
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
    @Override
    public final boolean hasS88()
    {
        return this.s88 > 0;
    }
        
    public final boolean hasConditions()
    {
        return this.conditions != null;
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
    @Override
    public boolean isEnabled()
    {
        return this.enabled;
    }
    
    /**
     * Returns the trigger type
     * @return 
     */
    @Override
    public s88Triggers getTriggerType()
    {
        return this.triggerType;
    }
    
    /**
     * Set the trigger type
     * @param type
     */
    @Override
    public void setTriggerType(s88Triggers type)
    {
        this.triggerType = type;
    }

    public void setId(int id)
    {
        this.id = id;
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
                " | Conditions: " + this.getConditionCSV() + 
                ")";
    }
    
    public JSONObject toJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, Exception
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
        
        if (this.hasConditions())
        {
            jsonObj.put("conditions", this.conditions.toJSON());
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

        // Defaults to CLEAR_THEN_OCCUPIED, the same default the simple constructor documents.  This was
        // left null when the key was absent, and the monitor's "== CLEAR_THEN_OCCUPIED" test then fell
        // through to waiting for occupied-then-clear - so a route imported from an autonomy file that
        // omits the key triggered on the opposite sensor edge from the one it should have.
        MarklinRoute.s88Triggers triggerType = MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED;

        if (jsonObject.has("triggerType"))
        {
            triggerType = MarklinRoute.s88Triggers.valueOf(jsonObject.getString("triggerType"));
        }
        
        NodeExpression conditionExpression = null;

        if (jsonObject.has("conditions"))
        {
            conditionExpression = NodeExpression.fromJSON(jsonObject.getJSONObject("conditions"));
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

        return new MarklinRoute(network, name, id, routeCommands, s88, triggerType, enabled, conditionExpression);
    }
    
    /**
     * Gets the route condition expression
     * @return 
     */
    @Override
    public NodeExpression getConditions()
    {
        return this.conditions;
    }
    
    /**
     * Returns a CSV representation of the route's condition accessories
     * @return 
     */
    @Override
    public String getConditionCSV()
    {
        StringBuilder out = new StringBuilder();
        String expression = NodeExpression.toTextRepresentation(this.conditions, network);
        out.append(expression);
        return out.toString().trim();
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
                // Pass through the accessory so we can pretty print its type.
                //
                // The non-creating lookup: this is a display path - it renders the route for the editor
                // and for export - and the creating one registered a phantom accessory for every
                // address in the route that had none yet, simply because someone opened the editor.
                // toLine already falls back to the plain "address,setting" form when this is null.
                out += r.toLine(network.getAccessoryByAddressIfPresent(r.getAddress(), r.getProtocol()));
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
                Objects.equals(this.getConditions(), other.getConditions())
                && this.getRoute().equals(other.getRoute());
    }
    
    /**
     * Checks if this route has any layout tiles
     * @return 
     */
    @Override
    public boolean hasTiles()
    {
        return !this.tiles.isEmpty();
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
                Objects.equals(this.getConditions(), other.getConditions())
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
        hash = 53 * hash + Objects.hashCode(this.conditions);
        return hash;
    }
}