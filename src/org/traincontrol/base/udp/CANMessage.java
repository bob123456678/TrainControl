package org.traincontrol.base.udp;

/**
 * Base CAN message class - to be expanded in the future to make TrainControl generic
 */
abstract public class CANMessage
{
    protected byte[] rawMessage;
    
    /**
     * Get the raw message bytes
     *
     * @return
     */
    public byte[] getRawMessage()
    {
        return rawMessage;
    }
}
