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

    /** A frame as the station would send it: a sensor event, from device 0, so never asked for. */
    private static byte[] sensor(int contact, boolean occupied)
    {
        return Packets.s88(contact, occupied).getRawMessage();
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

    /** A station's answer to a query: old and new state both the current one. */
    private static byte[] answer(int device, int contact, boolean occupied)
    {
        byte s = (byte) (occupied ? 1 : 0);
        return new CS2Message(CS2Message.CMD_ACC_SENSOR, 0x7b34, true, new byte[]
            {(byte) (device >> 8), (byte) device, (byte) (contact >> 8), (byte) contact, s, s, 0, 0}).getRawMessage();
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

    /** Broadcasts a frame and waits until the proxy has read it and set it aside, as it does every datagram over TCP. */
    private void broadcastSetAside(byte[] frame) throws Exception
    {
        Object lock = field("udpLock");
        java.lang.reflect.Field count = NetworkProxy.class.getDeclaredField("udpSetAside");
        count.setAccessible(true);
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
            this.station.sensor(1201, true);
            assertTrue(Components.waitFor(() -> this.model.heardFrame(linkS88(1201, true)), WAIT_MS), "precondition");

            this.station.sensorUnseen(1201, false);
            broadcastSetAside(linkS88(1201, false));
            assertEquals(this.model.lastState(1201), Boolean.TRUE, "precondition: the UDP frame was delivered over TCP");

            closedAndAskedOverUdp(wire, 1201);
            assertEndsAtTheStationsState(1201);
        }
    }

    /** The station closes; the loss is found and the contact is asked over UDP within 1 s, and answered there. */
    private void closedAndAskedOverUdp(DatagramSocket wire, int contact) throws Exception
    {
        this.station.close();
        assertTrue(logged("network.tcpLostReconnecting", WAIT_MS), "the loss was not found: " + this.model.lines);
        askedAndAnsweredOverUdp(wire, contact, 1000);
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
        this.station.sensor(1202, true);
        assertTrue(Components.waitFor(() -> this.model.heardFrame(linkS88(1202, true)), WAIT_MS), "precondition");

        this.station.drop();
        this.station.sensorUnseen(1202, false);

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);
        assertTrue(Components.waitFor(() -> Boolean.FALSE.equals(this.model.lastState(1202)), WAIT_MS),
            "the sensor did not end at the station's state: " + this.model.states(1202) + " " + this.model.lines);
    }

    /**
     * Only a contact whose device an event showed is asked, and only on a device that answers truly: not device 0, not
     * device 1 (the CS3 answers those itself, "occupied" for all), not a contact the model knows with no event, and
     * not on a device an answer named rather than an event.
     *
     * MUTATION: in `NetworkProxy`, `FIRST_QUERIED_DEVICE = 2` to `= 0`.  And: in `NetworkProxy.resync`,
     * `this.sensorDevice.get(contact)` to `this.sensorDevice.getOrDefault(contact, FIRST_QUERIED_DEVICE)`.  And: in
     * `NetworkProxy.learn`, take out `|| frame[9] == frame[10]`.
     */
    @Test(timeOut = 30000)
    public void testOnlySensorsSeenOnATruthfulDeviceAreAsked() throws Exception
    {
        connectedToASilentStation();
        this.model.alsoKnown.add(1304);

        this.station.sensor(1301, true);
        this.station.send(sensor(1302, true));
        this.station.send(TcpStation.event(1, 1303, true));
        this.station.send(answer(1, 1301, true));
        assertTrue(Components.waitFor(() -> this.model.heard.size() == 4, WAIT_MS), "precondition: not carried");

        droppedAndResynced(1, 1, 3);

        assertTrue(Components.waitFor(() -> this.model.heardFrame(answer(TcpStation.LINK_S88, 1301, true)), WAIT_MS),
            "the Link S88's contact was not asked over the new connection");
        assertEquals(this.station.queries().size(), 1, "other queries reached the station");
        assertEquals(Arrays.toString(this.station.queries().get(0)), "[2, 1301]", "the query is not for 2/1301");
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
     * A UDP copy lagging past the first resync's answer is put right by a second pass half a second later, not left
     * wrong until a reconnect try fails (a refused connect takes 2 s on Windows loopback).
     *
     * MUTATION: in `NetworkProxy.recover`, `resync(false, 2);` to `resync(false, 1);`.
     */
    @Test(timeOut = 30000)
    public void testALateUdpCopyAfterTheUdpResyncIsPutRightByASecondPass() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedToASilentStation();
            this.station.sensor(1602, true);
            this.station.sensor(1602, false);
            assertTrue(Components.waitFor(() -> this.model.heardFrame(linkS88(1602, false)), WAIT_MS), "precondition");

            closedAndAskedOverUdp(wire, 1602);

            broadcast(linkS88(1602, true));
            assertTrue(Components.waitFor(() -> Boolean.TRUE.equals(this.model.lastState(1602)), WAIT_MS),
                "precondition: the late copy was not delivered");

            // The second pass, before a reconnect try could fail and resync
            askedAndAnsweredOverUdp(wire, 1602, 1800);
            assertEndsAtTheStationsState(1602);
        }
    }

    /**
     * A resync says how many sensors answered, here none (the station ignores a device it does not have), and its
     * queries are not commands: the next drop sends none of them again.
     *
     * MUTATION: in `NetworkProxy.askOnce`, log `queries.size()` for `got`.  And: in `NetworkProxy.sendQuery`, add
     * `keep(query, System.nanoTime(), true);` before the write.
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
        assertEquals(this.model.count("network.framesSentAgain"), 0, "a query counted as a command: " + this.model.lines);
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
     * in `NetworkProxy.learn`, take out `this.tcpPort <= 0 || `.
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
     * Frames the station has answered are not kept to send again, so a busy connection never reaches the limit:
     * after 1,200 answered sends, a drop logs no command as not sent.
     *
     * MUTATION: in `NetworkProxy.writeTcp`, take out the loop that drops the frames taken as received.
     */
    @Test(timeOut = 60000)
    public void testAnsweredFramesAreNotKeptToTheLimit() throws Exception
    {
        connectedAndAnswered();

        for (int batch = 0; batch < 2; batch++)
        {
            for (int n = 0; n < 600; n++) this.proxy.sendMessage(new CS2Message(numbered(batch + 1, n)));

            byte[] last = answerTo(numbered(batch + 1, 599));
            assertTrue(Components.waitFor(() -> this.model.heardFrame(last), WAIT_MS), "batch " + batch + " not answered");

            // Waiting is the point: a frame heard past the margin after the batch makes the batch count as received
            Components.waitFor(() -> false, 400);
            this.station.send(sensor(1009 + batch, true));
            byte[] heard = sensor(1009 + batch, true);
            assertTrue(Components.waitFor(() -> this.model.heardFrame(heard), WAIT_MS), "precondition: not heard");
        }

        this.station.drop();

        assertTrue(logged("network.tcpReconnected", 15000), "no new connection: " + this.model.lines);
        assertEquals(this.model.count("network.framesDropped"), 0, "answered frames were counted as not sent: "
            + this.model.lines);
    }

    /**
     * A Stop sent while the connection is being replaced goes out over UDP at once, not after the tries.
     *
     * MUTATION: in `NetworkProxy.sendMessage`, take out `if (isUrgentStop(message) && suspect()) sendUdp(message);`.
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
     * A Go sent while the connection is replaced does not overtake a speed 0 sent before it: it is no Stop, so it
     * waits for the new connection, in order, and nothing goes over UDP.
     *
     * MUTATION: in `NetworkProxy.isUrgentStop`, add `|| sub == CS2Message.CMD_SYSSUB_GO`.
     */
    @Test(timeOut = 30000)
    public void testAGoDuringAReconnectWaitsBehindEarlierCommands() throws Exception
    {
        try (DatagramSocket wire = udpWire())
        {
            connectedAndCarried();
            this.station.drop();
            assertTrue(logged("network.tcpLostReconnecting", WAIT_MS), "the loss was not found: " + this.model.lines);

            byte[] stopTrain = new CS2Message(CS2Message.CMD_LOCO_VELOCITY, new byte[] {0, 0, 0, 5, 0, 0}).getRawMessage();
            byte[] go = Packets.powerGo().getRawMessage();
            this.proxy.sendMessage(new CS2Message(stopTrain));
            this.proxy.sendMessage(new CS2Message(go));

            // Waiting is the point: nothing may go over UDP
            assertEquals(nextOn(wire, 1000), null, "the Go went over UDP, ahead of the speed 0");

            assertTrue(Components.waitFor(() -> this.station.receivedOn(2).size() >= 2, 15000), "not sent: " + this.model.lines);
            assertEquals(this.station.receivedOn(2).get(0), stopTrain, "the speed 0 was not first");
            assertEquals(this.station.receivedOn(2).get(1), go, "the Go was not second");
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
     * 15 s: what was sent over TCP goes over UDP once each, and the station's broadcasts are heard.
     *
     * MUTATION: in `NetworkProxy.recover`, `for (Unconfirmed u : again) sendUdp(u.frame);` to
     * `for (Unconfirmed u : again) { sendUdp(u.frame); sendUdp(u.frame); }`.
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
