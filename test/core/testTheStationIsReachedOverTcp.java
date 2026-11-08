package core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.model.ModelListener;
import org.traincontrol.util.I18n;
import support.Components;
import support.Packets;
import support.TcpStation;

/**
 * The CAN frames go over TCP when the station accepts a connection, and over UDP when it does not or when the
 * connection is lost for good (FR-186).  Each claim drives a real `NetworkProxy` against `support.TcpStation` and
 * reads what reached the model it serves, what reached the station, and what was logged.
 *
 * The proxy's UDP socket is on any port (the battery's anyReceivePort), and a datagram sent to it stands in for the
 * station's UDP broadcast.  No claim sends over UDP, so nothing reaches 15731 on this machine.
 *
 * @author Adam
 */
public class testTheStationIsReachedOverTcp
{
    private static final long WAIT_MS = 5000;

    private NetworkProxy proxy;

    private TcpStation station;

    private Model model;

    /** A model that keeps what it is handed and the keys it is asked to log. */
    private static final class Model implements ModelListener
    {
        final List<byte[]> heard = Collections.synchronizedList(new ArrayList<>());
        final List<String> keys = Collections.synchronizedList(new ArrayList<>());
        final List<String> lines = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void receiveMessage(CANMessage message)
        {
            this.heard.add(((CS2Message) message).getRawMessage());
        }

        @Override
        public byte[] initMessageBuffer()
        {
            return new byte[CS2Message.MESSAGE_LENGTH];
        }

        @Override
        public CANMessage createMessage(byte[] raw)
        {
            return new CS2Message(raw);
        }

        @Override
        public void logf(String key, Object... args)
        {
            this.keys.add(key);
            this.lines.add(I18n.f(key, args));
        }

        @Override
        public void log(String message)
        {
            this.lines.add(message);
        }

        @Override
        public void log(Exception e)
        {
            this.lines.add(String.valueOf(e));
        }

        int count(String key)
        {
            return Collections.frequency(new ArrayList<>(this.keys), key);
        }

        boolean heardFrame(byte[] frame)
        {
            synchronized (this.heard)
            {
                for (byte[] h : this.heard) if (Arrays.equals(h, frame)) return true;
            }

            return false;
        }
    }

