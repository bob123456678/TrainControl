package org.traincontrol.automationui;

import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagramComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TileGraph.Exit;
import org.traincontrol.automationui.TileGraph.Landing;
import org.traincontrol.automationui.TileGraph.RouteId;
import org.traincontrol.automationui.TileGraph.TileKey;
import org.traincontrol.automationui.TilePorts.AccessorySlot;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * Layer 2: contracts the tile graph into the graph the autonomy model actually wants.
 *
 * Only feedback tiles survive as nodes - a feedback signal has no other way into the model, so every s88
 * becomes a Point whether or not the user thinks of it as a station.  Everything between two of them
 * collapses: a run of plain track becomes one edge, and a switch is not a node at all but a branch point
 * that forks the walk and contributes the accessory settings its position requires.
 *
 * What each edge keeps is the tiles it covers.  That is what monitoring lights up, and what lock
 * derivation intersects: two edges that share a tile cannot run at once, because they are the same piece
 * of railway.
 *
 * @author Adam
 */
public class GraphReducer
{
    /**
     * A stop on an edge's path: the tile, and which of its routes was used.
     *
     * The route matters for exactly one thing - an overpass carries two tracks at different heights, so
     * two edges crossing it by different routes do not conflict, while every other shared tile does.
     */
    public static class TileStep
    {
        private final TileKey tile;
        private final RouteId routeId;
        private final int state;

        TileStep(TileKey tile, RouteId routeId, int state)
        {
            this.tile = tile;
            this.routeId = routeId;
            this.state = state;
        }

        public TileKey getTile()
        {
            return tile;
        }

        public RouteId getRouteId()
        {
            return routeId;
        }

        public int getState()
        {
            return state;
        }

        @Override
        public String toString()
        {
            return tile + "/" + routeId;
        }
    }

    /**
     * A Point: one feedback tile, with the s88 it carries.
     */
    public static class ReducedPoint
    {
        private final TileKey tile;
        private final int s88;
        private final String name;
        private final boolean station;

        ReducedPoint(TileKey tile, int s88, String name, boolean station)
        {
            this.tile = tile;
            this.s88 = s88;
            this.name = name;
            this.station = station;
        }

        public TileKey getTile()
        {
            return tile;
        }

        public int getS88()
        {
            return s88;
        }

        public String getName()
        {
            return name;
        }

        /**
         * Whether the user designated this Point a station.  Only s88 tiles can be, which is the model's
         * own rule - Point refuses a destination without a sensor.
         * @return
         */
        public boolean isStation()
        {
            return station;
        }

        @Override
        public String toString()
        {
            return name;
        }
    }

    /**
     * A directed edge between two Points, with everything the autonomy model needs to run it.
     */
    public static class ReducedEdge
    {
        private final TileKey start;
        private final TileKey end;
        private final List<TileStep> path;
        private final Map<String, accessorySetting> commands;
        private final int length;

        ReducedEdge(TileKey start, TileKey end, List<TileStep> path,
            Map<String, accessorySetting> commands, int length)
        {
            this.start = start;
            this.end = end;
            this.path = Collections.unmodifiableList(path);
            this.commands = Collections.unmodifiableMap(commands);
            this.length = length;
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
         * The tiles between the two Points, endpoints excluded.  Monitoring lights these; lock derivation
         * intersects them.
         * @return
         */
        public List<TileStep> getPath()
        {
            return path;
        }

        /**
         * Accessory settings this edge requires, keyed by accessory name as the autonomy model expects -
         * switch positions and signal greens together, gathered the same way.
         * @return
         */
        public Map<String, accessorySetting> getCommands()
        {
            return commands;
        }

        /**
         * The sum of the lengths assigned to the tiles this edge covers.  Zero unless the user assigned
         * lengths, which keeps train-length accounting inert until they opt in.
         * @return
         */
        public int getLength()
        {
            return length;
        }

        @Override
        public String toString()
        {
            return start + " -> " + end;
        }
    }

