package org.traincontrol.marklin.file;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.marklin.MarklinLayout;
import org.traincontrol.marklin.MarklinLayoutComponent;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.traincontrol.base.Accessory;
import org.traincontrol.marklin.MarklinAccessory;

/**
 * Marklin Central Station 2/3 config file parser
 * @author Adam
 */
public final class CS2File
{
    // IP address for our HTTP requests
    private final String IP;
    
    // Control station
    MarklinControlStation control;
    
    // Store the URL to the CS2 layout config by default
    // Can be overriden by files on the local filesystem (if using a CS3, etc)
    private String layoutDataLoc;
    
    // Cache CS3 mags
    private final Map<Integer, JSONObject> magList;
        
    /**
     * Constructor
     * @param IP 
     * @param control 
     */
    public CS2File(String IP, MarklinControlStation control)
    {
        this.IP = IP;
        this.control = control;
        this.setDefaultLayoutDataLoc();
        this.magList = new HashMap<>();
    }
    
    /**
     * Sets the layout data location to the CS2 IP
     */
    public void setDefaultLayoutDataLoc()
    {
        this.layoutDataLoc = getDefaultLayoutDataLoc();
    }
    
    /**
     * Gets the default data location
     * @return 
     */
    public String getDefaultLayoutDataLoc()
    {
        return "http://" + this.IP;
    }
    
    /**
     * Sets the layout data location to a custom local path)
     * @param path 
     */
    public void setLayoutDataLoc(String path)
    {
        this.layoutDataLoc = path;
    }
    
    /**
     * Safely logs a message
     * @param message 
     */
    private void logMessage(String message)
    {
        logMessage(message, null, false);
    }
    
    /**
     * Safely logs a message
     * @param message 
     * @param e
     */
    private void logMessage(String message, Exception e, boolean debugOnly)
    {
        if (this.control != null)
        {
            if (!debugOnly || this.control.isDebug())
            {
                this.control.log(message);
            }
            
            if (this.control.isDebug() && e != null) this.control.log(e);
        }
        else 
        {
            System.out.println(message);
            if (e != null) e.printStackTrace();
        }
    }
    
    /**
     * IP to ping at startup
     * @param host
     * @return 
     */
    public static String getPingIP(String host)
    {
        return "http://" + host + "/can/";
    }
    
    /**
     * Fixes URL character issues
     * @param URL
     * @return 
     */
    public String sanitizeURL(String URL)
    {
        try
        {
            return URLEncoder.encode(URL, StandardCharsets.UTF_8.toString()).replace("+", "%20");
        }
        catch (UnsupportedEncodingException ex)
        {
            this.logMessage("URL encoding error: " + ex.getMessage());
 
            return URL.replace(" ", "%20");
        }
    }
    
    /**
     * Gets an image URL
     * @param image
     * @return 
     */
    public String getImageURL(String image)
    {
        return "http://" + this.IP + "/icons/" + 
                this.sanitizeURL(image) + ".png";
    }
    
    /**
     * Gets an image URL
     * @param image
     * @return 
     */
    public String getImageURLCS3(String image)
    {
        return "http://" + this.IP + "/app/assets/lok/" + 
                this.sanitizeURL(image) + ".png";
    }
    
    /**
     * Locomotive config file
     * @return 
     */
    public String getLocURL()
    {
        return "http://" + this.IP + "/config/lokomotive.cs2";
    }
    
    /**
     * Route config file
     * @return 
     */
    public String getRouteURL()
    {
        return "http://" + this.IP + "/config/fahrstrassen.cs2";
    }
    
    /**
     * CS2 accessory config file
     * @return 
     */
    public String getMagURL()
    {
        return "http://" + this.IP + "/config/magnetartikel.cs2";
    }
    
    /**
     * Layout index file
     * @return 
     */
    public String getLayoutMasterURL()
    {
        return getLayoutMasterURL(this.layoutDataLoc);
    }
    
    /**
     * Layout index file
     * @param dataPath
     * @return 
     */
    public static String getLayoutMasterURL(String dataPath)
    {
        return dataPath + "/config/gleisbild.cs2";
    }
    
    /**
     * Layout file
     * @param layoutName
     * @return 
     */
    public String getLayoutURL(String layoutName)
    {
        return getLayoutURL(this.layoutDataLoc, layoutName);
    }
    
    /**
     * Layout file
     * @param dataPath
     * @param layoutName
     * @return 
     */
    public String getLayoutURL(String dataPath, String layoutName)
    {
        return dataPath + "/config/gleisbilder/" 
                + (dataPath.contains("http://") ? sanitizeURL(layoutName) : layoutName) + ".cs2";
    }
    
    /**
     * CS3 Web App URL
     * @return 
     */
    public String getCS3AppUrl()
    {
        return "http://" + this.IP + "/app";
    }
    
    /**
     * CS3 Locomotive DB URL
     * @return 
     */
    public String getCS3LocDBUrl()
    {
        return "http://" + this.IP + "/app/api/loks";
    }
    
    /**
     * CS3 Route DB URL
     * @return 
     */
    public String getCS3RouteDBUrl()
    {
        return "http://" + this.IP + "/app/api/automatics";
    }
    
