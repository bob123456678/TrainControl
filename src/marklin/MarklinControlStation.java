package marklin;

import base.Accessory;
import base.Feedback;
import base.RemoteDeviceCollection;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import marklin.file.CS2File;
import marklin.udp.CS2Message;
import marklin.udp.NetworkProxy;
import model.ModelListener;
import model.View;
import model.ViewListener;
import util.Conversion;

/**
 * Main "station" class.  Mimics CS2 functionality.
 * 
 * This class waits for messages to come in, interprets them,
 * and updates state
 * 
 * @author Adam
 */
public class MarklinControlStation implements ViewListener, ModelListener
{
    // Verison number
    public static final String VERSION = "1.5.6";
    
    //// Settings
    
    // Locomotive database save file
    public static final String DATA_FILE_NAME = "LocDB.data";

    // Debug mode
    private static boolean debug = false;
        
    // Network sleep interval
    public static final long SLEEP_INTERVAL = 50;
    
    //// State
    
    // Locomotive database
    private final RemoteDeviceCollection<MarklinLocomotive, Integer> locDB;

    // Switch/signal database
    private final RemoteDeviceCollection<MarklinAccessory, Integer> accDB;

    // Feedback database
    private final RemoteDeviceCollection<MarklinFeedback, Integer> feedbackDB;
    
    // Route database
    private final RemoteDeviceCollection<MarklinRoute, Integer> routeDB;
    
    // Layouts
    private final RemoteDeviceCollection<MarklinLayout, String> layoutDB;

    // Network proxy reference
    private final NetworkProxy NetworkInterface;
    
    // GUI reference
    private final View view;
    
    // Is network communication on?
    private boolean on;
    
    // Is the power turned on?
    private boolean powerState;
        
    // Unique ID of the central station (0 for all stations)
    private int UID = 0;
    private int serialNumber;
    
    // Last message output
    private String lastMessage;
        
    public MarklinControlStation(NetworkProxy network, View view, boolean autoPowerOn)
    {        
        // Initialize maps
        this.locDB = new RemoteDeviceCollection<>();
        this.accDB = new RemoteDeviceCollection<>();
        this.feedbackDB = new RemoteDeviceCollection<>();
        this.routeDB = new RemoteDeviceCollection<>();
        this.layoutDB = new RemoteDeviceCollection<>();
        
        // Set references
        this.on = false;
        this.NetworkInterface = network;
        this.view = view;
        
        this.log("Marklin Control v" + VERSION);
        
        this.log("Restoring state...");
        
        // Restore state
        for (MarklinSimpleComponent c : this.restoreState())
        {            
            if (c.getType() == MarklinSimpleComponent.Type.LOC_MFX)
            {
                newLocomotive(c.getName(), c.getAddress(), 
                    MarklinLocomotive.decoderType.MFX, 
                    c.getState() ? MarklinLocomotive.locDirection.DIR_FORWARD : MarklinLocomotive.locDirection.DIR_BACKWARD,
                    c.getFunctions(), c.getFunctionTypes());                
            }
            else if (c.getType() == MarklinSimpleComponent.Type.LOC_MM2)
            {
                newLocomotive(c.getName(), c.getAddress(), 
                    MarklinLocomotive.decoderType.MM2, 
                    c.getState() ? MarklinLocomotive.locDirection.DIR_FORWARD : MarklinLocomotive.locDirection.DIR_BACKWARD,
                    c.getFunctions(), c.getFunctionTypes());                
            }
            else if (c.getType() == MarklinSimpleComponent.Type.LOC_MULTI_UNIT)
            {
                newLocomotive(c.getName(), c.getAddress(), 
                    MarklinLocomotive.decoderType.MULTI_UNIT, 
                    c.getState() ? MarklinLocomotive.locDirection.DIR_FORWARD : MarklinLocomotive.locDirection.DIR_BACKWARD,
                    c.getFunctions(), c.getFunctionTypes());                
            }
            else if (c.getType() == MarklinSimpleComponent.Type.SIGNAL)
            {
                newSignal(Integer.toString(c.getAddress() + 1), c.getAddress(), c.getState());                
            }
            else if (c.getType() == MarklinSimpleComponent.Type.SWITCH)
            {
                newSwitch(Integer.toString(c.getAddress() + 1), c.getAddress(), c.getState());                
            }
            else if (c.getType() == MarklinSimpleComponent.Type.FEEDBACK)
            {   
                newFeedback(c.getAddress(), null);
                
                // It would be more consistent not to restore this...
                // When we restore the state, it might be invalid if the CS2 was used without this program running
                // Feedbacks are clickable and should be synced manually
                this.feedbackDB.getById(c.getAddress()).setState(c.getState());
            }
            else if (c.getType() == MarklinSimpleComponent.Type.ROUTE)
            {
                newRoute(c.getName(), c.getAddress(), c.getRoute());
            }
        }
        
        this.log("State restored.");
        
        if (syncWithCS2() >= 0)
        {
            this.log("Connected to Central Station at " + network.getIP());

            // Turn on network communication and turn on the power
            this.on = true;
            
            this.sendPing();
            
            if (autoPowerOn) this.go();
        }
        else
        {
            this.log("Network connection not established");
        }   
    }
    
