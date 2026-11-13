package org.traincontrol.automationui;

import org.traincontrol.base.Accessory;
import org.traincontrol.base.LayoutDiagram;
import org.traincontrol.base.LayoutDiagramComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.traincontrol.base.LayoutDiagramComponent.componentType;
import org.traincontrol.automationui.TilePorts.Route;
import org.traincontrol.automationui.TilePorts.Side;

/**
 * Layer 1 of autonomy on the track diagram: every tile is a node, and adjacent tiles whose ports face each other are
 * connected.  Geometry proposes, the user disposes: TilePorts seeds the connections and the user's overrides correct
 * them, so a misread tile is a visibly wrong line rather than a silently wrong edge.  Answers the reducer's one
 * question: standing on this tile, having entered by this side, where can I go?  Nothing here knows about Points, edges
 * or the autonomy model.
 *
 * @author Adam
 */
public class TileGraph
{
    /**
     * Something a setup collection is keyed by: always a square, sometimes a square plus more.  Keys are typed, never a
     * string (erasure would make {@code Map<String, T>} and {@code Map<TileKey, T>} the same signature).  The self type
     * lets the move, rename and drop helpers be written once over both kinds.
     */
    public interface SquareKeyed<K extends SquareKeyed<K>>
    {
        /** The square this key is on, never null. */
        TileKey square();

        /** A new key, identical apart from being on the given square. */
        K withSquare(TileKey square);
    }

    /** A tile's address: which page, and where on it.  Pages are named, so this survives a diagram reload. */
    public static class TileKey implements SquareKeyed<TileKey>
    {
        private final String page;
        private final int x;
        private final int y;

        @Override
        public TileKey square()
        {
            return this;
        }

        @Override
        public TileKey withSquare(TileKey square)
        {
            return square;
        }

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

        /** The square beside this one across a side, on the same page (north is the smaller y); null for no side. */
        public TileKey beside(Side side)
        {
            if (side == null) return null;

            switch (side)
            {
                case N: return new TileKey(page, x, y - 1);
                case S: return new TileKey(page, x, y + 1);
                case E: return new TileKey(page, x + 1, y);
                default: return new TileKey(page, x - 1, y);
            }
        }

        /** The key as stored in the autonomy files: "page:x,y". */
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
     * Which side of one square faces another, or null when they are not neighbors (a path can jump pages through a
     * link, and that jump has no side).  Shared so the editor's tested path and the running diagram lay a line over one
     * grid.
     */
    public static Side gridSideTowards(TileKey from, TileKey to)
    {
        if (from == null || to == null) return null;

        if (!from.getPage().equals(to.getPage())) return null;

        if (to.getY() == from.getY())
        {
            if (to.getX() == from.getX() + 1) return Side.E;
            if (to.getX() == from.getX() - 1) return Side.W;
        }

        if (to.getX() == from.getX())
        {
            if (to.getY() == from.getY() + 1) return Side.S;
            if (to.getY() == from.getY() - 1) return Side.N;
        }

        return null;
    }

    /**
     * Which way a train may move through one route of one tile.  Stored per tile, so neighbors never disagree about a
     * link.
     */
    public static enum Direction
    {
        BOTH, TOWARD_A, TOWARD_B, NONE
    }

    /** Identifies one route of one tile: its switch position (state) and which route within it. */
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
     * A square together with one route across it: what a recorded direction belongs to; its string form goes on disk.
     */
    public static class DirectionKey implements SquareKeyed<DirectionKey>
    {
        private final TileKey tile;
        private final RouteId routeId;

        public DirectionKey(TileKey tile, RouteId routeId)
        {
            this.tile = tile;
            this.routeId = routeId;
        }

        public RouteId getRouteId()
        {
            return routeId;
        }

        @Override
        public TileKey square()
        {
            return tile;
        }

        @Override
        public DirectionKey withSquare(TileKey square)
        {
            return new DirectionKey(square, routeId);
        }

        /**
         * The key as stored in the autonomy files: "page:x,y#state,index".  The comma is the stored separator; changing
         * it would orphan every earlier setup.
         */
        @Override
        public String toString()
        {
            return tile.toString() + "#" + routeId.getState() + "," + routeId.getIndex();
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof DirectionKey)) return false;

            DirectionKey other = (DirectionKey) o;

            return tile.equals(other.tile) && routeId.equals(other.routeId);
        }

