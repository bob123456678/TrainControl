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
 * Layer 2: contracts the tile graph into the graph the autonomy model wants.  Only feedback tiles survive as nodes
 * (every s88 becomes a Point); a run of plain track becomes one edge, and a switch is a branch point that forks the
 * walk and contributes the accessory settings its position requires.  An edge keeps the tiles it covers, which
 * monitoring lights up and lock derivation intersects: edges sharing a tile cannot run at once.
 *
 * @author Adam
 */
public class GraphReducer
{
    /**
     * A stop on an edge's path: the tile, and which of its routes was used.  The route matters for one thing: an
     * overpass carries two tracks at different heights, so edges crossing it by different routes do not conflict.
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

    /** A Point: one feedback tile, with the s88 it carries. */
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
         * Whether the user designated this Point a station.  Only s88 tiles can be: Point refuses one without a sensor.
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

    /** A directed edge between two Points, with everything the autonomy model needs to run it. */
    public static class ReducedEdge
    {
        private final TileKey start;
        private final TileKey end;
        private final List<TileStep> path;
        private final Map<String, accessorySetting> commands;
        private final int length;

        /**
         * The track from the last SWITCH on this edge to its far end, the room a train has to stop in without standing
         * on the points.  `Integer.MIN_VALUE` when it crosses no switch (the caller keeps walking back), `-1` when some
         * tile after it has no length recorded.
         */
        private final int roomAtTheEnd;

        /**
         * Which side of the start tile this edge leaves by, and of the end tile it arrives at; null through a portal.
         * Kept because the reduction forgets them otherwise, which makes a reversal inexpressible (AutonomyBuilder's
         * split).
         */
        private final Side exitSide;
        private final Side entrySide;

        ReducedEdge(TileKey start, TileKey end, List<TileStep> path,
            Map<String, accessorySetting> commands, int length, Side exitSide, Side entrySide,
            int roomAtTheEnd)
        {
            this.start = start;
            this.end = end;
            this.path = Collections.unmodifiableList(path);
            this.commands = Collections.unmodifiableMap(commands);
            this.length = length;
            this.exitSide = exitSide;
            this.entrySide = entrySide;
            this.roomAtTheEnd = roomAtTheEnd;
        }

        /** The room after the last switch: `Integer.MIN_VALUE` when it crosses none, `-1` when NOTHING is measured. */
        public int getRoomAtTheEnd()
        {
            return roomAtTheEnd;
        }

        /** @return whether this edge crosses a switch, and so bounds where a train may come to rest */
        public boolean crossesASwitch()
        {
            return roomAtTheEnd != Integer.MIN_VALUE;
        }

        /** @return the side of the start tile the train leaves by, or null through a portal */
        public Side getExitSide()
        {
            return exitSide;
        }

        /** @return the side of the end tile the train arrives at, or null through a portal */
        public Side getEntrySide()
        {
            return entrySide;
        }

        public TileKey getStart()
        {
            return start;
        }

        public TileKey getEnd()
        {
            return end;
        }

        /** The tiles between the two Points, endpoints excluded: monitoring lights them, locks intersect them. */
        public List<TileStep> getPath()
        {
            return path;
        }

        /**
         * Accessory settings this edge requires, keyed by accessory name: switch positions and signal greens together.
         */
        public Map<String, accessorySetting> getCommands()
        {
            return commands;
        }

        /** The sum of the lengths assigned to the tiles this edge covers; zero unless the user assigned lengths. */
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

    /** Supplies what geometry cannot: the user's names, which Points are stations, and each tile's length. */
    public interface Authored
    {
        /** @return the user's name for this Point, or null to generate one from the coordinate */
        String getPointName(TileKey tile);

        /** @return whether the user designated this Point a station */
        boolean isStation(TileKey tile);

        /** @return the length assigned to this tile; 0 by default, which disables length accounting */
        int getTileLength(TileKey tile);

        /**
         * Whether this tile's 0 was answered on purpose (OB-274): measured track of no length to every length rule but
         * the own-tail one (OB-300), and not listed as missing.  False by default.
         */
        default boolean isTileLengthAnswered(TileKey tile)
        {
            return false;
        }
    }