    /**
     * Synchronizes CS2 state
     */
    @Override
    public final int syncWithCS2()
    {
        // Read remote config files
        CS2File fileParser = new CS2File(NetworkInterface.getIP(), this);
     
        this.log("Starting CS2 database sync...");

        int num = 0;
                
        // Fetch CS2's databases
        try
        {
            for (MarklinRoute r : fileParser.parseRoutes())
            {
                if (!this.routeDB.hasId(r.getId()))
                {
                    this.log("Added route " + r.getName());
                    newRoute(r);
                    num++;
                }
            }
                        
            if (this.layoutDB.getItemNames().isEmpty())
            {
                for (MarklinLayout l : fileParser.parseLayout())
                {
                    this.layoutDB.add(l, l.getName(), l.getName());
                    
                    this.log("Imported layout " + l.getName());
                    
                    for (MarklinLayoutComponent c : l.getAll())
                    {
                        if (c.isSwitch() || c.isSignal() || c.isUncoupler())
                        {                            
                            int newAddress = c.getAddress() - 1;
                            int targetAddress = MarklinAccessory.UIDfromAddress(newAddress);
                            
                            // Make sure all components are added
                            if (!this.accDB.hasId(targetAddress) ||
                               // The acessory exists, but type in our DB does not match what the CS2 has stored.  Re-create the accessory.
                               (this.accDB.hasId(targetAddress) && this.accDB.getById(targetAddress).isSignal() != c.isSignal())
                            )
                            {
                                // Skip components without a digital address
                                if (c.getAddress() == 0) continue;
                                
                                if (c.isSwitch() || c.isUncoupler())
                                {
                                    newSwitch(Integer.toString(c.getAddress()), newAddress, c.getState() != 1);
                                    
                                    if (c.isThreeWay())
                                    {
                                        if (!this.accDB.hasId(c.getAddress() + 1))
                                        {
                                            newSwitch(Integer.toString(c.getAddress() + 1), newAddress + 1, c.getState() == 2);                                            
                                        }
                                    }
                                }
                                else if (c.isSignal())
                                {
                                    newSignal(Integer.toString(c.getAddress()), newAddress, c.getState() != 1);
                                }
                                
                                this.log("Adding " + this.accDB.getById(targetAddress));
                            }
                            else
                            {            
                                // Actually not needed, since the station
                                // only updates its file on boot...
                                /*int cState = c.getState();
                                boolean state = this.accDB.getById(targetAddress).isSwitched();
                                     
                                // Ensure our state is synchronized
                                if (c.isThreeWay())
                                {
                                    boolean state2 = this.accDB.getById(targetAddress + 1).isSwitched();

                                    if (cState == 1 && state == true)
                                    {
                                        this.accDB.getById(targetAddress).setSwitched(false);
                                        this.accDB.getById(targetAddress + 1).setSwitched(false);
                                    }
                                    
                                    if (cState == 2 && state2 != true)
                                    {
                                        this.accDB.getById(targetAddress).setSwitched(false);
                                        this.accDB.getById(targetAddress + 1).setSwitched(true);
                                    }
                                    
                                    if (cState == 0 && state == false)
                                    {
                                        this.accDB.getById(targetAddress).setSwitched(true);
                                    }
                                    
                                    if (cState == 0 && state2 == true)
                                    {
                                        this.accDB.getById(targetAddress + 1).setSwitched(false);
                                    }
                                }
                                else if (c.isSwitch() || c.isSignal())
                                {
                                    if (cState == 1)
                                    {
                                        this.accDB.getById(targetAddress).setSwitched(false);
                                    }
                                    
                                    if (cState == 0)
                                    {
                                        this.accDB.getById(targetAddress).setSwitched(true);
                                    }   
                                }*/
                            }  
                            
                            c.setAccessory(this.accDB.getById(targetAddress));
                            
                            if (c.isThreeWay())
                            {
                                c.setAccessory2(this.accDB.getById(targetAddress + 1));
                            }
                        }
                        else if (c.isFeedback())
                        {                            
                            if (!this.feedbackDB.hasId(c.getRawAddress()))
                            {
                                newFeedback(c.getRawAddress(), null);   
                            }
                            
                            // CS2 gives us no state info :(
                            c.setFeedback(this.feedbackDB.getById(c.getRawAddress()));
                        }   
                        else if (c.isRoute())
                        {
                            c.setRoute(this.routeDB.getById(c.getAddress()));
                        }
                    }
                }
            }
            
            for (MarklinLocomotive l : fileParser.parseLocomotives())
            {
                // Add new locomotives
                if (!this.locDB.hasId(l.getUID()))
                {
                    this.log("Added locomotive " + l.getName() 
                            + " with address " 
                            + util.Conversion.intToHex(l.getAddress()) 
                            + " from CS2"
                    );

                    newLocomotive(l.getName(), l.getAddress(), l.getDecoderType(), l.getFunctionTypes());
                    num++;
                }
                
                // Show message that we did not sync a loc with a duplicate address
                if (this.locDB.hasId(l.getUID()) && !this.locDB.getById(l.getUID()).getName().equals(l.getName()))
                {
                    this.log("Locomotive " + l.getName() +
                            " was not imported because database already contains a locomotive with the same address: " + 
                            this.locDB.getById(l.getUID()).getName() + " (" + l.getUID() + ")");
                }
                
                // Set current locomotive icon - use name for MM2 due to risk of address collision
                if (l.getDecoderType() == MarklinLocomotive.decoderType.MM2)
                {
                    if (this.locDB.getByName(l.getName()) != null && l.getImageURL() != null)
                    {
                        this.locDB.getByName(l.getName()).setImageURL(l.getImageURL());                         
                    }
                }
                else
                {
                    if (this.locDB.getById(l.getUID()) != null && l.getImageURL() != null)
                    {
                        this.locDB.getById(l.getUID()).setImageURL(l.getImageURL());                         
                    }
                }
            }
        }
        catch (Exception e)
        {
             this.log("Failed to sync locomotive DB.");
             
             if (debug)
             {
                this.log(e.getMessage());
                e.printStackTrace();
             }
             
             return -1;
        }
        
        this.log("Sync complete.");
        
        return num;
    }
    
