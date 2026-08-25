package org.traincontrol.marklin.file;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.traincontrol.base.Locomotive;
import org.traincontrol.marklin.MarklinControlStation;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.marklin.MarklinLocomotive;
import org.traincontrol.marklin.MarklinRoute;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.traincontrol.base.Accessory;
import org.traincontrol.base.RouteCommand;
import org.traincontrol.marklin.MarklinAccessory;
import org.traincontrol.util.I18n;
import org.traincontrol.util.Util;

/**
 * Marklin Central Station 2/3 config file parser
 * @author Adam
 */
public final class CS2File
{
    // Timeouts for our HTTP requests.  Connecting is quick because the Central Station is on the local
    // network, but reads are given time to transfer potentially large database files.
    public static final int CONNECT_TIMEOUT_MS = 2000;
    public static final int READ_TIMEOUT_MS = 15000;

    // IP address for our HTTP requests
    private final String IP;
    
    // Control station
    MarklinControlStation control;
    
    // Store the URL to the CS2 layout config by default
    // Can be overriden by files on the local filesystem (if using a CS3, etc)
    private String layoutDataLoc;
    
    // Cache CS3 mags
    private final Map<Integer, JSONObject> magList;
    private final Map<String, JSONObject> locList;
        
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
        this.locList = new HashMap<>();
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
            
            this.logMessage(I18n.f(
                "error.urlEncoding",
                ex.getMessage()
            ));
 
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
     * @param local - do we prefer to fetch the local file, i.e. if the layoutDataLoc is local?
     *                We need this because it's possible to read routes from CS2 and layouts locally.
     * @return 
     */
    public String getMagURL(boolean local)
    {
        if (local)
        {
            return this.layoutDataLoc + "/config/magnetartikel.cs2";
        }
        else
        {
            return "http://" + this.IP + "/config/magnetartikel.cs2";
        }
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
                + (dataPath.contains("http://") ? sanitizeURL(layoutName) : sanitizeFilename(layoutName))
                + ".cs2";
    }

    /**
     * Makes a page name safe to use as a local filename.
     *
     * Page names come out of the Central Station index and are free text: a name carrying a path
     * separator or a character the filesystem forbids used to be joined straight onto the layouts
     * folder.  On download that made the write land outside the folder - or fail outright, part way
     * through, leaving a half-written layout the next sync reads as authoritative.
     *
     * Applied on BOTH sides on purpose.  The local read locates a page by the name in the index, so
     * sanitizing only the write would produce a file the reader then could not find.  The remote read
     * keeps sanitizeURL, which answers a different question - what is legal in a url - and is why the
     * download half of this was the only one ever guarded.
     *
     * Only characters that are actually unusable are replaced, so ordinary names with spaces, dashes
     * and accented letters are returned untouched and existing local layouts load exactly as before.
     *
     * @param name
     * @return
     */
    public static String sanitizeFilename(String name)
    {
        // One implementation, in Util, because LayoutDiagram is a writer too and cannot reach in here
        return org.traincontrol.util.Util.sanitizeFilename(name);
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
     * Pre v2.6.0 CS3 firmware handled differently
     * @param version
     * @return 
     */
    public String getCS3LocDBUrl(int version)
    {
        if (version < 260)
        {
            return "http://" + this.IP + "/app/api/loks";
        }
        else
        {
            return "http://" + this.IP + "/app/api/locos";
        }
    }
    
    /**
     * CS3 Loc DB URL - auto resolves
     * @return
     * @throws Exception 
     */
    public String getCS3LocDBUrl() throws Exception
    {
        boolean is260 = this.isCS3Version260OrAbove();
        
        return getCS3LocDBUrl(is260 ? 260 : 250);
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
        // try-with-resources: the early return below used to leak the reader, and its HTTP connection
        try (BufferedReader content = fetchURL(deviceInfoUrl))
        {
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

        // Without these, an unreachable Central Station blocks the caller until the OS abandons the connection.
        // Ignored for local files, which are read through this method when a layout override path is set.
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);

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
        // try-with-resources, matching parseJSONArray and parseJSONObject alongside.  The close used to
        // be the last statement of the body, so any parse failure left the reader - and with it an HTTP
        // connection, or a file handle when a local layout folder is configured - open until the
        // garbage collector got to it.
        try (BufferedReader reader = in)
        {
            return parseFileContents(reader);
        }
    }

    /**
     * The body of parseFile.  Call parseFile, which closes the reader whatever happens.
     * @param in
     * @return
     * @throws Exception 
     */
    private static List<Map<String, String> > parseFileContents(BufferedReader in) throws Exception
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
                // Limit of 2, for the same reason as the ordinary key=value branch below: the one array
                // key that carries free text is lokname, the name of a multi-unit member, which
                // parseLocomotives matches against the locomotive database to assemble the consist.
                // Split without a limit, a member named "BR 50 = Ep.III" was stored as "BR 50 ", matched
                // nothing, and was dropped from its consist with only a log line to say so.
                String[] parts = s.substring(3).split("=", 2);

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
                    
                    // Joined by hand rather than through HashMap.toString().
                    //
                    // toString() separates entries with ", ", and the repair that turned that back into
                    // "," could not tell the separator from a ", " INSIDE a value.  Exactly one array
                    // key carries free text - lokname, the name of a multi-unit member - so a member
                    // called "BR 50, Ep. III" was stored as "BR 50,Ep. III", matched no locomotive in
                    // the database, and was dropped from its consist with only a log line.  Commanding
                    // the head then moved one engine of two.
                    //
                    // This is the twin of the defect the split limit above was added for, in the same
                    // block: that one was a name containing "=", this one a name containing ", ".
                    //
                    // The map is still walked in ITS order, not insertion order, because parseLocomotives
                    // recovers the members by splitting on ",lok=" - which needs lokname to come first,
                    // and it does only because that is how these two keys hash.  Building the string
                    // from the same iteration keeps that exactly as it was.
                    StringBuilder entries = new StringBuilder();

                    for (Map.Entry<String, String> pair : array.entrySet())
                    {
                        if (entries.length() > 0) entries.append(',');

                        entries.append(pair.getKey()).append('=').append(pair.getValue());
                    }

                    String arrayString = current + "{" + entries + "}";
                    
                    // A dirty but effective workaround
                    arrayString = arrayString.replace("}{", "|");

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
                    // Limit of 2, so that a route or locomotive name containing an equals sign is not truncated
                    String[] parts = s.substring(2).split("=", 2);

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
        return items;
    }
    
