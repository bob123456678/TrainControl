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
 * Nothing new is taught to the autonomy model.  The output goes through the same parseAuto that a
 * hand-written file went through, so validation, path finding, locking and running are all untouched -
 * what changes is only where the file comes from.
 *
 * Output is deterministic: the same diagram and the same authored data produce byte-identical JSON, so
 * two builds can be diffed against each other and against a hand-written configuration.
 *
 * @author Adam
 */
public class AutonomyBuilder
{
    /**
     * The settings that belong to a configuration rather than to the track: pace, speeds, and the rest of
     * what used to sit at the top of a hand-written autonomy file.
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
     * One emitted Point: a tile, which arrival side it stands for, and whether it is the copy that
     * reverses.
     *
     * The reduction gives one Point per sensor, which is a truthful model of the track and a lossy
     * model of what a train may do there.  Arriving from the west and carrying on east is a different
     * move from arriving from the west and backing out west again, and as one Point they are the same
     * edge set - so either the reversal is unreachable or every passing train may perform one.  The
     * running model cannot tell them apart either: it records which Point a locomotive stands on and
     * never which way it faces, so nothing there refuses the edge back the way it came.
     *
     * A hand-written configuration solved this by putting several Points on one s88 and writing most
     * edges one way only.  The sample layout does exactly that: TunnelPre -> Tunnel exists and
     * Tunnel -> TunnelPre does not, and the way back out of that sensor is a second Point on the same
     * s88 called TunnelReverse.  Those one-way edges are not caution; they are the direction the train
     * is facing, written down.  This class reconstructs that shape from the diagram.
     */
    private static class Node
    {
        private final TileKey tile;

        /** which side a train arrived by, or null when the tile is not split */
        private final TilePorts.Side arrival;

        /** whether this is the copy a train turns round at */
        private final boolean reverse;

        Node(TileKey tile, TilePorts.Side arrival, boolean reverse)
        {
            this.tile = tile;
            this.arrival = arrival;
            this.reverse = reverse;
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
         * Whether a train standing on this copy may leave by the given side.
         *
         * The whole point of the split: the reversing copy leaves the way it came, and the plain copy
         * carries on along the track it is already on.
         *
         * @param exitSide the side an edge leaves by
         * @param onward the sides a train that arrived by this copy's side can carry on out of - the
         *        track it is standing on, which is not the same as every side the square has
         */
        boolean leavesBy(TilePorts.Side exitSide, Set<TilePorts.Side> onward)
        {
            if (arrival == null) return true;

            if (reverse) return arrival == exitSide;

            // Not merely "some other side".  A double curve is two curves crossing in one square with
            // no connection between them, so a train on one of them cannot leave by either side of the
            // other - and "any side but the one I came in at" let it, which is a move that changes
            // track in mid-square.  findPath refuses exactly that, so leaving it here meant the editor
            // called a run impossible while the configuration it built offered it.
            return arrival != exitSide && onward.contains(exitSide);
        }

        /**
         * Whether a train arriving by the given side lands on this copy.  Both copies of a side are
         * reachable from the same place - which is what lets the path finder choose between going on
         * and turning round.
         */
        boolean arrivesBy(TilePorts.Side entrySide)
        {
            return arrival == null || arrival == entrySide;
        }
    }

    /**
     * The authored key that marks a tile as somewhere a train may turn round.
     *
     * Stored beside the operational point data but never emitted: it is an instruction to this class
     * about how to SHAPE the graph, not something parseAuto has ever heard of.
     */
    public static final String CAN_REVERSE = "canReverse";

    /**
     * The authored key that marks a station as a berth trains are only ever sent to on purpose.
     *
     * Emitted as `reversing`, which is the model's word for a station autonomy will never choose and
     * cannot route a train through.  Also never emitted under its own name.
     */
    /**
     * The authored key that marks a square where a train has NO choice but to turn round.
     *
     * The difference from CAN_REVERSE is whether the plain copies are emitted at all.  "May" leaves
     * them, so a train can pass straight through and the path finder picks; "must" leaves only the
     * turning ones, so every arrival turns.  On a dead end the two are the same thing, because the
     * plain copy would have nowhere to go.
     */
    public static final String MUST_REVERSE = "mustReverse";

    public static final String AUTO_DESTINATION = "autoDestination";

    /**
     * The authored key holding a placed locomotive.  parseAuto's own name for it, so it goes out
     * unchanged - it is named here only because it is the one key that must land on a single copy.
     */
    public static final String LOCOMOTIVE = "loc";

    /**
     * The authored key saying which way a placed locomotive is pointing: the name of the side of the
     * square its front faces.
     *
     * Needed because a split square is several Points and a locomotive stands on exactly one of them -
     * the one whose arrival side is behind it.  The running model has no field for a facing, so this is
     * never emitted; it decides WHICH copy the locomotive is emitted on, which says the same thing in
     * the only language the model has.
     *
     * Absent for the squares only one train can reach one way, which is most of them.
     */
    public static final String FACING = "facing";

