package core;

import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.traincontrol.marklin.udp.CS2Message;

/**
 * Decoding tests for the CAN message header.
 *
 * A message is 13 bytes: P|P|P|P|_|_|_|C, C|C|C|C|C|C|C|R, H|H|H|H|H|H|H|H, H|H|H|H|H|H|H|H,
 * _|_|_|_|L|L|L|L, then an 8 byte payload.  Every field therefore has to be masked out of a signed
 * Java byte, and none of them were - the priority bits bled into the command, the second byte
 * sign-extended for commands at or above 0x40, the low byte of the hash wiped out the high byte, and
 * the length was neither masked nor bounded.
 *
 * Needs no model, no socket and no display, so it can run alongside anything.
 *
 * The four "real" cases below are packets captured from Central Stations on a live layout, taken from
 * the header and field values TrainControl logged for them.
 */
public class testCS2Message
{
    /**
     * Builds a 13 byte message from a 5 byte header and a payload.
     */
    private static byte[] packet(int[] header, int... payload)
    {
        byte[] raw = new byte[CS2Message.MESSAGE_LENGTH];

        for (int i = 0; i < header.length; i++)
        {
            raw[i] = (byte) header[i];
        }

        for (int i = 0; i < payload.length; i++)
        {
            raw[5 + i] = (byte) payload[i];
        }

        return raw;
    }

    /**
     * A ping with no payload.
     */
    @Test
    public void testDecodesRealPingRequest()
    {
        CS2Message m = new CS2Message(packet(new int[] {0x00, 0x36, 0x1f, 0x18, 0x00}));

        assertEquals((int) m.getPriority(), 0);
        assertEquals((int) m.getCommand(), 0x1b);
        assertFalse(m.getResponse());
        assertEquals((int) m.getHash(), 7960);
        assertEquals((int) m.getLength(), 0);
    }

    /**
     * A ping response whose hash has the high bit set in BOTH bytes - the case that used to decode as
     * -7395 rather than 58141.
     */
    @Test
    public void testDecodesRealPingResponseWithHighHashBytes()
    {
        CS2Message m = new CS2Message(packet(new int[] {0x00, 0x31, 0xe3, 0x1d, 0x08},
            0x63, 0x73, 0x40, 0xea, 0x11, 0x11, 0x00, 0x50));

        assertEquals((int) m.getCommand(), CS2Message.CAN_CMD_PING);
        assertTrue(m.getResponse(), "the response bit is the low bit of the second byte");
        assertEquals((int) m.getHash(), 58141, "0xE31D unsigned - it used to sign-extend to -7395");
        assertEquals((int) m.getLength(), 8);
        assertTrue(m.isPingCommand());
    }

    /**
     * A locomotive direction command, and the UID extraction that depends on it.
     */
    @Test
    public void testDecodesRealLocomotiveCommand()
    {
        CS2Message m = new CS2Message(packet(new int[] {0x00, 0x0a, 0x47, 0x11, 0x05},
            0x00, 0x00, 0x40, 0x69, 0x01));

        assertEquals((int) m.getCommand(), CS2Message.CMD_LOCO_DIRECTION);
        assertEquals((int) m.getHash(), 18193, "the v2 protocol hash, 0x4711");
        assertEquals((int) m.getLength(), 5);
        assertTrue(m.isLocCommand());

        // MFX_BASE + 0x69
        assertEquals(m.extractUID(), 0x4069);
        assertEquals(m.getSubCommand(), 0x01);
    }

    /**
     * An accessory command.
     */
    @Test
    public void testDecodesRealAccessoryCommand()
    {
        CS2Message m = new CS2Message(packet(new int[] {0x00, 0x16, 0x47, 0x11, 0x06},
            0x00, 0x00, 0x30, 0xfb, 0x00, 0x01));

        assertEquals((int) m.getCommand(), CS2Message.CMD_ACC_SWITCH);
        assertTrue(m.isAccessoryCommand());
        assertEquals((int) m.getLength(), 6);

        // MM2_BASE + 251, i.e. logical address 252
        assertEquals(m.extractUID(), 0x30fb);
        assertEquals(m.extractShortUID(), 0x30fb);
    }

