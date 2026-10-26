package org.traincontrol.automationui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * What a tile is doing, and how that is painted over it.  Drawn as a wash rather than by recoloring the tile art: the
 * icon cache is shared by every tile of a type, and the yellow highlight already swaps the icon out and back, so a
 * second effect would fight it for the one slot it restores from.  Colors are `DiagramColours`' (FR-106).
 *
 * @author Adam
 */
public class TileOverlay
{
    /** What is happening on a piece of track. */
    public static enum State
    {
        /** A path is claimed over this track and the train has not reached it yet. */
        ACTIVE,

        /** The train has passed this point of its path. */
        REACHED,

        /** Held clear so another path can run. */
        LOCKED,

        /** Nothing is happening here: painted as nothing, so a running layout shows what is moving. */
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

    /** How heavily a claim's line is drawn: a wash hid the arrows, and a border could not say where the path went. */
    private static final float OUTLINE_ALPHA = 0.95f;

    /** And how heavily a tile merely held clear is drawn: it should not compete with the paths that are running. */
    private static final float LOCKED_ALPHA = 0.4f;

    /** How much a square of held track is washed out: lightened, so it recedes from the running path. */
    private static final Color LOCKED_WASH = DiagramColours.PATH_HELD_WASH;

    private static final float LOCKED_WASH_ALPHA = DiagramColours.PATH_HELD_WASH_ALPHA;

    private static final float DOT_ALPHA = 0.9f;

    /**
     * The picture drawn where a train is moving (FR-027).  A file, so it can be replaced: any PNG dropped over
     * `src/org/traincontrol/gui/resources/running_train.png` is scaled to the tile, so only its shape and
     * transparency matter.  If it is missing or unreadable the dot is drawn instead.
     */
    private static final String TRAIN_ICON = "/org/traincontrol/gui/resources/running_train.png";

    /** How much of the tile the icon takes: not all of it, or it hides the line saying where the path is going. */
    private static final double ICON_SCALE = 0.76;

    /** How opaque the icon is drawn, halo and all (FR-130): ten per cent clear, so a caption under it still shows. */
    private static final float ICON_ALPHA = 0.9f;

    /**
     * Whether the icon is only for trains that are moving or drawn standing (FR-027), the dot serving for the rest;
     * since OB-317 the dot is left for a picture that cannot be read.
     */
    private static final boolean ICON_ONLY_WHILE_MOVING = true;

    /**
     * Whether the icon is turned to face the way the train is going (FR-027), read off the line already drawn so the
     * two cannot disagree.  Westbound is MIRRORED, since a side view turned 180 degrees is upside down; north and south
     * are quarter turns.
     */
    private static final boolean ICON_FOLLOWS_TRAVEL = true;

    /**
     * The icon, read once.  Null means there is none (absent or undecodable) and is remembered, so it is not re-read.
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

    /** @param train whether the train itself is standing here, which gets a mark of its own */
    public TileOverlay(State state, boolean train)
    {
        this(state, train, false, null);
    }

    /**
     * @param train whether the train itself is standing here
     * @param segments which way the path runs through this square; empty when unknown (a claim arriving through a link)
     */
    public TileOverlay(State state, boolean train, java.util.List<Segment> segments)
    {
        this(state, train, false, segments);
    }

    /**
     * @param train whether the train itself is standing here
     * @param moving whether it is running rather than standing still (FR-027)
     */
    public TileOverlay(State state, boolean train, boolean moving, java.util.List<Segment> segments)
    {
        this(state, train, moving, segments, false, null);
    }

    /**
     * A train the railway holds with no path to run, drawn as the locomotive a run draws and facing the way it stands.
     * One standing still on a path it holds is drawn the same, where it stands (OB-317).
     *
     * @param facing the side its front faces, or null where the railway records none
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

        // Clamped, so "moving" cannot be true on a square with no train: the two are one fact told in two halves.
        this.moving = train && moving;

        // Clamped the same way: parked is a train standing with no path, so never without a train or while it runs,
        // and the facing is only ever a parked train's.
        this.parked = train && parked && !this.moving;
        this.facing = this.parked ? facing : null;

        this.segments = segments == null || segments.isEmpty()
            ? java.util.Collections.<Segment>emptyList()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(segments));
    }

    /** @return the passes of a path through this square, in the order they were claimed */
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

    /** @return whether the train on this square is actually running (FR-027) */
    public boolean isMoving()
    {
        return moving;
    }

    /** @return whether the train here is drawn standing: held with no path, or still on a path it holds (OB-317) */
    public boolean isParked()
    {
        return parked;
    }

    /** The same mark, for a train paused or not (FR-117): a paused train's icon goes from black to gray. */
    public TileOverlay withPaused(boolean paused)
    {
        return paused == this.paused ? this : new TileOverlay(state, train, moving, segments, parked, facing, paused);
    }

