package org.traincontrol.automationui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.automationui.GraphReducer.ReducedEdge;
import org.traincontrol.automationui.GraphReducer.ReducedPoint;
import org.traincontrol.automationui.TileGraph.TileKey;

/**
 * The compile step: turns a reduced diagram into the autonomy JSON the existing model already reads.
 *
 * Nothing new is taught to the autonomy model: the output goes through the same parseAuto a hand-written file did, so
 * validation, path finding, locking and running are untouched.  Output is deterministic (the same diagram and authored
 * data give byte-identical JSON), so builds can be diffed against each other and against a hand-written configuration.
 *
 * @author Adam
 */
public class AutonomyBuilder
{
    /**
     * The settings that belong to a configuration rather than to the track: pace, speeds, and the rest of the top of a
     * hand-written file.
     */
    public static class Globals
    {
        private final Map<String, Object> values = new LinkedHashMap<>();

        public Globals()
        {
            // the three parseAuto insists on, at the model's own defaults
            values.put("minDelay", 1);
            values.put("maxDelay", 5);
            values.put("defaultLocSpeed", 35);
        }

        public Globals set(String key, Object value)
        {
            values.put(key, value);
            return this;
        }

        Map<String, Object> getValues()
        {
            return values;
        }
    }

    /**
     * One emitted Point: a tile, which arrival side it stands for, and whether it is the copy that reverses.  The
     * running model records which Point a locomotive stands on but never which way it faces, so nothing there refuses
     * the edge back the way it came; splitting by arrival side writes the facing down, as a hand-written configuration
     * did with one-way edges and a second Point on the same s88 (TunnelPre/TunnelReverse).
     */
    private static class Node
    {
        private final TileKey tile;

        /** which side a train arrived by, or null when the tile is not split */
        private final TilePorts.Side arrival;

        /** whether this is the copy a train turns round at */
        private final boolean reverse;

        /**
         * A copy of a square nothing arrives at, for a train facing one way out: no arrival bar or home facing applies.
         */
        private final boolean nothingArrives;

        Node(TileKey tile, TilePorts.Side arrival, boolean reverse)
        {
            this(tile, arrival, reverse, false);
        }

        Node(TileKey tile, TilePorts.Side arrival, boolean reverse, boolean nothingArrives)
        {
            this.tile = tile;
            this.arrival = arrival;
            this.reverse = reverse;
            this.nothingArrives = nothingArrives;
        }

        TileKey getTile()
        {
            return tile;
        }

        TilePorts.Side getArrival()
        {
            return arrival;
        }

        /**
         * Whether a train standing on this copy may leave by the given side: the reversing copy leaves the way it came,
         * the plain copy carries on along the track it is on (`onward`: the sides that track offers, not every side).
         */
        boolean leavesBy(TilePorts.Side exitSide, Set<TilePorts.Side> onward)
        {
            if (arrival == null) return true;

            if (reverse) return arrival == exitSide;

            // Not merely "some other side": a double curve is two curves crossing in one square with no connection, so
            // a train on one cannot leave by either side of the other (`findPath` refuses exactly that).
            return arrival != exitSide && onward.contains(exitSide);
        }

        /** Whether a train arriving by the given side lands on this copy; both copies of a side are reachable. */
        boolean arrivesBy(TilePorts.Side entrySide)
        {
            return arrival == null || arrival == entrySide;
        }
    }

    /** The authored key marking a tile where a train may turn round; never emitted, as it only shapes the graph. */
    public static final String CAN_REVERSE = "canReverse";

    /** The authored key marking a square where a train must turn round; only the turning copies are emitted. */
    public static final String MUST_REVERSE = "mustReverse";

    /** The authored key marking a berth trains are only sent to on purpose; emitted as `autoDestination` false. */
    public static final String AUTO_DESTINATION = "autoDestination";

    /** A placed locomotive's key: parseAuto's own name, named here because it must land on a single copy. */
    public static final String LOCOMOTIVE = "loc";

    /**
     * The authored key saying which way a placed locomotive points: the name of the side its front faces.  A split
     * square is several Points and a locomotive stands on exactly one, the one whose arrival side is behind it; the
     * model has no facing field, so this is never emitted and only decides which copy gets the locomotive.
     */
    public static final String FACING = "facing";

    /**
     * The way a home's locomotive faced when it was set (OB-282); setup only, it decides which copy carries the home.
     */
    public static final String HOME_FACING = "homeFacing";

    /** On a running Point: its home was set facing the way this copy faces, so Return Home matches facing (OB-282). */
    public static final String HOME_FACING_FIXED = "homeFacingFixed";

    /** On a running Point of a split square, the way a train standing on this copy points (OB-282). */
    public static final String COPY_FACING = "copyFacing";

    /** The old key for a berth (true for one), still read so that a setup authored under it keeps its berths. */
    public static final String PARKING = "parking";

    /**
     * The two flags this class derives, which never travel in from the authored data: an old configuration's could put
     * a terminus on a plain copy, or a terminus and a reversing flag on one Point, which fails the whole configuration.
     */
    private static final List<String> DERIVED = Arrays.asList("terminus", "reversing");

    private final GraphReducer reducer;
    private final Globals globals;

    private List<String> coordinatePages = null;

    // Tiles the user marked as somewhere a train may turn round.  See Node.
    private Set<TileKey> reversible = Collections.emptySet();

    // Stations autonomy may not choose for itself: emitted with autoDestination false and otherwise ordinary (a route
    // the user picks reaches them, Return Home fills them, and nothing about them stops a split).
    private Set<TileKey> manualOnly = Collections.emptySet();

    // Squares where turning round is not optional: only the turning copies are emitted.
    private Set<TileKey> mandatory = Collections.emptySet();

    // Per-point operational data from the active configuration - placements, homes, termini and the
    // rest - keyed by TileKey.toString().  See withPointExtras.
    private Map<String, JSONObject> pointExtras = null;

    /**
     * What each tile splits into, worked out once: nodesFor is asked for both ends of every edge and each call walks
     * every edge, so without this the build is quadratic.  Cleared by every setter that can change the answer.
     */
    private final Map<TileKey, List<Node>> nodeCache = new LinkedHashMap<>();

    public AutonomyBuilder(GraphReducer reducer, Globals globals)
    {
        this.reducer = reducer;
        this.globals = globals == null ? new Globals() : globals;
    }