    @BeforeMethod
    public void setUp()
    {
        this.model = new Model();
        this.proxy = null;
        this.station = null;
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception
    {
        if (this.proxy != null) this.proxy.stopListening();
        if (this.station != null) this.station.close();
    }

    private NetworkProxy connect(int port) throws Exception
    {
        this.proxy = new NetworkProxy(InetAddress.getByName("127.0.0.1"), port);
        this.proxy.setModel(this.model);
        return this.proxy;
    }

    /** A frame as the station would send it: a sensor event. */
    private static byte[] sensor(int contact, boolean occupied)
    {
        return Packets.s88(contact, occupied).getRawMessage();
    }

    private static byte[] answerTo(byte[] frame)
    {
        byte[] answer = Arrays.copyOf(frame, frame.length);
        answer[1] |= 1;
        return answer;
    }

    private DatagramSocket udpSocket() throws Exception
    {
        java.lang.reflect.Field field = NetworkProxy.class.getDeclaredField("socket");
        field.setAccessible(true);
        return (DatagramSocket) field.get(this.proxy);
    }

    /** Sends a datagram to the proxy's UDP socket, as the station's broadcast would arrive. */
    private void broadcast(byte[] frame) throws Exception
    {
        int port = udpSocket().getLocalPort();

        try (DatagramSocket out = new DatagramSocket())
        {
            out.send(new DatagramPacket(frame, frame.length, InetAddress.getByName("127.0.0.1"), port));
        }
    }

    /** The datagram waiting in the proxy's UDP socket, read here; empty when none comes within WAIT_MS. */
    private byte[] unreadDatagram() throws Exception
    {
        DatagramSocket socket = udpSocket();
        DatagramPacket packet = new DatagramPacket(new byte[64], 64);
        socket.setSoTimeout((int) WAIT_MS);

        try
        {
            socket.receive(packet);
            return Arrays.copyOf(packet.getData(), packet.getLength());
        }
        catch (java.net.SocketTimeoutException none)
        {
            return new byte[0];
        }
    }

    private boolean logged(String key, long ms)
    {
        return Components.waitFor(() -> this.model.count(key) > 0, ms);
    }

    /** Connected to a station that answers, over a connection that has carried a frame, so a loss is retried. */
    private void connectedAndAnswered() throws Exception
    {
        this.station = new TcpStation().answering(true);
        connect(this.station.port());

        CS2Message ping = new CS2Message(CS2Message.CAN_CMD_PING, new byte[0]);
        assertTrue(this.proxy.sendMessage(ping), "the ping was not sent");
        assertTrue(Components.waitFor(() -> this.model.heardFrame(answerTo(ping.getRawMessage())), WAIT_MS),
            "the station's answer to a ping never reached the model: " + this.model.lines);
    }

    /**
     * A station that takes a connection is reached over it: the ping arrives there byte for byte, its answer reaches
     * the model, and the log says TCP.
     *
     * MUTATION: in the `NetworkProxy(InetAddress, int)` constructor, `if (tcpPort > 0)` to `if (tcpPort < 0)`.
     */
    @Test(timeOut = 30000)
    public void testTcpIsUsedWhenTheStationAcceptsIt() throws Exception
    {
        // Sends a ping and waits for the station's answer to reach the model
        connectedAndAnswered();

        assertEquals(this.station.received().size(), 1, "the station received other than the one ping");
        assertEquals(this.station.received().get(0).bytes,
            new CS2Message(CS2Message.CAN_CMD_PING, new byte[0]).getRawMessage(),
            "the frame on the TCP connection is not the frame UDP would carry");
        assertEquals(this.model.count("network.tcpConnected"), 1, "the log does not say TCP: " + this.model.lines);
        assertEquals(this.model.count("network.tcpUnavailable"), 0, "the log says UDP: " + this.model.lines);
    }

    /**
     * Frames are reassembled at 13 bytes however the stream is cut: a frame split over two writes, and the rest of it
     * with a whole frame and part of the next in one, reach the model as whole frames, once each, in order.  Each piece
     * starts with the end of a frame, and the next piece is written only once that frame was heard, so every cut falls
     * between two reads.
     *
     * MUTATION: in `NetworkProxy.readUntilLost`, `filled += read;` to `filled = read;`.
     */
    @Test(timeOut = 30000)
    public void testFramesCutAnywhereInTheStreamAreReassembled() throws Exception
    {
        this.station = new TcpStation();
        connect(this.station.port());

        byte[][] frames = {sensor(1001, true), sensor(1002, true), sensor(1001, false), sensor(1002, false)};
        byte[] stream = new byte[frames.length * CS2Message.MESSAGE_LENGTH];

        for (int i = 0; i < frames.length; i++)
        {
            System.arraycopy(frames[i], 0, stream, i * CS2Message.MESSAGE_LENGTH, CS2Message.MESSAGE_LENGTH);
        }

        // The first frame and 5 bytes of the second; the rest of it, the third and 2 bytes of the fourth; the rest
        int[] cuts = {0, 18, 41, stream.length};

        // The frame each piece completes: the first, the third, the fourth
        int[] completes = {0, 2, 3};

        for (int i = 0; i + 1 < cuts.length; i++)
        {
            this.station.send(Arrays.copyOfRange(stream, cuts[i], cuts[i + 1]));

            byte[] whole = frames[completes[i]];
            assertTrue(Components.waitFor(() -> this.model.heardFrame(whole), WAIT_MS),
                "frame " + (completes[i] + 1) + " never reached the model whole: " + this.model.heard.size() + " heard");
        }

        // Every byte written is now read: nothing more can come
        List<byte[]> heard = new ArrayList<>(this.model.heard);
        assertEquals(heard.size(), frames.length, "the model was handed " + heard.size() + " frames for " + frames.length);

        for (int i = 0; i < frames.length; i++)
        {
            assertEquals(heard.get(i), frames[i], "frame " + (i + 1) + " reached the model changed");
        }
    }

    /**
     * A station that does not take a connection is reached over UDP, as before, and the log says so once, with the
     * reason.  A closed port on Windows loopback times out (about 2 s) rather than refusing.
     *
     * MUTATION: in `NetworkProxy.setModel`, take out `model.logf("network.tcpUnavailable", this.tcpRefusal);`.
     */
    @Test(timeOut = 30000)
    public void testAStationThatRefusesTcpIsReachedOverUdp() throws Exception
    {
        connect(TcpStation.closedPort());
        assertReachedOverUdpAndSaidOnce();
        assertEquals(this.model.count("network.tcpConnected"), 0, "the log says TCP: " + this.model.lines);
    }

    /**
     * The same for an address a connection cannot even be tried to, which fails at once.  Nothing is sent: the
     * address is the limited broadcast.
     *
     * MUTATION: in the `NetworkProxy(InetAddress, int)` constructor, take out `this.tcpRefusal = reasonOf(e);`.
     */
    @Test(timeOut = 30000)
    public void testAnUnreachableStationIsReachedOverUdp() throws Exception
    {
        this.proxy = new NetworkProxy(InetAddress.getByName("255.255.255.255"), NetworkProxy.TCP_PORT);
        this.proxy.setModel(this.model);
        assertReachedOverUdpAndSaidOnce();
    }

    private void assertReachedOverUdpAndSaidOnce() throws Exception
    {
        assertReachedOverUdp();
        assertEquals(this.model.count("network.tcpUnavailable"), 1,
            "the fallback to UDP is not said exactly once: " + this.model.lines);
    }

    /** A UDP listener runs, and a frame the station broadcasts reaches the model. */
    private void assertReachedOverUdp() throws Exception
    {
        assertTrue(logged("network.canListenerRunning", WAIT_MS), "no UDP listener started: " + this.model.lines);

        byte[] frame = sensor(1003, true);
        broadcast(frame);

        assertTrue(Components.waitFor(() -> this.model.heardFrame(frame), WAIT_MS),
            "a frame the station broadcast over UDP never reached the model: deaf");
    }

    /**
     * Over TCP the UDP socket is not read, or every frame would arrive twice: a broadcast is still waiting in it,
     * unread, after a frame sent later over TCP has been heard, and only the TCP frame reached the model.
     *
     * MUTATION: in `NetworkProxy.ReadMessages.run`, `if (tcp != null)` to `if (false)`.
     */
    @Test(timeOut = 30000)
    public void testOverTcpNoFrameArrivesTwice() throws Exception
    {
        this.station = new TcpStation();
        connect(this.station.port());
        assertTrue(Components.waitFor(() -> this.station.connections() == 1, WAIT_MS), "precondition: no connection");

        // Two different frames, so that which way each came is known
        byte[] overUdp = sensor(1005, true);
        byte[] overTcp = sensor(1005, false);
        broadcast(overUdp);
        this.station.send(overTcp);

        assertTrue(Components.waitFor(() -> this.model.heardFrame(overTcp), WAIT_MS), "the frame over TCP never arrived");
        assertTrue(Arrays.equals(unreadDatagram(), overUdp), "over TCP the UDP socket was read too, so frames arrive twice");
        assertFalse(this.model.heardFrame(overUdp), "over TCP the UDP socket was read too, so frames arrive twice");
        assertEquals(this.model.heard.size(), 1,
            "the station's one frame reached the model " + this.model.heard.size() + " times");
    }

    /**
     * A connection the station closes is replaced by a new one, and both are logged; what is sent after goes on the
     * new connection and its answer is heard.
     *
     * MUTATION: in `NetworkProxy.recover`, `boolean retry = this.carried;` to `boolean retry = false;`.
     */
    @Test(timeOut = 30000)
    public void testALostConnectionIsReplaced() throws Exception
    {
        connectedAndAnswered();

        this.station.drop();

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);
        assertEquals(this.model.count("network.tcpLostReconnecting"), 1, "the loss is not logged: " + this.model.lines);
        assertEquals(this.station.connections(), 2, "the station was not connected to again");

        byte[] frame = new CS2Message(CS2Message.CAN_CMD_PING, new byte[0]).getRawMessage();
        this.model.heard.clear();
        this.proxy.sendMessage(new CS2Message(frame));

        assertTrue(Components.waitFor(() -> this.station.receivedOn(2).size() >= 1, WAIT_MS),
            "a send after the reconnect never reached the station");
        assertTrue(Components.waitFor(() -> this.model.heardFrame(answerTo(frame)), WAIT_MS),
            "the answer on the new connection never reached the model");
    }

