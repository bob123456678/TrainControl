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
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import support.Reflect;
import support.TcpStation;

/**
 * A CS3's CAN frames go over TCP once the proxy is asked, and over UDP when the station does not take the connection
 * or it is lost for good (FR-186).  Each claim drives a real `NetworkProxy` against `support.TcpStation` and reads
 * what reached the model, the station over TCP and UDP, and the log.
 *
 * The station is on a loopback address of this class's own, so a claim can take UDP 15731 without a clash with
 * another test; a datagram sent to the proxy's UDP socket (any port) stands in for the station's broadcast.
 *
 * @author Adam
 */
public class testTheStationIsReachedOverTcp
{
    private static final long WAIT_MS = 5000;

    /** A loopback address of this run's own, so two runs at once (two worktrees) cannot take each other's frames. */
    private static final String STATION = "127." + (16 + java.util.concurrent.ThreadLocalRandom.current().nextInt(239)) + "."
        + java.util.concurrent.ThreadLocalRandom.current().nextInt(256) + "."
        + (1 + java.util.concurrent.ThreadLocalRandom.current().nextInt(254));

    private NetworkProxy proxy;

    private TcpStation station;

    private Model model;

    /** A model that keeps what it is handed and the keys it is asked to log. */
    private static final class Model implements ModelListener
    {
        final List<byte[]> heard = Collections.synchronizedList(new ArrayList<>());
        final List<String> keys = Collections.synchronizedList(new ArrayList<>());
        final List<String> lines = Collections.synchronizedList(new ArrayList<>());

        // Contacts the model knows without an event, as a layout's
        final List<Integer> alsoKnown = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void receiveMessage(CANMessage message)
        {
            java.util.function.BooleanSupplier hold = this.holdFirst;

            if (hold != null)
            {
                this.holdFirst = null;
                Components.waitFor(hold, WAIT_MS);
            }

            this.heard.add(((CS2Message) message).getRawMessage());

            if (((CS2Message) message).answersAResync()) this.resyncAnswers.incrementAndGet();
        }

        // How many frames the proxy marked as answers to its own queries
        final java.util.concurrent.atomic.AtomicInteger resyncAnswers = new java.util.concurrent.atomic.AtomicInteger();

        // The next frame handed over waits until this says yes
        volatile java.util.function.BooleanSupplier holdFirst;

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

        /** As the control station: every contact it has had a sensor frame for, and the layout's. */
        @Override
        public List<Integer> knownSensors()
        {
            TreeSet<Integer> known = new TreeSet<>(this.alsoKnown);

            synchronized (this.heard)
            {
                for (byte[] h : this.heard) if (isSensorFrame(h)) known.add(contactOf(h));
            }

            return new ArrayList<>(known);
        }

        /** A contact's states as the model was handed them, each run of one state counted once. */
        List<Boolean> states(int contact)
        {
            List<Boolean> out = new ArrayList<>();

            synchronized (this.heard)
            {
                for (byte[] h : this.heard)
                {
                    if (!isSensorFrame(h) || contactOf(h) != contact) continue;

                    boolean occupied = h[10] == 1;

                    if (out.isEmpty() || out.get(out.size() - 1) != occupied) out.add(occupied);
                }
            }

            return out;
        }

        Boolean lastState(int contact)
        {
            List<Boolean> all = states(contact);
            return all.isEmpty() ? null : all.get(all.size() - 1);
        }

        @Override
        public void logf(String key, Object... args)
        {
            if (key.equals(this.failOnce))
            {
                this.failOnce = null;
                throw new IllegalStateException("a fault injected into " + key);
            }

            this.keys.add(key);
            this.lines.add(I18n.f(key, args));

            if (args.length > 0 && args[0] instanceof Integer) this.sums.merge(key, (Integer) args[0], Integer::sum);
        }

        // A key whose next logf throws, once: an unexpected fault in the proxy's own thread
        volatile String failOnce;

        // Each key's first number, summed over its lines
        final Map<String, Integer> sums = new java.util.concurrent.ConcurrentHashMap<>();

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

    /** A sensor frame as the station would send it, of no change (another program's answer), so never asked for. */
    private static byte[] sensor(int contact, boolean occupied)
    {
        return answer(0, contact, occupied);
    }

    /** A sensor event from the Link S88, whose contacts a resync asks. */
    private static byte[] linkS88(int contact, boolean occupied)
    {
        return TcpStation.event(TcpStation.LINK_S88, contact, occupied);
    }

    private static boolean isSensorFrame(byte[] h)
    {
        return (h[1] & 0xfe) == CS2Message.CMD_ACC_SENSOR << 1 && h[4] == 8;
    }

    private static int contactOf(byte[] h)
    {
        return ((h[7] & 0xff) << 8) | (h[8] & 0xff);
    }

    /** The query a resync sends for a contact. */
    private static byte[] query(int device, int contact)
    {
        return new CS2Message(CS2Message.CMD_ACC_SENSOR,
            new byte[] {(byte) (device >> 8), (byte) device, (byte) (contact >> 8), (byte) contact}).getRawMessage();
    }

