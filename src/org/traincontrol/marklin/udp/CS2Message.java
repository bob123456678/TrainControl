package org.traincontrol.marklin.udp;

import org.traincontrol.util.Conversion;

/**
 * Class to represent and generate CS2 CAN messages
 * @author Adam
 */
public class CS2Message
{
    /* Class fields constants */

    // The raw 13-byte message sent to/from the Marklin CS2 over the network
    private byte[] rawMessage;
    // Message priority as defined in the protocol
    private Integer priority;
    // Command issued
    private Integer command;
    // Hash value
    private Integer hash;
    // Response bit
    private Boolean response;
    // Length of the payload
    private Integer length;
    // Data payload
    private byte[] data;
    // CAN message length
    public static final int MESSAGE_LENGTH = 13;

    /* Command constants */

    // System constants
    public static final int CMD_SYSTEM = 0x00;
    public static final int ID_SYSTEM = 0x00;

    // System sub-commands
    public static final int CMD_SYSSUB_STOP = 0x00;
    public static final int CMD_SYSSUB_GO = 0x01;
    public static final int CMD_SYSSUB_HALT = 0x02;
    public static final int CMD_SYSSUB_TRAINSTOP = 0x03;
    public static final int CMD_SYSSUB_STOPCYCLE = 0x04;
    public static final int CMD_SYSSUB_SWTIME = 0x06;
    public static final int CMD_SYSSUB_NEWREGNR = 0x09;
    public static final int CMD_SYSSUB_OVERLOAD = 0x0A;
    public static final int CMD_SYSSUB_STATUS = 0x0B;

    // Locomotive commands
    public static final int CMD_LOCO_DISCOVERY = 0x01;
    public static final int ID_LOCO_DISCOVERY = 0x02;
    public static final int CMD_LOCO_BIND = 0x02;
    public static final int ID_LOCO_BIND = 0x04;
    public static final int CMD_LOCO_VERIFY = 0x03;
    public static final int ID_LOCO_VERIFY = 0x06;
    public static final int CMD_LOCO_VELOCITY = 0x04;
    public static final int ID_LOCO_VELOCITY = 0x08;
    public static final int CMD_LOCO_DIRECTION = 0x05;
    public static final int ID_LOCO_DIRECTION = 0x0A;
    public static final int CMD_LOCO_FUNCTION = 0x06;
    public static final int ID_LOCO_FUNCTION = 0x0C;
    public static final int CMD_LOCO_READ_CONFIG = 0x07;
    public static final int ID_LOCO_READ_CONFIG = 0x0E;
    public static final int CMD_LOCO_WRITE_CONFIG = 0x08;
    public static final int ID_LOCO_WRITE_CONFIG = 0x10;

    // Accessory commands
    public static final int CMD_ACC_SWITCH = 0x0B;
    public static final int ID_ACC_SWITCH = 0x16;
    public static final int ID_ACC_SWITCH_RSP = 0x17;
    public static final int CMD_ACC_SENSOR = 0x11;

    // Software commands
    public static final int CAN_CMD_PING = 0x18;
    public static final int CAN_ID_PING = 0x30;

    // Other commands
    public static final int CAN_S88_REPORT = 0x21;
    public static final int CAN_SENSOR_EVENT = 0x23;

    /* Other Constants */

    // These magic numbers most likely refer to the protocol version
    // but this is poorly documented
    public static final int CS2_PROTOCOL_V1 = 0x0300;
    public static final int CS2_PROTOCOL_V2 = 0x4711;

    /* Constructors */

    /**
     * Constructor from byte array
     *
     * Separates the different parts of the message for later interpretation
     *
     * Parses the binary format below (P = priority, C = command, H = hash, R =
     * response, L = length)
     *
     * Byte 1: P|P|P|P|_|_|_|C Byte 2: C|C|C|C|C|C|C|R Byte 3: H|H|H|H|H|H|H|H
     * Byte 4: H|H|H|H|H|H|H|H Byte 5: _|_|_|_|L|L|L|L Byte 6-13: payload
     *
     * @param message - a byte array of 13 bytes
     */
    public CS2Message(byte[] message)
    {
        // Store the entire message
        this.rawMessage = message;

        // Priority - first 4 bits
        this.priority = message[0] >> 4;

        // Command - last bit of first byte, first 7 bits of second byte
        this.command = (message[0] << 7) | (message[1] >> 1);

        // Response - last bit of second byte
        this.response = (message[1] & 1) != 0;

        // Hash - third and fourth bytes
        this.hash = (message[2] << 8) | message[3];

        // Length - fifth byte
        this.length = (int) message[4];

        // Data - sixth through last byte
        this.data = new byte[8];

        // Only save data that's expected
        for (int i = 0; i < length; i++)
        {
            this.data[i] = message[5 + i];
        }
    }

