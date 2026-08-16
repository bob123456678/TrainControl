package org.traincontrol.base;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.traincontrol.base.TileGraph.Direction;
import org.traincontrol.base.TilePorts.Side;

/**
 * What a tile has been set to allow, drawn on the tile while autonomy is being set up.
 *
 * The authoring counterpart to TileOverlay: that one shows what trains are doing, this one shows what
 * they would be permitted to do.  They are separate because they answer different questions and are
 * looked at at different times, and because merging them would mean a value class whose equality mixed a
 * running state with an editing decision.
 *
 * Drawn as thin lines with chevrons rather than by tinting the tile.  A tint can say "something is set
 * here"; it cannot say WHICH WAY, and which way is the entire content of the decision being made.  On a
 * switch the lines also separate the branches, so a tile with three routes shows three answers rather
 * than one.
 *
 * @author Adam
 */
public class TileAnnotation
{
    /**
     * One route of a tile, and which way it may be travelled.
     */
    public static class Mark
    {
        private final Side a;
        private final Side b;
        private final Direction direction;

        public Mark(Side a, Side b, Direction direction)
        {
            this.a = a;
            this.b = b;
            this.direction = direction == null ? Direction.BOTH : direction;
        }

        public Side getA()
        {
            return a;
        }

        public Side getB()
        {
            return b;
        }

        public Direction getDirection()
        {
            return direction;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof Mark)) return false;

            Mark other = (Mark) o;

