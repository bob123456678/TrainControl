package marklin;

import base.Accessory;
import base.RemoteDeviceCollection;
import gui.TrainControlUI;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import marklin.file.CS2File;
import marklin.udp.CS2Message;
import marklin.udp.NetworkProxy;
import model.ModelListener;
import model.View;
import model.ViewListener;

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
    public static final String VERSION = "1.7.2";
    
    //// Settings
    
    // Locomotive database save file
    public static final String DATA_FILE_NAME = "LocDB.data";

    // Debug mode
    private static boolean debug = false;
        
    // Network sleep interval
    public static final long SLEEP_INTERVAL = 50;
    
    //// State
    
    // Locomotive database
    private final RemoteDeviceCollection<MarklinLocomotive, String> locDB;

    // Switch/signal database
    private final RemoteDeviceCollection<MarklinAccessory, Integer> accDB;

    // Feedback database
    private final RemoteDeviceCollection<MarklinFeedback, Integer> feedbackDB;
    
    // Route database
    private final RemoteDeviceCollection<MarklinRoute, Integer> routeDB;
    
    // Layouts
    private final RemoteDeviceCollection<MarklinLayout, String> layoutDB;
    
    // Mapping for int UID -> string UID
    private HashMap<Integer, List<String>> locIdCache;

    // Network proxy reference
    private final NetworkProxy NetworkInterface;
    
    // File parser class
    private CS2File fileParser;
    
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
    
    // Is this a CS3?
    private boolean isCS3 = false;
            
    public MarklinControlStation(NetworkProxy network, View view, boolean autoPowerOn, boolean debug)
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
        
        // Set debug mode
        this.debug(debug);
        
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
                    c.getFunctions(), c.getFunctionTypes(), c.getPreferredFunctions(), c.getPreferredSpeed());                
            }
            else if (c.getType() == MarklinSimpleComponent.Type.LOC_DCC)
            {
                newLocomotive(c.getName(), c.getAddress(), 
                    MarklinLocomotive.decoderType.DCC, 
                    c.getState() ? MarklinLocomotive.locDirection.DIR_FORWARD : MarklinLocomotive.locDirection.DIR_BACKWARD,
                    c.getFunctions(), c.getFunctionTypes(), c.getPreferredFunctions(), c.getPreferredSpeed());                
            }
            else if (c.getType() == MarklinSimpleComponent.Type.LOC_MM2)
            {
                newLocomotive(c.getName(), c.getAddress(), 
                    MarklinLocomotive.decoderType.MM2, 
                    c.getState() ? MarklinLocomotive.locDirection.DIR_FORWARD : MarklinLocomotive.locDirection.DIR_BACKWARD,
                    c.getFunctions(), c.getFunctionTypes(), c.getPreferredFunctions(), c.getPreferredSpeed());                
            }
            else if (c.getType() == MarklinSimpleComponent.Type.LOC_MULTI_UNIT)
            {
                newLocomotive(c.getName(), c.getAddress(), 
                    MarklinLocomotive.decoderType.MULTI_UNIT, 
                    c.getState() ? MarklinLocomotive.locDirection.DIR_FORWARD : MarklinLocomotive.locDirection.DIR_BACKWARD,
                    c.getFunctions(), c.getFunctionTypes(), c.getPreferredFunctions(), c.getPreferredSpeed());                
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
                newRoute(c.getName(), c.getAddress(), c.getRoute(), c.getS88(), c.getS88TriggerType(), c.getRouteEnabled(), c.getConditionS88s());
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
     * Returns the URL to the CS3 web app
     * @return
     */
    @Override
    public String getCS3AppUrl()
    {
        return this.fileParser.getCS3AppUrl();
    }
       
    /**
     * Parses layout files from the CS2 or local file system
     * @throws Exception 
     */
    private void syncLayouts() throws Exception
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
        
        // Code for basic address status
        /*Collections.sort(addresses);

        if (addresses.size() >= 1)
        {
            Integer min = addresses.get(0);
            Integer max = addresses.get(addresses.size() - 1);
            
            System.out.println("Minimum address:" + min);
            System.out.println("Maximum address:" + max);
            
            for (int i = 1; i < max; i++)
            {
                if (!addresses.contains(i))
                {
                    System.out.println("Free address: " + i);
                }
            }
        }*/
    }
    
    private Preferences getPrefs()
    {
        return Preferences.userNodeForPackage(TrainControlUI.class);
    }
    
    /**
     * Synchronizes CS2 state
     * @return 
     */
    @Override
    public final int syncWithCS2()
    {        
        // Read remote config files
        this.fileParser = new CS2File(NetworkInterface.getIP(), this);
             
        this.log("Starting Central Station database sync...");

        int num = 0;
                
        // Fetch Central Station databases
        try
        {            
            // Is this a CS2 or CS3?
            try
            {
                this.isCS3 = CS2File.isCS3(CS2File.getDeviceInfoURL(NetworkInterface.getIP()));
            }
            catch (Exception e)
            {
                this.log(e.toString());
            }
            
            this.log("Station detection result: " + (this.isCS3 ? "CS3" : "CS2"));
                           
            // Import layout
            String overrideLayoutPath = "";
            Preferences prefs = null;
            
            try
            {
                prefs = this.getPrefs();
                overrideLayoutPath = prefs.get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            }
            catch (Exception e)
            {
                this.log("Error loading user preferences; try re-running as admin.");
                
                if (debug)
                {
                    this.log(e.getMessage());
                    e.printStackTrace();
                }
            }
                
            if (!"".equals(overrideLayoutPath) && prefs != null)
            {
                fileParser.setLayoutDataLoc("file:///" + overrideLayoutPath + "/");
                
                this.log("Layout override setting enabled.  Will attempt to load static CS2 layout from: " + overrideLayoutPath);
                this.log("This path should contain the same 'gleisbild' file structure as the CS2 config folder.");
                
                try
                {
                    for (String name : this.layoutDB.getItemNames())
                    {
                        this.layoutDB.delete(name);
                    }
                    
                    syncLayouts();
                }
                catch (Exception e)
                {
                    if (debug)
                    {
                       this.log(e.getMessage());
                       e.printStackTrace();
                    }
                           
                    this.log("Error, reverting to default layout load.");
                    prefs.put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
                    fileParser.setDefaultLayoutDataLoc();
                    syncLayouts();
                }
            }
            else
            {      
                if (this.layoutDB.getItemNames().isEmpty())
                {
                    syncLayouts();
                }  
            }
            
            // Import routes
            for (MarklinRoute r : fileParser.parseRoutes())
            {
                // Other existing route with same name but different ID
                if (this.routeDB.hasName(r.getName()) && r.getId() != this.routeDB.getByName(r.getName()).getId())
                {
                    this.log("Deleting old route (duplicate name) " + this.routeDB.getByName(r.getName()).toString());

                    this.deleteRoute(r.getName());
                }
                
                // Delete route if it has changed
                if (this.routeDB.hasId(r.getId()) 
                        && (!r.getRoute().equals(this.routeDB.getById(r.getId()).getRoute()) || r.getS88() != this.routeDB.getById(r.getId()).getS88()
                            || r.getTriggerType() != this.routeDB.getById(r.getId()).getTriggerType()
                        ) 
                )
                {
                    this.log("Deleting old route (duplicate ID) " + this.routeDB.getById(r.getId()).toString());

                    this.deleteRoute(this.routeDB.getById(r.getId()).getName());
                }
                
                if (!this.routeDB.hasId(r.getId()))
                {
                    newRoute(r);
                    this.log("Added route " + r.getName());
                    num++;
                }
            }
            
            // Import locomotives
            List<MarklinLocomotive> parsedLocs;
            
            if (this.isCS3)
            {
                parsedLocs = fileParser.parseLocomotivesCS3();
            }
            else
            {
                parsedLocs = fileParser.parseLocomotives();
            }
                         
            for (MarklinLocomotive l : parsedLocs)
            {
                // Add new locomotives
                if (!this.locDB.hasId(l.getUID()))
                {
                    this.log("Added locomotive " + l.getName() 
                            + " with address " 
                            + l.getAddress() + " ("
                            + util.Conversion.intToHex(l.getIntUID()) + ")"
                            + " from Central Station"
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
        
        this.rebuildLocIdCache();
        
        this.log("Sync complete.");
        
        return num;
    }
    
    /**
     * Queries the central station for locomotive function state
     * @param name 
     */
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
    public final void debug(boolean state)
    {
        debug = state;
    }
    
    /**
     * Is the station a CS3?
     * @return 
     */
    @Override
    public boolean isCS3()
    {
        return this.isCS3;
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
            if (debug)
            {
                this.log(iOException.toString());
            }
            
            this.log("No compatible data file found, "
                    + "DB initializing with default data");
        } 
        catch (ClassNotFoundException classNotFoundException)
        {
            this.log("Bad data file for DB");            
        }

        return instance;
    }
        
    /**
     * Updates a route
     * @param name
     * @param newName
     * @param route 
     * @param s88 
     * @param s88Trigger 
     * @param routeEnabled 
     * @param conditionS88s 
     */
    @Override
    public final void editRoute(String name, String newName, Map<Integer, Boolean> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled,
            Map<Integer, Boolean> conditionS88s)
    {
        Integer id = this.routeDB.getByName(name).getId();
        
        // Disable the route so that the s88 condition stops firing
        this.routeDB.getByName(name).disable();
        
        this.deleteRoute(name);
        
        this.newRoute(newName.trim(), id, route, s88, s88Trigger, routeEnabled, conditionS88s);
    }
    
    /**
     * Returns a route
     * @param name
     * @return 
     */
    @Override
    public MarklinRoute getRoute(String name)
    {
        return this.routeDB.getByName(name);
    }
    
    /**
     * Adds a new route from file
     * @param r 
     * @return  
     */
    public final boolean newRoute(MarklinRoute r)
    {
        if (!this.routeDB.hasId(r.getId()) && !this.routeDB.hasName(r.getName().trim()))
        {
            this.routeDB.add(r, r.getName().trim(), r.getId());
            return true;
        }
        else
        {
            this.log("Route " + r.getId() + " or " + r.getName().trim() + " was already imported into database - skipping");
            return false;
        }
    }
    
    /**
     * Adds a new route from database
     * @param name
     * @param id
     * @param route 
     * @param s88 
     * @param s88Trigger 
     * @param routeEnabled 
     * @param conditionS88s 
     * @return  
     */
    public final boolean newRoute(String name, int id, Map<Integer, Boolean> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled,
            Map<Integer, Boolean> conditionS88s)
    {
        name = name.trim();
        
        if (!this.routeDB.hasId(id) && !this.routeDB.hasName(name))
        {
            this.routeDB.add(new MarklinRoute(this, name, id, route, s88, s88Trigger, routeEnabled, conditionS88s), name, id);    
            return true;
        }
        else
        {
            this.log("Route " + id + " or " + name + " was already imported into database - skipping");
            return false;
        }
    }
    
    /**
     * Adds a new route from user input
     * @param name
     * @param route 
     * @param s88 
     * @param s88Trigger 
     * @param routeEnabled 
     * @param conditionS88s 
     * @return creation status
     */
    @Override
    public final boolean newRoute(String name, Map<Integer, Boolean> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled,
        Map<Integer, Boolean> conditionS88s)
    {
        int newId = 1;
        if (this.routeDB.getItemIds().size() > 0)
        {
            newId = Collections.max(this.routeDB.getItemIds()) + 1;
        }
        
        name = name.trim();
        
        if (!this.routeDB.hasName(name))
        {
            this.routeDB.add(new MarklinRoute(this, name, newId, route, s88, s88Trigger, routeEnabled, conditionS88s), name, newId);  
                        
            return true;
        }
        else
        {
            return false;
        }
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
     * Adds a DCC locomotive to the system
     * @param name
     * @param address
     * @return 
     */
    @Override
    public final MarklinLocomotive newDCCLocomotive(String name, int address)
    {
        return newLocomotive(name, address, MarklinLocomotive.decoderType.DCC);
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
     * Returns the state of the passed feedback
     * @param name (the feedback module number)
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
     * Rebuilds our mapping between locomotive names and CS2 UIDs (needed due to the potential for duplicate MM2 addresses)
     */
    synchronized private void rebuildLocIdCache()
    {
        this.locIdCache = new HashMap<>();
        
        for (MarklinLocomotive l : this.locDB.getItems())
        {
            int id = l.getIntUID();
            if (!this.locIdCache.containsKey(id))
            {
                this.locIdCache.put(id, new LinkedList<>());
            }
            
            this.locIdCache.get(id).add(l.getUID());
        }
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
            Integer id = message.extractUID();
            
            List<String> locs = this.locIdCache.get(id);

            // Only worry about the message if it's a response
            // This prevents the gui from being updated when not connected
            // to the network, however, so remove this check when offline
            if (locs != null)
            {
                if (locs.size() > 0 && message.getResponse())
                {
                    for (String l : locs)
                    {
                        this.locDB.getById(l).parseMessage(message);
                    }

                    if (message.getResponse())
                    {
                        if (this.view != null) this.view.repaintLoc();
                    }
                }
                else if (locs.isEmpty())
                {
                    this.log("Unknown locomotive received command: " 
                        + MarklinLocomotive.addressFromUID(id));
                }
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
            locFunctionsOff(l);
        }
    }
    
    @Override
    public void locFunctionsOff(MarklinLocomotive l)
    {
        for (int i = 0; i < l.getNumF(); i++)
        {
            if (l.getF(i))
            {
                l.setF(i, false);
            }
        }
    }
    
    /**
     * Turns on all locomotives' lights
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
        
        this.rebuildLocIdCache();
        
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
        boolean[] functions, int[] functionTypes, boolean[] preferredFunctions, int preferredSpeed)
    {
        MarklinLocomotive newLoc = new MarklinLocomotive(this, address, type, name, dir, functions, functionTypes, preferredFunctions, preferredSpeed);
        
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
        boolean res = this.locDB.delete(name);
        
        if (res) this.rebuildLocIdCache();
        
        return res;
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
            
            this.rebuildLocIdCache();
            
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
     * Gets the configured CS2/3 IP address
     * @return
     */
    public String getIP()
    {
        return this.NetworkInterface.getIP();
    }
    
    /**
     * Manually executes a route
     * @param name 
     */
    @Override
    public final void execRoute(String name)
    {
        //this.log("Executing " + this.routeDB.getByName(name).toString());
        
        this.routeDB.getByName(name).execRoute();
    }
    
    @Override
    public final void deleteRoute(String name)
    {
        // Make sure automatic execution gets disables
        MarklinRoute r = this.routeDB.getByName(name);
        if (r != null)
        {
            r.disable();
        }
        
        this.routeDB.delete(name);
    }
    
    /**
     * Returns a route ID, or 0 if not found
     * @param name
     * @return 
     */
    @Override
    public int getRouteId(String name)
    {
        if (this.routeDB.hasName(name))
        {
            return this.routeDB.getByName(name).getId();
        }
        
        return 0;
    }
    
    /**
     * Gets route list, sorted by ID
     * @return 
     */
    @Override
    public List<String> getRouteList()
    {
        List<String> l = new LinkedList<>();
        List<Integer> ids = this.routeDB.getItemIds();
        Collections.sort(ids);
        
        for (int i : ids)
        {
            l.add(this.routeDB.getById(i).getName());
        }
                
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
    
    /**
     * Initialize with default values
     * @return
     * @throws UnknownHostException
     * @throws IOException 
     */
    public static MarklinControlStation init() throws UnknownHostException, IOException
    {
        return init(null, false, true, true, false);
    }
    
    /**
     * Main initialization method
     * @param initIP
     * @param simulate
     * @param showUI
     * @param autoPowerOn
     * @return
     * @throws UnknownHostException
     * @throws IOException 
     */
    public static MarklinControlStation init(String initIP, boolean simulate, boolean showUI, boolean autoPowerOn, boolean debug) throws UnknownHostException, IOException
    {
        // User interface
        TrainControlUI ui = new TrainControlUI();
        
        if (initIP == null)
        {
            initIP = ui.getPrefs().get(TrainControlUI.IP_PREF, null);
        }

        if (!simulate)
        {
            while (true)
            {
                try
                {
                    if (initIP == null)
                    {
                        initIP = JOptionPane.showInputDialog("Enter CS2 IP Address: ");

                        if (initIP == null)
                        {
                            System.out.println("No IP entered - shutting down.");
                            System.exit(1);
                        }
                    }

                    if (!CS2File.ping(initIP))
                    {
                        JOptionPane.showMessageDialog(null, "No response from " + initIP);
                    }
                    else
                    {
                        ui.getPrefs().put(TrainControlUI.IP_PREF, initIP);
                        break;
                    }
                }
                catch (Exception e)
                {
                    System.out.println("Invalid IP Specified");
                }

                initIP = null;
            }
        }
        else
        {
            initIP = null;
        }

        // Delegate the hard part
        NetworkProxy proxy = new NetworkProxy(InetAddress.getByName(initIP));

        // Initialize the central station
        MarklinControlStation model = 
          new MarklinControlStation(proxy, showUI ? ui : null, autoPowerOn, debug);

        // Set model
        if (showUI)
        {
            ui.setViewListener(model);
        }
        
        // Start execution
        proxy.setModel(model);

        // Connection failed - ask for IP on next run
        if (!model.getNetworkCommState())
        {
            ui.getPrefs().remove(TrainControlUI.IP_PREF);
        }
        
        return model;
    }
}
