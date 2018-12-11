package base;

import java.util.Arrays;

/**
 * Abstract locomotive class
 * Defines all key user-accessible functionality
 * @author Adam
 */
public abstract class Locomotive
{
    public static enum locDirection {DIR_FORWARD, DIR_BACKWARD};
    
    // The number of ms to wait while polling
    public static final long WAIT_INTERVAL = 20;
    
    // Speed from 0 to 100 (percent)
    private int speed;
    
    // Direction
    private locDirection direction;
    
    // Number of functions
    private int numF;
       
    // State of functions
    private boolean[] functionState;
    
    // Name of this locomotive
    private String name;
    
    // Picture of this locomotive
    private String imageURL;
    
    /**
     * Constructor with name and all functions off
     * @param name
     * @param numFunctions 
     */
    public Locomotive(String name, int numFunctions)
    {
        this.name = name;
        this.direction = locDirection.DIR_FORWARD;
        this.functionState = new boolean[numFunctions];
        this.numF = numFunctions;
        this._setSpeed(0);
    }
    
    /**
     * Constructor with full state
     * @param name
     * @param functionState 
     */
    public Locomotive(String name, int speed, locDirection direction,
        boolean[] functionState)
    {
        this.name = name;
        this.direction = direction;
        this.functionState = functionState;
        this.numF = functionState.length;
        this._setSpeed(speed);
    }

    /* Internal functionality */
    
    /**
     * Sets the speed internally
     * @param speed 
     */
    protected final void _setSpeed(int speed)
    {
        if (speed >= 0 && speed <= 100)
        {
            this.speed = speed;
        }        
    }
    
    /**
     * Sets a function state internally
     * @param fNumber
     * @param state 
     */
    protected final void _setF(int fNumber, boolean state)
    {
        if (fNumber < numF)
        {
            functionState[fNumber] = state;
        }
    }
    
    /**
     * Sets the locomotive's direction internally
     * @param direction 
     */
    protected final void _setDirection(locDirection direction)
    {
        this.direction = direction;
    }
            
    /* Abstract methods */
    
    /**
     * Sets a locomotive's speed (0-100)
     * Dev note: expects a call to _setSpeed
     * @param speed
     * @return 
     */
    public abstract Locomotive setSpeed(int speed);
    
    /**
     * Sets a functions state
     * Expects a call to _setF
     * @param fNumber
     * @param state
     * @return 
     */
    public abstract Locomotive setF(int fNumber, boolean state);
    
    /**
     * Sets a locomotive's direction
     * Expects a call to _setDirection
     * @param direction
     * @return 
     */
    public abstract Locomotive setDirection(locDirection direction);
    
    /**
     * Returns whether or not a given feedback name is set (string but most likely a number)
     * @return 
     */
    public abstract boolean isFeedbackSet(String name);
        
    /**
     * Returns the state of a given feedback name (string but most likely a number)
     * @return 
     */
    public abstract boolean getFeedbackState(String name);
    
    /**
     * Returns the state of a given accessory id (int)
     * This can be a switch or a signal
     * @return 
     */
    public abstract boolean getAccessoryState(int id);
    
    
    /* Public functionality */
    
    @Override
    public String toString()
    {
        return "Loc " + this.name + "\n" +
            "Fn: " + Arrays.toString(this.functionState) + "\n" +
            "Speed: " + this.speed + "\n" +
            "Direction: " + (this.goingForward() ? "Forward" : "Backward");
    }
    
    /**
     * Chains two feedback commands together
     * waitForClearFeedback then waitForOccupiedFeedback
     * @param name
     * @return 
     */
    public Locomotive waitForClearThenOccuplied(String name)
    {
        return this.waitForClearFeedback(name).waitForOccupiedFeedback(name);
    }
    
    /**
     * Chains two feedback commands together:
     * waitForOccupiedFeedback then waitForClearFeedback
     * @param name
     * @return 
     */
    public Locomotive waitForOccupiedThenClear(String name)
    {
        return this.waitForOccupiedFeedback(name).waitForClearFeedback(name);
    }
    
    /**
     * Sets an accessory state
     * @param id
     * @param state
     * @return 
     */
    public abstract Locomotive setAccessoryState(int id, boolean state);
    
    /**
     * Blocks until the specified accessory value is set
     * @param id accessory id
     * @param state accessory state
     * @return 
     */
    public Locomotive waitForAccessoryState(int id, boolean state)
    {        
        while (this.getAccessoryState(id) != state)
        {
            this.delay(Locomotive.WAIT_INTERVAL);
        }    
        
        return this;
    }
    
