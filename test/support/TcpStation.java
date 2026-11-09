package support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.NetworkProxy;

/**
 * A loopback stand-in for a Central Station's TCP port (FR-186), on a port of the operating system's choosing.  It
 * keeps every 13-byte frame a client writes, with the number of the connection it came on (1 for the first), and
 * sends frames or raw bytes to the client connected last.  With `answering` on it answers each frame as a station does,
 * with the same frame and the response bit set.  `drop` closes the client's connection; `refuse` stops accepting, so
 * the next connect fails.  It is also a Link S88, device LINK_S88: it keeps its contacts' states, reports a change as
 * the event a station sends, and answers a sensor query for its device with the current state, as Adam's CS3 does.
 *
 * @author Adam
 */
public final class TcpStation implements AutoCloseable
{
    /** One frame a client sent. */
    public static final class Frame
    {
        public final int connection;
        public final byte[] bytes;

        Frame(int connection, byte[] bytes)
        {
            this.connection = connection;
            this.bytes = bytes;
        }
    }

    private final ServerSocket server;

    private final List<Frame> received = Collections.synchronizedList(new ArrayList<>());

    private final List<Socket> clients = Collections.synchronizedList(new ArrayList<>());

    private final AtomicInteger connections = new AtomicInteger();

    private final java.util.Set<Integer> ended = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private volatile boolean answering;

    /** The Link S88's device number on Adam's CS3. */
    public static final int LINK_S88 = 2;

    // The contacts' states; with each write, guarded by this, so an answer and an event go out in the order they happen
    private final java.util.Map<Integer, Boolean> sensors = new java.util.HashMap<>();

    public TcpStation() throws IOException
    {
        this("127.0.0.1");
    }

    /** On a loopback address of the test's own, so that a test can also take UDP 15731 there without a clash. */
    public TcpStation(String address) throws IOException
    {
        this.server = new ServerSocket(0, 50, InetAddress.getByName(address));

        Thread accepting = new Thread(this::accept, "tcp-station-accept");
        accepting.setDaemon(true);
        accepting.start();
    }

    /** The port to give the proxy. */
    public int port()
    {
        return this.server.getLocalPort();
    }

    /** Whether each frame received is answered with itself, response bit set. */
    public TcpStation answering(boolean on)
    {
        this.answering = on;
        return this;
    }

    /** How many connections have been accepted. */
    public int connections()
    {
        return this.connections.get();
    }

    /** Every frame received so far, in order. */
    public List<Frame> received()
    {
        synchronized (this.received)
        {
            return new ArrayList<>(this.received);
        }
    }

    /** The frames received on one connection. */
    public List<byte[]> receivedOn(int connection)
    {
        List<byte[]> out = new ArrayList<>();

        for (Frame f : received())
        {
            if (f.connection == connection) out.add(f.bytes);
        }

        return out;
    }

    /** Writes bytes, a frame, part of one or several, to the client connected last. */
    public void send(byte[] bytes) throws IOException
    {
        Socket client = last();

        synchronized (client)
        {
            OutputStream out = client.getOutputStream();
            out.write(bytes);
            out.flush();
        }
    }

    /** A sensor event as a station sends it: `device`'s contact changed to `occupied`. */
    public static byte[] event(int device, int contact, boolean occupied)
    {
        return sensorFrame(device, contact, !occupied, occupied);
    }

    /** A station's answer to a query: old and new state both `occupied`. */
    public static byte[] answer(int device, int contact, boolean occupied)
    {
        return sensorFrame(device, contact, occupied, occupied);
    }

    private static byte[] sensorFrame(int device, int contact, boolean was, boolean is)
    {
        return new CS2Message(CS2Message.CMD_ACC_SENSOR, 0x7b34, true, new byte[]
        {
            (byte) (device >> 8), (byte) device, (byte) (contact >> 8), (byte) contact,
            (byte) (was ? 1 : 0), (byte) (is ? 1 : 0), 0, 0
        }).getRawMessage();
    }

    /** A contact changes, and the event goes to the client connected last; while none is connected it is lost. */
    public synchronized void sensor(int contact, boolean occupied)
    {
        this.sensors.put(contact, occupied);

        try
        {
            send(event(LINK_S88, contact, occupied));
        }
        catch (IOException | IllegalStateException lost)
        {
            // As on a real connection that is down
        }
    }

    /** A contact changes and no event reaches anyone: the change a resync must find. */
    public synchronized void sensorUnseen(int contact, boolean occupied)
    {
        this.sensors.put(contact, occupied);
    }

    // Contacts whose next query is answered late: after an event that changes them, with the state from before
    private final java.util.Set<Integer> changesBeforeAnswering = new java.util.HashSet<>();

