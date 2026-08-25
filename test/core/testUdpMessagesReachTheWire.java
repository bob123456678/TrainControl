package core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.NetworkProxy;

/**
 * A message handed to the proxy arrives on the wire, byte for byte.
 *
 * TA-B7, from the test suite audit, inside [OB-089]: "nothing anywhere asserts an outgoing datagram".
 * The existing `testNetworkProxy` covers the socket's LIFECYCLE well - a transient receive error does
 * not stop the listener, closing stops it, a send reopens a closed socket - and `testSendReopensAClosedSocket`
 * asserts that `sendMessage` returned true and that the socket is open afterwards. Neither of those
 * says anything about what was sent, or that anything was sent at all.
 *
 * That gap is worth closing on its own terms - this is the one path in the application that reaches
 * the physical railway, and "returned true" is a statement about a method, not about a locomotive
 * moving. It is worth closing NOW because `e45d7241` changed the socket this sends through: the
 * receive port is now optionally ephemeral, opened through a new shared method used by both the
 * constructor and the reopen inside `sendMessage`. Nothing in the suite would have noticed if that
 * change had broken transmission, because nothing in the suite has ever read a byte off a socket.
 *
 * **No model, no layout, no fixture.** This constructs a `NetworkProxy` directly against loopback, so
 * it does not load the sample layout and does not care whether TrainControl is running. That is
 * deliberate: it keeps the one test that proves bytes leave the machine independent of everything
 * that has made the rest of the battery fragile.
 */
public class testUdpMessagesReachTheWire
{
    /** Where a Central Station listens, and therefore where the proxy transmits. */
    private static final int TRANSMIT_PORT = 15731;

    private static DatagramSocket station;
    private static NetworkProxy proxy;

    @BeforeClass
    public static void setUpClass() throws Exception
    {
        // Standing in for the Central Station: bound where one would be, on loopback.
        station = new DatagramSocket(TRANSMIT_PORT, InetAddress.getByName("127.0.0.1"));
        station.setSoTimeout(4000);

        proxy = new NetworkProxy(InetAddress.getByName("127.0.0.1"));
    }

    @AfterClass
    public static void tearDownClass()
    {
        if (proxy != null) proxy.stopListening();
        if (station != null && !station.isClosed()) station.close();
    }

    /**
     * The bytes that arrive are the bytes the message is made of.
     *
     * Compared against the message's OWN serialisation rather than against a hand-written array. A
     * literal here would be a second statement of the CAN frame format, and the two would drift - the
     * DR family of findings is entirely about that. What is being tested is the transmission path, not
     * the encoding, and this asserts exactly that: whatever `getRawMessage` says, that is what left.
     */
    @Test
    public void testAPingArrivesUnchanged() throws Exception
    {
        CS2Message ping = new CS2Message(CS2Message.CAN_CMD_PING, new byte[0]);

        byte[] expected = ping.getRawMessage();

        assertEquals(expected.length, CS2Message.MESSAGE_LENGTH,
            "a CAN frame is " + CS2Message.MESSAGE_LENGTH + " bytes; this fixture is not one");

        assertTrue(proxy.sendMessage(ping), "the proxy reported that the send failed");

        DatagramPacket arrived = new DatagramPacket(new byte[64], 64);

        station.receive(arrived);

        assertEquals(arrived.getLength(), expected.length,
            "the datagram that arrived is " + arrived.getLength() + " bytes, not "
            + expected.length + ".  A short frame is not a partial command to a Central Station - it "
            + "is a different one");

        assertEquals(Arrays.copyOf(arrived.getData(), arrived.getLength()), expected,
            "the bytes on the wire are not the bytes of the message.  This is the one path in the "
            + "application that reaches the physical railway, and until now nothing asserted that "
            + "anything at all came out of it (TA-B7)");
    }

    /**
     * And a second one, after the socket has been closed underneath it.
     *
     * `sendMessage` reopens a closed socket, and since `e45d7241` it does so through the same method
     * the constructor uses - so the reopened socket may be on a different local port than the first.
     * The existing lifecycle test asserts the reopen happened; this asserts the reopened socket still
     * TRANSMITS, which is the part that matters and the part that would have broken silently.
     */
    @Test
    public void testASendStillArrivesAfterTheSocketIsReopened() throws Exception
    {
        // Drain anything the first test left, so what is read below is this test's own.
        drain();

        closeTheProxySocket();

        CS2Message ping = new CS2Message(CS2Message.CAN_CMD_PING, new byte[0]);

        assertTrue(proxy.sendMessage(ping),
            "sending after the socket was closed should reopen it and succeed");

        DatagramPacket arrived = new DatagramPacket(new byte[64], 64);

        station.receive(arrived);

        assertEquals(Arrays.copyOf(arrived.getData(), arrived.getLength()), ping.getRawMessage(),
            "nothing usable arrived after the socket was reopened.  The reopen is only worth having "
            + "if what follows it reaches the railway");
    }

    /**
     * Closes the proxy's socket without going through the proxy, the way a network fault would.
     */
    private static void closeTheProxySocket() throws Exception
    {
        java.lang.reflect.Field field = NetworkProxy.class.getDeclaredField("socket");

        field.setAccessible(true);

        ((DatagramSocket) field.get(proxy)).close();
    }

    private static void drain() throws Exception
    {
        station.setSoTimeout(50);

        try
        {
            while (true) station.receive(new DatagramPacket(new byte[64], 64));
        }
        catch (java.io.IOException nothingLeft)
        {
            // Expected: the timeout is how "nothing more to read" arrives.
        }
        finally
        {
            station.setSoTimeout(4000);
        }
    }
}
