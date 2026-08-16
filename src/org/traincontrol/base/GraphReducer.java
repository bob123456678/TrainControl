package org.traincontrol.base;

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
import org.traincontrol.base.TileGraph.Exit;
import org.traincontrol.base.TileGraph.Landing;
import org.traincontrol.base.TileGraph.RouteId;
import org.traincontrol.base.TileGraph.TileKey;
import org.traincontrol.base.TilePorts.AccessorySlot;
import org.traincontrol.base.TilePorts.Side;

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
    private String generatedName(TileKey tile)
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
            edges.add(new ReducedEdge(start, tile, new ArrayList<>(path),
                new LinkedHashMap<>(commands), sumLength(path)));
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
