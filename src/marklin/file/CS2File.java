package marklin.file;

import automation.Edge;
import automation.Layout;
import base.Accessory;
import base.Locomotive;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import marklin.MarklinControlStation;
import marklin.MarklinLayout;
import marklin.MarklinLayoutComponent;
import marklin.MarklinLocomotive;
import marklin.MarklinRoute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    }
    
    /**
     * Sets the layout data location to the CS2 IP
     */
    public void setDefaultLayoutDataLoc()
    {
        this.layoutDataLoc = "http://" + this.IP;
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
            this.control.log("URL encoding error: " + ex.getMessage());
            
            if (this.control.isDebug())
            {
                ex.printStackTrace();
            }
            
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
    public String getLayoutMasterURL(String dataPath)
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
    private static BufferedReader fetchURL(String url) throws Exception 
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
        return parseRoutes(parseFile(fetchURL(getRouteURL())));
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
     * Reads a route database
     * @param l parsed data
     * @return list of routes
     * @throws Exception 
     */
    public List<MarklinRoute> parseRoutes(List<Map<String, String> > l) throws Exception
    {        
        List<MarklinRoute> out = new ArrayList<>();
        
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
                    // As this would duplcate functionality with the CS2, we leave it disabled
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
                        if (setting >= 2)
                        {
                            r.addAccessory(id + 1, setting == 2);
                            
                            if (delay > 0)
                            {
                                r.setDelay(id + 1, delay);
                            }
                        }
                        
                        r.addAccessory(id, setting != 1 && setting != 3);
                        
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
     * Parses locomotives from the CS3 API
     * @param locomotiveList
     * @return 
     */
    public List<MarklinLocomotive> parseLocomotivesCS3(JSONArray locomotiveList)
    {
        List<MarklinLocomotive> out = new ArrayList<>();
        
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


                // Multi-units
                if (loc.has("traktion"))
                {
                    decoderType = MarklinLocomotive.decoderType.MULTI_UNIT;

                    if (this.control != null && this.control.isDebug())
                    {
                        this.control.log("Locomotive " + name + " is a multi-unit, using UID of " + uid);
                    }
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

                out.add(newLoc);
            }
            catch (NumberFormatException | JSONException e)
            {
                if (this.control != null)
                {
                    this.control.log("Failed to add locomotive at index " + i + " due to parsing error.");
                    this.control.log(e.toString());
                }
                else
                {
                    System.out.println("Failed to add locomotive at index " + i + " due to parsing error.");
                    e.printStackTrace();
                }
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

                    this.control.log("Locomotive " + name + " has no address field in config file, using UID of " + Integer.toString(address));
                }
                else
                {
                    this.control.log("Locomotive " + name + " has no address or UID field in config file. Skipping.  Raw data: " + m.toString());
                    continue;
                }
                                
                MarklinLocomotive.decoderType type;
                
                // Multi-units
                if (m.get("traktion") != null)
                {
                    type = MarklinLocomotive.decoderType.MULTI_UNIT;
                    
                    address = Integer.decode(m.get("uid"));
                    
                    this.control.log("Locomotive " + name + " is a multi-unit, using UID of " + Integer.toString(address));
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
     * @return
     * @throws Exception 
     */
    private MarklinLayoutComponent.componentType getComponentType(String name) throws Exception
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
            case "linksweiche":
                return MarklinLayoutComponent.componentType.SWITCH_LEFT;
            case "rechtsweiche":
                return MarklinLayoutComponent.componentType.SWITCH_RIGHT;
            case "yweiche":
                return MarklinLayoutComponent.componentType.SWITCH_Y;
            case "dreiwegweiche":
                return MarklinLayoutComponent.componentType.SWITCH_THREE;
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
            // Custom (non-CS2) components
            case "custom_perm_left":
                return MarklinLayoutComponent.componentType.CUSTOM_PERM_LEFT;
            case "custom_perm_right":
                return MarklinLayoutComponent.componentType.CUSTOM_PERM_RIGHT;
            case "custom_perm_y":
                return MarklinLayoutComponent.componentType.CUSTOM_PERM_Y;
            default:
                this.control.log("Layout: warning - component " + name + 
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
            List<Map<String, String> > l = parseFile(fetchURL(url));
            
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
                        if (this.control.isDebug())
                        {
                            this.control.log("Layout: element has no coordinate info, assuming 0,0 (" + m + ")");
                        }
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
                        this.control.log(String.format("Layout: component " + type + " at %s, %s has no address", x, y));
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
                    
                    // This will fail for unknown components.  Catch errors?
                    if (getComponentType(type) != null)
                    {
                        layout.addComponent(
                           getComponentType(type),
                           x, y, orient, state, address, rawAddress
                        );  
                        
                        // Add text, if any
                        if (m.get("text") != null)
                        {
                            layout.getComponent(x, y).setLabel(m.get("text"));
                        }
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
     
    /**
     * Parses TrainControl's autonomous operation configuration file
     * @param config 
     * @return  
     */
    public Layout parseAutonomyConfig(String config)
    {           
        Layout layout = new Layout(control);
        JSONObject o;
        
        try
        {
            o = new JSONObject(config);
        }
        catch (JSONException e)
        {
            control.log("Auto layout error: JSON parsing error");
            layout.invalidate();
            return layout;
        }
               
        List<String> locomotives = new LinkedList<>();

        JSONArray points;
        JSONArray edges;
        Integer minDelay;
        Integer maxDelay;
        Integer defaultLocSpeed;
        
        // Validate basic required data
        try
        {
            points = o.getJSONArray("points");
            edges = o.getJSONArray("edges");
            minDelay  = o.getInt("minDelay");
            maxDelay  = o.getInt("maxDelay");
            defaultLocSpeed  = o.getInt("defaultLocSpeed");
        }
        catch (JSONException e)
        {
            control.log("Auto layout error: missing or invalid keys (points, edges, minDelay, maxDelay, defaultLocSpeed)");
            layout.invalidate();
            return layout;
        }
        
        if (points == null || edges == null)
        {
            control.log("Auto layout error: missing keys (points, edges)");
            layout.invalidate();
            return layout;        
        }
                
        // Save values in layout class
        try
        {
            layout.setDefaultLocSpeed(defaultLocSpeed);
            layout.setMaxDelay(maxDelay);
            layout.setMinDelay(minDelay);
            layout.setTurnOffFunctionsOnArrival(o.has("turnOffFunctionsOnArrival") && o.getBoolean("turnOffFunctionsOnArrival"));
            
            if (!o.has("turnOnFunctionsOnDeparture"))
            {
                layout.setTurnOnFunctionsOnDeparture(true);
            }
            else
            {
                layout.setTurnOnFunctionsOnDeparture(o.getBoolean("turnOnFunctionsOnDeparture"));
            }            
        }
        catch (Exception e)
        {
            control.log("Auto layout error: " + e.getMessage());
            layout.invalidate();
            return layout;   
        }
        
        // Optional values
        if (o.has("preArrivalSpeedReduction"))
        {
            try
            {
                layout.setPreArrivalSpeedReduction(o.getDouble("preArrivalSpeedReduction"));
            }
            catch (Exception e)
            {
                control.log("Auto layout error: invalid value for preArrivalSpeedReduction (must be 0-1)");
                layout.invalidate();
                return layout;
            }    
        }
        
        if (o.has("maxLocInactiveSeconds"))
        {
            try
            {
                layout.setMaxLocInactiveSeconds(o.getInt("maxLocInactiveSeconds"));
                
                if (o.getInt("maxLocInactiveSeconds") > 0)
                {
                     control.log("Auto layout: trains will yield to other trains inactive longer than " + o.getInt("maxLocInactiveSeconds") + " seconds");
                }
            }
            catch (Exception e)
            {
                control.log("Auto layout error: invalid value for maxLocInactiveSeconds (must not be negative)");
                layout.invalidate();
                return layout;
            }    
        }
        
        // Debug/dev only setting
        try
        {
            layout.setSimulate(false);
            
            if (o.has("simulate"))
            {
                layout.setSimulate(o.getBoolean("simulate"));
            }
        }
        catch (Exception e)
        {
            control.log("Auto layout simulation warning: " + e.getMessage());
        }
        
        if (o.has("atomicRoutes"))
        {
            try
            {
                layout.setAtomicRoutes(o.getBoolean("atomicRoutes"));
                
                if (!o.getBoolean("atomicRoutes"))
                {
                    control.log("Auto layout notice: disabled atomic routes.  Edges will be unlocked as trains pass them instead of at the end of the route.");
                }

            }
            catch (JSONException e)
            {
                control.log("Auto layout error: invalid value for atomicRoutes (must be true or false)");
                layout.invalidate();
                return layout;
            }    
        }
           
        // Add points
        points.forEach(pnt -> { 
            JSONObject point = (JSONObject) pnt; 

            String s88 = null;
            if (point.has("s88"))
            {
                if (point.get("s88") instanceof Integer)
                {
                    s88 = Integer.toString(point.getInt("s88"));
                    
                    if (!control.isFeedbackSet(s88))
                    {
                        control.log("Auto layout warning: feedback " + s88 + " does not exist in CS2 layout");
                        control.newFeedback(point.getInt("s88"), null);
                    }
                }
                else if (!point.isNull("s88"))
                {
                    control.log("Auto layout error: s88 not a valid integer " + point.toString());
                    layout.invalidate();
                }
            }
            
            // Read optional coordinates
            Integer x = null, y = null;
            if (point.has("x"))
            {
                if (point.get("x") instanceof Integer)
                {
                    x = point.getInt("x");
                }
                else
                {
                    control.log("Auto layout error: x not a valid integer " + point.toString());
                    layout.invalidate();
                }
            }
            
            if (point.has("y"))
            {
                if (point.get("y") instanceof Integer)
                {
                    y = point.getInt("y");
                }
                else
                {
                    control.log("Auto layout error: y not a valid integer " + point.toString());
                    layout.invalidate();
                }
            }
            
            try 
            {
                layout.createPoint(point.getString("name"), point.getBoolean("station"), s88);
                     
                if (point.has("maxTrainLength"))
                {
                    if (point.get("maxTrainLength") instanceof Integer && point.getInt("maxTrainLength") >= 0)
                    { 
                        layout.getPoint(point.getString("name")).setMaxTrainLength(point.getInt("maxTrainLength"));

                        if (point.getInt("maxTrainLength") > 0)
                        {
                            control.log("Set max train length of " + point.getInt("maxTrainLength") + " for " + point.getString("name"));
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: " + point.getString("name") + " has invalid maxTrainLength value");
                        layout.invalidate();  
                    }
                }
                else
                {
                    layout.getPoint(point.getString("name")).setMaxTrainLength(0);
                }   
     
                // Set optional coordinates
                if (x != null && y != null)
                {
                    layout.getPoint(point.getString("name")).setX(x);
                    layout.getPoint(point.getString("name")).setY(y);
                }
                
                if (point.has("terminus"))
                {
                    if (point.get("terminus") instanceof Boolean)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setTerminus(point.getBoolean("terminus"));
                        } 
                        catch (Exception e)
                        {
                            control.log("Auto layout error: " + point.toString() + " " + e.getMessage());
                            layout.invalidate();  
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: invalid value for terminus " + point.toString());
                        layout.invalidate();
                    }
                }  
                
                if (point.has("active"))
                {
                    if (point.get("active") instanceof Boolean)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setActive(point.getBoolean("active"));
                        } 
                        catch (Exception e)
                        {
                            control.log("Auto layout error: " + point.toString() + " " + e.getMessage());
                            layout.invalidate();  
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: invalid value for active " + point.toString());
                        layout.invalidate();
                    }
                }   
                
                if (point.has("reversing"))
                {
                    if (point.get("reversing") instanceof Boolean)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setReversing(point.getBoolean("reversing"));
                        } 
                        catch (Exception e)
                        {
                            control.log("Auto layout error: " + point.toString() + " " + e.getMessage());
                            layout.invalidate();  
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: invalid value for reversing " + point.toString());
                        layout.invalidate();
                    }
                }    
                
                if (point.has("priority"))
                {
                    if (point.get("priority") instanceof Integer)
                    {
                        try
                        {
                            layout.getPoint(point.getString("name")).setPriority(point.getInt("priority"));
                        } 
                        catch (Exception e)
                        {
                            control.log("Auto layout error: " + point.toString() + " " + e.getMessage());
                            layout.invalidate();  
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: invalid value for priority " + point.toString());
                        layout.invalidate();
                    }
                } 
            } 
            catch (Exception ex)
            {
                if (control.isDebug())
                {
                    ex.printStackTrace();
                }
                
                control.log("Auto layout error: Point error for " + point.toString() + " (" + ex.getMessage() + ")");
                layout.invalidate();
                return;
            }

            // Set the locomotive
            if (point.has("loc") && !point.isNull("loc"))
            {
                if (point.get("loc") instanceof JSONObject)
                {
                    JSONObject locInfo = point.getJSONObject("loc");
                    
                    if (locInfo.has("name") && locInfo.get("name") instanceof String)
                    {
                        String loc = locInfo.getString("name");

                        if (control.getLocByName(loc) != null)
                        {
                            Locomotive l = control.getLocByName(loc);

                            if (locInfo.has("trainLength"))
                            {
                                if (locInfo.get("trainLength") instanceof Integer && locInfo.getInt("trainLength") >= 0)
                                {
                                    l.setTrainLength(locInfo.getInt("trainLength"));   

                                    control.log("Set train length of " + locInfo.getInt("trainLength") + " for " + loc);
                                }
                                else
                                {
                                    control.log("Auto layout error: " + loc + " has invalid trainLength value");
                                    layout.invalidate();  
                                }
                            }
                            else
                            {
                                l.setTrainLength(0);   
                            }

                            if (locInfo.has("reversible"))
                            {
                                if (locInfo.get("reversible") instanceof Boolean)
                                {
                                    l.setReversible(locInfo.getBoolean("reversible"));   

                                    if (locInfo.getBoolean("reversible"))
                                    {
                                        control.log("Flagged as reversible: " + loc);
                                    }
                                }
                                else
                                {
                                    control.log("Auto layout error: " + loc + " has invalid reversible value");
                                    layout.invalidate();  
                                }
                            }
                            else
                            {
                                l.setReversible(false);   
                            }

                            // Only throw a warning if this is not a station
                            if (point.getBoolean("station") != true)
                            {
                                control.log("Auto layout warning: " + loc + " placed on a non-station at will not be run automatically");
                            }

                            // Place the locomotive
                            layout.getPoint(point.getString("name")).setLocomotive(l);

                            // Reset if none present
                            l.setDepartureFunc(null);

                            // Set start and end callbacks
                            if (locInfo.has("speed") && locInfo.get("speed") != null)
                            {
                                try
                                {
                                    if (locInfo.getInt("speed") > 0 && locInfo.getInt("speed") <= 100)
                                    {
                                        l.setPreferredSpeed(locInfo.getInt("speed"));
                                    }
                                }
                                catch (JSONException ex)
                                {
                                    control.log("Auto layout error: Error in speed value for " + locInfo.getString("name"));
                                    layout.invalidate();
                                }
                            }

                            // Set start and end callbacks
                            if (locInfo.has("departureFunc") && locInfo.get("departureFunc") != null)
                            {
                                try
                                {
                                    l.setDepartureFunc(locInfo.getInt("departureFunc"));
                                }
                                catch (JSONException ex)
                                {
                                    control.log("Auto layout error: Error in departureFunc value for " + locInfo.getString("name"));
                                    layout.invalidate();
                                }
                            }

                            // Fires functions on departure and arrival
                            layout.applyDefaultLocCallbacks(l);

                            // Reset if none present
                            l.setArrivalFunc(null);

                            if (locInfo.has("arrivalFunc") && locInfo.get("arrivalFunc") != null)
                            {
                                try
                                {
                                    l.setArrivalFunc(locInfo.getInt("arrivalFunc"));
                                }
                                catch (JSONException ex)
                                {
                                    control.log("Auto layout error: Error in arrivalFunc value for " + locInfo.getString("name"));
                                    layout.invalidate();
                                }
                            }

                            if (l.getPreferredSpeed() == 0)
                            {
                                l.setPreferredSpeed(defaultLocSpeed);
                                control.log("Auto layout warning: Locomotive " + loc + " had no preferred speed.  Setting to default of " + defaultLocSpeed);
                            }

                            if (locomotives.contains(loc))
                            {
                                control.log("Auto layout error: duplicate locomotive " + loc + " at " + point.getString("name"));
                                layout.invalidate();
                            }
                            else
                            {
                                locomotives.add(loc);
                            }
                        }
                        else
                        {
                            control.log("Auto layout error: Locomotive " + loc + " does not exist");
                            layout.invalidate();
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: Locomotive configuration array at " + point.getString("name") + " missing name");
                        layout.invalidate();
                    }
                }
                else
                {
                    this.control.log("Auto layout warning: ignoring invalid value for loc \"" + point.get("loc").toString() + "\" (must be a JSON object)");
                } 
            }
        });

        // Add edges
        edges.forEach(edg -> 
        { 
            JSONObject edge = (JSONObject) edg; 
            try 
            {
                String start = edge.getString("start");
                String end = edge.getString("end");

                if (edge.has("commands") && !edge.isNull("commands"))
                {
                    JSONArray commands = edge.getJSONArray("commands");

                    // Validate commands
                    commands.forEach((cmd) -> {
                        JSONObject command = (JSONObject) cmd;
                        
                        // Validate accessory
                        if (command.has("acc") && !command.isNull("acc"))
                        {
                            String accessory = command.getString("acc");
                            if (null == control.getAccessoryByName(accessory))
                            {
                                // TODO - use Edge.validateConfigCommand
                                control.log("Auto layout warning: accessory \"" + accessory + "\" does not exist in CS2 layout");
                                
                                if (accessory.contains("Signal "))
                                {
                                    Integer address = Integer.valueOf(accessory.replace("Signal ", ""));
                                    
                                    if (control.getAccessoryByName("Switch " + address) != null)
                                    {
                                        control.log("Auto layout warning: " + accessory + " conflicts with switch with the same address.");
                                        //layout.invalidate();
                                    }
                                    
                                    control.newSignal(address.toString(), address, false);
                                    control.log("Auto layout warning: created " + accessory);
                                }
                                else if (accessory.contains("Switch "))
                                {   
                                    Integer address = Integer.valueOf(accessory.replace("Switch ", ""));

                                    if (control.getAccessoryByName("Signal " + address) != null)
                                    {
                                        control.log("Auto layout warning: " + accessory + " conflicts with signal with the same address.");
                                        //layout.invalidate();
                                    }
                                    
                                    control.newSwitch(address.toString(), address, false);
                                    control.log("Auto layout warning: created " + accessory);                       
                                }
                                else
                                {
                                    control.log("Auto layout error: unrecognized accessory type");
                                    layout.invalidate();
                                }
                            }
                        }
                        else
                        {
                            control.log("Auto layout error: Edge command missing accessory definition in " + start + "-" + end + " action: " + command.toString());
                            layout.invalidate(); 
                        }
                        
                        // Validate state
                        if (command.has("state") && !command.isNull("state"))
                        {            
                            String action = command.getString("state");

                            if (null == Accessory.stringToAccessorySetting(action))
                            {
                                control.log("Auto layout error: Invalid action in edge " + start + "->" + end + " (" + command.toString() + ")");
                                layout.invalidate();
                            }
                        }
                        else
                        {
                            control.log("Auto layout error: Edge command missing state " + start + "->" + end + " action: " + command.toString());
                            layout.invalidate();
                        }
                    });
                }
                
                Edge e = layout.createEdge(start, end); 
                
                // Store the raw config commands so that we can reference them later
                if (edge.has("commands") && !edge.isNull("commands"))
                {
                    JSONArray commands = edge.getJSONArray("commands");
                    commands.forEach((cmd) -> 
                    {
                        JSONObject command = (JSONObject) cmd;
                        String action = command.getString("state");
                        String acc = command.getString("acc");

                        e.addConfigCommand(acc, Accessory.stringToAccessorySetting(action));
                    });                    
                }            
                
                if (edge.has("length"))
                {
                    if (edge.get("length") instanceof Integer && edge.getInt("length") >= 0)
                    {
                        e.setLength(edge.getInt("length"));   

                        if (edge.getInt("length") > 0)
                        {
                            if (control.isDebug())
                            {
                                control.log("Set edge length of " + edge.getInt("length") + " for " + e.getName());
                            }
                        }
                    }
                    else
                    {
                        control.log("Auto layout error: " + e.getName() + " has invalid length value");
                        layout.invalidate();  
                    }
                }
                else
                {
                    e.setLength(0);
                }
            } 
            catch (Exception ex)
            {
                control.log("Auto layout error: Invalid edge " + edge.toString() + " (" + ex.getMessage() + ")");
                layout.invalidate();
            }
        });

        // Add lock edges
        edges.forEach(edg -> 
        { 
            JSONObject edge = (JSONObject) edg; 
            try 
            { 
                String start = edge.getString("start");
                String end = edge.getString("end");  
                
                if (layout.getEdge(start, end) != null && edge.has("lockedges"))
                {
                    edge.getJSONArray("lockedges").forEach(lckedg -> {
                        JSONObject lockEdge = (JSONObject) lckedg;

                        if (layout.getEdge(lockEdge.getString("start"), lockEdge.getString("end")) == null)
                        {
                            control.log("Auto layout error: Lock edge" + lockEdge.toString() + " does not exist");  
                            layout.invalidate();
                        }
                        else
                        {
                            layout.getEdge(start, end).addLockEdge(
                                layout.getEdge(lockEdge.getString("start"), lockEdge.getString("end"))
                            );
                        }
                    });
                }
            } 
            catch (JSONException ex)
            {
                control.log("Auto layout error: Lock edge error - " + edge.toString());
                layout.invalidate();
            }
        });
        
        // Set list of reversible locomotives
        /*reversible.forEach(locc -> { 
            String loc = (String) locc;
                            
            if (control.getLocByName(loc) != null)
            {
                layout.addReversibleLoc(control.getLocByName(loc));
                control.log("Flagged as reversible: " + loc);
            }
            else
            {
                control.log("Auto layout error: Reversible locomotive " + loc + " does not exist");
                layout.invalidate();
            }
        });*/

        /*if (locomotives.isEmpty())
        {
            control.log("Auto layout error: No locomotives placed.");
            layout.invalidate();
        }*/
        
        List<Locomotive> locsToRun = new LinkedList<>();
        
        for (String s : locomotives)
        {
            locsToRun.add(control.getLocByName(s));
        }
        
        layout.setLocomotivesToRun(locsToRun);
                    
        return layout;
    }
}