    /** The next query for the contact is answered with its state, but only after it changes: a late answer. */
    public synchronized void changeBeforeAnswering(int contact)
    {
        this.changesBeforeAnswering.add(contact);
    }

    /** A contact's state as this station knows it. */
    public synchronized boolean occupied(int contact)
    {
        return Boolean.TRUE.equals(this.sensors.get(contact));
    }

    /** The sensor queries received, as {device, contact}, in order. */
    public List<int[]> queries()
    {
        List<int[]> out = new ArrayList<>();

        for (Frame f : received())
        {
            if (isQuery(f.bytes)) out.add(new int[] {((f.bytes[5] & 0xff) << 8) | (f.bytes[6] & 0xff),
                ((f.bytes[7] & 0xff) << 8) | (f.bytes[8] & 0xff)});
        }

        return out;
    }

    private static boolean isQuery(byte[] frame)
    {
        return (frame[1] & 0xff) == (CS2Message.CMD_ACC_SENSOR << 1) && frame[4] == 4;
    }

    /** Answers a query for this device with the contact's state; another device's is ignored, as a station does. */
    private synchronized void answerQuery(Socket client, byte[] query) throws IOException
    {
        int device = ((query[5] & 0xff) << 8) | (query[6] & 0xff);
        int contact = ((query[7] & 0xff) << 8) | (query[8] & 0xff);

        if (device != LINK_S88) return;

        boolean state = occupied(contact);

        if (this.changesBeforeAnswering.remove(contact)) sensor(contact, !state);

        synchronized (client)
        {
            client.getOutputStream().write(answer(device, contact, state));
        }
    }

    /** Closes the last client's connection. */
    public void drop() throws IOException
    {
        last().close();
    }

    /** Whether a connection has ended: the client closed it, or drop did. */
    public boolean ended(int connection)
    {
        return this.ended.contains(connection);
    }

    /** Stops accepting connections: a connect to the port then fails. */
    public void refuse() throws IOException
    {
        this.server.close();
    }

    @Override
    public void close() throws IOException
    {
        refuse();

        synchronized (this.clients)
        {
            for (Socket s : this.clients) s.close();
        }
    }

    /** The client connected last, waited for: a client's connect returns before this side has accepted it. */
    private Socket last()
    {
        Components.waitFor(() -> !this.clients.isEmpty(), 5000);

        synchronized (this.clients)
        {
            if (this.clients.isEmpty()) throw new IllegalStateException("no client has connected");
            return this.clients.get(this.clients.size() - 1);
        }
    }

    private void accept()
    {
        try
        {
            while (!this.server.isClosed())
            {
                Socket client = this.server.accept();
                int number = this.connections.incrementAndGet();
                this.clients.add(client);

                Thread reading = new Thread(() -> read(client, number), "tcp-station-client-" + number);
                reading.setDaemon(true);
                reading.start();
            }
        }
        catch (IOException closed)
        {
            // refuse() or close()
        }
    }

    /** Keeps the client's frames, reassembled at 13 bytes. */
    private void read(Socket client, int number)
    {
        byte[] frame = new byte[CS2Message.MESSAGE_LENGTH];
        int filled = 0;

        try
        {
            InputStream in = client.getInputStream();

            for (int n; (n = in.read(frame, filled, frame.length - filled)) >= 0; )
            {
                filled += n;

                if (filled == frame.length)
                {
                    filled = 0;
                    byte[] copy = Arrays.copyOf(frame, frame.length);
                    this.received.add(new Frame(number, copy));

                    if (isQuery(copy))
                    {
                        answerQuery(client, copy);
                    }
                    else if (this.answering)
                    {
                        copy = Arrays.copyOf(copy, copy.length);
                        copy[1] |= 1;

                        synchronized (client)
                        {
                            client.getOutputStream().write(copy);
                        }
                    }
                }
            }
        }
        catch (IOException closed)
        {
            // The connection closed
        }

        this.ended.add(number);
    }

    /** Runs work with simulations connecting over TCP to a port, and puts the setting back. */
    public static <T> T forSimulations(int port, java.util.concurrent.Callable<T> work) throws Exception
    {
        java.lang.reflect.Field field = NetworkProxy.class.getDeclaredField(NetworkProxy.TEST_TCP_PORT_FIELD);
        field.setAccessible(true);
        int was = field.getInt(null);
        field.setInt(null, port);

        try
        {
            return work.call();
        }
        finally
        {
            field.setInt(null, was);
        }
    }

    /** A loopback port nothing listens on, for a connect that fails. */
    public static int closedPort() throws IOException
    {
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))
        {
            return probe.getLocalPort();
        }
    }
}