    /**
     * When no new connection can be made, UDP takes over for the rest of the session, logged, and the station's
     * broadcasts are heard again.  Three tries of up to 2 s each, after pauses of 1, 2 and 3 s.
     *
     * MUTATION: in `NetworkProxy.recover`, put `if (true) return;` before the block that sets `this.tcp = null`.
     */
    @Test(timeOut = 60000)
    public void testAConnectionThatCannotBeReplacedFallsBackToUdp() throws Exception
    {
        connectedAndAnswered();

        this.station.close();

        assertTrue(logged("network.tcpReconnectFailed", 30000), "no fallback to UDP: " + this.model.lines);
        assertReachedOverUdp();
    }

    /**
     * A command sent as the connection is lost is not lost with it: it is sent again on the new connection.
     *
     * MUTATION: in `NetworkProxy.takeUnconfirmed`, `if (u.at - this.lastHeardAt >= 0) again.add(u);` to
     * `if (false) again.add(u);`.
     */
    @Test(timeOut = 30000)
    public void testACommandSentAsTheConnectionIsLostIsSentAgain() throws Exception
    {
        connectedAndCarried();

        byte[] command = Packets.powerStop().getRawMessage();

        this.station.drop();
        this.proxy.sendMessage(new CS2Message(command));

        assertSentOnTheNewConnection(command, 15000);
    }

