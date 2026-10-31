package org.traincontrol.marklin;

import org.traincontrol.base.Locomotive;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.LayoutLabel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.util.Util;

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
    // Floor under any delay a command carries; public so the editor can raise a smaller typed value to it.
    public static final int DEFAULT_SLEEP_MS = 150;

    // The route-command delay that makes the *next* command land THREEWAY_DELAY_MS later, the gap the track diagram
    // leaves between a three-way's two commands: execRoute sleeps SLEEP_INTERVAL plus the command's own delay, hence
    // the subtraction.
    //
    // Used by the CS3 route importer and the route editor's wizard.  The CS2 flat-file importer cannot tell a three-way
    // from a plain turnout (parseMags keeps only SWITCH or SIGNAL) and command capture carries no delay, so their pairs
    // fire DEFAULT_SLEEP_MS apart.
    //
    // Has to exceed DEFAULT_SLEEP_MS: execRoute honors a command's own delay only when it is the larger of the two, so
    // lowering THREEWAY_DELAY_MS far enough would make this silently inert.
    public static final int THREEWAY_ROUTE_DELAY_MS =
        (int) (MarklinAccessory.THREEWAY_DELAY_MS - MarklinControlStation.SLEEP_INTERVAL);
    
    // State for routes with S88 trigger
    // volatile: the monitor thread loops on this field while enable() and disable() run on the EDT; without it a
    // disable may never be seen, and deleteRoute relies on it to retire the monitor of an edited or removed route.
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
        // Trimmed here, where every door meets: newRoute indexes under the trimmed name and deleteRoute deletes by the
        // route's own.
        super(name == null ? null : name.trim());
        
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
        super(name == null ? null : name.trim(), route);
        
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

            // A daemon, because it parks on an untimed wait that disable() cannot interrupt and would keep the JVM
            // alive after the window closes.  Named so a thread dump says which route is waiting.
            this.monitorThread.setDaemon(true);

            this.monitorThread.setName("route monitor " + this.getId() + " " + this.getName());

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
        // The labels this one replaces go now; nothing else can drop them on the main window
        // (LayoutLabel.forgetReplaced).
        LayoutLabel.forgetReplaced(this.tiles, l);

        this.tiles.add(l);
    }
    
    /**
     * Wrapper for a standard execution call
     * @param auto 
     */
    @Override
    /**
     * Walks a copy of the command list: deleteLoc removes commands through Iterator.remove() on the event thread while
     * this runs on its own thread, and both list kinds are fail-fast.  The copy is not atomic, only a microsecond
     * window.
     */
    public void execRoute(boolean auto)
    {
        execRoute(auto, 1, false);
    }

    /**
     * Runs the route even though one of its accessories is on track a train is running over.  Adam: "conflicting routes
     * should still be executable in case of a transient accessory failure."
     *
     * One "OK" covers the whole run: asking per accessory would mean a dialog per command mid-route.  Not passed down
     * to chained routes, which are asked on their own terms.
     */
    public void execRouteOverridingConflicts()
    {
        execRoute(false, 1, true);
    }

    /**
     * Which accessory of this route is on track a train is running over, or null if none.  Public so a menu can ask
     * before it acts (OB-050).
     *
     * @return the accessory's name, or null when the route is free to run
     */
    public String conflictingAccessory()
    {
        String[] why = conflictingAccessoryAndReason();

        return why == null ? null : why[0];
    }

    /**
     * The same question, answered with the refusal's message key as well: a turnout on a locked path is not a signal
     * protecting a parked platform, and the dialog must say which.
     *
     * @return the accessory's name and the message key for the refusal, or null when it is safe
     */
    public String[] conflictingAccessoryAndReason()
    {
        // An emergency stop is never a question (Adam: "Emergency stop should never conflict or prompt.").  Null here
        // takes the question away at every door; the accessory is still skipped and logged by the per-command check
        // below.
        if (this.hasEmergencyStop()) return null;

        return accessoryHeldByAutonomy();
    }

    /**
     * Whether this route cuts the power: an emergency stop anywhere in its commands, or a route it fires that is one
     * (`cutsThePowerAt`).  It is never asked about a conflict, since Cancel would call off a power cut the question
     * never mentioned.  Adam: "emergency stop should never conflict or prompt."
     *
     * @return true when this route, or a route it fires, stops the railway
     */
    public boolean hasEmergencyStop()
    {
        return cutsThePowerAt(1, new java.util.HashSet<>());
    }

    /**
     * Whether this route, run at this recursion limit, cuts the power - itself, or through a route it runs.  The limits
     * are `execRoute`'s own: every door starts a route at 1, a chained route runs one lower, a route below 0 is
     * refused, and a stop-only route runs at any level.  Asked along the path, not once per route: a route met first
     * too deep to fire its stop may fire it from a shorter way in.
     */
    private boolean cutsThePowerAt(int limit, java.util.Set<MarklinRoute> seen)
    {
        if (!seen.add(this)) return false;

        try
        {
            if (limit < 0 && !this.isOnlyStops()) return false;

            if (hasAStop(this.route)) return true;

            for (RouteCommand rc : new java.util.ArrayList<>(this.route))
            {
                if (rc == null || !rc.isRoute() || this.network == null) continue;

                MarklinRoute fired = this.network.getRoute(rc.getName());

                if (fired != null && fired != this && fired.cutsThePowerAt(limit - 1, seen)) return true;
            }

            return false;
        }
        finally
        {
            seen.remove(this);
        }
    }

    /**
     * Whether every command of this route is an emergency stop - a route that fires nothing further.
     *
     * @return true for a stop-only route
     */
    public boolean isOnlyStops()
    {
        java.util.List<RouteCommand> commands = new java.util.ArrayList<>(this.route);

        if (commands.isEmpty()) return false;

        for (RouteCommand rc : commands)
        {
            if (rc == null || !rc.isStop()) return false;
        }

        return true;
    }

    private static boolean hasAStop(java.util.List<RouteCommand> commands)
    {
        for (RouteCommand rc : new java.util.ArrayList<>(commands))
        {
            if (rc != null && rc.isStop()) return true;
        }

        return false;
    }

    /**
     * Whether these commands put an emergency stop among commands of other kinds, which no route may do.  Adam: "if a
     * route has emergency stop, it cannot have any other types of commands.  Reject it from being created or imported
     * as such.  This will keep a clean separation." A route that mixes them, loaded or imported, is split instead
     * (`MarklinControlStation.splitRoutesThatMixAStop`); the model and the route editor refuse to make one.
     *
     * @param commands a route's commands, or null
     * @return true where there is a stop and anything else
     */
    public static boolean mixesAStop(java.util.List<RouteCommand> commands)
    {
        if (commands == null) return false;

        boolean stop = false;
        boolean other = false;

        for (RouteCommand rc : new java.util.ArrayList<>(commands))
        {
            if (rc == null) continue;

            if (rc.isStop()) stop = true;
            else other = true;
        }

        return stop && other;
    }
    
    /**
     * The first accessory this route would set that autonomy has locked, or null; silent when autonomy is not running.
     */
    private String[] accessoryHeldByAutonomy()
    {
        for (RouteCommand rc : new java.util.ArrayList<>(this.route))
        {
            String[] why = heldReason(rc);

            if (why != null) return why;
        }

        return null;
    }

    /**
     * Whether autonomy holds the accessory this one command would set, asked fresh per command: a route takes seconds
     * to run, and a dispatch that locks a path mid-route must stop it throwing the turnout the path just configured.
     */
    private String[] heldReason(RouteCommand rc)
    {
        if (rc == null || !rc.isAccessory()) return null;

        // ASKED ONCE, NOT BUILT: a route runs on its own thread, and Unload can clear the railway between
        // asking whether there is one and asking for it
        final org.traincontrol.automation.Layout railway = this.network.getAutoLayoutIfLoaded();

        if (railway == null || !this.network.isAutonomyRunning()) return null;

        // Each with the setting its path holds it at.  Adam: "make sure the code allows switching to the correct (same)
        // setting that autonomy is using"
        java.util.Map<Accessory, Boolean> locked = railway.getActiveAccSettings();

        if (locked == null) return null;

        // The signals protecting squares somebody is standing on, which getActiveAccs cannot see: a protecting signal
        // is usually driven separately, by occupancy, so a route could turn a platform's signal green with a train
        // standing at it.  The rule lives in `Layout.protectsAnOccupiedSquare`.

        // By address AND protocol, which is how the route names it and how the station resolves it -
        // a bare address is ambiguous across decoder types on this railway.
        MarklinAccessory accessory =
            this.network.getAccessoryByAddressIfPresent(rc.getAddress(), rc.getProtocol());

        if (accessory == null) return null;

        // THE SETTING THE PATH ALREADY HOLDS MOVES NOTHING under its train, so only a command that would change it is
        // refused - and any command where two paths disagree about it (null)
        if (locked.containsKey(accessory) && !Boolean.valueOf(rc.getSetting()).equals(locked.get(accessory)))
        {
            return new String[] {accessory.getName(), "route.refusedAccessoryOnActivePath"};
        }

        // The aspect matters here: a turnout on a locked path must not move at all, but a protecting signal is harmed
        // only by a command that turns protection OFF, and a route setting it red does what protection would do.
        // `getSetting()` is true for RED and TURN, false for GREEN and STRAIGHT (Accessory.java).  Asked of the Layout
        // so this and the diagram's tile click share one rule.
        //
        // A green the train's own path holds is no change (the exit guard of the platform a path leaves, the home
        // signal of the one it is bound for), but only while it shows green: one signal may guard two platforms, and
        // protection may have set it red for a train standing beside another path's.
        boolean heldGreenAndShowing = Boolean.FALSE.equals(locked.get(accessory)) && !accessory.isSwitched();

        if (!rc.getSetting() && railway.protectsAnOccupiedSquare(accessory) && !heldGreenAndShowing)
        {
            // Standing, or only reserved: a locked path reserves every point along it, so a platform holds this signal
            // red before any train is at it.  The refusal is the same either way; only what it says differs.
            return new String[] {accessory.getName(), railway.protectsAStandingTrain(accessory)
                ? "route.refusedSignalProtectingOccupiedPlatform" : "route.refusedSignalProtectingReservedPlatform"};
        }

        return null;
    }

    /** What a route does when one of its accessory commands conflicts with a train (MT-247). */
    public enum ConflictResponse
    {
        /** The operator said yes: set the ironwork and everything else. */
        RUN_EVERYTHING,

        /** The operator said no: nothing in this route happens. */
        CANCEL_ROUTE,

        /** Nobody was there to ask: leave THIS accessory alone, run everything else. */
        SKIP_THIS_ACCESSORY
    }

    /**
     * Adam's ruling, as a function of who is there and what they said (MT-247): "1. cancel should cancel everything.
     * OK should fire everything.  2. if the route is auto triggered: popup, just a notification in the log.  don't run
     * the conflicting switch commands, but do run the power off and others."
     *
     * A person who said no cancels the whole route; with nobody there (the s88 door) only THIS accessory is left alone
     * and everything else still runs, so a route that cuts the power still does.  Named so the ruling can be run
     * without a View.
     *
     * @param askable whether there is somebody to ask - a person's door, with a window attached
     * @param saidYes what they answered, meaningless when askable is false
     * @return what the route should do
     */
    public static ConflictResponse respondToConflict(boolean askable, boolean saidYes)
    {
        if (!askable) return ConflictResponse.SKIP_THIS_ACCESSORY;

        return saidYes ? ConflictResponse.RUN_EVERYTHING : ConflictResponse.CANCEL_ROUTE;
    }
    /**
     * Executes the route
     * @param auto - was the route triggered automatically?
     * @param recursionLimit - the maximum number of other routes that can be triggered from this route
     */
    private void execRoute(boolean auto, int recursionLimit, boolean overrideConflicts)
    {
        // A route that is only a stop runs at any depth: it fires nothing further, and a split route's stop sits one
        // route below the route that fires it.
        if (recursionLimit < 0 && !this.isOnlyStops())
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

                // And the route list's mark, whoever started this (FR-137)
                if (this.network.getGUI() != null) this.network.getGUI().routeStarted(this);

                // try/finally so that stopExecuting always runs.  setExecuting is the re-entrancy
                // guard: if a command threw, the flag stayed set and every later attempt to run this
                // route silently returned false for the rest of the session.
                try
                {
                    // Not onto track a train is running over: route execution and autonomy path locking never consulted
                    // each other, so a route set back an accessory that a locked path had just configured.  Refused
                    // rather than confirmed at the door with nobody at it (an s88 trigger); the two doors with a person
                    // at them ask first.
                    //
                    // One command at a time (MT-247): being on track a train is using is a property of the accessory,
                    // so a switch on clear track is thrown whatever a different switch is doing, and the stop,
                    // functions, lights, speeds and chained routes all run.
                    //
                    // Unless somebody has said to go ahead: a turnout that did not take the command is exactly when it
                    // needs setting, and exactly when it is on a locked path.  A local, because the parameter is
                    // captured by the thread this runs on and cannot be assigned; the mid-route question below moves
                    // this one.
                    boolean override = overrideConflicts;

                    // Nothing is decided for the whole route here (MT-247): the per-command check below is the right
                    // grain, and it writes one log line per command actually refused.  `accessoryHeldByAutonomy` still
                    // screens a route before it runs, at the human doors, through `conflictingAccessoryAndReason`.

                    for (RouteCommand rc : new java.util.ArrayList<>(this.route))
                    {
                        if (rc != null)
                        {
                            if (rc.isAccessory())
                            {
                                // Asked per command, immediately before it goes out: this loop takes seconds, so a
                                // dispatch that locks a path mid-route must stop the next turnout.  Per command is also
                                // the grain of Adam's ruling (MT-247): the held accessory is skipped and the next is
                                // asked fresh.
                                String[] now = override ? null : heldReason(rc);

                                if (now != null)
                                {
                                    // Asked if there is somebody to ask.  `auto` is the s88 door, which fires with
                                    // nobody present, so the held accessory is skipped and logged.  The two doors a
                                    // person uses get the same question they were asked before the route started, once:
                                    // the answer is remembered by turning on the override.
                                    //
                                    // A person saying no ends the REST of the route (the commands before it already
                                    // went out and stand).  The question parks the route's own thread on purpose:
                                    // running its speeds over turnouts that were not set is worse.
                                    //
                                    // A route that cuts the power is never asked, here or before the route starts
                                    // (`conflictingAccessoryAndReason`); otherwise the two halves of one gesture
                                    // disagree and an answer about a turnout silently declines a power cut.
                                    final boolean askable = !auto && !this.hasEmergencyStop()
                                        && this.network.getGUI() != null;

                                    final ConflictResponse response = respondToConflict(askable,
                                        askable && this.network.getGUI().confirmRouteConflictMidway(
                                            this, now[0], now[1]));

                                    if (response == ConflictResponse.RUN_EVERYTHING)
                                    {
                                        override = true;
                                    }
                                    else if (response == ConflictResponse.CANCEL_ROUTE)
                                    {
                                        // Canceled whole; the log line first, so the record of why exists before the
                                        // route stops.
                                        this.network.logf(now[1], this.getName(), now[0]);

                                        this.network.logf("route.cancelledByOperator", this.getName());

                                        return;
                                    }
                                    else
                                    {
                                        // Logged here, where the refusal happens: both messages say the route did not
                                        // switch the accessory, which is false before the answer is known.
                                        this.network.logf(now[1], this.getName(), now[0]);

                                        // This command only (MT-247): everything else in the route still runs - speeds,
                                        // functions, chained routes.
                                        continue;
                                    }
                                }

                                int idd = rc.getAddress();
                                boolean state = rc.getSetting();

                                this.network.setAccessoryState(idd, rc.getProtocol(), state);
                            }
                            else if (rc.isStop())
                            {                        
                                // Sent whatever the power flag says (behaviour/routes.md 7a: an emergency stop is
                                // obeyed whatever else is true): the flag is only the last GO or STOP echo heard, so
                                // one lost GO skipped the stop of a route fired with nobody present.  It decides the
                                // notice and the log line only.
                                final boolean wasOn = this.network.getPowerState();

                                this.network.stop();

                                if (wasOn)
                                {
                                    this.network.logf(
                                        "route.powerTurnedOffCondition",
                                        this.getName()
                                    );

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
                                // Asked once, not built
                                final org.traincontrol.automation.Layout railway = this.network.getAutoLayoutIfLoaded();

                                if (railway != null)
                                {
                                    this.network.logf(
                                        "route.turningOnAutonomyLights",
                                        this.getName()
                                    );

                                    this.network.lightsOn(railway.getLocomotivesToRun().stream().map(Locomotive::getName).collect(Collectors.toList()));
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
                                            //
                                            // A chained route is asked about on its own terms - the operator agreed to
                                            // THIS route's conflict - so the override stays false.  `auto` is passed
                                            // on: an s88-fired route has nobody at the machine, and a modal dialog
                                            // there would block this thread forever.
                                            r.execRoute(auto, recursionLimit - 1, false);
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
                                // One expression for both branches, so a delay below DEFAULT_SLEEP_MS is never accepted
                                // and then silently discarded.  The floor is kept (it is the gap this program leaves
                                // between two route commands, and THREEWAY_ROUTE_DELAY_MS sits above it); `delayOf`
                                // raises a smaller number as it is typed.
                                long pause = Math.max(rc.getDelay(), MarklinRoute.DEFAULT_SLEEP_MS);

                                if (rc.getDelay() > 0)
                                {
                                    this.network.logf(
                                        "route.delay",
                                        pause
                                    );
                                }

                                Thread.sleep(MarklinControlStation.SLEEP_INTERVAL + pause);    
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

                    if (this.network.getGUI() != null) this.network.getGUI().routeFinished(this);
                }
            }
            else
            {
                // A sensor can fire the route again before it has finished: dropped, and now said, since this door said
                // nothing.
                this.network.logf("route.ui.infoAlreadyRunning", this.getName());
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
        for (RouteCommand rc : new java.util.ArrayList<>(this.route))
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
        JSONObject jsonObj = Util.orderedJSON();
        
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