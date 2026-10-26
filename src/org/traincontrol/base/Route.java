package org.traincontrol.base;

import java.util.LinkedList;
import java.util.List;
import org.traincontrol.automation.Layout;
import org.traincontrol.automation.Point;
import org.traincontrol.gui.LayoutLabel;
import org.traincontrol.model.ViewListener;

/**
 * Abstract route class
 * 
 * @author Adam
 */
abstract public class Route
{    
    public static enum s88Triggers {CLEAR_THEN_OCCUPIED, OCCUPIED_THEN_CLEAR};
    
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
     * Whether a command in this route runs the named route.
     *
     * Asked before that route is deleted, for the count the delete's question gives: `otherRouteDeleted` removes the
     * commands, and afterwards there is nothing left to count.
     *
     * @param name the route that may be deleted
     * @return true when at least one command here runs it
     */
    public boolean callsRoute(String name)
    {
        if (name == null) return false;

        for (RouteCommand rc : this.route)
        {
            if (rc != null && rc.isRoute() && name.equals(rc.getName())) return true;
        }

        return false;
    }

    /**
     * Takes every command that runs a deleted route out of this one, as deleting a locomotive removes the commands that
     * drive it (`locomotiveDeleted`): a command naming a route that is not there does nothing when this one fires, and
     * then runs whatever route is next given that name.  The wait the command carried goes with it.
     *
     * Through the iterator, on the live list, as `locomotiveDeleted` does: a route already running walks a copy taken
     * when it started, so it finishes the commands it began with.
     *
     * @param name the route that has been deleted
     * @return true when a command was removed, so the caller can say which route lost it
     */
    public boolean otherRouteDeleted(String name)
    {
        if (name == null) return false;

        boolean any = false;

        for (java.util.Iterator<RouteCommand> commands = this.route.iterator(); commands.hasNext();)
        {
            RouteCommand rc = commands.next();

            if (rc != null && rc.isRoute() && name.equals(rc.getName()))
            {
                commands.remove();

                any = true;
            }
        }

        return any;
    }

    /**
     * This will update route commands when a locomotive is renamed
     * @param oldName
     * @param newName 
     */
    public void locomotiveRenamed(String oldName, String newName)
    {
        if (oldName == null || newName == null || oldName.equals(newName)) return;

        for (RouteCommand rc : namesLocomotives())
        {
            if (oldName.equals(rc.getName())) rc.setName(newName);
        }
    }

    /**
     * Takes a deleted locomotive out of this route's COMMANDS, and reports its conditions.  A command for a locomotive
     * that cannot be resolved does nothing when the route fires, which looks exactly like a route that ran, so it goes.
     *
     * The conditions are deliberately left alone: they are combined with AND (NodeExpression.fromList), so a term
     * naming a deleted locomotive makes the whole condition false and the route stops firing, which is safe.  Removing
     * the term would make the route fire on a weaker condition than the operator wrote, and these routes throw switches
     * and signals ahead of moving trains.  The caller is told either way.
     *
     * @param name the locomotive that no longer exists
     * @return true if a CONDITION still names it, so the caller can say so - this route will not fire
     *         until somebody edits it
     */
    public boolean locomotiveDeleted(String name)
    {
        if (name == null) return false;

        for (java.util.Iterator<RouteCommand> commands = this.route.iterator(); commands.hasNext();)
        {
            RouteCommand rc = commands.next();

            if (namesALocomotive(rc) && name.equals(rc.getName())) commands.remove();
        }

        if (this.getConditions() == null) return false;

        for (RouteCommand rc : NodeExpression.toList(this.getConditions()))
        {
            if (namesALocomotive(rc) && name.equals(rc.getName())) return true;
        }

        return false;
    }

    /**
     * Every command in this route that names a locomotive, in its COMMANDS and in its CONDITIONS (which are
     * RouteCommands too: "has locomotive X reached sensor Y").  NodeExpression.toList hands back the objects
     * themselves, so changing one here changes the condition.  A renamed locomotive left in a condition silently stops
     * the route firing.
     *
     * @return the commands naming a locomotive, live
     */
    private java.util.List<RouteCommand> namesLocomotives()
    {
        java.util.List<RouteCommand> out = new java.util.ArrayList<>();

        for (RouteCommand rc : this.route)
        {
            if (namesALocomotive(rc)) out.add(rc);
        }

        if (this.getConditions() != null)
        {
            for (RouteCommand rc : NodeExpression.toList(this.getConditions()))
            {
                if (namesALocomotive(rc)) out.add(rc);
            }
        }

        return out;
    }