    /**
     * What this used to be called, still read so that a setup authored an hour ago keeps its berths.
     */
    public static final String PARKING = "parking";

    /**
     * The two flags this class DERIVES, and which therefore never travel in from the authored data.
     *
     * They used to be set directly, and a configuration written before this change can still carry
     * them.  Letting one through now would put a terminus on a plain copy of a split tile, or - worse -
     * a terminus and a reversing flag on one Point, which Point itself refuses in either order and
     * which fails the whole configuration rather than that one square.
     */
    private static final List<String> DERIVED = Arrays.asList("terminus", "reversing");

    private final GraphReducer reducer;
    private final Globals globals;

    private List<String> coordinatePages = null;

    // Tiles the user marked as somewhere a train may turn round.  See Node.
    private Set<TileKey> reversible = Collections.emptySet();

    // Stations autonomy may not choose for itself.  They are emitted with autoDestination false and
    // are otherwise ordinary: a route the user picks reaches them, Return Home fills them, and trains
    // may run through them if the track allows.  Nothing about them stops a split.
    private Set<TileKey> manualOnly = Collections.emptySet();

    // Squares where turning round is not optional: only the turning copies are emitted.
    private Set<TileKey> mandatory = Collections.emptySet();

    // Per-point operational data from the active configuration - placements, homes, termini and the
    // rest - keyed by TileKey.toString().  See withPointExtras.
    private Map<String, JSONObject> pointExtras = null;

    /**
     * What each tile splits into, worked out once.
     *
     * Not premature: nodesFor is asked inside the edge loop, for both ends of every edge, and each call
     * walks every edge to find the arrival sides - so without this the build is quadratic in the edge
     * count.  It also lets a name ask cheaply whether its tile became more than one Point, which is what
     * decides whether the name needs a suffix at all.
     *
     * Cleared by every setter that can change the answer.
     */
    private final Map<TileKey, List<Node>> nodeCache = new LinkedHashMap<>();

    public AutonomyBuilder(GraphReducer reducer, Globals globals)
    {
        this.reducer = reducer;
        this.globals = globals == null ? new Globals() : globals;
    }

    /**
     * Emits graph coordinates for each Point, taken from where its tile sits on the diagram.
     *
     * Off by default: the diagram is the layout now, so nothing needs a second set of positions, and a
     * stale one would only drift.  It is worth having for **inspection** - a graph laid out like the track
     * it came from can be read at a glance and checked against the diagram beside it, where the same graph
     * sprayed at random cannot be checked against anything.
     *
     * Pages are stacked vertically in the order given, so two pages do not land on top of each other.
     *
     * @param pagesInOrder the participating page names, or null to stop emitting coordinates
     * @return this
     */
    public AutonomyBuilder withCoordinatesFromTiles(List<String> pagesInOrder)
    {
        this.coordinatePages = pagesInOrder;
        return this;
    }

    /**
     * Merges per-point operational data into the generated Points.
     *
     * This is how a configuration differs from the track: where the locomotives start, which Points are
     * termini or reversing, homes, exclusions, speed multipliers.  The keys are TileKey.toString(), so
     * the data survives a Point being renamed; the values are whatever parseAuto accepts on a point.
     *
     * What the reduction itself decides - name, station, s88 - cannot be overridden from here, because
     * a configuration that quietly changed the track would be the JSON window all over again.
     *
     * @param extras tile key string to the point's extra properties, or null for none
     * @return this
     */
    public AutonomyBuilder withPointExtras(Map<String, JSONObject> extras)
    {
        this.pointExtras = extras;
        return this;
    }

    /**
     * The tiles where a train may turn round, which gain a turning copy of every arrival.
     *
     * Every tile is split by arrival side whether or not it is marked; what the marking adds is the
     * copy that leaves the way it came.  So this says "a train may turn round here", not "this square
     * is special enough to model properly".
     *
     * @param tiles the marked tiles, or null for none
     * @return this
     */
    public AutonomyBuilder withReversibleTiles(Set<TileKey> tiles)
    {
        this.reversible = tiles == null ? Collections.<TileKey>emptySet() : tiles;
        nodeCache.clear();
        return this;
    }

    /**
     * The stations that are parking berths.
     *
     * @param tiles the marked tiles, or null for none
     * @return this
     */
    /**
     * The squares where every arriving train must turn round.
     *
     * @param tiles the marked tiles, or null for none
     * @return this
     */
    public AutonomyBuilder withMandatoryTurns(Set<TileKey> tiles)
    {
        this.mandatory = tiles == null ? Collections.<TileKey>emptySet() : tiles;
        nodeCache.clear();
        return this;
    }

