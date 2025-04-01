package org.traincontrol.base;

/**
 * Abstract switch/signal class
 * 
 * @author Adam
 */
abstract public class Accessory
{
    // List of available decoders
    public static enum accessoryDecoderType {MM2, DCC};
    
    public static final accessoryDecoderType DEFAULT_IMPLICIT_PROTOCOL = accessoryDecoderType.MM2;
    
    public static enum accessoryType {SWITCH, SIGNAL};
    public static enum accessorySetting {GREEN, RED, STRAIGHT, TURN};

    // State of the switch/signal
    protected boolean switched;
    
    // The type of this accessory
    private final accessoryType type;
    
    // Name of this accessory
    private final String name;
    
    // Number of times this accessory has been actuated
    protected int numActuations;
    protected boolean stateAtLastActuation;
    
    /**
     * Simple constructors
     * @param name
     * @param type
     * @param state 
     */
    public Accessory(String name, accessoryType type, boolean state)
    {
       this.name = name;
       this.switched = state;
       this.type = type;
    }
    
    /**
     * Sets the state; _setSwitched should be called from this method
     * @param state 
     * @return  
     */
    abstract public Accessory setSwitched(boolean state);
    
    /**
     * Sets internal state
     * @param state 
     */
    protected final void _setSwitched(boolean state)
    {
        this.switched = state;
    }
    
    /**
     * Sets state based on the provided accessory setting
     * @param state
     * @return was the state value valid?
     */
    public boolean setState(accessorySetting state)
    {
        if (state == accessorySetting.TURN || state == accessorySetting.RED)
        {   
            this.turn();
            return true;
        }
        else if (state == accessorySetting.STRAIGHT || state == accessorySetting.GREEN)
        {
            this.straight();
            return true;
        }
        
        return false;
    }
    
    /**
     * Sets to green
     * @return 
     */
    public Accessory green()
    {
        return this.setSwitched(false);
    }
    
    /**
     * Sets to red
     * @return 
     */
    public Accessory red()
    {
        return this.setSwitched(true);
    }
    
    /**
     * Sets to turned
     * @return 
     */
    public Accessory turn()
    {
        return this.setSwitched(true);
    }
    
    /**
     * Swaps the state
     * @return 
     */
    public Accessory doSwitch()
    {
        if (this.isStraight())
        {
            return this.turn();
        }
        else
        {
            return this.straight();
        }
    }
     
    /**
     * Delays execution for the specified number of milliseconds
     * @param ms
     * @return 
     */
    public Accessory delay(long ms)
    {
        try
        {
            Thread.sleep(ms);
        } catch (InterruptedException ex)
        {
        }
        
        return this;
    }
    
    /**
     * Switches to straight
     * @return 
     */
    public Accessory straight()
    {
        return this.setSwitched(false);        
    }
    
    /**
     * returns switch state
     * @return 
     */
    public boolean isSwitched()
    {
        return this.switched;
    }
    
    /**
     * Returns switch state
     * @return 
     */
    public boolean isStraight()
    {
        return !this.switched;
    }
    
    /**
     * Returns switch state
     * @return 
     */
    public boolean isTurned()
    {
        return this.switched;
    }
    
    /**
     * Returns switch state
     * @return 
     */
    public boolean isGreen()
    {
        return !this.switched;
    }
    
    /**
     * Returns switch state
     * @return 
     */
    public boolean isRed()
    {
        return this.switched;
    }
    
    /**
     * The name of this switch
     * @return 
     */
    public String getName()
    {
        return this.name;
    }
    
    /**
     * Is this a switch?
     * @return 
     */
    public boolean isSwitch()
    {
        return this.type == accessoryType.SWITCH;
    }
    
    /**
     * Is this a signal?
     * @return 
     */
    public boolean isSignal()
    {
        return this.type == accessoryType.SIGNAL;
    }
    
    /**
     * Returns the accessory type
     * @return 
     */
    public accessoryType getType()
    {
        return this.type;
    }
    
    /**
     * Gets the number of times this accessory has been switched
     * @return 
     */
    public int getNumActuations()
    {
        return numActuations;
    }
    
    // Helper methods to convert accessory state to human-readable strings
    
    /**
     * Converts a string (if valid) to an accessorySetting
     * @param setting
     * @return 
     */
    public static Accessory.accessorySetting stringToAccessorySetting(String setting)
    {
        if (setting != null)
        {
            for (Accessory.accessorySetting a : Accessory.accessorySetting.values())
            {
                if (setting.trim().toLowerCase().equals(a.toString().toLowerCase()))
                {
                    return a;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Converts turn/red to true, straight/green to false
     * @param setting
     * @return 
     * @throws java.lang.Exception 
     */
    public static boolean stringAccessorySettingToSetting(String setting) throws Exception
    {
        if (setting != null)
        {
            setting = setting.trim().toLowerCase();
            
            if (accessorySetting.RED.toString().toLowerCase().equals(setting) || accessorySetting.TURN.toString().toLowerCase().equals(setting))
            {
                return true;
            }
            else if (accessorySetting.GREEN.toString().toLowerCase().equals(setting) || accessorySetting.STRAIGHT.toString().toLowerCase().equals(setting))

            {
                return false;
            }  
        }
        
        throw new Exception("Invalid accessory setting " + setting);  
    }
    
    /**
     * Converts a string (such as "switch") to the corresponding accessoryType
     * @param type
     * @return 
     * @throws java.lang.Exception 
     */
    public static accessoryType stringToAccessoryType(String type) throws Exception
    {   
        if (type != null)
        {
            for (Accessory.accessoryType a : Accessory.accessoryType.values())
            {
                if (type.trim().toLowerCase().equals(a.toString().toLowerCase()))
                {
                    return a;
                }
            }
        }
        
        throw new Exception("Invalid accessory type " + type);  
    }
    
    /**
     * Converts a setting (true or false) plus an accessory type to the corresponding accessory setting (red, turn, etc.)
     * @param setting
     * @param type
     * @return 
     */
    public static accessorySetting switchedToAccessorySetting(boolean setting, accessoryType type)
    {
        if (type == accessoryType.SIGNAL)
        {
            return setting ? accessorySetting.RED : accessorySetting.GREEN;
        }
        else if (type == accessoryType.SWITCH)
        {
            return setting ? accessorySetting.TURN : accessorySetting.STRAIGHT;
        }
        
        return null;   
    }
    
    /**
     * Pretty printing for the accessory type (Signal, Switch)
     * @param type
     * @return 
     */
    public static String accessoryTypeToPrettyString(accessoryType type)
    {
        String str = type.toString();
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    } 
        
    /**
     * Prints the accessory name and state in a standardized string, for this specific Accessory
     * @return 
     */
    public abstract String toAccessorySettingString();
    
    /**
     * Prints the accessory name and state in a standardized string, for this specific Accessory but with a hypothetical setting
     * @param state
     * @param protocol
     * @return 
     */
    public abstract String toAccessorySettingString(boolean state, String protocol);
    
    @Override
    public String toString()
    {
        return 
            "Accessory \"" + this.name + "\" (" + accessoryTypeToPrettyString(this.getType()) + ")\n" + 
            "State: " + switchedToAccessorySetting(this.isSwitched(), this.getType()).toString().toLowerCase();
    }
}