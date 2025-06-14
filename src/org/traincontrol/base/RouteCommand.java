package org.traincontrol.base;

import static org.traincontrol.base.RouteCommand.commandType.TYPE_ACCESSORY;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_LIGHTS_ON;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_AUTONOMY_LIGHTS_ON;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_FUNCTION;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_FUNCTIONS_OFF;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_LOCOMOTIVE;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_STOP;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_AUTO_LOCOMOTIVE;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_FEEDBACK;
import static org.traincontrol.base.RouteCommand.commandType.TYPE_ROUTE;

/**
 * Individual route command
 * 
 * @author Adam
 */
public class RouteCommand implements java.io.Serializable
{    
    public static enum commandType {TYPE_ACCESSORY, TYPE_LOCOMOTIVE, TYPE_FUNCTION, 
    TYPE_STOP, TYPE_AUTONOMY_LIGHTS_ON, TYPE_FUNCTIONS_OFF, TYPE_LIGHTS_ON, TYPE_FEEDBACK, TYPE_ROUTE,
    TYPE_AUTO_LOCOMOTIVE};

    public static final String LOC_AUTO_PREFIX = "autoloc";
    public static final String LOC_SPEED_PREFIX = "locspeed";
    public static final String LOC_FUNC_PREFIX = "locfunc";
    public static final String FEEDBACK_PREFIX = "Feedback";
    
    public static final String COMMAND_ROUTE_PREFIX = "Route";
    public static final String COMMAND_EMERGENCY_STOP = "Emergency Stop";
    public static final String COMMAND_ALL_LIGHTS_ON_AUTONOMY_LOCOMOTIVES_ONLY = "All Lights On (Autonomy Locomotives Only)";
    public static final String COMMAND_ALL_LIGHTS_ON = "All Lights On";
    public static final String COMMAND_ALL_FUNCTIONS_OFF = "All Functions Off";
        
    public static String KEY_NAME = "NAME";
    public static String KEY_ADDRESS = "ADDRESS";
    public static String KEY_FUNCTION = "FUNCTION";
    public static String KEY_SETTING = "SETTING";
    public static String KEY_SPEED = "SPEED";
    public static String KEY_DELAY = "DELAY";
    public static String KEY_PROTOCOL = "PROTOCOL";
    public static String KEY_ACCESSORY_TYPE = "ACCESSORY_TYPE";

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
     * @param protocol
     * @param setting
     * @return 
     */
    public static RouteCommand RouteCommandAccessory(int address, Accessory.accessoryDecoderType protocol, boolean setting)
    {
        RouteCommand r = new RouteCommand(TYPE_ACCESSORY);
        
        r.commandConfig.put(KEY_ADDRESS, Integer.toString(address));
        r.commandConfig.put(KEY_SETTING, Boolean.toString(setting));
        r.commandConfig.put(KEY_PROTOCOL, protocol.toString());
                
        return r;
    }
    
    /**
     * Returns a full feedback command
     * @param address
     * @param setting
     * @return
     */
    public static RouteCommand RouteCommandFeedback(int address, boolean setting)
    {
        RouteCommand r = new RouteCommand(TYPE_FEEDBACK);
        
        r.commandConfig.put(KEY_ADDRESS, Integer.toString(address));
        r.commandConfig.put(KEY_SETTING, Boolean.toString(setting));
        
        return r;
    }
    
