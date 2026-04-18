package org.traincontrol.model;

import org.traincontrol.base.udp.CANMessage;

/**
 * Required model functionality interface
 * @author Adam
 */
public interface ModelListener
{
    // Process and interpret a network message
    public void receiveMessage(CANMessage message);
    
    // Initializes a fixed buffer to read from the network (maximum CAN message size)
    public byte[] initMessageBuffer();
    
    // Create a message object based on the buffer
    CANMessage createMessage(byte[] rawBuffer);

    // Logging methods
    public void logf(String key, Object... args);
    public void log(String message);
    public void log(Exception e);
}
