package org.traincontrol.marklin.udp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.model.ModelListener;
import org.traincontrol.util.I18n;

/**
 *  This class facilitates communication with the Marklin CS2/CS3: over one TCP connection to the station's port 15731
 *  when it accepts one, otherwise over UDP as before (FR-186).  Both carry the same 13-byte frames, sent the same and
 *  handed to the same model.receiveMessage.
 */
public class NetworkProxy
{
    // Ports as defined in the marklin protocol
    public static final int RX_PORT = 15730;
    public static final int TX_PORT = 15731;
    
    /** The station's TCP port: the same frames as a stream, resent when lost and in order. */
    public static final int TCP_PORT = 15731;

    // UDP socket used to send and receive packets.  volatile because sendMessage can replace it after
    // a failure while the reader thread is looping on it.  Bound in TCP mode too, unread, so that a second copy of
    // TrainControl still cannot start.
    private volatile DatagramSocket socket;

    /**
     * Whether this process may take ANY receive port instead of the Marklin one.
     *
     * Off, and it has to stay off for the application: a Central Station sends to 15730 and nowhere else, so a
     * TrainControl bound anywhere else would transmit perfectly and hear nothing back, which looks like a railway that
     * has stopped responding.  That is why this is not a silent fallback when the port is busy.
     *
     * It exists for tests: every class that builds a MarklinControlStation binds the port, so only one could run at a
     * time, and none receives anything from a Central Station anyway.  Read from a system property so the battery turns
     * it on with one flag: java -Dtraincontrol.anyReceivePort=true ...
     */
    public static final boolean ANY_RECEIVE_PORT =
        Boolean.getBoolean("traincontrol.anyReceivePort");

    /**
     * The TCP port a simulation connects to: none (0), unless a test points it at a stand-in station.  Set by reflection
     * through TEST_TCP_PORT_FIELD, as MarklinControlStation's test address is.
     */
    private static volatile int TEST_TCP_PORT = 0;

    /** The name of the field above, for a test that sets it. */
    public static final String TEST_TCP_PORT_FIELD = "TEST_TCP_PORT";

    // How long to wait after a recoverable receive error, so that a persistent fault cannot spin
    private static final long RECEIVE_ERROR_BACKOFF_MS = 50;

    /** The receive buffer asked for: about 80,000 frames, minutes of the station's busiest traffic (OB-421). */
    private static final int RECEIVE_BUFFER_BYTES = 1024 * 1024;
    
    /** Short, because a station that will not take TCP holds the start-up up by this much. */
    private static final int TCP_CONNECT_TIMEOUT_MS = 2000;

    private static final int TCP_RECONNECT_TRIES = 3;

    /** The pause before each reconnect, times the try's number. */
    private static final long TCP_RECONNECT_BACKOFF_MS = 1000;

    /**
     * A connection that has carried nothing for this long since a frame was sent on it is taken as hung: the station
     * answers every command, and the window pings every 5 s.  Silence alone is not, as a CS2 may send nothing unasked.
     */
    private static final long TCP_ANSWER_LIMIT_MS = 15000;

    /** How often a TCP read wakes to check the answer limit. */
    private static final int TCP_READ_WAKE_MS = 1000;

    /** The most frames kept to send again on a replacement connection; the oldest go first. */
    private static final int UNCONFIRMED_LIMIT = 1000;

    // Transmission IP/port
    private final InetAddress transmitIP;
    private final int transmitPort;
    
    // The station's TCP port, 0 for UDP only
    private final int tcpPort;

    // The TCP connection, null in UDP mode
    private volatile Socket tcp;

    /**
     * Frames sent since the connection last carried anything, and frames held while it is replaced: the station may
     * not have them, so a replacement sends them again, in order.  Guarded by this.
     */
    private final ArrayDeque<Unconfirmed> unconfirmed = new ArrayDeque<>();

    // Guarded by this: the listener is replacing a lost connection, and sends are held meanwhile
    private boolean replacing;

    // Guarded by this: frames dropped from `unconfirmed` at its limit since the last replacement
    private int unconfirmedDropped;

