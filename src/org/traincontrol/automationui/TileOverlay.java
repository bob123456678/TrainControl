package org.traincontrol.automationui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * What a tile is doing, and how that is painted over it.  Drawn as a wash rather than by recoloring the tile art: the
 * icon cache is shared by every tile of a type, the icon is only refreshed when its NAME changes (which autonomy state
 * does not do), and the transient yellow highlight already swaps the icon out and back, so a second effect would fight
 * it for the one slot it restores from.  Colors are `DiagramColours`' (FR-106).
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
     * One pass of a running path through this square: in by one side, out by another.  A null side is an end of the run
     * (where the train is or is going), so the line stops mid-square; it is also what a jump through a link looks like.
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
     * How heavily a claim is drawn.  A wash hid the arrows drawn on the tile and a border could only say WHERE a path
     * went, so a line along the track is drawn, as the editor does for a tested path; the border survives underneath
     * for claims with no geometry to draw.
     */
    private static final float OUTLINE_ALPHA = 0.95f;

    /** And how heavily a tile merely held clear is drawn: it should not compete with the paths that are running. */
    private static final float LOCKED_ALPHA = 0.4f;

    /**
     * How much a square of held track is washed out.  Lightened rather than colored in, so it recedes from the running
     * path and says "held" without adding another color to the diagram; a near-white line at low alpha was nearly
     * invisible on a pale diagram.
     */
    private static final Color LOCKED_WASH = DiagramColours.PATH_HELD_WASH;

    private static final float LOCKED_WASH_ALPHA = DiagramColours.PATH_HELD_WASH_ALPHA;

    private static final float DOT_ALPHA = 0.9f;

    /**
     * The picture drawn where a train is moving (FR-027).  A file, so it can be replaced without touching this: Adam:
     * "it should also be easy to change/customize the icon.  Write it to the resources folder."  Any PNG dropped over
     * `src/org/traincontrol/gui/resources/running_train.png` is scaled to the tile, so only its shape and transparency
     * matter.  The shipped one is near-black with a white halo, to read on every path color.  If the file is missing or
     * unreadable the dot is drawn instead.
     */
    private static final String TRAIN_ICON = "/org/traincontrol/gui/resources/running_train.png";

    /** How much of the tile the icon takes: not all of it, or it hides the line saying where the path is going. */
    private static final double ICON_SCALE = 0.76;

    /** How opaque the icon is drawn, halo and all (FR-130): ten per cent clear, so a caption under it still shows. */
    private static final float ICON_ALPHA = 0.9f;

    /**
     * Whether the icon is only for trains that are moving or drawn standing (FR-027), the dot serving for the rest.
     * Since OB-317 the run draws every train one or the other, so the dot is left for a picture that cannot be read.
     * Set false and every train autonomy holds a path for gets the icon.
     */
    private static final boolean ICON_ONLY_WHILE_MOVING = true;

    /**
     * Whether the icon is turned to face the way the train is going (FR-027): Adam: "make the locomotive face the right
     * direction by rotating it or flipping it."  Read off the line already drawn through the square, so the locomotive
     * and the path cannot disagree.  Westbound is MIRRORED, since a side view turned 180 degrees is upside down; north
     * and south are quarter turns, which stand it on end but still show the front.
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

    /** Whether the train here is paused, which draws its icon gray (FR-117). */
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
     * @param train whether the train itself is standing here
     * @param segments which way the path runs through this square, empty when that is not known (a claim arriving
     *        through a link, or one that reports only that the square was claimed)
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
     * A train the railway holds with no path to run, drawn as the locomotive a run draws and facing the way it stands.
     * A train standing still on a path it holds (at its start while its route is set, or held on its way) is drawn the
     * same, where it stands (OB-317); the path line under it says it holds one.
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
     * @return whether the train here is drawn standing: held by the railway with no path to run, or standing still on a
     *         path it holds (OB-317)
     */
    public boolean isParked()
    {
        return parked;
    }

    /** The same mark, for a train paused or not (FR-117): a paused train's icon goes from black to gray. */
    public TileOverlay withPaused(boolean paused)
    {
        return paused == this.paused ? this : new TileOverlay(state, train, moving, segments, parked, facing, paused);
    }

    /**
     * @return whether the train here is paused, which draws its icon gray (FR-117)
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

    /** Whether this overlay would paint anything at all; an idle tile with no train paints nothing. */
    public boolean isBlank()
    {
        // The segments count: an overlay carrying a line with no state must not report itself blank, or it paints
        // nothing while still forcing the repaint.
        return state == State.IDLE && !train && segments.isEmpty();
    }

    /**
     * Where two claims meet on one tile, the more urgent one shows: reached beats active (the train has demonstrably
     * been there), active beats locked (a claimed path says more than something held clear).
     */
    public TileOverlay merge(TileOverlay other)
    {
        if (other == null) return this;

        // Both sets of geometry, not just the winner's: a square a path crosses twice is two passes, and drawing one
        // shows a route that stops mid-switch.  Identical passes are dropped.
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
     * Turns the graphics so that an icon drawn facing east faces the way the train is going; the origin is already at
     * the middle of the icon.
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
     * Which way the rail runs under the train on a curve - from the middle of the train's road to the midpoint of the
     * side it is heading for (MT-642) - or null on a straight, a switch or a crossing, where the icon stays square to
     * the tile.  A curve here is the line between the midpoints of two neighboring sides, so its middle is a quarter of
     * the tile in from each.  A RUN THAT ENDS HERE has only the side it came in by and goes on through the middle of
     * its road, which on a curve is towards the curve's other side, not the opposite one `headingOf` names for a
     * straight.
     *
     * @param trackCentre the middle of the train's road, or null if it is not known
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
     * train came in and is heading, or the side a parked train faces.  A double curve is two roads in one tile, so the
     * caller asks its annotation which of them these name (`TileAnnotation.trackCentreOf`).
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
     * Which way the train on this square is going, or null when this square cannot say.  Read off the line already
     * drawn through it, so the locomotive and its path cannot disagree: `to` is where the train is headed, and at the
     * far end of a run, with no `to`, the opposite of the side it came IN by.  The first segment that can answer,
     * because a square a path crosses twice carries two and a locomotive along either arm is better than no icon.
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
     * The most the halo reaches past the icon as drawn, as a share of its drawn width (OB-329): leaves the small size
     * as it was and takes a pixel off the large.
     */
    private static final double HALO_MOST = 1.0 / 15;

    /**
     * How far the halo reaches past the icon as drawn: its share of the icon (`HALO_REACH`), but no more than
     * `HALO_MOST` of it and never less than two pixels - two at the small size, three at the large where it was four
     * (OB-329).
     *
     * @return the halo's reach, in pixels
     */
    private static int haloOut(java.awt.image.BufferedImage picture, int side)
    {
        int share = (int) Math.round(haloReach(picture) * side / (double) picture.getWidth());

        return Math.min(share, Math.max(2, (int) Math.round(side * HALO_MOST)));
    }

    /**
     * The icon's silhouette in white, grown by `HALO_REACH` on every side, drawn under the icon so it reads as on top
     * of the caption and the badge it stands over (the icon's own thin rim did not).  The icon's size is unchanged.
     *
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

    /** The gray a paused train's black is drawn in - the gray of the empty dash and the barred chevron's family. */
    private static final int PAUSED_GREY = 150;

    /**
     * The train's icon with its black taken to a mid gray and its white left white (FR-117), so its windows and wheels
     * still read: each pixel's brightness laid between `PAUSED_GREY` and white, its alpha kept so the halo is the same.
     *
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
     * The locomotive picture, read from the resources folder the first time it is wanted (FR-027).  Remembered
     * INCLUDING the answer "there is none", since a missing file re-opened on every repaint of every square would be a
     * real cost.  Nothing is logged on failure: people will drop in something ImageIO cannot read, and the dot says the
     * same thing less prettily but better than a stack trace behind a cosmetic feature.
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
     * The part of the square this overlay's run line lies along, for the train's own line to be drawn again over it and
     * nowhere else: over the whole square it would lie twice over itself where no run line was under it (a standing
     * train's own square, the part of a run's first or last square the run does not reach) and come out a second,
     * lighter orange.  The whole square for a segment from side to side; for one that ends in the square, the half
     * toward the side it leaves by, cut at the track's center.  A held line is no run line.
     *
     * @param trackCentre where the track's center is, or null for the square's
     * @return the area, or null where there is no run line
     */
    public java.awt.Shape runArea(int width, int height, int[] trackCentre)
    {
        int cx = trackCentre != null ? trackCentre[0] : width / 2;
        int cy = trackCentre != null ? trackCentre[1] : height / 2;

        java.awt.geom.Area area = new java.awt.geom.Area();

        for (Segment segment : segments)
        {
            if (segment.getState() != State.ACTIVE && segment.getState() != State.REACHED) continue;

            if (segment.getFrom() != null && segment.getTo() != null) return new java.awt.Rectangle(0, 0, width, height);

            for (org.traincontrol.automationui.TilePorts.Side side
                : new org.traincontrol.automationui.TilePorts.Side[] {segment.getFrom(), segment.getTo()})
            {
                if (side == null) continue;

                switch (side)
                {
                    case N: area.add(new java.awt.geom.Area(new java.awt.Rectangle(0, 0, width, cy))); break;
                    case S: area.add(new java.awt.geom.Area(new java.awt.Rectangle(0, cy, width, height - cy))); break;
                    case E: area.add(new java.awt.geom.Area(new java.awt.Rectangle(cx, 0, width - cx, height))); break;
                    case W: area.add(new java.awt.geom.Area(new java.awt.Rectangle(0, 0, cx, height))); break;
                    default: break;
                }
            }
        }

        return area.isEmpty() ? null : area;
    }

    /** Paints this overlay over a tile that has already drawn itself; g is already translated to its own origin. */
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
     * The same, told whether the tile underneath has already been drawn faint (MT-375, OB-212).  Up to three washes can
     * land on one square: `LayoutLabel` fades the tile art where the railway refuses it, `TileAnnotation` lays one
     * under its arrows, and this one pales out track an active route is HOLDING.  Adam's ruling (MT-373): *"greyed out
     * means either edge locked or path blocked."*  One gray for either reason, so the second wash is not drawn rather
     * than the two blended into a third tone.
     *
     * @param trackCentre where this tile's own rails meet, for a run that ends here
     * @param alreadyFaded whether the caller has drawn the WHOLE tile faint already
     */
    public void paint(Graphics2D g, int width, int height, int[] trackCentre, boolean alreadyFaded)
    {
        paint(g, width, height, trackCentre,
            alreadyFaded ? new java.awt.Rectangle(0, 0, width, height) : null);
    }

    /**
     * The same, told exactly WHICH part of the tile is already faint: a double curve is two roads in one tile and a
     * train covers one, so the boolean form, which had to answer "yes", cost the road with no train its wash.
     *
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
                // edge is shared with the neighboring square, so two tiles in different states would
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
     * Draws WHERE THE TRAIN IS, on a pass of its own above everything else (OB-159).  Adam: "it is a z order issue.
     * The stations paint over the locomotives."  The station captions are separate components in front of every tile,
     * so drawn inside {@link #paint} a caption lying over a platform painted across the locomotive; but a tile in front
     * is opaque and paints the name out (OB-117).  So the container paints tiles, then captions, then asks every tile
     * for its train.
     *
     * @param g the container's graphics, translated to this tile and not clipped to it (MT-642): on a curve the icon
     *     reaches onto the next tiles, along the rail
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

            // A locomotive where one is actually running (FR-027), the dot everywhere else.  Adam: "add little opaque
            // locomotive icon at the s88 where a train is while autonomy is running (not while stationary)."  And where
            // one stands still, with a path or without (OB-317): the run marks nothing with the dot any more.
            java.awt.image.BufferedImage picture = drawsTheIcon() ? trainIcon() : null;

            // GRAY FOR A PAUSED TRAIN (FR-117; Adam, 2026-10-09: "Inactive locomotive icons go from black to gray")
            if (picture != null && paused) picture = pausedIconOf(picture);

            if (picture != null)
            {
                // The composite is reset because the outline above leaves a partial alpha on the Graphics, and an icon
                // drawn through that is a gray smudge; the icon's own alpha is set below, once it is one picture.
                g.setComposite(java.awt.AlphaComposite.SrcOver);

                int side = iconSide(width, height);

                java.awt.geom.AffineTransform wasAt = g.getTransform();

                // ON A CURVE, ALONG THE RAIL AT FULL SIZE (MT-642): the rail there is the line between two neighboring
                // sides' midpoints, so the icon is turned along it and reaches past the tile onto the track beyond,
                // unclipped.
                int[] along = alongTheCurve(trackCentre, width, height);

                try
                {
                    g.translate(on[0], on[1]);

                    if (along != null) turnAlong(g, along);
                    else turnToTravel(g);

                    // The halo first, at the icon's own scale and reaching past it on every side
                    int out = haloOut(picture, side);
                    int whole = side + 2 * out;

                    // HALO AND ICON AS ONE PICTURE, AT 90 PER CENT (FR-130): each drawn through the alpha on its own,
                    // the icon would lie over the halo's white and let only a hundredth of the label through.  Made in
                    // the screen's own pixels, already turned: made upright and turned afterwards, it is scaled twice
                    // and comes out ragged on a curve (MT-642).
                    java.awt.geom.AffineTransform turned = g.getTransform();

                    java.awt.Rectangle box = turned.createTransformedShape(
                        new java.awt.Rectangle(-side / 2 - out, -side / 2 - out, whole, whole)).getBounds();

                    java.awt.image.BufferedImage locomotive = new java.awt.image.BufferedImage(box.width, box.height,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);

                    java.awt.Graphics2D lg = locomotive.createGraphics();

                    try
                    {
                        lg.setRenderingHints(g.getRenderingHints());
                        lg.translate(-box.x, -box.y);
                        lg.transform(turned);
                        lg.drawImage(haloOf(picture), -side / 2 - out, -side / 2 - out, whole, whole, null);
                        lg.drawImage(picture, -side / 2, -side / 2, side, side, null);
                    }
                    finally
                    {
                        lg.dispose();
                    }

                    g.setTransform(new java.awt.geom.AffineTransform());
                    g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, ICON_ALPHA));
                    g.drawImage(locomotive, box.x, box.y, null);
                }
                finally
                {
                    // Restored rather than undone step by step: the caller handed over a Graphics
                    // it goes on using, and a transform or alpha left on it changes everything drawn after.
                    g.setTransform(wasAt);
                    g.setComposite(java.awt.AlphaComposite.SrcOver);
                }
            }
            else
            {
                // An outline says which track is claimed; it cannot say which part of it holds the
                // train.  The dot is the diagram's equivalent of the graph labeling its node.
                int diameter = dotDiameter(width, height);

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

    /** Whether the train is drawn as the locomotive icon rather than the dot. */
    private boolean drawsTheIcon()
    {
        return moving || parked || !ICON_ONLY_WHILE_MOVING;
    }

    /**
     * How wide the locomotive icon is drawn on a tile of this size.  Never smaller than the dot it replaces: at the
     * smallest size a scaled shape becomes a few gray pixels.
     */
    private static int iconSide(int width, int height)
    {
        return Math.max((int) Math.round(Math.min(width, height) * ICON_SCALE), dotDiameter(width, height));
    }

    /** How wide the dot is drawn on a tile of this size. */
    private static int dotDiameter(int width, int height)
    {
        return Math.max(6, Math.min(width, height) / 3);
    }

    /**
     * Where the train `paintTrain` draws lies, in the tile's coordinates, so a click on it can be taken as a click on
     * its square (OB-354): a disc as wide as the icon, or the dot, round the middle of the train's road; on a curve it
     * may reach past the tile.
     *
     * @return the area, or null when no train is drawn here
     */
    public java.awt.Shape trainArea(int width, int height, int[] trackCentre)
    {
        if (!train) return null;

        int[] on = middle(trackCentre, width, height);

        int side = drawsTheIcon() ? iconSide(width, height) : dotDiameter(width, height);

        return new java.awt.geom.Ellipse2D.Double(on[0] - side / 2.0, on[1] - side / 2.0, side, side);
    }

    /**
     * Where the middle of this tile IS, for anything that has to sit on the track: the geometric center is on the rail
     * for a straight and nowhere near it for a curve, where the track cuts the corner.  The end of a run (OB-026) and
     * the dot marking which square holds the train ask the same question.
     *
     * @param trackCentre the midpoint of the tile's own two track sides, or null if it is not known
     */
    private static int[] middle(int[] trackCentre, int width, int height)
    {
        return trackCentre != null && trackCentre.length == 2
            ? trackCentre : new int[] {width / 2, height / 2};
    }

    /**
     * Washes what is left of the tile once `alreadyFaint` is taken out (MT-375): nothing on an ordinary square the
     * caller already drew faint, and on a double curve the road a train does not cover.  The annotation's wash too.
     * @return false when nothing was left to wash
     */
    static boolean washWhatIsLeft(Graphics2D g, int width, int height, java.awt.Shape alreadyFaint, float alpha,
        Color color)
    {
        java.awt.geom.Area rest = new java.awt.geom.Area(new java.awt.Rectangle(0, 0, width, height));

        if (alreadyFaint != null) rest.subtract(new java.awt.geom.Area(alreadyFaint));

        if (rest.isEmpty()) return false;

        java.awt.Composite before = g.getComposite();
        java.awt.Shape beforeClip = g.getClip();

        g.clip(rest);

        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, alpha));
        g.setColor(color);
        g.fillRect(0, 0, width, height);

        g.setComposite(before);
        g.setClip(beforeClip);

        return true;
    }

    /**
     * The path itself, drawn through the square rather than around it: a border could not say which way it ran or what
     * the train had covered.  The stations' blue ahead of the train, dark gray behind it, and a white arrowhead
     * pointing the way it is going (FR-106; `DiagramColours`) - the same line the editor draws for a tested path.
     */
    private void paintRun(Graphics2D g, int width, int height, int[] trackCentre,
        java.awt.Shape alreadyFaint)
    {
        int span = Math.min(width, height);
        // Where a line stops when it has no side to leave by - the END of a run (OB-026).  The caller supplies the
        // midpoint of this tile's own two track sides: the tile center for a straight, on the rails for anything else.
        // The geometric center is nowhere near the track on a curve, so a train arriving at a curved station drew a
        // stub across the tile; bending the through-line through the center put it at forty-five degrees to the track.
        int[] centre = middle(trackCentre, width, height);

        // Held track is paled out first, under everything else, and only where nothing is actually running over the
        // square: a live path describes it, and washing it as well would say two things about one piece of rail.
        boolean onlyHeld = true;

        for (Segment segment : segments)
        {
            if (segment.getState() != State.LOCKED) onlyHeld = false;
        }

        if (onlyHeld && !segments.isEmpty())
        {
            // On a double curve the road a route is holding keeps its wash even when the other road carries a tail.
            if (!washWhatIsLeft(g, width, height, alreadyFaint, LOCKED_WASH_ALPHA, LOCKED_WASH)) return;
        }

        // Track merely held clear is dropped where a path is actually running over the same square.
        // Same rule the merged state follows, and without it the gray line and the colored one are
        // drawn down the same rails, where the gray reads as a second route going nowhere.
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

            // Edge to edge in one stroke, along the rail rather than around it: a curve or a switch's diverging leg is
            // drawn as a chord between the midpoints of two edges (as TileAnnotation.heading takes its arrow directions
            // from), and bending the line through the tile center put it at forty-five degrees to the track under it.
            g.drawLine(a[0], a[1], b[0], b[1]);
        }

        // Which way, in white with a dark edge (FR-106), on the half the train is heading INTO - clear of the center,
        // where two arrowheads on a square crossed twice would sit on top of each other.  Only where there is a
        // direction to state: locked track is not somewhere a train is going.
        float arrowStroke = Math.max(2f, span / 14f);

        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));

        java.util.List<int[][]> arrows = new java.util.ArrayList<>();

        for (Segment segment : segments)
        {
            // AND A SEGMENT WITH NO COLOR, which the line pass above also skips: `colourOf` answers null for IDLE, so
            // an idle segment would draw a black arrowhead floating over track with no line under it.
            if (segment.getState() == State.LOCKED || segment.getTo() == null
                || colourOf(segment.getState()) == null) continue;

            // Along the segment's own chord, for the same reason the line follows it: an arrowhead squared to the edge
            // on a curve points across the rail.  The editor's static arrows are not tilted, since they are one per
            // SIDE, shared by every route, and two chords meeting at one edge disagree by forty-five degrees.
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

        // The geometry counts: a republish is suppressed when the picture has not changed, and a train claiming the
        // same square from a different side, one that has just started or stopped (moving), or one paused or set going
        // (FR-117), is a changed picture.
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
