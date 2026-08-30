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
     * routes and each has its own direction. Before this it was the one collection still keyed by a
     * STRING, "page:x,y#state,index", parsed by hand at every site that touched it - and it had four
     * helper methods of its own, duplicating the typed ones branch for branch, because erasure makes
     * {@code Map<String, T>} and {@code Map<TileKey, T>} the same signature so they could not be
     * overloaded.
     *
     * The '#' suffix was got wrong twice while it was a string: once as a `tileDirections.remove(key)`
     * that could never match a suffixed key (DD-A1), and once in `forgetSquares`, which had to grow a
     * loop of its own to handle it.
     *
     * The self type is what lets the bookkeeping helpers - move a square, rename a page, drop a key
     * whose square is gone - be written once over both kinds of key and hand back the kind they were
     * given. Every one of them does the same two things: ask which square a key is on, and produce the
     * same key on a different square.
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
     * FR-013 stage two, replacing the string "page:x,y#state,index" that `tileDirections` was keyed by.
     * A square can carry several routes and each has its own direction, so the square alone is not
     * enough to identify one; the string form is still what goes on disk, and is produced by
     * {@link #toString()} at the boundary rather than assembled and picked apart at each site.
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
         * Assembled here rather than at the call sites, which is half of what stage two buys: the
         * suffix used to be built and parsed in a dozen places, and was got wrong in two of them.
         *
         * Note that RouteId's own toString puts a '#' between its two numbers, which is NOT the
         * separator used here - the stored form has always been "#state,index" with a comma, and
         * changing it would make every setup written before this build unreadable.
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
     * The writer makes both ends match (OB-041). This is the reading half, and it is not redundant -
     * every setup saved before 2026-08-23 has one-ended disables in it, and there is no migration. A
     * reader that asks about both is a repair for those files that costs nothing and needs nobody to
     * run anything. Found by a reviewer reading three days of commits (TD-2), who noticed that the fix
     * had gone in on the writer alone.
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
     * Two route buttons side by side, with real track running into the pair (OB-159).
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
     * Track running into a route button from three sides, one of which is silently dropped (OB-159).
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
     * The route identifier a portal traversal carries.  A portal is not one of the tile's drawn routes,
     * so it needs an identity of its own to hang direction off - and it takes the default, both ways.
     */
    /**
     * The route id a link is traversed under, which is deliberately not one getRoutes will ever return.
     *
     * A link's own route is a STUB - the same side twice - because the tile conducts track on one face
     * and a jump on the other, and a jump is not a side.  getRoutes lists that stub as (0,0); this is
     * (0,-1), so the two never collide and nothing can correlate them by accident.
     *
     * Two things follow, and both are load-bearing:
     *
     * A link cannot carry a direction. Direction here means "toward side A or side B", and a stub has
     * one side, so directionAllows would compare it with itself and answer true whichever way was
     * asked. Consulting it on this branch would look like one-way cross-page running and do nothing -
     * which is worse than not offering it. AutonomyEditorPanel therefore does not offer the four
     * direction answers on a link tile, and testALinkIsNotOfferedADirection pins that: the guard is not
     * tidiness, it is the only thing standing between a user and a setting that silently does nothing.
     *
     * Making a link one-way is a real thing somebody may want, and it needs the JUMP to carry the
     * direction rather than a side of the tile. That is a feature, recorded in the backlog, not
     * something to bolt onto this id.
     */
    private static final RouteId PORTAL_ROUTE = new RouteId(0, -1);

    private final Map<TileKey, LayoutDiagramComponent> tiles = new LinkedHashMap<>();
    private final Map<TileKey, TileKey> portals = new HashMap<>();
    private final Map<TileKey, Map<RouteId, Direction>> directions = new HashMap<>();
    private final Set<String> pages = new LinkedHashSet<>();
    private final List<Problem> problems = new ArrayList<>();

    /**
     * Every page in the layout IN FILE ORDER, excluded ones included.
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

        // AFTER every page is in, because these questions are about neighbours (OB-159).
        checkTransparentTiles();
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

        // A portal tile has two ports and only one of them is a side: the visible one, where it meets
        // ordinary track, and the pairing, which leads to its partner.  The pairing has no direction on
        // the grid, so it is addressed as a null side - arriving by null means arriving through the
        // portal, and leaving by null means taking it.
        //
        // Without this a link is a hole: track can reach it and nothing can leave, because its only
        // route is a stub and the loop below skips stubs.  That severed every cross-page route while
        // looking exactly like a diagram that simply had none.
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
            // raises is BLOCKING.  The field's own javadoc says why: a diagram can carry a link that
            // belongs to the drawing rather than to the railway, and refusing to build until it is
            // paired would be autonomy insisting on something the user has already decided against.
            //
            // exits() honours this, and so does the never-paired loop twenty lines below.  This loop
            // did not - so switching a link off stopped trains going through it and did NOT stop it
            // failing the build, which is the one combination that leaves the user nothing to do.
            if (portalClosed(from)) continue;

            if (!tiles.containsKey(to))
            {
                // the target is gone, or lives on a page excluded from autonomy
                found.add(new Problem(from,
                    pages.contains(to.getPage()) ? ERROR_PORTAL_UNPAIRED : ERROR_PORTAL_EXCLUDED, true));
                continue;
            }

            // Both ends still have to BE links.  A pairing is stored by coordinate and replayed without
            // asking what is there now, so redrawing the far end as plain track left a pairing that is
            // mutual, whose both ends exist, and which every check above is happy with - while the walk
            // jumps into a square that offers no way out and the cross-page route silently disappears.
            // This was the one portal misconfiguration nothing said anything about.
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

        // A link nobody has paired at ALL never reached the loop above, because that walks the
        // pairings rather than the links - so the one case that needs saying most, a link drawn and
        // then forgotten, was the one case nothing said anything about.  It is a hole in the track:
        // trains reach it and stop, and the page it was meant to continue onto is unreachable.
        for (Map.Entry<TileKey, LayoutDiagramComponent> entry : tiles.entrySet())
        {
            if (!TilePorts.hasPortal(entry.getValue().getType())) continue;

            if (portals.containsKey(entry.getKey())) continue;

            if (portalClosed(entry.getKey())) continue;

            // Worth saying, never blocking.  An unpaired link leads nowhere, and exits() already
            // declines to offer a way through one - so the track running into it simply ends, exactly as
            // it would at a blank square, and nothing downstream is left broken or half-built.  Blocking
            // on it meant a diagram imported from a Central Station refused to run autonomy at all until
            // every page-jump arrow drawn on it had been paired or switched off, including the ones
            // pointing at pages the user had deliberately left out.
            //
            // How much it matters still depends on whether a train could ever arrive.  A link with track
            // running into it costs the railway that stretch of track; a link drawn on its own, with
            // nothing joined to it, is somebody's unfinished intention and costs nothing at all.
            Side stub = stubSide(entry.getValue());

            boolean reachable = stub != null && landing(entry.getKey(), stub) != null;

            // AN ARROW TO A PAGE AUTONOMY WAS TOLD TO LEAVE OUT CANNOT BE PAIRED.
            //
            // There is no tile on the other side to pair it to - that page is not in this graph - so
            // reporting it as an error would be reporting a fault with no remedy but to disable the
            // arrow. Excluding the page was already the deliberate act saying autonomy does not go
            // there, and it should not have to be said twice.
            boolean unpairable = leadsOutsideAutonomy(entry.getValue());

            // A LINK TRACK RUNS INTO IS NOW BLOCKING (OB-150).
            //
            // Adam: "there should also be an error if there exist ANY active (not excluded/disabled)
            // links not linked to anything."
            //
            // The comment above argued against blocking, and it was right about the case it described:
            // a diagram imported from a Central Station carries page-jump arrows nobody has paired,
            // and refusing to run until every one of them is dealt with is unusable. That case is the
            // UNREACHABLE one - an arrow drawn on its own, with no track joined to it, costing nothing
            // - and it stays a warning.
            //
            // The reachable one is different in kind. Track runs into that link and stops: trains
            // reach it and have nowhere to go, and the page it was drawn to continue onto cannot be
            // got to at all. That is a hole in the railway rather than an unfinished intention.
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
     * The sides of a transparent tile that something conducts to (OB-159).
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
     * Whether the run of buttons this one belongs to meets real track on two different sides (OB-159).
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
     * Route buttons placed where what they conduct is not what was drawn (OB-159).
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
            // Adam, on the pair this first fired on: "since the two route icons are next to each other
            // but there is no connect to the link nor the straight track, it should not emit an error.
            // There is no ambiguity there."  He is right - past the far button of that pair is a
            // straight lying north-south, which presents no face, so the run reaches track only on one
            // side and splices nothing.  Turn that straight a quarter turn and it becomes a splice
            // across two squares of blank diagram, which is the case this is for.
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
            // what a button beside the rails or at the end of a line IS.  Without this the check fired
            // on three squares of the operator's own railway that are drawn correctly: at 1 - Main:3,5
            // the feedback to the east is rotated a quarter turn, so it presents no face at all and
            // the single western arm was reported as "dropped" by a pair that never existed.
            //
            // Measured before it shipped, by the test that runs these checks over his frozen snapshot.
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
     * Transparent routes are not drawn routes, so they are identified by the axis they turned out to
     * carry rather than by an index into the port map.
     */
    private static RouteId transparentRouteId(Route route)
    {
        return new RouteId(0, 100 + route.getA().ordinal() * 4 + route.getB().ordinal());
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
        // transparent routes are not in the port map; they take the default, both ways
        if (routeId.getIndex() >= 100 || routeId.getIndex() < 0) return null;

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
     * Named apart from the static `gridSideTowards` to end the collision between `sideTowards` and
     * `sideToward`, one letter apart, which is a defect waiting for a tired reader (DD-C9).
     *
     * NOT because they ask different questions, which is what this said until RC-C5. This one walks the
     * private `neighbour` helper, which is the same grid arithmetic - two squares whose coordinates
     * differ by one - so the two answer identically for any pair of squares on one page. The paragraph
     * below, saying a portal is not reachable here, contradicted the claim on its own.
     *
     * A portal's partner is not reachable here: the two squares are neighbours in the graph without
     * touching, so there is no side to name, and callers that care use landing() instead. The javadoc
     * used to claim the opposite of what the code does.
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
        // only "which squares does this one touch" unions the sides of every route on it.  The walk
        // could then arrive along one track and leave along the other, so a one-way run drawn between
        // two points on genuinely separate tracks reported success and restricted stretches of both.
        // applyOneWay could not save it either: at the jump square no single route touches both the
        // side it came from and the side it left by, so that square was skipped in silence.
        //
        // Carrying the entry side means a route is only usable if it touches that side, which is the
        // definition of continuous track and the same rule the reducer follows when it derives edges.
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
     * One square of an undirected walk, remembered with the side it was entered by.
     */
    private static final class Step
    {
        private final TileKey tile;
        private final Side entrySide;

        Step(TileKey tile, Side entrySide)
        {
            this.tile = tile;
            this.entrySide = entrySide;
        }

        /**
         * Two arrivals at one square by different sides are different places to be, because different
         * track leads on from each.  Keyed as both, so the walk explores both.
         */
        String key()
        {
            return tile + "@" + entrySide;
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
        // find it - which made every path search stop dead at a tunnel or link, even though the reducer
        // walks straight through one and derives edges over it.  It is entered by no side either, so
        // every route on the far square is open, exactly as at the square the walk started from.
        TileKey partner = portals.get(here.tile);

        // Not through a link that is switched off.  exits() declines to offer a way through one, so a
        // walk that crossed it anyway believed it could reach pages no train can actually get to - and
        // a path search that answers "reachable" for somewhere unreachable is worse than one that says
        // nothing, because something then plans a move on it.
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
