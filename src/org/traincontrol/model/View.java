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
     * Shows a non-blocking alert dialog to the user (used by autonomy to report a path that could not
     * be configured).  Must not block the calling thread.
     * @param message the message to display
     */
    public void showAutonomyAlert(String message);

    // Tells us which key(s) a locomotive is bound to
    public List<String> getAllLocButtonMappings(Locomotive l);
}
