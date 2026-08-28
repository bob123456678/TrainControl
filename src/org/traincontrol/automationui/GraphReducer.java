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

        /**
         * Which side of the start tile this edge leaves by, and which side of the end tile it arrives
         * at.  Null for a move through a portal, which has no side on the grid.
         *
         * The reduction is otherwise Point-to-Point and forgets this, which is exactly what makes a
         * reversal inexpressible: leaving by the side you came in on and carrying straight on look the
         * same once both are "an edge from T".  Anything that has to tell those apart - see
         * AutonomyBuilder's split - needs the sides, so they are kept.
         */
        private final Side exitSide;
        private final Side entrySide;

        ReducedEdge(TileKey start, TileKey end, List<TileStep> path,
            Map<String, accessorySetting> commands, int length, Side exitSide, Side entrySide)
        {
            this.start = start;
            this.end = end;
            this.path = Collections.unmodifiableList(path);
            this.commands = Collections.unmodifiableMap(commands);
            this.length = length;
            this.exitSide = exitSide;
            this.entrySide = entrySide;
        }

        /**
         * @return the side of the start tile the train leaves by, or null through a portal
         */
        public Side getExitSide()
        {
            return exitSide;
        }

        /**
         * @return the side of the end tile the train arrives at, or null through a portal
         */
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

    /**
     * Sensors where a branch was refused because its switch settings contradicted the path to it.
     *
     * Recorded rather than warned about at the time.  A fork whose two legs need one switch two ways is
     * ORDINARY - it is what a switch is - and warning per branch would put a line on the findings list
     * for every turnout on the layout.  What is worth saying is the case where the refusal was the only
     * way through: a wye or a folded loop with no sensor between the two crossings leaves the two ends
     * with no edge between them at all, and the user's first sight of that is a connectivity test
     * answering no.  So the note is kept and read at the end, when it is known whether anything else
     * got out of that sensor.
     */
    private final Set<TileKey> refusedForConflict = new LinkedHashSet<>();
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
     * A run that exists on the diagram but cannot be driven, because it needs one switch two ways.
     */
    public static final String WARN_SWITCH_NEEDED_TWICE = "autosetup.ui.warnSwitchNeededTwice";

    /**
     * A double-curve sensor with track on both of its curves.  The s88 is on ONE of them; the other is
     * a second track that happens to cross the same square.
     */
    public static final String WARN_DOUBLE_CURVE_SENSOR = "autosetup.ui.warnDoubleCurveSensor";

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
        refusedForConflict.clear();
        problems.clear();
        isolatedFeedbackTiles = 0;

        buildPoints();
        walkEdges();
        reportRunsNoSwitchCanServe();
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
        return findPath(from, to, Collections.<TileKey>emptySet());
    }

    /**
     * @param mayTurn the squares where a train is allowed to change direction, so a run through one of
     *        them may leave by the side it arrived at.  Everywhere else it may not: a train is pointing
     *        away from where it came from, and it can only go forwards.
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to, Set<TileKey> mayTurn)
    {
        return findPath(from, to, mayTurn, Collections.<TileKey>emptySet());
    }

    /**
     * @param mustTurn the squares where changing direction is not optional.  Carrying straight on is not
     *        a move at one of these - the build emits no straight-through copy - so a run that passed
     *        through one would be a run no train could make.  Listing a square here overrides mayTurn.
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to, Set<TileKey> mayTurn,
        Set<TileKey> mustTurn)
    {
        return findPath(from, to, mayTurn, mustTurn, Collections.<TileKey, Set<Side>>emptyMap());
    }

    /**
     * @param barred the sides each square refuses arrivals by - the red arrows on the diagram (OB-120).
     *
     * WHAT A BARRED SIDE MEANS: a train may not come in and STOP that way. The build says so plainly -
     * `AutonomyBuilder` emits the barred copy as a non-station and notes that it "still carries
     * traffic; it is simply not somewhere a train can be sent" - and the chevrons drawn for it are
     * documented the same way.
     *
     * An earlier version of this javadoc claimed the build dropped those copies entirely, and the code
     * under it refused the move wherever it appeared. That described a railway more restricted than the
     * one that exists, and this walk feeds the editor's path test: it drew no route for journeys the
     * railway would happily run. Corrected 2026-08-28 after an independent review.
     *
     * The START is exempt, as it is in the build: a train standing at a square did not arrive there by
     * any side, and refusing it would make a restricted station unable to SEND trains rather than
     * unable to receive them.
     */
    public List<ReducedEdge> findPath(TileKey from, TileKey to, Set<TileKey> mayTurn,
        Set<TileKey> mustTurn, java.util.Map<TileKey, Set<Side>> barred)
    {
        if (!points.containsKey(from) || !points.containsKey(to)) return null;

        if (from.equals(to)) return new ArrayList<ReducedEdge>();

        // The search state is a Point AND the side the train reached it by, not the Point alone.  Those
        // are genuinely different situations - what a train may do next depends on which way it is
        // pointing - and searching over Points alone produced runs that reversed at squares where no
        // train can, which is what the test told the user the railway would do.
        //
        // The starting Point has no arrival side: the train is already standing there, and the test
        // question is whether the TRACK allows the journey at all.
        Map<String, ReducedEdge> arrivedBy = new LinkedHashMap<>();
        Map<String, String> cameFrom = new LinkedHashMap<>();
        Map<String, TileKey> tileOf = new LinkedHashMap<>();
        Map<String, Side> sideOf = new LinkedHashMap<>();

        String start = searchKey(from, null);

        arrivedBy.put(start, null);
        tileOf.put(start, from);
        sideOf.put(start, null);

        java.util.ArrayDeque<String> frontier = new java.util.ArrayDeque<>();
        frontier.add(start);

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

                    // Where turning is compulsory, going back is the ONLY move: the build emits no
                    // straight-through copy of such a square, so a run that carried on through one is a
                    // run no train could make.
                    if (mustTurn.contains(at))
                    {
                        if (!back) continue;
                    }
                    else if (back)
                    {
                        if (!mayTurn.contains(at)) continue;
                    }
                    // Carrying on has to be carrying on along the SAME TRACK.  Comparing sides alone was
                    // not enough at a double-curve sensor, which is two curves crossing in one square
                    // with no connection between them: a train arriving on one of them was allowed onto
                    // any edge that did not leave by the side it came in at, including the edges of the
                    // other curve - a run that jumps tracks in mid-square, which is the same class of
                    // impossible move the arrival-side search was written to get rid of.
                    else if (!onwardSides(at, arrived).contains(edge.getExitSide()))
                    {
                        continue;
                    }
                }

                // The red arrows, and only where they mean something (OB-120, corrected 2026-08-28).
                //
                // A barred side says a train may not COME IN AND STOP that way. It does not say the
                // square cannot be passed through: `AutonomyBuilder` still emits the barred copy, and
                // its own comment says so - "the copy still exists and still carries traffic; it is
                // simply not somewhere a train can be sent". This used to refuse the move outright,
                // on a javadoc of mine claiming the build dropped such copies, which it does not.
                //
                // So the refusal belongs on the DESTINATION hop, which is what OB-120 was filed about:
                // "Test a path drew routes INTO stations that refuse arrivals from that side."
                if (edge.getEnd().equals(to)
                    && refusesArrival(barred, edge.getEnd(), edge.getEntrySide()))
                {
                    continue;
                }

                String next = searchKey(edge.getEnd(), edge.getEntrySide());

                if (arrivedBy.containsKey(next)) continue;

                arrivedBy.put(next, edge);
                cameFrom.put(next, here);
                tileOf.put(next, edge.getEnd());
                sideOf.put(next, edge.getEntrySide());

                if (edge.getEnd().equals(to))
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
     * Whether a square refuses trains arriving by a given side (OB-120).
     *
     * The same test `AutonomyBuilder.arrivalAllowed` makes when it decides which copies of a split
     * square to emit, written once here so the two cannot drift: a run this walk allows and the build
     * refuses is a route the editor draws and the railway will never take.
     *
     * A null side is the starting square, which has no arrival and is never barred.
     *
     * @param barred square to the sides it refuses, never null
     * @param tile the square being arrived at
     * @param by the side it is being arrived by
     * @return true when the arrival is not allowed
     */
    private boolean refusesArrival(java.util.Map<TileKey, Set<Side>> barred, TileKey tile, Side by)
    {
        if (by == null || barred == null || barred.isEmpty()) return false;

        Set<Side> sides = barred.get(tile);

        return sides != null && sides.contains(by);
    }

    /**
     * The sides a train that arrived at this square by the given side can carry on out of - the track it
     * is actually standing on, rather than every side the square happens to have.
     *
     * Never includes the arrival side: the tile graph builds its exits from routes, and a route never
     * leads back out the way it came in.  Turning round is therefore always the separate case.
     */
    /**
     * Every tile a train standing at {@code from} can reach, honouring the arrival-side split exactly as
     * findPath does.
     *
     * The plain adjacency over getEdges() over-reports this: it lets a run cross between the two
     * independent curves of a double-curve sensor, because a Point alone does not remember which side a
     * train came in by.  This is one (tile, arrival-side) BFS - the same frontier discipline findPath
     * uses - collecting the tiles reached rather than reconstructing one path, so the reachability it
     * reports is the reachability the emitted split graph and Layout.bfs actually have.
     *
     * @param from the square a train is standing on
     * @param mayTurn squares where a train may turn round (reversal permitted)
     * @param mustTurn squares where every arriving train must turn round
     * @return the tiles reachable, including {@code from} itself
     */
    public Set<TileKey> reachableTiles(TileKey from, Set<TileKey> mayTurn, Set<TileKey> mustTurn)
    {
        return reachableTiles(from, mayTurn, mustTurn, Collections.<TileKey, Set<Side>>emptyMap());
    }

    /**
     * @param barred the sides each square refuses arrivals by (OB-120), as findPath takes them - so the
     *        two walks cannot disagree about which runs exist
     */
    public Set<TileKey> reachableTiles(TileKey from, Set<TileKey> mayTurn, Set<TileKey> mustTurn,
        java.util.Map<TileKey, Set<Side>> barred)
    {
        Set<TileKey> reached = new java.util.LinkedHashSet<>();

        if (from == null || !points.containsKey(from)) return reached;

        // The starting Point has no arrival side - the train is already standing there, and the
        // question is whether the TRACK allows the journey onward.  Same as findPath's start state.
        reached.add(from);

        Set<String> visited = new java.util.LinkedHashSet<>();
        Map<String, TileKey> tileOf = new LinkedHashMap<>();
        Map<String, Side> sideOf = new LinkedHashMap<>();

        String start = searchKey(from, null);
        visited.add(start);
        tileOf.put(start, from);
        sideOf.put(start, null);

        java.util.ArrayDeque<String> frontier = new java.util.ArrayDeque<>();
        frontier.add(start);

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

                    // The same three-way rule findPath walks: where turning is compulsory the only move
                    // is back; where it is optional, going back needs permission; otherwise the move
                    // has to carry on along the track actually arrived on, not merely leave by some
                    // other side - which is what confines a double curve to its own arm.
                    if (mustTurn.contains(at))
                    {
                        if (!back) continue;
                    }
                    else if (back)
                    {
                        if (!mayTurn.contains(at)) continue;
                    }
                    else if (!onwardSides(at, arrived).contains(edge.getExitSide()))
                    {
                        continue;
                    }
                }

                // THROUGH a barred side, but not TO it (OB-120, corrected 2026-08-28).
                //
                // Reached means "a train could be sent here", which is the question both callers ask -
                // can this station reach another, can this reversing point reach anything. A square a
                // train may pass through but not stop at is not an answer to that, and the track beyond
                // it is still perfectly reachable.
                //
                // Refusing the move outright, which is what this did, made the findings describe a
                // more restricted railway than the one that exists: a station whose only route ran
                // through a barred side was reported as reaching nothing, and Adam acts on those
                // warnings by editing his diagram.
                if (!refusesArrival(barred, edge.getEnd(), edge.getEntrySide()))
                {
                    reached.add(edge.getEnd());
                }

                String next = searchKey(edge.getEnd(), edge.getEntrySide());

                if (visited.add(next))
                {
                    tileOf.put(next, edge.getEnd());
                    sideOf.put(next, edge.getEntrySide());
                    frontier.add(next);
                }
            }
        }

        return reached;
    }

    private Set<Side> onwardSides(TileKey tile, Side arrival)
    {
        Set<Side> out = new java.util.LinkedHashSet<>();

        for (TileGraph.Exit exit : graph.exits(tile, arrival))
        {
            if (exit.getSide() != null) out.add(exit.getSide());
        }

        return out;
    }

    /**
     * A square together with the side a train reached it by, which is what the path search walks over.
     */
    private static String searchKey(TileKey tile, Side arrival)
    {
        return tile + "|" + (arrival == null ? "-" : arrival.name());
    }

    /**
     * The tile graph this reduction was made from.
     *
     * Exposed because the reduction is Point-to-Point and cannot say which of a square's tracks an edge
     * runs on: two sides of one tile may belong to two routes that never meet.  Anything that has to
     * tell a through move from a track change - the split, the path test - has to ask the graph.
     *
     * @return
     */
    public TileGraph getGraph()
    {
        return graph;
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

            // A double-curve sensor draws two curves on one square, and the s88 is on ONE of them -
            // the other is simply a second track crossing the same tile (author, 2026-08-16).  This
            // was blocking, and refused to make the tile a Point at all, on the reading that one
            // sensor covered both tracks.  It does not, so the sensor is real and the Point is
            // emitted; what remains is that nothing here records WHICH curve carries it, so a train
            // on the other one is expected to trigger a sensor it never reaches.  That is worth
            // saying and is not worth refusing.
            if (component.getType() == componentType.FEEDBACK_DOUBLE_CURVE
                && bothCurvesConnected(tile))
            {
                problems.add(new TileGraph.Problem(tile, WARN_DOUBLE_CURVE_SENSOR, false));
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

    /**
     * Records a problem unless the same one is already recorded.
     *
     * walkEdges launches one walk per side of every Point and per exit of every side, so one FACT is
     * reached several times: a balloon loop carrying a single sensor is walked out east and back by
     * west, then out west and back by east, and reported twice.  N reconverging routes between one pair
     * of Points report N-1 times, all against the same square.
     *
     * The rule and the reasoning are TileGraph.validatePortals's, which filters on tile and key for
     * exactly this: "the same tile and the same message twice is one problem reported twice - which
     * reads as two things to fix and cannot be, since fixing it makes both disappear."
     *
     * @param problem the problem to record
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

        // Empty, deliberately.  Seeding it with the start tile did nothing: the start is a Point, so a
        // walk that came back to it is caught by the self-loop test above the circle test and never
        // reaches this set.
        Set<String> visited = new HashSet<>();

        continueWalk(start, firstExit.getSide(), landing, path, commands, visited);
    }

    /**
     * @param exitSide which side of the START tile this walk left by, carried the whole way so the edge
     *        that eventually lands can record where it began
     */
    private void continueWalk(TileKey start, Side exitSide, Landing landing, List<TileStep> path,
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
                noteOnce(new TileGraph.Problem(start, WARN_SELF_LOOP, false));
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
                    noteOnce(new TileGraph.Problem(start, WARN_PARALLEL_ROUTE, false));
                    return;
                }

                edges.remove(existing);

                noteOnce(new TileGraph.Problem(start, WARN_PARALLEL_ROUTE, false));
            }

            ReducedEdge edge = new ReducedEdge(start, tile, new ArrayList<>(path),
                new LinkedHashMap<>(commands), sumLength(path) + lengthOf(tile),
                exitSide, landing.getEntrySide());

            edges.add(edge);
            edgeByPair.put(pair, edge);

            return;
        }

        // Going in circles - but judged by the tile AND the side entered by, not the tile alone.
        //
        // A square can legitimately be crossed twice by one run, on two different tracks: a crossing or
        // an overpass carries two routes that never meet, so a line that passes over one and comes back
        // through the other is ordinary railway, not a loop.  Keyed on the tile alone, the second
        // arrival looked identical to the first and the walk simply stopped - the run vanished and
        // nothing was reported, which is the worst of the three possible outcomes.  It also contradicted
        // locationsOf, which goes out of its way to treat an overpass's two routes as independent for
        // locking: the model already says the two levels are separate.
        //
        // Still terminates: a genuine circle re-enters a tile by the same side it did before, so it is
        // caught on that pass, and there are only four sides.
        if (!visited.add(tile.toString() + "|" + landing.getEntrySide())) return;

        for (Exit exit : graph.exits(tile, landing.getEntrySide()))
        {
            Map<String, accessorySetting> branchCommands = new LinkedHashMap<>(commands);

            // A branch whose settings contradict the path so far cannot be taken - the same rule the
            // autonomy model applies when it refuses a path with conflicting accessory commands
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

    /**
     * The length assigned to one tile.
     *
     * The END of an edge is included in that edge's length, and the start is not.  A route is a chain
     * of edges, so counting the tile a train ARRIVES on gives every tile along the way exactly once -
     * and it is what makes a length set on a station count for anything.
     *
     * Without it the sum covered only the track strictly BETWEEN two sensors, and a user who set
     * lengths on their platforms - which is where the length of a train matters, and the first place
     * anybody would put them - saw every edge come out as zero.
     */
    private int lengthOf(TileKey tile)
    {
        return Math.max(0, authored.getTileLength(tile));
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
    /**
     * Says so where a sensor lost its only way out to a switch it needed two ways.
     *
     * Only where NOTHING got out.  A fork refusing one of its legs is what a switch is for, and warning
     * about that would put a line on the findings list for every turnout on the layout.  A sensor with
     * no edge at all, whose branches were all refused for contradicting themselves, is the case the
     * user cannot otherwise see: the track is drawn, the route is physically there, and the graph simply
     * has no edge - first noticed when a connectivity test says no.
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
     * places.
     *
     * A paired portal is NOT unioned with its partner here, although a tunnel and its far end are one
     * piece of track drawn twice.  It does not need to be, and the reason is worth writing down because
     * the next reader will otherwise assume the union is missing: a portal tile carries no feedback, so
     * it is never a Point, so a walk that crosses the jump records BOTH tiles as steps - and the two
     * edges then share the first tile's location key anyway.  Adding the union would be redundant
     * rather than a correction.
     *
     * This comment used to describe the union as something the method did.  It never has.
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
