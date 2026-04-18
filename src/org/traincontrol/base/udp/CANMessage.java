package org.traincontrol.base.udp;

/**
 * Base CAN message class - to be expanded in the future to make TrainControl generic
 */
public class CANMessage
{
    protected byte[] rawMessage;

    public CANMessage(byte[] message)
    {
        this.rawMessage = message;
    }
    
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
