package org.traincontrol.marklin.file;

import java.awt.Point;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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
        
    public CS2File(String IP, MarklinControlStation control)
    {
        this.IP = IP;
        this.control = control;
        this.setDefaultLayoutDataLoc();
        this.magList = new HashMap<>();
        this.locList = new HashMap<>();
    }
    
    public void setDefaultLayoutDataLoc()
    {
        this.layoutDataLoc = getDefaultLayoutDataLoc();
    }
    
    public String getDefaultLayoutDataLoc()
    {
        return "http://" + this.IP;
    }
    
    public void setLayoutDataLoc(String path)
    {
        this.layoutDataLoc = path;
    }
    
    private void logMessage(String message)
    {
        logMessage(message, null, false);
    }
    
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
    
    /** IP to ping at startup */
    public static String getPingIP(String host)
    {
        return "http://" + host + "/can/";
    }
    
    /** Fixes URL character issues */
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
    
    public String getImageURL(String image)
    {
        return "http://" + this.IP + "/icons/" + 
                this.sanitizeURL(image) + ".png";
    }
    
    public String getImageURLCS3(String image)
    {
        return "http://" + this.IP + "/app/assets/lok/" + 
                this.sanitizeURL(image) + ".png";
    }
    
    /** The web app's rule for a CS3 icon path (OB-404). */
    private String cs3IconURL(String iconPath)
    {
        int slash = iconPath.lastIndexOf('/');
        String file = iconPath.substring(slash + 1);
        String folder = slash > 0 ? iconPath.substring(0, slash) : "";
        String url = getImageURLCS3(file.endsWith(".png") ? file.substring(0, file.length() - 4) : file);

        // The folder is unencoded, as the web app sends it ("?path=".concat(u)), but for its spaces
        return folder.isEmpty() || "/home/cs3/cs3data/lokicons".equals(folder) || "/usr/local/cs3/lokicons".equals(folder)
            ? url : url + "?path=" + folder.replace(" ", "%20");
    }

    public String getLocURL()
    {
        return "http://" + this.IP + "/config/lokomotive.cs2";
    }
    
    public String getRouteURL()
    {
        return "http://" + this.IP + "/config/fahrstrassen.cs2";
    }
    
    /**
     * @param local - do we prefer to fetch the local file, i.e. if the layoutDataLoc is local?
     *                We need this because it's possible to read routes from CS2 and layouts locally.
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
    
    public String getLayoutMasterURL()
    {
        return getLayoutMasterURL(this.layoutDataLoc);
    }

    /** Whether the layout is read from the Central Station itself, not from a folder of ours. */
    public boolean isFromStation()
    {
        return getDefaultLayoutDataLoc().equals(this.layoutDataLoc);
    }

    /** @return the layout folder's path when it is one of ours, null for the station */
    public String localLayoutFolder()
    {
        if (isFromStation()) return null;

        try
        {
            return java.nio.file.Paths.get(new URL(this.layoutDataLoc.replaceAll(" ", "%20")).toURI()).toString();
        }
        catch (Exception notAFolder)
        {
            return null;
        }
    }
    
    public static String getLayoutMasterURL(String dataPath)
    {
        return dataPath + "/config/gleisbild.cs2";
    }
    
    public String getLayoutURL(String layoutName)
    {
        return getLayoutURL(this.layoutDataLoc, layoutName);
    }
    
    public String getLayoutURL(String dataPath, String layoutName)
    {
        return dataPath + "/config/gleisbilder/"
                + (dataPath.contains("http://") ? sanitizeURL(layoutName) : sanitizeFilename(layoutName))
                + ".cs2";
    }

    /**
     * Makes a page name safe to use as a local filename.
     *
     * Applied on BOTH sides: the local read locates a page by the name in the index, so sanitizing only the write would
     * leave a file the reader could not find.  The remote read keeps sanitizeURL.
     */
    public static String sanitizeFilename(String name)
    {
        // One implementation, in Util, because LayoutDiagram is a writer too and cannot reach in here
        return org.traincontrol.util.Util.sanitizeFilename(name);
    }
    
    public String getCS3AppUrl()
    {
        return "http://" + this.IP + "/app";
    }
    
    /** Pre v2.6.0 CS3 firmware handled differently */
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
    
    /** CS3 Loc DB URL - auto resolves */
    public String getCS3LocDBUrl() throws Exception
    {
        boolean is260 = this.isCS3Version260OrAbove();
        
        return getCS3LocDBUrl(is260 ? 260 : 250);
    }
    
    public String getCS3RouteDBUrl()
    {
        return "http://" + this.IP + "/app/api/automatics";
    }
    
    public String getCS3InfoUrl()
    {
        return "http://" + this.IP + "/app/api/info";
    }

    public String getCS3MagDBUrl()
    {
        return "http://" + this.IP + "/app/api/mags";
    }
            
    /**
     * Parsing this is not currently supported - 
     * with a CS3 we can display offline CS2 layout files instead
     */
    public String getCS3LayoutUrl()
    {
        return "http://" + this.IP + "/app/api/gbs";
    }
    
    public String getDeviceInfoURL()
    {
        return getDeviceInfoURL(this.IP);
    }
    
    public static String getDeviceInfoURL(String ipAddress)
    {
        return "http://" + ipAddress + "/config/geraet.vrs";
    }
    
    /** Check if this is a CS3 by looking at the info file */
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
    
    /** Opens a URL and returns a string stream */
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

    /** Parses a CS2 config file into a string map */
    public static List<Map<String, String> > parseFile(BufferedReader in) throws Exception
    {
        try (BufferedReader reader = in)
        {
            return parseFileContents(reader);
        }
    }

    /**
     * The shapes of a config file's lines, compiled once for `parseFileContents`: `String.matches` and the String form
     * of `replace` compile a pattern on every call, about half of what a sync or a layout load spends parsing.
     */
    private static final Pattern ARRAY_ENTRY = Pattern.compile("^ \\.\\.[a-z0-9A-Z]+=.+$");
    private static final Pattern BLOCK_NAME = Pattern.compile("^[a-zA-Z]+$");
    private static final Pattern KEY_AND_VALUE = Pattern.compile("^ \\.[a-z0-9A-Z]+=.+$");
    private static final Pattern KEY_ALONE = Pattern.compile("^ \\.[a-z0-9A-Z]+$");
    private static final Pattern BARE_KEY = Pattern.compile("^[a-zA-Z0-9_]+=.*$");

    /** The body of parseFile.  Call parseFile, which closes the reader whatever happens. */
    private static List<Map<String, String> > parseFileContents(BufferedReader in) throws Exception
    {
        List<Map<String, String> > items = new ArrayList <>();
                
        String s;
        String lastKey = null;
        Map<String, String> item = null;

        // Keys at the left margin, which arrive before any block is opened.
        Map<String, String> bare = null;
        
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
            if (s.contains(".funktionen_2")) s = s.replace(".funktionen_2", ".funktionen");
                        
            if (ARRAY_ENTRY.matcher(s).matches())
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
                    
                    // Joined by hand, not through HashMap.toString(): its ", " cannot be told from one INSIDE a value
                    // (lokname is free text). The map is still walked in ITS order: parseLocomotives splits on ",lok="
                    // and needs lokname first, which only the hash order gives.
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
                
                // Case-insensitive, like `LayoutDiagram.readLayoutIndexIds`: firmware casing varies (`Version`,
                // `version`), and a capitalized block name would otherwise give an empty diagram with no message.
                if (BLOCK_NAME.matcher(s).matches())
                {
                    if (item != null)
                    {
                        items.add(item);
                    }

                    if (bare != null)
                    {
                        bare.put("_type", "_bareKeys");

                        items.add(bare);

                        bare = null;
                    }

                    item = new HashMap<>();

                    // Lower-cased, so the readers of "_type" work whatever the file's casing.
                    item.put("_type", s.toLowerCase());

                    // The file's own spelling is kept: `exportToCS2TextFormat` writes `_type` back as the block name
                    // for blocks this program does not model.  Two keys rather than case-insensitive comparisons at
                    // every reader (OB-406).
                    if (!s.equals(s.toLowerCase())) item.put("_typeAsWritten", s);
                }
                else if (KEY_AND_VALUE.matcher(s).matches())
                {
                    // Limit of 2, so that a route or locomotive name containing an equals sign is not truncated
                    String[] parts = s.substring(2).split("=", 2);

                    if (item != null)
                    {
                        item.put(parts[0], parts[1]);
                    }
                }   
                else if (KEY_ALONE.matcher(s).matches())
                {
                    // Same character class as the arm above: a route's condition block ` .S88Flag` has no `=` and lands
                    // here; if it did not match, the `..kont=` group after it would be flushed under the wrong key and
                    // `parseRoutes` would drop the route.
                    lastKey = s.substring(2);
                }
                else if (BARE_KEY.matcher(s).matches())
                {
                    // A key at the left margin (`page=1` in a genuine page export): neither block name, key nor array
                    // header.  Collected into an item of its own, which `LayoutDiagram.addBareKey` puts back after the
                    // header.
                    if (bare == null) bare = new java.util.LinkedHashMap<>();

                    String[] parts = s.split("=", 2);

                    bare.put(parts[0], parts.length > 1 ? parts[1] : "");
                }
            } 
            
            // We need to add the current item to the list...
            if (s.equals("__done"))
            {
                if (item != null)
                {
                    items.add(item);
                }

                if (bare != null)
                {
                    bare.put("_type", "_bareKeys");

                    items.add(bare);

                    bare = null;
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
    
    /** Helper to detect CS3 404 errors */
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
     */
    private BufferedReader fetchCS3LocDB() throws Exception
    {
        return openCS3Locos(isCS3Version260OrAbove());
    }

    /**
     * The locomotive list of the endpoint the firmware version names.  If that answers not found, the old probe decides
     * which endpoint the station has, once, and its answer replaces the version for this parser; a station that has
     * neither is not found (FR-170).
     */
    private BufferedReader openCS3Locos(boolean is260) throws Exception
    {
        try
        {
            return fetchURL(getCS3LocDBUrl(is260 ? 260 : 250));
        }
        catch (FileNotFoundException notThere)
        {
            boolean probed = !isNotFoundError(getCS3LocDBUrl(260));
            this.cs3Firmware = new FirmwareVersion(2, probed ? 6 : 5, 0);

            if (probed == is260)
            {
                throw notThere;
            }

            return fetchURL(getCS3LocDBUrl(probed ? 260 : 250));
        }
    }

    public List<MarklinLocomotive> parseLocomotivesCS3() throws Exception
    {
        return parseLocomotivesCS3(readList(fetchCS3LocDB(), null));
    }

    /**
     * Whether some record of the locomotive list cannot be told by its internal name and name, which the members of a
     * multi-unit and the locomotives of a route are named by: then a member or a locomotive that is not found may be
     * that record, and what names it is left as it is (FR-169).
     */
    private static boolean hasUnnameableRecord(JSONArray locomotives)
    {
        for (int i = 0; i < locomotives.length(); i++)
        {
            JSONObject loc = locomotives.optJSONObject(i);

            if (loc == null || text(loc, "internname") == null || text(loc, "name") == null)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The same, for a caller that already knows the firmware.
     * @param is260 what isCS3Version260OrAbove answered
     */
    public List<MarklinLocomotive> parseLocomotivesCS3(boolean is260) throws Exception
    {
        return parseLocomotivesCS3(readList(openCS3Locos(is260), null));
    }
    
    // The accessory list the route import read, for the positions read after it in the same sync
    private JSONArray cs3Mags;

    /**
     * The CS3's last position of each two-position MM2 or DCC article (/app/api/mags/state), by accessory UID, true for
     * turned (state "0", as the CAN position byte).  The station can answer a first request with an empty body, so that
     * is asked once more.
     */
    public Map<Integer, Boolean> parseAccessoryStatesCS3() throws Exception
    {
        String body = "";

        for (int attempt = 0; attempt < 2 && body.trim().isEmpty(); attempt++)
        {
            try (BufferedReader in = fetchURL(getCS3MagDBUrl() + "/state"))
            {
                body = in.lines().collect(Collectors.joining("\n"));
            }
        }

        Map<String, String> stateById = new HashMap<>();
        JSONArray states = new JSONArray();

        try
        {
            states = new JSONArray(body);
        }
        catch (JSONException unreadable)
        {
            // An answer that will not read changes no position
        }

        for (int i = 0; i < states.length(); i++)
        {
            JSONArray pair = states.optJSONArray(i);

            if (pair != null && pair.length() > 0 && pair.optJSONObject(1) != null)
            {
                stateById.put(String.valueOf(pair.opt(0)), pair.optJSONObject(1).optString("state", ""));
            }
        }

        JSONArray mags = this.cs3Mags;

        if (mags == null)
        {
            try (BufferedReader in = fetchURL(getCS3MagDBUrl()))
            {
                mags = readList(in, null);
            }
        }

        Map<Integer, Boolean> out = new HashMap<>();

        for (int i = 0; i < mags.length(); i++)
        {
            JSONObject a = mags.optJSONObject(i);
            String state = a == null ? null : positionOf(stateById.get(String.valueOf(a.opt("id"))));

            if (a != null && a.optInt("states") == 2 && a.optInt("address") > 0 && state != null
                && ("mm".equals(a.optString("prot")) || "dcc".equals(a.optString("prot"))))
            {
                out.put(MarklinAccessory.UIDfromAddress(a.optInt("address") - 1, "dcc".equals(a.optString("prot"))
                    ? Accessory.accessoryDecoderType.DCC : Accessory.accessoryDecoderType.MM2), "0".equals(state));
            }
        }

        return out;
    }

    /**
     * "0" or "1" for a position the CS3 reports, which is 128 plus the position while the solenoid is energized
     * (FR-169); null for anything else, which is skipped.
     */
    private static String positionOf(String reported)
    {
        String plain = reported == null ? "" : reported.trim();

        return "0".equals(plain) || "128".equals(plain) ? "0" : "1".equals(plain) || "129".equals(plain) ? "1" : null;
    }

    /** Whether the CS3 runs firmware 2.6 or later, which serves its locomotives and routes differently. */
    public boolean isCS3Version260OrAbove() throws Exception
    {
        return cs3Firmware().atLeast(2, 6);
    }

    // The CS3's firmware, found once for this parser: a sync makes a new parser, and the actions after it reuse the
    // answer (FR-170)
    private volatile FirmwareVersion cs3Firmware;

    /**
     * The CS3's firmware, read from /app/api/info; if that cannot be read, 2.6.0 or 2.5.0 by whether the 2.6
     * locomotive endpoint answers.
     */
    private FirmwareVersion cs3Firmware() throws Exception
    {
        FirmwareVersion found = this.cs3Firmware;

        if (found == null)
        {
            try (BufferedReader in = fetchURL(getCS3InfoUrl()))
            {
                found = FirmwareVersion.parse(parseJSONObject(in).optString("softwareVersion", null));
            }
            catch (Exception unreadable)
            {
                found = null;
            }

            if (found == null)
            {
                found = new FirmwareVersion(2, isNotFoundError(getCS3LocDBUrl(260)) ? 5 : 6, 0);
            }

            this.cs3Firmware = found;
        }

        return found;
    }

    public List<MarklinRoute> parseRoutesCS3() throws Exception
    {
        return parseRoutesCS3(isCS3Version260OrAbove());
    }

    /**
     * The same, for a caller that already knows the firmware.  Routes need all three lists: if any cannot be read, this
     * throws UnreadableAnswerException and the caller leaves its routes as they are (FR-169).
     * @param is260 what isCS3Version260OrAbove answered
     */
    public List<MarklinRoute> parseRoutesCS3(boolean is260) throws Exception
    {
        // All three in one try-with-resources, so an exception after the first is opened leaks none of them.
        try (BufferedReader routeBR = fetchURL(getCS3RouteDBUrl());
             BufferedReader magBR   = fetchURL(getCS3MagDBUrl());
             BufferedReader locBR   = openCS3Locos(is260))
        {
            // The routes are the list itself from firmware 2.6, and under "automatics" before; either is read
            JSONArray routes = readList(routeBR, "automatics");
            JSONArray mags = readList(magBR, null);
            JSONArray locs = readList(locBR, null);

            requireUsable(routes, mags, "mag", o -> o.optInt("id", Integer.MIN_VALUE) != Integer.MIN_VALUE, "accessories");
            requireUsable(routes, locs, "lok", o -> true, "locomotives");

            return parseRoutesCS3(routes, mags, locs);
        }
    }

    /**
     * Whether the routes' items name records of `list` (a "typ" of `reference`, or any "lok" for locomotives): then every
     * record must carry what matches them, and there must be some, or no route could be read right.
     */
    private static void requireUsable(JSONArray routes, JSONArray list, String reference,
        java.util.function.Predicate<JSONObject> usable, String what) throws UnreadableAnswerException
    {
        boolean referred = false;

        for (int i = 0; i < routes.length(); i++)
        {
            JSONArray items = routes.optJSONObject(i) == null ? null : routes.optJSONObject(i).optJSONArray("items");

            for (int j = 0; items != null && j < items.length(); j++)
            {
                JSONObject item = items.optJSONObject(j);
                referred |= item != null && ("lok".equals(reference) ? item.has("lok") : reference.equals(item.opt("typ")));
            }
        }

        boolean any = false;

        for (int i = 0; referred && i < list.length(); i++)
        {
            JSONObject record = list.optJSONObject(i);

            if (record != null && !usable.test(record))
            {
                throw new UnreadableAnswerException("a record of the " + what + " cannot be matched");
            }

            any |= record != null;
        }

        if (referred && !any)
        {
            throw new UnreadableAnswerException("the routes use " + what + ", and there are none");
        }
    }

    /**
     * A CS3 list: the array itself, or the array under `key` of an object.  Anything else, including an empty or cut-off
     * answer, is not a list (FR-169).
     */
    private static JSONArray readList(BufferedReader in, String key) throws IOException
    {
        String body = readAndClose(in);

        try
        {
            Object value = new JSONTokener(body).nextValue();

            if (value instanceof JSONArray)
            {
                return (JSONArray) value;
            }

            if (key != null && value instanceof JSONObject && ((JSONObject) value).optJSONArray(key) != null)
            {
                return ((JSONObject) value).optJSONArray(key);
            }
        }
        catch (JSONException unreadable)
        {
            // Not a list, below
        }

        throw new UnreadableAnswerException("the answer is not a list");
    }

    /** The string under a key, or null if there is none or it is not a string. */
    private static String text(JSONObject o, String key)
    {
        return o.opt(key) instanceof String ? (String) o.opt(key) : null;
    }
    
    /**
     * Reads a CS2 accessory database
     * Unsure if this is complete - we only care about checking if an address is DCC
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
                
                // Not used yet: .schaltzeit (switching time, ms) and the exact .typ (dkw_1antrieb is read as a signal here)
                try
                {
                    MarklinAccessory acc = new MarklinAccessory(control, 
                        Integer.parseInt(m.get("id")),
                        m.get("typ").contains("weiche") ? 
                            Accessory.accessoryType.SWITCH :
                            Accessory.accessoryType.SIGNAL, 
                        m.get("dectyp") != null ?
                            MarklinAccessory.determineAccessoryDecoderType(m.get("dectyp").toUpperCase().trim()) :
                            Accessory.accessoryDecoderType.MM2,
                        m.get("name"), 
                        m.get("stellung") == null || "0".equals(m.get("stellung")), // 0 is turned; a CS2 leaves out a 0
                        0);

                    out.add(acc);
                }
                catch (NumberFormatException | ArrayIndexOutOfBoundsException e)
                {
                    // Skip the record, not the import, as in the sibling parsers: unguarded, a non-numeric value or an
                    // empty `id=` would fail the whole sync, which reads as "not connected" at start-up (OB-407).
                    control.logf("acc.invalidCs2Accessory", m.toString());
                }
             }
        }
        
        return out;
    }
    
    /** Reads a CS2 route database */
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

                    // Conditions (` .S88Flag`) come first, then commands (` .item`), in file order, as
                    // `addConditionS88` records them; the loop below tells them apart by whether a piece carries
                    // `kont`.
                    StringBuilder both = new StringBuilder();

                    if (m.get("S88Flag") != null)
                    {
                        both.append(m.get("S88Flag")).append("|");
                    }

                    both.append(m.get("item"));

                    String route = both.toString().replace("{", "").replace("}","");
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
     */
    public static int[] parseLocomotiveFunctions(String functionList)
    {
        return parseFunctionList(functionList, false);
    }

    public static int[] parseFunctionTriggerTypes(String functionList)
    {
        return parseFunctionList(functionList, true);
    }

    /**
     * Slot n holds function n's "typ", or with triggerTypes its "dauer" (else pulse for typ 128 and up, toggle).
     * "dauer" is read only for triggerTypes, so a malformed one never fails the "typ" answer.
     */
    private static int[] parseFunctionList(String functionList, boolean triggerTypes)
    {
        // Sanity check in case no functions are specified in the CS2 file
        if (functionList == null || functionList.isEmpty())
        {
            return new int[0];
        }

        String[] data = functionList.replace("{", "").replace("}", "").split("\\|");

        // Collected first: the function number is the index in the returned array, and a CS2 file may list functions
        // sparsely, so the array is sized by the highest number.  An out-of-range index would abort the whole sync for
        // one locomotive.
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
                else if (triggerTypes && "dauer".equals(item[0]))
                {
                    dauer = Integer.parseInt(item[1]);
                }
            }

            if (fn >= 0)
            {
                parsed.put(fn, !triggerTypes ? type
                        : dauer > 0 ? dauer : (type >= 128 ? Locomotive.FUNCTION_PULSE : Locomotive.FUNCTION_TOGGLE));

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
    
    private JSONObject getCS3MagById(int searchId, JSONArray mags)
    {
        return findCS3(this.magList, searchId, mags, o -> o.optInt("id", Integer.MIN_VALUE) == searchId);
    }
    
    private JSONObject getCS3LocById(String searchId, JSONArray locs)
    {
        return findCS3(this.locList, searchId, locs, o -> searchId.equals(text(o, "internname")));
    }

    /** The first record of a CS3 list that matches, remembered by the key it was asked for; records that are not objects are passed over. */
    private <K> JSONObject findCS3(Map<K, JSONObject> found, K key, JSONArray list, java.util.function.Predicate<JSONObject> matches)
    {
        if (found.containsKey(key))
        {
            return found.get(key);
        }

        for (int i = 0 ; i < list.length(); i++)
        {
            JSONObject obj = list.optJSONObject(i);

            if (obj != null && matches.test(obj))
            {
                found.put(key, obj);

                return obj;
            }
        }

        return null;
    }
    
    // The ids of the routes of the last parse that would not read: the sync keeps those it holds locked
    private final List<Integer> unreadableRouteIds = new ArrayList<>();

    /** The ids of the routes the last CS3 route parse had to skip, so that the routes held under them are left alone. */
    public List<Integer> getUnreadableRouteIds()
    {
        return new ArrayList<>(this.unreadableRouteIds);
    }

    // Whether a route of the last parse that would not read has no id to keep it by, and how many records of routes and
    // locomotives would not read
    private boolean unreadableRouteWithoutId;

    private int unreadableRecords;

    /** Whether a route that would not read had no readable id: then the routes held cannot be told apart from deleted ones. */
    public boolean hasUnreadableRouteWithoutId()
    {
        return this.unreadableRouteWithoutId;
    }

    /** How many route and locomotive records the parsers had to skip. */
    public int getUnreadableRecords()
    {
        return this.unreadableRecords;
    }

    /**
     * The name of the locomotive a route item names, or null with a warning if the list has none by that name; if some
     * record of the list would not read, it may be that one, and the route is skipped instead (FR-169).
     */
    private String routeLocomotiveName(JSONObject item, JSONArray locs, boolean unnameable)
    {
        JSONObject found = this.getCS3LocById(item.getString("lok"), locs);

        if (found != null)
        {
            return found.getString("name");
        }

        if (unnameable)
        {
            throw new JSONException("the locomotive may be a record that would not read");
        }

        logMessage(I18n.f("route.warningLocomotiveNotExistInDb", item.getString("lok")));
        return null;
    }

    public List<MarklinRoute> parseRoutesCS3(JSONArray routeList, JSONArray mags, JSONArray locs)
    {
        this.cs3Mags = mags;
        this.unreadableRouteIds.clear();
        this.unreadableRouteWithoutId = false;
        boolean unnameable = hasUnnameableRecord(locs);

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

                        // An item says what it is, and a train command names its locomotive
                        String itemType = item.getString("typ");

                        if (("speed".equals(itemType) || "dir".equals(itemType)) && !item.has("lok"))
                        {
                            throw new JSONException("a train command names no locomotive");
                        }

                        if (item.has("lok") && "speed".equals(item.getString("typ")) && item.has("lok"))
                        {
                            String locName = routeLocomotiveName(item, locs, unnameable);

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
                        }

                        // A "func" item is not read: the JSON does not appear to convey the function number (only the
                        // icon, which is non-unique).
                        else if (item.has("lok") && "dir".equals(item.getString("typ")) && item.has("lok"))
                        {
                            String locName = routeLocomotiveName(item, locs, unnameable);

                            if (locName != null)
                            {
                                // wert is 1 for backward and 0 for forward, as a number or as text, and omitted for forward
                                // like the other defaults; any other value does not say which way, and its route is skipped
                                Locomotive.locDirection dir = Locomotive.locDirection.DIR_FORWARD;
                                double wert = 0;

                                if (item.has("wert"))
                                {
                                    Object given = item.get("wert");
                                    wert = given instanceof Number ? ((Number) given).doubleValue()
                                        : Double.parseDouble(String.valueOf(given).trim());
                                }

                                if (wert != 0 && wert != 1)
                                {
                                    throw new JSONException("a direction is 0 or 1");
                                }

                                if (wert == 1)
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

                                // Not used yet: "schaltzeit" (ms) and the exact "typ"

                                // Which command the item's own "sekunde" pause belongs on: the LAST one this item
                                // emits.  For a three-way at stellung 1, 2 or 3 the pair is emitted
                                // address-then-address+1, and a pause between the two drives would overwrite the gap
                                // that holds them apart.
                                // Null when a stellung emits nothing.
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
                catch (NumberFormatException | JSONException | ClassCastException e)
                {
                    JSONObject skipped = routeList.optJSONObject(i);
                    this.unreadableRecords++;

                    if (skipped != null && skipped.optInt("id", Integer.MIN_VALUE) != Integer.MIN_VALUE)
                    {
                        this.unreadableRouteIds.add(skipped.optInt("id"));
                    }
                    else
                    {
                        this.unreadableRouteWithoutId = true;
                    }

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

    public List<MarklinLocomotive> parseLocomotivesCS3(JSONArray locomotiveList)
    {
        List<MarklinLocomotive> out = new ArrayList<>();
        
        Map<String, String> internalNames = new HashMap<>();
        
        // For multi-units, we need to parse the internal CS3 locomotive names.  A record with no name or internal name is
        // warned of in the loop below.
        for (int i = 0 ; i < locomotiveList.length(); i++)
        {
            JSONObject loc = locomotiveList.optJSONObject(i);

            if (loc != null && text(loc, "name") != null && text(loc, "internname") != null)
            {
                internalNames.put(text(loc, "internname"), text(loc, "name"));
            }
        }

        boolean unnameable = hasUnnameableRecord(locomotiveList);
        
        for (int i = 0 ; i < locomotiveList.length(); i++)
        {
            try
            {
                JSONObject loc = locomotiveList.getJSONObject(i);

                // What names it for a multi-unit and a route: a record without it is skipped, as one without a uid is
                loc.getString("internname");

                Integer uid = Integer.decode(loc.getString("uid"));

                String name = loc.getString("name");
                String icon = text(loc, "icon");
                
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
                            String identifier = entry.getString("lokname");

                            if (internalNames.containsKey(identifier))
                            {
                                // New traction object has no direction flag, assume +1.0 // TODO - look at sendDirectionSpeed and traktionFunktionsMapping
                                multiUnitLocMap.put(internalNames.get(identifier), 1.0);
                            }
                            else
                            {
                                // Not found because its record would not read: the unit is skipped, keeping its members
                                if (unnameable) throw new JSONException("a member may be a record that would not read");

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
                                // Not found because its record would not read: the unit is skipped, keeping its members
                                if (unnameable) throw new JSONException("a member may be a record that would not read");

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

                // Every function must read: a locomotive with a function missing would overwrite the functions it holds
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
                    newLoc.setImageURL(this.cs3IconURL(icon));
                }
                
                if (!multiUnitLocMap.isEmpty())
                {
                    newLoc.setModelMultiUnitLocomotives(multiUnitLocMap);
                }

                out.add(newLoc);
            }
            catch (NumberFormatException | JSONException | ClassCastException e)
            {
                this.unreadableRecords++;

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
        
    /** Parses locomotives from the CS2 database / legacy file in CS3 */
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

                            // As in the DCC branch below: with no address field the UID was used, and a UID is past the
                            // highest MFX address (only setAddress validates).
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
    
    /** Download the remote CS2 layout to the filesystem */
    public void downloadCS2Layout(File localPath) throws Exception
    {
        String gleisbild = getLayoutMasterURL();

        // Write this file to localPath/config/gleisbild.cs2
        File configDir = new File(localPath, "config");
        if (!configDir.exists())
        {
            configDir.mkdirs();
        }
        
        File masterLayoutFile = new File(configDir, "gleisbild.cs2");
        copyAtomically(gleisbild, masterLayoutFile);
        
        // Process layout list and write files to localPath/config/gleisbilder/<layoutName>.cs2
        File layoutsDir = new File(configDir, "gleisbilder");
        if (!layoutsDir.exists())
        {
            layoutsDir.mkdirs();
        }
        
        // A station's copy is read as a local layout, so it is given the station's page IDs as this build reads them,
        // each arrow the ID it goes to there, and the mark that its arrows go by ID: they go where the station's do
        final boolean fromStation = isFromStation();

        List<Map<String, String>> index = parseLayoutIndex();

        for (Map<String, String> page : index)
        {
            String layoutName = page.get("name");

            // Sanitized, and matching what getLayoutURL will look for when this folder is read back.
            // The remote fetch above is unaffected: it goes through sanitizeURL on the http branch.
            File layoutFile = new File(layoutsDir, sanitizeFilename(layoutName) + ".cs2");

            if (!fromStation)
            {
                copyAtomically(getLayoutURL(layoutName), layoutFile);

                continue;
            }

            try (BufferedReader reader = fetchURL(getLayoutURL(layoutName)))
            {
                StringBuilder text = new StringBuilder();

                String line;

                while ((line = reader.readLine()) != null) text.append(line).append('\n');

                String withIds = org.traincontrol.base.LayoutDiagram.withLinkPageIds(text.toString(), keys ->
                    LayoutDiagramComponent.stationLinkPageId(LayoutDiagramComponent.pageIdFromText(keys.get("zielseite")),
                        LayoutDiagramComponent.artikelNumber(keys.get("artikel"))));

                copyAtomically(new BufferedReader(new java.io.StringReader(withIds)), layoutFile);
            }
        }

        if (fromStation)
        {
            giveTheCopyTheStationsPageIds(masterLayoutFile, index, localPath.getAbsolutePath());

            org.traincontrol.base.LayoutDiagram.markLinksByPageId(localPath.getAbsolutePath());
        }
        
        // Download the accessory file      
        File magsFile = new File(configDir, "magnetartikel.cs2");

        copyAtomically(this.getMagURL(false), magsFile);

        // And the routes (FR-021): tolerated when absent, unlike the three above, since a station with no routes
        // defined is ordinary.
        try
        {
            copyAtomically(this.getRouteURL(), new File(configDir, "fahrstrassen.cs2"));
        }
        catch (Exception noRoutes)
        {
            logMessage("No routes were downloaded from the Central Station.", noRoutes, false);
        }

        // The /config/ export above is the CS2 format, which MarklinControlStation does not trust for accessories and
        // routes on a CS3 (it reads the CS3 JSON), so a CS3 backup fetches those here.  control can be null (unit
        // tests), and isCS3() is false if the station never answered the device-info probe.
        if (this.control != null && this.control.isCS3())
        {
            // Tolerated like fahrstrassen.cs2 above: an empty accessory or route list is a real station state, not a
            // fetch problem.
            try
            {
                copyAtomically(this.getCS3MagDBUrl(), new File(configDir, "CS3_mags.json"));
            }
            catch (Exception noCS3Mags)
            {
                logMessage("No CS3 accessory data was downloaded from the Central Station.", noCS3Mags, false);
            }

            try
            {
                copyAtomically(this.getCS3RouteDBUrl(), new File(configDir, "CS3_automatics.json"));
            }
            catch (Exception noCS3Routes)
            {
                logMessage("No CS3 route data was downloaded from the Central Station.", noCS3Routes, false);
            }

            // And the locomotives (OB-099): a CS3 keeps its locomotive database only in this JSON.
            try
            {
                try (BufferedReader locos = openCS3Locos(isCS3Version260OrAbove()))
                {
                    copyAtomically(locos, new File(configDir, "CS3_locos.json"));
                }
            }
            catch (Exception noCS3Locs)
            {
                logMessage("No CS3 locomotive data was downloaded from the Central Station.",
                    noCS3Locs, false);
            }
        }
    }

    /**
     * Rewrites the `.id` of each page in a downloaded index whose ID read as a local layout would differ from the
     * station's: a station numbers pages that state none, and tells apart pages that share one, in memory only.
     */
    private static void giveTheCopyTheStationsPageIds(File copy, List<Map<String, String>> index, String folder)
        throws Exception
    {
        Map<String, Integer> local = org.traincontrol.base.LayoutDiagram.readLayoutIndexIds(folder);

        Map<String, String> wanted = new HashMap<>();

        for (Map<String, String> page : index)
        {
            if (!page.get("id").equals(String.valueOf(local.get(page.get("name"))))) wanted.put(page.get("name"), page.get("id"));
        }

        if (wanted.isEmpty()) return;

        List<String> lines = Files.readAllLines(copy.toPath(), StandardCharsets.UTF_8);

        List<String> out = new ArrayList<>();

        int start = 0;

        while (start < lines.size())
        {
            int end = start + 1;

            while (end < lines.size() && (lines.get(end).trim().isEmpty() || lines.get(end).trim().startsWith("."))) end++;

            List<String> block = new ArrayList<>(lines.subList(start, end));

            String name = null;

            for (String line : block) if (line.trim().toLowerCase(java.util.Locale.ROOT).startsWith(".name=")) name = line.trim().substring(6);

            if ("seite".equalsIgnoreCase(block.get(0).trim()) && wanted.containsKey(name))
            {
                block.removeIf(line -> line.trim().toLowerCase(java.util.Locale.ROOT).startsWith(".id="));

                block.add(1, " .id=" + wanted.get(name));
            }

            out.addAll(block);

            start = end;
        }

        org.traincontrol.util.Util.writeAtomically(copy, stream ->
        {
            for (String line : out) stream.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        });
    }

    /**
     * Fetches a URL into a file, all of it or none of it: staged through a sibling file and moved into place, so a
     * transfer that stops part way leaves the previous file.  Written as UTF-8, matching fetchURL's decoder.
     */
    private void copyAtomically(String url, File target) throws Exception
    {
        try (BufferedReader reader = fetchURL(url))
        {
            copyAtomically(reader, target);
        }
    }

    /** The same, from a reader already open. */
    private void copyAtomically(BufferedReader reader, File target) throws Exception
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
                // As an IOException, as the staging write contracts: throwing at all discards the staged file and
                // keeps the previous one.
                throw new java.io.IOException(e);
            }
        });
    }

    /**
     * The pages named in gleisbild.cs2 as name and id, in file order, so an autonomy setup survives a page rename.  A
     * page with no `.id` has id ZERO, the CS2 convention that an omitted key is zero (see
     * `LayoutDiagram.pageIdOrPosition`). A list, not a map: two pages may share a name (OB-409).
     */
    private List<Map<String, String>> parseLayoutIndex() throws Exception
    {
        final boolean fromStation = isFromStation();

        List<Map<String, String> > l = parseFile(fetchURL(getLayoutMasterURL()));

        List<Map<String, String>> out = new ArrayList<>();

        int position = 0;

        boolean statesAnId = false;

        for (Map<String, String> m : l) if ("seite".equals(m.get("_type")) && m.get("id") != null) statesAnId = true;

        for (Map<String, String> m : l)
        {
            if ("seite".equals(m.get("_type")))
            {
                position++;

                String name = m.get("name");

                if (name == null) continue;

                Map<String, String> page = new java.util.LinkedHashMap<>();
                page.put("name", name);
                // Through LayoutDiagram's rule, not a second copy: a non-numeric `.id=` must come out as the page's
                // position, as in readLayoutIndexIds.
                page.put("id", String.valueOf(
                    org.traincontrol.base.LayoutDiagram.pageIdOrPosition(m.get("id"), position)));

                // A station index that states no ID at all is numbered in file order from 0, so its first page is 0
                // either way; otherwise an absent ID is 0 and the dedupe below separates any it shares
                if (fromStation && !statesAnId) page.put("id", String.valueOf(position - 1));

                out.add(page);
            }
        }

        // The station's own file cannot be written, so two pages sharing an ID are told apart here, in memory only, by
        // the same rule every sync (name order breaks the tie)
        if (fromStation)
        {
            Map<String, Integer> byName = new java.util.LinkedHashMap<>();

            for (Map<String, String> page : out) byName.put(page.get("name"), Integer.valueOf(page.get("id")));

            Map<String, Integer> deduped = org.traincontrol.base.LayoutDiagram.dedupeIds(byName,
                new java.util.TreeMap<Integer, String>(), 0);

            for (Map<String, String> page : out) page.put("id", String.valueOf(deduped.get(page.get("name"))));
        }

        return out;
    }
    
    /** Converts input file to JSON */
    public static JSONArray parseJSONArray (BufferedReader in) throws IOException
    {
        return new JSONArray(readAndClose(in));
    }
    
    /** Converts input file to JSON */
    public static JSONObject parseJSONObject (BufferedReader in) throws IOException
    {
        return new JSONObject(readAndClose(in));
    }
    
    /** Reads the stream's lines into one string and closes the stream, which frees the connection. */
    private static String readAndClose(BufferedReader in) throws IOException
    {
        try (BufferedReader reader = in)
        {
            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null)
            {
                sb.append(line);
            }

            return sb.toString();
        }
    }
    
    /**
     * Converts a component name from the MCS2 file to an internal componentType
     * @param address - only used for CS3 switches w/o address
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
     * How many pages the last parseLayout (which resets it) could not read, so a caller holding the list can tell a
     * complete railway from most of one.
     */
    private int pagesThatCouldNotBeRead;

    // How many pages the index named, which is what "did anything read" is asked against.
    private int pagesTheIndexNamed;

    public int getPagesThatCouldNotBeRead()
    {
        return this.pagesThatCouldNotBeRead;
    }

    /**
     * How many pages the last parseLayout's index NAMED, whether or not any arrived.  An index this parser does not
     * understand yields zero pages AND zero failures, so the caller's "did anything read" guard asks this.
     */
    public int getPagesTheIndexNamed()
    {
        return this.pagesTheIndexNamed;
    }

    public List<LayoutDiagram> parseLayout(List<MarklinAccessory> accDB) throws Exception
    {
        this.pagesThatCouldNotBeRead = 0;

        this.pagesTheIndexNamed = 0;

        // How a file word maps to a type, handed to the component class so its export can tell "still what was read"
        // from "redrawn by the user".  The address is irrelevant to that: the entries that depend on it differ only in
        // a permanent crossing, which is a different type either way.
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

        this.pagesTheIndexNamed = names.size();
        
        List<LayoutDiagram> out = new ArrayList<>();
        
        // by position, not by looking the name up: two pages may share a name, and a lookup would give
        // them both the first one's id
        int pageIndex = -1;

        // Addresses with articles of two protocols, which a CS3 can hold; resolveTwoProtocolTiles handles them (OB-402)
        Map<Integer, String> twoProtocols = control != null && control.isCS3() ? addressesWithTwoProtocols(accDB)
            : new HashMap<>();
        this.trackPlan = null;
        
        for (String name : names)
        {
            pageIndex++;

            // One page that will not parse costs THAT page, not the layout: the caller answers a failed parse by
            // clearing the local- layout folder preference.  pageIndex advances ABOVE this, so a skipped page does not
            // renumber the rest (autonomy keys on page id).
            try
            {
 
                String url = getLayoutURL(name);
            
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

                List<LayoutDiagramComponent> unresolved = new ArrayList<>();
                        
                for (Map<String, String> m : l)
                {
                    // The left-margin keys (`page=1`), which are not a block and must not be written as one.
                    if ("_bareKeys".equals(m.get("_type")))
                    {
                        for (Map.Entry<String, String> bare : m.entrySet())
                        {
                            layout.addBareKey(bare.getKey(), bare.getValue());
                        }

                        continue;
                    }

                    // The blocks above the elements (version, groesse, whatever a later firmware adds), kept so that
                    // saving the page does not delete them.
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

                            // Whatever else the file said about this square: saving regenerates the page from the
                            // model, so a key nobody carries would be deleted from the user's diagram.
                            LayoutDiagramComponent added = layout.getComponent(x, y);

                            if (added != null)
                            {
                                added.setUnmodelledKeys(m);

                                // The page a link goes to, when the file gives its ID
                                added.setLinkPageIdFromFile(m.get("zielseite"), m.get("zielartikel"));

                                // And the file's own word for the type: the type mapping is many-to-one, so writing the
                                // canonical word back would collapse every variant the file distinguished.
                                added.setOriginalTyp(m.get("typ"));

                                if (twoProtocols.containsKey(address) && m.get("prot") == null
                                    && added.isAccessory())
                                {
                                    unresolved.add(added);
                                }
                            }
                        }
                        else
                        {
                            // Not a component this program knows, so not one it may delete: kept verbatim and put back
                            // on save.
                            layout.addUnmodelledElement(m);
                        }
                    }
                }
            
                layout.checkBounds();

                if (!unresolved.isEmpty())
                {
                    resolveTwoProtocolTiles(layout, unresolved, twoProtocols, accDB);
                }
            
                out.add(layout);
            }
            catch (Exception | Error bad)
            {
                // Counted and logged, so a caller that prunes or reverts knows it is not looking at the whole railway.
                this.pagesThatCouldNotBeRead++;

                logMessage(I18n.f("layout.warningPageCouldNotBeRead", name, String.valueOf(bad)),
                    null, false);

                // The page keeps its place in the name-sorted list (a link tile holds a position in it) as a blank page with a
                // label saying it could not be loaded, carrying the page's id so its autonomy setup stays attached.
                // Marked unreadable so nothing saves the blank over the file being recovered (OB-408).
                try
                {
                    LayoutDiagram missing = new LayoutDiagram(name, 16, 8, getLayoutURL(name),
                        this.control);

                    missing.setPageId(index.get(pageIndex).get("id"));

                    missing.markUnreadable();

                    missing.addComponent(LayoutDiagramComponent.componentType.TEXT, 1, 1, 0, 0, 0, -1,
                        null, I18n.f("layout.pageCouldNotBeReadTile", name));

                    out.add(missing);
                }
                catch (Exception | Error worse)
                {
                    // The placeholder is a courtesy: failing to build one must not cost the rest of the layout.
                    logMessage(I18n.f("layout.warningPageCouldNotBeRead", name, String.valueOf(worse)),
                        null, true);
                }
            }
        }
        
        return out;
    }

    // The CS3's own track plans (/app/api/gbs): fetched at most once per parse, and only for an address with two
    // protocols
    private CS3TrackPlan trackPlan;

    // The addresses with two protocols this parser has warned about, by address and page, so a refresh after each edit
    // does not repeat them
    private final java.util.Set<String> twoProtocolsWarned = new java.util.HashSet<>();

    /** The addresses with articles of more than one protocol, each naming them ("W 14 DCC, W 15 MM2"). */
    private static Map<Integer, String> addressesWithTwoProtocols(List<MarklinAccessory> accDB)
    {
        Map<Integer, String> out = new HashMap<>();

        accDB.stream().collect(Collectors.groupingBy(MarklinAccessory::getAddress)).forEach((address, articles) ->
        {
            if (articles.stream().map(MarklinAccessory::getDecoderType).distinct().count() > 1)
            {
                out.put(address, articles.stream().map(a -> a.getName() + " " + a.getDecoderType())
                    .collect(Collectors.joining(", ")));
            }
        });

        return out;
    }

    /**
     * Gives each tile at an address with two protocols the protocol of the article this page's CS3 plan puts there; a
     * tile it cannot tell keeps the first article's, warned once per page.  A local copy is never checked (OB-402).
     */
    private void resolveTwoProtocolTiles(LayoutDiagram page, List<LayoutDiagramComponent> tiles,
        Map<Integer, String> twoProtocols, List<MarklinAccessory> accDB)
    {
        boolean fromStation = getDefaultLayoutDataLoc().equals(this.layoutDataLoc);
        CS3TrackPlan.Page plan = fromStation ? trackPlan().page(page.getName()) : null;

        // The grid's place on the page, voted by the articles of the addresses with one protocol
        Map<String, List<Point>> voters = new HashMap<>();

        for (MarklinAccessory a : accDB)
        {
            if (plan != null && plan.isGrid() && !twoProtocols.containsKey(a.getAddress()))
            {
                voters.merge(a.getName(), page.getAll().stream()
                    .filter(t -> t.isAccessory() && t.getAddress() == a.getAddress())
                    .map(t -> new Point(t.getX(), t.getY())).collect(Collectors.toList()),
                    (x, y) -> { List<Point> both = new ArrayList<>(x); both.addAll(y); return both; });
            }
        }

        Point offset = plan == null ? null : plan.gridOffset(voters);

        // The squares of each address that could not be told, and the protocol its untold tiles keep
        Map<Integer, List<String>> untold = new java.util.TreeMap<>();
        Map<Integer, Accessory.accessoryDecoderType> kept = new HashMap<>();

        for (LayoutDiagramComponent c : tiles)
        {
            List<MarklinAccessory> onPage = accDB.stream()
                .filter(a -> a.getAddress() == c.getAddress() && plan != null && plan.holds(a.getName()))
                .collect(Collectors.toList());

            if (onPage.stream().map(MarklinAccessory::getDecoderType).distinct().count() > 1 && offset != null)
            {
                onPage.removeIf(a -> !plan.isOn(a.getName(), new Point(c.getX() - offset.x, c.getY() - offset.y)));
            }

            if (!onPage.isEmpty() && onPage.stream().map(MarklinAccessory::getDecoderType).distinct().count() == 1)
            {
                c.setProtocol(onPage.get(0).getDecoderType());
            }
            else
            {
                untold.computeIfAbsent(c.getAddress(), k -> new ArrayList<>()).add(c.getX() + "," + c.getY());
                kept.put(c.getAddress(), c.getProtocol());
            }
        }

        for (Map.Entry<Integer, List<String>> e : untold.entrySet())
        {
            if (!this.twoProtocolsWarned.add(e.getKey() + " " + page.getName()))
            {
                continue;
            }

            logMessage(I18n.f(fromStation ? "layout.cs3TwoProtocolsUnresolved" : "layout.cs3TwoProtocolsNotChecked",
                twoProtocols.get(e.getKey()), e.getKey(), page.getName(), String.join(" ", e.getValue()),
                kept.get(e.getKey())));
        }
    }

    /** The station's track plans, read once per parse; a failed read gives a plan with no pages. */
    private CS3TrackPlan trackPlan()
    {
        if (this.trackPlan == null)
        {
            JSONObject gbs = null;

            try (BufferedReader in = fetchURL(getCS3LayoutUrl()))
            {
                gbs = parseJSONObject(in);
            }
            catch (Exception e)
            {
                logMessage(I18n.f("layout.cs3TrackPlanUnreadable", e), e, true);
            }

            this.trackPlan = new CS3TrackPlan(gbs);
        }

        return this.trackPlan;
    }

    /**
    * Checks if a there is a connection
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
