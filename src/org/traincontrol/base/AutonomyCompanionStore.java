package org.traincontrol.base;

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
import org.traincontrol.base.TileGraph.Direction;
import org.traincontrol.base.TileGraph.RouteId;
import org.traincontrol.base.TileGraph.TileKey;
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
    public static final String ERROR_LAST_CONFIGURATION = "autosetup.ui.errorLastConfiguration";
    public static final String ERROR_NOT_LOCAL = "autosetup.ui.errorAutonomyNeedsLocalLayout";

    private static final String FOLDER = "config/autonomy";
    private static final String SETUP_FILE = "setup.json";
    private static final String CONFIGURATION_PREFIX = "configuration-";

    private final File layoutFolder;

    // --- shared: one copy per layout, describing the physical diagram ---------------------------
    private final Map<String, String> pointNames = new LinkedHashMap<>();
    private final Set<String> stations = new LinkedHashSet<>();
    private final Map<String, Integer> tileLengths = new LinkedHashMap<>();
    private final Map<String, String> tileDirections = new LinkedHashMap<>();
    private final Map<String, String> portals = new LinkedHashMap<>();
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
        clear();

        if (!exists()) return;

        JSONObject root = new JSONObject(
            new String(Files.readAllBytes(setupFile().toPath()), StandardCharsets.UTF_8));

        int version = root.optInt("version", VERSION);

        if (version > VERSION)
        {
            throw new IOException(ERROR_VERSION + " (" + version + " > " + VERSION + ")");
        }

        readStringMap(root, "pointNames", pointNames);
        readStringSet(root, "stations", stations);
        readStringMap(root, "tileDirections", tileDirections);
        readStringMap(root, "portals", portals);
        readStringMap(root, "linkNames", linkNames);
        readStringSet(root, "excludedPages", excludedPages);

        JSONObject lengths = root.optJSONObject("tileLengths");

        if (lengths != null)
        {
            for (String key : lengths.keySet())
            {
                tileLengths.put(fromStored(key), lengths.getInt(key));
            }
        }

        activeConfiguration = root.optString("activeConfiguration", null);

        readStringMap(root, "pages", pageNamesWhenWritten);

        // stored against page ids; brought back to the names the rest of the application uses
        untranslate(pointNames);
        untranslate(tileDirections);
        untranslate(linkNames);
        untranslatePortals();
        untranslateSet(stations);

        pageIdConflicts.clear();

        for (Map.Entry<String, String> entry : pageNamesWhenWritten.entrySet())
        {
            String nowCalled = pageIdToName.get(entry.getKey());

            // absent is fine - the page may simply not be loaded.  Present and different is not: that id
            // belongs to another page now, so its settings are not ours to adopt.
            if (nowCalled != null && !nowCalled.equals(entry.getValue()))
            {
                pageIdConflicts.put(entry.getValue(), nowCalled);
            }
        }

        for (String key : root.keySet())
        {
            if (!KNOWN_SHARED.contains(key)) unknownSharedFields.put(key, root.get(key));
        }

        File[] files = folder().listFiles();

        if (files != null)
        {
            for (File file : files)
            {
                String name = file.getName();

                if (!name.startsWith(CONFIGURATION_PREFIX) || !name.endsWith(".json")) continue;

                JSONObject configuration = new JSONObject(
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));

                configurations.put(
                    configuration.optString("name",
                        name.substring(CONFIGURATION_PREFIX.length(), name.length() - 5)),
                    configuration);
            }
        }

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

        final JSONObject root = new JSONObject();

        root.put("version", VERSION);

        // written first so a human opening the file meets the readable part before the coordinate maps
        if (activeConfiguration != null) root.put("activeConfiguration", activeConfiguration);

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
        root.put("portals", new JSONObject(translatePortals()));
        root.put("linkNames", new JSONObject(translateKeys(linkNames, true)));
        root.put("excludedPages", new JSONArray(excludedPages));

        for (Map.Entry<String, Object> entry : unknownSharedFields.entrySet())
        {
            root.put(entry.getKey(), entry.getValue());
        }

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
     * Creates a configuration, optionally as a copy of an existing one.
     *
     * A copy takes everything - point properties, placements, homes, exclusions, globals, timetable -
     * because a configuration exists to differ in where the locomotives are, and starting from a blank
     * one would mean re-entering every decision that had nothing to do with that.
     *
     * @param name
     * @param copyFrom the configuration to copy, or null for an empty one
     */
    public void createConfiguration(String name, String copyFrom)
    {
        JSONObject source = copyFrom == null ? null : configurations.get(copyFrom);

        JSONObject created = source == null
            ? new JSONObject()
            : new JSONObject(source.toString());

        created.put("name", name);

        configurations.put(name, created);

        if (activeConfiguration == null) activeConfiguration = name;
    }

    public void renameConfiguration(String from, String to) throws IOException
    {
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
        if (configurations.size() <= 1) throw new IOException(ERROR_LAST_CONFIGURATION);

        if (configurations.remove(name) == null) return;

        File file = configurationFile(name);

        if (file.isFile()) file.delete();

        if (name.equals(activeConfiguration))
        {
            activeConfiguration = configurations.keySet().iterator().next();
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
        rekeyValues(portals, from, to);
        rekey(portals, from, to);
        rekey(linkNames, from, to);

        Set<String> renamedStations = new LinkedHashSet<>();

        for (String key : stations)
        {
            renamedStations.add(rekeyOne(key, from, to));
        }

        stations.clear();
        stations.addAll(renamedStations);

        if (excludedPages.remove(from)) excludedPages.add(to);

        // configurations reference points by name rather than by tile, so they are untouched by a page
        // rename - but any that grows a tile key later must be rewritten here too
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

    private static final Set<String> KNOWN_SHARED = new LinkedHashSet<>(java.util.Arrays.asList(
        "version", "activeConfiguration", "pointNames", "stations", "tileLengths", "tileDirections",
        "portals", "linkNames", "excludedPages", "pages"));

    private void clear()
    {
        pointNames.clear();
        stations.clear();
        tileLengths.clear();
        tileDirections.clear();
        portals.clear();
        linkNames.clear();
        excludedPages.clear();
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

    private static TileKey parseTileKey(String key)
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
