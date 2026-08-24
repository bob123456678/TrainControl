package org.traincontrol.automationui;

import org.traincontrol.util.I18n;
import org.traincontrol.base.LayoutDiagram;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
     * The highest schema this class can read.  A file claiming a higher version was written by a newer
     * TrainControl and is refused rather than read partially - silently dropping fields it does not
     * recognise would lose the user's work on the next save.
     *
     * 2 is "a station may carry several protecting signals", where stationSignals holds an array
     * rather than a square.  Version 1 reads that field with a string accessor and throws an
     * unchecked exception on an array - after load() has already emptied the store - so a file it
     * cannot read has to be one it REFUSES to read.  That is what the version is for.
     */
    public static final int VERSION = 2;

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
     * The signals that protect each station, keyed by the station's square.
     *
     * Paired by hand rather than inferred.  The nearest signal on the approach is not always the
     * protecting one, and a wrong guess here throws a real signal on real hardware.
     *
     * SEVERAL per station, because a platform reachable from two directions needs a signal on each
     * approach.  It held one until 3.0.0, and a setup written before then has a bare string where this
     * now has an array - both are read, and a station with one signal is still written as a string.
     *
     * Keyed like the portals - square to square - so it survives a page rename for the same reason
     * they do, and so it is reconciled away when the station goes.
     */
    private final Map<String, List<String>> stationSignals = new LinkedHashMap<>();

    /**
     * Squares whose occupancy makes a station unavailable to autonomy (FR-001).
     *
     * Station square to the squares being watched, the same shape as the signals above and kept for the
     * same reason: a station may be held back by more than one place, and each of them is a SQUARE
     * here, resolved to a Point name only when the configuration is built.
     */
    private final Map<String, List<String>> blockedPoints = new LinkedHashMap<>();

    /**
     * @param station the station's square
     * @return the squares that make it unavailable while occupied, in the order they were added
     */
    public List<TileKey> getBlockingPoints(TileKey station)
    {
        List<TileKey> out = new ArrayList<>();

        if (station == null) return out;

        List<String> keys = blockedPoints.get(station.toString());

        if (keys == null) return out;

        for (String key : keys)
        {
            TileKey blocker = parseTileKey(key);

            if (blocker != null) out.add(blocker);
        }

        return out;
    }

    /**
     * Replaces the squares that hold a station back.
     *
     * @param station the station's square
     * @param blockers the squares to watch; empty or null clears the restriction
     */
    public void setBlockingPoints(TileKey station, List<TileKey> blockers)
    {
        if (station == null) return;

        List<String> keys = new ArrayList<>();

        if (blockers != null)
        {
            for (TileKey blocker : blockers)
            {
                // De-duplicated here rather than in the picker, and never the station itself: standing
                // at a station already decides whether it is free, so watching it from itself makes a
                // station nothing can be sent to rather than one that is restricted.
                if (blocker != null && !blocker.equals(station) && !keys.contains(blocker.toString()))
                {
                    keys.add(blocker.toString());
                }
            }
        }

        if (keys.isEmpty()) blockedPoints.remove(station.toString());
        else blockedPoints.put(station.toString(), keys);
    }

    /**
     * @return every station held back by something, against the squares watched for it
     */
    public Map<TileKey, List<TileKey>> getBlockingPoints()
    {
        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        for (String key : blockedPoints.keySet())
        {
            TileKey station = parseTileKey(key);

            if (station == null) continue;

            List<TileKey> blockers = getBlockingPoints(station);

            if (!blockers.isEmpty()) out.put(station, blockers);
        }

        return out;
    }

    /**
     * @param station the station's square
     * @return the square of the first signal protecting it, or null
     */
    public TileKey getProtectingSignal(TileKey station)
    {
        List<TileKey> all = getProtectingSignals(station);

        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * @param station the station's square
     * @return the squares of every signal protecting it, in the order they were paired
     */
    public List<TileKey> getProtectingSignals(TileKey station)
    {
        List<TileKey> out = new ArrayList<>();

        if (station == null) return out;

        List<String> keys = stationSignals.get(station.toString());

        if (keys == null) return out;

        for (String key : keys)
        {
            TileKey signal = parseTileKey(key);

            if (signal != null) out.add(signal);
        }

        return out;
    }

    /**
     * @param station the station's square
     * @param signal the signal's square, or null to unpair everything
     */
    public void setProtectingSignal(TileKey station, TileKey signal)
    {
        setProtectingSignals(station, signal == null
            ? Collections.<TileKey>emptyList() : Collections.singletonList(signal));
    }

    /**
     * Replaces every signal protecting a station.
     *
     * @param station the station's square
     * @param signals the signals' squares; empty or null unpairs
     */
    public void setProtectingSignals(TileKey station, List<TileKey> signals)
    {
        if (station == null) return;

        List<String> keys = new ArrayList<>();

        if (signals != null)
        {
            for (TileKey signal : signals)
            {
                // De-duplicated here rather than in the picker, so that nothing else which writes this
                // - an import, a restored snapshot - can leave one signal in the list twice
                if (signal != null && !keys.contains(signal.toString())) keys.add(signal.toString());
            }
        }

        if (keys.isEmpty())
        {
            stationSignals.remove(station.toString());
        }
        else
        {
            stationSignals.put(station.toString(), keys);
        }
    }

    /**
     * @return every station that has protecting signals, against those signals' squares
     */
    public Map<TileKey, List<TileKey>> getProtectingSignals()
    {
        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        for (String key : stationSignals.keySet())
        {
            TileKey station = parseTileKey(key);

            if (station == null) continue;

            List<TileKey> signals = getProtectingSignals(station);

            if (!signals.isEmpty()) out.put(station, signals);
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
     * Whether this setup's keys can be trusted to mean the pages they name.
     *
     * A setup is keyed by page ID, and readShared turns those ids into page NAMES using the "pages" map
     * the file carries.  When a renumber has happened that map is wrong, so every entry is name-keyed
     * to the wrong page - and the coordinates of a page of settings do not exist on whatever page now
     * holds its old id.  Anything that deletes on the strength of "this square does not exist" is then
     * deleting on the strength of a lie.
     *
     * @return true while a renumber is outstanding and nothing has re-keyed the setup
     */
    public boolean isPageNumberingSuspect()
    {
        return !pageIdConflicts.isEmpty();
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

        // What is here now, so that a failure in the READ can put it back.
        //
        // The promise above holds for a parse failure, which happens before this point.  It did not
        // hold for a TYPE failure: readShared runs after clear() and uses the strict accessors
        // throughout - getString, getInt - each of which throws part way through with the store already
        // empty.  That is the very state the comment says was fixed, reached by a different door, and
        // importBundle cites this method as the model for the guard it does have.
        //
        // The trigger is a setup.json this build did not write - hand-edited, or from another tool -
        // since every field it writes round trips.  The guarantee is worth keeping whatever the odds:
        // the caller is left with a live blank store, one press of Save from writing it to disk.
        JSONObject wasThere = snapshotSetup();

        clear();

        try
        {
            readShared(root);

            activeConfiguration = root.optString("activeConfiguration", null);

            configurations.putAll(loaded);
        }
        catch (RuntimeException e)
        {
            restoreSetup(wasThere);

            throw e;
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
    /**
     * Every square that has been given a name, in the order they were named.
     *
     * For pickers that offer POINTS to choose between: a square with no name is one the operator cannot
     * recognise in a list, so it is not a choice that can be made sensibly.
     *
     * @return the named squares
     */
    public List<TileKey> getNamedTiles()
    {
        List<TileKey> out = new ArrayList<>();

        for (String key : pointNames.keySet())
        {
            TileKey tile = parseTileKey(key);

            if (tile != null) out.add(tile);
        }

        return out;
    }

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
        if (tile == null) return false;

        if (disabledPortals.contains(tile.toString())) return true;

        // The PARTNER as well, which is the same question TileGraph.portalClosed asks (TD-2).
        //
        // setPortalDisabled writes both ends, so on any setup saved since that fix the two agree and
        // this second look costs nothing. It is the setups written BEFORE it that need it: those have
        // one end switched off and the other left on, and TileGraph refuses to route through either
        // while this answered "open" for the far one - so the far end drew as a live two-way door, with
        // its arrows and a ticked "Use link" box, over track no train could pass.
        //
        // Before the fix both halves said "open" and were wrong together, which is at least
        // consistent. Repairing only the router made them disagree, which is harder to diagnose than
        // the defect was.
        TileKey partner = getPortalPartner(tile);

        return partner != null && disabledPortals.contains(partner.toString());
    }

    /**
     * Switches a link in or out of the railway, and its partner with it.
     *
     * OB-041, Adam: "if a linked link is turned off, its target isn't."
     *
     * A pair of links is one doorway with an end in two places, and autonomy walks through it in both
     * directions. A doorway shut at one end and open at the other is not half shut - it is a route that
     * exists going one way and not the other, which nothing on the diagram says and no train can be
     * told.
     *
     * Here rather than in AutonomySession, where pairPortals's "both ends on" rule lives, because this
     * is where the partner is known - and because a rule kept beside the caller is a rule the next
     * caller does not get. (`pairPortals` above still switches both ends on explicitly; that call now
     * does the second end twice, which costs nothing and says what it means.)
     *
     * @param tile the link
     * @param disabled true to leave it out of the railway entirely
     */
    public void setPortalDisabled(TileKey tile, boolean disabled)
    {
        set(tile, disabled);

        TileKey partner = getPortalPartner(tile);

        // Directly, not by calling this again: the pairing is mutual, so recursing would come straight
        // back here for the square it started from.
        if (partner != null) set(partner, disabled);
    }

    private void set(TileKey tile, boolean disabled)
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

    /**
     * Follows a locomotive's new name into every configuration.
     *
     * Three things in a configuration hold a locomotive by NAME - the placement, the home assignment
     * and the exclusion list - and none of them was repaired when one was renamed.  The active
     * configuration got away with it by accident: captureFromLayout launders it back from the running
     * layout, which holds locomotives by reference.  A configuration that was not active at the time
     * was never touched at all.
     *
     * What that costs is not a lost placement.  parseAuto refuses a configuration naming a locomotive
     * it cannot find, and answers a refusal by invalidating the WHOLE layout - so choosing that
     * configuration weeks later stops the railway working, with an error naming a locomotive and
     * nothing connecting it to the rename.
     *
     * EVERY configuration, including the inactive ones, which is the half that had no repair at all.
     *
     * @param from the old name
     * @param to the new name
     */
    public void locomotiveRenamed(String from, String to)
    {
        if (from == null || to == null || from.equals(to)) return;

        repairLocomotive(from, to);
    }

    /**
     * Takes a deleted locomotive out of every configuration.
     *
     * The placement and the home go; the exclusion is dropped from the list.  Leaving any of them names
     * a locomotive that resolves to nothing, which is the same fatal state as a stale rename.
     *
     * @param name the locomotive that no longer exists
     */
    public void locomotiveDeleted(String name)
    {
        if (name == null) return;

        repairLocomotive(name, null);
    }

    /**
     * @param from the name to look for
     * @param to the name to put in its place, or null to remove it
     */
    private void repairLocomotive(String from, String to)
    {
        for (JSONObject configuration : configurations.values())
        {
            repairLocomotiveIn(configuration, from, to);
        }
    }

    /**
     * The same repair, over a setup somebody else is holding.
     *
     * The diagram editor takes a snapshot of the whole setup when it opens, and puts it back if the
     * user cancels.  A rename made while that window is open repairs the LIVE store and leaves the
     * snapshot naming the old locomotive - so cancelling wrote it back, and a configuration naming a
     * locomotive that is not in the database is refused by parseAuto, which invalidates the whole
     * layout.  That is the exact state this repair exists to prevent, reached by holding a copy.
     *
     * Public and static because the holder is in another package and there is nothing here to hold: it
     * is a rewrite of somebody else's JSON, in the shape snapshotSetup returns.
     *
     * @param setup what snapshotSetup returned; changed in place
     * @param from the old locomotive name
     * @param to the new one, or null when it was deleted
     */
    public static void repairLocomotiveInSetup(JSONObject setup, String from, String to)
    {
        if (setup == null || from == null || from.equals(to)) return;

        JSONObject copies = setup.optJSONObject("configurations");

        if (copies == null) return;

        for (String name : copies.keySet())
        {
            JSONObject configuration = copies.optJSONObject(name);

            if (configuration != null) repairLocomotiveIn(configuration, from, to);
        }
    }

    /**
     * One configuration's placements, homes and exclusion lists.
     *
     * @param configuration the configuration, changed in place
     * @param from the old name
     * @param to the new name, or null to remove it
     */
    private static void repairLocomotiveIn(JSONObject configuration, String from, String to)
    {
        if (configuration == null || !configuration.has("points")) return;

        repairLocomotiveInPoints(configuration.getJSONObject("points"), from, to);
    }

    /**
     * Follows a rename into one page snapshot - what snapshotPage returns, held by the editor's undo.
     *
     * A third holder of the same names, and the one that reaches DISK: the diagram editor pushes a
     * snapshot onto its undo stack for every edit, and Ctrl+Z puts it back and saves.  So a rename made
     * while the editor is open was repaired in the live store and in the Cancel snapshot, and an undo
     * afterwards wrote the old name back over both - which parseAuto answers by invalidating the whole
     * layout, days later and with nothing connecting it to the rename.
     *
     * The shape differs from a configuration's: here each value is already the POINTS object, keyed by
     * square, rather than a configuration with a points child.  Hence the split below - the per-point
     * work is one method with three callers rather than three copies that can disagree.
     *
     * @param snapshot what snapshotPage returned; changed in place
     * @param from the old locomotive name
     * @param to the new one, or null when it was deleted
     */
    @SuppressWarnings("unchecked")
    public static void repairLocomotiveInPageSnapshot(Map<String, Object> snapshot, String from,
        String to)
    {
        if (snapshot == null || from == null || from.equals(to)) return;

        Object configurations = snapshot.get("configurations");

        if (!(configurations instanceof Map)) return;

        for (Object points : ((Map<String, JSONObject>) configurations).values())
        {
            if (points instanceof JSONObject) repairLocomotiveInPoints((JSONObject) points, from, to);
        }
    }

    /**
     * One square-keyed set of point properties: the placements, homes and exclusion lists in it.
     *
     * @param points square to that square's properties, changed in place
     * @param from the old name
     * @param to the new name, or null to remove it
     */
    private static void repairLocomotiveInPoints(JSONObject points, String from, String to)
    {
        if (points == null) return;

        for (String key : points.keySet())
        {
            JSONObject point = points.optJSONObject(key);

            if (point == null) continue;

            JSONObject placed = point.optJSONObject(AutonomyBuilder.LOCOMOTIVE);

            if (placed != null && from.equals(placed.optString("name", null)))
            {
                if (to == null) point.remove(AutonomyBuilder.LOCOMOTIVE);
                else placed.put("name", to);
            }

            if (from.equals(point.optString(HOME_KEY, null)))
            {
                if (to == null) point.remove(HOME_KEY);
                else point.put(HOME_KEY, to);
            }

            JSONArray excluded = point.optJSONArray(EXCLUDED_LOCS_KEY);

            if (excluded == null) continue;

            JSONArray kept = new JSONArray();

            for (int at = 0; at < excluded.length(); at++)
            {
                String was = excluded.optString(at, null);

                if (was == null) continue;

                if (!from.equals(was)) kept.put(was);
                else if (to != null) kept.put(to);
            }

            point.put(EXCLUDED_LOCS_KEY, kept);
        }
    }

    /** Named here rather than borrowed: AutonomyBuilder keeps its copy private. */
    private static final String HOME_KEY = "home";

    private static final String EXCLUDED_LOCS_KEY = "excludedLocs";

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

        root.put("version", versionWritten());

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
        root.put("stationSignals", new JSONObject(translateTileListMap(stationSignals)));
        root.put("blockedPoints", new JSONObject(translateTileListMap(blockedPoints)));
        root.put("portals", new JSONObject(translatePortals()));
        root.put("captions", new JSONObject(translateTileMap(captions)));
        root.put("linkNames", new JSONObject(translateKeys(linkNames, true)));
        // By page ID, like the other nine.  This was the one collection written raw, and it broke the
        // rule setPageIds states: a rename orphaned it, so an excluded page silently rejoined autonomy
        // and its old name sat in the set for ever because nothing prunes it.
        root.put("excludedPages", new JSONArray(translatePages(excludedPages)));
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

        bundle.put("version", versionWritten());
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

        // Whether this name was already here, so the rollback below knows if removing it would be
        // taking away something of the user's rather than undoing its own work.
        boolean existed = configurations.containsKey(name);

        // And a COPY of what is being replaced, when something is.
        //
        // The rollback below used to put back only a configuration this import had added, on the
        // reasoning that removing an existing name would take the user's own work with it.  That was a
        // false choice: the object being replaced can be kept and put back, which is what load() does
        // with snapshotSetup.  Importing over a name consents to being replaced by a GOOD file, not to
        // losing the configuration to an unreadable one.
        JSONObject replaced = existed ? new JSONObject(configurations.get(name).toString()) : null;

        importConfiguration(name, configuration);

        JSONObject incoming = file.optJSONObject(EXPORT_SHARED);

        if (incoming == null) return 0;

        JSONObject merged = sharedFields();

        int filled = 0;

        for (String key : incoming.keySet())
        {
            if ("version".equals(key)) continue;

            Object value = incoming.get(key);

            // "pages" is not a setting being merged - it is the exporter's record of what each of
            // THEIR ids was called, and it is the only evidence a renumber can be detected from.  Under
            // the merge rule below, mine won for every id both files knew, so readShared read my own
            // names back and compared them against my own index: the two could never disagree, and
            // pageIdConflicts was empty after any import by construction.
            //
            // Theirs wins per id, and ids only I have are kept - those say what MY pages were called
            // and nothing incoming refers to them.
            if ("pages".equals(key) && value instanceof JSONObject)
            {
                JSONObject mine = merged.optJSONObject(key);

                if (mine == null)
                {
                    merged.put(key, value);
                }
                else
                {
                    for (String id : ((JSONObject) value).keySet())
                    {
                        mine.put(id, ((JSONObject) value).get(id));
                    }
                }

                continue;
            }

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
            // What the store holds now, in the shape readShared takes, so that an import which turns
            // out to be unreadable can be put back.
            //
            // readShared uses the type-strict accessors and throws part way through on anything it did
            // not expect - and the merged object is assembled straight out of somebody else's file
            // without being checked.  Clearing first therefore emptied the shared half and then failed,
            // and the panel's "import unreadable" told the user nothing had happened while the store
            // stood blank, ready for the next save to write that over setup.json.
            //
            // load() was hardened against exactly this and says so; the import path shares readShared
            // and was not.
            JSONObject wasThere = sharedFields();

            try
            {
                // Through the same door load() uses, so the page-id translation happens once and here
                clearShared();
                readShared(merged);
            }
            catch (RuntimeException e)
            {
                clearShared();
                readShared(wasThere);

                // And the configuration, which was installed before any of this and was not being put
                // back with it.  What that left was worse than a failed import: a configuration whose
                // placements, homes and exclusions refer to points the rollback has just taken away,
                // offered in the list like any other.
                //
                // Put back exactly as it was, whichever case this is: taken out when the import added
                // it, and restored from the copy above when the import replaced something.
                if (existed) configurations.put(name, replaced);
                else forgetConfiguration(name);

                throw e;
            }
        }

        return filled;
    }

    /**
     * Everything this store holds, as JSON, for a caller that may have to put it back.
     *
     * The diagram editor writes the setup to disk as it goes - a moved tile has to reach the file, or
     * a crash between the move and the save loses the lot - and its Cancel button then had nothing to
     * undo those writes with: the DIAGRAM was re-read from disk and the setup was not, so cancelling
     * left a station recorded on a square the track had been moved away from.
     *
     * Deep copies on the way out, so that the snapshot cannot be changed underneath its holder by the
     * editing that follows.
     *
     * snapshotPage does this too now.  It did not when this was written, and the sentence saying so is
     * what led a reviewer to UR-5 - undo restoring the edit rather than undoing it, because the
     * snapshot shared the store's own lists and point objects.  Left as it stood it would read as a
     * defect still open.
     *
     * @return a snapshot for restoreSetup
     */
    public JSONObject snapshotSetup()
    {
        JSONObject out = new JSONObject();

        out.put("shared", sharedFields());

        JSONObject copies = new JSONObject();

        for (Map.Entry<String, JSONObject> entry : configurations.entrySet())
        {
            copies.put(entry.getKey(), new JSONObject(entry.getValue().toString()));
        }

        out.put("configurations", copies);
        out.put("active", activeConfiguration == null ? JSONObject.NULL : activeConfiguration);

        return out;
    }

    /**
     * Puts back everything snapshotSetup took.
     *
     * @param was a snapshot from snapshotSetup
     */
    public void restoreSetup(JSONObject was)
    {
        if (was == null) return;

        clearShared();
        readShared(was.getJSONObject("shared"));

        configurations.clear();

        JSONObject copies = was.getJSONObject("configurations");

        for (String name : copies.keySet())
        {
            configurations.put(name, new JSONObject(copies.getJSONObject(name).toString()));
        }

        activeConfiguration = was.isNull("active") ? null : was.getString("active");
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
        blockedPoints.clear();
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

        if (fileNameTaken(name, null)) throw new IOException(ERROR_NAME_IN_USE);

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

    /**
     * Takes back a configuration this class has just put in, without touching any file.
     *
     * For the import rollback only.  deleteConfiguration is the door for a user deleting one and does
     * more than this needs - it deletes the FILE, and refuses when it cannot - while an import that has
     * not reached a save has written nothing to delete.
     *
     * @param name the configuration to forget
     */
    private void forgetConfiguration(String name)
    {
        configurations.remove(name);

        // importConfiguration makes it active when nothing else was, so taking it back has to leave
        // that pointing at something that exists.
        if (name.equals(activeConfiguration))
        {
            activeConfiguration = configurations.isEmpty()
                ? null : configurations.keySet().iterator().next();
        }
    }

    public void renameConfiguration(String from, String to) throws IOException
    {
        // Refused rather than allowed to overwrite: renaming A to an existing B used to replace B and
        // then delete B's file, destroying a configuration the user never named in the gesture.
        if (!from.equals(to) && configurations.containsKey(to))
        {
            throw new IOException(ERROR_NAME_IN_USE);
        }

        if (!from.equals(to) && fileNameTaken(to, from))
        {
            throw new IOException(ERROR_NAME_IN_USE);
        }

        JSONObject configuration = configurations.remove(from);

        if (configuration == null) return;

        configuration.put("name", to);
        configurations.put(to, configuration);

        if (from.equals(activeConfiguration)) activeConfiguration = to;

        File old = configurationFile(from);
        File now = configurationFile(to);

        // MOVED and rewritten, not deleted.
        //
        // The new file used to be written only by the save that follows this, so deleting the old one
        // here left a window in which the configuration was on disk nowhere at all - and load()
        // rebuilds the list by scanning the folder, so a save that failed for any reason (a sync
        // client holding the folder, a full disk, the process dying) destroyed it permanently.
        //
        // Moving closes that window: the data is under one name or the other at every instant.  The
        // rewrite that follows is what makes the file agree with its own name - load() takes the name
        // from INSIDE the file, so a moved file alone would come back under the old name.
        //
        // A failure is raised rather than swallowed, for the reason deleteConfiguration gives: what is
        // in memory and what is on disk have to keep saying the same thing, and only the user can
        // decide what to do when they cannot.
        if (old.isFile())
        {
            try
            {
                // The move only when there is somewhere to move TO.  Two names can resolve to one
                // file - sanitising is many to one, and File.equals is case-insensitive on Windows, so
                // "Morning" to "morning" is a rename with nothing to move - but the REWRITE still has
                // to happen, because load() takes a configuration's name from inside its file.  Skipping
                // both left the old name in the file, so a rename that was not followed by a
                // successful save silently undid itself.
                if (!old.equals(now))
                {
                    Files.move(old.toPath(), now.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                writeJson(now, configuration);
            }
            catch (IOException e)
            {
                // Back to exactly where we were, on disk and in memory both.  A file left at the new
                // name holding the old name inside it would be saved a second time under the old name
                // later, and load() would then find the same configuration twice.
                if (now.isFile() && !old.isFile())
                {
                    try
                    {
                        Files.move(now.toPath(), old.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (IOException hopeless)
                    {
                        // nothing further can be done here; the data is still on disk under one name
                    }
                }

                configurations.remove(to);
                configurations.put(from, configuration);
                configuration.put("name", from);

                if (to.equals(activeConfiguration)) activeConfiguration = from;

                throw new IOException(I18n.f("autosetup.ui.errorRenameConfigurationFailed", from));
            }
        }
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
        rekeyListValues(stationSignals, from, to);
        rekey(stationSignals, from, to);
        rekeyListValues(blockedPoints, from, to);
        rekey(blockedPoints, from, to);
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
     * Follows tiles being moved on the diagram.
     *
     * Everything here is keyed by SQUARE, so a tile that moves leaves its whole setup behind at the
     * old coordinates - where there is now no track.  The next reconcile finds a station on a square
     * with no sensor and drops it, and a station that took ten minutes to name, face, restrict and
     * measure is gone because somebody nudged a tile one square left.  The caption alone was
     * followed, which made the loss look arbitrary: the NAME moved and the station under it did not.
     *
     * The whole set in one call, not one tile at a time.  A group dragged one square right has every
     * source square landing on another source square, so moving them in sequence makes each write
     * destroy the data the next read was going to need - the same trap the captions hit, where
     * dragging left happened to work and dragging right ate every other one.
     *
     * Two different things happen to a key.  A square that MOVES takes what is written about it; a
     * square that is only NAMED by something else - a caption pointing at a station, a station's
     * protecting signal, a portal's partner - is repointed where it stands.  Doing only the first
     * leaves half the setup naming the square the tile used to be on.
     *
     * A tile moved onto a square that is not itself moving replaces what was there, which is what
     * the diagram does too.
     *
     * @param moves each square being vacated, and where it is going
     */
    /**
     * Squares that have been built over, whose setup is therefore about track that is gone.
     *
     * moveTiles does this for the squares a move LANDS on, and for a long time that was the only way a
     * square could be overwritten.  A column or a row is bulk-replaced instead: the tiles that were
     * there are deleted and other tiles are written in their place, so the same thing happens to
     * twenty squares at once and nothing was telling the setup about any of them.
     *
     * @param tiles the squares whose setup is to be forgotten
     */
    public void forgetTiles(java.util.Collection<TileKey> tiles)
    {
        moveTiles(null, tiles);
    }

    private static Set<String> asKeys(java.util.Collection<TileKey> tiles)
    {
        Set<String> keys = new LinkedHashSet<>();

        if (tiles == null) return keys;

        for (TileKey tile : tiles)
        {
            if (tile != null) keys.add(tile.toString());
        }

        return keys;
    }

    public void moveTiles(Map<TileKey, TileKey> moves)
    {
        moveTiles(moves, null);
    }

    /**
     * One diagram edit: what moved, and what else was built over.
     *
     * Both halves in one call, and deliberately not two.  Every edit that replaces track has both - a
     * bulk column move is a line arriving and a line being written over - and doing them separately
     * means the caller has to get the ORDER right (forget first, or the arriving setup is wiped) and
     * has to tell the forgetting which squares are only passing through.  The bulk path did exactly
     * that, by hand, with the second argument nobody else had to pass; and a rule that has to be
     * restated at each call site is a rule that will eventually be restated wrongly.
     *
     * So the store works out the whole landing set itself and the callers cannot get it wrong.  A pure
     * forget is this with no moves, and a pure move is this with nothing built over.
     *
     * @param moves each square being vacated, and where it is going - may be null
     * @param builtOver squares whose track has been replaced by other track - may be null
     */
    public void moveTiles(Map<TileKey, TileKey> moves, java.util.Collection<TileKey> builtOver)
    {
        Map<String, String> byKey = new LinkedHashMap<>();

        if (moves != null)
        {
            for (Map.Entry<TileKey, TileKey> move : moves.entrySet())
            {
                if (move.getKey() == null || move.getValue() == null) continue;

                if (move.getKey().equals(move.getValue())) continue;

                byKey.put(move.getKey().toString(), move.getValue().toString());
            }
        }

        // Every square being LANDED ON that is not itself moving away.
        //
        // The diagram writes the arriving tile over whatever was there, and the setup has to let go of
        // the same square - the two loops below only overwrite a landing square when the source had
        // something to overwrite it with.  So dragging a piece of plain track onto a station left the
        // station's name, signals, length and restrictions attached to a square that now holds plain
        // track, and reconcile never tidied it up because the square still had a tile on it.
        //
        // A landing square that is ALSO a source is left alone: it is being vacated in the same
        // gesture, and its own entry travels with it.
        Set<String> landing = new LinkedHashSet<>();

        for (String to : byKey.values())
        {
            if (!byKey.containsKey(to)) landing.add(to);
        }

        // And the squares the caller says were built over by something other than a move: a bulk
        // column edit clears the whole destination line, including the squares whose source was blank.
        for (String over : asKeys(builtOver))
        {
            if (!byKey.containsKey(over)) landing.add(over);
        }

        // Sparing the labels of the tiles that are arriving - see forgetSquares.  A platform whose
        // name is written on the square below it, nudged down one, lands ON its own label.
        forgetSquares(landing, byKey);

        if (byKey.isEmpty()) return;

        moveKeys(pointNames, byKey);
        moveKeys(tileLengths, byKey);
        // Suffixed, because a direction belongs to a square AND a route across it.  moveKeys matches
        // whole keys, so it never matched one of these: a moved tile left every facing behind on the
        // square the track had walked away from, and the next reconcile - which does know about the
        // suffix - dropped them.  That is the same loss moveTiles exists to prevent, hiding behind a
        // key shape.
        moveSuffixedKeys(tileDirections, byKey);
        moveKeys(barredArrivals, byKey);
        moveKeys(linkNames, byKey);

        // Key and value both name a square, so both follow.  The value is REPOINTED rather than
        // moved: a caption on a square that stayed put, naming a station that moved, still names that
        // station.
        moveListValues(stationSignals, byKey);
        moveKeys(stationSignals, byKey);
        moveListValues(blockedPoints, byKey);
        moveKeys(blockedPoints, byKey);

        moveValues(portals, byKey);
        moveKeys(portals, byKey);

        moveValues(captions, byKey);
        moveKeys(captions, byKey);

        moveMembers(stations, byKey);
        moveMembers(disabledPortals, byKey);

        // The configurations key by tile as well - setPointProperty and captureFromLayout both write
        // "page:x,y" - and that is where the facings, the placements, the homes, the termini and the
        // maximum lengths live.  See renamePage, which learned this the same way.
        for (JSONObject configuration : configurations.values())
        {
            if (!configuration.has("points")) continue;

            JSONObject points = configuration.getJSONObject("points");
            JSONObject moved = new JSONObject();

            // The ones staying put first, so a tile arriving on a square that is not moving replaces
            // what was there rather than the other way round.
            for (String key : points.keySet())
            {
                if (!byKey.containsKey(key)) moved.put(key, points.get(key));
            }

            for (String key : points.keySet())
            {
                if (byKey.containsKey(key)) moved.put(byKey.get(key), points.get(key));
            }

            configuration.put("points", moved);
        }
    }

    /**
     * Everything this store holds about one page, as something that can be put back.
     *
     * For the diagram editor's undo.  It used to snapshot the CAPTIONS of a page and nothing else,
     * which was enough while a caption was the only thing the editor moved - and stopped being
     * enough the moment a tile started carrying its whole setup with it.  Undo then put the track
     * back and left the station, the name, the facings and the restrictions wherever the move had
     * taken them.
     *
     * By page, not by square, because that is the unit the editor works in and because a move can
     * put a square somewhere the caller has no reason to have thought of.
     *
     * The shape of what comes back is this class's own business - it goes straight back into
     * restorePage and nowhere else.
     *
     * @param page the page name
     * @return everything keyed to that page
     */
    public Map<String, Object> snapshotPage(String page)
    {
        Map<String, Object> out = new LinkedHashMap<>();

        if (page == null) return out;

        out.put("pointNames", onPage(pointNames, page));
        out.put("tileLengths", onPage(tileLengths, page));
        out.put("tileDirections", onPage(tileDirections, page));
        out.put("barredArrivals", onPage(barredArrivals, page));
        out.put("linkNames", onPage(linkNames, page));
        // COPIED, not shared.  Every other collection here holds strings and numbers, which cannot
        // change underneath a snapshot; this one holds LISTS, and forgetTiles calls removeAll on them
        // in place - so deleting the signal's square emptied the snapshot's list too and undo restored
        // the deletion.
        out.put("stationSignals", copyLists(onPage(stationSignals, page)));
        out.put("blockedPoints", copyLists(onPage(blockedPoints, page)));
        out.put("portals", onPage(portals, page));
        out.put("captions", onPage(captions, page));

        out.put("stations", membersOnPage(stations, page));
        out.put("disabledPortals", membersOnPage(disabledPortals, page));

        // The configurations key by tile too, and that is where the facings, the placements, the
        // homes and the maximum lengths live - the half of a station's setup that is not in the
        // shared file.  A snapshot without them restores half a station.
        Map<String, JSONObject> points = new LinkedHashMap<>();

        for (Map.Entry<String, JSONObject> entry : configurations.entrySet())
        {
            if (!entry.getValue().has("points")) continue;

            JSONObject from = entry.getValue().getJSONObject("points");
            JSONObject kept = new JSONObject();

            for (String key : from.keySet())
            {
                // Deep, for the same reason as the signals above and with more at stake: this is where
                // the facings, the placements, the homes and the maximum lengths live, and
                // setPointProperty writes them with points.getJSONObject(id).put(...) - straight into
                // the object a shared snapshot would be holding.  Undo then restored the edit.
                if (!isOnPage(key, page)) continue;

                Object value = from.get(key);

                kept.put(key, value instanceof JSONObject
                    ? new JSONObject(((JSONObject) value).toString()) : value);
            }

            points.put(entry.getKey(), kept);
        }

        out.put("configurations", points);

        return out;
    }

    /**
     * Puts a page back as snapshotPage found it.
     *
     * Everything currently keyed to that page is dropped first, so a square that has GAINED something
     * since the snapshot loses it again - which is what undo means.  Other pages are not touched.
     *
     * @param page the page name
     * @param snapshot what snapshotPage returned, or null to do nothing
     */
    @SuppressWarnings("unchecked")
    public void restorePage(String page, Map<String, Object> snapshot)
    {
        if (page == null || snapshot == null) return;

        putBack(pointNames, page, (Map<String, String>) snapshot.get("pointNames"));
        putBack(tileLengths, page, (Map<String, Integer>) snapshot.get("tileLengths"));
        putBack(tileDirections, page, (Map<String, String>) snapshot.get("tileDirections"));
        putBack(barredArrivals, page, (Map<String, String>) snapshot.get("barredArrivals"));
        putBack(linkNames, page, (Map<String, String>) snapshot.get("linkNames"));
        putBack(stationSignals, page, copyLists((Map<String, List<String>>) snapshot.get("stationSignals")));
        putBack(blockedPoints, page, copyLists((Map<String, List<String>>) snapshot.get("blockedPoints")));
        putBack(portals, page, (Map<String, String>) snapshot.get("portals"));
        putBack(captions, page, (Map<String, String>) snapshot.get("captions"));

        putMembersBack(stations, page, (Set<String>) snapshot.get("stations"));
        putMembersBack(disabledPortals, page, (Set<String>) snapshot.get("disabledPortals"));

        Map<String, JSONObject> points = (Map<String, JSONObject>) snapshot.get("configurations");

        if (points == null) return;

        for (Map.Entry<String, JSONObject> entry : configurations.entrySet())
        {
            if (!entry.getValue().has("points")) continue;

            JSONObject live = entry.getValue().getJSONObject("points");
            JSONObject rebuilt = new JSONObject();

            for (String key : live.keySet())
            {
                if (!isOnPage(key, page)) rebuilt.put(key, live.get(key));
            }

            JSONObject was = points.get(entry.getKey());

            if (was != null)
            {
                // Copied on the way back as well, so the store and the snapshot do not end up sharing
                // again: a snapshot is held for as long as the editor might undo, and the very next
                // edit would write through it.
                for (String key : was.keySet())
                {
                    Object value = was.get(key);

                    rebuilt.put(key, value instanceof JSONObject
                        ? new JSONObject(((JSONObject) value).toString()) : value);
                }
            }

            entry.getValue().put("points", rebuilt);
        }
    }

    /**
     * Whether a stored key names a square on a page.  The key is "page:x,y", and a page name may
     * itself contain a colon - so the comparison is on the prefix rather than on a split.
     */
    /**
     * Whether a stored key belongs to this page.
     *
     * By what the key PARSES to, not by what it starts with.  A key is "page:x,y" and a page name may
     * hold a colon - "Yard: Upper" is an ordinary thing to call a page - so "Yard: Upper:2,3" starts
     * with "Yard:" and was taken to be on the page called "Yard".  snapshotPage and restorePage then
     * captured and rewrote another page's entries, which is exactly what the editor's undo promises
     * not to do.
     *
     * parseTileKey splits on the LAST colon and has been exact all along.  This was the one place not
     * using it.
     */
    private static boolean isOnPage(String key, String page)
    {
        TileKey parsed = key == null ? null : parseTileKey(key);

        // A suffixed direction key - "page:x,y#state,index" - parses as its square, which is right:
        // it belongs to whatever page that square is on.
        if (parsed == null && key != null && key.lastIndexOf('#') > 0)
        {
            parsed = parseTileKey(key.substring(0, key.lastIndexOf('#')));
        }

        return parsed != null && parsed.getPage().equals(page);
    }

    /**
     * The same map with each list copied, so that nothing which edits a list in place can reach it.
     *
     * @param from a map whose values are lists, or null
     * @return a copy holding copies
     */
    private static Map<String, List<String>> copyLists(Map<String, List<String>> from)
    {
        Map<String, List<String>> out = new LinkedHashMap<>();

        if (from == null) return out;

        for (Map.Entry<String, List<String>> entry : from.entrySet())
        {
            out.put(entry.getKey(), entry.getValue() == null
                ? null : new ArrayList<>(entry.getValue()));
        }

        return out;
    }

    private static <T> Map<String, T> onPage(Map<String, T> from, String page)
    {
        Map<String, T> out = new LinkedHashMap<>();

        for (Map.Entry<String, T> entry : from.entrySet())
        {
            if (isOnPage(entry.getKey(), page)) out.put(entry.getKey(), entry.getValue());
        }

        return out;
    }

    private static Set<String> membersOnPage(Set<String> from, String page)
    {
        Set<String> out = new LinkedHashSet<>();

        for (String key : from)
        {
            if (isOnPage(key, page)) out.add(key);
        }

        return out;
    }

    private static <T> void putBack(Map<String, T> into, String page, Map<String, T> was)
    {
        for (java.util.Iterator<String> keys = into.keySet().iterator(); keys.hasNext();)
        {
            if (isOnPage(keys.next(), page)) keys.remove();
        }

        if (was != null) into.putAll(was);
    }

    private static void putMembersBack(Set<String> into, String page, Set<String> was)
    {
        for (java.util.Iterator<String> keys = into.iterator(); keys.hasNext();)
        {
            if (isOnPage(keys.next(), page)) keys.remove();
        }

        if (was != null) into.addAll(was);
    }

    /**
     * Lets go of everything the setup holds about a set of squares.
     *
     * Both halves: what is written ABOUT the square, and what elsewhere POINTS AT it.  A caption
     * naming a station that has just been built over names nothing, and a pairing to a signal that
     * has been built over would throw an accessory that is not there any more.
     *
     * @param squares stored keys, "page:x,y"
     */
    private void forgetSquares(Set<String> squares)
    {
        forgetSquares(squares, null);
    }

    /**
     * @param arriving each square that is MOVING and where it is going, so that a label about to be
     *        built over by the very thing it names can be told from one that is merely stale
     *
     * A caption is the odd one out among everything stored per square: it is a reference to somewhere
     * else rather than a fact about the square it sits on.  So when a square is built over, its caption
     * normally goes with the rest - it named track that is gone.
     *
     * Except when what built over it is the very station the caption names.  A platform's name is
     * usually written on a blank square beside it, and "beside" is often the square below - so nudging
     * that platform down one square lands it on its own label, and the label was thrown away by the
     * same gesture that carried the platform.  The name vanished for a move of one square in one
     * direction and survived every other, which is exactly how Adam found it.
     *
     * Kept and left where it is.  A caption may sit on its own station's square - that is how a name
     * gets drawn over a platform rather than beside it - so the entry that survives here is repointed
     * by the caller and ends up naming the tile it is now sitting on.
     *
     * The test is that the station it names is arriving on THIS square, not that it is moving at all.
     * Those are different questions the moment more than one tile moves at once: a column move carries
     * twenty tiles, and a label anywhere in the destination column names one of them roughly as often
     * as not - which would spare a label that some other tile has just been built over the top of, and
     * leave a station's name written on track it has nothing to do with.
     */
    private void forgetSquares(Set<String> squares, Map<String, String> arriving)
    {
        if (squares == null || squares.isEmpty()) return;

        for (String key : squares)
        {
            pointNames.remove(key);
            tileLengths.remove(key);
            barredArrivals.remove(key);
            linkNames.remove(key);
            stationSignals.remove(key);
            blockedPoints.remove(key);
            portals.remove(key);

            String names = captions.get(key);

            if (names == null || arriving == null || !key.equals(arriving.get(names)))
            {
                captions.remove(key);
            }

            stations.remove(key);
            disabledPortals.remove(key);
        }

        // A direction is keyed by the square and a route across it, so it is stored suffixed.
        //
        // This loop is the only thing that removes them. The list above used to carry a
        // `tileDirections.remove(key)` as its eleventh member - written because everything else was
        // there, and dead from the day it was written, because a bare square never matches a suffixed
        // key (DD-A1). It is gone, and this handles a bare key too, so nothing depends on every
        // direction having been written with a suffix.
        for (java.util.Iterator<String> keys = tileDirections.keySet().iterator(); keys.hasNext();)
        {
            String key = keys.next();
            int at = key.lastIndexOf('#');

            if (squares.contains(at >= 0 ? key.substring(0, at) : key)) keys.remove();
        }

        // And what named them
        for (java.util.Iterator<Map.Entry<String, String>> pairs = portals.entrySet().iterator();
            pairs.hasNext();)
        {
            if (squares.contains(pairs.next().getValue())) pairs.remove();
        }

        // A caption elsewhere naming one of these squares named track that is gone.
        //
        // No exception for the arriving tiles, and none is possible: a square that is being vacated is
        // never in this set - moveTiles builds it by excluding them - so a caption naming one of those
        // is not looked at here at all.  It is repointed afterwards instead.
        for (java.util.Iterator<Map.Entry<String, String>> pairs = captions.entrySet().iterator();
            pairs.hasNext();)
        {
            if (squares.contains(pairs.next().getValue())) pairs.remove();
        }

        for (java.util.Iterator<Map.Entry<String, List<String>>> pairs
            = stationSignals.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<String, List<String>> pair = pairs.next();

            pair.getValue().removeAll(squares);

            if (pair.getValue().isEmpty()) pairs.remove();
        }

        // The same for the squares a station is held back by: a restriction naming track that has been
        // built over is one nothing can satisfy or clear.
        for (java.util.Iterator<Map.Entry<String, List<String>>> pairs
            = blockedPoints.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<String, List<String>> pair = pairs.next();

            pair.getValue().removeAll(squares);

            if (pair.getValue().isEmpty()) pairs.remove();
        }

        // The configurations key by square as well - facings, placements, homes, lengths
        for (JSONObject configuration : configurations.values())
        {
            if (!configuration.has("points")) continue;

            JSONObject points = configuration.getJSONObject("points");

            for (String key : squares)
            {
                points.remove(key);
            }
        }
    }

    /**
     * The same for a map whose keys are a square with something else appended after a '#'.
     *
     * The square is the part that moves; whatever follows it identifies which of that square's several
     * entries this is, and travels unchanged.
     */
    private static <T> void moveSuffixedKeys(Map<String, T> map, Map<String, String> moves)
    {
        Map<String, T> out = new LinkedHashMap<>();

        // The ones staying put first, so a tile arriving on a square that is not moving replaces what
        // was there rather than the other way round - the same order moveKeys uses.
        for (Map.Entry<String, T> entry : map.entrySet())
        {
            if (movedSuffixed(entry.getKey(), moves) == null) out.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, T> entry : map.entrySet())
        {
            String moved = movedSuffixed(entry.getKey(), moves);

            if (moved != null) out.put(moved, entry.getValue());
        }

        map.clear();
        map.putAll(out);
    }

    /**
     * @return where a suffixed key goes, or null if its square is not moving
     */
    private static String movedSuffixed(String key, Map<String, String> moves)
    {
        int at = key.lastIndexOf('#');

        String square = at < 0 ? key : key.substring(0, at);
        String suffix = at < 0 ? "" : key.substring(at);

        String moved = moves.get(square);

        return moved == null ? null : moved + suffix;
    }

    /**
     * Rewrites the keys of a map, moved keys winning over ones that merely stayed.
     */
    private static <T> void moveKeys(Map<String, T> map, Map<String, String> moves)
    {
        Map<String, T> out = new LinkedHashMap<>();

        for (Map.Entry<String, T> entry : map.entrySet())
        {
            if (!moves.containsKey(entry.getKey())) out.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, T> entry : map.entrySet())
        {
            if (moves.containsKey(entry.getKey())) out.put(moves.get(entry.getKey()), entry.getValue());
        }

        map.clear();
        map.putAll(out);
    }

    /**
     * The same for a map whose values are LISTS of squares.
     *
     * Separately named rather than overloaded: erasure makes both signatures the same method, so a
     * list-valued copy of any of these helpers needs its own name whether or not that reads better.
     */
    private static void moveListValues(Map<String, List<String>> map, Map<String, String> moves)
    {
        for (Map.Entry<String, List<String>> entry : map.entrySet())
        {
            List<String> out = new ArrayList<>();

            for (String value : entry.getValue())
            {
                String moved = moves.get(value);

                out.add(moved == null ? value : moved);
            }

            entry.setValue(out);
        }
    }

    /**
     * Repoints values that name a square that moved.  In place: the entry stays where it is.
     */
    private static void moveValues(Map<String, String> map, Map<String, String> moves)
    {
        for (Map.Entry<String, String> entry : map.entrySet())
        {
            String moved = moves.get(entry.getValue());

            if (moved != null) entry.setValue(moved);
        }
    }

    /**
     * The same for a set of squares.
     */
    private static void moveMembers(Set<String> set, Map<String, String> moves)
    {
        Set<String> out = new LinkedHashSet<>();

        for (String key : set)
        {
            String moved = moves.get(key);

            out.add(moved == null ? key : moved);
        }

        set.clear();
        set.addAll(out);
    }

    /**
     * What a reconciliation found.  Nothing here is acted on silently: the whole point is that a diagram
     * changing under a setup should be visible.
     */
    public static class Reconciliation
    {
        /**
         * Nothing was reconciled.  For the callers that decline to prune - see AutonomySession.save,
         * which declines while a page renumber is outstanding.
         */
        public Reconciliation()
        {
        }

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
        // dropMissing tests the KEY - the station's square.  The VALUES are squares too, and this was
        // the one square-referencing collection whose values reconcile never looked at: portals are
        // checked on both ends, and a caption's two ends inside reconcileCaptions.  forgetSquares covers a signal
        // square that is BUILT OVER, so what was left was the plain deletion - and the pairing then
        // survived every save, ready to be INHERITED by whatever is drawn at those coordinates next.
        // That is the defect the linkNames drop below was written for, applied to the one collection
        // that commands real hardware: autonomy would start throwing an accessory nobody paired.
        report.droppedTileProperties.addAll(dropMissing(stationSignals, keys, false));
        report.droppedTileProperties.addAll(dropMissing(blockedPoints, keys, false));

        for (java.util.Iterator<Map.Entry<String, List<String>>> pairs
            = stationSignals.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<String, List<String>> pair = pairs.next();

            // A NEW list rather than removing from the one held: a page snapshot may be holding it for
            // the editor's undo, and editing it in place is how undo came to restore the deletion.
            List<String> kept = new ArrayList<>();

            for (String signal : pair.getValue())
            {
                if (keys.contains(signal)) kept.add(signal);
                else report.droppedTileProperties.add("protecting signal at " + signal);
            }

            if (kept.isEmpty()) pairs.remove();
            else pair.setValue(kept);
        }

        // And the squares a station is held back by, on the same rule and for the same reason: a
        // restriction watching a square that no longer exists cannot be satisfied, and would be
        // INHERITED by whatever is drawn there next.
        for (java.util.Iterator<Map.Entry<String, List<String>>> pairs
            = blockedPoints.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<String, List<String>> pair = pairs.next();

            List<String> kept = new ArrayList<>();

            for (String blocker : pair.getValue())
            {
                if (keys.contains(blocker)) kept.add(blocker);
                else report.droppedTileProperties.add("restriction watching " + blocker);
            }

            if (kept.isEmpty()) pairs.remove();
            else pair.setValue(kept);
        }

        // linkNames and disabledPortals, which were the only two of the eleven kept collections this
        // method said nothing about (DD-A1).
        //
        // Not untidiness. Both are remembered BY SQUARE, so a name and a switched-off flag for track
        // that no longer exists sat in the file indefinitely and were INHERITED by the next link drawn
        // on that square - which arrived pre-named and already disabled, with nothing saying why.
        //
        // Reported like everything else here, because a diagram edit that quietly costs a link name
        // should be visible rather than discovered later.
        report.droppedTileProperties.addAll(dropMissing(linkNames, keys, false));

        for (String key : dropMissingMembers(disabledPortals, keys))
        {
            report.droppedTileProperties.add("link switched off at " + key);
        }

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

        // A station that was never NAMED is not covered by the loop above, which walks the names whose
        // square has gone - so a square carrying a designation and no name was never visited, and there
        // was no dropMissingMembers(stations, keys) to match the one written for disabledPortals.  An
        // unnamed station is an ordinary state: setStation asks for no name, and placeCaption has a
        // "not named yet" answer for exactly this.  Left behind, the square stays a station for good,
        // so a sensor drawn at those coordinates later is silently one again - and checkNames raises a
        // blocking UNNAMED_STATION about a square nobody can see.
        //
        // Only the unnamed ones.  A named station whose tile is gone is kept above when a configuration
        // still refers to it, so the user can find it; nothing can refer to one with no name, so that
        // rule has nothing to say here.
        List<String> orphanStations = new ArrayList<>();

        for (String key : stations)
        {
            if (!keys.contains(key) && !pointNames.containsKey(key)) orphanStations.add(key);
        }

        for (String key : orphanStations)
        {
            stations.remove(key);
            report.droppedTileProperties.add("station at " + key);
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
        "barredArrivals", "stationSignals", "blockedPoints",
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
        // FIRST, because everything below is translated out of page ids and the translation needs to
        // know what those ids were called when this file was written.
        //
        // It used to be read after the tileLengths loop, which is the one collection translated inline
        // rather than by an untranslate* call below - so lengths were resolved against an empty map
        // while the other ten were resolved against a full one.  Harmless for as long as the
        // translation ignored this map, and wrong the moment it stopped ignoring it.
        readStringMap(root, "pages", pageNamesWhenWritten);

        readStringMap(root, "pointNames", pointNames);
        readStringSet(root, "stations", stations);
        readStringMap(root, "tileDirections", tileDirections);
        readStringMap(root, "barredArrivals", barredArrivals);
        readStringListMap(root, "stationSignals", stationSignals);
        readStringListMap(root, "blockedPoints", blockedPoints);
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

        // stored against page ids; brought back to the names the rest of the application uses
        untranslate(pointNames);
        untranslate(tileDirections);
        untranslate(barredArrivals);
        untranslateTileListMap(stationSignals);
        untranslateTileListMap(blockedPoints);
        untranslate(linkNames);
        untranslatePortals();
        untranslateTileMap(captions);
        untranslateSet(stations);
        untranslateSet(disabledPortals);
        untranslatePages(excludedPages);

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
        // Was missing, and clearShared() below has always had it.  load() empties the store and then
        // reads the file over it, and readShared only PUTS what the file holds - so a pairing made
        // since the last save survived a discard, and the next save wrote it to disk.  A signal
        // somebody had cancelled was then thrown on real hardware.
        stationSignals.clear();
        blockedPoints.clear();
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

    /**
     * The lowest version that can read what is about to be written.
     *
     * Not simply VERSION.  Bumping every file to 2 would make every setup this build touches
     * unreadable by the previous one, including the great majority that use nothing new - and the
     * whole point of writing a single signal as a bare string is that those files stay readable.  So
     * the number describes the SHAPES actually present rather than the build that wrote them, and it
     * rises only for the file that really does need the newer reader.
     *
     * @return 2 where some station carries more than one signal, 1 otherwise
     */
    private int versionWritten()
    {
        for (List<String> signals : stationSignals.values())
        {
            if (signals != null && signals.size() > 1) return 2;
        }

        return 1;
    }

    private File setupFile()
    {
        return new File(folder(), SETUP_FILE);
    }

    private File configurationFile(String name)
    {
        return new File(folder(), CONFIGURATION_PREFIX + CS2File.sanitizeFilename(name) + ".json");
    }

    /**
     * Whether another configuration would be written to the same file as this name.
     *
     * The name is free text and the filename is sanitised, and sanitising is many to one - every
     * character a filename may not hold becomes an underscore.  So "Night: Yard" and "Night_ Yard" are
     * two different configurations by name and one file on disk: saving wrote both to it, the second
     * over the first, and the next load - which rebuilds the list by scanning the folder - came back
     * with one of them simply gone.  Deleting or renaming either took the other's data with it.
     *
     * Checked at the two doors a name comes in by, rather than at save time, so the answer arrives
     * while the user is still looking at the name they typed.
     *
     * @param name the name being taken
     * @param except a name that may share the file - the one being renamed away from
     * @return whether some other configuration already owns that file
     */
    private boolean fileNameTaken(String name, String except)
    {
        File wanted = configurationFile(name);

        for (String existing : configurations.keySet())
        {
            if (existing.equals(name) || existing.equals(except)) continue;

            if (configurationFile(existing).equals(wanted)) return true;
        }

        return false;
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

        return pageOf(key.substring(0, colon)) + key.substring(colon);
    }

    /**
     * What page a stored id means, which is not always the page that holds that id now.
     *
     * Two different things can have happened since this file was written, and they want opposite
     * answers - the same pair readShared tells apart to raise its warning:
     *
     *   renamed    - the same page, called something else.  The id is the part that held still, so the
     *                current index is right and the settings follow the page.
     *   renumbered - a DIFFERENT page holds this id now.  The NAME is the part that held still, so the
     *                current index is wrong: it would attach a page of names, lengths and stations to
     *                whatever track happens to sit at that number today.
     *
     * The deciding question is the same one pageIdConflicts asks - whether the name this id used to
     * carry still exists somewhere.  If it does, this is a renumber and the name is followed.
     *
     * This is why "pages" is written at all, and until now it was only ever used to warn: the reading
     * went through the current index either way, so a renumber silently reattached the whole setup and
     * the next save reconciled away every setting whose coordinates did not exist on the page it had
     * been given to.  Adam lost 19 point names, 14 stations, 22 directions and 15 captions that way on
     * 2026-08-23, to a page rename that moved one page to the end of the index.
     *
     * Both branches agree whenever nothing has moved, which is the ordinary case.
     *
     * @param id the page id as stored in the file
     * @return the page name those settings belong to, or the id itself when nothing is known about it
     */
    private String pageOf(String id)
    {
        String whenWritten = pageNamesWhenWritten.get(id);

        // Renumbered: that page is still here, under a different number
        if (whenWritten != null && pageNameToId.containsKey(whenWritten)) return whenWritten;

        String now = pageIdToName.get(id);

        return now == null ? id : now;
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

    /**
     * The same for a map whose values are lists of squares.
     */
    private void untranslateTileListMap(Map<String, List<String>> map)
    {
        Map<String, List<String>> out = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : map.entrySet())
        {
            List<String> values = new ArrayList<>();

            for (String value : entry.getValue())
            {
                values.add(fromStored(value));
            }

            out.put(fromStored(entry.getKey()), values);
        }

        map.clear();
        map.putAll(out);
    }

    private Map<String, Object> translateTileListMap(Map<String, List<String>> map)
    {
        Map<String, Object> out = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> entry : map.entrySet())
        {
            List<String> values = new ArrayList<>();

            for (String value : entry.getValue())
            {
                values.add(toStored(value));
            }

            // One is written as a bare string, which is what every version before this one wrote and
            // is all a station with a single signal - most of them - ever needs.  A file gains an
            // array only where somebody has actually paired a second signal.
            out.put(toStored(entry.getKey()), values.size() == 1 ? values.get(0) : new JSONArray(values));
        }

        return out;
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

    /**
     * The same for a set of whole PAGES rather than of squares.
     *
     * toStored and fromStored split a key on its page and leave anything without a colon alone, so a
     * bare page name went through them unchanged - which is how the one collection that holds page
     * names came to be the one collection stored by name.
     *
     * A page the index has never heard of keeps its name, which is what makes this safe on a file
     * written before the change and on a page added since the index was read.
     */
    private Set<String> translatePages(Set<String> pages)
    {
        Set<String> out = new LinkedHashSet<>();

        for (String page : pages)
        {
            String id = pageNameToId.get(page);

            out.add(id == null ? page : id);
        }

        return out;
    }

    private void untranslatePages(Set<String> pages)
    {
        Set<String> out = new LinkedHashSet<>();

        for (String stored : pages)
        {
            // The same question as fromStored - an excluded page is named by id like everything else,
            // and a renumber moved it just the same.  This was the collection that used to be written
            // raw, and getting it wrong silently re-includes a page in autonomy.
            out.add(pageOf(stored));
        }

        pages.clear();
        pages.addAll(out);
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

    /**
     * The same key on a renamed page.
     *
     * Same rule as isOnPage, and it matters more here: renaming "Yard" would have rewritten every key
     * belonging to "Yard: Upper" as well, orphaning that page's whole setup for the next reconcile to
     * find and drop.  Nothing renames a page today - but "Manage Pages" is where a rename lands, and
     * that menu was built this week.
     */
    private static String rekeyOne(String key, String fromPage, String toPage)
    {
        if (key == null) return null;

        // The suffix, where there is one, is carried across untouched
        int hash = key.lastIndexOf('#');

        String square = hash > 0 ? key.substring(0, hash) : key;
        String suffix = hash > 0 ? key.substring(hash) : "";

        TileKey parsed = parseTileKey(square);

        if (parsed == null || !parsed.getPage().equals(fromPage)) return key;

        return toPage + ":" + parsed.getX() + "," + parsed.getY() + suffix;
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

    private static void rekeyListValues(Map<String, List<String>> map, String fromPage, String toPage)
    {
        for (Map.Entry<String, List<String>> entry : map.entrySet())
        {
            List<String> out = new ArrayList<>();

            for (String value : entry.getValue())
            {
                out.add(rekeyOne(value, fromPage, toPage));
            }

            entry.setValue(out);
        }
    }

    /**
     * The same, for a plain set of squares.
     *
     * @param members squares the set remembers
     * @param existing the squares the diagram still has
     * @return the ones that were dropped
     */
    private static List<String> dropMissingMembers(Set<String> members, Set<String> existing)
    {
        List<String> gone = new ArrayList<>();

        for (String key : members)
        {
            if (!existing.contains(key)) gone.add(key);
        }

        members.removeAll(gone);

        return gone;
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

    /**
     * Reads a map whose values are either one square or an array of them.
     *
     * Both shapes, because this field held a bare string until 3.0.0 and every setup written before
     * then still has one.  Nothing migrates: the string is read as a list of one, and the file gains
     * an array only when a second signal is paired and it is saved again.
     */
    private static void readStringListMap(JSONObject root, String field, Map<String, List<String>> into)
    {
        JSONObject object = root.optJSONObject(field);

        if (object == null) return;

        for (String key : object.keySet())
        {
            List<String> values = new ArrayList<>();

            JSONArray several = object.optJSONArray(key);

            if (several != null)
            {
                for (int at = 0; at < several.length(); at++)
                {
                    String one = several.optString(at, null);

                    if (one != null && !values.contains(one)) values.add(one);
                }
            }
            else
            {
                String one = object.optString(key, null);

                if (one != null) values.add(one);
            }

            if (!values.isEmpty()) into.put(key, values);
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
