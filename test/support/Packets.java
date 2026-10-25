package support;

import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.udp.CS2Message;

/**
 * The packets a Central Station puts on the network, built as the raw 13-byte frames the program reads off it.
 *
 * A test hands one to `MarklinControlStation.receiveMessage` and so acts as the railway: a sensor that makes or
 * breaks, a turnout thrown at the station, a locomotive driven from another controller, the power cut or restored.
 * Each frame goes through the parsing constructor `CS2Message(byte[])`, the one the network reader uses, so what the
 * test sends is read exactly as a frame off the wire is.
 *
 * Gathered from the frames three classes built for themselves (`rawAccessoryEcho`, `accessoryPacket`,
 * `fromTheCentralStation`), FR-136 Phase 6.
 *
 * @author Adam
 */
public final class Packets
{
    /** The hash a Central Station 2 or 3 answers with; the program does not read it. */
    private static final int HASH = CS2Message.CS2_PROTOCOL_V2;

    private Packets()
    {
    }

    /**
     * A sensor (s88 contact) changing state, as the station reports it.
     *
     * @param contact the contact number, as the diagram and the routes name it
     * @param occupied whether the contact is now made
     * @return the frame
     */
    public static CS2Message s88(int contact, boolean occupied)
    {
        return frame(CS2Message.CMD_ACC_SENSOR, true, new byte[]
        {
            0, 0, (byte) (contact >> 8), (byte) contact,
            (byte) (occupied ? 0 : 1), (byte) (occupied ? 1 : 0),
            0, 0
        });
    }

    /**
     * The station's answer to an accessory command: the position it put the accessory in.
     *
     * @param accessory the accessory
     * @param switched true for turned (red), false for straight (green)
     * @return the frame
     */
    public static CS2Message accessoryEcho(MarklinAccessory accessory, boolean switched)
    {
        return accessory(accessory, switched, true);
    }

    /**
     * An accessory command on the bus that is not the station's answer, as another controller sends one.
     *
     * @param accessory the accessory
     * @param switched true for turned (red), false for straight (green)
     * @return the frame
     */
    public static CS2Message accessoryCommand(MarklinAccessory accessory, boolean switched)
    {
        return accessory(accessory, switched, false);
    }

    /**
     * A locomotive's speed, as the station echoes it.
     *
     * @param locomotive the locomotive
     * @param stationSpeed the speed in the station's own units, 0 to 1000
     * @return the frame
     */
    public static CS2Message speedEcho(MarklinLocomotive locomotive, int stationSpeed)
    {
        int uid = locomotive.getIntUID();

        return frame(CS2Message.CMD_LOCO_VELOCITY, true, new byte[]
        {
            (byte) (uid >> 24), (byte) (uid >> 16), (byte) (uid >> 8), (byte) uid,
            (byte) (stationSpeed >> 8), (byte) stationSpeed
        });
    }

    /**
     * A locomotive's direction, as the station echoes it.
     *
     * @param locomotive the locomotive
     * @param forward the direction
     * @return the frame
     */
    public static CS2Message directionEcho(MarklinLocomotive locomotive, boolean forward)
    {
        int uid = locomotive.getIntUID();

        return frame(CS2Message.CMD_LOCO_DIRECTION, true, new byte[]
        {
            (byte) (uid >> 24), (byte) (uid >> 16), (byte) (uid >> 8), (byte) uid,
            (byte) (forward ? 1 : 2)
        });
    }

    /**
     * A locomotive function turned on or off, as the station echoes it.
     *
     * @param locomotive the locomotive
     * @param function the function number
     * @param on its state
     * @return the frame
     */
    public static CS2Message functionEcho(MarklinLocomotive locomotive, int function, boolean on)
    {
        int uid = locomotive.getIntUID();

        return frame(CS2Message.CMD_LOCO_FUNCTION, true, new byte[]
        {
            (byte) (uid >> 24), (byte) (uid >> 16), (byte) (uid >> 8), (byte) uid,
            (byte) function, (byte) (on ? 1 : 0)
        });
    }

    /**
     * The track power cut at the station (its Stop key).
     *
     * @return the frame
     */
    public static CS2Message powerStop()
    {
        return system(CS2Message.CMD_SYSSUB_STOP);
    }

    /**
     * The track power restored at the station (its Go key).
     *
     * @return the frame
     */
    public static CS2Message powerGo()
    {
        return system(CS2Message.CMD_SYSSUB_GO);
    }

    private static CS2Message system(int subCommand)
    {
        return frame(CS2Message.CMD_SYSTEM, true, new byte[] {0, 0, 0, 0, (byte) subCommand});
    }

    private static CS2Message accessory(MarklinAccessory accessory, boolean switched, boolean response)
    {
        int uid = accessory.getUID();

        return frame(CS2Message.CMD_ACC_SWITCH, response, new byte[]
        {
            (byte) (uid >> 24), (byte) (uid >> 16), (byte) (uid >> 8), (byte) uid,
            (byte) (switched ? 0 : 1), 1
        });
    }

    /**
     * The raw frame, laid out as the station sends it, read back through the network reader's own constructor.
     *
     * @param command the command
     * @param response whether the response bit is set, as it is on everything the station answers
     * @param data the payload, at most eight bytes
     * @return the parsed message
     */
    private static CS2Message frame(int command, boolean response, byte[] data)
    {
        byte[] raw = new byte[CS2Message.MESSAGE_LENGTH];

        raw[0] = (byte) ((command >> 7) & 0x01);
        raw[1] = (byte) (((command << 1) & 0xFE) | (response ? 1 : 0));
        raw[2] = (byte) (HASH >> 8);
        raw[3] = (byte) HASH;
        raw[4] = (byte) data.length;

        System.arraycopy(data, 0, raw, 5, data.length);

        return new CS2Message(raw);
    }
}