    @Override
    public void syncLocomotive(String name)
    {
        if (this.locDB.getByName(name) != null)
        {
            this.log("Syncing locomotive " + name);
            this.locDB.getByName(name).syncFromNetwork();
        }
    }
    
    /**
     * Sets debug state
     * @param state 
     */
    public void debug(boolean state)
    {
        debug = state;
    }
    
    /**
     * Saves initialized component database to a file
     */
    @Override
    public void saveState()
    {
        List<MarklinSimpleComponent> l = new LinkedList<>();
        
        for (MarklinLocomotive loc : this.locDB.getItems())
        {
            l.add(new MarklinSimpleComponent(loc));
        }
        
        for (MarklinAccessory acc : this.accDB.getItems())
        {
            l.add(new MarklinSimpleComponent(acc));
        }
        
        for (MarklinRoute r : this.routeDB.getItems())
        {
            l.add(new MarklinSimpleComponent(r));
        }
        
        for (MarklinFeedback f : this.feedbackDB.getItems())
        {
            l.add(new MarklinSimpleComponent(f));
        }
        
        try
        {
            // Write object with ObjectOutputStream to disk using
            // FileOutputStream
            ObjectOutputStream obj_out = new ObjectOutputStream(
                new FileOutputStream(MarklinControlStation.DATA_FILE_NAME));

            // Write object out to disk
            obj_out.writeObject(l);

            this.log("Saving DB to disk.");
        } 
        catch (IOException iOException)
        {
            this.log("Could not save DB. " + iOException.getMessage());
        }
    }

