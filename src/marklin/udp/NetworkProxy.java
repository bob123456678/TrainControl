package marklin.udp;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import model.ModelListener;

/**
 *  This class facilitates communication with the Marklin CS2 over UDP
 */
public class NetworkProxy
{
    // Ports as defined in the marklin protocol
    public static final int RX_PORT = 15730;
    public static final int TX_PORT = 15731;
    
    // UDP socket used to send and receive packets
    private DatagramSocket socket;
    
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
        this.socket = new DatagramSocket(NetworkProxy.RX_PORT);
        this.transmitIP = transmitIP;
        this.transmitPort = NetworkProxy.TX_PORT;       
    }
    
    /**
     * Gets the IP
     * @return 
     */
    public String getIP()
    {
        return this.transmitIP.toString().replaceAll("/", "");
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
        
        // Start reader
        new ReadMessages().start();
    }
    
    /**
     * Public interface to send a message out to the CS2 
     
     * @param m a CS2Message object 
     * @return true on success, else false
     */
    public boolean sendMessage(CS2Message m)
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
            socket.send(packet);
            
            if (this.socket.isClosed())
            {
                this.socket = new DatagramSocket(NetworkProxy.RX_PORT);
            }
        }
        catch (IOException e)
        {
            this.model.log("Error: failed to send packet");
            this.model.log(e.getMessage());
            
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
            try
            {
                // Create a read buffer based on the protocol message length
                byte[] buffer = new byte[CS2Message.MESSAGE_LENGTH];

                // Create a packet to receive the data
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                // Receive packets as they come in
                while (true) 
                {
                    // Wait to receive a datagram
                    socket.receive(packet);

                    // Send message to listener
                    model.receiveMessage(new CS2Message(buffer));

                    // Reset the length of the packet just in case
                    packet.setLength(buffer.length);
                }
            }
            catch (IOException e)
            {
                // Do not exit on error, simply close the socket connection
                model.log("Fatal network error");
                model.log(e);
            }
            finally
            {
                // Close connection on error or when finished
            	socket.close();
            }
        }
    }
}