    /**
     * CS3 Accessory DB URL
     * @return 
     */
    public String getCS3MagDBUrl()
    {
        return "http://" + this.IP + "/app/api/mags";
    }
        
        
    /**
     * CS3 Layout Data URL
     * Parsing this is not currently supported - 
     * with a CS3 we can display offline CS2 layout files instead
     * @return 
     */
    public String getCS3LayoutUrl()
    {
        return "http://" + this.IP + "/app/api/gbs";
    }
    
    /**
     * Device info file for the CS3
     * @return 
     */
    public String getDeviceInfoURL()
    {
        return getDeviceInfoURL(this.IP);
    }
    
    /**
     * Device info file for the CS3
     * @param ipAddress
     * @return 
     */
    public static String getDeviceInfoURL(String ipAddress)
    {
        return "http://" + ipAddress + "/config/geraet.vrs";
    }
    
    /**
     * Check if this is a CS3 by looking at the info file
     * @param deviceInfoUrl device info URL obtained by calling getDeviceInfoURL
     * @return
     * @throws Exception 
     */
    public static boolean isCS3(String deviceInfoUrl) throws Exception
    {
        BufferedReader content = fetchURL(deviceInfoUrl);

        while (true)
        {
            String line = content.readLine();
         
            if (line == null)
            {
                break;
            }
            else
            {
                if (line.contains("Central Station 3"))
                {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Opens a URL and returns a string stream
     * @param url
     * @return
     * @throws Exception 
     */
    public static BufferedReader fetchURL(String url) throws Exception 
    {
        URL website = new URL(url);
        URLConnection connection = website.openConnection();
        return new BufferedReader(
            new InputStreamReader(
                connection.getInputStream(), StandardCharsets.UTF_8)
        );
    }

    /**
     * Parses a CS2 config file into a string map
     * @param in
     * @return
     * @throws Exception 
     */
    public static List<Map<String, String> > parseFile(BufferedReader in) throws Exception
    {
        List<Map<String, String> > items = new ArrayList <>();
                
        String s;
        String lastKey = null;
        Map<String, String> item = null;
        
        Map<String, String> array = new HashMap<>();
        
        while (true) 
        {          
            s = in.readLine();
            
            // Done final pass...
            if (s == null)
            {
                s = "__done";
            }
            
            // F17-32 get a different key on the CS2
            s = s.replace(".funktionen_2", ".funktionen");
                        
            if (s.matches("^ \\.\\.[a-z]+=.+$"))
            {
                String[] parts = s.substring(3).split("=");

                array.put(parts[0], parts[1]);
            }
            else
            {
                if (!array.isEmpty() && item != null)
                {
                    String current = "";
                    if (item.containsKey(lastKey))
                    {
                        current = item.get(lastKey);
                    }
                    
                    String arrayString = current + array.toString();
                    
                    // A dirty but effective workaround
                    arrayString = arrayString.replace("}{", "|");
                    arrayString = arrayString.replace(", ", ",");

                    item.put(lastKey, arrayString);
                    
                    array.clear();
                }
                
                if (s.matches("^[a-z]+$"))
                {                
                    if (item != null)
                    {
                        items.add(item);
                    }

                    item = new HashMap<>();

                    item.put("_type", s);
                }
                else if (s.matches("^ \\.[a-z0-9A-Z]+=.+$"))
                {
                    String[] parts = s.substring(2).split("=");
                    
                    if (item != null)
                    {
                        item.put(parts[0], parts[1]);
                    }
                }   
                else if (s.matches("^ \\.[a-z]+$"))
                {
                    lastKey = s.substring(2);
                }
            } 
            
            // We need to add the current item to the list...
            if (s.equals("__done"))
            {   
                if (item != null)
                {
                    items.add(item);
                }
                
                break;
            }
        }
        
        // Release the resource
        in.close();
        
        return items;
    }
    
    public List<MarklinRoute> parseRoutes() throws Exception
    {
        return parseRoutes(parseFile(fetchURL(getRouteURL())), 
            parseMags(parseFile(fetchURL(getMagURL())))
        );
    }
    
    public List<MarklinRoute> parseRoutesCS3() throws Exception
    {
        return parseRoutesCS3(parseJSONObject(fetchURL(getCS3RouteDBUrl())), parseJSONArray(fetchURL(getCS3MagDBUrl())));
    }
    
    public List<MarklinLocomotive> parseLocomotives() throws Exception
    {
        return parseLocomotives(parseFile(fetchURL(getLocURL())));
    }
    
    public List<MarklinLocomotive> parseLocomotivesCS3() throws Exception
    {
        return parseLocomotivesCS3(parseJSONArray(fetchURL(this.getCS3LocDBUrl())));
    }
    
    /**
     * Reads a CS2 accessory database
     * Unsure if this is complete - we only care about checking if an address is DCC
     * @param l parsed data
     * @return list of routes
     * @throws Exception 
     */
    public List<MarklinAccessory> parseMags(List<Map<String, String> > l) throws Exception
    {        
        List<MarklinAccessory> out = new ArrayList<>();
        
        for (Map<String, String> m : l)
        {
            if ("artikel".equals(m.get("_type")))
            {
                if (m.get("id") == null || m.get("typ") == null)
                {
                    control.log("Invalid CS2 accessory: " + m.toString());
                    continue;
                }
                
                MarklinAccessory acc = new MarklinAccessory(control, 
                    Integer.parseInt(m.get("id")),
                    m.get("typ").contains("weiche") ? 
                        Accessory.accessoryType.SWITCH :
                        Accessory.accessoryType.SIGNAL, 
                    m.get("dectyp") != null ?
                        MarklinAccessory.determineAccessoryDecoderType(m.get("dectyp").toUpperCase().trim()) :
                        Accessory.accessoryDecoderType.MM2,
                    m.get("name"), 
                    !"0".equals(m.get("stellung")), 
                    0);
                            
                out.add(acc);       
             }
        }
        
        return out;
    }
    
    /**
     * Reads a CS2 route database
     * @param l parsed data
     * @param accDB
     * @return list of routes
     * @throws Exception 
     */
    public List<MarklinRoute> parseRoutes(List<Map<String, String> > l, List<MarklinAccessory> accDB) throws Exception
    {        
        List<MarklinRoute> out = new ArrayList<>();
        
        // Easily map to ID
        Map<Integer, MarklinAccessory> addressMap = accDB.stream()
            .collect(Collectors.toMap(
                    MarklinAccessory::getAddress, 
                    accessory -> accessory,
                    (existing, replacement) -> existing // uncouplers will have the same ID
            ));
        
        for (Map<String, String> m : l)
        {
            if ("fahrstrasse".equals(m.get("_type")))
            {
                MarklinRoute r = new MarklinRoute(control, m.get("name"), Integer.parseInt(m.get("id")));
                
                String route = m.get("item").replace("{", "").replace("}","");
                String[] pieces = route.split("\\|");

                if (m.containsKey("s88"))
                {
                    r.setS88(Integer.parseInt(m.get("s88")));
                }
                
                if (m.containsKey("s88Ein"))
                {
                    r.setTriggerType(MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR);
                }
                
                if (m.containsKey("extern"))
                {
                    // This variable indicates that the route will automatically fire
                    // As this would duplicate functionality with the CS2, we leave it disabled
                    // r.enable();
                }
                
                for (String piece : pieces)
                {
                    String[] infos = piece.split(",");

                    Integer id = 0;
                    Integer setting = 0;
                    Integer delay = 0;

                    Integer conditionS88 = 0;
                    Integer s88Status = 1;
                    
                    for (String info : infos)
                    {
                        if (info.contains("="))
                        {
                            String[] kv = info.split("=");
                            
                            if ("magnetartikel".equals(kv[0]))
                            {
                                id = Integer.valueOf(kv[1].trim());
                            }
                            
                            if ("stellung".equals(kv[0]))
                            {
                                setting = Integer.valueOf(kv[1].trim());
                            }
                            
                            if ("sekunde".equals(kv[0]))
                            {
                                delay = Float.valueOf(kv[1]).intValue() * 1000;
                            }
                               
                            // Condition S88s
                            if ("kont".equals(kv[0]))
                            {         
                                // Already saw a previous condition - add it to route
                                if (conditionS88 != 0)
                                {
                                    r.addConditionS88(conditionS88, s88Status != 0);
                                    s88Status = 1;
                                }
                                
                                conditionS88 = Integer.valueOf(kv[1].trim());
                            }
                            
                            if ("hi".equals(kv[0]))
                            {                                
                                s88Status = Integer.valueOf(kv[1].trim());
                            }
                        }
                        
                        // Add final condition to route
                        if (conditionS88 != 0)
                        {
                            r.addConditionS88(conditionS88, s88Status != 0);
                        }
                    }
                    
                    // Handle 3-way switches and signals
                    if (id > 0)
                    {
                        // Determine the decoder type
                        Accessory.accessoryDecoderType accType = Accessory.accessoryDecoderType.MM2;
                        
                        if (addressMap.get(id) != null)
                        {
                            accType = addressMap.get(id).getDecoderType();
                        }
                        
                        if (setting >= 2)
                        {
                            r.addAccessory(id + 1, accType, setting == 2);
                            
                            if (delay > 0)
                            {
                                r.setDelay(id + 1, delay);
                            }
                        }
                        
                        r.addAccessory(id, accType, setting != 1 && setting != 3);
                        
                        // Only set the delay once for three-way switches
                        if (delay > 0 && setting < 2)
                        {
                            r.setDelay(id, delay);
                        }
                        
                        // stellung 0
                        // id -> true (red)
                        // stellung 1
                        // id -> false (green)
                        // stellung 2
                        // id -> true (red)
                        // id + 1 -> true (red)
                        // stellung 3
                        // id -> false (green)
                        // id + 1 -> false (green)
                    }
                }
                                
                if (!r.getRoute().isEmpty())
                {
                    out.add(r);
                }
             }
        }
        
        return out;
    }
    
    /**
     * Extracts functions information from a function string parsed by parseFile
     * 
     * In CS2, if the function type is known, 
     * an integer value for "typ" will be present in the file 
     * @param functionList
     * @return 
     */
    public static int[] parseLocomotiveFunctions(String functionList)
    {
        String[] data = functionList.replace("{", "").replace("}", "").split("\\|");
        
        int[] output = new int[data.length];
        
        // TODO - improve the original parsing approach to make this unnecessary
        // Loop through each function
        for (String functionInfo : data)
        {
            int fn = 0;
            int type = 0;
            
            // Loop through the keys in each function
            for (String functionItem : functionInfo.split(","))
            {
                String[] item = functionItem.split("=");
                
                if ("nr".equals(item[0]))
                {
                    fn = Integer.parseInt(item[1]);
                }
                else if ("typ".equals(item[0]))
                {
                    type = Integer.parseInt(item[1]);
                }
            }
            
            output[fn] = type;
        }
        
        return output;
    }
    
    public static int[] parseFunctionTriggerTypes(String functionList)
    {
        String[] data = functionList.replace("{", "").replace("}", "").split("\\|");
        
        int[] output = new int[data.length];
        
        // TODO - improve the original parsing approach to make this unnecessary
        // Loop through each function
        for (String functionInfo : data)
        {
            int fn = 0;
            int type = 0;
            int dauer = 0;
            
            // Loop through the keys in each function
            for (String functionItem : functionInfo.split(","))
            {
                String[] item = functionItem.split("=");
                
                if ("nr".equals(item[0]))
                {
                    fn = Integer.parseInt(item[1]);
                }
                else if ("typ".equals(item[0]))
                {
                    type = Integer.parseInt(item[1]);
                }
                else if ("dauer".equals(item[0]))
                {
                    dauer = Integer.parseInt(item[1]);
                }
            }
            
            output[fn] = dauer > 0 ? dauer : (type >= 128 ? Locomotive.FUNCTION_PULSE : Locomotive.FUNCTION_TOGGLE);
        }
        
        return output;
    }
    
    public static int[] extractFunctionTypes(int[] functionTypes)
    {
        int[] output = new int[functionTypes.length];
        
        for (int i = 0; i < functionTypes.length; i++)
        {
            output[i] = functionTypes[i] % 128;
        }
        
        return output;
    }
    
    /**
     * Fetches a CS3 accessory DB entry based on its id
     * @param searchId
     * @param mags
     * @return 
     */
    private JSONObject getCS3MagById(int searchId, JSONArray mags)
    {
        // Cache accessories by ID
        if (this.magList.containsKey(searchId))
        {
            return this.magList.get(searchId);
        }
        
        for (int i = 0 ; i < mags.length(); i++)
        {
            JSONObject obj = mags.getJSONObject(i);
            
            if (searchId == obj.getInt("id"))
            {
                this.magList.put(searchId, obj);
                
                return obj;
            }
        }
        
        return null;   
    }
    
    /**
     * Parses routes from the CS3 API
     * @param routes
     * @param mags
     * @return 
     */
    public List<MarklinRoute> parseRoutesCS3(JSONObject routes, JSONArray mags)
    {
        List<MarklinRoute> out = new ArrayList<>();
        
        JSONArray routeList = routes.getJSONArray("automatics");
        
        for (int i = 0 ; i < routeList.length(); i++)
        {
            try
            {
                JSONObject route = routeList.getJSONObject(i);
                
                MarklinRoute r = new MarklinRoute(control, route.getString("name"), route.getInt("id"));
                
                JSONArray items = route.getJSONArray("items");
                
                for (int j = 0; j < items.length(); j++)
                {
                    JSONObject item = items.getJSONObject(j);
                    
                    if (item.has("typ") && "mag".equals(item.getString("typ")) && item.has("magnetartikel"))
                    {
                        // To get the address, we need to look up this accessory in the accessory DB
                        JSONObject accessory = getCS3MagById(item.getInt("magnetartikel"), mags); 
                        
                        if (accessory != null)
                        {
                            // System.out.println(accessory);
                            int address = accessory.getInt("address");
                            
                            Accessory.accessoryDecoderType protocol = Accessory.DEFAULT_IMPLICIT_PROTOCOL;
                            
                            if ("mm".equals(accessory.getString("prot")))
                            {
                                protocol = Accessory.accessoryDecoderType.MM2;
                            }
                            else if ("dcc".equals(accessory.getString("prot")))
                            {
                                protocol = Accessory.accessoryDecoderType.DCC;
                            }
                            
                            // stellung 0 - key not included
                            // this means red/turn
                            if (!item.has("stellung") || "0".equals(item.getString("stellung")))
                            {
                                r.addAccessory(address, protocol, true);
                                
                                // This is invalid for 3-way signals
                                // if (3 == accessory.getInt("states"))
                                if ("dreiwegweiche".equals(accessory.getString("typ")))
                                {
                                    r.addAccessory(address + 1, protocol, false);
                                }
                            }
                            // stellung 1 means isSwitched is false   
                            // this means green/straight
                            else if ("1".equals(item.getString("stellung")))
                            {                                
                                r.addAccessory(address, protocol, false);
                                
                                // This is invalid for 3-way signals
                                // if (3 == accessory.getInt("states"))
                                if ("dreiwegweiche".equals(accessory.getString("typ")))
                                {
                                    r.addAccessory(address + 1, protocol, false);
                                }
                            }
                            else if ("2".equals(item.getString("stellung")))
                            {           
                                r.addAccessory(address, protocol, false);
                                
                                if (3 == accessory.getInt("states"))
                                {
                                    r.addAccessory(address + 1, protocol, true);
                                }
                            }
                            // Unclear how this differs from 1, seems to only be used by certain signals
                            else if ("3".equals(item.getString("stellung")))
                            {           
                                r.addAccessory(address, protocol, false);
                                
                                if (3 == accessory.getInt("states"))
                                {
                                    r.addAccessory(address + 1, protocol, false);
                                }
                            }
                            
                            if (item.has("sekunde"))
                            {
                                r.setDelay(address, Float.valueOf(item.getFloat("sekunde") * 1000).intValue());
                            }
                        }     
                    }
                    else if (item.has("typ") && "s88".equals(item.getString("typ")))
                    {
                        if (r.getRoute().isEmpty())
                        {
                            // Only include S88s at the start.  First is trigger S88, others are treated as condition S88s
                            if (!r.hasS88())
                            {
                                r.setS88(item.getInt("id"));
                                
                                // value key won't be present if unoccupied
                                if (!item.has("value"))
                                {
                                    r.setTriggerType(MarklinRoute.s88Triggers.CLEAR_THEN_OCCUPIED);
                                }
                                else
                                {
                                    r.setTriggerType(MarklinRoute.s88Triggers.OCCUPIED_THEN_CLEAR);
                                }
                                
                                if (2 == item.getInt("mode"))
                                {
                                    // This value indicates that the route will automatically fire
                                    // As this would duplicate functionality with the CS3, we leave it disabled
                                    // r.enable();
                                }
                            }
                            else
                            {
                                r.addConditionS88(item.getInt("id"), !item.has("value"));
                            }
                        }
                        else
                        {
                            logMessage("Warning: ignoring extra S88 " + item.toString() + " in route " + r.getName());
                        }
                    }
                    
                    // System.out.println("---");   
                }
                
                // System.out.println(route);
                // System.out.println(r.toVerboseString());
                // System.out.println("======");
                                    
                out.add(r);
            }
            catch (NumberFormatException | JSONException e)
            {
                logMessage("Failed to add route at index " + i + " due to parsing error.", e, false);
            }
        }
        
        return out;
    }
 
    /**
     * Parses locomotives from the CS3 API
     * @param locomotiveList
     * @return 
     */
    public List<MarklinLocomotive> parseLocomotivesCS3(JSONArray locomotiveList)
    {
        List<MarklinLocomotive> out = new ArrayList<>();
        
        Map<String, String> internalNames = new HashMap<>();
        
        // For multi-units, we need to parse the internal CS3 locomotive names
        for (int i = 0 ; i < locomotiveList.length(); i++)
        {
            try
            {
                JSONObject loc = locomotiveList.getJSONObject(i);
                internalNames.put(loc.getString("internname"), loc.getString("name"));
            }
            catch (JSONException e)
            {
                logMessage("Error pre-parsing locomotives.", e, false);
            }
        }
        
        for (int i = 0 ; i < locomotiveList.length(); i++)
        {
            try
            {
                JSONObject loc = locomotiveList.getJSONObject(i);

                Integer uid = Integer.decode(loc.getString("uid"));

                String name = loc.getString("name");
                String icon = loc.getString("icon");
                
                if (icon != null)
                {
                    String[] pieces = icon.split("/");
                    icon = pieces[pieces.length - 1];
                }

                String type = loc.getString("dectyp");
                MarklinLocomotive.decoderType decoderType;
                Map<String, Double> multiUnitLocMap = new HashMap<>();

                // Multi-units
                if (loc.has("traktion"))
                {
                    decoderType = MarklinLocomotive.decoderType.MULTI_UNIT;
                    
                    // Parse multi unit locomotive names
                    for (int j = 0; j < loc.getJSONArray("traktion").length(); j++)
                    {
                        String identifier = loc.getJSONArray("traktion").getString(j).split(";")[0]; 

                        if (internalNames.containsKey(identifier))
                        { 
                            multiUnitLocMap.put(internalNames.get(identifier), loc.getJSONArray("traktion").getString(j).split(";").length > 1 ? -1.0 : 1.0); 
                        }
                        else
                        {
                            logMessage("Warning: unmatched multi-unit identifier " + identifier);
                        }
                    }
                                    
                    logMessage("Locomotive " + name + " is a multi-unit, using UID of " + uid, null, true);
                    
                    uid = uid - MarklinLocomotive.MULTI_UNIT_BASE;
                }
                // Others
                else
                {
                    if (type.contains("mfx"))
                    {
                        decoderType = MarklinLocomotive.decoderType.MFX;
                        uid = uid - MarklinLocomotive.MFX_BASE;
                    }
                    else if (type.contains("dcc"))
                    {
                        decoderType = MarklinLocomotive.decoderType.DCC;
                        uid = uid - MarklinLocomotive.DCC_BASE;
                    }
                    else
                    {
                        decoderType = MarklinLocomotive.decoderType.MM2;
                    }
                }

                // Parse functions
                List<Integer> functionTypes = new ArrayList<>();
                List<Integer> functionTriggerTypes = new ArrayList<>();

                for (Object readArr : loc.getJSONArray("funktionen"))
                {                
                    JSONObject fInfo = (JSONObject) readArr;
                    
                    // Icon path is http://cs3ip/app/assets/fct/fkticon_i_001.svg
                    Integer fType = Math.max(fInfo.getInt("typ"), fInfo.getInt("typ2"));                                          
                    Boolean isMoment = fInfo.getBoolean("isMoment");
                    Integer duration = fInfo.getInt("dauer");

                    functionTypes.add(fType);
                    
                    if (duration > 0)
                    {
                        functionTriggerTypes.add(duration);
                    }
                    else
                    {
                        functionTriggerTypes.add(isMoment ? Locomotive.FUNCTION_PULSE : Locomotive.FUNCTION_TOGGLE);
                    }
                }
                
                MarklinLocomotive newLoc = new MarklinLocomotive(
                    control, 
                    uid, 
                    decoderType,
                    name,
                    functionTypes.stream().mapToInt(k -> k).toArray(),
                    functionTriggerTypes.stream().mapToInt(k -> k).toArray()
                );
                
                if (icon != null)
                {
                    newLoc.setImageURL(this.getImageURLCS3(icon));
                }
                
                if (!multiUnitLocMap.isEmpty())
                {
                    newLoc.setCentralStationMultiUnitLocomotives(multiUnitLocMap);
                }

                out.add(newLoc);
            }
            catch (NumberFormatException | JSONException e)
            {
                logMessage("Failed to add locomotive at index " + i + " due to parsing error.", e, false);
            }
        }
        
        return out;
    }
        
    /**
     * Parses locomotives from the CS2 database / legacy file in CS3
     * @param l data from parseFile
     * @return
     * @throws Exception 
     */
    public List<MarklinLocomotive> parseLocomotives(List<Map<String, String> > l) throws Exception
    {                
        List<MarklinLocomotive> out = new ArrayList<>();
        
        for (Map<String, String> m : l)
        {
            if ("lokomotive".equals(m.get("_type")))
            {
                int address;
                String name = "";
                
                if (m.get("name") != null)
                {
                    name = m.get("name");
                }
                
                if (m.get("adresse") != null)
                {
                    address = Integer.decode(m.get("adresse"));
                }
                else if (m.get("uid") != null)
                {
                    address = Integer.decode(m.get("uid"));
                    
                    logMessage("Locomotive " + name + " has no address field in config file, using UID of " + Integer.toString(address));
                }
                else
                {
                    logMessage("Locomotive " + name + " has no address or UID field in config file. Skipping.  Raw data: " + m.toString());
                    continue;
                }
                                
                MarklinLocomotive.decoderType type;
                Map<String, Double> multiUnitLocMap = new HashMap<>();
                
                // Multi-units
                if (m.get("traktion") != null)
                {
                    type = MarklinLocomotive.decoderType.MULTI_UNIT;
                    
                    address = Integer.decode(m.get("uid"));
                    
                    // String looks like this
                    // "{lokname=Re4/4II 11229SBB,lok=0x4023|lokname=SBBC 421 378-1,lok=0x4024}"
                    List<String> multiUnitLocNames = Arrays.stream(m.get("traktion").replace("{", "").replace("}", "").split("\\|")).map(s -> s.split(",lok=")[0].replace("lokname=", "")) .collect(Collectors.toList());
                                 
                    for (String locName : multiUnitLocNames)
                    { 
                        multiUnitLocMap.put(locName, 1.0);
                    } 
                    
                    logMessage("Locomotive " + name + " is a multi-unit, using UID of " + Integer.toString(address), null, true);
                    
                    if (address > MarklinLocomotive.MULTI_UNIT_MAX_ADDR)
                    {
                        address -= MarklinLocomotive.MULTI_UNIT_BASE;
                    }
                }
                // Others
                else
                {
                    if (m.get("typ").equals("mfx"))
                    {
                        type = MarklinLocomotive.decoderType.MFX;
                    }
                    else if (m.get("typ").equals("dcc"))
                    {
                        type = MarklinLocomotive.decoderType.DCC;
                        
                        // The loc with address 1 will have an empty address entry in the file, but others won't
                        // So, simply subtract the DCC base from the UID if we had to use the UID instead of the address above
                        if (address > MarklinLocomotive.DCC_MAX_ADDR)
                        {
                            address -= MarklinLocomotive.DCC_BASE;
                        }
                    }
                    else
                    {
                        type = MarklinLocomotive.decoderType.MM2;
                    }
                }
                                 
                MarklinLocomotive loc = new MarklinLocomotive(
                    control, 
                    address, 
                    type,
                    name,
                    extractFunctionTypes(parseLocomotiveFunctions(m.get("funktionen"))),
                    parseFunctionTriggerTypes(m.get("funktionen"))
                );
                
                if (m.get("icon") != null)
                {
                    loc.setImageURL(this.getImageURL(m.get("icon")));
                }
                
                if (!multiUnitLocMap.isEmpty())
                {
                    loc.setCentralStationMultiUnitLocomotives(multiUnitLocMap);
                }
                                
                out.add(loc);
            }
        }
        
        return out;
    }
    
    /**
     * Reads the list of layouts
     * @return
     * @throws Exception 
     */
    private List<String> parseLayoutList() throws Exception
    {
        List<Map<String, String> > l = parseFile(fetchURL(getLayoutMasterURL()));
        
        List<String> out = new ArrayList<>();
        
        for (Map<String, String> m : l)
        {
            if ("seite".equals(m.get("_type")))
            {
                out.add(m.get("name"));
            }
        }        
        
        return out;
    }
    
    /**
     * Converts input file to JSON
     * @param in
     * @return
     * @throws IOException 
     */
    public static JSONArray parseJSONArray (BufferedReader in) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null)
        {
            sb.append(line);
        }
                
        return new JSONArray(sb.toString());
    }
    
    /**
     * Converts input file to JSON
     * @param in
     * @return
     * @throws IOException 
     */
    public static JSONObject parseJSONObject (BufferedReader in) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null)
        {
            sb.append(line);
        }

        return new JSONObject(sb.toString());
    }
    