    // Why a send found the connection broken, for the listener's log line
    private volatile String lostBecause;

    // System.nanoTime of the last bytes read off the connection, and of the first frame sent after them
    private volatile long lastHeardAt;
    private volatile long firstUnansweredAt;

    // Whether the current connection has carried a frame; one that never has is not worth reconnecting
    private volatile boolean carried;

    // Why the first connection failed, logged once there is a model; null when it did not fail
    private String tcpRefusal;

    private volatile boolean stopRequested;

    // Model listener class reference
    private ModelListener model;

    /** One frame and when it was sent. */
    private static final class Unconfirmed
    {
        final byte[] frame;
        long at;

        Unconfirmed(byte[] frame, long at)
        {
            this.frame = frame;
            this.at = at;
        }
    }

    /**
     * Constructor: tries TCP first, as a real station is reached.
     * 
     * @param transmitIP - the IP to send to
     * 
     * @throws IOException on error with the socket
     */
    public NetworkProxy(InetAddress transmitIP) throws IOException
    {
        this(transmitIP, TCP_PORT);
    }

    /**
     * @param transmitIP the station's address
     * @param tcpPort the station's TCP port, or 0 for UDP only
     * @throws IOException when the UDP socket cannot be bound (a second copy of TrainControl)
     */
    public NetworkProxy(InetAddress transmitIP, int tcpPort) throws IOException
    {
        this.socket = openReceiveSocket();
        this.transmitIP = transmitIP;
        this.transmitPort = NetworkProxy.TX_PORT;
        this.tcpPort = tcpPort;

        if (tcpPort > 0)
        {
            try
            {
                useTcp(connectTcp());
            }
            catch (IOException e)
            {
                this.tcpRefusal = reasonOf(e);
            }
        }
    }

    /**
     * The TCP port a simulation is given: 0, so it tries no TCP (there is no station, and a refused connect to this
     * machine takes 2 s on Windows), unless a test set TEST_TCP_PORT.
     */
    public static int simulationTcpPort()
    {
        return TEST_TCP_PORT;
    }

    /**
     * The receive socket: the Marklin port, or any free one when ANY_RECEIVE_PORT says so.  One method because there
     * are two call sites, the constructor and the reopen in sendMessage, and a mode applied to only one would give a
     * test a socket that worked until the first send failure.
     */
    private static DatagramSocket openReceiveSocket() throws IOException
    {
        // Port 0 asks the operating system for any free port.
        DatagramSocket opened = ANY_RECEIVE_PORT ? new DatagramSocket() : new DatagramSocket(NetworkProxy.RX_PORT);

        // Room for the frames that arrive while the listener is held up; a full buffer drops them, sensor changes
        // included (OB-421).  A hint: the operating system may grant less, and the listener runs on whatever it got.
        try
        {
            opened.setReceiveBufferSize(RECEIVE_BUFFER_BYTES);
        }
        catch (SocketException refused)
        {
            // The default buffer is kept
        }

        return opened;
    }

    /** A connection to the station's TCP port; keepalive is on, though Java 8 cannot shorten its 2 hours. */
    private Socket connectTcp() throws IOException
    {
        Socket opened = new Socket();

        try
        {
            opened.setTcpNoDelay(true);
            opened.setKeepAlive(true);
            opened.connect(new InetSocketAddress(this.transmitIP, this.tcpPort), TCP_CONNECT_TIMEOUT_MS);
            opened.setSoTimeout(TCP_READ_WAKE_MS);
            return opened;
        }
        catch (IOException e)
        {
            opened.close();
            throw e;
        }
    }

    /** Makes a connected socket the transport. */
    private synchronized void useTcp(Socket connected)
    {
        this.lastHeardAt = System.nanoTime();
        this.firstUnansweredAt = 0;
        this.carried = false;
        this.tcp = connected;
    }

