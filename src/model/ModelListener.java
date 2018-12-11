package model;

import marklin.udp.CS2Message;

/**
 * Required model functionality interface
 * @author Adam
 */
public interface ModelListener
{
    public void receiveMessage(CS2Message message);
    
    public void log(String message);
}
