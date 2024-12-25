package org.traincontrol.marklin;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.traincontrol.marklin.udp.CS2Message;
import org.traincontrol.util.Conversion;
import org.traincontrol.base.Locomotive;
import org.traincontrol.base.RemoteDevice;

/**
 * Marklin locomotive that implements CS2 interfaces/protocols
 * @author Adam
 */
public class MarklinLocomotive extends Locomotive
    implements java.io.Serializable, RemoteDevice<Locomotive, CS2Message>
{        
    public static enum decoderType {MFX, MM2, DCC, MULTI_UNIT};
    
    /* Constants */
    
    public static final int MFX_NUM_FN = 32;
    public static final int DCC_NUM_FN = 29;
    public static final int MM2_NUM_FN = 5;
    public static final int MM2_MAX_ADDR = 80;
    public static final int MFX_MAX_ADDR = 0x3FFF;
    public static final int DCC_MAX_ADDR = 2048;
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
    private final Map <MarklinLocomotive, Double> linkedLocomotives = new LinkedHashMap<>();     
    private Map <String, Double> preLinkedLocomotives;
     
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
    public void setCustomFunctions(boolean state)
    {
        this.customFunctions = state;
    }
    
    /**
     * Returns whether the functions have been customized
     * @return 
     */
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
    public int getNumFnIcons()
    {
        if (this.network.isCS3())
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
    public int sanitizeFIconIndex(int fType)
    {
        if (this.network.isCS3())
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
     * @param yellow
     * @return 
     */
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
                this.network.log("Missing local function icon: " + iconName);
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

            return true;
        }
        
        this.network.log("Warning: invalid address passed to " + this.getName() + " setAddress: " + newDecoderType + " " + newAddress);
        return false;
    }
       
    /**
     * Gets the decoder type
     * @return 
     */
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
    public boolean getAccessoryState(int id)
    {
        return this.network.getAccessoryState(id);
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
                
                this.network.log("Setting " + this.getName() 
                    + " loc direction " + (this.goingForward() ? "f" : "b"));
            }
        }
        else if (m.getCommand().equals(CS2Message.CMD_LOCO_FUNCTION))
        {
            if (m.getLength() == 6)
            {
                int fNumber = m.getData()[4];
                boolean fValue = m.getData()[5] != 0;
                
                this._setF(fNumber, fValue);
                
                this.network.log("Setting " + this.getName() 
                    + " loc f" + fNumber + " " + (fValue ? "1" : "0"));
            }
        }
        else if (m.getCommand().equals(CS2Message.CMD_LOCO_VELOCITY))
        {
            if (m.getLength() == 6)
            {
                int speed = CS2Message.mergeBytes(
                    new byte[] {m.getData()[4], m.getData()[5]}
                );
                
                speed /= 10;
                
                this._setSpeed(speed);
                
                this.network.log("Setting " + this.getName() 
                    + " loc speed " + speed);
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
        for (MarklinLocomotive l : this.linkedLocomotives.keySet())
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
        for (MarklinLocomotive l : this.linkedLocomotives.keySet())
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
        for (MarklinLocomotive l : this.linkedLocomotives.keySet())
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
    
    @Override
    synchronized public Locomotive setSpeed(int speed)
    {
        // Pass through commands
        for (Map.Entry<MarklinLocomotive, Double> entry : this.linkedLocomotives.entrySet())
        {
            double scaledSpeed = speed * Math.abs(entry.getValue()); 
            int roundedSpeed = (int) Math.ceil(scaledSpeed); 
            entry.getKey().setSpeed(roundedSpeed);;
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
        for (Map.Entry<MarklinLocomotive, Double> entry : this.linkedLocomotives.entrySet())
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
    
    @Override
    synchronized public Locomotive setF(int fNumber, boolean state)
    {
        // Pass through commands
        for (MarklinLocomotive l : this.linkedLocomotives.keySet())
        {
            l.setF(fNumber, state);
        }
        
        if (this.validF(fNumber))
        {
            // Force last known direction if this is the first command to move
            if (this.lastStartTime == 0)
            {
                this.setDirection(this.getDirection());
            }
            
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
        
        return this;        
    }
    
    @Override
    synchronized public Locomotive setAccessoryState(int id, boolean state)
    {
        this.network.setAccessoryState(id, state);
        
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
     * Prints a human-readable address (greater or equal to 1) based on a raw UID
     * @param UID
     * @return 
     */
    public static String addressFromUID(int UID)
    {
        if (UID > MFX_BASE)
        {
            return Conversion.intToHex(UID - MFX_BASE);
        }
        else if (UID > MULTI_UNIT_BASE)
        {
            return Conversion.intToHex(UID - MULTI_UNIT_BASE);
        }
        else if (UID > DCC_BASE)
        {
            return Conversion.intToHex(UID - DCC_BASE);
        }
        else
        {
            return Integer.toString(UID);
        }
    }
    
    /**
     * User-friendly string representation of the decoder type
     * Should only be used in the UI/log messages, not as authoritative data
     * @return 
     */
    public String getDecoderTypeLabel()
    {
        switch (this.type)
        {
            case MULTI_UNIT:
                return "Multi Unit";
            default:
                
                if (this.hasLinkedLocomotives())
                {
                    return "MU " + type.name();
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
    
    @Override
    public boolean equals(Object other)
    {
        if (!(other instanceof MarklinLocomotive))
        {
            return false;
        }

        return this.getName().equals(((MarklinLocomotive) other).getName()) &&
            this.address == ((MarklinLocomotive) other).address &&
            this.type == ((MarklinLocomotive) other).type;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;

        hash = 73 * hash + this.address;
        hash = 73 * hash + Objects.hashCode(this.type);
        hash = 73 * hash + Objects.hashCode(this.getName());

        return hash;
    }
    
    /**
     * Returns a reference to the model.  Useful for callbacks
     * @return 
     */
    public MarklinControlStation getModel()
    {
        return network;
    }
    
    /**
     * Check if this locomotive is linked to another
     * @param l
     * @return 
     */
    public boolean hasLinkedLocomotive(MarklinLocomotive l)
    {
        return this.linkedLocomotives.containsKey(l);
    }
    
    /**
     * Sets a list of locomotives to link to this one. Must call setLinkedLocomotives after this, i.e. once the model has loaded all locs
     * @param locList
     */
    public void preSetLinkedLocomotives(Map<String, Double> locList)
    {
        this.preLinkedLocomotives = locList;
    }
    
    /**
     * Processes the preset list and maps locomotives to be linked to this one
     * @return 
     */
    public int setLinkedLocomotives()
    {       
        linkedLocomotives.clear();
     
        // Multi-units defined in the Central Station cannot be linked to other locomotives
        if (preLinkedLocomotives == null || !(preLinkedLocomotives instanceof Map) 
                || this.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT) return -1;
        
        for (Map.Entry<String, Double> entry : preLinkedLocomotives.entrySet())
        {
            String locoName = entry.getKey();
            Double value = entry.getValue();
            
            MarklinLocomotive loco = network.getLocByName(locoName);
            
            // Validate
            if (loco == null)
            {
                network.log("Error setting linked locomotive: " + locoName + " does not exist");
            }
            else if (!loco.getLinkedLocomotives().isEmpty())
            {
                network.log("Error setting linked locomotive: " + locoName + " is already linked to other locomotives");
            }
            
            else if (value < -2 || value > 2 || value == 0)
            {
                network.log("Error setting linked locomotive: speed adjustment must be nonzero between -2 and 2");
            }
            else if (this.equals(loco))
            {
                network.log("Error setting linked locomotive: cannot assign " + locoName + " to itself");
            }
            else if (loco.getDecoderType() == MarklinLocomotive.decoderType.MULTI_UNIT)
            {
                network.log("Error setting linked locomotive: cannot assign " + locoName + " because it is a Central Station multi-unit");
            }
            else if (loco.hasLinkedLocomotives())
            {
                network.log("Error setting linked locomotive: cannot assign " + locoName + " because it is itself a multi-unit");
            }
            // Configure
            else 
            {
                linkedLocomotives.put(loco, value);
                
                // Ensure the correct direction
                if (value < 0)
                {
                    if (this.getDirection() == locDirection.DIR_BACKWARD)
                    {
                        loco.setDirection(locDirection.DIR_FORWARD);
                    }
                    else
                    {
                        loco.setDirection(locDirection.DIR_BACKWARD);
                    }
                }
                else
                {
                    loco.setDirection(this.getDirection());
                }
            }
        }
        
        return linkedLocomotives.size();
    }
    
    /**
     * Gets the list of linked locomotives (names only - suitable for export)
     * @return 
     */
    public Map<String, Double> getLinkedLocomotiveNames()
    {
        HashMap<String, Double> locomotiveNames = new HashMap<>();
        
        for (Map.Entry<MarklinLocomotive, Double> entry : this.linkedLocomotives.entrySet())
        {
            String locoName = entry.getKey().getName();
            Double value = entry.getValue();
            locomotiveNames.put(locoName, value);
        }
        
        return locomotiveNames;
    }
    
    /**
     * Gets the list of linked locomotives
     * @return 
     */
    public Map<MarklinLocomotive, Double> getLinkedLocomotives()
    {
        return this.linkedLocomotives;
    }
    
    /**
     * Returns true if this locomotive is linked to others as part of a multi-unit
     * @return 
     */
    public boolean hasLinkedLocomotives()
    {
        return !this.linkedLocomotives.isEmpty();
    }
}
