package org.traincontrol.marklin;

import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.LayoutDiagram;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import org.traincontrol.base.Locomotive.decoderType;
import org.traincontrol.base.NodeExpression;
import org.traincontrol.base.RemoteDeviceCollection;
import org.traincontrol.base.RenameProposals;
import org.traincontrol.base.Route;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.gui.TrainControlUI;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.CSDetect;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.model.ModelListener;
import org.traincontrol.model.View;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;
import static org.traincontrol.util.Util.escapeCsv;

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
    // Version number
    public static final String RAW_VERSION = "3.0.0";
        
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

    /**
     * Where a sync reads from, when it must not be the configured Central Station.
     *
     * A test seam, in the same spirit as DEBUG_SIMULATE_PACKETS above.  The address is a bare string
     * that CS2File turns into "http://" + it, so a host:port pair pointed at a local server makes the
     * whole of syncWithCS2 - the fetch AND the database reconciliation that follows it - exercisable
     * without a Central Station on the network.
     *
     * The reconciliation is two hundred lines that decide what happens to every locomotive the user
     * owns, and until this existed none of it could be tested at all.  Null in every normal run.
     */
    private static volatile String TEST_CS2_ADDRESS = null;

    /**
     * The name of the field above, for a test that wants to set it.
     *
     * Private, with the test reaching it by reflection.  A public mutable static is a control-station
     * address that ANY code can redirect - the whole application talks to whatever it says - and
     * "only the tests set it" is a convention rather than a rule.  Reflection makes the test say out
     * loud that it is reaching past the front of the class, which is exactly what it is doing, and
     * leaves nothing for ordinary code to reach for by accident.
     *
     * Named here rather than spelled out in the test so that renaming the field breaks the build
     * instead of breaking the test at run time with a NoSuchFieldException.
     */
    public static final String TEST_ADDRESS_FIELD = "TEST_CS2_ADDRESS";

    /**
     * The address a sync should read from.
     */
    private String cs2Address()
    {
        return TEST_CS2_ADDRESS != null ? TEST_CS2_ADDRESS : NetworkInterface.getIP();
    }
        
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
    private final RemoteDeviceCollection<LayoutDiagram, String> layoutDB;
    
    // Mapping for int UID -> string UID
    // volatile: written by rebuildLocIdCache() (synchronized) but read unsynchronized on the
    // message-processor thread in receiveMessage().  The volatile store/load gives the reader a
    // happens-before edge so it always sees a fully-built map (see rebuildLocIdCache).
    private volatile HashMap<Integer, List<String>> locIdCache;

    // Network proxy reference
    private final NetworkProxy NetworkInterface;
    
    // File parser class
    private CS2File fileParser;

    // Serialises layout refreshes.  Deliberately not the station's own monitor - see refreshLayouts
    private final Object layoutRefreshLock = new Object();
    
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
    /**
     * How long an unanswered ping is left in flight before the keepalive sends another.
     *
     * Under the interval the interface pings on, so a lost response costs one cycle rather than the
     * session.
     */
    private static final long PING_RETRY_NS = 2000L * 1000000L;

    // Written by the ping timer thread and cleared by the message processor, read by both
    private volatile long pingStart;

    /**
     * When the current run of unanswered pings began, or 0 when the last one was answered.
     *
     * Separate from pingStart, which is when the LATEST ping went out, because the two questions have
     * different answers as soon as a ping is resent: how long the station has been silent, and how
     * long the outstanding ping has been in flight.  Measuring latency against the first of a run
     * would report a whole outage as the round trip of the packet that ended it - and that figure is
     * wired to the latency cutoff, so recovering from an outage would have cut the power.
     */
    private volatile long pingOutstandingSince;

    private double lastLatency;

    /**
     * Whether the locomotive database was there and would not read.
     *
     * False for a first launch, when there is nothing to lose.  True only when a file exists and the
     * load failed - a lock some other process was holding for a moment, a permissions change, a
     * truncation - and in that case the application carries on with an EMPTY database, which is the
     * dangerous part: the save on the way out would then write that emptiness over the real thing.
     * writeAtomically does not help, because a complete successful write of nothing is not a partial
     * write.
     */
    private boolean databaseLoadFailed = false;

    // Thread pools for network messages
    CS2Message lastPacket;

    /**
     * When lastPacket arrived.  The CS3's duplicate arrives back to back, so anything later than this
     * window is a second real command rather than an echo of the first.
     */
    private long lastPacketAt;

    private static final long DUPLICATE_WINDOW_NS = 250L * 1000000L;

    /**
     * How long the Central Station is allowed to acknowledge a power command.
     *
     * Two seconds, which is generous by three orders of magnitude: the echo comes back over a local
     * network in milliseconds or it does not come at all.  The number is large so that a busy station
     * or a loaded Wi-Fi never trips it, and small enough that a station which has been switched off
     * costs the user a two-second pause rather than a dead application.
     */
    public static final long POWER_STATE_TIMEOUT = 2000;
    // DAEMONS.  These serve inbound CAN messages and have no meaning without the application they
    // serve, but the default factory makes ordinary threads - so all three outlived every caller and
    // kept the JVM up.  The GUI hid it behind System.exit(0) and nothing else could.
    private static ExecutorService messageProcessor(final String name)
    {
        return Executors.newFixedThreadPool(1, runnable ->
        {
            Thread thread = new Thread(runnable, name);

            thread.setDaemon(true);

            return thread;
        });
    }

    private final ExecutorService locMessageProcessor = messageProcessor("cs2-loc-messages");
    private final ExecutorService feedbackMessageProcessor = messageProcessor("cs2-feedback-messages");
    private final ExecutorService systemMessageProcessor = messageProcessor("cs2-system-messages");
    
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
        
        this.logf("app.uititle", I18n.f("app.title", MarklinControlStation.RAW_VERSION));
        
        this.logf("log.restoring");

        // Restore state
        for (MarklinSimpleComponent c : this.restoreState(MarklinControlStation.DATA_FILE_NAME))
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
                    this.logf("acc.deletedInvalid", newAccessory.getName());
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
                
        this.logf("log.restored");
        
        if (syncWithCS2() >= 0)
        {
            this.logf("log.csDataImported", network.getIP());

            // Turn on network communication and turn on the power
            this.on = true;
            
            this.sendPing(false);
            
            if (autoPowerOn) this.go(); 
        }
        else
        {
            this.logf("log.csNotConnected");
        } 
        
        // Resolve linked locomotives now that we have loaded everything
        for (Locomotive l : this.getLocomotives())
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
     * Re-reads the track diagrams from wherever they are configured to come from - a local folder when
     * one is set, the Central Station otherwise - reverting to the Central Station if the local folder
     * cannot be read.
     *
     * Extracted from syncWithCS2 verbatim, so that refreshing diagrams no longer requires a full sync.
     * A full sync also re-imports every route and locomotive from the station, and for a local diagram
     * edit that is both slow and side-effecting: routes differing from the station's copy are deleted
     * and re-added, and locomotive addresses and function types are adopted from it.  None of that is
     * wanted after renaming a page.
     */
    private void syncLayoutsFromConfiguredSource() throws Exception
    {
        // The lock lives here rather than in refreshLayouts so every entrance inherits it.  It was
        // originally taken in refreshLayouts alone, which left syncWithCS2 reaching the same
        // clearLayouts() + syncLayouts() unguarded: a diagram-edit refresh on one background thread
        // could still interleave with a full sync on another, and layoutDB is backed by plain HashMaps.
        // Intrinsic locks are reentrant, so a caller already holding it is harmless.
        synchronized (this.layoutRefreshLock)
        {
            String overrideLayoutPath = "";
        
            try
            {
                overrideLayoutPath = TrainControlUI.getPrefs().get(TrainControlUI.LAYOUT_OVERRIDE_PATH_PREF, "");
            }
            catch (Exception e)
            {                
                this.logf("error.prefLoadAdminHint");
            
                this.log(e);
            }
            
            if (!"".equals(overrideLayoutPath) && TrainControlUI.getPrefs() != null)
            {
                fileParser.setLayoutDataLoc("file:///" + overrideLayoutPath + "/");
            
                this.logf("layout.loadingStaticFiles", overrideLayoutPath);
            
                if (debug)
                {
                    this.logf("layout.configFolderStructureHint");
                }
            
                try
                {
                    // syncLayouts clears for itself, once it has something to put back
                    syncLayouts();
                }
                catch (Exception e)
                {
                    this.log(e);
                   
                    this.logf("layout.revertToDefaultSource", !debug ? " Enable debug mode for details." : "");
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
        }
    }

    /**
     * Reloads the track diagrams and nothing else.
     *
     * For the UI to call after editing a diagram, or a route drawn on one.  syncWithCS2 was used for
     * this and does far more - see syncLayoutsFromConfiguredSource.
     *
     * Does nothing before the first sync, when there is no parser to read through yet.
     */
    @Override
    public final void refreshLayouts()
    {
        if (this.fileParser == null) return;

        // Serialisation lives inside syncLayoutsFromConfiguredSource, so that syncWithCS2 - which
        // reaches the same clear-and-repopulate - is covered by the same lock.
        try
        {
            this.syncLayoutsFromConfiguredSource();
        }
        catch (Exception e)
        {
            this.log(e);
        }
    }

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
                this.logf("layout.noDCC");
            }
        }

        // Fetch and parse FIRST, then swap.  The caller used to call clearLayouts() before this method,
        // so layoutDB sat empty for the whole of the fetch above and the parse below - HTTP requests
        // for every layout page, seconds rather than a repaint - and anything asking for a diagram in
        // that window was told there were none.  Nothing here is atomic, but the empty state now lasts
        // as long as a loop over already-parsed objects instead of as long as a network round trip.
        List<LayoutDiagram> parsed = fileParser.parseLayout(accs);

        this.clearLayouts();

        for (LayoutDiagram l : parsed)
        {
            this.layoutDB.add(l, l.getName(), l.getName());

            this.logf("layout.imported", l.getName());

            for (LayoutDiagramComponent c : l.getAll())
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
                            this.logf("layout.invalidAccessoryAddress", c.getTypeName(), c.getAddress(), c.getX(), c.getY());
                            continue;
                        }
                        
                        if (c.isSwitch() || c.isUncoupler())
                        {
                            newAccessory(c.getAddress(), newAddress, Accessory.accessoryType.SWITCH, c.getProtocol(), c.getPrimaryDriveState());

                            if (c.isThreeWay())
                            {
                                newAccessory(c.getAddress() + 1, newAddress + 1, Accessory.accessoryType.SWITCH, c.getProtocol(), c.getSecondaryDriveState());                                            
                            }
                        }
                        else if (c.isSignal())
                        {
                            newAccessory(c.getAddress(), newAddress, Accessory.accessoryType.SIGNAL, c.getProtocol(), c.getPrimaryDriveState());
                        }

                        this.logf("acc.adding", this.accDB.getById(targetAddress).getName());
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
                        this.logf("layout.routeButtonMissingRoute", c.getAddress(), c.getX(), c.getY());
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
                    this.logf("layout.pruningFeedbackMissingLayout", fb.getName());
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
        waitForPowerState(state, POWER_STATE_TIMEOUT);
    }

    /**
     * Delays until the power state matches, or until the Central Station has had long enough to answer.
     *
     * BOUNDED, unlike the waits a locomotive sits in for a sensor.  The difference is what is being
     * waited for: a train reaching a sensor is an event out on the railway that may legitimately be
     * minutes away, and there is nothing safe to do if it has not happened.  This is an
     * ACKNOWLEDGEMENT of a command TrainControl itself just sent, over a local network, and it either
     * comes back in milliseconds or it is not coming.
     *
     * Untimed, it could park a caller for the rest of the session.  The power state is written in
     * exactly one place - the inbound GO/STOP echo - so nothing local can ever release the wait, and
     * the socket is unconnected, which means a datagram sent to a Central Station that has been
     * switched off or dropped off the network SUCCEEDS and simply disappears.  No error is raised
     * anywhere; the caller just waits for ever.  The tile handler that does this runs on a single
     * shared thread, so one such wait stopped every track diagram tile in the application from
     * responding, silently, until TrainControl was restarted.
     *
     * @param state the state to wait for
     * @param timeoutMs how long to allow before giving up
     * @return whether the state was actually reached
     * @throws InterruptedException
     */
    public boolean waitForPowerState(boolean state, long timeoutMs) throws InterruptedException
    {
        long deadline = System.currentTimeMillis() + timeoutMs;

        synchronized(this)
        {
            while (this.getPowerState() != state)
            {
                long left = deadline - System.currentTimeMillis();

                if (left <= 0) return false;

                try
                {
                    this.wait(left);
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();

                    return this.getPowerState() == state;
                }
            }
        }

        return true;
    }
       
    @Override
    public boolean hasAutoLayout()
    {
        return this.autoLayout != null;
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
     * Forgets the automation graph entirely, so that nothing is loaded.
     *
     * The way back out of having autonomy running.  Everything else here either replaces one graph with
     * another or refuses - so a layout that had been given a configuration could never be returned to
     * having none, and the only way to stop autonomy being loaded was to close the application.
     *
     * Whatever is moving is stopped first: the graph about to be discarded is what the running threads
     * are driving from.
     */
    @Override
    public void clearAutoLayout()
    {
        if (this.autoLayout != null)
        {
            this.autoLayout.invalidate();
            this.autoLayout.stopLocomotives();
        }

        this.autoLayout = null;
    }

    /**
     * Parses JSON corresponding to a layout automation config file
     * Resets any existing automation
     * @param s
     */
    @Override
    public void parseAuto(String s)
    {        
        // What the OPERATOR chose, kept across the rebuild.
        //
        // Everything else here is a property of the configuration and is rightly replaced with it.
        // Timetable capture is not: it is a button the user pressed a moment ago, and it lives on the
        // Layout object because that is where the capture happens.
        //
        // So every rebuild silently turned it off. The setup rebuilds far more often than it used to -
        // applying a diagram edit, placing a locomotive, loading a configuration all come through here -
        // and the toggle button is not repainted from the layout at those moments, so it stayed lit
        // over a layout that was no longer capturing. Adam: "capture locomotive commands is capturing
        // neither manual locomotive commands nor full autonomy commands into the timetable."
        //
        // Read before the old layout is discarded, applied after the new one exists.
        boolean wasCapturing = this.autoLayout != null && this.autoLayout.isTimetableCapture();

        if (this.autoLayout != null)
        {
            this.autoLayout.invalidate();
            this.autoLayout.stopLocomotives();
        }
        
        this.autoLayout = Layout.fromJSON(s, this);

        if (this.autoLayout != null) this.autoLayout.setTimetableCapture(wasCapturing);

        this.applyAutonomyRouteActivations();
    }
    
    /**
     * Applies route on/off status definitions in autonomy configuration
     */
    @Override
    public void applyAutonomyRouteActivations()
    {
        // Handle auto layout route definitions
        if (this.autoLayout != null)
        {
            // Layout specifies settings for routes
            if (this.autoLayout.isActivateRoutes())
            {
                // Disable or enable routes
                for (MarklinRoute r : this.getRoutes())
                {
                    if (!this.autoLayout.getActivateRouteIDs().contains(r.getId()))
                    {
                        if (r.isEnabled())
                        {
                            r.disable();
                            
                            this.logf("route.autolayoutDisabledRoute", r.getId() + ". " + r.getName());
                        }
                    }
                    else
                    {
                        if (r.hasS88())
                        {
                            if (!r.isEnabled())
                            {
                                r.enable();
                                r.executeAutoRoute();
                                this.logf("route.autolayoutEnabledRoute", r.getId() + ". " + r.getName());
                            }
                        }
                        else
                        {
                            this.logf("route.ui.autolayoutErrorS88RequiredForAutoFire", r.getId());
                        }
                    }
                }
            }
        }
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
        // Stepped a calendar day at a time rather than by a fixed 86400000 ms.  A day is 23 or 25 hours
        // long across a DST transition, so a fixed step could land twice on one date - the TreeMap then
        // silently overwrote the earlier entry, losing a day and duplicating another - or skip one.
        LocalDate day = LocalDate.now().minusDays(offset);

        TreeMap stats = new TreeMap<>(Comparator.reverseOrder());

        // Snapshot the locomotive list once - it does not change across the day-by-day loop,
        // so there is no need to allocate a fresh copy on every iteration.
        List<Locomotive> locs = this.getLocomotives();

        for (int i = 0; i < Math.abs(days); i++)
        {
            // ISO_LOCAL_DATE is yyyy-MM-dd, the same key Locomotive.getDate produces
            final String key = day.format(DateTimeFormatter.ISO_LOCAL_DATE);

            stats.put(
                key,
                locs.stream().mapToLong(loco -> loco.getRuntimeOnDay(key)).sum()
            );

            day = day.minusDays(1);
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
        // Calendar days, not fixed 86400000 ms steps - see getDailyRuntimeStats
        LocalDate day = LocalDate.now().minusDays(offset);

        TreeMap stats = new TreeMap<>(Comparator.reverseOrder());

        // Snapshot the locomotive list once - it does not change across the day-by-day loop.
        List<Locomotive> locs = this.getLocomotives();

        for (int i = 0; i < Math.abs(days); i++)
        {
            final String key = day.format(DateTimeFormatter.ISO_LOCAL_DATE);

            stats.put(
                key,
                locs.stream().mapToInt(loco -> loco.getRuntimeOnDay(key) > 0 ? 1 : 0).sum()
            );

            day = day.minusDays(1);
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
        // Calendar days, not fixed 86400000 ms steps - see getDailyRuntimeStats.  Here a repeated date
        // would not have overwritten anything (the result is a Set), but a skipped one still dropped a
        // day's locomotives from the count.
        LocalDate day = LocalDate.now().minusDays(offset);

        Set locs = new HashSet<>();

        // Snapshot the locomotive list once - it does not change across the day-by-day loop.
        List<Locomotive> allLocs = this.getLocomotives();

        for (int i = 0; i < Math.abs(days); i++)
        {
            String key = day.format(DateTimeFormatter.ISO_LOCAL_DATE);

            for (Locomotive l : allLocs)
            {
                if (l.getRuntimeOnDay(key) > 0)
                {
                    locs.add(l);
                }
            }

            day = day.minusDays(1);
        }

        return locs.size();
    }
    
    /**
     * Checks the CS data and identifies locomotives with the same address that have a different name in TC
     * @return
     * @throws Exception 
     */
    @Override
    public List<String[]> getLocomotivesToRenameFromImport() throws Exception
    {
        return getRenameProposals().getProposals();
    }

    /**
     * The rename check proper: the applicable proposals, and how much was declined.
     *
     * The count matters to the caller because an empty proposal list is ambiguous - either nothing
     * needs renaming, or everything that did was refused - and those want different messages.
     *
     * @return
     * @throws java.lang.Exception
     */
    @Override
    public RenameProposals getRenameProposals() throws Exception
    {
        List<String[]> renameCandidates = new ArrayList<>();
        int refused = 0;
        List<MarklinLocomotive> parsedLocs;

        // Parse locomotives based on CS version
        if (this.isCS3)
        {
            parsedLocs = fileParser.parseLocomotivesCS3();
        }
        else
        {
            parsedLocs = fileParser.parseLocomotives();
        }

        // Indexed once.  This used to rebuild the entire locomotive list inside the loop, once per
        // parsed locomotive.
        Map<Integer, List<MarklinLocomotive>> byAddress = new HashMap<>();

        for (MarklinLocomotive existingLoc : this.locDB.getItems())
        {
            // Consist heads are never proposed for renaming
            if (existingLoc.hasLinkedLocomotives()) continue;

            if (!byAddress.containsKey(existingLoc.getIntUID()))
            {
                byAddress.put(existingLoc.getIntUID(), new ArrayList<>());
            }

            byAddress.get(existingLoc.getIntUID()).add(existingLoc);
        }

        // The same ambiguity exists on the Central Station side, and has to be refused for the same
        // reason.  Two parsed locomotives at one address both match the single local locomotive there,
        // so the list gets two proposals with one source.  The consumer precomputes the list and acts
        // on it in order, so once the first rename is applied the second names a locomotive that no
        // longer exists - and if some unrelated local locomotive happens to hold the second target
        // name, the flow deletes it and then renames nothing, because its source is gone.  A delete
        // with no compensating rename, after two dialogs that both described a rename.
        Map<Integer, Integer> parsedCountByAddress = new HashMap<>();

        for (MarklinLocomotive l : parsedLocs)
        {
            parsedCountByAddress.merge(l.getIntUID(), 1, Integer::sum);
        }

        for (MarklinLocomotive l : parsedLocs)
        {
            List<MarklinLocomotive> matches = byAddress.get(l.getIntUID());

            if (matches == null) continue;

            // Checked after the match lookup so a Central Station duplicate with no local counterpart
            // stays silent - there is nothing to propose for it either way
            if (parsedCountByAddress.get(l.getIntUID()) > 1)
            {
                this.logf("loc.renameAmbiguousCentralStationDuplicate", l.getName(), parsedCountByAddress.get(l.getIntUID()));
                refused++;
                continue;
            }

            // Duplicates at one address are legitimate - the database is keyed by name AND address, so
            // two locomotives can share an address - but the Central Station has only one name for it.
            // Proposing that name for each of them is incoherent, and acting on the proposals in order
            // destroyed data: the rename flow deletes whatever already holds the target name, so the
            // second rename deleted the locomotive the first had just renamed.
            if (matches.size() > 1)
            {
                this.logf("loc.renameAmbiguousDuplicateAddress", l.getName(), matches.size());
                refused++;
                continue;
            }

            MarklinLocomotive existingLoc = matches.get(0);

            // If the name has changed, add to rename list
            if (!existingLoc.getName().equals(l.getName()))
            {
                renameCandidates.add(new String[] {existingLoc.getName(), l.getName()});
            }
        }

        List<String[]> ordered = orderRenameProposals(renameCandidates);

        // Whatever ordering dropped was a cycle
        refused += renameCandidates.size() - ordered.size();

        return new RenameProposals(ordered, refused);
    }

    /**
     * Orders rename proposals so each one is applied only once its target name is free, and drops any
     * that form a cycle.
     *
     * Both ambiguity refusals above are about ONE address.  What is left after them still interacts
     * across addresses, through the names: sources are unique and Central Station names are unique, so
     * this list is a partial injective mapping - a permutation - and permutations contain cycles.
     *
     * The consumer computes this list once and then applies it one entry at a time against a database
     * each entry mutates, deleting whatever already holds a target name so that renameLoc's
     * "target must be free" guard can pass.  That makes the order load-bearing:
     *
     *  - A CHAIN (the Central Station renamed X to C and the old C to D) is safe in one order and
     *    destroys the middle locomotive in the other.  Emitting "C to D" first frees the name, and the
     *    second rename then needs no deletion at all.
     *  - A SWAP (X and C exchange names) is safe in NO order.  Applying either entry deletes the
     *    locomotive the other entry was about to rescue - two confirmations, both describing renames,
     *    and the result is one deletion and no rename.  Unwinding it needs a temporary name, which this
     *    flow has no way to offer, so the cycle is refused instead.
     *
     * After this, a deletion in the consumer can only be a name held by something with no pending
     * rename of its own - the stale-duplicate case that step was written for.
     *
     * @param proposals
     * @return
     */
    private List<String[]> orderRenameProposals(List<String[]> proposals)
    {
        List<String[]> remaining = new LinkedList<>(proposals);
        List<String[]> ordered = new ArrayList<>();

        boolean progress = true;

        while (progress)
        {
            progress = false;

            // Iterated over a copy so the live list can be modified.  A proposal can never block
            // itself: generation only emits pairs whose two names differ.
            for (String[] proposal : new ArrayList<>(remaining))
            {
                if (!isPendingRenameSource(proposal[1], remaining))
                {
                    ordered.add(proposal);
                    remaining.remove(proposal);
                    progress = true;
                }
            }
        }

        // Nothing could be emitted, so every target left is still some other entry's source: a cycle
        for (String[] proposal : remaining)
        {
            this.logf("loc.renameCycleRefused", proposal[0], proposal[1]);
        }

        return ordered;
    }

    /**
     * Whether a locomotive by this name is still waiting to be renamed away
     * @param name
     * @param remaining
     * @return
     */
    private static boolean isPendingRenameSource(String name, List<String[]> remaining)
    {
        for (String[] proposal : remaining)
        {
            if (proposal[0].equals(name)) return true;
        }

        return false;
    }
    
    /**
     * Synchronizes CS2 state
     * @return 
     */
    @Override
    public final int syncWithCS2()
    {        
        // Read remote config files
        this.fileParser = new CS2File(cs2Address(), this);
             
        this.logf("log.csDBSyncStarting");

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
                this.isCS3 = CS2File.isCS3(CS2File.getDeviceInfoURL(cs2Address()));
                this.logf("log.csTypeDetectionResult", (this.isCS3 ? "CS3" : "CS2"));
            }
            catch (Exception e)
            {
                this.logf("log.csTypeDetectionError", e.toString());
            }
                                       
            // Import layout
            this.syncLayoutsFromConfiguredSource();
            
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
                    this.logf("route.deletingDuplicateName", r.getName());
                    
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
                    this.logf("route.deletingDuplicateId", this.routeDB.getById(r.getId()).getName());
                    this.deleteRoute(this.routeDB.getById(r.getId()).getName());
                }
                
                if (!this.routeDB.hasId(r.getId()))
                {
                    // Only report and count the route if it was actually added
                    if (newRoute(r))
                    {
                        this.logf("route.added", r.getName());
                        num++;
                    }
                    else
                    {
                        this.logf("route.notAdded", r.getName());
                    }
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
                        this.logf("loc.importSkippedDuplicateName", l.getName());
                    }
                    else
                    {
                        this.logf("loc.addedFromCentralStation",
                            l.getDecoderTypeLabel(),
                            l.getName(),
                            l.getAddress(),
                            Conversion.intToHex(l.getIntUID()));

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
                    // Deferred while anything is running.  setAddress changes which decoder this
                    // locomotive commands, so applying it mid-run sends every subsequent speed and
                    // function command to a different engine while the graph goes on tracking this one
                    // - and the train already moving keeps moving, now unaddressable.  A rename and a
                    // manual address change are both refused while running; a sync had no such guard
                    // and is triggered automatically from a dozen places, so the check belongs here.
                    //
                    // This used to cite hash drift as the reason as well.  That reason is gone: a
                    // locomotive hashes by identity, so no mutation moves it out of the collections
                    // holding it - see the note on MarklinLocomotive.hashCode.  Do not re-add repair
                    // machinery here to satisfy it.
                    if (this.isAutonomyRunning())
                    {
                        this.logf("loc.addressUpdateDeferredWhileRunning", l.getName());
                    }
                    else
                    {
                        String oldAddr = this.getLocAddress(l.getName());
                        this.locDB.getByName(l.getName()).setAddress(l.getAddress(), l.getDecoderType());

                        // Update DB entry
                        MarklinLocomotive existingLoc = this.locDB.getByName(l.getName());
                        this.locDB.delete(l.getName());
                        this.locDB.add(existingLoc, existingLoc.getName(), existingLoc.getUID());

                        this.logf("loc.addressUpdated",
                            existingLoc.getName(),
                            oldAddr,
                            this.getLocAddress(existingLoc.getName()));

                        // The same repair changeLocAddress performs, for the same reason
                        for (Locomotive other : getLocomotives())
                        {
                            if (other.hasLinkedLocomotives())
                            {
                                other.preSetLinkedLocomotives(other.getLinkedLocomotiveNames());
                                other.setLinkedLocomotives();
                            }
                        }
                    }
                }
                
                // Update function types if they have changed
                if (this.locDB.hasId(l.getUID()) &&
                        (!Arrays.equals(this.locDB.getById(l.getUID()).getFunctionTypes(), l.getFunctionTypes())
                        || !Arrays.equals(this.locDB.getById(l.getUID()).getFunctionTriggerTypes(), l.getFunctionTriggerTypes()))
                )
                {
                    if (this.locDB.getById(l.getUID()).isCustomFunctions())
                    {
                        this.logf("loc.functionTypesMismatchIgnoredUI", l.getName());
                    }
                    else
                    {
                        this.locDB.getById(l.getUID()).setFunctionTypes(l.getFunctionTypes(), l.getFunctionTriggerTypes());

                        this.logf("loc.functionTypesUpdated", l.getName());
                    }
                }
                              
                // Always adopt the remote icon.  This used to skip while a local override existed -
                // a guard protecting the override back when it LIVED in imageURL.  The override moved
                // to its own field with getImageURL falling back, so skipping only starved the
                // fallback: a custom icon restored from the database starts with no imageURL, the
                // guard kept it that way, and clearing the icon offline landed on nothing - the
                // UC-C12 scenario again, one restart later.
                if (this.locDB.getById(l.getUID()) != null && l.getImageURL() != null)
                {
                    this.locDB.getById(l.getUID()).setImageURL(l.getImageURL());                         
                }
                
                // Set multi unit info
                if (this.locDB.getById(l.getUID()) != null)
                {
                    this.locDB.getById(l.getUID()).setModelMultiUnitLocomotives(l.getModelMultiUnitLocomotiveNames());
                }
            }
        }
        catch (Exception e)
        {
            this.logf("loc.dbSyncFailed");
            this.log(e);
             
            return -1;
        }
        
        this.rebuildLocIdCache();
                
        this.logf("loc.syncCompleted");
        
        return num;
    }
    
    /**
     * Deletes the current layout from the model
     *
     * Takes layoutRefreshLock because this is a third entrance to the emptied-database state that
     * syncLayoutsFromConfiguredSource guards: "Switch to CS Layout" calls this directly, from the EDT,
     * before its own syncWithCS2().  Guarding only the two callers of that method left this one able to
     * delete from layoutDB while a background diagram-save refresh was repopulating it - plain HashMaps,
     * modified structurally from two threads.  The exclusion existed for free until FCR-B3 moved
     * refreshes off the EDT.
     *
     * Intrinsic locks are reentrant, so the callers already holding it are unaffected.
     */
    @Override
    public void clearLayouts()
    {
        synchronized (this.layoutRefreshLock)
        {
            for (String name : this.layoutDB.getItemNames())
            {
                this.layoutDB.delete(name);
            }
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
            this.logf("loc.syncing", name);
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
        
        // The database that would not load, kept before this one goes on top of it.
        //
        // One transient read failure at startup plus a normal exit used to destroy every locomotive
        // customization the user had ever made: the load failed silently, the application ran with an
        // empty database, and closing the window saved that empty database over the real one.  There
        // is no undo and the backups are manual.  So the old file is copied aside first, once, and
        // said out loud - the save still happens, because refusing it for ever would lose whatever the
        // session did instead.
        if (!backup && this.databaseLoadFailed)
        {
            this.databaseLoadFailed = false;

            try
            {
                File existing = new File(MarklinControlStation.DATA_FILE_NAME);

                if (existing.exists())
                {
                    File kept = new File(Util.getBackupPath("unreadable"
                        + Conversion.convertSecondsToDatetime(System.currentTimeMillis())
                            .replace(':', '-').replace(' ', '_')
                        + MarklinControlStation.DATA_FILE_NAME));

                    java.nio.file.Files.copy(existing.toPath(), kept.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                    this.logf("log.databaseUnreadableKept", kept.getAbsolutePath());
                }
            }
            catch (IOException | RuntimeException keepFailed)
            {
                this.log(keepFailed);
            }
        }

        // Backups go into a dedicated folder (falling back to the current directory if it can't be created)
        String path = backup
            ? Util.getBackupPath(prefix + MarklinControlStation.DATA_FILE_NAME)
            : (prefix + MarklinControlStation.DATA_FILE_NAME);

        // Staged through a sibling file and moved into place, so that dying part way through the write
        // leaves the previous database intact rather than a truncated one.  This is the only automatic
        // save of the locomotive database, and an unreadable one reads as a first launch - the next
        // sync then repopulates the locomotive list, so the lost customizations look mislaid rather
        // than destroyed.  try-with-resources inside still matters: without close() the final buffered
        // block never reaches the staging file, and a truncated file would be moved into place.
        try
        {
            Util.writeAtomically(new File(path), out ->
            {
                try (ObjectOutputStream obj_out = new ObjectOutputStream(out))
                {
                    // Write object out to disk
                    obj_out.writeObject(l);
                }
            });

            this.logf("log.savingDatabaseState", new File(path).getAbsolutePath());
        }
        catch (IOException iOException)
        {
            this.logf("log.databaseSaveFailed", iOException.getMessage());
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
            
            // 2.7.0 change
            // Handle moved enum: MarklinRoute$s88Triggers -> Route$s88Triggers
            if (name.equals("org.traincontrol.marklin.MarklinRoute$s88Triggers"))
            {
                return Route.s88Triggers.class;
            }
            
            if (name.equals("org.traincontrol.marklin.MarklinAccessory$accessoryDecoderType"))
            {
                return Accessory.accessoryDecoderType.class;
            }
            
            // 2.3.2 change
            if ((name.contains("base.") || name.contains("marklin.")) && !name.contains("org.traincontrol"))
            {
                name = "org.traincontrol." + name;
            }
            
            return Class.forName(name);
        }
        
        @Override
        protected ObjectStreamClass readClassDescriptor()
                throws IOException, ClassNotFoundException
        {
            ObjectStreamClass desc = super.readClassDescriptor();
            String name = desc.getName();

            // 2.7.0 change
            // Handle moved enum: MarklinRoute$s88Triggers -> Route$s88Triggers
            if (name.equals("org.traincontrol.marklin.MarklinRoute$s88Triggers"))
            {
                // Return the descriptor of the new enum class
                return ObjectStreamClass.lookup(Route.s88Triggers.class);
            }
            
            if (name.equals("org.traincontrol.marklin.MarklinAccessory$accessoryDecoderType"))
            {
                // Return the descriptor of the new enum class
                return ObjectStreamClass.lookup(Accessory.accessoryDecoderType.class);
            }

            return desc;
        }
    }

    /**
     * Whether the last restoreState found a database file it could not read.
     *
     * False for a first launch - nothing there is nothing to lose - and false after a load that
     * worked.  saveState reads it to decide whether the file it is about to replace is worth keeping
     * a copy of first.
     *
     * @return true if a database file existed and would not load
     */
    public boolean isDatabaseLoadFailed()
    {
        return this.databaseLoadFailed;
    }

    /**
     * Restores list of initialized components from a file
     * @param dataFile
     * @return 
     */
    public final List<MarklinSimpleComponent> restoreState(String dataFile)
    {
        List<MarklinSimpleComponent> instance = new LinkedList<>();

        // Cleared here rather than only set below, so a second load attempt that succeeds forgets a
        // first one that did not
        this.databaseLoadFailed = false;

        // try-with-resources ensures the stream is closed (avoids a file-handle leak on every load)
        try (ObjectInputStream obj_in = new CustomObjectInputStream(new FileInputStream(dataFile)))
        {
            // Read an object
            Object obj = obj_in.readObject();

            if (obj instanceof List)
            {
                // Cast object
                instance = (List<MarklinSimpleComponent>) obj;
            }

            this.logf("log.databaseLoadedFromFile");
        }
        catch (IOException iex)
        {
            // A file that is not there is a first launch.  A file that IS there and would not read is
            // something else entirely, and the difference decides whether the save on the way out is
            // allowed to replace it - see saveState.
            this.databaseLoadFailed = new File(dataFile).exists();

            if (debug)
            {
                this.log(iex.toString());
                this.log(iex);
            }
            
            this.logf("log.databaseInitDefault");       
        } 
        catch (ClassNotFoundException cex)
        {
            // Present but unreadable, exactly as above
            this.databaseLoadFailed = true;

            this.logf("log.databaseBadDataFile");   
            
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
    public final boolean editRoute(String name, String newName, List<RouteCommand> route, int s88, MarklinRoute.s88Triggers s88Trigger, boolean routeEnabled,
            NodeExpression conditions)
    {
        MarklinRoute existing = this.routeDB.getByName(name);

        if (existing == null)
        {
            this.logf("route.warningRouteNotExistCalledFrom", name, "editRoute");
            return false;
        }

        String trimmedNewName = newName.trim();

        // Checked before anything is deleted.  This method edits by delete-then-re-add, and newRoute
        // refuses a name that already belongs to another route - so a rename onto an existing name
        // used to delete the original and then decline to add it back, losing the route entirely.
        //
        // The only caller that can rename already checks this (RouteEditor), which is exactly why the
        // check belongs here too: the model should not depend on one dialog to protect its data.
        if (!name.equals(trimmedNewName) && this.routeDB.hasName(trimmedNewName))
        {
            this.logf("route.notAdded", trimmedNewName);
            return false;
        }

        Integer id = existing.getId();
        
        // Disable the route so that the s88 condition stops firing
        existing.disable();
        
        this.deleteRoute(name);
        
        if (!this.newRoute(trimmedNewName, id, route, s88, s88Trigger, routeEnabled, conditions))
        {
            this.logf("route.notAdded", trimmedNewName);
            return false;
        }
        
        // Let other routes know this has been renamed
        for (MarklinRoute r : this.getRoutes())
        {
            r.otherRouteRenamed(name, trimmedNewName);
        }

        return true;
    }
    
    /**
     * Checks the locomotive database for duplicate addresses
     * @return 
     */
    @Override
    public Map<Integer, Set<Locomotive>> getDuplicateLocAddresses()
    {
        Map<Integer, Set<Locomotive>> locs = getLocAddresses();

        locs.keySet().removeIf(key -> locs.get(key).size() == 1);

        return locs;
    }

    /**
     * Every locomotive address in the database, mapped to the locomotives using it.
     *
     * Separate from getDuplicateLocAddresses because the two answer different questions, and the
     * duplicate list was being used for both: it has every single-user address removed, so "is this
     * address in use" came back false for an address with exactly one locomotive on it - which is the
     * only case the question is ever really asked about.
     *
     * @return
     */
    @Override
    public Map<Integer, Set<Locomotive>> getLocAddresses()
    {
        Map<Integer, Set<Locomotive>> locs = new HashMap<>();
                
        for (MarklinLocomotive l : this.locDB.getItems())
        {
            // Do include MFX, but we will want to group these separately in the UI
            // Two MFX entries on one address are worth reporting: the same physical locomotive can be
            // duplicated in the UI for convenience, or left behind by a stale sync, and both entries
            // then drive the same decoder.  Do not be tempted to filter MFX out here.
            //if (l.getDecoderType() != MarklinLocomotive.decoderType.MFX)
            //{
                if (!locs.containsKey(l.getAddress()))
                {
                    locs.put(l.getAddress(), new HashSet<>());
                }

                locs.get(l.getAddress()).add(l);
            //}
        }
          
                
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
        // Routes are indexed by name, so an unnamed route cannot be added
        if (r == null || r.getName() == null)
        {
            return false;
        }

        if (!this.routeDB.hasId(r.getId()) && !this.routeDB.hasName(r.getName().trim()))
        {
            this.routeDB.add(r, r.getName().trim(), r.getId());
            return true;
        }
        else
        {
            // Retire the monitor of the route we are refusing.  MarklinRoute's complete constructor
            // starts one as soon as the route is enabled and has an s88 - before the object has been
            // offered to any database - so a hand-edited routes JSON with two entries sharing a name
            // left the rejected one watching its sensor forever, firing turnouts and speeds for a
            // route the UI has no handle on and only a restart could stop.
            r.disable();

            this.logf("route.alreadyImportedSkipping", r.getId(), r.getName().trim());
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
            this.logf("route.alreadyImportedSkipping", id, name);
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
     * Fetches an accessory by name, falling back to the same address under the other accessory type.
     *
     * A signal and a switch are the same physical decoder - the type only selects how TrainControl
     * displays it - and the database is keyed by address and protocol, so "Signal 5" and "Switch 5"
     * are one entry.  Only one of those two names is registered, though: whichever the accessory was
     * created as.  Without this fallback a route or autonomy edge naming the other one does not
     * resolve, and that accessory is then silently never commanded.
     *
     * Only the type prefix is swapped, so the address and any protocol suffix still have to match
     * exactly and the name map stays authoritative.  Never creates anything.
     * @param name
     * @return
     */
    @Override
    public final MarklinAccessory getAccessoryByName(String name)
    {
        MarklinAccessory accessory = this.accDB.getByName(name);

        if (accessory != null || name == null)
        {
            return accessory;
        }

        String switchPrefix = Accessory.accessoryTypeToPrettyString(Accessory.accessoryType.SWITCH);
        String signalPrefix = Accessory.accessoryTypeToPrettyString(Accessory.accessoryType.SIGNAL);

        if (name.startsWith(signalPrefix))
        {
            return this.accDB.getByName(switchPrefix + name.substring(signalPrefix.length()));
        }

        if (name.startsWith(switchPrefix))
        {
            return this.accDB.getByName(signalPrefix + name.substring(switchPrefix.length()));
        }

        return null;
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
    @Override
    public final MarklinFeedback newFeedback(int id, CANMessage message)
    {
        MarklinFeedback newFb = new MarklinFeedback(this, id, (CS2Message) message);
                
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
        // Build the new cache fully in a local map, then publish it in a single volatile write.
        // Unsynchronized readers must never see a half-populated map: the volatile store below
        // happens-before their volatile read, so they observe either the complete old map or the
        // complete new one - never one that is being mutated.
        HashMap<Integer, List<String>> newCache = new HashMap<>();

        for (MarklinLocomotive l : this.locDB.getItems())
        {
            int id = l.getIntUID();
            if (!newCache.containsKey(id))
            {
                newCache.put(id, new LinkedList<>());
            }

            newCache.get(id).add(l.getUID());
        }

        this.locIdCache = newCache;
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
     * Initializes a message buffer based on the protocol being used
     * @return 
     */
    @Override
    public byte[] initMessageBuffer()
    {
        return new byte[CS2Message.MESSAGE_LENGTH];
    }
    
    @Override
    public CANMessage createMessage(byte[] raw)
    {
        return new CS2Message(raw);
    }
        
    /**
     * Receives a network messages from the CS2 for interpretation
     * @param msg
     */
    @Override
    public void receiveMessage(CANMessage msg)
    {
        if (msg == null) return;
        
        CS2Message message = (CS2Message) msg;
        
        synchronized (this)
        {
            // CS3 seems to send respones packets twice.  Ignore the second.
            // Within a window, now.  The CS3's duplicate follows its original by milliseconds; an
            // identical packet an hour later is a second real command - the same accessory told to go
            // to the same place again, which is an ordinary thing to do - and dropping it meant no
            // confirmation, no log, and nothing to wake whatever was waiting for the actuation.
            if (lastPacket != null &&
                    (message.isAccessoryCommand() || message.isLocCommand() || message.isFeedbackCommand()) &&
                    message.equals(lastPacket)
                    && (System.nanoTime() - this.lastPacketAt) < DUPLICATE_WINDOW_NS
            )
            {
                if (this.debug && DEBUG_LOG_NETWORK)
                {
                    this.logf("network.skippingDuplicatePacket", message.toString());
                }
                
                return;
            }
        
            this.lastPacketAt = System.nanoTime();

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
            this.feedbackMessageProcessor.submit(() ->
            {
                int id = message.extractShortUID();

                // Resolved once into a local, as the locomotive and accessory branches below do.  This
                // was hasId followed by getById, two separate acquisitions of the collection's lock -
                // and syncLayouts prunes feedbacks the freshly loaded diagram does not refer to, on
                // another thread, which is exactly what a feedback auto-created here for an unknown
                // s88 is.  The resulting NPE would be swallowed by the executor's Future.
                //
                // A dropped feedback event is not a cosmetic loss: the state is a LEVEL, and a driving
                // thread waiting on that sensor waits without a timeout.  A train whose arrival was
                // dropped never slows and never stops.
                MarklinFeedback feedback = this.feedbackDB.getById(id);

                if (feedback != null)
                {
                    feedback.parseMessage(message);
                }
                else
                {
                    newFeedback(id, message);
                }
            });
        }
        // Only worry about the message if it's a response
        else if (message.isLocCommand() && message.getResponse())
        {            
            this.locMessageProcessor.submit(() ->
            {
                Integer id = message.extractUID();

                // Built on demand, exactly as exec() already does.  The cache is only populated by a
                // SUCCESSFUL sync, while the UDP reader is started regardless - so a Central Station
                // that answers CAN but not its web server left this null, and the NPE was captured by
                // this executor's discarded Future and surfaced nowhere.  Every locomotive state
                // update was dropped in silence, and the UI showed stale speeds until a later sync.
                if (this.locIdCache == null) rebuildLocIdCache();

                List<String> locs = this.locIdCache.get(id);

                if (locs != null)
                {
                    if (!locs.isEmpty())
                    {
                        List<Locomotive> locList = new ArrayList<>();

                        for (String l : locs)
                        {
                            MarklinLocomotive loc = this.locDB.getById(l);

                            // Null if the locomotive was deleted between locIdCache being read above
                            // and this lookup.  The resulting NPE was captured by the executor's
                            // Future and never surfaced anywhere, so the whole message was dropped in
                            // silence - including the updates for any other locomotives on this UID.
                            if (loc != null)
                            {
                                locList.add(loc);
                                loc.parseMessage(message);
                            }
                        }

                        if (this.view != null)
                        {
                            // repaintLoc() already hands off to its own executor + invokeLater,
                            // so no wrapper thread is needed (avoids a thread spawn per loco response).
                            this.view.repaintLoc(false, locList);
                        }
                    }
                    else
                    {
                        this.logf("network.unknownLocomotiveCommand", MarklinLocomotive.addressFromUID(id));
                    }
                }
            });
        }
        else if (message.isAccessoryCommand() && message.getResponse())
        {
            this.locMessageProcessor.submit(() ->
            {
                int id = message.extractUID();

                // Resolved once into a local, as the locomotive branch above does.  This was hasId
                // followed by three separate getById calls, so the accessory could disappear between
                // them - restoreState deletes accessories with an invalid address while the CAN
                // listener is already running - and the resulting NPE would be swallowed by the
                // executor's Future, dropping the update in silence.
                MarklinAccessory accessory = this.accDB.getById(id);

                if (accessory != null)
                {
                    accessory.parseMessage(message);

                    if (this.view != null)
                    {
                        // repaintSwitch() already marshals to the EDT via invokeLater internally.
                        this.view.repaintSwitch(accessory.getAddress() + 1, accessory.getDecoderType());
                        //this.view.repaintSwitches();
                    }
                }
            });
        }
        else if (message.isSysCommand() && 
           (message.getSubCommand() == CS2Message.CMD_SYSSUB_GO || message.getSubCommand() == CS2Message.CMD_SYSSUB_STOP)
        )
        {
            this.locMessageProcessor.submit(() ->
            {
                if (message.getSubCommand() == CS2Message.CMD_SYSSUB_GO)
                {
                    this.setPowerState(true);

                    // For correctly tracking locomotive stats
                    for (MarklinLocomotive l : this.locDB.getItems())
                    {
                        l.notifyOfPowerStateChange(true);
                    }

                    if (this.view != null) this.view.updatePowerState();
                    this.logf("log.powerOn");
                }
                else if (message.getSubCommand() == CS2Message.CMD_SYSSUB_STOP)
                {
                    this.setPowerState(false);

                    // For correctly tracking locomotive stats
                    for (MarklinLocomotive l : this.locDB.getItems())
                    {
                        l.notifyOfPowerStateChange(false);   
                    }

                    if (this.view != null) this.view.updatePowerState();
                    this.logf("log.powerOff");
                }
            });
        }
        else if (message.isPingCommand() && message.getResponse())
        {
            this.systemMessageProcessor.submit(() ->
            {
                // Track latency
                if (this.pingStart > 0)
                {
                    this.lastLatency = ((double) (System.nanoTime() - this.pingStart)) / 1000000.0;
                    this.pingStart = 0;
                    this.pingOutstandingSince = 0;

                    if (this.view != null)
                    {
                        // updateLatency() already marshals to the EDT via invokeLater internally.
                        this.view.updateLatency(this.lastLatency);
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

                        this.logf("network.connectedCentralStation", this.serialNumber);
                    }
                }
            });
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
            // A discarded SYSTEM command is said out loud whether or not debugging is on.  Stop, go
            // and emergency stop all come through here and all return void, so pressing Stop while
            // the application is in its not-connected state did nothing and reported nothing - and
            // that state is reached by a failed startup sync, which is exactly when the power may
            // still physically be on from an earlier session.
            if (m.isSysCommand() && !(debug && MarklinControlStation.DEBUG_LOG_NETWORK))
            {
                this.logf("network.transmissionDisabled", m.toString());
            }

            if (debug && MarklinControlStation.DEBUG_LOG_NETWORK)
            {
                this.logf("network.transmissionDisabled", m.toString());

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
     * Formatted log message
     * @param key
     * @param args 
     */
    /**
     * Tells the view a feedback changed, if there is one listening.
     *
     * Only the route editor's capture wants it.  A sensor's tiles repaint themselves, so nothing else
     * here has ever needed to know - which is why capturing a CONDITION worked for switches, whose
     * repaint already reached the view, and did nothing at all for sensors.
     *
     * @param name the feedback's name
     * @param state whether it is now occupied
     */
    public void feedbackChanged(String name, boolean state)
    {
        if (this.view != null) this.view.feedbackChanged(name, state);
    }

    @Override
    public final void logf(String key, Object... args)
    {
        log(I18n.f(key, args));
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
        // A ping already in flight used to silence this method until an answer arrived - and the only
        // thing that clears pingStart is an answer.  UDP does not promise one.  So a single dropped
        // response stopped the keepalive for the rest of the session: no ping was ever sent again, the
        // age of that one ping grew for ever, the status line read "lost connection" permanently even
        // after the network came back, and - the part that matters - the five-second latency check
        // kept firing, so a running layout with a latency limit had its power cut every five seconds,
        // including five seconds after the operator switched it back on.  Only a restart recovered.
        //
        // An unanswered ping is now retried rather than treated as still pending.  The outage clock
        // below is what keeps the lost-connection warning honest across the retries.
        if (this.pingStart == 0 || force
            || (System.nanoTime() - this.pingStart) > PING_RETRY_NS)
        {
            if (this.pingOutstandingSince == 0 || force)
            {
                this.pingOutstandingSince = System.nanoTime();
            }

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
        // The start of the silence, not the latest retry.  Reading the retry would reset this to zero
        // every time the keepalive tried again, so a station that had been unreachable for an hour
        // would look like one that had been unreachable for five seconds - which is exactly the
        // reading the lost-connection warning exists to distinguish.
        if (this.pingOutstandingSince > 0)
        {
            return (System.nanoTime() - this.pingOutstandingSince) / 1000000;
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
    public void locFunctionsOff(Locomotive loc)
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
            newLoc.setModelMultiUnitLocomotives(c.getCentralStationLinkedLocomotives());
            
            this.locDB.add(newLoc, newLoc.getName(), newLoc.getUID());
            
            return newLoc; 
        }
        else
        {
            // This is a sanity check for corrupt versions of the database from older versions of TrainControl
            this.logf("loc.savedDuplicateSkipping", c.getName());
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
        // Carry over the actuation count from whatever is already at this address.  Resolved by name
        // (which falls back to address + protocol) rather than getAccessoryByAddress: the latter takes
        // a LOGICAL address, so passing the raw one looked up the accessory one address below - and,
        // being a creating lookup, registered a spurious accessory there every time.
        MarklinAccessory current = this.getAccessoryByName(
            MarklinAccessory.getNameWithProtocol(logicalAddress, type, decoderType));

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
            this.logf("acc.invalidAddressWarning", name, address);
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
        
        if (l == null) throw new Exception(I18n.f("loc.notExist", locName));
        
        if (!MarklinLocomotive.validateNewAddress(newDecoderType, newAddress))
        {
            throw new Exception(I18n.f("loc.addrOutOfRange", newAddress));
        }
        
        if (newDecoderType == MarklinLocomotive.decoderType.MULTI_UNIT && l.hasLinkedLocomotives())
        {
            /* l.preSetLinkedLocomotives(null);
            l.setLinkedLocomotives();
            this.log("Multi-unit locomotives have been unlinked from " + locName);*/
            
            throw new Exception(I18n.f("loc.changeToMultiUnitNotAllowed"));       
        }
        
        // Execute the change.  locDB.delete rather than deleteLoc, matching what renameLoc already
        // does: this is a re-key of a locomotive that continues to exist, not a deletion.  deleteLoc
        // now unlinks the locomotive from every consist referencing it, which would silently drop it
        // from its multi-unit on an address change - and the revalidation loop at the end of this
        // method could not restore it, because the link would already be gone from the name map.
        this.locDB.delete(l.getName());
        
        l.setAddress(newAddress, newDecoderType);
        
        this.locDB.add(l, l.getName(), l.getUID());
        
        this.rebuildLocIdCache();
        
        this.logf("loc.addressChanged", l.getName(), newAddress, newDecoderType.name());
        
        // Ensure linked locomotives have valid addresses
        for (Locomotive other : getLocomotives())
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
        // Staging counts.  A staging flow spends its planning phase with nothing dispatched, so
        // isRunning() alone reported the whole window as idle - and every guard that asks the model
        // rather than the UI permitted a locomotive to be deleted, renamed or re-addressed out from
        // under a plan about to drive it.
        return this.hasAutoLayout()
            && (this.getAutoLayout().isRunning() || this.getAutoLayout().isStagingInProgress());
    }
    
    /**
     * Checks if this locomotive is directly linked to any others as a multi-units
     * @param l
     * @return 
     */
    @Override
    public Locomotive isLocLinkedToOthers(Locomotive l)
    {
        for (Locomotive other : getLocomotives())
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
    public final List<Locomotive> getLocomotives()
    {
        List<Locomotive> out = new ArrayList<>();
        out.addAll(this.locDB.getItems());
        return out;
    }
    
    /**
     * Same as getLocomotives, but without casting
     * @return 
     */
    public final List<MarklinLocomotive> getMarklinLocomotives()
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
        // Captured before the delete.  A consist holds Locomotive references rather than names, so a
        // deleted member has to be removed from them explicitly - otherwise the head goes on fanning
        // every speed, direction and function command to the decoder of a locomotive that no longer
        // appears anywhere in the UI, right up until a restart fails to resolve the saved name and
        // drops the link with nothing but a log line.
        MarklinLocomotive deleted = this.locDB.getByName(name);

        boolean res = this.locDB.delete(name);

        if (res)
        {
            if (deleted != null)
            {
                for (MarklinLocomotive other : this.locDB.getItems())
                {
                    // unlinkLocomotive rather than mutating the map returned by getLinkedLocomotives:
                    // it takes the same lock setSpeed holds while fanning out to that map
                    if (other.unlinkLocomotive(deleted))
                    {
                        this.logf("loc.unlinkedDeletedLocomotive", deleted.getName(), other.getName());
                    }
                }
            }

            this.rebuildLocIdCache();

            // The autonomy setup holds locomotives by name, in every configuration - see the note in
            // renameLoc.  A deleted one is taken out rather than followed.
            if (this.view != null) this.view.autonomyLocomotiveDeleted(name);

            // And the routes, which renameLoc has always repaired and this did not.
            //
            // A route command naming a locomotive that is not in the database does nothing when the
            // route fires - silently, because a route is a list of commands and one of them quietly
            // not applying looks exactly like a route that ran.  Renaming followed the locomotive into
            // every route; deleting left the name behind.
            for (MarklinRoute r : this.getRoutes())
            {
                // A condition naming the deleted locomotive is left in place on purpose - see the note
                // on locomotiveDeleted - so it is said out loud instead.  That route will not fire
                // again until somebody edits it, and a route that has silently stopped firing is
                // exactly the failure nobody notices.
                if (r.locomotiveDeleted(name))
                {
                    this.logf("route.warnConditionNamesDeletedLocomotive", r.getName(), name);
                }
            }
        }

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

        // Unlike the other lookups in this class, this one used to throw on an unknown name
        if (l == null)
        {
            return null;
        }

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
            
            // Nothing else to repair by identity: a locomotive hashes by identity, so renaming one
            // cannot move it out of the consists, exclusion sets or run lists that hold it.  This used
            // to need a sweep - see the note on MarklinLocomotive.hashCode.
            //
            // State held by NAME does still need repairing, and there are TWO such places: the routes
            // below, and the autonomy SETUP, which holds a name in each configuration's placements,
            // homes and exclusion lists.
            //
            // There were three.  The running layout's home assignments were the third, and they are
            // gone from this list because a Point now holds the LOCOMOTIVE rather than its name - a
            // rename changes the object, so there is nothing left to repair.  The setup keeps its
            // repair because a file has to hold a name.
            if (this.view != null) this.view.autonomyLocomotiveRenamed(name, newName);
            
            // Update names in routes
            for (MarklinRoute r : this.getRoutes())
            {
                r.locomotiveRenamed(name, newName);
            }
            
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
            this.logf("acc.invalidAddressStateWarning", "setAccessoryState", address);            
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
        MarklinRoute r = this.routeDB.getByName(name);

        // The programmatic API reaches this directly with caller-supplied names, and an unknown
        // one used to NPE.  getLocAddress in this class was fixed for exactly this shape.
        if (r == null)
        {
            this.logf("route.warningRouteNotExistCalledFrom", name, "execRoute");
            return;
        }

        r.execRoute(false);
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

            // Update auto layout route selections.
            // hasAutoLayout, not getAutoLayout() != null: getAutoLayout() CREATES a Layout when none
            // exists, so the old test was always true and deleting a route on a setup with no autonomy
            // silently instantiated one - bumping the static layoutVersion along with it.
            if (this.hasAutoLayout())
            {
                if (this.getAutoLayout().getActivateRouteIDs().contains((Integer) r.getId()))
                {
                    this.getAutoLayout().getActivateRouteIDs().remove((Integer) r.getId());
                }
            }
            
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
        
        Integer oldId = r.getId();
        
        this.routeDB.delete(name);
        
        r.setId(newId);
        this.routeDB.add(r, name, newId);
        
        // Update auto layout route selections
        if (this.hasAutoLayout())
        {
            if (this.getAutoLayout().getActivateRouteIDs().contains(oldId))
            {
                this.getAutoLayout().getActivateRouteIDs().remove(oldId);
                this.getAutoLayout().getActivateRouteIDs().add(newId);
            }
        }
        
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
     * Gets the state of the accessory with the specified address.  If it does not exist, a new
     * switch is created.
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
            this.logf("acc.invalidAddressStateWarning", "getAccessoryState", address);            
            
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
     * Returns an accessory based on its numerical address.  If the address does not exist, a new
     * switch is created.
     * @param address greater than 1
     * @param decoderType
     * @return 
     */
    @Override
    public MarklinAccessory getAccessoryByAddressIfPresent(int address, Accessory.accessoryDecoderType decoderType)
    {
        if (address < 1) return null;

        return this.accDB.getById(MarklinAccessory.UIDfromAddress(address - 1, decoderType));
    }

    /**
     * Returns the accessory at the given address, CREATING one if it does not exist.
     *
     * Use getAccessoryByAddressIfPresent for read-only paths - display, export, validation.  This one
     * registers a switch on a miss, which is deliberate for command paths but fills the database with
     * phantoms when called merely to look at something.
     * @param address
     * @param decoderType
     * @return 
     */
    public MarklinAccessory getAccessoryByAddress(int address, Accessory.accessoryDecoderType decoderType)
    { 
        // Sanity check
        if (address < 1)
        {
            this.logf("acc.invalidAddressStateWarning", "getAccessoryByAddress", address);
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
     * Gives back everything this control station holds: the listener, its port, and the three threads
     * that serve inbound messages.
     *
     * Not needed to close the application - every one of those is a daemon now, so they go with the
     * JVM - but a caller that creates a control station and finishes with it has no other way to
     * release UDP 15730, and a second init() in the same JVM then failed on a port the first still
     * held.
     */
    public void shutdown()
    {
        if (this.NetworkInterface != null) this.NetworkInterface.stopListening();

        this.locMessageProcessor.shutdownNow();
        this.feedbackMessageProcessor.shutdownNow();
        this.systemMessageProcessor.shutdownNow();
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
     * Sets the power
     * @param state 
     */
    private void setPowerState(boolean state)
    {
        synchronized(this)
        {
            this.powerState = state;
            notifyAll();
        }
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
    public LayoutDiagram getLayout(String name)
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

    @Override
    public void showAutonomyAlert(String message)
    {
        if (this.view != null)
        {
            this.view.showAutonomyAlert(message);
        }
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
        
        this.logf("route.deletingExisting");
        for (MarklinRoute r : this.routeDB.getItems())
        {
            this.deleteRoute(r.getName());
        }
        
        // If all read successfully, remove existing routes and update route DB
        for (MarklinRoute route : routes)
        {
            this.logf("route.adding", route.getName());

            if (!this.newRoute(route))
            {
                this.logf("route.notAdded", route.getName());
            }
        }
    }
        
    /**
     * Exports the locomotive database to a user-friendly CSV file for reference
     * @return 
     */
    @Override
    public String exportLocsToCSV()
    {
        StringBuilder csvBuilder = new StringBuilder();

        // Header row
        csvBuilder.append("Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes\n");

        for (Locomotive l : this.getLocomotives())
        {
            String name = escapeCsv(l.getName());
            List<String> mappings = this.view.getAllLocButtonMappings(l);
            String mappingStr = escapeCsv(String.join(";", mappings)); // Semicolon to avoid comma collision
            String decoder = escapeCsv(l.getDecoderTypeLabel());
            int address = l.getAddress();
            int prefSpeed = l.getPreferredSpeed();
            String runtime = escapeCsv(Conversion.convertSecondsToHMmSs(l.getTotalRuntime()));
            
            int startYear = l.getStructuredNotes().getStartYear();
            int endYear = l.getStructuredNotes().getEndYear();
            String railway = escapeCsv(l.getStructuredNotes().getRailway());
            String notes = escapeCsv(l.getStructuredNotes().getNotes());

            csvBuilder.append(String.format("%s,%s,%s,%d,%d,%s,%d,%d,%s,%s\n",
                name, mappingStr, decoder, address, prefSpeed, runtime, startYear, endYear, railway, notes));
        }

        return csvBuilder.toString();
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
        System.out.println(I18n.f("app.starting", I18n.f("app.title", MarklinControlStation.RAW_VERSION)));
        
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
                System.out.println(I18n.t("error.prefLoadAdminHint"));
                
                if (debug)
                {
                    ex.printStackTrace();
                }
            }   
        }

        if (!simulate)
        {
            String lastIP = "";
            
            while (true)
            {
                try
                {
                    if (initIP == null)
                    {       
                        if (!GraphicsEnvironment.isHeadless())
                        {
                            System.out.println(I18n.t("ui.promptIpPopup"));

                            JTextField ipField = new JTextField();
                            if (lastIP != null) ipField.setText(lastIP);

                            Object[] options = {
                                I18n.t("ui.ok"),
                                I18n.t("ui.cancel"),
                                I18n.t("ui.autoDetect")
                            };

                            Object[] message = {
                                I18n.t("ui.enterCentralStationIp"),
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
                            JDialog dialog = optionPane.createDialog(I18n.t("ui.ipAddressInputTitle"));
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
                                    System.out.println(I18n.t("ui.detectCentralStationAttempt")); 
                                    
                                    if (!CSDetect.hasLocalSubnets())
                                    {
                                        JOptionPane.showMessageDialog(
                                            null,
                                            I18n.t("ui.autoDetectNotPossibleNoInterfaces")
                                        );
                                        continue;
                                    }
                                    
                                    initIP = CSDetect.detectCentralStation();

                                    if (initIP == null)
                                    {
                                        JOptionPane.showMessageDialog(
                                            null,
                                            I18n.t("ui.noCentralStationDetected")
                                        ); 
                                        continue;
                                    }
                                    
                                    break;
                                default:
                                    break;
                            }
                        }
                        else
                        {
                            // Not try-with-resources: closing a Scanner closes System.in with it,
                            // and this prompt is inside a retry loop.  A headless user who mistyped
                            // the address once got NoSuchElementException out of the second prompt -
                            // uncaught, since only HeadlessException is - instead of another go.
                            {
                                Scanner scanner = new Scanner(System.in);

                                System.out.print(I18n.t("ui.enterCentralStationIpPrompt"));
                                initIP = scanner.next();
                            }
                        }
                        
                        if (initIP == null || "".equals(initIP))
                        {
                            System.out.println(I18n.t("ui.noIpEnteredShutdown"));
                            
                            if (!GraphicsEnvironment.isHeadless())
                            {
                                JOptionPane.showMessageDialog(null, I18n.t("ui.noIpEnteredShutdown"));
                            }
                            
                            System.exit(1);
                        }
                    }
                            
                    System.out.println(I18n.f("ui.connectingToIp", initIP));

                    if (!CS2File.ping(initIP))
                    {
                        System.out.println("No response from " + initIP);

                        if (!GraphicsEnvironment.isHeadless())
                        {
                            JOptionPane.showMessageDialog(
                                null,
                                I18n.f("ui.noResponseFromIp", initIP)
                            );                        
                        }
                    }
                    else
                    {
                        // Verify that the device is actually a central station
                        if (!CSDetect.isCentralStation(initIP) && !CSDetect.isVNCAvailable(initIP))
                        {
                            System.out.println(I18n.f("ui.deviceNotCentralStationOrUnreachable", initIP));

                            if (!GraphicsEnvironment.isHeadless())
                            {
                                JOptionPane.showMessageDialog(null, I18n.f("ui.deviceNotCentralStationOrUnreachable", initIP));
                            }
                        }
                        
                        try
                        {
                            TrainControlUI.getPrefs().put(TrainControlUI.IP_PREF, initIP);
                        }
                        catch (Exception ex)
                        {
                            System.out.println(I18n.t("ui.errorUpdatingPreferences"));

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
                    System.out.println(I18n.t("ui.unableToPromptForIpRestart"));
                }

                lastIP = initIP;
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
            model.logf("ui.initializing");

            final CountDownLatch latch = new CountDownLatch(1);

            // Whether the window was actually built (FBR-C3).
            //
            // Without it the caller below cannot tell: invokeLater only enqueues, so the try/catch
            // around it never sees anything the posted lambda throws, and nothing else after await()
            // asks.  The comment on the countDown claimed control was handed "back to a caller that
            // can log and exit"; there was no such caller, and a failed build left a live process with
            // no window, one log line, and display() throwing on the event thread.
            final java.util.concurrent.atomic.AtomicBoolean built =
                new java.util.concurrent.atomic.AtomicBoolean(false);

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                try
                {
                    theUI.setViewListener(model, latch);

                    built.set(true);
                }
                catch (Throwable ex)
                {
                    model.logf("ui.errorInitializing");
                    model.log(ex instanceof Exception ? (Exception) ex : new Exception(ex));

                    try
                    {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex1)
                    {
                        Thread.currentThread().interrupt();
                    }
                }
                finally
                {
                    // Counted down whatever happened (OB-077).
                    //
                    // The only countDown was the last statement of setViewListener, so anything that
                    // stopped it reaching that line left the latch at one - and the wait below has no
                    // timeout. The catch above logged, slept, and returned without counting down; a
                    // RuntimeException or an Error did not even reach it. Either way the application
                    // hung for ever with no window and nothing on screen to say why, which is the
                    // worst symptom a start-up fault can have.
                    //
                    // Throwable rather than IOException for the same reason: an Error thrown while
                    // building a window is exactly the case that used to walk past this handler.
                    //
                    // Counting down after a FAILED build is deliberate. It hands control back to a
                    // caller that logs and exits - see the `built` flag above and the check after
                    // await() - rather than to one waiting for something that is never going to
                    // happen.
                    latch.countDown();
                }
            });

            latch.await();

            // And now the caller the countDown's comment promised (FBR-C3).
            //
            // Going on to display() a window that failed to build gets a second exception on the
            // event thread and a process running with nothing on screen. Whatever went wrong was
            // already logged where it was caught; this says what is being done about it and stops.
            if (!built.get())
            {
                model.logf("ui.fatalErrorInitializing");

                System.exit(0);
            }

            model.logf("ui.rendering");
            
            try
            {
                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    theUI.display();
                    model.logf("ui.initialized");
                });
            }
            catch (Exception ex)
            {
                // Reachable only for a failure to POST the task - the lambda's own exceptions
                // land on the event thread, which is why the flag above exists rather than this
                // catch being widened to look as though it covers them.
                model.logf("ui.fatalErrorInitializing");
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
