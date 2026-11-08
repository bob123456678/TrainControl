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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.model.ModelListener;
import org.traincontrol.util.I18n;

/**
 *  This class facilitates communication with the Marklin CS2/CS3 over UDP, and with a CS3 over one TCP connection to
 *  its port 15731 once connectOverTcp is asked and the station takes it (FR-186).  Both carry the same 13-byte frames,
 *  sent the same and handed to the same model.receiveMessage.
 */
public class NetworkProxy
{
    // Ports as defined in the marklin protocol
    public static final int RX_PORT = 15730;
    public static final int TX_PORT = 15731;

    /** The station's TCP port: the same frames as a stream, resent when lost and in order. */
    public static final int TCP_PORT = 15731;
    
    // UDP socket used to send and receive packets.  volatile because sendMessage can replace it after
    // a failure while the reader thread is looping on it.
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

    /** A send unanswered this long makes the connection suspect: a system frame (Stop) then goes over UDP as well. */
    private static final long TCP_SUSPECT_MS = 1000;

    /** How often a TCP read wakes to check the answer limit. */
    private static final int TCP_READ_WAKE_MS = 1000;

    /**
     * A frame counts as received once the connection carried something this long after it was sent: an answer to an
     * earlier frame can arrive after a later one went out, so a newer frame needs this margin.
     */
    private static final long CONFIRM_MARGIN_NS = 250L * 1000000L;

    /** The most frames kept to send again on a replacement connection; the oldest go first. */
    private static final int UNCONFIRMED_LIMIT = 1000;

    /**
     * How far behind the last TCP frame a held datagram is still compared with TCP's own frames when the connection is
     * lost.  A UDP copy reaches this PC within milliseconds of the TCP one, or a few hundred behind on Wi-Fi.
     */
    private static final long OVERLAP_NS = 2000L * 1000000L;

    /** The most datagrams held while TCP runs; the oldest go first. */
    private static final int HELD_LIMIT = 20000;

    /** How long a switch to holding waits for its marker to come back through the UDP socket. */
    private static final long MARKER_WAIT_MS = 1000;

    // Transmission IP/port
    private final InetAddress transmitIP;
    private final int transmitPort;
    
    // The station's TCP port, once connectOverTcp is asked; 0 before
    private volatile int tcpPort;

    // Set by connectOverTcp before there is a model: setModel connects
    private boolean tcpWanted;

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

    private volatile boolean stopRequested;

    /**
     * While TCP runs, the UDP socket is still read but its datagrams are held, not delivered, or every frame would
     * arrive twice.  When the connection is lost, those TCP never carried go to the model, in order, and UDP is
     * delivered live again.  Guards held, tcpTail, holding and marker.
     */
    private final Object udpLock = new Object();

    private final ArrayDeque<Received> held = new ArrayDeque<>();

    // The frames TCP delivered within OVERLAP_NS of its last one, to tell the held copies of them
    private final ArrayDeque<Received> tcpTail = new ArrayDeque<>();

    private boolean holding;

    // The datagram this proxy sends itself to switch to holding: UDP frames queued before it are delivered
    private byte[] marker;

    private CountDownLatch markerPassed;

    // The TCP listener thread, while there is one
    private volatile Thread tcpReader;

    // Model listener class reference
    private ModelListener model;

    /** One frame and when it was sent; `written` once it went onto a connection, so the station may have it. */
    private static final class Unconfirmed
    {
        final byte[] frame;
        long at;
        boolean written;

        Unconfirmed(byte[] frame, long at, boolean written)
        {
            this.frame = frame;
            this.at = at;
            this.written = written;
        }
    }

    /** One frame and when it was received. */
    private static final class Received
    {
        final byte[] frame;
        final long at;

        Received(byte[] frame, long at)
        {
            this.frame = frame;
            this.at = at;
        }
    }