    /**
     * Emits graph coordinates for each Point from where its tile sits on the diagram, pages stacked vertically in the
     * order given (null stops).  Off by default: the diagram is the layout now and a second set would only drift.
     */
    public AutonomyBuilder withCoordinatesFromTiles(List<String> pagesInOrder)
    {
        this.coordinatePages = pagesInOrder;
        return this;
    }

    /**
     * Merges per-point operational data (where locomotives start, termini, reversing, homes, exclusions, speed
     * multipliers) into the generated Points, keyed by TileKey.toString() so it survives a rename.  What the reduction
     * decides (name, station, s88) cannot be overridden from here.  Null for none.
     */
    public AutonomyBuilder withPointExtras(Map<String, JSONObject> extras)
    {
        this.pointExtras = extras;
        return this;
    }

    /**
     * The tiles (null for none) where a train may turn round, which gain a turning copy of every arrival.  Every tile
     * is split by arrival side whether or not it is marked.
     */
    public AutonomyBuilder withReversibleTiles(Set<TileKey> tiles)
    {
        this.reversible = tiles == null ? Collections.<TileKey>emptySet() : tiles;
        nodeCache.clear();
        return this;
    }

    /** The squares (null for none) where every arriving train must turn round. */
    public AutonomyBuilder withMandatoryTurns(Set<TileKey> tiles)
    {
        this.mandatory = tiles == null ? Collections.<TileKey>emptySet() : tiles;
        nodeCache.clear();
        return this;
    }

    /**
     * Which accessories protect each station, emitted on every copy of the square: the copies are one platform and the
     * signals guard the platform, not a side of it.
     */
    public AutonomyBuilder withProtectingSignals(Map<TileKey, List<String>> signals)
    {
        this.protectingSignals = signals == null
            ? Collections.<TileKey, List<String>>emptyMap() : signals;

        return this;
    }

    private Map<TileKey, List<String>> protectingSignals = Collections.emptyMap();

    /**
     * The signals thrown red when a train arrives at a station as the end of its journey (FR-096), emitted on every
     * copy for the same reason as the protecting signals.
     */
    public AutonomyBuilder withEntrySignals(Map<TileKey, List<String>> signals)
    {
        this.entrySignals = signals == null ? Collections.<TileKey, List<String>>emptyMap() : signals;

        return this;
    }

    private Map<TileKey, List<String>> entrySignals = Collections.emptyMap();

    /**
     * Stations unavailable to autonomy while another square is occupied (FR-001), expressed as lock edges so the model
     * needs no new concept: every edge into the station gains every edge ending at the watched square.  A lock edge
     * asks whether that track is held by a route, not whether a train stands beyond it (Edge.isLockHeld), so the
     * station is held back while something runs over the watched approach.
     */
    public AutonomyBuilder withBlockingPoints(Map<TileKey, List<TileKey>> blocking)
    {
        this.blockingPoints = blocking == null
            ? Collections.<TileKey, List<TileKey>>emptyMap() : blocking;

        return this;
    }

    private Map<TileKey, List<TileKey>> blockingPoints = Collections.emptyMap();

    /**
     * What Mass Assign Lengths would ask a length for, square by square: the key of the piece, switch or shared square
     * each lies in, where that still wants a length (OB-297).  Marked on each edge's places, so the own-tail note
     * counts exactly what the operator is asked to measure.
     */
    public AutonomyBuilder withPiecesToMeasure(Map<TileKey, String> asked)
    {
        this.piecesToMeasure = asked;

        return this;
    }

    /** Null when nobody said, and the configuration then says nothing of it */
    private Map<TileKey, String> piecesToMeasure = null;

    /**
     * Which sides each station refuses arrivals by (null for none).  The copy for a barred side stops being a station;
     * trains still run over it but cannot be sent to it.
     */
    public AutonomyBuilder withBarredArrivals(Map<TileKey, Set<TilePorts.Side>> barred)
    {
        this.barredArrivals = barred == null
            ? Collections.<TileKey, Set<TilePorts.Side>>emptyMap() : barred;

        return this;
    }

    private Map<TileKey, Set<TilePorts.Side>> barredArrivals = Collections.emptyMap();

    /**
     * Whether a train may arrive at this copy of a square and stop there.  The tile-wide copy of a square that never
     * splits is never barred: refusing it would make the station unreachable rather than restricted.
     */
    private boolean arrivalAllowed(Node node)
    {
        // Nor a copy of a square nothing arrives at: a bar on its side (set while trains did, hidden by the editor
        // since) shut the copy as no station.
        if (node.getArrival() == null || node.nothingArrives) return true;

        Set<TilePorts.Side> barred = barredArrivals.get(node.getTile());

        return barred == null || !barred.contains(node.getArrival());
    }

    /** The stations (null for none) that are parking berths: ordinary stations autonomy is told not to choose. */
    public AutonomyBuilder withParkingTiles(Set<TileKey> tiles)
    {
        this.manualOnly = tiles == null ? Collections.<TileKey>emptySet() : tiles;
        return this;
    }

    /**
     * The sides a train can arrive at a square by, for anything offering the user a choice about them: the same answer
     * the split uses, so the editor cannot offer a restriction on a side the build has no copy for.  Empty when the
     * square is not split.
     */
    public List<TilePorts.Side> arrivalSidesOf(TileKey tile)
    {
        return new ArrayList<>(splitSides(tile));
    }

    /**
     * The sides trains arrive at this tile by, in a fixed order.  Every tile with an arrival is split, even with one
     * side: a single Point per sensor forgets which way the train points, so nothing stops a journey taking the edge
     * straight back.
     */
    private List<TilePorts.Side> splitSides(TileKey tile)
    {
        Set<TilePorts.Side> sides = new java.util.TreeSet<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            if (!edge.getEnd().equals(tile)) continue;

            // An edge that arrives by no side of the grid has no copy to land on, so the tile is left whole (costing it
            // the rule, keeping the route): only where a portal's far end was redrawn as a sensor, which
            // `validatePortals` refuses.
            if (edge.getEntrySide() == null) return Collections.emptyList();

            sides.add(edge.getEntrySide());
        }

