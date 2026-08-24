package org.traincontrol.automationui;

import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TileGraph.Landing;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;
import org.traincontrol.util.I18n;

/**
 * One layout's autonomy setup, from the files on disk to the graph a train can run on.
 *
 * The whole chain in one place - store, tile graph, reduction, generated configuration - so that the
 * panels showing it can be about showing it.  Every edit goes through here, and every edit re-derives,
 * because the alternative is a screen that agrees with itself and disagrees with the railway.
 *
 * Headless on purpose: nothing here draws anything, which is what lets the behaviour be tested without
 * a screen and lets both the editor and the viewer work from the same object.
 *
 * @author Adam
 */
public class AutonomySession
{
    private final AutonomyCompanionStore store;

    private List<LayoutDiagram> pages = new ArrayList<>();
    private TileGraph graph;
    private GraphReducer reducer;

    private boolean dirty = false;

    public AutonomySession(File layoutFolder)
    {
        this.store = new AutonomyCompanionStore(layoutFolder);
    }

    public AutonomyCompanionStore getStore()
    {
        return store;
    }

    /**
     * Whether this layout can hold a setup at all - autonomy is local-layout only, because its files
     * live beside the diagram.
     * @return
     */
    public boolean isUsable()
    {
        return store.isUsable();
    }

    /**
     * Whether anybody has set autonomy up on this layout yet.
     * @return
     */
    public boolean exists()
    {
        return store.exists();
    }

    /**
     * Whether there are unsaved edits.  The editor saves on close, so this is what decides whether
     * closing needs to ask.
     * @return
     */
    public boolean isDirty()
    {
        return dirty;
    }

    /**
     * Reads the setup for these pages and derives everything from it.
     *
     * @param diagrams every page of the layout
     * @throws IOException if the setup exists but cannot be read
     */
    public void open(List<LayoutDiagram> diagrams) throws IOException
    {
        this.pages = diagrams == null ? new ArrayList<LayoutDiagram>() : new ArrayList<>(diagrams);

        Map<String, String> pageIds = new LinkedHashMap<>();

        for (LayoutDiagram page : pages)
        {
            if (page.getPageId() != null) pageIds.put(page.getName(), page.getPageId());
        }

        store.setPageIds(pageIds);
        store.load();

        rebuild();

        // Captions used to live in the layout file.  Anything still written there is brought across now,
        // once; see migrateStationLabels.  Its failures are kept for the UI to report rather than thrown,
        // because a page that could not be rewritten is not a reason to refuse to open the setup - the
        // migration simply runs again next time.
        migrationFailures = migrateStationLabels();

        forgetCaptionsOfNonStations();

        dirty = false;
    }

    /**
     * Every caption on one page, as a snapshot something else can hold and hand back.
     *
     * For the track diagram editor's undo.  A caption belongs to the setup rather than to the tile, so
     * it does not ride the editor's snapshot of components - and the editor moves and deletes captions
     * as tiles are moved and deleted, which left Ctrl+Z restoring the tile and not the name on it.
     *
     * By page, because that is what an editor session is about, and because restoring captions for
     * squares on pages nobody was editing would undo somebody else's work.
     *
     * @param page the page name
     * @return caption square to station square, for that page only
     */
    public Map<TileKey, TileKey> captionsOnPage(String page)
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        if (page == null) return out;

        for (Map.Entry<TileKey, TileKey> caption : store.getCaptions().entrySet())
        {
            if (page.equals(caption.getKey().getPage())) out.put(caption.getKey(), caption.getValue());
        }

