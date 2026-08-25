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

    // What a route command's delay must be for the *next* command to land THREEWAY_DELAY_MS later -
    // the gap the track diagram already leaves between a three-way's two commands.  execRoute sleeps
    // SLEEP_INTERVAL plus the command's own delay, hence the subtraction.
    //
    // Used by the two builders that can space a pair: the CS3 route importer and the route editor's
    // wizard.  Two others cannot.  The CS2 flat-file importer has no way to tell a three-way from a
    // plain turnout - parseMags keeps only SWITCH or SIGNAL, discarding the "dreiwegweiche" the file
    // states - and command capture records what the diagram sent, carrying no delay of its own.
    // Without it a pair fires DEFAULT_SLEEP_MS apart, inside the margin the diagram path was tuned to.
    //
    // Has to exceed DEFAULT_SLEEP_MS to have any effect: execRoute honours a command's own delay
    // only when it is the larger of the two.  It does, with room to spare - but lowering
    // THREEWAY_DELAY_MS far enough would make this silently inert rather than merely shorter.
    public static final int THREEWAY_ROUTE_DELAY_MS =
        (int) (MarklinAccessory.THREEWAY_DELAY_MS - MarklinControlStation.SLEEP_INTERVAL);
    
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
        
        // Every door that builds conditions passes through here - text (already right-nested, a
        // no-op), hand-written JSON (normalized again, idempotent), and the locomotive database,
        // which Java-serializes the tree and restores it WITHOUT running any parser: a bare
        // cross-operator tree imported before normalization existed would come back through that
        // door unrepaired forever.
        this.conditions = NodeExpression.normalize(conditions);
                
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
        // The labels this one replaces go now.  See LayoutLabel.forgetReplaced: nothing else can drop
        // them on the main window, so without this every rebuilt page stayed registered for ever.
        LayoutLabel.forgetReplaced(this.tiles, l);

        this.tiles.add(l);
    }
    
    /**
     * Wrapper for a standard execution call
     * @param auto 
     */
    @Override
    public void execRoute(boolean auto)
    {
        execRoute(auto, 1, false);
    }

    /**
     * Runs the route even though one of its accessories is on track a train is running over.
     *
     * Adam, 2026-08-25: "conflicting routes should still be executable in case of a transient
     * accessory failure.  Add a confirmation dialog to the UI similar to how individual clicks
     * currently work when an accessory has an active route."
     *
     * The case he is protecting is real and is the reason a refusal alone is wrong: a turnout that did
     * not take the command, or reported the wrong position, is exactly when somebody needs to set it -
     * and it is exactly when it will be on a locked path, because the path is what commanded it.
     *
     * So the refusal is for the door with nobody at it. The two doors with a person at them ask, the
     * same way clicking the tile of an accessory on an active route has always asked, and this is what
     * they call when the answer is yes.
     *
     * Not passed down to chained routes: a route this one triggers is asked about on its own terms,
     * because the operator agreed to THIS route's conflict and was never shown the other's.
     */
    public void execRouteOverridingConflicts()
    {
        execRoute(false, 1, true);
    }

    /**
     * Which accessory of this route is on track a train is running over, or null if none.
     *
     * Public so that a menu can ask before it acts, rather than acting and being refused - the same
     * shape as canStartAutonomy, and the same lesson (OB-050, OB-090).
     *
     * @return the accessory's name, or null when the route is free to run
     */
    public String conflictingAccessory()
    {
        String[] why = accessoryHeldByAutonomy();

        return why == null ? null : why[0];
    }
    
    /**
     * The first accessory this route would set that autonomy has locked, or null if none.
     *
     * Asked of `getActiveAccs`, which is the same set the diagram's tile click asks and whose own
     * javadoc says it exists "to WARN before throwing an accessory on an active route". Route
     * execution never asked it (AU-A2).
     *
     * Silent when autonomy is not running, which is when routes are most of what this application is
     * for - nothing about ordinary route use changes.
     *
     * @return the accessory's name, for the log, or null when the route is safe to run
     */
    private String[] accessoryHeldByAutonomy()
    {
        for (RouteCommand rc : this.route)
        {
            String[] why = heldReason(rc);

            if (why != null) return why;
        }

        return null;
    }

    /**
     * Whether autonomy holds the accessory THIS ONE command would set, asked fresh.
     *
     * **Asked per command, immediately before the command, because a route takes seconds to run.**
     * The guard used to be evaluated once before the loop, and `execRoute` sleeps
     * `SLEEP_INTERVAL` plus the command's own delay between every pair of commands. So a dispatch
     * that locked a path while the route was part way through was invisible to it, and the route
     * went on to throw a turnout the path had just configured and validated - which is AU-A2 itself,
     * surviving in a window seconds wide, through all three doors including the s88 trigger with
     * nobody present.
     *
     * Found by an independent review that reproduced it with a timestamped log: the route committed
     * at 24.209, autonomy locked the turnout at 24.711, and the route set it against the path at
     * 26.764 while `getActiveAccs` contained it.
     *
     * @param rc the command about to be sent
     * @return the accessory's name and the message key for the refusal, or null when it is safe
     */
    private String[] heldReason(RouteCommand rc)
    {
        if (rc == null || !rc.isAccessory()) return null;

        if (!this.network.hasAutoLayout() || !this.network.isAutonomyRunning()) return null;

        java.util.Collection<Accessory> locked = this.network.getAutoLayout().getActiveAccs();

        if (locked == null) return null;

        // The signals protecting squares somebody is standing on, which getActiveAccs cannot see.
        //
        // It walks the config commands of active edges, and a protecting signal is usually not one -
        // it is driven separately, by occupancy. So a route could turn a platform's signal green with
        // a train standing at it, and nothing re-asserts it until the next occupancy change: a green
        // aspect inviting a hand-driven train into an occupied platform, for as long as the train
        // stays there.
        //
        // "Usually" rather than "never", which is how this comment first read. `refreshOneSignal` says
        // outright that TilePorts gives a SIGNAL tile a GREEN configuration command, so a path
        // configured across one drives it through getConfigCommands - the same Accessory. The two sets
        // overlap; this one covers the platforms no active path happens to cross.
        java.util.Set<String> protecting = new java.util.LinkedHashSet<>();

        for (org.traincontrol.automation.Point point : this.network.getAutoLayout().getPoints())
        {
            if (point.getCurrentLocomotive() != null) protecting.addAll(point.getProtectingSignals());
        }

        // By address AND protocol, which is how the route names it and how the station resolves it -
        // a bare address is ambiguous across decoder types on this railway.
        MarklinAccessory accessory =
            this.network.getAccessoryByAddressIfPresent(rc.getAddress(), rc.getProtocol());

        if (accessory == null) return null;

        if (locked.contains(accessory))
        {
            return new String[] {accessory.getName(), "route.refusedAccessoryOnActivePath"};
        }

        // The ASPECT matters here, and it does not for the case above.
        //
        // A turnout on a locked path must not move at all - any position but the one the path
        // configured is wrong for the train crossing it. A protecting signal is different: the only
        // harmful command is the one that turns protection OFF. A route setting it red is doing
        // exactly what protection would do, and refusing that was pure over-strictness - it fired for
        // every route touching any signal of any platform with a train parked at it, and because
        // accessories are skipped as a group, it took the whole route's turnouts with it. Found by
        // review, which reproduced it with no path locked anywhere.
        //
        // `getSetting()` is true for RED and TURN, false for GREEN and STRAIGHT (Accessory.java).
        if (!rc.getSetting() && protecting.contains(accessory.getName()))
        {
            return new String[] {accessory.getName(),
                "route.refusedSignalProtectingOccupiedPlatform"};
        }

        return null;
    }

    /**
     * Executes the route
     * @param auto - was the route triggered automatically?
     * @param recursionLimit - the maximum number of other routes that can be triggered from this route
     */
    private void execRoute(boolean auto, int recursionLimit, boolean overrideConflicts)
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
                    // Not onto track a train is running over (AU-A2).
                    //
                    // Route execution and autonomy path locking each worked exactly as designed and
                    // neither consulted the other. `configureAndLockPath` reserves every accessory on
                    // a path, commands it and validates it; a route then set the same accessory back,
                    // with no refusal and nothing said. The train is routed off its protected path.
                    //
                    // Three doors reached it and the automatic one is the worst: an s88 trigger route
                    // left over from manual operation fires when an AUTONOMY train crosses the trigger
                    // sensor - sensors are shared and reused on this railway - so no person is
                    // involved at all. The diagram's route tile looked guarded and was not: that guard
                    // asks `activeAccs.contains(c.getAccessory())`, and a route component's accessory
                    // is null.
                    //
                    // REFUSED rather than confirmed, and refused WHOLE. This class is the model half
                    // and has no business showing a dialog - the tile-click door already asks, for the
                    // one case where a person is present - and a route half executed leaves the layout
                    // in a state nobody chose. Adam's rule for a running layout is the same shape:
                    // "Never allow any modifications to a running layout."
                    //
                    // Only the accessories that are actually on a locked path, so a route that turns
                    // on the lights or stops the power runs during autonomy exactly as before.
                    // The ACCESSORY half only, never the whole route.
                    //
                    // The first version of this returned here, discarding every command in the route -
                    // and a validation pass proved what that costs: a route that cuts the power AND
                    // sets a trap point, which is the shape a safety route on an s88 trigger naturally
                    // has, was refused entirely because of the turnout. The emergency stop did not
                    // run. Measured, not reasoned: `getPowerState()` was still true afterwards.
                    //
                    // "Refused whole" is a good argument about accessories - setting three switches of
                    // five leaves the layout in a state nobody chose - and it is not an argument for
                    // suppressing a stop, which is safe to obey whatever else is true. So the
                    // accessories go as a group and everything else runs: stop, functions off, lights,
                    // locomotive speeds, chained routes.
                    // Unless somebody has said to go ahead.
                    //
                    // Adam: "conflicting routes should still be executable in case of a transient
                    // accessory failure." A turnout that did not take the command is exactly when it
                    // needs setting, and exactly when it will be on a locked path - because the path
                    // is what commanded it. A refusal with no way past it would take the recovery away
                    // at the moment it is wanted.
                    //
                    // So this stays the answer for the s88 trigger door, which fires with nobody
                    // present, and the two doors with a person at them ask first.
                    // A local, because the parameter is captured by the thread this runs on and so
                    // cannot be assigned; the mid-route question below moves this one.
                    boolean override = overrideConflicts;

                    String[] conflict = override ? null : accessoryHeldByAutonomy();

                    boolean skipAccessories = conflict != null;

                    if (skipAccessories)
                    {
                        // The reason comes back with the accessory, because the two reasons are not
                        // the same sentence.  "A train is running over it" is true of a locked path
                        // and false of a platform with a train parked at it, and the log said the
                        // first for both.
                        this.network.logf(conflict[1], this.getName(), conflict[0]);
                    }

                    for (RouteCommand rc : this.route)
                    {
                        if (rc != null)
                        {
                            if (rc.isAccessory())
                            {
                                // Skipped as a group when any of them is on a locked path - see above.
                                if (skipAccessories) continue;

                                // And asked AGAIN, immediately before the command.
                                //
                                // The check above is made once and this loop takes seconds: it sleeps
                                // SLEEP_INTERVAL plus each command's own delay between every pair of
                                // commands. So a dispatch that locked a path while the route was part
                                // way through was invisible, and the route went on to throw a turnout
                                // the path had just configured - AU-A2 itself, surviving in a window
                                // seconds wide, through the s88 door with nobody present.
                                //
                                // Once it trips, every LATER accessory is skipped too, so the route
                                // does not go on setting some of its ironwork and not the rest as
                                // conditions change under it. Partially set is a real cost and it is
                                // the smaller one: the alternative is throwing a switch under a train
                                // that is crossing it.
                                String[] now = override ? null : heldReason(rc);

                                if (now != null)
                                {
                                    this.network.logf(now[1], this.getName(), now[0]);

                                    // Asked, if there is somebody to ask (Adam, 2026-08-25: "ask me,
                                    // at the two human doors").
                                    //
                                    // `auto` is the s88 trigger door, which fires with nobody
                                    // present - there the only safe answer is to stop, and it does.
                                    // The two doors a person uses get the same question they were
                                    // asked before the route started, for the same reason: a turnout
                                    // that did not take its command is exactly when somebody needs to
                                    // set it.
                                    //
                                    // ONCE, not per command. The answer is remembered for the rest of
                                    // this route by turning on the override - being asked again at
                                    // every remaining accessory would be unusable, and would let one
                                    // route end up half in each state anyway.
                                    if (!auto && this.network.getGUI() != null
                                        && this.network.getGUI().confirmRouteConflictMidway(
                                            this, now[0]))
                                    {
                                        override = true;
                                    }
                                    else
                                    {
                                        // Every LATER accessory too, so the route does not go on
                                        // setting some of its ironwork and not the rest as conditions
                                        // change under it.
                                        skipAccessories = true;

                                        continue;
                                    }
                                }

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
                                            // A chained route is asked about on its own terms: the operator agreed to THIS
                                            // route's conflict and was never shown that one's.
                                            r.execRoute(false, recursionLimit - 1, false);
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