    /**
     * Blocks until a certain feedback value is set
     * If feedback is undefined, blocks until it is set
     * @param name
     * @return 
     */
    public Locomotive waitForOccupiedFeedback(String name)
    {        
        while (!this.isFeedbackSet(name) || !this.getFeedbackState(name))
        {
            this.delay(Locomotive.WAIT_INTERVAL);
        }    
        
        return this;
    }
    /**
     * Blocks until a certain feedback value is not set
     * If feedback is undefined, blocks until it is set
     * @param name
     * @return 
     */
    public Locomotive waitForClearFeedback(String name)
    {        
        while (!this.isFeedbackSet(name) || this.getFeedbackState(name))
        {
            this.delay(Locomotive.WAIT_INTERVAL);
        }    
        
        return this;
    }
    
    /**
     * Turns a function on for the specified number of milliseconds, then off
     * @param f
     * @param duration
     * @return 
     */
    public Locomotive toggleF(int f, int duration)
    {
        return this.setF(f, true).delay(duration).setF(f, false);
    }
    
    /**
     * Turns a function on for one second, then off
     * @param f
     * @return 
     */
    public Locomotive toggleF(int f)
    {
        return this.toggleF(f, 1000);
    }
    
    /**
     * Enables the lights (f0 = true)
     * @return 
     */
    public Locomotive lightsOn()
    {
        return this.setF(0, true);
    }
    
    /**
     * Disables the lights (f0 = false)
     * @return 
     */
    public Locomotive lightsOff()
    {
        return this.setF(0, false);
    }
    
    /**
     * Is the locomotive going forward?
     * @return 
     */
    public boolean goingForward()
    {
        return direction == locDirection.DIR_FORWARD;
    }
    
    /**
     * Is the locomotive going backward?
     * @return 
     */
    public boolean goingBackward()
    {
        return direction == locDirection.DIR_BACKWARD;
    }
    
    /**
     * Switches the locomotive's direction
     * @return 
     */
    public Locomotive switchDirection()
    {
        if (this.goingBackward())
        {
            return this.setDirection(locDirection.DIR_FORWARD);
        }
        else
        {
            return this.setDirection(locDirection.DIR_BACKWARD);
        }
    }
    
    /**
     * Sets the locomotive to go forward
     * @return 
     */
    public Locomotive goForward()
    {
        this.setDirection(locDirection.DIR_FORWARD);
        
        return this;
    }
    
    /**
     * Sets the locomotive to go backward
     * @return 
     */
    public Locomotive goBackward()
    {
        this.setDirection(locDirection.DIR_BACKWARD);
        
        return this;
    }
    
    /**
     * Stops the locomotive (set speed = 0)
     * @return 
     */
    public Locomotive stop()
    {
        this.setSpeed(0);
        
        return this;
    }
    
    /**
     * Delays for a random number of seconds between min and max
     * @param min
     * @param max
     * @return 
     */
    public Locomotive delay(int min, int max)
    {
        min = Math.abs(min);
        max = Math.abs(max);
        
        return this.delay((min + Math.round(Math.random() * (max - min))) * 1000);
    }
    
    /**
     * Delays execution for the specified number of milliseconds
     * @param ms
     * @return 
     */
    public Locomotive delay(long ms)
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
     * Gets a function state
     * @param fNumber
     * @return 
     */
    public boolean getF(int fNumber)
    {
        if (fNumber < numF)
        {
            return functionState[fNumber];
        }
        
        return false;
    }
    
    /* Getters and setters */
    
    /**
     * Gets the current speed
     * @return 
     */
    public int getSpeed()
    {
        return speed;
    }
    
    /**
     * Returns if the passed f number is a valid function
     * @param fNumber
     * @return 
     */
    public boolean validF(int fNumber)
    {
        return fNumber < numF;
    }
    
    /**
     * Returns the number of functions
     * @return 
     */
    public int getNumF()
    {
        return numF;
    }
    
    /**
     * Returns the locomotive's name
     * @return 
     */
    public String getName()
    {
        return name;
    }  
    
    /**
     * Renames the locomotive
     * @param newName 
     */
    public void rename(String newName)
    {
        this.name = newName;
    }
    
    /**
     * Returns the current direction
     * @return 
     */
    public locDirection getDirection()
    {
        return this.direction;
    }
    
    /**
     * Gets the raw function vector based 
     * on the locomotive's current function state
     * @return 
     */
    public boolean[] getFunctionState()
    {
        return this.functionState;
    }
    
    /**
     * Returns the image URL, if any
     */
    public String getImageURL()
    {
        return this.imageURL;
    }
    
    /**
     * Sets the image URL
     * @param u 
     */
    public void setImageURL(String u)
    {
        this.imageURL = u;
    }
}