    /**
     * Restores list of initialized components from a file
     * @return 
     */
    public final List<MarklinSimpleComponent> restoreState()
    {
        List<MarklinSimpleComponent> instance = new LinkedList<>();

        try
        {
            // Read object using ObjectInputStream
            ObjectInputStream obj_in = new ObjectInputStream(
                new FileInputStream(MarklinControlStation.DATA_FILE_NAME)
            );
            
            // Read an object
            Object obj = obj_in.readObject();

            if (obj instanceof List)
            {
                // Cast object
                instance = (List<MarklinSimpleComponent>) obj;
            }

            this.log("DB loaded from file.");
        } 
        catch (IOException iOException)
        {
            this.log("No data file found, "
                    + "DB initializing with default data");
        } 
        catch (ClassNotFoundException classNotFoundException)
        {
            this.log("Bad data file for DB");            
        }

        return instance;
    }
    
    /**
     * Adds a new route from file
     * @param r 
     */
    public final void newRoute(MarklinRoute r)
    {
        this.routeDB.add(r, r.getName(), r.getId());
    }
    
    /**
     * Adds a new route from user input
     * @param name
     * @param id
     * @param route 
     */
    public final void newRoute(String name, int id, Map<Integer, Boolean> route)
    {
        this.routeDB.add(new MarklinRoute(this, name, id, route), name, id);        
    }
    
     /**
     * Adds an MFX locomotive to the system
     * @param name
     * @param address
     * @return 
     */
    @Override
    public final MarklinLocomotive newMFXLocomotive(String name, int address)
    {
        return newLocomotive(name, address, MarklinLocomotive.decoderType.MFX);
    }
    
    /**
     * Adds a MM2 locomotive to the system
     * @param name
     * @param address
     * @return 
     */
    @Override
    public final MarklinLocomotive newMM2Locomotive(String name, int address)
    {
        return newLocomotive(name, address, MarklinLocomotive.decoderType.MM2);
    }
    
    /**
     * Fetches a locomotive
     * @param name
     * @return 
     */
    @Override
    public final MarklinLocomotive getLocByName(String name)
    {
        return this.locDB.getByName(name);     
    }
    
    /**
     * Fetches an accessory
     * @param name
     * @return 
     */
    @Override
    public final MarklinAccessory getAccessoryByName(String name)
    {
        return this.accDB.getByName(name);
    }
    
    /**
     * Adds a new signal
     * @param name
     * @param address
     * @param state
     * @return 
     */
    public final MarklinAccessory newSignal(String name, int address, boolean state)
    {
        return newAccessory("Signal " + name, address, Accessory.accessoryType.SIGNAL, state);
    }
    
    /**
     * Adds a new switch
     * @param name
     * @param address
     * @param state
     * @return 
     */
    public final MarklinAccessory newSwitch(String name, int address, boolean state)
    {
        return newAccessory("Switch " + name, address, Accessory.accessoryType.SWITCH, state);
    }
    