    /**
     * Whether this route's COMMANDS drive the named locomotive, so deleting it would take them away.  Asked before the
     * deletion, because `locomotiveDeleted` removes them silently.  Commands only: a CONDITION naming the locomotive is
     * left alone by that method and reported separately.
     *
     * @param name the locomotive that may be deleted
     * @return true when at least one command in this route drives it
     */
    public boolean commandsDrive(String name)
    {
        if (name == null) return false;

        for (RouteCommand rc : this.route)
        {
            if (namesALocomotive(rc) && name.equals(rc.getName())) return true;
        }

        return false;
    }

    private static boolean namesALocomotive(RouteCommand rc)
    {
        return rc != null && (rc.isLocomotiveSpeed() || rc.isFunction()
            || rc.isAutoLocomotive() || rc.isLocomotiveDirection());
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

            // Asked without registering: getAccessoryState creates a switch on a miss, and conditions are evaluated on
            // a timer while a route waits, so one typo made a permanent accessory.  The verdict is unchanged: an absent
            // accessory reads as not switched.
            return control.getAccessoryStateIfPresent(rc.getAddress(), rc.getProtocol())
                == rc.getSetting();
        }
        else if (rc.isFeedback())
        {
            return control.getFeedbackState(Integer.toString(rc.getAddress())) == rc.getSetting();
        }
        else if (rc.isAutoLocomotive())
        {
            // ASKED ONCE, NOT BUILT: a route's conditions are asked on its own thread
            Layout layout = control.getAutoLayoutIfLoaded();

            if (layout == null)
            {
                return false;
            }

            // NEITHER FORM HOLDS WITHOUT A TRAIN AND A RECORD TO ASK (FR-141): no autonomy above, or no such locomotive here.
            // A name with no locomotive behind it is a mistake, and "not at" must not make a mistake true
            Locomotive loc = control.getLocByName(rc.getName());

            if (loc == null)
            {
                return false;
            }

            // NOT AT is the same question with the answer turned round
            return trainStandsAt(layout, loc, Integer.toString(rc.getAddress())) == rc.isAtSensor();
        }
        
        return false;
    }

    /**
     * Whether autonomy has this train at this sensor, for `evaluate`: its last milestone, or where it stands.
     */
    private static boolean trainStandsAt(Layout layout, Locomotive loc, String s88)
    {
        // The run's bookkeeping caught up with the train first, and no longer: while the train stands on the sensor its
        // run has not yet recorded it (the gap in which a route fired by that sensor asks); a sensor it has not reached
        // never will.
        layout.waitForTheRunToRecord(loc, 3000);

        // The last milestone reached, while the locomotive is running a path
        if (s88.equals(layout.getLatestMilestoneS88(loc)))
        {
            return true;
        }

        // Otherwise where the train is now (OB-250): the start of the road it was given until it reaches a sensor, and
        // wherever it stands once a path has completed.  Not "which Point holds the train": every Point of a locked
        // path holds it, and the first in hash order could be one the train had not reached.  A locomotive not on the
        // autonomy graph is nowhere, and so stands at no sensor
        Point location = layout.whereTheTrainIs(loc);

        return location != null && s88.equals(location.getS88());
    }
    
    /**
     * Return the network ID of the route
     * @return 
     */
    abstract public int getId();
    
    abstract public boolean isEnabled();
    
    abstract public boolean isLocked();
    
    // Sensors
    abstract public void setS88(int s88);
    abstract public int getS88();
    
    abstract public s88Triggers getTriggerType();
    abstract public void setTriggerType(s88Triggers type);
    
    abstract public String getConditionCSV();
    abstract public NodeExpression getConditions();
    abstract public boolean hasS88();
    
    abstract public void execRoute(boolean auto);
    abstract public void addTile(LayoutLabel l);
    abstract public boolean hasTiles();
}
