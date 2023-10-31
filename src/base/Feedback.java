package base;

/**
 * Abstract feedback/sensor class
 * @author Adam
 */
public abstract class Feedback
{
    // Minimum delay between feedback updates, in ms
    public static final int IGNORE_SUB_INTERVAL = 0;
    
    // The feedback state
    private boolean set;
        
    // Timestamp of the last event
    private long lastEvent;
    
    // Name
    protected String name;
    
    /**
     * Initialized the object with empty state
     * @param name 
     */
    public Feedback(String name)
    {
        this.lastEvent = 0;
        this.set = false;
        this.name = name;
    }
    
    /**
     * Returns whether or not enough time has elapsed to consider an update
     * @param time
     * @return 
     */
    protected boolean readyForUpdate(long time)
    {
        if (time - this.lastEvent < IGNORE_SUB_INTERVAL)
        {
            return false;
        }
        
        return true;
    }
    
    /**
     * Returns the state of the feedback
     * @return 
     */
    public boolean isSet()
    {
        return this.set;
    }
    
    /**
     * Sets the state of the feedback
     * @param set
     */
    synchronized protected void _setState(boolean set)
    {
        if (set != this.set)
        {
            this.set = set;
            this.lastEvent = System.currentTimeMillis();
        }
    }
    
    /**
     * Returns the name of the feedback
     * @return 
     */
    public String getName()
    {
        return this.name;
    }
    
    @Override
    public String toString()
    {
        return "Feedback " + this.name + "\n" 
            + "State: " + (this.set ? "On" : "Off");
    }
}
