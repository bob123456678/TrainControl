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
import org.traincontrol.automationui.TileGraph.DirectionKey;
import org.traincontrol.automationui.TileGraph.SquareKeyed;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.marklin.file.CS2File;
import org.traincontrol.util.Util;

/**
 * What the diagram cannot say: names, stations, lengths, directions, and where the locomotives go.  Geometry is
 * re-derived from the track on every build, so this holds only the decisions a person made that reading the diagram
 * would not recover.
 *
 * The files live inside the track diagram folder, so the setup travels with its layout:
 *
 *   config/autonomy/setup.json                 one per layout: names, stations, lengths, directions,
 *                                              portal pairings, excluded pages, the active configuration
 *   config/autonomy/configuration-<name>.json  one per configuration: point properties, placements,
 *                                              homes, exclusions, globals, timetable
 *
 * One file per configuration makes duplicating one a file copy and lets it be handed on alone.
 *
 * @author Adam
 */
public class AutonomyCompanionStore
{
    /**
     * The highest schema this class can read.  A newer file is refused rather than read partially, since dropping
     * fields it does not recognize would lose the user's work on the next save.  2 is "a station may carry several
     * protecting signals" (an array in stationSignals).
     */
    public static final int VERSION = 2;

    public static final String ERROR_VERSION = "autosetup.ui.errorCompanionVersion";
    public static final String ERROR_NOT_LOCAL = "autosetup.ui.errorAutonomyNeedsLocalLayout";
    public static final String ERROR_NAME_IN_USE = "autosetup.ui.errorNameInUse";

    /** A name refused because it would be saved in another configuration's file - see `NameSharesAFile`. */
    public static final String ERROR_NAME_SHARES_A_FILE = "autosetup.ui.errorNameSharesAFile";

    /**
     * A configuration name refused because it would be saved in another configuration's file (sanitizing is many to
     * one).  The message is a key, as in every refusal here; the colliding configuration travels beside it.
     */
    public static final class NameSharesAFile extends IOException
    {
        private static final long serialVersionUID = 1L;

        private final String sharedWith;

        /** @param sharedWith the configuration already saved in that file */
        public NameSharesAFile(String sharedWith)
        {
            super(ERROR_NAME_SHARES_A_FILE);

            this.sharedWith = sharedWith;
        }

        /** @return the configuration already saved in that file */
        public String getSharedWith()
        {
            return sharedWith;
        }
    }

    private static final String FOLDER = "config/autonomy";
    private static final String SETUP_FILE = "setup.json";
    private static final String CONFIGURATION_PREFIX = "configuration-";

    /** Where an earlier build kept a configuration's timetable entries set aside; read only, to bring them back. */
    private static final String ENTRIES_SET_ASIDE = "timetableAside";

    private final File layoutFolder;

    // shared: one copy per layout, describing the physical diagram
    private final Map<TileKey, String> pointNames = new LinkedHashMap<>();
    private final Set<TileKey> stations = new LinkedHashSet<>();
    private final Map<TileKey, Integer> tileLengths = new LinkedHashMap<>();
    private final Map<DirectionKey, String> tileDirections = new LinkedHashMap<>();

    /**
     * Which sides a station refuses to let trains ARRIVE by, keyed by square: the barred sides rather than the allowed
     * ones, so a station nobody restricted stores nothing and a side added later is open.  Comma-separated side names.
     */
    private final Map<TileKey, String> barredArrivals = new LinkedHashMap<>();

