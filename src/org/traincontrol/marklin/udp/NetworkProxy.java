package org.traincontrol.marklin.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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

    /**
     * Whether this process may take ANY receive port instead of the Marklin one.
     *
     * Off, and it has to stay off for the application: a Central Station sends to 15730 and nowhere
     * else, so a TrainControl bound anywhere else would transmit perfectly and hear nothing back -
     * which looks like a railway that has stopped responding rather than like a misconfiguration.
     * That is why this is not a silent fallback when the port is busy.
     *
     * It exists for tests. Every test class that builds a MarklinControlStation binds this port, so
     * only one could run at a time, a hung class kept the port and made every class after it report a
     * mass failure, and none of them could run at all while Adam had the application open. None of
     * them receives anything from a Central Station either - there is none - so the port number is
     * pure contention with no benefit.
     *
     * Read from a system property rather than set in code, so the battery turns it on for the whole
     * run with one flag and no test has to remember anything:
     *
     *   java -Dtraincontrol.anyReceivePort=true ...
     *
     * Adam, 2026-08-24: "cant we have a mode where it runs without the ports so we stop being
     * blocked?"
     */
    public static final boolean ANY_RECEIVE_PORT =
        Boolean.getBoolean("traincontrol.anyReceivePort");

    // How long to wait after a recoverable receive error, so that a persistent fault cannot spin
    private static final long RECEIVE_ERROR_BACKOFF_MS = 50;
    
    // Transmission IP/port
    private final InetAddress transmitIP;
    private final int transmitPort;
    
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
     * The receive socket: the Marklin port, or any free one when ANY_RECEIVE_PORT says so.
     *
     * One method rather than two call sites, because there are two - the constructor and the reopen
     * in sendMessage - and a mode that applied to one of them would give a test a socket that worked
     * until the first send failure and then quietly tried to take the real port back.
     *
     * @return a bound socket
     * @throws IOException if even an ephemeral port cannot be had
     */
    private static DatagramSocket openReceiveSocket() throws IOException
    {
        // Port 0 asks the operating system for any free port.
        return ANY_RECEIVE_PORT ? new DatagramSocket() : new DatagramSocket(NetworkProxy.RX_PORT);
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

    // The listener thread, kept so that stopListening can end it
    private ReadMessages reader;

    /**
     * Closes the socket, which is what ends the listener.
     *
     * Nothing needs this while the application is running - the thread is a daemon and goes with the
     * JVM.  It is here so that a caller who creates a control station and finishes with it can give
     * the port back: a second init() in the same JVM otherwise found 15730 still held by the first.
     */
    public void stopListening()
    {
        if (this.socket != null && !this.socket.isClosed()) this.socket.close();
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
        //
        // A DAEMON, because it exists only to serve a running application: its only exit condition is
        // the socket closing, so as an ordinary thread it kept the JVM alive for ever after the caller
        // was done.  The GUI hid that behind System.exit(0); anything embedding TrainControl - the
        // example in org.traincontrol.examples, or a test - simply hung on return.
        this.reader = new ReadMessages();

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