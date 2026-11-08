package core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
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
 * A CS3's CAN frames go over TCP once the proxy is asked, and over UDP when the station does not take the connection
 * or it is lost for good (FR-186).  Each claim drives a real `NetworkProxy` against `support.TcpStation` and reads
 * what reached the model it serves, what reached the station over TCP and over UDP, and what was logged.
 *
 * The station is on a loopback address of this class's own, so a claim can take UDP 15731 there to read what the
 * proxy sends over UDP without a clash with another test.  The proxy's UDP socket is on any port (the battery's
 * anyReceivePort), and a datagram sent to it stands in for the station's UDP broadcast.
 *
 * @author Adam
 */
public class testTheStationIsReachedOverTcp
{
    private static final long WAIT_MS = 5000;

    private static final String STATION = "127.0.0.86";

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

        int times(byte[] frame)
        {
            int n = 0;

            synchronized (this.heard)
            {
                for (byte[] h : this.heard) if (Arrays.equals(h, frame)) n++;
            }

            return n;
        }

        boolean heardFrame(byte[] frame)
        {
            return times(frame) > 0;
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

    /** A proxy for the station's address, with the model set, asked to connect over TCP to a port there. */
    private NetworkProxy connect(String address, int port) throws Exception
    {
        this.proxy = new NetworkProxy(InetAddress.getByName(address));
        this.proxy.setModel(this.model);
        this.proxy.connectOverTcp(port);
        return this.proxy;
    }

    private NetworkProxy connect(int port) throws Exception
    {
        return connect(STATION, port);
    }

    /** A frame as the station would send it: a sensor event. */
    private static byte[] sensor(int contact, boolean occupied)
    {
        return Packets.s88(contact, occupied).getRawMessage();
    }

    private static byte[] ping(int tag)
    {
        return new CS2Message(CS2Message.CAN_CMD_PING, new byte[] {(byte) tag}).getRawMessage();
    }

    private static byte[] answerTo(byte[] frame)
    {
        byte[] answer = Arrays.copyOf(frame, frame.length);
        answer[1] |= 1;
        return answer;
    }

    private Object field(String name) throws Exception
    {
        java.lang.reflect.Field field = NetworkProxy.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(this.proxy);
    }

    /** Sends a datagram to the proxy's UDP socket, as the station's broadcast would arrive. */
    private void broadcast(byte[] frame) throws Exception
    {
        int port = ((DatagramSocket) field("socket")).getLocalPort();

        try (DatagramSocket out = new DatagramSocket())
        {
            out.send(new DatagramPacket(frame, frame.length, InetAddress.getByName("127.0.0.1"), port));
        }
    }

    /** Broadcasts a frame and waits until the proxy holds it, as it does every UDP frame while TCP runs. */
    private void broadcastHeld(byte[] frame) throws Exception
    {
        Object lock = field("udpLock");
        java.util.Collection<?> held = (java.util.Collection<?>) field("held");
        int before;

        synchronized (lock)
        {
            before = held.size();
        }

        broadcast(frame);

        assertTrue(Components.waitFor(() ->
        {
            synchronized (lock)
            {
                return held.size() > before;
            }
        }, WAIT_MS), "the proxy did not hold the broadcast frame: " + this.model.lines);
    }

    private boolean logged(String key, long ms)
    {
        return Components.waitFor(() -> this.model.count(key) > 0, ms);
    }

    /** Connected to a station that answers, over a connection that has carried a frame, so a loss is retried. */
    private void connectedAndAnswered() throws Exception
    {
        this.station = new TcpStation(STATION).answering(true);
        connect(this.station.port());

        CS2Message ping = new CS2Message(CS2Message.CAN_CMD_PING, new byte[0]);
        assertTrue(this.proxy.sendMessage(ping), "the ping was not sent");
        assertTrue(Components.waitFor(() -> this.model.heardFrame(answerTo(ping.getRawMessage())), WAIT_MS),
            "the station's answer to a ping never reached the model: " + this.model.lines);
    }

    /** Connected to a station that never answers, over a connection that has carried a frame, so a loss is retried. */
    private void connectedAndCarried() throws Exception
    {
        this.station = new TcpStation(STATION);
        connect(this.station.port());

        this.station.send(sensor(1007, true));
        assertTrue(Components.waitFor(() -> this.model.heard.size() == 1, WAIT_MS), "precondition: nothing carried");
    }

    /** UDP 15731 at the station's address: what the proxy sends over UDP. */
    private static DatagramSocket udpWire() throws Exception
    {
        return new DatagramSocket(new InetSocketAddress(InetAddress.getByName(STATION), NetworkProxy.TX_PORT));
    }

    /** The next datagram on the wire, or null when none comes within `ms`. */
    private static byte[] nextOn(DatagramSocket wire, long ms) throws Exception
    {
        DatagramPacket packet = new DatagramPacket(new byte[64], 64);
        wire.setSoTimeout((int) ms);

        try
        {
            wire.receive(packet);
            return Arrays.copyOf(packet.getData(), packet.getLength());
        }
        catch (SocketTimeoutException none)
        {
            return null;
        }
    }

    /**
     * A station that takes a connection is reached over it: the ping arrives there byte for byte, its answer reaches
     * the model, and the log says TCP.
     *
     * MUTATION: in `NetworkProxy.connectOverTcp`, `if (port <= 0 || ...` to `if (port > 0 || ...`.
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
        this.station = new TcpStation(STATION);
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
     * MUTATION: in `NetworkProxy.startTcp`, take out `model.logf("network.tcpUnavailable", reasonOf(e));`.
     */
    @Test(timeOut = 30000)
    public void testAStationThatRefusesTcpIsReachedOverUdp() throws Exception
    {
        connect("127.0.0.1", TcpStation.closedPort());
        assertReachedOverUdpAndSaidOnce();
        assertEquals(this.model.count("network.tcpConnected"), 0, "the log says TCP: " + this.model.lines);
    }

    /**
     * The same for an address a connection cannot even be tried to, which fails at once.  Nothing is sent: the
     * address is the limited broadcast.
     *
     * MUTATION: as testAStationThatRefusesTcpIsReachedOverUdp.
     */
    @Test(timeOut = 30000)
    public void testAnUnreachableStationIsReachedOverUdp() throws Exception
    {
        connect("255.255.255.255", NetworkProxy.TCP_PORT);
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
     * Over TCP the UDP copy of a frame is held, not delivered, or every frame would arrive twice: a frame broadcast and
     * also sent over TCP reaches the model once, and a frame only broadcast not at all while TCP runs.
     *
     * MUTATION: in `NetworkProxy.enterHolding` and `isMarker`, `this.holding = true;` to `this.holding = false;`.
     */
    @Test(timeOut = 30000)
    public void testOverTcpNoFrameArrivesTwice() throws Exception
    {
        this.station = new TcpStation(STATION);
        connect(this.station.port());
        assertTrue(logged("network.tcpConnected", WAIT_MS), "precondition: no connection: " + this.model.lines);

        byte[] both = sensor(1005, true);
        byte[] udpOnly = sensor(1006, true);
        broadcastHeld(both);
        broadcastHeld(udpOnly);
        this.station.send(both);

        assertTrue(Components.waitFor(() -> this.model.heardFrame(both), WAIT_MS), "the frame over TCP never arrived");
        assertEquals(this.model.times(both), 1, "a frame sent both ways reached the model twice");
        assertFalse(this.model.heardFrame(udpOnly), "over TCP a UDP frame reached the model as well");
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

        byte[] frame = ping(2);
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
     * MUTATION: in `NetworkProxy.takeUnconfirmed`, `if (u.at + CONFIRM_MARGIN_NS - this.lastHeardAt >= 0)` to
     * `if (false)`.
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

        byte[] ping = ping(1);
        this.proxy.sendMessage(new CS2Message(ping));

        assertSentOnTheNewConnection(ping, 30000);
        assertTrue(this.model.lines.stream().anyMatch(l -> l.contains(I18n.f("network.tcpNoAnswer", 15))),
            "the log does not say why: " + this.model.lines);
    }

    /**
     * A connection that answered for a while and then hangs is found too: the answer clock starts again at the first
     * send after the last answer.
     *
     * MUTATION: in `NetworkProxy.writeTcp`, `if (first == 0 || first - this.lastHeardAt <= 0)` to `if (first == 0)`.
     */
    @Test(timeOut = 60000)
    public void testAHangAfterAnAnsweredSpellIsFound() throws Exception
    {
        connectedAndAnswered();

        this.station.answering(false);
        this.proxy.sendMessage(new CS2Message(ping(1)));

        assertTrue(logged("network.tcpLostReconnecting", 25000),
            "a hang after the connection had answered was not found: " + this.model.lines);
    }

    /**
     * A healthy station that answered and then sends nothing more is not taken as hung: silence alone is not a hang.
     * The wait is the point: past the 15 s answer limit.
     *
     * MUTATION: in `NetworkProxy.readUntilLost`, take out `first - this.lastHeardAt > 0 && `.
     */
    @Test(timeOut = 60000)
    public void testAQuietStationIsNotTakenAsHung() throws Exception
    {
        connectedAndAnswered();

        assertFalse(logged("network.tcpLostReconnecting", 18000),
            "a quiet healthy connection was taken as hung: " + this.model.lines);
    }

    /**
     * A sensor change the station broadcast while TCP was failing, and that TCP never carried, reaches the model when
     * the loss is found: the held UDP frames TCP did not deliver are handed over.
     *
     * MUTATION: in `NetworkProxy.releaseHeld`, take out `for (byte[] frame : unmatched()) deliver(frame);`.
     */
    @Test(timeOut = 30000)
    public void testAFrameOnlyUdpCarriedReachesTheModelWhenTcpIsLost() throws Exception
    {
        connectedAndCarried();

        byte[] clear = sensor(1007, false);
        broadcastHeld(clear);
        assertFalse(this.model.heardFrame(clear), "precondition: the UDP frame was delivered while TCP ran");

        this.station.close();

        assertTrue(Components.waitFor(() -> this.model.heardFrame(clear), WAIT_MS),
            "the sensor clear broadcast during the outage was thrown away: " + this.model.lines);
    }

    /**
     * A stale state is never replayed: the UDP copy of an "occupied" that TCP delivered before its "clear" is held,
     * and when TCP is lost it is matched to TCP's own frame and dropped, so the sensor stays clear.
     *
     * MUTATION: in `NetworkProxy.unmatched`, `if (Arrays.equals(tail.get(k).frame, h.frame)) match = k;` to
     * `if (false) match = k;`.
     */
    @Test(timeOut = 30000)
    public void testAStaleUdpCopyIsNotReplayedWhenTcpIsLost() throws Exception
    {
        this.station = new TcpStation(STATION);
        connect(this.station.port());
        assertTrue(logged("network.tcpConnected", WAIT_MS), "precondition: no connection: " + this.model.lines);

        byte[] occupied = sensor(1111, true);
        byte[] clear = sensor(1111, false);
        broadcastHeld(occupied);
        this.station.send(occupied);
        this.station.send(clear);
        assertTrue(Components.waitFor(() -> this.model.heardFrame(clear), WAIT_MS), "precondition: TCP did not carry");

        this.station.close();

        assertTrue(logged("network.tcpLostReconnecting", WAIT_MS), "the loss was not found: " + this.model.lines);
        assertEquals(this.model.times(occupied), 1, "the stale occupied was replayed after the clear");
        assertTrue(Arrays.equals(this.model.heard.get(this.model.heard.size() - 1), clear),
            "the sensor's last state is not the clear TCP delivered");
    }

    /**
     * A Stop sent while the connection is being replaced goes out over UDP at once, not after the tries.
     *
     * MUTATION: in `NetworkProxy.sendMessage`, take out `if (isSystemFrame(message) && suspect()) sendUdp(message);`.
     */
    @Test(timeOut = 30000)
    public void testAStopDuringAReconnectGoesOutAtOnce() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedAndCarried();
            this.station.close();
            assertTrue(logged("network.tcpLostReconnecting", WAIT_MS), "the loss was not found: " + this.model.lines);

            byte[] stop = Packets.powerStop().getRawMessage();
            this.proxy.sendMessage(new CS2Message(stop));

            assertTrue(Arrays.equals(nextOn(wire, 1000), stop),
                "the Stop did not reach the station over UDP within 1 s of being sent: " + this.model.lines);
        }
    }

