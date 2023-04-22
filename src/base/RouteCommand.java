package base;

import static base.RouteCommand.commandType.TYPE_ACCESSORY;
import static base.RouteCommand.commandType.TYPE_FUNCTION;
import static base.RouteCommand.commandType.TYPE_LOCOMOTIVE;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Individual route command
 * 
 * @author Adam
 */
public class RouteCommand
{    
    public static enum commandType {TYPE_ACCESSORY, TYPE_LOCOMOTIVE, TYPE_FUNCTION};

    public static String KEY_NAME = "NAME";
    public static String KEY_ADDRESS = "ADDRESS";
    public static String KEY_FUNCTION = "FUNCTION";
    public static String KEY_SETTING = "SETTING";
    public static String KEY_SPEED = "SPEED";
    public static String KEY_DELAY = "DELAY";

    // Settings
    
    // The type of command
    protected commandType type;
    
    // Arbitrary command configuration
    protected LinkedHashMap<String, String> commandConfig;
        
    /**
     * Base constructor
     * @param type
     */
    private RouteCommand(commandType type)
    {
        this.commandConfig = new LinkedHashMap<>();
        this.type = type;
    }
    
    /**
     * Returns a full accessory command
     * @param address
     * @param setting
     * @return 
     */
    public static RouteCommand RouteCommandAccessory(int address, boolean setting)
    {
        RouteCommand r = new RouteCommand(TYPE_ACCESSORY);
        
        r.commandConfig.put(KEY_ADDRESS, Integer.toString(address));
        r.commandConfig.put(KEY_SETTING, Boolean.toString(setting));
        
        return r;
    }
    
    /**
     * Returns a full function command
     * @param name
     * @param function
     * @param setting
     * @return 
     */
    public static RouteCommand RouteCommandFunction(String name, int function, boolean setting)
    {
        RouteCommand r = new RouteCommand(TYPE_FUNCTION);
        
        r.commandConfig.put(KEY_NAME, name);
        r.commandConfig.put(KEY_FUNCTION, Integer.toString(function));
        r.commandConfig.put(KEY_SETTING, Boolean.toString(setting));
        
        return r;
    }
    
    /**
     * Returns a full locomotive speed command
     * @param name
     * @param speed
     * @return 
     */
    public static RouteCommand RouteCommandLocomotive(String name, int speed)
    {
        RouteCommand r = new RouteCommand(TYPE_LOCOMOTIVE);
        
        r.commandConfig.put(KEY_NAME, name);
        r.commandConfig.put(KEY_SPEED, Integer.toString(speed));
        
        return r;
    }
                
    protected boolean isLocomotive()
    {
        return this.type == TYPE_LOCOMOTIVE;
    }
    
    public boolean isAccessory()
    {
        return this.type == TYPE_ACCESSORY;
    }
    
    public boolean isFunction()
    {
        return this.type == TYPE_FUNCTION;
    }
    
    public commandType getType()
    {
        return type;
    }
    
    public LinkedHashMap<String, String> getCommandConfig()
    {
        return commandConfig;
    }
    
    public boolean getSetting()
    {
        return Boolean.parseBoolean(this.commandConfig.get(KEY_SETTING));
    }
    
    public int getAddress()
    {
        return Integer.parseInt(this.commandConfig.get(KEY_ADDRESS));
    }
    
    public int getSpeed()
    {
        return Integer.parseInt(this.commandConfig.get(KEY_SPEED));
    }
    
    public int getDelay()
    {
        if (this.commandConfig.containsKey(KEY_DELAY))
        {
            return Integer.parseInt(this.commandConfig.get(KEY_DELAY));
        }
        
        return 0;
    }
    
    public int getFunction()
    {
        return Integer.parseInt(this.commandConfig.get(KEY_FUNCTION));
    }
    
    public boolean equals(RouteCommand r)
    {
        return this.type == r.getType() && this.commandConfig.equals(r.getCommandConfig());
    }
    
    public void setDelay(int delay)
    {
        this.commandConfig.put(KEY_DELAY, Integer.toString(delay));
    }
    
    @Override
    public String toString()
    {
        String typeString = "undefined";
        
        if (this.isAccessory())
        {
            typeString = "Accessory";
        }
        else if (this.isFunction())
        {
            typeString = "Function";
        }
        else if (this.isLocomotive())
        {
            typeString = "Locomotive";
        }
        
        return "Route Command " + typeString + " -> " + this.commandConfig.toString();
    }
}
