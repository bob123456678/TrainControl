package base;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Abstract locomotive class
 * Defines all key user-accessible functionality
 * @author Adam
 */
public abstract class Locomotive
{
    public static enum locDirection {DIR_FORWARD, DIR_BACKWARD};
    
    // The number of ms to wait while polling
    public static final long POLL_INTERVAL = 20;

    // Minimum time feedback state must remain unchanged to register a s88 state change
    // Should be > the CS2 polling interval.  Can be overriden when calling waitForOccupiedFeedback / waitForClearFeedback directly
    // TODO - make configurable
    public static final int FEEDBACK_DURATION_THRESHOLD = 201;
    
    // Speed from 0 to 100 (percent)
    private int speed;
    
    // Direction
    private locDirection direction;
    
    // Number of functions
    private final int numF;
       
    // State of functions
    private final boolean[] functionState;
    
    // A preset of preferred function state
    private boolean[] preferredFunctions;
    private int preferredSpeed;

    // Name of this locomotive
    private String name;
    
    // Picture of this locomotive
    private String imageURL;
    
    // Types of functions
    protected final int[] functionTypes;
    
    // Custom event callbacks
    protected Map<String, Consumer<Locomotive>> callbacks;
    
    // Functions that fire on/prior to arrival
    protected Integer arrivalFunc;
    protected Integer departureFunc;
    protected boolean reversible;
    
    // Length of the train corresponding to ths locomotive
    protected Integer trainLength;
    
    // Number of completed paths, and last time
    protected Integer numPaths = 0;
    protected long lastPathTime = System.currentTimeMillis();
 
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
        this.functionTypes = new int[numFunctions];
        
        this.callbacks = new HashMap<>();
        
