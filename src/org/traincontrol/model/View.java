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
    
    // Tells us which key(s) a locomotive is bound to
    public List<String> getAllLocButtonMappings(Locomotive l);
}
