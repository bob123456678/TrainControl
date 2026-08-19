package org.traincontrol.automationui;

import org.traincontrol.util.I18n;
import org.traincontrol.base.LayoutDiagram;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.util.Util;

/**
 * What the diagram cannot say: the names, the stations, the lengths, the directions, and where the
 * locomotives go.
 *
 * Everything geometric is re-derived from the track on every build, so nothing here describes shape.
 * What it holds is the handful of decisions a person made that no amount of reading the diagram would
 * recover - which sensor is a station, what to call it, which way trains may run, how long a piece of
 * track counts as - plus the named configurations that vary only in where the locomotives start.
 *
 * The files live inside the track diagram folder, so the setup travels with the layout it describes:
 *
 *   config/autonomy/setup.json                 one per layout: names, stations, lengths, directions,
 *                                              portal pairings, excluded pages, which configuration
 *                                              is active
 *   config/autonomy/configuration-<name>.json  one per configuration: point properties, placements,
 *                                              homes, exclusions, globals, timetable
 *
 * One file per configuration rather than one file holding them all, because it makes the operations
 * users actually perform cheap and obvious: duplicating a configuration is a file copy, and a
 * configuration can be handed to somebody else on its own.
 *
 * @author Adam
 */
public class AutonomyCompanionStore
{
    /**
     * The schema this class writes.  A file claiming a higher version was written by a newer
     * TrainControl and is refused rather than read partially - silently dropping fields it does not
     * recognise would lose the user's work on the next save.
     */
    public static final int VERSION = 1;

    public static final String ERROR_VERSION = "autosetup.ui.errorCompanionVersion";
    public static final String ERROR_NOT_LOCAL = "autosetup.ui.errorAutonomyNeedsLocalLayout";
    public static final String ERROR_NAME_IN_USE = "autosetup.ui.errorNameInUse";

    private static final String FOLDER = "config/autonomy";
    private static final String SETUP_FILE = "setup.json";
    private static final String CONFIGURATION_PREFIX = "configuration-";

    private final File layoutFolder;

    // --- shared: one copy per layout, describing the physical diagram ---------------------------
    private final Map<String, String> pointNames = new LinkedHashMap<>();
    private final Set<String> stations = new LinkedHashSet<>();
    private final Map<String, Integer> tileLengths = new LinkedHashMap<>();
    private final Map<String, String> tileDirections = new LinkedHashMap<>();

    /**
     * Which sides a station refuses to let trains ARRIVE by, keyed by square.
     *
     * The BARRED sides rather than the allowed ones, because the default is that a train may arrive
     * from anywhere - so a station nobody has restricted stores nothing at all, and a side added to the
     * diagram later is open, which is what somebody who never opened this setting would expect.  Stored
     * the other way round, every station would need an entry and every new piece of track would arrive
     * shut.
     *
     * A comma-separated list of side names, so it reads plainly in the file and needs no schema of its
     * own.
     */
    private final Map<String, String> barredArrivals = new LinkedHashMap<>();

    /**
     * Which sides trains may not arrive by.
     *
     * @param tile the station's square
     * @return the barred sides, empty when the station takes trains from anywhere
     */
    public Set<TilePorts.Side> getBarredArrivals(TileKey tile)
    {
        Set<TilePorts.Side> out = new LinkedHashSet<>();

        String stored = tile == null ? null : barredArrivals.get(tile.toString());

        if (stored == null) return out;

        for (String name : stored.split(","))
        {
            if (name.trim().isEmpty()) continue;

            try
            {
                out.add(TilePorts.Side.valueOf(name.trim()));
            }
            catch (IllegalArgumentException e)
            {
                // A side name this build does not have.  Skipped rather than refused: the rest of the
                // setting is still good, and refusing the file would cost everything else in it over
                // one word.
            }
        }

        return out;
    }

    /**
     * @param tile the station's square
     * @param barred the sides trains may not arrive by, empty or null to take them from anywhere
     */
    public void setBarredArrivals(TileKey tile, Set<TilePorts.Side> barred)
    {
        if (tile == null) return;

        if (barred == null || barred.isEmpty())
        {
            // Nothing is stored for the default, so a setup nobody has touched has nothing to
            // reconcile, and a square whose restriction is lifted stops carrying one rather than
            // carrying an empty one.
            barredArrivals.remove(tile.toString());

            return;
        }

        StringBuilder text = new StringBuilder();

        for (TilePorts.Side side : barred)
        {
            if (text.length() > 0) text.append(",");

            text.append(side.name());
        }

        barredArrivals.put(tile.toString(), text.toString());
    }

    /**
     * @return every square carrying an arrival restriction, against the sides it bars
     */
    public Map<TileKey, Set<TilePorts.Side>> getBarredArrivals()
    {
        Map<TileKey, Set<TilePorts.Side>> out = new LinkedHashMap<>();

        for (String key : barredArrivals.keySet())
        {
            TileKey tile = parseTileKey(key);

            if (tile != null) out.put(tile, getBarredArrivals(tile));
        }

        return out;
    }
    private final Map<String, String> portals = new LinkedHashMap<>();

    /**
     * The signal that protects each station, keyed by the station's square.
     *
     * One per station, and paired by hand rather than inferred.  The nearest signal on the approach is
     * not always the protecting one, and a wrong guess here throws a real signal on real hardware.
     *
     * Keyed like the portals - square to square - so it survives a page rename for the same reason
     * they do, and so it is reconciled away when either tile goes.
     */
    private final Map<String, String> stationSignals = new LinkedHashMap<>();

    /**
     * @param station the station's square
     * @return the square of the signal protecting it, or null
     */
    public TileKey getProtectingSignal(TileKey station)
    {
        if (station == null) return null;

        return parseTileKey(stationSignals.get(station.toString()));
    }

    /**
     * @param station the station's square
     * @param signal the signal's square, or null to unpair
     */
    public void setProtectingSignal(TileKey station, TileKey signal)
    {
        if (station == null) return;

        if (signal == null)
        {
            stationSignals.remove(station.toString());
        }
        else
        {
            stationSignals.put(station.toString(), signal.toString());
        }
    }

