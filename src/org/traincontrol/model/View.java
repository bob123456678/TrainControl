package org.traincontrol.model;

import org.traincontrol.base.Locomotive;
import java.util.List;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Route;

/**
 * Interface for a generic train control GUI
 * @author Adam
 */
public interface View
{
    /**
     * Regenerates locomotive display after a change occurred
     */
    public void repaintLoc();
    public void repaintLoc(boolean force, List<Locomotive> locs);

    /**
     * Regenerates a switch display
     */
    public void repaintSwitches();
    public void repaintSwitch(int id, Accessory.accessoryDecoderType protocol);

    /**
     * A feedback has changed state.  Only the route editor's capture wants this: capturing a CONDITION means "run this
     * when the railway looks like it does now", and half of what a condition can say is about sensors.  Named for what
     * happened rather than for what to redraw: the tiles repaint themselves.
     *
     */
    public void feedbackChanged(String name, boolean state);

    /**
     * Follows a locomotive's new name into the autonomy setup, in every configuration.  The setup holds locomotives by
     * NAME and lives with the interface rather than the model, so the model cannot repair it itself; left alone, an
     * inactive configuration goes on naming a locomotive that no longer exists, and parseAuto answers a name it cannot
     * find by invalidating the whole layout.
     *
     * @param from the old name
     * @param to the new name
     */
    public void autonomyLocomotiveRenamed(String from, String to);

    /**
     * The same for a locomotive that has been deleted: it is taken out rather than followed.
     *
     * @param name the locomotive that no longer exists
     */
    public void autonomyLocomotiveDeleted(String name);
    
    /**
     * Regenerates the layout display
     */
    public void repaintLayout();
    
    /**
     * Updates the power state;
     */
    public void updatePowerState();
    
    /**
     * Logs a message
     * @param message 
     */
    public void log(String message);
    
    /**
     * Callback with latency info
     * @param latency 
     */
    public void updateLatency(double latency);
    
    /**
     * Alerts that an emergency stop condition was triggered
     * @param r 
     */
    public void emergencyStopTriggered(Route r);

    /**
     * Tells the window that a route has begun running, so that it can show it (FR-137).  May be called from the route's
     * own thread.
     *
     * @param r the route that has begun
     */
    public void routeStarted(Route r);

    /**
     * Tells the window that a route has finished running, so that what it held off while the route ran can be done: a
     * locomotive's direction change the setup did not follow (`TrainControlUI.followTheTurn`).  May be called from the
     * route's own thread.
     *
     * @param r the route that has finished
     */
    public void routeFinished(Route r);

    /**
     * Asks whether to carry on with a route that has hit a conflict PART WAY THROUGH.  The model hands the question to
     * whoever is at the door, as emergencyStopTriggered hands over the fact that a route cut the power.
     *
     * Only asked when a person started the route (the routes tab or the diagram's route tile).  The s88 trigger door
     * has nobody to ask, so it sets none of its accessories and runs the rest.
     *
     * THIS BLOCKS THE ROUTE, not merely its accessories: it is called on the route's own thread, and everything after
     * the conflicting accessory (speeds, functions off, chained routes) waits for the answer, because running a route's
     * speeds while its turnouts are not set is worse than making the operator answer first.
     *
     * A route carrying an emergency stop never gets here: `MarklinRoute` tests `hasEmergencyStop()` before asking
     * (MT-247), as "emergency stop should never conflict or prompt".  Both doors carve it out.
     *
     * @param r the route part way through executing
     * @param accessory the accessory autonomy has taken since the route started
     * @param reason the message key the route would have logged for this refusal, so the question names the right
     *  one: a turnout on a locked path, or a signal protecting a platform with a train parked at it
     * @return true to set this accessory and run the rest of the route; false to CANCEL THE ROUTE: "cancel should
     *  cancel everything.  OK should fire everything."  What has already gone out before the conflicting command has
     *  gone out; the cancel is about the rest
     */
    public boolean confirmRouteConflictMidway(Route r, String accessory, String reason);

    /**
     * Shows a non-blocking alert dialog to the user (used by autonomy to report a path that could not
     * be configured).  Must not block the calling thread.
     * @param message the message to display
     */
    public void showAutonomyAlert(String message);

    /**
     * Tells the user the first sync with the Central Station failed and asks whether to try it again (OB-338).
     * Called on the start-up thread, which waits for the answer.
     * @param error what went wrong
     * @return true to sync again; false to exit, which is also what closing the window means
     */
    public boolean offerResync(String error);

    // Tells us which key(s) a locomotive is bound to
    public List<String> getAllLocButtonMappings(Locomotive l);
}
