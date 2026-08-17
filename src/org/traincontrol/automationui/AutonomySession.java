package org.traincontrol.automationui;

import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;
import org.traincontrol.base.Locomotive;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
        baseNames = null;

        graph = new TileGraph(pages, store.getExcludedPages());

        store.applyTo(graph);

        graph.validatePortals();

        reducer = new GraphReducer(graph, store.asAuthored());
        reducer.reduce();
    }

    public TileGraph getGraph()
    {
        return graph;
    }

    public GraphReducer getReducer()
    {
        return reducer;
    }

    /**
     * The marker a track diagram text square carries to say it belongs to a station.
     *
     * Defined here rather than in the grid that draws it, because the setup layer is what decides
     * whether a station HAS one - LayoutGrid now takes its constant from this, so the two cannot drift
     * apart and quietly stop matching.
     */
    public static final String STATION_LABEL_PREFIX = "Point:";

    /**
     * Every station name that some square of some page is showing.
     *
     * All pages, including excluded ones: exclusion says autonomy will not route over a page, not that
     * the page has stopped being drawn, and a label there is still on the user's screen.
     *
     * @return the names, without the prefix
     */
    public Set<String> getLabelledStationNames()
    {
        Set<String> out = new LinkedHashSet<>();

        for (LayoutDiagram page : pages)
        {
            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component == null || component.getLabel() == null) continue;

                if (component.getLabel().startsWith(STATION_LABEL_PREFIX))
                {
                    out.add(component.getLabel().substring(STATION_LABEL_PREFIX.length()));
                }
            }
        }

        return out;
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
     * @return true if a label was written
     * @throws Exception if the page cannot be written
     */
    public boolean placeStationLabel(TileKey tile) throws Exception
    {
        String name = store.getPointName(tile);

        if (name == null || name.trim().isEmpty()) return false;

        if (getLabelledStationNames().contains(name)) return false;

        LayoutDiagram page = pageOf(tile);

        if (page == null) return false;

        LayoutDiagramComponent here = page.getComponent(tile.getX(), tile.getY());

        if (here == null) return false;

        if (runsEastWest(here))
        {
            // On the square itself: the rails run along the writing, not across it
            if (here.hasLabel()) return false;

            setStationLabel(tile, name);
            return true;
        }

        TileKey below = new TileKey(tile.getPage(), tile.getX(), tile.getY() + 1);

        LayoutDiagramComponent under = page.getComponent(below.getX(), below.getY());

        // Only onto empty space.  Track below would lose its own square to a caption, and a text
        // square below is somebody's own writing.
        if (under != null) return false;

        setStationLabel(below, name);
        return true;
    }

    /**
     * Whether this square's rails run east to west, so a name written on it lies along the track
     * rather than across it.
     */
    private boolean runsEastWest(LayoutDiagramComponent component)
    {
        for (Route route : TilePorts.ports(component.getType(),
            component.getOrientation(), component.getState()))
        {
            if (route.touches(TilePorts.Side.E) && route.touches(TilePorts.Side.W)) return true;
        }

        return false;
    }

    /**
     * Puts a station's name onto a text square of the track diagram, and writes the page out.
     *
     * The one place setup mode changes the DIAGRAM rather than the setup beside it, at the author's
     * instruction (2026-08-16): a station with no label on the diagram is invisible where it matters
     * most, and sending the user to a different editor to fix what this one just warned them about is
     * the sort of round trip this whole surface exists to remove.
     *
     * Written immediately rather than at Save, because Save in setup mode means the autonomy setup -
     * a diagram change riding along inside it would be saved by a button that says otherwise.
     *
     * @param tile the text square
     * @param name the station, or null to clear the square
     * @throws Exception if the page cannot be written
     */
    public void setStationLabel(TileKey tile, String name) throws Exception
    {
        LayoutDiagram page = pageOf(tile);

        if (page != null)
        {
            LayoutDiagramComponent component = page.getComponent(tile.getX(), tile.getY());

            // An empty square becomes a text square.  Without this the feature only works for somebody
            // who already drew a text square in the diagram editor - which is the trip to a different
            // editor this is meant to spare them.  Nothing else about the page is touched: a text
            // square carries no track, no address and no state, so it cannot change how trains run.
            if (component == null)
            {
                if (name == null) return;

                page.addComponent(LayoutDiagramComponent.componentType.TEXT,
                    tile.getX(), tile.getY(), 0, 0, 0, 0, null, STATION_LABEL_PREFIX + name);
            }
            else
            {
                component.setLabel(name == null ? "" : STATION_LABEL_PREFIX + name);
            }

            page.saveChanges(null, false);
            return;
        }
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
        return new AutonomyBuilder(reducer, globals())
            .withPointExtras(pointExtras())
            .withReversibleTiles(reversibleTiles())
            .withParkingTiles(parkingTiles())
            .build();
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
        if (pointName == null) return null;

        String base = baseNames().get(pointName);

        return base == null ? pointName : base;
    }

    // Emitted Point name -> the caption on the diagram.  Cached because updateStationLabels asks on
    // every feedback event during a run, and working it out walks every point against every edge.
    // Dropped whenever the graph is rebuilt, which is the only thing that can change it.
    // volatile: the labels are updated from the feedback thread while a rebuild can drop this from the
    // event thread, and a stale reference here means a station that stops filling in until the next edit
    private volatile Map<String, String> baseNames;

    private Map<String, String> baseNames()
    {
        if (baseNames == null)
        {
            baseNames = reducer == null ? new LinkedHashMap<String, String>()
                : new AutonomyBuilder(reducer, null)
                    .withReversibleTiles(reversibleTiles())
                    .withParkingTiles(parkingTiles()).baseNames();
        }

        return baseNames;
    }

    /**
     * Every Point of the running configuration that stands for one square of the diagram.
     *
     * @param baseName the caption on the diagram
     * @return the emitted names, in the order they were emitted
     */
    public List<String> pointNamesFor(String baseName)
    {
        List<String> out = new ArrayList<>();

        if (baseName == null) return out;

        for (Map.Entry<String, String> entry : baseNames().entrySet())
        {
            if (entry.getValue().equals(baseName)) out.add(entry.getKey());
        }

        return out;
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
    public String pointNameForTile(TileKey tile)
    {
        if (reducer == null) return null;

        return new AutonomyBuilder(reducer, null).uniqueNames().get(tile);
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
    public boolean isTurnAround(TileKey tile)
    {
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

        return new AutonomyBuilder(reducer, globals())
            .withPointExtras(pointExtras())
            .withReversibleTiles(reversibleTiles())
            .withParkingTiles(parkingTiles())
            .withCoordinatesFromTiles(pageOrder).build();
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

        Set<TileKey> split = reversibleTiles();

        tilesByName.putAll(new AutonomyBuilder(reducer, null)
            .withReversibleTiles(split).withParkingTiles(parkingTiles()).tilesByName());

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

                if (extras.length() > 0) points.put(tile.toString(), extras);
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

            existing.put(id, before);
        }

        // A point the running layout no longer has - its track was deleted - keeps nothing.
        List<String> gone = new ArrayList<>();

        for (String id : existing.keySet())
        {
            if (!points.has(id)) gone.add(id);
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

        return AutonomyChecks.run(graph, reducer, termini, getLabelledStationNames());
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

    public void setPointName(TileKey tile, String name)
    {
        String before = store.getPointName(tile);

        store.setPointName(tile, name);
        touched();

        // A label carries the NAME, so renaming a station strands every caption showing the old one:
        // the label is registered under a name no Point has, and simply stops filling in.  Nothing
        // says why, because as far as the diagram is concerned it is still a perfectly good label.
        if (before != null && !before.equals(name)) renameStationLabels(before, name);
    }

    /**
     * Rewrites every diagram caption showing one station name to show another.
     *
     * Silent about failure on purpose: this rides along with a rename, and a page that cannot be
     * written should not turn renaming a point into an error dialog.  The caption that did not follow
     * is then a station with no label, which the checks already report in its own words.
     */
    private void renameStationLabels(String before, String after)
    {
        for (LayoutDiagram page : pages)
        {
            boolean changed = false;

            for (LayoutDiagramComponent component : page.getAll())
            {
                if (component == null || component.getLabel() == null) continue;

                if (component.getLabel().equals(STATION_LABEL_PREFIX + before))
                {
                    component.setLabel(after == null || after.trim().isEmpty()
                        ? "" : STATION_LABEL_PREFIX + after);

                    changed = true;
                }
            }

            if (!changed) continue;

            try
            {
                page.saveChanges(null, false);
            }
            catch (Exception e)
            {
                // left to the "not shown anywhere on the track diagram" check to report
            }
        }
    }

    public void setStation(TileKey tile, boolean station)
    {
        store.setStation(tile, station);
        touched();
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
            return;
        }

        Object existing = getPointProperty(tile, "loc");

        org.json.JSONObject loc = existing instanceof org.json.JSONObject
            ? new org.json.JSONObject(existing.toString()) : new org.json.JSONObject();

        loc.put("name", name);

        setPointProperty(tile, "loc", loc);
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

            Side cameFrom = graph.sideToward(tile, path.get(i - 1));
            Side goingTo = graph.sideToward(tile, path.get(i + 1));

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

    public void pairPortals(TileKey a, TileKey b)
    {
        store.pairPortals(a, b);
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

            Side toward = graph.sideToward(tile, next);
            Side from = graph.sideToward(tile, previous);

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

        if (!reducer.getPoints().containsKey(tile)) return null;

        String name = store.getPointName(tile);

        return new TileAnnotation(new ArrayList<TileAnnotation.Mark>(), -1, false,
            new TileAnnotation.Badge(
                store.isStation(tile),
                store.isStation(tile) && isTurnAround(tile),
                !store.isStation(tile) && isTurnAround(tile),
                Boolean.FALSE.equals(getPointProperty(tile, "active")) || !isAutoDestination(tile),
                name != null && !name.trim().isEmpty(),
                firstRoute(tile) == null ? null : firstRoute(tile).getA(),
                firstRoute(tile) == null ? null : firstRoute(tile).getB()),
            false);
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
}
