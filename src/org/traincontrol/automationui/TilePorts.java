package org.traincontrol.automationui;

import org.traincontrol.base.LayoutDiagramComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.traincontrol.base.Accessory.accessorySetting;
import org.traincontrol.base.LayoutDiagramComponent.componentType;

/**
 * The port map: which sides of a track diagram tile are connected to each other.
 *
 * The seed for autonomy's tile graph.  Geometry proposes the connections and the user corrects them in the diagram
 * editor, so a misreading here shows up as a visibly wrong line rather than a silently wrong edge.
 *
 * The map is state-indexed: ports(type, orientation, state) returns the routes that exist in that state.  Switches
 * REPLACE their routes when thrown rather than adding to them (a SWITCH_LEFT is N-S unswitched and S-W switched), so
 * there is no "common" leg, branch or toe in this class.  Non-switch tiles have a single state.
 *
 * Orientation follows getImage: the icon is rotated by (4 - orientation) * 90 degrees clockwise.  Valid orientations
 * are what LayoutDiagramComponent.getNumOrientations() allows for the type; asking for orientation 3 of a STRAIGHT is
 * a bug, not a rotation.
 *
 * The table was verified tile by tile against the icon art (`assets/automation/tile-ports.png` is drawn from it), and
 * testAutonomyDiagramPorts.testTheWholePortTableIsWhatTheMapSaysItIs executes all of it, so adding a tile type without
 * stating its ports fails the build.
 *
 * @author Adam
 */
public class TilePorts
{
    /** A side of a tile, in clockwise order (which is what rotation walks). */
    public static enum Side
    {
        N, E, S, W;

        /** Rotates this side by the given number of quarter turns clockwise. */
        public Side rotateClockwise(int quarterTurns)
        {
            return values()[(ordinal() + ((quarterTurns % 4) + 4) % 4) % 4];
        }

        /** The side directly across the tile. */
        public Side opposite()
        {
            return rotateClockwise(2);
        }
    }

    /** Which of a tile's accessories a command applies to; SECONDARY is a three-way turnout's second. */
    public static enum AccessorySlot
    {
        PRIMARY, SECONDARY
    }

    /**
     * A path through a tile between two of its sides, normally traversable both ways.  A CUSTOM_PERM_* turnout (a
     * defective switch with no address, which cannot be thrown) may only be entered from a branch and left at the toe:
     * a facing move cannot choose a branch with the blades stuck, while a trailing move merges safely.  That is
     * hardware, so it lives here; user-authored direction is stored separately and ANDs with it.
     */
    public static class Route
    {
        private final Side a;
        private final Side b;
        private final Side directedToward;

        Route(Side a, Side b, Side directedToward)
        {
            this.a = a;
            this.b = b;
            this.directedToward = directedToward;
        }

        public Side getA()
        {
            return a;
        }

        public Side getB()
        {
            return b;
        }

        /** The only side this route may be traveled toward, or null when it is traversable both ways. */
        public Side getDirectedToward()
        {
            return directedToward;
        }

        /** True if this route touches the given side. */
        public boolean touches(Side side)
        {
            return a == side || b == side;
        }

        /** The far side of this route from the one given, or null if it does not touch that side. */
        public Side other(Side from)
        {
            if (a == from) return b;
            if (b == from) return a;
            return null;
        }

        /** Whether a train may enter at the given side and leave at the other. */
        public boolean isTraversableFrom(Side from)
        {
            if (!touches(from)) return false;
            return directedToward == null || directedToward == other(from);
        }

        /** Rotates this route by the given number of quarter turns clockwise. */
        public Route rotateClockwise(int quarterTurns)
        {
            return new Route(
                a.rotateClockwise(quarterTurns),
                b.rotateClockwise(quarterTurns),
                directedToward == null ? null : directedToward.rotateClockwise(quarterTurns)
            );
        }