            return a == other.a && b == other.b && direction == other.direction;
        }

        @Override
        public int hashCode()
        {
            return (a == null ? 0 : a.hashCode()) * 961
                 + (b == null ? 0 : b.hashCode()) * 31
                 + direction.hashCode();
        }

        @Override
        public String toString()
        {
            return a + "-" + b + ":" + direction;
        }
    }

    /**
     * Passable both ways.  Deliberately unobtrusive: on a finished layout most track is this, and a
     * default drawn loudly would bury the decisions somebody actually made.
     */
    private static final Color BOTH_WAYS = new Color(0, 110, 200);

    /**
     * One way only - the case the chevrons exist for.
     */
    private static final Color ONE_WAY = new Color(0, 140, 60);

    /**
     * Closed.  Red, and the only mark drawn as a bar rather than a path, because it is the one that means
     * a train cannot get through.
     */
    private static final Color CLOSED = new Color(200, 0, 0);

    private static final Color LENGTH = new Color(90, 60, 140);

    private static final float LINE_WIDTH = 1.6f;
    private static final float CHEVRON_WIDTH = 1.8f;

    /**
     * Selected for a bulk edit.  Drawn as a border rather than a fill so the track underneath stays
     * readable while forty tiles are being picked.
     */
    private static final Color SELECTED = new Color(255, 140, 0);

    /**
     * How much the tile art is dimmed under the marks.
     *
     * The lines are thin and the icons are busy, so at 0 the chevrons disappear into the track they
     * describe.  A wash of the tile's own background separates the two layers without hiding either -
     * the track stays legible, the marks stop competing with it.
     */
    private static final float DIM = 0.55f;

    private static final Color DIM_COLOUR = Color.WHITE;

    /**
     * A designated station.  Drawn as its own mark rather than left to the sensor icon, because a
     * sensor and a station look identical on the diagram and mean very different things: a train can be
     * SENT to a station, and only passes through everything else.
     */
    private static final Color STATION = new Color(0, 90, 180);

    private final List<Mark> marks;
    private final int length;
    private final boolean selected;
    private final boolean station;
    private final boolean named;

    /**
     * @param marks the routes to draw, or empty to draw none
     * @param length the tile's length, or a negative number not to show one
     * @param selected whether this tile is part of a bulk selection
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected)
    {
        this(marks, length, selected, false, true);
    }

    /**
     * @param marks the routes to draw, or empty to draw none
     * @param length the tile's length, or a negative number not to show one
     * @param selected whether this tile is part of a bulk selection
     * @param station whether trains may be sent here
     * @param named whether this point has a name of its own
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, boolean station,
        boolean named)
    {
        this.marks = marks == null ? Collections.<Mark>emptyList() : new ArrayList<>(marks);
        this.length = length;
        this.selected = selected;
        this.station = station;
        this.named = named;
    }

    public boolean isStation()
    {
        return station;
    }

    public boolean isNamed()
    {
        return named;
    }

    public List<Mark> getMarks()
    {
        return Collections.unmodifiableList(marks);
    }

    public int getLength()
    {
        return length;
    }

    public boolean isSelected()
    {
        return selected;
    }

    /**
     * Whether this would paint anything at all.
     * @return
     */
    public boolean isBlank()
    {
        return marks.isEmpty() && length < 0 && !selected && !station;
    }

    /**
     * Paints over a tile that has already drawn itself.
     *
     * @param g the tile's graphics, already translated to its own origin
     * @param width
     * @param height
     */
    public void paint(Graphics2D g, int width, int height)
    {
        if (isBlank()) return;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        java.awt.Font oldFont = g.getFont();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Knock the tile art back before drawing on it.  Thin lines over a busy icon are the same
            // contrast problem as writing on a photograph; this is the caption box behind the writing.
            if (!marks.isEmpty())
            {
                java.awt.Composite before = g.getComposite();

                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, DIM));
                g.setColor(DIM_COLOUR);
                g.fillRect(0, 0, width, height);

                g.setComposite(before);
            }

            for (Mark mark : marks)
            {
                paintMark(g, width, height, mark);
            }

            if (station) paintStation(g, width, height);

            if (length >= 0) paintLength(g, width, height);

            if (selected)
            {
                g.setStroke(new BasicStroke(2f));
                g.setColor(SELECTED);
                g.drawRect(1, 1, width - 3, height - 3);
            }
        }
        finally
        {
            g.setFont(oldFont);
            g.setColor(oldColor);
            g.setStroke(oldStroke);

            if (oldHint != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint);
        }
    }

    /**
     * Draws one route as a path from one side to the other through the middle of the tile.
     *
     * Through the middle rather than straight across, so a curve is drawn as a curve: a straight line
     * between the two sides of a curve tile would cut the corner and sit off the track it describes.
     */
    private void paintMark(Graphics2D g, int width, int height, Mark mark)
    {
        int[] from = midpoint(mark.getA(), width, height);
        int[] to = midpoint(mark.getB(), width, height);

        if (from == null || to == null) return;

        int cx = width / 2;
        int cy = height / 2;

        if (mark.getDirection() == Direction.NONE)
        {
            // A closed route is drawn as the two stubs it has become, with a bar across the middle.  The
            // stubs are what say WHICH route is closed on a tile that has more than one.
            g.setStroke(new BasicStroke(LINE_WIDTH));
            g.setColor(CLOSED);
            g.drawLine(from[0], from[1], cx, cy);
            g.drawLine(to[0], to[1], cx, cy);

            int bar = Math.max(3, Math.min(width, height) / 6);

            g.setStroke(new BasicStroke(CHEVRON_WIDTH));
            g.drawLine(cx - bar, cy - bar, cx + bar, cy + bar);
            g.drawLine(cx - bar, cy + bar, cx + bar, cy - bar);

            return;
        }

        boolean bidirectional = mark.getDirection() == Direction.BOTH;

        g.setStroke(new BasicStroke(LINE_WIDTH));
        g.setColor(bidirectional ? BOTH_WAYS : ONE_WAY);
        g.drawLine(from[0], from[1], cx, cy);
        g.drawLine(cx, cy, to[0], to[1]);

        g.setStroke(new BasicStroke(CHEVRON_WIDTH, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (bidirectional)
        {
            // one at each end, pointing out: the shape says "either way" without needing a legend
            chevron(g, cx, cy, from[0], from[1], width, height);
            chevron(g, cx, cy, to[0], to[1], width, height);
        }
        else
        {
            // TOWARD_A means trains may travel toward side A, so the chevron points at A
            int[] target = mark.getDirection() == Direction.TOWARD_A ? from : to;

            chevron(g, cx, cy, target[0], target[1], width, height);
        }
    }

    /**
     * Draws an arrowhead partway along the line from the centre toward a side, pointing that way.
     */
    private void chevron(Graphics2D g, int cx, int cy, int tx, int ty, int width, int height)
    {
        // Placed at three quarters rather than at the edge so two tiles meeting do not put their heads
        // against each other and read as one shape.
        double px = cx + (tx - cx) * 0.75;
        double py = cy + (ty - cy) * 0.75;

        double dx = tx - cx;
        double dy = ty - cy;
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 1) return;

        dx /= len;
        dy /= len;

        double size = Math.max(3.0, Math.min(width, height) / 5.0);

        // the two barbs are the direction vector rotated by +/- 140 degrees
        double angle = Math.atan2(dy, dx);

        for (double offset : new double[] {2.443, -2.443})
        {
            double bx = px + Math.cos(angle + offset) * size;
            double by = py + Math.sin(angle + offset) * size;

            g.drawLine((int) Math.round(px), (int) Math.round(py),
                       (int) Math.round(bx), (int) Math.round(by));
        }
    }

    /**
     * A platform mark in the top left: a filled roundel, hollow when the station has no name yet.
     *
     * Top left because the length sits bottom right and the route lines run through the middle, so the
     * three never overlap.  Hollow-when-unnamed is the only cue anywhere that a station still needs a
     * name - nothing refuses to work without one, it just turns up as a coordinate in a timetable.
     */
    private void paintStation(Graphics2D g, int width, int height)
    {
        int size = Math.max(7, Math.min(width, height) / 3);

        g.setStroke(new BasicStroke(2f));

        if (named)
        {
            g.setColor(STATION);
            g.fillOval(2, 2, size, size);
            g.setColor(Color.WHITE);
            g.drawOval(2, 2, size, size);
        }
        else
        {
            g.setColor(Color.WHITE);
            g.fillOval(2, 2, size, size);
            g.setColor(STATION);
            g.drawOval(2, 2, size, size);
        }
    }

    private void paintLength(Graphics2D g, int width, int height)
    {
        String text = String.valueOf(length);

        int size = Math.max(8, Math.min(width, height) / 4);

        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, (float) size));

        java.awt.FontMetrics metrics = g.getFontMetrics();

        int textWidth = metrics.stringWidth(text);

        // bottom right, where the tile art is emptiest across the shapes that carry a length
        int x = width - textWidth - 2;
        int y = height - 2;

        // read against both light and dark track art
        g.setColor(Color.WHITE);

        for (int ox = -1; ox <= 1; ox++)
        {
            for (int oy = -1; oy <= 1; oy++)
            {
                if (ox != 0 || oy != 0) g.drawString(text, x + ox, y + oy);
            }
        }

        g.setColor(LENGTH);
        g.drawString(text, x, y);
    }

    /**
     * Where a side meets the edge of the tile.
     */
    private static int[] midpoint(Side side, int width, int height)
    {
        if (side == null) return null;

        switch (side)
        {
            case N: return new int[] {width / 2, 0};
            case S: return new int[] {width / 2, height};
            case E: return new int[] {width, height / 2};
            case W: return new int[] {0, height / 2};
            default: return null;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof TileAnnotation)) return false;

        TileAnnotation other = (TileAnnotation) o;

        return length == other.length && selected == other.selected
            && station == other.station && named == other.named && marks.equals(other.marks);
    }

    @Override
    public int hashCode()
    {
        return marks.hashCode() * 31 + length * 2
            + (selected ? 1 : 0) + (station ? 4 : 0) + (named ? 8 : 0);
    }

    @Override
    public String toString()
    {
        return marks + (length >= 0 ? " len=" + length : "") + (selected ? " selected" : "")
            + (station ? (named ? " station" : " station(unnamed)") : "");
    }
}
