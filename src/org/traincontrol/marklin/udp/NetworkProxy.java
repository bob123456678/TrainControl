package org.traincontrol.marklin.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.model.ModelListener;

/**
 *  This class facilitates communication with the Marklin CS2/CS3 over UDP
 */
public class NetworkProxy
{
    // Ports as defined in the marklin protocol
    public static final int RX_PORT = 15730;
    public static final int TX_PORT = 15731;
    
    // UDP socket used to send and receive packets.  volatile because sendMessage can replace it after
    // a failure while the reader thread is looping on it.
    private volatile DatagramSocket socket;

    // How long to wait after a recoverable receive error, so that a persistent fault cannot spin
    private static final long RECEIVE_ERROR_BACKOFF_MS = 50;

    /** The receive buffer asked for: about 80,000 frames, minutes of the station's busiest traffic (OB-421). */
    private static final int RECEIVE_BUFFER_BYTES = 1024 * 1024;
    
    // Transmission IP/port
    private final InetAddress transmitIP;
    private final int transmitPort;
    
    /** The gap between two sensor queries: the Link S88 answers each within about 20 ms, 60 take 0.3 s. */
    private static final long QUERY_PACE_NS = 5L * 1000000L;

    /** How long a pass of sensor queries waits for its answers after the last query. */
    private static final long ANSWER_WAIT_NS = 1000L * 1000000L;

    /** When the second pass starts after the first, for a UDP copy of an older event that lands after the answer. */
    private static final long ASK_AGAIN_NS = 500L * 1000000L;

    // Each s88 contact's device, learned from the station's events once learnsDevices is set: for a CS3 only
    private final Map<Integer, Integer> sensorDevice = new ConcurrentHashMap<>();

    // Set by a CS3's sync (syncSensors)
    private volatile boolean learnsDevices;

    // Each contact's device as the station's device list has it, and as last saved, for a contact no event placed
    private volatile Map<Integer, Integer> listedDevice = new HashMap<>();
    private volatile Map<Integer, Integer> savedDevice = new HashMap<>();

    // Each contact asked, and when a real event for it came since its query (0 for none): an answer, or a late copy,
    // within ANSWER_WAIT_NS of that event is older than it.  Forgotten ANSWER_WAIT_NS after the last pass
    private final Map<Integer, Long> overtaken = new HashMap<>();

    // Guards `overtaken` and the fields below up to askEnded; takes no other lock while held
    private final Object passState = new Object();

    private int asks;

    // The passes among `asks` that hold routes: the connect's, the first with a model (D133)
    private int holds;

    private boolean connectAsked;

    // The start-up's sync asked before there was a model: setModel asks
    private boolean askOnModel;

    private long askEnded;

    // Held for a whole pass of queries, so two passes cannot share `awaiting`
    private final Object passLock = new Object();

    // Woken by an answer: what a pass waits on
    private final Object answerLock = new Object();

    // The contacts the running pass asked and has not had answered, or null; guarded by answerLock
    private Set<Integer> awaiting;

    private int answered;
    
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