    /**
     * Adds a new feedback based on a network message
     * @param id
     * @param message
     * @return 
     */
    public final MarklinFeedback newFeedback(int id, CS2Message message)
    {
        MarklinFeedback newFb = new MarklinFeedback(this,id,message);
                
        this.feedbackDB.add(newFb, newFb.getName(), newFb.getUID());
        
        return newFb;
    }
    
    /**
     * Returns whether or not the passed feedback object has been set
     * @param name
     * @return 
     */
    @Override
    public final boolean isFeedbackSet(String name)
    {
        return this.feedbackDB.hasName(name);
    }
    
    /**
     * Returns the state of the passed feedbacks
     * @param name
     * @return 
     */
    @Override
    public final boolean getFeedbackState(String name)
    {
        MarklinFeedback fb = this.feedbackDB.getByName(name);
        
        if (null != fb)
        {
            return fb.isSet();
        }
           
        return false;
    }
    
    /**
     * Receives a network messages from the CS2 for interpretation
     * @param message
     */
    @Override
    synchronized public void receiveMessage(CS2Message message)
    {
        // Prints out each message
        if (MarklinControlStation.debug)
        {
            this.log(message.toString());
        }
        
        // Send the message to the appropriate listener
        if(message.isLocCommand())
        {   
            int id = message.extractUID();

            // Only worry about the message if it's a response
            // This prevents the gui from being updated when not connected
            // to the network, however, so remove this check when offline
            if (this.locDB.hasId(id) && message.getResponse())
            {
                this.locDB.getById(id).parseMessage(message);
                
                if (message.getResponse())
                {
                    if (this.view != null) this.view.repaintLoc();
                }
            }
            else if (!this.locDB.hasId(id))
            {
                this.log("Unknown locomotive received command: " 
                    + MarklinLocomotive.addressFromUID(id));
            }
        }
        else if (message.isAccessoryCommand())
        {
            int id = message.extractUID();

            if (this.accDB.hasId(id) && message.getResponse())
            {
                this.accDB.getById(id).parseMessage(message);
                
                 if (this.view != null) this.view.repaintSwitches();
            }
        }
        else if (message.isFeedbackCommand())
        {
            int id = message.extractShortUID();
            
            if (this.feedbackDB.hasId(id))
            {
                this.feedbackDB.getById(id).parseMessage(message);
            }
            else
            {
                newFeedback(id, message);   
            }
        }
        else if (message.isSysCommand())
        {
            if (message.getSubCommand() == CS2Message.CMD_SYSSUB_GO)
            {
                this.powerState = true;
                
                 if (this.view != null) this.view.updatePowerState();
                this.log("Power On");
            }
            else if (message.getSubCommand() == CS2Message.CMD_SYSSUB_STOP)
            {
                this.powerState = false;
                
                if (this.view != null) this.view.updatePowerState();
                this.log("Power Off");
            }
        }
        else if (message.isPingCommand())
        {
            // Set the serial number if it is not already set
            if (this.UID == 0 && message.getResponse() && message.getLength() == 8)
            {
                int payload = CS2Message.mergeBytes(
                        new byte[]{message.getData()[6], message.getData()[7]}
                );
                
                // 0x0000 means this is the central station
                if (payload == 0)
                {
                    this.UID = message.extractUID();
                    this.serialNumber = (message.extractUID() - 0x43533200) / 2;

                    this.log("Connected to Central Station with serial number " + this.serialNumber);
                }
            }
        }
    }
        
    /**
     * Executes a command
     * @param m 
     */
    public void exec(CS2Message m)
    {
        if (on)
        {
            this.NetworkInterface.sendMessage(m);
        }
        else
        {
            if (debug)
            {
                this.log("Network transmission disabled\n" + m.toString());
            }
        }    
        
        try
        {
            Thread.sleep(SLEEP_INTERVAL);
        } catch (InterruptedException ex)
        {
            
        }
    }
        