    /**
     * Supplies what geometry cannot: the names the user gave, which Points are stations, and how long
     * each tile is.  The companion files provide this in production; tests provide it directly.
     */
    public interface Authored
    {
        /**
         * @param tile
         * @return the user's name for this Point, or null to generate one from the coordinate
         */
        String getPointName(TileKey tile);

        /**
         * @param tile
         * @return whether the user designated this Point a station
         */
        boolean isStation(TileKey tile);

        /**
         * @param tile
         * @return the length assigned to this tile; 0 by default, which disables length accounting
         */
        int getTileLength(TileKey tile);
    }

    /**
     * Authored data with nothing authored: generated names, no stations, no lengths.
     */
    public static class NothingAuthored implements Authored
    {
        @Override
        public String getPointName(TileKey tile)
        {
            return null;
        }

        @Override
        public boolean isStation(TileKey tile)
        {
            return false;
        }

        @Override
        public int getTileLength(TileKey tile)
        {
            return 0;
        }
    }

    // A walk that runs longer than this is a diagram pathology, not a route
    private static final int MAX_PATH_TILES = 2000;


    private final TileGraph graph;
    private final Authored authored;

    private final Map<TileKey, ReducedPoint> points = new LinkedHashMap<>();
    private final List<ReducedEdge> edges = new ArrayList<>();
    private final Map<ReducedEdge, Set<ReducedEdge>> locks = new LinkedHashMap<>();
    private final List<TileGraph.Problem> problems = new ArrayList<>();
    private int isolatedFeedbackTiles = 0;

    // One edge per ordered pair of Points, because that is the model's own notion of edge identity.
    private final Map<String, ReducedEdge> edgeByPair = new LinkedHashMap<>();

    /**
     * Two physical routes join the same pair of sensors; only the shorter is used.
     */
    public static final String WARN_PARALLEL_ROUTE = "autosetup.ui.warnParallelRoute";

    /**
     * A run of track leaves a sensor and returns to it without passing another - a balloon loop.
     */
    public static final String WARN_SELF_LOOP = "autosetup.ui.warnSelfLoop";

    /**
     * A double-curve sensor with track on both of its curves - one s88 over two separate tracks.
     */
    public static final String ERROR_DOUBLE_CURVE_SENSOR = "autosetup.ui.errorDoubleCurveSensor";

    public GraphReducer(TileGraph graph, Authored authored)
    {
        this.graph = graph;
        this.authored = authored == null ? new NothingAuthored() : authored;
    }

    /**
     * Runs the contraction.  Deterministic: the same diagram and the same authored data produce the same
     * graph, in the same order, so two builds are comparable.
     */
    public void reduce()
    {
        points.clear();
        edges.clear();
        edgeByPair.clear();
        locks.clear();
        problems.clear();
        isolatedFeedbackTiles = 0;

        buildPoints();
        walkEdges();
        deriveLocks();
    }

    /**
     * @return the Points, one per feedback tile that is connected to anything
     */
    public Map<TileKey, ReducedPoint> getPoints()
    {
        return Collections.unmodifiableMap(points);
    }

