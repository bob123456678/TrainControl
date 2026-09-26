package org.traincontrol.marklin;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.base.Accessory;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.Conversion;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RemoteDevice;
import org.traincontrol.util.I18n;

/**
 * Marklin locomotive that implements CS2 interfaces/protocols
 * @author Adam
 */
public class MarklinLocomotive extends Locomotive
    implements java.io.Serializable, RemoteDevice<Locomotive, CS2Message>
{            
    /* Constants */
    
    public static final int MFX_NUM_FN = 32;
    public static final int DCC_NUM_FN = 29;
    public static final int MM2_NUM_FN = 5;
    public static final int MULTI_UNIT_MAX_ADDR = 5120;
    public static final int MFX_BASE = 0x4000;
    public static final int DCC_BASE = 0xc000;
    public static final int MULTI_UNIT_BASE = 0x2c00; // The first MU created by the Central Station is 0x2c01
    
    public static int PULSE_FUNCTION_DURATION = 300;
        
    // Function icon colors
    public static final String[] COLOR_YELLOW = {"i_gr", "a_ge"};
    public static final String[] COLOR_WHITE = {"i_we", "a_we"};
    
    // The raw locomotive address
    private int address;
    
    // Calculated UID
    private int UID;
    
    // The locomotive's decoder
    private decoderType type;
    
    // Reference to the network
    private final MarklinControlStation network;    

    // Local function icons
    private final String resourcePath = "/org/traincontrol/gui/resources/functions";
    private static final int NUM_FN_ICONS_CS2 = 112;
    private static final int NUM_FN_ICONS_CS3 = 296;
    
    // Track if user customizations were made to function behavior
    private boolean customFunctions = false;
    
    // Locomotives linked to this locomotive that will operate as a multi-unit
    // Key - the other locomotive, Value - the speed adjustment (negative will force the opposite direction of this locomotive)
    // REPLACED, NEVER EDITED, AND READ WITHOUT A LOCK BY DESIGN (S14-B4).
    //
    // The save path is the reader that decides the shape of this field: MarklinSimpleComponent reads
    // getLinkedLocomotives() with no lock, and restoreState rebuilds every consist from exactly what it
    // wrote - so a save that sees this map empty writes a locomotive with NO members into locdb.data,
    // and the consist is gone after the next start with the file as the only record.  The rebuild runs
    // off the event thread inside syncWithCS2; the save runs from the window-closing path and from
    // Backup Data's own thread, and nothing serialises them.
    //
    // It was a live LinkedHashMap cleared and refilled in place, so every reader shared one instance and
    // there was a window in which it was empty.  Locking the readers was the other candidate and was
    // rejected: there are nine of them, one on a thread of its own in applyPreferredFunctions, and an
    // event-thread save would then wait behind a fan-out that holds this monitor across a UDP send.
    //
    // So: every published value is unmodifiable, a rebuild ASSIGNS a new one, and a reader keeps
    // whatever it was holding.  volatile is what makes that assignment visible to the threads above.
    private volatile Map <Locomotive, Double> linkedLocomotives = java.util.Collections.emptyMap();     
    private Map <String, Double> preLinkedLocomotives;
    
    // For informational purposes, this is the list of locomotives in a central station (not a TrainControl) multi unit
    private Map <String, Double> centralStationMultiUnitLocomotiveNames;
    
    /**
     * Constructor with name, type, and address
     * @param network
     * @param address
     * @param type
     * @param name
     */
    public MarklinLocomotive(MarklinControlStation network, int address, 
        decoderType type, String name)
    {        
        super(name, getMaxNumF(type));

        this.network = network;
        this.type = type;
        this.address = address;
        this.UID = calculateUID();
    }
    
    /**
     * Constructor with name, type, address, and function types
     * @param network
     * @param address
     * @param type
     * @param name
     * @param functionTypes
     * @param functionTriggerTypes
     */
    public MarklinLocomotive(MarklinControlStation network, int address, 
        decoderType type, String name, int[] functionTypes, int[] functionTriggerTypes)
    {        
        super(name, getMaxNumF(type), functionTypes, functionTriggerTypes);

        this.network = network;
        this.type = type;
        this.address = address;
        this.UID = calculateUID();
                
        assert this.functionTypes.length == getMaxNumF(type);
    }
    
    /**
     * Constructor with full state
     * @param network
     * @param address
     * @param type
     * @param dir
     * @param name
     * @param functions
     * @param functionTypes
     * @param functionTriggerTypes
     * @param preferredFunctions
     * @param preferredSpeed
     * @param departureFunc
     * @param arrivalFunc
     * @param reversible
     * @param trainLength
     * @param historicalOperatingTime
     */
    public MarklinLocomotive(MarklinControlStation network, int address, 
        decoderType type, String name, Locomotive.locDirection dir, boolean[] functions, int[] functionTypes, int[] functionTriggerTypes, boolean[] preferredFunctions, int preferredSpeed,
        Integer departureFunc, Integer arrivalFunc, boolean reversible, Integer trainLength, Map<String, Long> historicalOperatingTime)
    {
        super(name, 0, dir, getMaxNumF(type), functions, functionTypes, functionTriggerTypes, preferredFunctions, preferredSpeed, departureFunc, arrivalFunc, reversible, trainLength, historicalOperatingTime);

        this.network = network;
        this.type = type;
        this.address = address;
        this.UID = calculateUID();
        
        assert this.functionTypes.length == getMaxNumF(type);
        assert this.functionState.length == getMaxNumF(type);
    }
    
    /**
     * Changes the function type for a single function
     * @param fNo
     * @param type 
     * @param triggerType 
     */
    @Override
    public void setFunctionType(int fNo, int type, int triggerType)
    {
        if (this.validF(fNo))
        { 
            // Mark as custom if we are making changes
            if (this.functionTypes[fNo] != type || this.functionTriggerTypes[fNo] != triggerType)
            {
                this.customFunctions = true;
            }
            
            this.functionTypes[fNo] = type;
            
            this.functionTriggerTypes[fNo] = triggerType;
        }
    }
    
    /**
     * Flags that the functions have been customized
     * @param state 
     */
    @Override
    public void setCustomFunctions(boolean state)
    {
        this.customFunctions = state;
    }
    
    /**
     * Returns whether the functions have been customized
     * @return 
     */
    @Override
    public boolean isCustomFunctions()
    {
        return this.customFunctions || !this.getLocalFunctionImageURLs().isEmpty();
    }
    
    /**
     * Validates a proposed address for the locomotive
     * @param newType
     * @param newAddress
     * @return 
     */
    public static boolean validateNewAddress(decoderType newType, int newAddress)
    {
        switch (newType)
        { 
            case MM2:
                return newAddress > 0 && newAddress <= MM2_MAX_ADDR;
            case MFX:
                return newAddress > 0 && newAddress <= MFX_MAX_ADDR;
            case DCC:
                return newAddress > 0 && newAddress <= DCC_MAX_ADDR;
            case MULTI_UNIT:
                return newAddress > 0 && newAddress <= MULTI_UNIT_MAX_ADDR;
            default:
                return false;
        }
    }
    
    /**
     * Determines the Marklin UID based on address and protocol
     */
    private int calculateUID()
    {
        // Verify MM2 address range
        if (this.type == decoderType.MM2)
        {            
            return this.address;
        } 
        // Verify MFX address range
        else if (this.type == decoderType.MFX)
        {
            return this.address + MFX_BASE;            
        }  
        else if (this.type == decoderType.DCC)
        {
            return this.address + DCC_BASE;            
        }
        else if (this.type == decoderType.MULTI_UNIT)
        {
            return this.address + MULTI_UNIT_BASE;
        }    
            
        return 0;
    }
    
    /**
     * Returns the number of possible function icons depending on the type of control station
     * @return 
     */
    @Override
    public int getNumFnIcons()
    {
        if (this.network.isCS3() || !this.network.getNetworkCommState())
        {
            return NUM_FN_ICONS_CS3;
        }
        
        return NUM_FN_ICONS_CS2;
    }
    
    /**
     * Ensures that the function icon type is within a valid range (0-112 for CS2 or 0-296 for CS3)
     * @param fType
     * @return 
     */
    @Override
    public int sanitizeFIconIndex(int fType)
    {
        if (this.network.isCS3() || !this.network.getNetworkCommState())
        {
            return fType % (NUM_FN_ICONS_CS3 + 1);
        }
        else
        {
            // > 128 just means it's a pulse function.  While loop in case this is 224-255
            while (fType > NUM_FN_ICONS_CS2)
            {
                fType = fType % Math.min(128, fType); // icons 113-127 do not exist
            }

            return fType;
        }
    }
        
    /**
     * Returns the image URL for a function number
     * @param fNo - the function number
     * @param fType
     * @param active
     * @param yellow - white or yellow version
     * @return 
     */
    @Override
    public String getFunctionIconUrl(int fNo, int fType, boolean active, boolean yellow)
    {
        String customIconURL = this.getLocalFunctionImageURL(fNo);

        if (customIconURL != null && customIconURL.length() > 0)
        {
            return customIconURL;
        }
        else
        {
            return getFunctionIconUrl(fType, active, yellow);
        }
    }
    
    /**
     * Returns the image URL for a function icon, if any
     * @param fType - the CS2 icon index
     * @param active
     * @param yellow
     * @return 
     */
    @Override
    public String getFunctionIconUrl(int fType, boolean active, boolean yellow)
    {
        int index = active ? 1 : 0;
        String[] color = yellow ? COLOR_YELLOW : COLOR_WHITE;

        fType = sanitizeFIconIndex(fType);

        String iconName = "FktIcon_" + color[index] + "_" + (fType < 10 ? "0" : "") + Integer.toString(fType) + ".png";

        // Load local version of the marklin icon
        try
        {
            URL resource = MarklinLocomotive.class.getResource(resourcePath + "/" + iconName);
            return resource.toString();
        }
        catch (Exception e)
        {
            if (this.network.isDebug())
            {
                this.network.logf("error.missingLocalFunctionIcon", iconName);
            }
        }

        // Icon was missing, try loading from central station
        return "http://" + this.network.getIP() + "/fcticons/" + iconName;
    }
    
    /**
     * Checks if the given function is supposed to only stay on for a short period
     * @param fNo
     * @return 
     */
    @Override
    public boolean isFunctionPulse(int fNo)
    {
        if (this.validF(fNo))
        {
            return this.functionTriggerTypes[fNo] == Locomotive.FUNCTION_PULSE;
        }
        
        return false;
    }
    
    /**
     * Checks if the given function is supposed to only stay on for a short period
     * @param fNo
     * @return 
     */
    @Override
    public int isFunctionTimed(int fNo)
    {
        if (this.validF(fNo))
        {
            if (this.functionTriggerTypes[fNo] > 0)
            {
                return this.functionTriggerTypes[fNo];
            }
        }
        
        return 0;
    }
    
    /**
     * Gets the maximum number of functions for a given decoder type
     * @param decoder
     * @return 
     */
    public static int getMaxNumF(decoderType decoder)
    {
        if (decoder == decoderType.MM2)
        {
            return MM2_NUM_FN;
        }
        else if (decoder == decoderType.DCC)
        {
            return DCC_NUM_FN;
        }
        
        return MFX_NUM_FN;
    }
    
    /**
     * Gets the locomotives UID as defined in the CS2
     * @return 
     */
    public int getIntUID()
    {
        return this.UID;
    }
    
    /**
     * Gets the locomotives UID - we add the name b/c same mm2 address can be re-used
     * @return 
     */
    public String getUID()
    {
        return this.getName() + '_' + Integer.toString(UID);        
    }
    
    /**
     * Gets the raw locomotive address
     * @return 
     */
    @Override
    public int getAddress()
    {
        return this.address;
    }
    
    /**
     * Sets the raw locomotive address
     * Should only be called when updating state from CS2/CS3
     * @param newAddress
     * @param newDecoderType
     * @return validity status
     */
    public boolean setAddress(int newAddress, decoderType newDecoderType)
    {
        if (MarklinLocomotive.validateNewAddress(newDecoderType, newAddress))
        {
            this.type = newDecoderType;
            this.address = newAddress;
            this.UID = calculateUID();

            // Resize function arrays if needed
            functionTypes = Arrays.copyOf(functionTypes, getMaxNumF(newDecoderType)); 
            functionState = Arrays.copyOf(functionState, getMaxNumF(newDecoderType));
            functionTriggerTypes = Arrays.copyOf(functionTriggerTypes, getMaxNumF(newDecoderType));
            preferredFunctions = Arrays.copyOf(preferredFunctions, getMaxNumF(newDecoderType));
            this.numF = getMaxNumF(newDecoderType);

            // A decoder change can shrink the function count, and the arrival/departure functions
            // were the only per-function state not revisited here - so converting MFX (32) to MM2 (5)
            // left, say, arrival function 20 in place.  The one dialog that could repair it,
            // GraphLocAssign, sizes its combo boxes to numF and crashed on the stale index - wedging
            // the repair path.  Cleared through the setter, which is the only writer that validates;
            // it cannot be used to clamp, since it refuses out-of-range values and keeps the old one.
            if (this.getArrivalFunc() != null && this.getArrivalFunc() >= this.numF)
            {
                this.setArrivalFunc(null);
            }

            if (this.getDepartureFunc() != null && this.getDepartureFunc() >= this.numF)
            {
                this.setDepartureFunc(null);
            }

            return true;
        }
        
        this.network.logf(
            "loc.invalidAddressSet",
            this.getName(),
            newDecoderType,
            newAddress
        );

        return false;
    }
       
    /**
     * Checks if the passed locomotive has the same effective address as this one
     * @param l
     * @return 
     */
    @Override
    public boolean hasEquivalentAddress(Locomotive l)
    {
        if (l == null || !(l instanceof MarklinLocomotive)) return false;
        
        return l.getAddress() == this.getAddress() && l.getDecoderType() == this.getDecoderType();
    }
    
    /**
     * Gets the decoder type
     * @return 
     */
    @Override
    public decoderType getDecoderType()
    {
        return this.type;
    }
    
    @Override
    public Locomotive execRoute(String name)
    {
        this.network.execRoute(name);
        
        return this;
    }
    
    @Override
    public boolean isFeedbackSet(String name)
    {
        return this.network.isFeedbackSet(name);
    }
    
    @Override
    public boolean getAccessoryState(int id, Accessory.accessoryDecoderType type)
    {
        return this.network.getAccessoryState(id, type);
    }

    @Override
    public boolean getFeedbackState(String name)
    {
        return this.network.getFeedbackState(name);
    }
    
    @Override
    synchronized public void parseMessage(CS2Message m)
    {
        // Double-check the UID just in case        
        if (m.extractUID() != UID)
        {
            return;
        }
                      
        if (m.getCommand().equals(CS2Message.CMD_LOCO_DIRECTION))
        {
            if (m.getLength() == 5)
            {
                int direction = CS2Message.mergeBytes(
                    new byte[] {m.getData()[4]}
                );
                                
                if (direction == 1)
                {
                    // Reset speed if direction changed
                    if (this.getDirection() == locDirection.DIR_BACKWARD)
                    {
                        this._setSpeed(0);
                    }
                    
                    this._setDirection(locDirection.DIR_FORWARD);
                }
                else if (direction == 2)
                {
                    // Reset speed if direction changed
                    if (this.getDirection() == locDirection.DIR_FORWARD)
                    {
                        this._setSpeed(0);
                    }
                    
                    this._setDirection(locDirection.DIR_BACKWARD);
                }
                
                this.network.logf(
                    "loc.settingDirection",
                    this.getName(),
                    (this.goingForward() ? I18n.t("loc.forwardShort") : I18n.t("loc.backwardShort"))
                );
            }
        }
        else if (m.getCommand().equals(CS2Message.CMD_LOCO_FUNCTION))
        {
            if (m.getLength() == 6)
            {
                int fNumber = m.getData()[4];
                boolean fValue = m.getData()[5] != 0;
                
                this._setF(fNumber, fValue);
                
                this.network.logf(
                    "loc.settingFunction",
                    this.getName(),
                    fNumber,
                    (fValue ? "1" : "0")
                );
            }
        }
        else if (m.getCommand().equals(CS2Message.CMD_LOCO_VELOCITY))
        {
            if (m.getLength() == 6)
            {
                int speed = CS2Message.mergeBytes(
                    new byte[] {m.getData()[4], m.getData()[5]}
                );
                
                // ROUNDED, NOT TRUNCATED (C7).
                //
                // The Central Station speaks 0-1000 and this model stores 0-100, so every received
                // speed is scaled.  Integer division always rounds DOWN, so a fine step set on
                // another controller came back as the step below it - and `syncFromState` then
                // re-sends what it believes, which put the train back slightly slower than the
                // operator had just set it.  A systematic bias in one direction rather than a
                // rounding error either way.
                //
                // It matters more than a display detail now: the length and blocking rules reason
                // about what the model BELIEVES a train is doing, and this is where that belief
                // comes in from the outside world.
                speed = (int) Math.round(speed / 10.0);
                
                this._setSpeed(speed);
                
                this.network.logf(
                    "loc.settingSpeed",
                    this.getName(),
                    speed
                );
            }
        }
    }
    
    /**
     * Simulates instant stop functionality for MM2 locomotives
     * @return 
     */
    @Override
    synchronized public Locomotive instantStop()
    {
        if (this.type == MarklinLocomotive.decoderType.MM2)
        {
            return this.switchDirection().switchDirection().stop();
        }
        else
        {
            return stop();
        }
    }
    
    @Override
    synchronized public Locomotive stop()
    {        
        // Pass through commands
        for (Locomotive l : this.linkedLocomotives.keySet())
        {
            l.stop();
        }
        
        // Send stop command
        this.network.exec(new CS2Message(
            CS2Message.CMD_SYSTEM,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              CS2Message.CMD_SYSSUB_TRAINSTOP
            }
        ));
        
        // Added code to guarantee state sync
        if (this.getSpeed() > 0)
        {
            this.setSpeed(0);
        }
        
        return this;
    }
    
    @Override
    synchronized public Locomotive syncFromNetwork()
    {
        // Pass through commands
        for (Locomotive l : this.linkedLocomotives.keySet())
        {
            l.syncFromNetwork();
        }
        
        // Query speed
        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_VELOCITY,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID
            }
        ));
                
        // Query functions
        for (byte i = 0; i < this.getNumF(); i++)
        {
            this.network.exec(new CS2Message(
                CS2Message.CMD_LOCO_FUNCTION,
                new byte[]
                {
                  (byte) (UID >> 24), 
                  (byte) (UID >> 16), 
                  (byte) (UID >> 8), 
                  (byte) UID,
                  i
                }
            ));
        }
                
        // Query direction
        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_DIRECTION,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID
            }
        ));
        
        return this;
    }

    @Override
    synchronized public Locomotive syncFromState()
    {
        // Pass through commands
        for (Locomotive l : this.linkedLocomotives.keySet())
        {
            l.syncFromState();
        }
        
        // Send out speed command
        this.setSpeed(this.getSpeed());
        
        // Send out function command
        for (int i = 0; i < this.getNumF(); i++)
        {
            this.setF(i, this.getF(i));
        }
        
        // Send out direction command
        this.setDirection(this.getDirection());
        
        return this;
    }
    
    /**
     * Says, once, that this locomotive has been waiting a long time for a sensor.
     *
     * The operator otherwise has nothing at all to go on: a train that fails to start, or that takes a
     * different route from the one it was given, simply stops being mentioned - it is waiting for a
     * sensor it will never reach, and the wait is silent and endless by design.  Nothing here changes
     * that design; it only says so out loud, which is the half that was missing.
     *
     * @param feedbackName the sensor being waited for
     * @param waitedMs how long the wait has gone on
     */
    @Override
    protected void waitedTooLongFor(String feedbackName, long waitedMs)
    {
        if (this.network == null) return;

        // Off this thread, because this thread is holding the lock every sensor event needs.
        //
        // The hook is called from inside synchronized(monitor), and that monitor is STATIC - one for
        // every locomotive in the application - so Feedback._setState cannot run while it is held, and
        // s88 events arrive on a single thread.  The log call goes to a java.util.logging handler
        // writing to System.out, which blocks whenever its consumer does: run from the IDE with output
        // to the editor window, and a slow console write would stop arrival detection for EVERY train
        // under way, not just this one.
        //
        // The base class's javadoc says this hook must not block.  This was the only override, and it
        // did.  A single thread rather than a pool: these are strictly ordered notices about the same
        // railway, and two of them interleaved would read as one.
        final String said = this.getName();
        final long elapsed = waitedMs;

        ADVISORIES.submit(() -> this.network.logf("autolayout.warnLocomotiveWaitingLong",
            said, feedbackName, Math.round(elapsed / 60000.0)));
    }

    /**
     * The one thread that says these out loud.
     *
     * A daemon, so it cannot keep the application alive on its own - an advisory that has not been
     * printed is not a reason to refuse to shut down.
     */
    private static final java.util.concurrent.ExecutorService ADVISORIES =
        java.util.concurrent.Executors.newSingleThreadExecutor(runnable ->
        {
            Thread thread = new Thread(runnable, "TrainControl stuck-train advisories");

            thread.setDaemon(true);

            return thread;
        });

    @Override
    synchronized public Locomotive setSpeed(int speed)
    {
        // CLAMPED BEFORE ANYTHING IS SENT (S14-B2, NSV-C4).
        //
        // _setSpeed is wrapped in `if (speed >= 0 && speed <= 100)` with no else, so an out-of-range
        // value is DISCARDED: getSpeed() still reports whatever it was, and the head then re-transmits
        // that old speed while every member is sent the scaled value clamped to 100.  The members were
        // protected from this and the head was not, one level above where the member clamp was written -
        // and its comment describes exactly this outcome, "the two engines of one consist pulled against
        // each other".
        //
        // Clamped to 0 rather than to -1, which is the opposite of RouteCommand's rule and deliberately
        // so: -1 means instant stop to a ROUTE, and execRoute calls instantStop() for it rather than
        // coming here.  This method's argument is a speed.
        speed = Math.max(0, Math.min(speed, 100));

        // Pass through commands
        for (Map.Entry<Locomotive, Double> entry : this.linkedLocomotives.entrySet())
        {
            double scaledSpeed = speed * Math.abs(entry.getValue()); 
            int roundedSpeed;
            
            if (Math.abs(entry.getValue()) > 1)
            {
                roundedSpeed = (int) Math.ceil(scaledSpeed);
            }
            else
            {
                roundedSpeed = (int) Math.floor(scaledSpeed);
            }

            // Clamped, because multipliers up to 2 are accepted and the scaled value can therefore
            // exceed the 0-100 range _setSpeed will store.  _setSpeed IGNORES an out-of-range value
            // rather than clamping it, and the member then transmits its previous speed - so past the
            // threshold (67 for a 1.5x member) the member silently froze while the head kept
            // accelerating, and the two engines of one consist pulled against each other.
            // TWO-SIDED (NSV-C4).  A negative argument scaled by a multiplier stays negative, and
            // _setSpeed ignores it for the same reason it ignores 150 - so the member re-transmitted
            // its old speed at this end of the range too.
            roundedSpeed = Math.max(0, Math.min(roundedSpeed, 100));

            entry.getKey().setSpeed(roundedSpeed);
        }
        
        // Force last known direction if this is the first command to move
        if (this.lastStartTime == 0)
        {
            this.setDirection(this.getDirection());
        }
                
        super._setSpeed(speed);
        
        int newSpeed = this.getSpeed() * 10;
     
        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_VELOCITY,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              (byte) (newSpeed >> 8),
              (byte) newSpeed
            }
        ));	
        
        return this;
    }

    @Override
    synchronized public Locomotive setDirection(locDirection direction)
    {
        // Pass through commands
        for (Map.Entry<Locomotive, Double> entry : this.linkedLocomotives.entrySet())
        {
            if (entry.getValue() < 0)
            {
                entry.getKey().setDirection(direction == locDirection.DIR_FORWARD ? locDirection.DIR_BACKWARD : locDirection.DIR_FORWARD);
            }
            else
            {
                entry.getKey().setDirection(direction);
            }
        }
        
        // Mark that we have alreay corrected the locomotive direction 
        if (this.lastStartTime == 0)
        {
            this.lastStartTime = -1;
        }
          
        super._setDirection(direction);
        
        int newDirection = (direction == locDirection.DIR_FORWARD ? 1 : 2);
        
        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_DIRECTION,
            new byte[]
            {
              (byte) (UID >> 24), 
              (byte) (UID >> 16), 
              (byte) (UID >> 8), 
              (byte) UID,
              (byte) newDirection
            }
        ));
        
        return this;
    }
    
    /**
     * Records a function and sends its command to the station, whatever this decoder's function count
     * says (MT-359).
     *
     * The count bounds what is RECORDED - `_setF` ignores a number past it - and nothing else: the
     * command goes to the station either way, which decides what it means for the decoder it reaches.
     * Called for the head and each member by `setF`, after `setF` has refused anything beyond the whole
     * consist's range.
     *
     * @param fNumber the function
     * @param state on or off
     */
    private synchronized void sendFunction(int fNumber, boolean state)
    {
        super._setF(fNumber, state);

        this.network.exec(new CS2Message(
            CS2Message.CMD_LOCO_FUNCTION,
            new byte[]
            {
              (byte) (UID >> 24),
              (byte) (UID >> 16),
              (byte) (UID >> 8),
              (byte) UID,
              (byte) fNumber,
              (byte) (state ? 1 : 0)
            }
        ));
    }

    @Override
    synchronized public Locomotive setF(int fNumber, boolean state)
    {
        // THE CONSIST'S RANGE, WHICH IS THE HIGHEST OF ITS MEMBERS (Adam, MT-359, 2026-09-11).
        //
        // *"Make the allowed function be the highest possible for the consist - so two MM2 locs mean
        // F0-4 do something.  one mm2 loc and one dcc/mfx mean all functions are unlocked."*
        //
        // S14-B1 had this refuse anything the HEAD could not do, which fixed the real defect - the state
        // going nowhere - and answered the wrong question with it: the point of a consist is that a
        // member can do things the head cannot, and an MM2 head with an MFX member is a configuration
        // the program allows on purpose.
        //
        // What stays fixed is the part that mattered.  A function outside the whole consist's range is
        // still refused rather than sent and forgotten, `getF` answers for the members where the head's
        // own decoder has no such function, and `functionsOff` clears the same range it can set - so
        // nothing can be switched on that nothing can switch off, which is what made the original defect
        // worth a finding.
        if (fNumber < 0 || fNumber >= this.drivableFunctionCount()) return this;

        // EVERY LOCOMOTIVE IN THE CONSIST IS SENT THE COMMAND, HEAD INCLUDED (Adam, MT-359, 2026-09-13).
        //
        // *"Pressing F6 on the MM2 head, if it has a MFX/DCC member, should simply propagate a F6 command
        // to all the consist's locomotives as per normal.  The CS will figure out whether that means
        // anything."*  Asked whether the range above stays: **"Send to all, keep the cap."**
        //
        // This asked each member through its own `setF`, whose `validF` refused a number its decoder does
        // not list - so the MM2 member of an MFX-headed consist never heard f6, and an MM2 head skipped
        // its own copy.  Whether a decoder does anything with f6 is the station's business, and a
        // function count is a guess at it.  What is recorded locally is still bounded by each
        // locomotive's own count (`_setF`), so no state appears for a function nothing has; `getF` on
        // the head answers for the consist, as before.
        for (Locomotive l : this.linkedLocomotives.keySet())
        {
            if (l instanceof MarklinLocomotive)
            {
                ((MarklinLocomotive) l).sendFunction(fNumber, state);
            }
            else
            {
                l.setF(fNumber, state);
            }
        }

        // Force last known direction if this is the first command to move
        if (this.lastStartTime == 0)
        {
            this.setDirection(this.getDirection());
        }

        this.sendFunction(fNumber, state);

        return this;
    }
    
    @Override
    synchronized public Locomotive setAccessoryState(int id, Accessory.accessoryDecoderType type, boolean state)
    {
        // TODO decoder type should be passed in
        this.network.setAccessoryState(id, type, state);
        
        return this;
    }
    
    /**
     * Turns a function on for the default pulse function duration, then back off
     * @param f
     * @return 
     */
    @Override
    public Locomotive toggleF(int f)
    {
        return this.toggleF(f, PULSE_FUNCTION_DURATION);
    }
    
    /**
     * Prints a human-readable decoder type and address (1 or greater) based on a raw UID.
     * Used to identify a locomotive we have no record of, so it names the protocol - an address alone
     * does not say which locomotive to look for.
     * @param UID
     * @return
     */
    public static String addressFromUID(int UID)
    {
        // Tested from the highest base downwards.  DCC_BASE (0xc000) is above MFX_BASE (0x4000), so
        // testing MFX first caught every DCC and multi-unit UID as well and reported it as an MFX
        // address - the DCC and multi-unit branches could never be reached.
        if (UID > DCC_BASE)
        {
            return decoderType.DCC.name() + " " + (UID - DCC_BASE);
        }
        else if (UID > MFX_BASE)
        {
            return decoderType.MFX.name() + " " + (UID - MFX_BASE);
        }
        else if (UID > MULTI_UNIT_BASE)
        {
            return decoderType.MULTI_UNIT.name() + " " + (UID - MULTI_UNIT_BASE);
        }
        else
        {
            return decoderType.MM2.name() + " " + UID;
        }
    }
    
    /**
     * User-friendly string representation of the decoder type
     * Should only be used in the UI/log messages, not as authoritative data
     * @return 
     */
    @Override
    public String getDecoderTypeLabel()
    {
        switch (this.type)
        {
            case MULTI_UNIT:
                return I18n.t("loc.multiUnit");
            default:
                if (this.hasLinkedLocomotives())
                {
                    return I18n.f("loc.multiUnitLinked", type.name());
                }
                else
                {
                    return type.name();
                }
        }
    }
        
    @Override
    public String toString()
    {        
        return super.toString() + "\n" +
            "UID: " + Conversion.intToHex(this.UID) + "\n" +
            "Address: " + Conversion.intToHex(this.address) + "\n" +
            "Type: " + (this.type == decoderType.MFX ? "MFX" : (this.type == decoderType.DCC ? "DCC" : "MM2"));                
    }
    
    /**
     * Identity, deliberately.  Do NOT reimplement either of these in terms of the locomotive's
     * fields.  Name, address and decoder type are all mutable in place - rename assigns the name,
     * setAddress the address and type - so a field-based hashCode changes underneath any hash
     * container holding the locomotive.  The object stays in it and iteration still finds it, but
     * containsKey, get and remove all miss: the entry sits in a bucket its hash no longer
     * points to.
     *
     * That produced six separate defects across three reviews, each fixed where it happened to surface
     * - a consist that stopped recognising a member, a station exclusion that silently stopped applying,
     * a deleted locomotive still being exported.  Eight collections key on this object, and each repair
     * was a call some future author had to know to make.  Identity ends that: the hash is fixed for the
     * object's lifetime, so no mutation can move it.
     *
     * Nothing was relying on value equality.  Locomotive names are unique and the code enforces it, so
     * two distinct live locomotives could never be value-equal in the first place - identity and the
     * old equality already agreed everywhere they were used, and every comparison in the codebase is
     * between live objects from the same database.  The database keys by UID and name, never by the
     * object.
     *
     * The logical checks live where they belong and are unchanged: hasEquivalentAddress for the
     * address-and-protocol comparison, getName for the name, getIntUID for both together.  Reach for
     * those, not for equals.
     *
     * This also removes a latent deserialization hazard: linkedLocomotives is a non-transient
     * Map<Locomotive, Double> on a Serializable class, and HashMap.readObject hashes each key while the
     * key may still be only partly restored.  An identity hash does not depend on field state.
     * @param other
     */
    @Override
    public boolean equals(Object other)
    {
        return this == other;
    }

    @Override
    public int hashCode()
    {
        return System.identityHashCode(this);
    }
    
    /**
     * Returns a reference to the model.  Useful for callbacks
     * @return 
     */
    @Override
    public MarklinControlStation getModel()
    {
        return network;
    }
    
    /**
     * Checks if this locomotive is linked to another as a multi-unit
     * @param l
     * @return 
     */
    @Override
    public boolean isLinkedTo(Locomotive l)
    {
        // For strict enforcement, "Our linked locomotives have the same address as the other locomotive" can be placed here
        // This would disallow MU1 and MU2 from being linked to locomotives that share the same address, even if they have a different name
        
        return this.linkedLocomotives.containsKey((MarklinLocomotive) l);
    }
    
    /**
     * Every locomotive this one commands when it is driven - itself excluded (X8-A2).
     *
     * **Two ways of being a consist, one question.**  A locomotive may command others because somebody
     * linked them in TrainControl (`linkedLocomotives`) or because the Central Station holds it as a
     * multi-unit (`modelMultiUnitLocomotives`), and the two are exclusive:
     * `setLinkedLocomotives` clears the links and returns -1 for a `MULTI_UNIT`, because *"multi units
     * defined in the Central Station cannot be linked to other locomotives"*.
     *
     * It existed as two spellings inside `isSimultaneousMultiUnitCompatible`, kept in step by
     * somebody remembering - and they were not: the second of the two loops asked the raw links, which
     * for a Central Station multi-unit is always empty, so two of them driving the same locomotive
     * read as safe to run together.  Asked once here so that cannot happen again.
     *
     * @return the locomotives this one drives, empty when it drives only itself
     */
    public Collection<Locomotive> commandedLocomotives()
    {
        if (this.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
        {
            return this.getModelMultiUnitLocomotives();
        }

        return this.getLinkedLocomotives().keySet();
    }

    /**
     * NOT SYMMETRIC, AND BOTH DIRECTIONS HAVE TO BE ASKED (X8V-C8).
     *
     * `a.isSimultaneousMultiUnitCompatible(b)` and `b.isSimultaneousMultiUnitCompatible(a)` can
     * disagree, because the question is asked from the left-hand locomotive's point of view: it walks
     * what THIS one commands, and a plain member commands nothing.  Measured on a Central Station
     * multi-unit at address 4003 holding a member at 62: asked one way round the answer is false, the
     * other way true.
     *
     * `Layout.clearMultiUnitConflictsWith` is the only production caller - reached from placing, loading and every
     * edit door's sweep - and it asks both ways.  A second caller
     * that asked once would let the pair through half the time, which is the shape `X8-A2` was: a
     * missing branch in one of the two loops made both directions answer the same wrong thing, and the
     * both-ways call could not compensate because neither direction knew.
     *
     * Checks if this locomotive can be in a multi-unit with another, at the same time, based
     * on whether it is already linked to another as a multi-unit, or has the same address
     * Stricter check than isLinkedTo and is used by the autonomy layout to minimize errors
     * @param l
     * @return 
     */
    @Override
    public boolean isSimultaneousMultiUnitCompatible(Locomotive l)
    {
        if (l == null || !(l instanceof Locomotive)) return false;
        
        // This check would normally be enough, but for completeness we should also check for shared addresses...
        if (this.isLinkedTo(l))
        {
            return false;
        }
        
        // ONE QUESTION, ASKED OF BOTH SIDES (X8-A2).
        //
        // "Which locomotives does this one command?" had two spellings here - a decoder-type branch in
        // the first loop and the raw `getLinkedLocomotives()` in the second - and the second loop is
        // the one that compares MY members against YOURS.  So two Central Station multi-units driving
        // the same locomotive read as safe to run as two trains: both were placed on the graph, both
        // dispatched, and every speed and direction command sent to one fanned out to a decoder the
        // other was also driving.
        //
        // It was not merely likely to miss that case, it was provably dead for it:
        // `setLinkedLocomotives` clears `linkedLocomotives` and returns -1 for a MULTI_UNIT - *"multi
        // units defined in the Central Station cannot be linked to other locomotives"* - so the whole
        // body of the second loop could never run for exactly the case the first loop's branch was
        // written for.
        //
        // `Layout.clearMultiUnitConflictsWith` asks this both ways round and cannot compensate: both directions
        // had the same gap when both sides are multi-units, so neither answered.
        Collection<Locomotive> ours = this.commandedLocomotives();

        for (Locomotive other : ours)
        {
            if (other.hasEquivalentAddress((MarklinLocomotive) l))
            {
                return false;
            }
        }

        // And what the other one commands, against what we command.
        Collection<Locomotive> theirs = ((MarklinLocomotive) l).commandedLocomotives();

        for (Locomotive other : ours)
        {
            for (Locomotive other2 : theirs)
            {
                if (other.hasEquivalentAddress(other2) || other.equals(other2))
                {
                    return false;
                }
            }
        }
        
        // We can't have the same address as the other locomotive
        return !this.hasEquivalentAddress((MarklinLocomotive) l);
    }
    
    /**
     * Sets a list of locomotives to link to this one. Must call setLinkedLocomotives after this, i.e. once the model has loaded all locs
     * @param locList
     */
    @Override
    public void preSetLinkedLocomotives(Map<String, Double> locList)
    {
        this.preLinkedLocomotives = locList;
    }
    
    /**
     * Stages a list and applies it in one call, so no second thread can be staging at the same time.
     *
     * **`preSetLinkedLocomotives` writes an INSTANCE FIELD and `setLinkedLocomotives` reads it, with
     * nothing serialising the pair** (NSV-B2).  Two threads rebuild consists: `syncWithCS2`, which the
     * window runs off the event thread, and the multi-unit dialog, on the event thread.  One thread's
     * staged list could be overwritten before its own apply read it, and the consist was then rebuilt
     * from the other thread's list - silently, because both calls succeed.
     *
     * Every caller that has its list in hand should use this.  The two-call form remains for the one
     * caller that cannot: `restoreState` stages each consist while the locomotives are still being
     * loaded and applies them all afterwards, because a member cannot be resolved by name until it
     * exists.  That runs before the window is built, so nothing else is staging.
     *
     * @param locList member name to speed adjustment, as `preSetLinkedLocomotives` takes it
     * @return the number of members linked, or -1 where a Central Station multi-unit refuses linking
     */
    public int setLinkedLocomotives(Map<String, Double> locList)
    {
        return this.applyLinkedLocomotives(locList);
    }

    /**
     * Processes the preset list and maps locomotives to be linked to this one
     * @return
     */
    @Override
    public int setLinkedLocomotives()
    {
        return this.applyLinkedLocomotives(this.preLinkedLocomotives);
    }

    /**
     * Validates a list of member names and publishes the consist it describes.
     *
     * @param preLinkedLocomotives the staged list; null or a Central Station multi-unit clears
     * @return the number of members linked, or -1
     */
    private int applyLinkedLocomotives(Map<String, Double> preLinkedLocomotives)
    {              
        // Staged in a local map and swapped in one step, rather than clearing the live map and
        // refilling it in place.  setSpeed and setDirection iterate linkedLocomotives under this
        // locomotive's monitor; rebuilding it unsynchronised let a fan-out land mid-rebuild and either
        // throw ConcurrentModificationException or command the head alone.  That was tolerable while
        // only the multi-unit dialog rebuilt consists - a deliberate action on a consist the user is
        // editing - but a Central Station sync now rebuilds them too, automatically, and a consist can
        // be driven manually at the same time.
        //
        // The validation itself stays outside the lock: canBeLinkedTo logs through the UI, and holding
        // a locomotive's monitor across that is its own hazard.
        Map<Locomotive, Double> staged = new LinkedHashMap<>();

        // Multi-units defined in the Central Station cannot be linked to other locomotives
        if (preLinkedLocomotives == null || !(preLinkedLocomotives instanceof Map) 
                || this.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
        {
            // One assignment, under the monitor - see the publish below.
            synchronized (this)
            {
                this.linkedLocomotives = java.util.Collections.emptyMap();
            }

            return -1;
        }
        
        for (Map.Entry<String, Double> entry : preLinkedLocomotives.entrySet())
        {
            String locoName = entry.getKey();
            Double value = entry.getValue();
            
            MarklinLocomotive loco = network.getLocByName(locoName);
            
            // Validate speed adjustment
            if (value < -2 || value > 2 || value == 0)
            {
                this.network.logf(
                    "loc.errorLinkedLocSpeedAdjustment"
                );
            }
            // Validate locomotive & configure
            else if (this.canBeLinkedTo(loco, true, staged.keySet()))
            {
                staged.put(loco, value);
            }
        }
        
        // ONE ASSIGNMENT, of a map nothing will edit again - see the field.  This is what the staging
        // above was already most of the way towards; what it still did was clear() then putAll() on the
        // instance every reader holds.
        //
        // UNDER THE MONITOR, WHICH IS NOT ABOUT THE READERS (FV3-B2).  They read a volatile reference
        // and take no lock, which is the whole point of the design.  This excludes the other WRITER:
        // `unlinkLocomotive` is a read-copy-write on the same field and is synchronized, so without
        // this a rebuild overlapping an unlink would silently discard one of them - and the one that
        // loses is either a deleted locomotive put back into a consist, or a freshly synced consist
        // reverted.  The publish before this change was inside `synchronized (this)` and excluded it
        // for free; the exclusion went out with the clear-and-refill.
        //
        // The monitor is held for a field assignment and nothing else - not across the validation
        // above, which logs through the UI, and not across a fan-out, which sends UDP.
        synchronized (this)
        {
            this.linkedLocomotives = java.util.Collections.unmodifiableMap(staged);
        }

        // Ensure the correct direction - commands should automatically cascade
        if (!staged.isEmpty())
        {
            this.setDirection(this.getDirection());
        }

        return staged.size();
    }
    
    /**
     * How many functions this locomotive can drive, counting the consist it heads.
     *
     * Adam's rule, MT-359: *"the allowed function [is] the highest possible for the consist"*.  Two MM2
     * locomotives give five; one MM2 and one MFX give thirty-two, and all of them are reachable because
     * the MFX member can do them.
     *
     * A locomotive with no members answers exactly what it always did.
     *
     * @return the largest function count in this consist, including the head's own
     */
    @Override
    public int drivableFunctionCount()
    {
        int most = this.getNumF();

        for (Locomotive l : this.linkedLocomotives.keySet())
        {
            if (l != null && l.getNumF() > most) most = l.getNumF();
        }

        return most;
    }

    /**
     * Whether a function is on, asking the members for the ones the head's own decoder does not have.
     *
     * **NOT by widening `validF`**, which would be the obvious way and is wrong: `Locomotive.getF`
     * tests `validF` and then indexes `functionState`, an array sized to this decoder's own function
     * count, so a widened range would read past the end of it.
     *
     * The head's own functions answer from the head.  Beyond them the question is about the consist, and
     * the consist's answer is its members' - which is also the only place that state exists.
     *
     * @param fNumber the function
     * @return whether it is on
     */
    @Override
    public boolean getF(int fNumber)
    {
        if (fNumber >= 0 && fNumber < this.getNumF()) return super.getF(fNumber);

        if (fNumber < 0) return false;

        for (Locomotive l : this.linkedLocomotives.keySet())
        {
            if (l != null && l.getF(fNumber)) return true;
        }

        return false;
    }

    /**
     * Turns off every function this consist can turn on.
     *
     * The base walks `getNumF()`, which is the head's own count - so on a consist whose members have
     * more functions than the head, it could switch one on and not off again.  That asymmetry is the
     * defect S14-B1 was filed for, and it survives the change of rule: whatever range `setF` accepts,
     * this has to clear.
     *
     * @return this
     */
    @Override
    public Locomotive functionsOff()
    {
        for (int i = 0; i < this.drivableFunctionCount(); i++)
        {
            if (this.getF(i))
            {
                this.setF(i, false).delay(FUNCTION_DELAY_MS);
            }
        }

        return this;
    }

    /**
     * Gets the list of linked locomotives (names only - suitable for export)
     * @return
     */
    @Override
    public Map<String, Double> getLinkedLocomotiveNames()
    {
        HashMap<String, Double> locomotiveNames = new HashMap<>();
        
        for (Map.Entry<Locomotive, Double> entry : this.linkedLocomotives.entrySet())
        {
            String locoName = entry.getKey().getName();
            Double value = entry.getValue();
            locomotiveNames.put(locoName, value);
        }
        
        return locomotiveNames;
    }
        
    /**
     * Checks if the passed locomotive can be linked as a multi-unit to the current one
     * @param other
     * @param logError
     * @return 
     */
    @Override
    public boolean canBeLinkedTo(Locomotive other, boolean logError)
    {
        return canBeLinkedTo(other, logError, this.linkedLocomotives.keySet());
    }

    /**
     * As canBeLinkedTo, but checking the address-conflict rule against a supplied set of members
     * rather than the live one.
     *
     * setLinkedLocomotives stages its rebuild in a local map so that the live map is replaced in one
     * step - see there - and the conflict check has to see the members staged so far, not the ones
     * still in the live map from the previous configuration.
     *
     * @param other
     * @param logError
     * @param existingMembers members to test the address-conflict rule against
     * @return 
     */
    private boolean canBeLinkedTo(Locomotive other, boolean logError, java.util.Collection<Locomotive> existingMembers)
    {
        String error = null;

        if (other == null || !(other instanceof MarklinLocomotive))
        {
            error = I18n.f("loc.errorLinkInvalidObject", this.getName());
        }
        else if (this.equals(other))
        {
            error = I18n.f("loc.errorLinkSelf", this.getName(), other.getName());
        }
        else if (other.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
        {
            error = I18n.f("loc.errorLinkMultiUnitCentral", this.getName(), other.getName());
        }
        else if (other.hasLinkedLocomotives())
        {
            error = I18n.f("loc.errorLinkMultiUnitSelf", this.getName(), other.getName());
        }
        else if (this.hasEquivalentAddress(other))
        {
            error = I18n.f("loc.errorLinkSameAddress", this.getName(), other.getName());
        }
        else
        {
            for (Locomotive l : existingMembers)
            {
                if (l.hasEquivalentAddress(other))
                {
                    error = I18n.f("loc.errorLinkAddressConflict", this.getName(), other.getName(), l.getName());
                    break;
                }
            }
        }
        
        if (logError && error != null)
        {
            this.network.log(error);
        }
        
        return error == null;
    }
    
    /**
     * Gets the list of linked locomotives
     * @return 
     */
    @Override
    public Map<Locomotive, Double> getLinkedLocomotives()
    {
        // Unmodifiable at the door as well as at the assignment.  Every published value is already
        // unmodifiable, and this covers the one path that does not come from setLinkedLocomotives: a
        // MarklinLocomotive restored by Java serialization, whose map comes back as whatever was
        // written.  Nothing in src/ or test/ mutates what this returns - checked - so wrapping costs a
        // caller nothing and closes the door for the next one.
        return java.util.Collections.unmodifiableMap(this.linkedLocomotives);
    }
    
    /**
     * Returns true if this locomotive is linked to others as part of a multi-unit
     * @return 
     */
    @Override
    public boolean hasLinkedLocomotives()
    {
        return !this.linkedLocomotives.isEmpty();
    }

    /**
     * Removes a locomotive from this one's multi-unit, if it is a member.
     *
     * synchronized, on the same lock setSpeed and setDirection hold: those iterate
     * linkedLocomotives, which is a plain LinkedHashMap, so removing from it on another thread -
     * deleting a locomotive while its consist is being driven - could otherwise throw
     * ConcurrentModificationException part-way through a fan-out, leaving some members commanded
     * and others not.
     *
     * @param member the locomotive to remove
     * @return true if it was a member and has been removed
     */
    synchronized public boolean unlinkLocomotive(Locomotive member)
    {
        Map<Locomotive, Double> current = this.linkedLocomotives;

        if (!current.containsKey(member)) return false;

        // COPIED, not edited, for the reason the field gives: a save or a fan-out may be holding this
        // map, and removing from underneath it is the same defect as rebuilding in place.  The javadoc
        // above described the monitor as what made the removal safe; the monitor only ever covered the
        // readers that take it, which the save path does not.
        Map<Locomotive, Double> without = new LinkedHashMap<>(current);

        without.remove(member);

        this.linkedLocomotives = java.util.Collections.unmodifiableMap(without);

        return true;
    }
    
    /**
     * For informational purposes, sets the locomotives linked to this multi unit in the central station
     * @param l 
     */
    @Override
    public void setModelMultiUnitLocomotives(Map<String, Double> l)
    {
        if (this.getDecoderType() == Locomotive.decoderType.MULTI_UNIT)
        {
            this.centralStationMultiUnitLocomotiveNames = l;
        }
    }
    
    /**
     * Fetches the raw list of central station multi unit locomotives
     * @return 
     */
    @Override
    public Map<String, Double> getModelMultiUnitLocomotiveNames()
    {
        return centralStationMultiUnitLocomotiveNames;
    }
    
    /**
     * Returns the locomotives linked to this multi unit in the central station
     * @return 
     */
    @Override
    public List<Locomotive> getModelMultiUnitLocomotives()
    {  
        List<Locomotive> output = new ArrayList<>();
        
        if (this.getDecoderType() == Locomotive.decoderType.MULTI_UNIT && this.centralStationMultiUnitLocomotiveNames != null)
        {
            for (String s : this.centralStationMultiUnitLocomotiveNames.keySet())
            {
                if (this.network.getLocByName(s) != null)
                {
                    output.add(this.network.getLocByName(s));
                }
            }
        }
        
        return output;
    }
    
    @Override
    public int getPulseFunctionDuration()
    {
        return PULSE_FUNCTION_DURATION;
    }
}