    /** An exception's message for a log line, or its name when it has none. */
    private static String reasonOf(IOException e)
    {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static void closeQuietly(Socket s)
    {
        try
        {
            if (s != null) s.close();
        }
        catch (IOException ignored)
        {
            // Nothing to do: it is being given up
        }
    }

    /** The granted receive buffer in KB, for the listener's start line, or "?" when the socket will not say. */
    private Object receiveBufferKb()
    {
        try
        {
            return this.socket.getReceiveBufferSize() / 1024;
        }
        catch (SocketException e)
        {
            return "?";
        }
    }
    
    /**
     * Gets the Central Station's address, as a literal IP suitable for building URLs
     * @return
     */
    public String getIP()
    {
        // InetAddress.toString() is "hostname/literal", with the hostname empty when none is known.
        // Stripping the slash therefore only worked for an address entered as a dotted quad, where the
        // hostname is null: given a hostname the two halves were concatenated into nonsense such as
        // "localhost127.0.0.1", every HTTP fetch then failed, and the operator was told the Central
        // Station was unreachable moments after the connection check had passed.
        return this.transmitIP.getHostAddress();
    }

    // The listener thread, kept so that `listen()` can tell whether one is still running.
    private ReadMessages reader;

    /**
     * Closes the socket, which is what ends the listener.  Nothing needs this while the application runs (the thread is
     * a daemon); it lets a caller that finishes with a control station give the port back.  The TCP connection is
     * closed first and not under the lock, which a send stuck in a write may hold; a later send uses UDP.
     */
    public void stopListening()
    {
        this.stopRequested = true;
        closeQuietly(this.tcp);

        synchronized (this)
        {
            this.tcp = null;
            this.replacing = false;
            this.unconfirmed.clear();
            notifyAll();

            if (this.socket != null && !this.socket.isClosed()) this.socket.close();
        }
    }

    /**
     * Sets the model field
     * 
     * @param model the model instance
     */
    public void setModel(ModelListener model)
    {
    	// Set reference
        this.model = model;
          
        model.logf(
            "network.initializingCanListener",
            this.getIP()
        );

        if (this.tcp != null)
        {
            model.logf("network.tcpConnected");
        }
        else if (this.tcpRefusal != null)
        {
            model.logf("network.tcpUnavailable", this.tcpRefusal);
        }
        
        // Start reader.
        listen();
    }

    /**
     * Makes sure something is listening on the socket, starting a listener if nothing is.
     *
     * The other half of the reopen: a listener stops only when the socket closes, and `sendMessage` reopens the socket,
     * which brings transmission back but, without this, leaves an open socket with nothing on it: able to transmit but
     * deaf.  One method owns the question so the two halves cannot drift apart.
     */
    private synchronized void listen()
    {
        if (this.reader != null && this.reader.isAlive()) return;

        // Not before there is a model to read into: the model is set AFTER this class is constructed, and the control
        // station's constructor transmits a ping and a power command before it gets there, while `ReadMessages.run()`
        // opens with `model.initMessageBuffer()`.  Reaching here in that window needs a closed socket, so this is a
        // precondition rather than a fault anybody has seen.
        if (this.model == null) return;

        this.reader = new ReadMessages();

        // A DAEMON, because it exists only to serve a running application: its only exit condition is the socket
        // closing, so as an ordinary thread it kept the JVM alive after an embedding caller (an example, a test) was
        // done.
        this.reader.setName("cs2-can-listener");
        this.reader.setDaemon(true);
        this.reader.start();
    }
    
    /**
     * Public interface to send a message out to the CS2 
     
     * @param m a CANMessage object 
     * @return true on success (over TCP, also when the frame is held for a replacement connection), else false
     */
    public boolean sendMessage(CANMessage m)
    {
    	return this.sendMessage(m.getRawMessage());
    }
   
    // Private methods
        
    /**
     * Sends a message over the network
     * 
     * @param message, a raw byte array
     * @return true on success, else false
     */
    synchronized private boolean sendMessage(byte[] message)
    {
        if (this.replacing)
        {
            keep(message, System.nanoTime());
            return true;
        }

        Socket connection = this.tcp;

        if (connection != null)
        {
            try
            {
                writeTcp(connection, message);
            }
            catch (IOException e)
            {
                // The listener, woken by the close, replaces the connection and sends this frame on what replaces it
                this.replacing = true;
                this.lostBecause = reasonOf(e);
                closeQuietly(connection);
            }

            return true;
        }

        return sendUdp(message);
    }

    /** Writes one frame to the connection and keeps it until the connection carries something after it. */
    private void writeTcp(Socket connection, byte[] message) throws IOException
    {
        long now = System.nanoTime();

        // Frames sent before the last bytes heard are taken as received
        while (!this.unconfirmed.isEmpty() && this.unconfirmed.peekFirst().at - this.lastHeardAt < 0)
        {
            this.unconfirmed.pollFirst();
        }

        if (this.unconfirmed.isEmpty()) this.firstUnansweredAt = now;

        keep(message, now);

        OutputStream out = connection.getOutputStream();
        out.write(message);
        out.flush();
    }

    /** Adds a frame to `unconfirmed`, dropping the oldest at the limit. */
    private void keep(byte[] message, long at)
    {
        if (this.unconfirmed.size() >= UNCONFIRMED_LIMIT)
        {
            this.unconfirmed.pollFirst();
            this.unconfirmedDropped++;
        }

        this.unconfirmed.addLast(new Unconfirmed(java.util.Arrays.copyOf(message, message.length), at));
    }

    /** Sends a datagram, as before TCP. */
    private boolean sendUdp(byte[] message)
    {    	
	// Generate a packet containing the message
    	DatagramPacket packet = new DatagramPacket(message, message.length, 
            transmitIP, transmitPort);
        
    	// Transmit
        try
        {
            // Checked before sending: send() throws on a closed socket, so a reopen after it would never run.
            if (this.socket.isClosed())
            {
                this.socket = openReceiveSocket();

                // And something to listen on it: reopening the socket restored transmission only; the listener had
                // exited.
                listen();
            }

            socket.send(packet);
        }
        catch (IOException e)
        {
            // The model is set AFTER this class is constructed, and the control station's constructor transmits before
            // it gets there, so a send can fail with no model to log to.
            if (this.model != null)
            {
                this.model.logf(
                    "network.errorFailedToSendPacket"
                );            
                this.model.log(e.getMessage());
            }
            
            return false;
        }
        
        return true;
    }
    
    /** Hands one frame to the model; a frame the model cannot take must not stop reception. */
    private void deliver(byte[] frame)
    {
        try
        {
            model.receiveMessage(model.createMessage(frame));
        }
        catch (Exception e)
        {
            model.log(e);
        }
    }

    /** Reads the TCP connection, and each that replaces it, until a stop or UDP takes over. */
    private void readTcp(byte[] frame)
    {
        Socket connection;

        while (!this.stopRequested && (connection = this.tcp) != null)
        {
            String reason = readUntilLost(connection, frame);

            if (this.stopRequested) return;

            recover(connection, reason);
        }
    }

    /**
     * Delivers the frames of one connection, reassembled at 13 bytes however the reads split them, and returns why the
     * connection ended.
     */
    private String readUntilLost(Socket connection, byte[] frame)
    {
        int filled = 0;

        try
        {
            InputStream in = new BufferedInputStream(connection.getInputStream());

            while (true)
            {
                try
                {
                    int read = in.read(frame, filled, frame.length - filled);

                    if (read < 0) return I18n.t("network.tcpClosedByStation");

                    this.lastHeardAt = System.nanoTime();
                    this.carried = true;
                    filled += read;

                    if (filled == frame.length)
                    {
                        filled = 0;
                        deliver(frame);
                    }
                }
                catch (SocketTimeoutException quiet)
                {
                    if (this.stopRequested) return "";

                    long first = this.firstUnansweredAt;

                    if (first != 0 && first - this.lastHeardAt > 0 && System.nanoTime() - first > TCP_ANSWER_LIMIT_MS * 1000000L)
                    {
                        return I18n.f("network.tcpNoAnswer", TCP_ANSWER_LIMIT_MS / 1000);
                    }
                }
            }
        }
        catch (IOException e)
        {
            // Not under the lock, which a send stuck in a write may hold
            String because = this.lostBecause;
            return because != null ? because : reasonOf(e);
        }
    }

    /**
     * Replaces a lost connection: up to TCP_RECONNECT_TRIES new ones, then UDP for the rest of the session.  A
     * connection that never carried a frame goes straight to UDP.  Frames the station may not have are sent again.
     */
    private void recover(Socket lost, String reason)
    {
        closeQuietly(lost);

        boolean retry = this.carried;

        synchronized (this)
        {
            this.replacing = true;
            this.lostBecause = null;
        }

        model.logf(retry ? "network.tcpLostReconnecting" : "network.tcpLostUsingUdp", reason);

        IOException failed = null;
        int[] counts;

        for (int attempt = 1; retry && attempt <= TCP_RECONNECT_TRIES; attempt++)
        {
            if (!pause(TCP_RECONNECT_BACKOFF_MS * attempt)) return;

            try
            {
                Socket fresh = connectTcp();

                synchronized (this)
                {
                    if (this.stopRequested)
                    {
                        closeQuietly(fresh);
                        return;
                    }

                    counts = resendOn(fresh);
                }

                model.logf("network.tcpReconnected");
                logResent(counts);
                return;
            }
            catch (IOException e)
            {
                failed = e;
            }
        }

        synchronized (this)
        {
            if (this.stopRequested) return;

            this.tcp = null;
            drainUdp();

            java.util.List<Unconfirmed> again = takeUnconfirmed();
            counts = new int[] {again.size(), this.unconfirmedDropped};

            for (Unconfirmed u : again) sendUdp(u.frame);

            this.unconfirmedDropped = 0;
            this.replacing = false;
        }

        if (failed != null) model.logf("network.tcpReconnectFailed", reasonOf(failed));
        logResent(counts);
    }

    /** Takes `unconfirmed` less the frames sent before the lost connection last carried anything.  Holds this. */
    private java.util.List<Unconfirmed> takeUnconfirmed()
    {
        java.util.List<Unconfirmed> again = new java.util.ArrayList<>();

        for (Unconfirmed u : this.unconfirmed)
        {
            if (u.at - this.lastHeardAt >= 0) again.add(u);
        }

        this.unconfirmed.clear();
        return again;
    }

    /**
     * Sends the unconfirmed frames again, oldest first, on a new connection, and makes it the transport; on a failure
     * they are kept for the next try.  Holds this.  Returns how many were sent and how many had been dropped.
     */
    private int[] resendOn(Socket fresh) throws IOException
    {
        java.util.List<Unconfirmed> again = takeUnconfirmed();

        try
        {
            OutputStream out = fresh.getOutputStream();

            for (Unconfirmed u : again) out.write(u.frame);

            out.flush();
        }
        catch (IOException e)
        {
            this.unconfirmed.addAll(again);
            closeQuietly(fresh);
            throw e;
        }

        useTcp(fresh);

        // Unconfirmed on the new connection too, until it carries something
        long now = System.nanoTime();

        for (Unconfirmed u : again)
        {
            u.at = now;
            this.unconfirmed.addLast(u);
        }

        if (!again.isEmpty()) this.firstUnansweredAt = now;

        int[] counts = {again.size(), this.unconfirmedDropped};

        this.unconfirmedDropped = 0;
        this.replacing = false;
        return counts;
    }

    /** Logs, outside the lock, how many frames a replacement sent again and how many it had to drop. */
    private void logResent(int[] counts)
    {
        if (counts[0] > 0) model.logf("network.framesSentAgain", counts[0]);
        if (counts[1] > 0) model.logf("network.framesDropped", counts[1]);
    }

    /**
     * Throws away the datagrams that waited, unread, in the UDP socket while TCP ran: they are old, and the same frames
     * came over TCP.  Holds this.
     */
    private void drainUdp()
    {
        DatagramSocket waiting = this.socket;
        DatagramPacket packet = new DatagramPacket(new byte[CS2Message.MESSAGE_LENGTH], CS2Message.MESSAGE_LENGTH);
        long until = System.nanoTime() + 1000L * 1000000L;

        try
        {
            waiting.setSoTimeout(1);

            while (System.nanoTime() < until) waiting.receive(packet);
        }
        catch (IOException emptyOrClosed)
        {
            // A timeout says the socket is empty
        }
        finally
        {
            try
            {
                if (!waiting.isClosed()) waiting.setSoTimeout(0);
            }
            catch (SocketException e)
            {
                // Closed meanwhile; the listener's loop ends on that
            }
        }
    }

    /** Waits before a reconnect; false when a stop came meanwhile. */
    private synchronized boolean pause(long ms)
    {
        long until = System.currentTimeMillis() + ms;

        try
        {
            for (long left = ms; !this.stopRequested && left > 0; left = until - System.currentTimeMillis())
            {
                wait(left);
            }
        }
        catch (InterruptedException e)
        {
            // Not a stop: the tries go on, unpaused, and end in UDP
            Thread.currentThread().interrupt();
        }

        return !this.stopRequested;
    }

    /**
     * Threaded class that processes responses from the server
     * and sends them to the view listener for display
     */
    private class ReadMessages extends Thread
    { 	
    	/**
    	 * Constructor
    	 */
    	public ReadMessages()
    	{
            // Starting reader
    	}
    	
        /**
         * Sends messages to the model
         */
        @Override
        public void run()
        {
            // Create a read buffer based on the protocol message length
            byte[] buffer = model.initMessageBuffer();

            // Over TCP first, when connected; UDP is not read meanwhile, or every frame would arrive twice
            if (tcp != null)
            {
                model.logf("network.canListenerRunningTcp");

                try
                {
                    readTcp(buffer);
                }
                finally
                {
                    if (stopRequested) model.logf("network.canListenerClosed");
                }

                if (stopRequested) return;
            }

            // Create a packet to receive the data
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            model.logf(
                "network.canListenerRunning",
                receiveBufferKb()
            );

            try
            {
                // Receive packets as they come in.  A failure must not end this loop: one error would leave
                // TrainControl able to transmit but deaf (no feedback, echoes or power state, and path validation
                // failing).  Only a closed socket stops it.
                while (!socket.isClosed())
                {
                    try
                    {
                        // Wait to receive a datagram
                        socket.receive(packet);

                        // Only a datagram of the right LENGTH is parsed: one buffer is reused for every receive, so a
                        // short datagram would be parsed as its own header followed by whatever the last message left
                        // in the tail, a stale command re-applied that the duplicate-suppression window cannot catch.
                        if (packet.getLength() == buffer.length)
                        {
                            // Send message to listener
                            model.receiveMessage(model.createMessage(buffer));
                        }

                        // Reset the length of the packet just in case
                        packet.setLength(buffer.length);
                    }
                    catch (IOException e)
                    {
                        // A closed socket is handled by the loop test.  Anything else is treated as
                        // recoverable: log it, pause briefly so a persistent fault cannot spin, and
                        // keep listening.  sendMessage may also have replaced the socket by now, in
                        // which case the next pass picks up the new one.
                        if (socket.isClosed())
                        {
                            break;
                        }

                        model.log(e);

                        try
                        {
                            Thread.sleep(RECEIVE_ERROR_BACKOFF_MS);
                        }
                        catch (InterruptedException interrupted)
                        {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    catch (Exception e)
                    {
                        // A single malformed packet must not stop reception either
                        model.log(e);
                    }
                }
            }
            finally
            {
                model.logf(
                    "network.canListenerClosed"
                );

                // Deliberately does NOT close the socket.  The loop only exits once the socket is
                // already closed, so there is nothing to close - and because the loop re-reads the
                // field in order to pick up a socket sendMessage may have reopened, closing it here
                // could shut down a healthy replacement instead.
            }
        }
    }
}
