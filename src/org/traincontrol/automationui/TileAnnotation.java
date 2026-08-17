package org.traincontrol.automationui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.traincontrol.automationui.TileGraph.Direction;
import org.traincontrol.automationui.TilePorts.Side;

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
     * already reads: a station is a circle, a terminus a square, a reversing point a smaller
     * square, and a plain point a small diamond; blue means autonomy uses it, orange means it does not.
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

    private final List<Mark> marks;
    private final int length;
    private final boolean selected;
    private final Badge badge;
    private final boolean ignored;

    /**
     * Whether this square's track is drawn as a corner-cutting chord rather than square across the
     * tile - a curve or a double curve, and nothing else.
     *
     * A switch has chords too, and taking its arrow angles from them was a step too far: the author
     * asked for the tilt on curves only, and a switch reads better with its arrows square to the
     * edges, which is also how its trunk enters.
     */
    private final boolean curved;

    /**
     * Whether this square is a paired link - a hole in the diagram that comes out on another page.
     *
     * It needs saying because a link's only route is a STUB: one side, which is both how a train
     * leaves the track into the link and how one arrives out of it.  Drawn like any other side that
     * gets a single arrow, which is why only one of those two moves was ever visible.
     */
    private final boolean portal;

    /**
     * Segments of a tested path running through this square, one per direction that works.
     *
     * A path used to be shown by outlining every square it crossed, which said WHERE it went and
     * nothing about which way, or whether the other way was possible at all - two questions the test
     * exists to answer.  Drawn as a line through each square instead, the route reads as a route, and
     * a direction that has no path simply has no line: the answer is the gap.
     */
    private final List<Trace> traces;

    /**
     * Whether only the directions that are SHUT are drawn.
     *
     * Open track is most of a layout, so its arrows are most of the ink, and they say the thing the
     * reader can already assume.  Turned off, what is left is exactly the restrictions - which is the
     * whole of what somebody checking a setup is looking for.
     */
    private final boolean blockedOnly;

    /**
     * One square's worth of a tested path: in by one side, out by another.
     *
     * A null side is an end of the run - the sensor the test started or finished at - so the line stops
     * in the middle of that square rather than running off its edge into track nobody asked about.
     */
    public static class Trace
    {
        private final Side from;
        private final Side to;
        /**
         * Which of the two tested directions this segment belongs to.
         *
         * No longer changes how it is DRAWN - both directions share one line, and the chevrons say
         * which ways it runs - but it still decides how many chevrons a square gets, because a route
         * that works both ways contributes a segment from each direction.
         */
        private final boolean forward;

        public Trace(Side from, Side to, boolean forward)
        {
            this.from = from;
            this.to = to;
            this.forward = forward;
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof Trace)) return false;

            Trace other = (Trace) o;

            return from == other.from && to == other.to && forward == other.forward;
        }

        @Override
        public int hashCode()
        {
            return (from == null ? 0 : from.ordinal() * 31)
                + (to == null ? 0 : to.ordinal() * 7) + (forward ? 1 : 0);
        }

        @Override
        public String toString()
        {
            return (forward ? "->" : "<-") + from + ":" + to;
        }
    }

    /**
     * @param marks the routes to draw, or empty to draw none
     * @param length the tile's length, or a negative number not to show one
     * @param selected whether this tile is part of a bulk selection
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected)
    {
        this(marks, length, selected, null, false);
    }

    /**
     * @param marks the routes to draw, or empty to draw none
     * @param length the tile's length, or a negative number not to show one
     * @param selected whether this tile is part of a bulk selection
     * @param badge what this sensor is, or null when the square is not a point at all
     * @param ignored whether autonomy takes no notice of this square at all
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored)
    {
        this(marks, length, selected, badge, ignored, false);
    }

    /**
     * @param curved whether the track here is drawn as a chord across a corner, so the arrows follow
     *        it rather than the edge they leave through
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved)
    {
        this(marks, length, selected, badge, ignored, curved, false);
    }

    /**
     * @param portal whether this square is a paired link, whose one side carries traffic both ways
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal)
    {
        this(marks, length, selected, badge, ignored, curved, portal, null);
    }

    /**
     * @param traces the tested path through this square, one segment per direction that works
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal, List<Trace> traces)
    {
        this(marks, length, selected, badge, ignored, curved, portal, traces, false);
    }

    /**
     * @param blockedOnly whether to draw only the directions that are shut
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal, List<Trace> traces, boolean blockedOnly)
    {
        this.curved = curved;
        this.portal = portal;
        this.blockedOnly = blockedOnly;
        this.traces = traces == null ? Collections.<Trace>emptyList() : new ArrayList<>(traces);

        this.marks = marks == null ? Collections.<Mark>emptyList() : new ArrayList<>(marks);
        this.length = length;
        this.selected = selected;
        this.badge = badge;
        this.ignored = ignored;
    }

    public boolean isIgnored()
    {
        return ignored;
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
        return marks.isEmpty() && length < 0 && !selected && badge == null && !ignored
            && traces.isEmpty();
    }

    public List<Trace> getTraces()
    {
        return Collections.unmodifiableList(traces);
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

        // Restored with the rest.  The ignored branch sets a composite and returns, so without this the
        // only thing keeping a half-transparent brush from leaking out is that the caller happens to
        // hand over a scratch Graphics it then throws away.
        java.awt.Composite oldComposite = g.getComposite();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // A square autonomy takes no notice of is greyed out and nothing else is drawn on it.
            // Nothing here is the user's to decide, so anything drawn would invite a click.
            // Grey means one thing on this diagram: autonomy cannot use this square.  It used to mean
            // that AND "this track follows another tile", which are unrelated, so a reader could not
            // tell "you cannot set this" from "this is already set, over there".  Track that follows
            // its run is now drawn plainly and simply carries no arrows of its own; the flash on the
            // tile that changed is what points at where the answer lives.
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

            // Knock the tile art back before drawing on it.  Thin lines over a busy icon are the same
            // contrast problem as writing on a photograph; this is the caption box behind the writing.
            // No wash when only the shut directions are drawn.  It exists to lift thin arrows off busy
            // tile art, and with the open ones gone there is little left to lift - so all it did was
            // grey most of the layout to make a handful of red arrows very slightly crisper.
            if (!marks.isEmpty() && !blockedOnly)
            {
                java.awt.Composite before = g.getComposite();

                g.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, DIM));
                g.setColor(DIM_COLOUR);
                g.fillRect(0, 0, width, height);

                g.setComposite(before);
            }

            paintArrows(g, width, height);

            if (badge != null) paintBadge(g, width, height);

            if (length >= 0) paintLength(g, width, height);

            // Last, and so on top of everything: the arrows, the badge, the length, the outline.
            //
            // It was drawn underneath, so a broad line would not cover the directions being tested
            // against - but a tested path is a transient answer to a question just asked, and while it
            // is up it is the thing being looked at.  Anything it covers is still there a moment later
            // when the next click clears it.
            paintTraces(g, width, height);

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
            g.setComposite(oldComposite);

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
     * Draws one arrow per SIDE of the tile, rather than two per route.
     *
     * A route drew an arrow at each of its own two ends, which is right until two routes share an end.
     * A switch's branches all meet at its toe, so the toe collected one arrow per branch, stacked in
     * the same place - and where one branch was open and another shut, a green arrow sat on top of a
     * red one and the tile said both things at once.
     *
     * A side is the right unit anyway, because it is the question a train asks: may I leave this way?
     * The answer is yes if ANY route through the tile permits it, so the arrows are combined rather
     * than drawn over each other.
     */
    private void paintArrows(Graphics2D g, int width, int height)
    {
        // side -> may a train travel out through it
        java.util.Map<Side, Boolean> out = new java.util.LinkedHashMap<>();

        for (Mark mark : marks)
        {
            Direction direction = mark.getDirection();

            allow(out, mark.getA(), direction == Direction.BOTH || direction == Direction.TOWARD_A);
            allow(out, mark.getB(), direction == Direction.BOTH || direction == Direction.TOWARD_B);
        }

        // Drawn at full strength whatever the state.  Small and pale for open track was an attempt to
        // keep a bare layout quiet, and on a real one it read as grey track with specks on it - the
        // squares stopped looking like track that had been decided and started looking like track
        // something had gone wrong with.
        for (java.util.Map.Entry<Side, Boolean> entry : out.entrySet())
        {
            int[] target = midpoint(entry.getKey(), width, height);

            if (target == null) continue;

            if (blockedOnly && Boolean.TRUE.equals(entry.getValue())) continue;

            double[] outward = heading(entry.getKey(), width, height);
            int span = Math.min(width, height);

            if (portal)
            {
                portalArrows(g, target, outward, entry.getValue(), span);
                continue;
            }

            arrow(g, target, outward, entry.getValue(), span);
        }
    }

    /**
     * A link's two moves, drawn side by side at its one side.
     *
     * Onto the track and into the link happen at the same edge, so as a single arrow one of them is
     * always invisible - and it was the departure, because an arrow at a side means "a train may leave
     * the tile this way" and the drawing had nowhere to put the other sense.  Two arrows offset along
     * the edge, one out and one in, say both without either being guessed at.
     *
     * @param target the midpoint of the link's own side
     * @param outward the direction leading off the tile
     */
    private void portalArrows(Graphics2D g, int[] target, double[] outward, boolean allowed, int span)
    {
        double length = Math.sqrt(outward[0] * outward[0] + outward[1] * outward[1]);

        if (length < 1) return;

        double dx = outward[0] / length;
        double dy = outward[1] / length;

        // along the edge, so the two never sit on top of each other
        double gap = Math.max(3.0, span / 5.0);

        int[] leaving = new int[] {
            (int) Math.round(target[0] - dy * gap),
            (int) Math.round(target[1] + dx * gap)};

        // the arrival stops short of the edge and points inward, so the pair reads as a two-way door
        int[] arriving = new int[] {
            (int) Math.round(target[0] + dy * gap - dx * span * 0.5),
            (int) Math.round(target[1] - dx * gap - dy * span * 0.5)};

        arrow(g, leaving, outward, allowed, span);
        arrow(g, arriving, new double[] {-outward[0], -outward[1]}, allowed, span);
    }

    /**
     * The tested path, drawn through the square rather than around it.
     *
     * The two directions are offset to either side of the rails, so a route that works both ways shows
     * two lines and one that works one way shows one - which is the whole answer, read off the track
     * instead of out of a sentence.
     */
    private void paintTraces(Graphics2D g, int width, int height)
    {
        if (traces.isEmpty()) return;

        int span = Math.min(width, height);
        int[] centre = new int[] {width / 2, height / 2};

        // The route ONCE, however many directions it carries.  Drawn as one line per direction it was
        // two lines down the same piece of track wherever a route worked both ways, which is the
        // commonest case - so the picture was mostly duplicates, and the duplicate said nothing the
        // single line did not.
        g.setStroke(new BasicStroke(Math.max(3f, span / 7f),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Which legs cross this square.  Where a route works both ways over the SAME track the two
        // legs land on the same segment and it is drawn once; where they go different ways round - a
        // loop, a passing siding - the squares only one leg uses are drawn in that leg's own shade, so
        // the picture reads as an out and a back rather than as one circuit nobody asked for.
        java.util.Map<String, Boolean> shared = new java.util.LinkedHashMap<>();

        for (Trace trace : traces)
        {
            String at = trace.from + ":" + trace.to;

            if (shared.containsKey(at))
            {
                shared.put(at, Boolean.TRUE);
            }
            else
            {
                shared.put(at, Boolean.FALSE);
            }
        }

        java.util.Set<String> drawn = new java.util.LinkedHashSet<>();

        for (Trace trace : traces)
        {
            int[] a = trace.from == null ? centre : midpoint(trace.from, width, height);
            int[] b = trace.to == null ? centre : midpoint(trace.to, width, height);

            if (a == null || b == null) continue;

            String at = trace.from + ":" + trace.to;

            if (!drawn.add(at)) continue;

            g.setColor(Boolean.TRUE.equals(shared.get(at)) ? TRACE
                : trace.forward ? TRACE : TRACE_RETURN);

            g.drawLine(a[0], a[1], centre[0], centre[1]);
            g.drawLine(centre[0], centre[1], b[0], b[1]);
        }

        // Which way, as chevrons on the line.  Two of them, pointing opposite ways, is a route that
        // works both ways; one is a route that works one way; and the direction is read off the shape
        // rather than off a colour nobody can be expected to have learnt.
        g.setStroke(new BasicStroke(Math.max(2f, span / 14f),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g.setColor(TRACE_CHEVRON);

        for (Trace trace : traces)
        {
            int[] a = trace.from == null ? centre : midpoint(trace.from, width, height);
            int[] b = trace.to == null ? centre : midpoint(trace.to, width, height);

            if (a == null || b == null) continue;

            // On the half the train is heading INTO, so a chevron sits on open track rather than in
            // the middle where the two halves meet and two of them would overlap.
            chevron(g, centre, b, span);
        }
    }

    /**
     * A single arrowhead on the line, pointing from one point towards another.
     */
    private void chevron(Graphics2D g, int[] from, int[] to, int span)
    {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 2) return;

        dx /= length;
        dy /= length;

        // Two thirds of the way along, which keeps it clear of both the tile edge and the centre
        double atX = from[0] + dx * length * 0.66;
        double atY = from[1] + dy * length * 0.66;

        double size = Math.max(3.0, span / 6.0);
        double angle = Math.atan2(dy, dx);

        for (int i = 0; i < 2; i++)
        {
            double barb = i == 0 ? 2.5 : -2.5;

            g.drawLine((int) Math.round(atX), (int) Math.round(atY),
                (int) Math.round(atX + Math.cos(angle + barb) * size),
                (int) Math.round(atY + Math.sin(angle + barb) * size));
        }
    }

    /**
     * Which way the track actually runs where it leaves through one side.
     *
     * A curve on this diagram is not an arc - it is a straight chord cutting the corner, from the
     * midpoint of one edge to the midpoint of the next.  So the track at the E side of an E-S curve
     * runs up and to the right at forty-five degrees, and an arrow drawn due east sits across it
     * instead of along it.  Taking the heading from the chord puts every arrow on its own rail.
     *
     * Curves only.  A switch is built from chords too, but its arrows read better square to the edges
     * - which is how its trunk enters anyway - and tilting them was a step the author did not ask for.
     * A shared side would be ambiguous regardless: two chords through one edge disagree by forty-five
     * degrees, and there is only one arrow there to draw.
     */
    private double[] heading(Side side, int width, int height)
    {
        Side other = null;
        int through = 0;

        for (Mark mark : marks)
        {
            if (mark.getA() == side || mark.getB() == side)
            {
                through++;
                other = mark.getA() == side ? mark.getB() : mark.getA();
            }
        }

        int[] from = curved && through == 1 && other != null && other != side
            ? midpoint(other, width, height) : new int[] {width / 2, height / 2};

        int[] to = midpoint(side, width, height);

        return new double[] {to[0] - from[0], to[1] - from[1]};
    }

    /**
     * Records what a route says about one side, without letting a "no" overrule a "yes" from another
     * route through the same side.
     */
    private static void allow(java.util.Map<Side, Boolean> out, Side side, boolean allowed)
    {
        if (side == null) return;

        out.put(side, allowed || Boolean.TRUE.equals(out.get(side)));
    }

    /**
     * One direction of one route: an arrowhead pointing out of the tile at the side it leads to, or the
     * same place crossed out when trains may not go that way.
     *
     * @param target the midpoint of the side this direction leads to
     * @param heading which way the track runs there, not necessarily square to the edge - see heading()
     * @param allowed whether trains may travel this way
     * @param span the smaller of the tile's two dimensions, which sets the arrowhead size
     */
    private void arrow(Graphics2D g, int[] target, double[] heading, boolean allowed, int span)
    {
        double dx = heading[0];
        double dy = heading[1];
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 1) return;

        dx /= len;
        dy /= len;

        // The SHUT direction is the filled one, and the larger.  It was the other way round, which
        // gave the most ink on the diagram to the state that says nothing needs attention - and a
        // reader scanning for what is restricted was scanning for the faint shapes.
        //
        // Filled against hollow carries the difference without colour, so it survives being printed,
        // a poor screen, and a reader who cannot tell red from green.  Colour is never the only thing
        // saying it.
        double size = Math.max(4.0, span / 3.2) * (allowed ? 0.82 : 1.0);

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
            // hollow, so it reads as an outline even where the colour does not come through
            g.setColor(Color.WHITE);
            g.fillPolygon(xs, ys, 3);

            g.setColor(ONE_WAY);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(xs, ys, 3);
        }
        else
        {
            g.setColor(CLOSED);
            g.fillPolygon(xs, ys, 3);
        }
    }

    /**
     * How far short of the tile edge an arrowhead stops.  One pixel, so that the arrows on two
     * neighbouring tiles are visibly separate marks rather than one continuous shape.
     */
    private static final int EDGE_GAP = 1;

    /**
     * The tested route: a broad yellow line, the colour this application already uses to say "look
     * here" on the running diagram, and one nothing else in this editor uses.
     */
    private static final Color TRACE = new Color(255, 214, 0);

    /**
     * The way back, where it does not share track with the way out.
     *
     * Both legs in one colour is right while they run over the same rails - two lines down one piece
     * of track say nothing twice - and wrong the moment they go different ways round, where the single
     * colour turns an out-and-back into what looks like one circuit.
     */
    private static final Color TRACE_RETURN = new Color(255, 150, 40);

    /** The chevrons on it, dark enough to read against the yellow they sit on. */
    private static final Color TRACE_CHEVRON = new Color(120, 80, 0);

    /**
     * Whether a reversing point is drawn as a cross rather than as a small square.
     *
     * False, at the author's instruction: the cross read as an error marker rather than as a statement
     * about direction.  Kept as a switch rather than deleted, because the shape is a judgement about
     * what a diagram reads like and those get revisited - flip this one word and cross() is back.
     */
    private static final boolean REVERSING_AS_CROSS = false;

    /**
     * Draws what a sensor IS, in the graph window's own shapes and colours.
     *
     *   plain point       small diamond
     *   station           circle
     *   terminus          square
     *   reversing         small square
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
            // A small square, at the non-station size, so it reads as a lesser relative of the
            // terminus square rather than as a different kind of thing altogether - which is what it
            // is: the same act of switching direction, on a square trains do not stop at.  The cross
            // it used to be said "something is wrong here" more than it said anything about direction.
            if (REVERSING_AS_CROSS)
            {
                cross(g, x, y, size, fill, line);
            }
            else
            {
                g.setColor(fill);
                g.fillRect(x, y, size, size);
                g.setColor(line);
                g.drawRect(x, y, size, size);
            }
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
            && ignored == other.ignored && curved == other.curved && portal == other.portal
            && traces.equals(other.traces) && blockedOnly == other.blockedOnly
            && marks.equals(other.marks);
    }

    @Override
    public int hashCode()
    {
        return marks.hashCode() * 31 + length * 2
            + (selected ? 1 : 0) + (badge == null ? 0 : badge.hashCode() * 4)
            + (ignored ? 16 : 0) + (curved ? 64 : 0) + (portal ? 256 : 0) + traces.hashCode() * 3 + (blockedOnly ? 512 : 0);
    }

    @Override
    public String toString()
    {
        return marks + (length >= 0 ? " len=" + length : "") + (selected ? " selected" : "")
            + (badge == null ? "" : " " + badge)
            + (ignored ? " ignored" : "");
    }
}