    /** @return whether the train here is paused, which draws its icon gray (FR-117) */
    public boolean isPaused()
    {
        return paused;
    }

    /** @return the side a parked train's front faces, or null */
    public org.traincontrol.automationui.TilePorts.Side getFacing()
    {
        return facing;
    }

    /** Whether this overlay would paint anything at all; an idle tile with no train paints nothing. */
    public boolean isBlank()
    {
        // The segments count: a line with no state must not report blank, or it paints nothing while forcing a repaint.
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
     * Turns the graphics so an icon drawn facing east faces the way the train is going; the origin is the icon's
     * middle.
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
     * side it is heading for (MT-642) - or null on a straight, a switch or a crossing, where the icon stays square.  A
     * curve is the line between the midpoints of two neighboring sides, so its middle is a quarter of the tile in from
     * each.  A run that ENDS here has only the side it came in by and goes on through the middle of its road, towards
     * the curve's other side.
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
     * Turns the graphics so an icon drawn facing east runs along a curve's rail (MT-642), mirrored when heading left.
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
     * The sides of this tile the train's own road ends at (MT-642): where a running train came in and is heading, or
     * the side a parked train faces.  A double curve is two roads in one tile, so the caller asks
     * `TileAnnotation.trackCentreOf` which of them these name.
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
     * Which way the train on this square is going, or null when it cannot say.  Read off the line already drawn through
     * it, so the locomotive and its path cannot disagree: `to` is where the train is headed, and at the far end of a
     * run the opposite of the side it came IN by.  The first segment that can answer wins (a square a path crosses
     * twice carries two).
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

    /** The most the halo reaches past the icon as drawn, as a share of its drawn width (OB-329). */
    private static final double HALO_MOST = 1.0 / 15;

    /**
     * How far the halo reaches past the icon as drawn: `HALO_REACH` of it, at most `HALO_MOST`, never under 2 pixels
     * (OB-329).
     */
    private static int haloOut(java.awt.image.BufferedImage picture, int side)
    {
        int share = (int) Math.round(haloReach(picture) * side / (double) picture.getWidth());

        return Math.min(share, Math.max(2, (int) Math.round(side * HALO_MOST)));
    }

    /**
     * The icon's silhouette in white, grown by `HALO_REACH` on every side and drawn under the icon so it reads on top
     * of the caption and badge it stands over.  The icon's size is unchanged.
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
     * The train's icon with its black taken to mid gray and its white left white (FR-117), so windows and wheels still
     * read.
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
     * The locomotive picture, read from the resources folder the first time it is wanted (FR-027) and remembered
     * INCLUDING the answer "there is none".  Nothing is logged on failure: people will drop in something ImageIO
     * cannot read, and the dot says the same without a stack trace behind a cosmetic feature.
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
     * The part of the square this overlay's run line lies along, for the train's own line to be drawn again over it
     * and nowhere else: over the whole square it would lie twice over itself and come out a second, lighter orange.
     * The whole square for a segment from side to side; for one that ends in the square, the half toward the side it
     * leaves by, cut at the track's center.  A held line is no run line.
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

    /** The same, told the midpoint of the tile's own two track sides (null if unknown). */
    public void paint(Graphics2D g, int width, int height, int[] trackCentre)
    {
        paint(g, width, height, trackCentre, false);
    }

    /**
     * The same, told whether the tile underneath is already faint (MT-375, OB-212).  Up to three washes can land on one
     * square: `LayoutLabel` fades the tile art where the railway refuses it, `TileAnnotation` lays one under its
     * arrows, and this one pales out held track.  Adam's rule (MT-373): *"greyed out means either edge locked or path
     * blocked."*  One gray for either reason, so the second wash is not drawn rather than blended into a third tone.
     *
     * @param alreadyFaded whether the caller has drawn the WHOLE tile faint already
     */
    public void paint(Graphics2D g, int width, int height, int[] trackCentre, boolean alreadyFaded)
    {
        paint(g, width, height, trackCentre,
            alreadyFaded ? new java.awt.Rectangle(0, 0, width, height) : null);
    }

    /**
     * The same, told exactly WHICH part of the tile is already faint: a double curve is two roads in one tile and a
     * train covers one.
     *
     * @param alreadyFaint the region already drawn faint, or null when none of it is
     */
    public void paint(Graphics2D g, int width, int height, int[] trackCentre,
        java.awt.Shape alreadyFaint)
    {
        if (isBlank()) return;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        java.awt.Composite oldComposite = g.getComposite();
        Color oldColor = g.getColor();

        // Restored with the rest: a caller that hands over a shared Graphics would find every later line at this width.
        java.awt.Stroke oldStroke = g.getStroke();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // The line where the path is known, the old border where it is not: a claim arriving through a link has no
            // sides on this grid, and a square lit by nothing would read as never claimed.
            Color outline = segments.isEmpty() ? colourOf(state) : null;

            if (!segments.isEmpty()) paintRun(g, width, height, trackCentre, alreadyFaint);

            if (outline != null)
            {
                boolean locked = state == State.LOCKED;

                g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER,
                    locked ? LOCKED_ALPHA : OUTLINE_ALPHA));

                g.setColor(outline);

                // Thinner for locked track, and drawn INSIDE the tile: a line on the very edge is shared with the
                // neighboring square, and whichever painted last would win.
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
     * Draws WHERE THE TRAIN IS, on a pass of its own above everything else (OB-159).  The station captions are separate
     * components in front of every tile, and a tile in front is opaque and paints the name out (OB-117), so the
     * container paints tiles, then captions, then asks every tile for its train.
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

            // A locomotive where one is running or standing (FR-027, OB-317), the dot everywhere else.
            java.awt.image.BufferedImage picture = drawsTheIcon() ? trainIcon() : null;

            // Gray for a paused train (FR-117).
            if (picture != null && paused) picture = pausedIconOf(picture);

            if (picture != null)
            {
                // Reset because the outline above leaves a partial alpha on the Graphics, and an icon drawn through
                // that is a gray smudge.
                g.setComposite(java.awt.AlphaComposite.SrcOver);

                int side = iconSide(width, height);

                java.awt.geom.AffineTransform wasAt = g.getTransform();

                // On a curve the icon is turned along the rail and drawn at full size, reaching past the tile onto the
                // track beyond, unclipped (MT-642).
                int[] along = alongTheCurve(trackCentre, width, height);

                try
                {
                    g.translate(on[0], on[1]);

                    if (along != null) turnAlong(g, along);
                    else turnToTravel(g);

                    int out = haloOut(picture, side);
                    int whole = side + 2 * out;

                    // HALO AND ICON AS ONE PICTURE, AT 90 PER CENT (FR-130): drawn separately through the alpha, the
                    // icon would lie over the halo's white.  Made in the screen's own pixels, already turned: turned
                    // afterwards it is scaled twice and comes out ragged (MT-642).
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
                    // Restored rather than undone step by step: the caller goes on using this Graphics.
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
     * How wide the locomotive icon is drawn on a tile of this size; never under the dot, since a tiny icon is a few
     * gray pixels.
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
     * Where the train `paintTrain` draws lies, in the tile's coordinates, so a click on it counts as a click on its
     * square (OB-354): a disc as wide as the icon, or the dot, round the middle of the train's road.
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
     * Where the middle of this tile IS, for anything that sits on the track: on a curve the track cuts the corner
     * (OB-026).
     */
    private static int[] middle(int[] trackCentre, int width, int height)
    {
        return trackCentre != null && trackCentre.length == 2
            ? trackCentre : new int[] {width / 2, height / 2};
    }

    /**
     * Washes what is left of the tile once `alreadyFaint` is taken out (MT-375); false when nothing was left to wash.
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
     * The path itself, drawn through the square rather than around it: the stations' blue ahead of the train, dark gray
     * behind it, and a white arrowhead pointing the way it is going (FR-106; `DiagramColours`).
     */
    private void paintRun(Graphics2D g, int width, int height, int[] trackCentre,
        java.awt.Shape alreadyFaint)
    {
        int span = Math.min(width, height);
        // Where a line stops when it has no side to leave by - the END of a run (OB-026).  The caller supplies the
        // midpoint of this tile's own two track sides: the geometric center is nowhere near the track on a curve.
        int[] centre = middle(trackCentre, width, height);

        // Held track is paled out first, under everything, and only where nothing is running over the square, or it
        // would say two things about one rail.
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

        // Track merely held clear is dropped where a path is running over the same square, or the gray line reads as a
        // second route going nowhere.
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

            // Edge to edge in one stroke, along the rail: a curve or a switch's diverging leg is a chord between the
            // midpoints of two edges, and bending through the tile center put it at forty-five degrees to the track.
            g.drawLine(a[0], a[1], b[0], b[1]);
        }

        // Which way, in white with a dark edge (FR-106), on the half the train is heading INTO, clear of the center
        // where two arrowheads would overlap.  Only where there is a direction: locked track is not somewhere to go.
        float arrowStroke = Math.max(2f, span / 14f);

        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));

        java.util.List<int[][]> arrows = new java.util.ArrayList<>();

        for (Segment segment : segments)
        {
            // And a segment with no color, which the line pass skips: `colourOf` answers null for IDLE, so an idle
            // segment would draw an arrowhead over bare track.
            if (segment.getState() == State.LOCKED || segment.getTo() == null
                || colourOf(segment.getState()) == null) continue;

            // Along the segment's own chord, as the line is: an arrowhead squared to the edge on a curve points across
            // the rail.  The editor's static arrows are not tilted: one per SIDE, shared by every route.
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

        // The geometry counts: a republish is suppressed when the picture has not changed, and a train from another
        // side, moving, or paused (FR-117) is a changed picture.
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
