package base;

/**
 * Abstract switch/signal class
 * 
 * @author Adam
 */
abstract public class Accessory
{
    public static enum accessoryType {SWITCH, SIGNAL};
    public static enum accessorySetting {GREEN, RED, STRAIGHT, TURN};

    // State of the switch/signal
    protected boolean switched;
    
    // The type of this accessory
    private final accessoryType type;
    
    // Name of this accessory
    private final String name;
    
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
    
    @Override
    public String toString()
    {
        if (this.isSignal())
        {
             return "Signal " + this.name + "\n" +
            "State: " + (this.isGreen() ? "Green" : "Red");
        }
        else if (this.isSwitch())
        {
             return "Switch " + this.name + "\n" +
            "State: " + (this.isStraight() ? "Straight" : "Turned");
        }
        else
        {
            return "Accessory " + this.name + "\n" +
            "State: " + (this.isGreen()? "Off" : "On");
        }
    }
}