    /** Which sides trains may not arrive by; empty when the station takes trains from anywhere. */
    public Set<TilePorts.Side> getBarredArrivals(TileKey tile)
    {
        Set<TilePorts.Side> out = new LinkedHashSet<>();

        String stored = tile == null ? null : barredArrivals.get(tile);

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
                // A side name this build does not have is skipped: the rest of the setting is still good.
            }
        }

        return out;
    }

    /** @param barred the sides trains may not arrive by; empty or null takes them from anywhere */
    public void setBarredArrivals(TileKey tile, Set<TilePorts.Side> barred)
    {
        if (tile == null) return;

        if (barred == null || barred.isEmpty())
        {
            // Nothing is stored for the default, so an untouched setup has nothing to reconcile.
            barredArrivals.remove(tile);

            return;
        }

        StringBuilder text = new StringBuilder();

        for (TilePorts.Side side : barred)
        {
            if (text.length() > 0) text.append(",");

            text.append(side.name());
        }

        barredArrivals.put(tile, text.toString());
    }

    /** Every square carrying an arrival restriction, against the sides it bars. */
    public Map<TileKey, Set<TilePorts.Side>> getBarredArrivals()
    {
        Map<TileKey, Set<TilePorts.Side>> out = new LinkedHashMap<>();

        for (TileKey key : barredArrivals.keySet())
        {
            TileKey tile = key;

            if (tile != null) out.put(tile, getBarredArrivals(tile));
        }

        return out;
    }
    private final Map<TileKey, TileKey> portals = new LinkedHashMap<>();

    /**
     * The signals that protect each station, square to squares, so it survives a page rename and is reconciled away
     * with the station.  Paired by hand: the nearest signal on the approach is not always the protecting one, and a
     * wrong guess throws a real signal.  Several per station (one per approach); a bare string is still read as one.
     */
    private final Map<TileKey, List<TileKey>> stationSignals = new LinkedHashMap<>();

    /** Squares whose occupancy makes a station unavailable (FR-001), resolved to a Point name only when built. */
    private final Map<TileKey, List<TileKey>> blockedPoints = new LinkedHashMap<>();

    /**
     * The signals thrown red when a train ARRIVES at a station as the end of its journey, its entry guard (FR-096).
     * Nothing turns it green again; the next route does.  Apart from `stationSignals`: that is an aspect derived from
     * the platform being claimed, this a command on arrival.
     */
    private final Map<TileKey, List<TileKey>> entrySignals = new LinkedHashMap<>();

    /** The squares that make a station unavailable while occupied, in the order they were added. */
    public List<TileKey> getBlockingPoints(TileKey station)
    {
        List<TileKey> out = new ArrayList<>();

        if (station == null) return out;

        List<TileKey> keys = blockedPoints.get(station);

        if (keys == null) return out;

        for (TileKey key : keys)
        {
            TileKey blocker = key;

            if (blocker != null) out.add(blocker);
        }

        return out;
    }

    /** Replaces the squares that hold a station back; empty or null clears the restriction. */
    public void setBlockingPoints(TileKey station, List<TileKey> blockers)
    {
        if (station == null) return;

        List<TileKey> keys = new ArrayList<>();

        if (blockers != null)
        {
            for (TileKey blocker : blockers)
            {
                // De-duplicated here rather than in the picker, and never the station itself: standing at a station
                // already decides whether it is free, so watching it from itself would leave nothing able to be sent there.
                if (blocker != null && !blocker.equals(station) && !keys.contains(blocker))
                {
                    keys.add(blocker);
                }
            }
        }

        if (keys.isEmpty()) blockedPoints.remove(station);
        else blockedPoints.put(station, keys);
    }

    /** Every station held back by something, against the squares watched for it. */
    public Map<TileKey, List<TileKey>> getBlockingPoints()
    {
        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        for (TileKey key : blockedPoints.keySet())
        {
            TileKey station = key;

            if (station == null) continue;

            List<TileKey> blockers = getBlockingPoints(station);

            if (!blockers.isEmpty()) out.put(station, blockers);
        }

        return out;
    }

    /** The square of the first signal protecting a station, or null. */
    public TileKey getProtectingSignal(TileKey station)
    {
        List<TileKey> all = getProtectingSignals(station);

        return all.isEmpty() ? null : all.get(0);
    }

    /** The squares of every signal protecting a station, in the order they were paired. */
    public List<TileKey> getProtectingSignals(TileKey station)
    {
        List<TileKey> out = new ArrayList<>();

        if (station == null) return out;

        List<TileKey> keys = stationSignals.get(station);

        if (keys == null) return out;

        for (TileKey key : keys)
        {
            TileKey signal = key;

            if (signal != null) out.add(signal);
        }

        return out;
    }

    /** Pairs one signal with a station; a null signal unpairs everything. */
    public void setProtectingSignal(TileKey station, TileKey signal)
    {
        setProtectingSignals(station, signal == null
            ? Collections.<TileKey>emptyList() : Collections.singletonList(signal));
    }

    /** Replaces every signal protecting a station; empty or null unpairs. */
    public void setProtectingSignals(TileKey station, List<TileKey> signals)
    {
        if (station == null) return;

        List<TileKey> keys = new ArrayList<>();

        if (signals != null)
        {
            for (TileKey signal : signals)
            {
                // De-duplicated here so no writer (an import, a restored snapshot) can leave one signal twice; never
                // the station's own entry guard.
                if (signal != null && !keys.contains(signal) && !onList(entrySignals, station, signal)) keys.add(signal);
            }
        }

        if (keys.isEmpty())
        {
            stationSignals.remove(station);
        }
        else
        {
            stationSignals.put(station, keys);
        }
    }

    /** Every station that has protecting signals, against those signals' squares. */
    public Map<TileKey, List<TileKey>> getProtectingSignals()
    {
        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        for (TileKey key : stationSignals.keySet())
        {
            TileKey station = key;

            if (station == null) continue;

            List<TileKey> signals = getProtectingSignals(station);

            if (!signals.isEmpty()) out.put(station, signals);
        }

        return out;
    }

    /** The squares of every signal guarding the way into a station (FR-096), in the order they were paired. */
    public List<TileKey> getEntrySignals(TileKey station)
    {
        List<TileKey> out = new ArrayList<>();

        if (station == null) return out;

        List<TileKey> keys = entrySignals.get(station);

        if (keys != null) for (TileKey key : keys) if (key != null) out.add(key);

        return out;
    }

    /** Replaces every signal guarding the way into a station (FR-096); empty or null unpairs. */
    public void setEntrySignals(TileKey station, List<TileKey> signals)
    {
        if (station == null) return;

        List<TileKey> keys = new ArrayList<>();

        if (signals != null)
        {
            // De-duplicated as the protecting list is, and never the station's own exit guard: a signal on both lists
            // is thrown red on arrival and then set by the platform's occupancy.  Refused in both setters; the editor
            // says why.
            for (TileKey signal : signals)
            {
                if (signal != null && !keys.contains(signal) && !onList(stationSignals, station, signal)) keys.add(signal);
            }
        }

        if (keys.isEmpty()) entrySignals.remove(station);
        else entrySignals.put(station, keys);
    }

    /** Whether a station's list in one of the two guard maps holds this signal */
    private static boolean onList(Map<TileKey, List<TileKey>> guards, TileKey station, TileKey signal)
    {
        List<TileKey> listed = guards.get(station);

        return listed != null && listed.contains(signal);
    }

    /** Every station with entry-guard signals, against those signals' squares (FR-096). */
    public Map<TileKey, List<TileKey>> getEntrySignals()
    {
        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        for (TileKey station : entrySignals.keySet())
        {
            if (station == null) continue;

            List<TileKey> signals = getEntrySignals(station);

            if (!signals.isEmpty()) out.put(station, signals);
        }

        return out;
    }

    /**
     * Where each station caption is drawn and which square it is about: caption square to sensor square.  Kept here
     * because a label bound to a Point BY NAME broke on every rename or station split; autonomy never writes the
     * layout file.
     */
    private final Map<TileKey, TileKey> captions = new LinkedHashMap<>();
    private final Map<TileKey, String> linkNames = new LinkedHashMap<>();
    private final Set<String> excludedPages = new LinkedHashSet<>();

    /** Pages the operator turned back on after the repeated-sensor rule shut them. */
    private final Set<String> keptDespiteRepeats = new LinkedHashSet<>();

    private String activeConfiguration = null;

    // Anything a newer version wrote that this one does not understand, kept so a round trip through an
    // older TrainControl does not quietly delete it
    private final Map<String, Object> unknownSharedFields = new LinkedHashMap<>();

    private final Map<String, JSONObject> configurations = new LinkedHashMap<>();

    // Pages are keyed on disk by the id the Central Station gave them, not by name, so a rename does not orphan every
    // entry on the page.
    private final Map<String, String> pageNameToId = new LinkedHashMap<>();
    private final Map<String, String> pageIdToName = new LinkedHashMap<>();

    /** The pages the last `importBundle` left out because this layout does not have them (MT-380). */
    private final List<String> pagesLeftOutOfLastImport = new ArrayList<>();

    /** The pages the last import left out because this layout does not have them; empty before any import. */
    public List<String> getPagesLeftOutOfLastImport()
    {
        return Collections.unmodifiableList(new ArrayList<>(pagesLeftOutOfLastImport));
    }

    // The name each page id had when the setup was last written, so a renumber can be told from a rename
    private final Map<String, String> pageNamesWhenWritten = new LinkedHashMap<>();
    private final Map<String, String> pageIdConflicts = new LinkedHashMap<>();

    /**
     * Entries for pages the index cannot resolve, written back verbatim (OB-067).  Ids and names can collide (a page
     * may legally be called "2"), so an entry whose page is unknown cannot live in the collections without risking
     * reattachment to the wrong page.  It waits here as the JSON it arrived as and is merged back on save.  Field name
     * to the JSONObject or JSONArray of entries held for it.
     */
    private final Map<String, Object> heldForAbsentPages = new LinkedHashMap<>();

    /** @param layoutFolder the local layout folder, the one holding config/gleisbilder */
    public AutonomyCompanionStore(File layoutFolder)
    {
        this.layoutFolder = layoutFolder;
    }

    /**
     * Tells the store which page id belongs to which page name.  Entries are stored against the id so a rename does
     * not orphan a page of settings; the id is not trusted alone, since a reorder could reattach settings to the wrong
     * page, so the name each id had is recorded alongside and a mismatch is reported at load rather than acted on.
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
     * The highest page id this setup has ever recorded, live or absent.  `writeLayoutIndex` numbers a new page above
     * the highest id in the index FILE, which would reuse a retired id while its settings are still held here
     * (OB-067); this map remembers the highest.  A non-numeric id is skipped.  Answers 0 when nothing is known.
     */
    public int highestPageIdSeen()
    {
        int highest = 0;

        for (java.util.Collection<String> ids
            : java.util.Arrays.asList(pageNamesWhenWritten.keySet(), pageIdToName.keySet()))
        {
            for (String id : ids)
            {
                try
                {
                    highest = Math.max(highest, Integer.parseInt(id.trim()));
                }
                catch (NumberFormatException ignored)
                {
                    // Not a number, so not an id this method can reason about.
                }
            }
        }

        return highest;
    }

    /**
     * Whether this setup knows a page that is not among the ones the caller holds.  A caller with an incomplete
     * picture (CS2File skips a page that will not parse) must not prune on the strength of "this square does not
     * exist" (OB-068): save, but do not prune.  Answers the known names not in `loaded`, empty when the picture is
     * complete.
     */
    public java.util.List<String> pagesNotLoaded(java.util.Collection<String> loaded)
    {
        java.util.List<String> missing = new ArrayList<>();

        if (loaded == null) return missing;

        for (Map.Entry<String, String> written : pageNamesWhenWritten.entrySet())
        {
            String name = written.getValue();

            if (name == null || loaded.contains(name)) continue;

            // A page renamed outside TrainControl is still loaded: the Central Station keeps the id and changes the
            // name, so ask by id too, by the rule `resolvePage` uses.  Not `pageIsHere`: the index holds a stand-in for
            // a page that would not read (MT-135).
            String nowCalled = resolvePage(written.getKey());

            if (nowCalled != null && loaded.contains(nowCalled)) continue;

            missing.add(name);
        }

        return missing;
    }

    /**
     * Drops everything held for pages the operator has said are gone for good (FR-018).  A page absent from disk is
     * held (OB-067), so this is called only with the answer of the person who knows, never inferred.  An entry goes if
     * it names a gone page anywhere, key or value: releasing a pointer to a deleted page would put an id into the live
     * collections as a page NAME.  The recorded name goes too, so `pagesNotLoaded` stops naming the page.  Answers how
     * many held entries were dropped.
     */
    public int forgetHeldPages(java.util.Collection<String> goneByName)
    {
        if (goneByName == null || goneByName.isEmpty()) return 0;

        // The IDS the file recorded these pages under, not the names: a page may legally be called "5" (OB-092), and
        // declaring that name gone would delete the entries held for the page with ID 5.
        Set<String> parts = new LinkedHashSet<>();

        for (Map.Entry<String, String> was : pageNamesWhenWritten.entrySet())
        {
            if (goneByName.contains(was.getValue())) parts.add(was.getKey());
        }

        int dropped = 0;

        for (Map.Entry<String, Object> field : new LinkedHashMap<>(heldForAbsentPages).entrySet())
        {
            Object holding = field.getValue();

            if (holding instanceof JSONObject)
            {
                JSONObject src = (JSONObject) holding;
                JSONObject keep = new JSONObject();

                for (String key : src.keySet())
                {
                    if (mentionsAny(key, parts) || mentionsAny(src.get(key), parts))
                    {
                        dropped++;
                    }
                    else
                    {
                        keep.put(key, src.get(key));
                    }
                }

                if (keep.length() > 0) heldForAbsentPages.put(field.getKey(), keep);
                else heldForAbsentPages.remove(field.getKey());
            }
            else if (holding instanceof JSONArray)
            {
                JSONArray src = (JSONArray) holding;
                JSONArray keep = new JSONArray();

                for (int at = 0; at < src.length(); at++)
                {
                    if (mentionsAny(src.get(at), parts)) dropped++;
                    else keep.put(src.get(at));
                }

                if (keep.length() > 0) heldForAbsentPages.put(field.getKey(), keep);
                else heldForAbsentPages.remove(field.getKey());
            }
        }

        // Removed by value because the caller knows names and the map is keyed by id.
        pageNamesWhenWritten.values().removeAll(goneByName);

        return dropped;
    }

    /** Whether a stored key, or a value that may be one or a list of them, names a page in {@code parts}. */
    private boolean mentionsAny(Object held, Set<String> parts)
    {
        if (held instanceof JSONArray)
        {
            JSONArray each = (JSONArray) held;

            for (int at = 0; at < each.length(); at++)
            {
                if (mentionsAny(each.get(at), parts)) return true;
            }

            return false;
        }

        if (!(held instanceof String)) return false;

        String stored = (String) held;

        int colon = stored.lastIndexOf(':');

        // A bare page name, for excludedPages, which is a list of whole pages rather than of squares.
        return parts.contains(colon < 0 ? stored : stored.substring(0, colon));
    }

    /**
     * Whether this setup's keys can be trusted to mean the pages they name.  After a renumber an id no longer says
     * which page it meant; entries still land by recorded name (`resolvePage`), but "this square does not exist"
     * cannot be concluded from an id, so nothing may delete on it.  True while a renumber is outstanding.
     */
    public boolean isPageNumberingSuspect()
    {
        return !pageIdConflicts.isEmpty();
    }

    /**
     * Pages whose id now belongs to a different name than when the setup was written, recorded name to the name that id
     * now has.  Empty in normal use: a rename keeps the id; a renumbered page appears, and its settings belong to
     * whatever page held that id.
     */
    public Map<String, String> getPageIdConflicts()
    {
        return Collections.unmodifiableMap(pageIdConflicts);
    }

    /**
     * Whether this layout can hold an autonomy setup at all: the files live beside the diagram, so a layout read from
     * the Central Station has none until it is downloaded.
     */
    public boolean isUsable()
    {
        return layoutFolder != null && layoutFolder.isDirectory();
    }

    /** Whether a setup has been created for this layout yet. */
    public boolean exists()
    {
        return isUsable() && setupFile().isFile();
    }

    // loading and saving

    /**
     * Reads the setup and every configuration beside it.  A layout with no setup loads as empty rather than failing.
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

        // Read and parse BEFORE anything is thrown away: a failed load (sync lock, truncated write, setup from a newer
        // TrainControl) must leave the setup exactly as it was.
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

        // Parsed before anything is thrown away too.  A bare JSONException is unchecked and would walk out through the
        // IOException catches that expect a failed load to change nothing.
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
                    // Named, as an IOException: which file is unreadable tells the user what to do, and callers already
                    // survive that form.
                    throw new IOException(
                        I18n.f("autosetup.ui.errorConfigurationUnreadable", name,
                            String.valueOf(e.getMessage())), e);
                }

                bringBackTheEntriesSetAside(configuration);

                loaded.put(
                    configuration.optString("name",
                        name.substring(CONFIGURATION_PREFIX.length(), name.length() - 5)),
                    configuration);
            }
        }

        // What is here now, so a failure in the READ can put it back: readShared runs after clear() and its strict
        // accessors throw part way for a setup.json this build did not write.
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
     * Writes the setup and every configuration through Util.writeAtomically: a half-written file would read as a
     * layout nobody had set up.
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

    // shared data

    public String getPointName(TileKey tile)
    {
        return pointNames.get(tile);
    }

    /**
     * Every square that has been given a name, in the order they were named, for pickers of POINTS: an unnamed
     * square cannot be recognized in a list.
     */
    public List<TileKey> getNamedTiles()
    {
        List<TileKey> out = new ArrayList<>();

        for (TileKey key : pointNames.keySet())
        {
            TileKey tile = key;

            if (tile != null) out.add(tile);
        }

        return out;
    }

    /**
     * Every square that has been given a name, printed.  Kept in the PRINTED form (FR-013): callers key their own maps
     * by these strings, and converting would ripple into callers that have no reason to change.
     */
    public Map<String, String> getPointNames()
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, String> named : pointNames.entrySet())
        {
            out.put(named.getKey().toString(), named.getValue());
        }

        return Collections.unmodifiableMap(out);
    }

    public void setPointName(TileKey tile, String name)
    {
        if (name == null || name.trim().isEmpty())
        {
            pointNames.remove(tile);
        }
        else
        {
            pointNames.put(tile, name.trim());
        }
    }

    public boolean isStation(TileKey tile)
    {
        return stations.contains(tile);
    }

    public void setStation(TileKey tile, boolean station)
    {
        if (station)
        {
            stations.add(tile);
        }
        else
        {
            stations.remove(tile);
        }
    }

    /**
     * Whether this layout records any track length at all.  Asked of what was RECORDED, not of the graph's points: a
     * length belongs to a square and most squares carry no point.
     */
    public boolean measuresAnyTrack()
    {
        for (Integer length : tileLengths.values())
        {
            if (length != null && length > 0) return true;
        }

        return false;
    }

    /** The length assigned to this tile, 0 if none. */
    public int getTileLength(TileKey tile)
    {
        Integer value = tileLengths.get(tile);

        return value == null ? 0 : value;
    }

    /** Every square with a length recorded (FR-069), in the order they were measured. */
    public java.util.List<TileKey> tilesWithALength()
    {
        java.util.List<TileKey> out = new java.util.ArrayList<>();

        for (Map.Entry<TileKey, Integer> each : tileLengths.entrySet())
        {
            if (each.getValue() != null && each.getValue() > 0) out.add(each.getKey());
        }

        return out;
    }

    /** Forgets every length on every page (FR-069), answering how many squares had one. */
    public int clearEveryTileLength()
    {
        int had = tilesWithALength().size();

        tileLengths.clear();

        return had;
    }

    /**
     * Records that a square's length was answered 0 on purpose (OB-274), for adjacent tracks.  Unlike `setTileLength`
     * the 0 is KEPT: `getTileLength` answers 0 either way, `isTileLengthAnswered` tells the two apart, and the Mass
     * Assign Lengths walk, the Unmeasured Track display and the reducer (`answeredAtZero`, `Edge.isMeasured`, OB-300)
     * read it as measured track of no length, so a stretch of deliberate 0s is not judged.  A square that already has
     * a length keeps it.
     */
    public void answerTileLengthZero(TileKey tile)
    {
        if (tile == null) return;

        Integer had = tileLengths.get(tile);

        if (had != null && had > 0) return;

        tileLengths.put(tile, 0);
    }

    /** Whether a square's length has been answered at all: a length, or a deliberate 0 (OB-274). */
    public boolean isTileLengthAnswered(TileKey tile)
    {
        return tile != null && tileLengths.containsKey(tile);
    }

    /**
     * Lengths of 0 are not stored: 0 is what an unassigned tile means, so writing 0 here CLEARS a square, a deliberate
     * 0 included.  The one way a 0 is kept is `answerTileLengthZero` (OB-274).
     */
    public void setTileLength(TileKey tile, int length)
    {
        if (length <= 0)
        {
            tileLengths.remove(tile);
        }
        else
        {
            tileLengths.put(tile, length);
        }
    }

    /** Whether any direction has been set on this diagram; a fresh one has none, since a default is never stored. */
    public boolean hasTileDirections()
    {
        return !tileDirections.isEmpty();
    }

    public Direction getTileDirection(TileKey tile, RouteId routeId)
    {
        String value = tileDirections.get(new DirectionKey(tile, routeId));

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
     * Records a direction.  Null clears it, returning the route to the graph's default, which is not always BOTH, so a
     * default is never written as if it were a choice.
     */
    public void setTileDirection(TileKey tile, RouteId routeId, Direction direction)
    {
        if (direction == null)
        {
            tileDirections.remove(new DirectionKey(tile, routeId));
        }
        else
        {
            tileDirections.put(new DirectionKey(tile, routeId), direction.name());
        }
    }

    public String getLinkName(TileKey tile)
    {
        return linkNames.get(tile);
    }

    public void setLinkName(TileKey tile, String name)
    {
        if (name == null || name.trim().isEmpty())
        {
            linkNames.remove(tile);
        }
        else
        {
            linkNames.put(tile, name.trim());
        }
    }

    /** Links autonomy is to ignore, by tile; a fact about the diagram, stored with the track, not a configuration. */
    private final Set<TileKey> disabledPortals = new LinkedHashSet<>();

    public boolean isPortalDisabled(TileKey tile)
    {
        if (tile == null) return false;

        if (disabledPortals.contains(tile)) return true;

        // The PARTNER too, as TileGraph.portalClosed asks: a setup with one end off and the other on would draw the far
        // end as a live door over track no train can pass.
        TileKey partner = getPortalPartner(tile);

        return partner != null && disabledPortals.contains(partner);
    }

    /**
     * Switches a link in or out of the railway, and its partner with it (OB-041).  A pair is one doorway walked in both
     * directions; shut at one end only, it would be a route that exists one way, which nothing on the diagram says.
     * Here rather than in AutonomySession because this is where the partner is known.
     */
    public void setPortalDisabled(TileKey tile, boolean disabled)
    {
        set(tile, disabled);

        TileKey partner = getPortalPartner(tile);

        // Directly: the pairing is mutual, so calling this again would recurse back.
        if (partner != null) set(partner, disabled);
    }

    private void set(TileKey tile, boolean disabled)
    {
        if (disabled)
        {
            disabledPortals.add(tile);
        }
        else
        {
            disabledPortals.remove(tile);
        }
    }

    public TileKey getPortalPartner(TileKey tile)
    {
        return portals.get(tile);
    }

    /** Pairs two portals, mutually and exclusively: whatever either was paired with before is released. */
    public void pairPortals(TileKey a, TileKey b)
    {
        unpairPortal(a);
        unpairPortal(b);

        portals.put(a, b);
        portals.put(b, a);
    }

    public void unpairPortal(TileKey tile)
    {
        TileKey partner = portals.remove(tile);

        if (partner != null) portals.remove(partner);
    }

    /**
     * Repairs a setup that nothing has open, in place on disk.  A locomotive rename must reach the setup on disk too,
     * or the old name survives in the placement, home and exclusions and parseAuto invalidates the whole layout over a
     * locomotive it cannot resolve (OB-062).  A bare store is used rather than a session, which opens every page, can
     * raise a dialog and would fabricate a setup.json on a layout where autonomy was never touched.
     *
     * @param to the new name, or null when the locomotive is being deleted
     * @return true when a setup was found and rewritten, false when there was nothing to repair
     * @throws IOException if the setup exists but cannot be read or written
     */
    public static boolean repairLocomotiveOnDisk(File layoutFolder, String from, String to)
        throws IOException
    {
        if (from == null) return false;

        return repairOnDisk(layoutFolder, store ->
        {
            if (to == null) store.locomotiveDeleted(from);
            else store.locomotiveRenamed(from, to);
        });
    }

    /** Carries a page rename into a setup nothing has open, through a bare store (see repairLocomotiveOnDisk). */
    public static boolean renamePageOnDisk(File layoutFolder, String from, String to)
        throws IOException
    {
        if (from == null || to == null) return false;

        return repairOnDisk(layoutFolder, store -> store.renamePage(from, to));
    }

    /** Forgets a deleted page in a setup nothing has open (see renamePageOnDisk). */
    public static boolean deletePageOnDisk(File layoutFolder, String page) throws IOException
    {
        if (page == null) return false;

        return repairOnDisk(layoutFolder, store -> store.deletePage(page));
    }

    /** Something that can be done to a store. */
    private interface Repair
    {
        void apply(AutonomyCompanionStore store);
    }

    /** Opens a setup nothing has open, changes one thing about it, and puts it back; true when one was rewritten. */
    private static boolean repairOnDisk(File layoutFolder, Repair what) throws IOException
    {
        if (layoutFolder == null) return false;

        AutonomyCompanionStore store = new AutonomyCompanionStore(layoutFolder);

        // Nothing is created for a layout that has never had a setup.
        if (!store.exists()) return false;

        store.load();

        // Give it back the numbering the FILE was written under: nobody calls setPageIds on a bare store, and saving
        // would replace the file's record of what each id was called with {}, disarming the renumber detection.
        Map<String, String> nameToId = new LinkedHashMap<>();

        for (Map.Entry<String, String> page : store.pageNamesWhenWritten.entrySet())
        {
            nameToId.put(page.getValue(), page.getKey());
        }

        store.setPageIds(nameToId);

        // Read AGAIN, now that the pages have names: the first read had no numbering and returned id-form keys, which
        // renamePage and deletePage (working on NAMES) would silently ignore.
        store.load();

        what.apply(store);

        store.save();

        return true;
    }

    /**
     * Follows a locomotive's new name into every configuration, inactive ones included (captureFromLayout launders
     * only the active one).  The placement, the home assignment and the exclusion list hold it by NAME, and a stale
     * name invalidates the whole layout in parseAuto.
     */
    public void locomotiveRenamed(String from, String to)
    {
        if (from == null || to == null || from.equals(to)) return;

        repairLocomotive(from, to);
    }

    /**
     * Takes a deleted locomotive out of every configuration: the placement and the home go and the exclusion is
     * dropped, since any of them would name a locomotive that resolves to nothing.
     */
    public void locomotiveDeleted(String name)
    {
        if (name == null) return;

        repairLocomotive(name, null);
    }

    /** Renames the locomotive in every configuration; a null `to` removes it. */
    private void repairLocomotive(String from, String to)
    {
        for (JSONObject configuration : configurations.values())
        {
            repairLocomotiveIn(configuration, from, to);
        }

        repairTheUnfinishedEditNote(from, to);
    }

    /**
     * Whether the last rename or deletion failed to reach the pre-edit note: not fatal, but the revert on the next
     * start would put back a name the database no longer has.  Recorded rather than logged: this class has no logger.
     */
    private boolean noteRepairFailed;

    public boolean didTheNoteRepairFail()
    {
        return noteRepairFailed;
    }
    /**
     * Follows a locomotive's new name into the pre-edit note on disk, `config/autonomy/setup-before-edit.json`, which
     * holds locomotives by name and outlives the editor window: after a rename and a crash, `revertUnfinishedEdit`
     * would write the old name back, which `parseAuto` refuses by invalidating the whole layout.  A note that cannot be
     * read is left as it is; `unfinishedEdit` refuses one it cannot trust.
     */
    private void repairTheUnfinishedEditNote(String from, String to)
    {
        if (from == null || from.equals(to) || !beforeEditFile().isFile()) return;

        JSONObject note = unfinishedEdit();

        if (note == null) return;

        repairLocomotiveInSetup(note, from, to);

        // In place, not through rememberBeforeEdit: its atomic Files.move(REPLACE_EXISTING) needs DELETE access, which
        // a OneDrive lock withholds, and it swallows the failure.  Serialized BEFORE the stream is opened, since
        // `new FileOutputStream` truncates and `toString(2)` can throw, which would leave a zero-byte note.
        byte[] bytes;

        try
        {
            bytes = note.toString(2).getBytes(StandardCharsets.UTF_8);
        }
        catch (RuntimeException couldNotSerialise)
        {
            noteRepairFailed = true;

            return;
        }

        try (java.io.FileOutputStream out = new java.io.FileOutputStream(beforeEditFile()))
        {
            out.write(bytes);

            out.flush();
        }
        catch (IOException | RuntimeException couldNotWrite)
        {
            // Recorded for whoever opened the session; a half-written note fails `unfinishedEdit`'s shape check.
            noteRepairFailed = true;
        }
    }

    /**
     * The same repair, over a setup somebody else is holding: the diagram editor's snapshot, which Cancel puts back
     * and which would otherwise restore a name the database no longer has.  Public and static because the holder is in
     * another package; it rewrites JSON in the shape snapshotSetup returns.
     *
     * @param setup what snapshotSetup returned; changed in place
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

    /** One configuration's placements, homes and exclusion lists. */
    private static void repairLocomotiveIn(JSONObject configuration, String from, String to)
    {
        if (configuration == null) return;

        if (configuration.has("points"))
        {
            repairLocomotiveInPoints(configuration.getJSONObject("points"), from, to);
        }

        repairLocomotiveInTimetable(configuration, from, to);

        repairLocomotiveInPausedTrains(configuration, from, to);
    }

    /**
     * The fifth holder of a locomotive's name: the configuration's list of paused trains (FR-117), in "globals".  A
     * rename carries across it and a deletion takes the train off, since a name left there would start a new train
     * given it paused.  Written back sorted, and not at all once empty, as the railway writes it.
     */
    private static void repairLocomotiveInPausedTrains(JSONObject configuration, String from, String to)
    {
        repairLocomotiveInPausedList(configuration.optJSONObject("globals"), from, to);
    }

    /**
     * The same for any settings that hold the list: a configuration's "globals", or the window's record of the settings
     * its railway was built with (OB-343).  Modified in place; null is allowed.
     */
    public static void repairLocomotiveInPausedList(JSONObject globals, String from, String to)
    {
        org.json.JSONArray list = globals == null ? null
            : globals.optJSONArray(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES);

        if (list == null) return;

        java.util.Set<String> names = new java.util.TreeSet<>();

        boolean named = false;

        for (Object name : list)
        {
            if (from.equals(name))
            {
                named = true;

                if (to != null) names.add(to);
            }
            else if (name instanceof String)
            {
                names.add((String) name);
            }
        }

        if (!named) return;

        if (names.isEmpty()) globals.remove(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES);
        else globals.put(org.traincontrol.automation.Layout.PAUSED_LOCOMOTIVES, new org.json.JSONArray(names));
    }

    /**
     * The fourth holder of a locomotive's name: the captured timetable, which rides in "globals".  A rename is carried
     * across every entry (a stale name would cost the entry on the next load, OB-069); a delete removes the entry.
     * The entries also name POINTS, which a station rename breaks the same way; that half is not repaired here and is
     * survivable only because the loader drops the one entry rather than the list.
     */
    private static void repairLocomotiveInTimetable(JSONObject configuration, String from, String to)
    {
        if (!configuration.has("globals")) return;

        JSONObject globals = configuration.optJSONObject("globals");

        if (globals == null || !globals.has("timetable")) return;

        org.json.JSONArray timetable = globals.optJSONArray("timetable");

        if (timetable == null) return;

        org.json.JSONArray kept = new org.json.JSONArray();

        for (int at = 0; at < timetable.length(); at++)
        {
            JSONObject entry = timetable.optJSONObject(at);

            if (entry == null) continue;

            if (from.equals(entry.optString(AutonomyBuilder.LOCOMOTIVE, null)))
            {
                if (to == null) continue;

                entry.put(AutonomyBuilder.LOCOMOTIVE, to);
            }

            kept.put(entry);
        }

        globals.put("timetable", kept);
    }

    /**
     * Follows a rename into one page snapshot, what snapshotPage returns and the editor's undo holds.  The holder that
     * reaches DISK: Ctrl+Z puts a snapshot back and saves, so an undo after a rename would write the old name back.
     * Each value here is already the POINTS object, keyed by square, hence the per-point work shared in one method.
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

    /** One square-keyed set of point properties: the placements, homes and exclusion lists in it. */
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

    /**
     * The pages the operator has deliberately turned back on, unmodifiable.  A decision, as opposed to the absence of
     * one: `excludeRepeatedSensorPages` shuts a page whose sensors repeat another's, and a page the tool excluded and
     * the operator then switched back ON stays on.
     */
    public Set<String> getPagesKeptDespiteRepeats()
    {
        return Collections.unmodifiableSet(keptDespiteRepeats);
    }

    public void setPageExcluded(String page, boolean excluded)
    {
        if (excluded)
        {
            excludedPages.add(page);

            // Excluding it again withdraws the decision: a page cannot be both excluded and kept.
            keptDespiteRepeats.remove(page);
        }
        else
        {
            // Only when it really was off: un-ticking a box never ticked is not a decision about repeated sensors.
            if (excludedPages.remove(page)) keptDespiteRepeats.add(page);
        }
    }

    // configurations

    public List<String> getConfigurationNames()
    {
        return new ArrayList<>(configurations.keySet());
    }

    public String getActiveConfiguration()
    {
        return activeConfiguration;
    }

    /** Records which configuration is in use: what loads on the next start, so set whenever one is loaded. */
    public void setActiveConfiguration(String name)
    {
        if (name == null || configurations.containsKey(name)) activeConfiguration = name;
    }

    public JSONObject getConfiguration(String name)
    {
        return configurations.get(name);
    }

    /**
     * Everything this setup holds that is not one configuration: the decisions about the TRACK.  Named apart from
     * save() because exporting needs exactly this set too.
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

        // And what the file already knew about ids NOT in the index right now (heldForAbsentPages): one save during a
        // page's absence would otherwise drop the record that tells a renumber from a rename.  Never over a live id.
        for (Map.Entry<String, String> entry : pageNamesWhenWritten.entrySet())
        {
            if (!pages.containsKey(entry.getKey())) pages.put(entry.getKey(), entry.getValue());
        }

        root.put("pages", new JSONObject(pages));

        // Each collection writes itself under its FILE name (`disabledPortals` goes out as `disabledLinks`), stated
        // once in the registry, and keyed by page ID so a rename cannot orphan it.
        for (Kept k : kept()) k.write(root);

        // Whatever was held back because its page is not loaded goes back in exactly as it came (OB-067): one save while
        // a page's file is missing would otherwise delete that page's whole setup.
        for (String field : HELD_FIELDS.keySet())
        {
            mergeHeld(root, field);
        }

        // written back last, so a field this build does not model survives a round trip
        for (Map.Entry<String, Object> entry : unknownSharedFields.entrySet())
        {
            root.put(entry.getKey(), entry.getValue());
        }

        return root;
    }

    /** The key an exported file carries its configuration under; its presence tells the two formats apart. */
    public static final String EXPORT_CONFIGURATION = "configuration";

    /** The key an exported file carries the track decisions under. */
    public static final String EXPORT_SHARED = "shared";

    /**
     * A configuration together with the track decisions it refers to, ready to be written to a file: a configuration
     * alone would refer to squares the receiving setup had never heard of.  Null when there is no such configuration.
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
     * Brings in an exported file, in either the bundled form or the bare configuration written before.  The shared
     * half is merged, not adopted: an entry the local setup already has is kept and only the gaps are filled, so a
     * working setup's stations cannot be silently renamed.  Left out on purpose: pages this layout does not have (named
     * by `getPagesLeftOutOfLastImport` for the import door) and a field a NEWER build wrote, whose shape is unknown.
     * Answers how many shared entries were filled in.
     */
    public int importBundle(String name, JSONObject file) throws IOException
    {
        pagesLeftOutOfLastImport.clear();

        JSONObject configuration = file.optJSONObject(EXPORT_CONFIGURATION);

        // The bare form, written before exporting carried the shared half
        if (configuration == null)
        {
            importConfiguration(name, file);
            return 0;
        }

        // Whether this name was already here, so the rollback knows if removing it takes away something of the user's.
        boolean existed = configurations.containsKey(name);

        // A COPY of what is being replaced, for the rollback: importing over a name consents to a GOOD file, not to
        // losing the configuration to an unreadable one.
        JSONObject replaced = existed ? new JSONObject(configurations.get(name).toString()) : null;

        importConfiguration(name, configuration);

        JSONObject incoming = file.optJSONObject(EXPORT_SHARED);

        if (incoming == null) return 0;

        // THEIR PAGE IDS, TRANSLATED INTO MINE BY PAGE NAME, before anything is merged.  An export keys every square by
        // the EXPORTER's ids, which are numbered from the same start on every railway: adopted per id, their "Yard" (1)
        // would land on my "Main" (1).  Each of their ids maps to the id this layout uses for that name, a loaded
        // page's or the one recorded for a page it knows and cannot load right now; a name that is neither is a page
        // this layout does not have and maps to nothing.  Their page record is never merged.  Only when both sides have
        // a numbering (a OneDrive start with every page a placeholder has an empty index and a full record).
        JSONObject theirPages = incoming.optJSONObject("pages");

        boolean iKnowMyPages = !pageIdToName.isEmpty() || !pageNamesWhenWritten.isEmpty();

        Map<String, String> theirIdToMine = theirPages == null || !iKnowMyPages
            ? null : new java.util.HashMap<String, String>();

        if (theirIdToMine != null)
        {
            for (String id : theirPages.keySet())
            {
                Object called = theirPages.get(id);

                if (!(called instanceof String)) continue;

                String mine = pageNameToId.get(called);

                if (mine == null)
                {
                    for (Map.Entry<String, String> known : pageNamesWhenWritten.entrySet())
                    {
                        if (called.equals(known.getValue())) mine = known.getKey();
                    }
                }

                // A null value is a page this layout does not have: `containsKey` still answers true.
                theirIdToMine.put(id, mine);

                if (mine == null && !pagesLeftOutOfLastImport.contains(called))
                {
                    pagesLeftOutOfLastImport.add((String) called);
                }
            }
        }

        JSONObject merged = sharedFields();

        int filled = 0;

        for (String key : incoming.keySet())
        {
            if ("version".equals(key)) continue;

            // Their page record is how their ids were translated, not a setting to adopt.
            if (theirIdToMine != null && "pages".equals(key)) continue;

            // A field this version does not model is left out while translating: its shape is unknown, so it cannot be
            // put into this layout's numbering, and merged as it came it would be saved in the EXPORTER's ids.
            if (theirIdToMine != null && !knownShared().contains(key)) continue;

            Object value = intoThisLayoutsPages(key, incoming.get(key), theirIdToMine);

            // THEIR PAGE RECORD, ADOPTED - reachable only for a store that knows no page by any number: there is no
            // record of mine to re-label, so theirs wins per id (ids only I have are kept).
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

                // ONE CAPTION PER STATION PER PAGE (OB-341).  A square the setup has no caption on is no gap when the
                // station is captioned on that page already, so captions take only stations not yet captioned ON THEIR
                // PAGE, in the order of the squares (org.json holds keys in no order).
                Set<String> captioned = null;

                List<String> inners = new ArrayList<>(((JSONObject) value).keySet());

                if ("captions".equals(key))
                {
                    captioned = new LinkedHashSet<>();

                    for (String held : mine.keySet()) captioned.add(captionedOnItsPage(held, mine.get(held)));

                    inners.sort(AutonomyCompanionStore::bySquare);
                }

                for (String inner : inners)
                {
                    // Kept, not replaced: this is a merge.
                    if (mine.has(inner)) continue;

                    if (captioned != null && !captioned.add(captionedOnItsPage(inner, ((JSONObject) value).get(inner))))
                    {
                        continue;
                    }

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
            // What the store holds now, in the shape readShared takes, so an import that turns out unreadable can be
            // put back: readShared's strict accessors throw part way on anything unexpected, and the merged object is
            // assembled from somebody else's file.
            JSONObject wasThere = sharedFields();

            try
            {
                // Through the same door load() uses, so the page-id translation happens once and here.
                clearShared();
                readShared(merged);
            }
            catch (RuntimeException e)
            {
                clearShared();
                readShared(wasThere);

                // And the configuration, installed before any of this: removed when the import added it, restored from
                // the copy when it replaced one, else it would be listed with placements for points just taken away.
                if (existed) configurations.put(name, replaced);
                else forgetConfiguration(name);

                throw e;
            }
        }

        return filled;
    }

    /**
     * One incoming shared field, rewritten from the exporter's page ids into this layout's, with only the PIECES naming
     * a page this layout does not have taken out.  Read by the field's declared shape in `HELD_FIELDS`, so a value is
     * treated as a square only where the field says so (a station named "2" is not a page id).
     *
     *   keyed fields    an entry whose KEY is on a foreign page goes; so does one whose square value is
     *   square lists    each member is translated, and a foreign member goes on its own; an entry whose
     *                   list is left empty goes, because it said nothing but the foreign squares
     *   page lists      each page id is translated, and a foreign one goes
     *
     * A square whose page part the file's record does not name is left as it is (an old file keyed by name).
     *
     * @param theirIdToMine their id to this layout's, a null value for a page not here; null to translate nothing
     */
    private static Object intoThisLayoutsPages(String field, Object value, Map<String, String> theirIdToMine)
    {
        Held shape = theirIdToMine == null ? null : HELD_FIELDS.get(field);

        if (shape == null) return value;

        if (shape == Held.SQUARE_LIST || shape == Held.PAGE_LIST)
        {
            if (!(value instanceof JSONArray)) return value;

            JSONArray src = (JSONArray) value;
            JSONArray keep = new JSONArray();

            for (int at = 0; at < src.length(); at++)
            {
                String each = String.valueOf(src.get(at));

                String mine = shape == Held.PAGE_LIST
                    ? pageIntoMine(each, theirIdToMine) : squareIntoMine(each, theirIdToMine);

                if (mine != null) keep.put(mine);
            }

            return keep;
        }

        if (!(value instanceof JSONObject)) return value;

        JSONObject src = (JSONObject) value;
        JSONObject keep = new JSONObject();

        for (String key : src.keySet())
        {
            String mineKey = squareIntoMine(key, theirIdToMine);

            if (mineKey == null) continue;

            Object entry = src.get(key);

            if (shape == Held.SQUARE_VALUE && entry instanceof String)
            {
                entry = squareIntoMine((String) entry, theirIdToMine);

                if (entry == null) continue;
            }
            else if (shape == Held.SQUARE_LIST_VALUE)
            {
                if (entry instanceof String)
                {
                    entry = squareIntoMine((String) entry, theirIdToMine);

                    if (entry == null) continue;
                }
                else if (entry instanceof JSONArray)
                {
                    JSONArray members = new JSONArray();

                    for (int at = 0; at < ((JSONArray) entry).length(); at++)
                    {
                        String member = squareIntoMine(String.valueOf(((JSONArray) entry).get(at)), theirIdToMine);

                        if (member != null) members.put(member);
                    }

                    if (members.length() == 0) continue;

                    entry = members;
                }
            }

            keep.put(mineKey, entry);
        }

        return keep;
    }

    /** A stored square in their numbering, in mine; null when its page is not here; as it was when unknown. */
    private static String squareIntoMine(String stored, Map<String, String> theirIdToMine)
    {
        int colon = stored.lastIndexOf(':');

        if (colon < 0) return stored;

        String page = pageIntoMine(stored.substring(0, colon), theirIdToMine);

        return page == null ? null : page + stored.substring(colon);
    }

    /** A page id in their numbering, in mine; null when the page is not here; as it was when the file's record does not name it. */
    private static String pageIntoMine(String theirs, Map<String, String> theirIdToMine)
    {
        return theirIdToMine.containsKey(theirs) ? theirIdToMine.get(theirs) : theirs;
    }

    /**
     * Everything this store holds, as JSON, for a caller that may have to put it back: the diagram editor writes the
     * setup as it goes, and its Cancel re-reads the DIAGRAM from disk but not the setup.  Deep copies, so editing
     * cannot change the snapshot under its holder (snapshotPage does the same).
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

    /** Puts back what snapshotSetup took; a snapshot that will not read leaves the setup as it was and throws. */
    public void restoreSetup(JSONObject was)
    {
        if (was == null) return;

        // WHAT IS HERE NOW FIRST, so a snapshot this build cannot read changes nothing: the read below empties the
        // store and uses strict accessors, which is not safe for a snapshot off disk (the pre-edit note
        // `AutonomySession.revertUnfinishedEdit` applies may come from another build).  Same promise as `load()`.
        JSONObject wasThere = snapshotSetup();

        try
        {
            readSnapshot(was);
        }
        catch (RuntimeException unreadable)
        {
            readSnapshot(wasThere);

            throw unreadable;
        }
    }

    /**
     * Replaces the whole setup with a snapshot: the shared half, every configuration, and which one is chosen.  Kept
     * apart from `restoreSetup` so it can be run a second time over what was there; it empties before it reads, so it
     * is only safe beside a snapshot taken first.
     */
    private void readSnapshot(JSONObject was)
    {
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

    /** Empties the shared half, leaving the configurations alone. */
    private void clearShared()
    {
        for (Kept k : kept()) k.clear();

        unknownSharedFields.clear();
        pageNamesWhenWritten.clear();
        heldForAbsentPages.clear();
        pageIdConflicts.clear();
    }

    /**
     * Creates a configuration, optionally as a copy of an existing one (null copyFrom for an empty one).  A copy takes
     * everything, because a configuration exists to differ in where the locomotives are.
     */
    public void createConfiguration(String name, String copyFrom) throws IOException
    {
        createConfiguration(name, copyFrom, true);
    }

    /**
     * The same, choosing whether the copy takes where the trains stand and the timetable (the things a variant most
     * often starts without): false leaves out every placement, with its facing and arrival, and the timetable.
     *
     * @throws IOException when the name is in use
     */
    public void createConfiguration(String name, String copyFrom, boolean withTrains) throws IOException
    {
        // Duplicating onto an existing name would replace it silently.
        if (configurations.containsKey(name)) throw new IOException(ERROR_NAME_IN_USE);

        // ANOTHER CONFIGURATION'S FILE, refused as that and naming it
        refuseASharedFile(name, null);

        JSONObject source = copyFrom == null ? null : configurations.get(copyFrom);

        JSONObject created = source == null
            ? new JSONObject()
            : new JSONObject(source.toString());

        created.put("name", name);

        if (source != null && !withTrains) withoutTrains(created);

        configurations.put(name, created);

        if (activeConfiguration == null) activeConfiguration = name;
    }

    /**
     * Takes every train and the timetable out of a configuration, in place: each placement with the facing and the
     * arrival that described the train standing there (as `AutonomySession.placeLocomotive` clears one), the
     * timetable, and any entries an older build set aside.
     */
    private static void withoutTrains(JSONObject configuration)
    {
        JSONObject points = configuration.optJSONObject("points");

        if (points != null)
        {
            for (String key : points.keySet())
            {
                JSONObject point = points.optJSONObject(key);

                if (point == null) continue;

                point.remove(AutonomyBuilder.LOCOMOTIVE);
                point.remove(AutonomyBuilder.FACING);
                point.remove("arrivedFrom");
                point.remove("arrivedAlong");
            }
        }

        JSONObject globals = configuration.optJSONObject("globals");

        if (globals != null) globals.remove("timetable");

        configuration.remove(ENTRIES_SET_ASIDE);
    }

    /**
     * Brings in a configuration from a file somebody exported, under the given name (so importing does not overwrite
     * whatever shared the exporter's name).  It refuses a name that sanitizes to another configuration's file, as
     * `createConfiguration` and `renameConfiguration` do: the next `load()` would find only one of them and say
     * nothing.
     *
     * @throws IOException when the name would share a file with a configuration already here
     */
    public void importConfiguration(String name, JSONObject configuration) throws IOException
    {
        // ANOTHER CONFIGURATION'S FILE, refused as that and naming it
        refuseASharedFile(name, null);

        JSONObject imported = new JSONObject(configuration.toString());

        imported.put("name", name);

        configurations.put(name, imported);

        if (activeConfiguration == null) activeConfiguration = name;
    }

    /**
     * Takes back a configuration this class has just put in, without touching any file, for the import rollback:
     * deleteConfiguration also deletes the FILE, and an import that has not reached a save has written none.
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
        // Refused rather than allowed to overwrite: renaming A to an existing B would replace B and then delete B's file,
        // destroying a configuration the user never named in the gesture.
        if (!from.equals(to) && configurations.containsKey(to))
        {
            throw new IOException(ERROR_NAME_IN_USE);
        }

        // ANOTHER CONFIGURATION'S FILE, refused as that and naming it
        if (!from.equals(to)) refuseASharedFile(to, from);

        JSONObject configuration = configurations.remove(from);

        if (configuration == null) return;

        configuration.put("name", to);
        configurations.put(to, configuration);

        if (from.equals(activeConfiguration)) activeConfiguration = to;

        File old = configurationFile(from);
        File now = configurationFile(to);

        // MOVED and rewritten, not deleted: deleting the old file first would leave a window in which the configuration
        // was on disk nowhere, and load() scans the folder.  The rewrite makes the file agree with its own name (load()
        // takes the name from INSIDE it).  A failure is raised, not swallowed: memory and disk must keep saying the
        // same.
        if (old.isFile())
        {
            try
            {
                // Only when there is somewhere to move TO: two names can resolve to one file (File.equals is
                // case-insensitive on Windows), but the REWRITE still has to happen.
                if (!old.equals(now))
                {
                    Files.move(old.toPath(), now.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                writeJson(now, configuration);
            }
            catch (IOException e)
            {
                // Back to exactly where we were, on disk and in memory: a file left at the new name holding the old
                // name would be saved again under the old name and load() would find the configuration twice.
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
     * Deletes a configuration, refusing the last one: a layout with a setup but no configuration is a state nothing in
     * the UI could act on.
     *
     * @throws IOException if it is the only configuration left
     */
    public void deleteConfiguration(String name) throws IOException
    {
        if (!configurations.containsKey(name)) return;

        // The FILE decides, because load() rebuilds the list by scanning the folder: a file that could not be deleted
        // (held open by a sync client) would return next session as though nothing had happened.
        File file = configurationFile(name);

        if (file.isFile() && !file.delete())
        {
            // A sentence, because the caller shows this as the whole dialog.
            throw new IOException(I18n.f("autosetup.ui.errorDeleteConfigurationFailed", name));
        }

        configurations.remove(name);

        if (name.equals(activeConfiguration))
        {
        // The last one may go, so a layout somebody experimented on does not keep a configuration for ever; with none
        // left nothing is active, the state every layout starts in.
            activeConfiguration = configurations.isEmpty()
                ? null : configurations.keySet().iterator().next();
        }
    }

    /**
     * Removes the whole setup: every configuration, every decision, and the files holding them, as the way back out of
     * having set autonomy up.  The diagram is not touched; captions live here, so they go with it.
     *
     * @throws IOException if a file could not be removed; memory is cleared either way, so what remains on disk is not
     *         read back
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

        // And the pre-edit note: left behind, the next session build would find it and restore the whole deleted setup.
        File note = beforeEditFile();

        if (note.isFile() && !note.delete()) failed.add(note.getName());

        clear();

        // Only if it is now empty: a folder somebody keeps something else in is not this method's to remove.
        File folder = folder();

        String[] left = folder.list();

        if (folder.isDirectory() && left != null && left.length == 0) folder.delete();

        if (!failed.isEmpty())
        {
            throw new IOException(String.join(", ", failed));
        }
    }

    // keeping up with the diagram

    /**
     * Follows a page being renamed.  Every key begins with a page name, so a rename would otherwise orphan every name,
     * length, direction and pairing on that page; it is rewritten across the shared file and every configuration.
     */
    public void renamePage(String from, String to)
    {
        // Each collection knows which of its halves name a square: a caption is keyed by the square the text sits on
        // AND points at a station's square, and a switched-off link is remembered by its square.
        for (Kept k : kept()) k.renamePage(from, to);

        // AND the numbering, which every translation asks (OB-092): else `pageNameToId` keeps the old name, the next
        // save finds no id, and `translatePages` writes the bare NAME into excludedPages, which `untranslatePages`
        // reads as an ID (a page renamed to "5" came back as page 5, excluded).  A rename moves the name and leaves the
        // number.
        String renamedId = pageNameToId.remove(from);

        if (renamedId != null)
        {
            pageNameToId.put(to, renamedId);
            pageIdToName.put(renamedId, to);

            // And what that id was CALLED when the file was written, or saves before the next load decline to reconcile
            // and warn under the name it no longer has.  Transient: sharedFields rewrites the map at the next save.
            pageNamesWhenWritten.put(renamedId, to);
        }

        // Configurations key by tile too ("page:x,y"), so they are rewritten here, or a rename drops every placement,
        // home, terminus and length in every configuration.
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

        // AND THE NAMES MADE FROM THE PAGE'S NAME, where a timetable leg or a train's road stores them: a square with
        // no name of its own is named "page x,y", so the rename renamed its Points.
        final java.util.regex.Pattern made = java.util.regex.Pattern.compile("^" + java.util.regex.Pattern.quote(from)
            + " (\\d+,\\d+)( \\(.*)?$");

        for (JSONObject configuration : configurations.values())
        {
            renameStoredPoints(configuration, name ->
            {
                java.util.regex.Matcher m = made.matcher(name);

                return m.matches() ? to + " " + m.group(1) + (m.group(2) == null ? "" : m.group(2)) : name;
            });
        }
    }

    /**
     * Every Point name a configuration stores by name (its timetable's legs and each standing train's road) through a
     * rename, in place.  Everything else in it is keyed by square.
     */
    static void renameStoredPoints(JSONObject configuration, java.util.function.UnaryOperator<String> rename)
    {
        JSONObject globals = configuration.optJSONObject("globals");

        JSONArray table = globals == null ? null : globals.optJSONArray("timetable");

        List<JSONObject> entries = new ArrayList<>();

        for (int i = 0; table != null && i < table.length(); i++) entries.add(table.optJSONObject(i));

        for (JSONObject entry : entries)
        {
            JSONArray path = entry == null ? null : entry.optJSONArray("path");

            for (int j = 0; path != null && j < path.length(); j++)
            {
                JSONObject leg = path.optJSONObject(j);

                for (String end : new String[] {"start", "end"})
                {
                    if (leg != null && leg.opt(end) instanceof String) leg.put(end, rename.apply(leg.getString(end)));
                }
            }
        }

        JSONObject points = configuration.optJSONObject("points");

        for (String id : points == null ? java.util.Collections.<String>emptySet() : points.keySet())
        {
            JSONObject point = points.optJSONObject(id);

            JSONArray road = point == null ? null : point.optJSONArray("arrivedAlong");

            for (int j = 0; road != null && j < road.length(); j++)
            {
                JSONArray step = road.optJSONArray(j);

                for (int k = 0; step != null && k < step.length(); k++)
                {
                    if (step.opt(k) instanceof String) step.put(k, rename.apply(step.getString(k)));
                }
            }
        }
    }

    /**
     * Forgets everything about a page that has been deleted, renamePage's counterpart.  A deleted page's id is retired
     * (see LayoutDiagram.writeLayoutIndex), so what is left is its own settings.  Removal is by forgetSquares, which
     * also takes out what NAMES a square on this page from OTHER pages (a caption, a protecting signal, the far end of
     * a portal).  Answers the number of squares whose setup was forgotten.
     */
    public int deletePage(String page)
    {
        if (page == null) return 0;

        Set<TileKey> squares = new LinkedHashSet<>();

        // Every square this page names, as a key OR as a value: a protecting signal, a blocker or the far end of a
        // portal can sit here with no entry of its own, and a direction is keyed by square AND route.
        for (Kept k : kept()) k.squaresOn(page, squares);

        forgetSquares(squares);

        // Keyed by PAGE, not square, so forgetSquares does not see it; left behind, a later page of that name would
        // start excluded.
        excludedPages.remove(page);

        // A page that is gone cannot be one the operator is keeping; the name would apply to the next page created.
        keptDespiteRepeats.remove(page);

        // And the record of what that page was called (as forgetHeldPages ends): `pagesNotLoaded` walks it, so a stale
        // entry would name a DELETED page as one that failed to load and warn on every editor save for the layout's
        // life.  FR-018 cannot clear it, as a deleted page is not in the index.
        pageNamesWhenWritten.values().remove(page);

        // And out of the index this store holds, which `sharedFields` rebuilds the file's "pages" map from.  Safe for
        // the id floor (`highestPageIdSeen`): this page's settings are forgotten, so a reissued id collects nothing.
        String deletedId = pageNameToId.remove(page);

        if (deletedId != null) pageIdToName.remove(deletedId);

        // And the configurations, which key by square too (see renamePage).
        for (JSONObject configuration : configurations.values())
        {
            if (!configuration.has("points")) continue;

            JSONObject points = configuration.getJSONObject("points");

            for (String key : new LinkedHashSet<>(points.keySet()))
            {
                int at = key.lastIndexOf('#');

                if (isOnPage(at >= 0 ? key.substring(0, at) : key, page)) points.remove(key);
            }
        }

        return squares.size();
    }

    /**
     * Squares that have been built over, whose setup is therefore about track that is gone.  moveTiles covers the
     * squares a move LANDS on; a paste, a copied tile and a palette drop write over squares too.
     */
    public void forgetTiles(java.util.Collection<TileKey> tiles)
    {
        moveTiles(null, tiles);
    }

    private static Set<TileKey> asKeys(java.util.Collection<TileKey> tiles)
    {
        Set<TileKey> keys = new LinkedHashSet<>();

        if (tiles == null) return keys;

        for (TileKey tile : tiles)
        {
            if (tile != null) keys.add(tile);
        }

        return keys;
    }

    /**
     * Follows tiles being moved on the diagram.  Everything here is keyed by SQUARE, so a moved tile would leave its
     * whole setup at the old coordinates, where the next reconcile would drop it.
     *
     * The whole set in one call: in a group dragged one square right every source lands on another source, so moving
     * them in sequence makes each write destroy data the next read needed.  A square that MOVES takes what is written
     * about it; a square only NAMED by something else (a caption pointing at a station, a protecting signal, a portal's
     * partner) is repointed where it stands.  A tile moved onto a square that is not moving replaces what was there,
     * as the diagram does.
     */
    public void moveTiles(Map<TileKey, TileKey> moves)
    {
        moveTiles(moves, null);
    }

    /**
     * One diagram edit: what moved, and what else was built over (either may be null).  Both halves in one call
     * because separate calls would make each caller get the ORDER right (forget first, or the arriving setup is
     * wiped).  The store works out the whole landing set itself; a pure forget is this with no moves, a pure move with
     * nothing built over.
     */
    public boolean moveTiles(Map<TileKey, TileKey> moves, java.util.Collection<TileKey> builtOver)
    {
        Map<TileKey, TileKey> byKey = new LinkedHashMap<>();

        if (moves != null)
        {
            for (Map.Entry<TileKey, TileKey> move : moves.entrySet())
            {
                if (move.getKey() == null || move.getValue() == null) continue;

                if (move.getKey().equals(move.getValue())) continue;

                byKey.put(move.getKey(), move.getValue());
            }
        }

        // Every square being LANDED ON that is not itself moving away: the diagram writes the arriving tile over
        // whatever was there, and the loops below only overwrite a landing square when the source had something, so
        // plain track dragged onto a station would keep the station's name, signals and restrictions.  A landing square
        // that is also a source travels with its own entry.
        Set<TileKey> landing = new LinkedHashSet<>();

        for (TileKey to : byKey.values())
        {
            if (!byKey.containsKey(to)) landing.add(to);
        }

        // And the squares built over by something other than a move: a bulk column edit clears the whole destination.
        for (TileKey over : asKeys(builtOver))
        {
            if (!byKey.containsKey(over)) landing.add(over);
        }

        // Sparing the labels of the tiles that are arriving (see forgetSquares): a platform nudged down one lands ON
        // its own label.  The answer is WHETHER IT FOUND ANYTHING, which the caller's "did this change something" uses.
        boolean changed = forgetSquares(landing, byKey);

        if (byKey.isEmpty()) return changed;

        // Where both halves name a square, both follow, the value REPOINTED rather than moved: a caption on a square
        // that stayed put, naming a station that moved, still names that station.  A direction belongs to a square AND
        // a route, so moveKeys asks a key which square it is on (FR-013).
        for (Kept k : kept()) k.move(byKey);

        // The configurations key by tile as well (see renamePage).
        for (JSONObject configuration : configurations.values())
        {
            if (!configuration.has("points")) continue;

            JSONObject points = configuration.getJSONObject("points");
            JSONObject moved = new JSONObject();

            // The ones staying put first, so a tile arriving on a square that is not moving replaces what was there.
            // The configuration's keys are the PRINTED form (JSON), so each is parsed before being asked about
            // (FR-013): `byKey.containsKey(aString)` compiles, because containsKey takes Object, and is always false.
            for (String key : points.keySet())
            {
                TileKey tile = parseTileKey(key);

                if (tile == null || !byKey.containsKey(tile)) moved.put(key, points.get(key));
            }

            for (String key : points.keySet())
            {
                TileKey tile = parseTileKey(key);

                if (tile != null && byKey.containsKey(tile))
                {
                    moved.put(byKey.get(tile).toString(), points.get(key));
                }
            }

            configuration.put("points", moved);
        }

        // AND THE NAMES MADE FROM WHERE A MOVED SQUARE WAS, where a timetable leg or a train's road stores them:
        // a square with no name of its own is named "page x,y", so a move renamed its Points
        for (JSONObject configuration : configurations.values())
        {
            renameStoredPoints(configuration, name ->
            {
                for (Map.Entry<TileKey, TileKey> move : byKey.entrySet())
                {
                    String was = GraphReducer.generatedName(move.getKey());

                    if (name.equals(was) || name.startsWith(was + " ("))
                    {
                        return GraphReducer.generatedName(move.getValue()) + name.substring(was.length());
                    }
                }

                return name;
            });
        }

        // A real move always changes the keys it is given; the answer above matters only for the built-over half.
        return true;
    }

    /**
     * Everything this store holds about one page, as something that can be put back, for the diagram editor's undo: a
     * snapshot of a page's CAPTIONS alone stopped being enough once a tile carried its whole setup with it.  The shape
     * goes straight back into restorePage and nowhere else.
     */
    public Map<String, Object> snapshotPage(String page)
    {
        Map<String, Object> out = new LinkedHashMap<>();

        if (page == null) return out;

        // Keyed by the collection's file name, which restorePage asks for.  A collection with nothing to snapshot
        // (`excludedPages`) puts null.  The lists are COPIED: edited in place they would change the snapshot too.
        for (Kept k : kept()) out.put(k.json, k.snapshotOf(page));

        // The configurations key by tile too: a snapshot without them restores half a station.
        Map<String, JSONObject> points = new LinkedHashMap<>();

        for (Map.Entry<String, JSONObject> entry : configurations.entrySet())
        {
            if (!entry.getValue().has("points")) continue;

            JSONObject from = entry.getValue().getJSONObject("points");
            JSONObject kept = new JSONObject();

            for (String key : from.keySet())
            {
                // Deep: setPointProperty writes the facings, placements, homes and maximum lengths with
                // points.getJSONObject(id).put(...), straight into the object a shared snapshot would be holding.
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
     * Puts a page back as snapshotPage found it.  Everything currently keyed to that page is dropped first, so a square
     * that has GAINED something since loses it again, which is what undo means.  Other pages are not touched.  A null
     * snapshot does nothing.
     */
    @SuppressWarnings("unchecked")
    public void restorePage(String page, Map<String, Object> snapshot)
    {
        if (page == null || snapshot == null) return;

        // By the same names snapshotPage wrote, which are the collections' file names.  A snapshot taken by an older build
        // has no entry for a collection added since, and each kind's put-back treats absent as "this page had none".
        for (Kept k : kept()) k.restoreTo(page, snapshot.get(k.json));

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
                // Copied on the way back as well: the snapshot is held as long as the editor might undo, and the next
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

    /** Whether a square is on a page (FR-013): a field comparison, not a split of the string on a colon (OB-071). */
    private static boolean isOnPage(TileKey key, String page)
    {
        return key != null && key.getPage().equals(page);
    }

    /**
     * Whether a stored key belongs to this page, by what the key PARSES to, not what it starts with: a page may be
     * called "Yard: Upper", and "Yard: Upper:2,3" starts with "Yard:".  parseTileKey splits on the LAST colon.
     */
    private static boolean isOnPage(String key, String page)
    {
        TileKey parsed = key == null ? null : parseTileKey(key);

        // A suffixed direction key ("page:x,y#state,index") belongs to the page of its square.
        if (parsed == null && key != null && key.lastIndexOf('#') > 0)
        {
            parsed = parseTileKey(key.substring(0, key.lastIndexOf('#')));
        }

        return parsed != null && parsed.getPage().equals(page);
    }

    /** The same map with each list copied, so that nothing which edits a list in place can reach it. */
    private static Map<TileKey, List<TileKey>> copyLists(Map<TileKey, List<TileKey>> from)
    {
        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        if (from == null) return out;

        for (Map.Entry<TileKey, List<TileKey>> entry : from.entrySet())
        {
            out.put(entry.getKey(), entry.getValue() == null
                ? null : new ArrayList<>(entry.getValue()));
        }

        return out;
    }

    /**
     * The entries of one collection that live on one page.  Generic over {@link SquareKeyed} (FR-013), so the one
     * collection keyed by a square PLUS a route goes through the same method; erasure makes {@code Map<String, T>}
     * and {@code Map<TileKey, T>} the same signature, so they could not be overloaded.
     */
    private static <K extends SquareKeyed<K>, T> Map<K, T> onPage(Map<K, T> from, String page)
    {
        Map<K, T> out = new LinkedHashMap<>();

        for (Map.Entry<K, T> entry : from.entrySet())
        {
            if (isOnPage(entry.getKey().square(), page)) out.put(entry.getKey(), entry.getValue());
        }

        return out;
    }

    /**
     * One page's entries from a map whose VALUES are squares too.  `onPage` asks only about the key, which is wrong for
     * maps that pair squares with squares: portals, protecting signals and blocked points cross pages, so snapshot and
     * restore must capture every entry that MENTIONS this page, or an edit that broke the far page's half would have
     * no record of it to put back.
     */
    private static Map<TileKey, TileKey> onPageEitherEnd(Map<TileKey, TileKey> from, String page)
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, TileKey> entry : from.entrySet())
        {
            if (isOnPage(entry.getKey(), page)
                || (entry.getValue() != null && isOnPage(entry.getValue(), page)))
            {
                out.put(entry.getKey(), entry.getValue());
            }
        }

        return out;
    }

    /** The same, for a map of squares to LISTS of squares. */
    private static Map<TileKey, List<TileKey>> onPageEitherEnd(
        Map<TileKey, List<TileKey>> from, String page, boolean lists)
    {
        Map<TileKey, List<TileKey>> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, List<TileKey>> entry : from.entrySet())
        {
            boolean mentions = isOnPage(entry.getKey(), page);

            if (!mentions && entry.getValue() != null)
            {
                for (TileKey held : entry.getValue())
                {
                    if (isOnPage(held, page))
                    {
                        mentions = true;
                        break;
                    }
                }
            }

            if (mentions) out.put(entry.getKey(), entry.getValue());
        }

        return out;
    }

    /**
     * Puts back a page's entries in a collection whose values are squares too.  Everything MENTIONING the page goes,
     * then the snapshot goes back, so an entry on another page that pointed here is restored rather than left pointing
     * at a square the undo emptied.
     */
    private static void putBackEitherEnd(Map<TileKey, TileKey> into, String page,
        Map<TileKey, TileKey> was)
    {
        for (java.util.Iterator<Map.Entry<TileKey, TileKey>> pairs = into.entrySet().iterator();
            pairs.hasNext();)
        {
            Map.Entry<TileKey, TileKey> pair = pairs.next();

            if (isOnPage(pair.getKey(), page)
                || (pair.getValue() != null && isOnPage(pair.getValue(), page)))
            {
                pairs.remove();
            }
        }

        if (was != null) into.putAll(was);
    }

    /** The same, for a map of squares to LISTS of squares. */
    private static void putBackEitherEnd(Map<TileKey, List<TileKey>> into, String page,
        Map<TileKey, List<TileKey>> was, boolean lists)
    {
        for (java.util.Iterator<Map.Entry<TileKey, List<TileKey>>> pairs
            = into.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<TileKey, List<TileKey>> pair = pairs.next();

            boolean mentions = isOnPage(pair.getKey(), page);

            if (!mentions && pair.getValue() != null)
            {
                for (TileKey held : pair.getValue())
                {
                    if (isOnPage(held, page))
                    {
                        mentions = true;
                        break;
                    }
                }
            }

            if (mentions) pairs.remove();
        }

        if (was != null) into.putAll(was);
    }

    private static Set<TileKey> membersOnPage(Set<TileKey> from, String page)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey key : from)
        {
            if (isOnPage(key, page)) out.add(key);
        }

        return out;
    }

    /** Replaces one page's entries with what a snapshot remembered of them (null leaves it empty). */
    private static <K extends SquareKeyed<K>, T> void putBack(Map<K, T> into, String page,
        Map<K, T> was)
    {
        for (java.util.Iterator<K> keys = into.keySet().iterator(); keys.hasNext();)
        {
            if (isOnPage(keys.next().square(), page)) keys.remove();
        }

        if (was != null) into.putAll(was);
    }

    private static void putMembersBack(Set<TileKey> into, String page, Set<TileKey> was)
    {
        for (java.util.Iterator<TileKey> keys = into.iterator(); keys.hasNext();)
        {
            if (isOnPage(keys.next(), page)) keys.remove();
        }

        if (was != null) into.addAll(was);
    }

    /**
     * The same, for a set whose members are PAIRED ACROSS PAGES.  `disabledPortals` is the one: `setPortalDisabled`
     * writes both ends, on different pages by definition.  Capturing at either end is required, or the undo removes one
     * half of the pair and puts nothing back, and `disabledLinks` goes to disk with one end in it.
     */
    private Set<TileKey> membersOnPageEitherEnd(Set<TileKey> from, String page)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey key : from)
        {
            if (isOnPage(key, page) || isOnPage(getPortalPartner(key), page)) out.add(key);
        }

        return out;
    }

    /**
     * Puts a paired set's page back, both ends of every pair with it.  The pairing is read as it stands NOW, which is
     * correct: `portals` is restored before `disabledLinks` in the registry order, so it is already the snapshot's.
     */
    private void putPairedMembersBack(Set<TileKey> into, String page, Set<TileKey> was)
    {
        for (java.util.Iterator<TileKey> keys = into.iterator(); keys.hasNext();)
        {
            TileKey key = keys.next();

            if (isOnPage(key, page) || isOnPage(getPortalPartner(key), page)) keys.remove();
        }

        if (was != null) into.addAll(was);
    }

    /**
     * Lets go of everything the setup holds about a set of squares: what is written ABOUT the square, and what
     * elsewhere POINTS AT it (a caption naming a station built over, a pairing to a signal that is gone).
     */
    private boolean forgetSquares(Set<TileKey> squares)
    {
        return forgetSquares(squares, null);
    }

    /**
     * As above, with `arriving` (each MOVING square and where it is going) telling a label about to be built over by
     * the very thing it names from one that is merely stale.  A caption normally goes when its square is built over,
     * except when what built over it is the station it names: nudging a platform down one lands it on its own label,
     * which must survive.  The test is that the station is arriving on THIS square, not that it is moving at all, or a
     * label in the destination column would be spared whenever some other tile of the move happened to name it.
     */
    private boolean forgetSquares(Set<TileKey> squares, Map<TileKey, TileKey> arriving)
    {
        if (squares == null || squares.isEmpty()) return false;

        // WHETHER ANYTHING WAS ACTUALLY REMOVED (see `Kept.forget`): four callers write the whole setup to disk on
        // true.
        boolean any = false;

        // Each collection drops what it knows about these squares AND anything POINTING at them: a pointer at gone
        // track is one nothing can satisfy or clear.  Captions are the exception via `arriving`, for the KEY half only;
        // a vacated square is never in this set, because moveTiles builds it by excluding them.
        for (Kept k : kept()) any |= k.forget(squares, arriving);

        // The configurations key by square as well - facings, placements, homes, lengths
        for (JSONObject configuration : configurations.values())
        {
            if (!configuration.has("points")) continue;

            JSONObject points = configuration.getJSONObject("points");

            for (TileKey key : squares)
            {
                any |= points.remove(key.toString()) != null;
            }
        }

        return any;
    }

    /**
     * Follows squares dragged to new coordinates, moved keys winning over ones that merely stayed.  For a key that is a
     * square plus something else, that something travels unchanged (FR-013).  Two passes, and the order matters: an
     * entry moving ONTO a square another is moving off must not be overwritten by the one leaving.
     */
    private static <K extends SquareKeyed<K>, T> void moveKeys(Map<K, T> map,
        Map<TileKey, TileKey> moves)
    {
        Map<K, T> out = new LinkedHashMap<>();

        for (Map.Entry<K, T> entry : map.entrySet())
        {
            if (!moves.containsKey(entry.getKey().square())) out.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<K, T> entry : map.entrySet())
        {
            TileKey to = moves.get(entry.getKey().square());

            if (to != null) out.put(entry.getKey().withSquare(to), entry.getValue());
        }

        map.clear();
        map.putAll(out);
    }

    /** The same for a map whose values are LISTS of squares, named apart because erasure makes the signatures equal. */
    private static void moveListValues(Map<TileKey, List<TileKey>> map, Map<TileKey, TileKey> moves)
    {
        for (Map.Entry<TileKey, List<TileKey>> entry : map.entrySet())
        {
            List<TileKey> out = new ArrayList<>();

            for (TileKey value : entry.getValue())
            {
                TileKey moved = moves.get(value);

                out.add(moved == null ? value : moved);
            }

            entry.setValue(out);
        }
    }

    /** Repoints values that name a square that moved, in place: the entry stays where it is. */
    private static void moveValues(Map<TileKey, TileKey> map, Map<TileKey, TileKey> moves)
    {
        for (Map.Entry<TileKey, TileKey> entry : map.entrySet())
        {
            TileKey moved = moves.get(entry.getValue());

            if (moved != null) entry.setValue(moved);
        }
    }

    /** The same for a set of squares. */
    private static void moveMembers(Set<TileKey> set, Map<TileKey, TileKey> moves)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey key : set)
        {
            TileKey moved = moves.get(key);

            out.add(moved == null ? key : moved);
        }

        set.clear();
        set.addAll(out);
    }

    /** What a reconciliation found.  Nothing is acted on silently: a changing diagram should be visible. */
    public static class Reconciliation
    {
        /** Nothing was reconciled, for callers that decline to prune (AutonomySession.save, mid-renumber). */
        public Reconciliation()
        {
        }

        private final List<String> droppedTileProperties = new ArrayList<>();
        private final List<String> forgottenNames = new ArrayList<>();
        private final Map<String, List<String>> namesStillReferenced = new LinkedHashMap<>();
        private final List<String> declinedBecauseAbsent = new ArrayList<>();

        /**
         * A reconciliation that did not happen, and the pages that stopped it.  The no-argument constructor cannot say
         * why, and a refusal to tidy is the one moment somebody could put the missing page back before its id is
         * retired.
         *
         * @param absent the pages the setup knows about that are not loaded; empty when a suspect numbering refused
         */
        public static Reconciliation declined(List<String> absent)
        {
            Reconciliation out = new Reconciliation();

            if (absent != null) out.declinedBecauseAbsent.addAll(absent);

            return out;
        }

        /**
         * Pages that were not loaded when a save declined to reconcile; empty when nothing was declined, or when the
         * refusal was a suspect numbering rather than a missing page.
         */
        public List<String> getDeclinedBecauseAbsent()
        {
            return Collections.unmodifiableList(this.declinedBecauseAbsent);
        }

        /** Whether this reconciliation was refused rather than simply finding nothing. */
        public boolean wasDeclined()
        {
            return !this.declinedBecauseAbsent.isEmpty();
        }

        /** Lengths and directions removed with their tile: a deleted tile starts over. */
        public List<String> getDroppedTileProperties()
        {
            return Collections.unmodifiableList(droppedTileProperties);
        }

        /** Names dropped because their tile is gone and nothing referred to them; reported so the loss is visible. */
        public List<String> getForgottenNames()
        {
            return Collections.unmodifiableList(forgottenNames);
        }

        /**
         * Names whose tile is gone but which are still referenced, by a timetable, a home or a placement, name to what
         * refers to it.  The only ones kept: dropping one would break its referrer unexplained, and keeping it silently
         * would wire an unreachable Point into a timetable.  So it is kept AND named, for a person to resolve.
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
     * Brings the setup back into line with a diagram that has changed underneath it.  Tile properties and names have
     * different lifetimes: a length or a direction belongs to a tile and goes with it, but a NAME is referred to from
     * timetables, homes and placements, so it is kept only if something still refers to it.  Either way it is
     * reported, for the caller to show somebody.
     */
    public Reconciliation reconcile(Set<TileKey> existing)
    {
        Reconciliation report = new Reconciliation();

        Set<TileKey> keys = new LinkedHashSet<>();

        for (TileKey tile : existing)
        {
            keys.add(tile);
        }

        report.droppedTileProperties.addAll(asStrings(dropMissing(tileLengths, keys)));
        report.droppedTileProperties.addAll(asStrings(dropMissing(tileDirections, keys)));
        report.droppedTileProperties.addAll(asStrings(dropMissing(barredArrivals, keys)));
        // dropMissing tests the KEY, the station's square; the VALUES are squares too.  forgetSquares covers a signal
        // that is BUILT OVER, but a plain deletion would leave the pairing in the file to be INHERITED by whatever is
        // drawn there next, and autonomy would throw an accessory nobody paired.
        report.droppedTileProperties.addAll(asStrings(dropMissing(stationSignals, keys)));
        report.droppedTileProperties.addAll(asStrings(dropMissing(blockedPoints, keys)));
        report.droppedTileProperties.addAll(asStrings(dropMissing(entrySignals, keys)));

        // THE ENTRY GUARD'S SIGNAL SQUARES TOO (FR-096), on the protecting list's rule: a pairing to a gone square
        // would be inherited by whatever is drawn there next, and this list commands real hardware as well.
        dropPointers(entrySignals, signal -> !keys.contains(signal),
            signal -> report.droppedTileProperties.add("entry guard signal at " + signal));

        dropPointers(stationSignals, signal -> !keys.contains(signal),
            signal -> report.droppedTileProperties.add("protecting signal at " + signal));

        // And the squares a station is held back by, for the same reason: a restriction watching a square that no
        // longer exists cannot be satisfied and would be INHERITED by whatever is drawn there next.
        dropPointers(blockedPoints, blocker -> !keys.contains(blocker),
            blocker -> report.droppedTileProperties.add("restriction watching " + blocker));

        // linkNames and disabledPortals, both remembered BY SQUARE: a name and a switched-off flag for gone track would
        // be INHERITED by the next link drawn there.  Reported, so a lost link name is visible.
        report.droppedTileProperties.addAll(asStrings(dropMissing(linkNames, keys)));

        for (TileKey key : dropMissingMembers(disabledPortals, keys))
        {
            report.droppedTileProperties.add("link switched off at " + key);
        }

        // A caption goes when either end of it does: the square it is drawn on, or the sensor it is about.
        report.droppedTileProperties.addAll(reconcileCaptions(keys));

        List<TileKey> goneTiles = new ArrayList<>();

        for (TileKey key : pointNames.keySet())
        {
            if (!keys.contains(key)) goneTiles.add(key);
        }

        // Squares, not their printed form (FR-013): `pointNames.get(aString)` and `stations.remove(aString)` compile,
        // as Map.get and Set.remove take Object, and do nothing.  The report is printed where it is written.
        for (TileKey key : goneTiles)
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

        // A station that was never NAMED is not covered by the loop above (it walks names whose square has gone).  An
        // unnamed station is an ordinary state (setStation asks for no name); left behind, a sensor drawn there later
        // is silently a station again and checkNames raises a blocking UNNAMED_STATION about a square nobody can see.
        // Only the unnamed ones: a named station is kept above when a configuration still refers to it.
        List<TileKey> orphanStations = new ArrayList<>();

        for (TileKey key : stations)
        {
            if (!keys.contains(key) && !pointNames.containsKey(key)) orphanStations.add(key);
        }

        // Squares here too, as with goneTiles above.
        for (TileKey key : orphanStations)
        {
            stations.remove(key);
            report.droppedTileProperties.add("station at " + key);
        }

        // a portal whose partner is gone is half a pairing, which is worse than none
        List<String> brokenPairings = new ArrayList<>();

        for (Map.Entry<TileKey, TileKey> entry : portals.entrySet())
        {
            if (!keys.contains(entry.getKey()) || !keys.contains(entry.getValue()))
            {
                brokenPairings.add(entry.getKey().toString());
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
     * Everything in the configurations that names this Point.  Deliberately textual: a configuration is stored as it
     * was written, and parsing each shape would mean knowing the schema of things this class only stores.
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

    /** Adapts this store to what the reducer asks for. */
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

            @Override
            public boolean isTileLengthAnswered(TileKey tile)
            {
                return AutonomyCompanionStore.this.isTileLengthAnswered(tile);
            }
        };
    }

    /** Applies everything stored here to a tile graph: the portal pairings and the directions. */
    public void applyTo(TileGraph graph)
    {
        for (Map.Entry<TileKey, TileKey> entry : portals.entrySet())
        {
            TileKey from = entry.getKey();
            TileKey to = entry.getValue();

            if (from != null && to != null) graph.pairPortals(from, to);
        }

        for (TileKey id : disabledPortals)
        {
            TileKey tile = id;

            if (tile != null) graph.disablePortal(tile);
        }

        for (Map.Entry<DirectionKey, String> entry : tileDirections.entrySet())
        {
            try
            {
                graph.setDirection(entry.getKey().square(), entry.getKey().getRouteId(),
                    Direction.valueOf(entry.getValue()));
            }
            catch (RuntimeException e)
            {
                // a direction naming a route the tile no longer has is simply not applied
            }
        }

        // AND A CROSSING'S TWO HALVES AGREE: a direction stored on one square of a two-square crossing while it
        // was a permanent turnout ran its track one way on that square alone
        graph.settleCrossingHalves();

        // AND EVERY SQUARE RUNS AS ITS ARROWS SHOW (OB-351): a road shut a way its square's arrow draws open is opened,
        // whatever door or file put it there.  The session writes what changed back into this store.
        graph.normalizeToArms();

        // AND EVERY RUN RUNS ONE WAY ALONG ITS LENGTH (OB-361): a square that says more than the run it is part of is given
        // the run's direction, its narrowest, so what each square says is what a train finds.  Written back the same way.
        graph.normalizeRuns();
    }

    // internals

    /**
     * Every shared field this version writes.  A field NOT listed here is treated as something a newer TrainControl
     * wrote, kept aside on load and written back on save, so an omission is quietly destructive: the stale copy is
     * written after the real one and reverts every edit to that field.  Anything added to save() has to be added here.
     */
    private static final Set<String> KNOWN_SHARED_NOT_A_COLLECTION =
        new LinkedHashSet<>(java.util.Arrays.asList("version", "activeConfiguration", "pages"));

    /**
     * Every field name this build understands in the shared file, by its name in the FILE.  Anything else is a field a
     * NEWER build wrote: a LOAD keeps it verbatim and an id-translating import leaves it out.  Load-bearing: a
     * collection MISSING here would be read as unknown-and-preserved AND into its own collection, so the next save
     * writes both and the stale copy wins.  Derived from the registry rather than listed, so that cannot happen.
     */
    private Set<String> knownShared()
    {
        Set<String> known = new LinkedHashSet<>(KNOWN_SHARED_NOT_A_COLLECTION);

        for (Kept k : kept()) known.add(k.json);

        return known;
    }


    /**
     * What a held field's values are made of, the only thing the hold needs to know.  PLAIN is keyed by a square with
     * a value that is nobody's business here; the rest name squares as well, and an entry is held when ANY square it
     * names belongs to a page that is not loaded.
     */
    private enum Held
    {
        /** keyed by a square; the value is not one */
        PLAIN,
        /** keyed by a square; the value is a square */
        SQUARE_VALUE,
        /** keyed by a square; the value is one square or an array of them */
        SQUARE_LIST_VALUE,
        /** a bare list of squares */
        SQUARE_LIST,
        /** a bare list of whole PAGES, so the element is the page part itself */
        PAGE_LIST
    }

    /**
     * Every shared field that can be held for an absent page, and what its values are made of.  ONE list, read by both
     * the hold and the merge: a field in the merge but not the hold would come back with a page ID where a page NAME
     * belongs, and one in the hold but not the merge would be deleted from disk by a save while a page's file was
     * missing (OB-067).
     */
    private static final Map<String, Held> HELD_FIELDS = heldFields();

    /**
     * Which collections are held back when their page is not loaded, and in what shape they have in the FILE, by file
     * name.  A METHOD rather than a static block so the guard that insists every kept collection is handled everywhere
     * can address it by name; static, so the OB-025 registry (`kept()`) does not cover it.
     */
    private static Map<String, Held> heldFields()
    {
        Map<String, Held> fields = new LinkedHashMap<>();

        fields.put("pointNames", Held.PLAIN);
        fields.put("tileLengths", Held.PLAIN);
        fields.put("tileDirections", Held.PLAIN);
        fields.put("barredArrivals", Held.PLAIN);
        fields.put("linkNames", Held.PLAIN);
        fields.put("portals", Held.SQUARE_VALUE);
        fields.put("captions", Held.SQUARE_VALUE);
        fields.put("stationSignals", Held.SQUARE_LIST_VALUE);
        fields.put("entrySignals", Held.SQUARE_LIST_VALUE);
        fields.put("blockedPoints", Held.SQUARE_LIST_VALUE);
        fields.put("stations", Held.SQUARE_LIST);
        fields.put("disabledLinks", Held.SQUARE_LIST);
        fields.put("excludedPages", Held.PAGE_LIST);
        fields.put("keptDespiteRepeats", Held.PAGE_LIST);

        return java.util.Collections.unmodifiableMap(fields);
    }

    /**
     * The shared object with every entry naming an absent page taken out of it and held.  Only when the numbering is
     * known at all: a store loaded with no index (the on-disk repair path's first read) knows no page, holding
     * everything would empty it, and nothing can be misresolved there.  Answers a copy safe to read into the live
     * collections.
     */
    private JSONObject withoutAbsentPages(JSONObject root)
    {
        heldForAbsentPages.clear();

        if (pageIdToName.isEmpty()) return root;

        JSONObject out = new JSONObject();

        for (String field : root.keySet())
        {
            out.put(field, root.get(field));
        }

        for (Map.Entry<String, Held> field : HELD_FIELDS.entrySet())
        {
            switch (field.getValue())
            {
                case PLAIN:
                    holdEntries(out, field.getKey(), false, false);
                    break;

                case SQUARE_VALUE:
                    holdEntries(out, field.getKey(), true, false);
                    break;

                case SQUARE_LIST_VALUE:
                    holdEntries(out, field.getKey(), false, true);
                    break;

                case SQUARE_LIST:
                    holdElements(out, field.getKey(), false);
                    break;

                case PAGE_LIST:
                    holdElements(out, field.getKey(), true);
                    break;
            }
        }

        return out;
    }

    /** Moves the entries of one keyed field whose squares are not all here into the held set. */
    private void holdEntries(JSONObject root, String field, boolean valueIsASquare,
        boolean valueIsSquares)
    {
        JSONObject src = root.optJSONObject(field);

        if (src == null) return;

        JSONObject keep = new JSONObject();
        JSONObject held = new JSONObject();

        for (String key : src.keySet())
        {
            Object value = src.get(key);

            boolean here = allHere(key);

            if (here && valueIsASquare && value instanceof String) here = allHere((String) value);

            if (here && valueIsSquares)
            {
                if (value instanceof String)
                {
                    here = allHere((String) value);
                }
                else if (value instanceof JSONArray)
                {
                    JSONArray each = (JSONArray) value;

                    for (int at = 0; here && at < each.length(); at++)
                    {
                        here = allHere(String.valueOf(each.get(at)));
                    }
                }
            }

            if (here) keep.put(key, value); else held.put(key, value);
        }

        if (held.length() > 0) heldForAbsentPages.put(field, held);

        root.put(field, keep);
    }

    /** The same for a bare-list field; `wholePages` is true when the elements are page names, not squares. */
    private void holdElements(JSONObject root, String field, boolean wholePages)
    {
        JSONArray src = root.optJSONArray(field);

        if (src == null) return;

        JSONArray keep = new JSONArray();
        JSONArray held = new JSONArray();

        for (int at = 0; at < src.length(); at++)
        {
            String element = String.valueOf(src.get(at));

            boolean here = wholePages ? pageIsHere(element) : allHere(element);

            if (here) keep.put(src.get(at)); else held.put(src.get(at));
        }

        if (held.length() > 0) heldForAbsentPages.put(field, held);

        root.put(field, keep);
    }

    /**
     * Puts the held entries of one field back into what is about to be written, never over a live one: a page that has
     * come back is authoritative, and the held copy is older by definition.
     */
    private void mergeHeld(JSONObject root, String field)
    {
        Object held = heldForAbsentPages.get(field);

        if (held instanceof JSONObject && root.optJSONObject(field) != null)
        {
            JSONObject into = root.getJSONObject(field);
            JSONObject from = (JSONObject) held;

            for (String key : from.keySet())
            {
                if (!into.has(key)) into.put(key, from.get(key));
            }
        }
        else if (held instanceof JSONArray && root.optJSONArray(field) != null)
        {
            JSONArray into = root.getJSONArray(field);
            JSONArray from = (JSONArray) held;

            Set<String> seen = new LinkedHashSet<>();

            for (int at = 0; at < into.length(); at++) seen.add(String.valueOf(into.get(at)));

            for (int at = 0; at < from.length(); at++)
            {
                if (seen.add(String.valueOf(from.get(at)))) into.put(from.get(at));
            }
        }
    }

    /**
     * Reads the shared half of a setup object in, over a store already emptied of it.  Split out of load() so importing
     * can put a MERGED object through exactly the same reading, page-id translation and renumber detection included.
     * The active configuration is deliberately not read: importing must not change which one is running.
     *
     * @param wholeRoot the shared object as it came off disk.  The local `root` below is NOT this: it is the copy with
     *     absent pages filtered out, and the difference between the two names is load-bearing here.
     */
    private void readShared(JSONObject wholeRoot)
    {
        // FIRST: everything below is translated out of page ids and needs what those ids were called when this file was
        // written (the tileLengths loop is translated inline).
        readStringMap(wholeRoot, "pages", pageNamesWhenWritten);

        // Everything naming a page that is not loaded is taken out and held, not read in with the file's id standing in
        // for a page name (OB-067).  See heldForAbsentPages.
        JSONObject root = withoutAbsentPages(wholeRoot);

        // Read AND translated in one step, per field (FR-013): no window holds stored keys in a field the rest of the
        // class reads as memory keys (the state OB-067 was about).  `excludedPages` is the exception and says why for
        // itself: it holds page NAMES, so it is read raw and translated in its own read().
        for (Kept k : kept()) k.read(root);

        pageIdConflicts.clear();

        for (Map.Entry<String, String> entry : pageNamesWhenWritten.entrySet())
        {
            String nowCalled = pageIdToName.get(entry.getKey());

            if (nowCalled == null || nowCalled.equals(entry.getValue())) continue;

            // The id now carries a different name, which alone cannot tell two cases apart:
            //   renamed    - the same page, called something else: the old name is gone from the index and the settings
            //                are still that page's.
            //   renumbered - a DIFFERENT page holds this id: the old name is still in the index under another id, and
            //                adopting these settings would attach them to the wrong track.
            if (pageNameToId.containsKey(entry.getValue()))
            {
                pageIdConflicts.put(entry.getValue(), nowCalled);
            }
        }

        // From the whole object, not the filtered copy: an unmodeled field is written back verbatim.
        for (String key : wholeRoot.keySet())
        {
            if (!knownShared().contains(key)) unknownSharedFields.put(key, wholeRoot.get(key));
        }
    }


    // The kept collections (OB-025)

    /**
     * One collection of settings the store keeps, and everything the bookkeeping sites need of it: saving, loading,
     * clearing, renaming a page, moving or forgetting squares, snapshotting and restoring a page, deleting a page.
     * `testStoreCollectionsAreHandledEverywhere` makes an omission loud.  `reconcile` and `applyTo` stay written out by
     * hand: they ask what a square's absence means per collection, and populate the tile GRAPH, which has nowhere to
     * put a name or a length.
     */
    private abstract class Kept
    {
        /** The name this collection has in the file, which is not always its name in the code. */
        final String json;

        Kept(String json)
        {
            this.json = json;
        }

        /** Empties it. */
        abstract void clear();

        /** Writes it into the shared file's root object, translated to stored keys. */
        abstract void write(JSONObject root);

        /** Reads it back out, translating as it goes. */
        abstract void read(JSONObject root);

        /** Follows a page rename, on both halves of a collection that names squares twice. */
        abstract void renamePage(String from, String to);

        /** Follows squares being dragged somewhere else. */
        abstract void move(Map<TileKey, TileKey> byKey);

        /**
         * Drops everything about squares that are gone, and everything POINTING at them.  Reports whether it removed
         * anything: four call sites write the setup to disk on true, so a deleted piece of plain track must not
         * rebuild the graph and rewrite every file once per square.
         *
         * @param arriving the moves in progress, so a label being landed on by its own tile is spared
         */
        abstract boolean forget(Set<TileKey> squares, Map<TileKey, TileKey> arriving);

        /** Everything on one page, in a form restore can put back. */
        abstract Object snapshotOf(String page);

        /** Puts one page back as snapshotOf found it. */
        abstract void restoreTo(String page, Object snapshot);

        /**
         * Adds every square on this page that this collection names, as a key OR as a value (a protecting signal, a
         * blocker or the far end of a portal can sit on a page with no entry of its own).
         */
        abstract void squaresOn(String page, Set<TileKey> into);
    }

    /**
     * A map from a square to something that is not a square - a name, a length, a restriction.  Only how the value is
     * written and read differs, and that is left to the subclass.
     */
    private abstract class SquareMapKept<V> extends Kept
    {
        final Map<TileKey, V> map;

        SquareMapKept(String json, Map<TileKey, V> map)
        {
            super(json);
            this.map = map;
        }

        @Override void clear() { map.clear(); }

        @Override void renamePage(String from, String to) { rekey(map, from, to); }

        @Override void move(Map<TileKey, TileKey> byKey) { moveKeys(map, byKey); }

        @Override boolean forget(Set<TileKey> squares, Map<TileKey, TileKey> arriving)
        {
            boolean any = false;

            for (TileKey key : squares) any |= map.remove(key) != null;

            return any;
        }

        @Override Object snapshotOf(String page) { return onPage(map, page); }

        @Override @SuppressWarnings("unchecked")
        void restoreTo(String page, Object snapshot)
        {
            putBack(map, page, (Map<TileKey, V>) snapshot);
        }

        @Override void squaresOn(String page, Set<TileKey> into)
        {
            for (TileKey key : new LinkedHashSet<>(map.keySet()))
            {
                if (isOnPage(key, page)) into.add(key);
            }
        }
    }

    /** A square to a string: a name, a barred arrival, a link's label. */
    private class StringMapKept extends SquareMapKept<String>
    {
        StringMapKept(String json, Map<TileKey, String> map) { super(json, map); }

        @Override void write(JSONObject root) { root.put(json, new JSONObject(translateKeys(map))); }

        @Override void read(JSONObject root) { readSquareMap(root, json, map); }
    }

    /** A square to a length. */
    private class IntMapKept extends SquareMapKept<Integer>
    {
        IntMapKept(String json, Map<TileKey, Integer> map) { super(json, map); }

        @Override void write(JSONObject root)
        {
            root.put(json, new JSONObject(translateLengths(map)));
        }

        @Override void read(JSONObject root) { readSquareIntMap(root, json, map); }
    }

    /**
     * A square AND a route across it, to the way the track faces on that route.  The key is a square plus a route, so
     * a bare square never matches it and the bookkeeping must ask the key which square it is on (FR-013).
     */
    private class DirectionMapKept extends Kept
    {
        final Map<DirectionKey, String> map;

        DirectionMapKept(String json, Map<DirectionKey, String> map)
        {
            super(json);
            this.map = map;
        }

        @Override void clear() { map.clear(); }

        @Override void write(JSONObject root) { root.put(json, new JSONObject(translateKeys(map))); }

        @Override void read(JSONObject root) { readDirectionMap(root, json, map); }

        @Override void renamePage(String from, String to) { rekey(map, from, to); }

        @Override void move(Map<TileKey, TileKey> byKey) { moveKeys(map, byKey); }

        @Override boolean forget(Set<TileKey> squares, Map<TileKey, TileKey> arriving)
        {
            boolean any = false;

            for (java.util.Iterator<DirectionKey> keys = map.keySet().iterator(); keys.hasNext();)
            {
                if (squares.contains(keys.next().square()))
                {
                    keys.remove();
                    any = true;
                }
            }

            return any;
        }

        @Override Object snapshotOf(String page) { return onPage(map, page); }

        @Override @SuppressWarnings("unchecked")
        void restoreTo(String page, Object snapshot)
        {
            putBack(map, page, (Map<DirectionKey, String>) snapshot);
        }

        @Override void squaresOn(String page, Set<TileKey> into)
        {
            for (DirectionKey key : new LinkedHashSet<>(map.keySet()))
            {
                TileKey bare = key.square();

                if (bare != null && isOnPage(bare, page)) into.add(bare);
            }
        }
    }

    /**
     * A square to another square: a portal to its far end, a caption to the station it names.  Both halves are squares,
     * so both follow a rename and a move.
     */
    private class PairMapKept extends Kept
    {
        final Map<TileKey, TileKey> map;

        /**
         * Whether a landing square keeps its entry when the tile arriving on it is what the entry points at: captions
         * only.
         */
        final boolean spareArriving;

        PairMapKept(String json, Map<TileKey, TileKey> map, boolean spareArriving)
        {
            super(json);
            this.map = map;
            this.spareArriving = spareArriving;
        }

        @Override void clear() { map.clear(); }

        @Override void write(JSONObject root) { root.put(json, new JSONObject(translateTileMap(map))); }

        @Override void read(JSONObject root) { readSquarePairMap(root, json, map); }

        @Override void renamePage(String from, String to)
        {
            rekeyValues(map, from, to);
            rekey(map, from, to);
        }

        @Override void move(Map<TileKey, TileKey> byKey)
        {
            moveValues(map, byKey);
            moveKeys(map, byKey);
        }

        @Override boolean forget(Set<TileKey> squares, Map<TileKey, TileKey> arriving)
        {
            boolean any = false;

            for (TileKey key : squares)
            {
                if (spareArriving)
                {
                    TileKey names = map.get(key);

                    if (names != null && arriving != null && key.equals(arriving.get(names))) continue;
                }

                any |= map.remove(key) != null;
            }

            // And anything pointing AT a square that is gone.  No exception for the arriving tiles here: a square being
            // vacated is never in this set, because moveTiles builds it by excluding them.
            for (java.util.Iterator<Map.Entry<TileKey, TileKey>> pairs = map.entrySet().iterator();
                pairs.hasNext();)
            {
                if (squares.contains(pairs.next().getValue()))
                {
                    pairs.remove();
                    any = true;
                }
            }

            return any;
        }

        // BOTH ends: a portal's two halves are normally on different pages.
        @Override Object snapshotOf(String page) { return onPageEitherEnd(map, page); }

        @Override @SuppressWarnings("unchecked")
        void restoreTo(String page, Object snapshot)
        {
            putBackEitherEnd(map, page, (Map<TileKey, TileKey>) snapshot);
        }

        @Override void squaresOn(String page, Set<TileKey> into)
        {
            for (TileKey key : new LinkedHashSet<>(map.keySet()))
            {
                if (isOnPage(key, page)) into.add(key);
            }

            for (TileKey value : new ArrayList<>(map.values()))
            {
                if (isOnPage(value, page)) into.add(value);
            }
        }
    }

    /**
     * A square to a list of squares: the signals protecting a station, the squares holding it back.  The snapshot copies
     * the lists, so nothing that edits one in place can reach an undo.
     */
    private class ListMapKept extends Kept
    {
        final Map<TileKey, List<TileKey>> map;

        ListMapKept(String json, Map<TileKey, List<TileKey>> map)
        {
            super(json);
            this.map = map;
        }

        @Override void clear() { map.clear(); }

        @Override void write(JSONObject root)
        {
            root.put(json, new JSONObject(translateTileListMap(map)));
        }

        @Override void read(JSONObject root) { readSquareListMap(root, json, map); }

        @Override void renamePage(String from, String to)
        {
            rekeyListValues(map, from, to);
            rekey(map, from, to);
        }

        @Override void move(Map<TileKey, TileKey> byKey)
        {
            moveListValues(map, byKey);
            moveKeys(map, byKey);
        }

        @Override boolean forget(Set<TileKey> squares, Map<TileKey, TileKey> arriving)
        {
            boolean any = false;

            for (TileKey key : squares) any |= map.remove(key) != null;

            // A pointer at track that has been built over is one nothing can satisfy or clear.
            return dropPointers(map, squares::contains, gone -> { }) | any;
        }

        // BOTH ends: a station's protecting signal and a blocking point are routinely on another page.
        @Override Object snapshotOf(String page)
        {
            return copyLists(onPageEitherEnd(map, page, true));
        }

        @Override @SuppressWarnings("unchecked")
        void restoreTo(String page, Object snapshot)
        {
            putBackEitherEnd(map, page, copyLists((Map<TileKey, List<TileKey>>) snapshot), true);
        }

        @Override void squaresOn(String page, Set<TileKey> into)
        {
            for (TileKey key : new LinkedHashSet<>(map.keySet()))
            {
                if (isOnPage(key, page)) into.add(key);
            }

            for (List<TileKey> pointed : new ArrayList<>(map.values()))
            {
                for (TileKey one : pointed) if (isOnPage(one, page)) into.add(one);
            }
        }
    }

    /** A plain set of squares: which are stations, which links are switched off. */
    private class SquareSetKept extends Kept
    {
        final Set<TileKey> set;

        /** Whether the members are paired across pages, which only `disabledLinks` is. */
        final boolean paired;

        SquareSetKept(String json, Set<TileKey> set)
        {
            this(json, set, false);
        }

        SquareSetKept(String json, Set<TileKey> set, boolean paired)
        {
            super(json);
            this.set = set;
            this.paired = paired;
        }

        @Override void clear() { set.clear(); }

        @Override void write(JSONObject root) { root.put(json, new JSONArray(translateSet(set))); }

        @Override void read(JSONObject root) { readSquareSet(root, json, set); }

        @Override void renamePage(String from, String to)
        {
            Set<TileKey> renamed = new LinkedHashSet<>();

            for (TileKey key : set) renamed.add(rekeyOne(key, from, to));

            set.clear();
            set.addAll(renamed);
        }

        @Override void move(Map<TileKey, TileKey> byKey) { moveMembers(set, byKey); }

        @Override boolean forget(Set<TileKey> squares, Map<TileKey, TileKey> arriving)
        {
            return set.removeAll(squares);
        }

        @Override Object snapshotOf(String page)
        {
            return paired ? membersOnPageEitherEnd(set, page) : membersOnPage(set, page);
        }

        @Override @SuppressWarnings("unchecked")
        void restoreTo(String page, Object snapshot)
        {
            if (paired) putPairedMembersBack(set, page, (Set<TileKey>) snapshot);

            else putMembersBack(set, page, (Set<TileKey>) snapshot);
        }

        @Override void squaresOn(String page, Set<TileKey> into)
        {
            for (TileKey key : new LinkedHashSet<>(set))
            {
                if (isOnPage(key, page)) into.add(key);
            }
        }
    }

    /**
     * A set of whole PAGES rather than of squares: which pages autonomy is told to ignore.  Every square-level site
     * does nothing here, by decision (moving or deleting squares neither moves nor deletes a page).  It is translated
     * by page id on write, so a rename carries the decision with it.
     */
    private class PageSetKept extends Kept
    {
        final Set<String> set;

        PageSetKept(String json, Set<String> set)
        {
            super(json);
            this.set = set;
        }

        @Override void clear() { set.clear(); }

        @Override void write(JSONObject root) { root.put(json, new JSONArray(translatePages(set))); }

        @Override void read(JSONObject root)
        {
            // Read raw and translated afterwards - it holds page names, not squares, so the reader
            // that translates as it goes has nothing to translate with.
            readStringSet(root, json, set);
            untranslatePages(set);
        }

        @Override void renamePage(String from, String to)
        {
            if (set.remove(from)) set.add(to);
        }

        @Override void move(Map<TileKey, TileKey> byKey) { }

        @Override boolean forget(Set<TileKey> squares, Map<TileKey, TileKey> arriving) { return false; }

        @Override Object snapshotOf(String page) { return null; }

        @Override void restoreTo(String page, Object snapshot) { }

        @Override void squaresOn(String page, Set<TileKey> into) { }
    }

    /** Built on first use: a field initializer would hold nulls for the collections declared below it. */
    private List<Kept> kept;

    /**
     * Every collection of settings the store keeps.  This is the list to add to: clearing, saving, loading, renames,
     * moves, deletions, snapshots and page deletion all walk it.
     */
    private List<Kept> kept()
    {
        if (kept != null) return kept;

        List<Kept> all = new ArrayList<>();

        all.add(new StringMapKept("pointNames", pointNames));
        all.add(new SquareSetKept("stations", stations));
        all.add(new IntMapKept("tileLengths", tileLengths));
        all.add(new DirectionMapKept("tileDirections", tileDirections));
        all.add(new StringMapKept("barredArrivals", barredArrivals));
        all.add(new ListMapKept("stationSignals", stationSignals));
        all.add(new ListMapKept("entrySignals", entrySignals));
        all.add(new ListMapKept("blockedPoints", blockedPoints));
        all.add(new PairMapKept("portals", portals, false));
        all.add(new PairMapKept("captions", captions, true));
        all.add(new StringMapKept("linkNames", linkNames));
        all.add(new PageSetKept("excludedPages", excludedPages));

        // Page NAMES, translated on write like `excludedPages`, so a rename carries the decision with it.
        all.add(new PageSetKept("keptDespiteRepeats", keptDespiteRepeats));
        // Called disabledLinks in the file and disabledPortals in the code.  Named once, here.
        all.add(new SquareSetKept("disabledLinks", disabledPortals, true));

        kept = all;

        return kept;
    }

    private void clear()
    {
        // One list, walked by clear and clearShared alike: a hand-kept list missing a collection would let a pairing
        // made since the last save survive a discard and be written to disk.
        for (Kept k : kept()) k.clear();

        unknownSharedFields.clear();
        pageNamesWhenWritten.clear();
        heldForAbsentPages.clear();
        pageIdConflicts.clear();
        configurations.clear();
        activeConfiguration = null;
    }

    private File folder()
    {
        return new File(layoutFolder, FOLDER);
    }

    /** Where the setup as it stood before an editing session is kept (OB-108); exists only during an edit. */
    private File beforeEditFile()
    {
        return new File(folder(), "setup-before-edit.json");
    }

    /**
     * Writes down what the setup looked like before the layout editor started changing it (OB-108).  On disk, not in
     * memory, because a memory snapshot is lost by exactly the event it exists to survive, the process dying while the
     * editor is open: the setup is written after every drag but the diagram only at Save, so the next reconciling save
     * would prune the difference as track that does not exist.  It is put back if it is still there next time, which
     * can only mean the last session did not finish.  Answers whether it reached the disk.
     */
    public boolean rememberBeforeEdit(JSONObject was)
    {
        if (!isUsable() || was == null) return false;

        try
        {
            folder().mkdirs();

            writeJson(beforeEditFile(), was);

            return true;
        }
        catch (IOException couldNotWrite)
        {
            // The edit goes ahead either way: refusing to edit because a safety net could not be written would be a
            // worse trade than the net being absent.
            return false;
        }
    }

    /**
     * Forgets the pre-edit snapshot, because the editing session ended properly: Save (diagram and setup in step) or
     * Cancel (the editor already put the setup back).  Nothing else may clear it; "still here" means the last session
     * did not finish.
     */
    public boolean forgetBeforeEdit()
    {
        File was = beforeEditFile();

        if (!was.isFile()) return true;

        if (was.delete()) return true;

        // The delete failed, and under OneDrive that is not exotic.  A note that cannot be removed will be APPLIED, and
        // applying it after a clean Save undoes the work just saved, so it is made harmless: overwritten with the setup
        // as it stands now.  Written IN PLACE, not through writeAtomically, whose move needs the DELETE access that
        // just failed.  A torn write is acceptable: a half-written note fails unfinishedEdit's shape check, so it is
        // refused, kept and reported.
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(was))
        {
            out.write(snapshotSetup().toString(2).getBytes(StandardCharsets.UTF_8));

            out.flush();
        }
        catch (IOException | RuntimeException norThat)
        {
            return false;
        }

        // Still false: the note is harmless now, but it is still there, and the caller says so.
        return false;
    }

    /**
     * Whether a pre-edit note is on disk at all, whatever this build can make of it.  `unfinishedEdit` returning null
     * means either no note (ordinary, silent) or one that cannot be used, which is worth a line in the log or it is
     * refused again at every start in silence.
     */
    public boolean hasUnfinishedEditNote()
    {
        return beforeEditFile().isFile();
    }

    /** The setup as it stood before an editing session that never ended; null when the last session closed properly. */
    public JSONObject unfinishedEdit()
    {
        File was = beforeEditFile();

        if (!was.isFile()) return null;

        JSONObject note;

        try
        {
            note = new JSONObject(new String(Files.readAllBytes(was.toPath()),
                StandardCharsets.UTF_8));
        }
        catch (IOException | RuntimeException unreadable)
        {
            // Unreadable is not absent, but the safe answer is the same: leave the setup alone, since reverting to
            // something that will not parse would be the loss this is meant to prevent.
            return null;
        }

        // And the same checks `load()` makes: `restoreSetup` clears the store and then reads, which is safe for what
        // `snapshotSetup` produces and unsafe for a note off a disk written by another run or build (`{}`, an empty
        // setup, a version 99 file `load()` would refuse).  Refused and KEPT, not deleted: a note this build cannot
        // read may belong to the newer TrainControl run next, and deleting it would throw away that build's safety net.
        if (!(note.opt("shared") instanceof JSONObject)
            || !(note.opt("configurations") instanceof JSONObject)
            || note.getJSONObject("shared").optInt("version", VERSION) > VERSION)
        {
            return null;
        }

        return note;
    }

    /**
     * The lowest version that can read what is about to be written: 2 where some station carries more than one signal,
     * 1 otherwise.  Not simply VERSION: bumping every file to 2 would make every setup unreadable by the previous
     * build, including the great majority that use nothing new.
     */
    private int versionWritten()
    {
        for (List<TileKey> signals : stationSignals.values())
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
     * Whether another configuration would be written to the same file as this name.  Sanitizing is many to one, so
     * "Night: Yard" and "Night_ Yard" are two configurations and one file: saving would write the second over the first
     * and the next load, scanning the folder, would find one gone.  Checked at the three doors a name comes in by
     * (create, import, rename).  Answers the configuration that already owns that file, or null; `except` may share it
     * (the one being renamed away from).
     */
    private String fileNameTaken(String name, String except)
    {
        File wanted = configurationFile(name);

        for (String existing : configurations.keySet())
        {
            if (existing.equals(name) || existing.equals(except)) continue;

            if (configurationFile(existing).equals(wanted)) return existing;
        }

        return null;
    }

    /**
     * Refuses a name that would be saved in another configuration's file, naming that configuration.  Its own refusal
     * rather than `ERROR_NAME_IN_USE`, which would say "a configuration called X already exists" when X, the name
     * typed, is exactly the one that does not.
     *
     * @throws IOException a `NameSharesAFile`, naming the configuration that already owns that file
     */
    private void refuseASharedFile(String name, String except) throws IOException
    {
        String owner = fileNameTaken(name, except);

        if (owner != null) throw new NameSharesAFile(owner);
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
     * A key as it is stored: the page id where one is known, the page name otherwise, so a page added since the index
     * was read still round trips.  Ids are not distinguishable from names (`validateLayoutName` allows digits, so a
     * page may be called "2"), and a page whose NAME equals another's ID misroutes this and pageOf (OB-067); the
     * on-disk repair path (every key in id form) is the most exposed.  FR-013 dissolves it by replacing these keys with
     * TileKey.
     */
    private String toStored(String key)
    {
        // The LAST colon, as parseTileKey, isOnPage and rekeyOne do (OB-071): a page name may contain one ("Yard:
        // Upper"), and splitting on the first would store its squares under another page's id.
        int colon = key.lastIndexOf(':');

        if (colon < 0) return key;

        // Every key in memory belongs to a loaded page: anything else was held back at read time (OB-067, see
        // heldForAbsentPages), so this map is the right one to ask.
        String id = pageNameToId.get(key.substring(0, colon));

        return id == null ? key : id + key.substring(colon);
    }

    private String fromStored(String key)
    {
        // The LAST colon - see toStored above.
        int colon = key.lastIndexOf(':');

        if (colon < 0) return key;

        return pageOf(key.substring(0, colon)) + key.substring(colon);
    }

    /**
     * The page a stored key's page part (an id, or a name on a file old enough) names, or null when the index does not
     * know it.  The ONE place that answers "which page is this", so the two methods below cannot drift.
     */
    private String resolvePage(String stored)
    {
        String whenWritten = pageNamesWhenWritten.get(stored);

        // RENUMBERED: the name the file recorded is still in the index, under whatever number.
        if (whenWritten != null && pageNameToId.containsKey(whenWritten)) return whenWritten;

        // RENAMED, or simply unmoved: the index knows this id (the case page ids exist for; leaving it out would hold
        // back a renamed page, MT-135).
        return pageIdToName.get(stored);
    }

    /**
     * What page a stored id means, which is not always the page that holds that id now.  If the name this id used to
     * carry still exists somewhere (the question pageIdConflicts asks) the page was RENUMBERED and the NAME is
     * followed; otherwise it was renamed and the current index is right.  Reading through the index regardless would
     * reattach the whole setup to the wrong page after a renumber, and the next save would reconcile away every setting
     * whose coordinates did not exist there.  Answers the id itself when nothing is known about it.
     */
    private String pageOf(String id)
    {
        String resolved = resolvePage(id);

        // The id itself when nothing resolves it; pageIsHere exists because an id is a legal page name.
        return resolved == null ? id : resolved;
    }

    /**
     * Whether a stored page part names a page that is actually here, which `pageOf` cannot answer by its return value:
     * it hands back the id when it fails, and an id is a legal page name.
     */
    private boolean pageIsHere(String stored)
    {
        if (resolvePage(stored) != null) return true;

        // The file names this id and neither question found it: the page is genuinely not loaded.
        if (pageNamesWhenWritten.get(stored) != null) return false;

        // Otherwise it is a NAME, and unambiguous: files written before keys were stored by id hold names, and a page
        // added since the index was read has no id yet (testAnExcludedPageWithNoIdKeepsItsName).
        return true;
    }

    /**
     * Whether every square a stored entry names (`parts`: its key, and any square it names) belongs to a page that is
     * here.  The VALUE side matters as much as the key: an entry anchored on a loaded page can still name an absent
     * one, and half-translating it would leave the id-as-name this mechanism exists to prevent.
     */
    private boolean allHere(String... parts)
    {
        for (String part : parts)
        {
            if (part == null) continue;

            int colon = part.lastIndexOf(':');

            if (!pageIsHere(colon < 0 ? part : part.substring(0, colon))) return false;
        }

        return true;
    }

    /**
     * A collection's keys in the form they go on disk - page ID rather than page name.  Generic over
     * {@link SquareKeyed} (FR-013), so one method serves a square and a square plus a route.
     */
    private <K extends SquareKeyed<K>> Map<String, String> translateKeys(Map<K, String> map)
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<K, String> entry : map.entrySet())
        {
            out.put(toStored(entry.getKey().toString()), entry.getValue());
        }

        return out;
    }

    /**
     * Reads the directions in, translating each key as it is read: the counterpart of readSquareMap for the one
     * collection keyed by a square AND a route.  One pass is the point, since reading raw and translating afterwards
     * would leave stored keys in a memory-keyed field, and `Map.get` and `Set.contains` take Object, so a stored key
     * silently answers false.  A key that will not parse is dropped, as in the other collections.
     */
    private void readDirectionMap(JSONObject root, String field, Map<DirectionKey, String> into)
    {
        JSONObject object = root.optJSONObject(field);

        if (object == null) return;

        for (String key : object.keySet())
        {
            String memory = fromStored(key);

            int hash = memory.lastIndexOf('#');

            if (hash < 0) continue;

            TileKey tile = parseTileKey(memory.substring(0, hash));

            if (tile == null) continue;

            String[] route = memory.substring(hash + 1).split(",");

            if (route.length != 2) continue;

            try
            {
                into.put(new DirectionKey(tile,
                    new RouteId(Integer.parseInt(route[0]), Integer.parseInt(route[1]))),
                    object.getString(key));
            }
            catch (NumberFormatException notARoute)
            {
                // A key whose route part is not two numbers is not one this build wrote.
            }
        }
    }

    private Map<String, Object> translateTileListMap(Map<TileKey, List<TileKey>> map)
    {
        Map<String, Object> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, List<TileKey>> entry : map.entrySet())
        {
            List<String> values = new ArrayList<>();

            for (TileKey value : entry.getValue())
            {
                values.add(toStored(value.toString()));
            }

            // One is written as a bare string, as every earlier version wrote; an array only where a second signal is
            // paired.
            out.put(toStored(entry.getKey().toString()), values.size() == 1 ? values.get(0) : new JSONArray(values));
        }

        return out;
    }

    private Map<String, String> translateTileMap(Map<TileKey, TileKey> map)
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, TileKey> entry : map.entrySet())
        {
            out.put(toStored(entry.getKey().toString()), toStored(entry.getValue().toString()));
        }

        return out;
    }

    /**
     * The same for a set of whole PAGES rather than of squares.  toStored and fromStored leave anything without a colon
     * alone, so a bare page name goes through unchanged; a page the index has never heard of keeps its name, which
     * keeps this safe on an old file and on a page added since the index was read.
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
            // The same question as fromStored: an excluded page is named by id like everything else, and getting it
            // wrong silently re-includes a page in autonomy.  But ONLY when the file itself recorded that id (OB-092):
            // `translatePages` falls back to a bare page NAME, and a name that looks like a number would be read back
            // as somebody else's id.  The file's own `pages` record is the discriminator.
            out.add(pageNamesWhenWritten.containsKey(stored) ? pageOf(stored) : stored);
        }

        pages.clear();
        pages.addAll(out);
    }

    private Set<String> translateSet(Set<TileKey> set)
    {
        Set<String> out = new LinkedHashSet<>();

        for (TileKey key : set)
        {
            out.add(toStored(key.toString()));
        }

        return out;
    }

    /**
     * Takes the map rather than reaching for the field: a helper that works for one entry breaks the registry's
     * promise that one entry is enough, as a second int-valued collection would be written under the first one's name.
     */
    private Map<String, Integer> translateLengths(Map<TileKey, Integer> lengths)
    {
        Map<String, Integer> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Integer> entry : lengths.entrySet())
        {
            out.put(toStored(entry.getKey().toString()), entry.getValue());
        }

        return out;
    }

    // captions

    /** The square a caption is about: the sensor's square, or null when no caption is drawn on `captionTile`. */
    public TileKey getCaptionTarget(TileKey captionTile)
    {
        if (captionTile == null) return null;

        return captions.get(captionTile);
    }

    /**
     * Draws a station's caption on a square (a null stationTile clears).  One caption per square (the map is keyed by
     * the square the text sits on), while several squares may name one station as far as this store goes: the session
     * takes the old ones off when a new one is chosen (OB-256), and the setup check reports a station drawn twice on
     * one page (OB-341).
     */
    public void setCaption(TileKey captionTile, TileKey stationTile)
    {
        if (captionTile == null) return;

        if (stationTile == null)
        {
            captions.remove(captionTile);
            return;
        }

        captions.put(captionTile, stationTile);
    }

    /** Every square carrying a caption about this sensor; possibly none. */
    public Set<TileKey> captionsFor(TileKey stationTile)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (stationTile == null) return out;

        // Compared as SQUARES (FR-013): comparing the printed form, `wanted.equals(entry.getValue())` compiles, as
        // String.equals takes Object, and is false for every entry once the values are squares.
        for (Map.Entry<TileKey, TileKey> entry : captions.entrySet())
        {
            if (stationTile.equals(entry.getValue()))
            {
                TileKey where = entry.getKey();

                if (where != null) out.add(where);
            }
        }

        return out;
    }

    /** Every caption, as caption square to sensor square. */
    public Map<TileKey, TileKey> getCaptions()
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, TileKey> entry : captions.entrySet())
        {
            TileKey where = entry.getKey();
            TileKey what = entry.getValue();

            if (where != null && what != null) out.put(where, what);
        }

        return out;
    }

    /**
     * Forgets every caption whose station, or whose page, is no longer on the diagram, and answers which were dropped.
     * Called from reconcile.  A caption goes when the STATION is gone, and not before: the square the text sits on is
     * deliberately not required to hold anything (placeCaption prefers blank space, and the migration empties a text
     * label there), or the next save would delete the captions just placed.  The page still has to exist.
     */
    private List<String> reconcileCaptions(Set<TileKey> existing)
    {
        Set<String> pagesLeft = new LinkedHashSet<>();

        for (TileKey tile : existing)
        {
            if (tile != null) pagesLeft.add(tile.getPage());
        }

        List<String> dropped = new ArrayList<>();

        for (Map.Entry<TileKey, TileKey> entry : new LinkedHashMap<>(captions).entrySet())
        {
            TileKey where = entry.getKey();

            boolean pageLeft = where != null && pagesLeft.contains(where.getPage());

            if (pageLeft && existing.contains(entry.getValue())) continue;

            captions.remove(entry.getKey());
            dropped.add(entry.getKey().toString());
        }

        return dropped;
    }

    /** A station together with the page a caption of it sits on, as one value to be compared. */
    private static String captionedOnItsPage(String captionKey, Object station)
    {
        TileKey square = parseTileKey(captionKey);

        return station + "\n" + (square == null ? "" : square.getPage());
    }

    /**
     * Two keys in the file in the order of their squares: by page id, as text, so "10" comes before "2", then row, then
     * column.  A key that is no square comes after those, as text, so the order is total.
     */
    private static int bySquare(String a, String b)
    {
        TileKey first = parseTileKey(a);
        TileKey second = parseTileKey(b);

        if (first == null || second == null)
        {
            return first == null && second == null ? a.compareTo(b) : first == null ? 1 : -1;
        }

        int byPage = first.getPage().compareTo(second.getPage());

        if (byPage != 0) return byPage;

        int byRow = Integer.compare(first.getY(), second.getY());

        return byRow != 0 ? byRow : Integer.compare(first.getX(), second.getX());
    }

    // Public: the session prunes stale point data by tile, and has to be able to read the page out of a stored key to tell
    // "this square is gone" from "this square is on a page autonomy was told to ignore".
    public static TileKey parseTileKey(String key)
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

    /** The same square on a renamed page, or the square unchanged (FR-013). */
    private static TileKey rekeyOne(TileKey key, String fromPage, String toPage)
    {
        if (key == null) return null;

        return key.getPage().equals(fromPage) ? new TileKey(toPage, key.getX(), key.getY()) : key;
    }

    /**
     * The same key on a renamed page, by isOnPage's rule: renaming "Yard" must not rewrite the keys of "Yard: Upper".
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

    /** Moves a collection's entries from one page name to another. */
    private static <K extends SquareKeyed<K>, T> void rekey(Map<K, T> map, String fromPage,
        String toPage)
    {
        Map<K, T> renamed = new LinkedHashMap<>();

        for (Map.Entry<K, T> entry : map.entrySet())
        {
            renamed.put(
                entry.getKey().withSquare(rekeyOne(entry.getKey().square(), fromPage, toPage)),
                entry.getValue());
        }

        map.clear();
        map.putAll(renamed);
    }

    private static void rekeyValues(Map<TileKey, TileKey> map, String fromPage, String toPage)
    {
        for (Map.Entry<TileKey, TileKey> entry : map.entrySet())
        {
            entry.setValue(rekeyOne(entry.getValue(), fromPage, toPage));
        }
    }

    private static void rekeyListValues(Map<TileKey, List<TileKey>> map, String fromPage, String toPage)
    {
        for (Map.Entry<TileKey, List<TileKey>> entry : map.entrySet())
        {
            List<TileKey> out = new ArrayList<>();

            for (TileKey value : entry.getValue())
            {
                out.add(rekeyOne(value, fromPage, toPage));
            }

            entry.setValue(out);
        }
    }

    /** Keys, as the strings a report shows a person (FR-013): the one door where a key becomes its printed form. */
    private static <K> List<String> asStrings(List<K> keys)
    {
        List<String> out = new ArrayList<>();

        for (K key : keys)
        {
            out.add(key.toString());
        }

        return out;
    }

    /** The same, for a plain set of squares: the ones that were dropped. */
    private static List<TileKey> dropMissingMembers(Set<TileKey> members, Set<TileKey> existing)
    {
        List<TileKey> gone = new ArrayList<>();

        for (TileKey key : members)
        {
            if (!existing.contains(key)) gone.add(key);
        }

        members.removeAll(gone);

        return gone;
    }

    /** Forgets a collection's entries whose square is no longer on the diagram, answering the keys dropped. */
    private static <K extends SquareKeyed<K>, T> List<K> dropMissing(Map<K, T> map,
        Set<TileKey> existing)
    {
        List<K> gone = new ArrayList<>();

        for (K key : map.keySet())
        {
            if (!existing.contains(key.square())) gone.add(key);
        }

        for (K key : gone)
        {
            map.remove(key);
        }

        return gone;
    }

    /**
     * Drops the pointers at squares that are gone from each list of a square-to-squares collection, and the entries left
     * with none, telling `dropped` each; true if anything went.  A NEW list each time, never an edit of the one held.
     */
    private static boolean dropPointers(Map<TileKey, List<TileKey>> map,
        java.util.function.Predicate<TileKey> gone, java.util.function.Consumer<TileKey> dropped)
    {
        boolean any = false;

        for (java.util.Iterator<Map.Entry<TileKey, List<TileKey>>> pairs
            = map.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<TileKey, List<TileKey>> pair = pairs.next();

            List<TileKey> kept = new ArrayList<>();

            for (TileKey pointer : pair.getValue())
            {
                if (!gone.test(pointer)) kept.add(pointer);
                else
                {
                    dropped.accept(pointer);
                    any = true;
                }
            }

            if (kept.isEmpty())
            {
                pairs.remove();
                any = true;
            }
            else pair.setValue(kept);
        }

        return any;
    }

    /**
     * Reads a square-keyed field, translating each key as it arrives (FR-013): stored keys in a field the rest of the
     * class reads as memory keys never match (OB-067).  A key that will not parse is dropped.
     */
    private void readSquareMap(JSONObject root, String field, Map<TileKey, String> into)
    {
        JSONObject object = root.optJSONObject(field);

        if (object == null) return;

        for (String key : object.keySet())
        {
            TileKey tile = parseTileKey(fromStored(key));

            if (tile != null) into.put(tile, object.getString(key));
        }
    }

    /** The same, for the one field whose values are numbers. */
    private void readSquareIntMap(JSONObject root, String field, Map<TileKey, Integer> into)
    {
        JSONObject object = root.optJSONObject(field);

        if (object == null) return;

        for (String key : object.keySet())
        {
            TileKey tile = parseTileKey(fromStored(key));

            if (tile != null) into.put(tile, object.getInt(key));
        }
    }

    /**
     * The same, where the VALUE is a square as well - a portal's partner, a caption's station.  An entry whose value
     * will not parse is dropped whole: half a pairing is worse than none.
     */
    private void readSquarePairMap(JSONObject root, String field, Map<TileKey, TileKey> into)
    {
        JSONObject object = root.optJSONObject(field);

        if (object == null) return;

        for (String key : object.keySet())
        {
            TileKey tile = parseTileKey(fromStored(key));
            TileKey value = parseTileKey(fromStored(object.getString(key)));

            if (tile != null && value != null) into.put(tile, value);
        }
    }

    /**
     * The same, where the value is one square or an array of them.  A member that will not parse is dropped and the
     * rest kept, so one bad name does not cost a station its whole set of protecting signals.
     */
    private void readSquareListMap(JSONObject root, String field, Map<TileKey, List<TileKey>> into)
    {
        JSONObject object = root.optJSONObject(field);

        if (object == null) return;

        for (String key : object.keySet())
        {
            TileKey tile = parseTileKey(fromStored(key));

            if (tile == null) continue;

            List<TileKey> values = new ArrayList<>();

            Object raw = object.get(key);

            if (raw instanceof JSONArray)
            {
                JSONArray each = (JSONArray) raw;

                for (int i = 0; i < each.length(); i++)
                {
                    TileKey member = parseTileKey(fromStored(each.getString(i)));

                    if (member != null) values.add(member);
                }
            }
            else
            {
                TileKey only = parseTileKey(fromStored(String.valueOf(raw)));

                if (only != null) values.add(only);
            }

            // An entry whose members ALL failed to parse is dropped, not stored empty: `"key": []` is the array form
            // this file only gained at version 2, which the version gate exists to keep from an older TrainControl.
            if (!values.isEmpty()) into.put(tile, values);
        }
    }

    /** A set of squares, translated as it is read. */
    private void readSquareSet(JSONObject root, String field, Set<TileKey> into)
    {
        JSONArray array = root.optJSONArray(field);

        if (array == null) return;

        for (int i = 0; i < array.length(); i++)
        {
            TileKey tile = parseTileKey(fromStored(array.getString(i)));

            if (tile != null) into.add(tile);
        }
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

    /**
     * Folds the timetable entries an earlier build set aside for pages out of autonomy back into the configuration's
     * timetable, each after the entry it followed (first where it followed none, last where that entry is gone), and
     * forgets the list.  The railway keeps every entry and refuses one it cannot run when the run reaches it.
     */
    static void bringBackTheEntriesSetAside(JSONObject configuration)
    {
        JSONArray aside = configuration.optJSONArray(ENTRIES_SET_ASIDE);

        configuration.remove(ENTRIES_SET_ASIDE);

        if (aside == null || aside.length() == 0) return;

        JSONObject globals = configuration.optJSONObject("globals");

        if (globals == null)
        {
            globals = new JSONObject();

            configuration.put("globals", globals);
        }

        JSONArray table = globals.optJSONArray("timetable");

        List<Object> kept = new ArrayList<>();

        for (int i = 0; table != null && i < table.length(); i++) kept.add(table.get(i));

        java.util.Set<Object> broughtBack = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (int i = 0; i < aside.length(); i++)
        {
            JSONObject item = aside.optJSONObject(i);

            JSONObject entry = item == null ? null : item.optJSONObject("entry");

            if (entry == null) continue;

            JSONObject after = item.optJSONObject("after");

            int at = after == null ? 0 : kept.size();

            for (int j = 0; after != null && j < kept.size(); j++)
            {
                Object candidate = kept.get(j);

                if (candidate instanceof JSONObject && !broughtBack.contains(candidate)
                    && sameRun(after, (JSONObject) candidate))
                {
                    at = j + 1;

                    break;
                }
            }

            while (at < kept.size() && broughtBack.contains(kept.get(at))) at++;

            kept.add(at, entry);

            broughtBack.add(entry);
        }

        globals.put("timetable", new JSONArray(kept));
    }

    /** Whether two timetable entries send the same train the same way. */
    private static boolean sameRun(JSONObject a, JSONObject b)
    {
        if (a == null || b == null || !a.optString("loc").equals(b.optString("loc"))) return false;

        JSONArray p = a.optJSONArray("path");
        JSONArray q = b.optJSONArray("path");

        if (p == null || q == null || p.length() != q.length()) return false;

        for (int i = 0; i < p.length(); i++)
        {
            JSONObject x = p.optJSONObject(i);
            JSONObject y = q.optJSONObject(i);

            if (x == null || y == null || !x.optString("start").equals(y.optString("start"))
                || !x.optString("end").equals(y.optString("end")))
            {
                return false;
            }
        }

        return true;
    }
}
