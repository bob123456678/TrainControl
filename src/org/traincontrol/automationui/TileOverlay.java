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
 * Colours are the graph window's, read from graph.css, so somebody who has learned to read one view has
 * learned the other.
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

    // graph.css: edge.active / node.active
    private static final Color ACTIVE = new Color(196, 0, 0);

    // graph.css: edge.reached / node.reached
    private static final Color REACHED = new Color(0, 196, 33);

    // graph.css: edge.locked
    private static final Color LOCKED = new Color(238, 238, 238);

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
    private static final Color LOCKED_WASH = Color.WHITE;

    private static final float LOCKED_WASH_ALPHA = 0.45f;

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
     * on whatever is underneath it: plain track, a red claimed path, a green reached one, a grey locked
     * one. An icon with no halo disappears into the dark colours.
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
     * Whether the icon is only for trains that are MOVING (FR-027), the dot serving for the rest.
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
    private final java.util.List<Segment> segments;

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
        this.state = state == null ? State.IDLE : state;
        this.train = train;

        // Clamped, so "moving" cannot be true on a square with no train on it.  The two are one fact
        // told in two halves, and a pair that can contradict is a pair somebody will one day read the
        // wrong half of.
        this.moving = train && moving;

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
            train || other.train, moving || other.moving, both);
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
        for (Segment segment : segments)
        {
            if (segment.getTo() != null) return segment.getTo();

            if (segment.getFrom() != null) return segment.getFrom().rotateClockwise(2);
        }

        return null;
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

            if (!segments.isEmpty()) paintRun(g, width, height, trackCentre);

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
     * @param g the container's graphics, already translated and clipped to this tile
     * @param width the tile width
     * @param height the tile height
     * @param trackCentre the midpoint of the tile's own two track sides, or null if it is not known
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
            java.awt.image.BufferedImage picture =
                moving || !ICON_ONLY_WHILE_MOVING ? trainIcon() : null;

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

                try
                {
                    g.translate(on[0], on[1]);

                    turnToTravel(g);

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
                g.setColor(Color.BLACK);
                g.fillOval(on[0] - diameter / 2, on[1] - diameter / 2, diameter, diameter);

                g.setColor(Color.WHITE);
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
     * line laid along the track answers all three: red ahead of the train, green behind it, and a black
     * arrowhead pointing the way it is going.
     *
     * The same line the editor draws for a tested path, deliberately.  It is the same question asked at
     * two different times - which way does this route run - so it is worth only learning to read once.
     */
    private void paintRun(Graphics2D g, int width, int height, int[] trackCentre)
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
            java.awt.Composite before = g.getComposite();

            g.setComposite(java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, LOCKED_WASH_ALPHA));

            g.setColor(LOCKED_WASH);
            g.fillRect(0, 0, width, height);

            g.setComposite(before);
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

        // Which way, in black, on the half the train is heading INTO - clear of the centre, where two
        // arrowheads on a square crossed twice would sit on top of each other.
        //
        // Only where there is a direction to state.  Locked track is not somewhere a train is going,
        // it is track nobody else may use, and an arrow on it would claim a journey that is not
        // happening.
        g.setStroke(new java.awt.BasicStroke(Math.max(2f, span / 14f),
            java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));

        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));
        g.setColor(Color.BLACK);

        for (Segment segment : segments)
        {
            if (segment.getState() == State.LOCKED || segment.getTo() == null) continue;

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

            if (b != null) TileAnnotation.chevron(g, from == null ? centre : from, b, span);
        }
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
        return state == other.state && train == other.train && moving == other.moving
            && segments.equals(other.segments);
    }

    @Override
    public int hashCode()
    {
        return ((state.hashCode() * 31 + (train ? 1 : 0)) * 31 + (moving ? 1 : 0)) * 31
            + segments.hashCode();
    }

    @Override
    public String toString()
    {
        return state + (train ? (moving ? "+moving" : "+train") : "")
            + (segments.isEmpty() ? "" : segments.toString());
    }
}