        @Override
        public String toString()
        {
            if (directedToward == null)
            {
                return a.toString() + "-" + b.toString();
            }

            return other(directedToward).toString() + "->" + directedToward.toString();
        }
    }

    /** One switch position (or a fixed tile's only one): its routes and the accessory settings that select it. */
    public static class TileState
    {
        private final List<Route> routes;
        private final Map<AccessorySlot, accessorySetting> commands;

        TileState(List<Route> routes, Map<AccessorySlot, accessorySetting> commands)
        {
            this.routes = Collections.unmodifiableList(routes);
            this.commands = Collections.unmodifiableMap(commands);
        }

        public List<Route> getRoutes()
        {
            return routes;
        }

        /**
         * The accessory settings that must be commanded for this state to hold.  Every address the tile has is always
         * specified (a three-way's straight route commands both to straight), or a thrown branch would stay selected.
         */
        public Map<AccessorySlot, accessorySetting> getCommands()
        {
            return commands;
        }

        TileState rotateClockwise(int quarterTurns)
        {
            List<Route> rotated = new ArrayList<>();

            for (Route r : routes)
            {
                rotated.add(r.rotateClockwise(quarterTurns));
            }

            return new TileState(rotated, commands);
        }
    }

    // The port map at orientation 0, by type.  Index in the list is the state index.
    private static final Map<componentType, List<TileState>> PORTS = new EnumMap<>(componentType.class);

    // Types that cannot appear on a diagram used for autonomy at all
    private static final Map<componentType, Boolean> DISQUALIFIED = new EnumMap<>(componentType.class);

    // Types autonomy stops at rather than routes through
    private static final Map<componentType, Boolean> TERMINATOR = new EnumMap<>(componentType.class);

    // Types whose second port is a user-paired portal to another tile, possibly on another page
    private static final Map<componentType, Boolean> PORTAL = new EnumMap<>(componentType.class);

    // Types that carry no track of their own but let whatever line they sit on run beneath them
    private static final Map<componentType, Boolean> TRANSPARENT = new EnumMap<>(componentType.class);

