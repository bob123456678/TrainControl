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
 * connected.
 *
 * Geometry proposes, the user disposes: TilePorts seeds the connections and the user's overrides correct them, so a
 * misread tile is a visibly wrong line rather than a silently wrong edge.  Answers the reducer's one question: standing
 * on this tile, having entered by this side, where can I go?  Nothing here knows about Points, edges or the autonomy
 * model.
 *
 * @author Adam
 */
public class TileGraph
{
    /**
     * Something a setup collection is keyed by: always a square, sometimes a square plus more.
     *
     * Keys are typed and never the string "page:x,y#state,index": erasure makes {@code Map<String, T>} and {@code
     * Map<TileKey, T>} the same signature, and a suffix built by hand is easy to get wrong.  The self type lets the
     * move-a-square, rename-a-page and drop-a-key helpers be written once over both kinds.
     *
     * @param <K> the implementing type itself
     */
    public interface SquareKeyed<K extends SquareKeyed<K>>
    {
        /**
         * The square this key is on.
         *
         * @return the square, never null
         */
        TileKey square();

        /**
         * The same key, on a different square.
         *
         * @param square where it should be instead
         * @return a new key, identical apart from its square
         */
        K withSquare(TileKey square);
    }

    /**
     * A tile's address: which page, and where on it.  Pages are named, so this survives the diagram being
     * reloaded as long as the tile is still there.
     */
    public static class TileKey implements SquareKeyed<TileKey>
    {
        private final String page;
        private final int x;
        private final int y;

        /**
         * A square is its own square, so that a bare square and a square-plus-something can go through
         * the same bookkeeping.
         *
         * @return this
         */
        @Override
        public TileKey square()
        {
            return this;
        }

        /**
         * @param square the square to move to
         * @return that square, since a TileKey is nothing but its square
         */
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

        /**
         * The square beside this one across a side, on the same page; north is the smaller y.
         * @param side the side
         * @return the square beside, or null for no side
         */
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
     * Which side of one square faces another, or null when they are not neighbors.
     *
     * Null is a real answer: a path can step from one page to another through a link, and that jump has no side on the
     * grid.  A line through the square simply stops in the middle, as a train leaving the drawn track does.  Shared
     * because the editor's tested path and the running diagram must lay a line across the same grid, and two copies
     * could disagree about which way is north.
     *
     * @param from the square being left
     * @param to the next square
     * @return the side of {@code from} that {@code to} lies beyond
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
     * Which way a train may move through one route of one tile.
     *
     * A and B are the route's two sides as TilePorts reports them.  Stored per tile rather than per
     * connection between tiles, so two neighbors can never disagree about the link they share.
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
     * A square together with one route across it: what a recorded direction belongs to.  The string form
     * "page:x,y#state,index" goes on disk and is produced by {@link #toString()} at the boundary.
     *
     * @author Adam
     */
    public static class DirectionKey implements SquareKeyed<DirectionKey>
    {
        private final TileKey tile;
        private final RouteId routeId;

        /**
         * @param tile the square
         * @param routeId which route across it
         */
        public DirectionKey(TileKey tile, RouteId routeId)
        {
            this.tile = tile;
            this.routeId = routeId;
        }

        /**
         * @return which route across the square this is
         */
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
         * The key as stored in the autonomy files: "page:x,y#state,index".
         *
         * RouteId's own toString puts a '#' between its two numbers, which is NOT the separator used here: the stored
         * form uses a comma, and changing it would make every earlier setup unreadable.
         *
         * @return the stored form
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
    public static final String WARN_PORTAL_NEVER_PAIRED = "autosetup.ui.warnLinkNeverPaired";
    public static final String WARN_PORTAL_UNREACHABLE = "autosetup.ui.warnLinkNeverPairedUnreachable";

    /**
     * A link pointing at a page left out of autonomy (OB-150).  Not WARN_PORTAL_NEVER_PAIRED, whose "pair it" remedy
     * cannot work with no tile on the far side: include the page or switch the link off.
     */
    public static final String WARN_PORTAL_EXCLUDED_PAGE = "autosetup.ui.warnLinkToExcludedPage";

    /**
     * Links the user told autonomy to leave alone: a link that belongs to the drawing rather than the railway must not
     * block the build.
     */
    private final Set<TileKey> disabledPortals = new LinkedHashSet<>();

    /**
     * @param tile a link autonomy should ignore entirely
     */
    public void disablePortal(TileKey tile)
    {
        if (tile != null) disabledPortals.add(tile);
    }

    public boolean isPortalDisabled(TileKey tile)
    {
        return portalClosed(tile);
    }

    /**
     * Whether this doorway is shut, asked at EITHER end: a pair of links is one doorway, so one end shut and the other
     * open would be a route that exists one way only.
     *
     * The writer makes both ends match (OB-041); asking both here is not redundant, because older setups can hold
     * one-ended disables and there is no migration.
     *
     * @param tile either end
     * @return whether autonomy should ignore this link
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
     * A switch or signal that must be commanded to be passed, drawn without an address behind it.  Blocking by ruling:
     * routing over one would mean trusting it to already lie the right way, which is what CUSTOM_PERM_* exists to
     * declare.
     *
     * Only fires when autonomy is being built: a diagram may contain address-less tiles and still work as one, so
     * nothing on the display or control path constructs a TileGraph.  Keep this check out of layout parsing and
     * anything that runs on load.  A page that carries such tiles deliberately can be excluded from autonomy instead.
     */
    public static final String ERROR_NO_ADDRESS = "autosetup.ui.errorTileHasNoAddress";

    /**
     * Two route buttons side by side, with real track running into the pair (OB-160).  A button carries no track and
     * conducts whatever is beside it, so each counts the other as a side worth conducting to and the pair can splice
     * two lines through diagram with no rails.  Two buttons alone on a page are a control panel and are not reported.
     */
    public static final String ERROR_ADJACENT_ROUTE_TILES = "autosetup.ui.errorAdjacentRouteTiles";

    /**
     * Track running into a route button from three sides, one of which is silently dropped (OB-160).  transparentRoutes
     * joins N to S and E to W where both face, so with three sides the pair wins and the odd arm is severed
     * unannounced.  Ambiguous rather than wrong: three arms cannot be a crossing, so there is no honest guess to make.
     * Four is a crossing and is not reported.
     */
    public static final String ERROR_ROUTE_TILE_THREE_WAY = "autosetup.ui.errorRouteTileThreeWay";

    /**
     * The route id a link is traversed under, deliberately not one getRoutes will return: a link's own route is a stub
     * (the same side twice), listed as (0,0), and this is (0,-1) so the two never collide.
     *
     * A link cannot carry a direction: a stub has one side, so directionAllows would compare it with itself and answer
     * true either way.  AutonomyEditorPanel therefore offers no direction answers on a link tile
     * (testALinkIsNotOfferedADirection pins that).
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
     * Every page in the layout in the order the window lists them (sorted by name, as getLayoutList sorts), excluded
     * pages included.  A link tile records its destination as a zero-based index into this list, and the question it
     * answers is whether an arrow points at an excluded page, which `pages` has already dropped.
     */
    private final List<String> allPages = new ArrayList<>();

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
            // Before the exclusion: a link's destination indexes every page, so skipping would shift every address
            // after the first excluded page.
            allPages.add(diagram.getName());

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

                // Scissors are a drawing convention (two tiles depicting one double slip) whose topology cannot be
                // expressed per tile: refuse the diagram, since ignoring the tile leaves a hole that walks quietly
                // route around.
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
                    // Scanned here, not while walking, so an unaddressed switch on a siding no route reaches is still
                    // reported.
                    problems.add(new Problem(key, ERROR_NO_ADDRESS, true));
                }
            }
        }

