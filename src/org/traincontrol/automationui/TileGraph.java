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
     * Something a setup collection is keyed by, which is always a square and sometimes more.
     *
     * FR-013 stage two. Ten of the eleven store collections are keyed by a bare {@link TileKey};
     * `tileDirections` is keyed by a square AND a route across it, because a square can carry several
     * routes and each has its own direction.  Both are typed keys and never the string
     * "page:x,y#state,index" (DD-A1): erasure makes {@code Map<String, T>} and {@code Map<TileKey, T>}
     * the same signature, and a suffix built or parsed by hand is easy to get wrong.
     *
     * The self type lets the bookkeeping helpers - move a square, rename a page, drop a key whose square
     * is gone - be written once over both kinds of key and hand back the kind they were given. Each asks
     * which square a key is on, and produces the same key on a different square.
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
     * Which side of one square faces another, or null when they are not neighbours.
     *
     * Null is a real answer and not a failure: a path can step from one page to another through a link,
     * and that jump has no side on the grid to draw it as.  A line through the square simply stops in
     * the middle, which is what a train leaving the drawn track actually does.
     *
     * Here rather than in either caller because both the tested path in the editor and the running path
     * on the diagram have to lay a line across the same grid, and two copies of this would be two
     * chances to disagree about which way is north.
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
     * A square together with one route across it - what a recorded direction belongs to.
     *
     * FR-013 stage two. A square can carry several routes and each has its own direction, so the square
     * alone is not enough to identify one; the string form "page:x,y#state,index" is what goes on disk,
     * and is produced by {@link #toString()} at the boundary.
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
         * Assembled here rather than at the call sites, so the suffix is built in one place.
         *
         * Note that RouteId's own toString puts a '#' between its two numbers, which is NOT the
         * separator used here - the stored form is "#state,index" with a comma, and changing it would
         * make every setup written before this build unreadable.
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
     * A link pointing at a page that is not part of autonomy (OB-150).
     *
     * Its own message rather than a quieter WARN_PORTAL_NEVER_PAIRED, because that one tells the reader
     * to "right-click it to pair it" - the single thing that cannot work here. There is no tile on the
     * far side to pair it with, the page having been left out, so the remedy is to include the page or
     * switch the link off.
     */
    public static final String WARN_PORTAL_EXCLUDED_PAGE = "autosetup.ui.warnLinkToExcludedPage";

    /**
     * Links the user has told autonomy to leave alone.
     *
     * A diagram can carry a link that belongs to the drawing rather than to the railway autonomy runs -
     * a jump to a page that is only ever driven by hand, say - and refusing to build until it is paired
     * would be autonomy insisting on something the user has already decided against.
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
     * Whether this doorway is shut - asked at EITHER end.
     *
     * A pair of links is one doorway with an end in two places, and autonomy walks through it in both
     * directions, so a doorway shut at one end and open at the other is not half shut: it is a route
     * that exists going one way and not the other.
     *
     * The writer makes both ends match (OB-041). This is the reading half, and it is not redundant:
     * setups saved before 2026-08-23 can hold one-ended disables and there is no migration, so asking
     * about both ends repairs those files at no cost (TD-2).
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
     * A switch or signal that must be commanded to be passed, drawn without an address behind it.
     *
     * Blocking, by ruling: a diagram used for autonomy should not contain unmapped switches.  Routing
     * over one would mean trusting it to already be lying the right way, which is the danger
     * CUSTOM_PERM_* exists to declare - and here nobody declared it.
     *
     * **This only ever fires when autonomy is being built.**  A track diagram may contain address-less
     * tiles quite legitimately and go on working as a diagram: the application already tolerates them,
     * skipping them when it wires accessories.  Nothing on the display or control path constructs a
     * TileGraph, so drawing an unaddressed switch costs the user nothing until they ask autonomy to make
     * a graph out of that diagram.  Keep it that way - this check must not migrate into layout parsing
     * or into anything that runs on load.  A page that carries such tiles deliberately can be excluded
     * from autonomy instead.
     */
    public static final String ERROR_NO_ADDRESS = "autosetup.ui.errorTileHasNoAddress";

    /**
     * Two route buttons side by side, with real track running into the pair (OB-160).
     *
     * A button carries no track; it conducts whatever is beside it.  Two of them beside each other
     * each count the OTHER as a side worth conducting to, so the pair can splice two lines together
     * through a length of diagram that has no rails on it at all.
     *
     * Only when the pair touches real track: two buttons alone in a corner of the page conduct
     * nothing and are a control panel, which is what they are for.
     */
    public static final String ERROR_ADJACENT_ROUTE_TILES = "autosetup.ui.errorAdjacentRouteTiles";

    /**
     * Track running into a route button from three sides, one of which is silently dropped (OB-160).
     *
     * transparentRoutes joins N to S and E to W where both face; with three sides facing, the pair
     * wins and the odd arm is discarded - so a line drawn into the side of a button on a running line
     * is severed with nothing said about it.
     *
     * Ambiguous rather than wrong: a crossing carries two routes, so three arms cannot be one, and
     * there is no honest guess for this to make.  Four IS a crossing and is not reported.
     */
    public static final String ERROR_ROUTE_TILE_THREE_WAY = "autosetup.ui.errorRouteTileThreeWay";

    /**
     * The route id a link is traversed under, which is deliberately not one getRoutes will ever return.
     *
     * A link's own route is a STUB - the same side twice - because the tile conducts track on one face
     * and a jump on the other, and a jump is not a side.  getRoutes lists that stub as (0,0); this is
     * (0,-1), so the two never collide and nothing can correlate them by accident.
     *
     * A link cannot carry a direction. Direction here means "toward side A or side B", and a stub has
     * one side, so directionAllows would compare it with itself and answer true whichever way was
     * asked. AutonomyEditorPanel therefore does not offer the four direction answers on a link tile,
     * and testALinkIsNotOfferedADirection pins that: the guard is the only thing standing between a
     * user and a setting that silently does nothing.  A one-way link would need the JUMP to carry the
     * direction rather than a side of the tile.
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
     * Every page in the layout IN THE ORDER THE WINDOW LISTS THEM, excluded ones included.
     *
     * Which is sorted by NAME, not file order (NSV-C5).  TrainControl builds this list from
     * getLayoutList(), which sorts - and the distinction is the whole reason the list exists, because a
     * link tile indexes into it.
     *
     * A link tile records where it goes as a raw address counting from zero, so the page it means is
     * this list's nth entry - the same arithmetic the tooltip does when it prints a destination. It has
     * to hold the excluded pages too, because the whole question this answers is whether an arrow
     * points at a page autonomy was told to leave out, and `pages` above has already dropped those.
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
            // BEFORE the exclusion, because a link's destination is an index into every page rather
            // than into the kept ones, and skipping here would shift every address after the first
            // excluded page.
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

        // AFTER every page is in, because these questions are about neighbours (OB-160).
        checkTransparentTiles();

        // And so is this one (OB-320)
        pairTheCrossings();
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

        // A crossing runs both ways until somebody says otherwise, and a two-square one is a crossing (OB-320)
        if (crossingPartners.containsKey(tile)) return Direction.BOTH;

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

        // A portal tile has two ports and only one of them is a side: the visible one, where it meets
        // ordinary track, and the pairing, which leads to its partner.  The pairing has no direction on
        // the grid, so it is addressed as a null side - arriving by null means arriving through the
        // portal, and leaving by null means taking it.
        //
        // Without this a link is a hole: track can reach it and nothing can leave, because its only
        // route is a stub and the loop below skips stubs.
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
     * The moves out of a square on the track a train arrived along (OB-320).
     *
     * The same as `exits(tile, entrySide)` everywhere but one place: a square of a two-square crossing, entered by the
     * side its two squares meet at.  Both of the crossing's tracks pass that side, so the side alone cannot say which one
     * the train is on, and the answer offers the other track's leg as well - a reverse curve no train can run.  The square
     * the train was on before, and its road over it, say which.
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
     * The leg a two-square crossing's track leaves a square by, for a train that came onto it from the other square
     * (OB-320): the side opposite the leg it came onto the other square by, since each track runs corner to corner.  Null
     * anywhere else - the square is not half of a crossing, or the train did not come from its other half.
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
     * Two permanent turnouts drawn toe to toe.  The diagram has no diagonal crossing, so one is drawn as the
     * two halves of an X: the two turnouts' four legs are its four ends, and each track runs from a leg of
     * one square, over the toes, to the opposite leg of the other.  As turnouts they could only be trailed
     * into their toes, so nothing passed them: a train trailing into one toe met the other head on.
     *
     * Two Ys, or two lefts or two rights turned half round from each other - where every leg faces the
     * opposite leg of the other, so that a left's or a right's straight legs make a straight track through
     * both toes and its diverging legs the diagonal.  Only toe to toe, and only beside each other on one
     * page.  A permanent turnout without such a partner - alone, toe to a leg, a left with a right, a
     * three-way - is the turnout with no address it always was (Adam: *"those without another adjacent to
     * form a crossing are treated as switches only crossable in one direction by autonomy"*).
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
     * Makes the two halves of each two-square crossing's tracks agree where the directions applied to them do not
     * (RSA37-C1).
     *
     * Every door sets a crossing's track on both its squares (OB-320), so halves that disagree hold answers set while the
     * squares were permanent turnouts: in a setup saved before the change, or on a turnout given its partner since.  Each
     * was read in the crossing's terms on its own square, so the blades' own way became a one-way track, and a turnout's
     * closure a one-way track the other way.  They are read as a turnout's instead, the same on both halves: towards the
     * toe is the way the blades run, which restricted nothing, so the track runs both ways; towards a leg is how a
     * permanent turnout was closed (VD18-B1), and closed is closed, so the track is closed if either half was.
     *
     * The graph's directions only - the setup keeps what was set, which means the same again should the squares ever be
     * turnouts again, and the first edit of the track sets both halves anew.
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
     * towards a leg - which, against blades that run only into the toe, let nothing through (RSA37-C1).
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

            TileKey other = neighbour(entry.getKey(), toe);

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

        // Leaving by the pairing rather than by a side: the train emerges from the partner, having
        // arrived through its portal rather than at any of its edges.
        if (exitSide == null)
        {
            TileKey partner = portals.get(tile);

            if (partner == null || !tiles.containsKey(partner)) return null;

            return new Landing(partner, null);
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

            // A link the user has switched off is not autonomy's business, and every problem this loop
            // raises is BLOCKING (see disabledPortals).  exits() and the never-paired loop below honour
            // the switch-off too: a link switched off must neither carry trains nor fail the build.
            if (portalClosed(from)) continue;

            if (!tiles.containsKey(to))
            {
                // the target is gone, or lives on a page excluded from autonomy
                found.add(new Problem(from,
                    pages.contains(to.getPage()) ? ERROR_PORTAL_UNPAIRED : ERROR_PORTAL_EXCLUDED, true));
                continue;
            }

            // Both ends still have to BE links.  A pairing is stored by coordinate and replayed without
            // asking what is there now, so a far end redrawn as plain track leaves a pairing that is mutual
            // with both ends present, while the walk jumps into a square that offers no way out and the
            // cross-page route silently disappears.
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

        // A link nobody has paired at ALL never reaches the loop above, which walks the pairings rather
        // than the links.  It is a hole in the track: trains reach it and stop, and the page it was meant
        // to continue onto is unreachable.
        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (!TilePorts.hasPortal(entry.getValue().getType())) continue;

            if (portals.containsKey(entry.getKey())) continue;

            if (portalClosed(entry.getKey())) continue;

            // An unpaired link leads nowhere, and exits() declines to offer a way through one, so the
            // track running into it simply ends, as it would at a blank square.
            //
            // Whether that blocks depends on whether a train could ever arrive.  A link with track
            // running into it costs the railway that stretch of track; a link drawn on its own, with
            // nothing joined to it, is somebody's unfinished intention and costs nothing at all.
            Side stub = stubSide(entry.getValue());

            boolean reachable = stub != null && landing(entry.getKey(), stub) != null;

            // AN ARROW TO A PAGE AUTONOMY WAS TOLD TO LEAVE OUT CANNOT BE PAIRED: that page is not in this
            // graph, so there is no tile to pair it to and the only remedy is to disable the arrow.
            // Excluding the page was already the deliberate act saying autonomy does not go there, and
            // it should not have to be said twice.
            boolean unpairable = leadsOutsideAutonomy(entry.getValue());

            // A LINK TRACK RUNS INTO IS BLOCKING (OB-150).
            //
            // Adam: "there should also be an error if there exist ANY active (not excluded/disabled)
            // links not linked to anything."
            //
            // The UNREACHABLE case, an arrow drawn on its own with no track joined to it, costs nothing
            // and stays a warning: a diagram imported from a Central Station carries page-jump arrows
            // nobody has paired.  The reachable one is a hole in the railway rather than an unfinished
            // intention: trains reach it and have nowhere to go, and the page it was drawn to continue
            // onto cannot be got to at all.
            //
            // Three outcomes, and each says a different thing to do.  The unreachable case keeps its
            // own message even when its destination is excluded: nothing runs into it, which is both
            // the more useful fact and the reason it costs nothing either way.
            String key;

            if (!reachable) key = WARN_PORTAL_UNREACHABLE;
            else if (unpairable) key = WARN_PORTAL_EXCLUDED_PAGE;
            else key = WARN_PORTAL_NEVER_PAIRED;

            found.add(new Problem(entry.getKey(), key, reachable && !unpairable));
        }

        // Added once each.  Two ends of one bad pairing legitimately produce two problems, but the
        // same tile and the same message twice is one problem reported twice - which reads as two
        // things to fix and cannot be, since fixing it makes both disappear.
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
     * Whether a link points at a page that is not part of autonomy (OB-150).
     *
     * A link records its destination as a raw address counting from zero over EVERY page, so the page
     * it means is `allPages.get(address)`. If that page was excluded - or the address does not name a
     * page at all, which a hand-edited file can manage - then nothing on the far side exists to pair
     * this to, and no amount of work by the user would produce a pairing.
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
     * What a transparent tile conducts, read off its neighbours.
     *
     * A route button is a control someone put on the diagram; it says nothing about track.  So the track
     * beneath it is whatever the tiles around it imply:
     *
     *   - opposite sides that both face track become a through route.  A button dropped into a straight
     *     run carries that run, which is how layouts actually use them;
     *   - failing that, exactly two adjacent sides facing track become a corner, so a button on a curve
     *     works rather than silently breaking the curve;
     *   - anything else - nothing beside it, one neighbour only, or an ambiguous three-way - carries
     *     nothing, which is the honest answer for a button placed beside the rails.
     *
     * The user can still author a connection the inference declines to make; this only decides the seed.
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
                if (realTrackOn(tile, side) && !out.contains(neighbour(tile, side))) out.add(neighbour(tile, side));
            }
        }

        return out;
    }

    /**
     * The sides of a transparent tile that something conducts to (OB-160).
     *
     * Extracted so that transparentRoutes and the checks below cannot disagree about what "facing"
     * means.  Written out twice they drift, and a check that disagrees with the thing it is checking
     * is worse than no check at all.
     *
     * @param tile the transparent square
     * @return the sides, in N E S W order
     */
    private List<Side> facingSides(TileKey tile)
    {
        List<Side> facing = new ArrayList<>();

        for (Side side : Side.values())
        {
            TileKey neighbourKey = neighbour(tile, side);
            LayoutDiagramComponent neighbourComponent = tiles.get(neighbourKey);

            if (neighbourComponent == null) continue;

            // a transparent neighbour presents a face on every side, which stops two adjacent buttons
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
        LayoutDiagramComponent neighbourComponent = tiles.get(neighbour(tile, side));

        return neighbourComponent != null && !TilePorts.isTransparent(neighbourComponent.getType());
    }

    /**
     * Whether the run of buttons this one belongs to meets real track on two different sides (OB-160).
     *
     * A single button conducts what is beside it, which is ordinary and correct.  A RUN of them - two
     * or more touching - conducts across squares that have no rails at all, and that only matters when
     * there is something to conduct at each end.  One end is a siding stub or a panel beside the
     * track; two is a splice.
     *
     * The whole run is walked rather than this tile's neighbours, so that three buttons in a row with
     * track at each end is caught as readily as two.
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
                TileKey neighbourKey = neighbour(tile, side);

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
     * Route buttons placed where what they conduct is not what was drawn (OB-160).
     *
     * A SECOND PASS, after every page is in `tiles`.  The per-tile scan that raises the other problems
     * runs while the map is still being filled, so a tile there cannot see its neighbours - and every
     * question here is about neighbours.
     */
    private void checkTransparentTiles()
    {
        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (!TilePorts.isTransparent(entry.getValue().getType())) continue;

            TileKey tile = entry.getKey();

            List<Side> facing = facingSides(tile);

            // ADJACENT BUTTONS THAT REACH TRACK AT BOTH ENDS (Adam).
            //
            // "Only if they are connected to something else on the graph" - and a run of buttons
            // touching track at ONE end connects nothing to anything: a route through them needs two
            // ends.  Counted over the whole RUN rather than this tile, because three in a row with
            // track at each end is the same fault as two.
            //
            // A straight lying north-south beyond the far button presents no face, so a run that reaches
            // track only there is not a splice.
            if (runReachesTrackOnBothSides(tile))
            {
                problems.add(new Problem(tile, ERROR_ADJACENT_ROUTE_TILES, true));

                continue;
            }

            // AND AN ARM THAT WOULD BE DROPPED.
            //
            // Asked of the routes this tile actually produces rather than of the count, so it reports
            // the case that loses something and stays quiet about the one that does not: four sides
            // are emitted as two routes, which is a crossing and is what a crossing should be.
            Set<Side> carried = new LinkedHashSet<>();

            for (Route route : transparentRoutes(tile))
            {
                carried.add(route.getA());
                carried.add(route.getB());
            }

            // AND ONLY WHERE SOMETHING WAS ACTUALLY CARRIED.
            //
            // An empty result is not a dropped arm - it is a button that conducts nothing, which is
            // what a button beside the rails or at the end of a line IS.  A single arm with no face
            // opposite it (the neighbour there is rotated so it presents none) makes no pair, so nothing
            // is carried and nothing is dropped.
            if (carried.isEmpty()) continue;

            for (Side side : facing)
            {
                // Only a REAL arm is worth reporting.  Dropping a face that a neighbouring button
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
     * The road a route tile's identifier names, as the sides it joins - or null when the identifier is not one
     * (OB-279).
     *
     * The inverse of `transparentRouteId`, kept beside it so the two cannot come to disagree.  A route tile's roads
     * are not in the port map - they are whatever the track beside it carries - so a reader that turns a `RouteId`
     * into a line by indexing the port map finds nothing there.
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
     * A turnout the operator has declared permanently set: no address, so it cannot be thrown.
     *
     * Package-visible because `GraphReducer`'s berth-room walks have to ask the same question (OB-233):
     * `LayoutDiagramComponent.isSwitch()` names the six throwable types and none of these five.
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
     * Whether a direction can be CHOSEN for this route at all, given what the hardware allows.
     *
     * The walk's half of this question is `directionAllows` below: an authored direction ANDs with the
     * hardware's own restriction, so a direction the hardware refuses restricts nothing - choosing it
     * changes no train's road.  Offering it anyway is a control that offers an action the guard will
     * not honour, which is the shape OB-057 and OB-090 are about.
     *
     * A permanent turnout is the case that matters: its routes are all directed at the toe, so "toward
     * the fork" and "both ways" are not answers, and the menu should not draw a green arrow for a road
     * no train can take.  `NONE` is always a real answer - shutting a route is
     * meaningful whichever way it could be travelled.
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
     * Whether any train can use this route at all, given the authored direction AND the hardware.
     *
     * **AN ANSWER THE HARDWARE REFUSES IS A CLOSURE, NOT A NOTHING (VD18-B1).**  On a permanent
     * turnout every route is directed at the toe, so `TOWARD_A` - toward the fork - permits only an
     * entry the blades refuse: nothing can pass, which is precisely what an operator who shuts that
     * arm means.  Reading the answer as "the hardware refuses it, so it restricts nothing" would rewrite
     * it into the one way that IS open, turning a shut road into an open one.
     *
     * `directionIsPossible` above answers a different question - what the editor should OFFER - and
     * only this one says whether a train moves.
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
     * The answer in force on a road: what is stored, narrowed by the hardware (VD18-B1, VD18-C2).
     *
     * A stored answer the editor does not offer on this road (`directionIsPossible`) is either the default `BOTH` on a
     * permanent turnout - whose roads are all directed at the toe, so the blades narrow it to the one way there is - or an
     * answer that permits only an entry the blades refuse, which is a closure.  Every answer an ordinary switch or plain
     * track stores is possible there, so this changes nothing on them.
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
     * Whether a square's arrows can hide a road's direction from the reader (OB-351): two of its roads end at one side, so
     * that side's arrow is the answer of both.  Switches, three-ways, double slips and permanent turnouts.
     *
     * Not half of a two-square crossing: its two tracks meet at the side the squares share, which draws no arrow, and each
     * track's direction is drawn by its two outer ends, one on each square (OB-320, OB-322).
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

        List<Side> sides = armsOf(routes.values());

        int mask = 0;

        for (Map.Entry<RouteId, Route> entry : routes.entrySet())
        {
            mask |= leavesBy(inForce(getDirection(tile, entry.getKey()), entry.getValue()), entry.getValue(), sides);
        }

        return mask;
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
     * Every road of a square given the directions wanted on some of them, as its arms can hold them (OB-351; Adam: *"into
     * spur should simply require S to be green, and out of spur should require W to be green.  Align it so that the arrows
     * tell the full story."*).
     *
     * Each road wanted opens the arms it lets a train leave by and shuts the others it ends at; an arm one road opens and
     * another shuts stays open, and the arms no wanted road ends at are left as they are.  So making a road one way
     * shuts the arm it may no longer leave by for every road through that arm, and opening a road opens its arms for all
     * of them.  Each road then takes the answer of its two arms, in force - an answer the hardware leaves no meaning is
     * stored as the one it means.  A square whose arms are not shared (`sharesAnArm`) takes what was wanted as it is.
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
     * Brings every square whose arms are shared to what its arrows show (OB-351): each road the answer of its two arms,
     * the arms open where a road lets a train out.  A road closed a way its arrow showed open - a switch's spur shut while
     * its main line ran both ways - is opened that way, which is what the diagram drew; nothing is shut.  A square left
     * at its defaults is never touched: each kind's default is an answer of its arms.
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
     * Every run of plain track, keyed by the tile that speaks for it.
     *
     * A run of straight track has one direction, not eleven: setting it tile by tile is busywork, and
     * a run that disagrees with itself is a silent trap - the arrows look set and no train can pass.
     * So one tile in each run is the one to set, and the rest follow it.
     *
     * Computed from the DIAGRAM ALONE - tile types and which sides face which - and never from the
     * reduction.  That is the whole point: edges come and go as directions are set, so a run derived
     * from them would regroup itself every time somebody closed a route, and the greying would move
     * around under the user.  What is grey is a property of the track, not of the settings on it.
     *
     * A run tile is a piece of plain track: exactly one route through it, not a sensor, and not
     * something autonomy ignores.  Anything else - a switch, a crossing, a sensor - ends the run,
     * because each of those is a decision in its own right.
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

        // A stub - an END, a tunnel mouth, a link - has one route whose two sides are the same, so
        // walking "through" it comes straight back out the way it went in.  It is not a run tile: a
        // run that disagreed with itself is the trap runs exist to prevent.
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
     * Brings every run of two or more squares to one direction, its narrowest (OB-361; Adam, on OB-321: *"Any
     * disagreement should be corrected so that the viewer displays the source of truth, and the editor flags issues,
     * rather than surprising the user."*): a square that lets trains along it more ways than the run as a whole does is
     * given the run's way.  A train passes every square of a run, so nothing any train does changes; what changes is that
     * every square says it.  A run whose squares point opposite ways is left as it is (`runsPointingOppositeWays`).
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
        // walked, not when a neighbour asks whether it is there
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
     * Which side of a tile faces one of its neighbours.
     *
     * Geometric, not routing: it answers "which way is that square from this one".
     *
     * It walks the private `neighbour` helper, the same grid arithmetic as the static `gridSideTowards` (two
     * squares whose coordinates differ by one), so the two answer identically for any pair of squares on one
     * page (RC-C5).  They are named apart to end the collision between `sideTowards` and `sideToward`, one
     * letter apart (DD-C9).
     *
     * A portal's partner is not reachable here: the two squares are neighbours in the graph without
     * touching, so there is no side to name, and callers that care use landing() instead.
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
            if (other.equals(neighbour(tile, side))) return side;
        }

        // A portal's partner is a neighbour reached through no side at all, so there is no side to
        // name for it; callers that care about portals use landing() instead.
        return null;
    }

    /**
     * A route from one square to another over the track as DRAWN, ignoring which way trains may run.
     *
     * Deliberately blind to direction: this exists to change directions, so consulting them would mean
     * a run that has already been set one way could only ever be re-drawn the same way round.
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

        // Walked as (square, side it was entered by), not as squares.
        //
        // A square can carry more than one piece of track - a crossing, a double curve - and asking
        // only "which squares does this one touch" unions the sides of every route on it, so the walk
        // could arrive along one track and leave along the other.  Carrying the entry side means a route
        // is only usable if it touches that side, which is the definition of continuous track and the
        // same rule the reducer follows when it derives edges.
        Map<String, Step> cameFrom = new LinkedHashMap<>();
        java.util.ArrayDeque<Step> frontier = new java.util.ArrayDeque<>();

        // The first square has no entry side, so every route on it is open - which is right: the user
        // picked that square and has not said which of its tracks they meant.
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
     * The first of some squares reached by leaving a square through one side, over the track as DRAWN - whichever way
     * trains may run on it - and never past one of them (REG4-A1).
     *
     * An old autonomy.json joins each of its points to the next ones along the track, so this is where one of its edges
     * could lead from a square by that side.  A shortest walk to the edge's end cannot answer that: round a loop the far
     * end is reached both ways, and the shorter may be the way the train never went.
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

        // A portal's partner is a neighbour reached through no side at all, so the loop above cannot
        // find it; the reducer walks straight through one and derives edges over it, and so must this.
        // It is entered by no side either, so every route on the far square is open, exactly as at the
        // square the walk started from.
        TileKey partner = portals.get(here.tile);

        // Not through a link that is switched off.  exits() declines to offer a way through one, and a
        // path search that answers "reachable" for somewhere no train can get to has something plan a
        // move on it.
        if (partner != null && !portalClosed(here.tile) && tiles.containsKey(partner))
        {
            out.add(new Step(partner, null));
        }

        return out;
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
