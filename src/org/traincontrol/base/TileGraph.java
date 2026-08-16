package org.traincontrol.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.base.TilePorts.Route;
import org.traincontrol.base.TilePorts.Side;

/**
 * Layer 1 of autonomy on the track diagram: every tile is a node, and adjacent tiles whose ports face
 * each other are connected.
 *
 * Geometry proposes, the user disposes.  TilePorts seeds the connections; the user confirms or corrects
 * them in the editor, which is why a misread tile is a visibly wrong line rather than a silently wrong
 * edge.  This class holds the seeding and the user's overrides together and answers one question for the
 * reducer: standing on this tile, having entered by this side, where can I go?
 *
 * Nothing here knows about Points, edges or the autonomy model.  It deals in tiles.
 *
 * @author Adam
 */
public class TileGraph
{
    /**
     * A tile's address: which page, and where on it.  Pages are named, so this survives the diagram being
     * reloaded as long as the tile is still there.
     */
    public static class TileKey
    {
        private final String page;
        private final int x;
        private final int y;

        public TileKey(String page, int x, int y)
        {
            this.page = page;
            this.x = x;
            this.y = y;
        }

        public String getPage()
        {
            return page;
        }

        public int getX()
        {
            return x;
        }

        public int getY()
        {
            return y;
        }

        /**
         * The key as stored in the autonomy files: "page:x,y".
         * @return
         */
        @Override
        public String toString()
        {
            return page + ":" + x + "," + y;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof TileKey)) return false;

            TileKey other = (TileKey) o;