    public List<MarklinRoute> parseRoutes() throws Exception
    {
        return parseRoutes(parseFile(fetchURL(getRouteURL())), 
            getMagList(false)
        );
    }
    
    public List<MarklinLocomotive> parseLocomotives() throws Exception
    {
        return parseLocomotives(parseFile(fetchURL(getLocURL())));
    }
    
    /**
     * Helper to detect CS3 404 errors
     * @param url
     * @return 
     */
    public boolean isNotFoundError(String url)
    {
        try (BufferedReader in = fetchURL(url))
        {
            Object json = new JSONTokener(in).nextValue();

            // If it's an object, check for the error field
            if (json instanceof JSONObject)
            {
                JSONObject obj = (JSONObject) json;
                return "Not Found".equalsIgnoreCase(obj.optString("error", null));
            }

            // Arrays are always valid (never an error object)
            return false;

        }
        catch (FileNotFoundException e)
        {
            return true;
        }
        // This should never happen
        catch (Exception e)
        {
            logMessage("Unexpected error when checking url " + url + " (possible CS3 compatibility issue): " + e.getMessage(), e, false);
            return false;
        }
    }

    /**
     * Helper function that fetches a reader of the CS3 loc db with a check for newer versions v2.6.0+ that use a different endpoint
     * @return
     * @throws Exception 
     */
    private BufferedReader fetchCS3LocDB() throws Exception
    {
        return fetchURL(getCS3LocDBUrl());
    }

    /**
     * Gets the list of locomotives from the CS3
     * @return
     * @throws Exception 
     */
    public List<MarklinLocomotive> parseLocomotivesCS3() throws Exception
    {
        return parseLocomotivesCS3(parseJSONArray(fetchCS3LocDB()));
    }
    
    /**
     * Returns whether the CS3 is at or above firmware v2.6.0
     * @return
     * @throws Exception 
     */
    public boolean isCS3Version260OrAbove() throws Exception
    {
        return !isNotFoundError(getCS3LocDBUrl(260));
    }

    /**
     * Fetches route information from the CS3
     * @return
     * @throws Exception
     */
    public List<MarklinRoute> parseRoutesCS3() throws Exception
    {
        // Determine which locomotive DB version is active (v260+ or older 250).  Probed once, up front,
        // and the version passed explicitly below: the no-argument getCS3LocDBUrl() probes again, so this
        // used to ask the Central Station twice per import.  Doing it before anything is opened also
        // means a failed probe cannot strand a reader.
        boolean is260 = isCS3Version260OrAbove();

        // All three in one try-with-resources.  Each holds an HTTP connection to the Central Station, and
        // an exception after the first was opened - a refused fetch, or a malformed response from any of
        // them - used to leak every reader opened up to that point.  parseJSONArray/parseJSONObject close
        // them too, which is harmless: BufferedReader.close is a no-op once already closed.
        try (BufferedReader routeBR = fetchURL(getCS3RouteDBUrl());
             BufferedReader magBR   = fetchURL(getCS3MagDBUrl());
             BufferedReader locBR   = fetchURL(getCS3LocDBUrl(is260 ? 260 : 250)))
        {
            // Now parse routes based on the loc DB version
            if (is260)
            {
                // New firmware (260+): route DB is an array
                return parseRoutesCS3(
                    parseJSONArray(routeBR),
                    parseJSONArray(magBR),
                    parseJSONArray(locBR)
                );
            }
            else
            {
                // Old firmware: route DB is an object
                return parseRoutesCS3(
                    parseJSONObject(routeBR).getJSONArray("automatics"),
                    parseJSONArray(magBR),
                    parseJSONArray(locBR)
                );
            }
        }
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
                    control.logf(
                        "acc.invalidCs2Accessory",
                        m.toString()
                    );                    
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
                // Skip only the offending route.  Letting this throw would abort the import of every route.
                // The name is required because routes are indexed by it once they reach the database.
                if (m.get("id") == null || m.get("item") == null || m.get("name") == null)
                {
                    control.logf(
                        "route.invalidCs2Route",
                        m.get("name") != null ? m.get("name") : "?",
                        m.get("id") != null ? m.get("id") : "?"
                    );
                    continue;
                }

                try
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
                                    // Scale first, truncate second.  The other way round threw away
                                    // the fraction of every pause the operator tuned - the two real
                                    // route files in this repository carry 2.3 and 3.2 - and turned
                                    // anything under a second into no pause at all, because the
                                    // delay > 0 guard below then skips it.  parseRoutesCS3 has
                                    // always done it this way round.
                                    delay = Float.valueOf(Float.parseFloat(kv[1].trim()) * 1000).intValue();
                                }
                               
                                // Condition S88s
                                if ("kont".equals(kv[0]))
                                {
                                    // Another sensor follows in the same group - store the previous one
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
                        }

