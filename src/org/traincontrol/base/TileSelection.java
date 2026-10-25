package org.traincontrol.base;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The squares a user has picked out in the diagram editor, and the arithmetic of moving them together.
 *
 * Selection is a STATE rather than a modifier held down: squares are picked, they stay picked, and Escape lets them go,
 * so a drag has both hands free.  The geometry lives here rather than in the editor because it can be got wrong quietly
 * (a group dragged off the edge, a paste landing half outside, a rotation that moves squares instead of turning them)
 * and arithmetic can be tested where a mouse gesture cannot.
 *
 * Coordinates are the diagram's own: column then row, origin top left.
 */
public final class TileSelection
{
    /** One square, by position. */
    public static final class At
    {
        private final int x;
        private final int y;

        public At(int x, int y)
        {
            this.x = x;
            this.y = y;
        }

        public int getX()
        {
            return x;
        }

        public int getY()
        {
            return y;
        }

        @Override
        public boolean equals(Object other)
        {
            if (this == other) return true;
            if (!(other instanceof At)) return false;

            At o = (At) other;

            return x == o.x && y == o.y;
        }

        @Override
        public int hashCode()
        {
            return x * 31 + y;
        }

        @Override
        public String toString()
        {
            return x + "," + y;
        }
    }

    private final Set<At> chosen = new LinkedHashSet<>();

    /** Picks a square, or unpicks it if it was already picked, so the same gesture corrects itself. */
    public void toggle(int x, int y)
    {
        At at = new At(x, y);

        if (!chosen.remove(at)) chosen.add(at);
    }

    public void add(int x, int y)
    {
        chosen.add(new At(x, y));
    }

    public boolean contains(int x, int y)
    {
        return chosen.contains(new At(x, y));
    }

    public boolean isEmpty()
    {
        return chosen.isEmpty();
    }

    public int size()
    {
        return chosen.size();
    }

    /** Lets everything go.  What Escape does. */
    public void clear()
    {
        chosen.clear();
    }

    /** The squares, in the order they were picked. */
    public List<At> all()
    {
        return new ArrayList<>(chosen);
    }

    /** Everything in a rectangle, which is what dragging a box selects; the corners may be given either way round. */
    public void addRectangle(int fromX, int fromY, int toX, int toY)
    {
        for (int x = Math.min(fromX, toX); x <= Math.max(fromX, toX); x++)
        {
            for (int y = Math.min(fromY, toY); y <= Math.max(fromY, toY); y++)
            {
                add(x, y);
            }
        }
    }

    /**
     * The squares this selection would occupy if moved by a delta.  Returned rather than applied, so the caller can
     * refuse a group dragged past the edge whole: clipping would silently drop track off the side of the diagram.
     */
    public List<At> movedBy(int dx, int dy)
    {
        List<At> out = new ArrayList<>();

        for (At at : chosen) out.add(new At(at.getX() + dx, at.getY() + dy));

        return out;
    }

    /** Whether moving by a delta would keep every square on a diagram of this size. */
    public boolean fitsAfterMove(int dx, int dy, int width, int height)
    {
        for (At at : movedBy(dx, dy))
        {
            if (at.getX() < 0 || at.getY() < 0 || at.getX() >= width || at.getY() >= height)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * The square that drags the whole selection: the top right corner of its bounding box.  Here because it is a rule
     * about a selection, testable without a window.  The corner of the BOX, which need not be a chosen square (an
     * L-shaped selection has an empty corner): grabbing the corner of the rectangle you can see is worth more than the
     * grip sitting on something picked.  Top RIGHT, because a selection is usually dragged out left to right.
     *
     * @return the grip's x and y, or null when nothing is picked
     */
    public int[] handle()
    {
        int[] box = bounds();

        return box == null ? null : new int[]{box[2], box[1]};
    }

    /**
     * The smallest rectangle holding everything picked, as {minX, minY, maxX, maxY}, or null when nothing is picked.
     *
     * What a copy takes: the bounding box, INCLUDING squares inside it that were not picked, or the paste would be a
     * shape with holes.  READ THIS BEFORE PAIRING IT WITH A DELETE: delete walks the picked squares, this walks the box
     * around them, and a cut that assumed they match carried origins for sixteen squares when two had been emptied, and
     * moved the setup off fourteen that still held their track.
     */
    public int[] bounds()
    {
        if (chosen.isEmpty()) return null;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (At at : chosen)
        {
            minX = Math.min(minX, at.getX());
            minY = Math.min(minY, at.getY());
            maxX = Math.max(maxX, at.getX());
            maxY = Math.max(maxY, at.getY());
        }

        return new int[] {minX, minY, maxX, maxY};
    }
}