    /**
     * Converts a component name from the MCS2 file to an internal componentType
     * @param name
     * @param address - only used for CS3 switches w/o address
     * @return
     * @throws Exception 
     */
    private MarklinLayoutComponent.componentType getComponentType(String name, int address) throws Exception
    {
        switch(name)
        {
            case "entkuppler":
            case "entkuppler_1":
                return MarklinLayoutComponent.componentType.UNCOUPLER;
            case "prellbock":
                return MarklinLayoutComponent.componentType.END;                
            case "s88kontakt":
                return MarklinLayoutComponent.componentType.FEEDBACK;
            case "s88bogen":
                return MarklinLayoutComponent.componentType.FEEDBACK_CURVE;
            case "s88doppelbogen":
                return MarklinLayoutComponent.componentType.FEEDBACK_DOUBLE_CURVE;
            case "gerade":
                return MarklinLayoutComponent.componentType.STRAIGHT;
            case "signal":        // Standard signals
            case "signal_sh01":
            case "signal_hp02":   // TODO - add support for green/yellow signals
            case "signal_hp012":
            case "signal_hp012s":
            case "signal_p_hp012":
            case "signal_f_hp01": // Semaphore style signals
            case "signal_f_hp012":
            case "signal_f_hp02":
            case "signal_f_hp012s":
            case "std_rot_gruen_0":
            case "std_rot_gruen_1":
            case "std_rot":
            case "k84_einfach":    
            case "sonstige_gbs":
            case "standard":       
                return MarklinLayoutComponent.componentType.SIGNAL;
            case "doppelbogen":
                return MarklinLayoutComponent.componentType.DOUBLE_CURVE;
            case "bogen":
                return MarklinLayoutComponent.componentType.CURVE;
            case "tunnel":
                return MarklinLayoutComponent.componentType.TUNNEL;
            case "kreuzung":
                return MarklinLayoutComponent.componentType.CROSSING;
            case "unterfuehrung":
                return MarklinLayoutComponent.componentType.OVERPASS;
            case "dkweiche":
            case "dkweiche_2":
            case "andreaskreuz":// Special double slip ?
                return MarklinLayoutComponent.componentType.SWITCH_CROSSING;
            case "drehscheibe": // Turntable
                return MarklinLayoutComponent.componentType.TURNTABLE;
            case "lampe":       // Lamp
            case "lampe_rt":    // Red light
            case "lampe_bl":    // Blue light
            case "lampe_gn":    // Green light
            case "lampe_ge":    // Yellow light
            case "bahnschranke":// Railroad crossing - TODO add dedicated icon
                return MarklinLayoutComponent.componentType.LAMP;
            case "fahrstrasse": // Route
                return MarklinLayoutComponent.componentType.ROUTE;
            case "text":        // Standalone text
                return MarklinLayoutComponent.componentType.TEXT;
            case "pfeil":       // Link to another page
                return MarklinLayoutComponent.componentType.LINK;
            
            // If these have an address, make them switchable, otherwise default to a permanent crossing
            case "linksweiche":
            // CS3 double slip switch 2 is just two of these
            case "dkw3_li_2":
            case "dkw3_li":
                if (address > 0) return MarklinLayoutComponent.componentType.SWITCH_LEFT;
            case "custom_perm_left":
                return MarklinLayoutComponent.componentType.CUSTOM_PERM_LEFT;
            
            // If these have an address, make them switchable, otherwise default to a permanent crossing
            case "rechtsweiche":
            // CS3 double slip switch 2 is just two of these
            case "dkw3_re":
            case "dkw3_re_2":
                // CS3 double slip switch 2. If these have an address, make them switchable, otherwise default to a permanent crossing
                if (address > 0) return MarklinLayoutComponent.componentType.SWITCH_RIGHT;
            case "custom_perm_right":
                return MarklinLayoutComponent.componentType.CUSTOM_PERM_RIGHT;
            
            // Y switch.  If these have an address, make them switchable, otherwise default to a permanent crossing
            case "yweiche":
                if (address > 0) return MarklinLayoutComponent.componentType.SWITCH_Y;
            case "custom_perm_y":
                return MarklinLayoutComponent.componentType.CUSTOM_PERM_Y;
             
            case "dreiwegweiche":
                if (address > 0) return MarklinLayoutComponent.componentType.SWITCH_THREE;
            case "custom_perm_threeway":
                return MarklinLayoutComponent.componentType.CUSTOM_PERM_THREEWAY;
                
            case "hosentraeger":
            case "custom_scissors":
                if (address > 0) return MarklinLayoutComponent.componentType.CUSTOM_SCISSORS;
            case "custom_perm_scissors":
                return MarklinLayoutComponent.componentType.CUSTOM_PERM_SCISSORS;
                
            // Custom (non-CS2) components
            default:
                logMessage("Layout: warning - component " + name + 
                                 " is not supported and will not be displayed");
                return null;
        }
        
        //throw new Exception("Unsupported component: " + name);        
    }
    