        // AFTER every page is in, because these questions are about neighbors (OB-160).
        checkTransparentTiles();

        // And so is this one (OB-320)
        pairTheCrossings();
    }

    /**
     * Pairs two portal tiles (tunnels or page links).  Pairing is authored, never inferred: an unpaired portal does not
     * connect, the safe default for a diagram drawn years ago.  It is mutual and exclusive; a half-pairing is reported
     * rather than treated as a one-way jump.
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
     * The direction a route has before anyone touches it.  Plain track runs both ways; a switch runs base to forks (out
     * of the toe only), so every switch reads deterministically and opening the trailing direction is deliberate; a
     * double slip has no single toe and defaults to bidirectional.
     *
     * @param tile
     * @param routeId
     * @return
     */
    public Direction defaultDirection(TileKey tile, RouteId routeId)
    {
        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return Direction.BOTH;

        // A crossing runs both ways until somebody says otherwise, and a two-square one is a crossing (OB-320)
        if (crossingPartners.containsKey(tile)) return Direction.BOTH;

        Route route = routeOf(component, routeId);

        if (route == null) return Direction.BOTH;

        // A route the hardware already restricts sets no default: for a defective turnout, base-to-forks ANDed with the
        // blades' into-the-toe-only would leave the tile impassable both ways.
        if (route.getDirectedToward() != null) return Direction.BOTH;

        Side toe = TilePorts.deriveToe(component.getType(), component.getOrientation());

        if (toe == null) return Direction.BOTH;

        // base to forks: travel away from the toe
        if (route.getA() == toe) return Direction.TOWARD_B;
        if (route.getB() == toe) return Direction.TOWARD_A;

        return Direction.BOTH;
    }

    /**
     * The moves available from a tile, having entered it by the given side.  The hardware's constraint (a defective
     * turnout can only be trailed through) and the user's direction for that route must both allow the move, so no user
     * setting can re-open a facing move through a switch that cannot be thrown.
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

        // A portal tile has two ports but only one is a side; the pairing leads to its partner and has no direction on
        // the grid, so it is addressed as a null side: arriving by null means arriving through the portal, leaving by
        // null means taking it.  Without this a link is a hole, since its only route is a stub and the loop below skips
        // stubs.
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

        // A transparent tile - a route button - carries whatever line it happens to be sitting on, which
        // is decided by what is next to it rather than by anything in the tile itself.
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
     * The moves out of a square on the track a train arrived along (OB-320).  As `exits(tile, entrySide)` except on a
     * square of a two-square crossing entered by the side its two squares meet at: both tracks pass that side, so the
     * side alone would also offer the other track's leg, a reverse curve no train can run.  The previous square and its
     * road say which track.
     *
     * @param tile the square
     * @param entrySide the side it was entered by
     * @param previous the square the train was on before, or null
     * @param previousRoute its road over that square, or null
     * @return the moves
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
     * The leg a two-square crossing's track leaves a square by, for a train that came from the other square (OB-320):
     * the side opposite the leg it came onto the other square by, since each track runs corner to corner.  Null where
     * the square is not half of a crossing or the train did not come from its other half.
     *
     * @param tile the square
     * @param entrySide the side it was entered by
     * @param previous the square the train was on before
     * @param previousRoute its road over that square
     * @return the leg, or null
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
     * The other square of a two-square crossing, or null where this square is not half of one (OB-320).
     *
     * Two permanent turnouts drawn toe to toe: the diagram's way of drawing a diagonal crossing as the two halves of an
     * X, each track running from a leg of one square, over the toes, to the opposite leg of the other.  Qualifying
     * pairs are two Ys, or two lefts or two rights turned half round, where every leg faces the opposite leg of the
     * other; only toe to toe and beside each other on one page.  A permanent turnout without such a partner stays the
     * turnout it always was (Adam: *"those without another adjacent to form a crossing are treated as switches only
     * crossable in one direction by autonomy"*).
     *
     * @param tile a square
     * @return the other half, or null
     */
    public TileKey crossingPartner(TileKey tile)
    {
        return tile == null ? null : crossingPartners.get(tile);
    }

    /**
     * The other square's road for the same track of a two-square crossing - the one from the opposite leg - or null where
     * the square is not half of one (OB-320).
     *
     * @param tile a square
     * @param routeId one of its roads
     * @return the other square's road on that track, or null
     */
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
     * Makes the two halves of each two-square crossing's track agree where the directions applied to them do not
     * (OB-320).
     *
     * Halves can disagree when answers were set while the squares were permanent turnouts, as in an older setup.  They
     * are then read as a turnout's, the same on both halves: towards the toe is the way the blades run and restricted
     * nothing, so the track runs both ways; towards a leg is how a permanent turnout was closed, so the track is closed
     * if either half was.
     *
     * The graph's directions only: the setup keeps what was set, and the first edit of the track sets both halves anew.
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

    /**
     * Whether a direction on half of a crossing closed the road when the square was a permanent turnout: closed, or
     * towards a leg - which, against blades that run only into the toe, let nothing through.
     *
     * @param tile half of a crossing
     * @param routeId one of its roads
     * @param direction the road's direction
     * @return whether it closed the road
     */
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
     * A direction on one square's half of a two-square crossing's track, as the other square's half reads it (OB-320):
     * the same way along the track, named by that square's own sides.  Both ways and closed read the same on both.
     *
     * @param tile a square
     * @param routeId one of its roads
     * @param direction its direction
     * @return the other half's, or null where the square is not half of a crossing
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

        // toward this square's toe is onto the other square and out by its leg; toward this square's leg is off the
        // other square by its toe
        Side thereToward = toward == toe ? there.other(toe.opposite()) : toe.opposite();

        return thereToward == there.getA() ? Direction.TOWARD_A : Direction.TOWARD_B;
    }

    /**
     * Finds the two-square crossings: every two permanent turnouts whose toes face each other and whose legs face each
     * other's opposite legs (OB-320).  A turnout has one toe, so a square is half of one crossing at most.
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

            // EVERY LEG FACING THE OPPOSITE LEG of the other square, which is how each track runs corner to corner or
            // straight on.  A left with a right has both diverging legs on one side: no track could go on from one of
            // them, so the two stay turnouts.
            Set<Side> across = new LinkedHashSet<>();

            for (Side leg : legsOf(entry.getValue(), toe)) across.add(leg.opposite());

            if (across.equals(legsOf(there, toe.opposite()))) crossingPartners.put(entry.getKey(), other);
        }

        // NOT A TURNOUT TO WARN ABOUT: the warning says trains may pass it only trailing, and a crossing passes them both
        // ways
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

    /**
     * A square's roads in one position, as the port map has them - except on a square of a two-square crossing, whose
     * roads are a crossing's track and run both ways rather than only into the toe (OB-320).
     */
    private List<Route> routesOn(TileKey tile, LayoutDiagramComponent component, int state)
    {
        List<Route> routes = TilePorts.ports(component.getType(), component.getOrientation(), state);

        if (!crossingPartners.containsKey(tile)) return routes;

        List<Route> open = new ArrayList<>();

        for (Route route : routes) open.add(new Route(route.getA(), route.getB(), null));

        return open;
    }

    /**
     * Where leaving a tile by the given side lands: the adjacent tile entered by the facing side if it has a port there
     * (track that stops at a blank square stops), or for a portal's stub side its paired partner, possibly on another
     * page.
     *
     * @param tile
     * @param exitSide
     * @return where the move lands, or null if nothing is there
     */
    public Landing landing(TileKey tile, Side exitSide)
    {
        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null) return null;

        // Leaving by the pairing rather than by a side: the train emerges from the partner, having
        // arrived through its portal rather than at any of its edges.
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

        // the neighbor must actually have track on the facing side
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

            // A link switched off is not autonomy's business and every problem here is blocking (see disabledPortals);
            // exits() and the never-paired loop below honor the switch-off too.
            if (portalClosed(from)) continue;

            if (!tiles.containsKey(to))
            {
                // the target is gone, or lives on a page excluded from autonomy
                found.add(new Problem(from,
                    pages.contains(to.getPage()) ? ERROR_PORTAL_UNPAIRED : ERROR_PORTAL_EXCLUDED, true));
                continue;
            }

            // Both ends still have to BE links: a pairing is stored by coordinate, so a far end redrawn as plain track
            // leaves a mutual pairing whose walk jumps into a square with no way out.
            if (!TilePorts.hasPortal(tiles.get(from).getType())
                || !TilePorts.hasPortal(tiles.get(to).getType()))
            {
                found.add(new Problem(from, ERROR_PORTAL_UNPAIRED, true));
                continue;
            }

            if (!from.equals(portals.get(to)))
            {
                found.add(new Problem(from, ERROR_PORTAL_UNPAIRED, true));
            }
        }

        // A link nobody has paired at all never reaches the loop above, which walks pairings: trains reach it and stop,
        // and the page beyond is unreachable.
        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (!TilePorts.hasPortal(entry.getValue().getType())) continue;

            if (portals.containsKey(entry.getKey())) continue;

            if (portalClosed(entry.getKey())) continue;

            // An unpaired link leads nowhere and exits() offers no way through, so track into it simply ends.  Whether
            // that blocks depends on whether a train could ever arrive: a link with no track joined to it is an
            // unfinished intention and costs nothing.
            Side stub = stubSide(entry.getValue());

            boolean reachable = stub != null && landing(entry.getKey(), stub) != null;

            // An arrow to a page autonomy was told to leave out cannot be paired (no tile on that page is in this
            // graph); the only remedy is to disable the arrow.
            boolean unpairable = leadsOutsideAutonomy(entry.getValue());

            // A link track runs into is blocking (OB-150).  Adam: "there should also be an error if there exist ANY
            // active (not excluded/disabled) links not linked to anything."
            //
            // The unreachable case, an arrow drawn on its own with no track joined, stays a warning: a diagram imported
            // from a Central Station carries page-jump arrows nobody has paired.  Three outcomes, each with its own
            // remedy; the unreachable case keeps its own message even when its destination is excluded, since nothing
            // runs into it.
            String key;

            if (!reachable) key = WARN_PORTAL_UNREACHABLE;
            else if (unpairable) key = WARN_PORTAL_EXCLUDED_PAGE;
            else key = WARN_PORTAL_NEVER_PAIRED;

            found.add(new Problem(entry.getKey(), key, reachable && !unpairable));
        }

        // Added once each: two ends of one bad pairing legitimately raise two problems, but the same tile and message
        // twice reads as two things to fix.
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
     * Whether a link points at a page that is not part of autonomy (OB-150).  A link records its destination as a
     * zero-based address over every page, so the page it means is `allPages.get(address)`; if that page was excluded,
     * or the address names no page (a hand-edited file), nothing exists on the far side to pair to.
     *
     * @param link the link tile
     * @return true when the destination page is outside autonomy, so pairing is impossible
     */
    private boolean leadsOutsideAutonomy(LayoutDiagramComponent link)
    {
        if (link == null) return false;

        int address = link.getRawAddress();

        if (address < 0 || address >= allPages.size()) return true;

        return !pages.contains(allPages.get(address));
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

        if (TilePorts.isTransparent(type))
        {
            for (Route route : transparentRoutes(tile))
            {
                out.put(transparentRouteId(route), route);
            }

            return out;
        }

        for (int state = 0; state < TilePorts.getStateCount(type); state++)
        {
            List<Route> routes = routesOn(tile, component, state);

            for (int index = 0; index < routes.size(); index++)
            {
                out.put(new RouteId(state, index), routes.get(index));
            }
        }

        return out;
    }

    // internals

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

    /**
     * What a transparent tile conducts, read off its neighbors.  A route button says nothing about track, so the track
     * beneath it is what the tiles around it imply:
     *
     *   - opposite sides that both face track become a through route (a button dropped into a straight run carries it);
     *   - failing that, exactly two adjacent sides facing track become a corner;
     *   - anything else (nothing beside it, one neighbor, an ambiguous three-way) carries nothing.
     *
     * The user can still author a connection the inference declines; this only decides the seed.
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

        if (out.isEmpty() && facing.size() == 2)
        {
            out.add(new Route(facing.get(0), facing.get(1), null));
        }

        return out;
    }

    /**
     * The real track either side of a route tile, along the roads it carries - where its length is folded to.
     *
     * In the order `transparentRoutes` names the sides, and only squares with rails on them: a route tile beside
     * another route tile passes nothing to it.
     *
     * @param tile the route tile
     * @return the squares beside it along its roads, possibly none
     */
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
     * The sides of a transparent tile that something conducts to (OB-160).  Shared so transparentRoutes and the checks
     * below cannot disagree about what "facing" means.
     *
     * @param tile the transparent square
     * @return the sides, in N E S W order
     */
    private List<Side> facingSides(TileKey tile)
    {
        List<Side> facing = new ArrayList<>();

        for (Side side : Side.values())
        {
            TileKey neighbourKey = tile.beside(side);
            LayoutDiagramComponent neighbourComponent = tiles.get(neighbourKey);

            if (neighbourComponent == null) continue;

            // a transparent neighbor presents a face on every side, which stops two adjacent buttons
            // from each waiting on the other to decide
            if (TilePorts.isTransparent(neighbourComponent.getType())
                || hasPortOn(neighbourComponent, side.opposite()))
            {
                facing.add(side);
            }
        }

        return facing;
    }

    /**
     * Whether the tile on this side of a transparent one is real track rather than another button.
     *
     * @param tile the transparent square
     * @param side which way to look
     * @return true when something with rails on it faces back
     */
    private boolean realTrackOn(TileKey tile, Side side)
    {
        LayoutDiagramComponent neighbourComponent = tiles.get(tile.beside(side));

        return neighbourComponent != null && !TilePorts.isTransparent(neighbourComponent.getType());
    }

    /**
     * Whether the run of buttons this one belongs to meets real track on two different sides (OB-160).  A single button
     * conducts what is beside it; a run of two or more conducts across squares with no rails, which matters only with
     * something to conduct at each end: one end is a siding stub or a panel, two is a splice.  The whole run is walked,
     * so three buttons in a row are caught as readily as two.
     *
     * @param from any button in the run
     * @return true when the run has real track against it on at least two sides
     */
    private boolean runReachesTrackOnBothSides(TileKey from)
    {
        Set<TileKey> run = new LinkedHashSet<>();
        List<TileKey> pending = new ArrayList<>();

        run.add(from);
        pending.add(from);

        boolean adjacent = false;

        // The real faces, as SQUARES rather than as a count: two buttons in a run can each face the
        // same piece of track, and that is one end, not two.
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
     * Route buttons placed where what they conduct is not what was drawn (OB-160).  A second pass, after every page is
     * in `tiles`, because every question here is about neighbors and the per-tile scan cannot see them.
     */
    private void checkTransparentTiles()
    {
        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (!TilePorts.isTransparent(entry.getValue().getType())) continue;

            TileKey tile = entry.getKey();

            List<Side> facing = facingSides(tile);

            // Adjacent buttons that reach track at both ends.  Adam: "Only if they are connected to something else on
            // the graph"; a run touching track at one end connects nothing.  Counted over the whole run, since three in
            // a row is the same fault as two.  A straight north-south beyond the far button presents no face, so it is
            // not a splice.
            if (runReachesTrackOnBothSides(tile))
            {
                problems.add(new Problem(tile, ERROR_ADJACENT_ROUTE_TILES, true));

                continue;
            }

            // And an arm that would be dropped: asked of the routes this tile produces, not the count, so four sides
            // (emitted as two routes, a crossing) stay quiet.
            Set<Side> carried = new LinkedHashSet<>();

            for (Route route : transparentRoutes(tile))
            {
                carried.add(route.getA());
                carried.add(route.getB());
            }

            // Only where something was carried: an empty result is a button that conducts nothing, as one beside the
            // rails does.  A single arm with no opposite face makes no pair, so nothing is dropped.
            if (carried.isEmpty()) continue;

            for (Side side : facing)
            {
                // Only a REAL arm is worth reporting.  Dropping a face that a neighboring button
                // contributed loses no track, and the adjacency error above has already spoken.
                if (!carried.contains(side) && realTrackOn(tile, side))
                {
                    problems.add(new Problem(tile, ERROR_ROUTE_TILE_THREE_WAY, true));

                    break;
                }
            }
        }
    }

    /**
     * The road a route tile's identifier names, as the sides it joins, or null when the identifier is not one (OB-279).
     * The inverse of `transparentRouteId`, kept beside it so they cannot disagree.  A route tile's roads are not in the
     * port map, so indexing it finds nothing.
     *
     * @param id a route identifier
     * @return the road it names, or null for an identifier of an ordinary tile
     */
    public static Route transparentRouteOf(RouteId id)
    {
        if (id == null || id.getState() != 0 || id.getIndex() < 100) return null;

        int code = id.getIndex() - 100;

        Side[] sides = Side.values();

        if (code >= sides.length * sides.length) return null;

        return new Route(sides[code / sides.length], sides[code % sides.length], null);
    }

    /**
     * Transparent routes are not drawn routes, so they are identified by the axis they turned out to
     * carry rather than by an index into the port map.
     */
    private static RouteId transparentRouteId(Route route)
    {
        return new RouteId(0, 100 + route.getA().ordinal() * Side.values().length + route.getB().ordinal());
    }

    /**
     * A turnout the operator has declared permanently set: no address, so it cannot be thrown.  Package-visible because
     * `GraphReducer`'s berth-room walks ask the same question (OB-233); `LayoutDiagramComponent.isSwitch()` names the
     * six throwable types and none of these five.
     *
     * @param type the tile's type
     * @return true for the five CUSTOM_PERM_* types
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
        // transparent routes are not in the port map; they take the default, both ways
        if (routeId.getIndex() >= 100 || routeId.getIndex() < 0) return null;

        List<Route> routes = TilePorts.ports(
            component.getType(), component.getOrientation(), routeId.getState());

        if (routeId.getIndex() < 0 || routeId.getIndex() >= routes.size()) return null;

        return routes.get(routeId.getIndex());
    }

    /**
     * Whether a direction can be chosen for this route at all, given what the hardware allows.  The walk's half is
     * `directionAllows`: an authored direction ANDs with the hardware's restriction, so one the hardware refuses
     * restricts nothing, and offering it is a control for an action the guard will not honor (OB-057, OB-090).
     *
     * On a permanent turnout every route is directed at the toe, so "toward the fork" and "both ways" are not answers.
     * `NONE` is always a real answer.
     *
     * @param direction the answer being considered
     * @param route the route it would be set on
     * @return true when the hardware leaves that answer any meaning
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
     * Whether any train can use this route at all, given the authored direction and the hardware.
     *
     * An answer the hardware refuses is a closure, not a nothing: on a permanent turnout every route is directed at the
     * toe, so `TOWARD_A` (toward the fork) permits only an entry the blades refuse, which is what an operator shutting
     * that arm means.  Reading it as "restricts nothing" would turn a shut road into an open one.
     * `directionIsPossible` answers what the editor should offer; only this says whether a train moves.
     *
     * @param direction what is authored, or the default
     * @param route the route
     * @return true when some entry side is both traversable and allowed
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

    // arms (OB-351)

    /**
     * The answer in force on a road: what is stored, narrowed by the hardware.  A stored answer the editor does not
     * offer on this road (`directionIsPossible`) is either the default `BOTH` on a permanent turnout, which the blades
     * narrow to the one way there is, or one that permits only an entry the blades refuse, a closure.  Everything an
     * ordinary switch or plain track stores is possible there, so it changes nothing on them.
     *
     * @param stored what `getDirection` answers
     * @param route the road
     * @return the direction trains actually have on it
     */
    public static Direction inForce(Direction stored, Route route)
    {
        if (directionIsPossible(stored, route)) return stored;

        return !isPassable(stored, route) ? Direction.NONE
            : directionIsPossible(Direction.TOWARD_A, route) ? Direction.TOWARD_A
            : Direction.TOWARD_B;
    }

    /**
     * The arms of a square - every side any of its roads ends at - in a fixed order, so a mask over them means the same
     * every time it is asked.
     *
     * @param routes the square's roads
     * @return its arms
     */
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
     * The direction a road stores for a set of open arms: both its arms open is both ways, one open is one way toward it,
     * neither is closed - a train leaves the square by an arm exactly when that arm is open.
     *
     * @param route the road
     * @param sides the square's arms, as `armsOf` orders them
     * @param mask the open arms
     * @return the direction
     */
    public static Direction answerFor(Route route, List<Side> sides, int mask)
    {
        boolean openA = (mask & (1 << sides.indexOf(route.getA()))) != 0;
        boolean openB = (mask & (1 << sides.indexOf(route.getB()))) != 0;

        return openA && openB ? Direction.BOTH : openA ? Direction.TOWARD_A
            : openB ? Direction.TOWARD_B : Direction.NONE;
    }

    /**
     * Whether a square's arrows can hide a road's direction from the reader (OB-351): two of its roads end at one side,
     * so that side's arrow is the answer of both (switches, three-ways, double slips, permanent turnouts).  Not half of
     * a two-square crossing, whose tracks meet at the shared side that draws no arrow (OB-320, OB-322).
     *
     * @param tile the square
     * @return true where the square's directions are its arms'
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

    /**
     * The arms a square lets trains leave by, as a mask over `armsOf` its roads: an arm is open where any road through it
     * lets a train out, in force - which is what its arrows draw (`TileAnnotation.paintArrows`).
     *
     * @param tile the square
     * @return the mask
     */
    public int armMask(TileKey tile)
    {
        Map<RouteId, Route> routes = roads(tile);

        return armMask(tile, routes, armsOf(routes.values()));
    }

    /**
     * The same over roads and arms the caller has gathered itself.
     *
     * @param tile the square
     * @param routes the roads to count
     * @param sides the arms the mask is over
     * @return the mask
     */
    public int armMask(TileKey tile, Map<RouteId, Route> routes, List<Side> sides)
    {
        int mask = 0;

        for (Map.Entry<RouteId, Route> entry : routes.entrySet())
        {
            mask |= leavesBy(inForce(getDirection(tile, entry.getKey()), entry.getValue()), entry.getValue(), sides);
        }

        return mask;
    }

    /**
     * The arms open in a mask over `arms`, in words ("N, W").
     *
     * @param mask the mask, as `armMask` answers it
     * @param arms the arms it is over
     * @return the open arms, comma-separated; empty when none is open
     */
    public static String armsInWords(int mask, List<Side> arms)
    {
        List<String> open = new ArrayList<>();

        for (int i = 0; i < arms.size(); i++)
        {
            if ((mask & (1 << i)) != 0) open.add(String.valueOf(arms.get(i)));
        }

        return String.join(", ", open);
    }

    /**
     * The arms of a square with a way through, in the order `armMask` counts them.
     *
     * @param tile the square
     * @return its arms
     */
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
     * Every road of a square given the directions wanted on some of them, as its arms can hold them (OB-351; Adam:
     * *"into spur should simply require S to be green, and out of spur should require W to be green.  Align it so that
     * the arrows tell the full story."*).
     *
     * Each road wanted opens the arms it lets a train leave by and shuts the others it ends at; an arm one road opens
     * and another shuts stays open; arms no wanted road ends at are left as they are.  Each road then takes the answer
     * of its two arms, in force.  A square whose arms are not shared (`sharesAnArm`) takes what was wanted as it is.
     *
     * @param tile the square
     * @param wanted road to the direction wanted on it
     * @return every road wanted, and every other road of the square, to the direction it is to store
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

    /**
     * The squares `normalizeToArms` brought to their arrows when the setup was last applied, each to the roads it changed
     * and their new directions.
     */
    private final Map<TileKey, Map<RouteId, Direction>> normalizedToArms = new LinkedHashMap<>();

    /**
     * Brings every square whose arms are shared to what its arrows show (OB-351): each road takes the answer of its two
     * arms.  A road closed a way its arrow showed open is opened that way; nothing is shut.  A square left at its
     * defaults is never touched.
     *
     * @return each square changed, to the roads changed and their new directions; also kept for `getNormalizedToArms`
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

    /**
     * What the last `normalizeToArms` changed.
     *
     * @return each square changed, to the roads changed and their new directions
     */
    public Map<TileKey, Map<RouteId, Direction>> getNormalizedToArms()
    {
        Map<TileKey, Map<RouteId, Direction>> out = new LinkedHashMap<>();

        for (Map.Entry<TileKey, Map<RouteId, Direction>> each : normalizedToArms.entrySet())
        {
            out.put(each.getKey(), new LinkedHashMap<>(each.getValue()));
        }

        return out;
    }

    // runs (OB-321, OB-361)

    /**
     * One run of plain track: the tiles between two points, in order, and the points at either end.
     */
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

        /**
         * @return the tiles between the two points, in order from start to end
         */
        public List<TileKey> getTiles()
        {
            return Collections.unmodifiableList(tiles);
        }

        /**
         * The tile that speaks for this run - the first of them, as the author asked.
         */
        public TileKey getLeader()
        {
            return tiles.isEmpty() ? null : tiles.get(0);
        }
    }

    /** This diagram's runs, found once: they are the diagram's alone, and a graph is one diagram. */
    private Map<TileKey, Run> runs;

    /**
     * Every run of plain track, keyed by the tile that speaks for it.  A run has one direction, not eleven: setting it
     * tile by tile is busywork and a run that disagrees with itself is a silent trap (the arrows look set and no train
     * can pass), so one tile in each run is set and the rest follow.
     *
     * Computed from the diagram alone (tile types and which sides face which), never from the reduction: edges come and
     * go as directions are set, so a derived run would regroup every time a route closed and the graying would move
     * under the user.  A run tile is plain track: exactly one route through it, not a sensor, not something autonomy
     * ignores; a switch, crossing or sensor ends the run.
     *
     * @return leader tile to the run it leads
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

            // walk out of both ends until the plain track stops
            TileKey endA = walk(chain, tile, route.getA(), seen, false);
            TileKey endB = walk(chain, tile, route.getB(), seen, true);

            out.put(chain.getFirst(), new Run(endA, endB, new ArrayList<>(chain)));
        }

        runs = Collections.unmodifiableMap(out);

        return runs;
    }

    /**
     * The run a square belongs to, or null for a square that is no plain track.
     *
     * @param tile a square
     * @return its run, or null
     */
    public Run runOf(TileKey tile)
    {
        for (Run run : runs().values())
        {
            if (run.getTiles().contains(tile)) return run;
        }

        return null;
    }

    /**
     * Whether a square is a piece of plain track that can belong to a run.
     */
    private boolean isRunTile(TileKey tile)
    {
        LayoutDiagramComponent component = tiles.get(tile);

        if (component == null || component.isFeedback()) return false;

        if (TilePorts.isDisqualified(component.getType())
            || TilePorts.isTransparent(component.getType())) return false;

        if (getRoutes(tile).size() != 1) return false;

        // A stub (an END, a tunnel mouth, a link) has one route whose two sides are the same, so walking through it
        // comes straight back out: not a run tile.
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
     * Extends a chain out of one side of a tile for as long as the track stays plain.
     *
     * @param chain collected so far
     * @param from where to start
     * @param side which way to go
     * @param seen tiles already claimed by a run
     * @param append true to add to the end of the chain, false to add to the front
     * @return the square the run stops at - a switch, a sensor, or null at the end of the track
     */
    private TileKey walk(java.util.LinkedList<TileKey> chain, TileKey from, Side side,
        Set<TileKey> seen, boolean append)
    {
        TileKey here = from;
        Side out = side;

        // bounded because a loop of plain track has no end to reach
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
     * Which ways a train may pass a square of a run, along the run: forward, back, both or neither - or null when the
     * square's way along the run cannot be read.  The hardware's restriction and the user's direction both count, as they
     * do for a train (`exits`).
     */
    private String waysThrough(List<TileKey> run, int at)
    {
        Side[] sides = sidesAlong(run, at);

        if (sides == null) return null;

        return (leavesBy(run.get(at), sides[0], sides[1]) ? "forward" : "")
            + (leavesBy(run.get(at), sides[1], sides[0]) ? "back" : "");
    }

    /**
     * The side a square of a run is entered by going forward along it, and the side it is left by - or null when they
     * cannot be read.
     */
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
     * Which ways trains may run along a run as a whole - {forward, back} along its tiles - or null where a square of it
     * cannot be read.  A train on a run passes every square of it, so it goes a way only where every square lets it: the
     * narrowest of them.
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
     * Which way trains run along a run of plain track, as a direction of its first square's road - or null for a square
     * that leads no run of two or more, or a run that cannot be read (OB-361).  The narrowest of its squares, which is
     * what a train finds and what its first square draws.
     *
     * @param leader a square
     * @return the run's direction, or null
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
     * The runs whose squares point opposite ways - one lets trains along it only forward, another only back - so no train
     * can pass (OB-361).  `normalizeRuns` leaves them as they are: closing them would hide which way was meant behind a
     * setting that looks chosen, so the setup check says so instead.
     *
     * @return each such run, by its leader
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
     * The runs `normalizeRuns` brought to one direction when the setup was last applied, each leader to the squares it
     * changed, their roads and their new directions.
     */
    private final Map<TileKey, Map<TileKey, Map<RouteId, Direction>>> normalizedRuns = new LinkedHashMap<>();

    /**
     * Brings every run of two or more squares to one direction, its narrowest (OB-361; Adam: *"Any disagreement should
     * be corrected so that the viewer displays the source of truth, and the editor flags issues, rather than surprising
     * the user."*): a square that lets trains along it more ways than the run as a whole is given the run's way.  A
     * train passes every square of a run, so no train's behavior changes; every square just says it.  A run whose
     * squares point opposite ways is left as it is (`runsPointingOppositeWays`).
     *
     * @return each run changed, by its leader, to the squares changed; also kept for `getNormalizedRuns`
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

    /**
     * What the last `normalizeRuns` changed.
     *
     * @return each run changed, by its leader, to the squares changed, their roads and their new directions
     */
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

        // A transparent tile presents a face everywhere; what it actually conducts is settled when it is
        // walked, not when a neighbor asks whether it is there
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

    /**
     * Which side of a tile faces one of its neighbors.  Geometric, not routing: the same grid arithmetic as the static
     * `gridSideTowards`, named apart to end the collision between `sideTowards` and `sideToward`.  A portal's partner
     * is a neighbor reached through no side, so callers that care use landing() instead.
     *
     * @param tile
     * @param other
     * @return the side, or null when the two are not adjacent
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
     * A route from one square to another over the track as DRAWN, ignoring which way trains may run: this exists to
     * change directions, so consulting them would let a run already set one way only be re-drawn the same way round.
     *
     * @param from
     * @param to
     * @return the squares from one to the other inclusive, or null when no continuous track joins them
     */
    public List<TileKey> findUndirectedPath(TileKey from, TileKey to)
    {
        if (from == null || to == null || !tiles.containsKey(from) || !tiles.containsKey(to))
        {
            return null;
        }

        if (from.equals(to)) return new ArrayList<>(Collections.singletonList(from));

        // Walked as (square, side it was entered by), not as squares: a square can carry more than one piece of track
        // (a crossing, a double curve), so a walk over squares alone could arrive along one track and leave along the
        // other.  A route is usable only if it touches the entry side, the same rule the reducer follows when it
        // derives edges.
        Map<String, Step> cameFrom = new LinkedHashMap<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        // The first square has no entry side, so every route on it is open: the user picked the square without saying
        // which track they meant.
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
     * one of them.  An old autonomy.json joins each point to the next ones along the track, so this is where one of its
     * edges could lead from a square by that side; a shortest walk to the edge's end cannot answer that, since round a
     * loop the far end is reached both ways.
     *
     * @param from the square
     * @param side the side to leave it by
     * @param stops the squares a walk ends at
     * @return the stops reached, empty when none is or the square has no track by that side
     */
    public Set<TileKey> firstStopsLeaving(TileKey from, Side side, Set<TileKey> stops)
    {
        Set<TileKey> reached = new LinkedHashSet<>();

        if (from == null || side == null || stops == null || !tiles.containsKey(from)) return reached;

        boolean trackThatWay = false;

        for (Route route : getRoutes(from).values())
        {
            if (route.touches(side)) trackThatWay = true;
        }

        Landing first = trackThatWay ? landing(from, side) : null;

        if (first == null || !tiles.containsKey(first.getTile())) return reached;

        Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        frontier.add(new Step(first.getTile(), first.getEntrySide()));

        while (!frontier.isEmpty())
        {
            Step here = frontier.poll();

            if (!seen.add(here.key())) continue;

            // Back at the square it left, round a loop: that says nothing about this side.
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
     * Whether the track leaving a square by one side runs out - at a buffer stop, or where the drawn track simply stops -
     * before it reaches any of some squares, over the track as DRAWN, whichever way trains may run on it (FR-118).  A
     * walk that comes back round to the square it left goes somewhere, so that side is no dead end; nor is a side the
     * square has no track by.
     *
     * @param from the square
     * @param side the side to leave it by
     * @param stops the squares a walk ends at - the sensors
     * @return true when every way that side leads ends in nothing
     */
    public boolean runsOutLeaving(TileKey from, Side side, Set<TileKey> stops)
    {
        if (from == null || side == null || stops == null || !tiles.containsKey(from)) return false;

        boolean trackThatWay = false;

        for (Route route : getRoutes(from).values())
        {
            if (route.touches(side)) trackThatWay = true;
        }

        if (!trackThatWay) return false;

        Landing first = landing(from, side);

        if (first == null || !tiles.containsKey(first.getTile())) return true;

        Set<String> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        frontier.add(new Step(first.getTile(), first.getEntrySide()));

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
     * The squares from one square to another, leaving the first by one side, over the track as DRAWN and never past a
     * stop on the way - `firstStopsLeaving`'s walk, with the route it took (carrying an old file's directions).
     *
     * @param from the square
     * @param side the side to leave it by
     * @param to the stop to reach
     * @param stops the squares a walk ends at
     * @return the squares from `from` to `to` inclusive, or null when `to` is not the first stop that way
     */
    public List<TileKey> pathLeaving(TileKey from, Side side, TileKey to, Set<TileKey> stops)
    {
        if (from == null || side == null || to == null || !tiles.containsKey(from)) return null;

        boolean trackThatWay = false;

        for (Route route : getRoutes(from).values())
        {
            if (route.touches(side)) trackThatWay = true;
        }

        Landing first = trackThatWay ? landing(from, side) : null;

        if (first == null || !tiles.containsKey(first.getTile())) return null;

        Map<String, Step> cameFrom = new HashMap<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        Step start = new Step(first.getTile(), first.getEntrySide());

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

    /**
     * One square of an undirected walk, remembered with the side it was entered by.
     */
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
         * Two arrivals at one square by different sides are different places to be, because different
         * track leads on from each.  Keyed as both, so the walk explores both - and by the track as well where two
         * share the side (OB-320).
         */
        String key()
        {
            return tile + "@" + entrySide + (lane == null ? "" : "/" + lane);
        }
    }

    /**
     * Where a walk that arrived here can go next, over continuous track only.
     */
    private List<Step> continuations(Step here)
    {
        List<Step> out = new ArrayList<>();

        LayoutDiagramComponent component = tiles.get(here.tile);

        if (component == null) return out;

        // A TWO-SQUARE CROSSING A TRACK AT A TIME (OB-320).  Its tracks share the side its squares meet at, so the step
        // onto the other square carries the leg its track leaves that square by, and a step carrying one is held to it -
        // or the walk could arrive along one track and leave along the other, a reverse curve no train can run.
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
            // A route that does not touch the side we arrived by is a different piece of track laid
            // across this square, not a way on from here.
            if (here.entrySide != null && !route.touches(here.entrySide))
            {
                continue;
            }

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

        // A portal's partner is reached through no side, so the loop above cannot find it; the reducer walks through
        // one and derives edges over it, and so must this.  It is entered by no side, so every route on the far square
        // is open, as at the start square.
        TileKey partner = portals.get(here.tile);

        // Not through a link that is switched off: exits() declines to offer a way through one, and a path search that
        // says "reachable" for somewhere no train can get to has something plan a move on it.
        if (partner != null && !portalClosed(here.tile) && tiles.containsKey(partner))
        {
            out.add(new Step(partner, null));
        }

        return out;
    }
}
