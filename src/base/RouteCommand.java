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

    public static final String LOC_SPEED_PREFIX = "locspeed";
    public static final String LOC_FUNC_PREFIX = "locfunc";
    
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
    
    public boolean isLocomotive()
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
    
    public String getName()
    {
        return this.commandConfig.get(KEY_NAME);
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
    
    public static RouteCommand fromJSON(JSONObject jsonObject) throws IllegalArgumentException
    {
        RouteCommand.commandType type = RouteCommand.commandType.valueOf(jsonObject.getString("type"));
        RouteCommand routeCommand;

        switch (type)
        {
            case TYPE_ACCESSORY:
                int address = Integer.parseInt(jsonObject.getJSONObject("state").getString("ADDRESS"));
                boolean setting = Boolean.parseBoolean(jsonObject.getJSONObject("state").getString("SETTING"));
                routeCommand = RouteCommand.RouteCommandAccessory(address, setting);
                break;

            case TYPE_FUNCTION:
                String name = jsonObject.getJSONObject("state").getString("NAME");
                int function = Integer.parseInt(jsonObject.getJSONObject("state").getString("FUNCTION"));
                boolean functionSetting = Boolean.parseBoolean(jsonObject.getJSONObject("state").getString("SETTING"));
                routeCommand = RouteCommand.RouteCommandFunction(name, function, functionSetting);
                break;

            case TYPE_LOCOMOTIVE:
                String locoName = jsonObject.getJSONObject("state").getString("NAME");
                int speed = Integer.parseInt(jsonObject.getJSONObject("state").getString("SPEED"));
                routeCommand = RouteCommand.RouteCommandLocomotive(locoName, speed);
                break;

            case TYPE_STOP:
                routeCommand = RouteCommand.RouteCommandStop();
                break;

            default:
                throw new IllegalArgumentException("Invalid command type in route command JSON.");
        }

        // Adding delay if present
        if (jsonObject.getJSONObject("state").has("DELAY"))
        {
            int delay = Integer.parseInt(jsonObject.getJSONObject("state").getString("DELAY"));
            routeCommand.setDelay(delay);
        }

        return routeCommand;
    }
    
    
    /**
     * Returns a simple string representation of this command
     * @return 
     */
    public String toLine()
    {
        if (this.isAccessory())
        {
            return Integer.toString(this.getAddress()) + "," + (this.getSetting() ? "1" : "0") + (this.getDelay() > 0 ? "," + this.getDelay() : "") + "\n";
        }
        else if (this.isStop())
        {
            return this.toString() + "\n";
        }
        else if (this.isLocomotive())
        {
            return LOC_SPEED_PREFIX + "," + this.getName() + "," + this.getSpeed() + (this.getDelay() > 0 ? "," + this.getDelay() : "") + "\n";
        }
        else if (this.isFunction())
        {
            return LOC_FUNC_PREFIX + "," + this.getName() + "," + this.getFunction() + "," + (this.getSetting() ? "1" : "0") + (this.getDelay() > 0 ? "," + this.getDelay() : "") + "\n";
        }
        
        return "invalid command";
    }
    
    /**
     * Parses a simple string representation of the route (equivalent to Route.toCSV)
     * @param line
     * @return 
     */
    public static RouteCommand fromLine(String line)
    {
        if ("stop".equals(line.trim()))
        {
            return RouteCommand.RouteCommandStop();
        }
        else if (line.trim().startsWith(LOC_SPEED_PREFIX + ","))
        {
            String name = line.split(",")[1].trim();
            int speed = Integer.parseInt(line.split(",")[2].trim());
            
            // Validate speed, negative means instant stop
            if (speed < 0) speed = -1;
            if (speed > 100) speed = 100;

            RouteCommand rc = RouteCommand.RouteCommandLocomotive(name, speed);

            if (line.split(",").length > 3)
            {
                rc.setDelay(Math.abs(Integer.parseInt(line.split(",")[3].trim())));     
            }

            return rc;
        }
        else if (line.trim().startsWith(LOC_FUNC_PREFIX + ","))
        {
            String name = line.split(",")[1].trim();
            int fno = Math.abs(Integer.parseInt(line.split(",")[2].trim()));
            boolean state = line.split(",")[3].trim().equals("1");

            RouteCommand rc = RouteCommand.RouteCommandFunction(name, fno, state);

            if (line.split(",").length > 4)
            {
                rc.setDelay(Math.abs(Integer.parseInt(line.split(",")[4].trim())));     
            }

            return rc;
        }
        else if (line.trim().length() > 0)
        {
            int address = Math.abs(Integer.parseInt(line.split(",")[0].trim()));
            boolean state = line.split(",")[1].trim().equals("1");

            RouteCommand rc = RouteCommand.RouteCommandAccessory(address, state);

            if (line.split(",").length > 2)
            {
                rc.setDelay(Math.abs(Integer.parseInt(line.split(",")[2].trim())));     
            }

            return rc;
        }
        
        return null;
    }
}