    /**
     * Which sides each station refuses to let trains arrive by.
     *
     * A square is already emitted as one Point per arrival side, so a restriction has somewhere exact
     * to land: the copy for a barred side stops being a station.  Trains still run over it - the track
     * is unchanged and pass-through is a different question, answered by the direction arrows - they
     * simply cannot be sent TO it, which is what "you may not arrive from that way" means.
     *
     * @param barred square to the sides it bars, or null for no restrictions
     * @return this
     */
    /**
     * Which accessories protect each station.
     *
     * Emitted on every copy of the square, because the copies are one platform and the signals guard
     * the platform rather than a side of it.
     *
     * @param signals station square to accessory names
     * @return this
     */
    public AutonomyBuilder withProtectingSignals(Map<TileKey, List<String>> signals)
    {
        this.protectingSignals = signals == null
            ? Collections.<TileKey, List<String>>emptyMap() : signals;

        return this;
    }

    private Map<TileKey, List<String>> protectingSignals = Collections.emptyMap();

    /**
     * Stations that are unavailable to autonomy while another square is occupied (FR-001).
     *
     * Expressed as LOCK EDGES rather than as a rule of its own, which is Adam's call and the reason
     * there is no new concept in the running model: "add a lock edge ending with the requested S88 to
     * be excluded.  that will allow you to mostly reuse the existing model."
     *
     * So every edge INTO the station gains, as a lock edge, every edge that ENDS at the square being
     * watched - and the existing lock machinery does the rest.  What that means in practice is worth
     * being exact about, because it is not quite the words of the request: a lock edge asks whether
     * that track is HELD BY A ROUTE, not whether a train is standing at the sensor beyond it.
     * Edge.isLockHeld says so and explains why - counting a parked train made a train beside a junction
     * a permanent roadblock, and two of them could deadlock.  So the station is held back while
     * something is running over the watched approach, and free again once that train has arrived and
     * its path is released.
     *
     * @param blocking station square to the squares that hold it back
     * @return this
     */
    public AutonomyBuilder withBlockingPoints(Map<TileKey, List<TileKey>> blocking)
    {
        this.blockingPoints = blocking == null
            ? Collections.<TileKey, List<TileKey>>emptyMap() : blocking;

        return this;
    }

    private Map<TileKey, List<TileKey>> blockingPoints = Collections.emptyMap();

    public AutonomyBuilder withBarredArrivals(Map<TileKey, Set<TilePorts.Side>> barred)
    {
        this.barredArrivals = barred == null
            ? Collections.<TileKey, Set<TilePorts.Side>>emptyMap() : barred;

        return this;
    }

    private Map<TileKey, Set<TilePorts.Side>> barredArrivals = Collections.emptyMap();

    /**
     * Whether a train may arrive at this copy of a square and stop there.
     *
     * The tile-wide copy - the one a square that never splits is emitted as - is never barred: there
     * is no arrival side to bar, and refusing it would make the station unreachable rather than
     * restricted.
     */
    private boolean arrivalAllowed(Node node)
    {
        if (node.getArrival() == null) return true;

        Set<TilePorts.Side> barred = barredArrivals.get(node.getTile());

        return barred == null || !barred.contains(node.getArrival());
    }

    public AutonomyBuilder withParkingTiles(Set<TileKey> tiles)
    {
        this.manualOnly = tiles == null ? Collections.<TileKey>emptySet() : tiles;
        return this;
    }

    /**
     * The sides trains arrive at this tile by, in a fixed order.
     *
     * Every tile with an arrival is split, not only the marked ones.  A single Point per sensor is a
     * truthful model of the track and a false model of what a train may do, because it forgets which way
     * the train is pointing: the running model knows only which Point a locomotive stands on, so nothing
     * stops a journey taking the edge straight back where it came from.  Splitting by arrival side is
     * how the facing gets written down, and it is what the hand-built configurations were already doing
     * by hand with one-way edges and doubled Points.
     *
     * One arrival side is still worth splitting on.  It emits a single Point, so nothing is duplicated
     * and no name changes, but that Point knows which side it was reached by and therefore refuses to
     * leave by it - which is the whole rule, stated for the commonest case.
     */
    /**
     * The sides a train can arrive at a square by, for anything offering the user a choice about them.
     *
     * The same answer the split itself uses, so the editor cannot offer a restriction on a side the
     * build has no copy for - which would be a setting that silently did nothing.
     *
     * @param tile
     * @return the arrival sides, empty when the square is not split
     */
    public List<TilePorts.Side> arrivalSidesOf(TileKey tile)
    {
        return new ArrayList<>(splitSides(tile));
    }

    private List<TilePorts.Side> splitSides(TileKey tile)
    {
        Set<TilePorts.Side> sides = new java.util.TreeSet<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            if (!edge.getEnd().equals(tile)) continue;

            // A train that came through a link arrived by no side on the grid, so there is no copy for
            // it to land on: splitting would strand it at the far end of the link.  Left whole instead,
            // which costs this one tile the rule and keeps the route.
            if (edge.getEntrySide() == null) return Collections.emptyList();

            sides.add(edge.getEntrySide());
        }

