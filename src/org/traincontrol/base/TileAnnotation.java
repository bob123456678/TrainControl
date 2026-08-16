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
     * The graph window's own colours, so somebody who has read one view can read the other.
     * TrainControlUI paints an active point rgb(0,0,200) and an inactive one rgb(255,102,0).
     */
    private static final Color POINT_ACTIVE = new Color(0, 0, 200);
    private static final Color POINT_INACTIVE = new Color(255, 102, 0);

    /**
     * The home ring, matching TrainControlUI.COLOR_AT_HOME.
     */
    private static final Color AT_HOME = new Color(0, 200, 210);

    /**
     * What a sensor has been designated as, drawn as a badge on its tile.
     *
     * Shapes and colours follow the graph window exactly, because that is the vocabulary the user
     * already reads: a station is a circle, a terminus a square, a reversing point a cross, and a plain
     * point a small diamond; blue means autonomy uses it, orange means it does not.
     *
     * The five kinds the user thinks in are two questions over a station - must a train leave the way
     * it came (terminus), and does autonomy choose it on its own (parking) - which is why these are
     * independent flags rather than one enum.
     */
    public static class Badge
    {
        private final boolean station;
        private final boolean terminus;
        private final boolean reversing;
        private final boolean parking;
        private final boolean named;

        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named)
        {
            this.station = station;
            this.terminus = terminus;
            this.reversing = reversing;
            this.parking = parking;
            this.named = named;
        }

        public boolean isStation()
        {
            return station;
        }

        public boolean isTerminus()
        {
            return terminus;
        }

        public boolean isReversing()
        {
            return reversing;
        }

        public boolean isParking()
        {
            return parking;
        }

        public boolean isNamed()
        {
            return named;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (!(o instanceof Badge)) return false;

            Badge other = (Badge) o;

            return station == other.station && terminus == other.terminus
                && reversing == other.reversing && parking == other.parking && named == other.named;
        }

        @Override
        public int hashCode()
        {
            return (station ? 1 : 0) + (terminus ? 2 : 0) + (reversing ? 4 : 0)
                + (parking ? 8 : 0) + (named ? 16 : 0);
        }

        @Override
        public String toString()
        {
            return (station ? (parking ? "parking" : "station") : "point")
                + (terminus ? " terminus" : "") + (reversing ? " reversing" : "")
                + (named ? "" : " (unnamed)");
        }
    }

    /**
     * Autonomy takes no notice of this square.  Washed out rather than greyed over, so that a tile
     * nobody can configure recedes without becoming a dark block that draws the eye more than the
     * track does - which is what a heavier grey did on a page with forty route buttons on it.
     */
    private static final Color IGNORED = Color.WHITE;

    private static final float IGNORED_ALPHA = 0.62f;

    /**
     * Pushed back but still the user's to set - signals, which sit on almost every run and whose art
     * is the heaviest on the diagram, so at full strength they read as the most important thing on it.
     */
    private static final float MUTED_ALPHA = 0.45f;

    private final List<Mark> marks;
    private final int length;
    private final boolean selected;
    private final Badge badge;
    private final boolean ignored;
    private final boolean muted;

    /**
     * @param marks the routes to draw, or empty to draw none
     * @param length the tile's length, or a negative number not to show one
     * @param selected whether this tile is part of a bulk selection
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected)
    {
        this(marks, length, selected, null, false, false);
    }

    /**
     * @param marks the routes to draw, or empty to draw none
     * @param length the tile's length, or a negative number not to show one
     * @param selected whether this tile is part of a bulk selection
     * @param badge what this sensor is, or null when the square is not a point at all
     * @param ignored whether autonomy takes no notice of this square at all
     * @param muted whether to push the tile art back without saying it cannot be configured
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean muted)
    {
        this.marks = marks == null ? Collections.<Mark>emptyList() : new ArrayList<>(marks);
        this.length = length;
        this.selected = selected;
        this.badge = badge;
        this.ignored = ignored;
        this.muted = muted;
    }

    public boolean isIgnored()
    {
        return ignored;
    }

    public boolean isMuted()
    {
        return muted;
    }

    public Badge getBadge()
    {
        return badge;
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
        return marks.isEmpty() && length < 0 && !selected && badge == null && !ignored && !muted;
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

            // A square autonomy takes no notice of is greyed out and nothing else is drawn on it.
            // Nothing here is the user's to decide, so anything drawn would invite a click.
            if (ignored)
            {
                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, IGNORED_ALPHA));
                g.setColor(IGNORED);
                g.fillRect(0, 0, width, height);

                return;
            }

            // Signals and the like: pushed back, but still drawn on and still clickable.
            if (muted)
            {
                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, MUTED_ALPHA));
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);

                // back to fully opaque for whatever is drawn next; the finally block restores the
                // caller's own composite when this method returns
                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 1f));
            }

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

            // Routes are nudged off the centre when a tile carries more than one, so a crossing or a
            // double curve shows two separate paths rather than one X of overlapping lines that says
            // nothing about which of them is restricted.
            for (int i = 0; i < marks.size(); i++)
            {
                int spread = marks.size() < 2 ? 0
                    : Math.max(2, Math.min(width, height) / 8) * (i * 2 - (marks.size() - 1));

                paintMark(g, width, height, marks.get(i), spread);
            }

            if (badge != null) paintBadge(g, width, height);

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
    /**
     * Draws one route as an arrow lying along the track, rather than as a line spanning the tile.
     *
     * The lines were the problem.  The tile art already shows where the track goes, so drawing it
     * again added a second set of lines that met the neighbouring tile's lines at every boundary - and
     * with a head near each edge, a page of them read as scattered ticks rather than as flow.  What is
     * left is the part that carries information: a head, on the track, pointing the way a train may go.
     */
    private void paintMark(Graphics2D g, int width, int height, Mark mark, int spread)
    {
        int[] from = midpoint(mark.getA(), width, height);
        int[] to = midpoint(mark.getB(), width, height);

        if (from == null || to == null) return;

        // Where the arrow sits.  Nudged perpendicular to its own run when a tile carries more than one
        // route, so a switch shows one arrow per branch instead of three on top of each other.
        int cx = width / 2;
        int cy = height / 2;

        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len >= 1 && spread != 0)
        {
            cx += (int) Math.round(-dy / len * spread);
            cy += (int) Math.round(dx / len * spread);
        }

        if (mark.getDirection() == Direction.NONE)
        {
            // A bar across the run: the one mark that means a train cannot get through at all.
            int bar = Math.max(3, Math.min(width, height) / 5);

            g.setColor(CLOSED);
            g.setStroke(new BasicStroke(CHEVRON_WIDTH + 1f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));
            g.drawLine(cx - bar, cy - bar, cx + bar, cy + bar);
            g.drawLine(cx - bar, cy + bar, cx + bar, cy - bar);

            return;
        }

        boolean bidirectional = mark.getDirection() == Direction.BOTH;

        g.setColor(bidirectional ? BOTH_WAYS : ONE_WAY);

        if (bidirectional)
        {
            // back to back, so the shape itself says "either way" without a line between them
            head(g, cx, cy, from, width, height, 0.55);
            head(g, cx, cy, to, width, height, 0.55);
        }
        else
        {
            head(g, cx, cy, mark.getDirection() == Direction.TOWARD_A ? from : to, width, height, 0);
        }
    }

    /**
     * A solid arrowhead centred on a point, aimed at a side of the tile.
     *
     * @param cx where the arrow sits
     * @param cy
     * @param target the side midpoint it points at
     * @param offset how far toward the target to push it, as a fraction of the way there
     */
    private void head(Graphics2D g, int cx, int cy, int[] target, int width, int height,
        double offset)
    {
        double dx = target[0] - cx;
        double dy = target[1] - cy;
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 1) return;

        dx /= len;
        dy /= len;

        double size = Math.max(4.0, Math.min(width, height) / 3.2);

        double px = cx + dx * size * offset;
        double py = cy + dy * size * offset;

        double angle = Math.atan2(dy, dx);

        int[] xs = new int[3];
        int[] ys = new int[3];

        xs[0] = (int) Math.round(px + dx * size * 0.5);
        ys[0] = (int) Math.round(py + dy * size * 0.5);

        for (int i = 0; i < 2; i++)
        {
            double barb = i == 0 ? 2.55 : -2.55;

            xs[i + 1] = (int) Math.round(xs[0] + Math.cos(angle + barb) * size);
            ys[i + 1] = (int) Math.round(ys[0] + Math.sin(angle + barb) * size);
        }

        g.fillPolygon(xs, ys, 3);
    }

    /**
     * Draws what a sensor IS, in the graph window's own shapes and colours.
     *
     *   plain point       small diamond
     *   station           circle
     *   terminus          square
     *   reversing         cross
     *   blue              autonomy uses it
     *   orange            autonomy leaves it alone (parking, or switched off)
     *
     * Parity on purpose: the shapes and the two colours are exactly what TrainControlUI already paints
     * on the graph, so nobody has to learn a second vocabulary to read the same railway.
     *
     * Unnamed points are drawn hollow, which remains the only cue that one still needs a name.
     */
    private void paintBadge(Graphics2D g, int width, int height)
    {
        Color colour = badge.isParking() ? POINT_INACTIVE : POINT_ACTIVE;

        // A station takes a bigger badge than a passing point, as it does on the graph: 20px against
        // 17px there, the same proportion here.
        int size = Math.max(badge.isStation() ? 11 : 8,
            Math.min(width, height) / (badge.isStation() ? 2 : 3));

        int x = (width - size) / 2;
        int y = (height - size) / 2;

        g.setStroke(new BasicStroke(badge.isStation() ? 2f : 1.5f));

        // Filled when named, hollow when not - so an unnamed point is visible but visibly unfinished.
        Color fill = badge.isNamed() ? colour : Color.WHITE;
        Color line = badge.isNamed() ? Color.WHITE : colour;

        if (badge.isReversing())
        {
            cross(g, x, y, size, fill, line);
        }
        else if (badge.isTerminus())
        {
            g.setColor(fill);
            g.fillRect(x, y, size, size);
            g.setColor(line);
            g.drawRect(x, y, size, size);
        }
        else if (badge.isStation())
        {
            g.setColor(fill);
            g.fillOval(x, y, size, size);
            g.setColor(line);
            g.drawOval(x, y, size, size);
        }
        else
        {
            diamond(g, x, y, size, fill, line);
        }
    }

    private void cross(Graphics2D g, int x, int y, int size, Color fill, Color line)
    {
        int arm = size / 3;

        int[] xs = {x + arm, x + size - arm, x + size - arm, x + size, x + size,
                    x + size - arm, x + size - arm, x + arm, x + arm, x, x, x + arm};
        int[] ys = {y, y, y + arm, y + arm, y + size - arm, y + size - arm, y + size,
                    y + size, y + size - arm, y + size - arm, y + arm, y + arm};

        g.setColor(fill);
        g.fillPolygon(xs, ys, xs.length);
        g.setColor(line);
        g.drawPolygon(xs, ys, xs.length);
    }

    private void diamond(Graphics2D g, int x, int y, int size, Color fill, Color line)
    {
        int half = size / 2;

        int[] xs = {x + half, x + size, x + half, x};
        int[] ys = {y, y + half, y + size, y + half};

        g.setColor(fill);
        g.fillPolygon(xs, ys, 4);
        g.setColor(line);
        g.drawPolygon(xs, ys, 4);
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
            && (badge == null ? other.badge == null : badge.equals(other.badge))
            && ignored == other.ignored && muted == other.muted && marks.equals(other.marks);
    }

    @Override
    public int hashCode()
    {
        return marks.hashCode() * 31 + length * 2
            + (selected ? 1 : 0) + (badge == null ? 0 : badge.hashCode() * 4)
            + (ignored ? 16 : 0) + (muted ? 32 : 0);
    }

    @Override
    public String toString()
    {
        return marks + (length >= 0 ? " len=" + length : "") + (selected ? " selected" : "")
            + (badge == null ? "" : " " + badge)
            + (ignored ? " ignored" : "");
    }
}