    private static byte[] answer(int device, int contact, boolean occupied)
    {
        return TcpStation.answer(device, contact, occupied);
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
        return Reflect.get(NetworkProxy.class, this.proxy, name);
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

    /** Broadcasts a frame and waits until the proxy has read it and set it aside, as it does every datagram over TCP. */
    private void broadcastSetAside(byte[] frame) throws Exception
    {
        Object lock = field("udpLock");
        java.lang.reflect.Field count = Reflect.field(NetworkProxy.class, "udpSetAside");
        long before;

        synchronized (lock)
        {
            before = count.getLong(this.proxy);
        }

        broadcast(frame);

        assertTrue(Components.waitFor(() ->
        {
            synchronized (lock)
            {
                try
                {
                    return count.getLong(this.proxy) > before;
                }
                catch (IllegalAccessException e)
                {
                    throw new IllegalStateException(e);
                }
            }
        }, WAIT_MS), "the proxy did not set the broadcast frame aside: " + this.model.lines);
    }

    /** The station shows the contact occupied, and the model has heard it. */
    private void occupiedAndHeard(int contact) throws Exception
    {
        this.station.sensor(contact, true);
        assertTrue(Components.waitFor(() -> this.model.heardFrame(linkS88(contact, true)), WAIT_MS), "precondition");
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
        connectedAndCarried(new TcpStation(STATION));
    }

    private void connectedAndCarried(TcpStation to) throws Exception
    {
        this.station = to;
        connect(this.station.port());

        this.station.send(sensor(1007, true));
        assertTrue(Components.waitFor(() -> this.model.heard.size() == 1, WAIT_MS), "precondition: nothing carried");
    }

    /** As connectedAndCarried, then `frames` written to the connection and received by the station. */
    private void connectedAndWritten(byte[]... frames) throws Exception
    {
        connectedAndCarried();

        for (byte[] frame : frames) this.proxy.sendMessage(new CS2Message(frame));

        assertTrue(Components.waitFor(() -> this.station.received().size() == frames.length, WAIT_MS),
            "precondition: not sent");
    }

    /** Connected over TCP to a station that sends nothing unless told. */
    private void connectedToASilentStation() throws Exception
    {
        this.station = new TcpStation(STATION);
        connect(this.station.port());
        assertTrue(logged("network.tcpConnected", WAIT_MS), "precondition: no connection: " + this.model.lines);
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
     * with a whole frame and part of the next, reach the model as whole frames, once each, in order.  The next piece
     * is written only once the previous frame was heard, so every cut falls between two reads.
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
     * Over TCP the UDP copy of a frame is set aside, not delivered, or every frame would arrive twice: a frame
     * broadcast and also sent over TCP reaches the model once, and a frame only broadcast not at all while TCP runs.
     *
     * MUTATION: in `NetworkProxy.startTcp`, take out `setUdpLive(false);`.
     */
    @Test(timeOut = 30000)
    public void testOverTcpNoFrameArrivesTwice() throws Exception
    {
        connectedToASilentStation();

        byte[] both = sensor(1005, true);
        byte[] udpOnly = sensor(1006, true);
        broadcastSetAside(both);
        broadcastSetAside(udpOnly);
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
        // A client's connect returns before the station side has counted the accept
        assertTrue(Components.waitFor(() -> this.station.connections() == 2, WAIT_MS),
            "the station was not connected to again");

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
     * UDP stays live while a connect hangs (a closed loopback port on Windows times out after about 2 s): every frame
     * the station broadcasts meanwhile, a handset's locomotive, accessory or GO/STOP frame as much as a sensor's,
     * reaches the model.
     *
     * MUTATION: in `NetworkProxy.startTcp`, move `setUdpLive(false);` to before `connectTcp()`.
     */
    @Test(timeOut = 30000)
    public void testUdpIsHeardWhileTheFirstConnectHangs() throws Exception
    {
        this.proxy = new NetworkProxy(InetAddress.getByName("127.0.0.1"));
        this.proxy.setModel(this.model);
        assertTrue(logged("network.canListenerRunning", WAIT_MS), "no UDP listener started: " + this.model.lines);

        int port = TcpStation.closedPort();
        Thread connecting = new Thread(() -> this.proxy.connectOverTcp(port), "tcp-claim-connect");
        long start = System.nanoTime();
        connecting.start();

        assertAllHeard(broadcastWhile(3000, connecting::isAlive), start, 1000);
    }

    /**
     * The same while each try to replace a lost connection hangs, until UDP takes over for the session.
     *
     * MUTATION: in `NetworkProxy.recover`, add `setUdpLive(false);` before `Socket fresh = connectTcp();`.
     */
    @Test(timeOut = 60000)
    public void testUdpIsHeardWhileAReconnectHangs() throws Exception
    {
        connectedAndAnswered();

        long start = System.nanoTime();
        this.station.close();

        assertAllHeard(broadcastWhile(4000, () -> this.model.count("network.tcpReconnectFailed") == 0
            && System.nanoTime() - start < 30000000000L), start, 8000);
    }

    /** Broadcasts a sensor frame every 20 ms, from `contact` up, while `going` holds; returns them. */
    private List<byte[]> broadcastWhile(int contact, java.util.function.BooleanSupplier going) throws Exception
    {
        List<byte[]> sent = new ArrayList<>();

        while (going.getAsBoolean())
        {
            sent.add(sensor(contact++, true));
            broadcast(sent.get(sent.size() - 1));
            LockSupport.parkNanos(20000000L);
        }

        return sent;
    }

    /** Every frame broadcast reached the model, over a spell since `start` long enough that the connects hung. */
    private void assertAllHeard(List<byte[]> sent, long start, long hungMs)
    {
        long tookMs = (System.nanoTime() - start) / 1000000L;
        assertTrue(tookMs >= hungMs, "precondition: the connect did not hang (" + tookMs + " ms): " + this.model.lines);
        assertTrue(Components.waitFor(() -> sent.stream().allMatch(this.model::heardFrame), WAIT_MS), "of "
            + sent.size() + " frames broadcast while connecting, "
            + sent.stream().filter(f -> !this.model.heardFrame(f)).count() + " never reached the model");
    }

    /**
     * An unexpected error that ends the listener while it replaces a lost connection leaves nothing being replaced:
     * UDP takes over for the session, logged, so autonomy does not hold new routes for ever; and the sensors are asked
     * again over UDP, so one changed in the gap ends at the station's state.
     *
     * MUTATION: take the catch out of `NetworkProxy.readTcp`, or its `resync`, and this fails.
     */
    @Test(timeOut = 30000)
    public void testAListenerEndedByAnErrorLeavesNothingBeingReplaced() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedAndAnswered();
            occupiedAndHeard(1202);

            Thread listener = (Thread) field("tcpReader");

            this.model.failOnce = "network.tcpLostReconnecting";
            this.station.sensorUnseen(1202, false);
            this.station.drop();
            listener.join(15000);

            assertFalse(listener.isAlive(), "precondition: the listener did not end on the injected fault");
            assertFalse(this.proxy.isReplacing(), "the listener ended with the lost connection still being replaced, so"
                + " new routes would be held for the rest of the session: " + this.model.lines);
            assertTrue(logged("network.tcpLostUsingUdp", WAIT_MS), "the loss is not logged: " + this.model.lines);

            askedAndAnsweredOverUdp(wire, 1202, WAIT_MS);
            assertEndsAtTheStationsState(1202);
            assertReachedOverUdp();
        }
    }

    /**
     * A frame written just before the station drops the connection is not sent again, on the new connection or over
     * UDP: another program or the handset may have commanded since (FR-186).
     *
     * MUTATION: in `NetworkProxy`, keep each frame with its time in `unconfirmed`, and in `recover` write those counted
     * at the loss to `fresh` before `useTcp(fresh);`.
     */
    @Test(timeOut = 30000)
    public void testAFrameWrittenBeforeTheDropIsNotSentAgain() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            byte[] command = speed(0);
            connectedAndWritten(command);
            this.station.drop();

            assertNotOnTheNewConnection(command);

            // Waiting is the point: nothing may go over UDP
            assertEquals(nextOn(wire, 200), null, "the frame was sent again over UDP");
        }
    }

    /** A locomotive speed command. */
    private static byte[] speed(int speed)
    {
        return new CS2Message(CS2Message.CMD_LOCO_VELOCITY, new byte[] {0, 0, 0, 5, 0, (byte) speed}).getRawMessage();
    }

    /** A new connection is made, carries a later frame, and carried none of `frames` before it. */
    private void assertNotOnTheNewConnection(byte[]... frames)
    {
        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);

        byte[] later = ping(9);
        this.proxy.sendMessage(new CS2Message(later));
        assertTrue(Components.waitFor(() -> this.station.receivedOn(2).stream().anyMatch(f -> Arrays.equals(f, later)),
            WAIT_MS), "a send after the reconnect never reached the station: " + this.model.lines);