    /**
     * Constructor from data values
     *
     * Generates a raw message using the format below (P = priority, C =
     * command, H = hash, R = response, L = length)
     *
     * Byte 1: P|P|P|P|_|_|_|C Byte 2: C|C|C|C|C|C|C|R Byte 3: H|H|H|H|H|H|H|H
     * Byte 4: H|H|H|H|H|H|H|H Byte 5: _|_|_|_|L|L|L|L Byte 6-13: payload
     *
     * @param priority
     * @param command
     * @param hash
     * @param response
     * @param length
     * @param data
     */
    public CS2Message(int priority, int command, int hash,
            boolean response, int length, byte[] data)
    {
        this.fromCS2Message(priority, command, hash,
                response, length, data);
    }

    /**
     * Copy constructor
     *
     * @param c
     */
    public CS2Message(CS2Message c)
    {
        this.fromCS2Message(c.getPriority(), c.getCommand(), c.getHash(),
                c.getResponse(), c.getLength(), c.getData());
    }

    /**
     * Simplified constructor
     *
     * @param command
     * @param data
     *
     * Assumes priority = 0, length = data.length, response = 0, hash = v2 prot.
     */
    public CS2Message(int command, byte[] data)
    {
        this.fromCS2Message(0, command, CS2Message.CS2_PROTOCOL_V2,
                false, data.length, data);
    }

    /**
     * Simplified constructor
     *
     * @param command
     * @param hash
     * @param data
     *
     * Assumes priority = 0, length = data.length, response = 0
     */
    public CS2Message(int command, int hash, byte[] data)
    {
        this.fromCS2Message(0, command, hash, false, data.length, data);
    }

    /**
     * Simplified constructor
     *
     * @param command
     * @param hash
     * @param response
     * @param data
     *
     * Assumes priority = 0, length = data.length
     */
    public CS2Message(int command, int hash, boolean response, byte[] data)
    {
        this.fromCS2Message(0, command, hash, response, data.length, data);
    }

    /* Private methods */

    /**
     * Sets class fields based on data values
     *
     * @param priority
     * @param command
     * @param hash
     * @param response
     * @param length
     * @param data
     */
    private void fromCS2Message(int priority, int command, int hash,
            boolean response, int length, byte[] data)
    {
        // Initialize empty message
        this.rawMessage = new byte[13];

        // Set first byte
        this.rawMessage[0] = (byte) (priority << 4);
        this.rawMessage[0] |= (byte) (command >> 7);

        // Set second byte
        this.rawMessage[1] = (byte) (command << 1);

        if (response)
        {
            this.rawMessage[1] |= (byte) 1;
        }

        // Set third and fourth byte
        this.rawMessage[2] = (byte) (hash >> 8);
        this.rawMessage[3] = (byte) hash;

        // Set fifth byte
        this.rawMessage[4] = (byte) length;

        // Set remaining bytes
        for (int i = 0; i < length; i++)
        {
            this.rawMessage[5 + i] = data[i];
        }

        // Store data
        this.priority = priority;
        this.command = command;
        this.hash = (int) ((short) hash);
        this.response = response;
        this.length = length;
        this.data = data;
    }

