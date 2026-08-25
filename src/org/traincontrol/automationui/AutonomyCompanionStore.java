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
    private final Map<TileKey, String> pointNames = new LinkedHashMap<>();
    private final Set<TileKey> stations = new LinkedHashSet<>();
    private final Map<TileKey, Integer> tileLengths = new LinkedHashMap<>();
    private final Map<DirectionKey, String> tileDirections = new LinkedHashMap<>();

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
    private final Map<TileKey, String> barredArrivals = new LinkedHashMap<>();

    /**
     * Which sides trains may not arrive by.
     *
     * @param tile the station's square
     * @return the barred sides, empty when the station takes trains from anywhere
     */
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

    /**
     * @return every square carrying an arrival restriction, against the sides it bars
     */
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
    private final Map<TileKey, List<TileKey>> stationSignals = new LinkedHashMap<>();

    /**
     * Squares whose occupancy makes a station unavailable to autonomy (FR-001).
     *
     * Station square to the squares being watched, the same shape as the signals above and kept for the
     * same reason: a station may be held back by more than one place, and each of them is a SQUARE
     * here, resolved to a Point name only when the configuration is built.
     */
    private final Map<TileKey, List<TileKey>> blockedPoints = new LinkedHashMap<>();

    /**
     * @param station the station's square
     * @return the squares that make it unavailable while occupied, in the order they were added
     */
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

    /**
     * Replaces the squares that hold a station back.
     *
     * @param station the station's square
     * @param blockers the squares to watch; empty or null clears the restriction
     */
    public void setBlockingPoints(TileKey station, List<TileKey> blockers)
    {
        if (station == null) return;

        List<TileKey> keys = new ArrayList<>();

        if (blockers != null)
        {
            for (TileKey blocker : blockers)
            {
                // De-duplicated here rather than in the picker, and never the station itself: standing
                // at a station already decides whether it is free, so watching it from itself makes a
                // station nothing can be sent to rather than one that is restricted.
                if (blocker != null && !blocker.equals(station) && !keys.contains(blocker))
                {
                    keys.add(blocker);
                }
            }
        }

        if (keys.isEmpty()) blockedPoints.remove(station);
        else blockedPoints.put(station, keys);
    }

    /**
     * @return every station held back by something, against the squares watched for it
     */
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

        List<TileKey> keys = stationSignals.get(station);

        if (keys == null) return out;

        for (TileKey key : keys)
        {
            TileKey signal = key;

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

        List<TileKey> keys = new ArrayList<>();

        if (signals != null)
        {
            for (TileKey signal : signals)
            {
                // De-duplicated here rather than in the picker, so that nothing else which writes this
                // - an import, a restored snapshot - can leave one signal in the list twice
                if (signal != null && !keys.contains(signal)) keys.add(signal);
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

    /**
     * @return every station that has protecting signals, against those signals' squares
     */
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
    private final Map<TileKey, TileKey> captions = new LinkedHashMap<>();
    private final Map<TileKey, String> linkNames = new LinkedHashMap<>();
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
     * Entries for pages the index cannot resolve, kept out of memory and written back verbatim.
     *
     * The one thing the boundary could not previously know (OB-067). A key is "page:x,y" and both
     * halves of the translation are string lookups, so the page part has to be in the world its
     * lookup expects - a NAME going out, an ID coming in. Nothing recorded which world it was in, so
     * the code rested on "ids are numeric and names are not, and therefore never collide". They do:
     * `validateLayoutName` allows digits, so a page may legally be called "2", and Adam ruled it must
     * stay legal - "A page should be allowed to be named 2 - let FR-013 dissolve it."
     *
     * On the normal path there was never a fault: keys arrive in id form and are translated to names
     * once, so each lookup is asked about the kind of string it holds. The exposed path is the
     * on-disk repair added for OB-062, which loads WITHOUT numbering - every key stays in id form in
     * memory, and writing it back then ran an id through the name map. "2:x,y" would have been
     * rewritten through the page NAMED "2" and a page's settings reattached to a different page,
     * which is the failure Adam has already lost data to twice.
     *
     * The first attempt at this made `toStored` leave such keys alone on the way out, and a test
     * written for it showed why that is not enough. The damage does not need a save: an entry that
     * kept the file's id as its page part is ALREADY indistinguishable, in memory, from an entry
     * belonging to a page genuinely called that. `getPointName(new TileKey("1", 3, 3))` returned the
     * absent page 1's station, because "1" is what both of them look like. The pun is in the
     * representation, so no amount of care at the boundary can unmake it.
     *
     * Hence holding rather than resolving. Nothing whose page is unknown is put into the live
     * collections at all; it waits here, as the exact JSON it arrived as, and is merged back on save.
     * Every key in memory is therefore a page NAME of a page that is loaded - which is the invariant
     * the code has always assumed and never had.
     *
     * When the page comes back - a OneDrive placeholder that hydrates, a file the sync client had
     * open - its id resolves, nothing is held, and the entries load normally. Nothing is lost by
     * being away.
     *
     * Field name -> the JSONObject or JSONArray of entries held for it.
     */
    private final Map<String, Object> heldForAbsentPages = new LinkedHashMap<>();

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
     * The highest page id this setup has ever recorded, live or absent.
     *
     * IAR-A1.  `writeLayoutIndex` issues a new page the number above the highest one in the index
     * FILE, which means a retired id is accounted for exactly once - by the write that drops it. The
     * write after that reuses it, and the settings of the page it belonged to are still here, held
     * under that number because its file was merely absent rather than deleted (OB-067). The new page
     * collects a stranger's stations, lengths and exclusions, and nothing reports a renumber, because
     * as far as the index is concerned nothing was renumbered.
     *
     * The index cannot remember this by itself without a new field, and `gleisbild.cs2` is a file real
     * Maerklin hardware reads. This map can: it is the setup's record of what every id was called when
     * the file was written, and it deliberately keeps the ids of pages that are not loaded.
     *
     * A page id is written as a decimal number; anything that is not one is skipped rather than
     * guessed at.
     *
     * @return the highest id known, or 0 when nothing is
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
     * Whether this setup knows about a page that is not among the ones it is being shown.
     *
     * The setup records what each page id was called when it was written.  If one of those names is
     * missing from the pages a caller is holding, then the caller's picture of the layout is
     * incomplete - and anything that deletes on the strength of "this square does not exist" is about
     * to delete a page's worth of settings for a page that still exists.
     *
     * That is not hypothetical.  CS2File deliberately skips a page whose file will not parse or is not
     * there, and says so; on this layout, which lives in OneDrive, an unhydrated placeholder or a file
     * held by the sync client is enough.  readShared is relaxed about the same absence for the same
     * reason - "Absent is fine - the page may simply not be loaded" - so the two halves agreed that a
     * missing page is survivable, and then reconcile deleted its contents anyway.
     *
     * Found by review (OB-068).  The remedy is the one already written for a suspect numbering: save,
     * but do not prune.
     *
     * @param loaded the page names the caller actually has
     * @return the names this setup knows that are not in that set, empty when the picture is complete
     */
    public java.util.List<String> pagesNotLoaded(java.util.Collection<String> loaded)
    {
        java.util.List<String> missing = new ArrayList<>();

        if (loaded == null) return missing;

        for (String name : pageNamesWhenWritten.values())
        {
            if (name != null && !loaded.contains(name)) missing.add(name);
        }

        return missing;
    }

    /**
     * Drops everything being held for pages the operator has said are gone for good (FR-018).
     *
     * The half of FR-018 that none of my three proposed options touched. Entries belonging to a page
     * the index cannot resolve are held out of memory and written back verbatim (OB-067), which is
     * what makes a page's absence survivable - a OneDrive placeholder hydrates, the file comes back,
     * and nothing was lost by being away. The cost is that a page which was genuinely DELETED is held
     * on exactly the same terms, for ever, under an id that can never attach to anything again.
     *
     * Adam: "if we are talking about orphaned data, why not warn the user and then prune?"
     *
     * So this is only ever called with an answer from the person who knows - never inferred, never on
     * a timer, and never from the startup path. The application cannot tell a deleted page from an
     * unhydrated one and must not try.
     *
     * **An entry is dropped if it names a gone page ANYWHERE, key or value, not only if it is
     * anchored on one.** A caption on a page that is still here, pointing at a station on a page that
     * has been deleted, is a pointer to nothing. Leaving it held would be the leak this method exists
     * to close; releasing it into memory would be worse, because the page part of its value would then
     * stand in the live collections as a page NAME - the id-as-name pun the hold exists to prevent.
     *
     * The recorded name is dropped with it, so `pagesNotLoaded` stops naming the page and the operator
     * is not asked about it again on the next save.
     *
     * @param goneByName the page names the operator has said are deleted rather than merely absent
     * @return how many held entries were dropped
     */
    public int forgetHeldPages(java.util.Collection<String> goneByName)
    {
        if (goneByName == null || goneByName.isEmpty()) return 0;

        // The page PARTS a stored key could carry for these pages: the ids the file recorded them
        // under, and the names themselves for a setup old enough to be keyed by name.
        Set<String> parts = new LinkedHashSet<>(goneByName);

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

        // And the record of what those ids were called, so the page stops being reported as one that
        // is merely not loaded.  Written as a removal by value rather than by key because the caller
        // knows names and the map is keyed by id.
        pageNamesWhenWritten.values().removeAll(goneByName);

        return dropped;
    }

    /**
     * Whether a stored key, or a value that may itself be one or a list of them, names a gone page.
     *
     * @param held a key, a stored square, an array of them, or a value that is none of those
     * @param parts the page parts being removed - ids and, for an old enough file, names
     * @return true when any square in it belongs to one of those pages
     */
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
        return pointNames.get(tile);
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

        for (TileKey key : pointNames.keySet())
        {
            TileKey tile = key;

            if (tile != null) out.add(tile);
        }

        return out;
    }

    /**
     * Every square that has been given a name, printed.
     *
     * Kept in the PRINTED form (FR-013). The store holds squares now, but this is a public accessor
     * whose callers key their own maps by the same strings, and Adam's rule for this work was "string
     * keys only matter at import/export" - a display accessor is the nearest thing to an export.
     * Converting it would ripple into callers that have no reason to change.
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
     * @param tile
     * @return the length assigned to this tile, 0 if none
     */
    public int getTileLength(TileKey tile)
    {
        Integer value = tileLengths.get(tile);

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
            tileLengths.remove(tile);
        }
        else
        {
            tileLengths.put(tile, length);
        }
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

    /**
     * Links autonomy is to ignore, by tile.
     *
     * Stored with the track rather than with a configuration: whether a link is part of the railway
     * autonomy runs is a fact about the diagram, and it would be strange for one configuration to see
     * a hole in the track that another does not.
     */
    private final Set<TileKey> disabledPortals = new LinkedHashSet<>();

    public boolean isPortalDisabled(TileKey tile)
    {
        if (tile == null) return false;

        if (disabledPortals.contains(tile)) return true;

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

        return partner != null && disabledPortals.contains(partner);
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

        portals.put(a, b);
        portals.put(b, a);
    }

    public void unpairPortal(TileKey tile)
    {
        TileKey partner = portals.remove(tile);

        if (partner != null) portals.remove(partner);
    }

    /**
     * Repairs a setup that nothing has open, in place on disk.
     *
     * A locomotive rename has to reach three places: the database, the setup in memory, and the setup
     * on disk.  When a session is open the second and third are the same act.  When one is NOT open,
     * the window used to do nothing at all and say why: "the file it would repair is read the next time
     * it IS opened - by which time this rename is already in the locomotive database."
     *
     * That is wrong, and OB-062 is the finding.  Nothing repairs locomotive names at load: the file is
     * read as it stands, so the old name survives in the placement, the home and the exclusions until
     * somebody chooses that configuration - at which point parseAuto answers a locomotive it cannot
     * resolve by invalidating the whole layout, days later, with nothing connecting it to the rename.
     *
     * The reason the window gave for standing back was sound about the SESSION and not about the file.
     * Building a session opens every page, runs the caption migration, can raise a dialog and then
     * writes a setup.json - so renaming a locomotive on a layout where autonomy has never been touched
     * would fabricate a setup out of nothing.  A bare store does none of that: it reads one file, and
     * only if that file is already there.
     *
     * @param layoutFolder the layout folder, the one holding config/autonomy
     * @param from the name as it was
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

    /**
     * Carries a page rename into a setup nothing has open.
     *
     * The window renames the page in the store when a session is built.  When one is NOT built it used
     * to call the LAZY getter, which builds a session - opening every page, running the caption
     * migration, and then saving, which creates a setup.json.  So renaming a page on a layout where
     * autonomy had never been touched invented one.
     *
     * @param layoutFolder the layout folder
     * @param from the page name as it was
     * @param to the new page name
     * @return true when a setup was found and rewritten
     * @throws IOException if the setup exists but cannot be read or written
     */
    public static boolean renamePageOnDisk(File layoutFolder, String from, String to)
        throws IOException
    {
        if (from == null || to == null) return false;

        return repairOnDisk(layoutFolder, store -> store.renamePage(from, to));
    }

    /**
     * Forgets a deleted page in a setup nothing has open.  See renamePageOnDisk for why.
     *
     * @param layoutFolder the layout folder
     * @param page the page being deleted
     * @return true when a setup was found and rewritten
     * @throws IOException if the setup exists but cannot be read or written
     */
    public static boolean deletePageOnDisk(File layoutFolder, String page) throws IOException
    {
        if (page == null) return false;

        return repairOnDisk(layoutFolder, store -> store.deletePage(page));
    }

    /**
     * Something that can be done to a store.
     */
    private interface Repair
    {
        void apply(AutonomyCompanionStore store);
    }

    /**
     * Opens a setup that nothing has open, changes one thing about it, and puts it back.
     *
     * @param layoutFolder the layout folder, the one holding config/autonomy
     * @param what the change to make
     * @return true when a setup was found and rewritten, false when there was nothing to repair
     * @throws IOException if the setup exists but cannot be read or written
     */
    private static boolean repairOnDisk(File layoutFolder, Repair what) throws IOException
    {
        if (layoutFolder == null) return false;

        AutonomyCompanionStore store = new AutonomyCompanionStore(layoutFolder);

        // Nothing is created for a layout that has never had a setup.  exists() asks whether the FILE
        // is there, which is the same question the session path asks before it writes.
        if (!store.exists()) return false;

        store.load();

        // Give it back the numbering the FILE was written under, before anything is saved.
        //
        // Nobody calls setPageIds on a bare store - there is no session to tell it what the pages are
        // called - so pageIdToName is empty, and sharedFields() writes "pages" from exactly that map.
        // Saving would therefore replace the file's record of what each id was called with {}.
        //
        // That record is the only evidence a page renumber ever happened: readShared compares it
        // against the current index to tell a rename from a renumber, and pageOf resolves every stored
        // id through it. Blanking it is the same data loss this class was repaired for two commits ago,
        // arriving by a new door - a locomotive rename would quietly disarm the detection for the whole
        // setup.
        //
        // Taking it from what was just read means save() writes the file back with the same page
        // record and the same keys it came in with. The only thing this method changes is the
        // locomotive name.
        Map<String, String> nameToId = new LinkedHashMap<>();

        for (Map.Entry<String, String> page : store.pageNamesWhenWritten.entrySet())
        {
            nameToId.put(page.getValue(), page.getKey());
        }

        store.setPageIds(nameToId);

        // Read AGAIN, now that it knows what the pages are called.
        //
        // The first read had no numbering to work with, so every key came back in ID form - which is
        // harmless for a locomotive rename, because that changes names INSIDE configurations and never
        // touches a tile key.  It is not harmless for renamePage or deletePage: both work on page
        // NAMES, and against id-form keys they would match nothing and silently do nothing at all.
        //
        // The second read costs one file. Both directions then use the same map, so the keys written
        // back are the keys that came in - which testRepairingOnDiskChangesOnlyTheLocomotive pins.
        store.load();

        what.apply(store);

        store.save();

        return true;
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
        if (configuration == null) return;

        if (configuration.has("points"))
        {
            repairLocomotiveInPoints(configuration.getJSONObject("points"), from, to);
        }

        repairLocomotiveInTimetable(configuration, from, to);
    }

    /**
     * The fourth holder of a locomotive's name, and the one nothing reached.
     *
     * The note above this method used to enumerate three - the placement, the home assignment and the
     * exclusion list - and all three are inside "points". The captured timetable is not: it rides in
     * "globals", because AutonomySession copies every top-level key across and Layout.toJSON puts the
     * timetable there. Every entry names its locomotive.
     *
     * Left unrepaired, a rename left an entry naming a locomotive that no longer exists. Until this
     * commit that cost the whole timetable on the next load, because the loader took one exception as a
     * reason to discard all of them; it now costs that entry. Either way the fix is to carry the rename
     * across, so it costs nothing.
     *
     * A DELETE removes the entry outright: a timetable leg for a locomotive that is gone is not a leg
     * anybody can run, and leaving it would be leaving the loader to drop it on every load for ever.
     *
     * Found by review (OB-069). The entries also name POINTS, which a station rename breaks in the same
     * way - that half is not repaired here, and is survivable now only because the loader drops the one
     * entry rather than the list.
     *
     * @param configuration the configuration to repair, modified in place
     * @param from the locomotive's name as it was
     * @param to the new name, or null when the locomotive is being deleted
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
                // Gone, so the leg is gone with it
                if (to == null) continue;

                entry.put(AutonomyBuilder.LOCOMOTIVE, to);
            }

            kept.put(entry);
        }

        globals.put("timetable", kept);
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

        // And what the file already knew about ids that are NOT in the index right now.
        //
        // A page whose file is missing has its entries held rather than loaded (heldForAbsentPages),
        // and this is the other half of not losing anything while it is away: without it, one save
        // during the absence drops the record of what that id was called, which is the only evidence
        // a renumber can be told from a rename by. The entries would survive and the means of
        // recognising them would not.
        //
        // Never over a live one - an id the index has now is authoritative, and this map is by
        // definition older.
        for (Map.Entry<String, String> entry : pageNamesWhenWritten.entrySet())
        {
            if (!pages.containsKey(entry.getKey())) pages.put(entry.getKey(), entry.getValue());
        }

        root.put("pages", new JSONObject(pages));

        root.put("pointNames", new JSONObject(translateKeys(pointNames)));
        root.put("stations", new JSONArray(translateSet(stations)));
        root.put("tileLengths", new JSONObject(translateLengths()));
        root.put("tileDirections", new JSONObject(translateKeys(tileDirections)));
        root.put("barredArrivals", new JSONObject(translateKeys(barredArrivals)));
        root.put("stationSignals", new JSONObject(translateTileListMap(stationSignals)));
        root.put("blockedPoints", new JSONObject(translateTileListMap(blockedPoints)));
        root.put("portals", new JSONObject(translatePortals()));
        root.put("captions", new JSONObject(translateTileMap(captions)));
        root.put("linkNames", new JSONObject(translateKeys(linkNames)));
        // By page ID, like the other nine.  This was the one collection written raw, and it broke the
        // rule setPageIds states: a rename orphaned it, so an excluded page silently rejoined autonomy
        // and its old name sat in the set for ever because nothing prunes it.
        root.put("excludedPages", new JSONArray(translatePages(excludedPages)));
        root.put("disabledLinks", new JSONArray(translateSet(disabledPortals)));

        // Whatever was held back because its page is not loaded goes back in exactly as it came
        // (OB-067).  Without this, one save while a page's file is missing deletes that page's whole
        // setup - the same loss the page-id work was done for, arriving through absence rather than
        // through a rename.
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
        heldForAbsentPages.clear();
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
        Set<TileKey> renamedPortals = new LinkedHashSet<>();

        for (TileKey key : disabledPortals)
        {
            renamedPortals.add(rekeyOne(key, from, to));
        }

        disabledPortals.clear();
        disabledPortals.addAll(renamedPortals);

        Set<TileKey> renamedStations = new LinkedHashSet<>();

        for (TileKey key : stations)
        {
            renamedStations.add(rekeyOne(key, from, to));
        }

        stations.clear();
        stations.addAll(renamedStations);

        if (excludedPages.remove(from)) excludedPages.add(to);

        // AND the numbering, which this did not touch and which every translation asks (OB-092).
        //
        // Adam: "When I renamed '5 - Test' to 5, the main page (1 - Main, id 5) became excluded from
        // autonomy and lost all its train placement."
        //
        // Everything above rekeys a collection. None of it told the store that the page it knows as
        // `from` now answers to `to`, so `pageNameToId` still held the old name - and the next save
        // asked it about the new one and got nothing. `translatePages` then wrote the bare NAME into
        // excludedPages, because that is its fallback for a page it cannot find an id for, and
        // `untranslatePages` reads every value there as an ID. A page called "5" came back as
        // whichever page holds id 5.
        //
        // The exclusion is the visible half. The rest follows from it: an excluded page is not in the
        // graph, so every placement on it goes.
        //
        // A rename does not change any id - that is what ids are for - so this moves the name and
        // leaves the number alone.
        String renamedId = pageNameToId.remove(from);

        if (renamedId != null)
        {
            pageNameToId.put(to, renamedId);
            pageIdToName.put(renamedId, to);
        }

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
     * Forgets everything about a page that has been deleted.
     *
     * renamePage's counterpart, and it had none: deleting a page removed the file and rewrote the
     * index, and nothing told the setup.  What was left behind was worse than an orphan.  Page ids come
     * from the index, so removing a page used to renumber every page after it - and on the next load
     * the deleted page's entries resolved through pageOf, whose recorded name is now gone from the
     * index, so they were handed to whatever page had inherited that id.  Where the two pages shared
     * coordinates, one page's names and stations landed on the other's track; the rest was pruned by
     * the following reconcile and written back.  And because the old NAME no longer existed anywhere,
     * the renumber could not even be detected - readShared's test is whether that name still exists,
     * so isPageNumberingSuspect stayed false and the guard in AutonomySession.save never engaged.
     *
     * Ids are durable now (see LayoutDiagram.writeLayoutIndex), so the inheriting-page half of that is
     * gone: a deleted page's id is retired rather than handed on.  This is the other half - the page's
     * own settings, which are about track that no longer exists and which nothing else will ever claim.
     *
     * Gathered here and removed by forgetSquares, which is the method that already knows how to take a
     * square out of all eleven collections AND out of everything that NAMES one - a caption pointing at
     * a station on this page, a protecting signal, the far end of a portal.  Those are on OTHER pages
     * and would otherwise be left pointing at nothing.
     *
     * @param page the page being deleted
     * @return the number of squares whose setup was forgotten
     */
    public int deletePage(String page)
    {
        if (page == null) return 0;

        Set<TileKey> squares = new LinkedHashSet<>();

        // Every collection keyed by square.  Named individually rather than gathered by a helper so
        // that testStoreCollectionsAreHandledEverywhere governs this method too - a collection added
        // later has to be added here, and the test says so before anybody notices in the field.
        for (TileKey key : new LinkedHashSet<>(pointNames.keySet())) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(tileLengths.keySet())) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(barredArrivals.keySet())) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(linkNames.keySet())) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(stationSignals.keySet())) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(blockedPoints.keySet())) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(portals.keySet())) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(captions.keySet())) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(stations)) if (isOnPage(key, page)) squares.add(key);
        for (TileKey key : new LinkedHashSet<>(disabledPortals)) if (isOnPage(key, page)) squares.add(key);

        // tileDirections is keyed by the square AND a route across it, so the square has to be asked
        // for rather than assumed - this list is of bare squares.  Since FR-013 stage two that is one
        // method call rather than a string split; it was the same distinction the eleventh member of
        // forgetSquares' own list got wrong.
        for (DirectionKey key : new LinkedHashSet<>(tileDirections.keySet()))
        {
            TileKey bare = key.square();

            if (bare != null && isOnPage(bare, page)) squares.add(bare);
        }

        // And squares on this page that only ever appear as a VALUE.
        //
        // A protecting signal, a blocker or the far end of a portal can sit on this page while having
        // no entry of its own - nothing is recorded ABOUT that square, it is only pointed AT. Gathering
        // by key alone missed those, so the pointer survived the page and dangled until the next
        // reconcile happened to tidy it.
        //
        // renamePage handles the value half explicitly, by rekeying values as well as keys. This is
        // the same half, and this method's own contract already claimed to cover it. Found by review.
        for (List<TileKey> signals : stationSignals.values())
        {
            for (TileKey signal : signals) if (isOnPage(signal, page)) squares.add(signal);
        }

        for (List<TileKey> blockers : blockedPoints.values())
        {
            for (TileKey blocker : blockers) if (isOnPage(blocker, page)) squares.add(blocker);
        }

        for (TileKey far : portals.values()) if (isOnPage(far, page)) squares.add(far);

        for (TileKey station : captions.values()) if (isOnPage(station, page)) squares.add(station);

        forgetSquares(squares);

        // Keyed by PAGE rather than by square, so forgetSquares has nothing to say about it - and a
        // page that is gone cannot be excluded from autonomy.  Left behind, the name sits in the set
        // for ever, and a page later created with the same name would silently start out excluded.
        excludedPages.remove(page);

        // And the configurations, which key by square too.  renamePage's note records what happens
        // when they are missed: "a rename silently dropped every placement, home, terminus and length
        // in every configuration while the shared file survived, making the loss look arbitrary".
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
        Set<TileKey> landing = new LinkedHashSet<>();

        for (TileKey to : byKey.values())
        {
            if (!byKey.containsKey(to)) landing.add(to);
        }

        // And the squares the caller says were built over by something other than a move: a bulk
        // column edit clears the whole destination line, including the squares whose source was blank.
        for (TileKey over : asKeys(builtOver))
        {
            if (!byKey.containsKey(over)) landing.add(over);
        }

        // Sparing the labels of the tiles that are arriving - see forgetSquares.  A platform whose
        // name is written on the square below it, nudged down one, lands ON its own label.
        forgetSquares(landing, byKey);

        if (byKey.isEmpty()) return;

        moveKeys(pointNames, byKey);
        moveKeys(tileLengths, byKey);
        // A direction belongs to a square AND a route across it, and for as long as that was written
        // as a string this line could not be here at all: moveKeys matched whole keys, so it never
        // matched one of these, and a moved tile left every facing behind on the square the track had
        // walked away from - dropped by the next reconcile, which did know about the suffix.  That is
        // the same loss moveTiles exists to prevent, hiding behind a key shape.
        //
        // It took a second map of the moves in string form to fix, kept in step with this one by
        // being rebuilt from it.  Since FR-013 stage two there is one map of moves and one method,
        // because moveKeys asks a key which square it is on rather than assuming the key IS one.
        moveKeys(tileDirections, byKey);
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
            // The configuration's keys are the PRINTED form - it is JSON - so each is parsed before
            // being asked about (FR-013).
            //
            // `byKey.containsKey(aString)` compiles, because containsKey takes Object, and is always
            // false. That is the whole defect class this conversion is against, and it was reproduced
            // here within an hour of doing the conversion: the settings matrix caught it as "what a
            // configuration says about the square did not travel with the tile".
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

        putBack(pointNames, page, (Map<TileKey, String>) snapshot.get("pointNames"));
        putBack(tileLengths, page, (Map<TileKey, Integer>) snapshot.get("tileLengths"));
        putBack(tileDirections, page, (Map<DirectionKey, String>) snapshot.get("tileDirections"));
        putBack(barredArrivals, page, (Map<TileKey, String>) snapshot.get("barredArrivals"));
        putBack(linkNames, page, (Map<TileKey, String>) snapshot.get("linkNames"));
        putBack(stationSignals, page, copyLists((Map<TileKey, List<TileKey>>) snapshot.get("stationSignals")));
        putBack(blockedPoints, page, copyLists((Map<TileKey, List<TileKey>>) snapshot.get("blockedPoints")));
        putBack(portals, page, (Map<TileKey, TileKey>) snapshot.get("portals"));
        putBack(captions, page, (Map<TileKey, TileKey>) snapshot.get("captions"));

        putMembersBack(stations, page, (Set<TileKey>) snapshot.get("stations"));
        putMembersBack(disabledPortals, page, (Set<TileKey>) snapshot.get("disabledPortals"));

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
     * Whether a square is on a page (FR-013).
     *
     * One field comparison. The string form of this parsed the key to find its page part, which is
     * where OB-071 lived - it split on the first colon while four other sites split on the last, so
     * every square on a page whose name contained one was read as belonging to a different page.
     *
     * There is nothing here to get wrong any more, which is the point of the conversion.
     *
     * @param key the square
     * @param page the page name
     * @return whether it is on that page
     */
    private static boolean isOnPage(TileKey key, String page)
    {
        return key != null && key.getPage().equals(page);
    }

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
     * The entries of one collection that live on one page.
     *
     * Generic over {@link SquareKeyed} rather than over TileKey (FR-013 stage two), so the one
     * collection keyed by a square PLUS a route goes through the same method as the other ten. It used
     * to have a copy of its own - four of them, in fact - because erasure makes {@code Map<String, T>}
     * and {@code Map<TileKey, T>} the same signature and they could not be overloaded.
     *
     * @param <K> what the collection is keyed by
     * @param <T> what it holds
     * @param from the collection
     * @param page the page wanted
     * @return a new map of just that page's entries
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

    private static Set<TileKey> membersOnPage(Set<TileKey> from, String page)
    {
        Set<TileKey> out = new LinkedHashSet<>();

        for (TileKey key : from)
        {
            if (isOnPage(key, page)) out.add(key);
        }

        return out;
    }

    /**
     * Replaces one page's entries with what a snapshot remembered of them.
     *
     * @param <K> what the collection is keyed by
     * @param <T> what it holds
     * @param into the collection
     * @param page the page being restored
     * @param was what that page held, or null to leave it empty
     */
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
     * Lets go of everything the setup holds about a set of squares.
     *
     * Both halves: what is written ABOUT the square, and what elsewhere POINTS AT it.  A caption
     * naming a station that has just been built over names nothing, and a pairing to a signal that
     * has been built over would throw an accessory that is not there any more.
     *
     * @param squares stored keys, "page:x,y"
     */
    private void forgetSquares(Set<TileKey> squares)
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
    private void forgetSquares(Set<TileKey> squares, Map<TileKey, TileKey> arriving)
    {
        if (squares == null || squares.isEmpty()) return;

        for (TileKey key : squares)
        {
            pointNames.remove(key);
            tileLengths.remove(key);
            barredArrivals.remove(key);
            linkNames.remove(key);
            stationSignals.remove(key);
            blockedPoints.remove(key);
            portals.remove(key);

            TileKey names = captions.get(key);

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
        for (java.util.Iterator<DirectionKey> keys = tileDirections.keySet().iterator();
            keys.hasNext();)
        {
            // The square is asked for rather than picked out of a string (FR-013 stage two).  The
            // previous version split the key on a '#' and parsed the front of it, and the version
            // before THAT did not exist at all: the list above carried a `tileDirections.remove(key)`
            // as its eleventh member, written because everything else was there and dead from the day
            // it was written, because a bare square never matches a suffixed key (DD-A1).
            //
            // Set.contains takes Object, so handing it the string form compiled and answered false for
            // ever - which is the shape of defect a typed key removes rather than merely discourages.
            if (squares.contains(keys.next().square())) keys.remove();
        }

        // And what named them
        for (java.util.Iterator<Map.Entry<TileKey, TileKey>> pairs = portals.entrySet().iterator();
            pairs.hasNext();)
        {
            if (squares.contains(pairs.next().getValue())) pairs.remove();
        }

        // A caption elsewhere naming one of these squares named track that is gone.
        //
        // No exception for the arriving tiles, and none is possible: a square that is being vacated is
        // never in this set - moveTiles builds it by excluding them - so a caption naming one of those
        // is not looked at here at all.  It is repointed afterwards instead.
        for (java.util.Iterator<Map.Entry<TileKey, TileKey>> pairs = captions.entrySet().iterator();
            pairs.hasNext();)
        {
            if (squares.contains(pairs.next().getValue())) pairs.remove();
        }

        for (java.util.Iterator<Map.Entry<TileKey, List<TileKey>>> pairs
            = stationSignals.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<TileKey, List<TileKey>> pair = pairs.next();

            pair.getValue().removeAll(squares);

            if (pair.getValue().isEmpty()) pairs.remove();
        }

        // The same for the squares a station is held back by: a restriction naming track that has been
        // built over is one nothing can satisfy or clear.
        for (java.util.Iterator<Map.Entry<TileKey, List<TileKey>>> pairs
            = blockedPoints.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<TileKey, List<TileKey>> pair = pairs.next();

            pair.getValue().removeAll(squares);

            if (pair.getValue().isEmpty()) pairs.remove();
        }

        // The configurations key by square as well - facings, placements, homes, lengths
        for (JSONObject configuration : configurations.values())
        {
            if (!configuration.has("points")) continue;

            JSONObject points = configuration.getJSONObject("points");

            for (TileKey key : squares)
            {
                points.remove(key.toString());
            }
        }
    }

    /**
     * Follows squares that have been dragged to new coordinates, moved keys winning over ones that
     * merely stayed.
     *
     * The square is the part that moves; for a key that is a square plus something else, that
     * something else identifies which of the square's entries this is and travels unchanged. That
     * used to be a separate method operating on strings, splitting the key on a '#' - see FR-013
     * stage two.
     *
     * Two passes rather than one, and the order matters: an entry moving ONTO a square another entry
     * is moving off must not be overwritten by the one leaving.
     *
     * @param <K> what the collection is keyed by
     * @param <T> what it holds
     * @param map the collection
     * @param moves where each moved square went
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

    /**
     * The same for a map whose values are LISTS of squares.
     *
     * Separately named rather than overloaded: erasure makes both signatures the same method, so a
     * list-valued copy of any of these helpers needs its own name whether or not that reads better.
     */
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

    /**
     * Repoints values that name a square that moved.  In place: the entry stays where it is.
     */
    private static void moveValues(Map<TileKey, TileKey> map, Map<TileKey, TileKey> moves)
    {
        for (Map.Entry<TileKey, TileKey> entry : map.entrySet())
        {
            TileKey moved = moves.get(entry.getValue());

            if (moved != null) entry.setValue(moved);
        }
    }

    /**
     * The same for a set of squares.
     */
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
        private final List<String> declinedBecauseAbsent = new ArrayList<>();

        /**
         * A reconciliation that did not happen, and the pages that stopped it (DR-B10).
         *
         * The no-argument constructor above says "nothing was reconciled" and says nothing about why,
         * so a caller holding one cannot tell a clean layout from a layout it was refused permission
         * to tidy. Those are opposite situations: the first needs no message and the second is the one
         * moment somebody could put the missing page back before its id is retired.
         *
         * @param absent the pages the setup knows about that are not loaded, possibly empty when the
         *        refusal was a suspect numbering instead
         * @return a reconciliation that reports the refusal
         */
        public static Reconciliation declined(List<String> absent)
        {
            Reconciliation out = new Reconciliation();

            if (absent != null) out.declinedBecauseAbsent.addAll(absent);

            return out;
        }

        /**
         * Pages that were not loaded when a save declined to reconcile.
         *
         * @return their names, empty when nothing was declined - or when the refusal was a suspect
         *         numbering rather than a missing page
         */
        public List<String> getDeclinedBecauseAbsent()
        {
            return Collections.unmodifiableList(this.declinedBecauseAbsent);
        }

        /**
         * Whether this reconciliation was refused rather than simply finding nothing.
         *
         * @return true when the setup was not judged at all
         */
        public boolean wasDeclined()
        {
            return !this.declinedBecauseAbsent.isEmpty();
        }

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

        Set<TileKey> keys = new LinkedHashSet<>();

        for (TileKey tile : existing)
        {
            keys.add(tile);
        }

        report.droppedTileProperties.addAll(asStrings(dropMissing(tileLengths, keys)));
        report.droppedTileProperties.addAll(asStrings(dropMissing(tileDirections, keys)));
        report.droppedTileProperties.addAll(asStrings(dropMissing(barredArrivals, keys)));
        // dropMissing tests the KEY - the station's square.  The VALUES are squares too, and this was
        // the one square-referencing collection whose values reconcile never looked at: portals are
        // checked on both ends, and a caption's two ends inside reconcileCaptions.  forgetSquares covers a signal
        // square that is BUILT OVER, so what was left was the plain deletion - and the pairing then
        // survived every save, ready to be INHERITED by whatever is drawn at those coordinates next.
        // That is the defect the linkNames drop below was written for, applied to the one collection
        // that commands real hardware: autonomy would start throwing an accessory nobody paired.
        report.droppedTileProperties.addAll(asStrings(dropMissing(stationSignals, keys)));
        report.droppedTileProperties.addAll(asStrings(dropMissing(blockedPoints, keys)));

        for (java.util.Iterator<Map.Entry<TileKey, List<TileKey>>> pairs
            = stationSignals.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<TileKey, List<TileKey>> pair = pairs.next();

            // A NEW list rather than removing from the one held: a page snapshot may be holding it for
            // the editor's undo, and editing it in place is how undo came to restore the deletion.
            List<TileKey> kept = new ArrayList<>();

            for (TileKey signal : pair.getValue())
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
        for (java.util.Iterator<Map.Entry<TileKey, List<TileKey>>> pairs
            = blockedPoints.entrySet().iterator(); pairs.hasNext();)
        {
            Map.Entry<TileKey, List<TileKey>> pair = pairs.next();

            List<TileKey> kept = new ArrayList<>();

            for (TileKey blocker : pair.getValue())
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
        report.droppedTileProperties.addAll(asStrings(dropMissing(linkNames, keys)));

        for (TileKey key : dropMissingMembers(disabledPortals, keys))
        {
            report.droppedTileProperties.add("link switched off at " + key);
        }

        // A caption goes when either end of it does - the square it is drawn on, or the sensor it is
        // about.  Text pointing at track that no longer exists is the orphan this whole change removes.
        report.droppedTileProperties.addAll(reconcileCaptions(keys));

        List<TileKey> goneTiles = new ArrayList<>();

        for (TileKey key : pointNames.keySet())
        {
            if (!keys.contains(key)) goneTiles.add(key);
        }

        // Squares, not their printed form (FR-013).
        //
        // This gathered `key.toString()` and then asked `pointNames.get(aString)` and
        // `stations.remove(aString)`. Both compile - Map.get and Set.remove take Object - and both do
        // nothing at all, so a name whose square had gone was neither forgotten nor reported. The
        // report is printed at the point it is written instead, which is the only place the printed
        // form is wanted.
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
        List<TileKey> orphanStations = new ArrayList<>();

        for (TileKey key : stations)
        {
            if (!keys.contains(key) && !pointNames.containsKey(key)) orphanStations.add(key);
        }

        // Squares here too, for the same reason as goneTiles above: `stations.remove(aString)` is a
        // no-op that compiles.
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
     * What a held field's values are made of, which is the only thing the hold needs to know.
     *
     * PLAIN is keyed by a square with a value that is nobody's business here - a name, a length, a set
     * of barred sides.  The rest name squares as well, and an entry is held when ANY square it names
     * belongs to a page that is not loaded: a caption points at a station, a portal is paired with
     * another square, and a station's protecting signals are squares too.
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
     * Every shared field that can be held for an absent page, and what its values are made of.
     *
     * ONE list (DR-A1). This was three: four shape-classified arrays inside `withoutAbsentPages` and a
     * twelve-name array inside `sharedFields`, none of them governed by any test. A reviewer proved
     * what that costs by removing a single string - `"blockedPoints"` from the merge array, nothing
     * else. All three ratchets stayed green, and one save while a page's file was missing silently
     * deleted that page's FR-001 restrictions from disk. That is the sentence OB-067 was closed with,
     * minus one string in one array.
     *
     * The other direction is as quiet: a field in the merge list but not the hold list comes back into
     * memory with a page ID standing where a page NAME belongs, which is the pun the whole mechanism
     * exists to remove.
     *
     * So the hold and the merge read the same map, and it cannot be half-updated. The store's history
     * is a list of lists that drifted - DD-A1 counted fourteen - and this is the one whose drift is
     * invisible until somebody's page is offline.
     */
    private static final Map<String, Held> HELD_FIELDS;

    static
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
        fields.put("blockedPoints", Held.SQUARE_LIST_VALUE);
        fields.put("stations", Held.SQUARE_LIST);
        fields.put("disabledLinks", Held.SQUARE_LIST);
        fields.put("excludedPages", Held.PAGE_LIST);

        HELD_FIELDS = java.util.Collections.unmodifiableMap(fields);
    }

    /**
     * The shared object with every entry naming an absent page taken out of it and held.
     *
     * Only when the numbering is known at all. A store loaded with no index - which is what the
     * on-disk repair path does on its first read - knows nothing about any page, and holding
     * everything would empty it. Nothing can be misresolved there either, because the name map is as
     * empty as the id map, so the keys stay as they are and are written back as they are.
     *
     * @param root the shared object as read, with "pages" already taken from it
     * @return a copy safe to read into the live collections
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

    /**
     * Moves the entries of one keyed field whose squares are not all here into the held set.
     *
     * @param root the copy being filtered, edited in place
     * @param field which field
     * @param valueIsASquare whether the value names a square as well
     * @param valueIsSquares whether the value is one square or an array of them
     */
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

    /**
     * The same for a field that is a bare list.
     *
     * @param wholePages true when the elements are page names rather than squares
     */
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
     * Puts the held entries of one field back into what is about to be written.
     *
     * Never over one that is live: a page that has come back is authoritative, and the held copy is
     * by definition older than anything the running application has done since.
     *
     * @param root the object being built for the file
     * @param field which field
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
     * @param wholeRoot the shared object as it came off disk.  The local `root` below is NOT this: it
     *     is the copy with absent pages filtered out, and the difference between the two names is
     *     load-bearing here (FBR-C5).
     */
    private void readShared(JSONObject wholeRoot)
    {
        // FIRST, because everything below is translated out of page ids and the translation needs to
        // know what those ids were called when this file was written.
        //
        // It used to be read after the tileLengths loop, which is the one collection translated inline
        // rather than by an untranslate* call below - so lengths were resolved against an empty map
        // while the other ten were resolved against a full one.  Harmless for as long as the
        // translation ignored this map, and wrong the moment it stopped ignoring it.
        readStringMap(wholeRoot, "pages", pageNamesWhenWritten);

        // Everything naming a page that is not loaded is taken out here and held, rather than read in
        // with the file's id standing in for a page name (OB-067). See heldForAbsentPages.
        JSONObject root = withoutAbsentPages(wholeRoot);

        // Read AND translated in one step, per field (FR-013).
        //
        // This was two passes: fill each collection with the FILE's keys, then walk eleven of them
        // rewriting the keys in place. The order of those two lists had to agree, by eye, for ever -
        // and the state between them was every collection holding stored keys in fields the rest of
        // the class reads as memory keys, which is the state OB-067 was about. Neither the agreement
        // nor the state exists now: a reader that does not translate cannot put anything into a
        // TileKey-keyed map.
        readSquareMap(root, "pointNames", pointNames);
        readSquareSet(root, "stations", stations);
        readSquareMap(root, "barredArrivals", barredArrivals);
        readSquareListMap(root, "stationSignals", stationSignals);
        readSquareListMap(root, "blockedPoints", blockedPoints);
        readSquarePairMap(root, "portals", portals);
        readSquarePairMap(root, "captions", captions);
        readSquareMap(root, "linkNames", linkNames);
        readSquareIntMap(root, "tileLengths", tileLengths);

        // Typed like the other ten since FR-013 stage two - it translates as it reads, the same as
        // readSquareMap, so there is no window in which the collection holds stored keys in a
        // memory-keyed field.  excludedPages is a set of page NAMES rather than of squares, so it is
        // still read raw and translated afterwards.
        readDirectionMap(root, "tileDirections", tileDirections);
        readStringSet(root, "excludedPages", excludedPages);
        readSquareSet(root, "disabledLinks", disabledPortals);

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

        // From the whole object, not the filtered copy: an unmodelled field is written back verbatim
        // and the filter has no business editing it.
        for (String key : wholeRoot.keySet())
        {
            if (!KNOWN_SHARED.contains(key)) unknownSharedFields.put(key, wholeRoot.get(key));
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
        heldForAbsentPages.clear();
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
     * A page added since the index was last read still round trips - by name, which is no worse than
     * before.
     *
     * This used to say "ids are numeric and names are not, so the two never collide", and that is not
     * true: `validateLayoutName` allows digits, so a page may legally be called "2", and a page whose
     * NAME equals another page's ID misroutes this translation and pageOf both. Nothing enforces the
     * invariant they rest on (OB-067).
     *
     * Adam ruled on it rather than have the name forbidden: "A page should be allowed to be named 2 -
     * let FR-013 dissolve it." FR-013 replaces these string keys with TileKey objects, at which point
     * an id and a name stop sharing a representation and the question cannot be asked. Until then this
     * is a known trap with nothing standing in it, and the on-disk repair path is the most exposed to
     * it - every key there is in id form, so this would rewrite "2:x,y" through the page NAMED "2".
     */
    private String toStored(String key)
    {
        // The LAST colon, as parseTileKey, isOnPage and rekeyOne all do (OB-071).
        //
        // A key is "page:x,y" and a page name may contain a colon - parseTileKey's own comment calls
        // "Yard: Upper" an ordinary thing to call a page. Splitting on the first colon read that name
        // as "Yard", so every square on "Yard: Upper" was stored under the id belonging to a DIFFERENT
        // page, and renaming that other page would orphan this one's entire setup: MT-135-class loss
        // triggered by renaming a page you were not touching.
        //
        // These two were the last sites still splitting on the first colon; the other three were fixed
        // when the hazard was found, and nobody came back for these. Found by review.
        int colon = key.lastIndexOf(':');

        if (colon < 0) return key;

        // Every key in memory now belongs to a page that is loaded - anything else was held back at
        // read time rather than let in wearing an id where a name goes (OB-067, see
        // heldForAbsentPages). So this map is the right map to ask, whatever the page is called.
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
     * The page a stored key names, or null when the index does not know it.
     *
     * The ONE place that answers "which page is this", asked two ways by the two methods below
     * (DR-B5). They used to ask it separately, branch for branch, kept in step by a comment on the
     * second saying it "has to agree with it exactly" - which is a comment doing a compiler's job,
     * and the shape of finding this codebase has paid for more than once.
     *
     * FR-018 is the change that would have split them: it alters what happens to a page whose file is
     * merely absent, and only one of the two would have been edited.
     *
     * @param stored the page part of a stored key - an id, or a name on a file old enough
     * @return the page's current name, or null when neither question finds it
     */
    private String resolvePage(String stored)
    {
        String whenWritten = pageNamesWhenWritten.get(stored);

        // RENUMBERED: the name the file recorded is still in the index, under whatever number.
        if (whenWritten != null && pageNameToId.containsKey(whenWritten)) return whenWritten;

        // RENAMED, or simply unmoved: the index knows this id.  This is the case page ids exist for,
        // and leaving it out held back the whole of a renamed page - which is the MT-135 loss, caused
        // by the mechanism written to prevent it.
        return pageIdToName.get(stored);
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
        String resolved = resolvePage(id);

        // The id itself when nothing resolves it, which is what every caller of this has always been
        // handed - and the reason pageIsHere exists, since an id is a legal page name and a caller
        // cannot tell that fallback from a success by looking at it.
        return resolved == null ? id : resolved;
    }

    /**
     * Whether a stored page part names a page that is actually here.
     *
     * The question `pageOf` cannot answer by its return value: it hands back the id when it fails,
     * and an id is a legal page name, so the caller cannot tell success from failure by looking.
     *
     * @param stored the page part of a stored key - an id, or a name on a file old enough
     * @return true when it resolves to a page in the current index
     */
    private boolean pageIsHere(String stored)
    {
        // Whether resolvePage answered at all, rather than the same two questions asked again.
        if (resolvePage(stored) != null) return true;

        // The file names this id and neither question found it: the page is genuinely not loaded.
        if (pageNamesWhenWritten.get(stored) != null) return false;

        // Otherwise it is a NAME, and an unambiguous one.  Files written before keys were stored by
        // id hold names, and a page added since the index was read has no id yet - both were always
        // meant to survive, and testAnExcludedPageWithNoIdKeepsItsName says so.  Nothing can be
        // confused here: no id in the index and none in the file's own record is this string, so
        // there is no second reading for it to be mistaken for.
        return true;
    }

    /**
     * Whether every square a stored entry names belongs to a page that is here.
     *
     * The VALUE side matters as much as the key side. A caption is a square pointing at a station, a
     * portal is a square paired with another, and a station's signals are squares too - so an entry
     * anchored on a loaded page can still name one that is absent, and half-translating it leaves the
     * same id-as-name in memory that this whole mechanism exists to prevent.
     *
     * @param parts the stored keys the entry is made of - its key, and any square it names
     * @return true when all of them are resolvable
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
     * A collection's keys in the form they go on disk - page ID rather than page name.
     *
     * Generic over {@link SquareKeyed} since FR-013 stage two, which is what let
     * `translateSuffixedKeys` be deleted: it did exactly this, over a string key, and existed only
     * because erasure would not let it be an overload.
     *
     * @param <K> what the collection is keyed by
     * @param map the collection
     * @return the same entries, keyed the way the file wants them
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
     * Reads the directions in, translating each key as it is read.
     *
     * The counterpart of readSquareMap for the one collection keyed by a square AND a route. Reading
     * and translating in one pass rather than reading raw and translating afterwards is the point:
     * the two-step version left the collection holding STORED keys in a memory-keyed field for the
     * length of a statement, and stage one showed what that costs - `Map.get` and `Set.contains` take
     * Object, so a stored key handed to a typed collection compiles and silently answers false.
     *
     * A key that will not parse is dropped, which is what the other ten do. Nothing unresolvable
     * reaches here anyway: entries whose page the index cannot resolve are held out of memory and
     * written back verbatim (OB-067).
     *
     * @param root the shared object being read
     * @param field which field
     * @param into the collection to fill
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

            // One is written as a bare string, which is what every version before this one wrote and
            // is all a station with a single signal - most of them - ever needs.  A file gains an
            // array only where somebody has actually paired a second signal.
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
            //
            // But ONLY when the file itself recorded that id (OB-092). `translatePages` falls back to
            // writing a bare page NAME when it cannot find an id, and a name that happens to look like
            // a number was then read straight back as somebody else's id. The file's own `pages`
            // record is the discriminator: an id this file wrote is in it, and a name is not.
            //
            // Defence rather than the fix. The fix is that renamePage keeps the numbering current, so
            // the fallback is not reached; this is here because the fallback still exists for a page
            // that is genuinely not in the index, and one silent transplant of a page's whole setup
            // was enough.
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

    private Map<String, Integer> translateLengths()
    {
        Map<String, Integer> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Integer> entry : tileLengths.entrySet())
        {
            out.put(toStored(entry.getKey().toString()), entry.getValue());
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

        return captions.get(captionTile);
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
            captions.remove(captionTile);
            return;
        }

        captions.put(captionTile, stationTile);
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

        // Compared as SQUARES (FR-013).
        //
        // This held the printed form and asked `wanted.equals(entry.getValue())`, which compiles -
        // String.equals takes Object - and is false for every entry once the values are squares. The
        // symptom was a platform captioned at both ends reporting none: "both ends of the platform
        // name it, expected 2, found 0".
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

    /**
     * Every caption, as caption square to sensor square.
     * @return
     */
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

    private Map<String, String> translatePortals()
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, TileKey> entry : portals.entrySet())
        {
            out.put(toStored(entry.getKey().toString()), toStored(entry.getValue().toString()));
        }

        return out;
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
     * The same square on a renamed page, or the square unchanged (FR-013).
     *
     * A construction rather than string surgery. The string form had to find the page part and splice
     * a new one in front of the coordinates, and it had to agree with `parseTileKey` about where that
     * part ended - an agreement kept by comment, and broken once.
     *
     * @param key the square
     * @param fromPage the old page name
     * @param toPage the new one
     * @return the square on the new page, or the original when it was not on the old one
     */
    private static TileKey rekeyOne(TileKey key, String fromPage, String toPage)
    {
        if (key == null) return null;

        return key.getPage().equals(fromPage) ? new TileKey(toPage, key.getX(), key.getY()) : key;
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

    /**
     * Moves a collection's entries from one page name to another.
     *
     * @param <K> what the collection is keyed by
     * @param <T> what it holds
     * @param map the collection
     * @param fromPage the name it had
     * @param toPage the name it has now
     */
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

    /**
     * Keys, as the strings a report shows a person (FR-013).
     *
     * The reconciliation report is read by somebody rather than by code, so it stays a list of
     * strings; this is the one door where a key becomes its printed form. Collected here rather
     * than at five call sites so the printed form has one definition.
     *
     * Generic since stage two: the keys it is given are squares at most call sites and squares plus a
     * route at one of them, and a report does not care which.
     *
     * @param <K> what the keys are
     * @param keys what was dropped
     * @return the same, printed, in order
     */
    private static <K> List<String> asStrings(List<K> keys)
    {
        List<String> out = new ArrayList<>();

        for (K key : keys)
        {
            out.add(key.toString());
        }

        return out;
    }

    /**
     * The same, for a plain set of squares.
     *
     * @param members squares the set remembers
     * @param existing the squares the diagram still has
     * @return the ones that were dropped
     */
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

    /**
     * Forgets a collection's entries whose square is no longer on the diagram.
     *
     * @param <K> what the collection is keyed by
     * @param <T> what it holds
     * @param map the collection
     * @param existing the squares the diagram still has
     * @return the keys that were dropped
     */
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
     * Reads a square-keyed field, translating each key as it arrives (FR-013).
     *
     * One step, where this was two: fill the map with the FILE's keys, then rewrite them in place.
     * The state in between - a map full of stored keys, in a field the rest of the class reads as
     * memory keys - is the state OB-067 was about, and it cannot be written down any more.
     *
     * A key that will not parse is dropped rather than kept as something unusable. That is not new:
     * `parseTileKey` has always returned null for one, and every caller has always skipped it.
     *
     * @param root the shared object
     * @param field which field
     * @param into the collection to fill
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

    /**
     * The same, for the one field whose values are numbers.
     */
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
     * The same, where the VALUE is a square as well - a portal's partner, a caption's station.
     *
     * An entry whose value will not parse is dropped whole. Half of a pairing is worse than none: a
     * portal with no partner and a caption pointing nowhere are both states the rest of the class
     * would have to guard against for ever.
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
     * The same, where the value is one square or an array of them.
     *
     * A member that will not parse is dropped and the rest of the list kept, which is the tolerant
     * direction this file chose everywhere else: one bad name must not cost a station its whole set
     * of protecting signals.
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

            // An entry whose members ALL failed to parse is dropped, not stored empty (CR-C2).
            //
            // Storing it empty writes `"key": []` back out - and an array is the form this file only
            // gained at version 2, so a setup stamped version 1 would go to disk carrying one. That is
            // exactly what the version gate exists to keep away from an older TrainControl, and it
            // would have been produced by reading a corrupt file rather than by anything the user did.
            //
            // The pair readers drop a half-parsed entry for the same reason: half a pairing is worse
            // than none. An empty signal list is the same thing said differently.
            if (!values.isEmpty()) into.put(tile, values);
        }
    }

    /**
     * A set of squares, translated as it is read.
     */
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
}
