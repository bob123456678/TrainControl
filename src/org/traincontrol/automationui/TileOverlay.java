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
     * How heavily the outline is drawn.
     *
     * An OUTLINE rather than a wash.  The wash covered the tile, and the tile is where the arrows are
     * - the ones that say which way a train may travel through this square - so the one view that
     * matters most while trains are moving was the one view that hid them.  Outlining says exactly the
     * same thing about which track is claimed and leaves everything underneath legible.
     *
     * It is also what the editor already does to show a tested path, in orange, so a reader who has
     * used "test a path" has already learned to read this.
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

    /**
     * @param state
     * @param train whether the train itself is standing here, which gets a mark of its own
     */
    public TileOverlay(State state, boolean train)
    {
        this.state = state == null ? State.IDLE : state;
        this.train = train;
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
        return state == State.IDLE && !train;
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

        return new TileOverlay(
            rank(state) >= rank(other.state) ? state : other.state,
            train || other.train);
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

            Color outline = colourOf(state);

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

        return state == other.state && train == other.train;
    }

    @Override
    public int hashCode()
    {
        return state.hashCode() * 31 + (train ? 1 : 0);
    }

    @Override
    public String toString()
    {
        return state + (train ? "+train" : "");
    }
}
