package org.traincontrol.marklin;

import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.LayoutDiagram;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
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
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
import org.traincontrol.marklin.file.UnreadableAnswerException;
import org.traincontrol.base.udp.CANMessage;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.marklin.udp.CSDetect;
import org.traincontrol.marklin.udp.NetworkProxy;
import org.traincontrol.model.ModelListener;
import org.traincontrol.model.View;
import org.traincontrol.model.ViewListener;
import org.traincontrol.util.Conversion;
import org.traincontrol.util.I18n;
import org.traincontrol.util.QueuedLogHandler;
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
    public static final String RAW_VERSION = "3.1.0";

    /**
     * Whether this is a build on the way to a release; true puts the build ID in the log and the window title.
     * False in a release commit, true on dev between releases (docs/RELEASE.md); nothing derives it.
     */
    public static final boolean IS_PRE_RELEASE = true;

    /**
     * When the running code was built, for a pre-release: the jar's timestamp, or the class file's from an IDE build
     * directory.  Best-effort and quiet about failing: a version string is not worth an exception.
     *
     * @return the build stamp, or null when it cannot be established
     */
    public static String buildId()
    {
        try
        {
            java.security.CodeSource source =
                MarklinControlStation.class.getProtectionDomain().getCodeSource();

            if (source == null || source.getLocation() == null) return null;

            java.io.File at = new java.io.File(source.getLocation().toURI());

            // A directory is an IDE run, and its own timestamp is when it was created rather than when
            // anything in it was last compiled - so the class file is asked instead.
            if (at.isDirectory())
            {
                java.io.File cls = new java.io.File(at,
                    MarklinControlStation.class.getName().replace('.', '/') + ".class");

                if (cls.exists()) at = cls;
            }

            long stamp = at.lastModified();

            if (stamp <= 0) return null;

            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(stamp));
        }
        catch (Exception | Error cannotTell)
        {
            return null;
        }
    }

    /**
     * The version as a person should see it, with the build appended on a pre-release (one method, so the log line and
     * the window title cannot drift).
     *
     * @return "3.0.0", or "3.0.0 (build yyyy-MM-dd HH:mm)" before a release
     */
    public static String versionForDisplay()
    {
        if (!IS_PRE_RELEASE) return RAW_VERSION;

        String build = buildId();

        // The word PRE-RELEASE even when the build cannot be established, because that is the fact the
        // flag exists to publish and a missing timestamp does not make it a release.
        return build == null
            ? RAW_VERSION + " (pre-release)"
            : RAW_VERSION + " (pre-release build " + build + ")";
    }
        
    //// Settings
    
    // Locomotive database save file
    public static final String DATA_FILE_NAME = "LocDB.data";

    // Debug mode
    private boolean debug = false;

    // Debug only: the first N syncs fail when traincontrol.failFirstSyncs=N, for a manual test (OB-338)
    private static final String FAIL_FIRST_SYNCS = "traincontrol.failFirstSyncs";

    // Read as debug mode is entered (see debug(boolean)), and counted down as the syncs fail
    private final java.util.concurrent.atomic.AtomicInteger failFirstSyncsLeft = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * Whether this session simulates rather than talks to a Central Station.  Not `on`, which says the last sync
     * succeeded: a local layout folder syncs happily, so a simulation would report itself connected (OB-098).
     */
    private boolean simulation = false;
    
    // Do we print out packets in debug mode?
    public static boolean DEBUG_LOG_NETWORK = true;
    
    // Do we parse mock packets when not connected to the central station and in debug mode?
    // This will update the UI when locomotive/function/switch commands get sent
    public static boolean DEBUG_SIMULATE_PACKETS = false;

    /**
     * Where a sync reads from instead of the configured Central Station; a test seam like DEBUG_SIMULATE_PACKETS.
     * A bare host:port string (CS2File prefixes "http://"); null in every normal run.
     */
    private static volatile String TEST_CS2_ADDRESS = null;

    /**
     * The name of the field above, for a test that sets it by reflection (a public mutable static would let any code
     * redirect the station address).  Named here so a rename breaks the build, not the test at run time.
     */
    public static final String TEST_ADDRESS_FIELD = "TEST_CS2_ADDRESS";

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

    // Each sensor's s88 device as the saved file had it, handed to a CS3's proxy only (OB-418)
    private final Map<Integer, Integer> savedS88Devices = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Route database
    private final RemoteDeviceCollection<MarklinRoute, Integer> routeDB;

    // The routes the last import split, with the stop route each now fires - see importRoutes
    private volatile List<String[]> routesSplitByLastImport = new ArrayList<>();
    
    // Layouts
    private final RemoteDeviceCollection<LayoutDiagram, String> layoutDB;

    // The pages in the order the index file lists them (FR-175); layoutDB holds no order.  Replaced whole by each import.
    private volatile List<String> layoutOrder = new ArrayList<>();

    // Mapping for int UID -> string UID
    // volatile: written by rebuildLocIdCache() (synchronized) but read unsynchronized on the
    // message-processor thread in receiveMessage().  The volatile store/load gives the reader a
    // happens-before edge so it always sees a fully-built map (see rebuildLocIdCache).
    private volatile HashMap<Integer, List<String>> locIdCache;

    // Network proxy reference
    private final NetworkProxy NetworkInterface;
    
    // File parser class
    private CS2File fileParser;

    // Serializes layout refreshes.  Deliberately not the station's own monitor - see refreshLayouts
    private final Object layoutRefreshLock = new Object();
    
    // GUI reference
    private final View view;
    
    // volatile: written by setNetworkCommState from the menu and read by exec() from every command thread, no monitor.
    private volatile boolean on;

    /**
     * Who is told about every command handed to `exec`, sent or not (MT-359).  A test sees what a locomotive ASKS
     * for even though the network is off and exec discards the message.  Null unless a test sets it.
     */
    private volatile java.util.function.Consumer<CS2Message> sentMessageObserver;
    
    // Whether the track power is on.  volatile because getPowerState() takes no monitor (MarklinRoute reads it on the
    // route thread, the UI on the event thread); without a happens-before edge a route could run with the power off.
    private volatile boolean powerState = true; // default to true unless power is turned off

    /** Whether this power cut has warned already; read and set on `locMessageProcessor` only. */
    private boolean powerCutWarned;
        
    // Unique ID of the central station (0 for all stations)
    private int UID = 0;
    private int serialNumber;
    
    // Last message output
    private String lastMessage;
    
    // Is this a CS3?
    private boolean isCS3 = false;

    // The accessory UIDs this sync's pages show as both a switch and a signal (syncLayouts); see wireComponents (OB-405)
    private Set<Integer> mixedAddresses = Collections.emptySet();
    
    // Automation controller
    private Layout autoLayout;
    
    // Number of network messages processed
    private int numMessagesProcessed = 0;
    
    // Ping metrics
    /** How long an unanswered ping is left in flight before the keepalive sends another (under the ping interval). */
    private static final long PING_RETRY_NS = 2000L * 1000000L;

    // Written by the ping timer thread and cleared by the message processor, read by both
    private volatile long pingStart;

    /**
     * When the current run of unanswered pings began, or 0 when the last one was answered.  Separate from pingStart
     * (the LATEST ping): latency measured from the first of a run would report an outage as one round trip and cut the
     * power on recovery.
     */
    private volatile long pingOutstandingSince;

    /**
     * The last measured round trip, in milliseconds.  volatile, and a double can tear: written on the message-processor
     * thread, read by getLastLatency() for the display and the cutoff.
     */
    private volatile double lastLatency;

    /**
     * Whether the locomotive database existed and would not read.  The application then carries on with an EMPTY
     * database and the save on exit would write that emptiness over the real file; writeAtomically cannot help.
     */
    private boolean databaseLoadFailed = false;

    // Thread pools for network messages
    CS2Message lastPacket;

    /** When lastPacket arrived; the CS3's duplicate comes back to back, so a later one is a second real command. */
    private long lastPacketAt;

    private static final long DUPLICATE_WINDOW_NS = 250L * 1000000L;

    /**
     * How long the Central Station may take to acknowledge a power command: the echo takes milliseconds or never
     * comes, so two seconds never trips on a busy station and costs only a pause when it is off.
     */
    public static final long POWER_STATE_TIMEOUT = 2000;
    // DAEMONS.  These serve inbound CAN messages and have no meaning without the application they serve,
    // but the default factory makes ordinary threads, which would outlive every caller and keep the JVM up.
    /**
     * Where a task that ended in an error nobody caught is reported, for the pools' thread factories: the stack trace
     * on stderr, as the JVM's own handler prints it, and a line in the log window naming the thread, through the
     * station started last (the Logger alone when none has started).
     */
    public static final Thread.UncaughtExceptionHandler THREAD_ERRORS = (thread, error) ->
    {
        System.err.print("Exception in thread \"" + thread.getName() + "\" ");
        error.printStackTrace();

        String said = I18n.f("error.uncaughtInThread", thread.getName(), error);
        MarklinControlStation station = MarklinControlStation.reporter;

        if (station != null) station.log(said);
        else MarklinControlStation.log.warning(said);
    };

    /** The station started last, which `THREAD_ERRORS` reports through. */
    private static volatile MarklinControlStation reporter;

    private static ExecutorService messageProcessor(final String name)
    {
        return Executors.newFixedThreadPool(1, runnable ->
        {
            Thread thread = new Thread(runnable, name);

            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(THREAD_ERRORS);

            return thread;
        });
    }

    private final ExecutorService locMessageProcessor = messageProcessor("cs2-loc-messages");
    private final ExecutorService feedbackMessageProcessor = messageProcessor("cs2-feedback-messages");
    private final ExecutorService systemMessageProcessor = messageProcessor("cs2-system-messages");
    
    private static final Logger log = Logger.getLogger(MarklinControlStation.class.getName());
                    
    /**
     * Builds the station for a start that is not a simulation, and makes the first sync: the five-argument constructor
     * with simulate false.
     *
     * @param network the connection to the Central Station
     * @param view the window, or null when there is none
     * @param autoPowerOn whether to turn the track power on once connected
     * @param debug whether debug mode is on
     */
    public MarklinControlStation(NetworkProxy network, View view, boolean autoPowerOn, boolean debug)
    {
        this(network, view, autoPowerOn, debug, false);
    }

    /**
     * Builds the station and makes the first sync.
     *
     * @param network the connection to the Central Station
     * @param view the window, or null when there is none
     * @param autoPowerOn whether to turn the track power on once connected
     * @param debug whether debug mode is on
     * @param simulate whether there is no Central Station to reach, in which case a failed first sync is never asked about
     */
    public MarklinControlStation(NetworkProxy network, View view, boolean autoPowerOn, boolean debug, boolean simulate)
    {       
        reporter = this;

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
                // The record's time, not the clock's: the line is written later, from another thread
                return String.format("%s %s%n", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(record.getMillis()), record.getMessage());
            }
        });

        // Through a queue, so that no thread that logs waits on the console (OB-421)
        log.addHandler(new QueuedLogHandler(consoleHandler));
        
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
        
        this.logf("app.uititle", I18n.f("app.title", MarklinControlStation.versionForDisplay()));
        
        this.logf("log.restoring");

        // Restore state
        for (MarklinSimpleComponent c : this.restoreState(Util.dataPath(MarklinControlStation.DATA_FILE_NAME)))
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

                if (c.getS88Device() != null) this.savedS88Devices.put(c.getAddress(), c.getS88Device());
            }
            else if (c.getType() == MarklinSimpleComponent.Type.ROUTE)
            {
                newRoute(c.getName(), c.getAddress(), c.getRoute(), c.getS88(), c.getS88TriggerType(), c.getRouteEnabled(), c.getConditions());
            }
        }

        // No route mixes an emergency stop with other commands: one saved before the rule is split, now that
        // every saved route has its id, and the log names both halves.
        this.splitRoutesThatMixAStop();
                
        this.logf("log.restored");
        
        // Asked again until it connects or the user exits, where a window can ask (OB-338)
        Predicate<String> asker = resyncAsker(this.cs2Address(), simulate, GraphicsEnvironment.isHeadless() ? null : view);

        if (syncUntilConnected(this::syncWithCS2, this::getLastSyncError, asker, MarklinControlStation::exitUnconnected) >= 0)
        {
            this.logf("log.csDataImported", network.getIP());

            // Connected, so a second try would meet the same answer: the operator is told instead, and can sync again
            if (this.lastSyncPartial && view != null && !GraphicsEnvironment.isHeadless()) view.showSyncPartial();

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
     * Not a full sync, which also re-imports every route and locomotive: slow and side-effecting (routes are deleted
     * and re-added, addresses and function types adopted), none of it wanted after renaming a page.
     */
    private void syncLayoutsFromConfiguredSource() throws Exception
    {
        // The lock lives here so every entrance inherits it: syncWithCS2 reaches the same clearLayouts() +
        // syncLayouts() and layoutDB is plain HashMaps.  Intrinsic locks are reentrant.
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

        // Serialization lives inside syncLayoutsFromConfiguredSource, so that syncWithCS2 - which
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

    /**
     * Wires one parsed page to the accessories, feedbacks and routes its tiles name.  Parsing does not do this:
     * CS2File.parseLayout builds the components, and this loop attaches each to a live Accessory (creating one from the
     * tile's address when the station has none).  Without it every switch has a null accessory and TileGraph refuses to
     * trace through it.  Public so test support calls this rather than keeping a second copy of the loop.
     *
     * @param l the page
     * @param feedbackAddresses collects the s88 addresses seen, for the caller's pruning; may be null
     */
    public void wireComponents(LayoutDiagram l, List<Integer> feedbackAddresses)
    {
        if (l == null) return;

        List<Integer> seen = feedbackAddresses == null ? new LinkedList<Integer>() : feedbackAddresses;

        for (LayoutDiagramComponent c : l.getAll())
        {
            if (c.isAccessory())
            {                            
                int newAddress = c.getAddress() - 1;                    
                int targetAddress = MarklinAccessory.UIDfromAddress(newAddress, c.getProtocol());
                
                // Make sure all components are added
                if (!this.accDB.hasId(targetAddress) ||
                    // The accessory exists as the other kind: re-create it unless tiles of both kinds share it (OB-405)
                   (this.accDB.hasId(targetAddress) && !this.mixedAddresses.contains(targetAddress) && this.accDB.getById(targetAddress).isSignal() != c.isSignal()) ||
                        
                    // Create / convert the second accessory to switch if needed
                    c.isThreeWay() && (
                        !this.accDB.hasId(targetAddress + 1) ||
                        (this.accDB.hasId(targetAddress + 1) && !this.mixedAddresses.contains(targetAddress + 1) && this.accDB.getById(targetAddress + 1).isSignal() != c.isSignal())
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
                    // The station's accessory file is written only at start-up; a CS3 sync takes the positions (FR-171)
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
                
                seen.add(c.getRawAddress());
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

    /**
     * Each two-position accessory in the position the CS3 last set; nothing changes if the station does not answer (FR-171).
     */
    private void adoptCS3AccessoryStates()
    {
        long asked = System.nanoTime();

        try
        {
            this.fileParser.parseAccessoryStatesCS3().forEach((uid, turned) ->
            {
                MarklinAccessory a = this.accDB.getById(uid);

                if (a != null && a.isSwitched() != turned)
                {
                    a.adoptStationPosition(turned, asked);

                    if (this.view != null)
                    {
                        this.view.repaintSwitch(a.getAddress() + 1, a.getDecoderType());
                    }
                }
            });
        }
        catch (Exception e)
        {
            if (this.debug)
            {
                this.log(e);
            }
        }
    }

    private void syncLayouts() throws Exception
    {
        // ONE PARSER FOR THE WHOLE METHOD: syncWithCS2 reassigns `fileParser` outside the lock that serializes this
        // method, so every question below is asked of this local; a second read of the field could return a parser that
        // parsed nothing, and the prune would run on a partial railway.
        final CS2File parser = this.fileParser;

        // Prune stale feedbacks
        List<Integer> feedbackAddresses = new LinkedList<>();
        
        // Get accessory definition info for the layout
        List<MarklinAccessory> accs = new LinkedList<>();
        
        try
        {
            // true to prefer the local file
            accs = parser.getMagList(true);
        }
        catch (Exception e)
        {
            if (isDebug())
            {
                this.logf("layout.noDCC");
            }
        }

        // Fetch and parse FIRST, then swap, so layoutDB is empty only for a loop over parsed objects, not for the HTTP
        // requests of every page.
        List<LayoutDiagram> parsed = parser.parseLayout(accs);

        // The page counts are asked once, here, of the same parser, and carried.
        final int couldNotBeRead = parser.getPagesThatCouldNotBeRead();

        final int namedByTheIndex = parser.getPagesTheIndexNamed();

        // Nothing read is still a failure: syncLayoutsFromConfiguredSource reverts to the Central Station in a catch,
        // and a page that will not read comes back as a blank stand-in, so neither an exception nor an empty list says
        // that every page failed.  Count the pages that actually read, and throw BEFORE clearLayouts so the diagram on
        // screen survives.  An empty folder fails no pages and comes through as the empty layout it is.
        int actuallyRead = 0;

        for (LayoutDiagram l : parsed)
        {
            if (!l.isUnreadable()) actuallyRead++;
        }

        // Asked against what the index said there was: a parser that does not recognize the index's block name (a
        // capitalized `Seite`) returns zero pages and zero failures, yet the index names pages.

        if (actuallyRead == 0 && (couldNotBeRead > 0 || namedByTheIndex > 0))
        {
            throw new Exception(I18n.f("layout.errorNoPageCouldBeRead",
                Math.max(couldNotBeRead, namedByTheIndex)));
        }

        this.clearLayouts();

        // The addresses with tiles of both kinds: CAN does not care whether a switch or a signal, so every tile there
        // shares the accessory the database holds rather than re-creating it for each tile of the other kind (OB-405)
        Map<Integer, Set<Boolean>> kinds = new HashMap<>();

        for (LayoutDiagram l : parsed)
        {
            for (LayoutDiagramComponent c : l.getAll())
            {
                if (c.isAccessory())
                {
                    int uid = MarklinAccessory.UIDfromAddress(c.getAddress() - 1, c.getProtocol());
                    kinds.computeIfAbsent(uid, k -> new HashSet<>()).add(c.isSignal());

                    if (c.isThreeWay())
                    {
                        kinds.computeIfAbsent(uid + 1, k -> new HashSet<>()).add(c.isSignal());
                    }
                }
            }
        }

        this.mixedAddresses = kinds.keySet().stream().filter(uid -> kinds.get(uid).size() > 1).collect(Collectors.toSet());

        List<String> fileOrder = new ArrayList<>();

        for (LayoutDiagram l : parsed)
        {
            this.layoutDB.add(l, l.getName(), l.getName());

            fileOrder.add(l.getName());

            this.logf("layout.imported", l.getName());

            // Each component wired to what it stands for (parsing does not wire anything); see wireComponents.
            this.wireComponents(l, feedbackAddresses);
        }

        this.layoutOrder = fileOrder;

        attachLinks(parser);
        
        // Prune stale feedback, but only when the whole railway was read: a page that failed to load would take the
        // sensors seen only on it, and the autonomy points watching them, out of LocDB.data for good.
        if (couldNotBeRead > 0)
        {
            this.logf("layout.pruningSkippedPageUnreadable", couldNotBeRead);
        }
        else if (!feedbackAddresses.isEmpty())
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
     * Delays until the power state matches, or until the Central Station has had long enough to answer.  BOUNDED,
     * unlike a wait for a sensor: this is the acknowledgment of a command just sent, and it comes back in milliseconds
     * or not at all.  Only the inbound GO/STOP echo releases the wait, and a datagram to a station that is off succeeds
     * and vanishes; the tile handler waits on one shared thread, so an untimed wait would freeze every track diagram
     * tile until a restart.
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
       
    /**
     * The monitor the three autonomy-layout doors share: its own, not the model's, because getAutoLayout is asked
     * constantly and the model's monitor is held by other work (changeLocAddress, the id-cache rebuild), which would
     * put the event thread behind it.
     */
    private final Object autoLayoutLock = new Object();

    /**
     * Run between parsing a configuration and the model taking it, when set - the seam a test lands a lookup in.
     * Null in the application.
     */
    public static volatile Runnable whileParsingForTest;

    @Override
    public boolean hasAutoLayout()
    {
        synchronized (this.autoLayoutLock)
        {
            return this.autoLayout != null;
        }
    }
    
    @Override
    public Layout getAutoLayoutIfLoaded()
    {
        synchronized (this.autoLayoutLock)
        {
            return this.autoLayout;
        }
    }

    /**
     * Returns the auto layout class (and creates it if it does not yet exist)
     * @return 
     */
    @Override
    public Layout getAutoLayout()
    {
        synchronized (this.autoLayoutLock)
        {
        // SYNCHRONIZED, AND READ ONCE: a route thread asks hasAutoLayout() and then this, and the event thread could
        // clear the layout between the two; clearAutoLayout takes the same monitor.
            if (this.autoLayout == null)
            {
                this.autoLayout = new Layout(this);
            }

            return this.autoLayout;
        }
    }
    
    /**
     * Forgets the automation graph entirely, so that nothing is loaded: the way back out of having autonomy, since
     * everything else replaces one graph with another.  Whatever is moving is stopped first.
     */
    @Override
    public void clearAutoLayout()
    {
        Layout going;

        synchronized (this.autoLayoutLock)
        {
            going = this.autoLayout;
            this.autoLayout = null;
        }

        // STOPPED OUTSIDE THE LOCK: stopLocomotives talks to the railway and waits on it, and nothing may queue behind
        // it.
        // RETIRED, as a load retires the railway it replaces: a path's thread on the railway dropped stops its train at
        // its next check, rather than drive on a train already stopped.
        Layout.retireEveryLayout();

        if (going != null)
        {
            going.invalidate();
            going.stopLocomotives();
        }
    }

    /**
     * Parses JSON corresponding to a layout automation config file
     * Resets any existing automation
     * @param s
     */
    @Override
    public void parseAuto(String s)
    {        
        // What the OPERATOR chose, kept across the rebuild: the timetable capture button lives on the Layout, and the
        // setup rebuilds often (diagram edits, placing a locomotive, loading a configuration) without repainting it.
        // Read before the old layout is discarded, applied after the new one exists.
        boolean wasCapturing = this.autoLayout != null && this.autoLayout.isTimetableCapture();

        // AND WHERE THE TRAINS HAVE BEEN: `lastArrival` (read by LEAST_RECENTLY_VISITED) lives on the Layout, and the
        // history belongs to the session, not to a Layout.
        java.util.Map<String, Long> wasVisited =
            this.autoLayout != null ? this.autoLayout.getVisitHistory() : null;

        if (this.autoLayout != null)
        {
            this.autoLayout.invalidate();
            this.autoLayout.stopLocomotives();
        }
        
        Layout parsed = Layout.fromJSON(s, this);

        if (whileParsingForTest != null) whileParsingForTest.run();

        // TAKEN UNDER THE LOCK, AND MADE THE CURRENT ONE, so a lookup during the parse cannot build a railway newer
        // than the one parsed.
        synchronized (this.autoLayoutLock)
        {
            this.autoLayout = parsed;

            if (parsed != null) parsed.makeCurrent();
        }

        if (this.autoLayout != null)
        {
            this.autoLayout.setTimetableCapture(wasCapturing);
            this.autoLayout.restoreVisitHistory(wasVisited);
        }

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
        // Snapshot the locomotive list once - it does not change across the day-by-day loop,
        // so there is no need to allocate a fresh copy on every iteration.
        List<Locomotive> locs = this.getLocomotives();

        return dailyStats(days, offset, key -> locs.stream().mapToLong(loco -> loco.getRuntimeOnDay(key)).sum());
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
        // Snapshot the locomotive list once - it does not change across the day-by-day loop.
        List<Locomotive> locs = this.getLocomotives();

        return dailyStats(days, offset, key -> locs.stream().mapToInt(loco -> loco.getRuntimeOnDay(key) > 0 ? 1 : 0).sum());
    }
    
    /**
     * One figure for each calendar day, newest first, from the current date less the offset.  Stepped a calendar day
     * at a time: a day is 23 or 25 hours across a DST transition, so a fixed step could repeat or skip a date.
     * @param days
     * @param offset
     * @param figure the figure for a day, given its yyyy-MM-dd key as Locomotive.getDate produces it
     * @return 
     */
    private <T> TreeMap<String, T> dailyStats(int days, long offset, Function<String, T> figure)
    {
        LocalDate day = LocalDate.now().minusDays(offset);

        TreeMap<String, T> stats = new TreeMap<>(Comparator.reverseOrder());

        for (int i = 0; i < Math.abs(days); i++)
        {
            // ISO_LOCAL_DATE is yyyy-MM-dd, the same key Locomotive.getDate produces
            final String key = day.format(DateTimeFormatter.ISO_LOCAL_DATE);

            stats.put(key, figure.apply(key));

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
        // Calendar days, not fixed 86400000 ms steps - see dailyStats (a skipped date would drop a
        // day's locomotives from the count).
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
     * The locomotives whose run time counts on its own: a multi-unit, or a locomotive linked to others, records a run
     * of its own while its members record theirs, so the Stats table leaves it out of its totals and so does the chart.
     */
    private List<Locomotive> locomotivesRunningOnTheirOwn()
    {
        List<Locomotive> own = new ArrayList<>();

        for (Locomotive l : this.getLocomotives())
        {
            if (l.getDecoderType() != Locomotive.decoderType.MULTI_UNIT && !l.hasLinkedLocomotives()) own.add(l);
        }

        return own;
    }

    /** The history's keys for each day from `first` to `last`, formatted once for every locomotive to look up. */
    private static List<String> dayKeys(LocalDate first, LocalDate last)
    {
        List<String> keys = new ArrayList<>();

        for (LocalDate day = first; day != null && last != null && !day.isAfter(last); day = day.plusDays(1))
        {
            keys.add(day.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        return keys;
    }

    @Override
    public long getRuntimeBetween(LocalDate first, LocalDate last)
    {
        List<Locomotive> own = locomotivesRunningOnTheirOwn();

        long total = 0;

        for (String key : dayKeys(first, last))
        {
            for (Locomotive l : own) total += l.getRuntimeOnDay(key);
        }

        return total;
    }

    @Override
    public int getLocsRunBetween(LocalDate first, LocalDate last)
    {
        List<Locomotive> own = locomotivesRunningOnTheirOwn();

        Set<Locomotive> ran = new HashSet<>();

        for (String key : dayKeys(first, last))
        {
            for (Locomotive l : own)
            {
                if (l.getRuntimeOnDay(key) > 0) ran.add(l);
            }
        }

        return ran.size();
    }

    @Override
    public List<Map.Entry<String, Long>> getRuntimePerLocBetween(LocalDate first, LocalDate last)
    {
        List<Map.Entry<String, Long>> ran = new ArrayList<>();

        List<String> keys = dayKeys(first, last);

        for (Locomotive l : locomotivesRunningOnTheirOwn())
        {
            long total = 0;

            for (String key : keys) total += l.getRuntimeOnDay(key);

            if (total > 0) ran.add(new java.util.AbstractMap.SimpleImmutableEntry<>(l.getName(), total));
        }

        ran.sort((a, b) -> a.getValue().equals(b.getValue()) ? a.getKey().compareTo(b.getKey())
            : Long.compare(b.getValue(), a.getValue()));

        return ran;
    }

    /**
     * No run is older than TrainControl, whose first commit was in December 2018: an earlier day, or one after today, was
     * written by a PC whose clock was not set, and would start the chart's All range decades back.
     */
    private static final String FIRST_POSSIBLE_RUN_DAY = "2018-01-01";

    @Override
    public LocalDate getEarliestRunDay()
    {
        String earliest = null;
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        for (Locomotive l : locomotivesRunningOnTheirOwn())
        {
            for (Map.Entry<String, Long> day : l.getHistoricalOperatingTime().entrySet())
            {
                String key = day.getKey();

                // yyyy-MM-dd sorts as text in date order
                if (day.getValue() > 0 && key.length() == 10 && key.compareTo(FIRST_POSSIBLE_RUN_DAY) >= 0
                    && key.compareTo(today) <= 0 && (earliest == null || key.compareTo(earliest) < 0))
                {
                    earliest = day.getKey();
                }
            }
        }

        try
        {
            return earliest == null ? null : LocalDate.parse(earliest);
        }
        catch (java.time.format.DateTimeParseException e)
        {
            return null;
        }
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

        // Indexed once, not rebuilt for every parsed locomotive.
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

        // The same ambiguity exists on the Central Station side: two parsed locomotives at one address both match the
        // single local locomotive, so the list would get two proposals with one source.
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

            // Duplicates at one address are legitimate (the database is keyed by name AND address), but the station has
            // one name for it: the rename flow deletes whatever holds the target name, so a second rename would delete
            // the locomotive the first had just renamed.
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
     * Orders rename proposals so each one is applied only once its target name is free, and drops any that form a
     * cycle.
     *
     * The consumer applies entries one at a time, deleting whatever already holds a target name, so order matters:
     *
     * - A CHAIN (the Central Station renamed X to C and the old C to D) is safe only if "C to D" comes first, which
     *   frees the name; the other order destroys the middle locomotive.
     * - A SWAP (X and C exchange names) is safe in NO order: either entry deletes the locomotive the other was about to
     *   rescue.  Unwinding needs a temporary name this flow cannot offer, so the cycle is refused.
     *
     * Afterwards a deletion in the consumer can only hit a name held by something with no pending rename of its own.
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
     * The locomotives a Central Station sync changed while autonomy ran, by name, swept by the first sync that finds it
     * stopped (OB-303): a multi-unit's new members are taken at once, unlike an address change, which is held back.
     */
    private final java.util.Set<String> sweepsHeldBackByARun =
        java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<String>());

    /** How many of a railway's points hold a train, so a sweep can tell whether it took one off (OB-303). */
    private static int trainsStandingOn(Layout layout)
    {
        int standing = 0;

        for (org.traincontrol.automation.Point point : layout.getPoints())
        {
            if (point.getCurrentLocomotive() != null) standing++;
        }

        return standing;
    }

    /** What the last sync failed with, in words, or null when it did not fail. */
    private volatile String lastSyncError = null;

    /** What the last sync failed with (OB-338), or null when it did not fail. */
    public String getLastSyncError()
    {
        return this.lastSyncError;
    }

    /** Whether the last sync left part of the Central Station's data as it was because it could not be read (FR-169). */
    private volatile boolean lastSyncPartial = false;

    /** Whether the last sync could not read part of what the Central Station sent, and left that part as it was. */
    @Override
    public boolean wasLastSyncPartial()
    {
        return this.lastSyncPartial;
    }

    /**
     * Syncs, and while the sync fails asks whether to try again (OB-338).  The first sync is the only thing that brings
     * communication up, so a failure would leave no way to connect.  With nobody to ask the sync is made once.
     *
     * @param sync the sync; negative when it failed
     * @param lastError the reason the sync that just failed gave
     * @param asker given that reason, true to sync again and false to exit; null when nothing can be asked
     * @param exit what to do when the answer is to exit
     * @return the last sync's result, or -1 when the answer was to exit
     */
    public static int syncUntilConnected(IntSupplier sync, Supplier<String> lastError, Predicate<String> asker, Runnable exit)
    {
        int result = sync.getAsInt();

        while (result < 0 && asker != null)
        {
            if (!asker.test(lastError.get()))
            {
                exit.run();

                return -1;
            }

            result = sync.getAsInt();
        }

        return result;
    }

    /**
     * Who to ask when the first sync fails (OB-338): somebody only when there is a Central Station and a window to ask
     * in; a simulation's sync is expected to fail and must never raise a question.
     *
     * @param address the Central Station's address
     * @param simulate whether this is a simulation
     * @param view the window, or null when there is none
     * @return the question for the user, or null when nothing is to be asked
     */
    public static Predicate<String> resyncAsker(String address, boolean simulate, View view)
    {
        if (simulate || view == null || address == null || address.trim().isEmpty()) return null;

        return view::offerResync;
    }

    /**
     * Ends the application when the user gave up on the first sync (OB-338).  The saved address is forgotten, so the
     * next start asks for one; the exit code is the one the address prompt gives for leaving the start-up.
     */
    private static void exitUnconnected()
    {
        exitUnconnected(TrainControlUI.getPrefs(), () -> System.exit(1));
    }

    /**
     * Forgets the saved address in the given preferences, then runs the exit (OB-338).  Both are arguments so a claim
     * can run Exit without ending the JVM or touching the operator's preferences; the exit runs even when the
     * preferences refuse the removal.
     *
     * @param prefs where the address is saved
     * @param exit what ends the application
     */
    public static void exitUnconnected(java.util.prefs.Preferences prefs, Runnable exit)
    {
        try
        {
            prefs.remove(TrainControlUI.IP_PREF);
        }
        catch (Exception ex)
        {
            System.out.println(I18n.t("ui.errorUpdatingPreferences"));
        }

        // LEFT ON PURPOSE, from a question this version asked: an update waiting to hear from it is told it started, so
        // the user's own choice is not taken for a version that cannot start (FR-125).  Nothing unless an updater
        // started this run.
        org.traincontrol.util.SelfUpdate.reportStarted();

        exit.run();
    }

    /**
     * Whether a start leaves the saved Central Station address to be asked for again (OB-339): a start that did not
     * connect forgets it, but a simulation never connects and keeps it.
     *
     * @param simulate whether this start is a simulation
     * @param connected whether communication with the Central Station came on
     * @return true to forget the saved address
     */
    public static boolean forgetsTheAddress(boolean simulate, boolean connected)
    {
        return !simulate && !connected;
    }

    /** A failure in a few words: what kind it was, and what it said. */
    private static String describe(Exception failure)
    {
        String said = failure.getMessage();
        String kind = failure.getClass().getSimpleName();

        return said == null || said.trim().isEmpty() ? kind : kind + ": " + said.trim();
    }

    /**
     * Synchronizes CS2 state
     * @return 
     */
    @Override
    public final int syncWithCS2()
    {        
        this.lastSyncError = null;
        this.lastSyncPartial = false;

        // Read remote config files
        this.fileParser = new CS2File(cs2Address(), this);
             
        this.logf("log.csDBSyncStarting");

        int num = 0;

        // Each locomotive the pass below re-addresses or gives other Central Station members, swept at the end
        final List<MarklinLocomotive> sweptAfterTheSync = new ArrayList<>();
        
        // Fetch Central Station databases
        try
        {            
            // Debug only, and only the first N times: caught below exactly as a real failure is (OB-338)
            if (this.isDebug())
            {
                int before = this.failFirstSyncsLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0);

                if (before > 0)
                {
                    throw new IOException("Simulated sync failure for testing (traincontrol.failFirstSyncs), " + (before - 1) + " more to come");
                }
            }

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
            
            // Import routes
            List<MarklinRoute> parsedRoutes;

            if (this.isCS3)
            {
                // The firmware is read once for the whole sync, by the parser (OB-403, FR-170).  A CS3 answer that cannot
                // be read leaves the routes, or the locomotives below, as they are, and the sync goes on (FR-169).
                try
                {
                    parsedRoutes = fileParser.parseRoutesCS3();
                }
                catch (UnreadableAnswerException e)
                {
                    this.logf("route.importSkippedUnreadable");
                    this.lastSyncPartial = true;
                    parsedRoutes = null;
                }

                this.adoptCS3AccessoryStates();
            }
            else
            {
                parsedRoutes = fileParser.parseRoutes();
            }
            
            // Unlock all routes in case they have been deleted; one the Central Station has but that would not read stays
            // as it is
            // With a route that would not read and has no id to keep it by, none can be told from a deleted one
            boolean canTellDeleted = !this.isCS3 || !fileParser.hasUnreadableRouteWithoutId();

            for (MarklinRoute r : parsedRoutes == null || !canTellDeleted ? Collections.<MarklinRoute>emptyList() : this.getRoutes())
            {
                if (!this.isCS3 || !fileParser.getUnreadableRouteIds().contains(r.getId())) r.setLocked(false);
            }
            
            // Import routes, one step each - see adoptCentralStationRoute
            for (MarklinRoute r : parsedRoutes == null ? Collections.<MarklinRoute>emptyList() : parsedRoutes)
            {
                if (this.adoptCentralStationRoute(r)) num++;
            }
            
            // Import locomotives
            List<MarklinLocomotive> parsedLocs;
            
            if (this.isCS3)
            {
                try
                {
                    parsedLocs = fileParser.parseLocomotivesCS3();
                }
                catch (UnreadableAnswerException e)
                {
                    this.logf("loc.importSkippedUnreadable");
                    this.lastSyncPartial = true;
                    parsedLocs = new ArrayList<>();
                }

                // Records of either list that would not read were skipped, each with a warning of its own
                this.lastSyncPartial |= fileParser.getUnreadableRecords() > 0;
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
                this.adoptCentralStationAddress(l, sweptAfterTheSync);
                
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
                              
                // Always adopt the remote icon, even while a local override exists: the override has its own
                // field and getImageURL falls back to this one, so skipping would starve the fallback (a custom
                // icon restored from the database starts with no imageURL, and clearing the icon offline would
                // land on nothing).
                if (this.locDB.getById(l.getUID()) != null && l.getImageURL() != null)
                {
                    this.locDB.getById(l.getUID()).setImageURL(l.getImageURL());                         
                }
                
                // Set multi unit info
                if (this.locDB.getById(l.getUID()) != null)
                {
                    MarklinLocomotive held = this.locDB.getById(l.getUID());

                    boolean membersChanged = !java.util.Objects.equals(held.getModelMultiUnitLocomotiveNames(),
                        l.getModelMultiUnitLocomotiveNames());

                    held.setModelMultiUnitLocomotives(l.getModelMultiUnitLocomotiveNames());

                    if (membersChanged) sweptAfterTheSync.add(held);
                }
            }
        }
        catch (Exception e)
        {
            this.lastSyncError = describe(e);

            this.logf("loc.dbSyncFailed");
            this.log(e);
             
            return -1;
        }
        
        this.rebuildLocIdCache();

        // AND THE GRAPH ASKED WHAT THE SYNC MADE CONFLICT.  The sync writes a locomotive's address and is the door a
        // Central Station multi-unit's members come in by, so it asks the graph as the window's edit doors do
        // (OB-303).  Not while autonomy runs: what arrives then is remembered and swept later (`sweepsHeldBackByARun`).
        // Asked once, not built: the event thread can clear the railway between two questions
        final Layout loadedNow = this.getAutoLayoutIfLoaded();

        if (this.isAutonomyRunning())
        {
            // HELD BACK, AND REMEMBERED (OB-303) - see `sweepsHeldBackByARun`
            for (MarklinLocomotive changed : sweptAfterTheSync) this.sweepsHeldBackByARun.add(changed.getName());
        }
        else
        {
            // AND EVERY ONE A RUN HELD BACK, by the name the database has it under now (OB-303)
            final List<MarklinLocomotive> toSweep = new ArrayList<>(sweptAfterTheSync);

            synchronized (this.sweepsHeldBackByARun)
            {
                for (String name : this.sweepsHeldBackByARun)
                {
                    MarklinLocomotive held = this.locDB.getByName(name);

                    if (held != null && !toSweep.contains(held)) toSweep.add(held);
                }

                this.sweepsHeldBackByARun.clear();
            }

            boolean tookATrainOff = false;

            if (!toSweep.isEmpty() && loadedNow != null)
            {
                Layout layout = loadedNow;

                synchronized (layout)
                {
                    int standing = trainsStandingOn(layout);

                    for (MarklinLocomotive changed : toSweep) layout.sanitizeMultiUnits(changed);

                    tookATrainOff = trainsStandingOn(layout) < standing;
                }
            }

            // AND REDRAWN WHERE IT TOOK A TRAIN OFF (OB-303), as the window's doors redraw after the same sweep.
            // Outside the railway's monitor, because the redraw runs every listener the railway has.
            if (tookATrainOff) loadedNow.refreshUI();
        }

        // The sensors' true states, asked of a CS3 only: a query sets nothing on the station (OB-418)
        if (this.isCS3 && this.NetworkInterface != null)
        {
            this.NetworkInterface.knowDevices(this.fileParser.parseS88DevicesCS3(), this.savedS88Devices);
            this.NetworkInterface.syncSensors();
        }

        this.logf("loc.syncCompleted");
        
        return num;
    }
    
    /**
     * Takes one route the Central Station describes into this database: added where new, replaced where its definition
     * changed, and locked either way - except that a route running now is left for the next sync.  One step of
     * `syncWithCS2`'s route pass, a method of its own so the decision can be asked without a station.
     *
     * @param r the route as the Central Station describes it
     * @return true where a route was added, new or replaced, so the sync counts it
     */
    public boolean adoptCentralStationRoute(MarklinRoute r)
    {
        // Set only where this step deletes the same route to re-read it; a route arriving for
        // the first time was never in the selection and must not be put into it.
        boolean wasActivated = false;

        // Other existing route with same name but different ID
        if (this.routeDB.hasName(r.getName()) && r.getId() != this.routeDB.getByName(r.getName()).getId())
        {
            // NOT WHILE IT RUNS - see below.  The station's route cannot be added beside it under the same name, so
            // it waits for the next sync as well.
            if (this.routeDB.getByName(r.getName()).isExecuting())
            {
                this.logf("route.syncLeftRunningRoute", r.getName());

                return false;
            }

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
            // NOT WHILE IT RUNS (in the sync).  This replaces by deleting the route and adding the station's under the
            // same id - the delete-and-add `editRoute` refuses while the route runs, because what a route is doing
            // lives on its object (`isExecuting`).  Nobody can be asked to wait, so the route is left as is, still
            // locked below, and the log says so; the next sync finds the same difference.
            if (this.routeDB.getById(r.getId()).isExecuting())
            {
                this.logf("route.syncLeftRunningRoute", this.routeDB.getById(r.getId()).getName());
            }
            else
            {
                this.logf("route.deletingDuplicateId", this.routeDB.getById(r.getId()).getName());

                // The SAME route, re-read from the Central Station because something about it
                // changed - so the operator's autonomy selection survives it.
                wasActivated = this.isRouteActivatedByAutonomy(r.getId());

                this.deleteRoute(this.routeDB.getById(r.getId()).getName());
            }
        }

        boolean added = false;

        if (!this.routeDB.hasId(r.getId()))
        {
            // Only report and count the route if it was actually added
            if (newRoute(r))
            {
                this.restoreRouteActivation(r.getId(), wasActivated);

                this.logf("route.added", r.getName());

                added = true;
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

        return added;
    }

    /**
     * Gives a locomotive this database already has the address the Central Station reports for it: nothing happens
     * unless a locomotive of that name, with the same decoder type, is at a different address.  One step of
     * `syncWithCS2`'s locomotive pass, a method of its own so the decision can be asked without a station.
     *
     * @param l the locomotive as the Central Station describes it
     * @param sweptAfterTheSync where a locomotive re-addressed here is recorded, for the sweep at the end of the sync
     */
    public void adoptCentralStationAddress(MarklinLocomotive l, List<MarklinLocomotive> sweptAfterTheSync)
    {
        if (this.locDB.getByName(l.getName()) != null
            && this.locDB.getByName(l.getName()).getAddress() != l.getAddress()
            && this.locDB.getByName(l.getName()).getDecoderType() == l.getDecoderType()
        )
        {
            // Deferred while anything is running (OB-287): setAddress changes which decoder this locomotive commands,
            // so applying it mid-run sends every later command to a different engine while the graph tracks this one;
            // a sync is triggered from a dozen places, so the check belongs here.  AND WHILE A ROUTE DRIVES IT: a route
            // sends commands by name with delays between, so an adopted address would send the rest elsewhere.
            if (this.isAutonomyRunning() || this.runningRouteDriving(l.getName()) != null)
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

                // AND THE CACHE FOLLOWS THE NEW UID AT ONCE.  The rebuild at the end of the sync is on
                // the success path only, and until it runs every echo for this locomotive resolves to nothing.
                this.rebuildLocIdCache();

                this.logf("loc.addressUpdated",
                    existingLoc.getName(),
                    oldAddr,
                    this.getLocAddress(existingLoc.getName()));

                sweptAfterTheSync.add(existingLoc);

                // The same repair changeLocAddress performs, for the same reason
                for (Locomotive other : getLocomotives())
                {
                    if (other.hasLinkedLocomotives())
                    {
                        // ONE CALL: the two-call form stages on an instance field, and this loop runs off the event
                        // thread while the Multi-Unit tab can be staging on it.
                        other.setLinkedLocomotives(other.getLinkedLocomotiveNames());
                    }
                }
            }
        }
    }

    /**
     * Deletes the current layout from the model
     *
     * Takes layoutRefreshLock because this is a third entrance to the emptied-database state that
     * syncLayoutsFromConfiguredSource guards: "Switch to CS Layout" calls this directly, from the EDT,
     * before its own syncWithCS2().  Without it this could delete from layoutDB while a background diagram-save refresh
     * was repopulating it (plain HashMaps).
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

        // Entering debug mode reads the switch, so that it counts syncs from this start
        if (state) this.failFirstSyncsLeft.set(failFirstSyncsFrom(System.getProperty(FAIL_FIRST_SYNCS)));
    }

    /**
     * The count the traincontrol.failFirstSyncs property asks for: a positive integer, and anything else is 0 (OB-338).
     *
     * @param value the property's value, or null when it is not set
     * @return how many syncs to fail
     */
    private static int failFirstSyncsFrom(String value)
    {
        if (value == null) return 0;

        try
        {
            return Math.max(0, Integer.parseInt(value.trim()));
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
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
     * A sensor's s88 device to save: the one a CS3's real event showed, else the one saved before; never one only the
     * station's device list gave, as the list is read again at each sync (OB-418).
     */
    private Integer s88DeviceToSave(int contact)
    {
        Integer device = this.isCS3 && this.NetworkInterface != null
            ? this.NetworkInterface.learnedDeviceOf(contact) : null;

        return device != null ? device : this.savedS88Devices.get(contact);
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
            l.add(new MarklinSimpleComponent(f, s88DeviceToSave(f.getUID())));
        }
        
        // The database that would not load is copied aside first, once, and said out loud: a transient read failure at
        // startup plus a normal exit would otherwise save an empty database over every customization.  The mark is
        // cleared only once the copy exists; until then the next save tries again.
        if (!backup && this.databaseLoadFailed)
        {
            try
            {
                File kept = Util.keepUnreadableCopy(MarklinControlStation.DATA_FILE_NAME);

                if (kept != null) this.logf("log.databaseUnreadableKept", kept.getAbsolutePath());

                // Kept - or nothing is left to keep
                this.databaseLoadFailed = false;
            }
            catch (IOException | RuntimeException keepFailed)
            {
                this.log(keepFailed);

                this.logf("log.databaseSaveFailed", keepFailed.toString());

                return;
            }
        }

        // Backups go into a dedicated folder (falling back to the current directory if it can't be created)
        String path = backup
            ? Util.getBackupPath(prefix + MarklinControlStation.DATA_FILE_NAME)
            : Util.dataPath(prefix + MarklinControlStation.DATA_FILE_NAME);

        // Staged through a sibling file and moved into place, so that dying part way through the write
        // leaves the previous database intact rather than a truncated one.  This is the only automatic
        // save of the locomotive database, and an unreadable one reads as a first launch.  try-with-resources
        // inside still matters: without close() the final buffered block never reaches the staging file.
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
     * Opens a locomotive database with the reader `restoreState` uses, which resolves the class names older versions
     * wrote.  Reading through it loads nothing, logs nothing and leaves `isDatabaseLoadFailed` alone.
     *
     * @param in the database's bytes
     * @return the reader
     * @throws IOException if the stream's header cannot be read
     */
    public ObjectInputStream databaseReader(InputStream in) throws IOException
    {
        return new CustomObjectInputStream(in);
    }

    /**
     * Whether the last restoreState found a database file it could not read (false for a first launch, with nothing to
     * lose); saveState reads it to decide whether the file it is about to replace is worth a copy first.
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

        // THE STREAM IN ITS OWN RESOURCE, because the one wrapping it can throw before it exists: ObjectInputStream's
        // constructor throws on a corrupt header, so in a single resource the FileInputStream stays open and the
        // recovery fails, since writeAtomically's Files.move(REPLACE_EXISTING) cannot replace a file somebody has open
        // on Windows.  Buffered, as ObjectInputStream reads a byte at a time; the buffer holds no file handle.
        try (FileInputStream file_in = new FileInputStream(dataFile);
            ObjectInputStream obj_in = new CustomObjectInputStream(new BufferedInputStream(file_in)))
        {
            // Read an object
            Object obj = obj_in.readObject();

            if (obj instanceof List)
            {
                // Cast object
                instance = (List<MarklinSimpleComponent>) obj;

                this.logf("log.databaseLoadedFromFile");
            }
            else
            {
                // READ, AND NOT A DATABASE: a file that deserializes to something other than a list is as unreadable as
                // one that will not deserialize, and is marked the same way (see saveState), so the save on exit keeps
                // a copy.
                this.databaseLoadFailed = true;

                this.logf("log.databaseBadDataFile");
            }
        }
        catch (IOException iex)
        {
            // A file that is not there is a first launch; one that IS there and would not read decides whether the save
            // on exit may replace it - see saveState.
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

        // NO EMERGENCY STOP AMONG OTHER COMMANDS, refused before anything is deleted
        if (MarklinRoute.mixesAStop(route))
        {
            this.logf("route.errorStopAmongOtherCommands", name);
            return false;
        }

        // Refused before anything is deleted: this edits by deleting the route and adding a new object under the same
        // id, and what a route is doing (`isExecuting`) lives on its object; the thread that clears it holds the OLD
        // one.  Waiting loses nothing: a route runs the commands it started with (`execRoute` takes a copy).
        if (existing.isExecuting())
        {
            this.logf("route.errorEditRouteWhileRunning", name);
            return false;
        }

        String trimmedNewName = newName.trim();

        // Checked before anything is deleted: this method edits by delete-then-re-add, and newRoute refuses a name that
        // already belongs to another route, so a rename onto an existing name would delete the original and not add it
        // back.  RouteEditorFrame checks too; the model should not depend on one dialog to protect its data.
        if (!name.equals(trimmedNewName) && this.routeDB.hasName(trimmedNewName))
        {
            this.logf("route.notAdded", trimmedNewName);
            return false;
        }

        Integer id = existing.getId();

        // BEFORE THE DELETE STRIPS IT.  See isRouteActivatedByAutonomy for why the delete is
        // right to strip it and why this door has to put it back.
        final boolean wasActivated = this.isRouteActivatedByAutonomy(id);

        // AND THE LOCK, FOR THE SAME REASON: `newRoute` builds a fresh MarklinRoute whose `locked` defaults to false
        // (only the sync's import loop writes true), and the lock is what RightClickRouteMenu puts Change Route ID and
        // Delete behind.  Enable/Disable (via `writeRouteEnabledState`, also BulkEnableOrDisable) is deliberately not
        // gated on the lock, so without this an edit would unlock a Central Station route.
        final boolean wasLocked = existing.isLocked();
        
        // Disable the route so that the s88 condition stops firing
        existing.disable();
        
        this.deleteRoute(name);
        
        if (!this.newRoute(trimmedNewName, id, route, s88, s88Trigger, routeEnabled, conditions))
        {
            this.logf("route.notAdded", trimmedNewName);
            return false;
        }
        
        // And back into the autonomy selection, which the delete above took it out of.
        this.restoreRouteActivation(id, wasActivated);

        // And the lock, onto the route that now holds this id.
        if (wasLocked && this.routeDB.getById(id) != null)
        {
            this.routeDB.getById(id).setLocked(true);
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
     * Every locomotive address in the database, mapped to the locomotives using it.  Separate from
     * getDuplicateLocAddresses, which has every single-user address removed.
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
     * Whether a route id is one this application allocated rather than one the station could know about (OB-155):
     * newRoute hands out ids from ROUTE_STARTING_ID upwards, so a route at or above it was never told to the station.
     * It says what an id could collide with, not where a route came from: whether the station carried a route is the
     * route's lock, which only the sync's import sets (MT-467).
     *
     * @param id the route id in question
     * @return true when no sync could tell us anything about it
     */
    public static boolean isLocalRouteId(int id)
    {
        return id >= ROUTE_STARTING_ID;
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
            // AND THIS ARM RETIRES THE MONITOR TOO: a route is armed by its own constructor, so a refused route would
            // otherwise keep watching its sensor.
            if (r != null) r.disable();

            return false;
        }

        if (!this.routeDB.hasId(r.getId()) && !this.routeDB.hasName(r.getName().trim()))
        {
            this.routeDB.add(r, r.getName().trim(), r.getId());

            // AND THE TILES FOLLOW THE DATABASE: a route tile runs the OBJECT it was wired to, so
            // every add, replace or delete has to re-point it or the diagram keeps firing the old one.
            rebindRouteTiles();
            return true;
        }
        else
        {
            // Retire the monitor of the route we are refusing: MarklinRoute's constructor starts one as soon as the
            // route is enabled with an s88, so a rejected route would otherwise fire turnouts and speeds with no UI
            // handle on it.
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

            // And the tiles follow the database: see newRoute(MarklinRoute).
            rebindRouteTiles();    
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
        // NO EMERGENCY STOP AMONG OTHER COMMANDS - the route editor says so before it gets here
        if (MarklinRoute.mixesAStop(route))
        {
            this.logf("route.errorStopAmongOtherCommands", name);
            return false;
        }

        int newId = ROUTE_STARTING_ID;
        
        if (this.routeDB.hasId(newId))
        {
            newId = Collections.max(this.routeDB.getItemIds()) + 1;
        }
        
        name = name.trim();
        
        if (!this.routeDB.hasName(name))
        {
            this.routeDB.add(new MarklinRoute(this, name, newId, route, s88, s88Trigger, routeEnabled, conditions), name, newId);

            // And the tiles follow the database: see newRoute(MarklinRoute).
            rebindRouteTiles();  
                        
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
     * A signal and a switch are one physical decoder, but only the name the accessory was created as is registered;
     * without the fallback a route or autonomy edge naming the other one never commands it.  Only the type prefix is
     * swapped.  Never creates anything.
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
        // IN THE DATABASE BEFORE IT PARSES ITS FIRST MESSAGE: the constructor's parse wakes everything waiting on a
        // sensor, and those waiters read the database.  Added afterwards, a waiter would find no such sensor and sleep
        // until the next event anywhere; a route monitor waits untimed and would stay parked.  So: built empty, added,
        // then told.
        MarklinFeedback newFb = new MarklinFeedback(this, id, null);

        this.feedbackDB.add(newFb, newFb.getName(), newFb.getUID());

        if (message != null) newFb.parseMessage((CS2Message) message);

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
        
    @Override
    public List<Integer> knownSensors()
    {
        return this.feedbackDB.getItemIds();
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

        boolean duplicate;
        int number;

        synchronized (this)
        {
            // CS3 seems to send response packets twice.  Ignore the second, within a window: the duplicate follows by
            // milliseconds, while an identical packet an hour later is a second real command and must not be dropped.
            duplicate = lastPacket != null &&
                    (message.isAccessoryCommand() || message.isLocCommand() || message.isFeedbackCommand()) &&
                    message.equals(lastPacket)
                    && (System.nanoTime() - this.lastPacketAt) < DUPLICATE_WINDOW_NS;

            if (!duplicate)
            {
                this.lastPacketAt = System.nanoTime();

                numMessagesProcessed +=1;

                lastPacket = message;
            }

            number = numMessagesProcessed;
        }

        // Prints out each message, formatted outside the lock (OB-421)
        if (this.debug && DEBUG_LOG_NETWORK)
        {
            if (duplicate)
            {
                this.logf("network.skippingDuplicatePacket", message.toString());
            }
            else
            {
                this.log(number + " " + message.toString());
            }
        }

        if (duplicate) return;
                
        // Send the message to the appropriate listener
        if (message.isFeedbackCommand())
        {
            this.feedbackMessageProcessor.execute(() ->
            {
                int id = message.extractShortUID();

                // Resolved once into a local, as the locomotive and accessory branches do: hasId then getById would be
                // two lock acquisitions, and syncLayouts prunes feedbacks on another thread; the NPE would end the task
                // and reach the thread's handler, not the message.  A dropped feedback is not cosmetic: the state is a LEVEL, and a driving
                // thread waits on it without a timeout, so a train whose arrival was dropped never slows and never
                // stops.
                MarklinFeedback feedback = this.feedbackDB.getById(id);

                if (feedback != null)
                {
                    feedback.parseMessage(message);
                }
                // A DEVICE IS NOT CREATED FROM A FRAME THE PARSER WILL NOT READ: isFeedbackCommand answers for three
                // commands, but MarklinFeedback.parseMessage acts only on 0x11 with an 8-byte frame.  `id` is bytes 2
                // and 3 of the payload, which for the others is not a sensor number, so creating a module from such a
                // frame would persist a phantom module (saveState).  An existing module is still handed every frame:
                // parseMessage does its own checking and this must not become a second, disagreeing one.
                else if (message.getCommand() == CS2Message.CMD_ACC_SENSOR && message.getLength() == 8)
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

                // Built on demand, as exec() does: the cache is filled only by a SUCCESSFUL sync while the UDP reader
                // starts regardless, so this could be null and the NPE would surface nowhere (every update dropped in
                // silence).
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

                            // Null if the locomotive was deleted since locIdCache was read; the NPE would drop the
                            // whole message in silence, including the updates for the other locomotives on this UID.
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

                // Resolved once into a local, as the locomotive branch does: restoreState deletes accessories with an
                // invalid address while the CAN listener is running, and the NPE would be swallowed by the executor's
                // Future.
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
        else if (message.isSysCommand() && (message.getSubCommand() == CS2Message.CMD_SYSSUB_GO
            || message.getSubCommand() == CS2Message.CMD_SYSSUB_STOP
            || message.getSubCommand() == CS2Message.CMD_SYSSUB_OVERLOAD))
        {
            this.locMessageProcessor.submit(() ->
            {
                boolean on = message.getSubCommand() == CS2Message.CMD_SYSSUB_GO;

                // An overload is the station cutting the track power by itself, after a short circuit, with no Stop
                // (OB-426); its channel is the output that tripped.
                boolean overload = message.getSubCommand() == CS2Message.CMD_SYSSUB_OVERLOAD;

                this.setPowerState(on);
                if (on) this.powerCutWarned = false;

                // For correctly tracking locomotive stats
                for (MarklinLocomotive l : this.locDB.getItems())
                {
                    l.notifyOfPowerStateChange(on);
                }

                if (this.view != null) this.view.updatePowerState();

                if (overload)
                {
                    int channel = message.getLength() >= 6 ? message.getData()[5] & 0xFF : 0;

                    this.logf("log.powerOffOverload", channel);

                    // Once per cut, whether or not a Stop came first: a short repeats the message while it lasts
                    if (!this.powerCutWarned && this.view != null) this.view.showPowerCut(channel);
                    this.powerCutWarned = true;
                }
                else
                {
                    this.logf(on ? "log.powerOn" : "log.powerOff");
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
        // Told first, whether or not the message is then sent - see `setSentMessageObserver`.
        java.util.function.Consumer<CS2Message> observer = this.sentMessageObserver;

        if (observer != null) observer.accept(m);

        if (on)
        {
            this.NetworkInterface.sendMessage(m);
        }
        else
        {
            // A discarded SYSTEM command is said out loud whether or not debugging is on: Stop, go and emergency stop
            // return void, so pressing Stop while not connected would otherwise do nothing and report nothing.
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
     * Sets who is told about every command handed to `exec`, whether or not it is then sent (MT-359).
     *
     * @param observer the listener, or null to stop listening
     */
    public void setSentMessageObserver(java.util.function.Consumer<CS2Message> observer)
    {
        this.sentMessageObserver = observer;
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
     * Tells the view a feedback changed, if there is one listening (only the route editor's capture wants it; a
     * sensor's tiles repaint themselves).
     *
     * @param name the feedback's name
     * @param state whether it is now occupied
     */
    public void feedbackChanged(String name, boolean state)
    {
        if (this.view != null) this.view.feedbackChanged(name, state);
    }

    /**
     * Formatted log message
     * @param key
     * @param args
     */
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
        // An unanswered ping is retried rather than treated as pending: UDP promises no answer and only an answer
        // clears pingStart, so one dropped response would stop the keepalive for the session (status line reading "lost
        // connection" for ever, the latency check cutting the power).  The outage clock below keeps the warning honest.
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
        // The start of the silence, not the latest retry: reading the retry would reset this each time the keepalive
        // tried again, and an hour's outage would look like five seconds.
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

        // THE CACHE FOLLOWS EVERY ADD, not only the sync's happy path: the three-argument overload rebuilds, and one
        // rebuild after the sync's loop would miss locomotives added before a throw (their echoes logged as from an
        // unknown loc).
        this.rebuildLocIdCache();

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
        // Carry over the actuation count from whatever is already at this address, resolved by name (which falls back
        // to address + protocol): getAccessoryByAddress takes a LOGICAL address and, as a creating lookup, would
        // register a spurious accessory one address below.
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
            
            throw new Exception(I18n.f("loc.changeToMultiUnitNotAllowed"));       
        }
        
        // Execute the change.  locDB.delete rather than deleteLoc, matching renameLoc: this is a re-key of a locomotive
        // that continues to exist, and deleteLoc unlinks it from every consist, which would silently drop it from its
        // multi-unit.
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
                // One call - see the syncWithCS2 repair above, at the method itself.
                other.setLinkedLocomotives(other.getLinkedLocomotiveNames());
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
        // Staging counts: a staging flow spends its planning phase with nothing dispatched, so isRunning() alone would
        // report idle and guards could allow a locomotive to be deleted or re-addressed under a plan about to drive it.
        // ASKED ONCE, NOT BUILT: Unload could clear the railway between asking whether there is one and asking for it.
        final Layout layout = this.getAutoLayoutIfLoaded();

        return layout != null && (layout.isRunning() || layout.isStagingInProgress());
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
     * The route now executing that drives this locomotive, or null.  For the doors that edit or delete a locomotive:
     * taking it out from under a running route left ironwork half set.  Separate from `isAutonomyRunning`, since a
     * route runs by hand or from an s88 with autonomy idle.
     *
     * @param name the locomotive
     * @return the route, or null when no running route names it
     */
    @Override
    public MarklinRoute runningRouteDriving(String name)
    {
        if (name == null) return null;

        for (String routeName : this.getRouteList())
        {
            MarklinRoute route = this.getRoute(routeName);

            if (route == null || !route.isExecuting()) continue;

            // AND THE ROUTE IT IS ABOUT TO FIRE (MT-464).
            if (route.commandsDrive(name) || this.chainDrives(route, name)) return route;
        }

        return null;
    }

    /**
     * Whether a route this running one will FIRE drives the locomotive (MT-464).  ONE HOP, because that is all the
     * engine reaches (`execRoute` hands `limit - 1` to the route it fires and refuses below zero); asking further would
     * refuse a delete for a route that cannot fire.
     *
     * @param running the route now executing
     * @param name the locomotive
     * @return whether the route it will fire drives that locomotive
     */
    private boolean chainDrives(MarklinRoute running, String name)
    {
        // A COPY, for the reason the walks in MarklinRoute take one: a delete rewrites these lists from
        // the event thread while a route is part-way along its own.
        for (RouteCommand rc : new ArrayList<>(running.getRoute()))
        {
            if (rc == null || !rc.isRoute()) continue;

            MarklinRoute next = this.getRoute(rc.getName());

            // Not itself.  The editor refuses a route that calls its own name, and a hand-edited file is
            // still a way in - an endless walk here would hang the door rather than answer it.
            if (next != null && next != running && next.commandsDrive(name)) return true;
        }

        return false;
    }

    /**
     * Deletes the locomotive with the given name
     * @param name
     * @return 
     */
    @Override
    public boolean deleteLoc(String name)
    {
        // Captured before the delete.  A consist holds Locomotive references, so a deleted member has to be removed
        // from them explicitly or the head goes on fanning commands to a decoder that no longer appears anywhere.
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

            // And the routes, as renameLoc repairs them: a route command naming a locomotive not in the database does
            // nothing when the route fires, and silently, which looks exactly like a route that ran.
            for (MarklinRoute r : this.getRoutes())
            {
                // Asked BEFORE `locomotiveDeleted` removes the commands, so the log can name the routes that lose them.
                boolean drove = r.commandsDrive(name);

                // A condition naming the deleted locomotive stays on purpose (see locomotiveDeleted); said out loud,
                // since that route will not fire again until edited.
                if (r.locomotiveDeleted(name))
                {
                    this.logf("route.warnConditionNamesDeletedLocomotive", r.getName(), name);
                }

                if (drove)
                {
                    this.logf("route.warnCommandsRemovedForDeletedLocomotive", r.getName(), name);
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

        // An unknown name answers null
        if (l == null)
        {
            return null;
        }

        if (l.getDecoderType() == MarklinLocomotive.decoderType.MFX 
                || l.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
        {
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
            
            // A locomotive hashes by identity, so renaming one moves it out of no consist, exclusion set or run list
            // (see MarklinLocomotive.hashCode).  State held by NAME is repaired in TWO places: the routes below and the
            // autonomy SETUP (placements, homes, exclusion lists).  A Point holds the LOCOMOTIVE, so the running layout
            // needs none.
            if (this.view != null) this.view.autonomyLocomotiveRenamed(name, newName);

            // AND THE RUNNING LAYOUT'S RECORD OF WHERE IT WAS TURNED, which is keyed by name for the reason the
            // setup is: a rename before the window drains it would otherwise lose the turn.
            // Asked once, not built
            final Layout loadedNow = this.getAutoLayoutIfLoaded();

            if (loadedNow != null) loadedNow.locRenamed(name, newName);
            
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

        // The programmatic API reaches this directly with caller-supplied names, so an unknown one is logged
        // and ignored rather than an NPE (as getLocAddress does).
        if (r == null)
        {
            this.logf("route.warningRouteNotExistCalledFrom", name, "execRoute");
            return;
        }

        r.execRoute(false);
    }
    
    /**
     * Points every route tile on every page at the route the database holds now.  **A tile runs the route OBJECT it was
     * wired to** (`wireComponents` calls `setRoute(routeDB.getById(...))`), and every door that changes a route
     * replaces or removes that object, so without a re-bind a tile would go on firing the old commands.  Cheap, and
     * called from every door that touches the route database.
     */
    public final void rebindRouteTiles()
    {
        // UNDER THE LOCK THE PAGE DATABASE IS WRITTEN WITH: `clearLayouts` and the configured-source sync empty and
        // refill plain HashMaps from other threads, so a walk without it can see the emptied state.
        synchronized (this.layoutRefreshLock)
        {
            for (String page : this.getLayoutList())
            {
                LayoutDiagram diagram = this.getLayout(page);

                if (diagram == null) continue;

                for (LayoutDiagramComponent c : diagram.getAll())
                {
                    if (c == null || !c.isRoute()) continue;

                    MarklinRoute bound = this.routeDB.getById(c.getAddress());

                    // SAID OUT LOUD, as `wireComponents` says it (`layout.routeButtonMissingRoute`): this door runs
                    // after every routeDB write and must not clear the tile in silence.
                    if (bound == null && c.getRoute() != null)
                    {
                        this.logf("layout.routeButtonMissingRoute", c.getAddress(), c.getX(), c.getY());
                    }

                    c.setRoute(bound);
                }
            }
        }
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
            // Not getAutoLayout() != null: getAutoLayout() CREATES a Layout when none exists, so that test was always
            // true and deleting a route silently instantiated one, bumping the static layoutVersion.  Asked once:
            // Unload can clear the model between two questions.
            final Layout layout = this.getAutoLayoutIfLoaded();

            if (layout != null)
            {
                if (layout.getActivateRouteIDs().contains((Integer) r.getId()))
                {
                    layout.getActivateRouteIDs().remove((Integer) r.getId());
                }
            }
            
            this.routeDB.delete(r.getName());

            // And the tiles follow the database: see newRoute(MarklinRoute).
            rebindRouteTiles();
        }        
    }
    
    /**
     * Every other route with a command that runs this one, by name: what the route delete door counts for its question,
     * before anything is deleted.  The route's own commands are not asked: a route that runs itself goes with it.
     *
     * @param name the route
     * @return the routes that run it, empty when none does or there is no such route
     */
    @Override
    public List<String> routesCalling(String name)
    {
        List<String> out = new ArrayList<>();

        MarklinRoute called = name == null ? null : this.routeDB.getByName(name);

        if (called == null) return out;

        for (MarklinRoute r : this.getRoutes())
        {
            if (r != null && r != called && r.callsRoute(called.getName())) out.add(r.getName());
        }

        return out;
    }

    /**
     * Deletes a route and takes every command that runs it out of every other route, logging each route that lost one:
     * left behind, such a command would do nothing and then run whatever route was next given the name.  A door of its
     * own, not `deleteRoute`: `editRoute` deletes and re-adds through that, and an edit keeps every call to the route
     * (`otherRouteRenamed` follows it).
     *
     * @param name the route
     * @return how many routes lost a command that ran it
     */
    @Override
    public int deleteRouteAndItsCalls(String name)
    {
        MarklinRoute deleted = name == null ? null : this.routeDB.getByName(name);

        if (deleted == null) return 0;

        String called = deleted.getName();

        int stripped = 0;

        for (MarklinRoute r : this.getRoutes())
        {
            // WHICH ROUTES LOSE COMMANDS, by name, as each loses them - as the locomotive delete says it
            if (r != null && r != deleted && r.otherRouteDeleted(called))
            {
                stripped++;

                this.logf("route.warnCommandsRemovedForDeletedRoute", r.getName(), called);
            }
        }

        this.deleteRoute(called);

        return stripped;
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

        // AND THE TILES FOLLOW.  A route tile is bound by ADDRESS, which is the id, so a route whose
        // id changes leaves its old tile pointing at it and its new tile pointing at nothing.
        rebindRouteTiles();
        
        // Update auto layout route selections - asked once, not built
        final Layout layout = this.getAutoLayoutIfLoaded();

        if (layout != null)
        {
            if (layout.getActivateRouteIDs().contains(oldId))
            {
                layout.getActivateRouteIDs().remove(oldId);
                layout.getActivateRouteIDs().add(newId);
            }
        }
        
        return true;
    }
    
    /**
     * Whether autonomy is set to activate this route, asked BEFORE something deletes it: `deleteRoute` strips the id
     * out of `activateRouteIDs` (ids are reused), which is collateral for the doors that delete and re-add the SAME
     * route (`editRoute`, the Central Station refresh), so a fact a recovery destroys is captured first.
     *
     * @param id the route's id
     * @return true when autonomy is set to activate it
     */
    public boolean isRouteActivatedByAutonomy(int id)
    {
        // Asked once, not built
        final Layout layout = this.getAutoLayoutIfLoaded();

        return layout != null && layout.getActivateRouteIDs().contains((Integer) id);
    }

    /**
     * Puts a route back into the autonomy selection after it has been deleted and re-added, so every such door
     * (`changeRouteId`, `editRoute`, the Central Station refresh) shares one repair.
     *
     * @param id the route's id
     * @param wasActivated what `isRouteActivatedByAutonomy` said before the delete
     */
    public void restoreRouteActivation(int id, boolean wasActivated)
    {
        // Asked once, not built: the sync's thread and a route edit's reach this
        final Layout layout = wasActivated ? this.getAutoLayoutIfLoaded() : null;

        if (layout == null) return;

        if (!layout.getActivateRouteIDs().contains((Integer) id))
        {
            layout.getActivateRouteIDs().add(id);
        }
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
            // RESOLVED ONCE, AND SKIPPED IF IT HAS GONE: the lookups are not under the collection's lock, and the
            // sync's re-read of a changed station route deletes an id and puts it back a few statements later.
            MarklinRoute route = this.routeDB.getById(i);

            if (route != null) l.add(route.getName());
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
     * Whether the accessory at this address is switched, WITHOUT creating one that is not there.  One rule for three
     * callers (a route condition and the two keyboard paints), so they cannot disagree.
     *
     *
     * @param address
     * @param decoderType
     * @return whether it is switched; false when there is no such accessory
     */
    @Override
    public boolean getAccessoryStateIfPresent(int address, Accessory.accessoryDecoderType decoderType)
    {
        Accessory a = this.getAccessoryByAddressIfPresent(address, decoderType);

        return a != null && a.isSwitched();
    }

    /**
     * Returns an accessory based on its numerical address, or null when there is none.
     *
     * The read-only lookup: it never creates a switch, unlike getAccessoryByAddress.
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
     * Gives back everything this control station holds: the listener, its port, and the three threads that serve
     * inbound messages.  Not needed to close the application (they are daemons), but a caller that finishes with a
     * station has no other way to release UDP 15730, which a second init() in the same JVM needs.
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

    /** The power's epoch: even while it is on, as it is at start-up - see `getPowerEpoch`. */
    private final java.util.concurrent.atomic.AtomicInteger powerEpoch = new java.util.concurrent.atomic.AtomicInteger();

    @Override
    public int getPowerEpoch()
    {
        return this.powerEpoch.get();
    }
    
    /**
     * Sets the power
     * @param state 
     */
    private void setPowerState(boolean state)
    {
        synchronized(this)
        {
            // THE EPOCH MOVED AT EVERY CHANGE: odd from a STOP, even again from a GO, once however many
            // report it
            if (state != this.powerState) this.powerEpoch.incrementAndGet();

            this.powerState = state;
            notifyAll();
        }
    }
    
    /**
     * Gets all the available layout names for display, in the order the index file (gleisbild.cs2) lists them (FR-175),
     * a page the import did not list after those, by name.  Not what an arrow's number indexes: that is
     * `getLayoutListByName`.  A fresh list each call.
     * @return
     */
    @Override
    public List<String> getLayoutList()
    {
        List<String> held = this.layoutDB.getItemNames();
        List<String> l = new ArrayList<>();

        for (String name : this.layoutOrder)
        {
            if (held.remove(name)) l.add(name);
        }

        Collections.sort(held);

        l.addAll(held);

        return l;
    }

    // The page names the autonomy setup records under each ID, which decide who keeps an ID two pages share
    private volatile Map<Integer, String> setupPageNames = new java.util.TreeMap<>();

    /**
     * Tells every link which page it goes to (FR-175).  On a station, and on a local layout marked as going by page
     * ID, a link that carries an ID goes to the page with it.  Otherwise a local link's number is a place in the
     * name-sorted list, and a station link's number is the page's ID.  An unmarked local layout is converted here,
     * once: every arrow is given the ID of the page its number names, written into its file, and the layout is marked.
     */
    private void attachLinks(CS2File parser)
    {
        final boolean station = parser.isFromStation();

        final String folder = station ? null : parser.localLayoutFolder();

        final boolean byId = station || LayoutDiagram.linksGoByPageId(folder);

        this.setupPageNames = station ? new java.util.TreeMap<Integer, String>()
            : LayoutDiagram.readSetupPageNames(folder);

        List<String> byName = getLayoutListByName();

        for (String name : byName)
        {
            LayoutDiagram page = this.layoutDB.getByName(name);

            if (page == null) continue;

            for (LayoutDiagramComponent tile : page.getAll())
            {
                if (tile == null || !tile.isLink()) continue;

                Integer id = byId ? tile.getLinkPageId() : null;

                int raw = tile.getRawAddress();

                if (station) id = LayoutDiagramComponent.stationLinkPageId(id, raw);
                else if (id == null)
                {
                    // A number past the list is kept as it was, and goes to no page
                    if (raw >= 0 && raw < byName.size()) aimLink(tile, byName.get(raw));
                    else tile.setLinkTarget(null, null, raw);

                    continue;
                }

                String target = id == null ? null : getPageNameForId(id);

                int number = station ? raw : target != null ? byName.indexOf(target) : -1;

                tile.setLinkTarget(id, target, number);
            }
        }

        if (!byId && folder != null && new File(folder, "config/gleisbild.cs2").isFile()) convertLinksToPageIds(folder);
    }

    /**
     * Writes each arrow's page ID, as just resolved, into its page's file and marks the layout, so that from now on the
     * ID decides; every arrow goes where it went.  Not marked if a page could not be written, so the next open tries
     * again with the old reading.
     */
    private void convertLinksToPageIds(String folder)
    {
        int arrows = 0;

        try
        {
            for (String name : getLayoutListByName())
            {
                LayoutDiagram page = this.layoutDB.getByName(name);

                if (page != null) arrows += page.writeLinkPageIdsIntoFile();
            }

            LayoutDiagram.markLinksByPageId(folder);

            if (arrows > 0) this.logf("layout.linksConvertedToPageIds", arrows);
        }
        catch (Exception e)
        {
            this.log(e);
        }
    }

    /**
     * Aims a local link at a page by name, or at none.  A page whose ID another page shares is aimed at by its place
     * in the name-sorted list alone, since the ID could name the other page.
     *
     * @param tile the link
     * @param page the page's name, or null for none
     */
    @Override
    public void aimLink(LayoutDiagramComponent tile, String page)
    {
        List<String> byName = getLayoutListByName();

        if (page == null || !byName.contains(page))
        {
            tile.setLinkTarget(null, null, -1);

            return;
        }

        int id = getPageIdOf(page);

        int sharing = 0;

        for (String name : byName) if (getPageIdOf(name) == id) sharing++;

        tile.setLinkTarget(sharing == 1 ? Integer.valueOf(id) : null, page, byName.indexOf(page));
    }

    /**
     * The name of the page that has this ID, or null.  Two pages that share an ID (a layout from before IDs were told
     * apart) give it to the one the setup records under it, else the first by name.
     */
    @Override
    public String getPageNameForId(int id)
    {
        String found = null;

        for (String name : getLayoutListByName())
        {
            LayoutDiagram page = this.layoutDB.getByName(name);

            if (page == null || page.getPageIdNumber() != id) continue;

            if (name.equals(this.setupPageNames.get(id))) return name;

            if (found == null) found = name;
        }

        return found;
    }

    /** @return the ID of the page with this name, or -1 when there is none */
    @Override
    public int getPageIdOf(String name)
    {
        LayoutDiagram page = this.layoutDB.getByName(name);

        return page == null ? -1 : page.getPageIdNumber();
    }

    /**
     * The layout names sorted by name: the list an arrow's number indexes into, whatever order the pages are shown in
     * (`getLayoutList`).  A fresh list each call.
     * @return the names, sorted
     */
    @Override
    public List<String> getLayoutListByName()
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
     * Whether this session is simulating rather than driving a real Central Station, asked by anything that offers to
     * fetch from the station (see the field for why the sync's success flag does not answer this).
     *
     * @return true when TrainControl was started in simulate mode
     */
    @Override
    public boolean isSimulation()
    {
        return this.simulation;
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

        // SAID IN THE SIGNATURE, rather than thrown unannounced: `new JSONObject(garbage)` throws an unchecked
        // JSONException.  Wrapped rather than swallowed, since returning an empty list would say "your file has no
        // routes" and `importRoutes` would then delete every route the user has and add nothing.
        JSONObject jsonObject;
        JSONArray dataArray;

        try
        {
            jsonObject = new JSONObject(json);
            dataArray = jsonObject.getJSONArray("routes");
        }
        catch (org.json.JSONException notRouteJson)
        {
            throw new IllegalArgumentException(
                I18n.t("route.errorNotAValidRouteFile"), notRouteJson);
        }

        // AN IMPORTED ROUTE IS NOT ARMED: "they should not be armed.  The user can choose to do this when they are
        // ready." Constructing a MarklinRoute arms it (the complete constructor ends in executeAutoRoute(), which parks
        // a thread on the route's s88), so a route built here would watch the railway before any database has it, and a
        // malformed entry would leave the routes built before it armed and unreachable.  The file's `auto` is
        // overwritten with false before construction, so no monitor starts at all.
        for (int i = 0; i < dataArray.length(); i++)
        {
            JSONObject entry = dataArray.getJSONObject(i);

            entry.put("auto", false);

            routes.add(MarklinRoute.fromJSON(entry, this));
        }

        return routes;
    }
    
    /**
     * Replaces existing route data with that from a JSON file
     * @param json
     * @return how many routes were added
     */
    @Override
    public int importRoutes(String json)
    {
        return importRoutes(json, false);
    }

    /**
     * The routes a route export saved with their automatic firing on, by name - those with a sensor to watch, which are
     * the ones the import arms again when asked to.  Read here rather than out of `parseRoutesFromJson`, which
     * overwrites each route's `auto` before it builds it (a route built armed starts watching its sensor at once).
     * WITH A SENSOR, as Enable Auto Execution requires, so the question Routes > Import asks counts exactly those.
     *
     * @param json the export
     * @return their names, empty when there are none or the file cannot be read
     */
    @Override
    public List<String> routesSavedArmed(String json)
    {
        List<String> out = new ArrayList<>();

        try
        {
            org.json.JSONArray routes = new org.json.JSONObject(json).optJSONArray("routes");

            for (int i = 0; routes != null && i < routes.length(); i++)
            {
                org.json.JSONObject route = routes.optJSONObject(i);

                // `hasS88` of the route the import builds: its s88 is the absolute value of the file's
                if (route != null && route.optBoolean("auto", false) && !route.optString("name", "").isEmpty()
                    && Math.abs(route.optInt("s88", 0)) > 0)
                {
                    out.add(route.optString("name"));
                }
            }
        }
        catch (org.json.JSONException unreadable)
        {
            // The import itself says why the file cannot be read; there is nothing to ask about.
        }

        return out;
    }

    /**
     * Replaces existing route data with that from a JSON file, turning automatic firing back on for the routes the file
     * saved with it on when the operator said so.
     *
     * @param json the export
     * @param armAsSaved whether a route saved armed arrives armed
     * @return how many routes were added
     */
    @Override
    public int importRoutes(String json, boolean armAsSaved)
    {
        this.routesSplitByLastImport = new ArrayList<>();

        List<MarklinRoute> routes = this.parseRoutesFromJson(json);

        // Asked about before anything is replaced, and answered by the operator: "save the state in
        // the file on export, and ask the user on import.  if they want them armed, arm them.  otherwise,
        // don't."
        java.util.Set<String> savedArmed = armAsSaved
            ? new java.util.HashSet<>(routesSavedArmed(json)) : java.util.Collections.<String>emptySet();

        // A FILE THAT NAMES ONE ROUTE TWICE IS REFUSED WHOLE, before anything is deleted (OB-253): `newRoute` turns
        // away the second of two sharing an id or a trimmed name, so such a file would lose that route after the others
        // were gone.  Thrown, so the door says the import failed and the log says why.
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();

        for (MarklinRoute route : routes)
        {
            String name = route.getName() == null ? "" : route.getName().trim();

            boolean idTwice = !ids.add(route.getId());

            boolean nameTwice = !names.add(name);

            // SAYING WHAT REPEATS: the name and the number together sent the operator to one that appears once
            if (idTwice)
            {
                throw new IllegalArgumentException(I18n.f("route.errorImportNumbersARouteTwice",
                    String.valueOf(route.getId())));
            }

            if (nameTwice)
            {
                throw new IllegalArgumentException(name.isEmpty() ? I18n.t("route.errorImportTwoRoutesWithNoName")
                    : I18n.f("route.errorImportNamesARouteTwice", name));
            }
        }

        // EVERY ROUTE HERE IS BUILT DISARMED (see parseRoutesFromJson); a Yes from the door turns automatic firing back
        // on below, after every route is in, for exactly the routes the file saved with it on.
        this.logf("route.deletingExisting");
        for (MarklinRoute r : this.routeDB.getItems())
        {
            this.deleteRoute(r.getName());
        }

        // If all read successfully, remove existing routes and update route DB
        int added = 0;

        for (MarklinRoute route : routes)
        {
            this.logf("route.adding", route.getName());

            if (!this.newRoute(route))
            {
                this.logf("route.notAdded", route.getName());
            }
            else
            {
                added++;
            }
        }

        // ARMED AFTER EVERY ROUTE IS IN, as Enable Auto Execution arms one: the old routes are gone, so no
        // two watch one sensor.  Only a route that has a sensor to watch, as the right-click item requires.
        int rearmed = 0;

        for (MarklinRoute route : routes)
        {
            if (!savedArmed.contains(route.getName()) || !route.hasS88() || this.getRoute(route.getName()) != route)
            {
                continue;
            }

            route.enable();
            route.executeAutoRoute();

            rearmed++;
        }

        // No route mixes an emergency stop with other commands: one the file has is split once every route is in
        // and armed as the file saved it, and the door names it.
        this.routesSplitByLastImport = this.splitRoutesThatMixAStop();

        if (rearmed > 0)
        {
            this.logf(IMPORTED_ROUTES_REARMED, added, rearmed);

            return added;
        }

        // AND SAID: an imported route arrives disarmed, and saying so is what stops a restored backup from first being
        // noticed when a train runs through a sensor that once set a road.  The door shows the same sentence.
        this.logf(IMPORTED_ROUTES_NOTICE, added, I18n.t("ui.main.bulkEnable"),
            I18n.t("route.ui.menuEnableAutoExecution"));

        return added;
    }

    /**
     * Splits every route that puts an emergency stop among other commands, which no route may do: *"if a route has
     * emergency stop, it cannot have any other types of commands.  Reject it from being created or imported as
     * such."*  Such a route is split: *"Split it automatically."*
     *
     * The route keeps its name, sensor, conditions, lock and armed state; its stop moves to a new route of its own,
     * named after it and numbered from 1000 as an editor route is, and a Route command firing that one stands where the
     * stop stood, with the stop's wait.  A second stop in one route is dropped: one power cut is the same as two.  Run
     * after the start-up restores the saved routes and after an import, when every route is in: a new route's id is
     * free only then.
     *
     * @return each route split, with the stop route it now fires
     */
    public List<String[]> splitRoutesThatMixAStop()
    {
        List<String[]> split = new ArrayList<>();

        for (MarklinRoute r : new ArrayList<>(this.routeDB.getItems()))
        {
            if (r == null || !MarklinRoute.mixesAStop(r.getRoute())) continue;

            String name = r.getName();

            String stopName = I18n.f("route.stopRouteSplitName", name);

            for (int n = 2; this.routeDB.hasName(stopName); n++)
            {
                stopName = I18n.f("route.stopRouteSplitName", name) + " " + n;
            }

            List<RouteCommand> stopOnly = new ArrayList<>();
            List<RouteCommand> rest = new ArrayList<>();

            for (RouteCommand rc : r.getRoute())
            {
                if (rc != null && rc.isStop())
                {
                    // The first stop moves, and its place fires the stop route.  Its wait stays on the command in its
                    // place: the stop route runs on its own thread, and nothing after the command waits for it.
                    if (stopOnly.isEmpty())
                    {
                        stopOnly.add(RouteCommand.RouteCommandStop());

                        RouteCommand fire = RouteCommand.RouteCommandRoute(stopName);

                        fire.setDelay(rc.getDelay());

                        rest.add(fire);
                    }
                }
                else
                {
                    rest.add(rc);
                }
            }

            // NUMBERED AS A ROUTE MADE IN THE EDITOR IS, from 1000 up: below that are the
            // Central Station's own routes, which its sync replaces by number
            int stopId = Math.max(ROUTE_STARTING_ID, Collections.max(this.routeDB.getItemIds()) + 1);

            if (!this.newRoute(stopName, stopId, stopOnly, 0, r.getTriggerType(), false, null))
            {
                this.logf("route.notAdded", stopName);
                continue;
            }

            if (!this.editRoute(name, name, rest, r.getS88(), r.getTriggerType(), r.isEnabled(), r.getConditions()))
            {
                this.deleteRoute(stopName);
                this.logf("route.notAdded", name);
                continue;
            }

            this.logf("route.stopRouteSplit", name, stopName);

            split.add(new String[] {name, stopName});
        }

        return split;
    }

    /**
     * The routes the last import split, each with the stop route it now fires, for the door to name.
     *
     * @return pairs of route and stop route; empty when the last import split nothing
     */
    @Override
    public List<String[]> getRoutesSplitByLastImport()
    {
        return new ArrayList<>(this.routesSplitByLastImport);
    }

    /**
     * What an import of routes says about them, with the count, then Bulk Enable, then the right-click item.
     */
    public static final String IMPORTED_ROUTES_NOTICE = "route.infoImportedArriveDisarmed";

    /**
     * What an import says when the operator asked for the routes saved armed to be armed again: the count, then how
     * many were.
     */
    public static final String IMPORTED_ROUTES_REARMED = "route.infoImportedRearmed";
        
    /**
     * Exports the locomotive database to a user-friendly CSV file for reference
     * @return 
     */
    @Override
    public String exportLocsToCSV()
    {
        StringBuilder csvBuilder = new StringBuilder();

        // Header row
        csvBuilder.append("Name,ButtonMappings,DecoderType,Address,PreferredSpeed,TotalRuntime,Start Year,End Year,RailwayName,Notes,LastRun\n");

        for (Locomotive l : this.getLocomotives())
        {
            String name = escapeCsv(l.getName());
            // THE ONE FIELD THAT NEEDS A WINDOW: `view` is null when the model was built with showUI false (the
            // programmatic API, AutomationAPI.md), so an API user asking for the CSV would get a NullPointerException.
            // Empty rather than refusing: a session with no window has no button mappings.
            List<String> mappings = this.view != null
                ? this.view.getAllLocButtonMappings(l)
                : new ArrayList<>();
            String mappingStr = escapeCsv(String.join(";", mappings)); // Semicolon to avoid comma collision
            String decoder = escapeCsv(l.getDecoderTypeLabel());
            int address = l.getAddress();
            int prefSpeed = l.getPreferredSpeed();
            String runtime = escapeCsv(Conversion.convertSecondsToHMmSs(l.getTotalRuntime()));
            
            int startYear = l.getStructuredNotes().getStartYear();
            int endYear = l.getStructuredNotes().getEndYear();
            String railway = escapeCsv(l.getStructuredNotes().getRailway());
            String notes = escapeCsv(l.getStructuredNotes().getNotes());

            // The day the import compares to decide whether the file's runtime is newer (FR-049); blank if never run
            String lastRun = escapeCsv(l.getLastRunDay());

            csvBuilder.append(String.format("%s,%s,%s,%d,%d,%s,%d,%d,%s,%s,%s\n",
                name, mappingStr, decoder, address, prefSpeed, runtime, startYear, endYear, railway, notes, lastRun));
        }

        return csvBuilder.toString();
    }
    
    @Override
    public String exportAccessoryActuationsToCSV()
    {
        List<MarklinAccessory> accessories = new ArrayList<>(this.accDB.getItems());

        accessories.sort(Comparator.comparing((MarklinAccessory a) -> a.getDecoderType().ordinal())
            .thenComparingInt(MarklinAccessory::getAddress));

        StringBuilder csv = new StringBuilder("Name,Address,Protocol,Type,Actuations\n");

        for (MarklinAccessory a : accessories)
        {
            // The address the keyboard shows: the stored one counts from 0
            csv.append(escapeCsv(a.getName())).append(',').append(a.getAddress() + 1).append(',')
                .append(a.getDecoderType()).append(',').append(Accessory.accessoryTypeToPrettyString(a.getType()))
                .append(',').append(a.getNumActuations()).append('\n');
        }

        return csv.toString();
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
        System.out.println(I18n.f("app.starting", I18n.f("app.title", MarklinControlStation.versionForDisplay())));
        
        // User interface - only initialize if needed
        TrainControlUI ui = null;
        
        // THE LOOK AND FEEL FIRST, whether or not a window is built: a caller passing showUI=false (every test) would
        // otherwise get Metal dialogs.  Idempotent, so the constructor asking again costs nothing.
        if (!java.awt.GraphicsEnvironment.isHeadless()) TrainControlUI.installLookAndFeel();

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

                            // The application's own icon (OB-105): this dialog has no owner window (it is the FIRST
                            // thing shown), so Swing would give it the coffee cup.  Quietly, if at all: applyWindowIcon
                            // swallows its own failures, since a missing resource must not stop the application asking
                            // for an IP (OB-124).
                            try
                            {
                                org.traincontrol.gui.TrainControlUI.applyWindowIcon(dialog);
                            }
                            catch (RuntimeException noIcon)
                            {
                                // Decoration only - see above.
                            }
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
                            // Not try-with-resources: closing a Scanner closes System.in with it, and this prompt is
                            // inside a retry loop (a second prompt would otherwise get NoSuchElementException,
                            // uncaught since only HeadlessException is).
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
                            
                            // Left on purpose, from this version's own question: it started (FR-125)
                            org.traincontrol.util.SelfUpdate.reportStarted();

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
        
        // SOMETHING ON SCREEN while the station is reached (FR-041): until the window is built a slow start is
        // indistinguishable from a failed one.  Only when a window was asked for: every test passes showUI false.
        //
        // ON THE WINDOW ITSELF, not a splash of its own (OB-170): a second always-on-top window spends the process's
        // single chance at the foreground, so the main window would arrive behind it.  `ui` is built at the third
        // statement of this method and put on screen now with a spinner, on the event thread and waited for.
        if (showUI && ui != null)
        {
            final org.traincontrol.gui.TrainControlUI showing = ui;

            try
            {
                javax.swing.SwingUtilities.invokeAndWait(() ->
                    showing.showConnecting(I18n.t("ui.splashConnecting")));
            }
            catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
            }
            catch (java.lang.reflect.InvocationTargetException couldNotShow)
            {
                // Nothing here is worth failing a start-up over: the application works perfectly well without
                // the reassurance, and the alternative is refusing to start because it could not be drawn.
            }
        }

        final NetworkProxy proxy;
        final MarklinControlStation model;

        try
        {
            // Delegate the hard part
            proxy = new NetworkProxy(InetAddress.getByName(initIP));

            // Initialize the central station
            model = new MarklinControlStation(proxy, showUI ? ui : null, autoPowerOn, debug, simulate);
        }
        catch (Exception ex)
        {
            // Both statements above throw on exactly the failures the "Connecting..." notice exists to cover (an
            // unknown host, or a second copy of TrainControl, where the bind happens inside NetworkProxy's
            // constructor).  Without this catch the notice would still be standing when the caller's error dialog came
            // up underneath it (FR-041).
            if (ui != null) ui.connectingFailed();

            throw ex;
        }

        final TrainControlUI theUI = ui;

        // WHETHER THIS IS A SIMULATION, before the window starts: the window asks while it builds (Preferences > Debug
        // is offered only in debug and simulation), and `TrainControlUI.isCentralStationConnected` needs false from the
        // first frame (OB-098).  Set again below with the power.
        model.simulation = simulate;
        
        // Set model
        if (showUI && theUI != null)
        {
            model.logf("ui.initializing");

            final CountDownLatch latch = new CountDownLatch(1);

            // Whether the window was actually built: invokeLater only enqueues, so the try/catch around it never sees
            // what the posted lambda throws.  A failed build would leave a live process with no window and display()
            // throwing.
            final java.util.concurrent.atomic.AtomicBoolean built =
                new java.util.concurrent.atomic.AtomicBoolean(false);

            javax.swing.SwingUtilities.invokeLater(() ->
            {
                try
                {
                    // NULL, not the latch: setViewListener's LAST statement counts the latch down, which would release
                    // the waiter one statement before `built` was set and could exit an application whose window was
                    // fully built.  So there is exactly one release, in the finally below, and it fires after this flag
                    // either way.
                    theUI.setViewListener(model, null);

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
                    // Counted down whatever happened (OB-077): anything that stops setViewListener before its last
                    // statement would leave the latch at one, and the wait below has no timeout, so the application
                    // would hang with no window.  Throwable for the same reason.  Counting down after a FAILED build is
                    // deliberate: it hands control back to a caller that logs and exits (see `built` and the check
                    // after await()).
                    latch.countDown();

                    // Nothing here takes the notice down: it is ON the window, and hiding it only to show it again from
                    // `display()` made the window close and reopen.  A FAILED build takes the window with it.
                }
            });

            // await() itself can throw InterruptedException, so the catch below takes the notice down on
            // that path too.
            try
            {
                latch.await();
            }
            catch (InterruptedException interrupted)
            {
                // The notice goes only on the way OUT, not on every path: on the ordinary path `display()` replaces
                // it with the application.
                if (theUI != null) theUI.connectingFailed();

                throw interrupted;
            }

            // A start-up that never reaches the window build must not leave the notice on screen either; closing it is
            // safe to repeat.  NOT ON THE SUCCESS PATH: there, `display()` replaces the notice with the application.
            if (!built.get() && theUI != null) theUI.connectingFailed();

            // A failed build stops here: display() would throw again on the event thread and leave a process with
            // nothing on screen.  Whatever went wrong was already logged where it was caught.
            if (!built.get())
            {
                model.logf("ui.fatalErrorInitializing");

                // 1, not 0: whatever launched this should be told the start-up failed.  The log line reaches the window
                // through invokeLater, which this exit usually beats; the log file is the reason the message is seen at
                // all.
                System.exit(1);
            }

            model.logf("ui.rendering");
            
            try
            {
                javax.swing.SwingUtilities.invokeLater(() ->
                {
                    theUI.display();

                    model.logf("ui.initialized");

                    // UP: the window is on screen.  An update waiting to hear that this version started is told here,
                    // not at main's first line, so a version that fails anywhere in its start-up is put back (FR-125).
                    org.traincontrol.util.SelfUpdate.reportStarted();
                });
            }
            catch (Exception ex)
            {
                // Reachable only for a failure to POST the task: the lambda's own exceptions land on the event thread,
                // which is why the flag above exists.  exit(1) here too, as for the failed build above.
                model.logf("ui.fatalErrorInitializing");
                model.log(ex);
                System.exit(1);
            }
        }
                        
        // TCP for a CS3 the start-up sync found (FR-186): a CS2, or a station whose type is not known, runs on UDP
        // exactly as before
        if (model.isCS3() && model.getNetworkCommState())
        {
            proxy.connectOverTcp(simulate ? NetworkProxy.simulationTcpPort() : NetworkProxy.TCP_PORT);
        }

        // Start execution
        proxy.setModel(model);

        // The start-up sync ran before the proxy had its model: its sensors are asked now (OB-418)
        if (model.isCS3() && model.getNetworkCommState()) proxy.syncSensors();
        
        // Connection failed - ask for IP on next run
        if (forgetsTheAddress(simulate, model.getNetworkCommState()))
        {
            TrainControlUI.getPrefs().remove(TrainControlUI.IP_PREF);
        }
        
        // Make the model think that the power is on
        if (simulate)
        {
            model.powerState = true;

            // And remember that it is a simulation, so that anything offering to fetch from a
            // Central Station can decline (OB-098).
            model.simulation = true;
        }
                                    
        return model;
    }
}