    static
    {
        fixed(componentType.STRAIGHT, route(Side.E, Side.W));
        fixed(componentType.CURVE, route(Side.E, Side.S));
        fixed(componentType.DOUBLE_CURVE, route(Side.N, Side.W), route(Side.E, Side.S));

        // Feedback tiles are plain track that also carries an s88.  Every one of them becomes a Point.
        fixed(componentType.FEEDBACK, route(Side.E, Side.W));
        fixed(componentType.FEEDBACK_CURVE, route(Side.E, Side.S));
        // The contact is on the E-S curve as drawn - the second road, which `contactRoad` names
        fixed(componentType.FEEDBACK_DOUBLE_CURVE, route(Side.N, Side.W), route(Side.E, Side.S));

        // A signal must be GREEN for a train to pass, so traversing one carries a command just as throwing a switch
        // does and the reducer gathers commands along a path uniformly.  Other signals are set RED by conditional
        // routes.
        signalled(componentType.SIGNAL, accessorySetting.GREEN, route(Side.E, Side.W));

        // An uncoupler has an address too, but firing it is an operation the user asks for, not a
        // condition of passing over the tile, so nothing is commanded when a path crosses one.
        fixed(componentType.UNCOUPLER, route(Side.E, Side.W));

        // Two independent through routes that share the tile: they never join but do conflict (one physical crossing).
        fixed(componentType.CROSSING, route(Side.N, Side.S), route(Side.E, Side.W));

        // Grade separated, so the routes neither join nor conflict: the one exception to "shared tile is exclusive".
        fixed(componentType.OVERPASS, route(Side.N, Side.S), route(Side.E, Side.W));

        fixed(componentType.END, stub(Side.N));

        // A tunnel connects normally on its visible side; its continuation is a user-paired tile.
        fixed(componentType.TUNNEL, stub(Side.S));
        PORTAL.put(componentType.TUNNEL, Boolean.TRUE);

        // A link's art is a box holding an east-pointing arrow: the track attaches on the side opposite
        // the arrow head, and the arrow points away toward the page being jumped to.
        fixed(componentType.LINK, stub(Side.W));
        PORTAL.put(componentType.LINK, Boolean.TRUE);

        // Thrown replaces the route set rather than adding to it.
        switched(componentType.SWITCH_LEFT,
            state(commands(accessorySetting.STRAIGHT), route(Side.N, Side.S)),
            state(commands(accessorySetting.TURN), route(Side.S, Side.W)));

        switched(componentType.SWITCH_RIGHT,
            state(commands(accessorySetting.STRAIGHT), route(Side.N, Side.S)),
            state(commands(accessorySetting.TURN), route(Side.S, Side.E)));

        // A Y turnout has no straight route in either position.
        switched(componentType.SWITCH_Y,
            state(commands(accessorySetting.STRAIGHT), route(Side.S, Side.W)),
            state(commands(accessorySetting.TURN), route(Side.S, Side.E)));

        // Two addresses, three positions.  Both addresses are always commanded.
        switched(componentType.SWITCH_THREE,
            state(commands(accessorySetting.STRAIGHT, accessorySetting.STRAIGHT), route(Side.N, Side.S)),
            state(commands(accessorySetting.TURN, accessorySetting.STRAIGHT), route(Side.S, Side.W)),
            state(commands(accessorySetting.STRAIGHT, accessorySetting.TURN), route(Side.S, Side.E)));

        // A double slip: the two through routes when straight, the two diagonals when thrown.  It is the
        // only tile that contributes commands in both states rather than only when thrown.
        switched(componentType.SWITCH_CROSSING,
            state(commands(accessorySetting.STRAIGHT), route(Side.N, Side.S), route(Side.E, Side.W)),
            state(commands(accessorySetting.TURN), route(Side.N, Side.W), route(Side.S, Side.E)));

        // No address, so they cannot be thrown: trailing moves only, into the toe at S.
        fixed(componentType.CUSTOM_PERM_LEFT,
            into(Side.S, Side.N), into(Side.S, Side.W));
        fixed(componentType.CUSTOM_PERM_RIGHT,
            into(Side.S, Side.N), into(Side.S, Side.E));
        fixed(componentType.CUSTOM_PERM_Y,
            into(Side.S, Side.W), into(Side.S, Side.E));
        fixed(componentType.CUSTOM_PERM_THREEWAY,
            into(Side.S, Side.N), into(Side.S, Side.W), into(Side.S, Side.E));

        // Scissors are a drawing convention: two tiles depicting one double slip.  Their topology cannot
        // be expressed per tile, so a diagram carrying one cannot be used for autonomy at all.
        DISQUALIFIED.put(componentType.CUSTOM_SCISSORS, Boolean.TRUE);
        DISQUALIFIED.put(componentType.CUSTOM_PERM_SCISSORS, Boolean.TRUE);

        // A turntable connects any radial to any other under manual control and has no static port map.
        TERMINATOR.put(componentType.TURNTABLE, Boolean.TRUE);

        // A route button is TRANSPARENT: its art touches no border and its orientation says nothing, so what it
        // conducts depends on its neighbors, which this pure function of type and orientation cannot see: TileGraph
        // resolves it.  Layouts thread them through running lines (the sample layout has 43): treating them as
        // decoration severs those lines, while a fixed crossing invents track for a button placed beside the rails.
        fixed(componentType.ROUTE);
        TRANSPARENT.put(componentType.ROUTE, Boolean.TRUE);

        fixed(componentType.LAMP);
        fixed(componentType.TEXT);
    }

    private TilePorts()
    {
    }