    /**
     * @return every directed edge found
     */
    public List<ReducedEdge> getEdges()
    {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Which edges cannot run at the same time as which.  Derived from shared tiles, so a diagram-built
     * configuration cannot have the coverage gaps a hand-written one can.
     * @return
     */
    public Map<ReducedEdge, Set<ReducedEdge>> getLocks()
    {
        return Collections.unmodifiableMap(locks);
    }

    /**
     * @return feedback tiles left out because nothing connects to them
     */
    public int getIsolatedFeedbackTiles()
    {
        return isolatedFeedbackTiles;
    }

    /**
     * Whether, and how, a train could get from one Point to another.
     *
     * Breadth-first over the directed edges, so what comes back is a shortest run in edges - the one a
     * user can most easily check against the diagram.  This is the connectivity TEST the editor offers;
     * it deliberately ignores lengths, exclusions and locks, because the question it answers is "does
     * the track as authored allow this at all", and a "no" from a longer answer would leave the user
     * unsure which rule said it.
     *
     * @param from the tile of the starting Point
     * @param to the tile of the destination Point
     * @return the edges of a shortest run in order, or null when no run exists (or either end is not a
     *         Point of this reduction)
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to)
    {
        if (!points.containsKey(from) || !points.containsKey(to)) return null;

        if (from.equals(to)) return new ArrayList<ReducedEdge>();

        Map<TileKey, ReducedEdge> arrivedBy = new LinkedHashMap<>();

        java.util.ArrayDeque<TileKey> frontier = new java.util.ArrayDeque<>();
        frontier.add(from);

        while (!frontier.isEmpty())
        {
            TileKey here = frontier.poll();

            for (ReducedEdge edge : edges)
            {
                if (!edge.getStart().equals(here)) continue;

                TileKey next = edge.getEnd();

                if (next.equals(from) || arrivedBy.containsKey(next)) continue;

                arrivedBy.put(next, edge);

                if (next.equals(to))
                {
                    List<ReducedEdge> path = new ArrayList<>();

                    for (TileKey at = to; !at.equals(from);)
                    {
                        ReducedEdge step = arrivedBy.get(at);
                        path.add(0, step);
                        at = step.getStart();
                    }

                    return path;
                }

                frontier.add(next);
            }
        }

        return null;
    }

    public List<TileGraph.Problem> getProblems()
    {
        return Collections.unmodifiableList(problems);
    }

    // --- points -----------------------------------------------------------------------------------

    private void buildPoints()
    {
        for (TileKey tile : graph.getFeedbackTiles())
        {
            LayoutDiagramComponent component = graph.getTiles().get(tile);

            if (component == null) continue;

            // A sensor nothing connects to would only be an unreachable node, so it is counted and left
            // out rather than emitted to fail validation later
            if (!hasAnyConnection(tile))
            {
                isolatedFeedbackTiles++;
                continue;
            }

            // A double-curve sensor carries TWO independent curves on one tile with one s88.  As a
            // single Point it joins them: a train could enter on one curve and leave on the other,
            // crossing between tracks that never meet - and lock derivation cannot catch it, because a
            // Point is never part of any edge's path.  Reported rather than emitted wrong.
            if (component.getType() == componentType.FEEDBACK_DOUBLE_CURVE
                && bothCurvesConnected(tile))
            {
                problems.add(new TileGraph.Problem(tile, ERROR_DOUBLE_CURVE_SENSOR, true));
                continue;
            }

            String name = authored.getPointName(tile);

            if (name == null || name.trim().isEmpty())
            {
                name = generatedName(tile);
            }

            // Feedback is keyed on the RAW address, not the halved one.  CS2File divides the artikel
            // value by two for everything except routes, giving accessories their logical address, but
            // MarklinControlStation registers and looks up feedback by getRawAddress() - so that is the
            // number an autonomy Point's s88 has always meant.
            points.put(tile, new ReducedPoint(tile, component.getRawAddress(), name,
                authored.isStation(tile)));
        }
    }

    /**
     * A default name from the coordinate, which is unique by construction.  The s88 address is not usable
     * here - a station and its approach guards legitimately share one sensor, so several Points would
     * collide on the same name.
     */
    public static String generatedName(TileKey tile)
    {
        return tile.getPage() + " " + tile.getX() + "," + tile.getY();
    }

    /**
     * Whether anything at all adjoins this tile.
     *
     * Physical connection, not traversability: a sensor whose neighbours are all one-way away from it is
     * still part of the layout and should be a Point, whereas a sensor drawn on its own in a blank area
     * is not.  landing() already refuses a neighbour with no facing port, so this is exactly "is there
     * track on the other side".
     */
    /**
     * Whether both curves of a double-curve tile actually have track on them.
     *
     * One of them alone is harmless - the tile is then just a curved sensor.
     */
    private boolean bothCurvesConnected(TileKey tile)
    {
        Map<RouteId, Route> routes = graph.getRoutes(tile);

        if (routes.size() < 2) return false;

        int connected = 0;

        for (Route route : routes.values())
        {
            if (graph.landing(tile, route.getA()) != null
                || graph.landing(tile, route.getB()) != null) connected++;
        }

        return connected > 1;
    }

    private boolean hasAnyConnection(TileKey tile)
    {
        for (Side side : Side.values())
        {
            if (graph.landing(tile, side) != null) return true;
        }

        return false;
    }

    // --- edges ------------------------------------------------------------------------------------

    private void walkEdges()
    {
        for (TileKey start : points.keySet())
        {
            // A train standing here may leave by any side its own routes allow.  Leaving by X is the same
            // traversal as entering by the other side of that route, so the tile's direction governs.
            for (Side entry : Side.values())
            {
                for (Exit exit : graph.exits(start, entry))
                {
                    List<TileStep> path = new ArrayList<>();
                    Map<String, accessorySetting> commands = new LinkedHashMap<>();

                    // the starting tile's own position counts: leaving a switch by a branch requires it
                    if (!collectCommands(start, exit.getState(), commands)) continue;

                    walk(start, exit, path, commands);
                }
            }
        }
    }

    /**
     * Follows one route out of a Point until it reaches the next Point, forking at every switch.
     */
    private void walk(TileKey start, Exit firstExit, List<TileStep> path,
        Map<String, accessorySetting> commands)
    {
        Landing landing = graph.landing(start, firstExit.getSide());

        if (landing == null) return;

        Set<String> visited = new HashSet<>();
        visited.add(start.toString());

        continueWalk(start, landing, path, commands, visited);
    }

    private void continueWalk(TileKey start, Landing landing, List<TileStep> path,
        Map<String, accessorySetting> commands, Set<String> visited)
    {
        if (path.size() > MAX_PATH_TILES) return;

        TileKey tile = landing.getTile();

        // Reached the next Point: the edge is everything between the two
        if (points.containsKey(tile))
        {
            // An edge's identity in the autonomy model is its pair of Point NAMES, so two physical
            // routes between the same two sensors - a passing loop, a double-track section - cannot
            // both be emitted: createEdge throws on the second and parseAuto invalidates the entire
            // configuration, reporting only edge JSON with nothing pointing back at the diagram.
            //
            // A self-loop is the same problem in one tile: a balloon loop returns to its own sensor and
            // the model has no edge from a Point to itself.
            if (tile.equals(start))
            {
                problems.add(new TileGraph.Problem(start, WARN_SELF_LOOP, false));
                return;
            }

            String pair = start.toString() + " -> " + tile.toString();

            ReducedEdge existing = edgeByPair.get(pair);

            if (existing != null)
            {
                // Keep the shorter route, which is the one a train would be given anyway, and say that
                // the other exists - silently dropping half a layout's track would be worse than the
                // duplicate was.
                if (path.size() >= existing.getPath().size())
                {
                    problems.add(new TileGraph.Problem(start, WARN_PARALLEL_ROUTE, false));
                    return;
                }

                edges.remove(existing);

                problems.add(new TileGraph.Problem(start, WARN_PARALLEL_ROUTE, false));
            }

            ReducedEdge edge = new ReducedEdge(start, tile, new ArrayList<>(path),
                new LinkedHashMap<>(commands), sumLength(path));

            edges.add(edge);
            edgeByPair.put(pair, edge);

            return;
        }

        // A tile already on this path means the walk is going in circles
        if (!visited.add(tile.toString())) return;

        for (Exit exit : graph.exits(tile, landing.getEntrySide()))
        {
            Map<String, accessorySetting> branchCommands = new LinkedHashMap<>(commands);

            // A branch whose settings contradict the path so far cannot be taken - the same rule the
            // autonomy model applies when it refuses a path with conflicting accessory commands
            if (!collectCommands(tile, exit.getState(), branchCommands)) continue;

            Landing next = graph.landing(tile, exit.getSide());

            if (next == null) continue;

            List<TileStep> branchPath = new ArrayList<>(path);
            branchPath.add(new TileStep(tile, exit.getRouteId(), exit.getState()));

            // each branch gets its own visited set, so one fork cannot block another
            continueWalk(start, next, branchPath, branchCommands, new HashSet<>(visited));
        }
    }

    /**
     * Adds the settings a tile's position requires, refusing the path if they contradict what it already
     * carries.
     *
     * Switch positions and signal greens are gathered identically here: the port map states what each
     * position needs, and the tile supplies the accessory it needs it from.
     *
     * @return false if this position conflicts with the path so far
     */
    private boolean collectCommands(TileKey tile, int state, Map<String, accessorySetting> into)
    {
        LayoutDiagramComponent component = graph.getTiles().get(tile);

        if (component == null) return true;

        Map<AccessorySlot, accessorySetting> required = TilePorts.commands(component.getType(), state);

        for (Map.Entry<AccessorySlot, accessorySetting> entry : required.entrySet())
        {
            Accessory accessory = entry.getKey() == AccessorySlot.PRIMARY
                ? component.getAccessory() : component.getAccessory2();

            // A tile whose position needs commanding but has no address wired cannot be commanded, so
            // this position is not usable.  Skipping the command instead would route trains over a switch
            // TrainControl is unable to throw, trusting it to already be lying the right way - which is
            // exactly the danger CUSTOM_PERM_* exists to describe, except here nobody said so.
            // TileGraph already raised a blocking error for this tile, scanning every tile rather than
            // only the ones a walk reaches.  Refusing the position here as well keeps the reducer honest
            // if it is ever run on a graph whose problems were ignored.
            if (accessory == null) return false;

            String name = accessory.getName();
            accessorySetting wanted = entry.getValue();
            accessorySetting already = into.get(name);

            if (already != null && already != wanted) return false;

            into.put(name, wanted);
        }

        return true;
    }

    private int sumLength(List<TileStep> path)
    {
        int total = 0;

        for (TileStep step : path)
        {
            total += Math.max(0, authored.getTileLength(step.getTile()));
        }

        return total;
    }

    // --- locks ------------------------------------------------------------------------------------

    /**
     * Two edges that share a tile are the same piece of railway and cannot run at once.
     *
     * The one exception is an overpass, where the two tracks are at different heights: crossing it by
     * different routes is not a conflict, though crossing it by the same one still is.  Getting that
     * backwards is costly either way - treating it as a conflict needlessly serialises two independent
     * routes, while treating a same-route share as safe would allow a real collision.
     */
    private void deriveLocks()
    {
        // index every edge by the locations it occupies
        Map<String, List<ReducedEdge>> byLocation = new LinkedHashMap<>();

        for (ReducedEdge edge : edges)
        {
            for (TileStep step : edge.getPath())
            {
                for (String location : locationsOf(step))
                {
                    List<ReducedEdge> here = byLocation.get(location);

                    if (here == null)
                    {
                        here = new ArrayList<>();
                        byLocation.put(location, here);
                    }

                    here.add(edge);
                }
            }
        }

        for (List<ReducedEdge> sharing : byLocation.values())
        {
            for (ReducedEdge a : sharing)
            {
                for (ReducedEdge b : sharing)
                {
                    if (a == b) continue;

                    // the two directions of one run are not rivals for the track; they are the same track
                    if (a.getStart().equals(b.getEnd()) && a.getEnd().equals(b.getStart())) continue;

                    lock(a, b);
                }
            }
        }
    }

    /**
     * The location identifiers a step occupies.
     *
     * Normally the tile itself.  An overpass is identified per route, so its two levels are separate
     * places.  A paired portal counts as one location with its partner, since a tunnel and its far end
     * are one piece of track drawn twice.
     */
    private List<String> locationsOf(TileStep step)
    {
        List<String> out = new ArrayList<>();

        LayoutDiagramComponent component = graph.getTiles().get(step.getTile());

        if (component != null && component.getType() == componentType.OVERPASS)
        {
            out.add(step.getTile().toString() + "/" + step.getRouteId());
        }
        else
        {
            out.add(step.getTile().toString());
        }

        return out;
    }

    private void lock(ReducedEdge a, ReducedEdge b)
    {
        Set<ReducedEdge> set = locks.get(a);

        if (set == null)
        {
            set = new LinkedHashSet<>();
            locks.put(a, set);
        }

        set.add(b);
    }
}
