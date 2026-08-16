package org.traincontrol.base;

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
     * How much of the tile the wash covers.  Enough to read at a glance across a whole diagram, not so
     * much that the track under it stops being legible - the point is to see WHICH track is claimed.
     */
    private static final float WASH_ALPHA = 0.45f;

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

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color wash = colourOf(state);

            if (wash != null)
            {
                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, WASH_ALPHA));
                g.setColor(wash);
                g.fillRect(0, 0, width, height);
            }

            if (train)
            {
                // A wash says which track is claimed; it cannot say which part of it holds the train.
                // The dot is the diagram's equivalent of the graph labelling its node.
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
