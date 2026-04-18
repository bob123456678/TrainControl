package org.traincontrol.model;

import org.traincontrol.base.CANMessage;

/**
 * Required model functionality interface
 * @author Adam
 */
public interface ModelListener
{
    public void receiveMessage(CANMessage message);
    public byte[] initMessageBuffer();

    public void logf(String key, Object... args);
    public void log(String message);
    public void log(Exception e);
}
