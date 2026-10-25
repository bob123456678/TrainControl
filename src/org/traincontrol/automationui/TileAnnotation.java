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
 * What a tile has been set to allow, drawn on the tile while autonomy is being set up: the authoring counterpart to
 * TileOverlay (what trains are doing).  Thin lines with chevrons, not a tint, because a tint cannot say WHICH WAY.
 *
 * @author Adam
 */
public class TileAnnotation
{
    /** One route of a tile, and which way it may be traveled. */
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

    /** Trains may go this way.  Both ways is two green arrows, one way a green and a red, closed two reds. */
    private static final Color ONE_WAY = new Color(70, 205, 90);

    /** The edge round a green arrow: reads on both the black rail and the white around it (MT-675). */
    private static final Color ONE_WAY_EDGE = new Color(0, 95, 40);

    /** Closed.  The only mark drawn as a bar rather than a path, because it means a train cannot get through. */
    private static final Color CLOSED = new Color(200, 0, 0);

    /** The red the diagram writes accessory addresses in, so one color means small numbers over track art. */
    private static final Color LENGTH = Color.RED;


    /** Selected for a bulk edit: a border, not a fill, so the track stays readable while forty tiles are picked. */
    private static final Color SELECTED = new Color(255, 140, 0);

    /** How much the tile art is dimmed under the marks, so the thin chevrons do not vanish into busy icons. */
    private static final float DIM = 0.55f;

    private static final Color DIM_COLOUR = Color.WHITE;

    /** The color of a square autonomy uses (the graph window's blue) and of one it leaves alone (FR-103, MT-492). */
    private static final Color POINT_ACTIVE = DiagramColours.STATION;
    private static final Color POINT_INACTIVE = new Color(128, 130, 134);

    /** A square nothing can pass (the X): orange, so it reads apart from a berth autonomy merely does not choose. */
    private static final Color POINT_IMPASSABLE = new Color(255, 102, 0);

    /**
     * What a sensor has been designated as, drawn as a badge on its tile.  The SHAPE says what turning means, the SIZE
     * whether it is a station.  Blue means autonomy uses it, gray it does not (FR-103), orange that nothing can pass.
     *
     *                       trains do not turn    trains MAY turn    trains ALWAYS turn
     *     a station              big circle          big diamond         big square
     *     a passing point       small circle        small diamond       small square
     *
     * Terminus and parking are independent flags: must a train leave the way it came, and does autonomy choose the
     * station on its own.
     */
    public static class Badge
    {
        private final boolean station;
        private final boolean terminus;
        private final boolean optional;
        private final boolean reversing;
        private final boolean parking;
        private final boolean named;

        /** Switched OFF (OB-167), unlike `parking`, which only leaves the square out of autonomy's choices. */
        private final boolean shut;

        // Which route the badge sits on, to draw it on the rails; null when unknown (center).
        private final Side a;
        private final Side b;

        /** The side whose track runs out before another sensor (FR-118), or null; a station there is a terminus. */
        private final Side deadEnd;

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
            this(station, terminus, reversing, parking, named, a, b, false);
        }