        for (byte[] frame : frames)
        {
            assertFalse(this.station.receivedOn(2).stream().anyMatch(f -> Arrays.equals(f, frame)),
                "a frame was sent again on the new connection: " + this.model.lines);
        }
    }

    /**
     * A connection that hangs, open but carrying nothing, is found by a send that goes unanswered for 3 s, and
     * replaced within a few seconds more; the log says why, and counts the unanswered frame as one that may not have
     * arrived.
     *
     * MUTATION: in `NetworkProxy.readUntilLost`, take out `return I18n.f("network.tcpNoAnswer", ...)`; or set
     * `TCP_ANSWER_LIMIT_MS` back to 15000.
     */
    @Test(timeOut = 60000)
    public void testAHungConnectionIsFoundAndReplaced() throws Exception
    {
        connectedAndCarried();

        this.proxy.sendMessage(new CS2Message(ping(1)));

        assertTrue(logged("network.tcpReconnected", 8000), "no new connection within 8 s: " + this.model.lines);
        assertTrue(this.model.lines.stream().anyMatch(l -> l.contains(I18n.f("network.tcpNoAnswer", 3))),
            "the log does not say why: " + this.model.lines);
        assertTrue(this.model.lines.contains(I18n.f("network.framesMayNotHaveArrived", 1)),
            "the unanswered frame is not counted: " + this.model.lines);
    }

    /**
     * A hung connection is reset, not closed in order, so a stalled station that reads again later never gets the
     * frames written before the loss and runs no old command late (FR-186).
     *
     * MUTATION: in `NetworkProxy.recover`, `lost.setSoLinger(true, 0);` to `lost.setSoLinger(false, 0);`.
     */
    @Test(timeOut = 60000)
    public void testAHungConnectionIsResetSoTheStationRunsNothingLate() throws Exception
    {
        connectedAndCarried(new TcpStation(STATION).reading(false));

        this.proxy.sendMessage(new CS2Message(speed(5)));
        this.proxy.sendMessage(new CS2Message(ping(1)));

        assertTrue(logged("network.tcpLostReconnecting", 30000), "the hang was not found: " + this.model.lines);
        this.station.reading(true);

        assertTrue(Components.waitFor(() -> this.station.ended(1), WAIT_MS), "the lost connection did not end");
        assertEquals(this.station.receivedOn(1).size(), 0, "the station read frames written before the loss: "
            + this.model.lines);
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
     * The wait is the point: past the 3 s answer limit.
     *
     * MUTATION: in `NetworkProxy.readUntilLost`, take out `first - this.lastHeardAt > 0 && `.
     */
    @Test(timeOut = 60000)
    public void testAQuietStationIsNotTakenAsHung() throws Exception
    {
        connectedAndAnswered();

        assertFalse(logged("network.tcpLostReconnecting", 5000),
            "a quiet healthy connection was taken as hung: " + this.model.lines);
    }

    /**
     * A station that answers each send within the limit, here after 2 s, keeps its connection however many sends it
     * answers so.
     *
     * MUTATION: set `TCP_ANSWER_LIMIT_MS` to 1000, and this fails.
     */
    @Test(timeOut = 60000)
    public void testAStationThatAnswersWithinTheLimitKeepsItsConnection() throws Exception
    {
        connectedAndCarried();

        for (int i = 1; i <= 4; i++)
        {
            byte[] frame = ping(20 + i);
            this.proxy.sendMessage(new CS2Message(frame));

            // The wait is the answer's delay, and the connection must not be given up during it
            assertFalse(logged("network.tcpLostReconnecting", 2000), "a connection answered within the limit was"
                + " taken as hung, send " + i + ": " + this.model.lines);

            this.station.send(answerTo(frame));
        }

        assertFalse(logged("network.tcpLostReconnecting", 2000), "a connection answered within the limit was taken as"
            + " hung after its last answer: " + this.model.lines);
        assertEquals(this.station.connections(), 1, "the connection was replaced");
    }

    /** A station that answers pings, as every station does, and no other frame. */
    private static TcpStation answeringPingsOnly() throws Exception
    {
        return new TcpStation(STATION).answeringOnly(f -> ((f[1] & 0xFF) >> 1) == CS2Message.CAN_CMD_PING);
    }

    /**
     * A station that never answers one frame but answers pings keeps its connection past the answer limit: the proxy
     * pings once the frame is suspect, and the ping's answer shows the connection is alive.
     *
     * MUTATION: take the ping out of `NetworkProxy.readUntilLost`, and this fails.
     */
    @Test(timeOut = 60000)
    public void testAFrameTheStationLeavesUnansweredKeepsTheConnection() throws Exception
    {
        connectedAndCarried(answeringPingsOnly());

        this.proxy.sendMessage(new CS2Message(speed(10)));

        assertFalse(logged("network.tcpLostReconnecting", 6000), "a connection whose station answers pings was given up"
            + " over one frame it leaves unanswered: " + this.model.lines);
        assertEquals(this.station.connections(), 1, "the connection was replaced");
    }

    /**
     * Nor is a reconnected connection, which has carried nothing yet, given up for UDP when its first frame goes
     * unanswered: the ping's answer is its first.
     *
     * MUTATION: take the ping out of `NetworkProxy.readUntilLost`, and this fails.
     */
    @Test(timeOut = 60000)
    public void testAFreshConnectionWhoseFirstFrameGoesUnansweredStaysOnTcp() throws Exception
    {
        connectedAndCarried(answeringPingsOnly());

        this.station.drop();
        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);

        this.proxy.sendMessage(new CS2Message(speed(11)));

        assertFalse(logged("network.tcpLostUsingUdp", 6000), "a fresh connection whose station answers pings was left"
            + " for UDP over one frame it leaves unanswered: " + this.model.lines);
        assertEquals(this.model.count("network.tcpLostReconnecting"), 1, "the fresh connection was given up: "
            + this.model.lines);
    }

    /**
     * A sensor change only UDP carried, while TCP was failing, is put right once the loss is found: UDP is live at
     * once and the sensor is asked for its state over it, within a second, before any reconnect.
     *
     * MUTATION: in `NetworkProxy.recover`, take out the `resync(false);` after the loss is logged.
     */
    @Test(timeOut = 30000)
    public void testASensorChangedOnlyOverUdpIsPutRightWhenTcpIsLost() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedToASilentStation();
            occupiedAndHeard(1201);

            this.station.sensorUnseen(1201, false);
            broadcastSetAside(linkS88(1201, false));
            assertEquals(this.model.lastState(1201), Boolean.TRUE, "precondition: the UDP frame was delivered over TCP");

            closedAndAskedOverUdp(wire, 1201);
            assertEndsAtTheStationsState(1201);
        }
    }

    /**
     * The station closes; the loss is found and the contact is asked over UDP within 1 s, and answered there.  Returns
     * when the loss was seen logged.
     */
    private long closedAndAskedOverUdp(DatagramSocket wire, int contact) throws Exception
    {
        this.station.close();
        assertTrue(logged("network.tcpLostReconnecting", WAIT_MS), "the loss was not found: " + this.model.lines);
        long lossSeen = System.nanoTime();
        askedAndAnsweredOverUdp(wire, contact, 1000);
        return lossSeen;
    }

    /** The next datagram on the wire, within `ms`, asks for the contact; it is answered with the station's state. */
    private void askedAndAnsweredOverUdp(DatagramSocket wire, int contact, long ms) throws Exception
    {
        assertTrue(Arrays.equals(nextOn(wire, ms), query(TcpStation.LINK_S88, contact)),
            "contact " + contact + " was not asked over UDP within " + ms + " ms: " + this.model.lines);
        broadcast(answer(TcpStation.LINK_S88, contact, this.station.occupied(contact)));
    }

    private void assertEndsAtTheStationsState(int contact)
    {
        assertTrue(Components.waitFor(() -> Boolean.valueOf(this.station.occupied(contact)).equals(
            this.model.lastState(contact)), WAIT_MS), "the sensor did not end at the station's state: "
            + this.model.states(contact));
    }

    /** The station drops the connection; a new one is made and its resync logs the counts. */
    private void droppedAndResynced(int asked, int answered, int skipped)
    {
        try
        {
            this.station.drop();
        }
        catch (java.io.IOException e)
        {
            throw new IllegalStateException(e);
        }

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);
        assertTrue(loggedAfter("network.tcpReconnected", I18n.f("network.sensorsResynced", asked, answered, skipped)),
            "the new connection's resync did not log " + asked + " asked, " + answered + " answered, " + skipped
            + " skipped: " + this.model.lines);
    }

    /**
     * A sensor that changed while the connection was down, carried by neither TCP nor UDP, ends at the station's
     * state: asked over the new connection.
     *
     * MUTATION: in `NetworkProxy.recover`, take out `resync(true);`.
     */
    @Test(timeOut = 30000)
    public void testASensorChangedDuringADropIsPutRightOnTheNewConnection() throws Exception
    {
        connectedToASilentStation();
        occupiedAndHeard(1202);

        this.station.drop();
        this.station.sensorUnseen(1202, false);

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);
        assertTrue(Components.waitFor(() -> Boolean.FALSE.equals(this.model.lastState(1202)), WAIT_MS),
            "the sensor did not end at the station's state: " + this.model.states(1202) + " " + this.model.lines);
    }

    /**
     * A contact is asked only at the device a real event, a change of state, showed for it, whatever that device's
     * number (OB-418): devices vary by station.  A device that never sent a change is never asked: not for a contact
     * the model knows with no event, nor for one only an answer named (on Adam's CS3, the station's own false
     * "occupied" for device 1), nor at a device an answer named rather than an event.
     *
     * MUTATION: in `NetworkProxy.deliverTcp`, `marked(model.createMessage(frame), heard)` to
     * `marked(model.createMessage(frame), Heard.FRAME)`.  And: in `NetworkProxy.askOnce`, `if (device == null)` to
     * `if (device == null || device < 2)`.  And: there, `this.sensorDevice.get(contact)` to
     * `this.sensorDevice.getOrDefault(contact, 2)`.  And: in `NetworkProxy.learn`, `if (frame[9] != frame[10])` to
     * `if (true)`.
     */
    @Test(timeOut = 30000)
    public void testOnlyADeviceAnEventShowedIsAsked() throws Exception
    {
        connectedToASilentStation();
        this.model.alsoKnown.add(1304);

        this.station.sensor(1301, true);
        this.station.send(TcpStation.event(0, 1302, true));
        this.station.send(TcpStation.event(1, 1303, true));
        this.station.send(answer(1, 1305, true));
        this.station.send(answer(1, 1301, true));
        assertTrue(Components.waitFor(() -> this.model.heard.size() == 5, WAIT_MS), "precondition: not carried");

        droppedAndResynced(3, 1, 2);

        assertTrue(Components.waitFor(() -> this.model.heardFrame(answer(TcpStation.LINK_S88, 1301, true)), WAIT_MS),
            "the Link S88's contact was not asked over the new connection");
        assertEquals(this.model.resyncAnswers.get(), 1, "the station's answer was not marked as the resync's, or the "
            + "device 1 frame was");
        assertEquals(asked(), "[[0, 1302], [1, 1303], [2, 1301]]", "the queries are not each contact at its device");
    }

    /**
     * A CS3 reached over UDP, as when it refuses TCP, learns its contacts' devices from events too, and a sync asks
     * them over UDP and logs its own line (OB-418).
     *
     * MUTATION: in `NetworkProxy.connectOverTcp`, take out `this.learnsDevices = true;`.
     */
    @Test(timeOut = 30000)
    public void testACS3OnUdpIsAskedOverUdp() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            Thread syncing = syncingOverUdp(wire, 1901);
            broadcast(answer(TcpStation.LINK_S88, 1901, false));
            syncing.join(WAIT_MS);

            assertTrue(this.model.lines.contains(I18n.f("network.sensorsSynced", 1, 1, 0)),
                "the sync did not log 1 asked, 1 answered: " + this.model.lines);
            assertEquals(this.model.lastState(1901), Boolean.FALSE, "the answer did not reach the model");
        }
    }

    /**
     * A proxy on UDP, TCP refused, that has heard the contact occupied from the Link S88, syncing on a thread of its
     * own, which has just asked the contact over UDP.
     */
    private Thread syncingOverUdp(DatagramSocket wire, int contact) throws Exception
    {
        connect(STATION, TcpStation.closedPort());
        assertTrue(logged("network.tcpUnavailable", WAIT_MS), "precondition: TCP was taken: " + this.model.lines);

        byte[] event = linkS88(contact, true);
        broadcast(event);
        assertTrue(Components.waitFor(() -> this.model.heardFrame(event), WAIT_MS), "precondition: not heard");

        Thread syncing = new Thread(this.proxy::syncSensors, "tcp-claim-sync");
        syncing.start();
        assertTrue(Arrays.equals(nextOn(wire, WAIT_MS), query(TcpStation.LINK_S88, contact)),
            "the contact was not asked over UDP: " + this.model.lines);
        return syncing;
    }

    /** Broadcasts a frame of another contact after the others, and waits until it is heard, so they were read. */
    private void allRead() throws Exception
    {
        byte[] marker = linkS88(1999, true);
        broadcast(marker);
        assertTrue(Components.waitFor(() -> this.model.heardFrame(marker), WAIT_MS), "the marker was not heard");
    }

    /**
     * Over UDP a real event after the answer beats a copy of the answer that lands after it (the CS3 sends 2 to 4):
     * the sensor ends at the event's state.
     *
     * MUTATION: in `NetworkProxy.learn`, `return older ? Heard.OVERTAKEN` to `return ours && older ? Heard.OVERTAKEN`.
     */
    @Test(timeOut = 30000)
    public void testACopyOfTheAnswerAfterAnEventIsDropped() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            Thread syncing = syncingOverUdp(wire, 1902);
            byte[] answer = answer(TcpStation.LINK_S88, 1902, true);
            broadcast(answer);
            broadcast(linkS88(1902, false));
            broadcast(answer);
            allRead();

            assertEquals(this.model.lastState(1902), Boolean.FALSE, "the copy of the answer won over the later event: "
                + this.model.states(1902));
            syncing.join(WAIT_MS);
        }
    }

    /**
     * Over UDP a sync asks twice, as a resync does: a late copy of an older event that lands after the first query
     * makes the first answer look older than it, and the second pass, half a second on, puts the sensor right.
     *
     * MUTATION: in `NetworkProxy.syncSensors`, `overTcp ? 1 : 2` to `1`.
     */
    @Test(timeOut = 30000)
    public void testASyncOverUdpPutsRightALateCopyOfAnOlderEvent() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            Thread syncing = syncingOverUdp(wire, 1903);
            broadcast(linkS88(1903, false));
            broadcast(linkS88(1903, true));
            broadcast(answer(TcpStation.LINK_S88, 1903, false));
            allRead();

            assertTrue(Arrays.equals(nextOn(wire, 1500), query(TcpStation.LINK_S88, 1903)),
                "the contact was not asked a second time: " + this.model.lines);
            broadcast(answer(TcpStation.LINK_S88, 1903, false));
            syncing.join(WAIT_MS);

            assertEquals(this.model.lastState(1903), Boolean.FALSE, "the late copy's state was left: "
                + this.model.states(1903));
        }
    }

    /**
     * An answer counts as overtaken only shortly after the event that overtook it: a frame of no change long after an
     * event, as the station sends when another program asks, is delivered as before.
     *
     * MUTATION: in `NetworkProxy.learn`, `System.nanoTime() - event < RESYNC_WAIT_NS` to `true`.
     */
    @Test(timeOut = 30000)
    public void testAnOvertakenMarkLapses() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            syncingOverUdp(wire, 1904).join(WAIT_MS);
            broadcast(linkS88(1904, false));
            assertTrue(Components.waitFor(() -> this.model.heardFrame(linkS88(1904, false)), WAIT_MS),
                "precondition: the event was not heard");

            // Waiting is the point: the mark lapses RESYNC_WAIT_NS after the event
            Components.waitFor(() -> false, 1200);
            broadcast(answer(0, 1904, true));
            allRead();

            assertEquals(this.model.lastState(1904), Boolean.TRUE, "a frame long after the event was dropped");
        }
    }

    /**
     * Only an event after a contact's own query overtakes its answer: in a pass of 60, the last contact is made while the
     * first is asked, then clears unseen, and its answer, "clear", is the newer and stands.
     *
     * MUTATION: in `NetworkProxy.askOnce`, stamp `overtaken` for every contact as the pass is built, not as its query is
     * written.
     */
    @Test(timeOut = 30000)
    public void testAnEventBeforeItsOwnQueryDoesNotOvertakeItsAnswer() throws Exception
    {
        connectedToASilentStation();
        Map<Integer, Integer> listed = new HashMap<>();

        for (int c = 1801; c <= 1860; c++)
        {
            listed.put(c, TcpStation.LINK_S88);
            this.model.alsoKnown.add(c);
        }

        this.proxy.knowDevices(listed, new HashMap<>());
        this.station.onQuery(1801, () ->
        {
            this.station.sensor(1860, true);
            this.station.sensorUnseen(1860, false);
        });

        this.proxy.syncSensors();

        assertTrue(Components.waitFor(() -> Boolean.FALSE.equals(this.model.lastState(1860)), WAIT_MS),
            "the answer, newer than the event, was dropped: " + this.model.states(1860));
    }

    /**
     * The marks go once the ask is over: an event long after it overtakes nothing, and a frame of no change a moment
     * after the event, as another program's answer, is delivered.
     *
     * MUTATION: in `NetworkProxy.learn`, take out the `overtaken.clear()`.
     */
    @Test(timeOut = 30000)
    public void testTheMarksGoOnceTheAskIsOver() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            syncingOverUdp(wire, 1907).join(WAIT_MS);

            // Waiting is the point: the marks stay RESYNC_WAIT_NS after the ask, for its late copies
            Components.waitFor(() -> false, 1200);
            broadcast(linkS88(1907, false));
            broadcast(answer(0, 1907, true));
            allRead();

            assertEquals(this.model.lastState(1907), Boolean.TRUE, "a frame after an event long after the ask was dropped: "
                + this.model.states(1907));
        }
    }

    /**
     * At start-up every frame the station sent before TCP took over is delivered, once and in order, none set aside:
     * 500 queued on the UDP socket before the model is set, the first held until the start waits or TCP connects.
     *
     * MUTATION: in `NetworkProxy.startTcp`, take out `awaitTheBacklog();`.
     */
    @Test(timeOut = 30000)
    public void testEveryFrameBeforeTheConnectIsDeliveredOnce() throws Exception
    {
        this.station = new TcpStation(STATION);
        this.proxy = new NetworkProxy(InetAddress.getByName(STATION));
        this.proxy.connectOverTcp(this.station.port());
        List<byte[]> sent = new ArrayList<>();
        int port = ((DatagramSocket) field("socket")).getLocalPort();

        try (DatagramSocket out = new DatagramSocket())
        {
            for (int c = 1; c <= 500; c++)
            {
                sent.add(sensor(c, c % 2 == 0));
                out.send(new DatagramPacket(sent.get(c - 1), CS2Message.MESSAGE_LENGTH, InetAddress.getByName("127.0.0.1"),
                    port));
            }
        }

        Thread starting = new Thread(() -> this.proxy.setModel(this.model), "tcp-claim-start");
        this.model.holdFirst = () -> this.station.connections() == 1 || starting.getState() == Thread.State.TIMED_WAITING;
        starting.start();
        starting.join(WAIT_MS);
        assertTrue(logged("network.tcpConnected", WAIT_MS), "precondition: no connection: " + this.model.lines);

        List<byte[]> heard = new ArrayList<>(this.model.heard);
        assertEquals(heard.size(), sent.size(), "not every frame from before the connect was delivered, once");

        for (int i = 0; i < sent.size(); i++)
        {
            assertTrue(Arrays.equals(heard.get(i), sent.get(i)), "frame " + i + " out of order");
        }
    }

    /**
     * A query the proxy could not write, the connection being replaced, is not counted as asked.
     *
     * MUTATION: in `NetworkProxy.askOnce`, `if (written) sent++;` to `sent++;`.
     */
    @Test(timeOut = 30000)
    public void testAQueryNotWrittenIsNotCounted() throws Exception
    {
        connectedToASilentStation();
        occupiedAndHeard(1905);

        java.lang.reflect.Field replacing = Reflect.field(NetworkProxy.class, "replacing");
        replacing.setBoolean(this.proxy, true);

        try
        {
            this.proxy.syncSensors();
        }
        finally
        {
            replacing.setBoolean(this.proxy, false);
        }

        assertTrue(this.model.lines.contains(I18n.f("network.sensorsSynced", 0, 0, 0)),
            "a query not written was counted: " + this.model.lines);
        assertTrue(this.station.queries().isEmpty(), "precondition: the query was written");
    }

    /** The queries the station received, as {device, contact}, sorted. */
    private String asked()
    {
        TreeSet<String> out = new TreeSet<>();

        for (int[] q : this.station.queries()) out.add(Arrays.toString(q));

        return out.toString();
    }

    /** Whether, within WAIT_MS, `line` is logged after the line of `key`. */
    private boolean loggedAfter(String key, String line)
    {
        return Components.waitFor(() ->
        {
            List<String> lines = new ArrayList<>(this.model.lines);
            int from = lines.indexOf(I18n.f(key));
            return from >= 0 && lines.subList(from, lines.size()).contains(line);
        }, WAIT_MS);
    }

    /**
     * A UDP copy lagging past the first resync's answer is put right by a second pass half a second after the loss,
     * asked by 0.8 s, not left wrong until a reconnect try fails; also when some of the first pass's queries go
     * unanswered, here four on a device the station does not have.
     *
     * MUTATION: in `NetworkProxy.recover`, `resync(false, 2);` to `resync(false, 1);`.  And: in `NetworkProxy.ask`,
     * `n + 1 < passes ? start + RESYNC_AGAIN_NS : 0` to `0` (the first pass waits out its unanswered queries).
     */
    @Test(timeOut = 30000)
    public void testALateUdpCopyAfterTheUdpResyncIsPutRightByASecondPass() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedToASilentStation();
            this.station.sensor(1602, true);
            this.station.sensor(1602, false);
            for (int c = 1690; c < 1694; c++) this.station.send(TcpStation.event(3, c, true));

            assertTrue(Components.waitFor(() -> this.model.heard.size() == 6, WAIT_MS), "precondition: not carried");

            long lossSeen = closedAndAskedOverUdp(wire, 1602);

            for (int c = 1690; c < 1694; c++)
            {
                assertTrue(Arrays.equals(nextOn(wire, 1000), query(3, c)), "contact " + c + " not asked: " + this.model.lines);
            }

            broadcast(linkS88(1602, true));
            assertTrue(Components.waitFor(() -> Boolean.TRUE.equals(this.model.lastState(1602)), WAIT_MS),
                "precondition: the late copy was not delivered");

            // The second pass, due 0.5 s after the loss: by 0.8 s, before the first reconnect try (1 s) could cancel it
            askedAndAnsweredOverUdp(wire, 1602, Math.max(1, 800 - (System.nanoTime() - lossSeen) / 1000000L));
            assertEndsAtTheStationsState(1602);
        }
    }

    /**
     * A resync says how many sensors answered, here none (the station ignores a device it does not have), and its
     * queries are not commands: the next drop counts none of them as ones that may not have arrived.
     *
     * MUTATION: in `NetworkProxy.askOnce`, log `queries.size()` for `got`.  And: in `NetworkProxy.sendQuery`, add
     * `this.unconfirmed.addLast(System.nanoTime());` after the write.
     */
    @Test(timeOut = 30000)
    public void testAResyncSaysHowManyAnsweredAndIsNotSentAgain() throws Exception
    {
        connectedToASilentStation();
        this.station.send(TcpStation.event(3, 1401, true));
        assertTrue(Components.waitFor(() -> this.model.heardFrame(TcpStation.event(3, 1401, true)), WAIT_MS),
            "precondition: not carried");

        droppedAndResynced(1, 0, 0);
        this.station.drop();

        assertTrue(logged("network.tcpLostUsingUdp", WAIT_MS), "the second loss was not found: " + this.model.lines);
        assertEquals(this.model.count("network.framesMayNotHaveArrived"), 0, "a query counted as a command: "
            + this.model.lines);
    }

    /**
     * A stale state is never applied after a newer one.  UDP copies lagging TCP's frames, doubled as the CS3 sends
     * some, are set aside while TCP runs: a contact TCP took occupied then clear, with UDP's occupied twice after;
     * one TCP took clear then occupied, UDP's clear twice; one whose clear's UDP copy never came.  After a drop and
     * the resync each has only TCP's states, ending at the station's.
     *
     * MUTATION: in `NetworkProxy.deliverUdp`, `if (!this.udpLive)` to `if (false)`.
     */
    @Test(timeOut = 30000)
    public void testAStaleUdpCopyIsNeverAppliedAfterANewerState() throws Exception
    {
        connectedToASilentStation();

        int[] contacts = {1111, 1112, 1500};
        boolean[][] tcp = {{true, false}, {false, true}, {true, false}};
        boolean[][] udp = {{true, true, false}, {false, false, true}, {true}};

        for (int c = 0; c < contacts.length; c++)
        {
            for (boolean state : tcp[c]) this.station.sensor(contacts[c], state);

            byte[] last = linkS88(contacts[c], tcp[c][tcp[c].length - 1]);
            assertTrue(Components.waitFor(() -> this.model.heardFrame(last), WAIT_MS), "precondition: TCP did not carry");

            for (boolean state : udp[c]) broadcastSetAside(linkS88(contacts[c], state));
        }

        this.station.drop();
        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);

        for (int c = 0; c < contacts.length; c++)
        {
            byte[] truth = answer(TcpStation.LINK_S88, contacts[c], this.station.occupied(contacts[c]));
            assertTrue(Components.waitFor(() -> this.model.heardFrame(truth), WAIT_MS), "no resync of " + contacts[c]);
            assertEquals(this.model.states(contacts[c]), Arrays.asList(tcp[c][0], tcp[c][1]),
                "contact " + contacts[c] + " was handed a stale state");
        }
    }

    /**
     * UDP copies that arrive after the loss was found are live again, and may be stale: a contact TCP took occupied
     * then clear, whose occupied's copy comes late, reads occupied until the resync puts it back to clear.
     *
     * MUTATION: as testASensorChangedDuringADropIsPutRightOnTheNewConnection.
     */
    @Test(timeOut = 30000)
    public void testALateUdpCopyAfterTheLossIsPutRightByTheResync() throws Exception
    {
        connectedToASilentStation();
        this.station.sensor(1600, true);
        this.station.sensor(1600, false);
        assertTrue(Components.waitFor(() -> this.model.heardFrame(linkS88(1600, false)), WAIT_MS), "precondition");

        this.station.drop();
        assertTrue(logged("network.tcpLostReconnecting", WAIT_MS), "the loss was not found: " + this.model.lines);
        broadcast(linkS88(1600, true));
        assertTrue(Components.waitFor(() -> this.model.times(linkS88(1600, true)) == 2, WAIT_MS),
            "precondition: the late copy was not delivered");

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);
        assertTrue(Components.waitFor(() -> Boolean.FALSE.equals(this.model.lastState(1600)), WAIT_MS),
            "the sensor ended at the late copy's stale state: " + this.model.states(1600));
    }

    /**
     * Twenty Link S88 contacts change every 10 ms in turn, each change on TCP and as a UDP copy 100 ms later, through
     * three drops, the last with every contact changed while nothing carried it: every contact ends at the station's
     * state.
     *
     * MUTATION: as testASensorChangedDuringADropIsPutRightOnTheNewConnection.
     */
    @Test(timeOut = 120000)
    public void testEverySensorEndsAtTheStationsStateThroughDrops() throws Exception
    {
        connectedToASilentStation();

        final int first = 1700;
        final int count = 20;
        final AtomicBoolean feeding = new AtomicBoolean(true);
        ScheduledExecutorService udp = Executors.newSingleThreadScheduledExecutor();

        Thread feeder = new Thread(() ->
        {
            for (int k = 0; feeding.get() && k < 6000; k++)
            {
                int contact = first + k % count;
                boolean occupied = (k / count) % 2 == 0;
                this.station.sensor(contact, occupied);

                byte[] copy = linkS88(contact, occupied);
                udp.schedule(() -> broadcastQuietly(copy), 100, TimeUnit.MILLISECONDS);

                // A change every 10 ms, as a busy layout reports
                LockSupport.parkNanos(10000000L);
            }
        }, "tcp-claim-feeder");

        try
        {
            feeder.start();

            for (int drop = 1; drop <= 3; drop++)
            {
                final int want = drop * 150;
                assertTrue(Components.waitFor(() -> this.model.heard.size() >= want, 20000), "stalled at drop " + drop);

                if (drop == 3)
                {
                    feeding.set(false);
                    feeder.join(WAIT_MS);
                }

                this.station.drop();

                if (drop == 3)
                {
                    for (int c = first; c < first + count; c++) this.station.sensorUnseen(c, !this.station.occupied(c));
                }

                final int reconnects = drop;
                assertTrue(Components.waitFor(() -> this.model.count("network.tcpReconnected") >= reconnects, 15000),
                    "no reconnect after drop " + drop + ": " + this.model.lines);
            }

            assertTrue(Components.waitFor(() ->
            {
                for (int c = first; c < first + count; c++)
                {
                    if (!Boolean.valueOf(this.station.occupied(c)).equals(this.model.lastState(c))) return false;
                }

                return true;
            }, WAIT_MS), "a sensor did not end at the station's state: " + this.model.lines);
        }
        finally
        {
            feeding.set(false);
            udp.shutdownNow();
        }
    }

    private void broadcastQuietly(byte[] frame)
    {
        try
        {
            broadcast(frame);
        }
        catch (Exception closed)
        {
            // The proxy was stopped
        }
    }

    /**
     * A proxy never asked for TCP, as a CS2's is, hears the station's UDP frames as before, and keeps no sensor's
     * device: it never asks a sensor anything.
     *
     * MUTATION: in `NetworkProxy.deliverUdp`, `if (!this.udpLive)` to `if (!this.udpLive || this.tcpPort == 0)`.  And:
     * in `NetworkProxy.learn`, take out `!this.learnsDevices || `.
     */
    @Test(timeOut = 30000)
    public void testAProxyNeverAskedForTcpHearsUdp() throws Exception
    {
        this.proxy = new NetworkProxy(InetAddress.getByName("127.0.0.1"));
        this.proxy.setModel(this.model);
        assertReachedOverUdp();

        byte[] event = linkS88(1801, true);
        broadcast(event);
        assertTrue(Components.waitFor(() -> this.model.heardFrame(event), WAIT_MS), "the event was not heard");
        assertTrue(((Map<?, ?>) field("sensorDevice")).isEmpty(), "a proxy in UDP mode learned a sensor's device");
    }

    /**
     * Frames the station has answered are not counted at a drop as ones that may not have arrived.
     *
     * MUTATION: in `NetworkProxy.recover`, take out `forgetReceived();`.
     */
    @Test(timeOut = 30000)
    public void testAnsweredFramesAreNotCountedAsMissing() throws Exception
    {
        connectedAndAnswered();

        for (int n = 0; n < 100; n++) this.proxy.sendMessage(new CS2Message(numbered(1, n)));

        byte[] last = answerTo(numbered(1, 99));
        assertTrue(Components.waitFor(() -> this.model.heardFrame(last), WAIT_MS), "the frames were not answered");

        // Waiting is the point: a frame heard past the margin after the frames makes them count as received
        Components.waitFor(() -> false, 400);
        this.station.send(sensor(1009, true));
        assertTrue(Components.waitFor(() -> this.model.heardFrame(sensor(1009, true)), WAIT_MS), "precondition: not heard");

        this.station.drop();

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);
        assertEquals(this.model.count("network.framesMayNotHaveArrived"), 0,
            "answered frames were counted as ones that may not have arrived: " + this.model.lines);
    }

    /**
     * A Stop on a connection whose send has gone unanswered for a second goes over UDP at once as well, as the
     * connection may be hung; one sent before that goes on the connection only.
     *
     * MUTATION: in `NetworkProxy.sendMessage`, `isUrgentStop(message) && suspect() && sendUdp(message)` to `false`.
     */
    @Test(timeOut = 30000)
    public void testAStopOnAnUnansweredConnectionAlsoGoesOverUdp() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedAndCarried();

            byte[] stop = Packets.powerStop().getRawMessage();
            long unanswered = System.nanoTime();
            this.proxy.sendMessage(new CS2Message(ping(1)));
            byte[] overUdp = null;
            int stops = 0;

            // A Stop every 100 ms until one comes over UDP
            while (overUdp == null && System.nanoTime() - unanswered < 5000000000L)
            {
                this.proxy.sendMessage(new CS2Message(stop));
                stops++;
                overUdp = nextOn(wire, 100);
            }

            long after = System.nanoTime() - unanswered;
            final int sent = stops;

            assertTrue(Arrays.equals(overUdp, stop), "no Stop went over UDP: " + this.model.lines);
            assertTrue(after >= 1000000000L, "a Stop went over UDP " + after / 1000000 + " ms after the unanswered send");
            assertTrue(Components.waitFor(() -> this.station.received().stream().filter(f -> Arrays.equals(f.bytes, stop))
                .count() == sent, WAIT_MS), "the Stops did not all go on the connection too");
        }
    }

    /**
     * Commands given while the connection is replaced go over UDP at once, in order (a Go after a speed 0 does not
     * overtake it), and are not sent again on the new connection (FR-186).
     *
     * MUTATION: in `NetworkProxy.sendMessage`, `if (connection == null || this.replacing) return sendUdp(message);` to
     * `if (connection == null) return sendUdp(message); if (this.replacing) return true;`.
     */
    @Test(timeOut = 30000)
    public void testACommandDuringAReconnectGoesOverUdpAtOnce() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedAndCarried();
            this.station.drop();
            assertTrue(logged("network.tcpLostReconnecting", WAIT_MS), "the loss was not found: " + this.model.lines);

            byte[] stopTrain = speed(0);
            byte[] go = Packets.powerGo().getRawMessage();
            this.proxy.sendMessage(new CS2Message(stopTrain));
            this.proxy.sendMessage(new CS2Message(go));

            assertTrue(Arrays.equals(nextOn(wire, 1000), stopTrain), "the speed 0 did not go over UDP at once");
            assertTrue(Arrays.equals(nextOn(wire, 1000), go), "the Go did not follow it over UDP");
            assertNotOnTheNewConnection(stopTrain, go);
        }
    }

    /**
     * A frame written to a connection that then closed for good is not sent over UDP when TCP is given up.
     *
     * MUTATION: in `NetworkProxy`, keep each frame with its time in `unconfirmed`, and in `recover` send those counted
     * at the loss over UDP where the fallback sets `this.tcp = null`.
     */
    @Test(timeOut = 60000)
    public void testAFrameWrittenBeforeTcpIsGivenUpIsNotSentOverUdp() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedAndWritten(Packets.powerStop().getRawMessage());
            this.station.close();

            assertTrue(logged("network.tcpReconnectFailed", 30000), "no fallback to UDP: " + this.model.lines);

            // Waiting is the point: nothing may go over UDP
            assertEquals(nextOn(wire, 1000), null, "the Stop was sent over UDP when TCP was given up");
        }
    }

    /**
     * A station that takes the connection but never sends on it is left for UDP once a send goes unanswered for
     * 3 s: the station's broadcasts are heard, and what was sent over TCP is counted, not sent over UDP.
     *
     * MUTATION: in `NetworkProxy.recover`, `boolean retry = this.carried;` to `boolean retry = true;`.
     */
    @Test(timeOut = 60000)
    public void testASilentStationIsLeftForUdp() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedToASilentStation();

            List<byte[]> sent = new ArrayList<>();

            for (int i = 0; i < 5; i++) sent.add(ping(i));

            sent.add(Packets.powerGo().getRawMessage());

            for (byte[] frame : sent) this.proxy.sendMessage(new CS2Message(frame));

            assertTrue(logged("network.tcpLostUsingUdp", 25000), "no fallback to UDP: " + this.model.lines);

            byte[] occupied = sensor(1010, true);
            broadcast(occupied);
            assertTrue(Components.waitFor(() -> this.model.heardFrame(occupied), WAIT_MS),
                "a frame broadcast after the fallback never reached the model");

            // Waiting is the point: nothing may go over UDP
            assertEquals(nextOn(wire, 1000), null, "a frame sent over TCP was sent over UDP too");

            assertTrue(this.model.lines.contains(I18n.f("network.framesMayNotHaveArrived", sent.size())),
                "the frames sent over TCP are not counted: " + this.model.lines);
        }
    }

    /**
     * Three senders while the connection is dropped three times: no send waits, no frame reaches the station twice
     * over TCP and UDP together, none is out of order on a connection, and no more are missing than the log counted
     * as ones that may not have arrived (FR-186).
     *
     * MUTATION: as testACommandDuringAReconnectGoesOverUdpAtOnce.
     */
    @Test(timeOut = 120000)
    public void testSendersDuringDropsNeverWaitOrRepeat() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            List<byte[]> overUdp = Collections.synchronizedList(new ArrayList<>());
            AtomicBoolean collecting = new AtomicBoolean(true);
            wire.setReceiveBufferSize(1 << 20);

            Thread collector = new Thread(() ->
            {
                try
                {
                    while (collecting.get())
                    {
                        byte[] f = nextOn(wire, 100);

                        if (f != null && f.length == CS2Message.MESSAGE_LENGTH && f[1] == 0x30) overUdp.add(f);
                    }
                }
                catch (Exception e)
                {
                    throw new IllegalStateException(e);
                }
            }, "tcp-claim-udp-wire");

            collector.start();

            this.station = new TcpStation(STATION).answering(true);
            connect(this.station.port());
            assertTrue(Components.waitFor(() -> this.station.connections() == 1, WAIT_MS), "precondition: no connection");

            final int threads = 3;
            final int[] sentBy = new int[threads + 1];
            final AtomicBoolean sending = new AtomicBoolean(true);
            final AtomicLong longestSend = new AtomicLong();
            List<Thread> senders = new ArrayList<>();

            for (int t = 1; t <= threads; t++)
            {
                final int sender = t;
                Thread thread = new Thread(() ->
                {
                    for (int n = 0; sending.get(); n++)
                    {
                        long start = System.nanoTime();
                        this.proxy.sendMessage(new CS2Message(numbered(sender, n)));
                        longestSend.accumulateAndGet(System.nanoTime() - start, Math::max);
                        sentBy[sender] = n + 1;

                        // A command every few milliseconds from each, as a busy railway sends
                        LockSupport.parkNanos(3000000L);
                    }
                }, "tcp-claim-sender-" + t);

                senders.add(thread);
                thread.start();
            }

            // Each connection carries a while, and has been answered, before it is dropped: the last one too
            for (int connection = 1; connection <= 4; connection++)
            {
                final int on = connection;
                assertTrue(Components.waitFor(() -> this.station.receivedOn(on).size() >= 150, 30000),
                    "stalled on connection " + on + ": " + this.model.lines);

                if (on == 4) break;

                this.station.drop();
                assertTrue(Components.waitFor(() -> this.model.count("network.tcpReconnected") >= on, 15000),
                    "no reconnect after drop " + on + ": " + this.model.lines);
            }

            sending.set(false);

            for (Thread thread : senders) thread.join(60000);

            for (int t = 1; t <= threads; t++)
            {
                byte[] last = numbered(t, sentBy[t] - 1);
                assertTrue(Components.waitFor(() -> this.station.received().stream().anyMatch(f -> Arrays.equals(f.bytes, last))
                    || new ArrayList<>(overUdp).stream().anyMatch(f -> Arrays.equals(f, last)),
                    10000), "sender " + t + "'s last frame never arrived");
            }

            collecting.set(false);
            collector.join(WAIT_MS);

            List<TcpStation.Frame> all = this.station.received();
            Map<Integer, Integer> seen = new HashMap<>();
            Map<Integer, Integer> lastOn = new HashMap<>();
            int disorder = 0;

            for (TcpStation.Frame f : all)
            {
                int id = f.bytes[5] << 16 | ((f.bytes[6] & 0xff) << 8) | (f.bytes[7] & 0xff);
                seen.merge(id, 1, Integer::sum);
                Integer before = lastOn.get(f.connection * 8 + f.bytes[5]);

                if (before != null && id <= before) disorder++;

                lastOn.put(f.connection * 8 + f.bytes[5], id);
            }

            for (byte[] f : overUdp) seen.merge(f[5] << 16 | ((f[6] & 0xff) << 8) | (f[7] & 0xff), 1, Integer::sum);

            int twice = 0;
            int missing = 0;

            for (int t = 1; t <= threads; t++)
            {
                for (int n = 0; n < sentBy[t]; n++)
                {
                    int times = seen.getOrDefault(t << 16 | n, 0);

                    if (times > 1) twice++;
                    if (times == 0) missing++;
                }
            }

            int counted = this.model.sums.getOrDefault("network.framesMayNotHaveArrived", 0);
            String summary = "connections=" + this.station.connections() + " tcp=" + all.size() + " udp=" + overUdp.size()
                + " log=" + this.model.lines;
            assertEquals(twice, 0, "frames reached the station twice: " + summary);
            assertEquals(disorder, 0, "frames out of order on one connection: " + summary);
            assertTrue(missing <= counted, missing + " frames missing, " + counted + " counted: " + summary);
            assertTrue(longestSend.get() < 500000000L, "a send waited " + longestSend.get() / 1000000 + " ms: " + summary);
        }
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
     * The log says once, at the loss, how many frames written to the connection may not have arrived (FR-186).
     *
     * MUTATION: in `NetworkProxy.recover`, take out `if (cutOff > 0) model.logf("network.framesMayNotHaveArrived", ...`.
     */
    @Test(timeOut = 30000)
    public void testTheLogSaysHowManyMayNotHaveArrived() throws Exception
    {
        connectedAndWritten(ping(1), ping(2), ping(3));
        this.station.drop();

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);

        List<String> keys = new ArrayList<>(this.model.keys);
        assertEquals(keys.indexOf("network.framesMayNotHaveArrived"), keys.indexOf("network.tcpLostReconnecting") + 1,
            "not logged at the loss: " + this.model.lines);
        assertEquals(this.model.count("network.framesMayNotHaveArrived"), 1, "not logged once: " + this.model.lines);
        assertTrue(this.model.lines.contains(I18n.f("network.framesMayNotHaveArrived", 3)), "the count is not 3: "
            + this.model.lines);
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