    /** Connected to a station that never answers, over a connection that has carried a frame, so a loss is retried. */
    private void connectedAndCarried() throws Exception
    {
        this.station = new TcpStation();
        connect(this.station.port());

        this.station.send(sensor(1007, true));
        assertTrue(Components.waitFor(() -> this.model.heard.size() == 1, WAIT_MS), "precondition: nothing carried");
    }

    /** A new connection is made, and the frame arrives on it. */
    private void assertSentOnTheNewConnection(byte[] frame, long ms)
    {
        assertTrue(logged("network.tcpReconnected", ms), "no new connection: " + this.model.lines);
        assertTrue(Components.waitFor(() ->
            this.station.receivedOn(2).stream().anyMatch(f -> Arrays.equals(f, frame)), WAIT_MS),
            "the frame the station may have missed never reached it on the new connection: " + this.model.lines);
    }

    /**
     * A connection that hangs, open but carrying nothing, is found by a send that goes unanswered for 15 s, and
     * replaced; the unanswered frame is sent again on the new connection.
     *
     * MUTATION: in `NetworkProxy.readUntilLost`, take out `return I18n.f("network.tcpNoAnswer", ...)`.
     */
    @Test(timeOut = 60000)
    public void testAHungConnectionIsFoundAndReplaced() throws Exception
    {
        connectedAndCarried();

        byte[] ping = new CS2Message(CS2Message.CAN_CMD_PING, new byte[0]).getRawMessage();
        this.proxy.sendMessage(new CS2Message(ping));

        assertSentOnTheNewConnection(ping, 30000);
        assertTrue(this.model.lines.stream().anyMatch(l -> l.contains(I18n.f("network.tcpNoAnswer", 15))),
            "the log does not say why: " + this.model.lines);
    }

    /**
     * Stopping closes the connection, so the station sees it end, and ends the listener.
     *
     * MUTATION: in `NetworkProxy.stopListening`, take out `closeQuietly(this.tcp);`.
     */
    @Test(timeOut = 30000)
    public void testStoppingClosesTheConnectionAndEndsTheListener() throws Exception
    {
        this.station = new TcpStation();
        connect(this.station.port());
        assertTrue(logged("network.canListenerRunningTcp", WAIT_MS), "precondition: no TCP listener");

        java.lang.reflect.Field field = NetworkProxy.class.getDeclaredField("reader");
        field.setAccessible(true);
        Thread reader = (Thread) field.get(this.proxy);

        this.proxy.stopListening();

        assertTrue(Components.waitFor(() -> this.station.ended(1), WAIT_MS), "the station's connection is still open");
        reader.join(WAIT_MS);
        assertFalse(reader.isAlive(), "the listener thread is still running");
        assertEquals(this.model.count("network.canListenerClosed"), 1, "the end is not logged: " + this.model.lines);
    }
}
