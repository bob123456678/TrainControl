package org.traincontrol.marklin;

import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.automation.Layout;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RemoteDeviceCollection;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.MarklinLocomotive.decoderType;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.CSDetect;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.model.ModelListener;
import org.traincontrol.model.View;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.Conversion;

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
    public static final String RAW_VERSION = "2.5.3";
    
    // Window/UI titles
    public static final String VERSION = "v" + RAW_VERSION + " for Marklin Central Station 2 & 3";
    public static final String PROG_TITLE = "TrainControl ";
    
    //// Settings
    
    // Locomotive database save file
    public static final String DATA_FILE_NAME = "LocDB.data";

    // Debug mode
    private boolean debug = false;
    
    // Do we print out packets in debug mode?
    public static boolean DEBUG_LOG_NETWORK = true;
    
    // Do we parse mock packets when not connected to the central station and in debug mode?
    // This will update the UI when locomotive/function/switch commands get sent
    public static boolean DEBUG_SIMULATE_PACKETS = false;
        
    // Network sleep interval
    public static final long SLEEP_INTERVAL = 50;
    
    // The ID where we start internal routes
    private static final int ROUTE_STARTING_ID = 1000;
    
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
    private boolean powerState = true; // default to true unless power is turned off
        
    // Unique ID of the central station (0 for all stations)
    private int UID = 0;
    private int serialNumber;
    
    // Last message output
    private String lastMessage;
    
    // Is this a CS3?
    private boolean isCS3 = false;
    
    // Automation controller
    private Layout autoLayout;
    
    // Number of network messages processed
    private int numMessagesProcessed = 0;
    
    // Ping metrics
    private long pingStart;
    private double lastLatency;

    // Thread pools for network messages
    CS2Message lastPacket;
    private ExecutorService locMessageProcessor = Executors.newFixedThreadPool(1);
    private ExecutorService feedbackMessageProcessor = Executors.newFixedThreadPool(1);
    private ExecutorService systemMessageProcessor = Executors.newFixedThreadPool(1);
    
    private static final Logger log = Logger.getLogger(MarklinControlStation.class.getName());
                    
    public MarklinControlStation(NetworkProxy network, View view, boolean autoPowerOn, boolean debug)
    {       
        // Configure logger
        log.setUseParentHandlers(false);

        ConsoleHandler consoleHandler = new ConsoleHandler()
        {
            {
                setOutputStream(System.out); // Set output stream to System.out
            }
        };
        
        consoleHandler.setFormatter(new Formatter()
        {
            @Override
            public String format(LogRecord record)
            {
                return String.format("%s %s%n", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(System.currentTimeMillis()), record.getMessage());
            }
        });
        
        log.addHandler(consoleHandler);
        
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
        
        this.log(PROG_TITLE + VERSION);
        
        this.log("Restoring state...");
        
        // Restore state
        for (MarklinSimpleComponent c : this.restoreState())
        {            
            if (c.getLocType() != null)
            {
                newLocomotive(c);
            }
            else if (c.getType() == MarklinSimpleComponent.Type.SIGNAL || c.getType() == MarklinSimpleComponent.Type.SWITCH)
            {
                MarklinAccessory newAccessory = newAccessory(c.getAddress() + 1, c.getAddress(), 
                        c.getType() == MarklinSimpleComponent.Type.SIGNAL ? MarklinAccessory.accessoryType.SIGNAL : MarklinAccessory.accessoryType.SWITCH,
                        c.getAccessoryDecoderType(),
                        c.getState(), c.getNumActuations());                
            
                if (!newAccessory.isValidAddress())
                {
                    this.accDB.delete(newAccessory.getName());
                    this.log("Deleted invalid accessory from database: " + newAccessory.getName());
                }
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
                newRoute(c.getName(), c.getAddress(), c.getRoute(), c.getS88(), c.getS88TriggerType(), c.getRouteEnabled(), c.getConditions());
            }
        }
                
        this.log("State restored.");
        
        if (syncWithCS2() >= 0)
        {
            this.log("Imported data from Central Station at " + network.getIP());

            // Turn on network communication and turn on the power
            this.on = true;
            
            this.sendPing(false);
            
            if (autoPowerOn) this.go(); 
        }
        else
        {
            this.log("Central Station network connection not established.");
        } 
        
        // Resolve linked locomotives now that we have loaded everything
        for (MarklinLocomotive l : this.getLocomotives())
        {
            l.setLinkedLocomotives();
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
     * Saves the CS2 layout to the local filesystem
     * @param path
     * @throws Exception 
     */
    @Override
    public void downloadLayout(File path) throws Exception
    {
        this.fileParser.downloadCS2Layout(path);
    }
       
    /**
     * Parses layout files from the CS2 or local file system
     * @throws Exception 
     */
    private void syncLayouts() throws Exception
    {
        // Prune stale feedbacks
        List<Integer> feedbackAddresses = new LinkedList<>();
        
        // Get accessory definition info for the layout
        List<MarklinAccessory> accs = new LinkedList<>();
        
        try
        {
            // true to prefer the local file
            accs = fileParser.getMagList(true);
        }
        catch (Exception e)
        {
            if (isDebug())
            {
                this.log("Layout: no magnetartikel.cs2 found. DCC definitions unavailable.");
            }
        }

        for (MarklinLayout l : fileParser.parseLayout(accs))
        {
            this.layoutDB.add(l, l.getName(), l.getName());

            this.log("Imported layout " + l.getName());

            for (MarklinLayoutComponent c : l.getAll())
            {
                if (c.isSwitch() || c.isSignal() || c.isUncoupler())
                {                            
                    int newAddress = c.getAddress() - 1;                    
                    int targetAddress = MarklinAccessory.UIDfromAddress(newAddress, c.getProtocol());
                    
                    // Make sure all components are added
                    if (!this.accDB.hasId(targetAddress) ||
                        // The acessory exists, but type in our DB does not match what the CS2 has stored.  Re-create the accessory.
                       (this.accDB.hasId(targetAddress) && this.accDB.getById(targetAddress).isSignal() != c.isSignal()) ||
                            
                        // Create / convert the second accessory to switch if needed
                        c.isThreeWay() && (
                            !this.accDB.hasId(targetAddress + 1) ||
                            (this.accDB.hasId(targetAddress + 1) && this.accDB.getById(targetAddress + 1).isSignal() != c.isSignal())
                        )
                    )
                    {
                        // Skip components without a digital address
                        if (c.getAddress() <= 0)
                        {
                            this.log("Layout: invalid accessory address: " + c.getTypeName() + " " + c.getAddress() + " at " + c.getX() + "," + c.getY() + " ");
                            continue;
                        }
                        
                        if (c.isSwitch() || c.isUncoupler())
                        {
                            newAccessory(c.getAddress(), newAddress, Accessory.accessoryType.SWITCH, c.getProtocol(), c.getState() != 1);

                            if (c.isThreeWay())
                            {
                                newAccessory(c.getAddress() + 1, newAddress + 1, Accessory.accessoryType.SWITCH, c.getProtocol(), c.getState() == 2);                                            
                            }
                        }
                        else if (c.isSignal())
                        {
                            newAccessory(c.getAddress(), newAddress, Accessory.accessoryType.SIGNAL, c.getProtocol(), c.getState() != 1);
                        }

                        this.log("Adding " + this.accDB.getById(targetAddress).getName());
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
                    
                    feedbackAddresses.add(c.getRawAddress());
                }   
                else if (c.isRoute())
                {
                    MarklinRoute r = this.routeDB.getById(c.getAddress());
                    
                    if (r == null)
                    {
                        this.log("Layout warning: route button with address " + c.getAddress() + " at " + c.getX() + "," + c.getY() + " does not correspond to an existing route.");
                    }
                    
                    c.setRoute(r);
                }
            }
        }
        
        // Prune stale feedback
        if (!feedbackAddresses.isEmpty())
        {
            for (Integer feedbackId : this.feedbackDB.getItemIds())
            {
                if (!feedbackAddresses.contains(feedbackId))
                {
                    MarklinFeedback fb = this.feedbackDB.getById(feedbackId);
                    this.log("Pruning feedback that is not present on any layout: " + fb.getName());
                    this.feedbackDB.delete(fb.getName());
                }
            }
        }
    }
    
    /**
     * Delays until the power state matches the specified state
     * @param state
     * @throws InterruptedException 
     */
    @Override
    public void waitForPowerState(boolean state) throws InterruptedException
    {
        while (this.getPowerState() != state)
        {
            Thread.sleep(Locomotive.POLL_INTERVAL);
        }
    }
        
    /**
     * Returns the auto layout class (and creates it if it does not yet exist)
     * @return 
     */
    @Override
    public Layout getAutoLayout()
    {
        if (this.autoLayout == null)
        {
            this.autoLayout = new Layout(this);
        }
        
        return this.autoLayout;
    }
    
    /**
     * Parses JSON corresponding to a layout automation config file
     * Resets any existing automation
     * @param s
     */
    @Override
    public void parseAuto(String s)
    {        
        if (this.autoLayout != null)
        {
            this.autoLayout.invalidate();
            this.autoLayout.stopLocomotives();
        }
        
        this.autoLayout = Layout.fromJSON(s, this);
    }
    
    /**
     * Gets cumulative locomotive runtime for the number of days specified from the current date
     * @param days
     * @param offset
     * @return 
     */
    @Override
    public TreeMap<String, Long> getDailyRuntimeStats(int days, long offset)
    {
        long startDate = System.currentTimeMillis() - (offset * 86400000);
        
        TreeMap stats = new TreeMap<>(Comparator.reverseOrder());
        
        for (int i = 0; i < Math.abs(days); i++)
        {
            final long date = startDate;
            
            stats.put(
                Locomotive.getDate(startDate), 
                this.getLocomotives().stream().mapToLong(loco -> loco.getRuntimeOnDay(Locomotive.getDate(date))).sum()
            );
            
            startDate -= 86400000;
        }
        
        return stats;
    }
    
    /**
     * Gets the number of locomotives run daily over the number of days specified from the current date
     * @param days
     * @param offset
     * @return 
     */
    @Override
    public TreeMap<String, Integer> getDailyCountStats(int days, long offset)
    {
        long startDate = System.currentTimeMillis() - (offset * 86400000);
        
        TreeMap stats = new TreeMap<>(Comparator.reverseOrder());
        
        for (int i = 0; i < Math.abs(days); i++)
        {
            final long date = startDate;
            
            stats.put(
                Locomotive.getDate(startDate), 
                this.getLocomotives().stream().mapToInt(loco -> loco.getRuntimeOnDay(Locomotive.getDate(date)) > 0 ? 1 : 0).sum()
            );
            
            startDate -= 86400000;
        }
        
        return stats;
    }
    
    /**
     * Gets the total number of unique locomotives over the number of days specified from the current date
     * @param days
     * @param offset
     * @return 
     */
    @Override
    public int getTotalLocStats(int days, long offset)
    {
        long startDate = System.currentTimeMillis() - (offset * 86400000);
        
        Set locs = new HashSet<>();
        
        for (int i = 0; i < Math.abs(days); i++)
        {            
            for (Locomotive l : this.getLocomotives())
            {
                if (l.getRuntimeOnDay(Locomotive.getDate(startDate)) > 0)
                {
                    locs.add(l);
                }
            }
            
            startDate -= 86400000;
        }
        
        return locs.size();
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
        
        /* This is no longer needed now that we are allowing conditional routes during operation
        // Sanity check - in case accessories changed, etc.
        if (this.autoLayout != null)
        {
            this.autoLayout.invalidate();
            
            if (this.autoLayout.isAutoRunning())
            {
                this.autoLayout.stopLocomotives();
            }
            
            this.log("Invalidating auto layout to avoid state issues.  Please reload JSON.");
        }*/
                
        // Fetch Central Station databases
        try
        {            
            // Is this a CS2 or CS3?
            try
            {
                this.isCS3 = CS2File.isCS3(CS2File.getDeviceInfoURL(NetworkInterface.getIP()));
                this.log("Station type detection result: " + (this.isCS3 ? "CS3" : "CS2"));
            }
            catch (Exception e)
            {
                this.log("Station type detection error: " + e.toString());
            }
                                       
            // Import layout
            String overrideLayoutPath = "";
            
            try
            {
                overrideLayoutPath = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            }
            catch (Exception e)
            {
                this.log("Error loading user preferences; try re-running as admin.");
                
                this.log(e);
            }
                
            if (!"".equals(overrideLayoutPath) && TrainControlUI.getPrefs() != null)
            {
                fileParser.setLayoutDataLoc("file:///" + overrideLayoutPath + "/");
                
                this.log("Loading static layout files from: " + overrideLayoutPath);
                
                if (debug)
                {
                    this.log("This path should contain a 'config' folder with the same 'gleisbild' folder structure as on the CS2.");
                }
                
                try
                {
                    this.clearLayouts();
                    
                    syncLayouts();
                }
                catch (Exception e)
                {
                    this.log(e);
                           
                    this.log("Error, reverting to default layout source." + (!debug ? " Enable debug mode for details." : ""));
                    TrainControlUI.getPrefs().put(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
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
            
            // Import locomotives
            List<MarklinRoute> parsedRoutes;
            
            if (this.isCS3)
            {
                parsedRoutes = fileParser.parseRoutesCS3();
            }
            else
            {
                parsedRoutes = fileParser.parseRoutes();
            }
            
            // Unlock all routes in case they have been deleted
            for (MarklinRoute r : this.getRoutes())
            {
                r.setLocked(false);
            }
            
            // Import routes
            for (MarklinRoute r : parsedRoutes)
            {
                // Other existing route with same name but different ID
                if (this.routeDB.hasName(r.getName()) && r.getId() != this.routeDB.getByName(r.getName()).getId())
                {
                    this.log("Deleting old route (duplicate name): " + r.getName());

                    this.deleteRoute(r.getName());
                }
                
                // Delete route if it has changed
                if (this.routeDB.hasId(r.getId()) 
                        && (!r.getRoute().equals(this.routeDB.getById(r.getId()).getRoute()) 
                            || r.getS88() != this.routeDB.getById(r.getId()).getS88()
                            || r.getTriggerType() != this.routeDB.getById(r.getId()).getTriggerType()
                            || !Objects.equals(r.getConditions(), this.routeDB.getById(r.getId()).getConditions())
                        ) 
                )
                {   
                    this.log("Deleting old route (duplicate ID): " + this.routeDB.getById(r.getId()).getName());

                    this.deleteRoute(this.routeDB.getById(r.getId()).getName());
                }
                
                if (!this.routeDB.hasId(r.getId()))
                {                    
                    newRoute(r);                    
                    this.log("Added route " + r.getName());
                    num++;
                }
                
                // Routes from the Central Station are not editable
                if (this.routeDB.getById(r.getId()) != null)
                {
                    this.routeDB.getById(r.getId()).setLocked(true);
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
                    if (this.locDB.hasName(l.getName()))
                    {
                        // Show message that we did not sync a loc with a duplicate name
                        this.log("Did not import locomotive " + l.getName() + " from Central Station because a different locomotive with the same name exists in TrainControl.");
                    }
                    else
                    {
                        this.log("Added " + l.getDecoderTypeLabel() + " locomotive " + l.getName() 
                            + " with address " 
                            + l.getAddress() + " ("
                            + Conversion.intToHex(l.getIntUID()) + ")"
                            + " from Central Station"
                        );

                        newLocomotive(l.getName(), l.getAddress(), l.getDecoderType(), l.getFunctionTypes(), l.getFunctionTriggerTypes());
                        num++;
                    }
                }
                
                // We already have this locomotive, with the same decoder type, but different address.  Update the address and UID in database
                if (this.locDB.getByName(l.getName()) != null 
                    && this.locDB.getByName(l.getName()).getAddress() != l.getAddress()
                    && this.locDB.getByName(l.getName()).getDecoderType() == l.getDecoderType()
                )
                {
                    String oldAddr = this.getLocAddress(l.getName());
                    this.locDB.getByName(l.getName()).setAddress(l.getAddress(), l.getDecoderType());
                    
                    // Update DB entry
                    MarklinLocomotive existingLoc = this.locDB.getByName(l.getName());
                    this.locDB.delete(l.getName());
                    this.locDB.add(existingLoc, existingLoc.getName(), existingLoc.getUID());
                    
                    this.log("Updated address of " + existingLoc.getName() + " from " + oldAddr + " to " + this.getLocAddress(existingLoc.getName()));
                }
                
                // Update function types if they have changed
                if (this.locDB.hasId(l.getUID()) &&
                        (!Arrays.equals(this.locDB.getById(l.getUID()).getFunctionTypes(), l.getFunctionTypes())
                        || !Arrays.equals(this.locDB.getById(l.getUID()).getFunctionTriggerTypes(), l.getFunctionTriggerTypes()))
                )
                {
                    if (this.locDB.getById(l.getUID()).isCustomFunctions())
                    {
                        this.log("Function types for " + l.getName() + " do not match Central Station; this will be ignored because the locomotive was customized via the UI.");
                    }
                    else
                    {
                        this.locDB.getById(l.getUID()).setFunctionTypes(l.getFunctionTypes(), l.getFunctionTriggerTypes());

                        this.log("Updated function types for " + l.getName());
                    }
                }
                              
                // Set current locomotive icon if a remote icon is available, and a local icon is not set
                if (this.locDB.getById(l.getUID()) != null && l.getImageURL() != null && this.locDB.getById(l.getUID()).getLocalImageURL() == null)
                {
                    this.locDB.getById(l.getUID()).setImageURL(l.getImageURL());                         
                }
                
                // Set multi unit info
                if (this.locDB.getById(l.getUID()) != null)
                {
                    this.locDB.getById(l.getUID()).setCentralStationMultiUnitLocomotives(l.getCentralStationMultiUnitLocomotiveNames());
                }
            }
        }
        catch (Exception e)
        {
             this.log("Failed to sync locomotive DB.");
             this.log(e);
             
             return -1;
        }
        
        this.rebuildLocIdCache();
                
        this.log("Sync complete.");
        
        return num;
    }
    
    /**
     * Deletes the current layout from the model
     */
    @Override
    public void clearLayouts()
    {
        for (String name : this.layoutDB.getItemNames())
        {
            this.layoutDB.delete(name);
        }
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
     * @param backup
     */
    @Override
    public void saveState(boolean backup)
    {
        String prefix = backup ? ("backup" + Conversion.convertSecondsToDatetime(System.currentTimeMillis()).replace(':', '-').replace(' ', '_')) : "";
        
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
                new FileOutputStream(prefix + MarklinControlStation.DATA_FILE_NAME));

            // Write object out to disk
            obj_out.writeObject(l);

            this.log("Saving database state to: " + new File(prefix + MarklinControlStation.DATA_FILE_NAME).getAbsolutePath());
        } 
        catch (IOException iOException)
        {
            this.log("Could not save database. " + iOException.getMessage());
        }
    }
    
    /**
     * Because v2.3.2 changed package names, use this to handle class resolution
     */
    private class CustomObjectInputStream extends ObjectInputStream
    {
        public CustomObjectInputStream(InputStream in) throws IOException
        {
            super(in);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException
        {
            String name = desc.getName();
            if ((name.contains("base.") || name.contains("marklin.")) && !name.contains("org.traincontrol"))
            {
                name = "org.traincontrol." + name;
            }
            return Class.forName(name);
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
            ObjectInputStream obj_in = new CustomObjectInputStream(
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
        catch (IOException iex)
        {
            if (debug)
            {
                this.log(iex.toString());
                this.log(iex);
            }
            
            this.log("No compatible data file found, "
                    + "DB initializing with default data");
            
        } 
        catch (ClassNotFoundException cex)
        {
            this.log("Bad data file for DB");    
            
            if (debug)
            {
                this.log(cex);
            }
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
     * @param conditions
     */
    @Override
    public final void editRoute(String name, String newName, List<RouteCommand> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled,
            NodeExpression conditions)
    {
        Integer id = this.routeDB.getByName(name).getId();
        
        // Disable the route so that the s88 condition stops firing
        this.routeDB.getByName(name).disable();
        
        this.deleteRoute(name);
        
        this.newRoute(newName.trim(), id, route, s88, s88Trigger, routeEnabled, conditions);
    }
    
    /**
     * Checks the locomotive database for duplicate non-MFX addresses
     * @return 
     */
    @Override
    public Map<Integer, Set<MarklinLocomotive>> getDuplicateLocAddresses()
    {
        Map<Integer, Set<MarklinLocomotive>> locs = new HashMap<>();
                
        for (MarklinLocomotive l : this.locDB.getItems())
        {
            if (l.getDecoderType() != MarklinLocomotive.decoderType.MFX)
            {        
                if (!locs.containsKey(l.getAddress()))
                {
                    locs.put(l.getAddress(), new HashSet<>());
                }

                locs.get(l.getAddress()).add(l);
            }            
        }
          
        locs.keySet().removeIf(key -> locs.get(key).size() == 1);
                
        return locs;
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
    
    @Override
    public MarklinRoute getRoute(int id)
    {
        return this.routeDB.getById(id);
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
     * @param conditions
     * @return  
     */
    public final boolean newRoute(String name, int id, List<RouteCommand> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled,
            NodeExpression conditions)
    {
        name = name.trim();
        
        if (!this.routeDB.hasId(id) && !this.routeDB.hasName(name))
        {
            this.routeDB.add(new MarklinRoute(this, name, id, route, s88, s88Trigger, routeEnabled, conditions), name, id);    
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
     * @param conditions 
     * @return creation status
     */
    @Override
    public final boolean newRoute(String name, List<RouteCommand> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled,
        NodeExpression conditions)
    {
        int newId = ROUTE_STARTING_ID;
        
        if (this.routeDB.hasId(newId))
        {
            newId = Collections.max(this.routeDB.getItemIds()) + 1;
        }
        
        name = name.trim();
        
        if (!this.routeDB.hasName(name))
        {
            this.routeDB.add(new MarklinRoute(this, name, newId, route, s88, s88Trigger, routeEnabled, conditions), name, newId);  
                        
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
     * Creates a new signal (with the actuation count from the existing accessory, otherwise with 0 actuations)
     * @param address - the logical address (1 more than mm2 address)
     * @param decoderType
     * @param state
     * @return 
    */
    @Override
    public final MarklinAccessory newSignal(int address, Accessory.accessoryDecoderType decoderType, boolean state)
    {        
        return newAccessory(address, address - 1, Accessory.accessoryType.SIGNAL, decoderType, state);
    }
    
    /**
     * Creates a new switch (with the actuation count from the existing accessory, otherwise with 0 actuations)
     * @param address - the logical address (1 more than mm2 address)
     * @param decoderType
     * @param state
     * @return 
     */
    @Override
    public final MarklinAccessory newSwitch(int address, Accessory.accessoryDecoderType decoderType, boolean state)
    {
        return newAccessory(address, address - 1, Accessory.accessoryType.SWITCH, decoderType, state);
    }
    
    /**
     * Adds a new feedback based on a network message
     * @param id
     * @param message
     * @return 
     */
    public final MarklinFeedback newFeedback(int id, CS2Message message)
    {
        MarklinFeedback newFb = new MarklinFeedback(this, id, message);
                
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
     * Sets feedback state for simulation purposes
     * @param name (the feedback module number)
     * @param state
     * @return 
     */
    @Override
    public final boolean setFeedbackState(String name, boolean state) 
    {
        MarklinFeedback fb = this.feedbackDB.getByName(name);
        
        if (null != fb)
        {
            fb.setState(state);
                        
            return true;
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
     * Fetches the number of CAN messages processed so far
     * @return 
     */
    @Override
    public int getNumMessagesProcessed()
    {
        return this.numMessagesProcessed;
    }
        
    /**
     * Receives a network messages from the CS2 for interpretation
     * @param message
     */
    @Override
    public void receiveMessage(CS2Message message)
    {
        if (message == null) return;
        
        synchronized (this)
        {
            // CS3 seems to send respones packets twice.  Ignore the second.
            if (lastPacket != null && 
                    (message.isAccessoryCommand() || message.isLocCommand() || message.isFeedbackCommand()) && 
                    message.equals(lastPacket)
            )
            {
                if (this.debug && DEBUG_LOG_NETWORK)
                {
                    this.log("Skipping duplicate packet " + message.toString());
                }
                
                return;
            }
        
            numMessagesProcessed +=1;
            
            // Prints out each message
            if (this.debug && DEBUG_LOG_NETWORK)
            {
                this.log(numMessagesProcessed + " " + message.toString());
            }
            
            lastPacket = message;
        }
                
        // Send the message to the appropriate listener
        if (message.isFeedbackCommand())
        {
            this.feedbackMessageProcessor.submit(new Thread(() ->
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
            }));
        }
        // Only worry about the message if it's a response
        else if (message.isLocCommand() && message.getResponse())
        {            
            this.locMessageProcessor.submit(new Thread(() ->
            {
                Integer id = message.extractUID();

                List<String> locs = this.locIdCache.get(id);

                if (locs != null)
                {
                    if (!locs.isEmpty())
                    {
                        List<Locomotive> locList = new ArrayList<>();

                        for (String l : locs)
                        {
                            locList.add(this.locDB.getById(l));
                            ((MarklinLocomotive) locList.get(locList.size() - 1)).parseMessage(message);
                        }

                        if (this.view != null)
                        {
                            new Thread(() ->
                            {
                                 this.view.repaintLoc(false, locList);
                            }).start();    
                        }
                    }
                    else
                    {
                        this.log("Unknown locomotive received command: " 
                            + MarklinLocomotive.addressFromUID(id));
                    }
                }
            }));
        }
        else if (message.isAccessoryCommand() && message.getResponse())
        {
            this.locMessageProcessor.submit(new Thread(() ->
            {
                int id = message.extractUID();

                if (this.accDB.hasId(id))
                {
                    this.accDB.getById(id).parseMessage(message);

                    if (this.view != null)
                    {
                        new Thread(() -> 
                        {
                            this.view.repaintSwitch(this.accDB.getById(id).getAddress() + 1, this.accDB.getById(id).getDecoderType());
                            //this.view.repaintSwitches();
                        }).start();
                    } 
                }
            }));
        }
        else if (message.isSysCommand() && 
           (message.getSubCommand() == CS2Message.CMD_SYSSUB_GO || message.getSubCommand() == CS2Message.CMD_SYSSUB_STOP)
        )
        {
            this.locMessageProcessor.submit(new Thread(() ->
            {
                if (message.getSubCommand() == CS2Message.CMD_SYSSUB_GO)
                {
                    this.powerState = true;

                    // For correctly tracking locomotive stats
                    for (MarklinLocomotive l : this.locDB.getItems())
                    {
                        l.notifyOfPowerStateChange(true);
                    }

                    if (this.view != null) this.view.updatePowerState();
                    this.log("Power On");
                }
                else if (message.getSubCommand() == CS2Message.CMD_SYSSUB_STOP)
                {
                    this.powerState = false;

                    // For correctly tracking locomotive stats
                    for (MarklinLocomotive l : this.locDB.getItems())
                    {
                        l.notifyOfPowerStateChange(false);   
                    }

                    if (this.view != null) this.view.updatePowerState();
                    this.log("Power Off");
                }
            }));
        }
        else if (message.isPingCommand() && message.getResponse())
        {
            this.systemMessageProcessor.submit(new Thread(() ->
            {
                // Track latency
                if (this.pingStart > 0)
                {
                    this.lastLatency = ((double) (System.nanoTime() - this.pingStart)) / 1000000.0;
                    this.pingStart = 0;

                    if (this.view != null)
                    {
                        new Thread(() -> 
                        {
                            this.view.updateLatency(this.lastLatency);
                        }).start();
                    }
                }

                // Set the serial number if it is not already set
                if (this.UID == 0 && message.getLength() == 8)
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
            }));
        }
    }
        
    /**
     * Returns the last measured latency.  Should be preceded by a call to sendPing
     * @return 
     */
    public double getLastLatency()
    {
        return this.lastLatency;
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
            if (debug && MarklinControlStation.DEBUG_LOG_NETWORK)
            {
                this.log("Network transmission disabled\n" + m.toString());

                if (DEBUG_SIMULATE_PACKETS)
                {
                    if (this.locIdCache == null) rebuildLocIdCache();

                    this.receiveMessage(new CS2Message(
                            m.getCommand(), 
                            m.getHash(), true, m.getData()));
                }
            }
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
    
    @Override
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
            
            log.info(message);

            this.lastMessage = message;
        }
    }
    
    /**
     * Logs an exception
     * @param e 
     */
    @Override
    public final void log(Exception e)
    {
        if (this.view != null)
        {
            this.view.log(e.getMessage());    
        }

        log.warning(e.getClass().getName() + " " + e.getMessage());
        
        if (debug)
        {
            log.warning(String.join("\n", Arrays.stream(e.getStackTrace())
                    .map(StackTraceElement::toString)
                    .collect(Collectors.toList())));     
        }
    }
            
    /**
     * Pings the Central Station so that we can discover its UID
     * @param force - send the ping even if no response previously?
     */
    @Override
    public final void sendPing(boolean force)
    {        
        if (this.pingStart == 0 || force)
        {
            this.pingStart = System.nanoTime();
        
            this.exec(new CS2Message(
                CS2Message.CAN_CMD_PING,
                new byte[0]
            ));
        }
    }
    
    /**
     * Returns the timestamp (ms) of the last ping request
     * @return 
     */
    @Override
    public long getTimeSinceLastPing()
    {
        if (this.pingStart > 0)
        {
            return (System.nanoTime() - this.pingStart) / 1000000;
        }
        
        return 0;
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
        
        if (this.view != null) this.view.repaintLoc();
    }
    
    /**
     * Disables all active functions of all locomotives
     */
    @Override
    public void allFunctionsOff()
    {
        for (MarklinLocomotive l : this.locDB.getItems())
        {
            locFunctionsOff(l);
        }
    }
    
    /**
     * Turns off all active functions of the specified locomotive
     * @param loc 
     */
    @Override
    public void locFunctionsOff(MarklinLocomotive loc)
    {
        loc.functionsOff();
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
     * Adds a new locomotive to the internal database with no state except function types
     * @param name
     * @param address
     * @param type
     * @return 
     */
    private MarklinLocomotive newLocomotive(String name, int address, 
        MarklinLocomotive.decoderType type, int[] functionTypes, int[] functionTriggerTypes)
    {
        MarklinLocomotive newLoc = new MarklinLocomotive(this, address, type, name, functionTypes, functionTriggerTypes);
        
        this.locDB.add(newLoc, name, newLoc.getUID());
        
        return newLoc; 
    }
    
    /**
     * Adds a new locomotive with expanded state from saved data
     * @param c
     * @return 
     */
    private MarklinLocomotive newLocomotive(MarklinSimpleComponent c)
    {
        if (this.locDB.getByName(c.getName()) == null)
        {
            MarklinLocomotive newLoc = new MarklinLocomotive(this, c.getAddress(), c.getLocType(), c.getName(),
                    c.getState() ? MarklinLocomotive.locDirection.DIR_FORWARD : MarklinLocomotive.locDirection.DIR_BACKWARD,
                    c.getFunctions(), c.getFunctionTypes(), c.getFunctionTriggerTypes(), c.getPreferredFunctions(), c.getPreferredSpeed(),
                c.getDepartureFunction(), c.getArrivalFunction(), c.getReversible(), c.getTrainLength(), c.getHistoricalOperatingTime());

            newLoc.setLocalImageURL(c.getLocalImageURL());
            newLoc.setCustomFunctions(c.getCustomFunctions());
            newLoc.setLocalFunctionImageURLs(c.getLocalFunctionImageURLs());
            newLoc.setNotes(c.getLocNotes());
            newLoc.preSetLinkedLocomotives(c.getLinkedLocomotives()); // we need to call setLinkedLocomotives() once all locs are loaded
            newLoc.setCentralStationMultiUnitLocomotives(c.getCentralStationLinkedLocomotives());
            
            this.locDB.add(newLoc, newLoc.getName(), newLoc.getUID());
            
            return newLoc; 
        }
        else
        {
            // This is a sanity check for corrupt versions of the database from older versions of TrainControl
            this.log("Saved locomotive " + c.getName() + " is a duplicate.  Skipping.");
            return null;
        }
    }
    
    /**
     * Adds a new accessory to the internal database (with the acuation count from the existing accessory, otherwise with 0 actuations)
     * @param name
     * @param address - this should be 1 less than the logical address, i.e. Signal 1 has address 0
     * @param type
     * @param state
     * @return 
     */
    private MarklinAccessory newAccessory(int logicalAddress, int address, Accessory.accessoryType type, 
            Accessory.accessoryDecoderType decoderType, boolean state)
    {
        MarklinAccessory current = this.getAccessoryByAddress(address, decoderType);
                
        return newAccessory(logicalAddress, address, type, decoderType, state, current != null ? current.getNumActuations() : 0);
    }
    
    /**
     * Adds a new accessory to the internal database
     * @param name
     * @param address - this should be 1 less than the logical address, i.e. Signal 1 has address 0
     * @param type
     * @param state
     * @param numActuations
     * @return 
     */
    private MarklinAccessory newAccessory(int logicalAddress, int address, Accessory.accessoryType type, 
            Accessory.accessoryDecoderType decoderType,
            boolean state, int numActuations)
    {
        String name = MarklinAccessory.getNameWithProtocol(logicalAddress, type, decoderType);
                
        MarklinAccessory newAccessory = new MarklinAccessory(this, address, type, decoderType, name, state, numActuations);
        
        this.accDB.add(newAccessory, name, newAccessory.getUID());
        
        if (!newAccessory.isValidAddress())
        {
            this.log("Warning: accessory " + name + " has invalid address " + address);
        }
        
        return newAccessory;
    }

    /**
     * Returns the names of the locomotives that exist in the database
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
     * Changes the address / decoder type of a locomotive
     * @param locName
     * @param newAddress
     * @param newDecoderType
     * @throws Exception 
     */
    @Override
    synchronized public void changeLocAddress(String locName, int newAddress, decoderType newDecoderType) throws Exception
    {
        MarklinLocomotive l = this.locDB.getByName(locName);
        
        if (l == null) throw new Exception("Locomotive " + locName + " does not exist");
        
        if (!MarklinLocomotive.validateNewAddress(newDecoderType, newAddress))
        {
            throw new Exception("Address " + newAddress + " is outside of the allowed range.");
        }
        
        if (newDecoderType == MarklinLocomotive.decoderType.MULTI_UNIT && l.hasLinkedLocomotives())
        {
            /* l.preSetLinkedLocomotives(null);
            l.setLinkedLocomotives();
            this.log("Multi-unit locomotives have been unlinked from " + locName);*/
            
            throw new Exception("Cannot change decoder type to Central Station multi-unit when multi-unit locomotives have been linked in TrainControl.  Unlink them first.");
        }
        
        // Execute the change
        this.deleteLoc(l.getName());
        
        l.setAddress(newAddress, newDecoderType);
        
        this.locDB.add(l, l.getName(), l.getUID());
        
        this.rebuildLocIdCache();
        
        this.log("Changed address of " + l.getName() + " to " + newAddress + " (" + newDecoderType.name() + ")");
        
        // Ensure linked locomotives have valid addresses
        for (MarklinLocomotive other : getLocomotives())
        {
            if (other.hasLinkedLocomotives())
            {
                other.preSetLinkedLocomotives(other.getLinkedLocomotiveNames());
                other.setLinkedLocomotives();
            }
        }
    }
    
    /**
     * Checks if autonomous operation is currently engaged
     * @return 
     */
    @Override
    public boolean isAutonomyRunning()
    {
        return this.getAutoLayout() != null && this.getAutoLayout().isRunning();
    }
    
    /**
     * Checks if this locomotive is directly linked to any others as a multi-units
     * @param l
     * @return 
     */
    @Override
    public MarklinLocomotive isLocLinkedToOthers(MarklinLocomotive l)
    {
        for (MarklinLocomotive other : getLocomotives())
        {
            if (other.isLinkedTo(l))
            {
                return other;
            }
        }
        
        return null;
    }
    
    /**
     * Returns the names of the locomotives that exist in the database
     * @return 
     */
    @Override
    public final List<MarklinLocomotive> getLocomotives()
    {
        return this.locDB.getItems();
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
     * Returns a locomotive address as a string
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
            //address = Integer.toString(l.getAddress()) + " / 0x" + Integer.toHexString(l.getAddress());
            address = Integer.toString(l.getAddress());
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

    /**
     * Sets the state of the passed accessory by address.  
     * If the accessory does not exist, a new switch with that access is created
     * @param address greater than 1
     * @param state 
     * @param decoderType
     */
    @Override
    public void setAccessoryState(int address, Accessory.accessoryDecoderType decoderType, boolean state)
    {      
        // Sanity check
        if (address < 1)
        {
            this.log("Warning: Invalid address passed to setAccessoryState: " + address);   
            
            return;
        }
        
        MarklinAccessory a;
        
        if (this.accDB.hasId(MarklinAccessory.UIDfromAddress(address - 1, decoderType)))
        {
            a = this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1, decoderType));
        }
        else
        {
            a = this.newSwitch(address, decoderType, !state);
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
        
        this.routeDB.getByName(name).execRoute(false);
    }
    
    /**
     * Deletes the route with the specified name if it exists
     * @param name 
     */
    @Override
    public final void deleteRoute(String name)
    {
        // Make sure automatic execution gets disabled
        
        MarklinRoute r = this.routeDB.getByName(name);
        if (r != null)
        {
            r.disable();
            this.routeDB.delete(r.getName());
        }        
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
     * Changes the ID of an existing route.  The ID must not be in use
     * @param name
     * @param newId
     * @return 
     */
    @Override
    public boolean changeRouteId(String name, int newId)
    {
        MarklinRoute r = this.getRoute(name);
        
        // Route to clone does not exist, 
        if (r == null || this.routeDB.getById(newId) != null) return false;
        
        this.routeDB.delete(name);
        
        r.setId(newId);
        this.routeDB.add(r, name, newId);
        
        return true;
    }
    
    /**
     * Gets all existing routes
     * @return 
     */
    public List<MarklinRoute> getRoutes()
    {
        return this.routeDB.getItems();
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
    
    /**
     * Gets the state of the accessory with the specified address.If it does not exist, a new switch is created.
     * @param address greater than 1
     * @param decoderType
     * @return 
     */
    @Override
    public boolean getAccessoryState(int address, Accessory.accessoryDecoderType decoderType)
    { 
        // Sanity check
        if (address < 1)
        {
            this.log("Warning: Invalid address passed to getAccessoryState: " + address);
            
            return false;
        }
                
        // Get by name because UID in database != address
        if (this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1, decoderType)) != null)
        {
            return this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1, decoderType)).isSwitched();
        }
        else
        {
            this.newSwitch(address, decoderType, false);
        }
        
        return false;
    }
    
    /**
     * Returns an accessory based on its numerical address.If the address does not exist, a new switch is created.
     * @param address greater than 1
     * @param decoderType
     * @return 
     */
    @Override
    public MarklinAccessory getAccessoryByAddress(int address, Accessory.accessoryDecoderType decoderType)
    { 
        // Sanity check
        if (address < 1)
        {
            this.log("Warning: Invalid address passed to getAccessoryByAddress: " + address);
            
            return null;
        }
        
        // Get by name because UID in database != address
        if (this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1, decoderType)) != null)
        {
            return this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1, decoderType));
        }
        else
        {
            return this.newSwitch(address, decoderType, false);
        }        
    }
    
    /**
     * Returns if the power is on
     * @return 
     */
    @Override
    public boolean getPowerState()
    {
        return this.powerState;
    }
    
    /**
     * Gets all the available layout names
     * @return 
     */
    @Override
    public List<String> getLayoutList()
    {
        List<String> l = this.layoutDB.getItemNames();
        Collections.sort(l);
                
        return l;
    }

    /**
     * Fetches a single layout by name
     * @param name
     * @return 
     */
    @Override
    public MarklinLayout getLayout(String name)
    {
        return this.layoutDB.getByName(name);
    }

    /**
     * Returns whether debug mode is enabled
     * @return 
     */
    @Override
    public boolean isDebug()
    {
        return this.debug;
    }
    
    /**
     * Gets the UI reference
     * @return 
     */
    public View getGUI()
    {
        return this.view;
    }
    
    /**
     * Initialize with default values
     * @return
     * @throws UnknownHostException
     * @throws IOException 
     * @throws java.lang.InterruptedException 
     */
    public static MarklinControlStation init() throws UnknownHostException, IOException, InterruptedException
    {
        return init(null, false, true, true, false);
    }
    
    /**
     * Export all routes to a JSON string
     * @return 
     * @throws java.lang.IllegalAccessException
     * @throws java.lang.NoSuchFieldException
     */
    @Override
    public String exportRoutes() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, Exception
    {
        JSONObject outputObj = new JSONObject();
        
        JSONArray configObj = new JSONArray();
        
        for (MarklinRoute r : this.routeDB.getItems())
        {
            configObj.put(r.toJSON());
        }
        
        outputObj.put("routes", configObj);
        
        return outputObj.toString(4);
    }
    
    public List<MarklinRoute> parseRoutesFromJson(String json)
    {
        List<MarklinRoute> routes = new ArrayList<>();
        JSONObject jsonObject = new JSONObject(json);
        JSONArray dataArray = jsonObject.getJSONArray("routes");

        for (int i = 0; i < dataArray.length(); i++)
        {
            MarklinRoute route = MarklinRoute.fromJSON(dataArray.getJSONObject(i), this);
            routes.add(route);
        } 
        
        return routes;
    }
    
    /**
     * Replaces existing route data with that from a JSON file
     * @param json 
     */
    @Override
    public void importRoutes(String json)
    {
        List<MarklinRoute> routes = this.parseRoutesFromJson(json);
        
        this.log("Deleting existing routes...");
        for (MarklinRoute r : this.routeDB.getItems())
        {
            this.deleteRoute(r.getName());
        }
        
        // If all read successfully, remove existing routes and update route DB
        for (MarklinRoute route : routes)
        {
            this.log("Adding route: " + route.getName());
            this.newRoute(route);
        }
    }
        
    /**
     * Main initialization method
     * @param initIP
     * @param simulate
     * @param showUI
     * @param autoPowerOn
     * @param debug
     * @return
     * @throws UnknownHostException
     * @throws IOException 
     * @throws java.lang.InterruptedException 
     */
    public static MarklinControlStation init(String initIP, boolean simulate, boolean showUI, boolean autoPowerOn, boolean debug) throws UnknownHostException, IOException, InterruptedException
    {        
        System.out.println("TrainControl v" + MarklinControlStation.RAW_VERSION + " starting...");
        
        // User interface - only initialize if needed
        TrainControlUI ui = null;
        
        if (showUI) ui = new TrainControlUI();
        
        if (initIP == null)
        {
            try
            {
                initIP = TrainControlUI.getPrefs().get(TrainControlUI.IP_PREF, null);
            }
            catch (Exception ex)
            {
                System.out.println("Error accessing preferences.");
                
                if (debug)
                {
                    ex.printStackTrace();
                }
            }   
        }

        if (!simulate)
        {
            while (true)
            {
                try
                {
                    if (initIP == null)
                    {       
                        if (!GraphicsEnvironment.isHeadless())
                        {
                            System.out.println("Prompting for IP in pop-up...");
                            
                            JTextField ipField = new JTextField();

                            Object[] options = {"OK", "Cancel", "Auto-Detect"};
                            Object[] message = {
                                "Enter Central Station IP Address:",
                                ipField
                            };

                            JOptionPane optionPane = new JOptionPane(
                                message,
                                JOptionPane.PLAIN_MESSAGE,
                                JOptionPane.DEFAULT_OPTION,
                                null,
                                options,
                                options[0]
                            );

                            // To ensure the text field is focused
                            JDialog dialog = optionPane.createDialog("IP Address Input");
                            dialog.addWindowFocusListener(new WindowAdapter() 
                            {
                                @Override
                                public void windowGainedFocus(WindowEvent e)
                                {
                                    ipField.requestFocusInWindow();
                                }
                            });

                            dialog.setVisible(true);

                            int selectedOption = JOptionPane.CLOSED_OPTION;
                            Object selectedValue = optionPane.getValue();
                            for (int i = 0; i < options.length; i++)
                            {
                                if (options[i].equals(selectedValue))
                                {
                                    selectedOption = i;
                                    break;
                                }
                            }

                            switch (selectedOption)
                            {
                                case 0: // OK
                                    initIP = ipField.getText();
                                    
                                    if (initIP != null)
                                    {
                                        initIP = initIP.trim();
                                    }
                                    
                                    break;
                                case 1: // Cancel
                                    break;
                                case 2: // Auto-Detect
                                    System.out.println("Attempting to detect Central Station...");
                                    
                                    if (!CSDetect.hasLocalSubnets())
                                    {
                                        JOptionPane.showMessageDialog(null, "Auto-detection is not possible: no network interfaces found.  Enter IP manually or check firewall permissions.");
                                        continue;
                                    }
                                    
                                    initIP = CSDetect.detectCentralStation();

                                    if (initIP == null)
                                    {
                                        JOptionPane.showMessageDialog(null, "No Central Station detected.  Enter IP manually or try again.");
                                        continue;
                                    }
                                    
                                    break;
                                default:
                                    break;
                            }
                        }
                        else
                        {
                            try (Scanner scanner = new Scanner(System.in))
                            {
                                System.out.print("Enter Central Station IP Address: ");
                                initIP = scanner.next();
                            }
                        }
                        
                        if (initIP == null || "".equals(initIP))
                        {
                            System.out.println("No IP entered - shutting down.");

                            if (!GraphicsEnvironment.isHeadless())
                            {
                                JOptionPane.showMessageDialog(null, "No IP entered - shutting down.");
                            }
                            
                            System.exit(1);
                        }
                    }
                            
                    System.out.println("Connecting to " + initIP);

                    if (!CS2File.ping(initIP))
                    {
                        System.out.println("No response from " + initIP);

                        if (!GraphicsEnvironment.isHeadless())
                        {
                            JOptionPane.showMessageDialog(null, "No response from " + initIP);
                        }
                    }
                    else
                    {
                        // Verify that the device is actually a central station
                        if (!CSDetect.isCentralStation(initIP) && !CSDetect.isVNCAvailable(initIP))
                        {
                            System.out.println("Warning: the device at " + initIP + " does not appear to be a Central Station, or its web server is down/unreachable.");

                            if (!GraphicsEnvironment.isHeadless())
                            {
                                JOptionPane.showMessageDialog(null, "Warning: the device at " + initIP + " does not appear to be a Central Station, or its web server is down/unreachable.");
                            }
                        }
                        
                        try
                        {
                            TrainControlUI.getPrefs().put(TrainControlUI.IP_PREF, initIP);
                        }
                        catch (Exception ex)
                        {
                            System.out.println("Error updating preferences.");
                            
                            if (debug)
                            {
                                ex.printStackTrace();
                            }
                        } 
                        
                        break;
                    }
                }
                catch (HeadlessException e)
                {
                    System.out.println("Unable to prompt for IP; restart and specify the correct IP.");
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
        final MarklinControlStation model = 
            new MarklinControlStation(proxy, showUI ? ui : null, autoPowerOn, debug);

        final TrainControlUI theUI = ui;
        
        // Set model
        if (showUI && theUI != null)
        {
            model.log("Initializing UI");

            final CountDownLatch latch = new CountDownLatch(1);

            javax.swing.SwingUtilities.invokeLater(new Thread(() ->
            {
                try
                {
                    theUI.setViewListener(model, latch);
                }
                catch (IOException ex)
                {
                    model.log("Error initializing UI");
                    model.log(ex);

                    try
                    {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex1) {}
                }                
            }));

            latch.await();
            model.log("UI rendering...");

            try
            {
                javax.swing.SwingUtilities.invokeLater(new Thread(() ->
                {
                    theUI.display();
                    model.log("UI initialized.");
                }));
            }
            catch (Exception ex)
            {
                model.log("Fatal error initializing UI");
                model.log(ex);
                System.exit(0);
            }
        }
                        
        // Start execution
        proxy.setModel(model);
        
        // Connection failed - ask for IP on next run
        if (!model.getNetworkCommState())
        {
            TrainControlUI.getPrefs().remove(TrainControlUI.IP_PREF);
        }
        
        // Make the model think that the power is on
        if (simulate)
        {
            model.powerState = true;
        }
                                    
        return model;
    }
}