    /** The station's port, with room for the frames that arrive while the listener is held up. */
    private static DatagramSocket openReceiveSocket() throws IOException
    {
        DatagramSocket opened = new DatagramSocket(NetworkProxy.RX_PORT);

        // A full buffer drops frames, sensor changes included (OB-421).  A hint: the operating system may grant less,
        // and the listener runs on whatever it got.
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

    /**
     * Sets the model field
     * 
     * @param model the model instance
     */
    public void setModel(ModelListener model)
    {
        Boolean holding = null;

        synchronized (this.passState)
        {
            // Set reference
            this.model = model;

            // The start-up's sync ran before there was a model: its pass is counted before the listener starts, so no
            // frame that queued meanwhile fires a route (D133)
            if (this.askOnModel) holding = beginAsk();

            this.askOnModel = false;
        }
          
        model.logf(
            "network.initializingCanListener",
            this.getIP()
        );
        
        // Start reader
        new ReadMessages().start();

        // And its sensors are asked now (OB-418)
        if (holding != null) ask(holding);
    }

    /**
     * Asks each sensor whose device is known (deviceOf) for its state, twice, ASK_AGAIN_NS apart, and logs the counts;
     * with `wait`, returns once every query of the second pass is answered, or ANSWER_WAIT_NS after its last, else
     * asks on a thread of its own.  For a CS3's sync only (OB-418): from the first call on, events teach their
     * devices.  Before setModel, only that, and setModel asks.  The first pass with a model, the connect's, holds
     * routes until it ends (D133).
     */
    public void syncSensors(boolean wait)
    {
        this.learnsDevices = true;

        boolean holding;

        synchronized (this.passState)
        {
            if (this.model == null)
            {
                this.askOnModel = true;
                return;
            }

            // Counted here, not on the thread, so no frame between this call and the thread's start fires a route
            holding = beginAsk();
        }

        if (wait)
        {
            ask(holding);
            return;
        }

        Thread asking = new Thread(() -> ask(holding), "cs3-sensor-pass");

        asking.setDaemon(true);
        asking.start();
    }

    /** Counts a pass that ask ends, under `passState`; true when it holds routes, as the connect's does (D133). */
    private boolean beginAsk()
    {
        boolean holding = !this.connectAsked;

        this.connectAsked = true;
        this.asks++;

        if (holding) this.holds++;

        return holding;
    }

    /** The two passes of a pass counted by beginAsk, ASK_AGAIN_NS apart. */
    private void ask(boolean holding)
    {
        try
        {
            for (int n = 0; n < 2; n++)
            {
                long start = System.nanoTime();

                if (!askOnce(n == 0 ? start + ASK_AGAIN_NS : 0)) return;

                if (n == 0 && !waitFor(start + ASK_AGAIN_NS, false)) return;
            }
        }
        finally
        {
            synchronized (this.passState)
            {
                this.asks--;
                this.askEnded = System.nanoTime();

                if (holding) this.holds--;
            }
        }
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
        this.listedDevice = new HashMap<>(listed);
        this.savedDevice = new HashMap<>(saved);
    }

    /**
     * Whether a CS3's state confirmation after the connect is under way: a sensor change then updates the state but
     * fires no route (D133).  A later sync's pass holds nothing.  Never for a CS2, which is never asked.
     */
    public boolean isConfirming()
    {
        synchronized (this.passState)
        {
            return this.learnsDevices && this.holds > 0;
        }
    }

    /** Whether any pass of queries to a CS3, a sync's included, is under way: its answers are then no events. */
    public boolean isAsking()
    {
        synchronized (this.passState)
        {
            return this.learnsDevices && this.asks > 0;
        }
    }

    /**
     * One pass: the queries, paced, then the answers awaited until `until`, or for ANSWER_WAIT_NS when 0; logged.
     * False once the socket is closed.
     */
    private boolean askOnce(long until)
    {
        synchronized (this.passLock)
        {
            Map<Integer, byte[]> queries = new LinkedHashMap<>();
            Set<Integer> asked = ConcurrentHashMap.newKeySet();
            int skipped = 0;

            for (Integer contact : this.model.knownSensors())
            {
                Integer device = deviceOf(contact);

                if (device == null)
                {
                    skipped++;
                }
                else
                {
                    asked.add(contact);
                    queries.put(contact, new byte[]
                        {(byte) (device >> 8), (byte) (int) device, (byte) (contact >> 8), (byte) (int) contact});
                }
            }

            synchronized (this.answerLock)
            {
                this.awaiting = asked;
                this.answered = 0;
            }

            int sent = 0;

            for (Map.Entry<Integer, byte[]> query : queries.entrySet())
            {
                if (this.socket.isClosed()) break;

                // Marked before it is written, so only a later event overtakes its answer
                synchronized (this.passState)
                {
                    this.overtaken.put(query.getKey(), 0L);
                }

                if (sendMessage(new CS2Message(CS2Message.CMD_ACC_SENSOR, query.getValue()))) sent++;

                LockSupport.parkNanos(QUERY_PACE_NS);
            }

            boolean current = waitFor(until != 0 ? until : System.nanoTime() + ANSWER_WAIT_NS, true);
            int got;

            synchronized (this.answerLock)
            {
                got = this.answered;
                this.awaiting = null;
            }

            this.model.logf("network.sensorsSynced", sent, got, skipped);

            return current;
        }
    }

    /** Waits until `deadline`, or until every query is answered when `answers`; false once the socket is closed. */
    private boolean waitFor(long deadline, boolean answers)
    {
        synchronized (this.answerLock)
        {
            try
            {
                for (long left = deadline - System.nanoTime(); left > 0 && !this.socket.isClosed()
                    && !(answers && this.awaiting.isEmpty()); left = deadline - System.nanoTime())
                {
                    this.answerLock.wait(Math.max(1, left / 1000000L));
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return !this.socket.isClosed();
    }

    /**
     * Once learnsDevices is set, notes a contact's device from a sensor event, a change of state; a frame with the
     * state unchanged is an answer to a query, counted for the pass that asked.  True, and the frame not to be
     * delivered, when it is such an answer older than a real event for the contact since its query, less than
     * ANSWER_WAIT_NS ago (OB-418).  Stamps `message` with the passes under way before an answer can end its pass, as
     * the frame is parsed on another thread (OB-490).
     */
    private boolean learn(byte[] frame, CANMessage message)
    {
        int command = ((frame[0] & 0x01) << 7) | ((frame[1] & 0xFF) >> 1);

        if (!this.learnsDevices || command != CS2Message.CMD_ACC_SENSOR || frame[4] != 8) return false;

        int contact = ((frame[7] & 0xFF) << 8) | (frame[8] & 0xFF);

        boolean change = frame[9] != frame[10];

        if (change) this.sensorDevice.put(contact, ((frame[5] & 0xFF) << 8) | (frame[6] & 0xFF));

        synchronized (this.passState)
        {
            if (this.asks == 0 && System.nanoTime() - this.askEnded >= ANSWER_WAIT_NS) this.overtaken.clear();

            if (message instanceof CS2Message) ((CS2Message) message).heardDuring(this.asks > 0, this.holds > 0);

            if (change)
            {
                this.overtaken.replace(contact, System.nanoTime());
                return false;
            }
        }

        synchronized (this.answerLock)
        {
            if (this.awaiting != null && this.awaiting.remove(contact))
            {
                this.answered++;
                this.answerLock.notifyAll();
            }
        }

        Long event;

        synchronized (this.passState)
        {
            event = this.overtaken.get(contact);
        }

        return event != null && event != 0 && System.nanoTime() - event < ANSWER_WAIT_NS;
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
	// Generate a packet containing the message
    	DatagramPacket packet = new DatagramPacket(message, message.length, 
            transmitIP, transmitPort);
        
    	// Transmit
        try
        {
            // Checked before sending, not after: send() throws on a closed socket, so the reopen below
            // used to be unreachable and transmission stayed broken for the rest of the session
            if (this.socket.isClosed())
            {
                this.socket = openReceiveSocket();
            }

            socket.send(packet);
        }
        catch (IOException e)
        {
            // The model is set AFTER this class is constructed, and the constructor of the control
            // station transmits - a ping and a power command - before it gets there.  A send that
            // failed in that window threw a NullPointerException out of this catch block, out of the
            // constructor, and out of main, which printed "Error occurred: null" and stopped.
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
                "network.canListenerRunning"
            );

            try
            {
                // Receive packets as they come in.  A failure must not end this loop: one transient
                // error used to terminate the thread for the rest of the session, leaving TrainControl
                // able to transmit but deaf - no feedback, no accessory echoes, no power state changes,
                // and path integrity validation failing every path.  The only condition we stop for is
                // the socket being closed, which is the loop test below.
                while (!socket.isClosed())
                {
                    try
                    {
                        // Wait to receive a datagram
                        socket.receive(packet);

                        // Only a datagram of the right LENGTH is parsed.
                        //
                        // One buffer is reused for every receive, and the message is read out of the
                        // buffer rather than out of the packet - so a short datagram (a probe, a
                        // truncated frame, a device of another generation) was parsed as its own
                        // header followed by whatever the last message left behind in the tail.  That
                        // is a stale locomotive or accessory command re-applied under an unrelated
                        // command byte, and the duplicate-suppression window cannot catch it because
                        // the bytes are not identical to the last packet.
                        if (packet.getLength() == buffer.length)
                        {
                            // Send message to listener
                            CANMessage message = model.createMessage(buffer);

                            if (!learn(buffer, message)) model.receiveMessage(message);
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