        return new ArrayList<>(sides);
    }

    /**
     * The sides a train that arrived at this square by the given side can carry on out of.
     *
     * Asked of the tile graph rather than worked out from the edges, because only the graph knows which
     * of a square's tracks a side belongs to.  A null arrival - a square nothing reaches by a side of
     * the grid - is unconstrained, matching what leavesBy does with it.
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

    /**
     * The sides trains leave this tile by.
     */
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
     * Every Point a tile is emitted as: one per arrival side, and a second per side where trains may turn
     * round there.  A tile nothing arrives at is emitted whole, since there is no facing to record.
     */
    private List<Node> nodesFor(TileKey tile)
    {
        List<Node> cached = nodeCache.get(tile);

        if (cached != null) return cached;

        List<TilePorts.Side> sides = splitSides(tile);

        List<Node> out = new ArrayList<>();

        if (sides.isEmpty())
        {
            out.add(new Node(tile, null, false));
        }
        else
        {
            boolean canTurn = reversible.contains(tile) || mandatory.contains(tile);
            boolean must = mandatory.contains(tile);

            Set<TilePorts.Side> departures = departureSides(tile);

            for (TilePorts.Side side : sides)
            {
                // Whether a train that came in by this side has anywhere to go but back.
                //
                // Judged against the track it is standing on, not against every side the square has: on
                // a double curve, the other curve's departures are not somewhere this train can go, and
                // counting them hid a dead end behind a track it cannot reach.
                Set<TilePorts.Side> onward = onwardFrom(tile, side);

                boolean onwards = false;

                for (TilePorts.Side departure : departures)
                {
                    if (departure != side && onward.contains(departure)) onwards = true;
                }

                // The plain copy is what lets a train pass straight through.  Where turning is compulsory
                // it is simply not emitted, so there is nothing for the path finder to choose instead -
                // which is the whole difference between "may turn round here" and "must".
                //
                // Nor is it emitted where it would have no way out at all - a dead end, where the only
                // track ahead of an arriving train is the track it came along.  It would be a Point that
                // can be reached and never left, and, being a station like its turning twin, one full
                // autonomy could pick as a destination: the train arrives and its day is over.  The
                // turning copy is the whole truth about a dead end.
                //
                // Only where there IS a turning copy to carry the arrival.  On an unmarked dead end the
                // plain copy is emitted with no way out, because the alternative is emitting nothing and
                // losing the sensor from the graph entirely; that case is what the trapped-arrival check
                // exists to put in front of the user.
                if (!must && (onwards || !canTurn)) out.add(new Node(tile, side, false));

                if (canTurn) out.add(new Node(tile, side, true));
            }
        }

        nodeCache.put(tile, out);

        return out;
    }

    /**
     * Which copy of a split square a placed locomotive stands on.
     *
     * A train facing east was reached from the west, so the copy that holds it is the one whose arrival
     * side is the opposite of its facing.  With nothing authored the first copy is used, which is a
     * guess - and the only wrong thing it can do is send the train off the way it came on its first
     * move, which is why the editor offers the choice on any square where there is one to make.
     *
     * @return the index into nodes, always a valid one
     */
    /**
     * Which copy of a split square a HOME belongs on.
     *
     * A home is a property of the square - "this locomotive lives here" - but the running model hangs
     * it on a Point, and rebuildHomeStations refuses to let two Points claim one locomotive.  Emitted
     * on every copy, as it was, the second and later copies were stripped at every load with a warning
     * that blames a hand-edited file, and the home ended up on whichever copy parsed first: a specific
     * arrival side, chosen by enum order, meaning nothing to the person who authored it.  Where the
     * railway does not allow that approach, Return Home then refuses a home that looks perfectly good
     * on the diagram.
     *
     * Preferred in the order that keeps the most trains able to use it:
     *
     *   1. a plain copy trains may arrive at - somewhere any locomotive can stand and drive out of;
     *   2. any copy trains may arrive at, which on a must-turn square is a turning copy;
     *   3. copy zero, so a home is never silently dropped.
     *
     * A turning station copy is emitted as a TERMINUS, and staging refuses a terminus to a locomotive
     * that cannot reverse unless the route turns on the way - `mustBackIn`, since `20c30781` took that
     * clause out of `canRest` (SVN-C14).  That only decides the home when EVERY copy turns: since
     * 2026-08-31 the home is the square, and HomeStaging asks whether the locomotive can stand on ANY
     * copy of it - so a platform with a turning berth and a through road is a home for anything.
     * Where every copy turns it is a real property of a berth every train must back out of rather
     * than a defect, and AutonomyChecks.checkHomesThatNeedReversing says so on the findings list
     * instead of leaving it to be discovered as an IMPOSSIBLE from Return Home.
     *
     * @param nodes the copies this square was emitted as
     * @return the index of the copy to carry the home
     */
    private int homeCopy(List<Node> nodes)
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
     * The per-square key naming the locomotive that lives at a station.
     */
    private static final String HOME = "home";