        this.preferredFunctions = Arrays.copyOf(functionState, functionState.length);
        this.preferredSpeed = 0;
        this.trainLength = 0;
    }
    
    /**
     * Saves current function state as the new preferred function preset
     */
    public void savePreferredFunctions()
    {
        this.preferredFunctions = Arrays.copyOf(functionState, functionState.length);
    }
    
    /**
     * Retrieves the preferred function preset
     * @return 
     */
    public boolean[] getPreferredFunctions()
    {
        return this.preferredFunctions;
    }
    
    /**
     * Saves current speed as the new preferred speed preset
     */
    public void savePreferredSpeed()
    {
        this.preferredSpeed = this.speed;
    }
    
    /**
     * Sets a new preferred speed preset
     * @param speed
     */
    public void setPreferredSpeed(int speed)
    {
        if (speed >= 0 && speed <= 100)
        {
            this.preferredSpeed = speed;
        }
    }
    
    /**
     * Retrieves the preferred speed preset
     * @return 
     */
    public int getPreferredSpeed()
    {
        return this.preferredSpeed;
    }
    
    /**
     * Applies the saved function preset
     * @return 
     */
    public Locomotive applyPreferredSpeed()
    {
        this.setSpeed(this.preferredSpeed);
        
        return this;
    }
    
    /**
     * Applies the saved function preset
     * @return 
     */
    public Locomotive applyPreferredFunctions()
    {
        if (this.preferredFunctions != null)
        {
            for (int i = 0; i < preferredFunctions.length && i < this.functionTypes.length; i++)
            { 
                this.setF(i, this.preferredFunctions[i]);
            }
        }
        
        return this;
    }
    
    /**
     * Turns off all functions known to be on
     * @return 
     */
    public Locomotive functionsOff()
    {
        for (int i = 0; i < this.getNumF(); i++)
        {
            if (this.getF(i))
            {
                this.setF(i, false);
            }
        }
        
        return this;
    }
    
    /**
     * Constructor with name and all functions off
     * @param name
     * @param numFunctions 
     * @param functionTypes 
     */
    public Locomotive(String name, int numFunctions, int[] functionTypes)
    {
        this.name = name;
        this.direction = locDirection.DIR_FORWARD;
        this.functionState = new boolean[numFunctions];
        this.numF = numFunctions;
        this._setSpeed(0);
        this.functionTypes = new int[numFunctions];
        
        // Safely copy function types - raw data is often shorter or longer than needed
        for (int i = 0; i < functionTypes.length && i < this.functionTypes.length; i++)
        { 
            this.functionTypes[i] = functionTypes[i];
        }
  
        this.callbacks = new HashMap<>();
        
        this.preferredFunctions = Arrays.copyOf(functionState, functionState.length);
        this.preferredSpeed = 0;
        this.trainLength = 0;
    }
    
    /**
     * Gets the function type list
     * @return 
     */    
    public int[] getFunctionTypes()
    {
        return this.functionTypes;        
    }
    
    /**
     * Gets the function type for a given slot
     * @param fno
     * @return 
     */    
    public int getFunctionType(int fno)
    {
        if (fno < this.functionTypes.length)
        {
            return this.functionTypes[fno];
        }
        
        return -1;        
    }
    
    /**
     * Constructor with full state
     * @param name
     * @param speed
     * @param direction
     * @param functionState 
     * @param functionTypes 
     * @param preferredFunctions 
     * @param preferredSpeed 
     * @param departureFunc 
     * @param arrivalFunc 
     * @param trainLength
     * @param reversible 
     */
    public Locomotive(String name, int speed, locDirection direction,
        boolean[] functionState, int[] functionTypes, boolean[] preferredFunctions, 
        int preferredSpeed, Integer departureFunc, Integer arrivalFunc, boolean reversible,
        int trainLength
    )
    {
        this.name = name;
        this.direction = direction;
        this.functionState = functionState;
        this.numF = functionState.length;
        this._setSpeed(speed);
        this.functionTypes = new int[numF];
        
        // Safely copy function types - raw data is often shorter or longer than needed
        for (int i = 0; i < functionTypes.length && i < this.functionTypes.length; i++)
        { 
            this.functionTypes[i] = functionTypes[i];
        }
        
        this.callbacks = new HashMap<>();
        
        this.preferredFunctions = preferredFunctions;
        this.preferredSpeed = preferredSpeed;
        
        this.departureFunc = departureFunc;
        this.arrivalFunc = arrivalFunc;
        this.reversible = reversible;
        this.trainLength = trainLength;
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
        if (fNumber < numF && fNumber >= 0)
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
     * @param name
     * @return 
     */
    public abstract boolean isFeedbackSet(String name);
        
    /**
     * Returns the state of a given feedback name (string but most likely a number)
     * @param name
     * @return 
     */
    public abstract boolean getFeedbackState(String name);
    
    /**
     * Returns the state of a given accessory id (int)
     * This can be a switch or a signal
     * @param id
     * @return 
     */
    public abstract boolean getAccessoryState(int id);
    
    /**
     * Executes a route by the given name
     * @param name
     * @return 
     */
    public abstract Locomotive execRoute(String name);
    
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
            this.delay(Locomotive.POLL_INTERVAL);
        }    
        
        return this;
    }
    
    /**
     * Blocks until this locomotive meets or exceeds the threshold speed
     * @param threshold
     * @param maxWait maximum number of seconds to wait (0 to disable)
     * @return 
     */
    public Locomotive waitForSpeedAtOrAbove(int threshold, int maxWait)
    {
        long start = System.currentTimeMillis();
        
        while (this.getSpeed() < threshold)
        {
            this.delay(Locomotive.POLL_INTERVAL);   
            
            if (maxWait > 0 && System.currentTimeMillis() - start > maxWait * 1000) break;
        }
        
        return this;
    }
    
    /**
     * Blocks until this locomotive is below the threshold speed
     * @param threshold
     * @param maxWait maximum number of seconds to wait (0 to disable)
     * @return 
     */
    public Locomotive waitForSpeedBelow(int threshold, int maxWait)
    {
        long start = System.currentTimeMillis();
        
        while (this.getSpeed() >= threshold)
        {
            this.delay(Locomotive.POLL_INTERVAL);   
            
            if (maxWait > 0 && System.currentTimeMillis() - start > maxWait * 1000) break;
        }
        
        return this;
    }
    
    /**
     * Blocks until a certain feedback value is set
     * If feedback is undefined, blocks until it is set
     * @param name
     * @param minDuration for now many ms must the feedback indicate occupied?
     * @return 
     */
    public Locomotive waitForOccupiedFeedback(String name, int minDuration)
    {        
        while (!this.isFeedbackSet(name) || !this.getFeedbackState(name))
        {
            this.delay(Locomotive.POLL_INTERVAL);
        }    
        
        if (minDuration > 0)
        {
            this.delay(minDuration);

            // Feedback should still be occupied.  Otherwise, start over
            if (!this.isFeedbackSet(name) || !this.getFeedbackState(name))
            {
                return this.waitForOccupiedFeedback(name, minDuration);
            }
        }
        
        return this;
    }
    /**
     * Blocks until a certain feedback value is not set
     * If feedback is undefined, blocks until it is set
     * @param name
     * @param minDuration for now many ms must the feedback indicate clear?
     * @return 
     */
    public Locomotive waitForClearFeedback(String name, int minDuration)
    {        
        while (!this.isFeedbackSet(name) || this.getFeedbackState(name))
        {
            this.delay(Locomotive.POLL_INTERVAL);
        }    
        
        if (minDuration > 0)
        {
            this.delay(minDuration);

            // Feedback should still be clear.  Otherwise, start over
            if (!this.isFeedbackSet(name) || this.getFeedbackState(name))
            {
                return this.waitForClearFeedback(name, minDuration);
            }
        }
                
        return this;
    }
    
    /**
     * Waits for clear feedback lasting the default feedback duration threshold
     * @param name
     * @return 
     */
    public Locomotive waitForClearFeedback(String name)
    {
        return waitForClearFeedback(name, FEEDBACK_DURATION_THRESHOLD);
    }
    
    /**
     * Waits for occupied feedback lasting the default feedback duration threshold
     * @param name
     * @return
     */
    public Locomotive waitForOccupiedFeedback(String name)
    {
        return waitForOccupiedFeedback(name, FEEDBACK_DURATION_THRESHOLD);
    }
    
    /**
     * Chains two feedback commands together
     * waitForClearFeedback then waitForOccupiedFeedback
     * @param name
     * @return 
     */
    public Locomotive waitForClearThenOccupied(String name)
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
        return fNumber < numF && fNumber >= 0;
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
     * @return 
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
         
    /**
     * Checks if the specified callback has been defined
     * @param callbackName 
     * @return  
     */
    public boolean hasCallback(String callbackName)
    {
        return this.callbacks.containsKey(callbackName);
    }
    
    /**
     * Returns the requested callback function
     * @param callbackName
     * @return 
     */
    public Consumer<Locomotive> getCallback(String callbackName)
    {
        return this.callbacks.get(callbackName);
    }
    
    /**
     * Sets a new callback function for a given name
     * @param callbackName
     * @param callback 
     */
    public void setCallback(String callbackName, Consumer<Locomotive> callback)
    {
        this.callbacks.put(callbackName, callback);
    }
    
    /**
     * Getters and setters for functions
     * @return 
     */
    public Integer getArrivalFunc()
    {
        return arrivalFunc;
    }

    public void setArrivalFunc(Integer arrivalFunc)
    {
        if (arrivalFunc == null)
        {
            this.arrivalFunc = null;
        }
        else if (arrivalFunc <= numF && arrivalFunc >= 0)
        {
            this.arrivalFunc = arrivalFunc;
        }
    }

    public Integer getDepartureFunc()
    {
        return departureFunc;
    }
    
    public void setDepartureFunc(Integer departureFunc)
    {
        if (departureFunc == null)
        {
            this.departureFunc = null;
        }
        else if (departureFunc <= numF && departureFunc >= 0)
        {
            this.departureFunc = departureFunc;
        }
    }
    
    public boolean hasArrivalFunc()
    {
        return this.arrivalFunc != null;
    }
    
    public boolean hasDepartureFunc()
    {
        return this.departureFunc != null;
    }
    
    public boolean isReversible()
    {
        return reversible;
    }

    public void setReversible(boolean reversible)
    {
        this.reversible = reversible;
    }
    
    public Integer getNumPaths()
    {
        return this.numPaths;
    }
    
    public void incrementNumPaths()
    {
        this.numPaths += 1;
        this.lastPathTime = System.currentTimeMillis();
    }
    
    public long getLastPathTime()
    {
        return this.lastPathTime;
    }
    
    public Integer getTrainLength()
    {
        return trainLength;
    }

    public void setTrainLength(Integer trainLength)
    {
        this.trainLength = trainLength;
    }
}
