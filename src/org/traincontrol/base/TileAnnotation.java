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

    /**
     * A station autonomy will not choose on its own - a parking berth.  A different colour rather than
     * a different shape, because it is still a station in every other respect.
     */
    private static final Color PARKING = new Color(120, 85, 160);

    /**
     * What a sensor has been designated as.
     *
     * The five the user thinks in terms of are two independent questions over a station: whether a
     * train arriving must leave the way it came (terminus), and whether autonomy picks it during
     * normal running (parking).  A sensor that is not a station at all is simply a point trains run
     * through, and carries no badge.
     */
    public static class Station
    {
        private final boolean terminus;
        private final boolean parking;
        private final boolean named;

        public Station(boolean terminus, boolean parking, boolean named)
        {
            this.terminus = terminus;
            this.parking = parking;
            this.named = named;
        }

        public boolean isTerminus()
        {
            return terminus;
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
            if (!(o instanceof Station)) return false;

            Station other = (Station) o;

            return terminus == other.terminus && parking == other.parking && named == other.named;
        }

        @Override
        public int hashCode()
        {
            return (terminus ? 1 : 0) + (parking ? 2 : 0) + (named ? 4 : 0);
        }

        @Override
        public String toString()
        {
            return (parking ? "parking" : "station") + (terminus ? " terminus" : "")
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
    private final Station station;
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
     * @param station what this sensor is designated as, or null when it is not a station
     * @param ignored whether autonomy takes no notice of this square at all
     * @param muted whether to push the tile art back without saying it cannot be configured
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Station station,
        boolean ignored, boolean muted)
    {
        this.marks = marks == null ? Collections.<Mark>emptyList() : new ArrayList<>(marks);
        this.length = length;
        this.selected = selected;
        this.station = station;
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

    public Station getStation()
    {
        return station;
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
        return marks.isEmpty() && length < 0 && !selected && station == null && !ignored && !muted;
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

            if (station != null) paintStation(g, width, height);

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
    private void paintMark(Graphics2D g, int width, int height, Mark mark, int spread)
    {
        int[] from = midpoint(mark.getA(), width, height);
        int[] to = midpoint(mark.getB(), width, height);

        if (from == null || to == null) return;

        // The waypoint the route bends through.  Nudged perpendicular to the run when the tile carries
        // more than one route, which is what keeps a crossing legible.
        int cx = width / 2;
        int cy = height / 2;

        if (spread != 0)
        {
            double dx = to[0] - from[0];
            double dy = to[1] - from[1];
            double len = Math.sqrt(dx * dx + dy * dy);

            if (len >= 1)
            {
                cx += (int) Math.round(-dy / len * spread);
                cy += (int) Math.round(dx / len * spread);
            }
        }

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

        if (bidirectional)
        {
            // one head at each end of the run, pointing out
            head(g, cx, cy, from[0], from[1], width, height);
            head(g, cx, cy, to[0], to[1], width, height);
        }
        else
        {
            // TOWARD_A means trains may travel toward side A, so the head points at A.  Drawn ON the
            // bend rather than out near the edge: an arrowhead sitting at the tile boundary reads as a
            // mark between two squares rather than as the flow through this one, which is what made a
            // page of them look like scattered ticks instead of a direction of travel.
            int[] target = mark.getDirection() == Direction.TOWARD_A ? from : to;

            head(g, from[0], from[1], to[0], to[1], width, height,
                cx + (target[0] - cx) / 4, cy + (target[1] - cy) / 4, target);
        }
    }

    /**
     * A solid arrowhead partway along the line from the centre toward a side, pointing that way.
     */
    private void head(Graphics2D g, int cx, int cy, int tx, int ty, int width, int height)
    {
        head(g, cx, cy, tx, ty, width, height,
            (int) Math.round(cx + (tx - cx) * 0.6), (int) Math.round(cy + (ty - cy) * 0.6),
            new int[] {tx, ty});
    }

    /**
     * @param px where the head sits
     * @param py
     * @param target the point it aims at
     */
    private void head(Graphics2D g, int fx, int fy, int lx, int ly, int width, int height,
        int px, int py, int[] target)
    {
        double dx = target[0] - px;
        double dy = target[1] - py;
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 1) return;

        dx /= len;
        dy /= len;

        double size = Math.max(4.0, Math.min(width, height) / 3.5);

        // A filled triangle rather than two strokes: at tile size a pair of thin barbs is two marks
        // that happen to meet, and a solid head is one shape that obviously points somewhere.
        double angle = Math.atan2(dy, dx);

        int[] xs = new int[3];
        int[] ys = new int[3];

        xs[0] = (int) Math.round(px + dx * size * 0.6);
        ys[0] = (int) Math.round(py + dy * size * 0.6);

        for (int i = 0; i < 2; i++)
        {
            double offset = i == 0 ? 2.6 : -2.6;

            xs[i + 1] = (int) Math.round(xs[0] + Math.cos(angle + offset) * size);
            ys[i + 1] = (int) Math.round(ys[0] + Math.sin(angle + offset) * size);
        }

        g.fillPolygon(xs, ys, 3);
    }

    /**
     * A platform mark in the top left: a filled roundel, hollow when the station has no name yet.
     *
     * Top left because the length sits bottom right and the route lines run through the middle, so the
     * three never overlap.  Hollow-when-unnamed is the only cue anywhere that a station still needs a
     * name - nothing refuses to work without one, it just turns up as a coordinate in a timetable.
     */
    /**
     * Draws a station as a station rather than as a sensor with a dot on it.
     *
     * A station is the thing autonomy is FOR - it is where trains can be sent - and on the diagram it
     * was previously the same tile as every other sensor with a small mark added.  It now takes the
     * whole square: a coloured frame, a solid platform band, and a badge that says which kind it is.
     *
     *   plain station     blue frame, blank band
     *   terminus          blue frame, band with a buffer stop across the closed end
     *   parking           purple frame, band lettered P
     *   parking terminus  purple frame, lettered band with the buffer stop
     *
     * Unnamed stations are drawn hollow, which stays the only cue anywhere that one still needs a name.
     */
    private void paintStation(Graphics2D g, int width, int height)
    {
        Color colour = station.isParking() ? PARKING : STATION;

        // The frame is what makes a station findable from across the diagram; the band is what makes
        // it readable close up.
        g.setStroke(new BasicStroke(2f));
        g.setColor(colour);
        g.drawRect(1, 1, width - 3, height - 3);

        int band = Math.max(6, height / 3);
        int top = height - band - 1;

        if (station.isNamed())
        {
            g.setColor(colour);
            g.fillRect(2, top, width - 4, band);
        }
        else
        {
            // hollow: the station exists but has no name yet
            g.setColor(Color.WHITE);
            g.fillRect(2, top, width - 4, band);
            g.setColor(colour);
            g.setStroke(new BasicStroke(1f));
            g.drawRect(2, top, width - 5, band - 1);
        }

        Color ink = station.isNamed() ? Color.WHITE : colour;

        if (station.isParking())
        {
            g.setColor(ink);
            g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, band * 0.9f));

            java.awt.FontMetrics metrics = g.getFontMetrics();

            g.drawString("P", (width - metrics.stringWidth("P")) / 2,
                top + band - Math.max(1, (band - metrics.getAscent()) / 2) - 1);
        }

        if (station.isTerminus())
        {
            // a buffer stop: the bar a train cannot pass, drawn across the left end of the band
            g.setColor(ink);
            g.setStroke(new BasicStroke(2.5f));
            g.drawLine(4, top + 1, 4, top + band - 2);
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
            && (station == null ? other.station == null : station.equals(other.station))
            && ignored == other.ignored && muted == other.muted && marks.equals(other.marks);
    }

    @Override
    public int hashCode()
    {
        return marks.hashCode() * 31 + length * 2
            + (selected ? 1 : 0) + (station == null ? 0 : station.hashCode() * 4)
            + (ignored ? 16 : 0) + (muted ? 32 : 0);
    }

    @Override
    public String toString()
    {
        return marks + (length >= 0 ? " len=" + length : "") + (selected ? " selected" : "")
            + (station == null ? "" : " " + station)
            + (ignored ? " ignored" : "");
    }
}