    private int placementCopy(List<Node> nodes, JSONObject extras)
    {
        if (extras == null || !extras.has(FACING)) return 0;

        TilePorts.Side facing = side(extras.optString(FACING, null));

        if (facing == null) return 0;

        // Matched on the FACING each copy stands for, not on its arrival side.  Those differ on a
        // turning copy - it is pointing back at the side it came in by - so comparing arrival sides
        // meant a facing learned from a train that had just turned round matched no copy at all, fell
        // through to the first, and put the locomotive on the copy pointing the opposite way.  On a dead
        // end that copy is the one with no way out; elsewhere its first move is the backwards edge this
        // whole split exists to forbid.  Either way the next capture then wrote the wrong facing back.
        for (int copy = 0; copy < nodes.size(); copy++)
        {
            // the plain copy in preference to the turning one: a train standing at a place it MAY turn
            // round has not turned round yet
            if (facingOf(nodes.get(copy)) == facing && !nodes.get(copy).reverse) return copy;
        }

        for (int copy = 0; copy < nodes.size(); copy++)
        {
            if (facingOf(nodes.get(copy)) == facing) return copy;
        }

        return 0;
    }

    /**
     * Which way a train standing on this copy is pointing.
     *
     * The far side of the route it arrived ON, not the opposite of the side it came in by.  Those are
     * the same thing on straight track and different on everything else: a train entering an N-E curve
     * by N leaves by E, and saying it faces S describes a train sitting across the rails.
     *
     * That error was invisible in the model - the split only cares WHICH copy a train is on, and the
     * copies are told apart by arrival - and became visible the moment a facing was shown to a user
     * and used to choose a copy to place on.
     *
     * A switch whose arrival side belongs to more than one route is genuinely ambiguous: which way it
     * faces depends on which branch it is standing on, and the copy does not record that.  The first
     * matching route is taken, which is right for the common case of one route through a sensor and
     * no worse than the old answer anywhere else.
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

    /**
     * A side by name, or null if it is not one - an authored facing can outlive the track it described.
     */
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
     * What a copy is called.
     *
     * A tile that became a single Point keeps its plain name, which is most of them: splitting by
     * arrival side is machinery, and a railway whose every sensor suddenly grew a compass bearing would
     * be paying for that machinery in every list, log line and menu the user reads.  Only a square that
     * genuinely became two Points has to say which is which.
     *
     * Named for the direction of travel rather than the side arrived by, because that is what somebody
     * reading a running log wants: "Main 4 (eastbound, reverse)" says where the train is and what it is
     * about to do, where "Main 4 (in W, rev)" has to be decoded first.
     */
    private String nodeName(String base, Node node)
    {
        if (node.arrival == null || nodesFor(node.tile).size() == 1) return base;

        String name = base + " (" + heading(node.arrival) + (node.reverse ? ", reverse)" : ")");

        // And made unique against everything else, which uniqueNames cannot do on its own: it settles
        // the BASE names, and these suffixes are added afterwards.  Name one point "Main (eastbound)"
        // by hand and let a neighbouring "Main" split, and two Points came out with the same name -
        // which Layout.createPoint refuses, invalidating the whole configuration rather than the one
        // square, and reporting it in terms of a Point name nothing on the diagram carries.
        Set<String> taken = new java.util.LinkedHashSet<>(uniqueNames().values());

        taken.remove(base);

        if (!taken.contains(name)) return name;

        for (int suffix = 2; suffix < 1000; suffix++)
        {
            String candidate = name + " (" + suffix + ")";

            if (!taken.contains(candidate)) return candidate;
        }

        return name;
    }