    /**
     * Processes a layout
     * @return
     * @throws Exception 
     */
    public List<MarklinLayout> parseLayout() throws Exception
    {
        List<String> names = this.parseLayoutList();
        
        List<MarklinLayout> out = new ArrayList<>();
        
        for (String name : names)
        { 
            String url = getLayoutURL(name);
            
            if (control.isDebug())
            {
                control.log("Loading layout from: " + url);
            }
            
            List<Map<String, String> > l = parseFile(fetchURL(url));
            
            // Read accessory info for CS accessories
            List<MarklinAccessory> accDB = new ArrayList<>();
            
            if (this.getDefaultLayoutDataLoc().equals(this.layoutDataLoc) && !control.isCS3())
            {
                accDB = parseMags(parseFile(fetchURL(getMagURL())));
            }
            
            Map<Integer, MarklinAccessory> addressMap = accDB.stream()
                .collect(Collectors.toMap(
                        MarklinAccessory::getAddress, 
                        accessory -> accessory,
                        (existing, replacement) -> existing // uncouplers will have the same ID
                ));
            
            int maxX = 0;
            int maxY = 0;
            
            for (Map<String, String> m : l)
            {
                if ("element".equals(m.get("_type")))
                {
                    Integer coord = 0;

                    if (m.get("id") != null)
                    {
                        coord = Integer.valueOf(m.get("id").replace("0x", ""), 16);
                    }
                    else
                    {
                        logMessage("Layout: element has no coordinate info, assuming 0,0 (" + m + ")", null, true);
                    }
                    
                    Integer x = coord % 256;
                    Integer y = (coord >> 8) % 256;

                    if (x > maxX)
                    {
                        maxX = x;
                    }

                    if (y > maxY)
                    {
                        maxY = y;
                    }
                }
            }
            
            MarklinLayout layout = new MarklinLayout(name, maxX + 1, maxY + 1, url, this.control);
                        
            for (Map<String, String> m : l)
            {
                if ("element".equals(m.get("_type")))
                {
                    Integer coord = 0;
                    
                    if (m.get("id") != null)
                    {
                        coord = Integer.valueOf(m.get("id").replace("0x", ""), 16);
                    }

                    Integer x = coord % 256;
                    Integer y = (coord >> 8) % 256;
                    
                    Integer orient = 0;
                    Integer state = 0;
                    String type = m.get("typ");
                    
                    // Handle missing type
                    if (type == null)
                    {
                        if (m.get("text") != null)
                        {
                            type = "text";
                        }
                        else
                        {
                            type = "unknown";
                        }
                    }
                    
                    Integer rawAddress = 0;
                    
                    try
                    {
                        rawAddress = Integer.valueOf(m.get("artikel"));
                    }
                    catch (NumberFormatException e)
                    {
                        if (!"text".equals(type))
                        {
                            logMessage(String.format("Layout: component " + type + " at %s, %s has no address", x, y));
                        }
                    }
                    
                    Integer address = rawAddress;
                    
                    if (!"fahrstrasse".equals(type))
                    {
                        if (address % 2 == 0)
                        {
                            address = (address / 2);
                        }
                        else
                        {
                            address = (address - 1) / 2;
                        }
                    }
                                        
                    if (m.get("drehung") != null)
                    {
                        orient = Integer.valueOf(m.get("drehung")); 
                    }
                   
                    if (m.get("zustand") != null)
                    {
                        state = Integer.valueOf(m.get("zustand"));
                    }
                    
                    // Workaround for incorrectly oriented semaphore signals, which are rotated +90 degrees in the CS2 UI
                    if (type.contains("_f_"))
                    {
                        orient = Math.floorMod(orient - 1, 4);
                    }
                    
                    Accessory.accessoryDecoderType protocol = Accessory.accessoryDecoderType.MM2;
                    
                    // Read protocol from mags file - only works on CS2
                    if (addressMap.get(rawAddress) != null)
                    {
                        protocol = addressMap.get(rawAddress).getDecoderType();
                    }
                    
                    // Custom - read protocol from the local layout files
                    if (m.get("prot") != null)
                    {
                        if (MarklinAccessory.stringToAccessoryDecoderType(m.get("prot")) != null)
                        {
                            protocol = MarklinAccessory.stringToAccessoryDecoderType(m.get("prot"));
                        }
                        else
                        {
                            logMessage("Unknown protocol: " + m.get("prot"));
                        }
                    }
                    
                    // This will fail for unknown components.  Catch errors?
                    if (getComponentType(type, address) != null)
                    {
                        layout.addComponent(
                           getComponentType(type, address),
                           x, y, orient, state, address, rawAddress, protocol, m.get("text")
                        );
                    }
                }
            }
            
            layout.checkBounds();
            
            out.add(layout);
        }
        
        return out;
    }

    /**
    * Checks if a there is a connection
    * @param host
    * @return 
    */
    public static boolean ping(String host)
    {    
       try 
       {
           CS2File.fetchURL(CS2File.getPingIP(host));
       }
       catch (Exception e)
       {
           return false;
       }

       return true;
    }
}
