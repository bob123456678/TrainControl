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
     * A feedback has changed state.
     *
     * Only the route editor's capture wants this, and it wants it for the same reason it wants
     * repaintSwitch: capturing a CONDITION means "run this when the railway looks like it does now",
     * and half of what a condition can say is about sensors.  Switches reached the editor because
     * their repaint already came this way and feedback had no callback at all - so a sensor triggered
     * while capturing simply did nothing, with the tick box still ticked.
     *
     * Named for what happened rather than for what to redraw: the tiles repaint themselves.
     *
     * @param name the feedback's name, as a route command spells it
     * @param state whether it is now occupied
     */
    public void feedbackChanged(String name, boolean state);

    /**
     * Follows a locomotive's new name into the autonomy setup, in every configuration.
     *
     * The setup holds locomotives by NAME - the placement, the home assignment and the exclusion list -
     * and it lives with the interface rather than with the model, so the model cannot repair it itself.
     * Left alone, a configuration that was not active at the time goes on naming a locomotive that no
     * longer exists, and parseAuto answers a name it cannot find by invalidating the whole layout.
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
     * Asks whether to carry on with a route that has hit a conflict PART WAY THROUGH.
     *
     * The model does not put up dialogs, and this is not it doing so: it is the model handing the
     * question to whoever is at the door, exactly as emergencyStopTriggered above hands over the fact
     * that a route cut the power.
     *
     * Only asked when a person started the route - the routes tab or the diagram's route tile. The
     * s88 trigger door has nobody to ask and stops on its own.
     *
     * Adam, 2026-08-25, choosing between three ways of handling the case: "ask me, at the two human
     * doors". The alternative he turned down was letting the route finish and merely logging it,
     * which is the one that can move a switch under a train.
     *
     * @param r the route part way through executing
     * @param accessory the accessory autonomy has taken since the route started
     * @return true to set it anyway and finish the route, false to leave the rest of its accessories
     */
    public boolean confirmRouteConflictMidway(Route r, String accessory);

    /**
     * Shows a non-blocking alert dialog to the user (used by autonomy to report a path that could not
     * be configured).  Must not block the calling thread.
     * @param message the message to display
     */
    public void showAutonomyAlert(String message);

    // Tells us which key(s) a locomotive is bound to
    public List<String> getAllLocButtonMappings(Locomotive l);
}