    /**
     * Which way a train that arrived by this side is pointing - for the NAME, and only for the name.
     *
     * The straight-through answer, which is the opposite of the side arrived by.  facingOf gives a
     * different answer on a curve or a diverging leg, and a better one: it follows the route the train
     * actually took, and its own javadoc records why the naive version was wrong - "A train entering an
     * N-E curve by N leaves by E, and saying it faces S describes a train sitting across the rails."
     *
     * So on those squares the copy is CALLED "(eastbound)" while facingsAt reports its train faces
     * south.  That is one rule in two places with only one of them corrected (UR-16), and it is left
     * that way deliberately for now: a Point's name is what every configuration refers to - placements,
     * homes, exclusions and timetables are all by name - so changing which word appears here renames
     * Points on real layouts, and a configuration naming a Point that no longer exists is refused by
     * parseAuto, which invalidates the whole thing.  Correcting it needs a migration that rewrites the
     * configurations, not a change here.  Raised as MT-138.
     *
     * Nothing routes on this text.  StationIndex.withoutArrivalSuffix strips all four words whichever
     * one is chosen, so that list stays valid either way.
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

            // A locomotive is a physical object and stands on exactly one of the copies.  Everything
            // else authored on a square - homes, lengths, exclusions - is a property of the SQUARE and
            // belongs on all of them.
            int placed = placementCopy(nodes, extras);

            // The other per-square singleton.  See homeCopy.
            int homeOn = homeCopy(nodes);

            for (int copy = 0; copy < nodes.size(); copy++)
            {
                Node node = nodes.get(copy);

                JSONObject json = new JSONObject();

                json.put("name", nodeName(names.get(point.getTile()), node));

                // A station, unless a train arriving THIS way is not allowed to stop.  The copy still
                // exists and still carries traffic; it is simply not somewhere a train can be sent.
                //
                // Decided ONCE, because two flags are emitted from it.  Read again below off the
                // square, the turn-round copy of a barred side came out as a terminus that is not a
                // destination - a pair Point.setTerminus refuses, and parseAuto answers a refusal by
                // invalidating the whole layout.  Restricting a terminus platform, which is the most
                // natural use this setting has, made the entire setup unloadable.
                boolean stops = point.isStation() && arrivalAllowed(node);

                json.put("station", stops);
                json.put("s88", point.getS88());

                // Which copies are the same piece of track.
                //
                // Only where a square is emitted as more than one Point: below that there is nothing to
                // group, and saying so would put a line in every file to no purpose.  Two trains cannot
                // stand on one square - that is a collision - but occupancy is recorded per Point, so
                // without this a sibling copy reads free while a train stands on its twin.
                //
                // The TILE, not the s88.  Genuinely different places share a sensor on a real layout -
                // a station, its approach guard and a reversing point can be three Points on one
                // feedback - so the sensor cannot say which Points are one square.  The tile can.
                if (nodes.size() > 1) json.put("block", point.getTile().toString());

                // The points this station is held back by, by NAME (FR-001).
                //
                // The same setting is also emitted as LOCK EDGES further down, and the two answer
                // different questions on purpose: a lock edge asks whether that approach is held by a
                // route, and this asks whether a train is STANDING on the square. Adam asked for both.
                //
                // On every copy of the station, because the copies are one platform - the same reason
                // the protecting signals are on every copy.
                //
                // One name per watched square, and the FIRST copy of it: the rule asks about the whole
                // block, so any copy's name reaches the same piece of track, and writing them all would
                // be several names for one answer.
                List<TileKey> held = blockingPoints.get(point.getTile());

                if (held != null && !held.isEmpty())
                {
                    JSONArray watching = new JSONArray();

                    for (TileKey square : held)
                    {
                        List<Node> copies = nodesFor(square);

                        if (copies.isEmpty()) continue;

                        watching.put(nodeName(names.get(square), copies.get(0)));
                    }

                    if (watching.length() > 0) json.put("blockedBy", watching);
                }

                // The signals thrown to red while this platform is claimed.  On every copy, because
                // the copies are one platform.
                //
                // Including a copy whose arrival side is barred, which this used to leave out by also
                // asking `stops`.  A bar stops autonomy routing a train in; it does not stop a person
                // driving one in, and it does not stop one being placed there by hand - so a train can
                // be standing on that copy.  refreshOneSignal decides by asking every Point whose
                // protecting signals hold the accessory, and a copy left out is not asked: the signal
                // showed GREEN over an occupied platform, which is the failure protection exists to
                // prevent.
                //
                // Safe to emit on a non-station.  setProtectingSignals stores what it is given and
                // parseAuto does not check it against `station` - unlike the terminus flag two blocks
                // below, where the model refuses the pair and answers a refusal by invalidating the
                // whole configuration.  That is why `stops` is consulted there and not here.
                //
                // One is written as a bare string and several as an array, which is the shape parseAuto
                // reads and the shape every version before this one wrote.
                List<String> protecting = protectingSignals.get(point.getTile());

                if (protecting != null && !protecting.isEmpty())
                {
                    json.put("protectingSignal", protecting.size() == 1
                        ? (Object) protecting.get(0) : new JSONArray(protecting));
                }

                if (coordinatePages != null)
                {
                    // Roughly one tile per 60 units, which is the spacing the hand-written files use,
                    // with each page stacked below the last so they do not overlap.  Split copies are
                    // fanned out a little, or they would land exactly on top of one another.
                    int page = Math.max(0, coordinatePages.indexOf(point.getTile().getPage()));

                    json.put("x", point.getTile().getX() * 60 + copy * 14);
                    json.put("y", point.getTile().getY() * 60 + page * 1800 + copy * 14);
                }

                if (extras != null)
                {
                    for (String key : extras.keySet())
                    {
                        // never the structural fields: those are the reduction's to decide
                        if (json.has(key)) continue;

                        // One locomotive, one copy.  Emitted on every copy of a split square it became
                        // two trains with one name standing on the same sensor, which the running model
                        // has no way to reconcile.
                        if (LOCOMOTIVE.equals(key) && copy != placed) continue;

                        // One home, one copy, for the same reason and with a different rule for which
                        // copy - a train stands where it stands, but a home should land where the most
                        // locomotives can reach it.  See homeCopy.
                        if (HOME.equals(key) && copy != homeOn) continue;

                        // On a split tile, turning round is what the COPY means, so the authored flag
                        // is not carried onto either of them: it would make the plain copy reverse too,
                        // which is the behaviour the split exists to separate.  CAN_REVERSE never goes
                        // out at all - it is the instruction to split, not something parseAuto knows.
                        if (CAN_REVERSE.equals(key) || PARKING.equals(key)
                                || FACING.equals(key) || AUTO_DESTINATION.equals(key)) continue;

                        if (DERIVED.contains(key)) continue;

                        // ACTIVE IS CARRIED ON EVERY SQUARE, NOT ONLY ON A STATION (D24-B5).
                        //
                        // It used to be dropped here for anything that is not a station, on the
                        // reasoning that "the arrows say that through the derivation, so a value
                        // stored on a plain sensor is not carried out", and that the menu no longer
                        // offered it there.
                        //
                        // Both halves were wrong.  The three-way usage menu offers **Out of service**
                        // on every square there is - `setUsage(target, isStation, false)` - and it
                        // writes `active` and nothing else: it does not touch a single direction.  So
                        // marking a plain sensor out of service drew a cross in the editor, was
                        // dropped here, and trains carried on running through it.
                        //
                        // `Layout.isPathClear` refuses a path whose intermediate point is inactive,
                        // which is exactly what the cross promises, and it can only do that if the
                        // flag reaches the graph.

                        json.put(key, extras.get(key));
                    }
                }

                // A station turns round at a terminus; anything else turns round at a reversing point.
                // Same physical act, and the model spells it two ways: a terminus is a destination that
                // reverses on arrival, a reversing point is one autonomy will never choose to stop at.
                //
                // Either the copy that exists to turn trains round, or - where the square was marked and
                // nothing arrives at it by any side of the grid, so there was no facing to record and no
                // copy to make - the single Point it became.  Without that second case a square reached
                // only through a link emitted no flag at all and its trains would have run into the
                // buffers.
                //
                // MUST, not may, in that second case.  The flag means "every arriving train reverses",
                // which is what "must" says and is emphatically not what "may" says - so putting it on a
                // may-turn square silently promoted the user's choice to the other one, and made a
                // through station one no path could be routed through.  Where "may" cannot be expressed
                // - which is exactly this case, since expressing it needs the two copies a split makes -
                // nothing is emitted, and the checks report that the marking cannot mean anything here.
                if (node.reverse
                    || (mandatory.contains(point.getTile()) && splitSides(point.getTile()).isEmpty()))
                {
                    // Against what THIS copy is, not what the square is: a terminus must be a
                    // destination, and a copy trains may not arrive at is not one.  Emitted as a plain
                    // reversing point instead, which is what it now is - somewhere trains turn round
                    // and nobody is sent.
                    json.put(stops ? "terminus" : "reversing", true);
                }

                // A berth is an ordinary station that autonomy is told not to choose.  It used to be
                // emitted as a reversing station, which was the only way to say that and which dragged
                // two unrelated behaviours along: the train reversed on arrival whether or not anybody
                // asked, and no path could be routed through.  Those are what a terminus and a shut arm
                // say now, each on its own, so this says only the thing it means.
                if (manualOnly.contains(point.getTile()))
                {
                    json.put(AUTO_DESTINATION, false);
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

        // Which emitted name pairs each reduced edge became, so a lock on one edge becomes a lock on
        // every copy of it.  Over-locking rather than under: a split turns one physical route into
        // several logical ones, and they are all still the same piece of track.
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
                        nodeName(names.get(edge.getStart()), from),
                        nodeName(names.get(edge.getEnd()), to)
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

            // AND HOW MUCH OF IT IS AFTER THE LAST SWITCH (Adam's ruling, 2026-09-02).
            //
            // "Between the switch and the station, the length must be >= length of the train", and
            // "for the switches, for simplicity, let's use any direction, that way we are guaranteed
            // to be safe".  The reducer measures that stretch because it is the only part of this that
            // walks tiles; runtime `Edge` knows a length and nothing about what it is made of.
            //
            // Written only where the edge crosses a switch.  An edge that crosses none does not bound
            // where a train may stop, and the guard walks back past it - so the key being ABSENT is
            // meaningful and is not the same as a zero.
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

            // FR-001, on top of the locks the reduction derived: an edge arriving at a station somebody
            // has held back gains every edge that ENDS at the square being watched.
            //
            // Every emitted copy of those edges, like the derived locks above - a split turns one piece
            // of track into several named edges, and a restriction that named only one of them would be
            // satisfied by routing over its twin.
            //
            // Squares that no longer exist are simply not found here, so a restriction watching deleted
            // track quietly stops applying rather than taking the station out of service.  reconcile
            // drops it from the setup on the next pass and says so.
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
     * The name each Point will carry in the generated file, disambiguated where two collide.
     *
     * A user who names two Points the same thing is told at authoring time; this is the backstop for
     * generated names, and for the case where a rename has not yet been applied everywhere.
     */
    /**
     * Every name this builder would emit, mapped back to the square it belongs to.
     *
     * uniqueNames() answers "what is this tile called", which stops being the whole answer once a tile
     * is emitted as several Points.  Anything working backwards from a running configuration - the
     * diagram monitor, the capture that saves where the locomotives ended up - needs this instead, or
     * it silently loses every split copy.
     *
     * @return emitted Point name to tile
     */
    public Map<String, TileKey> tilesByName()
    {
        Map<String, TileKey> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, String> entry : uniqueNames().entrySet())
        {
            for (Node node : nodesFor(entry.getKey()))
            {
                out.put(nodeName(entry.getValue(), node), entry.getKey());
            }
        }