        @Override
        public int hashCode()
        {
            return tile.hashCode() * 31 + routeId.hashCode();
        }
    }

    /** A move out of a tile: leave by this side, having required these accessory settings. */
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

    /** Where a move lands: the tile entered, and the side it is entered by. */
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

    /** A reason the diagram cannot be used as drawn.  Errors block the build; warnings do not. */
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

    public static final String ERROR_SCISSORS = "autosetup.ui.errorScissorsNotSupported";
    public static final String ERROR_PORTAL_UNPAIRED = "autosetup.ui.errorLinkNotMutuallyPaired";
    public static final String WARN_PORTAL_NEVER_PAIRED = "autosetup.ui.warnLinkNeverPaired";
    public static final String WARN_PORTAL_UNREACHABLE = "autosetup.ui.warnLinkNeverPairedUnreachable";

    /**
     * A link pointing at a page left out of autonomy (OB-150): the remedy is to include the page or switch the link
     * off.
     */
    public static final String WARN_PORTAL_EXCLUDED_PAGE = "autosetup.ui.warnLinkToExcludedPage";

    /** Links the user told autonomy to leave alone: one that belongs to the drawing must not block the build. */
    private final Set<TileKey> disabledPortals = new LinkedHashSet<>();

    /** Tells autonomy to ignore this link entirely. */
    public void disablePortal(TileKey tile)
    {
        if (tile != null) disabledPortals.add(tile);
    }

    public boolean isPortalDisabled(TileKey tile)
    {
        return portalClosed(tile);
    }

    /**
     * Whether this doorway is shut, asked at EITHER end: a pair of links is one doorway.  The writer makes both ends
     * match (OB-041), but older setups can hold one-ended disables and there is no migration.
     */
    private boolean portalClosed(TileKey tile)
    {
        if (tile == null) return false;

        if (disabledPortals.contains(tile)) return true;

        TileKey partner = portals.get(tile);

        return partner != null && disabledPortals.contains(partner);
    }
    public static final String ERROR_PORTAL_EXCLUDED = "autosetup.ui.errorPortalTargetsExcludedPage";
    public static final String WARN_TURNTABLE = "autosetup.ui.warnTurntableNotRoutable";
    public static final String WARN_PERMANENT_TURNOUT = "autosetup.ui.warnPermanentTurnout";

    /**
     * A switch or signal that must be commanded to be passed, drawn without an address.  Blocking by ruling: routing
     * over one would mean trusting it to lie the right way already (what CUSTOM_PERM_* declares).  Only checked when
     * autonomy is built, not on load; a page carrying such tiles deliberately can be excluded.
     */
    public static final String ERROR_NO_ADDRESS = "autosetup.ui.errorTileHasNoAddress";

    /**
     * Two route buttons side by side with real track into the pair (OB-160): they splice two lines through no rails.
     */
    public static final String ERROR_ADJACENT_ROUTE_TILES = "autosetup.ui.errorAdjacentRouteTiles";

    /** Track into a route button from three sides, one silently dropped (OB-160); four is a crossing, not reported. */
    public static final String ERROR_ROUTE_TILE_THREE_WAY = "autosetup.ui.errorRouteTileThreeWay";

    /**
     * The route id a link is traversed under: (0,-1), so it never collides with the link's own stub route (0,0).  A
     * link carries no direction (testALinkIsNotOfferedADirection pins that).
     */
    private static final RouteId PORTAL_ROUTE = new RouteId(0, -1);

    private final Map<TileKey, LayoutDiagramComponent> tiles = new LinkedHashMap<>();
    private final Map<TileKey, TileKey> portals = new HashMap<>();

    /** Each square of a two-square crossing, to the other (OB-320) - see crossingPartner. */
    private final Map<TileKey, TileKey> crossingPartners = new HashMap<>();
    private final Map<TileKey, Map<RouteId, Direction>> directions = new HashMap<>();
    private final Set<String> pages = new LinkedHashSet<>();
    private final List<Problem> problems = new ArrayList<>();

    /**
     * Every page in the layout in window order (sorted by name), excluded ones included: a link's destination indexes
     * it.
     */
    private final List<String> allPages = new ArrayList<>();

    /**
     * Builds the tile graph from diagram pages; an excluded page (say a duplicate for display) would mint a second
     * Point per sensor.
     */
    public TileGraph(List<LayoutDiagram> diagrams, Set<String> excludedPages)
    {
        for (LayoutDiagram diagram : diagrams)
        {
            allPages.add(diagram.getName());

            if (excludedPages != null && excludedPages.contains(diagram.getName())) continue;

            pages.add(diagram.getName());

            for (LayoutDiagramComponent component : diagram.getAll())
            {
                TileKey key = new TileKey(diagram.getName(), component.getX(), component.getY());

                tiles.put(key, component);

                componentType type = component.getType();

                // Scissors have no per-tile topology: refuse the diagram, since ignoring the tile leaves a hole.
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
                    // Scanned here, not while walking, so an unaddressed switch no route reaches is still reported.
                    problems.add(new Problem(key, ERROR_NO_ADDRESS, true));
                }
            }
        }

        checkTransparentTiles();

        pairTheCrossings();
    }

    /**
     * Pairs two portal tiles.  Authored, never inferred; mutual and exclusive; a half-pairing is reported, not a
     * one-way jump.
     */
    public void pairPortals(TileKey a, TileKey b)
    {
        portals.put(a, b);
        portals.put(b, a);
    }

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

    public Direction getDirection(TileKey tile, RouteId routeId)
    {
        Map<RouteId, Direction> perTile = directions.get(tile);

        if (perTile != null && perTile.containsKey(routeId)) return perTile.get(routeId);

        return defaultDirection(tile, routeId);
    }

    /**
     * The direction a route has before anyone touches it: plain track both ways; a switch base to forks (out of the toe
     * only); a double slip has no single toe and defaults to bidirectional.
     */
    public Direction defaultDirection(TileKey tile, RouteId routeId)
    {
        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return Direction.BOTH;

        if (crossingPartners.containsKey(tile)) return Direction.BOTH;

        Route route = routeOf(component, routeId);

        if (route == null) return Direction.BOTH;

        // A route the hardware already restricts sets no default: ANDed with the blades' it would shut the tile.
        if (route.getDirectedToward() != null) return Direction.BOTH;

        Side toe = TilePorts.deriveToe(component.getType(), component.getOrientation());

        if (toe == null) return Direction.BOTH;

        if (route.getA() == toe) return Direction.TOWARD_B;
        if (route.getB() == toe) return Direction.TOWARD_A;

        return Direction.BOTH;
    }

    /**
     * The moves available from a tile entered by the given side.  The hardware's constraint and the user's direction
     * must both allow the move, so no user setting re-opens a facing move through a switch that cannot be thrown.
     */
    public List<Exit> exits(TileKey tile, Side entrySide)
    {
        List<Exit> out = new ArrayList<>();

        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return out;

        componentType type = component.getType();

        if (TilePorts.isDisqualified(type) || TilePorts.isTerminator(type)) return out;

        int orientation = component.getOrientation();

        // A portal tile is addressed as a null side (arriving by null = through the portal), else a link is a hole.
        if (TilePorts.hasPortal(type))
        {
            Side stub = stubSide(component);

            if (stub != null && portals.containsKey(tile) && !portalClosed(tile))
            {
                if (entrySide == stub)
                {
                    out.add(new Exit(null, 0, PORTAL_ROUTE));
                }
                else if (entrySide == null)
                {
                    out.add(new Exit(stub, 0, PORTAL_ROUTE));
                }
            }

            return out;
        }

        if (TilePorts.isTransparent(type))
        {
            for (Route route : transparentRoutes(tile))
            {
                if (!route.touches(entrySide)) continue;

                RouteId routeId = transparentRouteId(route);

                if (!directionAllows(getDirection(tile, routeId), route, entrySide)) continue;

                out.add(new Exit(route.other(entrySide), 0, routeId));
            }

            return out;
        }

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            List<Route> routes = routesOn(tile, component, state);

            for (int index = 0; index < routes.size(); index++)
            {
                Route route = routes.get(index);

                if (route.getA() == route.getB()) continue;

                if (!route.touches(entrySide)) continue;

                if (!route.isTraversableFrom(entrySide)) continue;

                RouteId routeId = new RouteId(state, index);

                if (!directionAllows(getDirection(tile, routeId), route, entrySide)) continue;

                out.add(new Exit(route.other(entrySide), state, routeId));
            }
        }

        return out;
    }

    /**
     * The moves out of a square on the track a train arrived along (OB-320): as `exits(tile, entrySide)` except on a
     * two-square crossing entered where its squares meet.  `previous` and `previousRoute` (null if none) say which
     * track.
     */
    public List<Exit> exits(TileKey tile, Side entrySide, TileKey previous, RouteId previousRoute)
    {
        List<Exit> all = exits(tile, entrySide);

        Side leg = legAcross(tile, entrySide, previous, previousRoute);

        if (leg == null) return all;

        List<Exit> out = new ArrayList<>();

        for (Exit exit : all)
        {
            if (exit.getSide() == leg) out.add(exit);
        }

        return out;
    }

    /**
     * The leg a two-square crossing's track leaves a square by, for a train from the other square (OB-320); else null.
     */
    public Side legAcross(TileKey tile, Side entrySide, TileKey previous, RouteId previousRoute)
    {
        TileKey partner = crossingPartners.get(tile);

        if (partner == null || previousRoute == null || !partner.equals(previous)) return null;

        Side toe = sideTowardNeighbour(tile, partner);

        if (toe == null || entrySide != toe) return null;

        Route there = getRoutes(partner).get(previousRoute);

        Side legThere = there == null ? null : there.other(toe.opposite());

        return legThere == null || legThere == toe.opposite() ? null : legThere.opposite();
    }

    /**
     * The other square of a two-square crossing, or null (OB-320): two permanent turnouts drawn toe to toe, as the two
     * halves of an X.  One without a partner stays a turnout (Adam: "those without another adjacent to form a crossing
     * are treated as switches only crossable in one direction by autonomy").
     */
    public TileKey crossingPartner(TileKey tile)
    {
        return tile == null ? null : crossingPartners.get(tile);
    }

    /** The other square's road for the same track of a two-square crossing, or null (OB-320). */
    public RouteId crossingRouteOnPartner(TileKey tile, RouteId routeId)
    {
        TileKey partner = crossingPartner(tile);

        if (partner == null || routeId == null) return null;

        Route here = getRoutes(tile).get(routeId);

        Side toe = sideTowardNeighbour(tile, partner);

        Side leg = here == null || toe == null ? null : here.other(toe);

        if (leg == null || leg == toe) return null;

        for (Map.Entry<RouteId, Route> there : getRoutes(partner).entrySet())
        {
            if (there.getValue().touches(leg.opposite())) return there.getKey();
        }

        return null;
    }

    /**
     * Makes the two halves of each two-square crossing agree where an older setup left them apart (OB-320); graph only.
     */
    public void settleCrossingHalves()
    {
        for (Map.Entry<TileKey, TileKey> pair : crossingPartners.entrySet())
        {
            TileKey tile = pair.getKey();
            TileKey partner = pair.getValue();

            for (RouteId routeId : new java.util.ArrayList<>(getRoutes(tile).keySet()))
            {
                RouteId across = crossingRouteOnPartner(tile, routeId);

                if (across == null) continue;

                Direction here = getDirection(tile, routeId);
                Direction there = getDirection(partner, across);

                if (there == crossingDirectionOnPartner(tile, routeId, here)) continue;

                Direction settled = closedAsATurnout(tile, routeId, here) || closedAsATurnout(partner, across, there)
                    ? Direction.NONE : Direction.BOTH;

                setDirection(tile, routeId, settled);
                setDirection(partner, across, settled);
            }
        }
    }

    /** Whether a direction on half of a crossing closed the road when the square was a permanent turnout. */
    private boolean closedAsATurnout(TileKey tile, RouteId routeId, Direction direction)
    {
        if (direction == Direction.NONE) return true;

        if (direction == null || direction == Direction.BOTH) return false;

        Route road = getRoutes(tile).get(routeId);

        Side toe = sideTowardNeighbour(tile, crossingPartner(tile));

        if (road == null || toe == null) return false;

        return (direction == Direction.TOWARD_A ? road.getA() : road.getB()) != toe;
    }

    /**
     * A direction on one half of a two-square crossing's track as the other half reads it (OB-320); null when no
     * crossing.
     */
    public Direction crossingDirectionOnPartner(TileKey tile, RouteId routeId, Direction direction)
    {
        RouteId across = crossingRouteOnPartner(tile, routeId);

        if (across == null) return null;

        if (direction == null || direction == Direction.BOTH || direction == Direction.NONE) return direction;

        TileKey partner = crossingPartner(tile);

        Route here = getRoutes(tile).get(routeId);
        Route there = getRoutes(partner).get(across);

        Side toe = sideTowardNeighbour(tile, partner);

        Side toward = direction == Direction.TOWARD_A ? here.getA() : here.getB();

        // toward this square's toe is out by the other's leg; toward its leg is off the other by its toe
        Side thereToward = toward == toe ? there.other(toe.opposite()) : toe.opposite();

        return thereToward == there.getA() ? Direction.TOWARD_A : Direction.TOWARD_B;
    }

    /**
     * Finds the two-square crossings: two permanent turnouts whose toes face each other and whose legs face opposite
     * legs (OB-320).
     */
    private void pairTheCrossings()
    {
        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            Side toe = crossingToe(entry.getValue());

            if (toe == null) continue;

            TileKey other = entry.getKey().beside(toe);

            LayoutDiagramComponent there = tiles.get(other);

            if (crossingToe(there) != toe.opposite()) continue;

            // Every leg must face the other's opposite leg; a left with a right has both legs on one side, so both stay
            // turnouts.
            Set<Side> across = new LinkedHashSet<>();

            for (Side leg : legsOf(entry.getValue(), toe)) across.add(leg.opposite());

            if (across.equals(legsOf(there, toe.opposite()))) crossingPartners.put(entry.getKey(), other);
        }

        // A crossing is not a turnout to warn about.
        problems.removeIf(problem -> crossingPartners.containsKey(problem.getTile())
            && WARN_PERMANENT_TURNOUT.equals(problem.getMessageKey()));
    }

    /** The toe of a permanent Y, left or right - a turnout that can be half of a two-square crossing - or null. */
    private static Side crossingToe(LayoutDiagramComponent component)
    {
        if (component == null) return null;

        componentType type = component.getType();

        if (type != componentType.CUSTOM_PERM_Y && type != componentType.CUSTOM_PERM_LEFT
            && type != componentType.CUSTOM_PERM_RIGHT) return null;

        return TilePorts.deriveToe(type, component.getOrientation());
    }

    /** The sides a turnout's roads leave its toe by. */
    private static Set<Side> legsOf(LayoutDiagramComponent component, Side toe)
    {
        Set<Side> legs = new LinkedHashSet<>();

        for (Route route : TilePorts.ports(component.getType(), component.getOrientation(), 0))
        {
            Side leg = route.other(toe);

            if (leg != null && leg != toe) legs.add(leg);
        }

        return legs;
    }

    /** A square's roads in one position; on a two-square crossing they run both ways (OB-320). */
    private List<Route> routesOn(TileKey tile, LayoutDiagramComponent component, int state)
    {
        List<Route> routes = TilePorts.ports(component.getType(), component.getOrientation(), state);

        if (!crossingPartners.containsKey(tile)) return routes;

        List<Route> open = new ArrayList<>();

        for (Route route : routes) open.add(new Route(route.getA(), route.getB(), null));

        return open;
    }

    /**
     * Where leaving a tile by a side lands: the adjacent tile entered by the facing side if it has a port there, or for
     * a portal's stub its paired partner, possibly on another page.  Null if nothing is there.
     */
    public Landing landing(TileKey tile, Side exitSide)
    {
        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return null;

        if (exitSide == null)
        {
            TileKey partner = portals.get(tile);

            if (partner == null || !tiles.containsKey(partner)) return null;

            return new Landing(partner, null);
        }

        TileKey neighbourKey = tile.beside(exitSide);
        LayoutDiagramComponent neighbourComponent = tiles.get(neighbourKey);

        if (neighbourComponent == null) return null;

        Side entrySide = exitSide.opposite();

        if (!hasPortOn(neighbourComponent, entrySide)) return null;

        return new Landing(neighbourKey, entrySide);
    }

    /**
     * Validates that every portal pairing is mutual, exclusive and not at an excluded page; adds the problems to this
     * graph's list.
     */
    public List<Problem> validatePortals()
    {
        List<Problem> found = new ArrayList<>();

        for (Map.Entry<TileKey, TileKey> entry : portals.entrySet())
        {
            TileKey from = entry.getKey();
            TileKey to = entry.getValue();

            if (!tiles.containsKey(from)) continue;

            if (portalClosed(from)) continue;

            if (!tiles.containsKey(to))
            {
                found.add(new Problem(from,
                    pages.contains(to.getPage()) ? ERROR_PORTAL_UNPAIRED : ERROR_PORTAL_EXCLUDED, true));
                continue;
            }

            // Both ends still have to BE links: a far end redrawn as plain track would make the walk jump into a dead
            // square.
            if (!TilePorts.hasPortal(tiles.get(from).getType())
                || !TilePorts.hasPortal(tiles.get(to).getType()))
            {
                found.add(new Problem(from, ERROR_PORTAL_UNPAIRED, true));
                continue;
            }

            if (!from.equals(portals.get(to))) found.add(new Problem(from, ERROR_PORTAL_UNPAIRED, true));
        }

        // A link nobody has paired never reaches the loop above, which walks pairings.
        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (!TilePorts.hasPortal(entry.getValue().getType())) continue;

            if (portals.containsKey(entry.getKey())) continue;

            if (portalClosed(entry.getKey())) continue;

            // An unpaired link leads nowhere; whether that blocks depends on whether a train could arrive (track joined
            // to it).
            Side stub = stubSide(entry.getValue());

            boolean reachable = stub != null && landing(entry.getKey(), stub) != null;

            // An arrow to an excluded page cannot be paired; the only remedy is to disable it.
            boolean unpairable = leadsOutsideAutonomy(entry.getValue());

            // A link track runs into is blocking (OB-150).  Adam: "there should also be an error if there exist ANY
            // active (not excluded/disabled) links not linked to anything."  An arrow with no track joined stays a
            // warning (a Central Station import carries page-jump arrows nobody has paired).
            String key;

            if (!reachable) key = WARN_PORTAL_UNREACHABLE;
            else if (unpairable) key = WARN_PORTAL_EXCLUDED_PAGE;
            else key = WARN_PORTAL_NEVER_PAIRED;

            found.add(new Problem(entry.getKey(), key, reachable && !unpairable));
        }

        for (Problem problem : found)
        {
            boolean already = false;

            for (Problem seen : problems)
            {
                if (seen.getTile() != null && seen.getTile().equals(problem.getTile())
                    && seen.getMessageKey().equals(problem.getMessageKey())) already = true;
            }

            if (!already) problems.add(problem);
        }

        return found;
    }

    /** Every tile in the graph, excluded pages already left out. */
    public Map<TileKey, LayoutDiagramComponent> getTiles()
    {
        return Collections.unmodifiableMap(tiles);
    }

    /** Every feedback tile: these become Points, one each, without the user asking (no other way into the model). */
    public List<TileKey> getFeedbackTiles()
    {
        List<TileKey> out = new ArrayList<>();

        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (entry.getValue().isFeedback()) out.add(entry.getKey());
        }

        return out;
    }

    /** The pages this graph covers. */
    public Set<String> getPages()
    {
        return Collections.unmodifiableSet(pages);
    }


    /** Whether a link points at a page not part of autonomy (OB-150), or at no page (a hand-edited file). */
    private boolean leadsOutsideAutonomy(LayoutDiagramComponent link)
    {
        if (link == null) return false;

        if (link.getLinkPageId() != null) return link.getLinkPageName() == null || !pages.contains(link.getLinkPageName());

        int address = link.getRawAddress();

        if (address < 0 || address >= allPages.size()) return true;

        return !pages.contains(allPages.get(address));
    }
    /** Everything wrong with the diagram as drawn; blocking entries must stop the build. */
    public List<Problem> getProblems()
    {
        return Collections.unmodifiableList(problems);
    }

    /** True if any problem blocks the build. */
    public boolean hasBlockingProblems()
    {
        for (Problem p : problems)
        {
            if (p.isBlocking()) return true;
        }

        return false;
    }

    /** The routes of a tile, paired with their identifiers, for the UI to list and the user to author. */
    public Map<RouteId, Route> getRoutes(TileKey tile)
    {
        Map<RouteId, Route> out = new LinkedHashMap<>();

        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return out;

        componentType type = component.getType();

        if (TilePorts.isTransparent(type))
        {
            for (Route route : transparentRoutes(tile)) out.put(transparentRouteId(route), route);

            return out;
        }

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            List<Route> routes = routesOn(tile, component, state);

            for (int index = 0; index < routes.size(); index++) out.put(new RouteId(state, index), routes.get(index));
        }

        return out;
    }

    /**
     * Whether any position this tile can take requires an accessory it does not have (a three-way needs both
     * addresses).
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

    /**
     * What a transparent tile (a route button) conducts, read off its neighbors: opposite sides that both face track
     * become a through route; else exactly two adjacent sides become a corner; anything else carries nothing.  Only the
     * seed: the user can still author a connection the inference declines.
     */
    public List<Route> transparentRoutes(TileKey tile)
    {
        List<Route> out = new ArrayList<>();

        List<Side> facing = facingSides(tile);

        for (Side side : new Side[]{Side.N, Side.E})
        {
            if (facing.contains(side) && facing.contains(side.opposite()))
            {
                out.add(new Route(side, side.opposite(), null));
            }
        }

        if (out.isEmpty() && facing.size() == 2) out.add(new Route(facing.get(0), facing.get(1), null));

        return out;
    }

    /** The real track either side of a route tile along its roads, where its length is folded to; rails only. */
    public List<TileKey> trackBesideARouteTile(TileKey tile)
    {
        List<TileKey> out = new ArrayList<>();

        for (Route route : transparentRoutes(tile))
        {
            for (Side side : new Side[] {route.getA(), route.getB()})
            {
                if (realTrackOn(tile, side) && !out.contains(tile.beside(side))) out.add(tile.beside(side));
            }
        }

        return out;
    }

    /**
     * The sides of a transparent tile that something conducts to (OB-160), in N E S W order; shared so checks agree on
     * "facing".
     */
    private List<Side> facingSides(TileKey tile)
    {
        List<Side> facing = new ArrayList<>();

        for (Side side : Side.values())
        {
            TileKey neighbourKey = tile.beside(side);
            LayoutDiagramComponent neighbourComponent = tiles.get(neighbourKey);

            if (neighbourComponent == null) continue;

            // a transparent neighbor presents a face on every side, so adjacent buttons do not wait on each other
            if (TilePorts.isTransparent(neighbourComponent.getType())
                || hasPortOn(neighbourComponent, side.opposite()))
            {
                facing.add(side);
            }
        }

        return facing;
    }

    /** Whether the tile on this side of a transparent one is real track rather than another button. */
    private boolean realTrackOn(TileKey tile, Side side)
    {
        LayoutDiagramComponent neighbourComponent = tiles.get(tile.beside(side));

        return neighbourComponent != null && !TilePorts.isTransparent(neighbourComponent.getType());
    }

    /**
     * Whether the run of buttons this one belongs to meets real track on two different sides (OB-160): a single button
     * conducts what is beside it; a run conducts across squares with no rails, so two ends is a splice.
     */
    private boolean runReachesTrackOnBothSides(TileKey from)
    {
        Set<TileKey> run = new LinkedHashSet<>();
        List<TileKey> pending = new ArrayList<>();

        run.add(from);
        pending.add(from);

        boolean adjacent = false;

        // The real faces as SQUARES, not a count: two buttons in a run can face the same track.
        Set<TileKey> reached = new LinkedHashSet<>();

        while (!pending.isEmpty())
        {
            TileKey tile = pending.remove(pending.size() - 1);

            for (Side side : facingSides(tile))
            {
                TileKey neighbourKey = tile.beside(side);

                if (realTrackOn(tile, side))
                {
                    reached.add(neighbourKey);

                    continue;
                }

                adjacent = true;

                if (run.add(neighbourKey)) pending.add(neighbourKey);
            }
        }

        return adjacent && reached.size() >= 2;
    }

    /**
     * Route buttons placed where what they conduct is not what was drawn (OB-160); a second pass, since it asks about
     * neighbors.
     */
    private void checkTransparentTiles()
    {
        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (!TilePorts.isTransparent(entry.getValue().getType())) continue;

            TileKey tile = entry.getKey();

            List<Side> facing = facingSides(tile);

            // Adjacent buttons that reach track at both ends.  Adam: "Only if they are connected to something else on
            // the graph"; a run touching track at one end connects nothing.  Counted over the whole run: three in a row
            // is the same fault as two.
            if (runReachesTrackOnBothSides(tile))
            {
                problems.add(new Problem(tile, ERROR_ADJACENT_ROUTE_TILES, true));

                continue;
            }

            // And an arm that would be dropped: four sides (two routes, a crossing) stay quiet.
            Set<Side> carried = new LinkedHashSet<>();

            for (Route route : transparentRoutes(tile))
            {
                carried.add(route.getA());
                carried.add(route.getB());
            }

            // Only where something was carried: an empty result is a button that conducts nothing.
            if (carried.isEmpty()) continue;

            for (Side side : facing)
            {
                if (!carried.contains(side) && realTrackOn(tile, side))
                {
                    problems.add(new Problem(tile, ERROR_ROUTE_TILE_THREE_WAY, true));

                    break;
                }
            }
        }
    }

    /**
     * The road a route tile's identifier names, as the sides it joins, or null (OB-279); the inverse of
     * `transparentRouteId`.
     */
    public static Route transparentRouteOf(RouteId id)
    {
        if (id == null || id.getState() != 0 || id.getIndex() < 100) return null;

        int code = id.getIndex() - 100;

        Side[] sides = Side.values();

        if (code >= sides.length * sides.length) return null;

        return new Route(sides[code / sides.length], sides[code % sides.length], null);
    }

    /** Transparent routes are identified by the axis they turned out to carry, not by an index into the port map. */
    private static RouteId transparentRouteId(Route route)
    {
        return new RouteId(0, 100 + route.getA().ordinal() * Side.values().length + route.getB().ordinal());
    }

    /**
     * A turnout the operator has declared permanently set: no address, so it cannot be thrown.  Package-visible because
     * `GraphReducer`'s berth-room walks ask the same question (OB-233).
     */
    static boolean isPermanentTurnout(componentType type)
    {
        return type == componentType.CUSTOM_PERM_LEFT
            || type == componentType.CUSTOM_PERM_RIGHT
            || type == componentType.CUSTOM_PERM_Y
            || type == componentType.CUSTOM_PERM_THREEWAY
            || type == componentType.CUSTOM_PERM_SCISSORS;
    }

    private Route routeOf(LayoutDiagramComponent component, RouteId routeId)
    {
        if (routeId.getIndex() >= 100 || routeId.getIndex() < 0) return null;

        List<Route> routes = TilePorts.ports(
            component.getType(), component.getOrientation(), routeId.getState());

        if (routeId.getIndex() < 0 || routeId.getIndex() >= routes.size()) return null;

        return routes.get(routeId.getIndex());
    }

    /**
     * Whether a direction can be chosen for this route given the hardware (OB-057): offering one `directionAllows`
     * would refuse is a control the guard will not honor.  On a permanent turnout every route is directed at the toe.
     */
    public static boolean directionIsPossible(Direction direction, Route route)
    {
        if (direction == Direction.NONE) return true;

        boolean towardA = route.isTraversableFrom(route.getB());
        boolean towardB = route.isTraversableFrom(route.getA());

        if (direction == Direction.BOTH) return towardA && towardB;

        return direction == Direction.TOWARD_A ? towardA : towardB;
    }

    /**
     * Whether any train can use this route, given the authored direction and the hardware.  A direction the hardware
     * refuses is a closure, not "restricts nothing": reading it that way would open a road the operator shut.
     * `directionIsPossible` says what the editor should offer; only this says whether a train moves.
     */
    public static boolean isPassable(Direction direction, Route route)
    {
        if (direction == Direction.NONE) return false;

        for (Side entry : new Side[] { route.getA(), route.getB() })
        {
            if (route.isTraversableFrom(entry) && directionAllows(direction, route, entry)) return true;
        }

        return false;
    }

    private static boolean directionAllows(Direction direction, Route route, Side entrySide)
    {
        if (direction == Direction.NONE) return false;
        if (direction == Direction.BOTH) return true;

        Side allowedToward = direction == Direction.TOWARD_A ? route.getA() : route.getB();

        return route.other(entrySide) == allowedToward;
    }

    /**
     * The answer in force on a road: what is stored, narrowed by the hardware.  A stored answer the editor does not
     * offer on this road is either the permanent turnout's default `BOTH`, which the blades narrow, or a closure.
     */
    public static Direction inForce(Direction stored, Route route)
    {
        if (directionIsPossible(stored, route)) return stored;

        return !isPassable(stored, route) ? Direction.NONE
            : directionIsPossible(Direction.TOWARD_A, route) ? Direction.TOWARD_A
            : Direction.TOWARD_B;
    }

    /** The arms of a square (every side any of its roads ends at), in a fixed order so a mask over them is stable. */
    public static List<Side> armsOf(java.util.Collection<Route> routes)
    {
        List<Side> sides = new ArrayList<>();

        for (Side side : Side.values())
        {
            for (Route route : routes)
            {
                if ((route.getA() == side || route.getB() == side) && !sides.contains(side)) sides.add(side);
            }
        }

        return sides;
    }

    /**
     * The direction a road stores for a set of open arms: both open is both ways, one open is one way toward it,
     * neither is closed.  `sides` are the arms as `armsOf` orders them.
     */
    public static Direction answerFor(Route route, List<Side> sides, int mask)
    {
        boolean openA = (mask & (1 << sides.indexOf(route.getA()))) != 0;
        boolean openB = (mask & (1 << sides.indexOf(route.getB()))) != 0;

        return openA && openB ? Direction.BOTH : openA ? Direction.TOWARD_A
            : openB ? Direction.TOWARD_B : Direction.NONE;
    }

    /**
     * Whether a square's arrows can hide a road's direction (OB-351): two roads end at one side, so its arrow is both
     * their answers.  Not half of a two-square crossing, whose shared side draws no arrow (OB-320, OB-322).
     */
    public boolean sharesAnArm(TileKey tile)
    {
        if (tile == null || crossingPartners.containsKey(tile)) return false;

        Set<Side> seen = java.util.EnumSet.noneOf(Side.class);

        for (Route route : roads(tile).values())
        {
            if (!seen.add(route.getA()) | !seen.add(route.getB())) return true;
        }

        return false;
    }

    /** A square's roads with a way through - not a link's stub, which has one side and no direction of its own. */
    private Map<RouteId, Route> roads(TileKey tile)
    {
        Map<RouteId, Route> out = new LinkedHashMap<>();

        for (Map.Entry<RouteId, Route> entry : getRoutes(tile).entrySet())
        {
            if (entry.getValue().getA() != entry.getValue().getB()) out.put(entry.getKey(), entry.getValue());
        }

        return out;
    }

    /** The arms a square lets trains leave by, as a mask over `armsOf`: what its arrows draw. */
    public int armMask(TileKey tile)
    {
        Map<RouteId, Route> routes = roads(tile);

        return armMask(tile, routes, armsOf(routes.values()));
    }

    /** The same over roads and arms the caller has gathered itself. */
    public int armMask(TileKey tile, Map<RouteId, Route> routes, List<Side> sides)
    {
        int mask = 0;

        for (Map.Entry<RouteId, Route> entry : routes.entrySet())
        {
            mask |= leavesBy(inForce(getDirection(tile, entry.getKey()), entry.getValue()), entry.getValue(), sides);
        }

        return mask;
    }

    /** The arms open in a mask over `arms`, in words ("N, W"); empty when none is open. */
    public static String armsInWords(int mask, List<Side> arms)
    {
        List<String> open = new ArrayList<>();

        for (int i = 0; i < arms.size(); i++)
        {
            if ((mask & (1 << i)) != 0) open.add(String.valueOf(arms.get(i)));
        }

        return String.join(", ", open);
    }

    /** The arms of a square with a way through, in the order `armMask` counts them. */
    public List<Side> armsOf(TileKey tile)
    {
        return armsOf(roads(tile).values());
    }

    /** The arms a road in this direction lets a train leave by, as a mask over `sides`. */
    private static int leavesBy(Direction direction, Route route, List<Side> sides)
    {
        int mask = 0;

        if (direction == Direction.BOTH || direction == Direction.TOWARD_A) mask |= 1 << sides.indexOf(route.getA());
        if (direction == Direction.BOTH || direction == Direction.TOWARD_B) mask |= 1 << sides.indexOf(route.getB());

        return mask;
    }

    /**
     * Every road of a square given the directions wanted on some, as its arms can hold them (OB-351): an arm one road
     * opens and another shuts stays open.
     */
    public Map<RouteId, Direction> armAnswersFor(TileKey tile, Map<RouteId, Direction> wanted)
    {
        if (!sharesAnArm(tile)) return new LinkedHashMap<>(wanted);

        Map<RouteId, Route> routes = roads(tile);

        List<Side> sides = armsOf(routes.values());

        int opened = 0;
        int shut = 0;

        for (Map.Entry<RouteId, Direction> each : wanted.entrySet())
        {
            Route route = routes.get(each.getKey());

            if (route == null || each.getValue() == null) continue;

            int leaves = leavesBy(inForce(each.getValue(), route), route, sides);

            int ends = (1 << sides.indexOf(route.getA())) | (1 << sides.indexOf(route.getB()));

            opened |= leaves;
            shut |= ends & ~leaves;
        }

        int mask = (armMask(tile) | opened) & ~(shut & ~opened);

        Map<RouteId, Direction> out = new LinkedHashMap<>();

        for (Map.Entry<RouteId, Route> entry : routes.entrySet())
        {
            out.put(entry.getKey(), inForce(answerFor(entry.getValue(), sides, mask), entry.getValue()));
        }

        return out;
    }

    /** What `normalizeToArms` changed at the last apply: each square to its changed roads and their new directions. */
    private final Map<TileKey, Map<RouteId, Direction>> normalizedToArms = new LinkedHashMap<>();

    /**
     * Brings every square whose arms are shared to what its arrows show (OB-351): a road closed a way its arrow showed
     * open is opened; nothing is shut.  A square at its defaults is never touched.  The result is kept for
     * `getNormalizedToArms`.
     */
    public Map<TileKey, Map<RouteId, Direction>> normalizeToArms()
    {
        normalizedToArms.clear();

        for (TileKey tile : new ArrayList<>(directions.keySet()))
        {
            if (directions.get(tile).isEmpty() || !sharesAnArm(tile)) continue;

            Map<RouteId, Route> routes = roads(tile);

            Map<RouteId, Direction> answers = armAnswersFor(tile, Collections.<RouteId, Direction>emptyMap());

            Map<RouteId, Direction> changed = new LinkedHashMap<>();

            for (Map.Entry<RouteId, Direction> answer : answers.entrySet())
            {
                if (inForce(getDirection(tile, answer.getKey()), routes.get(answer.getKey())) == answer.getValue()) continue;

                changed.put(answer.getKey(), answer.getValue());
            }

            for (Map.Entry<RouteId, Direction> each : changed.entrySet()) setDirection(tile, each.getKey(), each.getValue());

            if (!changed.isEmpty()) normalizedToArms.put(tile, changed);
        }

        return getNormalizedToArms();
    }

    /** What the last `normalizeToArms` changed. */
    public Map<TileKey, Map<RouteId, Direction>> getNormalizedToArms()
    {
        Map<TileKey, Map<RouteId, Direction>> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Map<RouteId, Direction>> each : normalizedToArms.entrySet())
        {
            out.put(each.getKey(), new LinkedHashMap<>(each.getValue()));
        }

        return out;
    }

    /** One run of plain track: the tiles between two points, in order, and the points at either end. */
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

        /** The tiles between the two points, in order from start to end. */
        public List<TileKey> getTiles()
        {
            return Collections.unmodifiableList(tiles);
        }
    }

    /** This diagram's runs, found once: they are the diagram's alone, and a graph is one diagram. */
    private Map<TileKey, Run> runs;

    /**
     * Every run of plain track, keyed by the tile that speaks for it: one tile in each run is set and the rest follow.
     * Computed from the diagram alone, never from the reduction (edges come and go as directions are set, and the
     * graying would move).  A run tile has exactly one route, is not a sensor and not ignored; a switch, crossing or
     * sensor ends the run.
     */
    public Map<TileKey, Run> runs()
    {
        if (runs != null) return runs;

        Map<TileKey, Run> out = new LinkedHashMap<>();

        Set<TileKey> seen = new LinkedHashSet<>();

        for (TileKey tile : tiles.keySet())
        {
            if (seen.contains(tile) || !isRunTile(tile)) continue;

            Route route = firstRoad(tile);

            java.util.LinkedList<TileKey> chain = new java.util.LinkedList<>();
            chain.add(tile);
            seen.add(tile);

            TileKey endA = walk(chain, tile, route.getA(), seen, false);
            TileKey endB = walk(chain, tile, route.getB(), seen, true);

            out.put(chain.getFirst(), new Run(endA, endB, new ArrayList<>(chain)));
        }

        runs = Collections.unmodifiableMap(out);

        return runs;
    }

    /** The run a square belongs to, or null for a square that is no plain track. */
    public Run runOf(TileKey tile)
    {
        for (Run run : runs().values())
        {
            if (run.getTiles().contains(tile)) return run;
        }

        return null;
    }

    /** Whether a square is a piece of plain track that can belong to a run. */
    private boolean isRunTile(TileKey tile)
    {
        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null || component.isFeedback()) return false;

        if (TilePorts.isDisqualified(component.getType())
            || TilePorts.isTransparent(component.getType())) return false;

        if (getRoutes(tile).size() != 1) return false;

        // A stub (an END, a tunnel mouth, a link) walks straight back out: not a run tile.
        Route route = firstRoad(tile);

        return route != null && route.getA() != route.getB();
    }

    /** A square's first road, or null. */
    private Route firstRoad(TileKey tile)
    {
        Map<RouteId, Route> routes = getRoutes(tile);

        return routes.isEmpty() ? null : routes.values().iterator().next();
    }

    /**
     * Extends a chain out of one side of a tile while the track stays plain (`append` adds to the end, else the front);
     * returns where it stops.
     */
    private TileKey walk(java.util.LinkedList<TileKey> chain, TileKey from, Side side,
        Set<TileKey> seen, boolean append)
    {
        TileKey here = from;
        Side out = side;

        for (int guard = 0; guard < 1000; guard++)
        {
            Landing landing = landing(here, out);

            if (landing == null) return null;

            TileKey next = landing.getTile();

            if (!isRunTile(next) || seen.contains(next)) return next;

            Route route = firstRoad(next);

            if (append) chain.addLast(next); else chain.addFirst(next);

            seen.add(next);

            here = next;
            out = route.other(landing.getEntrySide());

            if (out == null) return null;
        }

        return null;
    }

    /**
     * Which ways a train may pass a square of a run, along the run, or null when unreadable; hardware and user both
     * count.
     */
    private String waysThrough(List<TileKey> run, int at)
    {
        Side[] sides = sidesAlong(run, at);

        if (sides == null) return null;

        return (leavesBy(run.get(at), sides[0], sides[1]) ? "forward" : "")
            + (leavesBy(run.get(at), sides[1], sides[0]) ? "back" : "");
    }

    /** The side a square of a run is entered by going forward along it, and left by; null when they cannot be read. */
    private Side[] sidesAlong(List<TileKey> run, int at)
    {
        TileKey tile = run.get(at);

        Route route = firstRoad(tile);

        if (route == null) return null;

        Side back = at > 0 ? sideTowardNeighbour(tile, run.get(at - 1)) : null;
        Side ahead = at + 1 < run.size() ? sideTowardNeighbour(tile, run.get(at + 1)) : null;

        if (back == null && ahead == null) return null;

        if (back == null) back = route.other(ahead);
        if (ahead == null) ahead = route.other(back);

        if (back == null || ahead == null) return null;

        return new Side[] {back, ahead};
    }

    /** Whether a train that entered a square by one side may leave it by another. */
    private boolean leavesBy(TileKey tile, Side entry, Side exit)
    {
        for (Exit leaving : exits(tile, entry))
        {
            if (leaving.getSide() == exit) return true;
        }

        return false;
    }

    /**
     * Which ways trains may run along a run as a whole, or null where a square cannot be read: only where every square
     * lets them.
     */
    private boolean[] narrowest(List<TileKey> tiles)
    {
        boolean forward = true;
        boolean back = true;

        for (int at = 0; at < tiles.size(); at++)
        {
            String ways = waysThrough(tiles, at);

            if (ways == null) return null;

            forward &= ways.startsWith("forward");
            back &= ways.endsWith("back");
        }

        return new boolean[] {forward, back};
    }

    /**
     * Which way trains run along a run of plain track, as a direction of its first square's road: the narrowest of its
     * squares (OB-361).  Null for a square that leads no run of two or more, or a run that cannot be read.
     */
    public Direction runsAs(TileKey leader)
    {
        Run run = leader == null ? null : runs().get(leader);

        if (run == null || run.getTiles().size() < 2) return null;

        boolean[] ways = narrowest(run.getTiles());

        return ways == null ? null : directionAlong(run.getTiles(), 0, ways[0], ways[1]);
    }

    /** A square of a run's own direction for the run's ways, {forward, back} along it, or null where it cannot be read. */
    private Direction directionAlong(List<TileKey> run, int at, boolean forward, boolean back)
    {
        if (forward == back) return forward ? Direction.BOTH : Direction.NONE;

        Side[] sides = sidesAlong(run, at);

        Route route = firstRoad(run.get(at));

        if (sides == null || route == null) return null;

        Side toward = forward ? sides[1] : sides[0];

        return toward == route.getA() ? Direction.TOWARD_A : Direction.TOWARD_B;
    }

    /**
     * The runs whose squares point opposite ways, so no train can pass (OB-361), by leader; `normalizeRuns` leaves
     * them.
     */
    public Map<TileKey, Run> runsPointingOppositeWays()
    {
        Map<TileKey, Run> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Run> entry : runs().entrySet())
        {
            if (pointsOppositeWays(entry.getValue().getTiles())) out.put(entry.getKey(), entry.getValue());
        }

        return out;
    }

    /** Whether one square of a run lets trains along it only forward and another only back. */
    private boolean pointsOppositeWays(List<TileKey> tiles)
    {
        boolean onlyForward = false;
        boolean onlyBack = false;

        for (int at = 0; at < tiles.size(); at++)
        {
            String ways = waysThrough(tiles, at);

            onlyForward |= "forward".equals(ways);
            onlyBack |= "back".equals(ways);
        }

        return onlyForward && onlyBack;
    }

    /**
     * What `normalizeRuns` changed at the last apply: each leader to the squares changed, their roads and directions.
     */
    private final Map<TileKey, Map<TileKey, Map<RouteId, Direction>>> normalizedRuns = new LinkedHashMap<>();

    /**
     * Brings every run of two or more squares to one direction, its narrowest (OB-361).  A run whose squares point
     * opposite ways is left as it is.  The result is kept for `getNormalizedRuns`.
     */
    public Map<TileKey, Map<TileKey, Map<RouteId, Direction>>> normalizeRuns()
    {
        normalizedRuns.clear();

        for (Map.Entry<TileKey, Run> entry : runs().entrySet())
        {
            List<TileKey> run = entry.getValue().getTiles();

            if (run.size() < 2 || pointsOppositeWays(run)) continue;

            boolean[] ways = narrowest(run);

            if (ways == null) continue;

            String wanted = (ways[0] ? "forward" : "") + (ways[1] ? "back" : "");

            Map<TileKey, Map<RouteId, Direction>> changed = new LinkedHashMap<>();

            for (int at = 0; at < run.size(); at++)
            {
                if (wanted.equals(waysThrough(run, at))) continue;

                Direction direction = directionAlong(run, at, ways[0], ways[1]);

                if (direction == null) continue;

                RouteId road = getRoutes(run.get(at)).keySet().iterator().next();

                setDirection(run.get(at), road, direction);

                Map<RouteId, Direction> roads = new LinkedHashMap<>();

                roads.put(road, direction);

                changed.put(run.get(at), roads);
            }

            if (!changed.isEmpty()) normalizedRuns.put(entry.getKey(), changed);
        }

        return getNormalizedRuns();
    }

    /** What the last `normalizeRuns` changed. */
    public Map<TileKey, Map<TileKey, Map<RouteId, Direction>>> getNormalizedRuns()
    {
        Map<TileKey, Map<TileKey, Map<RouteId, Direction>>> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Map<TileKey, Map<RouteId, Direction>>> run : normalizedRuns.entrySet())
        {
            Map<TileKey, Map<RouteId, Direction>> squares = new LinkedHashMap<>();

            for (Map.Entry<TileKey, Map<RouteId, Direction>> square : run.getValue().entrySet())
            {
                squares.put(square.getKey(), new LinkedHashMap<>(square.getValue()));
            }

            out.put(run.getKey(), squares);
        }

        return out;
    }

    private static boolean hasPortOn(LayoutDiagramComponent component, Side side)
    {
        componentType type = component.getType();

        if (TilePorts.isDisqualified(type) || TilePorts.isTerminator(type)) return false;

        // A transparent tile presents a face everywhere; what it conducts is settled when it is walked.
        if (TilePorts.isTransparent(type)) return true;

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            for (Route r : TilePorts.ports(type, component.getOrientation(), state))
            {
                if (r.touches(side)) return true;
            }
        }

        return false;
    }

    /** The single open side of a portal or dead-end tile. */
    private static Side stubSide(LayoutDiagramComponent component)
    {
        for (Route r : TilePorts.ports(component.getType(), component.getOrientation(), 0))
        {
            if (r.getA() == r.getB()) return r.getA();
        }

        return null;
    }

    /**
     * Which side of a tile faces a neighbor, or null: geometric, as `gridSideTowards`.  A portal's partner is reached
     * through no side (use landing()).
     */
    public Side sideTowardNeighbour(TileKey tile, TileKey other)
    {
        if (tile == null || other == null) return null;

        for (Side side : Side.values())
        {
            if (other.equals(tile.beside(side))) return side;
        }

        return null;
    }

    /**
     * A route from one square to another over the track as DRAWN, ignoring which way trains may run (this exists to
     * change directions).  Returns the squares inclusive, or null when no continuous track joins them.
     */
    public List<TileKey> findUndirectedPath(TileKey from, TileKey to)
    {
        if (from == null || to == null || !tiles.containsKey(from) || !tiles.containsKey(to)) return null;

        if (from.equals(to)) return new ArrayList<>(Collections.singletonList(from));

        // Walked as (square, entry side), not squares: a square can carry two tracks (a crossing, a double curve), and
        // a route is usable only if it touches the entry side, as in the reducer.
        Map<String, Step> cameFrom = new LinkedHashMap<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        // The first square has no entry side, so every route on it is open.
        Step first = new Step(from, null);

        frontier.add(first);
        cameFrom.put(first.key(), null);

        while (!frontier.isEmpty())
        {
            Step here = frontier.poll();

            for (Step next : continuations(here))
            {
                if (cameFrom.containsKey(next.key())) continue;

                cameFrom.put(next.key(), here);

                if (next.tile.equals(to))
                {
                    List<TileKey> path = new ArrayList<>();

                    for (Step at = next; at != null; at = cameFrom.get(at.key())) path.add(0, at.tile);

                    return path;
                }

                frontier.add(next);
            }
        }

        return null;
    }

    /**
     * The first of some squares reached by leaving a square through one side, over the track as DRAWN and never past
     * one of them: where an old autonomy.json's edge could lead by that side (a shortest walk cannot say, round a
     * loop).  `stops` are the squares a walk ends at; empty when none is reached.
     */
    public Set<TileKey> firstStopsLeaving(TileKey from, Side side, Set<TileKey> stops)
    {
        Set<TileKey> reached = new LinkedHashSet<>();

        if (from == null || side == null || stops == null || !tiles.containsKey(from)) return reached;

        Step start = hasTrackBy(from, side) ? firstStep(from, side) : null;

        if (start == null) return reached;

        Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        frontier.add(start);

        while (!frontier.isEmpty())
        {
            Step here = frontier.poll();

            if (!seen.add(here.key())) continue;

            if (here.tile.equals(from)) continue;

            if (stops.contains(here.tile))
            {
                reached.add(here.tile);

                continue;
            }

            frontier.addAll(continuations(here));
        }

        return reached;
    }

    /**
     * Whether the track leaving a square by one side runs out before it reaches any of the stops, as DRAWN (FR-118).
     */
    public boolean runsOutLeaving(TileKey from, Side side, Set<TileKey> stops)
    {
        if (from == null || side == null || stops == null || !tiles.containsKey(from)) return false;

        if (!hasTrackBy(from, side)) return false;

        Step start = firstStep(from, side);

        if (start == null) return true;

        Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        frontier.add(start);

        while (!frontier.isEmpty())
        {
            Step here = frontier.poll();

            if (!seen.add(here.key())) continue;

            if (here.tile.equals(from) || stops.contains(here.tile)) return false;

            frontier.addAll(continuations(here));
        }

        return true;
    }

    /**
     * The squares leaving a square by one side to `to`, as DRAWN and never past a stop: `firstStopsLeaving`'s walk with
     * its route; else null.
     */
    public List<TileKey> pathLeaving(TileKey from, Side side, TileKey to, Set<TileKey> stops)
    {
        if (from == null || side == null || to == null || !tiles.containsKey(from)) return null;

        Step start = hasTrackBy(from, side) ? firstStep(from, side) : null;

        if (start == null) return null;

        Map<String, Step> cameFrom = new HashMap<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        cameFrom.put(start.key(), null);
        frontier.add(start);

        while (!frontier.isEmpty())
        {
            Step here = frontier.poll();

            if (here.tile.equals(to))
            {
                List<TileKey> path = new ArrayList<>();

                for (Step at = here; at != null; at = cameFrom.get(at.key())) path.add(0, at.tile);

                path.add(0, from);

                return path;
            }

            if (here.tile.equals(from) || (stops != null && stops.contains(here.tile))) continue;

            for (Step next : continuations(here))
            {
                if (cameFrom.containsKey(next.key())) continue;

                cameFrom.put(next.key(), here);
                frontier.add(next);
            }
        }

        return null;
    }

    /** Whether any of a square's routes runs to this side. */
    private boolean hasTrackBy(TileKey from, Side side)
    {
        for (Route route : getRoutes(from).values())
        {
            if (route.touches(side)) return true;
        }

        return false;
    }

    /** A walk's first step out of a square by one side; null where that lands on no square of this graph. */
    private Step firstStep(TileKey from, Side side)
    {
        Landing first = landing(from, side);

        if (first == null || !tiles.containsKey(first.getTile())) return null;

        return new Step(first.getTile(), first.getEntrySide());
    }

    /** One square of an undirected walk, remembered with the side it was entered by. */
    private static final class Step
    {
        private final TileKey tile;
        private final Side entrySide;

        /** On a square of a two-square crossing entered where its squares meet, the leg its track leaves by (OB-320) */
        private final Side lane;

        Step(TileKey tile, Side entrySide)
        {
            this(tile, entrySide, null);
        }

        Step(TileKey tile, Side entrySide, Side lane)
        {
            this.tile = tile;
            this.entrySide = entrySide;
            this.lane = lane;
        }

        /**
         * Arrivals by different sides are different places to be (different track leads on); keyed by the track as well
         * where two share a side (OB-320).
         */
        String key()
        {
            return tile + "@" + entrySide + (lane == null ? "" : "/" + lane);
        }
    }

    /** Where a walk that arrived here can go next, over continuous track only. */
    private List<Step> continuations(Step here)
    {
        List<Step> out = new ArrayList<>();

        LayoutDiagramComponent component = tiles.get(here.tile);

        if (component == null) return out;

        // A two-square crossing is walked a track at a time (OB-320): a step onto the other square carries its leg.
        TileKey otherHalf = crossingPartners.get(here.tile);

        if (otherHalf != null)
        {
            Side toe = sideTowardNeighbour(here.tile, otherHalf);

            for (Route route : getRoutes(here.tile).values())
            {
                Side leg = route.other(toe);

                if (leg == null || leg == toe) continue;

                if (here.entrySide != null && !route.touches(here.entrySide)) continue;

                if (here.lane != null && leg != here.lane) continue;

                if (here.entrySide != toe)
                {
                    Landing across = landing(here.tile, toe);

                    if (across != null && tiles.containsKey(across.getTile()))
                    {
                        out.add(new Step(across.getTile(), across.getEntrySide(), leg.opposite()));
                    }
                }

                if (here.entrySide != leg)
                {
                    Landing off = landing(here.tile, leg);

                    if (off != null && tiles.containsKey(off.getTile())) out.add(new Step(off.getTile(), off.getEntrySide()));
                }
            }

            return out;
        }

        Set<Side> exits = new LinkedHashSet<>();

        for (Route route : getRoutes(here.tile).values())
        {
            if (here.entrySide != null && !route.touches(here.entrySide)) continue;

            if (here.entrySide == null)
            {
                exits.add(route.getA());
                exits.add(route.getB());
            }
            else
            {
                exits.add(route.getA() == here.entrySide ? route.getB() : route.getA());
            }
        }

        for (Side side : exits)
        {
            if (side == null || side == here.entrySide) continue;

            Landing landing = landing(here.tile, side);

            if (landing != null && tiles.containsKey(landing.getTile()))
            {
                out.add(new Step(landing.getTile(), landing.getEntrySide()));
            }
        }

        // A portal's partner is reached through no side; the reducer walks through one, and so must this.
        TileKey partner = portals.get(here.tile);

        // Not through a switched-off link: exits() offers no way through one.
        if (partner != null && !portalClosed(here.tile) && tiles.containsKey(partner)) out.add(new Step(partner, null));

        return out;
    }
}