    /** Authored data with nothing authored: generated names, no stations, no lengths. */
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

    // NOT FINAL, AND NEVER CHANGED ONCE HANDED OUT (OB-298): `reduce` builds new ones and swaps them in whole, so a reader
    // walking these - `AutonomyBuilder.splitSides` was one, on another thread - finishes on the railway it began on.
    private volatile Map<TileKey, ReducedPoint> points = new LinkedHashMap<>();
    private volatile List<ReducedEdge> edges = new ArrayList<>();
    private volatile Map<ReducedEdge, Set<ReducedEdge>> locks = new LinkedHashMap<>();

    /**
     * Sensors where a branch was refused because its switch settings contradicted the path to it.  Not warned about per
     * turnout, since a fork whose legs need one switch two ways is ORDINARY; worth saying only when the refusal was the
     * only way through, which is known at the end.
     */
    private volatile Set<TileKey> refusedForConflict = new LinkedHashSet<>();
    private volatile List<TileGraph.Problem> problems = new ArrayList<>();
    private volatile int isolatedFeedbackTiles = 0;

    // One edge per ordered pair of Points, because that is the model's own notion of edge identity.
    private volatile Map<String, ReducedEdge> edgeByPair = new LinkedHashMap<>();

    /** Two physical routes join the same pair of sensors; only the shorter is used. */
    public static final String WARN_PARALLEL_ROUTE = "autosetup.ui.warnParallelRoute";

    /** A run of track leaves a sensor and returns to it without passing another - a balloon loop. */
    public static final String WARN_SELF_LOOP = "autosetup.ui.warnSelfLoop";

    /** A run that exists on the diagram but cannot be driven, because it needs one switch two ways. */
    public static final String WARN_SWITCH_NEEDED_TWICE = "autosetup.ui.warnSwitchNeededTwice";

    public GraphReducer(TileGraph graph, Authored authored)
    {
        this.graph = graph;
        this.authored = authored == null ? new NothingAuthored() : authored;
    }

    /**
     * Runs the contraction.  Deterministic: the same diagram and authored data give the same graph, in the same order.
     */
    public void reduce()
    {
        // BUILT APART AND HANDED OVER WHOLE (OB-298): a fresh reducer does the work and its lists replace these only
        // when complete, so a reader walking one handed out before (`AutonomyBuilder.splitSides`, off the event thread)
        // never sees a half rebuild.  The edges go last.
        GraphReducer fresh = new GraphReducer(graph, authored);

        fresh.buildPoints();
        fresh.walkEdges();
        fresh.reportRunsNoSwitchCanServe();
        fresh.deriveLocks();

        this.refusedForConflict = fresh.refusedForConflict;
        this.problems = fresh.problems;
        this.isolatedFeedbackTiles = fresh.isolatedFeedbackTiles;
        this.edgeByPair = fresh.edgeByPair;
        this.locks = fresh.locks;
        this.points = fresh.points;
        this.edges = fresh.edges;
    }

    /** @return the Points, one per feedback tile that is connected to anything */
    public Map<TileKey, ReducedPoint> getPoints()
    {
        return Collections.unmodifiableMap(points);
    }

