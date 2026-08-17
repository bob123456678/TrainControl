package org.traincontrol.base;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.base.TileGraph.Direction;
import org.traincontrol.base.TileGraph.Landing;
import org.traincontrol.base.TileGraph.RouteId;
import org.traincontrol.base.TileGraph.TileKey;
import org.traincontrol.base.TilePorts.Route;
import org.traincontrol.base.TilePorts.Side;

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
        if (store.getConfigurationNames().isEmpty())
        {
            store.createConfiguration(
                configurationName == null || configurationName.trim().isEmpty()
                    ? "Default" : configurationName.trim(),
                null);
        }

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

    public List<LayoutDiagram> getPages()
    {
        return Collections.unmodifiableList(pages);
    }

    /**
     * The per-point keys a configuration owns: everything operational parseAuto accepts on a point.
     * Structural keys (name, station, s88, coordinates) belong to the reduction and are not here.
     */
    private static final List<String> POINT_OPERATIONAL_KEYS = java.util.Arrays.asList(
        "loc", "terminus", "reversing", "active", "maxTrainLength", "speedMultiplier",
        "priority", "home", "excludedLocs");

    /**
     * The generated configuration, in the format the autonomy model already reads.
     * @return
     */
    public String buildConfiguration()
    {
        return new AutonomyBuilder(reducer, globals()).withPointExtras(pointExtras()).build();
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

        return new AutonomyBuilder(reducer, globals()).withCoordinatesFromTiles(pageOrder).build();
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

        for (Map.Entry<TileKey, String> entry
            : new AutonomyBuilder(reducer, null).uniqueNames().entrySet())
        {
            tilesByName.put(entry.getValue(), entry.getKey());
        }

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
                    if (point.has(key) && !point.isNull(key)) extras.put(key, point.get(key));
                }

                if (extras.length() > 0) points.put(tile.toString(), extras);
            }
        }

        configuration.put("points", points);

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
        return graph == null || reducer == null
            ? new ArrayList<AutonomyChecks.Finding>() : AutonomyChecks.run(graph, reducer);
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

    /**
     * Cycles what a tile allows, which is the whole of the connection tool.
     *
     * both -> one way -> the other way -> none -> both.  The user never has to hold a convention in
     * their head because the arrow drawn on the tile says which way "one way" currently means; if it
     * points the wrong way they click again.
     *
     * @param tile
     * @param routeId which of the tile's routes, for a switch branch or a double curve
     * @return the direction now in force
     */
    public Direction cycleDirection(TileKey tile, RouteId routeId)
    {
        Direction next;

        switch (graph.getDirection(tile, routeId))
        {
            case BOTH: next = Direction.TOWARD_A; break;
            case TOWARD_A: next = Direction.TOWARD_B; break;
            case TOWARD_B: next = Direction.NONE; break;
            default: next = Direction.BOTH; break;
        }

        setDirection(tile, routeId, next);

        return next;
    }

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

    public void setPointName(TileKey tile, String name)
    {
        store.setPointName(tile, name);
        touched();
    }

    public void setStation(TileKey tile, boolean station)
    {
        store.setStation(tile, station);
        touched();
    }

    /**
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

        return graph.getRoutes(tile).size() == 1;
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
     */
    public void setRunDirection(TileKey leader, RouteId routeId, Direction direction)
    {
        Run run = runs().get(leader);

        // Not part of a run at all - a lone tile, or a point.  Set it and nothing else.
        if (run == null)
        {
            setDirection(leader, routeId, direction);
            return;
        }

        // Both ways and closed mean the same on every tile, so they go on directly.
        if (direction == Direction.BOTH || direction == Direction.NONE)
        {
            setDirection(new LinkedHashSet<>(run.getTiles()), direction);
            return;
        }

        Route route = graph.getRoutes(leader).get(routeId);

        if (route == null) return;

        Side toward = direction == Direction.TOWARD_A ? route.getA() : route.getB();

        Landing landing = graph.landing(leader, toward);

        // Which way along the run the user pointed: toward the far point, or back toward the near one.
        // setOneWayRun then translates that into each tile's own sides, which is the part nobody should
        // have to do by hand.
        boolean towardEnd = landing == null
            || !run.getTiles().isEmpty() && landing.getTile().equals(nextAfter(run, leader));

        setOneWayRun(towardEnd ? run.getStart() : run.getEnd(),
            towardEnd ? run.getEnd() : run.getStart());
    }

    /**
     * The tile after this one along a run, or the run's far point when it is the last.
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
                Boolean.TRUE.equals(getPointProperty(tile, "terminus")),
                Boolean.TRUE.equals(getPointProperty(tile, "reversing")),
                Boolean.FALSE.equals(getPointProperty(tile, "active")),
                name != null && !name.trim().isEmpty(),
                firstRoute(tile) == null ? null : firstRoute(tile).getA(),
                firstRoute(tile) == null ? null : firstRoute(tile).getB()),
            false, false);
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
        Set<TileKey> existing = new LinkedHashSet<>(graph.getTiles().keySet());

        AutonomyCompanionStore.Reconciliation report = store.reconcile(existing);

        store.save();

        dirty = false;

        rebuild();

        return report;
    }
}
