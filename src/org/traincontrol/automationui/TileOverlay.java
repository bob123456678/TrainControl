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

    private static final float DOT_ALPHA = 0.9f;

    private final State state;
    private final boolean train;
    private final java.util.List<Segment> segments;

    /**
     * @param state
     * @param train whether the train itself is standing here, which gets a mark of its own
     */
    public TileOverlay(State state, boolean train)
    {
        this(state, train, null);
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
        this.state = state == null ? State.IDLE : state;
        this.train = train;
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
            train || other.train, both);
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

            if (!segments.isEmpty()) paintRun(g, width, height);

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

            if (train)
            {
                // An outline says which track is claimed; it cannot say which part of it holds the
                // train.  The dot is the diagram's equivalent of the graph labelling its node.
                int diameter = Math.max(6, Math.min(width, height) / 3);

                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, DOT_ALPHA));
                g.setColor(Color.BLACK);
                g.fillOval((width - diameter) / 2, (height - diameter) / 2, diameter, diameter);

                g.setColor(Color.WHITE);
                g.drawOval((width - diameter) / 2, (height - diameter) / 2, diameter, diameter);
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
    private void paintRun(Graphics2D g, int width, int height)
    {
        int span = Math.min(width, height);
        int[] centre = new int[] {width / 2, height / 2};

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

            // Through the centre in two strokes rather than corner to corner, so the line bends where
            // the track bends instead of cutting across it
            g.drawLine(a[0], a[1], centre[0], centre[1]);
            g.drawLine(centre[0], centre[1], b[0], b[1]);
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

            int[] b = TileAnnotation.midpoint(segment.getTo(), width, height);

            if (b != null) TileAnnotation.chevron(g, centre, b, span);
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
        return state == other.state && train == other.train && segments.equals(other.segments);
    }

    @Override
    public int hashCode()
    {
        return (state.hashCode() * 31 + (train ? 1 : 0)) * 31 + segments.hashCode();
    }

    @Override
    public String toString()
    {
        return state + (train ? "+train" : "") + (segments.isEmpty() ? "" : segments.toString());
    }
}