                        if (conditionS88 != 0)
                        {
                            r.addConditionS88(conditionS88, s88Status != 0);
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
                        
                            // Which command this item's "sekunde" pause belongs on.  Held as an
                            // object rather than found afterwards through setDelay(address, ms):
                            // that search returns at the first match, so a route touching one
                            // address twice - set a turnout, run past it, set it back - put the
                            // second item's pause on the first item's command and left its own
                            // with none.  The same defect the CS3 importer was fixed for.
                            RouteCommand pauseAfter = null;

                            if (setting >= 2)
                            {
                                pauseAfter = RouteCommand.RouteCommandAccessory(id + 1, accType, setting == 2);
                                r.addItem(pauseAfter);
                            }

                            RouteCommand primary =
                                RouteCommand.RouteCommandAccessory(id, accType, setting != 1 && setting != 3);

                            r.addItem(primary);

                            // Only set the delay once for three-way switches - on the first command
                            // of a pair, which is where it spaces the pair itself
                            if (pauseAfter == null)
                            {
                                pauseAfter = primary;
                            }

                            if (delay > 0)
                            {
                                pauseAfter.setDelay(delay);
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
                catch (NumberFormatException | ArrayIndexOutOfBoundsException e)
                {
                    // Skip only the offending route, matching parseLocomotivesCS3.  Letting this
                    // propagate would reach syncWithCS2's outer catch and abandon the entire import
                    // - every route and locomotive - because one record was malformed.  A non-numeric
                    // id and a key with no value (kont=) both land here; the null-field guard above
                    // covers only missing keys.
                    logMessage(
                        I18n.f("route.unparseableCs2Route",
                            m.get("name") != null ? m.get("name") : "?",
                            m.get("id") != null ? m.get("id") : "?"
                        ),
                        e,
                        false
                    );
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
        // Sanity check in case no functions are specified in the CS2 file
        if (functionList == null || functionList.isEmpty())
        {
            return new int[0];
        }

        String[] data = functionList.replace("{", "").replace("}", "").split("\\|");

        // Collected first, because the function number is the INDEX in the returned array while the
        // entry count only bounds how many are listed.  A CS2 file may list functions sparsely, and
        // indexing an entry-count-sized array by function number then threw - which escaped
        // parseLocomotives and aborted the entire Central Station sync for one bad locomotive.
        Map<Integer, Integer> parsed = new HashMap<>();
        int highest = -1;

        // Loop through each function
        for (String functionInfo : data)
        {
            int fn = 0;
            int type = 0;

            // Loop through the keys in each function
            for (String functionItem : functionInfo.split(","))
            {
                // A key with no value would otherwise run off the end of the array below
                if (!functionItem.contains("=")) continue;

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

            if (fn >= 0)
            {
                parsed.put(fn, type);

                if (fn > highest) highest = fn;
            }
        }

        // At least data.length, so a contiguous list returns exactly what it always did
        int[] output = new int[Math.max(data.length, highest + 1)];

        for (Map.Entry<Integer, Integer> function : parsed.entrySet())
        {
            output[function.getKey()] = function.getValue();
        }

        return output;
    }

    public static int[] parseFunctionTriggerTypes(String functionList)
    {
        // Sanity check in case no functions are specified in the CS2 file
        if (functionList == null || functionList.isEmpty())
        {
            return new int[0];
        }

        String[] data = functionList.replace("{", "").replace("}", "").split("\\|");

        // Collected first - see parseLocomotiveFunctions above for why the array cannot be sized by the
        // number of entries
        Map<Integer, Integer> parsed = new HashMap<>();
        int highest = -1;

        // Loop through each function
        for (String functionInfo : data)
        {
            int fn = 0;
            int type = 0;
            int dauer = 0;

            // Loop through the keys in each function
            for (String functionItem : functionInfo.split(","))
            {
                // A key with no value would otherwise run off the end of the array below
                if (!functionItem.contains("=")) continue;

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

            if (fn >= 0)
            {
                parsed.put(fn, dauer > 0 ? dauer : (type >= 128 ? Locomotive.FUNCTION_PULSE : Locomotive.FUNCTION_TOGGLE));

                if (fn > highest) highest = fn;
            }
        }

        int[] output = new int[Math.max(data.length, highest + 1)];

        for (Map.Entry<Integer, Integer> function : parsed.entrySet())
        {
            output[function.getKey()] = function.getValue();
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
     * Fetches a CS3 locomotive DB entry based on its id
     * @param searchId
     * @return 
     */
    private JSONObject getCS3LocById(String searchId, JSONArray locs)
    {
        // Cache accessories by ID
        if (this.locList.containsKey(searchId))
        {
            return this.locList.get(searchId);
        }
        
        for (int i = 0 ; i < locs.length(); i++)
        {
            JSONObject obj = locs.getJSONObject(i);
            
            if (searchId.equals(obj.getString("internname")))
            {
                this.locList.put(searchId, obj);
                
                return obj;
            }
        }
        
        return null;   
    }
    
    /**
     * Parses routes from the CS3 API
     * @param routeList
     * @param mags
     * @param locs
     * @return 
     */
    public List<MarklinRoute> parseRoutesCS3(JSONArray routeList, JSONArray mags, JSONArray locs)
    {
        if (locs.isEmpty())
        {
            logMessage(I18n.f("route.warningNoLocomotivesProvidedCs3"));
        }
        
        List<MarklinRoute> out = new ArrayList<>();
        
        if (routeList != null && routeList instanceof JSONArray)
        {
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

                        if (item.has("lok") && "speed".equals(item.getString("typ")) && item.has("lok"))
                        {
                            String locName = null;

                            if (this.getCS3LocById(item.getString("lok"), locs) != null)
                            {
                                locName = this.getCS3LocById(item.getString("lok"), locs).getString("name");
                            }

                            if (locName != null)
                            {
                                int speed = 0;

                                // wert is speed, default 0
                                if (item.has("wert"))
                                {
                                    speed = (int) ((item.getDouble("wert") / 1000.0) * 100.0); // check this: round down to nearest integer, ensure number is 0-100
                                }

                                RouteCommand rc = RouteCommand.RouteCommandLocomotiveSpeed(locName, speed);

                                if (item.has("sekunde") && item.getFloat("sekunde") > 0)
                                {
                                    // Fix lossy conversion from float to int
                                    rc.setDelay(Float.valueOf(item.getFloat("sekunde") * 1000).intValue());
                                }

                                r.addItem(rc);
                            }
                            else
                            {
                                logMessage(I18n.f("route.warningLocomotiveNotExistInDb", item.getString("lok")));
                            }
                        }

                        // This is disabled becuase the JSON does not appear to convey the function number (only the icon, which is non-unique)
                        /* 
                        else if (item.has("lok") && "func".equals(item.getString("typ")) && item.has("lok"))
                        {
                            String locName = null;

                            if (this.getCS3LocById(item.getString("lok"), locs) != null)
                            {
                                locName = this.getCS3LocById(item.getString("lok"), locs).getString("name");
                            }

                            if (locName != null)
                            {
                                // wert is on, default 0 but cant be on
                                // icon is (almost) fno, default 0
                                int fNo = 0;
                                boolean fActive = false;

                                if (item.has("icon"))
                                {
                                    fNo = item.getInt("icon");
                                }

                                if (item.has("wert") && "1".equals(item.get("wert")))
                                {
                                    fActive = true;
                                }

                                RouteCommand rc = RouteCommand.RouteCommandFunction(locName, fNo, fActive);

                                if (item.has("sekunde") && item.getFloat("sekunde") > 0)
                                {
                                    rc.setDelay(Float.valueOf(item.getFloat("sekunde") * 1000).intValue());
                                }

                                r.addItem(rc);
                            }
                            else
                            {
                                logMessage("Warning: route locomotive does not exist in database " + item.getString("lok"));
                            }
                        } */
                        else if (item.has("lok") && "dir".equals(item.getString("typ")) && item.has("lok"))
                        {
                            String locName = null;

                            if (this.getCS3LocById(item.getString("lok"), locs) != null)
                            {
                                locName = this.getCS3LocById(item.getString("lok"), locs).getString("name");
                            }

                            if (locName != null)
                            {
                                // wert is on, default 0 but cant be on
                                // icon is fno, defualt 0
                                // int fNo = 0;
                                Locomotive.locDirection dir = Locomotive.locDirection.DIR_FORWARD;

                                if (item.has("wert") && "1".equals(item.get("wert")))
                                {
                                    dir = Locomotive.locDirection.DIR_BACKWARD;
                                }

                                RouteCommand rc = RouteCommand.RouteCommandLocomotiveDirection(locName, dir);

                                if (item.has("sekunde") && item.getFloat("sekunde") > 0)
                                {
                                    rc.setDelay(Float.valueOf(item.getFloat("sekunde") * 1000).intValue());
                                }

                                r.addItem(rc);
                            }
                            else
                            {
                                logMessage(I18n.f("route.warningLocomotiveNotExistInDb", item.getString("lok")));
                            }
                        }
                        else if (item.has("typ") && "mag".equals(item.getString("typ")) && item.has("magnetartikel"))
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

                                // Which command the item's own "sekunde" pause belongs on: the LAST one this item
                                // emits.  It used to be found by searching the route for the address, and
                                // that search returns at the first match - so for a three-way at stellung
                                // 1, 2 or 3, where the pair is emitted address-then-address+1, the pause
                                // landed *between* the two drives instead of after them, and overwrote
                                // the gap that holds them apart.
                                //
                                // Null when a stellung emits nothing, which also stops the address search
                                // logging a missing key for it.
                                RouteCommand lastForItem = null;

                                // stellung 0 - key not included
                                // this means red/turn
                                if (!item.has("stellung") || "0".equals(item.getString("stellung")))
                                {
                                    // This is invalid for 3-way signals
                                    // if (3 == accessory.getInt("states"))
                                    if ("dreiwegweiche".equals(accessory.getString("typ")))
                                    {
                                        // Held as an object rather than delayed afterwards through
                                        // r.setDelay(address): that searches by address and stops at the
                                        // first match, so a route touching the same three-way twice
                                        // would put the gap on the wrong command.
                                        RouteCommand straighten =
                                            RouteCommand.RouteCommandAccessory(address + 1, protocol, false);

                                        straighten.setDelay(MarklinRoute.THREEWAY_ROUTE_DELAY_MS);
                                        r.addItem(straighten);
                                    }

                                    lastForItem = RouteCommand.RouteCommandAccessory(address, protocol, true);
                                    r.addItem(lastForItem);
                                }
                                // stellung 1 means isSwitched is false
                                // this means green/straight
                                else if ("1".equals(item.getString("stellung")))
                                {
                                    lastForItem =
                                        RouteCommand.RouteCommandAccessory(address, protocol, false);

                                    r.addItem(lastForItem);

                                    // This is invalid for 3-way signals
                                    // if (3 == accessory.getInt("states"))
                                    if ("dreiwegweiche".equals(accessory.getString("typ")))
                                    {
                                        // Both drives go straight here, so there is no wrong transient to
                                        // avoid - but they are still two coils, and the diagram path
                                        // spaces this case as well
                                        lastForItem.setDelay(MarklinRoute.THREEWAY_ROUTE_DELAY_MS);

                                        lastForItem =
                                            RouteCommand.RouteCommandAccessory(address + 1, protocol, false);

                                        r.addItem(lastForItem);
                                    }
                                }
                                else if ("2".equals(item.getString("stellung")))
                                {
                                    lastForItem =
                                        RouteCommand.RouteCommandAccessory(address, protocol, false);

                                    r.addItem(lastForItem);

                                    if (3 == accessory.getInt("states"))
                                    {
                                        // The gap is a turnout's, not every three-state accessory's.
                                        // states == 3 also matches a three-aspect signal, whose two
                                        // addresses drive lamps with nothing to bind - and delaying
                                        // those changed the parse of routes that were always correct.
                                        if ("dreiwegweiche".equals(accessory.getString("typ")))
                                        {
                                            lastForItem.setDelay(MarklinRoute.THREEWAY_ROUTE_DELAY_MS);
                                        }

                                        lastForItem =
                                            RouteCommand.RouteCommandAccessory(address + 1, protocol, true);

                                        r.addItem(lastForItem);
                                    }
                                }
                                // Unclear how this differs from 1, seems to only be used by certain signals
                                else if ("3".equals(item.getString("stellung")))
                                {
                                    lastForItem =
                                        RouteCommand.RouteCommandAccessory(address, protocol, false);

                                    r.addItem(lastForItem);

                                    if (3 == accessory.getInt("states"))
                                    {
                                        // Turnouts only - see stellung 2 above
                                        if ("dreiwegweiche".equals(accessory.getString("typ")))
                                        {
                                            lastForItem.setDelay(MarklinRoute.THREEWAY_ROUTE_DELAY_MS);
                                        }

                                        lastForItem =
                                            RouteCommand.RouteCommandAccessory(address + 1, protocol, false);

                                        r.addItem(lastForItem);
                                    }
                                }

                                if (item.has("sekunde") && lastForItem != null)
                                {
                                    lastForItem.setDelay(
                                        Float.valueOf(item.getFloat("sekunde") * 1000).intValue());
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
                                logMessage(I18n.f("route.warningIgnoringExtraS88InRoute", item.toString(), r.getName()));
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
                    logMessage(
                        I18n.f("route.invalidCs3Route",
                            describeCS3Field(routeList, i, "name"),
                            describeCS3Field(routeList, i, "id")
                        ),
                        e,
                        false
                    );
                }
            }
        }
        else
        {
            logMessage(I18n.f("route.warningRouteDataCs3"));
        }
        
        return out;
    }

    /**
     * Best-effort lookup of a single field of a CS3 record, so that an entry which failed to parse
     * can still be named in the error message
     * @param list
     * @param index
     * @param key
     * @return the field value, or "?" if it cannot be read
     */
    private String describeCS3Field(JSONArray list, int index, String key)
    {
        try
        {
            JSONObject entry = list.getJSONObject(index);

            if (entry.has(key))
            {
                return entry.get(key).toString();
            }
        }
        catch (JSONException e)
        {
            // We are already reporting a parsing failure, so fall through to the placeholder
        }

        return "?";
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
                logMessage(
                    I18n.f("loc.errorPreParsingLocomotives"),
                    e,
                    false
                );
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
                
                // dir and speed may now be available from v260+

                String type = loc.getString("dectyp");
                MarklinLocomotive.decoderType decoderType;
                Map<String, Double> multiUnitLocMap = new HashMap<>();

                // Multi-units
                if (loc.has("traktion"))
                {
                    decoderType = MarklinLocomotive.decoderType.MULTI_UNIT;
                    
                    // Parse multi unit locomotive names
                    // New CS3 v2.6.0+: this becomes an object
                    Object traktion = loc.get("traktion");

                    if (traktion instanceof JSONObject)
                    {
                        JSONObject trakObj = (JSONObject) traktion;

                        for (String key : trakObj.keySet())
                        {
                            JSONObject entry = trakObj.getJSONObject(key);

                            // Extract the locomotive identifier
                            String identifier = entry.optString("lokname", null);

                            if (internalNames.containsKey(identifier))
                            {
                                // New traction object has no direction flag, assume +1.0 // TODO - look at sendDirectionSpeed and traktionFunktionsMapping
                                multiUnitLocMap.put(internalNames.get(identifier), 1.0);
                            }
                            else
                            {
                                logMessage(
                                    I18n.f("loc.warningUnmatchedMultiUnitIdentifier", identifier)
                                );
                            }   
                        }
                    }
                    // CS3 v2.5.x and older
                    else
                    {
                        for (int j = 0; j < loc.getJSONArray("traktion").length(); j++)
                        {
                            String identifier = loc.getJSONArray("traktion").getString(j).split(";")[0]; 

                            if (internalNames.containsKey(identifier))
                            { 
                                multiUnitLocMap.put(internalNames.get(identifier), loc.getJSONArray("traktion").getString(j).split(";").length > 1 ? -1.0 : 1.0); 
                            }
                            else
                            {
                                logMessage(
                                    I18n.f("loc.warningUnmatchedMultiUnitIdentifier", identifier)
                                );                        
                            }
                        }
                    }
                                    
                    logMessage(
                        I18n.f("loc.multiUnitUsingUid", name, uid),
                        null,
                        true
                    );     
                    
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
                    newLoc.setModelMultiUnitLocomotives(multiUnitLocMap);
                }

                out.add(newLoc);
            }
            catch (NumberFormatException | JSONException e)
            {
                logMessage(
                    I18n.f("loc.invalidCs3Locomotive",
                        describeCS3Field(locomotiveList, i, "name"),
                        describeCS3Field(locomotiveList, i, "uid")
                    ),
                    e,
                    false
                );
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
                try
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
                    
                        logMessage(
                            I18n.f("loc.multiUnitUsingUid", name, Integer.toString(address)),
                            null,
                            true
                        );
                    
                        if (address > MarklinLocomotive.MULTI_UNIT_MAX_ADDR)
                        {
                            address -= MarklinLocomotive.MULTI_UNIT_BASE;
                        }
                    }
                    // Others
                    else
                    {
                        if ("mfx".equals(m.get("typ")))
                        {
                            type = MarklinLocomotive.decoderType.MFX;

                            // The same correction the DCC branch below has carried all along, and for
                            // the same reason: with no address field in the file the UID was used
                            // instead, and a UID is the address plus the type's base.  Left
                            // uncorrected it is past the highest MFX address there is, so the
                            // locomotive was created pointing at a decoder that cannot exist - every
                            // command sent into nothing, every state update from the real one
                            // unmatched, and the bad address written into the database.  The
                            // constructor does not validate; only setAddress does, so this bit
                            // existing locomotives not at all and new ones every time.
                            if (address > MarklinLocomotive.MFX_MAX_ADDR)
                            {
                                address -= MarklinLocomotive.MFX_BASE;
                            }
                        }
                        else if ("dcc".equals(m.get("typ")))
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
                
                    int[] funcs = parseLocomotiveFunctions(m.get("funktionen"));
                
                    MarklinLocomotive loc = new MarklinLocomotive(
                        control, 
                        address, 
                        type,
                        name,
                        extractFunctionTypes(funcs),
                        parseFunctionTriggerTypes(m.get("funktionen"))
                    );
                
                    if (funcs.length == 0)
                    {
                        logMessage(
                            I18n.f("loc.warningInitializedWithMissingFunctionData", name)
                        );                
                    }
                
                    if (m.get("icon") != null)
                    {
                        loc.setImageURL(this.getImageURL(m.get("icon")));
                    }
                
                    if (!multiUnitLocMap.isEmpty())
                    {
                        loc.setModelMultiUnitLocomotives(multiUnitLocMap);
                    }
                                
                    out.add(loc);
                }
                catch (NumberFormatException | NullPointerException | ArrayIndexOutOfBoundsException e)
                {
                    // Skip only the offending locomotive, matching parseLocomotivesCS3.  Letting this
                    // propagate would reach syncWithCS2's outer catch and abandon the entire import.
                    // A traktion block with no uid is the known escape: the address selection above
                    // tolerates a missing uid, but the multi-unit branch re-reads it unconditionally.
                    logMessage(
                        I18n.f("loc.invalidCs2Locomotive", m.get("name") != null ? m.get("name") : "?"),
                        e,
                        false
                    );
                }
            }
        }
        
        return out;
    }
    
    /**
     * Download the remote CS2 layout to the filesystem
     * @param localPath
     * @throws Exception 
     */
    public void downloadCS2Layout(File localPath) throws Exception
    {
        String gleisbild = getLayoutMasterURL();

        // Write this file to localPath/config/gleisbild.cs2
        File configDir = new File(localPath, "config");
        if (!configDir.exists())
        {
            configDir.mkdirs();
        }
        
        // Staged and moved into place, all three of these.
        //
        // Opening the destination truncates it at once, so a transfer that stopped part way - the
        // fifteen second read timeout, a station rebooted mid-sync, a cable - closed and COMMITTED a
        // half-written file.  When the destination is the local layout folder, which is what this
        // download is for, the next sync reads that truncation as the authoritative diagram: a page
        // missing most of its track, and nothing to say so.  The hazard is named in this file's own
        // comments; only the filename half of it was fixed.
        File masterLayoutFile = new File(configDir, "gleisbild.cs2");
        // UTF-8 explicitly, not the platform default: FileWriter would encode in Cp1252 on the
        // Windows/Java 8 configuration this targets, while the only reader of these files -
        // CS2File.fetchURL - decodes UTF-8 unconditionally.  A layout page whose name contains a
        // non-ASCII character was written in one encoding and read back in another, so the name came
        // back mangled, the file it pointed at could not be found, and syncWithCS2 responded by
        // silently clearing the local-layout override.
        copyAtomically(gleisbild, masterLayoutFile);
        
        // Process layout list and write files to localPath/config/gleisbilder/<layoutName>.cs2
        File layoutsDir = new File(configDir, "gleisbilder");
        if (!layoutsDir.exists())
        {
            layoutsDir.mkdirs();
        }
        
        for (String layoutName : parseLayoutList())
        {
            // Sanitised, and matching what getLayoutURL will look for when this folder is read back.
            // The remote fetch above is unaffected: it goes through sanitizeURL on the http branch.
            File layoutFile = new File(layoutsDir, sanitizeFilename(layoutName) + ".cs2");

            copyAtomically(getLayoutURL(layoutName), layoutFile);
        }
        
        // Download the accessory file      
        File magsFile = new File(configDir, "magnetartikel.cs2");

        copyAtomically(this.getMagURL(false), magsFile);

        // And the ROUTES, which this did not fetch (FR-021).
        //
        // Adam asked whether the current route config is exported when a backup is requested. For a
        // local layout it always was - the archive copies the whole `config` folder, and
        // `fahrstrassen.cs2` sits in it. For a layout read from the Central Station the backup
        // downloads a copy first, and this method is that download: it took the track diagram, the
        // pages and the accessories, and left the routes behind. So a Central Station backup was
        // missing them, and so was the local copy this menu makes when it switches over.
        //
        // Tolerated when absent, unlike the three above. A Central Station with no routes defined is
        // an ordinary thing, and the whole download should not fail because there is nothing to
        // fetch - which is a different judgement from the accessory file, whose absence would mean
        // the station is not answering properly.
        try
        {
            copyAtomically(this.getRouteURL(), new File(configDir, "fahrstrassen.cs2"));
        }
        catch (Exception noRoutes)
        {
            logMessage("No routes were downloaded from the Central Station.", noRoutes, false);
        }
    }
    
    /**
     * Fetches a URL and writes it to a file, all of it or none of it.
     *
     * Staged through a sibling file and moved into place, so a transfer that stops part way leaves
     * whatever was there before rather than a truncated file that the next sync will read as the
     * whole truth.
     *
     * @param url what to fetch
     * @param target where it goes
     * @throws Exception if the fetch or the write fails, with the previous file untouched
     */
    private void copyAtomically(String url, File target) throws Exception
    {
        try (BufferedReader reader = fetchURL(url))
        {
            Util.writeAtomically(target, out ->
            {
                try (BufferedWriter writer = new BufferedWriter(
                    new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8)))
                {
                    String line;

                    while ((line = reader.readLine()) != null)
                    {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                catch (Exception e)
                {
                    // As an IOException, because that is what the staging write contracts to throw -
                    // and throwing at all is the point: the staged file is discarded and the previous
                    // one stays where it is.
                    throw new java.io.IOException(e);
                }
            });
        }
    }

    /**
     * Reads the list of layouts
     * @return
     * @throws Exception 
     */
    private List<String> parseLayoutList() throws Exception
    {
        List<String> out = new ArrayList<>();

        for (Map<String, String> page : parseLayoutIndex())
        {
            out.add(page.get("name"));
        }

        return out;
    }

    /**
     * The pages named in gleisbild.cs2, with the id each carries.
     *
     * The id is what the Central Station orders pages by, and TrainControl has never used it.  Autonomy
     * does: it stores its setup against pages, and a page name is something a user renames while an id
     * is not, so keeping the id lets a setup survive a rename.
     *
     * The first page carries no id of its own and is page 1, which is what the Central Station assumes.
     *
     * A list rather than a map from name to id: two pages may carry the same name, and collapsing them
     * here would silently drop one from every caller, including the download that writes the files back.
     * Duplicate names already alias later, in layoutDB, but that is a decision made elsewhere and this
     * method should not make it for it.
     *
     * @return one entry per page, in file order, each with "name" and "id"
     * @throws Exception
     */
    private List<Map<String, String>> parseLayoutIndex() throws Exception
    {
        List<Map<String, String> > l = parseFile(fetchURL(getLayoutMasterURL()));

        List<Map<String, String>> out = new ArrayList<>();

        int position = 0;

        for (Map<String, String> m : l)
        {
            if ("seite".equals(m.get("_type")))
            {
                position++;

                String name = m.get("name");

                if (name == null) continue;

                Map<String, String> page = new java.util.LinkedHashMap<>();
                page.put("name", name);
                page.put("id", m.get("id") != null ? m.get("id") : String.valueOf(position));

                out.add(page);
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
        // Closes the reader, as parseFile does.  Every caller opens a reader purely to hand to this
        // method and consumes it exactly once - parseRoutesCS3 picks one of its two firmware branches,
        // so no reader is read twice - which makes closing here what actually frees the connection.
        // Without it every CS3 API call leaked one.
        try (BufferedReader reader = in)
        {
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }

            return new JSONArray(sb.toString());
        }
    }
    
    /**
     * Converts input file to JSON
     * @param in
     * @return
     * @throws IOException 
     */
    public static JSONObject parseJSONObject (BufferedReader in) throws IOException
    {
        // Closes the reader - see parseJSONArray
        try (BufferedReader reader = in)
        {
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }

            return new JSONObject(sb.toString());
        }
    }
    
    /**
     * Converts a component name from the MCS2 file to an internal componentType
     * @param name
     * @param address - only used for CS3 switches w/o address
     * @return
     * @throws Exception 
     */
    private LayoutDiagramComponent.componentType getComponentType(String name, int address) throws Exception
    {
        switch(name)
        {
            case "entkuppler":
            case "entkuppler_1":
                return LayoutDiagramComponent.componentType.UNCOUPLER;
            case "prellbock":
                return LayoutDiagramComponent.componentType.END;                
            case "s88kontakt":
                return LayoutDiagramComponent.componentType.FEEDBACK;
            case "s88bogen":
                return LayoutDiagramComponent.componentType.FEEDBACK_CURVE;
            case "s88doppelbogen":
                return LayoutDiagramComponent.componentType.FEEDBACK_DOUBLE_CURVE;
            case "gerade":
                return LayoutDiagramComponent.componentType.STRAIGHT;
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
                return LayoutDiagramComponent.componentType.SIGNAL;
            case "doppelbogen":
                return LayoutDiagramComponent.componentType.DOUBLE_CURVE;
            case "bogen":
                return LayoutDiagramComponent.componentType.CURVE;
            case "tunnel":
                return LayoutDiagramComponent.componentType.TUNNEL;
            case "kreuzung":
                return LayoutDiagramComponent.componentType.CROSSING;
            case "unterfuehrung":
                return LayoutDiagramComponent.componentType.OVERPASS;
            case "dkweiche":
            case "dkweiche_2":
            case "andreaskreuz":// Special double slip ?
                return LayoutDiagramComponent.componentType.SWITCH_CROSSING;
            case "drehscheibe": // Turntable
                return LayoutDiagramComponent.componentType.TURNTABLE;
            case "lampe":       // Lamp
            case "lampe_rt":    // Red light
            case "lampe_bl":    // Blue light
            case "lampe_gn":    // Green light
            case "lampe_ge":    // Yellow light
            case "bahnschranke":// Railroad crossing - TODO add dedicated icon
                return LayoutDiagramComponent.componentType.LAMP;
            case "fahrstrasse": // Route
                return LayoutDiagramComponent.componentType.ROUTE;
            case "text":        // Standalone text
                return LayoutDiagramComponent.componentType.TEXT;
            case "pfeil":       // Link to another page
                return LayoutDiagramComponent.componentType.LINK;
            
            // If these have an address, make them switchable, otherwise default to a permanent crossing
            case "linksweiche":
            // CS3 double slip switch 2 is just two of these
            case "dkw3_li_2":
            case "dkw3_li":
                if (address > 0) return LayoutDiagramComponent.componentType.SWITCH_LEFT;
            case "custom_perm_left":
                return LayoutDiagramComponent.componentType.CUSTOM_PERM_LEFT;
            
            // If these have an address, make them switchable, otherwise default to a permanent crossing
            case "rechtsweiche":
            // CS3 double slip switch 2 is just two of these
            case "dkw3_re":
            case "dkw3_re_2":
                // CS3 double slip switch 2. If these have an address, make them switchable, otherwise default to a permanent crossing
                if (address > 0) return LayoutDiagramComponent.componentType.SWITCH_RIGHT;
            case "custom_perm_right":
                return LayoutDiagramComponent.componentType.CUSTOM_PERM_RIGHT;
            
            // Y switch.  If these have an address, make them switchable, otherwise default to a permanent crossing
            case "yweiche":
                if (address > 0) return LayoutDiagramComponent.componentType.SWITCH_Y;
            case "custom_perm_y":
                return LayoutDiagramComponent.componentType.CUSTOM_PERM_Y;
             
            case "dreiwegweiche":
                if (address > 0) return LayoutDiagramComponent.componentType.SWITCH_THREE;
            case "custom_perm_threeway":
                return LayoutDiagramComponent.componentType.CUSTOM_PERM_THREEWAY;
                
            case "hosentraeger":
            case "custom_scissors":
                if (address > 0) return LayoutDiagramComponent.componentType.CUSTOM_SCISSORS;
            case "custom_perm_scissors":
                return LayoutDiagramComponent.componentType.CUSTOM_PERM_SCISSORS;
                
            // Custom (non-CS2) components
            default:
                logMessage(
                    I18n.f("layout.warningComponentNotSupported", name)
                );
                
                return null;
        }
        
        //throw new Exception("Unsupported component: " + name);        
    }
    
    public List<MarklinAccessory> getMagList(boolean local) throws Exception
    {
        return parseMags(parseFile(fetchURL(getMagURL(local))));
    }
    
    /**
     * Processes a layout
     * @param accDB
     * @return
     * @throws Exception 
     */
    public List<LayoutDiagram> parseLayout(List<MarklinAccessory> accDB) throws Exception
    {
        // How a file word maps to a type, handed to the component class so that its export can tell
        // "still what was read" from "redrawn by the user" without owning a second copy of this table.
        // The address is irrelevant to the question being asked - whether the word still means this type
        // - and the two switch entries that depend on it agree for everything except a permanent
        // crossing, which is a different type either way.
        LayoutDiagramComponent.setFileWordMapping(word ->
        {
            try
            {
                return getComponentType(word, 1);
            }
            catch (Exception e)
            {
                return null;
            }
        });

        List<Map<String, String>> index = this.parseLayoutIndex();

        List<String> names = new ArrayList<>();

        for (Map<String, String> page : index)
        {
            names.add(page.get("name"));
        }
        
        List<LayoutDiagram> out = new ArrayList<>();
        
        // by position, not by looking the name up: two pages may share a name, and a lookup would give
        // them both the first one's id
        int pageIndex = -1;
        
        for (String name : names)
        {
            pageIndex++;

            // One page that will not parse costs THAT page, not the layout.
            //
            // Nothing here caught a per-page failure, while both sibling parsers in this file were
            // given per-record guards - parseLocomotives says why: one bad locomotive used to abort
            // the entire Central Station sync.  Here it was worse than an abort, because the caller
            // answers a failed parse by clearing the local-layout folder preference: a single
            // malformed element, or a page the index names and the folder does not hold, silently
            // discarded the user's choice of where their layout lives.
            //
            // pageIndex is advanced ABOVE this, so a skipped page does not renumber the ones after
            // it.  That matters more than it looks: the autonomy setup is keyed by page id.
            try
            {
 
                String url = getLayoutURL(name);
            
                // Null-checked, as every other logging call in this class is - they all go through the
                // null-safe logMessage and this one did not.  A CS2File built without a control station is
                // a perfectly ordinary thing to make: it is how a layout is parsed on its own, with no
                // hardware and no model behind it.
                if (control != null && control.isDebug())
                {
                    control.logf(
                        "layout.loadingFromUrl",
                        url
                    );            
                }
            
                List<Map<String, String> > l = parseFile(fetchURL(url));
                        
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
                            logMessage(
                                I18n.f("layout.warningElementNoCoordinateInfoAssumingZeroZero", m),
                                null,
                                true
                            );
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
            
                LayoutDiagram layout = new LayoutDiagram(name, maxX + 1, maxY + 1, url, this.control);

                layout.setPageId(index.get(pageIndex).get("id"));
                        
                for (Map<String, String> m : l)
                {
                    // The blocks above the elements - version, groesse, anything a later firmware adds -
                    // kept so that saving the page does not delete them.  The exporter used to write a
                    // hardcoded version block in their place.
                    if (!"element".equals(m.get("_type")))
                    {
                        layout.addUnmodelledBlock(m);
                        continue;
                    }

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
                                logMessage(
                                    I18n.f("layout.errorComponentNoAddressAtCoordinates", type, x, y)
                                );
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
                    
                        // Read protocol from mags file
                        if (addressMap.get(address) != null)
                        {
                            protocol = addressMap.get(address).getDecoderType();
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
                                logMessage(
                                    I18n.f("acc.errorUnknownProtocol", m.get("prot"))
                                );
                            }
                        }
                    
                        LayoutDiagramComponent.componentType modelled = getComponentType(type, address);

                        if (modelled != null)
                        {
                            layout.addComponent(
                               modelled,
                               x, y, orient, state, address, rawAddress, protocol, m.get("text")
                            );

                            // Anything the file said about this square that the component has no field for.
                            // Saving regenerates the page from the model, so a key nobody carries is a key
                            // deleted from the user's diagram.
                            LayoutDiagramComponent added = layout.getComponent(x, y);

                            if (added != null)
                            {
                                added.setUnmodelledKeys(m);

                                // And the file's own word for the type, plus its rotation exactly as given.
                                // Both ARE modelled, and both are lossy: the type mapping is many-to-one, so
                                // writing the canonical word back collapses every variant the file
                                // distinguished, and the rotation of a semaphore signal is corrected on the
                                // way in by a rule keyed on the word that has just been thrown away.
                                // Only the word.  The rotation used to be kept too, so that an
                                // untouched component could be written back verbatim, but the export now
                                // derives the file's number from the word it is writing - which is exact,
                                // and cannot disagree with a second rule.
                                added.setOriginalTyp(m.get("typ"));
                            }
                        }
                        else
                        {
                            // Not a component this program knows, and therefore not one it may delete.  Kept
                            // verbatim so that saving the page - which naming a station does, unasked - puts
                            // it back rather than dropping it.
                            layout.addUnmodelledElement(m);
                        }
                    }
                }
            
                layout.checkBounds();
            
                out.add(layout);
            }
            catch (Exception | Error bad)
            {
                logMessage(I18n.f("layout.warningPageCouldNotBeRead", name, String.valueOf(bad)),
                    null, true);
            }
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
       // try-with-resources: this is called in a retry loop at startup, and each attempt used to leave
       // its connection open
       try (BufferedReader reachable = CS2File.fetchURL(CS2File.getPingIP(host)))
       {
           return reachable != null;
       }
       catch (Exception e)
       {
           return false;
       }
    }
}
