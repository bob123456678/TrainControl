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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.model.ModelListener;
import org.traincontrol.util.I18n;

/**
 *  This class facilitates communication with the Marklin CS2/CS3 over UDP, and with a CS3 over one TCP connection to
 *  its port 15731 once connectOverTcp is asked and the station takes it (FR-186).  Both carry the same 13-byte frames,
 *  sent the same and handed to the same model.receiveMessage.  While a lost connection is replaced, commands go over
 *  UDP at once, and nothing is held or sent again, as another copy or the station's handset may have commanded since.
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

    /** A send unanswered this long makes the connection suspect: a Stop then goes over UDP as well. */
    private static final long TCP_SUSPECT_MS = 1000;

    /** How often a TCP read wakes to check the answer limit. */
    private static final int TCP_READ_WAKE_MS = 1000;

    /**
     * A frame counts as received once the connection carried something this long after it was sent: an answer to an
     * earlier frame can arrive after a later one went out, so a newer frame needs this margin.
     */
    private static final long CONFIRM_MARGIN_NS = 250L * 1000000L;

    /** The gap between two sensor queries: the Link S88 answers each within about 20 ms, 60 take 0.3 s. */
    private static final long RESYNC_PACE_NS = 5L * 1000000L;

    /** How long a resync waits for its answers after the last query. */
    private static final long RESYNC_WAIT_NS = 1000L * 1000000L;

    /** When a second UDP pass starts after the first: before the first reconnect try, 1 s on, sets UDP aside. */
    private static final long RESYNC_AGAIN_NS = 500L * 1000000L;

    // Transmission IP/port
    private final InetAddress transmitIP;
    private final int transmitPort;
    
    // The station's TCP port, once connectOverTcp is asked; 0 before
    private volatile int tcpPort;

    // Set by connectOverTcp before there is a model: setModel connects
    private boolean tcpWanted;

    // The TCP connection, null in UDP mode
    private volatile Socket tcp;

    // Guarded by this: when each frame was written since the connection last carried anything, for the loss's log line
    private final ArrayDeque<Long> unconfirmed = new ArrayDeque<>();

    // Written under this: the listener is replacing a lost connection, and sends go over UDP meanwhile
    private volatile boolean replacing;

    // Counts the times `replacing` went true, so two reads tell whether a replacement began between them
    private final AtomicInteger replacements = new AtomicInteger();

    // Why a send found the connection broken, for the listener's log line
    private volatile String lostBecause;

    // System.nanoTime of the last bytes read off the connection, and of the first frame sent after them
    private volatile long lastHeardAt;
    private volatile long firstUnansweredAt;

    // Whether the current connection has carried a frame; one that never has is not worth reconnecting
    private volatile boolean carried;

    private volatile boolean stopRequested;

    /**
     * One transport is live at a time.  While TCP runs, the UDP socket stays bound, so a second copy of the program
     * still finds the port taken, and its datagrams are read and set aside, never delivered.  Guards udpLive and
     * udpSetAside.
     */
    private final Object udpLock = new Object();

    private boolean udpLive = true;

    // How many datagrams were set aside while TCP ran
    private long udpSetAside;

    // Changes whenever the live transport does, so a resync for an earlier one stops
    private final AtomicInteger liveEpoch = new AtomicInteger();

    // Each s88 contact's device, learned from the station's events once learnsDevices is set: for a CS3 only
    private final Map<Integer, Integer> sensorDevice = new ConcurrentHashMap<>();

    // Set by connectOverTcp and by a sync's syncSensors, both for a CS3 only
    private volatile boolean learnsDevices;

    // Each contact's device as the station's device list has it, and as last saved, for a contact no event placed
    private volatile Map<Integer, Integer> listedDevice = new java.util.HashMap<>();
    private volatile Map<Integer, Integer> savedDevice = new java.util.HashMap<>();

    // Each contact asked, and when a real event for it came since (0 for none): an answer, or a late copy, within
    // RESYNC_WAIT_NS of that event is older than it
    private final Map<Integer, Long> overtaken = new ConcurrentHashMap<>();

    // Held for a whole pass of queries, so the sync's and a resync's cannot share `awaiting`
    private final Object passLock = new Object();

    // Woken by an answer, a change of live transport and a stop: what a resync waits on
    private final Object resyncLock = new Object();

    // The contacts the running resync pass asked and has not had answered, or null; guarded by resyncLock
    private Set<Integer> awaiting;

    private int answered;

    // The TCP listener thread, while there is one
    private volatile Thread tcpReader;

    // Model listener class reference
    private ModelListener model;

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
            this.learnsDevices = true;

            if (this.model == null)
            {
                this.tcpWanted = true;
                return;
            }
        }

        startTcp();
    }

    /** Connects and starts the TCP listener, UDP set aside from before the connect; on a failure logs it, UDP live. */
    private void startTcp()
    {
        Socket connected;

        setUdpLive(false);

        try
        {
            connected = connectTcp();
        }
        catch (IOException e)
        {
            setUdpLive(true);
            model.logf("network.tcpUnavailable", reasonOf(e));
            return;
        }

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
    private static String reasonOf(Exception e)
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
            wakeResync();

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
     * @return true on success, else false
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

        // While a lost connection is replaced, over UDP at once and never again on the new one (FR-186)
        if (connection == null || this.replacing) return sendUdp(message);

        // A Stop must not wait on a connection that may be hung: over UDP at once as well
        boolean overUdp = isUrgentStop(message) && suspect() && sendUdp(message);

        try
        {
            writeTcp(connection, message);
        }
        catch (IOException e)
        {
            // The listener, woken by the close, replaces the connection; the frame did not go out on it
            startReplacing(reasonOf(e));
            closeQuietly(connection);

            if (!overUdp) return sendUdp(message);
        }

        return true;
    }

    /** Whether a lost TCP connection is being replaced; never on UDP alone.  Not under the lock a stuck write holds. */
    public boolean isReplacing()
    {
        return this.replacing;
    }

    /** How many replacements have begun; not under the lock either. */
    public int replacements()
    {
        return this.replacements.get();
    }

    /** Under this: the connection is lost and being replaced, the replacement counted as it begins. */
    private void startReplacing(String because)
    {
        if (!this.replacing) this.replacements.incrementAndGet();

        this.replacing = true;
        this.lostBecause = because;
    }

    /** A frame's command, as CS2Message reads it. */
    private static int command(byte[] frame)
    {
        return ((frame[0] & 0x01) << 7) | ((frame[1] & 0xFF) >> 1);
    }

    /**
     * Stop, Halt or a locomotive's emergency stop: what may overtake.  Go may not, or the power comes on before a speed
     * 0 sent ahead of it.
     */
    private static boolean isUrgentStop(byte[] frame)
    {
        if (frame.length < 10 || command(frame) != CS2Message.CMD_SYSTEM || frame[4] < 5) return false;

        int sub = frame[9] & 0xFF;

        return sub == CS2Message.CMD_SYSSUB_STOP || sub == CS2Message.CMD_SYSSUB_HALT
            || sub == CS2Message.CMD_SYSSUB_TRAINSTOP;
    }

    /** Whether a frame sent on the connection has gone unanswered for TCP_SUSPECT_MS.  Holds this. */
    private boolean suspect()
    {
        long first = this.firstUnansweredAt;

        return first != 0 && first - this.lastHeardAt > 0 && System.nanoTime() - first > TCP_SUSPECT_MS * 1000000L;
    }

    /** Writes one frame to the connection and notes when, until the connection carries something after it. */
    private void writeTcp(Socket connection, byte[] message) throws IOException
    {
        long now = System.nanoTime();

        forgetReceived();

        // The first send since the connection last carried anything starts the answer clock
        long first = this.firstUnansweredAt;

        if (first == 0 || first - this.lastHeardAt <= 0) this.firstUnansweredAt = now;

        OutputStream out = connection.getOutputStream();
        out.write(message);
        out.flush();
        this.unconfirmed.addLast(now);
    }

    /** Forgets the frames sent well before the connection last carried anything: taken as received.  Holds this. */
    private void forgetReceived()
    {
        while (!this.unconfirmed.isEmpty() && this.unconfirmed.peekFirst() + CONFIRM_MARGIN_NS - this.lastHeardAt < 0)
        {
            this.unconfirmed.pollFirst();
        }
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

                // And something to listen on it.
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

    /** Makes UDP the live transport, or sets its datagrams aside; once this returns, none is delivered unless live. */
    private void setUdpLive(boolean live)
    {
        synchronized (this.udpLock)
        {
            this.udpLive = live;
            this.liveEpoch.incrementAndGet();
        }

        wakeResync();
    }

    private void wakeResync()
    {
        synchronized (this.resyncLock)
        {
            this.resyncLock.notifyAll();
        }
    }

    /** Delivers a UDP frame while UDP is live; while TCP runs, sets it aside. */
    private void deliverUdp(byte[] buffer)
    {
        synchronized (this.udpLock)
        {
            if (!this.udpLive)
            {
                this.udpSetAside++;
                return;
            }

            Heard heard = learn(buffer);

            // Send message to listener
            if (heard != Heard.OVERTAKEN) model.receiveMessage(marked(model.createMessage(buffer), heard));
        }
    }

    /** Delivers a TCP frame. */
    private void deliverTcp(byte[] frame)
    {
        Heard heard = learn(frame);

        if (heard == Heard.OVERTAKEN) return;

        try
        {
            model.receiveMessage(marked(model.createMessage(frame), heard));
        }
        catch (Exception e)
        {
            model.log(e);
        }
    }

    /** A message, marked as the answer to a resync's query when it is one, so the model can tell it from an event. */
    private static CANMessage marked(CANMessage message, Heard heard)
    {
        if (heard == Heard.ANSWER && message instanceof CS2Message) ((CS2Message) message).markAnswersAResync();

        return message;
    }

    /** What a frame is: any other, the answer to a pass's query, or an answer older than an event since the query. */
    private enum Heard { FRAME, ANSWER, OVERTAKEN }

    /**
     * Once learnsDevices is set, notes a contact's device from a sensor event, a change of state; a frame with the
     * state unchanged is an answer to a query, counted for the pass that asked, and OVERTAKEN, never delivered, when a
     * real event for the contact came after its query and less than RESYNC_WAIT_NS ago (OB-418).
     */
    private Heard learn(byte[] frame)
    {
        if (!this.learnsDevices || command(frame) != CS2Message.CMD_ACC_SENSOR || frame[4] != 8) return Heard.FRAME;

        int contact = ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);

        if (frame[9] != frame[10])
        {
            this.sensorDevice.put(contact, ((frame[5] & 0xFF) << 8) | (frame[6] & 0xFF));
            this.overtaken.replace(contact, System.nanoTime());
            return Heard.FRAME;
        }

        boolean ours;

        synchronized (this.resyncLock)
        {
            ours = this.awaiting != null && this.awaiting.remove(contact);

            if (ours)
            {
                this.answered++;
                this.resyncLock.notifyAll();
            }
        }

        Long event = this.overtaken.get(contact);
        boolean older = event != null && event != 0 && System.nanoTime() - event < RESYNC_WAIT_NS;

        return older ? Heard.OVERTAKEN : ours ? Heard.ANSWER : Heard.FRAME;
    }

    /**
     * Asks each sensor whose device is known (deviceOf) for its state, on the live transport, and logs the counts;
     * returns once every query is answered, or RESYNC_WAIT_NS after the last.  Over UDP twice, as a resync does.  For a
     * CS3's sync (OB-418): from the first call on, events teach their devices also to a proxy never asked for TCP.
     * Before setModel, only that.
     */
    public void syncSensors()
    {
        this.learnsDevices = true;

        if (this.model == null || this.stopRequested) return;

        boolean overTcp;
        int epoch;

        synchronized (this.udpLock)
        {
            overTcp = !this.udpLive;
            epoch = this.liveEpoch.get();
        }

        ask(overTcp, epoch, overTcp ? 1 : 2, "network.sensorsSynced");
    }

    /**
     * The device a contact is asked at: the one a real event showed, else the station's device list's, else the one
     * saved; null when none.
     */
    public Integer deviceOf(int contact)
    {
        Integer device = this.sensorDevice.get(contact);

        if (device == null) device = this.listedDevice.get(contact);

        return device != null ? device : this.savedDevice.get(contact);
    }

    /** The device a real event showed for a contact, or null. */
    public Integer learnedDeviceOf(int contact)
    {
        return this.sensorDevice.get(contact);
    }

    /** The devices the station's device list places, and those saved before a restart (OB-418). */
    public void knowDevices(Map<Integer, Integer> listed, Map<Integer, Integer> saved)
    {
        this.listedDevice = new java.util.HashMap<>(listed);
        this.savedDevice = new java.util.HashMap<>(saved);
    }

    /**
     * Asks each sensor the model knows for its state, on the transport just made live, as frames missed meanwhile are
     * gone.  The station answers with the state as it answers, on the same transport and after any event it sent
     * before, so no answer overtakes a newer event.  Only a contact whose device is known (deviceOf) is asked.
     * `passes` above 1 asks again RESYNC_AGAIN_NS after each start, for a UDP copy that lands after the answer.  On a
     * thread of its own that stops when the live transport changes.
     */
    private void resync(boolean overTcp, int passes)
    {
        int epoch = this.liveEpoch.get();

        Thread asking = new Thread(() -> ask(overTcp, epoch, passes, "network.sensorsResynced"), "cs2-s88-resync");

        asking.setDaemon(true);
        asking.start();
    }

    /** Asks `passes` times, each pass RESYNC_AGAIN_NS after the last one's start, until superseded. */
    private void ask(boolean overTcp, int epoch, int passes, String key)
    {
        for (int n = 0; n < passes; n++)
        {
            long start = System.nanoTime();
            long until = n + 1 < passes ? start + RESYNC_AGAIN_NS : 0;

            if (!askOnce(overTcp, epoch, until, key)) return;

            if (n + 1 < passes && !waitFor(start + RESYNC_AGAIN_NS, epoch, false)) return;
        }
    }

    /**
     * One pass: the queries, paced, then the answers awaited until `until`, or for RESYNC_WAIT_NS when 0; logged, also
     * when a new transport cut it short, but not after a stop, under `key`.  False once superseded.
     */
    private boolean askOnce(boolean overTcp, int epoch, long until, String key)
    {
        synchronized (this.passLock)
        {
            List<byte[]> queries = new ArrayList<>();
            Set<Integer> asked = ConcurrentHashMap.newKeySet();
            int skipped = 0;

            for (Integer contact : model.knownSensors())
            {
                Integer device = deviceOf(contact);

                if (device == null)
                {
                    skipped++;
                }
                else
                {
                    asked.add(contact);
                    this.overtaken.put(contact, 0L);
                    queries.add(new CS2Message(CS2Message.CMD_ACC_SENSOR, new byte[]
                        {(byte) (device >> 8), (byte) (int) device, (byte) (contact >> 8), (byte) (int) contact}).getRawMessage());
                }
            }

            synchronized (this.resyncLock)
            {
                this.awaiting = asked;
                this.answered = 0;
            }

            int sent = 0;
            boolean current = true;

            for (byte[] query : queries)
            {
                if (this.stopRequested || this.liveEpoch.get() != epoch)
                {
                    current = false;
                    break;
                }

                boolean written;

                if (overTcp)
                {
                    written = sendQuery(query);
                }
                else
                {
                    synchronized (this)
                    {
                        written = sendUdp(query);
                    }
                }

                if (written) sent++;

                LockSupport.parkNanos(RESYNC_PACE_NS);
            }

            if (current) current = waitFor(until != 0 ? until : System.nanoTime() + RESYNC_WAIT_NS, epoch, true);

            int got;

            synchronized (this.resyncLock)
            {
                got = this.answered;
                this.awaiting = null;
            }

            if (!this.stopRequested) model.logf(key, sent, got, skipped);

            return current;
        }
    }

    /** Waits until `deadline`, or until every query is answered when `answers`; false on a stop or a new transport. */
    private boolean waitFor(long deadline, int epoch, boolean answers)
    {
        synchronized (this.resyncLock)
        {
            try
            {
                for (long left = deadline - System.nanoTime(); left > 0 && !this.stopRequested
                    && this.liveEpoch.get() == epoch && !(answers && this.awaiting.isEmpty());
                    left = deadline - System.nanoTime())
                {
                    this.resyncLock.wait(Math.max(1, left / 1000000L));
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return !this.stopRequested && this.liveEpoch.get() == epoch;
    }

    /**
     * Writes a sensor query to the connection; false when it was not written.  Not counted as a command that may not
     * have arrived, and no start of the answer clock: a resync runs again on the next live transport, and a query
     * left unanswered says nothing of the connection.
     */
    private synchronized boolean sendQuery(byte[] query)
    {
        Socket connection = this.tcp;

        if (connection == null || this.replacing) return false;

        try
        {
            OutputStream out = connection.getOutputStream();
            out.write(query);
            out.flush();
            return true;
        }
        catch (IOException e)
        {
            // As in sendMessage: the listener, woken by the close, replaces the connection
            startReplacing(reasonOf(e));
            closeQuietly(connection);
            return false;
        }
    }

    /** The TCP listener: reads the connection, and each that replaces it, until a stop or UDP takes over. */
    private void readTcp()
    {
        byte[] frame = model.initMessageBuffer();
        Socket connection;

        model.logf("network.canListenerRunningTcp");

        try
        {
            while (!this.stopRequested && (connection = this.tcp) != null)
            {
                String reason = readUntilLost(connection, frame);

                if (this.stopRequested) return;

                recover(connection, reason);
            }
        }
        catch (RuntimeException e)
        {
            // Nothing is left replacing the connection: UDP for the session, or autonomy would hold new routes for ever
            closeQuietly(this.tcp);

            synchronized (this)
            {
                this.tcp = null;
                this.replacing = false;
            }

            // As recover does on its own move to UDP: what changed while UDP was set aside is asked again
            setUdpLive(true);
            resync(false, 2);
            model.logf("network.tcpLostUsingUdp", reasonOf(e));

            throw e;
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
     * connection that never carried a frame goes straight to UDP.  UDP is live meanwhile and carries every command at
     * once; nothing is sent again, as another copy or the handset may have commanded since, so the frames that may not
     * have arrived are only counted in the log (FR-186).  The sensors are resynced on each transport made live.
     */
    private void recover(Socket lost, String reason)
    {
        // Reset, not closed in order, so the station drops what it has not read and runs no old command late.  The
        // CS3 runs Linux, where a reset discards its unread buffer too as far as we know (unconfirmed on the station).
        try
        {
            lost.setSoLinger(true, 0);
        }
        catch (SocketException ignored)
        {
            // Already closed
        }

        closeQuietly(lost);

        boolean retry = this.carried;
        int cutOff;

        synchronized (this)
        {
            startReplacing(null);
            forgetReceived();
            cutOff = this.unconfirmed.size();
            this.unconfirmed.clear();
        }

        setUdpLive(true);

        model.logf(retry ? "network.tcpLostReconnecting" : "network.tcpLostUsingUdp", reason);

        if (cutOff > 0) model.logf("network.framesMayNotHaveArrived", cutOff);

        resync(false, 2);

        IOException failed = null;

        for (int attempt = 1; retry && attempt <= TCP_RECONNECT_TRIES; attempt++)
        {
            if (!pause(TCP_RECONNECT_BACKOFF_MS * attempt)) return;

            try
            {
                setUdpLive(false);

                Socket fresh = connectTcp();

                synchronized (this)
                {
                    if (this.stopRequested)
                    {
                        closeQuietly(fresh);
                        return;
                    }

                    useTcp(fresh);
                    this.replacing = false;
                }

                model.logf("network.tcpReconnected");
                resync(true, 1);
                return;
            }
            catch (IOException e)
            {
                failed = e;
                setUdpLive(true);
                resync(false, 1);
            }
        }

        synchronized (this)
        {
            if (this.stopRequested) return;

            this.tcp = null;
            this.replacing = false;
        }

        if (failed != null) model.logf("network.tcpReconnectFailed", reasonOf(failed));
    }

    /** Waits before a reconnect; false when a stop came meanwhile. */
    private synchronized boolean pause(long ms)
    {
        long until = System.currentTimeMillis() + ms;

        try
        {
            for (long left = ms; !this.stopRequested && left > 0; left = until - System.currentTimeMillis()) wait(left);
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
                        if (packet.getLength() == buffer.length) deliverUdp(buffer);

                        // Reset the length of the packet just in case
                        packet.setLength(buffer.length);
                    }
                    catch (IOException e)
                    {
                        // A closed socket is handled by the loop test.  Anything else is treated as
                        // recoverable: log it, pause briefly so a persistent fault cannot spin, and
                        // keep listening.  sendMessage may also have replaced the socket by now, in
                        // which case the next pass picks up the new one.
                        if (socket.isClosed()) break;

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
