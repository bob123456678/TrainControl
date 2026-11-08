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
 * the next connect fails.
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

    public TcpStation() throws IOException
    {
        this.server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));

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

    private Socket last()
    {
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

                    if (this.answering)
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