    /**
     * A command the station may not have when TCP is given up is sent over UDP: a Stop written to a connection that
     * then closed, with no answer.
     *
     * MUTATION: in `NetworkProxy.recover`, take out `for (Unconfirmed u : again) sendUdp(u.frame);`.
     */
    @Test(timeOut = 60000)
    public void testAnUnansweredCommandGoesOverUdpWhenTcpIsGivenUp() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedAndCarried();

            byte[] stop = Packets.powerStop().getRawMessage();
            this.proxy.sendMessage(new CS2Message(stop));
            assertTrue(Components.waitFor(() -> this.station.received().size() == 1, WAIT_MS), "precondition: not sent");

            this.station.close();

            assertTrue(logged("network.tcpReconnectFailed", 30000), "no fallback to UDP: " + this.model.lines);
            assertTrue(Arrays.equals(nextOn(wire, WAIT_MS), stop),
                "the unanswered Stop was not sent over UDP when TCP was given up: " + this.model.lines);
        }
    }

    /**
     * A station that takes the connection but never sends on it is left for UDP once a send goes unanswered for
     * 15 s: what was sent over TCP goes over UDP once each, and a frame broadcast meanwhile reaches the model.
     *
     * MUTATION: in `NetworkProxy.recover`, `for (Unconfirmed u : again) sendUdp(u.frame);` to
     * `for (Unconfirmed u : again) { sendUdp(u.frame); sendUdp(u.frame); }`.
     */
    @Test(timeOut = 60000)
    public void testASilentStationIsLeftForUdp() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            this.station = new TcpStation(STATION);
            connect(this.station.port());
            assertTrue(logged("network.tcpConnected", WAIT_MS), "precondition: no connection: " + this.model.lines);

            List<byte[]> sent = new ArrayList<>();

            for (int i = 0; i < 5; i++) sent.add(ping(i));

            sent.add(Packets.powerGo().getRawMessage());

            for (byte[] frame : sent) this.proxy.sendMessage(new CS2Message(frame));

            byte[] occupied = sensor(1010, true);
            broadcastHeld(occupied);

            assertTrue(logged("network.tcpLostUsingUdp", 25000), "no fallback to UDP: " + this.model.lines);
            assertTrue(Components.waitFor(() -> this.model.heardFrame(occupied), WAIT_MS),
                "the frame broadcast during the silence never reached the model");

            for (int i = 0; i < sent.size(); i++)
            {
                assertTrue(Arrays.equals(nextOn(wire, WAIT_MS), sent.get(i)), "frame " + (i + 1) + " not sent over UDP");
            }

            // Waiting is the point: nothing more may come
            assertEquals(nextOn(wire, 1000), null, "a frame went over UDP more than once");
        }
    }

    /**
     * Three senders while the connection is dropped three times: no frame lost, none out of order on a connection,
     * and no send waits for a reconnect.
     *
     * MUTATION: in `NetworkProxy.resendOn`, send `again` newest first.  And: in `NetworkProxy.sendMessage`, take out
     * `keep(message, System.nanoTime(), false);`.
     */
    @Test(timeOut = 120000)
    public void testSendersDuringDropsLoseNothing() throws Exception
    {
        this.station = new TcpStation(STATION).answering(true);
        connect(this.station.port());
        assertTrue(Components.waitFor(() -> this.station.connections() == 1, WAIT_MS), "precondition: no connection");

        final int threads = 3;
        final int each = 600;
        final AtomicLong longestSend = new AtomicLong();
        List<Thread> senders = new ArrayList<>();

        for (int t = 1; t <= threads; t++)
        {
            final int sender = t;
            Thread thread = new Thread(() ->
            {
                for (int n = 0; n < each; n++)
                {
                    long start = System.nanoTime();
                    this.proxy.sendMessage(new CS2Message(numbered(sender, n)));
                    longestSend.accumulateAndGet(System.nanoTime() - start, Math::max);

                    // A command every few milliseconds from each, as a busy railway sends
                    LockSupport.parkNanos(3000000L);
                }
            }, "tcp-claim-sender-" + t);

            senders.add(thread);
            thread.start();
        }

        for (int drop = 1; drop <= 3; drop++)
        {
            final int want = drop * 350;
            final int reconnects = drop;
            assertTrue(Components.waitFor(() -> this.station.received().size() >= want, 30000), "stalled at drop " + drop);
            this.station.drop();
            assertTrue(Components.waitFor(() -> this.model.count("network.tcpReconnected") >= reconnects, 15000),
                "no reconnect after drop " + drop + ": " + this.model.lines);
        }

        for (Thread thread : senders) thread.join(60000);

        for (int t = 1; t <= threads; t++)
        {
            byte[] last = numbered(t, each - 1);
            assertTrue(Components.waitFor(() -> this.station.received().stream().anyMatch(f -> Arrays.equals(f.bytes, last)),
                10000), "sender " + t + "'s last frame never arrived");
        }

        List<TcpStation.Frame> all = this.station.received();
        int lost = 0;
        int disorder = 0;

        for (int t = 1; t <= threads; t++)
        {
            Map<Integer, Integer> seen = new HashMap<>();
            Map<Integer, Integer> lastOn = new HashMap<>();

            for (TcpStation.Frame f : all)
            {
                if (f.bytes[5] != t) continue;

                int n = ((f.bytes[6] & 0xff) << 8) | (f.bytes[7] & 0xff);
                seen.merge(n, 1, Integer::sum);
                Integer before = lastOn.get(f.connection);

                if (before != null && n <= before) disorder++;

                lastOn.put(f.connection, n);
            }

            for (int n = 0; n < each; n++) if (!seen.containsKey(n)) lost++;
        }

        String summary = "connections=" + this.station.connections() + " frames=" + all.size() + " log=" + this.model.lines;
        assertEquals(disorder, 0, "frames out of order on one connection: " + summary);
        assertEquals(lost, 0, "frames lost across the drops: " + summary);
        assertTrue(longestSend.get() < 500000000L, "a send waited " + longestSend.get() / 1000000 + " ms: " + summary);
    }

    /** A ping-command frame carrying its sender and number, so each can be told apart at the station. */
    private static byte[] numbered(int sender, int n)
    {
        byte[] f = new byte[CS2Message.MESSAGE_LENGTH];
        f[1] = 0x30;
        f[2] = 0x47;
        f[3] = 0x11;
        f[4] = 4;
        f[5] = (byte) sender;
        f[6] = (byte) (n >> 8);
        f[7] = (byte) n;
        return f;
    }

    /**
     * The log tells commands sent again, that the station may already have, from commands only held while
     * reconnecting and sent late.
     *
     * MUTATION: in `NetworkProxy.counts`, `if (u.written) written++;` to `written++;`.
     */
    @Test(timeOut = 30000)
    public void testTheLogTellsSentAgainFromSentLate() throws Exception
    {
        connectedAndAnswered();
        this.station.answering(false);

        // Waiting is the point: a frame heard past the margin after the ping makes the ping count as received
        Components.waitFor(() -> false, 400);
        this.station.send(sensor(1008, true));
        assertTrue(Components.waitFor(() -> this.model.heardFrame(sensor(1008, true)), WAIT_MS), "precondition: not heard");

        this.proxy.sendMessage(new CS2Message(ping(1)));
        assertTrue(Components.waitFor(() -> this.station.received().size() == 2, WAIT_MS), "precondition: not sent");

        this.station.drop();
        assertTrue(logged("network.tcpLostReconnecting", WAIT_MS), "the loss was not found: " + this.model.lines);
        this.proxy.sendMessage(new CS2Message(ping(2)));
        this.proxy.sendMessage(new CS2Message(ping(3)));

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);
        assertTrue(this.model.lines.contains(I18n.f("network.framesSentAgain", 1)), "sent again not 1: " + this.model.lines);
        assertTrue(this.model.lines.contains(I18n.f("network.framesSentLate", 2)), "sent late not 2: " + this.model.lines);
    }

    /**
     * Stopping closes the connection, so the station sees it end, and ends both listeners.
     *
     * MUTATION: in `NetworkProxy.stopListening`, take out both `closeQuietly(this.tcp);`.
     */
    @Test(timeOut = 30000)
    public void testStoppingClosesTheConnectionAndEndsTheListener() throws Exception
    {
        this.station = new TcpStation(STATION);
        connect(this.station.port());
        assertTrue(logged("network.canListenerRunningTcp", WAIT_MS), "precondition: no TCP listener");

        Thread reader = (Thread) field("reader");
        Thread tcpReader = (Thread) field("tcpReader");

        this.proxy.stopListening();

        assertTrue(Components.waitFor(() -> this.station.ended(1), WAIT_MS), "the station's connection is still open");
        reader.join(WAIT_MS);
        tcpReader.join(WAIT_MS);
        assertFalse(reader.isAlive(), "the UDP listener thread is still running");
        assertFalse(tcpReader.isAlive(), "the TCP listener thread is still running");
        assertEquals(this.model.count("network.canListenerClosed"), 1, "the end is not logged: " + this.model.lines);
    }
}