    /**
     * Enables or disables network communication
     * @param on 
     */
    public void setNetworkCommState(boolean on)
    {
        this.on = on;   
    }
    
    public boolean getNetworkCommState()
    {
        return this.on;
    }
    
    /**
     * Logs a message
     * @param message 
     */
    @Override
    public final void log(String message)
    {
        if (message != null && !message.equals(this.lastMessage))
        {
            // TODO - write to file, suppress, etc.
            if (this.view != null)
            {
                this.view.log(message);    
            }
            
            System.out.println(message);
            this.lastMessage = message;
        }
    }
    
    /**
     * Logs a message
     * @param message 
     */
    public final void logPartial(String message)
    {
        // TODO - write to file, suppress, etc.
        if (this.view != null)
        {
            this.view.log(message);    
        }
        
        System.out.print(message);   
    }
    
    /**
     * Pings the Central Station so that we can discover its UID
     */
    private void sendPing()
    {        
        this.exec(new CS2Message(
            CS2Message.CAN_CMD_PING,
            new byte[0]
        ));
    }
    
    /**
     * Stops all locomotives / system halt
     */
    @Override
    public void stopAllLocs()
    {	    	
        this.exec(new CS2Message(
            CS2Message.CMD_SYSTEM,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              CS2Message.CMD_SYSSUB_HALT
            }
        ));
        
        for (MarklinLocomotive l : this.locDB.getItems())
        {
            if (l.getSpeed() > 0)
            {
                l.stop().setSpeed(0);
            }
        }
        
