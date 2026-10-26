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
 * From the test suite audit, inside [OB-089]: "nothing anywhere asserts an outgoing datagram".  `testNetworkProxy`
 * covers the socket's LIFECYCLE and `testSendReopensAClosedSocket` asserts that `sendMessage` returned true, but
 * neither says anything about what was sent.  This is the one path that reaches the physical railway, and "returned
 * true" is a statement about a method, not about a locomotive moving; the receive port is now optionally ephemeral and
 * opened through a shared method used by the constructor and the reopen inside `sendMessage`, which nothing would have
 * noticed breaking transmission.
 *
 * No model, no layout, no fixture: a `NetworkProxy` is constructed directly against loopback, independent of everything
 * that makes the rest of the battery fragile.
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

    @AfterClass(alwaysRun = true)
    public static void tearDownClass()
    {
        if (proxy != null) proxy.stopListening();
        if (station != null && !station.isClosed()) station.close();
    }

    /**
     * The bytes that arrive are the bytes the message is made of: compared against the message's OWN serialization, not
     * a hand-written array (a second statement of the CAN frame format that would drift).  What is tested is the
     * transmission path, not the encoding.
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
     * And a second one, after the socket has been closed underneath it: `sendMessage` reopens a closed socket through
     * the constructor's method, so the reopened socket may be on a different local port.  The lifecycle test asserts
     * the reopen happened; this asserts the reopened socket still TRANSMITS.
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