        return new ArrayList<>(sides);
    }

    /**
     * Which copies of a square are the same piece of metal, and so cannot hold two trains at once.  Occupancy is
     * recorded per Point and a square is emitted as one Point per arrival side, so a sibling copy would read free while
     * a train stands on its twin.  The tile says which copies those are, not the s88: different places share a sensor.
     *
     * Except where the square is two pieces of metal (Adam, on OB-238): *"it is two pieces of metal.  imagine two
     * parallel tracks simple appearing on one tile for visual convenience.  two distinct, not connected paths."*  A
     * double curve or an overpass: grouping all its copies would refuse a second train a track that is free.  The road
     * is named by its lowest side, so both copies of one arc land on the same name.
     *
     * @return the block name, or null where there is nothing to group
     */
    private String blockFor(TileKey tile, Node node, List<Node> nodes)
    {
        org.traincontrol.base.LayoutDiagramComponent component = reducer.getGraph() == null
            ? null : reducer.getGraph().getTiles().get(tile);

        boolean neverMeet = node.getArrival() != null && component != null
            && (component.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.DOUBLE_CURVE
            || component.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.FEEDBACK_DOUBLE_CURVE
            || component.getType() == org.traincontrol.base.LayoutDiagramComponent.componentType.OVERPASS);

        if (!neverMeet) return nodes.size() > 1 ? tile.toString() : null;

        java.util.SortedSet<TilePorts.Side> road = new java.util.TreeSet<>();

        road.add(node.getArrival());
        road.addAll(onwardFrom(tile, node.getArrival()));

        int copies = 0;

        for (Node other : nodes)
        {
            if (other.getArrival() != null && road.contains(other.getArrival())) copies++;
        }

        return copies > 1 ? tile.toString() + "/" + road.first() : null;
    }

    /**
     * The sides a train that arrived at this square by the given side can carry on out of, asked of the tile graph,
     * which alone knows which of a square's tracks a side belongs to.  A null arrival is unconstrained, as in leavesBy.
     */
    private Set<TilePorts.Side> onwardFrom(TileKey tile, TilePorts.Side arrival)
    {
        Set<TilePorts.Side> out = new java.util.LinkedHashSet<>();

        if (arrival == null || reducer.getGraph() == null) return out;

        for (TileGraph.Exit exit : reducer.getGraph().exits(tile, arrival))
        {
            if (exit.getSide() != null) out.add(exit.getSide());
        }

        return out;
    }

    /** The sides trains leave this tile by. */
    private Set<TilePorts.Side> departureSides(TileKey tile)
    {
        Set<TilePorts.Side> sides = new java.util.TreeSet<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            if (edge.getStart().equals(tile) && edge.getExitSide() != null)
            {
                sides.add(edge.getExitSide());
            }
        }

        return sides;
    }

    /**
     * For each side trains leave a square by, the side a train facing that way would have come in by: the other end of
     * the track that side is on (what `facingOf` reads back), else the opposite side.  In the order found, without
     * repeats.
     */
    private List<TilePorts.Side> sidesBehindTheWaysOut(TileKey tile)
    {
        List<TilePorts.Side> out = new ArrayList<>();

        Map<TileGraph.RouteId, TilePorts.Route> routes =
            reducer.getGraph() == null ? null : reducer.getGraph().getRoutes(tile);

        for (TilePorts.Side ahead : departureSides(tile))
        {
            TilePorts.Side behind = null;

            if (routes != null)
            {
                for (TilePorts.Route route : routes.values())
                {
                    if (route.getA() == ahead) behind = route.getB();
                    else if (route.getB() == ahead) behind = route.getA();

                    if (behind != null) break;
                }
            }

            if (behind == null) behind = ahead.opposite();

            if (!out.contains(behind)) out.add(behind);
        }

        return out;
    }

    /**
     * Every Point a tile is emitted as: one per arrival side, and a second per side where trains may turn round.  A
     * tile nothing arrives at is one Point where one way or none leads out, else one copy per way out, so the copy a
     * train stands on says which way it faces.
     */
    private List<Node> nodesFor(TileKey tile)
    {
        List<Node> cached = nodeCache.get(tile);

        if (cached != null) return cached;

        List<TilePorts.Side> sides = splitSides(tile);

        List<Node> out = new ArrayList<>();

        if (sides.isEmpty())
        {
            // Nothing arrives, but a train standing here still faces a way: emitted whole, the square would record no
            // facing and a train placed by hand and sent the way it does not face would drive off over unlocked track
            // (the runtime never commands a direction; the copy is the facing).  So one copy per way out, given the
            // side such a train would have come in by, so `leavesBy` lets it out ahead only and `facingOf` names that
            // way.  One way out, or none, is one Point.
            List<TilePorts.Side> behind = sidesBehindTheWaysOut(tile);

            if (behind.size() > 1)
            {
                for (TilePorts.Side side : behind) out.add(new Node(tile, side, false, true));
            }
            else
            {
                out.add(new Node(tile, null, false));
            }
        }
        else
        {
            boolean canTurn = reversible.contains(tile) || mandatory.contains(tile);
            boolean must = mandatory.contains(tile);

            Set<TilePorts.Side> departures = departureSides(tile);

            for (TilePorts.Side side : sides)
            {
                // Whether a train that came in by this side has anywhere to go but back, judged against the track it is
                // standing on (on a double curve the other curve's departures are not somewhere it can go).
                Set<TilePorts.Side> onward = onwardFrom(tile, side);

                boolean onwards = false;

                for (TilePorts.Side departure : departures)
                {
                    if (departure != side && onward.contains(departure)) onwards = true;
                }

                // The plain copy lets a train pass straight through.  Where turning is compulsory it is not emitted, so
                // the path finder has nothing else to choose: the whole difference between "may turn round here" and
                // "must".  Nor where it would have no way out (a dead end would give a Point that can be reached and
                // never left, which full autonomy could pick as a destination), and only where there is a turning copy
                // to carry the arrival: an unmarked dead end keeps its plain copy so the sensor stays in the graph, and
                // the trapped-arrival check puts that in front of the user.
                if (!must && (onwards || !canTurn)) out.add(new Node(tile, side, false));

                if (canTurn) out.add(new Node(tile, side, true));
            }
        }

        nodeCache.put(tile, out);

        return out;
    }

    /**
     * Which copy of a split square a home belongs on.  A home is a property of the square, but the model hangs it on a
     * Point and rebuildHomeStations refuses two Points claiming one locomotive, so it goes on one copy, chosen by what
     * a train can do there: a plain copy trains may arrive at; any copy trains may arrive at (on a must-turn square, a
     * turning copy); copy zero, so a home is never silently dropped.
     *
     * A turning copy is a terminus and Return Home is manual, so a locomotive that cannot reverse is driven in nose
     * first.  That only decides the home when every copy turns; then AutonomyChecks.checkHomesThatNeedReversing says so
     * on the findings list instead of leaving an IMPOSSIBLE from Return Home.
     */
    private int homeCopy(List<Node> nodes, JSONObject extras)
    {
        // The copy facing the way the home was set, where the setup says which (OB-282), and only one trains may arrive
        // at.  The plain copy before its turning twin.
        TilePorts.Side wanted = homeFacingOf(extras);

        if (wanted != null)
        {
            for (int copy = 0; copy < nodes.size(); copy++)
            {
                Node node = nodes.get(copy);

                if (facingOf(node) == wanted && !node.reverse && arrivalAllowed(node)) return copy;
            }

            for (int copy = 0; copy < nodes.size(); copy++)
            {
                Node node = nodes.get(copy);

                if (facingOf(node) == wanted && arrivalAllowed(node)) return copy;
            }
        }

        for (int copy = 0; copy < nodes.size(); copy++)
        {
            if (!nodes.get(copy).reverse && arrivalAllowed(nodes.get(copy))) return copy;
        }

        for (int copy = 0; copy < nodes.size(); copy++)
        {
            if (arrivalAllowed(nodes.get(copy))) return copy;
        }

        return 0;
    }

    /** The facing a square's home was set with, or null when the setup does not say */
    private static TilePorts.Side homeFacingOf(JSONObject extras)
    {
        return extras == null || !extras.has(HOME_FACING) ? null : side(extras.optString(HOME_FACING, null));
    }

    /** The per-square key naming the locomotive that lives at a station. */
    private static final String HOME = "home";

    /**
     * Which copy of a split square a placed locomotive stands on: a train facing east was reached from the west, so the
     * copy whose arrival side is opposite its facing.  With nothing authored, `startableCopy` guesses, and the only
     * wrong thing it can do is send the train back the way it came on its first move, which is why the editor offers
     * the choice.
     *
     * @return the index into nodes, or -1 where the setup records a facing no copy faces (the train then stands on no
     *         Point and the setup check reports an error until it is turned, moved, or the track is put back)
     */
    private int placementCopy(List<Node> nodes, JSONObject extras)
    {
        // Nothing says which way it faces: somewhere it can start, not copy zero.
        if (extras == null || !extras.has(FACING)) return startableCopy(nodes);

        TilePorts.Side facing = side(extras.optString(FACING, null));

        if (facing == null) return startableCopy(nodes);

        // Matched on the facing each copy stands for, not its arrival side: those differ on a turning copy, so a facing
        // learned from a train that had just turned round would match no copy and put the locomotive on the copy
        // pointing the opposite way.  Among the copies facing that way, one trains may arrive at first, as `homeCopy`
        // asks.
        for (int copy = 0; copy < nodes.size(); copy++)
        {
            // The plain copy in preference to the turning one: a train standing where it MAY turn round has not turned
            // yet
            if (facingOf(nodes.get(copy)) == facing && !nodes.get(copy).reverse && arrivalAllowed(nodes.get(copy)))
            {
                return copy;
            }
        }

        for (int copy = 0; copy < nodes.size(); copy++)
        {
            if (facingOf(nodes.get(copy)) == facing && arrivalAllowed(nodes.get(copy))) return copy;
        }

        // And where none of those faces that way, a copy that faces it anyway: the copy is the direction, so a train
        // stood on a copy facing the other way is dispatched along a route locked one way while its decoder drives it
        // the other, and the next capture writes the wrong facing over the operator's.  A barred arrival bars stopping
        // there, not standing there.  A copy autonomy cannot start from is refused with a sentence that says why.
        for (int copy = 0; copy < nodes.size(); copy++)
        {
            if (facingOf(nodes.get(copy)) == facing && !nodes.get(copy).reverse) return copy;
        }

        for (int copy = 0; copy < nodes.size(); copy++)
        {
            if (facingOf(nodes.get(copy)) == facing) return copy;
        }

        // A square of one copy that records no facing is one place and holds a train facing either way
        // (`AutonomySession.pointNamedNow`).
        if (nodes.size() == 1 && facingOf(nodes.get(0)) == null) return 0;

        // And where no copy faces it, none: standing the train on a copy facing the other way would let the next
        // capture write that facing over the train's.  On no Point it stays as the setup records it, and the error
        // stops anything being sent until it is put right.
        return -1;
    }

    /**
     * Where a train goes when nothing says which way it faces, or no copy faces the way something says: a copy trains
     * may arrive at, the plain one first (`homeCopy` falls back to it too).  Not copy zero: copies are emitted N, E, S,
     * W, so it could be a copy that is no station.
     */
    private int startableCopy(List<Node> nodes)
    {
        for (int copy = 0; copy < nodes.size(); copy++)
        {
            if (!nodes.get(copy).reverse && arrivalAllowed(nodes.get(copy))) return copy;
        }

        for (int copy = 0; copy < nodes.size(); copy++)
        {
            if (arrivalAllowed(nodes.get(copy))) return copy;
        }

        return 0;
    }

    /**
     * Which way a train standing on this copy is pointing: the far side of the route it arrived on, not the opposite of
     * the side it came in by (an N-E curve entered by N leaves by E).  A switch whose arrival side belongs to more than
     * one route is ambiguous and the copy does not record which; the first matching route is taken, right for the
     * common case.
     */
    private TilePorts.Side facingOf(Node node)
    {
        if (node.arrival == null) return null;

        // Turned round: pointing back at the side it came in by, whatever the track does
        if (node.reverse) return node.arrival;

        Map<TileGraph.RouteId, TilePorts.Route> routes =
            reducer.getGraph() == null ? null : reducer.getGraph().getRoutes(node.tile);

        if (routes != null)
        {
            for (TilePorts.Route route : routes.values())
            {
                if (route.getA() == node.arrival) return route.getB();

                if (route.getB() == node.arrival) return route.getA();
            }
        }

        // Nothing said otherwise, so straight through
        return node.arrival.opposite();
    }

    /** A side by name, or null if it is not one - an authored facing can outlive the track it described. */
    private static TilePorts.Side side(String name)
    {
        if (name == null) return null;

        for (TilePorts.Side side : TilePorts.Side.values())
        {
            if (side.name().equals(name)) return side;
        }

        return null;
    }

    /**
     * What a copy is called.  A tile that became a single Point keeps its plain name, so a railway does not pay for the
     * splitting machinery in every list, log line and menu.  Named for the direction of travel rather than the side
     * arrived by: "Main 4 (eastbound, reverse)", not "Main 4 (in W, rev)".
     */
    private String nodeName(String base, Node node, Set<String> taken)
    {
        if (node.arrival == null || nodesFor(node.tile).size() == 1) return base;

        String name = base + " (" + heading(node.arrival) + (node.reverse ? ", reverse)" : ")");

        // Made unique against everything else, which uniqueNames cannot do on its own: it settles the base names and
        // these suffixes come afterwards.  A hand-named "Main (eastbound)" beside a splitting "Main" would give two
        // Points one name, which Layout.createPoint refuses, invalidating the whole configuration.  Against names the
        // caller worked out once: per copy it cost a fifth of a click in the autonomy editor.  The square's own name is
        // not counted against it.
        if (!takenBesides(taken, base, name)) return name;

        for (int suffix = 2; suffix < 1000; suffix++)
        {
            String candidate = name + " (" + suffix + ")";

            if (!takenBesides(taken, base, candidate)) return candidate;
        }

        return name;
    }

    /** Whether a name is taken by a square other than the one called base; `taken` is every square's name. */
    private static boolean takenBesides(Set<String> taken, String base, String name)
    {
        return !name.equals(base) && taken.contains(name);
    }

    /** Every square's name, for nodeName, worked out once by each caller from its own uniqueNames(). */
    private static Set<String> namesTaken(Map<TileKey, String> names)
    {
        return new java.util.HashSet<>(names.values());
    }

    /**
     * Which way a train that arrived by this side is pointing, for the name only: the straight-through answer.
     * facingOf is better on a curve or a diverging leg, so there the copy is called "(eastbound)" while facingsAt
     * reports its train faces south: left that way deliberately, since a Point's name is what every configuration
     * refers to and changing the word needs a migration (MT-138).
     *
     * Nothing routes on this text: StationIndex.withoutArrivalSuffix strips all four words.
     */
    private static String heading(TilePorts.Side arrival)
    {
        switch (arrival)
        {
            case W: return "eastbound";
            case E: return "westbound";
            case N: return "southbound";
            default: return "northbound";
        }
    }

    /**
     * Builds the autonomy JSON.
     *
     * @return a string suitable for parseAuto
     */
    public String build()
    {
        JSONObject root = new JSONObject();

        for (Map.Entry<String, Object> entry : globals.getValues().entrySet())
        {
            root.put(entry.getKey(), entry.getValue());
        }

        // Names are what the model keys on, so they have to be unique.  Two sensors legitimately share an
        // s88 - a station and its approach guards - so uniqueness is enforced here rather than assumed.
        Map<TileKey, String> names = uniqueNames();

        Set<String> taken = namesTaken(names);

        List<ReducedPoint> points = new ArrayList<>(reducer.getPoints().values());
        Collections.sort(points, new Comparator<ReducedPoint>()
        {
            @Override
            public int compare(ReducedPoint a, ReducedPoint b)
            {
                return a.getTile().toString().compareTo(b.getTile().toString());
            }
        });

        JSONArray pointList = new JSONArray();

        for (ReducedPoint point : points)
        {
            List<Node> nodes = nodesFor(point.getTile());

            JSONObject extras = pointExtras == null
                ? null : pointExtras.get(point.getTile().toString());

            // A locomotive is a physical object and stands on exactly one copy.  Everything else authored on a square
            // (homes, lengths, exclusions) is a property of the SQUARE and belongs on all of them.
            int placed = placementCopy(nodes, extras);

            // The other per-square singleton.  See homeCopy.
            int homeOn = homeCopy(nodes, extras);

            for (int copy = 0; copy < nodes.size(); copy++)
            {
                Node node = nodes.get(copy);

                JSONObject json = new JSONObject();

                json.put("name", nodeName(names.get(point.getTile()), node, taken));

                // A station, unless a train arriving this way may not stop: the copy still carries traffic but nothing
                // is sent to it.  Decided once, because two flags come from it; read again below, a barred side's
                // turn-round copy would be a terminus that is not a destination, which Point.setTerminus refuses and
                // parseAuto answers by invalidating the layout.
                boolean stops = point.isStation() && arrivalAllowed(node);

                json.put("station", stops);
                json.put("s88", point.getS88());

                // Which square this is a copy of: a rebuild carries each train by it, since a rename can hand a Point's
                // name to another square
                json.put("square", point.getTile().toString());

                // Which copies are the same piece of track, only where a square is emitted as more than one Point.  The
                // tile, not the s88: different places share a sensor on a real layout, so only the tile says which
                // Points are one square.
                                String block = blockFor(point.getTile(), node, nodes);

                if (block != null) json.put("block", block);

                // The points this station is held back by, by name (FR-001), on every copy because the copies are one
                // platform.  The same setting is also emitted as lock edges below, and the two answer different
                // questions: a lock edge asks whether that approach is held by a route, this whether a train is
                // standing on the square.  One name per watched square, its first copy: the rule asks about the whole
                // block.
                List<TileKey> held = blockingPoints.get(point.getTile());

                if (held != null && !held.isEmpty())
                {
                    JSONArray watching = new JSONArray();

                    for (TileKey square : held)
                    {
                        // Not a square off the graph (a station on an excluded page, or a sensor whose track was
                        // deleted): it has no Point and `nodeName` would write null, which the railway's reader throws
                        // on.  Left out, as the lock-edge half below does.
                        if (!reducer.getPoints().containsKey(square) || names.get(square) == null) continue;

                        List<Node> copies = nodesFor(square);

                        if (copies.isEmpty()) continue;

                        watching.put(nodeName(names.get(square), copies.get(0), taken));
                    }

                    if (watching.length() > 0) json.put("blockedBy", watching);
                }

                // The signals thrown to red while this platform is claimed, on every copy because the copies are one
                // platform, including a copy whose arrival side is barred (so not conditioned on `stops`): a bar stops
                // autonomy routing a train in, not a person driving or placing one, and refreshOneSignal asks only the
                // Points whose protecting signals hold the accessory.  Safe on a non-station: setProtectingSignals
                // stores what it is given, unlike the terminus flag below.  One is written as a bare string, several as
                // an array, as parseAuto reads.
                List<String> protecting = protectingSignals.get(point.getTile());

                if (protecting != null && !protecting.isEmpty())
                {
                    json.put("protectingSignal", protecting.size() == 1
                        ? (Object) protecting.get(0) : new JSONArray(protecting));
                }

                // And the entry guard (FR-096), in the same two shapes.
                List<String> entry = entrySignals.get(point.getTile());

                if (entry != null && !entry.isEmpty())
                {
                    json.put("entrySignal", entry.size() == 1 ? (Object) entry.get(0) : new JSONArray(entry));
                }

                // COORDINATES ALWAYS, not only for the inspection copy: `arrivedFrom` is a compass side, which cannot
                // be resolved to a piece of track without knowing where the neighbors lie.
                //
                // Roughly one tile per 60 units (the spacing the hand-written files use), each page stacked below the
                // last, with split copies fanned out a little so they do not land on one another.
                //
                // Without a page list the stacking is dropped, not the coordinates: pages then overlap, which matters
                // only when comparing points on DIFFERENT pages, and the only reader, `sideTowards`, asks about
                // immediate neighbors.  A portal neighbor is the exception.
                int page = coordinatePages == null ? 0
                    : Math.max(0, coordinatePages.indexOf(point.getTile().getPage()));

                json.put("x", point.getTile().getX() * 60 + copy * 14);
                json.put("y", point.getTile().getY() * 60 + page * 1800 + copy * 14);

                if (extras != null)
                {
                    for (String key : extras.keySet())
                    {
                        // never the structural fields: those are the reduction's to decide
                        if (json.has(key)) continue;

                        // One locomotive, one copy: on every copy of a split square it became two trains with one name
                        // on one sensor.
                        if (LOCOMOTIVE.equals(key) && copy != placed) continue;

                        // One home, one copy, by a different rule: a train stands where it stands, a home lands where
                        // most locomotives can reach it.  See homeCopy.
                        if (HOME.equals(key) && copy != homeOn) continue;

                        // On a split tile, turning round is what the copy means, so the authored flag is carried onto
                        // neither: it would make the plain copy reverse too.  CAN_REVERSE never goes out; it is the
                        // instruction to split.
                        if (CAN_REVERSE.equals(key) || PARKING.equals(key)
                                || FACING.equals(key) || AUTO_DESTINATION.equals(key) || HOME_FACING.equals(key)) continue;

                        if (DERIVED.contains(key)) continue;

                        // Active is carried on every square, not only a station: the usage menu writes `active` alone
                        // for Out of service on any square, and `Layout.isPathClear` refuses a path through an inactive
                        // point only if the flag reaches the graph.

                        json.put(key, extras.get(key));
                    }
                }

                // A station turns round at a terminus, anything else at a reversing point: a terminus is a destination
                // that reverses on arrival, a reversing point one autonomy never chooses to stop at.  Either the copy
                // that exists to turn trains round, or, where the square was marked and nothing arrives at it by a side
                // of the grid, the single Point it became (must, not may: the flag means every arriving train reverses,
                // and on a may-turn square it would make a through station unroutable; where may cannot be expressed
                // nothing is emitted, and the checks report that the marking means nothing here).
                if (node.reverse
                    || (mandatory.contains(point.getTile()) && splitSides(point.getTile()).isEmpty()))
                {
                    // Against what this copy is, not the square: a terminus must be a destination, and a copy trains
                    // may not arrive at is not one, so it is emitted as a plain reversing point.
                    json.put(stops ? "terminus" : "reversing", true);
                }

                // A berth is an ordinary station that autonomy is told not to choose, not a reversing one (which would
                // drag along reversing on arrival and no path routed through): a terminus and a shut arm say those.
                if (manualOnly.contains(point.getTile()))
                {
                    json.put(AUTO_DESTINATION, false);
                }

                // Which way a train on this copy points, on a split square (OB-282): what Return Home compares to tell
                // whether a train is home facing the way it was homed.  The facing, not the arrival side: a turning
                // copy shares its plain twin's arrival.
                if (nodes.size() > 1 && facingOf(node) != null) json.put(COPY_FACING, facingOf(node).name());

                // And that the home on this copy was set facing this way (OB-282), where the setup says so and this
                // copy holds it.  Never on a square nothing arrives at, where an earlier facing would hold a train to a
                // way it can never be brought home in.
                if (copy == homeOn && extras != null && extras.has(HOME) && homeFacingOf(extras) != null
                    && !node.nothingArrives && facingOf(node) == homeFacingOf(extras))
                {
                    json.put(HOME_FACING_FIXED, true);
                }

                pointList.put(json);
            }
        }

        root.put("points", pointList);

        List<ReducedEdge> edges = new ArrayList<>(reducer.getEdges());
        Collections.sort(edges, new Comparator<ReducedEdge>()
        {
            @Override
            public int compare(ReducedEdge a, ReducedEdge b)
            {
                int byStart = a.getStart().toString().compareTo(b.getStart().toString());

                if (byStart != 0) return byStart;

                return a.getEnd().toString().compareTo(b.getEnd().toString());
            }
        });

        JSONArray edgeList = new JSONArray();

        // Which emitted name pairs each reduced edge became, so a lock on one edge becomes a lock on every copy:
        // over-locking rather than under, since all are still the same piece of track.
        Map<ReducedEdge, List<String[]>> emitted = new LinkedHashMap<>();

        for (ReducedEdge edge : edges)
        {
            List<String[]> pairs = new ArrayList<>();

            for (Node from : nodesFor(edge.getStart()))
            {
                if (!from.leavesBy(edge.getExitSide(), onwardFrom(edge.getStart(), from.arrival)))
                {
                    continue;
                }

                for (Node to : nodesFor(edge.getEnd()))
                {
                    if (!to.arrivesBy(edge.getEntrySide())) continue;

                    pairs.add(new String[]
                    {
                        nodeName(names.get(edge.getStart()), from, taken),
                        nodeName(names.get(edge.getEnd()), to, taken)
                    });
                }
            }

            emitted.put(edge, pairs);
        }

        for (ReducedEdge edge : edges)
        {
          for (String[] pair : emitted.get(edge))
          {
            JSONObject json = new JSONObject();

            json.put("start", pair[0]);
            json.put("end", pair[1]);
            json.put("length", edge.getLength());

            // And the side it comes in by, which only this end knows (OB-182): the model works out where a neighboring
            // Point lies and calls that the arrival side, but a reduced run crosses tiles and turns corners.  Needed by
            // the walk that blocks the track behind a standing train and the question asked when placing one by hand;
            // it is the side the square is split on.
            if (edge.getEntrySide() != null) json.put("entrySide", edge.getEntrySide().name());

            // And the places it runs over, in order, each with what it measures (OB-207): the runtime must say where
            // along an edge a train lies, or a one-unit train in the first tile of a twelve-tile run would refuse every
            // path sharing any other.  `GraphReducer.placesAlong` uses the same location ids as the lock relation
            // below, so the two cannot drift; the lengths sum to the `length` above.
            JSONArray places = new JSONArray();

            for (GraphReducer.Place place : reducer.placesAlong(edge))
            {
                JSONObject at = new JSONObject();

                at.put("at", place.getId());
                at.put("length", place.getLength());

                // And whether its 0 was answered on purpose, so the runtime does not call it missing (only where true).
                if (place.isAnswered()) at.put("answered", true);

                // And what Mass Assign asks a length for there (OB-297).
                String asked = place.getTile() == null || piecesToMeasure == null ? null : piecesToMeasure.get(place.getTile());

                if (asked != null) at.put("toMeasure", asked);

                places.put(at);
            }

            if (places.length() > 0) json.put("places", places);

            // That the marks were written, so an edge with none reads as "nothing to measure" and not "not known".
            if (piecesToMeasure != null && places.length() > 0) json.put("lengthsAsked", true);

            // And how much of it is after the last switch (Adam's ruling): "Between the switch and the station, the
            // length must be >= length of the train", and "for the switches, for simplicity, let's use any direction,
            // that way we are guaranteed to be safe".  Only the reducer walks tiles; runtime `Edge` knows a length, not
            // what it is made of.  Written only where the edge crosses a switch: an edge crossing none does not bound
            // where a train may stop, so absent is not zero.
            if (edge.crossesASwitch()) json.put("roomAtTheEnd", edge.getRoomAtTheEnd());

            JSONArray commands = new JSONArray();

            List<String> accessoryNames = new ArrayList<>(edge.getCommands().keySet());
            Collections.sort(accessoryNames);

            for (String accessory : accessoryNames)
            {
                accessorySetting setting = edge.getCommands().get(accessory);

                JSONObject command = new JSONObject();
                command.put("acc", accessory);
                command.put("state", setting.toString().toLowerCase());
                commands.put(command);
            }

            if (commands.length() > 0)
            {
                json.put("commands", commands);
            }

            JSONArray lockEdges = new JSONArray();

            Set<ReducedEdge> locked = reducer.getLocks().get(edge);

            if (locked != null)
            {
                List<ReducedEdge> sorted = new ArrayList<>(locked);
                Collections.sort(sorted, new Comparator<ReducedEdge>()
                {
                    @Override
                    public int compare(ReducedEdge a, ReducedEdge b)
                    {
                        int byStart = a.getStart().toString().compareTo(b.getStart().toString());

                        if (byStart != 0) return byStart;

                        return a.getEnd().toString().compareTo(b.getEnd().toString());
                    }
                });

                for (ReducedEdge other : sorted)
                {
                    List<String[]> otherPairs = emitted.get(other);

                    if (otherPairs == null) continue;

                    for (String[] otherPair : otherPairs)
                    {
                        JSONObject lock = new JSONObject();
                        lock.put("start", otherPair[0]);
                        lock.put("end", otherPair[1]);
                        lockEdges.put(lock);
                    }
                }
            }

            // FR-001, on top of the locks the reduction derived: an edge arriving at a held-back station gains every
            // edge that ends at the watched square, for every emitted copy like the derived locks (a restriction naming
            // one copy would be satisfied by routing over its twin).  Squares that no longer exist are not found, so a
            // restriction watching deleted track quietly stops applying; reconcile drops it on the next pass and says
            // so.
            List<TileKey> watched = blockingPoints.get(edge.getEnd());

            if (watched != null)
            {
                for (TileKey square : watched)
                {
                    for (Map.Entry<ReducedEdge, List<String[]>> other : emitted.entrySet())
                    {
                        if (!square.equals(other.getKey().getEnd())) continue;

                        for (String[] otherPair : other.getValue())
                        {
                            // Not this edge itself, which would make the station block its own approach
                            if (otherPair[0].equals(pair[0]) && otherPair[1].equals(pair[1])) continue;

                            JSONObject lock = new JSONObject();
                            lock.put("start", otherPair[0]);
                            lock.put("end", otherPair[1]);
                            lockEdges.put(lock);
                        }
                    }
                }
            }

            if (lockEdges.length() > 0)
            {
                json.put("lockedges", lockEdges);
            }

            edgeList.put(json);
          }
        }

        root.put("edges", edgeList);

        return root.toString(2);
    }

    /**
     * Every name this builder would emit, mapped back to its square.  uniqueNames() is not the whole answer once a tile
     * is several Points; anything working backwards from a running configuration (the diagram monitor, the capture of
     * where locomotives ended up) needs this or loses every split copy.
     */
    public Map<String, TileKey> tilesByName()
    {
        Map<String, TileKey> out = new LinkedHashMap<>();

        Map<TileKey, String> names = uniqueNames();

        Set<String> taken = namesTaken(names);

        for (Map.Entry<TileKey, String> entry : names.entrySet())
        {
            for (Node node : nodesFor(entry.getKey()))
            {
                out.put(nodeName(entry.getValue(), node, taken), entry.getKey());
            }
        }

        return out;
    }

    /**
     * Every emitted name mapped to the name the square has.  The track diagram registers a station label under the
     * caption written on it (the base name) and the running configuration may know that place as several Points, so
     * anything pairing a Point with a label goes through this.  An unsplit Point maps to itself.
     */
    public Map<String, String> baseNames()
    {
        Map<String, String> out = new LinkedHashMap<>();

        Map<TileKey, String> names = uniqueNames();

        Set<String> taken = namesTaken(names);

        for (Map.Entry<TileKey, String> entry : names.entrySet())
        {
            for (Node node : nodesFor(entry.getKey()))
            {
                out.put(nodeName(entry.getValue(), node, taken), entry.getValue());
            }
        }

        return out;
    }

    /**
     * The ways a locomotive may be homed facing on a square: the facings of the copies trains may arrive at (OB-282).
     * *"we shouldn't allow an impossible facing to be saved"* - a copy trains may not arrive at is not one a train can
     * be brought home to, so its facing is not offered and `homeCopy` would not honor it.
     *
     * @return the facings, empty on a square that is not split
     */
    public java.util.Set<TilePorts.Side> homeFacingsAt(TileKey tile)
    {
        java.util.Set<TilePorts.Side> out = new java.util.LinkedHashSet<>();

        for (Node node : nodesFor(tile))
        {
            // Not on a square nothing arrives at: no train can be brought home to either copy, so the home is the
            // square.
            if (node.arrival != null && !node.nothingArrives && arrivalAllowed(node)) out.add(facingOf(node));
        }

        return out;
    }

    /**
     * Every emitted name mapped to the way a train standing on it is pointing, so a facing is learned rather than asked
     * for: once autonomy has run, the Point a locomotive ended on says which way round it is.  Only split copies
     * appear.
     */
    public Map<String, TilePorts.Side> facingByName()
    {
        Map<String, TilePorts.Side> out = new LinkedHashMap<>();

        Map<TileKey, String> names = uniqueNames();

        Set<String> taken = namesTaken(names);

        for (Map.Entry<TileKey, String> entry : names.entrySet())
        {
            for (Node node : nodesFor(entry.getKey()))
            {
                if (node.arrival == null) continue;

                out.put(nodeName(entry.getValue(), node, taken), facingOf(node));
            }
        }

        return out;
    }

    /**
     * Every emitted name of a split copy mapped to the side its trains come in by (OB-201): what a trapped arrival is
     * named by, and what closing arrivals from that side would close.  Only split copies appear.
     */
    public Map<String, TilePorts.Side> arrivalByName()
    {
        Map<String, TilePorts.Side> out = new LinkedHashMap<>();

        Map<TileKey, String> names = uniqueNames();

        Set<String> taken = namesTaken(names);

        for (Map.Entry<TileKey, String> entry : names.entrySet())
        {
            for (Node node : nodesFor(entry.getKey()))
            {
                if (node.arrival == null) continue;

                out.put(nodeName(entry.getValue(), node, taken), node.arrival);
            }
        }

        return out;
    }

    /**
     * Which way a train on a square of one copy that records no facing points: toward its only way out, or null where
     * it has none or more than one.
     */
    public TilePorts.Side onlyWayOutFacing(TileKey tile)
    {
        List<TilePorts.Side> behind = sidesBehindTheWaysOut(tile);

        return behind.size() == 1 ? facingOf(new Node(tile, behind.get(0), false)) : null;
    }

    /** Every emitted name of a turning copy: a train that came in and turned round there. */
    public Set<String> turningNames()
    {
        Set<String> out = new java.util.LinkedHashSet<>();

        Map<TileKey, String> names = uniqueNames();

        Set<String> taken = namesTaken(names);

        for (Map.Entry<TileKey, String> entry : names.entrySet())
        {
            for (Node node : nodesFor(entry.getKey()))
            {
                if (node.reverse) out.add(nodeName(entry.getValue(), node, taken));
            }
        }

        return out;
    }

    /**
     * The reduction this naming was made from: the squares' edges before any is split into copies, which the session's
     * one-way refusal reads for which way a train may leave or reach a square at all.
     */
    GraphReducer reduction()
    {
        return reducer;
    }

    /**
     * Every emitted edge name - "start -> end" over Point names - mapped to the reduced edge it came from; several
     * names can share one reduced edge once a tile is split.
     */
    public Map<String, ReducedEdge> edgesByName()
    {
        Map<String, ReducedEdge> out = new LinkedHashMap<>();
        Map<TileKey, String> names = uniqueNames();
        Set<String> taken = namesTaken(names);

        for (ReducedEdge edge : reducer.getEdges())
        {
            String start = names.get(edge.getStart());
            String end = names.get(edge.getEnd());

            if (start == null || end == null) continue;

            for (Node from : nodesFor(edge.getStart()))
            {
                if (!from.leavesBy(edge.getExitSide(), onwardFrom(edge.getStart(), from.arrival)))
                {
                    continue;
                }

                for (Node to : nodesFor(edge.getEnd()))
                {
                    if (!to.arrivesBy(edge.getEntrySide())) continue;

                    out.put(nodeName(start, from, taken) + " -> " + nodeName(end, to, taken), edge);
                }
            }
        }

        return out;
    }

    /**
     * The name each Point will carry in the generated file, disambiguated where two collide: the backstop for generated
     * names, and for a rename not yet applied everywhere.
     */
    public Map<TileKey, String> uniqueNames()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();
        Map<String, Integer> seen = new LinkedHashMap<>();

        // Settled over every page: the named squares of a page out of autonomy take their turn as if it were in, and
        // are not emitted, so a name two pages share is suffixed the same way whichever is out.
        Map<TileKey, String> named = new LinkedHashMap<>();

        for (ReducedPoint point : reducer.getPoints().values()) named.put(point.getTile(), point.getName());

        for (Map.Entry<TileKey, String> out0 : namesOutOfPlay.entrySet()) named.putIfAbsent(out0.getKey(), out0.getValue());

        List<TileKey> squares = new ArrayList<>(named.keySet());
        Collections.sort(squares, new Comparator<TileKey>()
        {
            @Override
            public int compare(TileKey a, TileKey b)
            {
                return a.toString().compareTo(b.toString());
            }
        });

        for (TileKey square : squares)
        {
            String base = named.get(square);
            Integer count = seen.get(base);
            boolean emitted = reducer.getPoints().containsKey(square);

            if (count == null)
            {
                seen.put(base, 1);

                if (emitted) out.put(square, base);

                continue;
            }

            // Keep suffixing until the result is genuinely unused: X, X and "X (2)" would otherwise produce "X (2)"
            // twice, which parseAuto rejects and which silently merges two tiles' placements when capture inverts this
            // map.
            int suffix = count + 1;
            String candidate = base + " (" + suffix + ")";

            while (seen.containsKey(candidate))
            {
                candidate = base + " (" + (++suffix) + ")";
            }

            seen.put(base, suffix);
            seen.put(candidate, 1);

            if (emitted) out.put(square, candidate);
        }

        return out;
    }

    /** The names of squares on pages out of autonomy, which take their turn in `uniqueNames` without being emitted. */
    public AutonomyBuilder withNamesOutOfPlay(Map<TileKey, String> names)
    {
        this.namesOutOfPlay = names == null ? Collections.<TileKey, String>emptyMap() : names;

        return this;
    }

    private Map<TileKey, String> namesOutOfPlay = Collections.emptyMap();
}
