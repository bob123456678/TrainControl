package org.traincontrol.automationui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * What a tile is doing, and how that is painted over it.
 *
 * Drawn as a wash rather than by recolouring the tile art, for three reasons that are all about the
 * existing rendering rather than about taste: the icon cache is shared by every tile of a type, so
 * recolouring one would recolour all of them or need a cache entry per state; the icon is only
 * refreshed when its NAME changes, which autonomy state does not do; and the transient yellow highlight
 * already works by swapping the icon out and back, so a second effect doing the same would fight it for
 * the one slot it restores from.
 *
 * Colours are `DiagramColours`', the one place the diagram's autonomy colours are kept (FR-106).
 *
 * @author Adam
 */
public class TileOverlay
{
    /**
     * What is happening on a piece of track.
     */
    public static enum State
    {
        /**
         * A path is claimed over this track and the train has not reached it yet.
         */
        ACTIVE,

        /**
         * The train has passed this point of its path.
         */
        REACHED,

        /**
         * Held clear so another path can run.
         */
        LOCKED,

        /**
         * Nothing is happening here.  Painted as nothing at all - a running layout should show what is
         * moving, not tint every tile it owns.
         */
        IDLE
    }

    private static final Color ACTIVE = DiagramColours.PATH_AHEAD;

    private static final Color REACHED = DiagramColours.PATH_DRIVEN;

    private static final Color LOCKED = DiagramColours.PATH_HELD;

    /**
     * One pass of a running path through this square: in by one side, out by another.
     *
     * A null side is an end of the run - where the train is, or where it is going - so the line stops in
     * the middle of that square rather than running off its edge into track nobody claimed.  It is also
     * what a jump through a link to another page looks like, which has no side on this grid to be drawn
     * as.
     */
    public static class Segment
    {
        private final org.traincontrol.automationui.TilePorts.Side from;
        private final org.traincontrol.automationui.TilePorts.Side to;
        private final State state;

        public Segment(org.traincontrol.automationui.TilePorts.Side from,
            org.traincontrol.automationui.TilePorts.Side to, State state)
        {
            this.from = from;
            this.to = to;
            this.state = state == null ? State.IDLE : state;
        }

        public org.traincontrol.automationui.TilePorts.Side getFrom()
        {
            return from;
        }

        public org.traincontrol.automationui.TilePorts.Side getTo()
        {
            return to;
        }

        public State getState()
        {
            return state;
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof Segment)) return false;

            Segment other = (Segment) o;

