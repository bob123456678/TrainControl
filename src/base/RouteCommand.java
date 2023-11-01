package base;

import static base.RouteCommand.commandType.TYPE_ACCESSORY;
import static base.RouteCommand.commandType.TYPE_FUNCTION;
import static base.RouteCommand.commandType.TYPE_LOCOMOTIVE;
import static base.RouteCommand.commandType.TYPE_STOP;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Objects;
import org.json.JSONObject;

/**
 * Individual route command
 * 
 * @author Adam
 */
public class RouteCommand implements java.io.Serializable
{    
    public static enum commandType {TYPE_ACCESSORY, TYPE_LOCOMOTIVE, TYPE_FUNCTION, TYPE_STOP};

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
    
    // Track class version to avoid resetting state every time
    private static final long serialVersionUID = -9112930314120758839L;
        
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
    
    /**
     * Returns a stop command
     * @return 
     */
    public static RouteCommand RouteCommandStop()
    {
        RouteCommand r = new RouteCommand(TYPE_STOP);
        
        return r;
    }

    public boolean isStop()
    {
        return this.type == TYPE_STOP;
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
    
    @Override
    public boolean equals(Object o)
    {
        // If the object is compared with itself then return true 
        if (o == this)
        {
            return true;
        }
 
        if (!(o instanceof RouteCommand))
        {
            return false;
        }
         
        // typecast o to RouteCommand so that we can compare data members
        RouteCommand rc = (RouteCommand) o;
        
        return this.type == rc.getType() && this.commandConfig.equals(rc.getCommandConfig());
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.type);
        hash = 37 * hash + Objects.hashCode(this.commandConfig);
        return hash;
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
        else if (this.isStop())
        {
            return "stop";
        }
        
        return typeString + ": " + this.commandConfig.toString();
    }
    
    public JSONObject toJSON() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException
    {		
        JSONObject jsonObj = new JSONObject();
        Field map = jsonObj.getClass().getDeclaredField("map");
        map.setAccessible(true);
        map.set(jsonObj, new LinkedHashMap<>());
        map.setAccessible(false);
        
        jsonObj.put("type", this.getType().toString());

        JSONObject configObj = new JSONObject();
            
        map = configObj.getClass().getDeclaredField("map");
        map.setAccessible(true);
        map.set(configObj, new LinkedHashMap<>());
        map.setAccessible(false);
        
        for (Entry<String, String> e: this.getCommandConfig().entrySet())
        {
            configObj.put(e.getKey(), e.getValue());
        }
            
        jsonObj.put("state", configObj);
  
        return jsonObj;
    }
}
