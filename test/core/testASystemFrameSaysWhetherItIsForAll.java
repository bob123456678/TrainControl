package core;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.udp.CS2Message;

/**
 * A system frame says whether it was addressed to every device (UID 0) or to one.  Adam's CS3 plus answered each stop
 * and go twice in the same millisecond, once to all and once to itself (UID 0x637340ea); the frames below are copied
 * from that capture.  Nothing reads the answer yet, so frame handling is unchanged.
 *
 * MUTATION: return false from isAddressedToAll, and the frames to all fail.
 *
 * @author Adam
 */
public class testASystemFrameSaysWhetherItIsForAll
{
    private static CS2Message frame(String hex)
    {
        byte[] raw = new byte[CS2Message.MESSAGE_LENGTH];

        for (int i = 0; i < raw.length; i++)
        {
            raw[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }

        return new CS2Message(raw);
    }

    @Test
    public void testAStopAndAGoToAll()
    {
        CS2Message stop = frame("0001e31d050000000000000000");
        CS2Message go = frame("0001e31d050000000001000000");

        assertTrue(stop.isAddressedToAll(), "the stop to all");
        assertTrue(go.isAddressedToAll(), "the go to all");
        assertEquals(stop.getSubCommand(), CS2Message.CMD_SYSSUB_STOP);
        assertEquals(go.getSubCommand(), CS2Message.CMD_SYSSUB_GO);
    }

    @Test
    public void testAStopAndAGoToTheStationItself()
    {
        assertFalse(frame("0001e31d05637340ea00000000").isAddressedToAll(), "the stop to the CS3");
        assertFalse(frame("0001e31d05637340ea01000000").isAddressedToAll(), "the go to the CS3");
    }

    @Test
    public void testOnlySystemFramesAreAsked()
    {
        assertFalse(frame("0017e31d080000000000000000").isAddressedToAll(), "an accessory echo, UID 0, is not a system frame");
        assertFalse(frame("0001e31d030000000000000000").isAddressedToAll(), "a system frame too short to hold a UID");
    }
}