            return from == other.from && to == other.to && state == other.state;
        }

        @Override
        public int hashCode()
        {
            return ((from == null ? 0 : from.hashCode()) * 31
                + (to == null ? 0 : to.hashCode())) * 31 + state.hashCode();
        }

        @Override
        public String toString()
        {
            return from + "->" + to + "/" + state;
        }
    }

    /**
     * How heavily a claim is drawn.
     *
     * Neither a wash nor a border, in the end.  The wash covered the tile and hid the arrows drawn on
     * it; the border left them alone but could only say WHERE a path went - not which way it ran, nor
     * which part of it the train had already covered, and on a square two paths crossed it had one
     * border to say both.
     *
     * A line along the track says all of it, and is what the editor already draws for a tested path -
     * so a reader who has used "test a path" has already learnt to read a running layout.  The border
     * survives underneath for claims with no geometry to draw.
     */
    private static final float OUTLINE_ALPHA = 0.95f;

    /**
     * And how heavily a tile merely held clear is drawn.
     *
     * Locked track is not where a train is going - it is track nobody else may use - so it says
     * something worth knowing and should not compete with the paths that are actually running.
     */
    private static final float LOCKED_ALPHA = 0.4f;

    /**
     * How much a square of held track is washed out.
     *
     * Locked track was drawn as a near-white line at low alpha, which on a pale diagram is very nearly
     * nothing: a held square looked like an ordinary one, and the whole point of showing it is that a
     * reader can see which track is spoken for.
     *
     * Lightened rather than coloured in.  It is not somewhere a train is going - it is somewhere
     * nobody else may go - so it should recede from the running path rather than compete with it, and
     * paling the tile says "held" without adding another colour to a diagram that already has four.
     */
    private static final Color LOCKED_WASH = DiagramColours.PATH_HELD_WASH;

    private static final float LOCKED_WASH_ALPHA = DiagramColours.PATH_HELD_WASH_ALPHA;

    private static final float DOT_ALPHA = 0.9f;

    /**
     * The picture drawn where a train is moving (FR-027).
     *
     * **A file, so it can be replaced without touching this.** Adam: "it should also be easy to
     * change/customize the icon.  Write it to the resources folder."  Drop any PNG over
     * `src/org/traincontrol/gui/resources/running_train.png` and that is the new icon - it is scaled to
     * the tile, so the size it is drawn at does not matter, only its shape and its transparency.
     *
     * The one shipped is a side-view locomotive in near-black with a white halo, because it has to read
     * on whatever is underneath it: plain track, the blue of a claimed path, the dark grey of a driven one,
     * the light grey of a locked one. An icon with no halo disappears into the dark colours.
     *
     * If the file is missing or unreadable the dot is drawn instead, which is what was here before this
     * existed - a diagram that stops saying where the trains are would be a worse fault than a plain
     * marker.
     */
    private static final String TRAIN_ICON = "/org/traincontrol/gui/resources/running_train.png";

    /**
     * How much of the tile the icon takes.
     *
     * Not the whole square: the line showing which way the path runs is drawn underneath, and an icon
     * that covers the tile hides the answer to "where is it going" in order to answer "where is it".
     */
    private static final double ICON_SCALE = 0.76;

    /**
     * Whether the icon is only for trains that are moving or drawn standing (FR-027), the dot serving for the rest.  Since
     * OB-317 the run draws every train one or the other - a train standing still on its path is drawn standing - so the
     * dot is left for a picture that cannot be read.
     *
     * Adam's "(not while stationary)". A train with an active path can be sitting still - waiting at a
     * platform, or held while another path clears - and the diagram already says where it is; what it
     * could not say is which of the trains on it are actually running. Set this false and every train
     * autonomy is holding a path for gets the icon, which is the other reasonable reading of the
     * request and is one line away.
     */
    private static final boolean ICON_ONLY_WHILE_MOVING = true;

    /**
     * Whether the icon is turned to face the way the train is going (FR-027).
     *
     * Adam: "make the locomotive face the right direction by rotating it or flipping it."  The way it
     * is going is read off the line already drawn through the square - the same geometry, so the
     * locomotive and the path it is on cannot disagree about which way it is pointing.
     *
     * Westbound is MIRRORED rather than turned through half a circle, because a side view rotated 180
     * degrees is upside down. North and south are quarter turns, which stand the locomotive on end -
     * there is no way to draw a side view running up the page that does not, and standing on end at
     * least says which end is the front.
     *
     * Set false and every locomotive faces the way the file draws it, which is what the first version
     * did.
     */
    private static final boolean ICON_FOLLOWS_TRAVEL = true;

    /**
     * The icon, read once.  Null means "there is none" - either the file is absent or it would not
     * decode - and is remembered, so a missing file is not re-read on every repaint of every tile.
     */
    private static java.awt.image.BufferedImage icon;

    private static boolean iconRead = false;

    private final State state;
    private final boolean train;
    private final boolean moving;
    private final boolean parked;
    private final org.traincontrol.automationui.TilePorts.Side facing;
    private final java.util.List<Segment> segments;

    /** Whether the train here is paused, which draws its icon grey (FR-117). */
    private final boolean paused;

    /**
     * @param state
     * @param train whether the train itself is standing here, which gets a mark of its own
     */
    public TileOverlay(State state, boolean train)
    {
        this(state, train, false, null);
    }

    /**
     * @param state
     * @param train whether the train itself is standing here
     * @param segments which way the path runs through this square, empty when that is not known - which
     *        is what a claim arriving through a link looks like, and what anything reporting only that
     *        the square was claimed hands over
     */
    public TileOverlay(State state, boolean train, java.util.List<Segment> segments)
    {
        this(state, train, false, segments);
    }

    /**
     * @param state
     * @param train whether the train itself is standing here
     * @param moving whether it is actually running rather than standing still (FR-027)
     * @param segments which way the path runs through this square
     */
    public TileOverlay(State state, boolean train, boolean moving, java.util.List<Segment> segments)
    {
        this(state, train, moving, segments, false, null);
    }

    /**
     * A train the railway holds with no path to run, drawn as the locomotive a run draws and facing the way it stands
     * (Adam, 2026-10-01: "when a train is standing somewhere, can we show its locomotive icon on top of the station in
     * the track diagram viewer, while maintaining editability? same icon as when a run is started").
     *
     * And a train standing still on a path it holds - at its start while its route is set, or held on its way - is drawn
     * the same, where it stands (OB-317; Adam, 2026-10-03: "it needs to be added to the starting path / kept where it is
     * standing"): it was the dot, and the locomotive vanished from its station as its run began.  The path line under it
     * says it holds one.
     *
     * @param facing the side of the square its front faces, or null where the railway records none
     * @return the mark
     */
    public static TileOverlay parked(org.traincontrol.automationui.TilePorts.Side facing)
    {
        return new TileOverlay(State.IDLE, true, false, null, true, facing);
    }

    private TileOverlay(State state, boolean train, boolean moving, java.util.List<Segment> segments, boolean parked,
        org.traincontrol.automationui.TilePorts.Side facing)
    {
        this(state, train, moving, segments, parked, facing, false);
    }

    private TileOverlay(State state, boolean train, boolean moving, java.util.List<Segment> segments, boolean parked,
        org.traincontrol.automationui.TilePorts.Side facing, boolean paused)
    {
        this.state = state == null ? State.IDLE : state;

        // Clamped as the rest are: paused is a fact about a train, so never without one
        this.paused = train && paused;
        this.train = train;

        // Clamped, so "moving" cannot be true on a square with no train on it.  The two are one fact
        // told in two halves, and a pair that can contradict is a pair somebody will one day read the
        // wrong half of.
        this.moving = train && moving;

        // Clamped the same way: parked is a train standing with no path, so never without a train or while it runs,
        // and the facing is only ever a parked train's.
        this.parked = train && parked && !this.moving;
        this.facing = this.parked ? facing : null;

        this.segments = segments == null || segments.isEmpty()
            ? java.util.Collections.<Segment>emptyList()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(segments));
    }

    /**
     * @return the passes of a path through this square, in the order they were claimed
     */
    public java.util.List<Segment> getSegments()
    {
        return segments;
    }

    public State getState()
    {
        return state;
    }

    public boolean hasTrain()
    {
        return train;
    }

    /**
     * @return whether the train on this square is actually running (FR-027)
     */
    public boolean isMoving()
    {
        return moving;
    }

    /**
     * @return whether the train here is drawn standing: held by the railway with no path to run (Adam, 2026-10-01), or
     *         standing still on a path it holds (OB-317)
     */
    public boolean isParked()
    {
        return parked;
    }

    /**
     * The same mark, for a train paused or not (FR-117; Adam, 2026-10-09: "On the track diagram viewer, Inactive
     * locomotive icons go from black to gray").
     *
     * @param paused whether the train here is paused
     * @return the mark
     */
    public TileOverlay withPaused(boolean paused)
    {
        return paused == this.paused ? this : new TileOverlay(state, train, moving, segments, parked, facing, paused);
    }

    /**
     * @return whether the train here is paused, which draws its icon grey (FR-117)
     */
    public boolean isPaused()
    {
        return paused;
    }

    /**
     * @return the side a parked train's front faces, or null
     */
    public org.traincontrol.automationui.TilePorts.Side getFacing()
    {
        return facing;
    }

    /**
     * Whether this overlay would paint anything at all.  An idle tile with no train paints nothing, so
     * the common case costs nothing.
     * @return
     */
    public boolean isBlank()
    {
        // The segments count.  equals() grew them so a changed picture is republished; this did not,
        // so an overlay carrying a line with no state reported itself blank and painted nothing while
        // still forcing the repaint.  Nothing emits that pair today - lay() always sets a state - and
        // the first thing that wants a neutral line would have found it silently invisible.
        return state == State.IDLE && !train && segments.isEmpty();
    }

    /**
     * Where two claims meet on one tile, the more urgent one shows.
     *
     * Reached beats active because the train has demonstrably been there; active beats locked because a
     * claimed path is more informative than the fact that something else is being held clear.
     * @param other
     * @return
     */
    public TileOverlay merge(TileOverlay other)
    {
        if (other == null) return this;

        // Both sets of geometry, not just the winner's.  A square a path crosses twice - a switch
        // taken on the way out and again on the way round - is two passes, and drawing one of them
        // shows a route that stops in the middle of a switch.  Identical passes are dropped, which is
        // what two claims over the same track through the same sides look like.
        java.util.List<Segment> both = new java.util.ArrayList<>(segments);

        for (Segment segment : other.segments)
        {
            if (!both.contains(segment)) both.add(segment);
        }

        return new TileOverlay(
            rank(state) >= rank(other.state) ? state : other.state,
            train || other.train, moving || other.moving, both, parked || other.parked,
            facing != null ? facing : other.facing, paused || other.paused);
    }

    /**
     * Turns the graphics so that an icon drawn facing east ends up facing the way the train is going.
     *
     * Called with the origin already at the middle of the icon, so a rotation is about its own centre.
     *
     * @param g the tile's graphics, translated to where the icon goes
     */
    private void turnToTravel(Graphics2D g)
    {
        if (!ICON_FOLLOWS_TRAVEL) return;

        org.traincontrol.automationui.TilePorts.Side heading = headingOf();

        if (heading == null) return;

        switch (heading)
        {
            // Mirrored, not turned half way round: a side view rotated 180 degrees is upside down.
            case W: g.scale(-1, 1); break;

            case N: g.rotate(-Math.PI / 2); break;
            case S: g.rotate(Math.PI / 2); break;

            // E is how the file draws it.
            default: break;
        }
    }

    /**
     * Which way the rail runs under the train on a curve - from the middle of the train's road to the midpoint of the side
     * it is heading for (MT-642) - or null on a straight, a switch or a crossing, where the icon stays square to the tile.
     *
     * A curve on this diagram is the straight line between the midpoints of two neighbouring sides, so its middle is a
     * quarter of the tile in from each, and the line from there to the heading side's midpoint is the rail.  Anything
     * else answers null: a heading along a straight leaves one of the two at zero, and a heading out of a curve by a side
     * it does not have lands anywhere but a quarter across and a quarter down.
     *
     * A RUN THAT ENDS HERE has only the side it came in by, and goes on from it through the middle of its road - which on
     * a curve is towards the curve's other side, not the side opposite its entry that `headingOf` names for a straight.
     *
     * @param trackCentre the middle of the train's road, or null if it is not known
     * @param width the tile width
     * @param height the tile height
     * @return which way the rail runs, as {dx, dy}, or null
     */
    private int[] alongTheCurve(int[] trackCentre, int width, int height)
    {
        if (!ICON_FOLLOWS_TRAVEL || trackCentre == null || trackCentre.length != 2) return null;

        // the side ahead - the way a parked train faces, or the side a run leaves by - or else the side a run that ends
        // here came in by: the segment `headingOf` reads
        org.traincontrol.automationui.TilePorts.Side ahead = parked ? facing : null;
        org.traincontrol.automationui.TilePorts.Side behind = null;

        for (Segment segment : segments)
        {
            if (parked || ahead != null || behind != null) break;

            if (segment.getTo() != null) ahead = segment.getTo();
            else if (segment.getFrom() != null) behind = segment.getFrom();
        }

        int dx, dy;

        if (ahead != null)
        {
            int[] to = TileAnnotation.midpoint(ahead, width, height);

            if (to == null) return null;

            dx = to[0] - trackCentre[0];
            dy = to[1] - trackCentre[1];
        }
        else if (behind != null)
        {
            int[] from = TileAnnotation.midpoint(behind, width, height);

            if (from == null) return null;

            dx = trackCentre[0] - from[0];
            dy = trackCentre[1] - from[1];
        }
        else
        {
            return null;
        }

        if (dx == 0 || dy == 0) return null;

        // a quarter of the tile across and a quarter down, give or take the rounding of halves
        if (Math.abs(4 * Math.abs(dx) - width) > 4 || Math.abs(4 * Math.abs(dy) - height) > 4) return null;

        // THE LINE ITSELF, not the rounded difference: the middle of a curve on a 30-pixel tile is (22,22), not
        // (22.5,22.5), and the difference from there turned the icon several degrees off the rail
        return new int[] {Integer.signum(dx) * width, Integer.signum(dy) * height};
    }

    /**
     * Turns the graphics so that an icon drawn facing east runs along the rail of a curve (MT-642), never upside down:
     * heading left, it is mirrored, as westbound is on a straight, and then turned from west to the rail.
     *
     * @param g the tile's graphics, translated to where the icon goes
     * @param along which way the rail runs, from `alongTheCurve`
     */
    private static void turnAlong(Graphics2D g, int[] along)
    {
        double angle = Math.atan2(along[1], along[0]);

        if (along[0] >= 0)
        {
            g.rotate(angle);

            return;
        }

        g.scale(-1, 1);
        g.rotate(Math.PI - angle);
    }

    /**
     * The sides of this tile the train's own road ends at, as far as this overlay knows them (MT-642): where a running
     * train came in and is heading - the segment `headingOf` reads - or the side a parked train faces.  A double curve is
     * two roads in one tile, and the caller asks its annotation which of them these name
     * (`TileAnnotation.trackCentreOf`), so the icon is drawn on the train's road and not the station's.
     *
     * @return the sides, possibly empty, never null
     */
    public java.util.List<org.traincontrol.automationui.TilePorts.Side> trainSides()
    {
        java.util.List<org.traincontrol.automationui.TilePorts.Side> sides = new java.util.ArrayList<>();

        if (parked)
        {
            if (facing != null) sides.add(facing);

            return sides;
        }

        for (Segment segment : segments)
        {
            if (segment.getFrom() == null && segment.getTo() == null) continue;

            if (segment.getFrom() != null) sides.add(segment.getFrom());

            if (segment.getTo() != null) sides.add(segment.getTo());

            return sides;
        }

        return sides;
    }

    /**
     * Which way the train on this square is going, or null when this square cannot say.
     *
     * Read off the line already drawn through it, so the locomotive and its path cannot disagree.
     * `to` is the side the run leaves by, which is where the train is headed. At the far end of a run
     * there is no `to` - the line stops in the middle of the square - and the answer is then the
     * opposite of the side it came IN by, which is the same direction said backwards.
     *
     * The first segment that can answer, because a square a path crosses twice carries two, and the
     * train is on one of them: a locomotive pointing along one of the two arms of a crossing is right
     * once and forgivable the other time, where no icon at all says nothing either time.
     *
     * @return the side the train is heading towards, or null
     */
    private org.traincontrol.automationui.TilePorts.Side headingOf()
    {
        // A PARKED TRAIN faces the way it stands: a line through its square is another train's path.
        if (parked) return facing;

        for (Segment segment : segments)
        {
            if (segment.getTo() != null) return segment.getTo();

            if (segment.getFrom() != null) return segment.getFrom().rotateClockwise(2);
        }

        return null;
    }

    /** The white halo round the train's icon, worked out once from it (the fifth look proposal). */
    private static java.awt.image.BufferedImage halo;

    /** How far the halo reaches past the icon, as a share of the icon's width. */
    private static final double HALO_REACH = 1.0 / 14;

    /**
     * The icon's silhouette in white, grown by `HALO_REACH` on every side (Adam, 2026-10-09, the diagram look
     * proposals: "Build 2,3,5,6").  Drawn under the icon so it reads as on top of the caption and the badge it stands
     * over; the icon's own thin rim did not.  The icon's size is unchanged - his "C: full size".
     *
     * @param icon the train's icon
     * @return the halo, `reach` pixels larger than the icon on every side
     */
    private static synchronized java.awt.image.BufferedImage haloOf(java.awt.image.BufferedImage icon)
    {
        if (halo != null) return halo;

        int reach = haloReach(icon);
        int w = icon.getWidth(), h = icon.getHeight();

        java.awt.image.BufferedImage grown = new java.awt.image.BufferedImage(w + 2 * reach, h + 2 * reach,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < grown.getHeight(); y++)
        {
            for (int x = 0; x < grown.getWidth(); x++)
            {
                int alpha = 0;

                for (int dy = -reach; dy <= reach && alpha < 255; dy++)
                {
                    for (int dx = -reach; dx <= reach && alpha < 255; dx++)
                    {
                        if (dx * dx + dy * dy > reach * reach) continue;

                        int sx = x - reach + dx, sy = y - reach + dy;

                        if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;

                        alpha = Math.max(alpha, (icon.getRGB(sx, sy) >>> 24) & 0xFF);
                    }
                }

                grown.setRGB(x, y, (alpha << 24) | 0x00FFFFFF);
            }
        }

        halo = grown;

        return halo;
    }

    /** A paused train's icon, worked out once from the train's (FR-117). */
    private static java.awt.image.BufferedImage pausedIcon;

    /** The grey a paused train's black is drawn in - the grey of the empty dash and the barred chevron's family. */
    private static final int PAUSED_GREY = 150;

    /**
     * The train's icon with its black taken to a mid grey and its white left white (FR-117), so its windows and wheels
     * still read: each pixel's brightness laid between `PAUSED_GREY` and white, its alpha kept - so the halo, which is
     * the icon's silhouette, is the same.
     *
     * @param icon the train's icon
     * @return the paused icon
     */
    private static synchronized java.awt.image.BufferedImage pausedIconOf(java.awt.image.BufferedImage icon)
    {
        if (pausedIcon != null) return pausedIcon;

        int w = icon.getWidth(), h = icon.getHeight();

        java.awt.image.BufferedImage grey = new java.awt.image.BufferedImage(w, h,
            java.awt.image.BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int argb = icon.getRGB(x, y);

                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;

                int bright = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);

                int v = PAUSED_GREY + bright * (255 - PAUSED_GREY) / 255;

                grey.setRGB(x, y, (argb & 0xFF000000) | (v << 16) | (v << 8) | v);
            }
        }

        pausedIcon = grey;

        return pausedIcon;
    }

    private static int haloReach(java.awt.image.BufferedImage icon)
    {
        return Math.max(1, (int) Math.round(icon.getWidth() * HALO_REACH));
    }

    /**
     * The locomotive picture, read from the resources folder the first time it is wanted (FR-027).
     *
     * Read once and remembered, INCLUDING the answer "there is none": this is asked while painting a
     * tile, and a missing file re-opened on every repaint of every square would be a real cost for a
     * question whose answer cannot change while the application is running.
     *
     * Nothing is logged if it fails. A replaceable icon is a thing people will replace, and half of
     * them will drop something in that ImageIO cannot read - the diagram falls back to the dot, which
     * says the same thing less prettily, and that is a better answer than a stack trace behind a
     * cosmetic feature.
     *
     * @return the icon, or null if there is not one to be had
     */
    private static java.awt.image.BufferedImage trainIcon()
    {
        if (!iconRead)
        {
            iconRead = true;

            try
            {
                java.net.URL url = TileOverlay.class.getResource(TRAIN_ICON);

                if (url != null) icon = javax.imageio.ImageIO.read(url);
            }
            catch (java.io.IOException | RuntimeException e)
            {
                icon = null;
            }
        }

        return icon;
    }

    private static int rank(State state)
    {
        switch (state)
        {
            case REACHED: return 3;
            case ACTIVE: return 2;
            case LOCKED: return 1;
            default: return 0;
        }
    }

    /**
     * Paints this overlay over a tile that has already drawn itself.
     *
     * @param g the tile's graphics, already translated to its own origin
     * @param width
     * @param height
     */
    public void paint(Graphics2D g, int width, int height)
    {
        paint(g, width, height, null);
    }

    /**
     * The same, told where this tile's track actually runs.
     *
     * @param trackCentre the midpoint of the tile's own two track sides, or null if it is not known
     */
    public void paint(Graphics2D g, int width, int height, int[] trackCentre)
    {
        paint(g, width, height, trackCentre, false);
    }

    /**
     * The same, told whether the tile underneath has already been drawn faint (Adam, MT-375/OB-212).
     *
     * *"I still see two levels of fade when a train locks the same section a second time, i.e. if
     * sending a train to bottommainb with the current bottommaina occupancy.  You can see the
     * difference in the tone of 13,11 and 13,12."*
     *
     * **This is the third wash that can land on one square, and the one he was actually looking at.**
     * `LayoutLabel` fades the tile art where the railway refuses the square; `TileAnnotation` lays one
     * under its arrows, which `OB-212`'s first fix silenced; and this one pales out track an active
     * route is HOLDING.  A square that is both blocked by a standing train and locked by a route got
     * two of them, which is the tone difference he can see between 13,11 and 13,12.
     *
     * **His ruling settles which gives way** (MT-373): *"greyed out means either edge locked or path
     * blocked."*  One grey, for either reason - so the second wash is not drawn, rather than the two
     * being blended into some third tone.
     *
     * @param g where to draw
     * @param width the tile
     * @param height the tile
     * @param trackCentre where this tile's own rails meet, for a run that ends here
     * @param alreadyFaded whether the caller has drawn the WHOLE tile faint already
     */
    public void paint(Graphics2D g, int width, int height, int[] trackCentre, boolean alreadyFaded)
    {
        paint(g, width, height, trackCentre,
            alreadyFaded ? new java.awt.Rectangle(0, 0, width, height) : null);
    }

    /**
     * The same, told exactly WHICH part of the tile is already faint (PRW-C1).
     *
     * A double curve is two roads in one tile with no connection between them, and a train covers one
     * of them - so "is this tile already faded" has no answer, and the boolean form had to pick one.
     * It picked "yes", and the road with no train on it lost the wash a route holding it should have
     * put there.
     *
     * @param g the brush
     * @param width the tile's width
     * @param height its height
     * @param trackCentre where this tile's rails meet, for a run that ends here
     * @param alreadyFaint the region already drawn faint, or null when none of it is
     */
    public void paint(Graphics2D g, int width, int height, int[] trackCentre,
        java.awt.Shape alreadyFaint)
    {
        if (isBlank()) return;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        java.awt.Composite oldComposite = g.getComposite();
        Color oldColor = g.getColor();

        // Restored with the rest.  The outline below sets one, and a caller that hands over a shared
        // Graphics would otherwise find every later line drawn at this width.
        java.awt.Stroke oldStroke = g.getStroke();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // The line where the path is known, the old border where it is not - a claim that
            // arrived through a link has no sides on this grid, and a square lit by nothing at all
            // would read as track that was never claimed.
            Color outline = segments.isEmpty() ? colourOf(state) : null;

            if (!segments.isEmpty()) paintRun(g, width, height, trackCentre, alreadyFaint);

            if (outline != null)
            {
                boolean locked = state == State.LOCKED;

                g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER,
                    locked ? LOCKED_ALPHA : OUTLINE_ALPHA));

                g.setColor(outline);

                // Thinner for locked track, and drawn INSIDE the tile either way: a line on the very
                // edge is shared with the neighbouring square, so two tiles in different states would
                // argue over the same pixels and whichever painted last would win.
                float weight = locked ? 1.6f : 2.6f;

                g.setStroke(new java.awt.BasicStroke(weight, java.awt.BasicStroke.CAP_BUTT,
                    java.awt.BasicStroke.JOIN_MITER));

                int inset = Math.round(weight / 2f);

                g.drawRect(inset, inset,
                    width - 1 - inset * 2, height - 1 - inset * 2);
            }

        }
        finally
        {
            g.setColor(oldColor);
            g.setComposite(oldComposite);
            g.setStroke(oldStroke);

            if (oldHint != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint);
        }
    }

    /**
     * Draws WHERE THE TRAIN IS, on a pass of its own above everything else (OB-159).
     *
     * Adam: "it is a z order issue.  The stations paint over the locomotives."
     *
     * This used to be the last thing {@link #paint} did, which put it inside the tile - and the
     * station captions are separate components in front of every tile, so a caption lying over a
     * platform painted across the locomotive standing on it.  Putting the TILE in front instead is
     * what OB-117 was filed about from the other side: a tile is opaque, so it does not draw a
     * locomotive over a name, it paints the name out and leaves its own background.
     *
     * Swing has one ordering and no notion of layers, so neither component can be in front of the
     * other and be right.  What can be arranged is a third pass, which is what this method is for: the
     * container paints its children in order - tiles, then captions - and then asks every tile for its
     * train.  Both reports come out true at once.
     *
     * Separate from {@link #paint} rather than a flag on it, because the two are drawn at different
     * times onto different Graphics and nothing else about them is shared.
     *
     * @param g the container's graphics, translated to this tile and not clipped to it (MT-642): on a curve the icon
     *     reaches onto the next tiles, along the rail
     * @param width the tile width
     * @param height the tile height
     * @param trackCentre the middle of the train's own road on this tile, or null if it is not known
     */
    public void paintTrain(Graphics2D g, int width, int height, int[] trackCentre)
    {
        if (!train) return;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        java.awt.Composite oldComposite = g.getComposite();
        Color oldColor = g.getColor();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int[] on = middle(trackCentre, width, height);

            // A locomotive where one is actually running (FR-027), the dot everywhere else.
            //
            // Adam: "add little opaque locomotive icon at the s88 where a train is while autonomy
            // is running (not while stationary)."  The dot said WHERE a train was and nothing more;
            // on a layout with several paths out at once, which of them are moving and which are
            // waiting is the thing a glance at the diagram could not answer.
            // And where one stands still (Adam, 2026-10-01), with a path or without (OB-317): the run marks nothing with
            // the dot any more.
            java.awt.image.BufferedImage picture =
                moving || parked || !ICON_ONLY_WHILE_MOVING ? trainIcon() : null;

            // GREY FOR A PAUSED TRAIN (FR-117; Adam, 2026-10-09: "Inactive locomotive icons go from black to gray")
            if (picture != null && paused) picture = pausedIconOf(picture);

            if (picture != null)
            {
                // Opaque, as asked.  The composite is reset because the outline above leaves a
                // partial alpha on the Graphics, and an icon drawn through that is a grey smudge.
                g.setComposite(java.awt.AlphaComposite.SrcOver);

                int side = (int) Math.round(Math.min(width, height) * ICON_SCALE);

                // Never smaller than the dot it replaces: at the smallest tile size a shape scaled
                // by a fraction becomes a few grey pixels, which is less legible than the dot and
                // would make this a regression for anybody running a compact diagram.
                side = Math.max(side, Math.max(6, Math.min(width, height) / 3));

                java.awt.geom.AffineTransform wasAt = g.getTransform();

                // ON A CURVE, ALONG THE RAIL AT FULL SIZE (MT-642, Adam: "the front of the locomotive is cut off on curved
                // tiles.  Rotate the icon to match the angle of the tile so it fits"; then, of the choices drawn for him,
                // "C: full size", and "there should be no clip").  The rail there is the line between two neighbouring
                // sides' midpoints, so the icon is turned along it and reaches past the tile onto the track beyond.
                int[] along = alongTheCurve(trackCentre, width, height);

                try
                {
                    g.translate(on[0], on[1]);

                    if (along != null) turnAlong(g, along);
                    else turnToTravel(g);

                    // The halo first, at the icon's own scale and reaching past it on every side
                    int reach = haloReach(picture);
                    double scale = side / (double) picture.getWidth();
                    int out = (int) Math.round(reach * scale);

                    g.drawImage(haloOf(picture), -side / 2 - out, -side / 2 - out, side + 2 * out, side + 2 * out, null);

                    g.drawImage(picture, -side / 2, -side / 2, side, side, null);
                }
                finally
                {
                    // Restored rather than undone step by step: the caller handed over a Graphics
                    // it goes on using, and a transform left on it moves everything drawn after.
                    g.setTransform(wasAt);
                }
            }
            else
            {
                // An outline says which track is claimed; it cannot say which part of it holds the
                // train.  The dot is the diagram's equivalent of the graph labelling its node.
                int diameter = Math.max(6, Math.min(width, height) / 3);

                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, DOT_ALPHA));
                g.setColor(DiagramColours.TRAIN_DOT);
                g.fillOval(on[0] - diameter / 2, on[1] - diameter / 2, diameter, diameter);

                g.setColor(DiagramColours.TRAIN_DOT_RING);
                g.drawOval(on[0] - diameter / 2, on[1] - diameter / 2, diameter, diameter);
            }
        }
        finally
        {
            g.setColor(oldColor);
            g.setComposite(oldComposite);

            if (oldHint != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint);
        }
    }

    /**
     * Where the middle of this tile IS, for anything that has to sit on the track.
     *
     * The geometric centre is on the rail for a straight and nowhere near it for a curve, where the
     * track cuts the corner.  Two things here need the answer - the end of a run (OB-026) and the dot
     * marking which square holds the train ("stars work, but are offcenter on curve stations") - and
     * they were wrong in the same way for the same reason, so they ask the same question now.
     *
     * @param trackCentre the midpoint of the tile's own two track sides, or null if it is not known
     */
    private static int[] middle(int[] trackCentre, int width, int height)
    {
        return trackCentre != null && trackCentre.length == 2
            ? trackCentre : new int[] {width / 2, height / 2};
    }

    /**
     * The path itself, drawn through the square rather than around it.
     *
     * A border said WHERE a path went and nothing about which way it ran, or which part of it the train
     * had already covered - and on a square two paths crossed, one border had to speak for both.  A
     * line laid along the track answers all three: the stations' blue ahead of the train, dark grey behind
     * it, and a white arrowhead pointing the way it is going (FR-106; `DiagramColours`).
     *
     * The same line the editor draws for a tested path, deliberately.  It is the same question asked at
     * two different times - which way does this route run - so it is worth only learning to read once.
     */
    private void paintRun(Graphics2D g, int width, int height, int[] trackCentre,
        java.awt.Shape alreadyFaint)
    {
        int span = Math.min(width, height);
        // Where a line stops when it has no side to leave by - the END of a run.
        //
        // OB-026: this was always the tile's geometric centre, which is on the rail for a straight and
        // nowhere near it for a curve, where the track cuts the corner and never passes through the
        // middle.  So a train arriving at a curved station drew a stub across the tile instead of along
        // it, while a curve the run passed THROUGH looked right - because that case has two sides and
        // never comes here at all.
        //
        // The caller supplies the midpoint of this tile's own two track sides, which is the tile centre
        // for a straight and lands on the rails for anything else.  That keeps the through-case exactly
        // as it was, which matters: bending the line through the centre was tried once before and put
        // it at forty-five degrees to the track underneath.
        int[] centre = middle(trackCentre, width, height);

        // Held track is paled out first, under everything else.
        //
        // Only where nothing is actually running over this square: a square carrying a live path is
        // described by that path, and washing it as well would say two things about one piece of rail.
        boolean onlyHeld = true;

        for (Segment segment : segments)
        {
            if (segment.getState() != State.LOCKED) onlyHeld = false;
        }

        if (onlyHeld && !segments.isEmpty())
        {
            // WHAT IS LEFT OF THE TILE (PRW-C1).  The whole of it on an ordinary square, which is
            // MT-375's fix unchanged; on a double curve, everything but the road already faded - so
            // the road a route is holding keeps its wash even when the other road carries a tail.
            java.awt.geom.Area rest = new java.awt.geom.Area(
                new java.awt.Rectangle(0, 0, width, height));

            if (alreadyFaint != null) rest.subtract(new java.awt.geom.Area(alreadyFaint));

            if (rest.isEmpty()) return;

            java.awt.Composite before = g.getComposite();
            java.awt.Shape beforeClip = g.getClip();

            g.clip(rest);

            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, LOCKED_WASH_ALPHA));

            g.setColor(LOCKED_WASH);
            g.fillRect(0, 0, width, height);

            g.setComposite(before);
            g.setClip(beforeClip);
        }

        // Track merely held clear is dropped where a path is actually running over the same square.
        // Same rule the merged state follows, and without it the grey line and the coloured one are
        // drawn down the same rails, where the grey reads as a second route going nowhere.
        boolean running = false;

        for (Segment segment : segments)
        {
            if (segment.getState() != State.LOCKED) running = true;
        }

        g.setStroke(new java.awt.BasicStroke(Math.max(3f, span / 7f),
            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

        for (Segment segment : segments)
        {
            boolean locked = segment.getState() == State.LOCKED;

            if (locked && running) continue;

            Color colour = colourOf(segment.getState());

            if (colour == null) continue;

            int[] a = segment.getFrom() == null
                ? centre : TileAnnotation.midpoint(segment.getFrom(), width, height);

            int[] b = segment.getTo() == null
                ? centre : TileAnnotation.midpoint(segment.getTo(), width, height);

            if (a == null || b == null) continue;

            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER,
                locked ? LOCKED_ALPHA : OUTLINE_ALPHA));

            g.setColor(colour);

            // Edge to edge in one stroke, along the rail rather than around it.
            //
            // A curve on this diagram is not an arc and a switch's diverging leg is not a right angle:
            // both are drawn as a straight chord from the midpoint of one edge to the midpoint of the
            // next, which is what TileAnnotation.heading has always taken its arrow directions from.
            // Bending the run line through the tile centre instead put it at forty-five degrees to the
            // track under it - two strokes cutting across the corner the rail cuts through - so on
            // every turn of a route the highlight and the railway disagreed about where the train was
            // going.  Straight through is unchanged by this: the chord and the centre lie on one line.
            g.drawLine(a[0], a[1], b[0], b[1]);
        }

        // Which way, in white with a dark edge (FR-106, RSA25-C4), on the half the train is heading INTO - clear of the
        // centre, where two arrowheads on a square crossed twice would sit on top of each other.
        //
        // Only where there is a direction to state.  Locked track is not somewhere a train is going,
        // it is track nobody else may use, and an arrow on it would claim a journey that is not
        // happening.
        float arrowStroke = Math.max(2f, span / 14f);

        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));

        java.util.List<int[][]> arrows = new java.util.ArrayList<>();

        for (Segment segment : segments)
        {
            // AND A SEGMENT WITH NO COLOUR, which the line pass twenty lines above also skips
            // (W21-C5). `colourOf` answers null for IDLE, so without this an idle segment drew a
            // black arrowhead floating over track with no line under it. Nothing emits that pair
            // today - the only IDLE overlay built anywhere passes null segments - so this is a trap
            // for the next author rather than a defect on screen, which is why `isBlank()` was taught
            // about the same pair and this loop was not.
            if (segment.getState() == State.LOCKED || segment.getTo() == null
                || colourOf(segment.getState()) == null) continue;

            // Along the segment's own chord, for the same reason the line follows it.  An arrowhead
            // squared to the edge on a curve points across the rail it is meant to be running on.
            //
            // Tilted here where the editor's static arrows are not, and the difference is real: those
            // draw one arrowhead per SIDE, shared by every route through it, and two chords meeting at
            // one edge disagree by forty-five degrees, so there is no honest angle to pick.  A run
            // segment is one route, and its heading is not in doubt.
            int[] from = segment.getFrom() == null
                ? centre : TileAnnotation.midpoint(segment.getFrom(), width, height);

            int[] b = TileAnnotation.midpoint(segment.getTo(), width, height);

            if (b != null) arrows.add(new int[][] {from == null ? centre : from, b});
        }

        // THE EDGE FIRST, a little wider, and the white over it: so it reads on a sensor's white contact as on the line
        g.setStroke(new java.awt.BasicStroke(arrowStroke + 2f,
            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(DiagramColours.PATH_ARROW_EDGE);

        for (int[][] arrow : arrows) TileAnnotation.chevron(g, arrow[0], arrow[1], span);

        g.setStroke(new java.awt.BasicStroke(arrowStroke,
            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setColor(DiagramColours.PATH_ARROW);

        for (int[][] arrow : arrows) TileAnnotation.chevron(g, arrow[0], arrow[1], span);
    }

    private static Color colourOf(State state)
    {
        switch (state)
        {
            case ACTIVE: return ACTIVE;
            case REACHED: return REACHED;
            case LOCKED: return LOCKED;
            default: return null;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof TileOverlay)) return false;

        TileOverlay other = (TileOverlay) o;

        // The geometry counts.  A republish is suppressed when the picture has not changed, and a
        // train that has come to claim the same square from a different side is a changed picture.
        // moving counts, for the same reason the geometry does: a republish is suppressed when the
        // picture has not changed, and a train that has just started or just stopped is a changed
        // picture - it is the whole of what this flag draws.
        // And paused (FR-117): a train paused or set going is a changed picture, and nothing else about it changes.
        return state == other.state && train == other.train && moving == other.moving
            && parked == other.parked && facing == other.facing && paused == other.paused
            && segments.equals(other.segments);
    }

    @Override
    public int hashCode()
    {
        return (((((state.hashCode() * 31 + (train ? 1 : 0)) * 31 + (moving ? 1 : 0)) * 31 + (parked ? 1 : 0)) * 31
            + (facing == null ? 0 : facing.hashCode())) * 31 + segments.hashCode()) * 31 + (paused ? 1 : 0);
    }

    @Override
    public String toString()
    {
        return state + (train ? (moving ? "+moving" : parked ? "+parked" + (facing == null ? "" : ":" + facing)
            : "+train") + (paused ? "+paused" : "") : "")
            + (segments.isEmpty() ? "" : segments.toString());
    }
}
