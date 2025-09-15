package org.traincontrol.base;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Abstract locomotive class
 * Defines all key user-accessible functionality
 * @author Adam
 */
public abstract class Locomotive
{
    public static enum locDirection {DIR_FORWARD, DIR_BACKWARD};
    
    public static final Object monitor = new Object();
    public static final Object speedMonitor = new Object();
    public static final Object accessoryMonitor = new Object();

    // The number of ms between successive function commands
    public static final long FUNCTION_DELAY_MS = 20;
    
    // Minimum time feedback state must remain unchanged to register a s88 state change
    // Should be > the CS2 polling interval.  Can be overriden when calling waitForOccupiedFeedback / waitForClearFeedback directly
    // TODO - make configurable
    public static final int FEEDBACK_DURATION_THRESHOLD = 201;
    
    // Speed from 0 to 100 (percent)
    private int speed;
    
    // Direction
    private locDirection direction;
    
    // Number of functions
    protected int numF;
       
    // State of functions
    protected boolean[] functionState;
    
    // A preset of preferred function state
    protected boolean[] preferredFunctions;
    private int preferredSpeed;

    // Name of this locomotive
    private String name;
    
    // Picture of this locomotive
    private String imageURL;
    private String localImageURL;
    
    // Mapping of custom funciton icons
    private Map<Integer, String> localFunctionImageURLs;
    
    // Types of functions
    protected int[] functionTypes;
    
    // Directly store how the function is to be triggered
    protected int[] functionTriggerTypes;
    public static final int FUNCTION_TOGGLE = -1;
    public static final int FUNCTION_PULSE = -2;
    
    // Custom event callbacks
    protected Map<String, Consumer<Locomotive>> callbacks;
    
    // Functions that fire on/prior to arrival
    protected Integer arrivalFunc;
    protected Integer departureFunc;
    protected boolean reversible;
    
    // Length of the train corresponding to this locomotive
    protected Integer trainLength;
    
    // Number of completed paths, and last time run, in autonomous mode
    protected Integer numPaths = 0;
    protected long lastPathTime = System.currentTimeMillis();
    
    // Cumulative time of operation, by date
    protected Map<String, Long> historicalOperatingTime;
    
    // When this locomotive was last run.  Used to ensure stats are tracked correctly when power is turned off.
    protected long lastStartTime = 0;
    
    // Track power state to ensure correct timings
    private boolean powerState = true;
 
    // User-defined notes about this locomotive
    private String notes = "";
    
    // Used to pause autonomous operation - not persisted
    private boolean autonomyPaused = false;

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
        this.functionTriggerTypes = new int[numFunctions];
        