    /**
     * The priority bits share the first byte with the top command bit, and must not leak into it.
     * Without the mask a non-zero priority produced a command in the thousands, which matched none of
     * the isXxxCommand tests - so the message would have been silently ignored.
     */
    @Test
    public void testPriorityDoesNotLeakIntoTheCommand()
    {
        // Priority 4, command 0x0b: first byte 0x40, second byte 0x0b << 1
        CS2Message m = new CS2Message(packet(new int[] {0x40, 0x16, 0x47, 0x11, 0x00}));

        assertEquals((int) m.getPriority(), 4);
        assertEquals((int) m.getCommand(), CS2Message.CMD_ACC_SWITCH,
            "the command must come from bit 0 of the first byte only");
        assertTrue(m.isAccessoryCommand());
    }

    /**
     * A command at or above 0x40 puts the high bit of the second byte to use, which used to
     * sign-extend the whole value negative.
     */
    @Test
    public void testCommandAboveSeveralBitsDecodes()
    {
        // Command 0x40 -> second byte 0x80
        CS2Message m = new CS2Message(packet(new int[] {0x00, 0x80, 0x47, 0x11, 0x00}));

        assertEquals((int) m.getCommand(), 0x40);
    }

    /**
     * A hash whose low byte has the high bit set: the low byte used to overwrite the high one entirely,
     * so 0xE39D came out as -99.
     */
    @Test
    public void testHashLowByteDoesNotOverwriteTheHighByte()
    {
        CS2Message m = new CS2Message(packet(new int[] {0x00, 0x36, 0xe3, 0x9d, 0x00}));

        assertEquals((int) m.getHash(), 0xE39D);
    }

    /**
     * Only the low nibble of the fifth byte is the payload length, and it must not be able to run past
     * the 8 byte payload however corrupted it is.
     */
    @Test
    public void testLengthIsMaskedAndBounded()
    {
        assertEquals((int) new CS2Message(packet(new int[] {0x00, 0x36, 0x47, 0x11, 0xf3})).getLength(), 3,
            "the high nibble is not part of the length");

        assertEquals((int) new CS2Message(packet(new int[] {0x00, 0x36, 0x47, 0x11, 0x0f})).getLength(), 8,
            "a length beyond the payload is clamped rather than overrunning it");

        assertEquals((int) new CS2Message(packet(new int[] {0x00, 0x36, 0x47, 0x11, 0xff})).getLength(), 8);
    }

    /**
     * The network reader reuses a single buffer for every packet, so a message must not keep a
     * reference to it - the raw bytes would change underneath it as soon as the next packet arrived.
     */
    @Test
    public void testMessageDoesNotAliasTheSourceBuffer()
    {
        byte[] buffer = packet(new int[] {0x00, 0x0a, 0x47, 0x11, 0x05}, 0x00, 0x00, 0x40, 0x69, 0x01);

        CS2Message m = new CS2Message(buffer);

        byte[] before = m.getRawMessage().clone();

        // What the reader does on the very next packet
        java.util.Arrays.fill(buffer, (byte) 0x7f);

        assertEquals(m.getRawMessage(), before, "the message must hold its own copy of the bytes");
        assertEquals((int) m.getCommand(), CS2Message.CMD_LOCO_DIRECTION);
        assertEquals(m.extractUID(), 0x4069);
    }

    /**
     * A hash survives a round trip through the outgoing constructor, unsigned, so that a constructed
     * message and a parsed one carrying the same hash compare equal.
     */
    @Test
    public void testHashRoundTripsUnsigned()
    {
        CS2Message constructed = new CS2Message(CS2Message.CMD_ACC_SWITCH, 0xE31D, new byte[] {1, 2, 3, 4, 5, 6});

        assertEquals((int) constructed.getHash(), 0xE31D);

        CS2Message parsed = new CS2Message(constructed.getRawMessage());

        assertEquals(parsed.getHash(), constructed.getHash(),
            "a parsed and a constructed message with one hash must agree");
        assertEquals(parsed.getCommand(), constructed.getCommand());
        assertEquals(parsed.getLength(), constructed.getLength());
    }

    /**
     * The sub command is the fifth payload byte, so five are needed to read it.
     */
    @Test
    public void testSubCommandNeedsFivePayloadBytes()
    {
        CS2Message enough = new CS2Message(CS2Message.CMD_SYSTEM,
            new byte[] {0, 0, 0, 0, CS2Message.CMD_SYSSUB_GO});

        assertEquals(enough.getSubCommand(), CS2Message.CMD_SYSSUB_GO);

        CS2Message tooShort = new CS2Message(CS2Message.CMD_SYSTEM, new byte[] {0, 0, 0, 0});

        assertEquals(tooShort.getSubCommand(), -1, "four bytes cannot carry a sub command");
    }
}