    /**
     * Returns a full route command
     * @param name
     * @return
     */
    public static RouteCommand RouteCommandRoute(String name)
    {
        RouteCommand r = new RouteCommand(TYPE_ROUTE);
        
        r.commandConfig.put(KEY_NAME, name);
        
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
     * Returns a full auto locomotive command
     * @param name
     * @param s88
     * @return 
     */
    public static RouteCommand RouteCommandAutoLocomotive(String name, int s88)
    {
        RouteCommand r = new RouteCommand(TYPE_AUTO_LOCOMOTIVE);
        
        r.commandConfig.put(KEY_NAME, name);
        r.commandConfig.put(KEY_ADDRESS, Integer.toString(s88));
        
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
    
    public static RouteCommand RouteCommandAutonomyLightsOn()
    {
        RouteCommand r = new RouteCommand(TYPE_AUTONOMY_LIGHTS_ON);
        
        return r;
    }
    
    public static RouteCommand RouteCommandLightsOn()
    {
        RouteCommand r = new RouteCommand(TYPE_LIGHTS_ON);
        
        return r;
    }
    
    public static RouteCommand RouteCommandFunctionsOff()
    {
        RouteCommand r = new RouteCommand(TYPE_FUNCTIONS_OFF);
        
        return r;
    }
    
    public boolean isLightsOn()
    {
        return this.type == TYPE_LIGHTS_ON;
    }
    
    public boolean isAutonomyLightsOn()
    {
        return this.type == TYPE_AUTONOMY_LIGHTS_ON;
    }
    
    public boolean isFunctionsOff()
    {
        return this.type == TYPE_FUNCTIONS_OFF;
    }

    public boolean isStop()
    {
        return this.type == TYPE_STOP;
    }
    
    public boolean isRoute()
    {
        return this.type == TYPE_ROUTE;
    }
    
    public boolean isLocomotive()
    {
        return this.type == TYPE_LOCOMOTIVE;
    }
    
    public boolean isAutoLocomotive()
    {
        return this.type == TYPE_AUTO_LOCOMOTIVE;
    }
    
    public boolean isAccessory()
    {
        return this.type == TYPE_ACCESSORY;
    }
    
    public boolean isFunction()
    {
        return this.type == TYPE_FUNCTION;
    }
    
    public boolean isFeedback()
    {
        return this.type == TYPE_FEEDBACK;
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
    
    /**
     * Changes the stored name of the route command
     * Used primarily when renaming routes
     * @param name 
     */
    public void setName(String name)
    {
        this.commandConfig.put(KEY_NAME, name);
    }
    
    public Accessory.accessoryDecoderType getProtocol()
    {
        return Accessory.determineAccessoryDecoderType(this.commandConfig.get(KEY_PROTOCOL));
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
    
    public String getAccessoryType()
    {
        return this.commandConfig.get(KEY_ACCESSORY_TYPE);
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
        else if (this.isFeedback())
        {
            typeString = "Feedback";
        }
        else if (this.isRoute())
        {
            typeString = COMMAND_ROUTE_PREFIX;
        }
        else if (this.isStop())
        {
            return COMMAND_EMERGENCY_STOP;
        }
        else if (this.isAutonomyLightsOn())
        {
            return COMMAND_ALL_LIGHTS_ON_AUTONOMY_LOCOMOTIVES_ONLY;
        }
        else if (this.isLightsOn())
        {
            return COMMAND_ALL_LIGHTS_ON;
        }
        else if (this.isFunctionsOff())
        {
            return COMMAND_ALL_FUNCTIONS_OFF;
        }
        else if (this.isAutoLocomotive())
        {
            return LOC_AUTO_PREFIX;
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
                int address = Integer.parseInt(jsonObject.getJSONObject("state").getString(KEY_ADDRESS));
                boolean setting = Boolean.parseBoolean(jsonObject.getJSONObject("state").getString(KEY_SETTING));
                String protocol = jsonObject.getJSONObject("state").optString(KEY_PROTOCOL, "");
                routeCommand = RouteCommand.RouteCommandAccessory(address, Accessory.determineAccessoryDecoderType(protocol), setting);
                break;
                
            case TYPE_FEEDBACK:
                int fAddress = Integer.parseInt(jsonObject.getJSONObject("state").getString(KEY_ADDRESS));
                boolean fSetting = Boolean.parseBoolean(jsonObject.getJSONObject("state").getString(KEY_SETTING));
                routeCommand = RouteCommand.RouteCommandFeedback(fAddress, fSetting);
                break;

            case TYPE_FUNCTION:
                String name = jsonObject.getJSONObject("state").getString(KEY_NAME);
                int function = Integer.parseInt(jsonObject.getJSONObject("state").getString(KEY_FUNCTION));
                boolean functionSetting = Boolean.parseBoolean(jsonObject.getJSONObject("state").getString(KEY_SETTING));
                routeCommand = RouteCommand.RouteCommandFunction(name, function, functionSetting);
                break;

            case TYPE_LOCOMOTIVE:
                String locoName = jsonObject.getJSONObject("state").getString(KEY_NAME);
                int speed = Integer.parseInt(jsonObject.getJSONObject("state").getString(KEY_SPEED));
                routeCommand = RouteCommand.RouteCommandLocomotive(locoName, speed);
                break;

            case TYPE_STOP:
                routeCommand = RouteCommand.RouteCommandStop();
                break;
                
            case TYPE_LIGHTS_ON:
                routeCommand = RouteCommand.RouteCommandLightsOn();
                break;
                
            case TYPE_AUTONOMY_LIGHTS_ON:
                routeCommand = RouteCommand.RouteCommandAutonomyLightsOn();
                break;
                
            case TYPE_FUNCTIONS_OFF:
                routeCommand = RouteCommand.RouteCommandFunctionsOff();
                break;
                
            case TYPE_ROUTE:
                String rName = jsonObject.getJSONObject("state").getString(KEY_NAME);
                routeCommand = RouteCommand.RouteCommandRoute(rName);
                break;
                
            case TYPE_AUTO_LOCOMOTIVE:
                String locName = jsonObject.getJSONObject("state").getString(KEY_NAME);
                int s88Addr = Integer.parseInt(jsonObject.getJSONObject("state").getString(KEY_ADDRESS));
                routeCommand = RouteCommand.RouteCommandAutoLocomotive(locName, s88Addr);
                break;

            default:
                throw new IllegalArgumentException("Invalid command type in route command JSON.");
        }
        
        // Adding delay if present
        if (jsonObject.getJSONObject("state").has(KEY_DELAY) && !jsonObject.getJSONObject("state").getString(KEY_DELAY).isEmpty())
        {
            int delay = Integer.parseInt(jsonObject.getJSONObject("state").getString(KEY_DELAY));
            routeCommand.setDelay(delay);
        }

        return routeCommand;
    }
    
    /**
     * Returns a simple string representation of this command
     * @param linkedAccessory the accessory object linked to this command's address - so that we can pretty print the accessory type
     * @return 
     */
    public String toLine(Accessory linkedAccessory)
    {
        if (this.isFeedback())
        {
            return RouteCommand.FEEDBACK_PREFIX + " " + Integer.toString(this.getAddress()) + "," + (this.getSetting() ? "1" : "0");
        }
        else if (this.isRoute())
        {
            return RouteCommand.COMMAND_ROUTE_PREFIX + " " + this.getName() + "\n";
        }
        else if (this.isAccessory())
        {
            String delayString = (this.getDelay() > 0 ? "," + this.getDelay() : "") + "\n";
            
            if (linkedAccessory != null)
            {
                return linkedAccessory.toAccessorySettingString(this.getSetting(), this.getProtocol().toString()) + delayString;
            }
            else
            {
                String protocol = "";
                
                if (this.getProtocol() != null && this.getProtocol() != Accessory.DEFAULT_IMPLICIT_PROTOCOL)
                {
                    protocol = " " + this.getProtocol().toString();
                }
                
                return Integer.toString(this.getAddress()) + protocol + "," + (this.getSetting() ? "1" : "0") + delayString;
            }
        }
        else if (this.isStop() || this.isFunctionsOff() || this.isLightsOn() || this.isAutonomyLightsOn())
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
        else if (this.isAutoLocomotive())
        {
            return LOC_AUTO_PREFIX + "," + this.getName() + "," + this.getAddress() + "\n";
        }
        
        return "invalid command";
    }
    
    /**
     * Converts a route command integer (1 or 0) to boolean, throws an error otherwise
     * @param setting
     * @return
     * @throws Exception 
     */
    private static boolean intToAccessorySwitched(int setting) throws Exception
    {
        if (setting != 1 && setting != 0)
        {
            throw new Exception("Invalid accessory setting value: " + setting);
        }
        
        return setting == 1;
    }
    
    /**
     * Parses a simple string representation of the route (equivalent to Route.toCSV)
     * @param line
     * @param extractAccessoryType
     * @return 
     * @throws java.lang.Exception 
     */
    public static RouteCommand fromLine(String line, boolean extractAccessoryType) throws Exception
    {
        line = line.trim();
        
        if (COMMAND_EMERGENCY_STOP.equals(line))
        {
            return RouteCommand.RouteCommandStop();
        }
        else if (COMMAND_ALL_LIGHTS_ON.equals(line))
        {
            return RouteCommand.RouteCommandLightsOn();
        }
        else if (COMMAND_ALL_LIGHTS_ON_AUTONOMY_LOCOMOTIVES_ONLY.equals(line))
        {
            return RouteCommand.RouteCommandAutonomyLightsOn();
        }
        else if (COMMAND_ALL_FUNCTIONS_OFF.equals(line))
        {
            return RouteCommand.RouteCommandFunctionsOff();
        }
        else if (line.startsWith(COMMAND_ROUTE_PREFIX))
        {
            String routeName = line.replaceFirst(COMMAND_ROUTE_PREFIX, "").trim();
            
            if ("".equals(routeName))
            {
                throw new Exception("Command \"" + line + "\" is missing the route name");
            }
            
            return RouteCommand.RouteCommandRoute(routeName);
        }
        else if (line.startsWith(LOC_SPEED_PREFIX + ","))
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
        else if (line.startsWith(LOC_FUNC_PREFIX + ","))
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
        else if (line.startsWith(LOC_AUTO_PREFIX + ","))
        {
            String name = line.split(",")[1].trim();
            int s88 = Math.abs(Integer.parseInt(line.split(",")[2].trim()));

            RouteCommand rc = RouteCommand.RouteCommandAutoLocomotive(name, s88);

            return rc;
        }
        else if (line.toLowerCase().startsWith(FEEDBACK_PREFIX.toLowerCase()))
        {
            String originalLine = line;
            
            line = line.toLowerCase().replace(FEEDBACK_PREFIX.toLowerCase(), "").trim();
            
            try
            {
                int address = Math.abs(Integer.parseInt(line.split(",")[0].trim()));
                boolean state = "1".equals(line.split(",")[1]);
                
                return RouteCommand.RouteCommandFeedback(address, state);
            }
            catch (Exception e2)
            {
                throw new Exception("Invalid line: " + originalLine);
            }
        }
        else if (line.length() > 0)
        {
            // Filter text - pretend it's just numbers
            String originalLine = line;
            
            // Input here could be any of: 
            // switch 1,1 
            // switch 1,turn
            // switch 1 dcc,turn,200
            // 1,1
            // 1,turn
            
            line = line.trim();
            
            // Switch 1 dcc
            // Switch 1
            // 1 dcc
            // 1
            String regex = "^([A-Za-z]*)\\s*([0-9]+)\\s*([A-Za-z0-9]*)$";

            // Compile the pattern
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(line.split(",")[0].trim());
            
            String accessoryTypePrefix = "";
            String accessoryAddress = "";
            String accessoryProtocol = "";

            // Check if the input matches the pattern
            if (matcher.matches())
            {
                // Extract and print the capturing groups
                accessoryTypePrefix = matcher.group(1); // First capturing group
                accessoryAddress = matcher.group(2); // Second capturing group
                accessoryProtocol = matcher.group(3); // Third capturing group
            }
            
            // Not needed because this info gets discarded
            // line = line.replace(Accessory.accessoryTypeToPrettyString(Accessory.accessoryType.SWITCH), "");

            int address = Math.abs(Integer.parseInt(accessoryAddress));
            
            // Validate & default protocol
            Accessory.accessoryDecoderType decoderType = Accessory.determineAccessoryDecoderType(accessoryProtocol);
                        
            boolean state;
            
            // If parsing the string fails, treat it as a number
            try
            {
                state = intToAccessorySwitched(Integer.parseInt(line.split(",")[1].trim()));
            }
            catch (NumberFormatException e)
            {
                try
                {
                    state = Accessory.stringAccessorySettingToSetting(line.split(",")[1].trim());
                }
                catch (Exception e2)
                {
                    throw new Exception("Invalid line: " + originalLine);
                }
            }
            
            RouteCommand rc = RouteCommand.RouteCommandAccessory(address, decoderType, state);

            if (line.split(",").length > 2)
            {
                rc.setDelay(Math.abs(Integer.parseInt(line.split(",")[2].trim())));     
            }

            if (extractAccessoryType)
            {
                rc.getCommandConfig().put(KEY_ACCESSORY_TYPE, accessoryTypePrefix);
            }
            
            return rc;
        }
        
        return null;
    }
    
    /**
     * Returns whether this RouteCommand can be a condition, rather than a directly executed command
     * @return 
     */
    public boolean isConditionCommand()
    {
        return this.isAccessory() || this.isFeedback() || this.isAutoLocomotive();
    }
}