        this.callbacks = new HashMap<>();
        this.historicalOperatingTime = new HashMap<>();
        this.localFunctionImageURLs = new HashMap<>();
        
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
     * Retrieves the function trigger types
     * @return 
     */
    public int[] getFunctionTriggerTypes()
    {
        return this.functionTriggerTypes;
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
                this.setF(i, this.preferredFunctions[i]).delay(FUNCTION_DELAY_MS);
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
                this.setF(i, false).delay(FUNCTION_DELAY_MS);
            }
        }
        
        return this;
    }
    
    /**
     * Constructor with name and all functions off
     * @param name
     * @param numFunctions 
     * @param functionTypes 
     * @param functionTriggerTypes 
     */
    public Locomotive(String name, int numFunctions, int[] functionTypes, int[] functionTriggerTypes)
    {
        this.name = name;
        this.direction = locDirection.DIR_FORWARD;
        this.functionState = new boolean[numFunctions];
        this.numF = numFunctions;
        this._setSpeed(0);
        
        this.setFunctionTypes(functionTypes, functionTriggerTypes);
  
        this.callbacks = new HashMap<>();
        this.localFunctionImageURLs = new HashMap<>();

        this.preferredFunctions = Arrays.copyOf(functionState, functionState.length);
        this.preferredSpeed = 0;
        this.trainLength = 0;
        this.historicalOperatingTime = new HashMap<>();
    }
    
    /**
     * Safely sets the function types
     * @param functionTypes 
     * @param functionTriggerTypes 
     */
    public final void setFunctionTypes(int[] functionTypes, int[] functionTriggerTypes)
    {
        this.functionTypes = Arrays.copyOf(functionTypes, this.numF);
        this.functionTriggerTypes = Arrays.copyOf(functionTriggerTypes, this.numF);
    }
    
    /**
     * Safely sets the function state
     * @param functionState 
     */
    public final void setFunctionState(boolean[] functionState)
    {
        this.functionState = Arrays.copyOf(functionState, this.numF);
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
     * @param numF
     * @param functionState 
     * @param functionTypes 
     * @param functionTriggerTypes 
     * @param preferredFunctions 
     * @param preferredSpeed 
     * @param departureFunc 
     * @param arrivalFunc 
     * @param trainLength
     * @param reversible 
     * @param historicalOperatingTime 
     */
    public Locomotive(String name, int speed, locDirection direction, int numF,
        boolean[] functionState, int[] functionTypes, int[] functionTriggerTypes, boolean[] preferredFunctions, 
        int preferredSpeed, Integer departureFunc, Integer arrivalFunc, boolean reversible,
        int trainLength, Map<String, Long> historicalOperatingTime)
    {
        this.name = name;
        this.direction = direction;
        this.numF = numF;
        this._setSpeed(speed);
        this.setFunctionState(functionState);
        this.setFunctionTypes(functionTypes, functionTriggerTypes);
      
        this.callbacks = new HashMap<>();
        this.localFunctionImageURLs = new HashMap<>();
        
        this.preferredFunctions = preferredFunctions;
        this.preferredSpeed = preferredSpeed;
        
        this.departureFunc = departureFunc;
        this.arrivalFunc = arrivalFunc;
        this.reversible = reversible;
        this.trainLength = trainLength;
        this.historicalOperatingTime = historicalOperatingTime;
    }
    
    /**
     * Callback used to pause tracking running times when the power is turned off/on
     * @param powerOn 
     */
    synchronized public void notifyOfPowerStateChange(boolean powerOn)
    {
        powerState = powerOn;
        
        // Locomotive was runnning - we need to stop the timer
        if (this.speed > 0)
        {
            String key = Locomotive.getDate();
            
            // Power on - reset the timer
            if (powerOn)
            {
                this.lastStartTime = System.currentTimeMillis();
            }
            // Power off - stop the timer and store result
            else
            {
                if (this.lastStartTime > 0)
                {
                    this.historicalOperatingTime.put(key, 
                        this.historicalOperatingTime.getOrDefault(key, 0L) +
                        (System.currentTimeMillis() - this.lastStartTime)
                    );    

                    this.lastStartTime = 0;
                }
            }
        }
    }

    /* Internal functionality */
    
    /**
     * Sets the speed internally
     * @param speed 
     */
    protected final void _setSpeed(int speed)
    {
        synchronized (speedMonitor)
        {
            if (speed >= 0 && speed <= 100)
            {
                // Update total runtime stat
                if (speed > 0 && this.speed == 0)
                {
                    this.lastStartTime = System.currentTimeMillis();

                    // Add a placeholder record to track the date
                    String key = Locomotive.getDate();

                    this.historicalOperatingTime.put(key, 
                        this.historicalOperatingTime.getOrDefault(key, 0L)
                    ); 
                }
                else if (speed == 0 && this.speed > 0 && this.lastStartTime > 0)
                {
                    // Now add the number of seconds to the running total
                    String key = Locomotive.getDate();

                    if (powerState)
                    {
                        this.historicalOperatingTime.put(key, 
                            this.historicalOperatingTime.getOrDefault(key, 0L) +
                            (System.currentTimeMillis() - this.lastStartTime)
                        );
                    }
                }

                this.speed = speed;            
            }  
            
            speedMonitor.notifyAll();
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
     * @param type
     * @return 
     */
    public abstract boolean getAccessoryState(int id, Accessory.accessoryDecoderType type);
    
    /**
     * Executes a route by the given name
     * @param name
     * @return 
     */
    public abstract Locomotive execRoute(String name);
    
    /**
     * Checks if this locomotive can be linked to another as a multi unit and run at the same time without conflicting
     * @param l
     * @return 
     */
    public abstract boolean isSimultaneousMultiUnitCompatible(Locomotive l);
    
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
     * @param type
     * @param state
     * @return 
     */
    public abstract Locomotive setAccessoryState(int id, Accessory.accessoryDecoderType type, boolean state);
    
    /**
     * Blocks until the specified accessory value is set
     * @param id accessory id
     * @param type
     * @param state accessory state
     * @return 
     */
    public Locomotive waitForAccessoryState(int id, Accessory.accessoryDecoderType type, boolean state)
    {        
        synchronized(accessoryMonitor)
        {
            while (this.getAccessoryState(id, type) != state)
            {
                try
                {
                    accessoryMonitor.wait();
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
            }   
        }
        
        return this;
    }
    
    /**
     * Waits up to maxWait seconds, unless this locomotive starts moving
     * @param maxWait
     */
    public void blockUntilMotion(int maxWait)
    {
        long start = System.currentTimeMillis();

        while (this.getSpeed() < 1)
        {
            this.delay(Locomotive.FUNCTION_DELAY_MS);   

            if (maxWait > 0 && System.currentTimeMillis() - start > maxWait * 1000) break;
        }
    }
    
    /**
     * Blocks until this locomotive meets or exceeds the threshold speed
     * @param threshold
     * @return 
     */
    public Locomotive waitForSpeedAtOrAbove(int threshold)
    {
        synchronized(speedMonitor)
        {        
            while (this.getSpeed() < threshold)
            {
                try
                {
                    speedMonitor.wait();
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return this;
    }
    
    /**
     * Blocks until this locomotive is below the threshold speed
     * @param threshold
     * @return 
     */
    public Locomotive waitForSpeedBelow(int threshold)
    {
        synchronized(speedMonitor)
        {        
            while (this.getSpeed() >= threshold)
            {
                try
                {
                    speedMonitor.wait();
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
            }
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
        synchronized(monitor)
        {
            while (!this.isFeedbackSet(name) || !this.getFeedbackState(name))
            {
                try
                {
                    monitor.wait();
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
            }  
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
        synchronized (monitor)
        {
            while (!this.isFeedbackSet(name) || this.getFeedbackState(name))
            {
                try
                {
                    monitor.wait();
                }
                catch (InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
            }  
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
    
    // Int versions of the same methods
    
    public final Locomotive waitForOccupiedFeedback(int feedbackId)
    {
        return waitForOccupiedFeedback(Integer.toString(feedbackId));
    }
    
    public final Locomotive waitForClearFeedback(int feedbackId)
    {
        return waitForClearFeedback(Integer.toString(feedbackId));
    }
    
    public final Locomotive waitForClearThenOccupied(int feedbackId)
    {
        return this.waitForClearFeedback(Integer.toString(feedbackId)).waitForOccupiedFeedback(Integer.toString(feedbackId));
    }
    
    public final Locomotive waitForOccupiedThenClear(int feedbackId)
    {
        return this.waitForOccupiedFeedback(Integer.toString(feedbackId)).waitForClearFeedback(Integer.toString(feedbackId));
    }
    
    public final boolean isFeedbackSet(int feedbackId)
    {
        return isFeedbackSet(Integer.toString(feedbackId));
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
        } 
        catch (InterruptedException ex) { }
        
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
    
    /**
     * Instantly stops the locomotive
     * @return 
     */
    abstract public Locomotive instantStop();
    
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
     * Returns the local image URL, if any
     * @return 
     */
    public String getLocalImageURL()
    {
        return this.localImageURL;
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
     * Sets a local image URL (this is remembered between saves)
     * @param u 
     */
    public void setLocalImageURL(String u)
    {
        this.imageURL = u;
        this.localImageURL = this.imageURL;
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

    /**
     * Sets an arrival function that will be called when this locomotive ends a path in autonomous mode
     * @param arrivalFunc 
     */
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

    /**
     * Returns the function number of the departure function
     * @return 
     */
    public Integer getDepartureFunc()
    {
        return departureFunc;
    }
    
    /**
     * Sets a departure function that will be called when this locomotive starts a path in autonomous mode
     * @param departureFunc 
     */
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
    
    /**
     * Returns if an arrival function is set
     * @return 
     */
    public boolean hasArrivalFunc()
    {
        return this.arrivalFunc != null;
    }
    
    /**
     * Returns if a departure function is set
     * @return 
     */
    public boolean hasDepartureFunc()
    {
        return this.departureFunc != null;
    }
    
    /**
     * Returns if this locomotive is reversible
     * @return 
     */
    public boolean isReversible()
    {
        return reversible;
    }

    /**
     * Sets this locomotive as a reversible train (used in autonomy)
     * @param reversible 
     */
    public void setReversible(boolean reversible)
    {
        this.reversible = reversible;
    }
    
    /**
     * Gets the total number of autonomous paths executed
     * @return 
     */
    public Integer getNumPaths()
    {
        return this.numPaths;
    }
    
    /**
     * Logs that an autonomous path was executed
     */
    public void incrementNumPaths()
    {
        this.numPaths += 1;
        this.lastPathTime = System.currentTimeMillis();
    }
    
    /**
     * Gets the last time that an autonomous path was executed
     * @return 
     */
    public long getLastPathTime()
    {
        return this.lastPathTime;
    }
    
    /**
     * Gets the train length
     * @return 
     */
    public Integer getTrainLength()
    {
        return trainLength;
    }

    /**
     * Set the train length (used for automation)
     * @param trainLength 
     */
    public void setTrainLength(Integer trainLength)
    {
        this.trainLength = trainLength;
    }
    
    /**
     * Amount of time the locomotive was run on a specific day
     * @param date - String formatted as yyyy-MM-dd
     * @return 
     */
    public long getRuntimeOnDay(String date)
    {
        return this.historicalOperatingTime.getOrDefault(date, 0L);
    }
    
    /**
     * Amount of time the locomotive was run today
     * @return 
     */
    public long getRuntimeToday()
    {
        return this.historicalOperatingTime.getOrDefault(Locomotive.getDate(), 0L);
    }
    
    /**
     * Total amount of time the locomotive was run
     * @return 
     */
    public long getTotalRuntime()
    {
        return this.historicalOperatingTime.values().stream().reduce(0L, Long::sum);
    }
    
    /**
     * On how many different days was the locomotive run
     * @return 
     */
    public int getNumDaysRun()
    {
        return this.historicalOperatingTime.keySet().size();
    }
    
    /**
     * The most recent date the locomotive was run
     * @param mostRecent
     * @return 
     */
    public String getOperatingDate(boolean mostRecent)
    {
        if (!this.historicalOperatingTime.isEmpty())
        {
            List<String> sortedList = new ArrayList<>(this.historicalOperatingTime.keySet());
            Collections.sort(sortedList);

            return sortedList.get(mostRecent ? sortedList.size() - 1 : 0);
        }
        else
        {
            return "(Never)";
        }
    }   
    
    /**
     * Gets operating time by day
     * @return 
     */
    public Map<String, Long> getHistoricalOperatingTime()
    {
        return historicalOperatingTime;
    }
       
    public static String getDate()
    {
        return getDate(System.currentTimeMillis());
    }
    
    public static String getDate(long ts)
    {
        return new SimpleDateFormat("yyyy-MM-dd").format(ts);
    }
    
    public void setLocalFunctionImageURLs(Map<Integer, String> urls)
    {
        if (urls != null && urls instanceof Map)
        {
            this.localFunctionImageURLs = urls;
        }
    }

    /**
     * Gets the custom icon URLs for every function
     * @return 
     */
    public Map<Integer, String> getLocalFunctionImageURLs()
    {
        return localFunctionImageURLs;
    }
    
    public String getLocalFunctionImageURL(int fNo)
    {
        if (fNo <= this.numF && fNo >= 0)
        {
            return this.localFunctionImageURLs.get(fNo);
        }
        
        return null;
    }
    
    public boolean setLocalFunctionImageURL(int fNo, String url)
    {
        if (fNo <= this.numF && fNo >= 0)
        {
            this.localFunctionImageURLs.put(fNo, url);
            return true;
        }
        
        return false;
    }
    
    public void unsetLocalFunctionImageURL(int fNo)
    {
        this.localFunctionImageURLs.remove(fNo);
    }
    
    public void unsetLocalFunctionImageURLs()
    {
        this.localFunctionImageURLs.clear();
    }
    
    /**
     * Legacy-compatible setter. Wraps plain text into JSON if needed.
     * @param notes
     */
    public void setNotes(String notes)
    {
        if (notes == null)
        {
            notes = "";
        }

        try
        {
            new JSONObject(notes); // valid JSON
            this.notes = notes;
        }
        catch (JSONException e)
        {
            LocomotiveNotes wrapper = new LocomotiveNotes(0, 0, "", notes);
            this.notes = wrapper.toJson().toString();
        }
    }

    /**
     * Returns raw notes string (JSON or plain text).
     * @return 
     */
    public String getNotes()
    {
        return this.notes;
    }

    /**
     * Sets structured notes all at once.
     * @param startYear
     * @param endYear
     * @param railway
     * @param plainNotes
     */
    public void setStructuredNotes(int startYear, int endYear, String railway, String plainNotes)
    {
        LocomotiveNotes wrapper = new LocomotiveNotes(startYear, endYear, railway, plainNotes);
        this.notes = wrapper.toJson().toString();
    }

    /**
     * Returns structured notes object.
     * @return 
     */
    public LocomotiveNotes getStructuredNotes()
    {
        return LocomotiveNotes.fromJson(this.notes);
    }
    
    /**
     * Flags autonomy as paused so that no further routes are started
     * @param state 
     */
    public void setAutonomyPaused(boolean state)
    {
        synchronized (this)
        {
            this.autonomyPaused = state;
        }
    }
    
    /**
     * Returns if autonomy has been paused
     * @return
     */
    public boolean isAutonomyPaused()
    {
        synchronized (this)
        {
            return this.autonomyPaused;
        }
    }
}