    /**
     * Constructor
     * 
     * @param transmitIP - the IP to send to
     * 
     * @throws IOException on error with the socket
     */
    public NetworkProxy(InetAddress transmitIP) throws IOException
    {
        this.socket = openReceiveSocket();
        this.transmitIP = transmitIP;
        this.transmitPort = NetworkProxy.TX_PORT;
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

    /**
     * Moves the frames onto a TCP connection to the station's port when it takes one; otherwise UDP carries on as
     * before, and the log says why.  Asked for a CS3 only, so a CS2 runs exactly as before (FR-186).  Before setModel,
     * setModel connects.  A port of 0 does nothing.
     */
    public void connectOverTcp(int port)
    {
        synchronized (this)
        {
            if (port <= 0 || this.tcp != null || this.tcpPort > 0) return;

            this.tcpPort = port;

            if (this.model == null)
            {
                this.tcpWanted = true;
                return;
            }
        }

        startTcp();
    }

    /** Connects, switches UDP to holding, and starts the TCP listener; on a failure logs it and stays on UDP. */
    private void startTcp()
    {
        Socket connected;

        try
        {
            connected = connectTcp();
        }
        catch (IOException e)
        {
            model.logf("network.tcpUnavailable", reasonOf(e));
            return;
        }

        enterHolding();
        useTcp(connected);
        model.logf("network.tcpConnected");

        Thread reading = new Thread(this::readTcp, "cs2-can-listener-tcp");
        reading.setDaemon(true);
        this.tcpReader = reading;
        reading.start();
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
     * closed first, not under the lock, which a send stuck in a write may hold, and again under it, in case a reconnect
     * had just put up a new one; a later send uses UDP.
     */
    public void stopListening()
    {
        this.stopRequested = true;
        closeQuietly(this.tcp);

        synchronized (this)
        {
            closeQuietly(this.tcp);
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
        
        // Start reader.
        listen();

        boolean wanted;

        synchronized (this)
        {
            wanted = this.tcpWanted;
            this.tcpWanted = false;
        }

        if (wanted) startTcp();
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
        Socket connection = this.tcp;

        if (connection == null && !this.replacing) return sendUdp(message);

        // A Stop must not wait for a replacement or a connection that may be hung: over UDP at once, and kept too
        if (isSystemFrame(message) && suspect()) sendUdp(message);

        if (this.replacing)
        {
            keep(message, System.nanoTime(), false);
            return true;
        }

        try
        {
            writeTcp(connection, message);
        }
        catch (IOException e)
        {
            // The listener, woken by the close, replaces the connection and sends this frame on what replaces it
            this.unconfirmed.peekLast().written = false;
            this.replacing = true;
            this.lostBecause = reasonOf(e);
            closeQuietly(connection);
        }

        return true;
    }

    /** Whether a frame is a system command (Stop, Go, Halt), as CS2Message reads the command. */
    private static boolean isSystemFrame(byte[] message)
    {
        return message.length > 1 && (((message[0] & 0x01) << 7) | ((message[1] & 0xFF) >> 1)) == CS2Message.CMD_SYSTEM;
    }

    /** Whether the connection is being replaced, or a frame sent on it has gone unanswered for TCP_SUSPECT_MS.  Holds this. */
    private boolean suspect()
    {
        long first = this.firstUnansweredAt;

        return this.replacing || (first != 0 && first - this.lastHeardAt > 0
            && System.nanoTime() - first > TCP_SUSPECT_MS * 1000000L);
    }

    /** Writes one frame to the connection and keeps it until the connection carries something after it. */
    private void writeTcp(Socket connection, byte[] message) throws IOException
    {
        long now = System.nanoTime();

        // Frames sent well before the last bytes heard are taken as received
        while (!this.unconfirmed.isEmpty() && this.unconfirmed.peekFirst().at + CONFIRM_MARGIN_NS - this.lastHeardAt < 0)
        {
            this.unconfirmed.pollFirst();
        }

        // The first send since the connection last carried anything starts the answer clock
        long first = this.firstUnansweredAt;

        if (first == 0 || first - this.lastHeardAt <= 0) this.firstUnansweredAt = now;

        keep(message, now, true);

        OutputStream out = connection.getOutputStream();
        out.write(message);
        out.flush();
    }

    /** Adds a frame to `unconfirmed`, dropping the oldest at the limit. */
    private void keep(byte[] message, long at, boolean written)
    {
        if (this.unconfirmed.size() >= UNCONFIRMED_LIMIT)
        {
            this.unconfirmed.pollFirst();
            this.unconfirmedDropped++;
        }

        this.unconfirmed.addLast(new Unconfirmed(Arrays.copyOf(message, message.length), at, written));
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

    /**
     * Switches UDP from delivering to holding.  Datagrams already queued in the socket predate the switch, so they are
     * still delivered: a marker sent to the socket itself ends them.  Without it back within MARKER_WAIT_MS, holding
     * starts anyway.
     */
    private void enterHolding()
    {
        byte[] mark = new byte[8];
        java.util.concurrent.ThreadLocalRandom.current().nextBytes(mark);
        CountDownLatch passed = new CountDownLatch(1);

        synchronized (this.udpLock)
        {
            this.held.clear();
            this.tcpTail.clear();
            this.marker = mark;
            this.markerPassed = passed;
        }

        try
        {
            DatagramSocket own = this.socket;
            own.send(new DatagramPacket(mark, mark.length, InetAddress.getLoopbackAddress(), own.getLocalPort()));
            passed.await(MARKER_WAIT_MS, TimeUnit.MILLISECONDS);
        }
        catch (IOException e)
        {
            // Holding starts below
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }

        synchronized (this.udpLock)
        {
            this.holding = true;
            this.marker = null;
        }
    }

    /** What the UDP listener does with a datagram of `length` bytes: false when it was this proxy's marker. */
    private boolean isMarker(byte[] buffer, int length)
    {
        synchronized (this.udpLock)
        {
            if (this.marker == null || length != this.marker.length) return false;

            for (int i = 0; i < length; i++) if (buffer[i] != this.marker[i]) return false;

            this.holding = true;
            this.marker = null;
            this.markerPassed.countDown();
            return true;
        }
    }

    /** Delivers a UDP frame, or holds it while TCP runs. */
    private void deliverOrHold(byte[] buffer)
    {
        synchronized (this.udpLock)
        {
            if (this.holding)
            {
                long now = System.nanoTime();
                this.held.addLast(new Received(Arrays.copyOf(buffer, buffer.length), now));

                while (this.held.size() > HELD_LIMIT || (!this.held.isEmpty()
                    && this.held.peekFirst().at - (this.lastTcpAt - OVERLAP_NS) < 0 && this.lastTcpAt != 0))
                {
                    this.held.pollFirst();
                }

                return;
            }
        }

        // Send message to listener
        model.receiveMessage(model.createMessage(buffer));
    }

    // System.nanoTime of the last frame TCP delivered, 0 before one; guarded by udpLock
    private long lastTcpAt;

    /** Delivers a TCP frame and remembers it, to tell the held UDP copy of it later. */
    private void deliverTcp(byte[] frame)
    {
        synchronized (this.udpLock)
        {
            long now = System.nanoTime();
            this.lastTcpAt = now;
            this.tcpTail.addLast(new Received(Arrays.copyOf(frame, frame.length), now));

            while (this.tcpTail.peekFirst().at - (now - OVERLAP_NS) < 0) this.tcpTail.pollFirst();
        }

        deliver(frame);
    }

    /**
     * When the connection is lost: the held datagrams TCP never carried go to the model, in order, and UDP is
     * delivered live from here.  A held datagram older than OVERLAP_NS behind the last TCP frame is dropped, and one
     * that matches a TCP frame of that span, in order, is TCP's own frame again: neither is replayed, so a state
     * TCP already moved past is never applied again.
     */
    private void releaseHeld()
    {
        synchronized (this.udpLock)
        {
            for (byte[] frame : unmatched()) deliver(frame);

            this.held.clear();
            this.tcpTail.clear();
            this.holding = false;
            this.marker = null;
        }
    }

    /** The held frames, from OVERLAP_NS behind the last TCP frame, that do not match TCP's frames in order.  Holds udpLock. */
    private List<byte[]> unmatched()
    {
        long from = this.lastTcpAt == 0 ? Long.MIN_VALUE : this.lastTcpAt - OVERLAP_NS;
        List<Received> tail = new ArrayList<>(this.tcpTail);
        List<byte[]> out = new ArrayList<>();
        int next = 0;

        for (Received h : this.held)
        {
            if (this.lastTcpAt != 0 && h.at - from < 0) continue;

            int match = -1;

            for (int k = next; k < tail.size() && match < 0; k++)
            {
                if (Arrays.equals(tail.get(k).frame, h.frame)) match = k;
            }

            if (match >= 0)
            {
                next = match + 1;
            }
            else
            {
                out.add(h.frame);
            }
        }

        return out;
    }

    /** The TCP listener: reads the connection, and each that replaces it, until a stop or UDP takes over. */
    private void readTcp()
    {
        byte[] frame = model.initMessageBuffer();
        Socket connection;

        model.logf("network.canListenerRunningTcp");

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
                        deliverTcp(frame);
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
     * connection that never carried a frame goes straight to UDP.  UDP is delivered live meanwhile, and frames the
     * station may not have are sent again.
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

        releaseHeld();

        model.logf(retry ? "network.tcpLostReconnecting" : "network.tcpLostUsingUdp", reason);

        IOException failed = null;
        int[] counts;

        for (int attempt = 1; retry && attempt <= TCP_RECONNECT_TRIES; attempt++)
        {
            if (!pause(TCP_RECONNECT_BACKOFF_MS * attempt)) return;

            try
            {
                Socket fresh = connectTcp();

                // UDP held again from here; the new connection is read only once that holds
                enterHolding();

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
                releaseHeld();
            }
        }

        synchronized (this)
        {
            if (this.stopRequested) return;

            this.tcp = null;

            List<Unconfirmed> again = takeUnconfirmed();
            counts = counts(again);

            for (Unconfirmed u : again) sendUdp(u.frame);

            this.replacing = false;
        }

        if (failed != null) model.logf("network.tcpReconnectFailed", reasonOf(failed));
        logResent(counts);
    }

    /** Takes `unconfirmed` less the frames sent well before the lost connection last carried anything.  Holds this. */
    private List<Unconfirmed> takeUnconfirmed()
    {
        List<Unconfirmed> again = new ArrayList<>();

        for (Unconfirmed u : this.unconfirmed)
        {
            if (u.at + CONFIRM_MARGIN_NS - this.lastHeardAt >= 0) again.add(u);
        }

        this.unconfirmed.clear();
        return again;
    }

    /** How many of the frames were written before (sent again), how many only held (sent late), and how many dropped. */
    private int[] counts(List<Unconfirmed> again)
    {
        int written = 0;

        for (Unconfirmed u : again) if (u.written) written++;

        int[] counts = {written, again.size() - written, this.unconfirmedDropped};

        this.unconfirmedDropped = 0;
        return counts;
    }

    /**
     * Sends the unconfirmed frames again, oldest first, on a new connection, and makes it the transport; on a failure
     * they are kept for the next try.  Holds this.  Returns the counts for logResent.
     */
    private int[] resendOn(Socket fresh) throws IOException
    {
        List<Unconfirmed> again = takeUnconfirmed();

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

        int[] counts = counts(again);

        useTcp(fresh);

        // Unconfirmed on the new connection too, until it carries something
        long now = System.nanoTime();

        for (Unconfirmed u : again)
        {
            u.at = now;
            u.written = true;
            this.unconfirmed.addLast(u);
        }

        if (!again.isEmpty()) this.firstUnansweredAt = now;

        this.replacing = false;
        return counts;
    }

    /** Logs, outside the lock, how many frames a replacement sent again, sent late, and had to drop. */
    private void logResent(int[] counts)
    {
        if (counts[0] > 0) model.logf("network.framesSentAgain", counts[0]);
        if (counts[1] > 0) model.logf("network.framesSentLate", counts[1]);
        if (counts[2] > 0) model.logf("network.framesDropped", counts[2]);
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
                            deliverOrHold(buffer);
                        }
                        else
                        {
                            isMarker(buffer, packet.getLength());
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