    /* Public methods */
    /**
     * Equivalence checker
     *
     * @param c
     * @return
     */
    public boolean equals(CS2Message c)
    {
        if (!c.getResponse().equals(this.response)
                || !c.getHash().equals(this.hash)
                || !c.getLength().equals(this.length)
                || !c.getPriority().equals(this.priority)
                || !c.getCommand().equals(this.command))
        {
            return false;
        }

        for (int i = 0; i < this.length; i++)
        {
            if (this.data[i] != c.getData()[i])
            {
                return false;
            }
        }

        for (int i = 0; i < 5 + this.length; i++)
        {
            if (this.rawMessage[i] != c.getRawMessage()[i])
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Is the message a system command?
     *
     * @return
     */
    public boolean isSysCommand()
    {
        return this.command == CMD_SYSTEM;
    }

    /**
     * Is the message a ping command?
     * @return
     */
    public boolean isPingCommand()
    {
        return this.command == CAN_CMD_PING;
    }

    /**
     * Merges bytes into a single integer (high-order first)
     *
     * @param bytes
     * @return
     */
    public static int mergeBytes(byte[] bytes)
    {
        int length = Math.min(4, bytes.length);
        int output = 0;

        for (int i = 0; i < length; i++)
        {
            // If we don't & 0xFF, bad things will happen during conversion
            output |= (((int) bytes[i] & 0xFF) << ((length - i - 1) * 8));
        }

        return output;
    }

    /**
     * Is the message a locomotive command?
     *
     * @return
     */
    public boolean isLocCommand()
    {
        return this.command == CMD_LOCO_DISCOVERY
            || this.command == CMD_LOCO_BIND
            || this.command == CMD_LOCO_VERIFY
            || this.command == CMD_LOCO_VELOCITY
            || this.command == CMD_LOCO_DIRECTION
            || this.command == CMD_LOCO_FUNCTION
            || this.command == CMD_LOCO_READ_CONFIG
            || this.command == CMD_LOCO_WRITE_CONFIG;
    }

    /**
     * Is the message an accessory command?
     *
     * @return
     */
    public boolean isAccessoryCommand()
    {
        return this.command == CMD_ACC_SWITCH;
    }

    /**
     * Is the message a feedback command?
     *
     * @return
     */
    public boolean isFeedbackCommand()
    {
        return this.command == CAN_S88_REPORT
            || this.command == CAN_SENSOR_EVENT
            || this.command == CMD_ACC_SENSOR;
    }

    /**
     * Is the message a different kind of command?
     *
     * @return
     */
    public boolean isOtherCommand()
    {
        return !(this.isFeedbackCommand() || this.isAccessoryCommand()
            || this.isLocCommand() || this.isSysCommand() || this.isPingCommand());
    }

    /**
     * Extracts the UID from the message
     *
     * @return
     */
    public int extractUID()
    {
        return CS2Message.mergeBytes(
            new byte[]
            {
                this.getData()[0], this.getData()[1],
                this.getData()[2], this.getData()[3]
            });
    }

    /**
     * Extracts a 2-byte UID from the message
     *
     * @return
     */
    public int extractShortUID()
    {
        return CS2Message.mergeBytes(
            new byte[]
            {
                this.getData()[2], this.getData()[3]
            });
    }

    /**
     * Gets the sub command
     * @return
     */
    public int getSubCommand()
    {
        if (this.data.length < 4)
        {
            return -1;
        }

        return CS2Message.mergeBytes(
            new byte[]
            {
                this.getData()[4]
            });
    }

    /**
     * Pretty printing
     * @return
     */
    @Override
    public String toString()
    {
        String s = "";
        String type = "Other";

        if (this.isAccessoryCommand())
        {
            type = "Accessory";
        }
        else if (this.isLocCommand())
        {
            type = "Loc";
        }
        else if (this.isFeedbackCommand())
        {
            type = "Feedback";
        }
        else if (this.isPingCommand())
        {
            type = "Ping";
        }
        else if (this.isSysCommand())
        {
            type = "System";
        }

        s += "\nPriority: " + this.priority.toString() + "\n";
        s += "Command: " + Conversion.intToHex(this.command) + "\n";
        s += "Type: " + type + "\n";
        s += "Response: " + (this.response == true ? "Yes" : "No") + "\n";
        s += "Hash: " + this.hash + "\n";
        s += "Length: " + this.length + "\n";
        s += "Data:   " + Conversion.bytesToHex(this.data) + "\n";
        s += "Header: " + Conversion.bytesToHex(new byte[]{
            this.rawMessage[0],
            this.rawMessage[1],
            this.rawMessage[2],
            this.rawMessage[3],
            this.rawMessage[4]

        }) + "\n";
        s += "Bin: " + Conversion.bytesToBin(rawMessage);

        return s;
    }

    /* Getters */
    /**
     * Get the raw message bytes
     *
     * @return
     */
    public byte[] getRawMessage()
    {
        return rawMessage;
    }

    /**
     * Get the priority
     *
     * @return
     */
    public Integer getPriority()
    {
        return priority;
    }

    /**
     * Get the command
     *
     * @return
     */
    public Integer getCommand()
    {
        return command;
    }

    /**
     * Get the hash
     *
     * @return
     */
    public Integer getHash()
    {
        return hash;
    }

    /**
     * Get the response bit
     *
     * @return
     */
    public Boolean getResponse()
    {
        return response;
    }

    /**
     * Get the data length
     *
     * @return
     */
    public Integer getLength()
    {
        return length;
    }

    /**
     * Get the data bytes
     *
     * @return
     */
    public byte[] getData()
    {
        return data;
    }
}