        return out;
    }

    /**
     * Every emitted name mapped to the name the SQUARE has.
     *
     * The track diagram registers a station label under the caption written on it - the base name - and
     * the running configuration may know that place as several Points.  Anything holding a running
     * Point and wanting the label, or holding a label and wanting the Point, has to go through this or
     * the two simply never meet.
     *
     * @return emitted Point name to base name.  An unsplit Point maps to itself.
     */
    public Map<String, String> baseNames()
    {
        Map<String, String> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, String> entry : uniqueNames().entrySet())
        {
            for (Node node : nodesFor(entry.getKey()))
            {
                out.put(nodeName(entry.getValue(), node), entry.getValue());
            }
        }

        return out;
    }

    /**
     * Every emitted name mapped to the way a train standing on it is pointing.
     *
     * This is how a facing gets LEARNED rather than asked for.  Once autonomy has run, the Point a
     * locomotive ended on says which way round it is, and capturing that back means the user never has
     * to answer the question again for that train - and, on a railway that has run once, never has to
     * answer it at all.
     *
     * @return emitted Point name to the side of the square its front faces.  Only split copies appear,
     *         because an unsplit Point records no facing.
     */
    public Map<String, TilePorts.Side> facingByName()
    {
        Map<String, TilePorts.Side> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, String> entry : uniqueNames().entrySet())
        {
            for (Node node : nodesFor(entry.getKey()))
            {
                if (node.arrival == null) continue;

                out.put(nodeName(entry.getValue(), node), facingOf(node));
            }
        }

        return out;
    }

    /**
     * Every emitted edge name - "start -> end" over Point names - mapped to the reduced edge it came
     * from.  Several names can share one reduced edge once a tile is split.
     *
     * @return edge name to the route it describes
     */
    public Map<String, ReducedEdge> edgesByName()
    {
        Map<String, ReducedEdge> out = new LinkedHashMap<>();
        Map<TileKey, String> names = uniqueNames();

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

                    out.put(nodeName(start, from) + " -> " + nodeName(end, to), edge);
                }
            }
        }

        return out;
    }

    public Map<TileKey, String> uniqueNames()
    {
        Map<TileKey, String> out = new LinkedHashMap<>();
        Map<String, Integer> seen = new LinkedHashMap<>();

        List<ReducedPoint> points = new ArrayList<>(reducer.getPoints().values());
        Collections.sort(points, new Comparator<ReducedPoint>()
        {
            @Override
            public int compare(ReducedPoint a, ReducedPoint b)
            {
                return a.getTile().toString().compareTo(b.getTile().toString());
            }
        });

        for (ReducedPoint point : points)
        {
            String base = point.getName();
            Integer count = seen.get(base);

            if (count == null)
            {
                seen.put(base, 1);
                out.put(point.getTile(), base);

                continue;
            }

            // Keep suffixing until the result is genuinely unused, and record what was emitted.  The
            // disambiguated name used to be written out without ever being added to seen, so three
            // points named X, X and "X (2)" produced "X (2)" twice - which parseAuto rejects outright,
            // and which silently merges two tiles' placements when capture inverts this map.
            int suffix = count + 1;
            String candidate = base + " (" + suffix + ")";

            while (seen.containsKey(candidate))
            {
                candidate = base + " (" + (++suffix) + ")";
            }

            seen.put(base, suffix);
            seen.put(candidate, 1);

            out.put(point.getTile(), candidate);
        }

        return out;
    }
}
