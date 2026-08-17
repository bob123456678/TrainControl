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
     * Trains may go this way.
     *
     * There used to be a second colour for track that runs both ways, which asked the reader to hold
     * three meanings when there are only two: an arrow says one thing, whether or not the arrow beside
     * it says the same.  Both ways is now simply two green arrows, one way is a green and a red, and
     * closed is two reds - one rule, read the same way everywhere.
     */
    private static final Color ONE_WAY = new Color(0, 140, 60);

    /**
     * Closed.  Red, and the only mark drawn as a bar rather than a path, because it is the one that means
     * a train cannot get through.
     */
    private static final Color CLOSED = new Color(200, 0, 0);

    private static final Color LENGTH = new Color(90, 60, 140);

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

        // Which route the badge sits on, so it can be drawn where the rails are rather than in the
        // middle of the square.  Null for a tile whose route is unknown, which falls back to centre.
        private final Side a;
        private final Side b;

        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named)
        {
            this(station, terminus, reversing, parking, named, null, null);
        }

        /**
         * @param a one side of the route this point sits on
         * @param b the other
         */
        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b)
        {
            this.station = station;
            this.terminus = terminus;
            this.reversing = reversing;
            this.parking = parking;
            this.named = named;
            this.a = a;
            this.b = b;
        }

        public Side getA()
        {
            return a;
        }

        public Side getB()
        {
            return b;
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
                && reversing == other.reversing && parking == other.parking && named == other.named
                && a == other.a && b == other.b;
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
     * The hatching drawn over a square autonomy cannot use, so that "unavailable" is a pattern and not
     * only a shade - the two silences on this diagram have to be told apart at a glance.
     */
    private static final Color HATCH = new Color(150, 150, 150);

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
            // Two different silences, which used to look identical and mean opposite things.
            //
            //   IGNORED   autonomy cannot use this square at all - a route button, a turntable, an
            //             excluded page.  Washed out AND hatched: the hatching is what says "not
            //             available", the way it does on any disabled control, and it survives being
            //             read in greyscale.
            //
            //   FOLLOWER  ordinary track that takes its direction from the head of its run.  Washed
            //             only, no hatching, because nothing is wrong with it - it is simply quiet,
            //             and clicking it still works.
            if (ignored)
            {
                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, IGNORED_ALPHA));
                g.setColor(IGNORED);
                g.fillRect(0, 0, width, height);

                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, 0.55f));
                g.setColor(HATCH);
                g.setStroke(new BasicStroke(1f));

                for (int at = -height; at < width; at += 5)
                {
                    g.drawLine(at, 0, at + height, height);
                }

                return;
            }

            // A follower: pushed back, still drawn on, still clickable.
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

            // On a tile with one route the arrow sits in the middle.  On a tile with several - a
            // switch, a crossing, a double curve - each arrow moves out toward the side its own route
            // leads to, so the branches separate by going where they actually go.
            //
            // They used to be nudged PERPENDICULAR to each route instead, which is right for two
            // parallel paths and wrong for a switch: its branches are alternatives sharing a toe, so a
            // sideways offset put each arrow somewhere that corresponded to nothing on the tile.
            for (Mark mark : marks)
            {
                paintMark(g, width, height, mark, marks.size() > 1);
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
     * Draws one route as a pair of arrows, one per direction of travel, out at the ends of the route.
     *
     * Two arrows rather than one, because a route has two directions and they are set independently:
     * with a single mark in the middle there was nowhere to say WHICH way is shut, and a red cross laid
     * over a blue arrow said both things at once in the same place.
     *
     * Each arrow sits just inside the edge its direction leads to, so a run of one-way track reads as a
     * line of arrows all pointing the same way, and a switch's branches separate by going where they
     * actually go rather than by being nudged sideways.
     */
    private void paintMark(Graphics2D g, int width, int height, Mark mark, boolean fanOut)
    {
        int[] from = midpoint(mark.getA(), width, height);
        int[] to = midpoint(mark.getB(), width, height);

        if (from == null || to == null) return;

        Direction direction = mark.getDirection();

        // Stated as what IS allowed rather than as what is not.  Written the other way round - "not
        // toward B" - it read as true for a CLOSED route as well, so a shut branch drew two green
        // arrows and looked wide open.
        arrow(g, width, height, from,
            direction == Direction.BOTH || direction == Direction.TOWARD_A);

        arrow(g, width, height, to,
            direction == Direction.BOTH || direction == Direction.TOWARD_B);
    }

    /**
     * One direction of one route: an arrowhead pointing out of the tile at the side it leads to, or the
     * same place crossed out when trains may not go that way.
     *
     * @param target the midpoint of the side this direction leads to
     * @param allowed whether trains may travel this way
     */
    private void arrow(Graphics2D g, int width, int height, int[] target, boolean allowed)
    {
        int cx = width / 2;
        int cy = height / 2;

        double dx = target[0] - cx;
        double dy = target[1] - cy;
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 1) return;

        dx /= len;
        dy /= len;

        // A blocked arrow is drawn smaller and hollow as well as red, so that the difference survives
        // being printed, being looked at on a poor screen, and being read by somebody who cannot tell
        // red from green.  Colour alone is never the only thing carrying the meaning.
        double size = Math.max(4.0, Math.min(width, height) / 3.2) * (allowed ? 1.0 : 0.72);

        // The tip stops EDGE_GAP short of the edge, so two arrows meeting across a tile boundary have
        // a hairline between them rather than touching and reading as one shape.
        double tipX = target[0] - dx * EDGE_GAP;
        double tipY = target[1] - dy * EDGE_GAP;

        // A blocked direction is the SAME arrow in red, not a cross.  A cross says "something is wrong
        // here" and leaves the reader to work out which way; an arrow says which way, and the colour
        // says it is shut - so the allowed and the blocked directions are read the same way round.
        double angle = Math.atan2(dy, dx);

        int[] xs = new int[3];
        int[] ys = new int[3];

        xs[0] = (int) Math.round(tipX);
        ys[0] = (int) Math.round(tipY);

        for (int i = 0; i < 2; i++)
        {
            double barb = i == 0 ? 2.55 : -2.55;

            xs[i + 1] = (int) Math.round(tipX + Math.cos(angle + barb) * size);
            ys[i + 1] = (int) Math.round(tipY + Math.sin(angle + barb) * size);
        }

        if (allowed)
        {
            g.setColor(ONE_WAY);
            g.fillPolygon(xs, ys, 3);
        }
        else
        {
            // hollow, so it reads as an outline even where the colour does not come through
            g.setColor(Color.WHITE);
            g.fillPolygon(xs, ys, 3);

            g.setColor(CLOSED);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(xs, ys, 3);
        }
    }

    /**
     * How far short of the tile edge an arrowhead stops.  One pixel, so that the arrows on two
     * neighbouring tiles are visibly separate marks rather than one continuous shape.
     */
    private static final int EDGE_GAP = 1;

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

        // Centred on the TRACK, not on the tile.  On a curve the art hugs the corner between the two
        // sides it joins, so a badge in the middle of the square sits off the rails - which is what
        // made sensors on curves look misplaced.  The midpoint of the route's own two sides lands on
        // the track for a curve and is the tile centre for anything straight.
        int[] on = trackCentre(width, height);

        int x = on[0] - size / 2;
        int y = on[1] - size / 2;

        // never let it hang outside the square
        x = Math.max(1, Math.min(width - size - 1, x));
        y = Math.max(1, Math.min(height - size - 1, y));

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

    /**
     * Where the track runs through this tile, as far as the marks can tell.
     *
     * The midpoint of the first route's two sides: the middle of the square for anything straight or
     * crossing, and the corner the rails actually bend around for a curve.
     */
    private int[] trackCentre(int width, int height)
    {
        // the badge's own route where it has one, otherwise the first route drawn, otherwise centre
        Side sideA = badge != null && badge.getA() != null ? badge.getA()
            : marks.isEmpty() ? null : marks.get(0).getA();

        Side sideB = badge != null && badge.getB() != null ? badge.getB()
            : marks.isEmpty() ? null : marks.get(0).getB();

        if (sideA == null || sideB == null) return new int[] {width / 2, height / 2};

        int[] a = midpoint(sideA, width, height);
        int[] b = midpoint(sideB, width, height);

        if (a == null || b == null) return new int[] {width / 2, height / 2};

        return new int[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
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
