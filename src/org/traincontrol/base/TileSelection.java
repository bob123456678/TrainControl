package org.traincontrol.base;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The squares a user has picked out in the diagram editor, and the arithmetic of moving them together.
 *
 * Selection is a STATE rather than a modifier held down: squares are picked, they stay picked, and
 * Escape lets them go. That is what makes dragging a group possible at all - a drag needs both hands
 * free, so the selection cannot be something the user is holding a key to maintain.
 *
 * The geometry lives here rather than in the editor because it is the part that can be got wrong
 * quietly: a group dragged off the edge of the diagram, a paste that lands half outside it, a rotation
 * that moves squares when it should turn them in place. All of that is arithmetic, and arithmetic can
 * be tested where a mouse gesture cannot.
 *
 * Coordinates are the diagram's own - column then row, origin top left.
 */
public final class TileSelection
{
    /**
     * One square, by position.
     */
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

    /**
     * Picks a square, or unpicks it if it was already picked.
     *
     * Toggling rather than adding, because the same gesture has to be able to correct itself - a user
     * who shift-clicks one square too many should not have to start again.
     */
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

    /**
     * Lets everything go.  What Escape does.
     */
    public void clear()
    {
        chosen.clear();
    }

    /**
     * The squares, in the order they were picked.
     */
    public List<At> all()
    {
        return new ArrayList<>(chosen);
    }

    /**
     * Everything in a rectangle, which is what dragging a box selects.
     *
     * The corners may be given either way round, because a user drags a box in whichever direction
     * suits them.
     */
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
     * The squares this selection would occupy if moved by a delta.
     *
     * Returned rather than applied, so the caller can check the result before committing to it - a
     * group dragged past the edge should be refused whole rather than clipped, which would silently
     * drop track off the side of the diagram.
     */
    public List<At> movedBy(int dx, int dy)
    {
        List<At> out = new ArrayList<>();

        for (At at : chosen) out.add(new At(at.getX() + dx, at.getY() + dy));

        return out;
    }

    /**
     * Whether moving by a delta would keep every square on a diagram of this size.
     *
     * @param width the diagram's width in squares
     * @param height its height
     */
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
     * The smallest rectangle holding everything picked, as {minX, minY, maxX, maxY}.
     *
     * What a copy takes: the bounding box, INCLUDING squares inside it that were not picked. Copying
     * only the picked squares would paste a shape with holes in it, and a piece of railway with holes
     * is not the piece the user pointed at.
     *
     * @return the bounds, or null when nothing is picked
     */
    /**
     * The square that drags the whole selection: the top right corner of its bounding box.
     *
     * Here rather than in the editor because it is a rule about a selection rather than about a
     * window, and because a rule that decides where a control appears is worth being able to test
     * without opening one.
     *
     * The corner of the BOX, which need not be a chosen square - an L-shaped selection has an empty
     * corner.  Being able to grab the corner of the rectangle you can see is worth more than the grip
     * always sitting on something picked.
     *
     * Top RIGHT: a selection is usually dragged out left to right, so the pointer is already over
     * there when the button comes up.
     *
     * @return the grip's x and y, or null when nothing is picked
     */
    public int[] handle()
    {
        int[] box = bounds();

        return box == null ? null : new int[]{box[2], box[1]};
    }

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
