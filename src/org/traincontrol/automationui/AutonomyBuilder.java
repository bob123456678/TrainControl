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
     * One emitted Point: a tile, and - where the tile is split - which arrival side it stands for and
     * whether it is the copy that reverses.
     *
     * The reduction gives one Point per sensor, which is a truthful model of the track and a lossy
     * model of what a train may do there.  Arriving from the west and carrying on east is a different
     * move from arriving from the west and backing out west again, and as one Point they are the same
     * edge set - so either the reversal is unreachable or every passing train performs one.
     *
     * A hand-written configuration solved this by putting several Points on one s88.  The sample
     * layout does exactly that: BottomMainC and BottomMainCTerm are both station, both s88 4, both
     * entered from BottomMainCPre - one leaves to BottomMainPost going on, the other to
     * TunnelReversePre having turned round.  This class reconstructs that shape from the diagram.
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

        /**
         * Whether a train standing on this copy may leave by the given side.
         *
         * The whole point of the split, in two lines: the reversing copy leaves the way it came, and
         * the plain copy leaves any other way.
         */
        boolean leavesBy(TilePorts.Side exitSide)
        {
            if (arrival == null) return true;

            return reverse ? arrival == exitSide : arrival != exitSide;
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
     * The tiles where a train may turn round, each of which is emitted as several Points.
     *
     * A tile is only actually split when it has more than one arrival side.  A dead-end platform is
     * marked terminus like any other, but splitting it would produce a plain copy with nowhere to go -
     * a station a train could reach and never leave.
     *
     * @param tiles the marked tiles, or null for none
     * @return this
     */
    public AutonomyBuilder withReversibleTiles(Set<TileKey> tiles)
    {
        this.reversible = tiles == null ? Collections.<TileKey>emptySet() : tiles;
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
        return this;
    }

    public AutonomyBuilder withParkingTiles(Set<TileKey> tiles)
    {
        this.manualOnly = tiles == null ? Collections.<TileKey>emptySet() : tiles;
        return this;
    }

    /**
     * The sides trains arrive at this tile by, in a fixed order, or empty when it is not split.
     */
    private List<TilePorts.Side> splitSides(TileKey tile)
    {
        if (!reversible.contains(tile)) return Collections.emptyList();

        Set<TilePorts.Side> sides = new java.util.TreeSet<>();

        for (ReducedEdge edge : reducer.getEdges())
        {
            // a portal arrival has no side on the grid, so it cannot be told apart from any other and
            // the tile is left whole
            if (edge.getEnd().equals(tile) && edge.getEntrySide() != null)
            {
                sides.add(edge.getEntrySide());
            }
        }

        return sides.size() < 2 ? Collections.<TilePorts.Side>emptyList()
            : new ArrayList<>(sides);
    }

    /**
     * Every Point a tile is emitted as: one when it is not split, two per arrival side when it is.
     */
    private List<Node> nodesFor(TileKey tile)
    {
        List<TilePorts.Side> sides = splitSides(tile);

        List<Node> out = new ArrayList<>();

        if (sides.isEmpty())
        {
            out.add(new Node(tile, null, false));
            return out;
        }

        boolean must = mandatory.contains(tile);

        for (TilePorts.Side side : sides)
        {
            // The plain copy is what lets a train pass straight through.  Where turning is compulsory
            // it is simply not emitted, so there is nothing for the path finder to choose instead -
            // which is the whole difference between "may turn round here" and "must".
            if (!must) out.add(new Node(tile, side, false));

            out.add(new Node(tile, side, true));
        }

        return out;
    }

    /**
     * What a split copy is called.
     *
     * Named for the direction of travel rather than the side arrived by, because that is what somebody
     * reading a running log wants: "Main 4 (eastbound, reverse)" says where the train is and what it is
     * about to do, where "Main 4 (in W, rev)" has to be decoded first.
     */
    private static String nodeName(String base, Node node)
    {
        if (node.arrival == null) return base;

        return base + " (" + heading(node.arrival) + (node.reverse ? ", reverse)" : ")");
    }

    /**
     * Which way a train that arrived by this side is pointing.
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

            for (int copy = 0; copy < nodes.size(); copy++)
            {
                Node node = nodes.get(copy);

                JSONObject json = new JSONObject();

                json.put("name", nodeName(names.get(point.getTile()), node));
                json.put("station", point.isStation());
                json.put("s88", point.getS88());

                if (coordinatePages != null)
                {
                    // Roughly one tile per 60 units, which is the spacing the hand-written files use,
                    // with each page stacked below the last so they do not overlap.  Split copies are
                    // fanned out a little, or they would land exactly on top of one another.
                    int page = Math.max(0, coordinatePages.indexOf(point.getTile().getPage()));

                    json.put("x", point.getTile().getX() * 60 + copy * 14);
                    json.put("y", point.getTile().getY() * 60 + page * 1800 + copy * 14);
                }

                JSONObject extras = pointExtras == null
                    ? null : pointExtras.get(point.getTile().toString());

                if (extras != null)
                {
                    for (String key : extras.keySet())
                    {
                        // never the structural fields: those are the reduction's to decide
                        if (json.has(key)) continue;

                        // On a split tile, turning round is what the COPY means, so the authored flag
                        // is not carried onto either of them: it would make the plain copy reverse too,
                        // which is the behaviour the split exists to separate.  CAN_REVERSE never goes
                        // out at all - it is the instruction to split, not something parseAuto knows.
                        if (CAN_REVERSE.equals(key) || PARKING.equals(key)
                                || AUTO_DESTINATION.equals(key)) continue;

                        if (DERIVED.contains(key)) continue;

                        // Active is a station's switch now.  On anything else it said exactly one
                        // thing - no path may pass through here - and the arrows say that through the
                        // derivation, so a value stored on a plain sensor is not carried out.  Nothing
                        // in the autonomy model changes; it simply stops being emitted where the menu
                        // no longer offers it.
                        if ("active".equals(key) && !point.isStation()) continue;

                        json.put(key, extras.get(key));
                    }
                }

                // A station turns round at a terminus; anything else turns round at a reversing point.
                // Same physical act, and the model spells it two ways: a terminus is a destination that
                // reverses on arrival, a reversing point is one autonomy will never choose to stop at.
                //
                // Either the copy that exists to turn trains round, or - where the square was marked
                // but has only one way in, so there was nothing to split - the single copy it became.
                // Without that second case a dead-end platform marked "trains turn round here" emitted
                // no flag at all and its trains would have run into the buffers.
                if (node.reverse || (reversible.contains(point.getTile()) && splitSides(point.getTile()).isEmpty()))
                {
                    json.put(point.isStation() ? "terminus" : "reversing", true);
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
                if (!from.leavesBy(edge.getExitSide())) continue;

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
                if (!from.leavesBy(edge.getExitSide())) continue;

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