    /**
     * The routes a tile of this type offers, in the given state, at the given orientation.
     *
     * @param orientation 0 .. getNumOrientations() - 1 for the type
     * @param state 0 .. getStateCount() - 1 for the type
     * @return the routes, empty for decorative, disqualified, terminator and unmapped types
     */
    public static List<Route> ports(componentType type, int orientation, int state)
    {
        List<TileState> states = PORTS.get(type);

        if (states == null || state < 0 || state >= states.size())
        {
            return Collections.emptyList();
        }

        // getImage rotates by (4 - orientation) * 90 degrees clockwise, so the ports follow
        return states.get(state).rotateClockwise(4 - normalizeOrientation(type, orientation)).getRoutes();
    }

    /**
     * The road of a sensor square its contact is on, at this orientation: its only road on a straight or a curve, and
     * on a double curve the E-S curve as drawn, turned with the square (MT-712; Adam: *"the station gets rendered on
     * the track without the sensor rather than the one with the sensor"*).  That is the second of `ports` at every
     * orientation, and where a station's badge is drawn.  What a train on the other curve reaches is separate: the
     * diagram does not say which track is wired to the contact, and the reduction makes one Point of the square.
     *
     * @return the road, or null for a square with none
     */
    public static Route contactRoad(componentType type, int orientation)
    {
        List<Route> roads = ports(type, orientation, 0);

        if (roads.isEmpty()) return null;

        return type == componentType.FEEDBACK_DOUBLE_CURVE && roads.size() == 2 ? roads.get(1) : roads.get(0);
    }

    /** The accessory settings that select the given state; empty for tiles with nothing to command. */
    public static Map<AccessorySlot, accessorySetting> commands(componentType type, int state)
    {
        List<TileState> states = PORTS.get(type);

        if (states == null || state < 0 || state >= states.size())
        {
            return Collections.emptyMap();
        }

        return states.get(state).getCommands();
    }

    /** How many positions this type has: 1 for fixed track, 2 for an ordinary switch, 3 for a three-way. */
    public static int getStateCount(componentType type)
    {
        List<TileState> states = PORTS.get(type);

        return states == null ? 0 : states.size();
    }

    /**
     * True if this type carries a second, user-paired port to a tile elsewhere (a tunnel or a page link).  The pairing
     * is authored, never inferred; an unpaired portal simply does not connect.
     */
    public static boolean hasPortal(componentType type)
    {
        return PORTAL.containsKey(type);
    }

    /**
     * True if a diagram carrying this type cannot be used for autonomy.  Scissors are a drawing convention, refused
     * rather than ignored: ignoring one would leave a hole that traces quietly route around.
     */
    public static boolean isDisqualified(componentType type)
    {
        return DISQUALIFIED.containsKey(type);
    }

    /** True if autonomy stops at this type rather than routing through it. */
    public static boolean isTerminator(componentType type)
    {
        return TERMINATOR.containsKey(type);
    }

    /**
     * True if this type carries no track of its own but lets whatever line it sits on pass beneath it.  A route button
     * is the case: what it conducts depends on what is next to it, context this class cannot see, so callers resolve
     * it.
     */
    public static boolean isTransparent(componentType type)
    {
        return TRANSPARENT.containsKey(type);
    }

    /**
     * True if a square of this type is never given a length and no length rule reads one (OB-273).  Adam: *"a route
     * tile should not need or accept a length.  it just implicitly connects things as if it were a crossing."*  So it
     * is in no piece of Mass Assign Lengths, takes no share of one, and is never reported as unmeasured.  **The one
     * list of such types**, asked once here because two lists that must agree is how OB-233 happened.
     */
    public static boolean takesNoLength(componentType type)
    {
        return isTransparent(type);
    }

    /** True if this type has routes in any state (false for decoration, turntables and disqualified tiles). */
    public static boolean isRoutable(componentType type)
    {
        if (isDisqualified(type) || isTerminator(type)) return false;
        if (isTransparent(type)) return true;

        for (int state = 0; state < getStateCount(type); state++)
        {
            if (!ports(type, 0, state).isEmpty()) return true;
        }

        return false;
    }