        /** @param optional whether turning round here is a choice rather than compulsory */
        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional)
        {
            this(station, terminus, reversing, parking, named, a, b, optional, false);
        }

        /** @param shut whether the square is switched off, so that nothing may pass or stop there */
        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional, boolean shut)
        {
            this(station, terminus, reversing, parking, named, a, b, optional, shut, null);
        }

        /** @param deadEnd the side of the road whose track runs out before it reaches another sensor, or null */
        public Badge(boolean station, boolean terminus, boolean reversing, boolean parking,
            boolean named, Side a, Side b, boolean optional, boolean shut, Side deadEnd)
        {
            this.deadEnd = deadEnd;
            this.shut = shut;
            this.optional = optional;
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

        /** The side of the road whose track runs out before another sensor (FR-118), or null. */
        public Side getDeadEnd()
        {
            return deadEnd;
        }

        public boolean isStation()
        {
            return station;
        }

        public boolean isTerminus()
        {
            return terminus;
        }

        /** Whether turning round here is a choice rather than compulsory. */
        public boolean isOptional()
        {
            return optional;
        }

        public boolean isReversing()
        {
            return reversing;
        }

        public boolean isParking()
        {
            return parking;
        }

        /** Whether nothing can use this square (OB-167): switched off, though the station flag is left as it was. */
        public boolean isImpassable()
        {
            return shut;
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
                && optional == other.optional
                && reversing == other.reversing && parking == other.parking && named == other.named
                && shut == other.shut
                && a == other.a && b == other.b && deadEnd == other.deadEnd;
        }

        @Override
        public int hashCode()
        {
            return (station ? 1 : 0) + (terminus ? 2 : 0) + (reversing ? 4 : 0)
                + (parking ? 8 : 0) + (named ? 16 : 0) + (optional ? 32 : 0) + (shut ? 64 : 0);
        }

        @Override
        public String toString()
        {
            if (isImpassable()) return "shut" + (named ? "" : " (unnamed)");

            return (station ? (parking ? "parking" : "station") : "point")
                + (terminus ? (optional ? " may turn" : " terminus") : "")
                + (reversing ? " reversing" : "")
                + (named ? "" : " (unnamed)");
        }
    }

    /** Autonomy takes no notice of this square: washed out rather than grayed, so it recedes. */
    private static final Color IGNORED = Color.WHITE;

    private static final float IGNORED_ALPHA = 0.62f;

    /** Hatching over a square autonomy cannot use, so "unavailable" is a pattern and not only a shade. */
    private static final Color HATCH = new Color(150, 150, 150);

    private final List<Mark> marks;
    private final int length;
    private final boolean selected;
    private final Badge badge;
    private final boolean ignored;

    /** Whether the track is a corner-cutting chord (curve or double curve only); a switch's arrows stay square. */
    private final boolean curved;

    /** Whether this square is a paired link, whose only route is a STUB drawn with a single arrow. */
    private final boolean portal;

    /** Segments of a tested path through this square, one per direction that works. */
    private final List<Trace> traces;

    /**
     * Which sides a train may come in by and STOP, where restricted; the arrows say which way traffic FLOWS.  Empty on
     * almost every square: the default is arrival from anywhere.
     */
    private final List<Arrival> arrivals;

    /** One side of a station, and whether trains may arrive by it. */
    public static class Arrival
    {
        private final Side side;
        private final boolean allowed;

        public Arrival(Side side, boolean allowed)
        {
            this.side = side;
            this.allowed = allowed;
        }

        public Side getSide()
        {
            return side;
        }

        public boolean isAllowed()
        {
            return allowed;
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof Arrival)) return false;

            Arrival other = (Arrival) o;

            return side == other.side && allowed == other.allowed;
        }

        @Override
        public int hashCode()
        {
            return (side == null ? 0 : side.hashCode()) * 31 + (allowed ? 1 : 0);
        }

        @Override
        public String toString()
        {
            return (allowed ? "arrive " : "no arrival ") + side;
        }
    }

    /** Whether only the SHUT directions are drawn: open track is most of the ink and says what can be assumed. */
    private final boolean blockedOnly;

    /** One square's worth of a tested path: in by one side, out by another; a null side is an end of the run. */
    public static class Trace
    {
        private final Side from;
        private final Side to;
        /** Which of the two tested directions this segment belongs to; it decides how many chevrons a square gets. */
        private final boolean forward;

        /**
         * Whether the station this leg ENDS at is never chosen by autonomy (parking, or a square that turns every
         * train).
         */
        private final boolean manualOnly;

        public Trace(Side from, Side to, boolean forward)
        {
            this(from, to, forward, false);
        }

        public Trace(Side from, Side to, boolean forward, boolean manualOnly)
        {
            this.from = from;
            this.to = to;
            this.forward = forward;
            this.manualOnly = manualOnly;
        }

        /** @return whether this leg ends somewhere only a person may send a train */
        public boolean isManualOnly()
        {
            return manualOnly;
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof Trace)) return false;

            Trace other = (Trace) o;

            return from == other.from && to == other.to && forward == other.forward
                && manualOnly == other.manualOnly;
        }

        @Override
        public int hashCode()
        {
            return (from == null ? 0 : from.ordinal() * 31)
                + (to == null ? 0 : to.ordinal() * 7) + (forward ? 1 : 0)
                + (manualOnly ? 2 : 0);
        }

        @Override
        public String toString()
        {
            return (forward ? "->" : "<-") + from + ":" + to + (manualOnly ? " (manual only)" : "");
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
     * @param badge what this sensor is, or null when the square is not a point at all
     * @param ignored whether autonomy takes no notice of this square at all
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored)
    {
        this(marks, length, selected, badge, ignored, false);
    }

    /** @param curved whether the track is drawn as a chord across a corner, so the arrows follow it */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved)
    {
        this(marks, length, selected, badge, ignored, curved, false);
    }

    /** @param portal whether this square is a paired link, whose one side carries traffic both ways */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal)
    {
        this(marks, length, selected, badge, ignored, curved, portal, null);
    }

    /** @param traces the tested path through this square, one segment per direction that works */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal, List<Trace> traces)
    {
        this(marks, length, selected, badge, ignored, curved, portal, traces, false);
    }

    /** @param blockedOnly whether to draw only the directions that are shut */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal, List<Trace> traces, boolean blockedOnly)
    {
        this(marks, length, selected, badge, ignored, curved, portal, traces, blockedOnly, null);
    }

    /**
     * @param arrivals which sides trains may arrive by, or null/empty for anywhere (the usual case, drawing nothing)
     */
    public TileAnnotation(List<Mark> marks, int length, boolean selected, Badge badge,
        boolean ignored, boolean curved, boolean portal, List<Trace> traces, boolean blockedOnly,
        List<Arrival> arrivals)
    {
        this.arrivals = arrivals == null
            ? Collections.<Arrival>emptyList() : new ArrayList<>(arrivals);

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

    /**
     * Whether this is drawn in the autonomy EDITOR rather than on a diagram.  Only FR-118's icons
     * (`StationIcons.SHAPES`) turn on it: there every point's icon is white with its color round it, as the arrows over
     * it are what the editor is read for.  Set rather than passed: it describes the SURFACE, not the square.
     */
    public TileAnnotation inTheEditor()
    {
        this.editing = true;

        return this;
    }

    private boolean editing = false;

    /** Also draws the green allowed-direction arrows, with no wash (FR-110: Display Allowed Directions). */
    public TileAnnotation withAllowedDirections()
    {
        this.allowedToo = true;
        return this;
    }

    private boolean allowedToo = false;

    /**
     * Grays the square as one autonomy takes no notice of, but still draws its length and outline (OB-353); no arrows.
     */
    public TileAnnotation withNoStation()
    {
        this.stationless = true;
        return this;
    }

    private boolean stationless = false;

    /** Whether this square is grayed for having no station on its track (OB-353). */
    public boolean isStationless()
    {
        return stationless;
    }

    /** Draws only the green allowed directions (the editor's Allowed only); the wash stays, to lift the open arrows. */
    public TileAnnotation withoutRestrictions()
    {
        this.allowedOnly = true;
        return this;
    }

    private boolean allowedOnly = false;

    /**
     * Draws no arrow at one side (null for none), where a two-square crossing's squares meet and the arrows would
     * repeat the track's outer ones (OB-322).
     */
    public TileAnnotation withoutArrowsAt(Side side)
    {
        this.bareSide = side;
        return this;
    }

    private Side bareSide = null;

    /** Says a train is set up to stand on this square. */
    public TileAnnotation withTrain()
    {
        this.occupied = true;

        return this;
    }

    private boolean occupied = false;

    /**
     * Says a length rule reads this square and nobody has given it a length (Unmeasured Track).  Carried through
     * `isBlank`, `equals` and `hashCode` as well as `paint` (OB-007): plain track has nothing else to say.
     */
    public TileAnnotation needsALength()
    {
        this.unmeasured = true;

        return this;
    }

    private boolean unmeasured = false;

    /** Whether this square is marked as needing a length. */
    public boolean isUnmeasured()
    {
        return unmeasured;
    }

    /** Amber wash: the outline already means "selected", gray "autonomy cannot use this", red a finding. */
    private static final Color UNMEASURED_COLOUR = new Color(245, 158, 11);
    private static final float UNMEASURED_ALPHA = 0.38f;

    private void paintUnmeasured(Graphics2D g, int width, int height)
    {
        java.awt.Composite before = g.getComposite();

        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, UNMEASURED_ALPHA));
        g.setColor(UNMEASURED_COLOUR);
        g.fillRect(0, 0, width, height);

        g.setComposite(before);
    }

    public boolean isIgnored()
    {
        return ignored;
    }

    /** The look of a square autonomy takes no notice of: washed out and hatched; leaves g's composite changed. */
    private static void paintGreyedOut(Graphics2D g, int width, int height)
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
     * Whether there is nothing here worth drawing.  paint() opens with this, so a field added to equals and hashCode
     * belongs here too, or it is never painted when it is the ONLY thing on a square (OB-007).
     */
    public boolean isBlank()
    {
        // `editing` is deliberately NOT here, unlike in equals and hashCode: on its own it paints nothing (it only
        // says what color a badge is), so an annotation carrying it and nothing else is still blank.
        return marks.isEmpty() && length < 0 && !selected && badge == null && !ignored && !stationless
            && traces.isEmpty() && arrivals.isEmpty() && !occupied && !unmeasured;
    }

    public List<Trace> getTraces()
    {
        return Collections.unmodifiableList(traces);
    }

    public List<Arrival> getArrivals()
    {
        return Collections.unmodifiableList(arrivals);
    }

    /** Paints over a tile that has already drawn itself; g is already translated to the tile's origin. */
    public void paint(Graphics2D g, int width, int height)
    {
        paint(g, width, height, false);
    }

    /** The same, told whether the tile underneath is already drawn faint (OB-212), so this wash gives way. */
    public void paint(Graphics2D g, int width, int height, boolean alreadyFaded)
    {
        paint(g, width, height, alreadyFaded
            ? new java.awt.Rectangle(0, 0, width, height) : null);
    }

    /**
     * The same, told exactly WHICH part of the tile is faint (null if none): a double curve is two roads in one tile
     * and a train covers one, so the road with no train keeps its wash.
     */
    public void paint(Graphics2D g, int width, int height, java.awt.Shape alreadyFaint)
    {
        if (isBlank()) return;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        java.awt.Font oldFont = g.getFont();

        // Restored with the rest: the ignored branch sets a composite and returns.
        java.awt.Composite oldComposite = g.getComposite();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // A square autonomy takes no notice of is grayed out and nothing else is drawn on it; anything drawn
            // would invite a click.
            if (ignored)
            {
                paintGreyedOut(g, width, height);

                // The route being tested is still drawn over the graying: it is an answer to the user's question,
                // not something to change.  (The running diagram's painter has no notion of ignored.)
                if (!traces.isEmpty())
                {
                    g.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, 1f));

                    paintTraces(g, width, height);
                }

                return;
            }

            // TRACK NO STATION IS ON, grayed the same way (OB-353) - and the rest drawn over it, as it can still be edited
            if (stationless)
            {
                paintGreyedOut(g, width, height);

                g.setComposite(oldComposite);
            }

            // Knock the tile art back before drawing on it, so thin lines do not fight a busy icon.  No wash when
            // only shut directions are drawn (little to lift), on a paired link (a wash on one in use and none on one
            // ignored would read the wrong way round), nor on a square out of service, which draws no arrows.
            if (!marks.isEmpty() && !blockedOnly && !portal && !outOfService())
            {
                TileOverlay.washWhatIsLeft(g, width, height, alreadyFaint, DIM, DIM_COLOUR);
            }

            // UNDER the arrows and badge, so the highlight says "measure this" without hiding the track or the square.
            if (unmeasured) paintUnmeasured(g, width, height);

            // THE BADGE UNDER THE ARROWS (MT-710): an arrow says where a train may go, which the icon cannot, and
            // FR-118's icons run nearly the width of the square where the arrows are.  `paintBadgeOverRun` draws it
            // again over them.
            if (badge != null) paintBadge(g, width, height);

            paintArrows(g, width, height);

            paintArrivals(g, width, height);

            // Over the badge (MT-099): both are centered on a station, and the changing fact goes on top of the fixed
            // one.
            if (occupied) paintTrainMark(g, width, height);

            if (length >= 0) paintLength(g, width, height);

            // Last, on top of everything: a tested path is a transient answer to a question just asked.
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

    /** The two sides meeting at the corner an arrival chevron on `side` is pushed to by `paintArrivals`, or null. */
    private java.util.Set<Side> cornerOf(Side side, int width, int height)
    {
        int[] at = midpoint(side, width, height);

        if (at == null) return null;

        double dx = width / 2.0 - at[0];
        double dy = height / 2.0 - at[1];
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 1) return null;

        double px = -dy / length;
        double py = dx / length;

        if (offsetCollidesWithTrack(side, px, py))
        {
            px = -px;
            py = -py;
        }

        Side toward = Math.abs(px) > Math.abs(py) ? (px > 0 ? Side.E : Side.W) : (py > 0 ? Side.S : Side.N);

        return java.util.EnumSet.of(side, toward);
    }

    /** Whether this square is out of service (nothing may pass or stop), so it carries no arrows. */
    private boolean outOfService()
    {
        return badge != null && badge.isImpassable();
    }

    /**
     * Whether nudging an arrival arrow this way puts it over the tile's own track (OB-175): on a curve one corner is
     * where the track is, so the nudge goes the other way.
     */
    private boolean offsetCollidesWithTrack(Side arriving, double px, double py)
    {
        if (arriving == null || marks.isEmpty()) return false;

        // Screen coordinates: y grows downward, so a negative y points NORTH.
        Side toward;

        if (Math.abs(px) > Math.abs(py))
        {
            toward = px > 0 ? Side.E : Side.W;
        }
        else
        {
            toward = py > 0 ? Side.S : Side.N;
        }

        // The side the arrow is ON is not a collision - every arrow sits on one.
        if (toward == arriving) return false;

        // The corner to keep clear of is the one the track through this side runs to (MT-714): on a curve its one
        // road's corner, on straight track neither.  (On a double curve every road would match, so no chevron moved.)
        for (Mark mark : marks)
        {
            if (mark.getA() == arriving && mark.getB() == toward || mark.getB() == arriving && mark.getA() == toward)
            {
                return true;
            }
        }

        return false;
    }

    /** The side facing this one, or null for null. */
    private static Side opposite(Side side)
    {
        if (side == null) return null;

        switch (side)
        {
            case N: return Side.S;
            case S: return Side.N;
            case E: return Side.W;
            default: return Side.E;
        }
    }
    /**
     * Where a train may pull in: a chevron at the edge pointing INTO the square, unlike the direction arrows (further
     * in, pointing outward) because they say different things.  Small, so a fully marked station still shows its badge
     * and name.  A barred side is the same chevron left open, in gray (OB-326).
     */
    private void paintArrivals(Graphics2D g, int width, int height)
    {
        // nor where trains may arrive, on a square nothing can arrive at (see `paintArrows`)
        if (arrivals.isEmpty() || outOfService()) return;

        int span = Math.min(width, height);

        // Half the arrowhead's width, and how far in from the edge its point reaches
        double wing = Math.max(2.5, span / 7.0);
        double depth = Math.max(3.0, span / 5.0);

        // Pushed along the edge, clear of the middle where the direction arrows sit, so the two kinds of mark read as
        // two.
        double offset = span / 4.0;

        // AND A LITTLE NEARER THE MIDDLE WHERE TWO SHARE A CORNER (MT-714): on a double curve the chevrons of the two
        // sides meeting at a free corner both go to it; a quarter of the square along, their points met.  A fifth keeps
        // them apart.
        java.util.Map<java.util.Set<Side>, Integer> corners = new java.util.HashMap<>();

        for (Arrival arrival : arrivals)
        {
            java.util.Set<Side> corner = cornerOf(arrival.getSide(), width, height);

            if (corner != null) corners.put(corner, corners.containsKey(corner) ? corners.get(corner) + 1 : 1);
        }

        g.setStroke(new BasicStroke(Math.max(1.2f, span / 22f),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (Arrival arrival : arrivals)
        {
            int[] at = midpoint(arrival.getSide(), width, height);

            if (at == null) continue;

            java.util.Set<Side> corner = cornerOf(arrival.getSide(), width, height);

            double along = corner != null && corners.get(corner) > 1 ? span / 5.0 : offset;

            double dx = width / 2.0 - at[0];
            double dy = height / 2.0 - at[1];
            double length = Math.sqrt(dx * dx + dy * dy);

            if (length < 1) continue;

            dx /= length;
            dy /= length;

            double px = -dy;
            double py = dx;

            // AND AWAY FROM THIS TILE'S OWN TRACK (OB-175).  The offset is a fixed rotation, so on a straight both
            // corners are clear; on a CURVE the run occupies one, and the corner toward a side it uses is avoided.
            if (offsetCollidesWithTrack(arrival.getSide(), px, py))
            {
                px = -px;
                py = -py;
            }

            // Pulled off the edge (shared with the neighbor) and along it, clear of the middle arrow
            double baseX = at[0] + dx * 1.5 + px * along;
            double baseY = at[1] + dy * 1.5 + py * along;

            double tipX = baseX + dx * depth;
            double tipY = baseY + dy * depth;

            java.awt.geom.Path2D head = new java.awt.geom.Path2D.Double();

            head.moveTo(baseX + px * wing, baseY + py * wing);
            head.lineTo(tipX, tipY);
            head.lineTo(baseX - px * wing, baseY - py * wing);

            if (arrival.isAllowed())
            {
                head.closePath();

                g.setColor(ARRIVAL);
                g.fill(head);

                g.setColor(ARRIVAL_EDGE);
                g.draw(head);
            }
            else
            {
                // THE CHEVRON ALONE, ITS ARMS SCALED WITH THE SQUARE, IN GRAY (OB-326): the same shape at 30 and 60
                // pixels, where a hollow chevron with strokes through it would read as a hollow triangle.
                java.awt.Stroke was = g.getStroke();

                g.setColor(ARRIVAL_BARRED);
                g.setStroke(new BasicStroke((float) Math.max(2.0, span / 14.0), BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND));
                g.draw(head);
                g.setStroke(was);
            }
        }
    }

    /**
     * Draws one arrow per SIDE of the tile, not two per route: a switch's branches meet at its toe, and a side is
     * permitted if ANY route through the tile permits it.
     */
    private void paintArrows(Graphics2D g, int width, int height)
    {
        // NONE ON A SQUARE OUT OF SERVICE: nothing can pass it, so a way through says nothing; the X says it all.
        if (outOfService()) return;

        // side -> may a train travel out through it
        java.util.Map<Side, Boolean> out = new java.util.LinkedHashMap<>();

        for (Mark mark : marks)
        {
            Direction direction = mark.getDirection();

            allow(out, mark.getA(), direction == Direction.BOTH || direction == Direction.TOWARD_A);
            allow(out, mark.getB(), direction == Direction.BOTH || direction == Direction.TOWARD_B);
        }

        // Drawn at full strength whatever the state: small and pale arrows for open track made the squares look
        // like track something had gone wrong with, not track that had been decided.
        for (java.util.Map.Entry<Side, Boolean> entry : out.entrySet())
        {
            int[] target = midpoint(entry.getKey(), width, height);

            if (target == null) continue;

            // nothing where a crossing's two squares meet (OB-322)
            if (entry.getKey() == bareSide) continue;

            if (blockedOnly && !allowedToo && Boolean.TRUE.equals(entry.getValue())) continue;

            if (allowedOnly && !Boolean.TRUE.equals(entry.getValue())) continue;

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
     * A link's two moves, drawn side by side at its one side: onto the track and into the link happen at the same edge,
     * so as a single arrow one is always invisible.  `target` is that side's midpoint, `outward` leads off the tile.
     */
    private void portalArrows(Graphics2D g, int[] target, double[] outward, boolean allowed, int span)
    {
        double length = Math.sqrt(outward[0] * outward[0] + outward[1] * outward[1]);

        if (length < 1) return;

        double dx = outward[0] / length;
        double dy = outward[1] / length;

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
     * A small star in the middle of a square, saying a train is set up to stand here; the caption is on another
     * square or gone with the labels.  White with a dark edge, because the tile art is not one color.
     */
    private void paintTrainMark(Graphics2D g, int width, int height)
    {
        int span = Math.min(width, height);

        // A SEVENTH of the tile (MT-057, OB-037): a sixth read as a shape in its own right, an eighth vanished because
        // the stroke widths below are floored at 3.0 and 1.6.  The floors and the arm are three numbers that agree.
        double arm = Math.max(2.5, span / 7.0);

        // On the BADGE wherever it went (MT-124), and on the TRACK: the editor moves a curved station's badge to the
        // corner.  Falls back to the track center when no badge was drawn.
        int[] on = badgeDrawnAt != null ? badgeDrawnAt : trackCentre(width, height);

        double centreX = on[0];
        double centreY = on[1];

        java.awt.geom.Path2D star = new java.awt.geom.Path2D.Double();

        // Six arms rather than four: four reads as a plus, which on a track diagram is a crossing
        for (int point = 0; point < 6; point++)
        {
            double angle = Math.PI * point / 3.0;

            star.moveTo(centreX, centreY);
            star.lineTo(centreX + Math.cos(angle) * arm, centreY + Math.sin(angle) * arm);
        }

        g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));

        g.setStroke(new BasicStroke((float) Math.max(1.8, arm / 1.6),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(TRAIN_MARK_EDGE);
        g.draw(star);

        g.setStroke(new BasicStroke((float) Math.max(1.0, arm / 3.0),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(TRAIN_MARK);
        g.draw(star);
    }

    private static final Color TRAIN_MARK = Color.WHITE;

    private static final Color TRAIN_MARK_EDGE = new Color(40, 40, 40, 180);

    /** The tested path, drawn through the square. */
    private void paintTraces(Graphics2D g, int width, int height)
    {
        if (traces.isEmpty()) return;

        int span = Math.min(width, height);

        // Where a traced line stops when it has no side to leave by (the first and last square of a run, built with a
        // null end deliberately): the track midpoint (OB-026), since on a bend the tile center is nowhere near the
        // rails.
        int[] centre = trackCentre(width, height);

        // The route ONCE, however many directions it carries: a second line on the same track says nothing more.
        g.setStroke(new BasicStroke(Math.max(3f, span / TRACE_STROKE_PARTS),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Where a route works both ways over the SAME track the two legs share a segment, drawn once; where they go
        // different ways round (a loop, a passing siding) a square only one leg uses is drawn in that leg's shade.
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

        java.util.Set<String> rails = new java.util.HashSet<>();

        for (Trace trace : traces)
        {
            int[] a = trace.from == null ? centre : midpoint(trace.from, width, height);
            int[] b = trace.to == null ? centre : midpoint(trace.to, width, height);

            if (a == null || b == null) continue;

            String at = trace.from + ":" + trace.to;

            if (!drawn.add(at)) continue;

            // WHERE THE LINE GOES BEATS WHICH WAY IT IS RUNNING: a leg ending somewhere autonomy will never choose is
            // drawn in its own color whichever direction it is; the chevrons already say the direction.
            g.setColor(trace.manualOnly ? TRACE_MANUAL
                : Boolean.TRUE.equals(shared.get(at)) ? TRACE
                : trace.forward ? TRACE : TRACE_RETURN);

            // THE SECOND PASS ALONG THE SAME RAIL IS NUDGED, BY HALF A NINTH, AND NO MORE (OB-216).  The nudge is a
            // fixed fraction, not a multiple of the segment's position in the list, which would carry a fourth segment
            // off its rail.
            String rail = String.valueOf(trace.from).compareTo(String.valueOf(trace.to)) <= 0
                ? trace.from + ":" + trace.to : trace.to + ":" + trace.from;

            double nudge = rails.add(rail) ? 0 : span / TRACE_NUDGE_PARTS;

            double dx = b[0] - a[0];
            double dy = b[1] - a[1];
            double length = Math.sqrt(dx * dx + dy * dy);

            double px = length < 1 ? 0 : -dy / length * nudge;
            double py = length < 1 ? 0 : dx / length * nudge;

            // Edge to edge in one stroke, along the rail: a chord between the midpoints of two edges, which is also
            // where heading() takes its arrow directions from.  A line bent through the center would lie at 45 degrees
            // to the track.
            g.drawLine((int) Math.round(a[0] + px), (int) Math.round(a[1] + py),
                (int) Math.round(b[0] + px), (int) Math.round(b[1] + py));
        }

        // Which way, as chevrons: two pointing opposite ways is a route that works both ways, one is one way.
        g.setStroke(new BasicStroke(Math.max(2f, span / 14f),
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        g.setColor(TRACE_CHEVRON);

        for (Trace trace : traces)
        {
            int[] a = trace.from == null ? centre : midpoint(trace.from, width, height);
            int[] b = trace.to == null ? centre : midpoint(trace.to, width, height);

            if (a == null || b == null) continue;

            // On the half the train is heading INTO, so two chevrons do not overlap in the middle, and along the
            // segment's own chord, so that on a curve it points down the rail instead of across it.
            chevron(g, a, b, span);
        }
    }

    /** A single arrowhead on the line, pointing from one point towards another.  Shared with the running overlay. */
    static void chevron(Graphics2D g, int[] from, int[] to, int span)
    {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double length = Math.sqrt(dx * dx + dy * dy);

        if (length < 2) return;

        dx /= length;
        dy /= length;

        // Two thirds of the way along, which keeps it clear of both the tile edge and the center
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
     * Which way the track actually runs where it leaves through one side.  A curve is a chord cutting the corner, so an
     * arrow drawn due east at an E-S curve's E side would sit across the track.  Curves only: a switch's arrows read
     * better square to the edges, and a shared side would be ambiguous.
     */
    private double[] heading(Side side, int width, int height)
    {
        // A Y'S ARROWS LIE ALONG ITS TRACK (MT-676).  Its legs are curves from the toe, so each leg's arrow lies
        // along the chord from the toe, as on a curve, and the toe's runs straight out of the square.
        Side toe = yToe();

        if (toe != null && side != null && side != toe.opposite())
        {
            int[] to = midpoint(side, width, height);
            int[] from = side == toe ? new int[] {width / 2, height / 2} : midpoint(toe, width, height);

            if (to != null && from != null) return new double[] {to[0] - from[0], to[1] - from[1]};
        }

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

        // The fallback is trackCentre, not the geometric center: the same on a straight, but it corrects a bend whose
        // side carries no route or more than one.
        int[] from = curved && through == 1 && other != null && other != side
            ? midpoint(other, width, height) : trackCentre(width, height);

        int[] to = midpoint(side, width, height);

        return new double[] {to[0] - from[0], to[1] - from[1]};
    }

    /**
     * The toe of a Y - two roads that share one side and leave it by the two sides square to it - or null for any other
     * square.  A Y switch and a permanent Y alike; a left or a right, whose straight road runs on through, is not one.
     */
    private Side yToe()
    {
        if (marks.size() != 2) return null;

        Mark one = marks.get(0);
        Mark two = marks.get(1);

        for (Side toe : new Side[] {one.getA(), one.getB()})
        {
            if (toe == null || two.getA() != toe && two.getB() != toe) continue;

            Side legOne = one.getA() == toe ? one.getB() : one.getA();
            Side legTwo = two.getA() == toe ? two.getB() : two.getA();

            if (legOne != null && legTwo != null && legOne == legTwo.opposite() && legOne != toe
                && legOne != toe.opposite())
            {
                return toe;
            }
        }

        return null;
    }

    /** Records what a route says about one side, without letting a "no" overrule a "yes" from another route. */
    private static void allow(java.util.Map<Side, Boolean> out, Side side, boolean allowed)
    {
        if (side == null) return;

        out.put(side, allowed || Boolean.TRUE.equals(out.get(side)));
    }

    /**
     * One direction of one route: an arrowhead pointing out of the tile at the side it leads to, or the same place
     * crossed out when trains may not go that way.  `span` is the tile's smaller dimension, which sets the arrowhead
     * size.
     */
    private void arrow(Graphics2D g, int[] target, double[] heading, boolean allowed, int span)
    {
        double dx = heading[0];
        double dy = heading[1];
        double len = Math.sqrt(dx * dx + dy * dy);

        if (len < 1) return;

        dx /= len;
        dy /= len;

        // The OPEN direction is the filled one and the two are the same size: most squares are open, so otherwise most
        // arrows would be faint.  Filled against hollow survives printing and color blindness.  The shut one is a pixel
        // smaller and further back, since an outline carries a line's worth of ink beyond a filled shape.
        double size = Math.max(4.0, span / 3.2) - (allowed ? 0 : 1);

        // The tip stops short of the edge so two arrows meeting across a tile boundary stay separate; the shut one a
        // pixel further back.
        double gap = EDGE_GAP + (allowed ? 0 : 1);

        double tipX = target[0] - dx * gap;
        double tipY = target[1] - dy * gap;

        // A blocked direction is the SAME arrow in red, not a cross: a cross says "something is wrong here" and leaves
        // the reader to work out which way, so the allowed and the blocked directions are read the same way round.
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

        // OUTLINED ON THE SHAPE ITSELF, not nudged to the pixel grid, whose rounding hangs an outline half a pixel right
        // and down and would pull an arrow off its rail.  Only here: the thin lines elsewhere on a square are crisper
        // on the grid.
        Object control = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);

        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        try
        {
            if (allowed)
            {
                g.setColor(ONE_WAY);
                g.fillPolygon(xs, ys, 3);

                g.setColor(ONE_WAY_EDGE);
                g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolygon(xs, ys, 3);
            }
            else
            {
                // Hollow, outlined in the same weight as the open ones, so the pair read as one mark in two states
                g.setColor(Color.WHITE);
                g.fillPolygon(xs, ys, 3);

                g.setColor(CLOSED);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolygon(xs, ys, 3);
            }
        }
        finally
        {
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                control == null ? RenderingHints.VALUE_STROKE_DEFAULT : control);
        }
    }

    /** How far short of the tile edge an arrowhead stops, so arrows on neighboring tiles stay separate marks. */
    private static final int EDGE_GAP = 1;

    /** The tested route: a broad yellow line, the color the running diagram uses to say "look here". */
    private static final Color TRACE = new Color(255, 214, 0);

    /** The tested route's line is one part in this many of its square's shorter side wide. */
    private static final float TRACE_STROKE_PARTS = 7f;

    /** The second pass over the same rail moves aside by one part in this many of the shorter side (OB-216). */
    private static final double TRACE_NUDGE_PARTS = 18.0;

    /**
     * The way back where it does not share track with the way out: one color would turn out-and-back into a circuit.
     */
    private static final Color TRACE_RETURN = new Color(255, 150, 40);

    /**
     * A leg that ends somewhere full autonomy will never choose (a parking berth, or a square that turns every train).
     * MAGENTA, the one strong hue left: far from the red arrowhead, the blue badge and the warm path colors.
     */
    private static final Color TRACE_MANUAL = new Color(190, 40, 160);

    /** The chevrons on it, dark enough to read against the yellow they sit on. */
    private static final Color TRACE_CHEVRON = new Color(120, 80, 0);

    /** The arrival marks: a color of their own for a question of their own. */
    private static final Color ARRIVAL = new Color(255, 205, 0);

    /**
     * Near-black outline, since yellow on the pale gray of an unlit tile disappears; it also tells filled from hollow.
     */
    private static final Color ARRIVAL_EDGE = new Color(60, 45, 0);

    /** A barred arrival: the same mark drawn as an absence, an open gray chevron (OB-326). */
    private static final Color ARRIVAL_BARRED = new Color(140, 140, 140);

    /**
     * The badge, and the train mark on it, drawn again over whatever is laid on top (MT-076): the running path is
     * painted after the whole annotation (LayoutLabel.paintComponent) and would bury a station under the route to it.
     */
    public void paintBadgeOverRun(Graphics2D g, int width, int height)
    {
        if (badge == null && !occupied) return;

        Object oldHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke oldStroke = g.getStroke();
        Color oldColor = g.getColor();
        java.awt.Composite oldComposite = g.getComposite();

        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));

            if (badge != null)
            {
                paintBadge(g, width, height);

                // and the arrows over it, as `paint` draws them (MT-710)
                paintArrows(g, width, height);

                paintArrivals(g, width, height);
            }

            // And the train mark with it, as over the badge in the first place.
            if (occupied) paintTrainMark(g, width, height);
        }
        finally
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldHint == null
                ? RenderingHints.VALUE_ANTIALIAS_DEFAULT : oldHint);

            g.setStroke(oldStroke);
            g.setColor(oldColor);
            g.setComposite(oldComposite);
        }
    }

    /** Which icons stations are drawn with. */
    public enum StationIcons
    {
        /** Circles, diamonds and squares (the grid in `paintBadge`): big for a station, small for a plain sensor. */
        CLASSIC,

        /**
         * FR-118's blocks and hexagons (`paintStation`), spilling onto the squares beside a bend; white in the editor.
         */
        SHAPES
    }

    /**
     * The icons stations are drawn with: the classic circles, diamonds and squares, or FR-118's shapes (one setting).
     */
    private static volatile StationIcons stationIcons = StationIcons.CLASSIC;

    /** Asked in ONE place, as `LayoutLabel.tailStyle` is: every square follows on its next paint. */
    public static StationIcons stationIcons()
    {
        return stationIcons;
    }

    /** Chooses the icons stations are drawn with from now on; null for the default. */
    public static void setStationIcons(StationIcons icons)
    {
        stationIcons = icons == null ? StationIcons.CLASSIC : icons;
    }

    /**
     * The box a mark is drawn in with the classic icons: 14 pixels at 30 and 28 at 60 for a station (the contact's own
     * width), 10 and 20 for a plain sensor (the inside of its ring).  A station out of service takes the same box, a
     * sensor a pixel more all round (`paintOutOfService`).
     */
    static int classicBox(int tile, boolean station)
    {
        return station ? Math.max(9, Math.round(tile * 14 / 30f)) : Math.max(6, Math.round(tile * 10 / 30f));
    }

    private void paintBadge(Graphics2D g, int width, int height)
    {
        // ONE COLOR WHENEVER AUTONOMY WILL NOT SEND A TRAIN HERE (FR-103, MT-492): orange for a square nothing can
        // pass, gray for a parking berth.  Impassable is asked FIRST: the flags are independent in the type, so a Badge
        // built without `parking` must still take the X's color.  Color says whether autonomy uses the square, the mark
        // what it does.
        Color colour = badge.isImpassable() ? POINT_IMPASSABLE : badge.isParking() ? POINT_INACTIVE : POINT_ACTIVE;

        // ONE MARK FOR EVERY SQUARE OUT OF SERVICE, whatever it otherwise is - `paintOutOfService`
        if (badge.isImpassable())
        {
            paintOutOfService(g, width, height, colour);

            return;
        }

        // A STATION TAKES THE ICONS CHOSEN FOR FR-118 - `paintStation` - while they are the ones drawn.  With the classic
        // icons, and for a passing point either way, the grid below.
        if (badge.isStation() && stationIcons() == StationIcons.SHAPES)
        {
            paintStation(g, width, height, colour);

            return;
        }

        boolean classic = stationIcons() == StationIcons.CLASSIC;

        // A station takes a bigger badge than a passing point (20px against 17px on the graph); `classicBox` with the
        // classic icons.
        int size = classic ? classicBox(Math.min(width, height), badge.isStation())
            : Math.max(badge.isStation() ? 11 : 8, Math.min(width, height) / (badge.isStation() ? 2 : 3));

        // A STATION WHOSE TRACK RUNS OUT IS A TERMINUS, whatever its turning setting (FR-118): the square, with the
        // classic icons.
        boolean deadEnd = classic && badge.isStation() && badge.getDeadEnd() != null;

        boolean turns = deadEnd || badge.isTerminus() || badge.isReversing();
        boolean mayTurn = turns && !deadEnd && badge.isOptional();

        // A diamond is drawn larger than a circle or square of the same box: turned 45 degrees, its corners that stick
        // out are smaller than the ones that go in, so it covers half the area and would read as the smaller mark.
        if (mayTurn)
        {
            size = Math.round(size * 1.35f);

            // Then a little back off each: a station's box is already half the tile, so a full third made the diamond
            // the loudest mark on the page.  Two off the big one and one off the small keep each the weight of its
            // variant.
            size -= badge.isStation() ? 2 : 1;
        }

        // A PLAIN SENSOR'S TURN MARK A LITTLE BIGGER, OVER ITS CONTACT: the diamond 16 pixels at 30 and 32 at 60.  With
        // FR-118's icons only: with the classic ones a sensor's mark sits inside its contact, smaller than a station's.
        if (!classic && turns) size = Math.round(Math.min(width, height) * (mayTurn ? 0.53f : 0.4f));

        // Centered on the TRACK: the midpoint of the route's own two sides, the tile center for a straight.
        int[] on = trackCentre(width, height);

        int x = on[0] - size / 2;
        int y = on[1] - size / 2;

        // A STATION is placed by `paintStation` (FR-118), on its rails.  A passing point's mark is a third of the tile.

        // A CLASSIC STATION'S DIAMOND ON A BEND, ON ITS RAILS: a pixel in from the middle of the chord, 15 pixels at 30
        // and 29 at 60 (17 and 36 on straight track); `paintBadge` cuts the miters off where they pass the square.
        boolean onTheBend = classic && badge.isStation() && mayTurn && trackBends();

        if (onTheBend)
        {
            int[] a = midpoint(badge.getA(), width, height), b = midpoint(badge.getB(), width, height);

            double cx = (a[0] + b[0]) / 2.0, cy = (a[1] + b[1]) / 2.0;

            cx += Math.signum(width / 2.0 - cx);
            cy += Math.signum(height / 2.0 - cy);

            size = Math.round(Math.min(width, height) * 29 / 60f);

            x = (int) Math.round(cx - size / 2.0);
            y = (int) Math.round(cy - size / 2.0);
        }

        // never let it hang outside the square - by its outline's width too: a station's is 2 pixels, and a classic
        // station's mark on a curve, a pixel from the edge, would put its outline's outer half where the square's paint
        // is cut off (MT-710)
        int margin = badge.isStation() ? 2 : 1;

        if (!onTheBend)
        {
            x = Math.max(margin, Math.min(width - size - margin, x));
            y = Math.max(margin, Math.min(height - size - margin, y));
        }

        // Where the badge ACTUALLY went, for the star drawn on top of it (MT-124).  Recorded rather than recomputed:
        // a second copy of the placement arithmetic would be a second chance to disagree.
        badgeDrawnAt = new int[] {x + size / 2, y + size / 2};

        g.setStroke(new BasicStroke(badge.isStation() ? 2f : 1.5f));

        // Filled when named, hollow when not, so an unnamed point is visible but visibly unfinished; white with its
        // color round it in the editor with FR-118's icons.
        boolean white = editing && !classic;

        Color fill = badge.isNamed() && !white ? colour : Color.WHITE;
        Color line = badge.isNamed() && !white ? Color.WHITE : colour;

        // THE SHAPE SAYS WHAT TURNING MEANS HERE, THE SIZE WHETHER IT IS A STATION (the grid in Badge's javadoc).  THE
        // CONTACT COVERED UNDER A STATION'S MARK, as under FR-118's icons: a classic diamond is wider than the contact
        // and leaves its ring showing; a classic sensor's mark sits inside it; FR-118's turn mark covers it.
        if (classic ? badge.isStation() : turns) coverTheContact(g, width, height);

        if (turns && mayTurn)
        {
            Graphics2D d = (Graphics2D) g.create();

            try
            {
                if (onTheBend) d.clipRect(0, 0, width, height);

                diamond(d, x, y, size, fill, line);
            }
            finally
            {
                d.dispose();
            }
        }
        else if (turns)
        {
            g.setColor(fill);
            g.fillRect(x, y, size, size);
            g.setColor(line);
            g.drawRect(x, y, size, size);
        }
        else
        {
            g.setColor(fill);
            g.fillOval(x, y, size, size);
            g.setColor(line);
            g.drawOval(x, y, size, size);
        }
    }

    /**
     * The sensor's contact covered under a plain sensor's turn mark (`cover`, kept inside the square); not where no
     * road.
     */
    private void coverTheContact(Graphics2D g, int width, int height)
    {
        if (badge.getA() == null || badge.getB() == null || badge.getA() == badge.getB()) return;

        int[] a = midpoint(badge.getA(), width, height), b = midpoint(badge.getB(), width, height);

        if (a == null || b == null) return;

        int[] centre = trackCentre(width, height);

        Graphics2D c = (Graphics2D) g.create();

        try
        {
            c.clipRect(0, 0, width, height);
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            c.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            c.translate(centre[0], centre[1]);
            c.rotate(Math.atan2(b[1] - a[1], b[0] - a[0]));

            cover(c, Math.min(width, height));
        }
        finally
        {
            c.dispose();
        }
    }

    /**
     * How tall a station's icon is across its track (FR-118): 13 pixels at 30, 26 at 60, no taller than its contact.
     */
    private static final float ICON_SHARE = 0.43f;

    /** How many times as long as it is tall a station's icon is, before the pixels taken off it. */
    private static final float ICON_LENGTH_RATIO = 1.9f;

    /** From this tile size up the larger proportions are used: the 60 pixel tile, against the 30. */
    private static final int LARGE_TILE = 60;

    /** The icon outline's stroke on a large tile and on a small one. */
    private static final float ICON_STROKE_LARGE = 2f;
    private static final float ICON_STROKE_SMALL = 1.5f;

    /** On a small tile a terminus's point is this share of its height long; on a large one, half. */
    private static final double TERMINUS_POINT_SMALL = 0.66;

    /**
     * A station's icon (FR-118), per the station icon page (https://claude.ai/artifact/Y9BydDSBLW3eLy85i1yQfB):
     *
     *     a station                      a rounded block, half as long again as it is tall
     *     trains may turn                the hexagon, pointed at both ends
     *     trains must turn               two terminus shapes back to back, the track's black line showing between
     *     its track runs out one way     the terminus: the hexagon flat against that side, with a bar there
     *     out of service                 a white circle, orange ring and X, drawn by `paintOutOfService` first
     *
     * Blue where autonomy uses it, gray a berth it does not choose; filled when named, white ringed in the color when
     * not, and always the latter in the autonomy editor.  Laid along the road from side a to side b, the terminus's
     * flat end on its dead-end side.  On a bend it lies along the chord, on its rails, its full size, and what falls
     * outside the square spills onto the squares beside it (`paintSpill`).
     */
    private void paintStation(Graphics2D g, int width, int height, Color colour)
    {
        int tile = Math.min(width, height);

        int h = Math.max(7, Math.round(tile * ICON_SHARE));

        boolean large = tile >= LARGE_TILE;

        boolean bends = trackBends();

        int[] centre = trackCentre(width, height);

        int[] a = midpoint(badge.getA(), width, height), b = midpoint(badge.getB(), width, height);

        boolean known = a != null && b != null && badge.getA() != badge.getB();

        // along the road from a to b, the shapes drawn flat end (a terminus's) left: a dead end on side b turns them
        // round
        double angle = known ? Math.atan2(b[1] - a[1], b[0] - a[0]) : 0;

        Side deadEnd = known && (badge.getDeadEnd() == badge.getA() || badge.getDeadEnd() == badge.getB())
            ? badge.getDeadEnd() : null;

        if (deadEnd != null && deadEnd == badge.getB()) angle += Math.PI;

        // WHITE WITH ITS COLOR ROUND IT IN THE EDITOR: the arrows are drawn over the icon, and are what it is read for
        Color fill = badge.isNamed() && !editing ? colour : Color.WHITE;
        Color line = badge.isNamed() && !editing ? Color.WHITE : colour;

        boolean turns = badge.isTerminus() || badge.isReversing();

        float stroke = large ? ICON_STROKE_LARGE : ICON_STROKE_SMALL;

        // THE LENGTHS (MT-710): a terminus 1px shorter than `round(h * 1.9)`, a may-reverse station 1px shorter at 30px
        // and 2px at 60px, so the must-turn pair (both halves and the gap) is no longer than the may-turn hexagon.
        int turning = Math.round(h * ICON_LENGTH_RATIO) - (large ? 2 : 1), terminus = Math.round(h * ICON_LENGTH_RATIO) - 1;

        java.awt.Shape outline = !turns && deadEnd == null ? blockShape(h)
            : deadEnd != null ? hexagon(terminus, h, true, large ? h / 2.0 : h * TERMINUS_POINT_SMALL)
            : badge.isOptional() ? hexagon(turning, h, false, h / 2.0)
            : mustTurnShape(h, turning, large);

        java.awt.Shape stroked = new BasicStroke(stroke).createStrokedShape(outline);

        java.awt.geom.Rectangle2D needs =
            java.awt.geom.AffineTransform.getRotateInstance(angle).createTransformedShape(stroked).getBounds2D();

        double scale = 1;

        // ON A BEND, ITS FULL SIZE ON ITS RAILS, SPILLING ONTO THE SQUARES BESIDE IT (MT-710): a chord across a corner
        // reaches past two edges and a square's paint is cut off at its edges, so `paintSpill` draws the rest over
        // them.
        boolean spills = known && bends;

        // AND ANYWHERE ELSE WHOLLY INSIDE ITS SQUARE (MT-710): moved toward the middle as far as it needs, and made
        // smaller only where even there it would not fit.
        double[] at = spills ? new double[] {centre[0], centre[1], scale}
            : fitInside(needs, scale, centre[0], centre[1], width, height);

        scale = at[2];

        badgeDrawnAt = new int[] {(int) Math.round(at[0]), (int) Math.round(at[1])};

        // WHAT IT COVERS, for the diagram, which draws what lies over a spill again over it: the icon's body and
        // outline, a pixel round, less the square.  Built once per size: building it on every paint stalled the window.
        if (spills && (spillFor == null || spillFor[0] != width || spillFor[1] != height))
        {
            java.awt.geom.AffineTransform placed = java.awt.geom.AffineTransform.getTranslateInstance(at[0], at[1]);

            placed.rotate(angle);
            placed.scale(scale, scale);

            java.awt.Shape body = placed.createTransformedShape(outline);

            java.awt.geom.Area region = new java.awt.geom.Area(body);

            region.add(new java.awt.geom.Area(new BasicStroke((float) (stroke * scale) + 2f).createStrokedShape(body)));
            region.subtract(new java.awt.geom.Area(new java.awt.Rectangle(0, 0, width, height)));

            spillRegion = region;
            spillFor = new int[] {width, height};
        }

        // THE CONTACT COVERED ON A BEND TOO: the icon is no taller than the contact, whose ring showed round it.  On
        // the chord's middle, kept inside the square.  Once: not again with the spill.
        if (spills && !spilling)
        {
            Graphics2D c = (Graphics2D) g.create();

            try
            {
                c.clipRect(0, 0, width, height);
                c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                c.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                c.translate(centre[0], centre[1]);
                c.rotate(angle);

                cover(c, tile);
            }
            finally
            {
                c.dispose();
            }
        }

        Graphics2D s = (Graphics2D) g.create();

        try
        {
            // WHERE IT SPILLS, CUT AT THE SQUARE'S EDGES - the square's own part inside them, the spill's outside them, so
            // no pixel is drawn twice and no soft edge darkens where the two meet
            if (spills) s.clip(spilling ? outside(width, height) : new java.awt.Rectangle(0, 0, width, height));

            s.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // PURE, as the page drew them: the default stroke control moves an outline by up to half a pixel, and at 60
            // pixels the must-turn's outlines then met across the gap and hid the track's black line in it
            s.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            s.translate(at[0], at[1]);
            s.rotate(angle);

            if (known && !bends) cover(s, tile);

            s.scale(scale, scale);
            s.setStroke(new BasicStroke(stroke));

            if (deadEnd != null)
            {
                // flat against the buffer, its bar there; more pointed at 30 pixels
                body(s, outline, fill, line);

                bar(s, -terminus / 2.0 + h * 0.3, h * 0.62, Math.max(2, Math.round(tile / 12f)), line);
            }
            else if (turns && badge.isOptional())
            {
                body(s, outline, fill, line);
            }
            else if (turns)
            {
                mustTurn(s, tile, h, turning, fill, line);
            }
            else
            {
                body(s, outline, fill, line);
            }
        }
        finally
        {
            s.dispose();
        }
    }

    /**
     * Whether this square's station icon reaches past the square onto the ones beside it: a station on a curve, on the
     * diagram, drawn its full size on its rails.  The square draws what falls inside it, `paintSpill` the rest.
     */
    public boolean spillsOverItsSquare()
    {
        // not a square the editor shades, which draws no icon on itself; and only FR-118's icons spill - a classic
        // mark is kept inside its square
        return stationIcons() == StationIcons.SHAPES && badge != null && badge.isStation() && !badge.isImpassable() && !ignored && badge.getA() != null
            && badge.getB() != null && badge.getA() != badge.getB() && trackBends();
    }

    /**
     * What of this square's station icon falls outside the square, drawn over the squares beside it
     * (`spillsOverItsSquare`); the graphics are at this square's origin, not cut off at its edges.
     */
    public void paintSpill(Graphics2D g, int width, int height)
    {
        if (!spillsOverItsSquare()) return;

        Graphics2D s = (Graphics2D) g.create();

        try
        {
            spilling = true;

            paintBadge(s, width, height);
        }
        finally
        {
            spilling = false;

            s.dispose();
        }
    }

    /** Whether `paintSpill` is drawing - the station's part outside its square, without the contact's cover. */
    private boolean spilling;

    /** What the station's icon covers outside its square, as `paintStation` built it for the size in `spillFor`. */
    private java.awt.Shape spillRegion;

    /** The width and height `spillRegion` was built for, or null before the icon is first drawn. */
    private int[] spillFor;

    /**
     * What this square's spill covers, in its own coordinates: the icon's body and outline, a pixel round for its soft
     * edge, less the square; null where nothing spills or the icon has not been drawn at this size yet.  The diagram
     * draws what lies over it again over the spill, so the spill keeps its depth under the captions.
     */
    public java.awt.Shape spillShape(int width, int height)
    {
        int[] built = spillFor;

        if (built == null || built[0] != width || built[1] != height || !spillsOverItsSquare()) return null;

        return spillRegion;
    }

    /**
     * This square's arrows and barred-arrival marks again, over a neighbor's station that spilled onto it, as `paint`
     * draws them; nothing on a square autonomy takes no notice of.  The graphics are cut off at the square's edges.
     */
    public void paintArrowsAgain(Graphics2D g, int width, int height)
    {
        if (ignored) return;

        Graphics2D a = (Graphics2D) g.create();

        try
        {
            a.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            a.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f));

            paintArrows(a, width, height);

            paintArrivals(a, width, height);

            // AND ITS OUTLINE, which its own paint draws over everything on it: the spill broke it
            if (selected)
            {
                a.setStroke(new BasicStroke(2f));
                a.setColor(SELECTED);
                a.drawRect(1, 1, width - 3, height - 3);
            }
        }
        finally
        {
            a.dispose();
        }
    }

    /**
     * The sensor's contact covered, under a station's icon on straight track (FR-118): white a pixel wider all round,
     * the track's black band (`tile` * 8 / 30 across) drawn back over it.  The icons are no taller than the contact, so
     * they alone would leave its edges showing.  The graphics are at the icon's center, turned along the track.
     */
    private static void cover(Graphics2D g, int tile)
    {
        int m = Math.round(tile * 0.2f + 2);

        g.setColor(Color.WHITE);
        g.fill(new java.awt.geom.Rectangle2D.Double(-m, -m, 2 * m, 2 * m));

        g.setColor(Color.BLACK);
        g.fill(new java.awt.geom.Rectangle2D.Double(-m, -tile * 4 / 30.0, 2 * m, tile * 8 / 30.0));
    }

    /** A station's block: half as long again as it is tall, its corners rounded (D's, on the station icon page). */
    private static void block(Graphics2D g, int h, Color fill, Color line)
    {
        body(g, blockShape(h), fill, line);
    }

    /** The block's shape, about the origin along x. */
    private static java.awt.Shape blockShape(int h)
    {
        int w = Math.round(h * 1.5f);

        double r = Math.max(2, h * 0.22);

        return new java.awt.geom.RoundRectangle2D.Double(-w / 2.0, -h / 2.0, w, h, 2 * r, 2 * r);
    }

    /** Around a square to a square's depth, but not the square: where its spill is drawn. */
    private static java.awt.Shape outside(int width, int height)
    {
        java.awt.geom.Area around = new java.awt.geom.Area(new java.awt.Rectangle(-width, -height, 3 * width, 3 * height));

        around.subtract(new java.awt.geom.Area(new java.awt.Rectangle(0, 0, width, height)));

        return around;
    }

    /**
     * Where an icon goes so that all of it is inside its square (MT-710): where asked if it fits there, else the first
     * place that fits on the way to the middle, else the middle made smaller until it does.  A half pixel to spare at
     * each edge.  `needs` is what the icon covers at scale 1, about its center; returns the center's x and y and the
     * scale.
     */
    private static double[] fitInside(java.awt.geom.Rectangle2D needs, double scale, double x, double y, int width,
        int height)
    {
        double mx = width / 2.0, my = height / 2.0;

        for (int step = 0; step <= 20; step++)
        {
            double t = step / 20.0, cx = x + t * (mx - x), cy = y + t * (my - y);

            if (cx + scale * needs.getMinX() >= 0.5 && cx + scale * needs.getMaxX() <= width - 0.5
                && cy + scale * needs.getMinY() >= 0.5 && cy + scale * needs.getMaxY() <= height - 0.5)
            {
                return new double[] {cx, cy, scale};
            }
        }

        double across = Math.max(-needs.getMinX(), needs.getMaxX()), down = Math.max(-needs.getMinY(), needs.getMaxY());

        return new double[] {mx, my, Math.min(scale, Math.min((mx - 0.5) / across, (my - 0.5) / down))};
    }

    /** The must-turn pair's outline about the origin along x: two terminus shapes back to back across a 4-pixel gap. */
    private static java.awt.Shape mustTurnShape(int h, int total, boolean large)
    {
        int gap = 4, half = (total - gap) / 2;

        java.awt.geom.Path2D.Double out = new java.awt.geom.Path2D.Double();

        for (int dir : new int[] {-1, 1})
        {
            java.awt.geom.AffineTransform t = java.awt.geom.AffineTransform.getTranslateInstance(dir * (gap / 2.0 + half / 2.0), 0);

            if (dir < 0) t.scale(-1, 1);

            out.append(t.createTransformedShape(hexagon(half, h, true, large ? h * 0.4 : h * 0.5)), false);
        }

        return out;
    }

    /**
     * Where trains must turn: two terminus shapes back to back, flat ends towards each other across a 4-pixel gap the
     * track's black line shows through, `total` long (MT-710).  The bar at 30 is a pixel wide and set on whole pixels:
     * in its half's own frame, which can sit on a half pixel, it would show gray.
     */
    private static void mustTurn(Graphics2D g, int tile, int h, int total, Color fill, Color line)
    {
        boolean large = tile >= LARGE_TILE;

        int gap = 4;

        int half = (total - gap) / 2;

        for (int dir : new int[] {-1, 1})
        {
            Graphics2D p = (Graphics2D) g.create();

            try
            {
                p.translate(dir * (gap / 2.0 + half / 2.0), 0);

                if (dir < 0) p.scale(-1, 1);

                body(p, hexagon(half, h, true, large ? h * 0.4 : h * 0.5), fill, line);

                if (large) bar(p, -half / 2.0 + Math.max(2, h * 0.22), h * 0.6, Math.max(2, Math.round(tile / 12f)), line);
            }
            finally
            {
                p.dispose();
            }

            if (!large)
            {
                // in the half's flat part, before its point begins
                int at = gap / 2 + Math.round(tile / 15f), tall = Math.round(tile * 8 / 30f);

                g.setColor(line);
                g.fill(new java.awt.geom.Rectangle2D.Double(dir > 0 ? at : -at - 1, -tall / 2.0, 1, tall));
            }
        }
    }

    /** A hexagon about the origin along x: pointed at both ends, or flat at the left (a terminus's buffer end). */
    private static java.awt.geom.Path2D hexagon(double w, double h, boolean flatLeft, double point)
    {
        java.awt.geom.Path2D.Double p = new java.awt.geom.Path2D.Double();

        if (flatLeft)
        {
            p.moveTo(-w / 2, -h / 2);
            p.lineTo(w / 2 - point, -h / 2);
            p.lineTo(w / 2, 0);
            p.lineTo(w / 2 - point, h / 2);
            p.lineTo(-w / 2, h / 2);
        }
        else
        {
            p.moveTo(-w / 2, 0);
            p.lineTo(-w / 2 + point, -h / 2);
            p.lineTo(w / 2 - point, -h / 2);
            p.lineTo(w / 2, 0);
            p.lineTo(w / 2 - point, h / 2);
            p.lineTo(-w / 2 + point, h / 2);
        }

        p.closePath();

        return p;
    }

    /** A shape filled, then outlined in the stroke already set. */
    private static void body(Graphics2D g, java.awt.Shape shape, Color fill, Color line)
    {
        g.setColor(fill);
        g.fill(shape);
        g.setColor(line);
        g.draw(shape);
    }

    /** A bar across the track at x, `tall` high and `wide` across, its left edge on a whole pixel of the shape's frame. */
    private static void bar(Graphics2D g, double x, double tall, int wide, Color colour)
    {
        g.setColor(colour);
        g.fill(new java.awt.geom.Rectangle2D.Double(Math.round(x - wide / 2.0), -tall / 2, wide, tall));
    }

    /**
     * A square out of service (nothing may pass or stop there), on the diagram and in the editor: a white circle, an
     * orange ring, an orange X.  On the middle of its track and filled white so the contact does not show; no wider
     * than the space inside the red arrows the sensor's art draws round its contact while occupied (14 pixels at 30, 28
     * at 60).  With the classic icons a station's sign is its mark's size and solid orange with a white X, and a plain
     * sensor's the white circle ringed in orange, a pixel more all round than its mark.
     */
    private void paintOutOfService(Graphics2D g, int width, int height, Color colour)
    {
        int tile = Math.min(width, height);

        int[] on = trackCentre(width, height);

        boolean shapes = stationIcons() == StationIcons.SHAPES;

        // FR-118's size for every square with its icons.  With the classic ones a station's mark's size, and a sensor's a
        // pixel more all round than its mark
        double across = shapes ? Math.round(tile * 0.47f)
            : classicBox(tile, badge.isStation()) + (badge.isStation() ? 0 : Math.round(tile / 15f));

        // A CLASSIC STATION'S IS SOLID ORANGE WITH A WHITE X - filled, as a named station's mark is, where a sensor's
        // stays the white circle ringed in orange
        boolean filled = !shapes && badge.isStation();

        // lighter strokes on a classic sensor's small sign, so its X still reads as an X
        boolean small = !shapes && across < 16;

        float least = small ? 1.5f : 2f;

        float ring = Math.max(least, (float) across / 9f);

        // the ring's middle, so that its outer edge is `across` wide; the small sign's X reaches nearer its ring, as thin
        double r = (across - ring) / 2.0, arm = filled ? across / 4.0 : r * (small ? 0.62 : 0.55);

        badgeDrawnAt = new int[] {on[0], on[1]};

        Graphics2D c = (Graphics2D) g.create();

        try
        {
            c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            c.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            c.translate(on[0], on[1]);

            c.setColor(filled ? colour : Color.WHITE);
            c.fill(new java.awt.geom.Ellipse2D.Double(-across / 2.0, -across / 2.0, across, across));

            if (!filled)
            {
                c.setColor(colour);
                c.setStroke(new BasicStroke(ring));
                c.draw(new java.awt.geom.Ellipse2D.Double(-r, -r, 2 * r, 2 * r));
            }

            c.setColor(filled ? Color.WHITE : colour);
            c.setStroke(new BasicStroke(Math.max(least, (float) across / 8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            c.draw(new java.awt.geom.Line2D.Double(-arm, -arm, arm, arm));
            c.draw(new java.awt.geom.Line2D.Double(-arm, arm, arm, -arm));
        }
        finally
        {
            c.dispose();
        }
    }

    /** Whether the track turns a corner: its two sides are not opposite (N, E, S and W are declared in order). */
    private boolean trackBends()
    {
        Side sideA = badge != null && badge.getA() != null ? badge.getA()
            : marks.isEmpty() ? null : marks.get(0).getA();

        Side sideB = badge != null && badge.getB() != null ? badge.getB()
            : marks.isEmpty() ? null : marks.get(0).getB();

        if (sideA == null || sideB == null || sideA == sideB) return false;

        return Math.abs(sideA.ordinal() - sideB.ordinal()) != 2;
    }

    /** Where paintBadge last drew, or null; only the train mark reads it, to sit ON the badge. */
    private int[] badgeDrawnAt;

    /**
     * The midpoint of this tile's own two track sides: the tile center for a straight, on the rails for anything else.
     * Public because the running overlay needs it: the end of a run has one side and a null, and would otherwise stop
     * in the middle of the square (OB-026).  The badges are placed by it (MT-057), so the run line and badge agree.
     */
    public int[] trackCentre(int width, int height)
    {
        // the badge's own route where it has one, otherwise the first route drawn, otherwise center
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

    /**
     * The middle of the road a train stands on, named by the sides it is known to end at (MT-642), on a curve or a
     * double curve (two roads in one tile, so a train on the other road was drawn on the wrong one); `trackCentre`
     * elsewhere.
     *
     * @param sides the sides of the train's road, from `TileOverlay.trainSides`
     */
    public int[] trackCentreOf(List<Side> sides, int width, int height)
    {
        if (sides == null || sides.isEmpty()) return trackCentre(width, height);

        java.util.Set<java.util.Set<Side>> roads = new java.util.LinkedHashSet<>();

        if (badge != null && badge.getA() != null && badge.getB() != null) roads.add(roadOf(badge.getA(), badge.getB()));

        for (Mark mark : marks)
        {
            if (mark.getA() != null && mark.getB() != null) roads.add(roadOf(mark.getA(), mark.getB()));
        }

        int[] centre = centreOf(roads, sides, width, height);

        return centre != null ? centre : trackCentre(width, height);
    }

    /**
     * The same, from a square's own ports (its track type and orientation) rather than from what autonomy says about
     * it: an annotation knows the roads only while the restriction arrows are shown.  Null anywhere but a curve or
     * double curve.
     *
     * @param routes the square's roads, from `TilePorts.ports`
     */
    public static int[] trackCentreOf(List<TilePorts.Route> routes, List<Side> sides, int width, int height)
    {
        if (routes == null || sides == null || sides.isEmpty()) return null;

        java.util.Set<java.util.Set<Side>> roads = new java.util.LinkedHashSet<>();

        for (TilePorts.Route route : routes)
        {
            if (route.getA() != null && route.getB() != null) roads.add(roadOf(route.getA(), route.getB()));
        }

        return centreOf(roads, sides, width, height);
    }

    /** The middle of the one road that ends at every one of the sides, where every road is a curve, or null. */
    private static int[] centreOf(java.util.Set<java.util.Set<Side>> roads, List<Side> sides, int width, int height)
    {
        java.util.Set<Side> found = null;

        for (java.util.Set<Side> road : roads)
        {
            // CURVES ONLY: two neighboring sides, on every road of the tile
            if (road.size() != 2) return null;

            java.util.Iterator<Side> ends = road.iterator();

            if (Math.abs(ends.next().ordinal() - ends.next().ordinal()) == 2) return null;

            if (!road.containsAll(sides)) continue;

            // more than one road through these sides: not this method's to choose
            if (found != null) return null;

            found = road;
        }

        if (found == null) return null;

        java.util.Iterator<Side> ends = found.iterator();

        int[] a = midpoint(ends.next(), width, height);
        int[] b = midpoint(ends.next(), width, height);

        if (a == null || b == null) return null;

        return new int[] {(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
    }

    /** A road as its two ends, whichever way round they were given. */
    private static java.util.Set<Side> roadOf(Side a, Side b)
    {
        return java.util.EnumSet.of(a, b);
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

        // A third of the tile, which is what LayoutGrid gives an address label (OB-019): the two numbers sit in opposite
        // corners of the same square in the same color, so the smaller one would read as a footnote to the larger.
        int size = Math.max(8, Math.min(width, height) / 3);

        g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, (float) size));

        java.awt.FontMetrics metrics = g.getFontMetrics();

        int textWidth = metrics.stringWidth(text);

        // Top right, away from the address the diagram writes at the leading edge: the two are the same color on
        // purpose, so they must not sit on top of one another and read as one number.
        int x = width - textWidth - 2;
        int y = metrics.getAscent() + 1;

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
     * Where a side meets the edge of the tile.  Public because the diagram's own train mark is a line along the rail
     * drawn by `LayoutLabel` (MT-309): the run line, the tested path and the train must agree about where a rail meets
     * the edge.  Null for a side that is not one of the four.
     */
    public static int[] midpoint(Side side, int width, int height)
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

        // `editing` is in here because it changes the PICTURE (whether a named point's icon is filled with its color or
        // white) and equals is what LayoutLabel asks to decide whether a redraw is needed.  The two populations
        // of labels happen to be segregated today, so no live path reaches it; leaving it out would be a trap.
        return length == other.length && selected == other.selected
            && (badge == null ? other.badge == null : badge.equals(other.badge))
            && ignored == other.ignored && stationless == other.stationless && curved == other.curved
            && portal == other.portal
            && traces.equals(other.traces) && blockedOnly == other.blockedOnly
            && occupied == other.occupied && editing == other.editing && unmeasured == other.unmeasured
            && allowedToo == other.allowedToo && allowedOnly == other.allowedOnly && bareSide == other.bareSide
            && marks.equals(other.marks) && arrivals.equals(other.arrivals);
    }

    @Override
    public int hashCode()
    {
        return marks.hashCode() * 31 + length * 2
            + (selected ? 1 : 0) + (badge == null ? 0 : badge.hashCode() * 4)
            + (ignored ? 16 : 0) + (curved ? 64 : 0) + (portal ? 256 : 0) + (occupied ? 1024 : 0)
            + traces.hashCode() * 3
            + (blockedOnly ? 512 : 0) + (editing ? 2048 : 0) + (unmeasured ? 4096 : 0) + (allowedToo ? 8192 : 0) + (allowedOnly ? 16384 : 0)
            + (bareSide == null ? 0 : (bareSide.ordinal() + 1) * 32768) + (stationless ? 131072 : 0)
            + arrivals.hashCode() * 7;
    }

    @Override
    public String toString()
    {
        return marks + (length >= 0 ? " len=" + length : "") + (selected ? " selected" : "")
            + (badge == null ? "" : " " + badge)
            + (arrivals.isEmpty() ? "" : " " + arrivals)
            + (ignored ? " ignored" : "") + (stationless ? " no station" : "");
    }
}