    /** @return every directed edge found */
    public List<ReducedEdge> getEdges()
    {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Which edges cannot run at the same time, derived from shared tiles so a diagram-built configuration has no gaps.
     */
    public Map<ReducedEdge, Set<ReducedEdge>> getLocks()
    {
        return Collections.unmodifiableMap(locks);
    }

    /** @return feedback tiles left out because nothing connects to them */
    public int getIsolatedFeedbackTiles()
    {
        return isolatedFeedbackTiles;
    }

    /**
     * Whether, and how, a train could get from one Point to another: breadth-first over the directed edges, so a
     * shortest run in edges.  The editor's connectivity TEST: it ignores lengths, exclusions and locks.
     *
     * @return the edges of a shortest run in order, or null when no run exists (or either end is not a Point here)
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to)
    {
        return findPath(from, to, Collections.<TileKey>emptySet());
    }

    /**
     * @param mayTurn the squares where a train may change direction, so a run through one may leave by the side it
     *        arrived at; elsewhere a train only goes forwards
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to, Set<TileKey> mayTurn)
    {
        return findPath(from, to, mayTurn, Collections.<TileKey>emptySet());
    }

    /**
     * @param mustTurn squares where changing direction is compulsory (no straight-through copy is built); overrides
     * mayTurn
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to, Set<TileKey> mayTurn,
        Set<TileKey> mustTurn)
    {
        return findPath(from, to, mayTurn, mustTurn, Collections.<TileKey, Set<Side>>emptyMap());
    }

    /**
     * @param barred the sides each square refuses arrivals by - the red arrows on the diagram (OB-120).  A barred side
     *        means a train may not come in and STOP that way, so only a destination hop is refused.  The START is
     *        exempt, as in the build: a standing train arrived by no side, and refusing it would stop a restricted
     *        station SENDING trains.
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to, Set<TileKey> mayTurn,
        Set<TileKey> mustTurn, java.util.Map<TileKey, Set<Side>> barred)
    {
        return findPath(from, to, mayTurn, mustTurn, barred, Collections.<TileKey>emptySet());
    }

    /**
     * The same, with squares that are CLOSED rather than merely barred.  This is the editor's *test a path* tool, which
     * runs while a setup is drawn and may not build; `testAutonomyDiagramReducer` holds it to the findings' answer
     * (`BuiltReach`).  A closed square is neither a way through nor a destination, in EVERY tier: `isPathClear` refuses
     * it as an intermediate and as the final point.  Only a closed START is exempt.
     *
     * @param barred arrival sides closed off - passed through, not stopped at
     * @param closed squares nothing may pass through
     * @return the path, or null when there is none
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to, Set<TileKey> mayTurn,
        Set<TileKey> mustTurn, java.util.Map<TileKey, Set<Side>> barred, Set<TileKey> closed)
    {
        return findPath(from, to, mayTurn, mustTurn, barred, closed, null);
    }

    /**
     * The same, starting a train only as it can stand at `from`.
     *
     * @param startArrivals the sides a train standing at `from` arrived by (`AutonomySession.startingArrivals`); null
     *        to start facing every way
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to, Set<TileKey> mayTurn,
        Set<TileKey> mustTurn, java.util.Map<TileKey, Set<Side>> barred, Set<TileKey> closed,
        Set<Side> startArrivals)
    {
        if (!points.containsKey(from) || !points.containsKey(to)) return null;

        if (from.equals(to)) return new ArrayList<ReducedEdge>();

        // The search state is a Point AND the side the train reached it by: what a train may do next depends on which
        // way it is pointing.  With no starting sides the start has no arrival side: the question is whether the TRACK
        // allows the journey.
        Map<String, ReducedEdge> arrivedBy = new LinkedHashMap<>();
        Map<String, String> cameFrom = new LinkedHashMap<>();
        Map<String, TileKey> tileOf = new LinkedHashMap<>();
        Map<String, Side> sideOf = new LinkedHashMap<>();

        java.util.ArrayDeque<String> frontier = new java.util.ArrayDeque<>();

        for (Side arrival : startArrivals == null ? Collections.<Side>singleton(null) : startArrivals)
        {
            String start = searchKey(from, arrival);

            arrivedBy.put(start, null);
            tileOf.put(start, from);
            sideOf.put(start, arrival);

            frontier.add(start);
        }

        while (!frontier.isEmpty())
        {
            String here = frontier.poll();

            TileKey at = tileOf.get(here);
            Side arrived = sideOf.get(here);

            for (ReducedEdge edge : edges)
            {
                if (!edge.getStart().equals(at)) continue;

                if (arrived != null)
                {
                    boolean back = edge.getExitSide() == arrived;

                    // Where turning is compulsory, going back is the ONLY move: no straight-through copy is built, so a
                    // run that carried on through one is a run no train could make.
                    if (mustTurn.contains(at))
                    {
                        if (!back) continue;
                    }
                    else if (back)
                    {
                        if (!mayTurn.contains(at)) continue;
                    }
                    // Carrying on has to be along the SAME TRACK: at a double-curve sensor (two curves crossing in one
                    // square, unconnected) comparing sides alone would let a train jump tracks mid-square.
                    else if (!onwardSides(at, arrived).contains(edge.getExitSide()))
                    {
                        continue;
                    }
                }

                // Barred sides only where they mean something (OB-120): a barred side bars COMING IN AND STOPPING, not
                // passing through, so the refusal belongs on the DESTINATION hop.
                String next = searchKey(edge.getEnd(), edge.getEntrySide());

                if (arrivedBy.containsKey(next)) continue;

                arrivedBy.put(next, edge);
                cameFrom.put(next, here);
                tileOf.put(next, edge.getEnd());
                sideOf.put(next, edge.getEntrySide());

                // ARRIVED, unless the square refuses to be arrived at that way (OB-120).  Tested HERE and not at the
                // hop above: refusing the hop would discard the search state, so the track BEYOND would never be
                // explored.  A closed square is not a destination either: asked before the arrival test, which accepts
                // the square and returns before the closure is consulted.
                if (closed != null && closed.contains(edge.getEnd())) continue;

                if (edge.getEnd().equals(to)
                    && !refusesArrival(barred, edge.getEnd(), edge.getEntrySide()))
                {
                    List<ReducedEdge> path = new ArrayList<>();

                    for (String step = next; arrivedBy.get(step) != null; step = cameFrom.get(step))
                    {
                        path.add(0, arrivedBy.get(step));
                    }

                    return path;
                }

                frontier.add(next);
            }
        }

        return null;
    }

    /**
     * Whether a square refuses trains arriving by a given side (OB-120), as `AutonomyBuilder.arrivalAllowed` does for
     * a copy of a split square.  A null side is the starting square, never barred.
     *
     * @return true when the arrival is not allowed
     */
    private boolean refusesArrival(java.util.Map<TileKey, Set<Side>> barred, TileKey tile, Side by)
    {
        if (by == null || barred == null || barred.isEmpty()) return false;

        Set<Side> sides = barred.get(tile);

        return sides != null && sides.contains(by);
    }

    /**
     * The sides a train that arrived by the given side can carry on out of: the track it stands on, never the arrival
     * side.
     */
    private Set<Side> onwardSides(TileKey tile, Side arrival)
    {
        Set<Side> out = new java.util.LinkedHashSet<>();

        for (TileGraph.Exit exit : graph.exits(tile, arrival))
        {
            if (exit.getSide() != null) out.add(exit.getSide());
        }

        return out;
    }

    /** A square together with the side a train reached it by, which is what the path search walks over. */
    private static String searchKey(TileKey tile, Side arrival)
    {
        return tile + "|" + (arrival == null ? "-" : arrival.name());
    }

    /**
     * The tile graph this reduction was made from.  The reduction is Point-to-Point and cannot say which of a square's
     * tracks an edge runs on, so the split and the path test ask the graph.
     */
    public TileGraph getGraph()
    {
        return graph;
    }

    public List<TileGraph.Problem> getProblems()
    {
        return Collections.unmodifiableList(problems);
    }

    // points

    private void buildPoints()
    {
        for (TileKey tile : graph.getFeedbackTiles())
        {
            LayoutDiagramComponent component = graph.getTiles().get(tile);

            if (component == null) continue;

            // A sensor nothing connects to is counted and left out rather than emitted to fail validation later
            if (!hasAnyConnection(tile))
            {
                isolatedFeedbackTiles++;
                continue;
            }

            // A DOUBLE-CURVE SENSOR IS NOT REPORTED: the reduction makes ONE Point of its two curves, with one s88, and
            // which curve carries the contact is not in the diagram (the .cs2 records one tile), so a check could only
            // say "I do not know whether that matters" of every such square.  Adam: "No warning needed otherwise."

            String name = authored.getPointName(tile);

            if (name == null || name.trim().isEmpty())
            {
                name = generatedName(tile);
            }

            // Feedback is keyed on the RAW address, not the halved one: MarklinControlStation registers and looks up
            // feedback by getRawAddress(), while CS2File halves the value for accessories.
            points.put(tile, new ReducedPoint(tile, component.getRawAddress(), name,
                authored.isStation(tile)));
        }
    }

    /**
     * A default name from the coordinate, unique by construction (a station and its approach guards share one sensor).
     */
    public static String generatedName(TileKey tile)
    {
        return tile.getPage() + " " + tile.getX() + "," + tile.getY();
    }

    /**
     * Whether anything at all adjoins this tile: physical connection, not traversability, so a one-way-away sensor is
     * still a Point.
     */

    private boolean hasAnyConnection(TileKey tile)
    {
        for (Side side : Side.values())
        {
            if (graph.landing(tile, side) != null) return true;
        }

        return false;
    }

    // edges

    /**
     * Records a problem unless the same one is already recorded: walkEdges reaches one FACT several times (a balloon
     * loop is walked out east and back by west), and one problem reported twice reads as two things to fix.
     */
    private void noteOnce(TileGraph.Problem problem)
    {
        for (TileGraph.Problem seen : problems)
        {
            if (seen.getTile() != null && seen.getTile().equals(problem.getTile())
                && seen.getMessageKey().equals(problem.getMessageKey())) return;
        }

        problems.add(problem);
    }

    private void walkEdges()
    {
        for (TileKey start : points.keySet())
        {
            // A train standing here may leave by any side its own routes allow: leaving by X is the same traversal as
            // entering by the other side of that route.
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

    /** Follows one route out of a Point until it reaches the next Point, forking at every switch. */
    private void walk(TileKey start, Exit firstExit, List<TileStep> path,
        Map<String, accessorySetting> commands)
    {
        Landing landing = graph.landing(start, firstExit.getSide());

        if (landing == null) return;

        // Empty, deliberately: the start is a Point, so a walk that came back to it is caught by the self-loop test.
        Set<String> visited = new HashSet<>();

        continueWalk(start, firstExit.getSide(), landing, path, commands, visited);
    }

    /**
     * @param exitSide the side of the START tile this walk left by, carried so the landing edge can record where it
     * began
     */
    private void continueWalk(TileKey start, Side exitSide, Landing landing, List<TileStep> path,
        Map<String, accessorySetting> commands, Set<String> visited)
    {
        if (path.size() > MAX_PATH_TILES) return;

        TileKey tile = landing.getTile();

        // Reached the next Point: the edge is everything between the two
        if (points.containsKey(tile))
        {
            // An edge's identity in the autonomy model is its pair of Point NAMES, so two routes between the same two
            // sensors (a passing loop, double track) cannot both be emitted: createEdge throws and parseAuto
            // invalidates the whole configuration.  A self-loop is the same problem: the model has no edge from a Point
            // to itself.
            if (tile.equals(start))
            {
                noteOnce(new TileGraph.Problem(start, WARN_SELF_LOOP, false));
                return;
            }

            String pair = start.toString() + " -> " + tile.toString();

            ReducedEdge existing = edgeByPair.get(pair);

            if (existing != null)
            {
                // Keep the shorter route, the one a train would be given anyway, and say the other exists: silently
                // dropping half a layout's track would be worse.
                if (path.size() >= existing.getPath().size())
                {
                    noteOnce(new TileGraph.Problem(start, WARN_PARALLEL_ROUTE, false));
                    return;
                }

                edges.remove(existing);

                noteOnce(new TileGraph.Problem(start, WARN_PARALLEL_ROUTE, false));
            }

            ReducedEdge edge = new ReducedEdge(start, tile, new ArrayList<>(path),
                new LinkedHashMap<>(commands), sumLength(path) + lengthOf(tile),
                exitSide, landing.getEntrySide(), roomAfterTheLastSwitch(path, tile));

            edges.add(edge);
            edgeByPair.put(pair, edge);

            return;
        }

        // Going in circles - judged by the tile AND the side entered by: a square can be crossed twice by one run on
        // two tracks (a crossing or an overpass), which is ordinary, while a genuine circle re-enters by the same side.
        // AND BY THE TRACK, on a two-square crossing (OB-320): its tracks share the side its squares meet at, so an
        // entry there is remembered with the square before and its road there.
        TileStep before = path.isEmpty() ? null : path.get(path.size() - 1);

        TileKey previous = before == null ? null : before.getTile();
        RouteId previousRoute = before == null ? null : before.getRouteId();

        Side lane = graph.legAcross(tile, landing.getEntrySide(), previous, previousRoute);

        if (!visited.add(tile.toString() + "|" + landing.getEntrySide() + (lane == null ? "" : "|" + lane))) return;

        for (Exit exit : graph.exits(tile, landing.getEntrySide(), previous, previousRoute))
        {
            Map<String, accessorySetting> branchCommands = new LinkedHashMap<>(commands);

            // A branch whose settings contradict the path so far cannot be taken (the autonomy model's own rule).
            if (!collectCommands(tile, exit.getState(), branchCommands))
            {
                refusedForConflict.add(start);
                continue;
            }

            Landing next = graph.landing(tile, exit.getSide());

            if (next == null) continue;

            List<TileStep> branchPath = new ArrayList<>(path);
            branchPath.add(new TileStep(tile, exit.getRouteId(), exit.getState()));

            // each branch gets its own visited set, so one fork cannot block another
            continueWalk(start, exitSide, next, branchPath, branchCommands, new HashSet<>(visited));
        }
    }

    /**
     * Adds the settings a tile's position requires, refusing the path if they contradict what it already carries.
     * Switch positions and signal greens are gathered identically.
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

            // A position that needs commanding but has no address wired cannot be commanded: skipping it would route
            // trains over a switch TrainControl cannot throw.  TileGraph already raised a blocking error; refusing here
            // keeps the reducer honest on a graph whose problems were ignored.
            if (accessory == null) return false;

            String name = accessory.getName();
            accessorySetting wanted = entry.getValue();
            accessorySetting already = into.get(name);

            if (already != null && already != wanted) return false;

            into.put(name, wanted);
        }

        return true;
    }

    /**
     * The length assigned to one tile.  The END of an edge is included in its length and the start is not: a route is
     * a chain of edges, so counting the tile a train ARRIVES on gives every tile once.
     */
    private int lengthOf(TileKey tile)
    {
        return Math.max(0, authored.getTileLength(tile));
    }

    /**
     * Whether the berth-room walk stops at this tile: a switch, or a turnout declared permanently set.
     *
     * A permanent turnout is still the last switch (OB-233): nothing can throw it, but a train standing across it
     * fouls the merge as it would a throwable one.  So it is `isSwitch()` plus the declared permanent types;
     * `isSwitch()` is left alone because some of its call sites offer an accessory address, which a permanent turnout
     * has none of.  A TWO-SQUARE CROSSING is a crossing (OB-320): nothing merges at its squares, so it does not end
     * the room.
     *
     * @return true when the room ends here
     */
    private boolean boundsTheRoom(TileKey tile, LayoutDiagramComponent component)
    {
        if (graph.crossingPartner(tile) != null) return false;

        return component != null
            && (component.isSwitch() || TileGraph.isPermanentTurnout(component.getType()));
    }

    /**
     * The track between the last switch on this edge and the square it ends at, endpoint included, the switch tile
     * itself NOT counted: the train comes to rest clear of the points.  Adam's ruling: the room is measured "between
     * the switch and the station", and *"for the switches, for simplicity, let's use any direction, that way we are
     * guaranteed to be safe"*, so which way the path crosses the points does not enter into it.
     *
     * "A switch" is what `boundsTheRoom` says.  Walks backwards and stops at the first switch, the binding one: a train
     * that fits between the last switch and the station fits between any earlier one.
     *
     * @return the measured room, `Integer.MIN_VALUE` when this edge crosses no switch; `-1` when it crosses one and
     *  NOT ONE tile of the stretch is measured; 0, not `-1`, where every one was answered 0 on purpose
     */
    private int roomAfterTheLastSwitch(List<TileStep> path, TileKey end)
    {
        // THE TOTAL, NOT AN ALL-OR-NOTHING (Adam, 2026-09-06).
        //
        // **"You need to measure total distance between points, not validate that every edge has a
        // length > 0.  It is only indeterminate if the entire logical segment has length 0."**
        //
        // This kept a `measured` flag that any single unmeasured tile cleared, and then threw away
        // every unit it had counted: `return measured ? room : -1`.  On his railway the run into
        // BottomMainPost is twelve tiles with one of them measured at 1, so the answer was -1 -
        // "bounded but unmeasured" - and `measuredRoomAtTheEndOf` declined to judge, which let a
        // four-unit train into one unit of room.  The measured tile was right there and was discarded
        // for the company it kept.
        //
        // Unmeasured tiles now contribute nothing, which is the same doctrine as the protrusion walk
        // and the berth rule: only positive lengths are determinate.  A stretch where NOTHING is
        // measured still answers -1, because that is genuinely no information rather than a small
        // number.
        //
        // This UNDER-states the room by however much is unmeasured, and that is the safe direction:
        // it refuses a train that might have fitted rather than admitting one that does not.  The
        // editor already names the unmeasured tiles - `unmeasuredAfterTheLastSwitch`, just below - so
        // the way to a bigger number is to measure them.
        int atTheEnd = authored.getTileLength(end);

        int room = Math.max(0, atTheEnd);

        // AND WHETHER EVERY TILE IS KNOWN - a length, or a 0 answered on purpose.  A stretch answered 0 throughout is a
        // room of 0, not no information: trains in atomic mode must still be let in if the total track lengths allow.
        boolean known = atTheEnd > 0 || answeredAtZero(end);

        for (int i = path.size() - 1; i >= 0; i--)
        {
            TileKey tile = path.get(i).getTile();

            LayoutDiagramComponent component = graph.getTiles().get(tile);

            if (boundsTheRoom(tile, component))
            {
                return room > 0 ? room : known ? 0 : -1;
            }

            int here = authored.getTileLength(tile);

            if (here > 0) room += here;
            else if (!answeredAtZero(tile)) known = false;
        }

        return Integer.MIN_VALUE;
    }

    /**
     * The squares in an edge's bounding stretch that nobody has measured: the mirror of `roomAfterTheLastSwitch`, so
     * the editor asks for exactly what the guard needs.  The switch tile is not asked for, the far endpoint is.  An
     * edge that crosses no switch has its WHOLE length asked for and the walk stops at its start: asking for a whole
     * route is a nag, and the guard declines to judge what it cannot measure.
     *
     * @return the unmeasured tiles, empty when the stretch is fully measured
     */
    public List<TileKey> unmeasuredAfterTheLastSwitch(ReducedEdge edge)
    {
        List<TileKey> out = new ArrayList<>();

        if (edge == null) return out;

        // AND IT STOPS ASKING ONCE THE STRETCH IS MEASURED AT ALL (MT-364): the notice must agree with the guard, which
        // answers a partly measured stretch with what it knows (MT-305), so that carries no notice.  A stretch with
        // nothing measured still names every square: there the guard is blind, not pessimistic.
        int measured = Math.max(0, authored.getTileLength(edge.getEnd()));

        if (measured <= 0) out.add(edge.getEnd());

        List<TileStep> path = edge.getPath();

        for (int i = path.size() - 1; i >= 0; i--)
        {
            TileKey tile = path.get(i).getTile();

            LayoutDiagramComponent component = graph.getTiles().get(tile);

            if (boundsTheRoom(tile, component)) break;

            int here = authored.getTileLength(tile);

            // A ROUTE TILE IS NOT TRACK TO MEASURE (OB-273): it takes no length, so it is never the square asked for.
            if (here > 0) measured += here;
            else if (!takesNoLength(tile)) out.add(tile);
        }

        return measured > 0 ? new ArrayList<TileKey>() : out;
    }

    /** @return whether the tile takes no length (`TilePorts.takesNoLength`), so is never asked for one */
    private boolean takesNoLength(TileKey tile)
    {
        LayoutDiagramComponent component = graph.getTiles().get(tile);

        return component != null && TilePorts.takesNoLength(component.getType());
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

    // locks

    /**
     * Says so where a sensor lost its only way out to a switch it needed two ways.  Only where NOTHING got out: a fork
     * refusing one of its legs is what a switch is for.  A sensor with no edge at all is the case the user cannot
     * otherwise see.
     */
    private void reportRunsNoSwitchCanServe()
    {
        for (TileKey start : refusedForConflict)
        {
            boolean gotOut = false;

            for (ReducedEdge edge : edges)
            {
                if (edge.getStart().equals(start))
                {
                    gotOut = true;
                    break;
                }
            }

            if (!gotOut) problems.add(new TileGraph.Problem(start, WARN_SWITCH_NEEDED_TWICE, false));
        }
    }

    /**
     * Two edges that share a tile are the same piece of railway and cannot run at once, except on the tile types whose
     * roads never meet: an overpass and the two kinds of double curve.  Crossing one by different routes is no
     * conflict, though the same route still is.
     */
    private void deriveLocks()
    {
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
     * The location identifiers a step occupies: normally the tile itself, but an overpass, a DOUBLE CURVE and a
     * feedback double curve are identified per route, so their roads are separate places (one location would make
     * `deriveLocks` rivals of them and the tail walk blame the road the train is not on).  A CROSSING stays one
     * place: a train on one road is on the other's metal.
     *
     * A paired portal is NOT unioned with its partner: a portal tile carries no feedback, so a walk across the jump
     * records BOTH tiles as steps and the two edges share the first tile's location key anyway.
     */
    private List<String> locationsOf(TileStep step)
    {
        List<String> out = new ArrayList<>();

        LayoutDiagramComponent component = graph.getTiles().get(step.getTile());

        if (component != null && (component.getType() == componentType.OVERPASS
            || component.getType() == componentType.DOUBLE_CURVE
            || component.getType() == componentType.FEEDBACK_DOUBLE_CURVE))
        {
            out.add(step.getTile().toString() + "/" + step.getRouteId());
        }
        else
        {
            out.add(step.getTile().toString());
        }

        return out;
    }

    /** One place an edge runs over, and what it measures. */
    public static final class Place
    {
        private final String id;

        private final int length;

        /** Measures 0 because it was answered 0 on purpose, rather than because nobody has measured it */
        private final boolean answered;

        /** The square the place is on - an overpass's two levels are two places on one square */
        private final TileKey tile;

        Place(String id, int length)
        {
            this(id, length, false, null);
        }

        Place(String id, int length, boolean answered, TileKey tile)
        {
            this.id = id;
            this.length = length;
            this.answered = answered;
            this.tile = tile;
        }

        /** @return the square this place is on, or null where it was not said */
        public TileKey getTile()
        {
            return this.tile;
        }

        /** @return whether this place measures 0 because it was answered 0 on purpose */
        public boolean isAnswered()
        {
            return this.answered;
        }

        /** @return the location identifier, as `locationsOf` names it */
        public String getId()
        {
            return this.id;
        }

        /** @return what this place measures, never negative */
        public int getLength()
        {
            return this.length;
        }
    }

    /**
     * The places an edge runs over, in order from its start, each with what it measures (OB-207).  `deriveLocks`
     * intersects `locationsOf` sets between edges; handing the runtime only that RESULT would let `Layout` answer at
     * whole-edge grain (a one-unit train in the FIRST tile of a twelve-tile run would refuse every path sharing any
     * other).  The places are not extra nodes: splitting the graph at switches would rename every edge.  The far
     * endpoint is included and the near one is not, as in `lengthOf`, so these lengths sum to `getLength()`.
     *
     * @return its places, in path order, empty for a null edge
     */
    public List<Place> placesAlong(ReducedEdge edge)
    {
        List<Place> out = new ArrayList<>();

        if (edge == null) return out;

        for (TileStep step : edge.getPath())
        {
            for (String id : locationsOf(step))
            {
                out.add(new Place(id, lengthOf(step.getTile()), answeredAtZero(step.getTile()), step.getTile()));
            }
        }

        out.add(new Place(edge.getEnd().toString(), lengthOf(edge.getEnd()), answeredAtZero(edge.getEnd()),
            edge.getEnd()));

        return out;
    }

    /**
     * Whether a square measures 0 because it was answered 0 (so the runtime does not call it missing), or because it
     * takes no length at all, a route tile (OB-273).  Unmarked, a leg answered 0 around a route tile read as unmeasured
     * track to every rule that asks `Edge.isMeasured`.
     */
    private boolean answeredAtZero(TileKey tile)
    {
        return lengthOf(tile) == 0 && (takesNoLength(tile) || authored.isTileLengthAnswered(tile));
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