            return x == other.x && y == other.y && page.equals(other.page);
        }

        @Override
        public int hashCode()
        {
            return (page.hashCode() * 31 + x) * 31 + y;
        }
    }

    /**
     * Which way a train may move through one route of one tile.
     *
     * A and B are the route's two sides as TilePorts reports them.  Stored per tile rather than per
     * connection between tiles, so two neighbours can never disagree about the link they share.
     */
    public static enum Direction
    {
        BOTH, TOWARD_A, TOWARD_B, NONE
    }

    /**
     * Identifies one route of one tile: which switch position it belongs to, and which route within it.
     *
     * A plain tile has state 0 only.  A switch has one entry per branch, which is the granularity the
     * user authors direction at.
     */
    public static class RouteId
    {
        private final int state;
        private final int index;

        public RouteId(int state, int index)
        {
            this.state = state;
            this.index = index;
        }

        public int getState()
        {
            return state;
        }

        public int getIndex()
        {
            return index;
        }

        @Override
        public String toString()
        {
            return state + "#" + index;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof RouteId)) return false;

            RouteId other = (RouteId) o;

            return state == other.state && index == other.index;
        }

        @Override
        public int hashCode()
        {
            return state * 31 + index;
        }
    }

    /**
     * A move out of a tile: leave by this side, having required these accessory settings.
     */
    public static class Exit
    {
        private final Side side;
        private final int state;
        private final RouteId routeId;

        Exit(Side side, int state, RouteId routeId)
        {
            this.side = side;
            this.state = state;
            this.routeId = routeId;
        }

        public Side getSide()
        {
            return side;
        }

        /**
         * The switch position this move requires.  Feed it to TilePorts.commands to get the settings.
         * @return
         */
        public int getState()
        {
            return state;
        }

        public RouteId getRouteId()
        {
            return routeId;
        }

        @Override
        public String toString()
        {
            return side + "(" + routeId + ")";
        }
    }

    /**
     * Where a move lands: the tile entered, and the side it is entered by.
     */
    public static class Landing
    {
        private final TileKey tile;
        private final Side entrySide;

        Landing(TileKey tile, Side entrySide)
        {
            this.tile = tile;
            this.entrySide = entrySide;
        }

        public TileKey getTile()
        {
            return tile;
        }

        public Side getEntrySide()
        {
            return entrySide;
        }

        @Override
        public String toString()
        {
            return tile + "@" + entrySide;
        }
    }

    /**
     * A reason the diagram cannot be used as drawn.  Errors block the build; warnings do not.
     */
    public static class Problem
    {
        private final TileKey tile;
        private final String messageKey;
        private final boolean blocking;

        Problem(TileKey tile, String messageKey, boolean blocking)
        {
            this.tile = tile;
            this.messageKey = messageKey;
            this.blocking = blocking;
        }

        public TileKey getTile()
        {
            return tile;
        }

        /**
         * A message bundle key, so the caller decides how to present it.
         * @return
         */
        public String getMessageKey()
        {
            return messageKey;
        }

        public boolean isBlocking()
        {
            return blocking;
        }

        @Override
        public String toString()
        {
            return (blocking ? "ERROR " : "WARN ") + messageKey + " at " + tile;
        }
    }

    // Message keys, matching the bundles
    public static final String ERROR_SCISSORS = "autosetup.ui.errorScissorsNotSupported";
    public static final String ERROR_PORTAL_UNPAIRED = "autosetup.ui.errorLinkNotMutuallyPaired";
    public static final String ERROR_PORTAL_EXCLUDED = "autosetup.ui.errorPortalTargetsExcludedPage";
    public static final String WARN_TURNTABLE = "autosetup.ui.warnTurntableNotRoutable";
    public static final String WARN_PERMANENT_TURNOUT = "autosetup.ui.warnPermanentTurnout";

    /**
     * A switch or signal that must be commanded to be passed, drawn without an address behind it.
     *
     * Blocking, by ruling: a diagram used for autonomy should not contain unmapped switches.  Routing
     * over one would mean trusting it to already be lying the right way, which is the danger
     * CUSTOM_PERM_* exists to declare - and here nobody declared it.
     */
    public static final String ERROR_NO_ADDRESS = "autosetup.ui.errorTileHasNoAddress";

    private final Map<TileKey, LayoutDiagramComponent> tiles = new LinkedHashMap<>();
    private final Map<TileKey, TileKey> portals = new HashMap<>();
    private final Map<TileKey, Map<RouteId, Direction>> directions = new HashMap<>();
    private final Set<String> pages = new LinkedHashSet<>();
    private final List<Problem> problems = new ArrayList<>();

    /**
     * Builds the tile graph from a set of diagram pages.
     *
     * @param diagrams every page of the layout
     * @param excludedPages pages left out of autonomy entirely - a page duplicated for display would
     *        otherwise mint a second Point for every sensor it redraws
     */
    public TileGraph(List<LayoutDiagram> diagrams, Set<String> excludedPages)
    {
        for (LayoutDiagram diagram : diagrams)
        {
            if (excludedPages != null && excludedPages.contains(diagram.getName()))
            {
                continue;
            }

            pages.add(diagram.getName());

            for (LayoutDiagramComponent component : diagram.getAll())
            {
                TileKey key = new TileKey(diagram.getName(), component.getX(), component.getY());

                tiles.put(key, component);

                componentType type = component.getType();

                // Scissors are a drawing convention - two tiles depicting one double slip - so their
                // topology cannot be expressed per tile.  Refuse the diagram rather than ignore the tile:
                // ignoring it leaves a hole that walks quietly route around.
                if (TilePorts.isDisqualified(type))
                {
                    problems.add(new Problem(key, ERROR_SCISSORS, true));
                }
                else if (TilePorts.isTerminator(type))
                {
                    problems.add(new Problem(key, WARN_TURNTABLE, false));
                }
                else if (isPermanentTurnout(type))
                {
                    problems.add(new Problem(key, WARN_PERMANENT_TURNOUT, false));
                }
                else if (missesAnAddress(component))
                {
                    // Scanned here rather than while walking, so an unaddressed switch on a siding no
                    // route happens to reach is still reported.  A blocking error that depends on being
                    // stumbled across is not a guarantee.
                    problems.add(new Problem(key, ERROR_NO_ADDRESS, true));
                }
            }
        }
    }

    /**
     * Pairs two portal tiles - tunnels, or page links.  Pairing is authored, never inferred: an unpaired
     * portal simply does not connect, which is the safe default when autonomy is added to a diagram
     * somebody drew years ago.
     *
     * Pairing is mutual and exclusive.  A half-pairing is reported rather than treated as a one-way jump.
     *
     * @param a
     * @param b
     */
    public void pairPortals(TileKey a, TileKey b)
    {
        portals.put(a, b);
        portals.put(b, a);
    }

    /**
     * Records the direction the user authored for one route of one tile.
     * @param tile
     * @param routeId
     * @param direction
     */
    public void setDirection(TileKey tile, RouteId routeId, Direction direction)
    {
        Map<RouteId, Direction> perTile = directions.get(tile);

        if (perTile == null)
        {
            perTile = new HashMap<>();
            directions.put(tile, perTile);
        }

        if (direction == null || direction == defaultDirection(tile, routeId))
        {
            perTile.remove(routeId);
        }
        else
        {
            perTile.put(routeId, direction);
        }
    }

    /**
     * The direction in force for one route: what the user set, or the default.
     * @param tile
     * @param routeId
     * @return
     */
    public Direction getDirection(TileKey tile, RouteId routeId)
    {
        Map<RouteId, Direction> perTile = directions.get(tile);

        if (perTile != null && perTile.containsKey(routeId))
        {
            return perTile.get(routeId);
        }

        return defaultDirection(tile, routeId);
    }

    /**
     * The direction a route has before anyone touches it.
     *
     * Plain track runs both ways.  A switch runs base to forks - out of the toe only - so every switch on
     * the diagram has a deterministic reading without the user setting anything, and opening the trailing
     * direction is a deliberate act.  A double slip has no single toe, so it defaults to bidirectional.
     *
     * @param tile
     * @param routeId
     * @return
     */
    public Direction defaultDirection(TileKey tile, RouteId routeId)
    {
        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return Direction.BOTH;

        Route route = routeOf(component, routeId);

        if (route == null) return Direction.BOTH;

        // A route the hardware already restricts sets no default of its own.  A defective turnout is the
        // case: base to forks would say "out of the toe only" while the blades say "into the toe only",
        // and ANDing those leaves the tile impassable in both directions.  Being unable to choose a fork
        // is exactly why driving out of the base is not a sensible default there.
        if (route.getDirectedToward() != null) return Direction.BOTH;

        Side toe = TilePorts.deriveToe(component.getType(), component.getOrientation());

        if (toe == null) return Direction.BOTH;

        // base to forks: travel away from the toe
        if (route.getA() == toe) return Direction.TOWARD_B;
        if (route.getB() == toe) return Direction.TOWARD_A;

        return Direction.BOTH;
    }

    /**
     * The moves available from a tile, having entered it by the given side.
     *
     * Two constraints apply and both must allow the move: the hardware's own - a defective turnout can
     * only be trailed through - and the user's direction for that route.  They AND, so no user setting
     * can re-open a facing move through a switch that cannot be thrown.
     *
     * @param tile
     * @param entrySide
     * @return
     */
    public List<Exit> exits(TileKey tile, Side entrySide)
    {
        List<Exit> out = new ArrayList<>();

        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return out;

        componentType type = component.getType();

        if (TilePorts.isDisqualified(type) || TilePorts.isTerminator(type)) return out;

        int orientation = component.getOrientation();

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            List<Route> routes = TilePorts.ports(type, orientation, state);

            for (int index = 0; index < routes.size(); index++)
            {
                Route route = routes.get(index);

                // a stub has one side and no through path
                if (route.getA() == route.getB()) continue;

                if (!route.touches(entrySide)) continue;

                // the hardware's own restriction
                if (!route.isTraversableFrom(entrySide)) continue;

                RouteId routeId = new RouteId(state, index);

                if (!directionAllows(getDirection(tile, routeId), route, entrySide)) continue;

                out.add(new Exit(route.other(entrySide), state, routeId));
            }
        }

        return out;
    }

    /**
     * Where leaving a tile by the given side lands.
     *
     * Ordinarily that is the adjacent tile, entered by the facing side, and only if that tile has a port
     * there - track that stops at a blank square stops.  A portal tile's stub side instead lands on its
     * paired partner, which may be on another page.
     *
     * @param tile
     * @param exitSide
     * @return where the move lands, or null if nothing is there
     */
    public Landing landing(TileKey tile, Side exitSide)
    {
        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return null;

        // A portal's visible side behaves like ordinary track; its second port is the pairing
        if (TilePorts.hasPortal(component.getType()) && isStubSide(component, exitSide))
        {
            TileKey partner = portals.get(tile);

            if (partner == null) return null;

            LayoutDiagramComponent partnerComponent = tiles.get(partner);

            if (partnerComponent == null) return null;

            Side partnerSide = stubSide(partnerComponent);

            return partnerSide == null ? null : new Landing(partner, partnerSide);
        }

        TileKey neighbourKey = neighbour(tile, exitSide);
        LayoutDiagramComponent neighbourComponent = tiles.get(neighbourKey);

        if (neighbourComponent == null) return null;

        Side entrySide = exitSide.opposite();

        // the neighbour must actually have track on the facing side
        if (!hasPortOn(neighbourComponent, entrySide)) return null;

        return new Landing(neighbourKey, entrySide);
    }

    /**
     * Validates that every portal pairing is mutual, exclusive, and does not point at an excluded page.
     * Half-pairings are errors rather than silently one-way jumps.
     * @return problems found, added to this graph's list as well
     */
    public List<Problem> validatePortals()
    {
        List<Problem> found = new ArrayList<>();

        for (Map.Entry<TileKey, TileKey> entry : portals.entrySet())
        {
            TileKey from = entry.getKey();
            TileKey to = entry.getValue();

            if (!tiles.containsKey(from)) continue;

            if (!tiles.containsKey(to))
            {
                // the target is gone, or lives on a page excluded from autonomy
                found.add(new Problem(from,
                    pages.contains(to.getPage()) ? ERROR_PORTAL_UNPAIRED : ERROR_PORTAL_EXCLUDED, true));
                continue;
            }

            if (!from.equals(portals.get(to)))
            {
                found.add(new Problem(from, ERROR_PORTAL_UNPAIRED, true));
            }
        }

        problems.addAll(found);

        return found;
    }

    /**
     * @return every tile in the graph, excluded pages already left out
     */
    public Map<TileKey, LayoutDiagramComponent> getTiles()
    {
        return Collections.unmodifiableMap(tiles);
    }

    /**
     * Every feedback tile.  These become Points, one each, without the user asking - a feedback signal
     * has no other way into the autonomy model.
     * @return
     */
    public List<TileKey> getFeedbackTiles()
    {
        List<TileKey> out = new ArrayList<>();

        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (entry.getValue().isFeedback())
            {
                out.add(entry.getKey());
            }
        }

        return out;
    }

    /**
     * @return the pages this graph covers
     */
    public Set<String> getPages()
    {
        return Collections.unmodifiableSet(pages);
    }

    /**
     * @return everything wrong with the diagram as drawn; blocking entries must stop the build
     */
    public List<Problem> getProblems()
    {
        return Collections.unmodifiableList(problems);
    }

    /**
     * @return true if any problem blocks the build
     */
    public boolean hasBlockingProblems()
    {
        for (Problem p : problems)
        {
            if (p.isBlocking()) return true;
        }

        return false;
    }

    /**
     * The routes of a tile, paired with their identifiers, for the UI to list and the user to author.
     * @param tile
     * @return
     */
    public Map<RouteId, Route> getRoutes(TileKey tile)
    {
        Map<RouteId, Route> out = new LinkedHashMap<>();

        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return out;

        componentType type = component.getType();

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            List<Route> routes = TilePorts.ports(type, component.getOrientation(), state);

            for (int index = 0; index < routes.size(); index++)
            {
                out.put(new RouteId(state, index), routes.get(index));
            }
        }

        return out;
    }

    // --- internals --------------------------------------------------------------------------------

    /**
     * Whether any position this tile can take requires an accessory it does not have.
     *
     * A three-way needs both of its addresses; a signal needs the one that lets it go green.  A tile that
     * commands nothing - plain track, a crossing, a defective turnout - can never miss an address.
     */
    private static boolean missesAnAddress(LayoutDiagramComponent component)
    {
        componentType type = component.getType();

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            for (TilePorts.AccessorySlot slot : TilePorts.commands(type, state).keySet())
            {
                Accessory accessory = slot == TilePorts.AccessorySlot.PRIMARY
                    ? component.getAccessory() : component.getAccessory2();

                if (accessory == null) return true;
            }
        }

        return false;
    }

    private static boolean isPermanentTurnout(componentType type)
    {
        return type == componentType.CUSTOM_PERM_LEFT
            || type == componentType.CUSTOM_PERM_RIGHT
            || type == componentType.CUSTOM_PERM_Y
            || type == componentType.CUSTOM_PERM_THREEWAY
            || type == componentType.CUSTOM_PERM_SCISSORS;
    }

    private Route routeOf(LayoutDiagramComponent component, RouteId routeId)
    {
        List<Route> routes = TilePorts.ports(
            component.getType(), component.getOrientation(), routeId.getState());

        if (routeId.getIndex() < 0 || routeId.getIndex() >= routes.size()) return null;

        return routes.get(routeId.getIndex());
    }

    private static boolean directionAllows(Direction direction, Route route, Side entrySide)
    {
        if (direction == Direction.NONE) return false;
        if (direction == Direction.BOTH) return true;

        Side allowedToward = direction == Direction.TOWARD_A ? route.getA() : route.getB();

        return route.other(entrySide) == allowedToward;
    }

    private static boolean hasPortOn(LayoutDiagramComponent component, Side side)
    {
        componentType type = component.getType();

        if (TilePorts.isDisqualified(type) || TilePorts.isTerminator(type)) return false;

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            for (Route r : TilePorts.ports(type, component.getOrientation(), state))
            {
                if (r.touches(side)) return true;
            }
        }

        return false;
    }

    private static boolean isStubSide(LayoutDiagramComponent component, Side side)
    {
        return stubSide(component) == side;
    }

    /**
     * The single open side of a portal or dead-end tile.
     */
    private static Side stubSide(LayoutDiagramComponent component)
    {
        for (Route r : TilePorts.ports(component.getType(), component.getOrientation(), 0))
        {
            if (r.getA() == r.getB()) return r.getA();
        }

        return null;
    }

    private static TileKey neighbour(TileKey tile, Side side)
    {
        switch (side)
        {
            case N: return new TileKey(tile.getPage(), tile.getX(), tile.getY() - 1);
            case S: return new TileKey(tile.getPage(), tile.getX(), tile.getY() + 1);
            case E: return new TileKey(tile.getPage(), tile.getX() + 1, tile.getY());
            case W: return new TileKey(tile.getPage(), tile.getX() - 1, tile.getY());
            default: return null;
        }
    }
}
