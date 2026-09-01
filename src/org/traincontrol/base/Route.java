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
     * Takes a deleted locomotive out of this route's COMMANDS, and reports its conditions.
     *
     * Renaming has always been followed into a route and deleting never was, so a route went on naming
     * a locomotive that is not in the database - and what that does is quiet.  A command for a
     * locomotive that cannot be resolved does nothing when the route fires, and a route is a list of
     * commands: one of them silently not applying looks exactly like a route that ran.  So the command
     * is removed: it cannot do anything, and leaving it keeps the route looking complete while it is
     * not.
     *
     * **The conditions are deliberately left alone, and an earlier version of this comment was wrong
     * to say otherwise.**  It reasoned that a condition which can never be true is a route that can
     * never run, and should go the same way.  That is true and the remedy is worse than the fault.
     * Conditions are combined with AND - see NodeExpression.fromList - so a term naming a deleted
     * locomotive makes the whole condition false, and the route stops firing.  Removing the term does
     * not restore the route the operator had: it creates a route that fires on what is LEFT, which is a
     * weaker condition than they ever wrote.  These routes throw switches and signals ahead of moving
     * trains, so a route that has quietly stopped firing is safe and a route that quietly starts firing
     * on a condition nobody agreed to is not.
     *
     * A term inside an OR could be dropped safely, since false is its identity there - but a rule that
     * depends on where in the tree a term sits is one nobody can predict from the outside, and the
     * caller is told either way.
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
     * Every command in this route that names a locomotive - in its COMMANDS and in its CONDITIONS.
     *
     * The conditions were missed for as long as this rule has existed.  They are RouteCommands too -
     * "has locomotive X reached sensor Y" is one - and NodeExpression.toList hands back the objects
     * themselves rather than copies, so changing one here changes the condition.
     *
     * A renamed locomotive left in a condition is the worst of the shapes this can take: the condition
     * cannot be satisfied, so the route stops firing, and nothing anywhere says that a rename did it.
     *
     * @return the commands naming a locomotive, live
     */
    /**
     * Whether this route's COMMANDS drive the named locomotive, so deleting it would take them away.
     *
     * Asked before the deletion rather than after, because `locomotiveDeleted` removes them silently
     * and there is then nothing left to count.  Commands only: a CONDITION naming the locomotive is
     * left alone by that method and reported separately, so counting it here would say a route is about
     * to lose something it keeps.
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
            return control.getAccessoryState(rc.getAddress(), rc.getProtocol()) == rc.getSetting();
        }
        else if (rc.isFeedback())
        {
            return control.getFeedbackState(Integer.toString(rc.getAddress())) == rc.getSetting();
        }
        else if (rc.isAutoLocomotive())
        {
            if (!control.hasAutoLayout())
            {
                return false;
            }

            Locomotive loc = control.getLocByName(rc.getName());

            if (loc == null)
            {
                return false;
            }

            Layout layout = control.getAutoLayout();
            String s88 = Integer.toString(rc.getAddress());

            // Avoid a race condition and ensure the autonomy resolution finishes first
            layout.waitForS88Reached(loc, s88);

            // The last milestone reached, while the locomotive is running a path
            if (s88.equals(layout.getLatestMilestoneS88(loc)))
            {
                return true;
            }

            // Otherwise wherever the locomotive is standing, for a path that has completed.  A
            // locomotive that is not on the autonomy graph at all has no location, and so simply does
            // not satisfy this condition
            Point location = layout.getLocomotiveLocation(loc);

            return location != null && s88.equals(location.getS88());
        }
        
        return false;
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