        return out;
    }

    /**
     * Puts a page's captions back the way a snapshot found them.
     *
     * Everything currently on the page goes first, so a caption ADDED since the snapshot is removed
     * rather than left beside the ones being restored.  Written through the store directly: the
     * one-station-one-caption rule in setCaption would fight a wholesale restore, and a snapshot
     * already satisfies it by construction - it is a state this session was in.
     *
     * @param page the page name
     * @param captions what captionsOnPage returned earlier
     */
    public void restoreCaptionsOnPage(String page, Map<TileKey, TileKey> captions)
    {
        if (page == null) return;

        for (TileKey where : captionsOnPage(page).keySet())
        {
            store.setCaption(where, null);
        }

        if (captions != null)
        {
            for (Map.Entry<TileKey, TileKey> caption : captions.entrySet())
            {
                // Not onto a square that has since stopped being a station.  Undo restores the state
                // before an editor gesture, but demotion is a SEPARATE action it does not cover - so
                // restoring a caption for a square demoted in between would put a station's name back on
                // a plain point, the very thing forgetCaptionsOfNonStations and the demote-clear exist
                // to prevent.
                if (caption.getValue() == null || store.isStation(caption.getValue()))
                {
                    store.setCaption(caption.getKey(), caption.getValue());
                }
            }
        }

        touched();
    }

    /**
     * Drops arrival restrictions naming sides the square no longer has.
     *
     * getBarredArrivals hides them from every reader, so this is only about the file: left in it, a
     * dead side comes back the day the diagram is edited back into a shape that has it, as a rule
     * nobody remembers writing.  Cleaned at save, which is when the diagram and the setup are being
     * reconciled anyway.
     */
    private void forgetArrivalsThatNoLongerExist()
    {
        for (Map.Entry<TileKey, Set<Side>> entry
            : new LinkedHashMap<>(store.getBarredArrivals()).entrySet())
        {
            // Only squares this setup can still SEE.
            //
            // The index is derived from the graph, and the graph leaves out excluded pages - so a
            // square on an excluded page has no arrival sides at all, and pruning against that would
            // read as "every side of this station has gone" and delete the restriction outright.
            // Excluding a page has to be reversible, which is the same rule the reconciliation below
            // this call exists to keep, and this would have broken it for one field only.
            //
            // The same shape catches a station fed through a link, which splits into nothing.
            if (!getStationIndex().squares().contains(entry.getKey())) continue;

            Set<Side> live = new LinkedHashSet<>(entry.getValue());

            live.retainAll(arrivalSides(entry.getKey()));

            if (live.size() != entry.getValue().size())
            {
                store.setBarredArrivals(entry.getKey(), live);
            }
        }
    }

    /**
     * Drops name plaques belonging to squares that are no longer stations.
     *
     * The rule is enforced at the setter now, but setups written before it exists carry captions for
     * squares that were demoted long ago - and one of them is what made a reversing point announce
     * itself as a station the moment a train touched it.  Cleared at open, once, so nobody has to find
     * and delete them by hand.
     *
     * Silent: there is nothing here a user could act on, and the plaque comes back the moment the
     * square is made a station again.
     */
    private void forgetCaptionsOfNonStations()
    {
        for (Map.Entry<TileKey, TileKey> caption
            : new LinkedHashMap<>(store.getCaptions()).entrySet())
        {
            if (caption.getValue() == null) continue;

            if (!store.isStation(caption.getValue())) store.setCaption(caption.getKey(), null);
        }
    }

    /**
     * The pages the caption migration could not rewrite, for whoever opened the session to report.
     */
    private List<String> migrationFailures = new ArrayList<>();

    public List<String> getMigrationFailures()
    {
        return Collections.unmodifiableList(migrationFailures);
    }

    /**
     * Throws away every edit made since the last save, by reading the setup back off disk.
     *
     * Without this, "exit without saving" was a promise nothing kept.  Every edit goes straight into the
     * live configuration this session hands out - there was no copy to go back to - so the discarded
     * work was still in memory afterwards, still drawn on the diagram, and written out by the next save
     * from anywhere at all: ticking a page, loading a configuration, or closing the application.
     *
     * Re-reading rather than undoing: what is on disk is by definition the last state the user agreed
     * to, and rebuilding from it cannot leave a half-reverted graph the way replaying edits backwards
     * could.  Anything already saved deliberately survives, which is what makes the Save button in the
     * editor mean something.
     *
     * Station captions ARE taken back, along with everything else.  They used to live on the track
     * diagram and be written to the layout file the moment they were set, which made them the one
     * thing this could not undo; they have been part of the setup since captions stopped being text
     * labels, so they are re-read with the rest of it and the question asked of the user no longer
     * has to make an exception of them.
     *
     * @throws IOException if the setup cannot be re-read, in which case nothing is changed
     */
    public void discardEdits() throws IOException
    {
        store.load();

        rebuild();

        dirty = false;
    }

    /**
     * Creates a setup for a layout that has none, with one configuration to put things in.
     *
     * @param configurationName what to call the first configuration
     * @throws IOException
     */
    public void initialize(String configurationName) throws IOException
    {
        // Always creates one.  This used to do nothing at all unless the store was empty, which was
        // right while it was the "set autonomy up for the first time" button and wrong the moment it
        // became "add a configuration": the second one silently did nothing, and the menu came back
        // unchanged with the old configuration still running.
        //
        // A name already in use throws, as it does for a duplicate - the caller says so.
        store.createConfiguration(
            configurationName == null || configurationName.trim().isEmpty()
                ? "Default" : configurationName.trim(),
            null);

        rebuild();
        save();
    }

    /**
     * Re-derives everything from the diagram and the stored decisions.
     *
     * Called after every edit rather than on demand.  A derivation that lagged behind an edit would show
     * the user a graph that was true a moment ago, which is worse than showing none: they would be
     * checking their work against the wrong answer.
     */
    public final void rebuild()
    {
        // The index is NOT nulled here.  Nulling opened a window - the whole expensive rebuild body
        // below - in which a feedback-thread reader (updateStationLabels) could see null and derive the
        // index itself, against the reducer this method is at that moment replacing.  That is the
        // unsynchronised derivation SA-C1 set out to remove, reopened across the rebuild instead of a
        // single field write.
        //
        // Left alone, a reader mid-rebuild sees the OLD index - immutable, built from the old reducer,
        // and safe to read - until the new one is published in one assignment at the end.
        graph = new TileGraph(pages, store.getExcludedPages());

        store.applyTo(graph);

        graph.validatePortals();

        reducer = new GraphReducer(graph, store.asAuthored());
        reducer.reduce();

        // Now, on this thread, rather than on whichever thread happens to ask first
        deriveStationIndex();
    }

    public TileGraph getGraph()
    {
        return graph;
    }

    /**
     * What came back from a legacy autonomy.json, so the caller can say what happened.
     */
    public static class LegacyImport
    {
        /**
         * Names written onto a square that had none.
         */
        public int matched = 0;

        /**
         * Names left alone because the square already had one.
         */
        public int skipped = 0;

        /**
         * Locomotives put back where the old graph had them.
         */
        public int placed = 0;

        /**
         * Squares marked as turning trains round, from a terminus or a reversing point.
         */
        public int reversing = 0;

        /**
         * Priorities, speed multipliers, exclusions and switches carried over.
         */
        public int settings = 0;

        /**
         * Names whose sensor is not on this diagram, in the order the file gave them.
         */
        public final List<String> unmatched = new ArrayList<>();

        /**
         * Locomotives the file places that this database has never heard of.
         *
         * Not placed.  The running model refuses a placement naming a locomotive it cannot find, and
         * refuses it by invalidating the WHOLE layout - so writing these in would have produced a
         * setup that will not load, reported as a locomotive problem with nothing to connect it to
         * the import that caused it.
         */
        public final List<String> unknownLocomotives = new ArrayList<>();

        /**
         * Locomotives the file places at more than one point.  Only the first is placed.
         *
         * The running model invalidates on this too - one locomotive cannot stand in two places - and
         * an old graph that has drifted can easily name the same one twice.
         */
        public final List<String> duplicateLocomotives = new ArrayList<>();
    }

    /**
     * The active configuration's own data for one square, created if this is the first thing on it.
     *
     * A placement is not a decision about the track - it is where a train happens to be standing - so
     * it belongs to a configuration and not to the shared half.  Keyed by tile, the way the rest of
     * the configuration is, so a Point renamed later keeps whatever is standing on it.
     *
     * @param tile
     * @return null when no configuration is loaded to put anything in
     */
    private org.json.JSONObject configurationExtras(TileKey tile)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return null;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null) return null;

        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        org.json.JSONObject points = configuration.getJSONObject("points");

        if (!points.has(tile.toString())) points.put(tile.toString(), new org.json.JSONObject());

        return points.getJSONObject(tile.toString());
    }

    /**
     * Brings station names, station flags and lengths across from a legacy autonomy.json.
     *
     * The graph this replaces held its points by NAME and carried the s88 each one watched.  The
     * diagram derives its points from the feedback squares themselves, so the two can be matched by
     * that s88 - it is the one thing both models agree on, and it is what makes a square a point in
     * the first place.
     *
     * This exists because the names were never derivable.  A diagram gives the track's shape; what any
     * of it is CALLED, and which squares count as stations, are decisions somebody made once and would
     * otherwise have to make again, square by square, on upgrading.
     *
     * Names already here are kept, the same rule importing a configuration follows: this fills gaps,
     * it does not overwrite somebody's work with a file's.
     *
     * Nothing is written to disk - the caller saves, so a bad match can still be cancelled.
     *
     * @param legacy the parsed autonomy.json
     * @return what was matched, skipped and not found
     */
    /**
     * The per-point settings an old graph holds that the build still reads, unchanged.
     *
     * Everything here is copied into the configuration verbatim and emitted verbatim: the builder
     * passes unknown extras straight through, so these need translating no more than the placement
     * did.  Listed rather than copied wholesale because the rest of a legacy point - name, station,
     * s88, terminus, reversing, x, y - is either handled deliberately above or derived now, and
     * copying those would fight the derivation.
     *
     *   priority          how strongly autonomy prefers this destination
     *   speedMultiplier   the pace trains take through it
     *   excludedLocs      the locomotives this station will not accept
     *   active            a station's own switch.  The build ignores it on anything else, which is
     *                     why it is carried as given rather than filtered here.
     */
    private static final List<String> CARRIED_SETTINGS =
        Arrays.asList("priority", "speedMultiplier", "excludedLocs", "active");

    public LegacyImport importLegacy(org.json.JSONObject legacy)
    {
        return importLegacy(legacy, null);
    }

    /**
     * @param legacy the parsed autonomy.json
     * @param knownLocomotives the names the locomotive database holds, or null not to check
     * @return what was matched, placed, marked, carried and refused
     */
    public LegacyImport importLegacy(org.json.JSONObject legacy, Set<String> knownLocomotives)
    {
        LegacyImport result = new LegacyImport();

        // One locomotive stands in one place.  Tracked across the whole file rather than per point,
        // because the model's objection is global: two points naming the same locomotive invalidate
        // the layout, whichever pages they are on.
        Set<String> placedAlready = new LinkedHashSet<>();

        org.json.JSONArray points = legacy.optJSONArray("points");

        if (points == null || reducer == null) return result;

        Map<Integer, TileKey> bySensor = new LinkedHashMap<>();

        // Sensors carried by more than one square, which no amount of reading the file can resolve.
        //
        // Two squares on the same s88 is ordinary - a station and its approach guard - and on a layout
        // whose pages repeat a section it happens across pages too.  The old graph names ONE point per
        // sensor, so there is no way to tell which square it meant, and putting it on whichever came
        // last would land somebody's station on the wrong page silently.  Refused and reported instead;
        // excluding the duplicating pages first is what makes the rest of the import unambiguous.
        Set<Integer> ambiguous = new LinkedHashSet<>();

        for (GraphReducer.ReducedPoint point : reducer.getPoints().values())
        {
            if (point.getS88() <= 0) continue;

            if (bySensor.containsKey(point.getS88())) ambiguous.add(point.getS88());

            bySensor.put(point.getS88(), point.getTile());
        }

        for (Integer sensor : ambiguous) bySensor.remove(sensor);

        for (int i = 0; i < points.length(); i++)
        {
            org.json.JSONObject point = points.optJSONObject(i);

            if (point == null) continue;

            String name = point.optString("name", "");

            if (name.trim().isEmpty()) continue;

            int sensor = point.optInt("s88", 0);

            TileKey tile = sensor > 0 ? bySensor.get(sensor) : null;

            // A point with no sensor, or one watching a sensor this diagram does not draw.  Reported
            // rather than dropped: it is how somebody finds out that a page is missing or excluded.
            if (tile == null)
            {
                result.unmatched.add(name);
                continue;
            }

            // Before the name, and regardless of it.  A placement is about the SQUARE, so a square
            // somebody has already named should still get its locomotive back.
            org.json.JSONObject standing = point.optJSONObject("loc");

            String home = point.optString("home", "");

            boolean anySetting = false;

            for (String key : CARRIED_SETTINGS)
            {
                if (point.has(key)) anySetting = true;
            }

            if (standing != null || !home.trim().isEmpty() || anySetting)
            {
                org.json.JSONObject extras = configurationExtras(tile);

                if (extras != null)
                {
                    if (standing != null && !extras.has(AutonomyBuilder.LOCOMOTIVE))
                    {
                        String locName = standing.optString("name", "").trim();

                        // Checked here rather than left to the load.  The model answers an unknown
                        // locomotive by invalidating the whole layout, so a file naming one that has
                        // since been renamed would have produced a setup that refuses to open, with
                        // an error about a locomotive and nothing saying the import put it there.
                        if (locName.isEmpty())
                        {
                            // a placement with no name: nothing to place, nothing worth reporting
                        }
                        else if (knownLocomotives != null && !knownLocomotives.contains(locName))
                        {
                            result.unknownLocomotives.add(locName);
                        }
                        else if (!placedAlready.add(locName))
                        {
                            result.duplicateLocomotives.add(locName);
                        }
                        else
                        {
                            // Copied whole: the old graph recorded the speed, the arrival and
                            // departure functions and the train length alongside the name, and the
                            // builder reads exactly this shape back out.
                            extras.put(AutonomyBuilder.LOCOMOTIVE,
                                new org.json.JSONObject(standing.toString()));

                            // And which way it is pointing, chosen at random from this square's
                            // copies.
                            //
                            // The old graph had one Point per sensor and no notion of facing, so the
                            // file cannot say - and without one every imported locomotive lands on
                            // whichever copy the build walked in by first, which on a real layout
                            // means a yard full of trains all facing the same arbitrary way.
                            //
                            // Not legality-checked, unlike placing one by hand on the diagram: that
                            // asks the RUNNING graph which copies can be left, and during an import
                            // there is no running graph to ask.  A copy that cannot be left is
                            // reported by the checks, which is the same answer arrived at later.
                            if (getFacing(tile) == null)
                            {
                                java.util.List<Side> ways =
                                    new ArrayList<>(facingsFor(tile).values());

                                if (!ways.isEmpty())
                                {
                                    setFacing(tile,
                                        ways.get(new java.util.Random().nextInt(ways.size())));
                                }
                            }

                            result.placed++;
                        }
                    }

                    if (!home.trim().isEmpty() && !extras.has("home")) extras.put("home", home.trim());

                    for (String key : CARRIED_SETTINGS)
                    {
                        // Gap-filled like everything else here, so re-running cannot undo an edit
                        // somebody made after the first import.
                        if (!point.has(key) || extras.has(key)) continue;

                        Object value = point.get(key);

                        // Copied rather than shared: a JSONArray handed straight over would be the same
                        // object the caller's parsed file still holds, and anything that later edited
                        // the exclusions here would edit their file's copy too.
                        if (value instanceof org.json.JSONArray)
                        {
                            value = new org.json.JSONArray(value.toString());
                        }
                        else if (value instanceof org.json.JSONObject)
                        {
                            value = new org.json.JSONObject(value.toString());
                        }

                        extras.put(key, value);

                        result.settings++;
                    }
                }
            }

            // Terminus and reversing are DERIVED now, so neither can be written down.  What the old
            // graph was recording, in both cases, is that every train arriving here turns round - and
            // that is authored as mustReverse.  Which of the two words the build then emits follows
            // from whether the square is a station, which is imported above, so one flag restores both.
            //
            //   terminus            a station that reverses on arrival.  In this file it is only ever
            //                       set on stations, which is what the model has always meant by it.
            //   reversing, plain    somewhere trains turn round that is not a destination.
            //   reversing, station  the old "reversing station", which said two things at once: it
            //                       reverses, AND autonomy never chooses it.  Those are separate now -
            //                       a terminus and a berth - so it takes the parking flag as well, or
            //                       importing would quietly turn somebody's shunting neck into a
            //                       destination trains get sent to.
            boolean turnsTrains = point.optBoolean("terminus", false)
                || point.optBoolean("reversing", false);

            // Left alone if the square already says something about reversing: this fills gaps.
            boolean alreadyMarked = getPointProperty(tile, AutonomyBuilder.MUST_REVERSE) != null
                || getPointProperty(tile, AutonomyBuilder.CAN_REVERSE) != null;

            if (turnsTrains && !alreadyMarked)
            {
                setPointFlag(tile, AutonomyBuilder.CAN_REVERSE, false);
                setPointProperty(tile, AutonomyBuilder.MUST_REVERSE, Boolean.TRUE);

                if (point.optBoolean("reversing", false) && point.optBoolean("station", false))
                {
                    setPointFlag(tile, AutonomyBuilder.PARKING, true);
                }

                result.reversing++;
            }

            String existing = store.getPointName(tile);

            if (existing != null && !existing.trim().isEmpty())
            {
                result.skipped++;
                continue;
            }

            store.setPointName(tile, name);

            // Labelled on the station square itself.
            //
            // Every station has to be shown on the diagram - it is an error not to be - and an import
            // that named fifty of them and captioned none would have handed the user fifty errors to
            // clear by hand.  The station's own square is the one place that is always right: it
            // exists, it is on the page the reader is looking at, and it cannot land on somebody
            // else's track the way searching for nearby blank space can.
            //
            // Only where the station has no caption already, like everything else here.
            if (point.optBoolean("station", false) && captionsFor(tile).isEmpty())
            {
                store.setCaption(tile, tile);
            }

            if (point.optBoolean("station", false)) store.setStation(tile, true);

            int length = point.optInt("maxTrainLength", 0);

            if (length > 0) store.setTileLength(tile, length);

            result.matched++;
        }

        // Derived again, once, now that the authored data has changed.
        //
        // The reduction is what the diagram draws from - the names on the squares, the station
        // markers, the captions - and it is built from a snapshot of the authored data taken when the
        // session opened.  Without this the import wrote everything correctly to the store and the
        // diagram went on showing what it knew before, so the whole thing looked to have done nothing
        // until the layout was reloaded.
        //
        // After the loop rather than inside it: the tile-by-sensor map above comes from the reduction,
        // so rebuilding mid-loop would pull it out from under the very walk that is using it.
        rebuild();

        return result;
    }

    /**
     * Brings in an exported file and derives again, so the diagram shows what arrived.
     *
     * The store's own importBundle knows nothing about the derivation - it holds authored data and
     * that is all - so a caller that went straight to it got a correct store and a screen still
     * showing what it knew before.  A configuration carries the flags that terminus, reversing and a
     * station's switch are DERIVED from, so those in particular arrived and stayed invisible.
     *
     * Here rather than in the caller because it is the same invariant every time: authored data
     * changed, so the derivation is stale.  The legacy import learned that separately.
     *
     * @param name what to call the configuration here
     * @param file the parsed export
     * @return how many shared entries were filled in
     */
    public int importBundle(String name, org.json.JSONObject file)
    {
        int filled = store.importBundle(name, file);

        rebuild();

        return filled;
    }

    /**
     * Shuts any page that repeats a sensor an earlier page already carries.
     *
     * A layout whose pages draw the same track twice - an overview and a detail view of one yard, say -
     * gives two squares the same s88, and nothing downstream can tell which one a train is standing on.
     * The reduction makes a Point of each, so the same sensor becomes two destinations; a legacy import
     * cannot decide which square a name belongs to and refuses it; and the checks report the duplicate
     * on every page it appears on.
     *
     * Earliest page wins, in the order the layout lists them, because that is the one a reader thinks
     * of as the real one and the only rule that does not depend on which page happens to be open.
     * A page that is shut does NOT contribute its sensors to what counts as seen: the next page
     * repeating them is then repeating the page that is still in play, not one nobody is using.
     *
     * Only ever run when a setup is brand new - see the caller.  It is a starting point, not a policy:
     * the page checkboxes are still there, and turning one back on must not be undone by this the next
     * time a configuration is added.
     *
     * @return the pages this shut, in the order they appear
     */
    public List<String> excludeRepeatedSensorPages()
    {
        Set<Integer> seen = new LinkedHashSet<>();

        List<String> shut = new ArrayList<>();

        for (LayoutDiagram page : pages)
        {
            if (store.getExcludedPages().contains(page.getName())) continue;

            Set<Integer> here = new LinkedHashSet<>();

            boolean repeats = false;

            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component == null || !component.isFeedback()) continue;

                int sensor = component.getRawAddress();

                if (sensor <= 0) continue;

                if (seen.contains(sensor)) repeats = true;

                here.add(sensor);
            }

            if (repeats)
            {
                store.setPageExcluded(page.getName(), true);

                shut.add(page.getName());

                continue;
            }

            seen.addAll(here);
        }

        if (!shut.isEmpty()) rebuild();

        return shut;
    }

    /**
     * Which configuration should be running once an import has finished.
     *
     * Stated here, as a rule rather than as a branch inside a button, because it was wrong in a way no
     * test could see: the caller returned early when something was already running, so an import onto
     * a working setup never reloaded - and the running layout went on describing the setup as it was
     * before, with every caption the import created drawn against a Point that did not exist.
     *
     * Whatever is already chosen wins.  Importing a configuration is not a request to switch to it,
     * and the shared half an import merges - the names, the stations, the lengths - belongs to every
     * configuration equally, so the one already running needs re-deriving whichever was imported.
     *
     * @param running what is loaded now, or null for nothing
     * @param imported the name the import was given
     * @return the configuration to load, or null when there is nothing to do
     */
    public static String configurationToLoadAfterImport(String running, String imported)
    {
        if (running != null && !running.trim().isEmpty()) return running;

        if (imported == null || imported.trim().isEmpty()) return null;

        return imported.trim();
    }

    /**
     * The kinds of file the one Import action accepts.
     */
    public static enum ImportFormat
    {
        /**
         * A configuration and the track decisions it refers to, as exportBundle writes them.
         */
        BUNDLE,

        /**
         * A configuration on its own, as exporting wrote it before bundles existed.
         */
        CONFIGURATION,

        /**
         * An autonomy.json from the graph this feature replaces.
         */
        LEGACY_GRAPH,

        /**
         * Something else entirely - a routes file, a locomotive database, a diagram.
         */
        UNKNOWN
    }

    /**
     * Works out which of them a parsed file is, so the user does not have to say.
     *
     * Each shape is identified by something only it has, not by anything as fragile as a filename:
     *
     *   BUNDLE          carries "configuration", which is the key exportBundle invented.
     *   LEGACY_GRAPH    carries "points" as an ARRAY.  The old graph was a list of Points, each with
     *                   its own name and s88; nothing else here is a list under that name.
     *   CONFIGURATION   carries "points" as an OBJECT, keyed by square.  That is the shape a
     *                   configuration has always had, and the only other thing that uses the name.
     *
     * The array-versus-object distinction is what makes this safe: the two formats that share a key
     * disagree about its type, so neither can be mistaken for the other by a file that merely happens
     * to contain the word.
     *
     * @param file the parsed file
     * @return what it is
     */
    public static ImportFormat detectImportFormat(org.json.JSONObject file)
    {
        if (file == null) return ImportFormat.UNKNOWN;

        if (file.optJSONObject(AutonomyCompanionStore.EXPORT_CONFIGURATION) != null)
        {
            return ImportFormat.BUNDLE;
        }

        // A list of Points, which is the old graph and nothing else
        if (file.optJSONArray("points") != null) return ImportFormat.LEGACY_GRAPH;

        // Keyed by square, which is a configuration
        if (file.optJSONObject("points") != null) return ImportFormat.CONFIGURATION;

        // A configuration whose points have all been removed is still a configuration, and its globals
        // are the only thing left to say so.
        if (file.optJSONObject("globals") != null) return ImportFormat.CONFIGURATION;

        // Deliberately NOT recognised: a configuration created and never used, which carries nothing
        // but its own name.  A name is not evidence - half the JSON in the world has one - and there
        // would be nothing in such a file to import anyway.

        return ImportFormat.UNKNOWN;
    }


    /**
     * Which locomotive the active configuration records standing on each square.
     *
     * Read from the configuration rather than from the running layout on purpose: the configuration is
     * what the next build reads, so it is where a second placement can hide.  The running layout has
     * already refused to hold one twice by the time anybody could ask it.
     *
     * @return square to locomotive name, empty when nothing is loaded
     */
    private Map<TileKey, String> placedLocomotives()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();

        String active = store.getActiveConfiguration();

        if (active == null) return out;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return out;

        org.json.JSONObject points = configuration.getJSONObject("points");

        for (String key : points.keySet())
        {
            org.json.JSONObject extras = points.optJSONObject(key);

            if (extras == null) continue;

            org.json.JSONObject standing = extras.optJSONObject("loc");

            if (standing == null) continue;

            String name = standing.optString("name", "");

            if (name.trim().isEmpty()) continue;

            TileKey tile = AutonomyCompanionStore.parseTileKey(key);

            if (tile != null) out.put(tile, name);
        }

        return out;
    }

    public GraphReducer getReducer()
    {
        return reducer;
    }

    /**
     * What a caption looked like when it lived in the layout file, and the only thing that still reads
     * it: the one-time migration that brings those labels into the setup.  See migrateStationLabels.
     */
    public static final String STATION_LABEL_PREFIX = "Point:";

    /**
     * Every station that some square of some page is showing the name of.
     *
     * Squares, not names.  A caption points at the sensor it is about, so asking "is this station
     * labelled" no longer means matching text against text - which is what made it possible for a
     * caption to look live while naming a Point that had been renamed out from under it.
     *
     * All pages, including excluded ones: exclusion says autonomy will not route over a page, not that
     * the page has stopped being drawn, and a caption there is still on the user's screen.
     *
     * @return the sensors that have a caption somewhere
     */
    public Set<TileKey> getLabelledStationTiles()
    {
        return new LinkedHashSet<>(store.getCaptions().values());
    }

    /**
     * The station a caption on this square is about.
     *
     * @param captionTile the square the text sits on
     * @return the sensor's square, or null when nothing is captioned there
     */
    public TileKey getCaptionTarget(TileKey captionTile)
    {
        return store.getCaptionTarget(captionTile);
    }

    /**
     * Every square showing this station's name.
     *
     * @param stationTile
     * @return the caption squares, possibly none
     */
    public Set<TileKey> captionsFor(TileKey stationTile)
    {
        return store.captionsFor(stationTile);
    }

    /**
     * Every caption on the layout, as the square it is drawn on to the sensor it is about.
     * @return
     */
    public Map<TileKey, TileKey> getCaptions()
    {
        return store.getCaptions();
    }

    /**
     * Shows a station's name on a square, or stops showing it.
     *
     * @param captionTile where the text goes
     * @param stationTile the sensor it is about, or null to clear the square
     */
    public void setCaption(TileKey captionTile, TileKey stationTile)
    {
        // One station, one caption - decided HERE rather than at each door.
        //
        // There are three ways to caption a station: place it automatically, choose the square
        // yourself in the autonomy editor, and now drag the square it sits on in the track diagram
        // editor.  Only the first knew to remove the old one, so choosing a new square left the
        // station named twice on the diagram and nothing said which was current.
        //
        // Cleared before setting rather than after, so moving a caption onto a square that already
        // shows the same station is not a clear-then-set of the same entry.
        if (stationTile != null) clearCaptions(stationTile, captionTile);

        store.setCaption(captionTile, stationTile);
        touched();
    }

    /**
     * Forgets the captions belonging to a square that has just been deleted from the diagram.
     *
     * Both directions, because a square can be either end of a caption:
     *
     *   the square the text sits ON      its caption goes with it.  A caption may legitimately sit
     *                                    on blank space, so an EMPTY square keeps its caption - but a
     *                                    square somebody has just deleted is not blank, it is gone,
     *                                    and the difference is the instruction they gave.
     *   the station the text is ABOUT    every caption naming it goes.  Text pointing at track that
     *                                    no longer exists is the orphan this whole design removed.
     *
     * Without this the label outlived the track: it stayed where it was, naming nothing, with no way
     * to get rid of it - and putting any tile back on that square made it look like the new tile's
     * label, because a caption is drawn wherever its square is.
     *
     * @param tile the square being deleted
     * @return true when something was forgotten, so the caller can say so
     */
    public boolean forgetCaptionsAt(TileKey tile)
    {
        if (tile == null) return false;

        boolean any = false;

        if (store.getCaptionTarget(tile) != null)
        {
            store.setCaption(tile, null);
            any = true;
        }

        for (TileKey where : new LinkedHashSet<>(captionsFor(tile)))
        {
            store.setCaption(where, null);
            any = true;
        }

        if (any) touched();

        return any;
    }

    /**
     * Everything the setup holds, for a caller that may have to put it all back.
     *
     * @return a snapshot for restoreSetup
     */
    public org.json.JSONObject snapshotSetup()
    {
        return store.snapshotSetup();
    }

    /**
     * Puts back a whole snapshot, and writes it out.
     *
     * @param was a snapshot from snapshotSetup
     * @return whether it reached the file
     */
    public boolean restoreSetup(org.json.JSONObject was)
    {
        if (was == null) return true;

        store.restoreSetup(was);

        rebuild();

        return saveQuietly();
    }

    /**
     * Everything the setup holds about one page, for the diagram editor's undo.
     *
     * @param page the page name
     * @return a snapshot to hand back to restorePage
     */
    public java.util.Map<String, Object> snapshotPage(String page)
    {
        return store.snapshotPage(page);
    }

    /**
     * Puts a page's setup back as it was, and rebuilds the graph from it.
     */
    public void restorePage(String page, java.util.Map<String, Object> snapshot)
    {
        if (snapshot == null) return;

        store.restorePage(page, snapshot);

        // Once - see moveTiles above
        touched();
    }

    /**
     * Writes the setup out without reconciling it against the diagram.
     *
     * For the diagram editor, which changes the setup a square at a time as tiles are moved.  Its
     * edits have to reach disk as they happen: nothing else saves this session, and the reset that
     * follows an edit to the diagram throws it away.  Reconciling is what must NOT happen here - the
     * diagram is being edited, so half of it disagrees with the setup at any given moment, and a
     * reconcile would delete everything on the half not yet caught up.
     *
     * @return whether it was written
     */
    public boolean saveQuietly()
    {
        try
        {
            saveWithoutReconciling();

            return true;
        }
        catch (java.io.IOException e)
        {
            return false;
        }
    }

    /**
     * Follows tiles being moved on the diagram, with everything written about them.
     *
     * The diagram editor calls this when it moves track.  Only the CAPTION used to follow, so a
     * station whose sensor was nudged one square kept its name floating over the new square and lost
     * everything else it was - the station designation, its facings, its arrival restrictions, its
     * length, its placement - because all of those are keyed by square and the square had changed.
     * The next reconcile then found a station on a square with no sensor and dropped it for good.
     *
     * The whole group at once: see AutonomyCompanionStore.moveTiles for why one at a time is unsafe.
     *
     * @param moves each square being vacated, and where it is going
     * @return true when anything moved
     */
    public boolean moveTiles(Map<TileKey, TileKey> moves)
    {
        return moveTiles(moves, null);
    }

    /**
     * One diagram edit: what moved, and what else was built over.
     *
     * See AutonomyCompanionStore.moveTiles for why these are one call rather than two.
     *
     * @param moves each square being vacated, and where it is going - may be null
     * @param builtOver squares whose track has been replaced - may be null
     * @return true when there was anything to do
     */
    public boolean moveTiles(Map<TileKey, TileKey> moves, java.util.Collection<TileKey> builtOver)
    {
        boolean any = builtOver != null && !builtOver.isEmpty();

        if (moves != null)
        {
            for (Map.Entry<TileKey, TileKey> move : moves.entrySet())
            {
                if (move.getKey() != null && move.getValue() != null
                    && !move.getKey().equals(move.getValue()))
                {
                    any = true;
                }
            }
        }

        if (!any) return false;

        store.moveTiles(moves, builtOver);

        // The graph is built from the squares, so it is now describing the old ones - and touched()
        // is what rebuilds it.  This used to call rebuild() again immediately afterwards, so every
        // move paid for two full passes: a fresh TileGraph over every page, a GraphReducer.reduce, and
        // an AutonomyBuilder naming run, twice.  Nothing between them could have changed.
        touched();

        return true;
    }

    /**
     * Squares whose track has been replaced by other track.
     *
     * What was written about a square describes the tile that WAS there - a station is a particular
     * sensor, a length is a particular piece of rail - so when a different tile is written over it,
     * the setup is describing something that is gone.  Reconcile cannot catch this on its own: it
     * drops setup from squares that are now EMPTY, and one of these is not empty, it is occupied by
     * something else.
     *
     * @param tiles the squares built over
     * @return true when there was anything to forget
     */
    public boolean forgetTiles(java.util.Collection<TileKey> tiles)
    {
        return moveTiles(null, tiles);
    }

    /**
     * One tile, for the single-tile drag.
     */
    public boolean moveTile(TileKey from, TileKey to)
    {
        Map<TileKey, TileKey> one = new LinkedHashMap<>();

        one.put(from, to);

        return moveTiles(one);
    }

    /**
     * Moves a station's caption from one square to another, for the track diagram editor.
     *
     * Dragging a tile carries whatever was written on it.  Without this, rearranging a diagram meant
     * every caption on every square that moved had to be placed again by hand, which on a real layout
     * is most of the reason not to rearrange it.
     *
     * @param from the square being vacated
     * @param to where it is going
     * @return true when a caption actually moved, so the caller can say so
     */
    public boolean moveCaption(TileKey from, TileKey to)
    {
        if (from == null || to == null || from.equals(to)) return false;

        TileKey station = store.getCaptionTarget(from);

        if (station == null) return false;

        store.setCaption(from, null);

        // Through setCaption, so anything already captioning that station elsewhere - including
        // whatever was on the destination square - goes with it.
        setCaption(to, station);

        return true;
    }

    /**
     * The page a key names, or null if this session has never heard of it.
     */
    private LayoutDiagram pageOf(TileKey tile)
    {
        for (LayoutDiagram page : pages)
        {
            if (page.getName().equals(tile.getPage())) return page;
        }

        return null;
    }

    /**
     * Puts a station's name on the diagram where it will be readable, if it has no label yet.
     *
     * Where depends on how the track lies.  A square whose rails run east-west has room for the name
     * beside them, so the label goes ON it.  A square whose rails run north-south, or round a corner,
     * has the name sitting across the track instead - so it goes on the square BELOW, which is where a
     * platform name is written on a real diagram.
     *
     * Placed only when the square below is free, and never over somebody's own caption: a diagram is
     * the user's drawing before it is autonomy's data.  Where there is nowhere to put it, nothing
     * happens and the "not shown anywhere" warning still says so.
     *
     * @param tile the station
     * @return what happened, so a caller can say why nothing did
     */
    public String placeCaption(TileKey tile)
    {
        // The authored name, not the generated one.  A square marked as a station a moment ago has
        // only the coordinate the reducer invented for it, and "1 - Main 12,7" written across a track
        // plan is worse than no caption at all - the caption goes on when the station is NAMED.
        String name = store.getPointName(tile);

        if (name == null || name.trim().isEmpty()) return "autosetup.ui.labelNotNamedYet";

        // A station already shown somewhere is MOVED, not refused.
        //
        // Asking to show a name is asking for it to be here, and answering "it is already somewhere"
        // left the user to find and delete the old one first - or, once imports began captioning every
        // station on its own square, made the action do nothing at all on a freshly imported setup.
        //
        // The old ones are cleared further down, once somewhere new has actually been found, so a
        // station with nowhere to go keeps the caption it had rather than losing it to a move that
        // then failed.

        LayoutDiagram page = pageOf(tile);

        if (page == null || graph == null) return "autosetup.ui.labelNoDiagram";

        LayoutDiagramComponent here = page.getComponent(tile.getX(), tile.getY());

        if (here == null) return "autosetup.ui.labelNoDiagram";

        // Beside the platform where there is room, on it where there is not.  A caption written on the
        // sensor sits across the rails; on the plain track next to it, it sits alongside them, which is
        // where a station name goes on a real diagram.
        //
        // Below for a station lying north-south, left for one lying east-west, then the other three -
        // a preference, not a rule, because the preferred square is usually occupied by something.
        List<Side> sides = labelSides(tile);

        // First choice: connected plain track running straight through.
        for (Side side : sides)
        {
            TileKey at = neighbour(tile, side);

            if (!connects(tile, side, at)) continue;

            LayoutDiagramComponent next = page.getComponent(at.getX(), at.getY());

            // Nor a square already carrying a caption: one square, one caption
            if (next == null || next.hasLabel() || next.isFeedback()) continue;

            if (store.getCaptionTarget(at) != null) continue;

            // Straight THROUGH rather than of type STRAIGHT.  A signal or an uncoupler is a plain
            // piece of running line with a fitting on it, and beside a platform there is often
            // nothing else - insisting on the bare type found no square at all on a real layout.
            if (!runsStraightThrough(next)) continue;

            setCaption(at, tile);

            return null;
        }

        // Second choice: an empty square next to it.  Blank space beside a platform is the most readable
        // place of all; it is simply rarer than track.  Nothing is added to the diagram to hold it - the
        // caption is autonomy's, and it is drawn on whatever square it names.
        for (Side side : sides)
        {
            TileKey at = neighbour(tile, side);

            // Inside the part of the page the running diagram DRAWS, which is the box around its
            // components rather than the whole grid.  getComponent cannot tell us: it answers null both
            // for a blank square and for one off the edge, and this loop reads null as "free".  So a
            // station against an edge had its caption filed one square outside the drawn area - shown
            // in the editor, which pads the grid, and never shown on the diagram, while the "not shown
            // anywhere" warning went quiet because a caption did exist.
            if (at.getX() < page.getMinx() || at.getY() < page.getMiny()) continue;
            if (at.getX() > page.getMaxx() || at.getY() > page.getMaxy()) continue;

            if (page.getComponent(at.getX(), at.getY()) != null) continue;

            if (store.getCaptionTarget(at) != null) continue;

            setCaption(at, tile);

            return null;
        }

        // Last resort: the sensor itself.  Across the rails is not ideal, and it is still better than
        // a station with no name anywhere - which is the thing the checks complain about.
        if (!here.hasLabel())
        {
            setCaption(tile, tile);

            return null;
        }

        return "autosetup.ui.labelNoRoom";
    }

    /**
     * Forgets wherever this station was being shown, so that placing it again leaves exactly one.
     *
     * Called only once somewhere new has been found: clearing first and then failing to place would
     * answer "show this name" by removing the name that was there.
     *
     * @param station
     */
    private void clearCaptions(TileKey station, TileKey except)
    {
        for (TileKey where : new LinkedHashSet<>(captionsFor(station)))
        {
            if (where.equals(except)) continue;

            store.setCaption(where, null);
        }
    }

    /**
     * Whether leaving this square by that side actually lands on that one.
     *
     * Connected, not merely adjacent: track that happens to pass the end of a platform is not the
     * platform road, and a name on it would point at the wrong line.
     */
    private boolean connects(TileKey tile, Side side, TileKey to)
    {
        if (graph == null) return false;

        TileGraph.Landing landing = graph.landing(tile, side);

        return landing != null && to.equals(landing.getTile());
    }

    /**
     * Whether a square's track runs straight through it - one route, joining two opposite sides.
     */
    private boolean runsStraightThrough(LayoutDiagramComponent component)
    {
        List<Route> routes = TilePorts.ports(component.getType(),
            component.getOrientation(), 0);

        if (routes.size() != 1) return false;

        Route route = routes.get(0);

        return route.getA() != null && route.getB() != null
            && route.getA() == route.getB().opposite();
    }

    /**
     * The sides to try, best first: below for a station lying north-south, left for one lying
     * east-west.
     */
    private List<Side> labelSides(TileKey tile)
    {
        boolean vertical = false;

        // Asked of the GRAPH, not of the port map directly.  ports() wants a state index, and a sensor
        // that happens to be triggered reports state 1 - which is past the end of the one state a
        // feedback tile has, so the call comes back empty and the square looks like it has no track on
        // it at all.  The graph has already settled that question.
        for (Route route : graph.getRoutes(tile).values())
        {
            if (route.touches(Side.N) && route.touches(Side.S)) vertical = true;
        }

        return vertical
            ? java.util.Arrays.asList(Side.S, Side.N, Side.W, Side.E)
            : java.util.Arrays.asList(Side.W, Side.E, Side.S, Side.N);
    }

    /**
     * The square on the given side of this one.  North is up, so it is the smaller y.
     */
    private TileKey neighbour(TileKey tile, Side side)
    {
        switch (side)
        {
            case N: return new TileKey(tile.getPage(), tile.getX(), tile.getY() - 1);
            case S: return new TileKey(tile.getPage(), tile.getX(), tile.getY() + 1);
            case E: return new TileKey(tile.getPage(), tile.getX() + 1, tile.getY());
            default: return new TileKey(tile.getPage(), tile.getX() - 1, tile.getY());
        }
    }

    /**
     * Brings captions written into the diagram as "Point:<name>" labels across into the setup, once.
     *
     * They used to live in the layout file, bound to a Point by NAME, and every trouble captions had
     * came from that: a rename had to rewrite every page showing the name, a station split into several
     * Points was called none of them, and a name that no longer existed left a caption that looked live
     * and did nothing.  Adam’s sample layout carried four of those last - BottomMainCTerm and the
     * rest, left behind by the hand-written configuration this feature replaced.
     *
     * A label naming a station this setup knows becomes a caption keyed to that station’s SQUARE.
     * One naming nothing is dropped, on the author’s instruction: it points at track that does not
     * exist, and drawing it taught the reader that a caption might mean nothing.
     *
     * The setup is written BEFORE the pages are.  If the order were the other way round and a page write
     * failed, the labels would be gone from the file and the captions absent from the setup - the
     * captions would simply have been deleted.  This way a failure leaves both, and the migration runs
     * again next time and reaches the same answer.
     *
     * This is the last time autonomy writes to a layout file at all.
     *
     * @return the pages that could not be written, empty when all was well
     */
    private List<String> migrateStationLabels()
    {
        Map<LayoutDiagram, List<LayoutDiagramComponent>> found = new LinkedHashMap<>();

        boolean migrated = false;

        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component == null || component.getLabel() == null) continue;

                if (!component.getLabel().startsWith(STATION_LABEL_PREFIX)) continue;

                if (!found.containsKey(page)) found.put(page, new ArrayList<LayoutDiagramComponent>());

                found.get(page).add(component);
            }
        }

        if (found.isEmpty()) return new ArrayList<>();

        for (Map.Entry<LayoutDiagram, List<LayoutDiagramComponent>> entry : found.entrySet())
        {
            for (LayoutDiagramComponent component : entry.getValue())
            {
                String name = component.getLabel().substring(STATION_LABEL_PREFIX.length());

                TileKey where = new TileKey(entry.getKey().getName(),
                    component.getX(), component.getY());

                TileKey station = tileNamed(name);

                if (station != null)
                {
                    store.setCaption(where, station);

                    migrated = true;
                }
            }
        }

        List<String> failures = new ArrayList<>();

        // Nothing named a station this setup knows, so there is nothing to migrate and nothing to
        // write.  Saving regardless created a setup file for a layout with no autonomy at all, and
        // rewrote every page that merely CONTAINED a label - and because an unrecognised label is
        // deliberately left where it is, the same pages were found and rewritten again at every
        // launch from then on.  The sample layout's orphan labels made that the shipped default.
        if (!migrated) return failures;

        try
        {
            store.save();
        }
        catch (IOException e)
        {
            // The setup could not be written, so the labels stay where they are and this runs again
            failures.add(String.valueOf(e.getMessage()));

            return failures;
        }

        for (Map.Entry<LayoutDiagram, List<LayoutDiagramComponent>> entry : found.entrySet())
        {
            boolean changed = false;

            for (LayoutDiagramComponent component : entry.getValue())
            {
                // Only the ones that became a caption.  A label naming a station this setup has never
                // heard of is left exactly where it is: stripping it would delete the only record that
                // it ever existed, on the strength of this program not recognising a name - and the
                // author's instruction to drop orphans was about not DRAWING them, not about editing
                // somebody's diagram to remove them.
                String was = component.getLabel();

                if (was == null || !was.startsWith(STATION_LABEL_PREFIX)) continue;

                if (tileNamed(was.substring(STATION_LABEL_PREFIX.length())) == null) continue;

                // Emptied, which is how a text square stops existing: the exporter does not write a TEXT
                // element with no text.  Anything the file said about that square which this program
                // cannot model is still written, so emptying it is not the same as deleting the line.
                component.setLabel("");

                changed = true;
            }

            // Only pages this actually changed.  A page holding nothing but unrecognised labels is
            // looked at and left alone; writing it would rewrite a file for no reason, and the labels
            // that caused the visit are still there to cause the next one.
            if (!changed) continue;

            try
            {
                entry.getKey().saveChanges(null, false);
            }
            catch (Exception e)
            {
                failures.add(entry.getKey().getName() + ": " + e.getMessage());
            }
        }

        return failures;
    }

    /**
     * The square of the station carrying this authored name, or null if no station does.
     */
    private TileKey tileNamed(String name)
    {
        if (name == null) return null;

        // Every named square, not only the ones the reduction has Points for.
        //
        // The reduction is built without the excluded pages, so a label naming a station on one of them
        // matched nothing - and the migration then stripped the label anyway, which is the loss twice
        // over: the caption was never recorded and the label it came from is gone.  Excluding a page has
        // to be reversible, and this is the one place that quietly was not.
        for (Map.Entry<String, String> entry : store.getPointNames().entrySet())
        {
            if (name.equals(entry.getValue()))
            {
                TileKey tile = AutonomyCompanionStore.parseTileKey(entry.getKey());

                if (tile != null) return tile;
            }
        }

        return null;
    }

    public List<LayoutDiagram> getPages()
    {
        return Collections.unmodifiableList(pages);
    }

    /**
     * The per-point keys a configuration owns: everything operational parseAuto accepts on a point.
     * Structural keys (name, station, s88, coordinates) belong to the reduction and are not here.
     */
    /**
     * Terminus and reversing are deliberately absent: both are DERIVED from the three switches at build
     * time, so the running graph carries the builder's answer rather than the user's.  Capturing one
     * would write "reversing" onto the square somebody marked "trains can turn round here", and the
     * next build would then reverse every train that passed it.
     */
    private static final List<String> POINT_OPERATIONAL_KEYS = java.util.Arrays.asList(
        "loc", "active", "maxTrainLength", "speedMultiplier",
        "priority", "home", "excludedLocs");

    /**
     * The generated configuration, in the format the autonomy model already reads.
     * @return
     */
    public String buildConfiguration()
    {
        return builder(globals()).build();
    }

    /**
     * A builder configured from this setup.
     *
     * The configuration used to be assembled by hand at each of four call sites, and they drifted:
     * one left out the split settings and so named a railway with no copies in it, another left out
     * the barred arrivals.  Whatever a builder is asked for - the file, the naming, the coordinates -
     * it must be describing the same railway, so there is one place that says what that railway is.
     *
     * @param globals the run-wide settings, or null when only the naming is wanted
     * @return a fresh builder
     */
    public AutonomyBuilder builder(AutonomyBuilder.Globals globals)
    {
        return new AutonomyBuilder(reducer, globals)
            .withPointExtras(pointExtras())
            .withReversibleTiles(reversibleTiles())
            .withMandatoryTurns(mandatoryTurnTiles())
            .withParkingTiles(parkingTiles())
            .withBarredArrivals(barredArrivals())
            .withProtectingSignals(protectingSignalNames());
    }

    /**
     * Which sides each station refuses to let trains arrive by.
     *
     * @return square to barred sides, only for the squares that restrict anything
     */
    /**
     * @param station a station's square
     * @return the square of the first signal protecting it, or null
     */
    public TileKey getProtectingSignal(TileKey station)
    {
        return store.getProtectingSignal(station);
    }

    /**
     * @param station a station's square
     * @return the squares of every signal protecting it, in the order they were paired
     */
    public List<TileKey> getProtectingSignals(TileKey station)
    {
        return store.getProtectingSignals(station);
    }

    /**
     * @param station a station's square
     * @param signal the signal's square, or null to unpair
     */
    public void setProtectingSignal(TileKey station, TileKey signal)
    {
        store.setProtectingSignal(station, signal);
        touched();
    }

    /**
     * Replaces every signal protecting a station.
     *
     * @param station a station's square
     * @param signals the signals' squares; empty or null unpairs
     */
    public void setProtectingSignals(TileKey station, List<TileKey> signals)
    {
        store.setProtectingSignals(station, signals);
        touched();
    }

    /**
     * Which ACCESSORIES protect each station, by name.
     *
     * The store pairs squares, because a square survives everything; the running layout commands
     * accessories, which it knows by name.  This is the join, and it is made here because only the
     * session has the diagram to read the address off.
     *
     * A pairing whose signal tile has gone, or carries no address, is left out rather than emitted as
     * something the layout would fail to find - and a station whose signals have ALL gone that way is
     * left out entirely rather than emitted with an empty list.
     *
     * @return station square to accessory names
     */
    public Map<TileKey, List<String>> protectingSignalNames()
    {
        Map<TileKey, List<String>> out = new LinkedHashMap<>();

        if (graph == null) return out;

        for (Map.Entry<TileKey, List<TileKey>> pair : store.getProtectingSignals().entrySet())
        {
            List<String> names = new ArrayList<>();

            for (TileKey tile : pair.getValue())
            {
                LayoutDiagramComponent signal = graph.getTiles().get(tile);

                if (signal == null || signal.getAccessory() == null) continue;

                String name = signal.getAccessory().getName();

                if (!names.contains(name)) names.add(name);
            }

            if (!names.isEmpty()) out.put(pair.getKey(), names);
        }

        return out;
    }

    public Map<TileKey, Set<Side>> barredArrivals()
    {
        return store.getBarredArrivals();
    }

    /**
     * @param tile a station's square
     * @return the sides trains may not arrive by, empty when it takes them from anywhere
     */
    public Set<Side> getBarredArrivals(TileKey tile)
    {
        Set<Side> barred = store.getBarredArrivals(tile);

        if (barred.isEmpty()) return barred;

        // Only the sides that still exist.
        //
        // A restriction is stored against a side of the square as the diagram was when it was set, and
        // the diagram moves: a tile replaced or rotated, an approach re-plumbed, and the square now
        // arrives from somewhere else entirely.  The stale side is dead in the build - there is no copy
        // for it - but it was still counted, and the count is what decides whether the menu will let
        // you shut another side.  A station could end up with every box ticked, every box disabled, and
        // nothing on screen to say why.
        barred.retainAll(arrivalSides(tile));

        return barred;
    }

    /**
     * @param tile a station's square
     * @param barred the sides trains may not arrive by
     */
    public void setBarredArrivals(TileKey tile, Set<Side> barred)
    {
        store.setBarredArrivals(tile, barred);
        touched();
    }

    /**
     * The sides a train can actually arrive at a square by.
     *
     * Read from the split rather than from the tile's ports: the split is what decides how many copies
     * a square becomes, and a side no copy exists for is not somewhere a restriction could bite.  So
     * the editor offers exactly the choices that mean something.
     *
     * @param tile
     * @return the arrival sides, in the order the build emits them
     */
    public List<Side> arrivalSides(TileKey tile)
    {
        return getStationIndex().arrivalSidesAt(tile);
    }

    /**
     * The squares where a train may turn round, which the builder emits as several Points each.
     *
     * Two ways to be one, and they are the same physical act seen from either side of the station
     * question:
     *   - a station marked TERMINUS, which is where a train ends its run and reverses.  As a single
     *     Point it is a dead end for routing - isPathClear refuses any path with a terminus in the
     *     middle - so a through platform that some trains terminate at could not be expressed at all.
     *   - anything else marked CAN REVERSE, which is a place a train changes direction on its way
     *     somewhere else: the move that reaches a siding trailing off behind it.
     *
     * @return the marked tiles
     */
    /**
     * The name the track diagram knows a running Point by.
     *
     * A split tile is several Points - "Main 4 (eastbound, reverse)" and the rest - but only ever one
     * caption on the diagram, written before any of them existed.  Without this the label for a split
     * station never fills in: it is registered under the base name and the running Point never has it.
     *
     * @param pointName a Point of the running configuration
     * @return the base name, or the name itself when nothing was split
     */
    public String baseNameOf(String pointName)
    {
        return pointName == null ? null : getStationIndex().baseNameOf(pointName);
    }

    // Emitted Point name -> the caption on the diagram.  Cached because updateStationLabels asks on
    // every feedback event during a run, and working it out walks every point against every edge.
    // Dropped whenever the graph is rebuilt, which is the only thing that can change it.
    // volatile: the labels are updated from the feedback thread while a rebuild can drop this from the
    // event thread, and a stale reference here means a station that stops filling in until the next edit
    private volatile StationIndex stationIndex;

    /**
     * The translation between squares and the Points derived from them.
     *
     * Derived once and held until the setup changes, because it is asked for constantly - once per
     * Point on every feedback event, in places - and because deriving it twice is how the answers
     * started disagreeing.  Everything that used to build its own AutonomyBuilder to answer half of
     * this question now asks here instead.
     *
     * @return the index, never null
     */
    public StationIndex getStationIndex()
    {
        StationIndex current = stationIndex;

        // Derived here only before the first rebuild, when nothing is running and nobody else can be
        // asking.  Every other path derives it eagerly - see deriveStationIndex - because the readers
        // are not on the thread the writers are on.
        if (current == null)
        {
            current = deriveStationIndex();
        }

        return current;
    }

    /**
     * Works the index out now, rather than leaving it for whoever asks first.
     *
     * Who asks first is the problem.  The labels are updated once per Point on every feedback event,
     * from the control station's own thread, while the event thread is free to edit the configuration -
     * setPointProperty is documented as usable while autonomy runs.  Deriving lazily meant that
     * feedback thread walking the configuration's JSONObjects, which are backed by plain HashMaps, at
     * the moment the event thread was writing to them.  A volatile field publishes the REFERENCE
     * safely; it says nothing about what happens inside the derivation.
     *
     * Called from the two places that invalidate it, both on the event thread, so a reader only ever
     * sees a finished immutable index - which is what this class has claimed all along.
     *
     * @return the new index, also stored
     */
    private StationIndex deriveStationIndex()
    {
        StationIndex derived = reducer == null ? StationIndex.EMPTY : new StationIndex(builder(null));

        stationIndex = derived;

        return derived;
    }

    /**
     * Every Point of the running configuration that stands for one square of the diagram.
     *
     * @param baseName the caption on the diagram
     * @return the emitted names, in the order they were emitted
     */
    /**
     * Every copy of a station, and which way a train standing on it would be pointing.
     *
     * A square is several Points - one per side a train can arrive by - and they are not
     * interchangeable: each one can only leave the way its own facing allows.  So "put this locomotive
     * here" is not a complete instruction, and answering it by taking the first copy puts the train on
     * a Point whose only moves are the ones the split exists to forbid.  That is a train autonomy can
     * see and cannot route.
     *
     * @param tile the station's square
     * @return the name of each copy against the side its train would face, in the order the build made
     *         them, empty when there is no setup or the square is not a Point
     */
    public Map<String, Side> facingsFor(TileKey tile)
    {
        return getStationIndex().facingsAt(tile);
    }

    /**
     * Whether two Points are the same piece of track.
     *
     * A square is several Points now - one per side a train can arrive by - so a train standing at
     * BottomMainB was being offered a path to BottomMainB, the copy facing the other way being a
     * different Point.  Going there is not a journey; it is the place it is already standing.
     *
     * Asked HERE rather than of the running layout, which cannot answer it.  The obvious key there
     * is the sensor, and the sensor is wrong: a station and its approach guard legitimately share
     * one and are genuinely two places.  What makes copies copies is the SQUARE they came from, and
     * only the setup knows which square a Point was built from.
     *
     * @param a a point name from the built graph
     * @param b another
     * @return true when both were built from one square
     */
    public boolean sameSquare(String a, String b)
    {
        if (a == null || b == null) return false;

        // Identical names are the same place even when the index has never heard of either - a
        // configuration built before the last change still has Points, and they are still somewhere.
        return a.equals(b) || getStationIndex().sameSquare(a, b);
    }

    public List<String> pointNamesFor(String baseName)
    {
        return getStationIndex().pointNamesFor(baseName);
    }

    /**
     * What the generated configuration calls the Point on a square.
     *
     * The BASE name - the one a diagram caption carries and the one anything looking a Point up by
     * caption will find.  Not the authored name, which can be blank or a duplicate; uniqueNames is
     * what settles both.
     *
     * @param tile
     * @return the name, or null when the square is not a Point
     */
    /**
     * The square a running Point stands on.
     *
     * The inverse of the naming the builder does, including the split copies - "Bahnhof (eastbound)"
     * and "Bahnhof (westbound)" both answer with the one square they are copies of.  This is what lets
     * anything holding a Point find the caption showing it, without going through its name.
     *
     * @param pointName a Point of the running configuration
     * @return its square, or null if this setup has never emitted that name
     */
    public TileKey tileForPointName(String pointName)
    {
        return getStationIndex().squareOf(pointName);
    }

    public String pointNameForTile(TileKey tile)
    {
        return getStationIndex().nameOf(tile);
    }

    public Set<TileKey> reversibleTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (isTurnAround(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * The stations that are parking berths.
     * @return
     */
    public Set<TileKey> parkingTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (isParking(tile)) out.add(tile);
        }

        return out;
    }

    /**
     * Whether trains may turn round on this square.
     *
     * Reads the older keys as well as the current one.  A setup authored before the three switches -
     * station, turn round, parking - said the same things as "terminus" and "reversing", and those are
     * now DERIVED rather than set: a configuration carrying them would otherwise quietly lose its
     * termini the first time it was rebuilt.  Nothing is rewritten on disk; the old spelling is simply
     * still understood, and the first edit to a square writes the new one.
     *
     * @param tile
     * @return
     */
    /**
     * Whether every train arriving here must turn round, rather than merely being able to.
     * @param tile
     * @return
     */
    public boolean isMustTurnAround(TileKey tile)
    {
        return Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.MUST_REVERSE));
    }

    /**
     * The squares where turning round is compulsory.
     * @return
     */
    /**
     * The squares where a run may change direction, as the path search wants them - reversible squares
     * minus the ones where turning is compulsory.
     *
     * One source, so the editor's path test and the reachability check cannot draw different turn sets
     * and then disagree about which stations connect.  It is the same computation both did inline.
     *
     * @return the may-turn set
     */
    public Set<TileKey> mayTurnTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>(reversibleTiles());
        out.removeAll(mandatoryTurnTiles());
        return out;
    }

    public Set<TileKey> mandatoryTurnTiles()
    {
        Set<TileKey> out = new LinkedHashSet<>();

        if (reducer == null) return out;

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (isMustTurnAround(tile)) out.add(tile);
        }

        return out;
    }

    public boolean isTurnAround(TileKey tile)
    {
        if (isMustTurnAround(tile)) return true;

        if (Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.CAN_REVERSE))) return true;

        // a terminus was always "a station where trains turn round"
        if (Boolean.TRUE.equals(getPointProperty(tile, "terminus"))) return true;

        // a reversing point that is NOT a station was "somewhere trains turn round on the way past"
        return Boolean.TRUE.equals(getPointProperty(tile, "reversing")) && !store.isStation(tile);
    }

    /**
     * Whether this station is a parking berth - somewhere autonomy never sends a train of its own
     * accord, and cannot route one through.
     *
     * @param tile
     * @return
     */
    public boolean isParking(TileKey tile)
    {
        if (!store.isStation(tile)) return false;

        // the switch as it is stored now: written only when it is off, like every other default
        if (Boolean.FALSE.equals(getPointProperty(tile, AutonomyBuilder.AUTO_DESTINATION))) return true;

        // what it was called for the hour this was spelt "parking"
        if (Boolean.TRUE.equals(getPointProperty(tile, AutonomyBuilder.PARKING))) return true;

        // and before that, a reversing STATION was the only way to say it at all
        return Boolean.TRUE.equals(getPointProperty(tile, "reversing"));
    }

    /**
     * Whether full autonomy may choose this station of its own accord.  The switch the user sees.
     * @param tile
     * @return
     */
    public boolean isAutoDestination(TileKey tile)
    {
        return !isParking(tile);
    }

    /**
     * Sets whether autonomy may choose this station, clearing the two older spellings of the same idea.
     *
     * @param tile
     * @param on
     */
    public void setAutoDestination(TileKey tile, boolean on)
    {
        setPointProperty(tile, AutonomyBuilder.AUTO_DESTINATION, on ? null : Boolean.FALSE);
        setPointProperty(tile, AutonomyBuilder.PARKING, null);
        setPointProperty(tile, "reversing", null);
    }

    /**
     * Sets one of the three switches, clearing the older spellings of the same idea so that a square
     * cannot end up saying one thing in two vocabularies.
     *
     * @param tile
     * @param key CAN_REVERSE or PARKING
     * @param on
     */
    public void setPointFlag(TileKey tile, String key, boolean on)
    {
        setPointProperty(tile, key, on ? Boolean.TRUE : null);

        // Never authored again, whichever way this went: they are derived at build time now, and one
        // left behind would keep asserting itself after the switch that set it had been turned off.
        setPointProperty(tile, "terminus", null);
        setPointProperty(tile, "reversing", null);
    }

    /**
     * The same, laid out like the track it came from, for looking at in the graph window.
     * @return
     */
    public String buildConfigurationForInspection()
    {
        List<String> pageOrder = new ArrayList<>();

        for (LayoutDiagram page : pages)
        {
            if (!store.getExcludedPages().contains(page.getName())) pageOrder.add(page.getName());
        }

        return builder(globals()).withCoordinatesFromTiles(pageOrder).build();
    }

    /**
     * The per-point operational data of the active configuration, for the builder to merge in.
     */
    private Map<String, org.json.JSONObject> pointExtras()
    {
        Map<String, org.json.JSONObject> out = new LinkedHashMap<>();

        String active = store.getActiveConfiguration();

        if (active == null) return out;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return out;

        org.json.JSONObject points = configuration.getJSONObject("points");

        for (String key : points.keySet())
        {
            out.put(key, points.getJSONObject(key));
        }

        return out;
    }

    /**
     * Lifts what the running layout knows into the active configuration - placements, homes, termini,
     * pace settings - so that what was set while trains were running is what loads next time.
     *
     * Takes the layout's own JSON rather than the layout, for two reasons: toJSON is the serialization
     * the legacy path trusted for years, so anything it captures is by definition loadable; and a
     * string can be tested without a control station.
     *
     * Keyed by tile rather than by name, so a Point renamed between sessions keeps its placements.
     * Points whose names no longer match any tile are dropped silently - they belong to track that no
     * longer exists, and carrying them forward would place a locomotive on nothing.
     *
     * @param layoutJson what the running Layout serialized to
     */
    public void captureFromLayout(String layoutJson)
    {
        captureFromLayout(layoutJson, store.getActiveConfiguration());
    }

    /**
     * The same, into a named configuration - for callers that know which configuration the running
     * layout was generated from.  The two can differ: a load that was refused partway leaves the store
     * pointing at a configuration that never ran, and capturing into it would overwrite it with another
     * configuration's state.
     *
     * @param layoutJson
     * @param configurationName which configuration this layout's state belongs to
     */
    public void captureFromLayout(String layoutJson, String configurationName)
    {
        if (layoutJson == null || reducer == null || configurationName == null) return;

        org.json.JSONObject configuration = store.getConfiguration(configurationName);

        if (configuration == null) return;

        org.json.JSONObject root = new org.json.JSONObject(layoutJson);

        // name -> tile, through the same naming the builder used to generate the file
        Map<String, TileKey> tilesByName = new LinkedHashMap<>();

        AutonomyBuilder naming = builder(null);

        tilesByName.putAll(naming.tilesByName());

        // Which way a train standing on each emitted Point is pointing.  See the facing capture below.
        Map<String, TilePorts.Side> facings = naming.facingByName();

        org.json.JSONObject points = new org.json.JSONObject();

        if (root.has("points"))
        {
            for (Object o : root.getJSONArray("points"))
            {
                org.json.JSONObject point = (org.json.JSONObject) o;

                TileKey tile = tilesByName.get(point.optString("name"));

                if (tile == null) continue;

                org.json.JSONObject extras = new org.json.JSONObject();

                for (String key : POINT_OPERATIONAL_KEYS)
                {
                    if (!point.has(key) || point.isNull(key)) continue;



                    // A placement records WHICH locomotive stands here and nothing else.  Point.toJSON
                    // also writes its length, reversibility, speed and functions, and parseAuto applies
                    // those back onto the Locomotive - so capturing them made loading a configuration
                    // silently revert changes made in the locomotive UI since.  Those live in LocDB.
                    if ("loc".equals(key) && point.get(key) instanceof org.json.JSONObject)
                    {
                        org.json.JSONObject loc = point.getJSONObject(key);

                        if (!loc.has("name")) continue;

                        extras.put(key, new org.json.JSONObject().put("name", loc.getString("name")));

                        continue;
                    }

                    extras.put(key, point.get(key));
                }

                // Which way the train ended up pointing, learned rather than asked for.  A square is
                // several Points once it is split, and the one a locomotive is standing on says which
                // way round it is - so after autonomy has run once, nobody has to answer that question.
                TilePorts.Side facing = facings.get(point.optString("name"));

                if (facing != null && extras.has("loc"))
                {
                    extras.put(AutonomyBuilder.FACING, facing.name());
                }

                // An empty one is still recorded, and must be.  Skipping it meant a square the running
                // layout had NOTHING to say about never entered this map, so the merge below never ran
                // for it and never reached its `else remove` - and a locomotive that had driven away
                // from a plain sensor stayed placed there in the configuration.  The next build emitted
                // the same locomotive twice, on the square it left and the square it reached.

                // Merged, not replaced.  A split square is visited once per copy and only ONE of them
                // carries the locomotive, so putting each copy's extras in turn meant the last copy read
                // won - and if that was not the copy the train was on, the placement was lost.
                String id = tile.toString();

                org.json.JSONObject into = points.has(id)
                    ? points.getJSONObject(id) : new org.json.JSONObject();

                for (String key : extras.keySet()) into.put(key, extras.get(key));

                points.put(id, into);
            }
        }

        // Merged per point, not substituted wholesale.  The running Layout was built BEFORE any edits
        // made in the editor since, so replacing the whole object discarded them - set a terminus,
        // press Apply, exit, and it was gone.  What the running layout knows about is overwritten;
        // everything else is left alone.
        org.json.JSONObject existing = configuration.has("points")
            ? configuration.getJSONObject("points") : new org.json.JSONObject();

        for (String id : points.keySet())
        {
            org.json.JSONObject captured = points.getJSONObject(id);
            org.json.JSONObject before = existing.has(id)
                ? existing.getJSONObject(id) : new org.json.JSONObject();

            // Keys the layout can speak for are replaced - including being REMOVED when the layout no
            // longer carries them, which is how a property returned to its default is cleared.
            for (String key : POINT_OPERATIONAL_KEYS)
            {
                if (captured.has(key)) before.put(key, captured.get(key));
                else before.remove(key);
            }

            // Not one of those keys, because the running layout has no field for it: it is worked out
            // from WHICH copy of a split square the locomotive was found on.  Only ever written here,
            // never cleared - a square with no train on it still remembers which way the last one was
            // pointing, and that is the better guess for the next one.
            if (captured.has(AutonomyBuilder.FACING))
            {
                before.put(AutonomyBuilder.FACING, captured.get(AutonomyBuilder.FACING));
            }

            existing.put(id, before);
        }

        // A square whose TILE is gone keeps nothing.  A square that is merely not a Point keeps
        // everything.
        //
        // This used to be judged against the squares that are still POINTS, and those are not the same
        // set: a sensor reduces to a Point only where track connects it to something.  Nudge a station
        // one square so that it no longer joins the run either side of it and it stops being a Point
        // while remaining perfectly present on the diagram - and the next capture deleted its
        // locomotive, its facing and its markings.  Adam found it doing exactly that, and asked for the
        // opposite: keep the placement, let the build refuse it, and have it come back when the track
        // is joined up again.
        //
        // Which is what this does now.  A disconnected station is a mistake somebody is in the middle
        // of making, not an instruction to forget the train that was standing there; the check reports
        // it, and reconnecting the track brings it back with nothing to re-enter.  Deleting the tile is
        // still deleting it - the tile is then not in the graph either.
        //
        // (Judged against squares rather than against what this capture had something to say about,
        // which is the older reasoning and still holds: a square marked "trains may turn round here"
        // and nothing else carries no operational data, so keying the prune on what was captured
        // deleted the marking the first time autonomy ran.)
        Set<String> stillThere = new LinkedHashSet<>();

        for (TileKey tile : graph.getTiles().keySet()) stillThere.add(tile.toString());

        // Which pages this reduction was even allowed to look at.  A page left out of autonomy has no
        // Points in the reduction, so judging its squares by that reduction condemns every one of them -
        // and excluding a page has to be reversible, or a page ticked off and back on has silently lost
        // its placements, its facings and its markings.
        // Taken from the layout's own pages, not from the pages the reduction happens to have Points on.
        // A page whose last sensor was deleted has no Points, so inferring the list from the reduction
        // quietly exempted it forever: its stale placements and markings could never be pruned, and if a
        // page of that name was ever added back they came back with it - a locomotive recorded as
        // standing on track it is not on.
        Set<String> pagesInPlay = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            if (!store.getExcludedPages().contains(page.getName())) pagesInPlay.add(page.getName());
        }

        List<String> gone = new ArrayList<>();

        for (String id : existing.keySet())
        {
            if (stillThere.contains(id)) continue;

            TileKey tile = AutonomyCompanionStore.parseTileKey(id);

            // Unparseable, or on a page this setup is not looking at: left alone rather than judged by
            // a reduction that was never given the chance to see it.
            if (tile == null || !pagesInPlay.contains(tile.getPage())) continue;

            gone.add(id);
        }

        for (String id : gone) existing.remove(id);

        configuration.put("points", existing);

        // and the top of the file: pace, speeds, and the rest of the settings panel
        org.json.JSONObject globals = new org.json.JSONObject();

        for (String key : root.keySet())
        {
            if (!"points".equals(key) && !"edges".equals(key)) globals.put(key, root.get(key));
        }

        configuration.put("globals", globals);

        dirty = true;
    }

    /**
     * The globals of the active configuration, which is where pace and speed settings live.
     */
    private AutonomyBuilder.Globals globals()
    {
        AutonomyBuilder.Globals globals = new AutonomyBuilder.Globals();

        String active = store.getActiveConfiguration();

        if (active == null) return globals;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("globals")) return globals;

        org.json.JSONObject stored = configuration.getJSONObject("globals");

        for (String key : stored.keySet())
        {
            globals.set(key, stored.get(key));
        }

        return globals;
    }

    /**
     * Whether a square can meaningfully be given a direction.
     *
     * False for a link.  A link's route is a stub - the same side twice - because the tile conducts
     * track on one face and a jump on the other, and a jump is not a side.  "Toward A" and "toward B"
     * therefore name the same place, and the traversal would allow both whichever was chosen: a
     * setting that silently does nothing, which is worse than no setting at all.
     *
     * Here rather than in the menu that asks it, so the rule has a name, one home, and a test.  Making
     * a link one-way is a real thing to want and needs the JUMP to carry the direction; that is on the
     * backlog, and this returns false until it exists.
     *
     * @param tile the square
     * @return true when the direction answers mean something there
     */
    public boolean canCarryDirection(TileKey tile)
    {
        if (graph == null || tile == null) return false;

        LayoutDiagramComponent component = graph.getTiles().get(tile);

        if (component == null) return false;

        if (org.traincontrol.automationui.TilePorts.hasPortal(component.getType())) return false;

        return !getRoutes(tile).isEmpty();
    }

    /**
     * The stations whose paired protecting signal no longer resolves to an accessory.
     *
     * The same walk protectingSignalNames does, keeping what it skips.  It has to skip them - a build
     * cannot emit an accessory that is not there - but skipping in silence is what leaves an operator
     * believing a platform is protected when it is not.
     */
    /**
     * The stations carrying no protecting signal at all.
     *
     * Distinct from signalsThatAreGone, which is a pairing that no longer resolves. This one is
     * "nothing was ever set", which is ordinary and worth listing rather than worth warning about -
     * a signal pairing can otherwise only be checked one station at a time.
     */
    private java.util.Set<TileKey> stationsWithNoSignal()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        if (graph == null) return out;

        for (TileKey tile : graph.getTiles().keySet())
        {
            if (!store.isStation(tile)) continue;

            if (store.getProtectingSignals(tile).isEmpty()) out.add(tile);
        }

        return out;
    }

    /**
     * Squares whose recorded facing is not one the square can actually hold.
     *
     * The facing says which way the train standing there points, and the build honours it by choosing
     * the copy of the split square that faces that way.  When no copy does, the builder falls through
     * to the first one - it has to place the train somewhere - and that copy may point the other way.
     * So the train quietly turns round.
     *
     * The way squares stop being able to hold a facing is that the track around them changes: move a
     * tile so that it is entered from different sides and the facings it offers change with it, while
     * the recorded one stays as it was.  Adam met exactly that - "the locomotive direction suddenly
     * changed" after moving a tile onto valid connected track.
     *
     * Reported rather than corrected, because there is no correct answer available here: the train is
     * physically pointing whichever way it is pointing, and only the operator knows.  What was missing
     * was being told at all.
     *
     * A square with NO facings is left out - that is a disconnected square, which has its own findings
     * and does not need this one on top.
     */
    private java.util.Set<TileKey> facingsThatCannotBeHeld()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        for (Map.Entry<TileKey, String> placed : placedLocomotives().entrySet())
        {
            Side recorded = getFacing(placed.getKey());

            if (recorded == null) continue;

            List<Side> offered = facingChoices(placed.getKey());

            if (offered.isEmpty() || offered.contains(recorded)) continue;

            out.add(placed.getKey());
        }

        return out;
    }

    private java.util.Set<TileKey> signalsThatAreGone()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        if (graph == null) return out;

        // ANY of them, not all: a station paired to two signals of which one has gone is protected on
        // one approach and not on the other, which is exactly the state worth warning about.
        for (Map.Entry<TileKey, List<TileKey>> pair : store.getProtectingSignals().entrySet())
        {
            for (TileKey tile : pair.getValue())
            {
                LayoutDiagramComponent signal = graph.getTiles().get(tile);

                if (signal == null || signal.getAccessory() == null)
                {
                    out.add(pair.getKey());
                    break;
                }
            }
        }

        return out;
    }

    /**
     * The squares an authored home locomotive lives at.
     */
    private java.util.Set<TileKey> homeTiles()
    {
        java.util.Set<TileKey> out = new LinkedHashSet<>();

        String active = store.getActiveConfiguration();

        if (active == null) return out;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return out;

        org.json.JSONObject points = configuration.getJSONObject("points");

        for (String id : points.keySet())
        {
            org.json.JSONObject stored = points.optJSONObject(id);

            if (stored == null) continue;

            String home = stored.optString("home", "");

            if (home.trim().isEmpty()) continue;

            TileKey tile = AutonomyCompanionStore.parseTileKey(id);

            if (tile != null) out.add(tile);
        }

        return out;
    }

    /**
     * Everything wrong or worth knowing about the setup as it stands.
     * @return
     */
    public List<AutonomyChecks.Finding> check()
    {
        // Guarded because a panel builds its list in its constructor, and nothing yet forces open() to
        // have been called first - so an unopened session would throw out of a constructor, which is a
        // much harder failure to read than an empty list.
        if (graph == null || reducer == null) return new ArrayList<AutonomyChecks.Finding>();

        // The terminus flag lives in the configuration, so the checks are told rather than left to
        // infer it from the shape of the graph.
        Set<TileKey> termini = new LinkedHashSet<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (Boolean.TRUE.equals(getPointProperty(tile, "terminus"))) termini.add(tile);
        }

        // "May turn round here" on a square with one way in cannot mean what it says: there is no
        // straight on to carry on to, so every train turns whatever the setting.
        Set<TileKey> pointless = new LinkedHashSet<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (!isTurnAround(tile) || isMustTurnAround(tile)) continue;

            Set<Side> arrivals = new LinkedHashSet<>();

            for (GraphReducer.ReducedEdge edge : reducer.getEdges())
            {
                if (edge.getEnd().equals(tile) && edge.getEntrySide() != null)
                {
                    arrivals.add(edge.getEntrySide());
                }
            }

            if (arrivals.size() < 2) pointless.add(tile);
        }

        // Squares a train can reach and then not leave: it arrived by one side, the only way on is back
        // out of that same side, and nobody has said trains may turn round there.
        Set<TileKey> trapped = new LinkedHashSet<>();

        for (TileKey tile : reducer.getPoints().keySet())
        {
            if (isTurnAround(tile)) continue;

            Set<Side> arrivals = new LinkedHashSet<>();
            Set<Side> departures = new LinkedHashSet<>();

            for (GraphReducer.ReducedEdge edge : reducer.getEdges())
            {
                if (edge.getEnd().equals(tile) && edge.getEntrySide() != null)
                {
                    arrivals.add(edge.getEntrySide());
                }

                if (edge.getStart().equals(tile) && edge.getExitSide() != null)
                {
                    departures.add(edge.getExitSide());
                }
            }

            for (Side arrival : arrivals)
            {
                // The track the train is standing on, not every side the square has.
                //
                // The builder asks this question through the arriving ROUTE, and hands the answer here
                // when it decides to emit a Point with no way out - so asking it a different way meant
                // the one case the builder explicitly delegates could go unreported: a double curve
                // whose one track dead-ends while the other carries traffic looked fine, because the
                // other curve's departures counted as somewhere to go.
                Set<Side> onwards = new LinkedHashSet<>();

                for (TileGraph.Exit exit : graph.exits(tile, arrival))
                {
                    if (exit.getSide() != null && departures.contains(exit.getSide()))
                    {
                        onwards.add(exit.getSide());
                    }
                }

                onwards.remove(arrival);

                if (onwards.isEmpty()) trapped.add(tile);
            }
        }

        // Captions the user’s own writing is sitting on top of.
        //
        // The square belongs to the diagram - it is their drawing before it is autonomy’s data - so
        // the text wins the square and the caption is the one that goes quiet.  Worth saying rather than
        // silently losing: a station that looks captioned and shows nothing is exactly the puzzle this
        // whole rework exists to stop.
        Map<TileKey, TileKey> covered = new LinkedHashMap<>();

        for (Map.Entry<TileKey, TileKey> caption : store.getCaptions().entrySet())
        {
            LayoutDiagram page = pageOf(caption.getKey());

            if (page == null) continue;

            LayoutDiagramComponent component =
                page.getComponent(caption.getKey().getX(), caption.getKey().getY());

            if (component == null || component.getLabel() == null) continue;

            if (!component.getLabel().trim().isEmpty()) covered.put(caption.getKey(), caption.getValue());
        }

        return AutonomyChecks.run(graph, reducer, termini, getLabelledStationTiles(), pointless,
            trapped, covered, placedLocomotives(), shutStations(),
            mayTurnTiles(), mandatoryTurnTiles(), homeTiles(), signalsThatAreGone(),
            stationsWithNoSignal(), facingsThatCannotBeHeld());
    }

    /**
     * Whether anything would stop this being built.
     * @return
     */
    public boolean hasBlockingProblems()
    {
        return graph != null && graph.hasBlockingProblems();
    }

    // --- editing ----------------------------------------------------------------------------------

    public void setDirection(TileKey tile, RouteId routeId, Direction direction)
    {
        record(tile, routeId, direction);
        touched();
    }

    /**
     * Records a direction without re-deriving, for callers that are about to set several.
     */
    private void record(TileKey tile, RouteId routeId, Direction direction)
    {
        graph.setDirection(tile, routeId, direction);

        // stored only when it differs from what the graph would default to, so a default never looks
        // like a decision somebody made
        store.setTileDirection(tile, routeId,
            direction == graph.defaultDirection(tile, routeId) ? null : direction);
    }

    /**
     * Applies one direction to many tiles at once.
     *
     * The reason bulk editing matters rather than being a convenience: switches default to base-to-forks,
     * so on a real layout most of the setting up is opening trailing moves, and doing that one tile at a
     * time would be the bulk of the work.
     *
     * @param tiles
     * @param direction
     */
    public void setDirection(Set<TileKey> tiles, Direction direction)
    {
        // Recorded first and re-derived once at the end.  Going through the single-tile setter would
        // rebuild the entire graph per route - forty tiles meaning forty full rebuilds on the event
        // thread, for the gesture that exists precisely because it is the common one.
        for (TileKey tile : tiles)
        {
            for (RouteId routeId : graph.getRoutes(tile).keySet())
            {
                record(tile, routeId, direction);
            }
        }

        touched();
    }

    /**
     * Applies a direction to each of one tile's routes separately, re-deriving once.
     *
     * The per-branch counterpart to the bulk setter above, and needed for the same reason: the two
     * states a junction has - trains converging on its single track, and trains leaving it - are
     * DIFFERENT Direction constants on different branches, because TOWARD_A names a route's own first
     * side and nothing makes those agree.  Setting them one at a time through the single setter would
     * rebuild the graph once per branch.
     *
     * @param tile
     * @param directions route to the direction it should take
     */
    public void setDirections(TileKey tile, Map<RouteId, Direction> directions)
    {
        for (Map.Entry<RouteId, Direction> entry : directions.entrySet())
        {
            record(tile, entry.getKey(), entry.getValue());
        }

        touched();
    }

    /**
     * Tells autonomy to use, or to ignore, a link.
     *
     * @param tile the link
     * @param disabled true to leave it out of the railway entirely
     */
    public void setPortalDisabled(TileKey tile, boolean disabled)
    {
        store.setPortalDisabled(tile, disabled);
        touched();
    }

    /**
     * Renames a station.
     *
     * Nothing else has to happen.  A caption points at the station’s SQUARE, so it follows a rename
     * without being touched - which is the whole reason captions moved out of the diagram.  This used to
     * rewrite every page showing the old name, could fail halfway, and destroyed anything on those pages
     * that the layout parser could not model.
     */
    public void setPointName(TileKey tile, String name)
    {
        store.setPointName(tile, name);
        touched();
    }

    public void setStation(TileKey tile, boolean station)
    {
        store.setStation(tile, station);

        // A caption names a station, so demoting one takes its name plaque with it.
        //
        // Left behind, the plaque outlives the thing it names: the square is drawn as a plain point
        // and the label under it stays registered, so a train reversing there lights up a station
        // name for a place that is no longer a station.  Which is exactly what happened, and read as
        // the diagram contradicting itself.
        //
        // Promotion already places a caption, so the pair is symmetrical - and re-promoting gives the
        // plaque back, on the square the placer picks.
        if (!station) clearCaptions(tile, null);

        // And so does any restriction on how trains may arrive at it.  It is inert while the square is
        // not a station, so leaving it costs nothing today and everything the day somebody makes the
        // square a station again and finds it refusing trains for a reason recorded months ago.
        if (!station) store.setBarredArrivals(tile, null);

        // And the signal that protected it: a plain point is not somewhere trains are held out of.
        if (!station) store.setProtectingSignal(tile, null);

        touched();
    }

    /**
     * The name of the locomotive this configuration puts on a square.
     *
     * A placement is an OBJECT - {"name": ..., "speed": ..., "arrivalFunc": ...} - because parseAuto
     * resets whatever a placement omits, so a train's length and functions have to travel with its
     * name rather than beside it.  Anything wanting only the name therefore has to unwrap it, and
     * String.valueOf on a JSONObject gives its JSON: the autonomy editor drew {"name":"EN57-203"}
     * across three tiles of somebody's railway.
     *
     * One place that knows the shape, because there were three and they did not agree.
     *
     * @param tile the square
     * @return the locomotive's name, or null when nothing is placed here
     */
    public String getLocomotiveNameAt(TileKey tile)
    {
        if (tile == null) return null;

        Object placed = getPointProperty(tile, "loc");

        if (placed == null) return null;

        String name;

        if (placed instanceof org.json.JSONObject)
        {
            org.json.JSONObject loc = (org.json.JSONObject) placed;

            // optString, not getString: a placement with no name is not this program's doing, but a
            // hand-edited file can carry one, and asking for a key that is not there throws.
            name = loc.optString("name", null);
        }
        else
        {
            // A bare string.  Not a shape written here, but setup files get edited by hand and an
            // older autonomy.json may hold one - and drawing nothing on an occupied platform is the
            // one wrong answer a label must not give.
            name = String.valueOf(placed);
        }

        if (name == null) return null;

        name = name.trim();

        return name.isEmpty() ? null : name;
    }

    /**
     * Puts a locomotive on a point without disturbing anything else known about it.
     *
     * parseAuto RESETS whatever a placement omits - train length to zero, reversible to false, the
     * arrival and departure functions to none - so writing a bare {"name": X} over an existing
     * placement silently dropped all of them.  Anything already recorded is carried across.
     *
     * @param tile
     * @param name the locomotive, or null to clear the placement
     */
    public void placeLocomotive(TileKey tile, String name)
    {
        if (name == null)
        {
            setPointProperty(tile, "loc", null);

            // The facing described the train that was standing there, not the square.  Left behind,
            // the next locomotive placed here inherits the last one's direction without being asked.
            setPointProperty(tile, AutonomyBuilder.FACING, null);

            return;
        }

        Object existing = getPointProperty(tile, "loc");

        org.json.JSONObject loc = existing instanceof org.json.JSONObject
            ? new org.json.JSONObject(existing.toString()) : new org.json.JSONObject();

        loc.put("name", name);

        // And wherever else it was, it is not there now.
        //
        // Moving a train in the running layout takes it off the point it was on; the configuration is
        // never told, and the configuration is what the next build reads.  So a locomotive placed by
        // hand kept its old placement as well, the build emitted it at two Points, and fromJSON
        // answers that by invalidating the WHOLE layout - after which every path is refused as
        // "configuration is invalid", with nothing pointing back at the placement that caused it.
        //
        // Found in a real export: 065 001-0 DB standing at both BottomMainA and BottomMainC.
        forgetPlacementsElsewhere(tile, name, loc);

        setPointProperty(tile, "loc", loc);
    }

    /**
     * Takes a locomotive off every square except the one it is being put on.
     *
     * Whatever was recorded with it where it is leaving - speed, arrival and departure functions,
     * train length - is carried into the placement being written.  parseAuto RESETS what a placement
     * omits, which is the same reason this method preserves an existing placement above, so a move
     * that dropped them would quietly reset the train to no functions and no length.
     *
     * @param keep the square it is moving to
     * @param name the locomotive
     * @param into the placement being written, which inherits what was recorded elsewhere
     */
    private void forgetPlacementsElsewhere(TileKey keep, String name, org.json.JSONObject into)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return;

        org.json.JSONObject points = configuration.getJSONObject("points");

        for (String key : new LinkedHashSet<>(points.keySet()))
        {
            if (keep != null && key.equals(keep.toString())) continue;

            org.json.JSONObject extras = points.optJSONObject(key);

            if (extras == null) continue;

            org.json.JSONObject standing = extras.optJSONObject("loc");

            if (standing == null || !name.equals(standing.optString("name", null))) continue;

            for (String setting : standing.keySet())
            {
                if (!into.has(setting)) into.put(setting, standing.get(setting));
            }

            extras.remove("loc");
            extras.remove(AutonomyBuilder.FACING);

            touched();
        }
    }

    /**
     * The ways a train standing on this square could be pointing.
     *
     * One per side track arrives by, mapped through onwardFrom - the other end of the route the train
     * came in on, not the opposite of the side it entered by. Those two agree on a straight and differ
     * on a curve, and its own javadoc calls the simpler rule "true by accident": a train entering an
     * N-E curve by N leaves by E, and saying it faces S describes a train sitting across the rails.
     * This sentence used to state that simpler rule as the reason (NR-7).
     *
     * Ordered the same way the builder orders its copies, so the first answer here is the one a
     * placement with no facing recorded actually gets - and more exactly so than when that was
     * written, since onwardFrom and AutonomyBuilder.facingOf now answer alike.
     *
     * @param tile
     * @return the possible facings, empty when nothing reaches the square and a single entry - which
     *         needs no asking about - when only one line does
     */
    public List<Side> facingChoices(TileKey tile)
    {
        Set<Side> arrivals = new java.util.TreeSet<>();

        if (reducer != null)
        {
            for (GraphReducer.ReducedEdge edge : reducer.getEdges())
            {
                if (edge.getEnd().equals(tile) && edge.getEntrySide() != null)
                {
                    arrivals.add(edge.getEntrySide());
                }
            }
        }

        Set<Side> out = new java.util.LinkedHashSet<>();

        for (Side arrival : arrivals) out.addAll(onwardFrom(tile, arrival));

        return new ArrayList<>(out);
    }

    /**
     * Where a train that came in by this side is pointing.
     *
     * The other end of the piece of track it is standing on - NOT the opposite compass point, which is
     * what this used to answer.
     *
     * MT-125, Adam: "feedback 1016/1015 offer south and west as facing directions, instead of north and
     * east." Those are curved squares. On a curve joining north to east, a train entering by the north
     * side leaves by the EAST side: it is pointing east, and south is a direction that square has no
     * track in at all. On a straight the two rules agree, which is why this survived everywhere anybody
     * looked - "a train that came in by the west side is pointing east" is true, and true by accident.
     *
     * Where the square offers a choice - a switch, a double curve - every road out of the side it came
     * in by is offered, because each is somewhere the train could genuinely be pointing and nothing
     * here knows which road it took.
     *
     * @param tile the square
     * @param arrival the side the train came in by
     * @return the ways it could be pointing, falling back to the reverse of the arrival where the
     *         square's track is not known
     */
    private List<Side> onwardFrom(TileKey tile, Side arrival)
    {
        List<Side> out = new ArrayList<>();

        for (Route route : getRoutes(tile).values())
        {
            if (route.getA() == arrival && route.getB() != null) out.add(route.getB());
            else if (route.getB() == arrival && route.getA() != null) out.add(route.getA());
        }

        // A square whose track nothing can describe - the old answer is still the best guess
        if (out.isEmpty()) out.add(arrival.opposite());

        return out;
    }

    /**
     * Which way the locomotive on this square is pointing, as recorded.
     *
     * @param tile
     * @return the side its front faces, or null when nobody has said and nothing has run
     */
    public Side getFacing(TileKey tile)
    {
        Object value = getPointProperty(tile, AutonomyBuilder.FACING);

        if (value == null) return null;

        for (Side side : Side.values())
        {
            if (side.name().equals(value.toString())) return side;
        }

        return null;
    }

    /**
     * Records which way the locomotive on this square is pointing.
     *
     * @param tile
     * @param facing the side its front faces, or null to forget
     */
    public void setFacing(TileKey tile, Side facing)
    {
        setPointProperty(tile, AutonomyBuilder.FACING, facing == null ? null : facing.name());
    }

    /**
     * One of a Point's operational properties, in the active configuration.    /**
     * One of a Point's operational properties, in the active configuration.
     *
     * Kept per configuration rather than beside the track, because these are what a configuration IS:
     * the same railway with different rules about where trains may stand and turn.  The keys are the
     * ones parseAuto reads, so nothing has to translate them on the way out.
     *
     * @param tile
     * @param key terminus, reversing, active, maxTrainLength, speedMultiplier, priority, home,
     *        excludedLocs
     * @param value the value, or null to remove the property entirely
     */
    public void setPointProperty(TileKey tile, String key, Object value)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null) return;

        if (!configuration.has("points")) configuration.put("points", new org.json.JSONObject());

        org.json.JSONObject points = configuration.getJSONObject("points");

        String id = tile.toString();

        if (!points.has(id)) points.put(id, new org.json.JSONObject());

        if (value == null) points.getJSONObject(id).remove(key);
        else points.getJSONObject(id).put(key, value);

        dirty = true;

        // The split names are computed from these properties - which squares turn trains round, and
        // which are berths - so a cached set of them is out of date the moment one changes.  It was
        // dropped only on a rebuild, and this method deliberately does not rebuild, so marking a square
        // while autonomy was running left the labels looking up Point names the running graph had never
        // heard of, and that station stopped filling in until the next load.
        deriveStationIndex();
    }

    /**
     * @param tile
     * @param key
     * @return the stored value, or null when this Point has no such property
     */
    public Object getPointProperty(TileKey tile, String key)
    {
        String active = store.getActiveConfiguration();

        if (active == null) return null;

        org.json.JSONObject configuration = store.getConfiguration(active);

        if (configuration == null || !configuration.has("points")) return null;

        org.json.JSONObject points = configuration.getJSONObject("points");

        String id = tile.toString();

        if (!points.has(id)) return null;

        org.json.JSONObject point = points.getJSONObject(id);

        return point.has(key) ? point.get(key) : null;
    }

    /**
     * Sets one direction across a whole run of track, from one square to another.
     *
     * The gesture the per-tile tools could not express.  A user does not think "close the westward
     * route on eleven tiles"; they think "trains only go this way along here", and then have to work
     * out which tiles that means and which way round each one's A and B happen to be.
     *
     * The route is found ignoring directions - the point is to change them - so an already one-way run
     * can be reversed by drawing it the other way.
     *
     * @param from the square trains may leave
     * @param to the square they may travel toward
     * @return how many tiles were changed, or -1 if there is no continuous track between the two
     */
    public int setOneWayRun(TileKey from, TileKey to)
    {
        List<TileKey> path = graph.findUndirectedPath(from, to);

        if (path == null) return -1;

        return applyOneWay(path);
    }

    /**
     * Sets one direction along a path that is already known, tile by tile.
     *
     * Separate from the two-argument form because a RUN knows its own tiles, and re-deriving them from
     * its two ends with a shortest-path search is wrong wherever two chains join the same pair: on a
     * passing loop or a double-track section, both runs have the same ends, so the search returned the
     * other chain and one-wayed track the user had not touched - while the run they clicked kept its
     * old direction and its cycle stuck.
     *
     * @param path the squares in order, boundaries included
     * @return how many tiles were changed
     */
    private int applyOneWay(List<TileKey> path)
    {
        int changed = 0;

        // Only the track BETWEEN the two ends is restricted.  The ends themselves are the squares the
        // user picked out; closing a route on them would also block traffic that never enters the run.
        for (int i = 1; i < path.size() - 1; i++)
        {
            TileKey tile = path.get(i);

            Side cameFrom = graph.sideTowardNeighbour(tile, path.get(i - 1));
            Side goingTo = graph.sideTowardNeighbour(tile, path.get(i + 1));

            if (cameFrom == null || goingTo == null) continue;

            for (Map.Entry<RouteId, Route> entry : graph.getRoutes(tile).entrySet())
            {
                Route route = entry.getValue();

                if (!route.touches(cameFrom) || !route.touches(goingTo)) continue;

                record(tile, entry.getKey(),
                    route.getA() == goingTo ? Direction.TOWARD_A : Direction.TOWARD_B);

                changed++;
            }
        }

        touched();

        return changed;
    }

    public void setTileLength(TileKey tile, int length)
    {
        store.setTileLength(tile, length);
        touched();
    }

    public void setPageExcluded(String page, boolean excluded)
    {
        store.setPageExcluded(page, excluded);
        touched();
    }

    public void setLinkName(TileKey tile, String name)
    {
        store.setLinkName(tile, name);
        touched();
    }

    /**
     * Pairs two portals, and switches BOTH ends on (OB-031).
     *
     * A pairing is one fact about two squares, and being switched off is a fact about one - so a pair
     * whose far end was disabled read as connected on the diagram and refused every train, with
     * nothing on the near end to say why. Adam: "when an active link from one page pairs with a link
     * on another, both must be marked as active. right now, the target doesn't have to be."
     *
     * Enabling rather than refusing the pairing. Somebody pairing two links is saying they are joined;
     * a disabled far end is a setting made earlier about a square that was not joined to anything, and
     * the newer statement is the one they mean.
     *
     * @param a one end
     * @param b the other
     */
    public void pairPortals(TileKey a, TileKey b)
    {
        store.pairPortals(a, b);

        store.setPortalDisabled(a, false);
        store.setPortalDisabled(b, false);

        touched();
    }

    public void unpairPortal(TileKey tile)
    {
        store.unpairPortal(tile);
        touched();
    }

    /**
     * One arrow per run of track between two sensors, on a square in the middle of it.
     *
     * Marking only what is RESTRICTED leaves a layout almost bare, which is right for spotting
     * decisions and wrong for the first question anybody asks: does this sensor reach that one, and
     * which way round.  This puts a single arrow on each derived connection - enough to read the flow
     * of the whole railway at a glance, without an arrow on every square.
     *
     * A pair of runs that face each other collapses into one double-headed arrow, because two arrows
     * on the same piece of track pointing opposite ways is how bidirectional track already looks.
     *
     * @return the square to mark, and what to draw there
     */
    public Map<TileKey, TileAnnotation.Mark> flowMarks()
    {
        Map<TileKey, TileAnnotation.Mark> out = new LinkedHashMap<>();

        if (reducer == null || graph == null) return out;

        for (GraphReducer.ReducedEdge edge : reducer.getEdges())
        {
            List<GraphReducer.TileStep> path = edge.getPath();

            if (path.isEmpty()) continue;

            // the middle of the run, so the arrow is not crowded against either sensor
            int at = path.size() / 2;

            TileKey tile = path.get(at).getTile();

            // where a train standing here is heading next
            TileKey next = at + 1 < path.size() ? path.get(at + 1).getTile() : edge.getEnd();
            TileKey previous = at > 0 ? path.get(at - 1).getTile() : edge.getStart();

            Side toward = graph.sideTowardNeighbour(tile, next);
            Side from = graph.sideTowardNeighbour(tile, previous);

            if (toward == null || from == null) continue;

            Route route = graph.getRoutes(tile).get(path.get(at).getRouteId());

            if (route == null || !route.touches(toward) || !route.touches(from)) continue;

            Direction direction = route.getA() == toward ? Direction.TOWARD_A : Direction.TOWARD_B;

            TileAnnotation.Mark existing = out.get(tile);

            // the opposing run over the same track: one arrow with two heads, not two arrows
            out.put(tile, existing != null && existing.getDirection() != direction
                ? new TileAnnotation.Mark(route.getA(), route.getB(), Direction.BOTH)
                : new TileAnnotation.Mark(route.getA(), route.getB(), direction));
        }

        return out;
    }

    /**
     * One run of plain track: the tiles between two points, in order, and the points at either end.
     */
    public static class Run
    {
        private final TileKey start;
        private final TileKey end;
        private final List<TileKey> tiles;

        Run(TileKey start, TileKey end, List<TileKey> tiles)
        {
            this.start = start;
            this.end = end;
            this.tiles = tiles;
        }

        public TileKey getStart()
        {
            return start;
        }

        public TileKey getEnd()
        {
            return end;
        }

        /**
         * @return the tiles between the two points, in order from start to end
         */
        public List<TileKey> getTiles()
        {
            return Collections.unmodifiableList(tiles);
        }

        /**
         * The tile that speaks for this run - the first of them, as the author asked.
         */
        public TileKey getLeader()
        {
            return tiles.isEmpty() ? null : tiles.get(0);
        }
    }

    /**
     * Every run of plain track, keyed by the tile that speaks for it.
     *
     * A run of straight track has one direction, not eleven: setting it tile by tile is busywork, and
     * a run that disagrees with itself is a silent trap - the arrows look set and no train can pass.
     * So one tile in each run is the one to set, and the rest follow it.
     *
     * Computed from the DIAGRAM ALONE - tile types and which sides face which - and never from the
     * reduction.  That is the whole point: edges come and go as directions are set, so a run derived
     * from them would regroup itself every time somebody closed a route, and the greying would move
     * around under the user.  What is grey is a property of the track, not of the settings on it.
     *
     * A run tile is a piece of plain track: exactly one route through it, not a sensor, and not
     * something autonomy ignores.  Anything else - a switch, a crossing, a sensor - ends the run,
     * because each of those is a decision in its own right.
     *
     * @return leader tile to the run it leads
     */
    public Map<TileKey, Run> runs()
    {
        Map<TileKey, Run> out = new LinkedHashMap<>();

        if (graph == null) return out;

        Set<TileKey> seen = new LinkedHashSet<>();

        for (TileKey tile : graph.getTiles().keySet())
        {
            if (seen.contains(tile) || !isRunTile(tile)) continue;

            Route route = firstRoute(tile);

            if (route == null) continue;

            java.util.LinkedList<TileKey> chain = new java.util.LinkedList<>();
            chain.add(tile);
            seen.add(tile);

            // walk out of both ends until the plain track stops
            TileKey endA = walk(chain, tile, route.getA(), seen, false);
            TileKey endB = walk(chain, tile, route.getB(), seen, true);

            out.put(chain.getFirst(), new Run(endA, endB, new ArrayList<>(chain)));
        }

        return out;
    }

    /**
     * Whether a square is a piece of plain track that can belong to a run.
     */
    private boolean isRunTile(TileKey tile)
    {
        LayoutDiagramComponent component = graph.getTiles().get(tile);

        if (component == null || component.isFeedback()) return false;

        if (TilePorts.isDisqualified(component.getType())
            || TilePorts.isTransparent(component.getType())) return false;

        if (graph.getRoutes(tile).size() != 1) return false;

        // A stub - an END, a tunnel mouth, a link - has one route whose two sides are the same, so
        // walking "through" it comes straight back out the way it went in.  That made the walk report a
        // tile INSIDE the run as its boundary, leaving the tail of the run unset while the rest went
        // one-way: a run silently disagreeing with itself, which is the trap runs exist to prevent.
        Route route = firstRoute(tile);

        return route != null && route.getA() != route.getB();
    }

    /**
     * Extends a chain out of one side of a tile for as long as the track stays plain.
     *
     * @param chain collected so far
     * @param from where to start
     * @param side which way to go
     * @param seen tiles already claimed by a run
     * @param append true to add to the end of the chain, false to add to the front
     * @return the square the run stops at - a switch, a sensor, or null at the end of the track
     */
    private TileKey walk(java.util.LinkedList<TileKey> chain, TileKey from, Side side,
        Set<TileKey> seen, boolean append)
    {
        TileKey here = from;
        Side out = side;

        // bounded because a loop of plain track has no end to reach
        for (int guard = 0; guard < 1000; guard++)
        {
            Landing landing = graph.landing(here, out);

            if (landing == null) return null;

            TileKey next = landing.getTile();

            if (!isRunTile(next) || seen.contains(next)) return next;

            Route route = firstRoute(next);

            if (route == null) return next;

            if (append) chain.addLast(next); else chain.addFirst(next);

            seen.add(next);

            here = next;
            out = route.other(landing.getEntrySide());

            if (out == null) return null;
        }

        return null;
    }

    /**
     * @return every tile of every run, mapped to the tile that speaks for it
     */
    public Map<TileKey, TileKey> runLeaders()
    {
        Map<TileKey, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Run> entry : runs().entrySet())
        {
            for (TileKey tile : entry.getValue().getTiles())
            {
                out.put(tile, entry.getKey());
            }
        }

        return out;
    }

    /**
     * Sets a direction on every tile of the run a leader speaks for, in the sense the leader means.
     *
     * @param leader the tile the user set
     * @param routeId which of its routes
     * @param direction what they chose
     * @return how many tiles were changed, so a caller does not announce a change that did not happen
     */
    public int setRunDirection(TileKey leader, RouteId routeId, Direction direction)
    {
        Run run = runs().get(leader);

        // Not part of a run at all - a lone tile, or a point.  Set it and nothing else.
        if (run == null)
        {
            setDirection(leader, routeId, direction);
            return 1;
        }

        // Both ways and closed mean the same on every tile, so they go on directly.
        if (direction == Direction.BOTH || direction == Direction.NONE)
        {
            setDirection(new LinkedHashSet<>(run.getTiles()), direction);
            return run.getTiles().size();
        }

        Route route = graph.getRoutes(leader).get(routeId);

        if (route == null) return 0;

        Side toward = direction == Direction.TOWARD_A ? route.getA() : route.getB();

        Landing landing = graph.landing(leader, toward);

        // Which way along the run the user pointed.  The run's OWN tiles are walked, not a path
        // re-derived from its two ends - see applyOneWay.  The boundaries are included so the first and
        // last tiles of the run get a direction like the rest; a null boundary (track that simply stops)
        // is left out rather than making the whole thing impossible.
        List<TileKey> path = new ArrayList<>();

        if (run.getStart() != null) path.add(run.getStart());

        path.addAll(run.getTiles());

        if (run.getEnd() != null) path.add(run.getEnd());

        boolean towardEnd = landing == null
            || !run.getTiles().isEmpty() && landing.getTile().equals(nextAfter(run, leader));

        if (!towardEnd) Collections.reverse(path);

        return applyOneWay(path);
    }

    /**
     * The tile after this one along a run, or the run's far boundary when it is the last.
     */
    private TileKey nextAfter(Run run, TileKey tile)
    {
        int at = run.getTiles().indexOf(tile);

        if (at < 0) return run.getEnd();

        return at + 1 < run.getTiles().size() ? run.getTiles().get(at + 1) : run.getEnd();
    }

    /**
     * What the setup says about one square, for drawing on the ordinary track diagram.
     *
     * Points only - no direction arrows at all.  The diagram tab is where trains are WATCHED, and the
     * question there is where they are and where they are heading next, which the running overlay
     * answers.  Directions belong to the editor, where they are being decided; drawn here they were
     * just a page of green arrows over a railway nobody was configuring.
     *
     * @param tile
     * @return the annotation, or null when this square has nothing to say
     */
    public TileAnnotation staticAnnotationFor(TileKey tile)
    {
        if (graph == null || reducer == null) return null;

        // A link switched off is greyed here as well as in the editor.
        //
        // It was greyed only while editing, and a link is the one square whose being switched off is
        // invisible from the track alone: the tile art is the same either way, no arrow is drawn on it
        // to go missing, and the route it used to offer simply stops existing.  So a reader of the
        // running diagram had nothing to look at that said why trains never take that door.
        //
        // Before the Point test, because a link may or may not carry a sensor and the answer must not
        // depend on which.
        if (isDisabledLink(tile))
        {
            return new TileAnnotation(new ArrayList<TileAnnotation.Mark>(), -1, false, null, true);
        }

        if (!reducer.getPoints().containsKey(tile)) return null;

        String name = store.getPointName(tile);

        // A plain point draws no badge on the running diagram.
        //
        // The tile is a feedback tile and looks like one, so the badge only repeated what the art
        // already said - on every sensor of the layout, which is most of them.  What a badge is FOR is
        // the things the art cannot say: this is a station, trains turn round here, autonomy is not
        // using it.  Those still draw.
        //
        // A non-station where trains turn round keeps its mark, because "trains reverse here" is a fact
        // about behaviour that nothing on the tile shows.
        boolean worthABadge = store.isStation(tile) || isTurnAround(tile);

        return new TileAnnotation(new ArrayList<TileAnnotation.Mark>(), -1, false,
            !worthABadge ? null : new TileAnnotation.Badge(
                store.isStation(tile),
                store.isStation(tile) && isTurnAround(tile),
                !store.isStation(tile) && isTurnAround(tile),
                Boolean.FALSE.equals(getPointProperty(tile, "active")) || !isAutoDestination(tile),
                name != null && !name.trim().isEmpty(),
                firstRoute(tile) == null ? null : firstRoute(tile).getA(),
                firstRoute(tile) == null ? null : firstRoute(tile).getB(),
                isTurnAround(tile) && !isMustTurnAround(tile)),
            false, false, false, null, false,
            // Only where something is actually restricted.  A station that takes trains from anywhere
            // has nothing to say here, and saying it on every platform of the layout would be the
            // clutter this mark exists to avoid.
            arrivalMarks(tile, false));
    }

    /**
     * The stations whose every way in has been barred.
     *
     * Worked out here because it needs both halves - what is barred, and how many ways in the square
     * actually has - and the split is this class's business.  A station with no arrival sides at all
     * is not shut: it is a square that never splits, which the restriction cannot touch.
     *
     * @return station square to true, only for the ones that are shut
     */
    public Map<TileKey, Boolean> shutStations()
    {
        Map<TileKey, Boolean> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Set<Side>> entry : barredArrivals().entrySet())
        {
            if (!store.isStation(entry.getKey())) continue;

            List<Side> ways = arrivalSides(entry.getKey());

            if (ways.isEmpty()) continue;

            boolean open = false;

            for (Side side : ways)
            {
                if (!entry.getValue().contains(side)) open = true;
            }

            if (!open) out.put(entry.getKey(), Boolean.TRUE);
        }

        return out;
    }

    /**
     * The arrival marks for a square, or none where there is nothing to say.
     *
     * Only stations have them: arriving somewhere is stopping there, and a square trains merely pass
     * over is not somewhere anything arrives.
     *
     * @param tile
     * @param always true to mark every arrival side of every station - which is the editor's own view
     *        of this setting - and false to mark only the stations that actually restrict something,
     *        which is what the running diagram shows.  Left on everywhere, a two-ended station would
     *        carry two marks saying "yes, trains may arrive", on every platform of the layout, to say
     *        nothing at all.
     * @return the marks, in the order the build emits the copies
     */
    public List<TileAnnotation.Arrival> arrivalMarks(TileKey tile, boolean always)
    {
        List<TileAnnotation.Arrival> out = new ArrayList<>();

        if (tile == null || !store.isStation(tile)) return out;

        Set<Side> barred = getBarredArrivals(tile);

        if (!always && barred.isEmpty()) return out;

        for (Side side : arrivalSides(tile))
        {
            out.add(new TileAnnotation.Arrival(side, !barred.contains(side)));
        }

        return out;
    }

    /**
     * Whether this square is a link that autonomy has been told to leave alone.
     *
     * Both halves have to hold: the store remembers a switched-off link by its square, and a square
     * that has since stopped being a link - the tile replaced, the page redrawn - is an ordinary piece
     * of track that would otherwise be greyed out by a setting nobody can see or clear.
     *
     * @param tile
     * @return true when the square is a link and switched off
     */
    public boolean isDisabledLink(TileKey tile)
    {
        if (graph == null || tile == null) return false;

        LayoutDiagramComponent component = graph.getTiles().get(tile);

        return component != null && TilePorts.hasPortal(component.getType())
            && store.isPortalDisabled(tile);
    }

    /**
     * The tile's first route, which is where its badge is drawn.
     */
    private Route firstRoute(TileKey tile)
    {
        Map<RouteId, Route> routes = graph == null ? null : graph.getRoutes(tile);

        return routes == null || routes.isEmpty() ? null : routes.values().iterator().next();
    }

    /**
     * Every route of a tile, so the panel can offer a switch's branches individually.
     * @param tile
     * @return
     */
    public Map<RouteId, Route> getRoutes(TileKey tile)
    {
        return graph == null ? new LinkedHashMap<RouteId, Route>() : graph.getRoutes(tile);
    }

    private void touched()
    {
        dirty = true;
        rebuild();
    }

    /**
     * Writes the setup out, and forgets what the diagram no longer has.
     *
     * Reconciled at save rather than at load, so a diagram edited between sessions is tidied at the
     * moment somebody is present to be told about it.
     *
     * @return what reconciling found, for showing
     * @throws IOException
     */
    public AutonomyCompanionStore.Reconciliation save() throws IOException
    {
        forgetArrivalsThatNoLongerExist();

        // Every tile of EVERY page, including the excluded ones.  The graph omits excluded pages by
        // construction, so reconciling against it made every setting on such a page look like it
        // belonged to a deleted tile - and saving then destroyed the lot, permanently, with
        // re-including the page giving nothing back.  Excluding a page must be reversible.
        Set<TileKey> existing = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component != null)
                {
                    existing.add(new TileKey(page.getName(), component.getX(), component.getY()));
                }
            }
        }

        AutonomyCompanionStore.Reconciliation report = store.reconcile(existing);

        store.save();

        dirty = false;

        rebuild();

        return report;
    }

    /**
     * Writes the setup out WITHOUT reconciling it against the diagram.
     *
     * For the paths that save because the diagram is being replaced underneath them, rather than
     * because a user pressed Save.  Reconciling compares the setup against the pages this session
     * holds - and on those paths the pages are the ones an editor mutated in place, which the user may
     * have just discarded with Cancel.  The editor works on the live LayoutDiagram objects and Cancel
     * reverts by re-reading from disk into NEW ones, so the session is left holding the edited
     * version: reconciling against it deleted the names, lengths, directions and arrival restrictions
     * of every square the user had deleted and then thought better of, permanently, with the track
     * itself coming back.
     *
     * Nothing is lost by waiting.  Reconciliation tidies a setup whose diagram has genuinely changed,
     * and the next explicit save does it with pages that are actually current - which is the moment
     * its report can be shown to somebody anyway.
     *
     * @throws IOException
     */
    public void saveWithoutReconciling() throws IOException
    {
        store.save();

        dirty = false;
    }
}