    /**
     * @return every station that has a protecting signal, against that signal's square
     */
    public Map<TileKey, TileKey> getProtectingSignals()
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : stationSignals.entrySet())
        {
            TileKey station = parseTileKey(entry.getKey());
            TileKey signal = parseTileKey(entry.getValue());

            if (station != null && signal != null) out.put(station, signal);
        }

        return out;
    }

    /**
     * Where each station caption is drawn, and which square it is about: caption square -> sensor square.
     *
     * Kept here rather than in the diagram, which is what makes a caption a thing rather than a piece of
     * text that happens to match a name.  As a label reading "Point:Bahnhof" it bound to a Point BY NAME
     * and therefore broke in every direction: a rename had to rewrite every page showing it, a station
     * split into several Points was called none of them, and a name that no longer existed left a label
     * that looked live and did nothing.  Keyed by square, none of that can happen - renaming is free,
     * because the caption never knew the name in the first place.
     *
     * It also means autonomy no longer writes to the layout file at all, which is where the worst defect
     * in this feature lived: saving regenerated the page and deleted anything the parser could not model.
     */
    private final Map<String, String> captions = new LinkedHashMap<>();
    private final Map<String, String> linkNames = new LinkedHashMap<>();
    private final Set<String> excludedPages = new LinkedHashSet<>();

    private String activeConfiguration = null;

    // Anything a newer version wrote that this one does not understand, kept so a round trip through an
    // older TrainControl does not quietly delete it
    private final Map<String, Object> unknownSharedFields = new LinkedHashMap<>();

    private final Map<String, JSONObject> configurations = new LinkedHashMap<>();

    // Pages are keyed on disk by the id the Central Station gave them, not by their name.  A name is
    // what a user changes on a whim; an id is what gleisbild.cs2 has always used to identify a page, so
    // it survives the rename that would otherwise orphan every entry on that page at once.
    private final Map<String, String> pageNameToId = new LinkedHashMap<>();
    private final Map<String, String> pageIdToName = new LinkedHashMap<>();

    // The name each page id had when the setup was last written, so a renumber can be told from a rename
    private final Map<String, String> pageNamesWhenWritten = new LinkedHashMap<>();
    private final Map<String, String> pageIdConflicts = new LinkedHashMap<>();

    /**
     * @param layoutFolder the local layout folder - the one holding config/gleisbilder
     */
    public AutonomyCompanionStore(File layoutFolder)
    {
        this.layoutFolder = layoutFolder;
    }

    /**
     * Tells the store which page id belongs to which page name.
     *
     * Entries are stored against the page ID, not the page name.  A name is something a user renames on
     * a whim, and every key here begins with one, so a rename would otherwise orphan a whole page of
     * names, lengths, directions and pairings at once - with nothing to connect the loss to the rename.
     *
     * The id is NOT trusted on its own.  The Central Station orders pages by it, so reordering them
     * there could renumber the pages - and a renumber would silently reattach a page of settings to the
     * WRONG page, which is worse than losing them, because nothing looks wrong.  So the name each id had
     * is recorded alongside, and a mismatch is reported at load rather than acted on.
     *
     * @param nameToId from the parsed layouts, LayoutDiagram.getPageId()
     */
    public void setPageIds(Map<String, String> nameToId)
    {
        pageNameToId.clear();
        pageIdToName.clear();

        if (nameToId == null) return;

        for (Map.Entry<String, String> entry : nameToId.entrySet())
        {
            if (entry.getValue() == null) continue;

            pageNameToId.put(entry.getKey(), entry.getValue());
            pageIdToName.put(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Pages whose id now belongs to a different name than when the setup was written.
     *
     * Empty in normal use.  A page renamed keeps its id and appears here not at all - that is the point.
     * A page RENUMBERED appears here, and its settings must not be adopted blindly, because they belong
     * to whatever page used to hold that id.
     *
     * @return recorded name -> name that id now has
     */
    public Map<String, String> getPageIdConflicts()
    {
        return Collections.unmodifiableMap(pageIdConflicts);
    }

    /**
     * Whether this layout can hold an autonomy setup at all.
     *
     * Autonomy is local-layout only: the files live beside the diagram, so there has to be a diagram
     * folder to put them in.  A layout read from the Central Station has none until it is downloaded.
     * @return
     */
    public boolean isUsable()
    {
        return layoutFolder != null && layoutFolder.isDirectory();
    }

    /**
     * Whether a setup has been created for this layout yet.
     * @return
     */
    public boolean exists()
    {
        return isUsable() && setupFile().isFile();
    }

    // --- loading and saving -----------------------------------------------------------------------

    /**
     * Reads the setup and every configuration beside it.  A layout with no setup loads as empty rather
     * than failing: that is a layout nobody has set autonomy up on yet.
     *
     * @throws IOException if a file exists but cannot be read or understood
     */
    public void load() throws IOException
    {
        if (!exists())
        {
            clear();
            return;
        }

        // Read and parse BEFORE anything is thrown away.
        //
        // This used to clear first, so a file that could not be read - a sync lock on the folder, a
        // truncated write, a setup written by a newer TrainControl - left the store empty and the
        // failure reported as though nothing had happened.  The caller then had a live, blank store:
        // every station, name and direction gone from the screen, and one press of Save away from being
        // gone from the disk as well.  A load that fails now leaves the setup exactly as it was.
        String contents = new String(
            Files.readAllBytes(setupFile().toPath()), StandardCharsets.UTF_8);

        JSONObject root;

        try
        {
            root = new JSONObject(contents);
        }
        catch (org.json.JSONException e)
        {
            throw new IOException(String.valueOf(e.getMessage()), e);
        }

        int version = root.optInt("version", VERSION);

        if (version > VERSION)
        {
            throw new IOException(ERROR_VERSION + " (" + version + " > " + VERSION + ")");
        }

        // The configurations are read and parsed before anything is thrown away too.
        //
        // The rule above was applied to setup.json and stopped there, so a locked or corrupt
        // configuration-*.json still emptied the store and then failed part way through refilling it -
        // the same live, half-loaded state, arrived at by the same route.  Worse for a corrupt one: a
        // bare JSONException is unchecked and walked straight out through every catch (IOException)
        // that guards discardEdits and open, which expect a failed load to change nothing.
        Map<String, JSONObject> loaded = new LinkedHashMap<>();

        File[] files = folder().listFiles();

        if (files != null)
        {
            for (File file : files)
            {
                String name = file.getName();

                if (!name.startsWith(CONFIGURATION_PREFIX) || !name.endsWith(".json")) continue;

                JSONObject configuration;

                try
                {
                    configuration = new JSONObject(
                        new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                }
                catch (org.json.JSONException e)
                {
                    // Named, and as an IOException.  Which file is unreadable is the only thing that
                    // tells the user what to do about it, and a load failure is something the callers
                    // already know how to survive - as long as it arrives in the form they catch.
                    throw new IOException(
                        I18n.f("autosetup.ui.errorConfigurationUnreadable", name,
                            String.valueOf(e.getMessage())), e);
                }

                loaded.put(
                    configuration.optString("name",
                        name.substring(CONFIGURATION_PREFIX.length(), name.length() - 5)),
                    configuration);
            }
        }

        clear();

        readShared(root);

        activeConfiguration = root.optString("activeConfiguration", null);

        configurations.putAll(loaded);

        if (activeConfiguration != null && !configurations.containsKey(activeConfiguration))
        {
            activeConfiguration = null;
        }

        if (activeConfiguration == null && !configurations.isEmpty())
        {
            activeConfiguration = configurations.keySet().iterator().next();
        }
    }

    /**
     * Writes the setup and every configuration.
     *
     * Written through Util.writeAtomically, for the same reason the locomotive database is: this is the
     * operator's accumulated work, and a half-written file would read as a layout nobody had set up.
     *
     * @throws IOException
     */
    public void save() throws IOException
    {
        if (!isUsable()) throw new IOException(ERROR_NOT_LOCAL);

        folder().mkdirs();

        final JSONObject root = sharedFields();

        // written first so a human opening the file meets the readable part before the coordinate maps
        if (activeConfiguration != null) root.put("activeConfiguration", activeConfiguration);

        writeJson(setupFile(), root);

        for (Map.Entry<String, JSONObject> entry : configurations.entrySet())
        {
            JSONObject configuration = entry.getValue();
            configuration.put("name", entry.getKey());

            writeJson(configurationFile(entry.getKey()), configuration);
        }
    }

    // --- shared data ------------------------------------------------------------------------------

    public String getPointName(TileKey tile)
    {
        return pointNames.get(tile.toString());
    }

    /**
     * Every square somebody has named, by its key.
     *
     * Needed by anything that has to find a station by NAME across the whole layout rather than across
     * the reduction - the reduction omits excluded pages, so asking it makes a station on one of those
     * look as though it does not exist.
     *
     * @return an unmodifiable view
     */
    public Map<String, String> getPointNames()
    {
        return Collections.unmodifiableMap(pointNames);
    }

    public void setPointName(TileKey tile, String name)
    {
        if (name == null || name.trim().isEmpty())
        {
            pointNames.remove(tile.toString());
        }
        else
        {
            pointNames.put(tile.toString(), name.trim());
        }
    }

    public boolean isStation(TileKey tile)
    {
        return stations.contains(tile.toString());
    }

    public void setStation(TileKey tile, boolean station)
    {
        if (station)
        {
            stations.add(tile.toString());
        }
        else
        {
            stations.remove(tile.toString());
        }
    }

    /**
     * @param tile
     * @return the length assigned to this tile, 0 if none
     */
    public int getTileLength(TileKey tile)
    {
        Integer value = tileLengths.get(tile.toString());

        return value == null ? 0 : value;
    }

    /**
     * Lengths of 0 are not stored.  A layout where nobody has assigned any adds nothing to the file, and
     * 0 is what an unassigned tile means anyway.
     * @param tile
     * @param length
     */
    public void setTileLength(TileKey tile, int length)
    {
        if (length <= 0)
        {
            tileLengths.remove(tile.toString());
        }
        else
        {
            tileLengths.put(tile.toString(), length);
        }
    }

    public Direction getTileDirection(TileKey tile, RouteId routeId)
    {
        String value = tileDirections.get(directionKey(tile, routeId));

        if (value == null) return null;

        try
        {
            return Direction.valueOf(value);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    /**
     * Records a direction.  Null clears it, returning the route to whatever the graph defaults it to -
     * which is not always BOTH, so a default is never written as if it were a choice.
     * @param tile
     * @param routeId
     * @param direction
     */
    public void setTileDirection(TileKey tile, RouteId routeId, Direction direction)
    {
        if (direction == null)
        {
            tileDirections.remove(directionKey(tile, routeId));
        }
        else
        {
            tileDirections.put(directionKey(tile, routeId), direction.name());
        }
    }

    public String getLinkName(TileKey tile)
    {
        return linkNames.get(tile.toString());
    }

    public void setLinkName(TileKey tile, String name)
    {
        if (name == null || name.trim().isEmpty())
        {
            linkNames.remove(tile.toString());
        }
        else
        {
            linkNames.put(tile.toString(), name.trim());
        }
    }

    /**
     * Links autonomy is to ignore, by tile.
     *
     * Stored with the track rather than with a configuration: whether a link is part of the railway
     * autonomy runs is a fact about the diagram, and it would be strange for one configuration to see
     * a hole in the track that another does not.
     */
    private final Set<String> disabledPortals = new LinkedHashSet<>();

    public boolean isPortalDisabled(TileKey tile)
    {
        return disabledPortals.contains(tile.toString());
    }

    public void setPortalDisabled(TileKey tile, boolean disabled)
    {
        if (disabled)
        {
            disabledPortals.add(tile.toString());
        }
        else
        {
            disabledPortals.remove(tile.toString());
        }
    }

    public TileKey getPortalPartner(TileKey tile)
    {
        return parseTileKey(portals.get(tile.toString()));
    }

    /**
     * Pairs two portals, mutually and exclusively.  Whatever either was paired with before is released,
     * so a pairing can be changed without leaving a stranded half behind.
     * @param a
     * @param b
     */
    public void pairPortals(TileKey a, TileKey b)
    {
        unpairPortal(a);
        unpairPortal(b);

        portals.put(a.toString(), b.toString());
        portals.put(b.toString(), a.toString());
    }

    public void unpairPortal(TileKey tile)
    {
        String partner = portals.remove(tile.toString());

        if (partner != null) portals.remove(partner);
    }

    public Set<String> getExcludedPages()
    {
        return Collections.unmodifiableSet(excludedPages);
    }

    public void setPageExcluded(String page, boolean excluded)
    {
        if (excluded)
        {
            excludedPages.add(page);
        }
        else
        {
            excludedPages.remove(page);
        }
    }

    // --- configurations ---------------------------------------------------------------------------

    public List<String> getConfigurationNames()
    {
        return new ArrayList<>(configurations.keySet());
    }

    public String getActiveConfiguration()
    {
        return activeConfiguration;
    }

    /**
     * Records which configuration is in use.  This is what loads on the next start, so it is set
     * whenever one is loaded rather than being a separate thing to remember.
     * @param name
     */
    public void setActiveConfiguration(String name)
    {
        if (name == null || configurations.containsKey(name)) activeConfiguration = name;
    }

    public JSONObject getConfiguration(String name)
    {
        return configurations.get(name);
    }

    /**
     * Everything this setup holds that is not one configuration: the decisions about the TRACK.
     *
     * Station names, which squares are stations, lengths, directions, portals, captions, link names and
     * the excluded pages.  Named apart from save() because two more callers need exactly this set and
     * had no way to ask for it - which is how exporting came to carry a configuration and none of the
     * things that configuration refers to.
     *
     * @return
     */
    private JSONObject sharedFields()
    {
        JSONObject root = new JSONObject();

        root.put("version", VERSION);

        // what each id was called when this was written, so a renumber can be told from a rename
        Map<String, String> pages = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : pageIdToName.entrySet())
        {
            pages.put(entry.getKey(), entry.getValue());
        }

        root.put("pages", new JSONObject(pages));

        root.put("pointNames", new JSONObject(translateKeys(pointNames, true)));
        root.put("stations", new JSONArray(translateSet(stations)));
        root.put("tileLengths", new JSONObject(translateLengths()));
        root.put("tileDirections", new JSONObject(translateKeys(tileDirections, true)));
        root.put("barredArrivals", new JSONObject(translateKeys(barredArrivals, true)));
        root.put("stationSignals", new JSONObject(translateTileMap(stationSignals)));
        root.put("portals", new JSONObject(translatePortals()));
        root.put("captions", new JSONObject(translateTileMap(captions)));
        root.put("linkNames", new JSONObject(translateKeys(linkNames, true)));
        root.put("excludedPages", new JSONArray(excludedPages));
        root.put("disabledLinks", new JSONArray(translateSet(disabledPortals)));

        // written back last, so a field this build does not model survives a round trip
        for (Map.Entry<String, Object> entry : unknownSharedFields.entrySet())
        {
            root.put(entry.getKey(), entry.getValue());
        }

        return root;
    }

    /**
     * The key an exported file carries its configuration under.  Its presence is what tells the two
     * formats apart.
     */
    public static final String EXPORT_CONFIGURATION = "configuration";

    /**
     * The key an exported file carries the track decisions under.
     */
    public static final String EXPORT_SHARED = "shared";

    /**
     * A configuration together with the track decisions it refers to, ready to be written to a file.
     *
     * Exporting used to write the configuration alone.  But a configuration is a set of placements,
     * homes and exclusions against POINTS, and what makes a square a point, what it is called, how long
     * it is and which way it runs all live in the shared half - so the file named things the receiving
     * setup had never heard of.  Importing it into a fresh setup produced a configuration referring
     * entirely to nothing, and looked exactly like having lost the names.
     *
     * @param name
     * @return null when there is no such configuration
     */
    public JSONObject exportBundle(String name)
    {
        JSONObject configuration = configurations.get(name);

        if (configuration == null) return null;

        JSONObject bundle = new JSONObject();

        bundle.put("version", VERSION);
        bundle.put(EXPORT_CONFIGURATION, new JSONObject(configuration.toString()));
        bundle.put(EXPORT_SHARED, sharedFields());

        return bundle;
    }

    /**
     * Brings in an exported file, in either the bundled form or the bare configuration written before.
     *
     * The shared half is merged rather than adopted: an entry the local setup already has is kept, and
     * only the gaps are filled.  That way importing onto a fresh setup restores everything, importing
     * onto a working one cannot silently rename somebody's stations, and either way the result is the
     * union - which is what "the same layout, somebody else's configuration" means.
     *
     * @param name what to call the configuration here
     * @param file the parsed export
     * @return how many shared entries were filled in
     */
    public int importBundle(String name, JSONObject file)
    {
        JSONObject configuration = file.optJSONObject(EXPORT_CONFIGURATION);

        // The bare form, written before exporting carried the shared half
        if (configuration == null)
        {
            importConfiguration(name, file);
            return 0;
        }

        importConfiguration(name, configuration);

        JSONObject incoming = file.optJSONObject(EXPORT_SHARED);

        if (incoming == null) return 0;

        JSONObject merged = sharedFields();

        int filled = 0;

        for (String key : incoming.keySet())
        {
            if ("version".equals(key)) continue;

            Object value = incoming.get(key);

            if (value instanceof JSONObject)
            {
                JSONObject mine = merged.optJSONObject(key);

                if (mine == null)
                {
                    merged.put(key, value);
                    filled += ((JSONObject) value).length();
                    continue;
                }

                for (String inner : ((JSONObject) value).keySet())
                {
                    // Kept, not replaced.  See the note above: this is a merge, not an adoption.
                    if (mine.has(inner)) continue;

                    mine.put(inner, ((JSONObject) value).get(inner));
                    filled++;
                }
            }
            else if (value instanceof JSONArray)
            {
                JSONArray mine = merged.optJSONArray(key);

                if (mine == null)
                {
                    merged.put(key, value);
                    filled += ((JSONArray) value).length();
                    continue;
                }

                Set<Object> already = new LinkedHashSet<>();

                for (int i = 0; i < mine.length(); i++) already.add(mine.get(i));

                for (int i = 0; i < ((JSONArray) value).length(); i++)
                {
                    Object entry = ((JSONArray) value).get(i);

                    if (already.contains(entry)) continue;

                    mine.put(entry);
                    filled++;
                }
            }
            else if (!merged.has(key))
            {
                merged.put(key, value);
                filled++;
            }
        }

        if (filled > 0)
        {
            // Through the same door load() uses, so the page-id translation happens once and here
            clearShared();
            readShared(merged);
        }

        return filled;
    }

    /**
     * Empties the shared half, leaving the configurations alone.
     */
    private void clearShared()
    {
        pointNames.clear();
        stations.clear();
        tileLengths.clear();
        tileDirections.clear();
        barredArrivals.clear();
        stationSignals.clear();
        portals.clear();
        captions.clear();
        linkNames.clear();
        excludedPages.clear();
        disabledPortals.clear();
        unknownSharedFields.clear();
        pageNamesWhenWritten.clear();
        pageIdConflicts.clear();
    }

    /**
     * Creates a configuration, optionally as a copy of an existing one.
     *
     * A copy takes everything - point properties, placements, homes, exclusions, globals, timetable -
     * because a configuration exists to differ in where the locomotives are, and starting from a blank
     * one would mean re-entering every decision that had nothing to do with that.
     *
     * @param name
     * @param copyFrom the configuration to copy, or null for an empty one
     */
    public void createConfiguration(String name, String copyFrom) throws IOException
    {
        // Same reason as renameConfiguration: duplicating onto an existing name replaced it silently.
        if (configurations.containsKey(name)) throw new IOException(ERROR_NAME_IN_USE);

        JSONObject source = copyFrom == null ? null : configurations.get(copyFrom);

        JSONObject created = source == null
            ? new JSONObject()
            : new JSONObject(source.toString());

        created.put("name", name);

        configurations.put(name, created);

        if (activeConfiguration == null) activeConfiguration = name;
    }

    /**
     * Brings in a configuration from a file somebody exported, under the given name.
     *
     * The counterpart of handing getConfiguration() to a file: the two together are what let a
     * configuration travel between people running the same layout.  The name comes from the caller
     * rather than the file, so importing does not silently overwrite whatever happened to share the
     * exporter's name.
     *
     * @param name what to call it here
     * @param configuration the exported object
     */
    public void importConfiguration(String name, JSONObject configuration)
    {
        JSONObject imported = new JSONObject(configuration.toString());

        imported.put("name", name);

        configurations.put(name, imported);

        if (activeConfiguration == null) activeConfiguration = name;
    }

    public void renameConfiguration(String from, String to) throws IOException
    {
        // Refused rather than allowed to overwrite: renaming A to an existing B used to replace B and
        // then delete B's file, destroying a configuration the user never named in the gesture.
        if (!from.equals(to) && configurations.containsKey(to))
        {
            throw new IOException(ERROR_NAME_IN_USE);
        }

        JSONObject configuration = configurations.remove(from);

        if (configuration == null) return;

        configuration.put("name", to);
        configurations.put(to, configuration);

        if (from.equals(activeConfiguration)) activeConfiguration = to;

        File old = configurationFile(from);

        if (old.isFile()) old.delete();
    }

    /**
     * Deletes a configuration, refusing the last one: a layout with a setup but no configuration is a
     * state nothing in the UI could act on.
     * @param name
     * @throws IOException if it is the only configuration left
     */
    public void deleteConfiguration(String name) throws IOException
    {
        if (!configurations.containsKey(name)) return;

        // The FILE decides, because the file is what comes back.
        //
        // load() rebuilds the list by scanning the folder rather than from setup.json, so a file that
        // could not be deleted - held open by a sync client, which this project lives under - returned
        // next session as though nothing had happened, and would even reappear inside a setup created
        // later.  Refusing here keeps what is in memory and what is on disk saying the same thing, and
        // tells the user which is which.
        File file = configurationFile(name);

        if (file.isFile() && !file.delete())
        {
            // A sentence, because the caller shows this as the whole dialog.  It used to be the bare
            // filename, so a user who could not delete a configuration was shown a window saying only
            // "configuration-Yard.json" and left to work out both what had happened and what to do.
            throw new IOException(I18n.f("autosetup.ui.errorDeleteConfigurationFailed", name));
        }

        configurations.remove(name);

        if (name.equals(activeConfiguration))
        {
            // The last one may go now.  It used to be refused, on the reasoning that a setup with no
            // configurations is a state nothing could act on - but that made setting autonomy up a
            // one-way door: a layout somebody had experimented on kept a configuration for ever.  With
            // none left there is simply nothing active, which is the state every layout starts in and
            // which the rest of this class has always handled.
            activeConfiguration = configurations.isEmpty()
                ? null : configurations.keySet().iterator().next();
        }
    }

    /**
     * Removes the whole setup: every configuration, every decision, and the files holding them.
     *
     * Offered because until now there was no way back out of having set autonomy up.  Configurations
     * could be deleted one at a time and the last one refused, so a layout somebody had experimented on
     * kept a setup they could not be rid of - and the only alternative was deleting a folder by hand,
     * which is exactly the sort of advice that ends in the wrong folder being deleted.
     *
     * The diagram is not touched.  Captions live here now, so they go with it; the track, the sensors
     * and everything the Central Station knows about are the layout’s, not autonomy’s, and this
     * has no business anywhere near them.
     *
     * @throws IOException if a file could not be removed, having removed what it could - the state in
     *         memory is cleared either way, so what remains on disk is not read back
     */
    public void deleteEverything() throws IOException
    {
        List<String> failed = new ArrayList<>();

        for (String name : new ArrayList<>(configurations.keySet()))
        {
            File file = configurationFile(name);

            if (file.isFile() && !file.delete()) failed.add(file.getName());
        }

        File setup = setupFile();

        if (setup.isFile() && !setup.delete()) failed.add(setup.getName());

        clear();

        // Only if it is now empty.  A folder somebody keeps something else in is not this method’s
        // to remove, and an empty one left behind costs nothing.
        File folder = folder();

        String[] left = folder.list();

        if (folder.isDirectory() && left != null && left.length == 0) folder.delete();

        if (!failed.isEmpty())
        {
            throw new IOException(String.join(", ", failed));
        }
    }

    // --- keeping up with the diagram --------------------------------------------------------------

    /**
     * Follows a page being renamed.
     *
     * Every key here begins with a page name, so a rename would otherwise orphan every name, length,
     * direction and pairing on that page at once - the user would see their entire setup vanish for a
     * page and have no way to tell that a rename caused it.  So it is rewritten universally, across the
     * shared file and every configuration.
     *
     * @param from
     * @param to
     */
    public void renamePage(String from, String to)
    {
        rekey(pointNames, from, to);
        rekey(tileLengths, from, to);
        rekey(tileDirections, from, to);
        rekey(barredArrivals, from, to);

        // Both halves, like the captions: the key is the station's square and the value is the
        // signal's, and a rename moves both.
        rekeyValues(stationSignals, from, to);
        rekey(stationSignals, from, to);
        rekeyValues(portals, from, to);
        rekey(portals, from, to);
        rekey(linkNames, from, to);

        // Captions are keyed by the square the text sits on AND point at the square of the station -
        // both are tile keys, so both move.  Rekeying only the keys left every caption on the page
        // pointing at a station on a page that no longer exists, and the next save deleted them for
        // good as unreconcilable.
        rekeyValues(captions, from, to);
        rekey(captions, from, to);

        // And a link switched off is remembered by its square, so a rename turned every one of them
        // back on - silently, and only on the renamed page.
        Set<String> renamedPortals = new LinkedHashSet<>();

        for (String key : disabledPortals)
        {
            renamedPortals.add(rekeyOne(key, from, to));
        }

        disabledPortals.clear();
        disabledPortals.addAll(renamedPortals);

        Set<String> renamedStations = new LinkedHashSet<>();

        for (String key : stations)
        {
            renamedStations.add(rekeyOne(key, from, to));
        }

        stations.clear();
        stations.addAll(renamedStations);

        if (excludedPages.remove(from)) excludedPages.add(to);

        // Configurations DO key by tile - setPointProperty and captureFromLayout both write
        // "page:x,y" - so they are rewritten here too.  The note that used to stand in this place said
        // they were untouched and warned that anything growing a tile key must be handled; it grew one,
        // and without this a rename silently dropped every placement, home, terminus and length in
        // every configuration while the shared file survived, making the loss look arbitrary.
        for (JSONObject configuration : configurations.values())
        {
            if (!configuration.has("points")) continue;

            JSONObject points = configuration.getJSONObject("points");
            JSONObject renamedPoints = new JSONObject();

            for (String key : points.keySet())
            {
                renamedPoints.put(rekeyOne(key, from, to), points.get(key));
            }

            configuration.put("points", renamedPoints);
        }
    }

    /**
     * What a reconciliation found.  Nothing here is acted on silently: the whole point is that a diagram
     * changing under a setup should be visible.
     */
    public static class Reconciliation
    {
        private final List<String> droppedTileProperties = new ArrayList<>();
        private final List<String> forgottenNames = new ArrayList<>();
        private final Map<String, List<String>> namesStillReferenced = new LinkedHashMap<>();

        /**
         * Lengths and directions removed because their tile is gone.  A tile carries these, so they go
         * with it - the author ruled a deleted tile starts over.
         * @return
         */
        public List<String> getDroppedTileProperties()
        {
            return Collections.unmodifiableList(droppedTileProperties);
        }

        /**
         * Names dropped because their tile is gone and nothing referred to them.  Harmless, but reported
         * so a diagram edit that quietly cost a page of names is visible.
         * @return
         */
        public List<String> getForgottenNames()
        {
            return Collections.unmodifiableList(forgottenNames);
        }

        /**
         * Names whose tile is gone but which are still referenced - by a timetable, a home, or a
         * locomotive placement.
         *
         * These are the ones that matter and the only ones kept.  Dropping one would break the thing
         * referring to it with no explanation; keeping it silently would leave a Point that can never be
         * reached wired into a timetable.  So it is kept AND named, for a person to resolve.
         *
         * @return name -> what still refers to it
         */
        public Map<String, List<String>> getNamesStillReferenced()
        {
            return Collections.unmodifiableMap(namesStillReferenced);
        }

        public boolean isClean()
        {
            return droppedTileProperties.isEmpty() && forgottenNames.isEmpty()
                && namesStillReferenced.isEmpty();
        }
    }

    /**
     * Brings the setup back into line with a diagram that has changed underneath it.
     *
     * Tile properties and names have different lifetimes, which is why this is not one rule:
     *
     *   - a length or a direction belongs to a tile.  When the tile goes, so do they, and nothing else
     *     in the setup referred to them;
     *   - a NAME is referred to from elsewhere - timetables, homes and placements all name Points - so
     *     dropping one breaks whatever refers to it, while keeping one silently leaves a Point wired
     *     into a timetable that no train can ever reach.
     *
     * So a name whose tile has gone is kept only if something still refers to it, and either way it is
     * reported.  That is the reconciliation the caller has to show somebody.
     *
     * @param existing every tile currently on the diagram
     * @return what was found
     */
    public Reconciliation reconcile(Set<TileKey> existing)
    {
        Reconciliation report = new Reconciliation();

        Set<String> keys = new LinkedHashSet<>();

        for (TileKey tile : existing)
        {
            keys.add(tile.toString());
        }

        report.droppedTileProperties.addAll(dropMissing(tileLengths, keys, false));
        report.droppedTileProperties.addAll(dropMissing(tileDirections, keys, true));
        report.droppedTileProperties.addAll(dropMissing(barredArrivals, keys, false));
        report.droppedTileProperties.addAll(dropMissing(stationSignals, keys, false));

        // A caption goes when either end of it does - the square it is drawn on, or the sensor it is
        // about.  Text pointing at track that no longer exists is the orphan this whole change removes.
        report.droppedTileProperties.addAll(reconcileCaptions(keys));

        List<String> goneTiles = new ArrayList<>();

        for (String key : pointNames.keySet())
        {
            if (!keys.contains(key)) goneTiles.add(key);
        }

        for (String key : goneTiles)
        {
            String name = pointNames.get(key);

            List<String> referrers = whatReferences(name);

            if (referrers.isEmpty())
            {
                pointNames.remove(key);
                stations.remove(key);
                report.forgottenNames.add(name + " (" + key + ")");
            }
            else
            {
                report.namesStillReferenced.put(name, referrers);
            }
        }

        // a portal whose partner is gone is half a pairing, which is worse than none
        List<String> brokenPairings = new ArrayList<>();

        for (Map.Entry<String, String> entry : portals.entrySet())
        {
            if (!keys.contains(entry.getKey()) || !keys.contains(entry.getValue()))
            {
                brokenPairings.add(entry.getKey());
            }
        }

        for (String key : brokenPairings)
        {
            unpairPortal(parseTileKey(key));
            report.droppedTileProperties.add("pairing at " + key);
        }

        return report;
    }

    /**
     * Everything in the configurations that names this Point.
     *
     * Deliberately textual: a configuration is stored as it was written, and a placement, a home and a
     * timetable entry all refer to a Point by its name.  Parsing each shape would mean this class
     * knowing the schema of things it only stores.
     */
    private List<String> whatReferences(String pointName)
    {
        List<String> out = new ArrayList<>();

        if (pointName == null || pointName.isEmpty()) return out;

        String quoted = JSONObject.quote(pointName);

        for (Map.Entry<String, JSONObject> entry : configurations.entrySet())
        {
            if (entry.getValue().toString().contains(quoted)) out.add(entry.getKey());
        }

        return out;
    }

    /**
     * Adapts this store to what the reducer asks for.
     * @return
     */
    public GraphReducer.Authored asAuthored()
    {
        return new GraphReducer.Authored()
        {
            @Override
            public String getPointName(TileKey tile)
            {
                return AutonomyCompanionStore.this.getPointName(tile);
            }

            @Override
            public boolean isStation(TileKey tile)
            {
                return AutonomyCompanionStore.this.isStation(tile);
            }

            @Override
            public int getTileLength(TileKey tile)
            {
                return AutonomyCompanionStore.this.getTileLength(tile);
            }
        };
    }

    /**
     * Applies everything stored here to a tile graph: the portal pairings and the directions.
     * @param graph
     */
    public void applyTo(TileGraph graph)
    {
        for (Map.Entry<String, String> entry : portals.entrySet())
        {
            TileKey from = parseTileKey(entry.getKey());
            TileKey to = parseTileKey(entry.getValue());

            if (from != null && to != null) graph.pairPortals(from, to);
        }

        for (String id : disabledPortals)
        {
            TileKey tile = parseTileKey(id);

            if (tile != null) graph.disablePortal(tile);
        }

        for (Map.Entry<String, String> entry : tileDirections.entrySet())
        {
            int hash = entry.getKey().lastIndexOf('#');

            if (hash < 0) continue;

            TileKey tile = parseTileKey(entry.getKey().substring(0, hash));

            if (tile == null) continue;

            String[] route = entry.getKey().substring(hash + 1).split(",");

            if (route.length != 2) continue;

            try
            {
                graph.setDirection(tile,
                    new RouteId(Integer.parseInt(route[0]), Integer.parseInt(route[1])),
                    Direction.valueOf(entry.getValue()));
            }
            catch (RuntimeException e)
            {
                // a direction naming a route the tile no longer has is simply not applied
            }
        }
    }

    // --- internals --------------------------------------------------------------------------------

    /**
     * Every shared field this version writes.
     *
     * A field NOT listed here is treated as something a newer TrainControl wrote, kept aside on load and
     * written back on save so an older version does not delete it.  That makes an omission from this
     * list quietly destructive rather than merely untidy: the stale copy is written after the real one,
     * so every edit to that field since the load is reverted the moment anything saves.
     *
     * "captions" was missing, and it cost exactly that - the migration recorded the captions, saved,
     * had them overwritten with the empty copy read a moment earlier, and then stripped the labels they
     * had been migrated from.  Anything added to save() has to be added here in the same breath.
     */
    private static final Set<String> KNOWN_SHARED = new LinkedHashSet<>(java.util.Arrays.asList(
        "version", "activeConfiguration", "pointNames", "stations", "tileLengths", "tileDirections",
        "barredArrivals", "stationSignals",
        "portals", "captions", "linkNames", "excludedPages", "disabledLinks", "pages"));


    /**
     * Reads the shared half of a setup object in, over a store already emptied of it.
     *
     * Split out of load() so that importing can put a MERGED object through exactly the same
     * reading - the page-id translation and the renumber detection included.  An importer that
     * parsed these fields itself would have had to repeat all of that, and would have drifted out
     * of step with load() the first time either changed.
     *
     * The active configuration is deliberately not read here: importing a configuration must not
     * change which one is running.
     *
     * @param root
     */
    private void readShared(JSONObject root)
    {
        readStringMap(root, "pointNames", pointNames);
        readStringSet(root, "stations", stations);
        readStringMap(root, "tileDirections", tileDirections);
        readStringMap(root, "barredArrivals", barredArrivals);
        readStringMap(root, "stationSignals", stationSignals);
        readStringMap(root, "portals", portals);
        readStringMap(root, "captions", captions);
        readStringMap(root, "linkNames", linkNames);
        readStringSet(root, "excludedPages", excludedPages);
        readStringSet(root, "disabledLinks", disabledPortals);

        JSONObject lengths = root.optJSONObject("tileLengths");

        if (lengths != null)
        {
            for (String key : lengths.keySet())
            {
                tileLengths.put(fromStored(key), lengths.getInt(key));
            }
        }

        readStringMap(root, "pages", pageNamesWhenWritten);

        // stored against page ids; brought back to the names the rest of the application uses
        untranslate(pointNames);
        untranslate(tileDirections);
        untranslate(barredArrivals);
        untranslateTileMap(stationSignals);
        untranslate(linkNames);
        untranslatePortals();
        untranslateTileMap(captions);
        untranslateSet(stations);
        untranslateSet(disabledPortals);

        pageIdConflicts.clear();

        for (Map.Entry<String, String> entry : pageNamesWhenWritten.entrySet())
        {
            String nowCalled = pageIdToName.get(entry.getKey());

            // Absent is fine - the page may simply not be loaded.
            if (nowCalled == null || nowCalled.equals(entry.getValue())) continue;

            // The id now carries a different name, and that alone cannot tell the two cases apart:
            //
            //   renamed    - the same page, called something else.  The old name is gone from the index,
            //                and the settings are still that page's.  This is the case ids exist for.
            //   renumbered - a DIFFERENT page now holds this id.  The old name is still in the index
            //                under some other id, and adopting these settings would attach a page of
            //                names and lengths to the wrong track.
            //
            // So the deciding question is whether the old name still exists somewhere.
            if (pageNameToId.containsKey(entry.getValue()))
            {
                pageIdConflicts.put(entry.getValue(), nowCalled);
            }
        }

        for (String key : root.keySet())
        {
            if (!KNOWN_SHARED.contains(key)) unknownSharedFields.put(key, root.get(key));
        }
    }

    private void clear()
    {
        pointNames.clear();
        stations.clear();
        tileLengths.clear();
        tileDirections.clear();
        barredArrivals.clear();
        portals.clear();
        captions.clear();
        linkNames.clear();
        excludedPages.clear();
        disabledPortals.clear();
        unknownSharedFields.clear();
        pageNamesWhenWritten.clear();
        pageIdConflicts.clear();
        configurations.clear();
        activeConfiguration = null;
    }

    private File folder()
    {
        return new File(layoutFolder, FOLDER);
    }

    private File setupFile()
    {
        return new File(folder(), SETUP_FILE);
    }

    private File configurationFile(String name)
    {
        return new File(folder(), CONFIGURATION_PREFIX + CS2File.sanitizeFilename(name) + ".json");
    }

    private void writeJson(File target, final JSONObject json) throws IOException
    {
        final byte[] bytes = json.toString(2).getBytes(StandardCharsets.UTF_8);

        Util.writeAtomically(target, new Util.StreamWriter()
        {
            @Override
            public void write(java.io.OutputStream out) throws IOException
            {
                out.write(bytes);
            }
        });
    }

    /**
     * A key as it is stored: the page id where one is known, the page name otherwise.
     *
     * Ids are numeric and names are not, so the two never collide, and a page added since the index was
     * last read still round trips - by name, which is no worse than before.
     */
    private String toStored(String key)
    {
        int colon = key.indexOf(':');

        if (colon < 0) return key;

        String id = pageNameToId.get(key.substring(0, colon));

        return id == null ? key : id + key.substring(colon);
    }

    private String fromStored(String key)
    {
        int colon = key.indexOf(':');

        if (colon < 0) return key;

        String name = pageIdToName.get(key.substring(0, colon));

        return name == null ? key : name + key.substring(colon);
    }

    private Map<String, String> translateKeys(Map<String, String> map, boolean storing)
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : map.entrySet())
        {
            out.put(storing ? toStored(entry.getKey()) : fromStored(entry.getKey()), entry.getValue());
        }

        return out;
    }

    private void untranslate(Map<String, String> map)
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : map.entrySet())
        {
            out.put(fromStored(entry.getKey()), entry.getValue());
        }

        map.clear();
        map.putAll(out);
    }

    /**
     * The same as untranslatePortals, for any map whose keys AND values are both squares.
     */
    private void untranslateTileMap(Map<String, String> map)
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : map.entrySet())
        {
            out.put(fromStored(entry.getKey()), fromStored(entry.getValue()));
        }

        map.clear();
        map.putAll(out);
    }

    private Map<String, String> translateTileMap(Map<String, String> map)
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : map.entrySet())
        {
            out.put(toStored(entry.getKey()), toStored(entry.getValue()));
        }

        return out;
    }

    private void untranslatePortals()
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : portals.entrySet())
        {
            out.put(fromStored(entry.getKey()), fromStored(entry.getValue()));
        }

        portals.clear();
        portals.putAll(out);
    }

    private void untranslateSet(Set<String> set)
    {
        Set<String> out = new LinkedHashSet<>();

        for (String key : set)
        {
            out.add(fromStored(key));
        }

        set.clear();
        set.addAll(out);
    }

    private Set<String> translateSet(Set<String> set)
    {
        Set<String> out = new LinkedHashSet<>();

        for (String key : set)
        {
            out.add(toStored(key));
        }

        return out;
    }

    private Map<String, Integer> translateLengths()
    {
        Map<String, Integer> out = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : tileLengths.entrySet())
        {
            out.put(toStored(entry.getKey()), entry.getValue());
        }

        return out;
    }

    // --- captions ---------------------------------------------------------------------------------

    /**
     * The square a caption is about.
     *
     * @param captionTile the square the text is drawn on
     * @return the sensor's square, or null when no caption is drawn there
     */
    public TileKey getCaptionTarget(TileKey captionTile)
    {
        if (captionTile == null) return null;

        return parseTileKey(captions.get(captionTile.toString()));
    }

    /**
     * Draws a station's caption on a square.
     *
     * One caption per square, by construction: the map is keyed by the square the text sits on, so a
     * second caption there replaces the first rather than fighting it.  Several squares may name the
     * same station, which is deliberate - a long platform is legitimately labelled at both ends.
     *
     * @param captionTile where the text goes
     * @param stationTile the sensor it is about, or null to clear
     */
    public void setCaption(TileKey captionTile, TileKey stationTile)
    {
        if (captionTile == null) return;

        if (stationTile == null)
        {
            captions.remove(captionTile.toString());
            return;
        }

        captions.put(captionTile.toString(), stationTile.toString());
    }

    /**
     * Every square carrying a caption about this sensor.
     *
     * @param stationTile
     * @return the caption squares, possibly none
     */
    public Set<TileKey> captionsFor(TileKey stationTile)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (stationTile == null) return out;

        String wanted = stationTile.toString();

        for (Map.Entry<String, String> entry : captions.entrySet())
        {
            if (wanted.equals(entry.getValue()))
            {
                TileKey where = parseTileKey(entry.getKey());

                if (where != null) out.add(where);
            }
        }

        return out;
    }

    /**
     * Every caption, as caption square to sensor square.
     * @return
     */
    public Map<TileKey, TileKey> getCaptions()
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : captions.entrySet())
        {
            TileKey where = parseTileKey(entry.getKey());
            TileKey what = parseTileKey(entry.getValue());

            if (where != null && what != null) out.put(where, what);
        }

        return out;
    }

    /**
     * Forgets every caption whose square, or whose sensor's square, is no longer on the diagram.
     *
     * Called from reconcile, for the same reason everything else there is: track gets redrawn between
     * sessions, and a caption about a sensor that has been deleted would be text pointing at nothing.
     *
     * @param existing every square the diagram still has
     * @return the caption squares that were dropped
     */
    /**
     * A caption goes when the STATION it is about is gone, and not before.
     *
     * The square the text sits on is deliberately not required to hold anything.  A caption is drawn by
     * autonomy on whatever square it names - placeCaption prefers blank space beside a platform, which
     * is the most readable place of all - and the migration puts one on a square whose text label it
     * then empties, which makes that square vanish from the layout file entirely.  Requiring the caption
     * square to host a component therefore deleted, on the very next save, both the captions the user
     * had just placed and every caption the migration had just created.
     *
     * The page still has to exist: a caption on a page somebody deleted is about a diagram that is gone.
     */
    private List<String> reconcileCaptions(Set<String> existing)
    {
        Set<String> pagesLeft = new LinkedHashSet<>();

        for (String key : existing)
        {
            TileKey tile = parseTileKey(key);

            if (tile != null) pagesLeft.add(tile.getPage());
        }

        List<String> dropped = new ArrayList<>();

        for (Map.Entry<String, String> entry : new LinkedHashMap<>(captions).entrySet())
        {
            TileKey where = parseTileKey(entry.getKey());

            boolean pageLeft = where != null && pagesLeft.contains(where.getPage());

            if (pageLeft && existing.contains(entry.getValue())) continue;

            captions.remove(entry.getKey());
            dropped.add(entry.getKey());
        }

        return dropped;
    }

    private Map<String, String> translatePortals()
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : portals.entrySet())
        {
            out.put(toStored(entry.getKey()), toStored(entry.getValue()));
        }

        return out;
    }

    private static String directionKey(TileKey tile, RouteId routeId)
    {
        return tile.toString() + "#" + routeId.getState() + "," + routeId.getIndex();
    }

    // Package-private rather than private: the session prunes stale point data by tile, and has to be
    // able to read the page out of a stored key to tell "this square is gone" from "this square is on a
    // page autonomy was told to ignore".
    static TileKey parseTileKey(String key)
    {
        if (key == null) return null;

        int colon = key.lastIndexOf(':');
        int comma = key.lastIndexOf(',');

        if (colon < 0 || comma < colon) return null;

        try
        {
            return new TileKey(key.substring(0, colon),
                Integer.parseInt(key.substring(colon + 1, comma)),
                Integer.parseInt(key.substring(comma + 1)));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private static String rekeyOne(String key, String fromPage, String toPage)
    {
        return key.startsWith(fromPage + ":")
            ? toPage + key.substring(fromPage.length())
            : key;
    }

    private static <T> void rekey(Map<String, T> map, String fromPage, String toPage)
    {
        Map<String, T> renamed = new LinkedHashMap<>();

        for (Map.Entry<String, T> entry : map.entrySet())
        {
            renamed.put(rekeyOne(entry.getKey(), fromPage, toPage), entry.getValue());
        }

        map.clear();
        map.putAll(renamed);
    }

    private static void rekeyValues(Map<String, String> map, String fromPage, String toPage)
    {
        for (Map.Entry<String, String> entry : map.entrySet())
        {
            entry.setValue(rekeyOne(entry.getValue(), fromPage, toPage));
        }
    }

    private static <T> List<String> dropMissing(Map<String, T> map, Set<String> existing, boolean suffixed)
    {
        List<String> gone = new ArrayList<>();

        for (String key : map.keySet())
        {
            String tile = suffixed && key.lastIndexOf('#') >= 0
                ? key.substring(0, key.lastIndexOf('#')) : key;

            if (!existing.contains(tile)) gone.add(key);
        }

        for (String key : gone)
        {
            map.remove(key);
        }

        return gone;
    }

    private static void readStringMap(JSONObject root, String field, Map<String, String> into)
    {
        JSONObject object = root.optJSONObject(field);

        if (object == null) return;

        for (String key : object.keySet())
        {
            into.put(key, object.getString(key));
        }
    }

    private static void readStringSet(JSONObject root, String field, Set<String> into)
    {
        JSONArray array = root.optJSONArray(field);

        if (array == null) return;

        for (int i = 0; i < array.length(); i++)
        {
            into.add(array.getString(i));
        }
    }
}