    /**
     * The side every route of this type touches in every position (the toe of a turnout), derived from the port map so
     * it cannot drift from the table; null when the routes share none (a double slip, or plain track with several
     * routes).  Used only for the direction default, which runs base to forks, so no toe defaults to bidirectional.
     */
    public static Side deriveToe(componentType type, int orientation)
    {
        int states = getStateCount(type);

        if (states == 0) return null;

        List<Side> candidates = new ArrayList<>();
        Collections.addAll(candidates, Side.values());

        boolean sawRoute = false;

        for (int state = 0; state < states; state++)
        {
            for (Route r : ports(type, orientation, state))
            {
                // a stub touches one side; it is not a fan-out, so it has no toe
                if (r.getA() == r.getB()) return null;

                sawRoute = true;

                List<Side> next = new ArrayList<>();

                for (Side s : candidates)
                {
                    if (r.touches(s)) next.add(s);
                }

                candidates = next;

                if (candidates.isEmpty()) return null;
            }
        }

        // A single route shares both its sides, which says nothing about fanning out
        if (!sawRoute || candidates.size() != 1) return null;

        return candidates.get(0);
    }

    /**
     * Reduces the orientation by the type's rotational symmetry (a STRAIGHT has two distinct orientations, a CROSSING
     * one), so a caller bug cannot produce a rotation the diagram can never contain.
     */
    private static int normalizeOrientation(componentType type, int orientation)
    {
        int allowed = numOrientations(type);

        return ((orientation % allowed) + allowed) % allowed;
    }

    /**
     * How many distinct orientations a tile type has.  Asks LayoutDiagramComponent rather than answering itself: a
     * verbatim copy of its body agreed only until somebody added a tile type with an axis of symmetry.
     */
    public static int numOrientations(componentType type)
    {
        return org.traincontrol.base.LayoutDiagramComponent.getNumOrientations(type);
    }

    private static Route route(Side a, Side b)
    {
        return new Route(a, b, null);
    }

    /** A route that may only be traveled toward the given side. */
    private static Route into(Side toward, Side from)
    {
        return new Route(from, toward, toward);
    }

    /** A single open port: a dead end, or the visible side of a portal tile. */
    private static Route stub(Side side)
    {
        return new Route(side, side, null);
    }

    private static Map<AccessorySlot, accessorySetting> commands(accessorySetting primary)
    {
        Map<AccessorySlot, accessorySetting> map = new HashMap<>();
        map.put(AccessorySlot.PRIMARY, primary);
        return map;
    }

    private static Map<AccessorySlot, accessorySetting> commands(accessorySetting primary, accessorySetting secondary)
    {
        Map<AccessorySlot, accessorySetting> map = new HashMap<>();
        map.put(AccessorySlot.PRIMARY, primary);
        map.put(AccessorySlot.SECONDARY, secondary);
        return map;
    }

    private static TileState state(Map<AccessorySlot, accessorySetting> commands, Route... routes)
    {
        List<Route> list = new ArrayList<>();
        Collections.addAll(list, routes);
        return new TileState(list, commands);
    }

    /** A fixed tile that nonetheless requires an accessory setting to be passed (a signal). */
    private static void signalled(componentType type, accessorySetting setting, Route... routes)
    {
        List<Route> list = new ArrayList<>();
        Collections.addAll(list, routes);

        List<TileState> states = new ArrayList<>();
        states.add(new TileState(list, commands(setting)));

        PORTS.put(type, Collections.unmodifiableList(states));
    }

    private static void fixed(componentType type, Route... routes)
    {
        List<Route> list = new ArrayList<>();
        Collections.addAll(list, routes);

        List<TileState> states = new ArrayList<>();
        states.add(new TileState(list, Collections.<AccessorySlot, accessorySetting>emptyMap()));

        PORTS.put(type, Collections.unmodifiableList(states));
    }

    private static void switched(componentType type, TileState... tileStates)
    {
        List<TileState> states = new ArrayList<>();
        Collections.addAll(states, tileStates);

        PORTS.put(type, Collections.unmodifiableList(states));
    }
}
