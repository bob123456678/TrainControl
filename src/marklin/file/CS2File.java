package marklin.file;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import marklin.MarklinControlStation;
import marklin.MarklinLayout;
import marklin.MarklinLayoutComponent;
import marklin.MarklinLocomotive;
import marklin.MarklinRoute;

/**
 * Marklin CS config file parsing class
 * @author Adam
 */
public class CS2File
{
    // IP address for our HTTP requests
    private String IP;
    
    // Control station
    MarklinControlStation control;
        
    /**
     * Constructor
     * @param IP 
     * @param control 
     */
    public CS2File(String IP, MarklinControlStation control)
    {
        this.IP = IP;
        this.control = control;
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
        return URL.replace(" ", "%20");
    }
    
    /**
     * Gets an image URL
     * @param image
     * @return 
     */
    public String getImageURL(String image)
    {
        return this.sanitizeURL("http://" + this.IP + "/icons/" + 
                image + ".png");
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
        return "http://" + this.IP + "/config/gleisbild.cs2";
    }
    
    /**
     * Layout file
     * @param layoutName
     * @return 
     */
    public String getLayoutURL(String layoutName)
    {
        return "http://" + this.IP + "/config/gleisbilder/" 
                + sanitizeURL(layoutName) + ".cs2";
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
                connection.getInputStream()));
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
            
            if (s.matches("^ \\.\\.[a-z]+=.+$"))
            {
                String[] parts = s.substring(3).split("=");

                array.put(parts[0], parts[1]);
            }
            else
            {
                if (array.size() > 0 && item != null)
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
                else if (s.matches("^ \\.[a-z]+=.+$"))
                {
                    String[] parts = s.substring(2).split("=");
                    
                    item.put(parts[0], parts[1]);
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

                for (String piece : pieces)
                {
                    String[] infos = piece.split(",");

                    Integer id = 0;
                    Integer setting = 0;

                    for (String info : infos)
                    {
                        if (info.contains("="))
                        {
                            String[] kv = info.split("=");
                            
                            if ("magnetartikel".equals(kv[0]))
                            {
                                id = new Integer(kv[1]);
                            }
                            
                            if ("stellung".equals(kv[0]))
                            {
                                setting = new Integer(kv[1]);
                            }
                        }
                    }
                    
                    // Handle 3-way switches
                    if (id > 0)
                    {
                        if (setting == 2)
                        {
                            r.addItem(id + 1, true);
                        }
                        
                        r.addItem(id, setting != 1);
                    }
                }
                                
                if (r.getRoute().size() > 0)
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
                
                if ("typ".equals(item[0]))
                {
                    type = Integer.parseInt(item[1]);
                }
            }
            
            output[fn] = type;
        }
        
        return output;
    }
        
    /**
     * Reads a locomotive database
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
                
                boolean isMFX = m.get("typ").equals("mfx");
                
                int[] functionTypes = parseLocomotiveFunctions(m.get("funktionen"));
                
                MarklinLocomotive loc = new MarklinLocomotive(
                    control, 
                    address, 
                    isMFX ? MarklinLocomotive.decoderType.MFX 
                        : MarklinLocomotive.decoderType.MM2,
                    name,
                    functionTypes
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
            case "text":
                return MarklinLayoutComponent.componentType.TEXT;
            // Unsupported components
            case "pfeil":       // Link to another page
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
            List<Map<String, String> > l = parseFile(fetchURL(getLayoutURL(name)));
            
            int maxX = 0;
            int maxY = 0;
            
            for (Map<String, String> m : l)
            {
                if ("element".equals(m.get("_type")))
                {
                    Integer coord = 0;

                    if (m.get("id") != null)
                    {
                        coord = Integer.parseInt(m.get("id").replace("0x", ""), 16);
                    }
                    else
                    {
                        this.control.log("Layout: element has no coordinate info, assuming 0,0 (" + m + ")");
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
            
            MarklinLayout layout = new MarklinLayout(name, maxX + 1, maxY + 1, this.control);
                        
            for (Map<String, String> m : l)
            {
                if ("element".equals(m.get("_type")))
                {
                    Integer coord = 0;
                    
                    if (m.get("id") != null)
                    {
                        coord = Integer.parseInt(m.get("id").replace("0x", ""), 16);
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
                        rawAddress = Integer.parseInt(m.get("artikel"));
                    }
                    catch (NumberFormatException e)
                    {
                        this.control.log(String.format("Layout: component " + type + " at %s, %s has no address", x, y));
                    }
                    
                    Integer address = rawAddress;
                    
                    if (address % 2 == 0)
                    {
                        address = (address / 2);
                    }
                    else
                    {
                        address = (address - 1) / 2;
                    }
                    
                    if (m.get("drehung") != null)
                    {
                        orient = Integer.parseInt(m.get("drehung")); 
                    }
                   
                    if (m.get("zustand") != null)
                    {
                        state = Integer.parseInt(m.get("zustand"));
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
            
    /*public static void main(String[] args) throws Exception
    {
        CS2File f = new CS2File("192.168.1.25", null);
        //System.out.println(f.parseLayout());
        //f.parseRoutes();
        
        //System.out.println (f.parseFile(f.fetchURL(f.getRouteURL())));
        //System.out.println (f.parseLocomotives());
    }*/
}