        this.view.repaintLoc();
    }
    
    /**
     * Disables all active functions
     */
    @Override
    public void allFunctionsOff()
    {
        for (MarklinLocomotive l : this.locDB.getItems())
        {
            for (int i = 0; i < l.getNumF(); i++)
            {
                if (l.getF(i))
                {
                    l.setF(i, false);
                }
            }
        }
    }
    
    /**
     * Disables all active functions
     * @param locomotives
     */
    @Override
    public void lightsOn(List<String> locomotives)
    {
        for (String l : locomotives)
        {
            if (this.locDB.hasName(l))
            {
                this.getLocByName(l).lightsOn();
            }
        }
    }
    
    /**
     * Turns off the power
     */
    @Override
    public void stop()
    {	    	    
        this.exec(new CS2Message(
            CS2Message.CMD_SYSTEM,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              CS2Message.CMD_SYSSUB_STOP
            }
        ));	    
    }

    /**
     * Turns on the power
     */
    @Override
    public final void go()
    {	    	    
        this.exec(new CS2Message(
            CS2Message.CMD_SYSTEM,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              CS2Message.CMD_SYSSUB_GO
            }
        ));	    
    }
    
    /**
     * Adds a new locomotive to the internal database with no known state
     * @param name
     * @param address
     * @param type
     * @return 
     */
    private MarklinLocomotive newLocomotive(String name, int address, 
        MarklinLocomotive.decoderType type)
    {
        MarklinLocomotive newLoc = new MarklinLocomotive(this, address, type, name);
        
        this.locDB.add(newLoc, name, newLoc.getUID());
        
        return newLoc; 
    }
    
    /**
     * Adds a new locomotive to the internal database with no state exception function types
     * @param name
     * @param address
     * @param type
     * @return 
     */
    private MarklinLocomotive newLocomotive(String name, int address, 
        MarklinLocomotive.decoderType type, int[] functionTypes)
    {
        MarklinLocomotive newLoc = new MarklinLocomotive(this, address, type, name, functionTypes);
        
        this.locDB.add(newLoc, name, newLoc.getUID());
        
        return newLoc; 
    }
    
    /**
     * Adds a new locomotive with expanded state
     * @param name
     * @param address
     * @param type
     * @param dir
     * @param functions
     * @param functionTypes
     * @return 
     */
    private MarklinLocomotive newLocomotive(String name, int address, 
        MarklinLocomotive.decoderType type, MarklinLocomotive.locDirection dir, 
        boolean[] functions, int[] functionTypes)
    {
        MarklinLocomotive newLoc = new MarklinLocomotive(this, address, type, name, dir, functions, functionTypes);
        
        this.locDB.add(newLoc, name, newLoc.getUID());
        
        return newLoc; 
    }
    
    /**
     * Adds a new accessory to the internal database
     * @param name
     * @param address
     * @param type
     * @return 
     */
    private MarklinAccessory newAccessory(String name, int address, Accessory.accessoryType type, boolean state)
    {
        MarklinAccessory newAccessory = new MarklinAccessory(this, address, type, name, state);
        
        this.accDB.add(newAccessory, name, newAccessory.getUID());
        
        return newAccessory;
    }

    /**
     * Returns the locomotives that exist in the database
     * @return 
     */
    @Override
    public List<String> getLocList()
    {
        List<String> l = this.locDB.getItemNames();
        Collections.sort(l);
                
        return l;
    }
    
    /**
     * Deletes the locomotive with the given name
     * @param name
     * @return 
     */
    @Override
    public boolean deleteLoc(String name)
    {
        return this.locDB.delete(name);
    }
    
    /**
     * Returns a locomotive address
     * @param name
     * @return 
     */
    @Override
    public String getLocAddress(String name)
    {
        MarklinLocomotive l = this.locDB.getByName(name);
        String address;
        
        if (l.getDecoderType() == MarklinLocomotive.decoderType.MFX 
                || l.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
        {
            address = "0x" + Integer.toHexString(l.getAddress());
        }
        else
        {
            address = Integer.toString(l.getAddress());
        }
        
        return address;
    }
    
    /**
     * Renames a locomotive in the database
     * @param name
     * @param newName
     * @return 
     */
    @Override
    public boolean renameLoc(String name, String newName)
    {
        MarklinLocomotive l = this.locDB.getByName(name);
        MarklinLocomotive l2 = this.locDB.getByName(newName);
        
        if (l != null && l2 == null)
        {
            this.locDB.delete(name);
            
            l.rename(newName);
            
            this.locDB.add(l, newName, l.getUID());
            
            return true;
        }
        
        return false;
    }

    @Override
    public void setAccessoryState(int address, boolean state)
    {              
        MarklinAccessory a;
        
        if (this.accDB.hasId(MarklinAccessory.UIDfromAddress(address - 1)))
        {
            a = this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1));
        }
        else
        {
            a = this.newSwitch(Integer.toString(address), address - 1, !state);
        }
        
        if (state)
        {
            a.turn();
        }
        else
        {
            a.straight();
        }             
    }
    
    /**
     *
     * @return
     */
    public String getIP()
    {
        return this.NetworkInterface.getIP();
    }
    
    @Override
    public final void execRoute(String name)
    {
        this.log("Executing route " + name);
        
        this.routeDB.getByName(name).execRoute();
    }
    
    @Override
    public final void deleteRoute(String name)
    {
        this.routeDB.delete(name);
    }
    
    @Override
    public List<String> getRouteList()
    {
        List<String> l = this.routeDB.getItemNames();
        Collections.sort(l);
                
        return l;
    }
    
    @Override
    public boolean getAccessoryState(int address)
    { 
        // Get by name because UID in database != address
        if (this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1)) != null)
        {
            return this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1)).isSwitched();
        }
        else
        {
            this.newSwitch(Integer.toString(address), address - 1, false);
        }
        
        return false;
    }
    
    @Override
    public boolean getPowerState()
    {
        return this.powerState;
    }

    @Override
    public List<String> getLayoutList()
    {
        List<String> l = this.layoutDB.getItemNames();
        Collections.sort(l);
                
        return l;
    }

    @Override
    public MarklinLayout getLayout(String name)
    {
        return this.layoutDB.getByName(name);
    }
    
    public View getGUI()
    {
        return this.view;
    }
}
