package org.traincontrol.base;

/**
 * This interface defines a remote device, such as a locomotive or switch
 * @author Adam
 * @param <DEVICE> the item's class
 * @param <MESSAGE> the message class
 */
public interface RemoteDevice<DEVICE, MESSAGE>
{
    /**
     * Transmits device's state to the network
     * @return 
     */
    public DEVICE syncFromState();
    
    /**
     * Update state based on network message
     * @param m 
     */
    public void parseMessage(MESSAGE m);
    
    /**
     * Synchronizes device's state based on network state
     * @return 
     */
    public DEVICE syncFromNetwork();    
}
