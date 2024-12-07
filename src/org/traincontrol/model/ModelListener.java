package org.traincontrol.model;

import org.traincontrol.marklin.udp.CS2Message;

/**
 * Required model functionality interface
 * @author Adam
 */
public interface ModelListener
{
    public void receiveMessage(CS2Message message);
    
    public void log(String message);
    public void log(Exception e);
}
